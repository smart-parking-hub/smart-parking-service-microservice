package com.smartparking.payment.application.service.impl;

import java.util.Comparator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.PaymentGatewayEnum;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.PaymentTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.CreatePaymentRequest;
import com.smartparking.payment.application.dto.DemoPaymentRequest;
import com.smartparking.payment.application.dto.PaymentDto;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.application.service.OutboxPublisherService;
import com.smartparking.payment.application.service.PaymentService;
import com.smartparking.payment.config.PayOSConfig;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.common.enums.WithdrawalStatusEnum;
import com.smartparking.payment.application.dto.AdminDashboardResponse;
import com.smartparking.payment.application.dto.AdminTransactionResponse;
import com.smartparking.payment.domain.repository.DisputeRepository;
import com.smartparking.payment.domain.repository.WalletLedgerEntryRepository;
import com.smartparking.payment.domain.repository.WithdrawalRequestRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final com.smartparking.payment.infrastructure.client.BookingPaymentVerifier bookingPaymentVerifier;
    private final PayOS payOS;
    private final PayOSConfig payOSConfig;
    private final PaymentRepository paymentRepository;
    private final com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard attemptGuard;
    private final MerchantWalletService merchantWalletService;
    private final OutboxPublisherService outboxPublisherService;
    private final ObjectMapper objectMapper;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final DisputeRepository disputeRepository;
    private final WalletLedgerEntryRepository walletLedgerEntryRepository;

    private final com.smartparking.payment.domain.repository.CashCheckoutReceiptRepository cashReceipts;

    @Value("${app.demo-payment-enabled:false}")
    private boolean demoPaymentEnabled;

    @org.springframework.beans.factory.annotation.Autowired
    private com.smartparking.payment.domain.repository.RefundRequestRepository refundRequests;

    @Value("${app.payos-recovery-enabled:true}")
    private boolean payosRecoveryEnabled = true;

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public String createPaymentLink(Long driverUserId, CreatePaymentRequest request) {
        PaymentTypeEnum type;
        try { type = PaymentTypeEnum.valueOf(request.getPaymentType()); }
        catch (RuntimeException failure) { throw new AppException(ErrorCode.BAD_REQUEST, "Loại thanh toán không hợp lệ"); }
        attemptGuard.lock(request.getBookingId(), type);
        if (type == PaymentTypeEnum.FINAL_PAYMENT && cashCollectionReserved(request.getBookingId()))
            throw new AppException(ErrorCode.CONFLICT, "Tiền mặt đang được xác nhận hoặc đã thu; không tạo thêm khoản PayOS");
        List<Payment> attempts = attempts(request.getBookingId(), type);
        if (attempts.stream().anyMatch(p -> p.getGateway() == PaymentGatewayEnum.WALLET && p.getStatus() == PaymentStatusEnum.PENDING))
            throw new AppException(ErrorCode.CONFLICT, "Thanh toán ví đang xử lý; vui lòng kiểm tra kết quả");
        attempts = attempts.stream().filter(p -> p.getGateway() != PaymentGatewayEnum.WALLET || p.getStatus() != PaymentStatusEnum.FAILED).toList();
        for (Payment old : attempts) {
            if (!driverUserId.equals(old.getDriverId())) throw new AppException(ErrorCode.FORBIDDEN, "Giao dịch không thuộc tài khoản");
            if (old.getStatus() == PaymentStatusEnum.REQUIRES_REVIEW || old.getStatus() == PaymentStatusEnum.REFUNDED)
                throw new AppException(ErrorCode.CONFLICT, "Giao dịch cần kiểm tra; không tạo thêm khoản thu");
        }
        if (attempts.stream().anyMatch(p -> p.getStatus() == PaymentStatusEnum.SUCCESS))
            return appendQueryParam(payOSConfig.getReturnUrl(), "bookingId", request.getBookingId().toString());

        var quote = bookingPaymentVerifier.require(request.getBookingId(), driverUserId);
        BigDecimal amount = quote.payable(type);
        if (amount == null || amount.signum() <= 0) throw new AppException(ErrorCode.BAD_REQUEST, "Booking không có số tiền cần thanh toán");
        Payment latest = attempts.isEmpty() ? null : attempts.getFirst();
        // Revalidate EVERY older order before issuing another one. An ambiguous provider result blocks recovery.
        for (Payment old : attempts) {
            if (!matchesQuote(old, quote, amount)) throw new AppException(ErrorCode.CONFLICT,
                    "Link thanh toán cũ không khớp số tiền booking hiện tại. Vui lòng kiểm tra lại booking hoặc liên hệ hỗ trợ.");
            PaymentLink link = verifiedLink(old);
            if (link == null) {
                if (old.getCheckoutUrl() == null) {
                    throw new AppException(ErrorCode.PAYMENT_FAILED, "Chưa xác minh được phiên PayOS. Vui lòng kiểm tra trạng thái; chưa tạo thêm khoản thu.");
                }
                // Provider has no record of this previously issued link; retire old attempt safely
                log.info("Link PayOS không tìm thấy cho orderCode {}. Hủy lượt cũ để tạo mới.", old.getOrderCode());
                old.setStatus(PaymentStatusEnum.FAILED);
                paymentRepository.save(old);
                continue;
            }
            if (link.getAmountPaid() == null || link.getAmountPaid() < 0)
                throw new AppException(ErrorCode.PAYMENT_FAILED, "Chưa xác minh được khoản tiền trên PayOS. Vui lòng kiểm tra lại.");
            if (link.getAmountPaid() > 0 || link.getStatus() == vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PAID) {
                if (old == latest && link.getStatus() == vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PAID
                        && BigDecimal.valueOf(link.getAmountPaid()).compareTo(amount) == 0) applyPaidPayment(old);
                else markReview(old, "Giao dịch cũ có tiền nhận; cần đối soát trước khi thanh toán thêm");
                throw new AppException(ErrorCode.CONFLICT, "Đã ghi nhận thông tin chuyển tiền. Vui lòng kiểm tra vé trước khi thanh toán tiếp.");
            }
            boolean amountMatches = link.getAmount() != null && BigDecimal.valueOf(link.getAmount()).compareTo(amount) == 0;
            if (link.getStatus() == vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PENDING
                    && old == latest && old.getStatus() == PaymentStatusEnum.PENDING && old.getCheckoutUrl() != null
                    && amountMatches) {
                // Older attempts must all be terminal before the latest pending order may be reused.
                continue;
            }
            if (link.getStatus() != vn.payos.model.v2.paymentRequests.PaymentLinkStatus.CANCELLED) {
                try {
                    payOS.paymentRequests().cancel(old.getOrderCode(), "Hủy phiên cũ để tạo lại giao dịch");
                } catch (Exception ex) {
                    log.warn("Không thể hủy phiên PayOS {}: {}", old.getOrderCode(), ex.getMessage());
                    throw new AppException(ErrorCode.CONFLICT, "Giao dịch đang xử lý hoặc chưa xác định. Vui lòng kiểm tra lại; chưa tạo khoản thu mới.");
                }
            }
            old.setStatus(PaymentStatusEnum.FAILED);
            paymentRepository.save(old);
        }
        if (latest != null && latest.getStatus() == PaymentStatusEnum.PENDING && latest.getCheckoutUrl() != null)
            return latest.getCheckoutUrl();
        if (!attempts.isEmpty() && !payosRecoveryEnabled)
            throw new AppException(ErrorCode.CONFLICT, "Thanh toán lại đang tạm dừng. Vui lòng xem vé hoặc liên hệ hỗ trợ.");

        long orderCode = attemptGuard.nextOrderCode();
        String key = "booking:" + request.getBookingId() + ":" + type.name();
        Payment payment = Payment.builder().bookingId(request.getBookingId()).driverId(driverUserId)
                .merchantId(quote.merchantId()).parkingLotId(quote.parkingLotId()).gateway(PaymentGatewayEnum.PAYOS)
                .transactionCode(String.valueOf(orderCode)).orderCode(orderCode).amount(amount).paymentType(type)
                .paymentChannel("ONLINE_TRANSFER")
                .status(PaymentStatusEnum.PENDING).idempotencyKey(attempts.isEmpty() ? key : key + ":attempt:" + orderCode)
                .bookingExpiresAt(type == PaymentTypeEnum.DEPOSIT ? quote.expiresAt() : null).build();
        // Persist the reservation even if PayOS times out (noRollbackFor above). Never lose an ambiguous order.
        paymentRepository.saveAndFlush(payment);
        String returnUrl = appendQueryParam(payOSConfig.getReturnUrl(), "bookingId", request.getBookingId().toString());
        String cancelUrl = appendQueryParam(payOSConfig.getCancelUrl(), "bookingId", request.getBookingId().toString());
        try {
            var item = PaymentLinkItem.builder().name("Booking " + request.getBookingId()).quantity(1).price(amount.longValueExact()).build();
            var builder = CreatePaymentLinkRequest.builder().orderCode(orderCode).amount(amount.longValueExact())
                    .description("Booking " + request.getBookingId()).items(List.of(item)).returnUrl(returnUrl).cancelUrl(cancelUrl);
            if (type == PaymentTypeEnum.DEPOSIT) builder.expiredAt(quote.expiresAt().atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toEpochSecond());
            CreatePaymentLinkResponse response = payOS.paymentRequests().create(builder.build());
            if (response == null || response.getAmount() == null || BigDecimal.valueOf(response.getAmount()).compareTo(amount) != 0
                    || !java.util.Objects.equals(response.getOrderCode(), orderCode)
                    || response.getCheckoutUrl() == null || response.getCheckoutUrl().isBlank()) {
                throw new AppException(ErrorCode.PAYMENT_FAILED, "Số tiền hoặc mã đơn PayOS không khớp booking. Chưa mở thanh toán; vui lòng kiểm tra lại.");
            }
            payment.setCheckoutUrl(response.getCheckoutUrl());
            paymentRepository.save(payment);
            return response.getCheckoutUrl();
        } catch (AppException failure) { throw failure; }
        catch (Exception failure) {
            String errorDetail = failure.getMessage() != null && !failure.getMessage().isBlank()
                    ? failure.getMessage()
                    : failure.getClass().getSimpleName();
            log.error("PayOS createPaymentLink failed for booking {} order {}: {}", request.getBookingId(), orderCode, errorDetail, failure);
            throw new AppException(ErrorCode.PAYMENT_FAILED, "Chưa xác minh được phiên PayOS (" + errorDetail + "). Vui lòng kiểm tra trạng thái; chưa tạo thêm khoản thu.");
        }
    }

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public String createGateFinalPaymentLink(Long merchantUserId, Long bookingId) {
        attemptGuard.lock(bookingId, PaymentTypeEnum.FINAL_PAYMENT);
        if (cashCollectionReserved(bookingId)) {
            throw new AppException(ErrorCode.CONFLICT, "Tiền mặt đang được xác nhận hoặc đã thu; không tạo thêm khoản PayOS");
        }
        var quote = bookingPaymentVerifier.requireMerchantFinalPayment(bookingId, merchantUserId);
        BigDecimal amount = quote.payable(PaymentTypeEnum.FINAL_PAYMENT);
        if (amount == null || amount.signum() <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Booking không có số tiền cần thanh toán");
        }
        List<Payment> attempts = attempts(bookingId, PaymentTypeEnum.FINAL_PAYMENT);
        if (attempts.stream().anyMatch(p -> p.getGateway() == PaymentGatewayEnum.WALLET && p.getStatus() == PaymentStatusEnum.PENDING)) {
            throw new AppException(ErrorCode.CONFLICT, "Thanh toán ví đang xử lý; vui lòng kiểm tra kết quả");
        }
        attempts = attempts.stream().filter(p -> p.getGateway() != PaymentGatewayEnum.WALLET || p.getStatus() != PaymentStatusEnum.FAILED).toList();
        for (Payment old : attempts) {
            if (old.getStatus() == PaymentStatusEnum.REQUIRES_REVIEW || old.getStatus() == PaymentStatusEnum.REFUNDED) {
                throw new AppException(ErrorCode.CONFLICT, "Giao dịch cần kiểm tra; không tạo thêm khoản thu");
            }
        }
        if (attempts.stream().anyMatch(p -> p.getStatus() == PaymentStatusEnum.SUCCESS)) {
            return appendQueryParam(payOSConfig.getReturnUrl(), "bookingId", bookingId.toString());
        }

        Payment latest = attempts.isEmpty() ? null : attempts.getFirst();
        if (latest != null && latest.getStatus() == PaymentStatusEnum.PENDING && latest.getCheckoutUrl() != null
                && latest.getAmount() != null && latest.getAmount().compareTo(amount) == 0) {
            // Preserve immutable origination channel when reusing an existing pending payment link.
            return latest.getCheckoutUrl();
        }

        long orderCode = attemptGuard.nextOrderCode();
        String key = "booking:" + bookingId + ":FINAL_PAYMENT";
        Payment payment = Payment.builder()
                .bookingId(bookingId)
                .driverId(quote.driverId())
                .merchantId(merchantUserId)
                .parkingLotId(quote.parkingLotId())
                .gateway(PaymentGatewayEnum.PAYOS)
                .transactionCode(String.valueOf(orderCode))
                .orderCode(orderCode)
                .amount(amount)
                .paymentType(PaymentTypeEnum.FINAL_PAYMENT)
                .paymentChannel("COUNTER_QR")
                .status(PaymentStatusEnum.PENDING)
                .idempotencyKey(attempts.isEmpty() ? key : key + ":attempt:" + orderCode)
                .bookingExpiresAt(quote.expiresAt())
                .build();

        String returnUrl = appendQueryParam(payOSConfig.getReturnUrl(), "bookingId", bookingId.toString());
        String cancelUrl = appendQueryParam(payOSConfig.getCancelUrl(), "bookingId", bookingId.toString());
        var item = PaymentLinkItem.builder()
                .name("Booking " + bookingId)
                .quantity(1)
                .price(amount.longValueExact())
                .build();
        CreatePaymentLinkRequest.CreatePaymentLinkRequestBuilder builder = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amount.longValueExact())
                .description("Booking " + bookingId)
                .items(List.of(item))
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl);

        try {
            CreatePaymentLinkResponse response = payOS.paymentRequests().create(builder.build());
            if (response == null || response.getAmount() == null || BigDecimal.valueOf(response.getAmount()).compareTo(amount) != 0
                    || !java.util.Objects.equals(response.getOrderCode(), orderCode)
                    || response.getCheckoutUrl() == null || response.getCheckoutUrl().isBlank()) {
                throw new AppException(ErrorCode.PAYMENT_FAILED, "Số tiền hoặc mã đơn PayOS không khớp booking. Chưa mở thanh toán; vui lòng kiểm tra lại.");
            }
            payment.setCheckoutUrl(response.getCheckoutUrl());
            paymentRepository.save(payment);
            return response.getCheckoutUrl();
        } catch (AppException failure) {
            throw failure;
        } catch (Exception failure) {
            String errorDetail = failure.getMessage() != null && !failure.getMessage().isBlank()
                    ? failure.getMessage()
                    : failure.getClass().getSimpleName();
            log.error("PayOS createGateFinalPaymentLink failed for booking {} order {}: {}", bookingId, orderCode, errorDetail, failure);
            throw new AppException(ErrorCode.PAYMENT_FAILED, "Chưa xác minh được phiên PayOS (" + errorDetail + "). Vui lòng kiểm tra trạng thái; chưa tạo thêm khoản thu.");
        }
    }

    private List<Payment> attempts(Long bookingId, PaymentTypeEnum type) {
        return paymentRepository.findAttemptsForUpdate(bookingId, type).stream()
                .filter(p -> p.getPaymentType() == type).sorted(Comparator.comparing(Payment::getId).reversed()).toList();
    }

    private boolean matchesQuote(Payment p, com.smartparking.payment.infrastructure.client.BookingPaymentVerifier.Quote quote, BigDecimal amount) {
        return p.getAmount() != null && p.getAmount().compareTo(amount) == 0
                && java.util.Objects.equals(p.getMerchantId(), quote.merchantId())
                && java.util.Objects.equals(p.getParkingLotId(), quote.parkingLotId());
    }

    private PaymentLink verifiedLink(Payment payment) {
        try {
            PaymentLink link = payOS.paymentRequests().get(payment.getOrderCode());
            if (link == null) {
                log.info("PayOS payment link not found for orderCode {}", payment.getOrderCode());
                return null;
            }
            if (link.getAmount() == null || payment.getAmount() == null
                    || BigDecimal.valueOf(link.getAmount()).compareTo(payment.getAmount()) != 0
                    || !java.util.Objects.equals(link.getOrderCode(), payment.getOrderCode())) {
                throw new AppException(ErrorCode.CONFLICT, "Link PayOS không khớp số tiền hoặc mã đơn. Vui lòng kiểm tra giao dịch.");
            }
            return link;
        } catch (AppException failure) { throw failure; }
        catch (Exception failure) {
            String msg = failure.getMessage();
            if (msg != null && (msg.contains("404") || msg.toLowerCase().contains("not found") || msg.contains("không tìm thấy"))) {
                log.warn("PayOS order {} not found: {}", payment.getOrderCode(), msg);
                return null;
            }
            throw new AppException(ErrorCode.PAYMENT_FAILED, "Chưa xác minh được giao dịch PayOS. Vui lòng kiểm tra lại.");
        }
    }

    private void markReview(Payment payment, String reason) {
        payment.setStatus(PaymentStatusEnum.REQUIRES_REVIEW);
        payment.setReviewReason(reason);
        paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public PaymentDto confirmDemoDeposit(Long driverUserId, Long bookingId) {
        return confirmDemoDeposit(driverUserId, DemoPaymentRequest.builder().bookingId(bookingId).build());
    }

    @Override
    @Transactional
    public PaymentDto confirmDemoDeposit(Long driverUserId, DemoPaymentRequest request) {
        if (!demoPaymentEnabled) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thanh toán demo không được bật trên môi trường này.");
        }

        Long bookingId = request.getBookingId();
        attemptGuard.lock(bookingId, PaymentTypeEnum.DEPOSIT);
        String key = "booking:" + bookingId + ":DEPOSIT";
        var group = attempts(bookingId, PaymentTypeEnum.DEPOSIT);
        var previous = group.isEmpty() ? paymentRepository.findByIdempotencyKey(key) : java.util.Optional.of(group.getFirst());
        if (previous.isPresent()) {
            Payment prevPayment = previous.get();
            if (!driverUserId.equals(prevPayment.getDriverId())) throw new AppException(ErrorCode.FORBIDDEN, "Giao dịch không thuộc tài khoản");
            if (prevPayment.getStatus() == PaymentStatusEnum.PENDING) {
                applyPaidPayment(prevPayment);
            }
            return PaymentDto.fromEntity(prevPayment);
        }
        var quote = bookingPaymentVerifier.require(bookingId, driverUserId);
        BigDecimal amount = quote.payable(PaymentTypeEnum.DEPOSIT);
        long orderCode = attemptGuard.nextOrderCode();

        Payment payment = Payment.builder()
                .bookingId(bookingId)
                .driverId(driverUserId)
                .merchantId(quote.merchantId())
                .parkingLotId(quote.parkingLotId())
                .gateway(PaymentGatewayEnum.DEMO)
                .transactionCode("DEMO-" + bookingId + "-" + System.currentTimeMillis())
                .orderCode(orderCode)
                .amount(amount)
                .paymentType(PaymentTypeEnum.DEPOSIT)
                .paymentChannel("ONLINE_TRANSFER")
                .status(PaymentStatusEnum.SUCCESS)
                .idempotencyKey(key).bookingExpiresAt(quote.expiresAt())
                .build();

        payment.setStatus(PaymentStatusEnum.PENDING);
        Payment saved = paymentRepository.saveAndFlush(payment);
        applyPaidPayment(saved);

        return PaymentDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public PaymentDto syncPayOSPayment(Long driverUserId, Long orderCode, Long bookingId, String paymentLinkId) {
        Payment selected;
        if (orderCode != null) {
            selected = paymentRepository.findByOrderCode(orderCode)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch thanh toán"));
            if (bookingId != null && !bookingId.equals(selected.getBookingId()))
                throw new AppException(ErrorCode.BAD_REQUEST, "Mã giao dịch không thuộc vé đặt chỗ");
        } else {
            selected = paymentRepository.findByBookingId(bookingId).stream().max(Comparator.comparing(Payment::getId))
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch thanh toán để đồng bộ"));
        }
        if (!driverUserId.equals(selected.getDriverId())) throw new AppException(ErrorCode.FORBIDDEN, "Giao dịch không thuộc tài khoản");
        attemptGuard.lock(selected.getBookingId(), selected.getPaymentType());
        List<Payment> group = attempts(selected.getBookingId(), selected.getPaymentType());
        Payment current = group.isEmpty() ? selected : group.getFirst();
        // An old return link must reflect the current attempt; any reconciliation blocks further charges.
        current = group.stream().filter(p -> p.getStatus() == PaymentStatusEnum.REQUIRES_REVIEW).findFirst().orElse(current);
        Payment payment = paymentRepository.findByIdForUpdate(current.getId()).orElseThrow();
        if (payment.getGateway() == PaymentGatewayEnum.WALLET) return PaymentDto.fromEntity(payment);
        if (payment.getStatus() == PaymentStatusEnum.SUCCESS) {
            outboxPublisherService.publishPaymentCompleted(payment);
            return PaymentDto.fromEntity(payment);
        }
        if (payment.getStatus() == PaymentStatusEnum.REQUIRES_REVIEW || payment.getStatus() == PaymentStatusEnum.REFUNDED)
            return PaymentDto.fromEntity(payment);
        boolean retry = false;
        boolean resume = false;
        try {
            PaymentLink link = verifiedLink(payment);
            if (link.getAmountPaid() == null || link.getAmountPaid() < 0) return PaymentDto.fromEntity(payment);
            if (link.getStatus() == vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PAID) {
                if (BigDecimal.valueOf(link.getAmountPaid()).compareTo(payment.getAmount()) != 0)
                    markReview(payment, "Số tiền PayOS đã thu không khớp giao dịch; cần đối soát");
                else applyPaidPayment(payment);
            } else if (link.getAmountPaid() > 0) {
                markReview(payment, "Giao dịch chưa hoàn tất nhưng có tiền nhận; cần đối soát");
            } else {
                if (link.getStatus() == vn.payos.model.v2.paymentRequests.PaymentLinkStatus.CANCELLED) {
                    payment.setStatus(PaymentStatusEnum.FAILED);
                    paymentRepository.save(payment);
                }
                try {
                    var quote = bookingPaymentVerifier.require(payment.getBookingId(), driverUserId);
                    boolean payable = matchesQuote(payment, quote, quote.payable(payment.getPaymentType()));
                    retry = payosRecoveryEnabled && payable && link.getStatus() == vn.payos.model.v2.paymentRequests.PaymentLinkStatus.CANCELLED;
                    resume = payable && payment.getStatus() == PaymentStatusEnum.PENDING && payment.getCheckoutUrl() != null
                            && link.getStatus() == vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PENDING;
                } catch (AppException failure) {
                    // Expired booking or unavailable ownership proof grants no recovery action.
                    log.debug("Payment recovery unavailable for booking {}", payment.getBookingId());
                }
            }
        } catch (Exception failure) {
            log.warn("Could not verify PayOS outcome for order {}", payment.getOrderCode());
        }
        PaymentDto dto = PaymentDto.fromEntity(payment);
        dto.setCanRetry(retry); dto.setCanResume(resume);
        return dto;
    }

    @Override
    @Transactional
    public void handleWebhook(String webhookBody) {
        if (webhookBody == null || webhookBody.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Webhook body must not be empty.");
        }

        try {
            Webhook webhook = objectMapper.readValue(webhookBody, Webhook.class);
            WebhookData webhookData = payOS.webhooks().verify(webhook);

            String orderCodeStr = String.valueOf(webhookData.getOrderCode());
            String code = webhookData.getCode(); // "00" = SUCCESS
            log.info("Webhook verified: orderCode={} code={}", orderCodeStr, code);

            paymentRepository.findByOrderCode(webhookData.getOrderCode()).ifPresent(found -> {
                attemptGuard.lock(found.getBookingId(), found.getPaymentType());
                Payment payment = paymentRepository.findByIdForUpdate(found.getId()).orElseThrow();
                if (payment.getStatus() == PaymentStatusEnum.REQUIRES_REVIEW || payment.getStatus() == PaymentStatusEnum.SUCCESS
                        || payment.getStatus() == PaymentStatusEnum.REFUNDED) {
                    log.warn("Webhook duplicate skipped for orderCode: {}", orderCodeStr);
                    return;
                }

                if ("00".equals(code)) {
                    if (webhookData.getAmount() == null || BigDecimal.valueOf(webhookData.getAmount()).compareTo(payment.getAmount()) != 0 || !"VND".equals(webhookData.getCurrency())) {
                        payment.setStatus(PaymentStatusEnum.REQUIRES_REVIEW);
                        payment.setReviewReason("Số tiền hoặc loại tiền webhook không khớp giao dịch; cần đối soát");
                        paymentRepository.save(payment);
                        return;
                    }
                    applyPaidPayment(payment);
                } else {
                    payment.setStatus(PaymentStatusEnum.FAILED);
                    paymentRepository.save(payment);
                    outboxPublisherService.publishPaymentFailed(payment, "Giao dịch bị từ chối bởi cổng thanh toán");
                }
            });
        } catch (Exception e) {
            log.warn("Rejected invalid PayOS webhook: {}", e.getMessage());
            throw new AppException(ErrorCode.BAD_REQUEST, "Invalid PayOS webhook signature");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentDto> getMyPayments(Long driverUserId, Long bookingId, Pageable pageable) {
        Page<Payment> payments = bookingId == null
                ? paymentRepository.findByDriverId(driverUserId, pageable)
                : paymentRepository.findByDriverIdAndBookingId(driverUserId, bookingId, pageable);
        Page<PaymentDto> page = payments.map(PaymentDto::fromEntity);
        if (refundRequests != null && !page.isEmpty()) {
            var byPayment = refundRequests.findByPaymentIdIn(page.getContent().stream().map(PaymentDto::getId).toList()).stream()
                    .collect(java.util.stream.Collectors.toMap(com.smartparking.payment.domain.model.RefundRequest::getPaymentId, r -> r));
            page.forEach(dto -> {
                var refund = byPayment.get(dto.getId());
                if (refund != null) { dto.setRefundAmount(refund.getRefundAmount()); dto.setRefundStatus(refund.getStatus());
                    dto.setRefundChannel(refund.getRefundChannel()); dto.setRefundReference(refund.getTransactionReference()); }
            });
        }
        return new PageResponse<>(page);
    }

    private void applyPaidPayment(Payment payment) {
        payment = paymentRepository.findByIdForUpdate(payment.getId()).orElseThrow();
        if (payment.getStatus() == PaymentStatusEnum.SUCCESS || payment.getStatus() == PaymentStatusEnum.REQUIRES_REVIEW
                || payment.getStatus() == PaymentStatusEnum.REFUNDED) return;
        if (payment.getPaymentType() == PaymentTypeEnum.FINAL_PAYMENT && cashCollectionReserved(payment.getBookingId())) {
            markReview(payment, "PayOS đến sau khi chọn thu tiền mặt; cần đối soát, không đóng lượt đỗ lần nữa");
            return;
        }
        final Long currentId = payment.getId();
        if (attempts(payment.getBookingId(), payment.getPaymentType()).stream().anyMatch(other ->
                !other.getId().equals(currentId) && (other.getStatus() == PaymentStatusEnum.SUCCESS
                || other.getStatus() == PaymentStatusEnum.REQUIRES_REVIEW || other.getId() > currentId))) {
            markReview(payment, "Giao dịch cũ hoặc trùng lần thanh toán; cần đối soát, không ghi nhận tiền hai lần");
            return;
        }
        if (!bookingPaymentVerifier.confirm(payment)) {
            payment.setStatus(PaymentStatusEnum.REQUIRES_REVIEW);
            payment.setReviewReason("Thanh toán đến sau hạn giữ chỗ; cần đối soát, không tự cấp lại slot");
            paymentRepository.save(payment);
            return;
        }
        payment.setStatus(PaymentStatusEnum.SUCCESS);
        paymentRepository.save(payment);

        if (payment.getMerchantId() != null) {
            merchantWalletService.recordPaymentEscrow(
                    payment,
                    "Thu hộ tạm giữ booking #" + payment.getBookingId());
        }

        outboxPublisherService.publishPaymentCompleted(payment);
        log.info("Payment {} applied SUCCESS for booking {}", payment.getId(), payment.getBookingId());
    }

    private boolean cashCollectionReserved(Long bookingId) {
        // A sync/webhook may have read payment before waiting on the group lock. Use a current
        // locking read, not an older MySQL REPEATABLE_READ snapshot, for the cash decision.
        return cashReceipts.findCurrentForUpdate(bookingId).filter(receipt -> !"REJECTED".equals(receipt.getStatus())).isPresent();
    }

    @org.springframework.lang.NonNull
    private static String appendQueryParam(String url, String key, String value) {
        String safeUrl = (url != null && !url.isBlank()) ? url : "http://localhost:3000";
        String sep = safeUrl.contains("?") ? "&" : "?";
        return safeUrl + sep + key + "=" + (value != null ? value : "");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminTransactionResponse> getAllTransactionsForAdmin(Pageable pageable) {
        Page<AdminTransactionResponse> page = paymentRepository.findAll(pageable)
                .map(AdminTransactionResponse::from);
        return new PageResponse<>(page);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminTransactionResponse getTransactionForAdmin(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Giao dịch không tồn tại"));
        return AdminTransactionResponse.from(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardStats() {
        long totalTransactions = paymentRepository.count();
        long pendingWithdrawals = withdrawalRequestRepository.countByStatus(WithdrawalStatusEnum.PENDING);
        long openDisputes = disputeRepository.countByStatus(DisputeStatusEnum.PENDING);
        BigDecimal feeSum = walletLedgerEntryRepository.sumTotalPlatformFee();
        double platformRevenue = feeSum != null ? feeSum.doubleValue() : 0.0;

        return AdminDashboardResponse.builder()
                .totalUsers(0)
                .totalDrivers(0)
                .totalMerchants(0)
                .totalParkingLots(0)
                .pendingParkingLots(0)
                .totalBookings(totalTransactions)
                .pendingWithdrawals(pendingWithdrawals)
                .openDisputes(openDisputes)
                .platformRevenue(platformRevenue)
                .build();
    }
}

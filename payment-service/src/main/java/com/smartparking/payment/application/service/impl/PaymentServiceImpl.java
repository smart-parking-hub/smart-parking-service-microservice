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
    private final MerchantWalletService merchantWalletService;
    private final OutboxPublisherService outboxPublisherService;
    private final ObjectMapper objectMapper;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final DisputeRepository disputeRepository;
    private final WalletLedgerEntryRepository walletLedgerEntryRepository;

    @Value("${app.demo-payment-enabled:false}")
    private boolean demoPaymentEnabled;

    @Override
    @Transactional
    public String createPaymentLink(Long driverUserId, CreatePaymentRequest request) {
        PaymentTypeEnum type;
        try { type = PaymentTypeEnum.valueOf(request.getPaymentType()); }
        catch (RuntimeException failure) { throw new AppException(ErrorCode.BAD_REQUEST, "Loại thanh toán không hợp lệ"); }
        String key = "booking:" + request.getBookingId() + ":" + type.name();
        var previous = paymentRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            Payment old = previous.get();
            if (!driverUserId.equals(old.getDriverId())) throw new AppException(ErrorCode.FORBIDDEN, "Giao dịch không thuộc tài khoản");
            if (old.getStatus() == PaymentStatusEnum.SUCCESS) return appendQueryParam(payOSConfig.getReturnUrl(), "bookingId", old.getBookingId().toString());
            if (old.getCheckoutUrl() != null && old.getStatus() == PaymentStatusEnum.PENDING && (old.getBookingExpiresAt() == null || old.getBookingExpiresAt().isAfter(java.time.LocalDateTime.now()))) return old.getCheckoutUrl();
            throw new AppException(ErrorCode.BAD_REQUEST, "Giao dịch đã hết hạn hoặc cần đối soát; không tạo thêm khoản thu");
        }
        var quote = bookingPaymentVerifier.require(request.getBookingId(), driverUserId);
        BigDecimal amount = quote.payable(type);
        if (amount == null || amount.signum() <= 0) throw new AppException(ErrorCode.BAD_REQUEST, "Booking không có số tiền cần thanh toán");
        long orderCode = Math.addExact(Math.multiplyExact(request.getBookingId(), 10L), type.ordinal() + 1L);
        String description = "Booking " + request.getBookingId();

        Payment payment = Payment.builder()
                .bookingId(request.getBookingId())
                .driverId(driverUserId)
                .merchantId(quote.merchantId())
                .parkingLotId(quote.parkingLotId())
                .gateway(PaymentGatewayEnum.PAYOS)
                .transactionCode(String.valueOf(orderCode))
                .orderCode(orderCode)
                .amount(amount)
                .paymentType(type)
                .status(PaymentStatusEnum.PENDING)
                .idempotencyKey(key).bookingExpiresAt(type == PaymentTypeEnum.DEPOSIT ? quote.expiresAt() : null)
                .build();

        paymentRepository.saveAndFlush(payment);

        String returnUrl = appendQueryParam(payOSConfig.getReturnUrl(), "bookingId", String.valueOf(request.getBookingId()));
        String cancelUrl = appendQueryParam(payOSConfig.getCancelUrl(), "bookingId", String.valueOf(request.getBookingId()));

        try {
            PaymentLinkItem item = PaymentLinkItem.builder()
                    .name(description)
                    .quantity(1)
                    .price(amount.longValue())
                    .build();

            CreatePaymentLinkRequest linkRequest = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(amount.longValueExact())
                    .description(description)
                    .items(List.of(item))
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .build();

            CreatePaymentLinkResponse result = payOS.paymentRequests().create(linkRequest);
            String checkoutUrl = result.getCheckoutUrl();
            log.info("PayOS link created for booking {}: {}", request.getBookingId(), checkoutUrl);
            payment.setCheckoutUrl(checkoutUrl);
            paymentRepository.save(payment);
            return checkoutUrl;
        } catch (Exception e) {
            log.warn("PayOS link unavailable for booking {}", request.getBookingId());
            throw new AppException(ErrorCode.PAYMENT_FAILED, "Chưa tạo được thanh toán PayOS. Vui lòng thử lại; chưa xác nhận giữ chỗ.");
        }
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
        String key = "booking:" + bookingId + ":DEPOSIT";
        var previous = paymentRepository.findByIdempotencyKey(key);
        if (previous.isPresent()) {
            if (!driverUserId.equals(previous.get().getDriverId())) throw new AppException(ErrorCode.FORBIDDEN, "Giao dịch không thuộc tài khoản");
            return PaymentDto.fromEntity(previous.get());
        }
        var quote = bookingPaymentVerifier.require(bookingId, driverUserId);
        BigDecimal amount = quote.payable(PaymentTypeEnum.DEPOSIT);
        long orderCode = Math.addExact(Math.multiplyExact(bookingId, 10L), 1L);

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
        Payment payment = null;
        if (orderCode != null) {
            payment = paymentRepository.findByOrderCode(orderCode).orElse(null);
        }
        if (payment == null && bookingId != null) {
            payment = paymentRepository.findByBookingId(bookingId).stream()
                    .sorted(Comparator.comparing(Payment::getId).reversed())
                    .findFirst()
                    .orElseGet(() -> paymentRepository.findByBookingId(bookingId).stream()
                            .max(java.util.Comparator.comparing(Payment::getId)).orElse(null));
        }

        if (payment == null) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch thanh toán để đồng bộ");
        }

        payment = paymentRepository.findByIdForUpdate(payment.getId()).orElseThrow();
        if (!driverUserId.equals(payment.getDriverId())) throw new AppException(ErrorCode.FORBIDDEN, "Giao dịch không thuộc tài khoản");
        if (payment.getStatus() == PaymentStatusEnum.SUCCESS) {
            outboxPublisherService.publishPaymentCompleted(payment);
            return PaymentDto.fromEntity(payment);
        }

        try {
            PaymentLink link = payOS.paymentRequests().get(payment.getOrderCode());
            if (link != null && link.getStatus() == vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PAID) {
                if (link.getAmountPaid() == null || BigDecimal.valueOf(link.getAmountPaid()).compareTo(payment.getAmount()) != 0) {
                    payment.setStatus(PaymentStatusEnum.REQUIRES_REVIEW);
                    payment.setReviewReason("Số tiền PayOS đã thu không khớp giao dịch; cần đối soát");
                    paymentRepository.save(payment);
                } else applyPaidPayment(payment);
            }
        } catch (Exception ex) {
            log.warn("Could not query PayOS link for orderCode={}: {}", payment.getOrderCode(), ex.getMessage());
        }

        return PaymentDto.fromEntity(payment);
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
                Payment payment = paymentRepository.findByIdForUpdate(found.getId()).orElseThrow();
                if (payment.getStatus() == PaymentStatusEnum.REQUIRES_REVIEW || payment.getStatus() == PaymentStatusEnum.SUCCESS) {
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
    public PageResponse<PaymentDto> getMyPayments(Long driverUserId, Pageable pageable) {
        Page<PaymentDto> page = paymentRepository.findByDriverId(driverUserId, pageable)
                .map(PaymentDto::fromEntity);
        return new PageResponse<>(page);
    }

    private void applyPaidPayment(Payment payment) {
        payment = paymentRepository.findByIdForUpdate(payment.getId()).orElseThrow();
        if (payment.getStatus() == PaymentStatusEnum.SUCCESS || payment.getStatus() == PaymentStatusEnum.REQUIRES_REVIEW) return;
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

    private static String appendQueryParam(String url, String key, String value) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + key + "=" + value;
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

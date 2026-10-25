package com.smartparking.payment.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.PaymentGatewayEnum;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.PaymentTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.CreatePaymentRequest;
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

    private final PayOS payOS;
    private final PayOSConfig payOSConfig;
    private final PaymentRepository paymentRepository;
    private final MerchantWalletService merchantWalletService;
    private final OutboxPublisherService outboxPublisherService;
    private final ObjectMapper objectMapper;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final DisputeRepository disputeRepository;
    private final WalletLedgerEntryRepository walletLedgerEntryRepository;

    @Value("${app.demo-payment-enabled:true}")
    private boolean demoPaymentEnabled;

    @Override
    @Transactional
    public String createPaymentLink(Long driverUserId, CreatePaymentRequest request) {
        PaymentTypeEnum type = PaymentTypeEnum.valueOf(request.getPaymentType());
        BigDecimal amount = request.getAmount() != null && request.getAmount().compareTo(BigDecimal.ZERO) > 0
                ? request.getAmount()
                : BigDecimal.valueOf(50000); // Default deposit or passed amount

        long orderCode = Long.parseLong(request.getBookingId() + "" + (System.currentTimeMillis() % 100000));
        String description = (type == PaymentTypeEnum.DEPOSIT ? "Coc giu cho " : "Thanh toan ") + request.getBookingId();

        Payment payment = Payment.builder()
                .bookingId(request.getBookingId())
                .driverId(driverUserId)
                .merchantId(request.getMerchantId())
                .parkingLotId(request.getParkingLotId())
                .gateway(PaymentGatewayEnum.PAYOS)
                .transactionCode(String.valueOf(orderCode))
                .orderCode(orderCode)
                .amount(amount)
                .paymentType(type)
                .status(PaymentStatusEnum.PENDING)
                .build();

        paymentRepository.save(payment);

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
                    .amount((long) amount.intValue())
                    .description(description)
                    .items(List.of(item))
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .build();

            CreatePaymentLinkResponse result = payOS.paymentRequests().create(linkRequest);
            String checkoutUrl = result.getCheckoutUrl();
            log.info("PayOS link created for booking {}: {}", request.getBookingId(), checkoutUrl);
            return checkoutUrl;
        } catch (Exception e) {
            log.warn("PayOS create link failed: {}. Fallback to simulated checkout.", e.getMessage());
            return returnUrl + "&simulated=true&orderCode=" + orderCode;
        }
    }

    @Override
    @Transactional
    public PaymentDto confirmDemoDeposit(Long driverUserId, Long bookingId) {
        if (!demoPaymentEnabled) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Thanh toán demo không được bật trên môi trường này.");
        }

        boolean alreadyPaid = paymentRepository.existsByBookingIdAndPaymentTypeAndStatus(
                bookingId, PaymentTypeEnum.DEPOSIT, PaymentStatusEnum.SUCCESS);
        if (alreadyPaid) {
            return paymentRepository.findByBookingId(bookingId).stream()
                    .filter(p -> p.getPaymentType() == PaymentTypeEnum.DEPOSIT && p.getStatus() == PaymentStatusEnum.SUCCESS)
                    .findFirst()
                    .map(PaymentDto::fromEntity)
                    .orElseThrow();
        }

        long orderCode = Long.parseLong(bookingId + "" + (System.currentTimeMillis() % 100000));
        BigDecimal amount = BigDecimal.valueOf(50000);

        Payment payment = Payment.builder()
                .bookingId(bookingId)
                .driverId(driverUserId)
                .gateway(PaymentGatewayEnum.DEMO)
                .transactionCode("DEMO-" + bookingId + "-" + System.currentTimeMillis())
                .orderCode(orderCode)
                .amount(amount)
                .paymentType(PaymentTypeEnum.DEPOSIT)
                .status(PaymentStatusEnum.SUCCESS)
                .build();

        Payment saved = paymentRepository.save(payment);
        outboxPublisherService.publishPaymentCompleted(saved);

        log.info("Demo payment confirmed for booking {}", bookingId);
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
                    .filter(p -> p.getStatus() == PaymentStatusEnum.PENDING)
                    .findFirst()
                    .orElse(null);
        }

        if (payment == null) {
            throw new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy giao dịch thanh toán để đồng bộ");
        }

        if (payment.getStatus() == PaymentStatusEnum.SUCCESS) {
            return PaymentDto.fromEntity(payment);
        }

        try {
            PaymentLink link = payOS.paymentRequests().get(payment.getOrderCode());
            if (link != null && link.getStatus() == vn.payos.model.v2.paymentRequests.PaymentLinkStatus.PAID) {
                applyPaidPayment(payment);
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

            paymentRepository.findByOrderCode(webhookData.getOrderCode()).ifPresent(payment -> {
                if (payment.getStatus() == PaymentStatusEnum.SUCCESS) {
                    log.warn("Webhook duplicate skipped for orderCode: {}", orderCodeStr);
                    return;
                }

                if ("00".equals(code)) {
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
        payment.setStatus(PaymentStatusEnum.SUCCESS);
        paymentRepository.save(payment);

        if (payment.getMerchantId() != null) {
            merchantWalletService.creditSettlement(
                    payment.getMerchantId(),
                    payment.getBookingId(),
                    payment,
                    payment.getAmount(),
                    "Thanh toán booking #" + payment.getBookingId());
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

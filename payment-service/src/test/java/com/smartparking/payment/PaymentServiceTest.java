package com.smartparking.payment;

import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.common.enums.PaymentGatewayEnum;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.WithdrawalStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.payment.application.dto.*;
import com.smartparking.payment.application.service.DisputeService;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.application.service.PaymentService;
import com.smartparking.payment.application.service.WithdrawalService;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.OutboxEventRepository;
import com.smartparking.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class PaymentServiceTest {

    @MockBean
    private com.smartparking.payment.infrastructure.client.BookingPaymentVerifier bookingPaymentVerifier;

    @MockBean
    private com.smartparking.payment.infrastructure.client.DisputeBookingClient disputeBookingClient;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private MerchantWalletService merchantWalletService;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private DisputeService disputeService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final Long driverUserId = 200L;
    private final Long merchantUserId = 300L;
    private final Long bookingId = 500L;

    @org.junit.jupiter.api.BeforeEach
    void verifiedBooking() {
        org.mockito.Mockito.lenient().when(bookingPaymentVerifier.require(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
            .thenAnswer(inv -> new com.smartparking.payment.infrastructure.client.BookingPaymentVerifier.Quote(inv.getArgument(0), inv.getArgument(1), merchantUserId, 10L, "PENDING_PAYMENT", new BigDecimal("50000"), new BigDecimal("250000"), java.time.LocalDateTime.now().plusMinutes(15)));
        org.mockito.Mockito.lenient().when(bookingPaymentVerifier.confirm(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(disputeBookingClient.verifyBookingOwnership(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
            .thenAnswer(inv -> new com.smartparking.payment.infrastructure.client.DisputeBookingClient.VerifiedBooking(inv.getArgument(0), inv.getArgument(1), merchantUserId, 10L));
    }

    @Test
    @DisplayName("Confirm demo payment creates SUCCESS payment and outbox event")
    void testConfirmDemoPayment() {
        PaymentDto payment = paymentService.confirmDemoDeposit(driverUserId, bookingId);

        assertNotNull(payment);
        assertNotNull(payment.getId());
        assertEquals(bookingId, payment.getBookingId());
        assertEquals(driverUserId, payment.getDriverId());
        assertEquals(PaymentGatewayEnum.DEMO, payment.getGateway());
        assertEquals(PaymentStatusEnum.SUCCESS, payment.getStatus());

        // Verify payment is persisted
        assertTrue(paymentRepository.findById(payment.getId()).isPresent());

        // Verify outbox event is stored for asynchronous Kafka dispatch
        assertFalse(outboxEventRepository.findAll().isEmpty());
    }

    @Test
    @DisplayName("Double-entry merchant ledger split, credit, hold, and refund")
    void testMerchantWalletLedger() {
        // Test split calculation (100,000 VND, 10% fee -> 10,000 fee, 90,000 net)
        BigDecimal gross = BigDecimal.valueOf(100000);
        SettlementSplit split = merchantWalletService.split(gross);
        assertEquals(new BigDecimal("10000.00"), split.getFeeAmount());
        assertEquals(new BigDecimal("90000.00"), split.getNetAmount());

        // Credit settlement to merchant
        Payment payment = Payment.builder()
                .bookingId(bookingId)
                .driverId(driverUserId)
                .merchantId(merchantUserId)
                .amount(gross)
                .gateway(PaymentGatewayEnum.DEMO)
                .paymentType(com.smartparking.common.enums.PaymentTypeEnum.DEPOSIT)
                .status(PaymentStatusEnum.SUCCESS)
                .build();
        payment = paymentRepository.save(payment);

        merchantWalletService.creditSettlement(merchantUserId, bookingId, payment, gross, "Settle test");
        BigDecimal balance = merchantWalletService.getWalletBalance(merchantUserId);
        assertEquals(new BigDecimal("90000.00"), balance);

        // Hold withdrawal of 50,000 VND
        merchantWalletService.holdWithdrawal(merchantUserId, BigDecimal.valueOf(50000), "Hold test");
        BigDecimal balanceAfterHold = merchantWalletService.getWalletBalance(merchantUserId);
        assertEquals(new BigDecimal("40000.00"), balanceAfterHold);

        // Over-withdrawing more than 40,000 should fail
        assertThrows(AppException.class, () ->
                merchantWalletService.holdWithdrawal(merchantUserId, BigDecimal.valueOf(50000), "Over hold"));

        // Refund withdrawal of 50,000 VND
        merchantWalletService.refundWithdrawal(merchantUserId, BigDecimal.valueOf(50000), "Refund test");
        BigDecimal balanceAfterRefund = merchantWalletService.getWalletBalance(merchantUserId);
        assertEquals(new BigDecimal("90000.00"), balanceAfterRefund);
    }

    @Test
    @DisplayName("Merchant request withdrawal and process approval/rejection")
    void testWithdrawalLifecycle() {
        // Credit 100,000 VND to wallet
        merchantWalletService.creditSettlement(merchantUserId, bookingId, null, BigDecimal.valueOf(100000), "Setup");

        CreateWithdrawalRequestDto req = CreateWithdrawalRequestDto.builder()
                .amount(BigDecimal.valueOf(60000))
                .build();

        WithdrawalResponse created = withdrawalService.requestWithdrawal(merchantUserId, req);
        assertNotNull(created.getId());
        assertEquals(WithdrawalStatusEnum.PENDING, created.getStatus());
        assertEquals(new BigDecimal("30000.00"), created.getAvailableBalance());

        // Admin rejects -> refunds 60,000 back to balance
        WithdrawalResponse processed = withdrawalService.processWithdrawal(1L, created.getId(), false, "Thông tin STK sai");
        assertEquals(WithdrawalStatusEnum.REJECTED, processed.getStatus());
        assertEquals(new BigDecimal("90000.00"), processed.getAvailableBalance());
    }

    @Test
    @DisplayName("Dispute creation, merchant response, and resolution lifecycle")
    void testDisputeLifecycle() {
        CreateDisputeRequest req = CreateDisputeRequest.builder()
                .bookingId(bookingId)
                .reason("Bãi xe thu thêm phí ngoài quy định")
                .description("Bảo vệ đòi thêm 50k khi lấy xe")
                .evidenceUrls(List.of("https://cdn.example.com/receipt.jpg"))
                .build();

        DisputeDto dispute = disputeService.createDispute(driverUserId, req);
        assertNotNull(dispute.getId());
        assertEquals(DriverDisputeStatus.OPEN, dispute.getStatus());
        assertEquals(merchantUserId, dispute.getMerchantId());
        assertEquals(10L, dispute.getParkingLotId());
        assertEquals(List.of("https://cdn.example.com/receipt.jpg"), dispute.getEvidenceUrls());

        disputeService.addMerchantResponse(merchantUserId, dispute.getId(),
                new CreateDisputeResponseRequest("Bãi xe đã kiểm tra biên lai và xác nhận."));
        DisputeDetailDto inReviewDetail = disputeService.getMyDisputeDetail(driverUserId, dispute.getId());
        assertEquals(DriverDisputeStatus.IN_REVIEW, inReviewDetail.getStatus());
        assertEquals(1, inReviewDetail.getTimeline().size());

        // Admin resolves dispute
        AdminDisputeResponse resolved = disputeService.resolveDispute(1L, dispute.getId(), "Đã hoàn tiền cho khách");
        assertEquals(DisputeStatusEnum.RESOLVED, resolved.getStatus());
        assertEquals("Đã hoàn tiền cho khách", resolved.getResolution());

        DisputeDetailDto finalDetail = disputeService.getMyDisputeDetail(driverUserId, dispute.getId());
        assertEquals(DriverDisputeStatus.RESOLVED, finalDetail.getStatus());
        assertEquals(2, finalDetail.getTimeline().size());
    }
}

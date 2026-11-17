package com.smartparking.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.dto.CashCheckoutCommand;
import com.smartparking.common.enums.*;
import com.smartparking.common.exception.AppException;
import com.smartparking.payment.application.dto.CreatePaymentRequest;
import com.smartparking.payment.application.service.*;
import com.smartparking.payment.application.service.impl.PaymentServiceImpl;
import com.smartparking.payment.config.PayOSConfig;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.*;
import com.smartparking.payment.infrastructure.client.BookingPaymentVerifier;
import com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import java.math.BigDecimal;
import java.util.concurrent.*;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:cashcheckout;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CashCheckoutService.class, PaymentAttemptGuard.class, PaymentServiceImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CashCheckoutIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("CHECKOUT_MYSQL_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            registry.add("spring.datasource.username", () -> "root");
            registry.add("spring.datasource.password", () -> "");
        }
    }
    @Autowired CashCheckoutService cash;
    @Autowired PaymentServiceImpl service;
    @Autowired CashCheckoutReceiptRepository receipts;
    @Autowired PaymentRepository payments;
    @Autowired LedgerJournalRepository journals;
    @Autowired JdbcTemplate jdbc;
    @MockBean BookingPaymentVerifier booking;
    @MockBean(answer = Answers.RETURNS_DEEP_STUBS) PayOS payOS;
    @MockBean PayOSConfig config;
    @MockBean MerchantWalletService wallet;
    @MockBean OutboxPublisherService outbox;
    @MockBean ObjectMapper mapper;
    private static final long ID = 9001L;
    private CashCheckoutCommand command() { return new CashCheckoutCommand(8L, 9L, new BigDecimal("32000")); }

    @BeforeEach void clean() {
        receipts.deleteAll(); payments.deleteAll();
        jdbc.execute("CREATE TABLE IF NOT EXISTS payment_attempt_groups(group_key VARCHAR(150) PRIMARY KEY)");
    }

    @Test void committedReservationSurvivesTimeoutAndBlocksNewPayOsUntilRecovery() {
        when(booking.confirmCash(eq(ID), any())).thenThrow(new IllegalStateException("RPC timed out"));
        assertThrows(IllegalStateException.class, () -> cash.confirm(ID, command()));
        assertEquals("PENDING", receipts.findById(ID).orElseThrow().getStatus());
        assertThrows(AppException.class, () -> service.createPaymentLink(7L,
                CreatePaymentRequest.builder().bookingId(ID).paymentType("FINAL_PAYMENT").build()));
        verify(booking, never()).require(any(), any());
        doReturn(true).when(booking).confirmCash(eq(ID), any());
        assertTrue(cash.retry(ID));
        assertTrue(cash.confirm(ID, command()));
        assertEquals("COMPLETED", receipts.findById(ID).orElseThrow().getStatus());
        verify(booking, times(2)).confirmCash(eq(ID), any());
        assertEquals(0, payments.count()); assertEquals(0, journals.count());
        verifyNoInteractions(wallet, outbox);
    }

    @ParameterizedTest
    @CsvSource({"PENDING,0,false", "PAID,32000,false", "CANCELLED,1,false", "CANCELLED,0,true"})
    void requiresAuthoritativeCancelledAndUnpaidProviderOrder(String providerStatus, Long paid, boolean allowed) {
        var payment = payments.saveAndFlush(Payment.builder().bookingId(ID).driverId(7L).merchantId(8L).parkingLotId(9L)
                .paymentType(PaymentTypeEnum.FINAL_PAYMENT).gateway(PaymentGatewayEnum.PAYOS)
                .orderCode(991L).amount(new BigDecimal("32000")).status(PaymentStatusEnum.PENDING).build());
        var link = mock(PaymentLink.class);
        when(link.getOrderCode()).thenReturn(991L); when(link.getAmount()).thenReturn(32000L);
        when(link.getStatus()).thenReturn(PaymentLinkStatus.valueOf(providerStatus)); when(link.getAmountPaid()).thenReturn(paid);
        when(payOS.paymentRequests().get(991L)).thenReturn(link);
        if (allowed) {
            when(booking.confirmCash(eq(ID), any())).thenReturn(true);
            assertTrue(cash.confirm(ID, command()));
        } else {
            assertThrows(AppException.class, () -> cash.confirm(ID, command()));
            assertFalse(receipts.existsById(ID)); verify(booking, never()).confirmCash(any(), any());
        }
        assertEquals(PaymentStatusEnum.PENDING, payments.findById(payment.getId()).orElseThrow().getStatus());
    }

    @Test void providerFailureCannotSwitchToCash() {
        payments.saveAndFlush(Payment.builder().bookingId(ID).driverId(7L).paymentType(PaymentTypeEnum.FINAL_PAYMENT)
                .gateway(PaymentGatewayEnum.PAYOS).orderCode(991L).amount(new BigDecimal("32000"))
                .status(PaymentStatusEnum.FAILED).build());
        when(payOS.paymentRequests().get(991L)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(AppException.class, () -> cash.confirm(ID, command())); assertFalse(receipts.existsById(ID));
    }

    @Test void explicitBookingRejectionDoesNotReportSuccess() {
        when(booking.confirmCash(eq(ID), any())).thenReturn(false);
        assertFalse(cash.confirm(ID, command())); assertEquals("REJECTED", receipts.findById(ID).orElseThrow().getStatus());
    }

    @Test void simultaneousCashRequestsKeepOneReceiptWhilePayOsIsBlocked() throws Exception {
        var entered = new CountDownLatch(2); var finish = new CountDownLatch(1);
        when(booking.confirmCash(eq(ID), any())).thenAnswer(inv -> {
            entered.countDown(); assertTrue(finish.await(10, TimeUnit.SECONDS)); return true;
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> cash.confirm(ID, command())); var second = pool.submit(() -> cash.confirm(ID, command()));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS)); assertEquals(1, receipts.count());
                assertThrows(AppException.class, () -> service.createPaymentLink(7L,
                        CreatePaymentRequest.builder().bookingId(ID).paymentType("FINAL_PAYMENT").build()));
            } finally { finish.countDown(); }
            assertTrue(first.get(15, TimeUnit.SECONDS)); assertTrue(second.get(15, TimeUnit.SECONDS));
        }
        assertEquals("COMPLETED", receipts.findById(ID).orElseThrow().getStatus());
    }

    @Test void changedAmountOrMerchantCannotReuseReservation() {
        when(booking.confirmCash(eq(ID), any())).thenThrow(new IllegalStateException("timeout"));
        assertThrows(IllegalStateException.class, () -> cash.confirm(ID, command()));
        assertThrows(AppException.class, () -> cash.confirm(ID, new CashCheckoutCommand(8L, 9L, new BigDecimal("31000"))));
        assertThrows(AppException.class, () -> cash.confirm(ID, new CashCheckoutCommand(88L, 9L, new BigDecimal("32000"))));
        verify(booking, times(1)).confirmCash(any(), any());
    }

    @Test void paidWebhookAfterCashIsRetainedForReconciliationWithoutEscrowOrAnotherBookingConfirmation() throws Exception {
        when(booking.confirmCash(eq(ID), any())).thenReturn(true);
        assertTrue(cash.confirm(ID, command()));
        var payment = payments.saveAndFlush(Payment.builder().bookingId(ID).driverId(7L).merchantId(8L).parkingLotId(9L)
                .paymentType(PaymentTypeEnum.FINAL_PAYMENT).gateway(PaymentGatewayEnum.PAYOS).orderCode(991L)
                .amount(new BigDecimal("32000")).status(PaymentStatusEnum.FAILED).build());
        var webhook = mock(vn.payos.model.webhooks.Webhook.class);
        var data = mock(vn.payos.model.webhooks.WebhookData.class);
        when(mapper.readValue("signed", vn.payos.model.webhooks.Webhook.class)).thenReturn(webhook);
        when(payOS.webhooks().verify(webhook)).thenReturn(data);
        when(data.getOrderCode()).thenReturn(991L); when(data.getCode()).thenReturn("00");
        when(data.getAmount()).thenReturn(32000L); when(data.getCurrency()).thenReturn("VND");
        service.handleWebhook("signed");
        assertEquals(PaymentStatusEnum.REQUIRES_REVIEW, payments.findById(payment.getId()).orElseThrow().getStatus());
        verify(booking, never()).confirm(any()); verifyNoInteractions(wallet, outbox);
        assertEquals("COMPLETED", receipts.findById(ID).orElseThrow().getStatus());
    }
}

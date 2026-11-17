package com.smartparking.payment;

import com.smartparking.common.enums.*;
import com.smartparking.common.exception.AppException;
import com.smartparking.payment.application.service.*;
import com.smartparking.payment.domain.model.*;
import com.smartparking.payment.domain.repository.*;
import com.smartparking.payment.infrastructure.client.BookingPaymentVerifier;
import com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static com.smartparking.payment.domain.model.LedgerAccountCode.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:driverwallet;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
        "app.wallet-recovery-delay-ms=3600000", "app.driver-wallet-enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DriverWalletLedger.class, DriverWalletPaymentService.class, PaymentAttemptGuard.class, com.smartparking.payment.application.service.impl.RefundServiceImpl.class,
        com.smartparking.payment.application.service.impl.MerchantWalletServiceImpl.class,
        com.smartparking.payment.application.service.impl.DailySettlementServiceImpl.class, DepositStatementService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DriverWalletIntegrationTest {
    @Autowired DriverWalletLedger wallet;
    @Autowired DriverWalletPaymentService service;
    @Autowired PaymentRepository payments;
    @Autowired LedgerJournalRepository journals;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockBean BookingPaymentVerifier booking;
    @MockBean OutboxPublisherService outbox;
    @Autowired com.smartparking.payment.application.service.impl.RefundServiceImpl refunds;
    @Autowired MerchantWalletService merchant;
    @Autowired DepositStatementService statements;
    @Autowired DailySettlementService settlement;

    @Test void concurrentCatchupAndOriginalDayRunsCreditAndCountThePaymentOnce() throws Exception {
        long driver = 5400L;
        var day = java.time.LocalDate.of(2020, 9, 1);
        seed(driver, 54000L, "16000");
        quote(54001L, driver, "16000");
        when(booking.confirmWallet(any())).thenReturn(true);
        var paid = service.pay(driver, 54001L);
        jdbc.update("UPDATE payments SET created_at=? WHERE id=?", day.atTime(23, 59), paid.getId());
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { ready.countDown(); start.await(); return settlement.runDailySettlement(day); });
            var second = pool.submit(() -> { ready.countDown(); start.await(); return settlement.runDailySettlement(day.plusDays(1)); });
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            second.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        var batch = settlement.runDailySettlement(day).getFirst();
        assertEquals(1, batch.getPaymentCount());
        equal("16000", batch.getGrossAmount());
        equal("1600", batch.getFeeAmount());
        equal("14400", batch.getNetAmount());
        assertEquals(1, journals.findByPaymentId(paid.getId()).stream()
                .filter(j -> "SETTLEMENT_CREDIT".equals(j.getJournalType())).count());
    }

    @Test void catchupDoesNotBackfillLegacyPayOsOrSettleFutureWalletPayments() {
        var day = java.time.LocalDate.of(2020, 10, 1);
        var oldPayOs = payments.saveAndFlush(Payment.builder().driverId(5500L).bookingId(55000L).merchantId(5L)
                .amount(money("16000")).paymentType(PaymentTypeEnum.DEPOSIT).gateway(PaymentGatewayEnum.PAYOS)
                .status(PaymentStatusEnum.SUCCESS).build());
        jdbc.update("UPDATE payments SET created_at=? WHERE id=?", day.minusDays(1).atStartOfDay(), oldPayOs.getId());
        merchant.recordPaymentEscrow(oldPayOs, "Legacy deposit outside catchup scope");
        seed(5500L, 55001L, "16000");
        quote(55002L, 5500L, "16000");
        when(booking.confirmWallet(any())).thenReturn(true);
        var future = service.pay(5500L, 55002L);
        jdbc.update("UPDATE payments SET created_at=? WHERE id=?", day.plusYears(100).atStartOfDay(), future.getId());
        settlement.runDailySettlement(day);
        assertTrue(journals.findByJournalKey("SETTLE:PAYMENT:" + oldPayOs.getId()).isEmpty());
        assertTrue(journals.findByJournalKey("SETTLE:PAYMENT:" + future.getId()).isEmpty());
    }

    @Test void bookingPaymentLookupIsScopedToAuthenticatedDriver() {
        assertNull(service.getForBooking(5300L, 53001L));
        seed(5300L, 53000L, "8000");
        quote(53001L, 5300L, "6000");
        when(booking.confirmWallet(any())).thenThrow(new RuntimeException("response lost"));
        var held = service.pay(5300L, 53001L);
        assertEquals(held.getId(), service.getForBooking(5300L, 53001L).getId());
        assertEquals(PaymentStatusEnum.PENDING, service.getForBooking(5300L, 53001L).getStatus());
        assertNull(service.getForBooking(5301L, 53001L));
        assertThrows(AppException.class, () -> service.getForBooking(5300L, -1L));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 50})
    void lateWalletCaptureIsSettledByNextDayWithoutDoubleCredit(int refundPercent) {
        long driver = 5100L + refundPercent;
        long bookingId = 51000L + refundPercent;
        var day = java.time.LocalDate.of(2020, 1, 1).plusDays(refundPercent);
        seed(driver, bookingId + 1000, "16000");
        quote(bookingId, driver, "16000");
        when(booking.confirmWallet(any())).thenThrow(new RuntimeException("response lost"));
        var pending = service.pay(driver, bookingId);
        jdbc.update("UPDATE payments SET created_at=? WHERE id=?", day.atTime(23, 59), pending.getId());
        settlement.runDailySettlement(day);
        doReturn(true).when(booking).confirmWallet(any());
        service.recover(pending.getId());
        if (refundPercent > 0) {
            var event = com.smartparking.common.event.BookingEvent.BookingCancelledEvent.builder()
                    .bookingId(bookingId).userId(driver).merchantId(5L).advanceDeposit(money("16000"))
                    .refundAmount(money("8000")).refundPercentage(refundPercent).refundRequested(true)
                    .refundChannel("WALLET").refundPolicy("PARTIAL_50_WITHIN_2H").build();
            assertEquals("COMPLETED", refunds.handleBookingCancellationRefund(event).getStatus());
        }
        var batch = settlement.runDailySettlement(day.plusDays(1)).getFirst();
        equal(refundPercent == 0 ? "1600" : "800", batch.getFeeAmount());
        equal(refundPercent == 0 ? "14400" : "7200", batch.getNetAmount());
        assertEquals(1, batch.getPaymentCount());
        assertEquals(day, batch.getBusinessDate());
        settlement.runDailySettlement(day.plusDays(1));
        var duplicate = settlement.runDailySettlement(day).getFirst();
        equal(batch.getNetAmount().toPlainString(), duplicate.getNetAmount());
        assertEquals(1, duplicate.getPaymentCount());
        settlement.runDailySettlement(day.plusDays(2));
        assertEquals(1, journals.findByPaymentId(pending.getId()).stream()
                .filter(j -> "SETTLEMENT_CREDIT".equals(j.getJournalType())).count());
        var row = statements.read(5L, 0, 100).getContent().stream()
                .filter(s -> s.paymentId().equals(pending.getId())).findFirst().orElseThrow();
        assertTrue(row.reconciled());
        equal("0", row.escrowRemaining());
    }

    @Test void rerunningCompletedBatchAddsOnlyNewlyCapturedPayment() {
        long driver = 5200L;
        var day = java.time.LocalDate.of(2020, 6, 1);
        seed(driver, 52000L, "32000");
        quote(52001L, driver, "16000");
        when(booking.confirmWallet(any())).thenReturn(true);
        var first = service.pay(driver, 52001L);
        jdbc.update("UPDATE payments SET created_at=? WHERE id=?", day.atTime(12, 0), first.getId());
        quote(52002L, driver, "16000");
        doThrow(new RuntimeException("response lost")).when(booking).confirmWallet(any());
        var second = service.pay(driver, 52002L);
        jdbc.update("UPDATE payments SET created_at=? WHERE id=?", day.atTime(23, 59), second.getId());
        assertEquals(1, settlement.runDailySettlement(day).getFirst().getPaymentCount());
        doReturn(true).when(booking).confirmWallet(any());
        service.recover(second.getId());
        var batch = settlement.runDailySettlement(day).getFirst();
        assertEquals(2, batch.getPaymentCount());
        equal("32000", batch.getGrossAmount());
        equal("3200", batch.getFeeAmount());
        equal("28800", batch.getNetAmount());
        assertEquals(2, settlement.runDailySettlement(day).getFirst().getPaymentCount());
        assertEquals(1, journals.findByPaymentId(second.getId()).stream()
                .filter(j -> "SETTLEMENT_CREDIT".equals(j.getJournalType())).count());
    }

    @BeforeEach void schema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS driver_wallets(driver_id BIGINT PRIMARY KEY, available_amount DECIMAL(15,2) DEFAULT 0 NOT NULL CHECK(available_amount>=0), held_amount DECIMAL(15,2) DEFAULT 0 NOT NULL CHECK(held_amount>=0))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS driver_wallet_entries(id BIGINT AUTO_INCREMENT PRIMARY KEY, driver_id BIGINT NOT NULL, journal_id BIGINT UNIQUE NOT NULL, payment_id BIGINT, booking_id BIGINT, entry_type VARCHAR(30), amount DECIMAL(15,2), held_delta DECIMAL(15,2), created_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS payment_attempt_groups(group_key VARCHAR(100) PRIMARY KEY)");
    }
    private BigDecimal money(String amount) { return new BigDecimal(amount); }
    private void equal(String expected, BigDecimal actual) { assertEquals(0, money(expected).compareTo(actual)); }
    private Payment seed(Long driver, long bookingId, String amount) {
        var p = payments.saveAndFlush(Payment.builder().driverId(driver).bookingId(bookingId).merchantId(5L).parkingLotId(6L)
                .amount(money(amount)).paymentType(PaymentTypeEnum.DEPOSIT).gateway(PaymentGatewayEnum.PAYOS).status(PaymentStatusEnum.REFUNDED).build());
        new TransactionTemplate(manager).executeWithoutResult(tx -> wallet.post(driver, bookingId, p.getId(), "TEST:REFUND:" + p.getId(), "REFUND", money(amount),
                REFUND_PAYABLE, DRIVER_WALLET_PAYABLE, money(amount), BigDecimal.ZERO));
        return p;
    }
    private void quote(long bookingId, long driver, String amount) {
        when(booking.require(bookingId, driver)).thenReturn(new BookingPaymentVerifier.Quote(bookingId, driver, 5L, 6L, "PENDING_PAYMENT",
                money(amount), money("30000"), LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusMinutes(10)));
    }
    @Test void newDriverHasZeroAndCannotReadOtherHistory() {
        equal("0", wallet.balance(1001L, true).availableAmount());
        seed(1002L, 9001L, "8000");
        assertTrue(wallet.entries(1001L, 0, 20).getContent().isEmpty());
        assertThrows(AppException.class, () -> wallet.entries(1001L, -1, 20));
    }
    @Test void refundThenPay6000From8000DoesNotCreateGatewayCashAndIsIdempotent() {
        seed(1003L, 9002L, "8000"); quote(9003L, 1003L, "6000");
        when(booking.confirmWallet(any())).thenReturn(true);
        var paid = service.pay(1003L, 9003L);
        assertEquals(PaymentStatusEnum.SUCCESS, paid.getStatus());
        equal("2000", wallet.balance(1003L, true).availableAmount()); equal("0", wallet.balance(1003L, true).heldAmount());
        assertEquals(paid.getId(), service.pay(1003L, 9003L).getId());
        equal("2000", wallet.balance(1003L, true).availableAmount());
        var postings = journals.findByPaymentId(paid.getId()).stream().flatMap(j -> j.getPostings().stream()).toList();
        assertTrue(postings.stream().noneMatch(p -> p.getAccountCode() == GATEWAY_CLEARING));
        equal("6000", postings.stream().filter(p -> p.getAccountCode() == CUSTOMER_DEPOSIT_ESCROW).map(LedgerPosting::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        verify(outbox, times(1)).publishPaymentCompleted(any());
        assertThrows(AppException.class, () -> service.get(9999L, paid.getId()));
    }
    @Test void insufficientBalanceRollsBackPaymentAndBalance() {
        seed(1004L, 9004L, "8000"); quote(9005L, 1004L, "16000");
        assertThrows(AppException.class, () -> service.pay(1004L, 9005L));
        equal("8000", wallet.balance(1004L, true).availableAmount());
        assertTrue(payments.findByBookingId(9005L).isEmpty()); verify(booking, never()).confirmWallet(any());
    }
    @Test void ambiguousConfirmationRetainsHoldAndRetryCapturesOnce() {
        seed(1005L, 9006L, "16000"); quote(9007L, 1005L, "16000");
        when(booking.confirmWallet(any())).thenThrow(new IllegalStateException("timeout")).thenReturn(true);
        var pending = service.pay(1005L, 9007L);
        assertEquals(PaymentStatusEnum.PENDING, pending.getStatus());
        equal("0", wallet.balance(1005L, true).availableAmount()); equal("16000", wallet.balance(1005L, true).heldAmount());
        service.recover(pending.getId()); service.recover(pending.getId());
        equal("0", wallet.balance(1005L, true).heldAmount());
        assertEquals(PaymentStatusEnum.SUCCESS, service.get(1005L, pending.getId()).getStatus());
        verify(outbox, times(1)).publishPaymentCompleted(any());
    }
    @Test void rejectedBookingReleasesHoldAndCannotSpendSame8000Twice() {
        seed(1006L, 9008L, "8000"); quote(9009L, 1006L, "6000"); quote(9010L, 1006L, "6000");
        when(booking.confirmWallet(any())).thenThrow(new IllegalStateException("timeout"));
        var pending = service.pay(1006L, 9009L);
        assertThrows(AppException.class, () -> service.pay(1006L, 9010L));
        doReturn(false).when(booking).confirmWallet(any());
        service.recover(pending.getId()); service.recover(pending.getId());
        equal("8000", wallet.balance(1006L, true).availableAmount()); equal("0", wallet.balance(1006L, true).heldAmount());
        assertEquals(PaymentStatusEnum.FAILED, service.get(1006L, pending.getId()).getStatus());
    }
    @Test void existingPayosAttemptBlocksWalletWithoutChangingBalance() {
        seed(1007L, 9011L, "16000"); quote(9012L, 1007L, "16000");
        payments.saveAndFlush(Payment.builder().driverId(1007L).bookingId(9012L).merchantId(5L).parkingLotId(6L).amount(money("16000"))
                .gateway(PaymentGatewayEnum.PAYOS).paymentType(PaymentTypeEnum.DEPOSIT).status(PaymentStatusEnum.PENDING).build());
        assertThrows(AppException.class, () -> service.pay(1007L, 9012L)); equal("16000", wallet.balance(1007L, true).availableAmount());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"50,false", "100,false", "50,true", "100,true"})
    void deposit16000RefundsIntoWalletAndStatementBalances(int percent, boolean afterSettlement) {
        long driver = 2000L + percent + (afterSettlement ? 1000 : 0);
        long bookingId = 20000L + driver;
        var paid = payments.saveAndFlush(Payment.builder().driverId(driver).bookingId(bookingId).merchantId(driver).parkingLotId(6L)
                .amount(money("16000")).paymentType(PaymentTypeEnum.DEPOSIT).gateway(PaymentGatewayEnum.PAYOS).status(PaymentStatusEnum.SUCCESS).build());
        merchant.recordPaymentEscrow(paid, "Deposit 16k");
        if (afterSettlement) merchant.creditSettlement(driver, bookingId, paid, paid.getAmount(), "Settlement 16k");
        var event = com.smartparking.common.event.BookingEvent.BookingCancelledEvent.builder().bookingId(bookingId).userId(driver).merchantId(driver)
                .advanceDeposit(money("16000")).refundAmount(money("16000").multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100)))
                .refundPercentage(percent).refundRequested(true).refundChannel("WALLET").refundPolicy("TEST").build();
        var result = refunds.handleBookingCancellationRefund(event);
        assertEquals("COMPLETED", result.getStatus()); assertEquals("WALLET", result.getRefundChannel());
        equal(percent == 50 ? "8000" : "16000", wallet.balance(driver, true).availableAmount());
        refunds.handleBookingCancellationRefund(event);
        equal(percent == 50 ? "8000" : "16000", wallet.balance(driver, true).availableAmount());
        assertThrows(AppException.class, () -> refunds.submitTransferEvidence(driver, bookingId, null));
        var statement = statements.read(driver, 0, 20).getContent().getFirst();
        assertTrue(statement.reconciled()); equal(percent == 50 ? "8000" : "16000", statement.refundCompleted());
        var cashCredits = journals.findByPaymentId(paid.getId()).stream().flatMap(j -> j.getPostings().stream())
                .filter(x -> x.getAccountCode() == GATEWAY_CLEARING && x.getDirection() == PostingDirection.CREDIT)
                .map(LedgerPosting::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        equal("0", cashCredits);
    }

    @Test void cancellationBeforeCaptureIsDurableAndRefundsOnlyAfterCapture() {
        seed(4001L, 40001L, "8000"); quote(40002L, 4001L, "6000");
        when(booking.confirmWallet(any())).thenThrow(new IllegalStateException("timeout after booking accepted"));
        var pending = service.pay(4001L, 40002L);
        var event = com.smartparking.common.event.BookingEvent.BookingCancelledEvent.builder().bookingId(40002L).userId(4001L).merchantId(5L)
                .refundAmount(money("3000")).advanceDeposit(money("6000")).refundPercentage(50).refundRequested(true).refundChannel("WALLET").refundPolicy("PARTIAL_50_WITHIN_2H").build();
        var deferred = refunds.handleBookingCancellationRefund(event);
        assertEquals("REQUESTED", deferred.getStatus()); equal("2000", wallet.balance(4001L,true).availableAmount());
        doReturn(true).when(booking).confirmWallet(any()); service.recover(pending.getId());
        var captured=payments.findById(pending.getId()).orElseThrow();
        merchant.creditSettlement(5L,40002L,captured,money("3000"),"Retained half settled while refund awaits recovery");
        assertEquals("COMPLETED", refunds.retryWalletRefund(deferred.getId(), null).getStatus());
        var statement=statements.read(5L,0,100).getContent().stream().filter(row -> row.paymentId().equals(pending.getId())).findFirst().orElseThrow();
        equal("300",statement.depositPlatformFee()); equal("2700",statement.depositMerchantNet()); assertTrue(statement.reconciled());
        refunds.retryWalletRefund(deferred.getId(), null);
        equal("5000", wallet.balance(4001L,true).availableAmount()); equal("0", wallet.balance(4001L,true).heldAmount());
    }
    @Test void zeroRefundRetainsFullDepositAndSplits1600Fee14400Merchant() {
        long driver = 4002L;
        var paid = payments.saveAndFlush(Payment.builder().driverId(driver).bookingId(40003L).merchantId(driver).parkingLotId(6L)
                .amount(money("16000")).paymentType(PaymentTypeEnum.DEPOSIT).gateway(PaymentGatewayEnum.PAYOS).status(PaymentStatusEnum.SUCCESS).build());
        merchant.recordPaymentEscrow(paid,"No-show deposit");
        var event = com.smartparking.common.event.BookingEvent.BookingCancelledEvent.builder().bookingId(40003L).userId(driver).merchantId(driver)
                .refundAmount(BigDecimal.ZERO).refundRequested(false).refundChannel("WALLET").refundPolicy("NO_REFUND_NO_SHOW").build();
        assertNull(refunds.handleBookingCancellationRefund(event));
        merchant.creditSettlement(driver,40003L,paid,paid.getAmount(),"No-show retained deposit");
        var statement = statements.read(driver,0,20).getContent().getFirst();
        equal("1600",statement.depositPlatformFee()); equal("14400",statement.depositMerchantNet());
        equal("0",wallet.balance(driver,true).availableAmount()); assertTrue(statement.reconciled());
    }
    @Test void refundReversesOriginalEightPercentEvenAfterConfigurationChangesToTen() {
        long driver = 4003L;
        var paid = payments.saveAndFlush(Payment.builder().driverId(driver).bookingId(40004L).merchantId(driver).parkingLotId(6L)
                .amount(money("16000")).paymentType(PaymentTypeEnum.DEPOSIT).gateway(PaymentGatewayEnum.PAYOS).status(PaymentStatusEnum.SUCCESS).build());
        merchant.recordPaymentEscrow(paid,"Historical deposit");
        try {
            org.springframework.test.util.ReflectionTestUtils.setField(merchant,"platformFeePercent",money("8"));
            merchant.creditSettlement(driver,40004L,paid,paid.getAmount(),"Original 8 percent");
            org.springframework.test.util.ReflectionTestUtils.setField(merchant,"platformFeePercent",money("10"));
            var event = com.smartparking.common.event.BookingEvent.BookingCancelledEvent.builder().bookingId(40004L).userId(driver).merchantId(driver)
                    .refundAmount(money("8000")).advanceDeposit(money("16000")).refundPercentage(50).refundRequested(true).refundChannel("WALLET").refundPolicy("PARTIAL_50_WITHIN_2H").build();
            assertEquals("COMPLETED",refunds.handleBookingCancellationRefund(event).getStatus());
            var statement = statements.read(driver,0,20).getContent().getFirst();
            equal("640",statement.depositPlatformFee()); equal("7360",statement.depositMerchantNet()); assertTrue(statement.reconciled());
        } finally { org.springframework.test.util.ReflectionTestUtils.setField(merchant,"platformFeePercent",money("10")); }
    }

    @Test void disabledRefundKeepsMoneyUncreditedUntilAdminRetriesAfterEnablement() {
        long driver=4005L;
        var paid=payments.saveAndFlush(Payment.builder().driverId(driver).bookingId(40005L).merchantId(driver).parkingLotId(6L)
                .amount(money("16000")).paymentType(PaymentTypeEnum.DEPOSIT).gateway(PaymentGatewayEnum.PAYOS).status(PaymentStatusEnum.SUCCESS).build());
        merchant.recordPaymentEscrow(paid,"Deposit before pause");
        var event=com.smartparking.common.event.BookingEvent.BookingCancelledEvent.builder().bookingId(40005L).userId(driver).merchantId(driver)
                .refundAmount(money("8000")).advanceDeposit(money("16000")).refundPercentage(50).refundRequested(true).refundChannel("WALLET").refundPolicy("PARTIAL_50_WITHIN_2H").build();
        try {
            org.springframework.test.util.ReflectionTestUtils.setField(refunds,"walletEnabled",false);
            var blocked=refunds.handleBookingCancellationRefund(event);
            assertEquals("REQUIRES_RECONCILIATION",blocked.getStatus()); equal("0",wallet.balance(driver,true).availableAmount());
            org.springframework.test.util.ReflectionTestUtils.setField(refunds,"walletEnabled",true);
            assertEquals("COMPLETED",refunds.retryWalletRefund(blocked.getId(),77L).getStatus());
            equal("8000",wallet.balance(driver,true).availableAmount());
        } finally { org.springframework.test.util.ReflectionTestUtils.setField(refunds,"walletEnabled",true); }
    }
    @AfterEach void walletProjectionAlwaysMatchesDriverLedgerAccounts() {
        jdbc.query("SELECT driver_id,available_amount,held_amount FROM driver_wallets", r -> {
            long driver=r.getLong(1);
            var available=jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN direction='CREDIT' THEN amount ELSE -amount END),0) FROM ledger_postings WHERE driver_id=? AND account_code='DRIVER_WALLET_PAYABLE'",BigDecimal.class,driver);
            var held=jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN direction='CREDIT' THEN amount ELSE -amount END),0) FROM ledger_postings WHERE driver_id=? AND account_code='DRIVER_WALLET_HOLD'",BigDecimal.class,driver);
            assertEquals(0,r.getBigDecimal(2).compareTo(available)); assertEquals(0,r.getBigDecimal(3).compareTo(held));
        });
    }

}

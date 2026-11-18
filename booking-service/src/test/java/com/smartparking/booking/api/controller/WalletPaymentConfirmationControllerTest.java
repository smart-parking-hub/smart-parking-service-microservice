package com.smartparking.booking.api.controller;

import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.exception.AppException;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE;

class WalletPaymentConfirmationControllerTest {
    private BookingRepository repository;
    private WalletPaymentConfirmationController controller;
    private Booking booking;
    private JdbcTemplate jdbc;
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:receipts" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE wallet_payment_receipts(payment_id BIGINT PRIMARY KEY,booking_id BIGINT,accepted BOOLEAN,created_at TIMESTAMP)");
        repository = mock(BookingRepository.class);
        controller = new WalletPaymentConfirmationController(repository, jdbc);
        ReflectionTestUtils.setField(controller, "serviceToken", "internal-test");
        booking = Booking.builder().id(41L).driverId(10L).merchantId(20L).advanceDeposit(new BigDecimal("16000"))
                .status(BookingStatusEnum.PENDING_PAYMENT).expiresAt(LocalDateTime.now(BUSINESS_ZONE).plusMinutes(10))
                .expectedCheckInTime(LocalDateTime.now(BUSINESS_ZONE).plusHours(3)).build();
        when(repository.findByIdForUpdate(41L)).thenReturn(Optional.of(booking));
    }
    private WalletPaymentConfirmationController.Confirmation request() {
        return new WalletPaymentConfirmationController.Confirmation(71L, 10L, 20L, new BigDecimal("16000"));
    }
    @Test void receiptRetainsAcceptedDecisionAfterCancellationAndRetriesDoNotReviveBooking() {
        assertTrue(controller.confirm(41L, "internal-test", request()).getData());
        assertEquals(BookingStatusEnum.PENDING_CHECKIN, booking.getStatus());
        booking.setStatus(BookingStatusEnum.CANCELLED);
        assertTrue(controller.confirm(41L, "internal-test", request()).getData());
        assertEquals(BookingStatusEnum.CANCELLED, booking.getStatus());
        verify(repository, times(1)).save(any());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_payment_receipts", Integer.class));
    }
    @Test void paidHalfHourPolicyTwoTicketExpiresAtTheReservedEnd() {
        var scheduledEnd = booking.getExpectedCheckInTime().plusMinutes(30);
        booking.setSchedulePolicyVersion(2);
        booking.setExpectedCheckOutTime(scheduledEnd);

        assertTrue(controller.confirm(41L, "internal-test", request()).getData());
        assertEquals(BookingStatusEnum.PENDING_CHECKIN, booking.getStatus());
        assertEquals(scheduledEnd, booking.getExpiresAt());
        assertEquals(scheduledEnd, com.smartparking.booking.application.dto.BookingDto.fromEntity(booking).getExpiresAt());
    }

    @Test void expiredDecisionRemainsRejectedOnRetry() {
        booking.setExpiresAt(LocalDateTime.now(BUSINESS_ZONE).minusSeconds(1));
        assertFalse(controller.confirm(41L, "internal-test", request()).getData());
        booking.setExpiresAt(LocalDateTime.now(BUSINESS_ZONE).plusHours(1));
        assertFalse(controller.confirm(41L, "internal-test", request()).getData());
        verify(repository, never()).save(any());
    }
    @Test void invalidTokenIdentityOrAmountCannotCreateReceipt() {
        assertThrows(AppException.class, () -> controller.confirm(41L, "wrong", request()));
        assertThrows(AppException.class, () -> controller.confirm(41L, "internal-test", new WalletPaymentConfirmationController.Confirmation(71L, 99L, 20L, new BigDecimal("16000"))));
        assertThrows(AppException.class, () -> controller.confirm(41L, "internal-test", new WalletPaymentConfirmationController.Confirmation(71L, 10L, 20L, new BigDecimal("1"))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wallet_payment_receipts", Integer.class));
    }
}

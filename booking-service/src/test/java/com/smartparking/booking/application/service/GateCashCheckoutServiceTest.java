package com.smartparking.booking.application.service;

import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.CashCheckoutClient;
import com.smartparking.common.dto.CashCheckoutCommand;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GateCashCheckoutServiceTest {
    @Mock BookingRepository bookings;
    @Mock CashCheckoutClient payments;
    @InjectMocks GateCashCheckoutService gate;
    private Booking booking() {
        var b = Booking.builder().id(41L).bookingCode("BK-41").merchantId(8L).parkingLotId(9L)
                .status(BookingStatusEnum.AWAITING_FINAL_PAYMENT).totalAmount(new BigDecimal("40000"))
                .advanceDeposit(new BigDecimal("8000")).build();
        when(bookings.findByBookingCode("BK-41")).thenReturn(Optional.of(b)); return b;
    }
    @Test void derivesTheBookingAndMerchantBeforeCallingPaymentCoordinator() {
        booking(); gate.confirm(8L, "BK-41", 9L, new BigDecimal("32000"));
        verify(payments).confirm(41L, new CashCheckoutCommand(8L, 9L, new BigDecimal("32000")));
    }
    @Test void rejectsAnotherMerchantOrLotAndWrongAmountBeforePaymentRpc() {
        booking();
        assertThrows(AppException.class, () -> gate.confirm(88L, "BK-41", 9L, new BigDecimal("32000")));
        assertThrows(AppException.class, () -> gate.confirm(8L, "BK-41", 99L, new BigDecimal("32000")));
        assertThrows(AppException.class, () -> gate.confirm(8L, "BK-41", 9L, new BigDecimal("40000")));
        verifyNoInteractions(payments);
    }
    @Test void cashRetryReturnsTheExistingDecisionButPayOsAndLegacyCompletionCannotBeClaimedAsCash() {
        var b = booking(); b.setStatus(BookingStatusEnum.COMPLETED); b.setFinalPaymentMethod("CASH");
        gate.confirm(8L, "BK-41", 9L, new BigDecimal("32000"));
        b.setFinalPaymentMethod("PAYOS");
        assertThrows(AppException.class, () -> gate.confirm(8L, "BK-41", 9L, new BigDecimal("32000")));
        b.setFinalPaymentMethod(null);
        assertThrows(AppException.class, () -> gate.confirm(8L, "BK-41", 9L, new BigDecimal("32000")));
        verifyNoInteractions(payments);
    }
}

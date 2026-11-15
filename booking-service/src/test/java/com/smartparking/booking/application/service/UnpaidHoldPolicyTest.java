package com.smartparking.booking.application.service;

import com.smartparking.booking.config.HoldPolicyProperties;
import com.smartparking.booking.domain.HoldRestrictionException;
import com.smartparking.booking.infrastructure.persistence.HoldPolicyStore;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnpaidHoldPolicyTest {
    final HoldPolicyStore store=mock(HoldPolicyStore.class);
    final HoldAbandonmentVerifier verifier=mock(HoldAbandonmentVerifier.class);
    final HoldPolicyProperties config=new HoldPolicyProperties();
    final UnpaidHoldPolicy policy=new UnpaidHoldPolicy(store,config,verifier);
    final LocalDateTime now=LocalDateTime.of(2026,11,15,14,0);
    void releases(LocalDateTime first,LocalDateTime second,String a,String b) {
        when(store.recentReleasesForUpdate(eq(7L),eq(5L),any())).thenReturn(List.of(
                new HoldPolicyStore.Release(1L,7L,11L,5L,first,"PENDING"),
                new HoldPolicyStore.Release(2L,7L,12L,5L,second,"PENDING")));
        when(verifier.verify(1L,now)).thenReturn(a); when(verifier.verify(2L,now)).thenReturn(b);
    }
    @Test void locksAccountThenVehicle() {
        policy.lock(7L,11L); var ordered=inOrder(store);
        ordered.verify(store).lock("ACCOUNT:7"); ordered.verify(store).lock("VEHICLE:11");
    }
    @Test void existingVehicleHoldBlocksWithoutReadingPaymentOrExposingAnotherDriversTicket() {
        when(store.activeForVehicle(11L)).thenReturn(List.of(new HoldPolicyStore.ActiveBooking(99L,8L)));
        var error=assertThrows(HoldRestrictionException.class,()->policy.check(7L,11L,5L,now));
        assertEquals(HoldRestrictionException.Reason.VEHICLE_LIMIT,error.getDetails().reason());
        assertNull(error.getDetails().existingBookingId()); verifyNoInteractions(verifier);
    }
    @Test void twoConfirmedReleasesCauseCooldownAtOriginalSecondReleaseTime() {
        releases(now.minusMinutes(40),now.minusMinutes(5),"UNPAID","UNPAID");
        var error=assertThrows(HoldRestrictionException.class,()->policy.check(7L,11L,5L,now));
        assertEquals(HoldRestrictionException.Reason.COOLDOWN,error.getDetails().reason());
        assertEquals(now.plusMinutes(25),error.getDetails().retryAt());
        assertEquals(error.getDetails().retryAt(),assertThrows(HoldRestrictionException.class,
                ()->policy.check(7L,11L,5L,now)).getDetails().retryAt());
    }
    @Test void sixtyMinuteWindowIncludesExactBoundary() {
        releases(now.minusMinutes(61),now.minusMinutes(1),"UNPAID","UNPAID");
        assertEquals(HoldRestrictionException.Reason.COOLDOWN,assertThrows(HoldRestrictionException.class,
                ()->policy.check(7L,11L,5L,now)).getDetails().reason());
    }
    @Test void releasesOutsideWindowDoNotCombine() {
        releases(now.minusMinutes(62),now.minusMinutes(1),"UNPAID","UNPAID");
        assertDoesNotThrow(()->policy.check(7L,11L,5L,now));
    }
    @Test void cooldownEndsAtExactBoundaryAndDoesNotRestartOnRetry() {
        releases(now.minusMinutes(40),now.minusMinutes(30),"UNPAID","UNPAID");
        assertDoesNotThrow(()->policy.check(7L,11L,5L,now));
    }
    @Test void paidOrReconciliationReleaseIsNotAnOffense() {
        releases(now.minusMinutes(15),now.minusMinutes(1),"UNPAID","PROTECTED");
        assertDoesNotThrow(()->policy.check(7L,11L,5L,now));
    }
    @Test void unknownMoneyGivesTemporaryVerificationErrorNotThirtyMinutePenalty() {
        releases(now.minusMinutes(15),now.minusMinutes(1),"UNKNOWN","UNKNOWN");
        var error=assertThrows(HoldRestrictionException.class,()->policy.check(7L,11L,5L,now));
        assertEquals(HoldRestrictionException.Reason.PAYMENT_CHECK_PENDING,error.getDetails().reason());
        assertEquals(now.plusMinutes(1),error.getDetails().retryAt());
    }
    @Test void oldUnknownReleasesCannotKeepSomeoneBlockedAfterPossibleCooldownEnded() {
        releases(now.minusMinutes(50),now.minusMinutes(31),"UNKNOWN","UNPAID");
        assertDoesNotThrow(()->policy.check(7L,11L,5L,now));
    }
    @Test void disabledPolicyDoesNotLockOrInspectOrPenalize() {
        config.setEnabled(false); policy.lock(7L,11L); policy.check(7L,11L,5L,now);
        verifyNoInteractions(store,verifier);
    }
}

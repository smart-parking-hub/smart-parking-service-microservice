package com.smartparking.booking.application.service;

import com.smartparking.booking.config.HoldPolicyProperties;
import com.smartparking.booking.domain.HoldRestrictionException;
import com.smartparking.booking.infrastructure.persistence.HoldPolicyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HoldRateLimiterTest {
    private final HoldPolicyStore store = mock(HoldPolicyStore.class);
    private final HoldPolicyProperties properties = new HoldPolicyProperties();
    private final HoldRateLimiter limiter = new HoldRateLimiter(store, properties);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 9, 0);

    @BeforeEach void setup() {
        when(store.rateWindow(7L)).thenReturn(Optional.empty());
    }

    @Test void aFutureWindowResetsInsteadOfBlockingUntilTomorrow() {
        when(store.rateWindow(7L)).thenReturn(Optional.of(new HoldPolicyStore.RateWindow(now.plusDays(1), 10)));
        assertDoesNotThrow(() -> limiter.acquire(7L, now));
        verify(store).setRateWindow(7L, now, 1);
    }

    @Test void anOversizedConfiguredWindowStillExpiresAfterSixtySeconds() {
        properties.setRateWindowSeconds(86400);
        when(store.rateWindow(7L)).thenReturn(Optional.of(new HoldPolicyStore.RateWindow(now, 10)));
        var blocked = assertThrows(HoldRestrictionException.class, () -> limiter.acquire(7L, now.plusSeconds(1)));
        assertEquals(now.plusSeconds(60), blocked.getDetails().retryAt());
        assertDoesNotThrow(() -> limiter.acquire(7L, now.plusSeconds(60)));
        verify(store).setRateWindow(7L, now.plusSeconds(60), 1);
    }

    @Test void aShorterConfiguredWindowIsPreserved() {
        properties.setRateWindowSeconds(15);
        when(store.rateWindow(7L)).thenReturn(Optional.of(new HoldPolicyStore.RateWindow(now, 10)));
        var blocked = assertThrows(HoldRestrictionException.class, () -> limiter.acquire(7L, now.plusSeconds(1)));
        assertEquals(now.plusSeconds(15), blocked.getDetails().retryAt());
        assertDoesNotThrow(() -> limiter.acquire(7L, now.plusSeconds(15)));
    }

    @Test void blockedRetriesKeepTheOriginalExpiryWithoutWritingAnotherWindow() {
        when(store.rateWindow(7L)).thenReturn(Optional.of(new HoldPolicyStore.RateWindow(now, 10)));
        for (int seconds : new int[]{1, 20, 59}) {
            var blocked = assertThrows(HoldRestrictionException.class, () -> limiter.acquire(7L, now.plusSeconds(seconds)));
            assertEquals(HoldRestrictionException.Reason.RATE_LIMIT, blocked.getDetails().reason());
            assertEquals(now.plusSeconds(60), blocked.getDetails().retryAt());
        }
        verify(store, never()).setRateWindow(anyLong(), any(), anyInt());
    }

    @Test void anAvailableAttemptKeepsTheAnchoredWindow() {
        when(store.rateWindow(7L)).thenReturn(Optional.of(new HoldPolicyStore.RateWindow(now, 9)));
        limiter.acquire(7L, now.plusSeconds(30));
        verify(store).setRateWindow(7L, now, 10);
    }

    @Test void aDisabledPolicyDoesNotReadOrWriteTheStore() {
        properties.setEnabled(false);
        limiter.acquire(7L, now);
        verifyNoInteractions(store);
    }
}

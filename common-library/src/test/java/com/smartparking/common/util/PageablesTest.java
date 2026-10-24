package com.smartparking.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PageablesTest {

    @Test
    @DisplayName("sanitize returns default pageable when input is null or unpaged")
    void testSanitizeNullOrUnpaged() {
        Pageable resNull = Pageables.sanitize(null);
        assertEquals(0, resNull.getPageNumber());
        assertEquals(20, resNull.getPageSize());

        Pageable resUnpaged = Pageables.sanitize(Pageable.unpaged());
        assertEquals(0, resUnpaged.getPageNumber());
        assertEquals(20, resUnpaged.getPageSize());
    }

    @Test
    @DisplayName("sanitize adjusts invalid page number and invalid page size")
    void testSanitizeInvalidBounds() {
        // negative page -> reset to 0
        Pageable mockNegative = mock(Pageable.class);
        when(mockNegative.isUnpaged()).thenReturn(false);
        when(mockNegative.getPageNumber()).thenReturn(-1);
        when(mockNegative.getPageSize()).thenReturn(20);

        Pageable negativePage = Pageables.sanitize(mockNegative);
        assertEquals(0, negativePage.getPageNumber());

        // exceeding max page -> reset to 0
        Pageable exceedingPage = Pageables.sanitize(PageRequest.of(20_000, 20));
        assertEquals(0, exceedingPage.getPageNumber());

        // size < 1 -> reset to default 20
        Pageable mockZeroSize = mock(Pageable.class);
        when(mockZeroSize.isUnpaged()).thenReturn(false);
        when(mockZeroSize.getPageNumber()).thenReturn(1);
        when(mockZeroSize.getPageSize()).thenReturn(0);

        Pageable zeroSize = Pageables.sanitize(mockZeroSize);
        assertEquals(1, zeroSize.getPageNumber());
        assertEquals(20, zeroSize.getPageSize());

        // size > 100 -> reset to default 20
        Pageable excessiveSize = Pageables.sanitize(PageRequest.of(1, 500));
        assertEquals(1, excessiveSize.getPageNumber());
        assertEquals(20, excessiveSize.getPageSize());
    }

    @Test
    @DisplayName("sanitize preserves valid page number and size")
    void testSanitizeValid() {
        Pageable valid = Pageables.sanitize(PageRequest.of(5, 50));
        assertEquals(5, valid.getPageNumber());
        assertEquals(50, valid.getPageSize());
    }
}

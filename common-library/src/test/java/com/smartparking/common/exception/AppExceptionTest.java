package com.smartparking.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppExceptionTest {

    @Test
    @DisplayName("AppException initializes with ErrorCode default message")
    void testErrorCodeConstructor() {
        AppException ex = new AppException(ErrorCode.UNAUTHORIZED);
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        assertEquals(ErrorCode.UNAUTHORIZED.getMessage(), ex.getMessage());
    }

    @Test
    @DisplayName("AppException initializes with custom message")
    void testCustomMessageConstructor() {
        AppException ex = new AppException(ErrorCode.BAD_REQUEST, "Custom bad request error");
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertEquals("Custom bad request error", ex.getMessage());
    }
}

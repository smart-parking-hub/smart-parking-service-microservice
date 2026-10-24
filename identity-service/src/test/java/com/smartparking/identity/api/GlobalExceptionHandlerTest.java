package com.smartparking.identity.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Handle AppException maps error codes to correct HTTP statuses")
    void testHandleAppException() {
        ResponseEntity<ApiResponse<Void>> res1 = handler.handleAppException(new AppException(ErrorCode.BAD_REQUEST, "Bad"));
        assertEquals(HttpStatus.BAD_REQUEST, res1.getStatusCode());

        ResponseEntity<ApiResponse<Void>> res2 = handler.handleAppException(new AppException(ErrorCode.FORBIDDEN, "Forbidden"));
        assertEquals(HttpStatus.FORBIDDEN, res2.getStatusCode());

        ResponseEntity<ApiResponse<Void>> res3 = handler.handleAppException(new AppException(ErrorCode.USER_NOT_FOUND, "Not found"));
        assertEquals(HttpStatus.NOT_FOUND, res3.getStatusCode());

        ResponseEntity<ApiResponse<Void>> res4 = handler.handleAppException(new AppException(ErrorCode.CONFLICT, "Conflict"));
        assertEquals(HttpStatus.CONFLICT, res4.getStatusCode());
    }

    @Test
    @DisplayName("Handle AccessDeniedException returns 403 FORBIDDEN")
    void testHandleAccessDenied() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleAccessDenied(new AccessDeniedException("Denied"));
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    @DisplayName("Handle general exception returns 500 INTERNAL_SERVER_ERROR")
    void testHandleGeneralException() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleGeneralException(new RuntimeException("Crash"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
    }
}

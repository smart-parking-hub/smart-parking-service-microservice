package com.smartparking.notification.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

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

        ResponseEntity<ApiResponse<Void>> res3 = handler.handleAppException(new AppException(ErrorCode.NOT_FOUND, "Not found"));
        assertEquals(HttpStatus.NOT_FOUND, res3.getStatusCode());

        ResponseEntity<ApiResponse<Void>> res4 = handler.handleAppException(new AppException(ErrorCode.UNAUTHORIZED, "Unauthorized"));
        assertEquals(HttpStatus.UNAUTHORIZED, res4.getStatusCode());

        ResponseEntity<ApiResponse<Void>> res5 = handler.handleAppException(new AppException(ErrorCode.CONFLICT, "Conflict"));
        assertEquals(HttpStatus.CONFLICT, res5.getStatusCode());

        ResponseEntity<ApiResponse<Void>> res6 = handler.handleAppException(new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Server err"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res6.getStatusCode());
    }

    @Test
    @DisplayName("Handle MethodArgumentNotValidException returns 400 BAD_REQUEST")
    void testHandleValidationException() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "object");
        bindingResult.addError(new FieldError("object", "field", "Field cannot be blank"));

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("testHandleValidationException"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiResponse<Void>> res = handler.handleValidationException(ex);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().getMessage().contains("Field cannot be blank"));
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

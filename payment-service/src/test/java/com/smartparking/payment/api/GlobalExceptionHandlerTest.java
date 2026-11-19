package com.smartparking.payment.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void expiredOrUnpayableBookingReturns400WithItsBusinessCode() {
        for (ErrorCode code : new ErrorCode[] {ErrorCode.BOOKING_STATUS_INVALID, ErrorCode.BOOKING_HOLD_EXPIRED}) {
            var response = handler.handleAppException(new AppException(code, "Đặt chỗ đã hết thời gian thanh toán"));
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(code.getCode(), response.getBody().getCode());
        }
    }

    @Test
    @DisplayName("Handle AppException maps error codes to correct HTTP statuses")
    void testHandleAppException() {
        ResponseEntity<ApiResponse<Void>> res1 = handler.handleAppException(new AppException(ErrorCode.BAD_REQUEST, "Bad"));
        assertEquals(HttpStatus.BAD_REQUEST, res1.getStatusCode());

        ResponseEntity<ApiResponse<Void>> res2 = handler.handleAppException(new AppException(ErrorCode.FORBIDDEN, "Forbidden"));
        assertEquals(HttpStatus.FORBIDDEN, res2.getStatusCode());

        ResponseEntity<ApiResponse<Void>> res3 = handler.handleAppException(new AppException(ErrorCode.NOT_FOUND, "Not found"));
        assertEquals(HttpStatus.NOT_FOUND, res3.getStatusCode());

        ResponseEntity<ApiResponse<Void>> res4 = handler.handleAppException(new AppException(ErrorCode.CONFLICT, "Conflict"));
        assertEquals(HttpStatus.CONFLICT, res4.getStatusCode());

        ResponseEntity<ApiResponse<Void>> res5 = handler.handleAppException(new AppException(ErrorCode.UNAUTHORIZED, "Unauthorized"));
        assertEquals(HttpStatus.UNAUTHORIZED, res5.getStatusCode());

        ResponseEntity<ApiResponse<Void>> res6 = handler.handleAppException(new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Server Error"));
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
    @DisplayName("Handle malformed JSON, parameter type mismatch, missing parameter, and constraint violations with safe 400 BAD_REQUEST")
    void testHandleBadRequestExceptions() throws Exception {
        HttpInputMessage inputMessage = new MockHttpInputMessage("{}".getBytes(StandardCharsets.UTF_8));
        ResponseEntity<ApiResponse<Void>> jsonRes = handler.handleUnreadableMessage(
                new HttpMessageNotReadableException("Jackson internal class com.smartparking", inputMessage));
        assertEquals(HttpStatus.BAD_REQUEST, jsonRes.getStatusCode());
        assertFalse(jsonRes.getBody().getMessage().contains("Jackson"));

        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("testHandleBadRequestExceptions"), -1);
        ResponseEntity<ApiResponse<Void>> mismatchRes = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Long.class, "id", param, new NumberFormatException()));
        assertEquals(HttpStatus.BAD_REQUEST, mismatchRes.getStatusCode());
        assertTrue(mismatchRes.getBody().getMessage().contains("id"));

        ResponseEntity<ApiResponse<Void>> missingRes = handler.handleMissingParam(
                new MissingServletRequestParameterException("page", "Integer"));
        assertEquals(HttpStatus.BAD_REQUEST, missingRes.getStatusCode());
        assertTrue(missingRes.getBody().getMessage().contains("page"));

        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("URL không hợp lệ");
        ResponseEntity<ApiResponse<Void>> constraintRes = handler.handleConstraintViolation(
                new ConstraintViolationException(Set.of(violation)));
        assertEquals(HttpStatus.BAD_REQUEST, constraintRes.getStatusCode());
        assertEquals("URL không hợp lệ", constraintRes.getBody().getMessage());

        ResponseEntity<ApiResponse<Void>> maxUploadRes = handler.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(5_242_880L));
        assertEquals(HttpStatus.BAD_REQUEST, maxUploadRes.getStatusCode());
        assertTrue(maxUploadRes.getBody().getMessage().contains("5 MB"));

        ResponseEntity<ApiResponse<Void>> missingPartRes = handler.handleMissingServletRequestPart(
                new MissingServletRequestPartException("files"));
        assertEquals(HttpStatus.BAD_REQUEST, missingPartRes.getStatusCode());
        assertTrue(missingPartRes.getBody().getMessage().contains("files"));

        ResponseEntity<ApiResponse<Void>> multipartRes = handler.handleMultipartException(
                new MultipartException("Invalid boundary"));
        assertEquals(HttpStatus.BAD_REQUEST, multipartRes.getStatusCode());
        assertTrue(multipartRes.getBody().getMessage().contains("multipart"));
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

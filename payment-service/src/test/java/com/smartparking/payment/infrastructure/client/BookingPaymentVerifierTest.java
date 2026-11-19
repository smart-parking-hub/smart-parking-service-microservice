package com.smartparking.payment.infrastructure.client;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.api.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BookingPaymentVerifierTest {
    private BookingPaymentVerifier verifier;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://booking.test");
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new BookingPaymentVerifier("http://booking.test");
        ReflectionTestUtils.setField(verifier, "client", builder.build());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-driver-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        server.verify();
    }

    private String quote(long id, long driverId, long merchantId) {
        return """
                {"data":{"id":%d,"driverId":%d,"merchantId":%d,"parkingLotId":31,
                "status":"PENDING_PAYMENT","advanceDeposit":2000,"totalAmount":10000,
                "expiresAt":"2099-11-20T10:00:00"}}
                """.formatted(id, driverId, merchantId);
    }

    @Test
    void acceptsOwnedQuoteAndForwardsBearer() {
        server.expect(requestTo("http://booking.test/api/v1/bookings/221"))
                .andExpect(header("Authorization", "Bearer test-driver-token"))
                .andRespond(withSuccess(quote(221, 14, 4), MediaType.APPLICATION_JSON));
        assertEquals(221L, verifier.require(221L, 14L).id());
    }

    @ParameterizedTest
    @CsvSource({"401,UNAUTHORIZED", "403,FORBIDDEN", "404,BOOKING_NOT_FOUND"})
    void translatesRejectedBookingRequest(int status, ErrorCode code) {
        server.expect(requestTo("http://booking.test/api/v1/bookings/221"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("private upstream diagnostic"));
        AppException failure = assertThrows(AppException.class, () -> verifier.require(221L, 14L));
        assertEquals(code, failure.getErrorCode());
        assertFalse(failure.getMessage().contains("private upstream diagnostic"));
        assertEquals(status, new GlobalExceptionHandler().handleAppException(failure).getStatusCode().value());
    }

    @Test
    void rejectsForeignDriver() {
        server.expect(anything()).andRespond(withSuccess(quote(221, 99, 4), MediaType.APPLICATION_JSON));
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(AppException.class, () -> verifier.require(221L, 14L)).getErrorCode());
    }

    @Test
    void rejectsWrongBookingId() {
        server.expect(anything()).andRespond(withSuccess(quote(222, 14, 4), MediaType.APPLICATION_JSON));
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(AppException.class, () -> verifier.require(221L, 14L)).getErrorCode());
    }

    @Test
    void missingBearerMakesNoUpstreamRequest() {
        RequestContextHolder.resetRequestAttributes();
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(AppException.class, () -> verifier.require(221L, 14L)).getErrorCode());
    }

    @ParameterizedTest
    @CsvSource({"500", "503"})
    void upstreamFailureBecomesServiceUnavailable(int status) {
        server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)));
        assertUnavailable();
    }

    @Test
    void timeoutBecomesServiceUnavailable() {
        server.expect(anything()).andRespond(withException(new SocketTimeoutException("private host")));
        assertUnavailable();
    }

    @Test
    void emptyQuoteBecomesServiceUnavailable() {
        server.expect(anything()).andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));
        assertUnavailable();
    }

    @Test
    void malformedQuoteBecomesServiceUnavailable() {
        server.expect(anything()).andRespond(withSuccess("invalid JSON", MediaType.APPLICATION_JSON));
        assertUnavailable();
    }

    @Test
    void merchantVerificationUsesSameErrorMapping() {
        server.expect(requestTo("http://booking.test/api/v1/merchant/bookings/221"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(AppException.class, () -> verifier.requireMerchantFinalPayment(221L, 4L)).getErrorCode());
    }

    private void assertUnavailable() {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> verifier.require(221L, 14L));
        var response = new GlobalExceptionHandler().handleUpstreamUnavailable(failure);
        assertEquals(503, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(failure.getReason().contains("private host"));
    }
}

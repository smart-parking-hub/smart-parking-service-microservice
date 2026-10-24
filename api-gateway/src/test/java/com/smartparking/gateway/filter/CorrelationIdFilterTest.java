package com.smartparking.gateway.filter;

import com.smartparking.common.security.SecurityConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    @DisplayName("Filter generates correlation ID when missing in request headers")
    void testFilterGeneratesCorrelationId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mutatedExchange -> {
            String header = mutatedExchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_CORRELATION_ID);
            assertNotNull(header);
            assertFalse(header.isBlank());
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
    }

    @Test
    @DisplayName("Filter preserves existing correlation ID in request headers")
    void testFilterPreservesCorrelationId() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/test")
                .header(SecurityConstants.HEADER_CORRELATION_ID, "MY-EXISTING-CORR-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mutatedExchange -> {
            String header = mutatedExchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_CORRELATION_ID);
            assertEquals("MY-EXISTING-CORR-123", header);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
    }
}

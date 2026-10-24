package com.smartparking.gateway.filter;

import com.smartparking.common.security.SecurityConstants;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private final String secret = "smartparkingsecretkeymustbeverylongandsecure2026version";

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret", secret);
    }

    @Test
    @DisplayName("Filter passes through without modification when Authorization header is absent")
    void testFilterWithoutAuthHeader() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/public").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mutatedExchange -> Mono.empty();

        filter.filter(exchange, chain).block();
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Filter rejects invalid JWT token with 401 UNAUTHORIZED")
    void testFilterInvalidToken() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mutatedExchange -> Mono.empty();

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Filter extracts claims and populates user headers on valid JWT token")
    void testFilterValidToken() {
        String token = Jwts.builder()
                .subject("999")
                .claim("role", "DRIVER")
                .claim("email", "driver@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/bookings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mutatedExchange -> {
            assertEquals("999", mutatedExchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_USER_ID));
            assertEquals("DRIVER", mutatedExchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_USER_ROLE));
            assertEquals("driver@example.com", mutatedExchange.getRequest().getHeaders().getFirst(SecurityConstants.HEADER_USER_EMAIL));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
    }
}

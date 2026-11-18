package com.smartparking.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.common.security.SecurityConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret:smartparkingsecretkeymustbeverylongandsecure2026version}")
    private String jwtSecret;

    @Value("${internal.secret:" + SecurityConstants.DEFAULT_INTERNAL_SECRET + "}")
    private String internalSecret = SecurityConstants.DEFAULT_INTERNAL_SECRET;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String rawPath = request.getURI().getPath();
        if (rawPath != null && (rawPath.contains("/internal/") || rawPath.endsWith("/internal"))) {
            log.warn("Blocked external access to internal path via API Gateway: {}", rawPath);
            return onError(exchange, "Không có quyền truy cập endpoint nội bộ", HttpStatus.FORBIDDEN);
        }

        // Always strip client-provided internal user identity headers to prevent spoofing
        // and inject valid trusted internal secret for downstream microservices
        ServerHttpRequest.Builder builder = request.mutate()
                .headers(httpHeaders -> {
                    httpHeaders.remove(SecurityConstants.HEADER_USER_ID);
                    httpHeaders.remove(SecurityConstants.HEADER_USER_ROLE);
                    httpHeaders.remove(SecurityConstants.HEADER_USER_EMAIL);
                    httpHeaders.remove(SecurityConstants.HEADER_INTERNAL_SECRET);
                })
                .header(SecurityConstants.HEADER_INTERNAL_SECRET, internalSecret);

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange.mutate().request(builder.build()).build());
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            String phone = claims.get("phone", String.class);
            String email = claims.get("email", String.class);

            if (StringUtils.hasText(userId)) {
                builder.header(SecurityConstants.HEADER_USER_ID, userId);
            }
            if (StringUtils.hasText(role)) {
                builder.header(SecurityConstants.HEADER_USER_ROLE, role);
            }
            if (StringUtils.hasText(email)) {
                builder.header(SecurityConstants.HEADER_USER_EMAIL, email);
            } else if (StringUtils.hasText(phone)) {
                builder.header(SecurityConstants.HEADER_USER_EMAIL, phone);
            }

            return chain.filter(exchange.mutate().request(builder.build()).build());

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT in API Gateway: {}", e.getMessage());
            return onError(exchange, "Token xác thực không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED);
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> apiResponse = ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), err);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(apiResponse);
        } catch (JsonProcessingException e) {
            bytes = ("{\"code\":\"UNAUTHORIZED\",\"message\":\"" + err + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return 0; // After CorrelationIdFilter (HIGHEST_PRECEDENCE)
    }
}

package com.smartparking.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.common.security.SecurityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class ApiKeyValidationFilter implements GlobalFilter, Ordered {

    @Value("${app.security.api-key:" + SecurityConstants.DEFAULT_APP_KEY + "}")
    private String expectedAppKey;

    @Value("${app.security.api-key-enabled:true}")
    private boolean apiKeyEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!apiKeyEnabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();

        // 1. Luôn cho phép các request CORS Preflight (OPTIONS) đi qua
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        String path = request.getPath().value();

        // 2. Bỏ qua các endpoint hạ tầng, monitoring, Swagger UI
        if (path.startsWith("/actuator") || path.startsWith("/swagger") || path.startsWith("/v3/api-docs")) {
            return chain.filter(exchange);
        }

        // 3. Kiểm tra khóa ứng dụng X-App-Key
        String clientAppKey = request.getHeaders().getFirst(SecurityConstants.HEADER_APP_KEY);

        if (!StringUtils.hasText(clientAppKey) || !expectedAppKey.equals(clientAppKey)) {
            log.warn("Unauthorized API call blocked by ApiKeyValidationFilter - IP: {}, Path: {}",
                    request.getRemoteAddress(), path);
            return onError(exchange, "Truy cập bị từ chối: Thiếu hoặc sai khóa xác thực ứng dụng (X-App-Key)", HttpStatus.FORBIDDEN);
        }

        return chain.filter(exchange);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ApiResponse<Void> apiResponse = ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), err);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(apiResponse);
        } catch (JsonProcessingException e) {
            bytes = ("{\"code\":\"ERR_403\",\"message\":\"" + err + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // Chạy sau CorrelationIdFilter và trước JwtAuthenticationFilter
    }
}

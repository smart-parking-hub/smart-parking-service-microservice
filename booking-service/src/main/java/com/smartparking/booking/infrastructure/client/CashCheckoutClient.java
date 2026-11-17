package com.smartparking.booking.infrastructure.client;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.CashCheckoutCommand;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CashCheckoutClient {
    private final RestClient client;
    private final CircuitBreakerFactory<?, ?> breakers;
    private final String token;

    public CashCheckoutClient(@Value("${services.payment.url:${PAYMENT_SERVICE_URL:http://localhost:8084}}") String url,
                              @Value("${app.internal-service-token:}") String token, CircuitBreakerFactory<?, ?> breakers) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.breakers = breakers;
        this.token = token;
    }

    public void confirm(Long bookingId, CashCheckoutCommand command) {
        if (token.isBlank()) throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Thiếu cấu hình xác nhận thanh toán");
        var response = breakers.create("cash-checkout").run(() -> client.post()
                .uri("/api/v1/internal/payments/{id}/cash-checkout", bookingId)
                .header("X-Internal-Service-Token", token).body(command).retrieve()
                .onStatus(status -> status.value() == 409, (request, result) -> {
                    throw new AppException(ErrorCode.CONFLICT, "Chưa thể xác nhận tiền mặt. Kiểm tra phiên PayOS hoặc kết quả thu tiền trước đó.");
                }).body(new ParameterizedTypeReference<ApiResponse<Boolean>>() {}), failure -> {
                    if (failure instanceof AppException app) throw app;
                    throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Chưa rõ kết quả xác nhận tiền mặt; hãy kiểm tra trạng thái trước khi thu thêm");
                });
        if (response == null || !Boolean.TRUE.equals(response.getData()))
            throw new AppException(ErrorCode.CONFLICT, "Chưa xác nhận được tiền mặt; vui lòng kiểm tra trạng thái trước khi thu thêm");
    }
}

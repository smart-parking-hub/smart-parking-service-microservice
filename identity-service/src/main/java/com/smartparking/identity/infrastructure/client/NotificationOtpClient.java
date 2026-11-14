package com.smartparking.identity.infrastructure.client;

import com.smartparking.common.enums.OtpPurposeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.service.OtpMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationOtpClient implements OtpMailSender {
    private final RestClient client;
    private final String token;
    private final CircuitBreakerFactory<?, ?> breakers;

    public NotificationOtpClient(@Value("${NOTIFICATION_SERVICE_URL:http://localhost:8085}") String url,
            @Value("${app.internal-service-token:}") String token, CircuitBreakerFactory<?, ?> breakers) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(12000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        this.token = token;
        this.breakers = breakers;
    }

    public record Delivery(String email, String code, OtpPurposeEnum purpose) {}

    @Override
    public void send(String email, String code, OtpPurposeEnum purpose) {
        if (token.isBlank()) throw unavailable();
        breakers.create("otp-mail").run(() -> {
            try {
                executePost(email, code, purpose);
            } catch (Exception ex) {
                // Retry once for cold-start SMTP/TLS handshake
                executePost(email, code, purpose);
            }
            return Boolean.TRUE;
        }, failure -> { throw unavailable(); });
    }

    private void executePost(String email, String code, OtpPurposeEnum purpose) {
        client.post().uri("/api/v1/internal/mail/otp")
                .header("X-Internal-Service-Token", token)
                .body(new Delivery(email, code, purpose)).retrieve().toBodilessEntity();
    }

    private AppException unavailable() {
        return new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                "Chưa gửi được email. Vui lòng thử lại sau.");
    }
}

package com.smartparking.booking.infrastructure.client;

import com.smartparking.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HoldPaymentAssessmentClient {
    public enum Outcome { UNPAID, PROTECTED, UNKNOWN }
    public record Assessment(Outcome disposition) {}
    private final RestClient client;
    private final CircuitBreakerFactory<?,?> breakers;
    @Value("${app.internal-service-token:}") private String serviceToken;
    public HoldPaymentAssessmentClient(@Value("${services.payment.url:http://localhost:8084}") String url,
                                       CircuitBreakerFactory<?,?> breakers) {
        var factory=new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(2000); factory.setReadTimeout(5000);
        client=RestClient.builder().baseUrl(url).requestFactory(factory).build(); this.breakers=breakers;
    }
    public Outcome assess(Long bookingId) {
        if (serviceToken==null || serviceToken.isBlank()) return Outcome.UNKNOWN;
        return breakers.create("unpaid-hold-assessment").run(() -> {
            var response=client.post().uri("/api/v1/internal/payments/{id}/unpaid-hold-assessment",bookingId)
                    .header("X-Internal-Service-Token",serviceToken).retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<Assessment>>() {});
            return response!=null && response.getData()!=null && response.getData().disposition()!=null
                    ? response.getData().disposition() : Outcome.UNKNOWN;
        }, failure -> Outcome.UNKNOWN);
    }
}

package com.smartparking.parkinglot.infrastructure.client;
import com.smartparking.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
@Component
public class BookingHoldVerifier {
    private final RestClient client;
    @Value("${app.internal-service-token:}") private String serviceToken;
    public BookingHoldVerifier(@Value("${BOOKING_SERVICE_URL:http://localhost:8083}") String url) {
        var factory = new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public boolean canRelease(Long bookingId) {
        if (serviceToken == null || serviceToken.isBlank()) return false;
            var response = client.get().uri("/api/v1/internal/bookings/{id}/hold-status",bookingId)
                .header("X-Internal-Service-Token",serviceToken).retrieve().body(new ParameterizedTypeReference<ApiResponse<String>>() {});
            return response != null && ("ABSENT".equals(response.getData()) || "CANCELLED".equals(response.getData()) || "EXPIRED".equals(response.getData()) || "COMPLETED".equals(response.getData()));
    }
    public record Snapshot(String status, java.time.LocalDateTime checkoutTime, java.time.LocalDateTime checkInTime, Integer schedulePolicyVersion) {}
    public Snapshot snapshot(Long bookingId) {
        if (serviceToken == null || serviceToken.isBlank()) return null;
        var response = client.get().uri("/api/v1/internal/bookings/{id}/schedule-status", bookingId)
                .header("X-Internal-Service-Token", serviceToken).retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<Snapshot>>() {});
        return response == null ? null : response.getData();
    }
}

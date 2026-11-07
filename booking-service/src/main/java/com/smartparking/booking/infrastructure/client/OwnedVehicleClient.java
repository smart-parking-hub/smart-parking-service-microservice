package com.smartparking.booking.infrastructure.client;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class OwnedVehicleClient {
    private final RestClient client;
    public OwnedVehicleClient(@Value("${services.identity.url:${IDENTITY_SERVICE_URL:http://localhost:8081}}") String url) {
        var factory = new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
    public record Vehicle(Long id, String licensePlate, VehicleTypeEnum vehicleType, String status) {}
    public Vehicle require(Long id) {
        var attributes = RequestContextHolder.getRequestAttributes();
        String token = attributes instanceof ServletRequestAttributes context ? context.getRequest().getHeader("Authorization") : null;
        if (token == null || !token.startsWith("Bearer ")) throw new AppException(ErrorCode.FORBIDDEN, "Thiếu phiên xác thực phương tiện");
        var response = client.get().uri("/api/v1/vehicles/{id}", id).header("Authorization", token)
                .retrieve().body(new ParameterizedTypeReference<ApiResponse<Vehicle>>() {});
        if (response == null || response.getData() == null || !"ACTIVE".equals(response.getData().status()))
            throw new AppException(ErrorCode.FORBIDDEN, "Không xác minh được phương tiện");
        return response.getData();
    }
}

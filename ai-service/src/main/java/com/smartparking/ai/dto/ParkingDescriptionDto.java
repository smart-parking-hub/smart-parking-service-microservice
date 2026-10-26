package com.smartparking.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class ParkingDescriptionDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotBlank(message = "Tên bãi xe không được để trống")
        private String lotName;

        @NotBlank(message = "Địa chỉ không được để trống")
        private String address;

        private Integer totalCapacity;
        private List<String> supportedVehicleTypes;
        private List<String> amenities;
        private String nearbyLandmarks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private String marketingDescription;
        private String operatingRegulations;
        private String wayfindingGuide;
    }
}

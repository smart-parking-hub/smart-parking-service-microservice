package com.smartparking.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class MockDataDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotBlank(message = "Khu vực không được để trống (VD: Quận 1, TP.HCM)")
        private String targetArea;

        @Min(value = 1, message = "Số lượng bãi tối thiểu là 1")
        @Max(value = 20, message = "Số lượng bãi tối đa là 20")
        @Builder.Default
        private int count = 5;

        private List<String> vehicleTypes;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MockParkingLot {
        private String name;
        private String address;
        private Double latitude;
        private Double longitude;
        private Integer totalCapacity;
        private Double basePricePerHour;
        private List<String> slotDistribution;
        private List<String> sampleReviews;
    }
}

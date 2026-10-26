package com.smartparking.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class AiChatDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotBlank(message = "Nội dung câu hỏi không được để trống")
        private String message;
        private String conversationId;
        private Double userLat;
        private Double userLon;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private String reply;
        private String source; // REALTIME_SLOTS, POLICY_FAQ, GENERAL
        private List<SuggestedParkingLot> suggestedLots;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SuggestedParkingLot {
        private Long lotId;
        private String lotName;
        private String address;
        private Double distanceKm;
        private Integer availableSlots;
        private Double pricePerHour;
        private Double averageRating;
        private String bookingUrl;
    }
}

package com.smartparking.booking.api.controller;

import com.smartparking.booking.application.service.LegacyScheduleAdoption;
import com.smartparking.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Explicit per-booking migration; access is restricted by the internal service-token filter. */
@RestController @RequiredArgsConstructor
public class ScheduleMigrationController {
    private final LegacyScheduleAdoption adoption;
    @org.springframework.beans.factory.annotation.Value("${app.internal-service-token:}") private String serviceToken;
    @PostMapping("/api/v1/internal/bookings/{id}/adopt-schedule")
    public ApiResponse<Boolean> adopt(@PathVariable Long id,
            @RequestHeader(value="X-Internal-Service-Token", required=false) String token) {
        if (serviceToken == null || serviceToken.isBlank() || token == null || !java.security.MessageDigest.isEqual(
                serviceToken.getBytes(java.nio.charset.StandardCharsets.UTF_8), token.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            throw new com.smartparking.common.exception.AppException(com.smartparking.common.exception.ErrorCode.FORBIDDEN, "Không có quyền chuyển đổi lịch nội bộ");
        return ApiResponse.ok(adoption.adopt(id));
    }
}

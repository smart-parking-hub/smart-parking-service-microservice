package com.smartparking.booking.api.controller;

import com.smartparking.booking.application.service.HoldStateQuery;
import com.smartparking.booking.application.service.HoldStateQuery.HoldState;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/bookings")
public class InternalHoldPolicyController {
    private final HoldStateQuery query;
    @Value("${app.internal-service-token:}") private String serviceToken;
    @GetMapping("/{id}/unpaid-hold-state")
    public ApiResponse<HoldState> state(@PathVariable Long id,
            @RequestHeader(value="X-Internal-Service-Token",required=false) String token) {
        if (serviceToken==null || serviceToken.isBlank() || token==null || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))
            throw new AppException(ErrorCode.FORBIDDEN,"Không có quyền kiểm tra giữ chỗ nội bộ");
        return ApiResponse.ok(query.get(id));
    }
}

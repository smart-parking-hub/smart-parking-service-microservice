package com.smartparking.payment.api.controller;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.service.UnpaidHoldAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/payments")
public class InternalUnpaidHoldController {
    private final UnpaidHoldAssessmentService assessments;
    @Value("${app.internal-service-token:}") private String serviceToken;
    public record Result(UnpaidHoldAssessmentService.Outcome disposition) {}
    @PostMapping("/{bookingId}/unpaid-hold-assessment")
    public ApiResponse<Result> assess(@PathVariable Long bookingId,
            @RequestHeader(value="X-Internal-Service-Token",required=false) String token) {
        if (serviceToken==null || serviceToken.isBlank() || token==null || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))
            throw new AppException(ErrorCode.FORBIDDEN,"Không có quyền xác minh giữ chỗ nội bộ");
        return ApiResponse.ok(new Result(assessments.assess(bookingId)));
    }
}

package com.smartparking.notification.api.controller;

import com.smartparking.common.enums.OtpPurposeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.notification.application.service.OtpEmailService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/internal/mail/otp")
public class InternalOtpMailController {
    private final OtpEmailService service;
    private final String token;

    public InternalOtpMailController(OtpEmailService service, @Value("${app.internal-service-token:}") String token) {
        this.service = service;
        this.token = token;
    }

    public record Request(@NotBlank @Email @Size(max = 100) String email,
                          @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
                          @NotNull OtpPurposeEnum purpose) {}

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void send(@RequestHeader(value = "X-Internal-Service-Token", required = false) String supplied,
                     @Valid @RequestBody Request request) {
        if (token.isBlank() || supplied == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền gửi email nội bộ.");
        }
        service.send(request.email(), request.code(), request.purpose());
    }
}

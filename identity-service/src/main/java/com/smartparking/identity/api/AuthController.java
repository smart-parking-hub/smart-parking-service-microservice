package com.smartparking.identity.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.identity.application.dto.*;
import com.smartparking.identity.application.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Đăng nhập thành công", response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đăng ký thành công", null));
    }

    @PostMapping("/otp/send")
    public ResponseEntity<ApiResponse<SendOtpResponse>> sendRegisterOtp(@Valid @RequestBody SendOtpRequest request) {
        SendOtpResponse response = authService.sendRegisterOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi mã OTP", response));
    }

    @PostMapping("/forgot-password/otp")
    public ResponseEntity<ApiResponse<SendOtpResponse>> sendForgotPasswordOtp(
            @Valid @RequestBody ForgotPasswordSendOtpRequest request) {
        SendOtpResponse response = authService.sendForgotPasswordOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Nếu email tồn tại, mã OTP đã được gửi", response));
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPasswordWithOtp(
            @Valid @RequestBody ForgotPasswordResetRequest request) {
        authService.resetPasswordWithOtp(request);
        return ResponseEntity.ok(ApiResponse.ok("Đổi mật khẩu thành công", null));
    }
}

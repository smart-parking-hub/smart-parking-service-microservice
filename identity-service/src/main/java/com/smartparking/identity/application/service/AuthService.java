package com.smartparking.identity.application.service;

import com.smartparking.identity.application.dto.*;

public interface AuthService {
    AuthResponse login(AuthRequest request);
    SendOtpResponse sendRegisterOtp(SendOtpRequest request);
    void verifyRegisterOtp(VerifyRegisterOtpRequest request);
    SendOtpResponse sendForgotPasswordOtp(ForgotPasswordSendOtpRequest request);
    void resetPasswordWithOtp(ForgotPasswordResetRequest request);
    void register(RegisterRequest request);
}

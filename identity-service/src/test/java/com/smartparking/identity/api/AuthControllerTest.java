package com.smartparking.identity.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.identity.application.dto.*;
import com.smartparking.identity.application.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController controller;

    @Test
    @DisplayName("login returns AuthResponse")
    void testLogin() {
        AuthRequest req = new AuthRequest();
        req.setPhone("0912345678");
        req.setPassword("password");
        AuthResponse authResp = AuthResponse.builder().token("jwt-token").build();
        when(authService.login(req)).thenReturn(authResp);

        ResponseEntity<ApiResponse<AuthResponse>> res = controller.login(req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("jwt-token", res.getBody().getData().getToken());
    }

    @Test
    @DisplayName("register returns CREATED status")
    void testRegister() {
        RegisterRequest req = new RegisterRequest();
        ResponseEntity<ApiResponse<Void>> res = controller.register(req);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        verify(authService, times(1)).register(req);
    }

    @Test
    @DisplayName("sendRegisterOtp returns SendOtpResponse")
    void testSendRegisterOtp() {
        SendOtpRequest req = new SendOtpRequest();
        req.setPhone("0912345678");
        SendOtpResponse resp = SendOtpResponse.builder().expiresInSeconds(300).build();
        when(authService.sendRegisterOtp(req)).thenReturn(resp);

        ResponseEntity<ApiResponse<SendOtpResponse>> res = controller.sendRegisterOtp(req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(300, res.getBody().getData().getExpiresInSeconds());
    }

    @Test
    @DisplayName("sendForgotPasswordOtp returns SendOtpResponse")
    void testSendForgotPasswordOtp() {
        ForgotPasswordSendOtpRequest req = new ForgotPasswordSendOtpRequest();
        req.setEmail("test@example.com");
        SendOtpResponse resp = SendOtpResponse.builder().expiresInSeconds(300).build();
        when(authService.sendForgotPasswordOtp(req)).thenReturn(resp);

        ResponseEntity<ApiResponse<SendOtpResponse>> res = controller.sendForgotPasswordOtp(req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(300, res.getBody().getData().getExpiresInSeconds());
    }

    @Test
    void verifyRegisterOtpDelegatesWithoutCreatingAccount() {
        VerifyRegisterOtpRequest req = new VerifyRegisterOtpRequest();
        req.setEmail("driver@example.test"); req.setOtpCode("123456");
        assertEquals(HttpStatus.OK, controller.verifyRegisterOtp(req).getStatusCode());
        verify(authService).verifyRegisterOtp(req);
        verifyNoMoreInteractions(authService);
    }

    @Test
    @DisplayName("resetPasswordWithOtp delegates to authService")
    void testResetPasswordWithOtp() {
        ForgotPasswordResetRequest req = new ForgotPasswordResetRequest();
        ResponseEntity<ApiResponse<Void>> res = controller.resetPasswordWithOtp(req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(authService, times(1)).resetPasswordWithOtp(req);
    }
}

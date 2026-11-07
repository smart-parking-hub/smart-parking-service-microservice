package com.smartparking.identity;

import com.smartparking.common.enums.RoleEnum;
import com.smartparking.identity.application.dto.*;
import com.smartparking.identity.application.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.smartparking.identity.application.service.OtpMailSender mail;

    @Test
    @DisplayName("Should successfully send OTP, register a DRIVER and login")
    void testRegisterAndLoginDriverFlow() {
        String phone = "0987654321";

        // 1. Send OTP
        SendOtpRequest otpReq = new SendOtpRequest();
        otpReq.setPhone(phone);
        otpReq.setEmail("driver@example.com");
        SendOtpResponse otpResp = authService.sendRegisterOtp(otpReq);
        var capturedCode = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mail).send(org.mockito.ArgumentMatchers.eq("driver@example.com"), capturedCode.capture(), org.mockito.ArgumentMatchers.eq(com.smartparking.common.enums.OtpPurposeEnum.REGISTER));
        assertEquals(60, otpResp.getResendAfterSeconds());

        // 2. Register Driver
        RegisterRequest regReq = new RegisterRequest();
        regReq.setPhone(phone);
        regReq.setEmail("driver@example.com");
        regReq.setPassword("Password123");
        regReq.setOtpCode(capturedCode.getValue());
        regReq.setRole(RoleEnum.DRIVER);
        regReq.setFullName("Nguyen Van Lai");
        authService.register(regReq);

        // 3. Login
        AuthRequest loginReq = new AuthRequest();
        loginReq.setPhone(phone);
        loginReq.setPassword("Password123");
        AuthResponse authResp = authService.login(loginReq);

        assertNotNull(authResp);
        assertNotNull(authResp.getToken());
        assertEquals("DRIVER", authResp.getRole());
    }
}

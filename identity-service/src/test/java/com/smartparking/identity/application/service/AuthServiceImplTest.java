package com.smartparking.identity.application.service;

import com.smartparking.common.enums.OtpPurposeEnum;
import com.smartparking.common.enums.RoleEnum;
import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.*;
import com.smartparking.identity.application.service.impl.AuthServiceImpl;
import com.smartparking.identity.domain.model.Driver;
import com.smartparking.identity.domain.model.Merchant;
import com.smartparking.identity.domain.model.OtpChallenge;
import com.smartparking.identity.domain.model.User;
import com.smartparking.identity.domain.repository.DriverRepository;
import com.smartparking.identity.domain.repository.MerchantRepository;
import com.smartparking.identity.domain.repository.OtpChallengeRepository;
import com.smartparking.identity.domain.repository.UserRepository;
import com.smartparking.identity.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private OtpChallengeRepository otpChallengeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("Login successfully returns JWT token and role")
    void testLoginSuccess() {
        AuthRequest request = new AuthRequest();
        request.setPhone("0988888888");
        request.setPassword("Password123");

        Authentication auth = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateJwtToken(auth)).thenReturn("mocked-jwt-token");

        User user = User.builder()
                .phone("0988888888")
                .role(RoleEnum.DRIVER)
                .status(UserStatusEnum.ACTIVE)
                .build();
        when(userRepository.findByPhone("0988888888")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mocked-jwt-token", response.getToken());
        assertEquals("DRIVER", response.getRole());
    }

    @Test
    @DisplayName("Login with wrong password throws INVALID_CREDENTIALS")
    void testLoginBadCredentials() {
        AuthRequest request = new AuthRequest();
        request.setPhone("0988888888");
        request.setPassword("WrongPassword");

        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        AppException ex = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Login with locked account throws FORBIDDEN")
    void testLoginAccountLocked() {
        AuthRequest request = new AuthRequest();
        request.setPhone("0988888888");
        request.setPassword("Password123");

        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("Locked"));

        AppException ex = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Login with disabled account throws FORBIDDEN")
    void testLoginAccountDisabled() {
        AuthRequest request = new AuthRequest();
        request.setPhone("0988888888");
        request.setPassword("Password123");

        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("Disabled"));

        AppException ex = assertThrows(AppException.class, () -> authService.login(request));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Send register OTP fails when phone already exists")
    void testSendRegisterOtpConflict() {
        SendOtpRequest request = new SendOtpRequest();
        request.setPhone("0988888888");

        when(userRepository.existsByPhone("0988888888")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.sendRegisterOtp(request));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("Send register OTP successfully creates challenge")
    void testSendRegisterOtpSuccess() {
        SendOtpRequest request = new SendOtpRequest();
        request.setPhone("0988888888");

        when(userRepository.existsByPhone("0988888888")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashedOtp");

        SendOtpResponse response = authService.sendRegisterOtp(request);

        assertNotNull(response);
        assertEquals(300, response.getExpiresInSeconds());
        verify(otpChallengeRepository, times(1)).save(any(OtpChallenge.class));
    }

    @Test
    @DisplayName("Send forgot password OTP for non-existing email returns blank safely")
    void testSendForgotPasswordOtpNonExistingEmail() {
        ForgotPasswordSendOtpRequest request = new ForgotPasswordSendOtpRequest();
        request.setEmail("unknown@example.com");

        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        SendOtpResponse response = authService.sendForgotPasswordOtp(request);

        assertNotNull(response);
        assertEquals(300, response.getExpiresInSeconds());
        verify(otpChallengeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Reset password fails when email not found")
    void testResetPasswordEmailNotFound() {
        ForgotPasswordResetRequest request = new ForgotPasswordResetRequest();
        request.setEmail("unknown@example.com");
        request.setOtpCode("123456");
        request.setNewPassword("NewPass123");

        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authService.resetPasswordWithOtp(request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Reset password fails when OTP expired")
    void testResetPasswordOtpExpired() {
        ForgotPasswordResetRequest request = new ForgotPasswordResetRequest();
        request.setEmail("test@example.com");
        request.setOtpCode("123456");
        request.setNewPassword("NewPass123");

        User user = User.builder().email("test@example.com").build();
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));

        OtpChallenge expired = OtpChallenge.builder()
                .phone("test@example.com")
                .purpose(OtpPurposeEnum.RESET_PASSWORD)
                .codeHash("hashed")
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .attemptCount(0)
                .build();
        when(otpChallengeRepository.findTopByPhoneAndPurposeOrderByCreatedAtDesc("test@example.com", OtpPurposeEnum.RESET_PASSWORD))
                .thenReturn(Optional.of(expired));

        AppException ex = assertThrows(AppException.class, () -> authService.resetPasswordWithOtp(request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("hết hạn"));
    }

    @Test
    @DisplayName("Register driver successfully with valid details")
    void testRegisterDriverSuccess() {
        RegisterRequest req = new RegisterRequest();
        req.setPhone("0912345678");
        req.setEmail("driver1@example.com");
        req.setPassword("Pass1234");
        req.setRole(RoleEnum.DRIVER);
        req.setFullName("Nguyen Van A");
        req.setOtpCode("654321");

        when(userRepository.existsByPhone(req.getPhone())).thenReturn(false);
        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);

        OtpChallenge challenge = OtpChallenge.builder()
                .phone(req.getPhone())
                .purpose(OtpPurposeEnum.REGISTER)
                .codeHash("hashedOtp")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attemptCount(0)
                .build();
        when(otpChallengeRepository.findTopByPhoneAndPurposeOrderByCreatedAtDesc(req.getPhone(), OtpPurposeEnum.REGISTER))
                .thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("654321", "hashedOtp")).thenReturn(true);
        when(passwordEncoder.encode("Pass1234")).thenReturn("hashedPassword");

        User savedUser = User.builder()
                .id(1L)
                .phone(req.getPhone())
                .email(req.getEmail())
                .role(RoleEnum.DRIVER)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        authService.register(req);

        verify(driverRepository, times(1)).save(any(Driver.class));
        assertNotNull(challenge.getConsumedAt());
    }

    @Test
    @DisplayName("Register merchant successfully with valid bank & citizen info")
    void testRegisterMerchantSuccess() {
        RegisterRequest req = new RegisterRequest();
        req.setPhone("0987654321");
        req.setEmail("merchant@example.com");
        req.setPassword("Pass1234");
        req.setRole(RoleEnum.MERCHANT);
        req.setFullName("Tran Thi B");
        req.setCitizenId("079200001234");
        req.setBankName("Vietcombank");
        req.setBankAccountNum("999888777");
        req.setOtpCode("112233");

        when(userRepository.existsByPhone(req.getPhone())).thenReturn(false);
        when(userRepository.existsByEmail(req.getEmail())).thenReturn(false);

        OtpChallenge challenge = OtpChallenge.builder()
                .phone(req.getPhone())
                .purpose(OtpPurposeEnum.REGISTER)
                .codeHash("hashedOtp")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attemptCount(0)
                .build();
        when(otpChallengeRepository.findTopByPhoneAndPurposeOrderByCreatedAtDesc(req.getPhone(), OtpPurposeEnum.REGISTER))
                .thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("112233", "hashedOtp")).thenReturn(true);

        User savedUser = User.builder()
                .id(2L)
                .phone(req.getPhone())
                .role(RoleEnum.MERCHANT)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        authService.register(req);

        verify(merchantRepository, times(1)).save(any(Merchant.class));
    }
}

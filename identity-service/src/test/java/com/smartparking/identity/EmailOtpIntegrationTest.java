package com.smartparking.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.OtpPurposeEnum;
import com.smartparking.common.enums.RoleEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.identity.application.dto.*;
import com.smartparking.identity.application.service.AuthService;
import com.smartparking.identity.application.service.OtpMailSender;
import com.smartparking.identity.domain.model.OtpChallenge;
import com.smartparking.identity.domain.repository.OtpChallengeRepository;
import com.smartparking.identity.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class EmailOtpIntegrationTest {
    @Autowired AuthService auth;
    @Autowired OtpChallengeRepository challenges;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @MockBean OtpMailSender mail;
    String email;

    @BeforeEach void setup() { email = UUID.randomUUID() + "@example.test"; }

    SendOtpRequest request() { var r = new SendOtpRequest(); r.setEmail(email); return r; }
    String send() {
        var response = auth.sendRegisterOtp(request());
        assertEquals(300, response.getExpiresInSeconds());
        assertEquals(60, response.getResendAfterSeconds());
        var code = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(email), code.capture(), eq(OtpPurposeEnum.REGISTER));
        return code.getValue();
    }
    OtpChallenge challenge() { return challenges.findAll().stream().filter(c -> c.getRecipient().equals(email)
            && c.getPurpose() == OtpPurposeEnum.REGISTER).findFirst().orElseThrow(); }
    RegisterRequest registration(String code) {
        var r = new RegisterRequest(); r.setEmail(email); r.setPhone("09" + String.format("%08d", Math.floorMod(email.hashCode(), 100000000)));
        r.setFullName("Email OTP Integration"); r.setRole(RoleEnum.DRIVER);
        r.setPassword("LocalPassword123"); r.setOtpCode(code); return r;
    }

    @Test void sendsToNormalizedEmailWithoutExposingCode() throws Exception {
        email = "normalized" + UUID.randomUUID() + "@example.test";
        var r = request(); r.setEmail("  " + email.toUpperCase() + "  ");
        var response = auth.sendRegisterOtp(r);
        verify(mail).send(eq(email), matches("[0-9]{6}"), eq(OtpPurposeEnum.REGISTER));
        assertFalse(json.writeValueAsString(response).contains("otpCode"));
        assertNotEquals("123456", challenge().getCodeHash());
    }

    @Test void enforcesCooldownAndReplacesChallengeAfterCooldown() {
        send(); assertThrows(AppException.class, () -> auth.sendRegisterOtp(request()));
        var first = challenge(); var id = first.getId();
        first.setSentAt(LocalDateTime.now().minusSeconds(61)); challenges.saveAndFlush(first);
        auth.sendRegisterOtp(request()); assertEquals(id, challenge().getId());
        verify(mail, times(2)).send(eq(email), anyString(), eq(OtpPurposeEnum.REGISTER));
    }

    @Test void failedAttemptsSurviveRollbackAndBlockCorrectCodeAfterFive() {
        String code = send(); String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 1; i <= 5; i++) {
            assertThrows(AppException.class, () -> auth.register(registration(wrong)));
            assertEquals(i, challenge().getAttemptCount());
        }
        assertThrows(AppException.class, () -> auth.register(registration(code)));
        assertFalse(users.existsByEmailIgnoreCase(email));
    }

    @Test void rejectsExpiredAndWrongRecipientCodes() {
        String code = send();
        var r = registration(code); r.setEmail("other-" + email);
        assertThrows(AppException.class, () -> auth.register(r));
        var c = challenge(); c.setExpiresAt(LocalDateTime.now().minusSeconds(1)); challenges.saveAndFlush(c);
        assertThrows(AppException.class, () -> auth.register(registration(code)));
        assertNull(challenge().getConsumedAt());
    }

    @Test void registersVerifiedEmailAndSeparatesResetPurpose() {
        String code = send(); auth.register(registration(code));
        assertNotNull(users.findByEmailIgnoreCase(email).orElseThrow().getEmailVerifiedAt());
        assertNotNull(challenge().getConsumedAt());
        var reset = new ForgotPasswordResetRequest(); reset.setEmail(email); reset.setOtpCode(code); reset.setNewPassword("NewPassword123");
        assertThrows(AppException.class, () -> auth.resetPasswordWithOtp(reset));
        var sendReset = new ForgotPasswordSendOtpRequest(); sendReset.setEmail(email);
        auth.sendForgotPasswordOtp(sendReset);
        auth.sendForgotPasswordOtp(sendReset); // Same generic 200 shape; no second SMTP send during cooldown.
        var captured = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq(email), captured.capture(), eq(OtpPurposeEnum.RESET_PASSWORD));
        reset.setOtpCode(captured.getValue()); auth.resetPasswordWithOtp(reset);
        assertTrue(encoder.matches("NewPassword123", users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash()));
        assertThrows(AppException.class, () -> auth.resetPasswordWithOtp(reset));
    }

    @Test void deliveryFailureRollsBackAndAllowsRetry() {
        doThrow(new IllegalStateException("SMTP unavailable")).doNothing().when(mail).send(eq(email), anyString(), any());
        assertThrows(IllegalStateException.class, () -> auth.sendRegisterOtp(request()));
        assertTrue(challenges.findAll().stream().noneMatch(c -> email.equals(c.getRecipient())));
        assertDoesNotThrow(() -> auth.sendRegisterOtp(request()));
    }

    @Test void unknownForgotEmailReturnsSamePublicShapeWithoutSending() {
        var r = new ForgotPasswordSendOtpRequest(); r.setEmail(email);
        var response = auth.sendForgotPasswordOtp(r);
        assertEquals(300, response.getExpiresInSeconds()); assertEquals(60, response.getResendAfterSeconds());
        verifyNoInteractions(mail);
    }

    @Test void changingEmailClearsExistingVerification() {
        var user = com.smartparking.identity.domain.model.User.builder()
                .email(email).emailVerifiedAt(LocalDateTime.now()).build();
        user.setEmail(email); assertNotNull(user.getEmailVerifiedAt());
        user.setEmail("changed-" + email); assertNull(user.getEmailVerifiedAt());
    }

    @Test void concurrentFirstSendsCannotBothDeliver() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> action = () -> { start.await(); try { auth.sendRegisterOtp(request()); return true; } catch (RuntimeException rejected) { return false; } };
            var a = pool.submit(action); var b = pool.submit(action); start.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
        verify(mail, times(1)).send(eq(email), anyString(), eq(OtpPurposeEnum.REGISTER));
    }
}

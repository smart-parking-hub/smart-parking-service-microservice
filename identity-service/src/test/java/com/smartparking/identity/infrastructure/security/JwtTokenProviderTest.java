package com.smartparking.identity.infrastructure.security;

import com.smartparking.common.enums.RoleEnum;
import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.identity.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private final String secret = "smartparkingsecretkeymustbeverylongandsecure2026version";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", secret);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationMs", 3600000L);
    }

    @Test
    @DisplayName("generateJwtToken, validateJwtToken, and getUserIdFromJwtToken success")
    void testTokenLifecycle() {
        User user = User.builder()
                .id(999L)
                .phone("0912345678")
                .role(RoleEnum.DRIVER)
                .status(UserStatusEnum.ACTIVE)
                .passwordHash("hash")
                .build();
        CustomUserDetails userDetails = CustomUserDetails.build(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        String token = tokenProvider.generateJwtToken(auth);
        assertNotNull(token);

        assertTrue(tokenProvider.validateJwtToken(token));
        assertEquals(999L, tokenProvider.getUserIdFromJwtToken(token));
    }

    @Test
    @DisplayName("validateJwtToken returns false for invalid token")
    void testValidateInvalidToken() {
        assertFalse(tokenProvider.validateJwtToken("invalid.token.structure"));
        assertFalse(tokenProvider.validateJwtToken(""));
    }
}

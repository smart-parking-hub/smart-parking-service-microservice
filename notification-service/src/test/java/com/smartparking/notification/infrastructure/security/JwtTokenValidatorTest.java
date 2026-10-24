package com.smartparking.notification.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenValidatorTest {

    private JwtTokenValidator validator;
    private final String secret = "smartparkingsecretkeymustbeverylongandsecure2026version";

    @BeforeEach
    void setUp() {
        validator = new JwtTokenValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret", secret);
    }

    @Test
    @DisplayName("validate returns true for valid token and extracts claims")
    void testValidateAndGetClaimsSuccess() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("testuser")
                .claim("userId", 123L)
                .claim("role", "ROLE_DRIVER")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key)
                .compact();

        assertTrue(validator.validate(token));

        Claims claims = validator.getClaims(token);
        assertNotNull(claims);
        assertEquals("testuser", claims.getSubject());
        assertEquals(123, claims.get("userId", Integer.class));
    }

    @Test
    @DisplayName("validate returns false for malformed or expired token")
    void testValidateInvalidToken() {
        assertFalse(validator.validate("invalid.token.string"));
        assertFalse(validator.validate(""));
    }
}

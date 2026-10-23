package com.smartparking.common.security;

public final class SecurityConstants {
    private SecurityConstants() {}

    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_MERCHANT = "ROLE_MERCHANT";
    public static final String ROLE_DRIVER = "ROLE_DRIVER";
}

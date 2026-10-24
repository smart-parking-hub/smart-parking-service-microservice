package com.smartparking.booking.infrastructure.security;

import com.smartparking.common.security.SecurityConstants;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalAuthFilterTest {

    @Mock
    private JwtTokenValidator jwtTokenValidator;

    @InjectMocks
    private InternalAuthFilter filter;

    @Test
    @DisplayName("Filter sets user authentication from gateway headers")
    void testFilterWithHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.HEADER_USER_ID, "101");
        request.addHeader(SecurityConstants.HEADER_USER_ROLE, "DRIVER");
        request.addHeader(SecurityConstants.HEADER_USER_EMAIL, "driver@example.com");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(101L, ((AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
    }

    @Test
    @DisplayName("Filter sets user authentication from Bearer JWT token")
    void testFilterWithJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTH_HEADER, "Bearer test.jwt.token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        when(jwtTokenValidator.validate("test.jwt.token")).thenReturn(true);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("505");
        when(claims.get("role", String.class)).thenReturn("MERCHANT");
        when(claims.get("phone", String.class)).thenReturn("0988776655");
        when(jwtTokenValidator.getClaims("test.jwt.token")).thenReturn(claims);

        filter.doFilterInternal(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(505L, ((AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
    }
}

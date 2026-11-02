package com.smartparking.payment.infrastructure.security;

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
    @DisplayName("Filter populates security context when gateway headers are present with valid secret")
    void testFilterWithHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.HEADER_INTERNAL_SECRET, SecurityConstants.DEFAULT_INTERNAL_SECRET);
        request.addHeader(SecurityConstants.HEADER_USER_ID, "300");
        request.addHeader(SecurityConstants.HEADER_USER_ROLE, "MERCHANT");
        request.addHeader(SecurityConstants.HEADER_USER_EMAIL, "merchant@example.com");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(300L, ((AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
    }

    @Test
    @DisplayName("Filter rejects gateway headers when X-Internal-Secret is absent to prevent spoofing")
    void testFilterWithGatewayHeadersWithoutSecretIsRejected() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.HEADER_USER_ID, "300");
        request.addHeader(SecurityConstants.HEADER_USER_ROLE, "ROLE_ADMIN");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Filter validates and parses JWT token when gateway headers are absent")
    void testFilterWithJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTH_HEADER, "Bearer valid.payment.token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        when(jwtTokenValidator.validate("valid.payment.token")).thenReturn(true);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("400");
        when(claims.get("role", String.class)).thenReturn("ADMIN");
        when(claims.get("phone", String.class)).thenReturn("0912000000");
        when(jwtTokenValidator.getClaims("valid.payment.token")).thenReturn(claims);

        filter.doFilterInternal(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(400L, ((AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
    }
}

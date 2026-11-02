package com.smartparking.notification.infrastructure.security;

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
    @DisplayName("Filter sets user authentication from gateway headers with valid secret")
    void testFilterWithHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.HEADER_INTERNAL_SECRET, SecurityConstants.DEFAULT_INTERNAL_SECRET);
        request.addHeader(SecurityConstants.HEADER_USER_ID, "888");
        request.addHeader(SecurityConstants.HEADER_USER_ROLE, "DRIVER");
        request.addHeader(SecurityConstants.HEADER_USER_EMAIL, "driver@example.com");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(888L, ((AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
    }

    @Test
    @DisplayName("Filter rejects gateway headers when X-Internal-Secret is absent to prevent spoofing")
    void testFilterWithGatewayHeadersWithoutSecretIsRejected() throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.HEADER_USER_ID, "888");
        request.addHeader(SecurityConstants.HEADER_USER_ROLE, "ROLE_ADMIN");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Filter sets user authentication from Bearer JWT token")
    void testFilterWithJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTH_HEADER, "Bearer valid.notif.token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        when(jwtTokenValidator.validate("valid.notif.token")).thenReturn(true);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("888");
        when(claims.get("role", String.class)).thenReturn("DRIVER");
        when(claims.get("phone", String.class)).thenReturn("0912345678");
        when(jwtTokenValidator.getClaims("valid.notif.token")).thenReturn(claims);

        filter.doFilterInternal(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(888L, ((AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getId());
    }
}

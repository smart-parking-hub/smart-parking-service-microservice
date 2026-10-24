package com.smartparking.identity.infrastructure.security;

import com.smartparking.common.security.SecurityConstants;
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
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService customUserDetailsService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @Test
    @DisplayName("shouldNotFilter skips auth and public paths")
    void testShouldNotFilter() {
        MockHttpServletRequest req1 = new MockHttpServletRequest();
        req1.setServletPath("/api/v1/auth/login");
        assertTrue(filter.shouldNotFilter(req1));

        MockHttpServletRequest req2 = new MockHttpServletRequest();
        req2.setServletPath("/api/v1/users/me");
        assertFalse(filter.shouldNotFilter(req2));
    }

    @Test
    @DisplayName("Filter sets authentication when valid JWT is provided")
    void testFilterValidJwt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTH_HEADER, "Bearer valid.identity.jwt");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        when(jwtTokenProvider.validateJwtToken("valid.identity.jwt")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromJwtToken("valid.identity.jwt")).thenReturn(100L);

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.isEnabled()).thenReturn(true);
        when(userDetails.isAccountNonLocked()).thenReturn(true);
        when(customUserDetailsService.loadUserById(100L)).thenReturn(userDetails);

        filter.doFilterInternal(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    @DisplayName("Filter rejects with 401 when user is locked or disabled")
    void testFilterLockedUser() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.AUTH_HEADER, "Bearer locked.jwt");

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        when(jwtTokenProvider.validateJwtToken("locked.jwt")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromJwtToken("locked.jwt")).thenReturn(100L);

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.isEnabled()).thenReturn(false);
        when(customUserDetailsService.loadUserById(100L)).thenReturn(userDetails);

        filter.doFilterInternal(request, response, chain);

        assertEquals(401, response.getStatus());
    }
}

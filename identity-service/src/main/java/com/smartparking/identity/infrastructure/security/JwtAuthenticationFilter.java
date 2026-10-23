package com.smartparking.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.security.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null && jwtTokenProvider.validateJwtToken(jwt)) {
                Long userId = jwtTokenProvider.getUserIdFromJwtToken(jwt);
                UserDetails userDetails = customUserDetailsService.loadUserById(userId);

                if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                    writeUnauthorized(response, "Tài khoản bị khóa hoặc không hoạt động.");
                    return;
                }

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> errorResponse = ApiResponse.error("ERR_401", message);
        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader(SecurityConstants.AUTH_HEADER);
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return headerAuth.substring(SecurityConstants.BEARER_PREFIX.length());
        }
        return null;
    }
}

package com.smartparking.parkinglot.infrastructure.security;

import com.smartparking.common.security.SecurityConstants;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class InternalAuthFilter extends OncePerRequestFilter {

    private final JwtTokenValidator jwtTokenValidator;

    @org.springframework.beans.factory.annotation.Value("${internal.secret:" + SecurityConstants.DEFAULT_INTERNAL_SECRET + "}")
    private String internalSecret = SecurityConstants.DEFAULT_INTERNAL_SECRET;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String headerUserId = request.getHeader(SecurityConstants.HEADER_USER_ID);
            String headerRole = request.getHeader(SecurityConstants.HEADER_USER_ROLE);
            String headerEmail = request.getHeader(SecurityConstants.HEADER_USER_EMAIL);
            String internalSecretHeader = request.getHeader(SecurityConstants.HEADER_INTERNAL_SECRET);
            boolean isValidInternalSecret = StringUtils.hasText(internalSecretHeader)
                    && internalSecretHeader.equals(internalSecret);

            if (StringUtils.hasText(headerUserId)) {
                if (!isValidInternalSecret) {
                    log.warn("Blocked untrusted request: X-User-Id received without valid X-Internal-Secret from IP: {}", request.getRemoteAddr());
                } else {
                    Long userId = Long.parseLong(headerUserId.trim());
                    AuthenticatedUser user = new AuthenticatedUser(userId, headerEmail, headerRole);
                    setAuthentication(request, user);
                }
            } else {
                String jwt = parseJwt(request);
                if (jwt != null && jwtTokenValidator.validate(jwt)) {
                    Claims claims = jwtTokenValidator.getClaims(jwt);
                    Long userId = Long.parseLong(claims.getSubject());
                    String role = claims.get("role", String.class);
                    String phone = claims.get("phone", String.class);

                    AuthenticatedUser user = new AuthenticatedUser(userId, phone, role);
                    setAuthentication(request, user);
                }
            }
        } catch (Exception e) {
            log.error("Could not set user authentication in parking-lot-service: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private void setAuthentication(HttpServletRequest request, AuthenticatedUser user) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader(SecurityConstants.AUTH_HEADER);
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith(SecurityConstants.BEARER_PREFIX)) {
            return headerAuth.substring(SecurityConstants.BEARER_PREFIX.length());
        }
        return null;
    }
}

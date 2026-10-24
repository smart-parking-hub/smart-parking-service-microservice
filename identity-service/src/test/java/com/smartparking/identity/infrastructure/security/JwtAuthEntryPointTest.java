package com.smartparking.identity.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthEntryPointTest {

    @Test
    @DisplayName("commence sets 401 status and writes json response")
    void testCommence() throws IOException {
        JwtAuthEntryPoint entryPoint = new JwtAuthEntryPoint();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/protected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("Invalid token"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("ERR_401"));
    }
}

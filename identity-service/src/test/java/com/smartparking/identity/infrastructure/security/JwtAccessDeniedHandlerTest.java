package com.smartparking.identity.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class JwtAccessDeniedHandlerTest {

    @Test
    @DisplayName("handle sets 403 status and writes json response")
    void testHandle() throws IOException {
        JwtAccessDeniedHandler handler = new JwtAccessDeniedHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("Access denied"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("ERR_403"));
    }
}

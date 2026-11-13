package com.smartparking.notification;

import com.smartparking.common.enums.OtpPurposeEnum;
import com.smartparking.notification.api.controller.InternalOtpMailController;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.internal-service-token=otp-integration-test-token",
        "app.mail.from=sender@example.test",
        // The transport is a mock, so it cannot supply an SMTP health contributor.
        "management.health.mail.enabled=false"
})
@AutoConfigureMockMvc
class InternalOtpMailIntegrationTest {
    private static final String API = "/api/v1/internal/mail/otp";
    private static final String TOKEN = "otp-integration-test-token";
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @MockBean JavaMailSender sender;

    @BeforeEach void setup() {
        when(sender.createMimeMessage()).thenAnswer(ignored -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private String request() throws Exception {
        return json.writeValueAsString(new InternalOtpMailController.Request(
                "driver@example.test", "001234", OtpPurposeEnum.REGISTER));
    }

    @Test void validServiceTokenDeliversThroughMailSenderAndReturnsNoContent() throws Exception {
        http.perform(post(API).header("X-Internal-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(sender).send(any(MimeMessage.class));
    }

    @Test void missingOrWrongTokenCannotTriggerSmtp() throws Exception {
        http.perform(post(API).contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isForbidden());
        for (String token : List.of("", "wrong-token")) {
            http.perform(post(API).header("X-Internal-Service-Token", token)
                            .contentType(MediaType.APPLICATION_JSON).content(request()))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(sender);
    }

    @Test void invalidDeliveryPayloadIsRejectedBeforeSmtp() throws Exception {
        for (var request : List.of(
                new InternalOtpMailController.Request("invalid", "001234", OtpPurposeEnum.REGISTER),
                new InternalOtpMailController.Request("driver@example.test", "12345", OtpPurposeEnum.REGISTER),
                new InternalOtpMailController.Request("driver@example.test", "001234", null))) {
            http.perform(post(API).header("X-Internal-Service-Token", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(sender);
    }

    @Test void smtpFailureReturnsSafeErrorAndAllowsNextDelivery() throws Exception {
        doThrow(new MailSendException("SMTP provider secret; code=001234; recipient=driver@example.test"))
                .doNothing().when(sender).send(any(MimeMessage.class));
        var result = http.perform(post(API).header("X-Internal-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false)).andReturn();
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("001234"));
        assertFalse(body.contains("driver@example.test"));
        assertFalse(body.contains("SMTP provider secret"));
        http.perform(post(API).header("X-Internal-Service-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isNoContent());
        verify(sender, times(2)).send(any(MimeMessage.class));
    }
}

package com.smartparking.notification;

import com.smartparking.common.enums.OtpPurposeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.notification.api.controller.InternalOtpMailController;
import com.smartparking.notification.application.service.OtpEmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import com.smartparking.notification.application.service.OtpEmailTemplate;
import jakarta.mail.Part;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OtpEmailTest {
    private JavaMailSender sender() {
        var sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        return sender;
    }
    private List<Part> leaves(Part message) throws Exception {
        List<Part> result = new ArrayList<>();
        if (message.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) result.addAll(leaves(multipart.getBodyPart(i)));
        } else result.add(message);
        return result;
    }
    @Test void sendsBrandedEmailWithExpiryAndBothPurposes() throws Exception {
        var sender = sender(); var service = new OtpEmailService(sender, "sender@example.test");
        service.send("driver@example.test", "123456", OtpPurposeEnum.REGISTER);
        service.send("driver@example.test", "654321", OtpPurposeEnum.RESET_PASSWORD);
        var captured = ArgumentCaptor.forClass(MimeMessage.class); verify(sender, times(2)).send(captured.capture());
        var messages = captured.getAllValues();
        for (var message : messages) message.saveChanges();
        assertEquals("driver@example.test", ((InternetAddress) messages.getFirst().getAllRecipients()[0]).getAddress());
        var from = (InternetAddress) messages.getFirst().getFrom()[0];
        assertEquals("sender@example.test", from.getAddress());
        assertEquals("Smart Parking Hub", from.getPersonal());
        var parts = leaves(messages.getFirst());
        String plain = null, html = null; boolean logo = false;
        for (var part : parts) {
            if (part.isMimeType("text/plain")) plain = (String) part.getContent();
            if (part.isMimeType("text/html")) html = (String) part.getContent();
            if (part.isMimeType("image/png")) {
                assertArrayEquals(new String[]{"<smart-parking-logo>"}, part.getHeader("Content-ID"));
                assertEquals(Part.INLINE, part.getDisposition());
                try (var stream = part.getInputStream()) { assertTrue(stream.readAllBytes().length > 0); }
                logo = true;
            }
        }
        assertNotNull(plain); assertTrue(plain.contains("123456")); assertTrue(plain.contains("5 phút"));
        assertNotNull(html); assertTrue(html.contains("123456")); assertTrue(html.contains("Xác thực email của bạn"));
        assertTrue(html.contains("cid:smart-parking-logo")); assertFalse(html.contains("{{"));
        assertFalse(html.contains("localhost")); assertFalse(html.contains("https://")); assertTrue(logo);
        assertTrue(messages.getLast().getSubject().contains("đặt lại mật khẩu"));
        boolean resetCopy = false;
        for (var part : leaves(messages.getLast())) {
            if (part.isMimeType("text/html")) resetCopy = ((String) part.getContent()).contains("tạo mật khẩu mới");
        }
        assertTrue(resetCopy);
    }
    @Test void rejectsMissingSenderAndDoesNotLeakSmtpException() {
        var emptySender = mock(JavaMailSender.class);
        assertThrows(AppException.class, () -> new OtpEmailService(emptySender, "").send("a@b.test", "123456", OtpPurposeEnum.REGISTER));
        verifyNoInteractions(emptySender);
        var sender = sender();
        doThrow(new MailSendException("secret provider detail 123456")).when(sender).send(any(MimeMessage.class));
        var error = assertThrows(AppException.class, () -> new OtpEmailService(sender, "s@b.test").send("a@b.test", "123456", OtpPurposeEnum.REGISTER));
        assertFalse(error.getMessage().contains("123456"));
    }
    @Test void preventsUntrustedHtmlContentInTemplate() {
        assertThrows(IllegalArgumentException.class, () -> OtpEmailTemplate.html("<img>", OtpPurposeEnum.REGISTER));
        assertThrows(IllegalArgumentException.class, () -> OtpEmailTemplate.html("123456", null));
        assertTrue(OtpEmailTemplate.html("000123", OtpPurposeEnum.REGISTER).contains("000123"));
    }
    @Test void internalDeliveryRequiresExactNonemptyServiceToken() {
        var service = mock(OtpEmailService.class);
        var request = new InternalOtpMailController.Request("a@b.test", "123456", OtpPurposeEnum.REGISTER);
        var controller = new InternalOtpMailController(service, "test-service-token");
        assertThrows(AppException.class, () -> controller.send(null, request));
        assertThrows(AppException.class, () -> controller.send("wrong", request));
        assertThrows(AppException.class, () -> new InternalOtpMailController(service, "").send("", request));
        verifyNoInteractions(service);
        controller.send("test-service-token", request);
        verify(service).send(request.email(), request.code(), request.purpose());
    }
}

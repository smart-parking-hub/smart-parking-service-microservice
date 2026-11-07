package com.smartparking.notification.application.service;

import com.smartparking.common.enums.OtpPurposeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ClassPathResource;
import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import org.springframework.stereotype.Service;

@Service
public class OtpEmailService {
    private final JavaMailSender sender;
    private final String from;

    public OtpEmailService(JavaMailSender sender, @Value("${app.mail.from:}") String from) {
        this.sender = sender;
        this.from = from;
    }

    public void send(String email, String code, OtpPurposeEnum purpose) {
        if (from.isBlank()) throw unavailable();
        String html = OtpEmailTemplate.html(code, purpose);
        String action = purpose == OtpPurposeEnum.REGISTER ? "xác thực email đăng ký" : "đặt lại mật khẩu";
        String plainText = "Mã " + action + " của bạn là: " + code
                + "\n\nMã có hiệu lực 5 phút, chỉ sử dụng một lần. Không chia sẻ mã với bất kỳ ai."
                + "\nNếu bạn không yêu cầu mã này, hãy bỏ qua email.\n\nSmart Parking Hub";
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(from, "Smart Parking Hub");
            helper.setTo(email);
            helper.setSubject("Smart Parking Hub — Mã " + action);
            helper.setText(plainText, html);
            helper.addInline("smart-parking-logo", new ClassPathResource("mail/smart-parking-logo.png"), "image/png");
            sender.send(message);
        } catch (MailException | MessagingException | UnsupportedEncodingException failure) {
            // SMTP errors can include recipients and message contents: never log provider exceptions.
            throw unavailable();
        }
    }

    private AppException unavailable() {
        return new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Dịch vụ gửi email tạm thời không khả dụng.");
    }
}

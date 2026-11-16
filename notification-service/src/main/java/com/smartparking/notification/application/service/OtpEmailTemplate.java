package com.smartparking.notification.application.service;

import com.smartparking.common.enums.OtpPurposeEnum;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class OtpEmailTemplate {
    private static final String HTML = load();

    private OtpEmailTemplate() {}

    public static String html(String code, OtpPurposeEnum purpose) {
        // Only a six-digit OTP and fixed application copy can enter the HTML template.
        if (code == null || !code.matches("[0-9]{6}") || purpose == null) {
            throw new IllegalArgumentException("Invalid OTP email content");
        }
        boolean register = purpose == OtpPurposeEnum.REGISTER;
        return HTML.replace("{{title}}", register ? "Xác thực email của bạn" : "Đặt lại mật khẩu")
                .replace("{{description}}", register
                        ? "Chào bạn, cảm ơn bạn đã chọn Smart Parking Hub. Nhập mã bên dưới để xác thực email và hoàn tất đăng ký."
                        : "Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu của bạn. Nhập mã bên dưới để tạo mật khẩu mới.")
                .replace("{{code}}", code);
    }

    private static String load() {
        try (var stream = new ClassPathResource("mail/otp.html").getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("OTP email template is unavailable", failure);
        }
    }
}

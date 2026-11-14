package com.smartparking.notification.application.service;

import com.smartparking.common.enums.OtpPurposeEnum;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class OtpEmailTemplate {
    private static final String HTML = load();

    private OtpEmailTemplate() {}

    public static String html(String code, OtpPurposeEnum purpose) {
        return html(code, purpose, "", "https://smart-parking-app-v2.vercel.app");
    }

    public static String html(String code, OtpPurposeEnum purpose, String email, String frontendUrl) {
        // Only a six-digit OTP and fixed application copy can enter the HTML template.
        if (code == null || !code.matches("[0-9]{6}") || purpose == null) {
            throw new IllegalArgumentException("Invalid OTP email content");
        }
        boolean register = purpose == OtpPurposeEnum.REGISTER;
        String actionButtonText = register ? "Xác thực Email & Đặt mật khẩu" : "Xác thực & Tạo mật khẩu mới";
        String path = register ? "/register" : "/forgot-password";
        String encodedEmail = email != null ? URLEncoder.encode(email, StandardCharsets.UTF_8) : "";
        String base = (frontendUrl == null || frontendUrl.isBlank()) ? "https://smart-parking-app-v2.vercel.app" : frontendUrl.replaceAll("/+$", "");
        String magicLink = base + path + "?email=" + encodedEmail + "&otpCode=" + code + "&autoVerify=true";

        return HTML.replace("{{title}}", register ? "Xác thực email của bạn" : "Đặt lại mật khẩu")
                .replace("{{description}}", register
                        ? "Chào bạn, cảm ơn bạn đã chọn Smart Parking Hub. Nhập mã bên dưới để xác thực email và hoàn tất đăng ký."
                        : "Chúng tôi đã nhận được yêu cầu đặt lại mật khẩu của bạn. Nhập mã bên dưới để tạo mật khẩu mới.")
                .replace("{{code}}", code)
                .replace("{{magicLink}}", magicLink)
                .replace("{{actionButtonText}}", actionButtonText);
    }

    private static String load() {
        try (var stream = new ClassPathResource("mail/otp.html").getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("OTP email template is unavailable", failure);
        }
    }
}

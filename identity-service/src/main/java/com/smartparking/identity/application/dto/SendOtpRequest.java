package com.smartparking.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendOtpRequest {

    @Pattern(regexp = "^(0|\\+84)[35789][0-9]{8}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @NotBlank(message = "Email nhận mã không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 100)
    private String email;

    public void setPhone(String phone) {
        if (phone != null && phone.startsWith("+84")) {
            this.phone = "0" + phone.substring(3);
        } else {
            this.phone = phone;
        }
    }
}

package com.smartparking.identity.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendOtpRequest {

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0|\\+84)[35789][0-9]{8}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    public void setPhone(String phone) {
        if (phone != null && phone.startsWith("+84")) {
            this.phone = "0" + phone.substring(3);
        } else {
            this.phone = phone;
        }
    }
}

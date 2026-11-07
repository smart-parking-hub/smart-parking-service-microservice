package com.smartparking.identity.application.dto;

import com.smartparking.common.enums.RoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0|\\+84)[35789][0-9]{8}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 50, message = "Mật khẩu phải có từ 6 đến 50 ký tự")
    private String password;

    @NotBlank(message = "Mã OTP không được để trống")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP phải gồm 6 chữ số")
    private String otpCode;

    @NotNull(message = "Vai trò (Role) không được để trống")
    private RoleEnum role;

    @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự")
    private String fullName;

    @Pattern(regexp = "^$|^(?:[0-9]{9}|[0-9]{12})$", message = "CCCD/CMND phải bao gồm chính xác 9 hoặc 12 chữ số")
    private String citizenId;

    @Size(max = 100, message = "Tên ngân hàng không được vượt quá 100 ký tự")
    private String bankName;

    @Pattern(regexp = "^$|^[0-9]{1,30}$", message = "Số tài khoản ngân hàng phải bao gồm từ 1 đến 30 chữ số")
    private String bankAccountNum;

    public void setPhone(String phone) {
        if (phone != null && phone.startsWith("+84")) {
            this.phone = "0" + phone.substring(3);
        } else {
            this.phone = phone;
        }
    }
}

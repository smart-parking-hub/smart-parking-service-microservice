package com.smartparking.identity.application.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateMerchantProfileRequest {

    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    private String fullName;

    @Email(message = "Email không hợp lệ")
    @Size(max = 100)
    private String email;

    @Pattern(regexp = "^$|^\\d{9,12}$", message = "CCCD/CMND phải gồm 9–12 chữ số")
    private String citizenId;

    @Size(max = 50, message = "Giấy phép kinh doanh tối đa 50 ký tự")
    private String businessLicense;

    @Size(max = 100, message = "Tên ngân hàng tối đa 100 ký tự")
    private String bankName;

    @Pattern(regexp = "^$|^\\d{6,30}$", message = "Số tài khoản phải gồm 6–30 chữ số")
    private String bankAccountNum;

    @Size(max = 100, message = "Tên chủ tài khoản tối đa 100 ký tự")
    private String accountHolderName;

    @Size(max = 255)
    private String address;

    @Size(max = 20)
    private String emergencyContact;

    @JsonSetter
    public void setBankName(String bankName) {
        this.bankName = bankName != null ? bankName.trim() : null;
    }

    @JsonSetter
    public void setBankAccountNum(String bankAccountNum) {
        this.bankAccountNum = bankAccountNum != null ? bankAccountNum.trim() : null;
    }

    @JsonSetter
    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName != null ? accountHolderName.trim() : null;
    }
}

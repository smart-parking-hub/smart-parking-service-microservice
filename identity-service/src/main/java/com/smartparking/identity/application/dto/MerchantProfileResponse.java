package com.smartparking.identity.application.dto;

import com.smartparking.common.enums.UserStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantProfileResponse {
    private Long merchantId;
    private Long userId;
    private String fullName;
    private String phone;
    private String email;
    private String citizenId;
    private String maskedCitizenId;
    private String businessLicense;
    private String bankName;
    private String bankAccountNum;
    private String accountHolderName;
    private String maskedBankAccount;
    private BigDecimal walletBalance;
    private UserStatusEnum accountStatus;
    private String verificationStatus;
    private String verificationLabel;
    private String address;
    private String emergencyContact;
}

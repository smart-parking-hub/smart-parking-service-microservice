package com.smartparking.payment.application.dto;

import com.smartparking.common.enums.WithdrawalStatusEnum;
import com.smartparking.payment.domain.model.WithdrawalRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalResponse {
    private Long id;
    private Long merchantId;
    private BigDecimal amount;
    private BigDecimal availableBalance;
    private WithdrawalStatusEnum status;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
    private Long processedByUserId;
    private String decisionReason;
    private String merchantName;
    private String merchantPhone;
    private String bankName;
    private String bankAccountNumber;
    private String maskedBankAccount;
    private String accountHolderName;

    public static WithdrawalResponse fromEntity(WithdrawalRequest request, BigDecimal currentBalance) {
        String rawAccount = request.getBankAccountNumber();
        String masked = "****";
        if (rawAccount != null && rawAccount.length() >= 4) {
            masked = "****" + rawAccount.substring(rawAccount.length() - 4);
        } else if (rawAccount != null && !rawAccount.isBlank()) {
            masked = rawAccount;
        }

        return WithdrawalResponse.builder()
                .id(request.getId())
                .merchantId(request.getMerchantId())
                .amount(request.getAmount())
                .availableBalance(currentBalance)
                .status(request.getStatus())
                .requestedAt(request.getRequestedAt())
                .processedAt(request.getProcessedAt())
                .processedByUserId(request.getProcessedBy())
                .decisionReason(request.getDecisionReason())
                .merchantName(request.getAccountHolderName() != null && !request.getAccountHolderName().isBlank()
                        ? request.getAccountHolderName() : "Merchant #" + request.getMerchantId())
                .merchantPhone("")
                .bankName(request.getBankName() != null ? request.getBankName() : "Ngân hàng liên kết")
                .bankAccountNumber(rawAccount)
                .maskedBankAccount(masked)
                .accountHolderName(request.getAccountHolderName())
                .build();
    }
}

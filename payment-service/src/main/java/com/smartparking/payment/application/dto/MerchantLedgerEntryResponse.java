package com.smartparking.payment.application.dto;

import com.smartparking.common.enums.WalletLedgerEntryTypeEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Read-only wallet activity; deliberately excludes the Payment entity and bank details. */
public record MerchantLedgerEntryResponse(
        Long id,
        Long bookingId,
        WalletLedgerEntryTypeEnum entryType,
        BigDecimal grossAmount,
        BigDecimal feePercent,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        String note,
        LocalDateTime createdAt) {
}

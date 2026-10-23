package com.smartparking.common.enums;

public enum WalletLedgerEntryTypeEnum {
    /** Cộng doanh thu đã thu (sau khi trừ phí nền tảng). */
    SETTLEMENT_CREDIT,
    /** Giữ tiền khi merchant gửi yêu cầu rút. */
    WITHDRAWAL_HOLD,
    /** Hoàn lại ví khi admin từ chối rút. */
    WITHDRAWAL_REFUND,
    /** Điều chỉnh tay / vận hành. */
    ADJUSTMENT
}

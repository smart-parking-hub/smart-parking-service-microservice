package com.smartparking.payment.domain.model;

/**
 * Approved six-account chart of accounts for payment-service double-entry bookkeeping (D2).
 */
public enum LedgerAccountCode {
    GATEWAY_CLEARING,
    CUSTOMER_DEPOSIT_ESCROW,
    MERCHANT_PAYABLE,
    MERCHANT_WITHDRAWAL_HOLD,
    PLATFORM_FEE_REVENUE,
    REFUND_PAYABLE
}


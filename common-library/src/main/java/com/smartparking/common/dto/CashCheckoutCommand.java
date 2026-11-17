package com.smartparking.common.dto;

import java.math.BigDecimal;

/** Service-authenticated command; merchant identity comes from the booking service. */
public record CashCheckoutCommand(Long merchantId, Long parkingLotId, BigDecimal amount) {}

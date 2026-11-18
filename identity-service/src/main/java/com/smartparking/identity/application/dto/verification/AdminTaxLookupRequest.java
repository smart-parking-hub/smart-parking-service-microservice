package com.smartparking.identity.application.dto.verification;

// Retain the legacy household flag so older clients receive a manual-review response.
public record AdminTaxLookupRequest(boolean confirmedBusinessTaxCode, boolean confirmedHouseholdTaxCode) {}

package com.smartparking.identity.application.dto.verification;

import com.smartparking.identity.application.service.verification.BusinessTaxRegistry.Business;
import com.smartparking.identity.application.service.verification.BusinessTaxRegistry.Outcome;

import java.time.Instant;

public record AdminTaxLookupResponse(Long dossierId, Integer submissionVersion, Outcome outcome, Instant checkedAt,
                                     Business business, Instant retryAt) {}

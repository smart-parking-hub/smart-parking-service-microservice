package com.smartparking.identity.application.dto.verification;

import com.smartparking.identity.domain.model.verification.OcrMethod;

public record AssessmentSourceDto(String documentId, String originalName, OcrMethod method, Integer submissionVersion) {}

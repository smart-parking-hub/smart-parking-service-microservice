package com.smartparking.identity.application.dto.verification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentCheckDto {
    private String code;
    private String result; // PASS, WARN, NOT_CHECKED
    private String documentType;
    private String field;
    private String declaredValue;
    private String extractedValue;
    private String message;
}

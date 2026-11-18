package com.smartparking.identity.application.dto.verification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRequirementDto {
    private String type;
    private boolean required;
    private int maxFiles;
}

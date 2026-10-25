package com.smartparking.identity.application.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDecisionRequest {
    @Size(max = 1000, message = "Lý do không được vượt quá 1000 ký tự")
    private String reason;
}

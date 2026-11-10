package com.smartparking.payment.application.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyRefundEvidenceRequest {

    private boolean approved;

    private boolean disputed;

    @Size(max = 500, message = "Ghi chú xác minh tối đa 500 ký tự")
    private String verificationNote;
}


package com.smartparking.payment.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitRefundEvidenceRequest {

    @NotBlank(message = "Mã giao dịch chuyển khoản hoàn tiền không được để trống")
    @Size(max = 120, message = "Mã giao dịch tối đa 120 ký tự")
    private String transactionReference;

    @Size(max = 1024, message = "Đường dẫn biên lai tối đa 1024 ký tự")
    private String evidenceUrl;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String merchantNote;

    private boolean disputed;
}


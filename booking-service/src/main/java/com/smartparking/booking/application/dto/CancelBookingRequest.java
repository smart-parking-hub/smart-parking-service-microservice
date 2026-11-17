package com.smartparking.booking.application.dto;

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
public class CancelBookingRequest {
    @NotBlank(message = "Vui lòng chọn/ghi lý do hủy")
    @Size(max = 200)
    private String reason;

    private String contactPhone;
    private String preferredResolution;
    private boolean acceptWalletRefund;
    private String note;
}

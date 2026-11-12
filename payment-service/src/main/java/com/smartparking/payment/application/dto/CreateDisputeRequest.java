package com.smartparking.payment.application.dto;

import com.smartparking.payment.application.validation.ValidHttpUrl;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDisputeRequest {
    @NotNull(message = "Thiếu bookingId")
    private Long bookingId;

    @Deprecated
    private Long merchantId;

    private Long parkingLotId;

    @NotBlank(message = "Thiếu lý do khiếu nại")
    @Size(max = 200, message = "Lý do khiếu nại tối đa 200 ký tự")
    private String reason;

    private String description;

    @Size(max = 5, message = "Tối đa 5 đường dẫn chứng từ")
    private List<@ValidHttpUrl String> evidenceUrls;
}

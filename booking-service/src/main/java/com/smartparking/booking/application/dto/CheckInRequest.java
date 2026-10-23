package com.smartparking.booking.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInRequest {
    @NotBlank(message = "Mã booking không được để trống")
    private String bookingCode;

    @NotNull(message = "Vui lòng chọn bãi đỗ đang vận hành")
    private Long parkingLotId;
}

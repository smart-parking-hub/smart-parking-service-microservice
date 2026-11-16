package com.smartparking.booking.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmCashRequest {

    @NotBlank(message = "Mã booking không được để trống")
    private String bookingCode;

    @NotNull(message = "Vui lòng chọn bãi đỗ đang vận hành")
    private Long parkingLotId;

    @DecimalMin(value = "0.0", inclusive = true, message = "Số tiền mặt xác nhận không được âm")
    private BigDecimal amount;
}

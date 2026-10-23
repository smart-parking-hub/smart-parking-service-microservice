package com.smartparking.parkinglot.application.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingRuleRequest {
    @NotNull(message = "Loại xe không được để trống")
    private VehicleTypeEnum vehicleType;

    @NotNull(message = "Giá cơ bản không được để trống")
    @DecimalMin(value = "0.01", message = "Giá cơ bản theo giờ phải lớn hơn 0")
    @Digits(integer = 8, fraction = 2, message = "Giá cơ bản tối đa 8 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal basePricePerHour;

    @DecimalMin(value = "1.00", message = "Hệ số cao điểm phải từ 1.00 đến 9.99")
    @DecimalMax(value = "9.99", message = "Hệ số cao điểm phải từ 1.00 đến 9.99")
    @Digits(integer = 1, fraction = 2, message = "Hệ số cao điểm có tối đa 2 chữ số thập phân")
    private BigDecimal surgeMultiplier;

    private LocalTime surgeStartTime;
    private LocalTime surgeEndTime;

    @DecimalMin(value = "1.00", message = "Hệ số quá giờ phải từ 1.00 đến 9.99")
    @DecimalMax(value = "9.99", message = "Hệ số quá giờ phải từ 1.00 đến 9.99")
    @Digits(integer = 1, fraction = 2, message = "Hệ số quá giờ có tối đa 2 chữ số thập phân")
    private BigDecimal overtimePenaltyMultiplier;
}

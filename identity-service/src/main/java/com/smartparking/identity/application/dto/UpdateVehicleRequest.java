package com.smartparking.identity.application.dto;

import com.smartparking.common.enums.VehicleTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateVehicleRequest {
    @NotBlank(message = "Biển số xe không được để trống")
    private String licensePlate;

    @NotNull(message = "Loại xe không được để trống")
    private VehicleTypeEnum vehicleType;

    @NotNull(message = "Trọng tải không được để trống")
    @Positive(message = "Trọng tải phải lớn hơn 0")
    private BigDecimal weightTon;

    @Size(max = 100, message = "Nhãn hiệu/model tối đa 100 ký tự")
    private String brandModel;
}

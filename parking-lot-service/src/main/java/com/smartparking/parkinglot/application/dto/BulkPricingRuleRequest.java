package com.smartparking.parkinglot.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BulkPricingRuleRequest {
    @NotEmpty(message = "Danh sách biểu phí không được để trống")
    @Size(max = 10, message = "Tối đa cấu hình 10 loại xe")
    private List<@Valid PricingRuleRequest> pricingRules;
}

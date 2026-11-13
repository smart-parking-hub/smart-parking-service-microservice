package com.smartparking.parkinglot.application.dto;

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
public class PublishLayoutRequest {

    @NotNull(message = "Thiếu expectedVersion")
    private Integer expectedVersion;

    @NotBlank(message = "Thiếu requestId")
    private String requestId;

    /**
     * Tùy chọn công khai ảnh sơ đồ nguồn làm background công khai. Mặc định false (giữ private).
     */
    private Boolean publishBackgroundImage;
}

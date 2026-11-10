package com.smartparking.identity.application.dto;

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
public class UserWarningRequest {

    @NotBlank(message = "Lý do cảnh cáo không được để trống")
    @Size(max = 1000, message = "Lý do không được vượt quá 1000 ký tự")
    private String reason;

    @Builder.Default
    private String penaltyType = "OFFICIAL_WARNING";

    @Builder.Default
    private Boolean notifyUser = true;

    @Size(max = 500, message = "Đường dẫn bằng chứng không được vượt quá 500 ký tự")
    private String evidenceUrl;
}


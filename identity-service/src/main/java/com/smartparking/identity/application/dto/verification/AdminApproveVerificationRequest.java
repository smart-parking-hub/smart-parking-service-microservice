package com.smartparking.identity.application.dto.verification;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminApproveVerificationRequest {
    @NotNull(message = "submissionVersion không được để trống")
    private Integer submissionVersion;

    @NotNull(message = "Xác nhận đã xem tài liệu không được để trống")
    @AssertTrue(message = "Admin phải xác nhận đã xem tài liệu đính kèm")
    private Boolean documentsReviewed;

    private String note;
}

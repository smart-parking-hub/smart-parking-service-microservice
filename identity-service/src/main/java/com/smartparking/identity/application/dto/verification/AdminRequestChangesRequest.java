package com.smartparking.identity.application.dto.verification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRequestChangesRequest {
    @NotNull(message = "submissionVersion không được để trống")
    private Integer submissionVersion;

    @NotEmpty(message = "Danh sách yêu cầu bổ sung không được để trống")
    @Valid
    private List<RequestedItemDto> items;

    private boolean resetLegalName;
}

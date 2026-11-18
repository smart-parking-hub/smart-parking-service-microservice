package com.smartparking.identity.application.dto.verification;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestedItemDto {
    @NotBlank(message = "Mục yêu cầu bổ sung không được để trống")
    private String key;

    @NotBlank(message = "Lý do bổ sung không được để trống")
    private String reason;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean addressed;
}

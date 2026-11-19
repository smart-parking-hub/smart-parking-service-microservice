package com.smartparking.booking.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantBookingSummaryResponse {
    @Builder.Default
    private long total = 0L;
    @Builder.Default
    private long pendingCheckIn = 0L;
    @Builder.Default
    private long parking = 0L;
    @Builder.Default
    private long completed = 0L;
    @Builder.Default
    private Map<String, Long> statusCounts = Collections.emptyMap();
}

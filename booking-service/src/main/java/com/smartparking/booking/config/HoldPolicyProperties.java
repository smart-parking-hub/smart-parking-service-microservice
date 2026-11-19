package com.smartparking.booking.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "app.booking.hold-policy")
public class HoldPolicyProperties {
    private boolean enabled = true;
    @Min(1) private int maxPendingPerAccount = 3;
    @Min(2) private int abandonmentThreshold = 2;
    @Min(1) private int windowMinutes = 5;
    @Min(1) private int cooldownMinutes = 5;
    @Min(1) private int rateLimit = 10;
    @Min(1) private int rateWindowSeconds = 60;
}

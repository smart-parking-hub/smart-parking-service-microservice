package com.smartparking.parkinglot.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.parking-layout")
@Data
public class ParkingLayoutProperties {

    /**
     * Bật hoặc tắt tính năng quản lý layout bãi xe
     */
    private boolean enabled = false;

    /**
     * Bật hoặc tắt tính năng chọn và giữ chỗ ô vật lý (physical slot booking)
     */
    private boolean physicalBookingEnabled = false;

    /**
     * Provider lưu trữ: filesystem (mặc định), mock (profile test).
     */
    private String storageProvider = "filesystem";

    /** Absolute, pre-created private directory; a persistent volume is required in containers. */
    private String filesystemRoot = "";

    private int maxObjectBytes = 5 * 1024 * 1024;

    /** Reserve, not a strict disk quota: concurrent writers can consume space after the check. */
    private long filesystemMinFreeBytes = 64L * 1024 * 1024;

}

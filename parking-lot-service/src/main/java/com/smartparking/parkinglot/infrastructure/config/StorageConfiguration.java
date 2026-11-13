package com.smartparking.parkinglot.infrastructure.config;

import com.smartparking.parkinglot.infrastructure.storage.MockStorageAdapter;
import com.smartparking.parkinglot.infrastructure.storage.StoragePort;
import com.smartparking.parkinglot.infrastructure.storage.FileSystemStorageAdapter;
import com.smartparking.parkinglot.infrastructure.storage.DisabledStorageAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import java.nio.file.Path;

@Configuration
@Slf4j
public class StorageConfiguration {

    /** Retained for existing direct configuration probes; Spring uses the Environment-aware bean. */
    public StoragePort storagePort(ParkingLayoutProperties properties) {
        return storagePort(properties, new StandardEnvironment());
    }

    @Bean
    @ConditionalOnMissingBean(StoragePort.class)
    public StoragePort storagePort(ParkingLayoutProperties properties, Environment environment) {
        String provider = properties.getStorageProvider();
        if ("filesystem".equalsIgnoreCase(provider)) {
            if (!StringUtils.hasText(properties.getFilesystemRoot())) {
                if (!properties.isEnabled() && !properties.isPhysicalBookingEnabled()) {
                    return new DisabledStorageAdapter();
                }
                throw new IllegalStateException("Filesystem layout storage requires app.parking-layout.filesystem-root");
            }
            return new FileSystemStorageAdapter(Path.of(properties.getFilesystemRoot()),
                    properties.getMaxObjectBytes(), properties.getFilesystemMinFreeBytes());
        }
        if ("mock".equalsIgnoreCase(provider) || "in-memory".equalsIgnoreCase(provider)) {
            if (!environment.acceptsProfiles(Profiles.of("test"))) {
                throw new IllegalStateException("In-memory layout storage is allowed only with the test profile");
            }
            log.info("Initializing in-memory MockStorageAdapter (storageProvider={})", provider);
            return new MockStorageAdapter();
        }

        throw new IllegalStateException("Unsupported or missing storage provider: " + provider);
    }
}

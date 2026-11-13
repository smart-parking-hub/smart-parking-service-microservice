package com.smartparking.parkinglot.infrastructure.storage;

import com.smartparking.parkinglot.infrastructure.config.ParkingLayoutProperties;
import com.smartparking.parkinglot.infrastructure.config.StorageConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorageConfigurationTest {
    @TempDir Path root;
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @Import(StorageConfiguration.class)
    @EnableConfigurationProperties(ParkingLayoutProperties.class)
    static class TestConfiguration { }

    @Test void disabledLayoutStartsWithoutCredentialsOrAStorageDirectory() {
        runner.run(context -> assertThat(context.getBean(StoragePort.class)).isInstanceOf(DisabledStorageAdapter.class));
    }

    @Test void enabledLayoutRequiresAnExplicitPersistentDirectory() {
        runner.withPropertyValues("app.parking-layout.enabled=true").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.parking-layout.physical-booking-enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void documentedEnvironmentVariablesBindAndCreateRealFilesystemStorage() {
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("layout-env", Map.of(
                        "APP_PARKING_LAYOUT_ENABLED", "true",
                        "APP_PARKING_LAYOUT_PHYSICAL_BOOKING_ENABLED", "false",
                        "APP_PARKING_LAYOUT_STORAGE_PROVIDER", "filesystem",
                        "APP_PARKING_LAYOUT_FILESYSTEM_ROOT", root.toString(),
                        "APP_PARKING_LAYOUT_MAX_OBJECT_BYTES", "2048",
                        "APP_PARKING_LAYOUT_FILESYSTEM_MIN_FREE_BYTES", "0"))))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(ParkingLayoutProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getFilesystemRoot()).isEqualTo(root.toString());
                    assertThat(properties.getMaxObjectBytes()).isEqualTo(2048);
                    assertThat(properties.getFilesystemMinFreeBytes()).isZero();
                    assertThat(context.getBean(StoragePort.class)).isInstanceOf(FileSystemStorageAdapter.class);
                });
    }

    @Test void configuredFilesystemIsAvailableEvenWhenFlagsAreOff() {
        runner.withPropertyValues("app.parking-layout.filesystem-root=" + root)
                .run(context -> assertThat(context.getBean(StoragePort.class)).isInstanceOf(FileSystemStorageAdapter.class));
    }

    @Test void invalidProviderAndInvalidFilesystemFailClosed() {
        runner.withPropertyValues("app.parking-layout.storage-provider=typo")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.parking-layout.filesystem-root=relative")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.parking-layout.filesystem-root=" + root.resolve("missing"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void volatileStorageRequiresExplicitTestProfile() {
        runner.withPropertyValues("app.parking-layout.storage-provider=mock")
                .run(context -> assertThat(context).hasFailed());
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
                .withPropertyValues("app.parking-layout.storage-provider=mock")
                .run(context -> assertThat(context.getBean(StoragePort.class)).isInstanceOf(MockStorageAdapter.class));
    }

    @Test void removedFirebaseProviderIsRejectedRatherThanSilentlyUsingFilesystem() {
        runner.withPropertyValues("app.parking-layout.storage-provider=firebase",
                        "app.parking-layout.firebase-project-id=obsolete-project")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Unsupported or missing storage provider: firebase");
                });
    }
}

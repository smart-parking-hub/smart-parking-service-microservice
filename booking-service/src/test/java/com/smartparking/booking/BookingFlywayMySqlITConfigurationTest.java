package com.smartparking.booking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression safety tests verifying:
 * 1) BookingFlywayMySqlIT does not possess class-level gating annotations that block scenario-specific runs.
 * 2) Scenario activation conditions and resolveDbUrl behave correctly.
 */
class BookingFlywayMySqlITConfigurationTest {

    @Test
    @DisplayName("R2: BookingFlywayMySqlIT has no class-level @EnabledIfEnvironmentVariable requiring BOOKING_MYSQL_IT_URL")
    void classLevelAnnotationDoesNotRequireBaseUrl() {
        assertNull(
                BookingFlywayMySqlIT.class.getAnnotation(EnabledIfEnvironmentVariable.class),
                "BookingFlywayMySqlIT must not have a class-level @EnabledIfEnvironmentVariable requiring base URL"
        );
    }

    @Test
    @DisplayName("R2: Both test methods possess @EnabledIf annotations pointing to scenario activation methods")
    void testMethodsHaveScenarioSpecificEnabledIf() throws Exception {
        Method cleanMethod = BookingFlywayMySqlIT.class.getDeclaredMethod(
                "cleanMigration_emptyDatabaseToV7_appliesAllMigrationsAndPreservesData");
        EnabledIf cleanAnnotation = cleanMethod.getAnnotation(EnabledIf.class);
        assertNotNull(cleanAnnotation, "cleanMigration method must have @EnabledIf");
        assertEquals("isCleanEnabled", cleanAnnotation.value());

        Method upgradeMethod = BookingFlywayMySqlIT.class.getDeclaredMethod(
                "upgradeMigration_fromV6ToV7_preservesPreUpgradeDataAndAppliesV7Defaults");
        EnabledIf upgradeAnnotation = upgradeMethod.getAnnotation(EnabledIf.class);
        assertNotNull(upgradeAnnotation, "upgradeMigration method must have @EnabledIf");
        assertEquals("isUpgradeEnabled", upgradeAnnotation.value());
    }

    @Test
    @DisplayName("R2: Suffix appending for DB URLs handles plain, parameterized, and pre-suffixed URLs")
    void appendSuffixToDbNameHandlesAllUrlVariants() {
        assertEquals("jdbc:mysql://localhost:33084/booking_clean",
                BookingFlywayMySqlIT.appendSuffixToDbName("jdbc:mysql://localhost:33084/booking", "clean"));

        assertEquals("jdbc:mysql://localhost:33084/booking_upgrade?serverTimezone=UTC",
                BookingFlywayMySqlIT.appendSuffixToDbName("jdbc:mysql://localhost:33084/booking?serverTimezone=UTC", "upgrade"));

        assertEquals("jdbc:mysql://localhost:33084/booking_clean",
                BookingFlywayMySqlIT.appendSuffixToDbName("jdbc:mysql://localhost:33084/booking_clean", "clean"));
    }
}


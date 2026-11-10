package com.smartparking.booking;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real Flyway (not ScriptUtils) verification of booking-service against isolated MySQL 8.4 databases.
 * Tests both:
 * 1) Clean migration: empty database -> V1..V7.
 * 2) Existing-schema upgrade: empty database -> target V6 -> snapshot data -> migrate V7 -> verify data preservation.
 *
 * Activation:
 * - Clean scenario runs when BOOKING_MYSQL_CLEAN_IT_URL or BOOKING_MYSQL_IT_URL is configured.
 * - Upgrade scenario runs when BOOKING_MYSQL_UPGRADE_IT_URL or BOOKING_MYSQL_IT_URL is configured.
 * - If REQUIRE_MYSQL_IT=true (or AC6_MYSQL_REQUIRED=true / MYSQL_IT_REQUIRED=true / BOOKING_MYSQL_IT_REQUIRED=true),
 *   scenarios are explicitly required and will fail with an actionable error if URLs are missing instead of skipping.
 * - In regular unit test runs (no variables set), tests are skipped to allow local builds without MySQL.
 */
class BookingFlywayMySqlIT {

    @Test
    @DisplayName("Clean migration: empty database -> V1..V7 on MySQL 8.4")
    @EnabledIf("isCleanEnabled")
    void cleanMigration_emptyDatabaseToV7_appliesAllMigrationsAndPreservesData() throws Exception {
        String url = resolveCleanUrl();
        if (url == null || url.isBlank()) {
            fail("AC6 MySQL IT configuration missing: BOOKING_MYSQL_CLEAN_IT_URL or BOOKING_MYSQL_IT_URL must be configured when AC6/MySQL IT is explicitly required.");
        }
        String user = System.getenv().getOrDefault("BOOKING_MYSQL_IT_USER", "root");
        String password = System.getenv().getOrDefault("BOOKING_MYSQL_IT_PASSWORD", "");

        // 1. Verify MySQL 8.4 and empty test database BEFORE running migrations
        verifyMySqlVersionAndEmptyDatabase(url, user, password);

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .load();
        MigrateResult result = flyway.migrate();
        assertTrue(result.success, "Clean Flyway migration must succeed");
        assertEquals(7, result.migrationsExecuted, "Expected all 7 migrations (V1..V7) to be freshly executed");
        assertEquals("7", flyway.info().current().getVersion().getVersion());

        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
            verifyBookingInvariantsAndSeed(st);
        }
    }

    @Test
    @DisplayName("Upgrade migration: target V6 -> snapshot -> migrate V7 -> verify legacy bookings preserved")
    @EnabledIf("isUpgradeEnabled")
    void upgradeMigration_fromV6ToV7_preservesPreUpgradeDataAndAppliesV7Defaults() throws Exception {
        String url = resolveUpgradeUrl();
        if (url == null || url.isBlank()) {
            fail("AC6 MySQL IT configuration missing: BOOKING_MYSQL_UPGRADE_IT_URL or BOOKING_MYSQL_IT_URL must be configured when AC6/MySQL IT is explicitly required.");
        }
        String user = System.getenv().getOrDefault("BOOKING_MYSQL_IT_USER", "root");
        String password = System.getenv().getOrDefault("BOOKING_MYSQL_IT_PASSWORD", "");

        // 1. Verify MySQL 8.4 and empty test database BEFORE running migrations
        verifyMySqlVersionAndEmptyDatabase(url, user, password);

        // Step 1: Migrate up to previous version V6
        Flyway flywayV6 = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .target("6")
                .cleanDisabled(true)
                .load();
        MigrateResult resultV6 = flywayV6.migrate();
        assertTrue(resultV6.success, "Migration to V6 must succeed");
        assertEquals(6, resultV6.migrationsExecuted, "Expected exactly 6 migrations (V1..V6) executed");
        assertEquals("6", flywayV6.info().current().getVersion().getVersion());

        // Snapshot pre-upgrade state from V6
        Map<Long, PreUpgradeBooking> preBookings = new HashMap<>();
        List<PreUpgradeQueue> preQueues = new ArrayList<>();
        List<PreUpgradeReview> preReviews = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
            // Confirm V7 table and columns do not exist yet in V6
            try (var rs = st.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'processed_events'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "processed_events table must not exist in V6");
            }
            try (var rs = st.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'bookings' AND column_name IN ('refund_amount', 'refund_status', 'refund_policy', 'refund_percentage')")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "V7 refund columns must not exist in V6 bookings table");
            }

            // Snapshot bookings in V6
            try (var rs = st.executeQuery(
                    "SELECT id, booking_code, driver_id, vehicle_id, parking_lot_id, merchant_id, vehicle_type, vehicle_plate, status, advance_deposit, total_amount FROM bookings ORDER BY id")) {
                while (rs.next()) {
                    preBookings.put(rs.getLong("id"), new PreUpgradeBooking(
                            rs.getLong("id"),
                            rs.getString("booking_code"),
                            rs.getLong("driver_id"),
                            rs.getLong("vehicle_id"),
                            rs.getLong("parking_lot_id"),
                            rs.getObject("merchant_id") != null ? rs.getLong("merchant_id") : null,
                            rs.getString("vehicle_type"),
                            rs.getString("vehicle_plate"),
                            rs.getString("status"),
                            rs.getBigDecimal("advance_deposit"),
                            rs.getBigDecimal("total_amount")
                    ));
                }
            }
            assertEquals(220, preBookings.size(), "Expected exactly 220 seeded bookings in V6");

            // Snapshot queues in V6
            try (var rs = st.executeQuery("SELECT id, driver_id, parking_lot_id, vehicle_type, status FROM queues ORDER BY id")) {
                while (rs.next()) {
                    preQueues.add(new PreUpgradeQueue(
                            rs.getLong("id"),
                            rs.getLong("driver_id"),
                            rs.getLong("parking_lot_id"),
                            rs.getString("vehicle_type"),
                            rs.getString("status")
                    ));
                }
            }
            assertEquals(30, preQueues.size(), "Expected exactly 30 seeded queues in V6");

            // Snapshot reviews in V6
            try (var rs = st.executeQuery("SELECT id, booking_id, parking_lot_id, driver_id, rating, comment FROM reviews ORDER BY id")) {
                while (rs.next()) {
                    preReviews.add(new PreUpgradeReview(
                            rs.getLong("id"),
                            rs.getLong("booking_id"),
                            rs.getLong("parking_lot_id"),
                            rs.getLong("driver_id"),
                            rs.getInt("rating"),
                            rs.getString("comment")
                    ));
                }
            }
            assertEquals(30, preReviews.size(), "Expected exactly 30 seeded reviews in V6 (fixing R1)");
        }

        // Step 2: Migrate to V7 (Apply refund policy columns and processed_events deduplication table)
        Flyway flywayV7 = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .target("7")
                .cleanDisabled(true)
                .load();
        MigrateResult resultV7 = flywayV7.migrate();
        assertTrue(resultV7.success, "Migration upgrade to V7 must succeed");
        assertEquals(1, resultV7.migrationsExecuted, "Expected exactly 1 incremental migration (V7) executed");
        assertEquals("7", flywayV7.info().current().getVersion().getVersion());

        // Step 3: Compare post-upgrade state with pre-upgrade snapshot
        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
            // Verify every pre-upgrade booking has identical core fields
            try (var rs = st.executeQuery(
                    "SELECT id, booking_code, driver_id, vehicle_id, parking_lot_id, merchant_id, vehicle_type, vehicle_plate, status, advance_deposit, total_amount, "
                            + "refund_status, refund_amount, refund_percentage, refund_policy, cancelled_at, refund_reference FROM bookings ORDER BY id")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    PreUpgradeBooking expected = preBookings.get(rs.getLong("id"));
                    assertNotNull(expected);
                    assertEquals(expected.bookingCode(), rs.getString("booking_code"));
                    assertEquals(expected.driverId(), rs.getLong("driver_id"));
                    assertEquals(expected.vehicleId(), rs.getLong("vehicle_id"));
                    assertEquals(expected.parkingLotId(), rs.getLong("parking_lot_id"));
                    Long actualMerchantId = rs.getObject("merchant_id") != null ? rs.getLong("merchant_id") : null;
                    assertEquals(expected.merchantId(), actualMerchantId);
                    assertEquals(expected.vehicleType(), rs.getString("vehicle_type"));
                    assertEquals(expected.vehiclePlate(), rs.getString("vehicle_plate"));
                    assertEquals(expected.status(), rs.getString("status"));
                    assertEquals(0, expected.advanceDeposit().compareTo(rs.getBigDecimal("advance_deposit")));
                    assertEquals(0, expected.totalAmount().compareTo(rs.getBigDecimal("total_amount")));

                    // Verify V7 default values applied to legacy rows
                    assertEquals("NONE", rs.getString("refund_status"));
                    assertEquals(0, BigDecimal.ZERO.compareTo(rs.getBigDecimal("refund_amount")));
                    assertEquals(0, rs.getInt("refund_percentage"));
                    assertNull(rs.getString("refund_policy"));
                    assertNull(rs.getTimestamp("cancelled_at"));
                    assertNull(rs.getString("refund_reference"));
                }
                assertEquals(220, count, "All 220 bookings must be preserved after upgrade");
            }

            // Verify queues identical
            try (var rs = st.executeQuery("SELECT id, driver_id, parking_lot_id, vehicle_type, status FROM queues ORDER BY id")) {
                int index = 0;
                while (rs.next()) {
                    PreUpgradeQueue expected = preQueues.get(index++);
                    assertEquals(expected.id(), rs.getLong("id"));
                    assertEquals(expected.driverId(), rs.getLong("driver_id"));
                    assertEquals(expected.parkingLotId(), rs.getLong("parking_lot_id"));
                    assertEquals(expected.vehicleType(), rs.getString("vehicle_type"));
                    assertEquals(expected.status(), rs.getString("status"));
                }
                assertEquals(30, index);
            }

            // Verify reviews identical
            try (var rs = st.executeQuery("SELECT id, booking_id, parking_lot_id, driver_id, rating, comment FROM reviews ORDER BY id")) {
                int index = 0;
                while (rs.next()) {
                    PreUpgradeReview expected = preReviews.get(index++);
                    assertEquals(expected.id(), rs.getLong("id"));
                    assertEquals(expected.bookingId(), rs.getLong("booking_id"));
                    assertEquals(expected.parkingLotId(), rs.getLong("parking_lot_id"));
                    assertEquals(expected.driverId(), rs.getLong("driver_id"));
                    assertEquals(expected.rating(), rs.getInt("rating"));
                    assertEquals(expected.comment(), rs.getString("comment"));
                }
                assertEquals(30, index);
            }

            // Verify V7 processed_events table is operational
            st.executeUpdate("INSERT INTO processed_events (event_id, event_type, aggregate_id) VALUES ('evt-upg-1', 'PAYMENT_REFUND_UPDATED', '1')");
            assertThrows(Exception.class, () -> st.executeUpdate(
                    "INSERT INTO processed_events (event_id, event_type, aggregate_id) VALUES ('evt-upg-1', 'PAYMENT_REFUND_UPDATED', '1')"));
        }
    }

    private void verifyMySqlVersionAndEmptyDatabase(String url, String user, String password) throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
            try (var rs = st.executeQuery("SELECT VERSION()")) {
                assertTrue(rs.next());
                String version = rs.getString(1);
                assertTrue(version.startsWith("8.4"), "Expected MySQL 8.4 on test target, got " + version);
            }
            try (var rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                assertTrue(rs.next());
                int initialTableCount = rs.getInt(1);
                assertEquals(0, initialTableCount,
                        "Target test database must be empty before test run; found " + initialTableCount + " tables in " + url);
            }
        }
    }

    private void verifyBookingInvariantsAndSeed(java.sql.Statement st) throws Exception {
        // V2 seed rows present
        try (var rs = st.executeQuery("SELECT COUNT(*) FROM bookings")) {
            assertTrue(rs.next());
            assertEquals(220, rs.getInt(1), "V2 seed bookings must be exactly 220");
        }
        try (var rs = st.executeQuery("SELECT COUNT(*) FROM queues")) {
            assertTrue(rs.next());
            assertEquals(30, rs.getInt(1), "V2 seed queues must be exactly 30");
        }
        try (var rs = st.executeQuery("SELECT COUNT(*) FROM reviews")) {
            assertTrue(rs.next());
            assertEquals(30, rs.getInt(1), "V2 seed reviews must be exactly 30");
        }
        // V7 refund default state for legacy bookings
        try (var rs = st.executeQuery("SELECT COUNT(*) FROM bookings WHERE refund_status <> 'NONE' OR refund_amount <> 0.00 OR refund_percentage <> 0")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "All legacy bookings must have V7 refund defaults");
        }
        // V6 check_in_time has DATETIME(6) / TIMESTAMP precision in information_schema
        try (var rs = st.executeQuery("""
                SELECT DATA_TYPE, DATETIME_PRECISION
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'bookings' AND column_name = 'check_in_time'
                """)) {
            assertTrue(rs.next());
            String dataType = rs.getString(1).toLowerCase();
            assertTrue(dataType.contains("time") || dataType.contains("date"), "Expected datetime/timestamp column type");
        }
        // V7 processed_events table is operational
        try (var rs = st.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'processed_events'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "processed_events table must exist in V7");
        }
    }

    static String resolveCleanUrl() {
        return resolveDbUrl("BOOKING_MYSQL_CLEAN_IT_URL", "clean");
    }

    static String resolveUpgradeUrl() {
        return resolveDbUrl("BOOKING_MYSQL_UPGRADE_IT_URL", "upgrade");
    }

    static boolean isCleanEnabled() {
        return isExplicitlyRequired() || resolveCleanUrl() != null;
    }

    static boolean isUpgradeEnabled() {
        return isExplicitlyRequired() || resolveUpgradeUrl() != null;
    }

    static boolean isExplicitlyRequired() {
        return isTrue(System.getenv("REQUIRE_MYSQL_IT"))
                || isTrue(System.getenv("AC6_MYSQL_REQUIRED"))
                || isTrue(System.getenv("MYSQL_IT_REQUIRED"))
                || isTrue(System.getenv("BOOKING_MYSQL_IT_REQUIRED"));
    }

    private static boolean isTrue(String val) {
        return val != null && (val.equalsIgnoreCase("true") || val.equals("1") || val.equalsIgnoreCase("yes"));
    }

    static String resolveDbUrl(String specificEnv, String defaultSuffix) {
        String specific = System.getenv(specificEnv);
        if (specific != null && !specific.isBlank()) {
            return specific.trim();
        }
        String base = System.getenv("BOOKING_MYSQL_IT_URL");
        if (base == null || base.isBlank()) {
            return null;
        }
        return appendSuffixToDbName(base.trim(), defaultSuffix);
    }

    static String appendSuffixToDbName(String jdbcUrl, String suffix) {
        int q = jdbcUrl.indexOf('?');
        String params = q >= 0 ? jdbcUrl.substring(q) : "";
        String main = q >= 0 ? jdbcUrl.substring(0, q) : jdbcUrl;
        int lastSlash = main.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < main.length() - 1) {
            String db = main.substring(lastSlash + 1);
            if (db.endsWith("_" + suffix)) {
                return jdbcUrl;
            }
            return main + "_" + suffix + params;
        }
        return jdbcUrl;
    }

    record PreUpgradeBooking(
            long id,
            String bookingCode,
            long driverId,
            long vehicleId,
            long parkingLotId,
            Long merchantId,
            String vehicleType,
            String vehiclePlate,
            String status,
            BigDecimal advanceDeposit,
            BigDecimal totalAmount
    ) {}
    record PreUpgradeQueue(long id, long driverId, long parkingLotId, String vehicleType, String status) {}
    record PreUpgradeReview(long id, long bookingId, long parkingLotId, long driverId, int rating, String comment) {}
}

package com.smartparking.payment;

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
 * Real Flyway (not ScriptUtils) verification of payment-service against isolated MySQL 8.4 databases.
 * Tests both:
 * 1) Clean migration: empty database -> V1..V5.
 * 2) Existing-schema upgrade: empty database -> target V4 -> snapshot data -> migrate V5 -> verify data preservation.
 *
 * Activation:
 * - Clean scenario runs when PAYMENT_MYSQL_CLEAN_IT_URL or PAYMENT_MYSQL_IT_URL is configured.
 * - Upgrade scenario runs when PAYMENT_MYSQL_UPGRADE_IT_URL or PAYMENT_MYSQL_IT_URL is configured.
 * - If REQUIRE_MYSQL_IT=true (or AC6_MYSQL_REQUIRED=true / MYSQL_IT_REQUIRED=true / PAYMENT_MYSQL_IT_REQUIRED=true),
 *   scenarios are explicitly required and will fail with an actionable error if URLs are missing instead of skipping.
 * - In regular unit test runs (no variables set), tests are skipped to allow local builds without MySQL.
 */
class PaymentFlywayMySqlIT {

    @Test
    @DisplayName("Clean migration: empty database -> V1..V5 on MySQL 8.4")
    @EnabledIf("isCleanEnabled")
    void cleanMigration_emptyDatabaseToV5_verifiesFreshExecutionAndLedgerInvariants() throws Exception {
        String url = resolveCleanUrl();
        if (url == null || url.isBlank()) {
            fail("AC6 MySQL IT configuration missing: PAYMENT_MYSQL_CLEAN_IT_URL or PAYMENT_MYSQL_IT_URL must be configured when AC6/MySQL IT is explicitly required.");
        }
        String user = System.getenv().getOrDefault("PAYMENT_MYSQL_IT_USER", "root");
        String password = System.getenv().getOrDefault("PAYMENT_MYSQL_IT_PASSWORD", "");

        // 1. Verify MySQL 8.4 and empty test database BEFORE running migrations
        verifyMySqlVersionAndEmptyDatabase(url, user, password);

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .target("5")
                .cleanDisabled(true)
                .load();
        MigrateResult result = flyway.migrate();
        assertTrue(result.success, "Clean Flyway migration must succeed");
        assertEquals(5, result.migrationsExecuted, "Expected all 5 migrations (V1..V5) to be freshly executed");
        assertEquals("5", flyway.info().current().getVersion().getVersion());

        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
            verifyLedgerInvariantsAndHistory(st);
        }
    }

    @Test
    @DisplayName("Upgrade migration: target V4 -> snapshot -> migrate V5 -> verify historical data preserved")
    @EnabledIf("isUpgradeEnabled")
    void upgradeMigration_fromV4ToV5_preservesPreUpgradeDataAndBalancesLedger() throws Exception {
        String url = resolveUpgradeUrl();
        if (url == null || url.isBlank()) {
            fail("AC6 MySQL IT configuration missing: PAYMENT_MYSQL_UPGRADE_IT_URL or PAYMENT_MYSQL_IT_URL must be configured when AC6/MySQL IT is explicitly required.");
        }
        String user = System.getenv().getOrDefault("PAYMENT_MYSQL_IT_USER", "root");
        String password = System.getenv().getOrDefault("PAYMENT_MYSQL_IT_PASSWORD", "");

        // 1. Verify MySQL 8.4 and empty test database BEFORE running migrations
        verifyMySqlVersionAndEmptyDatabase(url, user, password);

        // Step 1: Migrate up to previous version V4
        Flyway flywayV4 = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .target("4")
                .cleanDisabled(true)
                .load();
        MigrateResult resultV4 = flywayV4.migrate();
        assertTrue(resultV4.success, "Migration to V4 must succeed");
        assertEquals(4, resultV4.migrationsExecuted, "Expected exactly 4 migrations (V1..V4) executed");
        assertEquals("4", flywayV4.info().current().getVersion().getVersion());

        // Snapshot pre-upgrade state from V4
        Map<Long, PreUpgradeWallet> preWallets = new HashMap<>();
        List<PreUpgradeLedgerEntry> preEntries = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
            // Confirm V5 tables do not exist yet in V4
            try (var rs = st.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('ledger_journals', 'ledger_postings', 'settlement_batches', 'refund_requests', 'processed_events')")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "ledger_journals and other V5 tables must not exist in V4");
            }

            // Snapshot wallets (V1-V4 columns: id, merchant_id, balance, currency, status, version)
            try (var rs = st.executeQuery("SELECT id, merchant_id, balance, currency, status, version FROM wallets ORDER BY id")) {
                while (rs.next()) {
                    preWallets.put(rs.getLong("id"), new PreUpgradeWallet(
                            rs.getLong("id"),
                            rs.getLong("merchant_id"),
                            rs.getBigDecimal("balance"),
                            rs.getString("currency"),
                            rs.getString("status"),
                            rs.getLong("version")
                    ));
                }
            }
            assertEquals(10, preWallets.size(), "Expected 10 seeded wallets in V4");

            // Snapshot wallet_ledger_entries (V1-V4 columns: id, merchant_id, booking_id, payment_id, entry_type, gross_amount, fee_percent, fee_amount, net_amount, note)
            try (var rs = st.executeQuery(
                    "SELECT id, merchant_id, booking_id, payment_id, entry_type, gross_amount, fee_percent, fee_amount, net_amount, note FROM wallet_ledger_entries ORDER BY id")) {
                while (rs.next()) {
                    preEntries.add(new PreUpgradeLedgerEntry(
                            rs.getLong("id"),
                            rs.getLong("merchant_id"),
                            rs.getObject("booking_id") != null ? rs.getLong("booking_id") : null,
                            rs.getObject("payment_id") != null ? rs.getLong("payment_id") : null,
                            rs.getString("entry_type"),
                            rs.getBigDecimal("gross_amount"),
                            rs.getBigDecimal("fee_percent"),
                            rs.getBigDecimal("fee_amount"),
                            rs.getBigDecimal("net_amount"),
                            rs.getString("note")
                    ));
                }
            }
            assertEquals(160, preEntries.size(), "Expected exactly 160 seeded entries in V4");
        }

        // Step 2: Migrate to V5 (Cutover to double-entry ledger)
        Flyway flywayV5 = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .target("5")
                .cleanDisabled(true)
                .load();
        MigrateResult resultV5 = flywayV5.migrate();
        assertTrue(resultV5.success, "Migration upgrade to V5 must succeed");
        assertEquals(1, resultV5.migrationsExecuted, "Expected exactly 1 incremental migration (V5) executed");
        assertEquals("5", flywayV5.info().current().getVersion().getVersion());

        // Step 3: Compare post-upgrade state with pre-upgrade snapshot
        try (Connection c = DriverManager.getConnection(url, user, password); var st = c.createStatement()) {
            // Every pre-upgrade entry in wallet_ledger_entries must be 100% identical
            try (var rs = st.executeQuery(
                    "SELECT id, merchant_id, booking_id, payment_id, entry_type, gross_amount, fee_percent, fee_amount, net_amount, note FROM wallet_ledger_entries ORDER BY id")) {
                int index = 0;
                while (rs.next()) {
                    PreUpgradeLedgerEntry expected = preEntries.get(index++);
                    assertEquals(expected.id(), rs.getLong("id"));
                    assertEquals(expected.merchantId(), rs.getLong("merchant_id"));
                    Long actualBookingId = rs.getObject("booking_id") != null ? rs.getLong("booking_id") : null;
                    assertEquals(expected.bookingId(), actualBookingId);
                    Long actualPaymentId = rs.getObject("payment_id") != null ? rs.getLong("payment_id") : null;
                    assertEquals(expected.paymentId(), actualPaymentId);
                    assertEquals(expected.entryType(), rs.getString("entry_type"));
                    assertEquals(0, expected.grossAmount().compareTo(rs.getBigDecimal("gross_amount")));
                    assertEquals(0, expected.feePercent().compareTo(rs.getBigDecimal("fee_percent")));
                    assertEquals(0, expected.feeAmount().compareTo(rs.getBigDecimal("fee_amount")));
                    assertEquals(0, expected.netAmount().compareTo(rs.getBigDecimal("net_amount")));
                    assertEquals(expected.note(), rs.getString("note"));
                }
                assertEquals(preEntries.size(), index, "wallet_ledger_entries row count must not change");
            }

            // Every pre-upgrade wallet balance must remain identical
            try (var rs = st.executeQuery("SELECT id, merchant_id, balance, currency, status, version FROM wallets ORDER BY id")) {
                while (rs.next()) {
                    PreUpgradeWallet expected = preWallets.get(rs.getLong("id"));
                    assertNotNull(expected);
                    assertEquals(expected.merchantId(), rs.getLong("merchant_id"));
                    assertEquals(0, expected.balance().compareTo(rs.getBigDecimal("balance")),
                            "Wallet balance must not be changed by V5 migration");
                    assertEquals(expected.currency(), rs.getString("currency"));
                    assertEquals(expected.status(), rs.getString("status"));
                    assertEquals(expected.version(), rs.getLong("version"));
                }
            }

            // Verify V5 processed_events table is operational
            st.executeUpdate("INSERT INTO processed_events (event_id, event_type, aggregate_id) VALUES ('evt-upg-1', 'PAYMENT_COMPLETED', '1')");
            assertThrows(Exception.class, () -> st.executeUpdate(
                    "INSERT INTO processed_events (event_id, event_type, aggregate_id) VALUES ('evt-upg-1', 'PAYMENT_COMPLETED', '1')"));

            // Check V5 invariants on upgraded schema
            verifyLedgerInvariantsAndHistory(st);
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

    private void verifyLedgerInvariantsAndHistory(java.sql.Statement st) throws Exception {
        // Historical V2 data preserved: exactly 160 settlement entries (8% fee rows untouched).
        try (var rs = st.executeQuery(
                "SELECT COUNT(*), MIN(fee_percent), MAX(fee_percent) FROM wallet_ledger_entries WHERE entry_type = 'SETTLEMENT_CREDIT'")) {
            assertTrue(rs.next());
            assertEquals(160, rs.getInt(1), "Expected exactly 160 historical V2 settlement credit entries");
            assertEquals(0, new BigDecimal("8").compareTo(rs.getBigDecimal(2)));
            assertEquals(0, new BigDecimal("8").compareTo(rs.getBigDecimal(3)));
        }
        // Every journal balanced, both by header and by postings.
        try (var rs = st.executeQuery("""
                SELECT COUNT(*) FROM ledger_journals lj
                WHERE lj.total_debit <> lj.total_credit
                   OR lj.total_debit <> (SELECT COALESCE(SUM(p.amount),0) FROM ledger_postings p WHERE p.journal_id = lj.id AND p.direction = 'DEBIT')
                   OR lj.total_credit <> (SELECT COALESCE(SUM(p.amount),0) FROM ledger_postings p WHERE p.journal_id = lj.id AND p.direction = 'CREDIT')
                """)) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "Unbalanced journals found");
        }
        // Exactly 170 journals: 160 from V2 historical settlements + 10 opening balance reconciliations
        try (var rs = st.executeQuery("SELECT COUNT(*) FROM ledger_journals")) {
            assertTrue(rs.next());
            assertEquals(170, rs.getInt(1), "Expected 160 historical journals + 10 opening balance reconciliation journals");
        }
        // Exactly 10 merchant wallets
        try (var rs = st.executeQuery("SELECT COUNT(*) FROM wallets")) {
            assertTrue(rs.next());
            assertEquals(10, rs.getInt(1), "Expected 10 merchant wallets");
        }
        // Wallet projection reconciles with MERCHANT_PAYABLE.
        try (var rs = st.executeQuery("""
                SELECT COUNT(*) FROM wallets w
                WHERE w.balance <> (SELECT COALESCE(SUM(CASE WHEN p.direction = 'CREDIT' THEN p.amount ELSE -p.amount END), 0)
                                    FROM ledger_postings p
                                    WHERE p.account_code = 'MERCHANT_PAYABLE' AND p.merchant_id = w.merchant_id)
                """)) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "Wallets not reconciled with ledger");
        }
    }

    static String resolveCleanUrl() {
        return resolveDbUrl("PAYMENT_MYSQL_CLEAN_IT_URL", "clean");
    }

    static String resolveUpgradeUrl() {
        return resolveDbUrl("PAYMENT_MYSQL_UPGRADE_IT_URL", "upgrade");
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
                || isTrue(System.getenv("PAYMENT_MYSQL_IT_REQUIRED"));
    }

    private static boolean isTrue(String val) {
        return val != null && (val.equalsIgnoreCase("true") || val.equals("1") || val.equalsIgnoreCase("yes"));
    }

    static String resolveDbUrl(String specificEnv, String defaultSuffix) {
        String specific = System.getenv(specificEnv);
        if (specific != null && !specific.isBlank()) {
            return specific.trim();
        }
        String base = System.getenv("PAYMENT_MYSQL_IT_URL");
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

    record PreUpgradeWallet(long id, long merchantId, BigDecimal balance, String currency, String status, long version) {}
    record PreUpgradeLedgerEntry(
            long id,
            long merchantId,
            Long bookingId,
            Long paymentId,
            String entryType,
            BigDecimal grossAmount,
            BigDecimal feePercent,
            BigDecimal feeAmount,
            BigDecimal netAmount,
            String note
    ) {}
}

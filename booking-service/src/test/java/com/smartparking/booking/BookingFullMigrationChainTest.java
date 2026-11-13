package com.smartparking.booking;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL-level (ScriptUtils, NOT Flyway) verification of the complete booking migration chain V1..V7 on
 * H2 MODE=MySQL, including the V2 demo seed so that pre-existing rows are carried through every later
 * migration. Released migrations are not modified on disk; the only test-side translation is splitting
 * MySQL multi-clause "ALTER TABLE t ADD/MODIFY a, ADD/MODIFY b" (V5, V6) into single-clause statements,
 * which H2 does not accept. This is not a substitute for running Flyway against real MySQL 8.4.
 */
class BookingFullMigrationChainTest {

    private static final String[] CHAIN = {
            "V1__init_booking_schema.sql",
            "V2__seed_demo_data.sql",
            "V3__add_hourly_rate_to_bookings.sql",
            "V4__add_parking_lot_address_to_bookings.sql",
            "V5__snapshot_booking_tariff.sql",
            "V6__booking_gate_time_precision.sql",
            "V7__booking_refund_policy_and_processed_events.sql",
            "V8__add_physical_slot_to_bookings.sql"
    };

    private static final String FINGERPRINT_SQL = """
            SELECT COUNT(*),
                   COALESCE(SUM(id), 0),
                   COALESCE(SUM(advance_deposit), 0),
                   COALESCE(SUM(total_amount), 0),
                   SUM(CASE WHEN check_in_time IS NOT NULL THEN 1 ELSE 0 END),
                   SUM(CASE WHEN check_out_time IS NOT NULL THEN 1 ELSE 0 END),
                   COUNT(DISTINCT booking_code),
                   COUNT(DISTINCT status)
            FROM bookings
            """;

    @Test
    void fullChainV1toV7PreservesSeededBookingsAndAppliesSafeDefaults() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            run(connection, CHAIN[0]);
            run(connection, CHAIN[1]);
            List<String> afterSeed = fingerprint(connection);
            List<String> statusCountsAfterSeed = statusCounts(connection);
            int queues = count(connection, "SELECT COUNT(*) FROM queues");
            int reviews = count(connection, "SELECT COUNT(*) FROM reviews");
            assertEquals(220, Integer.parseInt(afterSeed.get(0)), "V2 seed must create exactly 220 historical bookings");
            assertEquals(30, queues, "V2 seed must create exactly 30 queues");
            assertEquals(30, reviews, "V2 seed must create exactly 30 reviews");

            for (int i = 2; i < CHAIN.length; i++) {
                run(connection, CHAIN[i]);
                assertEquals(afterSeed, fingerprint(connection), "Historical bookings changed by " + CHAIN[i]);
            }

            assertEquals(statusCountsAfterSeed, statusCounts(connection));
            assertEquals(queues, count(connection, "SELECT COUNT(*) FROM queues"));
            assertEquals(reviews, count(connection, "SELECT COUNT(*) FROM reviews"));

            // Columns added by later migrations are nullable/defaulted for legacy rows.
            int total = Integer.parseInt(afterSeed.get(0));
            assertEquals(total, count(connection,
                    "SELECT COUNT(*) FROM bookings WHERE refund_status = 'NONE' AND refund_amount = 0 AND refund_percentage = 0"
                            + " AND cancelled_at IS NULL AND refund_policy IS NULL AND refund_reference IS NULL"));
            assertEquals(total, count(connection,
                    "SELECT COUNT(*) FROM bookings WHERE request_key IS NULL AND parking_lot_address IS NULL"
                            + " AND surge_multiplier IS NULL AND overtime_penalty_multiplier IS NULL"
                            + " AND physical_slot_id IS NULL AND physical_slot_code IS NULL AND layout_publication_id IS NULL"));

            // V6: gate timestamps keep sub-second precision after migration.
            try (var st = connection.createStatement()) {
                st.executeUpdate("UPDATE bookings SET check_in_time = TIMESTAMP '2026-03-01 08:05:00.123456' WHERE id = 11");
                try (var rs = st.executeQuery("SELECT check_in_time FROM bookings WHERE id = 11")) {
                    assertTrue(rs.next());
                    assertEquals(123456000, rs.getTimestamp(1).getNanos());
                }
            }
            // V7: request_key uniqueness (V5) and processed_events dedup table usable.
            try (var st = connection.createStatement()) {
                st.executeUpdate("UPDATE bookings SET request_key = 'rk-1' WHERE id = 1");
                assertThrows(Exception.class,
                        () -> st.executeUpdate("UPDATE bookings SET request_key = 'rk-1' WHERE id = 2"));
                st.executeUpdate("INSERT INTO processed_events (event_id, event_type, aggregate_id) VALUES ('e1','PAYMENT_REFUND_UPDATED','1')");
                assertThrows(Exception.class, () -> st.executeUpdate(
                        "INSERT INTO processed_events (event_id, event_type, aggregate_id) VALUES ('e1','PAYMENT_REFUND_UPDATED','1')"));
            }
        }
    }

    private static void run(Connection connection, String file) throws Exception {
        String sql = new String(new ClassPathResource("db/migration/" + file).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        ScriptUtils.executeSqlScript(connection,
                new ByteArrayResource(translateForH2(sql).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Test-only dialect shim (files on disk untouched):
     * 1) MySQL DATE_ADD('lit', INTERVAL (expr) HOUR) in V2 seed -> H2 DATEADD(HOUR, expr, TIMESTAMP 'lit');
     * 2) multi-clause ALTER TABLE (V5/V6) -> single-clause statements.
     */
    static String translateForH2(String sql) {
        String dateAdd = sql.replaceAll("(?i)DATE_ADD\\(\\s*'([^']+)'\\s*,\\s*INTERVAL\\s*\\(([^)]*)\\)\\s*HOUR\\s*\\)",
                "DATEADD(HOUR, ($2), TIMESTAMP '$1')");
        return splitMultiClauseAlter(dateAdd);
    }

    /** Splits "ALTER TABLE t X a, X b;" (X = ADD COLUMN | MODIFY COLUMN) into one statement per clause. */
    static String splitMultiClauseAlter(String sql) {
        Pattern alter = Pattern.compile("(?is)ALTER\\s+TABLE\\s+(\\w+)\\s+((?:ADD|MODIFY)\\s+COLUMN\\b[^;]*);");
        Matcher m = alter.matcher(sql);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String table = m.group(1);
            String[] clauses = m.group(2).split("(?i),\\s*(?=(ADD|MODIFY)\\s+COLUMN\\b)");
            StringBuilder rep = new StringBuilder();
            for (String clause : clauses) {
                rep.append("ALTER TABLE ").append(table).append(' ').append(clause.trim()).append(";\n");
            }
            m.appendReplacement(out, Matcher.quoteReplacement(rep.toString()));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static List<String> fingerprint(Connection c) throws Exception {
        try (var st = c.createStatement(); var rs = st.executeQuery(FINGERPRINT_SQL)) {
            rs.next();
            List<String> values = new ArrayList<>();
            for (int i = 1; i <= 8; i++) {
                values.add(new java.math.BigDecimal(rs.getString(i)).stripTrailingZeros().toPlainString());
            }
            return values;
        }
    }

    private static List<String> statusCounts(Connection c) throws Exception {
        List<String> values = new ArrayList<>();
        try (var st = c.createStatement();
             var rs = st.executeQuery("SELECT status, COUNT(*) FROM bookings GROUP BY status ORDER BY status")) {
            while (rs.next()) {
                values.add(rs.getString(1) + "=" + rs.getInt(2));
            }
        }
        return values;
    }

    private static int count(Connection c, String sql) throws Exception {
        try (var st = c.createStatement(); var rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}


package com.smartparking.booking;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookingSchemaMigrationTest {

    @Test
    void upgradeAddsNullableAddressAndRefundPolicyColumnsWhilePreservingExistingBooking() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            // Exercise the production DDL; demo seed data is deliberately excluded.
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V1__init_booking_schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V3__add_hourly_rate_to_bookings.sql"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO bookings (booking_code, driver_id, vehicle_id, parking_lot_id,
                            vehicle_type, expires_at)
                        VALUES ('BK-MIGRATION', 1, 2, 3, 'CONTAINER', CURRENT_TIMESTAMP)
                        """);
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V4__add_parking_lot_address_to_bookings.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V7__booking_refund_policy_and_processed_events.sql"));
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery(
                         "SELECT parking_lot_address, refund_status, refund_amount, refund_percentage FROM bookings WHERE booking_code = 'BK-MIGRATION'")) {
                assertTrue(rows.next());
                assertNull(rows.getString(1));
                assertEquals("NONE", rows.getString(2));
                assertEquals(0, BigDecimal.ZERO.compareTo(rows.getBigDecimal(3)));
                assertEquals(0, rows.getInt(4));
                assertFalse(rows.next());
            }
            String address = "A".repeat(255);
            try (var statement = connection.prepareStatement(
                    "UPDATE bookings SET parking_lot_address = ?, refund_status = 'REQUESTED', refund_amount = 25000.00, refund_percentage = 50 WHERE booking_code = 'BK-MIGRATION'")) {
                statement.setString(1, address);
                assertEquals(1, statement.executeUpdate());
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT parking_lot_address, refund_status, refund_amount, refund_percentage FROM bookings")) {
                assertTrue(rows.next());
                assertEquals(address, rows.getString(1));
                assertEquals("REQUESTED", rows.getString(2));
                assertEquals(0, new BigDecimal("25000.00").compareTo(rows.getBigDecimal(3)));
                assertEquals(50, rows.getInt(4));
            }
            try (var statement = connection.prepareStatement(
                    "INSERT INTO processed_events (event_id, event_type, aggregate_id) VALUES (?, ?, ?)")) {
                statement.setString(1, "evt-1");
                statement.setString(2, "PAYMENT_REFUND_UPDATED");
                statement.setString(3, "1");
                assertEquals(1, statement.executeUpdate());
            }

            // Exercise V8 migration
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V8__add_physical_slot_to_bookings.sql"));
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery(
                         "SELECT physical_slot_id, physical_slot_code, layout_publication_id FROM bookings WHERE booking_code = 'BK-MIGRATION'")) {
                assertTrue(rows.next());
                assertNull(rows.getString(1));
                assertNull(rows.getString(2));
                assertNull(rows.getString(3));
            }
            try (var statement = connection.prepareStatement(
                    "UPDATE bookings SET physical_slot_id = ?, physical_slot_code = ?, layout_publication_id = ? WHERE booking_code = 'BK-MIGRATION'")) {
                statement.setString(1, "ps-101");
                statement.setString(2, "A-01");
                statement.setString(3, "pub-202");
                assertEquals(1, statement.executeUpdate());
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery(
                         "SELECT physical_slot_id, physical_slot_code, layout_publication_id FROM bookings WHERE booking_code = 'BK-MIGRATION'")) {
                assertTrue(rows.next());
                assertEquals("ps-101", rows.getString(1));
                assertEquals("A-01", rows.getString(2));
                assertEquals("pub-202", rows.getString(3));
            }
        }
    }
}

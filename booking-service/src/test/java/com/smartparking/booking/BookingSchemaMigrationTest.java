package com.smartparking.booking;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookingSchemaMigrationTest {

    @Test
    void upgradeAddsNullableAddressAndPreservesExistingBooking() throws Exception {
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
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery(
                         "SELECT parking_lot_address FROM bookings WHERE booking_code = 'BK-MIGRATION'")) {
                assertTrue(rows.next());
                assertNull(rows.getString(1));
                assertFalse(rows.next());
            }
            String address = "A".repeat(255);
            try (var statement = connection.prepareStatement(
                    "UPDATE bookings SET parking_lot_address = ? WHERE booking_code = 'BK-MIGRATION'")) {
                statement.setString(1, address);
                assertEquals(1, statement.executeUpdate());
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT parking_lot_address FROM bookings")) {
                assertTrue(rows.next());
                assertEquals(address, rows.getString(1));
            }
        }
    }
}

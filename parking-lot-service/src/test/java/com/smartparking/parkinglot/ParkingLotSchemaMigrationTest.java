package com.smartparking.parkinglot;

import com.smartparking.common.enums.ParkingLotStatusEnum;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ParkingLotSchemaMigrationTest {

    @Test
    void upgradePreservesLotAndAllowsEveryDomainStatus() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V1__init_parkinglot_schema.sql"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO parking_lots (id, merchant_id, name, address, latitude, longitude)
                        VALUES (1, 4, 'Migration lot', 'Existing address', 10.75, 106.65)
                        """);
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "UPDATE parking_lots SET status = 'CHANGES_REQUESTED' WHERE id = 1"));
            }

            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V3__add_changes_requested_parking_lot_status.sql"));
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT name, address, status FROM parking_lots WHERE id = 1")) {
                assertTrue(rows.next());
                assertEquals("Migration lot", rows.getString("name"));
                assertEquals("Existing address", rows.getString("address"));
                assertEquals("PENDING_APPROVAL", rows.getString("status"));
            }
            for (var status : ParkingLotStatusEnum.values()) {
                try (var statement = connection.prepareStatement("UPDATE parking_lots SET status = ? WHERE id = 1")) {
                    statement.setString(1, status.name());
                    assertEquals(1, statement.executeUpdate());
                }
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT status FROM parking_lots WHERE id = 1")) {
                    assertTrue(rows.next());
                    assertEquals(status.name(), rows.getString(1));
                }
            }
            try (var statement = connection.createStatement()) {
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "UPDATE parking_lots SET status = 'UNKNOWN' WHERE id = 1"));
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "UPDATE parking_lots SET status = NULL WHERE id = 1"));
                statement.executeUpdate("""
                        INSERT INTO parking_lots (id, merchant_id, name, address, latitude, longitude)
                        VALUES (2, 4, 'New lot', 'New address', 10.75, 106.65)
                        """);
                try (var rows = statement.executeQuery("SELECT status FROM parking_lots WHERE id = 2")) {
                    assertTrue(rows.next());
                    assertEquals("PENDING_APPROVAL", rows.getString(1));
                }
            }
        }
    }

    @Test
    void fullMigrationChainCreatesLayoutAndPhysicalSlotTablesAndPreservesLegacyData() throws Exception {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__init_parkinglot_schema.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V3__add_changes_requested_parking_lot_status.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V4__idempotent_slot_holds.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V5__slot_hold_reconciliation.sql"));

            // Insert legacy parking lot, slot, hold
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO parking_lots (id, merchant_id, name, address, latitude, longitude)
                        VALUES (10, 4, 'Legacy Lot', '123 Street', 10.75, 106.65)
                        """);
                statement.executeUpdate("""
                        INSERT INTO slots (id, parking_lot_id, vehicle_type, total_capacity, available_capacity)
                        VALUES (100, 10, 'SEDAN_SUV', 50, 49)
                        """);
                statement.executeUpdate("""
                        INSERT INTO slot_holds (booking_id, slot_id, released)
                        VALUES (9999, 100, 0)
                        """);
            }

            // Apply the existing V7 before the new additive layout migration.
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V7__admin_operations_outbox.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V8__parking_layout_and_physical_slots.sql"));

            // Verify tables and columns exist
            try (var statement = connection.createStatement()) {
                // Check legacy hold has null physical_slot_id and publication_id
                try (var rows = statement.executeQuery("SELECT booking_id, slot_id, released, physical_slot_id, publication_id FROM slot_holds WHERE booking_id = 9999")) {
                    assertTrue(rows.next());
                    assertEquals(9999L, rows.getLong("booking_id"));
                    assertEquals(100L, rows.getLong("slot_id"));
                    assertFalse(rows.getBoolean("released"));
                    assertNull(rows.getString("physical_slot_id"));
                    assertNull(rows.getString("publication_id"));
                }

                // Check parking_layouts table can be inserted
                statement.executeUpdate("""
                        INSERT INTO parking_layouts (id, parking_lot_id, layout_version, allocation_mode)
                        VALUES (1, 10, 1, 'GROUP')
                        """);

                // Check physical_parking_slots can be inserted
                statement.executeUpdate("""
                        INSERT INTO physical_parking_slots (id, parking_lot_id, slot_group_id, vehicle_type, code, normalized_code, status)
                        VALUES ('ps-1', 10, 100, 'SEDAN_SUV', 'A-01', 'A-01', 'AVAILABLE')
                        """);

                // Check current_slot_allocations can be inserted
                statement.executeUpdate("""
                        INSERT INTO current_slot_allocations (parking_lot_id, physical_slot_id, booking_id, slot_group_id)
                        VALUES (10, 'ps-1', 10001, 100)
                        """);

                try (var rows = statement.executeQuery("SELECT physical_slot_id, booking_id FROM current_slot_allocations WHERE booking_id = 10001")) {
                    assertTrue(rows.next());
                    assertEquals("ps-1", rows.getString("physical_slot_id"));
                    assertEquals(10001L, rows.getLong("booking_id"));
                }
            }
        }
    }
}

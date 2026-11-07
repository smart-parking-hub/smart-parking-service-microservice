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
}

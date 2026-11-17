package com.smartparking.booking;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleMigrationMySqlTest {
    @Test
    @EnabledIfEnvironmentVariable(named="SCHEDULE_BOOKING_UPGRADE_JDBC_URL", matches=".+")
    void v12PreservesEveryExistingTimeAndMoneyValueOnMySql() throws Exception {
        String url=System.getenv("SCHEDULE_BOOKING_UPGRADE_JDBC_URL");
        assertTrue(url.matches("jdbc:mysql://[^/]+/sph_schedule_test_[a-zA-Z0-9_]+(?:\\?.*)?"), "Only isolated test schemas are accepted");
        String user=System.getenv("SCHEDULE_TEST_DB_USER"), password=System.getenv("SCHEDULE_TEST_DB_PASSWORD");
        try(var c=DriverManager.getConnection(url,user,password);var statement=c.createStatement();
            var rows=statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {
            assertTrue(rows.next()); assertEquals(0,rows.getInt(1),"Use a fresh isolated schema; this test never cleans a database");
        }
        var baseline=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").target("11").cleanDisabled(true).load();
        assertTrue(baseline.migrate().success);
        List<List<String>> before;
        try(var c=DriverManager.getConnection(url,user,password);var statement=c.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO bookings (booking_code,driver_id,vehicle_id,parking_lot_id,vehicle_type,status,
                        advance_deposit,total_amount,expected_check_in_time,expected_check_out_time,expires_at,
                        check_in_time,check_out_time)
                    VALUES ('BK-SCHEDULE-UPGRADE-SENTINEL',900001,900001,900001,'CONTAINER','COMPLETED',
                        12345.67,98765.43,'2026-11-17 08:17:13.123456','2026-11-17 11:43:59.654321',
                        '2026-11-17 08:32:13.123456','2026-11-17 08:20:17.456789','2026-11-17 11:44:01.987654')
                    """);
            before=snapshot(c);
            assertFalse(before.isEmpty(),"Preservation must compare actual legacy rows, not two empty snapshots");
        }
        var current=Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").cleanDisabled(true).load();
        assertEquals(1,current.migrate().migrationsExecuted);
        assertEquals("12",current.info().current().getVersion().getVersion());
        try(var c=DriverManager.getConnection(url,user,password);var statement=c.createStatement()) {
            assertEquals(before,snapshot(c));
            try(var rows=statement.executeQuery("SELECT COUNT(*) FROM bookings WHERE schedule_policy_version IS NOT NULL OR schedule_queue_notified_at IS NOT NULL")) {
                assertTrue(rows.next());assertEquals(0,rows.getInt(1));
            }
        }
    }
    private List<List<String>> snapshot(Connection c) throws Exception {
        var result=new ArrayList<List<String>>();
        try(var statement=c.createStatement();var rows=statement.executeQuery("SELECT * FROM bookings ORDER BY id")) {
            var metadata=rows.getMetaData();
            while(rows.next()) {
                var row=new ArrayList<String>();
                for(int i=1;i<=metadata.getColumnCount();i++) {
                    String column=metadata.getColumnName(i);
                    if (!column.equalsIgnoreCase("schedule_policy_version") && !column.equalsIgnoreCase("schedule_queue_notified_at"))
                        row.add(rows.getString(i));
                }
                result.add(row);
            }
        }
        return result;
    }
}

package com.smartparking.booking;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

class UnpaidHoldMigrationTest {
    @Test
    @EnabledIfEnvironmentVariable(named="HOLD_MIGRATION_MYSQL_TEST_URL",matches=".+")
    void upgradePreservesBookingsAndDoesNotBackfillPenalties() throws Exception {
        String url=System.getenv("HOLD_MIGRATION_MYSQL_TEST_URL");
        if (!url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-z0-9_]+_hold_migration_it(?:\\?.*)?"))
            throw new IllegalArgumentException("Requires isolated local *_hold_migration_it database");
        String password=System.getenv("HOLD_POLICY_MYSQL_TEST_PASSWORD");
        try(var c=DriverManager.getConnection(url,"root",password);var s=c.createStatement();var r=s.executeQuery("SHOW TABLES")) {
            assertFalse(r.next(),"Migration test database must be empty");
        }
        var old=Flyway.configure().dataSource(url,"root",password).locations("classpath:db/migration").target("8").cleanDisabled(true).load();
        assertEquals(8,old.migrate().migrationsExecuted);
        byte[] before=snapshot(url,password);
        // This historical regression covers V8 -> V9; later additive migrations have dedicated tests.
        var current=Flyway.configure().dataSource(url,"root",password).locations("classpath:db/migration").target("9").cleanDisabled(true).load();
        assertEquals(1,current.migrate().migrationsExecuted);
        assertArrayEquals(before,snapshot(url,password));
        try(var c=DriverManager.getConnection(url,"root",password);var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM booking_unpaid_hold_releases")) {
            assertTrue(r.next());assertEquals(0,r.getInt(1));
        }
        assertEquals("9",current.info().current().getVersion().getVersion());
        assertEquals(0,current.migrate().migrationsExecuted);
    }
    private byte[] snapshot(String url,String password) throws Exception {
        var hash=MessageDigest.getInstance("SHA-256");
        try(var c=DriverManager.getConnection(url,"root",password);var s=c.createStatement();var r=s.executeQuery("SELECT * FROM bookings ORDER BY id")) {
            int count=0;
            while(r.next()) { count++;for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
                var value=r.getString(i);hash.update((value==null?"<NULL>":value).getBytes(StandardCharsets.UTF_8));hash.update((byte)0);
            } }
            assertTrue(count>0,"Seeded legacy bookings must be present");
        }
        return hash.digest();
    }
}

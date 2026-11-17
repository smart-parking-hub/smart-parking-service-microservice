package com.smartparking.booking;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CHECKOUT_MYSQL_URL", matches = ".+")
class CheckoutSchemaMySqlTest {
    @Test void upgradePreservesBookingsAndAddsNullableFinalDecision() throws Exception {
        String base = System.getenv("CHECKOUT_MYSQL_URL");
        String schema = "checkout_booking_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(base, "root", ""); var sql = c.createStatement()) {
            sql.execute("CREATE DATABASE " + schema);
        }
        Flyway old = Flyway.configure().dataSource(base, "root", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("10").cleanDisabled(true).load();
        old.migrate();
        long count;
        try (var c = DriverManager.getConnection(base, "root", ""); var sql = c.createStatement()) {
            c.setCatalog(schema);
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + schema + ".bookings")) { rows.next(); count = rows.getLong(1); }
        }
        var upgraded = Flyway.configure().dataSource(base, "root", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(true).load().migrate();
        assertEquals(1, upgraded.migrationsExecuted);
        try (var c = DriverManager.getConnection(base, "root", ""); var sql = c.createStatement()) {
            c.setCatalog(schema);
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + schema + ".bookings WHERE final_payment_method IS NULL AND final_payment_reference IS NULL AND final_payment_amount IS NULL")) {
                rows.next(); assertEquals(count, rows.getLong(1));
            }
        }
    }
}

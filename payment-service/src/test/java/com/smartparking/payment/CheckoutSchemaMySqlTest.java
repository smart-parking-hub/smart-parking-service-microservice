package com.smartparking.payment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "CHECKOUT_MYSQL_URL", matches = ".+")
class CheckoutSchemaMySqlTest {
    @Test void upgradePreservesPaymentsAndAddsAnEmptyCashReceiptTable() throws Exception {
        String base = System.getenv("CHECKOUT_MYSQL_URL");
        String schema = "checkout_payment_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(base, "root", ""); var sql = c.createStatement()) { sql.execute("CREATE DATABASE " + schema); }
        Flyway.configure().dataSource(base, "root", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("19").cleanDisabled(true).load().migrate();
        long count;
        try (var c = DriverManager.getConnection(base, "root", ""); var sql = c.createStatement()) {
            c.setCatalog(schema);
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + schema + ".payments")) { rows.next(); count = rows.getLong(1); }
        }
        var upgraded = Flyway.configure().dataSource(base, "root", "").schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").cleanDisabled(true).load().migrate();
        assertEquals(1, upgraded.migrationsExecuted);
        try (var c = DriverManager.getConnection(base, "root", ""); var sql = c.createStatement()) {
            c.setCatalog(schema);
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + schema + ".payments")) { rows.next(); assertEquals(count, rows.getLong(1)); }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + schema + ".cash_checkout_receipts")) { rows.next(); assertEquals(0, rows.getLong(1)); }
        }
    }
}

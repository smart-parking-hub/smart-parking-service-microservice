package com.smartparking.payment;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class PaymentAttemptMigrationTest {
    @Test void additiveMigrationPreservesOldOrdersAndStartsBeyondTheLargest() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:payment_attempt_migration;MODE=MySQL", "sa", "")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE payments (booking_id BIGINT, payment_type VARCHAR(30), order_code BIGINT)");
            statement.execute("INSERT INTO payments VALUES (239,'DEPOSIT',2391),(239,'FINAL_PAYMENT',2392),(51,'DEPOSIT',511)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V18__payment_attempt_recovery.sql"));
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM payments")) { rows.next(); assertEquals(3, rows.getInt(1)); }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM payment_attempt_groups")) { rows.next(); assertEquals(3, rows.getInt(1)); }
            statement.execute("INSERT INTO payment_order_codes () VALUES ()", java.sql.Statement.RETURN_GENERATED_KEYS);
            try (var rows = statement.getGeneratedKeys()) { assertTrue(rows.next()); assertTrue(rows.getLong(1) > 2392); }
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "PAYMENT_RECOVERY_MYSQL_MIGRATION_URL", matches = ".+")
    void mysqlAdditiveUpgradePreservesExistingPayments() throws Exception {
        String url = System.getenv("PAYMENT_RECOVERY_MYSQL_MIGRATION_URL");
        if (!url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-z0-9_]+_payos_migration_it(?:\\?.*)?"))
            throw new IllegalArgumentException("Migration test requires an isolated local *_payos_migration_it database");
        try (var connection = DriverManager.getConnection(url, "root", System.getenv("PAYMENT_RECOVERY_MYSQL_TEST_PASSWORD"))) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE payments (booking_id BIGINT, payment_type VARCHAR(30), order_code BIGINT)");
            statement.execute("INSERT INTO payments VALUES (239,'DEPOSIT',2391),(239,'FINAL_PAYMENT',2392),(51,'DEPOSIT',511)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V18__payment_attempt_recovery.sql"));
            try (var rows = statement.executeQuery("SELECT COUNT(*),MAX(order_code) FROM payments")) {
                rows.next(); assertEquals(3, rows.getInt(1)); assertEquals(2392, rows.getLong(2));
            }
            statement.execute("INSERT INTO payment_order_codes () VALUES ()", java.sql.Statement.RETURN_GENERATED_KEYS);
            try (var rows = statement.getGeneratedKeys()) { assertTrue(rows.next()); assertTrue(rows.getLong(1) > 2392); }
        }
    }
}

package com.smartparking.payment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Run only against an explicitly supplied EMPTY disposable database. Never clean an existing database. */
@EnabledIfEnvironmentVariable(named="DRIVER_WALLET_MYSQL_IT_URL", matches=".+")
class DriverWalletMySqlIT {
    @Test void v19PreservesManualRefundsAndAtomicBalanceUpdatesOnMySql() throws Exception {
        String url = System.getenv("DRIVER_WALLET_MYSQL_IT_URL");
        String user = System.getenv().getOrDefault("DRIVER_WALLET_MYSQL_IT_USER", "root");
        String password = System.getenv().getOrDefault("DRIVER_WALLET_MYSQL_IT_PASSWORD", "");
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            assertTrue(connection.getMetaData().getDatabaseProductName().contains("MySQL"));
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {
                rows.next(); assertEquals(0, rows.getInt(1), "Test database must start empty; this test never cleans it");
            }
        }
        Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").target("18").load().migrate();
        try (var connection = DriverManager.getConnection(url,user,password); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO refund_requests(idempotency_key,booking_id,driver_id,deposit_amount,refund_amount,policy_code,due_at) VALUES('legacy-manual',9001,7,16000,8000,'PARTIAL_50_WITHIN_2H',CURRENT_TIMESTAMP)");
        }
        Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").load().migrate();
        try (var connection = DriverManager.getConnection(url,user,password); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT refund_channel,refund_amount FROM refund_requests WHERE idempotency_key='legacy-manual'")) {
                rows.next(); assertEquals("BANK_MANUAL", rows.getString(1)); assertEquals(8000, rows.getBigDecimal(2).intValueExact());
            }
            sql.executeUpdate("INSERT INTO driver_wallets(driver_id,available_amount) VALUES(7,8000)");
        }
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> debit = () -> {
                start.await();
                try (var connection = DriverManager.getConnection(url,user,password); var sql = connection.createStatement()) {
                    connection.setAutoCommit(false);
                    int result = sql.executeUpdate("UPDATE driver_wallets SET available_amount=available_amount-6000,held_amount=held_amount+6000 WHERE driver_id=7 AND available_amount>=6000");
                    connection.commit(); return result;
                }
            };
            var first = pool.submit(debit); var second = pool.submit(debit); start.countDown();
            assertEquals(1, first.get(15,TimeUnit.SECONDS) + second.get(15,TimeUnit.SECONDS));
        }
        try (var connection = DriverManager.getConnection(url,user,password); var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT available_amount,held_amount FROM driver_wallets WHERE driver_id=7")) {
            rows.next(); assertEquals(2000,rows.getBigDecimal(1).intValueExact()); assertEquals(6000,rows.getBigDecimal(2).intValueExact());
        }
    }
}

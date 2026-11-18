package com.smartparking.identity;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class IdentitySchemaMigrationTest {

    private static final String MYSQL_HOST = resolveProperty("test.mysql.host", "DB_HOST", "localhost");
    private static final int MYSQL_PORT = Integer.parseInt(resolveProperty("test.mysql.port", "DB_PORT", "3306"));
    private static final String MYSQL_USER = resolveProperty("test.mysql.user", "DB_USERNAME", "root");
    private static final String MYSQL_PASSWORD = resolvePassword();

    private static String stripQuotes(String val) {
        if (val == null) return null;
        String trimmed = val.trim();
        if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            if (trimmed.length() >= 2) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }

    private static String readFromEnvFile(String key) {
        for (Path candidate : List.of(
                Paths.get(".env"),
                Paths.get("../.env"),
                Paths.get("../../.env")
        )) {
            if (Files.isRegularFile(candidate)) {
                try {
                    List<String> lines = Files.readAllLines(candidate, StandardCharsets.UTF_8);
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith(key + "=")) {
                            return stripQuotes(trimmed.substring((key + "=").length()));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static String resolveProperty(String sysProp, String envVar, String defaultValue) {
        String val = System.getProperty(sysProp);
        if (val != null && !val.isBlank()) return stripQuotes(val);
        val = System.getenv(envVar);
        if (val != null && !val.isBlank()) return stripQuotes(val);
        val = readFromEnvFile(envVar);
        if (val != null && !val.isBlank()) return val;
        return defaultValue;
    }

    private static String resolvePassword() {
        String val = System.getProperty("test.mysql.password");
        if (val != null) return stripQuotes(val);
        val = System.getenv("DB_PASSWORD");
        if (val != null) return stripQuotes(val);
        val = readFromEnvFile("DB_PASSWORD");
        if (val != null) return val;
        return "";
    }

    private boolean isMySqlAvailable() {
        return checkMySqlConnection(MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD);
    }

    private static boolean checkMySqlConnection(String host, int port, String user, String password) {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true",
                user, password != null ? password : "")) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("H2: Flyway V1..V5 upgrade adds account_holder_name nullable to merchants and preserves existing demo merchants")
    void upgradeFromV1ToV5PreservesExistingMerchantsAndAddsAccountHolderName() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            // Apply migrations V1 through V5 in sequence
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__init_identity_schema.sql"));
            executeMySqlInH2(connection, "db/migration/V2__seed_demo_data.sql");
            executeMySqlInH2(connection, "db/migration/V3__email_otp_verification.sql");
            executeMySqlInH2(connection, "db/migration/V4__add_user_warnings_and_discipline.sql");
            executeMySqlInH2(connection, "db/migration/V5__add_merchant_account_holder_name.sql");

            // 1. Verify that merchants table has the new column account_holder_name
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT account_holder_name FROM merchants LIMIT 1")) {
                assertTrue(rs.next(), "Merchants table should have records from seed data");
                assertNull(rs.getString("account_holder_name"), "Historical seed merchants must have NULL account_holder_name");
            }

            // 2. Verify all historical seed merchants (10 rows in V2) have account_holder_name IS NULL
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*), COUNT(account_holder_name) FROM merchants")) {
                assertTrue(rs.next());
                int totalCount = rs.getInt(1);
                int nonNullAccountHolderCount = rs.getInt(2);
                assertTrue(totalCount >= 10, "Expected at least 10 seed merchants");
                assertEquals(0, nonNullAccountHolderCount, "All existing seed merchants must retain NULL for the new column");
            }

            // 3. Verify updating account_holder_name preserves leading zeros and diacritics
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("UPDATE merchants SET account_holder_name = 'NGUYEN VAN CHU BAI', bank_account_num = '0071001234567' WHERE id = 1");
            }

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT bank_name, bank_account_num, account_holder_name FROM merchants WHERE id = 1")) {
                assertTrue(rs.next());
                assertEquals("Vietcombank", rs.getString("bank_name"));
                assertEquals("0071001234567", rs.getString("bank_account_num"));
                assertEquals("NGUYEN VAN CHU BAI", rs.getString("account_holder_name"));
            }
        }
    }

    @Test
    @DisplayName("MySQL: Flyway V1..V5 fresh database migration runs cleanly and validates merchants column structure")
    void mySqlFreshDatabaseMigrationV1ToV5() throws Exception {
        if (!isMySqlAvailable()) {
            fail("MySQL test environment verification FAILED: cannot connect to MySQL at "
                    + MYSQL_HOST + ":" + MYSQL_PORT + " with user '" + MYSQL_USER
                    + "'. Fresh database migration verification requires an accessible MySQL instance and valid external credentials.");
        }

        String dbName = "test_identity_fresh_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String serverUrl = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/?useSSL=false&allowPublicKeyRetrieval=true";
        String dbUrl = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true";

        try (Connection serverConn = DriverManager.getConnection(serverUrl, MYSQL_USER, MYSQL_PASSWORD);
             Statement serverStmt = serverConn.createStatement()) {
            serverStmt.executeUpdate("CREATE DATABASE " + dbName + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

            try {
                // Run Flyway V1..V5 on fresh database
                Flyway flyway = Flyway.configure()
                        .dataSource(dbUrl, MYSQL_USER, MYSQL_PASSWORD)
                        .locations("classpath:db/migration")
                        .load();
                var result = flyway.migrate();
                assertTrue(result.migrationsExecuted >= 5, "Expected at least 5 migrations executed on fresh database");

                // Verify column properties in information_schema
                try (Connection dbConn = DriverManager.getConnection(dbUrl, MYSQL_USER, MYSQL_PASSWORD);
                     Statement dbStmt = dbConn.createStatement()) {
                    try (ResultSet rs = dbStmt.executeQuery(
                            "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE " +
                            "FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = '" + dbName + "' AND TABLE_NAME = 'merchants' AND COLUMN_NAME = 'account_holder_name'")) {
                        assertTrue(rs.next(), "Column account_holder_name must exist in merchants table");
                        assertEquals("varchar", rs.getString("DATA_TYPE"));
                        assertEquals(100, rs.getInt("CHARACTER_MAXIMUM_LENGTH"));
                        assertEquals("YES", rs.getString("IS_NULLABLE"));
                    }

                    // Verify V5 migration status in flyway_schema_history
                    try (ResultSet rs = dbStmt.executeQuery(
                            "SELECT version, description, success FROM flyway_schema_history WHERE version = '5'")) {
                        assertTrue(rs.next(), "Version 5 must be recorded in flyway_schema_history");
                        assertEquals("add merchant account holder name", rs.getString("description"));
                        assertTrue(rs.getBoolean("success"));
                    }
                }
            } finally {
                serverStmt.executeUpdate("DROP DATABASE IF EXISTS " + dbName);
            }
        }
    }

    @Test
    @DisplayName("MySQL: upgrades V4→V5 and V7→V10 while preserving legacy merchant data")
    void mySqlUpgradeMigrationV4ToV5PreservesExistingData() throws Exception {
        if (!isMySqlAvailable()) {
            fail("MySQL test environment verification FAILED: cannot connect to MySQL at "
                    + MYSQL_HOST + ":" + MYSQL_PORT + " with user '" + MYSQL_USER
                    + "'. Upgrade migration verification requires an accessible MySQL instance and valid external credentials.");
        }

        String dbName = "test_identity_upgrade_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String serverUrl = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/?useSSL=false&allowPublicKeyRetrieval=true";
        String dbUrl = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true";

        try (Connection serverConn = DriverManager.getConnection(serverUrl, MYSQL_USER, MYSQL_PASSWORD);
             Statement serverStmt = serverConn.createStatement()) {
            serverStmt.executeUpdate("CREATE DATABASE " + dbName + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");

            try {
                // 1. Migrate up to V4 only
                Flyway flywayV4 = Flyway.configure()
                        .dataSource(dbUrl, MYSQL_USER, MYSQL_PASSWORD)
                        .locations("classpath:db/migration")
                        .target("4")
                        .load();
                var resultV4 = flywayV4.migrate();
                assertTrue(resultV4.migrationsExecuted >= 4, "Expected migrations V1..V4 executed");

                // Snapshot merchants before V5
                Map<Long, Map<String, String>> snapshotBefore = new LinkedHashMap<>();
                try (Connection dbConn = DriverManager.getConnection(dbUrl, MYSQL_USER, MYSQL_PASSWORD);
                     Statement dbStmt = dbConn.createStatement();
                     ResultSet rs = dbStmt.executeQuery("SELECT id, user_id, bank_name, bank_account_num FROM merchants ORDER BY id")) {
                    while (rs.next()) {
                        Map<String, String> row = new HashMap<>();
                        row.put("user_id", String.valueOf(rs.getLong("user_id")));
                        row.put("bank_name", rs.getString("bank_name"));
                        row.put("bank_account_num", rs.getString("bank_account_num"));
                        snapshotBefore.put(rs.getLong("id"), row);
                    }
                }
                assertFalse(snapshotBefore.isEmpty(), "Expected seed merchants to exist in V4");
                assertEquals(10, snapshotBefore.size(), "Expected 10 seed merchants in V2");

                // 2. Upgrade to V5
                Flyway flywayV5 = Flyway.configure()
                        .dataSource(dbUrl, MYSQL_USER, MYSQL_PASSWORD)
                        .locations("classpath:db/migration")
                        .target("5")
                        .load();
                var resultV5 = flywayV5.migrate();
                assertEquals(1, resultV5.migrationsExecuted, "Expected exactly 1 migration (V5) executed during upgrade");

                // 3. Verify data after V5 upgrade
                try (Connection dbConn = DriverManager.getConnection(dbUrl, MYSQL_USER, MYSQL_PASSWORD);
                     Statement dbStmt = dbConn.createStatement()) {

                    // Verify flyway_schema_history has V5
                    try (ResultSet rs = dbStmt.executeQuery(
                            "SELECT version, success FROM flyway_schema_history WHERE version = '5'")) {
                        assertTrue(rs.next());
                        assertTrue(rs.getBoolean("success"));
                    }

                    // Verify all existing merchants retain their banking data and account_holder_name is NULL
                    try (ResultSet rs = dbStmt.executeQuery(
                            "SELECT id, user_id, bank_name, bank_account_num, account_holder_name FROM merchants ORDER BY id")) {
                        int checkedCount = 0;
                        while (rs.next()) {
                            long id = rs.getLong("id");
                            Map<String, String> before = snapshotBefore.get(id);
                            assertNotNull(before, "Merchant row must match pre-migration snapshot");
                            assertEquals(before.get("user_id"), String.valueOf(rs.getLong("user_id")));
                            assertEquals(before.get("bank_name"), rs.getString("bank_name"));
                            assertEquals(before.get("bank_account_num"), rs.getString("bank_account_num"));
                            assertNull(rs.getString("account_holder_name"), "account_holder_name must remain NULL for existing records");
                            checkedCount++;
                        }
                        assertEquals(10, checkedCount, "All 10 merchants must be verified");
                    }

                    // 4. Update one record to verify round-trip persistence with leading zero and Vietnamese diacritics
                    dbStmt.executeUpdate(
                            "UPDATE merchants SET account_holder_name = 'NGUYỄN VĂN CHỦ BÃI', bank_account_num = '0071001234567' WHERE id = 1");

                    try (ResultSet rs = dbStmt.executeQuery(
                            "SELECT bank_name, bank_account_num, account_holder_name FROM merchants WHERE id = 1")) {
                        assertTrue(rs.next());
                        assertEquals("0071001234567", rs.getString("bank_account_num"));
                        assertEquals("NGUYỄN VĂN CHỦ BÃI", rs.getString("account_holder_name"));
                    }
                }
                // Move to the pre-KYC schema, then migrate it with existing Merchant data.
                Flyway.configure().dataSource(dbUrl, MYSQL_USER, MYSQL_PASSWORD)
                        .target("7").locations("classpath:db/migration").load().migrate();
                var kycFlyway = Flyway.configure().dataSource(dbUrl, MYSQL_USER, MYSQL_PASSWORD)
                        .target("9").locations("classpath:db/migration").load();
                assertEquals(2, kycFlyway.migrate().migrationsExecuted, "KYC upgrades V7 through V8 and V9");
                try (Connection dbConn = DriverManager.getConnection(dbUrl, MYSQL_USER, MYSQL_PASSWORD);
                     Statement dbStmt = dbConn.createStatement()) {
                    dbStmt.executeUpdate("""
                            INSERT INTO merchant_verification_dossiers (id, merchant_id, user_id, status, legal_name)
                            SELECT 100, id, user_id, 'DRAFT', 'EXISTING NAME' FROM merchants WHERE id = 1
                            """);
                    dbStmt.executeUpdate("""
                            INSERT INTO merchant_verification_documents
                            (id, dossier_id, document_type, original_name, content_type, size_bytes, sha256_hash)
                            VALUES ('legacy-image', 100, 'BUSINESS_REGISTRATION', 'legacy.png', 'image/png', 3, 'test')
                            """);
                    dbStmt.executeUpdate("INSERT INTO merchant_verification_document_bytes VALUES ('legacy-image', X'010203')");
                    dbStmt.executeUpdate("""
                            INSERT INTO merchant_verification_submissions
                            (dossier_id, submission_version, business_registration_number, legal_name)
                            VALUES (100, 1, '0101234567', 'EXISTING NAME')
                            """);
                }
                var upgraded = Flyway.configure().dataSource(dbUrl, MYSQL_USER, MYSQL_PASSWORD)
                        .locations("classpath:db/migration").load();
                assertEquals(1, upgraded.migrate().migrationsExecuted, "V10 upgrades V9 with existing KYC data");
                upgraded.validate();
                try (Connection dbConn = DriverManager.getConnection(dbUrl, MYSQL_USER, MYSQL_PASSWORD);
                     Statement dbStmt = dbConn.createStatement()) {
                    try (ResultSet rs = dbStmt.executeQuery("""
                            SELECT d.cloudinary_public_id, HEX(b.file_bytes) AS bytes
                            FROM merchant_verification_documents d JOIN merchant_verification_document_bytes b
                            ON b.document_id = d.id WHERE d.id = 'legacy-image'
                            """)) {
                        assertTrue(rs.next());
                        assertNull(rs.getString("cloudinary_public_id"));
                        assertEquals("010203", rs.getString("bytes"));
                    }
                    try (ResultSet rs = dbStmt.executeQuery("""
                            SELECT legal_name, ocr_document_id, ocr_method
                            FROM merchant_verification_submissions WHERE dossier_id = 100
                            """)) {
                        assertTrue(rs.next());
                        assertEquals("EXISTING NAME", rs.getString("legal_name"));
                        assertNull(rs.getString("ocr_document_id"));
                        assertNull(rs.getString("ocr_method"));
                    }
                }
                try (Connection dbConn = DriverManager.getConnection(dbUrl, MYSQL_USER, MYSQL_PASSWORD);
                     Statement dbStmt = dbConn.createStatement()) {
                    try (ResultSet rs = dbStmt.executeQuery(
                            "SELECT business_license, bank_account_num, account_holder_name FROM merchants WHERE id = 1")) {
                        assertTrue(rs.next());
                        assertEquals("0071001234567", rs.getString("bank_account_num"));
                        assertEquals("NGUYỄN VĂN CHỦ BÃI", rs.getString("account_holder_name"));
                    }
                    try (ResultSet rs = dbStmt.executeQuery("SELECT COUNT(*) FROM merchant_verification_dossiers")) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getInt(1), "Only the explicitly inserted dossier should exist");
                    }
                    try (ResultSet rs = dbStmt.executeQuery(
                            "SHOW COLUMNS FROM merchant_verification_submissions LIKE 'assessment_attempts'")) {
                        assertTrue(rs.next());
                        assertEquals("0", rs.getString("Default"));
                    }
                    try (ResultSet rs = dbStmt.executeQuery(
                            "SHOW COLUMNS FROM merchant_verification_document_bytes LIKE 'file_bytes'")) {
                        assertTrue(rs.next());
                        assertEquals("longblob", rs.getString("Type"));
                    }
                }
            } finally {
                serverStmt.executeUpdate("DROP DATABASE IF EXISTS " + dbName);
            }
        }
    }

    @Test
    @DisplayName("MySQL: Verification fails clearly when database is unreachable instead of silently passing")
    void whenMySqlIsUnreachable_VerificationFailsWithClearError() {
        boolean reachable = checkMySqlConnection("127.0.0.1", 59999, "non_existent_user", "non_existent_password");
        assertFalse(reachable, "Connection to unreachable port must evaluate to false");

        AssertionError error = assertThrows(AssertionError.class, () -> {
            if (!reachable) {
                fail("MySQL test environment verification FAILED: cannot connect to MySQL at 127.0.0.1:59999. Required for migration verification.");
            }
        });
        assertTrue(error.getMessage().contains("MySQL test environment verification FAILED"));
    }

    private static void executeMySqlInH2(Connection connection, String resourcePath) throws Exception {
        String content = new String(new ClassPathResource(resourcePath).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String cleaned = content
                .replaceAll("(?i)COLLATE\\s+[A-Za-z0-9_]+", "")
                .replaceAll("(?i)ENGINE\\s*=\\s*InnoDB", "")
                .replaceAll("(?i)DEFAULT CHARSET\\s*=\\s*[A-Za-z0-9_]+", "")
                .replaceAll("(?i)ALTER\\s+TABLE\\s+users\\s+ADD\\s+COLUMN\\s+warning_count\\s+INT\\s+NOT\\s+NULL\\s+DEFAULT\\s+0,\\s*ADD\\s+COLUMN\\s+suspended_until\\s+DATETIME\\(6\\)\\s+NULL",
                        "ALTER TABLE users ADD COLUMN warning_count INT NOT NULL DEFAULT 0;\nALTER TABLE users ADD COLUMN suspended_until DATETIME(6) NULL");
        ScriptUtils.executeSqlScript(connection, new ByteArrayResource(cleaned.getBytes(StandardCharsets.UTF_8)));
    }
}

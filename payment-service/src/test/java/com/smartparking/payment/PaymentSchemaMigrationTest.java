package com.smartparking.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentSchemaMigrationTest {

    @Test
    @DisplayName("Flyway V1..V5 upgrade preserves V2 8% historical entries, balances all journals, and reconciles wallets.balance")
    void upgradeFromV1ToV5PreservesHistoryAndReconcilesDoubleEntryLedger() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__init_payment_schema.sql"));
            executeMySqlInH2(connection, "db/migration/V2__seed_demo_data.sql");
            executeMySqlInH2(connection, "db/migration/V3__add_bank_info_to_withdrawal_requests.sql");
            executeMySqlInH2(connection, "db/migration/V4__payment_checkout_reconciliation.sql");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V5__double_entry_ledger_and_manual_refunds.sql"));

            // 1. Verify all 160 historical 8% fee entries in wallet_ledger_entries are untouched
            try (var stmt = connection.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*), MIN(fee_percent), MAX(fee_percent) FROM wallet_ledger_entries WHERE entry_type = 'SETTLEMENT_CREDIT'")) {
                assertTrue(rs.next());
                assertEquals(160, rs.getInt(1));
                assertEquals(0, new BigDecimal("8.00").compareTo(rs.getBigDecimal(2)));
                assertEquals(0, new BigDecimal("8.00").compareTo(rs.getBigDecimal(3)));
            }

            // 2. Verify every journal in ledger_journals has total_debit == total_credit and matches postings sum
            try (var stmt = connection.createStatement();
                 var rs = stmt.executeQuery("""
                         SELECT lj.id, lj.journal_key, lj.total_debit, lj.total_credit,
                                SUM(CASE WHEN lp.direction = 'DEBIT' THEN lp.amount ELSE 0 END) AS sum_debit,
                                SUM(CASE WHEN lp.direction = 'CREDIT' THEN lp.amount ELSE 0 END) AS sum_credit
                         FROM ledger_journals lj
                         JOIN ledger_postings lp ON lp.journal_id = lj.id
                         GROUP BY lj.id, lj.journal_key, lj.total_debit, lj.total_credit
                         """)) {
                int journalCount = 0;
                while (rs.next()) {
                    journalCount++;
                    BigDecimal totalDebit = rs.getBigDecimal("total_debit");
                    BigDecimal totalCredit = rs.getBigDecimal("total_credit");
                    BigDecimal sumDebit = rs.getBigDecimal("sum_debit");
                    BigDecimal sumCredit = rs.getBigDecimal("sum_credit");
                    assertEquals(0, totalDebit.compareTo(totalCredit), "Journal header must balance: " + rs.getString("journal_key"));
                    assertEquals(0, totalDebit.compareTo(sumDebit), "Journal debit must match postings: " + rs.getString("journal_key"));
                    assertEquals(0, totalCredit.compareTo(sumCredit), "Journal credit must match postings: " + rs.getString("journal_key"));
                }
                assertEquals(170, journalCount, "Expected 160 historical settlement journals + 10 opening reconciliation journals");
            }

            // 3. Verify every wallet.balance across all 10 merchants equals SUM(credit - debit) on MERCHANT_PAYABLE
            try (var stmt = connection.createStatement();
                 var rs = stmt.executeQuery("""
                         SELECT w.merchant_id, w.balance,
                                COALESCE(SUM(CASE WHEN lp.direction = 'CREDIT' THEN lp.amount ELSE -lp.amount END), 0) AS ledger_payable
                         FROM wallets w
                         LEFT JOIN ledger_postings lp
                           ON lp.merchant_id = w.merchant_id AND lp.account_code = 'MERCHANT_PAYABLE'
                         GROUP BY w.merchant_id, w.balance
                         """)) {
                int walletCount = 0;
                while (rs.next()) {
                    walletCount++;
                    BigDecimal walletBalance = rs.getBigDecimal("balance");
                    BigDecimal ledgerPayable = rs.getBigDecimal("ledger_payable");
                    assertEquals(0, walletBalance.compareTo(ledgerPayable),
                            "Wallet balance must equal ledger MERCHANT_PAYABLE net balance for merchant " + rs.getLong("merchant_id"));
                }
                assertEquals(10, walletCount);
            }

            // 4. Re-executing V5 is idempotent (no duplicate journals or postings)
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V5__double_entry_ledger_and_manual_refunds.sql"));
            try (var stmt = connection.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*) FROM ledger_journals")) {
                assertTrue(rs.next());
                assertEquals(170, rs.getInt(1));
            }
        }
    }

    @Test
    @DisplayName("Flyway V1..V15 applies dispute evidence files, history table, and unique booking constraint while preserving legacy disputes")
    void upgradeFromV1ToV15AppliesDisputeSchemaAndPreservesLegacyDisputes() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__init_payment_schema.sql"));
            executeMySqlInH2(connection, "db/migration/V2__seed_demo_data.sql");
            executeMySqlInH2(connection, "db/migration/V3__add_bank_info_to_withdrawal_requests.sql");
            executeMySqlInH2(connection, "db/migration/V4__payment_checkout_reconciliation.sql");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V5__double_entry_ledger_and_manual_refunds.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V12__add_evidence_urls_to_disputes.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V13__add_dispute_history.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V14__enforce_unique_dispute_booking.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V15__add_dispute_evidence_files.sql"));

            try (var stmt = connection.createStatement();
                 var rs = stmt.executeQuery("SELECT COUNT(*), SUM(CASE WHEN parking_lot_id IS NULL THEN 1 ELSE 0 END) FROM disputes")) {
                assertTrue(rs.next());
                assertEquals(20, rs.getInt(1), "All 20 seeded disputes must be preserved");
                assertEquals(20, rs.getInt(2), "Legacy disputes must retain NULL parking_lot_id");
            }

            try (var stmt = connection.createStatement()) {
                stmt.executeUpdate("""
                        INSERT INTO disputes (booking_id, driver_id, merchant_id, parking_lot_id, reason, description, evidence_urls, status)
                        VALUES (9901, 200, 300, 88, 'Test reason', 'Test desc', '["https://cdn.example.com/e1.jpg"]', 'PENDING')
                        """);
                assertThrows(Exception.class, () -> stmt.executeUpdate("""
                        INSERT INTO disputes (booking_id, driver_id, merchant_id, parking_lot_id, reason, status)
                        VALUES (9901, 200, 300, 88, 'Duplicate booking', 'PENDING')
                        """), "uk_disputes_booking_id must reject duplicate booking_id");

                stmt.executeUpdate("""
                        INSERT INTO dispute_history (dispute_id, event_type, actor_user_id, actor_role, message, resulting_status)
                        VALUES (1, 'MERCHANT_RESPONSE', 300, 'MERCHANT', 'Merchant reply', 'IN_REVIEW')
                        """);
                try (var rs = stmt.executeQuery("SELECT COUNT(*) FROM dispute_history WHERE dispute_id = 1")) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                }

                stmt.executeUpdate("""
                        INSERT INTO dispute_evidence_files (stored_file_name, original_file_name, content_type, size_bytes, uploader_driver_id, dispute_id)
                        VALUES ('11111111-1111-1111-1111-111111111111.png', 'receipt.png', 'image/png', 1024, 200, 1)
                        """);
                assertThrows(Exception.class, () -> stmt.executeUpdate("""
                        INSERT INTO dispute_evidence_files (stored_file_name, original_file_name, content_type, size_bytes, uploader_driver_id, dispute_id)
                        VALUES ('11111111-1111-1111-1111-111111111111.png', 'dup.png', 'image/png', 1024, 200, 1)
                        """), "uk_dispute_evidence_stored_name must reject duplicate stored_file_name");
                assertThrows(Exception.class, () -> stmt.executeUpdate("""
                        INSERT INTO dispute_evidence_files (stored_file_name, original_file_name, content_type, size_bytes, uploader_driver_id, dispute_id)
                        VALUES ('22222222-2222-2222-2222-222222222222.png', 'orphan.png', 'image/png', 1024, 200, 999999)
                        """), "fk_dispute_evidence_dispute must reject unknown dispute_id");
            }
        }
    }

    private void executeMySqlInH2(Connection connection, String resourcePath) throws Exception {
        String sql = new String(new ClassPathResource(resourcePath).getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("DATE_ADD\\('([^']+)',\\s*INTERVAL\\s*\\(([^)]+)\\)\\s*HOUR\\)", "DATEADD('HOUR', ($2), TIMESTAMP '$1')")
                .replaceAll("(?i)ALTER\\s+TABLE\\s+(\\w+)\\s+ADD\\s+COLUMN", "ALTER TABLE $1 ADD COLUMN")
                .replaceAll("(?i),\\s*ADD\\s+COLUMN", "; ALTER TABLE " + extractTableName(resourcePath) + " ADD COLUMN");
        ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
    }

    private String extractTableName(String resourcePath) {
        if (resourcePath.contains("withdrawal_requests")) {
            return "withdrawal_requests";
        }
        return "payments";
    }
}

package com.smartparking.payment;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class LegacyWithdrawalHoldMigrationTest {
    @Test void pendingLiabilityIsPreservedWithoutChangingWalletAndReplayIsSafe() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V1__init_payment_schema.sql"));
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V5__double_entry_ledger_and_manual_refunds.sql"));
            c.createStatement().execute("INSERT INTO wallets (merchant_id,balance) VALUES (4,900000)");
            c.createStatement().execute("INSERT INTO withdrawal_requests (merchant_id,amount,status,requested_at) VALUES (4,100000,'PENDING',CURRENT_TIMESTAMP),(4,200000,'APPROVED',CURRENT_TIMESTAMP)");
            c.createStatement().execute("INSERT INTO ledger_journals(id,journal_key,journal_type,merchant_id,total_debit,total_credit) VALUES(100,'already-held','WITHDRAWAL_HOLD',4,50000,50000)");
            c.createStatement().execute("INSERT INTO ledger_postings(journal_id,account_code,merchant_id,direction,amount) VALUES(100,'GATEWAY_CLEARING',4,'DEBIT',50000),(100,'MERCHANT_WITHDRAWAL_HOLD',4,'CREDIT',50000)");
            for (int i = 0; i < 2; i++) ScriptUtils.executeSqlScript(c,
                    new ClassPathResource("db/migration/V17__preserve_legacy_pending_withdrawal_holds.sql"));
            var r = c.createStatement().executeQuery("SELECT (SELECT balance FROM wallets WHERE merchant_id=4),"
                    + "(SELECT COUNT(*) FROM ledger_journals),(SELECT COUNT(*) FROM ledger_postings),"
                    + "(SELECT SUM(amount) FROM ledger_postings WHERE account_code='MERCHANT_WITHDRAWAL_HOLD' AND direction='CREDIT')");
            assertTrue(r.next());
            assertEquals(0, r.getBigDecimal(1).compareTo(new java.math.BigDecimal("900000")));
            assertEquals(2, r.getInt(2));
            assertEquals(4, r.getInt(3));
            assertEquals(0, r.getBigDecimal(4).compareTo(new java.math.BigDecimal("100000")));
        }
    }
}

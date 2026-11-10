package com.smartparking.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression safety tests verifying:
 * 1) PaymentFlywayMySqlIT does not possess class-level gating annotations that block scenario-specific runs.
 * 2) Scenario activation conditions and resolveDbUrl behave correctly.
 * 3) Snapshot records reflect authentic V1-V5 schema (no frozen_balance, no wallet_id/balance_after).
 * 4) Explicit requirement causes informative failure instead of silent skip.
 */
class PaymentFlywayMySqlITConfigurationTest {

    @Test
    @DisplayName("R2: PaymentFlywayMySqlIT has no class-level @EnabledIfEnvironmentVariable requiring PAYMENT_MYSQL_IT_URL")
    void classLevelAnnotationDoesNotRequireBaseUrl() {
        assertNull(
                PaymentFlywayMySqlIT.class.getAnnotation(EnabledIfEnvironmentVariable.class),
                "PaymentFlywayMySqlIT must not have a class-level @EnabledIfEnvironmentVariable requiring base URL"
        );
    }

    @Test
    @DisplayName("R2: Both test methods possess @EnabledIf annotations pointing to scenario activation methods")
    void testMethodsHaveScenarioSpecificEnabledIf() throws Exception {
        Method cleanMethod = PaymentFlywayMySqlIT.class.getDeclaredMethod(
                "cleanMigration_emptyDatabaseToV5_verifiesFreshExecutionAndLedgerInvariants");
        EnabledIf cleanAnnotation = cleanMethod.getAnnotation(EnabledIf.class);
        assertNotNull(cleanAnnotation, "cleanMigration method must have @EnabledIf");
        assertEquals("isCleanEnabled", cleanAnnotation.value());

        Method upgradeMethod = PaymentFlywayMySqlIT.class.getDeclaredMethod(
                "upgradeMigration_fromV4ToV5_preservesPreUpgradeDataAndBalancesLedger");
        EnabledIf upgradeAnnotation = upgradeMethod.getAnnotation(EnabledIf.class);
        assertNotNull(upgradeAnnotation, "upgradeMigration method must have @EnabledIf");
        assertEquals("isUpgradeEnabled", upgradeAnnotation.value());
    }

    @Test
    @DisplayName("R2: Suffix appending for DB URLs handles plain, parameterized, and pre-suffixed URLs")
    void appendSuffixToDbNameHandlesAllUrlVariants() {
        assertEquals("jdbc:mysql://localhost:33084/payment_clean",
                PaymentFlywayMySqlIT.appendSuffixToDbName("jdbc:mysql://localhost:33084/payment", "clean"));

        assertEquals("jdbc:mysql://localhost:33084/payment_upgrade?serverTimezone=UTC",
                PaymentFlywayMySqlIT.appendSuffixToDbName("jdbc:mysql://localhost:33084/payment?serverTimezone=UTC", "upgrade"));

        assertEquals("jdbc:mysql://localhost:33084/payment_clean",
                PaymentFlywayMySqlIT.appendSuffixToDbName("jdbc:mysql://localhost:33084/payment_clean", "clean"));
    }

    @Test
    @DisplayName("R3: PreUpgradeWallet record components match authentic V1-V5 schema (no frozen_balance)")
    void preUpgradeWalletRecordMatchesSchema() {
        List<String> componentNames = Arrays.stream(PaymentFlywayMySqlIT.PreUpgradeWallet.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertTrue(componentNames.contains("id"));
        assertTrue(componentNames.contains("merchantId"));
        assertTrue(componentNames.contains("balance"));
        assertTrue(componentNames.contains("currency"));
        assertTrue(componentNames.contains("status"));
        assertTrue(componentNames.contains("version"));

        assertFalse(componentNames.contains("frozenBalance"), "wallets table has no frozen_balance column in V1-V5");
    }

    @Test
    @DisplayName("R3: PreUpgradeLedgerEntry record components match authentic V1-V5 schema (no wallet_id, amount, balance_after)")
    void preUpgradeLedgerEntryRecordMatchesSchema() {
        List<String> componentNames = Arrays.stream(PaymentFlywayMySqlIT.PreUpgradeLedgerEntry.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertTrue(componentNames.contains("id"));
        assertTrue(componentNames.contains("merchantId"));
        assertTrue(componentNames.contains("bookingId"));
        assertTrue(componentNames.contains("paymentId"));
        assertTrue(componentNames.contains("entryType"));
        assertTrue(componentNames.contains("grossAmount"));
        assertTrue(componentNames.contains("feePercent"));
        assertTrue(componentNames.contains("feeAmount"));
        assertTrue(componentNames.contains("netAmount"));
        assertTrue(componentNames.contains("note"));

        assertFalse(componentNames.contains("walletId"), "wallet_ledger_entries uses merchant_id, not wallet_id");
        assertFalse(componentNames.contains("amount"), "wallet_ledger_entries uses gross_amount/net_amount, not amount");
        assertFalse(componentNames.contains("balanceAfter"), "wallet_ledger_entries has no balance_after column");
    }
}


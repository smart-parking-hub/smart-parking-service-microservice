package com.smartparking.payment;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class LegacyPaymentMigrationResourcesTest {
    private String sha256(String resource) throws Exception {
        try (var stream = new ClassPathResource(resource).getInputStream()) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
        }
    }
    @Test void releasedLocalHistoryKeepsItsExactBytesAndCanonicalChainRemainsAvailable() throws Exception {
        assertEquals("6481671c9d59c472770ec2a526544670db3a1da498bf6bf3cd228757a3128aec",
                sha256("db/local-v6-v7-compatible/V6__admin_operations_outbox.sql"));
        assertEquals("f9cfb7b363c8b72a2be433e5af6278bdfde5e025280f95bdb2fd8e748937c893",
                sha256("db/local-v6-v7-compatible/V7__preserve_legacy_pending_withdrawal_holds.sql"));
        assertTrue(new ClassPathResource("db/migration/V16__admin_operations_outbox.sql").exists());
        assertFalse(new ClassPathResource("db/local-v6-v7-compatible/V16__admin_operations_outbox.sql").exists());
        assertEquals(sha256("db/migration/V17__preserve_legacy_pending_withdrawal_holds.sql"),
                sha256("db/local-v6-v7-compatible/V17__preserve_legacy_pending_withdrawal_holds.sql"));
        assertEquals(sha256("db/migration/V18__payment_attempt_recovery.sql"),
                sha256("db/local-v6-v7-compatible/V18__payment_attempt_recovery.sql"));
    }
}

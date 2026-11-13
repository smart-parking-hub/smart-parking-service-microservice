package com.smartparking.identity.infrastructure.messaging;

import com.smartparking.common.event.AdminRecipientSnapshot;
import com.smartparking.common.messaging.TransactionalEventOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;

/** Reconciles all current users, including seeded/administratively changed Admins. */
@Component
@RequiredArgsConstructor
public class AdminRecipientSnapshotScheduler {
    private final JdbcTemplate jdbc;
    private final TransactionalEventOutbox outbox;
    @Value("${app.admin-operations.enabled:true}") private boolean enabled = true;
    @Value("${app.admin-operations.max-recipients:1000}") private int maxRecipients = 1000;

    @Scheduled(fixedDelayString = "${app.admin-operations.recipient-scan-ms:10000}")
    @Transactional
    public void capture() {
        if (!enabled) return;
        var cursor = jdbc.queryForMap("SELECT * FROM admin_recipient_snapshot_cursor WHERE id = 1 FOR UPDATE");
        var ids = jdbc.queryForList("SELECT id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' ORDER BY id", Long.class);
        if (ids.size() > maxRecipients) {
            throw new IllegalStateException("Admin recipient capacity exceeded; configure delivery capacity before resuming");
        }
        String fingerprint = fingerprint(ids.toString());
        Instant now = Instant.now();
        Timestamp previous = (Timestamp) cursor.get("captured_at");
        if (fingerprint.equals(cursor.get("fingerprint")) && previous != null
                && previous.toInstant().isAfter(now.minusSeconds(60))) return;
        long version = ((Number) cursor.get("snapshot_version")).longValue() + 1;
        var snapshot = AdminRecipientSnapshot.builder().eventType(AdminRecipientSnapshot.TYPE)
                .aggregateId("admin-recipients").timestamp(now).snapshotVersion(version).recipientIds(ids).build();
        outbox.enqueue(AdminRecipientSnapshot.TOPIC, "admin-recipients", snapshot);
        jdbc.update("UPDATE admin_recipient_snapshot_cursor SET snapshot_version = ?, fingerprint = ?, captured_at = ? WHERE id = 1",
                version, fingerprint, Timestamp.from(now));
    }

    private String fingerprint(String ids) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ids.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}

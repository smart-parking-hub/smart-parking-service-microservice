package com.smartparking.identity.application.service.verification;

import com.smartparking.common.exception.AppException;
import com.smartparking.identity.infrastructure.cloudinary.VerificationCloudinaryClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class VerificationCloudinaryCleanup {
    private final JdbcTemplate jdbc;
    private final VerificationCloudinaryClient cloudinary;

    public VerificationCloudinaryCleanup(JdbcTemplate jdbc, VerificationCloudinaryClient cloudinary) {
        this.jdbc = jdbc;
        this.cloudinary = cloudinary;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trackUpload(String publicId) {
        jdbc.update("""
                INSERT INTO merchant_verification_cloudinary_cleanup (public_id, next_attempt_at)
                VALUES (?, ?)
                """, publicId, LocalDateTime.now().plusMinutes(5));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void retainOnCommit(String publicId) {
        // The uncommitted delete locks this row during the upload. Rollback restores the cleanup job.
        if (remove(publicId) != 1) {
            throw new IllegalStateException("Missing Cloudinary cleanup job");
        }
    }

    @Scheduled(fixedDelayString = "${app.verification.cloudinary.cleanup-poll-ms:30000}")
    @Transactional
    public void cleanNext() {
        if (!cloudinary.isConfigured()) {
            return;
        }
        var due = jdbc.queryForList("""
                SELECT public_id FROM merchant_verification_cloudinary_cleanup
                WHERE next_attempt_at <= ? ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED
                """, String.class, LocalDateTime.now());
        if (due.isEmpty()) {
            return;
        }
        String publicId = due.getFirst();
        try {
            cloudinary.destroy(publicId);
        } catch (AppException error) {
            jdbc.update("""
                    UPDATE merchant_verification_cloudinary_cleanup SET next_attempt_at = ? WHERE public_id = ?
                    """, LocalDateTime.now().plusMinutes(5), publicId);
            return;
        }
        remove(publicId);
    }

    private int remove(String publicId) {
        return jdbc.update("DELETE FROM merchant_verification_cloudinary_cleanup WHERE public_id = ?", publicId);
    }
}

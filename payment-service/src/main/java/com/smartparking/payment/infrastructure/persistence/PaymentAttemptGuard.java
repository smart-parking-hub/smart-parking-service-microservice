package com.smartparking.payment.infrastructure.persistence;

import com.smartparking.common.enums.PaymentTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicLong;

/** Serializes create/sync/webhook for one booking and payment type, across application instances. */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentAttemptGuard {
    private static final long MAX_PAYOS_ORDER_CODE = 9_007_199_254_740_991L; // JavaScript Number.MAX_SAFE_INTEGER (2^53 - 1)
    private final JdbcTemplate jdbc;
    private final AtomicLong lastAssigned = new AtomicLong(-1L);

    public void lock(Long bookingId, PaymentTypeEnum type) {
        lockKey("booking:" + bookingId + ":" + type.name());
    }

    public void lockSettlementDate(java.time.LocalDate businessDate) {
        lockKey("settlement:" + businessDate);
    }

    private void lockKey(String key) {
        // The unique-key upsert takes an exclusive InnoDB row lock until transaction completion.
        jdbc.update("INSERT INTO payment_attempt_groups (group_key) VALUES (?) "
                + "ON DUPLICATE KEY UPDATE group_key = group_key", key);
        jdbc.queryForObject("SELECT group_key FROM payment_attempt_groups WHERE group_key = ? FOR UPDATE",
                String.class, key);
    }

    public long nextOrderCode() {
        initIfNecessary();
        while (true) {
            long base = System.currentTimeMillis() * 1000L;
            long code = lastAssigned.updateAndGet(prev -> Math.max(base, prev + 1));
            if (code <= 0 || code > MAX_PAYOS_ORDER_CODE) {
                throw new IllegalStateException("Payment order code exhausted: " + code);
            }
            try {
                jdbc.update("INSERT INTO payment_order_codes (id) VALUES (?)", code);
                return code;
            } catch (DuplicateKeyException ex) {
                // Another instance or concurrent insert registered this id; advance and retry
                lastAssigned.updateAndGet(prev -> Math.max(prev, code + 1));
            }
        }
    }

    private void initIfNecessary() {
        if (lastAssigned.get() == -1L) {
            synchronized (lastAssigned) {
                if (lastAssigned.get() == -1L) {
                    long maxInDb = 0L;
                    try {
                        Long maxVal = jdbc.queryForObject(
                                "SELECT COALESCE(MAX(id), 0) FROM payment_order_codes", Long.class);
                        if (maxVal != null && maxVal > 0) {
                            maxInDb = maxVal;
                        }
                    } catch (Exception ignored) {
                        // Table may be uninitialized or running in mock context
                    }
                    long base = System.currentTimeMillis() * 1000L;
                    lastAssigned.set(Math.max(base - 1, maxInDb));
                }
            }
        }
    }
}

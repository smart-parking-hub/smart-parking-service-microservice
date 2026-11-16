package com.smartparking.payment.infrastructure.persistence;

import com.smartparking.common.enums.PaymentTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.Objects;

/** Serializes create/sync/webhook for one booking and payment type, across application instances. */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentAttemptGuard {
    private final JdbcTemplate jdbc;

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
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> connection.prepareStatement(
                "INSERT INTO payment_order_codes () VALUES ()", Statement.RETURN_GENERATED_KEYS), holder);
        long code = Objects.requireNonNull(holder.getKey(), "Missing payment order code").longValue();
        if (code <= 0 || code > 9_007_199_254_740_991L) throw new IllegalStateException("Payment order code exhausted");
        return code;
    }
}

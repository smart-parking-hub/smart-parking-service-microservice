package com.smartparking.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.event.AdminOperationEvent;
import com.smartparking.common.event.AdminRecipientSnapshot;
import com.smartparking.common.event.DomainEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

/** Uses only the importing service's datasource and its current business transaction. */
public class TransactionalEventOutbox {
    private static final Set<String> TOPICS = Set.of(AdminOperationEvent.TOPIC, AdminRecipientSnapshot.TOPIC,
            com.smartparking.common.event.WithdrawalDecisionEvent.TOPIC);
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public TransactionalEventOutbox(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String topic, String key, DomainEvent event) {
        if (!TOPICS.contains(topic) || key == null || key.isBlank() || event == null
                || event.getEventId() == null || event.getEventId().isBlank()) {
            throw new IllegalArgumentException("Invalid admin event envelope");
        }
        try {
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("""
                    INSERT INTO admin_event_outbox
                    (event_id, topic_name, partition_key, payload, status, attempts, created_at, next_attempt_at)
                    VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?)
                    """, event.getEventId(), topic, key, mapper.writeValueAsString(event), now, now);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize admin event", e);
        }
    }
}

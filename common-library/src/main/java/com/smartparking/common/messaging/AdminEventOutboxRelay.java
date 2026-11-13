package com.smartparking.common.messaging;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/** A separate String producer avoids changing existing payment/refund serializers. */
public class AdminEventOutboxRelay implements DisposableBean {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminEventOutboxRelay.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final DefaultKafkaProducerFactory<String, String> factory;
    private final KafkaTemplate<String, String> template;
    private final boolean enabled;

    public AdminEventOutboxRelay(JdbcTemplate jdbc, TransactionTemplate transaction,
                                KafkaProperties properties, boolean enabled) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.enabled = enabled;
        var config = new HashMap<>(properties.buildProducerProperties(null));
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        factory = new DefaultKafkaProducerFactory<>(config, new StringSerializer(), new StringSerializer());
        template = new KafkaTemplate<>(factory);
    }

    // Package-private seam for deterministic broker-failure tests.
    AdminEventOutboxRelay(JdbcTemplate jdbc, TransactionTemplate transaction, KafkaTemplate<String, String> template) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.template = template;
        this.factory = null;
        this.enabled = true;
    }

    @Scheduled(fixedDelayString = "${app.admin-operations.relay-delay-ms:5000}")
    public void relay() {
        if (!enabled) return;
        transaction.executeWithoutResult(status -> {
            var rows = jdbc.query("""
                    SELECT event_id, topic_name, partition_key, payload, attempts
                    FROM admin_event_outbox
                    WHERE status = 'PENDING' AND next_attempt_at <= ?
                    ORDER BY created_at, event_id LIMIT 20 FOR UPDATE
                    """, (result, index) -> new PendingEvent(result.getString("event_id"), result.getString("topic_name"),
                    result.getString("partition_key"), result.getString("payload"), result.getInt("attempts")),
                    Timestamp.from(Instant.now()));
            for (var row : rows) {
                String id = row.id();
                try {
                    template.send(row.topic(), row.key(), row.payload()).get(5, TimeUnit.SECONDS);
                    jdbc.update("UPDATE admin_event_outbox SET status = 'PUBLISHED', published_at = ? WHERE event_id = ?",
                            Timestamp.from(Instant.now()), id);
                } catch (Exception failure) {
                    if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                    int attempts = row.attempts() + 1;
                    jdbc.update("""
                            UPDATE admin_event_outbox SET status = ?, attempts = ?, next_attempt_at = ? WHERE event_id = ?
                            """, attempts >= 5 ? "FAILED" : "PENDING", attempts,
                            Timestamp.from(Instant.now().plusSeconds(5L << Math.min(attempts - 1, 5))), id);
                    log.warn("Admin outbox event {} delivery attempt {} failed; state={}",
                            id, attempts, attempts >= 5 ? "FAILED" : "PENDING");
                    // Do not hold the rest of the batch for another network timeout.
                    break;
                }
            }
        });
    }

    private record PendingEvent(String id, String topic, String key, String payload, int attempts) {}

    @Override public void destroy() { if (factory != null) factory.destroy(); }
}

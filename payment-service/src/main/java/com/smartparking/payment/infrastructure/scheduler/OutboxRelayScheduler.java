package com.smartparking.payment.infrastructure.scheduler;

import com.smartparking.payment.domain.model.OutboxEvent;
import com.smartparking.payment.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayScheduler {

    private static final String TOPIC_PAYMENT_EVENTS = "payment.events";
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Polls PENDING payment outbox events and dispatches them to Kafka.
     * Guarantees at-least-once delivery for payment events.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processPendingOutboxEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                "PENDING", PageRequest.of(0, 50));

        if (pending.isEmpty() || kafkaTemplate == null) {
            return;
        }

        log.debug("Found {} pending payment outbox events to relay to Kafka", pending.size());

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(TOPIC_PAYMENT_EVENTS, event.getAggregateId(), event.getPayload())
                        .whenComplete((res, ex) -> {
                            if (ex == null) {
                                event.setStatus("PUBLISHED");
                                event.setProcessedAt(LocalDateTime.now());
                                outboxEventRepository.save(event);
                                log.info("Relayed payment outbox event {} ({}) to Kafka", event.getId(), event.getEventType());
                            } else {
                                int retries = (event.getRetryCount() != null ? event.getRetryCount() : 0) + 1;
                                event.setRetryCount(retries);
                                if (retries >= MAX_RETRIES) {
                                    event.setStatus("FAILED");
                                    log.error("Payment outbox event {} exceeded max retries. Marked as FAILED: {}",
                                            event.getId(), ex.getMessage());
                                }
                                outboxEventRepository.save(event);
                            }
                        });
            } catch (Exception e) {
                log.warn("Failed to dispatch payment outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
    }
}

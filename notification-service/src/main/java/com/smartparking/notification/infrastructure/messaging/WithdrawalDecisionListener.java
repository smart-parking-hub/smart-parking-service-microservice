package com.smartparking.notification.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.event.WithdrawalDecisionEvent;
import com.smartparking.notification.application.service.WithdrawalDecisionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WithdrawalDecisionListener {
    private final ObjectMapper mapper;
    private final WithdrawalDecisionHandler handler;

    @KafkaListener(topics = WithdrawalDecisionEvent.TOPIC, groupId = "notification-withdrawal-decisions",
            containerFactory = "adminOperationsKafkaFactory", autoStartup = "${app.admin-operations.enabled:true}")
    public void receive(String payload) {
        try {
            var root = mapper.readTree(payload);
            if (root == null || !root.isObject() || !root.path("eventId").isTextual()
                    || root.path("eventId").asText().isBlank() || !root.hasNonNull("timestamp")
                    || !root.path("schemaVersion").isIntegralNumber() || root.path("schemaVersion").asInt() != 1) {
                throw new IllegalArgumentException("Invalid withdrawal decision envelope");
            }
            handler.deliver(mapper.readValue(payload, WithdrawalDecisionEvent.class));
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("Invalid withdrawal decision JSON", invalid);
        }
    }
}

package com.smartparking.notification.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.event.AdminOperationEvent;
import com.smartparking.common.event.AdminRecipientSnapshot;
import com.smartparking.notification.application.service.AdminOperationsHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminOperationsListener {
    private final ObjectMapper mapper;
    private final AdminOperationsHandler handler;

    @KafkaListener(topics = AdminOperationEvent.TOPIC, groupId = "notification-admin-operations",
            containerFactory = "adminOperationsKafkaFactory", autoStartup = "${app.admin-operations.enabled:true}")
    public void operations(String payload) {
        try {
            validateEnvelope(payload);
            handler.deliver(mapper.readValue(payload, AdminOperationEvent.class));
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("Invalid admin operation JSON", invalid);
        }
    }

    @KafkaListener(topics = AdminRecipientSnapshot.TOPIC, groupId = "notification-admin-recipients",
            containerFactory = "adminOperationsKafkaFactory", autoStartup = "${app.admin-operations.enabled:true}")
    public void recipients(String payload) {
        try {
            validateEnvelope(payload);
            handler.applySnapshot(mapper.readValue(payload, AdminRecipientSnapshot.class));
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("Invalid recipient snapshot JSON", invalid);
        }
    }

    private void validateEnvelope(String payload) throws JsonProcessingException {
        var root = mapper.readTree(payload);
        // DomainEvent has producer-side UUID/timestamp defaults. Reject missing
        // transport fields BEFORE deserializing so malformed/replayed messages
        // cannot silently receive a new eventId or a fresh timestamp.
        if (root == null || !root.isObject() || !root.path("eventId").isTextual()
                || root.path("eventId").asText().isBlank() || root.path("eventId").asText().length() > 100
                || !root.path("eventType").isTextual() || root.path("eventType").asText().isBlank()
                || !root.hasNonNull("timestamp") || !root.path("schemaVersion").isIntegralNumber()
                || !root.path("schemaVersion").canConvertToInt() || root.path("schemaVersion").asInt() != 1) {
            throw new IllegalArgumentException("Missing or unsupported admin event envelope fields");
        }
    }
}

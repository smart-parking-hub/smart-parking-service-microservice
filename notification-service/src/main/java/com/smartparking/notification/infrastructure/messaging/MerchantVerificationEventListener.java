package com.smartparking.notification.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.event.MerchantVerificationEvent;
import com.smartparking.notification.application.service.MerchantVerificationNotificationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MerchantVerificationEventListener {

    private final ObjectMapper mapper;
    private final MerchantVerificationNotificationHandler handler;

    @KafkaListener(topics = MerchantVerificationEvent.TOPIC, groupId = "notification-merchant-verification",
            containerFactory = "adminOperationsKafkaFactory", autoStartup = "${app.admin-operations.enabled:true}")
    public void receive(String payload) {
        try {
            var root = mapper.readTree(payload);
            if (root == null || !root.isObject() || !root.path("eventId").isTextual()
                    || root.path("eventId").asText().isBlank() || !root.hasNonNull("timestamp")
                    || !root.path("schemaVersion").isIntegralNumber() || root.path("schemaVersion").asInt() != 1) {
                throw new IllegalArgumentException("Invalid merchant verification event envelope");
            }
            handler.deliver(mapper.readValue(payload, MerchantVerificationEvent.class));
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("Invalid merchant verification JSON", invalid);
        }
    }
}

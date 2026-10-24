package com.smartparking.payment.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.PaymentGatewayEnum;
import com.smartparking.payment.domain.model.OutboxEvent;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxPublisherServiceImpl outboxPublisherService;

    @Test
    @DisplayName("publishPaymentCompleted saves outbox and publishes to Kafka successfully")
    void testPublishPaymentCompletedSuccess() throws Exception {
        Payment payment = Payment.builder()
                .id(100L)
                .bookingId(1L)
                .driverId(10L)
                .amount(new BigDecimal("50000"))
                .gateway(PaymentGatewayEnum.PAYOS)
                .transactionCode("TX-123")
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"paymentId\":100}");
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("payment.events"), eq("100"), any())).thenReturn(future);

        outboxPublisherService.publishPaymentCompleted(payment);

        verify(outboxEventRepository, atLeastOnce()).save(any(OutboxEvent.class));
        verify(kafkaTemplate, times(1)).send(eq("payment.events"), eq("100"), any());
    }

    @Test
    @DisplayName("publishPaymentFailed handles Kafka failure callback safely")
    void testPublishPaymentFailedKafkaError() throws Exception {
        Payment payment = Payment.builder()
                .id(101L)
                .bookingId(2L)
                .driverId(11L)
                .amount(new BigDecimal("60000"))
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"paymentId\":101}");
        CompletableFuture<SendResult<String, Object>> failedFuture = CompletableFuture.failedFuture(new RuntimeException("Kafka unreachable"));
        when(kafkaTemplate.send(eq("payment.events"), eq("101"), any())).thenReturn(failedFuture);

        outboxPublisherService.publishPaymentFailed(payment, "Driver cancelled payment");

        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
        verify(kafkaTemplate, times(1)).send(eq("payment.events"), eq("101"), any());
    }

    @Test
    @DisplayName("saveAndPublish catches JSON serialization exception gracefully")
    void testSaveAndPublishSerializationException() throws Exception {
        Payment payment = Payment.builder()
                .id(102L)
                .bookingId(3L)
                .driverId(12L)
                .amount(new BigDecimal("70000"))
                .gateway(PaymentGatewayEnum.VNPAY)
                .build();

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization failed") {});

        outboxPublisherService.publishPaymentCompleted(payment);

        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}

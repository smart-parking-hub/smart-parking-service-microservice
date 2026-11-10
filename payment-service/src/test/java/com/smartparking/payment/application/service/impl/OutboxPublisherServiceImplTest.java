package com.smartparking.payment.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.PaymentGatewayEnum;
import com.smartparking.payment.domain.model.OutboxEvent;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.model.RefundRequest;
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
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
        Payment payment = Payment.builder().paymentType(com.smartparking.common.enums.PaymentTypeEnum.DEPOSIT)
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
        Payment payment = Payment.builder().paymentType(com.smartparking.common.enums.PaymentTypeEnum.DEPOSIT)
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
    @DisplayName("saveAndPublish throws IllegalStateException on JSON serialization exception for atomic rollback")
    void testSaveAndPublishSerializationException() throws Exception {
        Payment payment = Payment.builder().paymentType(com.smartparking.common.enums.PaymentTypeEnum.DEPOSIT)
                .id(102L)
                .bookingId(3L)
                .driverId(12L)
                .amount(new BigDecimal("70000"))
                .gateway(PaymentGatewayEnum.VNPAY)
                .build();

        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization failed") {});

        assertThrows(IllegalStateException.class, () -> outboxPublisherService.publishPaymentCompleted(payment));

        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("publishRefundStatusChanged saves outbox and sends PAYMENT_REFUND_UPDATED event")
    void testPublishRefundStatusChanged() throws Exception {
        RefundRequest refundRequest = RefundRequest.builder()
                .id(55L)
                .bookingId(10L)
                .paymentId(100L)
                .driverId(20L)
                .merchantId(30L)
                .refundAmount(new BigDecimal("25000"))
                .refundPercentage(50)
                .status("COMPLETED")
                .transactionReference("FT26110999")
                .verifiedAt(LocalDateTime.now())
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"refundRequestId\":55}");
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(eq("payment.events"), eq("10"), any())).thenReturn(future);

        outboxPublisherService.publishRefundStatusChanged(refundRequest);

        verify(outboxEventRepository, atLeastOnce()).save(any(OutboxEvent.class));
        verify(kafkaTemplate, times(1)).send(eq("payment.events"), eq("10"), any());
    }
}

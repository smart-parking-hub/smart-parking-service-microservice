package com.smartparking.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.notification.application.service.NotificationService;
import com.smartparking.notification.domain.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BusinessEventFailureTest {
    @Test void bookingAndPaymentStorageErrorsMustReachKafkaForRetry() {
        var service = mock(NotificationService.class);
        var processed = mock(ProcessedEventRepository.class);
        when(service.createNotification(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("storage unavailable"));
        var mapper = new ObjectMapper();
        assertThatThrownBy(() -> new BookingEventListener(service, processed, mapper).handleBookingEvent(
                "{\"eventId\":\"booking-failure\",\"eventType\":\"BOOKING_CREATED\",\"bookingId\":1,\"userId\":10}"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PaymentEventListener(service, processed, mapper).handlePaymentEvent(
                "{\"eventId\":\"payment-failure\",\"eventType\":\"PAYMENT_COMPLETED\",\"bookingId\":1,\"userId\":10}"))
                .isInstanceOf(IllegalStateException.class);
        verify(processed, never()).save(any());
    }
}

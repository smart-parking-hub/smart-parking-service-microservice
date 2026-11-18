package com.smartparking.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.event.BookingEvent;
import com.smartparking.common.event.PaymentEvent;
import com.smartparking.notification.domain.repository.NotificationRepository;
import com.smartparking.notification.domain.repository.ProcessedEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.datasource.url=jdbc:h2:mem:business_notifications;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
@EmbeddedKafka(partitions = 1, topics = {"booking.events", "payment.events", "user.events"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
class BusinessNotificationsKafkaIntegrationTest {
    @Autowired KafkaTemplate<String, Object> kafka;
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired ObjectMapper mapper;
    @Autowired NotificationRepository notifications;
    @Autowired ProcessedEventRepository processed;

    @Test void acceptsTypedProducerHeadersAndRawOutboxJsonAndPersistsOnlyOnce() throws Exception {
        // Production JsonSerializer adds a Java type header. The listener accepts
        // JSON text, so this must work with the header present as well as absent.
        var booking = BookingEvent.BookingCancelledEvent.builder()
                .eventType("BOOKING_CANCELLED").bookingId(9001L).userId(101L).merchantId(202L).build();
        kafka.send("booking.events", "9001", booking).get();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(notifications.count()).isEqualTo(2));

        try (var raw = new KafkaProducer<>(java.util.Map.<String, Object>of("bootstrap.servers", broker.getBrokersAsString()),
                new StringSerializer(), new StringSerializer())) {
            raw.send(new ProducerRecord<>("booking.events", "9001", mapper.writeValueAsString(booking))).get();
        }
        var payment = PaymentEvent.PaymentCompletedEvent.builder()
                .eventType("PAYMENT_COMPLETED").bookingId(9001L).userId(101L).build();
        kafka.send("payment.events", "9001", payment).get();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(notifications.count()).isEqualTo(3);
            assertThat(processed.existsByEventId(booking.getEventId())).isTrue();
            assertThat(processed.existsByEventId(payment.getEventId())).isTrue();
        });
        assertThat(notifications.findAll()).extracting(item -> item.getUserId()).containsExactlyInAnyOrder(101L, 202L, 101L);
    }
}

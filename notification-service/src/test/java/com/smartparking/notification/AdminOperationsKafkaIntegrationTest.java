package com.smartparking.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.event.AdminOperationEvent;
import com.smartparking.common.event.AdminRecipientSnapshot;
import com.smartparking.notification.domain.repository.NotificationRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "app.admin-operations.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:admin_kafka_test;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
@EmbeddedKafka(partitions = 3, topics = {
        AdminOperationEvent.TOPIC, AdminOperationEvent.TOPIC + ".DLT",
        AdminRecipientSnapshot.TOPIC, AdminRecipientSnapshot.TOPIC + ".DLT"
}, bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Sql("/admin-recipient-test-schema.sql")
@DirtiesContext
class AdminOperationsKafkaIntegrationTest {
    @Autowired EmbeddedKafkaBroker broker;
    @Autowired ObjectMapper mapper;
    @Autowired NotificationRepository notifications;

    @Test void kafkaSnapshotThenOperationDeliversOnceAndMalformedJsonGoesToDlt() throws Exception {
        var config = new HashMap<String, Object>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        try (var producer = new KafkaProducer<>(config, new StringSerializer(), new StringSerializer())) {
            var snapshot = AdminRecipientSnapshot.builder().eventType(AdminRecipientSnapshot.TYPE)
                    .snapshotVersion(1).recipientIds(List.of(101L, 202L)).build();
            producer.send(new ProducerRecord<>(AdminRecipientSnapshot.TOPIC, "admin-recipients", mapper.writeValueAsString(snapshot)))
                    .get(10, TimeUnit.SECONDS);
            // The consumer may receive this before the snapshot; its retry policy
            // must recover once the snapshot is committed.
            var event = AdminOperationEvent.builder().eventType(AdminOperationEvent.DISPUTE_OPENED)
                    .targetId(55L).actorId(303L).build();
            String payload = mapper.writeValueAsString(event);
            producer.send(new ProducerRecord<>(AdminOperationEvent.TOPIC, "dispute:55", payload)).get(10, TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(notifications.count()).isEqualTo(2));
            producer.send(new ProducerRecord<>(AdminOperationEvent.TOPIC, "dispute:55", payload)).get(10, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>(AdminOperationEvent.TOPIC, "invalid", "{invalid-json"))
                    .get(10, TimeUnit.SECONDS);
            var consumerConfig = KafkaTestUtils.consumerProps("admin-dlt-verification", "false", broker);
            try (var consumer = new DefaultKafkaConsumerFactory<>(consumerConfig,
                    new StringDeserializer(), new StringDeserializer()).createConsumer()) {
                consumer.subscribe(List.of(AdminOperationEvent.TOPIC + ".DLT"));
                var dlt = KafkaTestUtils.getSingleRecord(consumer, AdminOperationEvent.TOPIC + ".DLT", Duration.ofSeconds(20));
                assertThat(dlt.value()).isEqualTo("{invalid-json");
            }
            assertThat(notifications.count()).isEqualTo(2);
        }
    }
}

package com.smartparking.notification.infrastructure.messaging;

import com.smartparking.common.event.AdminOperationEvent;
import com.smartparking.common.event.AdminRecipientSnapshot;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import java.util.HashMap;

@Configuration(proxyBeanMethods = false)
public class AdminOperationsKafkaConfiguration {
    @Bean public AdminDeadLetterTransport adminDeadLetterTransport(KafkaProperties properties) {
        return new AdminDeadLetterTransport(properties);
    }

    @Bean public ConcurrentKafkaListenerContainerFactory<String, String> adminOperationsKafkaFactory(
            KafkaProperties properties, AdminDeadLetterTransport transport) {
        var config = new HashMap<>(properties.buildConsumerProperties(null));
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer()));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        var recoverer = new DeadLetterPublishingRecoverer(transport.template,
                (record, error) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(java.time.Duration.ofSeconds(12));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        factory.setCommonErrorHandler(handler);
        return factory;
    }

    @Bean public KafkaAdmin.NewTopics adminNotificationTopics(
            @Value("${app.admin-operations.partitions:3}") int partitions,
            @Value("${app.admin-operations.replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(com.smartparking.common.event.WithdrawalDecisionEvent.TOPIC).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(com.smartparking.common.event.WithdrawalDecisionEvent.TOPIC + ".DLT").partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(AdminOperationEvent.TOPIC).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(AdminOperationEvent.TOPIC + ".DLT").partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(AdminRecipientSnapshot.TOPIC).partitions(1).replicas(replicas).build(),
                TopicBuilder.name(AdminRecipientSnapshot.TOPIC + ".DLT").partitions(1).replicas(replicas).build());
    }

    // Not exposed as a KafkaTemplate bean: preserve Boot's existing Object template.
    static class AdminDeadLetterTransport implements DisposableBean {
        final DefaultKafkaProducerFactory<String, String> factory;
        final KafkaTemplate<String, String> template;
        AdminDeadLetterTransport(KafkaProperties properties) {
            var config = new HashMap<>(properties.buildProducerProperties(null));
            config.put(ProducerConfig.ACKS_CONFIG, "all");
            config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
            config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
            config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
            factory = new DefaultKafkaProducerFactory<>(config, new StringSerializer(), new StringSerializer());
            template = new KafkaTemplate<>(factory);
        }
        @Override public void destroy() { factory.destroy(); }
    }
}

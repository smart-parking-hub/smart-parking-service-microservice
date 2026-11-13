package com.smartparking.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.event.AdminOperationEvent;
import com.smartparking.common.event.AdminRecipientSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit opt-in only; never component-scanned by the other common-library consumers. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class OperationsOutboxConfiguration {
    @Bean public TransactionalEventOutbox adminEventOutbox(JdbcTemplate jdbc, ObjectMapper mapper) {
        return new TransactionalEventOutbox(jdbc, mapper);
    }

    @Bean public AdminEventOutboxRelay adminEventOutboxRelay(JdbcTemplate jdbc, PlatformTransactionManager manager,
            KafkaProperties properties, @Value("${app.admin-operations.enabled:true}") boolean enabled) {
        return new AdminEventOutboxRelay(jdbc, new TransactionTemplate(manager), properties, enabled);
    }

    @Bean public KafkaAdmin.NewTopics adminEventTopics(
            @Value("${app.admin-operations.partitions:3}") int partitions,
            @Value("${app.admin-operations.replicas:1}") int replicas) {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name(com.smartparking.common.event.WithdrawalDecisionEvent.TOPIC).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(AdminOperationEvent.TOPIC).partitions(partitions).replicas(replicas).build(),
                TopicBuilder.name(AdminRecipientSnapshot.TOPIC).partitions(1).replicas(replicas).build());
    }
}

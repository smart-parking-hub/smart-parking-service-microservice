package com.smartparking.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.event.AdminOperationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(TransactionalEventOutboxTest.Config.class)
class TransactionalEventOutboxTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean(destroyMethod = "shutdown") org.springframework.jdbc.datasource.embedded.EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean TransactionalEventOutbox outbox(JdbcTemplate jdbc) {
            return new TransactionalEventOutbox(jdbc, new ObjectMapper().findAndRegisterModules());
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionalEventOutbox outbox;
    @Autowired PlatformTransactionManager manager;

    @BeforeEach void schema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS business_record (id INT PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS admin_event_outbox (
                 event_id VARCHAR(100) PRIMARY KEY, topic_name VARCHAR(100), partition_key VARCHAR(100), payload CLOB,
                 status VARCHAR(20), attempts INT, created_at TIMESTAMP, next_attempt_at TIMESTAMP, published_at TIMESTAMP)
                """);
        jdbc.update("DELETE FROM business_record");
        jdbc.update("DELETE FROM admin_event_outbox");
    }

    private AdminOperationEvent event() {
        return AdminOperationEvent.builder().eventType(AdminOperationEvent.DISPUTE_OPENED)
                .targetId(1L).actorId(2L).build();
    }

    @Test void businessDataAndEventCommitOrRollBackTogether() {
        var transaction = new TransactionTemplate(manager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO business_record VALUES (1)");
            outbox.enqueue(AdminOperationEvent.TOPIC, "dispute:1", event());
            throw new IllegalStateException("Business rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_record", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_event_outbox", Integer.class)).isZero();
        transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO business_record VALUES (1)");
            outbox.enqueue(AdminOperationEvent.TOPIC, "dispute:1", event());
        });
        assertThat(jdbc.queryForObject("SELECT status FROM admin_event_outbox", String.class)).isEqualTo("PENDING");
    }

    @Test void enqueueRequiresTheBusinessTransactionAndValidDestination() {
        assertThatThrownBy(() -> outbox.enqueue(AdminOperationEvent.TOPIC, "1", event()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status ->
                outbox.enqueue("payment.events", "1", event()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void brokerFailureRetainsEventWithBackoffAndReplayUsesTheOriginalEventId() {
        var transaction = new TransactionTemplate(manager);
        var input = event();
        transaction.executeWithoutResult(status -> outbox.enqueue(AdminOperationEvent.TOPIC, "dispute:1", input));
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Broker unavailable")));
        var relay = new AdminEventOutboxRelay(jdbc, transaction, kafka);
        relay.relay();
        assertThat(jdbc.queryForObject("SELECT status FROM admin_event_outbox", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempts FROM admin_event_outbox", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT event_id FROM admin_event_outbox", String.class)).isEqualTo(input.getEventId());
        relay.relay();
        verify(kafka, times(1)).send(eq(AdminOperationEvent.TOPIC), eq("dispute:1"), contains(input.getEventId()));
        jdbc.update("UPDATE admin_event_outbox SET attempts = 4, next_attempt_at = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(5)));
        relay.relay();
        assertThat(jdbc.queryForObject("SELECT status FROM admin_event_outbox", String.class)).isEqualTo("FAILED");
        // Simulate the documented targeted replay after the underlying outage is fixed.
        jdbc.update("UPDATE admin_event_outbox SET status = 'PENDING', attempts = 0, next_attempt_at = ? WHERE event_id = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(5)), input.getEventId());
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        relay.relay();
        assertThat(jdbc.queryForObject("SELECT status FROM admin_event_outbox", String.class)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT event_id FROM admin_event_outbox", String.class)).isEqualTo(input.getEventId());
    }
}

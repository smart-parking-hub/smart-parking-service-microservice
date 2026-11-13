package com.smartparking.identity.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.event.AdminRecipientSnapshot;
import com.smartparking.common.messaging.TransactionalEventOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(AdminRecipientSnapshotSchedulerTest.Config.class)
@TestPropertySource(properties = {"app.admin-operations.enabled=true", "app.admin-operations.max-recipients=2"})
class AdminRecipientSnapshotSchedulerTest {
    @Configuration @EnableTransactionManagement
    @Import(AdminRecipientSnapshotScheduler.class)
    static class Config {
        @Bean(destroyMethod = "shutdown") org.springframework.jdbc.datasource.embedded.EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean TransactionalEventOutbox outbox(JdbcTemplate jdbc, ObjectMapper mapper) { return new TransactionalEventOutbox(jdbc, mapper); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AdminRecipientSnapshotScheduler scheduler;

    @BeforeEach void schema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS users (id BIGINT PRIMARY KEY, role VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS admin_recipient_snapshot_cursor (id INT PRIMARY KEY, snapshot_version BIGINT, fingerprint VARCHAR(64), captured_at TIMESTAMP)");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS admin_event_outbox (
                 event_id VARCHAR(100) PRIMARY KEY, topic_name VARCHAR(100), partition_key VARCHAR(100), payload CLOB,
                 status VARCHAR(20), attempts INT, created_at TIMESTAMP, next_attempt_at TIMESTAMP, published_at TIMESTAMP)
                """);
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM admin_recipient_snapshot_cursor");
        jdbc.update("DELETE FROM admin_event_outbox");
        jdbc.update("INSERT INTO admin_recipient_snapshot_cursor VALUES (1, 0, '', NULL)");
    }

    @Test void bootstrapSelectsOnlyActiveAdminsAndRoleOrStatusChangesPublishANewerSnapshot() throws Exception {
        jdbc.update("INSERT INTO users VALUES (101, 'ADMIN', 'ACTIVE'), (202, 'ADMIN', 'BANNED'), (303, 'DRIVER', 'ACTIVE')");
        scheduler.capture();
        var first = snapshot();
        assertThat(first.getRecipientIds()).containsExactly(101L);
        assertThat(first.getSnapshotVersion()).isEqualTo(1);
        scheduler.capture();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_event_outbox", Integer.class)).isEqualTo(1);
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = 101");
        jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE id = 202");
        scheduler.capture();
        var second = snapshot();
        assertThat(second.getRecipientIds()).containsExactly(202L);
        assertThat(second.getSnapshotVersion()).isEqualTo(2);
    }

    @Test void heartbeatResynchronizesWithoutHardcodedRecipientsAndCanPublishAnEmptySnapshot() throws Exception {
        scheduler.capture();
        assertThat(snapshot().getRecipientIds()).isEmpty();
        jdbc.update("UPDATE admin_recipient_snapshot_cursor SET captured_at = TIMESTAMP '2020-01-01 00:00:00'");
        scheduler.capture();
        assertThat(snapshot().getSnapshotVersion()).isEqualTo(2);
    }

    @Test void capacityOverflowFailsWithoutPublishingATruncatedRecipientList() {
        jdbc.update("INSERT INTO users VALUES (101, 'ADMIN', 'ACTIVE'), (202, 'ADMIN', 'ACTIVE'), (303, 'ADMIN', 'ACTIVE')");
        assertThatThrownBy(() -> scheduler.capture()).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_event_outbox", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT snapshot_version FROM admin_recipient_snapshot_cursor", Long.class)).isZero();
    }

    private AdminRecipientSnapshot snapshot() throws Exception {
        String payload = jdbc.queryForObject("SELECT payload FROM admin_event_outbox ORDER BY created_at DESC LIMIT 1", String.class);
        return mapper.readValue(payload, AdminRecipientSnapshot.class);
    }
}

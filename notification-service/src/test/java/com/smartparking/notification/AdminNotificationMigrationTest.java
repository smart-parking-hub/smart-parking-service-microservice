package com.smartparking.notification;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AdminNotificationMigrationTest {
    @Test void additiveMigrationPreservesExistingNotificationsAndEnforcesPerRecipientDedup() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__init_notification_schema.sql"))
                    .populate(connection);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO notifications (user_id, title, content) VALUES (101, 'Historical notification', 'Existing content')");
            }
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V3__admin_operations_notifications.sql"))
                    .populate(connection);
            try (var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT title, source_event_id FROM notifications WHERE user_id = 101")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getString("title")).isEqualTo("Historical notification");
                    assertThat(rows.getString("source_event_id")).isNull();
                }
                String insert = "INSERT INTO notifications (user_id, title, content, source_event_id) VALUES (%d, 'New event', 'Content', 'event-1')";
                statement.executeUpdate(insert.formatted(101));
                statement.executeUpdate(insert.formatted(202));
                assertThatThrownBy(() -> statement.executeUpdate(insert.formatted(101))).isInstanceOf(SQLException.class);
            }
        }
    }
}

package com.smartparking.notification;

import com.smartparking.notification.domain.model.Notification;
import com.smartparking.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class NotificationReadRepositoryTest {
    @Autowired NotificationRepository repository;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test void readingAgainPreservesTheOriginalReadTimeAndCannotTouchAnotherUser() {
        LocalDateTime original = LocalDateTime.of(2026, 1, 1, 10, 0);
        Notification item = repository.saveAndFlush(Notification.builder().userId(101L)
                .title("Đã xem").content("Nội dung").readAt(original).build());
        assertThat(repository.markAsRead(202L, item.getId())).isZero();
        assertThat(repository.markAsRead(101L, item.getId())).isZero();
        entityManager.clear();
        assertThat(repository.findById(item.getId()).orElseThrow().getReadAt()).isEqualTo(original);
    }
}

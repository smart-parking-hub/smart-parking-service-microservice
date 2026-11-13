package com.smartparking.notification;

import com.smartparking.common.event.AdminOperationEvent;
import com.smartparking.common.event.AdminRecipientSnapshot;
import com.smartparking.notification.application.service.AdminOperationsHandler;
import com.smartparking.notification.domain.model.Notification;
import com.smartparking.notification.domain.repository.NotificationRepository;
import com.smartparking.notification.domain.repository.ProcessedEventRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "jwt.secret=smartparkingsecretkeymustbeverylongandsecure2026version")
@AutoConfigureMockMvc
@Sql("/admin-recipient-test-schema.sql")
class AdminNotificationsIntegrationTest {
    @Autowired MockMvc http;
    @Autowired AdminOperationsHandler handler;
    @SpyBean NotificationRepository notifications;
    @Autowired ProcessedEventRepository processed;
    @Autowired JdbcTemplate jdbc;
    private static final String API = "/api/v1/notifications";
    private static final String SECRET = "smartparkingsecretkeymustbeverylongandsecure2026version";

    @BeforeEach void clean() {
        reset(notifications);
        notifications.deleteAll();
        processed.deleteAll();
    }

    private String jwt(long id, String role) {
        return Jwts.builder().subject(String.valueOf(id)).claim("role", role)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private AdminOperationEvent event(String type) {
        return AdminOperationEvent.builder().eventType(type).targetId(55L).actorId(300L)
                .amount(new BigDecimal("40000")).currency("VND").submissionVersion(1L).build();
    }

    private void snapshot(long version, Long... ids) {
        handler.applySnapshot(AdminRecipientSnapshot.builder().eventType(AdminRecipientSnapshot.TYPE)
                .snapshotVersion(version).recipientIds(List.of(ids)).build());
    }

    @Test void signedJwtIsRequiredAndUntrustedHeadersCannotSelectAUser() throws Exception {
        http.perform(get(API)).andExpect(status().isUnauthorized());
        http.perform(get(API).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        http.perform(get(API).header("X-User-Id", "101").header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
        for (String role : List.of("ADMIN", "DRIVER", "MERCHANT")) {
            http.perform(get(API + "/unread-count").header("Authorization", "Bearer " + jwt(101, role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(0));
        }
    }

    @Test void lifecycleIsCompatibleWithFrontendAndReadStateIsPerAdmin() throws Exception {
        snapshot(1, 101L, 202L);
        var input = event(AdminOperationEvent.WITHDRAWAL_REQUESTED);
        handler.deliver(input);
        handler.deliver(input);
        assertThat(notifications.count()).isEqualTo(2);
        http.perform(get(API).param("page", "0").param("size", "20")
                .header("Authorization", "Bearer " + jwt(101, "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.currentPage").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Yêu cầu rút tiền mới #55"));
        var a = notifications.findAll().stream().filter(n -> n.getUserId().equals(101L)).findFirst().orElseThrow();
        var b = notifications.findAll().stream().filter(n -> n.getUserId().equals(202L)).findFirst().orElseThrow();
        http.perform(put(API + "/" + b.getId() + "/read").header("Authorization", "Bearer " + jwt(101, "ADMIN")))
                .andExpect(status().isOk());
        assertThat(notifications.findById(b.getId()).orElseThrow().getReadAt()).isNull();
        http.perform(put(API + "/" + a.getId() + "/read").header("Authorization", "Bearer " + jwt(101, "ADMIN")))
                .andExpect(status().isOk());
        var original = notifications.findById(a.getId()).orElseThrow().getReadAt();
        http.perform(post(API + "/" + a.getId() + "/read").header("Authorization", "Bearer " + jwt(101, "ADMIN")))
                .andExpect(status().isOk());
        assertThat(notifications.findById(a.getId()).orElseThrow().getReadAt()).isEqualTo(original);
        http.perform(get(API + "/unread-count").header("Authorization", "Bearer " + jwt(101, "ADMIN")))
                .andExpect(jsonPath("$.data").value(0));
        http.perform(get(API + "/unread-count").header("Authorization", "Bearer " + jwt(202, "ADMIN")))
                .andExpect(jsonPath("$.data").value(1));
        http.perform(put(API + "/read-all").header("Authorization", "Bearer " + jwt(202, "ADMIN")))
                .andExpect(status().isOk());
        http.perform(post(API + "/read-all").header("Authorization", "Bearer " + jwt(202, "ADMIN")))
                .andExpect(status().isOk());
        http.perform(put(API + "/999999/read").header("Authorization", "Bearer " + jwt(101, "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test void pagingIsStableAndMarkAllIncludesHistoryBeyond100Rows() throws Exception {
        LocalDateTime sameTime = LocalDateTime.of(2026, 11, 12, 10, 0);
        for (int index = 0; index < 101; index++) {
            notifications.save(Notification.builder().userId(101L).title("Tin " + index)
                    .content("Nội dung").createdAt(sameTime).build());
        }
        http.perform(get(API).param("page", "0").param("size", "20")
                .header("Authorization", "Bearer " + jwt(101, "ADMIN")))
                .andExpect(jsonPath("$.data.totalElements").value(101))
                .andExpect(jsonPath("$.data.content[0].title").value("Tin 100"));
        http.perform(get(API).param("page", "5").param("size", "20")
                .header("Authorization", "Bearer " + jwt(101, "ADMIN")))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Tin 0"));
        http.perform(put(API + "/read-all").header("Authorization", "Bearer " + jwt(101, "ADMIN")))
                .andExpect(status().isOk());
        assertThat(notifications.countByUserIdAndReadAtIsNull(101L)).isZero();
    }

    @Test void newerSnapshotRemovesRecipientsAndOldSnapshotsCannotRestoreThem() {
        snapshot(2, 101L);
        snapshot(1, 101L, 202L);
        handler.deliver(event(AdminOperationEvent.DISPUTE_OPENED));
        assertThat(notifications.findAll()).extracting(Notification::getUserId).containsExactly(101L);
        snapshot(3, 202L);
        handler.deliver(event(AdminOperationEvent.LOT_REVIEW_REQUESTED));
        assertThat(notifications.findAll()).extracting(Notification::getUserId).containsExactlyInAnyOrder(101L, 202L);
    }

    @Test void missingEmptyOrStaleRecipientsDoNotConsumeAnEvent() {
        var input = event(AdminOperationEvent.DISPUTE_OPENED);
        assertThatThrownBy(() -> handler.deliver(input)).isInstanceOf(IllegalStateException.class);
        snapshot(1);
        assertThatThrownBy(() -> handler.deliver(input)).isInstanceOf(IllegalStateException.class);
        snapshot(2, 101L);
        jdbc.update("UPDATE admin_recipient_state SET source_created_at = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(3600)));
        assertThatThrownBy(() -> handler.deliver(input)).isInstanceOf(IllegalStateException.class);
        assertThat(processed.existsByEventId(input.getEventId())).isFalse();
        assertThat(notifications.count()).isZero();
        snapshot(3, 101L);
        handler.deliver(input);
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test void partialDeliveryFailureRollsBackNotificationsAndProcessedMarkerThenReplaySucceeds() {
        snapshot(1, 101L, 202L);
        var input = event(AdminOperationEvent.DISPUTE_OPENED);
        doAnswer(invocation -> {
            Iterable<Notification> batch = invocation.getArgument(0);
            notifications.saveAndFlush(batch.iterator().next());
            throw new IllegalStateException("Simulated failure after first recipient");
        }).when(notifications).saveAllAndFlush(any());
        assertThatThrownBy(() -> handler.deliver(input))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("Simulated failure after first recipient");
        assertThat(notifications.count()).isZero();
        assertThat(processed.existsByEventId(input.getEventId())).isFalse();
        reset(notifications);
        handler.deliver(input);
        assertThat(notifications.count()).isEqualTo(2);
    }

    @Test void concurrentDuplicateDeliveriesProduceOneNotificationPerRecipient() throws Exception {
        snapshot(1, 101L, 202L);
        var input = event(AdminOperationEvent.DISPUTE_OPENED);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> handler.deliver(input));
            var second = executor.submit(() -> handler.deliver(input));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(notifications.count()).isEqualTo(2);
    }

    @Test void malformedEnvelopesAreRejectedWithoutDatabaseSideEffects() {
        snapshot(1, 101L);
        var input = event(AdminOperationEvent.WITHDRAWAL_REQUESTED);
        input.setAmount(BigDecimal.ZERO);
        assertThatThrownBy(() -> handler.deliver(input)).isInstanceOf(IllegalArgumentException.class);
        input.setAmount(BigDecimal.ONE);
        input.setEventId(null);
        assertThatThrownBy(() -> handler.deliver(input)).isInstanceOf(IllegalArgumentException.class);
        assertThat(notifications.count()).isZero();
        assertThat(processed.count()).isZero();
    }
}

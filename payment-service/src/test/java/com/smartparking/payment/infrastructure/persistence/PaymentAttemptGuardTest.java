package com.smartparking.payment.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PaymentAttemptGuardTest {

    private static final long MAX_PAYOS_SAFE_INTEGER = 9_007_199_254_740_991L;

    @Test
    @DisplayName("nextOrderCode sinh mã đơn tăng dần đơn điệu và hợp lệ với PayOS")
    void nextOrderCodeGeneratesMonotonicallyIncreasingCodesWithinPayOsLimit() {
        // Arrange
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbc.update(anyString(), anyLong())).thenReturn(1);
        PaymentAttemptGuard guard = new PaymentAttemptGuard(jdbc);

        // Act
        long code1 = guard.nextOrderCode();
        long code2 = guard.nextOrderCode();
        long code3 = guard.nextOrderCode();

        // Assert
        assertTrue(code1 > 0, "Mã đơn hàng phải là số nguyên dương");
        assertTrue(code2 > code1, "Mã đơn hàng sau phải lớn hơn mã đơn trước (Monotonicity)");
        assertTrue(code3 > code2, "Mã đơn hàng sau phải lớn hơn mã đơn trước (Monotonicity)");
        assertTrue(code3 <= MAX_PAYOS_SAFE_INTEGER, "Mã đơn hàng không được vượt quá giới hạn an toàn PayOS");
    }

    @Test
    @DisplayName("nextOrderCode an toàn đa luồng và không bị trùng lặp mã khi nhiều luồng gọi đồng thời")
    void nextOrderCodeIsThreadSafeAndCollisionFreeUnderConcurrency() throws InterruptedException {
        // Arrange
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbc.update(anyString(), anyLong())).thenReturn(1);
        PaymentAttemptGuard guard = new PaymentAttemptGuard(jdbc);

        int threadCount = 30;
        int callsPerThread = 20;
        int totalExpectedCodes = threadCount * callsPerThread;
        Set<Long> generatedCodes = Collections.newSetFromMap(new ConcurrentHashMap<>());
        var executor = Executors.newFixedThreadPool(threadCount);
        var startLatch = new CountDownLatch(1);
        var doneLatch = new CountDownLatch(threadCount);

        // Act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < callsPerThread; j++) {
                        long code = guard.nextOrderCode();
                        generatedCodes.add(code);
                    }
                } catch (Exception e) {
                    fail("Lỗi phát sinh trong luồng thực thi: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert
        assertTrue(completed, "Các luồng phải hoàn thành trong thời gian quy định");
        assertEquals(totalExpectedCodes, generatedCodes.size(),
                "Tất cả " + totalExpectedCodes + " mã sinh ra phải là duy nhất, không được trùng lặp");
    }

    @Test
    @DisplayName("nextOrderCode tự động thử lại và tăng số kế tiếp khi gặp DuplicateKeyException do đụng độ cluster node")
    void nextOrderCodeHandlesDuplicateKeyCollisionGracefully() {
        // Arrange
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        when(jdbc.update(anyString(), anyLong())).thenAnswer(invocation -> {
            if (firstAttempt.compareAndSet(true, false)) {
                throw new DuplicateKeyException("Simulated duplicate key collision across cluster nodes");
            }
            return 1;
        });
        PaymentAttemptGuard guard = new PaymentAttemptGuard(jdbc);

        // Act
        long code = guard.nextOrderCode();

        // Assert
        assertTrue(code > 0, "Mã đơn hàng sau khi retry phải là số nguyên dương hợp lệ");
        verify(jdbc, times(2)).update(anyString(), anyLong());
    }
}

package com.smartparking.ai.infrastructure.gemini;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GeminiApiClientTimeoutTest {

    @Test
    @DisplayName("R6 verification: Stalled primary vision call exhausts deadline budget and skips fallback")
    void primaryCallTimeoutExhaustsBudgetAndSkipsFallback() {
        AtomicInteger callCount = new AtomicInteger(0);
        List<Integer> passedReadTimeouts = new ArrayList<>();

        GeminiApiClient client = new GeminiApiClient() {
            @Override
            protected RestTemplate createVisionRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
                passedReadTimeouts.add(readTimeoutMs);
                RestTemplate mockTemplate = mock(RestTemplate.class);
                when(mockTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                        .thenAnswer(inv -> {
                            callCount.incrementAndGet();
                            // Simulate primary call taking 1600ms then timing out
                            Thread.sleep(1600);
                            throw new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out"));
                        });
                return mockTemplate;
            }
        };

        ReflectionTestUtils.setField(client, "apiKey", "test-ai-key-12345");
        ReflectionTestUtils.setField(client, "model", "gemini-1.5-flash");
        // Set total deadline budget to 2000ms
        ReflectionTestUtils.setField(client, "visionReadTimeoutMs", 2000);

        long start = System.currentTimeMillis();
        Optional<String> result = client.generateMultimodalContent(
                "System instruction",
                "Extract layout",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(result.isEmpty(), "Extraction must fail gracefully on timeout");
        // Fallback model must be skipped because remaining budget (< 400ms) <= 1000ms threshold
        assertEquals(1, callCount.get(), "Fallback model must not be called when total budget is exhausted");
        assertTrue(elapsed >= 1500 && elapsed < 2500,
                "Total elapsed time (" + elapsed + "ms) must strictly respect the total budget and not double-timeout");
        assertEquals(1, passedReadTimeouts.size());
        assertTrue(passedReadTimeouts.get(0) <= 2000, "Primary call read timeout must be capped by total budget");
    }

    @Test
    @DisplayName("R6 verification: Fast primary failure passes remaining budget to fallback")
    void fastPrimaryFailurePassesRemainingBudgetToFallback() {
        AtomicInteger callCount = new AtomicInteger(0);
        List<Integer> passedReadTimeouts = new ArrayList<>();

        GeminiApiClient client = new GeminiApiClient() {
            @Override
            protected RestTemplate createVisionRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
                passedReadTimeouts.add(readTimeoutMs);
                RestTemplate mockTemplate = mock(RestTemplate.class);
                when(mockTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                        .thenAnswer(inv -> {
                            int count = callCount.incrementAndGet();
                            if (count == 1) {
                                // Fast 503 / 500 failure on primary model
                                Thread.sleep(200);
                                throw new ResourceAccessException("Service Unavailable 503");
                            }
                            // Fallback succeeds
                            return ResponseEntity.ok("""
                                    {
                                      "candidates": [
                                        {
                                          "content": {
                                            "parts": [
                                              { "text": "{\\"grid\\":{\\"rows\\":1,\\"cols\\":1},\\"cells\\":[]}" }
                                            ]
                                          }
                                        }
                                      ]
                                    }
                                    """);
                        });
                return mockTemplate;
            }
        };

        ReflectionTestUtils.setField(client, "apiKey", "test-ai-key-12345");
        ReflectionTestUtils.setField(client, "model", "gemini-1.5-flash");
        ReflectionTestUtils.setField(client, "visionReadTimeoutMs", 5000);

        Optional<String> result = client.generateMultimodalContent(
                "System instruction",
                "Extract layout",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        assertTrue(result.isPresent());
        assertEquals(2, callCount.get(), "Both primary and fallback should be attempted when budget allows");
        assertEquals(2, passedReadTimeouts.size());
        // Fallback read timeout must have remaining budget (< 5000ms)
        assertTrue(passedReadTimeouts.get(1) < passedReadTimeouts.get(0),
                "Fallback read timeout (" + passedReadTimeouts.get(1) + "ms) must be less than primary ("
                        + passedReadTimeouts.get(0) + "ms)");
    }
}


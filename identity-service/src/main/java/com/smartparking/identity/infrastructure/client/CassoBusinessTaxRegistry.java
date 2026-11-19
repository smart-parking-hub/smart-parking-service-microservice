package com.smartparking.identity.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.smartparking.identity.application.service.verification.BusinessTaxRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class CassoBusinessTaxRegistry implements BusinessTaxRegistry {
    private static final URI BASE_URI = URI.create("https://openapi.casso.vn");
    private static final String BUSINESS_NOT_FOUND = "IDKIT_BUSINESS_NOT_FOUND";
    private static final Duration DEFAULT_QUOTA_WAIT = Duration.ofSeconds(30);

    private final ObjectMapper json;
    private final HttpClient http;
    private final URI baseUri;
    private final boolean enabled;
    private final String appId;
    private final String appSecret;
    private final Duration timeout;
    private final ReentrantLock tokenLock = new ReentrantLock();
    private final AtomicReference<Instant> quotaUntil = new AtomicReference<>(Instant.EPOCH);
    private final AtomicReference<Token> token = new AtomicReference<>();

    @Autowired
    public CassoBusinessTaxRegistry(ObjectMapper json,
                                    @Value("${app.verification.casso.enabled:false}") boolean enabled,
                                    @Value("${app.verification.casso.app-id:}") String appId,
                                    @Value("${app.verification.casso.app-secret:}") String appSecret,
                                    @Value("${app.verification.casso.timeout:5s}") Duration timeout) {
        this(json, enabled, appId, appSecret, timeout, BASE_URI);
    }

    CassoBusinessTaxRegistry(ObjectMapper json, boolean enabled, String appId, String appSecret, Duration timeout,
                             URI baseUri) {
        this.json = json;
        this.enabled = enabled;
        this.appId = appId;
        this.appSecret = appSecret;
        this.timeout = timeout;
        this.baseUri = baseUri;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Result lookup(String taxCode) {
        if (!enabled) {
            return Result.unavailable(null);
        }
        if (appId.isBlank() || appSecret.isBlank()) {
            log.warn("Casso IDKit credentials are missing");
            return Result.unavailable(null);
        }
        Instant retryAt = activeQuotaWait();
        if (retryAt != null) {
            return Result.unavailable(retryAt);
        }

        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            Token current = accessToken(deadline, null);
            if (current == null) {
                return Result.unavailable(activeQuotaWait());
            }
            var response = lookupBusiness(taxCode, current, deadline);
            if (response.statusCode() == 401) {
                current = accessToken(deadline, current);
                if (current == null) {
                    return Result.unavailable(activeQuotaWait());
                }
                response = lookupBusiness(taxCode, current, deadline);
                if (response.statusCode() == 401) {
                    token.compareAndSet(current, null);
                }
            }
            return toResult(taxCode, response);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            log.warn("Casso IDKit lookup interrupted");
        } catch (IOException | TimeoutException | ExecutionException error) {
            // Status and exception class are safe to log. HTTP exceptions can contain the lookup URI or response.
            log.warn("Casso IDKit lookup failed ({})", error.getClass().getSimpleName());
        }
        return Result.unavailable(null);
    }

    private Token accessToken(long deadline, Token rejected) throws IOException, InterruptedException,
            ExecutionException, TimeoutException {
        Token cached = token.get();
        if (usable(cached, rejected)) {
            return cached;
        }
        if (!tokenLock.tryLock(remainingNanos(deadline), TimeUnit.NANOSECONDS)) {
            throw new TimeoutException("Timed out waiting for Casso token");
        }
        try {
            cached = token.get();
            if (usable(cached, rejected)) {
                return cached;
            }
            if (activeQuotaWait() != null) {
                return null;
            }
            token.set(null);
            String credentials = json.writeValueAsString(Map.of("app_id", appId, "app_secret", appSecret));
            var response = send(request("/oauth/token")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(credentials)), deadline);
            if (response.statusCode() != 200) {
                recordFailure("token", response);
                return null;
            }
            JsonNode payload = readPayload(response);
            String value = payload.path("access_token").textValue();
            long expiresIn = payload.path("expires_in").asLong(0);
            if (value == null || !value.matches("[A-Za-z0-9._~+/=-]+") || expiresIn <= 0) {
                log.warn("Casso IDKit token response was incomplete");
                return null;
            }
            long reusableSeconds = Math.min(expiresIn, 900) - Math.min(expiresIn / 10, 30);
            Token refreshed = new Token(value, Instant.now().plusSeconds(reusableSeconds));
            token.set(refreshed);
            return refreshed;
        } finally {
            tokenLock.unlock();
        }
    }

    private boolean usable(Token cached, Token rejected) {
        return cached != null && cached != rejected && Instant.now().isBefore(cached.refreshAt());
    }

    private HttpResponse<String> lookupBusiness(String taxCode, Token current, long deadline)
            throws InterruptedException, TimeoutException, ExecutionException {
        return send(request("/idkit/v1/business/" + taxCode)
                .header("Authorization", "Bearer " + current.value()).GET(), deadline);
    }

    private Result toResult(String taxCode, HttpResponse<String> response) throws IOException {
        if (response.statusCode() == 200) {
            JsonNode taxpayer = readPayload(response).path("business");
            String registeredName = taxpayer.path("name").textValue();
            if (taxCode.equals(taxpayer.path("taxCode").textValue()) && registeredName != null && !registeredName.isBlank()) {
                return Result.found(new Business(taxCode, registeredName, taxpayer.path("status").textValue(),
                        taxpayer.path("source").textValue(), appId.startsWith("trial-app_")));
            }
            log.warn("Casso IDKit lookup returned an incomplete or mismatched tax record");
            return Result.unavailable(null);
        }
        if (response.statusCode() == 400
                && BUSINESS_NOT_FOUND.equals(readPayload(response).path("errorCode").textValue())) {
            return Result.notFound();
        }
        recordFailure("tax lookup", response);
        return Result.unavailable(activeQuotaWait());
    }

    private JsonNode readPayload(HttpResponse<String> response) throws IOException {
        JsonNode body = json.readTree(response.body());
        return body == null ? MissingNode.getInstance() : body;
    }

    private void recordFailure(String operation, HttpResponse<String> response) {
        log.warn("Casso IDKit {} returned HTTP {}", operation, response.statusCode());
        if (response.statusCode() == 429) {
            Instant now = Instant.now();
            Instant retryAt = response.headers().firstValue("Retry-After")
                    .map(value -> parseRetryAfter(value, now))
                    .filter(now::isBefore)
                    .orElseGet(() -> now.plus(DEFAULT_QUOTA_WAIT));
            quotaUntil.accumulateAndGet(retryAt, (existing, next) -> existing.isAfter(next) ? existing : next);
        }
    }

    private Instant activeQuotaWait() {
        Instant until = quotaUntil.get();
        return Instant.now().isBefore(until) ? until : null;
    }

    private Instant parseRetryAfter(String value, Instant now) {
        try {
            return now.plusSeconds(Long.parseLong(value));
        } catch (NumberFormatException error) {
            try {
                return DateTimeFormatter.RFC_1123_DATE_TIME.parse(value, Instant::from);
            } catch (DateTimeException invalidDate) {
                return now;
            }
        } catch (DateTimeException | ArithmeticException error) {
            return now;
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(baseUri.resolve(path)).header("Accept", "application/json");
    }

    private HttpResponse<String> send(HttpRequest.Builder request, long deadline) throws InterruptedException,
            TimeoutException, ExecutionException {
        var pending = http.sendAsync(request.timeout(Duration.ofNanos(remainingNanos(deadline))).build(),
                HttpResponse.BodyHandlers.ofString());
        try {
            return pending.get(remainingNanos(deadline), TimeUnit.NANOSECONDS);
        } catch (InterruptedException | TimeoutException error) {
            pending.cancel(true);
            throw error;
        }
    }

    private long remainingNanos(long deadline) throws TimeoutException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new TimeoutException("Casso lookup exceeded its deadline");
        }
        return remaining;
    }

    private record Token(String value, Instant refreshAt) {}
}

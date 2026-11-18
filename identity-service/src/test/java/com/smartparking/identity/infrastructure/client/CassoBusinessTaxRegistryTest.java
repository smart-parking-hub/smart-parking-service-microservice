package com.smartparking.identity.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.identity.application.service.verification.BusinessTaxRegistry.Outcome;
import com.smartparking.identity.application.service.verification.BusinessTaxRegistry.TaxpayerType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CassoBusinessTaxRegistryTest {
    private static final String TAX_CODE = "0316794479-001";
    private static final String LOOKUP_PATH = "/idkit/v1/business/";
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final AtomicReference<String> credentials = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth/token", exchange -> {
            credentials.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int call = tokenCalls.incrementAndGet();
            reply(exchange, 200, "{\"access_token\":\"test-token-" + call + "\",\"expires_in\":900}");
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void getsBusinessWithCachedTokenAndRefreshesOnceAfterUnauthorized() throws IOException {
        var businessCalls = new AtomicInteger();
        var firstAuthorization = new AtomicReference<String>();
        var lastAuthorization = new AtomicReference<String>();
        server.createContext(LOOKUP_PATH + TAX_CODE, exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            int call = businessCalls.incrementAndGet();
            firstAuthorization.compareAndSet(null, authorization);
            lastAuthorization.set(authorization);
            if (call == 2) {
                reply(exchange, 401, "{}");
                return;
            }
            reply(exchange, 200, """
                    {"requestId":"req-1","business":{"taxCode":"0316794479-001","name":"CASSO BRANCH",
                    "status":"NNT đang hoạt động","source":"https://www.gdt.gov.vn","address":"Example"}}
                    """);
        });
        var registry = registry(Duration.ofSeconds(5));

        var first = registry.lookup(TAX_CODE, TaxpayerType.BUSINESS);
        var second = registry.lookup(TAX_CODE, TaxpayerType.BUSINESS);

        assertThat(first.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(first.business().taxCode()).isEqualTo(TAX_CODE);
        assertThat(first.business().name()).isEqualTo("CASSO BRANCH");
        assertThat(first.business().status()).isEqualTo("NNT đang hoạt động");
        assertThat(first.business().source()).isEqualTo("https://www.gdt.gov.vn");
        assertThat(first.business().trial()).isFalse();
        assertThat(second).isEqualTo(first);
        assertThat(tokenCalls).hasValue(2);
        assertThat(businessCalls).hasValue(3);
        assertThat(firstAuthorization).hasValue("Bearer test-token-1");
        assertThat(lastAuthorization).hasValue("Bearer test-token-2");
        var submittedCredentials = new ObjectMapper().readTree(credentials.get());
        assertThat(submittedCredentials.size()).isEqualTo(2);
        assertThat(submittedCredentials.path("app_id").asText()).isEqualTo("test-app");
        assertThat(submittedCredentials.path("app_secret").asText()).isEqualTo("test-secret");
    }

    @Test
    void callsTheHouseholdEndpointAndMarksTrialResults() {
        String taxCode = "012345678901";
        server.createContext("/idkit/v1/household/" + taxCode, exchange -> reply(exchange, 200, """
                {"requestId":"req-2","household":{"taxCode":"012345678901","name":"HỘ KINH DOANH MẪU",
                "status":"NNT đang hoạt động","source":"https://www.gdt.gov.vn","address":"Example"}}
                """));
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        var registry = new CassoBusinessTaxRegistry(new ObjectMapper(), true, "trial-app_sample", "test-secret",
                Duration.ofSeconds(5), uri);

        var result = registry.lookup(taxCode, TaxpayerType.HOUSEHOLD);

        assertThat(result.outcome()).isEqualTo(Outcome.FOUND);
        assertThat(result.business().taxCode()).isEqualTo(taxCode);
        assertThat(result.business().name()).isEqualTo("HỘ KINH DOANH MẪU");
        assertThat(result.business().source()).isEqualTo("https://www.gdt.gov.vn");
        assertThat(result.business().trial()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "400|{\"errorCode\":\"IDKIT_BUSINESS_NOT_FOUND\",\"errorType\":\"GRANT_ERROR\"}|NOT_FOUND",
            "400|{\"errorCode\":\"BAD_REQUEST\"}|UNAVAILABLE",
            "404|{\"errorCode\":\"IDKIT_BUSINESS_NOT_FOUND\"}|UNAVAILABLE",
            "502|{}|UNAVAILABLE",
            "200|{\"business\":{\"taxCode\":\"0100000000\",\"name\":\"Other\"}}|UNAVAILABLE",
            "200|not-json|UNAVAILABLE"
    })
    void distinguishesMissingBusinessFromProviderFailures(int httpStatus, String body, Outcome outcome) {
        server.createContext(LOOKUP_PATH, exchange -> reply(exchange, httpStatus, body));
        assertThat(registry(Duration.ofSeconds(5)).lookup(TAX_CODE, TaxpayerType.BUSINESS).outcome()).isEqualTo(outcome);
    }

    @Test
    void reportsRetryAfterAndSuppressesCallsWhileProviderIsRateLimited() {
        var businessCalls = new AtomicInteger();
        server.createContext(LOOKUP_PATH, exchange -> {
            businessCalls.incrementAndGet();
            exchange.getResponseHeaders().add("Retry-After", "60");
            reply(exchange, 429, "{}");
        });
        var registry = registry(Duration.ofSeconds(5));

        var response = registry.lookup(TAX_CODE, TaxpayerType.BUSINESS);

        assertThat(response.outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(response.retryAt()).isAfter(Instant.now().plusSeconds(50));
        assertThat(registry.lookup("0316794479", TaxpayerType.BUSINESS)).isEqualTo(response);
        assertThat(businessCalls).hasValue(1);
    }

    @Test
    void deadlineAlsoAppliesToResponseBodies() {
        var requestReceived = new CountDownLatch(1);
        var releaseResponse = new CountDownLatch(1);
        server.createContext(LOOKUP_PATH, exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 10);
                requestReceived.countDown();
                releaseResponse.await(8, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        });
        var registry = registry(Duration.ofSeconds(2));
        long start = System.nanoTime();
        try {
            assertThat(registry.lookup(TAX_CODE, TaxpayerType.BUSINESS).outcome()).isEqualTo(Outcome.UNAVAILABLE);
            assertThat(requestReceived.getCount()).isZero();
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        } finally {
            releaseResponse.countDown();
        }
    }

    private CassoBusinessTaxRegistry registry(Duration timeout) {
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new CassoBusinessTaxRegistry(new ObjectMapper(), true, "test-app", "test-secret", timeout, uri);
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (exchange) {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }
}

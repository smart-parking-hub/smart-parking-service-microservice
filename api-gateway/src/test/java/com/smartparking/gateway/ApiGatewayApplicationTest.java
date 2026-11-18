package com.smartparking.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false"
})
public class ApiGatewayApplicationTest {
    @Autowired
    private RouteLocator routes;


    @Test
    @DisplayName("Context loads successfully for API Gateway")
    void contextLoads() {
    }

    @Test
    void routesAdminTaxLookupToIdentity() {
        var request = MockServerHttpRequest.post("/api/v1/admin/merchant-verifications/1/submissions/1/tax-lookup");
        var exchange = MockServerWebExchange.from(request);
        var route = routes.getRoutes().filterWhen(candidate -> candidate.getPredicate().apply(exchange))
                .blockFirst(Duration.ofSeconds(5));
        assertThat(route).isNotNull();
        assertThat(route.getId()).isEqualTo("identity-service");
    }
}

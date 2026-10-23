package com.smartparking.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "eureka.client.enabled=false"
})
public class ApiGatewayApplicationTest {

    @Test
    @DisplayName("Context loads successfully for API Gateway")
    void contextLoads() {
    }
}

package com.smartparking.configserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.profiles.active=native"
})
public class ConfigServerApplicationTest {

    @Test
    @DisplayName("Context loads successfully for Config Server")
    void contextLoads() {
    }
}

package com.smartparking.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.payos.PayOS;

@Configuration
@ConfigurationProperties(prefix = "payos")
@Getter
@Setter
public class PayOSConfig {

    private String clientId = "dummy_client_id";
    private String apiKey = "dummy_api_key";
    private String checksumKey = "dummy_checksum_key";
    private String returnUrl = "http://localhost:5173/payment/success";
    private String cancelUrl = "http://localhost:5173/payment/cancel";

    @Bean
    public PayOS payOS() {
        return new PayOS(clientId, apiKey, checksumKey);
    }
}

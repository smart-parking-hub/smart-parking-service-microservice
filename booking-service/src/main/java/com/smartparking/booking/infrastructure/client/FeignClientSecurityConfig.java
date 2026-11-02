package com.smartparking.booking.infrastructure.client;

import com.smartparking.common.security.SecurityConstants;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientSecurityConfig {

    @Value("${internal.secret:" + SecurityConstants.DEFAULT_INTERNAL_SECRET + "}")
    private String internalSecret = SecurityConstants.DEFAULT_INTERNAL_SECRET;

    @Bean
    public RequestInterceptor feignInternalSecretInterceptor() {
        return requestTemplate -> requestTemplate.header(SecurityConstants.HEADER_INTERNAL_SECRET, internalSecret);
    }
}

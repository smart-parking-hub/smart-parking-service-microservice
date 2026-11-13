package com.smartparking.identity.infrastructure.config;

import com.smartparking.common.messaging.OperationsOutboxConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(OperationsOutboxConfiguration.class)
public class AdminOperationsMessagingConfiguration {}

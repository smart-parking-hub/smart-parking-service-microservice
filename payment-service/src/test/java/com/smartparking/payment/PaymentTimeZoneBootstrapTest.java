package com.smartparking.payment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.support.GenericApplicationContext;
import java.time.ZoneId;
import java.util.TimeZone;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentTimeZoneBootstrapTest {
    @ParameterizedTest
    @ValueSource(strings = {"UTC", "America/Los_Angeles"})
    void businessZoneIsConfiguredBeforeSingletonInitialization(String initialZone) {
        TimeZone previous = TimeZone.getDefault();
        try (var context = new GenericApplicationContext()) {
            TimeZone.setDefault(TimeZone.getTimeZone(initialZone));
            // Register the observer first: its initialization must still see the business zone.
            context.registerBean("capturedZone", ZoneId.class, ZoneId::systemDefault);
            context.registerBean("businessTimeZone", BeanFactoryPostProcessor.class,
                    PaymentServiceApplication::businessTimeZone);
            context.refresh();
            assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), context.getBean("capturedZone", ZoneId.class));
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}

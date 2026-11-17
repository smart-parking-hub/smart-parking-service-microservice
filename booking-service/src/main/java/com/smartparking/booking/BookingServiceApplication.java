package com.smartparking.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableJpaAuditing
public class BookingServiceApplication {

    @Bean
    static BeanFactoryPostProcessor businessTimeZone() {
        // Configure the JVM before datasource/JPA beans capture its default timezone.
        return beanFactory -> TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    @Bean
    static HibernatePropertiesCustomizer businessWallClockJdbcTypes() {
        // Bind LocalDateTime directly through JDBC 4.2 instead of converting via Timestamp.
        // The database session timezone may differ from the business JVM timezone.
        return properties -> properties.put(org.hibernate.cfg.MappingSettings.JAVA_TIME_USE_DIRECT_JDBC, true);
    }

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}

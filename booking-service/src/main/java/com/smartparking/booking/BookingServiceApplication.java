package com.smartparking.booking;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableJpaAuditing
public class BookingServiceApplication {

    @PostConstruct
    public void init() {
        // Ép JVM luôn chạy theo múi giờ Việt Nam dù chạy ở bất kỳ môi trường Docker nào
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
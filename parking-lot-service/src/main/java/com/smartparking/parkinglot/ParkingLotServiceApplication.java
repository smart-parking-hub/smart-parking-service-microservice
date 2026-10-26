package com.smartparking.parkinglot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ParkingLotServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ParkingLotServiceApplication.class, args);
    }
}

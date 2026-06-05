package com.example.roombooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartRoomBookingApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartRoomBookingApplication.class, args);
    }
}

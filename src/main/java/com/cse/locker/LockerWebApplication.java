package com.cse.locker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LockerWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(LockerWebApplication.class, args);
    }
}

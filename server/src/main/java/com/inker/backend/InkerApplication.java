package com.inker.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(InkerApplication.class, args);
    }
}

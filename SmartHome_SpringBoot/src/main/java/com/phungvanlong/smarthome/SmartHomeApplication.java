package com.phungvanlong.smarthome;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartHomeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartHomeApplication.class, args);
        System.out.println("=================================================");
        System.out.println("  SmartHome Spring Boot Server is running at:   ");
        System.out.println("  http://localhost:8080                         ");
        System.out.println("=================================================");
    }
}

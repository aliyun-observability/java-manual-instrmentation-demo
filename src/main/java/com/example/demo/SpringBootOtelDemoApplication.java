package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot Application with OpenTelemetry Manual Instrumentation Demo
 * 
 * This application demonstrates how to use OpenTelemetry SDK for manual instrumentation
 * in a Spring Boot application to create custom spans and metrics.
 */
@SpringBootApplication
@EnableScheduling
public class SpringBootOtelDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootOtelDemoApplication.class, args);
    }
}
package com.graphchart.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TelemetryApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(TelemetryApiApplication.class, args);
    }
}

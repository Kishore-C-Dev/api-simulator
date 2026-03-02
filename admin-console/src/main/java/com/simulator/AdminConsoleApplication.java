package com.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AdminConsoleApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminConsoleApplication.class, args);
    }
}

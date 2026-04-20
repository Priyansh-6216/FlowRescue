package com.flowrescue.compensation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.flowrescue.compensation", "com.flowrescue.common"})
public class CompensationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CompensationServiceApplication.class, args);
    }
}

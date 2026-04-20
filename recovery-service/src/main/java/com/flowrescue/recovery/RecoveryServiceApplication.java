package com.flowrescue.recovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.flowrescue.recovery", "com.flowrescue.common"})
public class RecoveryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RecoveryServiceApplication.class, args);
    }
}

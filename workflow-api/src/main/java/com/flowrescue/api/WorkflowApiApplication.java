package com.flowrescue.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.flowrescue.api", "com.flowrescue.common"})
public class WorkflowApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkflowApiApplication.class, args);
    }
}

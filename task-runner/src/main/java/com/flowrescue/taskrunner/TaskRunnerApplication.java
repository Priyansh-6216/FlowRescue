package com.flowrescue.taskrunner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.flowrescue.taskrunner", "com.flowrescue.common"})
public class TaskRunnerApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskRunnerApplication.class, args);
    }
}

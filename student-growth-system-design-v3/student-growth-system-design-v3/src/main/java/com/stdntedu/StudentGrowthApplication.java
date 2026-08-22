package com.stdntedu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StudentGrowthApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentGrowthApplication.class, args);
    }
}

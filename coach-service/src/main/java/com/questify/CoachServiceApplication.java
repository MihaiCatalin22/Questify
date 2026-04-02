package com.questify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CoachServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoachServiceApplication.class, args);
    }
}

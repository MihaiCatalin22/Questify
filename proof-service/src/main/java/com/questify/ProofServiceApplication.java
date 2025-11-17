package com.questify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ProofServiceApplication {
    public static void main(String[] args) { SpringApplication.run(ProofServiceApplication.class, args); }
}

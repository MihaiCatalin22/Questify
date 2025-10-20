package com.questify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class Questify {
    public static void main(String[] args) {
        SpringApplication.run(Questify.class, args);
    }
}
package com.patternforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PatternForgeApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(PatternForgeApplication.class, args);
    }
}

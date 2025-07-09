package com.reviewsystem;

import com.reviewsystem.service.ReviewImportService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;

@SpringBootApplication
@EnableConfigurationProperties
@EnableScheduling
public class ReviewSystemApplication {
    private static final Logger logger = LogManager.getLogger(ReviewSystemApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ReviewSystemApplication.class, args);
    }

    @Bean
    public CommandLineRunner importJLFileRunner(ReviewImportService reviewImportService) {
        return args -> {
            if (args.length > 0) {
                reviewImportService.importJLFiles();
            }
        };
    }
} 
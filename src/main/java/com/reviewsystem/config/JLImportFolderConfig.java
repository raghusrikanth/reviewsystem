package com.reviewsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jlimport")
public class JLImportFolderConfig {
    private String folderPath;
    private String tempDir;
    private int concurrentThreads = 4;
} 
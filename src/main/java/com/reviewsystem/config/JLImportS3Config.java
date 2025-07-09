package com.reviewsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jlimport.s3")
public class JLImportS3Config {
    private String bucket;
    private String region;
    private String accessKey;
    private String secretKey;
    private String prefix;
} 
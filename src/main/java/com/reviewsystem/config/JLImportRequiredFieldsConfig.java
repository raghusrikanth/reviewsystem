package com.reviewsystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "jlimport.required-fields")
public class JLImportRequiredFieldsConfig {
    private List<String> topLevel;
    private List<String> comment;
} 
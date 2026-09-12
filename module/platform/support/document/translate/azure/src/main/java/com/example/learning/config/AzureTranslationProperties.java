package com.example.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "azure.translator")
public record AzureTranslationProperties(
        String endpoint,
        String region,
        String key
) {
}

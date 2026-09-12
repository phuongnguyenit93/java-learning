package com.example.learning.config;

import com.azure.ai.translation.text.TextTranslationClient;
import com.azure.ai.translation.text.TextTranslationClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AzureTranslationProperties.class)
public class AzureTranslationClient {
    @Bean
    public TextTranslationClient textTranslationClient(
            AzureTranslationProperties properties
    ) {
        return new TextTranslationClientBuilder()
                .credential(new AzureKeyCredential(properties.key()))
                .region(properties.region())
                .endpoint(properties.endpoint())
                .buildClient();
    }
}

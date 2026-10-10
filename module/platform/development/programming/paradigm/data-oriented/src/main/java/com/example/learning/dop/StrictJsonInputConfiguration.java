package com.example.learning.dop;

import com.fasterxml.jackson.core.JsonParser;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Preserve the distinction between malformed/ambiguous transport JSON (400)
 * and a syntactically valid but schema-invalid order map (422).
 */
@Configuration
public class StrictJsonInputConfiguration {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer rejectDuplicateJsonFields() {
        return builder -> builder.featuresToEnable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
}

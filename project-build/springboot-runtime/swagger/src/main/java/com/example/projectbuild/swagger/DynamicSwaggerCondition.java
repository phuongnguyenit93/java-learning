package com.example.projectbuild.swagger;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

public class DynamicSwaggerCondition extends AllNestedConditions {
    public DynamicSwaggerCondition() {
        super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(prefix = "swagger", name = "enabled", havingValue = "true")
    static class SwaggerEnabled {}
}

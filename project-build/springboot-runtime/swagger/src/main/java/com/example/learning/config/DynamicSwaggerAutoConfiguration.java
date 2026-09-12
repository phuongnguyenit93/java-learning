package com.example.learning.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({DynamicSwaggerRegistrar.class,SwaggerResourceConfiguration.class})
@Conditional(DynamicSwaggerCondition.class)
@ConditionalOnClass(name = "org.springdoc.core.models.GroupedOpenApi")
public class DynamicSwaggerAutoConfiguration {
}

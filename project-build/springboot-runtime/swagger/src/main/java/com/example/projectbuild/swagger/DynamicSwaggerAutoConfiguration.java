package com.example.projectbuild.swagger;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({DynamicSwaggerRegistrar.class,SwaggerResourceConfiguration.class})
@Conditional(DynamicSwaggerCondition.class)
@ConditionalOnClass(name = "org.springdoc.core.models.GroupedOpenApi")
public class DynamicSwaggerAutoConfiguration {
}

package com.example.projectbuild.swagger.servlet;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "swagger", name = "enabled", havingValue = "true")
@Import(ServletSwaggerResourceConfiguration.class)
public class ServletSwaggerAutoConfiguration {
}

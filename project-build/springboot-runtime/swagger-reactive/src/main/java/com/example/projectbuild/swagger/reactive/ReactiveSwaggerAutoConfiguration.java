package com.example.projectbuild.swagger.reactive;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "swagger", name = "enabled", havingValue = "true")
@Import(ReactiveSwaggerResourceConfiguration.class)
public class ReactiveSwaggerAutoConfiguration {

    @Bean
    RouterFunction<ServerResponse> swaggerRootRedirect() {
        return RouterFunctions.route(
                RequestPredicates.GET("/"),
                request -> ServerResponse
                        .status(HttpStatus.FOUND)
                        .location(URI.create("/custom-swagger.html"))
                        .build()
        );
    }
}

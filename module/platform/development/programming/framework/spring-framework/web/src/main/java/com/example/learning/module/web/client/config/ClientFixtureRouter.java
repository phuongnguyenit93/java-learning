package com.example.learning.module.web.client.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ClientFixtureRouter {

    @Bean
    public RouterFunction<ServerResponse> springWebClientFixtureRoutes() {
        return RouterFunctions.route()
                .GET("/spring-web/support/ok", request ->
                        ServerResponse.ok().body(Map.of(
                                "route", "ok",
                                "message", "fixture-response"
                        )))
                .GET("/spring-web/support/missing", request ->
                        ServerResponse.status(404).body(Map.of(
                                "route", "missing",
                                "message", "fixture-not-found"
                        )))
                .GET("/spring-web/support/echo/{id}", request -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("id", request.pathVariable("id"));
                    body.put("mode", request.param("mode").orElse(""));
                    body.put(
                            "marker",
                            request.headers().firstHeader("X-Demo-Marker")
                    );
                    return ServerResponse.ok().body(body);
                })
                .build();
    }
}

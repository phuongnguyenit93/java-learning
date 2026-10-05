package com.example.learning.module.web.client.service;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Map;

@HttpExchange("/spring-web/support/echo")
public interface EchoHttpService {

    @GetExchange("/{id}")
    Map<String, Object> echo(
            @PathVariable String id,
            @RequestParam String mode,
            @RequestHeader("X-Demo-Marker") String marker
    );
}

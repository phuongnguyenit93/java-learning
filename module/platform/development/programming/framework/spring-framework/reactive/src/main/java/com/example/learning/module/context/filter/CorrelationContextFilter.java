package com.example.learning.module.context.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationContextFilter implements WebFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    public static final String REACTOR_CONTEXT_KEY = "reactiveExperimentCorrelationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestedCorrelationId = exchange.getRequest()
                .getHeaders()
                .getFirst(CORRELATION_HEADER);
        String correlationId = normalizeCorrelationId(requestedCorrelationId);

        return chain.filter(exchange)
                .contextWrite(context ->
                        context.put(REACTOR_CONTEXT_KEY, correlationId)
                );
    }

    public static String normalizeCorrelationId(String requestedCorrelationId) {
        return requestedCorrelationId == null || requestedCorrelationId.isBlank()
                ? "demo-context"
                : requestedCorrelationId;
    }
}

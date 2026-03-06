package com.example.learning.shared;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * TaskDecorator that propagates MDC and Request context (including AuditContext) 
 * to async threads.
 */
@Component
public class ThreadLocalTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        // Capture context from parent thread
        Map<String, String> contextMap = Optional.ofNullable(MDC.getCopyOfContextMap())
                .orElse(Collections.emptyMap());
        
        // Capture RequestAttributes (which includes AuditContext as request-scoped bean)
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        
        return () -> {
            try {
                // Restore MDC context in async thread
                MDC.setContextMap(contextMap);
                
                // Restore RequestAttributes if available
                if (requestAttributes != null) {
                    RequestContextHolder.setRequestAttributes(requestAttributes);
                }
                
                runnable.run();
            } finally {
                // Clean up to prevent memory leaks
                MDC.clear();
                RequestContextHolder.resetRequestAttributes();
            }
        };
    }
}
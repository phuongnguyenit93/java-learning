package com.example.learning.module.executor.config;

import com.example.learning.module.executor.context.DemoContext;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

public class DemoTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String captured = DemoContext.get();
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        RequestAttributes capturedRequestAttributes = RequestContextHolder.getRequestAttributes();

        return () -> {
            String previous = DemoContext.get();
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            RequestAttributes previousRequestAttributes = RequestContextHolder.getRequestAttributes();

            if (captured == null) {
                DemoContext.remove();
            } else {
                DemoContext.set(captured);
            }

            if (capturedMdc == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(capturedMdc);
            }

            if (capturedRequestAttributes == null) {
                RequestContextHolder.resetRequestAttributes();
            } else {
                RequestContextHolder.setRequestAttributes(capturedRequestAttributes);
            }

            try {
                runnable.run();
            } finally {
                if (previous == null) {
                    DemoContext.remove();
                } else {
                    DemoContext.set(previous);
                }

                if (previousMdc == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previousMdc);
                }

                if (previousRequestAttributes == null) {
                    RequestContextHolder.resetRequestAttributes();
                } else {
                    RequestContextHolder.setRequestAttributes(previousRequestAttributes);
                }
            }
        };
    }
}

package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/annotation/repeatable")
public class RepeatableAnnotationController {

    @Repeatable(Audits.class)
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Audit {
        String action();

        int level() default 1;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Audits {
        Audit[] value();
    }

    static final class PaymentService {
        @Audit(action = "SECURITY")
        @Audit(action = "COMPLIANCE", level = 2)
        void transfer() {
        }
    }

    @GetMapping
    public Map<String, Object> repeatable() throws NoSuchMethodException {
        Method method = PaymentService.class.getDeclaredMethod("transfer");

        List<Map<String, Object>> audits = Arrays.stream(method.getAnnotationsByType(Audit.class))
                .map(audit -> Map.<String, Object>of(
                        "action", audit.action(),
                        "level", audit.level()
                ))
                .toList();

        return Map.of(
                "getAnnotationAuditIsNull", method.getAnnotation(Audit.class) == null,
                "containerPresent", method.getDeclaredAnnotation(Audits.class) != null,
                "getAnnotationsByTypeCount", audits.size(),
                "audits", audits
        );
    }
}

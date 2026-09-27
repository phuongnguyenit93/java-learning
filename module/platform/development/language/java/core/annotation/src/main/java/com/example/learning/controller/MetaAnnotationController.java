package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/annotation/meta")
public class MetaAnnotationController {

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface AuditedComponent {
    }

    @GetMapping("/targets")
    public Map<String, Object> inspectTargets() {
        Target target = AuditedComponent.class.getAnnotation(Target.class);
        Retention retention = AuditedComponent.class.getAnnotation(Retention.class);

        List<String> targets = Arrays.stream(target.value())
                .map(Enum::name)
                .sorted()
                .toList();

        return Map.of(
                "targets", targets,
                "retention", retention.value().name(),
                "documented", AuditedComponent.class.isAnnotationPresent(Documented.class)
        );
    }
}

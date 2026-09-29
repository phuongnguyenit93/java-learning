package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/annotation/inspection")
public class AnnotationInspectionController {

    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.TYPE)
    @interface SourceOnly {
    }

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    @interface ClassOnly {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface RuntimeVisible {
    }

    @SourceOnly
    @ClassOnly
    @RuntimeVisible
    static final class Sample {
    }

    @GetMapping("/retention")
    public Map<String, Object> retention() {
        List<String> visibleAnnotations = Arrays.stream(Sample.class.getDeclaredAnnotations())
                .map(Annotation::annotationType)
                .map(Class::getSimpleName)
                .sorted()
                .toList();

        return Map.of(
                "sourceVisibleAtRuntime", Sample.class.isAnnotationPresent(SourceOnly.class),
                "classVisibleAtRuntime", Sample.class.isAnnotationPresent(ClassOnly.class),
                "runtimeVisibleAtRuntime", Sample.class.isAnnotationPresent(RuntimeVisible.class),
                "declaredRuntimeAnnotations", visibleAnnotations
        );
    }
}

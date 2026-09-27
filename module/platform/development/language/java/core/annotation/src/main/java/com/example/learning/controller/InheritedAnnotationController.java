package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Map;

@RestController
@RequestMapping("/java/core/annotation/inherited")
public class InheritedAnnotationController {

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface AuditedType {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface AuditedMethod {
    }

    @AuditedType
    static class BaseService {
        @AuditedMethod
        public void process() {
        }
    }

    static final class ChildService extends BaseService {
        @Override
        public void process() {
        }
    }

    @AuditedType
    interface AuditedContract {
    }

    static final class InterfaceOnlyService implements AuditedContract {
    }

    @GetMapping
    public Map<String, Object> inheritance() throws NoSuchMethodException {
        Method childMethod = ChildService.class.getDeclaredMethod("process");
        Method baseMethod = BaseService.class.getDeclaredMethod("process");

        return Map.of(
                "childClassSeesSuperclassAnnotation", ChildService.class.isAnnotationPresent(AuditedType.class),
                "interfaceOnlyClassSeesInterfaceAnnotation", InterfaceOnlyService.class.isAnnotationPresent(AuditedType.class),
                "baseMethodAnnotated", baseMethod.isAnnotationPresent(AuditedMethod.class),
                "overridingMethodAnnotated", childMethod.isAnnotationPresent(AuditedMethod.class)
        );
    }
}

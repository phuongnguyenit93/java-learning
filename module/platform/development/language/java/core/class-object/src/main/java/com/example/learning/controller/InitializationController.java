package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/class-object/initialization")
public class InitializationController {
    private static final List<String> STATIC_TRACE = new ArrayList<>();

    static class Parent {
        final List<String> trace;
        static { STATIC_TRACE.add("parent-static"); }
        Parent(List<String> trace) {
            this.trace = trace;
            trace.add("parent-constructor");
        }
    }

    static final class Child extends Parent {
        static { STATIC_TRACE.add("child-static"); }
        private final int field = initField();
        { trace.add("child-instance-initializer"); }

        Child(List<String> trace) {
            super(trace);
            trace.add("child-constructor");
        }

        private int initField() {
            trace.add("child-field-initializer");
            return 42;
        }
    }

    @GetMapping("/order")
    public Map<String, Object> traceOrder() {
        List<String> instanceTrace = new ArrayList<>();
        Child child = new Child(instanceTrace);
        return Map.of(
                "staticInitialization", List.copyOf(STATIC_TRACE),
                "instanceConstruction", List.copyOf(instanceTrace),
                "fieldValue", child.field);
    }
}

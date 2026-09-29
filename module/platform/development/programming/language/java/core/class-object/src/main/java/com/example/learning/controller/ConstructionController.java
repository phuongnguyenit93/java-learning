package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/java/core/class-object/construction")
public class ConstructionController {
    static class Parent {
        int observed;
        Parent() { hook(); }
        void hook() { }
    }

    static final class Child extends Parent {
        int value = 42;
        @Override void hook() { observed = value; }
    }

    @GetMapping("/override-risk")
    public Map<String, Object> constructorDispatchRisk() {
        Child child = new Child();
        return Map.of(
                "valueObservedDuringSuperConstructor", child.observed,
                "valueAfterConstruction", child.value,
                "conclusion", "dynamic dispatch reached Child before Child field initialization");
    }
}

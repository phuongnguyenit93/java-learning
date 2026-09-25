package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/java/core/language-basics/dispatch")
public class MethodDispatchController {
    @GetMapping("/overload-vs-override")
    public Map<String, Object> overloadVsOverride() {
        Parent declared = new Child();
        return Map.of(
                "declaredType", "Parent",
                "runtimeType", declared.getClass().getSimpleName(),
                "overloadChosen", choose(declared),
                "overrideBody", declared.describe()
        );
    }

    private String choose(Parent value) { return "choose(Parent)"; }
    private String choose(Child value) { return "choose(Child)"; }

    private static class Parent { String describe() { return "Parent.describe"; } }
    private static final class Child extends Parent { @Override String describe() { return "Child.describe"; } }
}

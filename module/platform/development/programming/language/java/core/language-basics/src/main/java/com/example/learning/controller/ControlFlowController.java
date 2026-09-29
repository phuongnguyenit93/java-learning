package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/language-basics/control-flow")
public class ControlFlowController {

    @GetMapping("/pattern-switch")
    public Map<String, Object> patternSwitch() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("integerCase", describe(42));
        result.put("stringCase", describe("java"));
        result.put("nullCase", describe(null));
        result.put("fallbackCase", describe(3.14));
        return result;
    }

    private String describe(Object value) {
        return switch (value) {
            case Integer integer -> "integer:" + integer;
            case String text -> "string:" + text;
            case null -> "null";
            default -> "other:" + value.getClass().getSimpleName();
        };
    }
}

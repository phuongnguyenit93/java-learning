package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/language-basics/casting")
public class CastingController {
    @GetMapping("/safe-and-unsafe")
    public Map<String, Object> safeAndUnsafeCast(@RequestParam(defaultValue = "hello") String input) {
        Object value = input;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runtimeClass", value.getClass().getSimpleName());
        result.put("safeCast", value instanceof String text ? text.toUpperCase() : "not-string");
        try {
            Integer ignored = (Integer) value;
            result.put("unsafeCast", ignored);
        } catch (ClassCastException exception) {
            result.put("unsafeCastFailure", exception.getClass().getSimpleName());
        }
        return result;
    }
}

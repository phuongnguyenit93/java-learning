package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/generics/erasure")
public class ErasureController {

    @GetMapping("/runtime-types")
    public Map<String, Object> inspectRuntimeTypes() {
        List<String> texts = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("textsSourceType", "List<String>");
        result.put("numbersSourceType", "List<Integer>");
        result.put("textsRuntimeClass", texts.getClass().getName());
        result.put("numbersRuntimeClass", numbers.getClass().getName());
        result.put("sameRuntimeClass", texts.getClass() == numbers.getClass());
        result.put("observation", "different generic arguments do not create different runtime ArrayList classes");
        return result;
    }

    @GetMapping("/bridge-method")
    public Map<String, Object> bridgeMethod() {
        List<Map<String, Object>> methods = Arrays.stream(StringMapper.class.getDeclaredMethods())
            .map(this::describe)
            .toList();

        long bridgeCount = methods.stream()
            .filter(method -> Boolean.TRUE.equals(method.get("bridge")))
            .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("declaredMethods", methods);
        result.put("bridgeMethodCount", bridgeCount);
        result.put("observation", "javac emits a synthetic bridge method so the erased Mapper contract still dispatches to map(String)");
        return result;
    }

    private Map<String, Object> describe(Method method) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("signature", method.toGenericString());
        description.put("bridge", method.isBridge());
        description.put("synthetic", method.isSynthetic());
        return description;
    }

    private interface Mapper<T> {
        T map(T value);
    }

    private static final class StringMapper implements Mapper<String> {
        @Override
        public String map(String value) {
            return value.trim();
        }
    }
}

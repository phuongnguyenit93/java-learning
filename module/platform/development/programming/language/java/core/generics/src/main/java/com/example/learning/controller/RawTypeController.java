package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/generics/raw-type")
public class RawTypeController {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @GetMapping("/heap-pollution")
    public Map<String, Object> heapPollution() {
        List<String> names = new ArrayList<>();
        names.add("java");

        List raw = names;
        raw.add(123);

        String failureType = "";
        String failureMessage = "";
        try {
            String ignored = names.get(1);
        } catch (ClassCastException exception) {
            failureType = exception.getClass().getSimpleName();
            failureMessage = exception.getMessage();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("declaredType", "List<String>");
        result.put("rawWrite", 123);
        result.put("runtimeContents", raw);
        result.put("failureType", failureType);
        result.put("failureMessage", failureMessage);
        result.put("observation", "the unchecked raw write succeeds, while the typed read fails later");
        return result;
    }
}

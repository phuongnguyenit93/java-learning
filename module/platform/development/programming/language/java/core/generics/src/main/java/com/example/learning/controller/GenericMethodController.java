package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/generics/method")
public class GenericMethodController {

    @GetMapping("/inference")
    public Map<String, Object> inference() {
        String text = echo("java");
        Integer number = echo(21);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("text", text);
        result.put("textRuntimeType", text.getClass().getSimpleName());
        result.put("number", number);
        result.put("numberRuntimeType", number.getClass().getSimpleName());
        result.put("observation", "the same generic method preserves the inferred return type for each call");
        return result;
    }

    private static <T> T echo(T value) {
        return value;
    }
}

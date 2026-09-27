package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/generics/wildcard")
public class WildcardController {

    @GetMapping("/producer-consumer")
    public Map<String, Object> producerConsumer() {
        List<Integer> source = List.of(10, 20, 30);
        List<Number> target = new ArrayList<>();

        copy(source, target);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceType", "List<Integer>");
        result.put("sourceRole", "producer via ? extends T");
        result.put("targetType", "List<Number>");
        result.put("targetRole", "consumer via ? super T");
        result.put("copiedValues", target);
        result.put("observation", "Integer values can be read from the producer and safely written to a Number consumer");
        return result;
    }

    private static <T> void copy(List<? extends T> source, List<? super T> target) {
        for (T value : source) {
            target.add(value);
        }
    }
}

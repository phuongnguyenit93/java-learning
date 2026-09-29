package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/collection/map")
public class MapController {

    @GetMapping("/ordering-and-compute")
    public Map<String, Object> orderingAndCompute() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("NEW", 2);
        counts.put("PAID", 1);

        Map<String, Integer> before = new LinkedHashMap<>(counts);

        counts.compute("NEW", (status, current) -> current == null ? 1 : current + 1);
        counts.computeIfAbsent("PACKING", status -> 0);
        counts.merge("PAID", 2, Integer::sum);
        counts.merge("SHIPPED", 1, Integer::sum);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("before", before);
        result.put("after", new LinkedHashMap<>(counts));
        result.put("newAfterCompute", counts.get("NEW"));
        result.put("packingCreatedByComputeIfAbsent", counts.get("PACKING"));
        result.put("paidAfterMerge", counts.get("PAID"));
        result.put("shippedCreatedByMerge", counts.get("SHIPPED"));
        result.put("encounterOrder", counts.keySet().stream().toList());
        return result;
    }
}

package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@RestController
@RequestMapping("/java/core/collection/set")
public class SetController {

    @GetMapping("/uniqueness-and-order")
    public Map<String, Object> uniquenessAndOrder() {
        List<String> input = List.of("B", "A", "B", "C", "A");

        Set<String> hashSet = new HashSet<>(input);
        Set<String> linkedHashSet = new LinkedHashSet<>(input);
        Set<String> treeSet = new TreeSet<>(input);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input", input);
        result.put("inputSize", input.size());
        result.put("uniqueSize", hashSet.size());
        result.put("duplicateBRejected", !hashSet.add("B"));
        result.put("hashSetOrderContract", "no encounter-order guarantee");
        result.put("linkedHashSetInsertionOrder", new ArrayList<>(linkedHashSet));
        result.put("treeSetSortedOrder", new ArrayList<>(treeSet));
        return result;
    }
}

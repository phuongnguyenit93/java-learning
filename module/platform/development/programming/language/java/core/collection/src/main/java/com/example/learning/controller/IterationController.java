package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/collection/iteration")
public class IterationController {

    @GetMapping("/fail-fast")
    public Map<String, Object> failFast() {
        List<String> unsafe = new ArrayList<>(List.of("U01", "U02", "U03"));
        Iterator<String> iterator = unsafe.iterator();
        String first = iterator.next();
        unsafe.add("U04");

        String detectedException;
        try {
            iterator.next();
            detectedException = "none";
        } catch (ConcurrentModificationException exception) {
            detectedException = exception.getClass().getSimpleName();
        }

        List<String> safe = new ArrayList<>(List.of("U01", "U02", "U03"));
        Iterator<String> safeIterator = safe.iterator();
        while (safeIterator.hasNext()) {
            if (safeIterator.next().equals("U02")) {
                safeIterator.remove();
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("firstReadBeforeExternalMutation", first);
        result.put("collectionAfterExternalMutation", List.copyOf(unsafe));
        result.put("iteratorDetected", detectedException);
        result.put("sameThreadCanTriggerCme", "ConcurrentModificationException".equals(detectedException));
        result.put("safeIteratorRemovalResult", List.copyOf(safe));
        return result;
    }
}

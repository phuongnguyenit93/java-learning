package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/language-basics/pass-by-value")
public class PassByValueController {
    @GetMapping("/mutate-and-reassign")
    public Map<String, Object> mutateAndReassign() {
        Box callerBox = new Box(1);
        int identityBefore = System.identityHashCode(callerBox);
        mutateThenReassign(callerBox);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("callerValueAfter", callerBox.value);
        result.put("callerIdentityUnchanged", identityBefore == System.identityHashCode(callerBox));
        result.put("conclusion", "object mutation is visible; parameter reassignment is local");
        return result;
    }

    private void mutateThenReassign(Box box) {
        box.value = 2;
        box = new Box(99);
        box.value = 100;
    }

    private static final class Box {
        private int value;
        private Box(int value) { this.value = value; }
    }
}

package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/language-basics/operator")
public class OperatorController {
    @GetMapping("/short-circuit")
    public Map<String, Object> shortCircuit() {
        List<String> trace = new ArrayList<>();
        boolean andResult = left(false, trace) && right(trace);
        boolean orResult = left(true, trace) || right(trace);
        return Map.of("andResult", andResult, "orResult", orResult, "trace", trace);
    }

    private boolean left(boolean value, List<String> trace) {
        trace.add("left(" + value + ")");
        return value;
    }

    private boolean right(List<String> trace) {
        trace.add("right");
        return true;
    }
}

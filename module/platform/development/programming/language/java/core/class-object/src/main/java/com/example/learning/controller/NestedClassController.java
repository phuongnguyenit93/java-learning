package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/java/core/class-object/nested")
public class NestedClassController {
    private final int outerOffset = 2;

    @GetMapping("/capture")
    public Map<String, Object> capture() {
        int localBase = 40;

        class LocalCalculator {
            int answer() { return localBase + outerOffset; }
        }

        LocalCalculator calculator = new LocalCalculator();
        return Map.of(
                "capturedLocal", localBase,
                "outerInstanceValue", outerOffset,
                "computed", calculator.answer(),
                "rule", "captured local variables must be final or effectively final");
    }
}

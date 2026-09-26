package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/java/core/numbers/floating")
public class FloatingPointController {

    @GetMapping("/precision")
    public Map<String, Object> precision() {
        double value = 0.1d + 0.2d;
        return Map.of(
                "computed", value,
                "expectedDecimal", 0.3d,
                "exactEqual", value == 0.3d,
                "delta", Math.abs(value - 0.3d)
        );
    }

    @GetMapping("/special-values")
    public Map<String, Object> specialValues() {
        return Map.of(
                "nanEqualsItself", Double.NaN == Double.NaN,
                "isNaN", Double.isNaN(0.0d / 0.0d),
                "positiveInfinity", 1.0d / 0.0d,
                "zeroEqualsNegativeZero", 0.0d == -0.0d,
                "positiveZeroReciprocal", 1.0d / 0.0d,
                "negativeZeroReciprocal", 1.0d / -0.0d
        );
    }

    @GetMapping("/comparison")
    public Map<String, Object> comparison() {
        double actual = 0.1d + 0.2d;
        double expected = 0.3d;
        double epsilon = 1.0e-9;
        double delta = Math.abs(actual - expected);

        return Map.of(
                "actual", actual,
                "expected", expected,
                "exactEqual", actual == expected,
                "epsilon", epsilon,
                "delta", delta,
                "withinTolerance", delta <= epsilon
        );
    }
}

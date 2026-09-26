package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/numbers/math")
public class MathController {

    @GetMapping("/floor-division")
    public Map<String, Object> floorDivision() {
        return Map.of(
                "ordinaryDivision", -7 / 3,
                "floorDivision", Math.floorDiv(-7, 3),
                "ordinaryRemainder", -7 % 3,
                "floorMod", Math.floorMod(-7, 3)
        );
    }

    @GetMapping("/exact-boundaries")
    public Map<String, Object> exactBoundaries() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uncheckedAbsMin", Math.abs(Integer.MIN_VALUE));
        result.put("uncheckedDivideMin", Integer.MIN_VALUE / -1);

        try {
            Math.absExact(Integer.MIN_VALUE);
        } catch (ArithmeticException e) {
            result.put("absExactFailure", e.getClass().getSimpleName());
        }

        try {
            Math.divideExact(Integer.MIN_VALUE, -1);
        } catch (ArithmeticException e) {
            result.put("divideExactFailure", e.getClass().getSimpleName());
        }
        return result;
    }
}

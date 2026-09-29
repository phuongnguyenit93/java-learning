package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/numbers/integer")
public class IntegerArithmeticController {

    @GetMapping("/overflow")
    public Map<String, Object> overflow() {
        int wrapped = Integer.MAX_VALUE + 1;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("max", Integer.MAX_VALUE);
        result.put("wrapped", wrapped);
        try {
            Math.addExact(Integer.MAX_VALUE, 1);
        } catch (ArithmeticException e) {
            result.put("checkedFailure", e.getClass().getSimpleName());
        }
        return result;
    }

    @GetMapping("/intermediate-overflow")
    public Map<String, Object> intermediateOverflow() {
        int quantity = 1_000_000;
        int unitPrice = 10_000;

        long wrong = quantity * unitPrice;
        long correct = (long) quantity * unitPrice;

        return Map.of(
                "quantity", quantity,
                "unitPrice", unitPrice,
                "intProductThenWidened", wrong,
                "widenBeforeMultiply", correct,
                "sameResult", wrong == correct
        );
    }

    @GetMapping("/boundary-cases")
    public Map<String, Object> boundaryCases() {
        return Map.of(
                "min", Integer.MIN_VALUE,
                "absUnchecked", Math.abs(Integer.MIN_VALUE),
                "divideUnchecked", Integer.MIN_VALUE / -1
        );
    }
}

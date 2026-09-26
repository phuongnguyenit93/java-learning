package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/numbers/big-integer")
public class BigIntegerController {

    @GetMapping("/conversion-boundary")
    public Map<String, Object> conversionBoundary() {
        BigInteger huge = new BigInteger("999999999999999999999");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", huge.toString());
        result.put("intValueUnchecked", huge.intValue());

        try {
            huge.intValueExact();
        } catch (ArithmeticException e) {
            result.put("intValueExactFailure", e.getClass().getSimpleName());
        }
        return result;
    }

    @GetMapping("/remainder-vs-mod")
    public Map<String, Object> remainderVsMod() {
        BigInteger value = BigInteger.valueOf(-7);
        BigInteger divisor = BigInteger.valueOf(3);
        return Map.of(
                "value", value,
                "divisor", divisor,
                "remainder", value.remainder(divisor),
                "mod", value.mod(divisor)
        );
    }
}

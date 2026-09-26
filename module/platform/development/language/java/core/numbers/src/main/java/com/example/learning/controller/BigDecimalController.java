package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@RestController
@RequestMapping("/java/core/numbers/big-decimal")
public class BigDecimalController {

    @GetMapping("/construction")
    public Map<String, Object> construction() {
        return Map.of(
                "fromString", new BigDecimal("0.1").toString(),
                "fromValueOf", BigDecimal.valueOf(0.1d).toString(),
                "fromDouble", new BigDecimal(0.1d).toString()
        );
    }

    @GetMapping("/comparison")
    public Map<String, Object> comparison() {
        BigDecimal left = new BigDecimal("1.0");
        BigDecimal right = new BigDecimal("1.00");
        return Map.of(
                "leftScale", left.scale(),
                "rightScale", right.scale(),
                "equals", left.equals(right),
                "compareTo", left.compareTo(right)
        );
    }

    @GetMapping("/division-rounding")
    public Map<String, Object> divisionAndRounding() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            BigDecimal.ONE.divide(new BigDecimal("3"));
        } catch (ArithmeticException e) {
            result.put("exactDivisionFailure", e.getClass().getSimpleName());
        }
        result.put(
                "rounded",
                BigDecimal.ONE.divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP).toString()
        );
        return result;
    }

    @GetMapping("/precision-scale")
    public Map<String, Object> precisionScale() {
        BigDecimal value = new BigDecimal("123.4500");
        BigDecimal normalized = value.stripTrailingZeros();
        return Map.of(
                "value", value.toString(),
                "precision", value.precision(),
                "scale", value.scale(),
                "normalized", normalized.toString(),
                "normalizedPrecision", normalized.precision(),
                "normalizedScale", normalized.scale()
        );
    }

    @GetMapping("/math-context-vs-scale")
    public Map<String, Object> mathContextVsScale() {
        BigDecimal value = new BigDecimal("12345.67");
        return Map.of(
                "source", value.toString(),
                "setScaleOne", value.setScale(1, RoundingMode.HALF_UP).toString(),
                "precisionFour", value.round(new MathContext(4, RoundingMode.HALF_UP)).toString()
        );
    }

    @GetMapping("/rounding-modes")
    public Map<String, Object> roundingModes() {
        BigDecimal positive = new BigDecimal("2.5");
        BigDecimal negative = new BigDecimal("-2.5");
        return Map.of(
                "positiveHalfUp", positive.setScale(0, RoundingMode.HALF_UP).toString(),
                "positiveHalfDown", positive.setScale(0, RoundingMode.HALF_DOWN).toString(),
                "positiveHalfEven", positive.setScale(0, RoundingMode.HALF_EVEN).toString(),
                "negativeHalfUp", negative.setScale(0, RoundingMode.HALF_UP).toString(),
                "negativeHalfDown", negative.setScale(0, RoundingMode.HALF_DOWN).toString(),
                "negativeHalfEven", negative.setScale(0, RoundingMode.HALF_EVEN).toString()
        );
    }

    @GetMapping("/collection-semantics")
    public Map<String, Object> collectionSemantics() {
        BigDecimal left = new BigDecimal("1.0");
        BigDecimal right = new BigDecimal("1.00");

        Set<BigDecimal> hashSet = new HashSet<>();
        hashSet.add(left);
        hashSet.add(right);

        Set<BigDecimal> treeSet = new TreeSet<>();
        treeSet.add(left);
        treeSet.add(right);

        return Map.of(
                "equals", left.equals(right),
                "compareTo", left.compareTo(right),
                "hashSetSize", hashSet.size(),
                "treeSetSize", treeSet.size()
        );
    }
}

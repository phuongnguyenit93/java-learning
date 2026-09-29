package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/language-basics/overload")
public class OverloadResolutionController {
    @GetMapping("/selection-matrix")
    public Map<String, Object> selectionMatrix() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intLiteral", pick(1));
        result.put("boxedVariable", pick(Integer.valueOf(1)));
        result.put("explicitVarargs", pick(new int[]{1, 2}));
        result.put("phaseRule", "fixed-arity applicability is considered before varargs");
        result.put("compileOnlyAmbiguity", "with unrelated overloads pick(String) and pick(StringBuilder), pick(null) is ambiguous");
        return result;
    }

    private String pick(long value) { return "pick(long)"; }
    private String pick(Integer value) { return "pick(Integer)"; }
    private String pick(int... values) { return "pick(int...)"; }
}

package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/language-basics/array")
public class ArrayController {
    @GetMapping("/covariance-failure")
    public Map<String, Object> covarianceFailure() {
        Object[] view = new String[]{"safe"};
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("compileTimeType", "Object[]");
        result.put("runtimeType", view.getClass().getTypeName());
        try {
            view[0] = 123;
        } catch (ArrayStoreException exception) {
            result.put("failure", exception.getClass().getSimpleName());
        }
        result.put("retainedValue", view[0]);
        return result;
    }
}

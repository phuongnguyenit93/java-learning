package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/java/core/language-basics/type-system")
public class TypeSystemController {
    @GetMapping("/runtime-type")
    public Map<String, Object> inspectRuntimeType(@RequestParam(defaultValue = "sample") Object value) {
        Object declaredAsObject = value;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("compileTimeView", "Object");
        result.put("runtimeClass", declaredAsObject.getClass().getName());
        result.put("instanceofString", declaredAsObject instanceof String);
        result.put("value", declaredAsObject.toString());
        return result;
    }
}

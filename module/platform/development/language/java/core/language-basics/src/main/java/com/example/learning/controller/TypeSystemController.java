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

    @GetMapping("/conversion-contexts")
    public Map<String, Object> conversionContexts() {
        byte left = 1;
        byte right = 2;
        byte assignmentConstant = 1;
        Integer boxed = 10;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assignmentConstantType", identify(assignmentConstant));
        result.put("bytePlusByteExpressionType", identify(left + right));
        result.put("methodInvocationWidening", acceptLong(10));
        result.put("boxingResultType", boxed.getClass().getSimpleName());
        result.put("compileOnlyBoundary", "useByte(1) does not compile without an explicit cast even though byte b = 1 is valid");
        return result;
    }

    private String identify(byte value) {
        return "byte";
    }

    private String identify(int value) {
        return "int";
    }

    private String acceptLong(long value) {
        return "int argument widened to long parameter: " + value;
    }
}

package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
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

    @GetMapping("/copy-reference-elements")
    public Map<String, Object> copyReferenceElements() {
        User[] original = {new User("A")};
        User[] copied = Arrays.copyOf(original, original.length);

        boolean differentArrays = original != copied;
        boolean sameElementReference = original[0] == copied[0];

        copied[0].name = "B";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("differentArrayObjects", differentArrays);
        result.put("sameElementReference", sameElementReference);
        result.put("originalElementNameAfterCopiedMutation", original[0].name);
        result.put("conclusion", "array copy creates a new array but copies reference element values");
        return result;
    }

    private static final class User {
        private String name;

        private User(String name) {
            this.name = name;
        }
    }
}

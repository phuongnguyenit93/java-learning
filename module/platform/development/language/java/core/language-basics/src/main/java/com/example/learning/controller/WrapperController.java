package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/java/core/language-basics/wrapper")
public class WrapperController {
    @GetMapping("/identity")
    public Map<String, Object> wrapperIdentity() {
        Integer smallA = 127;
        Integer smallB = 127;
        Integer largeA = 1000;
        Integer largeB = 1000;
        return Map.of(
                "smallIdentity", smallA == smallB,
                "smallEquals", smallA.equals(smallB),
                "largeIdentity", largeA == largeB,
                "largeEquals", largeA.equals(largeB)
        );
    }
}

package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/java/core/numbers/random")
public class RandomController {

    @GetMapping("/seeded-sequence")
    public Map<String, Object> seededSequence() {
        Random first = new Random(42L);
        Random second = new Random(42L);

        List<Integer> firstSequence = List.of(
                first.nextInt(1000),
                first.nextInt(1000),
                first.nextInt(1000)
        );
        List<Integer> secondSequence = List.of(
                second.nextInt(1000),
                second.nextInt(1000),
                second.nextInt(1000)
        );

        return Map.of(
                "first", firstSequence,
                "sameSeed", secondSequence,
                "reproducible", firstSequence.equals(secondSequence),
                "securityBoundary", "use SecureRandom when unpredictability is required"
        );
    }
}

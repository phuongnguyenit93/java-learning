package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/java/core/class-object/enum")
public class EnumController {
    interface FeePolicy { int feeFor(int amount); }

    enum FeeTier implements FeePolicy {
        STANDARD { public int feeFor(int amount) { return amount / 100; } },
        PREMIUM { public int feeFor(int amount) { return 0; } }
    }

    @GetMapping("/behavior")
    public Map<String, Object> enumBehavior() {
        return Map.of(
                "standardFee", FeeTier.STANDARD.feeFor(1_000),
                "premiumFee", FeeTier.PREMIUM.feeFor(1_000),
                "identity", FeeTier.valueOf("PREMIUM") == FeeTier.PREMIUM,
                "declarationOrder", Arrays.stream(FeeTier.values()).map(Enum::name).toList());
    }
}

package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/class-object/immutability")
public class ImmutabilityController {
    static final class BankAccountSnapshot {
        private final List<String> tags;
        BankAccountSnapshot(List<String> input) { tags = List.copyOf(input); }
        List<String> tags() { return tags; }
    }

    @GetMapping("/defensive-copy")
    public Map<String, Object> defensiveCopy() {
        List<String> input = new ArrayList<>(List.of("JAVA"));
        BankAccountSnapshot snapshot = new BankAccountSnapshot(input);
        input.add("VIP");

        boolean returnedCollectionMutable;
        try {
            snapshot.tags().add("MUTATED");
            returnedCollectionMutable = true;
        } catch (UnsupportedOperationException exception) {
            returnedCollectionMutable = false;
        }

        return Map.of(
                "callerInputAfterMutation", List.copyOf(input),
                "storedSnapshot", snapshot.tags(),
                "returnedCollectionMutable", returnedCollectionMutable);
    }
}

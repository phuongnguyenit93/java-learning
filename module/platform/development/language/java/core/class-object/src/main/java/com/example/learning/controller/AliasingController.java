package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/class-object/aliasing")
public class AliasingController {
    static final class BankAccount {
        private final List<String> tags = new ArrayList<>();
        List<String> tags() { return tags; }
    }

    @GetMapping("/mutation")
    public Map<String, Object> mutateAlias() {
        BankAccount account = new BankAccount();
        BankAccount alias = account;
        alias.tags().add("VIP");
        return Map.of(
                "sameIdentity", account == alias,
                "accountTags", List.copyOf(account.tags()),
                "aliasTags", List.copyOf(alias.tags()));
    }
}

package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/class-object/copy")
public class CopyController {
    static final class BankAccount {
        final String id;
        final List<String> tags;
        BankAccount(String id, List<String> tags) { this.id = id; this.tags = tags; }
        BankAccount shallowCopy() { return new BankAccount(id, tags); }
        BankAccount deepCopy() { return new BankAccount(id, new ArrayList<>(tags)); }
    }

    @GetMapping("/shallow-vs-deep")
    public Map<String, Object> shallowVsDeep() {
        BankAccount source = new BankAccount("A-01", new ArrayList<>(List.of("JAVA")));
        BankAccount shallow = source.shallowCopy();
        BankAccount deep = source.deepCopy();
        source.tags.add("VIP");
        return Map.of(
                "sourceTags", List.copyOf(source.tags),
                "shallowTags", List.copyOf(shallow.tags),
                "deepTags", List.copyOf(deep.tags),
                "shallowSharesNested", source.tags == shallow.tags,
                "deepSharesNested", source.tags == deep.tags);
    }
}

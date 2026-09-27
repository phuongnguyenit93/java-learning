package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/java/core/localization/collation")
public class CollationController {

    @GetMapping("/sort")
    public Map<String, Object> sort() {
        List<String> input = List.of("An", "Ân", "Anh", "Ánh", "Ban");

        List<String> stringOrder = new ArrayList<>(input);
        stringOrder.sort(String::compareTo);

        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi-VN"));
        collator.setStrength(Collator.TERTIARY);
        List<String> vietnameseOrder = new ArrayList<>(input);
        vietnameseOrder.sort(collator);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("input", input);
        result.put("stringCompareToOrder", stringOrder);
        result.put("viVNCollatorOrder", vietnameseOrder);
        result.put("strength", collator.getStrength());
        result.put("lesson", "Raw String order and human linguistic order are different contracts.");
        return result;
    }
}

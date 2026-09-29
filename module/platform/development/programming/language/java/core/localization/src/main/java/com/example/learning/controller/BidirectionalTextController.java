package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/localization/bidi")
public class BidirectionalTextController {

    @GetMapping("/analyze")
    public Map<String, Object> analyze() {
        String text = "שלום Java 21";
        Bidi bidi = new Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);

        List<Map<String, Object>> runs = new ArrayList<>();
        for (int i = 0; i < bidi.getRunCount(); i++) {
            int start = bidi.getRunStart(i);
            int limit = bidi.getRunLimit(i);
            int level = bidi.getRunLevel(i);

            Map<String, Object> run = new LinkedHashMap<>();
            run.put("index", i);
            run.put("text", text.substring(start, limit));
            run.put("start", start);
            run.put("limit", limit);
            run.put("embeddingLevel", level);
            run.put("direction", level % 2 == 0 ? "LTR" : "RTL");
            runs.add(run);
        }

        char[] chars = text.toCharArray();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logicalText", text);
        result.put("baseDirection", bidi.baseIsLeftToRight() ? "LTR" : "RTL");
        result.put("mixed", bidi.isMixed());
        result.put("requiresBidi", Bidi.requiresBidi(chars, 0, chars.length));
        result.put("runs", runs);
        result.put("lesson", "Keep Unicode text in logical order; Bidi analyzes direction, while the renderer owns final visual layout.");
        return result;
    }
}

package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@RestController
@RequestMapping("/java/core/localization/segmentation")
public class TextSegmentationController {

    @GetMapping("/boundaries")
    public Map<String, Object> boundaries() {
        Locale locale = Locale.forLanguageTag("vi-VN");
        String text = "A\u0301nh sáng. Học Java tốt!";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locale", locale.toLanguageTag());
        result.put("text", text);
        result.put("characters", segments(text, l -> BreakIterator.getCharacterInstance(l), locale));
        result.put("wordsAndSeparators", segments(text, l -> BreakIterator.getWordInstance(l), locale));
        result.put("sentences", segments(text, l -> BreakIterator.getSentenceInstance(l), locale));
        result.put("lineBreakSegments", segments(text, l -> BreakIterator.getLineInstance(l), locale));
        result.put("lesson", "BreakIterator exposes boundary positions; callers still decide how each segment is used.");
        return result;
    }

    private List<String> segments(
            String text,
            Function<Locale, BreakIterator> factory,
            Locale locale
    ) {
        BreakIterator iterator = factory.apply(locale);
        iterator.setText(text);

        List<String> segments = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            segments.add(text.substring(start, end));
        }
        return segments;
    }
}

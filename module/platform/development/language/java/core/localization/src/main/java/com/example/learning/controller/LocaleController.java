package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/java/core/localization/locale")
public class LocaleController {

    @GetMapping("/compare")
    public Map<String, Object> compareLocales() {
        List<Locale> locales = List.of(
                Locale.forLanguageTag("vi-VN"),
                Locale.forLanguageTag("en-US"),
                Locale.forLanguageTag("zh-Hant-TW")
        );

        List<Map<String, Object>> details = new ArrayList<>();
        for (Locale locale : locales) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tag", locale.toLanguageTag());
            item.put("language", locale.getLanguage());
            item.put("script", locale.getScript());
            item.put("region", locale.getCountry());
            item.put("displayNameInEnglish", locale.getDisplayName(Locale.ENGLISH));
            details.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locales", details);
        result.put("defaultLocale", Locale.getDefault().toLanguageTag());
        result.put("defaultDisplayLocale", Locale.getDefault(Locale.Category.DISPLAY).toLanguageTag());
        result.put("defaultFormatLocale", Locale.getDefault(Locale.Category.FORMAT).toLanguageTag());
        result.put("lesson", "Locale is language/cultural context; it is not a time zone, currency, or physical location.");
        return result;
    }
}

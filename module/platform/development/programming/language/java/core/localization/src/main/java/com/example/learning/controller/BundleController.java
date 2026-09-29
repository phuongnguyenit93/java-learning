package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

@RestController
@RequestMapping("/java/core/localization/bundle")
public class BundleController {

    private static final String BASE_NAME = "i18n.messages";
    private static final ResourceBundle.Control NO_DEFAULT_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    @GetMapping("/resolve")
    public Map<String, Object> resolveBundle() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vi-VN", resolve(Locale.forLanguageTag("vi-VN")));
        result.put("en-GB", resolve(Locale.forLanguageTag("en-GB")));
        result.put("fr-FR", resolve(Locale.forLanguageTag("fr-FR")));
        result.put("lesson", "ResourceBundle resolves prepared resources through candidate locales; it does not translate text automatically.");
        return result;
    }

    private Map<String, Object> resolve(Locale requested) {
        ResourceBundle bundle = ResourceBundle.getBundle(BASE_NAME, requested, NO_DEFAULT_FALLBACK);

        List<String> candidates = new ArrayList<>();
        for (Locale candidate : NO_DEFAULT_FALLBACK.getCandidateLocales(BASE_NAME, requested)) {
            candidates.add(candidate.equals(Locale.ROOT) ? "ROOT" : candidate.toLanguageTag());
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("requested", requested.toLanguageTag());
        item.put("candidates", candidates);
        item.put("resolvedBundleLocale", bundle.getLocale().equals(Locale.ROOT) ? "ROOT" : bundle.getLocale().toLanguageTag());
        item.put("source", bundle.getString("source"));
        item.put("greeting", bundle.getString("greeting"));
        return item;
    }
}

package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.MessageFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

@RestController
@RequestMapping("/java/core/localization/message")
public class MessageController {

    private static final String BASE_NAME = "i18n.messages";

    @GetMapping("/format")
    public Map<String, Object> format() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vi-VN", format(Locale.forLanguageTag("vi-VN")));
        result.put("en-US", format(Locale.US));
        result.put("lesson", "ResourceBundle owns the translated pattern; MessageFormat inserts arguments using the same Locale context.");
        return result;
    }

    private Map<String, Object> format(Locale locale) {
        ResourceBundle bundle = ResourceBundle.getBundle(BASE_NAME, locale);
        String pattern = bundle.getString("order.created");
        MessageFormat formatter = new MessageFormat(pattern, locale);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("locale", locale.toLanguageTag());
        item.put("pattern", pattern);
        item.put("arguments", new Object[]{1001L, "An"});
        item.put("message", formatter.format(new Object[]{1001L, "An"}));
        return item;
    }
}

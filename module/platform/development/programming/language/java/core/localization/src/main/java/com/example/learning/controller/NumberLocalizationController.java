package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/java/core/localization/number")
public class NumberLocalizationController {

    @GetMapping("/format-and-parse")
    public Map<String, Object> formatAndParse() {
        BigDecimal value = new BigDecimal("1234567.89");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canonicalValue", value.toPlainString());
        result.put("vi-VN", formatAndParse(value, Locale.forLanguageTag("vi-VN")));
        result.put("en-US", formatAndParse(value, Locale.US));
        result.put("lesson", "The numeric value stays stable while its human-readable representation changes with Locale.");
        return result;
    }

    @GetMapping("/compact")
    public Map<String, Object> compact() {
        long value = 1_200_000L;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canonicalValue", value);
        result.put("en-US", compact(value, Locale.US));
        result.put("vi-VN", compact(value, Locale.forLanguageTag("vi-VN")));
        result.put("lesson", "Compact suffixes/patterns are locale data; applications should not hard-code K/M/B rules.");
        return result;
    }

    private Map<String, Object> formatAndParse(BigDecimal value, Locale locale) {
        DecimalFormat format = (DecimalFormat) NumberFormat.getNumberInstance(locale);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        format.setParseBigDecimal(true);

        String text = format.format(value);
        ParsePosition position = new ParsePosition(0);
        Number parsed = format.parse(text, position);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("locale", locale.toLanguageTag());
        item.put("formatted", text);
        item.put("parsedValue", parsed.toString());
        item.put("fullyConsumed", position.getIndex() == text.length() && position.getErrorIndex() < 0);
        item.put("decimalSeparator", String.valueOf(format.getDecimalFormatSymbols().getDecimalSeparator()));
        item.put("groupingSeparator", String.valueOf(format.getDecimalFormatSymbols().getGroupingSeparator()));
        return item;
    }

    private Map<String, Object> compact(long value, Locale locale) {
        NumberFormat shortFormat = NumberFormat.getCompactNumberInstance(locale, NumberFormat.Style.SHORT);
        NumberFormat longFormat = NumberFormat.getCompactNumberInstance(locale, NumberFormat.Style.LONG);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("locale", locale.toLanguageTag());
        item.put("short", shortFormat.format(value));
        item.put("long", longFormat.format(value));
        return item;
    }
}

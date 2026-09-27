package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DecimalStyle;
import java.time.format.FormatStyle;
import java.time.temporal.WeekFields;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/java/core/localization/date-time")
public class DateTimeLocalizationController {

    @GetMapping("/format")
    public Map<String, Object> format() {
        Instant instant = Instant.parse("2026-09-27T08:30:00Z");
        ZoneId hcm = ZoneId.of("Asia/Ho_Chi_Minh");
        ZoneId newYork = ZoneId.of("America/New_York");
        Locale viVN = Locale.forLanguageTag("vi-VN");
        Locale enUS = Locale.US;

        DateTimeFormatter vi = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(viVN);
        DateTimeFormatter us = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(enUS);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instant", instant.toString());
        result.put("hcmWithViVN", vi.format(instant.atZone(hcm)));
        result.put("newYorkWithViVN", vi.format(instant.atZone(newYork)));
        result.put("hcmWithEnUS", us.format(instant.atZone(hcm)));
        result.put("newYorkWithEnUS", us.format(instant.atZone(newYork)));
        result.put("lesson", "ZoneId chooses the local time; Locale chooses how that local time is presented.");
        return result;
    }

    @GetMapping("/week-and-numbering")
    public Map<String, Object> weekAndNumberingConventions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("en-US", convention(Locale.US));
        result.put("vi-VN", convention(Locale.forLanguageTag("vi-VN")));

        Locale extended = Locale.forLanguageTag("th-TH-u-ca-buddhist-nu-thai");
        DateTimeFormatter localized = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).localizedBy(extended);
        Map<String, Object> extension = new LinkedHashMap<>();
        extension.put("tag", extended.toLanguageTag());
        extension.put("chronology", localized.getChronology().getId());
        extension.put("zeroDigit", String.valueOf(localized.getDecimalStyle().getZeroDigit()));
        extension.put("sample", localized.format(LocalDate.of(2026, 9, 27)));
        result.put("unicodeLocaleExtensionExample", extension);
        result.put("lesson", "Locale-sensitive calendar conventions are presentation context; explicit business calendar rules remain separate.");
        return result;
    }

    private Map<String, Object> convention(Locale locale) {
        WeekFields weeks = WeekFields.of(locale);
        DecimalStyle decimal = DecimalStyle.of(locale);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("locale", locale.toLanguageTag());
        item.put("firstDayOfWeek", weeks.getFirstDayOfWeek().name());
        item.put("minimalDaysInFirstWeek", weeks.getMinimalDaysInFirstWeek());
        item.put("zeroDigit", String.valueOf(decimal.getZeroDigit()));
        item.put("decimalSeparator", String.valueOf(decimal.getDecimalSeparator()));
        return item;
    }
}

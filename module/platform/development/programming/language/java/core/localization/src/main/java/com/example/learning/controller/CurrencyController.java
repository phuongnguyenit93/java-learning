package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/java/core/localization/currency")
public class CurrencyController {

    @GetMapping("/currency-vs-locale")
    public Map<String, Object> currencyVsLocale() {
        Locale viVN = Locale.forLanguageTag("vi-VN");
        Locale enUS = Locale.US;
        Currency vnd = Currency.getInstance(viVN);
        Currency usd = Currency.getInstance(enUS);
        BigDecimal amount = new BigDecimal("1234.50");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currencyFromViVN", describe(vnd, viVN));
        result.put("currencyFromEnUS", describe(usd, enUS));
        result.put("usdDisplayedForViVN", format(amount, usd, viVN));
        result.put("vndDisplayedForEnUS", format(amount, vnd, enUS));
        result.put("lesson", "Locale controls presentation conventions; the transaction currency is a separate domain value.");
        return result;
    }

    private Map<String, Object> describe(Currency currency, Locale displayLocale) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", currency.getCurrencyCode());
        item.put("numericCode", currency.getNumericCode());
        item.put("symbol", currency.getSymbol(displayLocale));
        item.put("displayName", currency.getDisplayName(displayLocale));
        item.put("defaultFractionDigits", currency.getDefaultFractionDigits());
        return item;
    }

    private String format(BigDecimal amount, Currency currency, Locale locale) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(currency);
        int fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits >= 0) {
            format.setMinimumFractionDigits(fractionDigits);
            format.setMaximumFractionDigits(fractionDigits);
        }
        return format.format(amount);
    }
}

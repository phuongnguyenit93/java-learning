package com.example.learning;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class DateUtils {
    public static boolean isValidDate (String pattern, String dateStr) {
        DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(pattern);

        try {
            LocalDate.parse(dateStr, DATE_FORMATTER);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static int getDateRange (String stringDateFrom,String stringDateTo) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate dateFrom = LocalDate.parse(stringDateFrom, formatter);
        LocalDate dateTo = LocalDate.parse(stringDateTo, formatter);

        if (dateTo.isBefore(dateFrom)) {
            throw new Error("toDt must after frDt");
        } else {
            int dateRange = (int) ChronoUnit.DAYS.between(dateFrom, dateTo);
            return dateRange;
        }
    }
}

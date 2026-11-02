package com.example.learning.casestudy.domain.group3.case6;

import java.time.LocalDate;
import java.util.Objects;

public record DateRange(LocalDate start, LocalDate end) {
    public DateRange {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (end.isBefore(start))throw new IllegalArgumentException();
    }
}

package com.example.learning.oop.language_mechanics.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class NumericTransfers {
    private NumericTransfers() {}

    /** PECS: producer extends, consumer super; preserves the element's type relationship. */
    public static <T> void copy(List<? extends T> source, List<? super T> destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        // Snapshot makes copying to the same list finite and predictable.
        for (T value : List.copyOf(source)) {
            destination.add(Objects.requireNonNull(value, "numeric value"));
        }
    }

    /** Bounded type parameter uses the Comparable contract at compile time. */
    public static <T extends Comparable<? super T>> T greatest(List<? extends T> values) {
        if (values == null || values.isEmpty()) {
            throw new NoSuchElementException("Cannot choose maximum from empty values");
        }
        T max = Objects.requireNonNull(values.get(0), "first value");
        for (T value : values) {
            if (Objects.requireNonNull(value, "value").compareTo(max) > 0) {
                max = value;
            }
        }
        return max;
    }

    public static BigDecimal sum(List<? extends Number> values) {
        Objects.requireNonNull(values, "values");
        BigDecimal total = BigDecimal.ZERO;
        for (Number value : values) {
            Objects.requireNonNull(value, "number");
            if (value instanceof Double d && !Double.isFinite(d)) {
                throw new IllegalArgumentException("Only finite numbers are allowed");
            }
            total = total.add(new BigDecimal(value.toString()));
        }
        return total;
    }
}

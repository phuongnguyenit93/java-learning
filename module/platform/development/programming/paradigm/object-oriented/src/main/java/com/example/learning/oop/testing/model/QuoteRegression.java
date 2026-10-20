package com.example.learning.oop.testing.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Three implementations share one public contract but may make different lookups. */
public final class QuoteRegression {
    private QuoteRegression() {}

    public static final long FREE_SHIPPING_FROM = 100_000;
    public static final long SHIPPING_FEE = 15_000;

    public record Line(String sku, int quantity) {
        public Line {
            if (sku == null || !sku.matches("[A-Za-z0-9-]{2,32}")) {
                throw new IllegalArgumentException("sku must contain 2..32 letters/digits/hyphens");
            }
            if (quantity < 1 || quantity > 10_000) {
                throw new IllegalArgumentException("quantity must be 1..10000");
            }
        }
    }

    public record Invoice(long subtotalVnd, long discountVnd, long shippingVnd, long totalVnd) {}

    public interface Catalog {
        long unitPriceVnd(String sku);
    }

    public interface QuoteEngine {
        Invoice quote(List<Line> lines, boolean member, Catalog catalog);
    }

    public static final class PerLineQuote implements QuoteEngine {
        @Override
        public Invoice quote(List<Line> lines, boolean member, Catalog catalog) {
            long subtotal = 0;
            for (Line line : lines) {
                subtotal = Math.addExact(subtotal,
                        Math.multiplyExact((long) line.quantity(), catalog.unitPriceVnd(line.sku())));
            }
            return invoice(subtotal, member, false);
        }
    }

    public static final class GroupedQuote implements QuoteEngine {
        @Override
        public Invoice quote(List<Line> lines, boolean member, Catalog catalog) {
            Map<String, Long> grouped = new LinkedHashMap<>();
            for (Line line : lines) {
                grouped.merge(line.sku(), (long) line.quantity(), Math::addExact);
            }
            long subtotal = 0;
            for (var entry : grouped.entrySet()) {
                subtotal = Math.addExact(subtotal,
                        Math.multiplyExact(entry.getValue(), catalog.unitPriceVnd(entry.getKey())));
            }
            return invoice(subtotal, member, false);
        }
    }

    /** Intentionally wrong at the exact free-shipping threshold. */
    public static final class BrokenShippingQuote implements QuoteEngine {
        private final QuoteEngine delegate = new GroupedQuote();

        @Override
        public Invoice quote(List<Line> lines, boolean member, Catalog catalog) {
            Invoice correct = delegate.quote(lines, member, catalog);
            return invoice(correct.subtotalVnd(), member, true);
        }
    }

    private static Invoice invoice(long subtotal, boolean member, boolean broken) {
        long discount = member
                ? Math.addExact((subtotal / 100) * 5, (subtotal % 100) * 5 / 100) : 0L;
        long shipping = (broken ? subtotal > FREE_SHIPPING_FROM : subtotal >= FREE_SHIPPING_FROM)
                ? 0 : SHIPPING_FEE;
        return new Invoice(subtotal, discount, shipping,
                Math.addExact(subtotal - discount, shipping));
    }

    public static final class RecordingCatalog implements Catalog {
        private final Map<String, Long> prices;
        private final List<String> lookups = new ArrayList<>();

        public RecordingCatalog(Map<String, Long> prices) {
            this.prices = Map.copyOf(Objects.requireNonNull(prices));
        }

        @Override
        public long unitPriceVnd(String sku) {
            lookups.add(sku);
            Long amount = prices.get(sku);
            if (amount == null) {
                throw new IllegalArgumentException("unknown SKU: " + sku);
            }
            return amount;
        }

        public int lookups() {
            return lookups.size();
        }
    }
}

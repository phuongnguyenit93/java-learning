package com.example.learning.oop.design_patterns.model;

import java.util.ArrayList;
import java.util.List;

/** Factory Method selects the product; Template Method fixes validation/row/summary order. */
public final class ReportPatterns {
    private ReportPatterns() {}

    public record Sale(String id, long amountVnd) {
        public Sale {
            if (id == null || !id.matches("[A-Z0-9-]{2,24}")) {
                throw new IllegalArgumentException("sale id must be 2..24 uppercase letters/digits/hyphens");
            }
            if (amountVnd < 0 || amountVnd > 1_000_000_000_000L) {
                throw new IllegalArgumentException("amountVnd must be 0..1000000000000");
            }
        }
    }

    public record Generated(String content, long totalVnd, int count, List<String> executionTrace) {}

    public abstract static class ReportTemplate {
        public final Generated render(List<Sale> sales) {
            if (sales == null || sales.isEmpty() || sales.size() > 20 ||
                    sales.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("report requires 1..20 non-null sales");
            }
            long total = 0;
            List<String> execution = new ArrayList<>();
            StringBuilder output = new StringBuilder();
            execution.add("HEADER");
            output.append(header()).append("\n");
            for (Sale sale : sales) {
                total = Math.addExact(total, sale.amountVnd());
                execution.add("ROW:" + sale.id());
                output.append(row(sale)).append("\n");
            }
            execution.add("SUMMARY");
            output.append(summary(sales.size(), total));
            return new Generated(output.toString(), total, sales.size(), List.copyOf(execution));
        }
        protected abstract String header();
        protected abstract String row(Sale sale);
        protected abstract String summary(int count, long total);
    }

    public static final class TextTemplate extends ReportTemplate {
        @Override protected String header() { return "SALES REPORT"; }
        @Override protected String row(Sale sale) {
            return sale.id() + " -> " + sale.amountVnd() + " VND";
        }
        @Override protected String summary(int count, long total) {
            return "TOTAL (" + count + "): " + total + " VND";
        }
    }

    public static final class CsvTemplate extends ReportTemplate {
        @Override protected String header() { return "saleId,amountVnd"; }
        @Override protected String row(Sale sale) {
            return sale.id() + "," + sale.amountVnd();
        }
        @Override protected String summary(int count, long total) {
            return "TOTAL," + total;
        }
    }

    public abstract static class ReportCreator {
        protected abstract ReportTemplate createTemplate(); // GoF Factory Method
        public final Generated create(List<Sale> sales) {
            return createTemplate().render(sales);
        }
    }

    public static final class TextCreator extends ReportCreator {
        @Override protected ReportTemplate createTemplate() { return new TextTemplate(); }
    }

    public static final class CsvCreator extends ReportCreator {
        @Override protected ReportTemplate createTemplate() { return new CsvTemplate(); }
    }
}

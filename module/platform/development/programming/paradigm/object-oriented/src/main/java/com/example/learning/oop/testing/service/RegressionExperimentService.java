package com.example.learning.oop.testing.service;

import com.example.learning.oop.testing.dto.TestingDtos.*;
import com.example.learning.oop.testing.model.QuoteRegression;
import com.example.learning.oop.testing.model.QuoteRegression.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Differential testing of public invoices while internal query counts can change. */
@Service
public final class RegressionExperimentService {
    public RegressionResult run(RegressionRequest request) {
        if (request == null || request.member() == null || request.pricesVnd() == null ||
                request.lines() == null || request.lines().isEmpty() || request.lines().size() > 20 ||
                request.pricesVnd().isEmpty() || request.pricesVnd().size() > 20) {
            throw new IllegalArgumentException("require member, 1..20 prices and 1..20 lines");
        }
        for (var entry : request.pricesVnd().entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[A-Za-z0-9-]{2,32}") ||
                    entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("catalog SKUs must be 2..32 characters and prices nonnegative");
            }
        }
        List<Line> lines = new ArrayList<>();
        for (PriceLine line : request.lines()) {
            if (line == null || line.quantity() == null) {
                throw new IllegalArgumentException("every line must contain sku and quantity");
            }
            Line validLine = new Line(line.sku(), line.quantity());
            if (!request.pricesVnd().containsKey(validLine.sku())) {
                throw new IllegalArgumentException("unknown SKU: " + validLine.sku());
            }
            lines.add(validLine);
        }
        Map<String, Long> prices = Map.copyOf(request.pricesVnd());
        List<Line> input = List.copyOf(lines);
        Invoice baseline = runQuote(new PerLineQuote(), prices, input, request.member()).invoice();
        List<RegressionVariant> variants = List.of(
                runVariant("PER_LINE_BASELINE", new PerLineQuote(), prices, input, request.member(), baseline),
                runVariant("GROUPED_REFACTOR", new GroupedQuote(), prices, input, request.member(), baseline),
                runVariant("BROKEN_SHIPPING_BOUNDARY", new BrokenShippingQuote(), prices, input, request.member(), baseline));
        return new RegressionResult(baseline, variants,
                variants.get(1).matchesBaseline(), !variants.get(2).matchesBaseline());
    }

    private record QuoteRun(Invoice invoice, int lookups) {}

    private static QuoteRun runQuote(QuoteEngine engine, Map<String, Long> prices,
                                    List<Line> lines, boolean member) {
        var catalog = new RecordingCatalog(prices);
        Invoice result = engine.quote(lines, member, catalog);
        return new QuoteRun(result, catalog.lookups());
    }

    private static RegressionVariant runVariant(String label, QuoteEngine engine,
                                                 Map<String, Long> prices, List<Line> lines,
                                                 boolean member, Invoice baseline) {
        QuoteRun actual = runQuote(engine, prices, lines, member);
        return new RegressionVariant(label, actual.invoice(), actual.lookups(),
                baseline.equals(actual.invoice()));
    }
}

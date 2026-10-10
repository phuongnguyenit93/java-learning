package com.example.learning.dop;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Transforms generic order values, without keeping state or performing payment or persistence.
 */
@Service
public class OrderPreviewService {

    private final OrderShapeSchema schema;

    public OrderPreviewService(OrderShapeSchema schema) {
        this.schema = schema;
    }

    public record Outcome(boolean accepted, Map<String, Object> body) {}

    public Outcome preview(Map<String, Object> input, int discountPercent) {
        List<OrderShapeSchema.Issue> problems = new ArrayList<>(schema.validate(input));
        if (discountPercent < 0 || discountPercent > 100) {
            problems.add(new OrderShapeSchema.Issue(
                    "discountPercent", "integer from 0 to 100", "outside range"
            ));
        }
        if (!problems.isEmpty()) {
            return new Outcome(false, Map.of(
                    "accepted", false,
                    "stage", "boundaryValidation",
                    "schema", schema.describe(),
                    "errors", problems,
                    "explanation", "Invalid generic data is rejected before transformation"
            ));
        }

        // Make independent, deeply non-mutable snapshots for the accepted shape.
        List<Map<String, Object>> lines = new ArrayList<>();
        long subtotal = 0;
        for (Object value : (List<?>) input.get("lines")) {
            Map<?, ?> line = (Map<?, ?>) value;
            String sku = (String) line.get("sku");
            long qty = ((Number) line.get("qty")).longValue();
            long price = ((Number) line.get("price")).longValue();
            lines.add(Map.of("sku", sku, "qty", qty, "price", price));
            subtotal = Math.addExact(subtotal, Math.multiplyExact(qty, price));
        }
        Map<String, Object> before = Map.of(
                "id", input.get("id"),
                "status", input.get("status"),
                "lines", List.copyOf(lines)
        );
        BigDecimal discounted = BigDecimal.valueOf(subtotal)
                .multiply(BigDecimal.valueOf(100L - discountPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("subtotal", subtotal);
        after.put("discountPercent", discountPercent);
        after.put("discountedTotal", discounted);

        return new Outcome(true, Map.of(
                "accepted", true,
                "schema", schema.describe(),
                "before", before,
                "after", Map.copyOf(after),
                "observations", Map.of(
                        "sumOfQuantityTimesPrice", subtotal,
                        "discountedTotal", discounted,
                        "inputSnapshotRetained", true,
                        "newValueDerivedWithoutModifyingTheSource", true,
                        "externalPaymentOrPersistencePerformed", false
                ),
                "trace", List.of(
                        "External JSON map validated by an independent shape contract",
                        "Reusable total operation reads a generic nested list",
                        "A new result value is derived; the original snapshot remains unchanged",
                        "No shared state, payment, or persistence is performed"
                )
        ));
    }
}

package com.example.learning.dop;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A deliberately small, independent shape contract for the learning experiment.
 * It validates generic data at the HTTP trust boundary, without giving the order map methods.
 */
@Component
public class OrderShapeSchema {

    private static final Set<String> ORDER_KEYS = Set.of("id", "status", "lines");
    private static final Set<String> LINE_KEYS = Set.of("sku", "qty", "price");
    private static final int MAX_TEXT_LENGTH = 64;
    private static final int MAX_ERROR_VALUE_LENGTH = 80;

    public record Issue(String path, String expected, String actual) {}

    public Map<String, Object> describe() {
        return Map.of(
                "representation", "generic JSON object (map of values and nested lists)",
                "id", "nonblank string, at most 64 characters",
                "status", "pending",
                "lines", "list with 1..20 entries; each entry has sku, qty, price",
                "sku", "nonblank string, at most 64 characters",
                "qty", "integer from 1 to 10000",
                "price", "integer from 0 to 1000000 (example units)",
                "extraFields", "rejected at this external trust boundary"
        );
    }

    public List<Issue> validate(Map<String, Object> input) {
        List<Issue> issues = new ArrayList<>();
        rejectUnknown(input, ORDER_KEYS, "", issues);
        requireString(input.get("id"), "id", issues);
        if (!"pending".equals(input.get("status"))) {
            Object status = input.get("status");
            issues.add(new Issue("status", "literal pending",
                    status instanceof String s ? "string value: " + bounded(s) : typeOf(status)));
        }
        Object linesValue = input.get("lines");
        if (!(linesValue instanceof List<?> lines)) {
            issues.add(new Issue("lines", "array of 1..20 objects", typeOf(linesValue)));
            return issues;
        }
        if (lines.isEmpty() || lines.size() > 20) {
            issues.add(new Issue("lines", "array length 1..20", "length " + lines.size()));
            if (lines.size() > 20) {
                return issues; // Keep the classroom experiment bounded on oversized input.
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String prefix = "lines[" + i + "]";
            Object entry = lines.get(i);
            if (!(entry instanceof Map<?, ?> raw)) {
                issues.add(new Issue(prefix, "object with sku, qty and price", typeOf(entry)));
                continue;
            }
            Map<String, Object> line = stringKeyMap(raw, prefix, issues);
            rejectUnknown(line, LINE_KEYS, prefix + ".", issues);
            requireString(line.get("sku"), prefix + ".sku", issues);
            requireInteger(line.get("qty"), prefix + ".qty", 1, 10_000, issues);
            requireInteger(line.get("price"), prefix + ".price", 0, 1_000_000, issues);
        }
        return issues;
    }

    private static Map<String, Object> stringKeyMap(
            Map<?, ?> raw, String prefix, List<Issue> issues
    ) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            } else {
                issues.add(new Issue(prefix, "string keys", "non-string key"));
            }
        }
        return result;
    }

    private static void rejectUnknown(
            Map<String, Object> data, Set<String> keys, String prefix, List<Issue> issues
    ) {
        for (String key : data.keySet()) {
            if (!keys.contains(key)) {
                issues.add(new Issue(prefix + bounded(key), "declared field name", "unexpected field"));
            }
        }
    }

    private static void requireString(Object value, String path, List<Issue> issues) {
        if (!(value instanceof String s) || s.isBlank() || s.length() > MAX_TEXT_LENGTH) {
            issues.add(new Issue(path, "nonblank string of 1..64 characters", typeOf(value)));
        }
    }

    private static void requireInteger(
            Object value, String path, long min, long max, List<Issue> issues
    ) {
        if (!(value instanceof Integer || value instanceof Long)
                || ((Number) value).longValue() < min
                || ((Number) value).longValue() > max) {
            issues.add(new Issue(path, "integer from " + min + " to " + max, typeOf(value)));
        }
    }

    private static String typeOf(Object value) {
        if (value == null) return "missing/null";
        if (value instanceof String s) return "string length " + s.length();
        if (value instanceof List<?>) return "array";
        if (value instanceof Map<?, ?>) return "object";
        if (value instanceof Number) return "number: " + bounded(value.toString());
        if (value instanceof Boolean) return "boolean";
        return value.getClass().getSimpleName();
    }

    private static String bounded(String value) {
        if (value.length() <= MAX_ERROR_VALUE_LENGTH) return value;
        return value.substring(0, MAX_ERROR_VALUE_LENGTH) + "... (length " + value.length() + ")";
    }
}

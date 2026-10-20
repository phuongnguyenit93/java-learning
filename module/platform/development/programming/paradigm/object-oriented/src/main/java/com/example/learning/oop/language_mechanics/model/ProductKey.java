package com.example.learning.oop.language_mechanics.model;

/** Immutable value key with value-based equality/hash from record components. */
public record ProductKey(String tenantId, String sku) {
    public ProductKey {
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9_-]{1,32}")
                || sku == null || !sku.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("tenantId and sku must be 1..32 safe characters");
        }
    }
}

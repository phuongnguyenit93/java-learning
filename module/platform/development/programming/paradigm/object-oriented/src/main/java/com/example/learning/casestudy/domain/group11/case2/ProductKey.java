package com.example.learning.casestudy.domain.group11.case2;

import java.util.Objects;

public final class ProductKey {
    private final String category, sku;
    public ProductKey(String category, String sku) {
        this.category = Objects.requireNonNull(category);
        this.sku = Objects.requireNonNull(sku);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ProductKey other)) return false;
        return category.equals(other.category) && sku.equals(other.sku);
    }
    @Override
    public int hashCode() {
        return Objects.hash(category, sku);
    }
}

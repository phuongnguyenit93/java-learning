package com.example.learning.oop.language_mechanics.repository;

import com.example.learning.oop.language_mechanics.model.ProductKey;
import com.example.learning.oop.language_mechanics.model.StockItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Request-local registry looks up new equivalent ProductKey instances using equals/hashCode. */
public final class StockRegistry {
    private final Map<ProductKey, StockItem> items = new LinkedHashMap<>();

    public boolean register(ProductKey key, StockItem item) {
        Objects.requireNonNull(item, "item");
        return items.putIfAbsent(key, item) == null;
    }

    public StockItem find(ProductKey key) {
        return items.get(key);
    }

    public int size() {
        return items.size();
    }
}

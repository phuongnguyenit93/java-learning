package com.example.learning.oop.language_mechanics.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** An ordinary class parameterized independently by key and value types. */
public final class TypedCache<K, V> {
    private final Map<K, V> values = new LinkedHashMap<>();

    public void add(K key, V value) {
        Objects.requireNonNull(key, "cache key");
        Objects.requireNonNull(value, "cache value");
        if (values.putIfAbsent(key, value) != null) {
            throw new IllegalArgumentException("Duplicate cache key");
        }
    }

    public Optional<V> lookup(K key) {
        Objects.requireNonNull(key, "lookup key");
        return Optional.ofNullable(values.get(key));
    }

    public int size() {
        return values.size();
    }
}

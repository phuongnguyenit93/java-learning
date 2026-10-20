package com.example.learning.oop.relationships.model;

import java.util.List;

/** Shared validation of bounded, request-local relationship experiments. */
public final class RelationshipInputs {
    private RelationshipInputs() {}

    public static String nonBlank(String field, String input) {
        if (input == null || input.isBlank() || input.length() > 80) {
            throw new IllegalArgumentException(field + " must have 1..80 nonblank characters");
        }
        return input.trim();
    }

    public static <T> List<T> bounded(String field, List<T> input, int max, boolean allowEmpty) {
        if (input == null || input.size() > max || (!allowEmpty && input.isEmpty())
                || input.stream().anyMatch(x -> x == null)) {
            throw new IllegalArgumentException(field + " must contain "
                    + (allowEmpty ? "0" : "1") + ".." + max + " non-null elements");
        }
        return List.copyOf(input);
    }
}

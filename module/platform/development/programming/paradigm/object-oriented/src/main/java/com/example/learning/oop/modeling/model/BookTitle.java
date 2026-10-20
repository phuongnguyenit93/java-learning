package com.example.learning.oop.modeling.model;

/** A title is not a physical copy: multiple copies may point to the same title. */
public record BookTitle(String code, String name) {
    public BookTitle {
        if (code == null || !code.matches("[A-Z0-9-]{2,24}")) {
            throw new IllegalArgumentException("title code must be 2..24 uppercase letters/digits/hyphens");
        }
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("title name must have 1..100 characters");
        }
        name = name.strip();
    }
}

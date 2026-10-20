package com.example.learning.oop.class_design.model;

public record BookCopy(String copyId, String title) {
    public BookCopy {
        if (copyId == null || copyId.isBlank() || title == null || title.isBlank()) {
            throw new IllegalArgumentException("Book copy id and title must be nonblank");
        }
    }
}

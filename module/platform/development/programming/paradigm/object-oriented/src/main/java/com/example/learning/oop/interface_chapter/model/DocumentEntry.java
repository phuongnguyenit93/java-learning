package com.example.learning.oop.interface_chapter.model;

/** Immutable public representation: callers cannot modify repository internals. */
public record DocumentEntry(String id, String title, String content, Status status) {
    public enum Status { DRAFT, PUBLISHED }

    public DocumentEntry {
        if (id == null || !id.matches("[A-Z0-9-]{2,24}")) {
            throw new IllegalArgumentException("document id must be 2..24 uppercase characters, digits or hyphens");
        }
        if (title == null || title.isBlank() || title.length() > 100) {
            throw new IllegalArgumentException("title must contain 1..100 characters");
        }
        if (content == null || content.isBlank() || content.length() > 2000) {
            throw new IllegalArgumentException("content must contain 1..2000 characters");
        }
        if (status == null) throw new IllegalArgumentException("status is required");
        title = title.strip();
    }
}

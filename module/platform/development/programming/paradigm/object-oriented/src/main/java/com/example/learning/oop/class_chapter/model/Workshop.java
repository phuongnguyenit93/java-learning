package com.example.learning.oop.class_chapter.model;

/** Separate instance counters, with a single class-level immutable capacity rule. */
public final class Workshop {
    public static final int MAX_CAPACITY = 50;

    private final String title;
    private final int capacity;
    private int registered;

    public Workshop(String title, int capacity) {
        if (title == null || title.isBlank() || title.length() > 80) {
            throw new IllegalArgumentException("title must contain 1-80 characters");
        }
        if (capacity <= 0 || capacity > MAX_CAPACITY) {
            throw new IllegalArgumentException("capacity must be between 1 and " + MAX_CAPACITY);
        }
        this.title = title.strip();
        this.capacity = capacity;
    }

    public boolean register() {
        if (registered == capacity) {
            return false;
        }
        registered++;
        return true;
    }

    public String title() { return title; }
    public int capacity() { return capacity; }
    public int registered() { return registered; }
    public int remainingSeats() { return capacity - registered; }
}

package com.example.learning.oop.modeling.model;

/** Each physical copy can belong to one active loan at a time. */
public final class BookCopy {
    private final String id;
    private final BookTitle title;
    private boolean borrowed;

    public BookCopy(String id, BookTitle title) {
        if (id == null || !id.matches("[A-Z0-9-]{2,24}")) {
            throw new IllegalArgumentException("copy id must be 2..24 uppercase letters/digits/hyphens");
        }
        if (title == null) throw new IllegalArgumentException("copy requires a known title");
        this.id = id;
        this.title = title;
    }

    public String id() { return id; }
    public BookTitle title() { return title; }
    public boolean borrowed() { return borrowed; }

    public void ensureAvailable() {
        if (borrowed) throw new ModelRuleViolation("BR-03", "physical copy already borrowed");
    }

    public void markBorrowed() {
        ensureAvailable();
        borrowed = true;
    }

    public void markReturned() {
        if (!borrowed) throw new IllegalStateException("copy must be borrowed before return");
        borrowed = false;
    }
}

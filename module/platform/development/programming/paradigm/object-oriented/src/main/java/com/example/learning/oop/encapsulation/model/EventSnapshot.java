package com.example.learning.oop.encapsulation.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Demonstrates an immutable snapshot of legacy mutable Date and mutable attendee objects. */
public final class EventSnapshot {
    private final Date startsAt;
    private final List<String> attendeeNames;

    public EventSnapshot(Date startsAt, List<AttendeeDraft> source) {
        if (startsAt == null || source == null || source.isEmpty() || source.size() > 20) {
            throw new IllegalArgumentException("date and 1..20 attendees required");
        }
        this.startsAt = new Date(startsAt.getTime()); // input defensive copy
        List<String> names = new ArrayList<>();
        for (AttendeeDraft attendee : source) {
            if (attendee == null) throw new IllegalArgumentException("attendee is required");
            names.add(attendee.name()); // deep value snapshot, not List<AttendeeDraft>
        }
        attendeeNames = List.copyOf(names);
    }

    public Date startsAt() {
        return new Date(startsAt.getTime()); // output defensive copy
    }

    public List<String> attendeeNames() {
        return attendeeNames; // immutable values in an unmodifiable list
    }

    public static final class AttendeeDraft {
        private String name;

        public AttendeeDraft(String name) { rename(name); }
        public String name() { return name; }
        public void rename(String next) {
            if (next == null || next.isBlank() || next.length() > 80) {
                throw new IllegalArgumentException("attendee name must contain 1..80 characters");
            }
            name = next.strip();
        }
    }
}

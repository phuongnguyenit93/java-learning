package com.example.learning.oop.lifecycle.model;

import java.util.List;

/** Fake external handle, real AutoCloseable semantics and deterministic event trace. */
public final class TrackedResource implements AutoCloseable {
    private final String name;
    private final boolean closeFails;
    private final List<String> events;
    private boolean closed;

    public TrackedResource(String name, boolean closeFails, List<String> events) {
        this.name = name;
        this.closeFails = closeFails;
        this.events = events;
        events.add("open:" + name);
    }

    public boolean closed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            throw new IllegalStateException("Resource already closed: " + name);
        }
        closed = true;
        events.add("close:" + name);
        if (closeFails) {
            throw new IllegalStateException("close failed: " + name);
        }
    }
}

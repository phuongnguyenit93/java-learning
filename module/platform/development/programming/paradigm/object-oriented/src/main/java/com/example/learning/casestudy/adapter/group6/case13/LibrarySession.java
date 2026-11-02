package com.example.learning.casestudy.adapter.group6.case13;

public class LibrarySession implements AutoCloseable {
    private boolean active = true;
    public boolean isActive() { return active; }
    public String query(String data) {
        if (!active) throw new IllegalStateException("Session closed");
        return "lib:" + data;
    }
    @Override public void close() { active = false; }
}

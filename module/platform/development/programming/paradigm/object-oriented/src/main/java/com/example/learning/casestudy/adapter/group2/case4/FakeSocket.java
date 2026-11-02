package com.example.learning.casestudy.adapter.group2.case4;

public final class FakeSocket implements AutoCloseable {
    public boolean closed;
    @Override public void close() {
        closed = true;
    }
}

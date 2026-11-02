package com.example.learning.casestudy.adapter.group2.case4;

public final class Client implements AutoCloseable {
    private final FakeSocket socket;
    public Client(FakeSocket socket) {
        this.socket = socket;
    }
    @Override public void close() {
        socket.close();
    }
}

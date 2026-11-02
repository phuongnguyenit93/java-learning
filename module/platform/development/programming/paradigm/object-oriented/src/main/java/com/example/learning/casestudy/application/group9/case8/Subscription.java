package com.example.learning.casestudy.application.group9.case8;

import com.example.learning.casestudy.application.group9.case8.Bus8;
import java.util.function.Consumer;

public class Subscription implements AutoCloseable {
    private final Bus8 bus;
    private final Consumer<String> listener;
    private boolean closed;
    public Subscription(Bus8 bus, Consumer<String> listener) {
        this.bus = bus; this.listener = listener;
    }
    public void close() {
        if (!closed) {
            closed = true;
            bus.active.remove(listener, this);
        }
    }
}

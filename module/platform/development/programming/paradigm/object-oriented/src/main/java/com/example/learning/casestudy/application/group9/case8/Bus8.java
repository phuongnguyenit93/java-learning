package com.example.learning.casestudy.application.group9.case8;

import com.example.learning.casestudy.application.group9.case8.Subscription;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class Bus8 {
    final Map<Consumer<String>, Subscription> active = new IdentityHashMap<>();
    public Subscription subscribe(Consumer<String> listener) {
        return active.computeIfAbsent(listener, c -> new Subscription(this, c));
    }
    public void publish(String message) {
        for (Consumer<String> listener : List.copyOf(active.keySet()))
            listener.accept(message);
    }
    public int count() { return active.size(); }
}

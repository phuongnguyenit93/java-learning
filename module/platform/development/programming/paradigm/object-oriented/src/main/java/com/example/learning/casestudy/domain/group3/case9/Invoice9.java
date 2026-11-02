package com.example.learning.casestudy.domain.group3.case9;

import com.example.learning.casestudy.domain.group3.case9.State;
import java.util.concurrent.atomic.AtomicReference;

public class Invoice9 {
    public final AtomicReference<State> state = new AtomicReference<>(new State(100, 0));
    public State snapshot() {
        return state.get();
    }
    public void update(int subtotal, int discount) {
        state.set(new State(subtotal, discount));
    }
}

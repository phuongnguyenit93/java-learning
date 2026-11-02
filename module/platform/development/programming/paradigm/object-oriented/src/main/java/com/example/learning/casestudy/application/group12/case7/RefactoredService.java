package com.example.learning.casestudy.application.group12.case7;

import com.example.learning.casestudy.domain.group12.case7.Order7;
import com.example.learning.casestudy.port.group12.case7.Listener;
import com.example.learning.casestudy.port.group12.case7.Store7;
import java.util.List;

public class RefactoredService {
    private final Store7 store;
    private final Listener listener;
    private final List<String> events;
    public RefactoredService(Store7 store, Listener listener, List<String> events) {
        this.store = store;
        this.listener = listener;
        this.events = events;
    }
    public void execute(Order7 order, String input) {
        events.add("validate");
        if (input == null || input.isBlank()) throw new IllegalArgumentException();
        store.commit(order);
        listener.onCommitted(order);
    }
}

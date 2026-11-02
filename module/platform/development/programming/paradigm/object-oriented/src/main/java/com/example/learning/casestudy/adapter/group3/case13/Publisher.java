package com.example.learning.casestudy.adapter.group3.case13;

import com.example.learning.casestudy.domain.group3.case13.Event;
import com.example.learning.casestudy.domain.group3.case13.Store;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class Publisher {
    public final Set<String> delivered = new HashSet<>();
    public void run(Store store, Consumer<Event> sender) {
        for (Event e : store.outbox) {
            if (delivered.contains(e.id()))continue;
            sender.accept(e);
            delivered.add(e.id());
        }
    }
}

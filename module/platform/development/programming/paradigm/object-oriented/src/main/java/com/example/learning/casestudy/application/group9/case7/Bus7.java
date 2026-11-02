package com.example.learning.casestudy.application.group9.case7;

import com.example.learning.casestudy.port.group9.case7.Subscriber;
import java.util.ArrayList;
import java.util.List;

public final class Bus7 {
    public final List<Subscriber> subscribers = new ArrayList<>();
    public void register(Subscriber s) { subscribers.add(s); }
    public List<RuntimeException> publish(String event) {
        List<RuntimeException> failures = new ArrayList<>();
        for (Subscriber s : List.copyOf(subscribers)) {
            try {
                s.onEvent(event);
            } catch (RuntimeException e) {
                failures.add(e);
            }
        }
        return failures;
    }
}

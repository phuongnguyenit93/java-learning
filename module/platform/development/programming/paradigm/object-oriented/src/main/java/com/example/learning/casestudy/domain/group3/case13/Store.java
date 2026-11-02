package com.example.learning.casestudy.domain.group3.case13;

import com.example.learning.casestudy.domain.group3.case13.Event;
import java.util.ArrayList;
import java.util.List;

public class Store {
    public String state ="NEW";
    public final List<Event> outbox = new ArrayList<>();
    public void pay(boolean allowed) {
        if (!allowed)throw new IllegalArgumentException();
        state ="PAID";
        outbox.add(new Event("e1"));
    }
}

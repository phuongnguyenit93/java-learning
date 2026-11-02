package com.example.learning.casestudy.application.group4.case6;

import com.example.learning.casestudy.port.group4.case6.EmailGateway;
import com.example.learning.casestudy.port.group4.case6.EventBus;
import com.example.learning.casestudy.port.group4.case6.OrderStore;

public final class SubmitOrder {
    private final OrderStore store;
    private final EventBus events;
    private final EmailGateway emails;
    public SubmitOrder(OrderStore s, EventBus e, EmailGateway m) {
        store = s;
        events = e;
        emails = m;
    }
    public void submit(String id) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("id");
        store.save(id);
        events.publish(id);
        emails.send(id);
    }
}

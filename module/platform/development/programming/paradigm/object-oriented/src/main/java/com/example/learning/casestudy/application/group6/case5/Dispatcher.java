package com.example.learning.casestudy.application.group6.case5;

import com.example.learning.casestudy.domain.group6.case5.Channel;
import com.example.learning.casestudy.port.group6.case5.NotificationSender;
import java.util.Map;

public class Dispatcher {
    private final Map<Channel, NotificationSender> routes;
    public Dispatcher(Map<Channel, NotificationSender> routes) { this.routes = Map.copyOf(routes); }
    public String dispatch(Channel c, String message) {
        NotificationSender s = routes.get(c);
        if (s == null) throw new IllegalArgumentException("Unknown channel");
        return s.send(message);
    }
}

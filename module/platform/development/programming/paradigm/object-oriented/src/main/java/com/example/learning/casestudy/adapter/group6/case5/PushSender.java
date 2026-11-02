package com.example.learning.casestudy.adapter.group6.case5;

import com.example.learning.casestudy.port.group6.case5.NotificationSender;

public record PushSender() implements NotificationSender {
    public String send(String m) { return "push:" + m; }
}

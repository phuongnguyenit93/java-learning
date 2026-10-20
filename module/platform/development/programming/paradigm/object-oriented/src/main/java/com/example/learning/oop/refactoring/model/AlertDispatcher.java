package com.example.learning.oop.refactoring.model;

import java.util.Objects;

/** An alert uses a channel; it is not a subclass of any channel implementation. */
public final class AlertDispatcher {
    private final MessageChannel channel;

    public AlertDispatcher(MessageChannel channel) {
        this.channel = Objects.requireNonNull(channel, "channel");
    }

    public void sendCritical(String recipient, String incident) {
        if (recipient == null || recipient.isBlank() || recipient.length() > 120
                || incident == null || incident.isBlank() || incident.length() > 200) {
            throw new IllegalArgumentException("recipient and incident must be nonblank and bounded");
        }
        channel.send(recipient, "[CRITICAL] " + incident);
    }
}

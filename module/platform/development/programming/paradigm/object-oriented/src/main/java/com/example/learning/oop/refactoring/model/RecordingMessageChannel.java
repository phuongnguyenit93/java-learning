package com.example.learning.oop.refactoring.model;

import com.example.learning.oop.refactoring.exception.TemporaryDeliveryFailure;
import java.util.ArrayList;
import java.util.List;

/** In-memory only: actual attempts and successful deliveries are externally observable. */
public final class RecordingMessageChannel implements MessageChannel {
    private final String name;
    private final int temporaryFailuresBeforeSuccess;
    private final List<String> events = new ArrayList<>();
    private final List<String> delivered = new ArrayList<>();
    private int attempts;

    public RecordingMessageChannel(String name, int temporaryFailuresBeforeSuccess) {
        if (!"EMAIL".equals(name) && !"SMS".equals(name)) {
            throw new IllegalArgumentException("channel must be EMAIL or SMS");
        }
        if (temporaryFailuresBeforeSuccess < 0 || temporaryFailuresBeforeSuccess > 10) {
            throw new IllegalArgumentException("transientFailures must be 0..10");
        }
        this.name = name;
        this.temporaryFailuresBeforeSuccess = temporaryFailuresBeforeSuccess;
    }

    @Override
    public void send(String recipient, String body) {
        attempts++;
        if (attempts <= temporaryFailuresBeforeSuccess) {
            events.add(name + ":attempt=" + attempts + ":TEMPORARY_FAILURE");
            throw new TemporaryDeliveryFailure("TEMPORARY_DELIVERY_FAILURE");
        }
        delivered.add(name + "->" + recipient + ":" + body);
        events.add(name + ":attempt=" + attempts + ":DELIVERED");
    }

    public int attempts() {
        return attempts;
    }

    public List<String> events() {
        return List.copyOf(events);
    }

    public List<String> delivered() {
        return List.copyOf(delivered);
    }
}

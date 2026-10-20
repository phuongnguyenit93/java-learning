package com.example.learning.oop.composition.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Formatter + transport + optional transport decorator, with no external delivery. */
public final class NotificationPipeline {
    private NotificationPipeline() {
    }

    public interface Formatter {
        String format(String orderId, long totalCents);
    }

    public interface Transport {
        void send(String recipient, String content);
    }

    public static final class FullFormatter implements Formatter {
        @Override
        public String format(String orderId, long totalCents) {
            return "Order " + orderId + " paid " + totalCents + " cents";
        }
    }

    public static final class CompactFormatter implements Formatter {
        @Override
        public String format(String orderId, long totalCents) {
            return orderId + ":PAID:" + totalCents;
        }
    }

    public static final class RecordingTransport implements Transport {
        private final String channel;
        private final boolean fails;
        private final List<String> events;

        public RecordingTransport(String channel, boolean fails, List<String> events) {
            this.channel = Objects.requireNonNull(channel);
            this.fails = fails;
            this.events = Objects.requireNonNull(events);
        }

        @Override
        public void send(String recipient, String content) {
            events.add("transport:" + channel + ":" + recipient + ":" + content);
            if (fails) {
                throw new IllegalStateException("Simulated transport rejection");
            }
        }
    }

    public static final class AuditedTransport implements Transport {
        private final Transport delegate;
        private final List<String> events;

        public AuditedTransport(Transport delegate, List<String> events) {
            this.delegate = Objects.requireNonNull(delegate);
            this.events = Objects.requireNonNull(events);
        }

        @Override
        public void send(String recipient, String content) {
            delegate.send(recipient, content);
            // Only record after the delegate returns successfully.
            events.add("audit:accepted");
        }
    }

    public static final class Notifier {
        private final Formatter formatter;
        private final Transport transport;
        private final List<String> events;

        public Notifier(Formatter formatter, Transport transport, List<String> events) {
            this.formatter = Objects.requireNonNull(formatter);
            this.transport = Objects.requireNonNull(transport);
            this.events = Objects.requireNonNull(events);
        }

        public record Outcome(String content, boolean accepted, String failure) {
        }

        public Outcome notifyPaid(String recipient, String orderId, long totalCents) {
            String body = formatter.format(orderId, totalCents);
            events.add("format:" + body);
            try {
                transport.send(recipient, body);
                return new Outcome(body, true, null);
            } catch (IllegalStateException ex) {
                return new Outcome(body, false, ex.getMessage());
            }
        }
    }

    public static List<String> freshTrace() {
        return new ArrayList<>();
    }
}

package com.example.learning.oop.polymorphism.model;

/**
 * Executable Java member resolution: overload by compile-time argument type,
 * override by receiver runtime type, fields and static methods by declaring type.
 * Public hidden fields are intentional for this single teaching experiment.
 */
public final class DispatchMechanics {
    private DispatchMechanics() {}

    public static class Event {
        private final int weight;
        public Event(int weight) {
            if (weight < 1 || weight > 10_000) {
                throw new IllegalArgumentException("weight must be 1..10000");
            }
            this.weight = weight;
        }
        public final int weight() { return weight; }
    }

    public static final class PriorityEvent extends Event {
        private final int priority;
        public PriorityEvent(int weight, int priority) {
            super(weight);
            if (priority < 1 || priority > 100) {
                throw new IllegalArgumentException("priority must be 1..100");
            }
            this.priority = priority;
        }
        public int priority() { return priority; }
    }

    public static class Handler {
        public final String label = "BASE-FIELD";
        public static String category() { return "BASE-STATIC"; }
        public String identify() { return "BASE-INSTANCE"; }
        public long handle(Event event) {
            return Math.multiplyExact((long) event.weight(), 10L);
        }
        public long handle(PriorityEvent event) {
            return Math.addExact(Math.multiplyExact((long) event.weight(), 100L),
                    event.priority());
        }
    }

    public static final class PremiumHandler extends Handler {
        public final String label = "PREMIUM-FIELD";
        private final long bonus;
        public PremiumHandler(long bonus) {
            if (bonus < 0 || bonus > 100_000) {
                throw new IllegalArgumentException("bonus must be 0..100000");
            }
            this.bonus = bonus;
        }
        public static String category() { return "PREMIUM-STATIC"; }
        @Override public String identify() { return "PREMIUM-INSTANCE"; }
        @Override public long handle(Event event) {
            return Math.addExact(super.handle(event), bonus);
        }
        @Override public long handle(PriorityEvent event) {
            return Math.addExact(super.handle(event), bonus);
        }
    }

    public static Observation observe(String receiverType, String eventType,
                                      int weight, Integer priority, long bonus) {
        if (!"BASE".equals(receiverType) && !"PREMIUM".equals(receiverType)) {
            throw new IllegalArgumentException("receiverType must be BASE or PREMIUM");
        }
        if (!"EVENT".equals(eventType) && !"PRIORITY".equals(eventType)) {
            throw new IllegalArgumentException("eventType must be EVENT or PRIORITY");
        }
        if (bonus < 0 || bonus > 100_000) {
            throw new IllegalArgumentException("bonus must be 0..100000");
        }
        if ("PRIORITY".equals(eventType) && priority == null) {
            throw new IllegalArgumentException("priority is required for PRIORITY");
        }

        Handler handler = "PREMIUM".equals(receiverType)
                ? new PremiumHandler(bonus) : new Handler();
        Event event = "PRIORITY".equals(eventType)
                ? new PriorityEvent(weight, priority) : new Event(weight);

        // The declared Event type chooses handle(Event); the receiver runtime
        // class then decides which override of that selected signature executes.
        long general = handler.handle(event);
        long selected = event instanceof PriorityEvent specialized
                ? handler.handle(specialized) : handler.handle(event);
        String parentField = handler.label;
        String runtimeField = handler instanceof PremiumHandler premium
                ? premium.label : handler.label;
        String runtimeStatic = handler instanceof PremiumHandler
                ? PremiumHandler.category() : Handler.category();

        return new Observation(receiverType, eventType, general, selected,
                handler.identify(), parentField, runtimeField,
                Handler.category(), runtimeStatic, handler == handler);
    }

    public record Observation(String receiverType, String eventType,
                              long viaDeclaredEvent, long viaSpecificEvent,
                              String overriddenInstanceMethod,
                              String fieldViaBaseReference, String fieldViaConcreteReference,
                              String staticViaBaseClass, String staticViaConcreteClass,
                              boolean sameReceiverObject) {}
}

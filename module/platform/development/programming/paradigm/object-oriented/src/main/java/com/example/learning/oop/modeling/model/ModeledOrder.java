package com.example.learning.oop.modeling.model;

/** Executable state graph; every event/state pair not listed is rejected. */
public final class ModeledOrder {
    public enum State { DRAFT, WAITING_PAYMENT, PAID, PACKING, SHIPPED, DELIVERED, CANCELLED }
    public enum Event { SUBMIT, CANCEL, CONFIRM_PAYMENT, START_PACKING, SHIP, CONFIRM_DELIVERY }

    private final String id;
    private final long totalVnd;
    private State state = State.DRAFT;
    private String trackingCode;

    public ModeledOrder(String id, long totalVnd) {
        if (id == null || !id.matches("[A-Z0-9-]{2,24}")) {
            throw new IllegalArgumentException("orderId must be 2..24 uppercase letters/digits/hyphens");
        }
        if (totalVnd < 1 || totalVnd > 1_000_000_000_000L) {
            throw new IllegalArgumentException("totalVnd must be 1..1000000000000");
        }
        this.id = id;
        this.totalVnd = totalVnd;
    }

    public String id() { return id; }
    public long totalVnd() { return totalVnd; }
    public State state() { return state; }
    public String trackingCode() { return trackingCode; }

    public void apply(Event event, Long receivedVnd, String tracking) {
        if (event == null) throw new IllegalArgumentException("event is required");
        // Check guards first, then commit a single state change.
        State next = switch (state) {
            case DRAFT -> switch (event) {
                case SUBMIT -> State.WAITING_PAYMENT;
                case CANCEL -> State.CANCELLED;
                default -> null;
            };
            case WAITING_PAYMENT -> switch (event) {
                case CONFIRM_PAYMENT -> State.PAID;
                case CANCEL -> State.CANCELLED;
                default -> null;
            };
            case PAID -> event == Event.START_PACKING ? State.PACKING : null;
            case PACKING -> event == Event.SHIP ? State.SHIPPED : null;
            case SHIPPED -> event == Event.CONFIRM_DELIVERY ? State.DELIVERED : null;
            case DELIVERED, CANCELLED -> null;
        };
        if (next == null) {
            throw new ModelRuleViolation("INVALID_TRANSITION", state + " cannot handle " + event);
        }
        if (event == Event.CONFIRM_PAYMENT && (receivedVnd == null || receivedVnd != totalVnd)) {
            throw new ModelRuleViolation("PAYMENT_GUARD", "confirmed payment must equal order total");
        }
        if (event == Event.SHIP && (tracking == null || !tracking.matches("[A-Z0-9-]{3,32}"))) {
            throw new ModelRuleViolation("TRACKING_GUARD", "trackingCode must be 3..32 uppercase letters/digits/hyphens");
        }
        if (event == Event.SHIP) trackingCode = tracking;
        state = next;
    }
}

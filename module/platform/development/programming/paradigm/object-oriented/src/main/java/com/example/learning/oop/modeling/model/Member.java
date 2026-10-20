package com.example.learning.oop.modeling.model;

/** Member owns its loan-limit rule, not the REST controller or a DTO. */
public final class Member {
    private static final int MAX_ACTIVE_LOANS = 3;
    private final String id;
    private final boolean active;
    private int activeLoans;

    public Member(String id, boolean active) {
        if (id == null || !id.matches("[A-Z0-9-]{2,24}")) {
            throw new IllegalArgumentException("member id must be 2..24 uppercase letters/digits/hyphens");
        }
        this.id = id;
        this.active = active;
    }

    public String id() { return id; }
    public boolean active() { return active; }
    public int activeLoans() { return activeLoans; }

    public void ensureEligible() {
        if (!active) throw new ModelRuleViolation("BR-01", "member is inactive");
    }

    public void ensureCapacity() {
        if (activeLoans >= MAX_ACTIVE_LOANS) {
            throw new ModelRuleViolation("BR-02", "member loan limit of three reached");
        }
    }

    public void recordBorrow() {
        ensureCapacity();
        activeLoans++;
    }

    public void recordReturn() {
        if (activeLoans <= 0) throw new IllegalStateException("member count invariant broken");
        activeLoans--;
    }
}

package com.example.learning.oop.modeling.model;

import java.time.LocalDate;

/** Historical entity with a one-way ACTIVE -> RETURNED transition. */
public final class Loan {
    public enum Status { ACTIVE, RETURNED }

    private final String id;
    private final String memberId;
    private final String copyId;
    private final LocalDate borrowedOn;
    private final LocalDate dueOn;
    private LocalDate returnedOn;

    public Loan(String id, String memberId, String copyId, LocalDate borrowedOn) {
        if (id == null || memberId == null || copyId == null || borrowedOn == null) {
            throw new IllegalArgumentException("loan identity and borrow date required");
        }
        this.id = id;
        this.memberId = memberId;
        this.copyId = copyId;
        this.borrowedOn = borrowedOn;
        this.dueOn = borrowedOn.plusDays(14); // BR-04
    }

    public String id() { return id; }
    public String memberId() { return memberId; }
    public String copyId() { return copyId; }
    public LocalDate borrowedOn() { return borrowedOn; }
    public LocalDate dueOn() { return dueOn; }
    public LocalDate returnedOn() { return returnedOn; }
    public Status status() { return returnedOn == null ? Status.ACTIVE : Status.RETURNED; }

    public void ensureCanReturn(LocalDate date) {
        if (returnedOn != null) throw new ModelRuleViolation("BR-05", "loan already returned");
        if (date == null || date.isBefore(borrowedOn)) {
            throw new ModelRuleViolation("RETURN_DATE", "return date cannot precede borrowing date");
        }
    }

    public void close(LocalDate date) {
        ensureCanReturn(date);
        returnedOn = date;
    }
}

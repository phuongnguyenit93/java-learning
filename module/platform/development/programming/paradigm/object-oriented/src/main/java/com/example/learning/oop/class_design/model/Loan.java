package com.example.learning.oop.class_design.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Rich entity: lifecycle, return invariant and late-fee calculation stay together. */
public final class Loan {
    private final String borrowerId;
    private final BookCopy copy;
    private final LocalDate borrowedOn;
    private final LocalDate dueOn;
    private LocalDate returnedOn;
    private long lateFeeCents;

    public Loan(String borrowerId, BookCopy copy, LocalDate borrowedOn, LocalDate dueOn) {
        if (borrowerId == null || borrowerId.isBlank()) {
            throw new IllegalArgumentException("borrowerId must be nonblank");
        }
        this.borrowerId = borrowerId;
        this.copy = Objects.requireNonNull(copy, "copy");
        this.borrowedOn = Objects.requireNonNull(borrowedOn, "borrowedOn");
        this.dueOn = Objects.requireNonNull(dueOn, "dueOn");
        if (!dueOn.isAfter(borrowedOn)) {
            throw new IllegalArgumentException("dueOn must be later than borrowedOn");
        }
    }

    public boolean isActive() { return returnedOn == null; }
    public String borrowerId() { return borrowerId; }
    public String copyId() { return copy.copyId(); }
    public String copyTitle() { return copy.title(); }
    public LocalDate borrowedOn() { return borrowedOn; }
    public LocalDate dueOn() { return dueOn; }
    public LocalDate returnedOn() { return returnedOn; }
    public long lateFeeCents() { return lateFeeCents; }

    public void returnOn(LocalDate date, long feePerLateDayCents) {
        Objects.requireNonNull(date, "onDate");
        if (!isActive()) {
            throw new IllegalStateException("ALREADY_RETURNED");
        }
        if (date.isBefore(borrowedOn)) {
            throw new IllegalArgumentException("Return cannot predate the borrowing");
        }
        if (feePerLateDayCents < 0) {
            throw new IllegalArgumentException("Late fee cannot be negative");
        }
        long daysLate = Math.max(0, ChronoUnit.DAYS.between(dueOn, date));
        long computedFee = Math.multiplyExact(daysLate, feePerLateDayCents);
        returnedOn = date;
        lateFeeCents = computedFee;
    }
}

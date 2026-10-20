package com.example.learning.oop.class_design.model;

import java.time.LocalDate;
import java.util.Objects;

/** Owns borrowing constraints; neither the HTTP adapter nor repository sets these rules. */
public record LoanPolicy(int maxActiveLoans, int loanDays) {
    public LoanPolicy {
        if (maxActiveLoans < 1 || maxActiveLoans > 20 || loanDays < 1 || loanDays > 365) {
            throw new IllegalArgumentException("maxActiveLoans must be 1..20 and loanDays 1..365");
        }
    }

    public void ensureEligible(long existingActiveLoans) {
        if (existingActiveLoans >= maxActiveLoans) {
            throw new IllegalStateException("BORROWER_LIMIT_REACHED");
        }
    }

    public LocalDate dueOn(LocalDate borrowedOn) {
        return Objects.requireNonNull(borrowedOn, "borrowedOn").plusDays(loanDays);
    }
}

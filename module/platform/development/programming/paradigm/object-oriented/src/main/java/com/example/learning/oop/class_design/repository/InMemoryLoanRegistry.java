package com.example.learning.oop.class_design.repository;

import com.example.learning.oop.class_design.model.BookCopy;
import com.example.learning.oop.class_design.model.Loan;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-request scenario store: no cross-request state or database assumptions. */
public final class InMemoryLoanRegistry {
    private final Map<String, BookCopy> copies = new LinkedHashMap<>();
    private final Map<String, Loan> loans = new LinkedHashMap<>();

    public void register(BookCopy copy) {
        if (copies.putIfAbsent(copy.copyId(), copy) != null) {
            throw new IllegalArgumentException("Duplicate copyId: " + copy.copyId());
        }
    }

    public BookCopy requireCopy(String copyId) {
        BookCopy copy = copies.get(copyId);
        if (copy == null) {
            throw new IllegalArgumentException("Unknown copyId: " + copyId);
        }
        return copy;
    }

    public long activeCount(String borrowerId) {
        return loans.values().stream()
                .filter(loan -> loan.isActive() && loan.borrowerId().equals(borrowerId))
                .count();
    }

    public void save(Loan loan) {
        Loan previous = loans.get(loan.copyId());
        if (previous != null && previous.isActive()) {
            throw new IllegalStateException("COPY_ALREADY_BORROWED");
        }
        loans.put(loan.copyId(), loan);
    }

    public Loan requireActiveLoan(String copyId) {
        requireCopy(copyId);
        Loan loan = loans.get(copyId);
        if (loan == null) {
            throw new IllegalStateException("COPY_NOT_BORROWED");
        }
        return loan;
    }

    public List<Loan> snapshot() {
        return List.copyOf(loans.values());
    }
}

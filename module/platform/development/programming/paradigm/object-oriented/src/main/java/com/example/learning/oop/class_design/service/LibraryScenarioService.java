package com.example.learning.oop.class_design.service;

import com.example.learning.oop.class_design.model.BookCopy;
import com.example.learning.oop.class_design.model.Loan;
import com.example.learning.oop.class_design.model.LoanPolicy;
import com.example.learning.oop.class_design.repository.InMemoryLoanRegistry;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import static com.example.learning.oop.class_design.dto.ClassDesignDtos.*;

/** A deterministic scenario exposes commands and pure snapshot queries in one request. */
@Service
public class LibraryScenarioService {
    private static final long DAILY_LATE_FEE_CENTS = 100;

    public LibraryResult run(LibraryRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.copies() == null || request.copies().isEmpty() || request.copies().size() > 40
                || request.actions() == null || request.actions().isEmpty()
                || request.actions().size() > 50) {
            throw new IllegalArgumentException("Provide 1..40 copies and 1..50 actions");
        }
        LoanPolicy policy = new LoanPolicy(request.maxActiveLoans(), request.loanDays());
        InMemoryLoanRegistry repository = new InMemoryLoanRegistry();
        for (BookInput copy : request.copies()) {
            if (copy == null) {
                throw new IllegalArgumentException("Book copy cannot be null");
            }
            repository.register(new BookCopy(copy.copyId(), copy.title()));
        }
        List<LibraryStep> steps = new ArrayList<>();
        for (int index = 0; index < request.actions().size(); index++) {
            LibraryAction action = Objects.requireNonNull(request.actions().get(index), "action");
            String type = requireText(action.type(), "action.type");
            String outcome = "QUERY_ONLY";
            boolean accepted = true;
            try {
                switch (type) {
                    case "BORROW" -> {
                        String borrower = requireText(action.borrowerId(), "borrowerId");
                        String copyId = requireText(action.copyId(), "copyId");
                        var borrowedOn = Objects.requireNonNull(action.onDate(), "onDate");
                        BookCopy copy = repository.requireCopy(copyId);
                        // Repository guards copy uniqueness; policy owns the borrower limit.
                        policy.ensureEligible(repository.activeCount(borrower));
                        repository.save(new Loan(borrower, copy, borrowedOn, policy.dueOn(borrowedOn)));
                        outcome = "BORROWED";
                    }
                    case "RETURN" -> {
                        Loan loan = repository.requireActiveLoan(requireText(action.copyId(), "copyId"));
                        loan.returnOn(Objects.requireNonNull(action.onDate(), "onDate"),
                                DAILY_LATE_FEE_CENTS);
                        outcome = "RETURNED";
                    }
                    case "QUERY" -> { /* read-only: no mutation */ }
                    default -> throw new IllegalArgumentException("Unsupported action: " + type);
                }
            } catch (IllegalStateException rejected) {
                accepted = false;
                outcome = rejected.getMessage();
            }
            steps.add(new LibraryStep(index + 1, type, accepted, outcome, views(repository)));
        }
        return new LibraryResult(List.copyOf(steps), views(repository));
    }

    private List<LoanView> views(InMemoryLoanRegistry repository) {
        return repository.snapshot().stream().map(loan ->
                new LoanView(loan.borrowerId(), loan.copyId(), loan.copyTitle(),
                        loan.borrowedOn(), loan.dueOn(), loan.returnedOn(), loan.lateFeeCents()))
                .toList();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be nonblank");
        }
        return value;
    }
}

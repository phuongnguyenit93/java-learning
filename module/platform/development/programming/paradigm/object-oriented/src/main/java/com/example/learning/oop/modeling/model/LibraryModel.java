package com.example.learning.oop.modeling.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request-local aggregate of identity maps; a copied book title != borrowed copy != loan.
 * All mutations occur under a single lock, with all expected guards checked first.
 */
public final class LibraryModel {
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final Map<String, BookCopy> copies = new LinkedHashMap<>();
    private final Map<String, Loan> history = new LinkedHashMap<>();

    public LibraryModel(List<Member> initialMembers, List<BookCopy> initialCopies) {
        if (initialMembers == null || initialCopies == null) {
            throw new IllegalArgumentException("members and copies are required");
        }
        for (Member m : initialMembers) {
            if (m == null || members.putIfAbsent(m.id(), m) != null) {
                throw new IllegalArgumentException("member IDs must be unique and non-null");
            }
        }
        for (BookCopy c : initialCopies) {
            if (c == null || copies.putIfAbsent(c.id(), c) != null) {
                throw new IllegalArgumentException("copy IDs must be unique and non-null");
            }
        }
    }

    public synchronized Loan borrow(String memberId, String copyId, LocalDate today, List<String> calls) {
        calls.add("LibraryModel.borrow");
        Member member = members.get(memberId);
        if (member == null) throw new ModelRuleViolation("UNKNOWN_MEMBER", "unknown member");
        BookCopy copy = copies.get(copyId);
        if (copy == null) throw new ModelRuleViolation("UNKNOWN_COPY", "unknown physical copy");
        calls.add("Member.ensureEligible");
        member.ensureEligible();
        calls.add("Member.ensureCapacity");
        member.ensureCapacity();
        calls.add("BookCopy.ensureAvailable");
        copy.ensureAvailable();
        calls.add("Loan.create");
        Loan loan = new Loan("LOAN-" + (history.size() + 1), member.id(), copy.id(), today);
        // All predictable domain failures have been checked. Commit the local change.
        calls.add("BookCopy.markBorrowed");
        copy.markBorrowed();
        calls.add("Member.recordBorrow");
        member.recordBorrow();
        calls.add("LibraryModel.saveLoan");
        history.put(loan.id(), loan);
        return loan;
    }

    public synchronized Loan returnLoan(String loanId, LocalDate today, List<String> calls) {
        calls.add("LibraryModel.returnLoan");
        calls.add("LibraryModel.lookupLoan");
        Loan loan = history.get(loanId);
        if (loan == null) throw new ModelRuleViolation("UNKNOWN_LOAN", "unknown loan id");
        calls.add("Loan.ensureCanReturn");
        loan.ensureCanReturn(today);
        Member member = members.get(loan.memberId());
        BookCopy copy = copies.get(loan.copyId());
        if (member == null || copy == null || !copy.borrowed() || member.activeLoans() == 0) {
            throw new IllegalStateException("loan-reference invariant broken");
        }
        calls.add("Loan.close");
        loan.close(today);
        calls.add("BookCopy.markReturned");
        copy.markReturned();
        calls.add("Member.recordReturn");
        member.recordReturn();
        // BR-06: no history deletion.
        return loan;
    }

    public synchronized List<Member> members() { return List.copyOf(members.values()); }
    public synchronized List<BookCopy> copies() { return List.copyOf(copies.values()); }
    public synchronized List<Loan> history() { return List.copyOf(history.values()); }

    /** Verify the entire object graph, not only each class's local invariant. */
    public synchronized void assertInvariants() {
        for (Member member : members.values()) {
            long actual = history.values().stream()
                    .filter(l -> l.status() == Loan.Status.ACTIVE && l.memberId().equals(member.id()))
                    .count();
            if (actual != member.activeLoans() || actual > 3) {
                throw new IllegalStateException("member count invariant broken");
            }
        }
        for (BookCopy copy : copies.values()) {
            long actual = history.values().stream()
                    .filter(l -> l.status() == Loan.Status.ACTIVE && l.copyId().equals(copy.id()))
                    .count();
            if (actual > 1 || copy.borrowed() != (actual == 1)) {
                throw new IllegalStateException("copy occupancy invariant broken");
            }
        }
        for (Loan loan : history.values()) {
            if (!members.containsKey(loan.memberId()) || !copies.containsKey(loan.copyId()) ||
                    !loan.dueOn().equals(loan.borrowedOn().plusDays(14)) ||
                    (loan.returnedOn() != null && loan.returnedOn().isBefore(loan.borrowedOn()))) {
                throw new IllegalStateException("loan historical invariant broken");
            }
        }
    }
}

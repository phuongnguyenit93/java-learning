package com.example.learning.oop.practice.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The same book/loan graph survives a policy change. The service owns inter-object
 * checks and one-JVM locking; dueOn is captured on Loan creation, never recomputed.
 */
public final class PracticeLibrary {
    public enum Kind { NORMAL, REFERENCE }
    public enum PolicyName { LEGACY, REVISED }

    public record Book(String isbn, String title, Kind kind) {
        public Book {
            requireCode(isbn, "isbn");
            if (title == null || title.isBlank() || title.length() > 120 || kind == null) {
                throw new IllegalArgumentException("valid book title and kind required");
            }
            title = title.strip();
        }
    }
    public record Member(String id, boolean active) {
        public Member { requireCode(id, "memberId"); }
    }
    public record Copy(String id, String isbn) {
        public Copy {
            requireCode(id, "copyId");
            requireCode(isbn, "isbn");
        }
    }
    public record Loan(String id, String memberId, String copyId, LocalDate borrowedOn,
                       LocalDate dueOn, LocalDate returnedOn) {
        public boolean open() { return returnedOn == null; }
        public boolean overdue(LocalDate onDate) {
            return open() && onDate.isAfter(dueOn);
        }
    }
    public record CopyView(String id, String isbn, Kind kind, boolean available) {}
    public record Snapshot(PolicyName policy, List<CopyView> copies, List<Loan> loans,
                           int openLoanCount) {}

    public interface BorrowingPolicy {
        int maxOpen();
        int daysFor(Kind kind);
        PolicyName name();
    }
    public static final class LegacyPolicy implements BorrowingPolicy {
        @Override public int maxOpen() { return 3; }
        @Override public int daysFor(Kind kind) { return 14; }
        @Override public PolicyName name() { return PolicyName.LEGACY; }
    }
    public static final class RevisedPolicy implements BorrowingPolicy {
        @Override public int maxOpen() { return 5; }
        @Override public int daysFor(Kind kind) { return kind == Kind.REFERENCE ? 7 : 14; }
        @Override public PolicyName name() { return PolicyName.REVISED; }
    }
    public static final class RuleRejected extends IllegalStateException {
        private final String rule;
        public RuleRejected(String rule, String detail) {
            super(detail); this.rule = rule;
        }
        public String rule() { return rule; }
    }

    private final Map<String, Book> books = new LinkedHashMap<>();
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final Map<String, Copy> copies = new LinkedHashMap<>();
    private final Map<String, Loan> history = new LinkedHashMap<>();
    private BorrowingPolicy policy = new LegacyPolicy();

    public PracticeLibrary(List<Book> titles, List<Member> people, List<Copy> physicalCopies) {
        if (titles == null || titles.isEmpty() || titles.size() > 20 ||
                people == null || people.isEmpty() || people.size() > 20 ||
                physicalCopies == null || physicalCopies.isEmpty() || physicalCopies.size() > 30) {
            throw new IllegalArgumentException("require 1..20 books, 1..20 members and 1..30 copies");
        }
        for (Book b : titles) {
            if (b == null || books.putIfAbsent(b.isbn(), b) != null) {
                throw new IllegalArgumentException("duplicate or null book");
            }
        }
        for (Member m : people) {
            if (m == null || members.putIfAbsent(m.id(), m) != null) {
                throw new IllegalArgumentException("duplicate or null member");
            }
        }
        for (Copy c : physicalCopies) {
            if (c == null || !books.containsKey(c.isbn()) ||
                    copies.putIfAbsent(c.id(), c) != null) {
                throw new IllegalArgumentException("duplicate, null or unlinked copy");
            }
        }
    }

    public synchronized PolicyName policy() { return policy.name(); }

    public synchronized void revise() {
        if (policy.name() != PolicyName.LEGACY) {
            throw new RuleRejected("POLICY_ALREADY_REVISED", "policy may only be changed once");
        }
        policy = new RevisedPolicy();
    }

    public synchronized Loan borrow(String memberId, String copyId, LocalDate today) {
        requireDate(today);
        Member member = members.get(memberId);
        Copy copy = copies.get(copyId);
        if (member == null) throw new RuleRejected("UNKNOWN_MEMBER", "member is not registered");
        if (copy == null) throw new RuleRejected("UNKNOWN_COPY", "physical copy does not exist");
        if (!member.active()) throw new RuleRejected("BR-01", "member is inactive");
        int opened = 0;
        for (Loan loan : history.values()) {
            if (!loan.open()) continue;
            if (loan.copyId().equals(copyId)) {
                throw new RuleRejected("BR-03", "physical copy already on loan");
            }
            if (loan.memberId().equals(memberId)) {
                opened++;
                if (loan.overdue(today)) {
                    throw new RuleRejected("BR-04", "member has an overdue open loan");
                }
            }
        }
        if (opened >= policy.maxOpen()) {
            throw new RuleRejected("BR-02", "active loan limit reached");
        }
        Book book = books.get(copy.isbn());
        LocalDate dueOn = today.plusDays(policy.daysFor(book.kind()));
        Loan fresh = new Loan("LOAN-" + (history.size() + 1), memberId, copyId, today, dueOn, null);
        history.put(fresh.id(), fresh);
        return fresh;
    }

    public synchronized Loan returnLoan(String loanId, LocalDate date) {
        requireDate(date);
        Loan loan = history.get(loanId);
        if (loan == null) throw new RuleRejected("UNKNOWN_LOAN", "loan does not exist");
        if (!loan.open()) throw new RuleRejected("BR-06", "loan already returned");
        if (date.isBefore(loan.borrowedOn())) {
            throw new RuleRejected("BR-07", "return precedes borrowed date");
        }
        Loan closed = new Loan(loan.id(), loan.memberId(), loan.copyId(),
                loan.borrowedOn(), loan.dueOn(), date);
        history.put(closed.id(), closed);
        return closed;
    }

    public synchronized Snapshot snapshot() {
        List<CopyView> views = copies.values().stream()
                .map(c -> new CopyView(c.id(), c.isbn(),
                        books.get(c.isbn()).kind(),
                        history.values().stream().noneMatch(l -> l.open() && l.copyId().equals(c.id()))))
                .toList();
        List<Loan> loans = List.copyOf(history.values());
        return new Snapshot(policy.name(), views, loans,
                (int) loans.stream().filter(Loan::open).count());
    }

    public synchronized void checkInvariants() {
        for (Copy c : copies.values()) {
            long opened = history.values().stream().filter(l -> l.open() && l.copyId().equals(c.id())).count();
            if (opened > 1) throw new IllegalStateException("copy linked to multiple open loans");
        }
        for (Loan loan : history.values()) {
            if (!members.containsKey(loan.memberId()) || !copies.containsKey(loan.copyId())
                    || loan.borrowedOn() == null || loan.dueOn() == null
                    || loan.dueOn().isBefore(loan.borrowedOn())
                    || (loan.returnedOn() != null && loan.returnedOn().isBefore(loan.borrowedOn()))) {
                throw new IllegalStateException("history invariant broken");
            }
        }
    }

    private static void requireCode(String id, String name) {
        if (id == null || !id.matches("[A-Z0-9-]{2,32}")) {
            throw new IllegalArgumentException(name + " must be 2..32 uppercase letters/digits/hyphens");
        }
    }
    private static void requireDate(LocalDate date) {
        if (date == null || date.getYear() < 2000 || date.getYear() > 2099) {
            throw new IllegalArgumentException("date year must be 2000..2099");
        }
    }
}

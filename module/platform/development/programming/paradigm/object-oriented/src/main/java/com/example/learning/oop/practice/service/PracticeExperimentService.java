package com.example.learning.oop.practice.service;

import com.example.learning.oop.practice.model.BankLedger;
import com.example.learning.oop.practice.model.PracticeOrder;
import com.example.learning.oop.practice.model.PracticeLibrary;
import static com.example.learning.oop.practice.dto.PracticeDtos.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** An actual state machine per request, with no durable financial/library side effects. */
@Service
public final class PracticeExperimentService {
    public BankResult bank(BankRequest request) {
        if (request == null || request.accounts() == null) {
            throw new IllegalArgumentException("accounts required");
        }
        steps(request.actions(), 20);
        List<BankLedger.Seed> seeds = new ArrayList<>();
        for (BankSeed input : request.accounts()) {
            if (input == null || input.openingBalanceVnd() == null) {
                throw new IllegalArgumentException("every account needs openingBalanceVnd");
            }
            seeds.add(new BankLedger.Seed(input.id(), input.owner(), input.openingBalanceVnd()));
        }
        for (BankAction action : request.actions()) {
            if (action == null || action.type() == null || action.accountId() == null ||
                    action.amountVnd() == null ||
                    !List.of("DEPOSIT", "WITHDRAW", "TRANSFER").contains(action.type()) ||
                    (action.type().equals("TRANSFER") && action.toAccountId() == null)) {
                throw new IllegalArgumentException("bank actions require known type, accountId, amount, and target for TRANSFER");
            }
        }
        BankLedger ledger = new BankLedger(seeds);
        List<BankLedger.AccountView> initial = ledger.snapshot();
        List<BankStep> result = new ArrayList<>();
        int index = 1;
        for (BankAction action : request.actions()) {
            List<BankLedger.AccountView> before = ledger.snapshot();
            boolean accepted = true;
            String detail = "completed";
            try {
                switch (action.type()) {
                    case "DEPOSIT" -> ledger.deposit(action.accountId(), action.amountVnd());
                    case "WITHDRAW" -> ledger.withdraw(action.accountId(), action.amountVnd());
                    case "TRANSFER" -> ledger.transfer(
                            action.accountId(), action.toAccountId(), action.amountVnd());
                    default -> throw new IllegalArgumentException("unsupported action");
                }
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
                accepted = false;
                detail = ex.getMessage();
            }
            result.add(new BankStep(index++, action.type(), accepted, detail,
                    before, ledger.snapshot()));
        }
        return new BankResult(initial, List.copyOf(result), ledger.snapshot(),
                "REQUEST_LOCAL_IN_MEMORY_NO_REAL_BANK_ACCOUNTS_OR_DURABLE_TRANSACTIONS");
    }

    public OrderResult order(OrderRequest request) {
        if (request == null) throw new IllegalArgumentException("order request required");
        steps(request.actions(), 20);
        for (OrderAction action : request.actions()) {
            if (action == null || action.type() == null ||
                    !List.of("SET_LINE", "CHANGE_QUANTITY", "REMOVE_LINE", "PLACE", "CANCEL").contains(action.type())) {
                throw new IllegalArgumentException("unsupported order action");
            }
            switch (action.type()) {
                case "SET_LINE" -> {
                    if (action.sku() == null || action.unitPriceCents() == null || action.quantity() == null) {
                        throw new IllegalArgumentException("SET_LINE needs sku/price/quantity");
                    }
                }
                case "CHANGE_QUANTITY" -> {
                    if (action.sku() == null || action.quantity() == null) {
                        throw new IllegalArgumentException("CHANGE_QUANTITY needs sku/quantity");
                    }
                }
                case "REMOVE_LINE" -> {
                    if (action.sku() == null) throw new IllegalArgumentException("REMOVE_LINE needs sku");
                }
                default -> {}
            }
        }
        PracticeOrder order = new PracticeOrder(request.orderId());
        OrderState initial = orderState(order);
        List<OrderStep> result = new ArrayList<>();
        int index = 1;
        for (OrderAction action : request.actions()) {
            OrderState before = orderState(order);
            boolean accepted = true;
            String detail = "completed";
            Boolean removed = null;
            try {
                switch (action.type()) {
                    case "SET_LINE" -> order.setLine(action.sku(), action.unitPriceCents(), action.quantity());
                    case "CHANGE_QUANTITY" -> order.changeQuantity(action.sku(), action.quantity());
                    case "REMOVE_LINE" -> removed = order.removeLine(action.sku());
                    case "PLACE" -> order.place();
                    case "CANCEL" -> order.cancel();
                    default -> throw new IllegalArgumentException("unsupported order action");
                }
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException | NoSuchElementException ex) {
                accepted = false;
                detail = ex.getMessage();
            }
            result.add(new OrderStep(index++, action.type(), accepted, detail,
                    removed, before, orderState(order)));
        }
        return new OrderResult(initial, List.copyOf(result), orderState(order),
                "REQUEST_LOCAL_IN_MEMORY_NO_PAYMENT_SHIPPING_OR_PERSISTENCE");
    }

    public LibraryResult library(LibraryRequest request) {
        if (request == null || request.books() == null ||
                request.members() == null || request.copies() == null) {
            throw new IllegalArgumentException("books, members, copies required");
        }
        steps(request.actions(), 25);
        List<PracticeLibrary.Book> books = new ArrayList<>();
        for (LibraryBookInput b : request.books()) {
            if (b == null || b.kind() == null) {
                throw new IllegalArgumentException("book kind and identity required");
            }
            try {
                books.add(new PracticeLibrary.Book(b.isbn(), b.title(),
                        PracticeLibrary.Kind.valueOf(b.kind())));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("book kind must be NORMAL or REFERENCE and data valid", ex);
            }
        }
        List<PracticeLibrary.Member> people = new ArrayList<>();
        for (LibraryMemberInput m : request.members()) {
            if (m == null || m.active() == null) {
                throw new IllegalArgumentException("member id/active required");
            }
            people.add(new PracticeLibrary.Member(m.id(), m.active()));
        }
        List<PracticeLibrary.Copy> copies = new ArrayList<>();
        for (LibraryCopyInput c : request.copies()) {
            if (c == null) throw new IllegalArgumentException("copy required");
            copies.add(new PracticeLibrary.Copy(c.id(), c.isbn()));
        }
        List<LocalDate> dates = new ArrayList<>();
        LocalDate last = null;
        for (LibraryAction action : request.actions()) {
            if (action == null || action.type() == null ||
                    !List.of("BORROW", "RETURN", "REVISE_POLICY").contains(action.type())) {
                throw new IllegalArgumentException("library action must be BORROW/RETURN/REVISE_POLICY");
            }
            if (action.type().equals("BORROW") && (action.memberId() == null || action.copyId() == null)
                    || action.type().equals("RETURN") && action.loanId() == null) {
                throw new IllegalArgumentException("action requires memberId/copyId or loanId");
            }
            LocalDate date = parseDate(action.onDate());
            if (last != null && date.isBefore(last)) {
                throw new IllegalArgumentException("action dates must be nondecreasing");
            }
            dates.add(date);
            last = date;
        }
        PracticeLibrary model = new PracticeLibrary(books, people, copies);
        LibraryState initial = libraryState(model);
        List<LibraryStep> result = new ArrayList<>();
        int index = 1;
        for (int i = 0; i < request.actions().size(); i++) {
            LibraryAction action = request.actions().get(i);
            LibraryState before = libraryState(model);
            boolean accepted = true;
            String rule = null, detail = "completed", loanId = null;
            try {
                switch (action.type()) {
                    case "BORROW" -> loanId = model.borrow(
                            action.memberId(), action.copyId(), dates.get(i)).id();
                    case "RETURN" -> loanId = model.returnLoan(
                            action.loanId(), dates.get(i)).id();
                    case "REVISE_POLICY" -> model.revise();
                    default -> throw new IllegalArgumentException("unsupported library action");
                }
            } catch (PracticeLibrary.RuleRejected ex) {
                accepted = false;
                rule = ex.rule();
                detail = ex.getMessage();
            }
            model.checkInvariants();
            result.add(new LibraryStep(index++, action.type(), accepted, rule, detail,
                    loanId, before, libraryState(model)));
        }
        return new LibraryResult(initial, List.copyOf(result), libraryState(model), true,
                "REQUEST_LOCAL_IN_MEMORY_POLICY_CHANGE_NOT_RETROACTIVE_NO_DB_OR_CLUSTER_LOCK");
    }

    private static LibraryState libraryState(PracticeLibrary model) {
        PracticeLibrary.Snapshot snapshot = model.snapshot();
        List<LibraryLoanView> loans = snapshot.loans().stream()
                .map(loan -> new LibraryLoanView(loan.id(), loan.memberId(),
                        loan.copyId(), loan.borrowedOn().toString(),
                        loan.dueOn().toString(),
                        loan.returnedOn() == null ? null : loan.returnedOn().toString(),
                        loan.open()))
                .toList();
        return new LibraryState(snapshot.policy().name(), snapshot.copies(),
                loans, snapshot.openLoanCount());
    }

    private static OrderState orderState(PracticeOrder order) {
        return new OrderState(order.id(), order.status().name(), order.lines(), order.summary());
    }

    private static void steps(List<?> actions, int max) {
        if (actions == null || actions.isEmpty() || actions.size() > max) {
            throw new IllegalArgumentException("actions must contain 1.." + max + " entries");
        }
    }

    private static LocalDate parseDate(String source) {
        if (source == null) throw new IllegalArgumentException("onDate yyyy-MM-dd required");
        try {
            LocalDate date = LocalDate.parse(source);
            if (date.getYear() < 2000 || date.getYear() > 2099) {
                throw new IllegalArgumentException("onDate year must be 2000..2099");
            }
            return date;
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("onDate must be ISO yyyy-MM-dd", ex);
        }
    }
}

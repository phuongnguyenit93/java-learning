package com.example.learning.oop.modeling.service;

import com.example.learning.oop.modeling.model.*;
import static com.example.learning.oop.modeling.dto.ModelingDtos.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** New in-memory graphs per request; snapshots and traces originate from actual object calls. */
@Service
public final class ModelingExperimentService {
    private static final String LIBRARY_SCOPE =
            "REQUEST_LOCAL_ONLY_SIMULATED_LIBRARY_NO_DATABASE_OR_CROSS_REQUEST_CONCURRENCY";
    private static final String ORDER_SCOPE =
            "REQUEST_LOCAL_ONLY_SIMULATED_ORDER_NO_PAYMENT_OR_EXTERNAL_SHIPPING";

    public LibraryResult library(LibraryRequest input) {
        if (input == null || input.titles() == null || input.members() == null ||
                input.copies() == null || input.actions() == null ||
                input.titles().isEmpty() || input.titles().size() > 20 ||
                input.members().isEmpty() || input.members().size() > 20 ||
                input.copies().isEmpty() || input.copies().size() > 30) {
            throw new IllegalArgumentException("require 1..20 titles, 1..20 members, 1..30 copies");
        }
        requireSteps(input.actions(), 25);
        Map<String, BookTitle> titles = new LinkedHashMap<>();
        for (TitleInput t : input.titles()) {
            if (t == null) throw new IllegalArgumentException("title is required");
            BookTitle title = new BookTitle(t.code(), t.name());
            if (titles.putIfAbsent(title.code(), title) != null) {
                throw new IllegalArgumentException("duplicate title code");
            }
        }
        List<Member> members = new ArrayList<>();
        for (MemberInput m : input.members()) {
            if (m == null || m.active() == null) {
                throw new IllegalArgumentException("member id and active flag are required");
            }
            members.add(new Member(m.id(), m.active()));
        }
        List<BookCopy> copies = new ArrayList<>();
        for (CopyInput c : input.copies()) {
            if (c == null) throw new IllegalArgumentException("copy is required");
            BookTitle title = titles.get(c.titleCode());
            if (title == null) throw new IllegalArgumentException("unknown title code for physical copy");
            copies.add(new BookCopy(c.id(), title));
        }
        List<LocalDate> dates = new ArrayList<>();
        LocalDate previous = null;
        for (LibraryAction action : input.actions()) {
            if (action == null || !List.of("BORROW", "RETURN").contains(action.type())) {
                throw new IllegalArgumentException("action.type must be BORROW or RETURN");
            }
            if (action.type().equals("BORROW")) {
                requireId(action.memberId(), "memberId");
                requireId(action.copyId(), "copyId");
            } else {
                requireId(action.loanId(), "loanId");
            }
            LocalDate date = parseDate(action.onDate());
            if (previous != null && date.isBefore(previous)) {
                throw new IllegalArgumentException("action dates must be non-decreasing");
            }
            previous = date;
            dates.add(date);
        }

        LibraryModel library = new LibraryModel(members, copies);
        LibrarySnapshot initial = snapshot(library);
        List<LibraryStep> steps = new ArrayList<>();
        for (int i = 0; i < input.actions().size(); i++) {
            LibraryAction a = input.actions().get(i);
            LibrarySnapshot before = snapshot(library);
            List<String> calls = new ArrayList<>();
            boolean accepted = true;
            String rule = null, detail = "completed", loanId = null;
            try {
                Loan loan = a.type().equals("BORROW")
                        ? library.borrow(a.memberId(), a.copyId(), dates.get(i), calls)
                        : library.returnLoan(a.loanId(), dates.get(i), calls);
                loanId = loan.id();
            } catch (ModelRuleViolation ex) {
                accepted = false;
                rule = ex.rule();
                detail = ex.getMessage();
            }
            library.assertInvariants();
            steps.add(new LibraryStep(i + 1, a.type(), accepted, rule, detail,
                    loanId, List.copyOf(calls), before, snapshot(library)));
        }
        library.assertInvariants();
        return new LibraryResult(initial, List.copyOf(steps), snapshot(library),
                true, LIBRARY_SCOPE);
    }

    public OrderResult order(OrderRequest input) {
        if (input == null || input.totalVnd() == null) {
            throw new IllegalArgumentException("orderId and totalVnd required");
        }
        requireSteps(input.actions(), 20);
        List<ModeledOrder.Event> events = new ArrayList<>();
        for (OrderAction action : input.actions()) {
            if (action == null || action.event() == null) {
                throw new IllegalArgumentException("order event is required");
            }
            try {
                events.add(ModeledOrder.Event.valueOf(action.event()));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("unsupported order event", ex);
            }
        }
        ModeledOrder order = new ModeledOrder(input.orderId(), input.totalVnd());
        OrderSnapshot initial = orderSnapshot(order);
        List<OrderStep> steps = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            OrderSnapshot before = orderSnapshot(order);
            OrderAction action = input.actions().get(i);
            boolean accepted = true;
            String rule = null, detail = "transition applied";
            try {
                order.apply(events.get(i), action.receivedVnd(), action.trackingCode());
            } catch (ModelRuleViolation ex) {
                accepted = false;
                rule = ex.rule();
                detail = ex.getMessage();
            }
            steps.add(new OrderStep(i + 1, events.get(i).name(),
                    accepted, rule, detail, before, orderSnapshot(order)));
        }
        return new OrderResult(initial, List.copyOf(steps), orderSnapshot(order), ORDER_SCOPE);
    }

    private static LibrarySnapshot snapshot(LibraryModel model) {
        List<MemberView> members = model.members().stream()
                .sorted(Comparator.comparing(Member::id))
                .map(m -> new MemberView(m.id(), m.active(), m.activeLoans())).toList();
        List<CopyView> copies = model.copies().stream()
                .sorted(Comparator.comparing(BookCopy::id))
                .map(c -> new CopyView(c.id(), c.title().code(),
                        c.title().name(), c.borrowed())).toList();
        List<LoanView> loans = model.history().stream()
                .sorted(Comparator.comparing(Loan::id))
                .map(l -> new LoanView(l.id(), l.memberId(), l.copyId(),
                        l.borrowedOn().toString(), l.dueOn().toString(),
                        l.returnedOn() == null ? null : l.returnedOn().toString(),
                        l.status().name())).toList();
        return new LibrarySnapshot(members, copies, loans);
    }

    private static OrderSnapshot orderSnapshot(ModeledOrder o) {
        return new OrderSnapshot(o.id(), o.state().name(), o.totalVnd(), o.trackingCode());
    }

    private static void requireSteps(List<?> steps, int max) {
        if (steps == null || steps.isEmpty() || steps.size() > max) {
            throw new IllegalArgumentException("actions must have 1.." + max + " steps");
        }
    }

    private static void requireId(String id, String name) {
        if (id == null || !id.matches("[A-Z0-9-]{2,24}")) {
            throw new IllegalArgumentException(name + " must be 2..24 uppercase letters/digits/hyphens");
        }
    }

    private static LocalDate parseDate(String input) {
        if (input == null) throw new IllegalArgumentException("onDate is required");
        try {
            LocalDate date = LocalDate.parse(input);
            if (date.getYear() < 2000 || date.getYear() > 2099) {
                throw new IllegalArgumentException("onDate year must be 2000..2099");
            }
            return date;
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("onDate must use yyyy-MM-dd", ex);
        }
    }
}

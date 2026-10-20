package com.example.learning.oop.class_chapter.service;

import com.example.learning.oop.class_chapter.model.BankAccount;
import com.example.learning.oop.class_chapter.model.InventoryItem;
import com.example.learning.oop.class_chapter.model.Workshop;
import static com.example.learning.oop.class_chapter.dto.ClassesExperimentDtos.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;


/** Stateless orchestration: every experiment creates independent, request-local objects. */
@Service
public final class ClassesExperimentService {
    private static final int MAX_STEPS = 20;
    private static final int MAX_REGISTRATION_ATTEMPTS = 50;

    public InventoryResult simulateInventory(InventoryRequest request) {
        if (request == null || request.unitPriceVnd() == null || request.initialQuantity() == null) {
            throw new IllegalArgumentException("sku, name, unitPriceVnd and initialQuantity are required");
        }
        checkActions(request.actions());
        for (InventoryAction action : request.actions()) {
            if (action == null || action.type() == null ||
                    !List.of("restock", "sell", "rename").contains(action.type())) {
                throw new IllegalArgumentException("inventory type must be restock, sell or rename");
            }
            if (!action.type().equals("rename") && action.units() == null) {
                throw new IllegalArgumentException("units is required for restock and sell");
            }
        }
        InventoryItem item = new InventoryItem(request.sku(), request.name(),
                request.unitPriceVnd(), request.initialQuantity());
        InventorySnapshot initial = snapshot(item);
        List<InventoryStep> steps = new ArrayList<>();
        int index = 1;
        for (InventoryAction action : request.actions()) {
            InventorySnapshot before = snapshot(item);
            boolean accepted = true;
            String detail = "completed";
            try {
                switch (action.type()) {
                    case "restock" -> item.restock(action.units());
                    case "sell" -> item.sell(action.units());
                    case "rename" -> item.rename(action.name());
                    default -> throw new IllegalArgumentException("unknown inventory action");
                }
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
                accepted = false;
                detail = ex.getMessage();
            }
            steps.add(new InventoryStep(index++, action.type(), accepted, detail,
                    before, snapshot(item)));
        }
        return new InventoryResult(initial, List.copyOf(steps), snapshot(item),
                InventoryItem.MAX_QUANTITY);
    }

    public WorkshopResult simulateWorkshops(WorkshopRequest request) {
        if (request == null || request.firstCapacity() == null ||
                request.secondCapacity() == null) {
            throw new IllegalArgumentException("titles and capacities for both workshops are required");
        }
        int firstAttempts = checkedAttempts(request.firstAttempts(), "firstAttempts");
        int aliasAttempts = checkedAttempts(request.aliasAttempts(), "aliasAttempts");
        Workshop first = new Workshop(request.firstTitle(), request.firstCapacity());
        Workshop second = new Workshop(request.secondTitle(), request.secondCapacity());
        Workshop alias = second; // Assignment copies the reference, not the object.
        Workshop independent = new Workshop(request.secondTitle(), request.secondCapacity());
        int firstAccepted = registerRepeatedly(first, firstAttempts);
        int aliasAccepted = registerRepeatedly(alias, aliasAttempts);
        return new WorkshopResult(snapshot(first), snapshot(second), snapshot(independent),
                alias == second, independent == second,
                firstAccepted, firstAttempts - firstAccepted,
                aliasAccepted, aliasAttempts - aliasAccepted, Workshop.MAX_CAPACITY);
    }

    public BankResult simulateBank(BankRequest request) {
        if (request == null || request.source() == null || request.destination() == null) {
            throw new IllegalArgumentException("source and destination are required");
        }
        checkActions(request.actions());
        for (BankAction action : request.actions()) {
            if (action == null || action.type() == null ||
                    !List.of("deposit", "withdraw", "transfer").contains(action.type()) ||
                    action.amountVnd() == null) {
                throw new IllegalArgumentException("each bank action needs type (deposit/withdraw/transfer) and amountVnd");
            }
        }
        BankAccount source = account(request.source());
        BankAccount destination = account(request.destination());
        if (source.accountNumber().equals(destination.accountNumber())) {
            throw new IllegalArgumentException("source and destination must use distinct accountNumbers");
        }
        BankSnapshot initialSource = snapshot(source);
        BankSnapshot initialDestination = snapshot(destination);
        List<BankStep> steps = new ArrayList<>();
        int index = 1;
        for (BankAction action : request.actions()) {
            BankSnapshot beforeSource = snapshot(source), beforeDestination = snapshot(destination);
            boolean accepted = true;
            String detail = "completed";
            try {
                switch (action.type()) {
                    case "deposit" -> source.deposit(action.amountVnd());
                    case "withdraw" -> source.withdraw(action.amountVnd());
                    case "transfer" -> source.transferTo(destination, action.amountVnd());
                    default -> throw new IllegalArgumentException("unknown bank action");
                }
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException ex) {
                accepted = false;
                detail = ex.getMessage();
            }
            steps.add(new BankStep(index++, action.type(), accepted, detail,
                    beforeSource, beforeDestination, snapshot(source), snapshot(destination)));
        }
        return new BankResult(initialSource, initialDestination, List.copyOf(steps),
                snapshot(source), snapshot(destination),
                "IN_MEMORY_SINGLE_REQUEST_SINGLE_THREAD_NOT_REAL_BANKING");
    }

    private static void checkActions(List<?> actions) {
        if (actions == null || actions.isEmpty() || actions.size() > MAX_STEPS) {
            throw new IllegalArgumentException("actions must contain 1-20 steps");
        }
    }

    private static int checkedAttempts(Integer count, String field) {
        if (count == null || count < 0 || count > MAX_REGISTRATION_ATTEMPTS) {
            throw new IllegalArgumentException(field + " must be between 0 and " + MAX_REGISTRATION_ATTEMPTS);
        }
        return count;
    }

    private static int registerRepeatedly(Workshop workshop, int attempts) {
        int successes = 0;
        for (int i = 0; i < attempts; i++) {
            if (workshop.register()) successes++;
        }
        return successes;
    }

    private static BankAccount account(BankSpec spec) {
        if (spec.openingBalanceVnd() == null) {
            throw new IllegalArgumentException("openingBalanceVnd is required");
        }
        return new BankAccount(spec.accountNumber(), spec.holderName(), spec.openingBalanceVnd());
    }

    private static InventorySnapshot snapshot(InventoryItem item) {
        return new InventorySnapshot(item.sku(), item.name(), item.unitPriceVnd(),
                item.quantity(), item.totalValueVnd());
    }

    private static WorkshopSnapshot snapshot(Workshop item) {
        return new WorkshopSnapshot(item.title(), item.capacity(), item.registered(), item.remainingSeats());
    }

    private static BankSnapshot snapshot(BankAccount account) {
        return new BankSnapshot(account.accountNumber(), account.holderName(), account.balanceVnd());
    }
}

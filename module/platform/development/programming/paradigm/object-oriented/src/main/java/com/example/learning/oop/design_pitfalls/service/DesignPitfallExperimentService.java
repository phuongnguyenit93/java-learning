package com.example.learning.oop.design_pitfalls.service;

import com.example.learning.oop.design_pitfalls.model.CheckoutComparison;
import com.example.learning.oop.design_pitfalls.model.MutableKeyDemonstration;
import com.example.learning.oop.design_pitfalls.model.SeatInventory;
import static com.example.learning.oop.design_pitfalls.dto.DesignPitfallDtos.*;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Request-local demonstrations, never shared between HTTP calls. */
@Service
public final class DesignPitfallExperimentService {
    public SeatsResult seats(SeatsRequest input) {
        if (input == null || input.capacity() == null || input.actions() == null ||
                input.actions().isEmpty() || input.actions().size() > 20) {
            throw new IllegalArgumentException("capacity and 1..20 seat actions are required");
        }
        for (SeatAction action : input.actions()) {
            if (action == null || action.type() == null || action.seats() == null ||
                    !List.of("RESERVE", "CANCEL").contains(action.type())) {
                throw new IllegalArgumentException("seat action must have type RESERVE/CANCEL and seats");
            }
        }
        SeatInventory inventory = new SeatInventory(input.capacity());
        SeatState initial = snapshot(inventory);
        List<SeatStep> steps = new ArrayList<>();
        int index = 1;
        for (SeatAction action : input.actions()) {
            SeatState before = snapshot(inventory);
            boolean accepted = true;
            String detail = "completed";
            try {
                switch (action.type()) {
                    case "RESERVE" -> inventory.reserve(action.seats());
                    case "CANCEL" -> inventory.cancel(action.seats());
                    default -> throw new IllegalArgumentException("unsupported action type");
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                accepted = false;
                detail = exception.getMessage();
            }
            steps.add(new SeatStep(index++, action.type(), action.seats(),
                    accepted, detail, before, snapshot(inventory)));
        }
        SeatState current = snapshot(inventory);
        boolean invariant = current.reserved() >= 0 && current.reserved() <= current.capacity()
                && current.available() == current.capacity() - current.reserved();
        if (!invariant) throw new IllegalStateException("seat invariant failed");
        return new SeatsResult(initial, List.copyOf(steps), current,
                true, "REQUEST_LOCAL_SINGLE_JVM_IN_MEMORY_NO_DURABLE_RESERVATIONS");
    }

    public MutableKeyResult mutableKey(MutableKeyRequest input) {
        if (input == null) throw new IllegalArgumentException("mutable-key input is required");
        var result = MutableKeyDemonstration.observe(input.originalName(), input.changedName());
        return new MutableKeyResult(result.originalName(), result.changedName(),
                result.originalHash(), result.newHash(),
                result.unsafeFoundBeforeRename(), result.unsafeFoundAfterRename(),
                result.lookupByOldNameAfterRename(), result.unsafeMapEntries(),
                result.stableFoundBeforeRename(), result.stableFoundAfterRename(),
                result.stableProfileNameAfterRename(), result.stableMapEntries());
    }

    public CheckoutResult checkout(CheckoutRequest input) {
        if (input == null || input.unitPriceVnd() == null || input.quantity() == null
                || input.availableUnits() == null || input.member() == null) {
            throw new IllegalArgumentException("sku, unitPriceVnd, quantity, availableUnits, member, deliveryMode are required");
        }
        var order = new CheckoutComparison.Input(input.sku(), input.unitPriceVnd(),
                input.quantity(), input.availableUnits(), input.member(), input.deliveryMode());
        var compared = CheckoutComparison.evaluate(order);
        if (!compared.sameResult()) throw new IllegalStateException("checkout refactor changed observable behavior");
        return new CheckoutResult(quote(compared.legacy()), quote(compared.refactored()),
                true, compared.collaborationTrace(),
                "PURE_IN_MEMORY_QUOTE_NO_STOCK_RESERVATION_PAYMENT_OR_PERSISTENCE");
    }

    private static SeatState snapshot(SeatInventory inventory) {
        SeatInventory.Snapshot s = inventory.snapshot();
        return new SeatState(s.capacity(), s.reserved(), s.available());
    }

    private static QuoteView quote(CheckoutComparison.Quote quote) {
        return new QuoteView(quote.subtotalVnd(), quote.discountVnd(),
                quote.deliveryVnd(), quote.payableVnd());
    }
}

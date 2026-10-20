package com.example.learning.oop.encapsulation.service;

import com.example.learning.oop.encapsulation.model.CartLine;
import com.example.learning.oop.encapsulation.model.EncapsulatedCart;
import com.example.learning.oop.encapsulation.model.StockLedger;
import com.example.learning.oop.encapsulation.model.EventSnapshot;
import com.example.learning.oop.encapsulation.model.LifecycleOrder;
import static com.example.learning.oop.encapsulation.dto.EncapsulationDtos.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.stereotype.Service;

/** Every simulation is isolated to one request; no shared balances, orders or persistence. */
@Service
public final class EncapsulationExperimentService {
    private static final int MAX_STEPS = 20;
    private static final String REQUEST_LOCAL = "REQUEST_LOCAL_IN_MEMORY_NO_PERSISTENCE";

    public CartResult simulateCart(CartRequest request) {
        if (request == null || request.initialLines() == null) {
            throw new IllegalArgumentException("initialLines is required");
        }
        checkActions(request.actions());
        List<CartLine> initial = new ArrayList<>();
        for (CartLineInput line : request.initialLines()) initial.add(line(line));
        for (CartAction action : request.actions()) {
            if (action == null || !List.of("put", "changeQuantity", "remove").contains(action.type())) {
                throw new IllegalArgumentException("cart action type must be put/changeQuantity/remove");
            }
            if (action.type().equals("put")) {
                if (action.unitPriceVnd() == null || action.quantity() == null) {
                    throw new IllegalArgumentException("put needs sku, unitPriceVnd and quantity");
                }
            } else if (action.type().equals("changeQuantity")) {
                if (action.quantity() == null) throw new IllegalArgumentException("quantity is required");
            }
        }

        EncapsulatedCart cart = new EncapsulatedCart(initial);
        CartState initialState = cartState(cart);
        List<CartStep> steps = new ArrayList<>();
        int index = 1;
        for (CartAction action : request.actions()) {
            CartState before = cartState(cart);
            boolean accepted = true;
            String detail = "completed";
            try {
                switch (action.type()) {
                    case "put" -> cart.putLine(line(new CartLineInput(
                            action.sku(), action.unitPriceVnd(), action.quantity())));
                    case "changeQuantity" -> cart.changeQuantity(action.sku(), action.quantity());
                    case "remove" -> cart.removeLine(action.sku());
                    default -> throw new IllegalArgumentException("unsupported action");
                }
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException error) {
                accepted = false;
                detail = error.getMessage();
            }
            steps.add(new CartStep(index++, action.type(), accepted, detail, before, cartState(cart)));
        }
        return new CartResult(initialState, List.copyOf(steps), cartState(cart), REQUEST_LOCAL);
    }

    public StockResult simulateStock(StockRequest request) {
        if (request == null || request.initialOnHand() == null || request.initialReserved() == null) {
            throw new IllegalArgumentException("sku, initialOnHand and initialReserved are required");
        }
        checkActions(request.actions());
        for (StockAction action : request.actions()) {
            if (action == null || !List.of("restock", "reserve", "release", "ship").contains(action.type())
                    || action.units() == null) {
                throw new IllegalArgumentException("stock action needs a supported type and units");
            }
        }
        StockLedger stock = new StockLedger(request.sku(), request.initialOnHand(), request.initialReserved());
        StockState initial = stockState(stock);
        List<StockStep> steps = new ArrayList<>();
        int index = 1;
        for (StockAction action : request.actions()) {
            StockState before = stockState(stock);
            boolean accepted = true;
            String detail = "completed";
            try {
                switch (action.type()) {
                    case "restock" -> stock.restock(action.units());
                    case "reserve" -> stock.reserve(action.units());
                    case "release" -> stock.release(action.units());
                    case "ship" -> stock.ship(action.units());
                    default -> throw new IllegalArgumentException("unsupported action");
                }
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException error) {
                accepted = false;
                detail = error.getMessage();
            }
            steps.add(new StockStep(index++, action.type(), accepted, detail, before, stockState(stock)));
        }
        return new StockResult(initial, List.copyOf(steps), stockState(stock));
    }

    public EventResult simulateEvent(EventRequest request) {
        if (request == null || request.startEpochMillis() == null ||
                request.mutatedExternalEpochMillis() == null || request.attendeeNames() == null ||
                request.attendeeNames().isEmpty() || request.attendeeNames().size() > 19) {
            throw new IllegalArgumentException("dates and 1..19 initial attendeeNames are required");
        }
        List<EventSnapshot.AttendeeDraft> draft = new ArrayList<>();
        for (String name : request.attendeeNames()) {
            draft.add(new EventSnapshot.AttendeeDraft(name));
        }
        // Pre-validate the mutations before creating the snapshot.
        if (request.renamedFirst() == null || request.addedAttendee() == null) {
            throw new IllegalArgumentException("renamedFirst and addedAttendee are required");
        }
        new EventSnapshot.AttendeeDraft(request.renamedFirst());
        new EventSnapshot.AttendeeDraft(request.addedAttendee());

        Date mutableDate = new Date(request.startEpochMillis());
        EventState before = draftState(mutableDate, draft);
        EventSnapshot frozen = new EventSnapshot(mutableDate, draft);
        mutableDate.setTime(request.mutatedExternalEpochMillis());
        draft.get(0).rename(request.renamedFirst());
        draft.add(new EventSnapshot.AttendeeDraft(request.addedAttendee()));
        EventState changedDraft = draftState(mutableDate, draft);
        EventState saved = eventState(frozen);

        // Attempt mutation through a returned legacy Date copy and list accessor.
        Date returnedDate = frozen.startsAt();
        returnedDate.setTime(request.mutatedExternalEpochMillis());
        boolean rejected = false;
        try {
            frozen.attendeeNames().add("UNAUTHORIZED");
        } catch (UnsupportedOperationException exception) {
            rejected = true;
        }
        return new EventResult(before, changedDraft, saved, eventState(frozen), rejected);
    }

    public OrderResult simulateOrder(OrderRequest request) {
        if (request == null || request.items() == null) {
            throw new IllegalArgumentException("orderId and items are required");
        }
        checkActions(request.actions());
        List<LifecycleOrder.OrderItem> items = new ArrayList<>();
        for (OrderItemInput item : request.items()) {
            if (item == null || item.quantity() == null) {
                throw new IllegalArgumentException("each item requires sku and quantity");
            }
            items.add(new LifecycleOrder.OrderItem(item.sku(), item.quantity()));
        }
        for (OrderAction action : request.actions()) {
            if (action == null || !List.of("markPaid", "ship", "cancel").contains(action.type())) {
                throw new IllegalArgumentException("order actions must be markPaid/ship/cancel");
            }
        }
        LifecycleOrder order = new LifecycleOrder(request.orderId(), items);
        OrderState initial = orderState(order);
        List<OrderStep> steps = new ArrayList<>();
        int index = 1;
        for (OrderAction action : request.actions()) {
            OrderState before = orderState(order);
            boolean accepted = true;
            String detail = "completed";
            try {
                switch (action.type()) {
                    case "markPaid" -> order.markPaid();
                    case "ship" -> order.ship();
                    case "cancel" -> order.cancel();
                    default -> throw new IllegalArgumentException("unsupported action");
                }
            } catch (IllegalArgumentException | IllegalStateException exception) {
                accepted = false;
                detail = exception.getMessage();
            }
            steps.add(new OrderStep(index++, action.type(), accepted, detail, before, orderState(order)));
        }
        return new OrderResult(initial, List.copyOf(steps), orderState(order),
                "SIMULATED_ORDER_NO_REAL_PAYMENT_OR_SHIPPING");
    }

    private static void checkActions(List<?> actions) {
        if (actions == null || actions.isEmpty() || actions.size() > MAX_STEPS) {
            throw new IllegalArgumentException("actions must contain 1..20 steps");
        }
    }

    private static CartLine line(CartLineInput input) {
        if (input == null || input.unitPriceVnd() == null || input.quantity() == null) {
            throw new IllegalArgumentException("cart lines require sku, unitPriceVnd, quantity");
        }
        return new CartLine(input.sku(), input.unitPriceVnd(), input.quantity());
    }

    private static CartState cartState(EncapsulatedCart cart) {
        List<CartLineState> lines = cart.lines().stream()
                .map(l -> new CartLineState(l.sku(), l.unitPriceVnd(), l.quantity(), l.subtotalVnd()))
                .toList();
        return new CartState(lines, cart.totalVnd());
    }

    private static StockState stockState(StockLedger stock) {
        return new StockState(stock.sku(), stock.onHand(), stock.reserved(), stock.available());
    }

    private static EventState draftState(Date date, List<EventSnapshot.AttendeeDraft> draft) {
        return new EventState(date.getTime(), draft.stream().map(EventSnapshot.AttendeeDraft::name).toList());
    }

    private static EventState eventState(EventSnapshot snapshot) {
        return new EventState(snapshot.startsAt().getTime(), snapshot.attendeeNames());
    }

    private static OrderState orderState(LifecycleOrder order) {
        List<OrderItemInput> items = order.items().stream()
                .map(i -> new OrderItemInput(i.sku(), i.quantity())).toList();
        return new OrderState(order.orderId(), order.status().name(), items);
    }
}

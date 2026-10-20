package com.example.learning.oop.class_design.service;

import com.example.learning.oop.class_design.model.CartLine;
import com.example.learning.oop.class_design.model.CheckedOutOrder;
import com.example.learning.oop.class_design.model.ShoppingCart;
import com.example.learning.oop.class_design.repository.InMemoryOrderRepository;
import com.example.learning.oop.class_design.repository.InMemoryReceiptOutbox;
import org.springframework.stereotype.Service;
import java.util.Objects;
import static com.example.learning.oop.class_design.dto.ClassDesignDtos.*;

/** Coordinates checkout; Cart owns subtotal, repository stores, outbox records side effects. */
@Service
public class CheckoutScenarioService {
    public CheckoutResult checkout(CheckoutRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.orderId() == null || request.orderId().isBlank()
                || request.receiptAddress() == null || request.receiptAddress().isBlank()
                || request.discountPercent() < 0 || request.discountPercent() > 100
                || request.lines() == null || request.lines().isEmpty()
                || request.lines().size() > 100) {
            throw new IllegalArgumentException("Invalid checkout input");
        }
        ShoppingCart cart = new ShoppingCart();
        for (CartLineInput line : request.lines()) {
            if (line == null) {
                throw new IllegalArgumentException("Cart line cannot be null");
            }
            cart.add(new CartLine(line.sku(), line.quantity(), line.unitPriceCents()));
        }
        long subtotal = cart.subtotalCents();
        // Division before multiplication avoids overflow and retains integer-cent rounding.
        long discount = Math.addExact(
                Math.multiplyExact(subtotal / 100, request.discountPercent()),
                (subtotal % 100) * request.discountPercent() / 100);
        long total = Math.subtractExact(subtotal, discount);
        CheckedOutOrder order = new CheckedOutOrder(request.orderId(), cart.snapshot(),
                subtotal, discount, total);

        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        InMemoryReceiptOutbox outbox = new InMemoryReceiptOutbox();
        repository.save(order);
        outbox.enqueue(new ReceiptEvent(order.orderId(), request.receiptAddress(), order.totalCents()));
        CheckedOutOrder persisted = repository.find(order.orderId());
        return new CheckoutResult(persisted.orderId(), persisted.subtotalCents(),
                persisted.discountCents(), persisted.totalCents(),
                persisted.lines().stream().map(line ->
                        new CartLineInput(line.sku(), line.quantity(), line.unitPriceCents())).toList(),
                outbox.snapshot());
    }
}

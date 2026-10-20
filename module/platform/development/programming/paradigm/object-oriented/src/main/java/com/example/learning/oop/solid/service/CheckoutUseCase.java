package com.example.learning.oop.solid.service;

import com.example.learning.oop.solid.model.CheckoutReceipt;
import com.example.learning.oop.solid.model.DiscountPricing;
import com.example.learning.oop.solid.model.PurchaseOrder;
import com.example.learning.oop.solid.model.OrderPorts.OrderWriter;
import com.example.learning.oop.solid.model.OrderPorts.ReceiptSender;
import java.util.Objects;

/** High-level use case: depends on policy/port contracts, not adapter classes. */
public final class CheckoutUseCase {
    private final DiscountPricing pricing;
    private final OrderWriter writer;
    private final ReceiptSender sender;

    public CheckoutUseCase(DiscountPricing pricing, OrderWriter writer, ReceiptSender sender) {
        this.pricing = Objects.requireNonNull(pricing);
        this.writer = Objects.requireNonNull(writer);
        this.sender = Objects.requireNonNull(sender);
    }

    public CheckoutReceipt checkout(PurchaseOrder order, String policyCode) {
        Objects.requireNonNull(order, "order");
        // Validate all domain calculations and policy contracts before any side effect.
        DiscountPricing.Quote quote = pricing.quote(policyCode, order.subtotalCents());
        CheckoutReceipt receipt = new CheckoutReceipt(order.id(), policyCode,
                quote.subtotalCents(), quote.discountCents(), quote.payableCents());
        writer.save(receipt);
        sender.send(receipt);
        return receipt;
    }
}

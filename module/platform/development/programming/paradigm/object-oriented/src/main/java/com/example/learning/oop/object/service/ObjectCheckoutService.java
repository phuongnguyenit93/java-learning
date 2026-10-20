package com.example.learning.oop.object.service;

import com.example.learning.oop.object.dto.ObjectExperimentDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** One isolated, synchronous checkout simulation per call; no real money or inventory. */
@Service
public class ObjectCheckoutService {

    public ObjectExperimentDto.CheckoutResult simulate(ObjectExperimentDto.CheckoutRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        String sku = ObjectSemanticsService.requiredText("sku", request.sku());
        int initialStock = ObjectSemanticsService.boundedInt("startingStock", request.startingStock(), 0, 1_000);
        int quantity = ObjectSemanticsService.boundedInt("quantity", request.quantity(), 1, 100);
        if (request.paymentApproved() == null) {
            throw new IllegalArgumentException("paymentApproved must be true or false");
        }
        BigDecimal unitPrice = request.unitPrice();
        if (unitPrice == null || unitPrice.compareTo(new BigDecimal("0.01")) < 0
                || unitPrice.compareTo(new BigDecimal("1000000")) > 0
                || unitPrice.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("unitPrice must be between 0.01 and 1000000 with at most 2 decimal places");
        }

        Cart cart = new Cart(sku, quantity, unitPrice);
        Inventory inventory = new Inventory(initialStock);
        PaymentGateway payment = new PaymentGateway(request.paymentApproved());
        Notifier notifier = new Notifier();
        Trace trace = new Trace();

        trace.add("Client", "checkout(cart)", "Request enters OrderService", inventory.available());
        BigDecimal total = cart.total();
        trace.add("Cart", "total()", total.toPlainString(), inventory.available());

        if (!inventory.reserve(cart.quantity())) {
            trace.add("Inventory", "reserve()", "Rejected: not enough stock", inventory.available());
            return result("STOCK_REJECTED", null, total, initialStock, inventory, notifier, trace);
        }
        trace.add("Inventory", "reserve()", "Reserved " + quantity + " item(s)", inventory.available());

        String transaction;
        try {
            transaction = payment.pay(total);
            trace.add("PaymentGateway", "pay()", "Approved: " + transaction, inventory.available());
        } catch (PaymentDeclinedException ex) {
            trace.add("PaymentGateway", "pay()", "Rejected by simulated gateway", inventory.available());
            inventory.release(cart.quantity());
            trace.add("Inventory", "release()", "Restored reservation after definite rejection", inventory.available());
            return result("PAYMENT_DECLINED", null, total, initialStock, inventory, notifier, trace);
        }

        Order order = new Order("DEMO-ORDER", transaction, total);
        trace.add("OrderService", "new Order()", "Created local simulated order", inventory.available());
        notifier.send(order);
        trace.add("Notifier", "send(order)", "Simulated notification (not transmitted)", inventory.available());
        return result("COMPLETED", order, total, initialStock, inventory, notifier, trace);
    }

    private static ObjectExperimentDto.CheckoutResult result(
            String outcome, Order order, BigDecimal total, int initialStock,
            Inventory inventory, Notifier notifier, Trace trace) {
        return new ObjectExperimentDto.CheckoutResult(
                outcome,
                true,
                order == null ? null : order.orderId(),
                order == null ? null : order.transactionId(),
                total,
                initialStock,
                inventory.available(),
                notifier.wasSent(),
                trace.snapshot());
    }

    private record Cart(String sku, int quantity, BigDecimal unitPrice) {
        BigDecimal total() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    private static final class Inventory {
        private int available;

        private Inventory(int startingStock) {
            this.available = startingStock;
        }

        int available() {
            return available;
        }

        boolean reserve(int count) {
            if (count > available) {
                return false;
            }
            available -= count;
            return true;
        }

        void release(int count) {
            available += count;
        }
    }

    private static final class PaymentGateway {
        private final boolean approved;

        private PaymentGateway(boolean approved) {
            this.approved = approved;
        }

        String pay(BigDecimal amount) {
            if (!approved) {
                throw new PaymentDeclinedException();
            }
            return "DEMO-TXN";
        }
    }

    private static final class PaymentDeclinedException extends RuntimeException {
    }

    private record Order(String orderId, String transactionId, BigDecimal total) {
    }

    private static final class Notifier {
        private boolean sent;

        void send(Order order) {
            sent = true;
        }

        boolean wasSent() {
            return sent;
        }
    }

    private static final class Trace {
        private final List<ObjectExperimentDto.WorkflowEvent> events = new ArrayList<>();

        void add(String actor, String operation, String detail, int currentStock) {
            events.add(new ObjectExperimentDto.WorkflowEvent(
                    events.size() + 1, actor, operation, detail, currentStock));
        }

        List<ObjectExperimentDto.WorkflowEvent> snapshot() {
            return List.copyOf(events);
        }
    }
}

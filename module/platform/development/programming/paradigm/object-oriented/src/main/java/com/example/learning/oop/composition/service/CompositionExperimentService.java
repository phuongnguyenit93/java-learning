package com.example.learning.oop.composition.service;

import com.example.learning.oop.composition.dto.CompositionDtos.*;
import com.example.learning.oop.composition.model.NotificationPipeline;
import com.example.learning.oop.composition.model.OrderPlacement;
import com.example.learning.oop.composition.model.ResourceOwnership;
import com.example.learning.oop.composition.model.SwitchablePricing;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class CompositionExperimentService {
    public NotificationResponse notification(NotificationRequest request) {
        if (request == null || request.channel() == null || request.format() == null) {
            throw new IllegalArgumentException("format and channel are required");
        }
        requireId(request.orderId(), "orderId");
        requireId(request.recipient(), "recipient");
        if (request.totalCents() < 0 || request.totalCents() > 100_000_000L) {
            throw new IllegalArgumentException("totalCents must be 0-100000000");
        }
        List<String> trace = NotificationPipeline.freshTrace();
        NotificationPipeline.Formatter formatter = switch (request.format()) {
            case FULL -> new NotificationPipeline.FullFormatter();
            case COMPACT -> new NotificationPipeline.CompactFormatter();
        };
        NotificationPipeline.Transport transport = new NotificationPipeline.RecordingTransport(
                request.channel().name(), request.failTransport(), trace);
        if (request.audited()) transport = new NotificationPipeline.AuditedTransport(transport, trace);
        NotificationPipeline.Notifier notifier = new NotificationPipeline.Notifier(formatter, transport, trace);
        NotificationPipeline.Notifier.Outcome outcome =
                notifier.notifyPaid(request.recipient(), request.orderId(), request.totalCents());
        return new NotificationResponse(request.channel().name(), outcome.content(),
                outcome.accepted(), outcome.failure(), List.copyOf(trace));
    }

    public PolicyResponse policies(PolicyRequest request) {
        if (request == null || request.steps() == null || request.steps().isEmpty()
                || request.steps().size() > 20) {
            throw new IllegalArgumentException("steps must contain 1-20 entries");
        }
        SwitchablePricing.Policy initial = SwitchablePricing.create(request.initialPolicy(),
                request.initialPercent(), request.initialDiscountCents());
        // Validate the whole input before executing any changes.
        for (PolicyStep step : request.steps()) {
            if (step == null || step.action() == null) {
                throw new IllegalArgumentException("Each step must have an action");
            }
            switch (step.action()) {
                case SWITCH, QUOTE_WITH_POLICY -> SwitchablePricing.create(
                        step.policy(), step.percent(), step.discountCents());
                case QUOTE -> { }
            }
            if (step.action() != PolicyAction.SWITCH) {
                if (step.subtotalCents() == null || step.subtotalCents() < 0
                        || step.subtotalCents() > 100_000_000L) {
                    throw new IllegalArgumentException("Quote subtotalCents must be 0-100000000");
                }
            }
        }
        // This pricing instance exists only during this HTTP request, not across users.
        SwitchablePricing pricing = new SwitchablePricing(initial);
        List<PolicyStepResult> timeline = new ArrayList<>();
        for (int i = 0; i < request.steps().size(); i++) {
            PolicyStep step = request.steps().get(i);
            SwitchablePricing.Quote quote = null;
            switch (step.action()) {
                case SWITCH -> pricing.replace(SwitchablePricing.create(
                        step.policy(), step.percent(), step.discountCents()));
                case QUOTE -> quote = pricing.quote(step.subtotalCents());
                case QUOTE_WITH_POLICY -> quote = pricing.quoteWith(step.subtotalCents(),
                        SwitchablePricing.create(step.policy(), step.percent(), step.discountCents()));
            }
            timeline.add(new PolicyStepResult(i + 1, step.action(), pricing.activeName(),
                    quote == null ? null : quote.usedPolicy(),
                    quote == null ? null : step.subtotalCents(),
                    quote == null ? null : quote.payableCents()));
        }
        return new PolicyResponse(initial.name(), pricing.activeName(), List.copyOf(timeline));
    }

    public PlaceOrderResponse placeOrder(PlaceOrderRequest request) {
        if (request == null || request.lines() == null || request.lines().isEmpty()
                || request.lines().size() > 20) {
            throw new IllegalArgumentException("Order requires 1-20 lines");
        }
        List<OrderPlacement.Line> lines = new ArrayList<>();
        for (OrderLineInput input : request.lines()) {
            if (input == null) throw new IllegalArgumentException("Order lines cannot contain null");
            lines.add(new OrderPlacement.Line(input.sku(), input.quantity(), input.unitPriceCents()));
        }
        OrderPlacement.Order order = OrderPlacement.Order.create(request.orderId(), lines);
        OrderPlacement.InMemoryOrderRepository storage = new OrderPlacement.InMemoryOrderRepository(
                request.failStorage());
        OrderPlacement.OrderService service = new OrderPlacement.OrderService(storage);
        boolean accepted = false;
        String failure = null;
        try {
            service.place(order);
            accepted = true;
        } catch (IllegalStateException ex) {
            failure = ex.getMessage();
        }
        List<PlacedOrder> stored = storage.findAll().stream()
                .map(o -> new PlacedOrder(o.id(), o.totalCents(), o.lines().stream()
                        .map(line -> new OrderLineInput(line.sku(), line.quantity(), line.unitPriceCents()))
                        .toList())).toList();
        return new PlaceOrderResponse(accepted, failure, storage.saveAttempts(), stored,
                "IN_MEMORY_ONLY; no durable persistence or transaction implied");
    }

    public ResourceResponse resource(ResourceRequest request) {
        if (request == null || request.ownership() == null) {
            throw new IllegalArgumentException("ownership is required");
        }
        if (request.content() == null || request.content().isBlank() || request.content().length() > 100) {
            throw new IllegalArgumentException("content must be nonblank and at most 100 characters");
        }
        List<String> trace = new ArrayList<>();
        ResourceOwnership.TrackedHandle handle = new ResourceOwnership.TrackedHandle(
                trace, request.failWrite());
        ResourceOwnership.Exporter exporter = null;
        String failure = null;
        try (ResourceOwnership.Exporter ownedScope = new ResourceOwnership.Exporter(
                handle, request.ownership() == Ownership.OWNED, trace)) {
            exporter = ownedScope;
            ownedScope.export(request.content());
        } catch (IllegalStateException ex) {
            failure = ex.getMessage();
        }
        boolean afterExporter = handle.closed();
        // If caller lent the resource, the caller (here, the service) closes it.
        if (request.ownership() == Ownership.BORROWED) handle.close();
        return new ResourceResponse(request.ownership(), exporter != null && exporter.closed(),
                afterExporter, handle.closed(), failure, List.copyOf(trace));
    }

    private static void requireId(String id, String field) {
        if (id == null || !id.matches("[A-Za-z0-9_@.\\-]{1,60}")) {
            throw new IllegalArgumentException(field + " must be 1-60 ASCII letters/digits or _ @ . -");
        }
    }
}

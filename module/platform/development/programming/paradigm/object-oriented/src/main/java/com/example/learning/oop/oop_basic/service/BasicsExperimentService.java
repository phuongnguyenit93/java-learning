package com.example.learning.oop.oop_basic.service;

import com.example.learning.oop.oop_basic.dto.BasicOopDtos.AccountActionInput;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.AccountActionResult;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.AccountSimulationRequest;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.AccountSimulationResponse;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.OrderSimulationRequest;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.OrderSimulationResponse;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.OrderStepInput;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.OrderStepResult;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.ShippingQuote;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.ShippingQuoteRequest;
import com.example.learning.oop.oop_basic.dto.BasicOopDtos.ShippingQuoteResponse;
import com.example.learning.oop.oop_basic.model.BankAccount;
import com.example.learning.oop.oop_basic.model.OrderAggregate;
import com.example.learning.oop.oop_basic.model.ShippingPolicy;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class BasicsExperimentService {
    private static final int MAX_STEPS = 20;
    private static final List<ShippingPolicy> SHIPPING_POLICIES =
            List.of(new ShippingPolicy.Pickup(), new ShippingPolicy.Courier());

    public AccountSimulationResponse simulateAccount(AccountSimulationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        requireSteps(request.actions(), "actions");
        BankAccount account = new BankAccount(request.accountId(), request.openingBalanceCents());
        List<AccountActionResult> history = new ArrayList<>();
        for (int i = 0; i < request.actions().size(); i++) {
            AccountActionInput operation = request.actions().get(i);
            if (operation == null || operation.action() == null) {
                throw new IllegalArgumentException("Every account action must have an action type");
            }
            long before = account.balanceCents();
            boolean accepted = false;
            String reason = null;
            try {
                switch (operation.action()) {
                    case DEPOSIT -> {
                        account.deposit(operation.amountCents());
                        accepted = true;
                    }
                    case WITHDRAW -> {
                        accepted = account.withdraw(operation.amountCents());
                        if (!accepted) {
                            reason = "Insufficient balance";
                        }
                    }
                }
            } catch (IllegalArgumentException | ArithmeticException error) {
                reason = error instanceof ArithmeticException
                        ? "Balance would overflow" : error.getMessage();
            }
            history.add(new AccountActionResult(i + 1, operation.action(), operation.amountCents(),
                    before, account.balanceCents(), accepted, reason));
        }
        return new AccountSimulationResponse(account.accountId(), request.openingBalanceCents(),
                account.balanceCents(), List.copyOf(history));
    }

    public OrderSimulationResponse simulateOrder(OrderSimulationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        requireSteps(request.steps(), "steps");
        OrderAggregate order = new OrderAggregate(request.orderId());
        List<OrderStepResult> history = new ArrayList<>();
        for (int i = 0; i < request.steps().size(); i++) {
            OrderStepInput step = request.steps().get(i);
            if (step == null || step.action() == null) {
                throw new IllegalArgumentException("Every order step must have an action type");
            }
            String beforeStatus = order.status().name();
            long beforeTotal = order.totalCents();
            boolean accepted = false;
            String reason = null;
            try {
                switch (step.action()) {
                    case ADD_LINE -> {
                        if (step.quantity() == null || step.unitPriceCents() == null) {
                            throw new IllegalArgumentException(
                                    "ADD_LINE requires sku, quantity and unitPriceCents");
                        }
                        order.addLine(step.sku(), step.quantity(), step.unitPriceCents());
                    }
                    case CONFIRM -> order.confirm();
                    case CANCEL -> order.cancel();
                }
                accepted = true;
            } catch (IllegalStateException | IllegalArgumentException | ArithmeticException error) {
                reason = error instanceof ArithmeticException
                        ? "Order amount would overflow" : error.getMessage();
            }
            history.add(new OrderStepResult(i + 1, step.action(), beforeStatus,
                    order.status().name(), beforeTotal, order.totalCents(),
                    order.lines().size(), accepted, reason));
        }
        return new OrderSimulationResponse(order.orderId(), order.status().name(),
                order.totalCents(), List.copyOf(history));
    }

    public ShippingQuoteResponse compareShipping(ShippingQuoteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.subtotalCents() < 0) {
            throw new IllegalArgumentException("subtotalCents cannot be negative");
        }
        if (request.distanceKm() < 0 || request.distanceKm() > 100) {
            throw new IllegalArgumentException("distanceKm must be between 0 and 100");
        }
        List<ShippingQuote> quotes = new ArrayList<>();
        for (ShippingPolicy policy : SHIPPING_POLICIES) {
            try {
                long fee = policy.feeCents(request.distanceKm());
                quotes.add(new ShippingQuote(policy.name(), fee,
                        Math.addExact(request.subtotalCents(), fee)));
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException("Payable amount would overflow", error);
            }
        }
        return new ShippingQuoteResponse(request.subtotalCents(), request.distanceKm(),
                List.copyOf(quotes));
    }

    private static void requireSteps(List<?> steps, String label) {
        if (steps == null || steps.isEmpty() || steps.size() > MAX_STEPS) {
            throw new IllegalArgumentException(label + " must contain 1-20 entries");
        }
    }
}

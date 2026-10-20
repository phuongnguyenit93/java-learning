package com.example.learning.oop.polymorphism.service;

import com.example.learning.oop.polymorphism.model.*;
import static com.example.learning.oop.polymorphism.dto.PolymorphismDtos.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Every invocation is bounded and uses new, request-local objects. */
@Service
public final class PolymorphismExperimentsService {
    private static final String SCOPE = "REQUEST_LOCAL_SIMULATION_NO_REAL_PAYMENT_NO_DURABLE_STATE";

    public DispatchResult dispatch(DispatchRequest request) {
        if (request == null || request.weight() == null || request.bonus() == null) {
            throw new IllegalArgumentException("receiverType, eventType, weight and bonus are required");
        }
        DispatchMechanics.Observation o = DispatchMechanics.observe(
                request.receiverType(), request.eventType(), request.weight(),
                request.priority(), request.bonus());
        return new DispatchResult(o.receiverType(), o.eventType(),
                o.viaDeclaredEvent(), o.viaSpecificEvent(), o.overriddenInstanceMethod(),
                o.fieldViaBaseReference(), o.fieldViaConcreteReference(),
                o.staticViaBaseClass(), o.staticViaConcreteClass(), o.sameReceiverObject());
    }

    public PaymentsResult payments(PaymentsRequest request) {
        if (request == null || request.initialWalletBalanceVnd() == null ||
                request.cardLimitVnd() == null ||
                request.actions() == null || request.actions().isEmpty() ||
                request.actions().size() > 20) {
            throw new IllegalArgumentException("initialWalletBalanceVnd, cardLimitVnd and 1..20 actions are required");
        }

        WalletPaymentMethod wallet = new WalletPaymentMethod(request.initialWalletBalanceVnd());
        Map<String, PaymentMethod> methods = Map.of(
                "CARD", new CardPaymentMethod(request.cardLimitVnd()),
                "WALLET", wallet,
                "BANK", new BankTransferPaymentMethod());
        List<PaymentCommand> commands = new ArrayList<>();
        List<PaymentMethod> selected = new ArrayList<>();
        for (PaymentAction action : request.actions()) {
            if (action == null || action.method() == null || action.amountVnd() == null) {
                throw new IllegalArgumentException("all payment actions need method, orderId and amountVnd");
            }
            PaymentMethod method = methods.get(action.method());
            if (method == null) throw new IllegalArgumentException("method must be CARD, WALLET or BANK");
            selected.add(method);
            commands.add(new PaymentCommand(action.orderId(), action.amountVnd()));
        }

        PaymentCheckoutService checkout = new PaymentCheckoutService();
        List<PaymentStep> steps = new ArrayList<>();
        int paid = 0, declined = 0, pending = 0;
        for (int i = 0; i < commands.size(); i++) {
            PaymentCommand command = commands.get(i);
            PaymentMethod method = selected.get(i);
            long before = wallet.balanceVnd();
            PaymentOutcome outcome = checkout.checkout(method, command);
            switch (outcome.status()) {
                case PAID -> paid++;
                case DECLINED -> declined++;
                case PENDING -> pending++;
            }
            steps.add(new PaymentStep(i + 1, method.name(), command.orderId(),
                    command.amountVnd(), outcome.status().name(), outcome.detail(),
                    before, wallet.balanceVnd()));
        }
        return new PaymentsResult(request.initialWalletBalanceVnd(), wallet.balanceVnd(),
                List.copyOf(steps), paid, declined, pending, SCOPE);
    }
}

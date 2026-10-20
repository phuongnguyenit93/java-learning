package com.example.learning.oop.interface_chapter.service;

import com.example.learning.oop.interface_chapter.model.PaymentCommand;
import com.example.learning.oop.interface_chapter.model.PaymentGateway;
import com.example.learning.oop.interface_chapter.model.PaymentReceipt;
import com.example.learning.oop.interface_chapter.model.PaymentRepository;
import java.util.Objects;

/** Constructor-injected ports. Exactly-once only within this local request simulation. */
public final class CheckoutService {
    private final PaymentGateway gateway;
    private final PaymentRepository repository;
    private int gatewayCalls;

    public CheckoutService(PaymentGateway gateway, PaymentRepository repository) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public ProcessedPayment chargeOrReplay(PaymentCommand command) {
        Objects.requireNonNull(command, "command");
        var old = repository.find(command.requestId());
        if (old.isPresent()) {
            if (!old.get().matches(command)) {
                throw new IllegalStateException("requestId already used for different payment details");
            }
            return new ProcessedPayment(old.get(), true);
        }
        gatewayCalls++;
        PaymentReceipt receipt = gateway.charge(command);
        if (receipt == null || !receipt.matches(command)) {
            throw new IllegalStateException("gateway violated payment receipt contract");
        }
        repository.save(receipt);
        return new ProcessedPayment(receipt, false);
    }

    public int gatewayCalls() { return gatewayCalls; }

    public PaymentRepository repository() { return repository; }

    public record ProcessedPayment(PaymentReceipt receipt, boolean replayed) {}
}

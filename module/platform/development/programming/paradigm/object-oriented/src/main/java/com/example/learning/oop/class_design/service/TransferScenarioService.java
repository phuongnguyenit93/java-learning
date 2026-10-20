package com.example.learning.oop.class_design.service;

import com.example.learning.oop.class_design.model.BankAccount;
import com.example.learning.oop.class_design.model.Money;
import org.springframework.stereotype.Service;
import java.util.Currency;
import java.util.Objects;
import static com.example.learning.oop.class_design.dto.ClassDesignDtos.*;

/** Transfers between entity instances; exposes observable before and after snapshots. */
@Service
public class TransferScenarioService {
    public TransferResult transfer(TransferRequest request) {
        Objects.requireNonNull(request, "request");
        Currency currency = Currency.getInstance(request.currency());
        BankAccount sender = new BankAccount(request.fromAccountId(),
                new Money(request.fromBalanceCents(), currency));
        BankAccount recipient = new BankAccount(request.toAccountId(),
                new Money(request.toBalanceCents(), currency));
        long beforeSender = sender.balance().cents();
        long beforeRecipient = recipient.balance().cents();
        sender.transferTo(recipient, new Money(request.amountCents(), currency));
        return new TransferResult(sender.id(), recipient.id(), currency.getCurrencyCode(),
                beforeSender, beforeRecipient, sender.balance().cents(), recipient.balance().cents());
    }
}

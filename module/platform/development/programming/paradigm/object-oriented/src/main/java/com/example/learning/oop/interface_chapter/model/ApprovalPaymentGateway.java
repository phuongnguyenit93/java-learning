package com.example.learning.oop.interface_chapter.model;

public final class ApprovalPaymentGateway implements PaymentGateway {
    @Override public PaymentReceipt charge(PaymentCommand command) {
        if (command == null) throw new IllegalArgumentException("payment command is required");
        return new PaymentReceipt(command.requestId(), command.orderId(), command.amountVnd(),
                PaymentReceipt.Status.APPROVED);
    }
}

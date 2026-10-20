package com.example.learning.oop.interface_chapter.model;

public record PaymentReceipt(String requestId, String orderId, long amountVnd, Status status) {
    public enum Status { APPROVED, DECLINED }

    public PaymentReceipt {
        new PaymentCommand(requestId, orderId, amountVnd);
        if (status == null) throw new IllegalArgumentException("payment status is required");
    }

    public boolean matches(PaymentCommand command) {
        return requestId.equals(command.requestId())
                && orderId.equals(command.orderId())
                && amountVnd == command.amountVnd();
    }
}

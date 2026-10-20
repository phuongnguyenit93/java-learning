package com.example.learning.oop.polymorphism.dto;

import java.util.List;

/** JSON-facing immutable request and response structures. */
public final class PolymorphismDtos {
    private PolymorphismDtos() {}

    public record DispatchRequest(String receiverType, String eventType,
                                  Integer weight, Integer priority, Long bonus) {}
    public record DispatchResult(String receiverType, String eventType,
                                 long viaDeclaredEvent, long viaSpecificEvent,
                                 String overriddenInstanceMethod,
                                 String fieldViaBaseReference, String fieldViaConcreteReference,
                                 String staticViaBaseClass, String staticViaConcreteClass,
                                 boolean sameReceiverObject) {}

    public record PaymentAction(String method, String orderId, Long amountVnd) {}
    public record PaymentsRequest(Long initialWalletBalanceVnd, Long cardLimitVnd,
                                  List<PaymentAction> actions) {}
    public record PaymentStep(int index, String method, String orderId, long amountVnd,
                              String status, String detail,
                              long walletBalanceBeforeVnd, long walletBalanceAfterVnd) {}
    public record PaymentsResult(long initialWalletBalanceVnd, long finalWalletBalanceVnd,
                                 List<PaymentStep> steps, int paidCount,
                                 int declinedCount, int pendingCount, String scope) {}

    public record ErrorResponse(String error) {}
}

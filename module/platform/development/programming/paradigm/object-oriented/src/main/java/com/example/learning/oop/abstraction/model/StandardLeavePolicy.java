package com.example.learning.oop.abstraction.model;

import java.util.Objects;

public final class StandardLeavePolicy implements LeavePolicy {
    @Override
    public LeaveDecision evaluate(LeaveRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.requestedDays() > request.remainingDays()) {
            return new LeaveDecision(DecisionCode.INSUFFICIENT_BALANCE,
                    "Requested days exceed remaining balance");
        }
        return new LeaveDecision(DecisionCode.APPROVED, "Balance is sufficient");
    }
}

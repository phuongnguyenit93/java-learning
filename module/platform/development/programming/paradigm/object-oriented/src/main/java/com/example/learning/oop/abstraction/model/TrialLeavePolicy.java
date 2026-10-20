package com.example.learning.oop.abstraction.model;

import java.util.Objects;

public final class TrialLeavePolicy implements LeavePolicy {
    @Override
    public LeaveDecision evaluate(LeaveRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.requestedDays() > request.remainingDays()) {
            return new LeaveDecision(DecisionCode.INSUFFICIENT_BALANCE,
                    "Requested days exceed remaining balance");
        }
        if (request.requestedDays() > 2) {
            return new LeaveDecision(DecisionCode.EXCEEDS_TRIAL_LIMIT,
                    "Trial policy permits at most two days per request");
        }
        return new LeaveDecision(DecisionCode.APPROVED, "Trial rules are satisfied");
    }
}

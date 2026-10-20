package com.example.learning.oop.abstraction.model;

import java.util.Objects;

/**
 * Shared contract: evaluate valid immutable input, never change a leave balance,
 * never approve a request exceeding its balance, always return a non-null decision.
 */
public interface LeavePolicy {
    LeaveDecision evaluate(LeaveRequest request);

    record LeaveRequest(String employeeId, int requestedDays, int remainingDays) {
        public LeaveRequest {
            if (employeeId == null || employeeId.isBlank()
                    || requestedDays <= 0 || remainingDays < 0) {
                throw new IllegalArgumentException("Invalid leave request");
            }
            employeeId = employeeId.trim();
        }
    }

    enum DecisionCode {
        APPROVED, INSUFFICIENT_BALANCE, EXCEEDS_TRIAL_LIMIT
    }

    record LeaveDecision(DecisionCode code, String reason) {
        public LeaveDecision {
            Objects.requireNonNull(code, "code");
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Reason must not be blank");
            }
        }

        public boolean approved() {
            return code == DecisionCode.APPROVED;
        }
    }
}

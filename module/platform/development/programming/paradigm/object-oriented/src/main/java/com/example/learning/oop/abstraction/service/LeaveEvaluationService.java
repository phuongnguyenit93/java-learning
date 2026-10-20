package com.example.learning.oop.abstraction.service;

import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.LeaveEvaluationRequest;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.LeaveEvaluationResponse;
import com.example.learning.oop.abstraction.model.LeavePolicy;
import com.example.learning.oop.abstraction.model.StandardLeavePolicy;
import com.example.learning.oop.abstraction.model.TrialLeavePolicy;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public final class LeaveEvaluationService {
    public LeaveEvaluationResponse evaluate(LeaveEvaluationRequest request) {
        if (request == null || request.policy() == null || request.policy().isBlank()) {
            throw new IllegalArgumentException("Leave policy must be STANDARD or TRIAL");
        }
        String policyCode = request.policy().trim().toUpperCase(Locale.ROOT);
        LeavePolicy policy = switch (policyCode) {
            case "STANDARD" -> new StandardLeavePolicy();
            case "TRIAL" -> new TrialLeavePolicy();
            default -> throw new IllegalArgumentException("Unknown leave policy: " + request.policy());
        };
        LeavePolicy.LeaveRequest input = new LeavePolicy.LeaveRequest(
                request.employeeId(), request.requestedDays(), request.remainingDays());
        LeavePolicy.LeaveDecision decision = policy.evaluate(input);
        return new LeaveEvaluationResponse(policyCode, input.employeeId(),
                decision.code().name(), decision.approved(), decision.reason(), true);
    }
}

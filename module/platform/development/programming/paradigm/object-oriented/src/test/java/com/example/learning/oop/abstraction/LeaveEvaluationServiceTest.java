package com.example.learning.oop.abstraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.LeaveEvaluationRequest;
import com.example.learning.oop.abstraction.model.LeavePolicy;
import com.example.learning.oop.abstraction.model.StandardLeavePolicy;
import com.example.learning.oop.abstraction.model.TrialLeavePolicy;
import com.example.learning.oop.abstraction.service.LeaveEvaluationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeaveEvaluationServiceTest {
    private final LeaveEvaluationService service = new LeaveEvaluationService();

    @Test
    void sameValidInputMayYieldDifferentCompliantPolicyDecisions() {
        var standard = service.evaluate(new LeaveEvaluationRequest("STANDARD", "NV-01", 3, 5));
        var trial = service.evaluate(new LeaveEvaluationRequest("TRIAL", "NV-01", 3, 5));
        assertEquals("APPROVED", standard.decisionCode());
        assertTrue(standard.approved());
        assertEquals("EXCEEDS_TRIAL_LIMIT", trial.decisionCode());
        assertFalse(trial.approved());
        assertEquals("NV-01", trial.employeeId());
        assertTrue(trial.simulatedOnly());
    }

    @Test
    void commonContractNeverApprovesInsufficientBalance() {
        LeavePolicy.LeaveRequest insufficient = new LeavePolicy.LeaveRequest("NV-X", 6, 5);
        for (LeavePolicy policy : List.of(new StandardLeavePolicy(), new TrialLeavePolicy())) {
            var denied = policy.evaluate(insufficient);
            assertFalse(denied.approved());
            assertEquals(LeavePolicy.DecisionCode.INSUFFICIENT_BALANCE, denied.code());
            assertThrows(NullPointerException.class, () -> policy.evaluate(null));
            assertTrue(policy.evaluate(new LeavePolicy.LeaveRequest("NV-Y", 1, 3)).approved());
        }
        assertEquals(5, insufficient.remainingDays()); // no debit side effect
    }

    @Test
    void trialPolicyApprovesExactlyTwoWhenBalanceAllowsIt() {
        var decision = service.evaluate(new LeaveEvaluationRequest("trial", " NV-X ", 2, 2));
        assertEquals("TRIAL", decision.policy());
        assertTrue(decision.approved());
        assertEquals("NV-X", decision.employeeId());
    }

    @Test
    void invalidPolicyAndDataCannotProduceApproval() {
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.evaluate(new LeaveEvaluationRequest("OTHER", "NV-01", 1, 4)));
        assertThrows(IllegalArgumentException.class,
                () -> service.evaluate(new LeaveEvaluationRequest("TRIAL", " ", 1, 4)));
        assertThrows(IllegalArgumentException.class,
                () -> service.evaluate(new LeaveEvaluationRequest("STANDARD", "NV-01", 0, 4)));
        assertThrows(IllegalArgumentException.class,
                () -> service.evaluate(new LeaveEvaluationRequest("STANDARD", "NV-01", 1, -1)));
    }
}

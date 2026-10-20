package com.example.learning.oop.relationships;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.learning.oop.relationships.dto.RelationshipDtos.EnrollmentAction;
import com.example.learning.oop.relationships.dto.RelationshipDtos.EnrollmentRequest;
import com.example.learning.oop.relationships.service.EnrollmentScenarioService;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnrollmentScenarioServiceTest {
    private final EnrollmentScenarioService service = new EnrollmentScenarioService();

    @Test
    void registrationHasManyToManyPairsWithLinkSpecificSequence() {
        var response = service.simulate(new EnrollmentRequest(List.of("S1", "S2"), List.of("C1", "C2"),
                List.of(new EnrollmentAction("ENROLL", "S1", "C1"),
                        new EnrollmentAction("ENROLL", "S1", "C2"),
                        new EnrollmentAction("ENROLL", "S2", "C1"),
                        new EnrollmentAction("ENROLL", "S1", "C1"),
                        new EnrollmentAction("CANCEL", "S1", "C2"))));
        assertEquals(2, response.activeEnrollments().size());
        assertEquals("S1", response.activeEnrollments().get(0).studentId());
        assertEquals("C1", response.activeEnrollments().get(0).courseId());
        assertEquals(1, response.activeEnrollments().get(0).enrollmentSequence());
        assertEquals(3, response.activeEnrollments().get(1).enrollmentSequence());
        assertTrue(response.uniquePairs());
        assertFalse(response.events().get(3).accepted());
        assertEquals("DUPLICATE_ENROLLMENT", response.events().get(3).outcome());
        assertTrue(response.events().get(4).accepted());
        assertEquals(3, response.notificationsSent());
        assertTrue(response.events().get(0).notificationSent());
        assertFalse(response.events().get(4).notificationSent());
        assertTrue(response.simulatedOnly());
    }

    @Test
    void cancelledPairCanBeEnrolledAgainWithNewLinkSequence() {
        var r = service.simulate(new EnrollmentRequest(List.of("S"), List.of("C"),
                List.of(new EnrollmentAction("CANCEL", "S", "C"),
                        new EnrollmentAction("ENROLL", "S", "C"),
                        new EnrollmentAction("CANCEL", "S", "C"),
                        new EnrollmentAction("ENROLL", "S", "C"))));
        assertFalse(r.events().get(0).accepted());
        assertEquals("NOT_ENROLLED", r.events().get(0).outcome());
        assertEquals(2, r.activeEnrollments().get(0).enrollmentSequence());
        assertEquals(2, r.notificationsSent());
    }

    @Test
    void validatesAllRelationsBeforeRunningAnyAction() {
        assertThrows(IllegalArgumentException.class, () -> service.simulate(null));
        assertThrows(IllegalArgumentException.class, () -> service.simulate(new EnrollmentRequest(
                List.of("S", "S"), List.of("C"), List.of(new EnrollmentAction("ENROLL", "S", "C")))));
        assertThrows(IllegalArgumentException.class, () -> service.simulate(new EnrollmentRequest(
                List.of("S"), List.of("C"), List.of(new EnrollmentAction("REGISTER", "S", "C")))));
        assertThrows(IllegalArgumentException.class, () -> service.simulate(new EnrollmentRequest(
                List.of("S"), List.of("C"), List.of(new EnrollmentAction("ENROLL", "S", "MISSING")))));
        assertThrows(IllegalArgumentException.class, () -> service.simulate(new EnrollmentRequest(
                List.of("S"), List.of("C"), List.of())));
    }
}

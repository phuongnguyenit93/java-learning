package com.example.learning.oop.relationships;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.learning.oop.relationships.dto.RelationshipDtos.DepartmentInput;
import com.example.learning.oop.relationships.dto.RelationshipDtos.EmployeeInput;
import com.example.learning.oop.relationships.dto.RelationshipDtos.TransferAction;
import com.example.learning.oop.relationships.dto.RelationshipDtos.TransferRequest;
import com.example.learning.oop.relationships.service.DepartmentTransferService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepartmentTransferServiceTest {
    private final DepartmentTransferService service = new DepartmentTransferService();

    @Test
    void bidirectionalTransferKeepsBothEndsConsistent() {
        var result = service.simulate(new TransferRequest(
                List.of(new DepartmentInput("A", 2), new DepartmentInput("B", 1)),
                List.of(new EmployeeInput("E1", "A"), new EmployeeInput("E2", null)),
                List.of(new TransferAction("E1", "B"), new TransferAction("E2", "A"),
                        new TransferAction("E1", null), new TransferAction("E2", "A"))));
        assertTrue(result.invariantHolds());
        assertTrue(result.simulatedOnly());
        assertEquals(List.of("E2"), result.departments().get(0).employees());
        assertEquals(List.of(), result.departments().get(1).employees());
        assertEquals(null, result.employees().get(0).departmentCode());
        assertEquals("A", result.employees().get(1).departmentCode());
        assertEquals("MOVED", result.events().get(2).reason());
        assertEquals("UNCHANGED", result.events().get(3).reason());
        assertThrows(UnsupportedOperationException.class, () -> result.departments().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> result.departments().get(0).employees().clear());
    }

    @Test
    void fullTargetRejectsMoveAndPreservesOriginalAssociation() {
        var result = service.simulate(new TransferRequest(
                List.of(new DepartmentInput("A", 1), new DepartmentInput("B", 1)),
                List.of(new EmployeeInput("E1", "A"), new EmployeeInput("E2", "B")),
                List.of(new TransferAction("E1", "B"))));
        assertFalse(result.events().get(0).accepted());
        assertEquals("DEPARTMENT_FULL", result.events().get(0).reason());
        assertEquals(List.of("E1"), result.departments().get(0).employees());
        assertEquals(List.of("E2"), result.departments().get(1).employees());
        assertTrue(result.invariantHolds());
    }

    @Test
    void rejectsUnknownIdsDuplicatesAndCapacityViolations() {
        assertThrows(IllegalArgumentException.class, () -> service.simulate(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new TransferRequest(
                        List.of(new DepartmentInput("A", 1), new DepartmentInput("A", 2)),
                        List.of(), List.of(new TransferAction("E1", "A")))));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new TransferRequest(
                        List.of(new DepartmentInput("A", 1)), List.of(
                                new EmployeeInput("E1", "A"), new EmployeeInput("E2", "A")),
                        List.of(new TransferAction("E1", "A")))));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new TransferRequest(
                        List.of(new DepartmentInput("A", 1)),
                        List.of(new EmployeeInput("E1", "A")),
                        List.of(new TransferAction("E1", "MISSING")))));
        List<TransferAction> malformed = new ArrayList<>();
        malformed.add(new TransferAction("E1", "A"));
        malformed.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new TransferRequest(
                        List.of(new DepartmentInput("A", 1)),
                        List.of(new EmployeeInput("E1", "A")), malformed)));
    }

    @Test
    void eachRequestStartsWithFreshIndependentGraph() {
        var request = new TransferRequest(List.of(new DepartmentInput("A", 1)),
                List.of(new EmployeeInput("E1", "A")), List.of(new TransferAction("E1", null)));
        assertEquals(null, service.simulate(request).employees().get(0).departmentCode());
        assertEquals(null, service.simulate(request).employees().get(0).departmentCode());
    }
}

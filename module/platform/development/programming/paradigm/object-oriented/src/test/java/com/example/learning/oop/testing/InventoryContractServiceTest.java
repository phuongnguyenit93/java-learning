package com.example.learning.oop.testing;

import com.example.learning.oop.testing.dto.TestingDtos.*;
import com.example.learning.oop.testing.model.*;
import com.example.learning.oop.testing.service.InventoryContractService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryContractServiceTest {
    private final InventoryContractService service = new InventoryContractService();

    @Test
    void runsSameContractAgainstMonitorAtomicAndDeliberatelyBrokenImplementations() {
        ContractRequest scenario = new ContractRequest(10, List.of(
                new SeatAction("RESERVE", 4),
                new SeatAction("RESERVE", 7),
                new SeatAction("CANCEL", 2),
                new SeatAction("RESERVE", 8)));
        ContractResult result = service.run(scenario);
        assertEquals(3, result.runs().size());
        assertTrue(result.conformingImplementationsPassed());
        assertTrue(result.defectDetected());
        for (int i = 0; i < 2; i++) {
            ContractRun run = result.runs().get(i);
            assertTrue(run.allPassed());
            assertEquals(4, run.passedSteps());
            assertEquals(List.of(true, false, true, true),
                    run.steps().stream().map(ContractStep::actualAccepted).toList());
            assertEquals(new SeatInventory.Snapshot(10, 10),
                    run.steps().get(3).actualSnapshot());
        }
        ContractRun broken = result.runs().get(2);
        assertFalse(broken.allPassed());
        assertEquals("BROKEN_MUTATE_ON_REJECT", broken.implementation());
        assertTrue(broken.steps().get(1).violations().contains("STATE_MISMATCH"));
        assertTrue(broken.steps().get(1).violations().contains("INVARIANT_BROKEN"));
        assertEquals(11, broken.steps().get(1).actualSnapshot().reserved());
    }

    @Test
    void statesAreFreshAndRejectedCancellationPreservesState() {
        var request = new ContractRequest(5, List.of(new SeatAction("CANCEL", 1),
                new SeatAction("RESERVE", 5)));
        var first = service.run(request);
        var second = service.run(request);
        assertEquals(first, second);
        assertEquals(0, first.runs().get(0).steps().get(0).actualSnapshot().reserved());
        assertEquals(5, first.runs().get(1).steps().get(1).actualSnapshot().reserved());
        assertFalse(first.defectDetected()); // this scenario never triggers the seeded bug
    }

    @Test
    void constructorAndRequestBoundariesRejectInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> new MonitorSeatInventory(0));
        assertThrows(IllegalArgumentException.class, () -> new AtomicSeatInventory(-1));
        assertThrows(IllegalArgumentException.class, () -> service.run(new ContractRequest(0,
                List.of(new SeatAction("RESERVE", 1)))));
        assertThrows(IllegalArgumentException.class, () -> service.run(new ContractRequest(10,
                List.of(new SeatAction("MOVE", 1)))));
        assertThrows(IllegalArgumentException.class, () -> service.run(new ContractRequest(10,
                List.of(new SeatAction("RESERVE", null)))));
        assertThrows(IllegalArgumentException.class, () -> service.run(new ContractRequest(10,
                List.of())));
    }

    @Test
    void monitorAndAtomicSharePublicBehaviorAcrossBoundaries() {
        for (SeatInventory inventory : List.<SeatInventory>of(
                new MonitorSeatInventory(3), new AtomicSeatInventory(3))) {
            assertEquals(new SeatInventory.Snapshot(3, 0), inventory.snapshot());
            assertFalse(inventory.reserve(0));
            assertFalse(inventory.cancel(1));
            assertTrue(inventory.reserve(3));
            assertFalse(inventory.reserve(1));
            assertTrue(inventory.snapshot().invariantHolds());
            assertTrue(inventory.cancel(3));
            assertFalse(inventory.cancel(1));
            assertEquals(new SeatInventory.Snapshot(3, 0), inventory.snapshot());
        }
    }
}

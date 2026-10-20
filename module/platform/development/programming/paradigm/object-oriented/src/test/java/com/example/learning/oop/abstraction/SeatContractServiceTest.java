package com.example.learning.oop.abstraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.SeatAction;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.SeatScenarioRequest;
import com.example.learning.oop.abstraction.model.InMemorySeatInventory;
import com.example.learning.oop.abstraction.model.SeatInventory;
import com.example.learning.oop.abstraction.service.SeatContractService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SeatContractServiceTest {
    private final SeatContractService service = new SeatContractService();

    @Test
    void scenarioProvesPrePostconditionsAndPreservesStateOnRejection() {
        var result = service.simulate(new SeatScenarioRequest(10, List.of(
                new SeatAction("RESERVE", 3),
                new SeatAction("RELEASE", 1),
                new SeatAction("RESERVE", 9),
                new SeatAction("RELEASE", 4))));
        assertEquals(10, result.capacity());
        assertEquals(8, result.available());
        assertEquals(2, result.reserved());
        assertEquals(4, result.steps().size());
        assertEquals(7, result.steps().get(0).availableAfter());
        assertEquals(8, result.steps().get(1).availableAfter());
        assertFalse(result.steps().get(2).accepted());
        assertEquals("INSUFFICIENT_SEATS", result.steps().get(2).rejectionCode());
        assertEquals(8, result.steps().get(2).availableBefore());
        assertEquals(8, result.steps().get(2).availableAfter());
        assertFalse(result.steps().get(3).accepted());
        assertEquals("RELEASE_EXCEEDS_RESERVED", result.steps().get(3).rejectionCode());
        assertEquals(8, result.steps().get(3).availableAfter());
        assertThrows(UnsupportedOperationException.class, result.steps()::clear);
        assertTrue(result.simulatedOnly());
    }

    @Test
    void distinctCallsHaveIndependentInventories() {
        var first = service.simulate(new SeatScenarioRequest(5, List.of(new SeatAction("RESERVE", 5))));
        var second = service.simulate(new SeatScenarioRequest(5, List.of(new SeatAction("RESERVE", 1))));
        assertEquals(0, first.available());
        assertEquals(4, second.available());
    }

    @Test
    void commonInterfaceEnforcesExactDeltaAndFailureGuarantee() {
        SeatInventory seats = new InMemorySeatInventory(5);
        assertEquals(5, seats.capacity());
        assertEquals(5, seats.available());
        seats.reserve(2);
        assertEquals(3, seats.available());
        seats.release(1);
        assertEquals(4, seats.available());
        assertThrows(IllegalStateException.class, () -> seats.reserve(5));
        assertEquals(4, seats.available());
        assertThrows(IllegalStateException.class, () -> seats.release(2));
        assertEquals(4, seats.available());
        assertThrows(IllegalArgumentException.class, () -> seats.reserve(0));
        assertEquals(4, seats.available());
        assertThrows(IllegalArgumentException.class, () -> seats.release(-1));
        assertEquals(4, seats.available());
    }

    @Test
    void wholeRequestPrevalidatedBeforeAnyStepCanRun() {
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new SeatScenarioRequest(0, List.of(new SeatAction("RESERVE", 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new SeatScenarioRequest(10, List.of(new SeatAction("BUY", 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new SeatScenarioRequest(10,
                        List.of(new SeatAction("RESERVE", 1), new SeatAction("RELEASE", 0)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new SeatScenarioRequest(10, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> new InMemorySeatInventory(-1));
        List<SeatAction> malformed = new ArrayList<>();
        malformed.add(new SeatAction("RESERVE", 1));
        malformed.add(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(new SeatScenarioRequest(10, malformed)));
    }

    @Test
    void synchronizedReserveDoesNotDoubleBookTheLastSeatInsideOneJvm() throws Exception {
        SeatInventory inventory = new InMemorySeatInventory(1);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Boolean> attempt = () -> {
                start.await();
                try {
                    inventory.reserve(1);
                    return true;
                } catch (IllegalStateException expected) {
                    return false;
                }
            };
            var first = pool.submit(attempt);
            var second = pool.submit(attempt);
            start.countDown();
            int successes = (first.get(3, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(3, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
            assertEquals(0, inventory.available());
        } finally {
            pool.shutdownNow();
        }
    }
}

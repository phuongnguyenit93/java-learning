package com.example.learning.oop.design_pitfalls;

import com.example.learning.oop.design_pitfalls.service.DesignPitfallExperimentService;
import static com.example.learning.oop.design_pitfalls.dto.DesignPitfallDtos.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesignPitfallServiceTest {
    private final DesignPitfallExperimentService service = new DesignPitfallExperimentService();

    @Test
    void seatExperimentTracesRejectedAndAcceptedMutations() {
        var response = service.seats(new SeatsRequest(5, List.of(
                new SeatAction("RESERVE", 4),
                new SeatAction("RESERVE", 2),
                new SeatAction("CANCEL", 1),
                new SeatAction("CANCEL", 5),
                new SeatAction("RESERVE", 2)
        )));
        assertEquals(new SeatState(5, 0, 5), response.initial());
        assertTrue(response.steps().get(0).accepted());
        assertFalse(response.steps().get(1).accepted());
        assertEquals(response.steps().get(1).before(), response.steps().get(1).after());
        assertFalse(response.steps().get(3).accepted());
        assertEquals(response.steps().get(3).before(), response.steps().get(3).after());
        assertEquals(new SeatState(5, 5, 0), response.current());
        assertTrue(response.invariantPreserved());
        assertTrue(response.scope().contains("SINGLE_JVM"));
        assertThrows(UnsupportedOperationException.class, () -> response.steps().clear());
    }

    @Test
    void seatExperimentValidatesStructureAndBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> service.seats(new SeatsRequest(1, List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> service.seats(new SeatsRequest(1, List.of(new SeatAction(null, 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.seats(new SeatsRequest(1, List.of(new SeatAction("BOGUS", 1)))));
        List<SeatAction> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) many.add(new SeatAction("RESERVE", 1));
        assertThrows(IllegalArgumentException.class,
                () -> service.seats(new SeatsRequest(10, many)));
    }

    @Test
    void seatExperimentsDoNotShareCrossRequestState() {
        SeatsRequest req = new SeatsRequest(1, List.of(new SeatAction("RESERVE", 1)));
        assertEquals(1, service.seats(req).current().reserved());
        assertEquals(1, service.seats(req).current().reserved());
        assertEquals(0, service.seats(req).initial().reserved());
    }

    @Test
    void mutableIdentityExperimentShowsActualHashMapBugAndSafeAlternative() {
        var response = service.mutableKey(new MutableKeyRequest("ALICE", "BOB"));
        assertTrue(response.unsafeFoundBeforeRename());
        assertFalse(response.unsafeFoundAfterRename());
        assertFalse(response.lookupByOldNameAfterRename());
        assertTrue(response.stableFoundAfterRename());
        assertEquals("BOB", response.stableProfileNameAfterRename());
        assertEquals(1, response.stableMapEntries());
    }

    @Test
    void refactoringExperimentReturnsIndependentResultsAndRealTrace() {
        var response = service.checkout(new CheckoutRequest(
                "SKU-1", 10_000L, 3, 5, true, "EXPRESS"));
        assertEquals(response.legacy(), response.refactored());
        assertTrue(response.sameResult());
        assertEquals(63_500L, response.refactored().payableVnd());
        assertEquals(5, response.collaborationTrace().size());
        assertEquals("StockGuard.requireAvailable", response.collaborationTrace().get(0));
        assertTrue(response.scope().contains("PURE_IN_MEMORY_QUOTE"));
    }

    @Test
    void refactoringExperimentReturns422ForInsufficientStockAnd400ForBadInput() {
        assertThrows(IllegalStateException.class, () -> service.checkout(
                new CheckoutRequest("SKU-1", 100L, 2, 1, false, "STANDARD")));
        assertThrows(IllegalArgumentException.class, () -> service.checkout(
                new CheckoutRequest("SKU-1", 100L, null, 1, false, "STANDARD")));
        assertThrows(IllegalArgumentException.class, () -> service.mutableKey(null));
    }
}

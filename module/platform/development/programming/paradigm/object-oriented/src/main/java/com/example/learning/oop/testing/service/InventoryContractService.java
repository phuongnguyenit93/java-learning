package com.example.learning.oop.testing.service;

import com.example.learning.oop.testing.dto.TestingDtos.*;
import com.example.learning.oop.testing.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import org.springframework.stereotype.Service;

/** Executes exactly the same black-box contract cases against each implementation. */
@Service
public final class InventoryContractService {
    public ContractResult run(ContractRequest request) {
        if (request == null || request.capacity() == null ||
                request.capacity() < 1 || request.capacity() > 100_000) {
            throw new IllegalArgumentException("capacity must be 1..100000");
        }
        if (request.actions() == null || request.actions().isEmpty() ||
                request.actions().size() > 30) {
            throw new IllegalArgumentException("actions must contain 1..30 operations");
        }
        for (SeatAction action : request.actions()) {
            if (action == null || action.seats() == null ||
                    action.seats() < 1 || action.seats() > 100_000 ||
                    !("RESERVE".equals(action.type()) || "CANCEL".equals(action.type()))) {
                throw new IllegalArgumentException("each action needs RESERVE/CANCEL and seats 1..100000");
            }
        }
        int capacity = request.capacity();
        var initial = new SeatInventory.Snapshot(capacity, 0);
        List<ContractRun> runs = List.of(
                execute("MONITOR", MonitorSeatInventory::new, capacity, request.actions()),
                execute("ATOMIC", AtomicSeatInventory::new, capacity, request.actions()),
                execute("BROKEN_MUTATE_ON_REJECT", BrokenSeatInventory::new, capacity, request.actions()));
        return new ContractResult(initial, runs,
                runs.get(0).allPassed() && runs.get(1).allPassed(),
                !runs.get(2).allPassed());
    }

    private static ContractRun execute(String name, IntFunction<SeatInventory> creator,
                                       int capacity, List<SeatAction> actions) {
        SeatInventory actual = creator.apply(capacity);
        // Reference state machine is intentionally independent of each implementation.
        int expectedReserved = 0;
        List<ContractStep> steps = new ArrayList<>();
        int index = 0;
        for (SeatAction action : actions) {
            index++;
            int seats = action.seats();
            boolean expectedAccepted;
            if (action.type().equals("RESERVE")) {
                expectedAccepted = seats <= capacity - expectedReserved;
                if (expectedAccepted) {
                    expectedReserved += seats;
                }
            } else {
                expectedAccepted = seats <= expectedReserved;
                if (expectedAccepted) {
                    expectedReserved -= seats;
                }
            }
            boolean actualAccepted = action.type().equals("RESERVE") ?
                    actual.reserve(seats) : actual.cancel(seats);
            var expected = new SeatInventory.Snapshot(capacity, expectedReserved);
            var observed = actual.snapshot();
            List<String> violations = new ArrayList<>();
            if (actualAccepted != expectedAccepted) {
                violations.add("RETURN_VALUE_MISMATCH");
            }
            if (!expected.equals(observed)) {
                violations.add("STATE_MISMATCH");
            }
            if (!observed.invariantHolds()) {
                violations.add("INVARIANT_BROKEN");
            }
            steps.add(new ContractStep(index, action.type(), seats, expectedAccepted,
                    actualAccepted, expected, observed, violations.isEmpty(), List.copyOf(violations)));
        }
        long passed = steps.stream().filter(ContractStep::passed).count();
        return new ContractRun(name, passed == steps.size(), passed, List.copyOf(steps));
    }
}

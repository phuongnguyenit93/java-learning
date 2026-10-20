package com.example.learning.oop.abstraction.service;

import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.SeatAction;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.SeatScenarioRequest;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.SeatScenarioResponse;
import com.example.learning.oop.abstraction.dto.AbstractionExperimentDto.SeatStep;
import com.example.learning.oop.abstraction.model.InMemorySeatInventory;
import com.example.learning.oop.abstraction.model.SeatInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public final class SeatContractService {
    private static final int MAX_STEPS = 40;

    public SeatScenarioResponse simulate(SeatScenarioRequest request) {
        if (request == null || request.capacity() <= 0 || request.capacity() > 1_000_000) {
            throw new IllegalArgumentException("capacity must be from 1 to 1000000");
        }
        if (request.actions() == null || request.actions().isEmpty()
                || request.actions().size() > MAX_STEPS) {
            throw new IllegalArgumentException("actions must contain 1 to " + MAX_STEPS + " steps");
        }

        // Validate the whole request first: malformed step N must not run steps 1..N-1.
        if (request.actions().stream().anyMatch(action -> action == null)) {
            throw new IllegalArgumentException("actions must not contain null");
        }
        List<SeatAction> actions = List.copyOf(request.actions());
        List<String> operations = new ArrayList<>(actions.size());
        for (SeatAction action : actions) {
            if (action == null || action.seats() <= 0 || action.operation() == null) {
                throw new IllegalArgumentException("Every action requires an operation and positive seats");
            }
            String operation = action.operation().trim().toUpperCase(Locale.ROOT);
            if (!operation.equals("RESERVE") && !operation.equals("RELEASE")) {
                throw new IllegalArgumentException("Operation must be RESERVE or RELEASE");
            }
            operations.add(operation);
        }

        // A fresh instance for each HTTP invocation, never a shared singleton inventory.
        SeatInventory inventory = new InMemorySeatInventory(request.capacity());
        List<SeatStep> steps = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            int before = inventory.available();
            SeatAction action = actions.get(i);
            String operation = operations.get(i);
            boolean accepted = true;
            String rejection = null;
            try {
                if (operation.equals("RESERVE")) {
                    inventory.reserve(action.seats());
                } else {
                    inventory.release(action.seats());
                }
            } catch (IllegalStateException expectedBusinessRejection) {
                accepted = false;
                rejection = expectedBusinessRejection.getMessage();
            }
            int after = inventory.available();
            if ((!accepted && before != after) || after < 0 || after > inventory.capacity()) {
                throw new IllegalStateException("Seat inventory violated its contract");
            }
            steps.add(new SeatStep(i + 1, operation, action.seats(), before,
                    after, accepted, rejection));
        }
        return new SeatScenarioResponse(inventory.capacity(), inventory.available(),
                inventory.capacity() - inventory.available(), steps, true);
    }
}

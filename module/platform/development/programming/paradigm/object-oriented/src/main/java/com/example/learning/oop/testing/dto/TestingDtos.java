package com.example.learning.oop.testing.dto;

import com.example.learning.oop.testing.model.SeatInventory.Snapshot;
import com.example.learning.oop.testing.model.QuoteRegression.Invoice;
import java.util.List;
import java.util.Map;

/** HTTP input/output types; experiment logic lives in model and service. */
public final class TestingDtos {
    private TestingDtos() {}

    public record SeatAction(String type, Integer seats) {}

    public record ContractRequest(Integer capacity, List<SeatAction> actions) {}

    public record ContractStep(int index, String action, int seats,
                               boolean expectedAccepted, boolean actualAccepted,
                               Snapshot expectedSnapshot, Snapshot actualSnapshot,
                               boolean passed, List<String> violations) {}

    public record ContractRun(String implementation, boolean allPassed,
                              long passedSteps, List<ContractStep> steps) {}

    public record ContractResult(Snapshot initial, List<ContractRun> runs,
                                 boolean conformingImplementationsPassed,
                                 boolean defectDetected) {}

    public record CheckoutScenario(String orderId, String sku, Integer quantity,
                                   Long unitPriceVnd, Integer stock,
                                   String paymentMode, String receiptMode) {}

    public record CollaborationRequest(List<CheckoutScenario> scenarios) {}

    public record CollaborationRun(String orderId, String status, long payableVnd,
                                   int initialStock, int remainingStock,
                                   int paymentAttempts, int sentReceipts,
                                   List<String> events, boolean invariantHolds) {}

    public record CollaborationResult(List<CollaborationRun> runs, String scope) {}

    public record PriceLine(String sku, Integer quantity) {}

    public record RegressionRequest(Map<String, Long> pricesVnd,
                                    List<PriceLine> lines, Boolean member) {}

    public record RegressionVariant(String implementation, Invoice invoice,
                                    int catalogLookups, boolean matchesBaseline) {}

    public record RegressionResult(Invoice baseline, List<RegressionVariant> variants,
                                   boolean refactorPreservedBehavior,
                                   boolean seededDefectDetected) {}

    public record ErrorResponse(String code, String detail) {}
}

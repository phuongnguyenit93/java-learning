package com.example.learning.oop.design_patterns.dto;

import java.util.List;

/** HTTP JSON contracts, intentionally separate from the domain and algorithm classes. */
public final class DesignPatternDtos {
    private DesignPatternDtos() {}

    public record PricingRequest(Long subtotalVnd, Long deliveryFeeVnd,
                                 String strategy, String order) {}
    public record PricingOption(String order, long payableVnd, long discountVnd,
                                List<String> executionTrace) {}
    public record PricingResult(String strategy, long subtotalVnd, long deliveryFeeVnd,
                                PricingOption selected, List<PricingOption> allOrders,
                                String scope) {}

    public record TaskAction(String type, String id, String title) {}
    public record TasksRequest(List<TaskAction> actions) {}
    public record TaskView(String id, String title, boolean done) {}
    public record TaskSnapshot(List<TaskView> tasks, int undoDepth, int redoDepth,
                               List<String> observerDeliveries) {}
    public record TaskStep(int index, String action, boolean accepted, String detail,
                           TaskSnapshot before, TaskSnapshot after) {}
    public record TasksResult(TaskSnapshot initial, List<TaskStep> steps,
                              TaskSnapshot current, String scope) {}

    public record SaleInput(String id, Long amountVnd) {}
    public record ReportRequest(String format, List<SaleInput> sales) {}
    public record ReportResult(String format, String content, long totalVnd,
                               int rowCount, List<String> executionTrace) {}

    public record ErrorResponse(String error) {}
}

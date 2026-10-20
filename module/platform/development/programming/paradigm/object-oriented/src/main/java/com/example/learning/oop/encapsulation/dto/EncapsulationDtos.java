package com.example.learning.oop.encapsulation.dto;

import java.util.List;

/** JSON input and output values; no business decisions are performed here. */
public final class EncapsulationDtos {
    private EncapsulationDtos() {}

    public record CartLineInput(String sku, Long unitPriceVnd, Integer quantity) {}
    public record CartAction(String type, String sku, Long unitPriceVnd, Integer quantity) {}
    public record CartRequest(List<CartLineInput> initialLines, List<CartAction> actions) {}
    public record CartLineState(String sku, long unitPriceVnd, int quantity, long lineTotalVnd) {}
    public record CartState(List<CartLineState> lines, long totalVnd) {}
    public record CartStep(int index, String type, boolean accepted, String detail,
                           CartState before, CartState after) {}
    public record CartResult(CartState initial, List<CartStep> steps, CartState current,
                             String scope) {}

    public record StockAction(String type, Integer units) {}
    public record StockRequest(String sku, Integer initialOnHand,
                               Integer initialReserved, List<StockAction> actions) {}
    public record StockState(String sku, int onHand, int reserved, int available) {}
    public record StockStep(int index, String type, boolean accepted, String detail,
                            StockState before, StockState after) {}
    public record StockResult(StockState initial, List<StockStep> steps, StockState current) {}

    public record EventRequest(Long startEpochMillis, List<String> attendeeNames,
                               Long mutatedExternalEpochMillis, String renamedFirst,
                               String addedAttendee) {}
    public record EventState(long startEpochMillis, List<String> attendeeNames) {}
    public record EventResult(EventState originalDraft, EventState mutatedDraft,
                              EventState protectedSnapshot, EventState afterGetterMutation,
                              boolean returnedListIsUnmodifiable) {}

    public record OrderItemInput(String sku, Integer quantity) {}
    public record OrderAction(String type) {}
    public record OrderRequest(String orderId, List<OrderItemInput> items,
                               List<OrderAction> actions) {}
    public record OrderState(String orderId, String status, List<OrderItemInput> items) {}
    public record OrderStep(int index, String type, boolean accepted, String detail,
                            OrderState before, OrderState after) {}
    public record OrderResult(OrderState initial, List<OrderStep> steps, OrderState current,
                              String scope) {}

    public record ErrorResponse(String error) {}
}

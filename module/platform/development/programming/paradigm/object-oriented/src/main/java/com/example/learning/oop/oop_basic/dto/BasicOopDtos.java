package com.example.learning.oop.oop_basic.dto;

import java.util.List;

/**
 * Request/response contracts for transient, repeatable OOP experiments.
 * Money is expressed in whole cents; no experiment stores account/order data.
 */
public final class BasicOopDtos {
    private BasicOopDtos() {
    }

    public enum AccountAction {
        DEPOSIT, WITHDRAW
    }

    public record AccountActionInput(AccountAction action, long amountCents) {
    }

    public record AccountSimulationRequest(
            String accountId, long openingBalanceCents, List<AccountActionInput> actions) {
    }

    public record AccountActionResult(
            int step, AccountAction action, long amountCents,
            long balanceBeforeCents, long balanceAfterCents,
            boolean accepted, String reason) {
    }

    public record AccountSimulationResponse(
            String accountId, long openingBalanceCents, long finalBalanceCents,
            List<AccountActionResult> timeline) {
    }

    public enum OrderAction {
        ADD_LINE, CONFIRM, CANCEL
    }

    public record OrderStepInput(
            OrderAction action, String sku, Integer quantity, Long unitPriceCents) {
    }

    public record OrderSimulationRequest(String orderId, List<OrderStepInput> steps) {
    }

    public record OrderStepResult(
            int step, OrderAction action, String statusBefore, String statusAfter,
            long totalBeforeCents, long totalAfterCents, int lineCountAfter,
            boolean accepted, String reason) {
    }

    public record OrderSimulationResponse(
            String orderId, String finalStatus, long totalCents,
            List<OrderStepResult> timeline) {
    }

    public record ShippingQuoteRequest(long subtotalCents, int distanceKm) {
    }

    public record ShippingQuote(String policy, long feeCents, long payableCents) {
    }

    public record ShippingQuoteResponse(
            long subtotalCents, int distanceKm, List<ShippingQuote> quotes) {
    }

    public record ErrorResponse(String code, String message) {
    }
}

package com.example.learning.oop.language_mechanics.dto;

import java.util.List;
import java.util.Map;

public final class LanguageMechanicsDtos {
    private LanguageMechanicsDtos() {}

    public record CacheEntry(String key, int quantity) {}
    public record GenericBatchRequest(List<Integer> integers, List<Double> decimals,
                                      List<CacheEntry> entries, List<String> lookups) {}
    public record GenericBatchResult(List<String> copiedValues, String sum,
                                     Integer largestInteger, Map<String, Integer> found,
                                     List<String> missing) {}

    public record StockAction(String type, String tenantId, String sku, Integer quantity,
                              String contactEmail, String providerToken) {}
    public record StockRequest(List<StockAction> actions) {}
    public record StockStep(int index, String action, boolean accepted, String code,
                            Integer available, String diagnostic) {}
    public record StockResult(List<StockStep> steps, int uniqueProducts) {}

    public record ShippingInput(String serviceCode, int weightGrams) {}
    public record ShippingRequest(List<ShippingInput> quotes) {}
    public record ShippingLine(String serviceCode, int weightGrams, boolean accepted,
                               Long feeVnd, String code) {}
    public record ShippingResult(List<ShippingLine> quotes, long totalAcceptedFeesVnd) {}
}

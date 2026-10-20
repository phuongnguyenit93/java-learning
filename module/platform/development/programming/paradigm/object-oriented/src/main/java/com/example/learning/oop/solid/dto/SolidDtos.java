package com.example.learning.oop.solid.dto;

import com.example.learning.oop.solid.model.CheckoutReceipt;
import com.example.learning.oop.solid.repository.MemoryReceiptSender.Notice;
import java.util.List;

public final class SolidDtos {
    private SolidDtos() {}

    public record QuoteRequest(String policyCode, Long subtotalCents) {}
    public record QuoteResult(String policyCode, long subtotalCents,
                              long discountCents, long payableCents) {}
    public record LineInput(String sku, Integer quantity, Long unitPriceCents) {}
    public record AttemptInput(String orderId, String policyCode, List<LineInput> lines) {}
    public record ExperimentRequest(List<AttemptInput> attempts) {}
    public record AttemptResult(int index, String orderId, boolean accepted,
                                String rule, String detail, CheckoutReceipt receipt,
                                int storedCount, int noticeCount) {}
    public record ExperimentResult(List<AttemptResult> attempts,
                                   List<CheckoutReceipt> storedOrders, List<Notice> notices,
                                   boolean countsConsistent, String scope) {}
    public record ErrorResponse(String code, String message) {}
}

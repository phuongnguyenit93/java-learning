package com.example.learning.oop.class_design.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;

/** HTTP transport contracts; domain classes never depend on these types. */
public final class ClassDesignDtos {
    private ClassDesignDtos() {}

    public record BookInput(String copyId, String title) {}
    public record LibraryAction(String type, String borrowerId, String copyId,
                                @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
                                LocalDate onDate) {}
    public record LibraryRequest(int maxActiveLoans, int loanDays, List<BookInput> copies,
                                 List<LibraryAction> actions) {}
    public record LoanView(String borrowerId, String copyId, String title,
                           @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
                           LocalDate borrowedOn,
                           @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
                           LocalDate dueOn,
                           @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
                           LocalDate returnedOn, long lateFeeCents) {}
    public record LibraryStep(int index, String action, boolean accepted, String outcome,
                              List<LoanView> loans) {}
    public record LibraryResult(List<LibraryStep> steps, List<LoanView> finalLoans) {}

    public record CartLineInput(String sku, int quantity, long unitPriceCents) {}
    public record CheckoutRequest(String orderId, String receiptAddress, int discountPercent,
                                  List<CartLineInput> lines) {}
    public record ReceiptEvent(String orderId, String destination, long amountCents) {}
    public record CheckoutResult(String orderId, long subtotalCents, long discountCents,
                                 long totalCents, List<CartLineInput> persistedLines,
                                 List<ReceiptEvent> receiptOutbox) {}

    public record TransferRequest(String fromAccountId, String toAccountId, String currency,
                                  long fromBalanceCents, long toBalanceCents, long amountCents) {}
    public record TransferResult(String fromAccountId, String toAccountId, String currency,
                                 long fromBeforeCents, long toBeforeCents,
                                 long fromAfterCents, long toAfterCents) {}
}

package com.example.learning.oop.practice.dto;

import com.example.learning.oop.practice.model.BankLedger;
import com.example.learning.oop.practice.model.PracticeLibrary;
import com.example.learning.oop.practice.model.PracticeOrder;
import java.util.List;

/** Small JSON view contracts; service/domain own the business decisions. */
public final class PracticeDtos {
    private PracticeDtos() {}

    public record BankSeed(String id, String owner, Long openingBalanceVnd) {}
    public record BankAction(String type, String accountId, String toAccountId, Long amountVnd) {}
    public record BankRequest(List<BankSeed> accounts, List<BankAction> actions) {}
    public record BankStep(int index, String type, boolean accepted, String detail,
                           List<BankLedger.AccountView> before, List<BankLedger.AccountView> after) {}
    public record BankResult(List<BankLedger.AccountView> initial, List<BankStep> steps,
                             List<BankLedger.AccountView> current, String scope) {}

    public record OrderAction(String type, String sku, Long unitPriceCents, Integer quantity) {}
    public record OrderRequest(String orderId, List<OrderAction> actions) {}
    public record OrderState(String id, String status, List<PracticeOrder.Line> lines,
                             PracticeOrder.Summary summary) {}
    public record OrderStep(int index, String type, boolean accepted,
                            String detail, Boolean removed,
                            OrderState before, OrderState after) {}
    public record OrderResult(OrderState initial, List<OrderStep> steps,
                              OrderState current, String scope) {}

    public record LibraryBookInput(String isbn, String title, String kind) {}
    public record LibraryMemberInput(String id, Boolean active) {}
    public record LibraryCopyInput(String id, String isbn) {}
    public record LibraryAction(String type, String memberId, String copyId,
                                String loanId, String onDate) {}
    public record LibraryRequest(List<LibraryBookInput> books,
                                 List<LibraryMemberInput> members,
                                 List<LibraryCopyInput> copies,
                                 List<LibraryAction> actions) {}
    /** Stable transport dates are ISO text, independent of Jackson's Java-time array settings. */
    public record LibraryLoanView(String id, String memberId, String copyId,
                                  String borrowedOn, String dueOn, String returnedOn,
                                  boolean open) {}
    public record LibraryState(String policy,
                               List<PracticeLibrary.CopyView> copies,
                               List<LibraryLoanView> loans, int openLoanCount) {}
    public record LibraryStep(int index, String type, boolean accepted, String rule,
                              String detail, String affectedLoanId,
                              LibraryState before,
                              LibraryState after) {}
    public record LibraryResult(LibraryState initial, List<LibraryStep> steps,
                                LibraryState current,
                                boolean invariantsPreserved, String scope) {}

    public record ErrorResponse(String error) {}
}

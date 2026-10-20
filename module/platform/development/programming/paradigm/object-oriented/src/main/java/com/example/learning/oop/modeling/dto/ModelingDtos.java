package com.example.learning.oop.modeling.dto;

import java.util.List;

/** HTTP input/output records; business rules live in model classes. */
public final class ModelingDtos {
    private ModelingDtos() {}

    public record TitleInput(String code, String name) {}
    public record MemberInput(String id, Boolean active) {}
    public record CopyInput(String id, String titleCode) {}
    public record LibraryAction(String type, String memberId, String copyId,
                                String loanId, String onDate) {}
    public record LibraryRequest(List<TitleInput> titles, List<MemberInput> members,
                                 List<CopyInput> copies, List<LibraryAction> actions) {}
    public record MemberView(String id, boolean active, int activeLoans) {}
    public record CopyView(String id, String titleCode, String titleName, boolean borrowed) {}
    public record LoanView(String id, String memberId, String copyId,
                           String borrowedOn, String dueOn, String returnedOn, String status) {}
    public record LibrarySnapshot(List<MemberView> members, List<CopyView> copies,
                                  List<LoanView> loans) {}
    public record LibraryStep(int index, String type, boolean accepted, String rule,
                              String detail, String affectedLoanId, List<String> calls,
                              LibrarySnapshot before, LibrarySnapshot after) {}
    public record LibraryResult(LibrarySnapshot initial, List<LibraryStep> steps,
                                LibrarySnapshot current, boolean invariantsSatisfied,
                                String scope) {}

    public record OrderAction(String event, Long receivedVnd, String trackingCode) {}
    public record OrderRequest(String orderId, Long totalVnd, List<OrderAction> actions) {}
    public record OrderSnapshot(String id, String state, long totalVnd, String trackingCode) {}
    public record OrderStep(int index, String event, boolean accepted, String rule,
                            String detail, OrderSnapshot before, OrderSnapshot after) {}
    public record OrderResult(OrderSnapshot initial, List<OrderStep> steps,
                              OrderSnapshot current, String scope) {}

    public record ErrorResponse(String error) {}
}

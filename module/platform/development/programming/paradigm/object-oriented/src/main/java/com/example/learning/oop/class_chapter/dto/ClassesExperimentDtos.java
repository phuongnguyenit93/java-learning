package com.example.learning.oop.class_chapter.dto;

import java.util.List;

/** HTTP input/output contracts; no domain behavior belongs in these records. */
public final class ClassesExperimentDtos {
    private ClassesExperimentDtos() { }

    public record InventoryRequest(String sku, String name, Long unitPriceVnd,
                                   Integer initialQuantity, List<InventoryAction> actions) { }
    public record InventoryAction(String type, Integer units, String name) { }
    public record InventorySnapshot(String sku, String name, long unitPriceVnd,
                                    int quantity, long totalValueVnd) { }
    public record InventoryStep(int index, String type, boolean accepted, String detail,
                                InventorySnapshot before, InventorySnapshot after) { }
    public record InventoryResult(InventorySnapshot initial, List<InventoryStep> steps,
                                  InventorySnapshot current, int maxQuantity) { }

    public record WorkshopRequest(String firstTitle, Integer firstCapacity,
                                  String secondTitle, Integer secondCapacity,
                                  Integer firstAttempts, Integer aliasAttempts) { }
    public record WorkshopSnapshot(String title, int capacity, int registered,
                                   int remainingSeats) { }
    public record WorkshopResult(WorkshopSnapshot first, WorkshopSnapshot second,
                                 WorkshopSnapshot independentCopy,
                                 boolean aliasIsSecond, boolean copyIsSecond,
                                 int acceptedFirst, int rejectedFirst,
                                 int acceptedViaAlias, int rejectedViaAlias,
                                 int sharedStaticMaxCapacity) { }

    public record BankRequest(BankSpec source, BankSpec destination, List<BankAction> actions) { }
    public record BankSpec(String accountNumber, String holderName, Long openingBalanceVnd) { }
    public record BankAction(String type, Long amountVnd) { }
    public record BankSnapshot(String accountNumber, String holderName, long balanceVnd) { }
    public record BankStep(int index, String type, boolean accepted, String detail,
                           BankSnapshot sourceBefore, BankSnapshot destinationBefore,
                           BankSnapshot sourceAfter, BankSnapshot destinationAfter) { }
    public record BankResult(BankSnapshot initialSource, BankSnapshot initialDestination,
                             List<BankStep> steps, BankSnapshot finalSource,
                             BankSnapshot finalDestination, String scope) { }

    public record ErrorResult(String error) { }
}

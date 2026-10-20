package com.example.learning.oop.class_chapter;

import com.example.learning.oop.class_chapter.controller.ClassesExperimentController;
import com.example.learning.oop.class_chapter.service.ClassesExperimentService;
import com.example.learning.oop.class_chapter.model.BankAccount;
import com.example.learning.oop.class_chapter.model.InventoryItem;
import com.example.learning.oop.class_chapter.model.Workshop;
import com.example.learning.oop.class_chapter.exception.ClassesExperimentExceptionHandler;
import static com.example.learning.oop.class_chapter.dto.ClassesExperimentDtos.*;

import java.lang.reflect.Modifier;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;


/**
 * Dependency-free smoke/edge suite. Run main() with assertions expressed as checks,
 * so it remains executable before Prime turns the module from LIBRARY into a server.
 */
public final class ClassesExperimentSelfTest {
    private ClassesExperimentSelfTest() { }

    public static void main(String[] args) throws Exception {
        testDomainEncapsulation();
        testInventory();
        testWorkshop();
        testBankAccount();
        testValidationAndBounds();
        testControllerContract();
        System.out.println("PASS: classes chapter controller/service/domain experiments (6 groups)");
    }

    private static void testDomainEncapsulation() throws Exception {
        var sku = InventoryItem.class.getDeclaredField("sku");
        var quantity = InventoryItem.class.getDeclaredField("quantity");
        var balance = BankAccount.class.getDeclaredField("balanceVnd");
        check(Modifier.isPrivate(sku.getModifiers()) && Modifier.isFinal(sku.getModifiers()),
                "sku must be private final");
        check(Modifier.isPrivate(quantity.getModifiers()) && !Modifier.isFinal(quantity.getModifiers()),
                "quantity must be private mutable");
        check(Modifier.isPrivate(balance.getModifiers()), "bank balance must be private");
        check(Modifier.isStatic(Workshop.class.getDeclaredField("MAX_CAPACITY").getModifiers()),
                "class shared limit must be static");
    }

    private static void testInventory() {
        var service = new ClassesExperimentService();
        InventoryResult result = service.simulateInventory(new InventoryRequest(
                "KB-01", "Keyboard", 129_000L, 10, List.of(
                new InventoryAction("restock", 5, null),
                new InventoryAction("sell", 3, null),
                new InventoryAction("sell", 20, null),
                new InventoryAction("rename", null, "Mechanical Keyboard"),
                new InventoryAction("restock", 0, null))));

        check(result.initial().quantity() == 10, "initial inventory snapshot");
        check(result.current().quantity() == 12, "successful mutation and rejected sell");
        check(result.current().totalValueVnd() == 1_548_000L, "inventory value");
        check(result.current().name().equals("Mechanical Keyboard"), "rename");
        check(!result.steps().get(2).accepted() &&
                result.steps().get(2).before().equals(result.steps().get(2).after()),
                "failed sale must preserve state");
        check(!result.steps().get(4).accepted(), "zero restock must be rejected");
        expect(UnsupportedOperationException.class, () -> result.steps().clear());
        expect(IllegalArgumentException.class, () -> new InventoryItem("A", "Item", -1, 0));
        expect(IllegalArgumentException.class, () -> new InventoryItem("A", "Item", 1, -1));
    }

    private static void testWorkshop() {
        var result = new ClassesExperimentService().simulateWorkshops(
                new WorkshopRequest("Java", 2, "OOP", 1, 3, 2));
        check(result.first().registered() == 2 && result.acceptedFirst() == 2 &&
                result.rejectedFirst() == 1, "first workshop has independent capacity");
        check(result.second().registered() == 1 && result.acceptedViaAlias() == 1 &&
                result.rejectedViaAlias() == 1, "alias changes second object");
        check(result.independentCopy().registered() == 0, "new returns independent object");
        check(result.aliasIsSecond() && !result.copyIsSecond(), "reference identity");
        check(result.sharedStaticMaxCapacity() == Workshop.MAX_CAPACITY, "static class limit");
    }

    private static void testBankAccount() {
        BankAccount src = new BankAccount("ACC001", "An", 1_000_000L);
        BankAccount dst = new BankAccount("ACC002", "Binh", 200_000L);
        src.deposit(300_000L);
        src.withdraw(100_000L);
        src.transferTo(dst, 400_000L);
        check(src.balanceVnd() == 800_000L && dst.balanceVnd() == 600_000L,
                "banking domain example");
        expect(IllegalStateException.class, () -> src.withdraw(900_000L));
        check(src.balanceVnd() == 800_000L, "failed withdrawal preserves balance");
        expect(IllegalArgumentException.class, () -> src.transferTo(src, 1));
        expect(IllegalArgumentException.class, () -> src.deposit(0));
        expect(NullPointerException.class, () -> src.transferTo(null, 1));
        BankAccount overflow = new BankAccount("ACC003", "Limit", Long.MAX_VALUE);
        expect(ArithmeticException.class, () -> src.transferTo(overflow, 1));
        check(src.balanceVnd() == 800_000L && overflow.balanceVnd() == Long.MAX_VALUE,
                "transfer destination overflow must preserve both accounts");

        BankResult result = new ClassesExperimentService().simulateBank(new BankRequest(
                new BankSpec("ACC100", "A", 100L),
                new BankSpec("ACC200", "B", 10L),
                List.of(new BankAction("transfer", 50L), new BankAction("withdraw", 80L),
                        new BankAction("deposit", 5L))));
        check(result.finalSource().balanceVnd() == 55L &&
                result.finalDestination().balanceVnd() == 60L, "bank service final states");
        check(!result.steps().get(1).accepted(), "insufficient funds reported as rejected step");
        check(result.steps().get(1).sourceBefore().equals(result.steps().get(1).sourceAfter()),
                "rejected bank step preserves source");
        check(result.scope().contains("IN_MEMORY"), "explicit simulation scope");
    }

    private static void testValidationAndBounds() {
        var service = new ClassesExperimentService();
        expect(IllegalArgumentException.class,
                () -> service.simulateInventory(new InventoryRequest("A", "Item", 1L, 1, List.of())));
        expect(IllegalArgumentException.class,
                () -> service.simulateInventory(new InventoryRequest("A", "Item", 1L, 1,
                        List.of(new InventoryAction("invent", 1, null)))));
        expect(IllegalArgumentException.class,
                () -> service.simulateWorkshops(new WorkshopRequest("A", 1, "B", 1, 51, 0)));
        expect(IllegalArgumentException.class,
                () -> service.simulateBank(new BankRequest(
                        new BankSpec("ACC111", "A", 1L),
                        new BankSpec("ACC111", "B", 1L),
                        List.of(new BankAction("transfer", 1L)))));
        expect(IllegalArgumentException.class,
                () -> service.simulateBank(new BankRequest(
                        new BankSpec("ACC111", "A", 1L),
                        new BankSpec("ACC222", "B", 1L),
                        List.of(new BankAction("unknown", 1L)))));
        var tooMany = new java.util.ArrayList<InventoryAction>();
        for (int i = 0; i < 21; i++) tooMany.add(new InventoryAction("restock", 1, null));
        expect(IllegalArgumentException.class,
                () -> service.simulateInventory(new InventoryRequest("A", "Item", 1L, 1, tooMany)));
    }

    private static void testControllerContract() throws Exception {
        var type = ClassesExperimentController.class;
        var route = type.getAnnotation(RequestMapping.class);
        check(route != null && List.of(route.value()).contains("/oop/classes"),
                "controller route namespace");
        check(List.of(type.getMethod("inventory", InventoryRequest.class)
                .getAnnotation(PostMapping.class).value()).contains("/inventory/experiment"),
                "inventory HTTP route");
        check(List.of(type.getMethod("workshops", WorkshopRequest.class)
                .getAnnotation(PostMapping.class).value()).contains("/workshops/experiment"),
                "workshops HTTP route");
        check(List.of(type.getMethod("bank", BankRequest.class)
                .getAnnotation(PostMapping.class).value()).contains("/bank/experiment"),
                "bank HTTP route");
        var controller = new ClassesExperimentController(new ClassesExperimentService());
        check(new ClassesExperimentExceptionHandler().invalidInput(new IllegalArgumentException("invalid")).getStatusCode().value() == 400,
                "malformed request should return HTTP 400");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) return;
            throw new AssertionError("Expected " + type.getSimpleName() +
                    " but got " + thrown.getClass().getSimpleName(), thrown);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}

package com.example.learning.oop.refactoring;

import com.example.learning.oop.refactoring.model.OrderLine;
import com.example.learning.oop.refactoring.model.ShoppingOrder;
import com.example.learning.oop.refactoring.service.CheckoutRefactoringService;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static com.example.learning.oop.refactoring.dto.RefactoringDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckoutRefactoringTest {
    private final CheckoutRefactoringService service = new CheckoutRefactoringService();

    private CheckoutComparison quote(boolean vip, long subtotal) {
        return service.compare(new CheckoutRequest(vip,
                List.of(new LineInput("SKU", subtotal, 1))));
    }

    @Test
    void explicitSpecificationProtectsVipAndShippingThresholdsIndependentlyOfBothVersions() {
        CheckoutComparison below = quote(true, 499_999);
        assertTrue(below.equivalent());
        assertEquals(0, below.after().discountVnd());
        assertEquals(30_000, below.after().shippingVnd());
        assertEquals(529_999, below.after().totalVnd());

        CheckoutComparison vipThreshold = quote(true, 500_000);
        assertTrue(vipThreshold.equivalent());
        assertEquals(50_000, vipThreshold.after().discountVnd());
        assertEquals(450_000, vipThreshold.after().payableVnd());
        assertEquals(480_000, vipThreshold.after().totalVnd());

        CheckoutComparison justBelowShipping = quote(false, 699_999);
        assertEquals(30_000, justBelowShipping.after().shippingVnd());
        assertEquals(729_999, justBelowShipping.after().totalVnd());

        CheckoutComparison atShipping = quote(false, 700_000);
        assertEquals(0, atShipping.after().shippingVnd());
        assertEquals(700_000, atShipping.after().totalVnd());

        CheckoutComparison vipWithFreeShipping = quote(true, 800_000);
        assertEquals(80_000, vipWithFreeShipping.after().discountVnd());
        assertEquals(720_000, vipWithFreeShipping.after().totalVnd());
        assertEquals("subtotal=800000;discount=80000;shipping=0;total=720000",
                vipWithFreeShipping.after().receipt());
    }

    @Test
    void seededDifferentialCasesPreserveObservableContractAndArithmetic() {
        Random random = new Random(20261020L);
        for (int iteration = 0; iteration < 100; iteration++) {
            List<LineInput> lines = new ArrayList<>();
            long expectedSubtotal = 0L;
            for (int n = 0; n < 1 + random.nextInt(8); n++) {
                long price = random.nextInt(300_000);
                int quantity = 1 + random.nextInt(5);
                lines.add(new LineInput("SKU-" + n, price, quantity));
                expectedSubtotal += price * quantity;
            }
            boolean vip = random.nextBoolean();
            var result = service.compare(new CheckoutRequest(vip, lines));
            long expectedDiscount = vip && expectedSubtotal >= 500_000
                    ? expectedSubtotal / 10 : 0;
            long expectedPayable = expectedSubtotal - expectedDiscount;
            long expectedShipping = expectedPayable >= 700_000 ? 0 : 30_000;
            assertTrue(result.equivalent(), "iteration " + iteration);
            assertEquals(result.before(), result.after());
            assertEquals(expectedSubtotal, result.after().subtotalVnd());
            assertEquals(expectedPayable + expectedShipping, result.after().totalVnd());
        }
    }

    @Test
    void orderSnapshotsPreventMutationAndRejectInvalidOrOverflowInputs() {
        List<OrderLine> callerLines = new ArrayList<>();
        callerLines.add(new OrderLine("JAVA", 40_000, 2));
        ShoppingOrder order = new ShoppingOrder(callerLines, false);
        var original = order.linesSnapshot();
        callerLines.clear();
        assertEquals(80_000, order.subtotalVnd());
        assertEquals(1, original.size());
        assertThrows(UnsupportedOperationException.class, original::clear);
        assertThrows(IllegalArgumentException.class, () -> quote(false, -1));
        assertThrows(IllegalArgumentException.class, () ->
                service.compare(new CheckoutRequest(true, List.of())));
        assertThrows(IllegalArgumentException.class, () ->
                service.compare(new CheckoutRequest(false,
                        List.of(new LineInput("SKU", 500, 0)))));
        assertThrows(ArithmeticException.class, () ->
                service.compare(new CheckoutRequest(false,
                        List.of(new LineInput("SKU", Long.MAX_VALUE, 2)))));
    }
}

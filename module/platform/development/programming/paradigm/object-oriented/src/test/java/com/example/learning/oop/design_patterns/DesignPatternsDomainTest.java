package com.example.learning.oop.design_patterns;

import com.example.learning.oop.design_patterns.model.PricingPipeline;
import com.example.learning.oop.design_patterns.model.TaskBoard;
import com.example.learning.oop.design_patterns.model.TaskCommandManager;
import com.example.learning.oop.design_patterns.model.ReportPatterns;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesignPatternsDomainTest {
    @Test
    void decoratorCompositionOrderReallyChangesResults() {
        var a = PricingPipeline.calculate(100_000L, 20_000L, "MEMBER_5", "DISCOUNT_THEN_FEE");
        var b = PricingPipeline.calculate(100_000L, 20_000L, "MEMBER_5", "FEE_THEN_DISCOUNT");
        assertEquals(115_000L, a.payableVnd());
        assertEquals(114_000L, b.payableVnd());
        assertEquals(5_000L, a.discountVnd());
        assertEquals(6_000L, b.discountVnd());
        assertEquals("BASE:100000", a.executionTrace().get(0));
        assertEquals("DISCOUNT_MEMBER_5:5000", a.executionTrace().get(1));
        assertEquals("DELIVERY:20000", a.executionTrace().get(2));
        assertEquals("DELIVERY:20000", b.executionTrace().get(1));
        assertEquals("DISCOUNT_MEMBER_5:6000", b.executionTrace().get(2));
    }

    @Test
    void strategyDiscountIsBoundedAndRoundingIsDeterministic() {
        assertEquals(10_000L, PricingPipeline.calculate(20_000L, 10_000L,
                "VOUCHER_30000", "DISCOUNT_THEN_FEE").payableVnd());
        assertEquals(0L, PricingPipeline.calculate(20_000L, 10_000L,
                "VOUCHER_30000", "FEE_THEN_DISCOUNT").payableVnd());
        assertEquals(0L, PricingPipeline.calculate(0L, 0L,
                "MEMBER_5", "FEE_THEN_DISCOUNT").payableVnd());
        assertEquals(19L, PricingPipeline.calculate(20L, 0L,
                "MEMBER_5", "DISCOUNT_THEN_FEE").payableVnd());
        assertEquals(20L, PricingPipeline.calculate(20L, 0L,
                "NONE", "DISCOUNT_THEN_FEE").payableVnd());
    }

    @Test
    void pricingRejectsInvalidInputAndUnknownImplementations() {
        assertThrows(IllegalArgumentException.class, () -> PricingPipeline.calculate(-1, 0, "NONE", "DISCOUNT_THEN_FEE"));
        assertThrows(IllegalArgumentException.class, () -> PricingPipeline.calculate(1, -1, "NONE", "DISCOUNT_THEN_FEE"));
        assertThrows(IllegalArgumentException.class, () -> PricingPipeline.calculate(1, 0, "UNKNOWN", "DISCOUNT_THEN_FEE"));
        assertThrows(IllegalArgumentException.class, () -> PricingPipeline.calculate(1, 0, "NONE", "UNKNOWN"));
    }

    @Test
    void commandManagerMaintainsUndoRedoAndClearsRedoAfterNewCommand() {
        TaskBoard board = new TaskBoard();
        var events = new TaskCommandManager.Events();
        events.subscribe("AUDIT", ignored -> {});
        events.subscribe("NOTIFY", ignored -> {});
        var manager = new TaskCommandManager(events);
        manager.execute(new TaskCommandManager.AddTask(board, "T-1", "Learn Java"));
        manager.execute(new TaskCommandManager.CompleteTask(board, "T-1"));
        assertTrue(board.snapshot().get(0).done());
        manager.undo();
        assertFalse(board.snapshot().get(0).done());
        assertEquals(1, manager.redoDepth());
        manager.redo();
        assertTrue(board.snapshot().get(0).done());
        manager.undo();
        manager.execute(new TaskCommandManager.AddTask(board, "T-2", "Write tests"));
        assertEquals(0, manager.redoDepth());
        assertThrows(IllegalStateException.class, manager::redo);
        assertEquals(2, board.snapshot().size());
        assertEquals(12, events.deliveries().size());
    }

    @Test
    void failedCommandNeverChangesHistoryOrEmitsEvent() {
        TaskBoard board = new TaskBoard();
        var events = new TaskCommandManager.Events();
        events.subscribe("AUDIT", ignored -> {});
        var manager = new TaskCommandManager(events);
        assertThrows(IllegalStateException.class,
                () -> manager.execute(new TaskCommandManager.CompleteTask(board, "MISSING")));
        assertEquals(0, manager.undoDepth());
        assertTrue(events.deliveries().isEmpty());
        manager.execute(new TaskCommandManager.AddTask(board, "T-1", "Valid"));
        List<String> previous = events.deliveries();
        assertThrows(IllegalStateException.class,
                () -> manager.execute(new TaskCommandManager.AddTask(board, "T-1", "Duplicate")));
        assertEquals(previous, events.deliveries());
        assertEquals(1, manager.undoDepth());
        assertEquals(1, board.snapshot().size());
    }

    @Test
    void unsubscribeStopsOnlyOneObserverAndErrorsAreIsolated() {
        var events = new TaskCommandManager.Events();
        events.subscribe("BROKEN", ignored -> { throw new IllegalStateException("simulated listener failure"); });
        events.subscribe("OK", ignored -> {});
        TaskBoard board = new TaskBoard();
        var manager = new TaskCommandManager(events);
        manager.execute(new TaskCommandManager.AddTask(board, "T-1", "First"));
        assertEquals(List.of("BROKEN:FAILED:EXECUTE_ADD:T-1", "OK:EXECUTE_ADD:T-1"), events.deliveries());
        events.unsubscribe("BROKEN");
        manager.execute(new TaskCommandManager.AddTask(board, "T-2", "Second"));
        assertEquals("OK:EXECUTE_ADD:T-2", events.deliveries().get(2));
        assertThrows(IllegalStateException.class, () -> events.unsubscribe("BROKEN"));
        assertEquals(2, board.snapshot().size());
    }

    @Test
    void reportTemplateFixesOrderingForBothCreationalFamilies() {
        List<ReportPatterns.Sale> sales = List.of(
                new ReportPatterns.Sale("SALE-1", 120L),
                new ReportPatterns.Sale("SALE-2", 30L));
        ReportPatterns.ReportCreator text = new ReportPatterns.TextCreator();
        ReportPatterns.ReportCreator csv = new ReportPatterns.CsvCreator();
        var a = text.create(sales);
        var b = csv.create(sales);
        assertEquals(150L, a.totalVnd());
        assertEquals(a.totalVnd(), b.totalVnd());
        assertEquals(2, b.count());
        assertEquals(List.of("HEADER", "ROW:SALE-1", "ROW:SALE-2", "SUMMARY"), a.executionTrace());
        assertEquals(a.executionTrace(), b.executionTrace());
        assertEquals("SALES REPORT\nSALE-1 -> 120 VND\nSALE-2 -> 30 VND\nTOTAL (2): 150 VND", a.content());
        assertEquals("saleId,amountVnd\nSALE-1,120\nSALE-2,30\nTOTAL,150", b.content());
    }

    @Test
    void reportRejectsBadRowsAndPreservesImmutableTrace() {
        var creator = new ReportPatterns.TextCreator();
        assertThrows(IllegalArgumentException.class, () -> new ReportPatterns.Sale("not_upper", 1L));
        assertThrows(IllegalArgumentException.class, () -> new ReportPatterns.Sale("SALE-1", -1L));
        assertThrows(IllegalArgumentException.class, () -> creator.create(List.of()));
        var withNull = new ArrayList<ReportPatterns.Sale>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> creator.create(withNull));
        var result = creator.create(List.of(new ReportPatterns.Sale("SALE-1", 0L)));
        assertThrows(UnsupportedOperationException.class, () -> result.executionTrace().clear());
    }
}

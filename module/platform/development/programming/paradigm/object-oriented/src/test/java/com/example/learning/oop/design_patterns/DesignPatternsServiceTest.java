package com.example.learning.oop.design_patterns;

import com.example.learning.oop.design_patterns.service.DesignPatternExperimentService;
import static com.example.learning.oop.design_patterns.dto.DesignPatternDtos.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesignPatternsServiceTest {
    private final DesignPatternExperimentService service = new DesignPatternExperimentService();

    @Test
    void pricingShowsSelectedOrderAndOtherRealAlternative() {
        var result = service.price(new PricingRequest(100_000L, 20_000L, "MEMBER_5", "FEE_THEN_DISCOUNT"));
        assertEquals(114_000L, result.selected().payableVnd());
        assertEquals(2, result.allOrders().size());
        assertEquals(115_000L, result.allOrders().get(0).payableVnd());
        assertEquals(114_000L, result.allOrders().get(1).payableVnd());
        assertTrue(result.scope().contains("REQUEST_LOCAL"));
    }

    @Test
    void pricingRejectsMissingInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> service.price(new PricingRequest(null, 0L, "NONE", "DISCOUNT_THEN_FEE")));
        assertThrows(IllegalArgumentException.class,
                () -> service.price(new PricingRequest(1L, 0L, "INVALID", "DISCOUNT_THEN_FEE")));
    }

    @Test
    void tasksExposeActualUndoRedoStateAndObserverDeliveries() {
        TasksResult result = service.tasks(new TasksRequest(List.of(
                new TaskAction("ADD", "T-1", "Original"),
                new TaskAction("COMPLETE", "T-1", null),
                new TaskAction("UNDO", null, null),
                new TaskAction("REDO", null, null),
                new TaskAction("UNSUBSCRIBE_NOTIFY", null, null),
                new TaskAction("UNDO", null, null))));
        assertTrue(result.initial().tasks().isEmpty());
        assertTrue(result.steps().get(1).after().tasks().get(0).done());
        assertFalse(result.steps().get(2).after().tasks().get(0).done());
        assertTrue(result.steps().get(3).after().tasks().get(0).done());
        assertEquals(9, result.current().observerDeliveries().size());
        assertEquals("AUDIT:UNDO_COMPLETE:T-1", result.current().observerDeliveries().get(8));
        assertFalse(result.current().tasks().get(0).done());
        assertEquals(1, result.current().redoDepth());
    }

    @Test
    void tasksKeepSnapshotAndEventsWhenActionRejected() {
        TasksResult result = service.tasks(new TasksRequest(List.of(
                new TaskAction("COMPLETE", "UNKNOWN", null),
                new TaskAction("ADD", "T-1", "First"),
                new TaskAction("ADD", "T-1", "Duplicate"),
                new TaskAction("UNDO", null, null),
                new TaskAction("UNDO", null, null))));
        assertFalse(result.steps().get(0).accepted());
        assertFalse(result.steps().get(2).accepted());
        assertEquals(result.steps().get(2).before(), result.steps().get(2).after());
        assertFalse(result.steps().get(4).accepted());
        assertEquals(result.steps().get(4).before(), result.steps().get(4).after());
        assertEquals(4, result.current().observerDeliveries().size());
        assertTrue(result.current().tasks().isEmpty());
    }

    @Test
    void everyTaskRequestHasIndependentReceiver() {
        TasksRequest req = new TasksRequest(List.of(new TaskAction("ADD", "T-1", "First")));
        assertEquals(1, service.tasks(req).current().tasks().size());
        assertEquals(1, service.tasks(req).current().tasks().size());
    }

    @Test
    void reportSharesAlgorithmButChangesOutputByCreator() {
        List<SaleInput> input = List.of(new SaleInput("S-01", 100L), new SaleInput("S-02", 200L));
        ReportResult text = service.report(new ReportRequest("TEXT", input));
        ReportResult csv = service.report(new ReportRequest("CSV", input));
        assertEquals(300L, text.totalVnd());
        assertEquals(text.totalVnd(), csv.totalVnd());
        assertEquals(text.executionTrace(), csv.executionTrace());
        assertTrue(text.content().startsWith("SALES REPORT"));
        assertTrue(csv.content().startsWith("saleId,amountVnd"));
    }

    @Test
    void reportAndTasksEnforceInputBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> service.report(new ReportRequest("UNKNOWN", List.of(new SaleInput("S-01", 1L)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.report(new ReportRequest("CSV", List.of(new SaleInput("S-01", null)))));
        var actions = new ArrayList<TaskAction>();
        for(int i=0;i<21;i++) actions.add(new TaskAction("UNDO", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.tasks(new TasksRequest(actions)));
        assertThrows(IllegalArgumentException.class, () -> service.tasks(
                new TasksRequest(List.of(new TaskAction("BOGUS", null, null)))));
    }
}

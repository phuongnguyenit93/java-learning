package com.example.learning.oop.inheritance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.learning.oop.inheritance.dto.InheritanceExperimentDto.ConstructorRequest;
import com.example.learning.oop.inheritance.service.ConstructorTraceService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConstructorTraceServiceTest {
    private final ConstructorTraceService service = new ConstructorTraceService();

    @Test
    void realParentConstructorDispatchObservesNullBeforeChildInitializer() {
        var trace = service.trace(new ConstructorRequest("READY"));
        assertEquals("null", trace.unsafeValueObservedByParent());
        assertEquals("READY", trace.finalValue());
        assertEquals(List.of("parent constructor begins",
                "parent sees overridden value: null", "child constructor completed: READY"),
                trace.unsafeSequence());
        assertEquals(List.of("parent constructor: no overridable calls",
                "child constructor completed: READY", "post-construction value: READY"),
                trace.safeSequence());
        assertTrue(trace.simulatedOnly());
    }

    @Test
    void independentInvocationsNeverShareMutableTraces() {
        var first = service.trace(new ConstructorRequest("Alpha"));
        var second = service.trace(new ConstructorRequest("Beta"));
        assertEquals("Alpha", first.finalValue());
        assertEquals("Beta", second.finalValue());
        assertThrows(UnsupportedOperationException.class, () -> first.safeSequence().clear());
    }

    @Test
    void emptyOrOversizedLabelsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.trace(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.trace(new ConstructorRequest("  ")));
        assertThrows(IllegalArgumentException.class,
                () -> service.trace(new ConstructorRequest("X".repeat(81))));
    }
}

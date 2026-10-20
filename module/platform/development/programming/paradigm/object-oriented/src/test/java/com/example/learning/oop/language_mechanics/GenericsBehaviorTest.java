package com.example.learning.oop.language_mechanics;

import com.example.learning.oop.language_mechanics.model.NumericTransfers;
import com.example.learning.oop.language_mechanics.model.TypedCache;
import com.example.learning.oop.language_mechanics.service.GenericBatchService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.example.learning.oop.language_mechanics.dto.LanguageMechanicsDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class GenericsBehaviorTest {
    @Test
    void wildcardProducerCopiesDifferentNumberTypesIntoCompatibleConsumer() {
        List<Integer> integers = List.of(4, 9, 2);
        List<Double> decimals = List.of(2.5, 0.25);
        List<Number> destination = new ArrayList<>();
        NumericTransfers.copy(integers, destination);
        NumericTransfers.copy(decimals, destination);
        assertEquals(List.of(4, 9, 2, 2.5, 0.25), destination);
        assertEquals(new BigDecimal("17.75"), NumericTransfers.sum(destination));
        assertEquals(9, NumericTransfers.greatest(integers));
    }

    @Test
    void genericCopyWithSameListUsesSnapshotAndTypedCachePreservesValueTypes() {
        List<String> self = new ArrayList<>(List.of("A", "B"));
        NumericTransfers.copy(self, self);
        assertEquals(List.of("A", "B", "A", "B"), self);

        TypedCache<Long, String> labels = new TypedCache<>();
        labels.add(7L, "BOOK");
        assertEquals("BOOK", labels.lookup(7L).orElseThrow());
        assertTrue(labels.lookup(8L).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> labels.add(7L, "DUPLICATE"));
        assertThrows(NullPointerException.class, () -> labels.add(null, "BAD"));
        assertEquals(1, labels.size());
    }

    @Test
    void serviceReturnsComputedDataAndRejectsNonfiniteAndDuplicateInputs() {
        GenericBatchResult result = new GenericBatchService().process(new GenericBatchRequest(
                List.of(6, 2, 8), List.of(1.5, 0.25),
                List.of(new CacheEntry("A", 0), new CacheEntry("B", 4)),
                List.of("A", "MISSING", "B")));
        assertEquals("17.75", result.sum());
        assertEquals(8, result.largestInteger());
        assertEquals(List.of("6", "2", "8", "1.5", "0.25"), result.copiedValues());
        assertEquals(0, result.found().get("A"));
        assertEquals(4, result.found().get("B"));
        assertEquals(List.of("MISSING"), result.missing());
        assertThrows(IllegalArgumentException.class, () ->
                new GenericBatchService().process(new GenericBatchRequest(
                        List.of(1), List.of(Double.NaN), List.of(), List.of())));
        assertThrows(IllegalArgumentException.class, () ->
                new GenericBatchService().process(new GenericBatchRequest(
                        List.of(), List.of(), List.of(new CacheEntry("X", 1),
                        new CacheEntry("X", 2)), List.of("X"))));
    }
}

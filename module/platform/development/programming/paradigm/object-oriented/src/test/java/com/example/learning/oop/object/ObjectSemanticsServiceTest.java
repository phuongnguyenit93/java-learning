package com.example.learning.oop.object;

import com.example.learning.oop.object.dto.ObjectExperimentDto;
import com.example.learning.oop.object.service.ObjectSemanticsService;
import com.example.learning.oop.object.service.ObjectCheckoutService;
import com.example.learning.oop.object.controller.ObjectExperimentsController;
import com.example.learning.oop.object.exception.ObjectExperimentExceptionHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectSemanticsServiceTest {
    private final ObjectSemanticsService service = new ObjectSemanticsService();

    @Test
    void referencesShowAliasingMutationAndPassByValueReassignment() {
        var r = service.references(new ObjectExperimentDto.ReferenceRequest("Counter", 10, 4));
        assertTrue(r.aliasIsSameInstance());
        assertTrue(r.separatelyCreatedIsDifferentInstance());
        assertTrue(r.separatelyCreatedInitiallyHadSameState());
        assertTrue(r.callerReferenceSurvivesParameterReassignment());
        assertEquals(10, r.before());
        assertEquals(14, r.afterThroughOriginal());
        assertEquals(14, r.afterThroughAlias());
        assertEquals(10, r.independentInstanceValue());
    }

    @Test
    void equalitySeparatesIdentityEqualsAndHashBasedMembership() {
        var sameValue = service.equality(new ObjectExperimentDto.EqualityRequest("P01", "P01"));
        assertFalse(sameValue.sameInstance());
        assertTrue(sameValue.valueEquals());
        assertTrue(sameValue.hashCodesEqual());
        assertEquals(1, sameValue.hashSetSize());
        assertFalse(sameValue.defaultObjectEquals());
        assertTrue(sameValue.aliasIsSameInstance());

        var differentValue = service.equality(new ObjectExperimentDto.EqualityRequest("P01", "P02"));
        assertFalse(differentValue.valueEquals());
        assertEquals(2, differentValue.hashSetSize());
    }

    @Test
    void copiesShowShallowNestedAliasingVersusIndependentDeepValue() {
        var r = service.copies(new ObjectExperimentDto.CopyRequest("old", "updated", "additional"));
        assertTrue(r.outerListsAreDifferent());
        assertTrue(r.shallowCopySharesElement());
        assertTrue(r.deepCopyHasIndependentElement());
        assertEquals(List.of("updated", "additional"), r.originalAfterMutation());
        assertEquals(List.of("updated"), r.shallowAfterMutation());
        assertEquals(List.of("old"), r.deepAfterMutation());
        assertEquals(List.of("updated"), r.unmodifiableSnapshotAfterMutation());
        assertEquals("old", r.immutableValueSnapshot());
    }

    @Test
    void absenceDistinguishesMissingObjectFromMissingOptionalField() {
        var found = service.absence(new ObjectExperimentDto.AbsenceRequest("C01", null));
        assertTrue(found.found());
        assertFalse(found.optionalAddressPresent());
        assertEquals("Lan", found.customerName());
        assertEquals(List.of(), found.addresses());
        assertTrue(found.emptyCollectionMeansNoItems());

        var withAddress = service.absence(new ObjectExperimentDto.AbsenceRequest("C01", "Hanoi"));
        assertTrue(withAddress.optionalAddressPresent());
        assertEquals(List.of("Hanoi"), withAddress.addresses());

        var missing = service.absence(new ObjectExperimentDto.AbsenceRequest("C99", "Hanoi"));
        assertFalse(missing.found());
        assertNull(missing.customerName());
        assertFalse(missing.optionalAddressPresent());
        assertEquals(List.of(), missing.addresses());
    }

    @Test
    void rejectsMissingOrOutOfBoundsInputsInsteadOfThrowingNullPointer() {
        assertThrows(IllegalArgumentException.class, () -> service.references(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.references(new ObjectExperimentDto.ReferenceRequest(" ", 1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.references(new ObjectExperimentDto.ReferenceRequest("A", 1, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.references(new ObjectExperimentDto.ReferenceRequest("A", 100, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.equality(null));
        assertThrows(IllegalArgumentException.class,
                () -> service.equality(new ObjectExperimentDto.EqualityRequest("ok", "")));
        assertThrows(IllegalArgumentException.class,
                () -> service.copies(new ObjectExperimentDto.CopyRequest("A", null, "B")));
        assertThrows(IllegalArgumentException.class,
                () -> service.absence(new ObjectExperimentDto.AbsenceRequest(null, "")));
    }

    @Test
    void experimentsStartFreshEveryTime() {
        var a = service.references(new ObjectExperimentDto.ReferenceRequest("x", 0, 1));
        var b = service.references(new ObjectExperimentDto.ReferenceRequest("x", 0, 1));
        assertEquals(1, a.afterThroughOriginal());
        assertEquals(1, b.afterThroughOriginal());
        assertNotNull(b.name());
    }
}

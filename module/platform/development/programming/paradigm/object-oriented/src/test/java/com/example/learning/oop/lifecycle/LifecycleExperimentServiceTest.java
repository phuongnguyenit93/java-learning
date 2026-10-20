package com.example.learning.oop.lifecycle;

import static org.junit.jupiter.api.Assertions.*;

import com.example.learning.oop.lifecycle.dto.LifecycleDtos.*;
import com.example.learning.oop.lifecycle.model.CourseEnrollment;
import com.example.learning.oop.lifecycle.service.LifecycleExperimentService;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LifecycleExperimentServiceTest {
    private final LifecycleExperimentService service = new LifecycleExperimentService();

    @Test
    void constructorFactoryAndBuilderPreserveOneInvariant() {
        for (CreationStyle style : CreationStyle.values()) {
            CreationResult response = service.create(new CreationRequest(
                    "JAVA-21", 3, List.of("AN", "BINH"), style));
            assertEquals(style, response.style());
            assertEquals("JAVA-21", response.courseCode());
            assertEquals(1, response.availableSeats());
            assertEquals(List.of("AN", "BINH"), response.students());
        }
        assertEquals(2, service.create(new CreationRequest(
                "JAVA-21", 2, List.of(), CreationStyle.CONSTRUCTOR)).availableSeats());
    }

    @Test
    void invalidCreationIsRejectedForAllPathsAndDefensiveCopyHolds() {
        for (CreationStyle style : CreationStyle.values()) {
            assertThrows(IllegalArgumentException.class, () -> service.create(
                    new CreationRequest("JAVA", 1, List.of("A", "B"), style)));
            assertThrows(IllegalArgumentException.class, () -> service.create(
                    new CreationRequest("JAVA", 3, List.of("A", "A"), style)));
            assertThrows(IllegalArgumentException.class, () -> service.create(
                    new CreationRequest("", 3, List.of("A"), style)));
        }
        assertThrows(IllegalArgumentException.class, () -> service.create(
                new CreationRequest("OK", null, List.of(), CreationStyle.BUILDER)));
        ArrayList<String> ownedByCaller = new ArrayList<>(List.of("A"));
        CourseEnrollment enrollment = CourseEnrollment.of("JAVA", 2, ownedByCaller);
        ownedByCaller.add("B");
        assertEquals(List.of("A"), enrollment.students());
        assertThrows(UnsupportedOperationException.class, () -> enrollment.students().add("C"));
        assertEquals(1, enrollment.availableSeats());
    }

    @Test
    void explicitRootsAndCyclesAreTraversedWithoutAssumingGc() {
        ReferenceResult result = service.references(new ReferenceRequest(
                List.of(new NodeInput("A", List.of("B")),
                        new NodeInput("B", List.of("A")),
                        new NodeInput("C", List.of())),
                List.of("A"),
                List.of(new RootStep(RootAction.RELEASE, "A"),
                        new RootStep(RootAction.RETAIN, "C"))));
        assertEquals(3, result.nodeCount());
        assertEquals(3, result.snapshots().size());
        assertEquals(List.of("A", "B"), result.snapshots().get(0).reachableFromRoots());
        assertEquals(List.of("C"), result.snapshots().get(0).notReachableFromRoots());
        assertEquals(List.of(), result.snapshots().get(1).reachableFromRoots());
        assertEquals(List.of("A", "B", "C"), result.snapshots().get(1).notReachableFromRoots());
        assertEquals(List.of("C"), result.snapshots().get(2).reachableFromRoots());
        assertTrue(result.interpretation().contains("not observed JVM GC roots"));
    }

    @Test
    void badGraphOrRootMutationIsRejectedWithoutSharedState() {
        assertThrows(IllegalArgumentException.class, () -> service.references(new ReferenceRequest(
                List.of(new NodeInput("A", List.of("MISSING"))), List.of(), List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.references(new ReferenceRequest(
                List.of(new NodeInput("A", List.of()), new NodeInput("A", List.of())),
                List.of(), List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.references(new ReferenceRequest(
                List.of(new NodeInput("A", List.of())), List.of(),
                List.of(new RootStep(RootAction.RELEASE, "A")))));
        ReferenceResult fresh = service.references(new ReferenceRequest(
                List.of(new NodeInput("A", List.of())), List.of("A"), List.of()));
        assertEquals(List.of("A"), fresh.snapshots().get(0).reachableFromRoots());
    }

    @Test
    void tryWithResourcesClosesInReverseOrder() {
        ResourceResult result = service.resources(new ResourceRequest(false, false, false));
        assertTrue(result.completedWithoutFailure());
        assertNull(result.primaryFailure());
        assertEquals(List.of("open:outer", "open:inner", "body:start", "body:success",
                "close:inner", "close:outer"), result.trace());
        assertEquals(Boolean.TRUE, result.closed().get("inner"));
        assertEquals(Boolean.TRUE, result.closed().get("outer"));
        assertEquals(List.of(), result.suppressedFailures());
    }

    @Test
    void bodyErrorKeepsPrimaryAndSuppressesBothCloseFailures() {
        ResourceResult result = service.resources(new ResourceRequest(true, true, true));
        assertFalse(result.completedWithoutFailure());
        assertEquals("body failed", result.primaryFailure());
        assertEquals(List.of("close failed: inner", "close failed: outer"),
                result.suppressedFailures());
        assertEquals(List.of("open:outer", "open:inner", "body:start",
                "close:inner", "close:outer"), result.trace());
        assertTrue(result.closed().values().stream().allMatch(Boolean::booleanValue));
    }

    @Test
    void innerCloseFailureIsPrimaryWhenBodyCompletes() {
        ResourceResult result = service.resources(new ResourceRequest(false, true, true));
        assertEquals("close failed: inner", result.primaryFailure());
        assertEquals(List.of("close failed: outer"), result.suppressedFailures());
        assertFalse(result.completedWithoutFailure());
    }

    @Test
    void earlyCallbackObservesDefaultNullButSafeFactoryObservesFinalValue() {
        PublicationResult output = service.publication(new PublicationRequest("REPORT"));
        assertNull(output.unsafeObservedOnRegister());
        assertEquals("REPORT", output.unsafeObservedAfterConstruction());
        assertEquals("REPORT", output.safeObservedOnRegister());
        assertEquals("REPORT", output.safeObservedAfterConstruction());
        assertEquals(1, output.unsafeSubscriptions());
        assertEquals(1, output.safeSubscriptions());
        assertThrows(IllegalArgumentException.class, () ->
                service.publication(new PublicationRequest("")));
    }
}

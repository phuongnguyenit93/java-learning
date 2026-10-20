package com.example.learning.oop.modeling;

import com.example.learning.oop.modeling.service.ModelingExperimentService;
import static com.example.learning.oop.modeling.dto.ModelingDtos.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelingServiceTest {
    private final ModelingExperimentService service = new ModelingExperimentService();

    private LibraryRequest request(List<LibraryAction> actions) {
        return new LibraryRequest(
                List.of(new TitleInput("TITLE-1", "Modern Java")),
                List.of(new MemberInput("MEM-1", true), new MemberInput("MEM-2", true)),
                List.of(new CopyInput("COPY-1", "TITLE-1"), new CopyInput("COPY-2", "TITLE-1")),
                actions);
    }

    @Test
    void libraryStepsShowRealBeforeAfterAndOrderedCollaborators() {
        LibraryResult result = service.library(request(List.of(
                new LibraryAction("BORROW", "MEM-1", "COPY-1", null, "2026-10-20"),
                new LibraryAction("BORROW", "MEM-2", "COPY-1", null, "2026-10-20"),
                new LibraryAction("RETURN", null, null, "LOAN-1", "2026-10-25"),
                new LibraryAction("BORROW", "MEM-2", "COPY-1", null, "2026-10-26"))));
        assertTrue(result.invariantsSatisfied());
        assertTrue(result.steps().get(0).accepted());
        assertEquals("LOAN-1", result.steps().get(0).affectedLoanId());
        assertEquals("2026-11-03", result.steps().get(0).after().loans().get(0).dueOn());
        assertFalse(result.steps().get(1).accepted());
        assertEquals("BR-03", result.steps().get(1).rule());
        assertEquals(result.steps().get(1).before(), result.steps().get(1).after());
        assertFalse(result.steps().get(1).calls().contains("LibraryModel.saveLoan"));
        assertEquals("RETURNED", result.current().loans().get(0).status());
        assertEquals("ACTIVE", result.current().loans().get(1).status());
        assertTrue(result.current().copies().get(0).borrowed());
        assertEquals(0, result.current().members().get(0).activeLoans());
        assertEquals(1, result.current().members().get(1).activeLoans());
        assertEquals(2, result.current().loans().size());
        assertEquals(result.initial().copies().get(0).titleCode(),
                     result.initial().copies().get(1).titleCode());
    }

    @Test
    void libraryInitialGraphIsUnaffectedByLaterMutations() {
        LibraryResult result = service.library(request(List.of(
                new LibraryAction("BORROW", "MEM-1", "COPY-1", null, "2026-10-20"))));
        assertEquals(0, result.initial().loans().size());
        assertEquals(0, result.initial().members().get(0).activeLoans());
        assertEquals(1, result.current().members().get(0).activeLoans());
        assertThrows(UnsupportedOperationException.class, () -> result.current().loans().clear());
    }

    @Test
    void libraryRejectsInvalidRequestStructuresBeforeMutation() {
        assertThrows(IllegalArgumentException.class, () -> service.library(
                request(List.of(new LibraryAction("BORROW", "MEM-1", "COPY-1", null, "bad")))));
        assertThrows(IllegalArgumentException.class, () -> service.library(
                new LibraryRequest(List.of(new TitleInput("TITLE-1", "Java")),
                        List.of(new MemberInput("MEM-1", true)),
                        List.of(new CopyInput("COPY-1", "NOT-FOUND")),
                        List.of(new LibraryAction("BORROW", "MEM-1", "COPY-1", null, "2026-10-20")))));
        assertThrows(IllegalArgumentException.class, () -> service.library(
                request(List.of(new LibraryAction("UNKNOWN", "MEM-1", "COPY-1", null, "2026-10-20")))));
        assertThrows(IllegalArgumentException.class, () -> service.library(
                request(List.of(new LibraryAction("BORROW", "MEM-1", "COPY-1", null, "2026-10-20"),
                        new LibraryAction("RETURN", null, null, "LOAN-1", "2026-10-19")))));
    }

    @Test
    void libraryUnknownMemberIsBusinessRejectionWithUnchangedSnapshot() {
        var result = service.library(request(List.of(
                new LibraryAction("BORROW", "MEM-77", "COPY-1", null, "2026-10-20"))));
        assertFalse(result.steps().get(0).accepted());
        assertEquals("UNKNOWN_MEMBER", result.steps().get(0).rule());
        assertEquals(result.initial(), result.current());
    }

    @Test
    void orderStateMachineExposesRejectedStepsWithoutStateChanges() {
        var result = service.order(new OrderRequest("ORD-1", 100L,
                List.of(new OrderAction("SHIP", null, "TRACK-1"),
                        new OrderAction("SUBMIT", null, null),
                        new OrderAction("CONFIRM_PAYMENT", 99L, null),
                        new OrderAction("CONFIRM_PAYMENT", 100L, null),
                        new OrderAction("START_PACKING", null, null),
                        new OrderAction("SHIP", null, "TRACK-1"),
                        new OrderAction("CONFIRM_DELIVERY", null, null))));
        assertEquals("DRAFT", result.initial().state());
        assertFalse(result.steps().get(0).accepted());
        assertEquals("INVALID_TRANSITION", result.steps().get(0).rule());
        assertFalse(result.steps().get(2).accepted());
        assertEquals("PAYMENT_GUARD", result.steps().get(2).rule());
        assertEquals(result.steps().get(2).before(), result.steps().get(2).after());
        assertEquals("DELIVERED", result.current().state());
        assertEquals("TRACK-1", result.current().trackingCode());
    }

    @Test
    void limitsAndUnknownEventsProduce400StyleInputExceptions() {
        var actions = new ArrayList<OrderAction>();
        for (int i = 0; i < 21; i++) actions.add(new OrderAction("SUBMIT", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.order(new OrderRequest("ORD-1", 100L, actions)));
        assertThrows(IllegalArgumentException.class,
                () -> service.order(new OrderRequest("ORD-1", 100L,
                        List.of(new OrderAction("NO_SUCH_EVENT", null, null)))));
        assertThrows(IllegalArgumentException.class,
                () -> service.order(new OrderRequest("ORD-1", -1L,
                        List.of(new OrderAction("SUBMIT", null, null)))));
    }
}

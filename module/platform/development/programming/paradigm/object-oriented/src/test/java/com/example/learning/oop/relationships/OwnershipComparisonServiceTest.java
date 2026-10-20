package com.example.learning.oop.relationships;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.learning.oop.relationships.dto.RelationshipDtos.OrderLineInput;
import com.example.learning.oop.relationships.dto.RelationshipDtos.OwnershipRequest;
import com.example.learning.oop.relationships.service.OwnershipComparisonService;
import java.util.List;
import org.junit.jupiter.api.Test;

class OwnershipComparisonServiceTest {
    private final OwnershipComparisonService service = new OwnershipComparisonService();

    private OwnershipRequest request(boolean close) {
        return new OwnershipRequest("TEAM-A", List.of("An", "Binh"), "An",
                "ORDER-1", List.of(new OrderLineInput("BOOK", 2, 20_000),
                        new OrderLineInput("PEN", 1, 5_000)), close);
    }

    @Test
    void membersRemainIndependentWhileOrderOwnsItsLines() {
        var r = service.compare(request(true));
        assertEquals(List.of("Binh"), r.teamMembersAfterRemoval());
        assertTrue(r.removedMemberStillExistsIndependently());
        assertEquals(45_000L, r.orderTotalBeforeCloseVnd());
        assertEquals(2, r.ownedLinesBeforeClose());
        assertEquals(0, r.ownedLinesAfterAction());
        assertTrue(r.orderClosed());
        assertTrue(r.simulatedOnly());
        assertThrows(UnsupportedOperationException.class, r.teamMembersAfterRemoval()::clear);
    }

    @Test
    void openOrderRetainsOwnLineObjects() {
        var r = service.compare(request(false));
        assertEquals(2, r.ownedLinesAfterAction());
        assertFalse(r.orderClosed());
        assertEquals(45_000L, r.orderTotalBeforeCloseVnd());
    }

    @Test
    void rejectsDuplicateAndInvalidCompositionPartsOrMissingMembers() {
        assertThrows(IllegalArgumentException.class, () -> service.compare(null));
        assertThrows(IllegalArgumentException.class, () -> service.compare(new OwnershipRequest(
                "T", List.of("X"), "Y", "O", List.of(new OrderLineInput("SKU", 1, 1)), true)));
        assertThrows(IllegalArgumentException.class, () -> service.compare(new OwnershipRequest(
                "T", List.of("X", "X"), "X", "O",
                List.of(new OrderLineInput("SKU", 1, 1)), true)));
        assertThrows(IllegalArgumentException.class, () -> service.compare(new OwnershipRequest(
                "T", List.of("X"), "X", "O",
                List.of(new OrderLineInput("SKU", 1, 1), new OrderLineInput("SKU", 1, 2)), true)));
        assertThrows(IllegalArgumentException.class, () -> service.compare(new OwnershipRequest(
                "T", List.of("X"), "X", "O", List.of(new OrderLineInput("SKU", -1, 1)), true)));
        assertThrows(ArithmeticException.class, () -> service.compare(new OwnershipRequest(
                "T", List.of("X"), "X", "O",
                List.of(new OrderLineInput("SKU", 2, Long.MAX_VALUE)), true)));
    }
}

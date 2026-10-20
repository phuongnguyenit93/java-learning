package com.example.learning.oop.class_design;

import com.example.learning.oop.class_design.model.BookCopy;
import com.example.learning.oop.class_design.model.Loan;
import com.example.learning.oop.class_design.model.LoanPolicy;
import com.example.learning.oop.class_design.service.LibraryScenarioService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.example.learning.oop.class_design.dto.ClassDesignDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassDesignLibraryTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);

    @Test
    void borrowingLimitCopyUniquenessReturnAndQueriesExposeActualTransitions() {
        LibraryRequest input = new LibraryRequest(1, 14,
                List.of(new BookInput("A", "Java"), new BookInput("B", "OOP")),
                List.of(new LibraryAction("BORROW", "MEM1", "A", DAY),
                        new LibraryAction("QUERY", null, null, null),
                        new LibraryAction("BORROW", "MEM2", "A", DAY),
                        new LibraryAction("BORROW", "MEM1", "B", DAY),
                        new LibraryAction("RETURN", null, "A", DAY.plusDays(17)),
                        new LibraryAction("BORROW", "MEM1", "B", DAY.plusDays(18)),
                        new LibraryAction("RETURN", null, "A", DAY.plusDays(18))));

        LibraryResult result = new LibraryScenarioService().run(input);
        assertEquals(List.of("BORROWED", "QUERY_ONLY", "COPY_ALREADY_BORROWED",
                        "BORROWER_LIMIT_REACHED", "RETURNED", "BORROWED", "ALREADY_RETURNED"),
                result.steps().stream().map(LibraryStep::outcome).toList());
        assertEquals(result.steps().get(0).loans(), result.steps().get(1).loans(),
                "A query must not mutate library state");
        assertEquals(DAY.plusDays(14), result.steps().get(0).loans().get(0).dueOn());
        assertEquals(300, result.finalLoans().get(0).lateFeeCents());
        assertEquals(DAY.plusDays(18), result.finalLoans().get(1).borrowedOn());
        assertEquals(2, result.finalLoans().size());
        assertFalse(result.steps().get(2).accepted());
    }

    @Test
    void invalidDatesDoNotChangeLoanAndReturnedLoanIsProtected() {
        Loan loan = new Loan("MEM", new BookCopy("A", "Java"), DAY, DAY.plusDays(14));
        assertThrows(IllegalArgumentException.class, () -> loan.returnOn(DAY.minusDays(1), 100));
        assertTrue(loan.isActive());
        loan.returnOn(DAY.plusDays(15), 100);
        assertEquals(100, loan.lateFeeCents());
        assertThrows(IllegalStateException.class, () -> loan.returnOn(DAY.plusDays(16), 100));
        assertEquals(DAY.plusDays(15), loan.returnedOn());
        assertThrows(IllegalArgumentException.class, () -> new LoanPolicy(0, 14));
    }

    @Test
    void duplicateCopiesUnknownIdsAndUnsupportedActionsAreBadInput() {
        var service = new LibraryScenarioService();
        assertThrows(IllegalArgumentException.class, () -> service.run(new LibraryRequest(2, 14,
                List.of(new BookInput("A", "Java"), new BookInput("A", "Duplicate")),
                List.of(new LibraryAction("QUERY", null, null, null)))));
        assertThrows(IllegalArgumentException.class, () -> service.run(new LibraryRequest(2, 14,
                List.of(new BookInput("A", "Java")),
                List.of(new LibraryAction("BORROW", "M", "UNKNOWN", DAY)))));
        assertThrows(IllegalArgumentException.class, () -> service.run(new LibraryRequest(2, 14,
                List.of(new BookInput("A", "Java")),
                List.of(new LibraryAction("DELETE_ALL", null, null, DAY)))));
    }
}

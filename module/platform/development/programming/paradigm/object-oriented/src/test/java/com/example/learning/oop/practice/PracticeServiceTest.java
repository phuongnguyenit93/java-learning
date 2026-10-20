package com.example.learning.oop.practice;

import com.example.learning.oop.practice.service.PracticeExperimentService;
import static com.example.learning.oop.practice.dto.PracticeDtos.*;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PracticeServiceTest {
    private final PracticeExperimentService service = new PracticeExperimentService();

    @Test
    void bankSnapshotsShowSuccessAndAtomicRejection() {
        BankResult result = service.bank(new BankRequest(
                List.of(new BankSeed("TK-1", "An", 100L),
                        new BankSeed("TK-2", "Binh", 20L)),
                List.of(new BankAction("TRANSFER","TK-1","TK-2",60L),
                        new BankAction("WITHDRAW","TK-1",null,100L),
                        new BankAction("DEPOSIT","TK-2",null,30L))));
        assertEquals(100L, result.initial().get(0).balanceVnd());
        assertEquals(40L, result.current().get(0).balanceVnd());
        assertEquals(110L, result.current().get(1).balanceVnd());
        assertFalse(result.steps().get(1).accepted());
        assertEquals(result.steps().get(1).before(),result.steps().get(1).after());
        assertTrue(result.scope().contains("NO_REAL_BANK"));
    }

    @Test
    void bankOverflowAndBadActionsDoNotCorruptState() {
        BankResult result=service.bank(new BankRequest(
                List.of(new BankSeed("TK-1","A",100L),
                        new BankSeed("TK-2","B",Long.MAX_VALUE)),
                List.of(new BankAction("TRANSFER","TK-1","TK-2",1L))));
        assertFalse(result.steps().get(0).accepted());
        assertEquals(result.initial(),result.current());
        assertThrows(IllegalArgumentException.class,
                () -> service.bank(new BankRequest(List.of(new BankSeed("TK-1","A",0L)),
                        List.of(new BankAction("X","TK-1",null,5L)))));
    }

    @Test
    void orderServiceEmitsFrozenSnapshotsAcrossLifecycle() {
        OrderResult result=service.order(new OrderRequest("ORD-1",
                List.of(new OrderAction("SET_LINE"," book ",100_000L,1),
                        new OrderAction("SET_LINE","PEN",10_000L,2),
                        new OrderAction("PLACE",null,null,null),
                        new OrderAction("CHANGE_QUANTITY","BOOK",null,3),
                        new OrderAction("CANCEL",null,null,null))));
        assertEquals("DRAFT",result.initial().status());
        assertEquals("CANCELLED",result.current().status());
        assertEquals(120_000L,result.current().summary().subtotalCents());
        assertEquals(0L,result.current().summary().shippingCents());
        assertFalse(result.steps().get(3).accepted());
        assertEquals(result.steps().get(3).before(),result.steps().get(3).after());
        assertEquals(0,result.steps().get(0).before().lines().size());
        assertEquals(1,result.steps().get(0).after().lines().size());
        assertThrows(UnsupportedOperationException.class,()->result.current().lines().clear());
    }

    @Test
    void orderValidationAndMaximumSteps() {
        assertThrows(IllegalArgumentException.class,
                ()->service.order(new OrderRequest("ORD-1",List.of())));
        assertThrows(IllegalArgumentException.class,
                ()->service.order(new OrderRequest("ORD-1",
                        List.of(new OrderAction("SET_LINE","A",null,1)))));
        var twentyOne=new ArrayList<OrderAction>();
        for(int i=0;i<21;i++)twentyOne.add(new OrderAction("CANCEL",null,null,null));
        assertThrows(IllegalArgumentException.class,
                ()->service.order(new OrderRequest("ORD-1",twentyOne)));
    }

    private LibraryRequest libraryRequest(List<LibraryAction> actions) {
        return new LibraryRequest(
                List.of(new LibraryBookInput("ISBN-1","Java","NORMAL"),
                        new LibraryBookInput("ISBN-2","Reference","REFERENCE")),
                List.of(new LibraryMemberInput("MEM-1",true),new LibraryMemberInput("MEM-2",true)),
                List.of(new LibraryCopyInput("CP-1","ISBN-2"),new LibraryCopyInput("CP-2","ISBN-2"),
                        new LibraryCopyInput("CP-3","ISBN-1")),
                actions);
    }

    @Test
    void libraryServiceDoesNotRewriteDueOnWhenPolicyChanges() {
        LibraryResult result=service.library(libraryRequest(List.of(
                new LibraryAction("BORROW","MEM-1","CP-1",null,"2026-03-01"),
                new LibraryAction("REVISE_POLICY",null,null,null,"2026-03-02"),
                new LibraryAction("BORROW","MEM-2","CP-2",null,"2026-03-02"),
                new LibraryAction("BORROW","MEM-1","CP-1",null,"2026-03-02"),
                new LibraryAction("RETURN",null,null,"LOAN-1","2026-03-03"))));
        assertEquals("LEGACY",result.initial().policy());
        assertEquals("REVISED",result.current().policy());
        assertEquals("2026-03-15",result.current().loans().get(0).dueOn().toString());
        assertEquals("2026-03-09",result.current().loans().get(1).dueOn().toString());
        assertFalse(result.steps().get(3).accepted());
        assertEquals("BR-03",result.steps().get(3).rule());
        assertEquals(result.steps().get(3).before(),result.steps().get(3).after());
        assertTrue(result.invariantsPreserved());
        assertEquals(1,result.current().openLoanCount());
    }

    @Test
    void libraryServiceRejectsOverdueWithoutPartialMutation() {
        LibraryResult result=service.library(libraryRequest(List.of(
                new LibraryAction("BORROW","MEM-1","CP-3",null,"2026-03-01"),
                new LibraryAction("BORROW","MEM-1","CP-2",null,"2026-03-16"))));
        assertFalse(result.steps().get(1).accepted());
        assertEquals("BR-04",result.steps().get(1).rule());
        assertEquals(result.steps().get(1).before(),result.steps().get(1).after());
        assertEquals(1,result.current().loans().size());
    }

    @Test
    void libraryServiceValidatesChronologicalDatesAndKindBeforeRunning() {
        assertThrows(IllegalArgumentException.class,()->service.library(libraryRequest(List.of(
                new LibraryAction("BORROW","MEM-1","CP-1",null,"2026-03-02"),
                new LibraryAction("RETURN",null,null,"LOAN-1","2026-03-01")))));
        assertThrows(IllegalArgumentException.class,()->service.library(new LibraryRequest(
                List.of(new LibraryBookInput("ISBN-1","Java","UNKNOWN")),
                List.of(new LibraryMemberInput("MEM-1",true)),
                List.of(new LibraryCopyInput("CP-1","ISBN-1")),
                List.of(new LibraryAction("BORROW","MEM-1","CP-1",null,"2026-03-01")))));
    }
}

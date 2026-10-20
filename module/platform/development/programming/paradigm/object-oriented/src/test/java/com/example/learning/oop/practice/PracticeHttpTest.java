package com.example.learning.oop.practice;

import com.example.learning.oop.practice.controller.PracticeExperimentController;
import com.example.learning.oop.practice.exception.PracticeExceptionHandler;
import com.example.learning.oop.practice.service.PracticeExperimentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PracticeHttpTest {
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new PracticeExperimentController(
                new PracticeExperimentService()))
                .setControllerAdvice(new PracticeExceptionHandler()).build();
    }

    @Test
    void bankJsonTracksTransferAndRejections() throws Exception {
        mvc().perform(post("/oop/practice/bank/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"accounts":[{"id":"TK-1","owner":"An","openingBalanceVnd":100},
                                 {"id":"TK-2","owner":"Binh","openingBalanceVnd":10}],
                     "actions":[{"type":"TRANSFER","accountId":"TK-1","toAccountId":"TK-2","amountVnd":60},
                                {"type":"WITHDRAW","accountId":"TK-1","amountVnd":99}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].accepted").value(true))
                .andExpect(jsonPath("$.steps[1].accepted").value(false))
                .andExpect(jsonPath("$.current[0].balanceVnd").value(40))
                .andExpect(jsonPath("$.current[1].balanceVnd").value(70));
    }

    @Test
    void orderJsonIsActualAggregateWithLockedPlacedState() throws Exception {
        mvc().perform(post("/oop/practice/orders/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"orderId":"ORD-1","actions":[
                     {"type":"SET_LINE","sku":" book ","unitPriceCents":90000,"quantity":2},
                     {"type":"PLACE"},
                     {"type":"CHANGE_QUANTITY","sku":"BOOK","quantity":3}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.status").value("PLACED"))
                .andExpect(jsonPath("$.current.summary.subtotalCents").value(180000))
                .andExpect(jsonPath("$.steps[2].accepted").value(false))
                .andExpect(jsonPath("$.steps[2].before.status").value("PLACED"))
                .andExpect(jsonPath("$.current.lines[0].sku").value("BOOK"));
    }

    @Test
    void libraryJsonShowsHistoricalDueDateAfterPolicyRevision() throws Exception {
        mvc().perform(post("/oop/practice/library/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"books":[{"isbn":"ISBN-1","title":"Java","kind":"REFERENCE"}],
                     "members":[{"id":"MEM-1","active":true},{"id":"MEM-2","active":true}],
                     "copies":[{"id":"CP-1","isbn":"ISBN-1"},{"id":"CP-2","isbn":"ISBN-1"}],
                     "actions":[
                       {"type":"BORROW","memberId":"MEM-1","copyId":"CP-1","onDate":"2026-03-01"},
                       {"type":"REVISE_POLICY","onDate":"2026-03-02"},
                       {"type":"BORROW","memberId":"MEM-2","copyId":"CP-2","onDate":"2026-03-02"}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initial.policy").value("LEGACY"))
                .andExpect(jsonPath("$.current.policy").value("REVISED"))
                .andExpect(jsonPath("$.current.loans[0].dueOn").value("2026-03-15"))
                .andExpect(jsonPath("$.current.loans[1].dueOn").value("2026-03-09"))
                .andExpect(jsonPath("$.invariantsPreserved").value(true));
    }

    @Test
    void malformedRequestsYieldScopedHttp400() throws Exception {
        mvc().perform(post("/oop/practice/bank/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"accounts":[],"actions":[{"type":"DEPOSIT","accountId":"X","amountVnd":1}]}
                    """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
        mvc().perform(post("/oop/practice/library/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"books":[],"members":[],"copies":[],"actions":[{"type":"REVISE_POLICY","onDate":"2026-03-01"}]}
                    """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
        assertEquals("/oop/practice",
                PracticeExperimentController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertEquals(PracticeExperimentController.class,
                PracticeExceptionHandler.class.getAnnotation(RestControllerAdvice.class)
                        .assignableTypes()[0]);
    }
}

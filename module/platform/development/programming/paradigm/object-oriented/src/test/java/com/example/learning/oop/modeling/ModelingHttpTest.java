package com.example.learning.oop.modeling;

import com.example.learning.oop.modeling.controller.ModelingExperimentController;
import com.example.learning.oop.modeling.exception.ModelingExceptionHandler;
import com.example.learning.oop.modeling.service.ModelingExperimentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ModelingHttpTest {
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(
                new ModelingExperimentController(new ModelingExperimentService()))
                .setControllerAdvice(new ModelingExceptionHandler()).build();
    }

    @Test
    void libraryHttpReturnsDerivedLoanTraceAndGraphSnapshots() throws Exception {
        mvc().perform(post("/oop/modeling/library/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"titles":[{"code":"TITLE-1","name":"Java"}],
                     "members":[{"id":"MEM-1","active":true},{"id":"MEM-2","active":true}],
                     "copies":[{"id":"COPY-1","titleCode":"TITLE-1"}],
                     "actions":[
                       {"type":"BORROW","memberId":"MEM-1","copyId":"COPY-1","onDate":"2026-10-20"},
                       {"type":"BORROW","memberId":"MEM-2","copyId":"COPY-1","onDate":"2026-10-20"},
                       {"type":"RETURN","loanId":"LOAN-1","onDate":"2026-10-25"}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initial.loans.length()").value(0))
                .andExpect(jsonPath("$.steps[0].calls[1]").value("Member.ensureEligible"))
                .andExpect(jsonPath("$.steps[1].accepted").value(false))
                .andExpect(jsonPath("$.steps[1].rule").value("BR-03"))
                .andExpect(jsonPath("$.current.loans[0].dueOn").value("2026-11-03"))
                .andExpect(jsonPath("$.current.loans[0].status").value("RETURNED"))
                .andExpect(jsonPath("$.current.copies[0].borrowed").value(false))
                .andExpect(jsonPath("$.invariantsSatisfied").value(true));
    }

    @Test
    void orderHttpReturnsStateTransitionEvidenceAndErrors() throws Exception {
        mvc().perform(post("/oop/modeling/orders/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"orderId":"ORD-1","totalVnd":100,
                     "actions":[{"event":"SUBMIT"},{"event":"CONFIRM_PAYMENT","receivedVnd":75},
                                {"event":"CONFIRM_PAYMENT","receivedVnd":100},
                                {"event":"START_PACKING"},{"event":"SHIP","trackingCode":"TRACK-1"},
                                {"event":"CONFIRM_DELIVERY"}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[1].rule").value("PAYMENT_GUARD"))
                .andExpect(jsonPath("$.steps[1].before.state").value("WAITING_PAYMENT"))
                .andExpect(jsonPath("$.steps[1].after.state").value("WAITING_PAYMENT"))
                .andExpect(jsonPath("$.current.state").value("DELIVERED"))
                .andExpect(jsonPath("$.current.trackingCode").value("TRACK-1"));
    }

    @Test
    void malformedLibraryAndUnsupportedOrderEventsReturnScoped400() throws Exception {
        mvc().perform(post("/oop/modeling/library/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"titles":[],"members":[],"copies":[],"actions":[]}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
        mvc().perform(post("/oop/modeling/orders/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"orderId":"ORD-1","totalVnd":100,"actions":[{"event":"UNKNOWN"}]}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
        assertEquals(ModelingExperimentController.class,
                ModelingExceptionHandler.class.getAnnotation(RestControllerAdvice.class)
                        .assignableTypes()[0]);
        assertEquals("/oop/modeling",
                ModelingExperimentController.class.getAnnotation(RequestMapping.class).value()[0]);
    }
}

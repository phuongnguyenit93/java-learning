package com.example.learning.oop.interface_chapter;

import com.example.learning.oop.interface_chapter.controller.InterfaceExperimentsController;
import com.example.learning.oop.interface_chapter.exception.InterfaceExperimentsExceptionHandler;
import com.example.learning.oop.interface_chapter.service.InterfaceExperimentsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InterfaceHttpTest {
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new InterfaceExperimentsController(
                new InterfaceExperimentsService()))
                .setControllerAdvice(new InterfaceExperimentsExceptionHandler()).build();
    }

    @Test
    void shippingHttpJsonHasBothRealPolicyFees() throws Exception {
        mvc().perform(post("/oop/interfaces/shipping/quote").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"distanceKm":4,"orderValueVnd":100000,"selectedPolicy":"ECONOMY"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selected.feeVnd").value(20000))
                .andExpect(jsonPath("$.allPolicies[1].feeVnd").value(40000));
    }

    @Test
    void documentsHttpJsonTracesRealSharedInstance() throws Exception {
        mvc().perform(post("/oop/interfaces/documents/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"initialDocuments":[],"actions":[
                     {"type":"save","id":"DOC-1","title":"Hello","content":"Message"},
                     {"type":"publish","id":"DOC-1"},
                     {"type":"read","id":"DOC-1"}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sharedInstance").value(true))
                .andExpect(jsonPath("$.current[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.steps[2].readResult.content").value("Message"));
    }

    @Test
    void checkoutHttpShowsReplayAndActualGatewayCallCount() throws Exception {
        mvc().perform(post("/oop/interfaces/checkout/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"gateway":"APPROVE","repository":"TREE_MAP","actions":[
                     {"requestId":"REQ-1","orderId":"ORD-1","amountVnd":100},
                     {"requestId":"REQ-1","orderId":"ORD-1","amountVnd":100}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.gatewayCalls").value(1))
                .andExpect(jsonPath("$.current.storedPayments[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.steps[1].replayed").value(true));
    }

    @Test
    void invalidHttpInputHasChapterScoped400Error() throws Exception {
        mvc().perform(post("/oop/interfaces/shipping/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"distanceKm":-1,"orderValueVnd":100,"selectedPolicy":"EXPRESS"}
                    """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
        var advice = InterfaceExperimentsExceptionHandler.class.getAnnotation(RestControllerAdvice.class);
        assertNotNull(advice);
        assertEquals(InterfaceExperimentsController.class, advice.assignableTypes()[0]);
        assertEquals("/oop/interfaces",
                InterfaceExperimentsController.class.getAnnotation(RequestMapping.class).value()[0]);
    }
}

package com.example.learning.oop.testing;

import com.example.learning.oop.testing.controller.TestingExperimentController;
import com.example.learning.oop.testing.exception.TestingExperimentExceptionHandler;
import com.example.learning.oop.testing.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TestingHttpTest {
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new TestingExperimentController(
                new InventoryContractService(), new CollaborationExperimentService(),
                new RegressionExperimentService()))
                .setControllerAdvice(new TestingExperimentExceptionHandler()).build();
    }

    @Test
    void contractEndpointReportsBothCorrectImplementationsAndViolatingSubtype() throws Exception {
        mvc().perform(post("/oop/testing/inventory/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                           {"capacity":10,"actions":[
                            {"type":"RESERVE","seats":4},
                            {"type":"RESERVE","seats":7},
                            {"type":"CANCEL","seats":2}]}
                           """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conformingImplementationsPassed").value(true))
                .andExpect(jsonPath("$.defectDetected").value(true))
                .andExpect(jsonPath("$.runs[0].steps[1].passed").value(true))
                .andExpect(jsonPath("$.runs[1].steps[1].passed").value(true))
                .andExpect(jsonPath("$.runs[2].steps[1].passed").value(false))
                .andExpect(jsonPath("$.runs[2].steps[1].actualSnapshot.reserved").value(11));
        mvc().perform(post("/oop/testing/inventory/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\":0,\"actions\":[{\"type\":\"RESERVE\",\"seats\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void collaborationEndpointReportsActualInteractionsAndCompensation() throws Exception {
        mvc().perform(post("/oop/testing/checkout/collaboration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                           {"scenarios":[
                              {"orderId":"ORD-1","sku":"SKU-1","quantity":2,
                               "unitPriceVnd":50000,"stock":5,
                               "paymentMode":"DECLINE","receiptMode":"SEND"},
                              {"orderId":"ORD-2","sku":"SKU-1","quantity":2,
                               "unitPriceVnd":50000,"stock":5,
                               "paymentMode":"APPROVE","receiptMode":"ERROR"}]}
                           """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs[0].status").value("PAYMENT_DECLINED_ROLLED_BACK"))
                .andExpect(jsonPath("$.runs[0].remainingStock").value(5))
                .andExpect(jsonPath("$.runs[0].events[2]").value("inventory.release"))
                .andExpect(jsonPath("$.runs[1].status").value("PAID_RECEIPT_FAILED"))
                .andExpect(jsonPath("$.runs[1].remainingStock").value(3))
                .andExpect(jsonPath("$.runs[1].sentReceipts").value(0))
                .andExpect(jsonPath("$.runs[1].invariantHolds").value(true));
        mvc().perform(post("/oop/testing/checkout/collaboration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarios\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void regressionEndpointChecksInvoicesNotInternalLookupCounts() throws Exception {
        mvc().perform(post("/oop/testing/pricing/regression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                           {"pricesVnd":{"BOOK":25000,"PEN":50000},
                            "lines":[{"sku":"BOOK","quantity":1},
                                     {"sku":"BOOK","quantity":1},
                                     {"sku":"PEN","quantity":1}],
                            "member":true}
                           """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refactorPreservedBehavior").value(true))
                .andExpect(jsonPath("$.seededDefectDetected").value(true))
                .andExpect(jsonPath("$.baseline.totalVnd").value(95000))
                .andExpect(jsonPath("$.variants[0].catalogLookups").value(3))
                .andExpect(jsonPath("$.variants[1].catalogLookups").value(2))
                .andExpect(jsonPath("$.variants[2].matchesBaseline").value(false));
        mvc().perform(post("/oop/testing/pricing/regression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                           {"pricesVnd":{"BOOK":9223372036854775807},
                            "lines":[{"sku":"BOOK","quantity":2}],"member":false}
                           """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ARITHMETIC_OVERFLOW"));
    }

    @Test
    void errorHandlerIsScopedOnlyToTestingController() {
        assertEquals("/oop/testing",
                TestingExperimentController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertEquals(TestingExperimentController.class,
                TestingExperimentExceptionHandler.class.getAnnotation(RestControllerAdvice.class)
                        .assignableTypes()[0]);
    }
}

package com.example.learning.oop.refactoring;

import com.example.learning.oop.refactoring.controller.RefactoringExperimentsController;
import com.example.learning.oop.refactoring.exception.RefactoringExceptionHandler;
import com.example.learning.oop.refactoring.service.AlertRefactoringService;
import com.example.learning.oop.refactoring.service.CheckoutRefactoringService;
import com.example.learning.oop.refactoring.service.DeliveryRefactoringService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RefactoringHttpTest {
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(
                        new RefactoringExperimentsController(new CheckoutRefactoringService(),
                                new DeliveryRefactoringService(), new AlertRefactoringService()))
                .setControllerAdvice(new RefactoringExceptionHandler())
                .build();
    }

    @Test
    void checkoutHttpPreservesBeforeAfterPricingAndReceipt() throws Exception {
        mvc().perform(post("/oop/refactoring/checkout/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"vip":true,"lines":[
                              {"sku":"JAVA","unitPriceVnd":200000,"quantity":2},
                              {"sku":"OOP","unitPriceVnd":100000,"quantity":1}]}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.equivalent").value(true))
                .andExpect(jsonPath("$.before.subtotalVnd").value(500000))
                .andExpect(jsonPath("$.after.discountVnd").value(50000))
                .andExpect(jsonPath("$.after.payableVnd").value(450000))
                .andExpect(jsonPath("$.after.shippingVnd").value(30000))
                .andExpect(jsonPath("$.after.totalVnd").value(480000))
                .andExpect(jsonPath("$.after.receipt").value(
                        "subtotal=500000;discount=50000;shipping=30000;total=480000"));
    }

    @Test
    void deliveryHttpComparesActualPolymorphicCalculations() throws Exception {
        mvc().perform(post("/oop/refactoring/delivery/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"deliveries":[
                              {"type":"STANDARD","orderValueVnd":499999,"weightKg":2},
                              {"type":"STANDARD","orderValueVnd":500000,"weightKg":2},
                              {"type":"EXPRESS","orderValueVnd":40000,"weightKg":2},
                              {"type":"PICKUP","orderValueVnd":1,"weightKg":1}]}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allEquivalent").value(true))
                .andExpect(jsonPath("$.deliveries[0].before.feeVnd").value(30000))
                .andExpect(jsonPath("$.deliveries[1].after.feeVnd").value(0))
                .andExpect(jsonPath("$.deliveries[2].after.feeVnd").value(65000))
                .andExpect(jsonPath("$.deliveries[2].after.etaDays").value(1))
                .andExpect(jsonPath("$.deliveries[3].after.etaDays").value(0));
    }

    @Test
    void alertsHttpShowsAttemptsAndNoFalseSuccessAfterRetryExhaustion() throws Exception {
        MockMvc api = mvc();
        api.perform(post("/oop/refactoring/alerts/experiment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"channel":"EMAIL","recipient":"oncall","incident":"Network outage",
                             "transientFailures":2,"maxAttempts":3}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivered").value(true))
                .andExpect(jsonPath("$.attempts").value(3))
                .andExpect(jsonPath("$.deliveredMessages[0]").value(
                        "EMAIL->oncall:[CRITICAL] Network outage"))
                .andExpect(jsonPath("$.outcome").value("DELIVERED"));
        api.perform(post("/oop/refactoring/alerts/experiment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"channel":"EMAIL","recipient":"oncall","incident":"Network outage",
                             "transientFailures":4,"maxAttempts":2}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivered").value(false))
                .andExpect(jsonPath("$.attempts").value(2))
                .andExpect(jsonPath("$.deliveredMessages.length()").value(0))
                .andExpect(jsonPath("$.outcome").value("RETRY_EXHAUSTED"));
    }

    @Test
    void chapterScopedAdviceMapsInvalidInputTo400() throws Exception {
        mvc().perform(post("/oop/refactoring/checkout/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"vip":false,"lines":[{"sku":"X","unitPriceVnd":-2,"quantity":1}]}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INPUT"));
        mvc().perform(post("/oop/refactoring/delivery/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"deliveries":[{"type":"UNKNOWN","orderValueVnd":1,"weightKg":1}]}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INPUT"));
        mvc().perform(post("/oop/refactoring/alerts/experiment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"channel":"EMAIL","recipient":"oncall","incident":"Outage",
                             "transientFailures":1,"maxAttempts":0}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INPUT"));
        assertEquals(RefactoringExperimentsController.class,
                RefactoringExceptionHandler.class.getAnnotation(RestControllerAdvice.class)
                        .assignableTypes()[0]);
    }
}

package com.example.learning.oop.polymorphism;

import com.example.learning.oop.polymorphism.controller.PolymorphismExperimentsController;
import com.example.learning.oop.polymorphism.exception.PolymorphismExceptionHandler;
import com.example.learning.oop.polymorphism.service.PolymorphismExperimentsService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PolymorphismHttpTest {
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(
                new PolymorphismExperimentsController(new PolymorphismExperimentsService()))
                .setControllerAdvice(new PolymorphismExceptionHandler()).build();
    }

    @Test
    void httpDispatchProvesOverloadAndHidingUsingActualJvmCalls() throws Exception {
        mvc().perform(post("/oop/polymorphism/dispatch/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"receiverType":"PREMIUM","eventType":"PRIORITY",
                     "weight":4,"priority":3,"bonus":7}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viaDeclaredEvent").value(47))
                .andExpect(jsonPath("$.viaSpecificEvent").value(410))
                .andExpect(jsonPath("$.overriddenInstanceMethod").value("PREMIUM-INSTANCE"))
                .andExpect(jsonPath("$.fieldViaBaseReference").value("BASE-FIELD"))
                .andExpect(jsonPath("$.fieldViaConcreteReference").value("PREMIUM-FIELD"));
    }

    @Test
    void httpPaymentsReturnsRealWalletTransitionsAndThreeStatuses() throws Exception {
        mvc().perform(post("/oop/polymorphism/payments/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"initialWalletBalanceVnd":100,"cardLimitVnd":50,"actions":[
                     {"method":"CARD","orderId":"ORD-1","amountVnd":60},
                     {"method":"WALLET","orderId":"ORD-2","amountVnd":40},
                     {"method":"BANK","orderId":"ORD-3","amountVnd":10}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidCount").value(1))
                .andExpect(jsonPath("$.declinedCount").value(1))
                .andExpect(jsonPath("$.pendingCount").value(1))
                .andExpect(jsonPath("$.finalWalletBalanceVnd").value(60))
                .andExpect(jsonPath("$.steps[0].status").value("DECLINED"))
                .andExpect(jsonPath("$.steps[1].status").value("PAID"))
                .andExpect(jsonPath("$.steps[2].status").value("PENDING"));
    }

    @Test
    void invalidHttpArgumentsMapToChapterScoped400() throws Exception {
        mvc().perform(post("/oop/polymorphism/dispatch/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"receiverType":"PREMIUM","eventType":"PRIORITY","weight":4,"bonus":7}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
        mvc().perform(post("/oop/polymorphism/payments/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"initialWalletBalanceVnd":100,"cardLimitVnd":100,"actions":[
                    {"method":"UNKNOWN","orderId":"ORD-1","amountVnd":10}]}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
        assertEquals(PolymorphismExperimentsController.class,
                PolymorphismExceptionHandler.class.getAnnotation(RestControllerAdvice.class).assignableTypes()[0]);
        assertEquals("/oop/polymorphism",
                PolymorphismExperimentsController.class.getAnnotation(RequestMapping.class).value()[0]);
    }
}

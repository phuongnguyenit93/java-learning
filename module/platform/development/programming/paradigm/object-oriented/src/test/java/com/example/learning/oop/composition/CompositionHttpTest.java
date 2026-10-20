package com.example.learning.oop.composition;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.learning.oop.composition.controller.CompositionExperimentController;
import com.example.learning.oop.composition.exception.CompositionExperimentExceptionHandler;
import com.example.learning.oop.composition.service.CompositionExperimentService;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class CompositionHttpTest {
    private MockMvc http;

    @BeforeEach
    void setup() {
        http = MockMvcBuilders.standaloneSetup(
                new CompositionExperimentController(new CompositionExperimentService()))
                .setControllerAdvice(new CompositionExperimentExceptionHandler()).build();
    }

    @Test
    void routesBelongOnlyToCompositionChapter() {
        RequestMapping mapping = CompositionExperimentController.class.getAnnotation(RequestMapping.class);
        assertEquals("/oop/composition", mapping.value()[0]);
        Set<String> paths = Arrays.stream(CompositionExperimentController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("/notifications", "/policies", "/orders", "/resources"), paths);
    }

    @Test
    void notificationPipelineCanFailWithoutClaimingAuditSuccess() throws Exception {
        http.perform(post("/oop/composition/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderId":"ORD1","recipient":"user@example.test","totalCents":100,
                         "format":"COMPACT","channel":"EMAIL","audited":true,"failTransport":true}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accepted").value(false))
            .andExpect(jsonPath("$.content").value("ORD1:PAID:100"))
            .andExpect(jsonPath("$.trace.length()").value(2));
    }

    @Test
    void policySwitchAndPerCallInjectionProduceExpectedJson() throws Exception {
        http.perform(post("/oop/composition/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"initialPolicy":"NONE","steps":[
                         {"action":"SWITCH","policy":"PERCENT","percent":10},
                         {"action":"QUOTE","subtotalCents":100},
                         {"action":"QUOTE_WITH_POLICY","policy":"FIXED","discountCents":30,"subtotalCents":100},
                         {"action":"QUOTE","subtotalCents":100}]}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.finalPolicy").value("PERCENT"))
            .andExpect(jsonPath("$.timeline[1].payableCents").value(90))
            .andExpect(jsonPath("$.timeline[2].payableCents").value(70))
            .andExpect(jsonPath("$.timeline[3].payableCents").value(90));
    }

    @Test
    void orderRepositoryFailureHasNoFalsePersistedResult() throws Exception {
        http.perform(post("/oop/composition/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderId":"ORD2","lines":[{"sku":"A","quantity":2,"unitPriceCents":5}],
                         "failStorage":true}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accepted").value(false))
            .andExpect(jsonPath("$.saveAttempts").value(1))
            .andExpect(jsonPath("$.storedOrders.length()").value(0));
    }

    @Test
    void resourceOwnershipIsVisibleOnHttpResponse() throws Exception {
        http.perform(post("/oop/composition/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"ownership":"BORROWED","content":"OK","failWrite":false}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resourceClosedAfterExporter").value(false))
            .andExpect(jsonPath("$.resourceClosedAfterCaller").value(true));
    }

    @Test
    void invalidInputsAndMalformedJsonAreScopedBadRequest() throws Exception {
        http.perform(post("/oop/composition/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"orderId":"X","lines":[{"sku":"A","quantity":0,"unitPriceCents":5}]}
                        """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        http.perform(post("/oop/composition/policies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{broken"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }
}

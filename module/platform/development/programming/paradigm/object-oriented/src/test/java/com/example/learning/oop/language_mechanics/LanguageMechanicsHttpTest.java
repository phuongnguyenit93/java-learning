package com.example.learning.oop.language_mechanics;

import com.example.learning.oop.language_mechanics.controller.LanguageMechanicsExperimentsController;
import com.example.learning.oop.language_mechanics.exception.LanguageMechanicsExceptionHandler;
import com.example.learning.oop.language_mechanics.service.GenericBatchService;
import com.example.learning.oop.language_mechanics.service.ShippingScenarioService;
import com.example.learning.oop.language_mechanics.service.StockExperimentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LanguageMechanicsHttpTest {
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new LanguageMechanicsExperimentsController(
                new GenericBatchService(), new StockExperimentService(),
                new ShippingScenarioService()))
                .setControllerAdvice(new LanguageMechanicsExceptionHandler()).build();
    }

    @Test
    void typedValuesReachHttpAsActualCalculatedData() throws Exception {
        mvc().perform(post("/oop/language-mechanics/generics/experiment")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"integers":[2,7],"decimals":[0.5,1.25],
                     "entries":[{"key":"A","quantity":4}],
                     "lookups":["A","NOT_FOUND"]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.copiedValues[2]").value("0.5"))
                .andExpect(jsonPath("$.sum").value("10.75"))
                .andExpect(jsonPath("$.largestInteger").value(7))
                .andExpect(jsonPath("$.found.A").value(4))
                .andExpect(jsonPath("$.missing[0]").value("NOT_FOUND"));
    }

    @Test
    void stockHttpDifferentiatesDomainRefusalFromBadRequestAndRedactsSecrets()
            throws Exception {
        mvc().perform(post("/oop/language-mechanics/stock/experiment")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"actions":[
                      {"type":"REGISTER","tenantId":"T","sku":"BOOK","quantity":2,
                       "contactEmail":"sensitive@example.org","providerToken":"secret-123"},
                      {"type":"RESERVE","tenantId":"T","sku":"BOOK","quantity":3},
                      {"type":"READ","tenantId":"T","sku":"BOOK"},
                      {"type":"RESERVE","tenantId":"T","sku":"BOOK","quantity":1}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[1].accepted").value(false))
                .andExpect(jsonPath("$.steps[1].code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.steps[2].available").value(2))
                .andExpect(jsonPath("$.steps[3].available").value(1))
                .andExpect(jsonPath("$.steps[0].diagnostic").value(
                        "StockItem{tenantId=T, sku=BOOK, available=2}"));

        mvc().perform(post("/oop/language-mechanics/stock/experiment")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"actions":[{"type":"REGISTER","tenantId":"T","sku":"B","quantity":-1}]}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INPUT"));
    }

    @Test
    void policyDispatchProvidesWeightedFeesAndInvalidWeightIsHttp400() throws Exception {
        mvc().perform(post("/oop/language-mechanics/shipping/experiment")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"quotes":[{"serviceCode":"standard","weightGrams":1},
                               {"serviceCode":"express","weightGrams":1001},
                               {"serviceCode":"unknown","weightGrams":1000}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quotes[0].feeVnd").value(20000))
                .andExpect(jsonPath("$.quotes[1].feeVnd").value(51000))
                .andExpect(jsonPath("$.quotes[2].accepted").value(false))
                .andExpect(jsonPath("$.quotes[2].code").value("SERVICE_NOT_SUPPORTED"))
                .andExpect(jsonPath("$.totalAcceptedFeesVnd").value(71000));

        mvc().perform(post("/oop/language-mechanics/shipping/experiment")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"quotes":[{"serviceCode":"express","weightGrams":0}]}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INPUT"));
        assertEquals(LanguageMechanicsExperimentsController.class,
                LanguageMechanicsExceptionHandler.class.getAnnotation(RestControllerAdvice.class)
                        .assignableTypes()[0]);
    }
}

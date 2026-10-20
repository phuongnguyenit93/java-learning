package com.example.learning.oop.design_pitfalls;

import com.example.learning.oop.design_pitfalls.controller.DesignPitfallExperimentController;
import com.example.learning.oop.design_pitfalls.exception.DesignPitfallExceptionHandler;
import com.example.learning.oop.design_pitfalls.service.DesignPitfallExperimentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DesignPitfallHttpTest {
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(
                new DesignPitfallExperimentController(new DesignPitfallExperimentService()))
                .setControllerAdvice(new DesignPitfallExceptionHandler()).build();
    }

    @Test
    void seatJsonShowsFailedAttemptWithoutBreakingState() throws Exception {
        mvc().perform(post("/oop/design-pitfalls/seats/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"capacity":3,"actions":[
                      {"type":"RESERVE","seats":2},
                      {"type":"RESERVE","seats":2},
                      {"type":"CANCEL","seats":1}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initial.reserved").value(0))
                .andExpect(jsonPath("$.steps[1].accepted").value(false))
                .andExpect(jsonPath("$.steps[1].before.reserved").value(2))
                .andExpect(jsonPath("$.steps[1].after.reserved").value(2))
                .andExpect(jsonPath("$.current.available").value(2))
                .andExpect(jsonPath("$.invariantPreserved").value(true));
    }

    @Test
    void mutableHashKeyJsonShowsLostLookupAndSafeIdentity() throws Exception {
        mvc().perform(post("/oop/design-pitfalls/mutable-key/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"originalName":"ALICE","changedName":"BOB"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unsafeFoundBeforeRename").value(true))
                .andExpect(jsonPath("$.unsafeFoundAfterRename").value(false))
                .andExpect(jsonPath("$.stableFoundAfterRename").value(true))
                .andExpect(jsonPath("$.stableProfileNameAfterRename").value("BOB"));
    }

    @Test
    void refactoredCheckoutJsonIsEqualToLegacyAndProvidesTrace() throws Exception {
        mvc().perform(post("/oop/design-pitfalls/checkout/refactor")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sku":"SKU-1","unitPriceVnd":10000,"quantity":3,
                     "availableUnits":5,"member":true,"deliveryMode":"STANDARD"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sameResult").value(true))
                .andExpect(jsonPath("$.legacy.payableVnd").value(48500))
                .andExpect(jsonPath("$.refactored.payableVnd").value(48500))
                .andExpect(jsonPath("$.collaborationTrace[0]").value("StockGuard.requireAvailable"));
    }

    @Test
    void errorsAreScopedToChapterWith400And422() throws Exception {
        mvc().perform(post("/oop/design-pitfalls/seats/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"capacity":0,"actions":[{"type":"RESERVE","seats":1}]}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
        mvc().perform(post("/oop/design-pitfalls/checkout/refactor")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sku":"SKU-1","unitPriceVnd":100,"quantity":2,
                     "availableUnits":1,"member":false,"deliveryMode":"STANDARD"}
                    """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").isNotEmpty());
        assertEquals(DesignPitfallExperimentController.class,
                DesignPitfallExceptionHandler.class.getAnnotation(RestControllerAdvice.class).assignableTypes()[0]);
        assertEquals("/oop/design-pitfalls",
                DesignPitfallExperimentController.class.getAnnotation(RequestMapping.class).value()[0]);
    }
}

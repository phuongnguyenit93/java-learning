package com.example.learning.oop.encapsulation;

import com.example.learning.oop.encapsulation.controller.EncapsulationExperimentController;
import com.example.learning.oop.encapsulation.exception.EncapsulationExceptionHandler;
import com.example.learning.oop.encapsulation.service.EncapsulationExperimentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EncapsulationHttpTest {
    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(
                new EncapsulationExperimentController(new EncapsulationExperimentService()))
                .setControllerAdvice(new EncapsulationExceptionHandler()).build();
    }

    @Test
    void stockJsonRequestChangesOnlyRequestLocalState() throws Exception {
        String payload = """
                {"sku":"BOX","initialOnHand":10,"initialReserved":0,
                 "actions":[{"type":"reserve","units":8},{"type":"reserve","units":3},{"type":"ship","units":4}]}
                """;
        mvc().perform(post("/oop/encapsulation/stock/experiment")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initial.onHand").value(10))
                .andExpect(jsonPath("$.steps[1].accepted").value(false))
                .andExpect(jsonPath("$.current.onHand").value(6))
                .andExpect(jsonPath("$.current.reserved").value(4));
    }

    @Test
    void cartPayloadReturnsObservableCurrentTotal() throws Exception {
        mvc().perform(post("/oop/encapsulation/cart/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"initialLines":[{"sku":"BOOK","unitPriceVnd":100,"quantity":2}],
                     "actions":[{"type":"put","sku":"PEN","unitPriceVnd":50,"quantity":1}]}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initial.totalVnd").value(200))
                .andExpect(jsonPath("$.current.totalVnd").value(250));
    }

    @Test
    void invalidPayloadIsMappedToBadRequest() throws Exception {
        mvc().perform(post("/oop/encapsulation/stock/experiment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sku":"BOX","initialOnHand":1,"initialReserved":3,
                     "actions":[{"type":"ship","units":1}]}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }
}

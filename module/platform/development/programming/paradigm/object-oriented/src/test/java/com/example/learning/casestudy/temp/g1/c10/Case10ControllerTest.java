package com.example.learning.casestudy.temp.g1.c10;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Case10ControllerTest {

    private static final String ROUTE =
            "/api/v1/java/case-studies/groups/1/cases/10/execute";

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new Case10Controller(new Case10Service()))
            .build();

    @Test
    void returnsBothLegacyAndPolicyResultsForSubmittedPricing() throws Exception {
        mvc.perform(post(ROUTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":1000,\"units\":2,\"discount\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legacy.subtotal").value(2000))
                .andExpect(jsonPath("$.legacy.discount").value(500))
                .andExpect(jsonPath("$.legacy.total").value(1500))
                .andExpect(jsonPath("$.policy.total").value(1500))
                .andExpect(jsonPath("$.equivalent").value(true));
    }

    @Test
    void mapsInvalidDiscountToHttp400() throws Exception {
        mvc.perform(post(ROUTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":1000,\"units\":2,\"discount\":2001}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsMissingInputAndSubtotalOverflowToHttp400() throws Exception {
        mvc.perform(post(ROUTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":1000,\"units\":2}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(ROUTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitPrice\":9223372036854775807,\"units\":2,\"discount\":0}"))
                .andExpect(status().isBadRequest());
    }
}

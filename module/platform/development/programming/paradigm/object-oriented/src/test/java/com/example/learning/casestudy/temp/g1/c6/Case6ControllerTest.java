package com.example.learning.casestudy.temp.g1.c6;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Case6ControllerTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new Case6Controller(new Case6Service()))
            .build();

    @Test
    void executesStandardAndExpressPolicies() throws Exception {
        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/6/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"standard\",\"weightGrams\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("standard"))
                .andExpect(jsonPath("$.weightGrams").value(1000))
                .andExpect(jsonPath("$.dispatchResult").value("STD:15000"));

        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/6/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"express\",\"weightGrams\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchResult").value("EXP:35000"));
    }

    @Test
    void invalidShipmentReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/6/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"express\",\"weightGrams\":6000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported shipment"));

        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/6/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"unknown\",\"weightGrams\":1000}"))
                .andExpect(status().isBadRequest());
    }
}

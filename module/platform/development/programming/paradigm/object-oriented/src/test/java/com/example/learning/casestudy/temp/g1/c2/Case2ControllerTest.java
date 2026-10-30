package com.example.learning.casestudy.temp.g1.c2;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Case2ControllerTest {

    private static final String ROUTE =
            "/api/v1/java/case-studies/groups/1/cases/2/execute";

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new Case2Controller(new Case2Service()))
            .build();

    @Test
    void executesBothFlowsWithTheSubmittedOrder() throws Exception {
        mvc.perform(post(ROUTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"B\",\"amount\":1250}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.original").value("B:1250"))
                .andExpect(jsonPath("$.simplified").value("B:1250"))
                .andExpect(jsonPath("$.equivalent").value(true));
    }

    @Test
    void returnsBadRequestForInvalidOrder() throws Exception {
        mvc.perform(post(ROUTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"\",\"amount\":-1}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(ROUTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"A\"}"))
                .andExpect(status().isBadRequest());
    }
}

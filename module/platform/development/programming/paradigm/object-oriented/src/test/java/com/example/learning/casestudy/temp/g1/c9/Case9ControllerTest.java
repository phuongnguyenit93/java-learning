package com.example.learning.casestudy.temp.g1.c9;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Case9ControllerTest {

    private static final String ROUTE =
            "/api/v1/java/case-studies/groups/1/cases/9/execute";

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new Case9Controller(new Case9Service()))
            .build();

    @Test
    void executesCpuWorkloadAndReturnsItsRealResult() throws Exception {
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"cpu\",\"rounds\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("cpu"))
                .andExpect(jsonPath("$.rounds").value(5))
                .andExpect(jsonPath("$.result").value(30));
    }

    @Test
    void executesAllocationWorkloadAndReturnsByteCount() throws Exception {
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"allocation\",\"rounds\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("allocation"))
                .andExpect(jsonPath("$.rounds").value(3))
                .andExpect(jsonPath("$.result").value(3072));
    }

    @Test
    void returnsBadRequestForUnknownModeAndUnsafeRoundCounts() throws Exception {
        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"unknown\",\"rounds\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("use: cpu|allocation"));

        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"allocation\",\"rounds\":20001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"cpu\",\"rounds\":0}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"cpu\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("mode and rounds are required"));
    }
}

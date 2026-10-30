package com.example.learning.casestudy.temp.g1.c5;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class Case5ControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Case5Controller(new Case5Service())).build();
    }

    @Test
    void postRunsCaseWithInputProvidedByClient() throws Exception {
        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/5/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sales\":[{\"id\":\"S1\",\"cents\":900}],\"simulateEmailFailure\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csv").value("id,cents\nS1,900\n"))
                .andExpect(jsonPath("$.simulatedEmailsSent").value(1));
    }

    @Test
    void invalidSaleReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/5/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sales\":[{\"id\":\"\",\"cents\":900}],\"simulateEmailFailure\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("every sale requires a nonblank id"));
    }

    @Test
    void simulatedSmtpFailureReturnsServiceUnavailable() throws Exception {
        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/5/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sales\":[{\"id\":\"S1\",\"cents\":900}],\"simulateEmailFailure\":true}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SMTP unavailable"));
    }
}

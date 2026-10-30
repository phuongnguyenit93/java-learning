package com.example.learning.casestudy.temp.g1.c3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Case3ControllerTest {
    private static final String ENDPOINT = "/api/v1/java/case-studies/groups/1/cases/3/execute";
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Case3Controller(new Case3Service())).build();
    }

    @Test
    void executesAccountScenarioOverHttp() throws Exception {
        mvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "openingBalance": 100,
                                  "operations": [
                                    {"action": "withdraw", "amount": 60},
                                    {"action": "deposit", "amount": 10}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalance").value(100))
                .andExpect(jsonPath("$.finalBalance").value(50))
                .andExpect(jsonPath("$.balances[0]").value(100))
                .andExpect(jsonPath("$.balances[1]").value(40))
                .andExpect(jsonPath("$.balances[2]").value(50));
    }

    @Test
    void invalidWithdrawalReturnsBadRequest() throws Exception {
        mvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "openingBalance": 100,
                                  "operations": [{"action": "withdraw", "amount": 150}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingRequiredOperationsReturnsBadRequest() throws Exception {
        mvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingBalance\": 100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overflowIsReportedAsBadRequest() throws Exception {
        mvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "openingBalance": 9223372036854775807,
                                  "operations": [{"action": "deposit", "amount": 1}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}

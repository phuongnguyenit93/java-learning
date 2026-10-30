package com.example.learning.casestudy.temp.g1.c1;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Case1ControllerTest {
    private static final String ROUTE =
            "/api/v1/java/case-studies/groups/1/cases/1/execute";

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(
                new Case1Controller(new Case1Service())).build();
    }

    @Test
    void httpResponseContainsRealChargesBookingAndNotifications() throws Exception {
        mvc().perform(post(ROUTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":"P-1","slot":"9:00","baseFee":100000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalCharge").value(100000))
                .andExpect(jsonPath("$.changedCharge").value(120000))
                .andExpect(jsonPath("$.bookedSlot").value("9:00"))
                .andExpect(jsonPath("$.notifications[0]").value("P-1"))
                .andExpect(jsonPath("$.notifications[1]").value("P-1"));
    }

    @Test
    void httpRejectsBlankPatientOrSlot() throws Exception {
        mvc().perform(post(ROUTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":"","slot":"9:00","baseFee":100000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid booking"));

        mvc().perform(post(ROUTE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"patientId":"P-1","slot":"","baseFee":100000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid booking"));
    }
}

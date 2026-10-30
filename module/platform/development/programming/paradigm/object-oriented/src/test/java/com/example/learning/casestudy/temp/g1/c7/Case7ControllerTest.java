package com.example.learning.casestudy.temp.g1.c7;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Case7ControllerTest {
    private static final String ROUTE = "/api/v1/java/case-studies/groups/1/cases/7/execute";

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new Case7Controller(new Case7Service())).build();
    }

    @Test
    void httpResponseShowsDomainPersistenceApiAndViewSeparately() throws Exception {
        mvc().perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"P-42","confirm":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialResponse.completed").value(false))
                .andExpect(jsonPath("$.persistence.paymentId").value("P-42"))
                .andExpect(jsonPath("$.persistence.dbStatus").value("PAID"))
                .andExpect(jsonPath("$.api.id").value("P-42"))
                .andExpect(jsonPath("$.api.completed").value(true))
                .andExpect(jsonPath("$.presentation.title").value("Đã thanh toán"))
                .andExpect(jsonPath("$.domainStatus").value("PAID"));
    }

    @Test
    void httpCanRepresentPendingPayments() throws Exception {
        mvc().perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"P-42","confirm":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persistence.dbStatus").value("NEW"))
                .andExpect(jsonPath("$.api.completed").value(false))
                .andExpect(jsonPath("$.presentation.title").value("Đang chờ"))
                .andExpect(jsonPath("$.domainStatus").value("NEW"));
    }

    @Test
    void httpRejectsBlankIdsAndMissingConfirmation() throws Exception {
        mvc().perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"  ","confirm":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("id"));

        mvc().perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"P-3"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("confirm"));
    }
}

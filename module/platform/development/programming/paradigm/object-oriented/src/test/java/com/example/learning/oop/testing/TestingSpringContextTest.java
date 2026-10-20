package com.example.learning.oop.testing;

import com.example.learning.ObjectOrientedProgrammingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Proves actual Spring scanning/DI and endpoint mapping, not only standalone MVC. */
@SpringBootTest(classes = ObjectOrientedProgrammingApplication.class)
@AutoConfigureMockMvc
class TestingSpringContextTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void servicesAreDiscoveredAndEndpointsRespondThroughSpringContext() throws Exception {
        mvc.perform(post("/oop/testing/inventory/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"capacity":5,"actions":[{"type":"RESERVE","seats":3},
                           {"type":"RESERVE","seats":4}]}
                          """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs[0].allPassed").value(true))
                .andExpect(jsonPath("$.runs[2].allPassed").value(false));
        mvc.perform(post("/oop/testing/pricing/regression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"pricesVnd":{"BOOK":100000},
                           "lines":[{"sku":"BOOK","quantity":1}],"member":false}
                          """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seededDefectDetected").value(true));
    }
}

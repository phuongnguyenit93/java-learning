package com.example.learning.oop.solid;

import com.example.learning.ObjectOrientedProgrammingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses actual Spring bean discovery/constructor injection and MVC registration. */
@SpringBootTest(classes = ObjectOrientedProgrammingApplication.class)
@AutoConfigureMockMvc
class SolidSpringContextTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void applicationWiresPoliciesAndExposesSolidEndpoints() throws Exception {
        mvc.perform(post("/oop/solid/discount/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyCode\":\"TEN_PERCENT\",\"subtotalCents\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discountCents").value(99))
                .andExpect(jsonPath("$.payableCents").value(900));
    }
}

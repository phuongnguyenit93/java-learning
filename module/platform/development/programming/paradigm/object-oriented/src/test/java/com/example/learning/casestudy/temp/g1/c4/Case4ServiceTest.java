package com.example.learning.casestudy.temp.g1.c4;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Case4ServiceTest {

    private final Case4Service service = new Case4Service();

    @Test
    void sameCustomerIdSupportsIndependentSalesAndSupportResponsibilities() {
        Case4Service.Result result = service.execute("C-1", 25, true);

        assertEquals("C-1", result.salesCustomerId());
        assertEquals(result.salesCustomerId(), result.supportCustomerId());
        assertEquals(25, result.loyaltyPoints());
        assertTrue(result.priorityCase());
    }

    @Test
    void supportPriorityCanRemainUnmarkedWithoutAffectingSalesPoints() {
        Case4Service.Result withoutPriority = service.execute("C-2", 12, false);
        Case4Service.Result withPriority = service.execute("C-2", 7, true);

        assertEquals("C-2", withoutPriority.supportCustomerId());
        assertEquals(12, withoutPriority.loyaltyPoints());
        assertFalse(withoutPriority.priorityCase());
        assertEquals(7, withPriority.loyaltyPoints());
        assertTrue(withPriority.priorityCase());
    }

    @Test
    void invalidCustomerIdAndPointsAreRejected() {
        assertEquals("customerId", assertThrows(IllegalArgumentException.class,
                () -> service.execute(null, 25, true)).getMessage());
        assertEquals("customerId", assertThrows(IllegalArgumentException.class,
                () -> service.execute("  ", 25, true)).getMessage());
        assertEquals("points", assertThrows(IllegalArgumentException.class,
                () -> service.execute("C-1", 0, true)).getMessage());
        assertEquals("points", assertThrows(IllegalArgumentException.class,
                () -> service.execute("C-1", -5, true)).getMessage());
    }

    @Test
    void httpEndpointReturnsRealResultsAndBadRequestForInvalidInput() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Case4Controller(service)).build();

        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/4/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"C-1\",\"points\":25,\"markPriority\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salesCustomerId").value("C-1"))
                .andExpect(jsonPath("$.supportCustomerId").value("C-1"))
                .andExpect(jsonPath("$.loyaltyPoints").value(25))
                .andExpect(jsonPath("$.priorityCase").value(true));

        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/4/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"\",\"points\":25,\"markPriority\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("customerId"));

        mvc.perform(post("/api/v1/java/case-studies/groups/1/cases/4/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"C-1\",\"points\":0,\"markPriority\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("points"));
    }
}

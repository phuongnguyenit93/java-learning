package com.example.learning.controller;

import com.example.learning.dto.InventoryRequest;
import com.example.learning.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService mockInventoryService;

    @Test
    void testIsInStock() throws Exception {
        // Setup
        when(mockInventoryService.isInStock("skuCode", 0)).thenReturn(false);

        // Run the test and verify the results
        mockMvc.perform(get("/api/inventory/in-stock")
                        .param("skuCode", "skuCode")
                        .param("quantity", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{}", true));
    }

    @Test
    void testIsInStock_InventoryServiceReturnsTrue() throws Exception {
        // Setup
        when(mockInventoryService.isInStock("skuCode", 0)).thenReturn(true);

        // Run the test and verify the results
        mockMvc.perform(get("/api/inventory/in-stock")
                        .param("skuCode", "skuCode")
                        .param("quantity", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{}", true));
    }

    @Test
    void testCreateStock() throws Exception {
        // Setup
        // Run the test and verify the results
        mockMvc.perform(post("/api/inventory")
                        .content("content").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("{}", true));
        verify(mockInventoryService).createStock(new InventoryRequest("skuCode", 0));
    }
}

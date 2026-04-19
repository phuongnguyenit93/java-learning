package com.example.learning.controller;

import com.example.learning.InventoryServiceApplication;
import com.example.learning.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = InventoryController.class)
@ContextConfiguration(classes = InventoryServiceApplication.class)
@WithMockUser
public class InventoryControllerTest1 {

    @Autowired
    private MockMvc mockMvc;

    @MockBean // Tạo bản Mock cho Service và đẩy vào Spring Context
    private InventoryService inventoryService;

    @Test

    void shouldReturnTrue_WhenProductIsInStock() throws Exception {
        // Arrange: Giả lập Service trả về true
        String skuCode = "IPHONE-15";
        Integer quantity = 1;
        when(inventoryService.isInStock(skuCode, quantity)).thenReturn(true);

        // Act & Assert (Tuân thủ Given - When - Then)
        mockMvc.perform(get("/api/inventory/in-stock")
                        .param("skuCode", skuCode)
                        .param("quantity", quantity.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // Check HttpStatus.OK (200)
                .andExpect(content().string("true")); // Check body trả về "true"
    }

    @Test
    void shouldReturnFalse_WhenProductIsOutOfStock() throws Exception {
        // Arrange: Giả lập Service trả về false
        String skuCode = "IPHONE-15";
        Integer quantity = 100;
        when(inventoryService.isInStock(skuCode, quantity)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/api/inventory/in-stock")
                        .param("skuCode", skuCode)
                        .param("quantity", quantity.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void shouldReturnBadRequest_WhenMissingParams() throws Exception {
        // Test trường hợp thiếu tham số (Edge Case)
        mockMvc.perform(get("/api/inventory/in-stock")
                        .param("skuCode", "IPHONE-15")) // Thiếu quantity
                .andExpect(status().isBadRequest()); // Spring tự trả về 400
    }
}

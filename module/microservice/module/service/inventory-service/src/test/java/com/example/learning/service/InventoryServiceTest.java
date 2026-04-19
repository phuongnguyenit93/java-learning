package com.example.learning.service;

import com.example.learning.dto.InventoryRequest;
import com.example.learning.mapper.InventoryMapper;
import com.example.learning.model.Inventory;
import com.example.learning.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository mockInventoryRepository;
    @Mock
    private InventoryMapper mockInventoryMapper;

    private InventoryService inventoryServiceUnderTest;

    @BeforeEach
    void setUp() {
        inventoryServiceUnderTest = new InventoryService(mockInventoryRepository, mockInventoryMapper);
    }

    @Test
    void testIsInStock() {
        // Setup
        when(mockInventoryRepository.existsBySkuCodeAndQuantityIsGreaterThanEqual("skuCode", 0)).thenReturn(false);

        // Run the test
        final boolean result = inventoryServiceUnderTest.isInStock("skuCode", 0);

        // Verify the results
        assertThat(result).isFalse();
    }

    @Test
    void testIsInStock_InventoryRepositoryReturnsTrue() {
        // Setup
        when(mockInventoryRepository.existsBySkuCodeAndQuantityIsGreaterThanEqual("skuCode", 0)).thenReturn(true);

        // Run the test
        final boolean result = inventoryServiceUnderTest.isInStock("skuCode", 0);

        // Verify the results
        assertThat(result).isTrue();
    }

    @Test
    void testCreateStock() {
        // Setup
        final InventoryRequest inventoryRequest = new InventoryRequest("skuCode", 0);
        when(mockInventoryMapper.toEntity(new InventoryRequest("skuCode", 0)))
                .thenReturn(new Inventory(0L, "skuCode", 0));

        // Run the test
        inventoryServiceUnderTest.createStock(inventoryRequest);

        // Verify the results
        verify(mockInventoryRepository).save(new Inventory(0L, "skuCode", 0));
    }

    @Test
    void testCreateStock_InventoryRepositoryThrowsOptimisticLockingFailureException() {
        // Setup
        final InventoryRequest inventoryRequest = new InventoryRequest("skuCode", 0);
        when(mockInventoryMapper.toEntity(new InventoryRequest("skuCode", 0)))
                .thenReturn(new Inventory(0L, "skuCode", 0));
        when(mockInventoryRepository.save(new Inventory(0L, "skuCode", 0)))
                .thenThrow(OptimisticLockingFailureException.class);

        // Run the test
        assertThatThrownBy(() -> inventoryServiceUnderTest.createStock(inventoryRequest))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}

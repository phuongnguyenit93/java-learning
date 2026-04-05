package com.example.learning.service;

import com.example.learning.dto.InventoryRequest;
import com.example.learning.mapper.InventoryMapper;
import com.example.learning.model.Inventory;
import com.example.learning.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;

    public boolean isInStock(String skuCode, Integer quantity) {
        return inventoryRepository.existsBySkuCodeAndQuantityIsGreaterThanEqual(skuCode,quantity);
    }

    public void createStock(InventoryRequest inventoryRequest) {
        Inventory inventory = inventoryMapper.toEntity(inventoryRequest);
        inventoryRepository.save(inventory);
    }
}

package com.example.learning.integration.inventory;

public interface InventoryProvider {
    boolean isInStock(String skuCode, Integer quantity);
}

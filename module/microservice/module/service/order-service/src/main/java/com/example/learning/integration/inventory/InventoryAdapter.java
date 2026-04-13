package com.example.learning.integration.inventory;

public abstract class InventoryAdapter implements InventoryProvider {
    @Override
    public boolean isInStock (String skuCode, Integer quantity) {
        return false;
    }
}

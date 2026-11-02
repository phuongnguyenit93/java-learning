package com.example.learning.casestudy.domain.group2.case13;

public final class Item {
    final String sku;
    public int quantity;
    public Item(String sku, int quantity) {
        this.sku = sku;
        this.quantity = quantity;
    }
    Item copy() {
        return new Item(sku, quantity);
    }
}

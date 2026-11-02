package com.example.learning.casestudy.application.group8.case9;

import com.example.learning.casestudy.port.group8.case9.Catalog;
import com.example.learning.casestudy.port.group8.case9.Orders;
import java.util.Objects;

public final class Checkout9 {
    private final Catalog catalog;
    private final Orders orders;
    public Checkout9(Catalog catalog, Orders orders) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.orders = Objects.requireNonNull(orders, "orders");
    }
    public void place(String id, String sku, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty");
        orders.save(id, Math.multiplyExact(catalog.unitPrice(sku), qty));
    }
}

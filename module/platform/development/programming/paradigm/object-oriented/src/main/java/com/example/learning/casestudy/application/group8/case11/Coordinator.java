package com.example.learning.casestudy.application.group8.case11;

import com.example.learning.casestudy.application.group8.case11.Billing;
import com.example.learning.casestudy.domain.group8.case11.Inventory11;

public record Coordinator(Billing billing, Inventory11 inventory) {
    public long purchase(String sku, int qty) {
        if (qty <= 0 || !inventory.canShip(sku))
            throw new IllegalArgumentException("not available");
        return billing.charge(sku, qty);
    }
}

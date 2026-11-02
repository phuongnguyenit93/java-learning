package com.example.learning.casestudy.domain.group12.case3;

import com.example.learning.casestudy.port.group12.case3.ShippingPrice;

public class BranchCalculator implements ShippingPrice {
    public int price(Shipment shipment) {
        if (shipment == null || shipment.weightGrams() < 0)
        throw new IllegalArgumentException("Invalid shipment");
        int base = shipment.weightGrams() == 0 ? 0 : 100;
        return base + (shipment.express() ? 50 : 0);
    }
}

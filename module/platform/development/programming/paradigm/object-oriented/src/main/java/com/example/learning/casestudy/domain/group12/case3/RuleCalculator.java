package com.example.learning.casestudy.domain.group12.case3;

import com.example.learning.casestudy.port.group12.case3.ShippingPrice;

public class RuleCalculator implements ShippingPrice {
    public int price(Shipment shipment) {
        if (shipment == null || shipment.weightGrams() < 0)
        throw new IllegalArgumentException("Invalid shipment");
        return (shipment.weightGrams() > 0 ? 100 : 0)
        + (shipment.express() ? 50 : 0);
    }
}

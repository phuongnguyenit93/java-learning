package com.example.learning.casestudy.utils.group12.case3;

import com.example.learning.casestudy.domain.group12.case3.Shipment;
import com.example.learning.casestudy.port.group12.case3.ShippingPrice;
import java.util.List;

public final class Case3Utils {
    private Case3Utils() {
    }
    public static void expectInvalid(ShippingPrice price, Shipment input) {
        try {
            price.price(input);
            throw new AssertionError("Must throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    public static void contract(ShippingPrice price) {
        for (Shipment s : List.of(
        new Shipment(0, false), new Shipment(0, true),
        new Shipment(1, false), new Shipment(1, true))) {
            int expected = (s.weightGrams() > 0 ? 100 : 0)
            + (s.express() ? 50 : 0);
            if (price.price(s) != expected) throw new AssertionError("Boundary");
            if (price.price(s) != price.price(s))
            throw new AssertionError("Unexpected state/side effect");
        }
        expectInvalid(price, null);
        expectInvalid(price, new Shipment(-1, false));
    }
}

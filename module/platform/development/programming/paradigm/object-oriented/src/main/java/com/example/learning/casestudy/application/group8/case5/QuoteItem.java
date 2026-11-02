package com.example.learning.casestudy.application.group8.case5;

import com.example.learning.casestudy.port.group8.case5.Inventory5;
import com.example.learning.casestudy.port.group8.case5.Prices;

public record QuoteItem(Prices prices, Inventory5 inventory) {
    public long quote(String sku) {
        if (!inventory.available(sku)) throw new IllegalStateException("out of stock");
        return prices.quote(sku);
    }
}

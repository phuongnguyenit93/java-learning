package com.example.learning.casestudy.application.group8.case11;

import com.example.learning.casestudy.port.group8.case11.RateTable;
import java.util.Objects;

public final class Billing {
    private final RateTable rates;
    public Billing(RateTable rates) { this.rates = Objects.requireNonNull(rates); }
    public long charge(String sku, int qty) {
        return Math.multiplyExact(rates.unitPrice(sku), qty);
    }
}

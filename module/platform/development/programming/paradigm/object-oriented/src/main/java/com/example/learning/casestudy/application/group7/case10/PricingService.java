package com.example.learning.casestudy.application.group7.case10;

import com.example.learning.casestudy.port.group7.case10.PriceRepository;

public final class PricingService {
    private final PriceRepository prices;
    public PricingService(PriceRepository prices){
        this.prices = prices;

    }
    public long total(String sku, int count){
        if (count<=0)throw new IllegalArgumentException("count");
        long price = prices.priceFor(sku);
        if (price<0)throw new IllegalStateException("negative price");
        return Math.multiplyExact(price, count);

    }
}

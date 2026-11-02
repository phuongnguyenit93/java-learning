package com.example.learning.casestudy.domain.group13.case10;

import java.util.HashMap;
import java.util.Map;

public final class Order {
    private final Map<String, Integer> qty = new HashMap<>();
    private Integer cachedTotal;
    public synchronized void setQuantity(String sku, int count){
        if (sku == null || sku.isBlank() || count<0)
        throw new IllegalArgumentException();
        qty.put(sku, count);
        cachedTotal = null; // Invalidate cùng mutation.
    }
    public synchronized int total(){
        if (cachedTotal == null)
        cachedTotal = qty.values().stream().mapToInt(Integer::intValue).sum();
        return cachedTotal;
    }
}

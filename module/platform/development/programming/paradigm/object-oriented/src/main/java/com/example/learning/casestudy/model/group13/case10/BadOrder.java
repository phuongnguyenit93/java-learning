package com.example.learning.casestudy.model.group13.case10;

import java.util.HashMap;
import java.util.Map;

public final class BadOrder {
    public final Map<String, Integer> qty = new HashMap<>();
    public Integer cachedTotal;
    public int total(){
        if (cachedTotal == null)
        cachedTotal = qty.values().stream().mapToInt(Integer::intValue).sum();
        return cachedTotal;
    }
    public void set(String sku, int count){
        qty.put(sku, count);
    }
    // Quên invalidation.
}

package com.example.learning.casestudy.domain.group7.case7;

import java.util.ArrayList;
import java.util.List;

public class Cart {
    private final int maxItems;
    private final List<String> items = new ArrayList<>();
    public Cart(int maxItems){
        if (maxItems<1)throw new IllegalArgumentException();
        this.maxItems = maxItems;

    }
    public void addItem(String sku){
        if (sku == null || sku.isBlank() || items.size()>=maxItems)
        throw new IllegalArgumentException("limit or sku");
        items.add(sku);

    }
    public List<String> snapshot(){
        return List.copyOf(items);

    }
}

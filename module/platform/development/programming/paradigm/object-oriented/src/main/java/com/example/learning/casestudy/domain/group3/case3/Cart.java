package com.example.learning.casestudy.domain.group3.case3;

import com.example.learning.casestudy.domain.group3.case3.Item3;
import java.util.ArrayList;
import java.util.List;

public class Cart {
    private final List<Item3> items = new ArrayList<>();
    private boolean locked;
    public void add(String sku, int qty, int stock) {
        if (locked)throw new IllegalStateException();
        if (sku == null || sku.isBlank() || qty <= 0 || qty>stock) throw new IllegalArgumentException();
        items.add(new Item3(sku, qty));
    }
    public void checkout() {
        locked = true;
    }
    public List<Item3> items() {
        return List.copyOf(items);
    }
}

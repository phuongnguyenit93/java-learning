package com.example.learning.casestudy.domain.group2.case13;

import java.util.ArrayList;
import java.util.List;

public final class Order {
    public final List<Item> items;
    public Order(List<Item> items) {
        this.items = items;
    }
    public Order shallowCopy() {
        return new Order(new ArrayList<>(items));
    }
    public Order deepCopy() {
        List<Item> copied = new ArrayList<>();
        for (Item i : items) copied.add(i.copy());
        return new Order(copied);
    }
}

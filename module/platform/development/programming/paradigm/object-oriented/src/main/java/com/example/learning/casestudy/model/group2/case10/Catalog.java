package com.example.learning.casestudy.model.group2.case10;

import java.util.ArrayList;
import java.util.List;

public final class Catalog {
    private final List<ProductView> products;
    public Catalog(List<Product> src) {
        List<ProductView> copied = new ArrayList<>();
        for (Product p : src) copied.add(new ProductView(p.sku, p.price));
        products = List.copyOf(copied);
    }
    public List<ProductView> products() {
        return products;
    }
}

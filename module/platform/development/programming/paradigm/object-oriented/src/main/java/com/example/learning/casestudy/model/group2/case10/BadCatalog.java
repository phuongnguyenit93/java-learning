package com.example.learning.casestudy.model.group2.case10;

import java.util.List;

public final class BadCatalog {
    public final List<Product> products;
    public BadCatalog(List<Product> src) {
        products = List.copyOf(src);
    }
}

package com.example.learning.casestudy.adapter.group4.case5;

import com.example.learning.casestudy.model.group4.case5.Product;
import com.example.learning.casestudy.port.group4.case5.ProductReader;
import java.util.Optional;

public final class CatalogMirror implements ProductReader {
    public Optional<Product> find(String sku) {
        return "A".equals(sku) ? Optional.of(new Product("A"))
                               : Optional.empty();
    }
}

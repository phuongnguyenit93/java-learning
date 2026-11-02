package com.example.learning.casestudy.repository.group4.case5;

import com.example.learning.casestudy.model.group4.case5.Product;
import com.example.learning.casestudy.port.group4.case5.ProductReader;
import com.example.learning.casestudy.port.group4.case5.ProductWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class MemoryStore implements ProductReader, ProductWriter {
    private final Map<String, Product> data = new HashMap<>();
    public Optional<Product> find(String sku) {
        return Optional.ofNullable(data.get(sku));
    }
    public void save(Product p) { data.put(p.sku(), p); }
}

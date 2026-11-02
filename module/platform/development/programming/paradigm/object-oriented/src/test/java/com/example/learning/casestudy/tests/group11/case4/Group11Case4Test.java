package com.example.learning.casestudy.tests.group11.case4;

import com.example.learning.casestudy.service.Group11Service;
import com.example.learning.casestudy.domain.group11.case4.Product;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Group11Case4Test {
    @Test
    void originalMainBodyExecutes() {
        new Group11Service().executeCase4();
    }

    @Test
    void verifiesSourceBehaviorWithJUnitAssertions() {
        Product a = new Product("Bút", "SKU-1");
        Product b = new Product("Bút", "SKU-2");
        SortedSet<Product> broken = new TreeSet<>(Comparator.comparing(Product::name));
        broken.add(a);
        broken.add(b);
        assertEquals(1, broken.size());
        Comparator<Product> safe = Comparator.comparing(Product::name).thenComparing(Product::sku);
        SortedSet<Product> fixed = new TreeSet<>(safe);
        fixed.add(a);
        fixed.add(b);
        assertEquals(2, fixed.size());
        assertTrue(fixed.contains(a));
        assertTrue(fixed.contains(b));
        assertNotEquals(0, safe.compare(a, b));
        assertEquals(0, safe.compare(a, a));
        assertEquals("SKU-1", fixed.first().sku());
    }
}

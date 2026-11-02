package com.example.learning.casestudy.tests.group11.case2;

import com.example.learning.casestudy.service.Group11Service;
import com.example.learning.casestudy.domain.group11.case2.ProductKey;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class Group11Case2Test {
    @Test
    void originalMainBodyExecutes() {
        new Group11Service().executeCase2();
    }

    @Test
    void verifiesSourceBehaviorWithJUnitAssertions() {
        ProductKey a = new ProductKey("BOOK", "42");
        ProductKey b = new ProductKey("BOOK", "42");
        ProductKey c = new ProductKey("BOOK", "42");
        assertEquals(a, a);
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(b, c);
        assertEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new ProductKey("BOOK", "43"));
        assertNotEquals(a, null);
        Map<ProductKey, Integer> map = new HashMap<>();
        map.put(a, 250);
        assertEquals(250, map.get(b));
    }
}

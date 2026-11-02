package com.example.learning.casestudy.tests.group11.case1;

import com.example.learning.casestudy.service.Group11Service;
import com.example.learning.casestudy.domain.group11.case1.Customer1;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Group11Case1Test {
    @Test
    void originalMainBodyExecutes() {
        new Group11Service().executeCase1();
    }

    @Test
    void verifiesSourceBehaviorWithJUnitAssertions() {
        Customer1 original = new Customer1("C-1", "Before");
        Map<Customer1, String> map = new HashMap<>();
        Set<Customer1> set = new HashSet<>();
        map.put(original, "active");
        set.add(original);
        int stableHash = original.hashCode();
        original.rename("After");
        assertEquals(stableHash, original.hashCode());
        assertEquals("active", map.get(new Customer1("C-1", "Alias")));
        assertTrue(set.contains(new Customer1("C-1", "Different")));
        assertEquals(1, set.size());
    }
}

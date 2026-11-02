package com.example.learning.casestudy.tests.group11.case9;

import com.example.learning.casestudy.service.Group11Service;
import com.example.learning.casestudy.domain.group11.case9.Customer9;
import com.example.learning.casestudy.port.group11.case9.Repository;
import com.example.learning.casestudy.repository.group11.case9.MemoryRepository;
import com.example.learning.casestudy.utils.group11.case9.Demo9Utils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Group11Case9Test {
    @Test
    void originalMainBodyExecutes() {
        new Group11Service().executeCase9();
    }

    @Test
    void verifiesSourceBehaviorWithJUnitAssertions() {
        Repository<Customer9, String> customers = new MemoryRepository<>();
        customers.save("C1", new Customer9("C1", "Mai"));
        assertEquals("Mai", Demo9Utils.customerName(customers, "C1"));
        assertTrue(customers.find("missing").isEmpty());
        assertThrows(java.util.NoSuchElementException.class,
        () -> Demo9Utils.customerName(customers, "missing"));
    }
}

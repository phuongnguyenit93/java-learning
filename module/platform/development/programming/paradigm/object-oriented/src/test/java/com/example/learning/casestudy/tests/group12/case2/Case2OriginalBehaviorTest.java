package com.example.learning.casestudy.tests.group12.case2;

import com.example.learning.casestudy.service.Group12Service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Case2OriginalBehaviorTest {
    @Test
    void originalMainBehaviorPasses() {
        assertDoesNotThrow(() -> new Group12Service().executeCase2());
    }
}

package com.example.learning.casestudy.tests.group13.case2;

import com.example.learning.casestudy.service.Group13Service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Case2OriginalBehaviorTest {
    @Test
    void originalMainBehaviorPasses() {
        assertDoesNotThrow(() -> new Group13Service().executeCase2());
    }
}

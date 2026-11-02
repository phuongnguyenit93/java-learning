package com.example.learning.casestudy.tests.group13.case5;

import com.example.learning.casestudy.service.Group13Service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Case5OriginalBehaviorTest {
    @Test
    void originalMainBehaviorPasses() {
        assertDoesNotThrow(() -> new Group13Service().executeCase5());
    }
}

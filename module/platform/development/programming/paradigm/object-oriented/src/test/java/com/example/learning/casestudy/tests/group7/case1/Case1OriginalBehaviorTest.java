package com.example.learning.casestudy.tests.group7.case1;

import com.example.learning.casestudy.service.Group7Service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Case1OriginalBehaviorTest {
    @Test
    void originalMainAssertionsAndExceptionPathsPass() {
        assertDoesNotThrow(() -> new Group7Service().executeCase1());

    }
}

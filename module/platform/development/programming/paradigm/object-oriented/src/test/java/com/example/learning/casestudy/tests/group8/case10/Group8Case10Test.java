package com.example.learning.casestudy.tests.group8.case10;

import com.example.learning.casestudy.service.Group8Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Group8Case10Test {
    private final Group8Service group = new Group8Service();

    @Test
    void executesOriginalMainStatementsWithAssertionsEnabled() {
        assertTrue(Group8Case10Test.class.desiredAssertionStatus(),
        "The source checks use assert: execute tests with -ea");
        assertDoesNotThrow(group::executeCase10);
    }

    @Test
    void repeatedExecutionPreservesOriginalCaseBehavior() {
        assertDoesNotThrow(group::executeCase10);
        assertDoesNotThrow(group::executeCase10);
    }
}

package com.example.learning.casestudy.tests.group8.case3;

import com.example.learning.casestudy.service.Group8Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Group8Case3Test {
    private final Group8Service group = new Group8Service();

    @Test
    void executesOriginalMainStatementsWithAssertionsEnabled() {
        assertTrue(Group8Case3Test.class.desiredAssertionStatus(),
        "The source checks use assert: execute tests with -ea");
        assertDoesNotThrow(group::executeCase3);
    }

    @Test
    void repeatedExecutionPreservesOriginalCaseBehavior() {
        assertDoesNotThrow(group::executeCase3);
        assertDoesNotThrow(group::executeCase3);
    }
}

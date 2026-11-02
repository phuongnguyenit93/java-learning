package com.example.learning.casestudy.tests.group8.case2;

import com.example.learning.casestudy.service.Group8Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Group8Case2Test {
    private final Group8Service group = new Group8Service();

    @Test
    void executesOriginalMainStatementsWithAssertionsEnabled() {
        assertTrue(Group8Case2Test.class.desiredAssertionStatus(),
        "The source checks use assert: execute tests with -ea");
        assertDoesNotThrow(group::executeCase2);
    }

    @Test
    void repeatedExecutionPreservesOriginalCaseBehavior() {
        assertDoesNotThrow(group::executeCase2);
        assertDoesNotThrow(group::executeCase2);
    }
}

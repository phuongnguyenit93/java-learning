package com.example.learning.casestudy.tests.group8.case11;

import com.example.learning.casestudy.service.Group8Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Group8Case11Test {
    private final Group8Service group = new Group8Service();

    @Test
    void executesOriginalMainStatementsWithAssertionsEnabled() {
        assertTrue(Group8Case11Test.class.desiredAssertionStatus(),
        "The source checks use assert: execute tests with -ea");
        assertDoesNotThrow(group::executeCase11);
    }

    @Test
    void repeatedExecutionPreservesOriginalCaseBehavior() {
        assertDoesNotThrow(group::executeCase11);
        assertDoesNotThrow(group::executeCase11);
    }
}

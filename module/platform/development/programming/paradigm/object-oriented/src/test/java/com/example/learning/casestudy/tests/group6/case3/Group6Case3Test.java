package com.example.learning.casestudy.tests.group6.case3;

import com.example.learning.casestudy.service.Group6Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Group6Case3Test {
    @Test
    void originalMainAssertionsAndRepeatability() {
        Group6Service group = new Group6Service();
        assertDoesNotThrow(group::executeCase3);
        assertDoesNotThrow(group::executeCase3);
    }
}

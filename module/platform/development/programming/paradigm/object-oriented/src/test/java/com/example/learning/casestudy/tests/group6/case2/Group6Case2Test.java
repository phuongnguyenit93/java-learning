package com.example.learning.casestudy.tests.group6.case2;

import com.example.learning.casestudy.service.Group6Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Group6Case2Test {
    @Test
    void originalMainAssertionsAndRepeatability() {
        Group6Service group = new Group6Service();
        assertDoesNotThrow(group::executeCase2);
        assertDoesNotThrow(group::executeCase2);
    }
}

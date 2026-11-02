package com.example.learning.casestudy.tests.group6.case7;

import com.example.learning.casestudy.service.Group6Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Group6Case7Test {
    @Test
    void originalMainAssertionsAndRepeatability() {
        Group6Service group = new Group6Service();
        assertDoesNotThrow(group::executeCase7);
        assertDoesNotThrow(group::executeCase7);
    }
}

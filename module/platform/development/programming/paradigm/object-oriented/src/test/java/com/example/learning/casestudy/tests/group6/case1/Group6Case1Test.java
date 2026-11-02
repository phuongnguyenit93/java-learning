package com.example.learning.casestudy.tests.group6.case1;

import com.example.learning.casestudy.service.Group6Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Group6Case1Test {
    @Test
    void originalMainAssertionsAndRepeatability() {
        Group6Service group = new Group6Service();
        assertDoesNotThrow(group::executeCase1);
        assertDoesNotThrow(group::executeCase1);
    }
}

package com.example.learning.casestudy.tests.group9.case2;

import com.example.learning.casestudy.service.Group9Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Group9Case2Test {
    @Test
    void originalAssertionsAndRepeatedExecution() {
        Group9Service service = new Group9Service();
        assertDoesNotThrow(service::executeCase2);
        assertDoesNotThrow(service::executeCase2);
    }
}

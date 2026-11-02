package com.example.learning.casestudy.tests.group9.case13;

import com.example.learning.casestudy.service.Group9Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Group9Case13Test {
    @Test
    void originalAssertionsAndRepeatedExecution() {
        Group9Service service = new Group9Service();
        assertDoesNotThrow(service::executeCase13);
        assertDoesNotThrow(service::executeCase13);
    }
}

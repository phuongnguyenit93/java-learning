package com.example.learning.casestudy.tests.group5.case4;

import com.example.learning.casestudy.service.Group5Service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Case4Test {
    @Test
    void sourceAssertionsExecuteForRepeatedServiceCalls() {
        boolean assertionsActive = false;
        assert assertionsActive = true;
        assertTrue(assertionsActive, "Run STEP 8 source assertions with -ea");
        Group5Service group = new Group5Service();
        group.executeCase4();
        group.executeCase4();
    }
}

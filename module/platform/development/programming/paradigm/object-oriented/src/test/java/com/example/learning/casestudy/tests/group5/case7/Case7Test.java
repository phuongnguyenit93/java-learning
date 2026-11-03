package com.example.learning.casestudy.tests.group5.case7;

import com.example.learning.casestudy.domain.group5.case7.Base7;
import com.example.learning.casestudy.domain.group5.case7.Child7;
import com.example.learning.casestudy.service.Group5Service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Case7Test {
    @Test
    void sourceAssertionsExecuteForRepeatedServiceCalls() {
        boolean assertionsActive = false;
        assert assertionsActive = true;
        assertTrue(assertionsActive, "Run STEP 8 source assertions with -ea");
        Group5Service group = new Group5Service();
        group.executeCase7();
        group.executeCase7();

        Base7 ref = new Child7();
        assertEquals("base-static", Base7.kind());
        assertEquals("child-static", Child7.kind());
        assertEquals("base-static", ref.kind(), "Static method hiding uses declared reference type");
        assertEquals("child-instance", ref.render(), "Instance overriding uses runtime type");
    }
}

package com.example.learning.casestudy.tests.group5.case5;

import com.example.learning.casestudy.service.Group5Service;
import com.example.learning.casestudy.utils.group5.case5.Case5Utils;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Case5Test {
    @Test
    void originalAssertionsStillExecuteTwice() {
        boolean assertionsActive = false;
        assert assertionsActive = true;
        assertTrue(assertionsActive, "Run source with -ea");
        Group5Service group = new Group5Service();
        group.executeCase5();
        group.executeCase5();
    }

    @Test
    void checkedIOExceptionBridgeKeepsTheOriginalExceptionObject() {
        IOException original = new IOException("source error");
        IOException observed = assertThrows(IOException.class,
            () -> { throw Case5Utils.<RuntimeException>rethrow(original); });
        assertSame(original, observed);
    }
}

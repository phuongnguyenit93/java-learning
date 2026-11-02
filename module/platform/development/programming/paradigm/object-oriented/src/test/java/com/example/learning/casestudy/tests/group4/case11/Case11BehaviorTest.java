package com.example.learning.casestudy.tests.group4.case11;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.learning.casestudy.service.Group4Service;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

class Case11BehaviorTest {
    private final Group4Service service = new Group4Service();

    @Test
    @ResourceLock("SYSTEM_OUT")
    void exactOriginalOutputAndMainAssertions() {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            assertDoesNotThrow(() -> service.executeCase11());
        } finally {
            System.setOut(original);
        }
        assertEquals("PASS: invariant sequence cannot be overridden" + System.lineSeparator(), bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void repeatableOriginalMainExecution() {
        assertDoesNotThrow(() -> service.executeCase11());
        assertDoesNotThrow(() -> service.executeCase11());
    }
}

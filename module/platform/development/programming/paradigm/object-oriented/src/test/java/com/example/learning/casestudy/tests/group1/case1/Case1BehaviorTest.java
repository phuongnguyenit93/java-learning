package com.example.learning.casestudy.tests.group1.case1;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.learning.casestudy.service.Group1Service;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

class Case1BehaviorTest {
    private final Group1Service service = new Group1Service();

    @Test
    @ResourceLock("SYSTEM_OUT")
    void sourceMainAssertionsAndOutputArePreserved() {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            assertDoesNotThrow(() -> service.executeCase1());
        } finally {
            System.setOut(original);
        }
        assertEquals("PASS: phí thay đổi, lịch hẹn vẫn đúng" + System.lineSeparator(), bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void originalProgramIsRepeatableWithinSameJVM() {
        assertDoesNotThrow(() -> service.executeCase1());
        assertDoesNotThrow(() -> service.executeCase1());
    }
}

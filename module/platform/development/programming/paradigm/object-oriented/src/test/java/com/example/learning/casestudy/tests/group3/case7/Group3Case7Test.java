package com.example.learning.casestudy.tests.group3.case7;

import com.example.learning.casestudy.service.Group3Service;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class Group3Case7Test {
    @Test
    @ResourceLock(Resources.SYSTEM_OUT)
    void originalAssertionsAndConsoleOutputArePreservedAcrossRepeatedRuns() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(stream);
            Group3Service service = new Group3Service();
            assertDoesNotThrow(service::executeCase7);
            assertDoesNotThrow(service::executeCase7);
        } finally {
            System.setOut(original);
        }
        assertEquals("PASS" + System.lineSeparator() + "PASS" + System.lineSeparator(),
                captured.toString(StandardCharsets.UTF_8));
    }
}

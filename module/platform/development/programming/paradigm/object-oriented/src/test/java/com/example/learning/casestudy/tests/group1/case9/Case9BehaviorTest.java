package com.example.learning.casestudy.tests.group1.case9;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.learning.casestudy.service.Group1Service;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

class Case9BehaviorTest {
    private final Group1Service service = new Group1Service();

    @Test
    void missingInvalidAndTooManyArgumentsRetainTheOriginalExceptions() {
        assertEquals("use: cpu|allocation", assertThrows(IllegalArgumentException.class,
                () -> service.executeCase9(new String[0])).getMessage());
        assertEquals("use: cpu|allocation", assertThrows(IllegalArgumentException.class,
                () -> service.executeCase9(new String[] {"cpu", "allocation"})).getMessage());
        assertEquals("invalid", assertThrows(IllegalArgumentException.class,
                () -> service.executeCase9(new String[] {"invalid"})).getMessage());
        assertEquals("", assertThrows(IllegalArgumentException.class,
                () -> service.executeCase9(new String[] {""})).getMessage());
    }

    @Test
    @ResourceLock("SYSTEM_OUT")
    void cpuAndAllocationModesPrintTheirOriginalResultsTwice() {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream captured = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            for (int repeat = 0; repeat < 2; repeat++) {
                assertDoesNotThrow(() -> service.executeCase9(new String[] {"cpu"}));
                assertDoesNotThrow(() -> service.executeCase9(new String[] {"allocation"}));
            }
        } finally {
            System.setOut(original);
        }
        String[] lines = bytes.toString(StandardCharsets.UTF_8).split("\\R");
        assertEquals(4, lines.length);
        assertEquals("workload completed; result=1439981869", lines[0]);
        assertEquals("workload completed; result=2048000000", lines[1]);
        assertEquals(lines[0], lines[2]);
        assertEquals(lines[1], lines[3]);
    }
}

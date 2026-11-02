package com.example.learning.casestudy.tests.group2.case14;

import com.example.learning.casestudy.service.Group2Service;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Case14Test {
    @Test
    void originalMainMatches() {
        PrintStream stdout = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream target = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(target);
            new Group2Service().executeCase14();
        } finally {
            System.setOut(stdout);
        }
        assertEquals("PASS: DTO-to-domain boundary enforces invariant\n", bytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }
}

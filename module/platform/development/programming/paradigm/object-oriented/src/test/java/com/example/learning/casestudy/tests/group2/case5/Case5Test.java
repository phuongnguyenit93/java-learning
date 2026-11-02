package com.example.learning.casestudy.tests.group2.case5;

import com.example.learning.casestudy.model.group2.case5.UnsafeConfig;
import com.example.learning.casestudy.runtime.CaseStudyJvmIsolation;
import com.example.learning.casestudy.service.Group2Service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Case5Test {
    @Test
    void childJvmsIsolateClassInitializationAndSystemProperties() {
        String prior = System.getProperty("demo.case5.url");
        System.setProperty("demo.case5.url", "parent-sentinel");
        try {
            Group2Service group = new Group2Service();
            group.executeCase5();
            group.executeCase5();
            assertEquals("parent-sentinel", System.getProperty("demo.case5.url"));
            assertEquals("parent-sentinel", UnsafeConfig.URL);
        } finally {
            if (prior == null) System.clearProperty("demo.case5.url");
            else System.setProperty("demo.case5.url", prior);
        }
    }

    @Test
    void failedChildProcessCannotReportSuccess() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> CaseStudyJvmIsolation.execute(Group2Service.class, "executeCase999"));
        assertTrue(failure.getMessage().contains("failed (exit "));
    }
}

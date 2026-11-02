package com.example.learning.casestudy.tests.group2.case9;

import com.example.learning.casestudy.application.group2.case9.BadFactory;
import com.example.learning.casestudy.service.Group2Service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Case9Test {
    @Test
    void childJvmsDoNotLeakMutableSingletonToParent() {
        assertEquals("light", BadFactory.SINGLETON.theme);
        Group2Service group = new Group2Service();
        group.executeCase9();
        group.executeCase9();
        assertEquals("light", BadFactory.SINGLETON.theme);
    }
}

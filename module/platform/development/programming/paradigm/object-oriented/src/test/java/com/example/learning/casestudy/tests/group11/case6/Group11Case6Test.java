package com.example.learning.casestudy.tests.group11.case6;

import com.example.learning.casestudy.service.Group11Service;
import com.example.learning.casestudy.domain.group11.case6.Node;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Group11Case6Test {
    @Test
    void originalMainBodyExecutes() {
        new Group11Service().executeCase6();
    }

    @Test
    void verifiesSourceBehaviorWithJUnitAssertions() {
        Node root = new Node("root");
        Node child = new Node("child");
        root.attach(child);
        String a = root.toString();
        String b = child.toString();
        assertTrue(a.contains("childCount=1"));
        assertTrue(b.contains("parentId=root"));
        assertTrue(a.length() < 100 && b.length() < 100);
        assertFalse(a.contains("Node{id='child'"));
    }
}

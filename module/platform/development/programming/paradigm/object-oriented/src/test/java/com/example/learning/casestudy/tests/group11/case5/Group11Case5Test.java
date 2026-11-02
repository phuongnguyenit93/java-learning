package com.example.learning.casestudy.tests.group11.case5;

import com.example.learning.casestudy.service.Group11Service;
import com.example.learning.casestudy.domain.group11.case5.LineView;
import com.example.learning.casestudy.domain.group11.case5.OrderSnapshot;
import com.example.learning.casestudy.model.group11.case5.MutableLine;
import com.example.learning.casestudy.utils.group11.case5.Demo5Utils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Group11Case5Test {
    @Test
    void originalMainBodyExecutes() {
        new Group11Service().executeCase5();
    }

    @Test
    void verifiesSourceBehaviorWithJUnitAssertions() {
        MutableLine original = new MutableLine("A", 2);
        List<MutableLine> source = new ArrayList<>(List.of(original));
        OrderSnapshot historical = Demo5Utils.fromMutable(source);
        original.quantity = 99;
        source.add(new MutableLine("B", 1));
        assertEquals(List.of(new LineView("A", 2)), historical.lines());
        assertThrows(UnsupportedOperationException.class,
        () -> historical.lines().add(new LineView("BAD", 1)));
        List<LineView> views = new ArrayList<>(List.of(new LineView("X", 3)));
        OrderSnapshot direct = new OrderSnapshot(views);
        views.clear();
        assertEquals(List.of(new LineView("X", 3)), direct.lines());
    }
}

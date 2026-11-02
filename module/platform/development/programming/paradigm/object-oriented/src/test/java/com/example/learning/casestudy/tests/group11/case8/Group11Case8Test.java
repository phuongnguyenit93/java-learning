package com.example.learning.casestudy.tests.group11.case8;

import com.example.learning.casestudy.service.Group11Service;
import com.example.learning.casestudy.domain.group11.case8.Address;
import com.example.learning.casestudy.domain.group11.case8.Profile;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Group11Case8Test {
    @Test
    void originalMainBodyExecutes() {
        new Group11Service().executeCase8();
    }

    @Test
    void verifiesSourceBehaviorWithJUnitAssertions() {
        Address external = new Address("Hanoi");
        List<Address> initial = new ArrayList<>(List.of(external));
        Profile original = new Profile(initial);
        Profile copied = new Profile(original);
        copied.relocateFirst("Danang");
        assertEquals("Hanoi", original.firstCity());
        assertEquals("Danang", copied.firstCity());
        external.city = "Hue";
        initial.clear();
        assertEquals("Hanoi", original.firstCity());
        assertEquals("Danang", copied.firstCity());
    }
}

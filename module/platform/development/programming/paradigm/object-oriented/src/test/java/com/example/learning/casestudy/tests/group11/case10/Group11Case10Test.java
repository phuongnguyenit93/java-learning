package com.example.learning.casestudy.tests.group11.case10;

import com.example.learning.casestudy.service.Group11Service;
import com.example.learning.casestudy.domain.group11.case10.Animal10;
import com.example.learning.casestudy.domain.group11.case10.Cat10;
import com.example.learning.casestudy.domain.group11.case10.Dog10;
import com.example.learning.casestudy.utils.group11.case10.Demo10Utils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Group11Case10Test {
    @Test
    void originalMainBodyExecutes() {
        new Group11Service().executeCase10();
    }

    @Test
    void verifiesSourceBehaviorWithJUnitAssertions() {
        List<Dog10> dogs = new ArrayList<>(List.of(new Dog10()));
        assertEquals("woof;", Demo10Utils.sounds(dogs));
        List<Animal10> writable = new ArrayList<>(dogs);
        writable.add(new Cat10());
        assertEquals("woof;meow;", Demo10Utils.sounds(writable));
        assertEquals(1, dogs.size());
        assertEquals("woof", dogs.get(0).sound());
    }
}

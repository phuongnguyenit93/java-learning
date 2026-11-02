package com.example.learning.casestudy.tests.group11.case11;

import com.example.learning.casestudy.service.Group11Service;
import com.example.learning.casestudy.domain.group11.case11.Animal11;
import com.example.learning.casestudy.domain.group11.case11.Cat11;
import com.example.learning.casestudy.domain.group11.case11.Dog11;
import com.example.learning.casestudy.utils.group11.case11.Demo11Utils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Group11Case11Test {
    @Test
    void originalMainBodyExecutes() {
        new Group11Service().executeCase11();
    }

    @Test
    void verifiesSourceBehaviorWithJUnitAssertions() {
        List<Dog11> dogs = List.of(new Dog11("Rex"), new Dog11("Luna"));
        List<Animal11> animals = new ArrayList<>();
        List<Object> audit = new ArrayList<>();
        Demo11Utils.copyAnimals(dogs, animals);
        Demo11Utils.copyAnimals(dogs, audit);
        assertEquals(2, animals.size());
        assertEquals("Rex", animals.get(0).name());
        assertEquals(2, audit.size());
        assertEquals(new Dog11("Luna"), audit.get(1));
        animals.add(new Cat11("Mimi"));
        assertEquals(2, dogs.size());
    }
}

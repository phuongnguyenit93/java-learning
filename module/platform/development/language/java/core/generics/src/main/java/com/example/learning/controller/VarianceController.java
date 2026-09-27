package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/generics/variance")
public class VarianceController {

    @GetMapping("/invariance")
    public Map<String, Object> invariance() {
        List<Dog> dogs = new ArrayList<>();
        dogs.add(new Dog("Milo"));

        List<? extends Animal> readableAnimals = dogs;
        Animal first = readableAnimals.get(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dogIsAnimal", first instanceof Animal);
        result.put("exactAssignmentAllowed", false);
        result.put("invalidAssignment", "List<Animal> animals = dogs");
        result.put("safeReadView", "List<? extends Animal>");
        result.put("firstAnimalName", first.name());
        result.put("reason", "allowing List<Dog> as List<Animal> would permit inserting a non-Dog Animal");
        return result;
    }

    private static class Animal {
        private final String name;

        private Animal(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }
    }

    private static final class Dog extends Animal {
        private Dog(String name) {
            super(name);
        }
    }
}

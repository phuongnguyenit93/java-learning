package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/oop/polymorphism")
public class PolymorphismController {
 interface Speaker { String speak(); }
 static final class Dog implements Speaker { public String speak(){return "woof";} }
 static final class Cat implements Speaker { public String speak(){return "meow";} }
 @GetMapping("/dispatch") public Map<String,Object> dispatch(){ Speaker first=new Dog(), second=new Cat(); return Map.of("declaredType","Speaker","firstRuntime",first.getClass().getSimpleName(),"firstResult",first.speak(),"secondRuntime",second.getClass().getSimpleName(),"secondResult",second.speak()); }
}

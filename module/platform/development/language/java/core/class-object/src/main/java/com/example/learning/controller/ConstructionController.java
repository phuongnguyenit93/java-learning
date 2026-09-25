package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/class-object/construction")
public class ConstructionController {
 static class Parent { int observed; Parent(){ hook(); } void hook(){} }
 static final class Child extends Parent { int value=42; @Override void hook(){ observed=value; } }
 @GetMapping("/override-risk") public Map<String,Object> constructorDispatchRisk(){ Child child=new Child(); return Map.of("valueAfterConstruction",child.value,"valueObservedDuringSuperConstructor",child.observed,"conclusion","virtual dispatch reached Child before Child field initializer"); }
}

package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/class-object/initialization")
public class InitializationController {
 private static final List<String> STATIC_TRACE=new ArrayList<>();
 static class Parent { static { STATIC_TRACE.add("parent-static"); } Parent(List<String> t){t.add("parent-constructor");} }
 static final class Child extends Parent { static { STATIC_TRACE.add("child-static"); } private int field=initField(); private final List<String> trace; { /* instance block runs after field initializer */ } Child(List<String> t){ super(t); this.trace=t; t.add("child-constructor"); } private int initField(){ return 42; } }
 @GetMapping("/order") public Map<String,Object> traceOrder(){ List<String> t=new ArrayList<>(); Child c=new Child(t); t.add(t.size()-1,"child-field-initializer(value="+c.field+")"); return Map.of("staticInitialization",List.copyOf(STATIC_TRACE),"instanceConstruction",t); }
}

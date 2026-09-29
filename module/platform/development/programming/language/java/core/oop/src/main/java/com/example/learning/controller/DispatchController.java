package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/oop/dispatch")
public class DispatchController {
 static class Parent { String field="parent-field"; String call(){return "Parent.call";} static String staticCall(){return "Parent.staticCall";} }
 static final class Child extends Parent { String field="child-field"; @Override String call(){return "Child.call";} static String staticCall(){return "Child.staticCall";} }
 private String select(Parent x){return "select(Parent)";} private String select(Child x){return "select(Child)";}
 @GetMapping("/overload-vs-override") public Map<String,Object> overloadVsOverride(){ Parent x=new Child(); return Map.of("overload",select(x),"override",x.call(),"runtime",x.getClass().getSimpleName()); }
 @GetMapping("/dispatch-vs-hiding") public Map<String,Object> dispatchVsHiding(){ Parent x=new Child(); return Map.of("instanceMethod",x.call(),"staticMethod",x.staticCall(),"field",x.field,"runtime",x.getClass().getSimpleName()); }
}

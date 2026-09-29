package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/abstract-interface/default-method")
public class DefaultMethodController {
 interface Greeting { default String greet(){return "hello-default";} }
 static final class DefaultGreeting implements Greeting {}
 interface Left { default String value(){return "left";} } interface Right { default String value(){return "right";} }
 static final class Both implements Left,Right { public String value(){return Left.super.value()+"+"+Right.super.value();} }
 @GetMapping("/dispatch") public Map<String,Object> dispatch(){ Greeting g=new DefaultGreeting(); return Map.of("result",g.greet(),"runtime",g.getClass().getSimpleName()); }
 @GetMapping("/resolve-conflict") public Map<String,Object> resolveConflict(){ return Map.of("resolved",new Both().value(),"rule","class must explicitly resolve unrelated default conflict"); }
}

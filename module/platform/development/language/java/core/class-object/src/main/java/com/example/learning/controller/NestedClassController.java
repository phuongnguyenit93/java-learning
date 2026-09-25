package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/class-object/nested")
public class NestedClassController {
 @GetMapping("/capture") public Map<String,Object> capture(){ int base=40; class Local { int answer(){return base+2;} } Local local=new Local(); return Map.of("capturedValue",base,"computed",local.answer(),"rule","captured local is final or effectively final"); }
}

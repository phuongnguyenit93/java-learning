package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/abstract-interface/abstract-class")
public class AbstractClassController {
 static abstract class Processor { private final String prefix; Processor(String p){prefix=p;} final String process(String value){return prefix+transform(value);} abstract String transform(String value); }
 static final class UpperProcessor extends Processor { UpperProcessor(){super("result:");} String transform(String v){return v.toUpperCase(Locale.ROOT);} }
 @GetMapping("/template") public Map<String,Object> templateBehavior(){ Processor p=new UpperProcessor(); return Map.of("declaredType","Processor","runtimeType",p.getClass().getSimpleName(),"result",p.process("java")); }
}

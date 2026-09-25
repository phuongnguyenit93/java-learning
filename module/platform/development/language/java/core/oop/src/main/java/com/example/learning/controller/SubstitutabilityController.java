package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/oop/substitutability")
public class SubstitutabilityController {
 interface Formatter { String format(String value); }
 static final class Upper implements Formatter { public String format(String v){return v.toUpperCase(Locale.ROOT);} }
 static final class Lower implements Formatter { public String format(String v){return v.toLowerCase(Locale.ROOT);} }
 private Map<String,String> run(Formatter f){return Map.of("implementation",f.getClass().getSimpleName(),"result",f.format("Java"));}
 @GetMapping("/implementations") public List<Map<String,String>> substituteImplementations(){ return List.of(run(new Upper()),run(new Lower())); }
}

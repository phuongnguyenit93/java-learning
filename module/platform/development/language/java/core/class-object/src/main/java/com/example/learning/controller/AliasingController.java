package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/class-object/aliasing")
public class AliasingController {
 @GetMapping("/mutation") public Map<String,Object> mutateAlias(){ List<String> first=new ArrayList<>(); List<String> second=first; second.add("visible-through-both"); return Map.of("sameIdentity",first==second,"first",first,"second",second); }
}

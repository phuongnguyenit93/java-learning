package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/abstract-interface/interface")
public class InterfaceController {
 interface Named { String name(); } interface Enabled { boolean enabled(); }
 static final class Feature implements Named, Enabled { public String name(){return "search";} public boolean enabled(){return true;} }
 @GetMapping("/multiple-contracts") public Map<String,Object> multipleContracts(){ Feature f=new Feature(); Named n=f; Enabled e=f; return Map.of("sameObject",n==e,"name",n.name(),"enabled",e.enabled(),"runtimeType",f.getClass().getSimpleName()); }
}

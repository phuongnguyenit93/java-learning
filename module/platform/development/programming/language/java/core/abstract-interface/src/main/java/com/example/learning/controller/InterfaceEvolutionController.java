package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/abstract-interface/evolution")
public class InterfaceEvolutionController {
 interface Versioned { String id(); default String display(){return "id="+id();} }
 static final class OldStyleImplementation implements Versioned { public String id(){return "legacy";} }
 @GetMapping("/default-compatibility") public Map<String,Object> defaultCompatibility(){ Versioned v=new OldStyleImplementation(); return Map.of("implementationOnlyDefines","id()","newDefaultMethodResult",v.display(),"runtimeType",v.getClass().getSimpleName()); }
}

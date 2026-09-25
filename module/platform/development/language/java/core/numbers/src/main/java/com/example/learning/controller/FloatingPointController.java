package com.example.learning.controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/java/core/numbers/floating")
public class FloatingPointController {
 @GetMapping("/precision") public Map<String,Object> precision(){ double v=0.1d+0.2d; return Map.of("computed",v,"expectedDecimal",0.3d,"exactEqual",v==0.3d,"delta",Math.abs(v-0.3d)); }
 @GetMapping("/special-values") public Map<String,Object> specialValues(){ return Map.of("nanEqualsItself",Double.NaN==Double.NaN,"isNaN",Double.isNaN(0.0d/0.0d),"positiveInfinity",1.0d/0.0d,"zeroEqualsNegativeZero",0.0d==-0.0d); }
}

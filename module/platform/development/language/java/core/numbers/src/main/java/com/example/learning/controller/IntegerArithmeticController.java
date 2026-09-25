package com.example.learning.controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/java/core/numbers/integer")
public class IntegerArithmeticController {
 @GetMapping("/overflow") public Map<String,Object> overflow(){
   int wrapped=Integer.MAX_VALUE+1; Map<String,Object> r=new LinkedHashMap<>(); r.put("max",Integer.MAX_VALUE); r.put("wrapped",wrapped);
   try { Math.addExact(Integer.MAX_VALUE,1); } catch(ArithmeticException e){ r.put("checkedFailure",e.getClass().getSimpleName()); }
   return r;
 }
}

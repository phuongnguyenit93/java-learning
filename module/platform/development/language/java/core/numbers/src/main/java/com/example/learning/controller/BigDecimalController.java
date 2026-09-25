package com.example.learning.controller;
import org.springframework.web.bind.annotation.*;
import java.math.*; import java.util.*;
@RestController @RequestMapping("/java/core/numbers/big-decimal")
public class BigDecimalController {
 @GetMapping("/construction") public Map<String,Object> construction(){ return Map.of("fromString",new BigDecimal("0.1").toString(),"fromValueOf",BigDecimal.valueOf(0.1d).toString(),"fromDouble",new BigDecimal(0.1d).toString()); }
 @GetMapping("/comparison") public Map<String,Object> comparison(){ BigDecimal a=new BigDecimal("1.0"), b=new BigDecimal("1.00"); return Map.of("leftScale",a.scale(),"rightScale",b.scale(),"equals",a.equals(b),"compareTo",a.compareTo(b)); }
 @GetMapping("/division-rounding") public Map<String,Object> divisionAndRounding(){ Map<String,Object> r=new LinkedHashMap<>(); try{ BigDecimal.ONE.divide(new BigDecimal("3")); }catch(ArithmeticException e){r.put("exactDivisionFailure",e.getClass().getSimpleName());} r.put("rounded",BigDecimal.ONE.divide(new BigDecimal("3"),4,RoundingMode.HALF_UP).toString()); return r; }
}

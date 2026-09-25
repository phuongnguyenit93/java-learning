package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/class-object/enum")
public class EnumController {
 interface PriceRule { int apply(int cents); }
 enum Tier implements PriceRule { STANDARD { public int apply(int c){return c;} }, DISCOUNT { public int apply(int c){return c*90/100;} } }
 @GetMapping("/behavior") public Map<String,Object> enumBehavior(){ return Map.of("standard",Tier.STANDARD.apply(1000),"discount",Tier.DISCOUNT.apply(1000),"identity",Tier.valueOf("DISCOUNT")==Tier.DISCOUNT,"declarationOrder",Arrays.stream(Tier.values()).map(Enum::name).toList()); }
}

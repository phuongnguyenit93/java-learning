package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/oop/composition")
public class CompositionController {
 interface Pricing { int price(int base); }
 static final class Regular implements Pricing { public int price(int base){return base;} }
 static final class Discount implements Pricing { public int price(int base){return base*80/100;} }
 static final class Checkout { private final Pricing pricing; Checkout(Pricing p){pricing=p;} int total(int base){return pricing.price(base);} }
 @GetMapping("/strategy-swap") public Map<String,Object> strategySwap(){ return Map.of("regular",new Checkout(new Regular()).total(1000),"discount",new Checkout(new Discount()).total(1000),"consumerType","Checkout unchanged"); }
}

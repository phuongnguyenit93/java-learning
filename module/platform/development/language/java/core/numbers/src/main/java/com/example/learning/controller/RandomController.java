package com.example.learning.controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/java/core/numbers/random")
public class RandomController {
 @GetMapping("/seeded-sequence") public Map<String,Object> seededSequence(){ Random a=new Random(42L), b=new Random(42L); List<Integer> x=List.of(a.nextInt(1000),a.nextInt(1000),a.nextInt(1000)); List<Integer> y=List.of(b.nextInt(1000),b.nextInt(1000),b.nextInt(1000)); return Map.of("first",x,"sameSeed",y,"reproducible",x.equals(y),"securityBoundary","use SecureRandom when unpredictability is required"); }
}

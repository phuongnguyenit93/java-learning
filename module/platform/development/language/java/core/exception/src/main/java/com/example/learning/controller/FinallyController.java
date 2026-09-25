package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/exception/finally")
public class FinallyController {
 @GetMapping("/return-interaction") public Map<String,Object> returnInteraction(){ return Map.of("normalReturnWithFinally",safeReturn(),"returnInsideFinally",dangerousReturn(),"warning","a return in finally replaces the pending return/exception"); }
 private int safeReturn(){try{return 1;}finally{int ignored=0;}}
 @SuppressWarnings("finally") private int dangerousReturn(){try{return 1;}finally{return 2;}}
}

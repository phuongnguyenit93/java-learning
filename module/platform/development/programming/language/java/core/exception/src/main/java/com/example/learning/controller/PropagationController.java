package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/exception/propagation")
public class PropagationController {
 @GetMapping("/stack") public Map<String,Object> propagate(){ try{levelOne();return Map.of();}catch(IllegalStateException e){return Map.of("type",e.getClass().getSimpleName(),"message",e.getMessage(),"topFrames",Arrays.stream(e.getStackTrace()).limit(3).map(StackTraceElement::getMethodName).toList());} }
 @GetMapping("/wrap-cause") public Map<String,Object> wrapCause(){ try{load();return Map.of();}catch(OrderReadException e){return Map.of("wrapper",e.getClass().getSimpleName(),"message",e.getMessage(),"cause",e.getCause().getClass().getSimpleName(),"causeMessage",e.getCause().getMessage());} }
 private void levelOne(){levelTwo();} private void levelTwo(){throw new IllegalStateException("boom");}
 private void load(){try{throw new IllegalArgumentException("low-level");}catch(IllegalArgumentException e){throw new OrderReadException("cannot read order",e);}}
 static final class OrderReadException extends RuntimeException { OrderReadException(String m,Throwable c){super(m,c);} }
}

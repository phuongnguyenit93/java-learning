package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/exception/design")
public class ExceptionDesignController {
 @GetMapping("/translate") public Map<String,Object> translateBoundary(){ try{repository();return Map.of();}catch(OrderServiceException e){return Map.of("translatedType",e.getClass().getSimpleName(),"message",e.getMessage(),"preservedCause",e.getCause().getClass().getSimpleName());} }
 private void repository(){try{throw new IllegalStateException("storage unavailable");}catch(IllegalStateException e){throw new OrderServiceException("cannot load order 42",e);}}
 static final class OrderServiceException extends RuntimeException { OrderServiceException(String m,Throwable c){super(m,c);} }
}

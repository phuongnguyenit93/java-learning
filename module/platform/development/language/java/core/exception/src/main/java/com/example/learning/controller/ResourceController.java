package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/exception/resource")
public class ResourceController {
 static final class DemoResource implements AutoCloseable { private final String name; private final List<String> trace; private final boolean fail; DemoResource(String n,List<String> t,boolean f){name=n;trace=t;fail=f;trace.add("open-"+name);} public void close(){trace.add("close-"+name);if(fail)throw new IllegalStateException("close-"+name+"-failed");} }
 @GetMapping("/close-order") public Map<String,Object> closeOrder(){ List<String> trace=new ArrayList<>(); try(DemoResource a=new DemoResource("A",trace,false);DemoResource b=new DemoResource("B",trace,false)){trace.add("body");} return Map.of("trace",trace); }
 @GetMapping("/suppressed") public Map<String,Object> suppressed(){ List<String> trace=new ArrayList<>(); try(DemoResource r=new DemoResource("R",trace,true)){trace.add("body-fails");throw new IllegalArgumentException("primary");}catch(Exception e){return Map.of("primary",e.getClass().getSimpleName(),"message",e.getMessage(),"suppressed",Arrays.stream(e.getSuppressed()).map(x->x.getClass().getSimpleName()+":"+x.getMessage()).toList(),"trace",trace);} }
}

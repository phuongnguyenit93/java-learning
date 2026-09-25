package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/class-object/immutability")
public class ImmutabilityController {
 static final class SafeNames { private final List<String> names; SafeNames(List<String> input){names=List.copyOf(input);} List<String> names(){return names;} }
 @GetMapping("/defensive-copy") public Map<String,Object> defensiveCopy(){ List<String> input=new ArrayList<>(List.of("A")); SafeNames value=new SafeNames(input); input.add("B"); boolean outputMutable; try{value.names().add("C"); outputMutable=true;}catch(UnsupportedOperationException e){outputMutable=false;} return Map.of("callerInputAfterMutation",input,"storedSnapshot",value.names(),"returnedViewMutable",outputMutable); }
}

package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/object-contract/equality")
public class EqualityController {
 static final class Value { private final int id; Value(int id){this.id=id;} @Override public boolean equals(Object o){return o instanceof Value v && id==v.id;} @Override public int hashCode(){return Integer.hashCode(id);} }
 @GetMapping("/identity-vs-equality") public Map<String,Object> identityVsEquality(){ Value a=new Value(7), b=new Value(7); return Map.of("sameReference",a==b,"logicalEquals",a.equals(b),"sameHash",a.hashCode()==b.hashCode()); }
}

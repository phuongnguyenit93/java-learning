package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/object-contract/hash")
public class HashContractController {
 static final class Broken { final int id; Broken(int id){this.id=id;} public boolean equals(Object o){return o instanceof Broken b && id==b.id;} }
 static final class MutableKey { int id; MutableKey(int id){this.id=id;} public boolean equals(Object o){return o instanceof MutableKey k&&id==k.id;} public int hashCode(){return Integer.hashCode(id);} }
 @GetMapping("/broken-contract") public Map<String,Object> brokenHashSetLookup(){ Set<Broken> set=new HashSet<>(); Broken stored=new Broken(1), equal=new Broken(1); set.add(stored); return Map.of("equals",stored.equals(equal),"storedHash",stored.hashCode(),"equalHash",equal.hashCode(),"containsEqualObject",set.contains(equal)); }
 @GetMapping("/mutable-key") public Map<String,Object> mutableKey(){ MutableKey key=new MutableKey(1); Map<MutableKey,String> map=new HashMap<>(); map.put(key,"value"); boolean before=map.containsKey(key); key.id=2; boolean after=map.containsKey(key); return Map.of("containsBeforeMutation",before,"containsAfterMutation",after,"currentHash",key.hashCode()); }
}

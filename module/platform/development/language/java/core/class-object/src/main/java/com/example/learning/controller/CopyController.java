package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/class-object/copy")
public class CopyController {
 static final class Person { final String name; final List<String> tags; Person(String n,List<String> t){name=n;tags=t;} Person shallow(){return new Person(name,tags);} Person deep(){return new Person(name,new ArrayList<>(tags));} }
 @GetMapping("/shallow-vs-deep") public Map<String,Object> shallowVsDeep(){ Person source=new Person("Ada",new ArrayList<>(List.of("java"))); Person shallow=source.shallow(), deep=source.deep(); source.tags.add("mutable"); return Map.of("sourceTags",source.tags,"shallowTags",shallow.tags,"deepTags",deep.tags,"shallowSharesNested",source.tags==shallow.tags,"deepSharesNested",source.tags==deep.tags); }
}

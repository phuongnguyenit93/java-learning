package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/string/concat")
public class StringConcatController {
 @GetMapping("/construction") public Map<String,Object> constructionTrace(){ String s=""; List<Integer> ids=new ArrayList<>(); for(int i=0;i<4;i++){s=s+i;ids.add(System.identityHashCode(s));} StringBuilder b=new StringBuilder(); for(int i=0;i<4;i++)b.append(i); return Map.of("concatResult",s,"concatIntermediateIdentities",ids,"builderResult",b.toString(),"builderIdentity",System.identityHashCode(b)); }
}

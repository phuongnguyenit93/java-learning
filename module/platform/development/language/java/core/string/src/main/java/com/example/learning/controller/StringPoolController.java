package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/string/pool")
public class StringPoolController {
 @GetMapping("/identity") public Map<String,Object> identity(){ String literalA="java",literalB="ja"+"va",runtime=new String("java"),interned=runtime.intern(); return Map.of("literalIdentity",literalA==literalB,"newStringIdentity",literalA==runtime,"contentEquals",literalA.equals(runtime),"internIdentity",literalA==interned); }
}

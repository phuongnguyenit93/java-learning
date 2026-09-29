package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*; import java.util.regex.*;
@RestController @RequestMapping("/java/core/string/regex")
public class RegexController {
 @GetMapping("/groups") public Map<String,Object> groups(){ Matcher m=Pattern.compile("(?<user>[a-z]+)@(?<domain>[a-z.]+)").matcher("ada@example.com"); boolean found=m.matches(); return Map.of("matched",found,"user",found?m.group("user"):"","domain",found?m.group("domain"):""); }
 @GetMapping("/backtracking") public Map<String,Object> backtracking(){ String input="a".repeat(18)+"!"; Pattern risky=Pattern.compile("(a+)+$"); long start=System.nanoTime(); boolean matched=risky.matcher(input).matches(); long elapsed=System.nanoTime()-start; return Map.of("inputLength",input.length(),"matched",matched,"boundedSample",true,"elapsedNanos",elapsed,"warning","nested ambiguous quantifiers can backtrack heavily; timing is illustrative only"); }
}

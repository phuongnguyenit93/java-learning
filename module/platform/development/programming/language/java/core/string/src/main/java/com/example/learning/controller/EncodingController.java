package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.nio.charset.*; import java.util.*;
@RestController @RequestMapping("/java/core/string/encoding")
public class EncodingController {
 @GetMapping("/round-trip") public Map<String,Object> roundTrip(){ String text="Xin chào ☕"; byte[] bytes=text.getBytes(StandardCharsets.UTF_8); return Map.of("text",text,"utf8Bytes",bytes.length,"decoded",new String(bytes,StandardCharsets.UTF_8),"roundTrip",text.equals(new String(bytes,StandardCharsets.UTF_8))); }
 @GetMapping("/wrong-charset") public Map<String,Object> wrongCharset(){ String text="café"; byte[] utf8=text.getBytes(StandardCharsets.UTF_8); String wrong=new String(utf8,StandardCharsets.ISO_8859_1); return Map.of("original",text,"decodedAsLatin1",wrong,"same",text.equals(wrong)); }
}

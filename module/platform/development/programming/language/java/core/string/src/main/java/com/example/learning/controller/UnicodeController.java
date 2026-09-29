package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.text.Normalizer; import java.util.*;
@RestController @RequestMapping("/java/core/string/unicode")
public class UnicodeController {
 @GetMapping("/char-vs-code-point") public Map<String,Object> charVsCodePoint(){ String value="A😀"; return Map.of("text",value,"utf16Length",value.length(),"codePointCount",value.codePointCount(0,value.length()),"codePoints",value.codePoints().mapToObj(cp->String.format("U+%04X",cp)).toList()); }
 @GetMapping("/normalization") public Map<String,Object> normalization(){ String composed="é", decomposed="e\u0301"; String nfcA=Normalizer.normalize(composed,Normalizer.Form.NFC), nfcB=Normalizer.normalize(decomposed,Normalizer.Form.NFC); return Map.of("visualA",composed,"visualB",decomposed,"equalsBefore",composed.equals(decomposed),"codePointsA",composed.codePoints().mapToObj(cp->String.format("U+%04X",cp)).toList(),"codePointsB",decomposed.codePoints().mapToObj(cp->String.format("U+%04X",cp)).toList(),"equalsAfterNfc",nfcA.equals(nfcB)); }
}

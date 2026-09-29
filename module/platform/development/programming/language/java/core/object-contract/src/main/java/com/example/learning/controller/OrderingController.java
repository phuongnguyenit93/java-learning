package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.math.BigDecimal; import java.util.*;
@RestController @RequestMapping("/java/core/object-contract/ordering")
public class OrderingController {
 record Person(String name,int age) implements Comparable<Person>{public int compareTo(Person o){return Integer.compare(age,o.age);}}
 @GetMapping("/natural-vs-custom") public Map<String,Object> naturalVsCustom(){ List<Person> source=List.of(new Person("Bob",30),new Person("Ada",40)); List<Person> natural=new ArrayList<>(source); Collections.sort(natural); List<Person> byName=new ArrayList<>(source); byName.sort(Comparator.comparing(Person::name)); return Map.of("naturalByAge",natural,"customByName",byName); }
 @GetMapping("/compareto-equals") public Map<String,Object> compareToEquals(){ BigDecimal a=new BigDecimal("1.0"),b=new BigDecimal("1.00"); Set<BigDecimal> hash=new HashSet<>(List.of(a,b)); Set<BigDecimal> tree=new TreeSet<>(List.of(a,b)); return Map.of("equals",a.equals(b),"compareTo",a.compareTo(b),"hashSetSize",hash.size(),"treeSetSize",tree.size()); }
}

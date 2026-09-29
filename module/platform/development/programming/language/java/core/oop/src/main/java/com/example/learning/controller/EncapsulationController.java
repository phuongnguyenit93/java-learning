package com.example.learning.controller;
import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/java/core/oop/encapsulation")
public class EncapsulationController {
 static final class Account { private int balance=100; int balance(){return balance;} void withdraw(int amount){ if(amount<=0||amount>balance) throw new IllegalArgumentException("invalid withdrawal"); balance-=amount; } }
 @GetMapping("/invariant") public Map<String,Object> invariantProtection(){ Account a=new Account(); a.withdraw(30); String failure="none"; try{a.withdraw(100);}catch(IllegalArgumentException e){failure=e.getMessage();} return Map.of("balance",a.balance(),"rejected",failure,"invariant","balance never becomes negative"); }
}

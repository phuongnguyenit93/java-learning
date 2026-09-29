package com.example.learning.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.example.learning.service.BankProducer;

@RestController
public class BankController {
    @Autowired
    BankProducer bankProducer;

    @GetMapping("/deposit/{money}")
    public void depositMoney(@PathVariable("money") String money) {
        bankProducer.depositMoney(money);
    }

    @GetMapping("withdrawal/{money}")
    public void withdrawMoney(@PathVariable("money") String money) {
        bankProducer.withdrawMoney(money);
    }
}

package com.example.learning.controller;

import com.example.learning.entity.UserRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RecordController {
    @GetMapping("/record-example")
    public void getRecordEntity () {
        UserRecord user = new UserRecord("Phuong", 32);
        System.out.println(user.age()); // Same as getAge();
        System.out.println(user.name()); // Same as getName();
    }
}

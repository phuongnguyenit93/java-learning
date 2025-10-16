package com.example.learning.controller;

import com.example.learning.entity.InstanceLearning;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AbstractInterfaceController {
    InstanceLearning instance = new InstanceLearning("Instance Learning 1");

    @GetMapping("abstract")
    public void getAbstractExample() {
        instance.absA1();
        instance.absA2();
        instance.absA3();
    }

    @GetMapping("interface")
    public void getInstanceExample() {
        instance.intfA1();
        instance.intfA2();
        instance.intfA3();
        instance.intfB1();
        instance.intfB2();
        instance.intfB3();
    }
}

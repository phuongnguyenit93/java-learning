package com.example.learning.controller;

import com.example.learning.service.BeanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class BeanController {
    @Autowired
    BeanService beanService;

    @GetMapping("byBean")
    public void getSchoolByBean() {
        beanService.getSchoolByBean();
    }

    @GetMapping("byComponent")
    public void getClassByComponent() {
        beanService.getClassByComponent();
    }
}

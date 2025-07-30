package com.example.learning.controller;

import com.example.learning.model.School;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class ProfileController {
    @Autowired
    School school;

    @GetMapping("info")
    public void getSchoolInfo(){
        System.out.println(school.getName());
        System.out.println(school.getClassName());
        System.out.println(school.getStudentName());
    }
}

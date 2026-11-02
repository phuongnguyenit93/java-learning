package com.example.learning.casestudy.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.learning.casestudy.service.Group12Service;

@RestController("caseStudyGroup12Controller")
@RequestMapping("/case-studies/group/12")
public class Group12Controller {
    private final Group12Service service;

    public Group12Controller(Group12Service service) {
        this.service = service;
    }

    @GetMapping("/case/1/execute")
    public void executeCase1() {
        service.executeCase1();
    }

    @GetMapping("/case/2/execute")
    public void executeCase2() {
        service.executeCase2();
    }

    @GetMapping("/case/3/execute")
    public void executeCase3() {
        service.executeCase3();
    }

    @GetMapping("/case/4/execute")
    public void executeCase4() {
        service.executeCase4();
    }

    @GetMapping("/case/5/execute")
    public void executeCase5() {
        service.executeCase5();
    }

    @GetMapping("/case/6/execute")
    public void executeCase6() {
        service.executeCase6();
    }

    @GetMapping("/case/7/execute")
    public void executeCase7() {
        service.executeCase7();
    }

    @GetMapping("/case/8/execute")
    public void executeCase8() {
        service.executeCase8();
    }
}

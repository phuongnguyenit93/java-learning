package com.example.learning.casestudy.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.learning.casestudy.service.Group8Service;

@RestController("caseStudyGroup8Controller")
@RequestMapping("/case-studies/group/8")
public class Group8Controller {
    private final Group8Service service;

    public Group8Controller(Group8Service service) {
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

    @GetMapping("/case/9/execute")
    public void executeCase9() {
        service.executeCase9();
    }

    @GetMapping("/case/10/execute")
    public void executeCase10() {
        service.executeCase10();
    }

    @GetMapping("/case/11/execute")
    public void executeCase11() {
        service.executeCase11();
    }

    @GetMapping("/case/12/execute")
    public void executeCase12() {
        service.executeCase12();
    }

    @GetMapping("/case/13/execute")
    public void executeCase13() {
        service.executeCase13();
    }
}

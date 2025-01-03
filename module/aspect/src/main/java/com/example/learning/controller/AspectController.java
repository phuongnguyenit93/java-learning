package com.example.learning.controller;

import com.example.learning.annotation.AspectAnnotation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@Tag(name = "Aspect")
public class AspectController {
    @GetMapping("/aspect1")
    @AspectAnnotation
    public void aspectExample1() {
        System.out.println("Running Aspect 1");
    }

    @GetMapping("/aspect2")
    public void aspectExample2() {
        System.out.println("Running Aspect 2");
    }

    @GetMapping("/aspect3")
    @AspectAnnotation
    public String aspectExample3() {
        System.out.println("Running Aspect 3");
        return "Running Completed";
    }

    @GetMapping("/aspect4")
    @AspectAnnotation
    public void aspectExample4() {
        System.out.println("Running Aspect 4");
        throw new RuntimeException("This is an error");
    }
}

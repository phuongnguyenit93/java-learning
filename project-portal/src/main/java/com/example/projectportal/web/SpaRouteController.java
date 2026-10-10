package com.example.projectportal.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaRouteController {

    @GetMapping({
            "/my-cv",
            "/my-cv/",
            "/learning",
            "/learning/",
            "/learning/{moduleId}",
            "/learning/{moduleId}/",
            "/learning/java/knowledge/{moduleId}",
            "/learning/java/knowledge/{moduleId}/",
            "/learning/java/knowledge",
            "/learning/java/knowledge/",
            "/learning/java/project",
            "/learning/java/project/",
            "/learning/java",
            "/learning/java/"
    })
    public String forwardSpaRoute() {
        return "forward:/index.html";
    }
}

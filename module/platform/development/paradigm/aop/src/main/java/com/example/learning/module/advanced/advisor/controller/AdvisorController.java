package com.example.learning.module.advanced.advisor.controller;

import com.example.learning.module.advanced.advisor.service.AdvisorExperimentService;
import com.example.learning.module.common.AopExperimentResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/aop/advanced/advisor")
public class AdvisorController {

    private final AdvisorExperimentService advisorExperimentService;

    public AdvisorController(AdvisorExperimentService advisorExperimentService) {
        this.advisorExperimentService = advisorExperimentService;
    }

    /**
     * README: readme/vi/menu/12.Advisor/Advisor.md#advisor-demo
     * Purpose: Chứng minh Advisor ghép Pointcut + Advice, đồng thời so sánh static và runtime MethodMatcher.
     */
    @GetMapping("/observe")
    public AopExperimentResponse observeAdvisor() {
        return AopExperimentResponse.of(
                advisorExperimentService.observeAdvisor(),
                List.of()
        );
    }
}

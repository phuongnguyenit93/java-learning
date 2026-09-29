package com.example.learning.module.advanced.proxyfactory.controller;

import com.example.learning.module.advanced.proxyfactory.service.ProgrammaticProxyService;
import com.example.learning.module.common.AopExperimentResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/aop/advanced/proxy-factory")
public class ProgrammaticProxyController {

    private final ProgrammaticProxyService programmaticProxyService;

    public ProgrammaticProxyController(ProgrammaticProxyService programmaticProxyService) {
        this.programmaticProxyService = programmaticProxyService;
    }

    /**
     * README: readme/vi/menu/11.ProxyFactory/ProxyFactory.md#proxy-factory-demo
     * Purpose: Tự tạo JDK proxy và CGLIB proxy bằng ProxyFactory với cùng một MethodInterceptor.
     */
    @GetMapping("/compare")
    public AopExperimentResponse compareProxyFactoryStrategies() {
        return AopExperimentResponse.of(
                programmaticProxyService.compareProxyStrategies(),
                List.of()
        );
    }
}

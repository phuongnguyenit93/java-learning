package com.example.learning.module.advanced.infrastructure.controller;

import com.example.learning.module.advanced.infrastructure.service.InfrastructureTargetService;
import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/aop/advanced/infrastructure")
public class InfrastructureController {

    private final InfrastructureTargetService infrastructureTargetService;
    private final AopTraceLog traceLog;
    private final ApplicationContext applicationContext;
    private final Environment environment;

    public InfrastructureController(
            InfrastructureTargetService infrastructureTargetService,
            AopTraceLog traceLog,
            ApplicationContext applicationContext,
            Environment environment
    ) {
        this.infrastructureTargetService = infrastructureTargetService;
        this.traceLog = traceLog;
        this.applicationContext = applicationContext;
        this.environment = environment;
    }

    /**
     * README: readme/vi/menu/13.Infrastructure/Infrastructure.md#infrastructure-demo
     * Purpose: Quan sát auto-proxy creator và Advisor chain nằm bên trong một Spring-managed AOP proxy.
     */
    @GetMapping("/inspect")
    public AopExperimentResponse inspectInfrastructure() {
        traceLog.reset();
        String result = infrastructureTargetService.execute();
        List<String> events = traceLog.snapshotAndClear();

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("isAopProxy", AopUtils.isAopProxy(infrastructureTargetService));
        facts.put("isCglibProxy", AopUtils.isCglibProxy(infrastructureTargetService));
        facts.put("isJdkDynamicProxy", AopUtils.isJdkDynamicProxy(infrastructureTargetService));
        facts.put("runtimeClass", infrastructureTargetService.getClass().getName());
        facts.put("targetClass", AopUtils.getTargetClass(infrastructureTargetService).getName());
        facts.put(
                "springAopAutoExplicitlyConfigured",
                environment.containsProperty("spring.aop.auto")
        );
        facts.put(
                "springAopAutoConfiguredValue",
                environment.getProperty("spring.aop.auto")
        );
        facts.put(
                "springAopProxyTargetClassExplicitlyConfigured",
                environment.containsProperty("spring.aop.proxy-target-class")
        );
        facts.put(
                "springAopProxyTargetClassConfiguredValue",
                environment.getProperty("spring.aop.proxy-target-class")
        );

        if (infrastructureTargetService instanceof Advised advised) {
            facts.put(
                    "advisors",
                    Arrays.stream(advised.getAdvisors())
                            .map(advisor -> advisor.getClass().getName())
                            .toList()
            );
        }

        String[] autoProxyCreatorNames =
                applicationContext.getBeanNamesForType(AbstractAutoProxyCreator.class);

        Map<String, String> autoProxyCreators = new LinkedHashMap<>();
        for (String beanName : autoProxyCreatorNames) {
            autoProxyCreators.put(
                    beanName,
                    applicationContext.getBean(beanName).getClass().getName()
            );
        }
        facts.put("autoProxyCreators", autoProxyCreators);
        facts.put("autoProxyCreatorPresent", !autoProxyCreators.isEmpty());

        return new AopExperimentResponse(result, events, facts);
    }
}

package com.example.learning.module.proxy.controller;

import com.example.learning.module.common.AopExperimentResponse;
import com.example.learning.module.common.AopTraceLog;
import com.example.learning.module.proxy.service.ProxyMentalModelService;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/aop/proxy")
public class ProxyMentalModelController {

    private final ProxyMentalModelService proxyMentalModelService;
    private final AopTraceLog traceLog;

    public ProxyMentalModelController(
            ProxyMentalModelService proxyMentalModelService,
            AopTraceLog traceLog
    ) {
        this.proxyMentalModelService = proxyMentalModelService;
        this.traceLog = traceLog;
    }

    /**
     * README: readme/vi/menu/3.Proxy/Proxy.md#proxy-demo
     * Purpose: Quan sát object được inject là AOP proxy và call chain đi qua proxy trước target.
     */
    @GetMapping("/inspect")
    public AopExperimentResponse inspectProxy() {
        traceLog.reset();

        String result = proxyMentalModelService.invokeTarget();

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("runtimeClass", proxyMentalModelService.getClass().getName());
        facts.put("targetClass", AopProxyUtils.ultimateTargetClass(proxyMentalModelService).getName());
        facts.put("isAopProxy", AopUtils.isAopProxy(proxyMentalModelService));
        facts.put("isCglibProxy", AopUtils.isCglibProxy(proxyMentalModelService));
        facts.put("isJdkDynamicProxy", AopUtils.isJdkDynamicProxy(proxyMentalModelService));

        return new AopExperimentResponse(
                result,
                traceLog.snapshotAndClear(),
                facts
        );
    }

    /**
     * README: readme/vi/menu/3.Proxy/Proxy.md#managed-vs-new-demo
     * Purpose: Chứng minh Spring-managed bean đi qua AOP proxy còn object tạo trực tiếp bằng new thì không.
     */
    @GetMapping("/managed-vs-new")
    public AopExperimentResponse compareManagedBeanAndPlainObject() {
        traceLog.reset();
        String managedResult = proxyMentalModelService.invokeTarget();
        List<String> managedEvents = traceLog.snapshot();

        traceLog.reset();
        ProxyMentalModelService plainObject =
                new ProxyMentalModelService(traceLog);
        String plainResult = plainObject.invokeTarget();
        List<String> plainEvents = traceLog.snapshotAndClear();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("managedResult", managedResult);
        result.put("managedEvents", managedEvents);
        result.put("plainResult", plainResult);
        result.put("plainEvents", plainEvents);

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("managedIsAopProxy", AopUtils.isAopProxy(proxyMentalModelService));
        facts.put("plainIsAopProxy", AopUtils.isAopProxy(plainObject));

        return new AopExperimentResponse(
                result,
                List.of(),
                facts
        );
    }
}

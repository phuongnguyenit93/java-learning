package com.example.learning.module.advanced.proxyfactory.service;

import com.example.learning.module.advanced.proxyfactory.interceptor.TracingMethodInterceptor;
import com.example.learning.module.common.AopTraceLog;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProgrammaticProxyService {

    private final AopTraceLog traceLog;

    public ProgrammaticProxyService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public Map<String, Object> compareProxyStrategies() {
        Map<String, Object> result = new LinkedHashMap<>();

        traceLog.reset();
        GreetingTarget jdkTarget = new GreetingTarget(traceLog);
        ProxyFactory jdkFactory = new ProxyFactory();
        jdkFactory.setTarget(jdkTarget);
        jdkFactory.setInterfaces(GreetingOperations.class);
        jdkFactory.setProxyTargetClass(false);
        jdkFactory.addAdvice(new TracingMethodInterceptor(traceLog));

        GreetingOperations jdkProxy =
                (GreetingOperations) jdkFactory.getProxy();

        String jdkResult = jdkProxy.greet("jdk");
        List<String> jdkEvents = traceLog.snapshot();

        Map<String, Object> jdk = new LinkedHashMap<>();
        jdk.put("result", jdkResult);
        jdk.put("events", jdkEvents);
        jdk.put("runtimeClass", jdkProxy.getClass().getName());
        jdk.put("isJdkDynamicProxy", AopUtils.isJdkDynamicProxy(jdkProxy));
        jdk.put("isCglibProxy", AopUtils.isCglibProxy(jdkProxy));
        jdk.put("proxyIsGreetingOperations", jdkProxy instanceof GreetingOperations);
        jdk.put("proxyIsGreetingTarget", jdkProxy instanceof GreetingTarget);
        jdk.put(
                "targetOnlyCapabilityVisibleThroughProxyType",
                hasPublicMethod(jdkProxy, "targetOnlyCapability")
        );
        result.put("jdkProxy", jdk);

        traceLog.reset();
        GreetingTarget cglibTarget = new GreetingTarget(traceLog);
        ProxyFactory cglibFactory = new ProxyFactory();
        cglibFactory.setTarget(cglibTarget);
        cglibFactory.setProxyTargetClass(true);
        cglibFactory.addAdvice(new TracingMethodInterceptor(traceLog));

        GreetingTarget cglibProxy =
                (GreetingTarget) cglibFactory.getProxy();

        String cglibResult = cglibProxy.greet("cglib");
        String targetOnlyResult = cglibProxy.targetOnlyCapability();
        List<String> cglibEvents = traceLog.snapshotAndClear();

        Map<String, Object> cglib = new LinkedHashMap<>();
        cglib.put("result", cglibResult);
        cglib.put("events", cglibEvents);
        cglib.put("runtimeClass", cglibProxy.getClass().getName());
        cglib.put("isJdkDynamicProxy", AopUtils.isJdkDynamicProxy(cglibProxy));
        cglib.put("isCglibProxy", AopUtils.isCglibProxy(cglibProxy));
        cglib.put("proxyIsGreetingOperations", cglibProxy instanceof GreetingOperations);
        cglib.put("proxyIsGreetingTarget", cglibProxy instanceof GreetingTarget);
        cglib.put(
                "targetOnlyCapabilityVisibleThroughProxyType",
                hasPublicMethod(cglibProxy, "targetOnlyCapability")
        );
        cglib.put("targetOnlyResult", targetOnlyResult);
        result.put("cglibProxy", cglib);

        return result;
    }

    private static boolean hasPublicMethod(
            Object proxy,
            String methodName
    ) {
        return Arrays.stream(proxy.getClass().getMethods())
                .anyMatch(method -> method.getName().equals(methodName));
    }
}

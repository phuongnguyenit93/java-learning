package com.example.learning.module.advanced.advisor.service;

import com.example.learning.module.advanced.advisor.pointcut.RuntimeArgumentPointcut;
import com.example.learning.module.common.AopTraceLog;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdvisorExperimentService {

    private final AopTraceLog traceLog;

    public AdvisorExperimentService(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    public Map<String, Object> observeAdvisor() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("staticNameMatchPointcut", observeStaticNameMatchPointcut());
        result.put("dynamicArgumentPointcut", observeDynamicArgumentPointcut());
        return result;
    }

    private Map<String, Object> observeStaticNameMatchPointcut() {
        AdvisorTarget target = new AdvisorTarget(traceLog);

        NameMatchMethodPointcut pointcut = new NameMatchMethodPointcut();
        pointcut.setMappedName("write");

        MethodInterceptor advice = invocation -> {
            traceLog.add("advisor-before:" + invocation.getMethod().getName());
            try {
                return invocation.proceed();
            } finally {
                traceLog.add("advisor-after:" + invocation.getMethod().getName());
            }
        };

        DefaultPointcutAdvisor advisor =
                new DefaultPointcutAdvisor(pointcut, advice);

        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvisor(advisor);

        AdvisorTarget proxy = (AdvisorTarget) proxyFactory.getProxy();

        traceLog.reset();
        String readResult = proxy.read();
        String writeResult = proxy.write();
        List<String> events = traceLog.snapshotAndClear();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("readResult", readResult);
        result.put("writeResult", writeResult);
        result.put("events", events);
        result.put("pointcutClass", pointcut.getClass().getName());
        result.put("classFilterClass", pointcut.getClassFilter().getClass().getName());
        result.put("methodMatcherClass", pointcut.getMethodMatcher().getClass().getName());
        result.put("methodMatcherIsRuntime", pointcut.getMethodMatcher().isRuntime());
        result.put("advisorClass", advisor.getClass().getName());
        result.put("adviceClass", advice.getClass().getName());
        return result;
    }

    private Map<String, Object> observeDynamicArgumentPointcut() {
        AdvisorTarget target = new AdvisorTarget(traceLog);

        RuntimeArgumentPointcut pointcut = new RuntimeArgumentPointcut(
                AdvisorTarget.class,
                "writeWithMode",
                "audit"
        );

        MethodInterceptor advice = invocation -> {
            Object mode = invocation.getArguments()[0];
            traceLog.add("dynamic-advisor-before:writeWithMode:mode=" + mode);
            try {
                return invocation.proceed();
            } finally {
                traceLog.add("dynamic-advisor-after:writeWithMode:mode=" + mode);
            }
        };

        DefaultPointcutAdvisor advisor =
                new DefaultPointcutAdvisor(pointcut, advice);

        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvisor(advisor);

        AdvisorTarget proxy = (AdvisorTarget) proxyFactory.getProxy();

        traceLog.reset();
        String auditResult = proxy.writeWithMode("audit");
        String plainResult = proxy.writeWithMode("plain");
        List<String> events = traceLog.snapshotAndClear();

        Method writeWithMode = Arrays.stream(AdvisorTarget.class.getMethods())
                .filter(method -> method.getName().equals("writeWithMode"))
                .findFirst()
                .orElseThrow();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("auditResult", auditResult);
        result.put("plainResult", plainResult);
        result.put("events", events);
        result.put(
                "classFilterMatchesAdvisorTarget",
                pointcut.getClassFilter().matches(AdvisorTarget.class)
        );
        result.put(
                "staticMethodMatch",
                pointcut.getMethodMatcher().matches(
                        writeWithMode,
                        AdvisorTarget.class
                )
        );
        result.put("methodMatcherIsRuntime", pointcut.getMethodMatcher().isRuntime());
        result.put(
                "runtimeMatchAudit",
                pointcut.getMethodMatcher().matches(
                        writeWithMode,
                        AdvisorTarget.class,
                        "audit"
                )
        );
        result.put(
                "runtimeMatchPlain",
                pointcut.getMethodMatcher().matches(
                        writeWithMode,
                        AdvisorTarget.class,
                        "plain"
                )
        );
        result.put("pointcutClass", pointcut.getClass().getName());
        result.put("advisorClass", advisor.getClass().getName());
        return result;
    }
}

package com.example.learning.module.proxy.aspect;

import com.example.learning.module.common.AopTraceLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ProxyMentalModelAspect {

    private final AopTraceLog traceLog;

    public ProxyMentalModelAspect(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Around("execution(* com.example.learning.module.proxy.service.ProxyMentalModelService.invokeTarget(..))")
    public Object observeProxyBoundary(ProceedingJoinPoint joinPoint) throws Throwable {
        traceLog.add("proxy-boundary:before-target");
        Object result = joinPoint.proceed();
        traceLog.add("proxy-boundary:after-target");
        return result;
    }
}

package com.example.learning.module.advanced.infrastructure.aspect;

import com.example.learning.module.common.AopTraceLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class InfrastructureAspect {

    private final AopTraceLog traceLog;

    public InfrastructureAspect(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Around("execution(* com.example.learning.module.advanced.infrastructure.service.InfrastructureTargetService.execute(..))")
    public Object observe(ProceedingJoinPoint joinPoint) throws Throwable {
        traceLog.add("infrastructure-aspect:before");
        Object result = joinPoint.proceed();
        traceLog.add("infrastructure-aspect:after");
        return result;
    }
}

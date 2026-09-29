package com.example.learning.module.crosscutting.aspect;

import com.example.learning.module.common.AopTraceLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class CrossCuttingLoggingAspect {

    private final AopTraceLog traceLog;

    public CrossCuttingLoggingAspect(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Around("execution(* com.example.learning.module.crosscutting.service.CrossCuttingService.*(..))")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();

        traceLog.add("logging-before:" + methodName);

        try {
            Object result = joinPoint.proceed();
            traceLog.add("logging-after:" + methodName);
            return result;
        } catch (Throwable throwable) {
            traceLog.add("logging-error:" + methodName);
            throw throwable;
        }
    }
}

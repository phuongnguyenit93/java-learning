package com.example.learning.module.advanced.boundary.aspect;

import com.example.learning.module.common.AopTraceLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RuntimeBoundaryAspect {

    private final AopTraceLog traceLog;

    public RuntimeBoundaryAspect(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Around("execution(* com.example.learning.module.advanced.boundary.service.RuntimeBoundaryService.execute(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        traceLog.add("runtime-aspect:join-point-kind=" + joinPoint.getKind());
        traceLog.add("runtime-aspect:before");
        Object result = joinPoint.proceed();
        traceLog.add("runtime-aspect:after");
        return result;
    }
}

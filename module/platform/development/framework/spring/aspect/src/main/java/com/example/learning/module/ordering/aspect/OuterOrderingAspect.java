package com.example.learning.module.ordering.aspect;

import com.example.learning.module.common.AopTraceLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(1)
public class OuterOrderingAspect {

    private final AopTraceLog traceLog;

    public OuterOrderingAspect(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Around("execution(* com.example.learning.module.ordering.service.OrderingService.execute(..))")
    public Object outer(ProceedingJoinPoint joinPoint) throws Throwable {
        traceLog.add("order-1:before");
        Object result = joinPoint.proceed();
        traceLog.add("order-1:after");
        return result;
    }
}

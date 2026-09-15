package com.example.learning.module.ordering.aspect;

import com.example.learning.module.common.AopTraceLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(2)
public class InnerOrderingAspect {

    private final AopTraceLog traceLog;

    public InnerOrderingAspect(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Around("execution(* com.example.learning.module.ordering.service.OrderingService.execute(..))")
    public Object inner(ProceedingJoinPoint joinPoint) throws Throwable {
        traceLog.add("order-2:before");
        Object result = joinPoint.proceed();
        traceLog.add("order-2:after");
        return result;
    }
}

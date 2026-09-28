package com.example.learning.module.advice.aspect;

import com.example.learning.module.common.AopTraceLog;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AdviceLifecycleAspect {

    private final AopTraceLog traceLog;

    public AdviceLifecycleAspect(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Before("execution(* com.example.learning.module.advice.service.AdviceLifecycleService.*(..))")
    public void before(JoinPoint joinPoint) {
        traceLog.add("@Before:" + joinPoint.getSignature().getName());
    }

    @After("execution(* com.example.learning.module.advice.service.AdviceLifecycleService.*(..))")
    public void after(JoinPoint joinPoint) {
        traceLog.add("@After:" + joinPoint.getSignature().getName());
    }

    @AfterReturning(
            pointcut = "execution(* com.example.learning.module.advice.service.AdviceLifecycleService.*(..))",
            returning = "result"
    )
    public void afterReturning(
            JoinPoint joinPoint,
            Object result
    ) {
        traceLog.add("@AfterReturning:" + joinPoint.getSignature().getName() + ":" + result);
    }

    @AfterThrowing(
            pointcut = "execution(* com.example.learning.module.advice.service.AdviceLifecycleService.*(..))",
            throwing = "throwable"
    )
    public void afterThrowing(
            JoinPoint joinPoint,
            Throwable throwable
    ) {
        traceLog.add("@AfterThrowing:" + joinPoint.getSignature().getName() + ":" + throwable.getClass().getSimpleName());
    }
}

package com.example.learning.module.around.aspect;

import com.example.learning.module.common.AopTraceLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AroundAdviceAspect {

    private final AopTraceLog traceLog;

    public AroundAdviceAspect(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Around("execution(* com.example.learning.module.around.service.AroundAdviceService.timedOperation(..))")
    public Object measureExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        long startedAt = System.nanoTime();
        traceLog.add("around:before-proceed");

        try {
            return joinPoint.proceed();
        } finally {
            long elapsedNanos = System.nanoTime() - startedAt;
            traceLog.add("around:after-proceed:elapsed-nanos=" + elapsedNanos);
        }
    }

    @Around("execution(* com.example.learning.module.around.service.AroundAdviceService.transformResult(..))")
    public Object transformReturnValue(ProceedingJoinPoint joinPoint) throws Throwable {
        traceLog.add("around:before-transform");
        String original = (String) joinPoint.proceed();
        String transformed = original + "|transformed-by-around";
        traceLog.add("around:transformed-return-value");
        return transformed;
    }

    @Around("execution(* com.example.learning.module.around.service.AroundAdviceService.skippedTarget(..))")
    public Object skipProceed() {
        traceLog.add("around:skip-proceed");
        return "returned-without-calling-target";
    }

    @Around("execution(* com.example.learning.module.around.service.AroundAdviceService.normalizeArgument(..))")
    public Object normalizeArgument(ProceedingJoinPoint joinPoint) throws Throwable {
        String original = (String) joinPoint.getArgs()[0];
        String normalized = original.trim().toLowerCase();

        traceLog.add("around:argument-before=" + original);
        traceLog.add("around:argument-after=" + normalized);

        return joinPoint.proceed(new Object[]{normalized});
    }

    @Around("execution(* com.example.learning.module.around.service.AroundAdviceService.throwsFailure(..))")
    public Object observeExceptionPropagation(ProceedingJoinPoint joinPoint) throws Throwable {
        traceLog.add("around:exception-before-proceed");

        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            traceLog.add("around:exception-observed=" + throwable.getClass().getSimpleName());
            throw throwable;
        } finally {
            traceLog.add("around:exception-finally");
        }
    }
}

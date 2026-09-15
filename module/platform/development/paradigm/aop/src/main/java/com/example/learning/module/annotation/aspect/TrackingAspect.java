package com.example.learning.module.annotation.aspect;

import com.example.learning.module.annotation.TrackExecution;
import com.example.learning.module.common.AopTraceLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TrackingAspect {

    private final AopTraceLog traceLog;

    public TrackingAspect(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Around("@annotation(trackExecution)")
    public Object track(
            ProceedingJoinPoint joinPoint,
            TrackExecution trackExecution
    ) throws Throwable {
        long startedAt = System.nanoTime();
        traceLog.add("track-before:label=" + trackExecution.value());

        try {
            Object result = joinPoint.proceed();
            traceLog.add("track-success:method=" + joinPoint.getSignature().getName());
            return result;
        } finally {
            long elapsedNanos = System.nanoTime() - startedAt;
            traceLog.add("track-finished:elapsed-nanos=" + elapsedNanos);
        }
    }
}

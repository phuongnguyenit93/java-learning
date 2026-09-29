package com.example.learning.module.patterns.aspect;

import com.example.learning.module.common.AopTraceLog;
import com.example.learning.module.patterns.annotation.AuditedOperation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PracticalPatternAspect {

    private final AopTraceLog traceLog;

    public PracticalPatternAspect(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Around("@annotation(auditedOperation)")
    public Object audit(
            ProceedingJoinPoint joinPoint,
            AuditedOperation auditedOperation
    ) throws Throwable {
        long startedAt = System.nanoTime();
        traceLog.add("audit-start:action=" + auditedOperation.action());

        try {
            Object result = joinPoint.proceed();
            traceLog.add("audit-success:method=" + joinPoint.getSignature().getName());
            return result;
        } catch (Throwable throwable) {
            traceLog.add("audit-failure:type=" + throwable.getClass().getSimpleName());
            throw throwable;
        } finally {
            traceLog.add("metric:elapsed-nanos=" + (System.nanoTime() - startedAt));
        }
    }
}

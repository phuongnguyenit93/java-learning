package com.example.learning.module.terminology.aspect;

import com.example.learning.module.common.AopTraceLog;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TerminologyAspect {

    private final AopTraceLog traceLog;

    public TerminologyAspect(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Before("execution(* com.example.learning.module.terminology.service.TerminologyService.execute(..))")
    public void explainTerms(JoinPoint joinPoint) {
        traceLog.add("aspect=TerminologyAspect");
        traceLog.add("advice=@Before");
        traceLog.add("join-point=" + joinPoint.getSignature().toShortString());
        traceLog.add("proxy-class=" + joinPoint.getThis().getClass().getSimpleName());
        traceLog.add("target-class=" + joinPoint.getTarget().getClass().getSimpleName());
    }
}

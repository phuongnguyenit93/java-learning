package com.example.learning.module.pointcut.aspect;

import com.example.learning.module.common.AopTraceLog;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PointcutMatchingAspect {

    private final AopTraceLog traceLog;

    public PointcutMatchingAspect(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Before("execution(* com.example.learning.module.pointcut.service.PointcutService.byExecution(..))")
    public void matchExecution() {
        traceLog.add("pointcut:execution");
    }

    @Before("execution(* com.example.learning.module.pointcut.service.PointcutService.*(..)) && " +
            "@annotation(com.example.learning.module.pointcut.annotation.PointcutMarker)")
    public void matchAnnotation() {
        traceLog.add("pointcut:@annotation");
    }

    @Before("execution(* com.example.learning.module.pointcut.service.PointcutService.byArgs(..)) && args(value)")
    public void matchArgs(String value) {
        traceLog.add("pointcut:args:" + value);
    }

    @Before("execution(* com.example.learning.module.pointcut.service.PointcutService.byRuntimeArgs(..)) && " +
            "args(java.lang.String)")
    public void matchRuntimeArgumentType(JoinPoint joinPoint) {
        Object value = joinPoint.getArgs()[0];
        traceLog.add("pointcut:args-runtime-type=" + value.getClass().getSimpleName());
    }

    @Pointcut("within(com.example.learning.module.pointcut.service.PointcutService)")
    public void pointcutServiceType() {
    }

    @Pointcut("execution(* com.example.learning.module.pointcut.service.PointcutService.byWithin(..))")
    public void byWithinMethod() {
    }

    @Before("pointcutServiceType() && byWithinMethod()")
    public void matchWithinAndNamedComposition() {
        traceLog.add("pointcut:within+named-composition");
    }

    @Before("this(com.example.learning.module.pointcut.service.PointcutService) && " +
            "execution(* com.example.learning.module.pointcut.service.PointcutService.byThisAndTarget(..))")
    public void matchThis() {
        traceLog.add("pointcut:this-proxy-type");
    }

    @Before("target(com.example.learning.module.pointcut.service.PointcutService) && " +
            "execution(* com.example.learning.module.pointcut.service.PointcutService.byThisAndTarget(..))")
    public void matchTarget() {
        traceLog.add("pointcut:target-type");
    }

    @Before("bean(pointcutService) && execution(* com.example.learning.module.pointcut.service.PointcutService.byBean(..))")
    public void matchSpringBeanName() {
        traceLog.add("pointcut:bean-name");
    }
}

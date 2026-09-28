package com.example.learning.module.advanced.advisor.pointcut;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;

import java.lang.reflect.Method;

public class RuntimeArgumentPointcut implements Pointcut {

    private final Class<?> targetType;
    private final String methodName;
    private final Object requiredFirstArgument;
    private final ClassFilter classFilter;
    private final MethodMatcher methodMatcher;

    public RuntimeArgumentPointcut(
            Class<?> targetType,
            String methodName,
            Object requiredFirstArgument
    ) {
        this.targetType = targetType;
        this.methodName = methodName;
        this.requiredFirstArgument = requiredFirstArgument;

        this.classFilter =
                candidateClass -> this.targetType.isAssignableFrom(candidateClass);

        this.methodMatcher = new MethodMatcher() {

            @Override
            public boolean matches(
                    Method method,
                    Class<?> targetClass
            ) {
                return classFilter.matches(targetClass) &&
                        RuntimeArgumentPointcut.this.methodName.equals(method.getName());
            }

            @Override
            public boolean isRuntime() {
                return true;
            }

            @Override
            public boolean matches(
                    Method method,
                    Class<?> targetClass,
                    Object... args
            ) {
                return matches(method, targetClass) &&
                        args.length > 0 &&
                        RuntimeArgumentPointcut.this.requiredFirstArgument.equals(args[0]);
            }
        };
    }

    @Override
    public ClassFilter getClassFilter() {
        return classFilter;
    }

    @Override
    public MethodMatcher getMethodMatcher() {
        return methodMatcher;
    }
}

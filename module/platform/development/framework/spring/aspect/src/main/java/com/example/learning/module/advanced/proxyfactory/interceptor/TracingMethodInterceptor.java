package com.example.learning.module.advanced.proxyfactory.interceptor;

import com.example.learning.module.common.AopTraceLog;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

public class TracingMethodInterceptor implements MethodInterceptor {

    private final AopTraceLog traceLog;

    public TracingMethodInterceptor(AopTraceLog traceLog) {
        this.traceLog = traceLog;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        traceLog.add("interceptor-before:" + invocation.getMethod().getName());

        try {
            return invocation.proceed();
        } finally {
            traceLog.add("interceptor-after:" + invocation.getMethod().getName());
        }
    }
}

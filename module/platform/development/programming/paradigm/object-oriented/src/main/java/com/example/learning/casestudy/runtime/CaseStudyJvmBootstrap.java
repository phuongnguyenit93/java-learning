package com.example.learning.casestudy.runtime;

import java.lang.reflect.InvocationTargetException;

/** Entry point for the Prime-owned shared STEP8 child-JVM launcher. */
public final class CaseStudyJvmBootstrap {
    private CaseStudyJvmBootstrap() {
    }

    public static void main(String[] args) throws Exception {
        if (!CaseStudyJvmIsolation.isChildProcess() || args.length != 2
                || !args[0].matches("com\\.example\\.learning\\.casestudy\\.service\\.Group[0-9]+Service")
                || !args[1].matches("executeCase[0-9]+")) {
            throw new IllegalArgumentException("Invalid STEP8 child JVM entry");
        }
        Class<?> type = Class.forName(args[0], true, Thread.currentThread().getContextClassLoader());
        Object service = type.getDeclaredConstructor().newInstance();
        try {
            type.getMethod(args[1]).invoke(service);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }
}

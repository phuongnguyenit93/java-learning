package com.example.projectbuild.executioncontext.context;

public final class ExecutionContextHolder {

    private static final InheritableThreadLocal<String> EXECUTION_ID = new InheritableThreadLocal<>();

    private ExecutionContextHolder() {
    }

    public static void set(String executionId) {
        EXECUTION_ID.set(executionId);
    }

    public static String currentExecutionId() {
        return EXECUTION_ID.get();
    }

    public static void clear() {
        EXECUTION_ID.remove();
    }
}

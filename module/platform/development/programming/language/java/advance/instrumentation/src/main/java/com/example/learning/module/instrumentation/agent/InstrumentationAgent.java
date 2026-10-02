package com.example.learning.module.instrumentation.agent;

import java.lang.instrument.Instrumentation;

public final class InstrumentationAgent {

    private static volatile Instrumentation instrumentation;

    private InstrumentationAgent() {
    }

    public static void premain(
            String agentArgs,
            Instrumentation instrumentation
    ) {
        install(instrumentation);
    }

    public static void agentmain(
            String agentArgs,
            Instrumentation instrumentation
    ) {
        install(instrumentation);
    }

    public static Instrumentation instrumentation() {
        return instrumentation;
    }

    private static void install(
            Instrumentation instrumentation
    ) {
        InstrumentationAgent.instrumentation =
                instrumentation;
    }
}

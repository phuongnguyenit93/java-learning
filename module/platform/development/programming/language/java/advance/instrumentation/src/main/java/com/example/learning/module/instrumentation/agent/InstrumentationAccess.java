package com.example.learning.module.instrumentation.agent;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class InstrumentationAccess {

    private InstrumentationAccess() {
    }

    public static Instrumentation current() {
        try {
            Class<?> agentClass =
                    ClassLoader.getSystemClassLoader()
                            .loadClass(
                                    InstrumentationAgent.class.getName()
                            );

            Method method =
                    agentClass.getMethod(
                            "instrumentation"
                    );

            return (Instrumentation) method.invoke(
                    null
            );
        } catch (ClassNotFoundException error) {
            return null;
        } catch (
                NoSuchMethodException |
                IllegalAccessException |
                InvocationTargetException error
        ) {
            throw new IllegalStateException(
                    "Unable to access the Java Instrumentation agent state.",
                    error
            );
        }
    }
}

package com.example.learning.module.instrumentation.service;

import com.example.learning.module.instrumentation.agent.InstrumentationAccess;
import com.example.learning.module.instrumentation.target.InstrumentationProbeTarget;
import org.springframework.stereotype.Service;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class InstrumentationExperimentService {

    public Map<String, Object> agentStatus() {
        Instrumentation instrumentation =
                InstrumentationAccess.current();

        boolean loaded =
                instrumentation != null;

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "agentLoaded",
                loaded
        );

        result.put(
                "applicationClassLoader",
                classLoaderName(
                        InstrumentationExperimentService.class
                                .getClassLoader()
                )
        );

        if (!loaded) {
            result.put(
                    "message",
                    "Start this module with its Java agent to enable Instrumentation experiments."
            );
            return result;
        }

        Class<?> probeTarget =
                InstrumentationProbeTarget.class;

        result.put(
                "retransformSupported",
                instrumentation.isRetransformClassesSupported()
        );
        result.put(
                "redefineSupported",
                instrumentation.isRedefineClassesSupported()
        );
        result.put(
                "nativeMethodPrefixSupported",
                instrumentation.isNativeMethodPrefixSupported()
        );
        result.put(
                "loadedClassCount",
                instrumentation.getAllLoadedClasses().length
        );
        result.put(
                "probeTargetClass",
                probeTarget.getName()
        );
        result.put(
                "probeTargetModifiable",
                instrumentation.isModifiableClass(
                        probeTarget
                )
        );
        result.put(
                "probeTargetClassLoader",
                classLoaderName(
                        probeTarget.getClassLoader()
                )
        );
        result.put(
                "agentClassLoader",
                systemAgentClassLoaderName()
        );

        return result;
    }

    public synchronized Map<String, Object> retransformGroups() {
        Instrumentation instrumentation =
                InstrumentationAccess.current();

        Map<String, Object> result =
                new LinkedHashMap<>();

        if (instrumentation == null) {
            result.put(
                    "experimentReady",
                    false
            );
            result.put(
                    "reason",
                    "Java agent is not loaded."
            );
            return result;
        }

        if (!instrumentation.isRetransformClassesSupported()) {
            result.put(
                    "experimentReady",
                    false
            );
            result.put(
                    "reason",
                    "This JVM does not support class retransformation."
            );
            return result;
        }

        Class<?> targetClass =
                InstrumentationProbeTarget.class;

        if (!instrumentation.isModifiableClass(targetClass)) {
            result.put(
                    "experimentReady",
                    false
            );
            result.put(
                    "reason",
                    "The probe target is not modifiable."
            );
            return result;
        }

        String before =
                InstrumentationProbeTarget.message();

        CallbackObservation nonRetransformable =
                new CallbackObservation(
                        targetClass
                );

        CallbackObservation retransformable =
                new CallbackObservation(
                        targetClass
                );

        boolean nonRetransformableAdded =
                false;
        boolean retransformableAdded =
                false;
        boolean nonRetransformableRemoved =
                false;
        boolean retransformableRemoved =
                false;

        try {
            instrumentation.addTransformer(
                    nonRetransformable,
                    false
            );
            nonRetransformableAdded =
                    true;

            instrumentation.addTransformer(
                    retransformable,
                    true
            );
            retransformableAdded =
                    true;

            instrumentation.retransformClasses(
                    targetClass
            );
        } catch (Exception error) {
            throw new IllegalStateException(
                    "Retransformation experiment failed.",
                    error
            );
        } finally {
            if (retransformableAdded) {
                retransformableRemoved =
                        instrumentation.removeTransformer(
                                retransformable
                        );
            }

            if (nonRetransformableAdded) {
                nonRetransformableRemoved =
                        instrumentation.removeTransformer(
                                nonRetransformable
                        );
            }
        }

        String after =
                InstrumentationProbeTarget.message();

        result.put(
                "experimentReady",
                true
        );
        result.put(
                "targetClass",
                targetClass.getName()
        );
        result.put(
                "behaviorBeforeRetransform",
                before
        );
        result.put(
                "behaviorAfterRetransform",
                after
        );
        result.put(
                "nonRetransformableCallbackCount",
                nonRetransformable.callbackCount()
        );
        result.put(
                "retransformableCallbackCount",
                retransformable.callbackCount()
        );
        result.put(
                "classBeingRedefinedObserved",
                retransformable.classBeingRedefinedObserved()
        );
        result.put(
                "callbackClassName",
                retransformable.lastClassName()
        );
        result.put(
                "nonRetransformableRemoved",
                nonRetransformableRemoved
        );
        result.put(
                "retransformableRemoved",
                retransformableRemoved
        );
        result.put(
                "conclusion",
                "During retransformation the retransformation-capable transformer is invoked again, while the incapable transformer is not."
        );

        return result;
    }

    private static String classLoaderName(
            ClassLoader classLoader
    ) {
        return classLoader == null
                ? "bootstrap"
                : classLoader.getClass().getName();
    }

    private static String systemAgentClassLoaderName() {
        try {
            Class<?> agentClass =
                    ClassLoader.getSystemClassLoader()
                            .loadClass(
                                    "com.example.learning.module.instrumentation.agent.InstrumentationAgent"
                            );

            return classLoaderName(
                    agentClass.getClassLoader()
            );
        } catch (ClassNotFoundException error) {
            return "not-loaded";
        }
    }

    private static final class CallbackObservation
            implements ClassFileTransformer {

        private final Class<?> targetClass;
        private final AtomicInteger callbackCount =
                new AtomicInteger();
        private final AtomicBoolean classBeingRedefinedObserved =
                new AtomicBoolean();
        private final AtomicReference<String> lastClassName =
                new AtomicReference<>();

        private CallbackObservation(
                Class<?> targetClass
        ) {
            this.targetClass =
                    targetClass;
        }

        @Override
        public byte[] transform(
                Module module,
                ClassLoader loader,
                String className,
                Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain,
                byte[] classfileBuffer
        ) {
            if (classBeingRedefined != targetClass) {
                return null;
            }

            callbackCount.incrementAndGet();
            classBeingRedefinedObserved.set(
                    true
            );
            lastClassName.set(
                    className
            );

            return null;
        }

        private int callbackCount() {
            return callbackCount.get();
        }

        private boolean classBeingRedefinedObserved() {
            return classBeingRedefinedObserved.get();
        }

        private String lastClassName() {
            return lastClassName.get();
        }
    }
}

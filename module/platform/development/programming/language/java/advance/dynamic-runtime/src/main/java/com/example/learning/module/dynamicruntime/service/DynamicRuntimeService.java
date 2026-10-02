package com.example.learning.module.dynamicruntime.service;

import org.springframework.stereotype.Service;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VarHandle;
import java.lang.invoke.WrongMethodTypeException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DynamicRuntimeService {

    private static final VarHandle COUNTER_VALUE;

    static {
        try {
            COUNTER_VALUE = MethodHandles.lookup()
                    .findVarHandle(Counter.class, "value", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public Map<String, Object> methodHandleDemo() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType methodType =
                    MethodType.methodType(String.class, String.class);

            MethodHandle virtualHandle =
                    lookup.findVirtual(
                            Greeter.class,
                            "greet",
                            methodType
                    );

            MethodHandle boundHandle =
                    virtualHandle.bindTo(
                            new Greeter("Hello")
                    );

            String exactResult =
                    (String) boundHandle.invokeExact(
                            "Phuong"
                    );

            MethodHandle adaptedHandle =
                    boundHandle.asType(
                            MethodType.methodType(
                                    Object.class,
                                    Object.class
                            )
                    );

            Object adaptedResult =
                    (Object) adaptedHandle.invokeExact(
                            (Object) "Dynamic Runtime"
                    );

            boolean wrongMethodTypeObserved = false;
            try {
                int ignored =
                        (int) boundHandle.invokeExact(
                                "wrong-return-type"
                        );
            } catch (WrongMethodTypeException expected) {
                wrongMethodTypeObserved = true;
            }

            return map(
                    "virtualHandleType", virtualHandle.type().toString(),
                    "boundHandleType", boundHandle.type().toString(),
                    "exactResult", exactResult,
                    "adaptedHandleType", adaptedHandle.type().toString(),
                    "adaptedResult", adaptedResult,
                    "wrongMethodTypeExceptionObserved", wrongMethodTypeObserved
            );
        } catch (Throwable error) {
            throw new IllegalStateException(
                    "MethodHandle experiment failed unexpectedly.",
                    error
            );
        }
    }

    public Map<String, Object> lookupDemo() {
        try {
            MethodType methodType =
                    MethodType.methodType(
                            String.class,
                            String.class
                    );

            boolean publicLookupRejected = false;
            try {
                MethodHandles.publicLookup()
                        .findVirtual(
                                SecretGreeter.class,
                                "greet",
                                methodType
                        );
            } catch (IllegalAccessException expected) {
                publicLookupRejected = true;
            }

            MethodHandles.Lookup callerLookup =
                    MethodHandles.lookup();

            MethodHandles.Lookup privateLookup =
                    MethodHandles.privateLookupIn(
                            SecretGreeter.class,
                            callerLookup
                    );

            MethodHandle privateHandle =
                    privateLookup.findVirtual(
                            SecretGreeter.class,
                            "greet",
                            methodType
                    );

            String privateResult =
                    (String) privateHandle.invokeExact(
                            new SecretGreeter(),
                            "Phuong"
                    );

            return map(
                    "publicLookupRejectedPrivateMember", publicLookupRejected,
                    "callerLookupClass", callerLookup.lookupClass().getName(),
                    "privateLookupClass", privateLookup.lookupClass().getName(),
                    "privateLookupModes", privateLookup.lookupModes(),
                    "privateInvocationResult", privateResult
            );
        } catch (Throwable error) {
            throw new IllegalStateException(
                    "Lookup experiment failed unexpectedly.",
                    error
            );
        }
    }

    public Map<String, Object> callSiteDemo() {
        try {
            MethodHandle initialTarget =
                    MethodHandles.constant(
                            String.class,
                            "VERSION_1"
                    );

            MutableCallSite callSite =
                    new MutableCallSite(
                            initialTarget
                    );

            MethodHandle dynamicInvoker =
                    callSite.dynamicInvoker();

            String beforeUpdate =
                    (String) dynamicInvoker.invokeExact();

            MethodHandle updatedTarget =
                    MethodHandles.constant(
                            String.class,
                            "VERSION_2"
                    );

            callSite.setTarget(
                    updatedTarget
            );

            MutableCallSite.syncAll(
                    new MutableCallSite[]{callSite}
            );

            String afterUpdate =
                    (String) dynamicInvoker.invokeExact();

            boolean wrongTargetTypeRejected = false;
            try {
                callSite.setTarget(
                        MethodHandles.constant(
                                Integer.class,
                                1
                        )
                );
            } catch (WrongMethodTypeException expected) {
                wrongTargetTypeRejected = true;
            }

            return map(
                    "callSiteType", callSite.type().toString(),
                    "dynamicInvokerType", dynamicInvoker.type().toString(),
                    "beforeTargetUpdate", beforeUpdate,
                    "afterTargetUpdate", afterUpdate,
                    "sameInvokerUsedBeforeAndAfterUpdate", true,
                    "wrongTargetTypeRejected", wrongTargetTypeRejected
            );
        } catch (Throwable error) {
            throw new IllegalStateException(
                    "CallSite experiment failed unexpectedly.",
                    error
            );
        }
    }

    public Map<String, Object> varHandleDemo() {
        Counter counter =
                new Counter(10);

        int before =
                (int) COUNTER_VALUE.get(
                        counter
                );

        boolean firstCompareAndSet =
                (boolean) COUNTER_VALUE.compareAndSet(
                        counter,
                        10,
                        20
                );

        boolean secondCompareAndSet =
                (boolean) COUNTER_VALUE.compareAndSet(
                        counter,
                        10,
                        30
                );

        int after =
                (int) COUNTER_VALUE.get(
                        counter
                );

        MethodType compareAndSetType =
                COUNTER_VALUE.accessModeType(
                        VarHandle.AccessMode.COMPARE_AND_SET
                );

        List<String> coordinateTypes =
                COUNTER_VALUE.coordinateTypes()
                        .stream()
                        .map(Class::getName)
                        .toList();

        return map(
                "variableType", COUNTER_VALUE.varType().getName(),
                "coordinateTypes", coordinateTypes,
                "compareAndSetType", compareAndSetType.toString(),
                "before", before,
                "firstCompareAndSetSucceeded", firstCompareAndSet,
                "secondCompareAndSetWithStaleExpectedSucceeded", secondCompareAndSet,
                "after", after,
                "getAndAddSupported",
                COUNTER_VALUE.isAccessModeSupported(
                        VarHandle.AccessMode.GET_AND_ADD
                ),
                "defaultInvokeExactBehavior",
                COUNTER_VALUE.hasInvokeExactBehavior()
        );
    }

    private static Map<String, Object> map(
            Object... pairs
    ) {
        Map<String, Object> result =
                new LinkedHashMap<>();

        for (
                int index = 0;
                index < pairs.length;
                index += 2
        ) {
            result.put(
                    (String) pairs[index],
                    pairs[index + 1]
            );
        }

        return result;
    }

    static final class Greeter {

        private final String prefix;

        Greeter(
                String prefix
        ) {
            this.prefix = prefix;
        }

        String greet(
                String name
        ) {
            return prefix + " " + name;
        }
    }

    public static final class SecretGreeter {

        private String greet(
                String name
        ) {
            return "Secret hello " + name;
        }
    }

    static final class Counter {

        private int value;

        Counter(
                int value
        ) {
            this.value = value;
        }
    }
}

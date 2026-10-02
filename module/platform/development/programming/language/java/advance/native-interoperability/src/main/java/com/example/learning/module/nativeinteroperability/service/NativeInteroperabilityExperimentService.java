package com.example.learning.module.nativeinteroperability.service;

import org.springframework.stereotype.Service;

import java.lang.WrongThreadException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class NativeInteroperabilityExperimentService {

    public Map<String, Object> spatialBoundsDemo() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(
                    8,
                    ValueLayout.JAVA_INT.byteAlignment()
            );

            segment.set(ValueLayout.JAVA_INT, 0, 100);
            int validRead = segment.get(ValueLayout.JAVA_INT, 0);

            boolean violationObserved = false;
            String exceptionType = null;

            try {
                segment.get(
                        ValueLayout.JAVA_INT_UNALIGNED,
                        6
                );
            } catch (IndexOutOfBoundsException expected) {
                violationObserved = true;
                exceptionType = expected.getClass().getSimpleName();
            }

            return map(
                    "segmentByteSize", segment.byteSize(),
                    "layoutByteSize", ValueLayout.JAVA_INT.byteSize(),
                    "validReadOffset", 0,
                    "validReadValue", validRead,
                    "attemptedReadOffset", 6,
                    "spatialBoundsViolationObserved", violationObserved,
                    "exceptionType", exceptionType
            );
        }
    }

    public Map<String, Object> temporalSafetyDemo() {
        Arena arena = Arena.ofConfined();
        try {
            MemorySegment segment =
                    arena.allocate(ValueLayout.JAVA_LONG);
            segment.set(ValueLayout.JAVA_LONG, 0, 42L);

            long valueBeforeClose =
                    segment.get(ValueLayout.JAVA_LONG, 0);
            boolean aliveBeforeClose =
                    segment.scope().isAlive();

            arena.close();

            boolean aliveAfterClose =
                    segment.scope().isAlive();
            boolean closedAccessRejected = false;
            String exceptionType = null;

            try {
                segment.get(ValueLayout.JAVA_LONG, 0);
            } catch (IllegalStateException expected) {
                closedAccessRejected = true;
                exceptionType =
                        expected.getClass().getSimpleName();
            }

            return map(
                    "valueBeforeClose", valueBeforeClose,
                    "scopeAliveBeforeClose", aliveBeforeClose,
                    "scopeAliveAfterClose", aliveAfterClose,
                    "accessAfterCloseRejected",
                    closedAccessRejected,
                    "exceptionType", exceptionType
            );
        } finally {
            if (arena.scope().isAlive()) {
                arena.close();
            }
        }
    }

    public Map<String, Object> threadAccessDemo() {
        try (Arena confinedArena = Arena.ofConfined();
             Arena sharedArena = Arena.ofShared();
             ExecutorService executor =
                     Executors.newSingleThreadExecutor(
                             runnable -> new Thread(
                                     runnable,
                                     "native-interop-worker"
                             )
                     )) {

            MemorySegment confined =
                    confinedArena.allocate(ValueLayout.JAVA_INT);
            MemorySegment shared =
                    sharedArena.allocate(ValueLayout.JAVA_INT);

            confined.set(ValueLayout.JAVA_INT, 0, 11);
            shared.set(ValueLayout.JAVA_INT, 0, 22);

            String ownerThread = Thread.currentThread().getName();

            Future<Map<String, Object>> workerResult =
                    executor.submit(() -> {
                        Thread worker = Thread.currentThread();
                        boolean confinedRejected = false;
                        String confinedExceptionType = null;

                        try {
                            confined.get(ValueLayout.JAVA_INT, 0);
                        } catch (WrongThreadException expected) {
                            confinedRejected = true;
                            confinedExceptionType =
                                    expected.getClass().getSimpleName();
                        }

                        int sharedValue =
                                shared.get(ValueLayout.JAVA_INT, 0);

                        return map(
                                "workerThread", worker.getName(),
                                "confinedAccessibleByWorker",
                                confined.isAccessibleBy(worker),
                                "confinedAccessRejected",
                                confinedRejected,
                                "confinedExceptionType",
                                confinedExceptionType,
                                "sharedAccessibleByWorker",
                                shared.isAccessibleBy(worker),
                                "sharedReadValue",
                                sharedValue
                        );
                    });

            Map<String, Object> workerEvidence;

            try {
                workerEvidence =
                        workerResult.get(2, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                workerResult.cancel(true);
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Thread-access experiment was interrupted.",
                        error
                );
            } catch (TimeoutException error) {
                workerResult.cancel(true);
                throw new IllegalStateException(
                        "Thread-access experiment timed out.",
                        error
                );
            } catch (ExecutionException error) {
                throw new IllegalStateException(
                        "Thread-access worker failed unexpectedly.",
                        error.getCause()
                );
            }

            Map<String, Object> result =
                    new LinkedHashMap<>(workerEvidence);
            result.put("ownerThread", ownerThread);
            result.put(
                    "confinedAccessibleByOwner",
                    confined.isAccessibleBy(Thread.currentThread())
            );

            return result;
        }
    }

    public Map<String, Object> structOffsetDemo() {
        StructLayout layout =
                MemoryLayout.structLayout(
                        ValueLayout.JAVA_BYTE.withName("kind"),
                        MemoryLayout.paddingLayout(3),
                        ValueLayout.JAVA_INT.withName("value")
                );

        MemoryLayout.PathElement kindPath =
                MemoryLayout.PathElement.groupElement("kind");
        MemoryLayout.PathElement valuePath =
                MemoryLayout.PathElement.groupElement("value");

        long kindOffset = layout.byteOffset(kindPath);
        long valueOffset = layout.byteOffset(valuePath);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(layout);
            segment.set(ValueLayout.JAVA_BYTE, kindOffset, (byte) 7);
            segment.set(ValueLayout.JAVA_INT, valueOffset, 123);

            return map(
                    "layoutByteSize", layout.byteSize(),
                    "layoutByteAlignment", layout.byteAlignment(),
                    "kindOffset", kindOffset,
                    "valueOffset", valueOffset,
                    "selectedValueByteSize",
                    layout.select(valuePath).byteSize(),
                    "kind",
                    Byte.toUnsignedInt(
                            segment.get(
                                    ValueLayout.JAVA_BYTE,
                                    kindOffset
                            )
                    ),
                    "value",
                    segment.get(
                            ValueLayout.JAVA_INT,
                            valueOffset
                    )
            );
        }
    }

    public Map<String, Object> nativeStrlenDemo() {
        Linker linker;

        try {
            linker = Linker.nativeLinker();
        } catch (UnsupportedOperationException error) {
            return map(
                    "linkerAvailable", false,
                    "symbol", "strlen",
                    "symbolAvailable", false,
                    "platform",
                    System.getProperty("os.name") + " / " +
                            System.getProperty("os.arch"),
                    "conclusion",
                    "The current JVM/platform does not provide the native linker."
            );
        }

        Optional<MemorySegment> symbol =
                linker.defaultLookup().find("strlen");

        if (symbol.isEmpty()) {
            return map(
                    "linkerAvailable", true,
                    "symbol", "strlen",
                    "symbolAvailable", false,
                    "platform",
                    System.getProperty("os.name") + " / " +
                            System.getProperty("os.arch"),
                    "conclusion",
                    "The native linker's default lookup does not expose strlen on this platform."
            );
        }

        ValueLayout sizeTLayout =
                ValueLayout.ADDRESS.byteSize() == Long.BYTES
                        ? ValueLayout.JAVA_LONG
                        : ValueLayout.JAVA_INT;

        FunctionDescriptor descriptor =
                FunctionDescriptor.of(
                        sizeTLayout,
                        ValueLayout.ADDRESS
                );

        MethodHandle strlen =
                linker.downcallHandle(
                        symbol.orElseThrow(),
                        descriptor
                );

        String input = "native-interop";

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeString =
                    arena.allocateUtf8String(input);

            long length;

            if (sizeTLayout.carrier() == long.class) {
                length =
                        (long) strlen.invokeExact(
                                nativeString
                        );
            } else {
                int rawLength =
                        (int) strlen.invokeExact(
                                nativeString
                        );
                length =
                        Integer.toUnsignedLong(rawLength);
            }

            return map(
                    "linkerAvailable", true,
                    "symbol", "strlen",
                    "symbolAvailable", true,
                    "input", input,
                    "expectedUtf8ByteLength",
                    input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                    "nativeResult", length,
                    "sizeTCarrier",
                    sizeTLayout.carrier().getSimpleName(),
                    "platform",
                    System.getProperty("os.name") + " / " +
                    System.getProperty("os.arch")
            );
        } catch (Error error) {
            throw error;
        } catch (Throwable error) {
            throw new IllegalStateException(
                    "Native strlen experiment failed unexpectedly.",
                    error
            );
        }
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (int index = 0; index < pairs.length; index += 2) {
            result.put(
                    (String) pairs[index],
                    pairs[index + 1]
            );
        }

        return result;
    }
}

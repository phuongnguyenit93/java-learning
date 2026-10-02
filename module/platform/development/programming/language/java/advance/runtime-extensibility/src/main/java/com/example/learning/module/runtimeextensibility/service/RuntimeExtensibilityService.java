package com.example.learning.module.runtimeextensibility.service;

import com.example.learning.module.runtimeextensibility.experiment.PluginContractMarker;
import com.example.learning.module.runtimeextensibility.experiment.ProviderConstructionProbe;
import com.example.learning.module.runtimeextensibility.spi.ReportExporter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RuntimeExtensibilityService {

    private static final String MARKER_CLASS_NAME =
            PluginContractMarker.class.getName();

    private static final int HOST_PLUGIN_CONTRACT_VERSION = 1;

    public Map<String, Object> serviceLoaderDemo() {
        ProviderConstructionProbe.reset();

        try {
            ServiceLoader<ReportExporter> loader =
                    ServiceLoader.load(
                            ReportExporter.class,
                            ReportExporter.class.getClassLoader()
                    );

            int beforeTraversal =
                    ProviderConstructionProbe.count();

            Iterator<ReportExporter> firstTraversal =
                    loader.iterator();

            if (!firstTraversal.hasNext()) {
                throw new IllegalStateException(
                        "No ReportExporter provider was discovered."
                );
            }

            ReportExporter firstProvider =
                    firstTraversal.next();

            int afterFirstProvider =
                    ProviderConstructionProbe.count();

            ReportExporter cachedProvider =
                    loader.iterator().next();

            int afterCachedTraversal =
                    ProviderConstructionProbe.count();

            loader.reload();

            ReportExporter providerAfterReload =
                    loader.iterator().next();

            int afterReloadTraversal =
                    ProviderConstructionProbe.count();

            return map(
                    "beforeTraversalConstructionCount", beforeTraversal,
                    "firstProviderType", firstProvider.getClass().getName(),
                    "afterFirstProviderConstructionCount", afterFirstProvider,
                    "sameInstanceReturnedFromCache", firstProvider == cachedProvider,
                    "afterCachedTraversalConstructionCount", afterCachedTraversal,
                    "providerAfterReloadType", providerAfterReload.getClass().getName(),
                    "sameInstanceAfterReload", firstProvider == providerAfterReload,
                    "afterReloadTraversalConstructionCount", afterReloadTraversal
            );
        } finally {
            ProviderConstructionProbe.clear();
        }
    }

    public Map<String, Object> providerSelectionDemo() {
        ProviderConstructionProbe.reset();

        try {
            ServiceLoader<ReportExporter> loader =
                    ServiceLoader.load(
                            ReportExporter.class,
                            ReportExporter.class.getClassLoader()
                    );

            List<ReportExporter> providers =
                    loader.stream()
                            .map(ServiceLoader.Provider::get)
                            .toList();

            List<String> discoveredFormats =
                    providers.stream()
                            .map(ReportExporter::format)
                            .toList();

            List<String> discoveredTypes =
                    providers.stream()
                            .map(provider -> provider.getClass().getName())
                            .toList();

            ReportExporter selected =
                    providers.stream()
                            .filter(provider -> "csv".equals(provider.format()))
                            .findFirst()
                            .orElseThrow(() ->
                                    new IllegalStateException(
                                            "CSV provider was not discovered."
                                    )
                            );

            return map(
                    "discoveredFormats", discoveredFormats,
                    "discoveredTypes", discoveredTypes,
                    "providerConstructionCount", ProviderConstructionProbe.count(),
                    "selectionRule", "format == csv",
                    "selectedFormat", selected.format(),
                    "selectedType", selected.getClass().getName(),
                    "exportResult", selected.export("quarterly-report")
            );
        } finally {
            ProviderConstructionProbe.clear();
        }
    }

    public Map<String, Object> classLoaderIdentityDemo() {
        byte[] classBytes =
                readMarkerClassBytes();

        IsolatedClassLoader loaderA =
                new IsolatedClassLoader(
                        MARKER_CLASS_NAME,
                        classBytes
                );

        IsolatedClassLoader loaderB =
                new IsolatedClassLoader(
                        MARKER_CLASS_NAME,
                        classBytes
                );

        try {
            Class<?> classA =
                    loaderA.loadClass(MARKER_CLASS_NAME);

            Class<?> classB =
                    loaderB.loadClass(MARKER_CLASS_NAME);

            Object instanceA =
                    classA.getDeclaredConstructor().newInstance();

            boolean castRejected = false;

            try {
                classB.cast(instanceA);
            } catch (ClassCastException expected) {
                castRejected = true;
            }

            return map(
                    "binaryNameA", classA.getName(),
                    "binaryNameB", classB.getName(),
                    "sameBinaryName", classA.getName().equals(classB.getName()),
                    "sameClassObject", classA == classB,
                    "loaderAIdentity", loaderIdentity(classA.getClassLoader()),
                    "loaderBIdentity", loaderIdentity(classB.getClassLoader()),
                    "aAssignableFromB", classA.isAssignableFrom(classB),
                    "bAssignableFromA", classB.isAssignableFrom(classA),
                    "crossLoaderCastRejected", castRejected
            );
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                    "ClassLoader identity experiment failed.",
                    error
            );
        }
    }

    public Map<String, Object> pluginReplacementDemo() {
        ManagedPluginGeneration generationOne =
                new ManagedPluginGeneration(
                        "v1",
                        HOST_PLUGIN_CONTRACT_VERSION
                );

        ManagedPluginGeneration generationTwo =
                new ManagedPluginGeneration(
                        "v2",
                        HOST_PLUGIN_CONTRACT_VERSION
                );

        AtomicReference<ManagedPluginGeneration> active =
                new AtomicReference<>();

        ManagedPluginGeneration.BlockingWork oldInFlightWork =
                null;

        Map<String, Object> result =
                new LinkedHashMap<>();

        try {
            generationOne.initialize();
            generationOne.start();
            active.set(generationOne);

            oldInFlightWork =
                    generationOne.startBlockingWork(
                            "request-before-switch"
                    );

            result.put(
                    "activeBeforeReplacement",
                    active.get().id()
            );

            result.put(
                    "oldGenerationInFlightAtPrepare",
                    generationOne.inFlightCount()
            );

            boolean replacementCompatible =
                    generationTwo.isCompatibleWith(
                            HOST_PLUGIN_CONTRACT_VERSION
                    );

            if (!replacementCompatible) {
                throw new IllegalStateException(
                        "Replacement plugin is not compatible with the host contract."
                );
            }

            generationTwo.initialize();
            generationTwo.start();

            boolean replacementHealthy =
                    generationTwo.healthCheck();

            if (!replacementHealthy) {
                throw new IllegalStateException(
                        "Replacement plugin failed its health check."
                );
            }

            String replacementStateBeforeSwitch =
                    generationTwo.state().name();

            boolean switched =
                    active.compareAndSet(
                            generationOne,
                            generationTwo
                    );

            if (!switched) {
                throw new IllegalStateException(
                        "Active plugin changed unexpectedly during the experiment."
                );
            }

            String replacementExecutionResult =
                    active.get().execute(
                            "request-after-switch"
                    );

            generationOne.beginDrain();

            int oldInFlightBeforeDrain =
                    generationOne.inFlightCount();

            boolean oldAcceptingWorkAfterDrain =
                    generationOne.acceptingWork();

            String oldInFlightResult =
                    oldInFlightWork.completeAndAwait();

            int oldInFlightAfterDrain =
                    generationOne.inFlightCount();

            generationOne.stop();

            result.put(
                    "replacementCompatible",
                    replacementCompatible
            );

            result.put(
                    "replacementHealthCheckPassed",
                    replacementHealthy
            );

            result.put(
                    "replacementStateBeforeSwitch",
                    replacementStateBeforeSwitch
            );

            result.put(
                    "atomicSwitchSucceeded",
                    switched
            );

            result.put(
                    "activeAfterReplacement",
                    active.get().id()
            );

            result.put(
                    "replacementExecutionResult",
                    replacementExecutionResult
            );

            result.put(
                    "oldGenerationInFlightBeforeDrain",
                    oldInFlightBeforeDrain
            );

            result.put(
                    "oldGenerationAcceptingWorkAfterDrain",
                    oldAcceptingWorkAfterDrain
            );

            result.put(
                    "oldGenerationInFlightAfterDrain",
                    oldInFlightAfterDrain
            );

            result.put(
                    "oldInFlightResult",
                    oldInFlightResult
            );

            result.put(
                    "oldGenerationState",
                    generationOne.state().name()
            );

            result.put(
                    "oldGenerationWorkerTerminated",
                    generationOne.workerTerminated()
            );

            result.put(
                    "oldGenerationEvents",
                    generationOne.events()
            );

            result.put(
                    "newGenerationEventsBeforeRequestCleanup",
                    generationTwo.events()
            );

            result.put(
                    "newGenerationActiveBeforeRequestCleanup",
                    generationTwo.state() == PluginState.ACTIVE
            );

            result.put(
                    "newGenerationWorkerTerminatedBeforeRequestCleanup",
                    generationTwo.workerTerminated()
            );
        } finally {
            if (oldInFlightWork != null) {
                oldInFlightWork.completeQuietly();
            }

            generationOne.stopQuietly();
            generationTwo.stopQuietly();
        }

        result.put(
                "newGenerationStateAfterRequestCleanup",
                generationTwo.state().name()
        );

        result.put(
                "newGenerationWorkerTerminatedAfterRequestCleanup",
                generationTwo.workerTerminated()
        );

        return result;
    }

    private static byte[] readMarkerClassBytes() {
        String resourceName =
                MARKER_CLASS_NAME.replace('.', '/') + ".class";

        ClassLoader sourceLoader =
                PluginContractMarker.class.getClassLoader();

        try (
                InputStream input =
                        sourceLoader.getResourceAsStream(resourceName)
        ) {
            if (input == null) {
                throw new IllegalStateException(
                        "Unable to read marker class bytes: " + resourceName
                );
            }

            return input.readAllBytes();
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Unable to read marker class bytes.",
                    error
            );
        }
    }

    private static String loaderIdentity(
            ClassLoader loader
    ) {
        return loader.getClass().getName()
                + "@"
                + Integer.toHexString(System.identityHashCode(loader));
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

    private enum PluginState {
        NEW,
        INITIALIZED,
        ACTIVE,
        DRAINING,
        STOPPED
    }

    private static final class ManagedPluginGeneration {

        private final String id;

        private final int contractVersion;

        private final List<String> events =
                new ArrayList<>();

        private final AtomicInteger inFlight =
                new AtomicInteger();

        private PluginState state =
                PluginState.NEW;

        private ExecutorService worker;

        private boolean acceptingWork;

        private ManagedPluginGeneration(
                String id,
                int contractVersion
        ) {
            this.id = id;
            this.contractVersion = contractVersion;
            this.events.add("created");
        }

        private String id() {
            return id;
        }

        private PluginState state() {
            return state;
        }

        private List<String> events() {
            return List.copyOf(events);
        }

        private int inFlightCount() {
            return inFlight.get();
        }

        private boolean acceptingWork() {
            return acceptingWork;
        }

        private boolean workerTerminated() {
            return worker == null || worker.isTerminated();
        }

        private boolean isCompatibleWith(
                int hostContractVersion
        ) {
            boolean compatible =
                    contractVersion == hostContractVersion;

            events.add(
                    "compatibility-validated:" + compatible
            );

            return compatible;
        }

        private void initialize() {
            requireState(PluginState.NEW);
            state = PluginState.INITIALIZED;
            events.add("initialized");
        }

        private void start() {
            requireState(PluginState.INITIALIZED);

            worker =
                    Executors.newSingleThreadExecutor(
                            runnable -> {
                                Thread thread =
                                        new Thread(
                                                runnable,
                                                "runtime-extensibility-" + id
                                        );

                                thread.setDaemon(false);
                                return thread;
                            }
                    );

            acceptingWork = true;
            state = PluginState.ACTIVE;
            events.add("active");
        }

        private boolean healthCheck() {
            requireState(PluginState.ACTIVE);

            String result =
                    submitAndAwait(
                            "health-check",
                            false
                    );

            boolean healthy =
                    (id + ":health-check").equals(result);

            events.add(
                    "health-checked:" + healthy
            );

            return healthy;
        }

        private String execute(
                String workName
        ) {
            requireAcceptingWork();

            return submitAndAwait(
                    workName,
                    true
            );
        }

        private BlockingWork startBlockingWork(
                String workName
        ) {
            requireAcceptingWork();

            CountDownLatch started =
                    new CountDownLatch(1);

            CountDownLatch release =
                    new CountDownLatch(1);

            Future<String> future =
                    worker.submit(
                            () -> {
                                inFlight.incrementAndGet();
                                started.countDown();

                                try {
                                    if (!release.await(2, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                                "Timed out waiting to release plugin work."
                                        );
                                    }

                                    return id + ":" + workName;
                                } finally {
                                    inFlight.decrementAndGet();
                                }
                            }
                    );

            awaitLatch(
                    started,
                    "Timed out waiting for plugin work to start."
            );

            events.add(
                    "work-started:" + workName
            );

            return new BlockingWork(
                    release,
                    future
            );
        }

        private void beginDrain() {
            requireState(PluginState.ACTIVE);
            acceptingWork = false;
            state = PluginState.DRAINING;
            events.add("draining");
        }

        private void stop() {
            if (state == PluginState.STOPPED) {
                return;
            }

            acceptingWork = false;

            if (worker != null) {
                worker.shutdown();

                try {
                    if (!worker.awaitTermination(2, TimeUnit.SECONDS)) {
                        worker.shutdownNow();

                        if (!worker.awaitTermination(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Plugin worker did not terminate."
                            );
                        }
                    }
                } catch (InterruptedException error) {
                    worker.shutdownNow();
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Interrupted while stopping plugin worker.",
                            error
                    );
                }
            }

            state = PluginState.STOPPED;
            events.add("stopped");
        }

        private void stopQuietly() {
            try {
                stop();
                return;
            } catch (RuntimeException ignored) {
                acceptingWork = false;
            }

            if (worker != null && !worker.isTerminated()) {
                worker.shutdownNow();

                try {
                    if (!worker.awaitTermination(2, TimeUnit.SECONDS)) {
                        events.add("cleanup-timeout");
                        return;
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    events.add("cleanup-interrupted");
                    return;
                }
            }

            state = PluginState.STOPPED;

            if (events.isEmpty() || !"stopped".equals(events.get(events.size() - 1))) {
                events.add("stopped");
            }
        }

        private String submitAndAwait(
                String workName,
                boolean recordEvent
        ) {
            Future<String> future =
                    worker.submit(
                            () -> {
                                inFlight.incrementAndGet();

                                try {
                                    return id + ":" + workName;
                                } finally {
                                    inFlight.decrementAndGet();
                                }
                            }
                    );

            String result =
                    awaitFuture(
                            future,
                            "Timed out waiting for plugin work."
                    );

            if (recordEvent) {
                events.add(
                        "work-completed:" + workName
                );
            }

            return result;
        }

        private void requireAcceptingWork() {
            requireState(PluginState.ACTIVE);

            if (!acceptingWork) {
                throw new IllegalStateException(
                        "Plugin " + id + " is not accepting new work."
                );
            }
        }

        private void requireState(
                PluginState expected
        ) {
            if (state != expected) {
                throw new IllegalStateException(
                        "Plugin "
                                + id
                                + " expected state "
                                + expected
                                + " but was "
                                + state
                );
            }
        }

        private static void awaitLatch(
                CountDownLatch latch,
                String timeoutMessage
        ) {
            try {
                if (!latch.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            timeoutMessage
                    );
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while waiting for plugin work.",
                        error
                );
            }
        }

        private static String awaitFuture(
                Future<String> future,
                String timeoutMessage
        ) {
            try {
                return future.get(2, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while waiting for plugin work.",
                        error
                );
            } catch (ExecutionException error) {
                throw new IllegalStateException(
                        "Plugin work failed.",
                        error.getCause()
                );
            } catch (TimeoutException error) {
                future.cancel(true);
                throw new IllegalStateException(
                        timeoutMessage,
                        error
                );
            }
        }

        private static final class BlockingWork {

            private final CountDownLatch release;

            private final Future<String> future;

            private boolean completed;

            private BlockingWork(
                    CountDownLatch release,
                    Future<String> future
            ) {
                this.release = release;
                this.future = future;
            }

            private String completeAndAwait() {
                release.countDown();

                String result =
                        awaitFuture(
                                future,
                                "Timed out draining old plugin work."
                        );

                completed = true;
                return result;
            }

            private void completeQuietly() {
                if (completed) {
                    return;
                }

                release.countDown();

                try {
                    awaitFuture(
                            future,
                            "Timed out cleaning up old plugin work."
                    );
                } catch (RuntimeException ignored) {
                    future.cancel(true);
                }

                completed = true;
            }
        }
    }

    private static final class IsolatedClassLoader extends ClassLoader {

        private final String isolatedClassName;

        private final byte[] isolatedClassBytes;

        private IsolatedClassLoader(
                String isolatedClassName,
                byte[] isolatedClassBytes
        ) {
            super(ClassLoader.getPlatformClassLoader());
            this.isolatedClassName = isolatedClassName;
            this.isolatedClassBytes = isolatedClassBytes.clone();
        }

        @Override
        protected Class<?> loadClass(
                String name,
                boolean resolve
        ) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (isolatedClassName.equals(name)) {
                    Class<?> loaded =
                            findLoadedClass(name);

                    if (loaded == null) {
                        loaded =
                                defineClass(
                                        name,
                                        isolatedClassBytes,
                                        0,
                                        isolatedClassBytes.length
                                );
                    }

                    if (resolve) {
                        resolveClass(loaded);
                    }

                    return loaded;
                }

                return super.loadClass(name, resolve);
            }
        }
    }
}

package com.example.learning.casestudy.runtime;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs source examples with JVM-global/ClassLoader-global effects in a fresh child VM.
 * The example itself stays inside its original GroupService.executeCaseN method.
 */
public final class CaseStudyJvmIsolation {
    private static final String CHILD_PROPERTY = "learning.step8.child";
    private static final int OUTPUT_LIMIT_BYTES = 1_048_576;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Semaphore RUNNING = new Semaphore(4);

    private CaseStudyJvmIsolation() {
    }

    public static boolean isChildProcess() {
        return Boolean.getBoolean(CHILD_PROPERTY);
    }

    public static void execute(Class<?> groupService, String methodName) {
        String className = groupService.getName();
        if (!className.matches("com\\.example\\.learning\\.casestudy\\.service\\.Group[0-9]+Service")
                || !methodName.matches("executeCase[0-9]+")) {
            throw new IllegalArgumentException("Unsupported isolated example target");
        }
        if (isChildProcess()) {
            throw new IllegalStateException("Recursive STEP8 JVM dispatch forbidden");
        }
        if (!RUNNING.tryAcquire()) {
            throw new IllegalStateException("Too many simultaneous STEP8 child JVMs");
        }
        try {
            run(className, methodName);
        } finally {
            RUNNING.release();
        }
    }

    private static void run(String className, String methodName) {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String cp = System.getProperty("java.class.path");
        if (cp == null || cp.isBlank()) {
            throw new IllegalStateException("Missing JVM classpath for STEP8 isolation");
        }
        List<String> args = new ArrayList<>();
        args.add(java);
        args.add("-D" + CHILD_PROPERTY + "=true");
        if (cp.indexOf(File.pathSeparatorChar) < 0 && cp.toLowerCase().endsWith(".jar")) {
            // Boot 3.3 repackaged JAR: PropertiesLauncher exposes BOOT-INF/classes and libs.
            args.add("-Dloader.main=" + CaseStudyJvmBootstrap.class.getName());
            args.add("-cp");
            args.add(cp);
            args.add("org.springframework.boot.loader.launch.PropertiesLauncher");
        } else {
            args.add("-cp");
            args.add(cp);
            args.add(CaseStudyJvmBootstrap.class.getName());
        }
        args.add(className);
        args.add(methodName);
        Process child = null;
        ExecutorService reader = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "step8-child-output");
            thread.setDaemon(true);
            return thread;
        });
        try {
            child = new ProcessBuilder(args).redirectErrorStream(true).start();
            Process process = child;
            Future<String> collected = reader.submit(() -> drain(process.getInputStream()));
            if (!child.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                child.destroyForcibly();
                child.waitFor(5, TimeUnit.SECONDS);
                throw new IllegalStateException("STEP8 child JVM timed out after " + TIMEOUT);
            }
            String output = collected.get(5, TimeUnit.SECONDS);
            if (child.exitValue() != 0) {
                throw new IllegalStateException(
                        "STEP8 child JVM failed (exit " + child.exitValue() + "): " + output);
            }
            System.out.print(output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("STEP8 child JVM interrupted", e);
        } catch (IOException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException("STEP8 child JVM execution failed", e);
        } finally {
            if (child != null && child.isAlive()) {
                child.destroyForcibly();
            }
            reader.shutdownNow();
        }
    }

    private static String drain(InputStream stream) throws IOException {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            int remaining = OUTPUT_LIMIT_BYTES - captured.size();
            if (remaining > 0) {
                captured.write(buffer, 0, Math.min(count, remaining));
            }
        }
        return captured.toString(StandardCharsets.UTF_8);
    }
}

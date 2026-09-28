package com.example.learning.module.context.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ThreadLocalService {

    private static final ThreadLocal<String> LOCAL_CONTEXT = new ThreadLocal<>();
    private static final InheritableThreadLocal<String> INHERITABLE_CONTEXT = new InheritableThreadLocal<>();

    public Map<String, Object> isolationDemo() throws InterruptedException {
        AtomicReference<String> threadAValue = new AtomicReference<>();
        AtomicReference<String> threadBValue = new AtomicReference<>();

        Thread threadA = new Thread(() -> useLocalContext("REQ-A", threadAValue), "context-a");
        Thread threadB = new Thread(() -> useLocalContext("REQ-B", threadBValue), "context-b");
        try {
            threadA.start();
            threadB.start();
            joinOrFail(threadA);
            joinOrFail(threadB);
        } finally {
            cleanup(threadA);
            cleanup(threadB);
        }

        return map(
                "threadA", threadAValue.get(),
                "threadB", threadBValue.get(),
                "callerValue", LOCAL_CONTEXT.get()
        );
    }

    public Map<String, Object> inheritanceDemo() throws InterruptedException {
        AtomicReference<String> childValue = new AtomicReference<>();
        INHERITABLE_CONTEXT.set("PARENT-A");

        Thread child = new Thread(() -> childValue.set(INHERITABLE_CONTEXT.get()), "inheritable-child");
        INHERITABLE_CONTEXT.set("PARENT-B");

        try {
            child.start();
            joinOrFail(child);
            return map(
                    "valueAtChildConstruction", "PARENT-A",
                    "parentBeforeChildStart", INHERITABLE_CONTEXT.get(),
                    "childObserved", childValue.get()
            );
        } finally {
            INHERITABLE_CONTEXT.remove();
            cleanup(child);
        }
    }

    public Map<String, Object> poolReuseProblemDemo() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            INHERITABLE_CONTEXT.set("USER_A");
            Future<String> first = pool.submit(INHERITABLE_CONTEXT::get);
            String firstValue = first.get(2, TimeUnit.SECONDS);

            INHERITABLE_CONTEXT.set("USER_B");
            Future<String> second = pool.submit(INHERITABLE_CONTEXT::get);
            String secondValue = second.get(2, TimeUnit.SECONDS);

            return map(
                    "task1", firstValue,
                    "task2", secondValue,
                    "parentAtSecondSubmit", "USER_B",
                    "staleContextObserved", "USER_A".equals(secondValue)
            );
        } finally {
            INHERITABLE_CONTEXT.remove();
            shutdownAndAwait(pool);
        }
    }

    public Map<String, Object> explicitPropagationDemo() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            pool.submit(() -> null).get(2, TimeUnit.SECONDS); // tạo worker trước khi set context
            LOCAL_CONTEXT.set("USER_B");

            Callable<String> propagated = propagate(LOCAL_CONTEXT::get);
            String observed = pool.submit(propagated).get(2, TimeUnit.SECONDS);

            return map(
                    "capturedContext", "USER_B",
                    "workerObserved", observed,
                    "propagationWorked", "USER_B".equals(observed)
            );
        } finally {
            LOCAL_CONTEXT.remove();
            shutdownAndAwait(pool);
        }
    }

    private static void useLocalContext(String value, AtomicReference<String> observed) {
        LOCAL_CONTEXT.set(value);
        try {
            observed.set(LOCAL_CONTEXT.get());
        } finally {
            LOCAL_CONTEXT.remove();
        }
    }

    private static <T> Callable<T> propagate(Callable<T> task) {
        String captured = LOCAL_CONTEXT.get();
        return () -> {
            String previous = LOCAL_CONTEXT.get();
            if (captured == null) {
                LOCAL_CONTEXT.remove();
            } else {
                LOCAL_CONTEXT.set(captured);
            }
            try {
                return task.call();
            } finally {
                if (previous == null) {
                    LOCAL_CONTEXT.remove();
                } else {
                    LOCAL_CONTEXT.set(previous);
                }
            }
        };
    }

    private static void joinOrFail(Thread worker) throws InterruptedException {
        worker.join(2_000);
        if (worker.isAlive()) {
            throw new IllegalStateException("Worker không kết thúc đúng thời gian dự kiến: " + worker.getName());
        }
    }

    private static void cleanup(Thread worker) throws InterruptedException {
        if (!worker.isAlive()) return;
        worker.interrupt();
        worker.join(2_000);
        if (worker.isAlive()) {
            throw new IllegalStateException("Worker vẫn còn sống sau cleanup: " + worker.getName());
        }
    }

    private static void shutdownAndAwait(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Executor vẫn còn sống sau cleanup.");
        }
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }
}

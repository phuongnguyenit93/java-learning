package com.example.learning.module.async.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AsyncService {

    public Map<String, Object> futureDemo() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch allowCompletion = new CountDownLatch(1);

        try {
            Callable<String> task = () -> {
                allowCompletion.await();
                return "PAYMENT_OK";
            };

            Future<String> future = executor.submit(task);
            boolean doneBeforeRelease = future.isDone();
            allowCompletion.countDown();
            String result = future.get(2, TimeUnit.SECONDS);

            return map(
                    "doneBeforeRelease", doneBeforeRelease,
                    "result", result,
                    "doneAfterGet", future.isDone()
            );
        } finally {
            allowCompletion.countDown();
            executor.shutdownNow();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Future demo executor không terminate sau cleanup.");
            }
        }
    }

    public Map<String, Object> futureCancellationDemo() throws InterruptedException {
        return map(
                "cancelWithoutInterrupt", cancellationSample(false),
                "cancelWithInterrupt", cancellationSample(true)
        );
    }

    private Map<String, Object> cancellationSample(boolean mayInterruptIfRunning) throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        Future<String> future = executor.submit(() -> {
            started.countDown();
            try {
                release.await();
                return "DONE";
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                return "INTERRUPTED";
            } finally {
                finished.countDown();
            }
        });

        try {
            if (!started.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Future cancellation worker không start đúng thời gian dự kiến.");
            }

            boolean cancelAccepted = future.cancel(mayInterruptIfRunning);
            boolean taskStillBlockedAfterCancel = finished.getCount() != 0;

            if (mayInterruptIfRunning) {
                if (!finished.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("cancel(true) không làm task interruptible kết thúc đúng hạn.");
                }
            } else {
                release.countDown();
                if (!finished.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Task cancel(false) không kết thúc sau khi release.");
                }
            }

            boolean getThrowsCancellationException = false;
            try {
                future.get();
            } catch (CancellationException expected) {
                getThrowsCancellationException = true;
            } catch (ExecutionException e) {
                throw new IllegalStateException("Future cancellation demo thất bại ngoài dự kiến.", e);
            }

            return map(
                    "mayInterruptIfRunning", mayInterruptIfRunning,
                    "cancelAccepted", cancelAccepted,
                    "futureCancelled", future.isCancelled(),
                    "workerInterrupted", interrupted.get(),
                    "taskStillBlockedImmediatelyAfterCancel", taskStillBlockedAfterCancel,
                    "getThrowsCancellationException", getThrowsCancellationException,
                    "taskEventuallyFinished", finished.getCount() == 0
            );
        } finally {
            release.countDown();
            executor.shutdownNow();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Future cancellation executor không terminate sau cleanup.");
            }
        }
    }

    public CompletableFuture<Map<String, Object>> pipelineDemo() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<String> stageThreads = Collections.synchronizedList(new ArrayList<>());

        CompletableFuture<Double> exchangeRate = CompletableFuture.supplyAsync(() -> {
            stageThreads.add("rate:" + Thread.currentThread().getName());
            sleep(40);
            return 25_000.0;
        }, executor);

        CompletableFuture<Double> price = CompletableFuture
                .supplyAsync(() -> {
                    stageThreads.add("order:" + Thread.currentThread().getName());
                    return "ORDER-001";
                }, executor)
                .thenComposeAsync(orderId -> CompletableFuture.supplyAsync(() -> {
                    stageThreads.add("price:" + Thread.currentThread().getName());
                    return 100.0;
                }, executor), executor);

        CompletableFuture<Map<String, Object>> result = price
                .thenCombineAsync(exchangeRate, (usd, rate) -> {
                    stageThreads.add("combine:" + Thread.currentThread().getName());
                    return usd * rate;
                }, executor)
                .thenApplyAsync(total -> {
                    stageThreads.add("shipping:" + Thread.currentThread().getName());
                    return map(
                            "priceVndBeforeShipping", total,
                            "finalPriceVnd", total + 30_000.0,
                            "stageThreads", List.copyOf(stageThreads)
                    );
                }, executor);

        return result.whenComplete((ignored, error) -> executor.shutdown());
    }

    public CompletableFuture<Map<String, Object>> fastestCompletionDemo() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch allowServerA = new CountDownLatch(1);
        CountDownLatch allowServerB = new CountDownLatch(1);

        CompletableFuture<String> serverA = CompletableFuture.supplyAsync(() -> {
            await(allowServerA);
            return "Server A: 200 USD";
        }, executor);

        CompletableFuture<String> serverB = CompletableFuture.supplyAsync(() -> {
            await(allowServerB);
            return "Server B: 190 USD";
        }, executor);

        allowServerA.countDown();

        CompletableFuture<Map<String, Object>> result = CompletableFuture.anyOf(serverA, serverB)
                .thenCompose(fastest -> {
                    allowServerB.countDown();
                    return serverB.handle((ignored, error) -> {
                        CompletableFuture<String> failedFirst = new CompletableFuture<>();
                        CompletableFuture<String> laterSuccess = new CompletableFuture<>();
                        CompletableFuture<Object> raced = CompletableFuture.anyOf(failedFirst, laterSuccess);

                        failedFirst.completeExceptionally(new IllegalStateException("first completion failed"));
                        boolean firstExceptionalCompletionWon = raced.isCompletedExceptionally();
                        boolean racedThrowsCompletionException = false;
                        try {
                            raced.join();
                        } catch (java.util.concurrent.CompletionException expected) {
                            racedThrowsCompletionException = true;
                        }

                        laterSuccess.complete("LATER_SUCCESS");

                        return map(
                                "fastestCompletion", fastest,
                                "meansBestPrice", false,
                                "allDemoTasksCompletedBeforeReturn", true,
                                "exceptionalCompletionCanWinAnyOf", firstExceptionalCompletionWon,
                                "anyOfJoinThrowsCompletionException", racedThrowsCompletionException,
                                "losingFutureCancelledAutomatically", laterSuccess.isCancelled()
                        );
                    });
                });

        return result.whenComplete((ignored, error) -> {
            allowServerA.countDown();
            allowServerB.countDown();
            executor.shutdown();
        });
    }

    public CompletableFuture<Map<String, Object>> exceptionHandlingDemo() {
        CompletableFuture<String> failed = CompletableFuture.supplyAsync(() -> {
            throw new IllegalStateException("simulated failure");
        });

        return failed.handle((value, error) -> map(
                "success", error == null,
                "value", value,
                "recovered", error != null,
                "fallback", error == null ? value : "FALLBACK"
        ));
    }

    public Map<String, Object> executionVariantDemo() throws Exception {
        String callerThread = Thread.currentThread().getName();
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
                new Thread(runnable, "async-variant-worker")
        );

        try {
            AtomicBoolean nonAsyncRanInline = new AtomicBoolean(false);
            AtomicBoolean asyncUsedConfiguredExecutor = new AtomicBoolean(false);

            CompletableFuture<String> completed = CompletableFuture.completedFuture("ready");
            String nonAsyncThread = completed.thenApply(value -> {
                String current = Thread.currentThread().getName();
                nonAsyncRanInline.set(callerThread.equals(current));
                return current;
            }).get(2, TimeUnit.SECONDS);

            String asyncThread = completed.thenApplyAsync(value -> {
                String current = Thread.currentThread().getName();
                asyncUsedConfiguredExecutor.set("async-variant-worker".equals(current));
                return current;
            }, executor).get(2, TimeUnit.SECONDS);

            return map(
                    "callerThread", callerThread,
                    "thenApplyThread", nonAsyncThread,
                    "thenApplyRanInlineForAlreadyCompletedStage", nonAsyncRanInline.get(),
                    "thenApplyAsyncThread", asyncThread,
                    "thenApplyAsyncUsedConfiguredExecutor", asyncUsedConfiguredExecutor.get(),
                    "differentThreadIsGeneralContract", false
            );
        } finally {
            shutdownAndAwait(executor);
        }
    }

    public Map<String, Object> allOfDemo() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            CompletableFuture<String> inventory = CompletableFuture.supplyAsync(() -> "IN_STOCK", executor);
            CompletableFuture<String> payment = CompletableFuture.supplyAsync(() -> "PAID", executor);
            CompletableFuture<String> shipping = CompletableFuture.supplyAsync(() -> "READY", executor);

            CompletableFuture<Void> all = CompletableFuture.allOf(inventory, payment, shipping);
            all.get(2, TimeUnit.SECONDS);

            CompletableFuture<String> failed = new CompletableFuture<>();
            CompletableFuture<String> successfulSibling = new CompletableFuture<>();
            CompletableFuture<Void> failedAll = CompletableFuture.allOf(failed, successfulSibling);

            failed.completeExceptionally(new IllegalStateException("simulated allOf failure"));
            boolean aggregateCompletedImmediatelyAfterFailure = failedAll.isDone();

            successfulSibling.complete("SUCCESS");
            boolean aggregateCompletedAfterSiblingFinished = failedAll.isDone();

            boolean failureObservedThroughAggregate = false;
            try {
                failedAll.join();
            } catch (java.util.concurrent.CompletionException expected) {
                failureObservedThroughAggregate = true;
            }

            return map(
                    "allCompleted", all.isDone(),
                    "allOfResultType", "Void",
                    "inventory", inventory.join(),
                    "payment", payment.join(),
                    "shipping", shipping.join(),
                    "aggregateCompletedImmediatelyAfterFirstFailure", aggregateCompletedImmediatelyAfterFailure,
                    "aggregateWaitedForRemainingSibling", !aggregateCompletedImmediatelyAfterFailure
                            && aggregateCompletedAfterSiblingFinished,
                    "aggregateExceptionalWhenInputFails", failedAll.isCompletedExceptionally(),
                    "aggregateFailureObserved", failureObservedThroughAggregate,
                    "successfulSiblingCancelledAutomatically", successfulSibling.isCancelled()
            );
        } finally {
            shutdownAndAwait(executor);
        }
    }

    public Map<String, Object> timeoutCancellationDemo() throws InterruptedException {
        return map(
                "orTimeout", completableFutureTimeoutSample(),
                "completableFutureCancelTrue", completableFutureCancellationSample()
        );
    }

    private Map<String, Object> completableFutureTimeoutSample() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean workerInterrupted = new AtomicBoolean(false);

        try {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                started.countDown();
                try {
                    release.await();
                    return "DONE";
                } catch (InterruptedException e) {
                    workerInterrupted.set(true);
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Worker interrupted", e);
                }
            }, executor).orTimeout(50, TimeUnit.MILLISECONDS);

            if (!started.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout demo worker không start đúng thời gian dự kiến.");
            }

            boolean timedOut = false;
            try {
                future.get(1, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                timedOut = e.getCause() instanceof TimeoutException;
            } catch (TimeoutException e) {
                throw new IllegalStateException("CompletableFuture.orTimeout không hoàn thành đúng hạn.", e);
            }

            return map(
                    "futureTimedOut", timedOut,
                    "workerInterruptedByOrTimeout", workerInterrupted.get(),
                    "timeoutAutomaticallyStopsUnderlyingWork", false
            );
        } finally {
            release.countDown();
            shutdownAndAwait(executor);
        }
    }

    private Map<String, Object> completableFutureCancellationSample() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean workerInterrupted = new AtomicBoolean(false);

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            started.countDown();
            try {
                release.await();
                return "DONE";
            } catch (InterruptedException e) {
                workerInterrupted.set(true);
                Thread.currentThread().interrupt();
                return "INTERRUPTED";
            } finally {
                finished.countDown();
            }
        }, executor);

        try {
            if (!started.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("CompletableFuture cancellation worker không start đúng hạn.");
            }

            boolean cancelAccepted = future.cancel(true);
            boolean workerStillRunningAfterCancel = finished.getCount() != 0;
            boolean workerInterruptedByCancel = workerInterrupted.get();
            boolean joinThrowsCancellationException = false;
            try {
                future.join();
            } catch (CancellationException expected) {
                joinThrowsCancellationException = true;
            }

            release.countDown();
            if (!finished.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Underlying CompletableFuture work không kết thúc sau release.");
            }

            return map(
                    "cancelAccepted", cancelAccepted,
                    "futureCancelled", future.isCancelled(),
                    "mayInterruptIfRunningAffectsCompletableFuture", false,
                    "workerInterruptedByCancel", workerInterruptedByCancel,
                    "workerStillRunningAfterCancel", workerStillRunningAfterCancel,
                    "joinThrowsCancellationException", joinThrowsCancellationException,
                    "underlyingWorkEventuallyFinished", finished.getCount() == 0
            );
        } finally {
            release.countDown();
            shutdownAndAwait(executor);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Task bị interrupt", e);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Task bị interrupt", e);
        }
    }

    private static void shutdownAndAwait(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Executor không terminate sau cleanup.");
            }
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

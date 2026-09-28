package com.example.learning.module.coordination.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Exchanger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class CoordinationService {

    private static final Duration THREAD_TIMEOUT = Duration.ofSeconds(2);

    public Map<String, Object> joinDemo() throws InterruptedException {
        AtomicBoolean workerCompleted = new AtomicBoolean(false);
        Thread worker = new Thread(() -> workerCompleted.set(true), "join-worker");

        try {
            worker.start();
            joinOrFail(worker);
        } finally {
            cleanupThreads(worker);
        }

        return map(
                "workerCompletedAfterJoin", workerCompleted.get(),
                "workerState", worker.getState().name()
        );
    }

    public Map<String, Object> waitNotifyDemo() throws InterruptedException {
        Object monitor = new Object();
        BooleanBox ready = new BooleanBox();
        CountDownLatch waitersEntered = new CountDownLatch(3);
        AtomicInteger awakenedBeforeReady = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        List<Thread> waiters = new ArrayList<>();

        try {
            for (int i = 1; i <= 3; i++) {
                Thread waiter = new Thread(() -> {
                    synchronized (monitor) {
                        waitersEntered.countDown();
                        while (!ready.value) {
                            try {
                                monitor.wait();
                                if (!ready.value) {
                                    awakenedBeforeReady.incrementAndGet();
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                        completed.incrementAndGet();
                    }
                }, "wait-notify-waiter-" + i);
                waiters.add(waiter);
                waiter.start();
            }
    
            awaitOrFail(waitersEntered, "Các waiter không vào wait set đúng thời gian dự kiến.");
            for (Thread waiter : waiters) {
                waitForState(waiter, Thread.State.WAITING);
            }
    
            synchronized (monitor) {
                monitor.notify();
            }
            waitUntil(() -> awakenedBeforeReady.get() >= 1, "notify() đánh thức một waiter");
    
            synchronized (monitor) {
                ready.value = true;
                monitor.notifyAll();
            }
    
            joinAll(waiters);
        } finally {
            synchronized (monitor) {
                ready.value = true;
                monitor.notifyAll();
            }
            cleanupThreads(waiters.toArray(Thread[]::new));
        }
        return map(
                "waiterCount", waiters.size(),
                "awakenedByNotifyBeforePredicateTrue", awakenedBeforeReady.get(),
                "completedAfterNotifyAll", completed.get(),
                "allWaitersTerminated", waiters.stream().noneMatch(Thread::isAlive)
        );
    }

    public Map<String, Object> conditionDemo() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Condition dataAvailable = lock.newCondition();
        Condition shutdownRequested = lock.newCondition();
        BooleanBox dataReady = new BooleanBox();
        BooleanBox shutdown = new BooleanBox();
        CountDownLatch waitersStarted = new CountDownLatch(2);
        AtomicBoolean dataWaiterProceeded = new AtomicBoolean(false);
        AtomicBoolean shutdownWaiterProceeded = new AtomicBoolean(false);

        Thread dataWaiter = new Thread(() -> {
            lock.lock();
            try {
                waitersStarted.countDown();
                while (!dataReady.value) {
                    dataAvailable.await();
                }
                dataWaiterProceeded.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "condition-data-waiter");

        Thread shutdownWaiter = new Thread(() -> {
            lock.lock();
            try {
                waitersStarted.countDown();
                while (!shutdown.value) {
                    shutdownRequested.await();
                }
                shutdownWaiterProceeded.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "condition-shutdown-waiter");

        boolean shutdownWaiterStillWaitingAfterDataSignal;
        try {
            dataWaiter.start();
            shutdownWaiter.start();
            awaitOrFail(waitersStarted, "Hai Condition waiter không start đúng thời gian dự kiến.");
            waitForAnyWaiting(dataWaiter);
            waitForAnyWaiting(shutdownWaiter);

            lock.lock();
            try {
                dataReady.value = true;
                dataAvailable.signal();
            } finally {
                lock.unlock();
            }

            waitUntil(dataWaiterProceeded::get, "data waiter được đánh thức bởi dataAvailable");
            shutdownWaiterStillWaitingAfterDataSignal = !shutdownWaiterProceeded.get();

            lock.lock();
            try {
                shutdown.value = true;
                shutdownRequested.signal();
            } finally {
                lock.unlock();
            }

            joinOrFail(dataWaiter);
            joinOrFail(shutdownWaiter);
        } finally {
            lock.lock();
            try {
                dataReady.value = true;
                shutdown.value = true;
                dataAvailable.signalAll();
                shutdownRequested.signalAll();
            } finally {
                lock.unlock();
            }
            cleanupThreads(dataWaiter, shutdownWaiter);
        }
        return map(
                "separateConditionQueues", true,
                "dataWaiterProceeded", dataWaiterProceeded.get(),
                "shutdownWaiterStillWaitingAfterDataSignal", shutdownWaiterStillWaitingAfterDataSignal,
                "shutdownWaiterProceededAfterOwnSignal", shutdownWaiterProceeded.get()
        );
    }

    public Map<String, Object> lockSupportDemo() throws InterruptedException {
        Object blocker = new Object();
        CountDownLatch parked = new CountDownLatch(3);
        AtomicBoolean allowFirst = new AtomicBoolean(false);
        AtomicBoolean allowSecond = new AtomicBoolean(false);
        AtomicBoolean firstResumed = new AtomicBoolean(false);
        AtomicBoolean secondResumed = new AtomicBoolean(false);
        AtomicBoolean interruptedParkReturned = new AtomicBoolean(false);
        AtomicBoolean interruptStatusObservedAfterPark = new AtomicBoolean(false);

        Thread first = new Thread(() -> {
            parked.countDown();
            while (!allowFirst.get()) {
                LockSupport.park(blocker);
            }
            firstResumed.set(true);
        }, "lock-support-first");
        Thread second = new Thread(() -> {
            parked.countDown();
            while (!allowSecond.get()) {
                LockSupport.park(blocker);
            }
            secondResumed.set(true);
        }, "lock-support-second");
        Thread interrupted = new Thread(() -> {
            parked.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                LockSupport.park(blocker);
            }
            interruptedParkReturned.set(true);
            interruptStatusObservedAfterPark.set(Thread.currentThread().isInterrupted());
        }, "lock-support-interrupted");

        boolean firstStillParkedAfterUnparkSecond;
        boolean blockerVisibleWhileParked;
        try {
            first.start();
            second.start();
            interrupted.start();
            awaitOrFail(parked, "Các LockSupport worker không start đúng thời gian dự kiến.");
            waitForAnyWaiting(first);
            waitForAnyWaiting(second);
            waitForAnyWaiting(interrupted);
            waitUntil(() -> LockSupport.getBlocker(first) == blocker,
                    "blocker metadata xuất hiện khi first worker đang park");
            blockerVisibleWhileParked = LockSupport.getBlocker(first) == blocker;

            allowSecond.set(true);
            LockSupport.unpark(second);
            waitUntil(secondResumed::get, "second worker resume sau unpark(second)");
            firstStillParkedAfterUnparkSecond = !firstResumed.get();

            interrupted.interrupt();
            joinOrFail(interrupted);

            allowFirst.set(true);
            LockSupport.unpark(first);
            joinOrFail(first);
            joinOrFail(second);
        } finally {
            allowFirst.set(true);
            allowSecond.set(true);
            LockSupport.unpark(first);
            LockSupport.unpark(second);
            interrupted.interrupt();
            LockSupport.unpark(interrupted);
            cleanupThreads(first, second, interrupted);
        }

        return map(
                "targetedWorkerResumed", secondResumed.get(),
                "otherWorkerStillParked", firstStillParkedAfterUnparkSecond,
                "firstWorkerEventuallyResumed", firstResumed.get(),
                "parkReturnedWhenInterrupted", interruptedParkReturned.get(),
                "interruptStatusObservedAfterPark", interruptStatusObservedAfterPark.get(),
                "parkThrowsInterruptedException", false,
                "blockerVisibleWhileParked", blockerVisibleWhileParked,
                "workersTerminated", !first.isAlive() && !second.isAlive() && !interrupted.isAlive()
        );
    }

    public Map<String, Object> blockingQueueDemo() throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);
        List<String> consumed = java.util.Collections.synchronizedList(new ArrayList<>());

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    queue.put("item-" + i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "queue-producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    consumed.add(queue.take());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "queue-consumer");

        try {
            producer.start();
            consumer.start();
            joinOrFail(producer);
            joinOrFail(consumer);
        } finally {
            cleanupThreads(producer, consumer);
        }

        return map(
                "capacity", 2,
                "consumed", List.copyOf(consumed),
                "queueEmptyAtEnd", queue.isEmpty(),
                "workersTerminated", !producer.isAlive() && !consumer.isAlive()
        );
    }

    public Map<String, Object> blockingQueueVariantsDemo() throws InterruptedException {
        ArrayBlockingQueue<String> arrayQueue = new ArrayBlockingQueue<>(2, true);
        arrayQueue.put("A");
        arrayQueue.put("B");
        boolean arrayOfferWhenFull = arrayQueue.offer("C");

        LinkedBlockingQueue<String> linkedQueue = new LinkedBlockingQueue<>(3);
        linkedQueue.put("L1");
        linkedQueue.put("L2");
        linkedQueue.put("L3");
        List<String> drained = new ArrayList<>();
        int drainedCount = linkedQueue.drainTo(drained, 2);

        PriorityBlockingQueue<PriorityTask> priorityQueue = new PriorityBlockingQueue<>();
        priorityQueue.put(new PriorityTask(3, "promotion"));
        priorityQueue.put(new PriorityTask(1, "otp"));
        priorityQueue.put(new PriorityTask(2, "delivery"));

        List<String> priorityOrder = new ArrayList<>();
        PriorityTask task;
        while ((task = priorityQueue.poll()) != null) {
            priorityOrder.add(task.name());
        }

        return map(
                "arrayBlockingQueueCapacity", 2,
                "arrayBlockingQueueFair", true,
                "arrayOfferWhenFull", arrayOfferWhenFull,
                "linkedBlockingQueueCapacity", 3,
                "drainedCount", drainedCount,
                "drained", drained,
                "linkedQueueRemaining", List.copyOf(linkedQueue),
                "priorityOrder", priorityOrder,
                "priorityQueueIsUnboundedByCapacity", true
        );
    }

    public Map<String, Object> priorityQueueSemanticsDemo() {
        PriorityBlockingQueue<PriorityTask> queue = new PriorityBlockingQueue<>();
        queue.add(new PriorityTask(1, "same-priority-a"));
        queue.add(new PriorityTask(1, "same-priority-b"));
        queue.add(new PriorityTask(0, "higher-priority"));

        List<String> observedOrder = new ArrayList<>();
        PriorityTask task;
        while ((task = queue.poll()) != null) {
            observedOrder.add(task.name());
        }

        return map(
                "observedOrder", observedOrder,
                "higherPriorityComesFirst", "higher-priority".equals(observedOrder.getFirst()),
                "equalPriorityFifoIsContract", false,
                "boundedCapacityBackpressure", false
        );
    }

    public Map<String, Object> synchronousQueueDemo() throws InterruptedException {
        SynchronousQueue<String> queue = new SynchronousQueue<>();
        AtomicReference<String> received = new AtomicReference<>();

        Thread consumer = new Thread(() -> {
            try {
                received.set(queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "handoff-consumer");

        Thread producer = new Thread(() -> {
            try {
                queue.put("payload");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "handoff-producer");

        boolean producerWaitingForConsumer;
        try {
            producer.start();
            waitForAnyWaiting(producer);
            producerWaitingForConsumer = true;
            consumer.start();
            joinOrFail(producer);
            joinOrFail(consumer);
        } finally {
            cleanupThreads(producer, consumer);
        }

        return map(
                "producerWaitedForConsumer", producerWaitingForConsumer,
                "received", received.get(),
                "queueSize", queue.size()
        );
    }

    public Map<String, Object> synchronizersDemo() throws InterruptedException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("countDownLatch", countDownLatchSample());
        result.put("cyclicBarrier", cyclicBarrierSample());
        result.put("semaphore", semaphoreSample());
        result.put("phaser", phaserSample());
        result.put("exchanger", exchangerSample());
        return result;
    }

    public Map<String, Object> synchronizerTimeoutDemo() throws InterruptedException {
        Map<String, Object> result = new LinkedHashMap<>();

        CountDownLatch workerFinished = new CountDownLatch(1);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicBoolean workerContinuedAfterCallerTimeout = new AtomicBoolean(false);
        Thread slowWorker = new Thread(() -> {
            try {
                workerStarted.countDown();
                releaseWorker.await();
                workerContinuedAfterCallerTimeout.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                workerFinished.countDown();
            }
        }, "latch-timeout-worker");
        boolean latchCompletedBeforeTimeout;
        try {
            slowWorker.start();
            awaitOrFail(workerStarted, "Latch worker không start đúng hạn.");
            // Worker chỉ được hoàn thành sau khi caller đã quan sát timeout.
            latchCompletedBeforeTimeout = workerFinished.await(50, java.util.concurrent.TimeUnit.MILLISECONDS);
            releaseWorker.countDown();
            joinOrFail(slowWorker);
        } finally {
            releaseWorker.countDown();
            cleanupThreads(slowWorker);
        }
        result.put("countDownLatch", map(
                "completedBeforeTimeout", latchCompletedBeforeTimeout,
                "workerContinuedAfterCallerTimeout", workerContinuedAfterCallerTimeout.get(),
                "workerTerminatedNormally", !slowWorker.isAlive()
        ));

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicBoolean barrierTimedOut = new AtomicBoolean(false);
        Thread lonelyParticipant = new Thread(() -> {
            try {
                barrier.await(100, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                barrierTimedOut.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException ignored) {
            }
        }, "barrier-timeout-participant");
        try {
            lonelyParticipant.start();
            joinOrFail(lonelyParticipant);
        } finally {
            cleanupThreads(lonelyParticipant);
        }
        boolean barrierBrokenAfterTimeout = barrier.isBroken();
        barrier.reset();
        AtomicInteger nextGenerationPassed = new AtomicInteger();
        Runnable nextGenerationTask = () -> {
            try {
                barrier.await();
                nextGenerationPassed.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException ignored) {
            }
        };
        Thread nextA = new Thread(nextGenerationTask, "barrier-next-a");
        Thread nextB = new Thread(nextGenerationTask, "barrier-next-b");
        try {
            nextA.start();
            nextB.start();
            joinOrFail(nextA);
            joinOrFail(nextB);
        } finally {
            cleanupThreads(nextA, nextB);
        }
        result.put("cyclicBarrier", map(
                "timedOut", barrierTimedOut.get(),
                "brokenAfterTimeout", barrierBrokenAfterTimeout,
                "brokenAfterReset", barrier.isBroken(),
                "nextGenerationPassed", nextGenerationPassed.get()
        ));

        Semaphore semaphore = new Semaphore(2);
        semaphore.acquire(2);
        boolean extraPermitAcquired;
        try {
            extraPermitAcquired = semaphore.tryAcquire(1, 50, java.util.concurrent.TimeUnit.MILLISECONDS);
        } finally {
            semaphore.release(2);
        }
        result.put("semaphore", map(
                "multiPermitAcquire", true,
                "extraPermitAcquiredBeforeTimeout", extraPermitAcquired,
                "availablePermitsAtEnd", semaphore.availablePermits()
        ));

        Phaser phaser = new Phaser(1);
        AtomicInteger phase0Workers = new AtomicInteger();
        AtomicInteger phase1Workers = new AtomicInteger();
        Thread onePhaseWorker = phaserWorker(phaser, "phaser-one-phase", false, phase0Workers, phase1Workers);
        Thread twoPhaseWorker = phaserWorker(phaser, "phaser-two-phase", true, phase0Workers, phase1Workers);
        int phaseBeforeAdvance = phaser.getPhase();
        int phaseAfterAdvance;
        boolean terminatedNormally;
        try {
            onePhaseWorker.start();
            twoPhaseWorker.start();
            phaseAfterAdvance = arriveAndAwaitOrFail(phaser);
            arriveAndAwaitOrFail(phaser);
            phaser.arriveAndDeregister();
            joinOrFail(onePhaseWorker);
            joinOrFail(twoPhaseWorker);
            terminatedNormally = phaser.isTerminated();
        } finally {
            phaser.forceTermination();
            cleanupThreads(onePhaseWorker, twoPhaseWorker);
        }
        result.put("phaser", map(
                "phaseBeforeAdvance", phaseBeforeAdvance,
                "phaseAfterAdvance", phaseAfterAdvance,
                "phase0Workers", phase0Workers.get(),
                "phase1Workers", phase1Workers.get(),
                "terminatedAfterDeregister", terminatedNormally,
                "coordinatorUsedInterruptibleTimedWait", true
        ));

        Exchanger<String> exchanger = new Exchanger<>();
        AtomicBoolean exchangerTimedOut = new AtomicBoolean(false);
        AtomicBoolean exchangerInterrupted = new AtomicBoolean(false);

        Thread timedParticipant = new Thread(() -> {
            try {
                exchanger.exchange("no-partner", 100, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                exchangerTimedOut.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "exchanger-timeout-participant");

        Exchanger<String> interruptibleExchanger = new Exchanger<>();
        Thread interruptedParticipant = new Thread(() -> {
            try {
                interruptibleExchanger.exchange("wait-for-partner");
            } catch (InterruptedException e) {
                exchangerInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        }, "exchanger-interrupt-participant");

        try {
            timedParticipant.start();
            joinOrFail(timedParticipant);

            interruptedParticipant.start();
            waitForAnyWaiting(interruptedParticipant);
            interruptedParticipant.interrupt();
            joinOrFail(interruptedParticipant);
        } finally {
            cleanupThreads(timedParticipant, interruptedParticipant);
        }

        result.put("exchanger", map(
                "timedExchangeWithoutPartnerTimedOut", exchangerTimedOut.get(),
                "blockingExchangeRespondedToInterrupt", exchangerInterrupted.get(),
                "partnerRequiredForSuccessfulExchange", true
        ));

        return result;
    }

    public Map<String, Object> semaphorePermitAccountingDemo() {
        Semaphore semaphore = new Semaphore(1);
        int initialPermits = semaphore.availablePermits();

        semaphore.release();
        int permitsAfterUnmatchedRelease = semaphore.availablePermits();

        boolean acquiredTwice = semaphore.tryAcquire(2);
        if (acquiredTwice) {
            semaphore.release(2);
        }

        return map(
                "initialPermits", initialPermits,
                "permitsAfterUnmatchedRelease", permitsAfterUnmatchedRelease,
                "semaphoreEnforcesOwner", false,
                "twoPermitsCouldBeAcquiredAfterOverRelease", acquiredTwice,
                "rule", "release only permits actually acquired"
        );
    }

    private Map<String, Object> countDownLatchSample() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger completed = new AtomicInteger();
        Thread a = new Thread(() -> { completed.incrementAndGet(); done.countDown(); }, "latch-a");
        Thread b = new Thread(() -> { completed.incrementAndGet(); done.countDown(); }, "latch-b");
        try {
            a.start();
            b.start();
            awaitOrFail(done, "Latch workers không hoàn thành đúng hạn.");
            joinOrFail(a);
            joinOrFail(b);
        } finally {
            cleanupThreads(a, b);
        }
        return map("completed", completed.get(), "count", done.getCount());
    }

    private Map<String, Object> cyclicBarrierSample() throws InterruptedException {
        AtomicInteger barrierActions = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(2, barrierActions::incrementAndGet);
        AtomicInteger passed = new AtomicInteger();
        Runnable task = () -> {
            try {
                barrier.await();
                passed.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException ignored) {
            }
        };
        Thread a = new Thread(task, "barrier-a");
        Thread b = new Thread(task, "barrier-b");
        try {
            a.start();
            b.start();
            joinOrFail(a);
            joinOrFail(b);
        } finally {
            cleanupThreads(a, b);
        }
        return map("passed", passed.get(), "barrierActions", barrierActions.get(), "broken", barrier.isBroken());
    }

    private Map<String, Object> semaphoreSample() throws InterruptedException {
        Semaphore semaphore = new Semaphore(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch firstWaveAcquired = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();

        try {
            for (int i = 0; i < 4; i++) {
                Thread worker = new Thread(() -> {
                    boolean acquired = false;
                    try {
                        semaphore.acquire();
                        acquired = true;
                        int current = active.incrementAndGet();
                        maxActive.accumulateAndGet(current, Math::max);
                        firstWaveAcquired.countDown();
                        await(release);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        if (acquired) {
                            active.decrementAndGet();
                            semaphore.release();
                        }
                    }
                }, "semaphore-" + i);
                workers.add(worker);
                worker.start();
            }
    
            awaitOrFail(firstWaveAcquired, "Không quan sát đủ hai worker acquire semaphore permit.");
            release.countDown();
            joinAll(workers);
        } finally {
            release.countDown();
            cleanupThreads(workers.toArray(Thread[]::new));
        }
        return map("permits", 2, "maxConcurrent", maxActive.get(), "availableAtEnd", semaphore.availablePermits());
    }

    private Map<String, Object> phaserSample() throws InterruptedException {
        Phaser phaser = new Phaser(1);
        AtomicInteger phase0Completed = new AtomicInteger();
        AtomicInteger phase1Completed = new AtomicInteger();
        List<Thread> workers = new ArrayList<>();

        int phaseBeforeAdvance = phaser.getPhase();
        boolean terminatedNormally;
        try {
            for (int i = 0; i < 2; i++) {
                phaser.register();
                Thread worker = new Thread(() -> {
                    phase0Completed.incrementAndGet();
                    phaser.arriveAndAwaitAdvance();
                    phase1Completed.incrementAndGet();
                    phaser.arriveAndDeregister();
                }, "phaser-" + i);
                workers.add(worker);
                worker.start();
            }

            arriveAndAwaitOrFail(phaser);
            phaser.arriveAndDeregister();
            joinAll(workers);
            terminatedNormally = phaser.isTerminated();
        } finally {
            phaser.forceTermination();
            cleanupThreads(workers.toArray(Thread[]::new));
        }
        return map(
                "initialPhase", phaseBeforeAdvance,
                "phase0Completed", phase0Completed.get(),
                "phase1Completed", phase1Completed.get(),
                "terminated", terminatedNormally
        );
    }

    private Thread phaserWorker(
            Phaser phaser,
            String name,
            boolean participateInSecondPhase,
            AtomicInteger phase0Workers,
            AtomicInteger phase1Workers
    ) {
        phaser.register();
        return new Thread(() -> {
            phase0Workers.incrementAndGet();
            if (!participateInSecondPhase) {
                phaser.arriveAndDeregister();
                return;
            }

            phaser.arriveAndAwaitAdvance();
            phase1Workers.incrementAndGet();
            phaser.arriveAndDeregister();
        }, name);
    }

    private Map<String, Object> exchangerSample() throws InterruptedException {
        Exchanger<String> exchanger = new Exchanger<>();
        AtomicReference<String> aReceived = new AtomicReference<>();
        AtomicReference<String> bReceived = new AtomicReference<>();

        Thread a = new Thread(() -> exchange(exchanger, "from-A", aReceived), "exchanger-a");
        Thread b = new Thread(() -> exchange(exchanger, "from-B", bReceived), "exchanger-b");
        try {
            a.start();
            b.start();
            joinOrFail(a);
            joinOrFail(b);
        } finally {
            cleanupThreads(a, b);
        }
        return map("aReceived", aReceived.get(), "bReceived", bReceived.get());
    }

    private static void exchange(Exchanger<String> exchanger, String value, AtomicReference<String> target) {
        try {
            target.set(exchanger.exchange(value));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void waitForState(Thread thread, Thread.State expected) throws InterruptedException {
        long deadline = System.nanoTime() + THREAD_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (thread.getState() == expected) return;
            Thread.sleep(5);
        }
        throw new IllegalStateException("Không quan sát được state " + expected + " của " + thread.getName());
    }

    private static void waitForAnyWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + THREAD_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) return;
            Thread.sleep(5);
        }
        throw new IllegalStateException("Thread không vào waiting state: " + thread.getName());
    }

    private static void joinAll(List<Thread> workers) throws InterruptedException {
        for (Thread worker : workers) joinOrFail(worker);
    }

    private static void joinOrFail(Thread thread) throws InterruptedException {
        thread.join(THREAD_TIMEOUT.toMillis());
        if (thread.isAlive()) {
            thread.interrupt();
            thread.join(THREAD_TIMEOUT.toMillis());
            throw new IllegalStateException("Thread không kết thúc: " + thread.getName());
        }
    }

    private static void cleanupThreads(Thread... threads) {
        boolean interrupted = Thread.interrupted();
        List<String> alive = new ArrayList<>();
        // Gửi cancellation tới tất cả trước khi chờ bất kỳ worker nào.
        for (Thread thread : threads) {
            if (thread != null && thread.isAlive()) {
                thread.interrupt();
                LockSupport.unpark(thread);
            }
        }
        try {
            for (Thread thread : threads) {
                if (thread == null) continue;
                long deadline = System.nanoTime() + THREAD_TIMEOUT.toNanos();
                while (thread.isAlive() && System.nanoTime() < deadline) {
                    try {
                        thread.join(Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                                deadline - System.nanoTime())));
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                if (thread.isAlive()) alive.add(thread.getName());
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
        if (!alive.isEmpty()) throw new IllegalStateException("Worker chưa cleanup: " + alive);
    }

    private static int arriveAndAwaitOrFail(Phaser phaser) throws InterruptedException {
        int phase = phaser.arrive();
        try {
            return phaser.awaitAdvanceInterruptibly(phase, THREAD_TIMEOUT.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Phaser không chuyển phase đúng hạn.", e);
        }
    }

    private static void awaitOrFail(CountDownLatch latch, String description) throws InterruptedException {
        if (!latch.await(THREAD_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(description);
        }
    }

    private static void waitUntil(Check check, String description) throws InterruptedException {
        long deadline = System.nanoTime() + THREAD_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (check.test()) return;
            Thread.sleep(5);
        }
        throw new IllegalStateException("Không đạt trạng thái mong đợi: " + description);
    }

    private static boolean await(CountDownLatch latch) {
        try {
            latch.await();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }

    private static final class BooleanBox {
        private boolean value;
    }

    private record PriorityTask(int priority, String name) implements Comparable<PriorityTask> {
        @Override
        public int compareTo(PriorityTask other) {
            return Integer.compare(priority, other.priority);
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean test();
    }
}

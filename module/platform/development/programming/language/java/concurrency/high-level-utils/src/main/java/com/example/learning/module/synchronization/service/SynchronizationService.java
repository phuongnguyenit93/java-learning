package com.example.learning.module.synchronization.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

@Service
public class SynchronizationService {

    private static final Duration THREAD_TIMEOUT = Duration.ofSeconds(2);

    public Map<String, Object> synchronizedCounter() throws InterruptedException {
        SynchronizedCounter counter = new SynchronizedCounter();
        int workerCount = 4;
        int incrementsPerWorker = 1_000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();

        try {
            for (int i = 0; i < workerCount; i++) {
                Thread worker = new Thread(() -> {
                    await(start);
                    for (int j = 0; j < incrementsPerWorker; j++) {
                        counter.increment();
                    }
                }, "sync-counter-" + i);
                workers.add(worker);
                worker.start();
            }

            start.countDown();
            joinAll(workers);

            int expected = workerCount * incrementsPerWorker;
            return linkedMap(
                    "expected", expected,
                    "actual", counter.value(),
                    "correct", counter.value() == expected,
                    "workersTerminated", workers.stream().noneMatch(Thread::isAlive)
            );
        } finally {
            start.countDown();
            cleanupThreads(workers.toArray(Thread[]::new));
        }
    }

    public Map<String, Object> tryLockDemo() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch holderHasLock = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        AtomicBoolean contenderAcquired = new AtomicBoolean(false);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                holderHasLock.countDown();
                await(releaseHolder);
            } finally {
                lock.unlock();
            }
        }, "try-lock-holder");

        Thread contender = new Thread(() -> {
            await(holderHasLock);
            try {
                if (lock.tryLock(100, TimeUnit.MILLISECONDS)) {
                    try {
                        contenderAcquired.set(true);
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "try-lock-contender");

        try {
            holder.start();
            contender.start();
            joinOrFail(contender);
            releaseHolder.countDown();
            joinOrFail(holder);
        } finally {
            releaseHolder.countDown();
            cleanupThreads(contender, holder);
        }

        return linkedMap(
                "holderAcquired", holderHasLock.getCount() == 0,
                "contenderAcquiredWhileHeld", contenderAcquired.get(),
                "workersTerminated", !holder.isAlive() && !contender.isAlive()
        );
    }

    public Map<String, Object> reentrantLockDemo() {
        ReentrantLock lock = new ReentrantLock();

        lock.lock();
        try {
            int holdCountAfterFirstAcquire = lock.getHoldCount();

            lock.lock();
            try {
                int holdCountAfterSecondAcquire = lock.getHoldCount();
                return linkedMap(
                        "heldByCurrentThread", lock.isHeldByCurrentThread(),
                        "holdCountAfterFirstAcquire", holdCountAfterFirstAcquire,
                        "holdCountAfterSecondAcquire", holdCountAfterSecondAcquire,
                        "reentrantAcquireSucceeded", holdCountAfterSecondAcquire == 2
                );
            } finally {
                lock.unlock();
            }
        } finally {
            lock.unlock();
        }
    }

    public Map<String, Object> interruptibleLockDemo() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch waiterStarted = new CountDownLatch(1);
        AtomicBoolean waiterInterrupted = new AtomicBoolean(false);
        AtomicBoolean waiterAcquired = new AtomicBoolean(false);

        lock.lock();
        Thread waiter = new Thread(() -> {
            waiterStarted.countDown();
            try {
                lock.lockInterruptibly();
                try {
                    waiterAcquired.set(true);
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                waiterInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        }, "interruptible-lock-waiter");

        try {
            waiter.start();
            waiterStarted.await();
            waitUntilWaiting(waiter);
            waiter.interrupt();
            joinOrFail(waiter);
        } finally {
            lock.unlock();
            cleanupThreads(waiter);
        }

        return linkedMap(
                "waiterInterrupted", waiterInterrupted.get(),
                "waiterAcquired", waiterAcquired.get(),
                "waiterTerminated", !waiter.isAlive()
        );
    }

    public Map<String, Object> reentrantLockPolicyDemo() {
        ReentrantLock defaultLock = new ReentrantLock();
        ReentrantLock explicitUnfairLock = new ReentrantLock(false);
        ReentrantLock fairLock = new ReentrantLock(true);

        return linkedMap(
                "defaultIsFair", defaultLock.isFair(),
                "explicitUnfairIsFair", explicitUnfairLock.isFair(),
                "explicitFairIsFair", fairLock.isFair(),
                "untimedTryLockHonorsFairness", false,
                "timedTryLockHonorsFairness", true,
                "fairnessGuaranteesThreadStartOrder", false,
                "fairnessEliminatesSchedulingVariance", false,
                "fairnessHasPotentialThroughputCost", true
        );
    }

    public Map<String, Object> readWriteLockDemo() throws InterruptedException {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        CountDownLatch readersStarted = new CountDownLatch(2);
        CountDownLatch releaseReaders = new CountDownLatch(1);
        CountDownLatch writerAttempting = new CountDownLatch(1);
        AtomicInteger activeReaders = new AtomicInteger();
        AtomicInteger maxConcurrentReaders = new AtomicInteger();
        AtomicBoolean writerAcquired = new AtomicBoolean(false);

        Runnable readTask = () -> {
            lock.readLock().lock();
            try {
                int active = activeReaders.incrementAndGet();
                maxConcurrentReaders.accumulateAndGet(active, Math::max);
                readersStarted.countDown();
                await(releaseReaders);
            } finally {
                activeReaders.decrementAndGet();
                lock.readLock().unlock();
            }
        };

        Thread readerA = new Thread(readTask, "rw-reader-a");
        Thread readerB = new Thread(readTask, "rw-reader-b");
        Thread writer = new Thread(() -> {
            writerAttempting.countDown();
            lock.writeLock().lock();
            try {
                writerAcquired.set(true);
            } finally {
                lock.writeLock().unlock();
            }
        }, "rw-writer");

        boolean writerAcquiredWhileReadersHeld;
        try {
            readerA.start();
            readerB.start();
            awaitOrFail(readersStarted, "Hai reader không acquire read lock đúng thời gian dự kiến.");
            writer.start();
            awaitOrFail(writerAttempting, "Writer không bắt đầu attempt write lock đúng thời gian dự kiến.");
            waitUntilWaiting(writer);
            writerAcquiredWhileReadersHeld = writerAcquired.get();
            releaseReaders.countDown();

            joinOrFail(readerA);
            joinOrFail(readerB);
            joinOrFail(writer);
        } finally {
            releaseReaders.countDown();
            cleanupThreads(readerA, readerB, writer);
        }

        return linkedMap(
                "maxConcurrentReaders", maxConcurrentReaders.get(),
                "writerAcquiredWhileReadersHeld", writerAcquiredWhileReadersHeld,
                "writerEventuallyAcquired", writerAcquired.get(),
                "workersTerminated", !readerA.isAlive() && !readerB.isAlive() && !writer.isAlive()
        );
    }

    public Map<String, Object> readWriteLockDowngradeDemo() throws InterruptedException {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        AtomicBoolean competingWriterAcquired = new AtomicBoolean(false);
        CountDownLatch competingWriterAttempting = new CountDownLatch(1);

        lock.writeLock().lock();
        boolean readHeldAfterWriteAcquire;
        boolean writeReleasedWhileReadHeld;

        Thread competingWriter = new Thread(() -> {
            competingWriterAttempting.countDown();
            lock.writeLock().lock();
            try {
                competingWriterAcquired.set(true);
            } finally {
                lock.writeLock().unlock();
            }
        }, "rw-downgrade-writer");

        try {
            try {
                lock.readLock().lock();
                readHeldAfterWriteAcquire = lock.getReadHoldCount() == 1;

                competingWriter.start();
                awaitOrFail(competingWriterAttempting, "Competing writer không bắt đầu đúng thời gian dự kiến.");
                waitUntilWaiting(competingWriter);

                lock.writeLock().unlock();
                writeReleasedWhileReadHeld = !lock.isWriteLockedByCurrentThread()
                        && lock.getReadHoldCount() == 1;

                if (competingWriterAcquired.get()) {
                    throw new IllegalStateException("Writer khác không được acquire khi read lock downgrade vẫn còn giữ.");
                }
            } finally {
                if (lock.isWriteLockedByCurrentThread()) {
                    lock.writeLock().unlock();
                }
                if (lock.getReadHoldCount() > 0) {
                    lock.readLock().unlock();
                }
            }

            joinOrFail(competingWriter);

            boolean readToWriteUpgradeSucceeded;
            lock.readLock().lock();
            try {
                readToWriteUpgradeSucceeded = lock.writeLock().tryLock(
                        50,
                        TimeUnit.MILLISECONDS
                );
                if (readToWriteUpgradeSucceeded) {
                    lock.writeLock().unlock();
                }
            } finally {
                lock.readLock().unlock();
            }

            return linkedMap(
                    "readHeldAfterWriteAcquire", readHeldAfterWriteAcquire,
                    "writeReleasedWhileReadHeld", writeReleasedWhileReadHeld,
                    "competingWriterBlockedDuringDowngrade", true,
                    "competingWriterEventuallyAcquired", competingWriterAcquired.get(),
                    "readToWriteUpgradeSucceeded", readToWriteUpgradeSucceeded,
                    "readToWriteUpgradeSupported", false
            );
        } finally {
            if (lock.isWriteLockedByCurrentThread()) {
                lock.writeLock().unlock();
            }
            while (lock.getReadHoldCount() > 0) {
                lock.readLock().unlock();
            }
            cleanupThreads(competingWriter);
        }
    }

    public Map<String, Object> readWriteLockPolicyDemo() {
        ReentrantReadWriteLock defaultLock = new ReentrantReadWriteLock();
        ReentrantReadWriteLock fairLock = new ReentrantReadWriteLock(true);

        return linkedMap(
                "defaultIsFair", defaultLock.isFair(),
                "explicitFairIsFair", fairLock.isFair(),
                "untimedReadTryLockHonorsFairness", false,
                "untimedWriteTryLockHonorsFairness", false,
                "fairnessGuaranteesThreadStartOrder", false,
                "fairnessHasThroughputTradeoff", true
        );
    }

    public Map<String, Object> stampedLockDemo() throws InterruptedException {
        StampedLock lock = new StampedLock();
        DoubleBox box = new DoubleBox(100.0);
        CountDownLatch readerSnapshotTaken = new CountDownLatch(1);
        CountDownLatch writerFinished = new CountDownLatch(1);
        AtomicBoolean stampValid = new AtomicBoolean(true);
        double[] initialSnapshot = new double[1];
        double[] fallbackValue = new double[1];

        Thread reader = new Thread(() -> {
            long stamp = lock.tryOptimisticRead();
            initialSnapshot[0] = box.value;
            readerSnapshotTaken.countDown();
            await(writerFinished);

            boolean valid = lock.validate(stamp);
            stampValid.set(valid);
            if (!valid) {
                stamp = lock.readLock();
                try {
                    fallbackValue[0] = box.value;
                } finally {
                    lock.unlockRead(stamp);
                }
            } else {
                fallbackValue[0] = initialSnapshot[0];
            }
        }, "stamped-reader");

        Thread writer = new Thread(() -> {
            await(readerSnapshotTaken);
            long stamp = lock.writeLock();
            try {
                box.value = 120.0;
            } finally {
                lock.unlockWrite(stamp);
                writerFinished.countDown();
            }
        }, "stamped-writer");

        try {
            reader.start();
            writer.start();
            joinOrFail(reader);
            joinOrFail(writer);
        } finally {
            cleanupThreads(reader, writer);
        }

        return linkedMap(
                "initialSnapshot", initialSnapshot[0],
                "optimisticStampValid", stampValid.get(),
                "updatedValue", box.value,
                "fallbackValue", fallbackValue[0]
        );
    }

    public Map<String, Object> stampedLockLimitationsDemo() {
        StampedLock lock = new StampedLock();
        long firstWriteStamp = lock.writeLock();
        long reentrantTryStamp;
        boolean conditionSupported;

        try {
            reentrantTryStamp = lock.tryWriteLock();
            if (reentrantTryStamp != 0L) {
                lock.unlockWrite(reentrantTryStamp);
            }

            try {
                lock.asReadLock().newCondition();
                conditionSupported = true;
            } catch (UnsupportedOperationException expected) {
                conditionSupported = false;
            }
        } finally {
            lock.unlockWrite(firstWriteStamp);
        }

        return linkedMap(
                "firstWriteLockAcquired", firstWriteStamp != 0L,
                "sameThreadReentrantTryWriteLockSucceeded", reentrantTryStamp != 0L,
                "reentrant", false,
                "conditionSupported", conditionSupported
        );
    }

    public Map<String, Object> atomicToolsDemo() throws InterruptedException {
        AtomicInteger atomicInteger = new AtomicInteger();
        LongAdder longAdder = new LongAdder();
        LongAccumulator maximum = new LongAccumulator(Long::max, Long.MIN_VALUE);
        int workerCount = 4;
        int increments = 1_000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();

        try {
            for (int workerId = 1; workerId <= workerCount; workerId++) {
                int valueForMaximum = workerId * 10;
                Thread worker = new Thread(() -> {
                    await(start);
                    for (int i = 0; i < increments; i++) {
                        atomicInteger.incrementAndGet();
                        longAdder.increment();
                    }
                    maximum.accumulate(valueForMaximum);
                }, "atomic-worker-" + workerId);
                workers.add(worker);
                worker.start();
            }

            start.countDown();
            joinAll(workers);

            int expected = workerCount * increments;
            return linkedMap(
                    "expectedCount", expected,
                    "atomicInteger", atomicInteger.get(),
                    "longAdder", longAdder.sum(),
                    "longAccumulatorMax", maximum.get()
            );
        } finally {
            start.countDown();
            cleanupThreads(workers.toArray(Thread[]::new));
        }
    }

    public Map<String, Object> atomicCasDemo() {
        AtomicInteger number = new AtomicInteger(10);
        boolean casWithExpectedValue = number.compareAndSet(10, 20);
        boolean casWithStaleExpectedValue = number.compareAndSet(10, 30);

        String stateA = new String("A");
        String stateB = new String("B");
        String stateC = new String("C");

        AtomicReference<String> plainReference = new AtomicReference<>(stateA);
        String staleExpectedReference = plainReference.get();
        plainReference.compareAndSet(stateA, stateB);
        plainReference.compareAndSet(stateB, stateA);
        boolean plainReferenceReturnedToA = plainReference.get() == stateA;
        boolean plainCasAfterAba = plainReference.compareAndSet(staleExpectedReference, stateC);

        AtomicStampedReference<String> stampedReference = new AtomicStampedReference<>(stateA, 0);
        int staleStamp = stampedReference.getStamp();
        stampedReference.compareAndSet(stateA, stateB, 0, 1);
        stampedReference.compareAndSet(stateB, stateA, 1, 2);
        boolean stampedCasWithStaleVersion = stampedReference.compareAndSet(
                stateA,
                stateC,
                staleStamp,
                staleStamp + 1
        );

        return linkedMap(
                "casWithExpectedValue", casWithExpectedValue,
                "valueAfterSuccessfulCas", number.get(),
                "casWithStaleExpectedValue", casWithStaleExpectedValue,
                "plainReferenceReturnedToA", plainReferenceReturnedToA,
                "plainCasAfterAbaSucceeded", plainCasAfterAba,
                "plainReferenceFinalValue", plainReference.get(),
                "stampedReferenceReturnedToA", stampedReference.getReference() == stateA,
                "stampedReferenceCurrentStamp", stampedReference.getStamp(),
                "stampedCasWithStaleVersionSucceeded", stampedCasWithStaleVersion,
                "stampDetectedIntermediateChange", !stampedCasWithStaleVersion
        );
    }

    public Map<String, Object> concurrentCollectionsDemo() throws InterruptedException {
        ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
        List<Thread> workers = new ArrayList<>();

        try {
            for (int i = 0; i < 4; i++) {
                Thread worker = new Thread(() -> {
                    for (int j = 0; j < 250; j++) {
                        counts.merge("request", 1, Integer::sum);
                    }
                }, "map-worker-" + i);
                workers.add(worker);
                worker.start();
            }
            joinAll(workers);
        } finally {
            cleanupThreads(workers.toArray(Thread[]::new));
        }

        CopyOnWriteArrayList<Integer> copyOnWrite = new CopyOnWriteArrayList<>(List.of(1, 2, 3));
        Iterator<Integer> snapshotIterator = copyOnWrite.iterator();
        copyOnWrite.add(4);
        List<Integer> iteratorSnapshot = new ArrayList<>();
        snapshotIterator.forEachRemaining(iteratorSnapshot::add);

        ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
        queue.offer("A");
        queue.offer("B");
        List<String> drained = new ArrayList<>();
        String item;
        while ((item = queue.poll()) != null) {
            drained.add(item);
        }

        return linkedMap(
                "concurrentHashMapCount", counts.get("request"),
                "copyOnWriteSnapshot", iteratorSnapshot,
                "copyOnWriteCurrent", List.copyOf(copyOnWrite),
                "queueDrained", drained,
                "pollWhenEmpty", queue.poll()
        );
    }

    public Map<String, Object> concurrentSkipListDemo() throws InterruptedException {
        ConcurrentSkipListMap<Integer, String> map = new ConcurrentSkipListMap<>();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();

        try {
            for (int workerId = 0; workerId < 4; workerId++) {
                int offset = workerId * 25;
                Thread worker = new Thread(() -> {
                    await(start);
                    for (int i = 24; i >= 0; i--) {
                        int key = offset + i;
                        map.put(key, "value-" + key);
                    }
                }, "skip-list-worker-" + workerId);
                workers.add(worker);
                worker.start();
            }

            start.countDown();
            joinAll(workers);
        } finally {
            start.countDown();
            cleanupThreads(workers.toArray(Thread[]::new));
        }

        List<Integer> keys = new ArrayList<>(map.keySet());
        boolean sorted = true;
        for (int i = 1; i < keys.size(); i++) {
            if (keys.get(i - 1) > keys.get(i)) {
                sorted = false;
                break;
            }
        }

        return linkedMap(
                "size", map.size(),
                "firstKey", map.firstKey(),
                "lastKey", map.lastKey(),
                "keysAreSorted", sorted,
                "workersTerminated", workers.stream().noneMatch(Thread::isAlive)
        );
    }

    private static void waitUntilWaiting(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + THREAD_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Thread.State state = thread.getState();
            if (state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING) {
                return;
            }
            Thread.sleep(5);
        }
        throw new IllegalStateException("Thread không chuyển sang trạng thái chờ: " + thread.getName());
    }

    private static void awaitOrFail(CountDownLatch latch, String message) throws InterruptedException {
        if (!latch.await(THREAD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(message);
        }
    }

    private static void joinAll(List<Thread> threads) throws InterruptedException {
        for (Thread thread : threads) {
            joinOrFail(thread);
        }
    }

    private static void joinOrFail(Thread thread) throws InterruptedException {
        thread.join(THREAD_TIMEOUT.toMillis());
        if (thread.isAlive()) {
            thread.interrupt();
            thread.join(THREAD_TIMEOUT.toMillis());
            throw new IllegalStateException("Thread không kết thúc: " + thread.getName());
        }
    }

    private static void cleanupThreads(Thread... threads) throws InterruptedException {
        for (Thread thread : threads) {
            if (thread == null || !thread.isAlive()) {
                continue;
            }
            thread.interrupt();
            thread.join(THREAD_TIMEOUT.toMillis());
            if (thread.isAlive()) {
                throw new IllegalStateException("Thread vẫn còn sống sau cleanup: " + thread.getName());
            }
        }
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

    private static Map<String, Object> linkedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static final class SynchronizedCounter {
        private int value;

        synchronized void increment() {
            value++;
        }

        synchronized int value() {
            return value;
        }
    }

    private static final class DoubleBox {
        private double value;

        private DoubleBox(double value) {
            this.value = value;
        }
    }
}

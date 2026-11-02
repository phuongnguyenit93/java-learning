package com.example.learning.casestudy.service;

import com.example.learning.casestudy.application.group13.case4.Bus;
import com.example.learning.casestudy.application.group13.case6.BoundedCache;
import com.example.learning.casestudy.application.group13.case9.BadService;
import com.example.learning.casestudy.application.group13.case9.BetterService;
import com.example.learning.casestudy.domain.group13.case10.Order;
import com.example.learning.casestudy.domain.group13.case2.Job;
import com.example.learning.casestudy.domain.group13.case5.Counter;
import com.example.learning.casestudy.domain.group13.case7.Line;
import com.example.learning.casestudy.domain.group13.case8.RequestContext;
import com.example.learning.casestudy.model.group13.case1.BadCounter1;
import com.example.learning.casestudy.model.group13.case10.BadOrder;
import com.example.learning.casestudy.model.group13.case4.BadBus;
import com.example.learning.casestudy.model.group13.case5.BadCounter5;
import com.example.learning.casestudy.model.group13.case6.BadRetention;
import com.example.learning.casestudy.model.group13.case6.Request;
import com.example.learning.casestudy.model.group13.case8.BadPool;
import com.example.learning.casestudy.model.group13.case8.MutableContext;
import com.example.learning.casestudy.port.group13.case4.Listener;
import com.example.learning.casestudy.runtime.CaseStudyJvmIsolation;
import com.example.learning.casestudy.utils.group13.case1.Case1Utils;
import com.example.learning.casestudy.utils.group13.case10.Case10Utils;
import com.example.learning.casestudy.utils.group13.case2.Case2Utils;
import com.example.learning.casestudy.utils.group13.case3.Case3Utils;
import com.example.learning.casestudy.utils.group13.case4.Case4Utils;
import com.example.learning.casestudy.utils.group13.case5.Case5Utils;
import com.example.learning.casestudy.utils.group13.case6.Case6Utils;
import com.example.learning.casestudy.utils.group13.case7.Case7Utils;
import com.example.learning.casestudy.utils.group13.case8.Case8Utils;
import com.example.learning.casestudy.utils.group13.case9.Case9Utils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service("caseStudyGroup13Service")
public class Group13Service {
    public void executeCase1() {
        try {
            BadCounter1 bad = new BadCounter1();
            CountDownLatch bothRead = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            Runnable unsafe = () -> {
                int old = bad.value;
                bothRead.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                bad.value = old + 1;
            };
            Thread a = new Thread(unsafe), b = new Thread(unsafe);
            a.start();
            b.start();
            bothRead.await();
            release.countDown();
            a.join();
            b.join();
            Case1Utils.check(bad.value == 1); // Hai lần tăng nhưng chỉ ghi nhận một.

            AtomicInteger good = new AtomicInteger();
            Thread[] workers = new Thread[4];
            for (int i = 0;i<workers.length;i++) {
                workers[i] = new Thread(() -> {
                    for (int n = 0;n<2_000;n++) good.incrementAndGet();
                });
                workers[i].start();
            }
            for (Thread t: workers) t.join();
            Case1Utils.check(good.get() == 8_000);
            System.out.println("PASS: atomic counter preserves all increments");
        } catch (Exception sourceException) {
            throw Case1Utils.propagate(sourceException);
        }
    }

    public void executeCase2() {
        try {
            Job job = new Job();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                Future<Boolean> observer = pool.submit(() -> {
                    start.await();
                    while (!job.isCompleted()) Thread.onSpinWait();
                    return job.isCompleted();
                });
                Future<?> writer = pool.submit(() -> {
                    start.await();
                    job.finish();
                    return null;
                });
                start.countDown();
                writer.get(5, TimeUnit.SECONDS);
                Case2Utils.check(observer.get(5, TimeUnit.SECONDS));
                Case2Utils.check(job.isCompleted());
                System.out.println("PASS: completion safely published");
            } finally {
                pool.shutdownNow();
            }
        } catch (Exception sourceException) {
            throw Case2Utils.propagate(sourceException);
        }
    }

    public void executeCase3() {
        try {
            Map<String, Integer> wrong = new HashMap<>();
            wrong.put("A", 0);
            int first = wrong.get("A"), second = wrong.get("A");
            wrong.put("A", first+1);
            wrong.put("A", second+1);
            Case3Utils.check(wrong.get("A") == 1); // Mô phỏng lịch xen kẽ bị lost update.

            ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
            ExecutorService pool = Executors.newFixedThreadPool(4);
            CountDownLatch go = new CountDownLatch(1);
            try {
                List<Future<?>> tasks = new ArrayList<>();
                for (int t = 0;t<4;t++){
                    tasks.add(pool.submit(() -> {
                        go.await();
                        for (int i = 0;i<1_000;i++) counts.merge("A", 1, Integer::sum);
                        return null;
                    }));
                }
                go.countDown();
                for (Future<?> task:tasks)task.get(5, TimeUnit.SECONDS);
                Case3Utils.check(counts.get("A") == 4_000);
                System.out.println("PASS: atomic merge per key");
            } finally {
                pool.shutdownNow();
            }
        } catch (Exception sourceException) {
            throw Case3Utils.propagate(sourceException);
        }
    }

    public void executeCase4() {
        BadBus bad = new BadBus();
        bad.listeners.add(()->Case4Utils.check(Thread.holdsLock(bad)));
        bad.publish(); // Chứng minh callback chạy dưới lock, không tạo deadlock.

        Bus safe = new Bus();
        List<String> received = new ArrayList<>();
        Listener[] self = new Listener[1];
        self[0] = event->{
            Case4Utils.check(!safe.holdingBusLock());
            received.add(event);
            safe.unsubscribe(self[0]);
        };
        safe.subscribe(self[0]);
        safe.publish("first");
        safe.publish("second");
        Case4Utils.check(received.equals(List.of("first")) && safe.count() == 0);
        System.out.println("PASS: callbacks invoked outside lock");

    }

    public void executeCase5() {
        try {
            BadCounter5 bad = new BadCounter5();
            int readA = bad.get(), readB = bad.get();
            bad.set(readA+1);
            bad.set(readB+1);
            Case5Utils.check(bad.get() == 1); // Getter/setter riêng vẫn lost update.
            Counter good = new Counter();
            Thread[] workers = new Thread[4];
            for (int i = 0;i<workers.length;i++){
                workers[i] = new Thread(()->{
                    for (int n = 0;n<1_000;n++)good.increment();
                });
                workers[i].start();
            }
            for (Thread t:workers)t.join();
            Case5Utils.check(good.get() == 4_000);
            System.out.println("PASS: lock covers entire compound operation");
        } catch (Exception sourceException) {
            throw Case5Utils.propagate(sourceException);
        }
    }

    public void executeCase6() {
        BadRetention bad = new BadRetention();
        for (int i = 0;i<3;i++)bad.remember(new Request("R"+i, new byte[1_024]));
        Case6Utils.check(bad.everything.size() == 3); // Vẫn giữ ba request.
        BoundedCache cache = new BoundedCache(2);
        cache.put("R1", "ok1");
        cache.put("R2", "ok2");
        Case6Utils.check(cache.get("R1").equals("ok1")); // R1 mới được truy cập.
        cache.put("R3", "ok3");
        Case6Utils.check(cache.size() == 2 && cache.get("R2") == null);
        Case6Utils.check(cache.get("R1").equals("ok1"));
        System.out.println("PASS: least-recently-used eviction");

    }

    public void executeCase7() {
        Line[] lines = {
            new Line(100, 2), new Line(55, 3), new Line(0, 1)
        };
        long[] prices = {
            100, 55, 0
        };
        int[] quantities = {
            2, 3, 1
        };
        long expected = Case7Utils.usingObjects(lines);
        Case7Utils.check(expected == 365);
        Case7Utils.check(Case7Utils.usingArrays(prices, quantities) == expected);
        try{
            Case7Utils.usingArrays(new long[]{
                1, 2
            }, new int[]{
                1
            });
            throw new AssertionError("mismatch accepted");
        } catch (IllegalArgumentException ok){
        }
        System.out.println("PASS: both implementations are functionally equal");

    }

    public void executeCase8() {
        if (!CaseStudyJvmIsolation.isChildProcess()) {
            CaseStudyJvmIsolation.execute(Group13Service.class, "executeCase8");
            return;
        }
        MutableContext first = BadPool.forUser("Alice");
        first.roles.add("ADMIN");
        MutableContext second = BadPool.forUser("Bob");
        Case8Utils.check(second == first);
        Case8Utils.check(second.user.equals("Bob") && second.roles.contains("ADMIN"));

        Set<String> inputRoles = new HashSet<>(Set.of("USER"));
        RequestContext bob = new RequestContext("Bob", inputRoles);
        inputRoles.add("ADMIN");
        RequestContext alice = new RequestContext("Alice", Set.of("ADMIN"));
        Case8Utils.check(!bob.isAdmin() && alice.isAdmin());
        try{
            bob.roles().add("ADMIN");
            throw new AssertionError("mutable snapshot");
        } catch (UnsupportedOperationException expected){
        }
        System.out.println("PASS: per-request immutable authorization snapshot");

    }

    public void executeCase9() {
        BadService old = new BadService();
        old.handle(()->Case9Utils.check(Thread.holdsLock(old)));
        BetterService better = new BetterService();
        better.handle(()->Case9Utils.check(!Thread.holdsLock(better)));
        better.handle(()->Case9Utils.check(!Thread.holdsLock(better)));
        Case9Utils.check(old.count() == 1 && better.count() == 2);
        System.out.println("PASS: independent work not under service monitor");

    }

    public void executeCase10() {
        BadOrder broken = new BadOrder();
        broken.set("A", 1);
        Case10Utils.check(broken.total() == 1);
        broken.set("A", 3);
        Case10Utils.check(broken.total() == 1); // Stale cache.
        Order good = new Order();
        good.setQuantity("A", 1);
        Case10Utils.check(good.total() == 1);
        good.setQuantity("A", 3);
        Case10Utils.check(good.total() == 3);
        good.setQuantity("B", 2);
        Case10Utils.check(good.total() == 5);
        try{
            good.setQuantity("B", -1);
            throw new AssertionError("negative accepted");
        } catch (IllegalArgumentException expected){
        }
        Case10Utils.check(good.total() == 5);
        System.out.println("PASS: derived cache consistent after mutation");

    }
}

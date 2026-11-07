<a id="back-to-top"></a>

# Locks &amp; Conditions

## Menu
- [Lock interface so với intrinsic monitor](#locks-and-conditions-001)
- [ReentrantLock: lock(), unlock() và finally](#locks-and-conditions-002)
- [tryLock() và timeout khi tranh chấp khóa](#locks-and-conditions-003)
- [lockInterruptibly() và hỗ trợ hủy khi chờ lock](#locks-and-conditions-004)
- [Fairness policy: throughput và starvation](#locks-and-conditions-005)
- [Condition await(), signal(), signalAll()](#locks-and-conditions-006)
- [Vì sao await() luôn cần vòng lặp kiểm tra điều kiện](#locks-and-conditions-007)
- [ReadWriteLock cho read-heavy workload](#locks-and-conditions-008)
- [ReentrantReadWriteLock: nâng/hạ cấp khóa và giới hạn](#locks-and-conditions-009)
- [StampedLock với optimistic read](#locks-and-conditions-010)
- [Chi phí, độ phức tạp và tiêu chí chọn loại lock](#locks-and-conditions-011)
- [Lock hierarchy và cách tránh thứ tự khóa vòng](#locks-and-conditions-012)

## <a id="locks-and-conditions-001">Lock interface so với intrinsic monitor</a>

<details>
<summary>Xem chi tiết</summary>

# Lock interface so với intrinsic monitor trong Java

Java có hai cách phổ biến để bảo vệ dữ liệu chia sẻ bằng **loại trừ tương hỗ**: từ khóa `synchronized` sử dụng **intrinsic monitor** của một object, và các đối tượng triển khai `java.util.concurrent.locks.Lock`, thường gặp nhất là `ReentrantLock`. Cả hai đều có thể bảo vệ cùng một invariant và tạo quan hệ bộ nhớ cần thiết giữa các thread, nhưng khác nhau về **cách lấy/nhả khóa, khả năng hủy khi chờ, timeout, điều kiện chờ và quản lý vòng đời khóa**.

Nắm khác biệt này giúp trả lời một câu hỏi thiết kế cụ thể: **critical section của mình chỉ cần độc quyền đơn giản hay còn cần thử lấy khóa, deadline, khả năng interrupt hoặc nhiều nhóm điều kiện chờ?** Bài này áp dụng cho Java concurrency nói chung, không bàn các framework tích hợp.

## 1. Từ “lock” và “monitor” chính xác là gì?

**Intrinsic monitor** (còn gọi là *intrinsic lock* hoặc *object monitor*) gắn với mỗi object Java và được sử dụng khi đi vào `synchronized`. Một thread muốn vào khối đồng bộ phải sở hữu monitor tương ứng. Các thread khác cố lấy monitor ấy phải chờ cho đến khi chủ sở hữu nhả. Một monitor chỉ cho một thread sở hữu tại một thời điểm và có tính **reentrant**: chính thread đang giữ khóa có thể lấy lại nó nhiều lần.

**`Lock` interface** là hợp đồng cấp thư viện trong `java.util.concurrent.locks`, cung cấp các method như `lock()`, `unlock()`, `tryLock()`, `lockInterruptibly()` và `newCondition()`. `ReentrantLock` là triển khai mutual exclusion có tính reentrant với các tính năng bổ sung. Một object `ReentrantLock` vẫn là một Java object có intrinsic monitor riêng, nhưng **hai cơ chế khóa này là độc lập**.

Cả hai bảo vệ được trạng thái chia sẻ khi **mọi đường truy cập liên quan tuân thủ cùng khóa hoặc giao thức đồng bộ**. Chỉ khóa writer trong khi reader đọc trực tiếp field thường, hoặc khóa hai object khác nhau, chưa đủ bảo vệ invariant/visibility.

### Ví dụ rất ngắn

Intrinsic monitor:

```java
private final Object monitor = new Object();
private int count;

void increment() {
    synchronized (monitor) {
        count++;
    }
}
```

Explicit lock:

```java
private final Lock lock = new ReentrantLock();
private int count;

void increment() {
    lock.lock();
    try {
        count++;
    } finally {
        lock.unlock();
    }
}
```

Hai mảnh code là **snippet đặt bên trong class**, không phải chương trình độc lập. Ví dụ thứ hai cần import `java.util.concurrent.locks.Lock` và `ReentrantLock`. Cả hai đều bảo vệ read–modify–write `count++` nếu **mọi** thao tác đọc/ghi cần nhất quán đều sử dụng đúng khóa ấy. Khác biệt dễ thấy: `synchronized` tự nhả monitor khi thoát scope, còn `Lock` bắt buộc lập trình viên gọi `unlock()` sau khi đã lấy được khóa.

## 2. Bảng so sánh tổng quan

| Tiêu chí | `synchronized` / intrinsic monitor | `Lock` / `ReentrantLock` |
| --- | --- | --- |
| Cơ chế | Từ khóa ngôn ngữ và monitor của một Java object | API cấp thư viện, một object triển khai `Lock` |
| Lấy khóa | Vào block/method `synchronized` | `lock()`, `lockInterruptibly()` hoặc `tryLock(...)` |
| Nhả khóa | Tự động khi thoát block/method, kể cả exception | Gọi `unlock()`, thường trong `finally` |
| Thử lấy khóa không chờ | Không có cú pháp chuẩn `synchronized.tryLock()` | `tryLock()` trả `boolean` ngay |
| Giới hạn thời gian chờ lấy khóa | Không có phiên bản timed acquire của `synchronized` | `tryLock(timeout, unit)` |
| Interrupt khi đang chờ **lấy khóa** | Không hủy được việc đợi vào monitor chỉ bằng interrupt | `lockInterruptibly()`, timed `tryLock` hỗ trợ interrupt |
| Cơ chế đợi điều kiện | `Object.wait/notify/notifyAll` trên **monitor đó** | `Condition.await/signal/signalAll` từ `newCondition()` |
| Số nhóm điều kiện | Một wait set gắn với mỗi object monitor | Có thể tạo nhiều `Condition` trên cùng `ReentrantLock` |
| Reentrancy | Có | `ReentrantLock` có; **không phải mọi triển khai `Lock` đều giống vậy** |
| Fairness option | Không có tùy chọn công bằng qua `synchronized` | `ReentrantLock(true)` có chính sách fairness theo hợp đồng, có giới hạn |
| Thread state khi tranh khóa | Thường `BLOCKED` khi chờ lấy monitor | Thường park, thấy `WAITING`/`TIMED_WAITING` tùy API/triển khai |
| Bảo đảm memory ordering | Unlock monitor happens-before lần lock kế tiếp trên cùng monitor | Lock/unlock của `Lock` có memory synchronization tương đương theo hợp đồng |
| Ưu tiên đơn giản, ít cấu hình | Rất phù hợp | Dài hơn, phải quản lý ownership/lifecycle rõ |
| Khóa qua nhiều method/scope | Cần thiết kế `synchronized` theo phạm vi lexical | Có thể giữ explicit lock qua call boundaries, nhưng dễ quên unlock |
| Chi phí | Phụ thuộc JVM/contended/uncontended workload | Phụ thuộc implementation/fairness/contended workload |

**Không có đáp án “`ReentrantLock` luôn nhanh hơn” hoặc “`synchronized` luôn chậm hơn”.** JVM tối ưu monitor và lock library theo nhiều điều kiện. Đo đạc workload, lock contention và latency trước khi chọn vì hiệu năng.

## 3. Intrinsic monitor hoạt động ra sao?

### 3.1. Monitor identity

Ba dạng hay gặp:

```java
class Example {
    private final Object lock = new Object();
    private static int globalCount;
    private int localCount;

    void withBlock() {
        synchronized (lock) {
            localCount++;
        }
    }

    synchronized void withInstanceMonitor() {
        localCount++;
    }

    static synchronized void withClassMonitor() {
        globalCount++;
    }
}
```

Đây là snippet class; chưa có `main`. `withBlock` dùng monitor của object `lock`, `withInstanceMonitor` dùng monitor của `this`, còn `static synchronized` dùng monitor của `Example.class`. **Ba monitor này không phải một.** Nếu hai method cùng sửa `localCount` nhưng dùng các monitor khác nhau, chúng **không** tự loại trừ nhau. Trong ví dụ, nếu người gọi có thể gọi hai method đồng thời trên cùng instance, cần thống nhất **một** khóa cho cả hai đường cập nhật.

Khóa trên `this` có thể bị code bên ngoài làm ảnh hưởng, nếu caller viết `synchronized (instance)`. Khóa bằng một object riêng `private final Object lock` giúp tránh giao thức khóa bị can thiệp ngoài ý muốn. Không khóa trên những object có thể được chia sẻ không chủ đích như string literal hay wrapper được cache.

### 3.2. Nhả monitor theo scope

`synchronized` tự nhả khóa khi đường thực thi rời khỏi khối, kể cả khi có `return` hoặc exception:

```java
synchronized (lock) {
    if (someCondition) {
        return;
    }
    updateState();
}
```

Đây là snippet bên trong method, giả định `lock`, `someCondition` và `updateState()` đã được định nghĩa. Không cần gọi `unlock` thủ công. Tuy vậy, tự nhả khóa **không rollback dữ liệu** đã được sửa một phần trước khi exception; invariant nghiệp vụ vẫn do code bảo vệ.

### 3.3. Reentrancy và sự khác nhau giữa các monitor

Một thread giữ monitor `m` có thể đi vào lại `synchronized(m)`. Điều đó hữu ích khi method synchronized gọi method synchronized khác cùng instance. Nhưng nested acquisition của **hai monitor khác nhau** vẫn có thể deadlock nếu hai thread lấy theo thứ tự ngược nhau.

### 3.4. Chờ để lấy monitor

Nếu thread A đang giữ monitor, thread B cố vào cùng `synchronized` sẽ thường ở `Thread.State.BLOCKED`. `Thread.interrupt()` không khiến B ném `InterruptedException` chỉ vì đang chờ monitor. Sau khi A nhả, B có thể vào dù interrupt flag đã được đặt. Đừng nhầm với `Object.wait()` bên trong monitor: **`wait()` hỗ trợ interrupt** và có semantics riêng.

## 4. `Lock` là hợp đồng gì, `ReentrantLock` bổ sung gì?

Các method cốt lõi của `java.util.concurrent.locks.Lock`:

| Method | Ý nghĩa |
| --- | --- |
| `lock()` | Lấy khóa, có thể phải chờ; xử lý interrupt trong khi chờ tùy hợp đồng triển khai. |
| `lockInterruptibly()` | Cố lấy khóa và cho phép thoát bằng `InterruptedException` khi bị interrupt. |
| `tryLock()` | Cố lấy khóa ngay; trả `true` khi lấy được và `false` nếu chưa lấy được. |
| `tryLock(long time, TimeUnit unit)` | Đợi có giới hạn thời gian để lấy khóa; trả `true/false`; hỗ trợ interruption. |
| `unlock()` | Nhả khóa mà thread đang sở hữu; với `ReentrantLock`, gọi sai owner có thể ném `IllegalMonitorStateException`. |
| `newCondition()` | Tạo đối tượng `Condition` cho phối hợp trên khóa, nếu implementation hỗ trợ. |

`ReentrantLock` cung cấp một implementation reentrant và các thông tin giám sát như `isLocked()`, `isHeldByCurrentThread()`, `getHoldCount()`, `hasQueuedThreads()` và khả năng cấu hình fairness lúc khởi tạo. Một số method quan sát chỉ phản ánh **snapshot**, không nên dùng để tự dựng thuật toán điều phối phụ thuộc timing.

**Không phải bất kỳ object implement `Lock` nào cũng có mọi đặc tính của `ReentrantLock`.** Đọc hợp đồng của implementation cụ thể trước khi giả định nó reentrant, fair hoặc có cách chờ tương tự.

### Cú pháp an toàn bắt buộc cho explicit lock

```java
lock.lock();
try {
    updateSharedState();
} finally {
    lock.unlock();
}
```

Snippet giả định `lock` là `Lock` và `updateSharedState()` là operation cần bảo vệ. `lock.lock()` đặt **trước** `try`: nếu acquisition thất bại/throw thì ta không `unlock` một lock chưa sở hữu. Sau khi lấy khóa thành công, phần thay đổi dữ liệu nằm trong `try` và việc nhả luôn trong `finally`.

**Lỗi nghiêm trọng:** gọi `lock.lock()` rồi có `return` hoặc exception trước `unlock()` mà không có `finally`. Mọi thread khác có thể chờ vô hạn; lỗi khó debug hơn `synchronized` vì JVM không tự nhả explicit `Lock` ở cuối method.

## 5. Ví dụ Java 17: hai counter đều đúng, khác cách quản lý khóa

Tạo tệp `MonitorVsLockCounter.java`:

```java
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MonitorVsLockCounter {
    static class MonitorCounter {
        private int value;

        synchronized void increment() {
            value++;
        }

        synchronized int get() {
            return value;
        }
    }

    static class ExplicitCounter {
        private final Lock lock = new ReentrantLock();
        private int value;

        void increment() {
            lock.lock();
            try {
                value++;
            } finally {
                lock.unlock();
            }
        }

        int get() {
            lock.lock();
            try {
                return value;
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        MonitorCounter monitor = new MonitorCounter();
        ExplicitCounter explicit = new ExplicitCounter();

        Runnable task = () -> {
            for (int i = 0; i < 10_000; i++) {
                monitor.increment();
                explicit.increment();
            }
        };

        Thread first = new Thread(task, "first");
        Thread second = new Thread(task, "second");
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("monitor=" + monitor.get());
        System.out.println("lock=" + explicit.get());
    }
}
```

Chạy:

```bash
javac MonitorVsLockCounter.java
java MonitorVsLockCounter
```

Stdout xác định:

```text
monitor=20000
lock=20000
```

Mỗi worker chạy `10_000` lần; tổng hai worker tạo `20_000` cập nhật trên mỗi counter. `synchronized` và `ReentrantLock` bảo vệ nguyên tử của phép `value++`, còn `join()` bảo đảm main đợi các worker kết thúc trước khi đọc. Đây là **ví dụ đúng về atomicity và visibility**, **không phải benchmark** tốc độ hai kiểu khóa. Nếu một method đọc trực tiếp `value` mà không dùng cùng giao thức, class có thể không còn thread-safe.

## 6. Điểm mạnh của `Lock`: thử lấy khóa và timeout

Với `synchronized`, một thread muốn vào monitor thường phải chờ tới khi lấy được; không có `synchronized.tryLock` hoặc cú pháp chờ tối đa 200 ms. Khi nghiệp vụ cần “không lấy được khóa thì trả về nhanh, báo busy hoặc dùng phương án khác”, `tryLock` hữu ích:

```java
if (lock.tryLock()) {
    try {
        doWork();
    } finally {
        lock.unlock();
    }
} else {
    handleBusy();
}
```

Snippet giả định có `lock`, `doWork` và `handleBusy`. `tryLock()` không chờ dài để tranh lock. Nếu dùng:

```java
if (lock.tryLock(200, TimeUnit.MILLISECONDS)) {
    try {
        doWork();
    } finally {
        lock.unlock();
    }
} else {
    handleTimeout();
}
```

Snippet này phải nằm trong method xử lý `InterruptedException` và cần import `java.util.concurrent.TimeUnit`. Timed `tryLock` có thể chờ tới thời hạn rồi trả `false` nếu không lấy được; thời điểm thực tế còn tùy scheduler. Nếu bị interrupt khi đang chờ, có thể ném `InterruptedException`; phải propagate hoặc restore flag theo chính sách dừng.

### Ví dụ Java 17 chạy được: thử khóa khi thread khác đang giữ

Tạo `TryLockExample.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class TryLockExample {
    public static void main(String[] args) throws Exception {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                acquired.countDown();
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });

        holder.start();
        try {
            if (!acquired.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Holder did not acquire");
            }

            boolean immediate = lock.tryLock();
            if (immediate) {
                lock.unlock();
            }
            System.out.println("immediate=" + immediate);

            boolean timed = lock.tryLock(50, TimeUnit.MILLISECONDS);
            if (timed) {
                lock.unlock();
            }
            System.out.println("timed=" + timed);
        } finally {
            release.countDown();
            holder.join();
        }

        lock.lockInterruptibly();
        try {
            System.out.println("afterRelease=true");
        } finally {
            lock.unlock();
        }
    }
}
```

```bash
javac TryLockExample.java
java TryLockExample
```

Stdout khi chương trình hoàn thành bình thường:

```text
immediate=false
timed=false
afterRelease=true
```

Main chỉ bắt đầu thử lấy khóa sau khi `holder` báo đã giữ `lock` qua latch `acquired`. Holder chỉ nhả sau khi main mở `release` trong `finally`, nên **cả immediate và timed tryLock đều không lấy được** trên đường chạy bình thường. Sau `holder.join()`, khóa đã được nhả; main lấy thành công bằng `lockInterruptibly()` rồi in dòng cuối.

Timeout `50 ms` ở đây là minh họa API, không phải một benchmark chính xác 50 ms. Trong test/production cần điều chỉnh deadline phù hợp môi trường, xử lý interrupt và không dựa vào một `sleep` để “hy vọng” holder đang giữ khóa. Hai latch cho ta thứ tự điều phối rõ ràng.

### `lockInterruptibly()` cần khi nào?

Nếu một thread đã bắt đầu đợi khóa nhưng yêu cầu của người dùng bị hủy hoặc ứng dụng đang shutdown, `lockInterruptibly()` cho phép chờ khóa có thể dừng theo interrupt. Khi bị interrupt trong lúc chờ, method ném `InterruptedException` và không trả về như thể đã lấy khóa. Caller không được `unlock()` nếu acquisition chưa thành công.

`ReentrantLock.lock()` thông thường không dùng `InterruptedException` làm đường trả về khi đang tranh lock; nếu cần cancellation có kiểm soát hãy chọn `lockInterruptibly()` hoặc timed `tryLock`. Sau khi **đã** lấy khóa, interrupt không tự động nhả khóa của thread: code vẫn chịu trách nhiệm release trong `finally`.

## 7. `Condition` so với `Object.wait/notifyAll`

**Mutual exclusion** trả lời “ai được vào critical section?”. **Condition waiting** trả lời “khi điều kiện nghiệp vụ chưa đúng, làm sao thread đang giữ khóa tạm nhả khóa để người khác thay đổi điều kiện?”. Cả intrinsic monitor lẫn explicit `Lock` đều hỗ trợ cơ chế chờ, nhưng API khác nhau.

### 7.1. Với intrinsic monitor

Một producer/consumer chờ trên chính monitor `monitor`:

```java
synchronized (monitor) {
    while (!ready) {
        monitor.wait();
    }
    consume();
}
```

Đây là snippet, giả định `ready` và `consume()` được bảo vệ bằng `monitor`. `wait()` yêu cầu caller sở hữu **monitor của object được gọi `wait`**, nếu không ném `IllegalMonitorStateException`. Khi chờ, `wait()` nhả monitor đó; sau khi được đánh thức, thread phải **lấy lại monitor** trước khi `wait()` trở về.

Producer dùng:

```java
synchronized (monitor) {
    ready = true;
    monitor.notifyAll();
}
```

`notifyAll()` đánh thức những waiter thuộc wait set của monitor; nó không trao monitor ngay lập tức cho waiter. Producer còn giữ monitor tới khi rời synchronized block. Waiter phải dùng **`while`** để kiểm tra lại predicate do spurious wakeup, có nhiều consumer hoặc predicate đã đổi trước khi nó lấy lại lock.

### 7.2. Với `Lock` và `Condition`

`ReentrantLock.newCondition()` trả một `Condition` gắn với explicit lock:

```java
lock.lock();
try {
    while (!ready) {
        readyCondition.await();
    }
    consume();
} finally {
    lock.unlock();
}
```

Producer:

```java
lock.lock();
try {
    ready = true;
    readyCondition.signalAll();
} finally {
    lock.unlock();
}
```

Đây là hai snippet trong cùng class giả định `lock` là `ReentrantLock`, `readyCondition = lock.newCondition()` và `ready/consume` dùng cùng lock; cần import `java.util.concurrent.locks.Condition`, `ReentrantLock`. `await()` chỉ được gọi khi caller đang giữ lock liên kết; nó **nhả lock trong lúc chờ rồi lấy lại trước khi trở về**. `signal()`/`signalAll()` yêu cầu giữ lock tương ứng đối với `ReentrantLock.ConditionObject`, nếu không ném `IllegalMonitorStateException`.

**Condition không phải một mutex khác:** nó là **hàng đợi chờ điều kiện** liên kết với lock. Cùng lock có thể tạo hai condition khác nhau như `notEmpty` và `notFull`, trong khi một intrinsic monitor có một wait set mặc định.

### 7.3. `await` không giống `sleep`

| Thao tác | Có nhả khóa đang sử dụng để chờ không? | Có nhận tín hiệu không? |
| --- | --- | --- |
| `Object.wait()` | Nhả monitor của đúng object `wait` | `notify/notifyAll`, interrupt, spurious wakeup |
| `Condition.await()` | Nhả **associated Lock**, sau đó lấy lại trước khi return | `signal/signalAll`, interrupt, spurious wakeup |
| `Thread.sleep(ms)` | **Không tự nhả** monitor hay `Lock` đang giữ | Có thể hết thời gian hoặc bị interrupt |
| `LockSupport.park()` | **Không tự nhả** monitor/`Lock` đang giữ | Permit từ `unpark`, interrupt, spurious return |

Nếu sleep khi đang giữ khóa rồi chờ một thread khác thay đổi predicate dưới cùng khóa, thread kia không thể vào để thay đổi dữ liệu. `wait/await` được thiết kế để tạm nhả khóa nhằm tạo cơ hội cho state tiến triển.

## 8. Ví dụ Java 17: một khóa, hai điều kiện `notEmpty` và `notFull`

Một “single-slot buffer” chỉ lưu một phần tử tại một thời điểm. Producer phải chờ khi buffer đầy; consumer phải chờ khi buffer rỗng. `ReentrantLock` cho phép hai queue chờ riêng để phát tín hiệu đúng nhóm:

Tạo `ConditionBufferExample.java`:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ConditionBufferExample {
    static class OneSlotBuffer {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();
        private final Condition notFull = lock.newCondition();
        private Integer item;

        void put(int value) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (item != null) {
                    notFull.await();
                }
                item = value;
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        int take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (item == null) {
                    notEmpty.await();
                }
                int result = item;
                item = null;
                notFull.signal();
                return result;
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        OneSlotBuffer buffer = new OneSlotBuffer();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> consumer = pool.submit(buffer::take);
            Future<Void> producer = pool.submit(() -> {
                buffer.put(42);
                return null;
            });

            producer.get(5, TimeUnit.SECONDS);
            int received = consumer.get(5, TimeUnit.SECONDS);
            System.out.println("received=" + received);
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac ConditionBufferExample.java
java ConditionBufferExample
```

Stdout khi chương trình hoàn thành bình thường:

```text
received=42
```

**Phân tích:**

1. Nếu consumer tới trước, nó lấy `lock`, thấy `item == null` rồi gọi `notEmpty.await()`: **nhả lock** và chờ.
2. Producer lấy lock, đặt `item = 42`, gọi `notEmpty.signal()`, nhả lock. Consumer sau khi lấy lại lock kiểm tra predicate lần nữa rồi đọc 42.
3. Nếu producer tới trước, `item` đã có 42 khi consumer tới; consumer không cần `await()`. Hai thứ tự đều tạo kết quả như nhau.
4. `notFull` sẽ có ích khi producer tiếp theo tới lúc slot đang đầy. Ví dụ chỉ có một phần tử, nên đường chờ `notFull` không nhất thiết xảy ra, nhưng code cài đặt đủ cả hai điều kiện.
5. `producer.get` và `consumer.get` chuyển các exception từ task về main, đồng thời chờ kết quả. Timeout ở caller không tự bảo đảm task đã dừng; `shutdownNow()` thử interrupt các worker nếu chưa hoàn tất.

Vì dùng `Integer` làm marker có/không dữ liệu, `null` được dành riêng cho trạng thái rỗng; nếu bài toán cho phép payload null, cần một cờ `occupied` riêng hoặc cấu trúc biểu diễn khác. Đây là ví dụ học tập; trong ứng dụng thực tế hãy cân nhắc `ArrayBlockingQueue`, vốn đã bao gói quy tắc đồng bộ, bound và signals đúng cách.

**Vì sao hai `Condition` có ích?** Với cùng một intrinsic monitor, `notify()` có thể đánh thức một waiter không thể tiến triển (ví dụ producer trong khi buffer vẫn đầy), nên các triển khai đơn giản thường dùng `notifyAll()` rồi kiểm tra lại predicate. Với `ReentrantLock`, `notEmpty.signal()` hướng đến nhóm consumer, `notFull.signal()` hướng đến nhóm producer; vẫn phải dùng `while` và giữ khóa khi signal.

## 9. Tương đương bằng monitor và khi nào đủ dùng

Nếu chỉ có một condition hoặc tải thấp, intrinsic monitor thường ngắn gọn:

```java
class MonitorOneSlotBuffer {
    private Integer item;

    synchronized void put(int value) throws InterruptedException {
        while (item != null) {
            wait();
        }
        item = value;
        notifyAll();
    }

    synchronized int take() throws InterruptedException {
        while (item == null) {
            wait();
        }
        int result = item;
        item = null;
        notifyAll();
        return result;
    }
}
```

Snippet class Java 17 không có `main`. Ở đây synchronized instance methods dùng monitor của `this`. `wait()` và `notifyAll()` được gọi ngầm trên `this` khi thread đang giữ monitor ấy. Dùng `notifyAll` để thông báo cả các waiter có predicate khác nhau, rồi mỗi thread **tự kiểm tra lại while**; đây là một tradeoff đơn giản nhưng có thể đánh thức thừa người chờ.

Không nên chuyển mọi `synchronized` sang `ReentrantLock` chỉ vì “Lock nhiều tính năng hơn”. Nếu bài toán chỉ là một critical section ngắn, một khóa rõ identity và không cần timeout hay điều kiện riêng, `synchronized` thường dễ đọc và ít rủi ro quên nhả khóa.

## 10. Các bảo đảm Java Memory Model

Khóa bảo vệ **hai thứ**: mutual exclusion tại critical section và memory synchronization giữa các thread tham gia cùng giao thức.

### 10.1. Với monitor

Một thread nhả (unlock) monitor `M` happens-before lần lock tiếp theo của monitor `M`. Do đó:

```java
synchronized (monitor) {
    sharedValue = 42;
}
```

và về sau:

```java
synchronized (monitor) {
    System.out.println(sharedValue);
}
```

Các snippet giả định cùng monitor và biến `sharedValue`, không phải chương trình độc lập. Nếu read thực sự lấy monitor sau khi writer đã nhả và không có writer khác xen vào sửa, nó có thể thấy bản ghi 42 theo memory synchronization. Nếu dùng **hai monitor khác nhau**, không có monitor-happens-before giữa chúng chỉ vì cùng từ khóa `synchronized`.

### 10.2. Với `Lock`

Theo hợp đồng `Lock`, một `unlock` thành công có memory synchronization effects như thao tác nhả monitor; `lock` thành công như thao tác lấy monitor. Vì thế caller không cần thêm `volatile` cho một field thường đã được **mọi** đường đọc/ghi bảo vệ bằng cùng `ReentrantLock`.

Tuy nhiên: **`lock.tryLock()` trả `false` không phải lần acquisition thành công**; nó không bảo vệ critical section và không nên được coi là “đã đồng bộ dữ liệu rồi”. Tương tự, các method trạng thái như `isLocked()` không thay thế thao tác lấy lock thật để đọc state cần bảo vệ.

### 10.3. Với `wait` và `await`

Khi chờ, thread nhả monitor/associated lock và sau đó lấy lại; cùng với quan hệ release/acquire tạo cơ chế publication của producer cho consumer. **Signal chỉ thông báo**; predicate dữ liệu nằm dưới cùng khóa mới là sự thật cần kiểm tra. Không đặt niềm tin vào “có signal thì condition chắc chắn còn đúng” vì các waiter khác có thể đã tiêu thụ tài nguyên trước khi thread vừa tỉnh lấy lại khóa.

### 10.4. Happens-before không đồng nghĩa một workflow atomic

Ví dụ:

```java
if (stock > 0) {
    stock--;
}
```

Nếu hai thread được phép xen giữa check và decrement, cả hai có thể thấy cùng stock cũ. Dùng một `volatile int stock` chưa đủ. Đưa **cả check và decrement** vào cùng `synchronized`/`lock`, hoặc dùng CAS trên một atomic variable là cách bảo vệ invariant. Một thao tác `join()` sau khi worker hoàn thành giúp main quan sát kết quả, **không thể hồi phục cập nhật đã bị mất** trước đó.

## 11. Fairness, starvation và tính reentrant

### 11.1. `ReentrantLock(true)` có ý nghĩa gì?

`new ReentrantLock(true)` yêu cầu chính sách fairness: khi tranh lock, implementation có khuynh hướng nhường cho thread đã chờ lâu hơn theo hợp đồng của lock. Nhưng:

- Không đảm bảo fairness ở cấp **CPU scheduling** hoặc mọi thời điểm lấy lock tuyệt đối.
- `tryLock()` không tham số có thể **barging** và lấy lock nếu đang rảnh ngay, ngay cả với fair lock có thread khác đang xếp hàng.
- Fairness thường có chi phí throughput/latency cao hơn trong nhiều workload; không bật vô điều kiện vì nghe có vẻ công bằng.
- Nếu cần timed acquisition tôn trọng fairness thì đọc rõ semantics của `tryLock(timeout, unit)` đối với `ReentrantLock`.
- Fairness không giải quyết chương trình giữ lock quá lâu, deadlock do thứ tự nhiều lock, hoặc phụ thuộc bên ngoài không hoàn thành.

### 11.2. Reentrancy không có nghĩa là vô hạn hoặc không tốn gì

`ReentrantLock` cho phép cùng thread `lock()` nhiều lần; mỗi lần tăng hold count. Thread phải `unlock()` **cùng số lần đã lấy** trước khi người khác có thể vào. Với `synchronized`, monitor cũng reentrant và nesting được JVM quản lý theo scope.

Ví dụ:

```java
lock.lock();
try {
    lock.lock();
    try {
        // Cùng thread đang giữ lock hai lần.
    } finally {
        lock.unlock();
    }
} finally {
    lock.unlock();
}
```

Snippet giả định `lock` là `ReentrantLock`. Nếu chỉ gọi `unlock` một lần, hold count còn 1 và lock vẫn do thread đó sở hữu. Do đó nested acquisition cần được cân nhắc; thường giữ code theo một discipline đơn giản để tránh lỗi này.

### 11.3. Starvation và priority inversion

Không một mutex nào đảm bảo business request luôn được phục vụ công bằng theo SLA. Khi một thread giữ khóa lâu, các thread khác phải chờ; nếu thread giữ khóa bị scheduler trì hoãn hoặc làm blocking I/O, latency toàn hệ thống tăng. Một fair `ReentrantLock` có thể giúp giảm một số hiện tượng tranh lock không công bằng, nhưng không phải công cụ giải quyết toàn bộ priority inversion hoặc overloading.

## 12. Lựa chọn `lockInterruptibly` và xử lý interruption

Một khía cạnh khác biệt quan trọng giữa monitor và `Lock` là **hủy khi còn đang đợi acquisition**:

| Tình huống | `synchronized` | `ReentrantLock` |
| --- | --- | --- |
| Đang cố lấy khóa | Interrupt không hủy intrinsic monitor acquisition | `lockInterruptibly()` có thể ném `InterruptedException` |
| Đã giữ khóa, thread bị interrupt | Không tự nhả monitor | Không tự nhả explicit lock |
| Đang chờ condition | `Object.wait()` phản hồi interrupt bằng exception | `Condition.await()` phản hồi interrupt theo hợp đồng |
| Muốn chờ tối đa 250 ms để **lấy** lock | Không có timed intrinsic monitor acquisition | `tryLock(250, TimeUnit.MILLISECONDS)` |
| Muốn thử ngay không chờ | Không có non-blocking monitor try-acquire trong cú pháp `synchronized` | `tryLock()` |

**Đừng nhầm acquisition với condition waiting.** `Object.wait(timeout)` có timeout nhưng nó chỉ được gọi **sau khi thread đã sở hữu monitor**, nhằm chờ một điều kiện; nó **không** khiến thao tác đi vào `synchronized` có timeout. Tương tự `Condition.await(timeout)` khác `lock.tryLock(timeout)`: cái đầu đợi condition trong lúc tạm nhả lock; cái sau đợi để lấy lock.

Khi bắt `InterruptedException` và không ném tiếp, thường khôi phục flag `Thread.currentThread().interrupt()` rồi kết thúc đường công việc phù hợp. Không nuốt interrupt rồi lặp vô hạn. Với `ReentrantLock.lockInterruptibly()`, nếu method ném `InterruptedException` trước khi trả về thành công, caller **chưa có khóa** nên không được `unlock()` như thể đã có.

## 13. Thread dump và công cụ quan sát

Trên HotSpot/OpenJDK, `jcmd <pid> Thread.print -l` hoặc Java Flight Recorder có thể giúp xem lock contention và các thread đang chờ. Ý nghĩa cần phân biệt:

- Thread đợi **intrinsic monitor** của `synchronized` thường hiện `BLOCKED (on object monitor)`.
- Thread đợi `ReentrantLock` thông qua cơ chế `LockSupport.park`/`AbstractQueuedSynchronizer` thường có thể ở `WAITING` hoặc `TIMED_WAITING`, tùy method, timeout và runtime.
- Thread đợi `Object.wait()` trên condition của monitor thường hiện `WAITING`; đợi `Object.wait(timeout)` thường `TIMED_WAITING`.
- Thread đợi `Condition.await()` cũng có thể xuất hiện trong stack qua `LockSupport`/`AbstractQueuedSynchronizer.ConditionObject`.
- Không kết luận có deadlock từ một thread đơn lẻ `BLOCKED` hay `WAITING`; tìm chu trình khóa và owner, kiểm tra nhiều snapshot, thời gian chờ và đường gọi.

Ví dụ command (thay PID thật):

```bash
jcmd <PID> Thread.print -l
```

Không đưa command này vào stdout của chương trình Java ở trên. Nội dung dump phụ thuộc JDK, phiên bản và thời điểm quan sát.

`ThreadMXBean` có API phát hiện một số deadlock liên quan monitors và ownable synchronizers. Nó hữu ích khi kiểm tra `synchronized` và `ReentrantLock`, nhưng không thể chứng minh không có deadlock nghiệp vụ liên quan DB/HTTP/queue hoặc mọi điều kiện chờ khác.

## 14. Các lỗi rất dễ mắc khi chuyển từ monitor sang `Lock`

### 14.1. Quên nhả explicit lock

```java
lock.lock();
doWork();             // Nếu ném exception, unlock bị bỏ qua.
lock.unlock();
```

Sai khi `doWork()` throw. Sửa bằng `try/finally` ngay sau acquisition thành công.

### 14.2. `unlock` khi chưa có khóa

```java
if (lock.tryLock()) {
    try {
        doWork();
    } finally {
        lock.unlock();
    }
}
```

Đúng vì chỉ `unlock` nếu `tryLock` trả true. Nếu `tryLock` false hoặc `lockInterruptibly` bị ngắt trước khi lấy khóa, không được `unlock`. Với `ReentrantLock`, thread khác gọi `unlock` sẽ ném `IllegalMonitorStateException`; không có “unlock hộ” owner.

### 14.3. Trộn `synchronized(lock)` với `lock.lock()`

```java
ReentrantLock lock = new ReentrantLock();

// Thread A:
synchronized (lock) {
    update();
}

// Thread B:
lock.lock();
try {
    update();
} finally {
    lock.unlock();
}
```

Đây là snippet minh họa hai đường code, không phải một chương trình chạy độc lập. **Hai thứ không bảo vệ nhau:** đường A lấy *intrinsic monitor của object ReentrantLock*, đường B lấy *trạng thái explicit ReentrantLock*. Vì vậy A và B có thể vào hai vùng đồng thời dù dùng đúng cùng biến reference tên `lock`. Chọn **một cơ chế thống nhất** cho mọi actor truy cập cùng state.

### 14.4. Dùng điều kiện sai lock

```java
Condition condition = lock.newCondition();
// ...
condition.await();
```

Nếu caller chưa giữ đúng `lock`, `ReentrantLock` sẽ ném `IllegalMonitorStateException`. Khi signal cũng vậy: phải giữ lock tương ứng khi gọi `condition.signal()`/`signalAll()`. Khi có hai condition từ cùng lock, cả hai vẫn bảo vệ dữ liệu bằng **chính lock ấy**; condition chỉ phân nhóm người chờ.

### 14.5. Dùng `if` thay `while`

```java
if (queue.isEmpty()) {
    notEmpty.await();
}
return queue.remove();
```

Sai vì spurious wakeup hoặc một consumer khác có thể lấy mất phần tử trước khi thread vừa thức lấy lại khóa. Dùng:

```java
while (queue.isEmpty()) {
    notEmpty.await();
}
return queue.remove();
```

Hai snippet giả định `queue`, `notEmpty` và khóa đang được giữ. Với condition waiting, **luôn gắn việc chờ với predicate thật được bảo vệ bởi cùng khóa**.

### 14.6. Signal ngoài critical section

Thao tác sửa predicate và signal cần nằm trong giao thức lock nhất quán. Nếu set `ready` không đồng bộ rồi `signal` ngoài lock, code vừa sai với contract Condition của `ReentrantLock` vừa có thể bỏ lỡ tín hiệu hoặc thấy dữ liệu không đúng. Hãy cập nhật state và phát signal **trong khi giữ lock**, còn việc thread được đánh thức vẫn cần chờ lock được nhả.

### 14.7. Lock quá rộng và I/O trong critical section

Đặt HTTP/DB call, `Thread.sleep` hoặc callback không tin cậy trong khi đang giữ lock có thể khiến thread khác chờ lâu, tăng tail latency, hoặc deadlock khi callback lấy khóa theo thứ tự khác. Cố gắng chỉ giữ lock để chuyển trạng thái bất biến; đưa I/O/callback ra ngoài nếu nghiệp vụ cho phép. Nếu phải giữ khóa để bảo toàn transaction, đặt deadline, kiểm soát lock order và thiết kế failure mode.

## 15. Lock ordering, deadlock và “độc quyền” không đồng nghĩa “không lỗi”

Giả sử A lấy `lock1` rồi `lock2`, còn B lấy `lock2` rồi `lock1`. Cả hai có thể giữ một khóa và đợi khóa còn lại mãi. Điều này xảy ra với **cả intrinsic monitor lẫn `ReentrantLock`**. Một chính sách giúp giảm deadlock là luôn lấy nhiều khóa theo **thứ tự toàn cục nhất quán**. Nếu chỉ có `Lock` và logic cho phép, `tryLock(timeout)` có thể giúp phát hiện/thoát tình huống chờ, nhưng không tự sửa invariant nghiệp vụ hoặc bảo đảm liveness khi retry.

Việc thêm mutex cũng có thể tạo **lock convoy** (nhiều thread xếp hàng sau holder chậm), contention và giới hạn throughput. Khi đã bảo vệ tính đúng đắn, nên đo critical-section duration, queue wait, p95/p99, CPU, thread dumps/JFR và ưu tiên thiết kế giảm shared mutable state hoặc phân vùng ownership nếu cần.

**Đừng dựa vào `Thread.sleep` để “đợi khóa được nhả”**. Trong code đã nắm khóa, sleep không nhả. Ngoài critical section, sleep vẫn không phải tín hiệu đồng bộ; hãy dùng `Condition`, `BlockingQueue`, `CountDownLatch` hoặc hợp đồng khác đúng mục đích.

## 16. Những câu hỏi chọn công cụ trong thực tế

| Yêu cầu | Gợi ý | Tại sao |
| --- | --- | --- |
| Bảo vệ một counter/collection đơn giản với một critical section ngắn | `synchronized`; hoặc atomic class nếu đúng operation | Gọn, tự nhả khóa, dễ review |
| Muốn thử ngay, không chờ nếu tài nguyên bận | `Lock.tryLock()` | Nhận false và xử lý busy |
| Muốn giới hạn thời gian **đợi lấy khóa** | `Lock.tryLock(timeout, unit)` | Hỗ trợ deadline ở acquisition |
| Muốn phản hồi interrupt khi **đang tranh khóa** | `lockInterruptibly()` | Có đường cancel chờ acquisition |
| Cần hai hoặc nhiều nhóm chờ trên cùng state | `ReentrantLock` + nhiều `Condition` | `notEmpty`/`notFull`, tránh đánh thức nhầm nhóm |
| Chỉ một condition đơn giản, chờ theo predicate | `synchronized` + `wait/notifyAll` có thể đủ | Không cần quản lý explicit unlock |
| Cần công bằng hơn khi tranh khóa | Cân nhắc `ReentrantLock(true)` và đo thực tế | Fairness có chi phí, không cam kết lịch CPU tuyệt đối |
| Nhiều reader ít writer trên cấu trúc lớn | Đánh giá `ReadWriteLock` hoặc immutable snapshot | Có thể tăng concurrency đọc, nhưng phức tạp hơn; không tự tốt hơn |
| Dữ liệu cực đơn giản một biến | `AtomicInteger`/`AtomicReference` | Operation cụ thể có thể atomic mà không cần mutex |
| Producer–consumer queue chuẩn | `BlockingQueue` | Có sẵn điều kiện chờ và giao thức thread-safe đã được kiểm chứng |

**`ReadWriteLock`** là interface khác, có `readLock()` và `writeLock()` trả về các `Lock` tương ứng; nó **không tự tương đương** với một `Lock` duy nhất. `StampedLock` cũng là công cụ riêng có API và hợp đồng khác, **không trực tiếp implements `Lock`**. Đừng chuyển thiết kế chỉ vì tên nghe mạnh hơn; chọn theo invariant, đường đọc/ghi, điều kiện chờ, fairness và tải thực tế.

### Java 21+ và virtual threads

Virtual thread vẫn phải bảo vệ shared mutable state theo Java Memory Model như platform thread. `synchronized`, `Lock` và atomic classes vẫn có ý nghĩa; virtual thread **không tự khiến race condition biến mất**. Hành vi blocking/pinning của monitor/lock và khả năng quan sát qua runtime có thể khác giữa các phiên bản JDK; khi triển khai số lượng lớn virtual threads, đo JFR/thread diagnostics theo bản JDK thực tế. Bài học ở đây sử dụng các API ổn định chạy được từ Java 17.

## 17. Bài tập tự kiểm tra

**1. Cả `synchronized` và `ReentrantLock` có bảo đảm visibility không?** Có, khi reader/writer tham gia **cùng monitor hoặc cùng explicit lock** theo hợp đồng unlock→lock. Tự khóa những object khác nhau không tạo bảo đảm chung.

**2. Vì sao `lock.lock()` nên đứng trước `try`?** `unlock()` chỉ được gọi sau acquisition thành công. Đặt acquisition bên trong `try` với `finally { unlock(); }` có thể khiến finally unlock khi chưa có khóa nếu acquisition throw.

**3. Khi thread đợi lấy monitor `synchronized`, gọi `interrupt()` có làm nó thoát khỏi việc chờ monitor và ném `InterruptedException` không?** Không. Muốn interruptible acquisition, dùng `Lock.lockInterruptibly()` hoặc timed `tryLock`. Tuy nhiên `Object.wait()` sau khi đã vào monitor có phản hồi interrupt.

**4. `wait(1000)` có đặt timeout cho việc vào `synchronized` không?** Không. `Object.wait(1000)` là chờ condition với timeout sau khi đang sở hữu monitor; nó nhả monitor trong lúc đợi rồi lấy lại trước khi trả về.

**5. `synchronized (lock)` trên một `ReentrantLock` object có tương đương `lock.lock()` không?** Không. Cái trước lấy intrinsic monitor của object; cái sau dùng explicit lock state.

**6. Vì sao phải dùng `while` quanh `Condition.await()`?** Có thể spurious wakeup, cạnh tranh nhiều consumer, hoặc predicate đã đổi trước khi thread lấy lại khóa. Chỉ predicate đã kiểm tra dưới lock mới quyết định được đi tiếp.

**7. Một buffer có hai condition `notFull` và `notEmpty`; có phải hai mutex khác nhau không?** Không. Chúng là hai wait sets gắn với **một ReentrantLock**, chia nhóm waiter nhưng dữ liệu vẫn được bảo vệ dưới cùng mutex.

**8. Có nên bật `ReentrantLock(true)` cho mọi ứng dụng?** Không. Fairness có tradeoff và không bảo đảm thứ tự scheduling tuyệt đối. Bắt đầu từ tính đúng đắn, theo dõi starvation/latency thực tế, chỉ bật nếu có yêu cầu và số đo chứng minh phù hợp.

**9. `Lock.tryLock()` trả false có nghĩa một thao tác đọc mutable state sau đó an toàn không?** Không. Caller chưa sở hữu lock; không được vào critical section hay coi failure là memory handoff hợp lệ.

**10. Một thread đã `signal()`, waiter có lập tức chạy phần sau `await()` không?** Không. Waiter phải **lấy lại lock** trước khi `await` trở về, và sau đó kiểm tra lại predicate; signal không tự chuyển quyền sở hữu mutex.

## 18. Checklist trước khi lựa chọn hoặc review

- **Invariant cần bảo vệ là gì?** Một phép tăng, check–then–act, nhiều field hay một hàng đợi có condition?
- **Lock identity có đúng không?** Mọi reader/writer dùng cùng monitor hoặc cùng `Lock`, không vô tình trộn hai loại khóa?
- **Có yêu cầu timeout/interrupt khi đợi khóa không?** Nếu có, `Lock.tryLock`/`lockInterruptibly` hữu ích.
- **Nếu dùng `Lock`, mọi đường acquisition thành công có nhả bằng `finally` không?** Có đường `return`/throw quên unlock?
- **Nếu dùng `Condition`, predicate được bảo vệ bằng cùng lock, `await` nằm trong `while` và signal được gọi khi đang giữ khóa chưa?**
- **Nếu dùng `Object.wait`, caller có giữ đúng monitor và kiểm tra lại condition sau khi thức không?**
- **Có giữ khóa khi làm I/O, sleep hoặc gọi callback bất định không?** Xem xét tách việc ra ngoài critical section.
- **Có nhiều khóa cần thứ tự lấy nhất quán không?** Nếu có vòng lock-order, cần thiết kế tránh deadlock.
- **Có cần fairness và đã đo contention/throughput chưa?** Không bật fair lock theo cảm tính.
- **Test có xác minh invariant và nhiều interleaving không?** Có deadline, cancellation và xử lý lỗi worker?
- **Đang dùng virtual threads?** Vẫn phải áp dụng đúng JMM và benchmark trên JDK thực tế.

**Kết luận:** `synchronized` là lựa chọn đơn giản, an toàn theo scope cho monitor mutual exclusion và một wait set gắn với object. `Lock`, đặc biệt `ReentrantLock`, mở thêm quyền kiểm soát acquisition (thử ngay, timeout, interruptible), fairness và nhiều `Condition`. Cả hai chỉ thật sự bảo vệ dữ liệu khi **đúng khóa, đúng phạm vi invariant và đúng giao thức trên mọi đường truy cập**; chọn theo nhu cầu cụ thể và đo hậu quả concurrency thay vì mặc định thay toàn bộ một bên bằng bên kia.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-and-conditions-002">ReentrantLock: lock(), unlock() và finally</a>

<details>
<summary>Xem chi tiết</summary>

# ReentrantLock: lock(), unlock() và finally trong Java

## 1. Mục tiêu và phạm vi

Sau bài học, bạn có thể:

- Giải thích **java.util.concurrent.locks.ReentrantLock** và quan hệ giữa đối tượng khóa, thread sở hữu khóa, critical section và dữ liệu được bảo vệ.
- Viết đúng mẫu **lock.lock() → try → finally → lock.unlock()**, kể cả khi code trong critical section ném exception hoặc return sớm.
- Hiểu **reentrancy** (một thread có thể lấy lại khóa mình đang giữ), **hold count**, trạng thái owner và hậu quả khi gọi unlock() sai.
- Phân biệt **lock()**, **lockInterruptibly()**, **tryLock()** và **tryLock(timeout, unit)** theo khả năng chờ, ngắt và điều kiện nhả khóa.
- Nhận biết bảo đảm **mutual exclusion** và **happens-before** của Lock, giới hạn về fairness, và cách tránh deadlock do lock ordering.
- Nắm trường hợp **Condition.await()/signal()/signalAll()** phù hợp hơn vòng lặp sleep/yield hoặc tự triển khai chờ điều kiện.
- Chọn đúng giữa **synchronized** và ReentrantLock, giữ khóa ngắn và bảo vệ toàn bộ bất biến chia sẻ.

Phạm vi chỉ là **Java concurrency của JDK**. Những cơ chế riêng của Spring được học trong chương khác.

## 2. ReentrantLock là gì?

**ReentrantLock** là một lớp cụ thể của **java.util.concurrent.locks**, triển khai giao diện **Lock**. Nó cung cấp một khóa loại trừ lẫn nhau (mutual exclusion) có tính **reentrant** giống intrinsic monitor mà **synchronized** dùng, nhưng với API điều khiển khóa tường minh.

~~~java
import java.util.concurrent.locks.ReentrantLock;

// Đoạn mã minh họa trong một lớp/phương thức Java.
ReentrantLock lock = new ReentrantLock();

lock.lock();
try {
    // Đọc/ghi dữ liệu dùng chung dưới cùng khóa.
} finally {
    lock.unlock();
}
~~~

Đây là đoạn minh họa cú pháp, không phải chương trình độc lập có main().

Khi một thread đã giữ ReentrantLock, các thread khác muốn lấy cùng đối tượng khóa sẽ phải chờ hoặc thất bại tùy phương thức sử dụng. Khác với **synchronized**, Java **không tự gọi unlock()** khi rời khỏi một khối mã bình thường, return hoặc ném exception. Vì vậy **finally** là yếu tố bảo toàn quyền sở hữu khóa.

Một số khái niệm:

| Khái niệm | Ý nghĩa |
| --- | --- |
| **Lock object** | Đối tượng ReentrantLock dùng chung giữa mọi đoạn mã cần bảo vệ cùng bất biến |
| **Owner** | Thread hiện đang sở hữu khóa |
| **Critical section** | Đoạn đọc/ghi dữ liệu phải thực hiện khi owner nắm khóa |
| **Acquisition** | Lấy khóa thành công qua lock(), lockInterruptibly() hoặc tryLock() |
| **Release** | Gọi unlock() đúng số lần bởi **chính owner** |
| **Hold count** | Số mức khóa còn được thread hiện tại giữ do lấy khóa reentrant |
| **Waiter/contender** | Thread khác đang cố lấy khóa do owner hiện tại chưa nhả |

Tính an toàn phụ thuộc vào việc **tất cả đường truy cập có liên quan** dùng **cùng một đối tượng ReentrantLock**, hoặc một giao thức đồng bộ hóa khác có lập luận JMM thích hợp. Chỉ có biến tên lock trong mỗi thread chưa đủ: nếu các thread tự tạo **new ReentrantLock()** riêng thì không có mutual exclusion giữa chúng.

## 3. Mẫu bắt buộc: lock() rồi try/finally

Mẫu cơ bản đúng:

~~~java
lock.lock();
try {
    updateSharedState();
} finally {
    lock.unlock();
}
~~~

Đây là mã minh họa; **lock** và **updateSharedState()** cần được định nghĩa ở lớp thực tế. Điểm đáng chú ý:

1. **lock.lock()** được gọi trước **try** và phải thành công thì thread mới được xem là owner.
2. Vừa lấy được khóa, đi vào **try** ngay để mọi đường thoát từ critical section đều tới **finally**.
3. Mọi thao tác cần bảo vệ nằm trong phạm vi try.
4. **finally** gọi unlock() đúng một lần tương ứng với lần lock() vừa lấy.
5. Nếu body return, break hoặc ném exception, **finally vẫn được thực thi** khi stack đi qua block đó; việc unlock không phụ thuộc đường thoát thông thường.
6. Nếu code trong finally có logic khác và bản thân nó ném exception trước khi unlock, khóa vẫn có thể bị giữ. Do đó nên để finally đơn giản, chỉ thực hiện thao tác giải phóng cần thiết.

**Vì sao try ở sau lock()?** Nếu một lần lấy khóa có khả năng thất bại mà chương trình vẫn gọi unlock(), thread không phải owner sẽ gặp **IllegalMonitorStateException**. Với lock() thông thường, lời gọi chờ đến khi lấy được khóa, nhưng đặt try **ngay sau** vẫn là mẫu chuẩn để không giải phóng khóa khi chưa sở hữu.

### Sai lầm 1: quên finally

~~~java
lock.lock();
updateSharedState(); // Nếu ném exception...
lock.unlock();        // ...câu này không chạy.
~~~

Đây là đoạn minh họa lỗi, không phải chương trình độc lập. Khóa có thể bị giữ mãi bởi thread đã gặp exception. Những thread khác cần khóa có thể chờ vô hạn, đặc biệt nếu thread owner vẫn còn sống. Nếu thread owner kết thúc sau exception, ReentrantLock **cũng không có cơ chế tự động unlock khi thread chết**; những thread đợi vẫn không thể nhờ vậy đi vào critical section.

### Sai lầm 2: unlock() khi chưa sở hữu

~~~java
// Anti-pattern nếu tryLock thất bại:
if (lock.tryLock()) {
    updateSharedState();
}
lock.unlock(); // Có thể ném IllegalMonitorStateException.
~~~

Nếu tryLock() trả false, caller không được cấp khóa. Nếu tryLock() thành công nhưng updateSharedState() ném exception, unlock bên ngoài cũng có thể bị bỏ qua.

Cách đúng:

~~~java
if (lock.tryLock()) {
    try {
        updateSharedState();
    } finally {
        lock.unlock();
    }
}
~~~

Chỉ nhả khóa khi acquisition thành công. Với **tryLock(timeout, unit)** và **lockInterruptibly()** cũng phải đặt finally trên **đường đi thực sự đã lấy được khóa**.

### Sai lầm 3: return sớm nhưng không nhả khóa

~~~java
lock.lock();
try {
    if (isRejected()) {
        return;
    }
    updateSharedState();
} finally {
    lock.unlock();
}
~~~

Đây là mã minh họa bên trong một phương thức trả void; **isRejected()** là hàm giả định. Return bên trong try vẫn chạy finally trước khi method kết thúc. Vì thế cấu trúc try/finally an toàn hơn việc cố gắng nhớ gọi unlock ở từng nhánh if/else.

## 4. Ví dụ Java 17+: đếm chính xác bằng ReentrantLock

Tệp **ReentrantCounterDemo.java**:

~~~java
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantCounterDemo {
    static final class Counter {
        private final ReentrantLock lock = new ReentrantLock();
        private int value;

        void increment() {
            lock.lock();
            try {
                value++;
            } finally {
                lock.unlock();
            }
        }

        int snapshot() {
            lock.lock();
            try {
                return value;
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();

        Runnable task = () -> {
            for (int i = 0; i < 10_000; i++) {
                counter.increment();
            }
        };

        Thread a = new Thread(task, "worker-a");
        Thread b = new Thread(task, "worker-b");
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("count = " + counter.snapshot());
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 ReentrantCounterDemo.java
java ReentrantCounterDemo
~~~

Stdout:

~~~text
count = 20000
~~~

**Lập luận đúng đắn:** cả hai thread chia sẻ cùng **Counter**, nên đều khóa **cùng Counter.lock**. Mỗi read–modify–write của value diễn ra khi một thread giữ khóa. Không có hai lần tăng cùng đè lên một giá trị cũ. snapshot() cũng đọc dưới khóa đó. Hai join() bảo đảm main đợi xong toàn bộ phép tăng trước khi kiểm tra kết quả.

Nếu increment() dùng ReentrantLock nhưng snapshot() đọc value tự do trong lúc các worker còn chạy, đó là thiết kế visibility không nhất quán. Với dữ liệu bảo vệ bằng lock, hãy thống nhất quy tắc truy cập cho mọi đường có liên quan.

### Độ rộng critical section

Không nên giữ lock cho toàn bộ vòng lặp nếu yêu cầu chỉ cần mỗi phép tăng riêng lẻ là nguyên tử; với một bất biến trải trên nhiều biến, ngược lại cần gói **toàn bộ quyết định nhiều bước** vào một critical section.

Ví dụ nghiệp vụ kiểm tra rồi trừ tồn kho:

~~~java
// Mã minh họa trong phương thức boolean reserve():
lock.lock();
try {
    if (stock <= 0) {
        return false;
    }
    stock--;
    return true;
} finally {
    lock.unlock();
}
~~~

**stock** là trường chia sẻ của cùng đối tượng, được bảo vệ bằng lock đã có. Đặt check ở ngoài rồi chỉ khóa quanh stock-- sẽ còn **logic race** vì thread khác có thể thay đổi stock giữa hai thao tác.

## 5. finally hoạt động ngay cả khi exception

Tệp **UnlockOnExceptionDemo.java**:

~~~java
import java.util.concurrent.locks.ReentrantLock;

public class UnlockOnExceptionDemo {
    public static void main(String[] args) {
        ReentrantLock lock = new ReentrantLock();

        try {
            lock.lock();
            try {
                System.out.println("inside = " + lock.isHeldByCurrentThread());
                throw new IllegalStateException("example failure");
            } finally {
                lock.unlock();
            }
        } catch (IllegalStateException ex) {
            System.out.println("caught = " + ex.getMessage());
        }

        System.out.println("after = " + lock.isHeldByCurrentThread());

        lock.lock();
        try {
            System.out.println("acquired again = true");
        } finally {
            lock.unlock();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 UnlockOnExceptionDemo.java
java UnlockOnExceptionDemo
~~~

Stdout:

~~~text
inside = true
caught = example failure
after = false
acquired again = true
~~~

**Giải thích:** exception thoát khỏi try chứa critical section, nhưng finally nhả lock trước khi catch ở ngoài xử lý lỗi. Câu kiểm tra sau catch trả false vì thread hiện tại không còn sở hữu khóa. Lần lock() tiếp theo được phép lấy khóa lại.

Cẩn thận: finally **nhả khóa**, nhưng **không tự rollback dữ liệu đã sửa**. Nếu đang cập nhật nhiều trường và ném exception ở giữa, cần giữ bất biến nhất quán hoặc có chiến lược phục hồi riêng; không nhầm giải phóng khóa với transaction.

## 6. Reentrancy và hold count

Reentrant nghĩa là một thread **đã sở hữu** ReentrantLock có thể gọi **lock() lần nữa** trên cùng đối tượng mà không tự deadlock. Mỗi lần lock thành công làm tăng hold count; mỗi lần unlock của owner làm giảm count. Khóa chỉ thực sự cho thread khác lấy khi hold count trở về 0.

Các phương thức hỗ trợ chẩn đoán:

- **isHeldByCurrentThread()**: thread đang gọi có sở hữu khóa này hay không.
- **getHoldCount()**: số lần thread hiện tại đang giữ khóa; với thread không sở hữu, trả 0.
- **isLocked()**: hiện có thread nào sở hữu khóa không; là snapshot, không thể dùng như điều kiện an toàn để thay lock().
- **isFair()**: khóa có được tạo với chính sách fair hay không.
- **getQueueLength()**: ước lượng số thread chờ, không phải số chính xác để quyết định nghiệp vụ.

### Ví dụ Java 17+: nested locking

Tệp **ReentrantHoldDemo.java**:

~~~java
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantHoldDemo {
    public static void main(String[] args) {
        ReentrantLock lock = new ReentrantLock();

        lock.lock();
        try {
            System.out.println("outer hold = " + lock.getHoldCount());

            lock.lock();
            try {
                System.out.println("inner hold = " + lock.getHoldCount());
            } finally {
                lock.unlock();
            }

            System.out.println("after inner = " + lock.getHoldCount());
        } finally {
            lock.unlock();
        }

        System.out.println("after outer = " + lock.getHoldCount());
    }
}
~~~

~~~bash
javac -encoding UTF-8 ReentrantHoldDemo.java
java ReentrantHoldDemo
~~~

Stdout:

~~~text
outer hold = 1
inner hold = 2
after inner = 1
after outer = 0
~~~

Đây là một thread duy nhất nên thứ tự stdout xác định. **Mỗi lần lock thành công phải có một lần unlock tương ứng**. Nếu chỉ unlock một lần sau hai lần lock, thread vẫn giữ khóa ở mức 1; thread khác chưa thể lấy được.

Tính reentrant hữu ích khi phương thức đang giữ lock gọi một phương thức khác của cùng lớp cũng dùng đúng lock đó. Dù vậy, không nên tùy tiện lạm dụng nhiều cấp khóa hoặc phương thức gọi lẫn nhau quá khó kiểm soát.

### unlock sai owner

**ReentrantLock.unlock()** yêu cầu thread hiện tại đang giữ khóa. Nếu một thread khác gọi unlock(), hoặc caller chưa sở hữu khóa, nó ném **IllegalMonitorStateException**. Không được nhờ một thread khác “dọn khóa hộ” khi owner mắc kẹt; cần thiết kế task và cancel/timeout đúng, không dùng unlock trái owner.

## 7. Bốn con đường acquisition

### 7.1. lock(): đợi cho đến khi lấy được khóa

~~~java
lock.lock();
try {
    performCriticalAction();
} finally {
    lock.unlock();
}
~~~

Nếu đã có owner khác, caller chờ đến khi có thể lấy khóa. **lock() không phải lời gọi lấy khóa có thể bị hủy bằng InterruptedException**. Khi một thread bị interrupt trong lúc chờ, lock() vẫn có thể tiếp tục chờ đến khi lấy được khóa; hành vi chi tiết của interrupt status phụ thuộc hợp đồng triển khai, nhưng không được giả định rằng nó sẽ tự thoát chờ bằng checked exception.

Dùng lock() cho critical section phải hoàn thành và có thời gian giữ khóa ngắn, không cần khả năng hủy việc **đang chờ lấy khóa**.

### 7.2. lockInterruptibly(): lấy khóa có thể bị ngắt

~~~java
lock.lockInterruptibly();
try {
    performCriticalAction();
} finally {
    lock.unlock();
}
~~~

**lockInterruptibly() throws InterruptedException** khi bị ngắt trước khi lấy khóa; trong trường hợp đó caller **không sở hữu khóa**, nên finally bên trên không được chạy. Vì try bắt đầu sau lời gọi acquisition nên mẫu này đúng.

Nếu đã lấy khóa, một interrupt phát sinh sau đó không tự động nhả khóa: code trong try phải hợp tác xử lý interrupt theo chính sách của thao tác, và finally vẫn phải unlock.

### 7.3. tryLock(): thử ngay, trả boolean

~~~java
if (lock.tryLock()) {
    try {
        performCriticalAction();
    } finally {
        lock.unlock();
    }
} else {
    // Không có quyền vào critical section lúc này.
}
~~~

**tryLock()** trả true khi lấy được khóa ngay, false khi chưa thể; không phải lời gọi đợi có hạn. Nếu thread đã giữ khóa thì có thể lấy reentrant và trả true. Caller phải quyết định rõ fallback khi false; đừng bỏ qua công việc quan trọng hoặc chạy phần critical section không khóa.

### 7.4. tryLock(timeout, unit): chờ có giới hạn

~~~java
// Minh họa trong phương thức khai báo throws InterruptedException:
boolean acquired = lock.tryLock(500, java.util.concurrent.TimeUnit.MILLISECONDS);
if (!acquired) {
    return;
}
try {
    performCriticalAction();
} finally {
    lock.unlock();
}
~~~

Đoạn này minh họa phương thức trả void, với lock và performCriticalAction() đã khai báo. **tryLock(timeout, unit)** có thể chờ tối đa khoảng thời gian được chỉ định, trả **false** nếu không lấy được khóa trước hạn, hoặc ném **InterruptedException** nếu bị ngắt trong khi chờ. Thời điểm timeout chịu độ phân giải timer và lập lịch; không dùng nó để hứa thời điểm tuyệt đối.

**Chỉ nhả khóa sau khi acquired == true.** Một lần timeout hoặc interruption trước acquisition không tạo nghĩa vụ unlock.

### So sánh

| Phương thức | Khi khóa đang bận | InterruptedException | Khi nào cần unlock? |
| --- | --- | --- | --- |
| **lock()** | Chờ đến khi lấy được | Không khai báo | Sau khi lock() trả về do lấy được |
| **lockInterruptibly()** | Chờ, có thể hủy do interrupt | Có | Chỉ sau khi lấy thành công |
| **tryLock()** | Trả false ngay | Không khai báo | Chỉ khi trả true |
| **tryLock(timeout, unit)** | Chờ trong giới hạn, hoặc trả false | Có | Chỉ khi trả true |

Tất cả đều tuân thủ quyền sở hữu: thread đã acquire phải release đủ số lần bằng chính thread đó.

## 8. Ví dụ tryLock(): bỏ qua tác vụ khi không lấy được khóa

Ví dụ dùng latch để khống chế thời điểm main thử lấy khóa, tránh các bài test mong kết quả dựa trên sleep.

Tệp **TryLockDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class TryLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                locked.countDown();
                try {
                    release.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                lock.unlock();
            }
        }, "holder");

        holder.start();
        locked.await();

        boolean acquiredWhileBusy = lock.tryLock();
        System.out.println("while busy = " + acquiredWhileBusy);
        if (acquiredWhileBusy) {
            lock.unlock();
        }

        release.countDown();
        holder.join();

        boolean acquiredAfter = lock.tryLock();
        System.out.println("after release = " + acquiredAfter);
        if (acquiredAfter) {
            try {
                System.out.println("main owns lock = " + lock.isHeldByCurrentThread());
            } finally {
                lock.unlock();
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 TryLockDemo.java
java TryLockDemo
~~~

Stdout trong điều kiện chạy bình thường, không có interrupt bên ngoài:

~~~text
while busy = false
after release = true
main owns lock = true
~~~

Khi locked.await() trên main kết thúc, holder đã lấy lock và chưa được phép rời phần chờ release.await(). Lần tryLock đầu của main trả false. Sau khi phát tín hiệu và join(), holder đã unlock; lần tryLock tiếp theo lấy được khóa. Trong cả hai trường hợp, chương trình chỉ gọi unlock khi chính main đã lấy khóa thành công.

Trong ứng dụng thật, sau khi tryLock() trả false cần có chính sách cụ thể: bỏ qua công việc không quan trọng, retry có giới hạn, queue lại, hoặc báo bận. Đừng biến false thành “thành công” nếu tác vụ chưa được thực hiện.

## 9. Ví dụ lockInterruptibly(): hủy việc chờ khóa

Tệp **InterruptibleLockDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class InterruptibleLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch attempting = new CountDownLatch(1);

        lock.lock();
        try {
            Thread worker = new Thread(() -> {
                attempting.countDown();
                try {
                    lock.lockInterruptibly();
                    try {
                        System.out.println("worker: acquired");
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    System.out.println("worker: interrupted");
                }
            }, "interruptible-worker");

            worker.start();
            attempting.await();
            worker.interrupt();
            worker.join();
            System.out.println("main: still owns lock = "
                    + lock.isHeldByCurrentThread());
        } finally {
            lock.unlock();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 InterruptibleLockDemo.java
java InterruptibleLockDemo
~~~

Stdout:

~~~text
worker: interrupted
main: still owns lock = true
~~~

**Giải thích:**

1. Main lấy lock trước khi khởi chạy worker, và giữ đến sau worker.join().
2. Worker chuẩn bị gọi lockInterruptibly(), báo điểm bắt đầu bằng latch.
3. Main gửi interrupt trước khi tự nhả lock. Dù tín hiệu đến trước hoặc trong lúc worker chờ, lockInterruptibly() xử lý interrupt và ném InterruptedException thay vì cấp khóa khi main vẫn đang giữ.
4. Worker không chạy finally unlock ở nhánh acquisition thất bại; nó chỉ khôi phục interrupt status và báo đã bị ngắt.
5. Main đợi worker kết thúc rồi xác nhận chính mình còn giữ khóa, sau đó finally mới unlock.

Nếu thay lockInterruptibly() bằng lock() thì không thể dựa vào interrupt để khiến worker thoát khỏi quá trình chờ như trên; tránh chạy cùng cấu trúc join khi main vẫn giữ khóa vì có thể tự tạo bế tắc.

## 10. ReentrantLock và Java Memory Model

**Lock** bảo đảm nhiều hơn việc chỉ cho một thread vào critical section: các triển khai Lock như ReentrantLock có ngữ nghĩa đồng bộ hóa bộ nhớ tương ứng với monitor.

- Một **unlock** thành công đối với lock có quan hệ *happens-before* với **lock** thành công kế tiếp đối với cùng lock.
- Các ghi xảy ra trước lần unlock được công bố cho thread sau khi lấy lock theo quy tắc JMM.
- **tryLock() thất bại** không có hiệu ứng synchronization của việc lấy khóa thành công.
- **isLocked()**, **getQueueLength()**, **hasQueuedThreads()** là quan sát phục vụ chẩn đoán; không dùng giá trị snapshot để thay việc acquire.
- Lock/unlock không khiến các truy cập dữ liệu từ code **không cùng giao thức** tự động an toàn.

~~~text
Thread A                       Thread B
--------                       --------
lock.lock()                    lock.lock()  [chờ]
try {                          try {
  shared = 42;                    read shared -> 42
} finally {                   } finally {
  lock.unlock();                  lock.unlock();
}                              }

A unlock ---- happens-before ----> B lock
(nếu B lấy khóa sau A và không có ghi xen giữa)
~~~

Đây là một sơ đồ theo giả định có hai critical section và không có writer khác chen giữa. Trong chương trình thật, phải xét mọi đường ghi và mọi khóa liên quan.

### ReentrantLock không thay thế atomicity của một nghiệp vụ nhiều bước nếu scope khóa sai

~~~java
// Mã minh họa logic race: hai critical section tách rời.
lock.lock();
try {
    if (stock <= 0) {
        return false;
    }
} finally {
    lock.unlock();
}

lock.lock();
try {
    stock--;
    return true;
} finally {
    lock.unlock();
}
~~~

Dù từng critical section hợp lệ, một thread khác có thể trừ stock giữa hai lần nhả/lấy. Phải khóa **bao quanh toàn quyết định** kiểm tra và trừ như ví dụ reserve ở phần 4.

## 11. Fair và nonfair ReentrantLock

Constructor mặc định:

~~~java
ReentrantLock fastLock = new ReentrantLock();
ReentrantLock fairLock = new ReentrantLock(true);
~~~

Đây là đoạn khai báo có thể đặt trong phương thức Java, không có main() độc lập.

- **Nonfair lock** là mặc định; một thread mới có thể giành lock trước các thread khác đã chờ, tùy lịch chạy.
- **Fair lock** được thiết kế để ưu tiên theo thứ tự chờ của các thread trong trường hợp cạnh tranh, nhưng **không cam kết fairness tuyệt đối của lịch CPU**, không cam kết mỗi thread nhận lượt đúng nhau và có thể làm giảm throughput.
- **tryLock() không có timeout** có thể giành được khóa ngay cả khi có thread khác đang chờ **dù lock được cấu hình fair**. Đây là hành vi barging được tài liệu hóa; không suy ra “fair” từ mọi lời gọi giống nhau.
- Khi dùng **tryLock(0, TimeUnit.SECONDS)** trên fair lock, lời gọi theo cơ chế acquisition có điều kiện và tôn trọng chính sách fairness của overload thời gian, đồng thời có thể bị interrupt.
- Không chọn fair=true theo thói quen. Cần đánh giá yêu cầu progress, độ trễ, tính công bằng và throughput của ứng dụng thực tế.

**Quan trọng:** Fairness không giải quyết **deadlock**, không bảo vệ dữ liệu ngoài khóa và không bảo đảm timeout thực thi chính xác từng nano giây.

## 12. Condition: chờ tín hiệu gắn với ReentrantLock

**Condition** được tạo từ **lock.newCondition()**, cung cấp tập thao tác chờ/phát tín hiệu tương tự Object.wait()/notify/notifyAll() nhưng gắn với một Lock tường minh.

- **condition.await()**: yêu cầu thread hiện tại đang giữ lock; nó nhả khóa tương ứng trong khi chờ và lấy lại khóa trước khi trả về hoặc ném InterruptedException theo hợp đồng.
- **condition.signal()**: báo cho một waiter của condition; waiter vẫn phải lấy lại lock mới tiếp tục.
- **condition.signalAll()**: báo cho tất cả waiter của condition; họ sẽ tranh lock và kiểm tra lại điều kiện.
- Vì có thể **spurious wakeup**, luôn dùng **while (!predicate) condition.await()**, không dùng một if duy nhất.
- Phải thao tác **await/signal dưới đúng lock** liên kết với condition. Gọi không phải owner dẫn tới IllegalMonitorStateException ở các thao tác có yêu cầu quyền sở hữu.

Ví dụ mẫu:

~~~java
// Đoạn minh họa: lock, ready và condition đã được khai báo.
lock.lock();
try {
    while (!ready) {
        condition.await();  // throws InterruptedException
    }
    consume();
} finally {
    lock.unlock();
}
~~~

Đây là mã minh họa trong phương thức có xử lý/khai báo InterruptedException; consume() là logic giả định. Một thread khác đổi ready=true rồi signal/signalAll khi sở hữu cùng lock.

### Ví dụ Java 17+: đợi condition và unlock đúng cách

Tệp **ConditionDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ConditionDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Condition readyCondition = lock.newCondition();
        boolean[] ready = {false};
        CountDownLatch started = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            lock.lock();
            try {
                System.out.println("worker: waiting");
                started.countDown();

                while (!ready[0]) {
                    try {
                        readyCondition.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                System.out.println("worker: resumed");
            } finally {
                lock.unlock();
            }
        }, "condition-worker");

        worker.start();
        started.await();

        lock.lock();
        try {
            ready[0] = true;
            System.out.println("main: signaled");
            readyCondition.signalAll();
        } finally {
            lock.unlock();
        }

        worker.join();
        System.out.println("main: done");
    }
}
~~~

~~~bash
javac -encoding UTF-8 ConditionDemo.java
java ConditionDemo
~~~

Stdout nếu không có interrupt bên ngoài:

~~~text
worker: waiting
main: signaled
worker: resumed
main: done
~~~

Worker giữ lock, in dòng đầu, rồi gọi await() nếu ready chưa đúng. await() nhả lock và đợi. Main đã đợi started, nên chỉ sau khi worker báo bắt đầu mới cố lấy lock, đặt ready và signalAll. Nếu main tới sớm hơn lúc worker thật sự await(), nó phải chờ lock; sau đó điều kiện vẫn nhất quán vì cả hai đọc/ghi dưới cùng lock. Worker khi tiếp tục lấy lại lock và kiểm tra ready trong while.

Nếu điều kiện đang đúng từ trước, while bỏ qua await; vì thế không mất tín hiệu ngay cả khi thứ tự scheduling thay đổi. **Lock/Condition chỉ phục vụ synchronization**, không phải kết quả nghiệp vụ của tác vụ.

### Condition khác với CountDownLatch

Một Condition có thể dùng lặp lại với predicate thay đổi theo thời gian. CountDownLatch là bộ đếm giảm đến 0 và không reset về giá trị ban đầu; nó hữu ích cho sự kiện một lần như “đã sẵn sàng” hoặc “đã hoàn tất”. Với producer/consumer thường nên xét BlockingQueue thay vì tự xây mọi giao thức condition.

## 13. Deadlock: ReentrantLock không tự cứu khỏi vòng chờ

Một thread có thể lấy nhiều ReentrantLock. Nếu hai thread lấy hai lock theo thứ tự ngược nhau, có thể xuất hiện vòng chờ:

~~~text
Thread A                          Thread B
--------                          --------
lock(first)                       lock(second)
  muốn lock(second)                 muốn lock(first)
  -> chờ B                          -> chờ A
                 DEADLOCK
~~~

Tính **reentrant** chỉ cho phép một thread lấy lại **cùng khóa mình đang giữ**. Nó không giải quyết việc A giữ khóa 1 đợi khóa 2 do B giữ, trong khi B đợi khóa 1.

Biện pháp phòng tránh:

1. Định nghĩa **thứ tự lock nhất quán** (ví dụ theo ID bất biến) khi một thao tác phải giữ nhiều lock.
2. Giữ critical section ngắn; tránh gọi I/O, callback không kiểm soát hoặc chờ thread khác kết thúc khi đang giữ lock.
3. Cân nhắc tryLock(timeout) nếu yêu cầu cần tránh chờ mãi; **khi không lấy đủ lock, phải nhả toàn bộ lock đang giữ và xử lý retry an toàn**.
4. Tránh thiết kế mà thread A giữ khóa rồi gọi join() đợi thread B trong khi B phải lấy chính khóa đó mới hoàn thành.
5. Không dùng sleep/yield để “giải deadlock”: hai phương thức không giải phóng khóa, cũng không thiết lập lock ordering.

Với nhiều ReentrantLock, cần quan sát lock owner/stack traces và các mối quan hệ chờ thông qua thread dump, ThreadMXBean hoặc công cụ chẩn đoán phù hợp. ReentrantLock có API theo dõi queue, nhưng snapshot không thay thế lập luận về thứ tự và ownership.

## 14. Chọn giữa synchronized và ReentrantLock

| Tiêu chí | synchronized | ReentrantLock |
| --- | --- | --- |
| Cách sử dụng | Cú pháp ngôn ngữ, monitor intrinsic của object | Lớp Lock với API tường minh |
| Nhả khóa | Tự động khi rời synchronized | **Phải gọi unlock() trong finally** |
| Reentrant | Có | Có |
| Mutual exclusion và happens-before | Có, khi dùng cùng monitor | Có, khi dùng cùng lock |
| Thử lấy ngay | Không có try-enter trực tiếp | tryLock() |
| Thử chờ có timeout | Không có tham số timeout cho monitor enter | tryLock(timeout, unit) |
| Chờ lấy khóa có thể ngắt | Chờ vào monitor không ném InterruptedException | lockInterruptibly() |
| Condition queue | Object.wait/notify/notifyAll | Condition.await/signal/signalAll |
| Cấu hình fairness | Không có tham số fairness | ReentrantLock(true), có giới hạn |
| Nguy cơ bỏ quên giải phóng | Thấp hơn do cấu trúc ngôn ngữ | Cao hơn nếu thiếu finally |

**Ưu tiên synchronized** khi chỉ cần mutual exclusion ngắn, dễ đọc, không cần tính năng acquisition đặc biệt. **Chọn ReentrantLock** khi thực sự cần tryLock, timeout, interruptible lock acquisition, nhiều Condition riêng, hoặc điều khiển lock tường minh có chứng cứ.

Không chọn ReentrantLock chỉ vì “nhanh hơn synchronized”. JIT, mức tranh chấp, độ dài critical section, topology và yêu cầu throughput/latency ảnh hưởng kết quả thực tế. Đo benchmark có phương pháp nếu hiệu năng là lý do lựa chọn.

## 15. Một số lỗi thực tế và cách sửa

| Sai lầm | Hậu quả | Sửa |
| --- | --- | --- |
| Gọi lock() nhưng unlock ngoài finally | Exception/return khiến lock bị giữ | Dùng try/finally ngay sau acquire |
| Gọi unlock() dù tryLock() trả false | IllegalMonitorStateException | Chỉ unlock nếu đã lấy thành công |
| Mỗi thread tạo new ReentrantLock riêng | Không loại trừ truy cập trên biến chung | Dùng chung một lock object |
| Đọc shared state mà không dùng giao thức khóa | Thiếu visibility/atomicity | Đọc và ghi theo cùng giao thức |
| Khóa từng bước check và update riêng | Logic race giữa hai lượt khóa | Bảo vệ toàn bộ bất biến |
| Gọi lock() hai lần, unlock() một lần | Hold count còn 1, thread khác bị chặn | Cân bằng mỗi acquire/release |
| Gọi Condition.await() mà không giữ lock | IllegalMonitorStateException | Acquire lock trước await và finally unlock |
| Dùng if khi chờ condition | Spurious wakeup/lịch xen kẽ có thể làm sai | Dùng while kiểm tra predicate |
| Gọi sleep() khi đang giữ lock | Giữ khóa suốt lúc ngủ, tăng contention | Tách phần chờ khỏi critical section nếu có thể |
| Chờ join()/Future.get() khi đang giữ lock cần cho worker | Có thể tự tạo deadlock | Xem dependency, nhả lock trước khi chờ |
| Tin getQueueLength()/isLocked() là một kiểm tra an toàn để quyết định update | Snapshot thay đổi ngay | Dùng acquire/CAS/condition thực sự |
| Tin fair=true nghĩa là không starvation trong mọi tình huống | Scheduler và tryLock có ngoại lệ; vẫn có chờ dài | Định nghĩa SLA và đo thực tế |
| Interrupt worker đang giữ lock thì khóa tự nhả | Không tự nhả; thread phải tới finally | Viết code cleanup và exit hợp tác |
| Dùng ReentrantLock bảo vệ trạng thái trên nhiều JVM | Lock chỉ dùng chung giữa các thread có cùng đối tượng trong JVM | Cần coordination ở nguồn dữ liệu chia sẻ |

### Nhận diện đặc biệt: unlock() không phải wait/notify

**unlock()** làm giảm hold count và khi về 0 cho phép thread khác lấy lock. Nó **không phải Condition.signal()**. Nếu worker đang chờ một condition predicate, việc nhả lock của thread khác không tự tương đương một tín hiệu signal mà worker có thể dùng để rời await(). Cần cập nhật predicate và phát signal theo hợp đồng của Condition.

### Có nên dùng isLocked() trước lock()?

Không cần và có thể sai về mặt điều phối. **isLocked()** chỉ là ảnh chụp: ngay sau khi trả false, thread khác có thể lấy khóa. Nếu muốn không chờ, dùng **tryLock()**. Nếu muốn chờ đến khi lấy được, gọi **lock()**. Chọn API quyết định theo yêu cầu, không kiểm tra rồi hành động dựa trên snapshot.

## 16. Checklist áp dụng vào code

1. Xác định **đối tượng dữ liệu và invariant** được bảo vệ; mọi đường truy cập liên quan phải dùng cùng giao thức.
2. Lưu ReentrantLock làm field **private final** nếu khóa bảo vệ trạng thái nội bộ của một đối tượng.
3. Đặt **lock.lock()** ngay trước **try**. Bất kỳ đoạn nào có acquisition thành công phải đi tới **finally unlock()**.
4. Nếu dùng **tryLock()**, chỉ vào try khi trả true. Nếu dùng **lockInterruptibly()/timed tryLock**, xử lý InterruptedException trên đường chưa sở hữu.
5. Không giữ lock khi làm I/O/chờ lâu nếu bất biến không bắt buộc; tránh callback ngoài kiểm soát trong critical section.
6. Với nhiều lock, định nghĩa thứ tự acquisition và cách cleanup khi lấy không đủ.
7. Với Condition, giữ lock khi await/signal, lưu predicate dưới cùng lock và kiểm tra predicate trong **while**.
8. Viết test kiểm tra invariants sau khi các worker kết thúc bằng join/Future/latch; đừng chốt correctness chỉ từ log hoặc sleep.
9. Nếu lỗi/timeout/cancellation có thể xảy ra, xác định chính sách **đã làm được phần nào** và trạng thái nghiệp vụ; finally chỉ giải phóng khóa, không rollback side effect.
10. Nếu dữ liệu trải qua nhiều JVM/process, ReentrantLock cục bộ không thay thế cơ chế đồng bộ của kho dữ liệu chung.

## 17. Tự kiểm tra

**Câu 1.** Vì sao không viết lock.lock(); doWork(); lock.unlock();?  
**Trả lời:** Nếu doWork ném exception hoặc return trước unlock, khóa vẫn bị giữ. finally đảm bảo cleanup khi rời phạm vi.

**Câu 2.** Nếu tryLock() trả false thì có được unlock() không?  
**Trả lời:** Không. Caller chưa sở hữu khóa; unlock() có thể ném IllegalMonitorStateException.

**Câu 3.** ReentrantLock.lock() hai lần và unlock() một lần thì thread khác lấy được chưa?  
**Trả lời:** Chưa; hold count còn 1. Phải có unlock tương ứng lần lấy còn lại.

**Câu 4.** lockInterruptibly() khác lock() thế nào?  
**Trả lời:** Nó cho phép bỏ việc chờ lấy khóa bằng InterruptedException khi bị interrupt, thay vì chờ không thể hủy theo cơ chế đó.

**Câu 5.** Main gọi lock.isLocked() thấy false rồi sửa biến ngay có an toàn không?  
**Trả lời:** Không. Snapshot có thể đổi trước khi sửa; cần acquire thành công hoặc cơ chế đồng bộ đúng.

**Câu 6.** Một Condition.await() có tự nhả lock không?  
**Trả lời:** Có, nó nhả lock liên kết trong lúc chờ và lấy lại trước khi tiếp tục theo hợp đồng. Waiter phải giữ lock khi bắt đầu await và dùng while kiểm tra điều kiện.

**Câu 7.** Nếu class đã dùng ReentrantLock bảo vệ counter, một reader không khóa có luôn thấy giá trị mới không?  
**Trả lời:** Không. Reader phải tham gia giao thức đồng bộ hóa/happens-before thích hợp.

**Câu 8.** Khi một thread giữ lock rồi gọi Future.get() chờ worker mà worker cần cùng lock, điều gì có thể xảy ra?  
**Trả lời:** Deadlock: thread gọi không nhả khóa trước khi worker hoàn tất, còn worker không hoàn tất vì không lấy được khóa.

**Câu 9.** Fair lock có bảo đảm tryLock() không bao giờ vượt hàng không?  
**Trả lời:** Không. tryLock() không timeout có thể lấy khóa ngay kể cả khi lock fair đang có thread khác chờ.

**Câu 10.** Có thể thay mọi synchronized bằng ReentrantLock và luôn tăng hiệu năng không?  
**Trả lời:** Không. Hai cơ chế có trade-off khác nhau, và cần benchmark trên workload thực. ReentrantLock phù hợp khi cần tính năng API riêng, không phải lựa chọn tự động nhanh hơn.

## 18. Ghi nhớ

**ReentrantLock** cung cấp mutual exclusion và memory-ordering giống lock của monitor nhưng yêu cầu quản lý quyền sở hữu một cách tường minh. **Khi đã acquire thành công, luôn unlock trong finally; khi acquisition thất bại, không unlock.** Với lock() thông thường, đặt try ngay sau lock(). Với tryLock, chỉ chạy try/finally khi được cấp khóa. Với lockInterruptibly hoặc tryLock có timeout, phân biệt rõ nhánh InterruptedException, nhánh không lấy được khóa và nhánh đã sở hữu.

Dùng tính **reentrant** có kiểm soát, giữ **hold count** cân bằng, dùng Condition với predicate trong while, và xem xét lock ordering khi có nhiều khóa. Hãy thiết kế critical section theo **toàn bộ bất biến** của dữ liệu và dùng API Java phù hợp với yêu cầu chờ, hủy, timeout, thay vì phụ thuộc timing ngẫu nhiên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-and-conditions-003">tryLock() và timeout khi tranh chấp khóa</a>

<details>
<summary>Xem chi tiết</summary>

# tryLock() và timeout khi tranh chấp khóa trong Java

## 1. Mục tiêu bài học

Khi nhiều thread cần truy cập cùng trạng thái, dùng `synchronized` hoặc `Lock` có thể bảo vệ critical section. Nhưng trong hệ thống chịu tải, **chờ khóa mãi** không luôn là lựa chọn phù hợp: người gọi có thể muốn thử một lần, chờ tối đa một khoảng thời gian, từ chối sớm hoặc chuyển sang tác vụ khác.

`ReentrantLock` cung cấp hai phương thức quan trọng:

~~~java
boolean acquired = lock.tryLock();                 // thử ngay, không chờ
boolean acquired = lock.tryLock(200, TimeUnit.MILLISECONDS);
// chờ lấy khóa tối đa một khoảng cho trước, có thể bị interrupt
~~~

Sau bài này, bạn có thể:

- Phân biệt `lock()`, `lockInterruptibly()`, `tryLock()` và `tryLock(long, TimeUnit)` theo khả năng chờ, timeout, interrupt và kết quả.
- Xác định đúng thread nào đang sở hữu `ReentrantLock`; chỉ `unlock()` sau khi chính thread đó đã lấy khóa thành công.
- Giải thích chính xác khi nào timed tryLock trả `true`, `false` hoặc ném `InterruptedException`; tránh nhầm timeout lấy khóa với timeout toàn bộ nghiệp vụ.
- Thiết kế một nhánh fallback có chủ đích khi lock đang bận, thay vì bỏ qua công việc quan trọng một cách âm thầm.
- Hiểu sự khác biệt giữa fair lock và non-fair lock, đặc biệt việc `tryLock()` **không tôn trọng fairness setting**.
- Sử dụng giới hạn thời gian, deadline, cancellation và logging/metric phù hợp; tránh lỗi deadlock, double unlock và mất interrupt.
- Chạy chương trình Java 17+ tái hiện cạnh tranh khóa **xác định**, không phụ thuộc vào việc đoán lịch chạy bằng `sleep()`.

**Phạm vi:** Java SE, `java.util.concurrent.locks` và Java Memory Model. Các ví dụ độc lập với thư viện bên ngoài, dùng Java 17 trở lên.

## 2. ReentrantLock và quyền sở hữu khóa

`ReentrantLock` là mutual exclusion lock: một thời điểm chỉ **một thread** sở hữu lock. Khi thread đã giữ lock, cùng thread đó có thể lấy lại khóa theo tính **reentrant**, và mỗi lần lấy thêm phải được cân bằng bằng lần nhả tương ứng.

~~~java
import java.util.concurrent.locks.ReentrantLock;

class Counter {
    private final ReentrantLock lock = new ReentrantLock();
    private int value;

    void increment() {
        lock.lock();
        try {
            value++;
        } finally {
            lock.unlock();
        }
    }

    int get() {
        lock.lock();
        try {
            return value;
        } finally {
            lock.unlock();
        }
    }
}
~~~

Đây là class Java hợp lệ nhưng chưa có `main`, minh họa giao thức: **mọi đường đọc/ghi biến value cùng dùng một lock**. Khi một thread khóa thành công và thoát với `unlock()`, thao tác của thread đó có memory synchronization effect tương đương thành công của monitor unlock; thread khác thành công khi acquire cùng lock có quan hệ visibility/ordering theo hợp đồng `Lock` và JMM.

Khác `synchronized`, `ReentrantLock` **không tự nhả** khi method kết thúc. Nếu quên `unlock()`, thread khác có thể đợi vô hạn. Vì thế phải luôn đặt việc nhả khóa trong `finally`, với điều kiện thread hiện tại thực sự sở hữu khóa.

`tryLock` **không đồng nghĩa chỉ đọc trạng thái lock**: khi trả `true`, nó đã **acquire** lock và tạo trách nhiệm `unlock()`. Khi trả `false`, caller **không nhận thêm quyền sở hữu** lock.

## 3. Bốn hình thức lấy khóa cần phân biệt

| API | Chờ khi khóa do thread khác giữ? | Interrupt khi đang đợi | Hợp đồng kết quả |
|---|---|---|---|
| `lock()` | Chờ cho đến khi có thể lấy | Không ném `InterruptedException` trong lúc chờ | Trả về `void` sau khi lấy được. |
| `lockInterruptibly()` | Chờ, nhưng có thể hủy bằng interrupt | Ném `InterruptedException` (nếu bị interrupt theo hợp đồng) | `void` khi lấy được, exception khi bị ngắt. |
| `tryLock()` | **Không chờ** | Không có pha đợi interruptible; không ném `InterruptedException` | `true` nếu lấy được ngay, `false` nếu thread khác đang giữ. |
| `tryLock(timeout, unit)` | Chờ tối đa khoảng yêu cầu nếu cần | **Có**, ném `InterruptedException` | `true` nếu lấy được; `false` nếu hết thời gian trước khi lấy; exception nếu bị interrupt. |

Điểm cần nắm: mọi API trên đều có thể thành công **ngay khi khóa đang rảnh**; `tryLock` không có nghĩa “đợi rồi có thể bỏ cuộc” trong mọi trường hợp, vì overload không tham số **không đợi**.

### Sơ đồ của timed tryLock

~~~text
             gọi tryLock(timeout, unit)
                         |
          đã bị interrupt tại entry?
                  /               \
               có                 không
               |                   |
      InterruptedException     khóa có thể acquire?
                                   /        \
                                 có          chưa
                                 |            |
                              true       đợi tối đa
                              |          thời gian
                       vào critical       |
                           section      ┌─┴───────────┐
                           /            |             |
                        unlock()     acquire         interrupt
                                       |             |
                                      true         exception
                                                   |
                                               hết hạn trước
                                                   |
                                                 false
~~~

Đây là sơ đồ logic cho điều kiện và kết quả, **không quy định scheduler phải đánh thức hoặc hoàn tất tác vụ đúng tại mốc thời gian**. Khi chờ, nhiều sự kiện có thể đến gần như đồng thời. `ReentrantLock` ưu tiên phản hồi interrupt theo contract, nên ứng dụng phải xử lý nhánh đó độc lập với timeout.

## 4. tryLock(): thử một lần, phản hồi ngay

Đối với `ReentrantLock`, `tryLock()`:

1. Nếu lock chưa bị thread khác giữ, acquire ngay rồi trả `true`.
2. Nếu **thread hiện tại đã sở hữu lock**, acquire reentrantly, tăng hold count rồi trả `true`.
3. Nếu thread khác đang giữ lock, trả `false` **ngay**, không đăng ký chờ lock được nhả.
4. Không khai báo `throws InterruptedException` và không cung cấp cơ chế đợi có thể hủy bằng interrupt.

Mẫu chuẩn:

~~~java
import java.util.concurrent.locks.ReentrantLock;

class NonBlockingCounter {
    private final ReentrantLock lock = new ReentrantLock();
    private int count;

    boolean tryIncrement() {
        if (!lock.tryLock()) {
            return false; // bận: caller quyết định bỏ qua, thử lại hoặc báo lỗi
        }
        try {
            count++;
            return true;
        } finally {
            lock.unlock();
        }
    }
}
~~~

**Điều kiện nghiệp vụ phải rõ:** `false` ở đây biểu thị “không nhận lock ngay”, **không** nhất thiết là “tác vụ thất bại vĩnh viễn”. Nếu counter ghi nhận giao dịch bắt buộc, im lặng bỏ qua lần tăng có thể làm **mất dữ liệu nghiệp vụ**. Caller cần quyết định queue, retry có giới hạn, trả trạng thái quá tải hoặc cơ chế đáng tin cậy khác.

### Tại sao kiểm tra isLocked() rồi lock() là sai?

~~~java
if (!lock.isLocked()) {
    lock.lock(); // vẫn có thể bị chờ!
    try {
        doWork();
    } finally {
        lock.unlock();
    }
}
~~~

`isLocked()` chỉ trả một quan sát nhất thời, không reserve khóa. Thread khác có thể acquire giữa `isLocked()` và `lock()`: một lỗi **check-then-act**. `tryLock()` gộp “kiểm tra khả dụng + acquire” thành **một thao tác nguyên tử theo hợp đồng lock**. Các phương thức `isLocked()`, `hasQueuedThreads()`, `getQueueLength()` thích hợp hơn cho quan sát/monitoring, không dùng để quyết định chắc chắn thread tiếp theo có lấy được lock.

## 5. Ví dụ chạy độc lập 1: một thread đang giữ lock

Chương trình này dàn dựng đúng tình huống: main giữ lock, thread khác thử lấy lock. Vì main chỉ nhả sau khi thread kia hoàn tất, kết quả `tryLock() = false` không phụ thuộc lịch chạy.

Lưu tệp **ImmediateTryLockDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class ImmediateTryLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        AtomicBoolean acquiredByWorker = new AtomicBoolean(true);

        lock.lock(); // main sở hữu lock
        try {
            Thread worker = new Thread(() -> {
                boolean acquired = lock.tryLock();
                acquiredByWorker.set(acquired);
                if (acquired) {
                    try {
                        // Có thể xử lý shared state ở đây.
                    } finally {
                        lock.unlock();
                    }
                }
            }, "worker");

            worker.start();
            worker.join(); // main vẫn giữ lock trong lúc worker thử
            System.out.println("whileMainHolds=" + acquiredByWorker.get());
        } finally {
            lock.unlock(); // main nhả quyền sở hữu của chính mình
        }

        boolean acquiredAfterRelease = lock.tryLock();
        try {
            System.out.println("afterRelease=" + acquiredAfterRelease);
        } finally {
            if (acquiredAfterRelease) {
                lock.unlock();
            }
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac ImmediateTryLockDemo.java
java ImmediateTryLockDemo
~~~

Kết quả:

~~~text
whileMainHolds=false
afterRelease=true
~~~

**Giải thích:**

- main gọi `lock.lock()` và giữ lock trong khối `try`.
- worker là thread khác, nên `worker.tryLock()` phải trả `false` khi main chưa unlock.
- `worker.join()` hoàn tất trước khi main đi vào `finally` nhả lock, loại bỏ khả năng worker được chạy sau thời điểm nhả.
- Sau khi nhả lock, main thử lại và thành công, vì không có contender khác. `AtomicBoolean` được dùng lưu kết quả worker; kết quả cũng được quan sát sau `join()`. `AtomicBoolean` không phải cơ chế sửa quyền sở hữu khóa.
- Ví dụ cố ý không dùng `sleep()` hay suy đoán thread sẽ chạy trước/sau theo thời gian thực.

Cách viết `try/finally` sau lần `tryLock()` thành công cần được giữ đúng trong code sản xuất. Trường hợp `false` không được phép gọi `unlock()` nếu thread không nắm quyền sở hữu.

## 6. tryLock(long timeout, TimeUnit unit): chờ có giới hạn

Overload có thời gian:

~~~java
boolean acquired =
        lock.tryLock(150, TimeUnit.MILLISECONDS);
~~~

Các tính chất quan trọng:

- `timeout` là một khoảng chờ **để lấy khóa**, không phải deadline toàn bộ công việc sau khi đã lấy.
- Nếu lock rảnh và không có rào cản như fairness queue, phương thức có thể trả `true` ngay.
- Nếu thread khác giữ lock, caller có thể đợi **tối đa khoảng yêu cầu** theo cơ chế của lock; nếu không acquire được trước khi hết hạn, trả `false`.
- Nếu thread đang bị interrupt khi vào hoặc bị interrupt trong lúc cố acquire, `ReentrantLock` ném `InterruptedException` và **xóa interrupted status tại thời điểm ném**.
- Với `timeout <= 0`, phương thức không đợi. Đây vẫn là overload có ngữ nghĩa kiểm tra interrupt; trên fair lock còn tôn trọng fairness.
- `TimeUnit` phải khác `null`; dùng `null` sẽ dẫn đến `NullPointerException`.
- Giá trị trả về không quy định tổng thời gian thực tế method caller hoàn tất. Scheduling, GC pause và phần xử lý sau acquire có thể làm độ trễ tổng khác mốc timeout.

Mẫu dùng điển hình:

~~~java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

class BoundedCounter {
    private final ReentrantLock lock = new ReentrantLock();
    private int count;

    boolean tryIncrement(long timeout, TimeUnit unit)
            throws InterruptedException {
        if (!lock.tryLock(timeout, unit)) {
            return false;
        }
        try {
            count++;
            return true;
        } finally {
            lock.unlock();
        }
    }
}
~~~

**Lưu ý:** Đây là method hợp lệ của class nhưng chưa có `main`. Caller phải quyết định ý nghĩa của `false`. Thread có thể không lấy được lock vì contention chứ không phải dữ liệu được bảo vệ không hợp lệ. Tăng `timeout` có thể giảm số lần trả `false` nhưng tăng tail latency, số thread đang chờ và áp lực tài nguyên.

## 7. Ví dụ chạy độc lập 2: timeout được xác định bằng ownership

Ví dụ sau không dựa vào may rủi thread nào chạy trước. Main giữ lock cho tới khi worker kết thúc timed tryLock. Worker không thể lấy lock và phải trả `false` khi hết thời gian chờ.

Lưu tệp **TimedTryLockDemo.java**:

~~~java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class TimedTryLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        AtomicReference<String> result = new AtomicReference<>();

        lock.lock();
        try {
            Thread waiter = new Thread(() -> {
                try {
                    boolean acquired =
                            lock.tryLock(40, TimeUnit.MILLISECONDS);
                    if (!acquired) {
                        result.set("timeout");
                        return;
                    }
                    try {
                        result.set("acquired");
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    result.set("interrupted");
                }
            }, "timed-waiter");

            waiter.start();
            waiter.join(); // khóa vẫn do main giữ khi waiter timeout
        } finally {
            lock.unlock();
        }

        System.out.println("result=" + result.get());
    }
}
~~~

Chạy:

~~~bash
javac TimedTryLockDemo.java
java TimedTryLockDemo
~~~

Output khi không bị interrupt ngoài:

~~~text
result=timeout
~~~

**Đối chiếu:**
- Main lấy lock đầu tiên, chỉ nhả trong `finally` sau khi đã `join()` worker.
- Worker không thể acquire khi main sở hữu; sau khoảng chờ nó nhận `false`, set `"timeout"` và thoát.
- Worker không gọi `unlock()` nếu nhận `false`.
- Khi worker kết thúc, main nhả khóa của mình và mới in. Dù scheduler chậm hơn 40ms hay gây thêm delay, kết quả logic vẫn là timeout trong điều kiện thực thi bình thường.
- Đây là phép thử **behavior**. Không được suy luận rằng method luôn trả sau đúng 40ms trên mọi hệ điều hành hoặc JVM.

## 8. Interrupt không phải timeout

Timed `tryLock` **interruptible**. Một thread đang chờ có thể bị hủy bởi `Thread.interrupt()`. Theo `ReentrantLock`, nếu bị interrupt khi vào method hoặc trong lúc đợi, nó ném `InterruptedException`. Đây **không phải** kết quả `false`, và code không nên xử lý như một timeout thông thường.

Mẫu xử lý trong một `Runnable`:

~~~java
try {
    if (!lock.tryLock(500, TimeUnit.MILLISECONDS)) {
        handleContentionTimeout();
        return;
    }
    try {
        performProtectedWork();
    } finally {
        lock.unlock();
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Dừng hoặc chuyển thông tin hủy lên tầng gọi.
}
~~~

Đây là **đoạn trích minh họa**, trong đó các method `handleContentionTimeout()` và `performProtectedWork()` do ứng dụng định nghĩa. Khi có thể khai báo `throws InterruptedException` và caller xử lý, cũng có thể truyền exception lên trên.

Tại sao thường khôi phục cờ? `InterruptedException` thường **xóa interrupted status** khi ném. Nếu code bắt mà không truyền tiếp, `Thread.currentThread().interrupt()` giúp tầng trên nhận biết yêu cầu hủy. Điều này không có nghĩa mọi nghiệp vụ đều phải tiếp tục chạy sau khi khôi phục; nên kết thúc hoặc làm theo hợp đồng task.

### Ví dụ chạy độc lập 3: interrupt khi một thread đang tranh khóa

Lưu **InterruptibleTryLockDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class InterruptibleTryLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<String> outcome = new AtomicReference<>();

        lock.lock();
        try {
            Thread waiter = new Thread(() -> {
                ready.countDown();
                try {
                    if (lock.tryLock(10, TimeUnit.SECONDS)) {
                        try {
                            outcome.set("acquired");
                        } finally {
                            lock.unlock();
                        }
                    } else {
                        outcome.set("timeout");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    outcome.set("interrupted");
                }
            }, "interruptible-waiter");

            waiter.start();
            ready.await();
            waiter.interrupt();
            waiter.join();
        } finally {
            lock.unlock();
        }

        System.out.println("result=" + outcome.get());
    }
}
~~~

Chạy:

~~~bash
javac InterruptibleTryLockDemo.java
java InterruptibleTryLockDemo
~~~

Output trong điều kiện không có tác động hủy phụ khác:

~~~text
result=interrupted
~~~

Giải thích kết quả:
- Main giữ lock cho đến khi waiter kết thúc. Waiter không thể acquire sớm.
- `ready` bảo đảm worker đã chạy ít nhất tới câu lệnh phát tín hiệu. Main gọi interrupt ngay sau đó; kể cả nếu tín hiệu interrupt đến **trước** lời gọi tryLock timed, overload timed vẫn phát hiện trạng thái interrupt và ném `InterruptedException`.
- Catch khôi phục cờ; kết quả `"interrupted"` được ghi rồi main `join()` quan sát.
- Thử nghiệm không cần đoán sleep bao lâu. Nó phân biệt nhánh hủy với hết thời gian chờ.
- Trong ứng dụng thật, một yêu cầu hủy không nên bị chuyển thành retry vô hạn; phải đi theo lifecycle của task.

## 9. Quyền sở hữu, try/finally và lỗi unlock sai

Mẫu chuẩn, khi timed tryLock có thể ném exception:

~~~java
boolean acquired = false;
try {
    acquired = lock.tryLock(timeout, unit);
    if (!acquired) {
        return;
    }

    // Chỉ thread đã acquire mới được thay đổi shared state.
    updateSharedState();
} finally {
    if (acquired) {
        lock.unlock();
    }
}
~~~

Đây là **đoạn trích trong một method** có thể ném `InterruptedException`; `timeout`, `unit` và `updateSharedState()` đến từ caller. Cách khác là kiểm tra kết quả trước, rồi đưa `try/finally` **bên trong nhánh true**, như các ví dụ ở trên. Cả hai bảo đảm không unlock khi không sở hữu.

### Không làm thế này

~~~java
try {
    if (lock.tryLock()) {
        useSharedState();
    }
} finally {
    lock.unlock(); // SAI nếu tryLock() trả false
}
~~~

Nếu thread không giữ lock, `ReentrantLock.unlock()` ném `IllegalMonitorStateException`; nó **không giải phóng** lock của thread khác. Đó là dấu hiệu vi phạm ownership contract, không phải “đảm bảo dọn dẹp”.

Các bẫy liên quan:
- `tryLock() == true` tạo quyền sở hữu cho **thread hiện tại**. Không trao quyền `unlock()` cho thread khác, kể cả thread đó có cùng reference đến `ReentrantLock`.
- Lock reentrant: cùng thread gọi `tryLock()` hai lần thành công thì phải cân bằng **hai lần** `unlock()`. Một lần unlock chỉ giảm hold count.
- Không bỏ quên `unlock()` khi code trong critical section ném exception.
- Không dùng `synchronized(lock)` thay cho `lock.lock()` rồi gọi `lock.unlock()`; intrinsic monitor của object Lock và giao thức Lock API là **hai loại khóa khác nhau**.
- `isHeldByCurrentThread()` và `getHoldCount()` hữu ích để kiểm tra/chẩn đoán, không nên tạo protocol “thử đoán ownership” thay vì giữ boolean từ lời gọi acquire.

## 10. Timeout có ý nghĩa chính xác đến đâu?

Giả sử caller đặt:

~~~java
lock.tryLock(100, TimeUnit.MILLISECONDS)
~~~

Mốc 100ms chỉ giới hạn **pha cố acquire lock**, **không** có nghĩa:

- Toàn bộ thao tác nghiệp vụ phải xong trong 100ms.
- Caller chắc chắn được scheduler chạy đúng lúc để quan sát deadline.
- Chủ lock sẽ bị cưỡng bức nhả khi có waiter timeout.
- Tác vụ vừa giữ lock vừa làm I/O dài sẽ tự bị cắt ngắn.
- Không còn ai cạnh tranh sau khi caller nhận `true`.
- `false` luôn có nghĩa hệ thống hỏng; có thể chỉ là chính sách từ chối có chủ đích.

Nếu cần kiểm soát **end-to-end deadline**, phải truyền ngân sách thời gian từ điểm bắt đầu nghiệp vụ qua các bước: đợi lock, gọi tài nguyên ngoài, chờ queue, retry. Với thời lượng đo trong JVM, `System.nanoTime()` thích hợp để tính **elapsed duration**; không dùng `System.currentTimeMillis()` làm đồng hồ monotonic cho deadline vì đồng hồ tường có thể thay đổi.

### Ví dụ chia sẻ một time budget

~~~java
long startedAt = System.nanoTime();
long budgetNanos = TimeUnit.MILLISECONDS.toNanos(500);

// Bước một diễn ra trước khi thử khóa.
prepareInput();

long elapsed = System.nanoTime() - startedAt;
long remaining = Math.max(0L, budgetNanos - elapsed);

if (!lock.tryLock(remaining, TimeUnit.NANOSECONDS)) {
    throw new IllegalStateException("lock unavailable within budget");
}
try {
    applyPreparedInput();
} finally {
    lock.unlock();
}
~~~

Đây là **đoạn trích minh họa**, các method và lock thuộc context ứng dụng. Nó trừ thời gian `prepareInput()` khỏi ngân sách đợi lock, **nhưng chưa giới hạn thời gian `applyPreparedInput()`**. Hợp đồng thực tế phải đặt deadline riêng hoặc truyền remaining budget đến những bước có khả năng block.

Với nghiệp vụ dài, chú ý tràn số khi cộng deadline nanos hoặc chuyển đổi đơn vị quá lớn. Khi cần so sánh elapsed, pattern trừ từ `System.nanoTime()` và thời lượng trong khoảng hỗ trợ thường dễ đúng hơn cộng timestamp tuyệt đối.

## 11. Fair lock: tryLock() có thể “chen hàng”

Khởi tạo `ReentrantLock`:

~~~java
ReentrantLock defaultLock = new ReentrantLock();      // non-fair
ReentrantLock fairLock = new ReentrantLock(true);     // fair
~~~

Fairness setting làm thay đổi chính sách ưu tiên các thread đang chờ khi tranh lock, nhưng:

**Quan trọng:** `fairLock.tryLock()` **vẫn có thể lấy lock ngay khi nó rảnh, dù các thread khác đã xếp hàng**. Đây là hành vi *barging* được JDK ghi rõ. Điều đó không đồng nghĩa chương trình sai; nhưng bạn không thể dùng `tryLock()` rồi khẳng định mọi thread tuân FIFO.

Ngược lại, `fairLock.tryLock(timeout, unit)` **tôn trọng fairness setting** khi có những waiter khác đang chờ. Nếu cần hành vi thử ngay gần tương đương nhưng vẫn tôn trọng fairness và kiểm tra interrupt, có thể dùng:

~~~java
boolean acquired = fairLock.tryLock(0, TimeUnit.SECONDS);
~~~

Vẫn phải xử lý `InterruptedException`. Nếu cùng thread đã sở hữu lock, việc reentrant acquisition là trường hợp riêng, không cần xếp hàng sau các thread khác.

### Có nên luôn tạo fair lock?

Không. Fairness của lock **không bảo đảm fairness của thread scheduling** và không cam kết deadline cho từng task. Fair lock có thể giảm biến động thời gian chờ trong một số tải nhưng thường có throughput thấp hơn vì ít cho phép barging; chi phí thực tế phụ thuộc workload. Không chọn `new ReentrantLock(true)` chỉ vì tên nghe “công bằng”.

Chọn policy theo yêu cầu:
- Nếu fairness là invariant quan trọng, đánh giá fair lock và sử dụng những API acquire tôn trọng fairness.
- Nếu cần throughput cao và việc chen hàng chấp nhận được, non-fair lock mặc định có thể phù hợp.
- Nếu yêu cầu latency deadline nghiêm ngặt, fairness một mình không đủ; còn phải giới hạn contention, thời gian giữ lock và các nguồn blocking khác.

## 12. Tránh deadlock khi cần nhiều khóa

Khi thao tác cần giữ hai lock A và B, deadlock có thể xuất hiện:

~~~text
Thread T1: giữ A ── chờ B
Thread T2: giữ B ── chờ A

Hai bên chờ vô hạn nếu dùng lock() và không có đường giải phóng.
~~~

Timed tryLock cho phép **bỏ cuộc và nhả những khóa đã giữ**, nhưng chỉ khi code viết đúng finally. Nó không tự động làm thuật toán miễn deadlock nếu một bước khác vẫn chờ vô hạn hoặc nếu caller luôn retry theo cách gây livelock.

Mẫu minh họa lấy hai lock với các bounded attempts:

~~~java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

class TwoResources {
    private final ReentrantLock first = new ReentrantLock();
    private final ReentrantLock second = new ReentrantLock();

    boolean tryUseBoth(long timeout, TimeUnit unit)
            throws InterruptedException {
        long budgetNanos = unit.toNanos(timeout);
        long startedAt = System.nanoTime();

        if (!first.tryLock(budgetNanos, TimeUnit.NANOSECONDS)) {
            return false;
        }
        try {
            long elapsed = System.nanoTime() - startedAt;
            long left = Math.max(0L, budgetNanos - elapsed);
            if (!second.tryLock(left, TimeUnit.NANOSECONDS)) {
                return false;
            }
            try {
                // Thao tác trên cả hai resource dưới đúng hai lock.
                return true;
            } finally {
                second.unlock();
            }
        } finally {
            first.unlock();
        }
    }
}
~~~

Đây là class minh họa, chưa có main và không có tài nguyên thật. Tại sao nó an toàn về **quyền sở hữu**?
1. Nếu không lấy được first, trả false ngay, không unlock first.
2. Sau khi first thành công, mọi đường đi khỏi outer try đều unlock first.
3. Nếu second không lấy được trong ngân sách còn lại, trả false nhưng vẫn nhả first qua outer finally.
4. Chỉ khi second thành công mới được vào nested try và finally nhả second.
5. Nếu bị interrupt trong khi chờ second, exception đi ra và outer finally nhả first.

**Giới hạn:** Nếu các thread khác không tuân theo cùng lock protocol, invariant vẫn có thể hỏng. Nếu có nhiều đường lấy A/B theo thứ tự khác nhau, cần lock ordering hoặc thiết kế có timeout/retry phù hợp. Việc retry quá tích cực giữa hai thread có thể tạo **livelock**, nên khi cần thử lại hãy giới hạn số lần/thời gian và cân nhắc backoff có kiểm soát. Với các phép tính phải kết thúc trong budget, tiếp tục kiểm soát thời gian critical section sau khi acquire.

## 13. Timeout khác Condition.await và timeout của Future

Các khoảng đợi có thể cùng dùng `TimeUnit` nhưng **bảo vệ những vấn đề khác nhau**:

| Lời gọi | Chờ điều gì? | Khi timeout |
|---|---|---|
| `lock.tryLock(t, unit)` | Quyền sở hữu lock | Trả `false` nếu không acquire được. |
| `condition.await(t, unit)` | Tín hiệu/điều kiện trong khi phối hợp qua lock | Trả boolean theo hợp đồng await; phải **kiểm tra lại predicate** khi tỉnh. |
| `future.get(t, unit)` | Task hoàn tất và có kết quả | Ném `TimeoutException` nếu chưa hoàn tất trong hạn. |
| `semaphore.tryAcquire(t, unit)` | Permit từ semaphore | Trả `false` khi không có permit trong hạn. |

Không nhầm thời gian chờ acquire lock với thời gian chờ một predicate đúng. Dùng `Condition` khi thread cần chờ **trạng thái được bảo vệ bởi lock** thay đổi; `Condition.await` được thiết kế nhả lock trong lúc đợi và acquire lại trước khi return. Đừng dùng vòng gọi `tryLock()` liên tục để mô phỏng một queue có condition, vì dễ gây CPU spinning hoặc starvation.

## 14. Tranh chấp khóa và thiết kế fallback

`tryLock()` và timed `tryLock` hữu ích khi ứng dụng có hành vi rõ ràng trong ba trường hợp:

1. **Đã lấy khóa:** thao tác trạng thái chung trong critical section rồi nhả khóa.
2. **Hết thời gian mà không lấy được:** trả mã bận, thử lại có giới hạn, chuyển sang queue hoặc báo một kết quả có ý nghĩa nghiệp vụ.
3. **Bị interrupt:** dừng hoặc truyền cancellation signal; tuyệt đối không biến request bị hủy thành retry vô hạn.

Ví dụ API nghiệp vụ không gộp hết các lỗi vào `false`:

~~~java
enum ReserveResult {
    RESERVED, NOT_ENOUGH, LOCK_BUSY
}
~~~

Đặt kết quả nghiệp vụ (`NOT_ENOUGH`) khác contention (`LOCK_BUSY`). Một lần acquire thành công vẫn có thể gặp dữ liệu không đáp ứng điều kiện. Khi không phân biệt hai trường hợp, caller khó quyết định retry nên hay không.

### Ví dụ class hoàn chỉnh: reserve dưới lock với timeout

~~~java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class InventoryCounter {
    public enum Result {
        RESERVED, NOT_ENOUGH, LOCK_BUSY
    }

    private final ReentrantLock lock = new ReentrantLock();
    private int available;

    public InventoryCounter(int initial) {
        if (initial < 0) {
            throw new IllegalArgumentException("negative initial stock");
        }
        available = initial;
    }

    public Result reserve(int amount, long timeout, TimeUnit unit)
            throws InterruptedException {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        if (!lock.tryLock(timeout, unit)) {
            return Result.LOCK_BUSY;
        }

        try {
            if (available < amount) {
                return Result.NOT_ENOUGH;
            }
            available -= amount;
            return Result.RESERVED;
        } finally {
            lock.unlock();
        }
    }

    public int available() {
        lock.lock();
        try {
            return available;
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        InventoryCounter inventory = new InventoryCounter(5);

        System.out.println(inventory.reserve(
                2, 100, TimeUnit.MILLISECONDS));
        System.out.println(inventory.reserve(
                4, 100, TimeUnit.MILLISECONDS));
        System.out.println("available=" + inventory.available());
    }
}
~~~

Tệp: **InventoryCounter.java**, chạy:

~~~bash
javac InventoryCounter.java
java InventoryCounter
~~~

Output khi main chạy tuần tự, không bị interrupt ngoài:

~~~text
RESERVED
NOT_ENOUGH
available=3
~~~

Giải thích:
- Lần đầu reserve(2) được lock và giảm 5 xuống 3.
- Lần sau reserve(4) vẫn lấy được lock nhưng không đủ hàng; phương thức trả `NOT_ENOUGH` và **không** làm giảm available.
- `LOCK_BUSY` chỉ xảy ra khi contention khiến không acquire được trong thời gian yêu cầu.
- Điều kiện kiểm tra `available < amount` và phép giảm đều nằm trong cùng critical section nên không có check–then–act race giữa các caller tuân thủ cùng protocol.
- Đây là state **trong một JVM**. Nếu nguồn hàng do nhiều tiến trình hoặc hệ thống ngoài cùng cập nhật, `ReentrantLock` của một process không đủ làm giao dịch phân tán.

### Khi fallback là retry

Retry cần một ngân sách tổng và điều kiện dừng. Đừng gọi lặp `tryLock()` không ngừng trong vòng while để “đợi tới khi có khóa”: đó là busy spin và có thể đốt CPU. Nếu yêu cầu chờ, dùng overload timed hoặc `lockInterruptibly()`. Nếu retry vì contention, dùng hạn mức thời gian, số lần thử, backoff và khả năng cancel. Chú ý rằng retry toàn bộ nghiệp vụ có thể không idempotent nếu một bước bên ngoài critical section đã tạo side effect.

## 15. Reentrant acquisition và hold count

Tên `ReentrantLock` cho biết thread đang sở hữu lock có thể acquire nó lần nữa:

~~~java
ReentrantLock lock = new ReentrantLock();

lock.lock();                  // hold count = 1
try {
    boolean second = lock.tryLock(); // true; hold count = 2
    try {
        System.out.println(second);  // true
        System.out.println(lock.getHoldCount()); // 2
    } finally {
        lock.unlock();        // hold count = 1
    }
} finally {
    lock.unlock();            // hold count = 0; lock được nhả
}
~~~

Đây là đoạn mã minh họa đặt trong một method/khối hợp lệ. Nếu main thực thi mà không có contender khác, các dòng in là `true` và `2`. Nó nhấn mạnh rằng `tryLock()` **không trả false chỉ vì isLocked()==true**: nếu thread hiện tại là owner, acquire lại được.

`getHoldCount()` biểu thị số lần thread **hiện tại** đang giữ lock, không phải tổng số thread đang đợi. Nếu quên một lần unlock khi acquire reentrantly, lock sẽ vẫn bị giữ dù code đã rời một tầng gọi; các worker khác có thể bị chặn.

## 16. Quan sát contention đúng cách

Khi không lấy được lock, thay vì tăng timeout tùy tiện, hãy đo:

- **Lock acquisition wait time:** thời gian từ bắt đầu tryLock đến thành công hoặc timeout.
- **Timeout ratio:** bao nhiêu request nhận `false` so với tổng attempts; phân tách theo operation.
- **Critical-section duration:** lock được giữ trong bao lâu; có I/O/slow callback trong lock không.
- **Interrupt/cancellation:** bao nhiêu yêu cầu đợi lock bị hủy, có khôi phục/lan truyền tín hiệu đúng không.
- **Throughput và tail latency:** số nghiệp vụ hoàn tất và các phân vị p95/p99.
- **Thread state/CPU:** `WAITING`, `TIMED_WAITING`, `BLOCKED`, CPU saturation, GC pauses.
- **Invariants:** dữ liệu có chính xác không sau timeout/retry, có bỏ sót nghiệp vụ hoặc xử lý trùng không.

`ReentrantLock.getQueueLength()` chỉ trả **ước lượng** số thread có thể đang đợi acquire; `hasQueuedThreads()` cũng là thông tin quan sát dễ thay đổi. Không dùng chúng như phép kiểm tra atomic để quyết định chắc chắn rằng việc acquire tiếp theo sẽ thành công.

Java Flight Recorder (JFR) có thể giúp nghiên cứu contention; Java thread dumps giúp xem call stack và trạng thái. Khả năng hiển thị chi tiết `java.util.concurrent.locks` tùy JDK/công cụ. Không suy luận lock đã được giải phóng chỉ vì thread dump không ghi một thread đang chờ tại thời điểm chụp.

## 17. Lỗi phổ biến cần nhận diện

**Lỗi 1 — unlock khi tryLock trả false.** Thread không sở hữu lock; `IllegalMonitorStateException` có thể xuất hiện. Chỉ unlock sau acquire thành công.

**Lỗi 2 — Không đặt unlock trong finally.** Exception hoặc return sớm có thể giữ lock mãi, gây treo các thread khác.

**Lỗi 3 — Xem timeout lấy lock là timeout nghiệp vụ.** Sau khi acquire, code trong critical section vẫn có thể chạy/đợi rất lâu. Dùng deadline xuyên suốt nếu yêu cầu độ trễ toàn end-to-end.

**Lỗi 4 — Đánh đồng timeout với interrupt.** Timed overload ném `InterruptedException` khi bị hủy; `false` là kết quả không lấy được khóa trong khoảng chờ. Chính sách xử lý khác nhau.

**Lỗi 5 — Bỏ mất interrupt.** Catch rồi retry mãi làm request khó hủy. Truyền exception lên hoặc khôi phục cờ và thoát theo lifecycle.

**Lỗi 6 — Dùng isLocked() trước lock().** Check và acquire không phải một thao tác atomic. Dùng tryLock.

**Lỗi 7 — Cho rằng tryLock() tuân thủ fairness.** Trên fair ReentrantLock, untimed tryLock vẫn có thể barging. Dùng timed form theo yêu cầu fairness.

**Lỗi 8 — Lặp tryLock() không nghỉ.** Busy spin có thể tiêu tốn CPU và gây contention thêm; ưu tiên chờ có giới hạn hoặc có backoff.

**Lỗi 9 — Khóa không cùng identity.** Mỗi thread giữ một ReentrantLock instance khác nhau sẽ không đồng bộ trên cùng state. Đưa lock chung vào đúng object sở hữu state.

**Lỗi 10 — Dùng synchronized(lock) cùng với lock.lock().** Hai cơ chế không có ràng buộc thay thế nhau; chúng acquire **hai khóa khác nhau về ngữ nghĩa**.

**Lỗi 11 — Nhả khóa từ thread khác.** Lock ownership theo thread. Không chuyển trách nhiệm unlock sang callback chạy ở worker khác.

**Lỗi 12 — Thử nhiều khóa theo thứ tự trái ngược rồi quên nhả khóa đầu khi khóa thứ hai timeout.** Cần nested try/finally và chính sách lock order/retry rõ ràng.

**Lỗi 13 — Cho rằng tryLock tự bảo vệ mọi dữ liệu.** Chỉ các thao tác tuân thủ cùng lock protocol mới có mutual exclusion. Đường đọc/ghi không dùng lock có thể làm sai invariant.

**Lỗi 14 — Dùng timeouts làm giải pháp duy nhất cho overload.** Timeout làm người gọi ngừng chờ nhưng không giải quyết nguyên nhân contention; xem critical section quá dài, queue, CPU/IO và tải admission.

## 18. Checklist chọn API

~~~text
Có shared mutable state cần exclusive access?
  |
  +-- Không --> tránh lock nếu trạng thái thực sự độc lập/immutable
  |
  +-- Có
       |
       +-- Có thể chờ cho đến khi lấy được?
       |      |
       |      +-- Có, không cần interrupt trong pha đợi
       |      |       → lock() + finally unlock()
       |      |
       |      +-- Có, nhưng phải hủy được
       |              → lockInterruptibly() + finally unlock()
       |
       +-- Không thể chờ?
       |       → tryLock()
       |       → false: fallback có chủ đích
       |
       +-- Chỉ được chờ một khoảng giới hạn?
               → tryLock(timeout, unit)
               → true: vào critical section + finally unlock()
               → false: timeout/fallback
               → InterruptedException: propagate hoặc restore + cancel
~~~

Trước khi đưa vào production, tự trả lời:

1. Lock reference có ổn định, thuộc đúng shared state và dùng chung bởi tất cả writer/reader không?
2. Có xử lý riêng thành công, timeout, interrupt? Caller có biết khi nào công việc không được thực hiện không?
3. Có đảm bảo unlock đúng số lần và đúng thread trên mọi đường return/exception không?
4. Timeout là ngân sách cho lock hay cho toàn tác vụ? Các bước khác có thời hạn không?
5. Fairness có phải yêu cầu thật? Dùng untimed tryLock trên fair lock có chủ đích không?
6. Nếu retry, có trần thời gian/số lần và cơ chế tránh spin/livelock không?
7. Nếu dùng nhiều lock, có lock-order policy, release on failure và kiểm tra deadlock không?
8. Đã đo contention, thời gian giữ khóa, timeout ratio và tail latency trên tải thật chưa?

## 19. Câu hỏi luyện tập

**Câu 1.** Thread A giữ lock, thread B gọi `tryLock()`. B sẽ nhận gì?

**Đáp án:** B nhận `false` ngay (nếu B không phải chính owner). Không chờ A nhả.

**Câu 2.** Nếu B gọi `tryLock(100, MILLISECONDS)` và A giữ quá 100ms, B sẽ nhận gì?

**Đáp án:** Nếu không có interrupt và B không acquire được, nó trả `false` khi hết hạn. Không có bảo đảm độ trễ tường chính xác tới mili giây.

**Câu 3.** B bị interrupt trong lúc chờ timed tryLock. Nên xử lý giống false không?

**Đáp án:** Không. Nó ném `InterruptedException` và xóa interrupted flag ở thời điểm ném; code cần propagate hoặc khôi phục flag rồi kết thúc theo cancellation policy.

**Câu 4.** Khi `tryLock()` trả true và trong critical section xảy ra exception, có cần unlock?

**Đáp án:** Có, trong `finally` sau khi acquire thành công.

**Câu 5.** Với `new ReentrantLock(true)`, `tryLock()` có tuân thủ hàng chờ fair không?

**Đáp án:** Không bắt buộc; overload untimed có thể barging. Timed overload tôn trọng fairness setting.

**Câu 6.** Cùng một thread đang giữ ReentrantLock lại gọi `tryLock()` thì sao?

**Đáp án:** Trả true, tăng hold count. Phải unlock đủ lần để nhả hoàn toàn.

**Câu 7.** Một service nhận `false` từ timed tryLock rồi silently return trong nhiệm vụ chuyển trạng thái bắt buộc. Có đúng không?

**Đáp án:** Chưa đủ. Phải quyết định nghĩa vụ xử lý: retry có giới hạn, đưa vào queue hoặc báo trạng thái thất bại rõ ràng. Timeout chỉ mô tả không acquire được lock, không xóa yêu cầu nghiệp vụ.

**Câu 8.** Có thể dùng `synchronized(myLock)` để khóa cùng quyền sở hữu như `myLock.lock()` không?

**Đáp án:** Không. Monitor nội tại của object Lock và giao thức Lock API không tự tương đương.

## 20. Tài liệu gốc

- `ReentrantLock` (Java SE 21): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html
- `Lock` interface (Java SE 21): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/Lock.html
- `TimeUnit`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/TimeUnit.html
- `Thread.interrupt`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#interrupt()
- JLS `17.4 (Java Memory Model): https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4

**Ghi nhớ:** `tryLock()` giúp quyết định **ngay** có vào critical section được không; `tryLock(timeout, unit)` cho phép **chờ có giới hạn và phản ứng interrupt**. Khi acquire thành công, thread phải unlock trong finally. Khi thất bại, trả về trạng thái có ý nghĩa nghiệp vụ; timeout không thay thế việc xử lý contention, deadline toàn nghiệp vụ hoặc thiết kế locking đúng đắn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-and-conditions-004">lockInterruptibly() và hỗ trợ hủy khi chờ lock</a>

<details>
<summary>Xem chi tiết</summary>

# `lockInterruptibly()` và hỗ trợ hủy khi chờ lock trong Java

## 1. Bài toán: thread cần khóa nhưng không thể chờ mãi

Trong một chương trình Java đồng thời, nhiều thread có thể cần truy cập tài nguyên chung. `Lock` và `ReentrantLock` giúp bảo vệ critical section, nhưng có tình huống một thread **chưa lấy được khóa** vì thread khác đang giữ nó.

Nếu chỉ gọi `lock.lock()`, thread sẽ tiếp tục chờ cho đến khi chiếm được khóa, kể cả khi có yêu cầu `interrupt()` trong lúc chờ. Điều này có thể không phù hợp với tác vụ cần **hủy kịp thời**: người dùng đã hủy thao tác, request đã hết hạn, dịch vụ đang shutdown, hoặc một nhánh tính toán không còn cần thiết.

`Lock.lockInterruptibly()` là phương thức lấy khóa **có thể bị ngắt khi đang chờ**. Nó cho phép thread phản ứng với `Thread.interrupt()` bằng `InterruptedException` thay vì phải tiếp tục đợi khóa được giải phóng.

Ví dụ về nhu cầu:

- Worker A giữ khóa của một cấu trúc dữ liệu và làm một công việc tương đối lâu.
- Worker B cần cùng khóa nên phải chờ.
- Hệ thống yêu cầu hủy B trước khi B lấy được khóa.
- Nếu B dùng `lockInterruptibly()`, yêu cầu ngắt có thể làm B thoát khỏi bước chờ mà không vào critical section.
- Nếu B dùng `lock()`, yêu cầu ngắt **không tự kết thúc việc chờ khóa**; B có thể chỉ lấy được khóa sau khi A nhả và sau đó code phải tự kiểm tra trạng thái ngắt.

**Phạm vi:** hành vi các API khóa và cơ chế interrupt của **Java concurrency nói chung**, không phụ thuộc framework. Bài này giải thích rõ **hủy khi đang đợi khóa** chứ không đánh đồng interrupt với việc JVM cưỡng chế kết thúc thread.

## 2. API và hợp đồng cốt lõi

`java.util.concurrent.locks.Lock` định nghĩa:

```java
void lock();
void lockInterruptibly() throws InterruptedException;
boolean tryLock();
boolean tryLock(long time, TimeUnit unit)
        throws InterruptedException;
void unlock();
```

Đây là **chữ ký API rút gọn để minh họa**, không phải một lớp Java độc lập. `TimeUnit` thuộc `java.util.concurrent`.

Với implementation phổ biến `ReentrantLock`:

1. `lockInterruptibly()` cố lấy khóa. Nếu có thể lấy và không có yêu cầu interrupt cần xử lý, nó trả về bình thường và caller **đã sở hữu khóa**.
2. Nếu khóa đang do thread khác giữ, caller chờ theo cơ chế của lock; khi thread chờ bị interrupt, nó có thể thoát khỏi hàng chờ bằng `InterruptedException`.
3. Nếu thread đã có trạng thái interrupted khi gọi `lockInterruptibly()`, nó ném `InterruptedException` ngay cả trong trường hợp khóa đang sẵn sàng; thao tác này ưu tiên phản ứng với interrupt hơn việc lấy khóa bình thường/tái nhập theo hợp đồng của `ReentrantLock`.
4. Nếu `lockInterruptibly()` ném `InterruptedException` do việc chờ lấy khóa, caller **không được coi mình đã lấy được khóa từ lời gọi đó**; tuyệt đối không gọi `unlock()` một cách vô điều kiện.
5. Nếu `lockInterruptibly()` trả về thành công, caller phải **luôn nhả khóa** sau critical section, thường bằng `try/finally`.

Mẫu chuẩn:

```java
lock.lockInterruptibly();    // Có thể ném InterruptedException trước khi lấy lock.
try {
    // Chỉ đến đây khi đã lấy được lock.
    updateSharedState();
} finally {
    lock.unlock();
}
```

Đây là đoạn minh họa trong phương thức đã khai báo hoặc xử lý `InterruptedException`; `lock` và `updateSharedState()` do ứng dụng định nghĩa. Chú ý `try` chỉ bắt đầu **sau** khi `lockInterruptibly()` thành công. Nếu đưa `lockInterruptibly()` vào bên trong một khối `try/finally` rồi gọi `unlock()` vô điều kiện, exception có thể khiến code cố nhả khóa chưa hề sở hữu và phát sinh `IllegalMonitorStateException`.

## 3. So sánh `lock()`, `lockInterruptibly()` và hai dạng `tryLock()`

| API | Nếu khóa đang bận | Phản ứng với interrupt khi chờ | Timeout | Kết quả |
| --- | --- | --- | --- | --- |
| `lock.lock()` | Chờ đến khi lấy được khóa | Với `ReentrantLock`, tiếp tục chờ; khôi phục/giữ trạng thái interrupt khi trả về | Không | `void`; trả về nghĩa là đã lấy khóa |
| `lock.lockInterruptibly()` | Chờ khóa với khả năng ngắt | Ném `InterruptedException` và thoát bước chờ | Không | `void`; trả về bình thường nghĩa là đã lấy khóa |
| `lock.tryLock()` | **Không chờ**, trả về ngay | Không phải API chờ có thể bị ngắt | Không | `true` nếu lấy được, `false` nếu không |
| `lock.tryLock(timeout, unit)` | Chờ tối đa khoảng thời gian yêu cầu | Ném `InterruptedException` khi đang chờ hoặc khi đã bị ngắt trước khi gọi | Có | `true` nếu lấy được; `false` nếu hết thời hạn |

**Không nhầm:** `lockInterruptibly()` **không tự đặt timeout**. Nếu không ai gọi `interrupt()` và không ai nhả khóa, thread vẫn có thể chờ vô hạn. Muốn tự giới hạn thời gian, dùng `tryLock(timeout, unit)` và xử lý cả `false` lẫn `InterruptedException`.

Với `ReentrantLock`, `lock()` là phương thức lấy khóa không hủy được *trong giai đoạn đợi khóa*. Nếu bị interrupt khi đang chờ, nó vẫn có thể đợi đến lúc lấy được khóa; trạng thái interrupt được giữ/khôi phục để code sau đó quan sát. Điều này **không có nghĩa** `interrupt()` hoàn toàn vô hiệu: lời gọi interrupt là một tín hiệu hợp tác mà các API khác hoặc code sau khi lấy khóa có thể xử lý.

Với `synchronized(monitor)`, việc **đợi vào monitor** cũng không có overload có thể hủy bằng `InterruptedException` hoặc deadline. Không thể trực tiếp thay `synchronized` bằng một cú pháp “interruptibly”. Khi yêu cầu khả năng hủy đợi khóa, `ReentrantLock` có lợi thế về API.

## 4. `Thread.interrupt()` là yêu cầu hợp tác, không phải cưỡng chế dừng

`Thread.interrupt()` đánh dấu trạng thái interrupt hoặc làm một số thao tác đang chờ theo cơ chế interruptible ném `InterruptedException`. Nó **không** cưỡng chế thread bị dừng ngay, không rollback các cập nhật đã làm, và không tự giải phóng khóa mà thread đang giữ.

Phân biệt ba thời điểm:

### 4.1. Interrupt trước khi lấy khóa

Nếu worker đang đợi tại `lockInterruptibly()`, yêu cầu interrupt cho phép nó ném `InterruptedException` và rời hàng chờ. Worker không vào phần xử lý cần khóa.

### 4.2. Interrupt sau khi đã lấy khóa

`lockInterruptibly()` đã **trả về bình thường** thì worker đang sở hữu khóa. Một lần interrupt tiếp theo **không tự nhả khóa** và không tự dừng critical section. Worker phải hợp tác: kiểm tra tín hiệu, xử lý `InterruptedException` từ các phương thức chặn khác, giữ bất biến dữ liệu, sau đó rời `try/finally` để nhả khóa.

### 4.3. Interrupt trong lúc chờ API khác

`Thread.sleep()`, `Object.wait()`, `Thread.join()`, `Condition.await()` và các phương thức chờ có thể bị ngắt khác có hợp đồng interrupt riêng. Mọi đường thoát khỏi critical section do exception đều phải được quản lý để `finally` nhả lock đã lấy.

**Ví dụ quan trọng:** worker đã vào critical section và đang gọi `Thread.sleep()`. Nếu bị interrupt, `sleep` ném `InterruptedException`; luồng phải thoát qua `finally` để `unlock()`. Dùng `lockInterruptibly()` lúc đầu chỉ giải quyết việc **đợi lấy khóa**, không thay công việc thiết kế logic hủy ở bên trong critical section.

## 5. Ví dụ xác định: hủy worker đang đợi `lockInterruptibly()`

Chương trình dưới dùng `CountDownLatch` để điều phối: **main giữ khóa**, worker chuẩn bị lấy khóa, main phát yêu cầu interrupt, worker kết thúc đường chờ, và main kiểm tra rằng nó vẫn giữ lock. Không dựa vào `Thread.sleep` để đoán khi nào worker đã thực sự bị block.

Tạo tệp `InterruptibleLockDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class InterruptibleLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch attempting = new CountDownLatch(1);

        lock.lock(); // Main cố tình giữ khóa khi worker bắt đầu.
        try {
            Thread worker = new Thread(() -> {
                attempting.countDown();

                try {
                    lock.lockInterruptibly();
                } catch (InterruptedException e) {
                    System.out.println("worker: bị hủy khi xin khóa");
                    Thread.currentThread().interrupt();
                    return;
                }

                try {
                    System.out.println("worker: đã vào vùng bảo vệ");
                } finally {
                    lock.unlock();
                }
            }, "worker");

            worker.start();
            attempting.await();
            worker.interrupt();
            worker.join();

            System.out.println("main giữ khóa: "
                    + lock.isHeldByCurrentThread());
        } finally {
            lock.unlock();
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac InterruptibleLockDemo.java
java InterruptibleLockDemo
```

Kết quả khi không có yêu cầu interrupt bên ngoài dành cho main:

```text
worker: bị hủy khi xin khóa
main giữ khóa: true
```

Lập luận theo thứ tự:

1. Main gọi `lock.lock()` trước khi tạo worker và **không nhả lock trước `worker.join()`**.
2. Worker phát tín hiệu `attempting.countDown()` rồi gọi `lock.lockInterruptibly()`.
3. Main nhận `attempting.await()`, gọi `worker.interrupt()`.
4. Nếu worker chưa kịp gọi `lockInterruptibly()`, cờ interrupted đã được đặt và lời gọi sắp tới ném exception. Nếu worker đã đứng chờ khóa, interrupt đánh thức thao tác chờ và khiến nó ném exception. **Cả hai trường hợp đều không để worker lấy khóa từ main.**
5. Worker in dòng hủy, khôi phục trạng thái interrupt và kết thúc runnable. `join()` bảo đảm main in sau worker.
6. Main vẫn giữ lock cho đến `finally`. Vì vậy dòng “worker: đã vào vùng bảo vệ” không thể xuất hiện trên đường chạy này.

**Tại sao `lock.unlock()` ở worker không bị gọi khi cancel?** Vì khối `try/finally` giải phóng nằm **sau** khi `lockInterruptibly()` trả về thành công. Khi có `InterruptedException`, worker rời khỏi nhánh bắt lỗi; nó không sở hữu lock nên không được `unlock()`.

Ví dụ đặt `Thread.currentThread().interrupt()` trước khi `return` để tầng gọi (nếu có) không mất thông tin hủy. Với một method cho phép `throws InterruptedException`, cách sạch hơn thường là **truyền tiếp exception**, không nhất thiết phải bắt và khôi phục cờ tại mọi tầng.

## 6. So sánh bằng ví dụ: `lock()` vẫn chờ dù bị interrupt

Trong ví dụ tiếp theo, main tiếp tục giữ khóa khi gửi interrupt. Worker dùng `lock()`, nên interrupt **không làm bước xin khóa trả về bằng exception**. Sau khi main nhả khóa, worker mới có thể vào critical section và kiểm tra cờ interrupt.

Tạo tệp `NonInterruptibleLockDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class NonInterruptibleLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch attempting = new CountDownLatch(1);

        lock.lock();
        Thread worker = new Thread(() -> {
            attempting.countDown();
            lock.lock();
            try {
                System.out.println("worker đã lấy khóa, interrupted="
                        + Thread.currentThread().isInterrupted());
            } finally {
                lock.unlock();
            }
        }, "worker");

        try {
            worker.start();
            attempting.await();
            worker.interrupt();

            System.out.println("main: sẽ nhả khóa");
        } finally {
            lock.unlock();
        }

        worker.join();
        System.out.println("main: worker đã xong");
    }
}
```

```bash
javac NonInterruptibleLockDemo.java
java NonInterruptibleLockDemo
```

Kết quả với `ReentrantLock`, khi main không bị interrupt từ bên ngoài:

```text
main: sẽ nhả khóa
worker đã lấy khóa, interrupted=true
main: worker đã xong
```

Giải thích:

- Main còn giữ khóa lúc gọi `worker.interrupt()` nên worker chưa thể chiếm khóa sớm.
- Worker dùng `lock()`, vì vậy nó không ném `InterruptedException` từ thao tác xin khóa.
- Với `ReentrantLock`, nếu bị interrupt trong lúc chờ, phương thức tiếp tục đợi rồi trả về với trạng thái interrupt được giữ/khôi phục. Cờ được thấy ở code sau khi khóa đã được lấy.
- Main in trước khi nhả khóa trong `finally`, nên thứ tự ba dòng là xác định nhờ `join()` và lock.

**Không diễn giải sai:** vì worker đã có trạng thái interrupted sau khi `lock()` trả về, một lời gọi `Thread.sleep()` tiếp theo có thể ném `InterruptedException` ngay. Code không được lờ đi việc này nếu nó cần khả năng hủy an toàn.

Nếu nghiệp vụ thật sự không cho phép hủy khi xin khóa, `lock()` có thể đúng. Nhưng nếu một task đang chờ rất lâu hoặc bị khóa bởi tài nguyên khác, chọn `lockInterruptibly()` làm hệ thống phản hồi yêu cầu hủy tốt hơn.

## 7. Khi nào dùng `tryLock(timeout, unit)`?

`lockInterruptibly()` không có deadline riêng. Một task chỉ cần chờ **tối đa một khoảng thời gian** có thể dùng:

```java
boolean acquired = lock.tryLock(250, TimeUnit.MILLISECONDS);
if (!acquired) {
    // Hết thời gian chờ, chưa lấy được khóa.
    return;
}
try {
    updateSharedState();
} finally {
    lock.unlock();
}
```

Đây là đoạn minh họa bên trong phương thức đã xử lý `InterruptedException`, có `lock` và `updateSharedState()`. Khác `lockInterruptibly()`, API có thể trả về `false` khi deadline hết. Nó **vẫn hỗ trợ interrupt** trong khi chờ, và có thể ném `InterruptedException`.

### Ví dụ không phụ thuộc may rủi của timeout

Tạo tệp `TimedTryLockDemo.java`:

```java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class TimedTryLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();

        lock.lock();
        try {
            Thread worker = new Thread(() -> {
                boolean acquired;
                try {
                    acquired = lock.tryLock(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("worker: bị ngắt");
                    return;
                }

                if (!acquired) {
                    System.out.println("worker: hết thời gian chờ");
                    return;
                }

                try {
                    System.out.println("worker: đã lấy khóa");
                } finally {
                    lock.unlock();
                }
            }, "worker");

            worker.start();
            worker.join(); // Main vẫn giữ khóa cho đến khi worker kết thúc.
        } finally {
            lock.unlock();
        }

        System.out.println("main: hoàn thành");
    }
}
```

```bash
javac TimedTryLockDemo.java
java TimedTryLockDemo
```

Kết quả khi không có interrupt ngoài ý muốn:

```text
worker: hết thời gian chờ
main: hoàn thành
```

Vì main giữ lock suốt lúc `worker.join()`, worker chắc chắn không lấy được lock trong thời hạn và phải nhận `false`. Đây là một phép thử minh họa deadline, **không nên mô phỏng cách tổ chức hệ thống thật bằng cách giữ khóa trong lúc đợi worker không thể tiến triển**. Với công việc có tài nguyên được chia sẻ, cần tránh giữ khóa rộng hơn mức cần thiết.

Một điểm tinh tế: timeout đo **khoảng chờ tối đa theo hợp đồng API, phụ thuộc lịch chạy và thời gian hệ thống**; mã ứng dụng không nên xem `100 ms` là một mốc tuyệt đối chính xác. Đừng dùng các phép `sleep(100)` để xác minh logic concurrency trong bài test.

### `tryLock()` không timeout là một lựa chọn khác

`lock.tryLock()` cố lấy khóa ngay và trả về `true` hoặc `false`. Nếu ứng dụng có phương án fallback tức thời, đây có thể là API rõ nhất:

```java
if (lock.tryLock()) {
    try {
        doWork();
    } finally {
        lock.unlock();
    }
} else {
    doSomethingElse();
}
```

Đây là minh họa, chưa phải chương trình độc lập. `tryLock()` không đợi đến khi có interrupt. Thêm nữa, trên một `ReentrantLock` được cấu hình fair, `tryLock()` không tham gia hoàn toàn chính sách công bằng hàng chờ; nó có thể “barging” khi lock rảnh. Nếu yêu cầu fairness của hàng chờ, cần hiểu hợp đồng riêng của `tryLock(timeout, unit)`.

## 8. Phân biệt interrupt khi chờ khóa và interrupt trong critical section

Không nên viết:

```java
lock.lockInterruptibly();
try {
    performLongRunningTask();
} finally {
    lock.unlock();
}
```

rồi kết luận rằng `performLongRunningTask()` chắc chắn dừng ngay khi có `Thread.interrupt()`. Một khi lock đã được lấy, hành vi hủy của task phụ thuộc vào **nội dung method đó**.

Nếu công việc CPU-bound lặp nhiều lần, cân nhắc kiểm tra trạng thái ngắt ở những điểm an toàn:

```java
lock.lockInterruptibly();
try {
    for (int i = 0; i < batches.size(); i++) {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("cancelled during work");
        }
        processBatch(batches.get(i));
    }
} finally {
    lock.unlock();
}
```

Đây là mã minh họa trong phương thức khai báo `throws InterruptedException`; `batches` và `processBatch` là dữ liệu/logic của ứng dụng. Việc ném exception trong `try` vẫn dẫn tới `finally` và nhả lock.

Tuy nhiên, **không được bỏ dở một thay đổi dữ liệu nhiều bước ở trạng thái sai** chỉ vì thread bị interrupt. Trong các critical section chứa invariant, hãy thiết kế một trong các hướng:

- Kiểm tra interrupt **trước khi bắt đầu giao dịch nội bộ**;
- Hoàn thành hoặc rollback một đơn vị thay đổi cần nguyên tử trước khi phản hồi hủy;
- Chia công việc thành các phần nhỏ có điểm commit nhất quán;
- Dùng giao dịch/compensation tương ứng nếu có tài nguyên ngoài JVM.

`ReentrantLock` chỉ cung cấp loại trừ lẫn nhau và quan hệ bộ nhớ; nó không rollback thay đổi đang thực hiện khi có exception hay interrupt.

### Cờ interrupt cũng là dữ liệu điều khiển

`Thread.currentThread().isInterrupted()` đọc trạng thái của thread **không xóa cờ**. `Thread.interrupted()` là phương thức static, đọc và **xóa cờ** của thread hiện tại. Chúng không thay thế nhau một cách tùy tiện.

Ví dụ:

```java
if (Thread.currentThread().isInterrupted()) {
    // Có tín hiệu hủy, cờ vẫn được giữ.
}
```

Ngược lại, gọi `Thread.interrupted()` nhiều lần khi không có interrupt mới có thể trả về `true` lần đầu rồi `false` lần sau. Nếu không có lý do rõ ràng, đừng vô ý tiêu thụ trạng thái ngắt trước khi thành phần khác có cơ hội xử lý.

## 9. `InterruptedException` và việc khôi phục trạng thái interrupt

Khi một API chờ có khả năng bị ngắt ném `InterruptedException`, trạng thái interrupt của thread thường được **xóa khi exception được phát ra** theo hợp đồng của API đó (bao gồm `ReentrantLock.lockInterruptibly()`). Điều này tránh truyền cùng một tín hiệu theo hai cơ chế khác nhau mà không có ý định.

Có hai cách xử lý phổ biến:

**Cách A — truyền exception lên tầng gọi** khi chữ ký phương thức cho phép:

```java
void transfer() throws InterruptedException {
    lock.lockInterruptibly();
    try {
        updateBalances();
    } finally {
        lock.unlock();
    }
}
```

**Cách B — khôi phục cờ và kết thúc công việc** khi không thể khai báo exception tại tầng hiện tại:

```java
void runTask() {
    try {
        lock.lockInterruptibly();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
    }
    try {
        updateBalances();
    } finally {
        lock.unlock();
    }
}
```

Đây là **hai đoạn minh họa**, với `lock` và `updateBalances()` được khai báo ở lớp thích hợp. Cách B khôi phục cờ vì `Runnable.run()` không thể khai báo thêm checked exception `InterruptedException`.

Lỗi phổ biến:

```java
try {
    lock.lockInterruptibly();
} catch (InterruptedException ignored) {
    // Bỏ qua rồi vẫn tiếp tục thao tác như đã có khóa: SAI.
}
updateBalances(); // Có thể truy cập state mà chưa giữ lock.
```

Đừng bắt exception rồi **đi tiếp như thể đã chiếm khóa**. Khi bị ngắt ở lúc xin khóa, phải rời task, truyền exception lên trên hoặc có một cơ chế retry được thiết kế rõ. Nếu tự retry vô hạn bất kể interrupt, bạn đã làm mất khả năng hủy vốn là lý do chọn `lockInterruptibly()`.

Ngoài ra, việc bắt và khôi phục cờ *không đủ* nếu code vẫn chạy tiếp và thực hiện các API chặn khác ngoài dự kiến. Hãy xác định rõ trạng thái “canceled” và đường thoát của tác vụ.

## 10. Hủy một task của `ExecutorService` bằng `Future.cancel(true)`

Trong Java, nhiều task được gửi vào `ExecutorService` thay vì tự tạo từng `Thread`. Nếu muốn hủy task đang thực thi, `Future.cancel(true)` **yêu cầu interrupt thread đang chạy task** (khi executor hỗ trợ theo hợp đồng tương ứng). Khi task đợi tại `lockInterruptibly()`, yêu cầu interrupt có thể khiến nó rời bước chờ.

Tạo tệp `FutureLockCancellationDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

public class FutureLockCancellationDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch attempting = new CountDownLatch(1);
        CountDownLatch taskExited = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        lock.lock(); // Main cố ý giữ lock khi task được gửi vào.
        try {
            Future<?> future = executor.submit(() -> {
                try {
                    attempting.countDown();
                    lock.lockInterruptibly();
                    try {
                        System.out.println("task: đã lấy khóa");
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException e) {
                    System.out.println("task: dừng chờ khóa");
                    Thread.currentThread().interrupt();
                } finally {
                    taskExited.countDown();
                }
            });

            attempting.await();
            boolean cancelled = future.cancel(true);
            taskExited.await();
            System.out.println("future cancelled=" + cancelled);
        } finally {
            lock.unlock();
            executor.shutdown();
        }
    }
}
```

```bash
javac FutureLockCancellationDemo.java
java FutureLockCancellationDemo
```

Kết quả khi không có yêu cầu interrupt bên ngoài dành cho main:

```text
task: dừng chờ khóa
future cancelled=true
```

Lý do kết quả xác định:

1. Main đã giữ `lock` nên task không thể lấy khóa trước khi có hủy.
2. `attempting.countDown()` bảo đảm main chỉ gọi `cancel(true)` sau khi task đã bắt đầu runnable.
3. `future.cancel(true)` đánh dấu yêu cầu hủy và gửi interrupt tới thread thực thi task. Task phản ứng ở `lockInterruptibly()`, đi vào `catch` và rời đường chờ.
4. `taskExited.await()` buộc main chờ khối `finally` của task thực thi, nên thông báo của task in trước dòng kết quả.
5. Main chỉ nhả khóa và gọi `executor.shutdown()` sau các bước đó.

**Cảnh báo:** `Future.isCancelled() == true` hoặc `cancel(true)` trả về `true` **không đồng nghĩa task đã thực sự ngừng và dọn dẹp xong** trong mọi chương trình. Future có thể chuyển sang trạng thái canceled trước khi worker hoàn tất cleanup; nếu task bỏ qua interrupt, nó còn có thể tiếp tục. Trong ví dụ có `taskExited` để xác nhận một điểm dọn dẹp cụ thể. Đừng chỉ dựa vào `Future.get()` sau cancellation để chờ phần cleanup xong, vì `get()` trên Future bị hủy ném `CancellationException`.

`cancel(false)` không yêu cầu interrupt task đang chạy; vì vậy không thể kỳ vọng nó đánh thức một task bị chặn tại `lockInterruptibly()`. `ExecutorService.shutdownNow()` cũng gửi tín hiệu interrupt theo khả năng của executor, nhưng không bảo đảm mọi task lập tức ngừng. Việc thiết kế task **hợp tác với interrupt** là trách nhiệm của code ứng dụng.

## 11. `lockInterruptibly()` có giúp tránh deadlock không?

**Có thể giúp một thread thoát khỏi đợi khóa khi bị hủy**, nhưng không tự phát hiện hay ngăn mọi deadlock.

Ví dụ hai tài nguyên có hai `ReentrantLock`:

```text
Thread A: giữ lock X ───> chờ lock Y
Thread B: giữ lock Y ───> chờ lock X
```

Nếu các bước đợi khóa thứ hai đều dùng `lockInterruptibly()`, hệ thống có thể **gửi interrupt có chủ đích** tới một trong hai thread đang chờ. Thread đó bắt `InterruptedException`, thoát các `finally` để nhả những khóa nó đã lấy, nhờ đó có thể phá vòng chờ.

Nhưng phải có đủ điều kiện:

- Có một thành phần phát hiện timeout, nhận yêu cầu hủy hoặc quyết định interrupt thread cần hủy. `lockInterruptibly()` tự nó **không phát hiện deadlock**.
- Mỗi lock đã lấy được phải nhả trong `finally`, kể cả khi bước lấy lock tiếp theo bị ngắt.
- Không được để một tác vụ vẫn giữ lock ở chỗ khác vì xử lý exception sai hoặc vì đang chờ một hoạt động **không hỗ trợ interrupt**.
- Không làm hỏng invariant khi thoát từ giữa một thao tác cập nhật nhiều bước.

Mẫu lấy hai khóa với đường dọn dẹp rõ:

```java
first.lockInterruptibly();
try {
    second.lockInterruptibly();
    try {
        changeBothResources();
    } finally {
        second.unlock();
    }
} finally {
    first.unlock();
}
```

Đây là đoạn minh họa, giả định `first` và `second` là `Lock` và `changeBothResources()` được định nghĩa trong ứng dụng. Nếu bị interrupt khi chờ `second`, chỉ `first` đã lấy được nên `finally` ngoài nhả nó. Nếu bị interrupt khi chờ `first`, chưa vào khối `try` nào nên không gọi `unlock()`.

**Biện pháp phòng deadlock ưu tiên vẫn là thứ tự lấy khóa nhất quán** (ví dụ luôn khóa theo ID tài nguyên tăng dần) và giảm việc giữ nhiều lock lồng nhau. Interruptible acquisition là một lớp hỗ trợ hủy/khôi phục, không thay thế thiết kế khóa đúng.

## 12. Hợp đồng `ReentrantLock`, fairness và reentrancy

`ReentrantLock` là một implementation reentrant của `Lock`. Thread đã giữ khóa có thể lấy lại cùng lock (hold count tăng), và phải nhả tương ứng số lần đã lấy. Với `lockInterruptibly()`, **nếu thread đã bị interrupted**, lời gọi có thể ném `InterruptedException` ngay thay vì tái nhập khóa theo hợp đồng ưu tiên xử lý ngắt. Không nên viết code giả định lời gọi này chắc chắn thành công chỉ vì chính mình đang giữ lock.

Với `ReentrantLock(true)` (fair lock), việc chờ khóa của `lock()` và `lockInterruptibly()` thường tuân theo chính sách ưu tiên thread chờ lâu hơn trong điều kiện contention. Tuy nhiên:

- Fairness không bảo đảm thứ tự task được CPU lập lịch hoặc độ trễ tối đa.
- `tryLock()` không timeout có thể lấy khóa ngay nếu nó đang rảnh, kể cả có thread khác chờ, theo hợp đồng của `ReentrantLock`.
- Fair locking thường có tradeoff về throughput; chọn dựa trên đo lường và yêu cầu ứng dụng.
- Reentrancy không thay đổi yêu cầu `unlock()` đúng số lần và thuộc về đúng thread đang giữ lock.

`isLocked()` chỉ cho biết tại thời điểm kiểm tra có thread đang giữ lock hay không. Nó **không** nói rằng thread hiện tại được phép nhả. `isHeldByCurrentThread()` giúp chẩn đoán quyền sở hữu của thread hiện tại, nhưng nên thiết kế nhánh lấy/nhả cho đúng thay vì phụ thuộc vào kiểm tra trạng thái để chữa một giao thức khóa sai.

## 13. Trạng thái thread khi đợi `ReentrantLock`

Khi một thread đợi `ReentrantLock`, implementation Java thường chặn qua cơ chế đồng bộ của `java.util.concurrent.locks` (ví dụ `LockSupport.park`). Trạng thái quan sát qua `Thread.getState()` có thể là `WAITING`, hoặc `TIMED_WAITING` với thời hạn.

**Đừng mặc định mọi thread chờ lock đều ở `BLOCKED`.** Theo ý nghĩa của `Thread.State`, `BLOCKED` dùng cho thread chờ monitor của `synchronized`. Một thread chờ `ReentrantLock` không bắt buộc biểu hiện như thread đang xin intrinsic monitor.

Dù `getState()` hữu ích khi chẩn đoán, đây chỉ là một snapshot. Không kiểm tra `worker.getState() == WAITING` rồi suy ra worker chắc chắn đã gọi đúng API. Để viết test xác định, sử dụng `CountDownLatch`/`Phaser`/điều phối phù hợp, sau đó khẳng định hành vi mong muốn qua kết quả, như ví dụ trong bài.

Sự khác biệt giữa platform thread và virtual thread (Java 21+) không đổi **hợp đồng của `Lock.lockInterruptibly()`**: thread chờ có thể phản ứng với interrupt. Cơ chế JVM sử dụng carrier thread khi virtual thread chờ là chi tiết lập lịch; không nên dùng nó để bỏ qua kiểm tra cancel hoặc logic nhả khóa. Khả năng interrupt và tình huống blocking thực tế vẫn cần được kiểm tra trên thư viện, JDK và workload sử dụng.

## 14. Quan hệ với `Condition.await()`

`ReentrantLock` thường kết hợp với `Condition` để chờ một **điều kiện dữ liệu**, không chỉ chờ lấy khóa. Hai hành động này khác nhau:

1. **`lock.lockInterruptibly()`:** chờ quyền sở hữu **lock**.
2. **`condition.await()`:** caller **đã giữ lock**, tạm nhả lock và chờ điều kiện được báo hiệu; khi trở về, caller lấy lại lock trước khi tiếp tục theo hợp đồng Condition.

Ví dụ minh họa:

```java
lock.lockInterruptibly();
try {
    while (!ready) {
        condition.await();
    }
    consumeReadyData();
} finally {
    lock.unlock();
}
```

Đây là đoạn minh họa với `lock`, `condition` được tạo từ `lock.newCondition()`, còn `ready` và `consumeReadyData()` do ứng dụng định nghĩa.

`Condition.await()` thường cũng có thể bị ngắt và ném `InterruptedException`. Vì vậy dù ngắt xảy ra ở **giai đoạn xin khóa** hay **giai đoạn đợi điều kiện sau khi đã có khóa**, code cần xử lý hợp đồng cho đúng:

- Nếu `lockInterruptibly()` ném exception, **không có lock** để nhả.
- Nếu `condition.await()` ném exception, caller đã lấy lock trước đó; theo hợp đồng `Condition` phù hợp, việc chờ đã quản lý nhả/lấy lại lock trước khi trở về/throw; khối `finally` ngoài vẫn phải `unlock()`.
- Kiểm tra điều kiện dưới `while` chứ không chỉ `if`, vì wakeup không đồng nghĩa condition đã đúng.

Không dùng `Thread.sleep()` trong lúc giữ `ReentrantLock` để chờ thread khác sửa `ready` dưới cùng lock: `sleep` không nhả lock, khiến thread kia không thể cập nhật dữ liệu. `Condition.await()` mới là cơ chế chờ điều kiện phù hợp với `ReentrantLock`.

## 15. Hủy có trách nhiệm: resource cleanup và tính nhất quán

Hỗ trợ hủy nhanh là mục tiêu quan trọng, nhưng **không đánh đổi bằng trạng thái dữ liệu sai**.

### 15.1. Đặt điểm hủy ở ranh giới an toàn

Nếu task chỉ cần đọc hoặc làm việc độc lập, có thể kiểm tra interrupt thường xuyên. Nếu task giữ lock để cập nhật nhiều trường liên quan, hãy xác định một đơn vị nguyên tử nghiệp vụ (một transaction trong bộ nhớ hoặc một batch) và chỉ hủy tại các điểm không phá invariant.

### 15.2. Không nhả lock khi chưa lấy được

Code nhả khóa phải chỉ chạy trên đường lấy khóa thành công:

```java
try {
    lock.lockInterruptibly();
    try {
        doWork();
    } finally {
        lock.unlock();
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

Đoạn này minh họa cách đặt `try/finally` lồng nhau. Nó hợp lệ về mặt unlock vì `finally` trong chỉ được vào sau `lockInterruptibly()` đã trả về. Nếu catch xong mà task còn việc cần lock, phải dừng chứ không được tiếp tục vào vùng dữ liệu bảo vệ mà không có khóa.

### 15.3. Không giữ lock khi làm I/O không kiểm soát

Nếu critical section bao trùm gọi mạng, đợi Future, thao tác file chậm hoặc callback tùy ý, thread khác có thể chờ khóa rất lâu. Điều này tăng nhu cầu cancellation và có thể gây vòng chờ. Khi có thể, tính toán hoặc thực hiện I/O bên ngoài critical section, rồi lấy lock ngắn để kiểm tra/commit dữ liệu. Nhưng **đừng chia nhỏ critical section làm vi phạm invariant**.

### 15.4. Chính sách hủy nên được mô tả rõ

Trong API của một worker hoặc service, trả lời:

- Request hủy đi qua kênh nào: `Future.cancel(true)`, `Thread.interrupt()` hay token riêng?
- Nếu chưa lấy được lock, có bỏ task hoàn toàn không?
- Nếu đang giữ lock, có điểm rollback/commit an toàn không?
- Nếu deadline hết, trả về trạng thái gì và ai chịu trách nhiệm retry?
- Exception có được propagate hay chuyển thành response lỗi có cấu trúc?
- Có xác nhận tài nguyên/lock được dọn xong trước khi báo “đã dừng” không?

Không sử dụng `Thread.stop()` để cưỡng chế kết thúc thread đang giữ khóa; đây là API không an toàn và đã bị deprecated từ lâu.

## 16. Các lỗi phổ biến

### Lỗi 1 — gọi `unlock()` dù `lockInterruptibly()` đã ném exception

```java
try {
    lock.lockInterruptibly();
    update();
} finally {
    lock.unlock();  // Có thể chưa lấy được lock!
}
```

**Sai.** Đặt lời gọi `lockInterruptibly()` bên ngoài `try/finally` bảo vệ critical section hoặc dùng biến `acquired` được quản lý cẩn thận.

### Lỗi 2 — bắt `InterruptedException` rồi nuốt tín hiệu

```java
try {
    lock.lockInterruptibly();
} catch (InterruptedException ignored) {
    // Không báo lên và cũng không khôi phục interrupt.
}
```

**Sai nếu ứng dụng yêu cầu khả năng hủy.** Truyền exception lên tầng gọi, hoặc khôi phục cờ và kết thúc công việc khi không thể propagate. Không tiếp tục thao tác cần lock mà chưa có lock.

### Lỗi 3 — nghĩ `lockInterruptibly()` sẽ ngừng mọi code sau khi lấy khóa

Phương thức chỉ giải quyết giai đoạn acquisition. Sau khi đã lấy lock, task phải tự hợp tác với interrupt, chịu trách nhiệm invariant và unlock.

### Lỗi 4 — dùng `lock()` rồi trông chờ `Future.cancel(true)` dừng ngay

Một task đang đợi `ReentrantLock.lock()` có thể tiếp tục đợi dù đã interrupt. Nếu cần cancel khi đang chờ, dùng `lockInterruptibly()` hoặc timed `tryLock` có hợp đồng phù hợp.

### Lỗi 5 — không phân biệt timeout và interrupt

`tryLock(timeout, unit)` trả về `false` khi hết thời gian nhưng **ném `InterruptedException`** khi bị ngắt. Hai tình huống có thể cần kết quả nghiệp vụ khác nhau; đừng gom cả hai thành một trạng thái chung mà mất thông tin.

### Lỗi 6 — đoán thứ tự thread bằng `sleep()`

Lịch chạy CPU không được cam kết. Trong unit test concurrency, dùng latch/phaser/điều phối để chứng minh precondition và việc kết thúc. Tránh viết test dựa trên “sleep 50 ms chắc worker đã chờ lock”.

### Lỗi 7 — tưởng `cancel(true)` là task đã sạch tài nguyên

`Future` có thể được đánh dấu canceled trước khi task thực sự hoàn thành cleanup. Khi cần xác minh dọn dẹp, có một tín hiệu hoặc cơ chế giám sát riêng như `CountDownLatch` ở `finally` của task.

### Lỗi 8 — dùng `lockInterruptibly()` nhưng mọi thread lấy nhiều khóa theo thứ tự đảo ngược

Thread có thể thoát vòng chờ khi bị interrupt, nhưng **deadlock vẫn có thể xảy ra** nếu không có interrupt hoặc nếu code không nhả khóa đã giữ. Quy định thứ tự lấy khóa nhất quán vẫn là nguyên tắc chính.

### Lỗi 9 — hủy giữa một cập nhật nhiều trường

Ngay cả khi finally nhả khóa đúng, dữ liệu có thể ở trạng thái không hợp lệ nếu một nửa giao dịch đã chạy. Cần hoàn tất hoặc rollback đơn vị cập nhật trước khi rời critical section.

## 17. Bài tập tự kiểm tra

**Câu 1.** Main giữ một `ReentrantLock`, worker gọi `lockInterruptibly()` và chờ. Main gọi `worker.interrupt()` nhưng chưa nhả lock. Worker có phải chờ đến khi main nhả lock không?

**Trả lời:** Không nhất thiết. Với `ReentrantLock.lockInterruptibly()`, interrupt làm worker ném `InterruptedException` và thoát bước chờ mà không cần lấy được lock. Worker phải rời task đúng cách; nó không được `unlock()` nếu chưa hề lấy lock.

**Câu 2.** Thay `lockInterruptibly()` bằng `lock()` trong tình huống trên. `interrupt()` có lập tức hủy việc chờ không?

**Trả lời:** Không. Với `ReentrantLock.lock()`, worker có thể tiếp tục đợi cho đến khi lấy được lock. Trạng thái interrupt được giữ/khôi phục để code tiếp theo có thể xử lý.

**Câu 3.** `lockInterruptibly()` có thể ném `InterruptedException` dù khóa hiện đang rảnh không?

**Trả lời:** Có. Nếu thread đã bị interrupted trước khi gọi, `ReentrantLock.lockInterruptibly()` có thể ưu tiên đáp ứng interrupt và ném exception thay vì lấy khóa.

**Câu 4.** Một worker đã lấy được lock và đang cập nhật ba trường liên quan. Main gửi interrupt. Lock có tự nhả và dữ liệu có tự rollback không?

**Trả lời:** Không. Interrupt là tín hiệu hợp tác. Worker phải xử lý ở điểm an toàn, hoàn thành/rollback cập nhật và thoát qua `finally` để nhả khóa.

**Câu 5.** `tryLock(100, TimeUnit.MILLISECONDS)` trả `false` có đồng nghĩa worker bị interrupt không?

**Trả lời:** Không. `false` nghĩa là không lấy được khóa trong thời hạn. Nếu thao tác bị interrupt khi chờ, phương thức có thể ném `InterruptedException`.

**Câu 6.** Vì sao `Future.cancel(true)` không phải bằng chứng chắc chắn task đã cleanup xong?

**Trả lời:** Việc đánh dấu Future canceled và gửi interrupt có thể xảy ra trước khi task thực sự dừng. Task có thể bỏ qua interrupt hoặc cần thời gian nhả tài nguyên; nên có cơ chế xác nhận cleanup nếu hợp đồng yêu cầu.

**Câu 7.** Một thread đã chiếm khóa A, rồi gọi `B.lockInterruptibly()` và bị ngắt. Phải nhả khóa nào?

**Trả lời:** Nó chưa có khóa B do lời gọi bị ngắt. Phải nhả khóa A đã lấy trong `finally`; không được gọi `B.unlock()` khi chưa chiếm được B.

**Câu 8.** Với `ReentrantLock` và `Condition`, `lockInterruptibly()` khác `condition.await()` ở đâu?

**Trả lời:** `lockInterruptibly()` chờ **lấy quyền sở hữu lock**. `await()` dùng khi caller **đã giữ lock**, rồi nhả lock tạm thời để chờ điều kiện và chiếm lại trước khi trở về theo hợp đồng. Cả hai có thể phản ứng với interrupt nhưng phục vụ các giai đoạn khác nhau.

**Câu 9.** Một ứng dụng luôn cần hủy nếu đợi lock quá 2 giây mà không có thread nào gọi `interrupt()`. Nên dùng `lockInterruptibly()` riêng lẻ hay `tryLock(2, TimeUnit.SECONDS)`?

**Trả lời:** Timed `tryLock` cung cấp timeout cần thiết, đồng thời vẫn có thể bị interrupt. `lockInterruptibly()` không tự hết thời hạn nếu không có yêu cầu ngắt.

## 18. Tóm tắt

`lockInterruptibly()` cho phép **hủy hợp tác ngay trong giai đoạn chờ lấy khóa**, nhờ đó phù hợp với task có vòng đời yêu cầu cancel, shutdown hoặc phản ứng với interrupt. Khác `lock()`, một thread đang chờ `ReentrantLock.lockInterruptibly()` có thể ném `InterruptedException` mà không cần đợi khóa được nhả.

Bốn quy tắc thực hành phải luôn đi cùng nhau:

1. **Chỉ gọi `unlock()` sau khi đã lấy được lock thành công.**
2. **Không nuốt `InterruptedException`**: propagate hoặc khôi phục cờ và thoát task.
3. **Không nhầm hủy khi đợi lock với hủy khi đang làm việc**: phần thân critical section vẫn phải kiểm tra tín hiệu ở điểm an toàn và giữ nguyên invariant.
4. **Chọn API theo chính sách chờ**: `lock()` nếu thực sự cần đợi không hủy tại acquisition, `lockInterruptibly()` nếu cần phản ứng với interrupt, `tryLock(timeout, unit)` nếu cần cả thời hạn và ngắt.

Khi kết hợp xử lý interrupt đúng, `try/finally`, giao thức khóa tránh deadlock và cơ chế quản lý task, ứng dụng Java có thể hủy công việc đang chờ tài nguyên mà không làm rò rỉ khóa hoặc phá hỏng dữ liệu dùng chung.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-and-conditions-005">Fairness policy: throughput và starvation</a>

<details>
<summary>Xem chi tiết</summary>

# Fairness policy: throughput và starvation trong Java Locks & Conditions

## 1. Vì sao chính sách fairness ảnh hưởng tới ứng dụng đồng thời?

Khi nhiều thread cùng muốn vào một vùng **critical section**, khóa quyết định thread nào có quyền tiếp tục khi tài nguyên sẵn sàng. Nếu một số thread được vào liên tục còn những thread khác phải chờ rất lâu, hệ thống có thể có thông lượng tốt ở mức tổng thể nhưng trải nghiệm của từng công việc không đồng đều.

Trong Java, `ReentrantLock`, `ReentrantReadWriteLock` và `Semaphore` có tùy chọn **fairness**. Đây là chính sách về **thứ tự ưu tiên cấp quyền khi có cạnh tranh**, không phải lời hứa rằng mọi thread hoặc mọi request đều hoàn thành trong thời gian bằng nhau.

Ba khái niệm cần tách riêng:

- **Fairness (tính công bằng):** mức độ cơ chế cấp quyền tôn trọng các thread đã chờ trước khi thread mới đến.
- **Throughput (thông lượng):** số công việc hoàn tất trong một đơn vị thời gian, ví dụ giao dịch/giây hoặc số lần vào critical section/giây.
- **Starvation (đói tài nguyên):** một thread có thể phải chờ kéo dài bất thường hoặc không đạt tiến triển do những thread khác tiếp tục nhận tài nguyên.

Điểm quan trọng: **fairness mạnh hơn có thể làm giảm thông lượng**, đặc biệt khi có nhiều thread tranh chấp một khóa và việc chuyển quyền đòi hỏi đánh thức/chuyển ngữ cảnh. Ngược lại, cấu hình không công bằng thường tận dụng tốt cơ hội thread đang chạy tiếp tục lấy khóa, nhưng có thể tạo độ trễ đuôi rất lớn cho một số waiter.

Bài này chỉ bàn về API và hành vi concurrency **thuần Java**, không liên quan cấu hình framework.

### Kết quả học tập

Sau bài này, bạn cần:

1. Phân biệt `ReentrantLock(true)` và `ReentrantLock(false)`; hiểu `fair` không có nghĩa “FIFO tuyệt đối”.
2. Giải thích **barging**, lock convoy, starvation và sự đánh đổi giữa throughput với p95/p99 wait time.
3. Nhận diện ngoại lệ quan trọng của `tryLock()` đối với fair lock.
4. Hiểu `Condition.await()`, `signal()`, `signalAll()` và việc reacquire lock có quan hệ gì với chính sách fairness.
5. Phân biệt fairness của khóa với fairness của `Semaphore` và `ReentrantReadWriteLock`.
6. Viết Java 17+ có thể chạy, và biết thiết kế benchmark/kiểm thử thay vì kết luận từ một lần quan sát scheduler.

## 2. Fair và nonfair thực chất khác nhau ở đâu?

### 2.1. Nonfair lock: cho phép barging

Với `new ReentrantLock()` hoặc `new ReentrantLock(false)`, mặc định khóa là **nonfair**. Một thread mới đến có thể giành được khóa dù đã có thread khác đợi, tùy trạng thái và cách triển khai cấp khóa. Hành vi một thread đến sau vượt lên trước waiter thường gọi là **barging**.

Điều này đôi khi có ích cho throughput: thread đang chạy trên CPU có thể lấy khóa ngay, thay vì phải đánh thức một thread đang ngủ và chờ nó được lập lịch. Những khoản chi phí chuyển ngữ cảnh, cache locality và scheduling có thể giảm đi.

Nhưng nếu nhiều “khách mới” liên tục giành quyền, một waiter cụ thể có thể chờ rất lâu. Càng cạnh tranh mạnh, việc chỉ đo số giao dịch trung bình càng có nguy cơ che mất độ trễ rất lớn của nhóm waiter kém may mắn.

### 2.2. Fair lock: ưu tiên waiter đã chờ lâu

`new ReentrantLock(true)` yêu cầu chính sách fair. Theo hợp đồng API, khi có tranh chấp, fair lock **ưu tiên cấp khóa cho thread có thời gian chờ lâu nhất**. Chính sách này giúp giảm khả năng một thread chờ lock bị bỏ lại liên tục bởi các thread đến sau.

Tuy vậy, Java documentation lưu ý rất rõ: **fairness của lock không bảo đảm fairness của scheduler**. Một thread có thể được cấp khóa nhiều lần trong một giai đoạn trong khi thread khác chưa được CPU lập lịch; việc chờ lock chỉ là một phần vòng đời task.

Một fair lock cũng không buộc hai thread phải **bắt đầu** đúng thứ tự chúng được tạo, `start()` hay enqueue task. Chính sách áp dụng tại những điểm cạnh tranh khóa theo hợp đồng, không tạo thứ tự toàn cục cho toàn bộ ứng dụng.

### 2.3. So sánh khái quát

| Tiêu chí | Nonfair `ReentrantLock` | Fair `ReentrantLock(true)` |
| --- | --- | --- |
| Mặc định của `ReentrantLock()` | Có | Không |
| Quyền barging với `lock()` khi có waiter | Có thể | Thường nhường ưu tiên cho waiter chờ lâu |
| Mục tiêu điển hình | Throughput cao trong workload cạnh tranh | Giảm chênh lệch thời gian chờ cấp khóa |
| Độ trễ đuôi của một waiter | Có thể rất xấu | Thường dễ kiểm soát hơn, không có deadline tuyệt đối |
| Context switching / handoff | Có thể tiết kiệm hơn | Có thể tốn hơn khi tranh chấp mạnh |
| Loại bỏ deadlock | Không | Không |
| Bảo vệ shared mutable state nếu mọi đường truy cập cùng khóa | Có | Có |
| Bảo đảm scheduler cấp CPU công bằng | Không | Không |

Khi **ít hoặc không có contention**, việc bật fairness có thể không tạo lợi ích quan sát được; chỉ khi workload thực sự có nhiều waiter, chính sách này mới thành vấn đề vận hành đáng cân nhắc.

## 3. Cấu hình và quan sát fairness với ReentrantLock

Cách khai báo:

~~~java
// Minh họa cấu hình; đoạn này không phải chương trình độc lập.
ReentrantLock defaultLock = new ReentrantLock();
ReentrantLock explicitlyNonfair = new ReentrantLock(false);
ReentrantLock fairLock = new ReentrantLock(true);

System.out.println(defaultLock.isFair());       // false
System.out.println(explicitlyNonfair.isFair());  // false
System.out.println(fairLock.isFair());           // true
~~~

`isFair()` báo **chính sách đã cấu hình**, không báo số lần mỗi thread được cấp khóa hoặc xác nhận latency đã công bằng. Muốn đánh giá hiệu quả, phải đo thời gian chờ, thời gian giữ khóa, throughput, số lần timeout/rejection và độ trễ tổng thể.

### 3.1. Ví dụ 1 — nhiều thread dùng fair lock (Java 17+)

Ví dụ sau xác minh kết quả cập nhật dùng chung và cho thấy nơi cấu hình fairness. Nó cố ý **không khẳng định thứ tự thread vào khóa**: thứ tự đó có thể thay đổi theo scheduler.

Tạo tệp `FairCounterDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class FairCounterDemo {
    static final class Counter {
        private final ReentrantLock lock;
        private int value;

        Counter(boolean fair) {
            lock = new ReentrantLock(fair);
        }

        void increment() {
            lock.lock();
            try {
                value++;
            } finally {
                lock.unlock();
            }
        }

        int value() {
            lock.lock();
            try {
                return value;
            } finally {
                lock.unlock();
            }
        }

        boolean isFair() {
            return lock.isFair();
        }
    }

    public static void main(String[] args) throws Exception {
        Counter counter = new Counter(true);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        Future<?>[] tasks = new Future<?>[4];

        try {
            for (int i = 0; i < tasks.length; i++) {
                tasks[i] = executor.submit(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < 1000; j++) {
                            counter.increment();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            start.countDown();
            for (Future<?> task : tasks) {
                task.get();
            }

            System.out.println("fair = " + counter.isFair());
            System.out.println("count = " + counter.value());
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac FairCounterDemo.java
java FairCounterDemo
~~~

Stdout khi task không bị interrupt và hoàn thành bình thường:

~~~text
fair = true
count = 4000
~~~

Bốn worker cùng chờ latch mở, mỗi worker tăng bộ đếm 1.000 lần. `ReentrantLock` bảo vệ phép `value++` nên tổng bằng 4.000. `isFair()` là `true` vì constructor chọn chính sách fair. `Future.get()` giúp caller đợi từng task hoàn tất và nhận lỗi nếu task bị exception không được xử lý.

**Điều ví dụ không chứng minh:** Nó không đo fair lock nhanh/chậm hơn nonfair lock, không chứng minh một thread cụ thể được cấp khóa trước, và không cho biết độ trễ đuôi của waiter. Thay `Counter(true)` bằng `Counter(false)` vẫn cho tổng 4.000 nếu cùng khóa bảo vệ mọi cập nhật; khác biệt nằm ở **chính sách tranh chấp**, không ở correctness của phép đếm đã khóa.

Nếu task bị interrupt trong `start.await()`, nó sẽ khôi phục interrupt flag rồi bỏ qua vòng tăng; vì thế điều kiện “task hoàn tất bình thường” ở output còn bao gồm việc không có hủy/interrupt từ bên ngoài. Trong code sản phẩm, nên quyết định rõ cách trả thất bại khi task bị hủy thay vì chỉ bỏ qua phần việc.

### 3.2. Tại sao phải unlock trong finally?

~~~java
// Minh họa trong một phương thức; lock đã được khai báo.
lock.lock();
try {
    mutateSharedState();
} finally {
    lock.unlock();
}
~~~

Fairness **không** giúp nếu một thread giữ khóa rồi thoát do exception mà không release. `ReentrantLock` không tự mở khóa khi rời method như `synchronized`. Cần `finally` và tránh giữ khóa xuyên qua I/O không giới hạn, callback không kiểm soát hoặc sleep dài.

## 4. Ngoại lệ cần nhớ: tryLock() có thể barging ngay cả trên fair lock

Một nhầm lẫn quan trọng là suy ra `new ReentrantLock(true)` khiến **mọi cách lấy khóa** tuân thủ hàng đợi.

Thực tế, theo hợp đồng `ReentrantLock`:

- `lock()` trên fair lock áp dụng chính sách ưu tiên waiter khi có contention.
- `lockInterruptibly()` cho phép chờ có thể ngắt, vẫn tuân thủ chính sách fair của khóa.
- `tryLock()` **không có timeout** sẽ lấy khóa ngay nếu khóa đang rảnh, **kể cả khi các thread khác đang chờ**, tức cho phép **barging dù lock được cấu hình fair**.
- `tryLock(timeout, unit)` trên fair lock tôn trọng chính sách fairness khi chờ quyền lấy khóa và có thể bị interrupt.
- Theo hướng dẫn API, `tryLock(0, TimeUnit.SECONDS)` có thể dùng khi muốn thử lấy ngay mà vẫn tôn trọng fairness; khác `tryLock()` không tham số. Lưu ý dạng có timeout có thể ném `InterruptedException`.

Điều này có ý nghĩa nếu code vừa dùng `lock()` ở đường chính vừa dùng `tryLock()` không tham số ở đường khác: đường thử nhanh có thể vượt lên trước những waiter đang được xếp hàng. Không nên tuyên bố “không starvation vì lock đã fair” nếu ứng dụng còn nhiều đường barging.

### Ví dụ 2 — tryLock không chặn vô thời hạn (Java 17+)

Tạo tệp `TryLockWaitDemo.java`:

~~~java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class TryLockWaitDemo {
    public static void main(String[] args)
            throws InterruptedException {
        ReentrantLock lock = new ReentrantLock(true);
        lock.lock(); // Luồng main đang giữ khóa.

        try {
            Thread worker = new Thread(() -> {
                boolean immediate = lock.tryLock();
                System.out.println("immediate = " + immediate);

                if (immediate) {
                    lock.unlock();
                }

                try {
                    boolean timed =
                        lock.tryLock(50, TimeUnit.MILLISECONDS);
                    System.out.println("timed = " + timed);

                    if (timed) {
                        lock.unlock();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    System.out.println("interrupted");
                }
            }, "contender");

            worker.start();
            worker.join();
        } finally {
            lock.unlock();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac TryLockWaitDemo.java
java TryLockWaitDemo
~~~

Stdout thông thường khi worker không bị interrupt:

~~~text
immediate = false
timed = false
~~~

Vì `main` giữ khóa cho đến **sau** `worker.join()`, worker không thể lấy khóa từ thread khác: `tryLock()` trả false ngay, còn `tryLock(50, MILLISECONDS)` chờ tối đa khoảng thời gian yêu cầu rồi trả false. Lời gọi timeout không bảo đảm chính xác 50 ms theo đồng hồ.

Ví dụ này cho thấy xử lý **thất bại có kiểm soát**, không phải một cuộc đua giữa các waiter. Nó không minh họa trực tiếp barging, vì khi worker thử, khóa chưa rảnh. Để nghiên cứu barging, cần thử trên những lịch thực thi có waiter đang xếp hàng, đo thứ tự cấp khóa, và chấp nhận rằng kết quả của một lần chạy không cố định.

### 4.1. tryLock không tự đảm bảo forward progress

Khi `tryLock` trả false, công việc vẫn chưa được thực hiện. Nếu code luôn thử lại trong một vòng lặp bận, một số thread có thể chiếm CPU mà không tiến triển, tạo contention mới hoặc livelock. Cần chọn backoff, giới hạn retry, deadline, queue hoặc một phương pháp blocking thích hợp.

Đối với nhiều khóa phải lấy cùng lúc, `tryLock` có thể giúp **trả lại khóa đã giữ** khi không lấy được khóa tiếp theo; nhưng vẫn cần thứ tự khóa hoặc giao thức phối hợp đúng để tránh deadlock, livelock và starvation.

## 5. Starvation khác deadlock và livelock như thế nào?

| Hiện tượng | Mô tả | Ví dụ |
| --- | --- | --- |
| **Starvation** | Một task không được tài nguyên đủ lâu dù công việc khác vẫn hoàn thành | Waiter liên tục bị task mới barging |
| **Deadlock** | Một nhóm task chờ nhau theo chu trình, không ai giải phóng phụ thuộc cần thiết | A giữ lock 1 chờ 2; B giữ 2 chờ 1 |
| **Livelock** | Task liên tục phản ứng/nhường nhau nhưng không hoàn tất việc hữu ích | Hai thread lặp lại tryLock rồi cùng retry đồng bộ |
| **Long blocking** | Một task chờ rất lâu vì owner giữ tài nguyên quá lâu | Holder gọi I/O chặn trong critical section |
| **Priority inversion** | Task ưu tiên cao phải đợi tài nguyên do task ưu tiên thấp đang giữ | High priority chờ lock; low priority chưa được chạy để release |

Có thể có starvation mà **không có deadlock**: một tập task liên tục hoàn thành trong khi task X vẫn chờ. Fair lock giúp giảm nguy cơ starvation **do thứ tự cấp khóa**, nhưng không giải quyết CPU scheduling starvation, deadlock do giữ nhiều khóa, pool worker starvation hoặc task giữ khóa quá lâu.

Cũng không có chứng minh “không starvation tuyệt đối” chỉ bằng `isFair() == true`. Nếu worker chưa từng được CPU lập lịch để chạy tới `lock()`, nó chưa nằm trong hàng đợi chờ khóa theo nghĩa cần xét. Nếu owner không release, waiter vẫn chờ bất kể chính sách nào.

## 6. Throughput, latency và lock convoy

### 6.1. Vì sao nonfair thường nhanh hơn khi tranh chấp mạnh?

Trong một workload có critical section ngắn, nhiều thread đang hoạt động và queue nhiều waiter:

1. Thread X release lock.
2. Một waiter Y đang đợi cần được đánh thức và CPU lập lịch.
3. Trong lúc đó thread Z vừa chạy trên CPU muốn lấy lock.

Với chính sách nonfair, Z có thể lấy khóa ngay nếu nó nhìn thấy khóa đang rảnh. Việc tận dụng thread đang chạy có thể giảm chờ scheduler và tránh một số lần chuyển ngữ cảnh.

Với chính sách fair, việc ưu tiên waiter đã chờ có thể yêu cầu chờ Y tiến triển. Nếu Y chưa được CPU lập lịch, những thread đang sẵn sàng chạy có thể phải đợi. Trong một số hệ thống, hiện tượng **lock convoy** (các thread xếp hàng chờ được phục vụ nối tiếp) làm throughput giảm.

Nhưng từ “thường” là định hướng thực nghiệm, **không phải định luật cho mọi tải**. Trong workload ít contention, rất nhiều thời gian nằm ở I/O, hoặc chính sách cấp CPU khác nhau, kết quả có thể khác. Chỉ benchmark trong môi trường đại diện mới xác định được mức đánh đổi.

### 6.2. Đừng chỉ đo số task hoàn thành

Một cấu hình có thể tạo 20.000 thao tác/giây nhưng một số task đợi vài giây mới lấy được lock. Cấu hình khác có thể xử lý 18.000 thao tác/giây nhưng giảm đáng kể thời gian chờ đuôi của waiter.

Chỉ số nên quan sát:

| Chỉ số | Câu hỏi |
| --- | --- |
| Throughput (ops/s) | Hệ thống hoàn tất bao nhiêu thao tác mỗi giây? |
| Lock wait p50/p95/p99 | Một waiter điển hình và waiter chậm nhất đợi bao lâu? |
| Max wait / timeout rate | Có task nào bị bỏ đói hoặc vượt deadline? |
| Critical-section hold time | Owner giữ khóa trong bao lâu? Có I/O/blocking trong khóa không? |
| Active waiters / queue length | Có bao nhiêu thread đang tranh chấp? |
| CPU/context switching | Scheduler overhead có đang lấn át công việc hữu ích? |
| GC/allocations | Công việc trong vùng khóa có tạo sức ép bộ nhớ không? |
| Per-thread completion distribution | Một số thread có được cấp quyền ít hơn hẳn không? |

Thông lượng cao nhưng p99 tăng đột biến có thể không phù hợp với tác vụ tương tác; ngược lại, một batch cần tổng tốc độ tối đa có thể chấp nhận chênh lệch thời gian chờ cá nhân nếu không có ràng buộc công bằng mạnh.

### 6.3. Fairness có thể tăng độ trễ trung bình không?

Có thể. Nếu chính sách handoff tốn thời gian mà tất cả task đều ngắn, tổng thời gian mỗi lần vào khóa có thể tăng. Đồng thời, việc giảm các lượt chờ cực dài có thể **cải thiện phân phối latency**. Cần nhìn cả average, tail latency và deadline miss; không có bảo đảm rằng fair luôn cải thiện p99 ở mọi môi trường.

Giảm thời gian giữ khóa, loại bỏ khóa không cần thiết, chia vùng trạng thái, dùng immutable snapshot hoặc message passing đôi khi mang lại lợi ích lớn hơn tranh luận fair/nonfair. Tuy nhiên, tối ưu không được phá invariant của dữ liệu chia sẻ.

## 7. Condition: được signal không đồng nghĩa được chạy ngay

`Condition` thường được tạo từ `ReentrantLock.newCondition()`, tách một hàng chờ **điều kiện nghiệp vụ** khỏi việc chờ **quyền sở hữu khóa**.

Quy trình cơ bản:

1. Thread giữ lock, thấy điều kiện chưa đúng.
2. Gọi `condition.await()`, **tạm release lock** và bắt đầu chờ tín hiệu.
3. Một thread khác lấy lock, cập nhật trạng thái rồi gọi `signal()` hoặc `signalAll()`.
4. Thread được đánh thức **phải lấy lại lock** trước khi `await()` hoàn tất và nó tiếp tục kiểm tra trạng thái.

**Signal không trực tiếp trao lock cho waiter**, và không có bảo đảm waiter được CPU chạy ngay sau `signal()`. Owner hiện tại vẫn giữ lock cho đến khi unlock, và nhiều thread khác có thể đang tranh chấp.

Theo hợp đồng `ConditionObject` của `ReentrantLock`, waiter được signal theo thứ tự FIFO trên hàng chờ condition, nhưng việc chúng **lấy lại lock** chịu chính sách acquisition của lock liên quan. Với fair lock, các thread cạnh tranh sau signal tuân thủ ưu tiên chờ khóa; điều đó vẫn không tạo deadline cứng hoặc bảo đảm scheduler.

### Ví dụ 3 — điều kiện và kiểm tra bằng while (Java 17+)

Tạo tệp `ConditionSignalDemo.java`:

~~~java
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ConditionSignalDemo {
    static final class Gate {
        private final ReentrantLock lock = new ReentrantLock(true);
        private final Condition openCondition = lock.newCondition();
        private boolean open;

        void awaitOpen() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (!open) {
                    openCondition.await();
                }
            } finally {
                lock.unlock();
            }
        }

        void open() {
            lock.lock();
            try {
                open = true;
                openCondition.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Gate gate = new Gate();

        Thread waiter = new Thread(() -> {
            try {
                gate.awaitOpen();
                System.out.println("Gate opened");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                System.out.println("Wait interrupted");
            }
        }, "gate-waiter");

        waiter.start();
        gate.open();
        waiter.join();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ConditionSignalDemo.java
java ConditionSignalDemo
~~~

Khi waiter không bị interrupt, stdout là:

~~~text
Gate opened
~~~

Dù `gate.open()` có thể chạy **trước** khi waiter đi vào `await()`, chương trình vẫn đúng vì trạng thái `open` được lưu và kiểm tra dưới cùng lock. Nếu `open` đã là true, waiter không chờ và không phụ thuộc vào việc có “nhận được signal” hay không.

`while (!open)` là bắt buộc về mặt mẫu thiết kế: Condition cho phép **spurious wakeup**, và trạng thái có thể đổi giữa signal với thời điểm waiter reacquire lock. Dùng `if` đơn giản có thể khiến waiter tiếp tục dù điều kiện không còn đúng.

`signal()` chỉ đánh thức một waiter đủ điều kiện; `signalAll()` đánh thức tất cả, đôi khi tạo cạnh tranh lớn khi nhiều thread cùng cố reacquire lock. Chọn theo bất biến và số waiter có thể tiến triển, không theo giả định rằng signalAll luôn “công bằng hơn”.

### 7.1. Chờ Condition có thể starvation không?

Có. Ngay cả khi lock fair, một task có thể không bao giờ nhận tín hiệu phù hợp, hoặc predicate của nó liên tục trở thành false trước khi reacquire. Fairness của lock không thay thế logic điều phối condition và không bảo đảm thứ tự hoàn thành nghiệp vụ.

Phải đánh giá:

- Điều kiện chờ có thể trở thành true không?
- Mọi thay đổi trạng thái quan trọng có signal đúng condition không?
- Có cần `signalAll` vì nhiều waiter có các predicate khác nhau không?
- Có timeout/cancellation để xử lý chờ dài không?
- Có thread nào có thể giữ lock quá lâu hoặc bị scheduler bỏ đói không?

Các câu hỏi đó riêng biệt với việc bật `new ReentrantLock(true)`.

## 8. Fairness của Semaphore khác gì fairness của Lock?

`Semaphore` quản lý một số **permit**. Ví dụ một semaphore có ba permit cho phép tối đa ba thao tác cùng được cấp permit, thường dùng để giới hạn concurrency đi vào tài nguyên ngoài.

~~~java
// Minh họa, không phải chương trình độc lập.
Semaphore permits = new Semaphore(3, true);

permits.acquire();
try {
    accessLimitedResource();
} finally {
    permits.release();
}
~~~

Cần import `java.util.concurrent.Semaphore` và xử lý `InterruptedException` cho `acquire()`. Với `fair=true`, semaphore áp dụng thứ tự FIFO tại **các điểm xếp hàng nội bộ cụ thể** khi có contention. Không suy ra đơn giản “thread gọi acquire trước chắc chắn lấy permit trước” vì thứ tự thực sự phụ thuộc thread nào tới điểm đó trước.

**Ngoại lệ tương tự lock:** `Semaphore.tryAcquire()` không tham số có thể lấy permit đang rảnh **dù có waiter trước**, ngay cả khi semaphore được cấu hình fair. `tryAcquire(0, TimeUnit.SECONDS)` áp dụng fairness khi có contention và có thể ném `InterruptedException`.

`Semaphore` không ràng buộc owner như `ReentrantLock`; một thread có thể `release()` permit do thread khác lấy, nên logic cấp/trả permit phải đặc biệt cẩn thận để tránh trả thừa. Bật fair không sửa được việc release sai.

### Ví dụ 4 — giới hạn công việc bằng fair semaphore (Java 17+)

Tạo tệp `FairSemaphoreDemo.java`:

~~~java
import java.util.concurrent.Semaphore;

public class FairSemaphoreDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Semaphore slots = new Semaphore(2, true);

        slots.acquire(2);
        System.out.println("available = " + slots.availablePermits());

        slots.release();
        System.out.println("available = " + slots.availablePermits());

        slots.release();
        System.out.println("fair = " + slots.isFair());
        System.out.println("available = " + slots.availablePermits());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac FairSemaphoreDemo.java
java FairSemaphoreDemo
~~~

Stdout:

~~~text
available = 0
available = 1
fair = true
available = 2
~~~

Ví dụ minh họa API và việc permit được quản lý đúng số lượng; vì không có tranh chấp giữa nhiều thread, nó **không** chứng minh chính sách fair làm thay đổi thứ tự phục vụ hay throughput. Khi benchmark semaphore, đo cả latency chờ permit và utilization của tài nguyên được bảo vệ.

## 9. Fairness của ReentrantReadWriteLock

`ReentrantReadWriteLock` tách **read lock** (cho phép nhiều reader cùng giữ khi không bị writer ngăn cản theo chính sách) và **write lock** (loại trừ quyền truy cập từ reader/writer khác).

- **Nonfair** là mặc định; không áp đặt ưu tiên theo thứ tự chờ và có thể khiến một số reader hoặc writer bị trì hoãn rất lâu trong điều kiện cạnh tranh liên tục.
- **Fair** (`new ReentrantReadWriteLock(true)`) áp dụng chính sách ưu tiên đến gần thứ tự thread yêu cầu: writer đơn lẻ chờ lâu và nhóm reader chờ lâu được phục vụ theo chính sách của JDK. Đó không phải yêu cầu mọi người đọc/ghi luôn hoàn thành theo thứ tự tuyệt đối.
- Các phương thức `readLock().tryLock()` và `writeLock().tryLock()` không tham số **không tôn trọng fairness**, tương tự `ReentrantLock.tryLock()`.
- Fairness không biến thời gian giữ read/write lock thành thời hạn có giới hạn; một writer vẫn có thể chờ reader đang giữ lock lâu, hoặc một reader bị gián đoạn/hết thời gian do yếu tố khác.

Đọc nhiều, ghi ít không đồng nghĩa read-write lock luôn nhanh hơn một `ReentrantLock`; cần xét số reader thực sự chạy song song, độ dài critical section, overhead của lock và khả năng writer bị chờ.

`ReentrantReadWriteLock.WriteLock` hỗ trợ `newCondition()`; `ReadLock.newCondition()` không hỗ trợ và ném `UnsupportedOperationException`. Các condition riêng của write lock cũng không tự cấp fairness cho predicate nghiệp vụ.

## 10. Chính sách fair không sửa các vấn đề nào?

### 10.1. Deadlock do thứ tự lấy nhiều khóa

Fair lock chỉ quyết định thứ tự **giữa các thread đang chờ cùng một lock**. Nó không loại bỏ vòng chờ:

~~~text
Thread A: giữ L1 -> chờ L2
Thread B: giữ L2 -> chờ L1
~~~

Cả L1 lẫn L2 đều cấu hình fair vẫn có thể deadlock. Phải dùng lock ordering thống nhất, giảm lồng khóa, hoặc giao thức tránh vòng chờ khác.

### 10.2. Giữ khóa quá lâu

Nếu một thread thực hiện truy vấn mạng, sleep hoặc ghi file chậm trong critical section, các waiter vẫn chờ đến lúc owner release. Fairness không ép owner nhường khóa theo time slice. Đo thời gian giữ khóa và đưa các tác vụ chặn dài ra ngoài vùng khóa khi bảo toàn bất biến cho phép.

### 10.3. Thread pool starvation

Một executor chỉ có một worker, task A đang chạy gửi task B vào cùng pool rồi chờ `B.get()`, có thể tự gây bế tắc phụ thuộc task. Bật fair cho một lock được task sử dụng không tạo thêm worker cho B.

### 10.4. CPU scheduler, priority và external resource

Fairness cấp khóa không kiểm soát thứ tự CPU scheduler, chính sách ưu tiên của OS, kết nối database, rate limit API hay tốc độ disk. Những điểm nghẽn đó cần biện pháp quản lý riêng: backpressure, timeout, bounded queues, capacity planning, v.v.

### 10.5. Correctness của dữ liệu dùng chung

Cả fair và nonfair lock đều có cơ chế mutual exclusion và memory visibility khi được dùng đúng cách. Một đường code đọc state ngoài khóa vẫn có thể sai dù khóa là fair. Lựa chọn fairness **không chữa data race** hoặc invariant bị vi phạm.

## 11. Dùng queue, thời gian chờ và quan sát trạng thái như thế nào?

`ReentrantLock` cung cấp các phương thức như `hasQueuedThreads()`, `hasQueuedThread(thread)`, `getQueueLength()` và `isFair()`. Chúng hữu ích cho **giám sát và chẩn đoán**, nhưng tình trạng queue có thể thay đổi ngay sau khi đọc. `getQueueLength()` là **ước lượng**, không phải số liệu giao dịch chính xác hoặc cơ sở để quyết định một thao tác compound an toàn.

Các giá trị đo nên gắn với task:

- Thời điểm ngay trước `lock()` và ngay sau khi acquire để tính thời gian chờ.
- Thời điểm acquire và release để tính thời gian giữ khóa.
- Thời điểm vào task và hoàn thành để tính end-to-end latency.
- Timeout/interrupt/retry count, phân phối theo loại task hoặc pool.
- Queue depth và context switching ở mức chẩn đoán.

Không nên log trên mỗi lần lấy khóa ở đường cực nóng nếu chính log trở thành nút thắt, hoặc làm sai phân phối latency của benchmark.

### 11.1. Mẫu đo thời gian chờ bằng System.nanoTime

~~~java
// Minh họa đặt trong phương thức có lock và recorder đã khởi tạo.
long started = System.nanoTime();
lock.lock();
long acquired = System.nanoTime();
try {
    performCriticalWork();
} finally {
    long released = System.nanoTime();
    lock.unlock();
    recorder.recordWaitNanos(acquired - started);
    recorder.recordHoldNanos(released - acquired);
}
~~~

Đây là mã minh họa, **không phải chương trình độc lập**. `System.nanoTime()` phù hợp để đo khoảng thời gian trong cùng JVM; `recorder` phải có chi phí thấp và thread-safe. Để không cho lỗi recorder làm ảnh hưởng tới việc giải phóng khóa, có thể đo trong critical section và **ghi nhận metrics sau khi đã unlock**, hoặc đảm bảo recorder không ném exception và không block.

Trong mã minh họa trên, phép đo `released` đặt trước `unlock()`, nên đây là ước lượng thời gian giữ khóa, không đo chính xác toàn bộ chi phí release; một recorder production nên nhất quán về cách đo và kiểm tra overhead.

## 12. Benchmark fair và nonfair đúng cách

### 12.1. Mẫu thiết kế benchmark

Không so sánh bằng cách chạy một lần rồi in `System.currentTimeMillis()`. Java có JIT compilation, GC, scheduler, CPU frequency và nhiều nhiễu môi trường. Với benchmark nghiêm túc:

1. **Chọn workload đại diện:** số worker, tỷ lệ đọc/ghi, độ dài critical section, mức contention, chi phí I/O và số lõi.
2. **Dùng cùng logic nghiệp vụ:** chỉ thay chính sách fair/nonfair, giữ nguyên pool, dữ liệu đầu vào, timeout, số tác vụ và môi trường.
3. **Warm-up và nhiều lượt đo:** để giảm sai số của JIT và các dao động ngắn.
4. **Đo thông lượng lẫn latency:** p50/p95/p99 wait time, per-thread completion, max wait và deadline miss.
5. **Kiểm tra correctness:** công việc hoàn tất đủ, invariant dữ liệu không bị phá, không bỏ qua exception.
6. **Tránh tự tạo bottleneck khi đo:** log đồng bộ, lock recorder hoặc khởi tạo object bên trong phép đo có thể che hiệu ứng đang kiểm tra.
7. **Phân biệt CPU-bound với I/O-bound:** chuyển ngữ cảnh và lock hold time có thể rất khác nhau.
8. **Không mặc định một kết luận:** khóa fair thường làm tăng chi phí dưới contention, nhưng mức giảm throughput và lợi ích về phân phối thời gian chờ phụ thuộc tải.

JMH là công cụ benchmark phổ biến trong hệ sinh thái Java. Khi benchmark khả năng bị starvation lâu dài, cần thêm bài thử có queue/waiter cạnh tranh liên tục và báo cáo phân phối thời gian chờ thay vì chỉ số throughput gộp.

### 12.2. Cách đọc một kết quả giả định

Giả sử hai cấu hình được đo cùng workload:

| Chỉ số **giả định** | Nonfair | Fair |
| --- | ---: | ---: |
| Throughput | 42.000 ops/s | 35.000 ops/s |
| p99 chờ lock | 18 ms | 8 ms |
| Max wait quan sát | 250 ms | 42 ms |

Đây **chỉ là số liệu minh họa để đọc bảng**, không phải benchmark thực tế, cũng không phản ánh một JVM hay phần cứng cụ thể. Nếu mục tiêu ưu tiên số operation tổng, nonfair có thể hấp dẫn. Nếu có deadline cho từng công việc, phân phối wait time của fair có thể phù hợp hơn.

Tuy vậy, nếu ứng dụng có một task đôi khi giữ lock 2 giây, chuyển sang fair **không** tự sửa được điểm nghẽn đó. Tối ưu đường giữ khóa có thể quan trọng hơn thay đổi chính sách cấp quyền.

## 13. Các sai lầm thường gặp

**“Fair lock bảo đảm thread nào gọi `lock()` trước thì vào trước.”** Không hẳn. Hai thread có thể được CPU lập lịch khác nhau; chính sách fair áp dụng khi có contention và chờ khóa, không cho phép suy ra thứ tự từ thời điểm tạo thread hoặc gửi task.

**“Bật fairness là chắc chắn không starvation.”** Không đúng với CPU scheduling, owner không release, condition không được signal, external resource và nhiều đường dùng `tryLock()` barging.

**“`tryLock()` trên fair lock luôn tôn trọng FIFO.”** Sai: overload không tham số có thể barging. Khi cần thử không chờ mà vẫn tôn trọng fairness, cân nhắc overload có timeout bằng 0 và xử lý interrupt.

**“Nonfair lock luôn nhanh hơn.”** Không có bảo đảm cho mọi workload; phải đo contention, throughput và latency theo ứng dụng thực.

**“Fairness giải quyết deadlock.”** Không. Thứ tự lấy nhiều khóa và chu trình chờ tài nguyên là vấn đề khác với thứ tự waiter của một khóa.

**“`signal()` trao lock ngay cho waiter.”** Không. Waiter chỉ tiếp tục sau khi reacquire lock; phải kiểm tra condition trong vòng `while`.

**“Dùng `signalAll()` là công bằng hơn.”** SignalAll có thể tạo một đợt cạnh tranh lớn, làm nhiều waiter thức dậy rồi lại chờ; lựa chọn phải theo predicate và yêu cầu liveness.

**“Queue length bằng 0 nghĩa là không có contention.”** `getQueueLength()` chỉ ước lượng tại một thời điểm; ngữ cảnh có thể thay đổi ngay sau khi đọc.

**“Một lần chạy 4 thread đã chứng minh fair/nonfair hiệu năng tốt hơn.”** Không. Cần benchmark nhiều lượt, kiểm soát điều kiện và số liệu phân phối, không kết luận từ thứ tự log tình cờ.

**“Bật fair sẽ sửa code không thread-safe.”** Fairness chỉ là chính sách cấp khóa; code đọc/ghi ngoài giao thức khóa vẫn có thể sai.

## 14. Hướng dẫn chọn chính sách

| Bài toán | Điểm ưu tiên | Gợi ý ban đầu |
| --- | --- | --- |
| Nhiều thao tác rất ngắn, throughput là ưu tiên chính | Tổng thông lượng, lock contention | Bắt đầu với nonfair mặc định, đo thời gian chờ |
| Nhiều công việc cùng loại, mỗi công việc có deadline | p95/p99 và starvation risk | Đánh giá fair, đồng thời đo throughput |
| Ít contention | Đơn giản, correctness | Nonfair thường đủ; fairness có thể không quan trọng |
| Reader nhiều, writer ít | Read/write contention | Đo `ReentrantReadWriteLock` và rủi ro writer wait |
| Giới hạn tài nguyên ngoài JVM | Permit queue và overload | `Semaphore` có hoặc không fair, tùy SLA |
| Condition với nhiều predicate | Predicate/liveness | Thiết kế đúng `await`/`signal`, fairness chỉ hỗ trợ reacquire |
| Nhiều khóa phải giữ cùng lúc | Không deadlock | Lock ordering nhất quán trước khi tối ưu fair |
| Task bị block bởi pool hoặc I/O | Capacity/deadline | Sửa bottleneck; fairness của lock không đủ |

Một cách làm thực tế là bắt đầu từ **tính đúng đắn** và **bất biến**, đo mức contention và yêu cầu chờ, sau đó chọn fair khi có bằng chứng về starvation hoặc latency đuôi đáng xử lý. Đừng chỉ thấy một thread chậm rồi vội bật fair: nguyên nhân có thể nằm ở thời gian giữ khóa, executor hoặc downstream.

## 15. Câu hỏi tự kiểm tra

**1.** `new ReentrantLock(true)` có bảo đảm task A hoàn thành trước task B nếu A được submit trước không?

Không. Thứ tự submit, được lập lịch, tới lời gọi lock, thời gian giữ lock và công việc sau critical section là các thứ tự khác nhau. Fairness chủ yếu xét quyền lấy lock khi có waiter.

**2.** Một fair `ReentrantLock` có hàng đợi waiter. Thread mới gọi `tryLock()` khi khóa vừa rảnh. Nó có thể vượt hàng không?

Có. `tryLock()` không tham số có thể barging dù khóa được cấu hình fair. Overload có timeout trên fair lock có ngữ nghĩa fairness khác.

**3.** Fairness có chữa deadlock giữa A giữ L1 chờ L2 và B giữ L2 chờ L1 không?

Không. Đó là vòng chờ của nhiều lock; cần thứ tự khóa chung hoặc giao thức khác tránh circular wait.

**4.** `condition.signal()` có làm waiter lập tức thực thi phần sau `await()` không?

Không. Waiter cần reacquire lock trước khi `await()` trả về; owner hiện tại vẫn có thể giữ lock và scheduler quyết định khi thread được chạy.

**5.** Vì sao phải đo cả throughput và p99 lock wait?

Throughput mô tả tổng số thao tác hoàn tất; p99 cho biết nhóm waiter chậm có thể chịu độ trễ lớn thế nào. Một cấu hình tổng thể nhanh hơn vẫn có thể tệ với các task có deadline.

**6.** Chỉ chọn `Semaphore(3,true)` có bảo đảm ba request sẽ hoàn thành theo đúng thứ tự nhận request không?

Không. Fairness áp dụng tại các điểm acquisition nội bộ của permit; việc được cấp permit không đồng nghĩa thời gian chạy công việc như nhau, và `tryAcquire()` không tham số có thể barging.

**Ghi nhớ:** **Fairness là chính sách cấp quyền khi cạnh tranh**, không phải một lời hứa về CPU scheduler hay thời hạn hoàn thành. Fair lock thường hỗ trợ giảm nguy cơ một waiter bị vượt hàng liên tục, đổi lại có thể giảm throughput. Hãy dùng đúng overload của API, giữ critical section ngắn, thiết kế condition chuẩn và đo cả hiệu năng tổng thể lẫn thời gian chờ của từng nhóm task.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-and-conditions-006">Condition await(), signal(), signalAll()</a>

<details>
<summary>Xem chi tiết</summary>

# Condition await(), signal(), signalAll() trong Java Concurrency

> **Ngữ cảnh:** Locks & Conditions của Java SE, tập trung vào `java.util.concurrent.locks.Condition`, cách phối hợp thread với `ReentrantLock`, trạng thái điều kiện (*predicate*), giao thức chờ/thông báo, interrupt, timeout và các bảo đảm Java Memory Model. Bài không sử dụng API concurrency riêng của Spring.

## 1. Bài toán: cần đợi một trạng thái, không chỉ lấy được khóa

Một **Lock** giúp các thread thay nhau vào critical section, nhưng không trả lời trực tiếp câu hỏi “đã có dữ liệu chưa?”, “buffer còn chỗ không?” hoặc “công việc đã được mở khóa logic chưa?”. Một thread có thể lấy được lock mà **điều kiện nghiệp vụ chưa đúng**. Nếu giữ lock và liên tục kiểm tra trong vòng lặp, thread khác không vào được để thay đổi trạng thái; nếu nhả lock rồi `Thread.sleep()`, việc phát hiện thay đổi bị trễ và không có một giao thức thông báo đáng tin.

`Condition` là một cơ chế *condition queue / wait set* gắn với `Lock`. Nó cho phép thread:

1. **Nắm lock**, kiểm tra trạng thái được bảo vệ.
2. Nếu predicate chưa đúng, gọi `await()` để **nhả lock và chờ**.
3. Thread khác lấy lock, **thay đổi trạng thái** rồi gọi `signal()` hoặc `signalAll()`.
4. Waiter thức dậy, **phải lấy lại lock** và kiểm tra predicate lần nữa trước khi hành động.

**Ý quan trọng nhất:** `Condition` không chứa “giá trị đúng/sai” của điều kiện, không tự nhớ mọi lần signal, cũng không thay thế biến trạng thái. Điều kiện thực sự là **predicate do chương trình định nghĩa**, ví dụ `!queue.isEmpty()` hoặc `count < capacity`. Các lời gọi `await/signal` chỉ giúp phối hợp khi predicate đó thay đổi.

## 2. Condition thuộc Lock nào?

`Condition` được tạo thông qua `Lock.newCondition()`, thường gặp nhất là `ReentrantLock.newCondition()`:

```java
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

final class SimpleGate {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition opened = lock.newCondition();
    private boolean isOpen;

    void open() {
        lock.lock();
        try {
            isOpen = true;
            opened.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void awaitOpen() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (!isOpen) {
                opened.await();
            }
        } finally {
            lock.unlock();
        }
    }
}
```

Đây là **class hoàn chỉnh có thể biên dịch** trong `SimpleGate.java`. Một thread gọi `awaitOpen()` đợi tới khi biến `isOpen` được đặt thành true; thread khác gọi `open()`. Nếu `open()` chạy **trước** `awaitOpen()`, waiter nhìn thấy predicate true dưới lock và **không cần chờ**, nên không bỏ lỡ trạng thái.

Điểm cốt lõi của đoạn code:

- `lock` bảo vệ **mọi** lần đọc/ghi `isOpen`.
- `opened` là Condition được tạo **từ chính `lock`**.
- `await()` nhả lock trong lúc chờ và lấy lại lock trước khi trả về.
- `signalAll()` báo các waiter có thể kiểm tra lại predicate; nó không tự giải phóng lock của thread gọi.
- `finally { lock.unlock(); }` bảo đảm nhả lock dù đang xử lý exception hay return.

**Một lock có thể tạo nhiều Condition khác nhau.** Ví dụ buffer có một Condition cho “không rỗng” và một Condition cho “chưa đầy”. Đây là khác biệt quan trọng so với một wait set monitor qua `Object.wait()/notifyAll()`.

## 3. Tách ba khái niệm: lock, predicate, condition queue

Giả sử có buffer một ô:

- **Lock:** bảo vệ `item` và các thao tác thay đổi `item`.
- **Predicate notEmpty:** `item != null`; consumer cần predicate này true trước khi lấy item.
- **Predicate notFull:** `item == null`; producer cần predicate này true trước khi đặt item.
- **Condition notEmpty:** nơi consumer có thể ngủ khi buffer rỗng.
- **Condition notFull:** nơi producer có thể ngủ khi buffer đầy.

Hai predicate là hai trạng thái logic đối nghịch; hai Condition là **hai hàng đợi waiter** để thông báo đúng nhóm. Bất kỳ một Condition nào cũng **không tự biết** `item` có null hay không; programmer phải sửa state rồi thông báo.

Một số lỗi đến từ việc nhầm chúng:

- Chỉ gọi `notEmpty.signal()` mà **chưa ghi item**: consumer thức dậy vẫn thấy buffer rỗng.
- Đặt item xong nhưng **không signal** khi producer/consumer liên quan đang chờ: waiter có thể ngủ vô hạn dù predicate đúng.
- Đưa consumer vào `notFull.await()`: consumer đang chờ sai event, có thể không tiến triển.
- Dùng `if` thay `while`: sau khi thức dậy, điều kiện có thể đã bị thread khác làm sai lại.
- Thay đổi `item` ngoài `lock`: predicate và signal không còn là một giao thức đồng bộ nhất quán.

## 4. `await()` làm gì với lock?

Đối với Condition do `ReentrantLock.newCondition()` tạo, thread phải **đang sở hữu ReentrantLock** trước khi gọi `await()`. Nếu không giữ lock, nó ném `IllegalMonitorStateException`.

Quá trình thường diễn ra:

```text
Thread consumer                         Thread producer
------------------------------------    ------------------------------------
lock.lock()
kiểm tra predicate: false
condition.await():
    đưa mình vào condition wait queue
    nhả lock
    chờ signal/interrupt/spurious wakeup
                                        lock.lock()
                                        cập nhật state
                                        condition.signal()
                                        ... vẫn đang giữ lock ...
                                        lock.unlock()
    cạnh tranh lấy lại lock
    lock đã được lấy lại
kiểm tra predicate bằng while
xử lý dữ liệu khi predicate true
lock.unlock()
```

**`await()` nhả lock gắn với Condition, chứ không tự nhả mọi khóa khác thread đang giữ.** Với `ReentrantLock` reentrant, implementation của `newCondition()` lưu trạng thái số lần giữ lock, nhả đầy đủ trước khi chờ, rồi khôi phục mức sở hữu khi reacquire. Nếu thread đang giữ cả lock A và B, `conditionOfA.await()` không tự giải phóng B; giữ B trong lúc chờ có thể tạo deadlock.

Khi `await()` trả về bình thường, thread **đang giữ lại lock** và chỉ khi đó mới chạy tiếp lệnh sau `await()`. Nếu bị interrupt, với `ReentrantLock.ConditionObject`, thread cũng phải giành lại lock trước khi `await()` ném `InterruptedException` theo hợp đồng triển khai. Bởi vậy, kết cấu `try/finally` bên ngoài `await()` nhả đúng lock và không tạo đường đi làm quên unlock.

### Đừng hiểu sai `WAITING` và `BLOCKED`

Trong thread dump, một thread đang chờ `Condition.await()` trên `ReentrantLock` thường được quan sát `WAITING (parking)`, hoặc `TIMED_WAITING` với phiên bản có timeout. Nó không nhất thiết là `Thread.State.BLOCKED`, vì `BLOCKED` dành cho việc đợi **intrinsic monitor**. Sau khi signal, waiter có thể còn chờ giành lại `ReentrantLock`; không hứa nó lập tức chạy.

## 5. `signal()` có ý nghĩa gì?

`condition.signal()` thông báo **một waiter** đang chờ trên Condition tương ứng. Với Condition của `ReentrantLock`, một waiter đủ điều kiện được chuyển từ condition queue để **tranh lấy lại associated lock**.

Ví dụ:

```java
lock.lock();
try {
    queue.add(item);       // predicate !queue.isEmpty() trở thành true
    notEmpty.signal();     // báo một consumer đang đợi
} finally {
    lock.unlock();         // waiter chỉ tiếp tục sau khi lấy lại lock
}
```

Đây là **đoạn minh họa trong method** có `lock`, `queue`, `item` và `notEmpty` được khai báo bên ngoài.

Ba điều phải nhớ:

1. **Signal không có nghĩa giao quyền sở hữu lock ngay.** Thread gọi `signal()` vẫn giữ lock đến khi `unlock()`; waiter phải cạnh tranh reacquire sau đó.
2. **Signal không phải “đặt predicate thành true”.** Trạng thái đã được thay đổi bởi `queue.add(item)`; signal chỉ thông báo khả năng có tiến triển.
3. **Signal không phải permit lưu lại vô hạn.** Nếu chưa có waiter, gọi `signal()` không tạo “một lượt đánh thức” được nhớ cho waiter xuất hiện sau. Vì vậy waiter phải luôn **kiểm tra predicate trước**, tránh lost signal.

Với `ReentrantLock`, ConditionObject có chính sách signal theo thứ tự FIFO của những waiter trên condition queue; **thứ tự thật sự giành lại lock** vẫn phụ thuộc chính sách lock và các thread tranh khóa khác. Với interface `Condition` nói chung, không giả định mọi implementation có cùng ordering.

## 6. `signalAll()` có ý nghĩa gì?

`condition.signalAll()` thông báo **tất cả các waiter hiện có** trên **Condition đó**; mỗi waiter vẫn phải lấy lại associated lock rồi tự kiểm tra predicate.

`signalAll()` thích hợp khi:

- Thay đổi trạng thái có thể cho **nhiều waiter** cùng tiến triển, ví dụ một gate mở vĩnh viễn cho tất cả.
- Nhiều waiter đang đợi các điều kiện chi tiết khác nhau trên **cùng Condition**, không biết nên chọn waiter nào cho chính xác.
- Có chuyển trạng thái quan trọng như shutdown/cancel khiến **mọi waiter** phải thức dậy để kiểm tra một cờ chung rồi thoát.
- Bạn không thể chứng minh việc signal một waiter bất kỳ sẽ bảo đảm tiến triển của hệ thống.

Nhược điểm là **thundering herd**: nhiều waiter được đưa vào tranh cùng lock, nhưng chỉ lần lượt một thread có thể lấy lock; nhiều waiter tỉnh dậy rồi nhận predicate false và chờ lại. Nó có thể làm tăng context switching, lock contention và latency.

`signalAll()` **không tự biến một item thành N item**. Nếu chỉ thêm một phần tử vào queue, dù có đánh thức N consumer, chỉ một consumer lấy được phần tử; những người khác phải trở về `await()` qua vòng `while`.

Bảng so sánh:

| API | Đánh thức ai? | Có làm predicate đúng? | Có nhả lock của caller ngay? |
|---|---|---|---|
| `signal()` | Một waiter trên Condition tương ứng (nếu có) | Không | Không |
| `signalAll()` | Tất cả waiter của Condition tương ứng (nếu có) | Không | Không |
| `await()` | Không đánh thức waiter khác; thread hiện tại đi chờ | Không | **Có**: tạm nhả associated lock rồi reacquire trước khi trở về |
| `unlock()` | Nhả quyền sở hữu lock; có thể cho contender giành lock | Không | Có: nhả lock theo contract |

**Không có số waiter cố định nào đủ để kết luận** nên dùng `signalAll()`. Cần nhìn predicate và invariant: một update cho phép đúng một waiter tiến triển hay cho phép nhiều waiter tiến triển? Có nhiều loại waiter chung Condition không? Có shutdown hay state chuyển vĩnh viễn không?

## 7. Vì sao bắt buộc dùng `while`?

Mẫu chuẩn:

```java
lock.lock();
try {
    while (!predicate()) {
        condition.await();
    }
    performOperationWithPredicateTrue();
} finally {
    lock.unlock();
}
```

Đây là **khung method minh họa**, với `lock`, `condition`, `predicate()` và `performOperationWithPredicateTrue()` được định nghĩa ở ngữ cảnh khác, method phải khai báo/xử lý `InterruptedException`.

Có ít nhất bốn lý do cần kiểm tra lại:

1. **Spurious wakeup:** `await()` có thể trả về dù chưa có `signal()` tương ứng, theo hợp đồng `Condition`. Đây là hành vi được phép của các primitive chờ; không được dựa vào “không bao giờ xảy ra trên máy của tôi”.
2. **Cạnh tranh sau signal:** waiter được thông báo phải **reacquire lock**. Trong thời gian đó thread khác có thể giành lock và tiêu thụ dữ liệu trước; predicate trở lại false.
3. **SignalAll đánh thức nhiều waiter:** có thể chỉ một waiter thật sự đủ điều kiện và các waiter còn lại phải tiếp tục chờ.
4. **Thông báo không phải kết quả nghiệp vụ:** thay đổi state có thể không đủ để thỏa tất cả điều kiện, hoặc waiter cần kiểm tra nhiều yếu tố hơn một “đã có sự kiện”.

Dùng `if (!predicate) await()` là lỗi vì sau một lần await trả về thread có thể bước tiếp khi predicate false, gây đọc queue rỗng, ghi quá capacity hoặc vi phạm invariant.

**Cũng không được gọi `await()` vô điều kiện rồi mới kiểm tra predicate**: có thể một producer đã hoàn tất công việc và signal **trước khi** waiter bắt đầu chờ; nếu waiter vẫn đi vào await mặc dù dữ liệu đã có, có thể ngủ mãi khi không còn signal nào tiếp theo. Predicate là **nguồn sự thật**.

## 8. Producer–consumer với hai Condition: `notEmpty` và `notFull`

Một bounded buffer có hai loại thread cần chờ **hai nguyên nhân khác nhau**:

- Producer muốn đặt item khi buffer đầy ⇒ đợi `notFull`.
- Consumer muốn lấy item khi buffer rỗng ⇒ đợi `notEmpty`.

Dùng **một lock** để bảo vệ cùng state và **hai Condition** để tránh đánh thức nhầm nhóm.

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// Đặt trong class BoundedBuffer<T> với capacity > 0.
private final ReentrantLock lock = new ReentrantLock();
private final Condition notEmpty = lock.newCondition();
private final Condition notFull = lock.newCondition();
private final Deque<T> queue = new ArrayDeque<>();
private final int capacity;

void put(T item) throws InterruptedException {
    lock.lockInterruptibly();
    try {
        while (queue.size() == capacity) {
            notFull.await();
        }
        queue.addLast(item);
        notEmpty.signal();
    } finally {
        lock.unlock();
    }
}

T take() throws InterruptedException {
    lock.lockInterruptibly();
    try {
        while (queue.isEmpty()) {
            notEmpty.await();
        }
        T item = queue.removeFirst();
        notFull.signal();
        return item;
    } finally {
        lock.unlock();
    }
}
```

Đây là **đoạn code thành viên của một generic class**, **không phải chương trình độc lập**: cần constructor khởi tạo `capacity` và quy định `item != null` nếu chọn `ArrayDeque`, vì nó không chấp nhận null.

Suy luận từng thao tác:

- Khi queue đầy, producer có lock nhưng không thể thêm; `notFull.await()` tạm **nhả lock**, để consumer có thể vào `take()` và lấy bớt item.
- Khi consumer lấy một item, trạng thái “còn chỗ” có thể đúng nên gọi `notFull.signal()`.
- Khi queue rỗng, consumer gọi `notEmpty.await()` và nhả lock; producer có thể đặt item.
- Khi producer đặt một item, trạng thái “có item” có thể đúng nên gọi `notEmpty.signal()`.
- Mỗi bước kiểm tra predicate, thay đổi queue và gửi signal đều nằm dưới **cùng `lock`**, tránh race giữa kiểm tra và chờ.
- `signal()` ở đây thông thường hợp lý vì mỗi lần `put/take` thay đổi khả năng tiến triển cho ít nhất một waiter ở nhóm đối diện; khi thiết kế thay đổi nhiều item, shutdown hoặc có predicate phức tạp, quyết định signal vs signalAll phải đánh giá lại.

Trong ứng dụng Java SE, nếu chỉ cần bounded producer-consumer queue, **`ArrayBlockingQueue` hoặc `LinkedBlockingQueue`** thường thích hợp và đã được kiểm chứng rộng rãi. Tự xây buffer giúp hiểu Condition, nhưng cũng mở thêm lỗi liên quan timeout, cancellation, capacity, shutdown và exception safety.

## 9. Ví dụ Java 17+ chạy độc lập: await, signal và signalAll

Chương trình này minh họa hai kiểu phối hợp:

- **OneSlot** là buffer một ô. `put` chờ `notFull` khi ô đang có item, `take` chờ `notEmpty` khi ô trống; mỗi phía dùng `signal()` để thông báo phía đối diện.
- **Gate** có predicate `isOpen`. Khi mở, tất cả waiter đều có thể đi tiếp, nên `signalAll()` hợp lý.
- `Future.get()` giúp main chờ kết quả, tránh dựa vào `Thread.sleep()` để đoán worker đã hoàn thành.

Tạo tệp **`ConditionLab.java`**:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ConditionLab {
    static final class OneSlot {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();
        private final Condition notFull = lock.newCondition();
        private String item;

        void put(String value) throws InterruptedException {
            if (value == null) throw new IllegalArgumentException("null not allowed");
            lock.lockInterruptibly();
            try {
                while (item != null) notFull.await();
                item = value;
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        String take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (item == null) notEmpty.await();
                String result = item;
                item = null;
                notFull.signal();
                return result;
            } finally {
                lock.unlock();
            }
        }
    }

    static final class Gate {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition opened = lock.newCondition();
        private boolean isOpen;

        void awaitOpen() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (!isOpen) opened.await();
            } finally {
                lock.unlock();
            }
        }

        void open() {
            lock.lock();
            try {
                isOpen = true;
                opened.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            OneSlot slot = new OneSlot();
            Future<String> received =
                pool.submit(() -> slot.take() + "," + slot.take());
            slot.put("alpha");
            slot.put("beta");
            System.out.println("received=" + received.get());

            Gate gate = new Gate();
            Future<Boolean> first = pool.submit(() -> {
                gate.awaitOpen();
                return true;
            });
            Future<Boolean> second = pool.submit(() -> {
                gate.awaitOpen();
                return true;
            });
            gate.open();
            System.out.println("gate=" + first.get() + "," + second.get());
        } finally {
            pool.shutdown();
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa source:

```bash
javac --release 17 ConditionLab.java
java ConditionLab
```

**Stdout đã xác minh:**

```text
received=alpha,beta
gate=true,true
```

### Giải thích tính đúng đắn của ví dụ

1. `OneSlot.item` được đọc/ghi chỉ khi đang giữ `OneSlot.lock`. `null` đại diện ô trống; input null bị từ chối để không nhầm với trạng thái “empty”.
2. Hai lệnh `put("alpha")` và `put("beta")` được main gọi nối tiếp. Nếu slot còn đầy khi put thứ hai bắt đầu, main chờ `notFull.await()`, nhả lock cho consumer lấy `alpha` rồi signal. Thứ tự dữ liệu nhận luôn là `alpha,beta`.
3. Consumer nhận bằng hai lần `take()`. Nếu chạy trước producer, consumer đợi `notEmpty`. Nếu producer chạy trước, consumer kiểm tra predicate rồi lấy ngay. Không phụ thuộc một signal đã xảy ra trước khi có waiter.
4. `received.get()` bảo đảm consumer task đã hoàn thành và trả chuỗi; do vậy dòng đầu luôn `received=alpha,beta` khi chương trình hoàn thành bình thường.
5. Hai task ở phần Gate có thể đi vào `awaitOpen()` trước hoặc sau khi `gate.open()`. Nếu đợi trước, `signalAll()` thông báo cả hai. Nếu vào sau, chúng quan sát `isOpen == true` và **không cần đợi**.
6. `Gate.opened.signalAll()` không làm cả hai waiter chạy song song trong critical section; mỗi waiter phải lấy lại lock trước khi return từ `await()`. Sau đó từng task trả `true`.
7. `first.get()`, `second.get()` tạo ranh giới hoàn tất trước khi main in dòng hai, nên kết quả được quyết định bởi predicate, không do lịch CPU.

**Phạm vi demo:** đây là ví dụ educational, không triển khai close/shutdown semantics cho buffer, timeout công việc, telemetry hoặc error propagation theo application contract. Pool gồm hai worker để có thể cho hai Gate waiters chạy cùng lúc; việc gate mở trước lúc cả hai thật sự await vẫn đúng, nhưng một lần chạy như vậy **không chứng minh** cả hai đã nằm trong condition queue khi `signalAll()` được gọi. Để kiểm thử trạng thái “đang chờ” chính xác hơn, cần orchestration và instrumentation thích hợp; correctness của Gate phải dựa trên predicate/lock protocol, không dựa vào giả định về scheduler.

`Future.get()` có thể ném `ExecutionException` nếu task gặp lỗi hoặc `InterruptedException` nếu caller bị interrupt; `main` trong demo khai báo `throws Exception` để tập trung vào cơ chế Condition. Ở ứng dụng thực, cần xử lý lỗi, hủy tác vụ, deadline và shutdown dứt điểm.

## 10. Signal và signalAll không thay thế state

Một **lost signal** thường xảy ra khi code tưởng rằng gọi signal là đủ, mặc dù chưa có predicate:

```java
// Đây là minh họa SAI, không phải giao thức chờ đầy đủ.
lock.lock();
try {
    condition.signal(); // không có waiter -> signal không được tích lũy
} finally {
    lock.unlock();
}

// Rất lâu sau: một thread khác gọi condition.await()
// không có predicate đã đúng để nó bỏ qua việc chờ.
```

Đây là đoạn mô tả lỗi; `lock`/`condition` được khai báo ở nơi khác và `await` phải được thực hiện khi sở hữu associated lock. Sửa bằng cách duy trì **trạng thái bền vững của điều kiện**:

```java
// Các field trong một class:
private boolean ready;
private final ReentrantLock lock = new ReentrantLock();
private final Condition changed = lock.newCondition();

void publishReady() {
    lock.lock();
    try {
        ready = true;
        changed.signalAll();
    } finally {
        lock.unlock();
    }
}

void waitUntilReady() throws InterruptedException {
    lock.lockInterruptibly();
    try {
        while (!ready) {
            changed.await();
        }
    } finally {
        lock.unlock();
    }
}
```

Các phần trên là **thành viên minh họa của một class** có import `Condition`/`ReentrantLock`. Nếu signal xảy ra trước, `ready` vẫn true nên waiter tới muộn đi tiếp. Nếu waiter đến trước, nó đợi rồi kiểm tra lại `ready` sau khi được thông báo. Đây là giao thức logic, không cần signal tồn tại như permit.

**Nếu cần một permit được lưu để thread đến sau có thể tiêu thụ**, primitive như `Semaphore` có mô hình khác. `Condition` không phải semaphore hay event counter; chọn primitive theo semantics cần thiết.

## 11. Interruptible waiting: cách xử lý hủy công việc

`Condition.await()` là phương thức **có thể bị interrupt** và khai báo `throws InterruptedException`. Với `ReentrantLock.newCondition()`, khi interrupt xảy ra trong thời gian chờ, implementation có thể thoát chờ bằng `InterruptedException` và reacquire lock trước khi ném exception. Theo JavaDoc, khi exception này được ném do interrupt, **interrupt status được xóa**; caller cần quyết định propagate, hủy công việc hoặc khôi phục cờ.

Đoạn mẫu thường dùng nếu method cho phép caller xử lý:

```java
void awaitReady() throws InterruptedException {
    lock.lockInterruptibly();
    try {
        while (!ready) {
            readyCondition.await();
        }
        useReadyState();
    } finally {
        lock.unlock();
    }
}
```

Đây là **method minh họa** cần các field `lock`, `ready`, `readyCondition` và method `useReadyState()` được khai báo ở class chứa nó.

- `lock.lockInterruptibly()` cho phép cả pha **đang đợi giành lock** bị hủy bằng interrupt.
- `await()` cho phép pha **đang chờ predicate** bị interrupt.
- `finally` bảo đảm release sau khi lock đã lấy; đặt `lockInterruptibly()` **trước** `try` giúp tránh unlock khi acquisition thất bại và thread chưa sở hữu lock.
- Nếu caller nhận `InterruptedException`, nó có thể propagate lên cấp cao hoặc dừng task theo chính sách ứng dụng.

Nếu code ở cuối pipeline buộc phải catch mà không thể rethrow:

```java
try {
    awaitReady();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // khôi phục flag
    return;                              // method void, dừng xử lý
}
```

Đây là **đoạn minh họa trong method void**, với `awaitReady()` là method có thể ném exception. Không được “nuốt” `InterruptedException` rồi tiếp tục vòng chờ vô hạn nếu ứng dụng mong muốn cancellation.

### `awaitUninterruptibly()`

`Condition.awaitUninterruptibly()` chờ mà không ném `InterruptedException`; interrupt có thể được ghi nhận và interrupt status được giữ/khôi phục khi trả về theo semantics API. Nó **vẫn có thể thức dậy giả**, nên luôn dùng vòng kiểm tra predicate. Không nên mặc định chọn nó cho ứng dụng cần graceful shutdown/cancel vì sẽ khó yêu cầu một waiter dừng nhanh.

### Vì sao không thể coi interrupt là signal nghiệp vụ?

Interrupt là cơ chế phối hợp hủy/ngắt thread; signal là thông báo rằng predicate **có thể** đã thay đổi. Một waiter có thể bị interrupt khi đang chờ hoặc đang reacquire; contract của `Condition` cho phép implementation ưu tiên phản ứng interrupt trong một số cuộc đua với signal (đồng thời phải xử lý việc chuyển signal theo ràng buộc thích hợp). Đừng giả định cả hai sự kiện luôn được xử lý theo một thứ tự duy nhất do mã nguồn quy định. Hãy xây API rõ ràng về cancellation, trạng thái và retry.

## 12. Các biến thể timed await và deadline

`Condition` có nhiều overload cho những yêu cầu thời gian:

| API | Hành vi chính | Giá trị trả về |
|---|---|---|
| `await()` | Chờ có thể interrupt, không yêu cầu deadline | `void` |
| `awaitUninterruptibly()` | Chờ không ném InterruptedException | `void` |
| `await(long time, TimeUnit unit)` | Chờ tối đa thời lượng tương đối | `boolean`: thường `false` nếu hết thời gian trước signal |
| `awaitNanos(long nanosTimeout)` | Chờ theo nanoseconds, dùng cho vòng retry | `long`: ước lượng số nanoseconds còn lại; ≤ 0 nghĩa thời gian đã hết |
| `awaitUntil(Date deadline)` | Đợi đến mốc giờ tuyệt đối | `boolean`: false nếu deadline đã qua trước khi được thông báo |

Trong **mọi** timed await, predicate vẫn phải được kiểm tra trong `while`. Đừng suy ra predicate true chỉ vì `await(time, unit)` trả true: return có thể chịu tác động của signal/spurious wakeup, và thread khác có thể thay đổi predicate trong lúc waiter reacquire lock.

### Mẫu timeout dùng `awaitNanos()`

```java
import java.util.concurrent.TimeUnit;

// Method của class chứa lock, notEmpty, queue:
boolean awaitNotEmpty(long timeout, TimeUnit unit)
        throws InterruptedException {
    long remaining = unit.toNanos(timeout);
    lock.lockInterruptibly();
    try {
        while (queue.isEmpty()) {
            if (remaining <= 0L) return false;
            remaining = notEmpty.awaitNanos(remaining);
        }
        return true; // queue đang không rỗng khi còn giữ lock
    } finally {
        lock.unlock();
    }
}
```

Đây là **method minh họa trong class**; `lock` và `notEmpty` được tạo bởi cùng ReentrantLock, `queue` là collection được bảo vệ bởi lock đó. Kết quả true ở đây chỉ là snapshot predicate **trước khi nhả lock**; caller khác lấy lock về sau có thể thấy queue đã rỗng, vì thế nếu muốn lấy item, thường phải **thực hiện việc lấy ngay trong cùng critical section** thay vì trả boolean rồi gọi một method khác.

Quan trọng:

- Dùng giá trị `remaining` do `awaitNanos()` trả về để tránh **reset toàn bộ timeout** sau mỗi spurious wakeup.
- Nếu dùng `await(time, unit)` bên trong vòng lặp mà lần nào cũng truyền đủ timeout, tổng thời gian chờ có thể kéo dài hơn deadline mong muốn.
- Với deadline tuyệt đối dựa trên `Date`/wall-clock, cần quan tâm clock adjustment; deadline tương đối đo khoảng thời gian thường phù hợp với monotonic `System.nanoTime()`.
- Timeout là **giới hạn yêu cầu chờ** chứ không phải cam kết thread được scheduler chạy chính xác tại mốc đó.
- Trong phiên bản timed của một bounded buffer, phải trả về “không thêm được/không lấy được” theo contract khi timeout, nhưng **không** được thay đổi state nửa chừng.

## 13. Phân biệt `Condition` và `Object.wait/notify`

Hai cơ chế có ý tưởng tương đồng nhưng có **khóa gắn kèm và API khác nhau**:

| Thuộc tính | `Object.wait()/notify()/notifyAll()` | `Condition.await()/signal()/signalAll()` |
|---|---|---|
| Gắn với | Intrinsic monitor của object | Một `Lock`, thường là `ReentrantLock` |
| Cách sở hữu trước khi wait/signal | Phải đang trong `synchronized(object)` tương ứng | Với `ReentrantLock.newCondition()`, phải giữ associated lock |
| Chờ | `object.wait()` | `condition.await()` |
| Báo một waiter | `object.notify()` | `condition.signal()` |
| Báo mọi waiter | `object.notifyAll()` | `condition.signalAll()` |
| Phân nhóm waiter | Một wait set cho mỗi object monitor | Có thể tạo **nhiều Condition riêng** từ cùng một Lock |
| Khi chờ | Tạm nhả monitor của chính object đó | Tạm nhả lock gắn với Condition |
| Thức dậy | Phải reacquire monitor trước khi wait trả | Phải reacquire associated lock trước khi await trả |
| Interrupt/timeout | Có overload tương ứng | Có nhiều overload, `awaitNanos` và `awaitUninterruptibly` |
| Typical state | `WAITING (on object monitor)` | `WAITING (parking)` tùy JVM/implementation |

**Không hoán đổi lẫn nhau:** `condition.await()` không thay bằng `monitor.wait()` chỉ vì cùng nhìn thấy tên `lock`. Một `ReentrantLock` object không phải monitor đang được sở hữu khi bạn gọi `lock.lock()`; gọi `lock.wait()` khi không đồng bộ trên *intrinsic monitor* của object `lock` vẫn là sai.

`Condition` là interface, triển khai khác có thể quy định semantics bổ sung; bảng mô tả trường hợp chuẩn của `ReentrantLock.newCondition()`. Khi cần fairness/order cụ thể hoặc interrupt semantics chính xác, đọc JavaDoc của implementation.

## 14. Các lỗi phổ biến phải tránh

### 14.1. Gọi await/signal mà chưa giữ associated lock

```java
ReentrantLock lock = new ReentrantLock();
Condition changed = lock.newCondition();

// Sai với Condition do ReentrantLock tạo:
changed.signal();
changed.await();
```

Đoạn là **minh họa lỗi**, không phải chương trình độc lập; `await()` còn yêu cầu xử lý/khai báo `InterruptedException`. Với `ReentrantLock`, cả hai lời gọi khi không sở hữu lock sẽ ném `IllegalMonitorStateException`. Mẫu đúng:

```java
lock.lock();
try {
    changed.signalAll();
} finally {
    lock.unlock();
}
```

Đoạn trên giả định `lock`/`changed` đã tồn tại trong cùng method/class.

### 14.2. Gọi await trong `if`

```java
if (queue.isEmpty()) {
    notEmpty.await();
}
T item = queue.removeFirst(); // có thể rỗng sau spurious wakeup/reacquire
```

Đây là **đoạn mô tả sai** trong critical section của generic class có `queue`, `notEmpty`, `T`. Sửa bằng `while (queue.isEmpty()) notEmpty.await();`; với nhiều consumer, một signal hoặc signalAll không bảo đảm người đang chạy vẫn thấy item.

### 14.3. Signal trước khi cập nhật predicate

```java
// Minh họa một thứ tự dễ gây hiểu sai, nếu điều kiện phải từ false sang true:
lock.lock();
try {
    condition.signal();
    // đổi state sau đó nhưng vẫn giữ lock...
    ready = true;
} finally {
    lock.unlock();
}
```

Đây là **đoạn code minh họa**. Với cùng lock, waiter chưa reacquire được cho đến unlock, nên thứ tự này **không nhất thiết sai về correctness** nếu predicate cuối đã đúng và không có ngoại lệ/nhánh kết thúc bất thường. Tuy nhiên, cách viết rõ ràng và ít rủi ro hơn là **cập nhật predicate trước, signal sau**, vì nó gắn thông báo với sự thay đổi đã thực hiện và thuận lợi khi code lớn hơn. Lỗi thật sự là **không cập nhật predicate**, cập nhật ngoài khóa, hoặc có control flow khiến signal không đi kèm trạng thái hợp lệ.

### 14.4. Lạm dụng `signalAll()`

Một producer thêm đúng một item nhưng đánh thức hàng nghìn consumer: các consumer cùng cạnh tranh lock, phần lớn thấy queue rỗng rồi await lại. Có thể dùng `signal()` nếu có thể chứng minh thông báo một waiter đủ để tiến triển. Ngược lại, dùng `signal()` cho shutdown nhiều waiter mà tất cả phải thoát là thiếu; lúc này `signalAll()` và kiểm tra cờ shutdown cần thiết.

### 14.5. Nhiều Condition nhưng đợi/signal nhầm queue

Consumer đang đợi `notEmpty` nhưng producer chỉ signal `notFull` sẽ không đánh thức consumer. Đặt tên Condition theo **predicate waiter chờ**, không chỉ theo tên method gọi signal; viết test cho buffer rỗng/đầy, nhiều producer/consumer, interrupt và shutdown.

### 14.6. Quên `finally` hoặc unlock quá sớm

`ReentrantLock` không tự nhả khi method ném exception như một scope `synchronized`; phải viết `lock.lock(); try { ... } finally { lock.unlock(); }`. Nhưng cũng không được `unlock()` nếu `lockInterruptibly()` hoặc `tryLock` chưa lấy thành công. Chỉ thiết lập `try/finally` cho đoạn thực sự sở hữu lock.

### 14.7. Giữ thêm khóa khác trong lúc await

`conditionOfA.await()` nhả A nhưng không nhả B. Nếu thread khác cần B để báo điều kiện trên A, hai phía có thể deadlock. Tránh nested locks nếu không cần; nếu cần, quy ước thứ tự lấy và nhả rõ ràng, không đợi event trong lúc giữ các tài nguyên làm event không thể xảy ra.

### 14.8. Sử dụng `Thread.sleep()` làm chờ điều kiện

Sleep không nhả lock đang giữ, không tạo signal và không bảo đảm predicate sẽ đổi đúng trong khoảng thời gian chọn. Dùng `await`, queue, latch hoặc future phù hợp với ý nghĩa nghiệp vụ.

### 14.9. Đồng nhất signal với việc waiter đã hoàn thành

`signalAll()` chỉ đưa waiter tới cơ hội tiến triển. Nếu caller cần tất cả waiter **xử lý xong** trước khi đi tiếp, phải sử dụng `Future.get()`, `CountDownLatch`, `Phaser` hoặc completion protocol riêng, không được suy từ một lần signal.

## 15. Liveness, fairness và hiệu năng

**Mutual exclusion** đảm bảo chỉ một thread giữ ReentrantLock tại một thời điểm. Nó không bảo đảm thread cụ thể sẽ giành lock sau signal trong một thời gian cố định.

- Với **non-fair ReentrantLock** (mặc định), thread mới tới có thể giành lock trước một waiter đã được signal. Chọn fair lock có thể giúp ưu tiên những thread chờ lâu trong cơ chế khóa có contention, nhưng không bảo đảm scheduler fairness tuyệt đối; `tryLock()` không timeout có semantics barging cần chú ý.
- Với `ReentrantLock.ConditionObject`, signal chuyển waiter từ condition queue sang lock queue, nhưng **waiter chưa chạy được** cho tới khi reacquire thành công.
- `signalAll()` có thể tạo thundering herd, tăng lock contention.
- Nếu predicate không bao giờ có thể đúng, `await()` có thể chờ mãi dù không có data race. Cần thiết kế shutdown, timeout và error propagation.
- Nếu thread đang sở hữu lock gọi I/O chậm hoặc xử lý CPU dài sau `signal()`, waiter phải chờ khóa lâu hơn. Nên giữ critical section đủ nhỏ để bảo vệ invariant, nhưng không tách những bước cần atomic.
- Với queue bounded, producer nhanh hơn consumer tạo backpressure và tăng wait time. Phân biệt chờ predicate có chủ đích với starvation do scheduler hoặc bottleneck ở lock.

Không phải bài toán nào cũng cần tự viết Condition. Với pipeline producer-consumer, `BlockingQueue` cung cấp giao thức chờ tiêu chuẩn, capacity và memory consistency đã được đóng gói. Với “một lần mở gate”, `CountDownLatch` có thể phù hợp hơn. Với số permits, `Semaphore` phù hợp hơn. `Condition` đặc biệt hữu ích khi bạn **đã có shared invariant dưới Lock** và cần những nhóm chờ phụ thuộc predicate.

## 16. Chẩn đoán bằng thread dump và JFR

Khi chương trình bị treo hoặc có độ trễ lớn, cần tìm:

1. Thread nào **giữ ReentrantLock**, thread nào đang đợi Condition nào?
2. Predicate nào còn false? Ai có trách nhiệm làm nó true?
3. Thread đổi predicate có **signal đúng Condition** không?
4. Waiter có giữ thêm lock khác, khiến producer không tiến triển được không?
5. Có shutdown/cancel mà không signalAll để wake những thread chờ dài hạn không?
6. Có timeout bị reset sau mỗi wakeup không?
7. Có exception khiến thread quên unlock hoặc mất signal cần thiết không?

Có thể lấy một số thread dump:

```bash
jcmd <PID> Thread.print -l
```

Thay `<PID>` bằng PID Java process đang chạy. Với `Condition.await()` trên `ReentrantLock`, stack có thể đi qua `AbstractQueuedSynchronizer.ConditionObject.await` và `LockSupport.park`. Đó thường là `WAITING (parking)`, không phải `WAITING (on object monitor)` của `Object.wait()`.

JFR (khi JVM hỗ trợ) có thể cung cấp sự kiện `jdk.ThreadPark` và thông tin về thời gian/stack thread đợi:

```bash
jcmd <PID> JFR.start name=condition-profile settings=profile duration=60s filename=conditions.jfr
jfr summary conditions.jfr
jfr print --events jdk.ThreadPark conditions.jfr
```

Không có event không nhất thiết chứng minh không có chờ: recording quá ngắn, event threshold, profiling settings hoặc workload không kích hoạt có thể làm không ghi nhận được. Thread dump/JFR là bằng chứng quan sát, **không thay thế kiểm chứng giao thức predicate và lock**.

## 17. Checklist review code dùng Condition

Khi kiểm tra một implementation dùng `Condition`, nên trả lời rõ:

1. **Lock được chia sẻ thế nào?** Các reader/writer của state có dùng đúng một ReentrantLock?
2. **Predicate là gì?** Có phát biểu bằng một biểu thức logic kiểm tra dưới lock hay không?
3. **Waiter luôn dùng while?** Có kiểm tra lại sau signal, spurious wakeup và cạnh tranh không?
4. **await được gọi trong khi sở hữu lock?** Associated lock có được tạm nhả trong khi chờ không?
5. **State được cập nhật trước signal?** Tất cả lần đọc/ghi predicate có nằm trong cùng critical section?
6. **signal hay signalAll?** Việc thức dậy một/nhiều waiter có phù hợp với thay đổi state và yêu cầu shutdown?
7. **Unlock luôn ở finally?** Có đường exception/cancel bỏ quên unlock hoặc unlock khi chưa acquire không?
8. **Interrupt policy?** API propagate `InterruptedException`, restore flag hay hỗ trợ cancellation thế nào?
9. **Timeout có deadline đúng?** Spurious wakeup có khiến thời gian chờ bị kéo dài không?
10. **Có lock khác đang giữ?** Chờ condition có thể chặn thread chịu trách nhiệm làm predicate true?
11. **Hệ thống kiểm tra completion riêng?** Signal không được dùng thay thế `Future.get()` hoặc latch khi cần xác nhận công việc hoàn tất.
12. **Có sẵn primitive cấp cao phù hợp hơn?** Không viết lại BlockingQueue/CountDownLatch nếu không cần custom predicate.
13. **Đã test race/close/interrupt?** Không chỉ test một lịch chạy thành công; stress với nhiều producer, consumer, timeout và shutdown.

## 18. Tự kiểm tra kiến thức và đáp án

**Câu 1.** Thread A gọi `condition.await()` khi đang giữ associated `ReentrantLock`. Nó còn giữ lock trong lúc chờ không?  
**Đáp án:** Không. Với Condition của ReentrantLock, await nhả lock tương ứng trong lúc chờ và reacquire trước khi trả về/hoàn tất qua đường interrupt.

**Câu 2.** Thread B gọi `signal()` rồi vẫn giữ lock thêm 5 giây. A có thể tiếp tục vào critical section ngay sau signal không?  
**Đáp án:** Không. A cần lấy lại lock. Trong lúc B chưa unlock, A chưa thể tiếp tục code được bảo vệ.

**Câu 3.** Producer signal trước khi consumer đến; có bị mất thông báo không?  
**Đáp án:** Signal không được giữ thành permit, nên thông báo đơn lẻ có thể không có waiter nhận. Giao thức đúng dựa trên **predicate được lưu**: consumer đến sau kiểm tra state true và không gọi await.

**Câu 4.** Vì sao `if (!ready) condition.await()` không an toàn?  
**Đáp án:** Await có thể trả sau spurious wakeup hoặc predicate đổi lại trước khi reacquire; dùng `while (!ready)`.

**Câu 5.** Khi thêm một item vào buffer rỗng, nên `signal()` hay `signalAll()`?  
**Đáp án:** Nếu consumer đều chờ cùng predicate “có item” và chỉ một item được thêm, `signal()` thường đủ; nhiều predicate cùng Condition, trạng thái mở rộng hoặc shutdown có thể đòi signalAll. Cần xét invariant và progress guarantee cụ thể.

**Câu 6.** `signalAll()` có làm tất cả waiter chạy đồng thời bên trong vùng khóa không?  
**Đáp án:** Không. Chúng cạnh tranh reacquire lock; chỉ một thread sở hữu lock tại một thời điểm.

**Câu 7.** `notFull.await()` có thể thay bằng `notEmpty.await()` trong producer được không?  
**Đáp án:** Không nếu hai Condition tương ứng với các nhóm waiter khác nhau. Producer phải chờ điều kiện “còn chỗ”, consumer mới chờ “có item”.

**Câu 8.** Khi có `InterruptedException` từ `await()`, có thể bỏ qua rồi tiếp tục đợi vô hạn không?  
**Đáp án:** Không nên nếu thread cần hỗ trợ hủy. Propagate exception, hủy task hoặc khôi phục interrupt flag nếu catch ở biên thích hợp.

**Câu 9.** Với vòng lặp timeout, tại sao cần cập nhật thời gian còn lại qua `awaitNanos()`?  
**Đáp án:** Vì có thể thức dậy nhiều lần mà predicate vẫn sai. Nếu reset full timeout mỗi lần, tổng thời gian có thể vượt deadline yêu cầu.

**Câu 10.** Thread giữ lock B rồi `conditionOfA.await()`; await có tự nhả B không?  
**Đáp án:** Không. Nó chỉ nhả associated lock A; B vẫn đang giữ, có thể khiến producer kẹt và gây deadlock.

**Câu 11.** `Condition` có tự giữ giá trị `ready` không?  
**Đáp án:** Không. `Condition` chỉ quản lý chờ/thông báo; boolean `ready` hoặc predicate khác phải được quản lý dưới lock.

**Câu 12.** Nếu tất cả chỉ cần đợi một sự kiện “hoàn tất một lần”, có bắt buộc dùng Condition không?  
**Đáp án:** Không. `CountDownLatch` thường diễn tả điều này rõ hơn. Condition hữu ích khi cần chờ các predicate tùy biến dưới cùng lock.

## 19. Tài liệu tham khảo

- [Java SE 17 — Condition](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Condition.html): API `await()`, `awaitNanos()`, `awaitUntil()`, `signal()`, `signalAll()`, spurious wakeup và interruption.
- [Java SE 17 — ReentrantLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): `newCondition()`, associated lock, fairness và ownership.
- [Java SE 17 — Lock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Lock.html): `lock()`, `lockInterruptibly()`, `unlock()` và memory synchronization.
- [Java SE 17 — BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): chờ producer-consumer, API bounded queue và memory consistency.
- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): one-shot gate và quy tắc chờ completion.
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): chờ kết quả task và memory consistency.
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Java Memory Model và happens-before.

**Ghi nhớ cuối cùng:** **predicate quyết định được đi tiếp hay không; lock bảo vệ predicate; `await()` nhả rồi lấy lại lock; `signal()`/`signalAll()` chỉ thông báo cơ hội kiểm tra lại.** Viết `while`, giữ đúng associated lock, cập nhật state trước khi signal, dùng `finally` để unlock và thiết kế cancellation/timeout rõ ràng. Khi những điều này được chứng minh, lịch chạy có thể không xác định mà chương trình vẫn đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-and-conditions-007">Vì sao await() luôn cần vòng lặp kiểm tra điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao await() luôn cần vòng lặp kiểm tra điều kiện?

Khi dùng **`ReentrantLock`** và **`Condition`** của `java.util.concurrent.locks`, một thread thường cần đợi một điều kiện nghiệp vụ: “hàng đợi có phần tử”, “đã mở cổng”, “còn dung lượng”, “đã có kết quả”, v.v. Phương thức **`Condition.await()`** giúp thread tạm nhả lock để thread khác có thể thay đổi trạng thái, sau đó được đánh thức và lấy lại lock để tiếp tục.

Nhưng **được đánh thức không đồng nghĩa điều kiện đang đợi chắc chắn đúng**. Vì vậy mẫu chuẩn là:

```java
lock.lock();
try {
    while (!conditionIsTrue()) {
        condition.await();
    }
    // Chỉ thực hiện khi predicate hiện tại đúng, dưới cùng lock.
} finally {
    lock.unlock();
}
```

Đây là trích đoạn minh họa, giả định `lock` là một `ReentrantLock`, `condition` là `Condition` được tạo bằng `lock.newCondition()`, còn `conditionIsTrue()` đọc trạng thái được bảo vệ bởi chính lock đó. Phương thức chứa đoạn mã phải khai báo hoặc xử lý `InterruptedException`.

**Vòng `while` không phải chi tiết trang trí.** Nó là một phần của giao thức đúng: kiểm tra điều kiện (*predicate*) → chờ khi chưa đúng → thức dậy → **kiểm tra lại** → chỉ hành động khi điều kiện đang đúng.

## 1. Condition.await() hoạt động thế nào?

`Condition` là cơ chế chờ theo điều kiện gắn với một `Lock`. Thông thường:

```java
ReentrantLock lock = new ReentrantLock();
Condition readyCondition = lock.newCondition();
```

`Condition.await()` vận hành theo logic:

1. Thread đang giữ **associated lock** khi gọi `await()`. Với `Condition` tạo từ `ReentrantLock`, gọi mà không giữ lock sẽ bị `IllegalMonitorStateException`.
2. `await()` **nhả lock** (bao gồm độ sâu giữ lock của ReentrantLock theo hợp đồng) và đưa thread vào hàng chờ điều kiện.
3. Thread khác lấy được lock, cập nhật trạng thái chung, rồi có thể gọi `signal()` hoặc `signalAll()`.
4. Waiter được phép thức dậy vì tín hiệu, interrupt hoặc *spurious wakeup*. Nó vẫn cần **lấy lại lock** trước khi `await()` trở về bình thường hoặc hoàn tất đường ném `InterruptedException`.
5. Sau khi có lock, waiter tiếp tục từ sau `await()`. Lúc này trạng thái chung có thể đã thay đổi thêm, nên nó phải kiểm tra lại predicate.

Sơ đồ:

```text
Thread consumer                       Thread producer
---------------                       ---------------
lock.lock()
while (queueEmpty) {
    notEmpty.await()
      | nhả lock
      v                                lock.lock()
   WAITING                             queue.add(item)
      ^                                notEmpty.signal()
      |                                lock.unlock()
      +---- thức dậy, tranh lock -----+
    lấy lại lock
    kiểm tra lại queueEmpty
}
remove item
lock.unlock()
```

**`signal()` không trao lock ngay lập tức.** Thread phát tín hiệu thường vẫn đang giữ lock đến khi `unlock()`. Waiter đã được đánh thức vẫn phải chờ đến lượt chiếm lock. Và `signal()` không tự biến điều kiện `queueEmpty == false` thành đúng: code thay đổi state mới có trách nhiệm đó.

## 2. Ba lý do phải dùng while thay vì if

### Lý do 1: Spurious wakeup — thức dậy không có tín hiệu mong đợi

Hợp đồng `Condition.await()` **cho phép thức dậy giả** (*spurious wakeup*). JVM/runtime có thể cho waiter trở về mà không có `signal()` trực tiếp từ thread khác như ứng dụng dự tính. Một phép đợi có timeout cũng có thể hết thời hạn trước khi predicate đúng.

Nếu viết:

```java
// KHÔNG an toàn với spurious wakeup.
if (queue.isEmpty()) {
    notEmpty.await();
}
return queue.remove();
```

thì waiter có thể thức dậy khi queue vẫn rỗng, và `remove()` ném exception hoặc phá giao thức. Chuyển thành:

```java
while (queue.isEmpty()) {
    notEmpty.await();
}
return queue.remove();
```

Hai trích đoạn giả định code đã giữ lock bảo vệ `queue` và condition `notEmpty`; chúng không phải chương trình độc lập. Mỗi lần thức dậy, `while` đọc **trạng thái hiện tại**, bất kể nguyên nhân thức dậy là gì.

### Lý do 2: Tín hiệu chỉ cho cơ hội kiểm tra, không dành sẵn tài nguyên

Giả sử hai consumer C1 và C2 đang đợi vì queue rỗng. Producer thêm **một** phần tử rồi gọi `signalAll()`.

- C1 và C2 đều được đánh thức và có thể tranh lock.
- C1 lấy lock trước, lấy phần tử cuối cùng, rồi mở khóa.
- C2 lấy lock sau. Queue **đã rỗng trở lại**.

Nếu C2 dùng `if`, nó đi thẳng qua câu lệnh sau `await()` mà **không kiểm tra lại** rồi cố lấy phần tử không tồn tại. Với `while`, C2 phát hiện queue rỗng và đợi lại.

Ngay cả với `signal()` (chỉ một waiter), một thread khác **không nằm trong hàng chờ** vẫn có thể lấy lock trước waiter được đánh thức và tiêu thụ tài nguyên. Vì vậy dùng `signal()` cũng **không cho phép bỏ `while`**.

### Lý do 3: Điều kiện có thể đổi trước khi waiter lấy lại lock

Producer có thể đặt `ready = true`, `signal()`, rồi có thread khác đặt `ready = false` **trước khi waiter lấy lại lock**. Tín hiệu chỉ phản ánh trạng thái tại một thời điểm cũ. Waiter phải quyết định từ **predicate đang có**, không dựa vào “mình vừa nhận signal”.

Quy tắc tổng quát: **notification = lời nhắc kiểm tra lại; predicate = nguồn sự thật**.

## 3. Tư duy đúng về predicate, lock và condition

| Thành phần | Trách nhiệm |
|---|---|
| **State/predicate** | Biểu thị điều kiện nghiệp vụ thật, ví dụ `!queue.isEmpty()`, `open`, `remaining > 0`. |
| **Lock** | Bảo vệ mọi đường đọc/ghi state liên quan và bảo đảm không bị chen giữa kiểm tra–đợi hoặc kiểm tra–thao tác. |
| **Condition** | Cung cấp chỗ chờ và tín hiệu cho những thread đang đợi predicate; nó **không lưu trữ sự thật nghiệp vụ**. |
| **await()** | Nhả associated lock, chờ, sau đó lấy lại lock trước khi trả về hoặc xử lý interrupt. |
| **signal()/signalAll()** | Cho waiter cơ hội được đánh thức; không bảo đảm predicate đúng hoặc waiter chạy ngay. |
| **while** | Kiểm tra lại invariant sau mọi đường thức dậy. |

Một giao thức cơ bản:

```java
// Bên chờ: dùng lock bảo vệ state.
lock.lock();
try {
    while (!ready) {
        readyCondition.await();
    }
    consumeReadyState();
} finally {
    lock.unlock();
}

// Bên phát tín hiệu: cùng lock và cùng state.
lock.lock();
try {
    ready = true;
    readyCondition.signalAll();
} finally {
    lock.unlock();
}
```

Đây là trích đoạn minh họa, cần các biến `lock`, `readyCondition`, `ready`, `consumeReadyState()` và xử lý `InterruptedException`. Nếu điều kiện là một sự kiện chỉ xảy ra một lần, predicate `ready` có thể giữ nguyên true. Nếu điều kiện liên quan tới một tài nguyên bị tiêu thụ, predicate phải được cập nhật sau mỗi lần lấy tài nguyên.

**Điểm chống lost signal:** kiểm tra điều kiện rồi gọi `await()` dưới **cùng lock**. Thread thay đổi điều kiện không thể chen vào khoảng giữa lúc waiter vừa kiểm tra false và lúc nó đã nhả lock để đợi. Nếu tín hiệu xảy ra trước khi waiter bắt đầu, waiter sẽ thấy predicate true khi nó có lock và **không đợi**. Không dùng `signal()` như một biến cờ tự động lưu tín hiệu.

## 4. Ví dụ 1: Condition Gate — tín hiệu trước hoặc sau waiter đều đúng

Tạo file **`ConditionGateDemo.java`**:

```java
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ConditionGateDemo {
    static final class Gate {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition opened = lock.newCondition();
        private boolean isOpen;

        void awaitOpen() throws InterruptedException {
            lock.lock();
            try {
                while (!isOpen) {
                    opened.await();
                }
            } finally {
                lock.unlock();
            }
        }

        void open() {
            lock.lock();
            try {
                isOpen = true;
                opened.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Gate gate = new Gate();

        Thread worker = new Thread(() -> {
            try {
                gate.awaitOpen();
                System.out.println("Worker entered");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "gate-worker");

        worker.start();
        gate.open();
        worker.join();
        System.out.println("Main finished");
    }
}
```

```bash
javac ConditionGateDemo.java
java ConditionGateDemo
```

Kết quả khi không có interrupt từ bên ngoài:

```text
Worker entered
Main finished
```

Có thể `gate.open()` chạy **trước** khi worker vào `awaitOpen()`: khi lấy được lock, worker thấy `isOpen = true` và bỏ qua `await()`. Nếu worker đã chờ, `signalAll()` đánh thức nó để kiểm tra lại trạng thái sau khi lấy lock. **Hai lịch đều đúng** nhờ lưu predicate và dùng `while`.

Các trường `isOpen` không cần thêm `volatile` trong giao thức này: tất cả truy cập đều dùng cùng `ReentrantLock`, và unlock/lock thiết lập quan hệ *happens-before* phù hợp theo Java Memory Model. `join()` giúp dòng `Main finished` xuất hiện sau khi worker đã hoàn thành.

## 5. Ví dụ 2: if sai một cách tái hiện được — signalAll cho hai waiter, một permit

Ví dụ sau **cố tình viết sai** với `if` để minh họa tại sao một tín hiệu không dành riêng tài nguyên cho waiter. Hai worker cùng chờ một permit; sau khi cả hai đã vào đường chờ, main phát một permit và đánh thức tất cả. Sai lầm là hai waiter cùng tiếp tục qua `if` mà không kiểm tra lại permit.

Tạo file **`BrokenIfAwaitDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BrokenIfAwaitDemo {
    static final class BrokenPermit {
        final ReentrantLock lock = new ReentrantLock();
        final Condition changed = lock.newCondition();
        final CountDownLatch bothArrived = new CountDownLatch(2);
        boolean available;

        void takeIncorrectly(AtomicInteger accepted)
                throws InterruptedException {
            lock.lock();
            try {
                if (!available) { // Sai: chỉ kiểm tra một lần.
                    bothArrived.countDown();
                    changed.await();
                }
                available = false;
                accepted.incrementAndGet();
            } finally {
                lock.unlock();
            }
        }

        void publishOnePermit() {
            lock.lock();
            try {
                available = true;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        BrokenPermit permits = new BrokenPermit();
        AtomicInteger accepted = new AtomicInteger();

        Runnable task = () -> {
            try {
                permits.takeIncorrectly(accepted);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start();
        b.start();

        permits.bothArrived.await();
        permits.publishOnePermit();
        a.join();
        b.join();

        System.out.println("Published permits: 1");
        System.out.println("Accepted requests: " + accepted.get());
    }
}
```

```bash
javac BrokenIfAwaitDemo.java
java BrokenIfAwaitDemo
```

Kết quả trong thực thi bình thường, không bị interrupt:

```text
Published permits: 1
Accepted requests: 2
```

`bothArrived` giảm đếm khi mỗi worker giữ lock và chuẩn bị chờ. Main chỉ phát permit sau khi cả hai đã đi tới điểm đó. Nếu worker thứ hai chưa nhả lock để `await()`, main vẫn phải chờ lấy lock trước khi `publishOnePermit()` có thể chạy; vậy khi main gọi `signalAll()`, hai worker đã ở đường đợi hoặc đã tự thức dậy theo quy tắc API. Với `if`, cả hai có thể đi qua câu lệnh sau await mà **không xác minh permit còn tồn tại**; kết quả hai yêu cầu được chấp nhận dù chỉ phát một permit.

**Cách sửa:** thay `if (!available)` bằng `while (!available)`, rồi đặt `available = false` dưới cùng lock. Khi đó chỉ một thread tiêu thụ permit; thread còn lại sẽ `await()` tiếp. Nhưng nếu trong ví dụ này chỉ có một permit **và không có lần phát/đóng tiếp theo**, thread còn lại sẽ đợi vô hạn và `join()` cũng không kết thúc. Muốn demo bản sửa đầy đủ, phải cung cấp thêm permit hoặc cơ chế đóng/hủy để đánh thức và cho waiter còn lại thoát. Ví dụ hàng đợi ở mục sau thể hiện giao thức đóng rõ ràng.

Ví dụ trên là **phản ví dụ cố ý không an toàn**, không được dùng như code sản xuất.

## 6. Ví dụ 3: một hàng đợi một slot dùng hai Condition

Trong producer–consumer, có hai predicate khác nhau:

- **`notEmpty`:** consumer được lấy khi slot có item, hoặc thoát nếu đã đóng.
- **`notFull`:** producer được đặt khi slot trống, hoặc dừng nếu đã đóng.

Dùng hai `Condition` gắn với **cùng một `ReentrantLock`** giúp phát tín hiệu đúng nhóm waiter và tránh đánh thức tất cả thread không liên quan. Mọi lần `await()` vẫn **cần vòng `while`**.

Tạo file **`OneSlotConditionQueue.java`**:

```java
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class OneSlotConditionQueue {
    static final class Channel {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();
        private final Condition notFull = lock.newCondition();
        private Integer value;
        private boolean closed;

        void put(int item) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (value != null && !closed) {
                    notFull.await();
                }
                if (closed) {
                    throw new IllegalStateException("channel closed");
                }
                value = item;
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        Integer take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (value == null && !closed) {
                    notEmpty.await();
                }
                if (value == null) {
                    return null; // Đã đóng và không còn item.
                }
                int result = value;
                value = null;
                notFull.signal();
                return result;
            } finally {
                lock.unlock();
            }
        }

        void close() {
            lock.lock();
            try {
                closed = true;
                notEmpty.signalAll();
                notFull.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Channel channel = new Channel();

        Thread producer = new Thread(() -> {
            try {
                for (int item = 1; item <= 3; item++) {
                    channel.put(item);
                }
                channel.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.close();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                Integer item;
                while ((item = channel.take()) != null) {
                    System.out.println("Received: " + item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        System.out.println("Finished");
    }
}
```

```bash
javac OneSlotConditionQueue.java
java OneSlotConditionQueue
```

Kết quả trong lần chạy bình thường, không bị interrupt:

```text
Received: 1
Received: 2
Received: 3
Finished
```

**Vì sao đúng?**

1. `value` và `closed` là state private chỉ được đọc/ghi dưới cùng `lock`.
2. Producer kiểm tra `value != null && !closed` trong `while`; nếu slot đầy, `notFull.await()` nhả lock để consumer lấy item.
3. Consumer kiểm tra `value == null && !closed` trong `while`; nếu slot rỗng, `notEmpty.await()` nhả lock để producer đặt item.
4. Khi producer đặt xong, nó `notEmpty.signal()`; khi consumer lấy xong, nó `notFull.signal()`. Vì có một producer và một consumer, signal một waiter phù hợp với phạm vi ví dụ.
5. `close()` đặt `closed = true` rồi `signalAll()` **cả hai condition queues**, vì các waiter phải kiểm tra lại việc đóng. Consumer vẫn lấy item còn trong slot trước khi nhận `null` báo hoàn tất.
6. `join()` bảo đảm chương trình in `Finished` sau khi cả hai thread kết thúc.

Nếu mở rộng nhiều producer và consumer, cần xác định rõ tính chất đóng, thứ tự, fairness, interrupt và xử lý lỗi; `signal()` đúng hay không phụ thuộc protocol. Đây là implementation tối giản để học về condition, không thay thế queue thực tế nhiều tính năng. Với ứng dụng thường gặp, **`ArrayBlockingQueue`** hoặc `LinkedBlockingQueue` đã quản lý locking/await/signal và có API `put()`/`take()` rõ ràng.

## 7. signal(), signalAll() không thay thế kiểm tra predicate

`Condition.signal()` thông báo cho **một** waiter trên condition queue tương ứng; `signalAll()` chuyển **tất cả** waiter đang chờ trên condition đó sang trạng thái có thể cạnh tranh lấy lock. Với `ReentrantLock` và Condition từ `newCondition()`, phải sở hữu lock khi `signal()`/`signalAll()`.

Phân biệt với `Object.notify()`/`notifyAll()`:

| `Object.wait()` | `Condition.await()` |
|---|---|
| Gắn với intrinsic monitor của một đối tượng. | Gắn với một `Lock` và đối tượng `Condition` do lock tạo. |
| Chờ bằng `synchronized(lockObj)` rồi `lockObj.wait()`. | Chờ bằng `lock.lock()` rồi `condition.await()`. |
| Phát tín hiệu `notify()`/`notifyAll()` trên cùng đối tượng monitor. | Phát tín hiệu `signal()`/`signalAll()` trên đúng `Condition`. |
| Một object có một wait set nội tại. | Một `ReentrantLock` có thể có nhiều condition queues (`notEmpty`, `notFull`). |
| Phải đợi trong `while` kiểm tra predicate. | **Cũng phải đợi trong `while` kiểm tra predicate.** |

Hai nhóm API không hoán đổi trực tiếp: `condition.signal()` không đánh thức thread đang `object.wait()`; `object.notify()` không đánh thức thread đang `condition.await()` trên một condition khác.

`signal()` **không tích lũy permit**: tín hiệu gửi khi không có waiter trên condition queue đó không được tự lưu lại để đánh thức một thread đến sau. Nếu giao thức cần nhớ có “còn 3 quyền tiếp tục”, hãy lưu một **counter state** hoặc cân nhắc `Semaphore`, không giả định 3 lần `signal()` tương đương 3 permit.

## 8. Interrupt: vòng lặp vẫn phải tuân theo đường hủy

`await()` là thao tác chờ có thể bị interrupt. Khi thread bị interrupt trong lúc đợi, nó có thể ném `InterruptedException` theo hợp đồng; cờ interrupt được xóa khi exception được ném. Trước khi hoàn tất đường ném exception, `Condition.await()` của `ReentrantLock` **lấy lại lock**, nên `finally { lock.unlock(); }` vẫn đúng.

Cách xử lý:

```java
lock.lock();
try {
    while (!ready) {
        condition.await();
    }
    // Trạng thái ready thật sự đúng.
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Kết thúc hợp tác hoặc chuyển trách nhiệm hủy.
} finally {
    lock.unlock();
}
```

Đây là trích đoạn minh họa, giả định các biến đã khai báo. Trong API cho phép ném `InterruptedException`, cách đơn giản hơn là **ném tiếp** để caller quyết định. Không nuốt exception rồi thực hiện hành động như thể điều kiện đã đúng.

Tạo tệp **`InterruptibleConditionDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class InterruptibleConditionDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Condition changed = lock.newCondition();
        CountDownLatch entered = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            lock.lock();
            try {
                entered.countDown();
                boolean ready = false;
                while (!ready) {
                    changed.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Waiting interrupted");
            } finally {
                lock.unlock();
            }
        }, "waiter");

        worker.start();
        entered.await();
        worker.interrupt();
        worker.join();

        System.out.println("Worker finished");
    }
}
```

```bash
javac InterruptibleConditionDemo.java
java InterruptibleConditionDemo
```

Kết quả:

```text
Waiting interrupted
Worker finished
```

`entered.countDown()` báo main rằng worker đã vào vùng lock; main có thể interrupt trước hoặc sau lời gọi `changed.await()`. Trường hợp trước, await phát hiện cờ interrupt theo hợp đồng và ném; trường hợp sau, await bị ngắt trong lúc chờ. Cả hai đều chạy nhánh catch, phục hồi cờ, mở khóa và cho worker kết thúc. `join()` đợi worker trước khi in dòng cuối. Đây là một ví dụ về **cancellation hợp tác**, không phải dùng `interrupt()` để cưỡng bức thay đổi predicate.

## 9. awaitUninterruptibly() có bỏ nhu cầu while không?

**Không.** `awaitUninterruptibly()` không ném `InterruptedException` khi đang chờ, nhưng vẫn có thể thức dậy giả hoặc được signal khi predicate chưa đúng. Nó vẫn phải nằm trong vòng `while`:

```java
lock.lock();
try {
    while (!ready) {
        condition.awaitUninterruptibly();
    }
} finally {
    lock.unlock();
}
```

Trích đoạn minh họa; `condition`, `lock`, `ready` đã được giả định khai báo. Khi được interrupt trong lúc đợi, `awaitUninterruptibly()` tiếp tục đợi thay vì trả về vì interrupt; trạng thái interrupt được bảo toàn theo hợp đồng. Hãy chọn API này **chỉ khi giao thức thực sự cần bỏ qua yêu cầu ngắt trong thời gian chờ**. Ứng dụng cần shutdown/cancel thường phù hợp hơn với `await()` có thể interrupt.

## 10. Timed await(): hết thời gian cũng không chứng minh điều kiện đúng

Các biến thể `Condition.awaitNanos(long nanosTimeout)`, `await(long time, TimeUnit unit)` và `awaitUntil(Date deadline)` cho phép chờ có thời hạn. Sau khi chúng trở về, chương trình vẫn phải **kiểm tra lại predicate**. Một lần timeout không có nghĩa điều kiện đã đúng; một lần được signal cũng không nghĩa điều kiện vẫn đúng.

Mẫu dùng `awaitNanos`:

```java
long remaining = timeoutNanos;
lock.lock();
try {
    while (!ready) {
        if (remaining <= 0L) {
            return false; // Hết thời hạn mà điều kiện chưa đúng.
        }
        remaining = condition.awaitNanos(remaining);
    }
    return true;
} finally {
    lock.unlock();
}
```

Đây là đoạn code **bên trong một method trả `boolean`** có thể ném `InterruptedException`, không phải chương trình Java độc lập. Các biến `lock`, `condition`, `ready`, `timeoutNanos` được giả định đã khai báo. `awaitNanos()` trả về thời lượng còn lại ước tính theo hợp đồng, có thể không dương nếu timeout. Việc đưa `remaining` trở lại vòng lặp bảo đảm tổng ngân sách chờ được quản lý thay vì đặt lại toàn bộ timeout sau mỗi lần thức dậy.

Đối với `await(time, unit)`, kết quả boolean **chỉ cho biết tình trạng chờ tín hiệu so với timeout**, không thay thế predicate. Nếu cần xử lý điều kiện trước/sau timeout một cách chính xác, thiết kế API nên ghi rõ “thành công khi predicate đúng” và kiểm tra dưới lock. Nếu cần timeout theo thời gian hệ thống, hãy chú ý clock changes; với đo khoảng, `System.nanoTime()` thường phù hợp hơn `currentTimeMillis()`.

## 11. Fair lock, signal order và starvation

`new ReentrantLock(true)` tạo fair lock theo chính sách của `ReentrantLock` cho việc cấp khóa, nhưng **không loại bỏ nhu cầu while**. Fairness không biến signal thành đặt chỗ cho tài nguyên, và không ngăn predicate đổi giữa lúc signal và lúc waiter có lock.

`signal()` có thể có quy tắc chọn waiter theo thứ tự trong implementation `ReentrantLock.ConditionObject`, nhưng thứ tự cuối cùng các thread **nhận được lock và thực hiện hành động** còn phụ thuộc tranh chấp và chính sách khóa. Không xây dựng correctness từ một giả định “waiter được signal đầu tiên sẽ là người xử lý dữ liệu đầu tiên” trừ khi contract API thực sự bảo đảm toàn bộ điều đó.

Nhiều waiter thức dậy rồi lại chờ có thể làm tăng contention hoặc *thundering herd*, nhất là khi dùng `signalAll()`. Cách tối ưu thường là sử dụng nhiều condition queues cho các predicate khác nhau, chọn `signal()` khi chứng minh được đủ và giữ vùng lock ngắn. Nhưng **tối ưu hiệu năng phải đến sau tính đúng đắn của predicate + while**.

## 12. Các lỗi hay gặp

| Lỗi | Hậu quả | Cách sửa |
|---|---|---|
| `if (!ready) await()` | Spurious wakeup hoặc thread khác lấy mất tài nguyên khiến hành động sai. | Dùng `while (!ready) await()`. |
| Gọi `await()` không sở hữu associated lock | `IllegalMonitorStateException` với Condition của ReentrantLock. | Giữ lock bằng `lock()`/`lockInterruptibly()`, mở trong `finally`. |
| Gọi `signal()` ngoài lock | `IllegalMonitorStateException` với Condition của ReentrantLock. | Cập nhật state và signal dưới cùng lock. |
| Cho rằng `signal()` đảm bảo predicate đúng | Predicate có thể đổi trước khi waiter lấy lại lock. | Kiểm tra lại trong while. |
| Dựa vào signal như trạng thái lưu trữ | Signal khi không có waiter bị bỏ qua. | Lưu boolean/counter/queue state; signal chỉ hỗ trợ thông báo. |
| Dùng `Thread.sleep()` trong khi giữ lock để chờ | Thread cần lock để thay đổi điều kiện không thể vào, gây nghẽn. | Dùng `await()` nhả lock theo hợp đồng. |
| Kiểm tra predicate ngoài lock rồi mới await trong lock | Race giữa kiểm tra và đợi, có thể lost signal. | Guard predicate và await trong cùng vùng lock. |
| Dùng `signalAll()` rồi `if` | Nhiều waiter vượt qua guard cũ và cùng tiêu thụ một tài nguyên. | Mỗi waiter phải while; cập nhật predicate sau tiêu thụ. |
| Nuốt `InterruptedException` | Mất tín hiệu hủy và có thể tiếp tục trái nghiệp vụ. | Ném tiếp hoặc khôi phục cờ và kết thúc hợp tác. |
| Reset timeout đầy đủ sau mỗi thức dậy | Có thể chờ lâu hơn ngân sách dự định. | Tính thời gian còn lại bằng `awaitNanos()`/deadline. |
| Gọi `signal()` rồi chờ waiter chạy trước khi unlock | Waiter không thể hoàn tất await trước khi chiếm lại lock. | Thoát vùng lock ngay sau khi cập nhật/tín hiệu khi hợp lý. |
| Chỉ đợi một predicate mà không có đường đóng/hủy | Thread có thể treo mãi nếu điều kiện không bao giờ đúng. | Thiết kế trạng thái `closed`, `signalAll()` và xử lý interrupt. |

## 13. Cách chứng minh một await-loop đúng

Khi review một class tự dùng `Condition`, hãy đặt các câu hỏi sau:

1. **Predicate là gì?** Ví dụ `!queue.isEmpty()`, `capacityAvailable`, `closed || ready`. Nó có phản ánh chính xác điều kiện để thực hiện hành động không?
2. **Ai có thể sửa predicate?** Tất cả đường truy cập liên quan có dùng cùng `ReentrantLock` không?
3. **Đã dùng `while` thay `if` chưa?** Có kiểm tra lại sau mọi wakeup không?
4. **Việc kiểm tra và await có chung lock không?** Nếu không, có khe lost-signal giữa check và wait.
5. **Hành động sau loop có làm predicate đổi không?** Nếu có, nó cũng phải nằm dưới lock và có tín hiệu tới waiter phù hợp.
6. **Trường hợp close/interrupt/timeout thì sao?** Loop có thể kết thúc hoặc hủy một cách có kiểm soát, không chờ vô hạn ngoài ý muốn.
7. **Signal chọn đúng condition queue chưa?** Với nhiều predicate, `notEmpty` và `notFull` cần được phân biệt để tránh đánh thức sai nhóm.
8. **Có nguy cơ deadlock không?** `await()` nhả associated lock nhưng không nhả những khóa khác thread đang giữ. Tránh giữ nhiều lock lồng nhau thiếu thứ tự.
9. **Có thể đơn giản hóa không?** Nếu chỉ cần queue, `BlockingQueue` thường dễ dùng đúng hơn; nếu chỉ cần tín hiệu one-shot, `CountDownLatch` có thể phù hợp; nếu cần permit, cân nhắc `Semaphore`.

## 14. Khi nào không cần tự viết await-loop?

`Condition` là công cụ thấp cấp cần thiết để thiết kế các cấu trúc đồng bộ hóa tùy biến, nhưng nhiều bài toán đã có abstraction cao hơn:

| Bài toán | Công cụ Java thường phù hợp |
|---|---|
| Producer–consumer có queue | `ArrayBlockingQueue`, `LinkedBlockingQueue`. |
| Một cổng mở một lần | `CountDownLatch`. |
| Chờ kết quả tác vụ | `Future.get()`, `CompletableFuture`. |
| Đếm tài nguyên có permit | `Semaphore`. |
| Nhiều pha cùng rendezvous | `CyclicBarrier`, `Phaser`. |
| Điều phối tùy biến với nhiều predicate | `ReentrantLock` + nhiều `Condition`; dùng `while` trong mọi waiter. |

Không nên tự viết queue nhiều thread nếu không cần: bên cạnh await-loop còn phải xử lý fairness, capacity, cancellation, shutdown, deadlock, thời hạn chờ và performance. Nhưng việc hiểu **vì sao while bắt buộc** giúp đọc, debug và sử dụng các concurrent abstractions đúng cách.

## 15. Câu hỏi tự kiểm tra

1. **`await()` được signal thì predicate có chắc đúng không?** Không. Thread khác có thể lấy tài nguyên trước hoặc predicate bị đổi; waiter phải kiểm tra lại.
2. **Spurious wakeup là gì?** Waiter có thể thức dậy dù không có tín hiệu tương ứng với điều kiện nghiệp vụ; được hợp đồng API cho phép.
3. **Tại sao không thể thay while bằng if nếu chỉ có một waiter?** Spurious wakeup và thay đổi predicate vẫn có thể xảy ra; không có bảo đảm tổng quát cho if.
4. **Tại sao check predicate phải cùng lock với await?** Để tránh thread khác thay đổi điều kiện và phát signal ở khe giữa check và wait.
5. **`signalAll()` có làm mọi waiter đủ điều kiện hành động không?** Không. Tất cả phải tranh lock và tự kiểm tra predicate; có thể chỉ một người được tiến triển.
6. **`signal()` có lưu một permit để thread tương lai sử dụng không?** Không; muốn lưu khả năng tiếp tục phải có state/counter, hoặc dùng `Semaphore`.
7. **`await()` có nhả lock liên quan không?** Có, theo hợp đồng của Condition/ReentrantLock, và phải lấy lại lock trước khi trở về. Nó không tự nhả các lock khác.
8. **Khi await bị interrupt và ném exception, có nên tiếp tục như điều kiện đúng không?** Không. Nên chuyển yêu cầu hủy cho caller hoặc khôi phục cờ và kết thúc theo protocol.
9. **Timed await kết thúc có đồng nghĩa predicate đúng không?** Không; phải kiểm tra lại predicate và ngân sách timeout.
10. **Đã dùng fair lock có cần while không?** Vẫn cần. Fairness không loại bỏ spurious wakeup và không đặt trước trạng thái tài nguyên cho waiter.

**Ghi nhớ:** `Condition.await()` chỉ nói *“hãy nhả lock và đợi cơ hội kiểm tra lại”*; không nói *“điều kiện giờ chắc chắn đúng”*. Vì thế mẫu đáng tin cậy luôn là **giữ đúng lock → `while (!predicate) await()` → thực hiện hành động dưới lock**, với xử lý timeout, interruption và tín hiệu phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-and-conditions-008">ReadWriteLock cho read-heavy workload</a>

<details>
<summary>Xem chi tiết</summary>

# ReadWriteLock cho read-heavy workload trong Java

> **Ý chính:** `ReadWriteLock` có hai chế độ khóa liên kết: **nhiều reader** có thể cùng giữ read lock, còn **writer** cần write lock độc quyền. Nó hữu ích cho một số workload đọc nhiều/ghi ít, nhưng **không có bảo đảm luôn nhanh hơn** `synchronized` hoặc `ReentrantLock`. Độ dài critical section, tỉ lệ đọc/ghi, contention, fairness và đặc tính dữ liệu quyết định.

Bài này thuộc **Locks & Conditions**, chỉ tập trung Java SE concurrency, dùng API Java 17+. Các vấn đề của Spring được dành cho chương riêng, không nằm trong phạm vi bài.

## 1. Vì sao cần read/write lock?

Giả sử một cấu trúc từ điển có hàng nghìn mục, nhiều thread đọc tìm kiếm và hiếm khi sửa. Dùng một monitor `synchronized(lock)` bảo vệ mọi thao tác thì dù chỉ đọc, các thread vẫn phải xếp hàng lần lượt. Với read/write lock, nhiều lần **đọc thuần túy** có thể cùng thực hiện, trong khi một lần ghi tạm chặn các reader/writer khác để cập nhật state nhất quán.

~~~text
Không có writer:
  Reader A -- READ lock --┐
  Reader B -- READ lock --+-- có thể cùng ở critical section
  Reader C -- READ lock --┘

Khi writer cần sửa:
  Writer -- WRITE lock ------- độc quyền khi đã lấy được
  Reader/Writer khác --------- đợi theo chính sách acquire
~~~

Đây là khả năng concurrent access về mặt hợp đồng khóa, **không bảo đảm CPU thực sự chạy các reader song song** hay writer được lấy khóa trước một deadline.

| Ai đang giữ khóa? | Reader mới | Writer mới |
| --- | --- | --- |
| Không ai | Có thể lấy read | Có thể lấy write |
| Một hoặc nhiều reader | Có thể cùng lấy read nếu chính sách chờ cho phép | Phải chờ các reader nhả |
| Một writer | Không được vào, trừ reentrant read của chính writer | Không được vào, trừ reentrant write của chính writer |

Đừng hiểu sai rằng “cứ thấy còn reader thì reader mới luôn được vào”: khi một writer đang chờ, fairness/acquisition policy có thể yêu cầu reader mới đợi để tránh writer bị đói.

## 2. Interface và implementation trong Java

`ReadWriteLock` là interface ở `java.util.concurrent.locks`. Mỗi instance trả về **một cặp liên kết** qua `readLock()` và `writeLock()`. `ReentrantReadWriteLock` là implementation Java SE thông dụng, hỗ trợ reentrancy và tùy chọn fairness.

~~~java
// Snippet minh họa cách khai báo; chưa phải chương trình độc lập.
ReadWriteLock rw = new ReentrantReadWriteLock();
Lock read = rw.readLock();
Lock write = rw.writeLock();
~~~

Hai critical section chuẩn:

~~~java
read.lock();
try {
    // Chỉ đọc dữ liệu chung và tạo snapshot nếu cần.
} finally {
    read.unlock();
}

write.lock();
try {
    // Kiểm tra invariant và cập nhật dữ liệu chung.
} finally {
    write.unlock();
}
~~~

Luôn dùng `try/finally` sau khi acquire thành công. `Lock` không có cấu trúc giải phóng tự động khi ra khỏi một block như `synchronized`. Khi có exception hoặc `return` sớm, `finally` vẫn giải phóng lock. Nếu gọi `unlock()` từ thread không sở hữu lock, hoặc unlock nhiều hơn số hold hợp lệ, sẽ gây lỗi.

**Mọi reader/writer bảo vệ cùng state phải dùng cùng instance `rw`.** Một thread lấy `rwA.readLock()` và thread khác lấy `rwB.writeLock()` thì hai khóa không có quan hệ loại trừ, bất kể tên biến giống nhau.

### Cơ chế happens-before

`ReadWriteLock` yêu cầu hiệu ứng memory synchronization của write lock cũng áp dụng với read lock liên kết. Sau khi một writer cập nhật dữ liệu và nhả write lock, các reader lấy read lock theo sau có cơ sở quan sát những cập nhật đó theo Java Memory Model. Vì vậy một `int` thường có thể được đọc/ghi đúng dưới protocol khóa này; không cần thêm `volatile` cho từng field nếu tất cả truy cập cạnh tranh đều tuân theo protocol.

Một đường ghi không khóa hoặc một reader đọc trực tiếp ngoài read lock đủ phá tính đúng đắn của thiết kế.

## 3. Khi nào read-heavy thực sự có lợi?

Một ứng viên phù hợp thường có: rất nhiều lần đọc; rất ít lần cập nhật; nhiều reader tranh cùng cấu trúc; đọc có thời lượng đáng kể; writer giữ write lock ngắn; thiết kế data và invariant rõ ràng.

Ngược lại, `ReentrantReadWriteLock` quản lý số lượng reader, write ownership và hàng đợi phức tạp hơn exclusive lock. Nếu mỗi lần đọc chỉ lấy một biến hoặc lookup cực ngắn, overhead có thể vượt lợi ích. Với tỉ lệ ghi cao, write lock độc quyền thường xuyên khiến shared reads hiếm khi cùng được thực hiện.

Tài liệu `ReadWriteLock` của JDK lưu ý hiệu quả phụ thuộc tỉ lệ read/write, thời lượng thao tác, contention và số bộ xử lý thực. Vì vậy **read-heavy là điều kiện gợi ý để benchmark, không phải chứng minh tốc độ**.

| Loại workload | Khả năng phù hợp |
| --- | --- |
| Map lớn với tra cứu dài, sửa ít, nhiều reader | Có thể hợp lý |
| Immutable configuration thay mới vài lần mỗi giờ | `volatile` reference tới immutable snapshot thường đơn giản hơn |
| Một biến đếm cập nhật rất thường xuyên | `AtomicLong`/`LongAdder` hoặc công cụ khác phù hợp hơn |
| Mỗi request get/put một key độc lập | `ConcurrentHashMap` có thể hiệu quả hơn khóa toàn map |
| Mỗi thao tác đọc thực ra cập nhật LRU/hit count | Không còn là read-only; cần protocol mutation |
| Critical section luôn rất ngắn và tranh chấp thấp | Exclusive lock có thể dễ hiểu và nhanh hơn |

## 4. Ví dụ 1: từ điển đọc nhiều, ghi ít

`HashMap` tự nó không được dùng để read/write đồng thời khi có mutation. Nhưng ta có thể **đóng gói hoàn toàn** nó và chỉ truy cập dưới read/write lock tương ứng.

Tạo `ReadMostlyDictionary.java` (Java 17+):

~~~java
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReadMostlyDictionary {
    private final Map<String, String> values = new HashMap<>();
    private final ReadWriteLock rw = new ReentrantReadWriteLock();

    public String get(String key) {
        rw.readLock().lock();
        try {
            return values.get(key);
        } finally {
            rw.readLock().unlock();
        }
    }

    public void put(String key, String value) {
        rw.writeLock().lock();
        try {
            values.put(key, value);
        } finally {
            rw.writeLock().unlock();
        }
    }

    public int size() {
        rw.readLock().lock();
        try {
            return values.size();
        } finally {
            rw.readLock().unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ReadMostlyDictionary dictionary = new ReadMostlyDictionary();
        dictionary.put("language", "Java");

        Thread firstReader = new Thread(() -> {
            for (int i = 0; i < 1_000; i++) {
                String value = dictionary.get("language");
                if (!"Java".equals(value) && !"Java 17".equals(value)) {
                    throw new AssertionError(value);
                }
            }
        });
        Thread secondReader = new Thread(() -> {
            for (int i = 0; i < 1_000; i++) {
                dictionary.size();
            }
        });
        Thread writer = new Thread(
                () -> dictionary.put("language", "Java 17"));

        firstReader.start();
        secondReader.start();
        writer.start();
        firstReader.join();
        secondReader.join();
        writer.join();

        System.out.println("size = " + dictionary.size());
        System.out.println("language = " + dictionary.get("language"));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac ReadMostlyDictionary.java
java ReadMostlyDictionary
~~~

Stdout bình thường:

~~~text
size = 1
language = Java 17
~~~

Trong lúc chạy, reader có thể nhìn thấy `Java` hoặc `Java 17` tùy lịch xen kẽ; **không có thứ tự quan sát cố định**. Nhưng vì đọc/ghi map dùng khóa đúng, reader không thao tác vào cấu trúc map trong lúc writer đang mutate. Sau `join()` writer, giá trị cuối cùng là `Java 17`.

`String` là immutable. Nếu map trả về một `MutableValue` mà thread khác sửa object ấy sau khi `get()` trả về, khóa chỉ bảo vệ **tra cứu map**, không bảo vệ mutation của object được trả về. Nên dùng immutable values, defensive copy, hoặc protocol khóa bảo vệ cả state bên trong.

Đừng phơi trực tiếp `values` qua getter; người gọi có thể sửa map mà không khóa và khiến cả mô hình sai.

## 5. Ví dụ 2: hai reader cùng vào, writer phải chờ

Để chứng minh khả năng nhiều reader cùng giữ read lock, hãy điều phối bằng `CountDownLatch` thay vì `Thread.sleep`.

Tạo `ReadLockCoordination.java` (Java 17+):

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReadLockCoordination {
    public static void main(String[] args) throws InterruptedException {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        CountDownLatch bothInside = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean writerEntered = new AtomicBoolean(false);

        Runnable reader = () -> {
            rw.readLock().lock();
            try {
                bothInside.countDown();
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                rw.readLock().unlock();
            }
        };

        Thread a = new Thread(reader, "reader-a");
        Thread b = new Thread(reader, "reader-b");
        a.start();
        b.start();

        bothInside.await(); // Cả hai reader đang giữ khóa đọc.
        System.out.println("both readers inside = true");

        Thread writer = new Thread(() -> {
            rw.writeLock().lock();
            try {
                writerEntered.set(true);
            } finally {
                rw.writeLock().unlock();
            }
        }, "writer");
        writer.start();

        System.out.println("writer before release = "
                + writerEntered.get());

        release.countDown();
        a.join();
        b.join();
        writer.join();

        System.out.println("writer after release = "
                + writerEntered.get());
    }
}
~~~

Chạy:

~~~bash
javac ReadLockCoordination.java
java ReadLockCoordination
~~~

Stdout nếu không có interruption từ bên ngoài:

~~~text
both readers inside = true
writer before release = false
writer after release = true
~~~

`bothInside.await()` chỉ hoàn tất khi **cả hai reader** đã lấy read lock và giảm latch; nếu read lock loại trừ reader khác, reader thứ hai không thể vào trong khi reader thứ nhất chờ release. Dòng `writer before release = false` là bảo đảm vì cả hai reader vẫn nắm read lock; writer chưa thể vào write critical section. Sau `release.countDown()` và `join()` cả ba thread, writer đã có cơ hội cập nhật cờ.

Đây là thí nghiệm để học lock semantics; **không giữ read lock qua một wait/blocking operation như vậy trong ứng dụng thật** nếu công việc phát tín hiệu cần write lock của chính nó. Một chu trình chờ có thể gây deadlock.

## 6. Một phương thức tên `get()` có thể là write operation

“Read” trong read lock có nghĩa **không thay đổi shared state được bảo vệ**, không có nghĩa “phương thức có tên getter”.

Các ví dụ read có side effect:

- `LinkedHashMap` được khởi tạo với `accessOrder=true`: một lần `get()` có thể thay đổi thứ tự node nội bộ.
- Bộ nhớ đệm tự khởi tạo khi đọc (`computeIfAbsent` hoặc lazy load).
- LRU eviction, cập nhật `lastAccess`, cache hit/miss metrics lưu trong field thường.
- Getter tạo memoized value hoặc sửa object con.
- Iteration có remove hoặc sửa phần tử mutable.

~~~java
// Snippet minh họa; lru là access-order map.
java.util.Map<String, String> lru =
        new java.util.LinkedHashMap<>(16, 0.75f, true);
String value = lru.get("key"); // Có thể thay đổi access order.
~~~

Nếu map này được nhiều thread chia sẻ, thao tác `get` có tính **mutation** nên không được tùy tiện gọi dưới shared read lock. Có thể phải dùng write lock hoặc một cấu trúc cache chuyên dụng. Khi mỗi “read” đều cập nhật state, giá trị của ReadWriteLock sẽ giảm đáng kể.

## 7. Snapshot nhất quán khi state có nhiều field

Giả sử một ledger có `total` và `version`, invariant là mỗi lần tăng total thì tăng version đúng một lần. Writer phải cập nhật cả hai **trong cùng write lock**, reader phải lấy cả hai **trong cùng read lock**.

Tạo `ReadWriteSnapshot.java` (Java 17+):

~~~java
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReadWriteSnapshot {
    private record Snapshot(long total, long version) {}

    private static final class Ledger {
        private final ReentrantReadWriteLock rw =
                new ReentrantReadWriteLock();
        private long total;
        private long version;

        void addOne() {
            rw.writeLock().lock();
            try {
                total++;
                version++;
            } finally {
                rw.writeLock().unlock();
            }
        }

        Snapshot snapshot() {
            rw.readLock().lock();
            try {
                return new Snapshot(total, version);
            } finally {
                rw.readLock().unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Ledger ledger = new Ledger();
        Runnable writer = () -> {
            for (int i = 0; i < 1_000; i++) {
                ledger.addOne();
            }
        };
        Thread first = new Thread(writer);
        Thread second = new Thread(writer);
        first.start();
        second.start();
        first.join();
        second.join();

        Snapshot snapshot = ledger.snapshot();
        System.out.println("total = " + snapshot.total());
        System.out.println("version = " + snapshot.version());
        System.out.println("consistent = "
                + (snapshot.total() == snapshot.version()));
    }
}
~~~

Chạy:

~~~bash
javac ReadWriteSnapshot.java
java ReadWriteSnapshot
~~~

Stdout:

~~~text
total = 2000
version = 2000
consistent = true
~~~

`snapshot()` tạo record bất biến ngay trong critical section nên caller dùng snapshot bên ngoài khóa vẫn có cùng cặp field. Không nên đọc hai field trong **hai lần** read lock riêng biệt: writer có thể chen vào giữa và làm snapshot pha hai phiên bản.

Nếu snapshot chứa list hay object mutable, cần tạo immutable/defensive copy của phần cần bảo vệ. Chỉ sao chép reference của collection đang được mutate không làm bản sao đó thread-safe.

## 8. Không upgrade trực tiếp từ read lock sang write lock

Với `ReentrantReadWriteLock`, reader đang giữ read lock **không được** lấy thêm write lock để “nâng cấp”. Write lock chỉ được cấp khi không còn reader khác; chính read lock của caller vẫn đang tồn tại, có thể khiến caller tự chờ vô hạn.

~~~java
// MÃ SAI: không chạy thử nếu không có timeout và phương án thoát.
rw.readLock().lock();
try {
    if (missing) {
        rw.writeLock().lock(); // Không hỗ trợ upgrade.
        try {
            initialize();
        } finally {
            rw.writeLock().unlock();
        }
    }
} finally {
    rw.readLock().unlock();
}
~~~

Đúng hơn là:

1. Đọc cache và điều kiện dưới read lock.
2. **Thả read lock** trước khi cố lấy write lock.
3. Acquire write lock.
4. **Kiểm tra lại** điều kiện vì trong khoảng trống giữa hai lock, thread khác có thể đã làm xong.
5. Chỉ thực hiện cập nhật nếu điều kiện vẫn còn đúng.
6. Nhả write lock trong `finally`.

Điểm rất quan trọng: read-unlock → write-lock **không phải upgrade nguyên tử**. Recheck là điều kiện correctness, không phải bước thừa.

### Ví dụ 4: double-check một cache miss

Tạo `ReadWriteCache.java` (Java 17+):

~~~java
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReadWriteCache {
    private final Map<String, String> values = new HashMap<>();
    private final ReentrantReadWriteLock rw =
            new ReentrantReadWriteLock();

    public String getOrLoad(String key) {
        rw.readLock().lock();
        try {
            String existing = values.get(key);
            if (existing != null) {
                return existing;
            }
        } finally {
            rw.readLock().unlock();
        }

        // Tính toán thuần túy, có thể làm ngoài lock.
        String loaded = key.toUpperCase(Locale.ROOT);

        rw.writeLock().lock();
        try {
            // Có thể đã được thread khác nạp trong lúc ta không giữ lock.
            String existing = values.get(key);
            if (existing != null) {
                return existing;
            }
            values.put(key, loaded);
            return loaded;
        } finally {
            rw.writeLock().unlock();
        }
    }

    public int size() {
        rw.readLock().lock();
        try {
            return values.size();
        } finally {
            rw.readLock().unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ReadWriteCache cache = new ReadWriteCache();
        String[] result = new String[2];

        Thread a = new Thread(() -> result[0] = cache.getOrLoad("java"));
        Thread b = new Thread(() -> result[1] = cache.getOrLoad("java"));
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("same = " + result[0].equals(result[1]));
        System.out.println("value = " + result[0]);
        System.out.println("size = " + cache.size());
    }
}
~~~

Chạy:

~~~bash
javac ReadWriteCache.java
java ReadWriteCache
~~~

Stdout:

~~~text
same = true
value = JAVA
size = 1
~~~

Hai thread có thể cùng thấy miss và **tính `loaded` nhiều lần**; logic tính uppercase thuần túy nên không có vấn đề. Nhưng chỉ một thread cần đặt value vào map vì bước recheck xảy ra dưới write lock. Nếu loader có side effect (gọi mạng, ghi DB, gửi email), không được tự suy ra loader được gọi đúng một lần; cần cơ chế single-flight, futures per key hoặc protocol khác.

Nếu computation ngoài lock phụ thuộc shared state đang thay đổi, phải bảo vệ consistency bằng version/retry hoặc chuyển phần quyết định quan trọng vào write section. Không thể chỉ release-read/acquire-write mà vẫn tin snapshot cũ là mới nhất.

## 9. Downgrade từ write lock xuống read lock được hỗ trợ

`ReentrantReadWriteLock` **cho phép** thread đã giữ write lock acquire thêm read lock (reentrant), sau đó nhả write lock nhưng vẫn giữ read lock. Nhờ đó writer vừa cập nhật có thể tiếp tục quan sát state dưới read lock **mà không có writer khác chen vào trong lúc chuyển chế độ**.

~~~text
acquire WRITE
update state
acquire READ khi WRITE vẫn held
release WRITE
đọc/tiếp tục xử lý dưới READ
release READ
~~~

Tạo `DowngradeExample.java` (Java 17+):

~~~java
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DowngradeExample {
    public static void main(String[] args) {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        Lock read = rw.readLock();
        Lock write = rw.writeLock();
        String[] state = {"old"};

        write.lock();
        try {
            state[0] = "new";
            read.lock();  // Writer lấy thêm read lock.
        } finally {
            write.unlock();
        }

        try {
            System.out.println("state = " + state[0]);
        } finally {
            read.unlock();
        }
    }
}
~~~

Chạy:

~~~bash
javac DowngradeExample.java
java DowngradeExample
~~~

Stdout:

~~~text
state = new
~~~

Đoạn này tập trung vào **đường chạy thành công**. Khi thực hiện nhiều lần acquire với code phức tạp có thể ném exception, cần kiểm soát chính xác khóa nào đã acquire trước khi unlock. Việc downgrade không biến mọi thao tác thành atomic hay thay thế bảo vệ cho object mutable được trả ra ngoài khóa.

| Trường hợp | Hỗ trợ |
| --- | --- |
| Read lock reentrant (cùng reader) | Có |
| Write lock reentrant (cùng writer) | Có |
| Writer giữ write rồi lấy read | Có |
| Reader giữ read rồi lấy write | **Không hỗ trợ upgrade** |
| Downgrade write → read không để writer chen ngang | Có, lấy read trước khi nhả write |

## 10. Fairness và nguy cơ writer starvation

Mặc định:

~~~java
ReentrantReadWriteLock regular = new ReentrantReadWriteLock();
ReentrantReadWriteLock fair = new ReentrantReadWriteLock(true);
~~~

Đây là snippet API. `regular` dùng **non-fair mode**; `fair` dùng chính sách **approximately arrival order** khi tranh quyền lấy lock.

- **Non-fair:** thứ tự cấp read/write lock không cố định theo FIFO; thường throughput cao nhưng trong contention liên tục một reader hoặc writer có thể bị trì hoãn rất lâu, thậm chí vô hạn.
- **Fair:** có chính sách gần thứ tự yêu cầu, chọn writer đang đợi lâu hoặc nhóm reader theo thứ tự phù hợp. Có thể giảm nguy cơ starvation nhưng thường phải trả giá về throughput. Fair không đồng nghĩa bảo đảm deadline hoặc fairness tuyệt đối của OS scheduler.
- **Ngoại lệ:** `readLock().tryLock()` và `writeLock().tryLock()` không tuân theo fair setting; nếu lock có thể lấy ngay, `tryLock()` có thể lấy dù còn thread khác đợi.

Khi một writer phải chờ nhiều reader nhả khóa, **writer latency** có thể bị ảnh hưởng dù read throughput tốt. Đọc trong read lock nên tập trung vào việc cần protected state; I/O, callback, render, parse nặng độc lập có thể thực hiện sau khi copy snapshot và thả khóa.

## 11. Condition: write lock có, read lock không

`ReentrantReadWriteLock` hỗ trợ `Condition` ở **write lock**:

~~~java
ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
java.util.concurrent.locks.Condition changed =
        rw.writeLock().newCondition();
~~~

`rw.readLock().newCondition()` ném `UnsupportedOperationException`; không có Condition trên read lock.

Để dùng `Condition.await()/signalAll()` đúng, thread phải nắm write lock liên kết và dùng protocol predicate; `await` thả write lock trong lúc chờ rồi lấy lại trước khi trả về. Mẫu minh họa (không độc lập):

~~~java
rw.writeLock().lock();
try {
    while (!ready) {
        changed.await();
    }
    // Đọc/thay đổi state theo invariant.
} finally {
    rw.writeLock().unlock();
}
~~~

Ở ví dụ thật, một thread khác phải thay `ready` dưới write lock đó và `signalAll()` khi điều kiện đổi. `while` quan trọng vì spurious wakeups và vì predicate có thể đã đổi khi thread thức dậy. Với queue producer-consumer, `BlockingQueue` có thể diễn đạt điều phối đơn giản hơn.

## 12. Interrupt, timeout và tryLock

Các lock implement `Lock` nên hỗ trợ các thao tác acquire khác nhau:

| API | Cách dùng |
| --- | --- |
| `lock()` | Chờ lấy lock; dùng `try/finally` để nhả |
| `lockInterruptibly()` | Có thể bị ngắt trong khi chờ; ném `InterruptedException` |
| `tryLock()` | Thử ngay; trả `true` nếu lấy được, `false` nếu không |
| `tryLock(timeout, unit)` | Chờ tới giới hạn; trả boolean hoặc ném `InterruptedException` |
| `unlock()` | Chỉ gọi khi thread hiện tại thực sự đã lấy lock |

~~~java
// Snippet phương thức có trả boolean, không độc lập.
if (!rw.writeLock().tryLock(100,
        java.util.concurrent.TimeUnit.MILLISECONDS)) {
    return false;
}
try {
    updateState();
    return true;
} finally {
    rw.writeLock().unlock();
}
~~~

Nếu timeout, không được `unlock()` vì chưa có lock. Timeout này chỉ giới hạn **thời gian chờ acquire**, không giới hạn thời gian làm việc bên trong write section. Khi bị `InterruptedException`, có chính sách dừng/propagate phù hợp; đừng nuốt tín hiệu rồi đợi mãi.

## 13. So sánh các lựa chọn cho read-heavy

### A. `synchronized` hoặc `ReentrantLock`

Nếu critical section cực ngắn và contention thấp, exclusive lock có thể vừa đơn giản vừa hiệu quả. Cần đo trước khi thay bằng RW lock. Exclusive lock cũng dễ quản lý hơn khi nhiều bước check-then-act và invariant đòi hỏi cả read/write đồng bộ.

### B. `ConcurrentHashMap`

Thích hợp nếu đa số operation là đọc/ghi từng key độc lập và không cần một snapshot toàn map. `get`, `put`, `compute` và `merge` có contract riêng; **chuỗi `get` rồi `put` không tự atomic**. Nếu cần một transaction nhiều key hoặc snapshot nhất quán của toàn bộ map, một protocol khác có thể cần thiết.

### C. `volatile` reference tới immutable snapshot

Cấu hình nhỏ thay ít nhưng đọc rất thường xuyên có thể được biểu diễn bằng:

~~~java
// Snippet mô tả fields/methods trong một class.
record Settings(String host, int port) {}

private volatile Settings current = new Settings("primary", 8080);

// Writer: xây mới đầy đủ, rồi gán reference.
current = new Settings("backup", 9090);

// Reader: lấy snapshot vào local.
Settings snapshot = current;
~~~

Ở đây không cần giữ read lock mỗi lần đọc; volatile write/read publish các ghi khởi tạo. Nhưng nếu nhiều writer đọc current → tính mới → gán current thì vẫn có thể mất cập nhật. Nếu `Settings` có component trỏ tới object mutable, record không làm nó bất biến sâu.

### D. `AtomicReference<ImmutableState>`

Hữu ích khi nhiều writer muốn thay **toàn state** bằng CAS/`updateAndGet`. Hàm update nên thuần vì có thể chạy lại. Atomicity áp dụng cho reference được thay thế, không làm side effect bên ngoài thành atomic.

### E. `StampedLock`

Có optimistic reads (`tryOptimisticRead`/`validate`), nhưng là loại lock khác, **không reentrant**, có quy tắc validate/visibility phức tạp. Đừng đổi sang `StampedLock` chỉ vì muốn nhanh hơn khi chưa hiểu code và chưa benchmark.

**Kết luận lựa chọn:** RW lock phù hợp khi ta muốn **nhiều reader của cùng cấu trúc lớn** cùng vào protected area, nhưng vẫn muốn writer cập nhật độc quyền và reader lấy snapshot nhất quán.

## 14. Đo hiệu năng đúng cách

Một benchmark hữu ích phải so **cùng logic và cùng semantics** giữa `synchronized`, `ReentrantReadWriteLock`, `ConcurrentHashMap` hoặc immutable snapshot. Không so những thiết kế khác nhau về độ an toàn mà chỉ nhìn throughput.

Nên biến thiên:

1. **Read/write ratio**: ví dụ 99/1, 90/10, 50/50.
2. **Số reader/writer, CPU cores**: có nhiều read thực sự chạy cùng lúc không?
3. **Thời gian giữ read lock**: chỉ đọc 1 primitive hay tạo snapshot từ map lớn?
4. **Thời gian giữ write lock**: cập nhật nhanh hay I/O chậm?
5. **Fairness**: throughput non-fair so với p95/p99 write waiting latency.
6. **GC/allocation**: snapshot copy có tạo áp lực heap?
7. **Load thực tế**: peak, burst writer, số thread, contention, starvation.

Dùng benchmark như JMH có warmup, nhiều lần chạy, kết quả thống kê; không kết luận từ một lần chạy ngắn với `System.nanoTime()` trong main. Với production hãy theo dõi read latency, write latency, queue wait time và lock contention.

Các API instrumentation như `getReadLockCount()`, `getQueueLength()` hoặc `isWriteLocked()` chỉ phục vụ monitoring; một giá trị đo tại thời điểm t **không phải cơ sở đồng bộ** để quyết định truy cập state không lock ở thời điểm t+1.

## 15. Các lỗi triển khai phổ biến

| Lỗi | Hệ quả | Cách làm đúng |
| --- | --- | --- |
| Dùng read lock cho một getter có mutation | Concurrent writers/readers gây race | Xác định đọc thuần hay ghi thực tế |
| Quên `unlock` trên exception | Lock không được nhả, thread khác treo | `try/finally` |
| Read lock và write lock lấy từ hai `rw` khác nhau | Không có mutual exclusion giữa chúng | Một instance chung cho state |
| Reader giữ read rồi đòi write | Có thể tự kẹt | Nhả read, acquire write, recheck |
| Không recheck sau chuyển read→write | Ghi đè dữ liệu mới, tính từ snapshot cũ | Kiểm tra lại điều kiện dưới write |
| Reader giữ read lock qua HTTP/log chậm | Writer phải chờ lâu | Copy snapshot, xử lý ngoài lock |
| Trả về mutable object rồi sửa ngoài lock | Bỏ qua protection | Immutable value/copy hoặc lock protocol |
| Snapshot nhiều field ở hai lần read khác nhau | Trộn phiên bản | Một read section chung |
| Bật fair rồi nghĩ không còn starvation/deadline issue | Sai hợp đồng scheduling | Benchmark, timeout, monitoring |
| `tryLock` được tưởng là FIFO trong fair mode | Barge trước thread đợi | Đọc hợp đồng `tryLock` |
| Tạo `Condition` từ read lock | Unsupported operation | Condition của write lock |
| Coi mọi cache `get` là read-only | LRU/lazy mutation khiến reader xung đột | Phân loại thao tác theo side effect |
| Tự tạo ReadWriteLock cho một `AtomicInteger` | Overhead không cần thiết | Dùng atomic operation đúng scope |
| Chọn RW lock không benchmark | Có thể chậm hơn simple exclusive lock | Đo trên workload đại diện |

## 16. Câu hỏi kiểm tra kiến thức

**1.** Vì sao 10 reader cùng giữ read lock được mà một writer phải đợi?  
**Đáp án:** Vì các đọc thuần không thay đổi shared state, còn write cần exclusivity để bảo vệ invariant. Chính sách acquire có thể khiến reader mới chờ khi writer đang đợi.

**2.** `HashMap` được bảo vệ bằng RW lock; một getter trả reference đến `ArrayList` nội bộ rồi caller sửa list ngoài lock. An toàn không?  
**Đáp án:** Không. Protection chỉ có hiệu lực khi caller tuân thủ protocol cho state mutable; cần copy hoặc lock cho cả mutation của object con.

**3.** Tại sao không được giữ read lock khi gọi `writeLock().lock()`?  
**Đáp án:** `ReentrantReadWriteLock` không hỗ trợ upgrade. Write cần các read locks biến mất, bao gồm read lock của chính thread đang đợi.

**4.** Sau khi thả read và lấy write lock, có cần kiểm tra lại trạng thái miss của cache?  
**Đáp án:** Có. Thread khác có thể điền cache trong khoảng chuyển lock.

**5.** Hạ write→read khác nâng read→write như thế nào?  
**Đáp án:** Writer có thể lấy read lock trong lúc vẫn nắm write rồi nhả write; reader không thể đòi write khi giữ read.

**6.** `readLock().newCondition()` có hoạt động không?  
**Đáp án:** Không, ném `UnsupportedOperationException`. Condition thuộc write lock.

**7.** Fair mode có bắt `tryLock()` tuân thứ tự hàng đợi không?  
**Đáp án:** Không. `tryLock()` không tuân fairness setting.

**8.** Một ứng dụng 99% đọc nhưng mọi read chỉ lookup một key ngắn. RW lock chắc chắn nhanh nhất không?  
**Đáp án:** Không. Overhead cao, ít contention hoặc `ConcurrentHashMap`/volatile snapshot có thể hợp lý hơn.

**9.** Đọc `total` và `version` ở hai lần read lock riêng có luôn nhất quán không?  
**Đáp án:** Không. Writer có thể chen giữa; chụp hai field trong một read critical section.

**10.** Nên đo chỉ throughput read thôi để quyết định triển khai?  
**Đáp án:** Không. Còn writer latency, starvation, tail latency, memory và correctness.

## Tài liệu tham khảo chính thức

- [`ReadWriteLock` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReadWriteLock.html): shared readers, exclusive writer, memory synchronization, tradeoff hiệu năng.
- [`ReentrantReadWriteLock` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantReadWriteLock.html): reentrancy, fairness, lock downgrading, không hỗ trợ upgrading, Condition.
- [`Lock` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Lock.html): acquisition, `tryLock`, interruption, `unlock`.
- [`Condition` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Condition.html): waiting/signal theo predicate.
- [`ConcurrentHashMap` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): concurrent map operations.
- [`StampedLock` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/StampedLock.html): alternative với optimistic reads.
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): happens-before và đồng bộ.

**Ghi nhớ:** Nhiều reader có thể cùng giữ read lock; writer cần write lock độc quyền. Chỉ dùng read lock cho **read-only critical section**, luôn nhả khóa trong `finally`, không upgrade trực tiếp, và benchmark để chứng minh lợi ích với workload thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-and-conditions-009">ReentrantReadWriteLock: nâng/hạ cấp khóa và giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# ReentrantReadWriteLock: nâng/hạ cấp khóa và giới hạn trong Java

Trong một hệ thống nhiều thread, nhiều thao tác chỉ **đọc** cùng một trạng thái, còn thao tác **ghi** cần độc quyền để bảo vệ bất biến. `ReentrantReadWriteLock` cung cấp một cặp khóa liên kết với nhau: `readLock()` để nhiều reader cùng giữ khi không có writer đang nắm khóa, và `writeLock()` để writer có quyền độc quyền. Tuy nhiên, cách chuyển từ đọc sang ghi và từ ghi sang đọc **không đối xứng**: Java cho phép **hạ cấp write → read**, nhưng **không hỗ trợ nâng cấp read → write** khi vẫn giữ read lock.

Bài này thuộc mục **Locks & Conditions**, phạm vi **Java concurrency nói chung**, áp dụng với **Java 17+** và API chuẩn trong `java.util.concurrent.locks`. Không dùng cơ chế riêng của Spring.

Sau bài học, bạn sẽ:
- Biết khi nào dùng read lock, write lock, và tại sao vẫn cần một bất biến cùng giao thức khóa.
- Hiểu ý nghĩa của **reentrancy**, read/write ownership và số lần khóa phải nhả.
- Chứng minh vì sao cố nâng read → write có thể tự chặn vĩnh viễn; biết mẫu **nhả read → lấy write → kiểm tra lại điều kiện**.
- Thực hiện **downgrade** bằng đúng thứ tự **write.lock → read.lock → write.unlock**, rồi đọc tiếp và nhả read lock.
- Nắm giới hạn: fairness, `tryLock`, conditions, interrupt, đo hiệu năng, tránh starvation/deadlock và cách kiểm tra mã.

## 1. ReadWriteLock và hai kiểu quyền truy cập

`java.util.concurrent.locks.ReadWriteLock` là interface có hai phương thức:

~~~java
Lock readLock();
Lock writeLock();
~~~

`ReentrantReadWriteLock` là một implementation có tính **reentrant** và hỗ trợ chính sách **nonfair** (mặc định) hoặc **fair** (được chỉ định qua constructor). Cả hai lock thuộc **cùng một cấu trúc** và điều phối quyền đọc/ghi trên **cùng trạng thái được bảo vệ**.

| Tình trạng hiện tại | Xin read lock | Xin write lock |
| --- | --- | --- |
| Không ai giữ lock | Có thể nhận, tùy chính sách fairness/hàng đợi | Có thể nhận, tùy chính sách fairness/hàng đợi |
| Một hoặc nhiều reader đang giữ | Reader khác có thể cùng đọc nếu chính sách cho phép | Writer khác phải chờ reader nhả hết |
| Một writer đang giữ | Reader khác phải chờ | Writer khác phải chờ |
| **Chính thread** đang giữ write lock | Có thể tái nhập write và lấy thêm read lock | Có thể tái nhập write lock |
| **Chính thread** chỉ đang giữ read lock | Có thể tái nhập read lock | **Không nâng cấp trực tiếp lên write lock** |

Các ô “có thể” phải xét fairness, việc một writer đang chờ và trạng thái interrupt; read lock không phải lệnh bảo đảm reader luôn vào ngay khi có reader khác.

Hai điều cốt lõi:

1. **Read-side concurrency:** nhiều reader có thể cùng vào đoạn chỉ đọc, khi không có writer khác nắm write lock.
2. **Write-side exclusion:** writer giữ write lock loại trừ những reader/writer khác khỏi phần trạng thái được bảo vệ, nhưng chỉ khi **mọi đường truy cập** đều tuân thủ cùng cặp lock.

Sự tách biệt read/write là một quyết định điều phối quyền. Nó **không tự tạo bất biến**, không biến dữ liệu mutable thành immutable, và không bảo vệ những thao tác đọc/ghi thực hiện ngoài khóa.

### 1.1. Memory visibility

Theo hợp đồng `ReadWriteLock`, các hiệu ứng đồng bộ bộ nhớ của write lock áp dụng với read lock tương ứng: reader lấy thành công read lock phải thấy những cập nhật đã được công bố qua lần nhả write lock trước đó theo giao thức khóa. Vì vậy có thể giữ các field được bảo vệ là kiểu bình thường thay vì `volatile` khi **mọi** thao tác phù hợp đều dùng đúng lock.

Tuy nhiên, memory visibility **không thay thế mutual exclusion**: nếu cần thao tác đọc–kiểm tra–cập nhật là một đơn vị, phải lấy write lock cho toàn bộ hành động.

## 2. Ví dụ đọc song song, ghi độc quyền

Trên Java 17, dùng `java.util.concurrent.locks.ReentrantReadWriteLock` với hai handle:

~~~java
private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
private final Lock read = rw.readLock();
private final Lock write = rw.writeLock();
~~~

Đây là đoạn field **minh họa**, chưa phải một file Java độc lập. Nhớ đặt lock thành `private final` để không làm lộ khả năng đồng bộ nội bộ cho caller ngoài lớp.

### Ví dụ Java 17+: hai reader thực sự giữ khóa cùng lúc

Dùng latch để quan sát **đúng hai reader cùng giữ read lock**, thay vì dựa vào thời gian ngủ không xác định.

~~~java
// ConcurrentReadersDemo.java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentReadersDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        Runnable reader = () -> {
            rw.readLock().lock();
            try {
                entered.countDown();
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                rw.readLock().unlock();
            }
        };

        Thread first = new Thread(reader, "reader-1");
        Thread second = new Thread(reader, "reader-2");
        first.start();
        second.start();

        entered.await();
        System.out.println("Readers holding: " + rw.getReadLockCount());
        release.countDown();

        first.join();
        second.join();
        System.out.println("Readers after: " + rw.getReadLockCount());
    }
}
~~~

~~~bash
javac ConcurrentReadersDemo.java
java ConcurrentReadersDemo
~~~

Stdout:

~~~text
Readers holding: 2
Readers after: 0
~~~

Khi main đợi `entered.await()` thành công, cả hai reader đã lấy read lock, rồi cùng đợi `release` trước khi có thể nhả khóa. Do đó giá trị 2 ở điểm in là xác định. Sau khi nhả latch và `join` cả hai thread, không còn ai giữ read lock, nên kết quả là 0.

**Lưu ý:** `getReadLockCount()` là API quan sát/giám sát; không nên dùng nó như điều kiện để quyết định tính đúng của giao thức đồng bộ trong ứng dụng. Ví dụ dùng latch để cố định một trạng thái minh họa, không dựa vào việc hỏi số reader để thực hiện update.

### Quy tắc lock/unlock

~~~java
read.lock();
try {
    // Chỉ đọc dữ liệu được bảo vệ.
} finally {
    read.unlock();
}

write.lock();
try {
    // Đọc, kiểm tra và thay đổi bất biến.
} finally {
    write.unlock();
}
~~~

Đây là **các đoạn minh họa**, giả sử `read` và `write` đã khai báo. Thao tác `unlock` phải được thực hiện bởi thread hiện đang sở hữu số lần giữ khóa tương ứng. `ReentrantReadWriteLock` đếm các lần giữ lại (reentrancy); mỗi lần `lock` thành công phải được cân bằng bằng một lần `unlock`. Nếu unlock sai ownership có thể ném `IllegalMonitorStateException`.

## 3. Nâng cấp read → write: không được hỗ trợ

Tình huống thường gặp: thread đọc cache dưới read lock, phát hiện cache thiếu/hết hạn và muốn **giữ read lock rồi xin write lock** để cập nhật. Điều này không hoạt động.

~~~java
// CẤM áp dụng cách này; mã minh họa có thể tự chặn vô hạn.
rw.readLock().lock();
try {
    if (cacheNeedsRefresh()) {
        rw.writeLock().lock(); // Chờ vô hạn nếu thread vẫn giữ read lock.
        try {
            refreshCache();
        } finally {
            rw.writeLock().unlock();
        }
    }
} finally {
    rw.readLock().unlock();
}
~~~

Ở đây `cacheNeedsRefresh()` và `refreshCache()` là tên minh họa, **không phải API Java có sẵn**; đoạn code không phải file chạy độc lập.

### 3.1. Vì sao có thể tự deadlock kể cả khi chỉ một reader?

Thread T hiện giữ read lock. Để lấy write lock, không được có reader nào đang giữ read lock (ngoại trừ trường hợp chủ thread đã sở hữu write lock từ trước và nay lấy read lock). Thread T đang là chính reader khiến writer không được cấp quyền; nhưng code của T lại chờ `write.lock()` trả về **trước khi nhả read lock**. Nó tự chặn tiến triển của mình.

~~~text
Thread T:
  read.lock()   -> đã giữ read
  write.lock()  -> đợi tất cả read holders nhả read
  read.unlock() -> nằm sau write.lock(), không bao giờ tới được

=> Self-deadlock / chờ chuyển đổi khóa không thể hoàn tất.
~~~

Điểm này **không được sửa bằng fairness=true**, không được sửa bằng `volatile` hay thay thứ tự `finally`. Việc `ReentrantReadWriteLock` **reentrant** chỉ có nghĩa thread có thể tái nhập các lock theo chiều được hỗ trợ; nó **không** có chế độ upgrade dành cho một reader duy nhất.

### 3.2. Kiểm chứng bằng tryLock không làm chương trình treo

~~~java
// UpgradeAttemptDemo.java
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UpgradeAttemptDemo {
    public static void main(String[] args) {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();

        rw.readLock().lock();
        try {
            boolean upgraded = rw.writeLock().tryLock();
            System.out.println("Upgrade while holding read: " + upgraded);
            if (upgraded) {
                rw.writeLock().unlock();
            }
        } finally {
            rw.readLock().unlock();
        }

        boolean acquired = rw.writeLock().tryLock();
        System.out.println("Acquire after release: " + acquired);
        if (acquired) {
            rw.writeLock().unlock();
        }
    }
}
~~~

~~~bash
javac UpgradeAttemptDemo.java
java UpgradeAttemptDemo
~~~

Stdout:

~~~text
Upgrade while holding read: false
Acquire after release: true
~~~

Ngay cả khi không có thread khác, `tryLock()` cho write lock trả `false` trong lúc chính caller chỉ giữ read lock. Sau khi nhả read lock, caller lấy write lock thành công trong chương trình chỉ một thread này.

**Không dùng `write.lock()` để làm thí nghiệm cố tình self-deadlock**: chương trình sẽ bị kẹt và có thể cần dừng từ bên ngoài. Với giao thức nâng cấp sai trong ứng dụng thật, xem thread dump và các hold-count/stack trace để tìm điểm thread cố lấy write khi vẫn giữ read.

## 4. Thay nâng cấp bằng nhả read → lấy write → kiểm tra lại

Nếu cần “đọc trước, có vấn đề mới sửa”, mẫu đúng là:

1. Lấy read lock để quan sát trạng thái.
2. Nếu cần cập nhật, **nhả toàn bộ read hold thuộc caller** trước khi xin write lock.
3. Lấy write lock.
4. **Kiểm tra lại điều kiện** vì thread khác có thể đã cập nhật sau thời điểm nhả read.
5. Nếu vẫn cần, thực hiện cập nhật trong write lock, rồi nhả write lock.
6. Nếu cần tiếp tục đọc trạng thái được bảo vệ, lấy lại read lock hoặc dùng downgrade theo mục tiếp theo.

Vì có khoảng trống giữa nhả read và lấy write, không được lấy dữ liệu đọc lúc đầu làm bằng chứng rằng điều kiện vẫn đúng.

### Ví dụ cache khởi tạo đúng một lần

~~~java
// CacheInitializationDemo.java
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class CacheInitializationDemo {
    static final class Cache {
        private final ReentrantReadWriteLock rw =
            new ReentrantReadWriteLock();
        private String value;
        private int rebuildCount;

        String get() {
            rw.readLock().lock();
            try {
                if (value != null) {
                    return value;
                }
            } finally {
                rw.readLock().unlock();
            }

            // Khoảng trống: thread khác có thể khởi tạo cache tại đây.
            rw.writeLock().lock();
            try {
                if (value == null) { // BẮT BUỘC recheck.
                    value = "ready";
                    rebuildCount++;
                }
                return value;
            } finally {
                rw.writeLock().unlock();
            }
        }

        int rebuildCount() {
            rw.readLock().lock();
            try {
                return rebuildCount;
            } finally {
                rw.readLock().unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Cache cache = new Cache();
        Thread a = new Thread(cache::get, "A");
        Thread b = new Thread(cache::get, "B");
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("Value: " + cache.get());
        System.out.println("Rebuilds: " + cache.rebuildCount());
    }
}
~~~

~~~bash
javac CacheInitializationDemo.java
java CacheInitializationDemo
~~~

Stdout:

~~~text
Value: ready
Rebuilds: 1
~~~

Dù A và B có thể đồng thời nhìn thấy `value == null` khi đang giữ read lock, sau khi nhả read, họ phải giành write lock từng người một. Thread nhận write lock đầu tiên gán `"ready"`; thread tiếp theo **kiểm tra lại** và không xây lại dữ liệu. Dòng `rebuildCount++` chỉ chạy khi `value` thực sự còn null bên trong write lock.

**Sai lầm:** chỉ đọc `if (value == null)` dưới read lock, nhả read rồi viết ngay dưới write **mà không kiểm tra lại**. Điều này có thể thực hiện tính toán cập nhật dư thừa, ghi đè phiên bản mới, hoặc làm side effect hai lần. Nếu việc xây lại cache thực hiện I/O tốn thời gian, cần cân nhắc thiết kế ownership/đồng bộ riêng và tránh giữ write lock quá lâu; không thể tự bỏ recheck để tối ưu.

### Khác biệt với một phép upgrade thật sự

Mẫu trên **không phải atomic upgrade**: giữa hai lần khóa, thread khác có quyền thay đổi trạng thái. Nhờ recheck, ta giữ đúng điều kiện cập nhật trong write lock; nhưng không giữ nguyên snapshot quan sát dưới read lock. Nếu nghiệp vụ đòi hỏi chuyển read→write không mất quyền giữa chừng, cần thiết kế lại giao thức (ví dụ giữ write từ đầu, CAS trên trạng thái bất biến hoặc primitive khác với hợp đồng chuyển đổi phù hợp).

## 5. Hạ cấp write → read: được hỗ trợ

**Downgrade** nghĩa là một thread đang giữ write lock muốn chuyển sang **tiếp tục đọc ổn định** mà không để writer khác chen vào giữa lúc nhả write và lấy read. `ReentrantReadWriteLock` hỗ trợ theo thứ tự:

~~~text
(1) write.lock()       -- thread có quyền ghi độc quyền
(2) update state       -- cập nhật dữ liệu
(3) read.lock()        -- chính writer được lấy thêm read
(4) write.unlock()     -- nhả quyền ghi, vẫn giữ read
(5) read state         -- writer khác không thể sửa khi read còn giữ
(6) read.unlock()      -- nhả quyền đọc
~~~

Bước **(3) phải trước bước (4)**. Nếu nhả write trước khi lấy read, một writer khác có thể giành write lock và sửa dữ liệu; khi ấy không còn bảo đảm quan sát đúng phiên bản vừa ghi.

### 5.1. Ví dụ Java 17+ đầy đủ

~~~java
// DowngradeDemo.java
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DowngradeDemo {
    public static void main(String[] args) {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        int[] data = {0}; // Chỉ để demo dữ liệu chia sẻ.

        rw.writeLock().lock();
        try {
            data[0] = 42;
            rw.readLock().lock(); // Writer được phép lấy read lock.
        } finally {
            rw.writeLock().unlock();
        }

        // Tới đây thread giữ read, không còn giữ write.
        try {
            System.out.println("Value under read lock: " + data[0]);
            System.out.println(
                "Read holds: " + rw.getReadHoldCount());
            System.out.println(
                "Write holds: " + rw.getWriteHoldCount());
        } finally {
            rw.readLock().unlock();
        }

        System.out.println("Read holds after: " + rw.getReadHoldCount());
    }
}
~~~

~~~bash
javac DowngradeDemo.java
java DowngradeDemo
~~~

Stdout:

~~~text
Value under read lock: 42
Read holds: 1
Write holds: 0
Read holds after: 0
~~~

Trong cùng thread, sau `rw.readLock().lock()` và `rw.writeLock().unlock()`, read hold vẫn còn. Phương thức `getReadHoldCount()` trả số read hold của chính thread đang gọi, khác với `getReadLockCount()` đo tổng số read hold trên cấu trúc lock. `getWriteHoldCount()` là số write hold reentrant của chính thread.

Bài demo có một thread nên dễ quan sát, nhưng thứ tự hạ cấp cũng đúng khi nhiều thread: một writer khác không thể nắm write lock trong lúc read lock còn được giữ. Các phương thức hold count dùng cho theo dõi/chẩn đoán, **không phải primitive để quyết định giao thức synchronization**.

### 5.2. Quản lý exception đúng khi có hai lock

Nếu code thực tế phức tạp, việc nhận read lock khi vẫn giữ write và nhả từng khóa ở các scope khác nhau dễ tạo lỗi:

- Nếu chưa nhận được read lock mà đã nhả write, không được giả định caller đang giữ read.
- Nếu đã nhận read lock, phải có `finally` tương ứng để nhả read khi không còn cần.
- Nếu đang giữ nhiều write hold do reentrancy, phải nhả đủ số write hold mới thực sự hết quyền ghi.
- Không chuyển ownership của lock cho thread khác; thread lấy khóa chịu trách nhiệm nhả khóa tương ứng.

Có thể gói chuyển trạng thái vào một abstraction nội bộ đã kiểm thử thay vì để nhiều caller tự tái hiện chuỗi hạ cấp.

## 6. Kết hợp double-check với downgrade cho cache

Một ứng dụng phổ biến: đọc cache; nếu không hợp lệ, yêu cầu quyền ghi để làm mới; sau đó tiếp tục xử lý dữ liệu dưới read lock.

~~~text
read.lock
  cache valid? -- yes --> use under read --> read.unlock
       |
       no
       v
read.unlock
write.lock
  recheck valid? -- no --> refresh
  read.lock (downgrade)
write.unlock
  use under read
read.unlock
~~~

Đoạn sau là **mẫu code minh họa** trong class có field `ReentrantReadWriteLock rw`, `boolean valid` và `Object data`. Giả sử `refresh()` tạo dữ liệu mà các reader cần; không phải file Java độc lập.

~~~java
Object useCache() {
    rw.readLock().lock();
    try {
        if (valid) {
            return data;
        }
    } finally {
        rw.readLock().unlock();
    }

    rw.writeLock().lock();
    try {
        if (!valid) { // Điều kiện phải được kiểm tra lại.
            data = refresh();
            valid = true;
        }
        rw.readLock().lock(); // Giữ read trước khi nhả write.
    } finally {
        rw.writeLock().unlock();
    }

    try {
        return data;
    } finally {
        rw.readLock().unlock();
    }
}
~~~

Cách này **hạ cấp đúng chiều** và không cho writer khác chen vào giữa việc làm mới với lúc bắt đầu đọc phiên bản mới. Nếu `refresh()` ném exception **trước khi lấy read lock**, block `finally` nhả write và ngoại lệ truyền ra; code sẽ không chạy tới đoạn đọc cuối. Với tác vụ phức tạp, cân nhắc việc giá trị `data` mutable có được sửa bên ngoài khóa hay không: trả reference rồi dùng nó sau khi nhả read lock có thể không còn snapshot ổn định nếu object tiếp tục bị writer sửa. Có thể trả bản bất biến, sao chép dữ liệu dưới khóa hoặc giữ xử lý cần nhất quán hoàn toàn trong phạm vi read lock.

**Điểm tinh tế:** hàm trên trả reference trong khối `try`, nhưng `finally` nhả read lock trước khi caller nhận và sử dụng object. Nếu caller cần truy cập nhiều thuộc tính mutable nhất quán sau đó, chỉ trả reference **chưa đủ**; cần có hợp đồng bất biến hoặc copy.

## 7. Reentrancy không phải upgrade

`ReentrantReadWriteLock` cho phép cùng thread:

- Đang giữ **read** tiếp tục `read.lock()` và phải `unlock()` đúng số lần.
- Đang giữ **write** tiếp tục `write.lock()`, tăng write hold count.
- Đang giữ **write** lấy thêm **read**; đây là cơ sở của downgrade.

Nó **không cho** thread chỉ đang giữ read nâng lên write. Ngay cả khi tự nghĩ mình “đang là reader duy nhất”, việc yêu cầu write có thể bị kẹt vì chính read hold của thread đó.

Đừng áp dụng trực giác “ReentrantLock cho phép cùng thread lấy lại khóa” cho một chuyển đổi **read → write**: hai quyền này khác nhau.

### Cùng thread giữ read và write, có phải writer khác được phép chạy?

Không. Khi thread chủ đang giữ write, các reader/writer khác bị chặn. Khi thread chủ nhả hết write nhưng còn read, các reader khác có thể cùng vào (tùy fairness), còn writer khác chưa thể sửa dữ liệu. Chính sự chuyển dịch quyền này tạo ra nghĩa của downgrade.

### Tại sao cần nhả đúng số lần?

Ví dụ minh họa:

~~~java
rw.writeLock().lock();
rw.writeLock().lock();
try {
    // Write hold count = 2 trong cùng thread.
} finally {
    rw.writeLock().unlock();
    rw.writeLock().unlock();
}
~~~

Nếu bỏ một lần unlock, thread vẫn giữ write lock và có thể chặn mọi reader/writer khác, tạo lỗi liveness rất khó phát hiện. Đoạn này không phải file độc lập; `rw` giả định đã được khai báo.

## 8. Fairness, tryLock và khả năng đói tài nguyên

### 8.1. Hai chế độ

~~~java
ReentrantReadWriteLock nonfair = new ReentrantReadWriteLock();
ReentrantReadWriteLock fair = new ReentrantReadWriteLock(true);
~~~

**Nonfair (mặc định):** thứ tự cấp quyền không được bảo đảm; thông lượng thường cao hơn, nhưng khi liên tục tranh chấp, reader hoặc writer có thể bị trì hoãn rất lâu, thậm chí vô thời hạn.

**Fair:** JDK áp dụng chính sách **xấp xỉ theo thứ tự đến** cho các thread đang tranh chấp, nhóm reader và writer dựa vào những người đang chờ lâu nhất. Fair mode có thể giảm nguy cơ starvation, nhưng có thêm overhead và **không bảo đảm fairness của scheduler**, cũng không cho một deadline cụ thể cho mọi tác vụ.

Quan trọng: **`readLock().tryLock()` và `writeLock().tryLock()` không tham gia chính sách fair** theo cách chờ thông thường; chúng có thể lập tức lấy khóa nếu khả dụng, dù có thread đang chờ (barging). Với yêu cầu tuân thủ fair khi thử không chờ, các overload `tryLock(0, TimeUnit.SECONDS)` có semantics gần tương ứng và xử lý interrupt.

### 8.2. Đừng chọn lock chỉ bằng nhãn fair

Các yếu tố cần đo:
- Tỷ lệ read/write, thời gian giữ từng lock, mức contention.
- Độ lớn công việc trong critical section.
- Cần deadline cho tác vụ không? `tryLock(timeout, unit)` hay `lockInterruptibly()` có thể hữu ích.
- Độ trễ tail, khả năng writer bị đói, overhead khi chuyển đổi hoặc recheck.

Một read/write lock có thể chậm hơn `synchronized` hoặc `ReentrantLock` thường nếu critical section rất nhỏ, số reader thấp, write thường xuyên hoặc contention lớn. Ưu thế nhiều reader song song chỉ có giá trị khi **đọc đủ nhiều và phần việc dưới read lock đủ đáng kể**.

### 8.3. Interrupt và timeout

`readLock()` và `writeLock()` đều hỗ trợ `lockInterruptibly()`/`tryLock(timeout, unit)`. Không được `unlock()` nếu không lấy được khóa:

~~~java
// Mã minh họa: write là Lock đã khai báo.
if (write.tryLock(100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
    try {
        // Thực hiện cập nhật.
    } finally {
        write.unlock();
    }
} else {
    // Hết thời hạn mà chưa nhận write lock.
}
~~~

Phương thức trong đoạn minh họa phải khai báo hoặc xử lý `InterruptedException`. Timeout không hứa sẽ có quyền ghi, nên caller phải có phương án hủy, retry hoặc báo không hoàn thành theo nhu cầu.

## 9. Conditions: chỉ write lock hỗ trợ

Một sự khác biệt quan trọng với `ReentrantLock` đơn lẻ là **Condition**:

~~~java
ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
Condition condition = rw.writeLock().newCondition(); // Hợp lệ.
rw.readLock().newCondition(); // UnsupportedOperationException.
~~~

Trong đoạn minh họa trên, cần import `java.util.concurrent.locks.Condition` nếu dùng trong file thật. `Condition` tạo từ write lock có hợp đồng điều kiện tương tự `ReentrantLock`; thông thường chỉ gọi `await()`/`signal()` khi đang giữ đúng write lock liên quan. `readLock().newCondition()` **không được hỗ trợ**.

### Ví dụ độc lập kiểm tra giới hạn

~~~java
// ConditionSupportDemo.java
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConditionSupportDemo {
    public static void main(String[] args) {
        ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
        Condition valid = rw.writeLock().newCondition();
        System.out.println("Write condition created: " + (valid != null));

        try {
            rw.readLock().newCondition();
            System.out.println("Unexpected support");
        } catch (UnsupportedOperationException e) {
            System.out.println("Read condition unsupported");
        }
    }
}
~~~

~~~bash
javac ConditionSupportDemo.java
java ConditionSupportDemo
~~~

Stdout:

~~~text
Write condition created: true
Read condition unsupported
~~~

Nếu cần điều kiện chờ nhiều reader/writer phối hợp, nên thiết kế rõ predicate và lock sở hữu condition. Khi dùng `await()`, luôn kiểm tra lại predicate trong vòng `while` vì có thể thức dậy mà điều kiện chưa đúng; đừng đồng bộ bằng cách đợi condition của read lock vốn không tồn tại.

## 10. So sánh với ReentrantLock, StampedLock và concurrent collections

| Công cụ | Điểm mạnh | Điều cần chú ý |
| --- | --- | --- |
| `ReentrantLock` | Mutual exclusion đơn giản, reentrant, condition và nhiều kiểu chờ | Không cho các reader đồng thời trong critical section |
| `ReentrantReadWriteLock` | Nhiều reader đồng thời, writer độc quyền, **downgrade write→read** | **Không upgrade read→write**, overhead/fairness/conditions |
| `StampedLock` (JDK 8+) | Read/write stamps, optimistic reads, một số `tryConvertToWriteLock`/`tryConvertToReadLock` | **Không reentrant**; conversion có thể thất bại; optimistic read bắt buộc validate, không coi stamp là monitor thông thường |
| `ConcurrentHashMap` | Cung cấp thao tác concurrent trên map, một số API atomic theo key | Chuỗi thao tác của caller không tự atomic; đọc/ghi nhiều key có thể cần thiết kế bổ sung |
| Immutable snapshot với atomic reference | Reader lấy phiên bản bất biến, writer thay reference nguyên tử | Logic cập nhật nhiều writer phải dùng CAS; object phải thực sự bất biến |

**StampedLock không phải một cách “bật upgrade” cho ReentrantReadWriteLock.** Nó là primitive khác với hợp đồng khác. `tryConvertToWriteLock(stamp)` có thể thất bại khi không thể chuyển quyền; caller phải xử lý thất bại, bao gồm nhả stamp cũ đúng cách và có thể thử chiến lược mới. `StampedLock` cũng không reentrant và optimistic read không đảm bảo snapshot hợp lệ nếu không xác thực bằng `validate`.

Nếu logic chỉ cần một lần đọc/ghi nhanh trên map, API atomic của collection có thể đơn giản hơn một lock lớn. Nhưng việc lựa chọn phải theo **bất biến**, không chỉ theo số lượng reader.

## 11. Những lỗi phổ biến

- **“Đang là reader duy nhất thì read.lock → write.lock được.”** Sai: việc nâng cấp trực tiếp không được hỗ trợ, có thể tự chặn vô hạn.
- **“Fair=true sửa lỗi upgrade.”** Sai: fairness không thay đổi quy tắc sở hữu/upgrade.
- **“Nhả read rồi lấy write là atomic upgrade.”** Sai: thread khác có thể thay đổi dữ liệu trong khoảng trống, bắt buộc recheck.
- **“write.unlock() rồi read.lock() là downgrade an toàn.”** Sai: writer khác có thể chen vào giữa hai lời gọi.
- **“Reentrant nghĩa là unlock một lần sau nhiều lần lock.”** Sai: phải cân bằng số lần giữ lock.
- **“Chỉ writer cần khóa; reader đọc field thường ở ngoài cũng được.”** Sai: reader phải dùng cùng giao thức để thấy dữ liệu/snapshot đúng.
- **“readLock.newCondition() dùng được như writeLock.newCondition().”** Sai: read condition ném `UnsupportedOperationException`.
- **“tryLock tuân thủ hoàn toàn fair=true.”** Sai với untimed `tryLock()`.
- **“ReadWriteLock luôn nhanh hơn synchronized.”** Không có bảo đảm; cần đo theo workload.
- **“getReadLockCount() là primitive để quyết định nâng cấp.”** Sai: đây là số liệu theo dõi, không tạo quyền chuyển khóa nguyên tử.
- **“Đã hạ cấp thì reader khác không bao giờ vào.”** Sai: sau khi nhả write và còn read, những reader khác có thể được phép đồng thời đọc (tùy policy); writer khác vẫn chưa thể ghi.
- **“Giữ khóa trong lúc gọi mã ngoài lâu cũng không ảnh hưởng.”** Sai: có thể tăng contention, latency và deadlock.

## 12. Quy trình kiểm tra code

1. **Chỉ ra trạng thái chia sẻ và invariant:** cache, map, bộ đếm, các field phải đọc nhất quán.
2. **Đánh dấu đoạn read-only và đoạn write:** có thao tác nào trong read section thực ra làm thay đổi object mutable không?
3. **Tìm dấu hiệu upgrade:** code giữ `readLock` rồi gọi `writeLock.lock`/`lockInterruptibly` hoặc `tryLock`? Nếu có, thiết kế lại nhả read và recheck.
4. **Tìm dấu hiệu downgrade:** có lấy **read trước khi nhả write** không? Có nhả read cuối cùng trong `finally` không?
5. **Kiểm tra reentrant hold count:** có nhiều lần lock nhưng thiếu unlock? Caller đã giữ một khóa từ scope bên ngoài chưa?
6. **Đảm bảo mọi reader/writer dùng cùng rw instance:** đừng tạo lock mới trên mỗi lời gọi hoặc trả reference lock ra API khi không chủ ý.
7. **Kiểm tra fairness và liveness:** nonfair có nguy cơ trì hoãn, fair có overhead, timed tryLock cần xử lý timeout/interrupt.
8. **Kiểm tra Condition:** chỉ viết condition từ write lock, predicate kiểm tra trong vòng `while`.
9. **Đo hành vi:** so throughput/latency so với `ReentrantLock`/`synchronized` dưới workload thực tế; không mặc định read/write lock sẽ tốt hơn.
10. **Dùng thread dump khi bị kẹt:** phân biệt thread đang chờ upgrade, deadlock do nhiều lock, hay contention bình thường.

## 13. Tự kiểm tra

**1.** Thread T đang giữ read lock, là reader duy nhất. Gọi `writeLock().lock()` có upgrade thành công không?  
**Đáp án:** Không. `ReentrantReadWriteLock` không hỗ trợ nâng cấp read→write; code có thể tự chờ vô hạn.

**2.** Sau khi phát hiện cache không hợp lệ dưới read lock, vì sao phải kiểm tra lại khi đã lấy write lock?  
**Đáp án:** Vì trong khoảng nhả read và chờ write, thread khác có thể đã làm mới cache; điều kiện cũ không còn đáng tin.

**3.** Cần đọc ngay phiên bản vừa ghi mà không cho writer khác chen vào. Thứ tự đúng?  
**Đáp án:** `write.lock → cập nhật → read.lock → write.unlock → đọc → read.unlock`.

**4.** `rw.writeLock().newCondition()` và `rw.readLock().newCondition()` khác gì?  
**Đáp án:** Write lock hỗ trợ `Condition`; read lock ném `UnsupportedOperationException`.

**5.** Với `new ReentrantReadWriteLock(true)`, `tryLock()` có tôn trọng hoàn toàn chính sách fair không?  
**Đáp án:** Không. Untimed `tryLock()` có thể barging khi khóa khả dụng.

**6.** Nếu chỉ có đọc rất ngắn và ghi thường xuyên, read/write lock chắc chắn nhanh hơn `synchronized`?  
**Đáp án:** Không; overhead quản lý readers/writer có thể cao hơn lợi ích. Cần benchmark thực tế.

**7.** Nếu thread giữ write lock rồi lấy read lock, nhả write thì thread đó còn giữ quyền nào?  
**Đáp án:** Còn read lock; có thể đọc tiếp. Writer khác không được cập nhật cho tới khi toàn bộ read holds liên quan được nhả.

**8.** Dùng `StampedLock.tryConvertToWriteLock` có luôn nâng cấp thành công?  
**Đáp án:** Không. Conversion có thể trả 0 khi không thể đổi quyền; phải xử lý thất bại và vòng đời stamp đúng cách.

**9.** Một thread đọc `Map` mà không giữ read lock, trong khi writer cập nhật dưới write lock. Đã thread-safe chưa?  
**Đáp án:** Chưa, nếu map là mutable và không có giao thức đồng bộ khác. Reader phải tuân thủ lock hoặc dùng collection/thiết kế khác có bảo đảm phù hợp.

**Ghi nhớ:** `ReentrantReadWriteLock` chia quyền **nhiều reader / một writer**. **Upgrade read→write không hỗ trợ**; nhả read rồi lấy write phải **recheck**. **Downgrade write→read có hỗ trợ**, nhưng phải **lấy read trước khi nhả write**. Tính đúng phụ thuộc bất biến và mọi đường truy cập; hiệu năng, fairness, conditions và reentrancy là những giới hạn cần kiểm chứng.

### Tài liệu tham khảo chuẩn

- Java 17 `ReentrantReadWriteLock` (reentrancy, downgrading, fairness, conditions): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantReadWriteLock.html
- Java 17 `ReadWriteLock` (memory consistency, sharing semantics): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReadWriteLock.html
- Java 17 `Lock`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Lock.html
- Java 17 `StampedLock`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/StampedLock.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-and-conditions-010">StampedLock với optimistic read</a>

<details>
<summary>Xem chi tiết</summary>

# StampedLock với optimistic read trong Java

## 1. Vấn đề: đọc thường xuyên, ghi ít nhưng vẫn phải nhất quán

Giả sử nhiều thread thường xuyên đọc tọa độ của một điểm, trong khi một thread khác thỉnh thoảng cập nhật cả `x` và `y`. Ta muốn giảm tranh chấp khi đọc, nhưng phải tránh lấy `x` của lần cập nhật mới và `y` của lần cập nhật cũ.

`java.util.concurrent.locks.StampedLock` là một cơ chế đồng bộ của JDK hỗ trợ **ba chế độ**: khóa đọc (*read lock*), khóa ghi (*write lock*) và đọc lạc quan (*optimistic read*). Điểm đặc biệt là optimistic read **không giữ một read lock thực sự**. Reader lấy một **stamp**, đọc dữ liệu vào biến cục bộ rồi gọi `validate(stamp)` để xem một writer có làm mất hiệu lực bản đọc hay không. Nếu validation thất bại, reader phải **đọc lại dưới read lock thực sự** trước khi sử dụng kết quả.

Mẫu chuẩn:

```java
long stamp = lock.tryOptimisticRead();
int localX = x;
int localY = y;

if (!lock.validate(stamp)) {
    stamp = lock.readLock();
    try {
        localX = x;
        localY = y;
    } finally {
        lock.unlockRead(stamp);
    }
}
// Chỉ dùng localX, localY sau bước validate hoặc fallback.
```

Đây là **snippet minh họa** trong một class đã có `lock`, `x`, `y`, không phải chương trình độc lập. Phần ví dụ bên dưới có đầy đủ class và `main` để chạy bằng Java 17+.

### Lợi ích và giới hạn

- **Lợi ích có thể có:** khi writer hiếm, reader optimistic không phải đăng ký chiếm read lock và có thể giảm overhead/tranh chấp so với một read lock truyền thống.
- **Giới hạn:** nếu writer đang hoặc vừa thay đổi dữ liệu, validation có thể thất bại; reader phải đọc lại và chịu thêm chi phí.
- **Không phải phép màu:** optimistic read không tự đồng bộ toàn bộ object, không khóa writer, không bảo đảm luôn nhanh hơn, không cấp quyền truy cập một cấu trúc mutable tùy ý mà không kiểm tra.
- **Chỉ nên dùng khi dữ liệu đọc thử an toàn:** ưu tiên các trường primitive hoặc tham chiếu được xử lý thận trọng, sao chép vào biến cục bộ và **không thực hiện tác dụng phụ dựa trên dữ liệu chưa xác minh**.

Đây là kiến thức Java concurrency nói chung, sử dụng API JDK, không gắn với bất kỳ framework nào.

## 2. Ba chế độ của `StampedLock`

| Chế độ | API lấy stamp | Điều nó bảo đảm | Cách kết thúc |
| --- | --- | --- | --- |
| **Write lock** | `writeLock()` | Loại trừ writer khác và reader dùng read lock; bảo vệ cập nhật trạng thái | `unlockWrite(stamp)` |
| **Read lock** | `readLock()` | Có thể nhiều reader cùng giữ; ngăn writer lấy write lock trong lúc còn giữ | `unlockRead(stamp)` |
| **Optimistic read** | `tryOptimisticRead()` | Lấy một dấu hiệu phiên bản để xác minh bản đọc; **không khóa writer** | `validate(stamp)`; **không unlock optimistic stamp** |

`StampedLock` dùng giá trị `long` làm **stamp**. Stamp nhận được từ các thao tác khóa là một **khả năng (capability) đại diện cho lần lấy khóa đó**, không chỉ là số đếm mà chương trình được phép tùy ý sửa.

- `readLock()` và `writeLock()` có thể chặn cho đến khi lấy được khóa; khi đã lấy thành công, caller phải nhả đúng chế độ bằng stamp phù hợp.
- `tryOptimisticRead()` **không chặn** để chờ writer; nó có thể trả về `0L` nếu write lock đang được giữ.
- `validate(stamp)` trả về `false` khi stamp không còn hợp lệ theo trạng thái đồng bộ của lock, kể cả trường hợp optimistic stamp bằng 0. Một lần writer lấy write lock có thể làm stamp lạc quan cũ mất hiệu lực.
- Không gọi `unlockRead` hoặc `unlockWrite` với optimistic stamp: nó **không đại diện cho một lock đã được giữ**.

**Một nguyên tắc khó nhầm:** một optimistic read thành công không có nghĩa reader “giữ” thứ gì để ngăn writer. Writer có thể bắt đầu cập nhật bất cứ lúc nào; reader chỉ chấp nhận những giá trị đã sao chép **sau khi** việc xác minh stamp thành công. Mọi truy cập tiếp theo với state chung cần giao thức đồng bộ mới.

## 3. Quy trình optimistic read đúng từng bước

1. **Nhận stamp:** `long stamp = lock.tryOptimisticRead()`. Đây không phải `readLock()`.
2. **Đọc thử vào local:** đọc các trường cần thiết (ví dụ `x`, `y`) và không để các giá trị ấy điều khiển side effect.
3. **Xác minh:** `lock.validate(stamp)` kiểm tra tính hợp lệ của thời điểm đọc lạc quan. Nếu writer đã làm mất hiệu lực stamp, không được sử dụng snapshot đọc thử.
4. **Fallback:** khi validation là `false`, lấy `readLock()`, đọc lại các trường **bên trong critical section**, rồi `unlockRead(stamp)` trong `finally`.
5. **Sử dụng snapshot:** sau khi xác minh/fallback, chỉ sử dụng các giá trị **local đã chụp**, hoặc một immutable snapshot đã tạo từ chúng. Không đọc tiếp trường chia sẻ ngoài lock rồi coi đó là một phần của snapshot cũ.

**Lỗi điển hình:** làm phép tính trên shared fields **sau** khi validate:

```java
// Sai về mặt ý tưởng: tính lại từ shared state sau validate.
long stamp = lock.tryOptimisticRead();
int localX = x;
int localY = y;
if (lock.validate(stamp)) {
    return x + y; // Đã đọc lại x, y sau validate.
}
```

Đây là snippet minh họa, không có phương thức/class bao quanh. Sau `validate`, một writer có thể đã bắt đầu; lần đọc `x`/`y` mới không thuộc snapshot được kiểm tra. Sửa bằng `return localX + localY` hoặc trả immutable value từ **các local đã xác minh**.

### Tại sao phải giữ local?

Nếu trước khi validate writer sửa `x` rồi `y`, reader có thể tạm thấy tổ hợp chưa nhất quán. Validation thất bại yêu cầu **bỏ** những local ấy và đọc lại dưới lock. Nếu validation thành công, bản đọc được chấp nhận theo giao thức optimistic read của `StampedLock`. Kể cả khi writer đến **ngay sau** validation, snapshot local vẫn đại diện cho dữ liệu đã được đọc trong một giai đoạn hợp lệ; nó không cần phản ánh bản cập nhật tương lai.

Tránh dereference sâu một chuỗi object mutable trong giai đoạn optimistic read: nếu tham chiếu thay đổi đồng thời, có thể gặp `NullPointerException`, index không hợp lệ, hoặc thao tác trên object đang được sửa **trước cả khi kịp validate**. Khi state phức tạp, dùng read lock thực sự hoặc thiết kế dữ liệu bất biến an toàn để đọc thử.

## 4. Ví dụ Java 17+ hoàn chỉnh: tọa độ với optimistic snapshot

Tạo file `StampedPoint.java`:

```java
import java.util.concurrent.locks.StampedLock;

public class StampedPoint {
    record Position(int x, int y) {}

    private final StampedLock lock = new StampedLock();
    private int x;
    private int y;

    public void moveTo(int newX, int newY) {
        long stamp = lock.writeLock();
        try {
            x = newX;
            y = newY;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public Position snapshot() {
        long stamp = lock.tryOptimisticRead();
        int localX = x;
        int localY = y;

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                localX = x;
                localY = y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return new Position(localX, localY);
    }

    public static void main(String[] args) {
        StampedPoint point = new StampedPoint();
        point.moveTo(4, 6);

        Position position = point.snapshot();
        System.out.println(position);
        System.out.println("Sum: " + (position.x() + position.y()));
    }
}
```

Biên dịch và chạy:

```bash
javac StampedPoint.java
java StampedPoint
```

Đầu ra xác định:

```text
Position[x=4, y=6]
Sum: 10
```

Giải thích:

- `moveTo` lấy write lock, thay đổi **cả hai trường** trong cùng critical section, và nhả khóa trong `finally`.
- `snapshot` lấy optimistic stamp, chụp `x` và `y` vào local. Nếu stamp đã bị writer làm mất hiệu lực, nó chuyển sang **read lock** để đọc lại cho nhất quán.
- Kết quả là `record Position` bất biến theo hai trường primitive, không lộ state mutable của object.
- Ví dụ `main` tuần tự nên kết quả cố định. Trong chương trình concurrent, snapshot có thể là tọa độ trước hoặc sau một lần `moveTo`, nhưng **không được chấp nhận cặp x/y bị trộn** nếu mọi writer đều dùng write lock của cùng `StampedPoint` và reader tuân thủ giao thức trên.

**Không được gộp hai bước “đọc dưới write lock” với “kiểm tra stamp” thành một suy đoán về tốc độ:** chỉ benchmark workload thực tế mới cho biết đọc optimistic có hiệu quả hơn `readLock` thông thường hay không.

## 5. Ví dụ Java 17+: bảo vệ invariant khi đọc/ghi đồng thời

Chúng ta có hai số dư nội bộ với bất biến `left + right == 100`. Writer liên tục chuyển một đơn vị từ left sang right; reader liên tục chụp snapshot. Một snapshot hợp lệ không được lấy left của một lần chuyển và right của lần khác.

Tạo file `StampedTransfer.java`:

```java
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;

public class StampedTransfer {
    record Balances(int left, int right) {
        int total() {
            return left + right;
        }
    }

    private final StampedLock lock = new StampedLock();
    private int left = 100;
    private int right = 0;

    public void moveOne() {
        long stamp = lock.writeLock();
        try {
            if (left > 0) {
                left--;
                right++;
            }
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public Balances snapshot() {
        long stamp = lock.tryOptimisticRead();
        int observedLeft = left;
        int observedRight = right;

        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try {
                observedLeft = left;
                observedRight = right;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        return new Balances(observedLeft, observedRight);
    }

    public static void main(String[] args) throws InterruptedException {
        StampedTransfer book = new StampedTransfer();
        AtomicBoolean valid = new AtomicBoolean(true);

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                book.moveOne();
            }
        }, "writer");

        Thread reader = new Thread(() -> {
            for (int i = 0; i < 5_000; i++) {
                if (book.snapshot().total() != 100) {
                    valid.set(false);
                }
            }
        }, "reader");

        writer.start();
        reader.start();
        writer.join();
        reader.join();

        System.out.println("Consistent snapshots: " + valid.get());
        System.out.println("Final balances: " + book.snapshot());
    }
}
```

Chạy:

```bash
javac StampedTransfer.java
java StampedTransfer
```

Đầu ra khi hai worker hoàn tất bình thường:

```text
Consistent snapshots: true
Final balances: Balances[left=0, right=100]
```

**Tại sao ví dụ có kết quả xác định dù scheduling bất định?**

- Mỗi lần `moveOne` giữ write lock từ lúc đọc `left` đến hết khi cập nhật `right`, nên không có hai writer hoặc read-lock reader nào xen vào đoạn sửa.
- Optimistic reader có thể đọc giữa hai phép ghi; khi một writer đã bắt đầu lấy write lock sau khi reader nhận stamp, `validate` không chấp nhận snapshot đó. Reader sẽ **đọc lại cả hai trường dưới read lock**.
- Snapshot là `record Balances` gồm hai số nguyên bất biến; phép tính total chỉ chạy trên hai local đã xác minh hoặc đọc dưới read lock.
- Writer thực hiện đủ 100 lần chuyển khi ban đầu `left == 100`. Vì vậy cuối cùng `left == 0`, `right == 100`, không phụ thuộc lịch chạy.
- `join()` làm main đợi đủ hai worker. `AtomicBoolean` ghi lại một cách an toàn việc có phát hiện snapshot sai hay không.

Nếu writer không sử dụng **cùng `StampedLock`**, bảo đảm trên không còn. Một direct write vào `left` hoặc `right` không tuân thủ khóa phá vỡ hợp đồng dù phần reader vẫn dùng validate.

**Phạm vi:** đây là trạng thái trong **một instance trong một JVM**. Nó không tự giải quyết cập nhật dữ liệu đến từ process/JVM hoặc dịch vụ khác.

## 6. Tại sao `validate` quan trọng với Java Memory Model?

Đọc optimistic **không giống** một read lock đầy đủ. Trong giai đoạn đọc thử, reader không giữ monitor/read lock để buộc writer đứng ngoài. Những lần đọc từng trường có thể quan sát giá trị ở các thời điểm khác nhau.

Giao thức của `StampedLock` dùng stamp/validation để xác định có thể chấp nhận bản đọc hay phải fallback. Bởi vậy:

- Chỉ **chụp các giá trị cần thiết**, không công bố chúng trước khi xác minh.
- Không dựa vào việc các trường riêng lẻ đều là `int` nguyên tử để kết luận cặp `(x,y)` nhất quán.
- Không để writer sửa state **ngoài write lock** của cùng object.
- Không tự thực hiện side effect như gửi sự kiện, trừ tiền hoặc cập nhật map dựa trên giá trị **chưa validate**.
- Nếu validation thất bại, đọc lại **từ đầu** dưới read lock, thay vì kết hợp trường mới với trường cũ.

Với các trường tham chiếu, một lần đọc tham chiếu rồi dereference object phức tạp có thể gây lỗi **trước khi** đến được `validate` nếu object thay đổi và cấu trúc nội bộ không an toàn. Đó là lý do các ví dụ trong bài chủ động dùng các trường primitive đơn giản và công bố snapshot dưới dạng immutable record.

`validate(stamp)` không “đóng băng” state sau khi trả true. Sau khi validate thành công, dữ liệu chung có thể lại thay đổi. **Chỉ snapshot local đã chụp** là dữ liệu ta có thể dùng như kết quả của lần đọc; một phép đọc bổ sung từ shared fields là thao tác mới và cần bảo vệ riêng.

## 7. Đọc lạc quan không phải là optimistic locking cho mọi tình huống

Dễ nhầm hai loại “optimistic”:

1. **`StampedLock.tryOptimisticRead()`:** cho phép đọc thử các trường mà không giữ read lock, sau đó dùng `validate` để xem có thể tin vào snapshot không. Nếu không, đọc lại dưới read lock.
2. **Optimistic update/CAS:** đọc giá trị, chuẩn bị giá trị mới và dùng `compareAndSet` để bảo đảm trạng thái chưa đổi trước khi cập nhật. Đây là **phép cập nhật có điều kiện**, khác với xác minh một snapshot chỉ đọc.
3. **Optimistic concurrency trên hệ thống lưu trữ:** có thể dùng version hoặc conditional update tại nguồn chân lý; nó không tự liên quan đến stamp của `StampedLock` trong JVM.

**Không được** dùng `tryOptimisticRead()` → `validate()` → sau đó sửa field như thể đã lấy write lock. Writer khác có thể chen vào ngay sau validate, và bản thân optimistic stamp **không trao quyền ghi**. Muốn sửa state được bảo vệ, phải lấy `writeLock()` (hoặc chuyển đổi từ một lock hiện có theo API đúng) rồi **kiểm tra lại điều kiện dưới write lock**.

## 8. `StampedLock` không reentrant: bẫy rất quan trọng

Khác với intrinsic monitor của `synchronized` và `ReentrantLock`, **`StampedLock` không có tính tái nhập**.

Tình huống nguy hiểm:

```java
// Snippet nguy hiểm: cùng thread đã giữ write lock.
long stamp = lock.writeLock();
try {
    helper();
} finally {
    lock.unlockWrite(stamp);
}

// helper() lại gọi lock.writeLock() trên chính lock đó.
```

Đây là snippet minh họa một thiết kế nguy hiểm, không phải chương trình có thể chạy độc lập. Nếu `helper` gọi `writeLock()` lần nữa trong khi thread hiện tại đang giữ write lock, nó có thể **tự chặn vô thời hạn**. `StampedLock` không theo dõi owner theo cách cho phép reentry.

Các bẫy tương tự:

- Giữ write lock rồi gọi một getter tự lấy read lock của cùng `StampedLock`.
- Giữ read lock rồi gọi setter yêu cầu write lock mà không giải phóng/chuyển đổi hợp lệ.
- Giữ read lock rồi chờ worker cần write lock mới có thể hoàn tất.
- Một helper nội bộ luôn tự lấy lock dù caller đã giữ một stamp hợp lệ.

**Cách thiết kế:** chia rõ public methods chịu trách nhiệm lấy khóa và private helpers yêu cầu caller đã có khóa. Có thể truyền state/snapshot hoặc thao tác trực tiếp bên trong vùng đang khóa, nhưng phải quy định tiền điều kiện của helper. Không gọi lại public getter/setter có thể tự chặn khi một stamp còn đang được giữ.

## 9. Chuyển đổi stamp bằng `tryConvertToWriteLock`

`StampedLock` hỗ trợ thử chuyển đổi giữa các chế độ thông qua `tryConvertToWriteLock(stamp)`, `tryConvertToReadLock(stamp)` và `tryConvertToOptimisticRead(stamp)` theo điều kiện hợp lệ của stamp và trạng thái lock.

**Quan trọng:** `tryConvertToWriteLock(stamp)` có thể trả về **`0L` nếu thất bại**. Không được giả định chuyển đổi luôn thành công. Khi thành công, phải dùng **stamp được trả về** cho các thao tác tương ứng; khi thất bại, **stamp gốc không tự được nhả**, và caller phải xử lý việc giải phóng hoặc thử lại theo giao thức của mình.

Ví dụ tình huống: cần đảm bảo cache được khởi tạo đúng một lần. Bắt đầu dưới read lock để kiểm tra `initialized`; nếu chưa, thử chuyển sang write lock; nếu thất bại thì nhả read lock, lấy write lock và **kiểm tra lại điều kiện** vì thread khác có thể đã khởi tạo trong khoảng chuyển khóa.

### Java 17+: khởi tạo một lần bằng conversion có fallback

Tạo file `StampedInitialize.java`:

```java
import java.util.concurrent.locks.StampedLock;

public class StampedInitialize {
    private final StampedLock lock = new StampedLock();
    private boolean initialized;
    private int value;
    private int initializationCount;

    public int getOrInitialize() {
        long stamp = lock.readLock();
        try {
            while (!initialized) {
                long writeStamp = lock.tryConvertToWriteLock(stamp);
                if (writeStamp != 0L) {
                    stamp = writeStamp;
                    value = 42;
                    initializationCount++;
                    initialized = true;
                    break;
                }

                lock.unlockRead(stamp);
                stamp = lock.writeLock();
                // Phải kiểm tra lại initialized khi đã có write lock.
            }
            return value;
        } finally {
            lock.unlock(stamp);
        }
    }

    public int initializationCount() {
        long stamp = lock.readLock();
        try {
            return initializationCount;
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        StampedInitialize cache = new StampedInitialize();
        int[] results = new int[2];

        Thread a = new Thread(() -> results[0] = cache.getOrInitialize());
        Thread b = new Thread(() -> results[1] = cache.getOrInitialize());
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("Values: [" + results[0] + ", " + results[1] + "]");
        System.out.println("Initialization count: "
                + cache.initializationCount());
    }
}
```

Chạy:

```bash
javac StampedInitialize.java
java StampedInitialize
```

Đầu ra khi chương trình hoàn tất bình thường:

```text
Values: [42, 42]
Initialization count: 1
```

**Giải thích vòng lặp:**

- Nếu read stamp chuyển sang write stamp thành công, thread đó có quyền cập nhật và đánh dấu `initialized = true`.
- Nếu chuyển đổi thất bại, thread nhả **read lock** trước, sau đó lấy **write lock**. Khoảng chuyển có thể cho thread khác khởi tạo; vì vậy điều kiện `while (!initialized)` phải được đánh giá lại **sau** khi đã có write lock.
- Nếu đã lấy write lock nhưng `initialized` vẫn false, lần gọi `tryConvertToWriteLock` với write stamp hợp lệ cho phép tiếp tục với write mode. Nếu thread khác đã khởi tạo, vòng lặp bỏ qua bước ghi.
- `lock.unlock(stamp)` trong `finally` dùng **stamp hiện hành**, cho dù lúc đó đang ở read mode hay write mode; API `unlock(stamp)` hỗ trợ nhả lock theo stamp hợp lệ của chế độ đang giữ. Chỉ nhả đúng một lần cho acquisition còn hiệu lực.

Đây là một ví dụ có chủ đích để giới thiệu conversion. Khi nhu cầu khởi tạo một lần đơn giản, `synchronized` hoặc một immutable holder có thể dễ bảo trì hơn. Conversion không phải lựa chọn mặc định cho mọi getter có thể ghi.

## 10. Các API đọc/ghi liên quan và cách dùng stamp an toàn

`StampedLock` còn có `tryReadLock()`, `tryWriteLock()`, các overload timeout, `readLockInterruptibly()` và `writeLockInterruptibly()`. Các API này phục vụ **lấy khóa thực**; đừng nhầm với `tryOptimisticRead()` là phép lấy dấu hiệu đọc không khóa.

Các nguyên tắc thực hành:

- **Lock acquisition trả stamp dùng một lần theo vòng đời giữ khóa.** Không lưu stamp lâu dài vào cache để dùng lại sau khi nhả.
- `unlockRead(stamp)` chỉ dùng với read stamp đang giữ; `unlockWrite(stamp)` chỉ dùng với write stamp đang giữ. Dùng stamp không hợp lệ/không phù hợp có thể ném `IllegalMonitorStateException`.
- `unlock(stamp)` dùng được với read hoặc write stamp hợp lệ đang giữ, nhưng **không** dùng để nhả optimistic stamp.
- Không dùng stamp như số version nghiệp vụ, ID transaction hoặc token an toàn xuyên process.
- `StampedLock` không theo dõi ownership theo cách giống `ReentrantLock`; việc chuyển stamp qua thread khác về mặt API không giống một khóa reentrant theo thread. Trừ khi thiết kế thật sự cần và được kiểm chứng, nên để thành phần lấy khóa chịu trách nhiệm nhả nó trong cấu trúc `try/finally` rõ ràng.
- Nếu một thread bị interrupt khi đang giữ khóa, lock **không tự nhả**; đoạn code phải bảo đảm `finally` chạy. Các phương thức interruptible acquisition khai báo `InterruptedException` cần được xử lý hoặc truyền tiếp.
- Khi conversion thất bại (trả 0), **không** gọi unlock theo mode mà chưa lấy được; phải quản lý stamp đang giữ trước đó theo hợp đồng API.

## 11. Khi nào `StampedLock` có thể hiệu quả hơn?

**Thường đáng cân nhắc khi** số lần đọc rất lớn, ghi hiếm, snapshot gồm vài trường đơn giản, nội dung đọc thử rẻ và khả năng validate thành công cao. Vì optimistic read không phải tranh read lock với writer, nó có thể giảm chi phí ở một số hệ thống.

**Thường không phù hợp khi:**

- Writer thường xuyên cập nhật: validation thất bại nhiều, reader phải làm hai lần đọc và thêm fallback.
- Snapshot cần duyệt/đọc một collection mutable phức tạp: bản đọc thử có thể không an toàn **trước khi validate**.
- Các phương thức gọi lồng nhau cần reentrancy: `StampedLock` có thể tự chặn.
- Có nhiều thao tác cần điều kiện chờ: `StampedLock` không cung cấp trực tiếp `Condition` giống `ReentrantLock`.
- Khóa đọc/ngắn ít tranh chấp: overhead triển khai optimistic read có thể không đáng giá.
- Yêu cầu fairness hoặc giới hạn thời gian phản hồi nghiêm ngặt: `StampedLock` không cam kết chính sách fairness và có thể xuất hiện starvation khi contention; hãy đo và thiết kế timeout/cancellation phù hợp.
- State chỉ có một giá trị đơn có thể cập nhật bằng `AtomicReference` hoặc snapshot immutable: một thiết kế atomic có thể đơn giản hơn.

### So sánh với các cách đồng bộ khác

| Cách | Điểm mạnh | Giới hạn chính |
| --- | --- | --- |
| `synchronized` | Đơn giản, reentrant, tự nhả monitor khi thoát scope | Mọi reader cùng monitor có thể loại trừ nhau |
| `ReentrantReadWriteLock` | Nhiều reader song song, writer độc quyền, lock reentrant | Reader phải lấy read lock thực; writer có thể bị cạnh tranh |
| `StampedLock` read/write | Nhiều reader hoặc một writer, hỗ trợ conversion | Không reentrant; quản lý stamp thủ công |
| `StampedLock` optimistic read | Đọc thử không khóa; validate rồi fallback | Dữ liệu đọc thử cần an toàn, writer có thể gây retry |
| Immutable snapshot + `AtomicReference` | Reader lấy reference bất biến nhất quán, dễ reasoning | Writer có thể cần xây snapshot mới và CAS/retry |

**Không mặc định “StampedLock nhanh hơn”:** tốc độ còn tùy độ dài critical section, số reader/writer, core CPU, chi phí validation, tỷ lệ fallback, cấu trúc dữ liệu và chính JVM. Hãy benchmark với JMH hoặc workload đo thực tế; quan sát latency, throughput, lock contention và tỷ lệ validation thất bại.

## 12. Những lỗi phổ biến cần tránh

1. **“Optimistic read đã giữ khóa.”** Sai: nó không ngăn writer; bắt buộc validate.
2. **“`tryOptimisticRead()` luôn cho stamp hợp lệ.”** Sai: có thể trả `0L` khi write lock đang được giữ; validate sẽ không xác nhận stamp ấy.
3. **“Validate trước rồi mới đọc field.”** Sai: cần đọc dữ liệu vào local **trước**, validate **sau**, rồi chỉ dùng local đã kiểm tra.
4. **“Validate thành công thì field chia sẻ không thể thay đổi nữa.”** Sai: writer có thể bắt đầu sau validation; không đọc lại shared fields như phần của snapshot cũ.
5. **“Validation thất bại thì dùng tạm dữ liệu đã đọc.”** Sai: phải bỏ snapshot thử và fallback/read lại theo giao thức đúng.
6. **“Mọi object mutable đều có thể truy cập sâu rồi validate.”** Sai: dereference/index/duyệt collection có thể lỗi hoặc gây side effect trước khi validate.
7. **“StampedLock là ReentrantLock có thêm optimistic mode.”** Sai: `StampedLock` **không reentrant**.
8. **“Chuyển read → write luôn thành công.”** Sai: `tryConvertToWriteLock` có thể trả `0L`; nhả lock đúng và kiểm tra lại state sau khi lấy write lock.
9. **“Optimistic stamp cũng phải unlock.”** Sai: stamp này không biểu thị lock đang giữ.
10. **“Lấy hai lần read/write lock rồi unlock một lần là được.”** Không có reentrancy theo kiểu monitor; có thể kẹt hoặc làm sai vòng đời stamp.
11. **“Chỉ writer bên trong StampedLock là đủ.”** Sai nếu có code path khác đọc/ghi state bỏ qua policy bảo vệ.
12. **“Tăng số thread là benchmark chứng minh hiệu quả.”** Chưa đủ: cần so sánh cùng workload, có warm-up, tỉ lệ đọc/ghi đại diện và độ tin cậy thống kê.

## 13. Checklist khi áp dụng vào class Java thực tế

1. **Xác định invariant:** các trường nào phải nhất quán trong cùng một snapshot?
2. **Đóng gói state:** biến và `StampedLock` nên là private; mọi writer cùng sử dụng `writeLock()`.
3. **Quy định cách đọc:** đọc thử local, `validate(stamp)` và fallback `readLock()` nếu cần.
4. **Không side effect trước validate:** không in log quyết định nghiệp vụ, không sửa state hay gọi callback từ dữ liệu chưa hợp lệ.
5. **Kiểm soát tham chiếu mutable:** ưu tiên primitive và immutable snapshot, không duyệt collection mutable chưa được bảo vệ.
6. **Nhả đúng stamp:** write/read lock phải được unlock trong `finally`; optimistic stamp không unlock.
7. **Không giả định reentrancy:** xem lại helper gọi lồng nhau và các đường nâng cấp read → write.
8. **Conversion phải có đường thất bại:** nếu nhận `0L`, xử lý stamp cũ, lấy lock thích hợp rồi kiểm tra lại điều kiện.
9. **Xử lý lỗi/interrupt/timeout:** không để stamp bị thất lạc hoặc thread khác chờ vô hạn.
10. **Đo trước và sau:** tỷ lệ validation thành công, số lần fallback, contention, throughput và latency dưới tải đọc/ghi thực.

**Ghi nhớ:** `StampedLock.tryOptimisticRead()` cho phép **đọc thử không giữ read lock**, sau đó dùng `validate(stamp)` để xác minh snapshot. Nếu validation thất bại, hãy **đọc lại từ đầu dưới `readLock()`**. Luôn quản lý đúng stamp của write/read lock, tránh reentrancy và chỉ chọn optimistic read khi dữ liệu thử an toàn cùng workload thực tế cho thấy lợi ích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-and-conditions-011">Chi phí, độ phức tạp và tiêu chí chọn loại lock</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí, độ phức tạp và tiêu chí chọn loại lock trong Java

Trong Java Concurrency, câu hỏi “dùng `synchronized`, `ReentrantLock`, `ReadWriteLock` hay `StampedLock`?” không thể trả lời chỉ bằng việc đếm bao nhiêu thread truy cập dữ liệu. Mỗi cơ chế đưa ra một hợp đồng về **mutual exclusion**, **memory visibility**, **cách chờ và hủy**, **mức tranh chấp**, **tính công bằng**, và **độ khó chứng minh chương trình đúng**. Một lock phức tạp hơn không đồng nghĩa nhanh hơn; một lock đơn giản không mặc nhiên là nút thắt.

Cách chọn tốt là xác định **invariant cần bảo vệ**, **sự phối hợp phải diễn ra trong một critical section**, **chi phí nếu thread chờ**, rồi mới đo hiệu năng theo workload thật. Bài học này thuộc nhóm **Locks & Conditions** của **Java Concurrency nói chung**, không xét các cơ chế riêng của Spring.

## 1. Trước hết: lock giải quyết bài toán gì?

Khi nhiều thread cùng đọc/ghi một trạng thái mutable, việc bảo vệ đúng cần hai phương diện:

- **Mutual exclusion / atomicity:** một nhóm bước liên quan invariant diễn ra không bị thread khác chen vào. Ví dụ kiểm tra số dư rồi trừ tiền phải ở cùng transaction logic.
- **Happens-before / visibility:** lần unlock trên cùng monitor xảy ra trước lần lock tiếp theo; các `Lock` tiêu chuẩn có memory semantics tương ứng. Reader tuân thủ cùng protocol mới có cơ sở quan sát cập nhật đã công bố.

Ví dụ:

~~~java
private final Object lock = new Object();
private int available = 100;
private int reserved;

boolean reserve() {
    synchronized (lock) {
        if (available == 0) {
            return false;
        }
        available--;
        reserved++;
        return true;
    }
}
~~~

Đây là **mảnh mã của một class**, không phải chương trình chạy độc lập. Invariant ở đây là `available >= 0`, `reserved >= 0` và `available + reserved == 100`. Cả check, decrement và increment nằm trong cùng critical section.

Muốn đọc hai trường **như một snapshot**, reader cũng phải lấy cùng lock và đọc chúng trong một lần sở hữu. Hai getter `synchronized` được gọi lần lượt có thể trả giá trị thuộc hai thời điểm khác nhau nếu một writer chen giữa các lần gọi. Chỉ dùng `volatile` cho mỗi trường không biến hai phép ghi thành một cập nhật nguyên tử.

**Điểm khởi đầu:** chọn **giao thức sở hữu dữ liệu** trước khi chọn class lock. Nếu mỗi getter hoặc callback có thể bỏ qua lock, thay `synchronized` bằng `StampedLock` không sửa được lỗi.

## 2. “Chi phí của lock” gồm những gì?

Không có một con số overhead cố định cho mọi JVM. Chi phí phụ thuộc JDK, CPU, mức tranh chấp, critical section, cache layout và cách chạy benchmark.

| Loại chi phí | Nguyên nhân | Cách kiểm tra |
| --- | --- | --- |
| **Acquire / release** | Lệnh và rào cản đồng bộ/atomic operations cần thiết để sở hữu và nhả khóa. | Benchmark path ít tranh chấp, profiler phù hợp. |
| **Contention wait** | Một contender phải đợi holder hiện tại. | Blocked time, lock profiling, thread dumps, JFR. |
| **Scheduling / wake-up** | Một thread bị park/block rồi được đánh thức và schedule lại. | p95/p99 latency, context switching, lock wait time. |
| **Cache coherence** | Nhiều core cập nhật cùng state/metadata lock, gây traffic. | Profiling/CPU counters khi có hỗ trợ. |
| **Serialization** | Critical section ngăn các operation chạy cùng lúc. | Throughput khi tăng số worker; độ dài hold time. |
| **Fairness bookkeeping** | Chính sách xếp hàng/tôn trọng thứ tự có thể làm giảm khả năng “barging” và throughput. | Benchmark fair vs nonfair trên cùng tải. |
| **Read/write coordination** | Read locks có metadata riêng; acquire/release cho mỗi reader vẫn có giá. | So sánh với mutex khi chỉ đọc một vài field. |
| **Optimistic retry** | CAS thất bại hoặc optimistic-read validation thất bại, phải làm lại công việc. | Retry rate, allocation, CPU, tail latency. |
| **Độ phức tạp lập trình** | Dễ quên `unlock()`, quên validate, nhầm condition, deadlock, invariant rải rác. | Code review, stress tests, sự cố production. |

Đừng chỉ đo nanoseconds của một `lock()` rỗng rồi suy ra hiệu năng chương trình. Một critical section dài do network I/O hoặc callback bên ngoài sẽ che khuất chi phí acquire. Ngược lại, hàng triệu phép cập nhật cực nhỏ lên một điểm chia sẻ có thể bị giới hạn bởi cache contention và số lần lấy lock.

**Thông số quan trọng nhất khi contention cao thường là thời gian giữ khóa và tần suất tranh chấp**, không phải tên API.

## 3. Những câu hỏi phải trả lời trước khi chọn

1. **Trạng thái nào phải nhất quán?** Một biến đơn, nhiều field trong cùng invariant, hay nhiều object?
2. **Tỷ lệ đọc/ghi và độ dài thao tác?** Đọc chỉ lấy hai field, hay truy vấn dữ liệu lớn? Ghi thường xuyên đến mức nào?
3. **Số contender thực sự?** Thread hiện hữu không có nghĩa tất cả đều cùng lấy một lock.
4. **Có phải đợi điều kiện?** Cần `await`/`signal` nhiều điều kiện độc lập hay chỉ cập nhật state?
5. **Có deadline/cancellation ở bước lấy khóa?** Nếu cần `tryLock(timeout)` hoặc `lockInterruptibly()`, intrinsic monitor không cung cấp chính sách tương đương cho acquisition.
6. **Cần fairness không?** Fairness có thể ảnh hưởng throughput và latency nhưng không thay thế SLA.
7. **Có cần lấy nhiều lock?** Lock order, reentrancy và khả năng deadlock sẽ quyết định độ phức tạp.
8. **Đọc có cần snapshot tuyến tính hóa (linearizable) không?** Một report có thể chấp nhận gần đúng, nhưng thanh toán/quota thường không thể.
9. **Khi lỗi/interrupt, state còn hợp lệ không?** Lock được giải phóng không đồng nghĩa đã rollback.
10. **Có đo được bottleneck không?** Không tối ưu một lock chỉ vì “nghe nói” class khác nhanh hơn.

Từng yêu cầu phải xuất phát từ hợp đồng của API và invariant, không phải mẹo cấu hình ngẫu nhiên.

## 4. `synchronized`: lựa chọn nền tảng, thường nên thử đầu tiên

`synchronized` dùng **intrinsic monitor** gắn với object. JVM tự acquire khi vào khối/method và release khi thoát, kể cả khi một exception thoát ra. Đây là lựa chọn có nhiều ưu điểm:

- Mã ngắn; không thể quên `unlock()` ở nhánh return/exception thông thường.
- Reentrant: thread đang giữ monitor có thể vào lại monitor đó.
- Hợp đồng Java Memory Model rõ ràng: unlock → lock sau đó trên **cùng monitor**.
- Phù hợp để bảo vệ một nhóm field cùng invariant.
- Không cần object Lock/Condition riêng nếu chỉ cần mutual exclusion.

Những giới hạn quan trọng:

- Chờ **lấy monitor** thông qua `synchronized` không có overload timeout hoặc `lockInterruptibly()`. Thread `BLOCKED` vì monitor không tự bỏ chờ khi bị interrupt.
- Không có chính sách FIFO/fairness được bảo đảm.
- Chỉ một monitor đi cùng mỗi object; có `Object.wait()/notify()/notifyAll()`, nhưng khi nhiều predicate khác nhau, việc tổ chức condition có thể khó.
- Nếu critical section giữ khóa khi gọi I/O, `Thread.sleep()`, `Future.get()` hoặc callback chậm, contention vẫn cao. `sleep()` không nhả monitor.

### 4.1 Chọn object lock nào?

~~~java
final class Counter {
    private final Object lock = new Object();
    private long value;

    void add(long delta) {
        synchronized (lock) {
            value += delta;
        }
    }

    long get() {
        synchronized (lock) {
            return value;
        }
    }
}
~~~

Đây là **mảnh class minh họa**. Private lock tránh code ngoài tự `synchronized` lên cùng object và ảnh hưởng khả năng tiến triển.

Lưu ý:

- `synchronized` instance method lấy monitor của `this`.
- `static synchronized` method lấy monitor của `Class` object.
- Hai instance khác nhau có hai monitor khác nhau, nên chỉ phù hợp nếu state được bảo vệ cũng độc lập.
- Tránh khóa trên chuỗi literal/interned hoặc boxed constant có thể được chia sẻ ngoài ý muốn.
- Không nhầm lẫn `volatile` với một monitor; volatile không bảo vệ check-then-act nhiều bước.

### 4.2 Khi nào synchronized đủ tốt?

Nếu critical section ngắn, ít contention, không cần timed/interruptible acquisition và một private monitor bảo vệ toàn invariant, `synchronized` thường là quyết định **ít rủi ro và dễ review**. Chỉ chuyển sang primitive phức tạp khi có yêu cầu cụ thể hoặc đo đạc chứng minh vấn đề.

## 5. `ReentrantLock`: explicit locking đổi lấy quyền điều khiển

`java.util.concurrent.locks.ReentrantLock` cũng cung cấp mutual exclusion và memory effects như intrinsic monitor khi dùng đúng. Khác biệt nằm ở các operation mở rộng:

| Nhu cầu | `synchronized` | `ReentrantLock` |
| --- | --- | --- |
| Mutual exclusion | Có | Có |
| Reentrant | Có | Có |
| Tự release khi thoát block vì exception | Có, JVM tự thực hiện | Không; **phải `unlock()` trong `finally`** |
| Thử lấy khóa ngay | Không có API tương đương trực tiếp | `tryLock()` |
| Lấy khóa có timeout | Không có trên acquisition | `tryLock(timeout, unit)` |
| Lấy khóa có thể hủy bằng interruption | Không có trên acquisition | `lockInterruptibly()` |
| Nhiều điều kiện chờ khác nhau trên cùng lock | Một wait set/monitor + `wait/notify` | Nhiều `Condition` từ `newCondition()` |
| Tùy chọn fair | Không có tham số fairness | `new ReentrantLock(true)` |
| Quan sát queue/waiters | Chủ yếu dùng JVM diagnostics | Có một số method giám sát, kết quả có tính thời điểm |

### 5.1 Quy tắc dùng đúng

~~~java
lock.lock();
try {
    // Đọc/ghi state được bảo vệ.
} finally {
    lock.unlock();
}
~~~

Đây là mảnh mã, giả định `lock` có kiểu `ReentrantLock`. Phải gọi `lock()` **trước** `try`, và chỉ `unlock()` sau khi đã lấy được khóa. Nếu `lockInterruptibly()` bị interrupt **trước khi acquire**, không được gọi `unlock()` trên thread không sở hữu khóa.

Vì lock là một object được quản lý thủ công, nguy cơ lỗi tăng nếu:

- Quên unlock trên một nhánh return/exception.
- Acquire/release sai số lần khi dùng reentrancy.
- Expose lock ra bên ngoài, khiến caller tùy ý giữ lâu.
- Giữ lock qua callback/I/O không kiểm soát.
- Gọi `Condition.await()` hoặc `signal()` mà không sở hữu lock theo hợp đồng.

**Không có bảo đảm ReentrantLock luôn nhanh hơn synchronized.** Chúng có thể có các đường tối ưu khác nhau theo JDK và workload; benchmark thực mới quyết định.

## 6. Ví dụ Java 17+: timeout khi đợi khóa

Intrinsic monitor không có timed acquisition. Ví dụ dưới dùng `ReentrantLock.tryLock(timeout)` để tránh một thread phải chờ vô hạn khi holder vẫn sở hữu khóa.

Tạo file `TimedLockChoiceDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class TimedLockChoiceDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch holderEntered = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);

        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                holderEntered.countDown();
                releaseHolder.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "holder");

        Thread contender = new Thread(() -> {
            try {
                boolean acquired =
                        lock.tryLock(100, TimeUnit.MILLISECONDS);
                System.out.println("Acquired: " + acquired);
                if (acquired) {
                    try {
                        System.out.println("Critical section");
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "contender");

        holder.start();
        holderEntered.await();

        contender.start();
        contender.join();
        releaseHolder.countDown();
        holder.join();
        System.out.println("Finished");
    }
}
~~~

Lệnh Java 17+:

~~~bash
javac TimedLockChoiceDemo.java
java TimedLockChoiceDemo
~~~

Stdout trong lần chạy bình thường, không có external interruption:

~~~text
Acquired: false
Finished
~~~

**Vì sao kết quả xác định?** Holder không giải phóng lock trước khi main mở `releaseHolder`; main chỉ mở sau khi contender đã kết thúc. Contender không thể lấy được lock trong thời hạn 100 ms và trả `false`. Không có `sleep()` để phỏng đoán lịch chạy.

Điểm cần nhớ:

- Timeout chỉ giới hạn **thời gian chờ để acquire**, không giới hạn thời gian thực hiện operation sau khi đã lấy lock.
- `tryLock()` không timeout thử ngay và trả boolean; `tryLock(timeout, unit)` có thể ném `InterruptedException`.
- Không dùng nhánh timeout như một “fallback” âm thầm bỏ cập nhật tài chính; phải định nghĩa rõ kết quả API, retry và thông báo caller.
- `unlock()` nằm trong `finally` sau khi acquire thành công, không nhả khóa sai chủ sở hữu.
- Nếu application shutdown/cancellation, phải phối hợp dọn dẹp và mở những latch/condition cần thiết.

## 7. Fairness: chi phí và ngộ nhận

`new ReentrantLock(true)` yêu cầu **fairness policy**: khi nhiều thread tranh nhau, lock có xu hướng trao quyền cho thread chờ lâu hơn theo cơ chế queue của API. `new ReentrantLock(false)` (mặc định) cho phép chính sách nonfair, thường có lợi về throughput trong nhiều workload.

Nhưng fairness không đồng nghĩa:

- mọi task hoàn thành trong một deadline;
- scheduler JVM/OS cấp CPU công bằng;
- thời gian critical section nhỏ lại;
- không còn starvation ở cấp ứng dụng do I/O, pool saturation hay phụ thuộc tác vụ.

Một chi tiết dễ bỏ qua: **`tryLock()` không tham số có thể lấy lock ngay khi nó đang rảnh ngay cả trên fair ReentrantLock**, dù có thread khác đang chờ. Nếu cần tuân thủ fairness hơn trong đường thử khóa, phải hiểu sự khác biệt giữa các overload và contract cụ thể. Không mô tả fair lock như một FIFO tuyệt đối cho mọi API.

Một fair lock có thể làm giảm tính “barging”, nhưng cũng có overhead phục vụ queue và có thể giảm throughput. Hãy chọn khi có yêu cầu có căn cứ về độ công bằng chờ khóa, và đo bằng workload đại diện.

## 8. `Condition`: khi một lock bảo vệ nhiều điều kiện chờ

Nếu thread cần chờ **một predicate** của state được lock bảo vệ, cứ liên tục giải phóng/reacquire lock để poll là không hiệu quả. `Condition` gắn với `Lock` cho phép thread đang sở hữu lock gọi `await()`:

1. Tạm **nhả lock liên kết** trong lúc chờ.
2. Chờ `signal()` / `signalAll()`, interruption hoặc spurious wakeup.
3. Reacquire lock trước khi `await()` trả về bình thường.
4. Phải kiểm tra lại predicate bằng **`while`**, không dùng `if`.

Khác `Object.wait()`, một `ReentrantLock` có thể tạo **nhiều Condition** để phân biệt những nhóm waiter:

- `notEmpty`: consumer đang đợi có phần tử;
- `notFull`: producer đang đợi còn chỗ.

### 8.1 Ví dụ Java 17+: bounded one-slot buffer

Tạo file `ConditionBufferDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class ConditionBufferDemo {
    static final class OneSlotBuffer {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();
        private final Condition notFull = lock.newCondition();
        private Integer value;

        void put(int item) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (value != null) {
                    notFull.await();
                }
                value = item;
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        int take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (value == null) {
                    notEmpty.await();
                }
                int item = value;
                value = null;
                notFull.signal();
                return item;
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        OneSlotBuffer buffer = new OneSlotBuffer();
        CountDownLatch consumerStarted = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            consumerStarted.countDown();
            try {
                System.out.println("Taken: " + buffer.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        consumer.start();
        consumerStarted.await();

        buffer.put(42);
        consumer.join();
        System.out.println("Finished");
    }
}
~~~

Lệnh biên dịch/chạy:

~~~bash
javac ConditionBufferDemo.java
java ConditionBufferDemo
~~~

Stdout khi không có interruption ngoài:

~~~text
Taken: 42
Finished
~~~

**Phân tích:**

- `OneSlotBuffer` chỉ có một chỗ, trạng thái `value == null` nghĩa buffer đang trống.
- Consumer kiểm tra predicate dưới lock; nếu trống, `notEmpty.await()` nhả lock để producer vào `put()`. Nếu producer put trước khi consumer vào, consumer thấy `value != null` và không cần đợi, nên không mất tín hiệu theo protocol này.
- Producer đặt value rồi `notEmpty.signal()` trong khi giữ lock, sau đó unlock. Consumer mới được lấy lại lock và đọc value; có quan hệ bộ nhớ đúng theo lock protocol.
- `while` bảo vệ khỏi spurious wakeup và điều kiện thay đổi trước khi waiter reacquire.
- Chương trình chỉ có một producer và một consumer để stdout đơn giản; class buffer có thể dùng trong nhiều thread nhưng vẫn phải xác định chính sách shutdown/cancellation và fairness theo yêu cầu.

**Không tự viết bounded queue như trên trong production nếu `ArrayBlockingQueue` đã đáp ứng nhu cầu.** Ví dụ nhằm giải thích Condition. Một queue tiêu chuẩn đã xử lý nhiều edge case và có API `put()`/`take()`/timed operations.

## 9. `ReadWriteLock`: cho nhiều reader vào cùng lúc

`java.util.concurrent.locks.ReadWriteLock` mô tả hai lock có vai trò:

- **Read lock:** nhiều reader có thể giữ đồng thời khi không có writer nắm write lock.
- **Write lock:** writer cần độc quyền để cập nhật state, chặn các reader/writer xung đột theo giao thức.

`ReentrantReadWriteLock` là implementation tiêu chuẩn. Nó có thể giúp workload có **đọc nhiều, ghi ít, critical section đọc đủ dài**, nhưng không phải cứ 90% read là chắc chắn tốt hơn mutex.

### 9.1 Chi phí ẩn

- Mỗi reader vẫn cần acquire/release một read lock và cập nhật/kiểm tra trạng thái phối hợp.
- Khi số thread đọc nhiều, metadata lock trở thành một điểm chia sẻ bị tranh chấp.
- Writer phải chờ reader đang giữ lock kết thúc; các reader dài làm tăng write latency.
- Fairness policy ảnh hưởng starvation/throughput, nhưng không tạo deadline bảo đảm.
- Với critical section đọc cực ngắn (chỉ hai getter), overhead read lock có thể đắt hơn một mutex đơn giản trong một số workload.

### 9.2 Upgrade/downgrade

`ReentrantReadWriteLock` hỗ trợ tình huống một thread giữ write lock rồi lấy read lock để **downgrade** có kiểm soát: acquire read khi vẫn giữ write, sau đó release write. Nhưng **read-to-write upgrade không được hỗ trợ một cách tổng quát**: giữ read lock rồi cố lấy write lock có thể dẫn đến chờ vô hạn khi không thể độc quyền.

Hãy làm: đọc/kiểm tra dưới read lock → release read lock → acquire write lock → **kiểm tra lại điều kiện** → commit. Khoảng giữa release và acquire cho phép state thay đổi; không được dựa vào điều kiện cũ như vẫn đúng.

### 9.3 Runnable Java 17+: snapshot dưới read lock

Tạo file `ReadWriteLockChoiceDemo.java`:

~~~java
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class ReadWriteLockChoiceDemo {
    private static final class Inventory {
        private final ReentrantReadWriteLock rw =
                new ReentrantReadWriteLock();
        private int available = 100;
        private int reserved;

        boolean reserve() {
            rw.writeLock().lock();
            try {
                if (available == 0) {
                    return false;
                }
                available--;
                reserved++;
                return true;
            } finally {
                rw.writeLock().unlock();
            }
        }

        Snapshot snapshot() {
            rw.readLock().lock();
            try {
                return new Snapshot(available, reserved);
            } finally {
                rw.readLock().unlock();
            }
        }
    }

    private record Snapshot(int available, int reserved) {
        int total() {
            return available + reserved;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Inventory inventory = new Inventory();
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                inventory.reserve();
            }
        };

        Thread a = new Thread(task, "a");
        Thread b = new Thread(task, "b");
        a.start();
        b.start();
        a.join();
        b.join();

        Snapshot s = inventory.snapshot();
        System.out.println("Available: " + s.available());
        System.out.println("Reserved: " + s.reserved());
        System.out.println("Total: " + s.total());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ReadWriteLockChoiceDemo.java
java ReadWriteLockChoiceDemo
~~~

Stdout:

~~~text
Available: 0
Reserved: 100
Total: 100
~~~

Hai writer cạnh tranh để acquire write lock; có đúng 100 lượt reserve thành công. Snapshot được đọc trong một lần read lock, nên không lấy `available` từ phiên bản trước và `reserved` từ phiên bản sau.

Lưu ý, chương trình này **không chứng minh hiệu năng** của read/write lock: nó chủ yếu có writer và chỉ một lần snapshot ở cuối. Benchmark một workload read-heavy thật mới có ý nghĩa để quyết định thay mutex.

## 10. `StampedLock`: nhiều chế độ, thêm nhiều quy tắc

`java.util.concurrent.locks.StampedLock` hỗ trợ **write lock**, **read lock** và **optimistic read** bằng stamp. Nó không phải một `ReentrantLock` có thêm tên gọi mới.

Các đặc điểm quan trọng:

- **Không reentrant:** một method đang giữ write lock mà lại cố lấy write lock theo đường gọi lồng nhau có thể tự chặn mình.
- Stamp là token trả về từ thao tác acquire; phải dùng đúng stamp khi `unlockRead(stamp)` / `unlockWrite(stamp)`.
- `tryOptimisticRead()` trả stamp dùng để **validate** xem có writer làm mất hiệu lực snapshot trong giai đoạn đọc không; không giữ read lock.
- Nếu `validate(stamp)` thất bại, phải đọc lại dưới read lock hoặc retry theo protocol phù hợp.
- Có các operation chuyển chế độ như `tryConvertToWriteLock`, nhưng conversion có thể thất bại; cần kiểm tra kết quả và quản lý stamp/mode cực cẩn thận.
- Không cung cấp hợp đồng Condition như `ReentrantLock.newCondition()`.
- Không bảo đảm fairness; dễ phát sinh starvation nếu dùng không phù hợp với workload.

### 10.1 Optimistic read: đọc rồi validate

Mẫu minh họa:

~~~java
long stamp = lock.tryOptimisticRead();
int x = state.x;
int y = state.y;

if (!lock.validate(stamp)) {
    stamp = lock.readLock();
    try {
        x = state.x;
        y = state.y;
    } finally {
        lock.unlockRead(stamp);
    }
}
return new Snapshot(x, y);
~~~

Đây là **mảnh method minh họa**, giả định `lock` là `StampedLock`, `state` chứa hai primitive field, method trả một `Snapshot` và các kiểu đã khai báo. Nó không chạy độc lập.

Điểm mấu chốt:

1. Optimistic read **có thể nhìn trạng thái trung gian trong lúc writer đang sửa**, trước khi validate.
2. Chỉ sử dụng kết quả sau **`validate(stamp) == true`**. Nếu validation không thành công, đọc lại dưới một read lock thật.
3. Không dùng các giá trị chưa validate để tạo side effect, truy cập dữ liệu ngoài biên mảng, gọi method trên reference có thể đang biến đổi hoặc đưa ra quyết định không thể đảo ngược.
4. Mẫu này đơn giản với primitive fields. Nếu object graph mutable/phức tạp, việc đọc trước validation có thể không an toàn về mặt thao tác hoặc dễ lỗi; cân nhắc dùng read lock từ đầu.
5. Việc stamp không phải “version ID” nghiệp vụ bền vững. Nó là token phục vụ protocol locking/validation.

### 10.2 Khi nào không nên dùng?

Nếu code cần reentrancy, nhiều Condition, các giá trị object mutable phức tạp, hoặc team chưa có test và review tốt cho optimistic protocol, `StampedLock` dễ làm tăng chi phí bảo trì hơn hiệu năng nhận được. Tỷ lệ optimistic validation thành công thấp khiến phải làm lại nhiều lần, vừa tốn CPU vừa phức tạp.

Dùng nó khi profiling và benchmark cho thấy **nhiều phép đọc độc lập, write hiếm và optimistic reads thường thành công**, đồng thời state được trình bày thành snapshot an toàn.

## 11. Không phải mọi bài toán đều cần lock

Một lock là giải pháp tốt cho invariant nhiều bước; với state đơn giản, primitive chuyên dụng có thể ít rủi ro hơn.

| Bài toán | Lựa chọn đáng xem xét | Vì sao |
| --- | --- | --- |
| Cờ/tín hiệu visibility đơn giản, một writer | `volatile` hoặc latch/future tùy giao thức | Không cần mutual exclusion cho nhiều bước. |
| Bộ đếm tăng độc lập cần kết quả chính xác khi đọc | `AtomicInteger`/`AtomicLong` | Atomic read-modify-write cho một biến. |
| Metrics nhiều writer, đọc tổng gần đúng trong lúc cập nhật | `LongAdder` | Phân tán contention; `sum()` không phải atomic snapshot khi concurrent updates. |
| Cập nhật nhiều field như **một immutable value** | `AtomicReference<State>` + CAS | Một reference chứa toàn bộ snapshot, update bằng một CAS. |
| Producer/consumer chờ phần tử | `BlockingQueue` | Tự xử lý phối hợp, không cần tự viết Condition buffer. |
| Chỉ cần một lần báo hoàn tất | `CountDownLatch` | Đợi sự kiện, không phải truy cập mutable state qua lock tự viết. |
| Quản lý đồng thời bao nhiêu task dùng tài nguyên | `Semaphore` | Điều tiết permit; không tự làm các field thành atomic. |
| Nhiều key độc lập, truy cập map đồng thời | `ConcurrentHashMap` | API concurrent chuyên dụng, nhưng operation nhiều key có invariant riêng. |

### 11.1 AtomicReference và CAS cũng có chi phí

Nếu dùng `AtomicReference<ImmutableState>` để giữ một snapshot nhiều field, writer sẽ tạo state mới rồi `compareAndSet(old, next)`. Với contender cùng update, CAS thất bại và phải retry. Chi phí gồm:

- Allocation cho những candidate state có thể bị bỏ đi.
- Retry khi contention cao; không có bảo đảm một thread cụ thể sẽ thành công trong số lần hữu hạn.
- Code khó review nếu có side effect trong phép tính next.
- Khả năng ABA khi algorithm tái sử dụng identity/giá trị theo cách không kiểm tra lịch sử.

Không đặt network call, gửi email hoặc thay đổi external state trong phần tính toán có thể retry. Với state phức tạp, một lock đơn giản có thể dễ đúng và nhanh hơn.

### 11.2 Một biến atomically đúng không có nghĩa nhiều biến nhất quán

Hai `AtomicInteger` riêng biệt không biến `available--` và `reserved++` thành atomic transition. Nếu cần `available + reserved == capacity` trong mọi snapshot, dùng **một critical section** hoặc **một immutable State được publish atomically**.

Mọi quyết định “không dùng lock” cũng phải chứng minh được HB, atomicity và invariant phù hợp. Đừng thay khóa bằng `volatile` chỉ để tránh BLOCKED thread.

## 12. Reentrancy, fairness, cancellation: không thể đổi lock máy móc

Bên cạnh performance, việc đổi primitive có thể thay đổi **ngữ nghĩa của API**.

### 12.1 Reentrant hay không?

- Intrinsic monitors: reentrant.
- `ReentrantLock`: reentrant.
- `ReentrantReadWriteLock`: hỗ trợ reentrancy theo các quy tắc riêng.
- `StampedLock`: **không reentrant**.

Một chuỗi method vốn chạy tốt dưới `synchronized` có thể tự deadlock sau khi đổi sang `StampedLock` nếu helper method lồng nhau lại lấy lock lần nữa. Trước khi thay, kiểm tra call graph và cách acquire nested.

### 12.2 Interruptibility của acquisition

- Thread đang `BLOCKED` để vào `synchronized` không có timed/interruptible acquisition theo cùng kiểu `ReentrantLock.lockInterruptibly()`.
- `ReentrantLock.lockInterruptibly()` và `tryLock(timeout, unit)` có thể ném `InterruptedException`.
- `Condition.await()` là operation chờ điều kiện, không giống chờ acquire lock lần đầu.
- `Object.wait()` có thể ném `InterruptedException` và nhả monitor đang wait trong thời gian chờ; `Thread.sleep()` không nhả monitor đã giữ.

Hãy phân biệt **cancellation của người đang chờ lấy lock** với **cancellation của người đang giữ lock**. Không API nào tự bảo đảm business operation có thể rollback chính xác ở bất kỳ instruction nào.

### 12.3 Fairness và starvation

Một lựa chọn fair có thể hữu ích khi một nhóm contender đợi bất hợp lý, nhưng không sửa code giữ lock quá lâu hoặc dependency graph bị vòng. Fairness còn có chi phí scheduling/throughput; điều cần xác định là policy người dùng mong đợi, không chỉ flag cấu hình.

## 13. Deadlock và thứ tự lấy nhiều lock

Khi phải giữ hai lock, một vòng phụ thuộc có thể xảy ra:

~~~text
Thread A: giữ lock1 -> chờ lock2
Thread B: giữ lock2 -> chờ lock1
~~~

Nếu chỉ có một owner lock bảo vệ toàn invariant, đôi khi thiết kế vừa đơn giản vừa dễ chứng minh hơn. Khi bắt buộc nhiều lock, dùng **thứ tự toàn cục ổn định**, ví dụ theo một ID duy nhất và bất biến của object.

Mảnh minh họa:

~~~java
Account first = left.id < right.id ? left : right;
Account second = left.id < right.id ? right : left;

synchronized (first.lock) {
    synchronized (second.lock) {
        // Kiểm tra và cập nhật state liên quan cả hai account.
    }
}
~~~

Đoạn này **không tự chạy**: cần class `Account` có ID bất biến và duy nhất, lock và state riêng. Trong sản phẩm phải xử lý trường hợp cùng account, ID trùng/không hợp lệ và mọi đường gọi khác cùng thứ tự. Không để lock public hoặc callback bên ngoài lấy lock ngược.

**Cân nhắc chi phí:** chia một lock thành nhiều lock có thể tăng concurrency khi dữ liệu độc lập, nhưng tăng bề mặt deadlock và làm snapshot nhiều object khó hơn. Chỉ shard lock khi chứng minh invariant không bị tách sai và đã có lợi ích đo được.

### 13.1 Đừng chờ completion khi giữ lock mà task cần

~~~java
synchronized (lock) {
    worker.start();
    worker.join();
}
~~~

Mảnh minh họa giả định `worker` trong lúc chạy cần `lock`; khi đó caller giữ lock và chờ worker hoàn tất, worker cần chính lock ấy để hoàn tất, tạo deadlock. `Thread.join()` không nhả monitor đang giữ.

Tránh `Future.get()`, `CountDownLatch.await()` hoặc I/O blocking trong critical section nếu công việc được chờ cần lock/worker đang bị chiếm dụng. Khi thật sự cần đợi điều kiện trên một lock, dùng `Condition`/`Object.wait()` theo protocol, để nhả lock gắn với điều kiện trong lúc đợi.

## 14. Virtual threads và lựa chọn lock

Từ Java 21, **virtual threads** giúp mô hình một thread cho mỗi tác vụ blocking trở nên khả thi với nhiều workload, nhưng **không giải phóng invariant khỏi nhu cầu đồng bộ**:

- Nhiều virtual threads lấy cùng monitor vẫn phải tuần tự trong critical section.
- Giữ lock qua I/O chậm vẫn khiến contender chờ và tăng latency ở cấp ứng dụng.
- `ReentrantLock` không tự nhanh hơn intrinsic monitor chỉ vì chạy trên virtual thread.
- Các vấn đề liên quan virtual-thread pinning khi giữ intrinsic monitor đã thay đổi giữa các phiên bản JDK. Ví dụ JDK 21 có những trường hợp pinning gắn với `synchronized`; các bản JDK sau có cải tiến. Cần xác nhận **JDK cụ thể đang triển khai** và quan sát JFR/profiler, không áp dụng quy tắc của một phiên bản cho mọi runtime.
- `ReentrantLock` với interruptible/timed acquisition hữu ích khi hợp đồng hủy yêu cầu, nhưng việc đổi loại lock vẫn phải giữ đúng thứ tự và invariant.

Nên đánh giá lock **dựa trên workload, version JDK và yêu cầu cancellation thật**, thay vì dùng “virtual threads” như lý do mặc định phải thay tất cả `synchronized`.

## 15. Benchmark đúng trước khi tối ưu

Một microbenchmark về lock rất dễ sai vì compiler/JIT, dead-code elimination, warm-up, false sharing, contention nhân tạo và kết quả bị chi phối bởi việc làm bên ngoài lock.

### 15.1 Các bước thực tế

1. **Thiết lập baseline correctness.** Trước và sau tối ưu phải cho cùng kết quả/invariant khi nhiều thread tranh chấp.
2. **Đo đúng workload.** Số reader, writer, phân bố thời gian critical section, độ lớn state và mức tải phải đại diện production.
3. **Phân biệt throughput và latency.** Một lock tăng throughput trung bình nhưng làm p99 chờ khóa quá cao có thể không phù hợp SLA.
4. **Đo contention.** Thread dump, `ThreadMXBean`, Java Flight Recorder (JFR) và profiler có thể cho biết thread nào chờ monitor/lock, holder đang làm gì, và thời gian chờ.
5. **Đo cả thời gian giữ khóa.** Nhiều contender `BLOCKED` có thể do một holder gọi I/O/sleep chậm trong critical section.
6. **Warm-up và so sánh công bằng.** Dùng cùng JDK, JVM flags, dữ liệu đầu vào, số thread, môi trường; chạy nhiều vòng và xem phân bố kết quả.
7. **Kiểm tra lỗi sau tối ưu.** Không chấp nhận “nhanh hơn” nếu bỏ mất atomic snapshot hoặc update.
8. **Ghi lại trade-off.** Cần biết độ phức tạp code tăng bao nhiêu so với lợi ích đo được.

**JMH** (Java Microbenchmark Harness) phù hợp để làm microbenchmark về JVM. Bài học này không tự đưa số nanosecond cố định, bởi kết quả phụ thuộc hoàn toàn vào cách dựng benchmark và runtime.

### 15.2 Những chỉ số nên ghi

| Metric | Diễn giải |
| --- | --- |
| Operations/second | Throughput hữu ích khi tăng số worker. |
| Median, p95, p99 latency | Thời gian chờ của yêu cầu/operation, đặc biệt đuôi phân bố. |
| Lock acquisition/blocked time | Chi phí các contender phải đợi. |
| Critical section hold time | Thời lượng holder giữ lock. |
| Retry/optimistic validation failure | Chi phí thêm của CAS và StampedLock. |
| CPU, allocation và GC | Retry/immutable candidate/metadata overhead. |
| Read/write proportion | Chứng minh ReadWriteLock hoặc optimistic read có hợp workload. |
| Correctness invariants | Không được mất tính đúng khi đổi primitive. |

Đừng đặt mục tiêu “không còn thread `BLOCKED`”. Một chút blocking có thể hợp lý và tiết kiệm CPU hơn vòng spin. Mục tiêu là correctness cùng hiệu năng đủ đáp ứng yêu cầu, không phải loại bỏ mọi lock.

## 16. Chọn loại lock theo tình huống

| Tình huống | Lựa chọn ban đầu hợp lý | Vì sao / điểm cần xác minh |
| --- | --- | --- |
| Một object mutable, vài field cùng invariant, critical section ngắn | **`synchronized` với private lock** | Dễ đọc, reentrant, tự release; kiểm tra mọi reader/writer cùng protocol. |
| Cần timed/interruptible acquisition | **`ReentrantLock`** | `tryLock(timeout)` / `lockInterruptibly()`; unlock trong finally. |
| Một lock có nhiều predicate cần chờ riêng | **`ReentrantLock + Condition`** hoặc cấu trúc concurrent có sẵn | Phân biệt notEmpty/notFull; kiểm tra predicate bằng while. |
| Nhiều reader, ít writer, read critical section tương đối dài | **`ReentrantReadWriteLock`** để thử nghiệm | Đo overhead read lock và writer latency, không tự động nhanh hơn mutex. |
| Rất nhiều phép đọc đơn giản, ghi hiếm, snapshot dễ validate | **`StampedLock`** nếu benchmark chứng minh lợi ích | Protocol non-reentrant và optimistic validation khó hơn, cần fallback. |
| Một biến counter/flag độc lập | **Atomic/volatile** theo bài toán | Tránh lock không cần thiết; volatile không sửa multi-step invariant. |
| Một immutable snapshot nhiều trường, update không side effect | **`AtomicReference<State>`** | CAS atomically publish state, đổi lấy retry/allocation. |
| Buffer producer–consumer | **`BlockingQueue`** | Ít lỗi hơn tự tổ chức wait/notify/Condition. |
| Nhiều key độc lập trong map | **`ConcurrentHashMap`** | Operation nhiều key vẫn cần protocol riêng. |

**Quy tắc ưu tiên:** correctness → deadline/cancellation → điều kiện chờ → read/write pattern → contention thực tế → độ phức tạp bảo trì. Nếu hai lựa chọn đều đạt yêu cầu, ưu tiên API đơn giản hơn cho đến khi có bằng chứng hiệu năng ngược lại.

## 17. Những lỗi chọn lock thường gặp

**Lỗi 1: “ReadWriteLock có hai loại lock nên luôn nhanh hơn synchronized.”** Sai. Các read lock vẫn có overhead; critical section nhỏ hoặc writer thường xuyên có thể khiến throughput thấp hơn mutex.

**Lỗi 2: “StampedLock optimistic read không chặn nên có thể dùng dữ liệu ngay.”** Sai. Dữ liệu trước validation có thể không nhất quán; chỉ được sử dụng khi validate thành công, hoặc phải fallback dưới read lock.

**Lỗi 3: “Đổi synchronized sang ReentrantLock là performance fix.”** Không có bảo đảm. Nếu holder vẫn gọi I/O chậm dưới lock, contention còn nguyên.

**Lỗi 4: “Lock fair đảm bảo mỗi thread hoàn thành đúng thời hạn.”** Sai. Fairness không thay scheduling, độ dài task và các điểm chờ khác.

**Lỗi 5: “Hai getter dưới read lock từng lần đủ tạo snapshot.”** Nếu có writer chen giữa hai lần gọi, chúng có thể thuộc hai phiên bản khác nhau. Đọc cả invariant dưới **một lần** read lock.

**Lỗi 6: “Một atomic variable cho mỗi field giải quyết invariant tổng.”** Atomic của từng biến không tạo atomic transaction giữa nhiều biến.

**Lỗi 7: “Chỉ cần `unlock()` trong happy path.”** Exception/return có thể làm rò lock; dùng `try/finally` ngay sau acquire.

**Lỗi 8: “Có thể upgrade read lock lên write lock trực tiếp.”** Với `ReentrantReadWriteLock`, read-to-write upgrade không được hỗ trợ như một thao tác tổng quát; phải release và kiểm tra lại sau khi lấy write lock.

**Lỗi 9: “`Condition.signal()` nghĩa waiter chạy ngay.”** Waiter còn phải reacquire lock và có thể thấy predicate thay đổi; luôn kiểm tra bằng while.

**Lỗi 10: “Chờ intrinsic monitor có thể bị interrupt để thoát ngay.”** Chờ acquire monitor không có cơ chế interruptible acquisition tương đương `lockInterruptibly()`.

**Lỗi 11: “JMH hoặc một lần profiling cho kết quả vĩnh viễn.”** Các JDK, máy, tần suất đọc/ghi và đường code khác nhau có thể đảo ngược kết quả.

**Lỗi 12: “Bỏ lock là luôn tốt hơn.”** Nếu thiết kế không còn HB/atomicity thích hợp, hệ thống có thể nhanh hơn nhưng sai dữ liệu.

## 18. Câu hỏi tự kiểm tra

**1.** Một object có ba field cùng invariant; thao tác ghi hiếm và chỉ mất vài instruction. Nên bắt đầu bằng gì?  
**Đáp:** Một private `synchronized` lock cho cả operation và snapshot, trừ khi có yêu cầu đặc biệt. Đo trước khi chọn giải pháp phức tạp.

**2.** Cần giới hạn thời gian chờ để lấy khóa. Intrinsic monitor có API timed acquisition không?  
**Đáp:** Không. `ReentrantLock.tryLock(timeout, unit)` phù hợp hơn, nhưng phải xử lý nhánh timeout và unlock đúng.

**3.** Dùng `ReentrantReadWriteLock` nhưng một writer chạy liên tục; có chắc nhanh hơn mutex không?  
**Đáp:** Không. Writer vẫn độc quyền, reader có overhead và có thể chờ; cần benchmark với tỷ lệ đọc/ghi thực.

**4.** Vì sao `Condition.await()` phải đi cùng vòng `while`?  
**Đáp:** Vì spurious wakeup và vì predicate có thể thay đổi trước khi waiter reacquire lock.

**5.** `StampedLock.tryOptimisticRead()` trả stamp, đọc hai field rồi validate thất bại. Nên làm gì?  
**Đáp:** Retry hoặc lấy read lock, đọc lại cả hai field và chỉ sử dụng snapshot hợp lệ.

**6.** Cần nhiều method gọi lồng nhau, cùng lấy lock. `StampedLock` có phù hợp như thay thế máy móc cho synchronized không?  
**Đáp:** Không. `StampedLock` không reentrant và có thể làm code tự deadlock.

**7.** Hai `AtomicInteger` quản lý available và reserved. Có đủ để giữ `available + reserved == capacity` trong mọi snapshot không?  
**Đáp:** Không. Cần cập nhật và đọc nhóm field theo một protocol atomic chung.

**8.** `tryLock()` không tham số trên một fair ReentrantLock có luôn tôn trọng hàng đợi không?  
**Đáp:** Không; overload này có thể barging khi khóa đang rảnh, bất kể fairness flag.

**9.** Tại sao không giữ lock khi gọi `Future.get()` đợi worker cần lock đó?  
**Đáp:** Có thể tạo vòng chờ khiến worker không hoàn tất; cần đợi ngoài critical section hoặc thiết kế condition protocol.

**10.** Khi nào nên chấp nhận thêm độ phức tạp của `ReadWriteLock` hoặc `StampedLock`?  
**Đáp:** Khi hợp đồng correctness được chứng minh, workload có đặc điểm phù hợp và benchmark/profiling cho thấy lợi ích đủ lớn so với overhead, retry và chi phí bảo trì.

**Ghi nhớ:** Không có “lock mạnh nhất” cho mọi tình huống. **`synchronized` là baseline tốt khi chỉ cần mutual exclusion rõ ràng**; `ReentrantLock` được chọn vì quyền điều khiển acquisition/conditions; `ReadWriteLock` khi đọc song song có lợi; `StampedLock` khi optimistic protocol thực sự đáng giá. Mọi quyết định phải bảo toàn invariant và happens-before, xử lý shutdown/interruption, rồi được xác nhận bằng đo đạc trên workload phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="locks-and-conditions-012">Lock hierarchy và cách tránh thứ tự khóa vòng</a>

<details>
<summary>Xem chi tiết</summary>

# Lock hierarchy và cách tránh thứ tự khóa vòng trong Java

## 1. Mục tiêu bài học

Sau bài học, bạn có thể:

- Giải thích **deadlock do thứ tự lấy khóa vòng** (*circular lock ordering*) và mô tả **wait-for graph** (đồ thị chờ) của các thread.
- Hiểu **lock hierarchy** (thứ bậc khóa) là quy tắc áp dụng **một thứ tự toàn cục, ổn định** khi cần giữ từ hai khóa trở lên.
- Chứng minh vì sao mọi đường lấy khóa theo cùng thứ tự tăng dần có thể loại bỏ vòng chờ do những khóa ấy, đồng thời phân biệt **deadlock**, **starvation**, **livelock** và **contention**.
- Viết Java 17+ dùng **synchronized** hoặc **ReentrantLock**, sắp xếp khóa theo ID/rank duy nhất, và luôn nhả khóa theo cấu trúc an toàn.
- Xử lý trường hợp cùng object, cùng rank, khóa động, va chạm **System.identityHashCode()**, callback và tài nguyên ngoài JVM.
- Nhận biết giới hạn của **tryLock()**, timeout, interrupt và retry: các cơ chế này không thay thế thứ tự khóa nhất quán.
- Chẩn đoán deadlock bằng thread dump và kiểm thử bất biến thay vì phụ thuộc **Thread.sleep()** hoặc thứ tự log tình cờ.

Bài chỉ nói về **Java concurrency trong JDK**, dưới chủ đề **Locks & Conditions**. Các cơ chế khóa và quản lý giao dịch đặc thù của Spring thuộc chương riêng.

## 2. Deadlock xảy ra như thế nào?

**Deadlock** là tình trạng những thực thể đang chờ nhau theo cách khiến không thực thể nào có thể tự tiến lên để giải phóng tài nguyên mà thực thể khác cần. Với các khóa loại trừ nhau trong một JVM, ví dụ quen thuộc nhất là **hai thread lấy hai khóa theo thứ tự ngược nhau**.

Giả sử có **lockA** và **lockB**:

~~~text
Thread T1                            Thread T2
---------                            ---------
lock(lockA)                          lock(lockB)
  đã giữ A                             đã giữ B
  yêu cầu B -> chờ T2                  yêu cầu A -> chờ T1

T1 giữ A và chờ B  ----+
                       +---- vòng chờ -> DEADLOCK
T2 giữ B và chờ A  ----+
~~~

Hai thread đều giữ một tài nguyên mà thread kia cần. Nếu không có timeout, chính sách hủy/giải phóng thích hợp hoặc tác nhân khác thay đổi điều kiện, không thread nào có thể hoàn thành đoạn mã để nhả khóa.

Với **synchronized**, thread chờ giành intrinsic monitor thường được quan sát là **Thread.State.BLOCKED**. Với **ReentrantLock**, thread có thể chờ bằng cơ chế khác và không nhất thiết được báo là BLOCKED; quan sát trạng thái đơn lẻ không đủ kết luận có deadlock.

### Bốn điều kiện kinh điển (Coffman)

| Điều kiện | Ý nghĩa |
| --- | --- |
| **Mutual exclusion** | Ít nhất một tài nguyên không thể được sở hữu đồng thời bởi nhiều thread |
| **Hold and wait** | Thread giữ tài nguyên này trong khi đợi tài nguyên khác |
| **No preemption** | Tài nguyên không thể bị thu hồi tùy ý khỏi thread đang sở hữu |
| **Circular wait** | Tồn tại chu trình các thread chờ tài nguyên do nhau giữ |

Một deadlock dạng khóa chỉ xảy ra khi các điều kiện phù hợp cùng xuất hiện. **Lock hierarchy nhằm phá điều kiện circular wait**, bằng quy tắc không cho thread lấy khóa theo chiều tạo vòng.

**Không nên hiểu rằng chỉ có hai khóa mới deadlock.** Vòng có thể gồm T1→T2→T3→T1, nhiều lock và thậm chí kết hợp lock với sự kiện như **join()**, **Future.get()** hay callback đang chờ.

## 3. Lock hierarchy: áp đặt một thứ tự khóa toàn cục

Cho các khóa được sử dụng chung một **rank** hoặc **level** có thứ tự tổng quát rõ ràng:

~~~text
Rank 10: lockAccount
   ↓
Rank 20: lockLedger
   ↓
Rank 30: lockAudit

Được phép: 10 -> 20 -> 30; 10 -> 30; 20 -> 30.
Không được: 30 -> 20; 20 -> 10; 10 -> 30 -> 20.
~~~

Một quy tắc đơn giản là:

> Khi thread đang giữ khóa có rank **r**, nó chỉ được lấy thêm khóa **khác** có rank **lớn hơn r**. Khi thoát vùng khóa lồng nhau, nhả các khóa theo chiều ngược lại.

Nếu mọi đường mã đều tuân thủ nghiêm ngặt, không thể có vòng chờ chỉ gồm những khóa này:

- Mỗi cạnh chờ để lấy thêm khóa đi từ rank thấp đến rank cao.
- Một chu trình sẽ đòi chuỗi tăng nghiêm ngặt quay lại rank bắt đầu, điều bất khả thi.
- Do đó không có **circular wait** đối với tập khóa chịu cùng quy tắc.

Đây là **lập luận về tính đúng đắn**, không phải bảo đảm rằng code sẽ chạy nhanh, không starvation hoặc không có kiểu chờ khác. Quy tắc phải áp dụng tới **tất cả đường lấy các khóa liên quan**, kể cả helper, callback và phần code nằm sâu bên trong thư viện do ứng dụng kiểm soát.

### Rank cần có thuộc tính nào?

Một cách gán rank thực tế nên đảm bảo:

1. **Ổn định** suốt thời gian dùng khóa: không đổi rank khi một thread có thể đang giữ nó.
2. **Có thể so sánh được theo cùng một quy tắc** ở mọi nơi dùng những khóa đó.
3. **Phân biệt được những khóa khác nhau** khi quy tắc yêu cầu thứ tự nghiêm ngặt; nếu hai khóa có cùng rank cần một cơ chế *tie-break* được chứng minh.
4. **Không phụ thuộc lịch scheduling**: không chọn khóa trước theo thread nào chạy nhanh hơn.
5. **Không bị lách qua đường khác**: mã lấy khóa thủ công hoặc gọi ngược vào code khác không được vi phạm thứ tự.

Ví dụ ID của đối tượng nghiệp vụ có thể phù hợp nếu được cấp **duy nhất, bất biến** trong phạm vi các khóa được điều phối. Đừng dùng giá trị có thể thay đổi hoặc so sánh chỉ bằng tên hiển thị.

## 4. Sơ đồ wait-for graph và cycle

**Wait-for graph** là đồ thị có hướng:

- **Đỉnh** là các thread hoặc tác vụ.
- Cạnh **Ti → Tj** nghĩa là Ti đang chờ tài nguyên do Tj sở hữu.
- **Chu trình chờ** trong ngữ cảnh tài nguyên độc quyền là bằng chứng quan trọng cho deadlock.

Ví dụ:

~~~text
T1 --đợi lockB do T2 giữ--> T2
T2 --đợi lockC do T3 giữ--> T3
T3 --đợi lockA do T1 giữ--> T1

=> T1 -> T2 -> T3 -> T1 : vòng chờ.
~~~

Một **lock-order graph** lại có đỉnh là khóa: cạnh **A → B** nghĩa là trong một đường thực thi, một thread lấy B khi đang giữ A. Nếu hệ thống xuất hiện cả **A → B** và **B → A**, có nguy cơ vòng chờ khi các đường mã đó thực thi đồng thời.

Không phải mọi cycle trên sơ đồ lý thuyết đều dẫn đến deadlock trong một lần chạy cụ thể; nó chỉ ra lịch xen kẽ tiềm năng cần kiểm tra. Ngược lại, một thread dump không thấy vòng tại thời điểm chụp **không chứng minh** không bao giờ deadlock.

### Thứ tự khóa khác với thứ tự chạy

Lock hierarchy không bắt T1 chạy trước T2, cũng không bắt task A xong trước task B. Nó chỉ áp đặt **thứ tự yêu cầu sở hữu tài nguyên** trong các đoạn mã cần nhiều khóa.

Ví dụ một thread có thể lấy rank 20 khi chưa giữ khóa nào; nó không phải lấy rank 10 trước. Nhưng khi đang giữ rank 20 thì **không được bổ sung rank 10**.

## 5. Anti-pattern: hai luồng lấy khóa ngược chiều

Đây là đoạn **minh họa lỗi**, không phải chương trình độc lập nên chạy trực tiếp:

~~~java
Object lockA = new Object();
Object lockB = new Object();

// Thread T1:
synchronized (lockA) {
    synchronized (lockB) {
        // Xử lý cần cả A và B.
    }
}

// Thread T2:
synchronized (lockB) {
    synchronized (lockA) {
        // Xử lý cần cả B và A.
    }
}
~~~

Nếu T1 đang giữ A và T2 giữ B, cả hai lần khóa trong có thể chờ nhau vô hạn. **Không thêm Thread.sleep() để “giải quyết”**: sleep không nhả monitor. Đổi Thread.yield() cũng không nhả monitor hay tạo một giao thức tránh vòng.

Không nên để ví dụ thực hành tạo deadlock vô hạn ở main vì chương trình có thể treo. Các ví dụ bên dưới dùng **thứ tự khóa đúng** hoặc fail-fast để có điểm kết thúc và stdout xác định.

## 6. Ví dụ 1: sắp xếp ReentrantLock theo rank

Một helper lấy hai khóa theo rank tăng dần, bất kể người gọi truyền đối số theo thứ tự nào. Vì hai thread có thể yêu cầu cùng hai tài nguyên theo chiều đối nghịch, helper cần tạo **cùng thứ tự thực tế** trước khi lock.

Tệp **OrderedLocksDemo.java**:

~~~java
import java.util.concurrent.locks.ReentrantLock;

public class OrderedLocksDemo {
    static final class RankedLock {
        final int rank;
        final ReentrantLock lock = new ReentrantLock();

        RankedLock(int rank) {
            this.rank = rank;
        }
    }

    static void withBoth(
            RankedLock a, RankedLock b, Runnable action) {

        if (a == b) {
            a.lock.lock();
            try {
                action.run();
            } finally {
                a.lock.unlock();
            }
            return;
        }

        if (a.rank == b.rank) {
            throw new IllegalArgumentException("ranks must be unique");
        }

        RankedLock first = a.rank < b.rank ? a : b;
        RankedLock second = a.rank < b.rank ? b : a;

        first.lock.lock();
        try {
            second.lock.lock();
            try {
                action.run();
            } finally {
                second.lock.unlock();
            }
        } finally {
            first.lock.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        RankedLock left = new RankedLock(10);
        RankedLock right = new RankedLock(20);
        int[] counter = {0};

        Runnable forward = () -> {
            for (int i = 0; i < 10_000; i++) {
                withBoth(left, right, () -> counter[0]++);
            }
        };
        Runnable backward = () -> {
            for (int i = 0; i < 10_000; i++) {
                withBoth(right, left, () -> counter[0]++);
            }
        };

        Thread a = new Thread(forward, "forward");
        Thread b = new Thread(backward, "backward");
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("counter = " + counter[0]);
    }
}
~~~

Lệnh chạy bằng Java 17+:

~~~bash
javac -encoding UTF-8 OrderedLocksDemo.java
java OrderedLocksDemo
~~~

Stdout:

~~~text
counter = 20000
~~~

**Tại sao đúng?**

1. **forward** truyền (left, right), **backward** truyền (right, left), nhưng cả hai đều khóa **rank 10 trước rank 20**.
2. Toàn bộ phép **counter[0]++** được thực hiện khi đã có quyền sở hữu cả hai khóa; các lần tăng không chồng lấn.
3. **finally** ở cả hai cấp bảo đảm nhả khóa được lấy thành công ngay cả khi action ném exception.
4. **join()** giữ main chờ cả hai thread xong trước khi đọc counter, nên stdout có giá trị xác định.
5. Nếu **a == b**, helper chỉ khóa một lần. Nếu hai khóa khác nhau nhưng rank bằng nhau, helper **từ chối** thay vì tự ý chọn thứ tự không nhất quán.

Trong hệ thống thật, không nên chỉ kiểm tra rank bằng nhau cho mỗi cặp rồi cho phép các đoạn code khác gán rank trùng lặp; phải định nghĩa **quy tắc cấp rank và quyền sở hữu tài nguyên** ở cấp thiết kế.

## 7. Ví dụ 2: chuyển tiền hai chiều với synchronized theo ID

Đây là bài toán phổ biến: chuyển tiền từ tài khoản A sang B phải giữ hai số dư nhất quán. Nếu mỗi thread khóa **tài khoản nguồn rồi tài khoản đích**, hai giao dịch ngược chiều dễ tạo A→B và B→A.

Ta áp đặt: **khóa tài khoản có ID nhỏ hơn trước, ID lớn hơn sau**, bất kể chiều chuyển tiền.

Tệp **OrderedTransferDemo.java**:

~~~java
public class OrderedTransferDemo {
    static final class Account {
        final long id;
        final Object monitor = new Object();
        long balance;

        Account(long id, long balance) {
            this.id = id;
            this.balance = balance;
        }
    }

    static boolean transfer(
            Account from, Account to, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (from == to) {
            return true;
        }
        if (from.id == to.id) {
            throw new IllegalArgumentException("ids must be unique");
        }

        Account first = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;

        synchronized (first.monitor) {
            synchronized (second.monitor) {
                if (from.balance < amount) {
                    return false;
                }

                from.balance -= amount;
                to.balance += amount;
                return true;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Account a = new Account(10, 1_000);
        Account b = new Account(20, 1_000);

        Thread ab = new Thread(() -> {
            for (int i = 0; i < 20_000; i++) {
                transfer(a, b, 1);
            }
        }, "a-to-b");

        Thread ba = new Thread(() -> {
            for (int i = 0; i < 20_000; i++) {
                transfer(b, a, 1);
            }
        }, "b-to-a");

        ab.start();
        ba.start();
        ab.join();
        ba.join();

        synchronized (a.monitor) {
            synchronized (b.monitor) {
                System.out.println("total = " + (a.balance + b.balance));
                System.out.println(
                    "nonnegative = "
                        + (a.balance >= 0 && b.balance >= 0)
                );
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 OrderedTransferDemo.java
java OrderedTransferDemo
~~~

Stdout:

~~~text
total = 2000
nonnegative = true
~~~

**Giải thích:**

- Hai thread yêu cầu chuyển tiền **hai chiều**, nhưng mọi lần chuyển đều khóa **ID 10 rồi ID 20**.
- Bước kiểm tra số dư và cập nhật cả hai tài khoản nằm trong cùng vùng giữ hai monitor, nên **tổng tiền được bảo toàn** và không có tài khoản âm do thao tác chuyển có kiểm tra.
- Một số giao dịch có thể trả **false** khi tài khoản nguồn tạm hết tiền; số lần thành công và số dư từng tài khoản **không phải đầu ra xác định** dưới mọi lịch chạy. Chương trình cố ý chỉ in hai bất biến đúng trong mọi lần chạy bình thường.
- Đoạn in cuối khóa hai tài khoản theo cùng thứ tự, tránh tạo đường lấy khóa ngược chiều trong chính phần quan sát.
- **ID phải duy nhất và bất biến** cho mọi Account khác nhau được quản lý bởi quy tắc này. Trong chương trình minh họa, ID 10 và 20 thỏa điều kiện.

**Giới hạn:** Đây chỉ là trạng thái trong một JVM. Chuyển tiền thật cần transaction, số dư/ghi sổ bền vững, kiểm soát overflow, idempotency và chính sách xử lý lỗi. Monitor trong Java không bảo vệ được dữ liệu giữa nhiều process hoặc instance ứng dụng.

## 8. Cùng object, cùng rank và quy tắc tie-break

### Hai tham chiếu trỏ đến cùng một lock

Nếu **a == b**, khóa reentrant có thể được lấy nhiều lần bởi cùng thread, nhưng code phải nhả đủ số lần. Với helper khóa cặp, cách rõ nhất thường là **nhận ra cùng object và khóa một lần**.

Nếu một đối tượng tham gia hai vai trò trong cùng thao tác, cần xác định logic của thao tác đó, không chỉ thay đổi thứ tự khóa. Ví dụ chuyển tiền từ một tài khoản sang chính nó có thể là no-op, nhưng đó là **quy tắc nghiệp vụ**, không phải điều Java Lock tự quyết định.

### Hai object khác nhau nhưng cùng rank

Nếu hai khóa khác nhau có cùng rank, không thể nói “cứ khóa nhỏ trước” khi chưa định nghĩa tie-break. Hai thread có thể chọn thứ tự khác nhau tùy vào vị trí tham số.

Giải pháp:

- **Cấp ID độc nhất** cho mọi khóa trong cùng miền so sánh.
- Dùng **bộ so sánh toàn phần ổn định** trên các thuộc tính bất biến (ví dụ ID + loại tài nguyên, nếu cặp này thực sự duy nhất).
- Với những trường hợp buộc phải có giá trị so sánh trùng, dùng **một khóa phân xử chung (*tie lock*)** cho trường hợp trùng, hoặc thiết kế lại hệ thống cấp rank.

Không cố “chọn đại theo chiều truyền tham số” khi bằng rank vì nó tái tạo đúng lỗi hai thứ tự đối nghịch.

### Vì sao identityHashCode không luôn đủ?

**System.identityHashCode(object)** cung cấp giá trị hash liên quan identity, **không cam kết không va chạm** giữa hai object khác nhau. Nếu so hash để sắp xếp khóa, phải xử lý tình huống hash bằng nhau.

Đoạn sau là **mã minh họa giải pháp tie-lock**, không phải một ứng dụng hoàn chỉnh:

~~~java
private static final Object TIE_LOCK = new Object();

static void withPair(Object a, Object b, Runnable action) {
    if (a == b) {
        synchronized (a) {
            action.run();
        }
        return;
    }

    int ha = System.identityHashCode(a);
    int hb = System.identityHashCode(b);

    if (ha < hb) {
        synchronized (a) {
            synchronized (b) {
                action.run();
            }
        }
    } else if (ha > hb) {
        synchronized (b) {
            synchronized (a) {
                action.run();
            }
        }
    } else {
        synchronized (TIE_LOCK) {
            synchronized (a) {
                synchronized (b) {
                    action.run();
                }
            }
        }
    }
}
~~~

Mảnh code được đặt trong một class để biên dịch; action là thao tác giả định cần cả hai monitor. **TIE_LOCK** phải là **một đối tượng dùng chung** cho toàn bộ giao thức. Khi hash trùng, chỉ một thread có thể đồng thời đi vào đường tie, nên hai đường truyền tham số trái chiều không lấy hai monitor theo thứ tự đối nghịch song song. Khi dùng cách này, phải áp dụng nhất quán cho **mọi đường lấy các khóa có liên quan**; nếu code khác giữ a hoặc b rồi mới yêu cầu TIE_LOCK, vẫn có thể tạo vòng chờ.

Với thiết kế mới, một ID toàn cục duy nhất, bất biến thường dễ chứng minh hơn việc dựa vào identity hash và tie lock.

## 9. Trường hợp nhiều hơn hai khóa

Với ba hay nhiều khóa, nguyên tắc không thay đổi: **thu thập danh sách khóa, loại các reference trùng, sắp xếp theo thứ tự toàn cục, lấy từ thấp tới cao và nhả ngược lại**.

Ví dụ pseudocode:

~~~text
input: danh sách resource cần khóa
1. Xác minh ID/rank bất biến, không có hai resource khác nhau mang cùng rank.
2. Loại reference trùng hoặc gộp yêu cầu trên cùng resource.
3. Sort theo rank tăng dần.
4. Acquire lần lượt tất cả các lock.
5. Khi đã giữ đủ, kiểm tra và thay đổi bất biến nghiệp vụ.
6. Release đúng các lock thực sự đã acquire theo chiều ngược lại.
~~~

Khi áp dụng với **ReentrantLock**, dùng nested try/finally hoặc ghi nhận rõ các khóa đã lấy thành công trong một cấu trúc stack; nhả trong finally. Nếu acquisition của khóa thứ ba thất bại hoặc bị interrupt, phải nhả hai khóa đầu **mà thread thực sự đang giữ**.

Với **synchronized**, không có phương thức enter/exit tùy ý bằng danh sách runtime mà không tổ chức cấu trúc lồng nhau hoặc helper đệ quy. Nếu tự tạo helper đệ quy, đảm bảo cùng thứ tự với mọi đường vào khác và không có callback lấy thêm khóa rank thấp hơn.

### Khi danh sách khóa thay đổi động

Một thread đã giữ rank 50 rồi mới phát hiện nó cần rank 10 **không được lấy thêm rank 10 ngay**. Các lựa chọn tùy hợp đồng:

- Tính toán trước tập tài nguyên, rồi lấy đúng thứ tự.
- Nhả những khóa đang giữ, xây dựng lại danh sách, sắp xếp và thử lại. **Phải kiểm tra lại điều kiện nghiệp vụ** vì trạng thái có thể đã đổi sau khi nhả.
- Thiết kế API để caller không thể yêu cầu thêm khóa rank thấp trong lúc đang giữ rank cao.
- Dùng mô hình ownership/message passing nếu giao dịch cần nhiều tài nguyên động đến mức hierarchy trở nên khó quản lý.

Đừng tự coi việc nhả và lấy lại khóa như một giao dịch không có kẽ hở. **Nhả khóa** cho phép thread khác cập nhật dữ liệu; mọi snapshot đọc trước đó có thể đã lỗi thời.

## 10. Ví dụ 3: kiểm tra thứ bậc bằng fail-fast trong lúc phát triển

Trong ứng dụng lớn, lock hierarchy được ghi trong tài liệu có thể bị phá khi code mới gọi helper khác. Có thể xây dựng **runtime checker** để phát hiện thao tác lấy khóa sai thứ tự ngay khi chạy test.

Ví dụ sau chủ động áp dụng quy tắc **rank phải tăng nghiêm ngặt** cho các khóa được quản lý; checker **không cho phép lấy lại cùng rank**, dù ReentrantLock thuần có hỗ trợ reentrancy. Đây là chính sách của checker để dễ phát hiện vi phạm, không phải hạn chế của ReentrantLock.

Tệp **LockRankCheckerDemo.java**:

~~~java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;

public class LockRankCheckerDemo {
    private static final ThreadLocal<Deque<Integer>> HELD =
            ThreadLocal.withInitial(ArrayDeque::new);

    static final class RankedLock {
        private final int rank;
        private final ReentrantLock lock = new ReentrantLock();

        RankedLock(int rank) {
            this.rank = rank;
        }

        void acquire() {
            Deque<Integer> held = HELD.get();
            if (!held.isEmpty() && rank <= held.peek()) {
                throw new IllegalStateException("lock order violation");
            }

            lock.lock();
            held.push(rank);
        }

        void release() {
            Deque<Integer> held = HELD.get();

            if (!lock.isHeldByCurrentThread()
                    || held.isEmpty()
                    || held.peek() != rank) {
                throw new IllegalStateException("invalid unlock order");
            }

            lock.unlock();
            held.pop();
            if (held.isEmpty()) {
                HELD.remove();
            }
        }
    }

    public static void main(String[] args) {
        RankedLock low = new RankedLock(10);
        RankedLock high = new RankedLock(20);

        low.acquire();
        try {
            high.acquire();
            try {
                System.out.println("ascending = allowed");
            } finally {
                high.release();
            }
        } finally {
            low.release();
        }

        high.acquire();
        try {
            try {
                low.acquire();
            } catch (IllegalStateException ex) {
                System.out.println("descending = rejected");
            }
        } finally {
            high.release();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 LockRankCheckerDemo.java
java LockRankCheckerDemo
~~~

Stdout:

~~~text
ascending = allowed
descending = rejected
~~~

**Điểm cần hiểu:**

- **ThreadLocal<Deque<Integer>>** lưu stack rank mà **thread đang gọi** đã acquire qua checker.
- Mỗi lần acquire, rank mới phải **lớn hơn** rank đang ở đỉnh stack; nếu không, ném exception **trước khi cố lấy khóa**, tránh tự tạo vòng chờ ngay trong demo.
- Mỗi lần release yêu cầu đúng owner và đúng thứ tự LIFO; **finally** giúp cân bằng quyền sở hữu khóa.
- Sau khi thả lock cuối cùng, HELD.remove() tránh giữ ThreadLocal state không cần thiết ở thread có thể được tái sử dụng.
- Demo chỉ minh họa một checker đơn giản cho **quy tắc rank tĩnh và API đóng kín**. Nó không biết khóa được lấy trực tiếp bằng synchronized, ReentrantLock khác, callback bên ngoài hay tài nguyên giữa process; không nên xem nó như detector hoàn chỉnh cho mọi deadlock.

Nếu ứng dụng cần một thread lấy lại **cùng** khóa, checker thực tế phải phân biệt **reentrant same-object** với **different-object same-rank**, theo dõi identity và số mức giữ phù hợp. Không chỉ bỏ điều kiện rank bằng nhau nếu như vậy cho phép hai đối tượng khác nhau cùng rank bị khóa theo thứ tự tùy ý.

## 11. tryLock() và timeout: cách khác để không chờ vô hạn

**ReentrantLock.tryLock()** thử lấy khóa ngay và trả về boolean. **tryLock(timeout, TimeUnit)** có thể chờ tối đa một khoảng và ném InterruptedException khi bị ngắt. Nếu không lấy được khóa thứ hai, caller có thể **nhả khóa thứ nhất, chờ một khoảng phù hợp rồi thử lại**.

Nguyên tắc bắt buộc khi “all-or-none acquisition”:

1. Ghi nhận **những khóa đã thực sự lấy thành công**.
2. Không thực hiện side effect nghiệp vụ khi chưa đủ quyền sở hữu cần thiết.
3. Nếu một khóa không lấy được, nhả **toàn bộ khóa đã lấy** trong finally.
4. Nếu retry, làm lại kiểm tra điều kiện vì trạng thái có thể thay đổi khi khóa đã được nhả.
5. Cần giới hạn lượt thử, deadline hoặc đường hủy để tránh retry vô hạn trong tình trạng tranh chấp.

### Ví dụ 4: thất bại khi lấy khóa thứ hai và cleanup an toàn

Tệp **TryLockCleanupDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class TryLockCleanupDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock first = new ReentrantLock();
        ReentrantLock second = new ReentrantLock();
        CountDownLatch finished = new CountDownLatch(1);

        second.lock(); // Main cố ý giữ second khi worker thử lấy.
        try {
            Thread worker = new Thread(() -> {
                first.lock();
                try {
                    boolean gotSecond = second.tryLock();
                    try {
                        System.out.println(
                            "got second = " + gotSecond
                        );
                    } finally {
                        if (gotSecond) {
                            second.unlock();
                        }
                    }
                } finally {
                    first.unlock(); // Dù tryLock thất bại.
                    finished.countDown();
                }
            }, "worker");

            worker.start();
            finished.await();
            worker.join();
            System.out.println("first locked = " + first.isLocked());
        } finally {
            second.unlock();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 TryLockCleanupDemo.java
java TryLockCleanupDemo
~~~

Stdout:

~~~text
got second = false
first locked = false
~~~

**Giải thích:** Main giữ second cho đến sau khi worker hoàn thành, nên tryLock(second) của worker phải trả false. Worker **không unlock second** vì nó chưa sở hữu; nó vẫn phải nhả first trong finally. Main chờ tín hiệu hoàn tất rồi join để đọc isLocked() khi worker đã nhả first, cho stdout xác định.

Ví dụ này minh họa cleanup khi acquisition thất bại, **không phải đề xuất thay mọi thứ tự khóa bằng tryLock**. Trong ứng dụng có nhiều worker cùng retry, nếu họ cùng giành được khóa đầu rồi cùng thất bại ở khóa sau, họ có thể liên tục nhả rồi tranh lại mà không có ai hoàn tất (**livelock**). Retry cần backoff/giới hạn/hủy thích hợp; **lock hierarchy vẫn thường là phương án dễ chứng minh hơn**.

### Interrupt có tự phá deadlock không?

- Thread chờ vào **synchronized monitor** không thể coi interrupt là cách lấy lại khóa hay ép chủ sở hữu nhả monitor.
- **ReentrantLock.lockInterruptibly()** cho phép bỏ việc *đang chờ lấy khóa* khi bị interrupt, nhưng không tự nhả những khóa thread đó **đã giữ**.
- **tryLock(timeout, unit)** giúp caller không chờ mãi ở một lần acquisition; caller vẫn phải chủ động cleanup các khóa trước đó.
- **Thread.interrupt()** là yêu cầu hợp tác, không phải lệnh cưỡng chế rollback hay mở khóa của thread khác.

Vì vậy, **phòng deadlock bằng thứ tự khóa nhất quán** là hướng chính khi bài toán cho phép áp dụng. Timeout và interrupt là các tính năng bổ sung cho điều khiển tiến độ và hủy an toàn.

## 12. Thứ tự khóa với synchronized, ReentrantLock và Condition

### synchronized

Khi dùng intrinsic monitor, vùng nested synchronized thể hiện rõ thứ tự yêu cầu:

~~~java
synchronized (lowerRankLock) {
    synchronized (higherRankLock) {
        // Critical section cần cả hai.
    }
}
~~~

Đây là đoạn minh họa trong một phương thức; hai biến phải là reference object khóa chung, đã được gán rank theo hợp đồng. Khi thoát scope, Java nhả monitor tương ứng kể cả có exception.

Không có API trực tiếp để đặt timeout cho **monitor enter** như tryLock(timeout). Vì vậy càng cần tránh các chuỗi khóa lồng nhau không có thứ tự.

### ReentrantLock

Dùng hai tầng try/finally:

~~~java
low.lock();
try {
    high.lock();
    try {
        update();
    } finally {
        high.unlock();
    }
} finally {
    low.unlock();
}
~~~

Đây là đoạn minh họa; low/high là các ReentrantLock đã xác định rank, update() là thao tác cần bảo vệ. Nếu high.lock() có exception bất thường trước khi acquire, finally bên ngoài vẫn nhả low. Trong trường hợp bình thường, thread lấy cả hai theo thứ tự ổn định rồi nhả ngược.

### Condition và việc tạm thời nhả khóa

**Condition.await()** cần thread hiện tại đang giữ ReentrantLock tương ứng; khi chờ, nó **nhả chính khóa liên kết với condition**, rồi lấy lại khóa trước khi trả về theo hợp đồng. Nó **không nhả mọi khóa khác** mà thread đang giữ.

Nếu đang giữ **low**, rồi **high**, sau đó gọi await() trên condition của **high**, thread có thể vẫn giữ low trong khi chờ điều kiện. Điều này có thể khiến thread khác cần low không thể thực hiện hành động giúp điều kiện high trở thành đúng. Quy tắc rank đơn giản vì thế **không thay thế phân tích giao thức await/signal, join hay callback**.

Ưu tiên thiết kế tránh giữ nhiều lock trong lúc chờ condition kéo dài, hoặc chứng minh rõ tín hiệu có thể được phát mà không cần những khóa đang bị giữ.

## 13. Deadlock, starvation, livelock và contention không giống nhau

| Hiện tượng | Mô tả | Lock hierarchy giải quyết đến đâu? |
| --- | --- | --- |
| **Deadlock** | Các thread tạo vòng chờ, không thể tiếp tục theo giao thức hiện tại | Ngăn vòng chờ do các khóa tuân thủ rank, không ngăn mọi loại deadlock do tài nguyên khác |
| **Starvation** | Một thread liên tục không được cấp CPU/khóa/tài nguyên trong khi thread khác tiến bộ | Không bảo đảm fairness hoặc thời gian chờ hữu hạn |
| **Livelock** | Thread liên tục phản ứng, retry/nhả khóa nhưng không hoàn thành công việc hữu ích | Không tự ngăn lỗi retry thiếu chính sách |
| **Contention** | Nhiều thread muốn cùng khóa nên một số phải chờ | Vẫn có, ngay cả khi thứ tự đúng |

Một chương trình tránh deadlock vẫn có thể chậm do tranh chấp khóa, starvation hoặc giữ khóa quá lâu. Với ReentrantLock, **fair=true** có các quy tắc ưu tiên nhất định nhưng không bảo đảm lịch CPU công bằng tuyệt đối; **tryLock()** không timeout còn có thể vượt hàng ngay cả khi lock được tạo fair.

Không nên viết bài test “không bị treo trong một giây nên không có deadlock”. Một lịch gây vòng chờ có thể chưa xuất hiện trong bài test đó. Phải xem **mọi thứ tự khóa có thể xảy ra** và lập luận từ cấu trúc chương trình.

## 14. Lock hierarchy khi gọi helper, callback và thư viện

Một trong những nguồn vi phạm hierarchy là một phương thức “vô hại” thực ra tự lấy thêm khóa:

~~~text
ServiceA.process():
  giữ lock(rank 30)
  gọi ServiceB.validate()
    -> ServiceB lấy lock(rank 10)
       => VI PHẠM 30 -> 10
~~~

Đôi khi callback hoặc phương thức override chạy dưới một lock lại gọi ngược về lớp đang giữ khóa khác, tạo **lock inversion** khó thấy trong code gọi trực tiếp. Hãy:

- Ghi rõ **lock rank** trong tài liệu của những API có khóa.
- Tránh gọi callback, hook, logger tùy biến, code plugin hoặc I/O bên ngoài khi đang giữ lock nếu không kiểm soát được khóa mà chúng sử dụng.
- Hạn chế **nested locks** bằng cách tổ chức lại quyền sở hữu dữ liệu hoặc lấy snapshot immutable trước khi gọi code ngoài.
- Với một thao tác cần nhiều tài nguyên, thu thập **đủ tập khóa** từ đầu rồi điều phối tập đó qua một helper duy nhất.
- Dùng checker trong test để phát hiện các lần acquire sai rank; không dựa vào mỗi lần stress test “chạy ổn”.

**Không có thứ tự khóa tự động cho mọi thư viện Java**. Một ReentrantLock tự tạo không biết rằng “rank của tôi là 10”; JVM không tự phát hiện việc dùng nó sai thứ tự. Rank chỉ có hiệu lực khi mọi đường sử dụng tuân thủ hợp đồng mà bạn thiết kế.

### Không để rank thay đổi sau khi khóa đã được công bố

Nếu rank lấy từ số dư, mức ưu tiên của task, thời gian tạo hoặc trường mutable, hai thread có thể sắp xếp cặp khóa khác nhau tại các thời điểm khác nhau. Rank của lock nên là thuộc tính **bất biến**; khi cần thay đổi cấu trúc nghiệp vụ, phải có giao thức tái cấu hình an toàn.

### Khóa theo ID không giải quyết dữ liệu giữa nhiều JVM

Hai JVM có thể có hai object lock riêng dù đại diện cùng account ID. Thứ tự khóa trên Java object trong JVM A **không loại trừ** các cập nhật từ JVM B. Với database, distributed storage hoặc tài nguyên từ xa, cần transaction/optimistic concurrency/lock của nơi lưu trữ thích hợp. Lock hierarchy cục bộ vẫn có ích để tránh deadlock **bên trong** mỗi JVM, nhưng không thay thế giao thức đồng bộ giữa tiến trình.

## 15. Thiết kế API để không thể lấy khóa sai thứ tự

Một API tốt giảm khả năng người gọi phải tự nhớ thứ tự:

- Đóng gói các lock thành **private final** field; tránh trả trực tiếp ReentrantLock ra ngoài để mọi nơi tự acquire tùy ý.
- Tạo một phương thức giao dịch như **transfer(from, to, amount)** hoặc **withLocks(resources, action)**; nó sắp xếp ID, lấy khóa, thực hiện hành động rồi nhả.
- Nếu cho phép nested lock, thiết kế thứ bậc và các method của từng tầng rõ ràng.
- Dùng immutable object/copy-on-write hoặc truyền thông điệp khi có thể tránh shared mutable state.
- Phân biệt trạng thái **đã khóa đủ** với trạng thái **đang tìm cách lấy** để không thực hiện side effect sớm.

Nếu một thao tác phải khóa danh sách đối tượng có thể thay đổi do thread khác tác động, lấy một snapshot của danh sách **chưa đủ** nếu đối tượng có thể bị thêm/bớt và bất biến áp dụng cho toàn bộ tập. Cần một giao thức bảo vệ **cả danh sách thành viên** (ví dụ khóa cấp cao hơn hoặc version và kiểm tra lại dưới khóa). Lock ordering là một phần của giải pháp, không tự giải quyết consistency của topology động.

## 16. Chẩn đoán deadlock trong Java

Khi nghi ngờ deadlock do monitor hoặc ownable synchronizer:

1. **Thu thread dump** khi hệ thống có dấu hiệu mắc kẹt. Có thể dùng các công cụ JDK như **jcmd <pid> Thread.print**, **jstack <pid>** trong môi trường cho phép.
2. Tìm những thread đang giữ monitor/lock nào, đang đợi lock nào; so sánh các stack trace có nested synchronized hoặc ReentrantLock acquisition.
3. Vẽ **wait-for graph**: thread A chờ tài nguyên do thread B giữ, B chờ C, C chờ A.
4. Kiểm tra **đường gọi** nào tạo cạnh lấy khóa theo chiều ngược hierarchy.
5. Sửa tại thiết kế: thống nhất thứ tự và API acquisition; không chỉ tăng timeout hay thêm log/sleep.
6. Thêm regression test cho cặp thao tác đảo tham số (A→B và B→A), xác nhận bất biến sau khi tất cả worker đã join.

**ThreadMXBean** cung cấp những phương thức như **findDeadlockedThreads()** để tìm chu trình deadlock trên object monitors hoặc ownable synchronizers mà JVM có thể phát hiện. Kết quả null không chứng minh chương trình không có deadlock ở thời điểm khác hoặc ở tài nguyên ngoài phạm vi quan sát. Một thread dump cũng có thể thiếu quyền truy cập/không ghi nhận tài nguyên ngoài JVM như database locks.

### Thử nghiệm không tạo deadlock cố ý

Một test có thể khởi chạy nhiều thread dùng helper sắp xếp khóa, sau đó **join với timeout** và assert các bất biến. Timeout giúp phát hiện việc test có thể đang treo; nó không tự sửa worker. Nếu test timeout, nên thu stack/lock info để chẩn đoán, rồi đảm bảo test không giữ non-daemon thread vĩnh viễn làm nghẽn cả test suite.

Các ví dụ runnable trong bài này tránh chương trình deadlock không có đường thoát. Muốn nghiên cứu lịch lỗi, nên dùng các môi trường/thử nghiệm cô lập và cơ chế dừng an toàn; không đưa mẫu deadlock vô hạn vào đường chạy mặc định của ứng dụng.

## 17. Bảng lỗi phổ biến

| Lỗi thiết kế | Rủi ro | Cách khắc phục |
| --- | --- | --- |
| T1 lấy A→B, T2 lấy B→A | Vòng chờ hai khóa | Thống nhất A→B ở mọi đường |
| Sắp xếp theo tham số đầu vào | Hai lời gọi đảo chiều có thể khóa ngược | Sắp theo ID/rank bất biến, không theo tham số |
| Rank thay đổi trong runtime | Hai thread có thể thấy thứ tự khác nhau | Gán rank ổn định theo hợp đồng |
| Hai khóa khác nhau cùng rank nhưng không tie-break | Không có thứ tự toàn phần | ID duy nhất hoặc giao thức tie lock |
| Tin identityHashCode không bao giờ collision | Có thể rơi vào đường xử lý thứ tự không xác định | Thiết kế tie-break an toàn |
| Một method lấy thêm khóa rank thấp dưới khóa rank cao | Vi phạm hierarchy ẩn trong helper | Đóng gói API, kiểm tra toàn call graph |
| Không nhả khóa đã lấy khi tryLock thứ hai thất bại | Giữ tài nguyên, tăng nguy cơ deadlock | finally nhả mọi phần đã acquire |
| Chạy nghiệp vụ trước khi đủ toàn bộ khóa | Side effect dở dang khi acquisition thất bại | Chỉ thực thi sau khi acquire đủ và kiểm tra invariant |
| Đợi join/await khi giữ khóa người khác cần | Có thể tạo vòng chờ ngoài việc giành lock | Phân tích dependency, tránh giữ khóa khi chờ |
| Dùng sleep/yield để “phá” deadlock | Không nhả khóa hay bảo đảm progress | Đổi lock order/giao thức |
| Bắt InterruptedException rồi bỏ qua và giữ khóa | Không có đường cleanup/hủy đúng | Xử lý interrupt và finally an toàn |
| Thấy getState() không BLOCKED nên kết luận không deadlock | ReentrantLock có cơ chế chờ khác monitor | Đọc lock owner/wait-for graph, dump |
| Dùng lock hierarchy trong một JVM để bảo vệ DB nhiều instance | Không có mutual exclusion liên tiến trình | Đồng bộ tại nơi dữ liệu được chia sẻ |
| Chỉ test chạy một lần không treo | Lịch gây cycle có thể chưa xảy ra | Chứng minh order + test đảo chiều + diagnostic |

## 18. Checklist cho code review

**Thiết kế:**

- [ ] Mỗi lock có **vai trò và rank/ID** rõ ràng, ổn định, phân biệt được khi cần.
- [ ] Mọi nested acquisition đều đi theo **một chiều tăng**, không có helper/callback âm thầm đi ngược.
- [ ] Nếu hai reference trỏ cùng object, có đường xử lý rõ; nếu khác object nhưng rank trùng, có tie-break đúng.
- [ ] Những bước thay đổi nhiều dữ liệu chung nằm trong critical section bảo vệ **toàn bộ invariant**.
- [ ] Không giữ một lock thấp trong lúc chờ sự kiện chỉ có thể xảy ra khi thread khác lấy được lock đó.

**Triển khai:**

- [ ] Với ReentrantLock, mỗi lần acquire thành công có **finally unlock** cùng thread, đúng số lần.
- [ ] Với tryLock thất bại/interrupt, nhả mọi khóa đã giữ và không tạo side effect nghiệp vụ quá sớm.
- [ ] Khi nhả rồi retry, đọc lại trạng thái và giới hạn retry/backoff theo yêu cầu.
- [ ] Không gọi I/O/callback hoặc giữ monitor trong lúc ngủ dài nếu không thật sự cần cho bất biến.
- [ ] Không sửa rank hay đối tượng lock sau khi đã công bố cho các thread khác.

**Kiểm thử & vận hành:**

- [ ] Có test hai chiều A→B và B→A, xác nhận tiến độ và bất biến.
- [ ] Có cách thu thread dump/ThreadMXBean nếu bị chặn.
- [ ] Có đường xử lý lỗi, timeout và cleanup phù hợp, không thay chúng bằng lịch chạy đoán trước.
- [ ] Nêu rõ phạm vi **một JVM** của các Java locks và ranh giới DB/tài nguyên ngoài tiến trình.

## 19. Tự kiểm tra

**Câu 1.** Tại sao T1 lấy A rồi B và T2 lấy B rồi A có thể deadlock?  
**Trả lời:** Hai thread có thể mỗi người giữ một khóa, đồng thời chờ khóa do người kia giữ. Đó là circular wait.

**Câu 2.** Quy tắc lấy khóa rank tăng dần loại bỏ điều kiện nào của deadlock?  
**Trả lời:** Loại bỏ **circular wait** trong tập khóa được quản lý đúng bởi quy tắc đó.

**Câu 3.** Nếu cả hai hàm transfer(A,B) và transfer(B,A) đều khóa account có ID nhỏ hơn trước, deadlock kiểu A↔B còn xảy ra không?  
**Trả lời:** Không do cặp monitor ấy theo hai chiều đối nghịch, nếu mọi đường lấy các monitor liên quan đều tuân thủ cùng thứ tự. Các tài nguyên khác vẫn cần phân tích riêng.

**Câu 4.** Vì sao không dùng System.identityHashCode làm rank duy nhất mặc nhiên?  
**Trả lời:** Hai object khác nhau vẫn có thể có cùng identity hash. Cần tie-break ổn định.

**Câu 5.** Khi đã giữ rank 30 nhưng phát hiện cần rank 10, nên làm gì?  
**Trả lời:** Không lấy tiếp rank 10 dưới rank 30. Có thể nhả, xác định lại tập tài nguyên, sắp xếp rồi acquire và kiểm tra lại trạng thái; hoặc thiết kế lại API.

**Câu 6.** tryLock(second) trả false khi đang giữ first. Có được tiếp tục cập nhật dữ liệu đòi cả hai lock không?  
**Trả lời:** Không. Phải nhả first và xử lý thất bại/retry theo giao thức, trừ khi nghiệp vụ có một phương án được chứng minh không cần second.

**Câu 7.** Fair ReentrantLock có thay thế lock hierarchy không?  
**Trả lời:** Không. Fairness không phá vòng chờ giữa các khóa khác nhau.

**Câu 8.** Condition.await() có nhả mọi khóa thread đang giữ không?  
**Trả lời:** Không. Nó chỉ nhả khóa liên kết với Condition theo hợp đồng; các khóa khác vẫn có thể bị giữ trong lúc chờ.

**Câu 9.** Một hệ thống không deadlock vẫn có thể chậm hoặc treo tiến độ vì sao?  
**Trả lời:** Có thể do contention, starvation, livelock, I/O hoặc chính sách chờ khác; tránh vòng chờ không hứa tiến độ hữu hạn.

**Câu 10.** Vì sao Java ReentrantLock giữa hai JVM không bảo vệ cùng một tài khoản trong DB?  
**Trả lời:** Hai process sở hữu các lock object khác nhau. Cần cơ chế đồng bộ ở tầng kho dữ liệu chung.

## 20. Tổng kết

**Lock hierarchy** là phương pháp phòng deadlock bằng cách áp đặt **một thứ tự toàn cục, ổn định khi lấy nhiều khóa**: sắp theo rank/ID, acquire từ thấp đến cao, release theo chiều ngược, xử lý object trùng và tie-break rõ ràng. Nó giải quyết vòng chờ do **thứ tự khóa đối nghịch**, nhưng không tự bảo đảm fairness, không loại bỏ mọi contention, và không tự giải quyết chờ condition, callback, I/O hay tài nguyên giữa các JVM.

Trong Java, hãy đóng gói các đoạn lấy nhiều monitor hoặc ReentrantLock vào API kiểm soát thứ tự, dùng **finally** để nhả mọi khóa thực sự sở hữu, kiểm tra lại invariant khi có retry, và kiểm chứng bằng test đảo chiều cùng thread dump khi cần. Cách tiếp cận đúng bắt đầu từ **đồ thị phụ thuộc khóa và bất biến dữ liệu**, không phải từ sleep, yield hay kỳ vọng scheduler sẽ may mắn chọn lịch không lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

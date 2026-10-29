<a id="back-to-top"></a>

# Phối hợp luồng

## Menu
- [wait(), notify(), notifyAll() hoạt động trong monitor](#thread-coordination-001)
- [Điều kiện chờ: while thay vì if](#thread-coordination-002)
- [Spurious wakeup và kiểm tra lại invariant](#thread-coordination-003)
- [Producer–consumer với hàng đợi giới hạn](#thread-coordination-004)
- [BlockingQueue: put(), take(), offer(), poll()](#thread-coordination-005)
- [Timeout khi chờ dữ liệu hoặc tài nguyên](#thread-coordination-006)
- [Lock + Condition cho nhiều điều kiện chờ](#thread-coordination-007)
- [LockSupport.park()/unpark() và permit](#thread-coordination-008)
- [Missed signal và lost notification: nguyên nhân](#thread-coordination-009)
- [Poison pill và kết thúc consumer có trật tự](#thread-coordination-010)
- [Tránh busy waiting bằng blocking primitives](#thread-coordination-011)
- [So sánh phối hợp bằng shared state và message passing](#thread-coordination-012)

## <a id="thread-coordination-001">wait(), notify(), notifyAll() hoạt động trong monitor</a>

<details>
<summary>Xem chi tiết</summary>

# `wait()`, `notify()`, `notifyAll()` hoạt động trong monitor như thế nào?

Trong Java, `Object.wait()`, `Object.notify()` và `Object.notifyAll()` là các primitive phối hợp luồng ở cấp thấp, gắn trực tiếp với **intrinsic monitor** của mỗi object. Chúng cho phép một thread **tạm nhả monitor và chờ điều kiện**, còn thread khác **thay đổi trạng thái được bảo vệ rồi gửi tín hiệu** để các thread đang chờ có cơ hội kiểm tra lại điều kiện đó.

Ba phương thức này dễ bị dùng sai vì có ba khái niệm tách biệt:

1. **Có quyền vào critical section:** thread đã lấy được monitor của object.
2. **Điều kiện nghiệp vụ đã đúng:** ví dụ queue có phần tử, cổng đã mở, buffer còn chỗ.
3. **Thread được đánh thức:** có tín hiệu, interruption, timeout hoặc spurious wakeup; điều này **không tự chứng minh** điều kiện nghiệp vụ đã đúng.

Một thiết kế đúng phải bảo vệ **predicate (điều kiện trạng thái)** dưới **cùng monitor**, gọi `wait()` trong **vòng `while`** kiểm tra predicate, và cập nhật trạng thái trước khi gọi `notify/notifyAll`. Chỉ gọi `notify()` không phải là "gửi một permit" mà thread chưa bắt đầu chờ có thể giữ lại để dùng sau.

Bài này thuộc phần **Phối hợp luồng** của **Java concurrency nói chung**, với JDK 17+ và các API cốt lõi của `java.lang` / `java.util.concurrent`. Không bàn tới cơ chế xử lý luồng riêng của Spring Framework.

## 1. Mỗi đối tượng Java có một intrinsic monitor

Về mặt ngôn ngữ, mỗi object có monitor phục vụ các thao tác `synchronized`, `wait` và `notify`. Khi thread chạy:

```java
synchronized (lock) {
    // critical section
}
```

nó phải sở hữu **monitor của chính object `lock`**. Thread khác cũng muốn `synchronized (lock)` phải chờ cho đến khi monitor được nhả.

Hai dạng method cũng dùng monitor:

| Cú pháp | Monitor liên quan |
| --- | --- |
| `synchronized (lock) { ... }` | Object được tham chiếu bởi `lock` |
| `synchronized void method() { ... }` | `this` |
| `static synchronized void method() { ... }` | Object `Class` của lớp tương ứng |

Monitor giữ thông tin sở hữu và mức vào lại (intrinsic monitor **reentrant**). Nếu một thread đang sở hữu monitor `M`, chính thread đó có thể gọi lại method `synchronized` dùng `M` mà không bị self-deadlock chỉ vì lock này. Nhưng các thread khác vẫn không thể sở hữu `M` đồng thời.

Tính đồng bộ ở đây có thêm ý nghĩa của **Java Memory Model**: unlock monitor happens-before lần lock tiếp theo trên cùng monitor. Vì thế, những ghi được thực hiện theo cùng giao thức khóa có thể được thread khác quan sát phù hợp khi nó lấy monitor.

## 2. Ba phương thức thuộc `Object`, không phải `Thread`

Các method này được định nghĩa trên `java.lang.Object`; object dùng để chờ/báo có thể là một monitor riêng do class tạo:

```java
private final Object lock = new Object();
```

Đây là khai báo field minh họa trong một class; không phải chương trình độc lập. Những chữ ký quan trọng:

```java
public final void wait() throws InterruptedException;
public final void wait(long timeoutMillis)
        throws InterruptedException;
public final void wait(long timeoutMillis, int nanos)
        throws InterruptedException;

public final void notify();
public final void notifyAll();
```

Các chữ ký trên được trình bày theo cú pháp khai báo API để học, không phải mã đặt vào một class mới.

- **`wait()`:** thread đang sở hữu monitor của object gọi phương thức sẽ **nhả monitor đó** và chờ cho đến khi đủ điều kiện để tiếp tục (được notify, interrupt hoặc thức dậy khác theo hợp đồng), rồi **phải lấy lại monitor** trước khi method trả về hoặc ném `InterruptedException`.
- **`notify()`:** chọn **một** thread tùy ý trong nhóm thread đang `wait` trên **cùng object**, nếu có. Không cam kết công bằng, FIFO, chọn theo ưu tiên hay chính xác thread có predicate phù hợp.
- **`notifyAll()`:** đánh thức **tất cả** thread đang `wait` trên **cùng object**. Chúng vẫn phải tranh monitor để vào lại; không chạy phần `synchronized` cùng một lúc.

**Điều kiện bắt buộc:** caller của cả ba method phải đang sở hữu monitor của **object dùng để gọi method**. Nếu không, sẽ gặp `IllegalMonitorStateException`. Điều này áp dụng cho cả `wait`, `notify` và `notifyAll`.

## 3. Tại sao `wait()` bắt buộc đi cùng `synchronized`?

Vì bước **kiểm tra điều kiện và bắt đầu chờ** phải diễn ra như một giao thức atomic đối với những thread cập nhật trạng thái.

Xét:

```java
synchronized (lock) {
    while (!condition) {
        lock.wait();
    }
    // condition đã đúng tại thời điểm thread đang giữ lock.
}
```

Đây là mẫu minh họa với biến `lock` và `condition` thuộc cùng giao thức bảo vệ; đoạn thật phải khai báo `throws InterruptedException` hoặc xử lý exception.

Trình tự:

1. Thread A lấy monitor `lock`.
2. A kiểm tra `condition` **khi đang sở hữu monitor**.
3. Nếu false, A gọi `lock.wait()`. Việc đưa thread vào trạng thái chờ và nhả monitor được phối hợp theo ngữ nghĩa của monitor, tránh khoảng "đã kiểm tra false nhưng signal hợp lệ bị lọt giữa" khi writer cũng dùng cùng monitor.
4. Thread B lấy monitor `lock` khi A đã nhả, đổi `condition = true`, rồi gọi `lock.notifyAll()`.
5. B rời `synchronized`, nhả monitor.
6. A có cơ hội lấy lại monitor `lock`, trở về từ `wait`, **kiểm tra lại predicate**, sau đó mới sử dụng trạng thái.

**Không đặt điều kiện ở một field khác được sửa mà bỏ qua cùng monitor** nếu giao thức phụ thuộc vào việc kiểm tra và chờ nguyên tử. Không đặt `wait` bên ngoài lock rồi hy vọng thêm `volatile` ở một chỗ khác sẽ sửa toàn bộ lost-signal race.

## 4. Chờ, thông báo và lấy lại monitor: ba thời điểm riêng

Sơ đồ với monitor `M`:

```text
Thread A (consumer)                  Thread B (producer)
-------------------                  -------------------
lock(M)
while (queue.isEmpty()):
    wait(M)
    |  A vào wait set của M
    |  A nhả monitor M
    v
  WAITING                             lock(M)
                                      queue.add(item)
                                      notifyAll(M)
                                      | đánh thức waiter ở M
                                      | nhưng B vẫn giữ M
                                      unlock(M)
A phải lấy lại monitor M <-----------+
  có thể tạm BLOCKED khi chờ lock
kiểm tra lại queue.isEmpty()
lấy item
unlock(M)
```

**`notifyAll` không nhả monitor ngay khi được gọi.** Thread B vẫn sở hữu monitor đến khi nó rời vùng `synchronized` hoặc thực hiện một lời gọi `wait` hợp lệ trên cùng monitor. A chỉ có thể tiếp tục phần code sau `wait` khi đã được phép thức và lấy lại monitor.

Từ góc nhìn `Thread.State`, một thread trong `Object.wait()` không timeout thường ở `WAITING`; khi đã được notify nhưng còn chờ vào lại monitor, nó có thể ở `BLOCKED`. `Thread.getState()` chỉ là ảnh chụp tạm thời, không nên dùng nó để điều phối đúng thời điểm gọi `notify`.

## 5. Mẫu chuẩn: guarded block với `while`

Mẫu này là nền tảng cần nhớ:

```java
final class Gate {
    private boolean open;

    synchronized void awaitOpen() throws InterruptedException {
        while (!open) {
            wait();
        }
    }

    synchronized void open() {
        open = true;
        notifyAll();
    }
}
```

`wait()` và `notifyAll()` không có receiver tường minh nên receiver là `this`. Vì phương thức được `synchronized` theo instance, caller đang sở hữu monitor `this` khi gọi cả hai.

Điểm mấu chốt:

- `open` là **predicate trạng thái**, không phải một tín hiệu trôi qua.
- Nếu `open()` được gọi **trước** `awaitOpen()`, waiter vẫn thoát ngay nhờ kiểm tra `open == true`; không cần "ghi nhớ" một notification.
- Nếu waiter đang chờ, `open()` chuyển trạng thái rồi notify, cho waiter cơ hội kiểm tra lại.
- `while` xử lý spurious wakeup, các thread cạnh tranh và tình huống predicate lại thành false (nếu thiết kế cho phép đóng lại).
- Mọi đường đọc/ghi `open` đều tuân theo **cùng monitor**.

Mẫu này là class minh họa chưa có `main`. Ví dụ hoàn chỉnh ngay sau đây có thể biên dịch và chạy độc lập.

## 6. Ví dụ Java 17+: cổng mở bằng `notifyAll()`

Lưu thành `MonitorGateDemo.java`:

```java
public class MonitorGateDemo {
    static final class Gate {
        private boolean open;

        synchronized void awaitOpen() throws InterruptedException {
            while (!open) {
                wait();
            }
        }

        synchronized void open() {
            open = true;
            notifyAll();
        }

        synchronized boolean isOpen() {
            return open;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Gate gate = new Gate();

        Thread worker = new Thread(() -> {
            try {
                gate.awaitOpen();
                System.out.println("Worker passed gate");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "gate-worker");

        worker.start();
        gate.open();    // Có thể diễn ra trước hoặc sau worker.wait().
        worker.join();

        System.out.println("Gate open: " + gate.isOpen());
    }
}
```

Biên dịch/chạy với JDK 17+:

```bash
javac MonitorGateDemo.java
java MonitorGateDemo
```

Stdout trong lần chạy bình thường:

```text
Worker passed gate
Gate open: true
```

Đây là chương trình **không dựa vào `Thread.sleep()` để đoán lúc worker đã chờ**. Nếu main gọi `open()` trước khi worker vào `awaitOpen()`, predicate đã true và worker đi tiếp; nếu main mở sau lúc worker chờ, `notifyAll` đánh thức worker. Cả hai cách đều hợp lệ và cho cùng kết quả.

`join()` đặt sau `open()` giúp main đợi worker kết thúc rồi mới in dòng cuối. Không nên thay nó bằng polling `worker.getState()` hoặc ngủ một khoảng cố định để đoán tiến độ.

Một hạn chế có chủ ý: chương trình không ép worker **nhất định phải vào WAITING**; nó kiểm chứng chính xác protocol "đi tiếp sau khi cổng đã mở", bất kể lịch chạy.

## 7. `notify()` và `notifyAll()` khác nhau ra sao?

Giả sử ba waiter cùng `wait()` trên object `lock`:

```text
wait set của lock: {T1, T2, T3}

lock.notify()
    -> đánh thức một waiter không được chỉ định (ví dụ T2)
    -> T1 và T3 vẫn chờ

lock.notifyAll()
    -> đánh thức mọi waiter trong wait set tại thời điểm đó
    -> T1, T2, T3 phải tranh monitor trước khi kiểm tra lại condition
```

**Không có FIFO guarantee** giữa các waiter chỉ nhờ `notify` hoặc `notifyAll`. Thứ tự tiếp tục phụ thuộc monitor acquisition, scheduling và những yếu tố triển khai khác.

| Tình huống | `notify()` | `notifyAll()` |
| --- | --- | --- |
| Một waiter duy nhất trên monitor | Có thể đủ khi predicate đúng | Cũng đúng, nhưng tổng quát hơn |
| Nhiều waiter cùng điều kiện, một lần cập nhật chỉ cho phép một consumer tiến | Có thể tối ưu nếu có chứng minh chặt chẽ | An toàn hơn khi còn bất định về waiter được chọn |
| Nhiều loại predicate dùng chung monitor | Có nguy cơ đánh thức waiter không thể tiến | Thường dễ chứng minh đúng hơn |
| Chuyển sang trạng thái terminal/close cần mọi waiter biết | Có thể bỏ sót nhiều waiter | Thường phù hợp |
| Có nhiều waiter và notification quá dày | Ít đánh thức hơn | Có thể gây nhiều wakeups và tranh monitor |

**`notifyAll()` không phải "luôn tốt nhất về hiệu năng"**, nhưng thường **dễ đúng hơn** khi condition sharing phức tạp. Nó có thể gây *thundering herd*: nhiều thread cùng thức, cuối cùng chỉ một hoặc vài thread thật sự tiến được, những thread còn lại lại `wait`. Khi hiệu năng quan trọng, cân nhắc `Condition` tách nhóm waiter hoặc các tiện ích ở `java.util.concurrent`.

## 8. Vì sao `notify()` có thể đánh thức sai loại waiter?

Một bounded buffer có hai predicate khác nhau:

- Producer chờ **buffer chưa đầy**.
- Consumer chờ **buffer không rỗng**.

Nếu tất cả chờ trên **cùng monitor**, trong wait set có thể đồng thời chứa producer và consumer. Một lần `notify()` không biết ai đang chờ điều kiện nào. Nó có thể đánh thức một producer trong khi buffer vẫn đầy, hoặc một consumer trong khi buffer vẫn rỗng.

Sự đánh thức đó không tạo tiến độ; thread thức dậy, lấy lại monitor, thấy predicate sai và chờ lại. Nếu thread có khả năng thực sự tiến không được đánh thức và không có thêm tín hiệu, hệ thống có thể mắc kẹt.

**`notifyAll()` cho mọi nhóm waiter cơ hội kiểm tra predicate của mình**, nhưng chúng vẫn phải dùng `while` và tranh monitor. Đặc biệt với nhiều condition trên cùng monitor, đó thường là lựa chọn dễ chứng minh đúng cho ví dụ học tập.

Nếu cần chọn nhóm chính xác hơn, `ReentrantLock` và hai `Condition` (ví dụ `notFull`/`notEmpty`) hoặc `ArrayBlockingQueue` có cơ chế phù hợp hơn.

## 9. Lỗi lost notification: notification không tích lũy như permit

Một lỗi phổ biến:

```java
// Thread A:
synchronized (lock) {
    lock.notify();
}

// Thread B (bắt đầu chờ muộn):
synchronized (lock) {
    lock.wait();
}
```

Hai đoạn là minh họa độc lập; không phải class runnable. Nếu A notify khi **chưa có waiter nào**, notification không được cất giữ để đánh thức B sau này. B có thể chờ mãi nếu không có notification khác.

Cách sửa **không phải chỉ đảo thứ tự start/sleep** giữa hai thread. Phải có state thể hiện sự kiện, ví dụ:

```java
synchronized (lock) {
    ready = true;
    lock.notifyAll();
}
```

và:

```java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
}
```

Đây là giao thức đúng nếu `ready` được bảo vệ bởi `lock` ở mọi đường liên quan. Khi signal đến sớm, `ready` vẫn true để reader thấy. Khi signal đến muộn, reader đợi và được đánh thức đúng cách.

**State là sự thật, notification chỉ là lời nhắc kiểm tra lại state.** Đây là cách nghĩ đáng tin nhất về monitor condition.

## 10. Spurious wakeup: tại sao phải dùng `while` thay cho `if`?

`Object.wait()` được phép trở về khi không có notification rõ ràng mà ứng dụng kỳ vọng (spurious wakeup). Ngoài ra, một waiter có thể được notify vì **predicate khác** trên cùng monitor, hoặc một thread khác lấy tài nguyên trước waiter khi nó lấy lại monitor.

Sai:

```java
synchronized (lock) {
    if (queue.isEmpty()) {
        lock.wait();
    }
    return queue.removeFirst(); // Có thể không có phần tử.
}
```

Đây là anti-pattern minh họa. Ngay cả nếu `wait()` trở lại, queue **có thể vẫn rỗng**.

Đúng:

```java
synchronized (lock) {
    while (queue.isEmpty()) {
        lock.wait();
    }
    return queue.removeFirst();
}
```

Ở đây giả định `queue` thuộc trạng thái được bảo vệ bằng `lock`. `while` bắt buộc waiter kiểm tra lại predicate sau mọi lần thức. Điều kiện được kiểm tra **trong monitor**, nên một thread khác không thể đồng thời thay đổi queue qua cùng monitor đúng lúc kiểm tra và lấy phần tử.

Với API public, ghi rõ `wait` có thể bị interruption và method có thể ném `InterruptedException`; đừng nuốt exception rồi tiếp tục như thể resource đã sẵn sàng.

## 11. Ví dụ Java 17+: producer–consumer dùng `wait/notifyAll`

Ví dụ dưới tạo một bounded queue capacity 2 với hai operation `put` và `take`. Producer chờ **chưa đầy**, consumer chờ **không rỗng**. Đây là cách viết để học cơ chế monitor; trong ứng dụng thực, `ArrayBlockingQueue` thường phù hợp hơn tự xây queue.

Lưu thành `MonitorQueueDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.Deque;

public class MonitorQueueDemo {
    static final class BoundedQueue {
        private final Deque<Integer> items = new ArrayDeque<>();
        private final int capacity;

        BoundedQueue(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            this.capacity = capacity;
        }

        synchronized void put(int value) throws InterruptedException {
            while (items.size() == capacity) {
                wait();
            }
            items.addLast(value);
            notifyAll();
        }

        synchronized int take() throws InterruptedException {
            while (items.isEmpty()) {
                wait();
            }
            int result = items.removeFirst();
            notifyAll();
            return result;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        BoundedQueue queue = new BoundedQueue(2);
        int[] result = new int[1];

        Thread producer = new Thread(() -> {
            try {
                for (int value = 1; value <= 5; value++) {
                    queue.put(value);
                }
                queue.put(0); // Sentinel: dữ liệu thực chỉ là số 1..5.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                int sum = 0;
                while (true) {
                    int value = queue.take();
                    if (value == 0) {
                        break;
                    }
                    sum += value;
                }
                result[0] = sum;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println("Sum: " + result[0]);
    }
}
```

Chạy:

```bash
javac MonitorQueueDemo.java
java MonitorQueueDemo
```

Stdout chính xác **trên đường chạy bình thường, không có interruption bên ngoài**:

```text
Sum: 15
```

Giải thích:

1. `items` là `ArrayDeque` thường, nhưng mọi lần thêm/lấy và kiểm tra `size/isEmpty` đều được bảo vệ bằng **monitor của `BoundedQueue`**. Không có writer nào được sửa trực tiếp `items` ngoài lớp.
2. Khi queue đầy, producer gọi `wait()` và nhả monitor, nhờ đó consumer vẫn có thể lấy một phần tử.
3. Khi queue rỗng, consumer gọi `wait()` và nhả monitor, nhờ đó producer vẫn có thể thêm phần tử.
4. `notifyAll()` sau mỗi thay đổi cấu trúc cho các waiter cơ hội kiểm tra lại predicate tương ứng.
5. Capacity 2 giới hạn số phần tử đang đợi. Sentinel `0` hợp lệ vì dữ liệu thường chỉ là 1 đến 5 và có đúng một consumer.
6. `result[0]` chỉ được consumer ghi; main đọc sau `consumer.join()`, nên quan hệ happens-before của join công bố kết quả mà không cần biến array thành atomic.
7. Không có cam kết thời điểm mỗi thread chạy; chương trình đúng với mọi lịch chạy bình thường đáp ứng giao thức.

**Giới hạn của demo:** nếu producer bị ngắt trước khi gửi sentinel hoặc consumer lỗi giữa chừng, bên còn lại có thể chờ vô hạn. Ứng dụng thực cần **close/failure protocol**, hủy và đường đánh thức waiter, hoặc dùng cấu trúc concurrent có lifecycle rõ. Đừng biến ví dụ nhỏ thành queue production mà không xử lý những nhánh đó.

## 12. Nhiều waiter và nhiều predicate: lý do dùng `notifyAll`

Một monitor có thể bảo vệ hai điều kiện khác nhau. Khi trạng thái cho phép **cả hai nhóm** tiến, `notifyAll` hợp lý vì nó không phải đoán waiter nào cần thức.

Lưu thành `TwoConditionsDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class TwoConditionsDemo {
    static final class Conditions {
        private boolean firstReady;
        private boolean secondReady;

        synchronized void awaitFirst(CountDownLatch ready)
                throws InterruptedException {
            ready.countDown();
            while (!firstReady) {
                wait();
            }
        }

        synchronized void awaitSecond(CountDownLatch ready)
                throws InterruptedException {
            ready.countDown();
            while (!secondReady) {
                wait();
            }
        }

        synchronized void releaseBoth() {
            firstReady = true;
            secondReady = true;
            notifyAll();
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Conditions conditions = new Conditions();
        CountDownLatch entered = new CountDownLatch(2);
        AtomicInteger completed = new AtomicInteger();

        Thread first = new Thread(() -> {
            try {
                conditions.awaitFirst(entered);
                completed.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread second = new Thread(() -> {
            try {
                conditions.awaitSecond(entered);
                completed.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        first.start();
        second.start();
        entered.await();
        conditions.releaseBoth();
        first.join();
        second.join();

        System.out.println("Completed: " + completed.get());
    }
}
```

```bash
javac TwoConditionsDemo.java
java TwoConditionsDemo
```

Stdout chính xác trên đường chạy bình thường:

```text
Completed: 2
```

`entered` là latch riêng chỉ để main biết cả hai thread đã vào method chờ. Mỗi waiter `countDown()` trong monitor ngay trước vòng `wait`. Do main cũng cần monitor đó để gọi `releaseBoth()`, nó không thể đổi predicate giữa bước waiter đang sở hữu monitor và bước waiter nhả monitor trong `wait()`; đó là giao thức quan trọng của ví dụ.

`notifyAll()` khiến cả hai waiter có cơ hội lấy lại monitor và nhìn thấy predicate tương ứng đã true. Hai lời gọi `incrementAndGet` cập nhật cùng một atomic counter, main đọc sau `join`. Không cam kết waiter nào hoàn tất trước.

Đây là ví dụ **một lần mở điều kiện**. Đối với workload lặp lại, cần thêm quy tắc reset predicate, đóng và hủy; không nên tái sử dụng latch đã về 0 để đánh dấu một vòng mới.

## 13. InterruptedException: thread đang `wait` có thể bị ngắt

`Object.wait()` là một blocking operation **interruptible**. Nếu thread đang chờ bị `Thread.interrupt()`, nó có thể trở về bằng cách ném `InterruptedException` theo hợp đồng của API. Khi `InterruptedException` được ném do interruption, interrupt status bị xóa như một phần của hành vi ném exception; caller quyết định propagate, cleanup, hoặc khôi phục trạng thái khi không thể xử lý ngay.

Quan trọng: thread phải **lấy lại monitor của object** trước khi `wait()` trả về hoặc thoát bằng exception. Vì thế catch/finally bao quanh `wait` có thể thực thi khi thread lại đang sở hữu monitor.

Ví dụ độc lập, lưu thành `WaitInterruptDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class WaitInterruptDemo {
    public static void main(String[] args)
            throws InterruptedException {
        Object monitor = new Object();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicBoolean caught = new AtomicBoolean();

        Thread waiter = new Thread(() -> {
            synchronized (monitor) {
                entered.countDown();
                try {
                    monitor.wait();
                } catch (InterruptedException e) {
                    caught.set(true);
                    Thread.currentThread().interrupt();
                }
            }
        }, "interruptible-waiter");

        waiter.start();
        entered.await();
        waiter.interrupt();
        waiter.join();

        System.out.println("Interrupted wait: " + caught.get());
    }
}
```

```bash
javac WaitInterruptDemo.java
java WaitInterruptDemo
```

Stdout chính xác:

```text
Interrupted wait: true
```

Ví dụ dùng latch để biết thread đã vào khối monitor trước khi main yêu cầu interrupt. Nếu interrupt đến **trước** lời gọi `wait()` thực tế, thread vẫn có interrupt status và `wait` sẽ phản hồi theo hợp đồng khi được gọi. Nếu interrupt đến **trong lúc đang chờ**, `wait` được đánh thức bởi interruption. Cả hai lịch chạy đều cho kết quả như trên.

Ở nhánh catch, `Thread.currentThread().interrupt()` khôi phục interrupt status để các lớp gọi bên ngoài có thể biết yêu cầu ngắt đã xảy ra. Trong API có `throws InterruptedException`, truyền exception lên caller thường rõ hơn việc bắt rồi nuốt lỗi.

**Không dùng `Thread.stop()`** để thay cho interruption hoặc xây logic hủy; cơ chế đó không phù hợp để bảo vệ invariant và đã bị deprecate vì không an toàn.

## 14. `wait(timeout)` và timed waiting

Java cung cấp:

- `wait()` hoặc `wait(0)`: chờ **không giới hạn thời gian** theo hợp đồng đợi của Object.
- `wait(timeoutMillis)` với timeout dương: yêu cầu chờ tối đa khoảng thời gian đó trước khi đủ điều kiện đánh thức do timeout; thời điểm lấy lại monitor có thể trễ hơn vì scheduling và lock contention.
- `wait(timeoutMillis, nanos)`: cho phép chỉ định thêm độ chính xác nanosecond trong phạm vi tham số hợp lệ.

**Timeout không đồng nghĩa predicate đã đúng.** Thread có thể trở về vì timeout, notification không liên quan, interruption hoặc spurious wakeup. Vì thế vẫn cần `while`.

Ví dụ dùng deadline thay vì mỗi lần thức lại chờ toàn bộ timeout ban đầu:

Lưu thành `TimedGateDemo.java`:

```java
import java.util.concurrent.TimeUnit;

public class TimedGateDemo {
    static final class Gate {
        private boolean open;

        synchronized boolean awaitOpen(long timeoutMillis)
                throws InterruptedException {
            if (timeoutMillis < 0) {
                throw new IllegalArgumentException("negative timeout");
            }

            long deadline = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            long remaining;

            while (!open &&
                    (remaining = deadline - System.nanoTime()) > 0) {
                TimeUnit.NANOSECONDS.timedWait(this, remaining);
            }

            return open;
        }

        synchronized void open() {
            open = true;
            notifyAll();
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Gate gate = new Gate();

        System.out.println("Opened: " + gate.awaitOpen(50));
        gate.open();
        System.out.println("Opened after signal: "
                + gate.awaitOpen(50));
    }
}
```

```bash
javac TimedGateDemo.java
java TimedGateDemo
```

Stdout chính xác trong lần chạy bình thường:

```text
Opened: false
Opened after signal: true
```

Giải thích: trong lần gọi đầu, không có thread nào mở `Gate` nên sau khi hết deadline predicate vẫn false. Lần sau `open()` đã được gọi nên `awaitOpen` trả true ngay, không cần `wait`.

`System.nanoTime()` phù hợp đo khoảng trôi qua vì nó là đồng hồ monotonic dành cho elapsed time, không phải giờ hệ thống. Đoạn demo sử dụng khoảng timeout ngắn và đơn giản; API thư viện production cần xem xét overflow của phép cộng deadline nếu cho phép timeout cực lớn, và quy tắc khi truyền 0. `TimeUnit.NANOSECONDS.timedWait` chuyển khoảng nanosecond thành lời gọi chờ có timeout thích hợp.

Nếu muốn trả về một kết quả thành công **chỉ khi predicate đã đạt**, không được dùng việc `wait` trả về như chứng cứ; method phải đọc predicate trong monitor trước khi trả true.

## 15. `wait()` khác `Thread.sleep()` như thế nào?

| Thuộc tính | `Object.wait()` | `Thread.sleep(...)` |
| --- | --- | --- |
| Receiver | Một object monitor | Static API trên `Thread` hiện tại |
| Phải giữ monitor trước khi gọi? | **Có**, monitor của receiver | Không |
| Trong lúc chờ có nhả monitor đang nói tới? | **Có**, nhả monitor của receiver | **Không**, không tự nhả monitor nào |
| Dùng để chờ predicate do thread khác đổi? | Có, qua guarded block | Không phù hợp làm tín hiệu |
| Thức do gì? | Notification, interruption, timeout, spurious wakeup theo hợp đồng | Hết khoảng ngủ hoặc interruption |
| Khi method trả về | Phải lấy lại monitor receiver | Không tự lấy monitor mới |
| Nguy cơ nếu đang giữ monitor khác | Các monitor khác vẫn có thể tiếp tục bị giữ | Vẫn giữ toàn bộ monitor đang sở hữu |

Nếu viết:

```java
synchronized (lock) {
    Thread.sleep(1_000);
}
```

thì thread **vẫn giữ monitor `lock` trong thời gian ngủ**. Thread khác cần `lock` có thể bị `BLOCKED` dù CPU không làm việc hữu ích. Đây không phải cách cho thread khác vào critical section để cập nhật điều kiện.

Ngược lại:

```java
synchronized (lock) {
    lock.wait();
}
```

thì thread sẽ nhả monitor `lock` trong lúc chờ. Nhưng nếu không có predicate và protocol signal, đoạn thứ hai có nguy cơ lost notification hoặc chờ vô hạn. Hai đoạn trên chỉ minh họa hành vi monitor, không phải chương trình chạy đầy đủ.

## 16. `wait()` chỉ nhả monitor của **receiver**, không nhả mọi khóa

Một thread có thể sở hữu nhiều monitor lồng nhau:

```java
synchronized (outer) {
    synchronized (inner) {
        inner.wait();
    }
}
```

Đây là đoạn minh họa với hai object `outer` và `inner` giả định.

`inner.wait()` tạm nhả **monitor `inner`**, không tự nhả monitor `outer`. Trong lúc thread đang chờ trên inner, nó vẫn có thể đang giữ outer. Nếu một thread khác cần outer để chuẩn bị signal phù hợp, chương trình có thể deadlock.

Đây là lý do cần kiểm tra **những khóa còn giữ trên stack** trước khi gọi `wait` hoặc một operation blocking. `Object.wait` không phải primitive "unlock toàn bộ các resource".

Tương tự, `Condition.await()` của `java.util.concurrent.locks` nhả và lấy lại **lock gắn với Condition** theo hợp đồng, không tự giải phóng mọi monitor/lock khác mà caller đang giữ.

Với intrinsic monitor reentrant, `wait` nhả các mức sở hữu của **chính monitor receiver** trong khi chờ và khôi phục quyền sở hữu phù hợp trước khi trở về; nhưng không nhả các monitor độc lập.

## 17. `IllegalMonitorStateException` và các lỗi API dễ gặp

Sai:

```java
Object lock = new Object();
lock.wait();        // Caller không giữ monitor lock.
```

Sai tương tự:

```java
Object lock = new Object();
lock.notifyAll();   // Caller không giữ monitor lock.
```

Hai đoạn là ví dụ lỗi riêng lẻ; để chạy phải xử lý checked `InterruptedException` ở ví dụ `wait`. Trong cả hai trường hợp, việc không sở hữu monitor của receiver gây `IllegalMonitorStateException`.

Một lỗi tinh vi hơn:

```java
synchronized (firstLock) {
    secondLock.wait();
}
```

Nếu `firstLock` và `secondLock` là hai object khác nhau và caller không sở hữu monitor `secondLock`, vẫn lỗi. Không đủ chỉ "đang ở bên trong một block `synchronized` bất kỳ".

### `wait()` khác `join()`

`Thread.join()` là API cấp cao hơn để **đợi thread kết thúc**. Nó có giao thức completion và memory consistency phù hợp. Không cần người dùng tự gọi `wait()`, `notify()` trên object `Thread` để điều phối completion; làm vậy dễ va chạm với cách triển khai và giao thức của thư viện.

Nếu cần kết quả của task, dùng `Future.get()`; nếu cần tín hiệu one-shot, dùng `CountDownLatch`. Chúng trình bày mục tiêu rõ hơn việc tự thao tác monitor.

## 18. Happens-before: notification có vai trò gì với visibility?

Khi một thread đổi predicate và gọi `notifyAll()` trong monitor, sau đó rời monitor, thread được đánh thức chỉ đi tiếp khi đã lấy lại **cùng monitor**.

Ví dụ:

```java
synchronized (lock) {
    data = 42;
    ready = true;
    lock.notifyAll();
} // unlock lock
```

và:

```java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
    System.out.println(data);
}
```

Hai đoạn là minh họa của một giao thức chung với `data`, `ready` bảo vệ bằng `lock`. Nếu không có writer xung đột khác, reader sau khi vượt predicate sẽ thấy `42`.

Giải thích JMM:

1. Trong writer, cập nhật `data` và `ready` được thực hiện trước lần unlock monitor `lock` theo program order.
2. Unlock monitor `lock` **happens-before** lần lock tiếp theo trên cùng monitor mà waiter phải thực hiện trước khi rời `wait`.
3. Reader kiểm tra `ready` và đọc `data` khi đang giữ monitor, nên có quan hệ visibility/order phù hợp.
4. `notifyAll` là phần của **giao thức điều phối**; **không nên tách notification ra khỏi predicate và monitor** để suy luận rằng chỉ một lời gọi notify đã công bố mọi dữ liệu bất kỳ.

Nếu một writer thứ ba sửa `data` không qua monitor, vẫn có data race tiềm tàng. Monitor protocol chỉ có hiệu lực khi **mọi đường truy cập xung đột cần bảo vệ cùng tuân thủ giao thức**.

## 19. Multiple consumers: vì sao điều kiện có thể sai trở lại sau khi notify?

Giả sử queue vừa có một item. Một lần `notifyAll()` đánh thức ba consumer đang chờ trên cùng monitor.

```text
Producer:
  put 1 item
  notifyAll()
  unlock

Consumer A lấy monitor trước:
  queue không rỗng -> lấy item -> unlock

Consumer B lấy monitor sau:
  queue đã rỗng -> while kiểm tra lại -> wait

Consumer C lấy monitor sau:
  queue vẫn rỗng -> while kiểm tra lại -> wait
```

Không có gì sai ở đây: **notification chỉ trao cơ hội**, không dành riêng item cho từng waiter. Một waiter đã thức phải cạnh tranh monitor và kiểm tra lại trạng thái, vì thread khác có thể đã tiêu thụ điều kiện.

Đó là nguyên nhân thứ hai (ngoài spurious wakeup) khiến `if` là sai khi chờ resource có thể bị tiêu thụ bởi waiter khác.

Cần phân biệt:

- `notifyAll` đánh thức mọi waiter **đang trong wait set** của monitor, không tự thức mọi thread `BLOCKED` đang đợi vào `synchronized`.
- Thread vừa được đánh thức chưa được bảo đảm chạy trước thread mới đến lấy monitor.
- Không có FIFO hoặc fairness guarantee mặc định cho intrinsic monitor.
- Một waiter chỉ tiến qua vòng `while` khi **predicate đúng tại lúc nó giữ monitor**.

## 20. Đóng, hủy và lỗi trong giao thức monitor

Một bounded queue tự viết cần trả lời nhiều câu hỏi ngoài trường hợp bình thường:

- Nếu producer chết trước khi gửi sentinel, consumer thoát bằng cách nào?
- Nếu consumer bị hủy khi queue đang đầy, producer có thể kẹt trong `put` không?
- Nếu `close()` được gọi khi nhiều waiter đang `wait`, ai đánh thức họ?
- Sau khi close, `put` trả exception, false hay vẫn nhận item?
- `take` sau close có tiêu thụ hết item tồn trước đó hay dừng ngay?
- Nếu thread bị interrupt giữa operation, state có bị cập nhật nửa chừng không?

Một hướng thiết kế là bảo vệ thêm **`closed` predicate** dưới cùng monitor và `notifyAll` khi chuyển trạng thái đóng:

```java
synchronized void close() {
    closed = true;
    notifyAll();
}

synchronized Integer take() throws InterruptedException {
    while (items.isEmpty() && !closed) {
        wait();
    }
    if (items.isEmpty()) {
        return null; // Quy ước null nghĩa là closed và đã rỗng.
    }
    Integer value = items.removeFirst();
    notifyAll();
    return value;
}
```

Đây là minh họa phương thức trong một class giả định có `closed` và `items`; không phải chương trình độc lập. Trong contract cần cấm null là dữ liệu hợp lệ nếu `null` được dùng làm sentinel kết thúc. Đường `put` cũng phải kiểm tra `closed` trước/sau vòng chờ theo quy tắc thích hợp, không chỉ sửa mỗi `take`.

**Cần chuyển trạng thái trước khi notify** để waiter thức dậy không cần đoán nguyên nhân. Nếu `close` không đánh thức toàn bộ waiters, một số thread có thể vẫn chờ mãi dù hệ thống không nhận thêm dữ liệu.

Ứng dụng thực nên ưu tiên `BlockingQueue`, future, latch hoặc thành phần có lifecycle rõ nếu không có lý do cần hiện thực monitor condition từ đầu.

## 21. `wait/notify` so với các tiện ích Java cấp cao

| Bài toán | Primitive thường phù hợp | Vì sao |
| --- | --- | --- |
| Chờ một predicate của state bảo vệ bởi monitor | `synchronized` + `while` + `wait/notifyAll` | Kiểm soát trực tiếp state và monitor |
| Producer–consumer, queue có capacity | `ArrayBlockingQueue` hoặc `LinkedBlockingQueue` | Đã đóng gói protocol not-empty/not-full |
| Đợi một nhóm task hoàn tất | `CountDownLatch` hoặc `Future` | Biểu diễn completion; không cần tự quản wait set |
| Đồng bộ nhiều vòng theo phase | `CyclicBarrier` hoặc `Phaser` | Có semantics cho nhiều lượt phối hợp |
| Giới hạn số tác vụ giữ resource | `Semaphore` | Permit, acquisition/release, timeout và interruption |
| Cần nhiều nhóm condition cùng một lock | `ReentrantLock` + `Condition` | Có thể tạo nhiều condition queues, giảm đánh thức không cần thiết |
| Chờ một thread kết thúc | `Thread.join()` | Hợp đồng lifecycle và HB trực tiếp |
| Chờ kết quả task async | `Future.get()` / `CompletableFuture` | Gắn việc chờ với kết quả và lỗi của task |

`Condition.await()` cũng phải được dùng với predicate trong vòng `while`; nó có thể có spurious wakeup. `Condition.signal` gần về vai trò với `notify`, nhưng thuộc condition queue gắn với một `Lock`, không phải monitor wait set của `Object`.

Không dùng `wait/notify` chỉ vì nó "thấp cấp nên nhanh hơn"; các tiện ích concurrent thường rõ semantics và dễ kiểm chứng hơn, có thể cho phép timeout/cancellation/lifecycle tốt hơn.

## 22. Callback hoặc I/O đang giữ monitor: rủi ro deadlock

Một thread có thể rời wait set nhưng **vẫn không tiến được** nếu thread khác giữ monitor quá lâu:

```java
synchronized (lock) {
    condition = true;
    lock.notifyAll();
    slowNetworkCall(); // Vẫn giữ lock!
}
```

Đây là đoạn anti-pattern minh họa. Việc gọi `notifyAll` **không thả khóa**. Nếu `slowNetworkCall` block vô hạn, waiter dù đã được đánh thức vẫn không lấy lại monitor để đi tiếp.

Mẫu tốt hơn:

```java
synchronized (lock) {
    condition = true;
    lock.notifyAll();
}
// Đã nhả monitor.
slowNetworkCall();
```

Cũng cần tránh callback do caller cung cấp, `Thread.join()`, `Future.get()`, `BlockingQueue.take()` hoặc I/O có thể block trong một critical section, nhất là nếu tác vụ được đợi cần chính monitor đó để hoàn tất.

Nếu I/O thay đổi state sau khi hoàn thành, cần **chụp snapshot dưới lock → làm I/O ngoài lock → lấy lock kiểm tra phiên bản/invariant và commit**. Việc nhả lock trước I/O không tự tạo transaction xuyên cả JVM và hệ thống bên ngoài.

## 23. Kiểm thử monitor protocol sao cho đáng tin?

Không nên thử bằng cách đoán thời gian:

```java
worker.start();
Thread.sleep(100);
synchronized (lock) {
    lock.notifyAll();
}
```

Đây là anti-pattern minh họa. `sleep` chỉ tạo độ trễ, không chứng minh worker đã vào wait set. Nếu không có predicate, signal đến sớm vẫn bị mất. Nếu có predicate đúng, không cần phụ thuộc sleep.

Với test, nên dùng:

1. **CountDownLatch/Phaser** để chủ động phối hợp mốc bắt đầu và kết thúc.
2. **Predicate dưới monitor** để bảo đảm notification sớm hay muộn đều đúng.
3. **Timeout giới hạn** trong test có khả năng treo khi lỗi, không dùng `join()` vô hạn cho một test sai thiết kế.
4. **Thread dump / ThreadMXBean** khi cần chẩn đoán waiter, owner và monitor.
5. **Kiểm tra invariant** sau khi các worker hoàn thành, không chỉ tìm exception.
6. **Stress test** qua nhiều lịch chạy, nhưng không coi việc chưa thấy lỗi là chứng minh an toàn.
7. **Test shutdown/interruption** để không để waiter ở lại vô thời hạn khi pipeline đóng hoặc một producer lỗi.

Một lưu ý kiểm thử: latch báo "worker đã tới trước `wait`" không tự đồng nghĩa worker **đã rơi vào `WAITING`**, trừ khi protocol còn bảo đảm monitor ownership/ordering phù hợp. Trong `TwoConditionsDemo`, cùng monitor với `releaseBoth` giúp kiểm tra điều này mà không dựa vào sleep.

## 24. Những nhầm lẫn thường gặp

**1. "`notify()` cho thread chờ quyền chạy ngay."** Không. Waiter còn phải lấy lại monitor; notifier vẫn giữ monitor cho đến khi nhả.

**2. "`notifyAll()` khiến mọi thread chạy trong monitor cùng lúc."** Không. Monitor vẫn mutual exclusive; các waiter tranh lock lần lượt.

**3. "`wait()` dùng ngoài `synchronized` cho nhanh."** Sai. Caller phải đang sở hữu monitor receiver, nếu không gặp `IllegalMonitorStateException`.

**4. "Chỉ cần đang ở một block synchronized bất kỳ."** Không. Phải sở hữu **đúng monitor của object gọi `wait/notify`**.

**5. "`wait` tự động nhả mọi lock."** Sai. Chỉ nhả monitor receiver, không nhả các monitor hoặc lock khác trên stack.

**6. "`sleep` cũng nhả lock như `wait`."** Sai. `Thread.sleep` không nhả monitor đang giữ.

**7. "`notify` được ghi nhớ nếu chưa ai đợi."** Sai. Không có permit tích lũy; dùng predicate bảo vệ trong monitor.

**8. "Sau khi `wait` trả về, condition chắc chắn đúng."** Sai. Có spurious wakeup, nhiều waiter tranh resource và các predicate khác; dùng `while`.

**9. "`if` đủ nếu chỉ có một producer."** Vẫn dễ sai khi cancellation, spurious wakeup hoặc nhiều consumer; nguyên tắc guarded block là `while`.

**10. "Dùng `notify` luôn tiết kiệm hơn mà vẫn đúng."** Không khi có nhiều loại predicate trên cùng wait set; có thể đánh thức sai waiter và mất tiến độ.

**11. "Gọi `notifyAll` sau khi nhả monitor là đúng."** Caller của notify phải đang **sở hữu monitor**; không được nhả trước rồi gọi ngoài `synchronized`.

**12. "Có `volatile` thì có thể tách check condition ra ngoài lock rồi `wait`."** Visibility đơn thuần không thay thế atomicity giữa kiểm tra predicate và nhả monitor/chờ.

**13. "`InterruptedException` nghĩa thread đã chết."** Không. Đó là tín hiệu interruption; code catch có thể tiếp tục chạy.

**14. "Timeout return nghĩa là điều kiện đúng."** Không. Hết thời gian chỉ nói thread đã thôi chờ; predicate phải được kiểm tra riêng.

**15. "`notifyAll` giải quyết mọi vấn đề deadlock."** Không. Nó không nhả monitor ngay, không giải phóng các khóa khác và không sửa dependency cycle hoặc resource leak.

**16. "Gọi `notify()` trên object `Thread` là cách chờ thread hoàn thành."** Không nên. Dùng `Thread.join()` để dựa vào contract lifecycle đúng.

## 25. Checklist khi viết `wait/notifyAll`

Trước khi gửi code vào production, kiểm tra:

1. **Monitor có cố định không?** Mọi waiter/notifier dùng đúng cùng một object, không đổi receiver giữa chừng.
2. **Predicate là gì?** Có field trạng thái rõ ràng thể hiện điều kiện cần, thay vì chỉ dựa trên một "tín hiệu"?
3. **Mọi đường truy cập state có cùng khóa không?** Nếu có đường bỏ qua monitor thì memory/invariant reasoning có thể sai.
4. **`wait` có ở trong `while (!predicate)` không?** Không dùng `if` làm guarded wait.
5. **State có được cập nhật trước notification không?** Người thức dậy phải có điều kiện để kiểm tra.
6. **`notify` có thực sự đủ không?** Khi nhiều predicate cùng wait set hoặc close phải đánh thức nhiều waiter, dùng `notifyAll` nếu không chứng minh được tính đúng của `notify`.
7. **Có giữ monitor quá lâu sau notify không?** Tránh callback, I/O, future wait và các thao tác block không cần thiết.
8. **Interruption xử lý ra sao?** Propagate hoặc khôi phục flag nếu không xử lý; cleanup không làm mất invariant.
9. **Có timeout/close/failure protocol không?** Waiter có đường thoát nếu producer chết hoặc hệ thống shutdown.
10. **Wait có giữ thêm lock khác không?** `Object.wait` chỉ nhả monitor receiver.
11. **Có nhiều consumers/producers không?** Một notification có thể đánh thức sai waiter; nhiều waiter thức lại có thể thấy predicate sai.
12. **Nếu state có thể reset, có vòng đời generation không?** Tránh dùng một flag một lần cho giao thức nhiều chu kỳ mà không quy định reset.
13. **Có primitive cấp cao thay thế không?** `BlockingQueue`, `CountDownLatch`, `Condition` hoặc `Future` thường giảm số lỗi phải tự xử lý.

## 26. Câu hỏi tự kiểm tra

**1. Vì sao phải sở hữu monitor để gọi `wait()`?**

Vì waiter cần kiểm tra predicate và đi vào trạng thái chờ theo giao thức monitor. Không giữ đúng monitor sẽ ném `IllegalMonitorStateException` và không có atomicity giữa check và wait.

**2. Một thread vừa được `notifyAll()` có thể ở `BLOCKED` không?**

Có. Thread đó có thể đã được đánh thức khỏi wait set nhưng còn phải chờ lấy lại monitor trước khi `wait` trả về.

**3. Tại sao `while` đúng hơn `if` khi chờ queue không rỗng?**

Vì thread có thể thức do spurious wakeup, notification cho condition khác hoặc vì một consumer khác đã lấy item trước. Predicate phải kiểm tra lại khi giữ monitor.

**4. Nếu producer gọi `notify()` trước khi consumer gọi `wait()`, notification có được giữ lại không?**

Không. Cần predicate như `queue.isEmpty()` hoặc `ready`; consumer chỉ chờ nếu điều kiện thật sự chưa đạt.

**5. `wait` và `sleep` khác nhau về monitor thế nào?**

`wait` nhả monitor receiver trong lúc chờ và lấy lại trước khi trả về; `sleep` không nhả monitor mà thread đang giữ.

**6. Khi có cả producer lẫn consumer cùng chờ trên một monitor, vì sao `notifyAll` có thể an toàn hơn `notify`?**

Vì `notify` chọn một waiter không biết predicate nào hợp lệ; đánh thức sai nhóm có thể khiến hệ thống không tiến. `notifyAll` cho mọi nhóm cơ hội kiểm tra lại điều kiện của mình.

**7. Nếu `wait` ném `InterruptedException`, thread có còn giữ monitor không?**

Có. Thread phải lấy lại monitor receiver trước khi method thoát bằng exception; ứng dụng vẫn cần cleanup/propagate interrupt đúng cách.

**8. Có nên gọi `notifyAll` rồi làm HTTP request trước khi rời `synchronized` không?**

Thường không. Notified waiters vẫn phải đợi monitor, nên HTTP request chậm kéo dài lock hold time. Hãy tách I/O khỏi critical section và thiết kế commit/state-check phù hợp.

**9. Trong bounded queue, tại sao `put` và `take` đều nên notify khi state thay đổi?**

Vì `put` có thể làm buffer từ rỗng sang không rỗng (đánh thức consumer), còn `take` có thể làm buffer từ đầy sang còn chỗ (đánh thức producer). Với nhiều predicate chung monitor, `notifyAll` giúp hai nhóm kiểm tra lại đúng lúc.

**10. Một waiter thức dậy nhưng predicate vẫn false nên làm gì?**

Giữ monitor, quay lại `wait` trong vòng `while`, trừ khi có interruption, timeout, close hoặc chính sách hủy cần xử lý.

## 27. Tóm tắt bản chất

- **Monitor là cơ chế sở hữu khóa; predicate là sự thật nghiệp vụ; notification là tín hiệu kiểm tra lại.** Không đánh đồng ba khái niệm này.
- **`wait()`** chỉ hợp lệ khi caller sở hữu monitor receiver; nó nhả monitor đó trong lúc chờ và lấy lại monitor trước khi trả về hoặc ném `InterruptedException`.
- **`notify()`** đánh thức tối đa một waiter tùy ý, **`notifyAll()`** đánh thức mọi waiter trên cùng monitor. Không method nào nhả monitor ngay khi được gọi.
- **`while (!predicate) wait()`** là mẫu cốt lõi để xử lý spurious wakeup, tranh resource, các condition khác và notification tới sớm thông qua predicate.
- **Không dựa vào `Thread.sleep` hay lịch chạy giả định** để chứng minh tín hiệu sẽ đến đúng thời điểm; giữ state và signal trong cùng monitor.
- **Interruption, timeout, shutdown và lỗi producer/consumer** là một phần của giao thức phối hợp cần thiết kế, không phải phần tùy chọn trong hệ thống thực.
- **Hạn chế tự triển khai monitor condition khi có công cụ Java cao hơn phù hợp** như `BlockingQueue`, `CountDownLatch`, `Condition`, `Future` hoặc `Thread.join()`.

Khi nhìn thấy `wait/notify` trong Java, câu hỏi đầu tiên nên là: **"Predicate nào đang được bảo vệ bởi monitor nào, và mọi thread có kiểm tra lại predicate sau khi thức không?"** Nếu trả lời rõ được câu hỏi đó, phần lớn các lỗi lost notification và chờ sai điều kiện sẽ dễ nhận diện và sửa đúng bản chất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-coordination-002">Điều kiện chờ: while thay vì if</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện chờ trong Java: Vì sao dùng while thay vì if?

## 1. Mục tiêu và bối cảnh

Trong **phối hợp luồng (thread coordination)**, một thread thường cần chờ cho đến khi trạng thái chia sẻ cho phép nó làm tiếp: consumer chờ queue có phần tử, producer chờ buffer còn chỗ, worker chờ tài nguyên được cấp, hoặc nhiều thread chờ cờ `ready` được bật. Java cung cấp `Object.wait()` và `Condition.await()` để chờ mà không phải quay vòng tiêu CPU liên tục. Tuy nhiên, việc gọi đúng API thôi chưa đủ: **thread được đánh thức không có nghĩa điều kiện mà nó chờ đã trở thành đúng hoặc còn đúng**.

Quy tắc cốt lõi của bài:

~~~java
// Mẫu đúng về cấu trúc:
synchronized (lock) {
    while (!conditionIsTrue()) {
        lock.wait();
    }
    // Thực hiện thao tác phụ thuộc điều kiện khi còn giữ lock.
}
~~~

Trong đoạn minh họa này, `lock` và `conditionIsTrue()` được khai báo ở phạm vi phù hợp; caller phải xử lý/khai báo `InterruptedException`. **Không dùng `if` thay cho `while`** khi điều kiện phải được bảo đảm sau khi thức dậy.

Sau bài, bạn sẽ:
- Phân biệt **điều kiện nghiệp vụ (predicate)** với **tín hiệu đánh thức (notification)**.
- Giải thích chính xác các nguyên nhân khiến `wait()/await()` kết thúc dù predicate vẫn sai: **spurious wakeup**, cạnh tranh giữa các waiter, và điều kiện thay đổi trước khi thread lấy lại khóa.
- Hiểu việc **kiểm tra predicate và gọi wait phải nằm trong cùng một giao thức đồng bộ**, nếu không có thể mất thông báo.
- Nhận biết `wait()` nhả monitor, `notify()/notifyAll()` không lập tức chuyển lock, và waiter phải lấy lại monitor trước khi kiểm tra điều kiện.
- Sử dụng `while` với `Object.wait()`, `Condition.await()`, timeout, interruption và trạng thái đóng (shutdown).
- Chạy các chương trình Java 17+ chứng minh vì sao `if` sai ngay cả khi code có `synchronized`.

**Phạm vi:** cơ chế concurrency của **Java nói chung**, không sử dụng các annotation/API hoặc cấu hình bất đồng bộ riêng của Spring, vì Spring đã có chương riêng.

## 2. Hai sự thật khác nhau: “được báo” và “đủ điều kiện”

Giả sử queue ban đầu trống. Consumer chỉ được lấy một phần tử khi:

~~~text
predicate notEmpty := queue.size() > 0
~~~

Producer có thể thêm một phần tử rồi gọi `notifyAll()`. Nhưng đây là hai hành động khác nhau:
1. Producer **thay đổi trạng thái**: queue có dữ liệu.
2. Producer **phát tín hiệu**: thread đang chờ được quyền cố gắng thức dậy và tranh lock.

Đừng đánh đồng chúng. Notification không mang sẵn phần tử, không cung cấp “giấy phép lấy một item” và **không giữ chỗ** cho một consumer cụ thể. Trước khi consumer thức dậy và lấy được khóa, một consumer khác có thể đã lấy phần tử. Vì vậy câu “tôi vừa được notify” không chứng minh `!queue.isEmpty()`.

Một tình huống đơn giản:

~~~text
Queue có 0 phần tử.
C1 đang wait vì queue trống.
C2 cũng đang wait vì queue trống.

Producer thêm đúng 1 phần tử X và gọi notifyAll().
  |
  +-- C1 lấy lock trước, lấy X, queue trở lại rỗng.
  |
  +-- C2 lấy lock sau.
        Nếu dùng if: nhảy qua kiểm tra cũ rồi remove -> lỗi.
        Nếu dùng while: kiểm tra lại, queue vẫn rỗng -> chờ tiếp.
~~~

**Điều kiện (predicate)** trả lời “tôi có được làm tiếp không?”, còn **notification** trả lời “đã có một sự kiện có thể khiến điều kiện thay đổi, hãy kiểm tra lại”. Code đúng luôn dựa vào **predicate được đọc dưới đúng khóa**, không dựa vào một lời thông báo đã đến.

## 3. Cơ chế Object.wait() và monitor

### 3.1. Cùng một monitor phải bảo vệ cả predicate và state

Mẫu consumer:

~~~java
synchronized (monitor) {
    while (queue.isEmpty()) {
        monitor.wait();
    }
    return queue.removeFirst();
}
~~~

Mẫu producer cùng giao thức:

~~~java
synchronized (monitor) {
    queue.addLast(item);
    monitor.notifyAll();
}
~~~

Đây là các đoạn minh họa nằm trong method hợp lệ, giả định `monitor`, `queue`, `item` đã có; phía consumer khai báo hoặc xử lý `InterruptedException`. Tất cả đường sửa/đọc `queue` mà liên quan predicate phải dùng **cùng monitor**.

Lý do:
- Consumer lấy monitor để kiểm tra queue.
- Nếu trống, `monitor.wait()` đặt thread vào wait set và **nhả monitor đó** theo giao thức của `wait()`. Producer không thể chen vào giữa lần kiểm tra và bước wait theo cách làm mất tín hiệu khi cả hai cùng tuân thủ monitor.
- Producer lấy monitor, thêm phần tử, rồi gọi `notifyAll()` khi đang giữ monitor; sau đó nhả monitor.
- Consumer được đánh thức **phải lấy lại monitor** trước khi `wait()` trở về; chỉ lúc ấy nó mới kiểm tra lại `queue.isEmpty()`.
- Nếu điều kiện vẫn sai, vòng `while` gọi `wait()` lần nữa. Nếu đúng, hành động `removeFirst()` xảy ra **dưới cùng khóa** với lần kiểm tra cuối.

Một thread có thể giữ nhiều lock, nhưng `monitor.wait()` chỉ tự nhả **monitor của object `monitor`**. Nó không nhả các `synchronized` monitor khác hay `ReentrantLock` khác mà thread đang giữ. Giữ lock khác trong lúc chờ có thể gây deadlock hoặc ngăn thread phát tín hiệu tiến triển.

### 3.2. wait() khác sleep()

| API | Đặc tính chờ | Khả năng giải phóng khóa |
| --- | --- | --- |
| `monitor.wait()` | Chờ notification, interruption hoặc spurious wakeup | **Nhả đúng monitor được gọi wait**, lấy lại trước khi trả về |
| `monitor.wait(millis)` | Thêm khả năng hết timeout | Nhả và lấy lại đúng monitor |
| `Thread.sleep(millis)` | Ngủ theo thời gian | **Không nhả bất kỳ monitor nào** chỉ vì sleep |
| `condition.await()` | Chờ signal/interruption/spurious wakeup của `Condition` | Nhả rồi lấy lại **lock liên kết với Condition** |
| `BlockingQueue.take()` | Chờ queue có phần tử, trừ khi bị interrupt | Primitive tự phối hợp nội bộ; không phải gọi `wait()` thủ công |

`Thread.sleep(100)` bên trong `synchronized` không phải cách chờ producer đổi state: thread ngủ vẫn giữ monitor, ngăn producer cần monitor đó vào cập nhật. Thêm `sleep` cũng không tạo quan hệ happens-before cần thiết để sửa race.

### 3.3. Phải giữ monitor khi gọi wait/notify

Các lời gọi `wait()`, `notify()` và `notifyAll()` trên một object yêu cầu thread hiện tại đang giữ **monitor của chính object ấy**. Nếu không, Java ném `IllegalMonitorStateException`.

~~~java
Object lock = new Object();

// Sai về giao thức: lock.wait(); // Nếu không ở synchronized(lock).

synchronized (lock) {
    // lock.wait(); // Hợp lệ về ownership, nhưng sẽ đợi.
    lock.notifyAll(); // Hợp lệ về ownership.
}
~~~

Trên các ví dụ ở bài, phần thread gọi wait phải có đường thoát, không gọi `wait()` vô hạn trong main mà không có thread khác phát signal.

## 4. Ba lý do bắt buộc kiểm tra lại điều kiện bằng while

### 4.1. Spurious wakeup (thức dậy không do tín hiệu dự kiến)

Javadoc của `Object.wait()` và `Condition.await()` cho phép một lần chờ trở về dù không có notification hoặc sự kiện hoàn thành tương ứng. Đây gọi là **spurious wakeup**.

Ví dụ sai:

~~~java
synchronized (monitor) {
    if (!ready) {
        monitor.wait();
    }
    useResource(); // Không có bảo đảm ready == true.
}
~~~

Với một spurious wakeup, `wait()` trả về nhưng `ready` vẫn false, và code dùng `if` không quay lại kiểm tra. Với `while`:

~~~java
synchronized (monitor) {
    while (!ready) {
        monitor.wait();
    }
    useResource(); // Predicate đã được kiểm tra đúng khi giữ monitor.
}
~~~

Java không hứa spurious wakeup xuất hiện trong mọi lần chạy; không dùng benchmark may rủi hoặc `Thread.sleep` để “chứng minh” nó. Nhưng code **phải đúng kể cả khi hiện tượng xảy ra**.

### 4.2. Nhiều waiter có thể cùng thức dậy nhưng chỉ có một tài nguyên

`notifyAll()` làm nhiều waiter cố thức dậy; mỗi waiter phải tranh để lấy lại monitor. Nếu producer thêm một item, **chỉ một consumer có thể lấy item ấy**. Consumer khác tỉnh sau đó có thể thấy queue rỗng.

Vấn đề này xảy ra **ngay cả không có spurious wakeup**. Vì thế lời giải thích “while chỉ để phòng JVM tự dưng đánh thức” là **chưa đầy đủ**.

### 4.3. Điều kiện đổi sau signal nhưng trước lúc waiter lấy lại lock

Ngay cả khi producer vừa gọi `notify()` vì điều kiện đã đúng:
- Producer vẫn giữ monitor cho đến khi nó rời vùng `synchronized`.
- Waiter vừa được notify chưa thể chạy phần sau `wait()` khi chưa lấy lại monitor.
- Một thread thứ ba có thể được scheduler cho lấy monitor sớm hơn waiter và thay đổi state.
- Tại lần waiter giữ monitor trở lại, predicate có thể đã sai.

Đây là cách nhìn tương đồng với **Mesa-style condition variable**: signal không “trao tay” điều kiện; waiter **tự kiểm tra** sau khi được quyền chạy. Không suy luận rằng `notifyAll()` chuyển trạng thái Thread ngay lập tức sang thực thi nghiệp vụ.

## 5. Ví dụ chạy được 1 — if tiếp tục sai sau một notification hợp lệ

Bài toán: worker chỉ được tiếp tục nếu `ready=true`. Main cố tình phát `notifyAll()` **mà không đổi `ready`**. Một chương trình dùng `if` sẽ tiếp tục khi điều kiện vẫn false. Chúng ta dùng latch để kiểm soát điểm đợi, không dựa vào sleep.

Lưu file **`IfWaitBugDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;

public class IfWaitBugDemo {
    private static final Object MONITOR = new Object();
    private static boolean ready;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch enteringWait = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            synchronized (MONITOR) {
                if (!ready) {
                    enteringWait.countDown();
                    try {
                        MONITOR.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                System.out.println("proceeded-when-ready=" + ready);
            }
        }, "if-waiter");

        worker.start();
        enteringWait.await();

        synchronized (MONITOR) {
            // Một tín hiệu không gắn với việc predicate trở thành true.
            MONITOR.notifyAll();
        }

        worker.join();
    }
}
~~~

Chạy Java 17+:

~~~bash
javac IfWaitBugDemo.java
java IfWaitBugDemo
~~~

Stdout trên đường thực thi không có interruption:

~~~text
proceeded-when-ready=false
~~~

**Vì sao kết quả xác định trong thí nghiệm này?**
- Worker gọi `enteringWait.countDown()` **trong khi đang giữ MONITOR**, ngay trước `wait()`.
- Main đợi latch, sau đó cố vào `synchronized(MONITOR)`. Main **không thể vào** cho tới khi worker thật sự gọi `wait()` và nhả MONITOR (nếu worker vẫn đang giữ nó).
- Main phát `notifyAll()` khi `ready` vẫn false rồi nhả monitor.
- Worker lấy lại monitor, trả về khỏi `wait()` và **không kiểm tra lại** vì đã rời nhánh `if`. Nó in false.

Ngay cả một notification có thật vẫn có thể không đồng nghĩa condition mong muốn đã đúng. Ví dụ này làm rõ lỗi của `if` mà không phải chờ spurious wakeup ngẫu nhiên.

**Không dùng code `if` này trong production.** Nếu worker phụ thuộc vào một tài nguyên thực, hành động sau khi thức dậy có thể lấy dữ liệu không tồn tại hoặc vi phạm invariant nghiệp vụ.

## 6. Ví dụ chạy được 2 — while bỏ qua wakeup chưa đủ điều kiện

Sửa cùng tình huống bằng `while`. Lần notify đầu tiên không thay `ready`; worker kiểm tra và tiếp tục chờ. Main đợi xác nhận worker đã **kiểm tra lại false** trước khi đặt `ready=true` và notify lần thứ hai.

Tạo file **`WhileWaitDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;

public class WhileWaitDemo {
    private static final Object MONITOR = new Object();
    private static boolean ready;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch firstWait = new CountDownLatch(1);
        CountDownLatch checkedFalseAgain = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            int falseChecks = 0;

            synchronized (MONITOR) {
                while (!ready) {
                    falseChecks++;

                    if (falseChecks == 1) {
                        firstWait.countDown();
                    }
                    if (falseChecks == 2) {
                        checkedFalseAgain.countDown();
                    }

                    try {
                        MONITOR.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                System.out.println(
                        "rechecked-false=" + (falseChecks >= 2));
                System.out.println("proceeded-when-ready=" + ready);
            }
        }, "while-waiter");

        worker.start();
        firstWait.await();

        synchronized (MONITOR) {
            MONITOR.notifyAll(); // Wakeup khi ready vẫn false.
        }

        checkedFalseAgain.await();

        synchronized (MONITOR) {
            ready = true;
            MONITOR.notifyAll();
        }

        worker.join();
    }
}
~~~

Chạy:

~~~bash
javac WhileWaitDemo.java
java WhileWaitDemo
~~~

Stdout khi hoàn thành bình thường:

~~~text
rechecked-false=true
proceeded-when-ready=true
~~~

**Lập luận:**
1. Worker lần đầu thấy `ready=false` và báo `firstWait`.
2. Main chỉ lấy được `MONITOR` sau khi worker đã vào wait và nhả monitor, nên notify đầu tiên đánh thức worker đang chờ; `ready` vẫn false.
3. Worker lấy lại monitor, vào đầu `while` và tăng `falseChecks`; latch `checkedFalseAgain` xác nhận nó đã **thấy false lần nữa**.
4. Main đặt `ready=true` dưới monitor và notify. Nếu worker chưa đi vào wait lần nữa, main phải chờ lock cho tới khi worker wait; nếu worker đã wait thì main có thể phát tín hiệu. Cả hai trường hợp đều an toàn.
5. Worker chỉ in `proceeded-when-ready=true` sau khi nó đã lấy lại monitor và điều kiện đúng.

Một spurious wakeup thêm cũng không phá chứng minh: có thể xuất hiện nhiều lần kiểm tra false hơn, nhưng cuối cùng chương trình chỉ vượt vòng `while` khi state thật sự cho phép. Demo cố tình không in **số lần chính xác** bị đánh thức vì JDK không bảo đảm số đó.

**Khác biệt cốt lõi:** `if` kiểm tra một lần trước lúc chờ; `while` kiểm tra **trước mỗi lần chờ và sau mỗi lần thức dậy**.

## 7. Ví dụ chạy được 3 — hai consumer được báo, chỉ một item

Nếu hai waiter đã chờ queue rỗng, producer thêm một phần tử và `notifyAll()`, mẫu `if` có thể khiến waiter thứ hai cố lấy phần tử không còn tồn tại.

Ví dụ này điều phối để **hai consumer đều đi vào wait set trước khi producer thêm item**. Ta bắt lỗi từ `removeFirst()` và đếm kết quả thay vì phụ thuộc thread nào thắng.

Tạo file **`TwoConsumersIfDemo.java`**:

~~~java
import java.util.ArrayDeque;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class TwoConsumersIfDemo {
    private static final Object MONITOR = new Object();
    private static final ArrayDeque<String> queue = new ArrayDeque<>();

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch bothWaiting = new CountDownLatch(2);
        AtomicInteger claimed = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        Runnable consume = () -> {
            synchronized (MONITOR) {
                if (queue.isEmpty()) {
                    bothWaiting.countDown();
                    try {
                        MONITOR.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                try {
                    queue.removeFirst();
                    claimed.incrementAndGet();
                } catch (NoSuchElementException e) {
                    failures.incrementAndGet();
                }
            }
        };

        Thread a = new Thread(consume, "consumer-a");
        Thread b = new Thread(consume, "consumer-b");
        a.start();
        b.start();

        bothWaiting.await();
        synchronized (MONITOR) {
            queue.addLast("X");
            MONITOR.notifyAll();
        }

        a.join();
        b.join();

        System.out.println("claimed=" + claimed.get());
        System.out.println("empty-remove-failures=" + failures.get());
    }
}
~~~

Chạy:

~~~bash
javac TwoConsumersIfDemo.java
java TwoConsumersIfDemo
~~~

Stdout khi không có interruption:

~~~text
claimed=1
empty-remove-failures=1
~~~

Tại sao lỗi tái hiện được?
- `bothWaiting` chỉ về 0 sau khi cả hai thread **đã vào synchronized và chuẩn bị gọi wait**. Main chỉ có thể thêm item khi lấy được monitor, tức các waiter đã nhả monitor để chờ.
- `notifyAll()` đánh thức cả hai. Người đầu lấy lock lấy item X; người thứ hai lấy lock sau thì queue đã rỗng.
- Vì dùng `if`, người thứ hai không kiểm tra lại queue, nên `removeFirst()` ném `NoSuchElementException` và được đếm.
- `AtomicInteger` chỉ được dùng để tổng hợp kết quả demo; nó **không sửa lỗi điều kiện chờ**.

Việc **lock bảo vệ queue là cần nhưng chưa đủ** nếu code dùng `if` sau wait: thao tác tiêu thụ phải được thực hiện **chỉ khi predicate đúng tại lần giữ lock cuối cùng**.

## 8. Sửa tình huống nhiều waiter: while kết hợp điều kiện closed

Nếu đổi `if` thành `while(queue.isEmpty()) wait()` trong ví dụ trên, consumer thứ hai sẽ **chờ hợp lệ** sau khi item duy nhất bị lấy. Nhưng nếu producer không bao giờ thêm phần tử nữa, thread đó có thể chờ mãi. Đây không phải bug của `while`; đó là yêu cầu **liveness/shutdown** chưa được thiết kế.

Một giao thức tốt có hai điều kiện:
- Khi `queue` có item: lấy item.
- Khi `queue` trống **và đã đóng**: kết thúc đợi.
- Khi `queue` trống **và chưa đóng**: tiếp tục wait.

Tạo file **`TwoConsumersWhileDemo.java`**:

~~~java
import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class TwoConsumersWhileDemo {
    private static final Object MONITOR = new Object();
    private static final ArrayDeque<String> queue = new ArrayDeque<>();
    private static boolean closed;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch bothWaiting = new CountDownLatch(2);
        AtomicInteger claimed = new AtomicInteger();
        AtomicInteger stopped = new AtomicInteger();

        Runnable consume = () -> {
            synchronized (MONITOR) {
                while (queue.isEmpty() && !closed) {
                    bothWaiting.countDown();
                    try {
                        MONITOR.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                if (queue.isEmpty()) {
                    stopped.incrementAndGet();
                    return;
                }

                queue.removeFirst();
                claimed.incrementAndGet();
            }
        };

        Thread a = new Thread(consume, "consumer-a");
        Thread b = new Thread(consume, "consumer-b");
        a.start();
        b.start();

        bothWaiting.await();

        synchronized (MONITOR) {
            queue.addLast("X");
            closed = true;
            MONITOR.notifyAll();
        }

        a.join();
        b.join();

        System.out.println("claimed=" + claimed.get());
        System.out.println("stopped=" + stopped.get());
        System.out.println("remaining=" + queue.size());
    }
}
~~~

Chạy:

~~~bash
javac TwoConsumersWhileDemo.java
java TwoConsumersWhileDemo
~~~

Stdout:

~~~text
claimed=1
stopped=1
remaining=0
~~~

Tại sao phiên bản này an toàn?
1. Cả hai consumer đợi khi `queue.isEmpty() && !closed`.
2. Main cập nhật **queue và closed trong cùng monitor** rồi `notifyAll()`, tránh trạng thái trung gian không nhất quán.
3. Một consumer lấy X; consumer còn lại thấy queue rỗng nhưng `closed=true`, nên thoát thay vì wait vô hạn hoặc gọi `removeFirst()` sai.
4. Lần check và remove đều dưới cùng monitor. Không ai thay đổi queue giữa hai bước.
5. Các trường `claimed/stopped` chỉ phục vụ kết quả demo; main đọc sau `join()`, khi worker đã kết thúc.

**Lưu ý trong ứng dụng thật:** `closed` ở đây có nghĩa **không nhận thêm phần tử** và consumer vẫn có thể lấy nốt item đã có. Nếu nghiệp vụ yêu cầu hủy toàn bộ hàng chờ ngay khi đóng, cần định nghĩa semantics khác. Đừng tự động áp dụng một quy tắc shutdown cho mọi loại queue.

## 9. Lost notification: vì sao check–wait phải nằm cùng monitor?

Lỗi lost notification khác với lỗi `if` ở mục trước. Ví dụ một thread kiểm tra flag ngoài monitor rồi mới chờ:

~~~java
// SAI: ready được kiểm tra ngoài monitor.
if (!ready) {
    synchronized (monitor) {
        monitor.wait();
    }
}
~~~

Thread khác có thể làm `ready=true` và notify vào **khoảng giữa** hai bước. Lúc ấy không có waiter, notification bị mất; thread cũ bắt đầu wait và có thể chờ vô hạn. `volatile ready` có thể cải thiện visibility cho riêng biến ready, nhưng **không tự ghép** thao tác check và wait thành một giao thức nguyên tử.

Mẫu nhất quán là:

~~~java
synchronized (monitor) {
    while (!ready) {
        monitor.wait();
    }
}
~~~

Đường thay đổi `ready` cũng phải giữ `monitor`:

~~~java
synchronized (monitor) {
    ready = true;
    monitor.notifyAll();
}
~~~

Đó là cách tránh missed notification mà **không cần lưu lịch sử tín hiệu**. Nếu signal đến trước, waiter thấy predicate đã đúng và không chờ. Nếu signal đến sau khi waiter chờ, nó đánh thức waiter để kiểm tra lại.

**Quy tắc giao thức:** predicate phải là trạng thái chia sẻ được bảo vệ; notification là một gợi ý “đến kiểm tra lại” dưới monitor/condition tương ứng. Không đọc predicate bằng một khóa nhưng chờ dưới khóa khác, trừ khi đã có thiết kế coordination khác được chứng minh đúng.

## 10. Trong mỗi vòng while, phải giữ invariant ở điểm nhả khóa

`while` không tự sửa mọi lỗi concurrency. Cần bảo đảm **state của đối tượng nhất quán trước khi `wait()` nhả lock**.

Ví dụ một buffer có `size` và dữ liệu thực trong queue. Nếu code giảm `size` rồi `wait()` trước khi xóa phần tử thật, thread khác vào monitor có thể nhìn thấy `size != queue.size()`. Nó sẽ dựa vào predicate sai và có thể làm hỏng dữ liệu.

Mẫu sai về nguyên tắc:

~~~java
synchronized (monitor) {
    available--;
    monitor.wait(); // Nhả lock lúc invariant chưa được khôi phục.
    reserved++;
}
~~~

Giả định invariant `available + reserved == total`. Sau khi giảm `available` nhưng chưa tăng `reserved`, invariant sai. Nếu thread bị interrupt hoặc exception xảy ra, nó có thể không hoàn tất bước sau; việc `synchronized` tự nhả lock không rollback các thay đổi đã xảy ra.

Một mẫu tốt:

~~~java
synchronized (monitor) {
    while (available <= 0) {
        monitor.wait();  // Chưa đổi state, invariant vẫn đúng.
    }

    available--;
    reserved++;          // Cập nhật đầy đủ trong cùng critical section.
    monitor.notifyAll(); // Nếu predicate của nhóm waiter khác có thể đổi.
}
~~~

Các đoạn trên là **snippet** trong method có xử lý `InterruptedException` và state được khai báo phù hợp. Đường trả/nhả tài nguyên phải dùng cùng monitor và phát tín hiệu sau khi state đổi.

**Invariant** nói đối tượng hợp lệ hay không; **predicate** nói thread đang chờ có thể hành động hay chưa. Buffer rỗng hoàn toàn có thể hợp lệ theo invariant `0 <= size <= capacity` nhưng không đáp ứng predicate `notEmpty`. Sau wakeup, `while` kiểm tra lại predicate; mọi đường cập nhật dưới monitor phải giữ invariant hợp lệ.

## 11. Bounded buffer chạy được với nhiều producer/consumer

Đây là abstraction tự viết để học phối hợp luồng. Trong code production, `BlockingQueue` của thư viện chuẩn thường hợp lý hơn; nhưng việc tự viết cho thấy vì sao cần **hai vòng while**.

Tạo file **`BoundedBufferWhileDemo.java`**:

~~~java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BoundedBufferWhileDemo {
    private static final class Buffer<T> {
        private final ArrayDeque<T> queue = new ArrayDeque<>();
        private final int capacity;

        Buffer(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            this.capacity = capacity;
        }

        synchronized void put(T value) throws InterruptedException {
            while (queue.size() == capacity) {
                wait();
            }
            queue.addLast(value);
            notifyAll();
        }

        synchronized T take() throws InterruptedException {
            while (queue.isEmpty()) {
                wait();
            }
            T value = queue.removeFirst();
            notifyAll();
            return value;
        }

        synchronized int size() {
            return queue.size();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Buffer<Integer> buffer = new Buffer<>(2);
        AtomicInteger sum = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();

        for (int p = 0; p < 2; p++) {
            final int base = p * 5;
            Thread producer = new Thread(() -> {
                try {
                    for (int i = 1; i <= 5; i++) {
                        buffer.put(base + i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(producer);
        }

        for (int c = 0; c < 2; c++) {
            Thread consumer = new Thread(() -> {
                try {
                    for (int i = 0; i < 5; i++) {
                        sum.addAndGet(buffer.take());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(consumer);
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        System.out.println("sum=" + sum.get());
        System.out.println("remaining=" + buffer.size());
    }
}
~~~

Chạy:

~~~bash
javac BoundedBufferWhileDemo.java
java BoundedBufferWhileDemo
~~~

Stdout khi không có interruption:

~~~text
sum=55
remaining=0
~~~

**Chứng minh kết quả:** Hai producer đưa đúng các số từ 1 đến 5 và 6 đến 10 (tổng 55); hai consumer lấy tổng cộng 10 phần tử. `synchronized put/take` giữ invariant `0 <= size <= 2` và không cho hai consumer lấy cùng phần tử. `while` ngăn lấy từ queue rỗng hoặc thêm vào buffer đã đầy sau wakeup. `AtomicInteger` dùng để cộng kết quả từ hai consumer, tránh race trên tổng; `join()` chờ tất cả kết thúc trước khi main in.

**Những điều không cố định:** thứ tự phần tử giữa **hai producer khác nhau** và consumer nào nhận số nào không được dự đoán. Chương trình chỉ khẳng định tổng và số lượng cuối. Nếu một thread bị interrupt và dừng sớm, phía còn lại có thể chờ vô hạn; demo cố tình không có giao thức close để tập trung vào while, vì thế không thay nó cho một queue production chưa có cancellation/shutdown.

### Khi nhiều nhóm waiter dùng chung monitor

Producer chờ `notFull`; consumer chờ `notEmpty`. Một `notify()` ngẫu nhiên có thể đánh thức producer khi buffer vẫn đầy hoặc consumer khi vẫn rỗng, trong khi nhóm cần thức có thể còn ngủ. `notifyAll()` đơn giản và dễ chứng minh liveness hơn trong ví dụ monitor chung; mọi waiter phải kiểm tra lại predicate. Giá đổi lại là có thể đánh thức nhiều thread rồi chúng đi ngủ lại (contention/thundering herd).

Trong cấu trúc có nhiều loại predicate, `ReentrantLock` kết hợp nhiều `Condition` giúp signal đúng nhóm.

## 12. Condition.await() và signal(): cùng nguyên tắc while

Với `java.util.concurrent.locks.ReentrantLock`:

~~~java
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// Các thành viên/method của một class (snippet, không phải file độc lập):
private final ReentrantLock lock = new ReentrantLock();
private final Condition notEmpty = lock.newCondition();
private final ArrayDeque<String> queue = new ArrayDeque<>();

public String take() throws InterruptedException {
    lock.lockInterruptibly();
    try {
        while (queue.isEmpty()) {
            notEmpty.await();
        }
        return queue.removeFirst();
    } finally {
        lock.unlock();
    }
}

public void put(String value) {
    lock.lock();
    try {
        queue.addLast(value);
        notEmpty.signal();
    } finally {
        lock.unlock();
    }
}
~~~

Nếu sử dụng snippet này trong class, cần import thêm `java.util.ArrayDeque`. Các ràng buộc:
- Caller phải **giữ đúng ReentrantLock** gắn với Condition trước khi gọi `await()/signal()`.
- `await()` **nhả lock trong lúc chờ**, rồi lấy lại trước khi trở về hoặc ném `InterruptedException`.
- `signal()` **không nhả lock ngay** và không trao quyền vượt predicate.
- Ngay cả khi Condition chỉ có một loại waiter, spurious wakeup và các thread khác vẫn có thể khiến predicate sai; luôn dùng `while`.
- `unlock()` trong `finally` giúp nhả lock khi có exception.

**Không chuyển máy móc từ `notifyAll` sang `signal`** mà không xét liveness. `signal` chọn một waiter trên condition đó; nếu nhiều loại predicate dùng chung một Condition, đánh thức sai loại hoặc không đủ waiter có thể gây kẹt. Dùng các Condition riêng cho `notEmpty/notFull` khi cần, hoặc `signalAll` nếu giao thức yêu cầu đánh thức tất cả.

### Khi nào không cần viết while thủ công?

`BlockingQueue.take()` đã tự thực hiện giao thức chờ/lấy phần tử theo hợp đồng class; caller không cần kiểm tra `isEmpty()` rồi gọi `take()`:

~~~java
String item = blockingQueue.take();
~~~

Đây là snippet bên trong method có `blockingQueue` được khai báo, phải xử lý `InterruptedException`. Nếu cần không chờ, dùng `poll()`, trả `null` khi trống (với queue chuẩn không chứa null). Nếu cần thời gian chờ giới hạn, dùng `poll(timeout, unit)`.

**Không viết:**

~~~java
if (!blockingQueue.isEmpty()) {
    String item = blockingQueue.take();
}
~~~

Bởi một consumer khác có thể lấy hết queue sau `isEmpty()`, khiến `take()` đợi dù điều kiện đã từng đúng. Chỉ một lời gọi `take/poll` có semantics thích hợp sẽ tránh check-then-act ở phía caller.

## 13. Timed wait: while vẫn cần, nhưng không được khởi động lại timeout

Các overload như `Object.wait(long millis)`, `Condition.await(long time, TimeUnit unit)` và `Condition.awaitNanos(long nanosTimeout)` cho phép chờ có hạn.

**Lưu ý:** `Object.wait(0)` là chờ **không thời hạn**, không phải “không chờ”. Một lần timed wait có thể trở về vì tín hiệu, spurious wakeup, hết thời gian hoặc interruption; trong mọi trường hợp, không suy ra predicate đúng khi chưa kiểm tra.

Mẫu chưa đạt yêu cầu deadline tổng:

~~~java
while (!ready) {
    monitor.wait(1_000); // Mỗi wakeup lại cấp nguyên một giây.
}
~~~

Nếu có nhiều lần wakeup khi `ready` còn false, tổng thời gian đợi có thể vượt 1 giây rất xa. Với timeout tổng, dùng **thời gian còn lại**.

### Mẫu Condition.awaitNanos

~~~java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// Snippet là field/method bên trong một class:
private final ReentrantLock lock = new ReentrantLock();
private final Condition readyChanged = lock.newCondition();
private boolean ready;

public boolean awaitReady(long timeout, TimeUnit unit)
        throws InterruptedException {
    long remaining = unit.toNanos(timeout);
    lock.lockInterruptibly();
    try {
        while (!ready) {
            if (remaining <= 0) {
                return false;
            }
            remaining = readyChanged.awaitNanos(remaining);
        }
        return true;
    } finally {
        lock.unlock();
    }
}

public void makeReady() {
    lock.lock();
    try {
        ready = true;
        readyChanged.signalAll();
    } finally {
        lock.unlock();
    }
}
~~~

- `while` kiểm tra predicate sau mỗi lần return từ `awaitNanos`.
- `remaining` được giảm theo kết quả của API, tránh cấp lại timeout ban đầu.
- Method trả `false` nếu hết budget condition-wait trong lúc predicate chưa đúng.
- Method trả `true` **chỉ sau khi quan sát ready=true khi giữ lock**.
- Tính toán thời gian ở đây bao quát **quãng đợi condition**, không phải deadline end-to-end bao gồm lúc đầu chờ lấy `lock`. Nếu cần deadline toàn bộ, phải tính thời gian của cả `lockInterruptibly()` bằng một giao thức `tryLock(remaining, unit)`/deadline thích hợp.

Đừng dùng `Thread.sleep` lặp lại như timed condition wait trong ứng dụng có độ nhạy latency hoặc cần hủy nhanh.

## 14. Interruption và exception: không được vượt qua điều kiện

Khi thread bị interrupt lúc `Object.wait()` hoặc `Condition.await()`, API có thể ném `InterruptedException`. **Interruption không phải dấu hiệu predicate đã đúng**. Worker cần dừng, propagate exception hoặc xử lý một chính sách cancellation riêng. Không bắt exception rồi tiếp tục thao tác nghiệp vụ như thể đã được cấp quyền.

Mẫu phù hợp trong một method khai báo `throws InterruptedException`:

~~~java
synchronized (monitor) {
    while (!ready) {
        monitor.wait(); // Nếu bị interrupt, ngoại lệ truyền ra.
    }
    useResource();
}
~~~

Nếu không thể propagate:

~~~java
try {
    doInterruptibleWait();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // Khôi phục interrupt status.
    return; // Không sử dụng tài nguyên khi predicate chưa đúng.
}
~~~

Đây là snippet trong method trả `void`. Với `Object.wait()`, JVM bắt thread phải lấy lại monitor trước khi wait trở về hoặc ném interruption theo contract, bảo đảm `synchronized` vẫn kiểm soát vùng nhả khóa. Nhưng trạng thái chia sẻ có thể đã được **các thread khác** sửa trong khoảng chờ; không nên suy ra nó không đổi.

### Exception sau một thao tác nửa chừng

Giả sử invariant `available + reserved == total`; code cập nhật `available--` rồi gọi một phương thức có thể throw, sau đó mới tăng `reserved++`. Nếu exception xảy ra ở giữa, `synchronized` tự nhả monitor nhưng **không rollback** state. Hãy validate trước, commit thay đổi đầy đủ trong critical section và có rollback nếu nghiệp vụ yêu cầu; không bao giờ đi vào `wait()` khi trạng thái đang phá invariant mà các thread khác có thể quan sát.

### Shutdown phải là một phần của predicate

Nếu producer dừng vĩnh viễn, consumer có thể chờ mãi dù vòng `while` là chính xác. Cần thêm điều kiện `closed`/`cancelled` và phát `notifyAll/signalAll` khi shutdown:

~~~java
synchronized (monitor) {
    while (queue.isEmpty() && !closed) {
        monitor.wait();
    }

    if (queue.isEmpty()) {
        // closed == true và không còn item:
        // kết thúc theo giao thức (ví dụ Optional.empty).
    } else {
        // lấy phần tử và xử lý.
    }
}
~~~

Đây là snippet mô tả control flow, không phải một chương trình độc lập. Đường ghi `closed=true` phải nằm trong cùng monitor, rồi đánh thức tất cả waiter liên quan. Nếu có hai nhóm waiter như producer và consumer, shutdown có thể phải signal **cả hai** nhóm. Khi close có nghĩa là không được thêm mới nhưng được lấy hết hàng đợi, consumer vẫn có thể lấy item còn lại trước khi kết thúc.

## 15. Những hiểu lầm và lỗi điển hình

| Sai lầm | Hậu quả | Cách đúng |
| --- | --- | --- |
| `if (!ready) wait()` | Bỏ qua kiểm tra sau wakeup | `while (!ready) wait()` |
| Nghĩ `notify()` nghĩa điều kiện chắc chắn đúng | Notify không mang predicate/giữ chỗ | Đọc state dưới monitor sau wakeup |
| Chỉ nghĩ tới spurious wakeup | Bỏ qua contention giữa nhiều waiter | Xem cả cạnh tranh tiêu thụ tài nguyên |
| Check ngoài lock rồi mới wait trong lock | Missed notification | Check và wait dưới cùng monitor |
| Producer sửa state ngoài monitor | Không có giao thức nhất quán/visibility | Mọi đường truy cập dùng cùng lock |
| Gọi wait/notify mà không giữ đúng monitor | `IllegalMonitorStateException` | Giữ monitor của object được gọi |
| Cho rằng notifyAll giải phóng monitor | Waiter vẫn cần tranh lock | Thread signal phải thoát vùng khóa |
| Dùng `Thread.sleep()` thay wait | Sleep không nhả monitor | Dùng condition wait hoặc BlockingQueue |
| Dùng `getState() == WAITING` để quyết định notify | Snapshot có race và không phải predicate nghiệp vụ | Dựa vào state được bảo vệ |
| Nhả lock giữa check và lấy phần tử | Check-then-act race | Cả hai thao tác trong critical section |
| Chờ khi invariant đang sai | Thread khác có thể thấy state chưa hoàn chỉnh | Khôi phục invariant trước wait |
| Dùng `notify()` khi có nhiều loại waiter | Đánh thức sai nhóm, có thể mất tiến triển | `notifyAll` hoặc Condition riêng |
| Timed wait luôn dùng lại timeout ban đầu | Wakeups làm tổng chờ vượt deadline | Tính remaining/deadline |
| Bỏ qua `InterruptedException` | Cancellation/shutdown bị treo | Propagate hoặc restore rồi thoát |
| Chỉ có một item nhưng hai waiter, không có closed | Một waiter có thể chờ mãi hợp lệ | Thiết kế close/cancel protocol |
| Tự viết wait/notify cho một queue đơn giản | Dễ mắc lỗi không cần thiết | Ưu tiên `BlockingQueue.take/poll` |
| Dùng `LongAdder/AtomicInteger` để “sửa” if wait | Atomic counter không thay predicate/lock protocol | Thiết kế condition đúng |

## 16. Checklist thiết kế cơ chế chờ

1. **Predicate chính xác là gì?** Không chỉ là “có notification”, mà là một điều kiện có thể kiểm tra như `!queue.isEmpty()` hoặc `ready == true`.
2. **State nào tạo predicate?** Ai có quyền thay đổi, ai đọc, tất cả có dùng cùng monitor/lock không?
3. **Invariant là gì?** Nó có đúng trước khi thread nhả lock hoặc đi vào `wait()/await()` không?
4. **Kiểm tra predicate trong vòng while chưa?** Nếu thức dậy do spurious wakeup hoặc waiter khác tiêu thụ tài nguyên, chương trình có kiểm tra lại không?
5. **Điều kiện cuối và hành động có cùng critical section?** Nếu nhả lock giữa hai bước, condition có thể biến mất.
6. **Signal có đến sau khi state đổi không?** Signal không thay thế state; nếu state chưa đổi thì waiter sẽ lại chờ.
7. **Có thể signal xảy ra trước waiter không?** Mẫu đúng phải khiến waiter quan sát predicate đã đúng và không wait.
8. **Có nhiều nhóm waiter không?** Chọn `notifyAll` hoặc các `Condition` riêng để tránh đánh thức sai nhóm.
9. **Có timeout tổng và interruption không?** Không tái sử dụng nguyên timeout sau mỗi wakeup; có đường thoát khi task bị hủy.
10. **Có thể không còn producer không?** Cần `closed/cancelled`, poison pill hoặc giao thức kết thúc rõ.
11. **Có lock khác bị giữ khi wait không?** `wait()` chỉ nhả đúng monitor của nó.
12. **Có thể dùng primitive cấp cao?** `BlockingQueue` cho dữ liệu, `CountDownLatch` cho tín hiệu một lần, `Semaphore` cho permits, `Phaser` cho pha xử lý.
13. **Đã kiểm tra liveness?** Đúng invariant nhưng thread chờ mãi cũng có thể là lỗi thiết kế.
14. **Đã kiểm tra logic khi có exception?** Nhả lock không có nghĩa rollback.
15. **Đã dùng test điều phối thay vì sleep để tái hiện?** Latch/barrier giúp ép một interleaving; stress test bổ sung nhưng không chứng minh vắng race.

## 17. Câu hỏi tự kiểm tra

1. Vì sao sau `notifyAll()` không thể dùng `if` rồi mặc nhiên tiếp tục? **Vì thread khác có thể lấy tài nguyên trước, predicate có thể sai và có spurious wakeup.**
2. Nếu chỉ một consumer đang chờ, `while` có còn cần không? **Có**, vì spurious wakeup và predicate vẫn có thể thay đổi.
3. `Object.wait()` có nhả mọi lock đang giữ không? **Không**, chỉ monitor của object gọi wait.
4. `Thread.sleep(100)` bên trong synchronized có giúp producer vào monitor không? **Không**, sleep giữ monitor.
5. Nếu producer notify khi consumer chưa bắt đầu chờ, chuyện gì xảy ra với giao thức đúng? **Consumer kiểm tra state, thấy predicate đúng thì không wait.**
6. Tại sao `volatile ready` không tự ngăn missed notification khi check và wait tách rời? **Volatile không khiến hai bước đó nguyên tử trong một monitor protocol.**
7. Hai consumer được notifyAll với một item, tối đa bao nhiêu người lấy thành công? **Một**. Người kia phải check lại rồi đợi hoặc thoát nếu closed.
8. Nếu code dùng while mà vẫn chờ mãi khi không còn producer, đó có phải lỗi while không? **Không; thiếu giao thức kết thúc/timeout/cancel.**
9. Điều kiện nào để gọi `Condition.await()` hợp lệ? **Thread giữ lock gắn với Condition và xử lý interruption.**
10. Sau khi `Condition.await()` trả về, có được bỏ qua predicate không? **Không**, phải dùng while.
11. `notify()` và `notifyAll()` khác nhau thế nào? **Notify chọn một waiter; notifyAll đánh thức tất cả waiter trong wait set, không bảo đảm thứ tự chạy.**
12. Tại sao một timed wait trong vòng while không nên luôn dùng lại 1 giây? **Mỗi wakeup làm tổng thời gian tăng vượt deadline mong muốn.**
13. Khi `InterruptedException` xuất hiện, có thể tiếp tục dùng tài nguyên chờ được không? **Không tự động; interruption là đường hủy, không chứng minh điều kiện đúng.**
14. Vì sao gọi `wait()` giữa hai bước cập nhật state liên quan có thể sai invariant? **Wait nhả monitor, cho thread khác nhìn state trung gian.**
15. Có nên dùng `if (!queue.isEmpty()) queue.take()` để tránh block? **Không, queue có thể trống sau lần check; dùng `poll()` nếu muốn không chờ.**
16. Trong thí nghiệm `IfWaitBugDemo`, vì sao in false là hợp lệ dù đã nhận notify? **Thông báo được phát nhưng ready không đổi; if không kiểm tra lại.**
17. Trong thí nghiệm `TwoConsumersIfDemo`, vì sao một lần remove thất bại? **Cả hai waiter được đánh thức nhưng chỉ có một item; waiter thứ hai không kiểm tra lại.**
18. Điều gì quan trọng hơn việc quan sát `Thread.State.WAITING`? **Trạng thái nghiệp vụ và predicate dưới giao thức đồng bộ đúng.**

## Kết luận

**Một thread thức dậy không có nghĩa điều kiện đã sẵn sàng.** `wait()/await()` có thể trở về vì spurious wakeup, và ngay cả với notification thực, thread khác có thể tiêu thụ tài nguyên trước waiter. Do vậy, condition waiting trong Java phải dựa trên **while kiểm tra predicate dưới cùng khóa**, không dựa vào `if` chỉ kiểm tra một lần.

Giao thức đúng: **giữ khóa → kiểm tra predicate → nếu sai thì wait/await và nhả khóa → lấy lại khóa → kiểm tra lại trong while → thực hiện thao tác cần thiết khi vẫn giữ khóa → cập nhật invariant và phát tín hiệu → nhả khóa**. Cần bổ sung timeout, interruption và shutdown để bảo đảm tiến triển; ưu tiên primitive cấp cao như `BlockingQueue` khi không cần tự xây monitor protocol.

## Tài liệu tham khảo Java SE 17

- [java.lang.Object — wait, notify, notifyAll](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html): monitor ownership, wait set, spurious wakeup, reacquire.
- [java.util.concurrent.locks.Condition](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Condition.html): await, signal, signalAll và điều kiện chờ.
- [java.util.concurrent.locks.ReentrantLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): lock, lockInterruptibly, unlock.
- [java.util.concurrent.BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): take, poll và timeout.
- [Java Language Specification 17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): happens-before và Java Memory Model.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-coordination-003">Spurious wakeup và kiểm tra lại invariant</a>

<details>
<summary>Xem chi tiết</summary>

# Spurious wakeup và kiểm tra lại invariant trong Java

## 1. Vấn đề: được đánh thức không có nghĩa điều kiện đã đúng

Khi phối hợp nhiều luồng, một thread thường phải chờ một **điều kiện nghiệp vụ**: hàng đợi có phần tử, buffer còn chỗ trống, tác vụ đã hoàn thành, hoặc một trạng thái đã chuyển sang `READY`. Java cung cấp `Object.wait()`/`notify()`/`notifyAll()` và `Condition.await()`/`signal()`/`signalAll()` để chờ và thông báo giữa các thread.

**Spurious wakeup** là trường hợp một lời gọi chờ có thể **trở về dù không có thông báo, interrupt hoặc timeout đủ để giải thích việc trở về**, như đặc tả Java cho phép. Chương trình đúng không được giả định mỗi lần `wait()` hoặc `await()` trở về đều có nghĩa điều kiện mong muốn đã thỏa.

Quan trọng hơn, **ngay cả khi không có spurious wakeup**, điều kiện vẫn có thể sai khi thread được đánh thức:

- Một thread khác gọi `notifyAll()`/`signalAll()`, nhưng chỉ một số waiter có thể tiêu thụ tài nguyên.
- Tín hiệu liên quan một loại thay đổi trạng thái khác, không phải điều kiện thread này đang chờ.
- Thread đã được signal nhưng phải **lấy lại khóa**; một thread khác có thể thay đổi trạng thái trước khi nó reacquire.
- Waiter được đánh thức sau một biến cố nhưng predicate bị thay đổi trở lại trước khi tiếp tục.
- Có timeout hoặc interrupt; mỗi lý do thức dậy phải được xử lý theo hợp đồng tương ứng.

**Quy tắc nền tảng: mọi lời chờ có điều kiện phải nằm trong vòng `while` kiểm tra lại predicate dưới cùng khóa bảo vệ trạng thái.**

~~~java
// Mẫu đúng, không phải chương trình độc lập.
// Thread phải đang giữ monitor của lock.
synchronized (lock) {
    while (!conditionSatisfied()) {
        lock.wait();
    }
    // Tại đây predicate đúng và ta vẫn giữ đúng monitor.
    consumeOrUpdateState();
}
~~~

Đối với `Condition`:

~~~java
// Mẫu đúng, không phải chương trình độc lập.
// state được bảo vệ bởi cùng ReentrantLock.
lock.lock();
try {
    while (!conditionSatisfied()) {
        condition.await();
    }
    consumeOrUpdateState();
} finally {
    lock.unlock();
}
~~~

Không tự thay `while` bằng `if` chỉ vì “đã chắc chắn bên phát tín hiệu gọi notify”. **Tín hiệu thông báo là gợi ý rằng có thể đã thay đổi trạng thái; predicate dưới lock mới là nguồn sự thật.**

Bài này thuộc phạm vi **phối hợp luồng Java nói chung**, sử dụng các API sẵn có trong JDK. Các cơ chế dành riêng cho framework không nằm trong phạm vi bài.

## 2. Invariant và condition predicate là gì?

### 2.1. Phân biệt invariant, predicate và signal

Một **invariant** là thuộc tính phải luôn đúng tại những thời điểm được bảo vệ bởi giao thức đồng bộ. Ví dụ buffer có `capacity` phần tử phải giữ:

~~~text
0 <= size <= capacity
~~~

Để chờ được đọc, consumer dùng **predicate** `!queue.isEmpty()`. Để chờ được ghi, producer dùng predicate `queue.size() < capacity`. Các predicate này được suy ra từ trạng thái mà khóa bảo vệ; không phải từ số lần `signal()` đã xảy ra.

Một **signal** chỉ cho phép một hoặc nhiều waiter được đánh thức để kiểm tra lại điều kiện. Nó không tự “cấp một phần tử”, không giữ chỗ trong queue và không cam kết predicate vẫn đúng khi waiter chạy.

| Khái niệm | Câu hỏi | Ví dụ buffer |
| --- | --- | --- |
| Invariant | Hệ thống luôn phải bảo toàn điều gì? | `0 <= size <= capacity` |
| Predicate | Waiter phải kiểm tra điều kiện cụ thể nào? | `size > 0` để take |
| State transition | Biến cố nào có thể khiến predicate đổi từ sai sang đúng? | Producer thêm phần tử |
| Signal | Ai cần được đánh thức để kiểm tra lại? | `notEmpty.signal()` |
| Lock | Ai bảo vệ quá trình kiểm tra và thay đổi state? | Một monitor/`ReentrantLock` chung |

Dùng **cùng một khóa** cho đọc predicate, thay đổi state và đi vào chờ ngăn tình huống kiểm tra trạng thái ở ngoài lock rồi bỏ lỡ thông báo trong lúc chuyển sang chờ.

### 2.2. Vì sao phải dùng while?

Giả sử hàng đợi ban đầu trống, hai consumer C1, C2 đều chờ. Producer thêm **một** phần tử rồi `signalAll()`:

~~~text
Ban đầu: queue = []
C1: await() vì queue trống
C2: await() vì queue trống

Producer: put(X), signalAll(), unlock()
C1: reacquire, thấy X, lấy X, unlock()
C2: reacquire, queue đã trống trở lại
~~~

C2 **đã được signal hợp lệ**, nhưng khi nó reacquire, predicate `!queue.isEmpty()` là **false**. Nếu chỉ dùng `if (empty) await()` rồi chạy tiếp `remove()`, C2 có thể đọc từ queue trống và lỗi. Với `while`, C2 quay lại chờ một phần tử khác.

Đây là ví dụ của **predicate không còn đúng sau tín hiệu**, không nhất thiết là spurious wakeup. Hai lý do khác nhau nhưng cùng dẫn đến yêu cầu dùng `while`.

## 3. Object.wait(): cơ chế và điều kiện sử dụng

### 3.1. wait thuộc object monitor

`Object.wait()`, `notify()` và `notifyAll()` hoạt động trên **monitor** của đúng object đó. Thread gọi phải đang giữ monitor tương ứng; cách rõ ràng nhất là dùng `synchronized(lock)`.

~~~java
// Mẫu dùng đúng monitor; không phải chương trình độc lập.
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
}
~~~

Khi gọi `lock.wait()` hợp lệ:

1. Thread **tạm nhả monitor của chính `lock`** và chuyển vào hàng chờ liên quan đến monitor đó.
2. Thread có thể được đánh thức do notify/notifyAll, interrupt, hoặc spurious wakeup; với biến thể có timeout, còn có timeout.
3. Trước khi `wait()` trở về bình thường hoặc ném `InterruptedException`, thread phải **lấy lại monitor của `lock`**.
4. Thread tiếp tục bên trong vùng `synchronized` và có thể kiểm tra predicate trong khi giữ monitor.

`wait()` không nhả **mọi khóa khác** mà thread đang sở hữu. Nếu một thread giữ thêm monitor B rồi gọi `lockA.wait()`, nó vẫn có thể giữ B trong lúc chờ, gây deadlock khi thread muốn thay đổi predicate cũng cần B.

Gọi `wait()` hoặc `notify()`/`notifyAll()` mà **không giữ monitor** của receiver sẽ ném `IllegalMonitorStateException`. Không dùng `Thread.sleep(...)` để thay thế `wait()`: sleep **không nhả khóa**, không đợi predicate và không tạo thông báo phối hợp.

### 3.2. notify, notifyAll và số lượng waiter

- `notify()` chọn **một** thread trong tập waiter trên monitor để đánh thức, nếu có. Không có bảo đảm bạn sẽ chọn đúng loại waiter của predicate hiện tại.
- `notifyAll()` đánh thức **tất cả** waiter trong tập tương ứng. Họ vẫn phải cạnh tranh lấy lại monitor và kiểm tra predicate.
- Chỉ gọi `notify`/`notifyAll` khi giữ monitor không có nghĩa waiter chạy ngay: thread đang giữ monitor còn phải nhả nó, và scheduler mới quyết định thời điểm waiter tiếp tục.
- Nếu **không có waiter**, notify không lưu “credit” chờ được nhận sau. Đây là lý do cần giữ trạng thái như `ready` hoặc `queue.size()` và luôn kiểm tra trước khi đi vào wait.

Trong ví dụ đơn giản, `notifyAll()` dễ tránh chọn sai loại waiter, nhưng đánh thức nhiều thread có thể gây **thundering herd** (nhiều waiter thức dậy chỉ để thấy predicate vẫn sai). Khi có nhiều loại predicate, `Condition` với các condition queue riêng đôi khi rõ ràng hơn.

### 3.3. Ví dụ 1 — Gate an toàn với while (Java 17+)

Tạo tệp `ObjectWaitGateDemo.java`:

~~~java
public class ObjectWaitGateDemo {
    static final class Gate {
        private boolean open;

        synchronized void awaitOpen()
                throws InterruptedException {
            while (!open) {
                wait();
            }
        }

        synchronized void open() {
            open = true;
            notifyAll();
        }

        synchronized boolean isOpen() {
            return open;
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Gate gate = new Gate();

        Thread worker = new Thread(() -> {
            try {
                gate.awaitOpen();
                System.out.println("worker passed");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "gate-waiter");

        worker.start();
        gate.open();
        worker.join();

        System.out.println("open = " + gate.isOpen());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ObjectWaitGateDemo.java
java ObjectWaitGateDemo
~~~

Stdout khi không có interrupt ngoài ý muốn:

~~~text
worker passed
open = true
~~~

Tình huống race quan trọng: nếu `gate.open()` xảy ra **trước** khi worker kịp vào `awaitOpen()`, worker thấy `open == true` ngay khi giữ monitor nên không cần wait. Nếu worker đi vào wait trước, `open()` đổi trạng thái và notifyAll dưới cùng monitor, nên worker sẽ kiểm tra lại và đi tiếp.

Với một spurious wakeup khi `open` còn false, `while` buộc worker quay lại `wait()`; không có dòng “worker passed” sai. Kết quả không phụ thuộc việc scheduler chạy worker trước hay thread main trước.

Đoạn này dùng `synchronized` ở tất cả đường đọc/ghi `open`, nên còn có bảo đảm visibility/happens-before qua monitor. `join()` giúp main chờ worker hoàn tất trước khi in dòng cuối.

## 4. Phân biệt spurious wakeup với thông báo không phù hợp

### 4.1. Spurious wakeup không phải “notify sai”

Một spurious wakeup là việc wait/await có thể trở về mà **không có nguyên nhân thông báo/timeout/interrupt cần thiết**. Nó được đặc tả cho phép. Không nên viết code hoặc test dựa vào kỳ vọng JVM **phải** tạo spurious wakeup trong một lần chạy.

Mặt khác, thread có thể được gọi `notifyAll()` một cách **hoàn toàn hợp lệ** nhưng predicate của nó vẫn sai. Trường hợp này có thể tái hiện có kiểm soát bằng Java để chứng minh `if` không đủ, mà **không phải giả vờ rằng đã tạo được spurious wakeup thực sự**.

### 4.2. Ví dụ 2 — lỗi if với thông báo không thay đổi predicate (Java 17+)

Ví dụ sau **cố ý sai**, dùng để hiểu vì sao phải sửa bằng `while`. Một thread được thông báo nhưng điều kiện `ready` vẫn false, nên nó không được tiếp tục công việc.

Tạo tệp `WrongIfWaitDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;

public class WrongIfWaitDemo {
    static final class Gate {
        private boolean ready;
        private final CountDownLatch aboutToWait =
            new CountDownLatch(1);

        synchronized boolean awaitOnce()
                throws InterruptedException {
            if (!ready) { // SAI: phải là while (!ready).
                aboutToWait.countDown();
                wait();
            }
            return ready;
        }

        void waitUntilEntered() throws InterruptedException {
            aboutToWait.await();
        }

        synchronized void unrelatedNotify() {
            notifyAll(); // Không thay đổi ready.
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Gate gate = new Gate();

        Thread worker = new Thread(() -> {
            try {
                boolean result = gate.awaitOnce();
                System.out.println("predicate = " + result);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "incorrect-waiter");

        worker.start();
        gate.waitUntilEntered();
        gate.unrelatedNotify();
        worker.join();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac WrongIfWaitDemo.java
java WrongIfWaitDemo
~~~

Stdout khi worker không bị interrupt:

~~~text
predicate = false
~~~

Vì sao kết quả xác định được?

1. `aboutToWait.countDown()` xảy ra **bên trong monitor của Gate**, ngay trước `wait()`.
2. Thread main chỉ gọi `unrelatedNotify()` sau khi latch mở; `unrelatedNotify()` cũng phải lấy **monitor của Gate**.
3. Nếu worker vẫn chưa gọi `wait()`, main chưa thể lấy monitor để notify. Do đó khi main vào `unrelatedNotify()`, worker đã nhả monitor để chờ, hoặc đã tự thức dậy rồi đi tiếp.
4. `unrelatedNotify()` đánh thức waiter nếu nó còn đang chờ, nhưng **không đặt `ready = true`**.
5. Vì code dùng `if`, worker không kiểm tra lại predicate sau khi `wait()` trở về và in `false`.

Ví dụ này không phụ thuộc spurious wakeup: chính một thông báo có thật, nhưng không phù hợp, đã phơi bày lỗi. Nếu thay `if (!ready)` bằng `while (!ready)`, worker sẽ **tiếp tục chờ**, vì chưa có code nào đặt ready thành true. Khi sửa thành ứng dụng thực, phải bổ sung một lệnh chuyển trạng thái hợp lệ và notify để worker hoàn thành, không được chỉ đổi `if` rồi để test treo.

## 5. Condition.await(): cùng nguyên tắc, API linh hoạt hơn

`java.util.concurrent.locks.Condition` thường được tạo từ `ReentrantLock.newCondition()`. Một lock có thể tạo **nhiều condition queue**, ví dụ `notEmpty` và `notFull`, giúp tách loại waiter.

Khung đúng:

~~~java
// Mẫu Condition, không phải chương trình độc lập.
lock.lock();
try {
    while (!predicate()) {
        condition.await();
    }
    processSharedState();
} finally {
    lock.unlock();
}
~~~

Khi `await()` được gọi trong lúc thread giữ khóa phù hợp:

- Nó **nhả lock liên kết** với condition trong khi chờ; với `ReentrantLock`, trạng thái số lần giữ khóa được quản lý khi nhả và reacquire.
- Nó có thể trở về vì `signal()`/`signalAll()`, spurious wakeup, hoặc bị gián đoạn theo hợp đồng của overload.
- Trước khi trở về bình thường, waiter **lấy lại lock**, rồi tiếp tục trong cùng vùng bảo vệ trạng thái.
- `signal()` không chuyển ngay quyền sở hữu lock sang waiter; thread đang signal vẫn giữ lock đến khi unlock.
- Với `ConditionObject` do `ReentrantLock` tạo, gọi `await()` hoặc `signal()` khi không giữ lock phù hợp là sai; có thể ném `IllegalMonitorStateException`.

### 5.1. Điều kiện không phải một biến Boolean

Một `Condition` không tự lưu trữ predicate “ready”. Nó chứa cơ chế xếp hàng và đánh thức. State thực sự cần được giữ trong trường dữ liệu được lock bảo vệ: `count`, `queue`, `closed`, `ready`, v.v.

Sai khi suy luận: “Đã gọi `signal()`, vậy người chờ sau này sẽ nhận tín hiệu đó.” Signal không tích lũy credit cho waiter chưa bắt đầu chờ. Nếu cần lưu sự kiện hoặc permit, dùng trạng thái rõ ràng hay primitive có semantics tích lũy như `Semaphore`, `BlockingQueue`, khi phù hợp.

### 5.2. Condition có thể thức dậy giả

Theo API `Condition`, các triển khai cho phép spurious wakeup và lập trình viên **luôn phải giả định nó có thể xảy ra**. Một implementation cụ thể có thể ít hoặc không tạo wakeup giả trên đường thực tế; điều đó không đổi hợp đồng viết code.

Vòng `while` còn giúp xử lý việc một thread khác giành lại lock và thay đổi predicate trước waiter. Có hoặc không có spurious wakeup, điều kiện vẫn phải được kiểm tra **trong vùng khóa**.

## 6. Ví dụ 3 — bounded buffer với hai predicate (Java 17+)

Một buffer có dung lượng hữu hạn cần hai điều kiện:

- Consumer chờ `!queue.isEmpty()` trước khi lấy phần tử.
- Producer chờ `queue.size() < capacity` trước khi thêm phần tử.

Cả hai được bảo vệ bằng **một `ReentrantLock`**, nhưng có **hai Condition** tách biệt để báo đúng nhóm waiter.

Tạo tệp `BoundedBufferConditionDemo.java`:

~~~java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BoundedBufferConditionDemo {
    static final class Buffer {
        private final Deque<Integer> queue = new ArrayDeque<>();
        private final int capacity;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();
        private final Condition notFull = lock.newCondition();

        Buffer(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException(
                    "Capacity must be positive"
                );
            }
            this.capacity = capacity;
        }

        void put(int value) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (queue.size() == capacity) {
                    notFull.await();
                }
                queue.addLast(value);
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        int take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (queue.isEmpty()) {
                    notEmpty.await();
                }
                int result = queue.removeFirst();
                notFull.signal();
                return result;
            } finally {
                lock.unlock();
            }
        }

        int size() {
            lock.lock();
            try {
                return queue.size();
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Buffer buffer = new Buffer(1);

        Thread producer = new Thread(() -> {
            try {
                buffer.put(10);
                buffer.put(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        producer.start();

        System.out.println("taken = " + buffer.take());
        System.out.println("taken = " + buffer.take());
        producer.join();
        System.out.println("remaining = " + buffer.size());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BoundedBufferConditionDemo.java
java BoundedBufferConditionDemo
~~~

Stdout khi không có interrupt ngoài dự kiến:

~~~text
taken = 10
taken = 20
remaining = 0
~~~

**Vì sao kết quả xác định?**

- Producer chỉ ghi các phần tử 10 rồi 20 theo thứ tự trong cùng một thread.
- Buffer dùng FIFO `ArrayDeque` nên consumer sẽ nhận 10 trước 20.
- Capacity bằng 1 nên khi producer muốn ghi 20 trong lúc vẫn có 10, nó đi vào `while (queue.size() == capacity)` để chờ.
- Consumer lấy 10 sẽ `notFull.signal()`; producer reacquire lock, kiểm tra lại còn chỗ trống và thêm 20. Consumer có thể phải chờ `notEmpty` trước đó hoặc sau đó, nhưng không ảnh hưởng thứ tự dữ liệu.
- `size()` và mọi đường đọc/ghi `queue` đều dùng cùng lock; `join()` khiến phép đọc cuối chạy sau khi producer hoàn tất.
- Không có dòng in trong producer, do đó thứ tự scheduler giữa các thread không làm thay đổi thứ tự stdout của main.

**Không có bảo đảm điều phối công bằng tuyệt đối:** `notEmpty.signal()` đánh thức một waiter, nhưng không cam kết waiter đó được CPU chạy ngay hoặc nhận phần tử trước mọi thread khác. Với nhiều producer/consumer, các `while` bảo vệ invariant khi nhiều thread tranh chấp.

### 6.1. Vì sao không dùng if trong buffer?

Nếu consumer chỉ kiểm tra `if (queue.isEmpty()) notEmpty.await();`, rồi không kiểm tra lại, nó có thể đi tới `removeFirst()` khi queue đã trống do waiter khác tiêu thụ. Với producer, `if (queue.size() == capacity) notFull.await()` có thể cho phép vượt capacity sau khi một producer khác thêm phần tử trước khi nó reacquire. Dùng `while` ở **cả hai đầu**.

### 6.2. Tránh signal quá sớm hoặc ngoài lock

Trạng thái cần được thay đổi trong khi giữ lock, rồi signal các waiter tương ứng trước khi release. Với `Condition` của `ReentrantLock`, việc gọi `signal()` ngoài lock không chỉ sai về mặt logic mà còn có thể ném `IllegalMonitorStateException`.

Cần chú ý một ứng dụng thực tế sẽ phải thiết kế thêm **đóng buffer**, xử lý producer/consumer bị hủy, khả năng shutdown và báo lỗi cho waiter đang chờ vô hạn. Nếu chỉ cần hàng đợi chuẩn, `ArrayBlockingQueue` hoặc `LinkedBlockingQueue` có thể giảm code tự quản lý condition và lỗi phối hợp.

## 7. Timeout: thức dậy do hết hạn vẫn cần kiểm tra invariant

Một overload như `Condition.awaitNanos(long nanosTimeout)` trả về số nanosecond còn lại theo ước tính. Trong vòng lặp, phải **cập nhật ngân sách còn lại**, không chờ lại toàn bộ timeout ở mỗi lần thức dậy.

Mẫu đúng:

~~~java
// Mẫu Java, không phải chương trình độc lập.
long remaining = unit.toNanos(timeout);
lock.lockInterruptibly();
try {
    while (!ready) {
        if (remaining <= 0L) {
            return false;
        }
        remaining = readyChanged.awaitNanos(remaining);
    }
    return true;
} finally {
    lock.unlock();
}
~~~

Lý do:

1. Mỗi lần `awaitNanos` có thể trở về bởi signal/spurious wakeup, nhưng `ready` vẫn false.
2. Nếu lặp lại một timeout nguyên vẹn sau mỗi wakeup, tổng thời gian đợi có thể vượt nhiều lần ngân sách ban đầu.
3. Trả về `false` khi ngân sách đã hết **và predicate vẫn false**; nếu predicate đã true, trả `true` dù vừa trải qua một lần chờ dài.
4. Thời điểm lấy được lock cũng có thể tốn thời gian. Nếu yêu cầu deadline **tính từ khi bắt đầu gọi**, phải đưa cả thời gian lấy lock vào tính toán, chẳng hạn dùng `tryLock(timeout, unit)` và deadline thống nhất.
5. Timeout không tự thay đổi state nghiệp vụ hoặc hủy một producer đang hoạt động. Caller phải quyết định xử lý timeout rõ ràng.

### Ví dụ 4 — timeout và predicate đã đúng (Java 17+)

Tạo tệp `TimedConditionDemo.java`:

~~~java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TimedConditionDemo {
    static final class Gate {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition changed = lock.newCondition();
        private boolean ready;

        boolean awaitReady(long timeout, TimeUnit unit)
                throws InterruptedException {
            long remaining = unit.toNanos(timeout);
            lock.lockInterruptibly();
            try {
                while (!ready) {
                    if (remaining <= 0L) {
                        return false;
                    }
                    remaining = changed.awaitNanos(remaining);
                }
                return true;
            } finally {
                lock.unlock();
            }
        }

        void open() {
            lock.lock();
            try {
                ready = true;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Gate gate = new Gate();

        System.out.println(
            "before = " + gate.awaitReady(0, TimeUnit.NANOSECONDS)
        );

        gate.open();

        System.out.println(
            "after = " + gate.awaitReady(0, TimeUnit.NANOSECONDS)
        );
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac TimedConditionDemo.java
java TimedConditionDemo
~~~

Stdout chính xác:

~~~text
before = false
after = true
~~~

Giới hạn 0 nanosecond làm lần chờ đầu trả `false` ngay vì predicate chưa đúng. Sau `open()`, predicate là true nên lần gọi thứ hai trả `true` ngay, **không cần chờ hoặc dựa vào một signal đã phát trong quá khứ**. Ví dụ minh họa đúng nghĩa kiểm tra trạng thái; nó không được dùng để ước tính thời gian chờ của các thread cạnh tranh.

## 8. Interrupt: không được gộp tất cả trường hợp thức dậy làm một

### 8.1. `InterruptedException` là kênh riêng

`Object.wait()` và `Condition.await()` thông thường có thể ném `InterruptedException` nếu thread bị interrupt. Đây **không phải** một lần trả về thành công từ `await`. Caller phải xử lý theo hợp đồng: truyền exception lên, hủy task hoặc trả trạng thái thất bại được thiết kế rõ ràng.

Khi thread bị interrupt trong lời chờ ném `InterruptedException`, cờ interrupt thường bị xóa trong quá trình ném. Nếu không thể truyền exception lên, một mẫu phổ biến là **khôi phục** cờ bằng `Thread.currentThread().interrupt()` trước khi kết thúc tác vụ.

~~~java
// Mẫu xử lý trong Runnable; không phải chương trình độc lập.
try {
    gate.awaitOpen();
    doUsefulWork();
} catch (InterruptedException error) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Không được bắt `InterruptedException` rồi tiếp tục làm như predicate đã đúng. Đồng thời, không mặc định việc interrupt một thread đang chờ sẽ làm mọi tài nguyên và side effect của nó tự động được hủy.

### 8.2. `awaitUninterruptibly` và vòng chờ

`Condition.awaitUninterruptibly()` đợi mà không ném `InterruptedException` theo cùng cách `await()`. Nhưng nó **vẫn cho phép spurious wakeup**, vì vậy cũng phải được đặt trong `while (!predicate)`. Nó không thích hợp nếu ứng dụng cần chấp hành cancellation/interrupt nhanh; thường ưu tiên `await()` và xử lý ngắt hợp lý.

### 8.3. Object.wait với timeout

`Object.wait(long timeoutMillis)` và `wait(long millis, int nanos)` cho phép chờ có thời hạn. Quy tắc `while` và tính lại deadline vẫn giữ nguyên. Cẩn thận ý nghĩa `wait(0)`: với overload `wait(long)`, số 0 nghĩa là **chờ vô thời hạn**, không phải trả về ngay. Điều đó khác với một số API timeout 0 như mẫu `awaitReady` ở trên.

## 9. Visibility và happens-before: vì sao khóa phải nhất quán?

Java Memory Model quy định việc **nhả monitor** của một object và **lấy monitor đó** sau này tạo quan hệ synchronizes-with/happens-before thích hợp. Tương tự, `Lock` và `Condition` theo hợp đồng của chúng cho phép các thao tác cập nhật state được công bố khi thread khác reacquire cùng lock.

Cơ chế đúng thường theo thứ tự:

~~~text
Producer:
  lock
  cập nhật state (predicate có thể chuyển false -> true)
  notify/signal đúng condition
  unlock

Consumer:
  lock
  while (!predicate) wait/await (tạm unlock)
  reacquire lock
  kiểm tra predicate lần nữa
  đọc/cập nhật state
  unlock
~~~

Tín hiệu không phải “phép thuật làm mọi biến nhìn thấy mới”. **Việc truy cập state dưới cùng khóa** mới cung cấp đồng bộ hóa cần thiết cho các đường đọc/ghi. Nếu producer thay đổi một biến thông thường **ngoài** giao thức khóa còn consumer đọc nó dưới khóa, bảo đảm có thể không đủ.

### 9.1. Tại sao check rồi wait ở ngoài lock là lỗi?

~~~java
// Sai nếu ready bị cập nhật ở thread khác và không có giao thức chung.
if (!ready) {
    synchronized (lock) {
        lock.wait();
    }
}
~~~

Có một khoảng trống giữa kiểm tra `ready` và việc wait thật sự. Thread khác có thể đặt `ready = true` và notify trong khoảng đó. Notification không được lưu cho tương lai, nên waiter có thể đi vào wait **sau khi tín hiệu đã mất**, rồi chờ vô hạn dù trạng thái sẵn sàng.

Cách đúng là **kiểm tra predicate và vào wait trong cùng critical section**. `wait()` nhả monitor nguyên tử theo cơ chế wait-set, cho phép thread thay đổi state lấy khóa và báo hiệu đúng trình tự.

### 9.2. Không dùng Thread.sleep để “đợi tín hiệu”

`Thread.sleep(...)` không tạm nhả monitor đang giữ, không tự tạo happens-before và không phản ứng với `notify`/`signal`. Nếu giữ lock rồi sleep để chờ thread khác làm cho predicate đúng, bạn có thể ngăn chính thread kia lấy khóa cần thiết để thay đổi trạng thái.

`Thread.yield()` và `Thread.onSpinWait()` cũng không thay thế được condition waiting hoặc giao thức đồng bộ; chúng chỉ ảnh hưởng gợi ý lập lịch/tối ưu.

## 10. Lỗi phổ biến và cách tránh

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| `if (!ready) wait()` | Thức dậy nhưng ready vẫn false; tiếp tục sai | `while (!ready) wait()` |
| `if (queue.isEmpty()) await()` | Nhiều consumer tranh chấp, một consumer có thể đọc queue rỗng | Kiểm tra `while` dưới lock |
| Gọi `notifyAll()` rồi giả định mỗi waiter có một item | Một tín hiệu không tương ứng số permit/item | Lưu state và kiểm tra lại predicate |
| Chỉ đọc `ready` ngoài `synchronized` | Data race, có thể bỏ lỡ notification | Cùng monitor cho check, wait và update |
| Gọi `wait()` ngoài monitor | `IllegalMonitorStateException` | Đồng bộ hóa trên đúng object receiver |
| Gọi `Condition.signal()` ngoài associated lock | Không đáp ứng hợp đồng, có thể lỗi runtime | Signal khi đang giữ lock tương ứng |
| Dùng `Thread.sleep` thay cho wait | Không nhả monitor hoặc chờ signal | Dùng monitor/Condition/queue |
| Không xử lý `InterruptedException` | Mất cancellation hoặc tiếp tục sai | Propagate hoặc restore flag và thoát |
| Chờ timeout lặp lại bằng toàn bộ thời lượng ban đầu | Tổng thời gian có thể vượt deadline | Tính lại ngân sách còn lại |
| Lặp `await()` nhưng predicate chỉ kiểm tra ở nơi khác | Không bảo đảm predicate đúng lúc tiếp tục | Kiểm tra ở chính vòng `while` cùng lock |
| Cố thử tạo spurious wakeup bằng cách gọi `notify` | Lẫn tín hiệu thật với wakeup giả | Dùng notification không phù hợp để demo `if` sai; hiểu hợp đồng wakeup giả riêng |
| Tin `signal()` trao ngay lock cho waiter | Dự đoán sai thứ tự | Waiter phải reacquire lock |
| Dùng `notify()` với nhiều kiểu predicate trên cùng wait-set | Có thể đánh thức waiter không phù hợp | `notifyAll` khi cần hoặc tách Condition |
| Giữ thêm lock B khi `lockA.wait()` | Vẫn giữ B, có thể gây vòng chờ | Tránh chờ khi giữ nhiều khóa, thiết kế lại thứ tự |
| Giữ lock khi gọi I/O hoặc callback không kiểm soát | Contention/deadlock/latency | Thu hẹp critical section |
| Dùng `awaitUninterruptibly` mà quên kiểm tra predicate | Spurious wakeup vẫn có thể xảy ra | Vẫn dùng `while` |

## 11. Từ wait/notify sang primitive cấp cao

Trong code ứng dụng, không phải bài toán nào cũng cần tự viết vòng `wait/notify`. Các primitive `java.util.concurrent` có thể mã hóa giao thức tương ứng rõ ràng hơn:

| Mục đích | Lựa chọn thường phù hợp | Lý do |
| --- | --- | --- |
| Chờ item từ producer | `BlockingQueue.take()` | Queue tự quản lý trạng thái rỗng/có phần tử |
| Hạn chế số task được vào tài nguyên | `Semaphore.acquire()` | Permit được lưu/cấp theo hợp đồng |
| Đợi một số tác vụ hoàn tất | `CountDownLatch.await()` | Count là trạng thái phối hợp rõ ràng |
| Các thread cùng đạt điểm đồng bộ lặp lại | `CyclicBarrier` hoặc `Phaser` | Giao thức pha thay vì tự tạo flags |
| Đợi kết quả task | `Future.get()` / `CompletableFuture` | Có cả kết quả/exception và hoàn tất |
| Chờ một predicate phức tạp trong object đồng bộ | `Condition.await()` | Vẫn phải dùng vòng predicate dưới lock |
| Bài tập cơ chế monitor cơ bản | `Object.wait/notifyAll` | Hiểu rõ nguyên tắc, nhưng nhiều chi tiết dễ sai |

Ngay cả khi dùng primitive cấp cao, vẫn cần hiểu cancellation, timeout, liveness và invariant. Ví dụ `BlockingQueue` giải quyết chờ phần tử, nhưng không tự định nghĩa quy tắc “producer đã đóng và sẽ không còn item nào nữa”; phần đóng/kết thúc vẫn cần giao thức riêng nếu ứng dụng yêu cầu.

## 12. Kiểm thử correctness mà không phụ thuộc wakeup giả ngẫu nhiên

Vì spurious wakeup có thể không xuất hiện trong một số JVM/lần chạy, một test chỉ lặp `wait()` nhiều lần rồi chờ wakeup giả không phải phép chứng minh đáng tin.

### 12.1. Các tình huống nên kiểm tra

1. **Tín hiệu đến trước waiter:** state đã đúng, waiter vào sau phải **không chờ**. Ví dụ Gate ở phần trên xử lý điều này.
2. **Tín hiệu không làm predicate đúng:** waiter phải **tiếp tục chờ**; ví dụ `WrongIfWaitDemo` cho thấy code dùng if sai bằng một notify có thật.
3. **Nhiều waiter, ít tài nguyên:** khi có một item và nhiều consumer được đánh thức, chỉ consumer lấy được item mới đi tiếp; những thread khác quay lại chờ.
4. **Predicate đổi lại trước reacquire:** signal xảy ra, một thread khác tiêu thụ tài nguyên trước waiter, và waiter phải kiểm tra lại.
5. **Interrupt khi đang chờ:** task truyền lỗi/ngắt đúng hợp đồng, không xử lý nghiệp vụ khi predicate false.
6. **Timeout:** task trả kết quả timeout đúng khi hết ngân sách, không làm kéo dài toàn bộ deadline vì nhiều wakeup.
7. **Shutdown/cancellation:** mọi waiter biết khi nào nên kết thúc thay vì chờ mãi trên predicate không thể thành true.
8. **Bất biến dữ liệu:** trong buffer, size luôn trong khoảng 0 đến capacity; không lấy phần tử khi queue rỗng hoặc thêm khi đầy.

### 12.2. Kỹ thuật kiểm thử

- Dùng `CountDownLatch` hoặc `CyclicBarrier` để sắp đặt các interleaving cần kiểm tra, thay vì `Thread.sleep(...)` như một cách phỏng đoán.
- Mọi test có khả năng block cần timeout và thu thập thread dump khi lỗi, tránh để test suite treo vô hạn.
- Dùng future/exception channel để không âm thầm bỏ qua lỗi của worker; `Thread.join()` chỉ cho biết thread đã kết thúc, không có nghĩa nghiệp vụ đã thành công.
- Chạy stress test với nhiều producer/consumer và so sánh số phần tử tạo–tiêu thụ; kết quả test thành công chỉ là bằng chứng hỗ trợ, không thay thế lập luận về giao thức.
- Khi debug thread đang `WAITING`, xem nó đang `Object.wait`, `Condition.await` hay một primitive khác; nhiều thread đang WAITING là bình thường nếu chúng thực sự chờ tài nguyên chưa sẵn sàng.

### 12.3. Không biến lỗi test thành kết luận sai về spurious wakeup

Nếu test minh họa `if` sai vì ta cố ý gọi `notifyAll` khi state vẫn false, hãy ghi rõ đây là **tín hiệu thật không phù hợp**. Nó chứng minh vòng `while` là cần thiết nhưng **không phải** bằng chứng rằng JVM đã tạo spurious wakeup.

Nếu test timeout đôi lúc thất bại vì máy quá tải, điều tra deadline, scheduling và điều kiện chờ; không tự động kết luận nguyên nhân là spurious wakeup.

## 13. Checklist review trước khi đưa vào sản phẩm

- Mọi waiter có một **predicate rõ ràng** gắn với trạng thái dùng chung chưa?
- Việc kiểm tra predicate và thực hiện `wait/await` có nằm trong **cùng lock/monitor** không?
- Mọi lời chờ predicate được đặt trong **`while (!predicate)`** chưa, kể cả khi chỉ có một waiter?
- Sau khi thức dậy, thread có reacquire lock và **kiểm tra lại invariant** trước khi sử dụng dữ liệu không?
- Writer có đổi state dưới lock rồi mới notify/signal phù hợp không?
- Có khả năng signal đến trước waiter? State có đủ để waiter đến sau không bỏ lỡ sự kiện không?
- Có nhiều waiter/các loại predicate khác nhau? `notify` hay `signal` đơn lẻ có nguy cơ chọn nhầm nhóm không?
- Có cơ chế timeout, interrupt, shutdown để tránh chờ vô hạn khi điều kiện không còn khả thi không?
- Có đường nào giữ nhiều lock rồi wait/await gây deadlock không?
- Có test cho thông báo không phù hợp, tranh chấp consumer, timeout và interrupt không?
- Nếu dùng `Condition`, code có release đúng lock trong `finally` và tránh làm I/O chặn dài trong critical section không?
- Có thể thay cơ chế tự viết bằng `BlockingQueue`, `Semaphore` hoặc `CountDownLatch` để đơn giản hóa không?

### Câu hỏi tự kiểm tra

**1.** Thread vừa trở về từ `Object.wait()` sau khi được gọi `notifyAll()`. Có được suy ra ngay `queue.isEmpty() == false` không?

Không. Một waiter khác có thể đã tiêu thụ item trước khi thread này reacquire monitor; cũng có thể notification không tương ứng với predicate đó. Phải kiểm tra lại dưới khóa.

**2.** Spurious wakeup có phải được tạo bởi lời gọi `notify()` “không đúng lúc” không?

Không. `notify()` là **tín hiệu thật**. Spurious wakeup là khả năng một wait/await trở về không có sự kiện thông báo/timeout/interrupt cần thiết. Cả hai đều cho thấy phải dùng `while`.

**3.** Nếu producer notify trước khi consumer gọi wait thì notification có được lưu lại không?

Không. Monitor notification không lưu permit cho waiter đến sau. Predicate phải được giữ trong state bảo vệ bởi cùng monitor; consumer kiểm tra state trước khi chờ.

**4.** Sau `condition.signal()`, waiter đã sở hữu `ReentrantLock` ngay chưa?

Chưa. Nó phải reacquire lock sau khi thread đang signal nhả lock; predicate có thể thay đổi trong khoảng thời gian đó.

**5.** Timeout của `awaitNanos` trở về nhưng predicate vẫn false. Có được thực hiện công việc như khi predicate đã true không?

Không. Phải kiểm tra predicate và ngân sách chờ; nếu hết thời gian mà điều kiện chưa đạt, trả kết quả timeout.

**6.** Gọi `Object.wait()` có nhả mọi lock mà thread đang giữ không?

Không. Nó nhả monitor của object receiver dùng để wait; các lock khác đang giữ không được tự động nhả.

**7.** Vì sao vòng `while` phải nằm **bên trong** khóa chứ không phải kiểm tra một lần ở ngoài rồi mới gọi wait?

Để việc kiểm tra predicate và chuyển vào wait được phối hợp nguyên tử với bên thay đổi state, tránh khoảng trống “check xong, notification xảy ra, rồi mới đi ngủ” gây lost notification.

**Ghi nhớ:** Thức dậy **không phải** là điều kiện đã đúng. Dù là spurious wakeup, signal không phù hợp, tranh chấp giữa nhiều waiter hay timeout, chương trình đúng phải **giữ và kiểm tra predicate cùng khóa**, lặp `while` khi chưa thỏa, và chỉ sử dụng trạng thái sau khi invariant được xác nhận.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-coordination-004">Producer–consumer với hàng đợi giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Producer–consumer với hàng đợi giới hạn trong Java Concurrency

> **Bối cảnh:** Phối hợp luồng (*thread coordination*) trong Java SE, đặc biệt là giao tiếp giữa producer và consumer qua **bounded blocking queue**. Bài trình bày `BlockingQueue`, `ArrayBlockingQueue`, `LinkedBlockingQueue`, backpressure, xử lý lỗi/dừng luồng và cơ chế triển khai bằng `Lock` + `Condition`. Phạm vi là **concurrency Java nói chung**; các API và cơ chế đặc thù của Spring thuộc một chương khác.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích mô hình **producer–consumer** và tại sao cần một **hàng đợi có giới hạn sức chứa** thay vì cho producer đẩy dữ liệu không kiểm soát.
- Phân biệt ba trạng thái **queue rỗng / có dữ liệu / đầy** cùng những hành vi chờ tương ứng của producer và consumer.
- Chọn đúng API `put`, `take`, `offer`, `poll`, timeout và biết tại sao không dùng `sleep` để chờ dữ liệu.
- Giải thích **backpressure** và các chính sách xử lý khi hệ thống tạo dữ liệu nhanh hơn khả năng tiêu thụ.
- Dùng `ArrayBlockingQueue` để xây một ví dụ Java 17 chạy được, có capacity, tín hiệu kết thúc, thu thập kết quả và đóng `ExecutorService`.
- Phân tích **memory visibility**, thứ tự FIFO, nhiều producer/consumer, điều kiện “đã xử lý xong”, nguy cơ deadlock/starvation và giới hạn độ bền dữ liệu.
- Hiểu hàng đợi bounded có thể được xây bằng **một `ReentrantLock` và hai `Condition`**, nhưng biết khi nào nên ưu tiên implementation chuẩn.
- Viết test và số liệu giám sát để phân biệt **tắc nghẽn bình thường có backpressure** với lỗi concurrency.

**Nguyên tắc trung tâm:** bounded queue là ranh giới đồng bộ giữa **tốc độ sản xuất** và **tốc độ xử lý**. Producer có thể phải đợi khi queue đầy, consumer có thể phải đợi khi queue rỗng. Việc đợi và truyền dữ liệu cần được điều phối bằng primitive có bảo đảm concurrency thực sự, không bằng đoán thời gian.

## 2. Mô hình producer–consumer

Producer tạo ra một **item** hoặc **work item**; consumer lấy item và xử lý. Hai vai trò có thể nằm trên những thread khác nhau và chạy với tốc độ khác nhau.

```text
   Producer P1 --\
                  \
   Producer P2 ----> [ BOUNDED QUEUE: capacity = N ] ---> Consumer C1
                  /                 |                   ---> Consumer C2
   Producer P3 --/             0 <= size <= N

Producer chờ khi đầy:            size == N
Consumer chờ khi rỗng:           size == 0
Khi có item / có chỗ:            luồng liên quan có thể tiếp tục
```

Hai trường hợp đặc biệt:

- **Queue rỗng:** consumer gọi một operation chờ như `take()` sẽ ngủ cho tới khi có item, thread bị interrupt hoặc xảy ra diễn biến phù hợp với contract của primitive.
- **Queue đầy:** producer gọi `put()` sẽ chờ cho tới khi một consumer lấy bớt item, hoặc producer bị interrupt.

Một hàng đợi giới hạn có khả năng chứa không quá **N phần tử đang ở trong queue**. Điều đó **không giới hạn tổng số công việc đã được lấy ra và đang xử lý**, cũng không ngăn consumer tiếp tục gọi một downstream service chậm. Cần tính cả **in-flight work** khi giới hạn tài nguyên toàn pipeline.

### Ví dụ thực tế

- Nhận log event rồi có worker ghi ra đĩa.
- Nhận ảnh rồi có worker biến đổi/kích thước/encode.
- Đọc dữ liệu từ một file lớn, chuyển các batch nhỏ cho nhiều CPU worker xử lý.
- Nhận command nội bộ JVM rồi một owner duy nhất cập nhật state.
- Đọc thông điệp từ nguồn ngoài rồi đẩy vào pipeline trong-memory có giới hạn số lượng chờ.

Đây là **mô hình nội bộ Java process**. Khi dữ liệu không được mất dù process crash, một `BlockingQueue` trong RAM **không thay thế** broker/persistent log/transaction store.

## 3. Vì sao “bounded” là thuộc tính quan trọng?

Giả sử producer trung bình tạo **120 item/giây**, consumer chỉ xử lý **100 item/giây**. Nếu cứ tiếp tục trong một phút và không có cơ chế kiểm soát, backlog lý tưởng hóa tăng khoảng:

```text
(120 - 100) item/s × 60 s = 1.200 item
```

Đây chỉ là phép ước lượng khi tốc độ ổn định, chưa tính burst, variance và in-flight. Nếu queue không giới hạn thực tế, backlog có thể tiếp tục ăn RAM cho đến khi GC tăng mạnh hoặc ứng dụng bị `OutOfMemoryError`.

Với queue capacity 200:

- **Số item đang xếp hàng** tối đa là 200, theo invariant của queue.
- Khi đầy, `put()` buộc producer **đợi consumer lấy bớt**, nên áp lực lan ngược đến nguồn sản xuất.
- Hệ thống có thể ổn định về lượng dữ liệu **đang giữ trong queue**, nhưng không có phép màu biến consumer 100 item/s thành 120 item/s.
- Khi source không cho phép producer block (ví dụ luồng network/event callback có deadline), chương trình cần chính sách `offer`, timeout, reject, drop, retry hoặc throttle rõ ràng.

**Backpressure** là khả năng tầng tiêu thụ phát tín hiệu “chưa nhận thêm được” cho tầng sản xuất. Tín hiệu có thể là **blocking**, boolean false, timeout, exception hoặc cơ chế điều tiết của tầng gọi. Bounded queue là một cách thiết lập backpressure nhưng cách xử lý phản ứng phụ thuộc application contract.

### Capacity không phải throughput

Tăng capacity có thể hấp thụ burst ngắn, nhưng không làm tăng tốc xử lý trung bình của consumer. Capacity quá lớn cũng không miễn phí: nó giữ nhiều object, làm tăng thời gian chờ của item và che khuất downstream bottleneck.

Chọn capacity dựa trên kích thước item, mức burst, SLA latency và throughput quan sát. Không dùng `Integer.MAX_VALUE` làm “bounded” nếu ngân sách RAM hữu hạn.

## 4. Java có sẵn BlockingQueue

`java.util.concurrent.BlockingQueue<E>` là interface cho queue phối hợp thread. Các API được thiết kế để làm việc khi chưa thể thêm/lấy item ngay.

| Hoạt động | Ném exception nếu không thực hiện ngay | Trả kết quả tức thì | Chờ vô thời hạn tới khi khả dụng | Chờ trong thời hạn |
|---|---|---|---|---|
| Thêm | `add(e)` | `offer(e)` → `false` khi đầy | `put(e)` | `offer(e, timeout, unit)` → boolean |
| Lấy và xóa | `remove()` | `poll()` → `null` khi rỗng | `take()` | `poll(timeout, unit)` → item hoặc `null` |
| Xem đầu, không xóa | `element()` | `peek()` | Không có phương thức blocking tương ứng | Không có phương thức timed tương ứng |

**Điểm nhớ nhanh:**

- `put(e)` chờ nếu đầy, có thể ném `InterruptedException`.
- `take()` chờ nếu rỗng, có thể ném `InterruptedException`.
- `offer(e)` không chờ chỗ trống: khi queue đầy, thường trả `false`.
- `poll()` không chờ item mới: khi rỗng, trả `null`.
- `offer(e, time, unit)` và `poll(time, unit)` giúp **ràng buộc thời gian chờ** và xử lý trường hợp tài nguyên không sẵn sàng.
- `add(e)` có thể ném `IllegalStateException` khi queue bị giới hạn và không còn sức chứa; `remove()` có thể ném `NoSuchElementException` nếu rỗng.
- Các implementation `BlockingQueue` chuẩn **không cho phép null item**, một phần vì `poll` dùng null làm tín hiệu “không có item”.

Đừng lấy kết quả `peek()` hay `size()` rồi tin queue sẽ giữ nguyên cho đến thao tác tiếp theo: thread khác có thể thay đổi giữa hai lời gọi. Nếu mục tiêu là “chờ rồi lấy”, gọi **`take()`** thay vì check-then-act bằng `size()`.

## 5. Chọn implementation phù hợp

### `ArrayBlockingQueue<E>`

```java
BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);
```

Đây là **đoạn code trong method** có import `java.util.concurrent.BlockingQueue` và `java.util.concurrent.ArrayBlockingQueue`.

- Capacity cố định, **bắt buộc > 0**.
- Hàng đợi FIFO theo thứ tự enqueue của các item.
- Cài đặt sử dụng mảng có kích thước cố định và cơ chế lock/condition nội bộ.
- Có constructor `new ArrayBlockingQueue<>(capacity, fair)` để chọn chính sách fairness đối với những thread chờ truy cập; fairness có thể làm giảm throughput và **không bảo đảm** thời điểm một job hoàn thành.
- Phù hợp khi biết rõ số item tối đa cần đệm và muốn kiểm soát bộ nhớ tương đối dễ.

### `LinkedBlockingQueue<E>`

```java
BlockingQueue<String> queue = new LinkedBlockingQueue<>(100);
```

Đây là đoạn code minh họa với import `java.util.concurrent.LinkedBlockingQueue`. Implementation sử dụng các node liên kết và có thể đặt capacity trong constructor. Nó cũng hỗ trợ FIFO theo thứ tự enqueue.

**Cẩn thận:** `new LinkedBlockingQueue<>()` không phải bounded theo ngân sách thực tế của ứng dụng; nó có capacity mặc định `Integer.MAX_VALUE`. Đây là nguồn lỗi thường gặp: hệ thống nhìn như dùng queue thread-safe, nhưng khi producer nhanh hơn consumer, backlog vẫn có thể tăng đến mức kiệt bộ nhớ. **Luôn chỉ định capacity nếu boundedness là yêu cầu.**

### Các implementation có semantics khác

- `SynchronousQueue<E>` có **không có chỗ đệm item bên trong**: put/take là một cơ chế handoff trực tiếp giữa producer và consumer. Nó không giống `ArrayBlockingQueue<>(1)`.
- `PriorityBlockingQueue<E>` lấy theo **priority ordering**, không phải FIFO bounded queue; nó không cung cấp một hard capacity tương tự `ArrayBlockingQueue`.
- `DelayQueue<E>` dùng thời điểm đủ điều kiện của phần tử; không phải bounded FIFO thông thường.

Mỗi loại queue tạo một contract khác. Không đổi implementation chỉ vì tên đều kết thúc bằng “BlockingQueue”.

## 6. Ví dụ Java 17 chạy được: giới hạn capacity và chuyển việc

Chương trình có hai phần:

1. Minh họa capacity = 2 bằng `offer` và `take`: thêm đủ hai item, thử thêm item thứ ba khi đầy phải nhận `false`; lấy một item rồi thêm tiếp thành công.
2. Chạy **một producer và một consumer** dùng `ExecutorService`, truyền ba giá trị 5, 7, 9 qua bounded queue và gửi một **poison pill** để consumer biết khi nào dừng. Consumer trả `Result` cho main qua `Future.get()`.

Tạo file **`BoundedQueueDemo.java`**:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BoundedQueueDemo {
    private static final int STOP = -1;
    record Result(int count, long sum) { }

    public static void main(String[] args) throws Exception {
        BlockingQueue<Integer> bounded = new ArrayBlockingQueue<>(2);
        System.out.println("offer10=" + bounded.offer(10));
        System.out.println("offer20=" + bounded.offer(20));
        System.out.println("offer30WhenFull=" + bounded.offer(30));
        System.out.println("firstTake=" + bounded.take());
        System.out.println("offer30AfterTake=" + bounded.offer(30));
        System.out.println("remaining=" + bounded.take() + "," + bounded.take());

        BlockingQueue<Integer> inbox = new ArrayBlockingQueue<>(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Result> consumer = pool.submit(() -> {
                int count = 0;
                long sum = 0;
                while (true) {
                    int value = inbox.take();
                    if (value == STOP) break;
                    count++;
                    sum += value;
                }
                return new Result(count, sum);
            });
            Future<?> producer = pool.submit(() -> {
                inbox.put(5);
                inbox.put(7);
                inbox.put(9);
                inbox.put(STOP);
                return null;
            });

            producer.get();
            Result result = consumer.get();
            System.out.println("consumedCount=" + result.count());
            System.out.println("consumedSum=" + result.sum());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac --release 17 BoundedQueueDemo.java
java BoundedQueueDemo
```

**Stdout đã biên dịch và chạy xác minh:**

```text
offer10=true
offer20=true
offer30WhenFull=false
firstTake=10
offer30AfterTake=true
remaining=20,30
consumedCount=3
consumedSum=21
```

### Giải thích phần capacity

- `new ArrayBlockingQueue<>(2)` khởi tạo queue rỗng, chứa tối đa hai phần tử.
- `offer(10)` và `offer(20)` thành công; queue đầy.
- `offer(30)` trả `false` **ngay**, không ngồi đợi consumer.
- `take()` lấy đầu FIFO là 10, tạo một chỗ trống.
- `offer(30)` lần sau thành công; hai item còn lại lần lượt là 20 và 30.
- Phần này chạy trong main để tạo **kết quả xác định**, không phụ thuộc vào scheduling.

### Giải thích phần producer–consumer

- `inbox` có capacity 2, nên producer có thể bị block ở `put(9)` hoặc `put(STOP)` nếu consumer chưa lấy kịp.
- `inbox.put(...)` vừa đảm nhiệm việc thêm item, vừa điều phối khi queue đầy; không cần code tự gọi `wait/notify`.
- Consumer gọi `inbox.take()` trong vòng lặp; khi queue rỗng, nó chờ producer.
- Giá trị `STOP=-1` được dành riêng làm tín hiệu kết thúc. Ba item hợp lệ là 5, 7, 9, nên consumer đếm chính xác 3 và tính tổng 21.
- `producer.get()` chờ producer hoàn thành việc enqueue cả ba item và STOP; `consumer.get()` chờ consumer thật sự xử lý xong và trả kết quả. Chỉ queue trống **không phải bằng chứng** mọi item đã xử lý xong.
- Pool có **hai worker threads**, đủ cho producer và consumer tiến triển ngay cả khi một bên đang block.
- `finally { pool.shutdownNow(); }` trong demo là đường cleanup sau các lần `get()`; tại đường chạy bình thường cả hai task đã hoàn tất. Ở ứng dụng thật, cần chiến lược graceful shutdown và timeout/cancellation riêng, không dùng shutdownNow như cách “đợi cho xong”.

**Lưu ý về STOP:** nếu dữ liệu hợp lệ có thể là -1, giao thức này **sai** vì consumer sẽ hiểu nhầm dữ liệu thành lệnh dừng. Khi thiết kế nghiêm túc, nên dùng kiểu thông điệp rõ ràng, ví dụ sealed interface `Message` với `Data` và `Stop`, hoặc một protocol đóng riêng. Với nhiều consumer, cần chính sách phát tín hiệu đóng tới từng consumer hoặc dùng cơ chế khác để họ không còn block ở `take()`.

## 7. Vì sao queue thread-safe mà payload vẫn có thể không an toàn?

`BlockingQueue` bảo vệ **cấu trúc queue và thao tác enqueue/dequeue**. Java concurrency API cũng quy định **memory consistency effects**: những hành động trong thread **trước khi đặt một object vào queue** *happen-before* các hành động trong thread khác **sau khi truy cập hoặc lấy object đó từ queue**.

Ví dụ:

```java
// Minh họa: queue là BlockingQueue<Message> và Message là immutable.
Message message = new Message(42, "ready");
queue.put(message);
// Thread khác: Message received = queue.take();
// Các trạng thái đã chuẩn bị trước put được công bố theo contract.
```

Đây là đoạn code minh họa; `Message` và `queue` được khai báo ở class ngoài, không phải chương trình độc lập.

**Nhưng queue không làm object mutable trở thành thread-safe mãi mãi.** Nếu producer đặt một tham chiếu `ArrayList` vào queue rồi vẫn tiếp tục `add` trên chính list đó trong khi consumer đang duyệt, hai bên có thể race trên **payload**, dù thao tác `put/take` đúng.

Các cách an toàn phổ biến:

1. **Immutable message:** xây dữ liệu đầy đủ rồi publish dưới dạng record/các trường immutable; nếu có collection, defensive copy và không chia sẻ phần tử mutable nguy hiểm.
2. **Ownership transfer:** sau khi enqueue mutable message, producer **ngừng sửa/đọc-mutate**; consumer trở thành owner cho đến khi bàn giao tiếp.
3. **Synchronized payload:** mọi lần đọc/ghi payload cùng dùng một synchronization protocol nếu bắt buộc chia sẻ để mutate.
4. **Snapshot/copy:** nếu producer cần tiếp tục sửa object gốc, đưa snapshot riêng vào queue.

`Future.get()` có thêm quan hệ memory consistency cho kết quả task, nhưng không thay thế queue publication khi consumer cần đọc dữ liệu trong lúc producer vẫn hoạt động.

## 8. Backpressure: chọn chính sách khi đầy

Có nhiều lựa chọn, tùy tính chất workload và SLO:

| Chính sách | Primitive thường dùng | Lợi ích | Rủi ro / điều cần định nghĩa |
|---|---|---|---|
| **Block producer** | `queue.put(item)` | Không bỏ item chỉ vì đầy; áp lực truyền ngược nguồn | Producer có thể bị treo lâu, giữ tài nguyên hoặc chiếm worker thread |
| **Fail fast / reject** | `queue.offer(item)` | Caller biết ngay queue đầy | Caller phải xử lý `false`; nếu bỏ qua là mất công việc |
| **Chờ có giới hạn** | `queue.offer(item, timeout, unit)` | Giới hạn thời gian producer có thể chờ | Khi timeout phải retry/drop/report theo contract; thời gian không đồng nghĩa đã enqueue |
| **Retry có điều kiện** | `offer` + bounded retries | Có thể vượt burst ngắn | Retry không giới hạn tạo vòng chờ hoặc tăng áp lực toàn hệ thống |
| **Drop có chủ đích** | Chỉ áp dụng cho dữ liệu được phép bỏ | Giữ latency, ngăn backlog | Mất dữ liệu; phải có metrics và nêu rõ loại item được bỏ |
| **Rate limiting / throttle upstream** | Cơ chế giới hạn ở producer/source | Ngăn backlog trước khi vào queue | Cần phối hợp nhiều tầng và phản hồi nguồn |
| **Scale consumer** | Tăng worker khi công việc có thể song song | Tăng khả năng tiêu thụ | Có thể làm downstream nghẽn hơn hoặc phá ordering/invariant |

**Không thể vừa cam kết “không mất item”, “producer không bao giờ chờ”, “RAM hữu hạn” và “consumer chậm vô hạn” mà không có thêm storage/khả năng xử lý**. Thiết kế phải chọn điểm đánh đổi hoặc đưa backlog ra tầng bền vững.

### Ví dụ timed offer

```java
boolean accepted = queue.offer(item, 250, TimeUnit.MILLISECONDS);
if (!accepted) {
    // Theo contract ứng dụng: reject, persist, ghi metric hoặc retry có giới hạn.
}
```

Đây là **đoạn code trong method** cần khai báo/xử lý `InterruptedException`, có import `java.util.concurrent.TimeUnit` và biến `queue`, `item` đã tồn tại. `250ms` chỉ là con số minh họa, không phải lời khuyên SLA cố định.

Một retry đúng cần giữ rõ **item đã được enqueue hay chưa**. Nếu producer không biết một side effect downstream đã xảy ra, retry có thể tạo bản sao logic. Tính exactly-once không được queue in-memory bảo đảm; cần idempotency key/transaction khi công việc mang tác động bên ngoài.

## 9. Chờ đợi đúng cách: đừng tự kiểm tra size rồi ngủ

Mẫu nên tránh:

```java
// Minh họa sai: polling và đoán thời gian.
while (queue.isEmpty()) {
    Thread.sleep(10);
}
Item item = queue.remove();
```

Đây là code minh họa chưa đầy đủ; `Item` và `queue` được khai báo ngoài đoạn. Vấn đề:

- `isEmpty()` chỉ là snapshot tại một thời điểm; giữa khi kiểm tra và `remove()` thread khác có thể lấy hết queue.
- `sleep` không chờ một event chính xác và có thể tạo độ trễ không cần thiết.
- Nhiều consumer polling làm tăng CPU/wakeup hoặc phải chọn trade-off giữa latency và thời gian ngủ.
- `Thread.sleep()` không tạo memory-ordering protocol cho những shared state ngoài queue.

Mẫu trực tiếp:

```java
Item item = queue.take();
process(item);
```

Đây cũng là đoạn minh họa trong method `throws InterruptedException`, với `queue` là `BlockingQueue<Item>`. `take()` thực hiện đúng việc chờ đến khi có item, lấy một item rồi trả về; không cần tự xây wait loop cho trường hợp chuẩn.

Tương tự, không nên làm:

```java
if (queue.remainingCapacity() > 0) {
    queue.add(item);
}
```

`remainingCapacity()` có thể đã thay đổi trước `add` nếu có producer khác. Nếu mục tiêu là “thử thêm nếu còn chỗ”, gọi **`offer(item)`** và kiểm tra boolean. Chỉ dùng `remainingCapacity` để quan sát/ước lượng, không để *reserve* slot.

## 10. Multiple producers và consumers: FIFO không đảm bảo completion order

Với `ArrayBlockingQueue` và `LinkedBlockingQueue`, item được lấy theo FIFO của **thứ tự enqueue thành công**. Nhưng cần tách nhiều thứ tự:

- **Thứ tự producer tạo item:** P1 có thể tạo A trước P2 tạo B.
- **Thứ tự enqueue:** P2 có thể được scheduler cho `put(B)` vào queue trước `put(A)`.
- **Thứ tự dequeue:** đối với FIFO queue, consumer lấy theo thứ tự item đã thực sự vào queue.
- **Thứ tự hoàn tất xử lý:** với nhiều consumer, C1 lấy A trước nhưng xử lý chậm; C2 lấy B sau nhưng hoàn tất trước.

```text
Queue FIFO:       A -> B -> C
Consumer C1:      take(A) ----------- done(A)
Consumer C2:           take(B) -- done(B)
Order take:       A, B
Order complete:   B, A
```

FIFO **không biến workload đa consumer thành thực thi tuần tự**. Nếu nghiệp vụ yêu cầu side effect theo thứ tự tuyệt đối, cần một owner xử lý tuần tự hoặc sequence/commit protocol phù hợp. Tăng số consumer chỉ an toàn khi các item được phép xử lý độc lập hoặc có synchronization cho invariant liên quan.

### Với nhiều producer, thứ tự dữ liệu không phải thứ tự thời gian tuyệt đối

Không nên suy rằng item từ các producer khác nhau sẽ có một global order giống timestamp do mỗi producer tự gán. Nếu cần tổng thứ tự nghiệp vụ, hãy xác định ai cấp sequence và ở giai đoạn nào (trước enqueue, tại enqueue, khi commit). Sequence có thể gắn vào message, nhưng thứ tự **completion** vẫn cần cách phối hợp khác.

### Một message có được nhiều consumer xử lý không?

Đối với một queue thông thường, một lần `take()` **loại item khỏi queue**, nên một item không được broadcast cho toàn bộ consumers chỉ vì họ cùng gọi `take()`. Đây là **work distribution**, không phải pub/sub. Nếu mỗi consumer phải nhận cùng một event, cần fan-out thành nhiều queue hoặc hệ thống pub/sub rõ ràng.

## 11. Dừng consumer an toàn: poison pill, interrupt và completion

### Poison pill

Poison pill là item đặc biệt báo consumer kết thúc vòng `take`:

```java
while (true) {
    Message message = queue.take();
    if (message instanceof Stop) break;
    handle(message);
}
```

Đây là đoạn mã minh họa; `Message` là kiểu chứa data/stop, `Stop` là một subtype, `queue` là `BlockingQueue<Message>`, `handle` là method đã định nghĩa; method phải xử lý/khai báo `InterruptedException`.

Cần bảo đảm:

1. **Stop không phải dữ liệu hợp lệ.** Dùng typed message để tránh xung đột sentinel.
2. **Stop được đặt sau các item cần xử lý.** Nếu có nhiều producer, phải biết **tất cả producer đã ngừng enqueue** trước khi đưa tín hiệu dừng cuối, nếu không dữ liệu có thể đến sau Stop.
3. **Nhiều consumer:** một Stop thường chỉ được **một** consumer lấy. Nếu có M consumers đang block tại `take()` và dùng cùng cơ chế này, thường cần M Stop items hoặc shutdown protocol khác.
4. **Queue bounded:** việc enqueue Stop cũng có thể block khi queue đầy; phải có consumer đang tiến triển để làm chỗ, hoặc timed/cancel policy thích hợp.
5. **Stop chỉ xác nhận consumer đã thấy tín hiệu**; caller muốn biết consumer **đã xử lý xong** phải chờ `Future.get()`/`join()` hay completion signal đáng tin.

**Không lấy `queue.isEmpty()` làm dấu hiệu dừng:** queue có thể tạm rỗng giữa hai lần producer tạo item, hoặc toàn bộ item đã bị consumer lấy ra nhưng chưa xử lý xong.

### Interrupt và cancellation

`put()`/`take()` có thể ném `InterruptedException`. Thread đang chờ phải có chính sách rõ:

- Khi API cho phép, **propagate** exception để caller quyết định hủy.
- Khi catch ở biên task không thể rethrow, khôi phục interrupt status bằng `Thread.currentThread().interrupt()` và thoát/dọn tài nguyên.
- Đừng catch rỗng rồi tiếp tục chờ vô hạn: shutdown có thể không hoàn tất.
- `Thread.interrupt()` không phải bảo đảm transaction rollback. Item đã được dequeue và xử lý một phần cần chính sách retry/idempotency riêng.

Ví dụ xử lý trong worker:

```java
try {
    while (running) {
        Item item = queue.take();
        process(item);
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // Cleanup/cancel theo contract của worker.
}
```

Đây là đoạn minh họa trong method có `running`, `Item`, `queue` và `process` ở bên ngoài. Nếu interrupt xảy ra giữa xử lý tác vụ không blocking, xử lý hủy còn cần logic khác để phát hiện và dừng, không thể chỉ trông chờ `take()`.

### Graceful shutdown so với shutdownNow

`ExecutorService.shutdown()` ngừng nhận task mới, cho task đã được submit tiếp tục theo contract; nó **không chờ tất cả task kết thúc**. Dùng `Future.get()`/`awaitTermination()` với timeout khi cần hoàn tất. `shutdownNow()` cố gắng dừng các task đang chạy (thường qua interrupt) và trả những task còn chưa chạy, **không cam kết mọi task dừng tức thì**.

Một shutdown protocol tốt cần quyết định: ngừng producer trước hay sau, xử lý backlog ra sao, gửi stop ở thời điểm nào, timeout bao lâu, item in-flight nếu thất bại được xử lý ra sao.

## 12. Một lỗi deadlock tinh vi: producer và consumer dùng cùng pool quá nhỏ

Giả sử một executor có **chỉ một worker**:

```java
// Sơ đồ lỗi, không phải code chạy đầy đủ.
ExecutorService pool = Executors.newSingleThreadExecutor();
BlockingQueue<Integer> q = new ArrayBlockingQueue<>(1);

pool.submit(() -> {
    q.put(1);
    q.put(2); // block vì queue đầy
    return null;
});
pool.submit(() -> q.take()); // task này đang xếp hàng, chưa có worker chạy
```

Đoạn trên là **minh họa thiết kế deadlock** với các imports của `java.util.concurrent`. Producer đầu tiên giữ worker duy nhất, thêm 1 rồi block ở lần `put(2)`. Task consumer nằm trong executor queue, không được chạy cho đến khi producer kết thúc; nhưng producer đang đợi consumer lấy item. Đây là vòng chờ **không thể tiến triển**.

Khắc phục tùy kiến trúc:

- Dùng các worker/nguồn thực thi riêng để consumer **có thể** chạy khi producer block.
- Không submit blocking producer và consumer phụ thuộc nhau vào executor không đủ capacity.
- Chọn nonblocking/timeout submit phù hợp, hoặc thiết kế luồng dữ liệu theo completion stages nếu đúng semantics.
- Nếu producer/consumer cùng một pipeline, bảo đảm luôn có đường thoát khi queue đầy hoặc khi một phía hỏng.

Không suy rằng “có thread pool nên chắc chắn các task sẽ chạy song song”. Kích thước pool, queue task của executor và bounded queue dữ liệu là **hai hàng đợi khác nhau**, và phụ thuộc giữa chúng có thể tạo deadlock.

## 13. Dùng Lock + hai Condition để hiểu bên trong bounded queue

Khi tự cài một bounded buffer, cần bảo vệ invariant:

```text
0 <= queue.size() <= capacity

Producer được thêm nếu:  queue.size() < capacity
Consumer được lấy nếu:   !queue.isEmpty()
```

Một `ReentrantLock` bảo vệ queue cùng hai condition:

- `notFull`: producer chờ “còn chỗ”.
- `notEmpty`: consumer chờ “có item”.

Sau `put` thành công, thông báo một consumer qua `notEmpty.signal()`. Sau `take` thành công, thông báo một producer qua `notFull.signal()`.

### Implementation educational: TinyBoundedBuffer.java

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class TinyBoundedBuffer<E> {
    private final int capacity;
    private final Deque<E> queue = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    public TinyBoundedBuffer(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
    }

    public void put(E element) throws InterruptedException {
        Objects.requireNonNull(element);
        lock.lockInterruptibly();
        try {
            while (queue.size() == capacity) {
                notFull.await();
            }
            queue.addLast(element);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public E take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();
            }
            E element = queue.removeFirst();
            notFull.signal();
            return element;
        } finally {
            lock.unlock();
        }
    }
}
```

Lớp trên đã được **biên dịch thành công với `javac --release 17`**. Nó là một class Java độc lập (`TinyBoundedBuffer.java`) nhưng **không có `main`**, nên không chạy trực tiếp bằng `java TinyBoundedBuffer`. Có thể tạo một test driver khác để gọi `put/take`.

### Phân tích chi tiết

1. Constructor từ chối `capacity <= 0` vì bounded buffer này bắt buộc có ít nhất một slot.
2. `ArrayDeque` là cấu trúc **không tự thread-safe**; tất cả truy cập `queue` đều được bảo vệ bởi cùng `lock`.
3. Producer lấy lock bằng `lockInterruptibly()` để có thể hủy cả giai đoạn **đợi giành lock**.
4. `while (queue.size() == capacity) notFull.await()` kiểm tra predicate trước khi chờ; `await()` nhả lock trong khi đợi, rồi **reacquire trước khi trả về**.
5. Khi có chỗ, producer thêm item, signal nhóm consumer đợi `notEmpty`, rồi nhả lock trong `finally`.
6. Consumer tương tự: khi rỗng thì await `notEmpty`; khi có item thì xóa đầu, signal `notFull`, trả item.
7. `while` là bắt buộc vì spurious wakeup, `signalAll` từ nơi khác hoặc một thread khác có thể lấy tài nguyên trước waiter sau khi nó reacquire.
8. `signal()` không nhả lock ngay. Waiter chỉ có thể đi tiếp sau khi thread đang giữ lock thoát critical section và waiter giành lại lock.
9. Nếu `await()` bị interrupt, nó ném `InterruptedException` theo contract; `finally` vẫn release lock sau khi đã được reacquire theo semantics của ReentrantLock Condition.
10. `Objects.requireNonNull` từ chối null vì `ArrayDeque` không cho null và vì null thường được dùng làm marker “empty” ở các API queue.

**Chủ ý giới hạn:** lớp này chưa triển khai `offer`, `poll`, timeout, `size`, fairness, close/shutdown, batch operations, metrics, cancellation khi consumer thất bại hay các edge cases production. Nó chỉ diễn giải invariant và điều kiện chờ. Với phần lớn use case, dùng `ArrayBlockingQueue` chuẩn sẽ dễ bảo trì và đáng tin hơn.

### Tại sao phải có hai Condition?

Nếu producer và consumer cùng nằm trên một condition queue chung, `signal()` có thể đánh thức một waiter **không phải nhóm cần tiến triển**. Ví dụ sau take, cần đánh thức producer đang chờ queue đầy, không phải đánh thức thêm consumer rỗng. Hai condition phân tách đúng vai trò, giảm wake-up không cần thiết.

`signalAll()` có thể được dùng khi trạng thái cho nhiều waiter cùng tiến triển, hoặc khi shutdown cần đánh thức mọi nhóm để kiểm tra cờ dừng. Tuy nhiên, đánh thức tất cả khi chỉ có một slot mới thường tạo thundering herd: nhiều thread tranh lock rồi phần lớn lại chờ.

## 14. Điều gì xảy ra với Java Memory Model?

Queue là **concurrency primitive**. JavaDoc của `BlockingQueue` mô tả memory-consistency effect: các thao tác của producer trước khi đặt object vào queue *happen-before* thao tác của consumer sau khi truy cập/lấy object đó.

Điều này quan trọng khi item chứa dữ liệu được producer xây trước khi publish:

```java
// Minh họa trong method; record tạo immutable payload:
record Event(int id, String text) {}

BlockingQueue<Event> inbox = new ArrayBlockingQueue<>(10);
Event event = new Event(7, "created");
inbox.put(event);
// Thread khác lấy bằng inbox.take() rồi đọc id và text.
```

Đây là đoạn code minh họa, không có `main` và cần xử lý/khai báo `InterruptedException`. Khi consumer nhận event, nó có thể đọc đúng những giá trị đã được chuẩn bị trước enqueue theo hợp đồng publication.

Tuy nhiên:

- Queue không làm mọi field mutable của object trở thành atomic.
- Bên gửi tiếp tục mutate payload sau enqueue có thể tạo data race.
- Nếu cần bảo toàn invariant đa trường, nên dùng immutable snapshot hoặc transfer ownership; đọc một reference an toàn không có nghĩa thay đổi bên trong object graph được đồng bộ.
- Nếu producer crash ở thời điểm đang tạo nhưng **trước enqueue**, item chưa được truyền; nếu process crash khi queue còn chứa dữ liệu, queue trong RAM có thể mất toàn bộ backlog.
- Việc consumer lấy item ra không đồng nghĩa side effect đã commit thành công.

**`volatile`/`synchronized` thủ công không cần bọc quanh mỗi `put/take`** chỉ để bảo vệ cấu trúc `BlockingQueue` chuẩn; implementation đã có synchronization. Nhưng nếu consumer và producer chia sẻ **trạng thái khác ngoài queue**, trạng thái ấy vẫn cần giao thức thread safety riêng.

## 15. Failure handling: dequeue không phải “xử lý thành công”

Trong producer–consumer, cần phân biệt bốn mốc:

```text
Sản xuất item -> enqueue thành công -> dequeue thành công
                                     -> xử lý side effect -> xác nhận hoàn tất
```

`take()` chỉ xác nhận một consumer đã lấy item ra. Nếu consumer ném exception trong `process(item)`, item đã **không còn trong queue**. `BlockingQueue` thông thường không tự có cơ chế acknowledgment/requeue như một durable broker.

Ví dụ:

```java
Item item = queue.take();
process(item); // Có thể thất bại sau khi item đã bị xóa khỏi queue.
```

Đây là đoạn code minh họa. Muốn bảo đảm không mất job theo contract cần một cơ chế riêng:

- Ghi job bền vững trước khi enqueue, hoặc dùng broker với ack/redelivery phù hợp.
- Thiết kế retry queue/dead-letter xử lý lỗi với số lần thử có giới hạn.
- Dùng idempotency key cho side effect có thể chạy lại.
- Theo dõi in-flight jobs và trạng thái hoàn tất, không chỉ `size()`.
- Quy định xử lý khi consumer process chết, process JVM dừng, timeout hoặc kết quả không rõ ràng.

**Không khẳng định “exactly once” chỉ nhờ ArrayBlockingQueue.** Trong một execution bình thường, một element được một lần `take` lấy ra khỏi queue. Điều đó khác hoàn toàn với “side effect bên ngoài hệ thống chỉ xảy ra đúng một lần qua crash/retry”.

## 16. Cách tính capacity và số consumer

Không có một giá trị capacity tối ưu cho mọi chương trình. Một quy trình thực dụng:

1. **Đo tốc độ đến (arrival rate)**, số item/s ở tình huống trung bình và burst.
2. **Đo tốc độ xử lý (service rate)** của mỗi consumer, phân biệt CPU-bound và I/O-bound.
3. **Ước lượng memory cost mỗi item**, bao gồm object graph, payload và metadata.
4. **Chọn ngân sách backlog** theo dung lượng RAM và maximum queueing latency được phép.
5. **Giới hạn tổng in-flight** nếu mỗi consumer giữ item lớn hoặc side effect tốn tài nguyên.
6. **Stress với burst và downstream chậm**, đo rejection/timeout, blocked producer time, queue depth và p95/p99 end-to-end latency.
7. **Điều chỉnh consumer concurrency** theo bottleneck thực, tránh mở quá nhiều concurrent request đến downstream có quota.

Ví dụ giả định queue có capacity 500 và mỗi item giữ khoảng 20 KiB payload + object liên quan: riêng backlog có thể chiếm cỡ **10 MiB** chưa tính overhead, references, GC metadata hoặc các item đang xử lý. Đây là phép ước lượng minh họa; cần đo heap thực trên data thật trước khi chọn production capacity.

### Quan hệ tốc độ và độ trễ

Nếu backlog hiện tại xấp xỉ 200 item, consumer xử lý ổn định khoảng 50 item/s và không có item mới, thời gian để xả hết backlog lý tưởng là khoảng `200 / 50 = 4 giây`. Khi producer vẫn thêm item, số liệu thay đổi; khi processing time có phương sai lớn, p99 latency có thể cao hơn trung bình nhiều.

Little's Law (`L = λW`) có thể giúp kiểm tra tính nhất quán của số đo đối với một hệ ổn định và phạm vi đo được định nghĩa rõ, nhưng **không dùng nó như công thức độc lập để đặt capacity** trong hệ thống có burst lớn hoặc chưa ổn định.

### Backpressure có thể lan ngược đến đâu?

Nếu producer đang chạy trong request thread, `put()` block có thể làm request latency vượt SLA và timeout. Nếu producer lấy dữ liệu từ socket, việc block có thể làm buffer phía trước tăng. Nếu producer là một task trong executor, blocking có thể làm hết worker cho task quan trọng khác.

Vì vậy, chỉ **có queue bounded** chưa đủ; phải biết upstream phản ứng với việc đầy như thế nào và có backlog nào **trước queue** không.

## 17. Metrics và chẩn đoán một bounded queue

Một số tín hiệu hữu ích:

| Metric/quan sát | Diễn giải | Cẩn thận |
|---|---|---|
| `queue.size()` theo thời gian | Backlog hiện tại, xu hướng tích tụ | Chỉ là snapshot; không dùng để điều phối atomic |
| `remainingCapacity()` | Số slot trống ở thời điểm quan sát | Không phải reservation; thay đổi ngay khi producer khác enqueue |
| Thời gian `put`/timed offer | Producer bị backpressure bao lâu | Phân biệt thời gian chờ với thời gian tạo item |
| Thời gian `take`/poll | Consumer đợi vì thiếu item | Chờ queue rỗng có thể là idle bình thường |
| Accepted/rejected/timeouts | Áp lực tạo việc so với công suất | Cần biết policy drop/retry và số lần duplicate |
| In-flight count | Item đang xử lý sau dequeue | Không suy ra bằng `queue.size()` |
| Processing latency/throughput | Hiệu năng consumer và downstream | Queue full có thể do consumer chậm, không phải do lock queue |
| Error/retry/dead-letter count | Khả năng tiến triển sau lỗi | Retry vô hạn có thể tự tạo storm |

Khi JVM đang chạy, có thể thu thread dump:

```bash
jcmd <PID> Thread.print -l
```

Thay `<PID>` bằng process ID thật. Với queue chuẩn, stack có thể xuất hiện `ArrayBlockingQueue.put/take`, `ConditionObject.await`, `LockSupport.park` khi thread đợi queue đầy/rỗng. `WAITING (parking)` thường là chờ một condition, **không đủ để kết luận deadlock**.

Nếu cần đo các thời gian chờ, dùng JFR và profiler phù hợp (tùy JDK/JVM, event settings và workload). Thread dump là snapshot; việc quan sát chờ queue rỗng của một consumer nhàn rỗi có thể là hành vi hoàn toàn đúng.

## 18. Kiểm thử producer–consumer đúng cách

Một bài test tốt không chỉ hỏi “chương trình có in đúng một lần không?”. Nó phải bao phủ invariant, thứ tự và các tình huống đợi:

### Invariant cốt lõi

- `0 <= size <= capacity` trên toàn bộ các trạng thái có thể quan sát qua API queue.
- Với một producer và một consumer, item được lấy theo FIFO của queue.
- Nếu không có drop/cancel/error và tất cả producer hoàn tất, mọi item đã enqueue phải được consumer xử lý theo contract.
- Không item dữ liệu nào bị coi nhầm là poison pill.
- Khi dừng, các thread không còn bị block vô hạn ở `put/take`.
- Khi processing thất bại, hệ thống có chính sách retry/dead-letter/khai báo lỗi rõ ràng.

### Test các trạng thái ranh giới

1. **Empty queue:** consumer đợi, sau đó producer enqueue item; consumer tiếp tục.
2. **Full queue:** producer dùng `offer` nhận `false`; với `put`, producer chờ tới khi có chỗ.
3. **FIFO:** một producer enqueue A, B, C; một consumer lấy đúng A, B, C.
4. **Many producers:** tất cả item có ID được nhận chính xác theo contract; không giả định global order nếu không có sequence.
5. **Many consumers:** kiểm tra không item bị xử lý hai lần trong đường chạy thường, nếu không có retry; thứ tự completion có thể khác dequeue.
6. **Timeout:** timed offer trả `false` khi không có slot trong thời hạn; timed poll trả `null` khi chưa có item.
7. **Interrupt:** producer/consumer đang chờ có thể thoát theo cancellation policy và không giữ tài nguyên vô thời hạn.
8. **Shutdown:** không đặt STOP trước khi producer cuối hoàn tất; các consumer được thông báo dừng; pool hoàn tất hoặc hết deadline.
9. **Failure:** consumer ném exception giữa chừng; main nhận được lỗi qua `Future.get()` và kích hoạt recovery policy.
10. **Memory pressure:** payload lớn, backlog đột biến; capacity hạn chế số item queue nhưng in-flight/queues khác cũng phải đo.

### Đồng bộ test bằng primitive, không đoán bằng sleep

`CountDownLatch` hoặc `CyclicBarrier` có thể phối hợp cho test biết producer đã tới một checkpoint. Dùng `Future.get(timeout, unit)` để test fail có kiểm soát nếu không tiến triển. `Thread.sleep` có thể giúp mô phỏng xử lý chậm, nhưng **không phải công cụ bảo đảm** producer đã block hay consumer đã nhận item.

Ví dụ assertion logic cần đặt sau khi các Future hoàn tất: số item consumer xử lý bằng số item producer enqueue thành công, tổng checksum khớp, tất cả task thoát sạch.

## 19. Những lỗi thường gặp

1. **Dùng `new LinkedBlockingQueue<>()` rồi tưởng có capacity nhỏ.** Mặc định capacity là `Integer.MAX_VALUE`; cần chỉ định rõ ngưỡng khi muốn bounded.
2. **Bỏ qua `offer() == false`.** Công việc có thể bị mất lặng lẽ. Return boolean là một phần contract phải xử lý.
3. **Sử dụng `queue.size()` để quyết định chắc chắn put/take có thể thực hiện.** Race giữa check và action; dùng operation atomic do queue cung cấp.
4. **Dùng `Thread.sleep` thay `take`.** Polling tạo độ trễ, khó cancellation và không cần thiết.
5. **Coi queue empty là mọi công việc đã hoàn thành.** Consumer có thể đã dequeue nhưng vẫn đang xử lý.
6. **Chỉ đưa một poison pill cho N consumers.** N−1 consumers có thể vẫn block ở `take`.
7. **Đưa poison pill trước khi tất cả producers dừng.** Có thể còn data enqueue phía sau tín hiệu dừng, bị bỏ lại.
8. **Poison pill có thể trùng dữ liệu hợp lệ.** Dùng typed message hoặc marker được bảo đảm không trùng.
9. **Producer và consumer phụ thuộc nhau trong executor chỉ có một worker.** Producer block khi queue đầy, consumer task không bao giờ được cấp worker.
10. **Cho rằng FIFO dequeue nghĩa là FIFO completion.** Với nhiều consumer, task xử lý chậm/nhanh khác nhau.
11. **Tiếp tục sửa mutable payload sau enqueue.** Queue thread-safe không tự bảo vệ state bên trong item.
12. **Cho rằng `take` đồng nghĩa “đã xử lý/commit”.** Dequeue chỉ lấy item; side effect có thể lỗi.
13. **Nuốt `InterruptedException`.** Shutdown/hủy có thể treo vô thời hạn.
14. **Gọi `shutdownNow` và mặc định mọi task đã dừng.** ShutdownNow là nỗ lực hủy; cần quan sát kết thúc và xử lý interruption.
15. **Dùng queue không giới hạn để “đỡ mất item”.** Đổi sang nguy cơ OOM khi upstream nhanh hơn downstream.
16. **Tăng capacity mãi để chữa consumer chậm.** Capacity hấp thụ burst, không thay tốc độ xử lý bền vững.
17. **Đánh thức sai nhóm khi tự cài queue.** Producer phải đợi notFull, consumer phải đợi notEmpty; signal đúng condition.
18. **Dùng `if` thay `while` quanh `Condition.await`.** Spurious wakeup hoặc tranh chấp sau signal có thể làm predicate không còn đúng.
19. **Quên `finally { lock.unlock(); }` trong implementation tự viết.** Exception có thể giữ explicit lock mãi.
20. **Đặt side effect không idempotent trong retry không giới hạn.** Việc xử lý có thể lặp lại, cần chính sách commit/retry rõ.

## 20. Cách chọn mô hình và khi không nên tự xây queue

| Nhu cầu | Lựa chọn khởi đầu |
|---|---|
| Bounded FIFO buffer cơ bản trong JVM | `ArrayBlockingQueue<>(capacity)` |
| Bounded FIFO bằng node, capacity cấu hình rõ | `LinkedBlockingQueue<>(capacity)` |
| Không cần đệm, producer gặp consumer trực tiếp | `SynchronousQueue` |
| Một task trả kết quả cho caller, không cần queue data riêng | `Callable` + `Future` / `ExecutorService` |
| Điều phối từng giai đoạn hay chờ một sự kiện | `CountDownLatch`, `Phaser` hoặc `Condition` tùy predicate |
| Cần một state owner tuần tự nhận command | Một consumer + bounded queue, protocol shutdown/ack |
| Cần durability, replay và redelivery qua crash | Persistent broker/log hoặc transactional storage |
| Cần bảo vệ invariant nhiều field khi xử lý item | Khóa/atomic/immutable state đúng phạm vi, **ngoài** queue |

Chọn **primitive đơn giản nhất đáp ứng đúng contract**. Tự viết bounded buffer bằng `Condition` hữu ích để học, nhưng production implementation cần đáp ứng một tập lớn tình huống không có trong ví dụ: timeout, fairness, exception safety, shutdown, interrupt, batch draining và metrics.

## 21. Câu hỏi tự kiểm tra kèm đáp án

**Câu 1.** `new ArrayBlockingQueue<>(2)` đang chứa hai item. `offer(third)` làm gì?  
**Đáp án:** Trả `false` ngay khi không còn slot, không chờ. Nếu dùng `put(third)` thì producer sẽ chờ khi đầy.

**Câu 2.** `take()` được gọi khi queue rỗng, điều gì xảy ra?  
**Đáp án:** Thread chờ cho tới khi có item để lấy hoặc bị interrupt; không cần polling bằng `sleep`.

**Câu 3.** Tại sao queue bounded có thể giảm OOM nhưng không tự tăng throughput?  
**Đáp án:** Nó giới hạn số item đệm và có thể block/reject producer; tốc độ consumer/downstream không tự tăng.

**Câu 4.** `LinkedBlockingQueue<>()` không truyền capacity có phải bounded theo 100 item không?  
**Đáp án:** Không. Capacity mặc định là `Integer.MAX_VALUE`, có thể gây backlog lớn; nên chỉ định giới hạn theo tài nguyên.

**Câu 5.** Hai consumer lấy A rồi B theo FIFO, có chắc xử lý A hoàn tất trước B không?  
**Đáp án:** Không. C2 xử lý B nhanh hơn C1 xử lý A thì B có thể hoàn tất trước.

**Câu 6.** Producer đặt một `ArrayList` vào queue rồi tiếp tục thay đổi nó; consumer duyệt cùng list. BlockingQueue có bảo vệ list không?  
**Đáp án:** Không. Queue đồng bộ enqueue/dequeue và publication; payload mutable vẫn cần immutable snapshot, ownership transfer hoặc khóa riêng.

**Câu 7.** Có ba consumer đang `take()` và chỉ một poison pill, hệ thống có thể treo khi shutdown không?  
**Đáp án:** Có. Chỉ một consumer lấy Stop; các consumer khác có thể tiếp tục đợi. Cần gửi đủ tín hiệu hoặc protocol khác.

**Câu 8.** `queue.isEmpty() == true` có chứng minh mọi task đã xử lý xong không?  
**Đáp án:** Không. Item có thể đang in-flight trong consumer. Dùng `Future.get()`/completion tracking để biết kết quả.

**Câu 9.** Tại sao `remainingCapacity() > 0` không nên dùng như một thao tác “đặt chỗ”?  
**Đáp án:** Producer khác có thể enqueue giữa lúc kiểm tra và thêm. `offer` là operation quyết định thành công/thất bại tại thời điểm thực thi.

**Câu 10.** Khi tự cài queue bằng `ReentrantLock`, tại sao phải dùng `while` quanh `await`?  
**Đáp án:** Waiter có thể thức dậy giả, hoặc thread khác lấy item/slot trước khi waiter giành lại lock. Luôn kiểm tra lại predicate.

**Câu 11.** Producer trong executor một worker bị block ở `put` khi queue đầy, consumer task đang chờ trong cùng executor. Vì sao có thể deadlock?  
**Đáp án:** Producer giữ worker duy nhất; consumer cần worker để lấy item nhưng không được chạy; producer sẽ không giải phóng worker trước khi consumer tiến triển.

**Câu 12.** Có thể gọi side effect thanh toán ngay sau `take` và khẳng định không bao giờ xảy ra hai lần khi retry không?  
**Đáp án:** Không. Queue trong JVM không cung cấp exactly-once external side effects; retry/crash có thể gây lặp, cần idempotency/transaction.

**Câu 13.** `signal()` trong queue tự cài có nhả lock ngay và cho waiter chạy không?  
**Đáp án:** Không. Caller vẫn giữ lock đến `unlock`; waiter phải reacquire và kiểm tra lại predicate.

**Câu 14.** Một queue capacity 100 không có item nhưng consumer đang `WAITING (parking)`. Có chắc deadlock không?  
**Đáp án:** Không. Consumer có thể đang chờ item đúng thiết kế. Phải xem producer, predicate, stack, trạng thái shutdown và khả năng tiến triển.

**Câu 15.** Khi muốn producer không chờ quá 300ms, nên chọn API nào?  
**Đáp án:** `offer(item, 300, TimeUnit.MILLISECONDS)` và xử lý `false`/`InterruptedException` theo contract.

## 22. Tài liệu tham khảo

- [Java SE 17 — BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): các nhóm method và memory-consistency effects.
- [Java SE 17 — ArrayBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html): capacity, FIFO, fairness và blocking operations.
- [Java SE 17 — LinkedBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html): linked-node queue và các constructor capacity.
- [Java SE 17 — SynchronousQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/SynchronousQueue.html): handoff không có queue storage.
- [Java SE 17 — ReentrantLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): explicit lock, unlock, interruptible acquisition.
- [Java SE 17 — Condition](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/Condition.html): `await`, `signal`, `signalAll` và các predicate wait loops.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): lifecycle, shutdown và submission.
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): completion và xử lý kết quả/ngoại lệ từ task.
- [JLS Java SE 17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Java Memory Model và happens-before.

**Kết luận:** mô hình producer–consumer với bounded queue phải giải quyết đồng thời **an toàn chia sẻ dữ liệu**, **giới hạn backlog**, **tiến triển của cả hai phía** và **lifecycle của công việc**. Hãy bắt đầu bằng `ArrayBlockingQueue` hoặc `LinkedBlockingQueue` có capacity rõ ràng, dùng `put/take` hoặc timed `offer/poll` theo contract, xử lý interruption và shutdown dứt điểm, không coi queue rỗng là xử lý xong và không nhầm enqueue/dequeue với một transaction bền vững.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-coordination-005">BlockingQueue: put(), take(), offer(), poll()</a>

<details>
<summary>Xem chi tiết</summary>

# BlockingQueue: put(), take(), offer(), poll() trong Java

Khi nhiều thread cần truyền công việc hoặc dữ liệu qua lại, việc tự dùng một `List`, một biến cờ và vòng lặp liên tục kiểm tra có thể gây race condition, chờ bận (*busy waiting*) hoặc làm mất dữ liệu. `java.util.concurrent.BlockingQueue<E>` là một abstraction **queue thread-safe**, đồng thời cung cấp các thao tác **đợi khi chưa thể thêm hoặc lấy phần tử**. Nó là nền tảng thường gặp của mô hình **producer–consumer**, hàng đợi tác vụ, bộ đệm giới hạn dung lượng và *backpressure* trong Java.

Bốn phương thức trọng tâm là:

- `put(e)`: đưa phần tử vào, **chờ nếu chưa có chỗ**.
- `take()`: lấy và loại bỏ phần tử đầu hàng đợi, **chờ nếu hàng đợi rỗng**.
- `offer(e)`: thử thêm **ngay**, trả `false` nếu không thể thêm vào lúc đó.
- `poll()`: thử lấy **ngay**, trả `null` nếu không có phần tử.

Các overload `offer(e, timeout, unit)` và `poll(timeout, unit)` cung cấp **thời gian chờ có giới hạn**. Cần hiểu sự khác nhau này trước khi thiết kế đường hủy, timeout, dừng consumer và xử lý hàng đợi đầy.

Bài Knowledge thuộc chủ đề **Phối hợp luồng**, áp dụng cho **Java concurrency nói chung (Java 17+)** và không đi vào các cơ chế riêng của Spring.

Sau bài này, bạn có thể:
1. Chọn đúng một trong các thao tác thêm/lấy/đợi/thử của `BlockingQueue`.
2. Viết producer–consumer có dữ liệu truyền an toàn mà không phải tự dùng `wait/notify`.
3. Biết tác động của queue **bounded** và **unbounded** lên backpressure và bộ nhớ.
4. Xử lý `InterruptedException`, timeout, poison pill và cách kết thúc worker có trật tự.
5. Hiểu visibility theo Java Memory Model khi chuyển giao dữ liệu qua queue.
6. Nhận diện giới hạn: queue không có `close()` chuẩn, không biến object mutable bên trong thành thread-safe, và không tự bảo đảm shutdown hay xử lý task đúng một lần.

## 1. BlockingQueue là gì và tại sao hữu ích?

`BlockingQueue<E>` thuộc `java.util.concurrent` và mở rộng ý tưởng của `Queue<E>`. Nó cung cấp những phép thao tác đồng thời để nhiều producer thêm và nhiều consumer lấy phần tử mà không phải tự khóa một `ArrayList`. Tùy implementation, thứ tự lấy phần tử có thể là FIFO, theo priority, theo thời điểm đến hạn, hoặc handoff trực tiếp: **`BlockingQueue` không hứa tất cả implementation đều FIFO**.

Mô hình ý tưởng:

~~~text
Producer A ---- put/offer ----+
                              |
Producer B ---- put/offer ----+--> [ BlockingQueue<E> ] --> take/poll --> Consumer X
                              |                      \--> take/poll --> Consumer Y
Producer C ---- put/offer ----+

Khi queue BOUNDED đầy: put() đợi chỗ trống.
Khi queue rỗng:        take() đợi phần tử.
offer() và poll():     trả kết quả ngay, không chờ.
~~~

Một queue thread-safe giúp điều phối thao tác trên **cấu trúc hàng đợi**, nhưng không tự trả lời mọi câu hỏi nghiệp vụ: có bao nhiêu consumer? Dừng thế nào? Nếu xử lý thất bại có retry không? Có bảo đảm đúng một lần hay không? Dữ liệu có immutable không? Các câu hỏi đó cần một giao thức riêng.

### 1.1. Tại sao không dùng vòng lặp poll liên tục?

~~~java
// Mã chỉ minh họa anti-pattern; queue đã được khai báo.
while (true) {
    Message message = queue.poll();
    if (message != null) {
        process(message);
    }
}
~~~

Nếu hàng đợi rỗng, vòng lặp có thể **quay liên tục và tốn CPU**. Những tên `Message` và `process` ở đây chỉ là placeholder để minh họa, **không phải chương trình độc lập**. Với worker cần chờ đến khi có dữ liệu, `take()` là cách diễn đạt đúng hơn; nếu cần định kỳ kiểm tra shutdown, `poll(timeout, unit)` có thể phù hợp.

## 2. Bốn nhóm phương thức: ném lỗi, trả trạng thái, chờ, chờ có hạn

`BlockingQueue` định nghĩa những lựa chọn có ý nghĩa khác nhau khi không thể thao tác ngay:

| Thao tác | Ném ngoại lệ ngay | Trả giá trị/trạng thái ngay | Chờ vô hạn đến khi khả thi | Chờ có thời hạn |
| --- | --- | --- | --- | --- |
| **Thêm** | `add(e)` | `offer(e)` | `put(e)` | `offer(e, timeout, unit)` |
| **Lấy và loại bỏ** | `remove()` | `poll()` | `take()` | `poll(timeout, unit)` |
| **Xem đầu, không loại bỏ** | `element()` | `peek()` | Không có trong API | Không có trong API |

Đây là bảng chính theo hợp đồng `BlockingQueue`, đặc biệt quan trọng với queue **có giới hạn dung lượng**.

**Với queue đầy:**
- `add(e)` ném `IllegalStateException` nếu không thêm được do giới hạn dung lượng.
- `offer(e)` trả `false` nếu không thêm được ngay.
- `put(e)` chờ đến khi có chỗ; có thể ném `InterruptedException`.
- `offer(e, timeout, unit)` chờ tối đa thời hạn, trả `true` nếu thêm được, `false` nếu hết hạn; có thể ném `InterruptedException`.

**Với queue rỗng:**
- `remove()` và `element()` ném `NoSuchElementException`.
- `poll()` và `peek()` trả `null`.
- `take()` đợi đến khi có phần tử, rồi **lấy và loại bỏ**; có thể ném `InterruptedException`.
- `poll(timeout, unit)` đợi tối đa thời hạn để lấy phần tử; trả `null` nếu hết hạn; có thể ném `InterruptedException`.

Phân biệt `peek()` với `poll()`: `peek()` chỉ xem phần tử đầu, không lấy ra; `poll()` xóa phần tử khỏi queue khi thành công. Không có thao tác `peek` đợi vô hạn trong interface này.

### 2.1. Vì sao không có null element?

Các implementation của `BlockingQueue` **không cho phép đưa `null` vào queue**. Lý do quan trọng: `poll()` và `peek()` dùng `null` làm dấu hiệu “không có phần tử”, vì vậy một giá trị `null` thực trong queue sẽ gây nhập nhằng.

~~~java
// Mã minh họa: queue là BlockingQueue<String> đã tạo.
queue.offer(null); // Ném NullPointerException.
~~~

Nếu nghiệp vụ có khái niệm “không có dữ liệu” hoặc “kết thúc”, hãy dùng một **kiểu thông điệp rõ ràng** như record chứa trạng thái, một sentinel object không phải null, hoặc cơ chế cancellation/shutdown khác.

## 3. Ví dụ không chặn: offer() và poll()

`ArrayBlockingQueue` có **dung lượng cố định**, thích hợp để minh họa queue đầy/rỗng và backpressure. Chương trình Java 17+ sau không có thread phụ nên stdout xác định.

~~~java
// OfferPollDemo.java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class OfferPollDemo {
    public static void main(String[] args) {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);

        System.out.println("offer 10: " + queue.offer(10));
        System.out.println("offer 20: " + queue.offer(20));
        System.out.println("offer 30: " + queue.offer(30));

        System.out.println("poll: " + queue.poll());
        System.out.println("poll: " + queue.poll());
        System.out.println("poll empty: " + queue.poll());
    }
}
~~~

~~~bash
javac OfferPollDemo.java
java OfferPollDemo
~~~

Stdout:

~~~text
offer 10: true
offer 20: true
offer 30: false
poll: 10
poll: 20
poll empty: null
~~~

Lý do:
1. Queue có capacity 2 nên nhận 10 và 20.
2. Lúc đầy, `offer(30)` trả `false` **ngay**, không chờ.
3. `ArrayBlockingQueue` có thứ tự FIFO nên `poll()` lấy 10 rồi 20.
4. Khi không còn phần tử, `poll()` trả `null` ngay.

Cách dùng phù hợp: đường xử lý muốn **thử nhận việc** và quyết định ngay nếu queue quá tải (báo busy, drop theo chính sách, retry ở nơi khác). Nếu không kiểm tra giá trị `false` từ `offer` mà coi như đã gửi thành công, ứng dụng có thể âm thầm **mất công việc**.

## 4. Ví dụ chặn: put() và take()

`put(e)` là cách một producer gửi phần tử khi chấp nhận **đợi lúc queue đầy**. `take()` là cách consumer chờ nhận việc khi **queue rỗng**. Cả hai là interruptible blocking operations.

### 4.1. Producer–consumer với queue giới hạn

Đây là ví dụ đầy đủ Java 17+ có một producer, một consumer và sentinel kết thúc. Thứ tự kết quả trong danh sách xác định nhờ queue FIFO; chỉ in khi hai thread đã kết thúc.

~~~java
// ProducerConsumerDemo.java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ProducerConsumerDemo {
    private static final String END = "__END__";

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);
        List<String> received = new ArrayList<>();

        Thread producer = new Thread(() -> {
            try {
                queue.put("alpha");
                queue.put("beta");
                queue.put(END);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                while (true) {
                    String item = queue.take();
                    if (END.equals(item)) {
                        break;
                    }
                    received.add(item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        System.out.println("Received: " + received);
        System.out.println("Remaining: " + queue.size());
    }
}
~~~

~~~bash
javac ProducerConsumerDemo.java
java ProducerConsumerDemo
~~~

Khi không có interrupt ngoài ý muốn, stdout:

~~~text
Received: [alpha, beta]
Remaining: 0
~~~

**Giải thích chi tiết:**
- Queue có capacity 2; producer có thể bị chặn tại `put(END)` cho tới khi consumer lấy bớt dữ liệu. Không có busy waiting hoặc gọi `notify` thủ công.
- Một producer đưa các phần tử theo thứ tự `alpha → beta → END`. `ArrayBlockingQueue` giữ FIFO, một consumer lấy theo đúng thứ tự.
- `END` là marker không phải `null`; khi lấy marker, consumer thoát.
- Danh sách `ArrayList` chỉ do **một consumer** ghi; main chỉ đọc sau khi `consumer.join()`, vì vậy ví dụ không có sửa đồng thời lên danh sách.
- Việc catch `InterruptedException` trong code demo chỉ khôi phục cờ interrupt và thoát worker; nếu producer bị interrupt trước khi gửi `END`, consumer có thể vẫn đợi mãi. **Không lấy ví dụ tối giản này làm giao thức shutdown toàn diện**: trong production cần một supervisor có cơ chế hủy/đánh thức cả producer và consumer, xử lý việc enqueue sentinel thất bại, và deadline khi chờ hoàn tất.

### 4.2. Queue đầy mang lại backpressure

Với queue dung lượng B, producer có thể đưa tối đa B phần tử chưa được consumer lấy ra. Nếu producer nhanh hơn consumer trong thời gian dài, `put()` **đợi có chỗ**, làm chậm quá trình nhận việc ở đầu nguồn: đây là một dạng **backpressure**.

Backpressure giúp giới hạn dữ liệu tồn đọng nhưng cũng tạo ra một đường chờ mới. Nếu producer giữ một khóa mà consumer cần để lấy/hoàn tất công việc, producer bị chặn trong `put()` có thể góp phần tạo deadlock. Nên tránh giữ lock ngoài không cần thiết trong lúc có thao tác queue chặn.

**Chặn không đồng nghĩa xử lý thành công:** `put()` trả về nghĩa là phần tử đã được đưa vào queue, **không** có nghĩa consumer đã xử lý xong. `take()` trả về nghĩa là phần tử đã được lấy ra khỏi queue, **không** có nghĩa xử lý nghiệp vụ đã thành công. Nếu cần xác nhận kết quả, phải có một kênh ack/result riêng.

## 5. Timed offer() và poll(): chờ có giới hạn

`offer(e, timeout, unit)` và `poll(timeout, unit)` đặc biệt hữu ích khi:
- Producer có deadline và không được chờ vô hạn.
- Consumer cần định kỳ kiểm tra điều kiện dừng, metrics hoặc trạng thái hệ thống.
- Caller muốn chọn phương án fallback khi queue đầy/rỗng quá lâu.

Chương trình độc lập sau dùng một queue có capacity 1 và thời hạn ngắn:

~~~java
// TimedQueueDemo.java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class TimedQueueDemo {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);
        queue.put(7);

        boolean sent = queue.offer(8, 50, TimeUnit.MILLISECONDS);
        System.out.println("Offer while full: " + sent);

        System.out.println("Take: " + queue.take());

        Integer missing = queue.poll(50, TimeUnit.MILLISECONDS);
        System.out.println("Poll while empty: " + missing);

        System.out.println("Offer available: " +
            queue.offer(9, 50, TimeUnit.MILLISECONDS));
        System.out.println("Poll available: " +
            queue.poll(50, TimeUnit.MILLISECONDS));
    }
}
~~~

~~~bash
javac TimedQueueDemo.java
java TimedQueueDemo
~~~

Trong điều kiện chạy bình thường, không có interrupt ngoài ý muốn, stdout:

~~~text
Offer while full: false
Take: 7
Poll while empty: null
Offer available: true
Poll available: 9
~~~

Không có thread khác can thiệp:
- Queue ban đầu đầy với 7, nên `offer(8, 50, MILLISECONDS)` chờ tối đa thời hạn và cuối cùng trả `false`.
- `take()` lấy 7, tạo chỗ trống.
- `poll(50, MILLISECONDS)` chờ trên queue rỗng rồi trả `null`.
- Khi có chỗ, timed `offer(9, ...)` trả `true`; `poll(...)` lấy 9.

**Timeout không có nghĩa là lệnh gửi được giữ lại để tự động tiếp tục sau đó.** Nếu timed offer trả `false`, phần tử 8 **không được thêm** vào queue. Caller phải quyết định retry, từ chối, ghi log, gửi nơi khác hoặc bỏ theo chính sách. Nếu timed poll trả `null`, không có phần tử nào được lấy bởi lời gọi đó.

### 5.1. InterruptedException có ý nghĩa gì?

`put()`, `take()` và các timed overload khai báo `throws InterruptedException`. Khi thread đang chờ và bị interrupt, lời gọi có thể ném ngoại lệ để caller hợp tác dừng. Nếu tầng gọi có thể khai báo `throws InterruptedException` thì thường nên truyền tiếp; nếu phải catch, có thể phục hồi cờ:

~~~java
// Mã minh họa bên trong một worker có biến queue:
try {
    process(queue.take());
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return; // Thoát worker theo giao thức của ứng dụng.
}
~~~

`process` là phương thức giả định đã khai báo; đoạn không độc lập. Không thực hiện vòng lặp vô hạn kiểu catch rồi bỏ qua interrupt, vì nó có thể khiến worker không chịu dừng. Nhưng cũng không nên giả định `interrupt` dừng cưỡng bức mọi đoạn code hoặc tự hoàn tác thao tác nghiệp vụ mà consumer đã thực hiện.

**Phân biệt interrupt và timeout:** timed `poll` trả `null` khi hết hạn, nhưng **ném `InterruptedException` khi bị gián đoạn trong lúc chờ**. Hai trường hợp cần chính sách xử lý khác nhau.

## 6. Các implementation quan trọng và lựa chọn dung lượng

Không phải mọi `BlockingQueue` có cùng cấu trúc, giới hạn và thứ tự.

| Implementation | Đặc trưng | Dung lượng / thứ tự / tình huống |
| --- | --- | --- |
| `ArrayBlockingQueue<E>` | Mảng có capacity cố định | **Bounded**, FIFO; thích hợp cho buffer có giới hạn |
| `LinkedBlockingQueue<E>` | Các node liên kết | FIFO; có thể cấp capacity, mặc định giới hạn ở `Integer.MAX_VALUE` |
| `SynchronousQueue<E>` | Handoff trực tiếp giữa producer và consumer | **Không lưu phần tử** (capacity 0); `put` cần một bên nhận tương ứng |
| `PriorityBlockingQueue<E>` | Queue dựa trên priority | Về API **logically unbounded**, lấy theo mức ưu tiên chứ không phải FIFO chung |
| `DelayQueue<E extends Delayed>` | Phần tử chỉ lấy được khi hết delay | Unbounded theo API; `take()` chờ tới phần tử đến hạn |
| `LinkedTransferQueue<E>` | Queue dựa trên linked nodes, hỗ trợ transfer | Unbounded theo API; có thể handoff/transfer trực tiếp tùy thao tác |

### 6.1. Bounded không giống unbounded

Với một queue **bounded** như `new ArrayBlockingQueue<>(100)`, số phần tử chờ được giới hạn. Khi đầy:
- `put` chờ.
- `offer` trả `false`.
- Timed `offer` chờ tối đa thời hạn rồi quyết định.

Với một queue **logically unbounded**, producer thường không bị giới hạn bởi một capacity hữu hạn được cấu hình trong API. Điều này **không có nghĩa RAM vô hạn**: nếu tốc độ producer lâu dài lớn hơn consumer, backlog có thể gây áp lực bộ nhớ hoặc `OutOfMemoryError`. `LinkedBlockingQueue` không truyền capacity có giới hạn khai báo mặc định là `Integer.MAX_VALUE`, cực lớn về mặt vận hành; hãy cân nhắc truyền capacity rõ ràng.

`remainingCapacity()` có thể hữu ích để quan sát, nhưng trong hệ thống nhiều thread, đây là giá trị nhất thời: một thread khác có thể thêm/lấy ngay sau phép kiểm tra. **Không viết `if (remainingCapacity() > 0) put(...)` rồi tưởng có quyền đặt chỗ nguyên tử.** Cứ dùng `offer`/`put` trực tiếp theo đúng chính sách.

### 6.2. Fairness của ArrayBlockingQueue

`ArrayBlockingQueue` có constructor cho phép cấu hình `fair`:

~~~java
// Mã minh họa:
BlockingQueue<String> queue = new ArrayBlockingQueue<>(8, true);
~~~

Với fairness=true, các thread producer/consumer đang bị chặn được phục vụ theo chính sách xếp hàng công bằng hơn theo hợp đồng implementation. Điều này có thể giảm khả năng một thread phải chờ lâu nhưng cũng thường làm giảm throughput. Không suy diễn fairness của queue thành “mọi task đều được xử lý đúng thứ tự thời gian thực” hoặc “scheduler Java sẽ luôn công bằng”.

### 6.3. SynchronousQueue không phải queue một phần tử

`SynchronousQueue` **không có capacity lưu trữ nội bộ**: mỗi lần insert phải gặp một remove tương ứng từ thread khác. Vì vậy `offer(e)` không đợi sẽ thường trả `false` nếu chưa có consumer đang chờ; `put(e)` chờ cho tới khi có bên nhận phối hợp.

Ví dụ Java 17+ dưới có hai thread trao một phần tử:

~~~java
// SynchronousHandoffDemo.java
import java.util.concurrent.SynchronousQueue;

public class SynchronousHandoffDemo {
    public static void main(String[] args) throws InterruptedException {
        SynchronousQueue<String> queue = new SynchronousQueue<>();

        Thread consumer = new Thread(() -> {
            try {
                queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        queue.put("parcel");
        consumer.join();

        System.out.println("Size: " + queue.size());
        System.out.println("Handoff completed");
    }
}
~~~

~~~bash
javac SynchronousHandoffDemo.java
java SynchronousHandoffDemo
~~~

Khi không bị interrupt ngoài ý muốn, stdout:

~~~text
Size: 0
Handoff completed
~~~

`put("parcel")` hoàn tất khi có bên nhận tương ứng. `size()` là 0 vì `SynchronousQueue` không có khả năng chứa phần tử. Đây là một **handoff**, không phải một buffer có sức chứa 1. Việc `consumer.join()` chờ consumer kết thúc không đồng nghĩa consumer đã hoàn thành xử lý nghiệp vụ phức tạp nào; ví dụ consumer chỉ lấy phần tử.

## 7. Bảo đảm visibility giữa producer và consumer

`BlockingQueue` là concurrent collection của `java.util.concurrent` và có **memory consistency effects** được JDK đặc tả. Hành động của thread producer **trước khi đặt một object vào queue** *happens-before* hành động của thread khác **sau khi truy cập hoặc lấy phần tử đó** từ queue.

Ví dụ minh họa:

~~~java
// Mã minh họa, không phải file độc lập.
final class Message {
    int value;
}
Message message = new Message();
message.value = 42;          // Producer chuẩn bị dữ liệu.
queue.put(message);          // Bàn giao qua BlockingQueue.

// Trong consumer khác:
Message received = queue.take();
System.out.println(received.value); // Nhìn thấy 42 nếu không ghi cạnh tranh khác.
~~~

Quan hệ này giúp producer **công bố dữ liệu đã chuẩn bị** cho consumer mà không cần tự gắn `volatile` lên mọi field của object chỉ phục vụ handoff. Nhưng **đặt object mutable vào queue không tự làm nó bất biến**. Nếu producer tiếp tục sửa `message.value` trong lúc consumer đọc, vẫn có khả năng data race và kết quả không mong muốn.

**Mẫu tốt:** chuẩn bị object xong → put → không sửa object đó nữa, hoặc dùng message bất biến (`record` với thành phần immutable). Đối với queue chứa reference tới một object mutable dùng chung bởi nhiều thread, cần quy tắc ownership/copy/synchronization riêng.

### 7.1. Giao tiếp bằng message thay vì shared mutable state

Thay vì hai thread cùng sửa một `ArrayList`, có thể để producer đưa **từng message** vào queue, và chỉ consumer cập nhật trạng thái xử lý. Điều này giúp giảm phạm vi dữ liệu chia sẻ và làm rõ ranh giới ownership.

Tuy nhiên, queue chỉ bảo vệ quá trình enqueue/dequeue. Nếu cùng một message được gửi đến nhiều nơi, hoặc sau khi `take` consumer chia sẻ reference cho thread khác, trách nhiệm bảo vệ state vẫn tồn tại.

## 8. Thiết kế shutdown: BlockingQueue không có close() chuẩn

`BlockingQueue` **không khai báo phương thức `close()` để tự đánh thức mọi consumer**. Nếu mọi producer đã ngừng nhưng consumer vẫn ở `take()` trên một queue rỗng, nó có thể chờ vô hạn. Vì vậy phải thiết kế tín hiệu kết thúc.

Các cách thường gặp:
1. **Poison pill/sentinel:** gửi một phần tử đặc biệt, consumer thấy thì thoát. Đúng lúc với giao thức có số producer/consumer được biết.
2. **Interrupt/cancellation:** supervisor gửi interrupt cho consumer đang `take()`, worker xử lý `InterruptedException` rồi thoát theo chính sách.
3. **Timed poll + cờ dừng:** worker dùng `poll(timeout, unit)`, định kỳ kiểm tra một điều kiện kết thúc được công bố đúng cách.
4. **Quản lý executor:** dừng nhận công việc, phối hợp producers/consumers, hủy khi cần và `awaitTermination`. `ExecutorService.shutdown()` một mình không đánh thức task đang chờ mãi trên queue nếu task không có đường dừng.

### 8.1. Poison pill có nhiều consumer

Khi có N consumer đang lấy độc lập từ cùng queue, **một poison pill chỉ được một consumer lấy**. Nếu muốn tất cả N consumer đều thoát qua sentinel, thường phải gửi **N phần tử sentinel**, sau khi toàn bộ producer đã hoàn tất gửi công việc thường.

Ví dụ đầy đủ Java 17+, hai consumer, bốn task. Chỉ in kết quả từ main sau `join` nên không lệ thuộc vào thứ tự xử lý của consumer:

~~~java
// MultipleConsumersShutdownDemo.java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MultipleConsumersShutdownDemo {
    record Work(int id, boolean stop) {}
    private static final Work STOP = new Work(-1, true);

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Work> queue = new ArrayBlockingQueue<>(3);
        AtomicInteger processed = new AtomicInteger();

        Runnable consumer = () -> {
            try {
                while (true) {
                    Work item = queue.take();
                    if (item.stop()) {
                        break;
                    }
                    processed.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread a = new Thread(consumer, "consumer-A");
        Thread b = new Thread(consumer, "consumer-B");
        a.start();
        b.start();

        for (int i = 1; i <= 4; i++) {
            queue.put(new Work(i, false));
        }
        queue.put(STOP); // Một pill cho consumer A hoặc B.
        queue.put(STOP); // Pill thứ hai cho consumer còn lại.

        a.join();
        b.join();
        System.out.println("Processed: " + processed.get());
        System.out.println("Remaining: " + queue.size());
    }
}
~~~

~~~bash
javac MultipleConsumersShutdownDemo.java
java MultipleConsumersShutdownDemo
~~~

Khi không có interrupt ngoài ý muốn và không có task lỗi, stdout:

~~~text
Processed: 4
Remaining: 0
~~~

Vì hai STOP được enqueue **sau toàn bộ bốn task** trên `ArrayBlockingQueue` FIFO, không consumer nào lấy STOP trước khi bốn phần tử công việc đã được lấy ra. Hai consumer mỗi người chỉ thoát sau khi lấy một STOP. `processed.incrementAndGet()` là phép tăng nguyên tử; sau khi main `join` cả hai, giá trị là 4.

**Giới hạn của poison pill:**
- Nếu có **nhiều producer** và một producer gửi STOP khi producer khác còn gửi việc, consumer có thể dừng sớm, bỏ sót task phía sau. Cần phối hợp producer completion trước khi gửi pill.
- Nếu consumer bị lỗi/dừng sớm, các lệnh `put(STOP)` có thể kẹt khi queue đầy mà không còn consumer đủ để lấy; cần supervisor, timeout/cancellation.
- Nếu một consumer tái enqueue STOP để consumer khác biết, phải thiết kế rất cẩn thận vì thứ tự và việc lặp lại có thể khó chứng minh. Với số consumer biết trước, một pill trên mỗi consumer thường đơn giản hơn.
- Phải chọn sentinel **không trùng với dữ liệu hợp lệ**. Record có `boolean stop` trong ví dụ phân biệt rõ thông điệp dừng với công việc thường.

## 9. Xử lý task lỗi, đảm bảo hoàn tất và backpressure

### 9.1. Queue chỉ bàn giao, không phải xử lý đúng một lần

Khi `take()` trả về một task, task đã bị loại khỏi queue. Nếu consumer sau đó ném exception trước khi xử lý xong, queue **không tự đưa task trở lại**. Muốn retry/ack, bạn cần cơ chế riêng:
- Nơi lưu trạng thái xử lý hoặc lưu trữ bền vững nếu cần chống mất khi process dừng.
- Quy tắc retry giới hạn, idempotency, xử lý task độc hại (*poison message*).
- Giám sát những consumer chết bất ngờ và việc queue tồn đọng.
- Hợp đồng at-most-once, at-least-once hoặc exactly-once ở cấp nghiệp vụ; bản thân `BlockingQueue` in-memory không bảo đảm delivery bền vững hay exactly-once khi có crash.

### 9.2. Bounded queue và overload

Một hệ thống có queue capacity 1.000 vẫn có thể quá tải nếu producer tạo việc lâu dài nhanh hơn consumer xử lý. Các chính sách khác nhau:

| Chính sách | Cách biểu đạt gần đúng | Đánh đổi |
| --- | --- | --- |
| **Đợi khi đầy** | `put(e)` | Backpressure mạnh, nhưng có thể làm caller chờ vô hạn |
| **Từ chối nhanh** | `if (!offer(e)) reject()` | Latency có giới hạn tại enqueue, phải có xử lý từ chối |
| **Chờ có deadline** | `offer(e, timeout, unit)` | Có thể thoát khi quá hạn, cần chính sách fallback |
| **Bỏ bớt** | `offer(e)` rồi discard có chủ đích | Chấp nhận mất một số sự kiện, chỉ đúng nếu nghiệp vụ cho phép |
| **Tăng năng lực consumer** | Nhiều consumer hơn, nếu workload cho phép | Có contention, ordering, tài nguyên và chi phí vận hành |

Đừng “khắc phục” backpressure bằng cách chọn một queue không giới hạn rồi để backlog tăng mãi. Điều đó chỉ chuyển vấn đề từ chờ producer sang **tích tụ bộ nhớ**.

### 9.3. FIFO không đồng nghĩa hoàn tất theo thứ tự

Với một `ArrayBlockingQueue` và hai consumer, phần tử A có thể được **lấy ra trước** B nhưng xử lý A lại **kết thúc sau** B do công việc hoặc lịch thread khác nhau. Queue FIFO quy định **thứ tự dequeue**, không bảo đảm thứ tự hoàn tất nghiệp vụ nhiều worker. Nếu yêu cầu giữ thứ tự kết quả, phải có một consumer, cơ chế sequence/reorder, hoặc kiến trúc tương ứng.

## 10. Những sai lầm thường gặp

- **“put() trả về là consumer đã làm xong.”** Sai; chỉ nghĩa là enqueue thành công.
- **“take() cũng chỉ xem đầu queue.”** Sai; nó lấy **và loại bỏ** phần tử.
- **“offer() sẽ tự đợi khi queue đầy.”** Sai với overload không timeout; nó trả `false` ngay.
- **“poll() chặn cho tới lúc có việc.”** Sai; nó trả `null` ngay nếu rỗng.
- **“Timed offer trả false nhưng việc sẽ tự được gửi sau.”** Sai; không có enqueue của phần tử đó.
- **“BlockingQueue chứa được null.”** Sai; null dùng để báo trạng thái không có phần tử.
- **“Tất cả BlockingQueue đều FIFO.”** Sai; priority/delay/handoff có semantics khác.
- **“ArrayBlockingQueue capacity 2 có thể tăng dung lượng tự động.”** Sai; capacity cố định.
- **“LinkedBlockingQueue mặc định là buffer nhỏ an toàn.”** Sai; capacity mặc định rất lớn, cần cân nhắc áp lực RAM.
- **“SynchronousQueue chứa một phần tử chờ.”** Sai; capacity nội tại bằng 0, handoff trực tiếp.
- **“Một poison pill dừng mọi consumer.”** Sai; một phần tử chỉ được một consumer lấy.
- **“shutdown() luôn giải phóng worker đang take().”** Sai nếu không có tín hiệu/dừng theo giao thức.
- **“Queue làm object bên trong tự động immutable.”** Sai; giao tiếp bằng queue tạo memory-ordering cho dữ liệu trước handoff, không bảo vệ những lần sửa đồng thời sau đó.
- **“remainingCapacity() > 0 cho phép đặt chỗ.”** Sai; giá trị có thể thay đổi ngay sau khi đọc.
- **“Đọc queue.size() rồi mới quyết định enqueue/dequeue là một phép atomic.”** Sai; đó là check–then–act bị cạnh tranh.
- **“Queue không cần quản lý timeout/interrupt.”** Sai với blocking operations.
- **“Consumer lấy task thì queue đảm bảo xử lý đúng một lần ngay cả khi crash.”** Sai; cần cơ chế xử lý lỗi/durable store riêng.

## 11. Checklist thiết kế và kiểm thử

1. **Xác định mục đích:** queue dùng để đệm công việc hay handoff trực tiếp? Cần FIFO, priority hay delay?
2. **Chọn capacity:** peak backlog cho phép, memory budget, tốc độ producer/consumer, chính sách khi queue đầy.
3. **Chọn API theo yêu cầu:** `put` nếu chấp nhận đợi; `offer` nếu từ chối nhanh; timed offer nếu có deadline; `take` khi muốn đợi việc; timed poll nếu cần kiểm tra liveness định kỳ.
4. **Xử lý mọi kết quả:** kiểm tra `offer` trả false, `poll` trả null, xử lý `InterruptedException` và timeout như các trạng thái khác nhau.
5. **Quy định ownership message:** producer chuẩn bị xong trước enqueue, tránh sửa concurrent sau handoff; cân nhắc record bất biến.
6. **Xác định giao thức shutdown:** producer nào thông báo hoàn tất? Ai đặt poison pill? Có bao nhiêu consumer? Điều gì xảy ra nếu một worker lỗi hoặc bị hủy?
7. **Xác định yêu cầu xử lý:** có ack, retry, deduplication, idempotency hoặc durable persistence không?
8. **Đo và giám sát:** queue length, thời gian chờ enqueue/dequeue, throughput, số consumer hoạt động, tốc độ backlog tăng.
9. **Kiểm thử đúng lịch xấu:** queue đầy, rỗng, producer bị interrupt, consumer chết, timed offer thất bại, nhiều producer cùng kết thúc.
10. **Không suy luận từ stress test đơn lẻ:** kiểm tra invariant và đường thoát bằng các điểm đồng bộ có kiểm soát.

### Mẫu kiểm tra nhanh khi đọc code

| Nếu nhìn thấy | Hãy hỏi |
| --- | --- |
| `queue.put(item)` bên trong một synchronized block | Có nguy cơ chờ queue trong lúc giữ lock mà consumer cần không? |
| `if (!queue.offer(item)) { }` | Việc bị từ chối được xử lý hay âm thầm mất? |
| `while (queue.poll() == null)` | Có busy spin tiêu thụ CPU không? |
| `queue.take()` trong worker chạy mãi | Ai sẽ đánh thức/dừng nó khi hệ thống shutdown? |
| Một sentinel duy nhất, nhiều consumer | Những consumer còn lại sẽ thoát bằng đường nào? |
| `LinkedBlockingQueue<>()` không capacity | Có nguy cơ backlog chiếm bộ nhớ quá lớn không? |
| `queue.size()` để quyết định chính xác lượng tồn | Giá trị này có thay đổi giữa check và action không? |
| Worker tạo object rồi vẫn sửa sau `put` | Có data race trên object mutable không? |

## 12. Câu hỏi tự kiểm tra

**1.** `ArrayBlockingQueue<Integer>` có capacity 1, đang chứa 10. `offer(20)` trả gì?  
**Đáp án:** `false` ngay, vì không còn capacity. `put(20)` sẽ chờ tới khi có chỗ (hoặc bị interrupt).

**2.** Queue rỗng. `poll()` và `take()` khác nhau thế nào?  
**Đáp án:** `poll()` trả `null` ngay; `take()` chờ có phần tử và có thể bị interrupt.

**3.** `poll(100, MILLISECONDS)` hết thời gian nhưng không có item, trả gì?  
**Đáp án:** `null`. Nếu thread bị interrupt trong lúc chờ, có thể ném `InterruptedException`.

**4.** Sau `put(task)`, producer có thể kết luận task đã được consumer xử lý thành công?  
**Đáp án:** Không. `put` chỉ xác nhận enqueue thành công; cần ack/result riêng để biết trạng thái nghiệp vụ.

**5.** Vì sao không nên đưa `null` vào `BlockingQueue`?  
**Đáp án:** Vì `poll`/`peek` dùng `null` báo rỗng; API cấm null element.

**6.** Có ba consumer chờ `take()` và producer kết thúc. Một STOP message đủ dừng cả ba không?  
**Đáp án:** Không. Một message chỉ được một consumer lấy. Nếu dùng poison pill, thường gửi ba pill sau khi mọi producer hoàn tất.

**7.** `SynchronousQueue` có thể chứa bao nhiêu item đợi?  
**Đáp án:** Zero. Mỗi lần gửi thành công cần gặp một thao tác nhận tương ứng.

**8.** `new LinkedBlockingQueue<>()` có phải queue bounded nhỏ?  
**Đáp án:** Không. Giới hạn khai báo mặc định là `Integer.MAX_VALUE`, vì vậy cần cân nhắc cấu hình capacity để tránh tích tụ bộ nhớ.

**9.** Producer khởi tạo `Message.value = 42`, đưa object vào BlockingQueue rồi consumer lấy ra. Có bảo đảm visibility của ghi trước enqueue không?  
**Đáp án:** Có, theo memory consistency effects của concurrent collection, miễn giao thức phù hợp và không có ghi cạnh tranh sau đó.

**10.** Nếu queue FIFO có hai consumer, task A được lấy trước B, A chắc chắn hoàn tất trước B?  
**Đáp án:** Không. Thứ tự dequeue không đồng nghĩa thứ tự hoàn tất xử lý song song.

**11.** Nếu `offer(e, 1, SECONDS)` trả false thì queue có tự gửi e khi trống chỗ sau đó không?  
**Đáp án:** Không. Caller phải quyết định retry hoặc fallback.

**12.** Nếu hệ thống cần xử lý công việc tồn tại qua JVM crash, `BlockingQueue` trong bộ nhớ có đủ không?  
**Đáp án:** Không. Cần cơ chế lưu bền/ack/recovery theo yêu cầu nghiệp vụ; queue chỉ đảm nhiệm phối hợp trong JVM.

**Ghi nhớ:** `put/take` là các thao tác **chờ**; `offer/poll` dạng không timeout là **thử ngay**; timed `offer/poll` là **chờ có hạn**. Chọn capacity và chính sách backpressure rõ ràng, xử lý interrupt và shutdown chủ động, và đừng nhầm **enqueue/dequeue thành công** với **xử lý nghiệp vụ thành công**.

### Tài liệu tham khảo chính thức

- Java SE 17 `BlockingQueue` (bảng bốn nhóm thao tác và memory consistency): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html
- Java SE 17 `ArrayBlockingQueue`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html
- Java SE 17 `LinkedBlockingQueue`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html
- Java SE 17 `SynchronousQueue`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/SynchronousQueue.html
- Java SE 17 `java.util.concurrent` package: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-coordination-006">Timeout khi chờ dữ liệu hoặc tài nguyên</a>

<details>
<summary>Xem chi tiết</summary>

# Timeout khi chờ dữ liệu hoặc tài nguyên trong Java

## 1. Timeout giải quyết bài toán gì?

Trong chương trình đa luồng, một thread thường phải **chờ một sự kiện**: dữ liệu xuất hiện trong queue, một lock được giải phóng, một permit còn trống, worker hoàn tất hoặc một điều kiện trạng thái trở thành đúng. Nếu chờ vô hạn, một tác vụ bị treo, producer chết hoặc tài nguyên luôn bị tranh chấp có thể khiến thread chờ mãi.

**Timeout** là giới hạn thời gian thread **sẵn sàng dành cho thao tác chờ**, giúp ứng dụng có thể tiếp tục xử lý khi sự kiện chưa xuất hiện. Đây không phải lời cam kết rằng công việc sẽ hoàn thành trong thời hạn đó.

Ví dụ: một consumer chỉ được chờ tối đa 200 ms để lấy dữ liệu. Nếu chưa có, nó có thể trả về kết quả “chưa có dữ liệu”, ghi nhận retry hoặc chuyển sang một tác vụ khác. **Không được** tự giả định producer đã dừng, dữ liệu không bao giờ tới, hoặc queue đã hỏng chỉ vì `poll(timeout)` hết hạn.

Hai yêu cầu cần tách biệt:

- **Bounded waiting:** không chờ mãi một điều kiện hoặc tài nguyên.
- **Cancellation / deadline của tác vụ:** yêu cầu công việc đang chạy ngừng hoặc không bắt đầu sau một hạn chót. Timeout khi *đợi kết quả* thường **không tự hủy** tác vụ cung cấp kết quả.

Bài học tập trung vào Java concurrency nói chung, sử dụng API của JDK (Java 17+), không gắn với framework.

## 2. Bốn điều cần phân biệt: timeout, deadline, interruption, cancellation

| Khái niệm | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| **Timeout** | Khoảng thời gian tối đa muốn dành cho một thao tác chờ | `queue.poll(200, MILLISECONDS)` |
| **Deadline** | Mốc kết thúc chung cho nhiều giai đoạn; mỗi giai đoạn chỉ dùng **thời gian còn lại** | Hai lần chờ queue và lock dùng cùng ngân sách 500 ms |
| **Interruption** | Tín hiệu hợp tác gửi tới một thread để yêu cầu thoát thao tác chờ/dừng công việc | `waitingThread.interrupt()` |
| **Cancellation** | Quyết định không tiếp tục một task; mức độ hiệu lực phụ thuộc API và code task | `future.cancel(true)` gửi interrupt khi phù hợp |

Một thread có thể ngừng chờ vì **thành công**, **hết thời gian**, **interrupt**, hoặc **lỗi khác**. Thiết kế đúng phải phân biệt các trường hợp đó bằng return value/exception và không biến mọi thất bại thành “timeout”.

### Một phép chờ có hạn có chấm dứt đúng thời điểm không?

**Không bảo đảm chính xác từng mili-giây.** Scheduler, timer và tải CPU ảnh hưởng thời điểm thread thức dậy. Phương thức có timeout thường bảo đảm về cách sử dụng ngân sách chờ theo hợp đồng API, không phải đáp ứng hard real-time. Việc thực thi mã sau khi thức dậy cũng có thể bị scheduler trì hoãn.

Ngoài ra, một thread đã lấy được lock/permit hoặc nhận dữ liệu **trước thời điểm kiểm tra kết quả** vẫn phải xử lý việc đó, kể cả khi thời hạn tổng của ứng dụng vừa trôi qua. Timeout là một phần của giao thức, không thay cho việc kiểm tra state và xử lý trường hợp tranh chấp.

## 3. Nhận dữ liệu với `BlockingQueue.poll(timeout, unit)`

`BlockingQueue` phù hợp cho producer–consumer. Các API thường gặp:

- `take()`: chờ đến khi có phần tử, không đặt timeout; có thể ném `InterruptedException`.
- `poll()`: kiểm tra ngay, không chờ; nếu rỗng, trả `null`.
- `poll(long timeout, TimeUnit unit)`: đợi tối đa khoảng timeout khi queue rỗng; trả phần tử nếu có, hoặc `null` nếu hết thời gian mà vẫn chưa nhận được.
- `put(element)`: chờ nếu queue có giới hạn và đang đầy.
- `offer(element, timeout, unit)`: đợi một khoảng có giới hạn để thêm vào queue đầy; trả `boolean`.

**Ý nghĩa của `null`:** đối với `BlockingQueue` tiêu chuẩn, phần tử null không được phép đưa vào. Vì vậy `poll(timeout) == null` là một tín hiệu không lấy được dữ liệu trong khoảng đợi, không phải “đọc được bản ghi null”.

### Ví dụ Java 17+: thử lấy dữ liệu có thời hạn

Tạo file `TimedQueueDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TimedQueueDemo {
    public static void main(String[] args) throws InterruptedException {
        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(2);

        String missing = queue.poll(0, TimeUnit.MILLISECONDS);
        System.out.println("Initially empty: " + (missing == null));

        queue.put("job-1");
        String received = queue.poll(1, TimeUnit.SECONDS);

        System.out.println("Received: " + received);
        System.out.println("Queue empty: " + queue.isEmpty());
    }
}
```

Biên dịch/chạy:

```bash
javac TimedQueueDemo.java
java TimedQueueDemo
```

Đầu ra:

```text
Initially empty: true
Received: job-1
Queue empty: true
```

Trong ví dụ này, `poll(0, ...)` được gọi khi queue chắc chắn rỗng nên trả null ngay, còn lần `poll(1, SECONDS)` thực thi khi đã có `job-1`, nên lấy được phần tử mà không cần chờ. Ví dụ cố ý tạo đầu ra **xác định**, không giả vờ rằng lịch scheduling hay độ trễ luôn cố định.

Nếu có producer/consumer thật chạy trên các thread khác nhau, `poll(200, MILLISECONDS)` có thể trả dữ liệu ngay, sau một phần của khoảng chờ hoặc trả null. Không hứa bất kỳ thứ tự/timing nào trừ khi có cơ chế đồng bộ khác.

### Queue đầy cũng là bài toán “chờ tài nguyên”

Với queue bounded, producer cần chính **dung lượng trống**, không chỉ consumer cần dữ liệu. Dùng `offer(element, timeout, unit)` để tránh producer bị treo vô hạn vì consumer đã ngừng lấy dữ liệu.

`offer` trả `false` khi không chèn được trong khoảng thời gian cho phép. Đó **không** chứng minh queue vĩnh viễn đầy. Ứng dụng phải có chiến lược rõ ràng: retry có giới hạn, backpressure, trả tín hiệu quá tải hoặc từ chối có kiểm soát; không âm thầm đánh mất bản ghi quan trọng.

## 4. Chờ lock với `ReentrantLock.tryLock(timeout, unit)`

`ReentrantLock.lock()` đợi cho đến khi lấy được khóa, không đặt timeout. `tryLock()` không tham số **chỉ thử ngay**. `tryLock(long timeout, TimeUnit unit)` chờ tối đa một khoảng cho lock khả dụng và trả `true` nếu lấy được, `false` nếu không lấy được trong giới hạn.

**Quy tắc đặc biệt quan trọng:** chỉ `unlock()` khi **đã thật sự lấy được lock**. Nếu `tryLock` trả `false` hoặc bị `InterruptedException` trước khi acquire, thread đó không phải owner.

Mẫu sử dụng:

```java
// Snippet: lock đã là một ReentrantLock.
boolean acquired = lock.tryLock(200, TimeUnit.MILLISECONDS);
if (!acquired) {
    return; // Tùy hợp đồng của phương thức bao quanh.
}
try {
    // Xử lý trạng thái được lock bảo vệ.
} finally {
    lock.unlock();
}
```

Snippet trên **không phải chương trình độc lập**: `lock` và phương thức bao quanh chưa được khai báo.

### Ví dụ Java 17+: timeout khi lock đang bị giữ

Tạo file `TimedLockDemo.java`:

```java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class TimedLockDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        lock.lock(); // Thread main giữ lock trong suốt lúc worker thử.

        try {
            Thread worker = new Thread(() -> {
                boolean acquired = false;
                try {
                    acquired = lock.tryLock(50, TimeUnit.MILLISECONDS);
                    System.out.println("Worker acquired: " + acquired);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Worker interrupted");
                } finally {
                    if (acquired) {
                        lock.unlock();
                    }
                }
            }, "worker");

            worker.start();
            worker.join(); // Main vẫn giữ lock, worker chỉ được đợi 50 ms.
        } finally {
            lock.unlock();
        }

        System.out.println("Main released lock");
    }
}
```

Chạy:

```bash
javac TimedLockDemo.java
java TimedLockDemo
```

Đầu ra nếu không có thread bên ngoài interrupt worker:

```text
Worker acquired: false
Main released lock
```

Main cố ý giữ khóa trong khi đợi worker kết thúc. Worker **không thể** lấy khóa do main giữ, nên `tryLock(50, MILLISECONDS)` trả false sau khi hết khoảng chờ (không cần đúng chính xác 50 ms). Nhờ worker **có timeout**, `join()` của main có thể hoàn tất. Nếu worker dùng `lock()` không timeout thì chương trình mẫu có thể deadlock: main chờ worker, worker chờ lock do main giữ.

Đây là ví dụ kiểm soát thời gian chờ, **không phải** mẫu khuyến nghị “vừa giữ lock vừa join worker” trong ứng dụng thật. Thực tế nên tránh giữ khóa trong lúc chờ công việc khác cần khóa đó.

### Fairness và interrupt

`tryLock(timeout, unit)` của `ReentrantLock` có thể bị ngắt và ném `InterruptedException`. Với fair lock, cơ chế chờ có xét chính sách ưu tiên người chờ lâu của lock, nhưng fairness không bảo đảm scheduler công bằng tuyệt đối. `tryLock()` không timeout có những khác biệt về barging trên fair lock; không dùng kết quả thử khóa để chứng minh thứ tự nghiệp vụ.

## 5. Chờ permit bằng `Semaphore.tryAcquire(timeout, unit)`

`Semaphore` thường biểu diễn số slot/tài nguyên có thể dùng đồng thời, ví dụ tối đa 4 kết nối. `acquire()` chờ permit khi chưa có; `tryAcquire(timeout, unit)` cho phép chờ có giới hạn.

```java
// Snippet: permits là Semaphore đã khai báo.
if (!permits.tryAcquire(100, TimeUnit.MILLISECONDS)) {
    // Không có permit trong ngân sách chờ.
    return;
}
try {
    // Dùng slot tài nguyên.
} finally {
    permits.release();
}
```

Khác `ReentrantLock`, `Semaphore` không gắn permit với owner thread theo cùng mô hình; code cần thiết kế ai chịu trách nhiệm release. **Không release khi chưa acquire thành công**, vì có thể làm tăng sai số permit và phá giới hạn tài nguyên.

### Ví dụ Java 17+: giới hạn hai tài nguyên

Tạo file `TimedSemaphoreDemo.java`:

```java
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class TimedSemaphoreDemo {
    public static void main(String[] args) throws InterruptedException {
        Semaphore slots = new Semaphore(2);

        boolean first = slots.tryAcquire(0, TimeUnit.MILLISECONDS);
        boolean second = slots.tryAcquire(0, TimeUnit.MILLISECONDS);
        boolean third = slots.tryAcquire(0, TimeUnit.MILLISECONDS);

        System.out.println("First acquired: " + first);
        System.out.println("Second acquired: " + second);
        System.out.println("Third acquired: " + third);

        if (first) {
            slots.release();
        }
        if (second) {
            slots.release();
        }
        System.out.println("Available permits: "
                + slots.availablePermits());
    }
}
```

Chạy:

```bash
javac TimedSemaphoreDemo.java
java TimedSemaphoreDemo
```

Đầu ra:

```text
First acquired: true
Second acquired: true
Third acquired: false
Available permits: 2
```

Không có scheduling tranh chấp trong ví dụ nên kết quả được xác định. Mã chỉ minh họa permit; thực tế cần tính cả **vòng đời của tài nguyên vật lý**. Nếu acquire permit nhưng không mở được kết nối, `finally` vẫn phải nhả permit khi appropriate; nếu đã mở kết nối thì phải đóng kết nối theo hợp đồng riêng của nó.

## 6. Chờ tín hiệu với `CountDownLatch.await(timeout, unit)`

`CountDownLatch` chờ một **mốc sự kiện**, không đợi một giá trị queue. Các API:

- `await()`: chờ vô hạn đến khi count về 0 hoặc thread bị interrupt.
- `await(timeout, unit)`: trả về **`true`** nếu latch đạt 0 trước khi kết thúc chờ, **`false`** nếu timeout khi count chưa về 0.
- `countDown()`: giảm count nếu lớn hơn 0; latch đã về 0 sẽ giữ trạng thái mở, không dùng lại để đếm chu kỳ khác.

```java
// Snippet: latch đã khai báo là CountDownLatch.
boolean ready = latch.await(300, TimeUnit.MILLISECONDS);
if (!ready) {
    // Chưa thấy sự kiện hoàn tất trong thời gian được phép chờ.
}
```

**Lỗi thường gặp:** gọi `await(timeout)` rồi mặc định code sau đó có quyền dùng dữ liệu đã chuẩn bị. Phải kiểm tra `true/false`. `false` không có nghĩa producer sẽ không bao giờ hoàn tất; một thread khác có thể gọi `countDown()` ngay sau đó.

Dùng latch để biểu diễn “tất cả worker đã tới mốc sẵn sàng”, “cấu hình đã được nạp xong”, hoặc “N phần việc đã hoàn tất”. Nhưng nếu một worker chết trước khi `countDown()`, latch có thể không về 0; nên đặt countDown trong `finally` nếu ngữ nghĩa của latch là “worker đã kết thúc dù thành công hay thất bại”, hoặc công bố trạng thái lỗi riêng nếu cần phân biệt thành công.

## 7. `Condition.awaitNanos` và vòng lặp điều kiện

Khi tự quản lý một queue/điều kiện dùng `Lock` và `Condition`, cần cẩn thận với:

1. **Spurious wakeup:** một lần `await` có thể thức dậy mà điều kiện chưa đúng.
2. **Signal không đồng nghĩa điều kiện đã đúng:** có thể có nhiều thread chờ, thread khác đã tiêu thụ dữ liệu.
3. **Timeout tổng:** sau một lần thức dậy, không được **reset** ngân sách chờ về giá trị ban đầu nếu người dùng chỉ cấp một khoảng chờ chung.

`Condition.awaitNanos(long nanosTimeout)` trả về **ước lượng số nano-giây còn lại**; có thể không dương khi đã hết hạn. Để xử lý đúng, phải giữ lock khi kiểm tra predicate, đợi trong `while` và dùng số còn lại cho lần đợi tiếp theo.

### Java 17+: mailbox một phần tử với timeout

Tạo tệp `TimedMailbox.java`:

```java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TimedMailbox {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition hasMessage = lock.newCondition();
    private String message;

    public void put(String value) {
        lock.lock();
        try {
            if (message != null) {
                throw new IllegalStateException("Mailbox full");
            }
            message = value;
            hasMessage.signal();
        } finally {
            lock.unlock();
        }
    }

    public String receive(long timeout, TimeUnit unit)
            throws InterruptedException {
        long remaining = unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (message == null) {
                if (remaining <= 0L) {
                    return null;
                }
                remaining = hasMessage.awaitNanos(remaining);
            }

            String received = message;
            message = null;
            return received;
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        TimedMailbox box = new TimedMailbox();
        System.out.println("Empty: "
                + (box.receive(0, TimeUnit.MILLISECONDS) == null));
        box.put("hello");
        System.out.println("Received: "
                + box.receive(1, TimeUnit.SECONDS));
    }
}
```

Biên dịch/chạy:

```bash
javac TimedMailbox.java
java TimedMailbox
```

Đầu ra:

```text
Empty: true
Received: hello
```

**Giải thích:**

- `message` chỉ được truy cập khi giữ **cùng một lock**; không đọc/ghi ngoài đồng bộ.
- `while (message == null)` kiểm tra lại predicate sau mỗi lần tỉnh dậy, kể cả spurious wakeup.
- `remaining` được cập nhật từ `awaitNanos` nên nếu thức dậy nhiều lần, thời gian đợi **không bắt đầu lại từ đầu**.
- `lockInterruptibly()` cho phép thread bị interrupt trong lúc chờ lấy lock; `awaitNanos` cũng có thể bị interrupt.
- `finally` bảo đảm nhả lock dù nhận được dữ liệu, timeout hoặc exception.
- Demo gọi lần đầu với timeout bằng 0 (queue rỗng), lần sau khi dữ liệu đã được đặt vào mailbox, nên không phụ thuộc timing của thread khác.

**Một giới hạn có chủ đích:** `put` ở đây từ chối khi mailbox đang đầy thay vì chờ có slot; mục tiêu ví dụ là **chờ dữ liệu với một Condition**. Nếu producer cũng cần chờ cho tới khi mailbox trống, phải thiết kế thêm điều kiện `notFull`, predicate và timeout phù hợp, hoặc dùng `ArrayBlockingQueue` có sẵn để tránh tự viết một concurrent queue đầy đủ.

### Timeout khi lấy lock và khi đợi condition

`receive()` ở trên dùng `lockInterruptibly()`, nghĩa là **thời gian chờ để giành lock chưa được tính vào timeout của `awaitNanos`**. Nếu API nghiệp vụ hứa **toàn bộ lời gọi receive không quá một ngân sách**, cần dùng `tryLock(remaining, unit)` để tính cả thời gian chờ khóa, sau đó truyền **thời gian còn lại** tới `awaitNanos`.

Đây là khác biệt quan trọng giữa “timeout của **một bước chờ**” và “deadline của **toàn bộ thao tác**”.

## 8. `Future.get(timeout, unit)`: chờ kết quả không có nghĩa hủy task

Khi gửi một `Callable` qua `ExecutorService`, ta có thể đợi kết quả bằng `Future.get(timeout, unit)`. Nếu task chưa hoàn tất khi hết thời gian, lệnh ném `TimeoutException`. **Future vẫn có thể đang chạy.** Chỉ `cancel(...)` theo chính sách ứng dụng mới gửi yêu cầu hủy, và ngay cả `cancel(true)` cũng không bảo đảm task lập tức dừng nếu code không hợp tác với interrupt.

### Java 17+: timeout được tạo có kiểm soát

Tạo file `FutureTimeoutDemo.java`:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureTimeoutDemo {
    public static void main(String[] args)
            throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch gate = new CountDownLatch(1);

        try {
            Callable<String> task = () -> {
                gate.await();
                return "done";
            };
            Future<String> future = executor.submit(task);

            try {
                future.get(20, TimeUnit.MILLISECONDS);
                System.out.println("Unexpected completion");
            } catch (TimeoutException expected) {
                System.out.println("Timed out while waiting");
            }

            gate.countDown(); // Cho phép task thực sự kết thúc.
            System.out.println("Result after release: " + future.get());
        } finally {
            gate.countDown(); // Tránh kẹt worker khi main rời try sớm.
            executor.shutdown();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac FutureTimeoutDemo.java
java FutureTimeoutDemo
```

Đầu ra khi không có interrupt ngoài và executor hoạt động bình thường:

```text
Timed out while waiting
Result after release: done
```

Vì `gate` chưa mở trong khoảng chờ đầu tiên, task **không thể** trả về kết quả trước khi main gọi `gate.countDown()`. Vì vậy lần `get(20, MILLISECONDS)` đầu phải timeout; sau khi mở gate, `get()` tiếp theo lấy kết quả. Lệnh `get` không tự hủy task.

Các tình huống khác:

- Task ném exception: `get` có thể ném `ExecutionException` và chứa nguyên nhân của task.
- Task bị cancel: `get` ném `CancellationException`.
- Thread gọi `get` bị interrupt: `get` có thể ném `InterruptedException`.
- Hết thời gian nhưng task còn chạy: `get` ném `TimeoutException`; caller phải quyết định retry, cancel hoặc tiếp tục xử lý khác.

Nếu sử dụng `cancel(true)`, hãy làm cho task **hợp tác với interrupt**, chẳng hạn thoát khi thao tác chờ ném `InterruptedException` hoặc kiểm tra cờ interrupt trong vòng xử lý phù hợp. Không dùng `Thread.stop()` để cưỡng ép hủy.

## 9. `Thread.join(timeout)`: chờ thread kết thúc có giới hạn

`Thread.join()` không timeout sẽ chờ thread đích kết thúc. `Thread.join(long millis)` hoặc overload có nano-giây cho phép chờ một khoảng có giới hạn, nhưng **trả `void`**, không trả true/false. Sau timed join, phải kiểm tra `worker.isAlive()` nếu muốn biết worker còn chạy không.

Các bẫy:

- `join(0)` **là chờ vô hạn**, không phải thử ngay.
- `join(100)` không làm worker bị hủy khi 100 ms trôi qua.
- `join(timeout)` có thể trở về vì worker đã kết thúc hoặc vì thời gian chờ hết.
- `worker.isAlive()` là snapshot trạng thái; sau khi kiểm tra, worker có thể kết thúc ngay.
- `join` có thể ném `InterruptedException` khi **thread đang chờ** bị interrupt; nó không tự interrupt worker.

Dùng `Future` thường tự nhiên hơn khi cần quản lý kết quả, exception, timeout và cancellation của task, thay vì thao tác trực tiếp với vòng đời `Thread`.

## 10. Một deadline chung cho nhiều lần chờ

Giả sử API cho **500 ms để lấy dữ liệu và lấy lock**. Nếu gọi `queue.poll(500ms)` rồi `lock.tryLock(500ms)`, tổng thời gian **có thể vượt xa 500 ms**, vì mỗi lời gọi nhận **ngân sách riêng**. Nếu tiếp tục retry với 500 ms mỗi lần, thời gian tổng còn có thể kéo dài không giới hạn.

Cần tính **thời gian còn lại**:

```java
long budgetNanos = TimeUnit.MILLISECONDS.toNanos(500);
long started = System.nanoTime();

// Sau mỗi giai đoạn:
long elapsed = System.nanoTime() - started;
long remaining = budgetNanos - elapsed;

if (remaining <= 0L) {
    // Đã hết ngân sách; không bắt đầu thêm một lần chờ dài.
}
```

Đây là snippet về cách tính ngân sách; chưa có phương thức xử lý được khai báo. `System.nanoTime()` phù hợp để đo **thời gian trôi qua** trong một JVM, vì nó là đồng hồ đơn điệu tương đối cho việc đo khoảng thời gian, không dùng để suy ra ngày giờ thực tế.

**Tại sao không dùng `System.currentTimeMillis()`?** Wall clock có thể bị chỉnh bởi NTP, quản trị viên hoặc đồng bộ thời gian; chênh lệch của nó không phải lựa chọn tối ưu để đo deadline tương đối.

**Lưu ý về overflow:** phép trừ `now - start` với `nanoTime` thích hợp để đo khoảng cách khi ngân sách nhỏ hơn khoảng 2^63 ns (xấp xỉ 292 năm). Với timeout rất lớn, cần giới hạn hợp lý và tránh làm tràn phép cộng `start + timeout`. Các API `TimeUnit` và `Duration` nên được dùng nhất quán về đơn vị; không tự nhân milliseconds × 1_000_000 khi giá trị có thể lớn đến mức overflow.

### Java 17+: chung một ngân sách cho queue và lock

Tạo file `SharedDeadlineDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class SharedDeadlineDemo {
    static boolean processWithinBudget(
            ArrayBlockingQueue<String> queue,
            ReentrantLock lock,
            long budget,
            TimeUnit unit) throws InterruptedException {
        long allowed = unit.toNanos(budget);
        long start = System.nanoTime();

        if (allowed <= 0L) {
            return false;
        }

        String message = queue.poll(allowed, TimeUnit.NANOSECONDS);
        if (message == null) {
            return false;
        }

        long remaining = allowed - (System.nanoTime() - start);
        if (remaining <= 0L) {
            return false;
        }

        if (!lock.tryLock(remaining, TimeUnit.NANOSECONDS)) {
            return false;
        }

        try {
            System.out.println("Processed: " + message);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ArrayBlockingQueue<String> queue =
                new ArrayBlockingQueue<>(1);
        ReentrantLock lock = new ReentrantLock();

        System.out.println("Empty: " + !processWithinBudget(
                queue, lock, 1, TimeUnit.MILLISECONDS));

        queue.put("task-1");
        boolean processed = processWithinBudget(
                queue, lock, 1, TimeUnit.SECONDS);
        System.out.println("Success: " + processed);
    }
}
```

Chạy:

```bash
javac SharedDeadlineDemo.java
java SharedDeadlineDemo
```

Một đầu ra có thể gặp:

```text
Empty: true
Processed: task-1
Success: true
```

**Điều nào được bảo đảm?**

- Lời gọi đầu tiên xử lý queue rỗng: `poll` hết thời gian và trả null, nên in `Empty: true`.
- Lời gọi thứ hai đã có dữ liệu và lock không bị tranh chấp, nhưng **đầu ra thành công vẫn phụ thuộc** việc thread có thể được scheduler trì hoãn bất thường giữa `poll` và phép tính `remaining`. Với tải bình thường sẽ thường thành công; không được khẳng định `Processed`/`Success: true` là tất yếu với mọi lịch.
- Nếu ngân sách đã hết trước khi lấy lock, phương thức trả false. **Trong ví dụ này, phần tử đã được lấy ra khỏi queue** trước khi thử lock: nếu xử lý thất bại, có nguy cơ dữ liệu bị bỏ mất. Đây là **giới hạn nghiệp vụ quan trọng**, không phải mẫu queue processing đầy đủ cho production.

**Sửa ở mức thiết kế:** nếu lấy dữ liệu đồng nghĩa “nhận trách nhiệm xử lý”, phải có cơ chế acknowledgment, đưa lại queue có kiểm soát, lease/visibility timeout, hoặc thiết kế lấy tài nguyên **trước** khi dequeue để không đánh mất item. Thứ tự lấy tài nguyên cũng phải tránh deadlock và tắc nghẽn. Không xem một hàm trả false là đủ nếu nó đã tạo tác dụng phụ một phần.

### “Hard deadline” của toàn bộ công việc còn khó hơn

Cùng một time budget cho các lần **blocking** vẫn không biến mọi đoạn code thành hard real-time. CPU có thể trì hoãn thread sau khi lấy lock; xử lý tác vụ hoặc I/O không hỗ trợ timeout có thể vượt hạn. Cần truyền deadline xuống từng tầng, đặt timeout ở các API có hỗ trợ và xác định quy tắc kiểm tra kết quả khi thời hạn đã qua.

## 11. Xử lý `InterruptedException` đúng cách

Các thao tác blocking như `queue.poll(timeout, unit)`, `lock.tryLock(timeout, unit)`, `latch.await(timeout, unit)`, `Condition.awaitNanos` và `Future.get(timeout, unit)` có thể ném `InterruptedException`.

Có hai cách xử lý phổ biến:

1. **Truyền tiếp:** khai báo `throws InterruptedException` để caller quyết định dừng công việc và giải phóng các tài nguyên liên quan.
2. **Bắt ở tầng không thể truyền:** hoàn tất dọn dẹp cần thiết, gọi `Thread.currentThread().interrupt()` để phục hồi interrupt status nếu không tiếp tục ném ngoại lệ, rồi trả kết quả phù hợp.

```java
// Snippet: worker là Thread khác, trả false nếu bị interrupt.
static boolean waitUntilDone(Thread worker) {
    try {
        worker.join();
        return true;
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
    }
}
```

Snippet là helper trong class khác, không phải chương trình độc lập. `false` chỉ nói **không hoàn tất việc chờ**, không chứng minh worker đã kết thúc hoặc đã bị hủy.

Đừng nuốt `InterruptedException` rồi tiếp tục chờ vô hạn theo cùng chính sách mà không giải thích. Khi một API có timeout trả `false` hoặc `null`, điều đó khác với `InterruptedException`: một bên là hết ngân sách chờ, bên kia là tín hiệu interrupt; hệ thống có thể cần xử lý khác nhau.

## 12. Những lỗi thường gặp

| Sai lầm | Tại sao sai | Cách sửa |
| --- | --- | --- |
| Dùng `Thread.sleep(100)` để “chờ dữ liệu” | Không xác minh dữ liệu đã có; không thiết lập giao thức đồng bộ | Dùng `BlockingQueue`, latch, Future hoặc Condition phù hợp |
| `poll(timeout) == null` thì kết luận producer đã chết | Timeout chỉ nói chưa nhận được phần tử trong khoảng chờ | Theo dõi trạng thái producer/lỗi qua kênh riêng |
| `tryLock` trả false nhưng vẫn `unlock()` | Chưa sở hữu lock; có thể ném `IllegalMonitorStateException` | Dùng biến `acquired`, unlock chỉ trong nhánh thành công |
| `Semaphore.tryAcquire` thất bại vẫn `release()` | Tăng permit sai, phá giới hạn tài nguyên | Release đúng số permit đã acquire thành công |
| `Future.get(timeout)` hết hạn thì task đã bị hủy | Task có thể vẫn đang chạy | Xác định cancellation policy, `cancel(true)` nếu phù hợp |
| `Thread.join(0)` là non-blocking | `join(0)` chờ vô hạn | Dùng `isAlive()` để quan sát trạng thái, hoặc timed join dương |
| Dùng `if` thay `while` quanh `Condition.await` | Có spurious wakeups và signal không bảo đảm predicate | Kiểm tra predicate trong vòng `while` |
| Reset timeout sau mỗi lần wakeup | Có thể chờ tổng thời gian dài hơn ngân sách | Dùng `awaitNanos` hoặc tính thời gian còn lại |
| Hai bước chờ mỗi bước dùng toàn bộ timeout | Tổng thời gian chờ có thể gần gấp đôi | Truyền chung deadline/remaining |
| Dùng `currentTimeMillis` để đo elapsed | Wall clock có thể thay đổi | Dùng `System.nanoTime()` cho khoảng thời gian |
| Timeout được coi là lỗi dữ liệu | Có thể là tranh chấp/tải tạm thời | Thiết kế retry, backpressure, cảnh báo phù hợp |
| Hết timeout sau khi lấy dữ liệu thì bỏ item | Tạo mất dữ liệu hoặc xử lý không nhất quán | Thiết kế ownership, ack và rollback/requeue phù hợp |
| Nuốt interrupt | Thread có thể không dừng khi được yêu cầu | Propagate hoặc restore interrupt status |
| Timeout nhỏ được xem là cam kết hard real-time | Scheduling và các bước non-blocking khác vẫn tốn thời gian | Thiết kế SLA, đo tail latency và deadline cuối-đến-cuối |

## 13. Lựa chọn API theo điều kiện đang chờ

| Đang chờ gì? | API JDK phổ biến | Kết quả khi timeout |
| --- | --- | --- |
| Một **phần tử dữ liệu** trong queue | `BlockingQueue.poll(timeout, unit)` | `null` |
| **Dung lượng trống** của queue | `BlockingQueue.offer(e, timeout, unit)` | `false` |
| Một **lock** | `ReentrantLock.tryLock(timeout, unit)` | `false` |
| Một hay nhiều **permit** | `Semaphore.tryAcquire(timeout, unit)` | `false` |
| Một **mốc đồng bộ** | `CountDownLatch.await(timeout, unit)` | `false` |
| Một **predicate dưới lock** | `Condition.awaitNanos(remaining)` | Ước lượng remaining không dương nếu hết thời gian; luôn kiểm tra predicate |
| Kết quả từ **task** | `Future.get(timeout, unit)` | Ném `TimeoutException` |
| **Thread** kết thúc | `Thread.join(timeout)` | Trả `void`; kiểm tra `isAlive()` |
| Kết quả bất đồng bộ theo pipeline | `CompletableFuture.orTimeout(...)` / `completeOnTimeout(...)` | Hoàn tất future theo chính sách timeout của API, không mặc nhiên dừng side effect nguồn |

**Lưu ý về `CompletableFuture`:** `orTimeout` làm future hoàn tất ngoại lệ nếu chưa xong đúng hạn; `completeOnTimeout` hoàn tất với giá trị dự phòng khi hết hạn. Cả hai áp dụng lên **trạng thái của future**, không phải cách cưỡng ép ngừng mọi tác vụ thực thi bên dưới. `CompletableFuture.get(timeout, unit)` là timeout riêng cho thao tác đợi của caller, không tự biến future thành kết quả timeout.

## 14. Checklist cho thiết kế có timeout

1. **Xác định điều kiện thật:** cần dữ liệu, permit, lock, tín hiệu, thread đã kết thúc hay kết quả task?
2. **Chọn API có timeout tương ứng:** `poll`, `tryLock`, `tryAcquire`, `await`, `Future.get` hoặc timed join.
3. **Đọc đúng kết quả:** `null`/`false`/`TimeoutException`/`void` có ý nghĩa khác nhau.
4. **Định nghĩa tổng ngân sách:** timeout của một thao tác hay deadline cho toàn bộ workflow? Truyền thời gian **còn lại** qua các bước.
5. **Dùng `nanoTime` cho elapsed**, không tính deadline tương đối bằng wall clock nếu tránh được.
6. **Cân nhắc spurious wakeup:** kiểm tra predicate bằng `while`; không reset ngân sách vô hạn.
7. **Giải phóng tài nguyên chính xác:** `finally` cho lock, permit và tài nguyên đã acquire; không release khi thất bại.
8. **Phân biệt timeout và cancellation:** nếu không nhận kết quả, task/producer có còn chạy không? Có cần gửi tín hiệu hủy hợp tác?
9. **Bảo vệ dữ liệu đã lấy/đã ghi:** khi timeout ở bước sau, item hoặc side effect trước đó phải được xử lý đúng, không tự động biến mất.
10. **Xử lý interrupt riêng:** propagate hoặc phục hồi cờ nếu bắt; tránh swallowing.
11. **Kiểm thử cả thành công và hết hạn:** tạo điều kiện test có kiểm soát bằng latch/lock, không dựa vào sleep tình cờ.
12. **Đo dưới tải thực:** tỉ lệ timeout, queue latency, lock contention, tail latency, số lần retry và khả năng hệ thống hồi phục sau quá tải.

**Ghi nhớ:** timeout chỉ **giới hạn nỗ lực chờ** một điều kiện, không bảo đảm tác vụ nguồn đã hoàn tất hay đã bị hủy. Java cung cấp các API chờ có thời hạn cho queue, lock, semaphore, latch, condition, future và thread. Sử dụng đúng return value/exception, một ngân sách thời gian chung khi cần, cleanup trong `finally` và giao thức cancellation/ownership phù hợp sẽ giúp tránh treo vô hạn mà không làm mất tính đúng đắn của chương trình đồng thời.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-coordination-007">Lock + Condition cho nhiều điều kiện chờ</a>

<details>
<summary>Xem chi tiết</summary>

# Lock + Condition cho nhiều điều kiện chờ trong Java Concurrency

Trong một cấu trúc đồng thời, nhiều nhóm thread có thể phải chờ **những điều kiện khác nhau trên cùng một trạng thái chia sẻ**. Ví dụ, producer đợi “hàng đợi **chưa đầy**”, consumer đợi “hàng đợi **không rỗng**”, còn những thread khác có thể đợi “hệ thống **đã đóng**”. Nếu dùng một hàng đợi chờ chung và đánh thức sai nhóm, chương trình có thể làm nhiều thread thức dậy vô ích, thậm chí mắc lỗi tiến triển khi sử dụng `notify()` không phù hợp.

**`ReentrantLock` + nhiều `Condition`** cho phép một khóa bảo vệ *toàn bộ trạng thái chung*, đồng thời tạo **các hàng chờ riêng** cho từng predicate (điều kiện logic). Thread đợi trên `notEmpty` chỉ được báo hiệu bởi `notEmpty.signal()` hoặc `notEmpty.signalAll()`; thread đợi `notFull` được báo hiệu trên `notFull`.

Bài học tập trung vào Java core: `java.util.concurrent.locks.Lock`, `ReentrantLock`, `Condition`, cơ chế khóa, chờ/đánh thức và quản lý vòng đời.

## 1. Vì sao cần nhiều Condition?

Hãy xét một hàng đợi có dung lượng cố định:

- **Producer:** chỉ thêm khi `size < capacity`; nếu đầy, phải chờ.
- **Consumer:** chỉ lấy khi `size > 0`; nếu rỗng, phải chờ.
- **Đóng hàng đợi:** tất cả thread đang chờ phải thức dậy để kiểm tra `closed` và hoàn tất/thoát theo hợp đồng.

Nếu dùng một intrinsic monitor và `wait()`/`notify()`, các nhóm cùng nằm trong một wait set. Một lần `notify()` có thể đánh thức một producer khác khi hàng đợi vẫn đầy, trong khi consumer cần thức dậy để lấy dữ liệu. `notifyAll()` thường giải quyết nguy cơ chọn sai nhóm nhưng có thể khiến nhiều thread không đủ điều kiện cũng phải tranh khóa rồi đợi lại.

Với `Condition` ta biểu diễn rõ:

```java
private final ReentrantLock lock = new ReentrantLock();
private final Condition notEmpty = lock.newCondition();
private final Condition notFull = lock.newCondition();
```

Đây là đoạn khai báo trong class, không phải chương trình chạy độc lập. **Hai `Condition` dùng chung một lock** nhưng có **hai condition queues** khác nhau. Khi thêm phần tử: `notEmpty.signal()`; khi lấy phần tử: `notFull.signal()`. Khi đóng và mọi waiter cần tự kiểm tra lại: `signalAll()` trên **cả hai**.

> Quy tắc cốt lõi: *Một lock bảo vệ trạng thái; mỗi Condition đại diện cho một lý do chờ riêng.*

## 2. Phân biệt Lock, Condition và predicate

| Thành phần | Vai trò | Không phải là |
|---|---|---|
| `ReentrantLock lock` | Loại trừ truy cập đồng thời tới state và tạo quan hệ bộ nhớ đúng theo Java Memory Model. | Hàng đợi dữ liệu hoặc cờ điều kiện. |
| `Condition notEmpty` | Hàng chờ của thread cần đợi có dữ liệu. | Biến tự lưu số phần tử hoặc số lần signal. |
| `Condition notFull` | Hàng chờ của thread cần đợi có dung lượng. | Một khóa riêng bảo vệ `size`. |
| Predicate `!queue.isEmpty()` | Sự thật hiện tại quyết định consumer có được lấy phần tử không. | Tín hiệu `signal()`. |
| Predicate `queue.size() < capacity` | Sự thật hiện tại quyết định producer có được thêm không. | Một quyền tiếp tục được “đặt sẵn” bởi signal. |
| `closed` | Trạng thái vòng đời cho phép waiter quyết định thoát hoặc từ chối thao tác. | Tín hiệu hủy tự động tới mọi thread. |

**Predicate là nguồn sự thật; Condition chỉ hỗ trợ chờ để kiểm tra lại predicate.** Một lời `signal()` không gắn payload, không tích lũy permit và không tự đặt điều kiện thành `true`.

Các `Condition` khác nhau có thể chung một `ReentrantLock` vì state `queue/closed` là *một invariant chung*: không được vượt dung lượng, không lấy từ queue rỗng, không nhận thêm sau khi đóng.

## 3. await(): nhả khóa khi chờ và lấy lại khóa trước khi tiếp tục

Đối với `Condition` được tạo bởi `ReentrantLock.newCondition()`, luồng thường đi theo chu trình:

1. `lock.lock()` hoặc `lock.lockInterruptibly()` để sở hữu **associated lock**.
2. Kiểm tra predicate dưới khóa.
3. Nếu predicate sai, gọi `condition.await()`. `await()` nhả associated lock (kể cả hold count nếu đang tái nhập) và đưa thread vào condition queue.
4. Thread khác lấy được lock, sửa state và gọi `condition.signal()`/`signalAll()`.
5. Waiter được báo hiệu chuyển sang cạnh tranh lấy lock; nó **chưa chạy tiếp ngay**.
6. Sau khi lấy lại associated lock, `await()` mới có thể trở về (hoặc xử lý đường interrupt), và waiter **kiểm tra lại predicate trong `while`**.

```text
Producer                              Consumer
--------                              --------
lock.lock()
while (queueFull) {
    notFull.await()
       | nhả lock
       v                              lock.lock()
     WAITING                          remove item
       ^                              notFull.signal()
       |                              lock.unlock()
       +---- tranh lock --------------+
    lấy lại lock
    kiểm tra lại queueFull
}
add item
notEmpty.signal()
lock.unlock()
```

Điểm dễ nhầm là **`signal()` không trao ngay lock cho thread được đánh thức**. Thread phát tín hiệu còn giữ lock cho tới khi unlock; các thread khác cũng có thể tranh lấy lock trước waiter.

`await()` trên `Condition` của `ReentrantLock` hoặc `signal()`/`signalAll()` khi **chưa giữ associated lock** sẽ ném `IllegalMonitorStateException`. Phải dùng đúng lock đã tạo Condition, không chỉ cầm một lock bất kỳ.

## 4. Mẫu chuẩn: nhiều Condition với các vòng while riêng

Mã minh họa sau giả định toàn bộ biến thuộc cùng một class và mọi thao tác lên queue/closed đều dùng cùng lock:

```java
// Producer: đợi còn chỗ.
lock.lockInterruptibly();
try {
    while (queue.size() == capacity && !closed) {
        notFull.await();
    }
    if (closed) {
        throw new IllegalStateException("queue closed");
    }
    queue.addLast(item);
    notEmpty.signal();
} finally {
    lock.unlock();
}
```

```java
// Consumer: đợi có dữ liệu hoặc queue đóng.
lock.lockInterruptibly();
try {
    while (queue.isEmpty() && !closed) {
        notEmpty.await();
    }
    if (queue.isEmpty()) {
        return null; // Quy ước: đã đóng và không còn dữ liệu.
    }
    T item = queue.removeFirst();
    notFull.signal();
    return item;
} finally {
    lock.unlock();
}
```

Đây là các **trích đoạn**, không phải hai chương trình độc lập: cần định nghĩa `lock`, `notFull`, `notEmpty`, `queue`, `capacity`, `closed`, `item`, kiểu `T`, và khai báo `InterruptedException` ở phương thức tương ứng.

Đặc điểm chính:

- **Hai predicate, hai Condition**, nhưng **một khóa chung**.
- Thao tác **đọc predicate → await hoặc thực thi → sửa state → signal** đều được thực hiện dưới khóa.
- `while`, không phải `if`, vì thức dậy giả (*spurious wakeup*), tranh chấp giữa waiter và state thay đổi sau tín hiệu.
- `finally` luôn mở khóa sau khi thực thi thành công, `return` hoặc exception.
- Điều kiện `closed` phải được **xử lý riêng** để thread không mắc kẹt khi queue không còn nhận thêm dữ liệu.

## 5. Ví dụ 1: One-slot queue với notEmpty và notFull

Ví dụ hoàn chỉnh dùng một producer, một consumer và hộp thư chỉ có **một vị trí**. Code minh họa chính xác cách `Condition` chia waiter theo lý do chờ. Một bản queue production cần thêm chính sách timeout, capacity, xử lý lỗi và lifecycle; ở đây mục tiêu là hiểu monitor/condition.

Tạo file **`OneSlotConditions.java`**:

```java
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class OneSlotConditions {
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
                    throw new IllegalStateException("closed");
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
                    return null;
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
                for (int i = 1; i <= 3; i++) {
                    channel.put(i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
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

Lệnh chạy JDK 17+:

```bash
javac OneSlotConditions.java
java OneSlotConditions
```

Đầu ra khi không có interrupt bên ngoài:

```text
Received: 1
Received: 2
Received: 3
Finished
```

**Vì sao chương trình chạy đúng bất kể producer hay consumer được lên lịch trước?**

- Nếu producer chạy trước, nó có thể đặt một item; nếu muốn đặt item tiếp khi slot đầy, `notFull.await()` nhả lock để consumer lấy.
- Nếu consumer chạy trước, nó vào `notEmpty.await()` và nhả lock để producer đặt.
- Producer gọi `notEmpty.signal()` khi chuyển queue từ rỗng thành có item; consumer gọi `notFull.signal()` khi giải phóng slot.
- Producer kết thúc và gọi `close()` trong `finally`: mọi waiter trên hai condition queues đều được đánh thức để xem trạng thái `closed`.
- Consumer có thể lấy item cuối **sau khi** `closed = true`, vì `take()` ưu tiên trả phần tử còn tồn. Chỉ khi queue rỗng và đã đóng thì `null` báo kết thúc.
- Main `join()` đủ hai thread trước khi in `Finished`.

Đoạn này không dùng `volatile` cho `closed` hoặc `value`: mọi đường đọc/ghi hai trường đều dưới **cùng ReentrantLock**, cung cấp mutual exclusion và quan hệ bộ nhớ thích hợp.

## 6. Vì sao signal() đúng nhóm lại quan trọng?

Với một `Condition` duy nhất cho cả producer/consumer, một lời `signal()` không biết người được đánh thức đang chờ queue đầy hay rỗng. Một producer có thể đánh thức producer khác khi hàng đợi vẫn đầy, rồi người đó trở lại chờ, trong khi consumer vẫn ngủ: khả năng tiến triển có thể bị cản trở.

Hai `Condition` giúp:

- Khi producer **thêm** item, chỉ consumer cần xét lại `notEmpty`; đánh thức waiter trên `notEmpty`.
- Khi consumer **lấy** item, chỉ producer cần xét lại `notFull`; đánh thức waiter trên `notFull`.
- Khi **đóng**, cả hai nhóm đều cần kiểm tra trạng thái đóng, nên gọi `signalAll()` cho **cả hai** queues.

Đây là cách giảm những lần thức dậy không cần thiết mà vẫn giữ giao thức dễ chứng minh đúng. **Không phải cứ nhiều Condition là cần nhiều Lock**: tạo hai lock khác nhau để bảo vệ cùng `queue.size()` có thể phá invariant nếu các đường cập nhật thiếu phối hợp.

### signal() và signalAll() không có cùng mục đích

| Phương thức | Tác dụng | Dùng khi |
|---|---|---|
| `notEmpty.signal()` | Báo một waiter trong hàng chờ `notEmpty` nếu có. | Vừa thêm một item và chỉ cần một consumer thức dậy để thử lấy. |
| `notFull.signal()` | Báo một waiter trong hàng chờ `notFull` nếu có. | Vừa giải phóng một chỗ và chỉ cần một producer thử đặt. |
| `notEmpty.signalAll()` | Báo mọi waiter chờ `notEmpty`. | Đóng queue hoặc thay đổi trạng thái cho phép nhiều waiter tiến triển/thoát. |
| `notFull.signalAll()` | Báo mọi waiter chờ `notFull`. | Đóng queue hoặc thay đổi trạng thái cần mọi producer xét lại. |

`signal()` trên `notEmpty` **không tự đánh thức** các waiter trên `notFull`. Nó cũng không lưu tín hiệu cho những waiter đến sau. Nếu producer đặt item khi chưa có consumer đợi, consumer đến sau đọc **predicate queue không rỗng** và tiếp tục, không cần “nhận lại” tín hiệu cũ.

## 7. Vì sao luôn dùng while, kể cả khi signal đúng Condition?

Giả sử producer thêm một item và `notEmpty.signal()`. Consumer C1 được đánh thức, nhưng trước khi C1 lấy lại lock, một consumer C2 không đợi có thể lấy lock, lấy item và mở khóa. C1 sau đó tiếp tục mà queue đã **rỗng trở lại**.

Nếu C1 viết `if (queue.isEmpty()) notEmpty.await();`, nó có thể đi tiếp với giả định cũ và gọi `removeFirst()` trên queue rỗng. Với **`while (queue.isEmpty())`**, C1 kiểm tra lại và đợi tiếp. Ngoài ra `Condition.await()` được phép có *spurious wakeup*: thức dậy dù không nhận signal tương ứng với nghiệp vụ.

Mẫu cần nhớ:

```java
lock.lock();
try {
    while (!predicate()) {
        condition.await();
    }
    actUnderSameLock();
} finally {
    lock.unlock();
}
```

Đây là trích đoạn minh họa với `lock`, `condition`, `predicate()`, `actUnderSameLock()` đã khai báo; phương thức chứa nó phải khai báo/xử lý `InterruptedException`.

Không dùng `Thread.sleep()` trong khi giữ lock để “đợi condition”: `sleep()` **không nhả lock**, nên thread cần lock để cập nhật predicate có thể không vào được. `await()` có chức năng **nhả và lấy lại associated lock** phục vụ phối hợp điều kiện.

## 8. Ví dụ 2: hai nhóm chờ cho hai trạng thái khác nhau

Một ứng dụng không có queue cũng có thể cần nhiều Condition: nhóm A đợi hệ thống **ready**, nhóm B đợi hệ thống **stopped**. Đây là hai predicate độc lập nhưng cùng thuộc một đối tượng trạng thái, được bảo vệ bởi một lock.

Tạo file **`TwoConditionGate.java`**:

```java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TwoConditionGate {
    static final class Lifecycle {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition readyCondition = lock.newCondition();
        private final Condition stoppedCondition = lock.newCondition();
        private boolean ready;
        private boolean stopped;

        void awaitReady() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (!ready) {
                    readyCondition.await();
                }
            } finally {
                lock.unlock();
            }
        }

        void awaitStopped() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (!stopped) {
                    stoppedCondition.await();
                }
            } finally {
                lock.unlock();
            }
        }

        void setReady() {
            lock.lock();
            try {
                ready = true;
                readyCondition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        void stop() {
            lock.lock();
            try {
                stopped = true;
                stoppedCondition.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Lifecycle lifecycle = new Lifecycle();
        AtomicInteger readyDone = new AtomicInteger();
        AtomicInteger stoppedDone = new AtomicInteger();

        Thread readyWaiter = new Thread(() -> {
            try {
                lifecycle.awaitReady();
                readyDone.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread stoppedWaiter = new Thread(() -> {
            try {
                lifecycle.awaitStopped();
                stoppedDone.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        readyWaiter.start();
        stoppedWaiter.start();

        lifecycle.setReady();
        lifecycle.stop();

        readyWaiter.join();
        stoppedWaiter.join();

        System.out.println("Ready waiters passed: " + readyDone.get());
        System.out.println("Stopped waiters passed: " + stoppedDone.get());
    }
}
```

```bash
javac TwoConditionGate.java
java TwoConditionGate
```

Kết quả khi không có interrupt bên ngoài:

```text
Ready waiters passed: 1
Stopped waiters passed: 1
```

Hai condition queues phục vụ hai sự kiện logic khác nhau; việc `setReady()` **không** signal `stoppedCondition`. Nếu main đặt state trước khi waiter kịp đợi, waiter sẽ quan sát predicate true trong `while` guard và đi tiếp, không phụ thuộc tín hiệu có người nhận hay không.

Đây là ví dụ tối giản của các cờ **một chiều** (false → true). Nếu lifecycle cho phép reset sang false, cần thiết kế lại giao thức để tránh waiter bỏ lỡ các thế hệ sự kiện (*generations*). Chẳng hạn có thể dùng biến `version`/state machine thay vì dựa vào một boolean.

## 9. Nhiều producer/consumer: tranh chấp và tính nhất quán

Một queue thật có thể có **nhiều** producer và consumer. `signal()` một consumer khi thêm một phần tử thường hợp lý vì chỉ một người lấy được phần tử đó. Khi consumer lấy một phần tử, `signal()` một producer nếu vừa có chỗ trống cũng hợp lý. Nhưng tính đúng đắn đòi hỏi:

1. Mọi method cập nhật `queue` và `closed` phải dùng cùng lock.
2. Mọi waiter dùng vòng `while` quanh predicate tương ứng.
3. Mỗi thao tác làm predicate của nhóm nào thay đổi thì báo cho **condition nhóm ấy**.
4. Khi đóng, **mọi waiter trên tất cả Condition liên quan** phải được đánh thức; một lần `signal()` không đủ.
5. Nếu hủy/interrupt xảy ra, tránh để queue rơi vào trạng thái cập nhật một nửa; xử lý ở ranh giới thao tác đúng hợp đồng.

### Ví dụ 3: Bounded queue với nhiều producer và consumer

Ví dụ này dùng dung lượng ba, ba producer mỗi producer phát 100 số và hai consumer cộng các số nhận được. Sau khi tất cả producer hoàn tất, main đóng queue để consumer còn đợi có thể thoát. Các thread nhận việc theo thứ tự không xác định, nhưng **tổng và số lượng** được kiểm tra được.

Tạo file **`MultiProducerConsumer.java`**:

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class MultiProducerConsumer {
    static final class BoundedQueue {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notEmpty = lock.newCondition();
        private final Condition notFull = lock.newCondition();
        private final Deque<Integer> queue = new ArrayDeque<>();
        private final int capacity;
        private boolean closed;

        BoundedQueue(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be positive");
            }
            this.capacity = capacity;
        }

        void put(int value) throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (queue.size() == capacity && !closed) {
                    notFull.await();
                }
                if (closed) {
                    throw new IllegalStateException("closed");
                }
                queue.addLast(value);
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }

        Integer take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (queue.isEmpty() && !closed) {
                    notEmpty.await();
                }
                if (queue.isEmpty()) {
                    return null;
                }
                Integer value = queue.removeFirst();
                notFull.signal();
                return value;
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
        BoundedQueue queue = new BoundedQueue(3);
        AtomicInteger count = new AtomicInteger();
        AtomicLong sum = new AtomicLong();

        Thread[] consumers = new Thread[2];
        for (int i = 0; i < consumers.length; i++) {
            consumers[i] = new Thread(() -> {
                try {
                    Integer value;
                    while ((value = queue.take()) != null) {
                        count.incrementAndGet();
                        sum.addAndGet(value);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "consumer-" + i);
            consumers[i].start();
        }

        Thread[] producers = new Thread[3];
        for (int i = 0; i < producers.length; i++) {
            final int base = i * 100;
            producers[i] = new Thread(() -> {
                try {
                    for (int value = base + 1; value <= base + 100; value++) {
                        queue.put(value);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "producer-" + i);
            producers[i].start();
        }

        for (Thread producer : producers) {
            producer.join();
        }
        queue.close();
        for (Thread consumer : consumers) {
            consumer.join();
        }

        System.out.println("Count: " + count.get());
        System.out.println("Sum: " + sum.get());
    }
}
```

```bash
javac MultiProducerConsumer.java
java MultiProducerConsumer
```

Kết quả trong lần chạy bình thường, không có interrupt ngoài:

```text
Count: 300
Sum: 45150
```

**Tại sao không bị mất item?** Mọi `put` và `take` bảo vệ cùng `Deque` bằng một lock. Nếu đầy, producer đợi `notFull`; nếu rỗng, consumer đợi `notEmpty`. Mỗi thread sau khi thức dậy kiểm tra lại predicate; do đó không thể lấy item từ queue rỗng hoặc thêm vượt capacity. Sau khi producer hoàn tất, main gọi `close()`, khiến tất cả consumer đang chờ (nếu có) được đánh thức và thoát khi không còn item. Tổng các số từ 1 đến 300 là `300 * 301 / 2 = 45150`, không phụ thuộc cách phân chia công việc giữa hai consumer.

**Giới hạn:** ví dụ không mô tả toàn bộ hành vi khi một producer bị lỗi, khi có request dừng giữa chừng hoặc khi cần rollback; những tình huống đó cần thêm chính sách lifecycle. Trong ứng dụng thông thường, `ArrayBlockingQueue` đã cung cấp một queue giới hạn dung lượng, được kiểm thử kỹ và thường thích hợp hơn việc tự viết.

## 10. Đóng và hủy: phải đánh thức mọi nhóm cần xem lại state

Một lỗi phổ biến là queue có hai Condition, nhưng khi `close()` chỉ gọi `notEmpty.signalAll()`. Các producer đang chờ `notFull` sẽ không nhận tín hiệu đóng, có thể đợi mãi dù không bao giờ được phép thêm item nữa.

Mẫu đóng đúng khi **cả hai nhóm** cần thoát:

```java
lock.lock();
try {
    closed = true;
    notEmpty.signalAll();
    notFull.signalAll();
} finally {
    lock.unlock();
}
```

Đây là trích đoạn trong class có `lock`, `closed`, `notEmpty`, `notFull`; không phải chương trình độc lập.

Các `while` trong `take()`/`put()` phải xét thêm `closed` để có đường thoát, ví dụ `while (empty && !closed)` hoặc `while (full && !closed)`. Nếu predicate chỉ là `while (empty)`, thread vừa được đánh thức bởi `close()` vẫn quay lại chờ vì queue rỗng. **Signal phải đi kèm thay đổi state cho phép waiter quyết định khác trước.**

Trong ứng dụng thực, hợp đồng đóng cần nói rõ: dữ liệu còn trong queue có được xử lý hết không, producer gặp `closed` ném exception hay trả trạng thái gì, các call đang chờ xử lý interrupt/timeout thế nào, và có cần báo lỗi tới caller hay không.

## 11. InterruptedException và lockInterruptibly()

`Condition.await()` là thao tác có thể bị interrupt. Khi bị interrupt trong lúc đợi, nó ném `InterruptedException` theo hợp đồng; trạng thái interrupt thường đã được xóa khi exception được ném. `Condition.await()` của `ReentrantLock` **lấy lại lock trước khi hoàn tất đường ném exception**, vì vậy `finally { lock.unlock(); }` vẫn đúng.

Hai cách phổ biến:

- **Ném tiếp** `InterruptedException` từ method để caller chịu trách nhiệm cancellation.
- Nếu method không ném tiếp, **khôi phục cờ interrupt** bằng `Thread.currentThread().interrupt()` rồi chấm dứt hoặc chuyển việc dọn dẹp theo hợp đồng.

`lock.lockInterruptibly()` có thể bị interrupt **khi đang đợi chiếm lock**, khác với `lock.lock()` vốn không ném `InterruptedException` do cố lấy lock. Khi `lockInterruptibly()` ném exception trước khi lấy lock thì chưa đi vào vùng try/finally chứa `unlock()`, nên mẫu mã đặt `lock.lockInterruptibly();` **trước** `try`.

Đừng nuốt interrupt rồi mặc nhiên coi predicate đã đúng. Một thread được đánh thức vì cancellation có thể không được phép tiếp tục lấy hoặc tạo dữ liệu.

## 12. Timed await: quản lý thời gian còn lại

Với `Condition`, `awaitNanos(long timeoutNanos)` cho phép chờ theo ngân sách thời gian và trả về phần thời gian còn lại (có thể bằng hoặc dưới 0 khi hết). Nó vẫn có thể thức dậy giả hoặc được signal khi predicate đã đổi, nên **vòng while vẫn bắt buộc**.

Mẫu phương thức chờ cổng với timeout, dạng trích đoạn:

```java
boolean awaitReady(long timeoutNanos) throws InterruptedException {
    long remaining = timeoutNanos;
    lock.lockInterruptibly();
    try {
        while (!ready) {
            if (remaining <= 0L) {
                return false;
            }
            remaining = readyCondition.awaitNanos(remaining);
        }
        return true;
    } finally {
        lock.unlock();
    }
}
```

Đoạn này giả định `ready`, `lock`, `readyCondition` thuộc cùng class. Trên mỗi vòng, dùng `remaining` **thay vì cấp lại nguyên timeout**, tránh kéo dài thời gian chờ do bị signal/spurious wakeup nhiều lần. Giá trị trả về **true** nghĩa là predicate `ready` đã đúng khi giữ lock; **false** nghĩa là ngân sách hết trước khi predicate đúng. Không đánh đồng kết quả trả về của một lần await với trạng thái nghiệp vụ.

Với timeout có giới hạn, cần xử lý các trường hợp số học tràn khi thời gian rất lớn hoặc cấu hình không hợp lệ; dùng `TimeUnit` và API chờ có deadline nếu phù hợp. Nếu không cần viết condition logic tùy biến, API `BlockingQueue.poll(timeout, unit)` đã giải quyết sẵn trường hợp đợi item có timeout.

## 13. Quan hệ bộ nhớ (Java Memory Model)

Quy tắc về Lock trong Java tạo quan hệ đồng bộ bộ nhớ tương đương với monitor: một lần **unlock** trên một lock có quan hệ *happens-before* với lần **lock tiếp theo** trên cùng lock theo contract. Vì mọi thread đều đọc/ghi `queue` và `closed` dưới một `ReentrantLock`, state đã được cập nhật trước unlock sẽ được nhìn thấy đúng khi waiter chiếm lock trở lại.

Với nhiều Condition trên cùng lock:

- Các `Condition` **không sở hữu những bản sao khác nhau của queue**: chúng chỉ phân nhóm waiter.
- Các thay đổi state được công bố nhờ giao thức lock/unlock; bản thân một lời `signal()` **không thay thế** việc bảo vệ predicate.
- Không cần thêm `volatile` cho mọi field đã được bảo vệ nhất quán bằng cùng lock.
- Nếu một getter đọc state **ngoài** giao thức lock, class có thể mất bảo đảm hiển thị và an toàn.
- `Thread.start()` và `Thread.join()` tạo thêm quan hệ ở biên vòng đời thread, nhưng không thay thế lock/condition khi cần phối hợp **trong lúc nhiều thread cùng đang hoạt động**.

`signal()`/ `signalAll()` chỉ quyết định thread nào có cơ hội tranh lấy lock; **thứ tự thực thi thực tế vẫn do lập lịch và tranh chấp lock quyết định**.

## 14. Fairness và việc đánh thức quá nhiều waiter

`new ReentrantLock(true)` có chính sách *fair* khi phân phối lock dưới tranh chấp theo contract, nhưng điều đó **không đảm bảo thứ tự nghiệp vụ FIFO hoàn toàn**, không loại bỏ spurious wakeup và không làm `signal()` trở thành một lần đặt chỗ cho item. Sau signal, waiter vẫn phải cạnh tranh theo quy tắc lock.

Nếu gọi `signalAll()` cho hàng trăm consumer chỉ vì thêm một item, tất cả có thể phải thức dậy và tranh lock, trong khi cuối cùng chỉ một consumer lấy được item — hiện tượng *thundering herd*. Phân chia waiter thành `notEmpty` và `notFull`, sau đó sử dụng `signal()` khi đủ, giúp giảm nhiều lần đánh thức không cần thiết.

Tuy nhiên, **signal ít hơn không đồng nghĩa tốt hơn trong mọi trường hợp**. Đối với sự kiện đóng, thay đổi phiên bản hoặc điều kiện mà nhiều waiter có thể cùng tiến triển, `signalAll()` là lựa chọn cần thiết. Ưu tiên **đúng về safety/liveness** trước rồi mới đo contention và tối ưu.

## 15. Những lỗi thường gặp

| Sai lầm | Hệ quả | Cách sửa |
|---|---|---|
| Tạo `notEmpty` từ lock A, nhưng giữ lock B khi `await()` | Với `ReentrantLock.ConditionObject` sẽ ném `IllegalMonitorStateException`. | Condition phải được dùng cùng associated lock. |
| Dùng `if (empty) await()` | Có thể thức dậy giả hoặc item đã bị thread khác lấy. | Luôn `while (predicate false) await()`. |
| Đổi `queue` ngoài lock rồi signal trong lock | Dễ có race và phá invariant. | Tất cả đọc/ghi state liên quan và tín hiệu dùng cùng lock. |
| Tạo hai Condition nhưng dùng chung một predicate mơ hồ | Không tận dụng đúng việc phân nhóm waiter; dễ signal sai. | Định nghĩa rõ từng predicate và nhóm waiter. |
| Sau put lại signal `notFull` | Có thể đánh thức producer không đủ điều kiện; consumer còn ngủ. | Sau put signal `notEmpty`, sau take signal `notFull`. |
| Khi close chỉ signal một trong hai Condition | Nhóm waiter còn lại có thể đợi mãi. | SignalAll tất cả condition queues chịu ảnh hưởng bởi closed. |
| Nghĩ signal truyền ngay lock cho waiter | Waiter vẫn phải đợi notifier unlock và tranh lock. | Giữ critical section ngắn, luôn kiểm tra lại predicate. |
| Dùng `Thread.sleep()` trong khi giữ lock để chờ | Thread thay đổi predicate có thể không vào được. | Dùng await hoặc concurrent abstraction có sẵn. |
| Nuốt `InterruptedException` và tiếp tục thao tác | Mất yêu cầu hủy, có thể sai hợp đồng. | Ném tiếp hoặc khôi phục cờ interrupt, kết thúc theo policy. |
| Reset nguyên timeout sau mỗi wakeup | Có thể chờ lâu hơn thời hạn dự tính. | Dùng `awaitNanos(remaining)` hoặc deadline. |
| Tạo Condition cho từng field không liên quan đến lock chung | Thiết kế phức tạp và nguy cơ deadlock. | Phân tích invariant và ownership trước khi tạo queues. |
| Dùng lock/condition tự viết cho mọi queue | Tăng chi phí kiểm chứng/duy trì. | Ưu tiên `BlockingQueue` khi phù hợp. |

Một lỗi tinh tế: code vẫn có thể làm đúng dữ liệu nhưng **không tiến triển**. Ví dụ producer/consumer đều await theo predicate chính xác, nhưng không ai signal condition cần thiết. Vì vậy phải đánh giá **safety** (không vượt capacity, không lấy queue rỗng) và **liveness** (waiter thích hợp cuối cùng được đánh thức) riêng biệt.

## 16. So sánh với Object.wait/notify và API cấp cao

| Công cụ | Tình huống sử dụng | Đặc điểm |
|---|---|---|
| `Object.wait()/notify()/notifyAll()` | Phối hợp trên intrinsic monitor. | Mỗi object có một wait set; phải dùng `synchronized` và `while`. |
| `ReentrantLock` + nhiều `Condition` | Cấu trúc đồng thời có nhiều predicate trên một state chung. | Nhiều condition queues, `lockInterruptibly`, timed wait; phải dùng cùng lock và `while`. |
| `BlockingQueue` | Producer–consumer cần queue. | Cơ chế chờ/đánh thức và quản lý dung lượng đã được thư viện thiết kế. |
| `Semaphore` | Quản lý số permit, hạn chế số tác vụ đồng thời. | Permit được lưu; khác `signal()` không tích lũy. |
| `CountDownLatch` | Chờ điều kiện one-shot như “N tác vụ đã xong”. | Không reset được; không cần tự quản Condition cho tín hiệu một lần. |
| `CyclicBarrier`/`Phaser` | Phối hợp theo nhiều pha. | Có giao thức pha thay vì tự viết predicate phức tạp. |

Những Condition khác nhau **không phải các lock khác nhau**. Sức mạnh của mô hình là một lock bảo vệ invariant còn nhiều hàng chờ **phản ánh các lý do chờ**. Nếu chỉ cần truyền item giữa worker, nên dùng `ArrayBlockingQueue` hoặc `LinkedBlockingQueue` thay vì tự viết để giảm rủi ro.

## 17. Checklist khi thiết kế nhiều Condition

1. Xác định **state/invariant chung**: ai sở hữu queue, capacity, closed?
2. Chọn **một lock** phù hợp cho tất cả đường truy cập state ấy.
3. Liệt kê các predicate độc lập: `notEmpty`, `notFull`, `closed`, `ready`, `finished`...
4. Chỉ tạo Condition khi thực sự cần hàng chờ riêng cho một nhóm waiter.
5. Trong mỗi method chờ: **lock → while(predicate sai) await → hành động → unlock trong finally**.
6. Trong method cập nhật: **lock → thay state → signal condition thích hợp → unlock**.
7. Nếu một sự kiện ảnh hưởng nhiều nhóm, đặc biệt close/cancel, báo trên **mọi Condition liên quan**; dùng `signalAll()` nếu mọi waiter cần xét lại.
8. Xem xét spurious wakeup, thread khác lấy mất tài nguyên, timeout và interruption.
9. Không giữ thêm những lock không liên quan khi await: await chỉ nhả associated lock, có thể gây deadlock nếu chờ thread cần lock khác.
10. Viết rõ hợp đồng khi đóng và dữ liệu còn trong queue; quyết định method ném exception, trả sentinel hay đợi.
11. Test nhiều producer và consumer, kiểm tra tổng/số item, không chỉ một lượt chạy.
12. Khi chỉ cần bài toán đã có abstraction chuẩn, ưu tiên API cấp cao.

## 18. Tự kiểm tra kiến thức

1. **Vì sao `notEmpty` và `notFull` cùng một lock?** Vì cả hai predicate phụ thuộc cùng state queue; phải bảo vệ invariant bằng một giao thức loại trừ nhất quán.
2. **Sau `put()`, vì sao báo `notEmpty`?** Vì queue mới có thêm item; consumer đang đợi item cần được thức dậy, không phải producer đang đợi chỗ trống.
3. **Sau `take()`, vì sao báo `notFull`?** Vì đã giải phóng capacity để producer có thể đặt tiếp.
4. **Khi close, gọi `notEmpty.signalAll()` đã đủ không?** Không nếu có producer đợi `notFull`; phải báo tất cả nhóm có liên quan.
5. **`signal()` có bảo đảm waiter chạy ngay không?** Không; waiter phải lấy lại lock trước khi `await()` trả về.
6. **Vì sao `while` bắt buộc dù đã dùng hai Condition?** Vì spurious wakeup và predicate có thể thay đổi trước khi waiter lấy lại lock.
7. **`signal()` có lưu permit cho thread đến sau không?** Không; state/predicate phải lưu sự thật, hoặc dùng `Semaphore` nếu cần permit.
8. **Dùng fair `ReentrantLock` có loại bỏ nhu cầu while không?** Không. Fairness không có nghĩa condition luôn đúng khi thức dậy.
9. **`await()` có nhả cả những lock khác thread đang giữ không?** Không; chỉ nhả associated lock của Condition theo contract.
10. **Khi nào nên thay tự viết Condition bằng `BlockingQueue`?** Khi mục tiêu là truyền dữ liệu producer–consumer thông thường và không cần một giao thức điều phối đặc biệt.

**Ghi nhớ:** một `ReentrantLock` bảo vệ **trạng thái chung**, nhiều `Condition` chia các **nhóm waiter theo predicate**, còn `while` bảo đảm mỗi lần thức dậy đều kiểm tra lại sự thật hiện tại. Đổi state và báo đúng condition dưới cùng lock, đồng thời đánh thức tất cả nhóm cần thoát khi đóng/hủy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-coordination-008">LockSupport.park()/unpark() và permit</a>

<details>
<summary>Xem chi tiết</summary>

# LockSupport.park()/unpark() và permit trong Java

`java.util.concurrent.locks.LockSupport` cung cấp các thao tác **park** để một thread tự tạm dừng, và **unpark** để một thread khác cấp quyền cho thread đích tiếp tục. Nó là một **primitive chờ/đánh thức cấp thấp**, thường được sử dụng bên dưới các cấu trúc đồng bộ khác trong Java, không phải queue, mutex hay bộ đếm công việc.

Khái niệm cốt lõi là **permit**: mỗi thread có tối đa **một** permit. `LockSupport.unpark(thread)` làm permit của thread đích có sẵn; `LockSupport.park()` của thread đó có thể tiêu thụ permit và trở về. Vì permit không tích lũy, nhiều lần unpark trước park **không** tạo nhiều lượt park được phép đi tiếp. `park` cũng có thể trở về khi bị interrupt hoặc vì *spurious return*, nên code đúng cần **predicate riêng, vòng kiểm tra lại và memory visibility hợp lệ**.

Bài học này thuộc Java concurrency nói chung, không phụ thuộc một framework riêng.

## 1. Mô hình permit một-bit

Hình dung permit của một thread là trạng thái logic 0 hoặc 1:

| Sự kiện | Permit | Ý nghĩa |
| --- | --- | --- |
| Thread được khởi tạo, chưa nhận tín hiệu | 0 | Chưa có permit chờ sẵn |
| Thread khác gọi `unpark(T)` | 1 | T được cấp quyền cho một lần park |
| Thread khác gọi `unpark(T)` thêm một lần | Vẫn 1 | Không cộng dồn thành 2 |
| T gọi `park()` khi permit đã có | 0 sau khi tiêu thụ | Lời gọi có thể trở về ngay |
| T gọi `park()` khi chưa có permit | Có thể chờ | Đợi unpark, interrupt hoặc trở về vì nguyên nhân khác được API cho phép |

Mô hình này để **lý giải behavior**, không phải field `boolean` có thể đọc từ API. Không có `LockSupport.hasPermit()` hay thao tác “nhận số lượng permit”; không thể dùng permit thay một queue chứa job.

Một vài lịch thực thi:

```text
A: unpark(B)            -> permit(B) có sẵn
B: park()               -> tiêu thụ permit, trở về

A: unpark(B)
A: unpark(B)            -> vẫn chỉ một permit
B: park()               -> tiêu thụ một permit
B: park()               -> có thể phải chờ tín hiệu mới

B: park()               -> có thể tạm dừng
A: unpark(B)            -> B có thể được đánh thức
```

Không nói rằng lần `park` thứ hai **bắt buộc chờ mãi**: `park` có thể trở về vì interrupt/spurious return. Điều quan trọng là không được dựa vào lần `unpark` thứ hai như một permit tích lũy riêng.

## 2. `unpark` có thể xảy ra trước `park`

Với `Object.notify()`, nếu code sai giao thức và thông báo xảy ra trước khi thread vào wait set, thông báo đó không tự được ghi nhớ thành “vé đợi”. `LockSupport` có cơ chế permit, cho phép thread được unpark trước rồi mới park.

```text
Main thread                         Worker thread
--------------------------------    ------------------------------
Worker đã start                     chưa gọi park()
unpark(worker)                      permit = 1
                                    park() tiêu thụ permit
                                    tiếp tục thực thi
```

**Nhưng có hai giới hạn đáng nhớ.** Thứ nhất, permit **không được bảo đảm giữ lại qua `Thread.start()` xen vào**; không dựa vào `unpark` một thread chưa start. Thứ hai, **một lời gọi park trung gian** của code thư viện có thể tiêu thụ permit trước khi code của bạn tới `park` dự kiến. Vì vậy không lưu permit như một token bảo đảm tồn tại xuyên qua các lời gọi blocking tùy ý.

### Ví dụ Java 17: cấp permit trước khi worker gọi park

Tạo tệp `PreUnparkExample.java`:

```java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class PreUnparkExample {
    public static void main(String[] args) throws InterruptedException {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean proceed = new AtomicBoolean(false);
        AtomicBoolean returned = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            started.set(true);
            while (!proceed.get()) {
                Thread.onSpinWait();
            }
            LockSupport.park();
            returned.set(true);
        }, "worker");

        worker.start();
        long deadline =
                System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

        while (!started.get()) {
            if (System.nanoTime() >= deadline) {
                worker.interrupt();
                throw new IllegalStateException("Worker not started");
            }
            Thread.onSpinWait();
        }

        LockSupport.unpark(worker);
        proceed.set(true);

        worker.join(5_000);
        if (worker.isAlive()) {
            worker.interrupt();
            throw new IllegalStateException("Worker still parked");
        }
        System.out.println("parkReturned=" + returned.get());
    }
}
```

```bash
javac PreUnparkExample.java
java PreUnparkExample
```

Stdout khi hoàn thành bình thường:

```text
parkReturned=true
```

**Giải thích:** worker đã khởi động và báo `started` trước khi main unpark. Trong lúc chờ `proceed`, worker chỉ spin, **không dùng một primitive park khác** có thể tiêu thụ permit. Main cấp permit rồi mở cổng. Worker gọi `park` với permit đã có nên không cần unpark mới. `join` bảo đảm main in sau khi worker hoàn tất.

Busy-spin ở đây **chỉ để tạo checkpoint minh họa** và có deadline; không phải mô hình chờ khuyến nghị trong production. Một lần chạy in dòng trên cũng không chứng minh park không bao giờ spurious return; hợp đồng permit mới là cơ sở để hiểu.

## 3. Vì sao unpark không đếm job?

Giả sử producer tạo 100 tác vụ rồi gọi `unpark(consumer)` 100 lần; nếu consumer chưa gọi `park`, những lần unpark chỉ tạo **một permit**. Một consumer viết “100 lần park, mỗi lần xử lý một job” có thể kẹt sau lần đầu.

```java
// Cách diễn đạt sai về nghiệp vụ:
for (int i = 0; i < 100; i++) {
    LockSupport.unpark(consumer);
}

// Phía consumer, cũng sai nếu coi một park = một job:
for (int i = 0; i < 100; i++) {
    LockSupport.park();
    processOneJob();
}
```

Đây là **snippet minh họa thiết kế sai**; `consumer` và `processOneJob` chưa được định nghĩa. Permit không phải queue và không chứa payload. Thiết kế đúng phải lưu công việc trong một **queue thread-safe**, hoặc dùng `BlockingQueue` đã có `put/take`. Khi cần N giấy phép truy cập tài nguyên, dùng `Semaphore`: permit của semaphore được đếm theo hợp đồng khác hẳn một-bit permit của LockSupport.

## 4. `park` có thể trở về mà không được `unpark`

Ngoài unpark, `park` có thể trở về vì **interrupt** hoặc **spurious return**. Với các overload timed, nó còn có thể trở về vì hết thời gian. Do đó đoạn:

```java
LockSupport.park();
processJob();
```

sai nếu `processJob` chỉ được thực hiện khi một điều kiện nghiệp vụ thật đã xảy ra.

Cần một predicate độc lập:

```java
while (!ready.get()) {
    LockSupport.park();
    if (Thread.currentThread().isInterrupted()) {
        return;
    }
}
processJob();
```

Đây là snippet trong method `void`, `ready` là `AtomicBoolean` và `processJob()` là nghiệp vụ. Nếu park thức sớm mà `ready=false`, thread chờ lại. Nếu bị interrupt, nó thoát theo policy hủy của ví dụ.

**Quan trọng:** `LockSupport.park()` không ném `InterruptedException` và không tự xóa interrupt flag. Khi cờ interrupt đang bật, park có thể liên tục trả ngay; nếu vòng lặp chỉ kiểm tra predicate false mà không xử lý interrupt, worker có thể **spin nóng và chiếm CPU**.

## 5. Java 17: phối hợp bằng predicate và permit

Producer phải **công bố trạng thái**, sau đó **unpark đúng thread**. Consumer chờ trên predicate chứ không tin rằng `park` trở về tự mang nghĩa “có dữ liệu”.

Tạo `ParkPredicateExample.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class ParkPredicateExample {
    public static void main(String[] args) throws Exception {
        AtomicBoolean ready = new AtomicBoolean(false);
        AtomicInteger received = new AtomicInteger(-1);
        CountDownLatch started = new CountDownLatch(1);
        int[] payload = {0};

        Thread consumer = new Thread(() -> {
            started.countDown();

            while (!ready.get()) {
                LockSupport.park("waiting-for-payload");
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }
            received.set(payload[0]);
        }, "consumer");

        consumer.start();

        if (!started.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Consumer not started");
        }

        payload[0] = 42;
        ready.set(true);
        LockSupport.unpark(consumer);

        consumer.join(5_000);
        if (consumer.isAlive()) {
            consumer.interrupt();
            throw new IllegalStateException("Consumer did not finish");
        }
        System.out.println("received=" + received.get());
    }
}
```

```bash
javac ParkPredicateExample.java
java ParkPredicateExample
```

Stdout bình thường:

```text
received=42
```

**Vì sao đúng?** Producer ghi `payload[0]=42`, sau đó `ready.set(true)`. Consumer chỉ đọc payload sau khi `ready.get()` quan sát giá trị true. Các thao tác atomic/volatile của `ready` mang memory-consistency semantics cần thiết để công bố payload. `unpark` chỉ để worker đang park có cơ hội chạy và **kiểm tra lại cờ**; bản thân permit không làm một object mutable trở nên thread-safe.

Xét ba lịch:

- Consumer park trước → producer unpark → consumer thức và thấy cờ true.
- Producer công bố trước khi consumer đọc cờ → consumer thấy true và bỏ qua park.
- Consumer đọc false, producer công bố/unpark, rồi consumer mới park → permit đã cấp giúp park đi tiếp, consumer kiểm tra lại và thấy true.

Nếu `park` spurious return khi cờ vẫn false, vòng tiếp tục chờ. Nếu consumer bị interrupt trước khi thấy cờ, code thoát theo policy của ví dụ.

**Ghi chú:** tham số string của `park(blocker)` chỉ là **metadata để debug**, không phải lock, payload hay object cần unpark. `unpark` vẫn nhận chính `Thread` đích.

## 6. Không nhả monitor khi gọi `park`

Khác với `Object.wait()` và `Condition.await()`, `LockSupport.park()` **không nhả monitor hoặc `ReentrantLock` mà caller đang giữ**.

Ví dụ dễ deadlock:

```java
synchronized (lock) {
    while (!ready) {
        LockSupport.park();
    }
}
```

Snippet này giả định producer cũng cần `synchronized(lock)` để chuyển `ready=true`. Worker đang park nhưng **vẫn sở hữu monitor `lock`**, nên producer bị BLOCKED và không thể thay state để giúp worker đi tiếp.

Mẫu phù hợp với intrinsic monitor là:

```java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
}
```

Đoạn nằm trong method có `throws InterruptedException`. `Object.wait()` nhả đúng monitor của object được gọi `wait`, sau đó lấy lại monitor trước khi trở về. Producer thay predicate và `lock.notifyAll()` trong cùng monitor.

Với `ReentrantLock` và `Condition`:

```java
lock.lock();
try {
    while (!ready) {
        condition.await();
    }
} finally {
    lock.unlock();
}
```

Snippet nằm trong method khai báo `throws InterruptedException`; `condition` được tạo bằng `lock.newCondition()`. `await()` nhả associated lock trong lúc chờ và lấy lại trước khi return. **Không thay `await` bằng `park` bên trong critical section** mà hy vọng nó cũng nhả lock.

## 7. Java 17: interrupt đánh thức park mà không ném exception

Tạo tệp `ParkInterruptExample.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class ParkInterruptExample {
    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean sawInterrupt = new AtomicBoolean(false);

        Thread worker = new Thread(() -> {
            started.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                LockSupport.park();
            }
            sawInterrupt.set(true);
        }, "worker");

        worker.start();
        if (!started.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Worker not started");
        }

        worker.interrupt();
        worker.join(5_000);
        if (worker.isAlive()) {
            throw new IllegalStateException("Worker did not stop");
        }

        System.out.println("interruptObserved=" + sawInterrupt.get());
    }
}
```

```bash
javac ParkInterruptExample.java
java ParkInterruptExample
```

Stdout khi hoàn thành bình thường:

```text
interruptObserved=true
```

Nếu main interrupt **trước** khi worker gọi park, cờ đã bật và vòng có thể thoát ngay. Nếu interrupt xảy ra **trong lúc** worker park, park trở về và điều kiện vòng thấy cờ true. Nếu park tự thức dậy sớm, điều kiện vẫn false rồi worker chờ tiếp. Kết quả không dựa vào việc scheduler chọn chính xác mốc nào.

`Thread.currentThread().isInterrupted()` chỉ đọc cờ; `Thread.interrupted()` đọc **và xóa** cờ của thread hiện tại. Đừng vô ý dùng method thứ hai khiến code gọi bên ngoài bỏ lỡ yêu cầu hủy.

## 8. `parkNanos`, `parkUntil` và thời hạn chờ

Các overload:

| API | Ý nghĩa |
| --- | --- |
| `LockSupport.park()` | Chờ không chỉ định thời hạn |
| `LockSupport.park(Object blocker)` | Chờ và gắn blocker cho diagnostics |
| `LockSupport.parkNanos(long nanos)` | Chờ theo khoảng thời gian **tương đối dương** |
| `LockSupport.parkNanos(Object blocker, long nanos)` | Tương tự, có blocker |
| `LockSupport.parkUntil(long deadline)` | Chờ tới mốc **epoch milliseconds tuyệt đối** |
| `LockSupport.parkUntil(Object blocker, long deadline)` | Tương tự, có blocker |
| `LockSupport.unpark(Thread t)` | Cấp permit cho t, có thể làm park đang chờ trở về |

**`parkNanos(0)` không giống `Object.wait(0)`.** Với `parkNanos`, duration không dương không tạo khoảng park hữu ích; `Object.wait(0)` lại có nghĩa chờ vô thời hạn.

Nếu muốn timeout 200 ms theo duration, dùng monotonic `System.nanoTime()`, tính remaining sau mỗi lần thức:

```java
long deadline =
        System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(200);

while (!ready.get()) {
    long remaining = deadline - System.nanoTime();
    if (remaining <= 0) {
        return false;
    }
    LockSupport.parkNanos(remaining);
    if (Thread.currentThread().isInterrupted()) {
        return false;
    }
}
return true;
```

Snippet trong method `boolean` với `ready` là `AtomicBoolean`, cần import `TimeUnit` và `LockSupport`. `parkNanos` có thể trở về **trước** khi timeout vì unpark, interrupt hoặc spurious return; phải kiểm tra predicate và thời gian mỗi vòng. Scheduler không bảo đảm sẽ chạy thread **ngay đúng** mốc timeout. Với timeout cực lớn cần xét overflow của `nanoTime()+timeout`.

`parkUntil(deadline)` dùng epoch milliseconds (thường tính từ `System.currentTimeMillis()`), không phải duration nanoseconds. Vì dùng mốc đồng hồ lịch, việc điều chỉnh wall clock có thể ảnh hưởng chờ; khi đo duration tương đối, `parkNanos` và `nanoTime` thường dễ suy luận hơn.

## 9. `park(blocker)` và `getBlocker` chỉ phục vụ quan sát

Ví dụ:

```java
LockSupport.park(waitQueue);
Object blocker = LockSupport.getBlocker(worker);
```

Đây là snippet, `waitQueue` là object mô tả lý do chờ, `worker` là thread đích. `blocker` **không phải monitor đang được khóa**, không chứa permit và không phải tham số dùng cho `unpark`. Nó giúp JFR/thread dump và người debug hiểu worker đang đợi điều gì.

`getBlocker(worker)` có thể trả object blocker khi worker đang park qua overload có blocker, hoặc `null` nếu worker không còn park, vừa đổi trạng thái hay park không có blocker. **Không dùng getBlocker làm tín hiệu đồng bộ**, vì chỉ là snapshot và có race.

Một thread đang đợi trong `LockSupport.park()` thường có trạng thái Java `WAITING (parking)`; timed park có thể xuất hiện `TIMED_WAITING`. Không kết luận deadlock chỉ từ một lần thấy WAITING: thread pool worker rảnh đang park là bình thường. Đọc stack trace, blocker, predicate và nguồn phát tín hiệu.

## 10. So sánh với các primitive Java khác

| Cơ chế | Gắn với gì? | Nhả lock khi chờ? | Có đếm nhiều phần tử/permit không? |
| --- | --- | --- | --- |
| `park/unpark` | Permit một-bit của **thread** | **Không** | Không |
| `Object.wait/notifyAll` | Wait set của object monitor | Nhả đúng monitor được gọi `wait` | Không chứa queue job |
| `Condition.await/signalAll` | Condition gắn với `Lock` | Nhả associated lock, lấy lại sau | Không chứa queue job |
| `Thread.sleep` | Khoảng thời gian | Không | Không |
| `Thread.join` | Vòng đời thread khác | Không tự nhả mọi monitor caller đang giữ | Không |
| `CountDownLatch` | Bộ đếm một chiều | Không nhả monitor tùy ý | Có counter tới 0 |
| `Semaphore` | Giấy phép truy cập tài nguyên | Không nhả monitor tùy ý | **Có** permit số lượng |
| `BlockingQueue` | Queue chứa payload | Quản lý synchronization bên trong | **Có** phần tử |

**Ưu tiên abstraction cấp cao**: muốn truyền nhiều công việc thì dùng `BlockingQueue`; giới hạn N task thì dùng `Semaphore`; đợi N sự kiện hoàn tất thì `CountDownLatch`; chờ predicate dưới mutex thì `Condition` hoặc `wait`. `LockSupport` phù hợp khi xây một synchronizer cấp thấp, có cơ chế state và waiter queue riêng.

Các implementation dựa trên `AbstractQueuedSynchronizer` có thể dùng park/unpark bên trong để đỗ worker, nhưng chúng còn cần atomic state, queue waiter, cancellation, interruption và memory semantics. **Không thể chỉ thay một synchronized block bằng một lệnh park để tạo được mutex**.

## 11. Race giữa kiểm tra predicate và park

Giả sử consumer thực hiện:

```java
while (!hasWork.get()) {
    LockSupport.park();
}
```

Producer thực hiện:

```java
hasWork.set(true);
LockSupport.unpark(worker);
```

Đây là hai snippets, `hasWork` là `AtomicBoolean`; `worker` đã start và không có lời gọi park trung gian bất ngờ.

- Producer đặt true trước khi consumer đọc → consumer không park.
- Consumer đọc false, producer đặt true/unpark, rồi consumer park → permit cấp sớm giúp nó trở về và kiểm tra lại true.
- Consumer park trước → producer unpark, consumer tỉnh và đọc true.
- Consumer spurious return → nếu false, nó park lại.
- Consumer bị interrupt → phải xử lý interruption, nếu không vòng có thể spin do park trả ngay.

Một permit **còn dư** nếu consumer thấy true mà không cần park có thể làm một lời gọi park khác về sau trả ngay một lần. Đây không phải một job thừa. Thuật toán chính xác luôn tin **predicate** thay vì số lần thức dậy.

**Không xem cờ boolean là queue đa phần tử.** Nếu nhiều producer/consumer cùng sửa số lượng job, cần một queue/counter có atomicity phù hợp, chứ không chỉ cờ và permit.

## 12. Memory ordering: permit không thay Java Memory Model

Một chương trình đúng cần hai tầng:

1. **Trạng thái được công bố an toàn:** dùng `volatile`, atomic, synchronized, lock hoặc operation của concurrent collection với memory consistency được JDK quy định.
2. **Cơ chế đánh thức:** gọi unpark đúng thread có thể đang chờ để nó kiểm tra lại trạng thái.

Ví dụ:

```java
// Producer:
payload = 42;
ready.set(true);
LockSupport.unpark(consumer);

// Consumer:
while (!ready.get()) {
    LockSupport.park();
}
consume(payload);
```

Đây là snippet, với `ready` là `AtomicBoolean`. Ghi payload trước `ready.set(true)` và đọc `ready.get()==true` trước khi dùng payload tạo happens-before cần thiết. Chỉ có `unpark` mà không có giao thức visibility cho mutable state thì không thể suy luận mọi thao tác đọc/ghi đều an toàn.

Nếu payload tiếp tục bị sửa sau khi ready true, công bố một lần **không** làm các lần sửa sau trở thành an toàn. Cần immutable snapshot, queue, lock hoặc atomic update đúng phạm vi invariant.

## 13. Java 21+ virtual threads và LockSupport

Virtual threads vẫn phải tuân thủ Java Memory Model. `park/unpark` có ý nghĩa với việc chờ/đánh thức virtual thread, trong khi runtime có thể quản lý mount/unmount trên carrier thread khác cách platform thread chờ OS. Chi tiết pinning và diagnostics phụ thuộc phiên bản JDK; **không suy ra mỗi virtual thread park tương ứng một OS thread bị khóa**.

Dù là virtual hay platform thread, các nguyên tắc không đổi: predicate có visibility, xử lý spurious return/interrupt, không giữ khóa producer cần khi park, và đo đạc trên đúng runtime. Các chương trình của bài dùng Java 17, không cần virtual-thread API.

## 14. Những lỗi phổ biến

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Gọi unpark N lần để ghi nhớ N công việc | Permit không cộng dồn | Queue/counter lưu job |
| Park trả về rồi làm việc ngay | Spurious return/interrupt | `while` kiểm tra predicate |
| Dùng field thường làm predicate không đồng bộ | Thiếu visibility | Volatile/atomic/lock |
| Park khi còn giữ monitor cần cho producer | Deadlock | `wait/Condition.await` hoặc nhả khóa |
| Nghĩ park tự nhả `ReentrantLock` | Khóa vẫn bị giữ | Chờ ngoài critical section |
| Unpark thread chưa start | Permit không được bảo đảm qua start | Start trước signal |
| Giữ permit qua blocking API khác | Park trung gian có thể tiêu thụ | Dựa vào predicate |
| Bỏ qua interrupt | Vòng chờ có thể spin nóng | Xử lý cancellation |
| Dùng `Thread.interrupted()` khi chỉ muốn xem flag | Vô tình xóa cờ | `isInterrupted()` |
| `parkNanos(0)` như `Object.wait(0)` | Quy ước zero khác | Dùng `park()` khi không timeout |
| Truyền duration vào `parkUntil` | Sai đơn vị/ý nghĩa | Epoch milliseconds tuyệt đối |
| `getBlocker` như điều kiện quyết định | Snapshot không ổn định | Primitive đồng bộ thật |
| Dùng park/unpark như mutex | Không có mutual exclusion | `synchronized`/`Lock`/atomic |
| Dùng `Thread.sleep` để ép interleaving | Scheduling ngẫu nhiên | Latch/barrier |
| Thấy `WAITING` và kết luận deadlock | Worker idle vẫn có thể park | Đọc stack, blocker và predicate |

## 15. Debug thread park lâu: checklist

1. **Đang chờ predicate nào?** Có job, chờ khóa, chờ result, hay chờ shutdown?
2. **Ai đổi predicate?** Có mã producer chắc chắn thay đúng state trước khi signal không?
3. **Ai unpark?** Có target đúng `Thread` instance, đã start và còn sống?
4. **Có park trung gian?** Permit cấp sớm có bị một lời gọi park trong thư viện khác tiêu thụ?
5. **Predicate có visibility?** Atomic/volatile hoặc lock/happens-before đúng chưa?
6. **Thread có giữ lock?** Một worker đang park có đang giữ monitor/Lock producer cần?
7. **Interrupt flag?** Nếu bật, park trả ngay làm CPU nóng; nếu code nuốt interrupt, shutdown khó.
8. **Timed wait?** Đơn vị thời gian và deadline tính đúng chưa? Có tính lại remaining sau return?
9. **Diagnostics?** Xem `getBlocker`, JFR/thread dump, nhiều snapshot và trạng thái ứng dụng.
10. **Test điều phối?** Kiểm tra unpark-before-park, park-before-unpark, interrupt và spurious return giả lập bằng vòng kiểm tra predicate.
11. **Có thể dùng primitive JDK cao cấp hơn?** Tránh tự cài synchronizer nếu không thật sự cần.

## 16. Câu hỏi tự kiểm tra

**1. `unpark(T)` ba lần rồi `T.park()` ba lần, có chắc cả ba đi tiếp ngay?** Không. Permit tối đa một. Lần park sau có thể chờ (dù có thể tự trở về spurious).

**2. Unpark trước park có phải luôn an toàn?** Permit có thể cấp trước park, nhưng phải xét thread đã start, không có park trung gian tiêu thụ và predicate được đồng bộ.

**3. Park trở về có chắc có job?** Không. Có thể do permit, interrupt hoặc spurious return. Dùng predicate thật.

**4. Park có nhả monitor không?** Không. `Object.wait`/`Condition.await` mới nhả associated lock theo API.

**5. `park` có ném `InterruptedException`?** Không. Nó có thể trở về với interrupt flag vẫn bật.

**6. `parkNanos(0)` giống `Object.wait(0)`?** Không. `parkNanos` không chờ hữu ích với thời lượng không dương; `Object.wait(0)` là vô thời hạn.

**7. Blocker trong `park(blocker)` có phải object để unpark?** Không. `unpark` nhận `Thread`, blocker chỉ giúp diagnostics.

**8. `Semaphore` permit khác `LockSupport` permit ra sao?** Semaphore đếm giấy phép tài nguyên; LockSupport chỉ có permit một-bit trên mỗi thread cho park.

**9. Có thể dùng LockSupport để bảo vệ `count++` không?** Không. Nó không tạo mutual exclusion hay atomic read–modify–write.

**10. Worker bị interrupt và vòng chỉ viết `while (!ready) park()` có thể tốn CPU?** Có. Vì park có thể trả ngay khi interrupt flag đang bật.

**11. Vì sao cần atomic/volatile flag trước khi unpark?** Để công bố dữ liệu/điều kiện theo Java Memory Model; permit chỉ là wakeup.

**12. Vì sao `BlockingQueue` dễ dùng hơn tự ghép ArrayList và permit?** Nó cung cấp lưu item, phối hợp thread và memory consistency trong cùng hợp đồng, tránh lost work và race trên collection.

## 17. Tổng kết áp dụng

**LockSupport permit không phải job, mutex hoặc quota.** Nó là **quyền chờ/đánh thức tối đa một lần chưa tiêu thụ cho một thread**. `unpark` đến trước `park` vẫn có tác dụng theo hợp đồng, nhưng không tích nhiều permit và có thể bị tiêu thụ bởi một park trung gian. `park` không nhả khóa đang giữ, không ném `InterruptedException` và có thể tự trở về.

Để dùng đúng, cần **predicate được công bố an toàn**, kiểm tra điều kiện bằng vòng `while` sau khi park, chính sách interrupt/timeout rõ ràng và một data structure thực sự lưu trạng thái nghiệp vụ. Khi có thể, ưu tiên `BlockingQueue`, `Semaphore`, `CountDownLatch` hoặc `Condition`; dùng `LockSupport` trực tiếp khi có lý do thiết kế synchronizer cấp thấp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-coordination-009">Missed signal và lost notification: nguyên nhân</a>

<details>
<summary>Xem chi tiết</summary>

# Missed signal và lost notification: nguyên nhân trong Java Concurrency

Khi hai thread phối hợp, một bên phát tín hiệu “đã có dữ liệu” hoặc “điều kiện đã đúng”, bên còn lại chờ để tiếp tục. Lỗi **missed signal / lost notification** xảy ra khi thread chờ không nhận được thông báo cần thiết, có thể chờ mãi dù sự kiện mà nó quan tâm đã xảy ra. Với `Object.wait()/notify()/notifyAll()`, thông báo không phải một message được JVM lưu lại để phát cho thread tham gia sau đó.

Điểm cốt lõi của bài học không phải “gọi `notify` nhiều lần hơn” mà là **xây dựng giao thức trạng thái**: có một *predicate* mô tả điều kiện thật, kiểm tra nó và chuyển sang chờ **dưới cùng một monitor**, thay đổi predicate rồi phát tín hiệu theo cùng monitor, và luôn kiểm tra lại khi thức dậy. Nếu bài toán có primitive cấp cao phù hợp như `CountDownLatch`, `Semaphore`, `BlockingQueue`, `Future`, hãy ưu tiên hợp đồng rõ hơn thay vì tự viết wait/notify.

Bài này thuộc **Phối hợp luồng của Java Concurrency nói chung**, không bàn đến cơ chế riêng của Spring.

## 1. Phân biệt “sự kiện”, “trạng thái” và “thông báo”

Giả sử một consumer cần đợi tới khi producer tạo xong kết quả. Ba khái niệm khác nhau:

| Thành phần | Ví dụ | Vai trò |
| --- | --- | --- |
| **Trạng thái nghiệp vụ** | `ready == true`; queue không rỗng; `remaining == 0` | Sự thật mà chương trình cần kiểm tra. |
| **Predicate (điều kiện chờ)** | `ready`; `!queue.isEmpty()` | Khi predicate đúng, thread có thể tiếp tục. |
| **Thông báo** | `notify()`, `notifyAll()`, `Condition.signal()` | Gợi ý cho thread đang chờ thức dậy để kiểm tra trạng thái lại. |

`notify()` **không tự ghi** vào trạng thái `ready` và không tạo hàng đợi thông báo. `notifyAll()` cũng không biến thông báo thành “permit” được để dành cho thread sẽ gọi `wait()` trong tương lai.

Một giao thức đúng lưu **sự thật cần nhớ** trong state:

~~~java
synchronized (lock) {
    ready = true;
    lock.notifyAll();
}
~~~

Consumer đọc predicate dưới cùng lock:

~~~java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
    // Điều kiện ready đã đúng tại thời điểm sở hữu lock.
}
~~~

Hai khối trên là **mảnh mã minh họa**, giả định `lock` và `ready` đã khai báo; method chờ phải xử lý hoặc khai báo `InterruptedException`. Đây là mẫu *guarded block* chuẩn.

## 2. Cơ chế của Object.wait/notify

Với intrinsic monitor, Java có *wait set* gắn với một object. Những hành động quan trọng:

1. Thread phải **đang sở hữu monitor** của object khi gọi `wait()`, `notify()` hoặc `notifyAll()` trên object đó; nếu không, JVM ném `IllegalMonitorStateException`.
2. Gọi `wait()` khiến thread vào wait set, **nhả monitor của chính object đó**, rồi chờ notification, interruption, timeout (nếu có) hoặc spurious wakeup.
3. Trước khi `wait()` trả về hoặc ném `InterruptedException`, thread phải **lấy lại monitor** đó. Nó không thể chạy tiếp trong critical section ngay khi mới được notify nếu holder chưa nhả khóa.
4. `notify()` chọn **một thread** đang ở wait set của object, nếu có; không có bảo đảm nó chọn waiter “đúng mục đích”.
5. `notifyAll()` đánh thức **tất cả thread** hiện đang chờ trong wait set; chúng lần lượt cạnh tranh để sở hữu monitor sau khi monitor được giải phóng.
6. Khi **không có waiter** trong wait set tại thời điểm notify, thông báo không được tích lũy để thread đến sau sử dụng.
7. Spurious wakeup có thể xảy ra, nên việc một lần `wait()` trả về **không chứng minh** predicate đã đúng.

Mô tả “đánh thức” không có nghĩa waiter chạy ngay. `notify` chỉ thay đổi khả năng tiếp tục chờ monitor; thread gọi vẫn có thể sở hữu monitor cho đến khi thoát `synchronized`. Và `wait()` chỉ nhả **monitor mà nó đang wait**, không tự nhả những monitor khác đang giữ.

### 2.1 Không nhầm với Thread.sleep

- `Thread.sleep(100)`: tạm dừng thread một thời gian nhưng **không nhả monitor** đang giữ và không chờ sự kiện nghiệp vụ.
- `Object.wait(100)`: phải đang giữ đúng monitor; tạm nhả monitor đó trong khi chờ. Nó có thể trả về vì timeout, notification hoặc spurious wakeup.
- `Thread.join()`: chờ **vòng đời thread cụ thể kết thúc**, không phải chờ một predicate tự do.
- `LockSupport.park()`: cơ chế permit cấp thấp khác, có thể nhớ tối đa một permit trong giao thức park/unpark; không đồng nhất với Object.notify.

## 3. Lost notification khi notify xảy ra trước wait

Xét thời điểm:

~~~text
Producer                         Consumer
--------                         --------
synchronized(lock) {
    notifyAll()   // Chưa ai wait
}
                                 synchronized(lock) {
                                     wait() // Bắt đầu chờ sau thông báo
                                 }
                                 Không có tín hiệu mới => có thể chờ mãi.
~~~

Thông báo trước `wait()` **không được lưu lại**. Nếu không có predicate state, consumer không có cách nào biết sự kiện đã xảy ra.

### 3.1 Ví dụ Java 17+ tái hiện khoảng hở

Tạo `EarlyNotifyDemo.java`:

~~~java
public final class EarlyNotifyDemo {
    public static void main(String[] args) throws InterruptedException {
        Object lock = new Object();

        synchronized (lock) {
            lock.notifyAll(); // Chưa có waiter; không được lưu lại.
        }
        System.out.println("Notified before wait");

        synchronized (lock) {
            lock.wait(50); // Timed wait để demo không bị treo vô hạn.
        }
        System.out.println("Wait returned; notification was not stored");
    }
}
~~~

Biên dịch và chạy JDK 17+:

~~~bash
javac EarlyNotifyDemo.java
java EarlyNotifyDemo
~~~

Stdout (không có interruption bên ngoài):

~~~text
Notified before wait
Wait returned; notification was not stored
~~~

Dòng cuối **không** khẳng định `wait(50)` nhất thiết đã ngủ đúng 50 ms: lời gọi có thể kết thúc do timeout hoặc spurious wakeup, và scheduling cũng ảnh hưởng thời gian thực. Dữ liệu chắc chắn của demo là `notifyAll` xảy ra khi chưa có waiter; không tồn tại notification được lưu sẵn cho `wait` về sau. `wait(50)` chỉ giúp chương trình ví dụ kết thúc được, **không sửa giao thức sai**.

### 3.2 Điều gì không giải quyết được?

- Thay `notify()` bằng `notifyAll()` **không** lưu thông báo khi không có waiter.
- Gọi `Thread.sleep(200)` trước khi notify để “đợi consumer kịp chạy” chỉ làm lỗi ít tái hiện hơn; scheduler không đưa ra bảo đảm.
- Dùng `volatile boolean ready` nhưng vẫn gọi `wait()` vô điều kiện không sửa lỗi; waiter phải **kiểm tra ready trước khi chờ**, và quan hệ check→wait phải đúng.
- Đợi một lần notify mà không có state không phân biệt được “chưa từng có sự kiện”, “đã xảy ra sớm” và “có spurious wakeup”.

## 4. Missed signal do check rồi wait không nguyên tử

Một kiểu lỗi khác phát sinh khi consumer kiểm tra điều kiện **ngoài monitor** rồi mới vào monitor để wait:

~~~java
if (!ready) {
    synchronized (lock) {
        lock.wait();
    }
}
~~~

Đây là mảnh **sai**, chưa phải chương trình độc lập. `ready` có thể là volatile và vẫn có race logic:

~~~text
Consumer                         Producer
--------                         --------
if (!ready) => true
                                 synchronized(lock) {
                                     ready = true
                                     notifyAll()     // Không ai đang wait
                                 }
synchronized(lock) {
    wait()  // Bắt đầu đợi mặc dù ready đã true
}
~~~

Không nhất thiết là lỗi visibility: consumer có thể đã đọc `ready == false` **đúng ở thời điểm kiểm tra**. Lỗi là khoảng hở thời gian giữa **check** và **đăng ký chờ**; producer có thể phát tín hiệu trong khoảng đó.

Ngay cả đặt check trong một khối `synchronized` **riêng**, rồi ra khỏi lock và vào lại để wait, vẫn có khoảng hở:

~~~java
boolean shouldWait;
synchronized (lock) {
    shouldWait = !ready;
}
if (shouldWait) {
    synchronized (lock) {
        lock.wait();
    }
}
~~~

Code sai vì hai lần lấy lock **không** tạo một thao tác check-and-wait. Producer có thể thay đổi predicate giữa hai critical section.

### 4.1 Vì sao một khối synchronized giải quyết race?

Mẫu đúng:

~~~java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
}
~~~

Khi consumer đang giữ `lock` để kiểm tra `ready`, producer không thể đồng thời vào `synchronized(lock)` để thay `ready`. Nếu consumer thấy false, nó gọi `wait()`; việc nhả monitor và chuyển vào trạng thái wait **được phối hợp nguyên tử theo hợp đồng monitor** đối với producer muốn lấy lock ấy. Producer chỉ có thể lấy lock, ghi ready và notify sau khi consumer đã sẵn sàng chờ, hoặc producer đã ghi ready trước khi consumer lấy lock (lúc đó consumer thấy true và **không wait**).

Đây là cách loại bỏ cửa sổ **check → subscribe** của giao thức. Không cần đoán consumer chạy nhanh hơn producer.

## 5. Quy tắc protected predicate: trạng thái và wait cùng một lock

Một giao thức có thể chứng minh đúng có ba quy tắc:

1. **Định nghĩa predicate rõ ràng:** chẳng hạn `ready == true`, `queue.size() > 0` hoặc `remaining == 0`.
2. **Mọi lần kiểm tra/đổi predicate đều dưới cùng monitor:** cùng object lock, không chỉ cùng tên biến; tránh writer cập nhật không đồng bộ.
3. **Notify sau khi thay state:** cập nhật predicate và gọi `notifyAll()` trong cùng critical section; waiter dùng `while (!predicate) wait()`.

Writer:

~~~java
synchronized (lock) {
    ready = true;
    lock.notifyAll();
}
~~~

Waiter:

~~~java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
}
~~~

Cặp này xử lý cả hai trường hợp:

- **Signal sớm:** producer đặt ready=true trước, consumer lấy lock và không vào wait.
- **Waiter sớm:** consumer kiểm tra false, nhả lock qua wait, producer vào đổi ready và notify; consumer thức dậy, reacquire lock, kiểm tra lại true.

### 5.1 Vì sao dùng while, không dùng if?

Lỗi:

~~~java
synchronized (lock) {
    if (!ready) {
        lock.wait();
    }
    useResource();
}
~~~

Đây là mảnh mã sai, giả định `useResource()` cần ready true. `wait()` có thể trả về do **spurious wakeup**, hoặc bị đánh thức bởi một notification cho điều kiện khác, hoặc nhiều waiter thức dậy và một waiter khác đã tiêu thụ tài nguyên trước khi waiter này lấy được monitor. Khi đó ready có thể vẫn false.

Dùng `while` luôn kiểm tra lại **predicate nghiệp vụ**, không coi notification là lời khẳng định điều kiện đã đúng.

## 6. Ví dụ Java 17+: guarded block xử lý cả signal sớm và waiter sớm

Tạo `GuardedSignalDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;

public final class GuardedSignalDemo {
    private static final class Gate {
        private final Object lock = new Object();
        private boolean open;

        void open() {
            synchronized (lock) {
                open = true;
                lock.notifyAll();
            }
        }

        void awaitOpen(CountDownLatch approachingWait)
                throws InterruptedException {
            synchronized (lock) {
                while (!open) {
                    if (approachingWait != null) {
                        approachingWait.countDown();
                    }
                    lock.wait();
                }
            }
        }

        boolean isOpen() {
            synchronized (lock) {
                return open;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Trường hợp 1: signal trước khi có waiter.
        Gate early = new Gate();
        early.open();
        early.awaitOpen(null);
        System.out.println("Early signal observed: " + early.isOpen());

        // Trường hợp 2: waiter đến trước signal.
        Gate late = new Gate();
        CountDownLatch approachingWait = new CountDownLatch(1);

        Thread waiter = new Thread(() -> {
            try {
                late.awaitOpen(approachingWait);
                System.out.println("Late signal observed: " + late.isOpen());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "waiter");

        waiter.start();
        approachingWait.await();
        late.open();
        waiter.join();
        System.out.println("Done");
    }
}
~~~

Biên dịch/chạy JDK 17+:

~~~bash
javac GuardedSignalDemo.java
java GuardedSignalDemo
~~~

Stdout trong lần chạy bình thường, không có external interruption:

~~~text
Early signal observed: true
Late signal observed: true
Done
~~~

**Giải thích từng điểm đồng bộ:**

- `Gate.open()` lưu state `open = true` **trước** notification, dưới cùng monitor.
- `awaitOpen` dùng `while (!open)` và `wait()` dưới monitor đó.
- Với early signal, waiter đọc `open == true` và **không cần chờ**. Điều được nhớ là state, không phải notify.
- Với late signal, waiter gọi `approachingWait.countDown()` khi đang giữ monitor ngay trước `wait()`. Main đợi latch, rồi gọi `late.open()`. Dù main có tranh monitor trước hay sau waiter chuyển sang wait, không mất sự kiện: waiter vẫn giữ monitor đến lúc gọi `wait()` và sau đó mới nhả monitor cho producer.
- `wait()` nhả monitor, cho `open()` cập nhật state. Khi waiter được đánh thức, nó lấy lại monitor, kiểm tra predicate và in kết quả.
- `waiter.join()` giúp main đợi worker hoàn tất trước khi in Done. Không cần `sleep()` hoặc vòng spin để ước lượng thứ tự.

Lưu ý: `approachingWait` là thiết bị điều phối của **demo**, không phải thành phần bắt buộc trong guarded block thực tế. Một `Gate` dùng lâu dài cần thêm quy tắc reset/đóng hoặc cancellation nếu API yêu cầu. Vì state chỉ chuyển false→true một lần nên ví dụ này là **one-shot signal**.

## 7. Tại sao notifyAll thường an toàn hơn notify khi nhiều predicate?

`Object.notify()` không nhận tham số “đánh thức thread đang chờ notEmpty”. Nó chọn **một waiter bất kỳ** trong wait set của monitor. Nếu nhiều loại waiter có predicate khác nhau, thông báo có thể đánh thức **đúng monitor nhưng sai nhóm**.

Ví dụ bounded buffer có hai điều kiện:

- Producer đợi `notFull` để còn chỗ đặt phần tử.
- Consumer đợi `notEmpty` để có phần tử lấy ra.

Nếu hai loại waiter cùng `wait()` trên một monitor, `notify()` có thể chọn thread mà predicate vẫn false. Thread ấy thức dậy, kiểm tra lại và tiếp tục chờ. Trong một vài lịch xen kẽ, thread cần được đánh thức không nhận thêm notification nào, gây **liveness failure** dù trạng thái đã cho phép tiến triển.

`notifyAll()` đánh thức tất cả waiter, mỗi thread tự kiểm tra lại predicate. Đây thường là lựa chọn **dễ chứng minh đúng hơn** khi nhiều predicate/không kiểm soát chặt số waiter. Đánh đổi là **thundering herd**: nhiều thread thức dậy, tranh lại monitor, rồi đa số lại wait, tăng scheduling/contending overhead.

Khi chỉ có một predicate, số waiter và giao thức được kiểm soát nghiêm ngặt, `notify()` có thể đủ. Nhưng phải **chứng minh** rằng một waiter được chọn bất kỳ đều có thể tiến triển theo protocol, không chỉ hy vọng nó là người mình muốn.

### 7.1 Condition phân chia wait set

`ReentrantLock.newCondition()` cho phép tạo các nhóm chờ riêng, ví dụ `notEmpty` và `notFull`. Khi producer put item, nó có thể `notEmpty.signal()`, tránh đánh thức producer chờ chỗ trống. Tuy nhiên Condition vẫn **không tích lũy tín hiệu tự động** cho waiter đến sau; predicate vẫn là nguồn sự thật.

## 8. ReentrantLock và Condition: cùng lỗi nếu check ngoài lock

Protocol tương ứng với `Condition`:

~~~java
lock.lock();
try {
    while (!ready) {
        condition.await();
    }
} finally {
    lock.unlock();
}
~~~

Writer:

~~~java
lock.lock();
try {
    ready = true;
    condition.signalAll();
} finally {
    lock.unlock();
}
~~~

Đây là **mảnh mã minh họa**, giả định `lock` là `ReentrantLock`, `condition = lock.newCondition()` và `ready` được bảo vệ bởi lock. Method chờ xử lý `InterruptedException`.

`Condition.await()` có những điểm tương ứng `Object.wait()`:

- Caller phải đang sở hữu lock liên kết với Condition.
- Await nhả lock trong lúc chờ, rồi reacquire trước khi trả về.
- Có thể trả về do signal, spurious wakeup hoặc bị interrupt; vẫn dùng `while`.
- `signal()` không lưu token/permit cho thread chưa await.
- `signalAll()` đánh thức tất cả waiter của **Condition cụ thể đó**, không phải mọi Condition thuộc cùng ReentrantLock.

Đừng viết:

~~~java
if (!ready) {              // Đọc ở ngoài lock.
    lock.lock();
    try {
        condition.await(); // Có thể chờ sau khi signal đã xảy ra.
    } finally {
        lock.unlock();
    }
}
~~~

Câu lệnh sai trên tạo cùng cửa sổ lost signal như Object.wait; việc chuyển sang `ReentrantLock` không tự sửa **giao thức**.

## 9. Ví dụ Java 17+: Condition chờ một predicate

Tạo tệp `ConditionSignalDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class ConditionSignalDemo {
    private static final class Gate {
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

        void awaitOpen(CountDownLatch approachingWait)
                throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (!isOpen) {
                    approachingWait.countDown();
                    opened.await();
                }
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Gate gate = new Gate();
        CountDownLatch approachingWait = new CountDownLatch(1);

        Thread consumer = new Thread(() -> {
            try {
                gate.awaitOpen(approachingWait);
                System.out.println("Condition satisfied");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        consumer.start();
        approachingWait.await();
        gate.open();
        consumer.join();
        System.out.println("Done");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ConditionSignalDemo.java
java ConditionSignalDemo
~~~

Stdout trong lần chạy bình thường:

~~~text
Condition satisfied
Done
~~~

Ở đây `CountDownLatch` giúp main biết consumer đã đến vùng kiểm tra điều kiện. Main có thể được schedule trước khi consumer thực sự vào `await()` nhưng vẫn không thể cập nhật `isOpen` khi consumer còn giữ lock. Việc `await()` nhả lock và chuyển sang chờ được phối hợp với signaler lấy lock nên không có check→wait gap.

Khác với intrinsic monitor, `ReentrantLock.lockInterruptibly()` cho phép hủy một thread đang chờ acquire lock bằng interruption. Điều này hữu ích khi task có cancellation contract rõ ràng; nó không loại bỏ nhu cầu kiểm tra predicate bằng while.

## 10. CountDownLatch: trạng thái completion được giữ lại

Khi yêu cầu chỉ là “đợi sự kiện đã hoàn tất một lần”, `CountDownLatch` thường đơn giản và ít lỗi hơn wait/notify.

~~~java
CountDownLatch done = new CountDownLatch(1);

// Producer, có thể gọi trước consumer:
done.countDown();

// Consumer, kể cả khi đến sau:
done.await();
~~~

Đây là mảnh code minh họa. `countDown()` chuyển bộ đếm từ 1 về 0, **trạng thái 0 được lưu lại**. Mọi lần `await()` sau đó trả về ngay nếu bộ đếm đã bằng 0; không mất tín hiệu khi consumer đến muộn.

Đặc điểm:

- Latch là **one-shot**, không reset. Khi cần chu kỳ mới, tạo latch mới hoặc dùng primitive cho nhiều vòng.
- `await()` có thể bị interrupt; overload timeout trả boolean.
- Hành động của producer trước `countDown()` happens-before hành động của thread khác sau `await()` hoàn tất thành công theo memory-consistency effects.
- Latch báo đủ tín hiệu, nhưng **không tự chứa giá trị kết quả hay exception** của task.
- Với nhiều producer, count phải phản ánh đúng số mốc hoàn tất; tín hiệu thiếu khiến waiter có thể đợi mãi.

## 11. Semaphore: permit được giữ lại cho acquire sau

`Semaphore` quản lý số permit. Khi producer gọi `release()` lúc chưa có consumer, số permit tăng (trong giới hạn số nguyên theo hợp đồng), nên consumer `acquire()` đến sau có thể sử dụng permit đó.

~~~java
Semaphore permit = new Semaphore(0);
permit.release();   // Đến trước acquire.
permit.acquire();   // Lấy permit đã được giữ.
~~~

Đây là mảnh code minh họa, yêu cầu import `java.util.concurrent.Semaphore` và xử lý `InterruptedException`.

Khác biệt:

- `Object.notify` không tích lũy notification.
- `Semaphore.release` **tăng số permit**, permit được giữ cho lần acquire sau.
- Permit là **quyền/số lượng**, không phải tự động một payload hay event identity. Nếu có 10 kết quả khác nhau, cần cơ chế dữ liệu/queue tương ứng.
- Khi dùng semaphore để giới hạn concurrency, cần release đúng số permit đã acquire. Nếu release thừa, có thể mở rộng số người truy cập ngoài giới hạn mong muốn.
- Fairness option của semaphore không thay thế deadline/cancellation và không làm business state atomic.

Với one-shot completion, dùng latch thường diễn đạt ý nghĩa rõ hơn. Với đếm tài nguyên hoặc producer/consumer dạng permit, semaphore có thể phù hợp.

## 12. BlockingQueue: giữ cả dữ liệu lẫn điều kiện “có phần tử”

Một `BlockingQueue` thường là lựa chọn tốt hơn `wait()/notify()` tự viết cho producer–consumer:

~~~java
BlockingQueue<String> queue = new ArrayBlockingQueue<>(10);

// Producer:
queue.put("message");

// Consumer:
String message = queue.take();
~~~

Đây là mảnh code minh họa, cần import `BlockingQueue`, `ArrayBlockingQueue` và xử lý `InterruptedException`. Nếu producer put trước consumer take, phần tử được giữ trong queue, không bị “mất thông báo”; consumer lấy dữ liệu thật.

Khác biệt với `notify`: queue lưu **phần tử**, không chỉ cố đánh thức thread. Nó còn định nghĩa API blocking, timeout, capacity, rejection hoặc backpressure tùy implementation.

Tuy nhiên cần chú ý:

- Queue không tự bảo đảm một consumer duy nhất nhận mọi message; nhiều consumer có thể chia việc.
- Queue có thể đầy; `put()` sẽ đợi nếu không còn chỗ, `offer(timeout)` cho phép deadline.
- Nếu producer sửa object mutable **sau khi put**, consumer có thể đua với lần sửa; hãy publish immutable message/snapshot hoặc có protocol riêng.
- Queue không tự giải quyết shutdown/cancellation; có thể dùng interruption, sentinel/poison pill theo hợp đồng hoặc chính sách lifecycle rõ ràng.
- Có memory-consistency effects cho việc chuyển giao phần tử theo hợp đồng concurrent collection/queue.

## 13. Ví dụ Java 17+: các primitive nhớ trạng thái hoặc dữ liệu

Tạo `StoredSignalDemo.java`:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public final class StoredSignalDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        done.countDown();    // Signal xảy ra trước await.
        done.await();
        System.out.println("Latch: completed");

        Semaphore permits = new Semaphore(0);
        permits.release();   // Permit xảy ra trước acquire.
        permits.acquire();
        System.out.println("Semaphore: acquired");

        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);
        queue.put(42);       // Data đến trước take.
        System.out.println("Queue: " + queue.take());
    }
}
~~~

Biên dịch/chạy JDK 17+:

~~~bash
javac StoredSignalDemo.java
java StoredSignalDemo
~~~

Stdout:

~~~text
Latch: completed
Semaphore: acquired
Queue: 42
~~~

Ba primitive lưu lại **ba loại thông tin khác nhau**: latch giữ trạng thái bộ đếm hoàn tất, semaphore giữ permit, queue giữ phần tử dữ liệu. Vì thế đều xử lý đúng tình huống producer tới trước consumer, nhưng không thể thay thế nhau một cách cơ học nếu semantics nghiệp vụ khác nhau.

## 14. Future và CompletableFuture: chờ completion có kết quả

Nếu signal thực chất nghĩa là “một tác vụ đã tính xong hoặc thất bại”, `Future` thường đúng ngữ nghĩa hơn một biến `ready`:

~~~java
ExecutorService executor = Executors.newSingleThreadExecutor();
try {
    Future<Integer> answer = executor.submit(() -> 6 * 7);
    System.out.println(answer.get());
} finally {
    executor.shutdown();
}
~~~

Đây là mảnh code minh họa, cần import `ExecutorService`, `Executors`, `Future` và method xử lý các exception liên quan. `Future.get()` chờ completion, trả kết quả khi thành công, hoặc báo lỗi/huỷ bằng exception thích hợp; task có thể hoàn thành **trước** thời điểm caller gọi get. Completion được lưu trong Future, không bị mất như một notification rỗng.

`CompletableFuture` còn cung cấp combinator như `thenApply`, `thenCompose`, `allOf`. `CompletableFuture.join()` đợi completion và có unchecked `CompletionException` đối với lỗi thay vì checked `ExecutionException` của `Future.get()`. Đừng nhầm `CompletableFuture.join()` với `Thread.join()`; cái sau chờ vòng đời thread.

Trong hệ thống nhiều task, sử dụng result-bearing primitive giúp tránh phải tự phối hợp một cờ ready, một nơi lưu kết quả và một nơi lưu exception rời rạc.

## 15. Timeout, interruption và spurious wakeup

Một chờ đúng về mặt notification vẫn có thể chờ vô hạn nếu producer chết, bỏ quên signal hoặc điều kiện không bao giờ thành sự thật. Hãy thiết kế **đường hủy và deadline** theo yêu cầu.

### 15.1 Timed wait cũng phải kiểm tra predicate

Sai:

~~~java
synchronized (lock) {
    lock.wait(1_000);
    if (!ready) {
        // Không có bảo đảm rõ tại sao wait trả về.
    }
}
~~~

Đây là mảnh mã minh họa. Một lần timed wait không đủ cho protocol vì `wait` có thể trả về do notification không liên quan/spurious wakeup trước deadline. Phải dùng vòng kiểm tra predicate và tính **thời gian còn lại**, nếu không mỗi lần thức dậy rồi wait lại đủ 1.000 ms có thể kéo dài deadline ngoài dự kiến.

Với `Condition`, `awaitNanos` trả khoảng thời gian còn lại theo hợp đồng và phù hợp vòng lặp kiểm tra:

~~~java
boolean awaitReady(long timeout, TimeUnit unit)
        throws InterruptedException {
    long remaining = unit.toNanos(timeout);
    lock.lockInterruptibly();
    try {
        while (!ready) {
            if (remaining <= 0L) {
                return false;
            }
            remaining = condition.awaitNanos(remaining);
        }
        return true;
    } finally {
        lock.unlock();
    }
}
~~~

Đây là mảnh method minh họa: `lock` là `ReentrantLock`, `condition` là Condition liên kết cùng lock, `ready` là predicate. Khi `true` được trả, predicate đúng trong lúc thread đang giữ lock. Khi `false`, deadline đã hết mà predicate chưa được quan sát đúng theo protocol; producer có thể làm ready=true **ngay sau khi method nhả lock**, nên caller phải xử lý race thời điểm kết thúc theo hợp đồng.

### 15.2 InterruptedException không phải notification

`wait()`, `Condition.await()`, `CountDownLatch.await()` và `BlockingQueue.take()` có thể ném `InterruptedException`. Interruption **không** có nghĩa predicate đã đúng hoặc job đã hoàn thành.

Nếu method có thể truyền exception, thường để nó propagate. Trong `Runnable.run()` không khai báo checked exception, có thể xử lý bằng:

~~~java
try {
    awaitWork();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    return;
}
~~~

Đây là mảnh trong method `void run()`, giả định `awaitWork()` là method có thể throw. Việc gọi `Thread.currentThread().interrupt()` khôi phục cờ bị xóa khi `InterruptedException` được ném ở các blocking API thông thường; sau đó thoát hoặc cleanup phù hợp.

**Không** bắt exception rồi gọi `wait()` lại vô hạn và bỏ qua cancellation. Nếu thread đang giữ resource/lock khác, cần thiết kế cleanup để không giữ trạng thái dở dang.

### 15.3 Spurious wakeup

JMM và API cho phép `wait()`/`Condition.await()` thức dậy mà không có notification đúng điều kiện. Đây là lý do **bắt buộc kiểm tra predicate bằng while**, không phải một hiện tượng chỉ có ở một CPU hiếm gặp. Trong kiểm thử, đừng assert “mọi lần wait trả về đều có notify trước đó” như một luật Java.

## 16. Visibility và happens-before: notify không tự thay predicate

Một protocol phối hợp đúng cần cả **atomic handoff** của check→wait và **memory visibility** cho state được công bố.

Khi writer:

~~~java
synchronized (lock) {
    result = 42;
    ready = true;
    lock.notifyAll();
}
~~~

và reader:

~~~java
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
    System.out.println(result);
}
~~~

thì cùng monitor tạo mutual exclusion, và một lần unlock của writer happens-before lần lock sau đó trên cùng monitor của reader. Nhờ đó reader thấy state đã publish theo giao thức, không cần làm tất cả field thành volatile.

`notifyAll` tự nó không biến mọi dữ liệu khác của ứng dụng thành thread-safe. Những truy cập không tuân thủ monitor protocol vẫn có thể race. Tương tự, một `volatile ready` chỉ hữu ích khi read/write được đặt đúng thứ tự; nó không bảo đảm các field khác giữ một invariant nhiều bước nguyên tử.

Với `CountDownLatch`, hành động trước `countDown()` happens-before những hành động sau `await()` thành công; với queue/future, hợp đồng memory-consistency của API xác định đường publish dữ liệu.

### 16.1 Cảnh báo “đồng bộ nhầm monitor”

Sai:

~~~java
synchronized (producerLock) {
    ready = true;
    producerLock.notifyAll();
}

// Consumer:
synchronized (consumerLock) {
    while (!ready) {
        consumerLock.wait();
    }
}
~~~

Đây là mảnh minh họa với **hai object lock khác nhau**. Producer notify trên `producerLock`, còn consumer wait trên `consumerLock`: chúng ở hai wait set khác nhau. Dù cả hai đều gọi synchronized và notify/wait đúng cách về mặt cú pháp, consumer vẫn có thể kẹt.

Trong một giao thức event đơn giản, giữ cùng một `lock` và cùng predicate state; đặt chúng dưới ownership của một object thay vì cho caller tự truyền lock tùy ý.

## 17. notify trước khi release lock có “báo sớm” không?

Một số người sửa bug bằng cách đưa `notify()` ra **ngoài synchronized**:

~~~java
synchronized (lock) {
    ready = true;
}
lock.notifyAll(); // IllegalMonitorStateException.
~~~

Đây là mã lỗi vì caller không còn sở hữu monitor của `lock`. `notify` phải gọi **bên trong** vùng synchronized của cùng object.

Gọi notify trước khi ra khỏi critical section là **bình thường**:

~~~java
synchronized (lock) {
    ready = true;
    lock.notifyAll();
    // Có thể làm nốt ít việc không blocking.
}
~~~

Waiter được notify nhưng sẽ chờ lấy lại monitor cho đến khi writer nhả. Điều này giúp writer có thể hoàn tất update invariant trước khi waiter đọc. Tuy nhiên đừng tiếp tục giữ lock quá lâu sau notify (I/O, callback, sleep), vì nó làm tăng latency của thread đang được đánh thức.

Nguyên tắc đúng là **cập nhật trạng thái và signal dưới lock, rồi nhả lock sớm**, không tách signal ra khỏi lock.

## 18. Lost notification trong producer-consumer và shutdown

Những lỗi signal thường trở nên phức tạp khi hệ thống có nhiều người sản xuất/tiêu thụ hoặc cần dừng.

### 18.1 Nhiều waiter tranh một phần tử

Khi producer đưa **một phần tử** vào buffer rồi notifyAll, nhiều consumer có thể thức dậy. Consumer đầu lấy item, consumer thứ hai đến sau thấy queue trống. Nếu dùng `if` thay `while`, consumer thứ hai có thể cố lấy từ queue trống hoặc trả dữ liệu sai. Vòng `while (empty) wait()` giải quyết điều kiện đã thay đổi trước khi waiter reacquire.

### 18.2 Shutdown cũng cần predicate

Giả sử consumer đợi “queue có phần tử”, trong khi producer đã dừng mãi mãi. Nếu không có predicate `closed` hoặc cơ chế interruption/sentinel, consumer có thể chờ vô hạn. Một pattern rõ ràng là:

~~~text
while queue empty AND not closed:
    await

if queue empty AND closed:
    exit
else:
    take item
~~~

Đây là **mã giả**, không phải Java độc lập. Khi đóng queue, update `closed=true` dưới cùng lock và `signalAll/notifyAll` để đánh thức những waiter đang chờ. Predicate của wait giờ là “queue có phần tử **hoặc** hệ thống đã đóng”. Thread đến sau shutdown cũng thấy `closed` được lưu và không chờ.

Nếu dùng `BlockingQueue`, cần định nghĩa cách kết thúc vì Java BlockingQueue thông thường không có operation `close()` chung cho mọi implementation; cân nhắc interruption, sentinel có hợp đồng, hoặc lifecycle controller riêng.

### 18.3 Các vòng sự kiện cần thế hệ (generation)

Một `boolean ready` chỉ phù hợp nếu trạng thái one-shot false→true. Khi producer có nhiều vòng “đã xong lần thứ N”, một cờ true không phân biệt các thế hệ. Dùng **sequence number/generation** hoặc primitive tương ứng như `Phaser`/`CyclicBarrier` khi bài toán yêu cầu phối hợp theo vòng.

Ví dụ thread consumer ghi nhận `lastSeenVersion`, rồi đợi cho đến khi `version > lastSeenVersion`. Producer tăng version dưới lock trước khi notifyAll; trạng thái version được lưu, nên notification sớm không làm mất thông tin “đã có một vòng mới”. Cần xử lý overflow và số thế hệ nếu thực sự dùng lâu dài.

## 19. Kiểm thử missed signal có tính quyết định

Lỗi lost notification thường “không xảy ra ở máy tôi” vì interleaving hiếm. Kiểm thử bằng `sleep()` có thể pass hàng nghìn lần rồi fail trên CI. Cách thử đáng tin hơn:

1. **Tạo lịch signal-sớm:** producer signal trước, rồi mới cho consumer bắt đầu đợi. Với API đúng, consumer phải nhận biết predicate đã đúng ngay, không block.
2. **Tạo lịch waiter-sớm:** consumer kiểm tra predicate dưới lock, sử dụng latch đánh dấu đã tới điểm wait, rồi producer signal. Đảm bảo completion xảy ra.
3. **Tạo contention:** nhiều consumer cùng chờ, một producer phát item; mọi consumer thức dậy phải kiểm tra lại predicate bằng while, không tiêu thụ trùng.
4. **Thử notification không liên quan:** nếu waiter thức dậy mà predicate vẫn false, phải quay lại chờ chứ không chạy tiếp.
5. **Thử interruption:** waiter rời blocking operation theo policy, không bỏ lock/state dở dang.
6. **Thử timeout:** khi không có producer, method không chờ vô hạn nếu contract có deadline; khi hết thời gian phải báo status rõ.
7. **Thử shutdown:** consumer đã chờ và consumer đến sau khi close đều phải được xử lý đúng.
8. **Thử nhiều vòng:** nếu protocol có generation, các thế hệ không bị bỏ sót theo contract (nếu được yêu cầu lưu từng event, dùng queue).
9. **Dọn test trong finally:** mở latch/cancel/interrupt đúng cách, join worker, tránh test treo và để thread không-daemon sống ngoài test.

### 19.1 Test đúng điều kiện, không test ngẫu nhiên thời gian

Đừng viết:

~~~java
Thread.sleep(20);
producer.signal();
Thread.sleep(20);
assertTrue(consumerCompleted);
~~~

Đây là mảnh lỗi minh họa; `Thread.sleep` không bảo đảm thread đã đến điểm `wait`. Thay vào đó, dùng `CountDownLatch` đánh dấu mốc đồng bộ, `Future.get(timeout)` để giới hạn thời gian test và assert predicate/kết quả.

Khi test cho thấy timeout, kiểm tra stack trace của waiter: thread `WAITING` trong `Object.wait()`, `Condition.await()` hay một blocking queue? Kiểm tra cùng lock hay sai lock, thứ tự update predicate, notification có phát trước wait không, có signal nhầm nhóm không, có thread khác tiêu thụ state trước không.

## 20. Phân biệt lost notification với deadlock và starvation

| Hiện tượng | Nguyên nhân cốt lõi | Dấu hiệu |
| --- | --- | --- |
| **Lost notification** | Event notify xảy ra khi không có waiter, hoặc check→wait gap để waiter bỏ lỡ signal. | Waiter ngủ dù predicate/sự kiện đã xảy ra; không có signal tiếp theo. |
| **Wrong waiter selection** | `notify` đánh thức waiter có predicate vẫn false. | Waiter thức rồi chờ lại; waiter phù hợp vẫn ngủ. |
| **Spurious wakeup** | Wait trả về mà không có event đáp ứng predicate. | Nếu dùng if thay while, code chạy sai. |
| **Deadlock** | Vòng phụ thuộc lock/điều kiện khiến các participant không tiến triển. | Thread A chờ B, B lại chờ A hoặc monitor tương ứng. |
| **Starvation** | Một thread không được phục vụ trong khi thread khác tiếp tục tiến triển. | Có hoạt động tổng thể nhưng waiter cụ thể không hoàn tất. |
| **Visibility bug** | Dữ liệu đọc/ghi thiếu quan hệ HB phù hợp. | Thread có thể không nhìn thấy cập nhật; thêm sleep/log thay đổi kết quả. |

Những lỗi có thể đi cùng nhau. Ví dụ writer notify trên monitor sai khiến waiter chờ mãi, hoặc thread giữ lock rồi `join()` worker cần lock ấy tạo deadlock dù notification protocol của một phần khác đúng.

Không kết luận “lost notification” chỉ vì thread dump cho thấy `WAITING`. Trạng thái đó có thể là trạng thái đúng khi predicate chưa thành. Cần đối chiếu *state, signal, monitor ownership, generation và đường đi của từng thread*.

## 21. Những lỗi phổ biến và cách sửa

| Lỗi | Hệ quả | Cách sửa |
| --- | --- | --- |
| Gọi `notifyAll` trước khi waiter tồn tại, không lưu state. | Waiter đến sau có thể chờ vô hạn. | Lưu predicate `ready`, hoặc dùng latch/semaphore/queue. |
| Kiểm tra `ready` ngoài lock rồi mới `wait()`. | Producer có thể signal giữa check và wait. | Check + wait trong **cùng synchronized block**. |
| Dùng `if (!ready) wait()`. | Spurious wakeup hoặc waiter khác tiêu thụ state. | Dùng `while (!ready) wait()`. |
| Producer sửa predicate ngoài lock nhưng notify trong lock. | Giao thức không có cùng ranh giới đồng bộ. | Update state và notify dưới cùng monitor. |
| Producer và consumer dùng hai object lock khác nhau. | Wait set không khớp, không đánh thức đúng waiter. | Dùng cùng monitor và một owner rõ. |
| Dùng `notify()` khi nhiều loại predicate cùng wait. | Đánh thức sai nhóm, liveness failure. | `notifyAll()` hoặc nhiều Condition. |
| Gọi `notify()` ngoài synchronized. | `IllegalMonitorStateException`. | Sở hữu đúng monitor lúc notify. |
| Sau notify vẫn giữ lock để I/O lâu. | Waiter đã thức nhưng không reacquire được. | Nhả lock sớm, I/O ngoài critical section. |
| Bắt `InterruptedException` rồi lặp lại wait không giới hạn. | Cancellation không có hiệu lực. | Propagate hoặc restore interrupt và thoát an toàn. |
| Dùng `wait(timeout)` một lần rồi coi predicate đúng. | Timeout/spurious wakeup có thể trả về không vì điều kiện. | Loop kiểm tra predicate và deadline. |
| Dùng boolean one-shot cho nhiều vòng sự kiện. | Không biết có generation mới. | Version/generation hoặc queue sự kiện. |
| Producer kết thúc mà không signal shutdown. | Consumer có thể đợi mãi. | Predicate closed/cancellation và notifyAll khi đóng. |
| Dùng `sleep()` thay bảo đảm handshake. | Race chỉ trở nên ngẫu nhiên hơn. | Dùng primitive đồng bộ đúng giao thức. |

## 22. Chọn primitive theo điều bạn thực sự cần nhớ

| Mục tiêu | Lựa chọn Java hợp lý | Tín hiệu đến trước waiter có còn được nhớ? |
| --- | --- | --- |
| Một thay đổi trạng thái đơn giản, predicate tự quản lý | `synchronized` + `while` + `wait/notifyAll` | Có, **nhờ predicate**, không phải do notify. |
| Chờ nhiều predicate trên cùng state có lock | `ReentrantLock` + nhiều `Condition` | Có, nếu **state** được cập nhật/kiểm tra đúng; signal tự nó không tích lũy. |
| Một lần hoàn tất (hoặc N mốc) | `CountDownLatch` | Có, count về 0 được giữ. |
| Đếm permit/quyền truy cập | `Semaphore` | Có, permit đã release có thể được acquire về sau. |
| Chuyển một message từ producer sang consumer | `BlockingQueue` | Có, item nằm trong queue cho tới khi được take, theo dung lượng/policy. |
| Nhận kết quả hoặc lỗi của task | `Future` / `CompletableFuture` | Có, completion/result/error được giữ. |
| Đợi vòng đời một `Thread` kết thúc | `Thread.join()` | Có, completion của thread được nhận biết qua join. |
| Phối hợp nhiều giai đoạn lặp | `Phaser` / `CyclicBarrier` hoặc generation protocol | Theo hợp đồng từng primitive; cần xử lý timeout/broken parties. |

Đừng chọn từ khóa `notifyAll` chỉ vì “cần báo tín hiệu”. Hãy chọn **loại state phải giữ lại**: boolean, count, permit, dữ liệu, kết quả hay thế hệ. Primitive càng gần bài toán thì số nhánh cạnh tranh cần tự chứng minh càng ít.

## 23. Câu hỏi tự kiểm tra

**1.** Producer gọi `notifyAll` khi chưa có waiter. Consumer gọi `wait` sau đó. Consumer có tự nhận notification cũ không?  
**Không.** Notification không được xếp hàng. Consumer chỉ có thể tránh chờ nếu predicate state cho biết công việc đã xong.

**2.** Vì sao `volatile ready` vẫn có thể lost notification nếu consumer đọc ready rồi mới vào synchronized để wait?  
**Vì** producer có thể chuyển ready và notify trong khoảng giữa hai bước. Đây là check→wait race, không chỉ visibility race.

**3.** Vì sao `while (!ready) lock.wait()` đúng hơn `if`?  
**Vì** wait có thể spurious wakeup, hoặc tình trạng đã thay đổi trước khi waiter lấy lại lock; cần kiểm tra predicate mỗi lần.

**4.** `Object.wait()` nhả mọi lock mà thread đang giữ không?  
**Không.** Nó nhả monitor của object được wait, không nhả những monitor khác.

**5.** `notifyAll()` có đảm bảo mọi waiter sẽ chạy ngay không?  
**Không.** Các waiter còn phải reacquire monitor; scheduler và trạng thái predicate quyết định tiến triển.

**6.** Vì sao cập nhật ready rồi notify phải ở cùng synchronized block?  
**Để** việc kiểm tra predicate và chuyển sang wait phối hợp đúng với lần thay state/notify của producer, đồng thời có memory visibility theo monitor.

**7.** Một consumer thức dậy khi buffer đã bị consumer khác lấy hết. Nó nên làm gì?  
**Kiểm tra lại predicate trong while** và chờ tiếp nếu buffer vẫn trống.

**8.** Khi nào `notify()` có rủi ro lớn hơn `notifyAll()`?  
**Khi** cùng wait set có nhiều waiter hoặc nhiều predicate; notify có thể chọn waiter không thể tiến triển.

**9.** `CountDownLatch.countDown()` đến trước `await()` có bị mất không?  
**Không.** Bộ đếm được giữ lại; await trả ngay khi count đã bằng 0.

**10.** `Semaphore.release()` và `Object.notify()` khác nhau ở điểm nào?  
**Release** tạo/tăng permit được giữ lại; **notify** chỉ đánh thức một waiter hiện đang chờ, không lưu event.

**11.** `Condition.signal()` có lưu thông báo cho thread gọi `await()` về sau không?  
**Không.** Vẫn cần predicate state và check/await dưới cùng lock.

**12.** Một lần `wait(1000)` trả về có đồng nghĩa ready đã true không?  
**Không.** Có thể do timeout, notification không liên quan hoặc spurious wakeup; phải kiểm tra ready dưới lock.

**13.** Nếu consumer chờ queue có item nhưng producer đã shutdown vĩnh viễn, cần thêm gì?  
**Một trạng thái closed/cancellation và tín hiệu đánh thức** để consumer không chờ mãi.

**14.** Tại sao log và sleep trong test có thể che giấu lost notification?  
**Vì** chúng thay đổi scheduling và thời gian interleaving; cần latch/barrier để buộc lịch thử cụ thể.

**15.** Cách nghĩ quan trọng nhất khi thiết kế đồng bộ tín hiệu?  
**Lưu sự thật vào state**, dùng một protocol check→wait nguyên tử, rồi xem notification chỉ là cơ chế đánh thức để kiểm tra state lại.

**Ghi nhớ:** Trong `Object.wait()/notify()`, **notification không phải message được lưu trữ**. Hai nguồn gây mất tín hiệu phổ biến nhất là **notify xảy ra trước wait** và **tách check predicate khỏi wait**. Chữa đúng bằng guarded block `synchronized(lock) { while (!predicate) lock.wait(); }`, cập nhật predicate và `notifyAll` dưới cùng monitor, xử lý spurious wakeup, interruption và shutdown. Khi có primitive cấp cao lưu đúng thứ cần nhớ, hãy ưu tiên `CountDownLatch`, `Semaphore`, `BlockingQueue` hoặc `Future` để giảm rủi ro giao thức.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-coordination-010">Poison pill và kết thúc consumer có trật tự</a>

<details>
<summary>Xem chi tiết</summary>

# Poison pill và kết thúc consumer có trật tự trong Java

> **Ý chính:** *Poison pill* là một **thông điệp đặc biệt** được đặt vào luồng công việc (thường qua `BlockingQueue`) để consumer hiểu rằng nó cần **kết thúc vòng nhận việc sau khi xử lý các công việc đã đứng trước tín hiệu đó**. Muốn kết thúc có trật tự, phải kiểm soát thứ tự producer ngừng gửi, thời điểm enqueue poison, số consumer và việc chờ consumer thật sự hoàn tất. **Poison pill không phải lệnh cưỡng bức dừng thread, không phải cơ chế xử lý thất bại và không tự đóng queue hay executor.**

Bài thuộc nhóm **Phối hợp luồng**, tập trung vào Java SE concurrency (`Thread`, `BlockingQueue`, `CountDownLatch`, `ExecutorService`). Không bao gồm cơ chế của Spring; các ví dụ có thể biên dịch/chạy với Java 17+.

## 1. Bài toán: consumer chờ mãi khi không còn producer

Mô hình producer–consumer có một hoặc nhiều producer tạo nhiệm vụ, một hoặc nhiều consumer lấy việc từ queue để xử lý:

~~~text
Producer A ─┐
            ├── put(work) --> [ BlockingQueue ] --> take() --> Consumer
Producer B ─┘
~~~

Consumer thường có vòng lặp:

~~~java
// Snippet minh họa, chưa phải chương trình độc lập.
while (true) {
    Work work = queue.take();
    process(work);
}
~~~

`BlockingQueue.take()` **đợi** khi queue chưa có phần tử. Khi producer đã dừng hẳn và queue đã cạn, consumer có thể tiếp tục chờ vô hạn nếu chương trình **không có một giao thức thông báo kết thúc**.

`BlockingQueue` của Java 17 có `put`, `take`, `offer`, `poll`, nhưng **không có một thao tác close()/shutdown() phổ quát** khiến tất cả reader đang `take()` tự động nhận end-of-stream. Có nhiều giao thức giải quyết vấn đề, và poison pill là một mẫu đơn giản.

## 2. Poison pill hoạt động như thế nào?

Poison pill là một **item được consumer nhận từ chính kênh nhận việc**, nhưng thay vì xử lý như công việc thông thường, consumer nhận ra đây là **end-of-stream** và rời vòng lặp.

~~~text
Producer:   put(W1) --> put(W2) --> put(W3) --> put(STOP)
Queue FIFO: [W1, W2, W3, STOP]
Consumer:   take W1 → process
            take W2 → process
            take W3 → process
            take STOP → break/return
~~~

Đối với một queue FIFO điển hình như `ArrayBlockingQueue` hoặc `LinkedBlockingQueue`, các item được **enqueue trước STOP** sẽ được lấy khỏi queue trước STOP. Nếu consumer **xử lý đồng bộ từng item tới xong trước khi gọi take tiếp**, lúc thấy STOP, toàn bộ công việc phía trước nó đã được xử lý xong bởi consumer đó. Với nhiều consumer, các nhiệm vụ lấy trước STOP có thể vẫn **đang được xử lý đồng thời** ở consumer khác; do đó muốn biết **toàn hệ thống đã xong**, phải chờ mọi consumer kết thúc hoặc có acknowledgment thích hợp.

### Ba loại sự kiện cần phân biệt

| Loại | Ai phát? | Consumer làm gì? | Có nghĩa là hệ thống xong chưa? |
| --- | --- | --- | --- |
| **Work item** | Producer | Thực hiện nghiệp vụ, ghi kết quả hoặc báo lỗi | Chưa |
| **Poison pill / STOP** | Coordinator hoặc producer cuối cùng, đúng thời điểm | Dừng việc lấy thêm item, dọn dẹp và thoát consumer | Chỉ consumer ấy đã nhận lệnh dừng, chưa chắc mọi thread khác xong |
| **Interrupt / cancellation** | Thread khác hoặc môi trường điều khiển | Phản ứng theo chính sách interrupt, có thể thoát sớm | Không tương đương drain toàn bộ work |
| **Shutdown của executor** | Chủ sở hữu executor | Ngừng nhận task mới, thực thi task đã nhận theo hợp đồng | Không tự gửi poison cho một queue ứng dụng |

Mẫu này nên được coi là một **protocol của ứng dụng**, không phải cú pháp đặc biệt hay API built-in tên `PoisonPill` của Java.

## 3. Thiết kế kiểu message: không dùng null hoặc chuỗi trùng dữ liệu

Một cách naïve là dùng String `"STOP"` làm tín hiệu. Nhưng nếu producer có quyền gửi dữ liệu người dùng bằng String, một payload hợp lệ tên `"STOP"` có thể bị hiểu nhầm thành lệnh dừng. Một chuỗi có tên lạ vẫn có rủi ro **collision** hoặc lỗi encoding/normalization.

`BlockingQueue` **không nhận `null` làm phần tử**: `put(null)`/`offer(null)` ném `NullPointerException`. Ngoài ra `poll()` trả `null` để báo không lấy được item, nên null không phải cách biểu diễn tốt cho poison.

Một mẫu mạnh hơn trong Java 17 là **message type rõ ràng**:

~~~java
// Đoạn kiểu dữ liệu minh họa (đặt trong một class Java).
sealed interface Message permits Job, Stop {}
record Job(int id) implements Message {}
enum Stop implements Message { INSTANCE }
~~~

Ở đây `Job` là dữ liệu bình thường, còn `Stop.INSTANCE` là một singleton enum xác định. Consumer kiểm tra bằng **identity** `message == Stop.INSTANCE`; không có payload bình thường nào tự biến thành enum này vì trùng nội dung.

Phân loại:

- **In-band signal:** STOP đi qua chính queue, giữ được quan hệ thứ tự enqueue/dequeue của queue.
- **Out-of-band signal:** một cờ `volatile`, interruption, close của kênh riêng hoặc completion signal. Có thể thích hợp hơn trong một số bài toán, nhưng phải thiết kế cả cách wakeup consumer đang chặn ở `take()`.
- **Completion acknowledgement:** sau khi phát STOP, chủ sở hữu vẫn phải `join()`/`Future.get()`/đợi latch để biết consumer đã thật sự rời vòng chạy.

## 4. Ví dụ 1 — một producer, một consumer, FIFO và kết thúc đúng thứ tự

Tạo tệp `SingleConsumerPoison.java` (Java 17+):

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class SingleConsumerPoison {
    sealed interface Message permits Job, Stop {}
    record Job(int id) implements Message {}
    enum Stop implements Message { INSTANCE }

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Message> queue = new ArrayBlockingQueue<>(2);

        Thread consumer = new Thread(() -> {
            try {
                while (true) {
                    Message message = queue.take();
                    if (message == Stop.INSTANCE) {
                        System.out.println("consumer stopped");
                        return;
                    }

                    Job job = (Job) message;
                    System.out.println("processed " + job.id());
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        consumer.start();

        queue.put(new Job(1));
        queue.put(new Job(2));
        queue.put(Stop.INSTANCE); // Sau tất cả work; capacity=2 vẫn ổn.
        consumer.join();

        System.out.println("joined = " + !consumer.isAlive());
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac SingleConsumerPoison.java
java SingleConsumerPoison
~~~

Stdout trong điều kiện bình thường, không có interruption bên ngoài:

~~~text
processed 1
processed 2
consumer stopped
joined = true
~~~

**Diễn giải theo hợp đồng:**

1. `ArrayBlockingQueue` là FIFO. Main enqueue hai `Job` theo thứ tự 1, 2 và sau đó mới enqueue `Stop.INSTANCE`.
2. Vì queue chỉ chứa tối đa hai item, `put(Stop.INSTANCE)` **có thể block** khi queue đầy; consumer lấy item tạo thêm chỗ trống. Đây là **backpressure**, không phải lỗi.
3. Consumer xử lý từng `Job` ngay trong vòng nhận việc và chỉ dừng sau khi `take()` trả `Stop.INSTANCE`.
4. `consumer.join()` chờ **thread consumer kết thúc** trước khi main in trạng thái cuối. Nếu bỏ `join()`, việc đã enqueue STOP không đủ chứng minh consumer đã xử lý xong.
5. Không gọi `Thread.stop()` và không cần `Thread.sleep()` để “đợi cho đủ lâu”.

Vì chỉ có một consumer, thứ tự in công việc là xác định. Với nhiều consumer, thứ tự **hoàn thành** các nhiệm vụ khác nhau có thể không trùng thứ tự lấy từ queue.

**Điều kiện ngầm:** producer không được tiếp tục enqueue thêm work **sau** STOP. Nếu một item nằm phía sau STOP, consumer có thể thoát và bỏ item đó lại trong queue.

## 5. Giao thức shutdown có trật tự: đóng admission → đợi producer → phát STOP → đợi consumer

Đây là chuỗi phối hợp nên thiết kế **ngay từ đầu**, không chỉ thêm một dòng `queue.put(POISON)` trong `finally`:

~~~text
RUNNING
   |
   | 1. Không nhận/yêu cầu công việc mới nữa
   v
STOP_ACCEPTING
   |
   | 2. Producer đang chạy hoàn tất việc enqueue hoặc báo lỗi
   v
PRODUCERS_FINISHED
   |
   | 3. Coordinator enqueue đúng số STOP
   v
STOP_SENT
   |
   | 4. Consumer xử lý item trước STOP, nhận STOP rồi thoát
   v
CONSUMERS_TERMINATED
   |
   | 5. Thu kết quả, đóng tài nguyên và công bố trạng thái cuối
   v
CLOSED
~~~

**Quan trọng:** bước 1 và 2 là chuyện của *producer lifecycle*. Nếu vẫn cho một producer mới bắt đầu sau khi đã phát STOP, protocol bị phá. Bước 4 là chuyện của *consumer lifecycle*. Không được gọi toàn bộ hệ thống đã hoàn tất chỉ vì STOP đã vào queue.

### Ai được gửi poison?

Cách rõ nhất là một **coordinator** (thread điều phối) biết tất cả producer đã ngừng phát work, sau đó mới enqueue STOP. Với một producer duy nhất, producer có thể gửi poison ở cuối nếu nó thực sự sở hữu lifecycle của toàn bộ dòng công việc. Với nhiều producer độc lập, **mỗi producer tự gửi một STOP khi xong** thường sai: producer nhanh có thể phát STOP trong khi producer khác còn tạo work.

Nếu có nhiều producer, dùng `Thread.join()`, `Future.get()` hoặc `CountDownLatch` để xác nhận **tất cả đã kết thúc**, kiểm tra lỗi của họ, rồi coordinator mới phát STOP. Lưu ý `join` chỉ xác nhận thread kết thúc; không tự chứng minh producer đã enqueue đủ dữ liệu nếu producer bị hủy hoặc lỗi giữa chừng.

## 6. Nhiều consumer: thường cần một poison pill cho mỗi consumer đang đợi

Giả sử có hai consumer dùng chung một queue. Mỗi `take()` **lấy và loại bỏ** phần tử khỏi queue; poison pill không được broadcast tự động đến tất cả người đọc.

~~~text
Queue: [Job 1, Job 2, STOP, STOP]

Consumer A: take Job 1 ... take STOP -> exit
Consumer B: take Job 2 ... take STOP -> exit
~~~

Mỗi consumer xử lý một STOP rồi **thoát**, nên với `N` consumer có thể đang block ở `take()`, một protocol đơn giản gửi **N STOP messages** sau khi toàn bộ producer đã dừng. Đây là quy tắc **đếm theo số consumer**, không theo số producer và không theo số work item.

Nếu chỉ gửi một STOP cho hai consumer, một consumer thoát, consumer còn lại có thể chờ vô hạn khi queue trống. Nếu số consumer có thể thay đổi hoặc consumer có thể chết trước khi nhận STOP, số lượng STOP phải được quản lý theo **đăng ký/ownership thực tế**, hoặc dùng cơ chế shutdown khác.

### Ví dụ 2 — hai consumer, hai STOP, tổng kết không phụ thuộc scheduling

Tạo `MultipleConsumersPoison.java` (Java 17+):

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MultipleConsumersPoison {
    sealed interface Message permits Job, Stop {}
    record Job(int id) implements Message {}
    enum Stop implements Message { INSTANCE }

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Message> queue = new ArrayBlockingQueue<>(3);
        ConcurrentLinkedQueue<Integer> processed = new ConcurrentLinkedQueue<>();
        AtomicInteger stopped = new AtomicInteger();

        Runnable consumerWork = () -> {
            try {
                while (true) {
                    Message message = queue.take();
                    if (message == Stop.INSTANCE) {
                        stopped.incrementAndGet();
                        return; // Consumer này chỉ lấy một STOP.
                    }
                    processed.add(((Job) message).id());
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        };

        Thread a = new Thread(consumerWork, "consumer-a");
        Thread b = new Thread(consumerWork, "consumer-b");
        a.start();
        b.start();

        for (int id = 1; id <= 6; id++) {
            queue.put(new Job(id));
        }
        queue.put(Stop.INSTANCE);
        queue.put(Stop.INSTANCE);

        a.join();
        b.join();

        List<Integer> sorted = new ArrayList<>(processed);
        Collections.sort(sorted);

        System.out.println("processed = " + sorted);
        System.out.println("stopped = " + stopped.get());
        System.out.println("queue empty = " + queue.isEmpty());
    }
}
~~~

Chạy:

~~~bash
javac MultipleConsumersPoison.java
java MultipleConsumersPoison
~~~

Đầu ra xác định khi hai consumer hoạt động bình thường:

~~~text
processed = [1, 2, 3, 4, 5, 6]
stopped = 2
queue empty = true
~~~

Không in tên thread nhận từng công việc vì **phân phối công việc giữa A/B không xác định**; thay vào đó thu kết quả bằng `ConcurrentLinkedQueue`, sau khi `join` cả hai thì sort để xác minh đủ 6 ID.

Hai STOP nằm **sau tất cả sáu work items** trong một FIFO queue. Mỗi consumer nhận STOP sẽ return, không tiếp tục lấy STOP thứ hai; như vậy cả hai cuối cùng đều có tín hiệu. Sau khi hai thread kết thúc, không còn work được enqueue phía sau STOP nên queue trống trong kịch bản này.

**Đừng lạm dụng `queue.isEmpty()` để xác định đã xử lý xong khi consumer vẫn chạy.** Một worker có thể đã lấy item ra khỏi queue nhưng đang thực hiện công việc mất nhiều thời gian. `isEmpty()` chỉ là ảnh chụp queue tại thời điểm đọc, không phải acknowledgment hoàn thành.

## 7. Nhiều producer: đợi mọi nguồn việc hoàn tất trước khi phát STOP

Giả sử producer A và B tạo các Job khác nhau. Nếu A tự gửi STOP khi xong, B có thể vẫn đưa thêm Job sau STOP, khiến consumer thoát sớm. Thứ tự đúng là một coordinator đợi **cả A và B**, sau đó mới phát STOP.

### Ví dụ 3 — hai producer, một consumer, coordinator phát poison

Tạo `CoordinatedProducers.java` (Java 17+):

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class CoordinatedProducers {
    sealed interface Message permits Job, Stop {}
    record Job(int id) implements Message {}
    enum Stop implements Message { INSTANCE }

    private static Thread producer(
            BlockingQueue<Message> queue, int from, int to) {
        return new Thread(() -> {
            try {
                for (int id = from; id <= to; id++) {
                    queue.put(new Job(id));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Message> queue = new ArrayBlockingQueue<>(3);
        List<Integer> processed = new ArrayList<>();

        Thread consumer = new Thread(() -> {
            try {
                while (true) {
                    Message message = queue.take();
                    if (message == Stop.INSTANCE) {
                        return;
                    }
                    processed.add(((Job) message).id());
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });

        Thread firstProducer = producer(queue, 1, 3);
        Thread secondProducer = producer(queue, 4, 6);

        consumer.start();
        firstProducer.start();
        secondProducer.start();

        firstProducer.join();
        secondProducer.join();

        // Chỉ coordinator gửi STOP sau KHI CẢ HAI producer đã kết thúc.
        queue.put(Stop.INSTANCE);
        consumer.join();

        Collections.sort(processed);
        System.out.println("processed = " + processed);
        System.out.println("remaining = " + queue.size());
    }
}
~~~

Chạy:

~~~bash
javac CoordinatedProducers.java
java CoordinatedProducers
~~~

Đầu ra trong điều kiện không bị ngắt:

~~~text
processed = [1, 2, 3, 4, 5, 6]
remaining = 0
~~~

Hai producer có thể xen kẽ enqueue Job ID theo thứ tự không đoán trước; chỉ **tập hợp** Job nhận được là xác định. `Collections.sort(processed)` chạy ở main **sau** khi `consumer.join()` nên không có data race trên `ArrayList`: chỉ một consumer sửa list, main đọc sau khi consumer kết thúc.

**Giới hạn production:** Producer demo trên bắt `InterruptedException` và thoát nhưng không báo lỗi riêng. Nếu một producer bị interrupt trước khi gửi đủ 3 Job, coordinator vẫn có thể gửi STOP và program không được coi đó là “đã xử lý đủ yêu cầu”. Trong hệ thống thật, cần thu **producer result/failure** bằng `Future.get()`, một kênh error, hoặc status rõ ràng và áp dụng policy: drain phần đã nhận, retry phần chưa gửi, hoặc chuyển sang shutdown bất thường. **Kết thúc thread không đồng nghĩa producer thành công.**

Nếu cần bảo đảm tuyệt đối producer không gửi sau điểm đóng admission, phải có một cơ chế ownership/gate hoặc một coordinator quản lý các job sources; chỉ nhìn `queue.size() == 0` không cho biết các producer đã dừng.

## 8. Ví dụ sai có thể tái hiện chắc chắn: STOP được enqueue quá sớm

Một lỗi nguy hiểm là phát STOP **trước khi các producer thật sự dừng**. Điều này không chỉ là race hiếm: ta có thể dựng một lịch xen kẽ xác định để thấy một Job bị bỏ lại.

Tạo `EarlyPoisonBug.java` (Java 17+):

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class EarlyPoisonBug {
    sealed interface Message permits Job, Stop {}
    record Job(int id) implements Message {}
    enum Stop implements Message { INSTANCE }

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
        CountDownLatch sendLateJob = new CountDownLatch(1);
        List<Integer> processed = new ArrayList<>();

        Thread lateProducer = new Thread(() -> {
            try {
                sendLateJob.await();
                queue.put(new Job(2)); // Đến SAU STOP.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });

        Thread consumer = new Thread(() -> {
            try {
                while (true) {
                    Message message = queue.take();
                    if (message == Stop.INSTANCE) {
                        return;
                    }
                    processed.add(((Job) message).id());
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });

        consumer.start();
        lateProducer.start();

        queue.put(new Job(1));
        queue.put(Stop.INSTANCE); // LỖI: lateProducer chưa xong!
        sendLateJob.countDown();

        lateProducer.join();
        consumer.join();

        System.out.println("processed = " + processed);
        System.out.println("leftover = " + queue.peek());
    }
}
~~~

Chạy:

~~~bash
javac EarlyPoisonBug.java
java EarlyPoisonBug
~~~

Đầu ra được điều khiển bởi thứ tự enqueue:

~~~text
processed = [1]
leftover = Job[id=2]
~~~

Dù consumer và producer nào được OS chạy trước, các phép enqueue có thứ tự: `Job(1)` → `Stop` → `Job(2)`. Một consumer duy nhất xử lý Job 1 rồi gặp STOP và return. Job 2 không có consumer nào xử lý, còn nằm trong queue.

**Cách sửa:** không phát STOP cho tới khi late producer và mọi producer khác đã xác nhận hoàn tất việc enqueue; trong demo, thứ tự đúng là phát `sendLateJob.countDown()`, đợi `lateProducer.join()` rồi mới `queue.put(Stop.INSTANCE)`. Nhưng còn phải xử lý trường hợp producer kết thúc vì lỗi, không chỉ kết thúc thread.

## 9. `BlockingQueue` capacity và nguy cơ shutdown bị kẹt

Poison pill là **một phần tử thông thường** đối với queue. Nếu dùng bounded queue, khi queue đầy:

~~~java
queue.put(Stop.INSTANCE); // Có thể BLOCK tới khi có consumer lấy item.
~~~

Trong shutdown bình thường, chờ chỗ trống thường hợp lệ: các consumer đang drain work sẽ tạo capacity. Nhưng có các trường hợp **không tiến triển**:

- Không còn consumer sống để `take()`.
- Consumer bị kẹt ở I/O hoặc lỗi nghiệp vụ không thoát; queue vẫn đầy.
- Các producer đang chờ `put` nhưng coordinator phát STOP trong khi consumer chưa được start/scheduled.
- Dùng executor có số thread quá nhỏ: mọi worker pool đều đang chạy producer bị block ở `put`, còn consumer task **xếp hàng trong cùng pool và chưa được cấp worker**.
- Coordinator giữ một lock mà consumer cần để hoàn thành, đồng thời coordinator đang chờ `put(POISON)` tạo thành vòng chờ.
- Consumer gặp STOP và thoát nhưng còn Job phía sau, không còn thread nào xử lý chúng.

Vì vậy, cần chiến lược về **timeout, interrupt, lỗi và tài nguyên**. Đối với bounded queue, có thể dùng `offer(poison, timeout, unit)` để giới hạn thời gian chờ enqueue:

~~~java
// Snippet trong phương thức có thể ném InterruptedException.
boolean inserted = queue.offer(
        Stop.INSTANCE, 5, java.util.concurrent.TimeUnit.SECONDS);
if (!inserted) {
    // Chuyển sang nhánh shutdown lỗi/timeout; KHÔNG giả định đã dừng.
}
~~~

**Rất quan trọng:** `offer` trả `false` nghĩa là poison **chưa được đưa vào queue**. Không được đánh dấu consumer đã nhận STOP hoặc gọi shutdown thành công. Nhánh fallback phải có nghĩa thực: báo lỗi cho coordinator, thử lại có giới hạn, interrupt/cancel consumer, hoặc chuyển trạng thái hệ thống theo policy.

`offer` timed không tự bảo đảm việc xử lý xong trong 5 giây. Nó chỉ giới hạn **thời gian chờ chỗ trống để enqueue**.

### `put` và `take` đều interruptible

`BlockingQueue.put`/`take` có thể ném `InterruptedException`. Không nên bắt exception rồi lặng lẽ tiếp tục vô hạn. Với consumer muốn thoát khi bị ngắt:

~~~java
// Snippet minh họa, không phải chương trình độc lập.
try {
    while (true) {
        Message message = queue.take();
        if (message == Stop.INSTANCE) {
            break;
        }
        process(message);
    }
} catch (InterruptedException interrupted) {
    Thread.currentThread().interrupt();
    // Dọn dẹp và báo kết thúc bất thường; có thể còn work trong queue.
} finally {
    cleanupConsumerResources();
}
~~~

`Thread.currentThread().interrupt()` khôi phục cờ interrupt khi tầng hiện tại không xử lý tiếp; cụ thể nên thoát task, để chủ sở hữu quyết định trạng thái/hủy phù hợp. Không nhầm việc consumer thoát do interruption với **graceful drain** của poison pill.

## 10. Consumer gặp exception khi xử lý một Job

Poison pill chỉ điều phối **điểm kết thúc bình thường**. Khi `process(job)` ném exception, cần policy riêng:

| Policy | Ý nghĩa | Rủi ro/đổi chác |
| --- | --- | --- |
| **Fail-fast** | Consumer dừng và báo lỗi cho coordinator | Queue có thể còn backlog, poison có thể không được nhận |
| **Retry có giới hạn** | Retry với backoff/deadline | Cần tránh lặp vô hạn, idempotency với side effects |
| **Dead-letter** | Lưu thất bại ở kênh riêng rồi tiếp tục | Cần bảo đảm lưu/report lỗi và quyền truy cập |
| **Skip và ghi nhận** | Bỏ Job lỗi, xử lý những Job khác | Chỉ hợp lệ khi nghiệp vụ chấp nhận dữ liệu thiếu |
| **Cancel toàn pipeline** | Dừng producer và consumer, giải phóng tài nguyên | Không phải graceful shutdown; phải xử lý item đang dở |

Mọi lựa chọn cần trả lời: Job hiện tại được coi là **done**, **failed** hay **pending**? Nếu consumer bỏ qua exception và tiếp tục nhận STOP, việc consumer kết thúc **không chứng minh tất cả Job thành công**.

### Ack sau xử lý, không phải sau `take`

Nếu cần cam kết hoàn thành, hãy tạo/ghi nhận acknowledgment **sau** khi `process(job)` thành công. `take()` chỉ chứng minh item đã rời queue, không chứng minh kết quả đã được lưu. Đối với công việc có side effect ngoài bộ nhớ JVM (database/mạng), còn phải thiết kế idempotency và cách xử lý việc bị gián đoạn giữa xử lý và ack.

Poison pill không tự đảm bảo **exactly-once processing**. Nó là tín hiệu end-of-stream của giao thức queue, không phải transaction hoặc durability mechanism.

## 11. Phối hợp với `ExecutorService`: poison và shutdown là hai lớp khác nhau

Có hai vòng đời:

- **Consumer loop** do ứng dụng viết: thường `queue.take()` lặp vô hạn cho tới STOP hoặc cancellation.
- **ExecutorService** chạy task consumer: sở hữu worker threads, nhận task mới và cung cấp cơ chế `shutdown()`/`awaitTermination()`.

Gọi `executor.shutdown()` **không tự thêm STOP vào queue của ứng dụng**. Nếu một consumer task đã được submit và đang kẹt ở `take()`, graceful executor shutdown vẫn phải chờ task đó tự kết thúc; nếu không có STOP/cancellation, executor có thể không terminated.

Ngược lại, consumer nhận STOP và return **không tự đóng executor**. Chủ sở hữu vẫn phải gọi shutdown theo chính sách phù hợp.

Một quy trình logic:

~~~text
A. Ngừng tiếp nhận work mới vào hệ thống.
B. Đợi producer tasks hoàn thành (kiểm tra Future.get để biết lỗi).
C. Enqueue đủ STOP cho các consumer thực sự đang nhận queue.
D. Đợi consumer tasks kết thúc qua Future.get/join.
E. ExecutorService.shutdown và awaitTermination nếu đang quản lý executor.
F. Đóng file/socket/tài nguyên khác theo policy; ghi nhận kết quả pipeline.
~~~

Nếu `ExecutorService` dùng để quản lý cả producer và consumer, đảm bảo **đủ worker để consumer thực sự chạy** trong lúc producer đợi bounded queue. Ví dụ một pool có 2 thread, 2 producer đã chiếm cả hai và block tại `put`, còn consumer mới chỉ đang ở queue của executor: đây là **thread-pool starvation deadlock**. Có thể dùng pool riêng, số worker phù hợp hoặc kiến trúc không block producer khi không có consumer.

### So sánh các API dừng

| Cơ chế | Làm gì? | Không làm gì? |
| --- | --- | --- |
| `queue.put(STOP)` | Gửi tín hiệu bằng queue, giữ thứ tự của các lần enqueue hợp lệ | Không tự đợi consumer; không ép thread dừng |
| `Thread.join()` | Đợi một thread kết thúc | Không tự báo lỗi xử lý work, không đặt STOP |
| `Future.get()` | Đợi task và lấy kết quả/exception của task được submit | Không tự đóng queue |
| `ExecutorService.shutdown()` | Không nhận task mới, cho task đã nhận tiếp tục theo hợp đồng | Không tự phát STOP vào queue tùy ý |
| `ExecutorService.awaitTermination(...)` | Đợi executor terminated sau shutdown | Không tự sửa một consumer kẹt `take()` |
| `ExecutorService.shutdownNow()` | Cố interrupt task đang chạy và trả task chưa chạy, best effort | Không bảo đảm drain work hoặc terminate tức thì |
| `Thread.interrupt()` | Phát tín hiệu interrupt cho thread | Không bảo đảm mọi code dừng ngay hay xử lý xong backlog |

Với mục tiêu **graceful drain**, ưu tiên cho consumer tự thấy STOP và return; dùng timeout/cancellation path riêng khi shutdown không thể tiến triển hoặc yêu cầu khẩn cấp. `shutdownNow()` không tương đương “poison pill nhanh hơn”.

## 12. FIFO ordering và giới hạn của tính trật tự

Một nguyên tắc hay bị hiểu sai: “STOP được enqueue sau Job thì các Job chắc chắn đã hoàn thành trước mọi STOP được xử lý”. Cần phân biệt:

1. **Thứ tự enqueue:** Trong một FIFO queue, Job được enqueue trước poison nằm trước nó theo thứ tự queue.
2. **Thứ tự dequeue:** Các item trước STOP được lấy trước STOP (bỏ qua các thao tác khác làm thay đổi queue như remove/drain).
3. **Thứ tự xử lý:** Nếu chỉ **một consumer**, xử lý synchronous trước lần `take` tiếp, mỗi Job trước STOP được xử lý trước khi consumer thoát.
4. **Nhiều consumer:** Một consumer có thể nhận STOP và thoát trong khi consumer khác **vẫn xử lý Job đã lấy trước đó**. Vì vậy đợi **tất cả consumers** hoặc nhận đủ ack mới kết luận pipeline đã hoàn thành.
5. **Consumer dispatch async:** Nếu một consumer chỉ lấy Job rồi submit sang executor khác và ngay lập tức `take` tiếp, nó có thể nhận STOP và thoát **trước khi các task đã chuyển tiếp hoàn tất**. Cần đợi downstream tasks riêng.

Không phải mọi implementation `BlockingQueue` đều giữ FIFO theo cùng semantics. Ví dụ `PriorityBlockingQueue` lấy theo **priority/comparator**, vì vậy STOP không nhất thiết đứng sau Job dù enqueue sau; một poison có priority cao có thể được lấy trước backlog. `DelayQueue` chỉ trả những phần tử đã hết delay. `SynchronousQueue` không có vùng lưu backlog; `put` trực tiếp trao phần tử cho consumer đợi. **Giao thức phải phù hợp với queue thực sự chọn.**

Nếu dùng `PriorityBlockingQueue`, hãy thiết kế thứ tự poison sao cho không phá backlog và vẫn bảo đảm không còn producer enqueue Job, hoặc chọn kênh completion riêng. Với nhiều worker và priority khác nhau, cần giải bài toán termination cụ thể; không mang quy tắc FIFO sang queue khác rồi kết luận luôn đúng.

## 13. Tín hiệu STOP nằm cùng queue mang lại điều gì?

`BlockingQueue` có bảo đảm *memory consistency*: hành động producer **trước khi** đặt một object vào queue *happen-before* hành động consumer **sau khi** lấy object ấy khỏi queue. Điều này giúp truyền dữ liệu work đã chuẩn bị tới consumer an toàn, nếu dữ liệu đó không bị mutate vô kỷ luật sau publication.

Ngoài ra:

- STOP đi qua cùng đường nhận việc nên consumer không cần polling cờ liên tục.
- Với queue FIFO, STOP có thứ tự tương đối rõ ràng với các work item đã được enqueue trước nó.
- Consumer có một nhánh chuyển trạng thái đơn giản: Work → process, Stop → cleanup/return.
- Hệ thống vẫn phải có cơ chế **dừng admission** và **đợi acknowledgments**. Queue memory visibility không tự thay các yêu cầu này.

**Không nên kết luận:** vì producer đã `put(job)` thì job chắc chắn được xử lý thành công; queue chỉ xác nhận enqueue (đối với `put` đã trả về), còn việc xử lý cần ack/result riêng.

## 14. Tổng hợp trạng thái shutdown và lỗi cần kiểm tra

Một coordinator có thể quản lý pipeline qua trạng thái:

| Trạng thái | Có nhận Job mới? | Producer còn tạo Job? | Consumer hoạt động? | Điều cần quan sát |
| --- | --- | --- | --- | --- |
| `RUNNING` | Có | Có | Có | Độ sâu queue, lỗi, tốc độ xử lý |
| `STOP_ACCEPTING` | Không | Đang xả việc đã nhận | Có | Producer hoàn thành hoặc failure |
| `SIGNALING_STOP` | Không | Không | Có | Enqueue đủ STOP thành công |
| `DRAINING` | Không | Không | Một phần còn hoạt động | Job in flight, ack, lỗi |
| `TERMINATED` | Không | Không | Không | Task join/Future.get đã xong, final status |
| `FAILED/CANCELLED` | Theo policy | Có thể dừng sớm | Có thể bị ngắt | Backlog/in-flight/dead-letter/khả năng retry |

Bảng là **mô hình ứng dụng**, không phải enum có sẵn trong JDK. Dấu chấm hết của consumer không đương nhiên là một trạng thái business success. Với producer lỗi, phải định nghĩa số lượng Job thực sự được chấp nhận và kết quả mong đợi.

### Checklist thiết kế

- **Nguồn work:** Ai được phép enqueue? Làm sao chặn producer mới khi chuẩn bị đóng?
- **Số consumer:** Cố định hay co giãn? Ai biết con số để phát STOP?
- **Tín hiệu:** Có kiểu riêng không? Có thể nhầm với payload hay null không?
- **Queue:** FIFO hay priority/delay/handoff? Có bounded capacity không?
- **Thứ tự:** STOP có chắc sau **mọi item đã được chấp nhận**?
- **Producer failure:** `join` hay `Future.get` có đủ để xác nhận thành công? Cần ghi nhận thiếu item không?
- **Consumer failure:** Consumer chết khi còn backlog thì STOP sẽ được ai nhận? Có giám sát và cancellation path không?
- **Ack:** “lấy khỏi queue” hay “xử lý thành công” là tiêu chí hoàn tất?
- **Timeout:** `put(STOP)` và `join` có thể chờ vô hạn không? Nếu timeout sẽ làm gì?
- **Downstream:** Consumer có submit async task khác không? Đã đợi downstream completion chưa?
- **Tài nguyên:** Connection/file/socket/callback cleanup đặt ở `finally` nào? Ai đóng executor?
- **Quan sát:** Metrics accepted/processed/failed/in-flight/stopped count có ý nghĩa nhất quán không?

## 15. Những sai lầm thường gặp

| Sai lầm | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| Chỉ gửi một poison cho nhiều consumer | Consumer còn lại có thể chờ `take()` mãi | Một STOP cho mỗi consumer theo protocol, hoặc cơ chế broadcast khác |
| Mỗi producer tự gửi STOP khi xong | STOP có thể đứng trước work của producer khác | Coordinator đợi **mọi producer** rồi phát STOP |
| Enqueue STOP khi admission còn mở | Item đến sau STOP bị bỏ lại | Ngừng nhận mới, đợi nguồn đang chạy, mới enqueue poison |
| Dùng chuỗi `"STOP"` trùng dữ liệu | Payload hợp lệ bị hiểu thành lệnh dừng | Kiểu message riêng/enum sentinel |
| Dùng `null` làm poison | `BlockingQueue` không nhận null | Sentinel non-null có kiểu rõ ràng |
| Gọi `queue.clear()` rồi phát STOP | Mất backlog chưa xử lý | Phân biệt abort với graceful drain |
| Không `join`/`Future.get` sau phát STOP | Bắt đầu đóng tài nguyên khi consumer còn chạy | Đợi termination và kiểm tra lỗi |
| Dựa vào `queue.isEmpty()` làm bằng chứng done | Job đã dequeue còn đang xử lý | Theo dõi in-flight, ack, task completion |
| Bounded queue đầy, `put(POISON)` vô hạn | Coordinator shutdown có thể treo | Timeout/fallback và giám sát consumer |
| Consumer bị interrupt nhưng coi là graceful | Work có thể còn backlog | Báo cancel/failure, kế hoạch retry/cleanup |
| Consumer exception rồi âm thầm thoát | Poison bị bỏ lại, mất Job | Báo lỗi, điều phối restart/cancel |
| Producer/consumer cùng pool quá nhỏ | Producer block làm consumer không được chạy | Thiết kế capacity/pool, pool tách hoặc nonblocking |
| Dùng `PriorityBlockingQueue` và tin enqueue order | STOP được lấy trước backlog | Xét comparator/priority, chọn protocol khác |
| Consumer dispatch async rồi nhận STOP là xong | Downstream task chưa hoàn tất | Đợi các Future/ack downstream |
| Dùng `shutdownNow` để thay poison | Ngắt xử lý, không drain backlog | Chọn rõ graceful hay immediate cancellation |
| Tin poison bảo đảm exactly-once | Không xử lý crash, retry, side effects | Idempotency, ack, persistent queue/transaction phù hợp |

## 16. Tự kiểm tra

**Câu 1.** Có ba consumer cùng `take()` từ một `ArrayBlockingQueue` FIFO, sau khi mọi producer ngừng, coordinator chỉ enqueue một poison. Đã đủ để cả ba rời vòng lặp chưa?  
**Đáp án:** Chưa. Một `take` loại bỏ poison, nên chỉ một consumer nhận; theo giao thức mỗi consumer nhận một STOP, cần ba STOP hoặc giải pháp khác.

**Câu 2.** Producer A gửi xong và enqueue STOP, producer B vẫn có thể enqueue Job. Bài toán là gì?  
**Đáp án:** Consumer có thể gặp STOP trước Job của B rồi thoát, để lại backlog. Phải đóng admission/đợi mọi producer trước khi phát STOP.

**Câu 3.** `queue.isEmpty() == true` nhưng consumer đang thực hiện Job vừa `take()`. Có thể đóng database ngay không?  
**Đáp án:** Không thể dựa vào `isEmpty()`. Job đã rời queue nhưng chưa xử lý xong; cần completion acknowledgment.

**Câu 4.** `BlockingQueue.put(STOP)` có thể chặn dù việc gửi poison đúng lúc không?  
**Đáp án:** Có, nếu queue bounded đang đầy. Trong graceful shutdown, consumer tiến triển sẽ tạo capacity. Nếu consumer chết/kẹt, cần timeout và failure policy.

**Câu 5.** Tại sao `BlockingQueue.put(null)` không thể là poison?  
**Đáp án:** Queue không chấp nhận null; các thao tác enqueue null ném `NullPointerException`.

**Câu 6.** Một consumer nhận STOP rồi return, executor có tự terminated ngay không?  
**Đáp án:** Không. Executor có lifecycle riêng, vẫn cần `shutdown`/`awaitTermination` phù hợp.

**Câu 7.** Gọi `ExecutorService.shutdown()` trước khi consumer task thoát có tự khiến `take()` trả về end-of-stream không?  
**Đáp án:** Không. Shutdown chỉ ngăn task mới; consumer task đã được nhận vẫn chờ queue theo logic của nó.

**Câu 8.** Producer gọi `put(Job)` thành công, có đồng nghĩa Job được xử lý thành công không?  
**Đáp án:** Không. Put chỉ xác nhận đã enqueue. Cần ack sau processing hoặc kết quả task.

**Câu 9.** Nếu consumer lấy Job rồi submit công việc cho executor khác, sau đó nhận STOP, đã hoàn thành mọi Job trước STOP chưa?  
**Đáp án:** Chưa chắc. Consumer mới hoàn tất dispatch; phải đợi downstream task completion.

**Câu 10.** Tại sao `PriorityBlockingQueue` cần thiết kế poison khác với FIFO queue?  
**Đáp án:** Vì dequeue theo comparator/priority, không phải thứ tự enqueue; STOP có thể được lấy trước các Job được enqueue trước nó.

## Tài liệu chính thức

- [`BlockingQueue` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): `put`/`take`, timed `offer`/`poll`, không nhận null, không có close/shutdown chung, memory consistency.
- [`ArrayBlockingQueue` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html): bounded FIFO queue cho producer–consumer.
- [`LinkedBlockingQueue` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html): FIFO linked queue, tùy chọn capacity.
- [`CountDownLatch` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): điều phối các giai đoạn.
- [`ExecutorService` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): `shutdown`, `shutdownNow`, `awaitTermination`, `Future`.
- [`Thread` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): `join`, `interrupt`, thread lifecycle.
- [`PriorityBlockingQueue` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/PriorityBlockingQueue.html): ordering theo priority, khác queue FIFO.

**Ghi nhớ:** Poison pill có ý nghĩa khi **đứng sau toàn bộ work đã được chấp nhận** và được **mọi consumer cần dừng** nhận theo protocol rõ ràng. Trình tự bền vững là **đóng nhận việc → đợi producer → gửi đủ STOP → đợi consumer → kiểm tra kết quả và giải phóng tài nguyên**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-coordination-011">Tránh busy waiting bằng blocking primitives</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh busy waiting bằng blocking primitives trong Java

## 1. Mục tiêu bài học

Sau bài này, bạn có thể:

- Giải thích **busy waiting**, **spinning**, **blocking wait** và vì sao vòng lặp kiểm tra liên tục thường lãng phí CPU, gây contention và có thể lỗi visibility.
- Chọn đúng công cụ Java theo điều kiện đang chờ: **CountDownLatch**, **BlockingQueue**, **Semaphore**, **Future.get()**, **Condition.await()** và các phương thức chờ kết thúc thread.
- Phân biệt **tín hiệu một lần**, **trao dữ liệu producer–consumer**, **giới hạn tài nguyên**, **chờ kết quả** và **chờ predicate có thể thay đổi**.
- Viết các chương trình Java 17+ sử dụng blocking primitives, kiểm soát đúng **interruption**, **timeout**, **cleanup** và **happens-before**.
- Nhận diện tình huống **blocking khi giữ lock**, deadlock, lost notification, lựa chọn nhầm queue hoặc timeout, và việc thay busy loop bằng sleep mà chưa có giao thức đồng bộ.
- Biết giới hạn của blocking: không hứa thời điểm thức dậy chính xác, không tự đồng nghĩa tác vụ nghiệp vụ thành công, và không tự xử lý kết quả đã commit bên ngoài JVM.

Bài thuộc chủ đề **Phối hợp luồng** trong **Java concurrency nói chung**. Các cơ chế điều phối riêng của Spring thuộc chương khác.

## 2. Busy waiting là gì?

**Busy waiting** là cách một thread liên tục kiểm tra điều kiện mà hầu như không có việc hữu ích để làm, thường bằng một vòng lặp:

~~~java
// Mã minh họa lỗi: ready là field thường được chia sẻ giữa các thread.
while (!ready) {
    // Kiểm tra liên tục, tiêu tốn CPU.
}
process();
~~~

Đoạn trên không phải chương trình độc lập: **ready** và **process()** là thành phần giả định.

Những vấn đề nổi bật:

1. **Tốn CPU**: thread vẫn thường xuyên được scheduler cho chạy chỉ để đọc lại cùng điều kiện.
2. **Gây tranh chấp cache/memory bus**: nhiều thread đọc một cờ chung liên tục có thể gây áp lực không cần thiết.
3. **Thiếu bảo đảm visibility** nếu ready là field thông thường: không có quan hệ happens-before, thread chờ có thể không thấy cập nhật từ thread khác; trình biên dịch/JIT còn có quyền tối ưu dựa trên JMM.
4. **Khó kiểm soát thời gian dừng**: nếu producer thất bại và không phát tín hiệu, vòng lặp không có timeout hoặc chính sách hủy rõ ràng.
5. **Tăng tải khi nhiều thread chờ**: polling theo chu kỳ ngắn có thể làm hệ thống bận nhưng không tiến triển.

Một vòng **while (!ready) {}** chỉ nên tồn tại trong những thuật toán spinning có thiết kế chuyên biệt, đã giải quyết memory ordering, chi phí CPU, fairness và phương án fallback. Nó không phải mặc định cho chờ sự kiện trong ứng dụng Java.

### Thay bằng volatile có đủ không?

~~~java
// Mã minh họa: volatile xử lý visibility của cờ, không xử lý lãng phí CPU.
private volatile boolean ready;

// Thread chờ:
while (!ready) {
    Thread.onSpinWait();
}
~~~

**Thread.onSpinWait()** chỉ là một *hint* rằng thread đang trong vòng spin; JVM/CPU có thể dùng hint để cải thiện cách thực thi. Nó **không block**, **không tự phát hiện tín hiệu** và **không đảm bảo thời điểm thức dậy hoặc tiến độ**. Volatile giúp nhìn thấy cập nhật, nhưng vòng lặp vẫn có thể tiêu tốn CPU.

## 3. Blocking primitive khác busy loop ở đâu?

Một **blocking primitive** cho phép thread **chờ theo một điều kiện/sự kiện đã được API quản lý**, thay vì tự đọc trạng thái chung liên tục. Hợp đồng API quyết định khi nào chờ được giải phóng, quan hệ bộ nhớ nào được thiết lập, và cách timeout/interrupt hoạt động.

Ví dụ chờ đúng một tín hiệu:

~~~java
// Mã minh họa trong phương thức khai báo throws InterruptedException:
CountDownLatch done = new CountDownLatch(1);

// Worker sau khi hoàn thành bước liên quan sẽ gọi done.countDown().
// Thread chờ:
done.await();
~~~

Ở đây **CountDownLatch** là kiểu thuộc java.util.concurrent (cần import). Đoạn minh họa không có main() và không tự chạy; ví dụ hoàn chỉnh nằm ở phần 6.

Cách hiểu hợp đồng: khi count giảm về 0, các thread đang await được phép tiếp tục. Runtime không phải thực hiện một vòng Java **while (latch.getCount() > 0)** tự viết liên tục. Cách triển khai nội bộ của primitive có thể có bước ngắn tối ưu, nhưng về mặt sử dụng đây là **blocking wait**, không phải busy-wait do ứng dụng tự làm.

### Sơ đồ

~~~text
Busy waiting:
  reader -> check -> check -> check -> check -> ... -> check -> tiếp tục
                  CPU có thể hoạt động gần như liên tục

Blocking:
  waiter -> await() -> [đợi tín hiệu / điều kiện / kết quả]
  producer ----------------------> signal / put / complete
  waiter -> được phép tiếp tục -> thực hiện bước tiếp theo
~~~

**Blocking không có nghĩa “chắc chắn chờ bằng 0 CPU” hoặc “tỉnh dậy ngay lập tức”.** Chi tiết phụ thuộc JVM, hệ điều hành, scheduler và API. Điểm chính là ứng dụng không tự duy trì vòng thăm dò điều kiện liên tục.

## 4. Chọn đúng primitive theo sự kiện đang đợi

| Nhu cầu | Primitive Java thường phù hợp | Điểm quan trọng |
| --- | --- | --- |
| Đợi N công việc báo xong | **CountDownLatch** | Đếm về 0; không reset |
| Các thread gặp nhau tại rào cản nhiều giai đoạn | **CyclicBarrier** hoặc **Phaser** | Có giao thức từng pha, xử lý broken barrier |
| Producer gửi phần tử cho consumer | **BlockingQueue** | **put/take** vừa truyền dữ liệu vừa phối hợp |
| Giới hạn số hoạt động đồng thời | **Semaphore** | Quản lý permit; acquire/release đúng quyền nghiệp vụ |
| Đợi một kết quả tính toán bất đồng bộ | **Future.get()** | Có giá trị, lỗi hoặc trạng thái hủy |
| Đợi một thread cụ thể kết thúc | **Thread.join()** | Kết thúc thread không đồng nghĩa thành công nghiệp vụ |
| Đợi predicate dưới explicit lock | **Condition.await()/signalAll()** | Luôn kiểm tra predicate trong while |
| Đợi predicate dưới intrinsic monitor | **Object.wait()/notifyAll()** | Phải sở hữu đúng monitor, luôn while |
| Đợi một permit park/unpark thấp tầng | **LockSupport.park()/unpark()** | Mức thấp, cần tự thiết kế predicate và unpark chính xác |

Đừng thay **BlockingQueue** bằng **CountDownLatch** chỉ vì cả hai đều có phương thức chờ: latch không mang phần tử. Đừng thay **Semaphore** bằng một boolean nếu bài toán thực sự yêu cầu tối đa K người vào cùng lúc.

## 5. Ví dụ đối chiếu: polling, sleep và block

### 5.1. Busy loop

~~~java
// Anti-pattern: cờ ordinary không có giao thức visibility.
while (!finished) {
    // spin
}
~~~

Không bảo đảm thấy finished=true; tiêu thụ CPU.

### 5.2. Polling với sleep

~~~java
// Minh họa: finished cần cơ chế visibility như volatile hoặc atomic.
while (!finished) {
    Thread.sleep(50);
}
~~~

Mẫu này **không còn đốt CPU liên tục** trong thời gian ngủ, nhưng vẫn là **periodic polling**:

- Có **độ trễ bổ sung** giữa lúc finished được đặt true và lần kiểm tra tiếp theo.
- Có chi phí thức dậy nhiều lần chỉ để kiểm tra.
- **Thread.sleep() không tự tạo happens-before** cho finished; cần volatile/atomic/lock phù hợp.
- Nó không có kênh báo lỗi hoặc biết producer đã chết mà chưa cập nhật cờ.
- Có thể bị InterruptedException và cần xử lý phù hợp.

Nếu API nguồn sự kiện cho phép **signal hoặc blocking wait**, hãy dùng nó. Polling có thể chấp nhận được khi tích hợp một API không hỗ trợ signal, với khoảng kiểm tra và deadline được chọn có chủ đích.

### 5.3. Đợi tín hiệu

~~~java
// Minh họa: done là một CountDownLatch đã được chia sẻ an toàn.
done.await();
~~~

Thread ngủ chờ sự kiện thay vì tự lặp kiểm tra. Giao thức completion do primitive quản lý; cần chắc chắn producer **luôn countDown() đúng lúc**, kể cả khi có lỗi nếu contract yêu cầu mọi waiter không bị kẹt.

## 6. Ví dụ 1: CountDownLatch thay vòng spin

Một chương trình chờ một worker báo rằng kết quả sẵn sàng. Ta chủ động dùng latch làm mốc công bố kết quả và chờ bằng await().

Tệp **LatchInsteadOfSpinDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class LatchInsteadOfSpinDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger();

        Thread worker = new Thread(() -> {
            try {
                result.set(6 * 7);
            } finally {
                ready.countDown();
            }
        }, "worker");

        worker.start();
        ready.await();
        System.out.println("result = " + result.get());
        worker.join();
    }
}
~~~

~~~bash
javac -encoding UTF-8 LatchInsteadOfSpinDemo.java
java LatchInsteadOfSpinDemo
~~~

Stdout:

~~~text
result = 42
~~~

**Giải thích:**

1. Worker tính giá trị rồi gán vào AtomicInteger trước khi countDown.
2. **CountDownLatch.await()** của main chờ cho count về 0; không có vòng polling tự viết.
3. Với CountDownLatch, các hành động trước countDown của producer **happens-before** những hành động sau khi await thành công của consumer. AtomicInteger còn cung cấp hợp đồng bộ nhớ riêng của biến atomic.
4. **join()** ở cuối giúp thu hồi/đợi thread worker kết thúc; bản thân latch đã đủ để quan sát kết quả được công bố **trước countDown**, không nhất thiết bảo đảm toàn bộ code sau countDown đã kết thúc.
5. Nếu worker có thể thất bại, ứng dụng thật cần lưu **kết quả hoặc exception** và quy định latch báo “đã kết thúc giai đoạn” hay “đã thành công”. Latch về 0 **không tự nói nghiệp vụ thành công**.

Trong ví dụ, lỗi unchecked trong phép toán không xảy ra, nên đầu ra xác định.

### Latch có thể dùng nhiều người đợi

Với **new CountDownLatch(N)**, N lần countDown làm bộ đếm giảm đến 0. N có thể là số lượng worker hoặc số giai đoạn được thỏa. **countDown khi đã bằng 0 không làm số đếm âm**. Một latch sau khi về 0 sẽ tiếp tục cho các await sau đi qua, **không reset**; muốn vòng đời nhiều pha cần primitive khác.

## 7. Timeout: không chờ vô hạn nếu điều kiện có deadline

Hầu hết API đồng bộ hóa có một số overload chờ có timeout:

- **CountDownLatch.await(long, TimeUnit)**: trả boolean, true khi count về 0 trước khi hết chờ, false khi timeout.
- **Future.get(long, TimeUnit)**: trả kết quả hoặc ném TimeoutException; computation **không tự bị hủy** chỉ vì caller hết thời gian chờ.
- **BlockingQueue.poll(long, TimeUnit)**: trả phần tử hoặc null khi hết hạn mà chưa có phần tử.
- **Semaphore.tryAcquire(long, TimeUnit)**: trả true khi lấy permit được, false nếu timeout.
- **Condition.await(long, TimeUnit)**: trả giá trị theo thời gian chờ còn lại; predicate phải tự kiểm tra lại.
- **Thread.join(long)**: trở về khi thread đích kết thúc hoặc hết thời gian chờ; sau join(timeout) không được mặc nhiên coi thread đã chết.

### Ví dụ 2: timed await có kết quả dự đoán được

Tệp **LatchTimeoutDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class LatchTimeoutDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch neverSignaled = new CountDownLatch(1);

        boolean completed =
                neverSignaled.await(5, TimeUnit.MILLISECONDS);

        System.out.println("completed = " + completed);
        System.out.println("remaining = " + neverSignaled.getCount());
    }
}
~~~

~~~bash
javac -encoding UTF-8 LatchTimeoutDemo.java
java LatchTimeoutDemo
~~~

Stdout:

~~~text
completed = false
remaining = 1
~~~

Không thread nào gọi countDown(), nên await có timeout trả false và count còn 1. Timeout cho caller một **đường thoát có kiểm soát**, nhưng latch vẫn chưa hoàn tất. Với tác vụ thật, cần quyết định cảnh báo, retry, tiếp tục chờ, gửi yêu cầu hủy hoặc giải phóng tài nguyên theo policy.

**Không lấy thời gian chờ quá ngắn làm bảo đảm về latency**: JVM, scheduler và timer có sai số/granularity; timeout không phải cam kết thực thi đúng từng mili giây.

## 8. BlockingQueue: chờ phần tử thay vì chờ cờ ready

Bài toán producer–consumer tự nhiên cần **truyền dữ liệu**, không chỉ phát tín hiệu. **BlockingQueue<E>** cung cấp:

- **put(e)**: có thể chờ khi queue bị đầy (với queue có giới hạn).
- **take()**: chờ khi queue trống, trả phần tử khi có dữ liệu.
- **offer(e)**: thử thêm ngay, trả false nếu không thể thêm.
- **offer(e, timeout, unit)**: đợi có giới hạn để thêm.
- **poll()**: lấy ngay hoặc trả null nếu rỗng.
- **poll(timeout, unit)**: đợi có giới hạn để lấy.

**ArrayBlockingQueue** có dung lượng cố định; **LinkedBlockingQueue** có thể được cấu hình dung lượng nhưng constructor không chỉ định capacity dùng giới hạn lớn. Nếu muốn **backpressure** thực sự, xác định capacity thích hợp.

**BlockingQueue không cho phép phần tử null**, vì null thường mang ý nghĩa “không nhận được phần tử”. Nếu cần tín hiệu kết thúc, dùng một message đặc biệt hoặc protocol rõ ràng khác.

### Ví dụ 3: chờ bằng take(), không spin trên isEmpty()

Tệp **BlockingQueueDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BlockingQueueDemo {
    private static final int END = -1;

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Integer> queue =
                new ArrayBlockingQueue<>(2);

        Thread producer = new Thread(() -> {
            try {
                queue.put(1);
                queue.put(2);
                queue.put(3);
                queue.put(END);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        producer.start();

        int sum = 0;
        int count = 0;

        while (true) {
            int value = queue.take();
            if (value == END) {
                break;
            }
            sum += value;
            count++;
        }

        producer.join();
        System.out.println("processed = " + count);
        System.out.println("sum = " + sum);
    }
}
~~~

~~~bash
javac -encoding UTF-8 BlockingQueueDemo.java
java BlockingQueueDemo
~~~

Stdout:

~~~text
processed = 3
sum = 6
~~~

**Diễn giải:**

1. Producer đưa ba số vào queue FIFO, rồi đưa **END** để báo hết dữ liệu.
2. Consumer là main. **take()** đợi khi queue trống và tiếp tục khi producer put thêm phần tử; không tự spin trên **isEmpty()**.
3. Capacity 2 giới hạn số phần tử chưa được tiêu thụ; **put()** có thể chờ, tạo backpressure thay vì phát sinh queue vô hạn.
4. Sau END, main join producer rồi in tổng, nên stdout xác định.
5. Ví dụ dùng một producer, một consumer và một sentinel **không trùng giá trị dữ liệu hợp lệ**. Với nhiều producer/consumer, protocol kết thúc cần được thiết kế riêng (ví dụ số sentinel phù hợp hoặc đóng queue theo một giao thức), không thể tùy tiện đưa một END rồi giả định tất cả consumer dừng.

**Memory visibility:** Các hành động của thread trước khi **đưa object vào BlockingQueue** happens-before các hành động của thread khác **sau khi lấy object đó ra**. Nhưng điều đó **không tự biến object mutable thành immutable**: nếu producer vẫn sửa một đối tượng sau khi put, có thể xuất hiện race khi consumer đọc cùng object.

### Anti-pattern: isEmpty() + poll() / sleep()

~~~java
// Mã minh họa lỗi thiết kế, queue là shared queue đã tạo.
while (queue.isEmpty()) {
    Thread.sleep(10);
}
var item = queue.poll();
~~~

Queue có thể đổi ngay sau isEmpty(); item có thể là null khi một consumer khác lấy phần tử trước. Nếu cần chờ phần tử, dùng **take()**. Nếu không được block vô hạn, dùng **poll(timeout, unit)** rồi kiểm tra null.

## 9. Semaphore: chờ quyền truy cập tài nguyên

**Semaphore** quản lý một số lượng **permit**. Chỉ thread lấy đủ permit mới được đi vào phần công việc cần giới hạn concurrency.

- **acquire()**: đợi đến khi có permit, có thể ném InterruptedException.
- **tryAcquire()**: thử ngay, trả boolean.
- **tryAcquire(timeout, unit)**: đợi có giới hạn.
- **release()**: trả permit theo giao thức ứng dụng.

**Semaphore không có ownership theo thread như ReentrantLock**: API cho phép thread khác release permit, và release dư có thể khiến số permit vượt mức dự kiến. Vì vậy chính ứng dụng phải đảm bảo **mỗi lần acquire thành công có một release tương ứng**, trừ khi đang chủ ý thực hiện một giao thức chuyển giao permit.

### Ví dụ 4: giới hạn đồng thời tối đa hai tác vụ

Tệp **SemaphoreLimitDemo.java**:

~~~java
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class SemaphoreLimitDemo {
    public static void main(String[] args) throws InterruptedException {
        Semaphore slots = new Semaphore(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();

        Runnable work = () -> {
            boolean acquired = false;
            try {
                slots.acquire();
                acquired = true;

                int now = active.incrementAndGet();
                maximum.accumulateAndGet(now, Math::max);

                try {
                    int value = 6 * 7; // Ví dụ xử lý công việc hữu ích.
                    if (value == 42) {
                        completed.incrementAndGet();
                    }
                } finally {
                    active.decrementAndGet();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                if (acquired) {
                    slots.release();
                }
            }
        };

        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(work, "worker-" + i);
            threads[i].start();
        }

        for (Thread worker : threads) {
            worker.join();
        }

        System.out.println("completed = " + completed.get());
        System.out.println("capacity respected = " + (maximum.get() <= 2));
        System.out.println("permits = " + slots.availablePermits());
    }
}
~~~

~~~bash
javac -encoding UTF-8 SemaphoreLimitDemo.java
java SemaphoreLimitDemo
~~~

Stdout:

~~~text
completed = 4
capacity respected = true
permits = 2
~~~

**Vì sao đúng?**

- Mỗi thread phải acquire một permit trước khi tăng active.
- Chỉ tối đa hai thread có thể giữ permit đồng thời; **maximum <= 2** là một bất biến, không cần giả định chính xác maximum bằng 2 trong mọi lịch.
- **active** giảm trong finally sau khi làm việc; **slots.release()** chỉ chạy nếu thread đã acquire thành công.
- Sau khi join cả bốn thread, tất cả permit đã được trả và số task xử lý là 4.

**Cảnh giác:** Nếu catch InterruptedException khi chưa acquire mà vẫn gọi release(), semaphore có thể có **3 permit** thay vì 2 và đánh mất giới hạn. Cơ chế cleanup là một phần bắt buộc của giao thức.

**Fairness:** new Semaphore(permits, true) có chính sách fairness cho các acquisition theo hợp đồng API, nhưng không đảm bảo mỗi thread được CPU công bằng tuyệt đối; các lời gọi **tryAcquire() ngay** có thể vượt hàng theo quy tắc riêng.

## 10. Future.get(): chờ kết quả thay vì hỏi isDone liên tục

Nếu ứng dụng gửi một computation vào **ExecutorService**, **Future<T>** đại diện cho completion và kết quả.

Anti-pattern:

~~~java
// Mã minh họa: future là Future<Integer>.
while (!future.isDone()) {
    // Busy waiting hoặc gọi sleep theo chu kỳ.
}
int answer = future.get();
~~~

Trong khi đó:

~~~java
// Minh họa trong phương thức xử lý checked exceptions.
int answer = future.get(); // Block cho tới completion hoặc lỗi/hủy.
~~~

**Future.get()** có thể:

- Trả giá trị T khi computation hoàn tất bình thường.
- Ném **ExecutionException** khi task ném exception.
- Ném **CancellationException** khi Future đã bị hủy.
- Ném **InterruptedException** nếu thread đang chờ bị ngắt.
- Với overload có timeout, ném **TimeoutException** nếu caller chờ quá hạn; **task vẫn có thể chạy tiếp**.

**isDone() = true không đồng nghĩa thành công**; nó cũng đúng khi thất bại hoặc bị hủy. Nếu cần trạng thái nghiệp vụ, phải diễn giải kết quả/exception theo giao thức ứng dụng.

### Ví dụ 5: đợi Future trả kết quả

Tệp **FutureBlockingDemo.java**:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class FutureBlockingDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> future = pool.submit(() -> 21 * 2);
            int result = future.get();

            System.out.println("result = " + result);
            System.out.println("done = " + future.isDone());
            System.out.println("cancelled = " + future.isCancelled());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 FutureBlockingDemo.java
java FutureBlockingDemo
~~~

Stdout:

~~~text
result = 42
done = true
cancelled = false
~~~

Caller **không tự polling** mà đợi kết quả qua get(). ExecutorService phải được shutdown để không giữ worker thread sống ngoài ý muốn. **Future.get()** trả kết quả hoặc báo lỗi; đây là hợp đồng ở tầng computation, không định nghĩa transaction nghiệp vụ bên ngoài.

**Đừng giữ monitor/ReentrantLock mà worker cần rồi gọi get()**: caller giữ khóa và đợi worker, worker đợi khóa của caller → có thể deadlock.

## 11. Condition: block khi đợi một predicate có thể đổi nhiều lần

Với **ReentrantLock**, API **Condition** phù hợp khi thread cần chờ **một điều kiện về state chung** và một thread khác cập nhật điều kiện đó. Chọn Condition khi cần logic đa bước mà queue/latch không diễn tả trực tiếp.

Quy tắc:

1. Tạo condition qua **lock.newCondition()**.
2. Caller phải đang giữ **chính lock liên kết** khi gọi await(), signal() hoặc signalAll().
3. Đặt predicate dưới cùng lock; thường dùng **while (!predicate) condition.await()**.
4. **await() nhả lock liên kết khi chờ**, rồi reacquire trước khi tiếp tục.
5. Có thể xảy ra **spurious wakeup**; không dùng if một lần để giả định điều kiện đã đúng.
6. Signal không có nghĩa thread chờ đã chạy ngay: nó còn phải lấy lại lock.

### Ví dụ 6: await khi chưa có điều kiện, signalAll khi sẵn sàng

Tệp **ConditionBlockingDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ConditionBlockingDemo {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Condition readyCondition = lock.newCondition();
        boolean[] ready = {false};
        int[] result = {0};
        CountDownLatch started = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            lock.lock();
            try {
                started.countDown();
                while (!ready[0]) {
                    try {
                        readyCondition.await();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                result[0] = 42;
            } finally {
                lock.unlock();
            }
        }, "condition-worker");

        worker.start();
        started.await();

        lock.lock();
        try {
            ready[0] = true;
            readyCondition.signalAll();
        } finally {
            lock.unlock();
        }

        worker.join();

        lock.lock();
        try {
            System.out.println("ready = " + ready[0]);
            System.out.println("result = " + result[0]);
        } finally {
            lock.unlock();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ConditionBlockingDemo.java
java ConditionBlockingDemo
~~~

Stdout:

~~~text
ready = true
result = 42
~~~

**Giải thích:**

- Worker đợi **predicate ready[0]**, không lặp thăm dò khi chưa sẵn sàng.
- **await()** nhả lock liên kết, cho main cập nhật ready và signalAll.
- Main không thể “bỏ lỡ” điều kiện chỉ vì signal đến trước await: nó phải giữ cùng lock để sửa ready, và worker luôn kiểm tra predicate trong while.
- Sau signal, worker lấy lại lock, kiểm tra ready, ghi result rồi nhả lock.
- main.join() đợi worker hoàn thành; main cũng đọc dưới cùng lock, nên kết quả cuối xác định.

**Không dùng condition như một message queue nếu không lưu predicate.** Lời gọi signal khi chưa có waiter không lưu một “tín hiệu cho tương lai”; predicate mới là nguồn sự thật. Nếu cần truyền từng message, BlockingQueue thường phù hợp hơn.

## 12. Object.wait()/notifyAll(): blocking với intrinsic monitor

Bên cạnh Condition, Java có **Object.wait()**, **notify()**, **notifyAll()**:

~~~java
// Minh họa với lock là Object monitor và ready là field chia sẻ.
synchronized (lock) {
    while (!ready) {
        lock.wait();
    }
    consume();
}
~~~

Thread sửa điều kiện:

~~~java
synchronized (lock) {
    ready = true;
    lock.notifyAll();
}
~~~

Hai mảnh trên không phải chương trình độc lập: cần khai báo lock, ready, consume() và xử lý InterruptedException.

**Khác nhau với sleep():**

- **Object.wait()** trên đúng object mà thread đang giữ monitor sẽ **nhả monitor của object đó trong thời gian chờ**, sau đó reacquire trước khi tiếp tục.
- **Thread.sleep()** không nhả monitor đang giữ.
- **notify/notifyAll không nhả monitor ngay**; waiter phải đợi notifier rời synchronized và tự giành lại monitor.
- Wait có thể thức dậy giả; **while** phải kiểm tra điều kiện lại.
- Gọi wait/notify mà không giữ đúng monitor gây **IllegalMonitorStateException**.

Nếu đang giữ nhiều monitor, wait chỉ nhả monitor **được gọi wait()**, không nhả mọi monitor khác. Vì vậy vẫn có nguy cơ deadlock nếu chờ trong khi giữ một khóa khác cần thiết cho producer.

## 13. Interrupt: chờ có thể bị hủy theo hợp đồng

Các lời gọi blocking phổ biến như **CountDownLatch.await()**, **BlockingQueue.take()/put()**, **Semaphore.acquire()**, **Condition.await()**, **Thread.join()** và **Future.get()** hỗ trợ **InterruptedException**.

Khi một thread đang chờ và nhận interrupt, lời gọi có thể ném InterruptedException; thông thường cờ interrupt bị xóa khi exception được ném. Code cần có chính sách hợp tác:

- Nếu **không thể xử lý tại tầng hiện tại**, thường **khôi phục cờ** bằng Thread.currentThread().interrupt() rồi kết thúc/đi lên tầng gọi theo hợp đồng.
- Nếu phương thức cho phép, có thể **propagate InterruptedException** cho caller.
- Không nuốt interrupt rồi **tiếp tục chờ vô hạn** nếu yêu cầu hủy cần được tôn trọng.
- Với permit/lock, trong finally **chỉ release/unlock những gì đã thực sự acquire**.

### Ví dụ 7: interruption giải phóng thread đang await()

Tệp **InterruptibleAwaitDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class InterruptibleAwaitDemo {
    public static void main(String[] args) throws InterruptedException {
        CountDownLatch neverReleased = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();

        Thread worker = new Thread(() -> {
            started.countDown();
            try {
                neverReleased.await();
            } catch (InterruptedException ex) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                restored.set(Thread.currentThread().isInterrupted());
            }
        }, "await-worker");

        worker.start();
        started.await();
        worker.interrupt();
        worker.join();

        System.out.println("interrupted = " + interrupted.get());
        System.out.println("flag restored = " + restored.get());
    }
}
~~~

~~~bash
javac -encoding UTF-8 InterruptibleAwaitDemo.java
java InterruptibleAwaitDemo
~~~

Stdout:

~~~text
interrupted = true
flag restored = true
~~~

Dù interrupt đến trước hay sau thời điểm worker thực sự vào await(), nếu cờ interrupt hiện diện khi await bắt đầu hoặc khi đang chờ, await() xử lý nó bằng InterruptedException. Worker khôi phục flag và kết thúc; main join rồi in trạng thái được AtomicBoolean ghi nhận.

**Interrupt không phải lệnh cưỡng chế kết thúc thread.** Nếu worker bỏ qua interrupt hoặc đang ở một thao tác không hỗ trợ hủy, nó có thể tiếp tục. Cần thiết kế code thực thi hợp tác và không giả định external side effect tự rollback.

## 14. Deadline và timeouts: chờ đúng cách khi có nhiều bước

Khi cần thực hiện nhiều bước chờ trong **một tổng deadline**, đừng cấp nguyên timeout cho từng bước:

~~~text
Deadline toàn bộ công việc: 1000 ms
Bước A chờ 1000 ms
Bước B lại chờ 1000 ms
=> Tổng thời gian có thể vượt budget ban đầu.
~~~

Nên tính **thời gian còn lại** từ một mốc đơn điệu (**System.nanoTime()**) cho từng bước chờ. Tránh tính deadline tương đối bằng **System.currentTimeMillis()** vì đồng hồ wall-clock có thể bị điều chỉnh.

Pseudocode minh họa (không phải chương trình độc lập):

~~~text
budget = 1 second
deadline = monotonicNow + budget

remaining = deadline - monotonicNow
if remaining <= 0 -> timeout
await(stepA, remaining)

remaining = deadline - monotonicNow
if remaining <= 0 -> timeout
await(stepB, remaining)
~~~

Lưu ý phép cộng deadline khi budget rất lớn có thể overflow nếu không xử lý phù hợp; API timeout của JDK có ngữ nghĩa riêng. Với bài toán thời gian thực, nên xác định SLA, sử dụng deadline/timeout có chủ đích và đo actual latency, không coi await(timeout) là một scheduler có độ chính xác tuyệt đối.

Nếu **Future.get(timeout)** ném TimeoutException, đó là **caller chờ quá lâu**, không chứng minh worker đã dừng. Nếu policy cần hủy, caller có thể yêu cầu **future.cancel(true)** và worker phải hỗ trợ interrupt; những side effect đã hoàn tất trước đó không được tự hoàn tác.

## 15. Tránh deadlock khi sử dụng blocking primitives

Blocking primitives giúp giảm busy waiting nhưng **không tự đảm bảo deadlock-free**.

### Giữ lock rồi đợi worker cần chính lock đó

~~~text
Main giữ lock L
  -> main gọi Future.get() hoặc Thread.join() chờ worker

Worker muốn synchronized(L) hoặc lock.lock()
  -> worker chờ main nhả L

=> Vòng chờ: main đợi worker, worker đợi L của main.
~~~

Giải pháp là xác định **thứ tự chờ và phạm vi lock**. Nếu công việc cần chờ không đòi lock được giữ, có thể nhả lock rồi await; khi lấy lại lock phải **kiểm tra lại điều kiện**, vì state đã có thể thay đổi. Không bỏ lock một cách tùy tiện nếu điều đó phá invariant.

### Worker pool tự chờ task cùng pool

Một pool chỉ có một worker đang thực thi task A. Nếu A gửi task B vào **cùng single-thread executor** rồi gọi **Future.get()** đợi B, B không được chạy vì worker duy nhất đang bị A chiếm. Đó là **thread-starvation deadlock** dù không có hai ReentrantLock.

Cách thiết kế khác: không blocking đợi công việc mà cùng một tài nguyên duy nhất cần thực hiện; dùng composition/chains hoặc bố trí worker độc lập theo yêu cầu. **Thêm timeout chỉ làm có đường thoát**, không chứng minh kiến trúc đúng.

### Full queue và chu trình chờ

Producer giữ một resource rồi **put()** vào queue đã đầy; consumer muốn lấy resource ấy trước khi **take()**. Producer chờ queue có chỗ, consumer chờ resource → có thể deadlock. Vì vậy không chỉ xét lock order mà còn xét **resource dependency graph** gồm permits, queue capacity và kết quả tác vụ.

## 16. Không phải lúc nào spinning cũng vô nghĩa

Có các trường hợp kỹ thuật rất ngắn, đã đo đạc, trong thuật toán lock-free hoặc low-latency, **spinning có giới hạn** có thể giảm chi phí park/unpark so với blocking. Tuy nhiên cần:

- Dùng cơ chế memory ordering phù hợp (**volatile**, atomic, VarHandle theo đúng mode).
- Giới hạn số vòng/thời gian và có fallback sang blocking nếu chờ kéo dài.
- Đo mức CPU tiêu thụ, tail latency, fairness và tác động tới các thread khác.
- Cẩn thận với một hoặc rất ít CPU core: spinner có thể chiếm CPU mà producer cần để hoàn thành.
- Không dùng **Thread.onSpinWait()** như một lời hứa JMM về visibility, thứ tự hoặc thức dậy.

Đối với phần lớn giao thức chờ dữ liệu, tín hiệu, completion hoặc permit trong ứng dụng thông thường, **blocking primitive đúng ngữ nghĩa dễ bảo trì hơn vòng spin tự viết**.

## 17. Hướng dẫn chọn primitive theo ngữ nghĩa

| Nếu bạn đang hỏi... | Hãy xem xét | Đừng làm |
| --- | --- | --- |
| “Bao giờ các worker đã xong một bước?” | CountDownLatch hoặc Future | Poll một cờ ordinary vô hạn |
| “Có phần tử để xử lý chưa?” | BlockingQueue.take()/poll(timeout) | isEmpty rồi sleep |
| “Có slot tài nguyên để vào không?” | Semaphore.acquire()/tryAcquire(timeout) | Counter thường và spin |
| “Computation trả giá trị hay lỗi gì?” | Future.get() / composition | while(!isDone()) rồi get |
| “Điều kiện dưới khóa đã thỏa chưa?” | Condition.await() trong while | await không giữ lock hoặc dùng if |
| “Thread cụ thể đã kết thúc chưa?” | Thread.join() | while(thread.isAlive()) không có sleep/hủy |
| “Tôi chỉ cần trao một signal cấp thấp?” | Primitive cấp cao; LockSupport khi thực sự cần | park mà không có state predicate |
| “Tôi cần tối ưu chờ cực ngắn?” | Đo đạc spin có giới hạn + fallback | Spin vô hạn và hy vọng scheduler |

**Quy tắc chọn:** xác định **thứ bạn muốn nhận** (tín hiệu, phần tử, permit, kết quả, predicate), **khi nào nó hợp lệ**, **ai cung cấp**, và **đường lỗi/timeout/interrupt**. Sau đó chọn primitive có hợp đồng gần nhất.

## 18. Lỗi thường gặp và cách sửa

| Lỗi | Hậu quả | Cách sửa |
| --- | --- | --- |
| Spin trên field thường | Data race/visibility và tiêu tốn CPU | Dùng blocking primitive hoặc giao thức volatile/atomic được chứng minh |
| Spin với volatile rồi cho rằng đã tối ưu | Vẫn tốn CPU | Dùng await/take/get khi thích hợp |
| Sleep rồi cho rằng có happens-before | Có thể không thấy dữ liệu đúng | Dùng primitive có memory consistency rõ |
| Dùng CountDownLatch để truyền object như queue | Latch chỉ mang sự kiện count | BlockingQueue hoặc Future |
| Không countDown trên đường lỗi | Waiter có thể treo | Hoàn thiện contract completion và finally |
| Dùng await(timeout) rồi xem false là thành công | Điều kiện chưa thỏa | Tách nhánh timeout |
| Cho rằng Future.isDone nghĩa là thành công | Done gồm cả exception/cancel | Kiểm tra Future.get và chính sách nghiệp vụ |
| Gọi Future.get timeout rồi giả định task dừng | Task có thể vẫn chạy | Cancel hợp tác theo policy |
| BlockingQueue không có capacity rõ | Có thể tăng bộ nhớ không kiểm soát | Chọn bounded queue và backpressure |
| put vào queue khi đang giữ lock mà consumer cần | Deadlock hoặc contention | Phân tích dependency, thay cấu trúc phối hợp |
| Tăng Semaphore permits do release dư | Phá giới hạn đồng thời | Release chính xác sau acquire thành công |
| Gọi Condition.signal ngoài lock | IllegalMonitorStateException | Giữ lock liên kết |
| Dùng if quanh Condition.await | Spurious wakeup, predicate bị thay đổi | while kiểm tra lại |
| Nuốt InterruptedException | Đường hủy không có hiệu lực | Propagate hoặc restore flag rồi dừng theo policy |
| Đợi Future trong single-worker task của cùng pool | Thread-starvation deadlock | Không block chờ task phụ thuộc cùng worker |
| Tin thread WAITING nghĩa là “không giữ khóa” | Có thể vẫn giữ lock khác | Rà soát tất cả khóa đang sở hữu |
| Tin blocking luôn tức thời hoặc công bằng | Scheduler/timer/contestion khác nhau | Đo latency, deadline và fairness thực tế |

## 19. Bài tập tự kiểm tra

**1. Vì sao vòng while (!flag) {} có thể sai cả khi worker chắc chắn gán flag=true?**  
Vì flag thông thường có thể bị data race/thiếu happens-before; reader không được bảo đảm thấy cập nhật. Dù flag volatile, vòng spin vẫn có thể tốn CPU.

**2. Khi chỉ cần biết N worker đã hoàn thành một giai đoạn, nên dùng gì?**  
CountDownLatch(N), mỗi worker countDown theo contract, caller await. Nếu cần truy xuất lỗi/kết quả từng worker, thêm cơ chế thu kết quả thích hợp hoặc dùng Future.

**3. Khi cần nhận từng phần tử từ producer, CountDownLatch có đủ không?**  
Không. Latch không mang dữ liệu; BlockingQueue cung cấp put/take và memory consistency cho phần tử được trao.

**4. Semaphore khác ReentrantLock ở điểm sở hữu khóa?**  
ReentrantLock yêu cầu owner unlock chính lock mình đã giữ. Semaphore quản lý permit và không buộc thread release là thread acquire; ứng dụng phải tự bảo toàn số permit.

**5. Có được đặt signalAll() ngoài lock để tránh contention không?**  
Không với Condition do ReentrantLock tạo: signalAll yêu cầu thread sở hữu lock liên kết. Hãy cập nhật predicate và signal dưới cùng lock.

**6. Future.get(timeout) ném TimeoutException có nói job đã bị hủy không?**  
Không. Đó chỉ là caller hết thời gian chờ; task có thể vẫn chạy.

**7. Nếu worker bị interrupt trong lúc CountDownLatch.await(), nên xử lý thế nào?**  
Theo contract: có thể propagate InterruptedException; nếu bắt mà không xử lý ở tầng hiện tại, khôi phục interrupt flag và kết thúc hợp tác, đồng thời cleanup tài nguyên đã acquire.

**8. Vì sao Thread.sleep(50) trong polling không phải giải pháp phối hợp tốt nhất?**  
Nó tạo độ trễ/tải thức dậy theo chu kỳ và không tự cung cấp memory visibility. Blocking primitive cho phép chờ đúng sự kiện.

**9. Một Condition.await() có nhả tất cả lock mà thread đang giữ không?**  
Không, chỉ nhả lock liên kết với Condition trong thời gian await. Nếu thread giữ lock khác, deadlock vẫn có thể xảy ra.

**10. BlockingQueue capacity 2 có chắc producer chỉ giữ tối đa hai object trong toàn chương trình không?**  
Không. Capacity chỉ giới hạn số phần tử **đang nằm trong queue**. Producer/consumer có thể giữ reference khác ngoài queue, và side effect/bộ nhớ liên quan không tự bị giới hạn bởi capacity.

## 20. Kết luận

**Tránh busy waiting** không đơn thuần là thêm sleep vào while; đó là **chuyển từ hỏi lại trạng thái liên tục sang chờ đúng sự kiện bằng một giao thức có bảo đảm đồng bộ hóa**.

- Dùng **CountDownLatch** để chờ mốc hoàn tất một lần.
- Dùng **BlockingQueue** để chờ và trao phần tử.
- Dùng **Semaphore** để chờ quyền truy cập tài nguyên có giới hạn.
- Dùng **Future.get()** để chờ kết quả computation, kể cả lỗi/hủy.
- Dùng **Condition.await()** hoặc **Object.wait()** cùng predicate trong while khi điều kiện được quản lý bởi lock/monitor.

Với mọi primitive, phải làm rõ **timeout, interrupt, resource cleanup, memory visibility, trạng thái nghiệp vụ và dependency graph**. Blocking làm giảm vòng thăm dò vô ích, nhưng chỉ một giao thức đúng mới đảm bảo chương trình vừa tiến triển vừa bảo toàn các bất biến.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-coordination-012">So sánh phối hợp bằng shared state và message passing</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh phối hợp bằng shared state và message passing trong Java

## 1. Hai cách cơ bản để các thread cộng tác

Một chương trình concurrent thường chia công việc thành nhiều tác vụ chạy đồng thời. Những tác vụ ấy cần **trao đổi dữ liệu**, **báo tiến độ** hoặc **tạo ra một kết quả chung**. Trong Java, hai cách tổ chức nền tảng là:

1. **Shared state (trạng thái dùng chung):** các thread cùng truy cập một vùng dữ liệu. Chúng phối hợp bằng monitor `synchronized`, `Lock`, `volatile`, biến atomic, điều kiện chờ hoặc các cấu trúc dữ liệu concurrent.
2. **Message passing (truyền thông điệp):** các tác vụ trao đổi những thông điệp qua một kênh, chẳng hạn `BlockingQueue`. Một tác vụ có thể **sở hữu độc quyền** trạng thái của nó, còn tác vụ khác yêu cầu nó làm việc bằng cách gửi thông điệp thay vì sửa trực tiếp trạng thái đó.

Cả hai đều tồn tại trong Java concurrency thông thường. Không có lựa chọn nào luôn nhanh hơn hoặc luôn đơn giản hơn. Khác biệt quan trọng là **dữ liệu nằm ở đâu, ai có quyền thay đổi, ai chờ ai, và sự nhất quán được đảm bảo bằng cách nào**.

Ví dụ một bộ đếm nhận yêu cầu tăng từ hai worker:

```text
SHARED STATE

Producer A ────┐
                ├──> shared Counter <── synchronized / AtomicInteger
Producer B ────┘
        Các thread thực hiện cập nhật trực tiếp.


MESSAGE PASSING

Producer A ────┐
                ├──> BlockingQueue<Increment> ──> Counter owner
Producer B ────┘                             (chỉ owner sửa count)
        Các thread gửi yêu cầu, owner cập nhật.
```

Hai thiết kế có thể cho cùng kết quả đúng, nhưng đường truyền quyền sở hữu và chi phí điều phối khác nhau. Bài học thuộc chủ đề **Phối hợp luồng** của **Java concurrency nói chung**, không phụ thuộc framework.

## 2. Phân biệt dữ liệu dùng chung với truyền thông điệp

### Shared state

Ở mô hình shared state, nhiều thread có thể có tham chiếu tới cùng object mutable:

```java
final class Counter {
    private int count;

    synchronized void increment() {
        count++;
    }

    synchronized int get() {
        return count;
    }
}
```

Đây là **định nghĩa lớp minh họa**, không phải chương trình có `main`. Mọi thread gọi `increment()` trên **cùng instance** phải lấy monitor của instance đó. Nếu reader cũng gọi `get()`, các thao tác được bảo vệ nhất quán bằng cùng monitor.

**Ý tưởng:** dữ liệu chung vẫn tồn tại; các thread được phép truy cập nhưng phải tuân thủ giao thức đồng bộ của nó.

### Message passing

Ở mô hình truyền thông điệp, một worker khác có thể gửi yêu cầu tăng thông qua hàng đợi:

```java
record Increment(int amount) {}

BlockingQueue<Increment> mailbox = new ArrayBlockingQueue<>(32);
mailbox.put(new Increment(1));
```

Đây là **đoạn minh họa** cần import `java.util.concurrent.BlockingQueue` và `ArrayBlockingQueue`. Một thread tiêu thụ `mailbox.take()` sẽ nhận thông điệp. Nếu chỉ thread tiêu thụ đó sở hữu và cập nhật `count`, các producer không cần khóa biến `count` — vì chúng **không được phép sửa nó**.

**Ý tưởng:** thay vì chia sẻ quyền ghi state, các thread chia sẻ một kênh chuyển giao yêu cầu/dữ liệu. Cần đảm bảo an toàn cho kênh, xử lý khi đầy/rỗng, vòng đời consumer và ngữ nghĩa thông điệp.

**Điểm tinh tế:** message passing trong cùng JVM vẫn có thể chuyển **tham chiếu tới object trên heap chung**. Việc gọi `queue.put(message)` không tự deep-copy `message` và không tự biến payload mutable thành immutable. Muốn tránh data race, thường dùng **immutable message** hoặc giao thức chuyển quyền sở hữu rõ ràng: producer không sửa payload nữa sau khi gửi.

## 3. Bảng so sánh nhanh

| Tiêu chí | Shared state | Message passing |
| --- | --- | --- |
| Giao diện giữa các tác vụ | Gọi method/đọc-ghi object dùng chung | Gửi/nhận thông điệp qua queue hoặc channel |
| Chủ sở hữu mutable state | Có thể nhiều thread, cần giao thức đồng bộ | Thường một owner xử lý state; producer chỉ gửi yêu cầu |
| Primitive Java thường dùng | `synchronized`, `ReentrantLock`, `AtomicInteger`, `volatile`, `Condition` | `BlockingQueue`, `ArrayBlockingQueue`, `LinkedBlockingQueue`, `TransferQueue` |
| Nguy cơ thường gặp | Data race, stale read, lost update, deadlock, invariant bị phá | Queue đầy, producer bị chặn, backlog, shutdown khó, payload bị sửa sau khi gửi |
| Cách kiểm tra nhất quán | Khóa chung, thao tác atomic, immutable snapshot | Một consumer tuần tự hóa cập nhật hoặc protocol điều phối giữa owners |
| Kiểm soát tải | Thường phải bổ sung cơ chế giới hạn riêng | Bounded queue hỗ trợ backpressure tự nhiên |
| Trả kết quả | Gọi method thường có thể trả kết quả ngay | Có thể cần response message hoặc `CompletableFuture` |
| Độ trễ | Có thể ngắn khi chỉ một cập nhật atomic | Thêm enqueue, dequeue và chuyển ngữ cảnh/tác vụ |
| Mức cô lập | Caller có thể biết sâu cấu trúc state | Owner có thể giấu chi tiết state sau tập thông điệp |
| Phù hợp | Một số ít trường cập nhật đơn giản, snapshot được đồng bộ | Pipeline, producer–consumer, actor-like owner, bàn giao tác vụ |

**Không đồng nhất “message passing = không lock”:** queue thread-safe có thể sử dụng khóa, CAS, condition hoặc các kỹ thuật khác bên trong implementation. Điều bạn đạt được là **không phải tự chia sẻ quyền ghi state nghiệp vụ** nếu mô hình owner được thiết kế đúng.

**Không đồng nhất “shared state = chắc chắn sai”:** một `AtomicInteger.incrementAndGet()` hoặc một object được bảo vệ khóa đúng cách hoàn toàn có thể thread-safe và rõ ràng hơn việc tạo cả một worker với queue cho một phép tăng đơn giản.

## 4. Ví dụ chạy được: shared state với synchronized counter

Tạo tệp `SharedCounterDemo.java` (Java 17+):

```java
import java.util.concurrent.CountDownLatch;

public class SharedCounterDemo {
    private static final class Counter {
        private int value;

        synchronized void increment() {
            value++;
        }

        synchronized int value() {
            return value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        CountDownLatch start = new CountDownLatch(1);

        Runnable worker = () -> {
            try {
                start.await();
                for (int i = 0; i < 1_000; i++) {
                    counter.increment();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread a = new Thread(worker, "a");
        Thread b = new Thread(worker, "b");
        a.start();
        b.start();
        start.countDown();

        a.join();
        b.join();
        System.out.println("shared total=" + counter.value());
    }
}
```

```bash
javac SharedCounterDemo.java
java SharedCounterDemo
```

Kết quả khi không có interrupt ngoài ý muốn:

```text
shared total=2000
```

**Cơ chế đúng:**

1. `Counter` giữ `value` là trường private.
2. Hai thread gọi `increment()` trên **cùng một instance `counter`**.
3. Method synchronized dùng monitor của `counter`, nên hai phép đọc–sửa–ghi không xen ngang nhau.
4. `value()` đọc dưới chính monitor đó.
5. Main đợi cả hai `join()` nên chỉ in sau khi mọi phép tăng đã hoàn tất.

Nếu bỏ `synchronized` khỏi `increment()`, phép `value++` có thể mất cập nhật. Nếu chỉ để `synchronized` trên getter mà writer không khóa, giao thức không còn đúng. Nếu mỗi thread tạo một `Counter` riêng, kết quả sẽ là hai bộ đếm độc lập — không phải một shared counter.

### Khi dùng AtomicInteger thay vì synchronized

Với đúng một phép tăng đơn giản, có thể sử dụng `AtomicInteger`:

```java
AtomicInteger counter = new AtomicInteger();
counter.incrementAndGet();
int total = counter.get();
```

Đoạn này minh họa (cần import `java.util.concurrent.atomic.AtomicInteger`). Một atomic counter loại bỏ nhu cầu tự viết critical section để bảo vệ **duy nhất bộ đếm đó**. Tuy nhiên, nếu thao tác cần đổi ba trường đồng thời và duy trì một invariant, ba `AtomicInteger` riêng chưa chắc đủ; cần khóa bao phủ thao tác hoặc immutable snapshot được công bố atomically.

## 5. Ví dụ tương đương: message passing với một owner

Ở thiết kế tiếp theo, **producer không có tham chiếu tới biến `count`**. Mỗi producer gửi 1.000 thông điệp có giá trị `1` vào queue. Consumer duy nhất nhận từng thông điệp và cập nhật `count` cục bộ.

Tạo tệp `MessageCounterDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

public class MessageCounterDemo {
    private static final int STOP = -1;

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Integer> inbox = new ArrayBlockingQueue<>(16);
        CountDownLatch start = new CountDownLatch(1);
        int[] result = new int[1];

        Thread owner = new Thread(() -> {
            int count = 0;
            try {
                while (true) {
                    int amount = inbox.take();
                    if (amount == STOP) {
                        break;
                    }
                    count += amount;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            result[0] = count;
        }, "owner");

        Runnable producerTask = () -> {
            try {
                start.await();
                for (int i = 0; i < 1_000; i++) {
                    inbox.put(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread a = new Thread(producerTask, "producer-a");
        Thread b = new Thread(producerTask, "producer-b");
        owner.start();
        a.start();
        b.start();
        start.countDown();

        a.join();
        b.join();
        inbox.put(STOP);
        owner.join();

        System.out.println("message total=" + result[0]);
    }
}
```

```bash
javac MessageCounterDemo.java
java MessageCounterDemo
```

Kết quả khi không có interrupt ngoài ý muốn:

```text
message total=2000
```

**Vì sao đúng:**

- Producer chỉ gửi các số dương `1`, không giữ biến `count` của owner.
- `BlockingQueue.put()` và `take()` bảo vệ quá trình bàn giao giữa nhiều producer và consumer.
- Chỉ owner thay đổi biến `count` cục bộ, nên phần cập nhật count không có hai thread cạnh tranh sửa.
- Main đợi **cả hai producer kết thúc** trước khi gửi `STOP`. Mọi thông điệp số `1` đã vào FIFO queue trước STOP, do đó owner nhận hết các thông điệp rồi mới dừng.
- Main đợi `owner.join()` trước khi đọc `result[0]`; kết thúc thread qua `join` cung cấp quan hệ happens-before cho lần ghi kết quả của owner. Mảng một phần tử ở đây chỉ là nơi lưu kết quả cho ví dụ đơn giản, không phải một queue hoặc cơ chế đồng bộ.

**Chú ý:** nếu main gửi `STOP` trước khi các producer hoàn tất, owner có thể dừng quá sớm và để lại công việc chưa xử lý; producer có thể tiếp tục `put()` đến khi queue đầy và chờ vô hạn. Bài học chính là **shutdown cũng là một phần của giao thức message passing**.

Có thể dùng record `Increment(int amount)` thay vì `Integer` để phân biệt thông điệp nghiệp vụ với ký hiệu điều khiển; ở bài minh họa dùng số `STOP = -1` vì mọi dữ liệu hợp lệ đều là số dương. Ứng dụng thực cần tránh để marker trùng dữ liệu hợp lệ.

## 6. Vì sao cả hai chương trình đều cho 2000 nhưng ý nghĩa khác nhau?

Trong `SharedCounterDemo`, hai worker thực hiện trực tiếp công việc:

```text
A --increment()--> Counter
B --increment()--> Counter
         monitor serialize critical sections
```

Trong `MessageCounterDemo`, mỗi worker diễn đạt một yêu cầu:

```text
A --put(1)--> Queue --take()--> Owner --count += 1
B --put(1)--> Queue --take()--> Owner --count += 1
```

Về mặt correctness của tổng cuối, hai cách đều đúng khi giao thức được tuân thủ. Nhưng trách nhiệm khác nhau:

- Shared state: mọi đường đọc/ghi state đều phải đồng bộ; nếu một code path quên khóa, bug có thể xuất hiện.
- Message passing: owner chịu trách nhiệm tuần tự hóa state; producer phải gửi đúng message và quản lý lifecycle. Nếu payload mutable bị chia sẻ hoặc nhiều owner cùng sửa state ngoài protocol, lợi ích ownership biến mất.

Chi phí cũng khác: shared counter có thể chỉ phải lock/unlock hoặc atomic instruction, còn message passing thường thêm thao tác enqueuing, dequeuing, buffer và context switch. Với nhiều producer, queue còn cung cấp điểm gom tải và tách nhịp sản xuất/tiêu thụ.

## 7. Sở hữu dữ liệu: actor-like model trong cùng JVM

Một thiết kế message passing dễ suy luận là **single writer**:

1. Một thành phần sở hữu mutable state.
2. Các thành phần khác **không nhận tham chiếu mutable** tới state đó.
3. Họ gửi **command**, **event** hoặc **request** qua kênh thread-safe.
4. Owner nhận từng message rồi đổi state.
5. Nếu cần trả lời, owner gửi một response hoặc hoàn thành một `CompletableFuture`.

Mô hình này gần với nguyên tắc actor, nhưng sử dụng `Thread` và `BlockingQueue` **không tự tạo ra toàn bộ actor runtime**. Bạn vẫn cần tự xử lý mailbox, scheduling, failure recovery, ordering, giới hạn tải và shutdown.

### Command, event, request khác nhau ở đâu?

- **Command:** “hãy thực hiện thay đổi X”. Có thể được chấp nhận hoặc từ chối.
- **Event:** “X đã xảy ra”. Thường mô tả một sự kiện đã commit; người tiêu thụ có thể cập nhật một view.
- **Request–response:** “hãy tính hoặc thay đổi X rồi gửi kết quả cho tôi”. Cần correlation giữa yêu cầu và phản hồi.

Có thể dùng Java record để định nghĩa thông điệp bất biến:

```java
record Add(int amount) {}
record TotalRequest(long requestId) {}
record TotalReply(long requestId, int total) {}
```

Đây là những định nghĩa record minh họa. Record có các trường component là final, nhưng **không tự bất biến sâu** nếu chứa mảng hoặc danh sách mutable. Với primitive và `String`, xây dựng message bất biến đơn giản hơn.

## 8. Ví dụ request–response bằng CompletableFuture

Không phải mọi caller đều muốn “gửi rồi quên”. Đôi khi họ cần biết owner đã xử lý yêu cầu và kết quả là bao nhiêu. Một cách trong JVM là đặt một `CompletableFuture` trong message; owner hoàn thành future khi xử lý xong.

Tạo tệp `QueueRequestReplyDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;

public class QueueRequestReplyDemo {
    record Add(int amount, CompletableFuture<Integer> reply) {}

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Add> inbox = new ArrayBlockingQueue<>(8);
        CompletableFuture<Integer> first = new CompletableFuture<>();
        CompletableFuture<Integer> second = new CompletableFuture<>();

        Thread owner = new Thread(() -> {
            int total = 0;
            try {
                for (int i = 0; i < 2; i++) {
                    Add message = inbox.take();
                    total += message.amount();
                    message.reply().complete(total);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "owner");

        owner.start();
        inbox.put(new Add(5, first));
        inbox.put(new Add(7, second));

        System.out.println("first=" + first.join());
        System.out.println("second=" + second.join());
        owner.join();
    }
}
```

```bash
javac QueueRequestReplyDemo.java
java QueueRequestReplyDemo
```

Kết quả:

```text
first=5
second=12
```

Main gửi lần lượt hai message vào FIFO queue. Owner là consumer duy nhất, nên xử lý `Add(5)` trước `Add(7)`. Mỗi message kèm future riêng, tránh nhầm response. `CompletableFuture.join()` trên main đợi kết quả của từng request; đây là **một cách đồng bộ giữa caller và owner**, không phải `Thread.join()`. Nếu future hoàn thành exceptionally, `join()` có thể ném `CompletionException`.

**Quan trọng:** đối tượng `CompletableFuture` là mutable để owner hoàn thành response. Message record ở đây chỉ cố định tham chiếu tới future; nó không phải đồ thị bất biến sâu hoàn toàn. Điều này được phép vì protocol trao quyền hoàn thành cho owner và quyền chờ cho caller, nhưng mọi thành phần phải tuân thủ hợp đồng.

Một implementation production cần định nghĩa nếu owner bị crash, bị interrupt hoặc gặp exception trước khi hoàn thành future thì phải báo lỗi thế nào; nếu bỏ quên future, caller `join()` có thể chờ mãi. Nên hoàn thành exceptionally, đặt timeout phía caller và thiết kế vòng đời rõ ràng. `CompletableFuture.cancel()` cũng **không tự đảm bảo ngắt luồng owner** hoặc gỡ bỏ message đang nằm trong queue.

## 9. Shared state vẫn hữu ích khi có nhiều reader

Không phải cứ có nhiều thread đọc là phải gửi “ReadRequest” cho một owner. Ví dụ thông tin cấu hình được tạo thành **immutable snapshot** rồi công bố qua `AtomicReference<Config>`:

```java
record Config(String host, int port) {}

AtomicReference<Config> active =
        new AtomicReference<>(new Config("localhost", 8080));

Config snapshot = active.get();
System.out.println(snapshot.host() + ":" + snapshot.port());
```

Đây là đoạn minh họa, cần import `java.util.concurrent.atomic.AtomicReference`, đặt trong một class/method hợp lệ. Reader chỉ lấy một tham chiếu snapshot và đọc các field bất biến, không cần owner nhận một message để trả host/port. Khi config đổi, writer tạo `Config` mới hoàn chỉnh rồi công bố **một tham chiếu** phù hợp.

Đây là một mô hình **lai**: dữ liệu đọc được chia sẻ, nhưng bất biến và được công bố an toàn; cập nhật có thể đến qua message passing tới một owner, rồi owner thay root reference. Hai hướng không loại trừ nhau.

**Cẩn trọng:** hai lần đọc `active.get().host()` và `active.get().port()` có thể đến từ hai phiên bản nếu writer thay snapshot giữa chúng. Khi cần consistency, lấy `Config snapshot = active.get()` **một lần** rồi đọc mọi component từ snapshot ấy.

## 10. Shared mutable object cần những gì?

Khi chọn shared state, cần xác định:

- **Invariant:** điều kiện phải luôn đúng, ví dụ `balance >= 0` hoặc `left + right == 100`.
- **Reader/writer:** ai có quyền đọc, ghi, và tất cả có tuân thủ cùng cơ chế không?
- **Atomicity:** một thay đổi logic có thể gồm nhiều trường hay không?
- **Visibility:** thread đọc có bảo đảm thấy ghi của thread khác không?
- **Điểm chờ:** có giữ lock trong lúc I/O hoặc chờ thread khác không?
- **Liveness:** có thể deadlock, starvation hoặc vòng chờ thiếu tín hiệu không?

### Ví dụ dùng lock đúng phạm vi

```java
final class Balance {
    private int amount;

    synchronized boolean withdraw(int cost) {
        if (cost < 0 || cost > amount) {
            return false;
        }
        amount -= cost;
        return true;
    }

    synchronized void deposit(int value) {
        if (value < 0) {
            throw new IllegalArgumentException();
        }
        amount += value;
    }

    synchronized int snapshot() {
        return amount;
    }
}
```

Đây là đoạn định nghĩa lớp minh họa. Kiểm tra đủ tiền và phép trừ **cùng một critical section** để không có hai writer cùng vượt điều kiện. Cũng cần kiểm tra overflow nếu domain có giá trị lớn. Việc khóa writer mà để reader đọc trường trực tiếp ngoài lock vẫn là lỗi đồng bộ.

Một `volatile int amount` không làm thao tác “kiểm tra rồi trừ” nguyên tử. `AtomicInteger` có thể giải bằng vòng CAS nếu một field, nhưng code phải retry/validate trên state mới. Nếu dữ liệu trải nhiều field thì một immutable snapshot hoặc critical section là cách dễ chứng minh hơn.

## 11. Message passing và happens-before: dữ liệu trong thông điệp có nhìn thấy được không?

Một queue thuộc `java.util.concurrent` không chỉ làm nhiệm vụ giữ phần tử. API của các concurrent collection có **bảo đảm bộ nhớ** để việc chuyển giao giữa thread có ý nghĩa.

Với các `BlockingQueue` tiêu chuẩn, hành động của producer **trước khi đặt một object vào queue** *happens-before* những hành động của consumer **sau khi lấy object đó ra** theo hợp đồng các concurrent collection. Vì vậy producer có thể tạo payload, khởi tạo các field và `put`; consumer lấy message qua `take` có cơ sở JMM để nhìn thấy những ghi xảy ra trước lúc enqueue.

Nhưng hãy phân biệt hai điều:

1. **Công bố dữ liệu đã tạo trước khi gửi:** queue giúp producer và consumer có thứ tự/visibility rõ.
2. **Sửa payload sau khi gửi:** nếu producer tiếp tục sửa một object mutable trong khi consumer đang đọc, queue **không tự bảo vệ những sửa đổi sau enqueue**.

Ví dụ nguy hiểm:

```java
List<String> payload = new ArrayList<>();
payload.add("A");
queue.put(payload);
payload.add("B"); // Có thể tranh chấp với consumer đang duyệt payload.
```

Đây là đoạn phản ví dụ, cần khai báo/import `queue` và các loại collection phù hợp. Cách sửa là copy thành immutable data trước khi gửi:

```java
List<String> message = List.copyOf(payload);
queue.put(message);
```

Nếu các phần tử danh sách là object mutable, `List.copyOf` chỉ copy **cấu trúc/tham chiếu phần tử**, không tự deep-copy từng object. Nên dùng kiểu phần tử bất biến hoặc xác định quyền sở hữu rõ.

## 12. Ordering: thứ tự thông điệp là thứ tự nào?

`BlockingQueue` có nhiều implementation với hợp đồng ordering khác nhau. `ArrayBlockingQueue` và `LinkedBlockingQueue` được dùng như FIFO queue: các phần tử được lấy theo thứ tự vào queue. Tuy nhiên, khi **nhiều producer gửi đồng thời**, ai enqueue trước là kết quả của việc xen kẽ được hệ thống thực hiện, **không phải thứ tự tuyệt đối theo tên thread**.

Một consumer duy nhất xử lý từng message theo thứ tự dequeue, nhưng nếu có **nhiều consumer**, các message có thể được lấy bởi các worker khác nhau và **hoàn tất xử lý không theo đúng thứ tự nhận**.

Câu hỏi thiết kế:

- Có cần giữ thứ tự **trong một producer** không?
- Có cần thứ tự toàn cục **giữa nhiều producer** không? Nếu có, ai cấp sequence và ở đâu?
- Có cần đảm bảo **xử lý xong** tuần tự hay chỉ **dequeue** tuần tự?
- Có message nào được xử lý lại sau lỗi không?
- Nếu message là một phần của giao dịch, làm sao giữ consistency giữa các message?

**FIFO không có nghĩa exactly-once processing.** Queue trong bộ nhớ không tự cung cấp lưu trữ bền vững hoặc giao dịch. Một consumer có thể lấy message ra rồi gặp exception trước khi cập nhật kết quả. Cần quyết định retry, dead-letter, idempotency hoặc cơ chế durable messaging khi bài toán yêu cầu.

## 13. Backpressure: khác biệt rất quan trọng

Một producer có thể tạo việc nhanh hơn consumer xử lý. Nếu queue tăng vô hạn, chương trình có thể giữ ngày càng nhiều object trong heap và cuối cùng gặp áp lực GC hoặc `OutOfMemoryError`.

Một **bounded queue** đặt giới hạn backlog. Khi đầy:

- `put(message)` **chờ** đến khi có chỗ; có thể bị interrupt bằng `InterruptedException`.
- `offer(message)` không đợi, trả `false` nếu không thêm được ngay.
- `offer(message, timeout, unit)` chờ tối đa thời hạn và có thể bị interrupt.
- `add(message)` có thể ném `IllegalStateException` nếu queue có giới hạn và không còn chỗ.

### Ví dụ backpressure với kết quả xác định

Tạo `BackpressureDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BackpressureDemo {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(1);

        queue.put(10);
        System.out.println("offer while full=" + queue.offer(20));
        System.out.println("take=" + queue.take());
        System.out.println("offer after take=" + queue.offer(20));
        System.out.println("take again=" + queue.take());
    }
}
```

```bash
javac BackpressureDemo.java
java BackpressureDemo
```

Kết quả:

```text
offer while full=false
take=10
offer after take=true
take again=20
```

Queue có capacity 1. Khi chứa `10`, thêm `20` bằng `offer` không thành công. Sau khi `take` lấy `10`, có một ô trống cho `20`. Ví dụ chạy một thread để minh họa **hợp đồng queue**, không phải phép đo số luồng cùng chạy.

### Chọn chính sách khi queue đầy

Không có chính sách đúng cho mọi hệ thống. Có thể:

- **Block producer:** dùng `put` để lan ngược áp lực; cần xét producer có được phép chờ không.
- **Fail fast:** dùng `offer` trả `false` để caller nhận biết “busy” và quyết định retry.
- **Timeout:** dùng `offer(timeout)` để cân bằng thời gian chờ.
- **Drop/coalesce:** chỉ áp dụng nếu nghiệp vụ cho phép mất hoặc gộp message (ví dụ một số chỉ số quan sát).
- **Scale consumer hoặc partition:** chia tải theo key khi yêu cầu ordering/ownership cho phép.

Với **unbounded queue**, `put` thường không block vì giới hạn capacity theo kiểu bounded, nhưng không nên xem “không có backpressure” là miễn phí: heap vẫn hữu hạn. `LinkedBlockingQueue` không truyền capacity thì có khả năng nhận rất nhiều phần tử trước khi chạm giới hạn thực tế của bộ nhớ.

## 14. Shutdown và cancellation của message passing

Ở shared state, một lời gọi synchronized method có thể kết thúc sau khi sửa xong. Với message passing, còn một **mailbox**, một hoặc nhiều consumer, và các producer có thể đang đợi gửi. Đóng hệ thống cần trả lời:

1. Có nhận message mới nữa không?
2. Các producer đang block tại `put` được xử lý như thế nào?
3. Có cần xử lý hết queue trước khi dừng?
4. Consumer đang block tại `take` được đánh thức bằng cách nào?
5. Request đang chờ response sẽ được hoàn thành hay báo lỗi?
6. Có bao nhiêu consumer và cần bao nhiêu tín hiệu dừng?

### Poison pill (thông điệp dừng)

Ở `MessageCounterDemo`, main gửi `STOP` sau khi producer đã hoàn thành. Một consumer nhận STOP và kết thúc. Với **N consumer**, thông thường cần **N tín hiệu dừng** tương thích với protocol, hoặc một cơ chế dừng khác; gửi một STOP không tự đánh thức tất cả consumer.

Nếu queue đầy mà consumer đã chết, `put(STOP)` có thể chờ vô hạn. Nếu producer còn chạy, STOP có thể nằm trước các message nghiệp vụ, khiến consumer kết thúc quá sớm. Do đó cần **quy trình shutdown theo thứ tự**: ngừng nhận mới, chờ producer hoặc hủy có kiểm soát, cho phép drain nếu cần, gửi tín hiệu dừng hợp lệ, đợi consumer cleanup.

### Interrupt khác poison pill

`Thread.interrupt()` là **yêu cầu hủy hợp tác** ở cấp thread; các phương thức chặn như `BlockingQueue.take()` và `put()` có thể ném `InterruptedException`. Poison pill là **một message của giao thức nghiệp vụ**. Chúng không tương đương:

- Interrupt có thể dừng consumer khi queue vẫn còn message.
- Poison pill thường giữ được thứ tự theo queue và cho phép “drain rồi dừng”, nhưng phụ thuộc khả năng enqueue marker.
- Có thể kết hợp một cờ lifecycle và cơ chế interrupt/wakeup; cần đảm bảo không có producer gửi sau trạng thái đóng.

Không dùng `Thread.stop()` để hủy consumer; đó là API không an toàn và đã lỗi thời.

## 15. Error handling: exception xảy ra ở đâu?

Trong shared state, caller thường thực thi method trên chính thread của mình. Nếu method ném exception, caller nhận exception trực tiếp và có thể xử lý trong cùng call stack.

Trong message passing, producer thường chỉ enqueue. Exception xảy ra trong **consumer** sau đó, không tự “quay về” thread producer:

```text
Producer -> put(command) -> return
Consumer -> take(command) -> process -> exception
```

Muốn caller biết kết quả, phải thiết kế **response channel**, một future, callback, event kết quả hoặc cơ chế ghi nhận lỗi phù hợp. Nếu owner ngừng vì exception và các future chưa hoàn thành, caller có thể treo khi chờ.

Trong một worker event loop, nên phân biệt:

- **Lỗi của một message:** có thể báo lỗi cho riêng request đó, tiếp tục xử lý phần tử tiếp theo nếu state vẫn nhất quán.
- **Lỗi phá invariant của owner:** có thể phải ngừng owner, thông báo những request đang pending và khởi tạo lại state theo hợp đồng.
- **Lỗi hạ tầng:** nếu queue hoặc resource phụ trợ không còn hoạt động, cần có đường shutdown/retry rõ ràng.

Message passing tách thành phần xử lý nhưng **không tự giải quyết retry, exactly-once, transaction, idempotency hoặc hồi phục sau crash**. Những yêu cầu ấy cần giao thức ứng dụng và thường là tầng lưu trữ phù hợp.

## 16. Trạng thái chia sẻ vẫn xuất hiện ngay trong message passing

Tại JVM, “truyền message” không phải truyền dữ liệu qua một không gian địa chỉ cô lập như hai process ở hai máy khác nhau. Các thread vẫn có thể có tham chiếu tới cùng object. Vì vậy cần hai nguyên tắc đồng thời:

1. **Kênh phải thread-safe:** chẳng hạn `ArrayBlockingQueue` là primitive được thiết kế cho nhiều producer/consumer.
2. **Nội dung message có ownership rõ:** tốt nhất là immutable DTO/record chứa dữ liệu bất biến; nếu là payload mutable được chuyển quyền, producer phải **ngừng sửa** sau khi chuyển và consumer phải sở hữu rõ ràng.

Vì vậy hai cách thường hòa trộn:

- Producer tạo một **immutable message** ở local state.
- Producer `put` vào **shared thread-safe queue**.
- Consumer sở hữu mutable state nghiệp vụ, cập nhật tuần tự.
- Consumer có thể **publish immutable snapshot** qua một `AtomicReference` để reader đọc nhanh.

Mô hình lai này dùng shared state ở phần queue/atomic reference, nhưng tách trách nhiệm ghi state chính. Không có ranh giới tuyệt đối “message passing không chia sẻ bộ nhớ”.

## 17. Nên chọn shared state hay message passing?

### Chọn shared state khi

- Trạng thái nhỏ, bất biến đơn giản và thao tác cần phản hồi **ngay trong lời gọi method**.
- Có primitive atomic phù hợp, như tăng một counter bằng `AtomicInteger.incrementAndGet()`.
- Critical section ngắn và số thread tranh chấp ở mức chấp nhận được.
- Các thao tác trên object có contract rõ ràng, có thể gói trong method synchronized hoặc lock bảo vệ toàn bộ invariant.
- Cần đọc snapshot hiện hành với độ trễ thấp và đã có cơ chế công bố dữ liệu đúng.

### Chọn message passing khi

- Có mô hình **producer–consumer**, pipeline hoặc worker nhận các command.
- Muốn một owner quản lý độc quyền mutable state để giảm số đường ghi trực tiếp.
- Muốn tách nhịp tạo việc và xử lý, đồng thời cần **bounded backlog/backpressure**.
- Có nhiều loại message và một vòng xử lý tuần tự hóa các thay đổi liên quan.
- Cần gom command, ghi log sự kiện, phân luồng theo key hoặc thêm cơ chế gửi phản hồi theo request.

### Cân nhắc phương án lai khi

- Một owner nhận message để cập nhật state;
- State public được biểu diễn bằng **immutable snapshot**;
- Nhiều reader chỉ lấy snapshot bằng atomic/volatile reference;
- Queue dùng để điều phối writer, còn reader không phải gửi message chỉ để xem dữ liệu.

Bảng chọn nhanh:

| Bài toán | Lựa chọn xuất phát | Rủi ro cần kiểm tra |
| --- | --- | --- |
| Nhiều thread tăng một thống kê | `AtomicLong` hoặc `LongAdder` theo yêu cầu snapshot | Contention, overflow, ngữ nghĩa `sum()` |
| Chuyển tiền trong một object nhỏ | `synchronized`/lock hoặc immutable CAS snapshot | Tính nguyên tử của kiểm tra và cập nhật |
| Một worker tiêu thụ nhiều job | `BlockingQueue<Job>` + consumer | Backpressure, shutdown, interrupt |
| Nhiều producer gửi log sự kiện | Bounded queue + logger owner (nếu thích hợp) | Mất log khi queue đầy, lỗi I/O, drain |
| Cấu hình ít thay đổi, nhiều reader | Immutable config + `AtomicReference` | Reader lấy cùng snapshot một lần |
| Nhiều task cùng chỉnh một object graph phức tạp | Một owner nhận command hoặc lock bao trùm invariant | Chi phí queue/lock và state leak |
| Cần request–response bất đồng bộ | Message + `CompletableFuture`/response channel | Timeout, correlation, exceptional completion |
| Cần giao dịch xuyên database và RAM | Giao thức transaction/idempotency ở tầng tương ứng | Không primitive trong JVM nào tự bảo đảm distributed atomicity |

## 18. Performance và fairness: đừng quyết định chỉ từ tên mô hình

**Shared state** có thể rất nhanh khi lock không bị tranh hoặc khi dùng primitive atomic thích hợp. Nếu một class monitor toàn cục được giữ qua I/O chậm, nó có thể gây head-of-line blocking. Nếu nhiều thread cùng sửa một giá trị atomic nóng, cache contention có thể khiến throughput giảm.

**Message passing** có thêm bước enqueue/dequeue, copy/đóng gói message và thường là chuyển ngữ cảnh. Bù lại, owner có thể batch nhiều message, tối ưu locality của state, hạn chế số writer và giữ backpressure bằng queue giới hạn.

Không có kết luận “không khóa = nhanh” hay “queue = luôn mở rộng tốt”. Một consumer duy nhất có thể trở thành điểm nghẽn nếu khối lượng xử lý quá lớn. Muốn scale, có thể chia state thành các partition với mỗi owner riêng, nhưng yêu cầu ordering và invariant xuyên partition trở nên phức tạp hơn.

Cũng cần phân biệt:

- **Fairness:** một request có bị chờ mãi vì các request khác không? Một số implementation lock/queue cho tùy chọn fairness nhưng có tradeoff.
- **Throughput:** số công việc hoàn thành mỗi giây.
- **Latency:** thời gian từ lúc yêu cầu gửi tới lúc có kết quả.
- **Tail latency:** các trường hợp chậm nhất, thường chịu tác động mạnh bởi queue backlog.
- **Memory:** số message chờ trong heap, object allocation và pressure của GC.

Đo trên workload thật hoặc benchmark đúng cách; một vài lần `System.nanoTime()` không đủ để khẳng định mô hình nào nhanh hơn vì ảnh hưởng của JIT, warm-up, số core và lịch chạy.

## 19. Những hiểu nhầm phổ biến

### 19.1. “Message passing tức là không còn data race”

Sai nếu producer gửi object mutable rồi tiếp tục sửa. Consumer có thể đọc cùng object trong lúc producer ghi. Queue chỉ bảo đảm giao thức chuyển giao qua các thao tác queue; nó không tự đóng băng payload.

### 19.2. “Shared state chỉ cần volatile là đủ”

Sai nếu thao tác là read-modify-write hoặc invariant trải nhiều field. `volatile` phù hợp công bố/đọc một số trạng thái theo hợp đồng, nhưng không biến `counter++` thành atomic transaction.

### 19.3. “Một owner nghĩa là không cần suy nghĩ về synchronization”

Sai nếu dữ liệu của owner rò rỉ qua getter trả mutable reference, callback hoặc future/collection không được bảo vệ. Single writer là một **quy ước quyền sở hữu**, không phải thuộc tính mà JVM tự áp đặt cho bất kỳ object nào.

### 19.4. “Queue FIFO bảo đảm mọi tác vụ hoàn tất đúng thứ tự”

FIFO của queue bảo đảm thứ tự **dequeue** theo hợp đồng của queue. Với nhiều consumer, thời điểm hoàn tất có thể khác thứ tự nhận. Với nhiều producer, thứ tự enqueue toàn cục phụ thuộc lịch chạy.

### 19.5. “`put()` luôn trả về ngay”

Với bounded queue đã đầy, `put` sẽ block cho đến khi có chỗ hoặc bị interrupt. Nếu thiết kế không xử lý cancellation/shutdown, producer có thể chờ vô hạn.

### 19.6. “`Future.cancel(true)` là consumer đã dừng và cleanup xong”

Cancel gửi tín hiệu theo hợp đồng của Future/executor, không tự bảo đảm thread đã cleanup ngay hoặc queue đã drain. Muốn xác nhận shutdown hoàn tất, cần thêm cơ chế giám sát/đợi phù hợp.

### 19.7. “Consumer đã nhận message thì chắc chắn đã xử lý thành công”

Không. Consumer có thể ném exception sau `take()`. Nếu cần bảo đảm retry/durable delivery, một queue trong RAM đơn giản chưa đủ.

### 19.8. “Gửi một poison pill dừng được mọi consumer”

Nếu có nhiều consumer, một marker chỉ được một consumer nhận. Thiết kế số marker hoặc cơ chế shutdown phù hợp với số worker và hợp đồng phân phối.

### 19.9. “`CompletableFuture` phản hồi cho request tự là message bất biến”

Record chứa tham chiếu `CompletableFuture` chỉ cố định tham chiếu, còn future tự nó là object có trạng thái thay đổi. Cần phân quyền ai có thể complete/cancel và xử lý timeout/exception.

### 19.10. “Khi đã dùng message passing thì không cần tính chuyện nhất quán xuyên owner”

Nếu một nghiệp vụ phải cập nhật state ở **hai owner khác nhau**, gửi hai message liên tiếp không tự biến hai thay đổi thành một transaction. Cần xác định thứ tự, xác nhận, bù trừ hoặc coordinator; hoặc cân nhắc gộp invariant trong một owner.

## 20. Tự kiểm tra

**Câu 1.** Hai worker gọi một `synchronized` method trên cùng counter. Vì sao không lost update?

**Trả lời:** Chúng dùng cùng monitor của instance; không thể cùng thực hiện phần read-modify-write đã bảo vệ tại một thời điểm. Các method đọc liên quan cũng phải dùng cùng cơ chế.

**Câu 2.** Hai producer gửi `Increment(1)` qua `BlockingQueue` tới một consumer duy nhất. Consumer có cần đồng bộ biến count cục bộ với hai producer không?

**Trả lời:** Không nếu count chỉ thuộc quyền sở hữu consumer và producer không có đường sửa/đọc nó ngoài protocol. Queue giải quyết việc chuyển message giữa thread; kết quả chuyển ra thread khác cần quan hệ công bố phù hợp.

**Câu 3.** Sau `queue.put(list)`, producer có thể tiếp tục `list.add(...)` mà vẫn an toàn không?

**Trả lời:** Không có bảo đảm nếu consumer đồng thời đọc danh sách mutable ấy. Dùng snapshot bất biến hoặc một giao thức chuyển quyền sở hữu không còn writer cũ.

**Câu 4.** `ArrayBlockingQueue` có capacity 1 đang chứa một phần tử. `offer(newItem)` trả gì?

**Trả lời:** `false` nếu không còn chỗ vào thời điểm gọi; `put(newItem)` thì chờ có chỗ trừ khi bị interrupt.

**Câu 5.** Có thể gửi STOP vào queue trước khi producer hoàn thành không?

**Trả lời:** Dễ gây lỗi. Consumer có thể dừng trước khi xử lý các thông điệp đến sau STOP, producer có thể block nếu queue không còn consumer. Cần giao thức shutdown/drain rõ.

**Câu 6.** Queue FIFO và hai consumer có đảm bảo message đầu tiên hoàn tất xử lý trước message thứ hai không?

**Trả lời:** Không. FIFO chi phối thứ tự lấy phần tử, còn hai consumer xử lý song song có thể hoàn thành đảo thứ tự.

**Câu 7.** Một message kèm `CompletableFuture` đã được gửi; consumer gặp exception trước khi complete. Caller `future.join()` có tự nhận exception từ thread consumer không?

**Trả lời:** Không tự động. Cần catch lỗi và `completeExceptionally` hoặc áp dụng timeout, supervision và chính sách failure rõ ràng.

**Câu 8.** Trong shared state, khi nào chỉ một `AtomicInteger` là lựa chọn hợp lý hơn message passing?

**Trả lời:** Khi chỉ cần cập nhật một biến số nguyên bằng thao tác atomic đơn giản và không có invariant nhiều field, không cần ownership pipeline hay backpressure.

**Câu 9.** Một owner nhận message rồi thay `AtomicReference<Config>` để nhiều reader đọc. Đây là shared state hay message passing?

**Trả lời:** Là **mô hình lai**: message passing cho đường ghi, shared immutable snapshot cho đường đọc.

**Câu 10.** Hai owner xử lý hai phần giao dịch. Có thể bảo đảm cả hai cập nhật atomic chỉ bằng hai lần `queue.put` không?

**Trả lời:** Không. Hai thông điệp tạo hai hành động độc lập. Cần thiết kế transaction/coordination hay bù trừ phù hợp.

## 21. Kết luận

**Shared state** phối hợp bằng cách bảo vệ dữ liệu mà nhiều thread cùng truy cập. Điểm mạnh là trực tiếp, có thể rất hiệu quả với thao tác nhỏ; yêu cầu cốt lõi là **mọi reader và writer tuân thủ cùng cơ chế synchronization và invariant**.

**Message passing** phối hợp bằng cách gửi dữ liệu/yêu cầu qua kênh thread-safe và thường giao **một owner** thực hiện cập nhật. Điểm mạnh là giảm chia sẻ quyền ghi state và cho phép điều phối producer–consumer, queueing, backpressure; đổi lại, phải thiết kế message immutability/ownership, thứ tự xử lý, response, failure và shutdown.

Hai cách không loại trừ nhau: một ứng dụng Java có thể dùng `BlockingQueue` để gửi command, một owner để cập nhật trạng thái, và một `AtomicReference` chứa immutable snapshot cho những reader cần đọc nhanh. Chọn theo **đơn vị nhất quán, tốc độ cập nhật, nhu cầu phản hồi và vòng đời công việc**, không theo khẩu hiệu “dùng queue sẽ không có race” hay “dùng lock thì không scalable”.


</details>

- [Quay lại đầu trang](#back-to-top)

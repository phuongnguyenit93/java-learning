<a id="back-to-top"></a>

# Atomic &amp; CAS

## Menu
- [AtomicInteger, AtomicLong và AtomicBoolean](#atomic-and-cas-001)
- [AtomicReference và cập nhật object state](#atomic-and-cas-002)
- [compareAndSet() và nguyên tắc CAS](#atomic-and-cas-003)
- [CAS loop, retry và contention](#atomic-and-cas-004)
- [getAndUpdate(), updateAndGet() và accumulator](#atomic-and-cas-005)
- [Atomic compound reference qua immutable state](#atomic-and-cas-006)
- [AtomicIntegerFieldUpdater và điều kiện áp dụng](#atomic-and-cas-007)
- [ABA problem và AtomicStampedReference](#atomic-and-cas-008)
- [LongAdder và LongAccumulator khi cập nhật cạnh tranh](#atomic-and-cas-009)
- [VarHandle: truy cập atomic và access mode](#atomic-and-cas-010)
- [Atomic không thay thế giao dịch nhiều đối tượng](#atomic-and-cas-011)
- [Chọn atomic, lock hay thread confinement](#atomic-and-cas-012)
- [AtomicIntegerArray, AtomicLongArray và AtomicReferenceArray: cập nhật từng phần tử](#atomic-and-cas-013)
- [Hàm updateAndGet, accumulateAndGet và nguy cơ tác dụng phụ khi CAS phải thử lại](#atomic-and-cas-014)

## <a id="atomic-and-cas-001">AtomicInteger, AtomicLong và AtomicBoolean</a>

<details>
<summary>Xem chi tiết</summary>

# AtomicInteger, AtomicLong và AtomicBoolean trong Java

Khi nhiều thread cùng đọc và thay đổi một biến, ba thao tác tưởng như đơn giản — đọc giá trị, tính giá trị mới, rồi ghi lại — có thể xen kẽ khiến dữ liệu sai. `AtomicInteger`, `AtomicLong` và `AtomicBoolean` trong `java.util.concurrent.atomic` cung cấp các **phép đọc, ghi và cập nhật nguyên tử trên một giá trị** với các bảo đảm bộ nhớ xác định theo Java Memory Model (JMM). Chúng đặc biệt phù hợp với bộ đếm, sequence number, cờ trạng thái và một số giao thức chuyển trạng thái đơn giản.

Điểm quan trọng nhất: **một thao tác atomic không có nghĩa mọi logic bao quanh nó tự động atomic**. Dùng `AtomicInteger.incrementAndGet()` có thể tránh lost update khi tăng một bộ đếm, nhưng một chuỗi `get()` rồi `set()` vẫn có khoảng xen kẽ; `AtomicBoolean` có thể bảo đảm chỉ một thread đổi cờ từ `false` sang `true`, nhưng không tự bảo đảm công việc phía sau đã hoàn tất.

Bài này thuộc chủ đề **Atomic & CAS** của **Java concurrency nói chung**. Các API và ví dụ dùng Java SE 17+, không dùng cơ chế concurrency riêng của Spring Framework.

## 1. Vì sao `int`, `long` và `boolean` thông thường không đủ?

Ví dụ:

```java
final class UnsafeCounter {
    private int value;

    void increment() {
        value++; // Không atomic.
    }

    int get() {
        return value;
    }
}
```

Đoạn trên là class minh họa, không có `main`. Hai thread có thể cùng đọc `value == 10`, cùng tính `11` rồi cùng ghi `11`. Kết quả bị **lost update**: hai lời gọi tăng chỉ tạo hiệu ứng một lần tăng.

`value++` là phép **read-modify-write**, không phải thao tác không thể xen giữa. Đánh dấu field thành `volatile int value` giúp cung cấp semantics visibility/ordering cho từng lần đọc/ghi volatile, **nhưng không làm toàn bộ `value++` thành atomic**.

Tách ba nhu cầu:

- **Atomicity:** một operation diễn ra như một bước không thể bị thread khác xen giữa ở ranh giới API đã định nghĩa.
- **Visibility và ordering:** reader được bảo đảm quan sát các ghi trước đó khi có quan hệ happens-before phù hợp.
- **Coordination/progress:** thread nào chờ ai, ai có quyền thực hiện tác vụ, khi nào dừng hoặc báo hoàn tất.

Atomic variables hỗ trợ hai nhóm đầu trong **phạm vi mỗi phép toán và giao thức dùng đúng**. Chúng không thay `CountDownLatch`, `BlockingQueue`, `Future` hoặc monitor khi cần phối hợp tiến độ hay giao dịch nhiều field.

## 2. Ba lớp atomic cơ bản

| Lớp | Giá trị được quản lý | Mẫu sử dụng phù hợp |
| --- | --- | --- |
| `AtomicInteger` | Một `int` 32-bit có dấu | Đếm task, version nhỏ, trạng thái số, quota đơn biến |
| `AtomicLong` | Một `long` 64-bit có dấu | Sequence number, số liệu lớn, counter dài hạn |
| `AtomicBoolean` | Một `boolean` | Cờ một lần, trạng thái enabled/stopped, quyền bắt đầu thao tác |
 
Cả ba đều nằm trong `java.util.concurrent.atomic` và cho phép **nhiều thread dùng chung một instance**. Ví dụ:

```java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

AtomicInteger total = new AtomicInteger(0);
AtomicLong sequence = new AtomicLong(1000L);
AtomicBoolean started = new AtomicBoolean(false);
```

Đây là đoạn minh họa khai báo biến, không phải chương trình độc lập.

Không có nghĩa object nào chứa atomic field cũng thread-safe. Nếu một class có `AtomicInteger count` và một `ArrayList` được nhiều thread sửa, chỉ **các thao tác được atomic class bảo đảm** là atomic; list và những invariant liên quan vẫn cần thiết kế riêng.

Đối với counter tăng với contention cực cao, `LongAdder` đôi khi hữu ích hơn `AtomicLong` cho thống kê. Nhưng `LongAdder.sum()` trong lúc các thread cập nhật **không phải snapshot atomic**, nên không thay thế tùy tiện khi cần một con số tuyến tính hóa tại một thời điểm.

## 3. API quan trọng: không chỉ `get()` và `set()`

Các method thường gặp:

| API | Ý nghĩa | Lưu ý |
| --- | --- | --- |
| `get()` | Đọc giá trị bằng semantics volatile | Giá trị có thể thay đổi ngay sau khi trả về |
| `set(newValue)` | Ghi giá trị bằng semantics volatile | Một lần ghi atomic, không phải phép kiểm tra-ghi có điều kiện |
| `getAndSet(newValue)` | Thay giá trị atomic, trả **giá trị cũ** | Hữu ích để lấy rồi reset counter hoặc claim một cờ |
| `compareAndSet(expected, update)` | Nếu giá trị hiện tại bằng expected, atomically đổi sang update | Trả `true` khi update thành công; `false` khi expected không còn khớp |
| `compareAndExchange(expected, update)` | Trao đổi có điều kiện, trả giá trị đã quan sát tại thời điểm thao tác | Có từ Java 9; giá trị trả về giúp biết trạng thái thực |
| `lazySet(newValue)` | Ghi với release semantics (weak hơn volatile `set` ở một số chiều) | Chỉ dùng nếu hiểu giao thức publication và ordering cần thiết |

Riêng `AtomicInteger` và `AtomicLong` có:

| API | Kết quả |
| --- | --- |
| `getAndIncrement()` | Tăng 1, trả giá trị **trước** khi tăng |
| `incrementAndGet()` | Tăng 1, trả giá trị **sau** khi tăng |
| `getAndDecrement()` và `decrementAndGet()` | Giảm 1; khác giá trị cũ/mới |
| `getAndAdd(delta)` | Cộng delta, trả giá trị cũ |
| `addAndGet(delta)` | Cộng delta, trả giá trị mới |
| `getAndUpdate(fn)` và `updateAndGet(fn)` | Tính giá trị mới bằng hàm một ngôi; có thể chạy lại hàm do tranh chấp |
| `getAndAccumulate(x, fn)` và `accumulateAndGet(x, fn)` | Tính giá trị mới bằng hàm hai ngôi; có thể chạy lại hàm khi CAS thất bại |

`AtomicBoolean` không có phép tăng/giảm; bộ ba quan trọng của nó là `get`, `set` và `compareAndSet`, cùng `getAndSet`.

**Quy ước return value là một phần quan trọng của contract.** Một lỗi thường gặp là dùng `getAndIncrement()` rồi nghĩ nó trả số mới, hoặc dùng `incrementAndGet()` khi muốn phát ID bằng giá trị trước increment.

## 4. CAS: Compare-And-Set hoạt động thế nào?

CAS biểu diễn một phép cập nhật có điều kiện:

```text
current = giá trị của atomic variable tại điểm thực hiện

nếu current == expected:
    đặt current = update
    trả về true
ngược lại:
    giữ nguyên current
    trả về false
```

Đây là **mô hình ý nghĩa**, không phải pseudo-code được triển khai bằng một lượt đọc ordinary rồi ghi ordinary. Sức mạnh của CAS là bước kiểm tra và thay đổi được bảo đảm **atomic** theo hợp đồng của operation.

Ví dụ:

```java
AtomicInteger state = new AtomicInteger(10);

boolean first = state.compareAndSet(10, 20);   // true
boolean second = state.compareAndSet(10, 30);  // false
int now = state.get();                         // 20
```

Đây là ví dụ minh họa với biến đã import `AtomicInteger`, không phải class có `main`.

Khi hai thread cùng CAS từ `0` sang hai giá trị khác nhau, **chỉ một** có thể thành công trên cùng trạng thái `0` nếu không có thao tác khác đưa trạng thái trở lại `0`. Thread thua phải xử lý kết quả `false` — không được giả định thao tác đã thực hiện.

### CAS và linearizability

Mỗi `compareAndSet` có thể được coi như xảy ra tại một điểm quan sát giữa lúc gọi và lúc trả về; các lần cập nhật thành công cho **một atomic variable** có thứ tự phù hợp. Nhưng **toàn bộ nhiều lời gọi CAS/get/set**, hoặc cập nhật atomic variable cộng với thao tác I/O ngoài đó, không tự biến thành một transaction tuyến tính hóa.

CAS cũng không tự cam kết fairness: một thread có thể thua nhiều lần trong cạnh tranh cao. Một thuật toán lặp CAS thường tránh monitor lock cho cập nhật nhỏ nhưng vẫn tiêu tốn CPU khi retry nhiều lần.

## 5. Ví dụ Java 17+: ba lớp trong chương trình đa luồng

Lưu thành `AtomicBasicsDemo.java`:

```java
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AtomicBasicsDemo {
    public static void main(String[] args)
            throws InterruptedException {
        AtomicInteger processed = new AtomicInteger();
        AtomicLong total = new AtomicLong();
        AtomicBoolean claimed = new AtomicBoolean(false);

        Runnable work = () -> {
            for (int i = 0; i < 10_000; i++) {
                processed.incrementAndGet();
                total.addAndGet(2L);
            }
        };

        Thread first = new Thread(work);
        Thread second = new Thread(work);

        first.start();
        second.start();
        first.join();
        second.join();

        boolean firstClaim = claimed.compareAndSet(false, true);
        boolean secondClaim = claimed.compareAndSet(false, true);

        System.out.println("Processed: " + processed.get());
        System.out.println("Total: " + total.get());
        System.out.println("First claim: " + firstClaim);
        System.out.println("Second claim: " + secondClaim);
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac AtomicBasicsDemo.java
java AtomicBasicsDemo
```

Stdout chính xác:

```text
Processed: 20000
Total: 40000
First claim: true
Second claim: false
```

Giải thích:

1. Hai worker cùng giữ reference tới **cùng** `processed` và `total`.
2. `incrementAndGet` và `addAndGet` là những operation atomic độc lập, nên mỗi worker hoàn tất 10.000 lần cập nhật trên mỗi biến, không có lost update.
3. `Thread.join()` đợi **cả hai** worker xong trước khi main in. Do đó output không phụ thuộc vào việc worker nào chạy nhanh hơn.
4. `compareAndSet(false, true)` lần đầu thành công; lần thứ hai thất bại do cờ đã là `true`. Hai lần CAS này được main gọi tuần tự chỉ để minh họa giá trị trả về.
5. **Không** suy ra từ chương trình rằng `processed` và `total` được thay đổi như một cặp atomic. Một reader chạy ngay giữa hai lần update trong worker có thể nhìn thấy chúng ở hai mốc khác nhau.

Nếu nghiệp vụ đòi hỏi "mọi lúc `total == 2 * processed`" đối với reader concurrent, cần một giao thức khác để bảo vệ **invariant giữa hai biến**, ví dụ khóa hoặc một immutable state object được thay nguyên tử qua `AtomicReference`.

## 6. `AtomicInteger`: bộ đếm, trạng thái số và hạn mức

### Đếm số operation hoàn thành

`AtomicInteger` rất phù hợp với:

- Số lượng tác vụ đã hoàn tất.
- Số phần tử được xử lý, khi một event được tính đúng một lần.
- Đếm số waiter hoặc số yêu cầu đang hoạt động với giao thức tăng/giảm rõ.
- Trạng thái số dạng `NEW=0`, `RUNNING=1`, `DONE=2` khi cần transition atomic theo điều kiện.

Lưu ý nghĩa của counter phải được xác định: **số tác vụ bắt đầu, số tác vụ thành công và số tác vụ đang chạy là ba metric khác nhau**. Một atomic counter có thể đếm chính xác từng lần gọi tăng nhưng không tự biết lần tăng có đại diện đúng nghiệp vụ hay không.

### Không viết check-then-act bằng hai operation

Anti-pattern:

```java
if (inFlight.get() < limit) {
    inFlight.incrementAndGet();
}
```

Nếu hai thread cùng thấy giá trị ngay dưới `limit`, cả hai có thể tăng, khiến hạn mức bị vượt. `get` và `incrementAndGet` **riêng lẻ** an toàn; **chuỗi hai lời gọi** không atomic.

Giải pháp là gói phép kiểm tra và thay đổi thành **một operation logic CAS loop** hoặc sử dụng `Semaphore` nếu mục đích thực sự là hạn chế permit và cần phối hợp chờ.

## 7. Ví dụ Java 17+: tăng với giới hạn bằng CAS loop

Lưu thành `BoundedAtomicCounterDemo.java`:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class BoundedAtomicCounterDemo {

    static final class BoundedCounter {
        private final int maximum;
        private final AtomicInteger value = new AtomicInteger();

        BoundedCounter(int maximum) {
            if (maximum < 0) {
                throw new IllegalArgumentException("negative maximum");
            }
            this.maximum = maximum;
        }

        boolean tryIncrement() {
            while (true) {
                int current = value.get();
                if (current >= maximum) {
                    return false;
                }
                if (value.compareAndSet(current, current + 1)) {
                    return true;
                }
                // Thua tranh chấp: đọc lại và kiểm tra giới hạn.
            }
        }

        int get() {
            return value.get();
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        BoundedCounter counter = new BoundedCounter(1_750);
        AtomicInteger successes = new AtomicInteger();
        Thread[] workers = new Thread[8];

        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int attempt = 0; attempt < 1_000; attempt++) {
                    if (counter.tryIncrement()) {
                        successes.incrementAndGet();
                    }
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("Accepted: " + successes.get());
        System.out.println("Counter: " + counter.get());
    }
}
```

```bash
javac BoundedAtomicCounterDemo.java
java BoundedAtomicCounterDemo
```

Stdout chính xác:

```text
Accepted: 1750
Counter: 1750
```

Phân tích từng bước:

- Mỗi worker cố tăng 1.000 lần; tổng là 8.000 lần thử nhưng counter tối đa 1.750.
- `current = value.get()` chỉ là ảnh chụp tạm. Thread khác có thể thay đổi value ngay sau đó.
- `compareAndSet(current, current + 1)` bảo đảm rằng chỉ thread còn thấy **đúng current** mới thay đổi được sang `current + 1`.
- Nếu CAS thất bại, vòng lặp phải **đọc lại** và kiểm tra giới hạn. Không dùng một giá trị `current` cũ để retry vô hạn.
- Khi giá trị đạt 1.750, mọi lần thử mới trả `false`. Vì đây là counter chỉ tăng và không có phép giảm, không có đường quay lại trạng thái cũ.
- `successes` đếm số lời gọi `tryIncrement` trả true; output được in sau `join()` nên tổng chính xác.

Giới hạn của giải pháp: nó chỉ quản lý **một biến**; không có cơ chế chờ khi hết chỗ. Nếu cần cấp/release slot cho tài nguyên, quản lý fairness, timeout, interruption và chờ permit, `Semaphore` thường diễn đạt đúng hơn. Vòng CAS có thể retry nhiều lần dưới contention mạnh.

### Vì sao `current + 1` không overflow trong ví dụ?

Constructor chỉ chấp nhận `maximum >= 0`. CAS chỉ tính `current + 1` khi `current < maximum`; vì `maximum` là `int`, `current` không thể lớn hơn `Integer.MAX_VALUE - 1` tại nhánh cập nhật. Do đó ví dụ không bị overflow ở bước này.

Đối với counter không có giới hạn, `AtomicInteger.incrementAndGet()` **không tự kiểm tra overflow**; nếu tăng quá `Integer.MAX_VALUE`, giá trị sẽ wrap sang `Integer.MIN_VALUE` theo số nguyên Java. Nếu hệ thống cần tính đơn điệu tuyệt đối hoặc ID không được tái sử dụng, phải quản lý ngưỡng và vòng đời.

## 8. `AtomicLong`: sequence number và số liệu lớn

`AtomicLong` có cùng nhóm phép toán số học như `AtomicInteger` nhưng lưu một `long` 64-bit, hữu ích khi số lượng thao tác có thể vượt khoảng `int`.

Ví dụ phát sequence number:

```java
AtomicLong nextId = new AtomicLong(1_000L);
long first = nextId.getAndIncrement();   // 1000
long second = nextId.getAndIncrement();  // 1001
```

Đây là đoạn minh họa. `getAndIncrement` trả giá trị cũ; nếu cần trả giá trị sau khi tăng, dùng `incrementAndGet`.

### Phạm vi duy nhất của ID

Một `AtomicLong` đảm bảo mỗi update thành công trên **chính instance đó** có thứ tự atomic. Nhưng không tự bảo đảm:

- ID là duy nhất giữa **hai tiến trình JVM khác nhau**;
- ID tồn tại bền vững sau restart;
- ID không bị trùng nếu counter overflow hoặc bị reset;
- các bản ghi được commit theo thứ tự số ID;
- request lấy ID thấp hơn hoàn tất công việc sớm hơn.

Đây là sự khác nhau giữa **atomic sequence in-process** và **distributed/durable identity**. Trên một JVM, nếu không reset và chưa overflow, `getAndIncrement` có thể dùng phát số tăng dần cho nhiều caller đồng thời. Nhưng không phải công cụ giải quyết mọi yêu cầu ID trong hệ thống phân tán.

### Ví dụ Java 17+ xác nhận ID không trùng

Lưu thành `AtomicSequenceDemo.java`:

```java
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public class AtomicSequenceDemo {
    public static void main(String[] args)
            throws InterruptedException {
        AtomicLong nextId = new AtomicLong(1_000);
        long[] issued = new long[400];
        Thread[] workers = new Thread[4];

        for (int worker = 0; worker < workers.length; worker++) {
            final int index = worker;
            workers[worker] = new Thread(() -> {
                int offset = index * 100;
                for (int j = 0; j < 100; j++) {
                    issued[offset + j] = nextId.getAndIncrement();
                }
            });
            workers[worker].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        Arrays.sort(issued);
        boolean contiguous = true;
        for (int i = 0; i < issued.length; i++) {
            if (issued[i] != 1_000L + i) {
                contiguous = false;
                break;
            }
        }

        System.out.println("Issued: " + issued.length);
        System.out.println("Range: " + issued[0] + ".."
                + issued[issued.length - 1]);
        System.out.println("Contiguous: " + contiguous);
    }
}
```

```bash
javac AtomicSequenceDemo.java
java AtomicSequenceDemo
```

Stdout chính xác:

```text
Issued: 400
Range: 1000..1399
Contiguous: true
```

Mỗi worker ghi vào **một vùng chỉ số riêng** của array nên không có hai writer tranh cùng phần tử. `join` công bố các ghi của worker cho main trước khi sort và đọc. Các giá trị phát ra có thể thuộc worker khác nhau theo thứ tự không cố định, nhưng tập giá trị là từ 1000 đến 1399 và không trùng. Sắp xếp trước khi in khiến kết quả xác định.

## 9. `AtomicBoolean`: cờ đơn và quyền chuyển trạng thái

`AtomicBoolean` phù hợp để quản lý một trạng thái nhị phân mà nhiều thread có thể đọc/đổi:

- Cờ yêu cầu dừng công việc.
- Cờ đã bắt đầu hoặc đã gửi tín hiệu một lần.
- Cờ "chỉ cho một caller nhận quyền".
- Cờ bật/tắt một chức năng khi ngữ nghĩa không cần thêm trạng thái.

Khác biệt giữa `set(true)` và `compareAndSet(false, true)` rất quan trọng:

```java
AtomicBoolean started = new AtomicBoolean(false);

started.set(true); // Ghi true; mọi caller đều có thể gọi thành công.

boolean winner = started.compareAndSet(false, true);
// Chỉ trả true nếu caller thắng chuyển false -> true.
```

Một cờ `true` có thể có nghĩa "đã có ai đó claim quyền" chứ chưa có nghĩa "công việc đã thành công". Nếu cần phân biệt `NEW`, `RUNNING`, `SUCCEEDED` và `FAILED`, cờ boolean thường **không đủ biểu diễn trạng thái**; hãy dùng state machine, future hoặc enum/state atomic phù hợp.

### Ví dụ Java 17+: chỉ một thread claim quyền bắt đầu

Lưu thành `AtomicBooleanOnceDemo.java`:

```java
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicBooleanOnceDemo {
    public static void main(String[] args)
            throws InterruptedException {
        AtomicBoolean claimed = new AtomicBoolean(false);
        AtomicInteger winners = new AtomicInteger();
        Thread[] candidates = new Thread[6];

        for (int i = 0; i < candidates.length; i++) {
            candidates[i] = new Thread(() -> {
                if (claimed.compareAndSet(false, true)) {
                    winners.incrementAndGet();
                }
            });
            candidates[i].start();
        }

        for (Thread candidate : candidates) {
            candidate.join();
        }

        System.out.println("Winners: " + winners.get());
        System.out.println("Claimed: " + claimed.get());
    }
}
```

```bash
javac AtomicBooleanOnceDemo.java
java AtomicBooleanOnceDemo
```

Stdout chính xác:

```text
Winners: 1
Claimed: true
```

Dù sáu thread chạy theo lịch nào, chỉ một CAS từ `false` sang `true` có thể thành công khi không có thread nào reset cờ. Vì mỗi winner tăng counter đúng một lần và main đợi hết các thread, kết quả là xác định.

**Đừng tự động áp dụng lại cách này để đảm bảo exactly-once side effect.** Ví dụ winner cập nhật `claimed = true` rồi gọi một external service. Nếu service lỗi, cờ vẫn true; những caller khác sẽ không thử nữa. Muốn "đã hoàn thành thành công đúng một lần" phải có **giao thức xử lý lỗi**, idempotency/retry và trạng thái biểu diễn completion, không chỉ cờ claim.

## 10. Cờ dừng và sự khác nhau giữa visibility với wake-up

Một dạng phổ biến:

```java
final class StopSignal {
    private final java.util.concurrent.atomic.AtomicBoolean
            stopped = new java.util.concurrent.atomic.AtomicBoolean();

    void requestStop() {
        stopped.set(true);
    }

    boolean shouldStop() {
        return stopped.get();
    }
}
```

Class minh họa này dùng `AtomicBoolean` để publish cờ. Khi worker chủ động kiểm tra `shouldStop()`, nó có thể quan sát cập nhật nhờ semantics volatile của `get()/set()`.

**Nhưng cờ không đánh thức thread đang block.** Nếu worker đang `BlockingQueue.take()`, `Thread.sleep(...)`, `CountDownLatch.await()` hoặc I/O, riêng `stopped.set(true)` không khiến thread rời lời gọi đó. Tùy API, cần thêm interruption, timeout, sentinel/poison-pill, close signal hoặc cơ chế đóng resource.

Không nên dùng vòng `while (!stopped.get()) { }` chạy mãi trên CPU chỉ để chờ event. Một vòng spin có thể phù hợp với đoạn cực ngắn trong một số thuật toán đặc biệt, nhưng với tác vụ ứng dụng thông thường nên dùng blocking coordination có chính sách hủy.

### Stop flag không thay `Thread.interrupt()`

`AtomicBoolean` và interruption phục vụ hai vai trò khác nhau:

- Flag mang **yêu cầu ở mức trạng thái ứng dụng** do người lập trình định nghĩa.
- `Thread.interrupt()` mang **yêu cầu ngắt** theo hợp đồng của thread/API blocking. Nó không cưỡng bức giết thread; có thao tác ném `InterruptedException`, có thao tác phản hồi khác hoặc không hỗ trợ interruption như kỳ vọng.

Nếu catch `InterruptedException` mà không thể xử lý tại chỗ, cách thông dụng là kết thúc tác vụ và/hoặc khôi phục flag bằng `Thread.currentThread().interrupt()`. Đừng nuốt exception rồi tiếp tục chờ vô hạn.

## 11. `getAndSet`: lấy giá trị cũ, reset hoặc claim

`getAndSet` thực hiện exchange atomic:

```java
AtomicInteger intervalCount = new AtomicInteger(17);

int previous = intervalCount.getAndSet(0); // 17
int current = intervalCount.get();         // 0
```

Đây là đoạn minh họa. Với nhiều writer tiếp tục cập nhật trong lúc reset, `getAndSet(0)` có một điểm exchange atomic. Nhưng nếu phải tính tổng qua **nhiều counter** cùng lúc, nhiều lần `getAndSet` trên các biến khác nhau **không tạo một snapshot toàn cục**.

Với `AtomicBoolean`:

```java
AtomicBoolean pending = new AtomicBoolean(true);
boolean wasPending = pending.getAndSet(false);
```

Caller nhận `true` nếu tại thời điểm exchange cờ là `true`. Các thread khác có thể đổi cờ sau đó. Nếu nghiệp vụ yêu cầu chỉ thread nào nhìn thấy `false` mới có quyền set true, `compareAndSet(false, true)` diễn đạt điều kiện rõ ràng hơn `getAndSet(true)`.

## 12. Các method nhận hàm: `updateAndGet` và `accumulateAndGet`

Một số cập nhật không đơn thuần cộng 1. API hỗ trợ cập nhật qua hàm:

```java
AtomicInteger level = new AtomicInteger(8);

int updated = level.updateAndGet(current ->
        Math.min(10, current + 1)); // 9

int previous = level.getAndAccumulate(4,
        (current, delta) -> Math.min(10, current + delta)); // 9

int now = level.get(); // 10
```

Đây là đoạn minh họa sau khi import `AtomicInteger`.

**Quy tắc cực quan trọng:** hàm được truyền vào `updateAndGet`, `getAndUpdate`, `accumulateAndGet` hoặc `getAndAccumulate` **có thể bị gọi lại nhiều lần** nếu CAS thất bại do tranh chấp. Do đó hàm nên **không có side effect**, xác định theo input, chạy nhanh và không block.

Sai:

```java
counter.updateAndGet(old -> {
    sendEmail();  // Side effect có thể xảy ra nhiều lần khi retry!
    return old + 1;
});
```

Đây là anti-pattern minh họa; `sendEmail()` giả định. Một callback gửi email, ghi file, increment counter bên ngoài hoặc gọi network không được đặt trong hàm update nếu có nguy cơ bị thực thi lại. Hãy tách side effect sang một giao thức riêng sau khi update thành công, đồng thời cân nhắc idempotency, thứ tự và lỗi.

Hàm cũng không nên tự đọc rồi sửa chính atomic variable theo cách làm việc retry khó hiểu; chỉ trả giá trị mới dựa trên tham số hiện tại và điều kiện được kiểm soát.

## 13. Ví dụ Java 17+: update function thuần và bounded value

Lưu thành `AtomicUpdateDemo.java`:

```java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AtomicUpdateDemo {
    public static void main(String[] args) {
        AtomicInteger level = new AtomicInteger(8);

        int updated = level.updateAndGet(
                current -> Math.min(10, current + 1));

        int previous = level.getAndAccumulate(4,
                (current, delta) -> Math.min(10, current + delta));

        AtomicLong bytes = new AtomicLong(100);
        long after = bytes.addAndGet(25L);

        System.out.println("Updated level: " + updated);
        System.out.println("Previous level: " + previous);
        System.out.println("Current level: " + level.get());
        System.out.println("Bytes: " + after);
    }
}
```

```bash
javac AtomicUpdateDemo.java
java AtomicUpdateDemo
```

Stdout chính xác:

```text
Updated level: 9
Previous level: 9
Current level: 10
Bytes: 125
```

Mỗi lambda chỉ tính giá trị từ đối số, không tạo side effect. Do đó việc JDK có thể thử lại hàm vì CAS thất bại không nhân đôi một external action.

Hạn chế: phép `Math.min(10, current + 1)` **không phải giải pháp xử lý overflow tổng quát** khi current có thể gần `Integer.MAX_VALUE`. Trong ví dụ, giá trị được quản lý thuộc vùng nhỏ (8 đến 10) nên an toàn. Nếu dùng cho input tùy ý, phải kiểm tra giới hạn trước khi cộng hoặc dùng phép kiểm tra/overflow-safe phù hợp.

## 14. Memory semantics: atomics cung cấp những gì theo JMM?

`AtomicInteger`, `AtomicLong` và `AtomicBoolean` không chỉ cung cấp arithmetic/CAS. Các operation như `get()` và `set()` có semantics bộ nhớ tương đương đọc/ghi **volatile** của biến giá trị; các cập nhật như `compareAndSet`, `getAndSet` và arithmetic atomic có những bảo đảm phù hợp với read-modify-write volatile.

Hiểu mẫu publication đơn giản:

```java
int payload; // Ordinary data, ví dụ trong một holder.
AtomicBoolean ready = new AtomicBoolean(false);

// Writer:
payload = 42;
ready.set(true);

// Reader:
if (ready.get()) {
    use(payload); // Đọc payload đã được công bố đúng giao thức.
}
```

Đây là đoạn minh họa logic, `payload` và `ready` là state dùng chung và `use` là phương thức giả định. Nếu **writer chỉ ghi payload trước `ready.set(true)`**, còn reader chỉ đọc payload **sau khi `ready.get()` đã quan sát true** từ publication đó, và không có writer khác sửa payload xung đột, thì quan hệ happens-before từ volatile write/read đảm bảo reader thấy payload phù hợp.

Điều này đến từ tính bắc cầu:

```text
writer: write payload = 42
                  |
                  v
           ready.set(true)       volatile write
                  |
             synchronizes-with
                  |
                  v
reader:     ready.get() == true  volatile read
                  |
                  v
             read payload
```

Không phải dữ liệu ordinary nào cũng tự trở nên safe nếu có một atomic variable ở gần đó. Nếu có **nhiều lượt publication**, reader giữ tham chiếu mutable lâu, hoặc các writer khác sửa payload, cần xác định giao thức chi tiết hơn. Với nhiều field cần quan sát đồng thời, cân nhắc immutable snapshot và công bố cả tham chiếu qua `AtomicReference` hoặc khóa.

### `lazySet` và các access mode nâng cao

`lazySet` có release semantics; nó có thể hữu ích trong giao thức one-way publication, nhưng **không hứa reader bất kỳ sẽ thấy trạng thái trong một thời hạn cụ thể**. Java 9+ còn có các thao tác plain, opaque, acquire, release và compare-and-exchange variants. Chúng phục vụ lập trình concurrency chuyên sâu.

Đừng dùng một access mode yếu hơn chỉ vì nghĩ "nhanh hơn" mà chưa phân tích đầy đủ JMM. Với ứng dụng thông thường, `get()`, `set()` và `compareAndSet()` rõ ràng, ít rủi ro hơn. `AtomicBoolean` không phải primitive chờ hay đánh thức; `get()` quan sát giá trị chứ không đợi một sự kiện xảy ra.

## 15. Overflow, khoảng giá trị và kiểu dữ liệu

### `AtomicInteger` và `AtomicLong` không tự báo overflow

`AtomicInteger.incrementAndGet()` thực hiện phép tăng trên kiểu `int`; khi vượt `Integer.MAX_VALUE`, kết quả wrap theo số nguyên 32-bit Java. `AtomicLong` có vấn đề tương tự sau `Long.MAX_VALUE`.

Nếu ID hoặc version **không được phép quay lại**, phải quản lý overflow:

- Kiểm tra hạn mức trước khi tăng trong một phép cập nhật atomic có điều kiện.
- Dùng `long` khi khoảng `int` không đủ, nhưng vẫn phải xác định vòng đời và ngưỡng.
- Không sử dụng counter JVM-local làm global unique ID nếu còn nhiều instance JVM hoặc restart.
- Nếu cần phép cộng overflow-safe, dùng kiểm tra phù hợp (như `Math.addExact` trong giao thức update rõ ràng) và xác định cách xử lý exception.

### Một `long` ordinary và một `AtomicLong` không giống nhau

Một field ordinary `long` không tự làm `value++` atomic; ngoài ra JMM còn phân biệt các bảo đảm cho non-volatile `long`/`double` ở mức đọc/ghi 64-bit. Trong thực tế trên nhiều JVM hiện đại, một số thao tác 64-bit thường không bị xé, nhưng **không được dựa vào chi tiết triển khai thay cho hợp đồng Java**. `AtomicLong` cung cấp atomic operations và memory semantics rõ ràng, dùng được giữa các nền tảng tuân thủ JMM.

**Kiểu lớn hơn không có nghĩa contention thấp hơn.** `AtomicLong` phù hợp vì khoảng giá trị cần thiết, không mặc nhiên nhanh hơn `AtomicInteger`.

## 16. ABA problem: CAS thành công không có nghĩa state chưa từng thay đổi

CAS chỉ so giá trị hiện tại với giá trị mong đợi. Giả sử:

```text
Thread A đọc state = A
Thread B đổi A -> B -> A
Thread A compareAndSet(A, C) thành công
```

Tại lúc CAS của A, giá trị đúng là A, nên CAS hợp lệ. Nhưng A có thể **nhầm rằng không có thay đổi nào** trong lúc nó xử lý. Đây là **ABA problem**.

Với counter chỉ tăng không reset trong khoảng không overflow, ABA thường ít đáng lo. Nhưng với state có thể quay lại giá trị cũ, ví dụ cờ false/true/false, ID tái sử dụng hoặc thuật toán lock-free trên cấu trúc dữ liệu, ABA có thể phá giả định nghiệp vụ.

Cách giảm rủi ro tùy bài toán:

- Dùng một **version/generation** tăng theo mỗi thay đổi quan trọng.
- Với tham chiếu object, xem `AtomicStampedReference` nếu cần so cả reference và stamp.
- Gói state và version vào một immutable object rồi cập nhật thông qua `AtomicReference` với CAS trên reference.
- Dùng monitor/lock nếu atomicity của nhiều field hoặc thuật toán lifecycle phức tạp hơn lợi ích của CAS.

**Một `AtomicBoolean` không biết lịch sử.** Nếu cờ từ `false` thành `true` rồi trở lại `false`, CAS kỳ vọng false vẫn có thể thành công — không có bất cứ cam kết "cờ chưa từng đổi" nào trong `compareAndSet`.

## 17. Thread safety của một biến khác với invariant nhiều biến

Giả sử cần bảo đảm `available + reserved == capacity`. Dù `available` và `reserved` đều là `AtomicInteger`, chuỗi:

```java
available.decrementAndGet();
reserved.incrementAndGet();
```

vẫn gồm **hai operation atomic riêng biệt**. Một reader nằm giữa hai bước có thể thấy tổng tạm thời khác `capacity`. Nếu invariant phải luôn đúng với các reader concurrent, cần giải pháp ở ranh giới transaction:

- Giữ **một monitor hoặc lock chung** quanh cả cập nhật và mọi điểm quan sát liên quan.
- Gói các field thành **một immutable state** rồi đổi toàn bộ reference bằng một CAS được thiết kế đúng.
- Chuyển mutable state cho **một owner** duy nhất và giao tiếp qua queue/message.
- Nếu yêu cầu chỉ là quan sát sau khi mọi worker kết thúc, dùng proper completion primitive rồi đọc kết quả tại điểm đó.

Tương tự, một `AtomicBoolean initialized` cộng với một mutable `List` cần giao thức safe publication. Cờ true không khiến các lần sửa list concurrent về sau trở thành an toàn.

Không nên kết luận **"class thread-safe" chỉ vì field đều có tiền tố Atomic**. Thread safety phụ thuộc invariant, alias, callbacks, collection values, tài nguyên bên ngoài và các operation được người dùng ghép lại.

## 18. `AtomicBoolean` và state machine: "đã claim" khác "đã hoàn thành"

Một service có thể muốn chỉ cho một thread làm một công việc, các thread khác chờ kết quả. Một cờ `AtomicBoolean busy` hỗ trợ claim:

```java
if (busy.compareAndSet(false, true)) {
    try {
        doWork();
    } finally {
        busy.set(false);
    }
}
```

Đây là **mẫu minh họa**, `busy` và `doWork` giả định. Một số rủi ro:

- Cờ reset về false cho phép thread khác chạy lại, nên đây là **exclusive-in-progress**, không phải once-ever.
- `doWork` ném exception rồi reset về false, có thể đã tạo **partial side effect**; retry phải có policy.
- Các thread không claim được chỉ bỏ qua công việc; nếu cần chờ, dùng `Semaphore`, `Lock`, queue hoặc future phù hợp.
- Nếu muốn caller khác nhận **cùng kết quả** của một lần tính toán, `CompletableFuture<T>` hoặc future được công bố an toàn thường diễn đạt completion tốt hơn cờ.
- Nếu doWork bị kẹt trong I/O, flag không tự tạo timeout/cancellation.

Nếu có trạng thái `NEW`, `RUNNING`, `SUCCEEDED`, `FAILED`, nên dùng state machine rõ và xác định transition hợp lệ. `AtomicInteger` có thể giữ enum ordinal hoặc mã trạng thái, nhưng phải quản lý mapping và kết quả; dùng enum qua `AtomicReference<State>` thường dễ đọc hơn khi phạm vi bài toán cần nhiều trạng thái.

## 19. `AtomicLong` so với `LongAdder`: khi nào ưu tiên throughput?

Một `AtomicLong` quản lý **một giá trị**; nhiều thread cập nhật một biến có thể tạo contention tại cùng một điểm. `LongAdder` phân tán hoạt động cập nhật nội bộ để cải thiện throughput trong một số workload có contention cao.

| Tiêu chí | `AtomicLong` | `LongAdder` |
| --- | --- | --- |
| Đọc một giá trị tại một operation | `get()` đọc giá trị atomic | `sum()` tổng hợp nhiều thành phần |
| Tăng 1 | `incrementAndGet()` hoặc `getAndIncrement()` | `increment()` |
| Cần lấy giá trị mới ngay sau lần tăng | Có, qua `incrementAndGet()` | Không cung cấp semantics tương ứng trên tổng toàn bộ adder |
| Snapshot khi writer vẫn cập nhật | `get()` là một atomic read | `sum()` không phải atomic snapshot toàn cục |
| Contention cao, chỉ đo thống kê | Có thể thành điểm tranh chấp | Thường là lựa chọn nên benchmark |

Nếu đang đếm "tổng request đã từng xử lý" chỉ để hiển thị thống kê xấp xỉ trong lúc cập nhật liên tục, `LongAdder` có thể phù hợp. Nếu cần CAS để thực thi hạn mức, ID hoặc chuyển trạng thái có điều kiện, **`AtomicLong`** diễn đạt contract chính xác hơn.

Đừng đổi `AtomicLong` sang `LongAdder` chỉ dựa trên benchmark mà bỏ qua khác biệt về **ngữ nghĩa đọc**.

## 20. CAS loop: retry, starvation và side effect

Mẫu thông dụng:

```java
while (true) {
    int oldValue = atomic.get();
    int newValue = transform(oldValue); // Hàm nên thuần.
    if (atomic.compareAndSet(oldValue, newValue)) {
        break;
    }
}
```

Đây là mẫu minh họa; `atomic` và `transform` chưa khai báo. CAS thất bại khi trạng thái đã đổi từ giá trị mong đợi, nên ta thử lại dựa trên giá trị mới.

Các hệ quả:

- Hàm `transform` có thể chạy nhiều lần, nên **không đặt I/O, callback hoặc external side effect** trong quá trình tính giá trị mới.
- Một thread có thể thua liên tục, đặc biệt nếu contention rất cao; **không có fairness tự động**.
- Retry là việc tiêu thụ CPU; nên benchmark trước khi kết luận CAS loop nhanh hơn `synchronized` trong workload thực tế.
- Với dữ liệu nhiều field, việc ghép state thành một atomic reference hoặc dùng lock có thể đơn giản, dễ review hơn.
- CAS failure không phải exception: caller phải kiểm tra giá trị `false` và thực hiện policy tương ứng (retry, bỏ cuộc hoặc báo lỗi).
- Nếu algorithm có ABA, phải thêm version hoặc thiết kế loại bỏ đường quay lại state cũ không hợp lệ.

`compareAndSet` bản strong không có semantics "được phép thất bại giả" như một số biến thể `weakCompareAndSet...`. Các biến thể weak CAS và các memory access mode yếu hơn phù hợp cho thuật toán nâng cao, không nên tự thay vào code thông thường nếu chưa chứng minh được tính đúng.

## 21. Có cần `synchronized` khi đã dùng Atomic?

**Có thể có.** Atomic variables loại bỏ nhu cầu khóa cho **một số operation đơn biến**, không cấm dùng khóa khi nghiệp vụ cần.

Chọn công cụ theo invariant:

| Bài toán | Công cụ thường phù hợp |
| --- | --- |
| Một bộ đếm nhiều writer tăng/giảm độc lập | `AtomicInteger` hoặc `AtomicLong` |
| Một flag duy nhất dùng để claim quyền | `AtomicBoolean.compareAndSet` |
| Giới hạn một counter chỉ tăng đến max | CAS loop trên `AtomicInteger` |
| Hạn chế nhiều task giữ permit và phải chờ/cancel | `Semaphore` hoặc queue bounded |
| Snapshot nhất quán của hai field thay đổi cùng nhau | `synchronized`/`Lock` hoặc một immutable state atomic reference |
| Điều phối completion giữa worker và caller | `Future`, `CountDownLatch`, `Thread.join()` |
| Map gồm nhiều entry concurrent | `ConcurrentHashMap` với operation atomic theo key, không phải một atomic field cho cả map |
| Counter thống kê contention cao | Cân nhắc `LongAdder` và semantics của `sum()` |

`synchronized` còn cung cấp **critical section**: thread giữ một monitor trong lúc kiểm tra và cập nhật invariant nhiều bước. Một `AtomicInteger` chỉ bảo vệ trường atomic đó, không khóa hoạt động trên collection hay field khác.

Thường nên **đơn giản hóa ownership và trạng thái** trước khi tìm cách thay mọi lock bằng CAS phức tạp.

## 22. Các lỗi phổ biến với ba loại Atomic

1. **"`volatile count++` đã atomic."** Sai; phải dùng operation atomic hoặc lock.
2. **"Dùng `AtomicInteger` thì mọi lời gọi `get` rồi `set` là atomic."** Sai; hai lời gọi có khoảng xen kẽ.
3. **"`compareAndSet` trả false nghĩa là JVM lỗi."** Sai; có thread khác cập nhật hoặc expected không khớp.
4. **"Atomic biến class thành thread-safe."** Sai nếu class còn invariant nhiều field, mutable collection hoặc callback không được bảo vệ.
5. **"`AtomicBoolean` true nghĩa công việc đã thành công."** Chỉ đúng nếu chính giao thức định nghĩa và công bố completion như vậy; CAS claim là bước khác completion.
6. **"Cờ dừng đánh thức thread đang block."** Sai; cần signaling/interruption/timeout/close phù hợp.
7. **"`AtomicLong` tạo ID duy nhất toàn hệ thống phân tán."** Sai; atomicity theo instance/JVM không cung cấp distributed uniqueness hoặc durability.
8. **"Atomic counters không overflow."** Sai; phép số học kiểu `int`/`long` wrap nếu vượt phạm vi.
9. **"Lambda của `updateAndGet` chỉ chạy đúng một lần."** Sai; có thể bị gọi lại do CAS retry.
10. **"CAS thành công chứng tỏ giá trị chưa từng thay đổi."** Sai với ABA khi state quay lại giá trị mong đợi.
11. **"`lazySet` là `set` giống hệt mọi mặt."** Không; access mode và ordering có điểm khác, cần đọc contract.
12. **"Đọc hai Atomic liên tiếp tạo snapshot nhất quán."** Sai; giữa hai lần đọc, các writer vẫn có thể tiến hành.
13. **"Atomic operation bảo đảm mọi thread có tiến độ công bằng."** Không; scheduling và retry có thể gây starvation.
14. **"Không cần kiểm tra kết quả `compareAndSet`."** Sai; nếu bỏ qua `false`, cập nhật dự định có thể chưa được thực hiện.
15. **"Dùng `getAndIncrement` là lấy giá trị sau tăng."** Sai; nó trả giá trị cũ. Dùng `incrementAndGet` để lấy giá trị mới.

## 23. Checklist thiết kế và code review

Trước khi chọn một trong ba atomic class, hãy trả lời:

1. **State có thật sự là một giá trị độc lập không?** Nếu invariant gồm nhiều field, đừng mặc định một atomic đủ.
2. **Ai đọc và ai ghi?** Có bao nhiêu writer, reader, và instance atomic có thực sự được chia sẻ đúng không?
3. **Operation cần atomic là gì?** Tăng một lần, check-and-update, exchange hay giao dịch nhiều bước?
4. **API nào trả giá trị cũ, API nào trả giá trị mới?** Đừng nhầm `getAnd...` với `...AndGet`.
5. **Memory publication được tạo bằng cách nào?** Có đường happens-before rõ ràng từ writer đến reader không?
6. **Có thể overflow/reset không?** Nếu giá trị dùng làm ID/version, xác định chính sách giới hạn.
7. **Có ABA không?** State có thể quay lại expected trước CAS không, và logic có cần nhớ lịch sử không?
8. **Hàm update có side effect không?** Mọi lambda gọi từ `updateAndGet`/`accumulateAndGet` nên chịu được retry.
9. **Cờ dừng có đủ đánh thức worker không?** Nếu worker block, cần interrupt/timeout/protocol riêng.
10. **Có cần giới hạn concurrency bằng permit hoặc queue không?** Nếu có, cân nhắc `Semaphore`/`BlockingQueue` thay vì counter tự chế.
11. **Có yếu tố performance đáng đo không?** Benchmark contention thực tế; đừng đổi code đúng, rõ ràng lấy tối ưu đoán mò.
12. **Completion và failure được truyền về thế nào?** Một flag claim không thay future hoặc state machine rõ ràng.

## 24. Câu hỏi tự kiểm tra

**1. Vì sao `AtomicInteger` xử lý lost update tốt hơn `volatile int` với `count++`?**

`AtomicInteger.incrementAndGet` là operation read-modify-write atomic. `volatile count++` chỉ bảo đảm semantics của lần đọc/ghi riêng, không ghép thành một operation.

**2. Hai thread gọi `AtomicBoolean.compareAndSet(false, true)`. Có thể cả hai trả `true` không nếu cờ ban đầu false và không ai reset?**

Không. Khi CAS đầu tiên thành công, trạng thái thành true; CAS kỳ vọng false còn lại sẽ thất bại. Nếu có thread reset true về false, một CAS tiếp theo có thể thành công lại.

**3. Gọi `AtomicLong.getAndIncrement()` từ 100 thread có đảm bảo ID unique trên nhiều JVM không?**

Không. Nó bảo đảm những operation atomic trên **cùng một instance**, không tự điều phối nhiều tiến trình, restart hay overflow.

**4. `getAndSet(0)` có tạo một snapshot atomic của nhiều `AtomicInteger` không?**

Không. Mỗi exchange chỉ atomic trên một biến riêng; không có transaction toàn cục giữa các biến.

**5. Với logic "chỉ tăng khi dưới max", vì sao `get() < max` rồi `incrementAndGet()` không đủ?**

Vì thread khác có thể tăng giữa hai lời gọi. Dùng CAS loop hoặc operation khác bao cả kiểm tra và thay đổi.

**6. Có nên gửi email trong lambda của `updateAndGet` không?**

Không. Lambda có thể bị chạy lại khi CAS tranh chấp, khiến email được gửi nhiều lần dù chỉ một update cuối cùng thành công.

**7. `AtomicBoolean stopped` có thay cho interrupt được không?**

Không. Cờ không tự đánh thức worker đang block; interruption có hợp đồng khác và một số operation đáp ứng bằng `InterruptedException`.

**8. CAS có thể bỏ sót việc state đã đổi A → B → A không?**

Có. Đây là ABA problem: CAS chỉ so giá trị hiện tại với expected, không ghi nhận lịch sử. Nếu lịch sử quan trọng, dùng version/stamp hoặc giao thức phù hợp.

**9. Khi nào `LongAdder` có thể tốt hơn `AtomicLong`?**

Khi workload chủ yếu là thống kê nhiều writer có contention cao, và chấp nhận `sum()` khi cập nhật concurrent không phải snapshot atomic. Nếu cần CAS/hạn mức chính xác tại một operation, dùng `AtomicLong`.

**10. Một class có hai `AtomicInteger` thì có tự bảo đảm tổng hai biến luôn bằng N không?**

Không. Hai operation trên hai atomic variable là độc lập. Muốn reader thấy invariant tổng thể, cần transaction dưới lock hoặc state được thay như một đơn vị.

## 25. Tổng kết

- **`AtomicInteger`**: counter hoặc trạng thái kiểu `int` với phép đọc, ghi, arithmetic và CAS atomic.
- **`AtomicLong`**: cùng mô hình với `long`, phù hợp counter và sequence trong một phạm vi sở hữu xác định; không bảo đảm global uniqueness/durability hay tránh overflow.
- **`AtomicBoolean`**: flag và chuyển trạng thái nhị phân bằng CAS; claim quyền không đồng nghĩa hoàn thành công việc.
- **`compareAndSet`**: atomic check-and-update; phải xử lý failure, retry có kiểm soát và hiểu ABA khi giá trị có thể quay lại.
- **Memory semantics** của atomic operations giúp xây publication đúng theo JMM; tuy nhiên không bảo vệ tự động mutable object, nhiều field hoặc workflow bao quanh.
- **Không đặt side effect vào update function** có thể retry; không dùng flag đơn làm giải pháp cho blocking coordination hoặc lifecycle phức tạp.
- **Atomic không thay thế mọi khóa**: khi invariant nhiều biến, yêu cầu chờ, transaction hoặc callback phức tạp, `synchronized`, `Lock`, `Semaphore` hay mô hình single-owner có thể dễ chứng minh đúng hơn.

Cách áp dụng hiệu quả nhất là bắt đầu từ câu hỏi **"phép toán logic nhỏ nhất nào phải atomic?"**. Nếu chỉ cần cập nhật một `int`, `long` hoặc `boolean`, ba atomic class là công cụ gọn và mạnh. Nếu tính đúng đắn trải qua nhiều biến hoặc nhiều giai đoạn, hãy thiết kế giao thức đồng bộ ở **cấp nghiệp vụ**, thay vì kỳ vọng một biến atomic giải quyết toàn bộ concurrency.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-002">AtomicReference và cập nhật object state</a>

<details>
<summary>Xem chi tiết</summary>

# AtomicReference và cập nhật object state trong Java Concurrency

## 1. Mục tiêu: cập nhật trạng thái của cả đối tượng bằng một điểm nguyên tử

Trong Java, một nghiệp vụ có thể phải cập nhật **nhiều trường liên quan** cùng lúc. Ví dụ, đặt chỗ cần giảm `available` và tăng `reserved` sao cho bất biến `available + reserved = total` luôn đúng. Nếu hai thread đọc rồi ghi từng trường riêng lẻ, chúng có thể làm mất cập nhật hoặc để lộ trạng thái dở dang.

`AtomicReference<V>` giữ **một tham chiếu đối tượng** có thể đọc, ghi hoặc thay thế nguyên tử. Khi kết hợp với **object state bất biến** và thao tác **compare-and-set (CAS)**, ta có thể biến việc thay đổi nhiều giá trị thành **một lần thay tham chiếu tới snapshot mới**. Reader lấy một tham chiếu sẽ quan sát một phiên bản trạng thái nhất quán.

Mục tiêu của bài:

- Hiểu `AtomicReference<V>` thực sự bảo vệ **tham chiếu** nào, và vì sao nó không tự biến đối tượng mutable được trỏ tới thành thread-safe.
- Phân biệt `get()`, `set()`, `getAndSet()`, `compareAndSet(expected, update)`, `getAndUpdate()`, `updateAndGet()` và các phương thức tích lũy.
- Giải thích CAS loop, reference identity (`==`), retry khi tranh chấp và điểm tuyến tính hóa (**linearization point**) của lần cập nhật thành công.
- Biết khi nào sử dụng immutable record/immutable class, vì sao phải coi update function là **side-effect-free**, và cách giữ invariant nhiều trường.
- Tái hiện hai thread cạnh tranh một snapshot, chứng minh một CAS thất bại được thử lại thay vì làm mất cập nhật.
- Nhận diện ABA, snapshot cũ, mutable nested objects, side effects bên ngoài, starvation/contention và giới hạn giao dịch nhiều tài nguyên.
- Lựa chọn giữa `AtomicReference`, `AtomicInteger`/`AtomicLong`, `LongAdder`, `synchronized` và `Lock` theo bài toán.

**Phạm vi:** Java SE/JVM concurrency. Ví dụ dùng **Java 17+**, phù hợp với record và `java.util.concurrent.atomic`. Không dùng framework.

## 2. AtomicReference bảo đảm gì?

Khai báo:

~~~java
import java.util.concurrent.atomic.AtomicReference;

AtomicReference<String> state = new AtomicReference<>("NEW");
String observed = state.get();
state.set("READY");
~~~

Đối tượng `AtomicReference` chứa một ô tham chiếu; trong ví dụ, ô tham chiếu ban đầu trỏ tới `"NEW"`, rồi được thay bằng tham chiếu tới `"READY"`. Trong Java Memory Model, `get()` có ngữ nghĩa đọc volatile; `set()` có ngữ nghĩa ghi volatile. CAS có tính nguyên tử với memory effects theo `VarHandle.compareAndSet`.

Ta có thể hình dung:

~~~text
           AtomicReference<State>
           ┌─────────────────────┐
           │ current ────────────┼────────► State v1 (immutable)
           └─────────────────────┘            available=5
                                              reserved=0

Một thread tạo State v2 hoàn chỉnh (3,2) rồi CAS thành công:

           AtomicReference<State>
           ┌─────────────────────┐
           │ current ────────────┼────────► State v2 (immutable)
           └─────────────────────┘            available=3
                                              reserved=2

Reader đã giữ v1 tiếp tục thấy v1; reader mới có thể lấy v2.
Không có thời điểm reader thấy một nửa v1 cộng một nửa v2
nếu nó đọc một snapshot và snapshot thật sự immutable.
~~~

**Giới hạn cốt lõi:** AtomicReference chỉ bảo vệ việc **đọc/thay thế tham chiếu**, không tự khóa object hoặc mọi field bên trong. Ví dụ sau vẫn có data race:

~~~java
class MutableState {
    int count;
}

AtomicReference<MutableState> ref =
        new AtomicReference<>(new MutableState());

// Hai thread cùng gọi:
ref.get().count++; // KHÔNG atomic, có thể lost update
~~~

`ref.get()` trả về tham chiếu theo ngữ nghĩa atomic/volatile, nhưng `count++` thay đổi **cùng object mutable** mà không được bảo vệ. Không có CAS nào trên tham chiếu ở biểu thức này. Dùng `AtomicReference` quanh một object mutable **không phải** là phép biến đổi tự động sang thread-safe.

## 3. Từ các field cập nhật rời rạc đến immutable snapshot

Xét trạng thái tồn kho có hai field:

~~~java
int available = 5;
int reserved = 0;
~~~

Mọi thao tác `reserve(2)` hợp lệ phải giảm available và tăng reserved, giữ bất biến tổng bằng 5. Nếu cập nhật hai field rời rạc:

~~~java
available -= 2;
reserved += 2;
~~~

một reader có thể đi vào giữa và thấy available đã giảm còn reserved chưa tăng. Nếu có nhiều writer, cả hai còn có thể đọc cùng giá trị cũ và ghi đè nhau.

Giải pháp: dùng một immutable record chứa **toàn bộ trạng thái cần nhất quán**:

~~~java
record Stock(int available, int reserved) {
    Stock {
        if (available < 0 || reserved < 0) {
            throw new IllegalArgumentException("negative stock");
        }
    }
}
~~~

Sau khi tạo, record không có setter cho `available` hoặc `reserved`; cả hai là thành phần primitive và không có mutable nested references. Một cập nhật tạo **record mới**, sau đó CAS thay tham chiếu:

~~~java
AtomicReference<Stock> stock =
        new AtomicReference<>(new Stock(5, 0));

Stock before = stock.get();
Stock after = new Stock(before.available() - 2,
                        before.reserved() + 2);

boolean ok = stock.compareAndSet(before, after);
~~~

Nếu `ok == true` thì update toàn cặp đã công bố nguyên tử. Nếu `ok == false`, trạng thái có thể đã đổi bởi thread khác: không được mặc định cập nhật thành công, mà phải **đọc lại và tính lại** dựa trên phiên bản mới.

Để giữ bất biến nhiều trường, logic tạo trạng thái mới phải bảo vệ các điều kiện trước khi CAS. Chẳng hạn, không tạo Stock âm khi available không đủ. Constructor bất biến còn là một lớp phòng thủ bổ sung, nhưng không thay cho việc kiểm tra điều kiện nghiệp vụ và cập nhật trên snapshot mới nhất.

## 4. compareAndSet(expected, update): so sánh danh tính tham chiếu

Phương thức quan trọng nhất:

~~~java
boolean updated = ref.compareAndSet(expected, replacement);
~~~

Quy tắc của `AtomicReference.compareAndSet`:

1. Lấy tham chiếu hiện tại của atomic reference.
2. Nếu tham chiếu hiện tại **chính là đối tượng** `expected` theo phép `==`, thay bằng `replacement` trong một thao tác atomic và trả `true`.
3. Nếu tham chiếu hiện tại **không phải cùng đối tượng**, giữ nguyên reference và trả `false`.
4. Tính đúng của thao tác được bảo đảm theo memory semantics của API, không cần thêm `synchronized` chỉ để bảo vệ chính lần CAS đó.

**CAS so sánh identity, không gọi `equals()` để so giá trị nội dung.** Với record, `equals()` có thể coi hai record có field giống nhau là bằng nhau, nhưng `compareAndSet` vẫn coi chúng là **hai references khác nhau**.

Ví dụ:

~~~java
AtomicReference<String> text =
        new AtomicReference<>(new String("READY"));

String equalButDifferent = new String("READY");
System.out.println(text.get().equals(equalButDifferent)); // true
System.out.println(text.compareAndSet(equalButDifferent, "DONE")); // false
~~~

Đây là đoạn trích Java nằm trong một method, không phải chương trình `main` độc lập. Hai chuỗi được tạo bằng `new String(...)` có nội dung bằng nhau nhưng khác tham chiếu. Việc dùng `String` để minh họa CAS không có nghĩa mọi phép so sánh chuỗi trong nghiệp vụ nên dùng `==`; giá trị chuỗi thông thường vẫn cần `equals()`.

### Linearization point

Một CAS thành công là một điểm trong trình tự đồng thời mà ta có thể xem toàn bộ phép thay reference diễn ra **tại một thời điểm logic**. Các thread tranh cùng atomic reference có thể thấy phiên bản trước hoặc phiên bản sau, nhưng không thấy một phần của lần thay thế reference.

Với snapshot bất biến, điểm CAS thành công chính là điểm **publish** phiên bản mới. Nếu state chứa hai trường được tạo nhất quán từ trước, việc publish chỉ một reference giúp reader không cần khóa để lấy một **phiên bản đầy đủ**.

### Tại sao không dùng get() rồi set()?

~~~java
State old = current.get();
State updated = transform(old);
current.set(updated); // nguy cơ ghi đè bản cập nhật từ thread khác
~~~

`get()` và `set()` đều là atomic accesses **riêng lẻ**. Nếu thread A và B đều đọc cùng `old`, cả hai có thể tính phiên bản mới và gọi set; lần set sau có thể ghi đè update hợp lệ của thread trước. Đây là **lost update** tại mức object state.

`getAndSet(newState)` cũng chỉ **thay thế vô điều kiện** và trả về reference cũ; nó không phù hợp nếu bản mới được tính từ snapshot cũ mà phải giữ toàn bộ cập nhật cạnh tranh. Để bảo vệ read–modify–write, dùng CAS loop hoặc `updateAndGet` với update function thuần.

## 5. CAS loop đúng: đọc, tính, thử thay, thất bại thì thử lại

Mẫu cơ bản:

~~~java
while (true) {
    State before = state.get();       // một snapshot hiện tại
    State after = transform(before);  // tạo phiên bản mới
    if (state.compareAndSet(before, after)) {
        return;                       // cập nhật đã thành công
    }
    // Một thread khác thay state: đọc lại và tính lại.
}
~~~

Ở đây `State` và `transform` là các kiểu/hàm của ứng dụng; đoạn này mô tả thuật toán nằm trong một method, **không phải chương trình chạy độc lập**.

Một chu trình CAS cần chú ý:

- `transform(before)` phải tạo một trạng thái hợp lệ dựa trên **snapshot before**.
- Nếu có **điều kiện thất bại nghiệp vụ** (hết hàng, không đủ quota), kiểm tra điều kiện trên **mỗi snapshot được đọc lại**, không chỉ lần đầu.
- CAS false không phải exception; đó là kết quả cạnh tranh bình thường. Không được trả thành công khi CAS false.
- Dưới contention, một thread có thể phải thử nhiều lần. AtomicReference không hứa mỗi caller đều hoàn thành sau số vòng lặp hữu hạn; **lock-free ở cấp thuật toán** không đồng nghĩa **wait-free cho từng thread**.
- Update function không nên thực hiện I/O, gửi event, ghi file, tăng metric phụ hay thay đổi state có thể quan sát. Nếu CAS thất bại, hàm có thể được gọi thêm lần nữa.
- Nếu thao tác có thể tạo exception (validation, `Math.addExact`), exception nên xảy ra **trước khi CAS** và không để lại cập nhật bất toàn vào shared state.
- Một CAS thành công chỉ bảo vệ trạng thái bên trong atomic reference này. Nếu vừa thay state vừa cập nhật một database/queue khác, hai hành động **không tự biến thành một transaction**.

## 6. Ví dụ độc lập 1: hai thread cùng cập nhật một snapshot, không mất update

Để tái hiện chính xác tranh chấp, chương trình cho hai thread **cùng đọc phiên bản đầu** rồi mới cho chúng thử CAS. Một thread thắng CAS lần đầu; thread còn lại phải thất bại **đúng một lần**, đọc lại, tính phiên bản mới rồi thành công. Kết quả được xác định bằng latch, không phụ thuộc khoảng ngủ.

Lưu tệp **AtomicReferenceCasDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicReferenceCasDemo {
    record State(int count, int revision) {}

    private static final AtomicReference<State> state =
            new AtomicReference<>(new State(0, 0));
    private static final AtomicInteger failedCas = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch beginCas = new CountDownLatch(1);

        Runnable increment = () -> {
            State before = state.get();
            bothRead.countDown();

            try {
                beginCas.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            while (true) {
                State after =
                        new State(before.count() + 1, before.revision() + 1);
                if (state.compareAndSet(before, after)) {
                    return;
                }
                failedCas.incrementAndGet();
                before = state.get();
            }
        };

        Thread a = new Thread(increment, "cas-a");
        Thread b = new Thread(increment, "cas-b");
        a.start();
        b.start();

        bothRead.await();
        beginCas.countDown();
        a.join();
        b.join();

        State result = state.get();
        System.out.println("count=" + result.count());
        System.out.println("revision=" + result.revision());
        System.out.println("failedCAS=" + failedCas.get());
    }
}
~~~

Biên dịch, chạy trên JDK 17+:

~~~bash
javac AtomicReferenceCasDemo.java
java AtomicReferenceCasDemo
~~~

Output khi không có yêu cầu interrupt bên ngoài:

~~~text
count=2
revision=2
failedCAS=1
~~~

**Giải thích tính xác định:**

1. `bothRead` đảm bảo cả A và B đã lấy cùng reference `State(0,0)` ban đầu trước khi `beginCas` được mở.
2. Cả hai tạo `State(1,1)` riêng và thử thay **cùng expected object ban đầu**.
3. Chỉ **một** CAS có thể thay reference ban đầu thành phiên bản mới. CAS của thread còn lại trả false, tăng `failedCas` đúng một lần.
4. Thread thua đọc phiên bản `State(1,1)` vừa publish, tạo `State(2,2)` và CAS thành công; không còn contender khác đang cập nhật.
5. `join()` chờ cả hai thread kết thúc; các thao tác trước khi worker kết thúc được quan sát đúng bởi main sau join. Kết quả cuối là hai lần tăng, không bị lost update.
6. Đây là ví dụ CAS **đúng ở cấp logic**. Nó không chứng minh ứng dụng nên có retry vô hạn trong mọi workload và không phải benchmark tốc độ.

Quan sát: Nếu thay vòng CAS bằng `state.set(after)`, cả A và B đều có thể ghi `State(1,1)` và làm mất một lần tăng. Bản chất của bài là liên kết phiên bản đã đọc với quyền thay thế **chỉ khi phiên bản đó còn là hiện hành**.

## 7. Ví dụ độc lập 2: cập nhật nhiều field của object state

Bài toán: giữ bất biến `available + reserved = initialStock` trong **một JVM**. Mỗi thao tác đặt chỗ cần kiểm tra đủ hàng rồi chuyển số lượng từ available sang reserved. Nếu nhiều thread đến đồng thời, CAS phải bảo vệ trọn **check + update**.

Lưu tệp **AtomicInventoryDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicReference;

public class AtomicInventoryDemo {
    record Stock(int available, int reserved) {
        Stock {
            if (available < 0 || reserved < 0) {
                throw new IllegalArgumentException("negative stock");
            }
        }
    }

    private final AtomicReference<Stock> current;

    public AtomicInventoryDemo(int initial) {
        if (initial < 0) {
            throw new IllegalArgumentException("negative initial stock");
        }
        current = new AtomicReference<>(new Stock(initial, 0));
    }

    public boolean reserve(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        while (true) {
            Stock before = current.get();
            if (before.available() < amount) {
                return false;
            }

            Stock after = new Stock(
                    before.available() - amount,
                    Math.addExact(before.reserved(), amount));

            if (current.compareAndSet(before, after)) {
                return true;
            }
        }
    }

    public Stock snapshot() {
        return current.get();
    }

    public static void main(String[] args) {
        AtomicInventoryDemo inventory = new AtomicInventoryDemo(5);

        System.out.println("reserve2=" + inventory.reserve(2));
        System.out.println("reserve4=" + inventory.reserve(4));
        System.out.println("reserve1=" + inventory.reserve(1));

        Stock result = inventory.snapshot();
        System.out.println("available=" + result.available());
        System.out.println("reserved=" + result.reserved());
        System.out.println("total=" +
                (result.available() + result.reserved()));
    }
}
~~~

Chạy:

~~~bash
javac AtomicInventoryDemo.java
java AtomicInventoryDemo
~~~

Output:

~~~text
reserve2=true
reserve4=false
reserve1=true
available=2
reserved=3
total=5
~~~

**Tại sao thread-safe cho thao tác reserve trên cùng instance?**

- `Stock` record chỉ có hai thành phần primitive, nên mỗi phiên bản thực sự immutable.
- `reserve` đọc **một snapshot**, kiểm tra điều kiện đủ hàng và tạo snapshot mới với tổng không đổi.
- Nếu thread khác đã thay current trước CAS, phép thử false và vòng lặp **đọc lại**; không đặt chỗ dựa trên một snapshot đã lỗi thời.
- CAS thành công publish **cả available và reserved** trong một lần thay tham chiếu; reader dùng một `Stock` local thấy cùng phiên bản của cả hai trường.
- `snapshot()` lấy một reference nhất quán tại thời điểm đọc, nhưng **không khóa các cập nhật tương lai**. Nếu caller đọc `snapshot()` nhiều lần, mỗi lần có thể thấy phiên bản khác.
- `Math.addExact` giúp phát hiện overflow khi cộng reserved; ở trạng thái hợp lệ bình thường với kho nhỏ, nó không phát sinh lỗi.
- Ví dụ main gọi tuần tự để stdout xác định. Cùng mã `reserve` có thể được gọi đồng thời bởi nhiều thread; đảm bảo invariant chỉ áp dụng nếu **mọi** thay đổi Stock đều đi qua giao thức đúng của instance đó.

**Ranh giới quan trọng:** `AtomicReference` ở đây không phải transaction giữa nhiều process, database hoặc dịch vụ khác. Nó không quản lý reservation timeout, idempotency hay giao dịch bên ngoài. Đó là các yêu cầu nghiệp vụ riêng.

## 8. Các convenience methods để cập nhật dựa trên trạng thái cũ

`AtomicReference` có các API phổ biến:

| API | Ý nghĩa | Giá trị trả về |
|---|---|---|
| `get()` | Đọc reference hiện tại với volatile read semantics | Reference hiện tại |
| `set(next)` | Ghi reference mới với volatile write semantics | `void` |
| `lazySet(next)` | Ghi reference mới với release semantics | `void` |
| `getAndSet(next)` | Thay vô điều kiện một cách atomic | Reference trước khi thay |
| `compareAndSet(expect, next)` | Thay nếu reference hiện tại `== expect` | `boolean` thành công/thất bại |
| `compareAndExchange(expect, next)` | Thay nếu reference hiện tại `== expect` | Reference thực tế quan sát tại phép thử (witness) |
| `updateAndGet(fn)` | Tính từ state hiện tại và cập nhật nguyên tử | Reference **mới** sau cập nhật |
| `getAndUpdate(fn)` | Tính từ state hiện tại và cập nhật nguyên tử | Reference **cũ** trước cập nhật |
| `accumulateAndGet(x, fn)` | Kết hợp current và `x` bằng hàm hai đối số | Reference **mới** sau cập nhật |
| `getAndAccumulate(x, fn)` | Tương tự nhưng trả phiên bản trước | Reference **cũ** |

Các phương thức dựa trên function dùng cơ chế nguyên tử; trong quá trình cạnh tranh, **function có thể được gọi lặp lại**. Do đó tránh side effects trong hàm và không giả định function chỉ chạy một lần trên mỗi lời gọi API.

Ví dụ với record bất biến:

~~~java
record Versioned(String text, int revision) {}

AtomicReference<Versioned> ref =
        new AtomicReference<>(new Versioned("draft", 0));

Versioned old = ref.getAndUpdate(s ->
        new Versioned(s.text(), s.revision() + 1));

Versioned current = ref.updateAndGet(s ->
        new Versioned("published", s.revision() + 1));
~~~

Đây là đoạn trích bên trong một method. Khi chỉ một thread chạy các dòng này, `old` là `Versioned("draft", 0)` và `current` là `Versioned("published", 2)`. Điểm khác nhau là **giá trị trả về** của `getAndUpdate` và `updateAndGet`. Với concurrency, kết quả và thời điểm của mỗi phiên bản tuân theo thứ tự các cập nhật thành công, không thể suy từ thứ tự câu lệnh giữa các thread khác nhau.

### Khi dùng `set` hoặc `getAndSet` hợp lý?

`set(next)` phù hợp khi caller chủ ý **publish một trạng thái hoàn toàn mới không cần giữ update cạnh tranh**, chẳng hạn cập nhật một immutable configuration snapshot có chính sách “bản mới nhất thay thế bản cũ”. `getAndSet(next)` hữu ích nếu cần đồng thời **thay và thu lại reference cũ**. Nhưng nếu `next` được suy ra từ current state và không được phép mất update khác, hãy dùng CAS hoặc hàm cập nhật nguyên tử.

## 9. Ví dụ độc lập 3: API updateAndGet và getAndUpdate

Lưu tệp **AtomicReferenceUpdateDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicReference;

public class AtomicReferenceUpdateDemo {
    record Config(String mode, int revision) {}

    public static void main(String[] args) {
        AtomicReference<Config> ref =
                new AtomicReference<>(new Config("draft", 0));

        Config before = ref.getAndUpdate(s ->
                new Config(s.mode(), s.revision() + 1));

        Config after = ref.updateAndGet(s ->
                new Config("published", s.revision() + 1));

        System.out.println("old=" + before.mode()
                + "/" + before.revision());
        System.out.println("new=" + after.mode()
                + "/" + after.revision());
        System.out.println("current=" + ref.get().revision());
    }
}
~~~

Chạy:

~~~bash
javac AtomicReferenceUpdateDemo.java
java AtomicReferenceUpdateDemo
~~~

Output trong trình tự đơn thread này:

~~~text
old=draft/0
new=published/2
current=2
~~~

Dùng các update methods giúp viết ngắn gọn, nhưng **không thay đổi quy tắc purity** của hàm chuyển trạng thái. Trong ứng dụng có contention, lambda có thể được chạy lại và tạo nhiều object ứng viên; chỉ một cập nhật tại mỗi lần gọi thành công được publish.

## 10. Vì sao lambda cập nhật phải side-effect-free?

Ví dụ **sai**:

~~~java
ref.updateAndGet(old -> {
    sendEmail("State is changing"); // SAI: side effect có thể lặp
    return new State(old.value() + 1);
});
~~~

Đoạn code là minh họa trong một method có `ref` và các kiểu/phương thức do ứng dụng định nghĩa. Giả sử thread A tính candidate từ version 1, nhưng thread B publish version 2 trước khi A CAS. A phải **thử lại** lambda với version 2. `sendEmail` có thể đã chạy lần đầu **dù candidate của lần đó không được publish**, sau đó chạy tiếp lần thứ hai. Kết quả là gửi dư sự kiện hoặc gây effect không khớp state.

Giải pháp nguyên tắc:

1. Lambda chỉ tính toán và trả về **candidate immutable state**, không I/O và không mutation bên ngoài.
2. Sau khi CAS thành công, caller có thể thực hiện side effect **một lần theo nhánh success**.
3. Nếu yêu cầu **atomically** gắn side effect với state transition (ví dụ cập nhật DB và gửi thông báo bắt buộc đúng một lần), AtomicReference **không đủ**: cần một giao thức ở tầng sở hữu tài nguyên (transaction/outbox/idempotency hoặc cơ chế tương đương), không tự giả định bước “CAS xong rồi gửi” là atomic với hai hệ thống.

Ngay cả side effect sau CAS vẫn có thể thất bại: cập nhật memory thành công, nhưng gửi thông báo thất bại. Phải xác định yêu cầu bền vững, retry và idempotency riêng.

### Exception trong hàm cập nhật

Nếu update function ném exception **trước CAS** thì lời gọi cập nhật đó thất bại và không publish candidate của lần thử đang xét. Tuy nhiên, nếu function đã có side effect trước khi ném, side effect đó **không tự rollback**. Vì thế purity giúp cả correctness và khả năng khôi phục lỗi.

## 11. Snapshot reference, tính bất biến và cấu trúc lồng nhau

Một snapshot immutable phải thật sự bất biến trong phạm vi dữ liệu mà reader dựa vào.

**Không an toàn nếu chỉ record ở tầng ngoài:**

~~~java
import java.util.List;

record Settings(List<String> features) {}

List<String> mutable = new java.util.ArrayList<>();
mutable.add("search");

AtomicReference<Settings> ref =
        new AtomicReference<>(new Settings(mutable));

// Thread khác vẫn có thể:
mutable.add("billing");
~~~

Record không cho thay reference `features` nhưng bản thân `List` bên trong vẫn mutable và bị caller giữ tham chiếu. Một reader nhận `Settings` qua AtomicReference có thể thấy List bị sửa đồng thời sau publication.

**Tạo defensive copy thích hợp:**

~~~java
import java.util.List;

record Settings(List<String> features) {
    Settings {
        features = List.copyOf(features);
    }
}
~~~

`List.copyOf` tạo danh sách unmodifiable ở cấp container. Nếu phần tử là **immutable String**, cách này giúp trạng thái phù hợp. Nếu phần tử là mutable object, `List.copyOf` không tự làm các phần tử thành immutable; cần copy sâu hơn hoặc thiết kế immutable element.

Bài học:
- Reference swap an toàn **không tự cô lập mutation trên object cũ**.
- Reader giữ một snapshot cũ vẫn có thể sử dụng nó sau khi current trỏ sang snapshot mới; thiết kế cần bảo đảm snapshot cũ **không bị mutate**.
- Nếu muốn cập nhật Map/List phức tạp, thường tạo một immutable copy mới rồi CAS publish thay vì sửa Map/List cũ tại chỗ.
- Copy-on-write phù hợp khi **đọc nhiều, cập nhật ít**; nếu trạng thái lớn và update liên tục, copy có thể tốn CPU/heap/GC.

## 12. Multi-field invariant: atomicity của snapshot không mở rộng sang tài nguyên khác

Với một immutable `State(a,b,c)`, CAS thay reference làm ba field xuất hiện cùng nhau trong một phiên bản. Nhưng cần phân biệt:

| Phạm vi | Có thể bảo đảm bằng AtomicReference? | Lưu ý |
|---|---|---|
| Thay một tham chiếu từ old sang new | **Có** | CAS cung cấp điểm atomic. |
| Giữ invariant của các field trong **cùng immutable state** | **Có nếu transform đúng** | Mỗi candidate phải hợp lệ và tất cả writers dùng protocol. |
| Thay cùng lúc hai AtomicReferences khác nhau | **Không tự động** | Hai CAS riêng có thể bị reader chen giữa. |
| Mutation trong object mutable đã publish | **Không** | Cần cơ chế riêng hoặc immutable design. |
| Ghi file, database, network cùng một lúc với CAS | **Không** | Ngoài phạm vi atomic reference của JVM. |
| Đảm bảo writer riêng lẻ không bị starvation | **Không** | CAS loop có thể retry liên tục dưới contention. |
| Bảo đảm trạng thái bền vững sau process crash | **Không** | AtomicReference nằm trong memory của tiến trình. |

Nếu một invariant trải qua nhiều objects mà không thể gộp vào một immutable snapshot, `synchronized`/`Lock` có thể dễ hiểu hơn; với nhiều tiến trình hay storage ngoài, cần hệ thống transaction/consistency tại nguồn dữ liệu.

## 13. ABA: reference đã đổi A → B → A mà CAS không phát hiện

CAS trả true nếu **reference hiện tại `== expected`**. Nó không trả lời câu hỏi “giữa lúc tôi đọc và lúc tôi cập nhật, object này **đã từng thay đổi** hay chưa?”.

Ví dụ:

~~~text
T1 đọc expected = tham chiếu A
T2 CAS A -> B             (state đổi)
T2 CAS B -> A             (trỏ lại đúng object A cũ)
T1 CAS expected A -> C    (thành công vì current == A)
~~~

T1 không phát hiện chu trình A→B→A, dù các hành động ở giữa có thể ảnh hưởng logic của cấu trúc dữ liệu. Đây là **ABA problem**, đặc biệt cần chú ý trong các cấu trúc lock-free quản lý node, stack, freelist hoặc vòng đời object.

**Không đồng nhất ABA với hai object bằng `equals()`:** nếu T2 thay A bằng một object A2 có cùng nội dung nhưng **reference khác**, T1 đang giữ A sẽ CAS thất bại. ABA nguy hiểm khi current trở lại **chính reference A cũ**.

### Giải pháp khi cần biết lịch sử thay đổi

`AtomicStampedReference<V>` giữ **cặp (reference, integer stamp)** và cho phép CAS cả reference lẫn stamp:

~~~java
import java.util.concurrent.atomic.AtomicStampedReference;

AtomicStampedReference<String> stamped =
        new AtomicStampedReference<>("A", 0);

int[] holder = new int[1];
String current = stamped.get(holder);  // đọc cặp nhất quán
int version = holder[0];

boolean changed =
        stamped.compareAndSet(current, "B", version, version + 1);
~~~

Đây là đoạn trích dùng trong một method. `get(holder)` trả reference và điền stamp tương ứng vào `holder[0]`. `compareAndSet` chỉ thành công khi **cả reference và stamp** khớp expected. Tăng stamp trên mỗi transition có thể giúp phát hiện A→B→A ngay cả khi reference quay lại A. Tuy nhiên integer stamp có phạm vi hữu hạn, có thể overflow/quay vòng; cần đánh giá khả năng wraparound trong workload dài.

`AtomicMarkableReference<V>` lưu (reference, boolean mark), phù hợp một số thuật toán cần đánh dấu, nhưng boolean mark không thay thế được version counter tổng quát.

**Khi nào không cần quá lo ABA?** Trong mẫu immutable state mà **mỗi lần cập nhật tạo một object mới và không bao giờ publish lại object cũ**, cùng identity cũ khó xuất hiện trở lại theo giao thức đó. Dù vậy không suy từ đó rằng mọi thuật toán CAS đều miễn ABA: hãy xét cụ thể việc tái sử dụng reference và invariant của cấu trúc.

## 14. Cập nhật object state và Java Memory Model

Các bảo đảm đồng bộ không phải là “ngẫu nhiên nhờ CPU cache”. Java API quy định memory effects:

- `AtomicReference.get()` có semantics của volatile read.
- `AtomicReference.set(...)` có semantics của volatile write.
- `compareAndSet(...)` có memory effects theo `VarHandle.compareAndSet` và thực hiện phép cập nhật reference atomically.
- Khi writer khởi tạo candidate object **trước** khi publish thành công, reader đọc reference đã publish phù hợp có thể quan sát các ghi khởi tạo trước đó theo happens-before.
- Final fields trong immutable object còn có các quy tắc khởi tạo riêng của JMM; nhưng không nên dựa vào final để cho phép mutable nested objects bị thay đổi tùy ý sau publish.

Một mô hình hữu ích:

~~~text
Writer:    new State(fields hoàn chỉnh)
                  │
                  ▼
           CAS current old → new (publish)
                  │
            đồng bộ phù hợp
                  │
                  ▼
Reader:    State snapshot = current.get()
                  │
                  ▼
           đọc fields từ đúng snapshot
~~~

Reader **không cần khóa** để đọc các trường của một immutable state đã publish an toàn. Tuy nhiên, reader có thể quan sát **phiên bản cũ hoặc mới** tùy thứ tự tương tác; nó không được đảm bảo luôn thấy bản cập nhật tương lai trước khi bản đó tồn tại.

Nếu reader gọi `current.get()` hai lần và đọc field A từ phiên bản đầu, field B từ phiên bản sau, nó có thể tự tạo snapshot trộn. Giữ **một tham chiếu local**:

~~~java
State snapshot = current.get();
int a = snapshot.a();
int b = snapshot.b();
~~~

Đoạn minh họa này giả định State có accessor a()/b(). Cả hai field được đọc từ **cùng một immutable object**. Điều này khác với “khóa mọi update” và vẫn cho writer hoạt động đồng thời.

## 15. CAS thất bại, retry và chi phí hiệu năng

AtomicReference thường rất hữu ích khi state thay đổi tương đối ít hoặc phép biến đổi nhanh; nhưng **mỗi CAS thất bại** có thể làm lãng phí công tính toán và object ứng viên vừa được tạo.

Các nguồn chi phí:

1. **Contention:** nhiều writers cùng đọc một reference và tranh commit.
2. **Allocation:** mỗi lần thử có thể tạo immutable State object mới; candidate thất bại trở thành garbage.
3. **Retry:** transform có thể chạy lại nhiều lần, đặc biệt nếu dùng updateAndGet/functional methods.
4. **Cache coherence:** nhiều core tranh một vị trí cập nhật chung.
5. **Read/write balance:** copy immutable Map/Tree quá lớn cho mỗi update có thể tốn hơn một lock bảo vệ cấu trúc mutable.
6. **Fairness/starvation:** CAS lock-free không cam kết từng thread hoàn thành trong thời gian có giới hạn.
7. **Chính sách áp lực:** nếu mọi request retry không giới hạn khi hệ thống quá tải, CPU contention có thể tăng thay vì phục vụ tốt hơn.

**Không benchmark bằng một lần chạy.** Muốn so sánh `AtomicReference` với lock, cần cùng invariant, cùng workload, số writers/readers, thời gian transform, số field, tỷ lệ contention, cấu hình JDK/CPU và đo throughput, p95/p99 latency, allocation và GC. Trong một số trường hợp, `synchronized` hoặc `ReentrantLock` đơn giản hơn và có hiệu quả tốt hơn.

### Có cần backoff?

Với contention cao, đôi khi cân nhắc retry có giới hạn, backoff hoặc serializing updates qua một owner thread. Không có công thức backoff cố định phù hợp mọi workload. `Thread.onSpinWait()` chỉ là **hint** cho vòng spin ngắn, không biến CAS thành công hay tạo fairness; không gọi sleep/yield tùy tiện để “sửa” tính đúng. Nếu cần bảo đảm thời hạn hoặc hủy, thiết kế chính sách retry/cancellation riêng.

## 16. Chọn AtomicReference, atomic counter hay lock

| Tình huống | Lựa chọn thường hợp lý | Vì sao |
|---|---|---|
| Tăng một biến `int` hoặc `long` chính xác | `AtomicInteger`/`AtomicLong` | Increment/CAS chuyên biệt, không cần tạo object snapshot mỗi lần tăng. |
| Nhiều writers ghi nhận metric, reader chỉ lấy tổng quan sát | `LongAdder` | Giảm contention, nhưng `sum()` không phải snapshot atomically khi có writer. |
| Thay một immutable config snapshot | `AtomicReference<Config>` | Atomic publish một object mới, reader có snapshot nhất quán. |
| Check và cập nhật **nhiều field trong cùng immutable state** | `AtomicReference<State>` + CAS loop | Atomic reference swap bảo vệ invariant giữa các field. |
| Mutable graph lớn, transform phức tạp, contention cao | `synchronized`/`ReentrantLock` | Có thể dễ bảo trì hơn và tránh cấp phát/copy lại liên tục. |
| Một trạng thái phụ thuộc tài nguyên ngoài | Transaction/consistency protocol phù hợp | AtomicReference không bao trùm database, file, network. |
| Cần phân biệt A→B→A trên cùng reference | `AtomicStampedReference` hoặc thuật toán versioning phù hợp | Có version/stamp để phát hiện chu kỳ reference nếu không bị wraparound. |
| Chỉ cần publish một immutable value không phụ thuộc old | `volatile` field hoặc `AtomicReference.set` | Không cần CAS nếu không có read-modify-write cạnh tranh cần bảo vệ. |

### Khi lock đơn giản hơn

Nếu phép cập nhật cần làm việc dài, gọi API có side effect hoặc thao tác trên nhiều cấu trúc mutable khó copy, CAS loop buộc transform có thể chạy lặp; code dễ phức tạp. Một critical section có lock chung đôi khi diễn đạt đúng **trọn đơn vị nghiệp vụ** rõ hơn. Nhưng giữ lock qua I/O dài có thể tăng contention; thiết kế cần đánh giá ranh giới đồng bộ, không mặc định lock luôn tốt hơn hay CAS luôn nhanh hơn.

## 17. Các lỗi phổ biến khi code review

**Lỗi 1 — Coi AtomicReference<Mutable> là khóa.** `ref.get().field++` vẫn racy. Reference atomic không làm field update atomic.

**Lỗi 2 — Gọi get() rồi set(transform(old)).** Hai thao tác riêng có thể lost update. Cần CAS hoặc function update atomically nếu update phụ thuộc state hiện tại.

**Lỗi 3 — Dùng equals thay cho expected identity.** `compareAndSet` dựa trên `==` reference identity. Hai record có nội dung bằng nhau vẫn có thể là reference khác.

**Lỗi 4 — Sau CAS false vẫn trả true hoặc áp dụng candidate cũ.** Đọc lại current rồi tính lại toàn bộ điều kiện và snapshot.

**Lỗi 5 — Cập nhật nhiều trường bằng hai AtomicReferences độc lập.** Hai CAS riêng không phải một transaction. Nếu cần snapshot nhất quán, gói cả hai field vào một immutable State.

**Lỗi 6 — Record chứa List/Map mutable.** Record chỉ shallow immutable. Phải copy/tránh sửa nested state đang chia sẻ.

**Lỗi 7 — Gửi email hoặc tăng metric phụ bên trong updateAndGet lambda.** Function có thể được chạy nhiều lần khi CAS retry. Chỉ dùng hàm thuần, thực hiện side effect sau success nếu hợp đồng cho phép.

**Lỗi 8 — Đọc current hai lần rồi ghép field của hai phiên bản.** Giữ một `State snapshot = ref.get()` để đọc nhiều field nhất quán.

**Lỗi 9 — Nhầm reference identity với lịch sử version.** ABA có thể xảy ra nếu cùng reference được publish lại. Xét stamped/versioned protocol khi invariant cần biết mọi thay đổi trung gian.

**Lỗi 10 — Ghi object cũ sau khi đã publish mới.** Reader đang giữ object cũ có thể bị tác động. Dùng immutable snapshots và không mutate chúng sau publish.

**Lỗi 11 — CAS loop vô hạn trong mọi workload.** Dưới contention cao có thể starvation, CPU và allocation tăng. Đo tải, đặt deadline/retry budget khi yêu cầu nghiệp vụ cần.

**Lỗi 12 — Giữ lẫn Java in-memory CAS và database transaction.** AtomicReference chỉ có phạm vi trong một JVM process.

**Lỗi 13 — Bỏ qua overflow/validation.** Atomic snapshot vẫn có thể chứa dữ liệu nghiệp vụ sai nếu transform không giữ invariant. Dùng kiểm tra điều kiện và `Math.addExact` khi cần.

**Lỗi 14 — Thay state bằng set(null) và assume mọi reader đều không-null.** Constructor không tham số của AtomicReference khởi tạo null; nếu cho phép null, mọi đường đọc phải có contract xử lý hoặc validation rõ ràng.

## 18. Mẫu thiết kế thực tế

Một service giữ immutable runtime configuration với các thao tác:

~~~java
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeSettings {
    public record Config(String endpoint, int retryLimit) {
        public Config {
            Objects.requireNonNull(endpoint);
            if (retryLimit < 0) {
                throw new IllegalArgumentException("negative retryLimit");
            }
        }
    }

    private final AtomicReference<Config> current =
            new AtomicReference<>(new Config("api-a", 2));

    public Config snapshot() {
        return current.get();
    }

    public Config updateRetryLimit(int newLimit) {
        if (newLimit < 0) {
            throw new IllegalArgumentException("negative newLimit");
        }
        return current.updateAndGet(
                old -> new Config(old.endpoint(), newLimit));
    }

    public void publish(Config replacement) {
        current.set(Objects.requireNonNull(replacement));
    }
}
~~~

Class Java 17+ này không có main; mục đích là chỉ ra **hai chính sách cập nhật khác nhau**:

- `publish(replacement)`: chủ ý thay **toàn bộ config** bằng snapshot từ caller, không cần giữ update khác. Nếu concurrent writers gọi publish, bản ghi cuối theo thứ tự thực tế sẽ thắng.
- `updateRetryLimit(newLimit)`: cập nhật **một thuộc tính dựa trên config mới nhất**; nếu writer khác thay endpoint trước CAS, function có thể được chạy lại và giữ endpoint mới thay vì ghi đè bằng một endpoint cũ.

**Giới hạn semantics:** Trong một cuộc đua giữa `publish` và `updateRetryLimit`, mỗi method có điểm atomic riêng. Caller không được giả định “lệnh được gọi trước” nhất thiết được commit trước giữa các thread khác nhau. Nếu có thứ tự nghiệp vụ bắt buộc hoặc quyền ưu tiên giữa hai loại cập nhật, cần chính sách version/locking rõ hơn.

### Tư duy về quyền sở hữu writer

Nếu ứng dụng chỉ có một config publisher và nhiều readers, `volatile Config` đơn giản đôi khi đủ. Khi có nhiều writers cần update dựa trên old state mà không làm mất nhau, AtomicReference/CAS phù hợp hơn. Không chọn CAS chỉ vì “thread-safe” nghe hấp dẫn; trước tiên xác định có **compound update/invariant** cần bảo vệ hay không.

## 19. Kiểm tra bằng stress test và công cụ

Cách kiểm tra đúng:

1. **Deterministic interleaving**: dùng `CountDownLatch` để ép hai writers đọc cùng snapshot, chứng minh CAS false và retry như ví dụ đầu.
2. **Stress test**: cho nhiều threads cập nhật một shared reference; chờ `join()` hoặc `Future.get()` rồi kiểm tra invariant và tổng lượng update, tránh đọc quá sớm.
3. **Snapshot consistency**: với state đa trường, kiểm tra mọi phiên bản đọc được đều tuân thủ invariant, không chỉ final state.
4. **Contention metrics**: đếm CAS failures/retries, allocation, CPU, độ trễ xử lý, tail latency và throughput.
5. **Exception/cancellation**: xem function ném lỗi giữa chừng có side effect không; khi ngừng task, còn writer nào update sau điểm kiểm tra không.
6. **Reference lifecycle**: có reuse object identity hoặc mutate old snapshots không; ABA hoặc memory retention có thể ảnh hưởng thuật toán.

Một lần stress test chạy đúng **không chứng minh** không có race trên mọi interleaving. Với thuật toán concurrency tinh vi, có thể nghiên cứu OpenJDK jcstress để thăm dò các kết quả quan sát được theo Java Memory Model. Không dùng thời gian `Thread.sleep()` làm bằng chứng tính đúng hoặc để “đồng bộ” các thread.

## 20. Bài tập tự kiểm tra

**Câu 1.** Vì sao `AtomicReference<MutableCounter> ref` với `ref.get().count++` không thread-safe?

**Đáp án:** AtomicReference chỉ bảo vệ việc lấy/thay **tham chiếu**. Phép tăng trên field của object mutable vẫn là read–modify–write không atomic và có thể lost update.

**Câu 2.** Hai records có `equals() == true` có thể làm `compareAndSet` trả false không?

**Đáp án:** Có. CAS so reference identity bằng `==`; hai records có field bằng nhau nhưng là instances khác nhau không phải cùng expected reference.

**Câu 3.** CAS false thì candidate đã tính có nên được publish bằng `set(candidate)` để “khỏi mất công” không?

**Đáp án:** Không nếu candidate phụ thuộc state cũ. `set` có thể ghi đè update của thread khác. Đọc lại snapshot và tính lại điều kiện, rồi CAS mới.

**Câu 4.** Tại sao updateAndGet lambda không được gửi email?

**Đáp án:** Khi tranh chấp, lambda có thể chạy lại trước khi CAS thành công; side effect có thể xảy ra nhiều lần hoặc xảy ra dù không commit candidate.

**Câu 5.** Với hai trường `balance` và `version`, dùng hai AtomicInteger riêng có đảm bảo reader luôn thấy cùng phiên bản không?

**Đáp án:** Không. Hai atomic updates độc lập không trở thành một transaction. Gói dữ liệu trong một immutable snapshot và thay reference nguyên tử hoặc dùng lock bảo vệ đọc/ghi theo invariant.

**Câu 6.** Reader gọi `ref.get()` một lần và giữ immutable State local trong khi writer CAS nhiều lần. Reader có thấy state “bị đổi giữa chừng” không?

**Đáp án:** Không nếu State thật sự immutable. Reader giữ một object snapshot cụ thể, trong khi writer publish references mới.

**Câu 7.** Tại sao CAS đôi lúc vẫn sai dù mỗi CAS là nguyên tử?

**Đáp án:** Code xung quanh có thể dùng stale assumptions, vi phạm invariants, chứa side effects hoặc gặp ABA nếu cùng reference được tái sử dụng. Atomic CAS không tự chứng minh toàn thuật toán đúng.

**Câu 8.** Khi nào `AtomicLong` tốt hơn `AtomicReference<State>`?

**Đáp án:** Khi chỉ cần tăng/giảm một số nguyên đơn lẻ, AtomicLong có API trực tiếp và tránh allocation snapshot cho mỗi lần cập nhật; nếu cần invariant nhiều trường, State snapshot có thể phù hợp hơn.

## 21. Tài liệu API chính thức

- `AtomicReference<V>`, Java 21: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html
- `AtomicStampedReference<V>`, Java 21: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicStampedReference.html
- `AtomicMarkableReference<V>`, Java 21: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicMarkableReference.html
- `AtomicInteger`, Java 21: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html
- `VarHandle`, Java 21: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/VarHandle.html
- **JLS `17 — Threads and Locks (Java Memory Model):** https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html

**Ghi nhớ cốt lõi:** AtomicReference bảo vệ **một lần thay object reference**, không tự bảo vệ mutation bên trong object. Để cập nhật state nhiều trường một cách nhất quán, hãy dựng **immutable snapshot mới** từ snapshot cũ, kiểm tra invariant và publish bằng CAS; nếu CAS thất bại, đọc lại và tính lại. Mọi hàm có thể bị gọi khi retry phải không có side effect. Khi state vượt quá một reference hoặc đụng tài nguyên ngoài, cần một protocol nhất quán rộng hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-003">compareAndSet() và nguyên tắc CAS</a>

<details>
<summary>Xem chi tiết</summary>

# compareAndSet() và nguyên tắc CAS trong Java

## 1. Vấn đề mà CAS giải quyết

Trong lập trình đa luồng, hai thread có thể cùng cập nhật một giá trị. Biểu thức `counter++` thực chất cần đọc giá trị cũ, tính `+1` và ghi kết quả. Các bước có thể xen kẽ:

```text
Ban đầu counter = 10
Thread A: đọc 10              Thread B: đọc 10
Thread A: tính 11             Thread B: tính 11
Thread A: ghi 11              Thread B: ghi 11
Cuối cùng counter = 11, dù đã yêu cầu tăng hai lần.
```

Đây là **lost update**. `volatile` cho một trường có bảo đảm về visibility và thứ tự bộ nhớ, nhưng **không biến phép đọc–sửa–ghi thành một thao tác atomic**. Để sửa đúng, cần một giao thức cho phép phát hiện “giá trị đã bị ai đó đổi trước khi mình ghi”.

**Compare-And-Set (CAS)** là thao tác nguyên tử có điều kiện: **chỉ thay giá trị hiện tại bằng giá trị mới nếu giá trị hiện tại vẫn khớp giá trị kỳ vọng**. Trong thư viện Java, các lớp `AtomicInteger`, `AtomicLong`, `AtomicBoolean`, `AtomicReference` hỗ trợ phương thức `compareAndSet(expected, update)`.

Pseudocode:

```text
CAS(expected, update):
    nếu current khớp expected:
        current = update
        trả về true
    ngược lại:
        giữ nguyên current
        trả về false
```

Điều quan trọng là **so sánh và thay thế hợp thành một thao tác không bị xen ngang**. Viết bằng `if` thông thường trên một trường không được đồng bộ sẽ không tạo ra CAS.

Bài này chỉ nói về **concurrency của Java nói chung**, không phụ thuộc một framework cụ thể.

## 2. Ý nghĩa của kết quả boolean

- `true`: expected khớp giá trị hiện tại tại điểm CAS; cập nhật **đã commit**.
- `false`: đối với **strong CAS `compareAndSet`**, expected không khớp trong lần thử này; `update` **không được ghi**.
- Không có ngoại lệ “CAS tự retry” trong một lời gọi `compareAndSet` đơn lẻ. Người dùng API phải quyết định retry, từ chối hoặc xử lý conflict.
- Nếu dùng `AtomicReference<T>`, việc khớp expected được xét theo **danh tính tham chiếu (`==`)**, không phải `equals()`.
- Với các kiểu atomic số nguyên, so sánh expected theo giá trị nguyên thủy.

### Ví dụ 1: lời gọi thành công và thất bại

Tạo `CasBasicsDemo.java`:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class CasBasicsDemo {
    public static void main(String[] args) {
        AtomicInteger number = new AtomicInteger(10);

        boolean a = number.compareAndSet(10, 11);
        boolean b = number.compareAndSet(10, 12);
        boolean c = number.compareAndSet(11, 20);

        System.out.println("a=" + a);
        System.out.println("b=" + b);
        System.out.println("c=" + c);
        System.out.println("value=" + number.get());
    }
}
```

```bash
javac CasBasicsDemo.java
java CasBasicsDemo
```

Kết quả:

```text
a=true
b=false
c=true
value=20
```

Lần đầu `10 -> 11` thành công. Lần thứ hai vẫn kỳ vọng `10` nên thất bại, giữ nguyên `11`. Lần cuối kỳ vọng `11` nên thành công, ghi `20`.

Trong chương trình đa luồng, lý do expected trở thành “cũ” thường là một thread khác đã cập nhật giá trị giữa lúc mình gọi `get()` và lúc CAS.

## 3. Nhiều thread tranh CAS: chỉ một người thắng cùng expected

Giả sử hai thread cùng đọc `0`, đều gọi `compareAndSet(0, 1)`. Khi một CAS thành công, giá trị đã thành `1`; CAS còn lại với expected `0` phải thất bại nếu không có cập nhật đưa giá trị về `0`. Để kiểm chứng mà không phụ thuộc việc CPU ưu tiên thread nào, dùng latch.

Tạo `CasOneWinnerDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class CasOneWinnerDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger value = new AtomicInteger(0);
        AtomicInteger wins = new AtomicInteger(0);
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Runnable task = () -> {
            int expected = value.get();
            bothRead.countDown();
            try {
                go.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (value.compareAndSet(expected, expected + 1)) {
                wins.incrementAndGet();
            }
        };

        Thread a = new Thread(task, "A");
        Thread b = new Thread(task, "B");
        a.start();
        b.start();
        bothRead.await();
        go.countDown();
        a.join();
        b.join();

        System.out.println("wins=" + wins.get());
        System.out.println("value=" + value.get());
    }
}
```

```bash
javac CasOneWinnerDemo.java
java CasOneWinnerDemo
```

Kết quả khi không có interrupt từ bên ngoài:

```text
wins=1
value=1
```

Hai thread chắc chắn đều đọc `0` trước khi được mở latch `go`. Một CAS thay giá trị thành `1`; CAS còn lại thấy expected không phù hợp. **Không thể dự đoán A hay B thắng**, nên ví dụ chỉ in số lần thành công. Nếu mỗi thread đại diện cho một lần **tăng bắt buộc phải được giữ**, thread thất bại phải đọc lại và retry.

## 4. Vòng retry CAS: đọc lại, tính lại, thử lại

Mẫu cập nhật một biến atomic:

```java
while (true) {
    int before = counter.get();
    int after = before + 1;
    if (counter.compareAndSet(before, after)) {
        break;
    }
    // CAS thất bại: thread khác có thể đã cập nhật counter.
    // Lượt sau đọc giá trị mới và tính lại.
}
```

Đây là đoạn minh họa, giả định `counter` là `AtomicInteger`. **Không** đưa `before` ra khỏi vòng rồi dùng mãi expected cũ: khi giá trị đã khác expected, vòng lặp có thể không bao giờ thành công.

### Ví dụ 2: mỗi yêu cầu tăng đều được giữ

Tạo `CasRetryDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class CasRetryDemo {
    static void increment(AtomicInteger value) {
        for (;;) {
            int before = value.get();
            int after = before + 1;
            if (value.compareAndSet(before, after)) {
                return;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicInteger value = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Runnable task = () -> {
            try {
                start.await();
                for (int i = 0; i < 10_000; i++) {
                    increment(value);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start();
        b.start();
        start.countDown();
        a.join();
        b.join();
        System.out.println("value=" + value.get());
    }
}
```

```bash
javac CasRetryDemo.java
java CasRetryDemo
```

Kết quả khi không có interrupt từ bên ngoài:

```text
value=20000
```

Mỗi thread tăng 10.000 lần. Khi CAS thất bại, nó thử lại với giá trị hiện tại; không bỏ một lần tăng. `join()` giúp main chỉ in sau khi cả hai hoàn tất.

Mẫu này chưa xử lý giới hạn số nguyên, hủy tác vụ, hoặc nhiều side effect khác. Nếu `int` có thể vượt giới hạn, phải thiết kế kiểm tra overflow. Nếu một vòng retry cần hủy được, cần chủ động đọc trạng thái interrupt hoặc token hủy tại điểm phù hợp.

## 5. Điểm tuyến tính hóa và JMM

CAS thành công có thể được hiểu là có một **linearization point** — điểm logic mà thay đổi từ expected sang update có hiệu lực nguyên tử. Điều này giải thích vì sao hai CAS cùng mong chờ một giá trị không thể cùng ghi đè mù quáng như hai phép `counter++`.

Các atomic API thông thường còn có **bảo đảm bộ nhớ** theo Java Memory Model (JMM). `get()`, `set()`, `compareAndSet()` cung cấp ngữ nghĩa phù hợp cho công bố và quan sát dữ liệu như các thao tác volatile tương ứng. Một thread tạo trạng thái hoàn chỉnh rồi công bố qua atomic reference, thread khác nhận tham chiếu đó bằng `get()` có quan hệ visibility/happens-before phù hợp để quan sát dữ liệu đã khởi tạo trước lúc công bố.

Nhưng không được suy rộng:

- Một CAS trên `atomic` không tự bảo vệ các trường mutable của object khác.
- Một CAS không thay thế transaction cho thao tác gồm nhiều biến/đối tượng riêng.
- Hai CAS thành công liên tiếp vẫn có **hai** điểm tuyến tính hóa, không hợp thành một giao dịch nguyên tử.
- Nếu object được công bố còn bị sửa không đồng bộ về sau, nguyên tử ở cấp tham chiếu không cứu các data race bên trong.

Hãy suy luận theo **đơn vị trạng thái cần nhất quán**, không chỉ theo sự xuất hiện của tên API atomic.

## 6. `AtomicReference` so theo identity, không phải `equals()`

Với `AtomicReference<T>`, expected phải là **đúng tham chiếu** đang được lưu. Hai object có nội dung bằng nhau theo `equals()` vẫn là hai identity khác nhau.

Tạo `CasReferenceIdentityDemo.java`:

```java
import java.util.concurrent.atomic.AtomicReference;

public class CasReferenceIdentityDemo {
    record State(int value) {}

    public static void main(String[] args) {
        State original = new State(5);
        State equalCopy = new State(5);
        AtomicReference<State> ref = new AtomicReference<>(original);

        System.out.println("equals=" + original.equals(equalCopy));
        System.out.println("same=" + (original == equalCopy));

        boolean wrong = ref.compareAndSet(equalCopy, new State(6));
        boolean right = ref.compareAndSet(original, new State(6));

        System.out.println("wrong=" + wrong);
        System.out.println("right=" + right);
        System.out.println("value=" + ref.get().value());
    }
}
```

```bash
javac CasReferenceIdentityDemo.java
java CasReferenceIdentityDemo
```

Kết quả:

```text
equals=true
same=false
wrong=false
right=true
value=6
```

`State` là record nên hai record với cùng giá trị có `equals() == true`, nhưng chúng là hai object. CAS dùng tham chiếu `equalCopy` làm expected thất bại, còn dùng `original` là tham chiếu thực sự đang giữ thì thành công.

Đúng mẫu là:

```java
State before = ref.get();
State after = new State(before.value() + 1);
boolean changed = ref.compareAndSet(before, after);
```

Đây là đoạn minh họa; nếu `changed` là `false`, cần quyết định retry hoặc báo conflict. Tránh tự tạo một object mới “bằng” với state hiện tại rồi truyền làm expected.

## 7. CAS một immutable snapshot để cập nhật nhiều biến liên quan

Giả sử `left` và `right` là hai số dư có tổng luôn bằng `100`. Hai `AtomicInteger` cập nhật lần lượt:

```java
left.addAndGet(-1);
right.addAndGet(1);
```

vẫn có thể khiến reader thấy tổng bằng `99` ở giữa hai bước. Mỗi atomic tự nhất quán không biến một nhóm field thành transaction.

Giải pháp phù hợp với trạng thái nhỏ là gói cả hai trong **một record bất biến** và CAS **một tham chiếu gốc**.

Tạo `CasSnapshotDemo.java`:

```java
import java.util.concurrent.atomic.AtomicReference;

public class CasSnapshotDemo {
    record Balances(int left, int right) {
        Balances {
            if (left < 0 || right < 0 || left + right != 100) {
                throw new IllegalArgumentException("invalid state");
            }
        }
    }

    static void transferOne(AtomicReference<Balances> ref) {
        for (;;) {
            Balances before = ref.get();
            Balances after =
                    new Balances(before.left() - 1, before.right() + 1);

            if (ref.compareAndSet(before, after)) {
                return;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<Balances> state =
                new AtomicReference<>(new Balances(100, 0));

        Runnable task = () -> {
            for (int i = 0; i < 50; i++) {
                transferOne(state);
            }
        };

        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start();
        b.start();
        a.join();
        b.join();

        Balances snapshot = state.get();
        System.out.println("left=" + snapshot.left());
        System.out.println("right=" + snapshot.right());
        System.out.println("total="
                + (snapshot.left() + snapshot.right()));
    }
}
```

```bash
javac CasSnapshotDemo.java
java CasSnapshotDemo
```

Kết quả:

```text
left=0
right=100
total=100
```

Hai thread mỗi người thực hiện 50 lần chuyển một đơn vị, tất cả đều phải commit. Mỗi vòng CAS tạo một record hoàn chỉnh, hợp lệ; nếu bị cạnh tranh, vòng lặp lấy record mới và tính lại. Không có record nào bị sửa tại chỗ.

**Reader phải lấy một tham chiếu duy nhất** khi cần nhiều trường cùng phiên bản:

```java
Balances snapshot = state.get();
int left = snapshot.left();
int right = snapshot.right();
```

Đây là đoạn minh họa dùng `state` ở ví dụ trên. Nếu gọi `state.get().left()` và `state.get().right()` **hai lần riêng**, writer có thể thay snapshot giữa hai lượt, khiến reader ghép hai phiên bản khác nhau.

Ví dụ cố ý có đúng 100 đơn vị và đúng 100 lần chuyển nên không âm. Trong ứng dụng thật, validation như đủ tiền, overflow, điều kiện hủy phải được kiểm tra trên **mỗi snapshot đọc lại**, và cập nhật RAM không thay thế transaction database.

## 8. Hàm CAS nội bộ có thể bị gọi lại

`AtomicInteger.updateAndGet`, `getAndUpdate` và các biến thể của `AtomicReference` thường thực hiện cơ chế retry nội bộ để áp dụng một hàm của giá trị cũ.

```java
AtomicInteger n = new AtomicInteger(10);
int after = n.updateAndGet(old -> old + 5);
```

Đoạn minh họa này cần import `AtomicInteger`; `after` bằng `15` khi không có writer khác. Nhưng nếu bị contention, hàm `old -> old + 5` **có thể được gọi nhiều lần** cho một lần commit.

Vì vậy không nên viết:

```java
n.updateAndGet(old -> {
    chargeCustomer(); // Có thể gọi nhiều lần do retry!
    return old + 5;
});
```

Đây là phản ví dụ, `chargeCustomer()` là hàm nghiệp vụ. Hàm cập nhật nên thuần (không có tác dụng phụ không thể lặp), tính state mới từ state cũ. Side effect bên ngoài CAS phải được điều phối bằng idempotency hoặc transaction nếu hệ thống yêu cầu “một lần duy nhất”; chuyển side effect ra ngoài lambda **không tự làm hai hệ thống trở thành một transaction**.

Một quy tắc quan trọng: **mỗi lần CAS thất bại phải tính lại state và điều kiện hợp lệ trên snapshot mới**. Không tái sử dụng kết quả kiểm tra từ phiên bản cũ nếu điều kiện nghiệp vụ có thể thay đổi.

## 9. ABA problem: giá trị A → B → A

CAS xem **giá trị hiện tại**, không tự lưu lịch sử. Nếu thread A đọc giá trị `A` rồi chờ, thread B có thể đổi `A -> B -> A`. Khi A gọi CAS với expected `A`, thao tác có thể thành công. Đây gọi là **ABA**.

```text
Thread A: read A ------------------ CAS(A, C) SUCCESS
Thread B:        A -> B -> A
Shared:    A ------- B ---- A ------ C
```

CAS không sai: tại thời điểm của nó, current quả thực là A. **Lỗi chỉ xuất hiện nếu thuật toán giả định “vẫn A” đồng nghĩa “chưa có thay đổi lịch sử”.** Ví dụ, với node của một lock-free stack, tham chiếu trở lại node cũ không nhất thiết có nghĩa các liên kết còn như trước.

### Phiên bản hóa bằng `AtomicStampedReference`

Java cung cấp `AtomicStampedReference<V>` để cập nhật nguyên tử một cặp **(reference, stamp)**. Mọi writer có thể tăng stamp theo giao thức, nhờ đó trạng thái quay lại cùng reference vẫn được nhận ra là một phiên bản mới.

Tạo `CasAbaDemo.java`:

```java
import java.util.concurrent.atomic.AtomicStampedReference;

public class CasAbaDemo {
    public static void main(String[] args) {
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();

        AtomicStampedReference<Object> state =
                new AtomicStampedReference<>(a, 0);
        int oldStamp = state.getStamp();

        boolean ab = state.compareAndSet(a, b, 0, 1);
        boolean ba = state.compareAndSet(b, a, 1, 2);
        boolean stale =
                state.compareAndSet(a, c, oldStamp, oldStamp + 1);

        System.out.println("A-B=" + ab);
        System.out.println("B-A=" + ba);
        System.out.println("same=" + (state.getReference() == a));
        System.out.println("stale=" + stale);
        System.out.println("stamp=" + state.getStamp());
    }
}
```

```bash
javac CasAbaDemo.java
java CasAbaDemo
```

Kết quả:

```text
A-B=true
B-A=true
same=true
stale=false
stamp=2
```

Tham chiếu đã quay về đúng object `a`, nhưng stamp tăng từ `0` lên `2`. CAS với expected stamp `0` thất bại. Cơ chế cần **mọi writer cập nhật stamp nhất quán**. Stamp `int` vẫn có giới hạn và có thể quay vòng nếu hệ thống chạy đủ lâu. `AtomicMarkableReference` giữ một cờ boolean kèm tham chiếu cho bài toán đánh dấu trạng thái cụ thể, không phải version counter tổng quát.

**Không phải mọi ABA là bug:** nếu thuật toán chỉ quan tâm giá trị hiện tại, việc quay lại giá trị cũ có thể hoàn toàn chấp nhận.

## 10. Strong CAS và weak CAS

`compareAndSet` thông thường của `AtomicInteger` và `AtomicReference` là **strong CAS**: khi trả `false`, expected không khớp tại lần thử tương ứng.

Một số phương thức **weak CAS** như `weakCompareAndSetPlain` có thể trả `false` dù expected khớp (**spurious failure**). Vì vậy không thể suy ra chắc chắn đã có writer khác chỉ từ một lần weak CAS trả false. Các biến thể weak CAS như `weakCompareAndSetAcquire`, `weakCompareAndSetRelease` còn có ngữ nghĩa bộ nhớ khác nhau; không thay tên phương thức mà bỏ qua hợp đồng ordering.

`compareAndSet` là lựa chọn dễ hiểu hơn cho đa số trường hợp. Weak CAS chỉ nên dùng khi biết rõ memory ordering và thuật toán retry cần thiết.

**Đừng nhầm** spurious failure của weak CAS với spurious wakeup của `Object.wait()` hoặc `Condition.await()`. Chúng là hai hiện tượng thuộc các API khác nhau.

## 11. CAS so với các primitive khác

| Primitive | Điểm mạnh | Điểm phải lưu ý |
| --- | --- | --- |
| `volatile` | Công bố/quan sát giá trị đơn lẻ theo ordering JMM | `counter++` không nguyên tử |
| `synchronized` | Bảo vệ critical section, có mutual exclusion và happens-before qua cùng monitor | Cần lock scope đúng, quản lý contention |
| `AtomicInteger.incrementAndGet` | Tăng nguyên tử, không cần tự viết loop | Phù hợp một biến số |
| `compareAndSet` | Chuyển trạng thái có điều kiện, cập nhật nguyên tử một vị trí | Phải xử lý thất bại |
| `AtomicReference<State>` | Công bố immutable snapshot nguyên tử | State bên trong phải thực sự bất biến hoặc có đồng bộ riêng |
| `LongAdder` | Có thể tăng throughput khi nhiều writer cộng dồn | `sum()` không phải snapshot nguyên tử dưới writer đồng thời |

Nếu chỉ tăng số, ưu tiên `incrementAndGet` cho rõ ràng. Nếu nhiều field phải đổi như một đơn vị, dùng một snapshot gốc hoặc lock. Với contention cao, CAS loop có thể tiêu tốn CPU do retry; **không mặc định CAS luôn nhanh hơn khóa**.

Khái niệm **lock-free** chỉ đúng khi **toàn thuật toán** bảo đảm có một operation nào đó tiến triển; không có nghĩa mọi thread chắc chắn thành công trong thời gian ngắn. **Wait-free** mạnh hơn: từng operation có bảo đảm giới hạn bước. CAS là nền tảng nhiều thuật toán không chặn, nhưng việc thấy một CAS trong method không chứng minh method đó lock-free.

## 12. CAS có cần xử lý interrupt hay hủy không?

Lời gọi `compareAndSet` không phải thao tác chờ khóa kiểu `lockInterruptibly()`, nên không tự ném `InterruptedException` để báo hủy. Một vòng retry CPU-bound có thể cần đọc `Thread.currentThread().isInterrupted()` hoặc một token hủy ở điểm an toàn:

```java
for (;;) {
    if (Thread.currentThread().isInterrupted()) {
        return; // Chưa commit lượt cập nhật này.
    }

    State before = ref.get();
    State after = calculateNext(before);

    if (ref.compareAndSet(before, after)) {
        return; // Lượt cập nhật đã commit.
    }
}
```

Đây là đoạn minh họa với `State`, `ref` và `calculateNext` do ứng dụng định nghĩa. Tín hiệu hủy có thể đến **ngay sau kiểm tra nhưng trước CAS**; nếu cần “cancel hoặc commit, không thể cả hai” ở một điểm logic, phải mô hình hóa trạng thái hủy trong cùng state machine hoặc dùng giao thức điều phối chặt hơn.

Dừng task sau một nửa thao tác nhiều bước có thể làm hỏng invariant. Chỉ phản hồi hủy ở điểm an toàn hoặc có rollback phù hợp. `Thread.interrupt()` không tự hoàn tác một CAS đã thành công.

## 13. CAS dùng cho state machine

Một use case dễ hiểu là chỉ cho phép task chuyển `NEW -> RUNNING -> DONE`. Nếu hai thread cùng đòi khởi động, chỉ một phép chuyển `NEW -> RUNNING` được phép thành công.

Tạo `CasStateDemo.java`:

```java
import java.util.concurrent.atomic.AtomicReference;

public class CasStateDemo {
    enum Status { NEW, RUNNING, DONE }

    public static void main(String[] args) {
        AtomicReference<Status> state =
                new AtomicReference<>(Status.NEW);

        boolean start = state.compareAndSet(Status.NEW, Status.RUNNING);
        boolean startAgain = state.compareAndSet(Status.NEW, Status.RUNNING);
        boolean finish = state.compareAndSet(Status.RUNNING, Status.DONE);

        System.out.println("start=" + start);
        System.out.println("startAgain=" + startAgain);
        System.out.println("finish=" + finish);
        System.out.println("state=" + state.get());
    }
}
```

```bash
javac CasStateDemo.java
java CasStateDemo
```

Kết quả:

```text
start=true
startAgain=false
finish=true
state=DONE
```

`enum` có constant là object duy nhất, phù hợp với identity CAS. Ví dụ giải thích các chuyển trạng thái theo một chuỗi đơn luồng; với nhiều thread phải thiết kế đầy đủ quy tắc `FAILED`, `CANCELED`, kết quả task và side effect ngoài JVM. Một CAS state thành công không tự có nghĩa toàn bộ nghiệp vụ đã xong.

## 14. Những API gần CAS

- `compareAndSet(e, u)` trả boolean: có commit khi expected khớp không?
- `compareAndExchange(e, u)` trả **giá trị được quan sát** tại điểm so sánh (witness), thay vì chỉ boolean. Có trong Java 9+.
- `getAndSet(u)` thay atomically bằng `u` và trả giá trị cũ, không kiểm tra expected.
- `getAndUpdate(fn)` và `updateAndGet(fn)` áp dụng hàm cập nhật với khả năng retry; khác nhau ở giá trị trả về.
- `VarHandle` cung cấp các thao tác atomic/memory ordering cấp thấp trên field/array element, hữu ích khi cần thiết kế đặc thù; với đa số bài toán, atomic wrapper dễ dùng hơn.

Không thay một API bằng API khác chỉ vì cả hai có chữ “set”: expected, giá trị trả về và memory ordering có thể khác nhau.

## 15. Những lỗi điển hình cần tránh

1. **Bỏ qua boolean:** gọi CAS rồi báo thành công dù nhận `false`.
2. **Retry với expected cũ:** không đọc lại state khiến loop có thể chạy mãi.
3. **Gọi side effect trong lambda retry:** tác vụ có thể lặp nhiều lần cho một lần commit.
4. **Lấy hai snapshot khác thời điểm:** đọc hai field qua hai lần `AtomicReference.get()` có thể ghép hai version.
5. **Dùng hai biến atomic cho một invariant:** mỗi biến nguyên tử riêng không bảo vệ toàn bộ nhóm.
6. **Nhầm equals và identity:** `AtomicReference.compareAndSet` so tham chiếu object.
7. **Bỏ qua ABA:** giá trị quay về cũ không chứng minh lịch sử không thay đổi.
8. **Tưởng CAS không bao giờ tốn CPU:** contention cao tạo nhiều lần thất bại, có thể cần backoff hoặc primitive khác.
9. **Bỏ qua overflow và chính sách hủy:** CAS không tự kiểm tra domain bound hay nhận interrupt.
10. **Đánh đồng CAS với giao dịch:** một update RAM không làm I/O, database hoặc message broker được commit nguyên tử cùng nó.

## 16. Tự kiểm tra

**Câu 1.** `AtomicInteger` có giá trị `4`. Gọi `compareAndSet(4, 5)` rồi `compareAndSet(4, 6)`. Kết quả?

**Trả lời:** `true` rồi `false`; giá trị cuối `5`.

**Câu 2.** Hai thread đọc cùng `0` rồi CAS `(0, 1)`. Nếu không có writer khác, cả hai có thành công không?

**Trả lời:** Không. Một thread thắng và đổi giá trị thành `1`. Thread kia phải retry với expected mới nếu muốn thực hiện lượt tăng riêng của nó.

**Câu 3.** `volatile int x` có làm `x++` an toàn với nhiều writer không?

**Trả lời:** Không. Visibility khác atomicity của toàn bộ read-modify-write.

**Câu 4.** Hai record có `equals()==true` nhưng `==false`; dùng record mới làm expected của CAS reference có đúng không?

**Trả lời:** Không nếu reference hiện tại giữ record còn lại. CAS reference so identity.

**Câu 5.** Hai số dư dùng hai `AtomicInteger` riêng có đủ bảo đảm tổng không đổi khi chuyển tiền không?

**Trả lời:** Không. Phải bảo vệ nhóm cập nhật/đọc hoặc gói thành một immutable snapshot thay bằng CAS trên root reference.

**Câu 6.** Vì sao hàm `updateAndGet` nên không có side effect?

**Trả lời:** Hàm có thể được gọi lại do contention, khiến side effect lặp nhiều lần.

**Câu 7.** ABA `A -> B -> A` có làm CAS sai về nguyên tử không?

**Trả lời:** Không. CAS xét giá trị tại thời điểm thực hiện; lỗi chỉ có nếu thuật toán cần lịch sử mà không lưu version.

**Câu 8.** `weakCompareAndSetPlain` trả false có luôn biểu thị thread khác đã sửa không?

**Trả lời:** Không. Weak CAS có thể thất bại giả.

**Câu 9.** CAS có tự bảo đảm thread bị hủy sẽ không commit tiếp không?

**Trả lời:** Không. Cần thiết kế điểm hủy và commit rõ ràng, có thể gộp trạng thái cancel trong một state machine atomic.

## 17. Tổng kết

**CAS = compare rồi set atomically nếu và chỉ nếu giá trị hiện tại còn khớp expected.** Để giữ mọi lần cập nhật dưới contention, dùng vòng **đọc lại → tính lại → CAS → retry nếu thất bại**. Một CAS thành công có điểm tuyến tính hóa rõ cho **một vị trí atomic**.

Trong Java, áp dụng đúng còn đòi hỏi hiểu **visibility/happens-before của JMM**, so sánh identity của `AtomicReference`, ABA, weak CAS và side effect khi retry. Khi invariant trải trên nhiều field, đóng gói chúng thành **immutable snapshot** rồi CAS một tham chiếu gốc, hoặc dùng khóa cùng giao thức; đừng dùng CAS như giải pháp thay thế cho mọi transaction hay mọi điều phối giữa thread.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-004">CAS loop, retry và contention</a>

<details>
<summary>Xem chi tiết</summary>

# CAS loop, retry và contention trong Java

**Compare-and-set (CAS)** là phép cập nhật nguyên tử có điều kiện: “chỉ thay giá trị hiện tại bằng giá trị mới nếu nó vẫn bằng giá trị mà tôi kỳ vọng”. Trong Java, CAS được cung cấp qua `AtomicInteger`, `AtomicLong`, `AtomicReference`, các lớp atomic khác và `VarHandle`. Từ CAS, ta có thể xây **vòng lặp thử lại** (*CAS loop*) để cập nhật trạng thái chia sẻ mà không cần mutex cho mỗi phép thay đổi nhỏ.

Tuy nhiên, khi nhiều thread cùng tranh một biến (*contention*), vòng CAS có thể phải tính lại và thử nhiều lần. Nó có thể tiết kiệm khóa ở tình huống thích hợp nhưng **không miễn phí, không mặc nhiên fair, không đảm bảo mỗi thread hoàn thành nhanh**, và còn có bẫy về side effects, ABA, overflow, memory ordering và việc đặt retry vô hạn trong request có deadline.

Bài viết tập trung vào **Java concurrency nói chung**, không dựa vào cơ chế của Spring hay framework.

## 1. CAS thực hiện chính xác điều gì?

Giả sử `state` là một `AtomicInteger` đang giữ số `10`:

```java
boolean ok = state.compareAndSet(10, 11);
```

Đây là snippet. CAS kiểm tra **tại điểm nguyên tử thực thi** rằng giá trị hiện tại có bằng `10` hay không. Nếu có, nó cập nhật thành `11` và trả `true`; nếu không, nó không ghi và trả `false`.

| State trước CAS | Operation | Trả về | State sau CAS |
| --- | --- | --- | --- |
| `10` | `compareAndSet(10, 11)` | `true` | `11` |
| `11` | `compareAndSet(10, 12)` | `false` | `11` |
| `11` | `compareAndSet(11, 12)` | `true` | `12` |

CAS gộp việc **so sánh và cập nhật chính một vị trí** thành một thao tác nguyên tử. Đoạn khác trông tương đương nhưng **không nguyên tử**:

```java
if (state.get() == 10) {
    state.set(11);
}
```

Đây là hai method calls riêng. Thread B có thể sửa state sau khi thread A `get()` nhưng trước khi A `set()`. Không nên gọi “field atomic” rồi giả định mọi workflow ghép các method atomic đều tự nhiên atomic.

**Ba thành phần của CAS:** vị trí lưu state, giá trị expected đã quan sát và giá trị proposed update. CAS **không** tự cam kết gì về một database, một file, một phép gọi mạng hoặc các field khác không thuộc vị trí atomic đó.

## 2. Vì sao phải viết CAS loop?

Một phép CAS có thể thất bại hợp lệ khi một thread khác đã cập nhật state trước. Thay vì bỏ cuộc, nhiều thuật toán thử lại từ snapshot mới:

```java
for (;;) {
    int oldValue = state.get();
    int newValue = transform(oldValue);

    if (state.compareAndSet(oldValue, newValue)) {
        return;
    }
    // Snapshot cũ không còn hợp lệ: đọc mới và tính lại.
}
```

Đây là **snippet minh họa**, giả định `state` là `AtomicInteger`, `transform` là một phép biến đổi thuần và code nằm trong method `void`. Trình tự:

1. **Read:** đọc state hiện tại.
2. **Validate/compute:** kiểm tra invariant và tính proposed state dựa vào giá trị vừa đọc.
3. **CAS:** thử cập nhật nguyên tử.
4. **Success:** hoàn tất đúng một lần cập nhật.
5. **Failure:** đọc lại, kiểm tra lại điều kiện, tính lại kết quả và thử lại.

**Sai lầm nghiêm trọng** là đọc `oldValue` một lần ở ngoài vòng rồi cứ dùng expected đó khi retry. Nếu state đã đổi, vòng có thể spin mãi hoặc áp dụng quyết định đã lỗi thời.

### Ví dụ: bán hàng còn tồn kho

```java
boolean sellOne(AtomicInteger stock) {
    for (;;) {
        int current = stock.get();
        if (current <= 0) {
            return false;
        }
        if (stock.compareAndSet(current, current - 1)) {
            return true;
        }
    }
}
```

Snippet method cần import `java.util.concurrent.atomic.AtomicInteger` và đặt trong class. Nếu hai thread cùng thấy `stock = 1`, chỉ một CAS `1 -> 0` thành công. Thread còn lại retry, đọc `0` và trả `false`. **Đọc lại điều kiện `current <= 0` là phần bắt buộc**: không thể mù quáng thử trừ từ snapshot cũ.

## 3. Retry là tín hiệu contention, không phải exception

Tưởng tượng hai thread cùng đọc version `7`:

```text
A: read version = 7
B: read version = 7
B: CAS(7 -> 8) succeeds
A: CAS(7 -> 8) fails
A: read version = 8
A: compute a new update
A: CAS(8 -> 9) succeeds
```

Khi A thất bại, có thể B **đã tiến triển**; không có exception nghiệp vụ. Trong nhiều thuật toán, đây là điều thường xuyên xảy ra và cần retry. Nhưng một lần retry cũng làm lãng phí ít nhất một số thao tác đọc, tính toán và thử CAS.

Một CAS mạnh `compareAndSet` trên `AtomicInteger` thất bại khi expected không khớp với state tại thời điểm phép thử. Lưu ý `compareAndSet` của `AtomicReference` so sánh **reference identity**, không phải `equals`; việc expected bằng hay khác tùy vào API cụ thể.

**Không phải CAS nào cũng phải retry vô hạn.** Một method cấp thấp có thể liên tục thử tới khi thắng; một request có SLA có thể cần retry budget, deadline, cancellation và kết quả “busy” khi quá tải. Nếu điều kiện nghiệp vụ thay đổi (hết tồn kho, trạng thái đã chuyển), retry có thể **kết thúc bằng false** mà không phải lỗi.

## 4. Java 17: test điều phối để chắc chắn thấy một CAS thất bại

Đếm failed CAS trong một stress test nhỏ có thể luôn ra `0` trên máy ít tải vì hai worker không thực sự tranh nhau. Để học cơ chế, ta cố tình dùng `CyclicBarrier` buộc **cả hai đọc cùng giá trị `0` trước khi ai được phép thử CAS đầu tiên**.

Tạo tệp `CASRetryDemo.java`:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CASRetryDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        CyclicBarrier bothRead = new CyclicBarrier(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> increment = () -> {
                int attempt = 0;
                for (;;) {
                    int oldValue = counter.get();

                    if (attempt == 0) {
                        bothRead.await(5, TimeUnit.SECONDS);
                    }

                    if (counter.compareAndSet(oldValue, oldValue + 1)) {
                        return null;
                    }

                    failures.incrementAndGet();
                    attempt++;
                }
            };

            Future<Void> first = pool.submit(increment);
            Future<Void> second = pool.submit(increment);

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);

            System.out.println("counter=" + counter.get());
            System.out.println("failedCAS=" + failures.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac CASRetryDemo.java
java CASRetryDemo
```

Stdout **khi cả hai worker đến barrier và hoàn thành trước timeout**:

```text
counter=2
failedCAS=1
```

**Vì sao xác định?** Hai worker đều đọc `0` trước barrier. Một CAS `compareAndSet(0,1)` thành công; lần CAS đầu của người còn lại thất bại vì state giờ là `1`. Worker thua đếm một thất bại, đọc lại `1` và CAS `1 -> 2` thành công. Ai thắng trước không ảnh hưởng kết quả. `Future.get` giúp main nhận exception worker và chỉ in khi cả hai xong.

Đây là **test chủ động tạo một interleaving**, không phải dự đoán retry trung bình dưới tải thực. Barrier tạo thêm quan hệ happens-before, nên không dùng chương trình để suy diễn về mọi outcome JMM trong code không có barrier. Timeout giúp test thất bại rõ khi participant vắng mặt, không phải cam kết mọi máy sẽ hoàn thành chính xác trong năm giây.

## 5. CAS và `volatile`: visibility không đủ cho read–modify–write

Khai báo:

```java
volatile int count;

void increment() {
    count++;
}
```

là snippet trong class. `volatile` giúp các lần truy cập có semantics về visibility/ordering, nhưng `count++` gồm read–modify–write về mặt logic. A có thể đọc `0`, B đọc `0`, rồi cả hai ghi `1`. Hai lần tăng nhưng kết quả chỉ `1`.

`AtomicInteger.incrementAndGet()` đã giải quyết phép tăng bằng operation atomic. Khi chỉ cần tăng hoặc cộng số:

```java
AtomicInteger count = new AtomicInteger();
int newValue = count.incrementAndGet();
int beforeAdd = count.getAndAdd(5);
```

Snippet cần import `AtomicInteger`. Nên dùng các operation đã có thay vì viết CAS loop thủ công cho phép cộng không có thêm điều kiện. Vòng CAS tùy chỉnh có ích nếu **toàn bộ điều kiện và update** cần dựa trên state hiện hành, ví dụ trừ quota chỉ nếu còn đủ.

`AtomicInteger.updateAndGet`, `getAndUpdate`, `accumulateAndGet` cũng cung cấp phép update theo function. **Function có thể được đánh giá nhiều lần khi contention**, nên phải thuần và không mang side effects không thể lặp an toàn.

## 6. Contention làm CAS loop tốn chi phí ở đâu?

**Contention** là nhiều thread cùng cạnh tranh cập nhật một state chia sẻ. Một biến atomic là **điểm nóng** khi nhiều core cùng làm phép read–modify–write.

Các dạng chi phí:

- **Failed CAS:** phải đọc lại state, tính lại proposed value; nếu phép tính đắt, tổng công việc bị lặp lớn.
- **Cache coherence:** một cache line cần chuyển quyền sở hữu khi nhiều core cập nhật cùng vùng nhớ, kể cả khi CAS thành công.
- **Busy-spinning:** retry liên tục giữ CPU bận, trong khi worker chưa tạo thêm một update thành công.
- **Tail latency:** một worker có thể thua CAS nhiều lần trước khi thắng; p95/p99 tăng dù throughput trung bình vẫn cao.
- **Starvation:** một thread liên tục bị thread khác vượt qua; CAS không mặc nhiên cho lịch công bằng FIFO.
- **False sharing:** hai biến logic độc lập nhưng tình cờ ở gần cùng cache line có thể gây ảnh hưởng cache coherence; tách điểm nóng hoặc dùng layout phù hợp đôi khi giúp, nhưng phải đo.

Càng nhiều thread **không nhất thiết** càng nhiều updates/giây. Có điểm bão hòa khi CPU, cache, coherence và bộ nhớ giới hạn. Đo failed attempts/update, throughput, latency và CPU theo số writer để nhận biết.

Một thất bại CAS không phải luôn là dấu hiệu bất lợi: trong thuật toán lock-free phù hợp, failure có thể đồng nghĩa **một thread khác đã tiến triển**. Vấn đề là mức thất bại và chi phí tổng thể so với mục tiêu throughput/latency.

### Đừng đo contention bằng cách tự tạo thêm contention

Nếu mỗi lần CAS fail đều cập nhật một `AtomicLong failedAttempts` chung, chính metric có thể trở thành hotspot! Trong benchmark, cân nhắc per-thread counters và tổng hợp sau khi hoàn tất; trong hệ thống thực, dùng sampling/tracing/metrics có chi phí kiểm soát.

## 7. Lock-free, wait-free và các bảo đảm tiến triển

Không nên dùng các từ này như đồng nghĩa của “có dùng CAS”.

| Khái niệm | Bảo đảm khái quát |
| --- | --- |
| **Blocking** | Một operation có thể chờ người khác nhả mutex hoặc hoàn thành công việc |
| **Obstruction-free** | Một thread có thể tiến triển khi nó chạy riêng đủ lâu không có contention |
| **Lock-free** | Hệ thống có tiến triển tổng thể; một thread riêng có thể cứ phải retry |
| **Wait-free** | Mỗi operation hoàn thành sau số bước hữu hạn bị chặn theo thuật toán, dù các thread khác tranh chấp |

CAS loop dùng `AtomicInteger` có thể là một phần thuật toán lock-free, nhưng **không tự động khiến toàn method lock-free** nếu body còn chờ latch, gọi I/O hoặc giữ monitor. Và lock-free **không** đảm bảo mỗi request thắng nhanh: một worker có thể bị starvation trong khi worker khác liên tục thành công.

Progress guarantee cần được phân tích ở cấp **toàn thuật toán**, cùng các giả định về scheduler và phần công việc khác; không thể kết luận chỉ từ việc nhìn thấy `compareAndSet`.

## 8. Java 17: CAS loop bảo vệ invariant tồn kho

Tạo `CASInventoryExample.java`, trong đó chỉ có một sản phẩm và hai thread cùng muốn mua:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CASInventoryExample {
    static class Inventory {
        private final AtomicInteger stock;

        Inventory(int initial) {
            if (initial < 0) {
                throw new IllegalArgumentException("negative stock");
            }
            this.stock = new AtomicInteger(initial);
        }

        boolean sellOne() {
            for (;;) {
                int current = stock.get();
                if (current == 0) {
                    return false;
                }
                if (stock.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }

        int remaining() {
            return stock.get();
        }
    }

    public static void main(String[] args) throws Exception {
        Inventory inventory = new Inventory(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Callable<Boolean> purchase = inventory::sellOne;
            Future<Boolean> first = pool.submit(purchase);
            Future<Boolean> second = pool.submit(purchase);

            int successful =
                    (first.get(5, TimeUnit.SECONDS) ? 1 : 0)
                  + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);

            System.out.println("successful=" + successful);
            System.out.println("remaining=" + inventory.remaining());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

```bash
javac CASInventoryExample.java
java CASInventoryExample
```

Stdout khi hoàn thành bình thường:

```text
successful=1
remaining=0
```

Bất kể thread nào thắng, chỉ một CAS `1 -> 0` thành công; worker còn lại đọc `0` hoặc retry rồi thấy `0` và trả false. Không có số lần retry cố định trong ví dụ này, vì scheduler có thể cho worker thứ hai bắt đầu sau khi stock đã hết.

**Phạm vi atomicity rất quan trọng:** đây là “tồn kho trong một JVM”, không phải transaction hoàn chỉnh giữa tồn kho, thanh toán, DB và hệ thống vận chuyển. CAS trong RAM không chống oversell nếu nhiều instance độc lập quản lý mỗi instance một counter và không phối hợp tại nguồn dữ liệu chung.

## 9. Một số mẫu CAS loop sai

### 9.1. Retry bằng expected đã lỗi thời

```java
int old = stock.get();
if (old == 0) return false;

while (!stock.compareAndSet(old, old - 1)) {
    // Lỗi: old không bao giờ được làm mới.
}
return true;
```

Snippet trong một method `boolean`. Nếu thread khác đổi stock, expected cũ có thể không bao giờ khớp lại, gây vòng spin vô hạn. Hãy **đọc lại state bên trong vòng** và kiểm tra lại điều kiện nghiệp vụ.

### 9.2. Cập nhật expected nhưng không kiểm tra lại invariant

```java
int old = stock.get();
for (;;) {
    if (stock.compareAndSet(old, old - 1)) {
        return true;
    }
    old = stock.get(); // Thiếu kiểm tra stock > 0.
}
```

Snippet sai vì có thể trừ `0 -> -1`. Cập nhật giá trị expected là cần nhưng **không đủ**; mỗi retry phải xem update có còn hợp lệ không.

### 9.3. Kiểm tra điều kiện trên field thường không đồng bộ

```java
if (account.isOpen()) {
    balance.compareAndSet(oldBalance, newBalance);
}
```

Nếu `isOpen()` phụ thuộc một state khác mà thread khác có thể thay đổi không phối hợp, phép CAS trên `balance` **không** làm nguyên tử cả điều kiện `isOpen` và cập nhật. Có thể cần chuyển mọi invariant vào một **immutable aggregate** được cập nhật bằng một `AtomicReference`, hoặc dùng lock chung/state machine thích hợp.

### 9.4. Bỏ qua overflow

`current + 1` với `int` có thể overflow sang âm. CAS không sửa lỗi toán học; khi ứng dụng yêu cầu counter không vượt `Integer.MAX_VALUE`, cần kiểm tra trước mỗi CAS hoặc chọn kiểu lớn hơn và vẫn định nghĩa ngưỡng hợp lệ.

## 10. Side effects trong retry: tuyệt đối không xem như chạy một lần

Đoạn sau **sai về nghiệp vụ**:

```java
for (;;) {
    int previous = balance.get();
    chargeExternalPayment(); // Có thể chạy nhiều lần!
    if (balance.compareAndSet(previous, previous - 1)) {
        return;
    }
}
```

Đây là snippet, chưa có kiểm tra số dư và chưa độc lập. Nếu CAS thua do thread khác sửa `balance`, vòng gọi `chargeExternalPayment()` lần nữa, có thể tính phí hai lần dù chỉ một cập nhật trong RAM được chấp nhận. Nếu payment API thành công nhưng response mất, việc retry còn phức tạp hơn.

**Quy tắc:** phần tính proposed state trong CAS loop nên là **pure function**, không chứa side effects không idempotent. Việc chuyển side effects ra sau CAS cũng chưa tự tạo exactly-once xuyên crash/network; cần transaction, idempotency key, outbox hoặc workflow bù trừ phù hợp.

Với `AtomicInteger.updateAndGet(x -> transform(x))`, `transform` có thể được gọi lại nhiều lần do contention. Đừng đặt gửi email, metric đếm “thành công”, ghi DB hoặc thay đổi collaborator mutable trong update function nếu không hiểu semantics lặp.

Đoạn minh họa thuần:

```java
AtomicInteger level = new AtomicInteger(3);
int capped = level.updateAndGet(x -> Math.min(x + 1, 10));
```

Update function chỉ tính toán trên input, không tác động ra ngoài. **Nhưng** một function “có vẻ thuần” mà lại đọc clock hoặc state mutable khác có thể cho ra proposed value khác nhau mỗi lần retry; cần định nghĩa rõ snapshot input của operation và trạng thái nào được phép đọc lại.

## 11. ABA: state quay về giá trị cũ nhưng đã có thay đổi ở giữa

CAS chỉ so sánh giá trị **ở thời điểm CAS**, không kiểm tra lịch sử. Ví dụ:

```text
A: read reference = object X
B: X -> Y
B: Y -> X
A: compareAndSet(expected=X, update=Z) succeeds
```

Nếu ứng dụng chỉ quan tâm reference hiện tại là X, điều này có thể chấp nhận được. Nhưng một thuật toán lock-free về node/resource có thể yêu cầu “node chưa hề bị tháo và đưa lại”; khi đó **ABA** là lỗi.

### Java 17: ví dụ ABA và version stamp

Tạo `ABADemo.java`:

```java
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;

public class ABADemo {
    public static void main(String[] args) {
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();

        AtomicReference<Object> plain = new AtomicReference<>(a);
        Object expected = plain.get();

        plain.compareAndSet(a, b);
        plain.compareAndSet(b, a);

        boolean plainAccepted =
                plain.compareAndSet(expected, c);
        System.out.println("plainAccepted=" + plainAccepted);

        AtomicStampedReference<Object> stamped =
                new AtomicStampedReference<>(a, 0);
        Object expectedRef = stamped.getReference();
        int expectedStamp = stamped.getStamp();

        stamped.compareAndSet(a, b, 0, 1);
        stamped.compareAndSet(b, a, 1, 2);

        boolean stampedAccepted =
                stamped.compareAndSet(expectedRef, c,
                                      expectedStamp, expectedStamp + 1);
        System.out.println("stampedAccepted=" + stampedAccepted);
        System.out.println("currentStamp=" + stamped.getStamp());
    }
}
```

```bash
javac ABADemo.java
java ABADemo
```

Stdout:

```text
plainAccepted=true
stampedAccepted=false
currentStamp=2
```

Ví dụ chạy tuần tự để mô phỏng lịch concurrent, do đó output xác định. `AtomicReference` so sánh reference bằng identity: object `a` đã quay lại **đúng instance cũ**, nên CAS của plain reference nhận. `AtomicStampedReference` kiểm tra đồng thời reference và version: state đã trải qua `stamp 0 -> 1 -> 2`, nên expected stamp cũ `0` bị từ chối.

Stamp không tự giải quyết mọi chuyện: version hữu hạn có thể wrap hoặc bị tái sử dụng nếu thiết kế thiếu kỷ luật, và quản lý node memory/reclamation của thuật toán không khóa vẫn cần phân tích riêng.

## 12. Strong CAS, weak CAS và memory ordering

`AtomicInteger.compareAndSet` là CAS mạnh theo hợp đồng API. Một số biến thể **weak CAS** như `weakCompareAndSetPlain` được phép **spurious failure**: trả `false` dù expected trùng state. Những method mang hậu tố `Plain`, `Acquire`, `Release` và dạng volatile còn khác nhau về **memory-order semantics**.

Điểm cần nhớ:

- Với strong `compareAndSet`, nếu expected không còn khớp thì operation fail. Một lần fail là bình thường dưới contention.
- Với weak CAS, loop phải chấp nhận fail ngay cả khi không có concurrent modification.
- Không đổi sang `weakCompareAndSetPlain` để “nhanh hơn” nếu thuật toán dựa vào volatile publication/happens-before mà chưa phân tích.
- `VarHandle.compareAndExchange` trả về **witness value** (giá trị đã quan sát), khác `compareAndSet` trả `boolean`. Đọc hợp đồng từng API trước khi dùng.
- Không mặc định weak CAS tốt hơn strong CAS trên mọi CPU/JDK; microbenchmark với workload thật khi tối ưu.

CAS trên `AtomicInteger` có memory effects được định nghĩa; operation mặc định tương ứng với semantics phù hợp cho atomic/volatile access. Nhưng **atomicity và visibility không gộp tất cả mutable objects khác thành một transaction**. Nếu tham chiếu atomic trỏ đến một object mutable và thread khác thay đổi phần tử bên trong, CAS reference không tự bảo vệ phần tử ấy.

## 13. Backoff, spin và `Thread.onSpinWait()`

Với contention nhẹ, retry ngay sau khi CAS fail thường đơn giản. Với contention nặng, hàng chục worker cùng spin vào một địa chỉ có thể làm tăng CPU/cache coherence và tail latency.

### Spin hint

Java 9+ hỗ trợ:

```java
int retries = 0;
for (;;) {
    int old = value.get();
    if (value.compareAndSet(old, old + 1)) {
        break;
    }
    retries++;
    if (retries > 16) {
        Thread.onSpinWait();
    }
}
```

Đây là snippet với `value` kiểu `AtomicInteger`. `Thread.onSpinWait()` là **hint cho JVM/CPU**, không phải yield có cam kết công bằng, không tạo timeout và không thay thế lock/condition. Nó có thể giúp một số spin-loop nhưng vẫn tiêu tốn CPU.

### Backoff

Một số chiến lược:

| Chiến lược | Ưu | Nhược |
| --- | --- | --- |
| Retry liên tục | Độ trễ thấp khi ít tranh chấp | Nóng CPU/cache khi nhiều writer |
| Fixed pause | Dễ đặt ngưỡng | Có thể trì hoãn vô ích khi contention giảm |
| Exponential backoff | Giảm tần suất thử nếu thất bại liên tiếp | Nếu tăng quá mức, latency cao |
| Random jitter | Tránh nhiều thread đồng bộ thức dậy cùng lúc | Tăng biến thiên latency và khó chứng minh fairness |
| Partition/shard state | Giảm nguồn contention | Tăng phức tạp tổng hợp/consistency |

`LockSupport.parkNanos(...)` có thể tạm park, nhưng **không bảo đảm ngủ đúng số nanos**: có thể trở lại vì interrupt/unpark/spurious return và scheduler ảnh hưởng thời gian. Backoff cũng **không** mang lại tính fair tuyệt đối.

### Pseudocode minh họa có backoff

```java
long pause = 1_000L;
for (;;) {
    int old = value.get();
    int next = transform(old);

    if (value.compareAndSet(old, next)) {
        return;
    }
    java.util.concurrent.locks.LockSupport.parkNanos(pause);
    pause = Math.min(pause * 2, 1_000_000L);
}
```

Đây là snippet giả định `value`, `transform` và một method `void`; **chưa xử lý cancellation, deadline, overflow và business-side effects**. Không dùng nguyên xi trong request production. Chọn backoff bằng số đo và semantics; đừng áp một khoảng nghỉ “chuẩn” cho mọi ứng dụng.

## 14. CAS loop có thời hạn và khả năng hủy

Một vòng retry vô hạn có thể phù hợp trong atomic class cấp thấp, nhưng trên đường request với SLA cần hợp đồng:

- **Thành công:** CAS thành công và invariant được áp dụng.
- **Từ chối có điều kiện:** state mới không còn đủ điều kiện (ví dụ stock = 0).
- **Bận/timeout:** retry vượt budget/deadline mà chưa cập nhật.
- **Hủy:** thread bị yêu cầu cancel; một CAS loop CPU-bound không tự ném `InterruptedException`.
- **Không chắc trạng thái external side effect:** không được đoán “chưa xảy ra” chỉ vì HTTP response mất; dùng idempotency/transaction.

Snippet giới hạn số lần thử:

```java
boolean tryIncrement(AtomicInteger value, int maxAttempts) {
    if (maxAttempts < 1) {
        throw new IllegalArgumentException("maxAttempts");
    }

    for (int n = 0; n < maxAttempts; n++) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }

        int current = value.get();
        if (value.compareAndSet(current, current + 1)) {
            return true;
        }

        Thread.onSpinWait();
    }
    return false;
}
```

Đây là snippet trong class, import `AtomicInteger`. `false` nghĩa **chưa cập nhật**, có thể do cancellation hoặc quá nhiều CAS fail; nếu API cần phân biệt hai trường hợp, trả enum/result thay boolean. Kiểm tra overflow trước CAS nếu invariant không cho phép tràn. Giới hạn attempts **không bảo đảm một thời gian thực cố định** vì scheduler có thể tạm dừng thread bất kỳ lúc nào.

### Khi retry backoff làm giảm throughput

Ở contention thấp, mỗi lần `parkNanos` có overhead, có thể làm tệ hơn retry trực tiếp. Ở contention cao, backoff có thể giúp tránh “thundering herd”. Mức tốt tùy CPU, JVM, access pattern, số core và chi phí tính proposed state. Không đo thì không kết luận.

## 15. `AtomicLong` hay `LongAdder` khi nhiều writer?

Nếu ứng dụng chỉ đếm số event, có hai lựa chọn tiêu biểu:

- `AtomicLong.incrementAndGet()`: operation tuyến tính hóa trên một biến; có thể đọc giá trị chính xác theo semantics của atomic operation.
- `LongAdder.increment()`: chia cập nhật qua nhiều cell nội bộ dưới contention để tăng throughput trong một số workload. `LongAdder.sum()` **không phải một snapshot nguyên tử đối với cập nhật đang đồng thời**.

Vì vậy `LongAdder` phù hợp với **metrics/statistics** nếu chấp nhận giá trị tổng tại thời điểm đọc không có một linearization point duy nhất; **không** phù hợp để thay `AtomicLong` trong “nếu số dư > 0 thì trừ” hoặc “chỉ cho phép tối đa N reservations” cần một quyết định atomic.

Không nên tự động chọn `LongAdder` cho một counter ít writer; memory và chi phí tổng hợp có thể cao hơn. Benchmark đúng workload.

## 16. Immutable snapshot + CAS khi nhiều field liên quan

Nếu state gồm `version` và `stock`, cập nhật hai field `AtomicInteger` riêng lẻ không tự tạo snapshot nguyên tử. Có thể gói chúng thành **immutable aggregate** và dùng `AtomicReference` CAS trên toàn reference:

```java
record Snapshot(int version, int stock) {}

AtomicReference<Snapshot> current =
        new AtomicReference<>(new Snapshot(0, 5));

for (;;) {
    Snapshot old = current.get();
    if (old.stock() == 0) {
        break;
    }

    Snapshot next =
            new Snapshot(old.version() + 1, old.stock() - 1);

    if (current.compareAndSet(old, next)) {
        break;
    }
}
```

Snippet Java 17 cần import `AtomicReference` và nằm trong method phù hợp. Mỗi reader lấy **một reference** sẽ quan sát version/stock cùng một snapshot. Khi CAS fail, writer phải tạo lại snapshot từ bản mới. Record có hai primitive nên bản thân snapshot này bất biến.

Nếu snapshot chứa `List<MutableItem>`, record không tự deep-copy phần tử; phải thiết kế immutable value/ownership đúng cách. Nếu nhiều writer có side effects ngoài state, một CAS reference cũng không tự làm toàn workflow nguyên tử xuyên nhiều hệ thống.

## 17. Benchmark và tìm nguyên nhân contention

Không dùng một phép đo đơn lẻ trên máy cá nhân để kết luận `compareAndSet` “nhanh hơn synchronized” trong mọi trường hợp. JIT warmup, GC, false sharing, CPU architecture, cache-line layout, number of cores và chi phí transform đều ảnh hưởng.

| Chỉ số | Hỏi điều gì? |
| --- | --- |
| Successful updates/s | Throughput thực tế có tăng khi thêm writer không? |
| Attempts per success | Một cập nhật phải thử trung bình bao nhiêu lần? |
| Failure ratio | Thất bại bao nhiêu trên toàn bộ attempts? |
| Latency p50/p95/p99 | Tail latency có tăng dưới contention không? |
| CPU utilization | Busy-spin có chiếm quá nhiều CPU không? |
| Fairness per worker | Có worker nào liên tục không được hoàn tất? |
| Coherence/cache metrics | Có hotspot hoặc false sharing không? |
| Correctness/invariant | Có lost update, stock âm hoặc side effect lặp không? |

**JMH** có cơ chế warmup, fork, measurement và tránh những lỗi benchmark micro phổ biến; dùng để so các operation có **cùng semantics**, nhiều mức thread count và workload đại diện. Kết hợp JFR/CPU profiling, stress test và load test hệ thống. Mục tiêu đầu tiên là **đúng invariant**, sau đó mới tối ưu.

Với benchmark CAS loop, hãy cẩn thận không đặt một atomic counter đo thất bại ở chính hotspot cạnh tranh; instrumentation có thể làm thay đổi kết quả. Đếm cục bộ trên mỗi worker hoặc sử dụng công cụ profiling phù hợp rồi tổng hợp.

## 18. ABA không phải vấn đề duy nhất của cấu trúc lock-free

Với một stack/queue tự thiết kế bằng CAS, còn nhiều câu hỏi ngoài primitive:

- **Node lifecycle:** node có được phép tái sử dụng hay bị tháo/đưa lại trong lúc thread khác còn reference cũ?
- **Memory visibility:** khi publish node mới, các field đã khởi tạo có thể được reader quan sát đúng không?
- **Invariant khi retry:** nếu cập nhật một pointer thất bại, có phải đọc lại cả chuỗi node đang quan sát?
- **Progress:** có thread nào có thể bị starvation; nếu worker thất bại/treo, các thread khác còn tiến triển không?
- **Memory reclamation:** loại bỏ node không đồng nghĩa có thể tái sử dụng an toàn tức thì trong mọi thuật toán; JVM GC quản lý memory vật lý nhưng không giải quyết mọi logical ABA/node reuse.
- **Exception/cancellation:** nếu tính proposed state ném exception, state vẫn đúng không?
- **Fairness:** nếu một thread phải retry mãi dưới cạnh tranh cao, service-level response nên làm gì?

Khi không có nhu cầu đặc biệt, ưu tiên các cấu trúc đã được kiểm chứng trong `java.util.concurrent` thay vì tự viết lock-free queue/stack. Một atomic CAS nhỏ là primitive an toàn; chứng minh một thuật toán lock-free hoàn chỉnh khó hơn nhiều.

## 19. Những sai lầm thường gặp

| Sai lầm | Hậu quả | Cách làm đúng |
| --- | --- | --- |
| Retry mãi với expected cũ | Spin vô hạn hoặc không tiến triển | Đọc state mới mỗi vòng |
| Update từ snapshot cũ | Ghi đè/logic sai | Đánh giá lại predicate và proposed state |
| Coi `get` + `set` là CAS | Check–then–act bị xen ngang | Dùng CAS hoặc operation atomic phù hợp |
| Dùng `volatile count++` | Lost update | `incrementAndGet` hoặc CAS đúng |
| Cho I/O/side effect vào retry | Có thể chạy nhiều lần | Tách effect, dùng transaction/idempotency |
| Callback trong `updateAndGet` không pure | Có thể có side effect lặp | Function thuần, an toàn khi re-evaluate |
| Không xử lý ABA khi cần version | CAS chấp nhận A→B→A | Version/stamp/ownership phù hợp |
| Nhầm weak CAS với strong CAS | Sai về spurious failure/memory ordering | Đọc đúng hợp đồng method |
| Gọi mọi CAS loop là wait-free | Che starvation | Phân tích progress toàn thuật toán |
| Spin mãi trong request có deadline | CPU cao, latency không kiểm soát | Retry budget/deadline/cancel |
| Luôn dùng backoff | Thêm latency dưới contention nhẹ | Benchmark/điều chỉnh theo tải |
| Một CAS chỉ bảo vệ một field nhưng invariant nhiều field | Snapshot không nhất quán | Immutable aggregate+CAS hoặc mutex |
| `LongAdder.sum()` thay atomic balance | Không phải atomic snapshot | Dùng atomic operation đủ mạnh |
| CAS trên JVM rồi coi như DB transaction | Quá bán hàng ở nhiều process | Đồng bộ tại nguồn dữ liệu/transaction |
| Instrumentation dùng một atomic hotspot | Đo làm tăng contention | Per-thread metrics/sampling |
| Quên integer overflow | Counter/quota sai | Kiểm tra ngưỡng trong mỗi vòng |
| Chỉ test một lịch chạy | Không thấy bug lạ | Stress+coordinated tests+JMM reasoning |

## 20. Chọn thiết kế theo tình huống

**Counter ít writer, cần giá trị chính xác theo từng operation:** `AtomicInteger`/`AtomicLong` method có sẵn; không cần tự CAS loop.

**Counter thống kê cực nhiều writer:** cân nhắc `LongAdder`, nếu chấp nhận `sum` không snapshot nguyên tử khi có updates đồng thời.

**Quota trong một JVM:** CAS loop với check-and-decrement; sau mỗi thất bại đọc lại quota và quyết định có còn được phép không.

**Immutable configuration:** publish snapshot qua `volatile`/`AtomicReference`; nếu nhiều writers cùng dựa vào old version, CAS có thể tránh lost update.

**Một nghiệp vụ nhiều field hoặc nhiều external resources:** cân nhắc `synchronized`, `ReentrantLock`, transaction, queue/ownership và idempotency. Một CAS trên biến cờ không tự bảo vệ mọi resource.

**Contention quá cao:** đo retry ratio và tail latency; cân nhắc chia state, batching, `LongAdder` khi semantics phù hợp hoặc một chiến lược backoff đã được đo. Không tăng threads để hy vọng “nhanh lên” nếu hotspot đã bão hòa.

## 21. Câu hỏi tự kiểm tra

**1. CAS failure có phải lỗi không?** Không mặc định. Với strong CAS, nó thường chỉ là expected không khớp do state đã thay đổi; thuật toán có thể retry hoặc trả kết quả nghiệp vụ khác.

**2. Sau CAS fail, có thể giữ nguyên expected và chỉ chạy lại `compareAndSet` không?** Không. Phải đọc lại và tính lại state đề xuất; nếu điều kiện không còn đúng, dừng hợp lệ.

**3. `AtomicInteger` có làm `get` rồi `set` thành một transaction không?** Không. Phải dùng operation atomic cho cả logic cần nguyên tử hoặc CAS loop đúng.

**4. Một worker liên tục thua CAS có mâu thuẫn với lock-free không?** Không. Lock-free hướng tới tiến triển tổng thể; wait-free mới đòi guarantee mạnh hơn cho từng operation.

**5. Vì sao không gửi email trong `updateAndGet(x -> ...)`?** Function có thể được gọi nhiều lần nếu CAS nội bộ retry, làm lặp side effect.

**6. `LongAdder` thay `AtomicLong` cho tồn kho được không?** Không nên: `sum()` không atomic snapshot với cập nhật đồng thời, nên không đủ cho check-and-decrement quyết định nghiệp vụ.

**7. `onSpinWait` có bảo đảm nhường CPU hay hẹn giờ không?** Không; chỉ là performance hint. Backoff có chính sách thời gian khác và cũng không tự đảm bảo fairness.

**8. ABA là gì?** State chuyển từ A sang B rồi trở lại A; CAS so expected=A có thể thắng dù lịch sử đã thay đổi. Khi cần version hãy dùng stamp/cấu trúc phù hợp.

**9. CAS loop có tự bảo vệ object mutable nằm trong `AtomicReference` không?** Không. Nó bảo vệ thay reference theo contract; data bên trong có thể cần immutable design/ownership.

**10. Test có output cố định `failedCAS=1` có chứng minh số retry luôn là 1 trong production không?** Không. Đó là interleaving được barrier cố ý dựng; thực tế tùy scheduling và contention.

**11. `compareAndSet` và `weakCompareAndSetPlain` có thể tráo nhau không?** Không tự động: weak CAS có thể fail giả và memory-order semantics có thể khác.

**12. Có cách nào bảo đảm CAS retry luôn kết thúc trong 5 ms không?** Không chỉ bằng primitive. Cần deadline/cancellation semantics, kiểm soát khối lượng công việc và scheduler; timer không làm update đúng tự động.

## 22. Checklist triển khai

1. Xác định **state và invariant**; chỉ dùng CAS nếu một atomic cell/reference có thể diễn đạt điều kiện cần bảo toàn.
2. Dùng API atomic chuẩn trước; đừng tự loop cho `incrementAndGet` nếu không có nhu cầu mới.
3. Trong mỗi retry: **đọc mới → kiểm tra mới → tính mới → CAS**.
4. Giữ update function **thuần**, không đặt I/O hoặc side effects không idempotent trong retry.
5. Phân biệt **CAS success**, condition failure, retry budget exhausted và cancellation.
6. Kiểm tra **overflow**, value/reference identity và **ABA** nếu trạng thái có version/lifetime.
7. Hiểu strong/weak CAS cùng memory-order semantics theo JMM.
8. Không gọi cả thuật toán “lock-free/wait-free” chỉ vì có CAS.
9. Đo throughput, retry ratio, CPU, p95/p99 và starvation khi contention tăng.
10. Cân nhắc backoff, sharding, batching, `LongAdder` hoặc lock/immutable state tùy nhu cầu; chọn bằng số đo.
11. Không suy ra atomicity xuyên DB/HTTP/process từ CAS trong một JVM.
12. Test với thread interleavings có chủ đích và stress test, nhưng lập luận đúng đắn vẫn dựa vào contract Java Memory Model.

**Ghi nhớ:** CAS là **optimistic concurrency**: chỉ cập nhật khi snapshot vẫn hợp lệ. Khi thất bại phải **đọc lại, tính lại và kiểm tra lại điều kiện nghiệp vụ**. CAS giúp tránh mutex cho một số state đơn giản nhưng contention có thể khiến retry tiêu tốn CPU và tăng độ trễ; thiết kế tốt kết hợp atomicity, liveness, memory semantics, pure retry logic và phép đo trên workload thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-005">getAndUpdate(), updateAndGet() và accumulator</a>

<details>
<summary>Xem chi tiết</summary>

# getAndUpdate(), updateAndGet() và accumulator trong Java Atomic & CAS

## Mục tiêu học tập

Khi nhiều thread cùng thay đổi một counter, ngưỡng giới hạn, giá trị lớn nhất hoặc một snapshot trạng thái, mẫu đọc–tính–ghi thông thường rất dễ gây **lost update**. Họ API `java.util.concurrent.atomic` hỗ trợ cập nhật dựa trên hàm mà không phải tự viết vòng lặp `compareAndSet` trong mỗi trường hợp. Nhưng tên gần giống nhau có thể gây nhầm giữa **giá trị trước khi cập nhật** và **giá trị sau khi cập nhật**; thêm nữa, thuật ngữ **accumulator** vừa chỉ các phương thức tích lũy trên atomic value, vừa chỉ các lớp như `LongAccumulator` và `DoubleAccumulator` với mục tiêu hiệu năng khác nhau.

Bài học giúp bạn:
- Dùng `getAndUpdate()` và `updateAndGet()` đúng với `UnaryOperator`, `IntUnaryOperator`, `LongUnaryOperator`.
- Phân biệt `getAndAccumulate(x, fn)` và `accumulateAndGet(x, fn)` với `BinaryOperator`; hiểu rõ thứ tự hai đối số.
- Giải thích cập nhật nguyên tử bằng **CAS**, thời điểm cập nhật có hiệu lực và vì sao callback có thể được tính lại nhiều lần.
- Tránh side effect trong callback, đếm sai thành công, overflow, tái sử dụng object mutable và suy diễn sai về tính nguyên tử của nhiều biến.
- Lựa chọn `AtomicInteger/AtomicLong/AtomicReference` so với `LongAdder/LongAccumulator/DoubleAccumulator` tùy nhu cầu **đúng tuyệt đối khi ra quyết định** hay **thống kê throughput cao**.
- Tự chạy ví dụ **Java 17+** minh họa unary update, binary accumulation, xử lý tồn kho cạnh tranh, snapshot immutable và accumulator thống kê.

**Phạm vi:** Java SE, Java Memory Model và concurrency nói chung, không đề cập các API hoặc cấu hình riêng của Spring vì chúng có chương độc lập.

## 1. Vì sao read–modify–write thường gặp race condition?

Giả sử hai thread cùng tăng một biến:

~~~java
int value = sharedCount;
sharedCount = value + 1;
~~~

Kể cả khi `sharedCount` là `volatile`, chuỗi **đọc → tính → ghi** vẫn gồm nhiều bước. Lịch chạy có thể là:

~~~text
Ban đầu sharedCount = 0

Thread A                Thread B
--------                --------
đọc 0
                        đọc 0
tính 1
                        tính 1
ghi 1
                        ghi 1

Đã có hai lần tăng, nhưng kết quả cuối có thể chỉ là 1.
~~~

**Volatile** mang bảo đảm visibility và ordering cho từng thao tác đọc/ghi tương ứng; nó **không biến** hai dòng trên thành một transaction. Nếu chỉ cần tăng một đơn vị, `AtomicInteger.incrementAndGet()` hoặc `getAndIncrement()` đã có semantics phù hợp. Nhưng nếu chuyển đổi phức tạp như “chỉ giảm nếu còn dương”, “đặt giá trị tối đa bằng giới hạn” hay “gộp snapshot theo một công thức”, các phương thức cập nhật bằng hàm rất hữu ích.

Một thay đổi atomic cần có **đơn vị trạng thái rõ ràng**:
- `AtomicInteger` cập nhật **một `int`**.
- `AtomicLong` cập nhật **một `long`**.
- `AtomicReference<V>` cập nhật **một tham chiếu `V`**; nếu `V` là record immutable chứa nhiều trường, một lần thay tham chiếu có thể công bố **một snapshot nhất quán**.
- `LongAccumulator`/`DoubleAccumulator` là những bộ tích lũy có thể **phân tán cập nhật nội bộ để giảm contention**, nên semantics đọc/reset khác với một atomic value đơn.

## 2. Bốn phương thức cốt lõi: unary và accumulator

| Phương thức | Hàm nhận gì? | Biến đổi logic | Giá trị trả về |
| --- | --- | --- | --- |
| `getAndUpdate(fn)` | Giá trị hiện tại | `new = fn(current)` | **Giá trị cũ**, trước lần cập nhật thành công |
| `updateAndGet(fn)` | Giá trị hiện tại | `new = fn(current)` | **Giá trị mới**, sau lần cập nhật thành công |
| `getAndAccumulate(x, fn)` | Giá trị hiện tại và `x` | `new = fn(current, x)` | **Giá trị cũ** |
| `accumulateAndGet(x, fn)` | Giá trị hiện tại và `x` | `new = fn(current, x)` | **Giá trị mới** |

Ở bảng này, “cũ” và “mới” là giá trị gắn với **thao tác atomic riêng đang thực hiện**. Nếu một thread khác cập nhật ngay sau đó, lần gọi `get()` kế tiếp có thể trả một giá trị khác; đừng nghĩ giá trị mà `updateAndGet` trả về nhất thiết vẫn là giá trị hiện hành vào thời điểm caller dùng nó.

### 2.1. Unary update

Với `AtomicInteger`, phương thức nhận một `IntUnaryOperator`:

~~~java
int previous = counter.getAndUpdate(current -> current * 2);
int updated = counter.updateAndGet(current -> current + 3);
~~~

`current` được thư viện đưa vào hàm ở mỗi lần thử cập nhật, **không** nhất thiết giống một lần `counter.get()` mà caller đã thực hiện trước đó. Tương tự, `AtomicLong` nhận `LongUnaryOperator`; `AtomicReference<V>` nhận `UnaryOperator<V>`.

Dấu **getAnd...** ở đầu tên nhắc rằng method trả giá trị **trước** phép thay đổi. Dấu **...AndGet** ở cuối tên nhắc rằng method trả giá trị **sau** phép thay đổi.

### 2.2. Binary accumulation

Với `AtomicInteger`:

~~~java
int previous = counter.getAndAccumulate(5, (current, x) -> current + x);
int updated = counter.accumulateAndGet(7, Math::max);
~~~

Khác unary update, hàm nhận **hai** đối số: **giá trị hiện tại trước**, **giá trị truyền vào method sau**. Với `AtomicLong` là `LongBinaryOperator`; với `AtomicReference<V>` là `BinaryOperator<V>`.

Đừng nhầm:

~~~java
// Đúng khi nghiệp vụ là current - delta:
counter.accumulateAndGet(4, (current, delta) -> current - delta);

// Ngược lại, phép này mang nghĩa delta - current:
counter.accumulateAndGet(4, (current, delta) -> delta - current);
~~~

Với phép giao hoán như cộng, lỗi đảo đối số thường khó phát hiện; với trừ, chia, ghép chuỗi hoặc combine record có thứ tự, nó đổi hoàn toàn ý nghĩa.

### 2.3. Atomic không đồng nghĩa tất cả callback chỉ chạy một lần

Các update method có thể phải thử lại khi CAS thất bại do thread khác vừa thay đổi giá trị. Vì vậy các tài liệu API yêu cầu update function **không có side effect**. Một lời gọi method chỉ có một kết quả cập nhật atomic, nhưng hàm do bạn truyền vào **có thể được tính lại**.

~~~text
Thread A                         Thread B
--------                         --------
đọc state = 10
tính fn(10) -> 11
                                 thay state: 10 -> 20 (CAS thành công)
CAS(10,11) thất bại
đọc lại state = 20
tính fn(20) -> 21
CAS(20,21) thành công

Trạng thái cuối: 21.
fn của A đã chạy hai lần dù chỉ một cập nhật của A thành công.
~~~

Đây là một lịch chạy **có thể xảy ra**, không phải cam kết mọi JVM phải gọi function hai lần. Thông thường thư viện lặp tới khi một phép cập nhật thành công; không bảo đảm một số lần thử tối đa khi contention.

## 3. Ví dụ Java 17: getAndUpdate khác updateAndGet

Tạo file **`UnaryUpdateDemo.java`**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class UnaryUpdateDemo {
    public static void main(String[] args) {
        AtomicInteger value = new AtomicInteger(10);

        int before = value.getAndUpdate(current -> current * 2);
        System.out.printf(
                "getAndUpdate returned=%d current=%d%n",
                before, value.get());

        int after = value.updateAndGet(current -> current + 3);
        System.out.printf(
                "updateAndGet returned=%d current=%d%n",
                after, value.get());
    }
}
~~~

Chạy:

~~~bash
javac UnaryUpdateDemo.java
java UnaryUpdateDemo
~~~

Stdout chính xác khi không có thread nào khác thay đổi `value`:

~~~text
getAndUpdate returned=10 current=20
updateAndGet returned=23 current=23
~~~

Diễn giải:
1. Bắt đầu là `10`. `getAndUpdate(current -> current * 2)` thay giá trị thành 20 nhưng **trả 10**.
2. `updateAndGet(current -> current + 3)` thay giá trị từ 20 thành 23 và **trả 23**.
3. Hai phép cập nhật đều atomic; ví dụ không có tranh chấp nên những lần `get()` sau đó thấy giá trị tương ứng.
4. Nếu có thread thứ ba cập nhật cùng `value` giữa method và `get()`, phần `current` được in ở lần `get()` sau có thể khác giá trị mà method trả về. **Atomicity chỉ áp dụng tại từng method**, không bao trọn cả câu lệnh in tiếp theo.

### Trường hợp dùng thực tế

- `getAndUpdate` phù hợp khi caller cần **giá trị trước** để xác định tác động của hành động, ví dụ claim một đơn vị tài nguyên nếu `previous > 0`.
- `updateAndGet` phù hợp khi caller muốn **snapshot sau khi thay đổi**, ví dụ tính giá trị hạn mức sau điều chỉnh.
- Nếu chỉ cần tăng/cộng một lượng cố định, `getAndIncrement`, `incrementAndGet`, `getAndAdd` và `addAndGet` thường ngắn, rõ và có thể hiệu quả hơn một lambda tổng quát.
- Đừng dùng `set(get() + delta)` để thay thế: thao tác gồm hai method riêng vẫn có lost update.

## 4. Ví dụ Java 17: getAndAccumulate khác accumulateAndGet

Tạo file **`BinaryAccumulateDemo.java`**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BinaryAccumulateDemo {
    public static void main(String[] args) {
        AtomicInteger number = new AtomicInteger(6);

        int before = number.getAndAccumulate(
                4, (current, multiplier) -> current * multiplier);

        System.out.printf(
                "getAndAccumulate returned=%d current=%d%n",
                before, number.get());

        int after = number.accumulateAndGet(5, Integer::sum);
        System.out.printf(
                "accumulateAndGet returned=%d current=%d%n",
                after, number.get());

        AtomicLong maximum = new AtomicLong(Long.MIN_VALUE);
        long oldMax = maximum.getAndAccumulate(-7L, Long::max);
        long newMax = maximum.accumulateAndGet(-2L, Long::max);

        System.out.printf(
                "maxBeforeIsIdentity=%b maxNow=%d returnedNew=%d%n",
                oldMax == Long.MIN_VALUE, maximum.get(), newMax);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BinaryAccumulateDemo.java
java BinaryAccumulateDemo
~~~

Stdout:

~~~text
getAndAccumulate returned=6 current=24
accumulateAndGet returned=29 current=29
maxBeforeIsIdentity=true maxNow=-2 returnedNew=-2
~~~

Giải thích:
- `getAndAccumulate(4, multiply)` gọi hàm với `current=6` và `multiplier=4` để tạo 24; giá trị trả về là **6**.
- `accumulateAndGet(5, Integer::sum)` cập nhật `24 + 5 = 29`, trả **29**.
- Với một phép lấy **max** khi có thể gặp giá trị âm, `Long.MIN_VALUE` là giá trị khởi tạo hợp lý. Nếu khởi tạo max bằng 0, tập toàn số âm sẽ cho kết quả sai.
- `Long::max` là một hàm tích lũy không cần biết lịch thực thi đối với mỗi cập nhật riêng, nhưng thứ tự các update thành công đồng thời vẫn không được caller dự đoán.

### Các ví dụ lambda thường dùng

~~~java
AtomicInteger bounded = new AtomicInteger(8);
bounded.updateAndGet(current -> Math.min(10, current + 5)); // thành 10

AtomicLong highWater = new AtomicLong(Long.MIN_VALUE);
highWater.accumulateAndGet(42L, Long::max); // thành 42

AtomicInteger remaining = new AtomicInteger(3);
int previous = remaining.getAndUpdate(
        current -> current > 0 ? current - 1 : current);
boolean claimed = previous > 0; // true khi lấy được 1 đơn vị.
~~~

Các đoạn trên là **snippet**, giả định đã import các class tương ứng hoặc dùng tên đầy đủ. Chúng không được trình bày như một chương trình độc lập. Trong ví dụ giới hạn `Math.min(10,current+5)`, nếu có thể nhận giá trị gần `Integer.MAX_VALUE` thì phép cộng `current+5` có nguy cơ overflow; khi đó cần chọn biểu thức chống overflow hoặc validate range theo nghiệp vụ.

## 5. Cơ chế CAS dưới lớp API

Với `AtomicInteger`, một cách **mô phỏng ý nghĩa** của `updateAndGet` là:

~~~java
static int updateExample(
        java.util.concurrent.atomic.AtomicInteger atomic) {
    while (true) {
        int observed = atomic.get();
        int desired = observed + 1;

        if (atomic.compareAndSet(observed, desired)) {
            return desired;
        }
    }
}
~~~

Đây là **phương thức minh họa**, có thể đặt trong class chứa logic tương ứng. Nó cho thấy:
1. **Đọc** giá trị đang quan sát.
2. **Tính** giá trị mới từ snapshot đó.
3. **CAS** chỉ ghi nếu state vẫn bằng giá trị kỳ vọng.
4. Nếu thất bại, **lặp lại** với state mới.

`getAndUpdate` tương tự về ý nghĩa nhưng trả giá trị `observed` của lần CAS thành công. Implementation thực tế có thể có tối ưu nội bộ, không buộc phải triển khai chính xác từng dòng như snippet này.

### Điểm tuyến tính hóa (linearization point)

Một lần update thành công có thể được suy luận như đã diễn ra nguyên tử tại thời điểm CAS thành công. Hai thread đều gọi `updateAndGet(x -> x + 1)` trên cùng một `AtomicInteger` sẽ không làm mất cập nhật, dù các callback có thể được tính nhiều lần.

**Nhưng:** phương thức chỉ bảo vệ atomicity của **một vị trí trạng thái**, không tự gộp thao tác với database, log hay một biến atomic khác. Nếu hai trường `available` và `sold` được cập nhật bằng **hai** atomic riêng, observer vẫn có thể thấy trạng thái trung gian giữa hai lần CAS.

### Visibility và ordering

Các phương thức cập nhật mạnh trên lớp atomic có hiệu ứng bộ nhớ được tài liệu hóa, liên quan đến `VarHandle.compareAndSet`. Không cần tự thêm `volatile` vào giá trị được lớp atomic quản lý. Tuy nhiên, khi trạng thái logic gồm những field bên ngoài object atomic, bạn vẫn phải xác định quan hệ happens-before và invariant cho những field đó. Không suy rằng một CAS trên counter tự “khóa” tất cả dữ liệu ứng dụng.

## 6. Quy tắc vàng: callback phải side-effect-free

### 6.1. Những điều không nên làm trong update function

Một cách dùng nguy hiểm:

~~~java
AtomicInteger remaining = new AtomicInteger(10);

// SAI: side effect có thể chạy nhiều lần khi CAS phải thử lại.
remaining.updateAndGet(current -> {
    chargeCustomer();        // Gọi thanh toán bên ngoài.
    sendNotification();      // Gửi thông báo bên ngoài.
    return current - 1;
});
~~~

Đây là **ví dụ sai có chủ đích**, không phải class chạy độc lập; `chargeCustomer` và `sendNotification` giả định tồn tại. Khi contention, callback có thể chạy lại, khiến thanh toán hoặc thông báo diễn ra nhiều lần dù chỉ một phép cập nhật atomic hoàn tất.

Các loại side effect cần tránh trong hàm update:
- Ghi file, ghi database, gọi mạng, gửi email/thanh toán.
- Sửa một object khác hoặc tăng counter ghi nhận “số lần thành công”.
- Ghi log “đã hoàn tất thành công” trong callback; có thể log cả lần CAS bị hủy.
- Sinh ID nghiệp vụ không được phép tạo lại hoặc consume một giá trị của queue.
- Dựa vào dữ liệu mutable không được đồng bộ, khiến callback thiếu tính xác định giữa các lần thử.

Một hàm tốt có thể viết như **hàm thuần**:

~~~java
current -> current > 0 ? current - 1 : current
(current, delta) -> current + delta
(previous, sample) -> Math.max(previous, sample)
~~~

Dù cùng hàm được gọi nhiều lần, nó chỉ tạo kết quả từ input và không thay đổi thế giới bên ngoài. **Không có bảo đảm callback được gọi đúng một lần**.

### 6.2. Side effect sau khi method trả về có an toàn tuyệt đối không?

Không tự động. Nếu một thread claim tài nguyên bằng cách giảm counter nguyên tử, một lời gọi method trả về có thể xác định **claim được hay không**; sau đó caller mới gửi yêu cầu nghiệp vụ. Nhưng nếu gửi yêu cầu thất bại, bị timeout hoặc retry, vẫn cần thiết kế idempotency, hoàn trả/bù trừ hoặc giao thức transaction riêng.

Atomic update là một thao tác **trong bộ nhớ JVM**. Nó không tự tạo transaction kết hợp với payment gateway, database hoặc hệ thống khác. Đối với sự kiện “chỉ được thanh toán đúng một lần”, thường phải có một trạng thái nghiệp vụ/ID idempotency an toàn ở hệ thống xử lý thanh toán, chứ không dựa duy nhất vào atomic local.

### 6.3. Exception trong callback

Nếu hàm cập nhật ném exception, **lần gọi này không hoàn tất cập nhật được tính từ callback lỗi**. Tuy nhiên, một thread khác vẫn có thể cập nhật atomic value trong khoảng thời gian đó; không được suy từ exception rằng toàn bộ trạng thái chung vẫn như ban đầu. Tránh hàm update làm một nửa side effect rồi ném lỗi, vì CAS không rollback những side effect đã xảy ra.

Với kiểm soát overflow:

~~~java
AtomicInteger amount = new AtomicInteger(Integer.MAX_VALUE);
amount.updateAndGet(current -> Math.addExact(current, 1));
~~~

Đây là snippet minh họa: `Math.addExact` ném `ArithmeticException` khi cộng tràn `int` thay vì âm thầm wrap. Các phép cộng trên `AtomicInteger` thông thường là cộng kiểu `int` Java, vốn có thể overflow theo biểu diễn hai bù. Nếu nghiệp vụ cần số không âm hoặc giới hạn chặt, phải đặt predicate và xử lý overflow trong hàm cập nhật.

## 7. Ví dụ Java 17: nhiều worker claim số lượng hữu hạn

Yêu cầu: có đúng 100 đơn vị tồn kho. Tám worker, mỗi worker thử bán 50 lần. **400 lần thử** cạnh tranh nhau nhưng không được bán quá 100; số tồn kho không âm.

Tạo file **`AtomicStockDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicStockDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger remaining = new AtomicInteger(100);
        ExecutorService pool = Executors.newFixedThreadPool(8);

        try {
            List<Future<Integer>> futures = new ArrayList<>();

            for (int worker = 0; worker < 8; worker++) {
                futures.add(pool.submit(() -> {
                    int successes = 0;
                    for (int i = 0; i < 50; i++) {
                        int old = remaining.getAndUpdate(
                                current ->
                                        current > 0 ? current - 1 : current);
                        if (old > 0) {
                            successes++;
                        }
                    }
                    return successes;
                }));
            }

            int sold = 0;
            for (Future<Integer> future : futures) {
                sold += future.get();
            }

            int rest = remaining.get();
            System.out.printf(
                    "sold=%d remaining=%d invariant=%b%n",
                    sold, rest, sold + rest == 100 && rest >= 0);
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac AtomicStockDemo.java
java AtomicStockDemo
~~~

Stdout khi cả tám task hoàn thành bình thường:

~~~text
sold=100 remaining=0 invariant=true
~~~

Tại sao deterministic?
1. `getAndUpdate` thực hiện cập nhật atomic với hàm `current > 0 ? current - 1 : current`.
2. Nếu giá trị **trước** cập nhật lớn hơn 0, đúng một claim thành công; nếu đã 0, số tồn giữ nguyên và claim thất bại.
3. Tổng 400 lần thử, vượt số lượng 100, nên sau mọi task kết thúc có 100 thành công và 300 thất bại; không quan trọng thread nào thắng.
4. `successes++` nằm **ngoài callback**, chỉ sau khi method atomic đã trả về, nên không đếm trùng khi CAS retry.
5. `Future.get()` chờ và phát hiện exception; số `sold` được cộng ở main sau khi task hoàn thành, không phải một atomic field thứ hai cần cập nhật đồng thời.

**Giới hạn:** snapshot `sold + remaining` chỉ được tính sau khi hoàn tất. Nếu hệ thống yêu cầu một thread khác đọc đồng thời **cả hai trường** và luôn thấy invariant `sold + remaining == 100`, phải gói cả hai trong **một state atomic** (ví dụ `AtomicReference` tới record immutable) hoặc dùng lock; hai biến atomic độc lập không tạo snapshot đa trường.

## 8. AtomicReference + record: accumulator cho một snapshot nhiều trường

`AtomicReference<T>` hỗ trợ cùng họ phương thức nhưng thao tác trên **một tham chiếu**. Khi `T` là value object immutable, ta có thể cập nhật nhiều trường một cách nhất quán bằng cách **thay toàn bộ snapshot**, không sửa object cũ.

Tạo file **`ReferenceAccumulatorDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ReferenceAccumulatorDemo {
    private record Stats(long count, long sum) {}

    public static void main(String[] args) throws Exception {
        AtomicReference<Stats> stats =
                new AtomicReference<>(new Stats(0, 0));

        Stats before = stats.getAndAccumulate(
                new Stats(1, 7),
                (current, delta) ->
                        new Stats(
                                current.count() + delta.count(),
                                current.sum() + delta.sum()));

        Stats after = stats.get();
        System.out.printf(
                "beforeCount=%d beforeSum=%d nowCount=%d nowSum=%d%n",
                before.count(), before.sum(),
                after.count(), after.sum());

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> tasks = new ArrayList<>();

            for (int i = 0; i < 5; i++) {
                tasks.add(pool.submit(() -> stats.updateAndGet(
                        current -> new Stats(
                                current.count() + 1,
                                current.sum() + 2))));
            }

            for (Future<?> task : tasks) {
                task.get();
            }

            Stats finalSnapshot = stats.get();
            System.out.printf(
                    "finalCount=%d finalSum=%d%n",
                    finalSnapshot.count(), finalSnapshot.sum());
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac ReferenceAccumulatorDemo.java
java ReferenceAccumulatorDemo
~~~

Stdout:

~~~text
beforeCount=0 beforeSum=0 nowCount=1 nowSum=7
finalCount=6 finalSum=17
~~~

Giải thích:
- `getAndAccumulate(new Stats(1,7), combine)` cập nhật từ `Stats(0,0)` sang `Stats(1,7)` và **trả object cũ**.
- Năm task tiếp tục dùng `updateAndGet` để tạo năm snapshot mới, mỗi snapshot cộng một vào count và hai vào sum. Kết quả cuối là `count=6`, `sum=7 + 5*2 = 17`.
- Hai field là kiểu `long` trong record và không thay đổi sau construction, nên **một lần `AtomicReference.get()`** trả về một phiên bản nhất quán để đọc cả hai.
- Hàm combine/update chỉ tạo object mới và không có side effect bên ngoài, nên có thể được tính lại an toàn khi CAS contention.

Một lỗi tinh vi cần tránh:

~~~java
long count = stats.get().count();
long sum = stats.get().sum();  // Có thể là phiên bản khác.
~~~

Nếu thread khác cập nhật giữa hai lần `get()`, hai biến được ghép từ **hai snapshot khác nhau**. Muốn có một snapshot nhất quán, phải lấy **một lần**:

~~~java
Stats snapshot = stats.get();
long count = snapshot.count();
long sum = snapshot.sum();
~~~

Hai đoạn này là snippet bên trong class có `Stats` và `stats` được khai báo; không phải chương trình chạy độc lập.

### Giới hạn khi dùng AtomicReference

- Atomicity là của **tham chiếu**. Nếu tham chiếu trỏ đến object mutable mà bên khác sửa trực tiếp, `AtomicReference` không tự làm những mutation đó atomic.
- Record có `List<Person>` hoặc array mutable **không tự deep-immutable**; cần defensive copy hoặc cấu trúc immutable phù hợp.
- Các callback `getAndUpdate/updateAndGet/getAndAccumulate/accumulateAndGet` có thể được gọi lại. Việc tạo value object mới không gây vấn đề logic, nhưng có allocation/GC cost dưới contention.
- Nếu hai object có cùng nội dung nhưng là hai tham chiếu khác nhau, các phép CAS trên `AtomicReference` làm việc dựa trên **tham chiếu kỳ vọng**. Tránh suy luận CAS dựa trên `equals()` thay vì equality của reference.
- Có những thuật toán CAS phức tạp gặp **ABA problem** (state chuyển A→B→A khiến giá trị quan sát có vẻ không đổi); nếu cần phiên bản/generation, có thể gói version vào immutable snapshot hoặc nghiên cứu `AtomicStampedReference`. Không mặc định mọi reference update đều gặp ABA.

## 9. Accumulator có hai ý nghĩa: method trên Atomic* và lớp LongAccumulator

Đây là chỗ dễ lẫn nhất khi đọc API.

**Nhóm 1 — method `getAndAccumulate/accumulateAndGet`:**
- Thuộc `AtomicInteger`, `AtomicLong`, `AtomicReference` và họ atomic array/updater tương ứng.
- Cập nhật **một giá trị/vị trí trạng thái** bằng một hàm binary.
- Trả giá trị **trước/sau** lần cập nhật atomic thành công.
- Phù hợp cả các quyết định check-update mạnh, ví dụ giới hạn tài nguyên hoặc một snapshot cần tính đúng theo phép cập nhật.

**Nhóm 2 — lớp `LongAccumulator`/`DoubleAccumulator`:**
- Dành cho **thống kê/tổng hợp giá trị khi nhiều thread cùng cập nhật**, có thể phân tán trạng thái nội bộ thành nhiều ô để giảm contention.
- Gọi `accumulate(x)` để đưa một giá trị mẫu vào; không trả về “giá trị cũ/mới” của mỗi lần gọi.
- Gọi `get()` để tổng hợp giá trị hiện hành từ các ô; **khi có cập nhật đồng thời, đây không phải snapshot atomic**.
- `reset()` và `getThenReset()` phù hợp với thời điểm không còn writer cập nhật; nếu gọi cạnh tranh với update, kết quả có thể không đại diện chính xác một ranh giới thời gian.
- Hàm tích lũy nên **side-effect-free**, và để có kết quả không phụ thuộc thứ tự thực thi, nên **kết hợp (associative) và giao hoán (commutative)**.

### 9.1. Lớp LongAccumulator dùng identity

Khởi tạo:

~~~java
LongAccumulator maximum =
        new LongAccumulator(Long::max, Long.MIN_VALUE);
~~~

Tham số thứ nhất là `LongBinaryOperator` kết hợp giá trị tích lũy hiện có và mẫu mới; tham số thứ hai là **identity element**.

Một identity tốt phải thỏa:

~~~text
combine(identity, x) == x
combine(x, identity) == x
~~~

Đối với `max` trên toàn bộ miền `long`, `Long.MIN_VALUE` là identity phù hợp. Đối với `sum`, identity là 0; đối với tích thì identity là 1, nhưng phải xét overflow.

Nếu dùng `new LongAccumulator(Long::max, 0)` và mọi mẫu đều âm, kết quả sẽ sai vì 0 lớn hơn mọi mẫu. **Identity không phải “giá trị mặc định tùy ý”.**

### 9.2. Thứ tự tích lũy không được bảo đảm

Với `LongAccumulator`, JVM có thể phân tán update giữa nhiều ô nội bộ, rồi kết hợp lại. Bởi vậy phép toán như max, min, sum số nguyên (theo quy tắc overflow của Java nếu có) hợp với thứ tự bất kỳ. Phép trừ, ghép text có thứ tự, hoặc một hàm tính theo sequence thường **không phù hợp**. Nếu cần áp dụng một trình tự nghiệp vụ cụ thể, sử dụng owner tuần tự hoặc một giao thức có thứ tự rõ ràng.

**Floating-point:** phép cộng `double` không kết hợp chính xác theo số thực vì rounding và đặc thù IEEE 754. Với `DoubleAccumulator` hoặc `DoubleAdder`, thay đổi thứ tự gộp có thể tạo sai lệch làm tròn; không hứa kết quả bit-for-bit giống một phép cộng tuần tự theo thứ tự cố định. Đối với tiền, cân nhắc biểu diễn đơn vị nguyên nhỏ nhất hoặc `BigDecimal` với giao thức đồng bộ thích hợp, thay vì dùng `double` như một số tiền chính xác tuyệt đối.

## 10. Ví dụ Java 17: LongAccumulator cho số liệu nhiều thread

Tạo file **`LongAccumulatorDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAccumulator;

public class LongAccumulatorDemo {
    public static void main(String[] args) throws Exception {
        LongAccumulator maximum = new LongAccumulator(
                Long::max, Long.MIN_VALUE);
        LongAccumulator sum = new LongAccumulator(Long::sum, 0L);

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> jobs = new ArrayList<>();

            for (int worker = 0; worker < 4; worker++) {
                final int start = worker * 25 + 1;
                jobs.add(pool.submit(() -> {
                    for (int number = start; number < start + 25; number++) {
                        maximum.accumulate(number);
                        sum.accumulate(number);
                    }
                }));
            }

            for (Future<?> job : jobs) {
                job.get();
            }

            System.out.printf(
                    "max=%d sum=%d%n", maximum.get(), sum.get());

            long previousMax = maximum.getThenReset();
            System.out.printf(
                    "beforeReset=%d identityRestored=%b%n",
                    previousMax, maximum.get() == Long.MIN_VALUE);
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                pool.shutdownNow();
            }
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac LongAccumulatorDemo.java
java LongAccumulatorDemo
~~~

Stdout chính xác sau khi mọi task hoàn thành bình thường:

~~~text
max=100 sum=5050
beforeReset=100 identityRestored=true
~~~

Lý do kết quả xác định:
- Bốn worker xử lý các đoạn số nguyên `1..25`, `26..50`, `51..75` và `76..100`. Tất cả mẫu từ 1 tới 100 được đưa vào đúng một lần.
- `Long::max` và `Long::sum` là các phép phù hợp để tổng hợp không phụ thuộc thứ tự cho tập dữ liệu này; `1+2+...+100 = 5050`.
- `Future.get()` xác nhận mọi job đã xong trước khi main gọi `get()`.
- `getThenReset()` được gọi **sau khi không còn concurrent update**, nên nhận max 100 rồi đặt lại về identity `Long.MIN_VALUE`. Nếu gọi trong lúc writer vẫn chạy, API không bảo đảm một ranh giới cập nhật chính xác.

**Không dùng `LongAccumulator` để quyết định “chỉ bán nếu còn tồn kho” theo dạng `if (accumulator.get() > 0) accumulator.accumulate(-1)`.** `get()` không phải snapshot nguyên tử khi có update đồng thời và chuỗi check-then-accumulate cũng không atomic. Với resource quota, dùng `AtomicInteger.getAndUpdate`, CAS hoặc lock để bảo vệ **cả predicate và thay đổi**.

## 11. So sánh AtomicLong, LongAdder và LongAccumulator

| Tiêu chí | `AtomicLong` | `LongAdder` | `LongAccumulator` |
| --- | --- | --- | --- |
| Giá trị logic | Một `long` nguyên tử | Tổng tích lũy qua nhiều ô nội bộ | Tích lũy qua nhiều ô với hàm tùy chọn |
| Update nổi bật | `incrementAndGet`, `getAndUpdate`, `accumulateAndGet` | `add`, `increment`, `decrement` | `accumulate(x)` |
| Có trả old/new tại mỗi update không? | **Có** với các method tương ứng | **Không** | **Không** |
| Đọc khi writer cạnh tranh | `get()` là đọc atomic của một giá trị | `sum()` không phải snapshot atomic | `get()` không phải snapshot atomic |
| Tránh lost update cho một phép read-modify-write? | **Có** qua update/CAS | Tích lũy số liệu an toàn, không cung cấp check-then-act atomic | Tích lũy số liệu an toàn, không cung cấp check-then-act atomic |
| Hàm tích lũy | Unary/binary operator trên một state | Phép cộng chuyên dụng | Custom `LongBinaryOperator` |
| Tình huống phù hợp | Counter chính xác, quota, high-water mark có trả giá trị | Counter thống kê nhiều writer | Max/min/sum hoặc metric associative/commutative nhiều writer |
| Trade-off | CAS contention trên một giá trị | Nhiều ô, chi phí bộ nhớ, đọc aggregate | Nhiều ô, chi phí bộ nhớ, cần identity/function đúng |

`LongAdder` tương ứng với trường hợp cộng của `LongAccumulator` với identity 0 về mặt chức năng. Dưới contention cao, các adder/accumulator dạng phân tán thường đạt throughput cập nhật tốt hơn một atomic counter tập trung, nhưng phải chấp nhận giới hạn đọc/reset và chi phí bộ nhớ. **Không có một lớp nào luôn nhanh nhất** cho mọi workload; đo bằng benchmark phù hợp và xem đúng workload có cần một giá trị linearizable hay không.

### Khi cần nhiều metric cùng snapshot

Giả sử một hệ thống dùng hai `LongAdder` cho `requests` và `errors`. Sau này muốn đọc “tỉ lệ lỗi” bằng cách gọi `sum()` trên từng adder, hai lần đọc có thể thuộc **hai thời điểm khác nhau**; không phải một transaction thống nhất. Với dashboard chấp nhận approximation, điều đó có thể ổn. Với quyết định nghiệp vụ đòi exact consistency của nhiều trường, nên dùng một immutable record trong `AtomicReference`, lock hoặc snapshot protocol khác.

## 12. Một số nhầm lẫn quan trọng

### 12.1. Dùng getAndUpdate khi cần giá trị sau cập nhật

~~~java
int updated = counter.getAndUpdate(x -> x + 1);
~~~

Tên biến `updated` gây hiểu sai: phương thức trả giá trị **cũ**. Hãy dùng `updateAndGet` nếu cần số mới, hoặc đặt tên `previous` và xử lý theo ý nghĩa cũ.

### 12.2. Tưởng callback “đã được gọi” nghĩa là phép cập nhật “đã thành công”

~~~java
counter.updateAndGet(current -> {
    successCount++;
    return current + 1;
});
~~~

Sai nếu `successCount` là biến được cập nhật từ callback: CAS có thể thất bại và function bị gọi lại. Cũng có data race đối với `successCount++` nếu nhiều thread cùng sửa. Chuyển logic đếm thành công ra **sau khi method trả về**, hoặc thiết kế trạng thái tổng hợp trong một atomic snapshot nếu cần invariant chặt.

### 12.3. Dùng hai atomic riêng cho một invariant

~~~java
available.updateAndGet(x -> x - 1);
sold.updateAndGet(x -> x + 1);
~~~

Mỗi method atomic riêng, nhưng có khoảng thời gian giữa hai method. Một observer có thể thấy `available + sold` tạm sai. Muốn bảo vệ invariant đa trường, dùng lock chung hoặc `AtomicReference` tới immutable record và một lần update toàn snapshot.

### 12.4. Đổi chiều đối số accumulator

~~~java
balance.accumulateAndGet(amount, (current, delta) -> current - delta);
~~~

Hàm nhận **current trước, amount sau**; viết ngược thứ tự khi dùng phép trừ sẽ đổi phép tính. Với `LongAccumulator`, phép trừ cũng không phù hợp làm hàm tổng hợp không phụ thuộc thứ tự.

### 12.5. Gắn side effect vào hàm CAS

Không gọi thanh toán, I/O, sinh business ID dùng một lần, hoặc ghi log “thành công” trong callback. Một phép update thành công **không** ngụ ý callback chỉ chạy một lần.

### 12.6. Hiểu sai identity của LongAccumulator

`new LongAccumulator(Long::max, 0)` không đúng cho mọi input có thể âm. `Long.MIN_VALUE` mới là identity tự nhiên của `max` trên miền `long`.

### 12.7. Dùng LongAccumulator.get() để kiểm soát quota

`get()` khi có update đồng thời không là snapshot nguyên tử; compound action “đọc số còn lại rồi trừ” có race. Dùng `AtomicInteger/AtomicLong` với hàm cập nhật có predicate hoặc locking.

### 12.8. Cộng số nguyên mà bỏ qua overflow

`AtomicInteger.addAndGet` và `updateAndGet(x -> x + amount)` không tự ném exception khi overflow `int`; phép cộng theo Java có thể wrap. Dùng `Math.addExact` hoặc predicate kiểm tra giới hạn nếu overflow là lỗi nghiệp vụ.

### 12.9. Dùng immutable reference nhưng sửa object phía trong

`AtomicReference<List<String>>` chỉ cập nhật **tham chiếu** nguyên tử; không biến `ArrayList.add` thành thao tác thread-safe. Nếu muốn snapshot immutable, dùng value type/defensive copy và publish bản mới.

### 12.10. Gọi getThenReset khi vẫn có writers

`LongAccumulator.getThenReset()` không tạo ranh giới nguyên tử cho các writer đang chạy. Có thể bỏ hoặc gộp sai update tương ứng thời điểm chuyển kỳ thống kê; nếu cần ranh giới kỳ chính xác, điều phối writer dừng/quiescent hoặc thiết kế giao thức chuyển kỳ riêng.

## 13. Chọn API theo tình huống

**Tình huống A — cần trả giá trị cũ** khi cấp phát thứ tự hoặc claim slot: cân nhắc `getAndUpdate` / `getAndAccumulate`; với tăng 1 đơn giản có `getAndIncrement`.

**Tình huống B — cần trả giá trị sau cập nhật**: `updateAndGet` / `accumulateAndGet`; với cộng cố định có `addAndGet`.

**Tình huống C — chuyển đổi dựa trên một trạng thái**, ví dụ clamp vào biên, monotonic max, giảm có điều kiện: `getAndUpdate` hoặc `updateAndGet` với callback pure.

**Tình huống D — có thêm một giá trị đầu vào `x`**, ví dụ số lượng cộng thêm, mẫu đo mới, max với observation mới: `getAndAccumulate` hoặc `accumulateAndGet`; bảo đảm hàm nhận (`current`, `x`) đúng thứ tự.

**Tình huống E — state có nhiều trường nhưng cần snapshot nhất quán**: immutable record + `AtomicReference.updateAndGet` hay `accumulateAndGet`. Reader phải lấy **một snapshot một lần**, và không để reference tới nested mutable state lọt ra.

**Tình huống F — metric thống kê nhiều writers, không cần old/new mỗi update**: `LongAdder` (sum) hoặc `LongAccumulator` (max/min/custom). Chấp nhận `sum/get` khi cập nhật đồng thời không phải snapshot atomic.

**Tình huống G — giao dịch cập nhật nhiều tài nguyên ngoài JVM**: không thể “dùng một CAS” là xong; cần giao thức transaction/idempotency hoặc coordination của chính các hệ thống đó.

## 14. Checklist khi review code Atomic/CAS

1. **Invariant là gì?** Một biến atomic hay một tập trường cần cập nhật thống nhất?
2. **Caller cần old hay new?** Xác định rõ method trả về, đặt tên biến `previous/updated` nhất quán.
3. **Unary hay binary?** Unary nhận current; binary nhận current rồi x.
4. **Callback có pure không?** Không I/O, không gọi thanh toán, không ghi state ngoài atomic, không giả định gọi một lần.
5. **Callback có thể được retry an toàn không?** Chịu được contention và tính toán lại với current mới.
6. **Có overflow không?** Xử lý `int/long` theo yêu cầu nghiệp vụ; chọn `Math.addExact` nếu cần báo lỗi.
7. **Reference có trỏ tới immutable graph không?** Record có field chứa list/array mutable cần defensive copy.
8. **Đọc nhiều field có dùng cùng snapshot không?** Không gọi hai lần `state.get()` rồi trộn hai phiên bản.
9. **Side effect sau claim có thể thất bại không?** Thiết kế bù trừ/idempotency theo hệ thống ngoài JVM.
10. **Có dùng LongAdder/LongAccumulator để check-then-act không?** Nếu có, xem lại vì `get/sum` không phải ranh giới atomic khi concurrent writers.
11. **Hàm accumulator có identity đúng không?** Kiểm tra đối với số âm, giá trị biên và tập rỗng.
12. **Cần kết quả bất biến theo thứ tự không?** Với LongAccumulator, chọn toán tử associative/commutative; chú ý `double` rounding.
13. **Sau concurrent tasks có chờ và kiểm tra exception không?** Với executor, gọi `Future.get()` để không che lỗi.
14. **Đã đo hiệu năng trên workload thật chưa?** Xem throughput thành công, p95/p99 latency, contention, retry/GC và số lượng writer; không khẳng định “lock-free luôn nhanh”.
15. **Kết quả đưa ra có mô tả rõ boundary không?** Giá trị trả về từ một cập nhật atomic không đồng nghĩa snapshot nhất quán của toàn ứng dụng.

## 15. Câu hỏi tự kiểm tra

1. `AtomicInteger(10).getAndUpdate(x -> x * 2)` trả gì, giữ gì? **Trả 10, giữ 20**.
2. `AtomicInteger(10).updateAndGet(x -> x * 2)` trả gì? **Trả 20**, giá trị mới.
3. Với `getAndAccumulate(4, (a,b) -> a-b)`, đối số nào là state hiện tại? **`a`**, còn `b=4`.
4. Tại sao `counter.set(counter.get()+1)` vẫn có lost update? **Vì đọc và ghi tách thành hai operation**.
5. Tại sao callback của `updateAndGet` không được gửi email? **CAS có thể retry và callback được tính lại**.
6. Một thread khác đổi atomic value sau `updateAndGet` nhưng trước `get()`. Hai kết quả có thể khác nhau không? **Có**, vì mỗi method có linearization riêng.
7. `LongAccumulator.accumulate(x)` có trả giá trị old/new giống `AtomicLong.accumulateAndGet` không? **Không**, nó trả `void`.
8. `LongAccumulator.get()` có là snapshot atomic khi các writer vẫn đang cập nhật không? **Không**.
9. Tại sao `LongAccumulator(Long::max, 0)` có thể sai? **Nếu toàn bộ sample âm, 0 sẽ luôn thắng**.
10. Khi nào có thể gọi `LongAccumulator.getThenReset()` để đọc một kỳ đáng tin? **Khi đã điều phối không còn concurrent updates trong ranh giới đó**.
11. Hai `AtomicInteger` cho `available/sold` có đảm bảo observer thấy `available+sold==initial` ở mọi thời điểm không? **Không, phải cập nhật cả cặp theo một giao thức atomic chung**.
12. Dùng `AtomicReference<Stats>` với record immutable mang lại lợi ích gì? **Một lần thay tham chiếu công bố toàn snapshot, reader lấy một tham chiếu rồi đọc các field cùng phiên bản**.
13. `AtomicReference.compareAndSet` so sánh reference hay gọi `equals()` của object? **Tham chiếu kỳ vọng theo identity**.
14. Vì sao `LongAccumulator` cần hàm associative và commutative để có kết quả không phụ thuộc lịch cập nhật? **Thứ tự tích lũy giữa các ô/threads không được bảo đảm**.
15. `DoubleAccumulator` có đảm bảo tổng số thực bit-for-bit không phụ thuộc thứ tự không? **Không, floating-point rounding có thể làm kết quả khác**.
16. Với counter rất đơn giản cần tăng một lần, có nhất thiết dùng lambda không? **Không; `incrementAndGet` hoặc `getAndIncrement` rõ hơn**.

## Kết luận

`getAndUpdate()` và `updateAndGet()` đều **cập nhật nguyên tử bằng một hàm unary**, nhưng một method trả **giá trị cũ**, method kia trả **giá trị mới**. `getAndAccumulate(x,fn)` và `accumulateAndGet(x,fn)` làm tương tự với một hàm binary theo thứ tự đối số **(current, x)**. Các callback phải **không có side effect**, vì có thể bị gọi lại khi CAS cạnh tranh.

Đừng nhầm các method atomic đó với lớp `LongAccumulator`/`DoubleAccumulator` chuyên cho **tích lũy thống kê nhiều writers**: chúng không trả old/new tại mỗi update và phép đọc/reset khi đang cập nhật không phải snapshot atomic. Chọn API dựa trên invariant, nhu cầu trả về, khả năng đọc nhất quán và kết quả cần chính xác; chỉ tối ưu hiệu năng sau khi đã chứng minh đúng dưới concurrency.

## Tài liệu tham khảo Java SE 17

- [AtomicInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): `getAndUpdate`, `updateAndGet`, `getAndAccumulate`, `accumulateAndGet`.
- [AtomicLong](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html): cập nhật kiểu `long` và memory semantics.
- [AtomicReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html): unary/binary update của reference.
- [LongAccumulator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAccumulator.html): identity, accumulation order, get/reset.
- [LongAdder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html): thống kê tổng khi nhiều writer.
- [DoubleAccumulator](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/DoubleAccumulator.html): tích lũy double và giới hạn do thứ tự làm tròn.
- [Java Language Specification 17, Chapter 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Threads and Locks; happens-before.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-006">Atomic compound reference qua immutable state</a>

<details>
<summary>Xem chi tiết</summary>

# Atomic compound reference qua immutable state

Trong lập trình Java concurrency, một thao tác thường phải thay đổi **nhiều trường cùng lúc** để giữ một bất biến nghiệp vụ. Ví dụ: giảm số suất còn trống và tăng số suất đã đặt; chuyển tiền giữa hai số dư; hoặc cập nhật danh sách cấu hình cùng phiên bản. Hai phép ghi độc lập vẫn tạo ra **khoảng thời gian ở giữa** để thread khác quan sát trạng thái dở dang, dù mỗi trường sử dụng `AtomicInteger`, `volatile` hay getter thread-safe.

**Atomic compound reference qua immutable state** là kỹ thuật gói toàn bộ những trường cần nhất quán vào **một object immutable**, rồi quản lý **một tham chiếu duy nhất** bằng `AtomicReference<State>`. Writer đọc snapshot hiện hành, tạo snapshot mới và dùng `compareAndSet(old, next)` để **thay một tham chiếu nguyên tử**. Reader chỉ cần lấy một snapshot bằng `get()` và đọc mọi component từ chính snapshot đó.

Mục tiêu là bảo đảm:
- **Reader không thấy trạng thái cập nhật một nửa** trong nhóm trường được gói cùng một snapshot.
- **Writer không làm mất cập nhật** của writer khác nếu kiểm tra điều kiện, tính trạng thái mới và commit bằng vòng CAS đúng cách.
- **Đối tượng cũ không bị sửa**, nên những thread còn đang cầm snapshot cũ vẫn đọc một phiên bản nhất quán.

Tất cả cơ chế trong bài là Java core (`java.util.concurrent.atomic` và Java Memory Model), không phụ thuộc framework.

## 1. Vì sao nhiều atomic field vẫn có race ở cấp invariant?

Giả sử lớp Inventory quản lý hai số:

```text
available >= 0
reserved >= 0
available + reserved == totalCapacity
```

Cập nhật này **không nguyên tử theo cặp**:

```java
// Mã minh họa: hai biến là AtomicInteger.
available.decrementAndGet();
reserved.incrementAndGet();
```

`decrementAndGet()` và `incrementAndGet()` an toàn **riêng lẻ**, nhưng một reader có thể chạy sau phép giảm và trước phép tăng, thấy tổng tạm thời sai. `volatile` không giải quyết được vì nó không biến hai phép ghi thành một transaction.

### Ví dụ 1 — Tái hiện ảnh chụp không nhất quán

Tạo file **`SplitAtomicState.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class SplitAtomicState {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger available = new AtomicInteger(1);
        AtomicInteger reserved = new AtomicInteger(0);

        CountDownLatch firstWriteDone = new CountDownLatch(1);
        CountDownLatch allowSecondWrite = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            available.decrementAndGet();
            firstWriteDone.countDown();

            try {
                allowSecondWrite.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            reserved.incrementAndGet();
        }, "writer");

        writer.start();
        firstWriteDone.await();

        System.out.println("Intermediate: available="
                + available.get() + ", reserved=" + reserved.get());
        System.out.println("Intermediate total: "
                + (available.get() + reserved.get()));

        allowSecondWrite.countDown();
        writer.join();

        System.out.println("Final total: "
                + (available.get() + reserved.get()));
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac SplitAtomicState.java
java SplitAtomicState
```

Kết quả trong lần chạy bình thường, không có interrupt bên ngoài:

```text
Intermediate: available=0, reserved=0
Intermediate total: 0
Final total: 1
```

Hai `CountDownLatch` điều khiển lịch xen kẽ để main đọc chính xác lúc phép ghi đầu đã hoàn tất còn phép ghi thứ hai chưa chạy. Đây là **race condition của invariant**, không phải lỗi của từng lớp `AtomicInteger`. Việc sau cùng tổng trở về 1 **không làm hợp lệ** ảnh chụp tạm thời bằng 0 mà client có thể nhìn thấy.

Nếu client cần đọc `available` và `reserved` như một đơn vị thì phải có giao thức bảo vệ cả hai, chẳng hạn một monitor chung hoặc một immutable state snapshot được công bố nguyên tử.

## 2. Thiết kế State immutable: một phiên bản, nhiều trường

Ta dùng record Java 17 cho value object:

```java
record Inventory(int available, int reserved) {
}
```

Đây là trích đoạn định nghĩa kiểu, chưa phải chương trình độc lập. Record chỉ chứa hai `int` không có đường sửa field sau khi tạo, do đó mỗi instance là một **ảnh chụp bất biến**. Khi muốn thay đổi, tạo object mới:

```java
Inventory old = new Inventory(2, 0);
Inventory next = new Inventory(1, 1);
// old vẫn là (2, 0), next là (1, 1).
```

Dùng một tham chiếu trung tâm:

```java
AtomicReference<Inventory> state =
        new AtomicReference<>(new Inventory(2, 0));
```

Đoạn trên cần import `java.util.concurrent.atomic.AtomicReference`. Một reader đọc đúng cách:

```java
Inventory snapshot = state.get();
int total = snapshot.available() + snapshot.reserved();
```

Lưu ý **lấy `get()` một lần**. Nếu viết `int a = state.get().available(); int b = state.get().reserved();`, hai lần `get()` có thể nhận **hai snapshot khác nhau** khi writer cập nhật ở giữa, tái tạo lỗi trộn phiên bản dù bản thân mỗi snapshot đều nhất quán.

Tính đúng đắn đến từ **hai thành phần kết hợp**:

1. **Snapshot immutable:** sau khi công bố object không bị sửa thông qua alias nào.
2. **AtomicReference:** `get()` đọc một tham chiếu nguyên tử; CAS commit một tham chiếu nguyên tử, đồng thời dùng hiệu ứng bộ nhớ thích hợp của Java Memory Model.

Chỉ đặt `AtomicReference<MutableState>` rồi gọi `state.get().setX(...)` **không làm các mutation bên trong an toàn**. CAS chỉ bảo vệ việc thay tham chiếu chứ không bọc mọi setter của object mà nó trỏ tới.

## 3. CAS loop: đọc snapshot, tính mới, commit hoặc retry

Khung thuật toán:

```java
while (true) {
    State oldState = state.get();
    State newState = transform(oldState);
    if (state.compareAndSet(oldState, newState)) {
        break; // Commit thành công.
    }
    // Thread khác đã đổi state; phải đọc và tính lại.
}
```

Đây là trích đoạn minh họa với `State`, `state` và `transform` được giả định đã định nghĩa. `compareAndSet(expected, update)` thành công khi tham chiếu hiện tại **chính là reference expected (`==`)**, và thay bằng reference update trong một bước nguyên tử. Nó so sánh **identity**, không gọi `equals()` trên hai record có component giống nhau.

Ví dụ hai writer đồng thời:

```text
current = S0

Thread A: get S0 -> tạo S1
Thread B: get S0 -> tạo S2

A: CAS(S0, S1) = true   -> current = S1
B: CAS(S0, S2) = false  -> không ghi đè S1
B: get S1 -> tính lại S3 từ S1
B: CAS(S1, S3) = true   -> current = S3
```

Nếu B dùng `state.set(S2)` thay vì retry, bản cập nhật của A có thể bị ghi đè. **Phép gán tham chiếu nguyên tử** khác với **cập nhật đọc–tính–ghi nguyên tử**. CAS loop tạo một điểm commit có thể tuyến tính hóa (*linearization point*) tại phép CAS thành công.

Điểm bắt buộc: điều kiện nghiệp vụ cũng phải được kiểm tra **trên snapshot mới nhất của mỗi vòng lặp**. Không được đọc “còn hai suất” trước vòng lặp rồi cứ cố ghi nếu các writer khác đã lấy hết suất.

## 4. Ví dụ 2 — AtomicInventory: cập nhật nhiều trường cùng nhau

Tạo file **`AtomicInventory.java`**:

```java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicInventory {
    record Inventory(int available, int reserved) {
    }

    static final class Store {
        private final AtomicReference<Inventory> state =
                new AtomicReference<>(new Inventory(2, 0));

        boolean reserveOne() {
            while (true) {
                Inventory old = state.get();

                if (old.available() <= 0) {
                    return false;
                }

                Inventory next = new Inventory(
                        old.available() - 1,
                        old.reserved() + 1
                );

                if (state.compareAndSet(old, next)) {
                    return true;
                }
                // Thử lại với snapshot mới và kiểm tra lại số suất.
            }
        }

        Inventory snapshot() {
            return state.get();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Store store = new Store();
        AtomicInteger accepted = new AtomicInteger();

        Runnable request = () -> {
            if (store.reserveOne()) {
                accepted.incrementAndGet();
            }
        };

        Thread a = new Thread(request, "A");
        Thread b = new Thread(request, "B");
        Thread c = new Thread(request, "C");

        a.start();
        b.start();
        c.start();
        a.join();
        b.join();
        c.join();

        Inventory s = store.snapshot();
        System.out.println("Accepted: " + accepted.get());
        System.out.println("Available: " + s.available());
        System.out.println("Reserved: " + s.reserved());
        System.out.println("Total: " + (s.available() + s.reserved()));
    }
}
```

```bash
javac AtomicInventory.java
java AtomicInventory
```

Kết quả:

```text
Accepted: 2
Available: 0
Reserved: 2
Total: 2
```

**Diễn giải:**

- Ban đầu chỉ có object `Inventory(2, 0)`.
- Mỗi thread cùng gọi `reserveOne()` và có thể đọc snapshot giống nhau. Nhưng **chỉ một CAS** với cùng expected object thắng khi current vẫn là object đó.
- Thread CAS thất bại **không sửa state**. Nó phải lấy snapshot mới, kiểm tra lại `available` và tính lại.
- Cuối cùng hai lượt đặt thành công, trạng thái `Inventory(0, 2)`. Thread thứ ba sẽ thấy đã hết suất ở một vòng lặp nào đó và trả về `false`.
- Không xác định **thread nào thắng**, nhưng invariant `available + reserved == 2` đúng với **mọi snapshot được công bố**.
- `accepted` là biến đo trong chương trình demo, không phải thành phần dùng để bảo vệ invariant của Store. `join()` chờ tất cả worker trước khi main in trạng thái cuối.

Reader có thể gọi `store.snapshot()` khi writer đang chạy. Snapshot trả về không bị mutate, nên dù người khác commit bản tiếp theo, reader vẫn thấy **một phiên bản nhất quán**. Tuy nhiên, nó có thể là bản đã cũ vào thời điểm reader ra quyết định; các quyết định cần commit phải quay lại CAS/lock, không chỉ dựa vào snapshot đã đọc trước đó.

## 5. Trả kết quả sau CAS và thiết kế API hợp thành

Một yêu cầu thực tế không chỉ cần biết có đặt thành công hay không mà còn muốn số suất **sau chính lượt đặt đó**. Trong CAS loop, có thể trả `next` **sau khi CAS thành công**, vì đó là snapshot đã commit của lời gọi.

```java
// Trích đoạn trong Store; định nghĩa Inventory/state như Ví dụ 2.
Inventory reserveAndReturn() {
    while (true) {
        Inventory old = state.get();
        if (old.available() == 0) {
            return old; // Ví dụ tối giản: nhánh không đặt được.
        }
        Inventory next = new Inventory(
                old.available() - 1,
                old.reserved() + 1
        );
        if (state.compareAndSet(old, next)) {
            return next;
        }
    }
}
```

Trong API production, nên định nghĩa kiểu kết quả thể hiện rõ **đặt thành công hay thất bại**; không buộc caller suy luận từ `Inventory` trả về. Snapshot `next` phản ánh chính trạng thái đã commit, nhưng **thread khác có thể thay nó ngay sau khi CAS thắng**. Nó không phải cam kết “đây sẽ là state mới nhất cho tới khi caller xử lý xong”.

Cần tránh thiết kế API gồm `getAvailable()` rồi `reserve()` và bảo caller tự thực hiện check-then-act. Chỉ nên công bố thao tác hợp thành `reserveOne()`/ `tryReserve()` để bảo đảm invariant ở cấp quyết định.

## 6. Ví dụ 3 — Chuyển tiền giữa hai số dư trong cùng snapshot

Một ứng dụng khác là chuyển giá trị từ `checking` sang `savings`. Bất biến: hai số dư không âm và **tổng không đổi**. Ta gói hai số dư vào một record để mọi lần commit thay hai giá trị như một đơn vị.

Tạo file **`AtomicAccounts.java`**:

```java
import java.util.concurrent.atomic.AtomicReference;

public class AtomicAccounts {
    record Balances(long checking, long savings) {
    }

    static final class Ledger {
        private final AtomicReference<Balances> state =
                new AtomicReference<>(new Balances(100, 0));

        boolean moveToSavings(long amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }

            while (true) {
                Balances old = state.get();
                if (old.checking() < amount) {
                    return false;
                }
                Balances next = new Balances(
                        old.checking() - amount,
                        old.savings() + amount
                );
                if (state.compareAndSet(old, next)) {
                    return true;
                }
            }
        }

        Balances snapshot() {
            return state.get();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Ledger ledger = new Ledger();

        Thread a = new Thread(() -> ledger.moveToSavings(30));
        Thread b = new Thread(() -> ledger.moveToSavings(40));

        a.start();
        b.start();
        a.join();
        b.join();

        Balances s = ledger.snapshot();
        System.out.println("Checking: " + s.checking());
        System.out.println("Savings: " + s.savings());
        System.out.println("Total: " + (s.checking() + s.savings()));
    }
}
```

```bash
javac AtomicAccounts.java
java AtomicAccounts
```

Kết quả:

```text
Checking: 30
Savings: 70
Total: 100
```

Hai lời chuyển lần lượt 30 và 40 đều có thể thực hiện từ số dư 100; thứ tự CAS nào cũng dẫn đến 30/70. Mỗi snapshot đã commit duy trì tổng 100. **Đây chỉ là ví dụ sổ cái trong bộ nhớ một JVM**: không bảo đảm lưu bền, phục hồi, kiểm toán hay giao dịch giữa database/tiến trình. Đối với tiền thật cần thiết kế số học (đơn vị tiền nguyên nhỏ nhất hoặc `BigDecimal` theo quy tắc phù hợp), transaction và cơ chế tránh thực hiện hai lần ở hệ thống bên ngoài. CAS **không** thay thế transaction của hệ thống sở hữu dữ liệu.

## 7. Immutable nghĩa là không có mutable alias: record chưa đủ trong mọi trường hợp

Record có primitive và String tương đối đơn giản để chia sẻ. Nhưng record chứa list vẫn có thể **chỉ immutable ở mức tham chiếu**:

```java
record UnsafeCatalog(java.util.List<String> items) {
    // Caller giữ List mutable được truyền vào vẫn có thể sửa nó.
}
```

Đây là trích đoạn kiểu dữ liệu. `final List` không ngăn một `ArrayList` bên ngoài sửa nội dung. Bảo vệ bằng defensive copy:

```java
record Catalog(java.util.List<String> items) {
    Catalog {
        items = java.util.List.copyOf(items);
    }
}
```

`List.copyOf` tạo hoặc tái sử dụng một danh sách **không sửa được** với các phần tử đã cho theo hợp đồng. Nếu phần tử là `String` immutable, state được chia sẻ an toàn. Nếu là `MutableOrder` có setter, copy list **không deep-copy** từng item; tham chiếu mutable bên trong vẫn có thể bị sửa. Khi đó cần component immutable, sao chép sâu hoặc kiểm soát ownership.

Không nhầm `Collections.unmodifiableList(original)` (một view không cho sửa trực tiếp, nhưng `original` có thể bị nơi khác thay đổi) với snapshot độc lập.

## 8. Ví dụ 4 — Cập nhật Catalog bằng copy-on-write và updateAndGet

Tạo file **`AtomicCatalog.java`**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicCatalog {
    record Catalog(List<String> items) {
        Catalog {
            items = List.copyOf(items);
        }
    }

    static final class Store {
        private final AtomicReference<Catalog> catalog =
                new AtomicReference<>(new Catalog(List.of()));

        void add(String item) {
            catalog.updateAndGet(old -> {
                List<String> next = new ArrayList<>(old.items());
                next.add(item);
                return new Catalog(next);
            });
        }

        Catalog snapshot() {
            return catalog.get();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Store store = new Store();

        Thread a = new Thread(() -> store.add("A"));
        Thread b = new Thread(() -> store.add("B"));
        a.start();
        b.start();
        a.join();
        b.join();

        List<String> printed = new ArrayList<>(store.snapshot().items());
        Collections.sort(printed);
        System.out.println("Items: " + printed);
        System.out.println("Count: " + printed.size());
    }
}
```

```bash
javac AtomicCatalog.java
java AtomicCatalog
```

Kết quả:

```text
Items: [A, B]
Count: 2
```

`AtomicReference.updateAndGet` cập nhật atomically theo hợp đồng, và **hàm cập nhật có thể được đánh giá lại** trong trường hợp CAS bị tranh chấp. Vì thế lambda chỉ tạo list **local**, không sửa snapshot cũ và không gây side effect bên ngoài. Khi hai thread cùng thêm A và B, kết quả đã commit bao gồm cả hai phần tử. Thứ tự thêm phụ thuộc lịch chạy; chương trình sắp xếp **một bản sao để in ra kết quả ổn định**, không sắp xếp snapshot đã công bố.

Nếu danh sách rất lớn và số lượt cập nhật nhiều, copy-on-write có chi phí cấp phát và sao chép O(n) cho mỗi lần thêm. Khi đó một concurrent collection phù hợp hoặc khóa bảo vệ cấu trúc mutable có thể hiệu quả hơn.

**Không làm thế này:**

```java
// Trích đoạn PHẢN VÍ DỤ: lambda có side effect bên ngoài.
state.updateAndGet(old -> {
    sendEmail(); // Có thể thực thi lại nếu có retry.
    return transform(old);
});
```

Đoạn trên giả định `state`, `sendEmail`, `transform` tồn tại. Vì lambda có thể chạy lại, gửi email trong nó có thể bị lặp dù chỉ một cập nhật thắng CAS. Để tránh, tính snapshot mới bằng hàm thuần, commit trước, rồi thiết kế side effect với idempotency, transaction hoặc cơ chế phục hồi riêng. Chuyển side effect ra sau CAS **không tự bảo đảm** atomicity của side effect với commit JVM khi process có thể lỗi.

## 9. Hiệu ứng bộ nhớ và safe publication theo JMM

`AtomicReference.get()` có hiệu ứng bộ nhớ như một lần **đọc volatile**; `set()` như một lần **ghi volatile**; `compareAndSet()` thành công có hiệu ứng đọc/ghi volatile theo hợp đồng API. Khi writer xây dựng `new State(...)` hoàn chỉnh rồi công bố qua thao tác atomic, reader nhận tham chiếu với `get()` có thể dựa trên quan hệ đồng bộ hóa **happens-before** tương ứng để thấy các trường đã được tạo đúng cách.

Nhưng hãy phân biệt:

- **Hiển thị/publish:** reader thấy các field đã được khởi tạo trong snapshot.
- **Không có mutation tiếp theo:** snapshot immutable bảo vệ reader khỏi thay đổi sâu.
- **Không mất cập nhật:** writer dùng CAS loop thay vì `get()` rồi `set()` đơn thuần.

Nếu thiếu một trong ba, kết quả có thể sai. Việc thấy các field của `State` là `final` giúp bảo đảm khởi tạo đúng cách theo JMM, **không** làm các object mutable trỏ tới trở nên bất biến. Không để `this` hoặc tham chiếu tới state đang khởi tạo thoát khỏi constructor trước khi hoàn tất.

Có thể dùng `volatile State current` để reader lấy **một snapshot** bằng phép đọc volatile nếu chỉ có một writer hoặc writer được phối hợp ngoài. Nhưng khi nhiều writer đều làm `newState = f(current); current = newState`, hai phép tính có thể cùng dựa trên state cũ rồi overwrite nhau. CAS hoặc khóa là cần thiết khi muốn cập nhật hợp thành không mất thay đổi.

## 10. set(), getAndSet(), compareAndSet() và updateAndGet() khác gì?

| API | Hợp đồng chính | Khi nào phù hợp |
|---|---|---|
| `get()` | Lấy tham chiếu hiện hành với hiệu ứng đọc volatile. | Reader lấy **một** immutable snapshot để đọc nhiều field. |
| `set(next)` | Ghi một tham chiếu atomically với hiệu ứng ghi volatile. | Công bố một giá trị đã định; không tự bảo vệ tính toán từ current của nhiều writer. |
| `getAndSet(next)` | Thay tham chiếu và trả tham chiếu cũ như một thao tác nguyên tử. | Swap một tham chiếu giá trị đã chuẩn bị sẵn. |
| `compareAndSet(old, next)` | Chỉ thay nếu current **cùng reference** với old; trả boolean. | Check-then-act trên immutable snapshot với CAS loop. |
| `updateAndGet(fn)` | Biến đổi và commit state mới nguyên tử, trả giá trị mới. | Cập nhật thuần, lambda chịu được retry. |
| `getAndUpdate(fn)` | Tương tự nhưng trả state trước khi cập nhật. | Cần biết snapshot cũ của commit; lambda vẫn phải thuần. |

**Identity vs equality:** `AtomicReference.compareAndSet` so sánh object reference, không phải `equals()`. Nếu tự tạo một object `new State(...)` có cùng giá trị với state hiện tại rồi dùng làm expected, CAS có thể thất bại vì khác instance. Hãy truyền chính tham chiếu `old = state.get()` làm expected.

## 11. ABA: giá trị có thể quay lại mà CAS không biết lịch sử

**ABA problem**: Thread T1 đọc tham chiếu A; thread T2 đổi A → B → **trở lại chính tham chiếu A**; T1 gọi CAS(expected A, next) vẫn có thể thắng vì current lúc đó lại cùng A, mặc dù có thay đổi trung gian.

Điểm quan trọng: **cùng nội dung chưa phải cùng reference**. Nếu T2 tạo một instance A mới có component giống object A cũ nhưng identity khác, CAS với reference cũ không thắng. Một thiết kế **luôn tạo snapshot mới, không bao giờ tái công bố instance cũ** hạn chế kiểu ABA trên identity; tuy nhiên thuật toán nào cần phát hiện “đã có bất cứ update nào kể từ lúc đọc” thì vẫn cần thiết kế version rõ ràng.

Có thể thêm `version` tăng cùng mỗi snapshot, hoặc cân nhắc `AtomicStampedReference` nếu yêu cầu phát hiện thay đổi trung gian. Khi dùng version phải tính đến overflow và chính sách tái sử dụng. Không mặc định rằng CAS nguyên thủy có chức năng ghi nhớ lịch sử state.

## 12. Chi phí và bảo đảm tiến triển

CAS loop không giữ monitor chặn writer khác trong lúc tính snapshot; nó là kỹ thuật **optimistic concurrency**. Điều này hữu ích khi số lượt đọc cao, snapshot nhỏ, cập nhật ít hoặc tranh chấp vừa phải. Nhưng phải cân nhắc:

- **Retry:** dưới contention, một thread có thể CAS thất bại nhiều lần, tốn CPU.
- **Allocation/GC:** mỗi lần thử có thể tạo object mới; bản thất bại trở thành rác.
- **Copy-on-write:** snapshot chứa collection lớn có thể cần O(n) công việc và bộ nhớ cho mỗi update.
- **Fairness:** CAS không bảo đảm mỗi thread riêng lẻ hoàn thành trong một giới hạn thời gian.
- **Side effects:** các thao tác phải được thiết kế để retry an toàn, vì phép tính có thể chạy lại.

Dùng atomic reference không tự làm **toàn bộ thuật toán wait-free** hoặc bảo đảm liveness không giới hạn. Nếu state phức tạp hoặc phải gọi mã bên ngoài dài, một `synchronized`/`ReentrantLock` bao trùm invariant có thể đơn giản và dễ chứng minh đúng hơn. Chỉ tối ưu sau khi đo contention, latency và throughput thực tế.

## 13. Giới hạn atomicity: một reference không phải mọi tài nguyên

Gói hai field vào một immutable State chỉ làm nguyên tử **bộ giá trị mà reference đó trỏ đến**. Nó **không** tạo transaction cho:

- Hai `AtomicReference` **khác nhau** khi cập nhật lần lượt.
- Object mutable ở nơi khác không được đưa vào snapshot.
- Database, file, mạng hoặc tiến trình JVM khác.
- Tác dụng phụ của một lời gọi HTTP, gửi message, ghi audit log.

Nếu nghiệp vụ cần atomicity xuyên nhiều hệ thống, phải dựa trên transaction/constraint/idempotency/outbox hoặc giao thức nhất quán do hệ thống lưu trữ/tích hợp hỗ trợ. Một lock hoặc CAS trong JVM đơn lẻ không bảo đảm nhiều tiến trình khác tuân theo.

Ví dụ `AtomicAccounts` là một **sổ cái minh họa trong bộ nhớ**, không phải giao dịch ngân hàng thực. Việc ghép những yêu cầu khác nhau (ghi bền, rollback, retry yêu cầu khách hàng) đòi hỏi kiến trúc ngoài phạm vi `AtomicReference`.

## 14. So sánh các lựa chọn

| Tình huống | Công cụ phù hợp | Điều kiện/giới hạn |
|---|---|---|
| Tăng một bộ đếm | `AtomicInteger.incrementAndGet()` | Một phép cập nhật nguyên tử trên một biến. |
| Quota còn/đã dùng là một invariant nhiều field | `AtomicReference<ImmutableState>` + CAS hoặc monitor | Phải bảo vệ cả các phép đọc snapshot và mọi đường cập nhật. |
| Reader nhiều, writer ít, state nhỏ | Immutable snapshot + atomic reference | Đọc dễ và nhất quán, trả giá bằng allocation khi ghi. |
| Update nhiều field và có nhánh dài/phức tạp | `synchronized` hoặc `ReentrantLock` | Mọi đường truy cập cùng invariant cần dùng cùng khóa. |
| Map có các key độc lập | `ConcurrentHashMap` | Atomicity theo hợp đồng từng thao tác, không tự giao dịch nhiều key. |
| Chia sẻ danh sách đọc nhiều/ghi ít | Copy-on-write hoặc snapshot immutable | Tránh nếu danh sách rất lớn và writer cực nhiều. |
| State chia sẻ xuyên nhiều JVM | Transaction/conditional write của backend dữ liệu | CAS trong một JVM không bảo đảm toàn cục. |

**Chọn cơ chế dựa trên phạm vi invariant**. Nếu tất cả state liên quan vừa trong một immutable object và cập nhật có thể tính thuần, CAS hợp lý. Nếu state trải nhiều hệ thống hoặc cần thao tác không thể retry, mô hình cần một công cụ khác.

## 15. Checklist thiết kế một compound atomic state

1. **Viết invariant:** liệt kê tất cả field phải nhất quán với nhau.
2. **Đặt vào một State immutable:** record, class final, defensive copy và component immutable.
3. **Ẩn AtomicReference:** dùng `private final AtomicReference<State>`, không để caller tùy tiện `set()`.
4. **Reader chụp một lần:** `State snapshot = state.get()`, đọc mọi field từ cùng snapshot.
5. **Writer đọc–kiểm tra–tính:** tạo object mới từ snapshot đọc được, không sửa object cũ.
6. **CAS commit:** chỉ trả thành công khi `compareAndSet(old, next)` thành công.
7. **Retry đúng:** khi CAS thất bại phải đọc lại và kiểm tra lại điều kiện nghiệp vụ, không ghép state lỗi thời.
8. **Hàm biến đổi thuần:** không I/O, gửi email hoặc ghi vào state khác bên trong retry lambda.
9. **Rõ contract cho snapshot trả về:** snapshot có thể là phiên bản cũ ngay sau `get()`, dù nhất quán.
10. **Đo hiệu năng:** theo dõi retry, thời gian tính, lượng allocation và độ lớn snapshot.
11. **Kiểm thử invariant dưới cạnh tranh:** dùng latch/barrier tạo lịch xen kẽ, `join()` đợi worker, kiểm tra tổng số commit.
12. **Xử lý lỗi ngoài JVM riêng:** không giả định CAS commit đồng thời với database/message/file.

## 16. Câu hỏi tự kiểm tra

1. **Tại sao hai `AtomicInteger` không bảo vệ tổng bất biến hai field?** Mỗi biến atomic riêng, reader có thể thấy khoảng giữa hai cập nhật.
2. **Tại sao reader không nên gọi `state.get()` hai lần rồi ghép hai field?** Hai lần có thể đọc hai snapshot khác phiên bản.
3. **CAS trên `AtomicReference` so sánh `==` hay `equals()`?** So sánh identity reference (`==`).
4. **Nếu CAS thất bại, có thể gọi `state.set(next)` của lần thử cũ không?** Không, vì có thể ghi đè kết quả writer khác.
5. **Vì sao `updateAndGet` nên nhận lambda không side effect?** Lambda có thể được gọi lại do retry.
6. **Có thể sửa `ArrayList` bên trong record có field final mà vẫn coi state immutable không?** Không; cần tránh mutable alias, dùng defensive copy và immutable component.
7. **CAS vừa thắng có nghĩa snapshot luôn mới nhất cho tới khi caller xử lý xong không?** Không; writer khác có thể thay ngay sau đó.
8. **CAS có làm database và JVM thành một transaction không?** Không, cần cơ chế riêng của hệ thống lưu trữ.
9. **ABA nghĩa là gì?** Reference A đổi sang B rồi quay về **chính A**, CAS chỉ dựa identity có thể không nhận ra lịch sử.
10. **Khi nào khóa có thể phù hợp hơn CAS?** State phức tạp, side effect khó retry, snapshot copy tốn kém hoặc contention lớn.

**Ghi nhớ:** nếu nhiều trường thuộc cùng invariant, hãy công bố **một immutable State qua một AtomicReference**, dùng **CAS** để commit các thay đổi hợp thành, và cho reader lấy **một snapshot duy nhất**. Không mutate snapshot cũ; retry phải được thiết kế thuần; đồng bộ trong JVM không tự thay thế transaction bên ngoài.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-007">AtomicIntegerFieldUpdater và điều kiện áp dụng</a>

<details>
<summary>Xem chi tiết</summary>

# AtomicIntegerFieldUpdater và điều kiện áp dụng trong Java

## 1. Vấn đề: cần phép cập nhật atomic cho một trường của object

Trong Java concurrency, phép tăng `counter++` trên một trường `int` thông thường không nguyên tử: hai thread có thể cùng đọc giá trị cũ rồi ghi đè kết quả của nhau. Một cách giải quyết quen thuộc là giữ một `AtomicInteger` trong mỗi object:

~~~java
// Minh họa trong một class, không phải chương trình độc lập.
private final AtomicInteger counter = new AtomicInteger();

void increment() {
    counter.incrementAndGet();
}
~~~

Cách này đơn giản và thường là lựa chọn đầu tiên. Nhưng nếu chương trình có **rất nhiều đối tượng**, mỗi object cần một bộ đếm nhỏ, việc đặt một object `AtomicInteger` riêng trong từng instance có thể phát sinh thêm allocation, tham chiếu và chi phí bộ nhớ.

`java.util.concurrent.atomic.AtomicIntegerFieldUpdater<T>` cho phép thao tác atomic lên **một trường `volatile int` nằm trực tiếp trong một object có kiểu T**:

~~~java
// Minh họa trong chính class khai báo trường, không phải chương trình độc lập.
private volatile int count;

private static final AtomicIntegerFieldUpdater<MyCounter> COUNT =
    AtomicIntegerFieldUpdater.newUpdater(MyCounter.class, "count");
~~~

Sau đó có thể gọi `COUNT.incrementAndGet(this)`, `COUNT.compareAndSet(this, expected, update)` hoặc `COUNT.get(this)`. **Một updater có thể được chia sẻ** để cập nhật trường tương ứng của nhiều instance, không cần mỗi instance chứa một object `AtomicInteger`.

**Điều cần nhớ ngay từ đầu:** Field updater là công cụ cấp thấp cho một trường cụ thể, **không thay thế** việc thiết kế ownership, bảo vệ bất biến nhiều biến và bảo đảm mọi đường cập nhật tuân thủ cùng một giao thức.

Bài tập trung vào API JDK và Java Memory Model của **Java nói chung**, không đi vào cấu hình của framework.

### Mục tiêu học tập

Sau bài, bạn cần hiểu:

1. Tại sao field updater khác với `AtomicInteger` và khi nào có lợi.
2. Các điều kiện bắt buộc: trường **instance**, kiểu chính xác **primitive `int`**, có modifier **`volatile`**, truy cập hợp lệ tại điểm tạo updater.
3. Cách tạo `AtomicIntegerFieldUpdater.newUpdater(...)`, cập nhật bằng CAS và lấy kết quả trả về đúng ý nghĩa.
4. Tại sao trộn updater với phép ghi trực tiếp hoặc `count++` có thể làm hỏng protocol atomic.
5. Phạm vi bảo đảm visibility/ordering của updater, các giới hạn với nhiều trường, contention và retry.
6. Cách chọn giữa updater, `AtomicInteger`, `AtomicLongFieldUpdater`, `AtomicReferenceFieldUpdater` và `VarHandle` trên Java 17+.

## 2. Khái niệm và chữ ký API

Một updater là một đối tượng mô tả **cách truy cập nguyên tử** một trường `int` cụ thể của một kiểu đối tượng.

~~~java
// Cú pháp API ở mức minh họa, không phải chương trình độc lập.
AtomicIntegerFieldUpdater<Worker> updater =
    AtomicIntegerFieldUpdater.newUpdater(Worker.class, "progress");

// target phải là một Worker hợp lệ.
updater.get(target);
updater.incrementAndGet(target);
updater.compareAndSet(target, 10, 11);
~~~

Phương thức tạo:

~~~java
// Chữ ký tổng quát, không phải đoạn chương trình độc lập.
public static <U> AtomicIntegerFieldUpdater<U> newUpdater(
    Class<U> tclass,
    String fieldName
);
~~~

Trong đó:

- `tclass` là class khai báo trường mục tiêu; ở ví dụ là `Worker.class`.
- `fieldName` là **tên trường thật** dưới dạng string, chẳng hạn `"progress"`. Nó không phải tên getter và compiler không kiểm tra sự tồn tại của chuỗi này.
- `U` là kiểu của đối tượng mục tiêu; mỗi thao tác updater nhận một **instance** chứa trường.
- Đối tượng updater thường được tạo một lần và lưu vào `private static final` để nhiều instance dùng chung.

Phần lớn các phép toán quen thuộc của `AtomicInteger` cũng có trên updater, nhưng nhận thêm **đối tượng đích**:

| Updater API | Ý nghĩa |
| --- | --- |
| `get(obj)` | Đọc trường có ngữ nghĩa volatile |
| `set(obj, newValue)` | Ghi trường có ngữ nghĩa volatile |
| `lazySet(obj, newValue)` | Ghi với ngữ nghĩa release/lazy set, yếu hơn một volatile set toàn phần |
| `compareAndSet(obj, expected, update)` | CAS: đổi giá trị nếu hiện tại bằng `expected`, trả boolean |
| `getAndSet(obj, update)` | Gán giá trị mới và trả giá trị cũ |
| `getAndIncrement(obj)` | Tăng nguyên tử, trả **giá trị trước khi tăng** |
| `incrementAndGet(obj)` | Tăng nguyên tử, trả **giá trị sau khi tăng** |
| `getAndAdd(obj, delta)` | Cộng nguyên tử, trả giá trị cũ |
| `addAndGet(obj, delta)` | Cộng nguyên tử, trả giá trị mới |
| `updateAndGet(obj, function)` | Cập nhật nguyên tử bằng hàm biến đổi, trả giá trị mới |

Một updater áp dụng được cho **nhiều instance của cùng class**, nhưng lần gọi trên instance A không tự động cập nhật instance B. Field updater quản lý **vị trí trường trong từng đối tượng**, không quản lý một biến toàn cục dùng chung.

## 3. Điều kiện bắt buộc khi khai báo trường

### 3.1. Phải là field instance, không phải static

`AtomicIntegerFieldUpdater` cập nhật một trường cụ thể của một **đối tượng nhận vào**. Field được khai báo `static` không phù hợp vì nó thuộc về class, không thuộc từng instance.

~~~java
// Hợp lệ về hình dạng trường, khi updater tạo trong phạm vi truy cập phù hợp:
private volatile int attempts;

// Không hợp lệ làm target cho AtomicIntegerFieldUpdater:
private static volatile int globalAttempts;
~~~

Với bộ đếm toàn cục, có thể dùng một `static final AtomicInteger`, hoặc chọn một cơ chế field/handle khác nếu có lý do rõ ràng.

### 3.2. Kiểu phải chính xác là primitive int

Field target phải có kiểu nguyên thủy `int`. Những kiểu sau **không** dùng với `AtomicIntegerFieldUpdater`:

- `Integer`: object wrapper, không phải primitive `int`.
- `long`: dùng `AtomicLongFieldUpdater` nếu có cùng nhu cầu.
- `boolean`: không phải target của integer field updater; có thể dùng `AtomicBoolean` hoặc biểu diễn trạng thái bằng `int` khi thật sự cần.
- `Object` hoặc một reference bất kỳ: cân nhắc `AtomicReferenceFieldUpdater<T,V>`.

Một `Integer` được boxing/unboxing tự động trong biểu thức Java không làm một trường `Integer` trở thành trường `int` về mặt reflection và API.

### 3.3. Bắt buộc có volatile

Field phải được khai báo `volatile`:

~~~java
// Điều kiện hợp lệ.
private volatile int state;

// Không hợp lệ để tạo updater.
private int stateWithoutVolatile;
~~~

`volatile` thiết lập ngữ nghĩa visibility/ordering liên luồng theo Java Memory Model, còn updater bổ sung các phép **read–modify–write atomic** như CAS và `incrementAndGet`.

Không nên bỏ `volatile` với lý do “đã gọi updater rồi”: constructor/factory updater kiểm tra loại trường và modifier, một field không volatile sẽ bị từ chối.

### 3.4. Quyền truy cập phải hợp lệ tại nơi gọi newUpdater

Mỗi updater được tạo bằng reflection và thực hiện các kiểm tra truy cập theo ngữ cảnh của caller. Một cách đóng gói đơn giản, chắc chắn là **đặt factory updater bên trong chính class khai báo field**:

~~~java
// Đoạn minh họa hợp lệ trong class Worker, không phải chương trình độc lập.
private volatile int progress;

private static final AtomicIntegerFieldUpdater<Worker> PROGRESS =
    AtomicIntegerFieldUpdater.newUpdater(Worker.class, "progress");
~~~

`private volatile` vẫn **hoàn toàn có thể dùng được** nếu updater được tạo từ ngữ cảnh có quyền truy cập private đó. Vì vậy quy tắc “field phải public” là **sai**.

Ngược lại, một class bên ngoài tự tạo updater cho field private của class khác có thể bị từ chối truy cập. Với package-private/protected field, package, quan hệ kế thừa và receiver thực tế cũng có thể ảnh hưởng đến quyền. Trong ứng dụng có Java module, các ranh giới truy cập/module càng cần được xem xét.

Không tìm cách chữa một lỗi access bằng cách tùy tiện mở mọi field public. Ưu tiên factory nằm trong class sở hữu trường và cung cấp các method nghiệp vụ phù hợp.

### 3.5. Không được gán lại ý nghĩa của field

Vì field là `volatile int` bình thường, Java vẫn cho phép code trong class đọc hoặc ghi trực tiếp. Nhưng nếu đã chọn updater để bảo vệ quy tắc cập nhật, **mọi bên ghi cùng field phải tuân thủ giao thức đã định**. Một setter trực tiếp bất ngờ có thể ghi đè giá trị tính từ CAS hoặc phá bất biến.

Field cũng không thể đồng thời là `final volatile` theo Java language rules. Nó cần được cập nhật trong vòng đời object nên không phải final field.

## 4. Ví dụ 1 — nhiều thread tăng trường bằng updater (Java 17+)

Tạo tệp `FieldUpdaterCounterDemo.java`:

~~~java
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class FieldUpdaterCounterDemo {
    static final class Counter {
        private volatile int value;

        private static final AtomicIntegerFieldUpdater<Counter> VALUE =
            AtomicIntegerFieldUpdater.newUpdater(
                Counter.class, "value"
            );

        void increment() {
            VALUE.incrementAndGet(this);
        }

        int get() {
            return VALUE.get(this);
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Counter counter = new Counter();

        Runnable work = () -> {
            for (int i = 0; i < 5000; i++) {
                counter.increment();
            }
        };

        Thread first = new Thread(work, "counter-1");
        Thread second = new Thread(work, "counter-2");
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("value = " + counter.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac FieldUpdaterCounterDemo.java
java FieldUpdaterCounterDemo
~~~

Đầu ra chính xác khi cả hai worker hoàn tất:

~~~text
value = 10000
~~~

Giải thích:

1. `Counter` có trường `private volatile int value`, đúng yêu cầu type/modifier/instance.
2. `VALUE` là `static final` nên cả hai thread cùng dùng **một updater**, nhưng tác động tới trường của cùng đối tượng `counter`.
3. `incrementAndGet(this)` thực hiện tăng nguyên tử, tránh lost update của `value++` thông thường.
4. `join()` chờ hai worker xong trước khi `main` đọc và in; kết quả 2 × 5.000 = 10.000.
5. Việc dùng updater không làm `Counter` tự động bảo vệ **mọi trạng thái khác**; chỉ trường `value` được cập nhật theo giao thức đó.

Nếu class còn các field phụ thuộc `value`, một phép CAS đơn lẻ không tự biến cập nhật nhiều field thành transaction.

## 5. CAS cho trạng thái một chiều: ví dụ 2 (Java 17+)

Một ứng dụng thường có trạng thái như `NEW → RUNNING → DONE`. Nếu hai thread cùng yêu cầu bắt đầu một job, **chỉ một bên** được đổi từ NEW sang RUNNING. `compareAndSet` phù hợp để thực hiện phép chuyển trạng thái này một cách nguyên tử.

Tạo tệp `UpdaterStateMachineDemo.java`:

~~~java
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class UpdaterStateMachineDemo {
    static final class Job {
        private static final int NEW = 0;
        private static final int RUNNING = 1;
        private static final int DONE = 2;

        private volatile int state = NEW;

        private static final AtomicIntegerFieldUpdater<Job> STATE =
            AtomicIntegerFieldUpdater.newUpdater(
                Job.class, "state"
            );

        boolean tryStart() {
            return STATE.compareAndSet(this, NEW, RUNNING);
        }

        boolean finish() {
            return STATE.compareAndSet(this, RUNNING, DONE);
        }

        String stateName() {
            return switch (STATE.get(this)) {
                case NEW -> "NEW";
                case RUNNING -> "RUNNING";
                case DONE -> "DONE";
                default -> "INVALID";
            };
        }
    }

    public static void main(String[] args) {
        Job job = new Job();

        System.out.println("start first = " + job.tryStart());
        System.out.println("start second = " + job.tryStart());
        System.out.println("finish = " + job.finish());
        System.out.println("state = " + job.stateName());
    }
}
~~~

Biên dịch và chạy với Java 17+:

~~~bash
javac UpdaterStateMachineDemo.java
java UpdaterStateMachineDemo
~~~

Đầu ra chính xác:

~~~text
start first = true
start second = false
finish = true
state = DONE
~~~

- CAS thứ nhất thấy `state == NEW` và chuyển sang RUNNING.
- CAS thứ hai đòi `state == NEW` nhưng state đã RUNNING, nên trả `false`.
- `finish` chỉ chuyển RUNNING sang DONE.
- Không dùng `if (state == NEW) state = RUNNING` cho tác vụ này: check-then-act có thể bị thread khác chen vào giữa hai bước.

Nếu hai thread thực sự gọi `tryStart()` cùng lúc, chỉ **một trong hai** có thể thành công, nhưng thứ tự thread nào thắng không cố định. Không dùng output “thread A thắng” như kết quả bắt buộc.

Với state machine nhiều chuyển tiếp, bạn vẫn phải định nghĩa đầy đủ các trạng thái, điều kiện transition và cách xử lý failure. CAS trên một `int` chỉ bảo vệ chính transition đã viết; nó không tự hoàn thành job, rollback side effect hoặc quản lý timeout.

## 6. Trộn updater với ghi trực tiếp: một lỗi quan trọng

### 6.1. Vì sao “field volatile” vẫn có thể lost update?

Một field như `private volatile int value` cho phép phép đọc/ghi riêng có ngữ nghĩa volatile, nhưng **`value++` vẫn gồm nhiều bước**. Nếu một code path gọi `VALUE.incrementAndGet(this)`, trong khi code path khác gọi `value++` hoặc `value = cached + 1`, các thao tác có thể xen nhau gây mất cập nhật.

Đừng trộn:

~~~java
// Minh họa trong class có updater VALUE; KHÔNG an toàn nếu chạy đồng thời.
void incrementWrong() {
    value++;
}

void incrementRight() {
    VALUE.incrementAndGet(this);
}
~~~

Ngay cả khi việc ghi trực tiếp cũng là volatile write và có visibility, phép cập nhật gộp vẫn không có atomicity. Ngoài ra, setter trực tiếp có thể bỏ qua các điều kiện CAS như “chỉ chuyển NEW sang RUNNING một lần”.

Javadoc lưu ý bảo đảm atomicity của field updater phụ thuộc việc các bên thao tác **phù hợp với giao thức atomic**; một updater không thể ngăn các phương thức khác truy cập field theo cách thiếu an toàn. Vì thế nên để field `private` và tạo các method có tên thể hiện rõ transition.

### 6.2. Ví dụ 3 — lost update có kiểm soát (Java 17+)

Thay vì hy vọng scheduler ngẫu nhiên tạo race, ta dùng latch để cả hai thread đọc giá trị cũ rồi mới ghi. Trong cùng lúc, một trường khác được cập nhật bằng updater để đối chiếu.

Tạo tệp `FieldUpdaterRaceDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class FieldUpdaterRaceDemo {
    static final class Counts {
        private volatile int unsafe;
        private volatile int safe;

        private static final AtomicIntegerFieldUpdater<Counts> SAFE =
            AtomicIntegerFieldUpdater.newUpdater(
                Counts.class, "safe"
            );

        void incrementSafe() {
            SAFE.incrementAndGet(this);
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        Counts counts = new Counts();
        CountDownLatch bothRead = new CountDownLatch(2);

        Runnable work = () -> {
            int before = counts.unsafe;
            bothRead.countDown();

            try {
                bothRead.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            }

            counts.unsafe = before + 1;
            counts.incrementSafe();
        };

        Thread a = new Thread(work, "worker-a");
        Thread b = new Thread(work, "worker-b");
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("unsafe = " + counts.unsafe);
        System.out.println("safe = " + Counts.SAFE.get(counts));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac FieldUpdaterRaceDemo.java
java FieldUpdaterRaceDemo
~~~

Đầu ra chính xác khi worker không bị interrupt:

~~~text
unsafe = 1
safe = 2
~~~

Cả hai thread lấy cùng `before == 0` rồi được giải phóng qua `bothRead`. Vì vậy hai thao tác `counts.unsafe = before + 1` đều ghi 1, dù trường này có `volatile`. Với trường `safe`, hai lần `SAFE.incrementAndGet(counts)` dùng read–modify–write atomic, nên ra 2.

Thí nghiệm có chủ đích tổ chức thứ tự đọc trước khi ghi, không phải một stress test ngẫu nhiên. Latch dùng để phối hợp ví dụ; nó không biến phép cập nhật `unsafe` thành atomic.

## 7. Cách viết CAS loop đúng và xử lý tranh chấp

Không phải mọi logic cập nhật đều có sẵn một method đơn giản. Ví dụ tăng bộ đếm nhưng **không được vượt quá giới hạn**:

~~~java
// Minh họa trong class có private volatile int value và updater VALUE.
int incrementUnlessAtMax() {
    for (;;) {
        int oldValue = VALUE.get(this);

        if (oldValue == Integer.MAX_VALUE) {
            return oldValue;
        }

        if (VALUE.compareAndSet(this, oldValue, oldValue + 1)) {
            return oldValue + 1;
        }

        // Có cạnh tranh: đọc lại và thử CAS trên trạng thái mới.
    }
}
~~~

Giải thích:

- `get` lấy giá trị cũ với ngữ nghĩa volatile.
- Nếu đã đạt giới hạn, không thay đổi.
- Nếu chưa đạt, CAS chỉ thay đổi khi trường vẫn bằng giá trị đã đọc. Nếu thread khác cập nhật trước, CAS thất bại và vòng lặp thử lại.
- Việc kiểm tra `Integer.MAX_VALUE` ngăn overflow đối với phép tăng này.

**Các điểm cần lưu ý trong CAS loop:**

1. **Side effect:** không gửi email, gọi API, ghi file hoặc trừ tiền bên trong đoạn có thể được lặp lại nhiều lần. Một attempt có thể thất bại sau khi side effect đã thực hiện.
2. **Progress:** CAS không bảo đảm mỗi thread được cấp cơ hội công bằng. Một thread có thể thất bại liên tục dưới contention dù tổng hệ thống vẫn tiến triển.
3. **CPU:** spin quá nhiều gây tốn CPU; đo contention thực tế, cân nhắc backoff, khóa, queue hoặc phân mảnh trạng thái.
4. **Overflow:** toán học `int` của Java có wraparound khi vượt phạm vi; giao thức nghiệp vụ cần quy tắc rõ ràng.
5. **Invariant:** nếu thay đổi cần ảnh hưởng hai biến, CAS trên một trường đơn không giữ được tính nguyên tử của cả hai.
6. **ABA:** trạng thái A → B → A có thể khiến CAS từ A tới C thành công dù trạng thái đã thay đổi giữa lúc đọc và update; nếu lịch sử/phiên bản quan trọng, cần version hoặc biểu diễn state phù hợp.

Các phương thức `updateAndGet` và `getAndUpdate` cũng có thể gọi hàm biến đổi nhiều lần do CAS retry. Hàm nên ngắn, không có side effect và an toàn khi thực thi lặp lại.

## 8. Ví dụ 4 — một updater, nhiều object độc lập (Java 17+)

Điểm có giá trị của field updater là **không tạo một wrapper atomic cho từng instance**. Một updater static được chia sẻ nhưng vẫn thao tác trên các object khác nhau.

Tạo tệp `ManyCountersDemo.java`:

~~~java
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class ManyCountersDemo {
    static final class Bucket {
        private volatile int hits;

        private static final AtomicIntegerFieldUpdater<Bucket> HITS =
            AtomicIntegerFieldUpdater.newUpdater(
                Bucket.class, "hits"
            );

        void addHit() {
            HITS.incrementAndGet(this);
        }

        int hits() {
            return HITS.get(this);
        }
    }

    public static void main(String[] args) {
        Bucket first = new Bucket();
        Bucket second = new Bucket();

        first.addHit();
        first.addHit();
        second.addHit();

        System.out.println("first = " + first.hits());
        System.out.println("second = " + second.hits());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ManyCountersDemo.java
java ManyCountersDemo
~~~

Đầu ra chính xác:

~~~text
first = 2
second = 1
~~~

`Bucket.HITS` không chứa giá trị của riêng nó. Nó biết *trường nào* sẽ được cập nhật. `first` và `second` vẫn có hai trường `hits` độc lập. Nếu mỗi instance tạo updater riêng sẽ bỏ lỡ lợi ích của việc tái sử dụng factory và phát sinh thao tác truy cập reflective không cần thiết.

Tuy vậy, không suy ra rằng updater luôn nhanh hơn `AtomicInteger`. Thời gian thao tác còn phụ thuộc JVM, field layout, cache contention, số thread, chính sách cấp phát và workload. **Lợi ích khả dĩ nổi bật là giảm object wrapper trên mỗi instance**, không phải bảo đảm throughput cao hơn trong mọi trường hợp.

## 9. Java Memory Model: visibility và ordering của updater

### 9.1. Field cần volatile vì lý do gì?

`volatile` không làm `x++` trở thành atomic. Nhưng nó cung cấp các bảo đảm đọc/ghi giữa các thread khi có giao thức công bố phù hợp:

- Ghi volatile và đọc volatile tương ứng được JMM ràng buộc bằng quan hệ **synchronizes-with/happens-before**.
- Một lần đọc/ghi volatile riêng có tính nguyên tử cho trường `int`; updater bổ sung CAS và cập nhật gộp nguyên tử.
- Những hành động trước volatile write có thể được công bố cho thread đọc volatile sau đó theo quan hệ HB thích hợp.

Cần phân biệt **visibility**, **ordering** và **atomicity**. Updater không làm cả object tự thread-safe. Ví dụ nếu có hai trường `processed` và `failed`, hai updater độc lập không tạo snapshot nguyên tử của cặp giá trị.

### 9.2. Các đường đọc trực tiếp có được dùng không?

Một method `get() { return value; }` trong class chứa `private volatile int value` vẫn có ngữ nghĩa volatile read. Nhưng để tập trung giao thức và dễ review, dùng `VALUE.get(this)` thể hiện rõ thao tác thông qua updater.

**Quan trọng:** đọc trực tiếp một volatile field không gây lỗi tự thân; rủi ro chính là những **đường ghi/cập nhật không tuân thủ cùng invariant**, như `value++` hoặc `value = newValue` phá điều kiện CAS. Đừng tuyệt đối hóa rằng mọi truy cập trực tiếp đều “bị cấm bởi JVM”; chúng hợp lệ về ngôn ngữ nhưng có thể sai thiết kế.

### 9.3. Một updater không tạo giao dịch cho nhiều field

Xét hai trường `remaining` và `reserved`. Một phép `getAndDecrement` trên `remaining` rồi `incrementAndGet` trên `reserved` là **hai thao tác atomic riêng**, reader có thể thấy trạng thái giữa hai lần cập nhật.

Nếu invariant phải được giữ ở một thời điểm quan sát, cân nhắc:

- `synchronized` hoặc `Lock` bảo vệ cả hai trường và mọi đường đọc phù hợp.
- Một object immutable chứa cả hai giá trị, được thay thế bằng một `AtomicReference<Snapshot>`.
- Chuyển ownership sang một thread xử lý tuần tự thông điệp, nếu mô hình hệ thống cho phép.

Field updater đặc biệt thích hợp cho **một trạng thái số độc lập**, **counter**, **flag dạng số**, hoặc **transition trên một trường** mà các đường cập nhật đã được kiểm soát.

## 10. Đối chiếu AtomicInteger, updater, VarHandle và updater khác

| Lựa chọn | Trạng thái lưu ở đâu? | Khi nào phù hợp? | Lưu ý |
| --- | --- | --- | --- |
| `AtomicInteger` | Trong một object atomic riêng | Ít instance, code đơn giản, cần API rõ | Có thêm object wrapper nếu mỗi instance giữ một atomic |
| `AtomicIntegerFieldUpdater<T>` | Trường `volatile int` trong `T`; updater được chia sẻ | Rất nhiều object nhỏ, muốn CAS trên field trực tiếp | Yêu cầu tên trường dạng chuỗi, quyền truy cập, volatile int instance |
| `AtomicLongFieldUpdater<T>` | Trường `volatile long` trong `T` | Counter 64-bit trên nhiều instance | Cũng cần xem access, hiệu năng và contention |
| `AtomicReferenceFieldUpdater<T,V>` | Trường `volatile V` trong `T` | Chuyển reference state atomically | Bảo vệ reference, không làm object được trỏ tới immutable |
| `VarHandle` (Java 9+) | Handle tới field/array element/khác | Muốn điều khiển access mode linh hoạt | API cấp thấp, nhiều memory-ordering mode, dễ dùng sai |
| `synchronized` / `Lock` | Trạng thái có thể gồm nhiều trường | Bảo vệ invariant tổng hợp, update phức tạp | Contention và nguy cơ deadlock nếu khóa lồng nhau |
| `LongAdder` | Nội bộ nhiều cell phục vụ tổng cộng | Thống kê đếm với tranh chấp cao | `sum()` không phải snapshot nguyên tử khi update đồng thời |

### 10.1. Khi nào AtomicInteger vẫn là lựa chọn tốt hơn?

Nếu chỉ có một vài object và số lượng worker không lớn, `AtomicInteger` thường:

- Dễ đọc và dùng hơn, không yêu cầu tên field string.
- Có thiết kế đóng gói và thao tác atomic rõ ràng trên chính instance.
- Ít nguy cơ bị một đường setter trực tiếp ghi sai vào trường primitive.
- Dễ chuyển code hoặc refactor field mà không phải duy trì tên chuỗi ở factory.

Đừng chọn updater chỉ để “micro-optimize” một class hiếm khi được tạo. Đo allocation, heap, throughput, tail latency và GC trước khi quyết định.

### 10.2. VarHandle trên Java 17+

`VarHandle` có thể trỏ tới một field và hỗ trợ `getVolatile`, `setVolatile`, `compareAndSet`, `getAndAdd` cùng các access mode khác nhau.

~~~java
// Minh họa về cách tìm VarHandle, không phải chương trình độc lập.
VarHandle handle = MethodHandles.lookup().findVarHandle(
    Counter.class, "value", int.class
);
int old = (int) handle.getAndAdd(counter, 1);
~~~

Đoạn trên giả định code có quyền truy cập field và import `java.lang.invoke.MethodHandles`, `java.lang.invoke.VarHandle`, đồng thời xử lý checked reflection exceptions. Field `value` phải là một instance field kiểu `int`. Khác với updater, `VarHandle` có nhiều access mode; phải hiểu hợp đồng memory ordering của từng mode để tránh chọn thao tác yếu hơn nhu cầu.

`VarHandle` không mặc nhiên an toàn hơn hoặc nhanh hơn updater; việc thay thế cần benchmark, review memory semantics và kiểm tra đúng JDK mục tiêu.

## 11. Những lỗi cấu hình thường gặp

### 11.1. Nhầm field thông thường với volatile field

~~~java
// Minh họa sai: updater sẽ từ chối trường không volatile.
static final class WrongCounter {
    int count;
}
~~~

Một lời gọi `AtomicIntegerFieldUpdater.newUpdater(WrongCounter.class, "count")` không tạo được updater hợp lệ cho field này. Cách sửa không phải cast hay generic khác, mà là thiết kế lại field phù hợp với điều kiện `volatile int` và giao thức cập nhật.

### 11.2. Nhầm Integer với int

~~~java
// Minh họa sai cho AtomicIntegerFieldUpdater:
private volatile Integer count;
~~~

Dù reference được khai báo volatile, field không có kiểu primitive `int`. Dùng `volatile int`, hoặc cấu trúc atomic phù hợp với object reference nếu bài toán thực sự cần reference.

### 11.3. Nhầm static field với instance field

~~~java
// Minh họa sai cho AtomicIntegerFieldUpdater:
private static volatile int count;
~~~

Field updater tác động lên từng đối tượng. Với một global counter, hãy dùng một shared `AtomicInteger` hay một cơ chế global state thích hợp.

### 11.4. Trường private được tạo updater từ class ngoài

Một class không có quyền vào trường private không thể dựa vào `newUpdater(Target.class, "secretCounter")` để tự ý cập nhật field đó. Factory có thể bị từ chối tại runtime. Giải pháp đúng thường là đặt updater **trong class sở hữu** và cung cấp method nghiệp vụ hoặc dùng API công khai có kiểm soát.

### 11.5. Lỗi tên string khi refactor

Nếu đổi field từ `"attempts"` thành `"retries"` nhưng quên đổi literal tại `newUpdater`, code có thể **biên dịch thành công nhưng lỗi khi khởi tạo updater tại runtime**. IDE và test nên kiểm tra tên field trong initializer. Giữ factory tập trung trong class khai báo để refactor dễ hơn.

### 11.6. Trộn updater với setter thường

Một method `reset() { count = 0; }` chạy đồng thời có thể phá hợp đồng counter, dù `count` vẫn volatile. Nếu reset được phép, cần xác định có thể xảy ra trong lúc increment không và cơ chế atomic của reset tương ứng (ví dụ `getAndSet`), cùng ý nghĩa nghiệp vụ của các cập nhật đang chạy.

### 11.7. Cho rằng CAS luôn bảo đảm mọi thread tiến triển

CAS loop có thể thất bại liên tục dưới contention. Không có lời hứa một thread cụ thể sẽ lấy được quyền cập nhật sau một số attempt hữu hạn. Điều đó khác với việc toàn bộ thuật toán có thể tiến triển theo một thuộc tính lock-free được chứng minh riêng.

### 11.8. Dùng updater như một lock tổng thể

Field updater chỉ cập nhật một field. Nó không khóa mọi hành động của caller và không giúp giao dịch nhiều bước trên file, database hoặc nhiều object trở thành atomic.

## 12. Performance: bộ nhớ, contention và chi phí factory

### 12.1. Khi số lượng object lớn, vì sao đáng cân nhắc?

Giả sử một hệ thống có hàng triệu object `Node`, mỗi node có một bộ đếm `visits`. Nếu mỗi node giữ một `AtomicInteger` riêng, JVM có thể phải cấp phát nhiều object wrapper. Một `volatile int visits` cùng **một static updater** có thể giảm lượng object phụ trợ.

Không đưa ra một con số “tiết kiệm X byte mỗi node” chung cho mọi máy: kích thước object phụ thuộc layout JVM, compressed references, alignment, padding và những field khác. Hãy dùng profiler hoặc phép đo allocation trên cấu hình JVM thực tế.

### 12.2. FieldUpdater không đảm bảo throughput tốt hơn

Một updater phải giải quyết field offset/access và có đường gọi tương ứng. JIT có thể tối ưu rất nhiều nhưng kết quả phụ thuộc workload. Trong tranh chấp mạnh, các thread vẫn cùng cạnh tranh CAS trên **một ô nhớ**, có thể làm tăng chi phí cache coherence và số lần retry.

Khi số update vào **cùng một field** cực lớn, việc chuyển sang updater không loại bỏ contention. Xem xét phân tán counter, batching, sharding hoặc `LongAdder` cho thống kê khi không cần snapshot cập nhật chính xác tức thời.

### 12.3. Không tạo updater trên mỗi lần gọi

~~~java
// Minh họa chống chỉ định về thiết kế, không phải chương trình độc lập.
void incrementInefficient() {
    AtomicIntegerFieldUpdater<Counter> updater =
        AtomicIntegerFieldUpdater.newUpdater(Counter.class, "value");
    updater.incrementAndGet(this);
}
~~~

Factory dùng reflection và kiểm tra truy cập; việc tạo lại thường xuyên là thừa. Cấu hình một `private static final` updater tại class sở hữu field sẽ đơn giản hơn và ít chi phí hơn.

Đối với benchmark, phải so sánh cùng logic và mức contention, có warm-up/JIT, nhiều lượt chạy, đo cả throughput lẫn allocation/GC và p95/p99 latency. Một microbenchmark tự viết bằng vài lần `System.nanoTime()` có thể gây hiểu nhầm.

## 13. Kiểm thử và quan sát đúng cách

**Kiểm tra tính hợp lệ của factory:**

- Field đúng là `volatile int` dạng instance.
- Chuỗi tên field chính xác và class khai báo đúng.
- Caller có quyền truy cập; updater được tạo trong nơi sở hữu field.
- Không tạo updater lặp đi lặp lại.

**Kiểm tra correctness đồng thời:**

- Nhiều thread increment cùng một instance rồi join; kết quả bằng tổng dự kiến.
- Nhiều instance dùng chung updater; từng instance phải độc lập.
- Nhiều thread CAS cạnh tranh chuyển state từ NEW: chỉ một thread thành công.
- Không có setter/`++` trực tiếp làm sai protocol.
- Nếu cần retry CAS, hàm tính toán có thể lặp lại mà không tạo side effect.
- Các invariant nhiều field được bảo vệ bằng cơ chế riêng.

**Kiểm tra performance:**

- Số instance và allocation chênh lệch giữa `AtomicInteger` và updater.
- Thời gian chờ, số lần CAS thất bại, CPU trong contention cao.
- Ảnh hưởng của false sharing/cache-line contention và layout trong môi trường thực.
- Độ trễ đuôi và tỉ lệ hoàn tất của các thread; đừng xem tốc độ tổng là bảo đảm công bằng cho từng thread.

Stress test có thể phát hiện bug, nhưng một số lượt chạy thành công không chứng minh giao thức memory đúng cho mọi interleaving. Cần lập luận theo JMM và review toàn bộ đường truy cập field.

## 14. Checklist chọn AtomicIntegerFieldUpdater

| Câu hỏi | Nếu câu trả lời là “không” |
| --- | --- |
| Field mục tiêu là **instance primitive int**? | Không dùng `AtomicIntegerFieldUpdater` |
| Field có modifier **volatile**? | Sửa khai báo hoặc chọn cách khác |
| Updater được tạo trong ngữ cảnh có quyền truy cập? | Đưa factory về class sở hữu hoặc đổi API |
| Có nhiều instance và lý do đo được để giảm object wrapper? | `AtomicInteger` thường đơn giản hơn |
| Mọi đường ghi trường dùng chung một giao thức atomic? | Dễ mất update hoặc phá state machine |
| Một field có đủ để bảo vệ invariant không? | Dùng lock hoặc atomic immutable snapshot |
| CAS retry function không có side effect? | Tách side effect khỏi vòng lặp |
| Có chính sách contention, overflow và liveness? | Bổ sung trước khi dùng sản phẩm |
| Đã đo performance trên workload thật? | Đừng khẳng định updater luôn nhanh hơn |

### Câu hỏi tự kiểm tra

**1.** `private volatile int count` có dùng với field updater được không?

**Có**, nếu updater được tạo trong ngữ cảnh có quyền truy cập private đó, ví dụ static factory nằm trong cùng class. Field **không cần public**.

**2.** Vì sao `private volatile Integer count` không hợp lệ?

Vì `AtomicIntegerFieldUpdater` yêu cầu **primitive `int`**, không phải reference `Integer`. Volatile trên reference không thay đổi kiểu field.

**3.** Hai thread đều gọi `incrementAndGet` lên cùng field updater và cùng object thì có mất update như `count++` không?

Không, với các thao tác updater theo giao thức hợp lệ, phép read–modify–write là nguyên tử. Nếu các đường ghi trực tiếp không an toàn cùng tồn tại, bảo đảm tổng thể có thể bị phá.

**4.** Một updater static có thể dùng cho hai object `Counter` không?

Có. Updater mô tả field target, và mỗi lần gọi nhận object cụ thể. Hai instance vẫn giữ giá trị riêng.

**5.** Dùng CAS cập nhật `state` có giúp cập nhật đồng thời thêm `progress` và `timestamp` như một transaction không?

Không. CAS trên một `int` chỉ bảo vệ chính trường đó. Cần một immutable snapshot chung được thay nguyên tử hoặc dùng khóa cho invariant nhiều trường.

**6.** Vì sao không nên đặt một lời gọi API tạo side effect vào lambda của `updateAndGet`?

Vì lambda có thể được gọi lại khi CAS thất bại do contention. Side effect có thể lặp lại dù chỉ một lần cập nhật cuối cùng thành công.

**7.** Nếu class chỉ có vài instance và không có chứng cứ về chi phí allocation, nên ưu tiên công cụ nào?

Thường bắt đầu với `AtomicInteger` để code dễ hiểu, rồi cân nhắc updater khi thật sự có lợi ích bộ nhớ/thiết kế được đo và kiểm chứng.

**Tóm lại:** `AtomicIntegerFieldUpdater` cho phép cập nhật atomic một **trường `volatile int` dạng instance** thông qua updater có thể chia sẻ. Dùng nó khi điều kiện field/access đáp ứng, giao thức cập nhật được kiểm soát và lợi ích về layout/allocation là đáng kể. Nó không thay thế các quyết định về invariant đa trường, safe publication, side effect, contention và tính đúng đắn của toàn hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-008">ABA problem và AtomicStampedReference</a>

<details>
<summary>Xem chi tiết</summary>

# ABA problem và AtomicStampedReference trong Java Concurrency

> **Phạm vi:** Java concurrency nói chung, thuộc chủ đề **Atomic & CAS**. Bài giải thích hiện tượng ABA, trường hợp ABA có thể phá tính đúng đắn của thuật toán lock-free, cơ chế ghép reference với stamp của `AtomicStampedReference`, cách viết CAS loop và những giới hạn thiết kế cần kiểm chứng. Không đi vào các API riêng của Spring.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Mô tả chính xác **compare-and-set (CAS)** kiểm tra điều gì và vì sao CAS thông thường không ghi nhận toàn bộ lịch sử của giá trị.
- Phân tích chuỗi thay đổi **A → B → A** và nhận ra khi nào một thread dùng snapshot cũ vẫn có thể CAS thành công.
- Phân biệt **ABA có xảy ra** với **ABA gây lỗi**: không phải mọi lần giá trị trở lại A đều làm chương trình sai.
- Giải thích `AtomicStampedReference<V>` lưu logic một **cặp (reference, int stamp)** và kiểm tra cả hai khi CAS.
- Sử dụng `get(int[] stampHolder)` để đọc reference và stamp thành **một snapshot nhất quán**, thay vì đọc tách hai thành phần trong lúc các thread khác cập nhật.
- Viết đúng `compareAndSet(expectedReference, newReference, expectedStamp, newStamp)`, đặt stamp mới theo một quy ước monotonic/unique phù hợp.
- Hiểu vì sao **stamp không tự bảo vệ mutable object nội bộ**, không thay thế memory reclamation, không làm nhiều state ngoài cặp trở nên atomic, và vẫn có nguy cơ tái sử dụng stamp.
- Viết bài thử nghiệm Java 17 có lịch thực thi kiểm soát để quan sát ABA với `AtomicReference` và cách `AtomicStampedReference` chặn một cập nhật dựa trên phiên bản cũ.

**Thông điệp chính:** CAS thường trả lời “giá trị hiện tại có bằng giá trị tôi đã đọc không?”. Nhưng nhiều thuật toán thực sự cần câu hỏi mạnh hơn: **“đây có còn là cùng một thế hệ/trạng thái mà tôi đã đọc không?”**. Stamp cung cấp thêm tín hiệu phiên bản để phát hiện thay đổi trung gian mà kiểm tra giá trị cuối đơn thuần bỏ lỡ.

## 2. Ôn lại CAS trước khi nói đến ABA

CAS là một thao tác read-check-write nguyên tử ở cấp biến atomic được API quy định. Ý tưởng:

```text
compareAndSet(expected, update):

    nếu current vẫn bằng expected:
        current = update
        trả true
    ngược lại:
        giữ nguyên current
        trả false
```

`AtomicReference<V>` là lớp Java SE dùng CAS trên **một tham chiếu object**. Ví dụ:

```java
AtomicReference<String> state = new AtomicReference<>("A");
boolean changed = state.compareAndSet("A", "B");
```

Đây là **đoạn minh họa**, cần import `java.util.concurrent.atomic.AtomicReference`. Với reference CAS, phép so sánh là **reference identity (`==`)**, không phải `String.equals()`. Ví dụ dùng string literal để trực quan, nhưng ứng dụng thực phải hiểu identity của từng object; trong ví dụ chạy đầy đủ ở dưới ta chủ động tạo các object `A`, `B` và `C` riêng, giữ cùng reference trong suốt lần thử.

CAS giải quyết **atomicity của một lần cập nhật**: giữa lúc kiểm tra expected và ghi update, thread khác không thể chen một cập nhật lên cùng biến atomic. Nhưng CAS **không lưu các trạng thái đã diễn ra trước đó**. Nếu giá trị hiện tại một lần nữa bằng expected, CAS thông thường có thể coi điều kiện thành công, dù đã có nhiều cập nhật ở giữa.

### Ví dụ CAS loop

```java
// Đoạn minh họa bên trong một method:
// ref là AtomicReference<State> đã được khởi tạo;
// transform(State) trả về trạng thái mới, không gây side effect.
for (;;) {
    State observed = ref.get();
    State next = transform(observed);
    if (ref.compareAndSet(observed, next)) {
        break;
    }
}
```

Đây là **mã minh họa, không phải chương trình độc lập**: `State`, `ref` và `transform()` cần được định nghĩa. Vòng lặp thử lại vì có thể thread khác thay đổi reference trước CAS của chúng ta.

**Lưu ý quan trọng:** vòng lặp CAS thất bại rồi thử lại giúp xử lý nhiều cuộc đua thông thường, nhưng **không tự giải quyết ABA**. Trong trường hợp giá trị đã đi A→B→A, CAS cũ **không thất bại**; không có cơ hội tự retry.

## 3. ABA problem: A → B → A

Giả sử thread T1 đọc tham chiếu hiện tại là A rồi chuẩn bị cập nhật. Trước khi T1 thực hiện CAS, thread T2 thay đổi A→B, sau đó B→A.

```text
Thời điểm             Thread T1                    Thread T2
-------------------   ---------------------------- --------------------------------
t0                    đọc current = A
t1                                                 CAS A -> B thành công
t2                                                 CAS B -> A thành công
t3                    CAS(expected=A, update=C)
t4                    -> thành công vì current=A
```

Ở t3, kiểm tra reference identity vẫn đúng: **giá trị cuối trỏ lại cùng object A**. CAS không phân biệt trạng thái A ở t0 với trạng thái A ở t2. Đây là hiện tượng **ABA**.

Nếu T1 chỉ cần đặt giá trị C **miễn current đang là A**, có thể kết quả vẫn đúng. Nhưng nếu T1 đã đưa ra một quyết định dựa trên giả định **“không có ai thay đổi state kể từ khi tôi quan sát”**, thì CAS thành công là tín hiệu **quá yếu**.

### ABA không đồng nghĩa với data race kiểu truy cập biến không đồng bộ

Với `AtomicReference`, mỗi lần đọc/CAS trên reference vẫn có memory semantics atomic đúng theo API. ABA có thể xảy ra **dù mọi truy cập biến atomic đều được đồng bộ đúng**.

Vì thế:

- **Data race / thiếu happens-before** là một loại vấn đề memory model.
- **ABA** thường là một vấn đề **logic/versioning** của thuật toán optimistic/lock-free: dữ liệu hiện tại có thể bằng dữ liệu đã đọc, nhưng lịch sử thay đổi khiến suy luận cũ không còn hợp lệ.
- Dùng `volatile` hay bảo đảm visibility đơn thuần **không khôi phục được lịch sử thay đổi** nếu chỉ giữ một giá trị.
- Một CAS thành công bảo đảm atomicity của chính thao tác CAS, **không bảo đảm** phần dữ liệu phụ thuộc đã được T1 quan sát trước đó vẫn hợp lệ.

## 4. Khi ABA thực sự nguy hiểm?

### Tình huống lock-free stack (mô hình)

Giả sử stack dùng một `AtomicReference<Node> head` và mỗi node có trường `next`:

```text
Trước:
head -> A -> B -> C

Thread T1:
  đọc oldHead = A
  đọc next = B
  chuẩn bị CAS(head, A, B)

Thread T2:
  pop A
  pop B
  push lại cùng object A (next của A giờ là C)
  head lại là A

Thread T1:
  CAS(head, A, B) thành công
```

T1 vẫn có reference A đúng identity nên CAS có thể thành công, nhưng nó sử dụng **next=B đã đọc từ thế hệ cấu trúc cũ**. Trong khi đó B có thể đã bị loại khỏi stack, nên việc đặt head=B có thể làm hỏng cấu trúc logic, làm mất node hoặc đưa node không còn hợp lệ vào stack.

Đây là **sơ đồ khái niệm**, không phải triển khai stack lock-free hoàn chỉnh. Các phiên bản thực tế cần cân nhắc việc node được tái sử dụng, ràng buộc mutation của `next`, publication, tiến triển, memory reclamation và chứng minh tính linearizable của toàn bộ thuật toán.

**Điểm gây lỗi không phải chỉ vì head quay lại A.** Điểm gây lỗi là T1 đang dùng **thông tin phụ thuộc vào một thế hệ cấu trúc đã thay đổi**, trong khi CAS chỉ xác minh một reference head.

### Một tình huống khác: trạng thái workflow

Giả sử trạng thái một công việc đang là `READY`. T1 đọc READY và quyết định thực hiện bước X sau khi tính toán chậm. Trong thời gian đó T2 chuyển READY→RUNNING→READY cho một vòng xử lý mới. T1 dùng CAS READY→RUNNING rồi nghĩ mình đã “đặt lock” cho vòng ban đầu. Nếu xử lý yêu cầu liên kết với đúng **lượt công việc**, cần một version/generation; chỉ kiểm tra enum READY là chưa đủ.

Ở tình huống này, đôi khi cách tốt hơn là mô hình hóa state có **generation ID** bất biến, chứ không nhất thiết dùng `AtomicStampedReference`. Cần lựa chọn theo invariant thực sự.

## 5. Java giải quyết kiểu ABA này như thế nào?

`AtomicStampedReference<V>` thuộc `java.util.concurrent.atomic`. Nó quản lý **một cặp logic**:

```text
(reference, stamp)
```

Trong đó:

- `reference` là tham chiếu đến object V.
- `stamp` là số nguyên `int` do **người viết chương trình** quy định ý nghĩa, thường được dùng làm version/generation.
- Thao tác CAS kiểm tra **cả expectedReference và expectedStamp**, rồi cập nhật **cả newReference và newStamp** như một thao tác atomic trên cặp.

Tạo biến:

```java
AtomicStampedReference<String> state =
        new AtomicStampedReference<>(initialReference, 0);
```

Đây là **đoạn minh họa trong một method hoặc field initializer**, cần import `java.util.concurrent.atomic.AtomicStampedReference`; `initialReference` là biến đã có.

Trình tự thay đổi:

```text
Trước:  (A, 0)
T2:     (A, 0) -> (B, 1)
T2:     (B, 1) -> (A, 2)
T1:     muốn CAS từ (A, 0) sang (C, 1)

Kết quả: FAIL, vì current = (A, 2), không còn là (A, 0).
```

Mặc dù reference cuối vẫn là A, **version đã khác**. Nếu T1 thực sự muốn cập nhật thế hệ mới, nó phải **đọc lại cặp (A, 2), tính lại bước tiếp theo theo dữ liệu mới**, rồi thử CAS mới.

### Ý nghĩa của tên “StampedReference”

Đừng hiểu stamp là “tự phát sinh” hay một mã thời gian của hệ điều hành. `AtomicStampedReference` **không tự cộng stamp**: mọi lần `compareAndSet` hoặc `set` cần caller cung cấp stamp mới. Nếu caller tái dùng stamp cũ hoặc reset về 0, ABA có thể quay trở lại.

Một convention thường gặp:

```text
current = (reference, version)
successful logical mutation:
    nextVersion = version + 1
    CAS(currentReference, nextReference, version, nextVersion)
```

Convention này cần được áp dụng **trên mọi đường cập nhật liên quan**. Các đường `set()`, `attemptStamp()` và logic retry phải cùng tuân thủ chính sách versioning. Không coi `int` version là duy nhất vĩnh viễn: `int` có thể tràn số và quay lại giá trị cũ sau đủ nhiều lần tăng, hoặc bị người gọi chủ động tái sử dụng.

## 6. Các API cốt lõi của AtomicStampedReference

| API | Vai trò | Điều cần ghi nhớ |
|---|---|---|
| `new AtomicStampedReference<>(initialRef, initialStamp)` | Khởi tạo cặp | `initialStamp` hoàn toàn do caller chọn |
| `getReference()` | Lấy reference hiện tại | Chỉ lấy một thành phần tại thời điểm gọi |
| `getStamp()` | Lấy stamp hiện tại | Chỉ lấy một thành phần tại thời điểm gọi |
| `get(int[] stampHolder)` | Lấy reference và ghi stamp vào `stampHolder[0]` | **Cách nên dùng để lấy snapshot cặp nhất quán** |
| `compareAndSet(expectedRef, newRef, expectedStamp, newStamp)` | Atomic CAS cho cặp | So sánh reference bằng **identity** và stamp bằng giá trị số nguyên |
| `set(newRef, newStamp)` | Đặt cặp mới | Không kiểm tra expected; caller phải tự bảo đảm policy/version |
| `attemptStamp(expectedRef, newStamp)` | Thử thay stamp nếu current reference là expectedRef | Hữu ích khi thật sự muốn đổi version mà không đổi reference |
| `weakCompareAndSet(...)` | API CAS yếu | Với API Java 17 của lớp này, xem đặc tả cụ thể; không mặc định thay bằng nó để bảo đảm tiến triển hay hiệu năng |

**Điểm cần đặc biệt nhớ:** `getReference()` rồi `getStamp()` là **hai lệnh riêng**. Nếu thread khác cập nhật ở giữa, bạn có thể tạo ra một cặp expectedReference/stamp **chưa từng tồn tại đồng thời**. Dùng `get(stampHolder)` để lấy cặp từ cùng một snapshot.

Ví dụ:

```java
int[] holder = new int[1];
String expectedRef = stamped.get(holder);
int expectedStamp = holder[0];

// Tính trạng thái kế tiếp từ cùng một snapshot:
String desiredRef = nextReference(expectedRef);
int desiredStamp = expectedStamp + 1;

boolean success = stamped.compareAndSet(
        expectedRef, desiredRef, expectedStamp, desiredStamp);
```

Đây là **đoạn minh họa trong method**, `stamped` và `nextReference()` đã được khai báo ở bên ngoài. `holder` cần có ít nhất một phần tử; với `get`, Java ghi stamp vào chỉ số 0.

Một CAS **thất bại** không phải là lỗi runtime: có thể thread khác vừa cập nhật cặp. Ứng dụng thường đọc lại state, tính lại desired và thử tiếp nếu contract cho phép, hoặc trả kết quả “conflict” cho caller.

## 7. Ví dụ Java 17 chạy độc lập: ABA với AtomicReference và AtomicStampedReference

Để tránh phụ thuộc vào “scheduler có vô tình tạo ra ABA không”, chương trình dưới đây **chủ động sắp thứ tự bằng `Thread.join()`**:

1. Thread chính giữ snapshot cũ.
2. Thread khác hoàn thành A→B→A rồi kết thúc.
3. Thread chính mới thực hiện CAS với snapshot cũ.
4. Lặp lại quy trình với cặp có stamp.

Chúng ta dùng **ba object `String` khác nhau** và so sánh bằng reference identity để tránh hiểu nhầm giữa `==` và `equals()`. Việc `join()` hoàn thành giúp bảo đảm thread chính quan sát cập nhật của worker sau khi worker kết thúc.

Tạo file **`AbaStampedDemo.java`**:

```java
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicStampedReference;

public class AbaStampedDemo {
    private static final String A = new String("A");
    private static final String B = new String("B");
    private static final String C = new String("C");

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<String> plain = new AtomicReference<>(A);
        String oldReference = plain.get();
        Thread firstChanger = new Thread(() -> {
            if (!plain.compareAndSet(A, B) || !plain.compareAndSet(B, A)) {
                throw new AssertionError("unexpected plain CAS failure");
            }
        });
        firstChanger.start();
        firstChanger.join();

        System.out.println("plainBackToA=" + (plain.get() == A));
        System.out.println("plainStaleCas=" + plain.compareAndSet(oldReference, C));
        System.out.println("plainNowC=" + (plain.get() == C));

        AtomicStampedReference<String> stamped = new AtomicStampedReference<>(A, 0);
        int[] stampHolder = new int[1];
        String originalReference = stamped.get(stampHolder);
        int originalStamp = stampHolder[0];

        Thread secondChanger = new Thread(() -> {
            if (!stamped.compareAndSet(A, B, 0, 1)
                    || !stamped.compareAndSet(B, A, 1, 2)) {
                throw new AssertionError("unexpected stamped CAS failure");
            }
        });
        secondChanger.start();
        secondChanger.join();

        System.out.println("stampedBackToA=" + (stamped.getReference() == A));
        System.out.println("stampedVersion=" + stamped.getStamp());
        System.out.println("stampedStaleCas="
                + stamped.compareAndSet(originalReference, C, originalStamp, originalStamp + 1));

        int[] latestStampHolder = new int[1];
        String latestReference = stamped.get(latestStampHolder);
        boolean freshCas = stamped.compareAndSet(
                latestReference, C, latestStampHolder[0], latestStampHolder[0] + 1);

        System.out.println("stampedFreshCas=" + freshCas);
        System.out.println("stampedFinalVersion=" + stamped.getStamp());
    }
}
```

Biên dịch/chạy với JDK 17 hoặc mới hơn:

```bash
javac --release 17 AbaStampedDemo.java
java AbaStampedDemo
```

**Stdout đã biên dịch và chạy kiểm chứng:**

```text
plainBackToA=true
plainStaleCas=true
plainNowC=true
stampedBackToA=true
stampedVersion=2
stampedStaleCas=false
stampedFreshCas=true
stampedFinalVersion=3
```

### Giải thích từng phần

**Phần `AtomicReference`:**

- T1/main đọc `oldReference = A`.
- Worker dùng CAS để chuyển **chính object reference** A→B→A. Hai lần CAS thành công; sau `join()`, current lại là object A.
- `plain.compareAndSet(oldReference, C)` trả `true` vì CAS chỉ thấy reference A đúng identity ở thời điểm cập nhật. **Nó không biết đã từng tồn tại B.**
- Kết quả `plainNowC=true` chỉ cho thấy CAS đã được thực hiện thành công; nếu hợp đồng thuật toán cần phát hiện mọi thay đổi kể từ lúc đọc, điều kiện này còn thiếu.

**Phần `AtomicStampedReference`:**

- T1 đọc đồng thời `(originalReference=A, originalStamp=0)` bằng `get(holder)`.
- Worker làm `(A,0) → (B,1) → (A,2)`.
- T1 thử `compareAndSet(A, C, 0, 1)`. Reference hiện tại đúng A, nhưng stamp hiện tại là 2, **khác expected stamp 0**, nên trả `false`.
- T1 đọc lại `(A,2)` rồi thực hiện một CAS mới với expected stamp 2 và desired stamp 3. Lần này thành công vì **không có thread khác chen vào** giữa snapshot mới và CAS mới trong ví dụ.
- `stampedFinalVersion=3` là kết quả của chính convention tăng version ta chọn, **không phải vì lớp tự động tăng stamp**.

**Vì sao ví dụ dùng `join()`?** Khi cần minh họa hai lịch rõ ràng, nó cho phép worker hoàn thành ABA trước khi main CAS. Trong workload thực, ABA xảy ra do các thread xen kẽ; ta không thể dựa vào lịch quan sát tự nhiên để luôn thấy tình huống ấy. Việc đặt các checkpoint như vậy là kỹ thuật thử nghiệm, không phải hướng dẫn triển khai thuật toán lock-free.

**Lưu ý về exception bên trong Thread:** nếu điều kiện `AssertionError` trong lambda bất ngờ xảy ra, `join()` không tự ném lại lỗi cho main như `Future.get()`; trong demo này ta đã chạy kiểm chứng đúng cả hai CAS của worker. Test production cần kênh báo lỗi rõ, ví dụ `Future` hoặc capture `Throwable` để assertion có thể fail cả test.

## 8. Cách viết vòng lặp stamped CAS an toàn về mặt logic

Khi muốn tính trạng thái mới từ reference đã đọc, mẫu phổ biến:

```java
// Minh họa trong một method: ref là AtomicStampedReference<State>.
// computeNext(State) chỉ tính toán, không gây side effect.
for (;;) {
    int[] stampBox = new int[1];
    State observed = ref.get(stampBox);
    int seenVersion = stampBox[0];

    State next = computeNext(observed);
    int nextVersion = seenVersion + 1;

    if (ref.compareAndSet(observed, next, seenVersion, nextVersion)) {
        break;
    }
    // Cặp đã thay đổi: đọc lại và tính next dựa trên snapshot mới.
}
```

Đây là **code minh họa**, không phải chương trình độc lập; `State`, `ref` và `computeNext()` cần có trong class. Với các thuật toán phức tạp, phải phân tích riêng tính đúng đắn của `computeNext`, quan hệ giữa state ngoài reference và reference CAS, chính sách reuse phiên bản, và cách đối phó contention.

### Không đặt side effect không thể lặp vào CAS loop

Ví dụ không an toàn về ý định:

```java
// Minh họa sai: side effect có thể chạy nhiều lần khi CAS fail.
for (;;) {
    int[] stampBox = new int[1];
    State s = ref.get(stampBox);
    sendEmail(); // Có thể gửi lặp nếu nhiều lần CAS thất bại!
    if (ref.compareAndSet(s, update(s), stampBox[0], stampBox[0] + 1)) {
        break;
    }
}
```

Đây là **đoạn minh họa lỗi**; `State`, `ref`, `sendEmail()`, `update()` thuộc ngữ cảnh ngoài. CAS loop có thể retry nhiều lần nên những side effect như gửi email, gọi API có phí, trừ tài khoản ngoài JVM hoặc ghi log audit cần xử lý riêng qua transaction/idempotency/commit protocol.

Mẫu tốt hơn là tính `next` thuần túy, CAS, rồi **sau khi thành công** kích hoạt side effect với cơ chế chống trùng/lỗi riêng. Ngay cả làm side effect sau CAS cũng không khiến thao tác ngoài JVM trở thành atomic cùng CAS: thread/process có thể crash giữa hai bước, nên cần giải pháp outbox/idempotency hoặc transaction khi bài toán yêu cầu.

### Không tự suy ra progress/fairness

`AtomicStampedReference` giúp tránh một lớp false-success CAS, nhưng không bảo đảm thread cụ thể cuối cùng sẽ thành công. Dưới contention rất cao, một thread có thể retry lâu. **Lock-free algorithm** là thuộc tính của **toàn thuật toán**, không phải thuộc tính suy ra từ việc có một AtomicStampedReference trong code. Một thuật toán dùng atomic nhưng gọi blocking I/O, chờ condition hoặc có vòng phụ thuộc vẫn có thể không lock-free.

## 9. Đọc snapshot đúng: `get(holder)` thay vì hai lệnh riêng

Một lỗi khó thấy:

```java
// Minh họa nguy cơ snapshot không nhất quán.
State refSnapshot = stamped.getReference();
int stampSnapshot = stamped.getStamp();
```

Đây là code minh họa, `stamped` là `AtomicStampedReference<State>`. Giữa hai lệnh, thread khác có thể đổi cặp:

```text
Trước:          (A, 10)
T1 getReference -> A
T2 cập nhật:   (B, 11)
T1 getStamp     -> 11

T1 thu được (A, 11), dù chưa hề tồn tại cặp đó trong khoảnh khắc đã quan sát.
```

T1 có thể làm suy luận sai trước cả khi CAS. Nếu nó CAS, thường CAS sẽ thất bại vì cặp ghép tạm không khớp, nhưng **không nên dựa vào CAS fail để sửa cách đọc state đã lệch**.

Mẫu đúng:

```java
int[] stampBox = new int[1];
State refSnapshot = stamped.get(stampBox);
int stampSnapshot = stampBox[0];
```

Cả hai giá trị phản ánh **cùng một snapshot logic** theo hợp đồng `get(int[])`. Nếu T2 đổi sau khi T1 đọc, CAS với expected pair cũ sẽ thất bại miễn chính pair đó chưa bị tái tạo.

**Lưu ý:** đây không phải snapshot atomic của mọi field bên trong `State`. Nếu `State` là object mutable mà thread khác đang sửa field của nó, `get(holder)` chỉ chụp **reference và stamp**; nó không đóng băng object graph.

## 10. So sánh identity với equality

`AtomicReference.compareAndSet()` và `AtomicStampedReference.compareAndSet()` kiểm tra **reference identity**, tức cùng một object, chứ không gọi `equals()`. Ví dụ:

```java
String a1 = new String("A");
String a2 = new String("A");

System.out.println(a1.equals(a2)); // true
System.out.println(a1 == a2);      // false
```

Đoạn trên có thể đặt trong method main bất kỳ, không cần import. Nếu `AtomicStampedReference<String>` đang giữ `a1`, thì:

```java
// Minh họa với stamped giữ (a1, 5).
boolean ok = stamped.compareAndSet(a2, a1, 5, 6);
// ok == false: a2 không cùng identity với a1.
```

Đây là **đoạn minh họa** với `stamped`, `a1`, `a2` đã khai báo. Stamp khớp nhưng reference **không khớp**. Trong ví dụ chạy chính, ta cố ý giữ A/B/C thành ba object riêng và sử dụng cùng instance A khi quay về, để hiện tượng ABA xảy ra theo nghĩa CAS reference identity.

**Không dùng object wrapper mới một cách tùy tiện** rồi cho rằng “giá trị A trở lại A” sẽ đủ để gây ABA trên `AtomicReference`. Đối với reference CAS, cần current quay về **chính object reference** được quan sát, hoặc cần thiết kế khác có cùng token value. Trong thực tế lock-free structure, việc **tái sử dụng node/reference** làm ABA nguy hiểm.

## 11. Stamp không tự động bảo vệ object bên trong

Ví dụ:

```java
final class MutablePayload {
    int amount;
}

// Minh họa: ref đang giữ một MutablePayload cùng stamp=4.
// Thread T2 giữ chính payload và làm payload.amount = 999
// nhưng KHÔNG thay cặp trong AtomicStampedReference.
```

Đây là class và mô tả minh họa. T1 có thể đọc reference/stamp `(payload,4)`, T2 sửa `payload.amount` bên ngoài atomic container, rồi T1 CAS dựa trên stamp 4 vẫn thành công. **Stamp không thay đổi**, nên nó không phát hiện mutation của referent. Nếu cập nhật field bình thường xảy ra đồng thời mà thiếu synchronization, còn có nguy cơ **data race** theo JMM.

Một chính sách đúng có thể là:

- Chỉ lưu **immutable state objects** trong AtomicStampedReference; mọi thay đổi tạo object state mới rồi thay cả cặp bằng CAS.
- Hoặc bảo vệ việc sửa mutable referent theo giao thức lock/ownership khác rõ ràng, và đảm bảo cập nhật stamp khi cần.
- Không xuất bản tham chiếu mutable để thread khác tùy ý đổi trong khi thuật toán đang dựa vào tính bất biến của snapshot.

**Tương tự với object graph nhiều cấp:** việc CAS một reference atomic không bảo đảm một thao tác ghi file, cập nhật database hoặc thay ba collection độc lập trở thành một giao dịch thống nhất.

## 12. Stamp overflow, tái sử dụng và giới hạn chống ABA

`stamp` là một `int` Java (32 bit, có dấu). Nếu mỗi lần cập nhật dùng `stamp + 1`, phép cộng có thể **overflow** rồi tiếp tục vòng qua các giá trị cũ. Sau đủ nhiều cập nhật, một cặp `(A, oldStamp)` về mặt lý thuyết **có thể xuất hiện trở lại**.

Ngoài overflow, ABA vẫn có thể xảy ra nếu code:

- Dùng `set(A, oldStamp)` sau khi state thay đổi.
- Reset stamp về 0 khi reload/reinitialize nhưng reader cũ còn tồn tại.
- Có nhiều writer tự chọn newStamp không theo chính sách tăng version.
- Dùng `attemptStamp` hoặc update path khác mà không tuân thủ quy tắc version.
- Áp dụng modulo một tập nhỏ stamp, khiến cặp bị tái sử dụng rất nhanh.

Vì vậy, `AtomicStampedReference` **giảm đáng kể nguy cơ stale CAS theo generation khi version được quản lý đúng**, nhưng không cung cấp bảo đảm “không bao giờ ABA trong mọi hoàn cảnh”.

### Lựa chọn chiến lược version

Tùy yêu cầu:

1. **Chương trình tồn tại không lâu và số mutation bị giới hạn:** một int stamp tăng monotonically trong phạm vi hợp lệ có thể đủ; cần chứng minh không wrap/reuse khi có observer sống lâu.
2. **Không được phép trùng generation theo yêu cầu nghiêm ngặt:** thiết kế version domain lớn hơn, immutable state mang generation/UUID hoặc một cơ chế quản lý epoch phù hợp. `AtomicStampedReference` **chỉ cung cấp int stamp**; không thể giả vờ nó là long/UUID.
3. **Cấu trúc node tái sử dụng:** phối hợp version tagging với cơ chế ownership/reclamation của cấu trúc; versioning đơn lẻ không giải quyết mọi vấn đề của node lifecycle.
4. **Không cần nhận biết generation:** không nên áp stamp chỉ vì nghe nói CAS “có ABA”; tăng phức tạp không mang thêm correctness nếu invariant thực sự chỉ dựa trên current value.

Nói cách khác, trước khi dùng stamped reference, phải xác định **khoảng thời gian một observer có thể giữ snapshot cũ**, tốc độ cập nhật cặp, và khả năng vòng đời/phiên bản quay lại giá trị trước đó.

## 13. AtomicMarkableReference có tương đương không?

Java còn có `AtomicMarkableReference<V>`, quản lý cặp `(reference, boolean mark)`. Một mark boolean rất hữu ích trong các thuật toán dùng đánh dấu logic, ví dụ “node đã bị logically deleted chưa?”. Tuy nhiên, với bài toán phát hiện **bao nhiêu thế hệ đã đi qua**, một bit mark có thể quay 0→1→0 nên không đủ phân biệt mọi ABA.

| Lớp/API | Trạng thái atomic được quản lý | Phù hợp nhất |
|---|---|---|
| `AtomicReference<V>` | Một reference | Chuyển trạng thái chỉ cần kiểm tra current identity |
| `AtomicStampedReference<V>` | Reference + `int stamp` | Cần kiểm tra một version/generation bên cạnh reference |
| `AtomicMarkableReference<V>` | Reference + boolean mark | Cần thêm trạng thái mark, ví dụ logical deletion |
| `AtomicInteger` / `AtomicLong` | Một giá trị số | Counter/flags/các phép số nguyên atomic |
| `synchronized` / `ReentrantLock` | Critical section bảo vệ nhiều field | Cần atomic transaction logic trên nhiều giá trị, không nhất thiết lock-free |

Không có lớp nào mặc nhiên “tốt nhất”. Quyết định dựa trên **semantics của invariant**: cần ngăn writer khác cập nhật đồng thời, hay cần phát hiện lịch sử phiên bản, hay cần đánh dấu logical removal?

## 14. Stamp và tính linearizable

**Linearizability** là tính chất một operation đồng thời có thể được xem như xảy ra tại một điểm nguyên tử nào đó giữa lúc bắt đầu và lúc kết thúc, đồng thời tôn trọng thứ tự thời gian của các operation không chồng lấn.

Với `AtomicStampedReference.compareAndSet`, việc kiểm tra/cập nhật **cặp (reference, stamp)** là một atomic action, giúp programmer xây dựng linearization point cho thao tác cập nhật cặp.

Tuy nhiên:

- Toàn bộ một phương thức nghiệp vụ có thể thực hiện nhiều bước khác ngoài CAS: đọc dữ liệu, validate, ghi side effect, công bố event.
- Một CAS atomic **không tự chứng minh toàn bộ phương thức linearizable**.
- Nếu hai field bên ngoài atomic pair phải chuyển đồng thời, cần giao thức khác bảo vệ cả nhóm hoặc biến chúng thành **một immutable state object** được thay nguyên tử.
- Nếu reader cần thấy generation mới và payload đồng nhất, payload phải được xây dựng/published an toàn và không bị mutate trái phép sau đó.
- Nếu owner/reclamation làm một node không còn hợp lệ trong lúc reader đang dùng, stamp ở head không tự bảo vệ reader khỏi mọi dạng lỗi lifecycle.

Đây là lý do một ví dụ `AtomicStampedReference` chạy đúng vài lần **không phải bằng chứng** một lock-free linked list/stack triển khai hoàn chỉnh đã đúng.

## 15. Java Memory Model liên quan thế nào?

Các lớp atomic của `java.util.concurrent.atomic` định nghĩa memory effects cho những thao tác của chúng. Trên Java 17, đọc thông thường qua `AtomicReference.get()` và các atomic CAS có các hiệu ứng bộ nhớ được API quy định, tương thích với các quy tắc liên quan đến volatile và atomic read-modify-write.

Một snapshot `get(holder)` đủ để lấy cặp reference/stamp nhất quán và một CAS giúp cập nhật cặp nguyên tử, nhưng vẫn phải xét:

- **Publication của object tham chiếu:** nếu object được tạo rồi công bố qua atomic container đúng cách, các ghi chuẩn bị dữ liệu trước publication được phối hợp theo memory semantics thích hợp.
- **Mutability sau publication:** nếu nhiều thread cùng sửa các trường thường bên trong object, cặp atomic không làm những ghi ấy có mutual exclusion hoặc trở thành atomically visible.
- **Không dựa vào `Thread.sleep` làm ordering:** việc scheduler nhường CPU hay chờ một khoảng thời gian không phải quan hệ HB thay thế atomic/lock/latch/join.
- **CAS loop có thể thất bại vì contention:** xử lý retry/failure theo contract; không suy từ một thất bại rằng chắc chắn có ABA—bất kỳ cập nhật cạnh tranh nào cũng có thể làm expected pair sai.

Ở ví dụ thực hành, `Thread.join()` làm thứ tự các bước thử nghiệm được cố định và bảo đảm worker hoàn thành trước khi main tiếp tục. Trong thuật toán concurrent thực, correctness phải đúng **bất kể interleaving nào hợp lệ** theo hợp đồng.

## 16. Đừng nhầm ABA với các vấn đề khác

| Dấu hiệu | Có thể là | Phương pháp phân tích |
|---|---|---|
| CAS thất bại liên tục dưới contention | Cập nhật cạnh tranh / retry starvation | Đo số lần retry, đọc lại expected và new state |
| CAS thành công sau A→B→A nhưng dựa trên snapshot cũ | ABA gây stale decision | Thêm generation/stamp nếu invariant cần |
| CAS thành công nhưng reader thấy field mutable bất thường | Data race trên referent | Kiểm tra safe publication và ownership/lock của payload |
| Hai field không nhất quán dù đã dùng atomic cho từng field | Multi-field invariant bị chia | Gom state thành immutable snapshot hoặc khóa cả transaction |
| Node trong stack bị tái sử dụng với old `next` | ABA và lifecycle/node reuse | Version tagging + algorithm/reclamation audit |
| Consumer nhận event lặp do retry | Side effect đặt trong CAS loop | Tách side effect khỏi retry, idempotency/outbox |
| Version quay lại ban đầu | Wrap-around/tái sử dụng stamp | Xem policy và tuổi thọ snapshot |
| Thứ tự output giữa hai thread thay đổi | Nondeterministic scheduling | Xem yêu cầu nghiệp vụ; không phải ABA nếu không có stale-snapshot false success |

Dùng cùng một từ “race condition” cho mọi lỗi dễ làm chọn sai công cụ. Hãy xác định **điều gì đã được đọc, khi nào giá trị thay đổi, và thao tác cuối xác minh được gì**.

## 17. Thiết kế phiên bản và bất biến: ví dụ tài nguyên logic

Giả sử một trạng thái hệ thống chứa `RecordState(id, status, generation)`. Thread T1 đọc trạng thái “READY thế hệ 7” và chuẩn bị một bước xử lý. Thread T2 có thể hoàn tất thế hệ 7, khởi tạo thế hệ 8, rồi status trở lại READY.

Nếu T1 chỉ CAS enum `READY→RUNNING`, nó không phân biệt hai vòng xử lý. Nếu việc xử lý phải gắn với đúng thế hệ, cần expected **generation 7** được kiểm tra khi nhận quyền.

Có hai cách modeling thông dụng:

**Cách A — AtomicStampedReference:** reference là token/immutable state và stamp là generation, mỗi transition tăng stamp. Consumer dùng `get(holder)` rồi CAS expected pair.

**Cách B — AtomicReference tới immutable object có generation:** tham chiếu trỏ tới một record mới cho mỗi transition. Reader chỉ CAS từ **chính object snapshot cũ** sang object trạng thái mới. Nếu không bao giờ tái sử dụng instance cũ, reference identity sẽ khác ở mỗi generation, nên loại ABA do quay về **cùng reference** trong mô hình đó. Tuy nhiên, phải kiểm soát mọi đường cập nhật và không tái sử dụng object snapshots.

Cách B phù hợp khi nhiều field là một invariant phải cập nhật nguyên tử. Cách A có ý nghĩa khi reference hiện tại có thể quay về cùng identity nhưng phiên bản vẫn cần tách biệt. Không có sự thay thế máy móc một-cho-một mà không xét kiểu dữ liệu và ràng buộc thiết kế.

## 18. Những lỗi triển khai thường gặp

1. **Dùng `getReference()` và `getStamp()` tách biệt rồi tin đó là snapshot.** Có thể có cập nhật ở giữa. Dùng `get(holder)`.
2. **Chỉ tăng stamp khi reference khác.** Nếu thao tác logic thay đổi thế hệ rồi quay về cùng reference, bạn đã tự tái tạo ABA; cần quy ước version cho **mọi mutation có ý nghĩa**.
3. **Reset stamp bằng `set` tùy tiện.** Có thể tái sử dụng cặp cũ mà observer vẫn giữ.
4. **Cho rằng lớp tự tăng stamp.** Mọi `newStamp` do caller cung cấp. `compareAndSet` không tự cộng.
5. **Dùng `equals()` để chọn expected object.** CAS reference so theo `==`; hai object nội dung bằng nhau không nhất thiết cùng reference.
6. **Giữ mutable payload và sửa field trực tiếp.** Stamp không tự phát hiện mutation ngoài container.
7. **Đặt email/network write trong CAS retry.** Retry có thể lặp side effect. Phải có idempotency/transaction riêng.
8. **Cho rằng int stamp duy nhất vô thời hạn.** Overflow hoặc manual reuse có thể tạo lại old pair.
9. **Lạm dụng AtomicStampedReference cho mọi counter.** Nếu invariant chỉ cần tăng nguyên tử, `AtomicInteger.incrementAndGet` đơn giản hơn.
10. **Cho rằng ABA luôn làm kết quả sai.** Nếu quyết định chỉ dựa trên state hiện tại A, ABA có thể không ảnh hưởng. Phải chỉ ra stale assumption bị vi phạm.
11. **Cho rằng CAS của head bảo vệ mọi field node.** Algorithm phải chứng minh cả cấu trúc và lifecycle của node, không chỉ head.
12. **Không xử lý thất bại CAS.** Thất bại có thể là contention bình thường; code cần retry, trả conflict hoặc chiến lược khác.
13. **Tạo desired state từ snapshot cũ rồi retry CAS với expected mới mà không tính lại.** Kết quả mới có thể dựa trên một invariant đã lỗi thời.
14. **Tưởng stamp là timestamp từ đồng hồ.** Nó là int do caller quản lý; không tự có quan hệ với thời gian thực.
15. **Cho rằng giải quyết ABA tức có lock-free, wait-free và fairness.** Đây là thuộc tính riêng của toàn thuật toán.
16. **Suy từ một test pass rằng không thể ABA nữa.** Phải phân tích mọi đường thay đổi stamp và giới hạn vòng đời/version.

## 19. Cách thử nghiệm ABA có tính tái hiện

Một chiến lược thử nghiệm hiệu quả hơn việc stress ngẫu nhiên ngay từ đầu:

1. **Xác định invariant:** ví dụ “nếu state đã trải qua một thế hệ khác từ lúc tôi đọc thì không được commit dựa trên snapshot cũ”.
2. **Đặt snapshot đầu tiên:** thread T1 đọc A và (nếu có) version 0.
3. **Tạo lịch cưỡng bức:** dùng `CountDownLatch`, `CyclicBarrier` hoặc `Thread.join()` để T2 hoàn thành A→B→A trong khi T1 vẫn giữ expected cũ.
4. **Thử CAS của T1:** `AtomicReference` sẽ chấp nhận nếu identity là A; stamped CAS với version 0 sẽ thất bại nếu version hiện tại là 2.
5. **Đọc lại và chứng minh retry:** T1 lấy cặp mới rồi tính lại desired; nếu không còn cạnh tranh, CAS có thể thành công.
6. **Kiểm tra payload/structure invariants:** không chỉ assert true/false của CAS; nếu algorithm là stack, kiểm tra không mất node, không tạo cycle, không truy cập node không còn hợp lệ.
7. **Thử đường sai:** cố ý reset stamp, bỏ tăng version, mutate referent nội bộ để thấy những điều `AtomicStampedReference` không bảo đảm.
8. **Thử stress/real contention:** nhiều thread, nhiều iteration, dữ liệu ngẫu nhiên có kiểm soát và assertions. Thất bại là bằng chứng bug; nhiều lần pass không phải chứng minh formal correctness.

Trong demo runnable, `Thread.join()` là cách đơn giản để buộc T2 hoàn thành trước T1 CAS. Trong một bài test thuật toán lock-free thật, một hoặc nhiều barrier/latch giúp kiểm soát checkpoint **mà không cần dùng sleep**.

### Về benchmark

`AtomicStampedReference` có thể phát sinh chi phí quản lý cặp phiên bản, contention và allocation tùy triển khai. Đừng khẳng định nó nhanh hơn/chậm hơn mọi CAS thường mà không đo đúng workload/JDK. Benchmark cần so sánh các phiên bản **đều đúng về logic**; một thuật toán bỏ stamp có thể “nhanh hơn” nhưng sai trong lịch ABA quan trọng.

Nếu đo contention và latency, cân nhắc JMH cho microbenchmark Java. Tránh benchmark chỉ một lần bằng `System.nanoTime()` rồi xem chênh lệch nhỏ là kết luận chắc chắn.

## 20. Khi nào nên và không nên dùng AtomicStampedReference?

**Nên cân nhắc khi:**

- Logic yêu cầu phát hiện **đã có transition từ lúc snapshot**, kể cả current reference quay về object ban đầu.
- Đã có thiết kế optimistic CAS với một reference có thể được **tái sử dụng**.
- Có chính sách stamp rõ ràng, không tái sử dụng trong lifetime observer có liên quan.
- Muốn giữ reference + version là một atomic pair mà không đặt lock cho phần cập nhật đó.
- Có khả năng kiểm chứng mọi read/write path đi qua atomic container, không mutate referent trái quy tắc.

**Không mặc định dùng khi:**

- Chỉ cần `counter++` hoặc phép update một giá trị độc lập: `AtomicInteger`/`AtomicLong` hợp hơn.
- Cần bảo vệ invariant của nhiều mutable field không thể gói vào một immutable snapshot: `synchronized`/`ReentrantLock` thường rõ hơn.
- Dữ liệu đã được thiết kế immutable và mỗi transition tạo **instance state mới duy nhất**; `AtomicReference<ImmutableState>` có thể đủ.
- Có thuật toán collection sẵn có như `ConcurrentLinkedQueue`, `ConcurrentHashMap`, `BlockingQueue`: ưu tiên primitive được kiểm chứng thay vì tự triển khai cấu trúc lock-free.
- Cần “version không bao giờ trùng” trong vòng đời cực dài mà `int` không đáp ứng; phải chọn version domain/architecture khác.
- Muốn giải quyết serialization xuyên database/process: atomic Java chỉ bảo vệ state trong JVM phù hợp, không tự thành distributed transaction.

## 21. Câu hỏi tự kiểm tra và đáp án

**Câu 1.** T1 đọc reference A. T2 CAS A→B→A và T1 tiếp tục CAS A→C. Vì sao CAS thường có thể trả true?  
**Đáp án:** CAS thường chỉ so reference hiện tại với expected A. Khi current đã trở lại chính object A, nó không có thông tin về B đã tồn tại ở giữa.

**Câu 2.** Chỉ cần phát hiện A→B→A là đủ kết luận có bug?  
**Đáp án:** Không. ABA chỉ gây lỗi nếu T1 có giả định rằng state/structure chưa thay đổi kể từ snapshot và giả định ấy là yêu cầu correctness.

**Câu 3.** Tại sao `AtomicStampedReference` ngăn ví dụ với `(A,0)→(B,1)→(A,2)`?  
**Đáp án:** T1 kỳ vọng cả reference A **và** stamp 0; current stamp 2 nên CAS cũ thất bại.

**Câu 4.** `AtomicStampedReference` có tự tăng stamp khi gọi `compareAndSet` không?  
**Đáp án:** Không. Caller truyền newStamp. Phải quy định mọi đường mutation có ý nghĩa đều cập nhật version.

**Câu 5.** Dùng `getReference()` rồi `getStamp()` có chắc là cùng snapshot không?  
**Đáp án:** Không; có thể thread khác cập nhật giữa hai call. Dùng `get(int[] stampHolder)`.

**Câu 6.** Hai instance `new String("A")` có `equals` nhau; CAS reference có coi là cùng expected không?  
**Đáp án:** Không. Reference CAS dùng identity `==`, không dùng `equals()`.

**Câu 7.** Referent `Payload` bị sửa `payload.value` nhưng reference và stamp không đổi. Stamped CAS có tự phát hiện không?  
**Đáp án:** Không. Nó chỉ kiểm tra cặp reference/stamp. Mutable fields phải được bảo vệ bằng state design/ownership/lock khác.

**Câu 8.** Vì sao `int stamp` có thể không đủ trong hệ thống dài hạn?  
**Đáp án:** Nó có số giá trị hữu hạn và có thể overflow/wrap, hoặc bị code chủ động reset, cho phép cặp cũ xuất hiện lại.

**Câu 9.** Lỗi của stale `next` trong lock-free stack có được giải quyết hoàn toàn chỉ bằng AtomicStampedReference?  
**Đáp án:** Không thể khẳng định như vậy. Stamp giúp ngăn CAS dựa trên version head cũ, nhưng còn cần kiểm chứng toàn bộ thuật toán, node reuse và memory reclamation/lifecycle.

**Câu 10.** Có thể gọi side effect bên trong vòng lặp CAS retry không?  
**Đáp án:** Không nên với side effect không thể lặp. CAS có thể fail nhiều lần nên tác động có thể bị thực hiện lặp, và CAS không tạo transaction với external systems.

**Câu 11.** Nếu CAS với stamp cũ thất bại, sửa bằng việc lấy latest stamp rồi CAS ngay với desired tính từ snapshot cũ có đúng không?  
**Đáp án:** Không nhất thiết. Phải đọc lại **cả reference/stamp**, kiểm tra invariant và **tính lại desired** theo snapshot mới trước khi retry.

**Câu 12.** `AtomicMarkableReference` và `AtomicStampedReference` có cùng công dụng không?  
**Đáp án:** Không. Markable dùng boolean mark, phù hợp đánh dấu logic; stamped dùng int version, hữu ích để phân biệt các thế hệ thay đổi.

## 22. Tài liệu tham khảo

- [Java SE 17 — AtomicStampedReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicStampedReference.html): constructor, `get(int[])`, `compareAndSet`, `set`, `attemptStamp`.
- [Java SE 17 — AtomicReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html): `get`, `set`, `compareAndSet`.
- [Java SE 17 — AtomicMarkableReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicMarkableReference.html): reference kết hợp boolean mark.
- [Java SE 17 — java.util.concurrent.atomic](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/package-summary.html): giới thiệu các atomic classes và memory effects.
- [Java Language Specification Java SE 17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): data race, happens-before và memory ordering.
- [Java SE 17 — Thread](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): `start`, `join` và các quan hệ completion.

**Tóm tắt cuối:** **ABA = current trở về A sau khi đã thay đổi ở giữa**. CAS thường kiểm tra current identity nên có thể cho stale update thành công. `AtomicStampedReference` bổ sung **generation/stamp** để CAS kiểm tra một **cặp (reference, version)**. Nó có hiệu quả khi **mọi mutation quan trọng tăng version, version không bị tái dùng trong khoảng có observer, referent được quản lý an toàn và thuật toán được chứng minh trọn vẹn**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-009">LongAdder và LongAccumulator khi cập nhật cạnh tranh</a>

<details>
<summary>Xem chi tiết</summary>

# LongAdder và LongAccumulator khi cập nhật cạnh tranh trong Java

Khi hàng chục hoặc hàng trăm thread liên tục cộng vào **một bộ đếm dùng chung**, mỗi lần cập nhật cùng một biến nguyên tử có thể tranh chấp. Trong Java, `AtomicLong` cung cấp các phép đọc–sửa–ghi nguyên tử rất phù hợp nếu ta cần **một giá trị có thể đọc ngay tại từng thời điểm** và các thao tác như `getAndIncrement()`. Nhưng với các số liệu **thống kê được cộng dồn liên tục** (lượt truy cập, số sự kiện, số byte), việc mọi thread cùng tranh một điểm cập nhật có thể trở thành nút nghẽn.

`LongAdder` và `LongAccumulator` trong package `java.util.concurrent.atomic` là hai công cụ chuyên dụng của JDK: thay vì bắt mọi cập nhật tranh chấp trên một ô lưu trữ duy nhất, implementation có thể **phân tán cập nhật qua nhiều ô nội bộ**, rồi gộp lại khi cần đọc. Chúng thường cải thiện throughput dưới cạnh tranh cao, đổi lại **chi phí bộ nhớ lớn hơn và phép đọc tổng không phải snapshot nguyên tử**.

Bài này thuộc **Atomic & CAS**, tập trung vào **Java concurrency nói chung, Java 17+**. Không sử dụng tính năng đồng thời riêng của Spring.

Mục tiêu:
- Phân biệt chính xác `AtomicLong`, `LongAdder` và `LongAccumulator` theo semantics chứ không chỉ tốc độ.
- Dùng `LongAdder.increment()`, `add()`, `sum()`, `reset()`, `sumThenReset()` đúng ngữ cảnh.
- Thiết kế hàm và identity cho `LongAccumulator`; hiểu vì sao **associative, commutative, side-effect-free** là điều kiện quan trọng.
- Giải thích tại sao đọc một tổng hoặc reset trong lúc cập nhật **không cho kết quả snapshot/ranh giới thời gian chính xác**.
- Chọn công cụ cho metrics, đếm theo key, phép max/min, và nhận ra khi nào cần một cập nhật tuyến tính hóa của `AtomicLong` hay khóa.

## 1. Vấn đề: một điểm cập nhật chung dưới contention

Giả sử nhiều thread cùng tăng số sự kiện đã xử lý:

~~~java
// Minh họa logic, chưa phải file chạy độc lập.
private final AtomicLong events = new AtomicLong();

void onEvent() {
    events.incrementAndGet();
}
~~~

`AtomicLong.incrementAndGet()` đảm bảo mỗi lần tăng là một phép **read–modify–write nguyên tử** trên cùng một giá trị. Đó là ưu điểm lớn: nếu caller cần **giá trị mới của chính lần tăng**, API trả về ngay con số ấy. Tuy nhiên, khi có nhiều thread liên tục cập nhật cùng một điểm, việc đồng bộ cạnh tranh quanh giá trị này có thể làm tăng số lần retry/chi phí cache-coherence trên phần cứng.

Thường ta có hai nhóm nhu cầu khác nhau:

1. **Trạng thái điều khiển / quyết định chính xác:** cấp số thứ tự duy nhất, kiểm tra giới hạn rồi tăng, thay đổi trạng thái theo một giao thức. Cần atomicity của **một thời điểm cập nhật**; `AtomicLong` hoặc một primitive/khóa phù hợp.
2. **Số liệu thống kê:** mỗi thread chỉ muốn thông báo “thêm 1”, “thêm 64 byte” hoặc “cập nhật max”. Caller không cần biết số mới sau từng lần cập nhật; một giá trị đọc trong khi các thread vẫn ghi có thể là **số đo nhất thời**. `LongAdder`/`LongAccumulator` thích hợp để cân nhắc.

**Không phải cứ có nhiều thread thì LongAdder luôn nhanh hơn AtomicLong.** Trong tranh chấp thấp, khác biệt có thể nhỏ; với workload ít ghi, đọc rất nhiều hoặc cần bộ nhớ nhỏ, `AtomicLong` có thể phù hợp hơn. Hãy chọn theo **đúng semantics trước**, rồi benchmark thực tế.

## 2. LongAdder: cộng dồn long với nhiều thread

`java.util.concurrent.atomic.LongAdder` duy trì một tổng kiểu `long`, khởi đầu từ **0**. Những phương thức chính:

| Phương thức | Ý nghĩa | Trả về |
| --- | --- | --- |
| `add(long x)` | Cộng `x` (được phép âm) | `void` |
| `increment()` | Tương đương `add(1)` | `void` |
| `decrement()` | Tương đương `add(-1)` | `void` |
| `sum()` | Cộng các giá trị nội bộ để trả tổng hiện quan sát được | `long` |
| `longValue()` | Tương đương `sum()` | `long` |
| `reset()` | Đặt các ô nội bộ về 0 | `void` |
| `sumThenReset()` | Lấy tổng và reset, hữu ích giữa các giai đoạn khi đã hết writer | `long` |

Mỗi `add`/`increment` đóng góp vào tổng được duy trì bằng cơ chế đồng thời của class. Dưới contention, implementation có thể phân tán cập nhật qua một **base** và các **cell** (ô phụ). Khi đọc `sum()`, nó tổng hợp các thành phần này.

~~~text
Mô hình ý tưởng (không phải layout cố định của mọi JVM):

             LongAdder
       +--------------------+
       | base: một phần tổng |
       | cells khi cần:      |
       |  cell[0]: +8        |
       |  cell[1]: +6        |
       |  cell[2]: +9        |
       +--------------------+
             |
             v
       sum() -> tổng hợp

Các thread có thể cập nhật những cell khác nhau để giảm tranh chấp.
~~~

Một thread cụ thể **không được API bảo đảm gắn mãi với một cell**, số lượng cell không phải thông số API cố định, và số cell có thể tăng khi tranh chấp. Đây là giải thích thiết kế, không phải lời hứa về representation/hashing bên trong mọi phiên bản JDK.

### 2.1. Ví dụ Java 17+: bốn thread cập nhật 40.000 lần

~~~java
// LongAdderContentionDemo.java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAdder;

public class LongAdderContentionDemo {
    public static void main(String[] args) throws InterruptedException {
        int workers = 4;
        int perWorker = 10_000;

        LongAdder total = new LongAdder();
        CountDownLatch begin = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);

        for (int i = 0; i < workers; i++) {
            Thread t = new Thread(() -> {
                try {
                    begin.await();
                    for (int n = 0; n < perWorker; n++) {
                        total.increment();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            t.start();
        }

        begin.countDown();
        done.await();
        System.out.println("Total: " + total.sum());
    }
}
~~~

~~~bash
javac LongAdderContentionDemo.java
java LongAdderContentionDemo
~~~

Trong lần chạy bình thường, không có interrupt bất ngờ, stdout:

~~~text
Total: 40000
~~~

**Giải thích:** Bốn worker mỗi worker đóng góp 10.000. Main chỉ gọi `sum()` **sau** khi `done.await()` đã xác nhận cả bốn task hoàn tất. Các hành động trước `countDown()` được công bố theo memory-consistency của `CountDownLatch`; không còn cập nhật đồng thời khi main tính tổng. Vì vậy `sum()` cho kết quả chính xác trong ví dụ này.

Nếu main gọi `total.sum()` **trong lúc worker còn cập nhật**, nó chỉ nhận một giá trị nhất thời, **không phải snapshot nguyên tử**.

### 2.2. Tăng, giảm, cộng lượng âm

~~~java
// LongAdderOperationsDemo.java
import java.util.concurrent.atomic.LongAdder;

public class LongAdderOperationsDemo {
    public static void main(String[] args) {
        LongAdder value = new LongAdder();

        value.add(10);
        value.increment();
        value.decrement();
        value.add(-3);
        System.out.println("Sum: " + value.sum());

        long finished = value.sumThenReset();
        System.out.println("Finished: " + finished);
        System.out.println("After reset: " + value.sum());
    }
}
~~~

~~~bash
javac LongAdderOperationsDemo.java
java LongAdderOperationsDemo
~~~

Stdout:

~~~text
Sum: 7
Finished: 7
After reset: 0
~~~

`sumThenReset()` được gọi lúc **không có writer**, nên không gây tranh chấp với reset; vì thế các con số trên xác định. `LongAdder` không giới hạn ở phép tăng dương; `add(-3)` hợp lệ. Tuy nhiên, một cặp `sum()` rồi `add(-amount)` không tự tạo giao dịch nghiệp vụ “kiểm tra hạn mức rồi trừ”, vì `sum` không phải điểm đồng bộ cho invariant đó.

## 3. LongAccumulator: tích lũy theo phép toán tự chọn

`java.util.concurrent.atomic.LongAccumulator` tổng hợp giá trị `long` bằng một phép toán do caller cung cấp:

~~~java
new LongAccumulator(accumulatorFunction, identity);
~~~

- `accumulatorFunction` là `java.util.function.LongBinaryOperator`, có signature `long applyAsLong(long left, long right)`.
- `identity` là phần tử đơn vị của phép toán, tức một giá trị e thỏa mãn `f(e, x) = x` (và nên thỏa `f(x, e) = x` cho quy tắc kết hợp đối xứng).
- `accumulate(x)` đưa một quan sát x vào bộ tích lũy.
- `get()` (hoặc `longValue()`) trả kết quả tổng hợp các phần lưu nội bộ.
- `reset()` trở về identity; `getThenReset()` đọc rồi reset, nhưng không phải một phép chốt kỳ nguyên tử đối với concurrent updates.

Vì không có thứ tự tích lũy được bảo đảm giữa các thread, phép toán **phải phù hợp với việc gộp lại theo những thứ tự và nhóm khác nhau**. Theo tài liệu JDK, để có kết quả có thể dự đoán, operator nên **associative, commutative và side-effect-free**.

| Phép tích lũy | Operator | Identity phù hợp | Diễn giải |
| --- | --- | --- | --- |
| Tổng | `Long::sum` | `0L` | Tổng các lượng cập nhật |
| Cực đại | `Long::max` | `Long.MIN_VALUE` | Giá trị lớn nhất quan sát được |
| Cực tiểu | `Long::min` | `Long.MAX_VALUE` | Giá trị nhỏ nhất quan sát được |
| Phép OR từng bit | `(a, b) -> a \| b` | `0L` | Hợp nhất bit flags |
| Phép XOR từng bit | `(a, b) -> a ^ b` | `0L` | XOR các giá trị |
| Trừ dần | `(a, b) -> a - b` | Không thích hợp cho kiểu tổng hợp này | Không associative/commutative |
| Trung bình cập nhật `(a+b)/2` | Không thích hợp | Không đáp ứng tính kết hợp | Phụ thuộc thứ tự và làm tròn |

**Cảnh báo với identity cực đại:** Nếu dùng `Long::max` nhưng cho identity `0L`, một tập quan sát chỉ chứa số âm (ví dụ -12, -4, -7) sẽ cho kết quả **0**, sai. Phải chọn `Long.MIN_VALUE` để mọi `long` đều hợp lệ. Ngược lại, `Long::min` nên chọn `Long.MAX_VALUE`.

### 3.1. Ví dụ Java 17+: tìm cực đại của các giá trị âm

~~~java
// LongAccumulatorMaxDemo.java
import java.util.concurrent.atomic.LongAccumulator;

public class LongAccumulatorMaxDemo {
    public static void main(String[] args) throws InterruptedException {
        LongAccumulator maximum =
            new LongAccumulator(Long::max, Long.MIN_VALUE);

        Thread a = new Thread(() -> {
            maximum.accumulate(-9);
            maximum.accumulate(-4);
        });
        Thread b = new Thread(() -> {
            maximum.accumulate(-12);
            maximum.accumulate(-7);
        });
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("Maximum: " + maximum.get());
        System.out.println("Saved: " + maximum.getThenReset());
        System.out.println("After reset: " + maximum.get());
    }
}
~~~

~~~bash
javac LongAccumulatorMaxDemo.java
java LongAccumulatorMaxDemo
~~~

Stdout:

~~~text
Maximum: -4
Saved: -4
After reset: -9223372036854775808
~~~

Vì `Long::max` **associative/commutative**, kết quả không phụ thuộc thread nào cập nhật trước. Sau `join()` không còn writer, nên `getThenReset()` trả đúng -4 và `get()` sau reset trở về identity `Long.MIN_VALUE`, không phải 0.

**Cần phân biệt giá trị rỗng:** Với max dùng `Long.MIN_VALUE`, nếu chưa có mẫu nào, `get()` cũng trả `Long.MIN_VALUE`. Nếu hệ thống cần phân biệt “chưa quan sát” với “đã quan sát chính Long.MIN_VALUE”, phải có thiết kế bổ sung (ví dụ một cấu trúc trạng thái có cờ hiện diện), và phải tính tới tính nhất quán khi đọc nhiều biến.

### 3.2. Ví dụ phép toán khác: GCD (ước chung lớn nhất)

GCD của số nguyên dương có tính kết hợp và giao hoán, identity phù hợp là `0` vì `gcd(0,x)=x` với x dương.

~~~java
// LongAccumulatorGcdDemo.java
import java.util.concurrent.atomic.LongAccumulator;

public class LongAccumulatorGcdDemo {
    private static long gcd(long a, long b) {
        while (b != 0) {
            long remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }

    public static void main(String[] args) {
        // Ví dụ chỉ truyền số nguyên dương.
        LongAccumulator gcdAccumulator =
            new LongAccumulator(LongAccumulatorGcdDemo::gcd, 0L);

        gcdAccumulator.accumulate(18);
        gcdAccumulator.accumulate(24);
        gcdAccumulator.accumulate(30);
        System.out.println("GCD: " + gcdAccumulator.get());
    }
}
~~~

~~~bash
javac LongAccumulatorGcdDemo.java
java LongAccumulatorGcdDemo
~~~

Stdout:

~~~text
GCD: 6
~~~

Hàm `gcd` ở đây **thuần**, không thay đổi state bên ngoài và chỉ nhận số dương trong ví dụ. Nếu muốn hỗ trợ mọi giá trị `long`, cần thiết kế kỹ cho số âm và `Long.MIN_VALUE` vì `Math.abs(Long.MIN_VALUE)` tràn trong kiểu long; ví dụ chỉ minh họa tính kết hợp/giao hoán trên miền số dương.

### 3.3. Hàm accumulator có thể được thực hiện lại

Không dùng updater để gây side effect không thể lặp lại:

~~~java
// MÃ NÊN TRÁNH: minh họa logic, không phải chương trình độc lập.
LongAccumulator wrong = new LongAccumulator((current, x) -> {
    sendEmail(x); // Side effect có thể lặp lại trong quá trình retry.
    return current + x;
}, 0L);
~~~

Vì implementation có thể thử lại việc cập nhật khi tranh chấp, hàm có thể được áp dụng **nhiều lần cho một lời gọi `accumulate`**. Mã đúng chỉ thực hiện phép tính thuần từ `current` và `x` rồi trả kết quả; gửi email, ghi DB, ghi log nghiệp vụ một lần... phải tách khỏi hàm gộp.

Chọn operator không associative/commutative như phép trừ cũng là lỗi: kết quả phụ thuộc thứ tự và cách chia ô, trong khi API không bảo đảm một thứ tự duy nhất.

## 4. Vì sao giảm contention được?

### 4.1. AtomicLong: một điểm cập nhật

`AtomicLong` giữ một trạng thái `long` đơn mà các phép atomic update cùng cạnh tranh. Điều này rất thích hợp nếu cần:

- `incrementAndGet()` trả về ngay giá trị mới.
- `getAndIncrement()` cấp **số thứ tự duy nhất** tại điểm tăng.
- `compareAndSet(expected, update)` cho một chuyển trạng thái có điều kiện.
- `get()` đọc **một giá trị nguyên tử hiện tại** của field theo ngữ nghĩa API.

### 4.2. LongAdder/Accumulator: phân tán cập nhật khi cần

Khi tranh chấp, implementation có thể tăng số biến nội bộ chứa các phần tổng. Thread cập nhật phần nội bộ tương ứng, và lúc đọc kết quả thì code phải gộp các phần đó lại. Điểm được tối ưu là **throughput cập nhật** dưới contention, nhưng có những đánh đổi:

- Tốn **nhiều bộ nhớ hơn**, nhất là với nhiều instance LongAdder/LongAccumulator và nhiều writer.
- `sum()`/`get()` phải tổng hợp nhiều thành phần, nên chi phí **đọc** có thể tăng.
- `sum()`/`get()` **không phải atomic snapshot** khi writer vẫn đang cập nhật.
- Không có một kết quả **trả về từ từng lần update** giống `AtomicLong.incrementAndGet()`; `LongAdder.increment()` trả `void`.
- Các thao tác reset phải được dùng ở giai đoạn **không có concurrent updates**, nếu cần kết quả ranh giới kỳ chính xác.

Phân tán cập nhật không có nghĩa là dùng khóa lớn bảo vệ mỗi update. Cả hai class tận dụng cơ chế nguyên tử và điều phối bên trong JDK, nhưng không cung cấp khóa cho caller để bọc một giao dịch nhiều bước.

## 5. sum() / get(): số liệu đọc được không phải snapshot nguyên tử

Tài liệu JDK 17 nêu rõ:

- `LongAdder.sum()` trả tổng hiện được tổng hợp, **không phải một snapshot nguyên tử**. Những cập nhật xảy ra trong lúc tổng hợp có thể chưa được đưa vào kết quả.
- `LongAccumulator.get()` cũng **không phải snapshot nguyên tử** đối với concurrent updates.
- Khi **không có concurrent updates**, phép lấy tổng/kết quả cho giá trị chính xác của những cập nhật đã hoàn tất và được công bố theo giao thức đồng bộ thích hợp.

Hãy hình dung phép đọc đang cộng nhiều cell:

~~~text
Thời gian →
Reader:   đọc cell A = 10 ─────── đọc cell B = 20 ─── trả 30
Writer:             cập nhật A thành 11 ─── cập nhật B thành 21

Có thể không tồn tại một thời điểm duy nhất mà tổng của cả A và B đúng
bằng giá trị Reader vừa tính từ các phiên bản cell khác nhau.
~~~

Đây là **sơ đồ khái niệm** về sự thiếu atomic snapshot, không phải mô tả chính xác cơ chế từng cell của JDK. Cần tránh kết luận “mỗi lần `sum()` trả về là tổng **tại thời điểm gọi**” nếu vẫn có writers đang chạy.

### 5.1. Đọc thống kê và quyết định nghiệp vụ là hai chuyện

Trường hợp hợp lý:
- Hiển thị một metric xấp xỉ khi request vẫn đang được xử lý.
- Đếm số tác vụ/hit được tích lũy trong worker pool, đọc theo chu kỳ để giám sát.
- Gộp max độ trễ đã quan sát, không yêu cầu một snapshot tuyến tính hóa cho thuật toán điều khiển.

Trường hợp **không nên** chỉ dựa vào `sum()`:
- `if (adder.sum() < limit) adder.increment()` để đặt **giới hạn cứng**.
- `sum()` để cấp số vé/ID duy nhất.
- Quyết định đặt chỗ khi `available == 1` rồi decrement, cần bảo đảm không oversell.
- Chốt một số liệu thanh toán tuyệt đối tại đúng thời điểm trong khi các thread vẫn cập nhật.

Ngay cả `AtomicLong` cũng không tự biến **chuỗi `get()` rồi `incrementAndGet()`** thành một giao dịch điều kiện. Khi có ràng buộc “nếu chưa vượt giới hạn mới tăng”, hãy dùng **CAS loop**, một đoạn khóa hoặc primitive chuyên dụng để kiểm tra và cập nhật tại cùng điểm nguyên tử.

### 5.2. Muốn tổng chính xác sau một đợt: đợi writers kết thúc

Ví dụ LongAdderContentionDemo đã dùng `CountDownLatch` để chờ đầy đủ tasks trước khi gọi `sum()`. Một chương trình dùng executor có thể:
1. `submit` tất cả tác vụ;
2. đợi tất cả `Future.get()` hoàn tất thành công (xử lý exception/hủy đúng cách);
3. chắc chắn không còn producer mới vào cùng counter;
4. đọc `sum()` sau điểm đồng bộ ấy.

**Không đủ** nếu chỉ thấy `ExecutorService.shutdown()` đã được gọi: shutdown dừng nhận task mới nhưng **không đợi** những task đã được nhận hoàn tất. Nếu việc chờ bị interrupt, timeout hoặc một task tiếp tục chạy sau khi caller bỏ chờ, không thể tuyên bố không còn concurrent updates.

## 6. reset(), sumThenReset(), getThenReset() và ranh giới kỳ

`LongAdder.reset()` đặt các phần lưu tổng về 0. `LongAdder.sumThenReset()` đọc tổng rồi reset; `LongAccumulator.reset()` đặt các phần về **identity**; `LongAccumulator.getThenReset()` đọc kết quả rồi reset về identity.

Các thao tác này **không được bảo đảm atomic với những writer đang cập nhật**. JDK mô tả reset là thao tác có tính cạnh tranh nội tại; chỉ dùng an toàn cho mục tiêu số liệu chính xác khi **biết không có concurrent updates**.

| API | Sau reset | Điều kiện để chốt số liệu chính xác |
| --- | --- | --- |
| `LongAdder.reset()` | 0 | Không có writer concurrent |
| `LongAdder.sumThenReset()` | 0 | Không có writer concurrent |
| `LongAccumulator.reset()` | Identity được cấp lúc tạo | Không có writer concurrent |
| `LongAccumulator.getThenReset()` | Identity được cấp lúc tạo | Không có writer concurrent |

### 6.1. Vì sao reset đồng thời có thể mất cập nhật?

Phép tổng hợp rồi xóa các ô nội bộ **không phải một giao dịch** với tất cả cập nhật đang xảy ra. Một update có thể nằm trong khoảng đọc/xóa của một ô và cuối cùng không được phân loại vào kỳ vừa chốt hoặc kỳ mới theo cách mong muốn. Do đó kết quả `sumThenReset()` trong lúc producer đang ghi **không có bảo đảm** bằng “tất cả sự kiện trước thời điểm reset” và kỳ sau cũng không chắc phục hồi một ranh giới logic chính xác.

Đừng đọc tên `sumThenReset` thành “atomic getAndSet(0)”. Với `AtomicLong`, `getAndSet(0)` là một thao tác nguyên tử trên **một biến duy nhất** và có semantics khác.

### 6.2. Chiến lược khi cần báo cáo theo kỳ

**Cách A — Có điểm nghỉ (quiescence):** dừng hoặc chặn các producer mới, đợi toàn bộ producer hiện tại hoàn tất, sau đó gọi `sumThenReset`/`getThenReset`, rồi cho producer tiếp tục. Đây là cách trực tiếp để xác định ranh giới.

**Cách B — Thống kê xấp xỉ:** liên tục gọi `sum()`/`get()` mà không reset, chấp nhận giá trị tức thời và tính delta giữa những lần quan sát cho dashboard. Delta này **không phải** một số đo transactionally exact nếu writers còn hoạt động; có thể dùng cho metrics giám sát phù hợp.

**Cách C — Đổi thế hệ counter có phối hợp:** có thể quản lý nhiều counter/snapshot theo epoch, nhưng việc thay reference `AtomicReference<LongAdder>` bằng `getAndSet` **chưa đủ** để tạo ranh giới tuyệt đối: một writer đã giữ reference counter cũ có thể cập nhật nó **sau** lúc reference mới được công bố. Muốn không bỏ sót/đếm sai kỳ, cần giao thức phối hợp với những writer đang chạy (drain/in-flight tracking, khóa, barrier hoặc cơ chế khác), không chỉ hoán đổi reference.

Khi nghiệp vụ cần chính xác từng giao dịch hoặc số dư, dùng primitive giao dịch thích hợp thay vì dựa vào thống kê kiểu LongAdder.

## 7. LongAdder so với AtomicLong: chọn theo mục tiêu

| Tiêu chí | `AtomicLong` | `LongAdder` |
| --- | --- | --- |
| Trạng thái | Một giá trị `long` nguyên tử | Tổng phân tán qua các giá trị nội bộ khi contention |
| Thao tác tăng | `incrementAndGet()` / `getAndIncrement()` trả giá trị | `increment()` trả `void` |
| Đọc | `get()` là phép đọc nguyên tử một giá trị | `sum()` không phải atomic snapshot |
| CAS điều kiện | Có `compareAndSet` | Không cung cấp CAS trên tổng |
| Dưới contention cao | Có thể tốn chi phí tại một điểm cập nhật | Thường có throughput cập nhật cao hơn |
| Dưới contention thấp | Đơn giản, overhead nhỏ | Lợi ích có thể ít, chi phí quản lý tổng vẫn tồn tại |
| Bộ nhớ | Thường nhỏ hơn | Có thể lớn hơn khi mở rộng nội bộ |
| Mục đích | Điều khiển/chuyển trạng thái, ID, chính xác tức thời trên một biến | Metrics, counters, throughput updates |
| Reset | `getAndSet(0L)` là atomic RMW | `sumThenReset()` không atomic với concurrent updates |

**Ví dụ quyết định đúng:**
- Số lượt truy cập trang được các worker tăng liên tục, đọc để hiển thị dashboard → `LongAdder` phù hợp để thử.
- Cấp số thứ tự tăng dần không trùng cho mỗi request → dùng `AtomicLong.getAndIncrement()`.
- Giới hạn cứng “chỉ tối đa 100 tác vụ cùng hoạt động” → dùng giao thức đồng bộ cho **check và cập nhật** (ví dụ CAS/semaphore), không phải `if (adder.sum() < 100)`.
- Ghi số liệu cho đợt xử lý batch có `join()` đầy đủ trước khi chốt → `LongAdder.sum()` chính xác ở điểm sau batch.

### Tại sao không dùng volatile long++?

`volatile long count` có visibility/ordering cho lần đọc/ghi field, nhưng `count++` vẫn là **read–modify–write không nguyên tử**. Hai thread có thể cùng đọc một giá trị rồi cùng ghi kết quả mới, gây lost update. `AtomicLong.incrementAndGet()` và `LongAdder.increment()` giải quyết việc cộng dồn đồng thời theo hợp đồng của từng API, nhưng mang semantics đọc khác nhau.

## 8. LongAccumulator so với LongAdder và AtomicLong

| Tiêu chí | `LongAdder` | `LongAccumulator` | `AtomicLong` |
| --- | --- | --- | --- |
| Phép kết hợp | Cộng dồn `long` | `LongBinaryOperator` tùy chọn | Atomic update trên một `long` |
| Identity | Luôn 0 | Do caller cấp | Giá trị khởi tạo do caller cấp |
| Cập nhật | `add`, `increment`, `decrement` | `accumulate(x)` | `addAndGet`, `updateAndGet`, `accumulateAndGet`... |
| Đọc | `sum()` | `get()` | `get()` |
| Atomic snapshot khi đang ghi | Không | Không | Có cho một `long` |
| Mức tranh chấp | Có thể phân tán các cập nhật | Có thể phân tán các cập nhật | Cùng tranh một giá trị |
| Phù hợp | Lượt truy cập, bytes, đếm sự kiện | Max/min hoặc phép reduction tùy chọn | Số thứ tự, CAS, cập nhật điều kiện |

`LongAdder` là trường hợp phổ biến của phép cộng, có thể hiểu gần với `new LongAccumulator(Long::sum, 0L)` về ý nghĩa tổng hợp, dù API riêng của `LongAdder` tiện hơn và implementation/performance cần được đo theo workload.

**Không dùng `LongAccumulator` với operator tùy tiện.** Operator cần có identity và không phụ thuộc thứ tự gộp. Chẳng hạn phép “giữ average hiện tại” `(old + sample) / 2` không associative và không cho average tổng thể đúng. Nếu muốn average, nên theo dõi **sum + count**, rồi chia khi tổng hợp; nếu cần một snapshot chính xác đồng thời của cả hai, phải phối hợp việc đọc cả hai theo giao thức phù hợp.

**Long overflow:** cả `LongAdder` lẫn `LongAccumulator` trả kiểu `long`; các phép cộng trên long có thể **tràn** theo quy tắc số nguyên Java. Nếu nghiệp vụ cần tổng vượt phạm vi long với độ chính xác tuyệt đối, chọn biểu diễn khác và cơ chế đồng bộ phù hợp; không giả định phân tán counters giúp tránh overflow.

## 9. Mẫu đếm theo key với ConcurrentHashMap

Tình huống phổ biến: số lượt xuất hiện của từng mã sự kiện. Ta có thể dùng `ConcurrentHashMap<String, LongAdder>` để mỗi key có một bộ đếm đồng thời riêng. `computeIfAbsent` đảm nhiệm việc tạo/bắt lại bộ đếm cho key một cách an toàn theo hợp đồng của map; `increment()` cộng giá trị trên instance được lấy.

~~~java
// LongAdderFrequencyDemo.java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class LongAdderFrequencyDemo {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, LongAdder> frequency =
            new ConcurrentHashMap<>();

        Runnable record = () -> {
            frequency
                .computeIfAbsent("ok", key -> new LongAdder())
                .increment();
            frequency
                .computeIfAbsent("error", key -> new LongAdder())
                .increment();
            frequency
                .computeIfAbsent("ok", key -> new LongAdder())
                .increment();
        };

        Thread a = new Thread(record);
        Thread b = new Thread(record);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("ok: " + frequency.get("ok").sum());
        System.out.println("error: " + frequency.get("error").sum());
    }
}
~~~

~~~bash
javac LongAdderFrequencyDemo.java
java LongAdderFrequencyDemo
~~~

Stdout:

~~~text
ok: 4
error: 2
~~~

**Vì sao đúng?** Hai thread đều cộng vào adder gắn với key tương ứng; việc tạo value qua `computeIfAbsent` là thao tác của concurrent map, không phải `if (map.get(key) == null) map.put(key,new LongAdder())` tự viết. Main `join()` hai worker rồi mới đọc; kết quả không phụ thuộc vào thứ tự lập lịch.

**Giới hạn cần nhớ:**
- Nếu có code **remove/replace** value của key khi writer khác đã giữ reference tới adder cũ, có thể có cập nhật vào object không còn được map tham chiếu. Cần thiết kế lifecycle của key/counter, không kết luận mỗi `increment` chắc chắn được tính vào value hiện đang nằm trong map.
- Việc `sum()` trên nhiều key trong khi writer hoạt động **không cho một snapshot nguyên tử của toàn map**.
- Mỗi key có một adder: với nhiều key ít được cập nhật, chi phí object/metadata có thể lớn. Cần cân nhắc retention/eviction và benchmark.

## 10. Sai lầm phổ biến khi dùng LongAdder/LongAccumulator

1. **Xem `sum()` là một lần đọc atomic của tổng.** Không đúng khi writer vẫn đang cập nhật. Kết quả là tổng hợp nhất thời, không phải linearizable snapshot.
2. **Dùng `sum()` để điều khiển hard limit.** Mẫu `if (counter.sum() < limit) counter.increment()` bị check–then–act race và còn đọc non-atomic snapshot.
3. **Dùng `sumThenReset()` để chốt số chính xác trong lúc producers vẫn ghi.** Reset cạnh tranh có thể bỏ sót/đếm sai kỳ. Phải có quiescence hoặc giao thức epoch đồng bộ đầy đủ.
4. **Chọn sai identity của LongAccumulator.** Với `Long::max` dùng 0 sẽ sai khi mọi mẫu âm; dùng `Long.MIN_VALUE`.
5. **Dùng operator không associative/commutative.** Kết quả có thể phụ thuộc thứ tự và cách gộp, vốn không được bảo đảm.
6. **Đặt side effect trong operator.** Operator có thể chạy lại khi retry, gây gửi thông báo/thực hiện thanh toán trùng.
7. **Nhầm `increment()` với `incrementAndGet()`.** `LongAdder.increment()` trả `void`, không cấp sequence ID.
8. **Giả định LongAdder luôn nhanh hơn.** Mức contention, read frequency, GC, số instance và kiến trúc CPU ảnh hưởng kết quả.
9. **Nghĩ LongAdder là một counter có giá trị không bao giờ giảm.** `add(-x)` và `decrement()` hợp lệ, và phép tổng của nhiều cell được đọc cạnh tranh không bảo đảm một tiến trình đơn giản như một AtomicLong.
10. **Quên xử lý overflow.** Kết quả `long` có giới hạn; cần xem miền giá trị và thời gian tồn tại của counter.
11. **Tưởng `reset()` giúp thread-safe giữa các kỳ.** `reset()` không phải atomic epoch transition; cần ngăn writer cập nhật trong thời gian reset nếu đòi exactness.
12. **Dùng map frequency mà xóa key không kiểm soát.** Writer có thể đã giữ reference tới value cũ; thay map entry không tự chặn update đang bay vào value đó.

## 11. Cách benchmark trước khi chọn

Để so `AtomicLong` với `LongAdder` hoặc `LongAccumulator`, cần dùng workload đại diện, không chỉ đo một vòng lặp ngắn trong `main`.

- **Số writer:** một thread, vài thread, số thread lớn hơn số core? Cần thử nhiều mức.
- **Tần suất đọc:** dashboard mỗi giây hay đọc liên tục sau từng update? `sum()` có chi phí gộp khi có nhiều cell.
- **Hình thức tác vụ:** có tính toán khác giữa hai lần cập nhật hay tất cả thread cùng tranh một điểm?
- **Số instance:** một bộ đếm nóng khác nhiều nghìn bộ đếm lạnh trong map.
- **Bộ nhớ và GC:** adder có thể tiêu thụ nhiều bộ nhớ hơn; đo heap/allocations nếu quan trọng.
- **Warmup/JIT/CPU:** dùng công cụ microbenchmark đáng tin như **JMH** cho đo lường nghiêm túc; tránh kết luận từ `System.nanoTime()` quanh một loop tối ưu khó kiểm soát.
- **Correctness trước:** bảo đảm kết quả sau khi mọi writer hoàn tất là đúng; đừng thay atomicity bắt buộc bằng số liệu throughput.

Thông thường hãy đo **operations/second**, **latency** và **memory** theo các mức contention. Một kết quả benchmark trong một máy không phải bảo đảm trên mọi workload.

## 12. Quy trình thiết kế an toàn

1. **Xác định tính chất của số liệu:** metric thống kê hay trạng thái điều khiển? Có cần giá trị **trả về ngay từ mỗi update** hay không?
2. **Viết ràng buộc tính đúng:** chỉ cần tổng chính xác khi kết thúc batch, hay cần quyết định ở mọi thời điểm?
3. **Chọn primitive:** `AtomicLong` cho ID/conditional CAS; `LongAdder` cho tổng/counter nóng; `LongAccumulator` cho reduction như max/min với operator đúng.
4. **Kiểm tra operator:** có identity thật, associative, commutative, side-effect-free không? Có overflow/giá trị âm/miền đầu vào đặc biệt không?
5. **Xác định điểm đọc:** nếu `sum()/get()` khi writers đang chạy, chấp nhận đây không phải atomic snapshot; nếu cần exact, phối hợp dừng và đợi writers.
6. **Thiết kế kỳ reset:** tuyệt đối không mặc định `sumThenReset()/getThenReset()` là atomic với producer. Xây dựng quiescence/epoch protocol nếu đòi chốt kỳ chính xác.
7. **Đánh giá lifecycle của counter:** trong map, ai tạo/xóa/replace? Có writer cầm reference cũ không?
8. **Stress test và benchmark:** thử nhiều mức contention, assertion số liệu cuối, theo dõi throughput và memory trước khi tối ưu.

## 13. Tự kiểm tra

**1.** Bốn thread mỗi thread tăng `LongAdder` 10.000 lần, main đợi đủ bốn thread rồi gọi `sum()`. Giá trị mong đợi là bao nhiêu?  
**Đáp án:** 40.000 nếu mọi worker hoàn tất các cập nhật; sau điểm đợi không còn concurrent updates nên tổng chính xác.

**2.** Có thể dùng `LongAdder.increment()` để trả về một ID duy nhất cho mỗi request không?  
**Đáp án:** Không. Phương thức trả `void` và `sum()` không phải atomic snapshot. Nếu cần ID, dùng `AtomicLong.getAndIncrement()` hoặc primitive tương đương.

**3.** Với `LongAccumulator(Long::max, 0L)`, thêm -5 và -2. Kết quả đúng phải là -2 nhưng `get()` có thể trả gì?  
**Đáp án:** 0, vì chọn identity sai. Sửa thành `Long.MIN_VALUE`.

**4.** Tại sao `LongAccumulator((a,b)->a-b, 0)` không thích hợp?  
**Đáp án:** Phép trừ không associative/commutative; kết quả phụ thuộc thứ tự và cách gộp cập nhật.

**5.** `sumThenReset()` có phải một thao tác atomic tương đương `AtomicLong.getAndSet(0)` không?  
**Đáp án:** Không. Với writer đồng thời, các phép tổng hợp và reset ô nội bộ không tạo một ranh giới kỳ atomic.

**6.** Nếu muốn đếm tần suất theo key trong `ConcurrentHashMap`, mẫu nào hợp lý?  
**Đáp án:** `map.computeIfAbsent(key, k -> new LongAdder()).increment()`. Cần phối hợp thêm nếu key có thể bị remove/replace cùng lúc.

**7.** Khi nào `LongAdder` có thể kém lợi thế hơn `AtomicLong`?  
**Đáp án:** Khi contention thấp, đọc tổng liên tục, có nhiều bộ đếm ít cập nhật, hay việc dùng thêm bộ nhớ/đọc gộp không phù hợp.

**8.** Với `LongAccumulator(Long::max, Long.MIN_VALUE)` chưa có mẫu nào, `get()` trả gì?  
**Đáp án:** Trả identity là `Long.MIN_VALUE`, không thể tự biết “chưa có mẫu” nếu ứng dụng không thêm mô hình biểu thị sự hiện diện.

**9.** Việc swap `AtomicReference<LongAdder>` sang adder mới có đủ để chốt cứng kỳ trước không?  
**Đáp án:** Chưa đủ. Worker đã đọc reference cũ có thể cập nhật vào nó sau swap; cần phối hợp writer đang chạy để có ranh giới exact.

**10.** LongAccumulator có nên dùng operator có tác dụng phụ ghi database không?  
**Đáp án:** Không. Hàm có thể bị áp dụng lại khi retry. Hãy giữ operator thuần và tách I/O khỏi phép reduction.

**Ghi nhớ:** `LongAdder` là lựa chọn mạnh cho **counter/sum được nhiều thread cập nhật** khi một snapshot đọc trong lúc ghi không cần tuyến tính hóa. `LongAccumulator` mở rộng ý tưởng sang phép reduction có identity đúng và operator kết hợp/giao hoán, thuần. `AtomicLong` vẫn quan trọng khi cần **một điểm cập nhật/đọc nguyên tử hoặc CAS điều kiện**. Chọn theo tính đúng của giao thức, rồi mới tối ưu throughput.

### Tài liệu tham khảo chính thức

- Java SE 17, `LongAdder`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html
- Java SE 17, `LongAccumulator`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAccumulator.html
- Java SE 17, `AtomicLong`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html
- Java SE 17, `ConcurrentHashMap`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-010">VarHandle: truy cập atomic và access mode</a>

<details>
<summary>Xem chi tiết</summary>

# VarHandle: truy cập atomic và access mode trong Java

## 1. VarHandle là gì và giải quyết bài toán nào?

**`java.lang.invoke.VarHandle`** là API JDK (xuất hiện từ Java 9, dùng được trong Java 17+) mô tả cách truy cập **một biến** qua một handle có kiểu: trường instance, trường static hoặc phần tử mảng. Khác với một getter/setter thông thường, `VarHandle` cho phép chọn **access mode** tại mỗi lần truy cập: đọc/ghi plain, opaque, acquire/release, volatile và—khi kiểu biến hỗ trợ—các thao tác atomic như compare-and-set, compare-and-exchange, get-and-set, get-and-add.

Ví dụ, cùng một trường `int` có thể được đọc bằng `getVolatile` hoặc dùng `getAndAdd` để tăng nguyên tử. API đem những primitive cấp thấp của Java Memory Model (JMM) đến mã Java thuần, không cần dùng `sun.misc.Unsafe`.

Điều cần nhớ ngay từ đầu:

- **VarHandle không phải chính biến:** nó là một đối tượng mô tả cách truy cập đến vị trí biến. Dữ liệu thực vẫn nằm trong một instance, một trường static hoặc mảng.
- **Access mode quyết định memory-ordering semantics** của *lần truy cập*, không nên xem mọi `get`/`set` tự có tính chất như `volatile`.
- **Handle không tự khóa nhiều trường cùng lúc.** Atomicity của `getAndAdd` hoặc CAS áp dụng cho **một vị trí biến**; bất biến trải qua nhiều biến cần thiết kế riêng.
- **Quyền truy cập vẫn được kiểm tra** khi tạo handle qua `MethodHandles.Lookup`; VarHandle không phải cách hợp lệ để bỏ qua đóng gói hoặc quyền module.
- **Không mọi mode hợp lệ với mọi kiểu trường.** Có thể kiểm tra bằng `isAccessModeSupported(...)`; mode không hỗ trợ gây `UnsupportedOperationException` khi gọi.

Trong bài này ta dùng Java 17+, API JDK tiêu chuẩn và các ví dụ Java concurrency nói chung.

## 2. VarHandle xác định “biến nào” bằng kiểu và tọa độ

Có ba dạng quen thuộc:

| Loại vị trí | Cách tạo handle | Tọa độ khi gọi | Ví dụ |
| --- | --- | --- | --- |
| Trường **instance** | `lookup.findVarHandle(MyClass.class, "value", int.class)` | Đối tượng receiver | `VALUE.getVolatile(myObject)` |
| Trường **static** | `lookup.findStaticVarHandle(MyClass.class, "total", int.class)` | Không có receiver | `TOTAL.getVolatile()` |
| Phần tử **mảng** | `MethodHandles.arrayElementVarHandle(int[].class)` | Mảng và chỉ số | `ELEMENT.getVolatile(array, index)` |

`VarHandle.varType()` cho biết **kiểu giá trị** của biến; `coordinateTypes()` cho biết các **kiểu tọa độ** để xác định vị trí. Ví dụ handle trường instance `int value` có value type `int` và một coordinate là loại đối tượng chủ; handle phần tử `int[]` có hai coordinate: mảng `int[]` và chỉ số `int`.

### Tạo handle cho trường private

Tạo một handle trong **chính class khai báo trường**, thường dùng:

```java
private static final VarHandle VALUE;

static {
    try {
        VALUE = MethodHandles.lookup()
                .findVarHandle(MyClass.class, "value", int.class);
    } catch (ReflectiveOperationException e) {
        throw new ExceptionInInitializerError(e);
    }
}
```

Đây là **snippet** về pattern khởi tạo; `MyClass` và import chưa được khai báo ở đây. Ví dụ hoàn chỉnh ở phần sau.

`findVarHandle` xác nhận tên trường và kiểu trường tương ứng; nếu không tìm thấy hoặc không được phép truy cập thì có thể ném `NoSuchFieldException` hoặc `IllegalAccessException` (đều nằm trong nhóm `ReflectiveOperationException`). `MethodHandles.lookup()` mang quyền truy cập của class gọi nó; với class khác hoặc module khác, không được giả định có quyền xem trường private. `MethodHandles.privateLookupIn(...)` chỉ dùng khi đáp ứng quyền truy cập/module openness tương ứng, không phải lối tắt vượt bảo mật.

**Lưu ý với trường `final`:** một handle có thể đọc trường final khi được phép, nhưng các access mode ghi thường **không được hỗ trợ** cho final field. Không tìm cách sửa final field thông qua một mode không hợp lệ.

## 3. Các access mode và bảo đảm memory-ordering

VarHandle chia access mode theo ý nghĩa truy cập dữ liệu và ordering. Bảng sau là sơ đồ học tập; chi tiết có trong Javadoc của `VarHandle` và Java Memory Model.

| Mode/API điển hình | Cách hiểu thực hành | Lưu ý |
| --- | --- | --- |
| `get`, `set` | **Plain**: tương ứng truy cập thông thường, không thêm bảo đảm đồng bộ liên thread kiểu volatile | Không dùng plain để công bố state mutable không đồng bộ |
| `getOpaque`, `setOpaque` | **Opaque**: bảo đảm về tính nhất quán/coherence ở mức biến được truy cập, nhưng ordering yếu hơn acquire/release | Không tự công bố dữ liệu phụ thuộc ở trường khác |
| `getAcquire` | **Acquire read**: các truy cập tiếp theo trong thread đọc không bị đưa lên trước acquire theo các ràng buộc của mode | Hữu ích khi đọc cờ đã được writer công bố bằng release |
| `setRelease` | **Release write**: các truy cập trước đó của thread ghi không bị đẩy xuống sau release theo ràng buộc của mode | Thường dùng cùng `getAcquire` trên **cùng biến tín hiệu** |
| `getVolatile`, `setVolatile` | **Volatile**: đọc/ghi có các bảo đảm ordering/visibility của `volatile` | Mạnh hơn về ordering, nhưng hai lời gọi riêng không biến thành một transaction |
| `compareAndSet`, `compareAndExchange` | **Atomic conditional update** | Dùng khi phải kiểm tra giá trị hiện tại rồi ghi một cách nguyên tử |
| `getAndSet`, `getAndAdd` | **Atomic read–modify–write** (trên kiểu hỗ trợ) | Trả giá trị trước cập nhật; tránh lost update như `x++` thường |
| Các biến thể `...Acquire`, `...Release`, `...Plain` | Phiên bản với bảo đảm ordering được chọn cho thao tác atomic tương ứng | Chỉ dùng khi hiểu rõ hợp đồng bộ nhớ của mode đó |

Các cấp độ không có nghĩa **plain = luôn nhìn thấy ngay** hoặc **volatile = thao tác nghiệp vụ nhiều bước tự atomic**. JMM xác định các quan hệ giữa những hành động; việc mỗi lần chạy tình cờ quan sát được dữ liệu mới không phải bằng chứng an toàn.

### Plain access

`(int) VALUE.get(receiver)` và `VALUE.set(receiver, 42)` thể hiện phép đọc/ghi thông thường. Nếu một thread ghi và thread khác đọc cùng biến bằng plain mà không có quan hệ happens-before khác, chương trình có thể có **data race**. Không thay `synchronized`, latch hoặc volatile bằng hai lời gọi plain chỉ vì chúng đi qua VarHandle.

### Opaque access

`getOpaque`/`setOpaque` áp dụng các ràng buộc yếu, hướng tới những giao thức cấp thấp cần tính nhất quán của cùng một vị trí nhưng không cần ordering đầy đủ với các vị trí khác. **Không được** suy ra: reader thấy một cờ cập nhật qua opaque thì các trường dữ liệu thông thường viết trước cờ chắc chắn đã được publish theo quan hệ release/acquire.

Nếu người học chưa có nhu cầu giao thức non-blocking cấp thấp, acquire/release hoặc volatile thường rõ nghĩa và dễ kiểm chứng hơn.

### Acquire và release

Pattern công bố một lần, **writer**:

```java
payload = 42;           // Ghi dữ liệu thường trước.
READY.setRelease(this, 1); // Công bố cờ.
```

**Reader**:

```java
while ((int) READY.getAcquire(this) == 0) {
    Thread.onSpinWait();
}
int local = payload; // Đọc dữ liệu sau khi thấy cờ công bố.
```

Hai snippet này giả sử `payload`, `READY` và receiver đã được khai báo; **không phải hai file độc lập**. Khi acquire đọc đúng giá trị đã được writer công bố qua release trên cùng biến tín hiệu, tác dụng ordering của release/acquire cho phép sử dụng dữ liệu đã ghi trước việc công bố theo giao thức đó. Ví dụ độc lập ở phần 7.

Vòng `onSpinWait()` chỉ là minh họa cơ chế; production thường ưu tiên `CountDownLatch`, `BlockingQueue`, `Future` hoặc API chờ khác khi không cần polling. Busy-wait có thể lãng phí CPU, cần timeout/cancellation nếu công việc có thể không hoàn tất.

### Volatile access

`getVolatile`/`setVolatile` có semantics tương tự truy cập vào trường volatile. Một lần ghi volatile happens-before lần đọc volatile tương ứng theo các quy tắc synchronization order, hỗ trợ visibility/ordering giữa các thread.

**Sai lầm quen thuộc:** hai thread cùng thực hiện `(int) VALUE.getVolatile(obj)` rồi `VALUE.setVolatile(obj, old + 1)`. Từng lần get/set có semantics volatile, nhưng **cặp lời gọi không nguyên tử**: cả hai có thể đọc cùng `old` rồi cùng ghi một kết quả. Để tăng đúng, dùng `getAndAdd` hoặc CAS loop.

## 4. Java 17+ hoàn chỉnh: bộ đếm atomic bằng `getAndAdd`

Tạo file `VarHandleCounter.java`:

```java
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class VarHandleCounter {
    private int count;

    private static final VarHandle COUNT;

    static {
        try {
            COUNT = MethodHandles.lookup()
                    .findVarHandle(VarHandleCounter.class, "count", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public void increment() {
        COUNT.getAndAdd(this, 1);
    }

    public int get() {
        return (int) COUNT.getVolatile(this);
    }

    public static void main(String[] args) throws InterruptedException {
        VarHandleCounter counter = new VarHandleCounter();
        Thread[] workers = new Thread[4];

        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < 1_000; j++) {
                    counter.increment();
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("Final count: " + counter.get());
    }
}
```

Biên dịch và chạy:

```bash
javac VarHandleCounter.java
java VarHandleCounter
```

Đầu ra khi bốn worker kết thúc bình thường:

```text
Final count: 4000
```

**Điểm cốt lõi:**

1. `COUNT` là handle tới trường instance `int count` của `VarHandleCounter`; mỗi lần gọi cần truyền đúng receiver (`this`).
2. `getAndAdd(this, 1)` là **một thao tác read–modify–write atomic**, khác với lấy `getVolatile` rồi `setVolatile` thành hai bước.
3. `getVolatile(this)` đọc kết quả với semantics volatile. `Thread.join()` cũng tạo quan hệ happens-before từ hành động của worker tới thread main sau khi chờ thành công.
4. Scheduler có thể chạy các worker theo mọi thứ tự, nhưng không làm mất lượt tăng; 4 × 1.000 = 4.000.

VarHandle phù hợp khi cần điều khiển kiểu truy cập của một trường cụ thể. Với **bộ đếm đơn giản**, `AtomicInteger` thường ngắn hơn, dễ đọc hơn và có ít cơ hội sử dụng sai access mode. Dùng VarHandle khi bạn thật sự cần một kiểu layout hoặc giao thức mà các lớp atomic không diễn đạt rõ.

## 5. Compare-and-set: kiểm tra và cập nhật cùng một bước

`compareAndSet(receiver, expected, newValue)` kiểm tra biến hiện tại có bằng expected không và, nếu đúng, ghi giá trị mới **nguyên tử**. Kết quả là `boolean`:

- `true`: phép thay thành công.
- `false`: giá trị hiện tại không khớp expected (hoặc, với **weak** CAS variants, còn có thể thất bại giả—*spurious failure*).

Với kiểu reference, so sánh trong các primitive CAS liên quan đến **tham chiếu** (reference identity) chứ không phải tự gọi logic `equals()` của object. Với primitive, so sánh theo kiểu dữ liệu hỗ trợ.

### CAS loop để giữ bất biến “số lượng không âm”

Tạo file `VarHandleInventory.java`:

```java
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;

public class VarHandleInventory {
    private int remaining = 3;
    private static final VarHandle REMAINING;

    static {
        try {
            REMAINING = MethodHandles.lookup()
                    .findVarHandle(VarHandleInventory.class,
                                   "remaining", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public boolean reserveOne() {
        while (true) {
            int old = (int) REMAINING.getVolatile(this);
            if (old <= 0) {
                return false;
            }
            if (REMAINING.compareAndSet(this, old, old - 1)) {
                return true;
            }
            // Giá trị đã thay đổi: phải đọc lại và thử lần nữa.
        }
    }

    public int remaining() {
        return (int) REMAINING.getVolatile(this);
    }

    public static void main(String[] args) throws InterruptedException {
        VarHandleInventory inventory = new VarHandleInventory();
        AtomicInteger accepted = new AtomicInteger();
        Thread[] buyers = new Thread[10];

        for (int i = 0; i < buyers.length; i++) {
            buyers[i] = new Thread(() -> {
                if (inventory.reserveOne()) {
                    accepted.incrementAndGet();
                }
            });
            buyers[i].start();
        }

        for (Thread buyer : buyers) {
            buyer.join();
        }

        System.out.println("Accepted: " + accepted.get());
        System.out.println("Remaining: " + inventory.remaining());
    }
}
```

Chạy:

```bash
javac VarHandleInventory.java
java VarHandleInventory
```

Đầu ra khi tất cả thread hoàn tất:

```text
Accepted: 3
Remaining: 0
```

Các thread tranh nhau nhưng chỉ ba phép chuyển `3 → 2 → 1 → 0` có thể thành công. Khi CAS thất bại vì giá trị bị thay, vòng lặp không dùng snapshot cũ nữa. Phép kiểm tra `old > 0` và việc decrement được liên kết bởi CAS tại **điểm tuyến tính hóa** của lần cập nhật thành công.

**Phạm vi atomicity:** một CAS chỉ bảo vệ `remaining`. Nếu thao tác bán hàng còn phải thêm record vào danh sách, ghi file hoặc cập nhật tài nguyên ngoài JVM, CAS trên một trường **không** biến toàn bộ công việc thành transaction. Cần lựa chọn thêm cơ chế bảo vệ các bất biến liên quan.

## 6. `compareAndExchange`, weak CAS và các atomic access mode

### `compareAndSet` và `compareAndExchange` khác nhau

- `compareAndSet(expected, update)` trả về `boolean` thành công/thất bại.
- `compareAndExchange(expected, update)` trả về **giá trị thực sự quan sát tại thời điểm so sánh** (*witness value*). Nếu witness bằng expected, phép ghi được thực hiện; nếu không, witness là giá trị đã khiến thao tác thất bại.
- Các biến thể `compareAndExchangeAcquire` và `compareAndExchangeRelease` cho phép chọn memory-ordering yếu hơn ở những chiều phù hợp của thao tác; phải đọc đúng hợp đồng API.
- `weakCompareAndSetPlain` cùng các biến thể weak có thể **thất bại giả** ngay khi giá trị hiện tại vẫn khớp expected. Khi dùng chúng trong thuật toán, thường cần retry và tính đến tiến triển trong điều kiện cạnh tranh.

Ví dụ ý tưởng với một VarHandle có value type `int` và tọa độ receiver:

```java
// Snippet: VALUE là VarHandle cho trường int của obj.
boolean swapped = VALUE.compareAndSet(obj, 5, 6);
int witness = (int) VALUE.compareAndExchange(obj, 6, 7);
```

Đây là snippet, không thể dự đoán `swapped`/`witness` nếu chưa biết trạng thái thực của `obj`. Không in một kết quả “đảm bảo” cho mã có trạng thái chưa xác định.

### Atomic read–modify–write

Các access mode như `getAndSet`, `getAndAdd`, `getAndBitwiseOr`/`And`/`Xor` (khi type hỗ trợ) đọc giá trị cũ và cập nhật biến như **một thao tác atomic**.

`getAndAdd` trả về **giá trị trước khi cộng**, không phải kết quả sau cộng. Ví dụ nếu giá trị hiện tại là 10:

```java
// Snippet: VALUE là VarHandle kiểu int của obj.
int old = (int) VALUE.getAndAdd(obj, 5); // old = 10
// Giá trị được lưu là 15.
```

Để biết một mode có dùng được với handle cụ thể hay không, kiểm tra:

```java
boolean allowed =
        VALUE.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD);
```

Các snippet trên chưa bao gồm khai báo `obj`, `VALUE`; ví dụ array ở phần 8 là một chương trình chạy độc lập.

**Chọn mode đúng với thuật toán:** `getAndAdd` cho bộ đếm; CAS loop cho cập nhật có điều kiện; `getAndSet` cho trao đổi một giá trị; các mode acquire/release dùng khi chứng minh được ordering yếu vẫn đủ. Chọn mode yếu “để chạy nhanh hơn” mà không chứng minh happens-before có thể tạo bug rất khó tái hiện.

## 7. Java 17+: publication với `setRelease` và `getAcquire`

Một nhu cầu phổ biến: producer tạo dữ liệu rồi công bố cờ sẵn sàng; consumer chỉ đọc dữ liệu **sau khi quan sát được cờ**. Ta không cần khóa toàn bộ object nếu giao thức một lần này được thiết kế chính xác.

Tạo file `VarHandlePublication.java`:

```java
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class VarHandlePublication {
    private int payload;
    private int ready;

    private static final VarHandle READY;

    static {
        try {
            READY = MethodHandles.lookup()
                    .findVarHandle(VarHandlePublication.class,
                                   "ready", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        VarHandlePublication signal = new VarHandlePublication();

        Thread producer = new Thread(() -> {
            signal.payload = 42;
            READY.setRelease(signal, 1);
        });

        Thread consumer = new Thread(() -> {
            while ((int) READY.getAcquire(signal) == 0) {
                Thread.onSpinWait();
            }
            System.out.println("Received: " + signal.payload);
        });

        consumer.start();
        producer.start();
        producer.join();
        consumer.join();
    }
}
```

Chạy:

```bash
javac VarHandlePublication.java
java VarHandlePublication
```

Khi cả hai thread hoàn tất bình thường, đầu ra:

```text
Received: 42
```

**Phân tích theo memory ordering:**

1. Producer ghi trường thường `payload = 42` **trước** khi phát cờ bằng `setRelease(signal, 1)`.
2. Consumer lặp `getAcquire(signal)` cho tới khi thấy 1. Việc acquire đọc cờ đã được phát bằng release thiết lập ordering cần thiết để truy cập `payload` ở phía sau đọc dữ liệu đã được công bố.
3. `payload` không cần tự được khai báo volatile trong **giao thức một writer, publish một lần, không có ghi tiếp vào payload** này.
4. `Thread.onSpinWait()` chỉ gợi ý cho JVM/CPU về busy-spin; nó **không thay thế** acquire/release hoặc tạo signal.
5. Để tránh vòng chờ vô hạn nếu producer không công bố do lỗi, production phải có chính sách hủy, timeout hoặc dùng một primitive blocking phù hợp.

**Giới hạn:** nếu producer thay payload liên tục trong khi consumer đang đọc, hoặc nhiều producer cùng công bố một cờ duy nhất, giao thức trên **không tự đủ** để cung cấp snapshot đa phiên bản. Khi đó cần version, queue, immutable snapshot hoặc khóa phù hợp với yêu cầu.

## 8. Java 17+: VarHandle cho phần tử mảng

Một handle cho phần tử `int[]` yêu cầu **hai tọa độ**: mảng và chỉ số. Không cần định nghĩa trường trong class riêng. Tạo file `VarHandleArray.java`:

```java
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class VarHandleArray {
    private static final VarHandle ELEMENT =
            MethodHandles.arrayElementVarHandle(int[].class);

    public static void main(String[] args) {
        int[] values = {10, 20};

        int old = (int) ELEMENT.getAndAdd(values, 1, 5);
        boolean replaced = ELEMENT.compareAndSet(values, 0, 10, 15);

        System.out.println("Old second: " + old);
        System.out.println("New second: "
                + (int) ELEMENT.getVolatile(values, 1));
        System.out.println("First replaced: " + replaced);
        System.out.println("New first: "
                + (int) ELEMENT.getVolatile(values, 0));
    }
}
```

Chạy:

```bash
javac VarHandleArray.java
java VarHandleArray
```

Đầu ra:

```text
Old second: 20
New second: 25
First replaced: true
New first: 15
```

Trong ví dụ, `getAndAdd(values, 1, 5)` cộng 5 **nguyên tử** vào phần tử chỉ số 1 và trả giá trị cũ 20. `compareAndSet(values, 0, 10, 15)` thành công vì giá trị tại chỉ số 0 đúng là 10. Một thao tác atomic trên phần tử `values[0]` **không** tạo transaction cho toàn bộ mảng; nếu cần thay nhiều phần tử như một snapshot bất biến thì phải thêm giao thức thích hợp.

VarHandle mảng là lựa chọn tốt khi cần atomic access từng slot của một mảng primitive/reference. Không suy từ nó rằng `ArrayList` hoặc một cấu trúc dữ liệu mutable phức tạp cũng an toàn để thao tác đồng thời.

## 9. Mối quan hệ với JMM: visibility, atomicity và ordering

Phải phân biệt ba câu hỏi độc lập:

1. **Visibility:** thread đọc có quan sát được dữ liệu writer đã công bố không?
2. **Ordering:** các thao tác phụ thuộc trước/sau nhau có được sắp đúng theo giao thức đồng bộ không?
3. **Atomicity:** phép kiểm tra–cập nhật có diễn ra như một bước không thể bị chen ngang trên vị trí đó không?

Ví dụ:

- `VALUE.getVolatile(obj)` giải quyết yêu cầu đọc kiểu volatile, nhưng đọc rồi gọi `setVolatile` ở bước sau **không** là atomic read–modify–write.
- `VALUE.getAndAdd(obj, 1)` là phép cộng nguyên tử trên một vị trí, nhưng không bảo vệ toàn bộ invariant gồm thêm một trường khác.
- `setRelease(flag, 1)` và `getAcquire(flag)` phù hợp để publish dữ liệu đã ghi **trước** tín hiệu cho reader chỉ đọc dữ liệu **sau** khi thấy tín hiệu; không bảo đảm những ghi tiếp theo của writer sẽ là snapshot nhất quán.
- `getOpaque`/`setOpaque` yếu hơn acquire/release về ordering, nên không tự thay cho giao thức publish dữ liệu phụ thuộc.
- `get`/`set` plain trên cùng trường mutable từ các thread khác nhau không có đồng bộ thích hợp có thể tạo data race.

### VarHandle có làm trường thường thành volatile mãi mãi không?

**Không.** Một `VarHandle` cho phép gọi **nhiều mode khác nhau** trên cùng vị trí. Độ mạnh của một mode là thuộc tính của lần truy cập cụ thể. Nếu ở một đường code dùng `setRelease` nhưng đường khác dùng `get` plain khi cần đọc dữ liệu đã publish, không thể chỉ viện dẫn rằng “trường đã được xử lý bằng VarHandle” để khẳng định an toàn.

Thiết kế cần quy định **cặp mode phù hợp** trên mọi đường truy cập, cùng bất biến và quan hệ đồng bộ. Một accessor dùng mode khác có thể phá cách suy luận ban đầu.

### Fences

`VarHandle` còn có các phương thức static như `fullFence()`, `acquireFence()`, `releaseFence()`, `loadLoadFence()` và `storeStoreFence()` để diễn tả một số ràng buộc thứ tự bộ nhớ độc lập với một phép truy cập biến cụ thể. Đây là công cụ cấp thấp cho các thuật toán đồng thời chuyên sâu.

**Không** dùng một fence lẻ như bản sửa thần kỳ cho data race hay lỗi check–then–act. Phải có giao thức tương tác đầy đủ giữa reader/writer, chứng minh tính hợp lệ theo JMM và chọn đúng vị trí đặt fence. Khi chưa có lý do rất cụ thể, dùng các access mode acquire/release/volatile hoặc API concurrent cao hơn thường dễ kiểm chứng hơn.

## 10. Kiểu dữ liệu, signature và lỗi khi gọi VarHandle

Các phương thức `get`, `set`, `compareAndSet`... có hình thức **signature-polymorphic**. Trình biên dịch/JVM kiểm tra kiểu tham số/tọa độ và kiểu trả về theo access mode. Vì vậy:

- Handle trường instance cần receiver đúng kiểu và các tham số thích hợp với `varType()`.
- Handle static **không** cần receiver.
- Handle array cần tọa độ **array** và **index** trước giá trị mới hoặc expected/new.
- Kết quả đọc thường cần cast phù hợp, ví dụ `int n = (int) HANDLE.getVolatile(obj)`.
- Ký hiệu `(int) HANDLE.getVolatile(...)` không nên bỏ đi khi dùng trong biểu thức kiểu Java dễ suy diễn sai.
- Gọi với kiểu/ký hiệu không phù hợp có thể gây lỗi liên quan method type như `WrongMethodTypeException`; receiver sai kiểu có thể gây lỗi runtime tương ứng.
- Mode không hỗ trợ bởi loại biến/handle có thể ném `UnsupportedOperationException`.
- Khi dùng handle field có quyền truy cập restricted, việc tạo handle có thể thất bại vì `IllegalAccessException`; không thể sửa bằng việc lén thay đổi access mode.

### Kiểm tra capability trước khi chọn mode

```java
// Snippet: handle được tạo hợp lệ ở chỗ khác.
boolean atomicAddSupported =
        handle.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD);
Class<?> valueType = handle.varType();
```

Đoạn này chưa phải chương trình độc lập. Kiểm tra capability đặc biệt hữu ích nếu viết utility tổng quát nhận handle nhiều kiểu khác nhau. Với các demo kiểu `int` bên trên, mode `GET_AND_ADD` được hỗ trợ.

**Một điểm thiết kế:** `VarHandle` không tự động gom các thao tác bất kỳ thành primitive cấp cao. Nếu mode không hỗ trợ, không nên giả lập bằng `get` rồi `set` khi thuật toán đòi nguyên tử tính.

## 11. So sánh VarHandle với `AtomicInteger`, `AtomicReference` và lock

| Cơ chế | Khi nào phù hợp | Giới hạn / chi phí suy luận |
| --- | --- | --- |
| `AtomicInteger`/`AtomicLong` | Bộ đếm, trạng thái primitive đơn có CAS và atomic update | Tạo object wrapper và API gắn với biến atomic cụ thể |
| `AtomicReference` | Thay thế một reference (thường trỏ tới immutable snapshot) bằng CAS | Chỉ atomic với **tham chiếu**, không tự làm object được tham chiếu trở thành bất biến |
| `AtomicIntegerFieldUpdater` | Atomic access tới một field theo ràng buộc của updater | Điều kiện kiểu/field và cơ chế truy cập riêng |
| **`VarHandle`** | Cần access mode cụ thể cho field/static/array slot, xây primitive concurrent hoặc kiểm soát memory semantics | API cấp thấp; phải tự thiết kế đúng cặp mode, casts và giao thức |
| `synchronized`/`ReentrantLock` | Cần critical section hoặc invariant liên quan nhiều trường | Lock contention, nguy cơ deadlock; nhưng thường đơn giản hơn CAS phức tạp |
| Immutable snapshot qua `volatile`/atomic reference | Nhiều reader chỉ đọc, writer thay bản đầy đủ | Writer có thể phải tạo snapshot mới; reader cần tránh sửa nội dung snapshot |

**VarHandle không phải lựa chọn bắt buộc để có code nhanh hơn.** Nếu `AtomicInteger.incrementAndGet()` diễn đạt trọn nghiệp vụ, nó thường là lựa chọn rõ ràng. VarHandle phát huy giá trị trong triển khai thư viện cấu trúc dữ liệu, thuật toán non-blocking, mảng atomic hoặc những trường cần phối hợp mode ordering khác nhau. Đo benchmark thực tế trước khi kết luận hiệu năng.

## 12. Những sai lầm dễ gây lỗi concurrency

1. **“VarHandle = volatile ở mọi truy cập.”** Sai: plain, opaque, acquire/release và volatile có semantics khác nhau.
2. **“`getVolatile` rồi `setVolatile` là một CAS.”** Sai: vẫn là hai thao tác; thread khác chen vào được.
3. **“`getAndAdd` trả về giá trị mới.”** Sai: nó trả giá trị **trước** cập nhật.
4. **“`compareAndSet` có thể được thay bằng hai thao tác get/set.”** Sai: check–then–act khi tách ra không atomic.
5. **“Weak CAS false nghĩa là giá trị chắc chắn không khớp.”** Sai: weak CAS có thể thất bại giả.
6. **“`setOpaque`/`getOpaque` công bố tất cả các trường viết trước.”** Sai: ordering yếu hơn giao thức release/acquire cần cho dữ liệu phụ thuộc.
7. **“Writer dùng setRelease nhưng reader dùng plain get vẫn đủ.”** Không được suy ra có giao thức công bố an toàn.
8. **“CAS trên một field bảo vệ cả object nhiều field.”** Sai: invariant liên trường cần cơ chế khác hoặc biểu diễn thành một trạng thái atomic.
9. **“Một handle tới private field vượt được Java module system.”** Sai: quyền tạo handle bị kiểm tra.
10. **“Mode nào cũng hợp lệ cho mọi kiểu.”** Sai: dùng `isAccessModeSupported` nếu viết utility tổng quát.
11. **“Cứ dùng VarHandle sẽ nhanh hơn synchronized.”** Sai: phụ thuộc contention, đường đọc/ghi, CPU/JVM, độ phức tạp retry và cache effects.
12. **“Một lần test ra kết quả đúng nghĩa là memory ordering đúng.”** Sai: lỗi JMM có thể chỉ xuất hiện ở một lịch/JVM/hardware khác.

## 13. Checklist áp dụng VarHandle vào code thật

1. **Chọn biến:** trường instance/static hay phần tử mảng? Giá trị thuộc kiểu gì? Các tọa độ nhận dạng vị trí là gì?
2. **Xác định quyền truy cập:** handle có được tạo bằng `Lookup` hợp lệ và không phá module encapsulation không?
3. **Nêu invariant:** chỉ cần atomic cho một trường, hay phải bảo vệ nhiều trường cùng lúc?
4. **Chọn access mode nhỏ nhất có thể chứng minh đúng:** plain/opaque chỉ khi có cơ chế khác, acquire/release cho giao thức xuất bản, volatile khi cần semantics mạnh hơn, CAS/getAndAdd cho atomic RMW.
5. **Chứng minh reader/writer hợp tác:** mode phía đọc có tương thích với mode phía ghi? Dữ liệu phụ thuộc được ghi trước publish và chỉ đọc sau acquire không?
6. **Xử lý CAS thất bại:** nếu dùng loop, đọc lại state; có chính sách tiến triển, giới hạn retry/timeout nếu nghiệp vụ cần.
7. **Không có side effect lặp ngoài ý muốn:** CAS loop có thể chạy nhiều lần; không gửi mail hay ghi file trong thân hàm tính toán retry mà không có idempotency.
8. **Kiểm tra kiểu và hỗ trợ mode:** receiver, array index, return cast, `AccessMode` có hợp lệ không?
9. **Kiểm thử concurrency thật:** dùng bất biến, interleaving có kiểm soát, stress test; dùng jcstress cho các litmus test JMM chuyên sâu khi phù hợp.
10. **Cân nhắc API cao hơn:** một `AtomicInteger`/lock/queue/immutable snapshot có thể dễ đọc, dễ review và an toàn hơn.
11. **Đo thay vì đoán:** benchmark với warm-up JVM và workload đại diện; phân biệt hiệu năng với tính đúng đắn.
12. **Ghi lại policy:** mọi lần truy cập cùng state nên tuân thủ quy tắc access mode và memory ordering đã quyết định.

**Kết luận:** VarHandle là công cụ JDK để truy cập một biến với **access mode được chọn rõ ràng**, bao gồm plain, opaque, acquire/release, volatile và nhiều thao tác atomic. Sức mạnh của nó nằm ở việc thể hiện chính xác memory semantics mà một thuật toán concurrent cần; trách nhiệm của người viết là chứng minh giao thức giữa các thread, bảo vệ invariant và không nhầm nhiều phép truy cập riêng lẻ với một thao tác nguyên tử.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-011">Atomic không thay thế giao dịch nhiều đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Atomic không thay thế giao dịch nhiều đối tượng trong Java Concurrency

Trong Java, các lớp như `AtomicInteger`, `AtomicLong`, `AtomicBoolean` và `AtomicReference` cung cấp **thao tác nguyên tử trên một đơn vị trạng thái cụ thể**. Ví dụ, `incrementAndGet()` không mất lượt tăng; `compareAndSet(expected, update)` chỉ cập nhật khi giá trị hiện tại khớp expected. Nhưng nếu một nghiệp vụ phải sửa **hai tài khoản, hai trường liên quan hoặc nhiều object khác nhau**, từng phép cập nhật atomic riêng lẻ **không tự biến toàn bộ quy trình thành một giao dịch nguyên tử**.

Một phép chuyển tiền là ví dụ rõ nhất: trừ tiền ở A và cộng tiền ở B phải cùng nhau tạo nên một trạng thái hợp lệ. Nếu thread khác nhìn thấy giữa hai bước, tổng tiền tạm thời giảm. Nếu có exception hoặc JVM dừng sau bước trừ, tổng có thể sai lâu dài. Dùng hai `AtomicLong` không giải quyết ranh giới giao dịch ấy.

Bài học thuộc **Atomic & CAS trong Java Concurrency nói chung**. Nội dung phân biệt tính nguyên tử của từng biến với transaction nhiều object; hướng dẫn dùng một vùng khóa chung, khóa nhiều object có thứ tự, hoặc gộp state bất biến vào một `AtomicReference`. Các cơ chế tích hợp riêng của Spring không nằm trong phạm vi.

## 1. Atomic là gì, và phạm vi bảo đảm nằm ở đâu?

`java.util.concurrent.atomic` chứa các primitive phục vụ lập trình đồng thời. Một số ví dụ:

| API | Phép toán nguyên tử điển hình | Đơn vị được bảo vệ |
| --- | --- | --- |
| `AtomicInteger.incrementAndGet()` | Đọc–sửa–ghi một số nguyên không bị lost update do contender cùng thao tác trên atomic ấy. | Một `AtomicInteger`. |
| `AtomicLong.addAndGet(delta)` | Cộng vào một biến atomic và trả về giá trị mới. | Một `AtomicLong`. |
| `AtomicBoolean.compareAndSet(false, true)` | Chuyển cờ nếu giá trị hiện tại còn false. | Một `AtomicBoolean`. |
| `AtomicReference<T>.compareAndSet(old, next)` | Thay **reference** nếu còn là expected reference. | Một reference atomic. |
| `AtomicReference<T>.getAndUpdate(fn)` | Thử cập nhật một reference theo hàm, có thể thực thi lại hàm khi tranh chấp. | Một reference atomic; không tự bảo vệ side effect của fn. |
| `AtomicIntegerArray` | Các thao tác nguyên tử **trên từng phần tử** được chỉ định. | Một phần tử tại một chỉ số mỗi lần thao tác. |

Các thao tác atomic tiêu chuẩn còn có **memory effects** theo hợp đồng Java Memory Model. Tuy nhiên có ba tầng correctness cần xét riêng:

1. **Atomicity:** một thao tác RMW riêng lẻ có bị mất cập nhật hay không?
2. **Visibility/ordering:** các thread quan sát phép ghi theo quan hệ đồng bộ/happens-before nào?
3. **Invariant liên biến/liên đối tượng:** nhiều giá trị có cùng phản ánh **một trạng thái nghiệp vụ hợp lệ** không?

Một chương trình có thể đạt (1) và (2) cho từng atomic variable nhưng **vẫn không đạt (3)** cho toàn bộ nghiệp vụ.

### 1.1 Giao dịch là một hợp đồng lớn hơn một instruction CAS

Trong bài này, “giao dịch” theo nghĩa *một operation logic toàn vẹn* phải có đặc tính tối thiểu:

- **All-or-nothing về trạng thái được bảo vệ:** các bước phụ thuộc phải được quan sát như một thay đổi duy nhất, hoặc không có bước nào được commit.
- **Invariant được giữ:** ví dụ `balanceA + balanceB == total`.
- **Isolation ở ranh giới quan sát đã định:** người đọc không được lấy nửa trạng thái cũ và nửa trạng thái mới nếu hợp đồng yêu cầu snapshot nhất quán.
- **Exception/cancellation safety:** lỗi ở giữa không được để object ở trạng thái vi phạm invariant.

Đây **không mặc nhiên là ACID transaction của database**. Atomicity trong bộ nhớ một JVM khác với durability qua JVM crash, hai process, database hoặc remote service. Phân biệt phạm vi ngay từ đầu để tránh dùng sai cơ chế.

## 2. Ví dụ kinh điển: hai AtomicLong vẫn có khoảng hở

Giả sử hai tài khoản nội bộ có tổng 100 đơn vị:

~~~java
AtomicLong accountA = new AtomicLong(100);
AtomicLong accountB = new AtomicLong(0);

accountA.addAndGet(-30);
accountB.addAndGet(30);
~~~

Từng `addAndGet` là atomic **trên chính biến đó**. Nhưng cả hai phép cộng/trừ là **hai điểm cập nhật khác nhau**. Trong khoảng giữa chúng, state thật sự có thể là:

~~~text
Trước:  A=100, B=0,  A+B=100
Giữa:  A=70,  B=0,  A+B=70
Sau:    A=70,  B=30, A+B=100
~~~

Một thread đọc báo cáo tài sản đúng lúc giữa hai bước thấy tổng 70. Nó không phải “đọc cache lỗi thời”; đó là một **trạng thái trung gian có thật** mà giao thức cho phép quan sát.

Tình huống còn nguy hiểm hơn nếu exception xuất hiện sau debit nhưng trước credit. Việc cả hai field là atomic không tạo cơ chế rollback, không làm việc cộng bên B tự diễn ra khi bước trừ A đã thành công.

## 3. Chương trình Java 17+: tái hiện lỗi một cách xác định

Thay vì dựa vào một vòng stress test may rủi bắt được interleaving, sử dụng hai `CountDownLatch` để tạm dừng thread chuyển tiền ngay sau bước trừ.

Tạo tệp `AtomicTransferGapDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class AtomicTransferGapDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger accountA = new AtomicInteger(100);
        AtomicInteger accountB = new AtomicInteger(0);
        CountDownLatch debited = new CountDownLatch(1);
        CountDownLatch continueTransfer = new CountDownLatch(1);

        Thread transfer = new Thread(() -> {
            boolean success = accountA.compareAndSet(100, 70);
            if (!success) {
                throw new AssertionError("Unexpected starting balance");
            }
            debited.countDown();
            try {
                continueTransfer.await();
                accountB.addAndGet(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "transfer");

        transfer.start();
        debited.await();

        try {
            System.out.println("During: A=" + accountA.get()
                    + ", B=" + accountB.get()
                    + ", total=" + (accountA.get() + accountB.get()));
        } finally {
            continueTransfer.countDown();
        }

        transfer.join();
        System.out.println("After: A=" + accountA.get()
                + ", B=" + accountB.get()
                + ", total=" + (accountA.get() + accountB.get()));
    }
}
~~~

Biên dịch và chạy trên JDK 17 trở lên:

~~~bash
javac AtomicTransferGapDemo.java
java AtomicTransferGapDemo
~~~

Stdout trong điều kiện bình thường, không có external interruption:

~~~text
During: A=70, B=0, total=70
After: A=70, B=30, total=100
~~~

**Vì sao output có thể xác định?** Thread transfer đã hoàn thành CAS trừ tiền và báo `debited.countDown()`. Main gọi `debited.await()` rồi đọc hai atomic, trong khi transfer chưa thể cộng vào B do `continueTransfer.await()`. Main mở cổng trong `finally` rồi chờ bằng `join()`. Latch phục vụ **kiểm soát thời điểm thử nghiệm**, không phải cơ chế giao dịch.

Ngay cả khi `accountA.compareAndSet` là một CAS có điều kiện đúng, CAS đó **không bao phủ biến accountB**. Thêm `volatile`, `get()` nhiều lần hoặc `Thread.sleep()` không biến hai phép cập nhật thành một operation.

**Lưu ý phạm vi demo:** nếu thread transfer bị interrupt bên ngoài trong lúc đợi latch, đoạn ví dụ có thể bỏ qua bước credit. Đó chính là một kiểu failure window mà production code phải xử lý; không lấy ví dụ lỗi làm mã nghiệp vụ an toàn.

## 4. CAS bảo vệ một precondition nhưng không commit nhiều object

Có thể sửa rủi ro “rút tiền vượt số dư” trên riêng A bằng vòng CAS:

~~~java
boolean withdraw(AtomicLong balance, long amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException("amount must be positive");
    }
    while (true) {
        long current = balance.get();
        if (current < amount) {
            return false;
        }
        if (balance.compareAndSet(current, current - amount)) {
            return true;
        }
    }
}
~~~

Đây là **mảnh method Java**, giả định import `AtomicLong`; nó biên dịch khi được đặt trong class phù hợp. Phép CAS giữ được điều kiện không trừ dưới 0 đối với **một balance**, nếu mọi đường ghi balance dùng cùng giao thức. Nhưng nếu sau đó chuyển tiền bằng:

~~~java
if (withdraw(accountA, 30)) {
    accountB.addAndGet(30);
}
~~~

thì giữa hai hành động vẫn tồn tại khoảng hở. CAS trên A chỉ là **điểm tuyến tính hóa của withdraw**, không phải điểm tuyến tính hóa của **transfer A→B**.

### 4.1 Nguyên tắc ranh giới commit

Khi operation gồm nhiều thay đổi, phải trả lời:

- Khi nào có thể nói “transfer đã thành công”?
- Thread khác có được phép đọc cả hai tài khoản trong lúc transfer chưa hoàn tất không?
- Nếu credit thất bại, có rollback được debit không? Nếu rollback lại gặp tranh chấp thì sao?
- Nếu process chết giữa chừng, trạng thái có được khôi phục không?
- Những method nào khác sửa A/B? Chúng có cùng protocol không?

Nếu không thể chỉ ra một ranh giới commit bảo vệ toàn bộ invariant, việc bổ sung thêm AtomicInteger/AtomicLong không giải quyết được nguyên nhân gốc.

## 5. Atomic field riêng lẻ và snapshot nhất quán là hai bài toán khác nhau

Cả hai đoạn đọc sau đều **không bảo đảm** một snapshot tuyến tính hóa của cặp tài khoản khi writer đang cập nhật chúng độc lập:

~~~java
long a = accountA.get();
long b = accountB.get();
long total = a + b;
~~~

hoặc:

~~~java
long a1 = accountA.get();
long b = accountB.get();
long a2 = accountA.get();
if (a1 == a2) {
    // Không phải bằng chứng tổng quát rằng cả a và b cùng một phiên bản.
}
~~~

Đây là **mảnh code minh họa**, giả định hai atomic đã được tạo. Trong đoạn thứ hai, A có thể thay đổi rồi quay về giá trị ban đầu (**ABA**), hoặc B đổi độc lập trong lúc A không đổi. So sánh hai lần đọc A không xác nhận một giao dịch tổng thể đã xảy ra hay chưa.

**Snapshot consistency** đòi hỏi tất cả giá trị quan trọng được lấy từ **một trạng thái logic**. Một số cách đạt được:

1. Một `synchronized`/`ReentrantLock` chung bảo vệ mọi writer và reader của invariant.
2. Một immutable aggregate state, publish qua `AtomicReference<State>` và reader đọc **một reference**.
3. Một protocol versioned snapshot/seqlock được thiết kế rất cẩn thận (khó hơn nhiều, có khả năng retry/starvation, không thích hợp nếu tự viết tùy tiện).
4. Transaction của nơi lưu dữ liệu nếu state nằm ngoài một JVM.

Nếu yêu cầu nghiệp vụ chỉ cần số liệu gần đúng (ví dụ dashboard metrics), snapshot tạm lệch có thể chấp nhận. Nhưng nếu ra quyết định rút tiền, cấp quota hoặc kiểm tra invariant tài chính, phải xác định consistency contract chặt hơn.

## 6. Đúng hướng 1: một owner và một critical section chung

Khi hai balance thuộc **cùng một owner object** trong một JVM, giải pháp dễ hiểu nhất thường là một lock bảo vệ cả hai.

Ý tưởng:

~~~java
synchronized (lock) {
    if (accountA >= amount) {
        accountA -= amount;
        accountB = Math.addExact(accountB, amount);
    }
}
~~~

Đây là mảnh minh họa, chưa xử lý toàn bộ validation và exception safety. Cần **tính/validate giá trị đích trước khi mutate** vì `Math.addExact` có thể ném exception; nếu trừ A xong mới gọi `addExact` rồi xảy ra overflow, state bị dở dang khi monitor được nhả.

Mẫu an toàn hơn:

~~~java
synchronized (lock) {
    if (accountA < amount) {
        return false;
    }
    long nextA = accountA - amount;
    long nextB = Math.addExact(accountB, amount);

    accountA = nextA;
    accountB = nextB;
    return true;
}
~~~

Đoạn trên nằm trong method trả `boolean`, giả định `amount > 0` đã được kiểm tra. Các phép tính có thể throw được thực hiện trước commit; sau đó chỉ còn phép gán primitive. Mọi reader cần đọc cả A và B **dưới cùng lock**, vì trong nội bộ critical section vẫn có hai phép gán liên tiếp.

### 6.1 Chương trình Java 17+ có thể chạy độc lập

Tạo tệp `LockedTransferDemo.java`:

~~~java
public final class LockedTransferDemo {
    private record Snapshot(long accountA, long accountB) {
        long total() {
            return Math.addExact(accountA, accountB);
        }
    }

    private static final class Ledger {
        private final Object lock = new Object();
        private long accountA = 100;
        private long accountB;

        boolean transfer(boolean fromA, long amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            synchronized (lock) {
                long from = fromA ? accountA : accountB;
                long to = fromA ? accountB : accountA;
                if (from < amount) {
                    return false;
                }

                long nextFrom = from - amount;
                long nextTo = Math.addExact(to, amount);
                if (fromA) {
                    accountA = nextFrom;
                    accountB = nextTo;
                } else {
                    accountB = nextFrom;
                    accountA = nextTo;
                }
                return true;
            }
        }

        Snapshot snapshot() {
            synchronized (lock) {
                return new Snapshot(accountA, accountB);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Ledger ledger = new Ledger();
        Runnable work = () -> {
            for (int i = 0; i < 100; i++) {
                if (!ledger.transfer(true, 1)
                        || !ledger.transfer(false, 1)) {
                    throw new AssertionError("Transfer unexpectedly failed");
                }
            }
        };

        Thread first = new Thread(work, "first");
        Thread second = new Thread(work, "second");
        first.start();
        second.start();
        first.join();
        second.join();

        Snapshot s = ledger.snapshot();
        System.out.println("A=" + s.accountA());
        System.out.println("B=" + s.accountB());
        System.out.println("Total=" + s.total());
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac LockedTransferDemo.java
java LockedTransferDemo
~~~

Stdout khi không có external interruption:

~~~text
A=100
B=0
Total=100
~~~

Mỗi thread thực hiện 100 cặp chuyển 1 đơn vị A→B rồi B→A. Vì mỗi thread chỉ chuyển về sau khi đã chuyển đi, tổng số lượt A→B đã commit không thể nhỏ hơn số lượt B→A đã commit; B có đủ cho mỗi lượt chuyển về. Trong thời gian chạy, số dư A và B có thể thay đổi, nhưng **mọi snapshot lấy qua `ledger.snapshot()`** giữ tổng bằng 100 và không có balance âm.

Các điểm quan trọng:

- `Ledger` sở hữu **cả hai balance**. Caller không thể tự sửa một tài khoản qua reference public.
- `transfer` gồm kiểm tra số dư, kiểm tra overflow và cập nhật **hai trường dưới một lần lock**.
- `snapshot` lấy **cùng lock**, trả immutable record. Reader không ghép hai phiên bản.
- Cả phép tính `Math.addExact` nằm **trước** commit. Nếu phép tính throw, state cũ còn nguyên.
- `Thread.join()` giúp main nhận biết hai worker đã kết thúc; trong ví dụ này không có operation expected-to-fail.
- Output cuối cùng xác định, nhưng ví dụ cũng dạy **cách đọc snapshot ở mọi thời điểm**, chứ không chỉ đo tổng cuối.

**Không nên mặc định một `synchronized` lock toàn ứng dụng**. Lock nên thuộc owner của đúng invariant; các state không liên quan có thể có owner riêng để tránh contention vô ích.

## 7. Đúng hướng 2: khóa nhiều object theo thứ tự toàn cục

Đôi khi mỗi `Account` phải là một object độc lập, không thể gộp tự nhiên vào một ledger duy nhất. Muốn transfer hai object, cần một protocol khóa **đồng thời cả hai** và yêu cầu mọi code liên quan tuân thủ nó.

Nếu thread T1 khóa A→B và T2 khóa B→A, cả hai có thể deadlock. Cần quy tắc nhất quán, thường là theo **ID duy nhất, bất biến**, không phụ thuộc hướng chuyển:

~~~java
Account first = from.id < to.id ? from : to;
Account second = from.id < to.id ? to : from;

synchronized (first.lock) {
    synchronized (second.lock) {
        // Kiểm tra số dư from và cập nhật cả from/to.
    }
}
~~~

Đây là mảnh mã minh họa. `Account` cần `id` không trùng, `lock` private/được protocol kiểm soát, `balance` và các method liên quan. Cần xử lý trường hợp `from == to`, ID trùng, amount không hợp lệ và overflow; không lấy snippet như một class production hoàn chỉnh.

### 7.1 Điều kiện để đúng thật sự

Không chỉ riêng method transfer phải tuân thủ order:

1. Bất kỳ writer nào sửa balance của một account cũng phải giữ **lock của account ấy**.
2. Một operation ảnh hưởng hai account cần giữ **cả hai lock** theo order ổn định.
3. Một reader yêu cầu **snapshot của cặp account** cũng phải lấy cả hai lock cùng protocol. Đọc từng account qua getter synchronized riêng vẫn có thể lấy từ hai thời điểm khác nhau.
4. Không truyền lock ra ngoài cho code không kiểm soát; callback có thể tự lấy lock ngược thứ tự.
5. Thực hiện validation và tính toán trước khi commit, tránh exception giữa debit và credit.
6. Nếu amount hoặc tổng có khả năng overflow, dùng kiểu số học và kiểm tra miền thích hợp.

Một transfer có hai điểm gán Java bên trong nested synchronized không phải một instruction CPU. Nhưng **đối với tất cả observer tuân thủ lock protocol**, thao tác có thể được xem như một operation nguyên tử; bên ngoài không thể xen vào để quan sát trạng thái trung gian.

### 7.2 Đơn vị khóa và scalability

Khóa hai account có thể cho các giao dịch trên **hai cặp không liên quan** chạy đồng thời. Đó là lợi ích so với một global ledger lock. Đổi lại:

- Logic lock order phức tạp hơn.
- Dễ phát sinh deadlock khi các operation ba hoặc nhiều account chen nhau.
- Snapshot toàn bộ hệ thống phải phối hợp nhiều lock, tốn thời gian.
- Quy tắc ownership và ngoại lệ phải được review trên mọi call path.

Chọn đa lock khi **nhu cầu song song và số lượng object** đủ để biện minh. Không tách lock chỉ vì “đang dùng atomic”, khi hệ thống chưa có giải pháp đọc snapshot đồng nhất.

## 8. Đúng hướng 3: gộp cả invariant vào immutable state rồi CAS một reference

Nếu một nhóm field có thể quản lý như **một state bất biến**, `AtomicReference<State>` cho phép thay **toàn bộ snapshot** bằng một CAS. Đây là cách dùng atomic để đạt một dạng transaction nhiều field **sau khi đã tái thiết kế chúng thành một đơn vị atomic duy nhất**.

~~~text
State old = (A=100, B=0)
      |
      | tạo State mới đầy đủ (A=70, B=30)
      v
AtomicReference<State>.compareAndSet(old, next)
      |
      +-- thành công: toàn bộ snapshot mới có hiệu lực
      |
      +-- thất bại: discard candidate, đọc state mới và thử lại
~~~

Lợi ích cốt lõi là reader có thể lấy `State s = ref.get()` **một lần**, rồi đọc hai field từ chính immutable object đó. Không có “A cũ, B mới” nếu object được xây dựng hoàn chỉnh trước khi publish và không còn bị sửa.

### 8.1 Java 17+ runnable: transfer bằng AtomicReference

Tạo `AtomicStateTransferDemo.java`:

~~~java
import java.util.concurrent.atomic.AtomicReference;

public final class AtomicStateTransferDemo {
    private record State(long accountA, long accountB) {
        State {
            if (accountA < 0 || accountB < 0
                    || Math.addExact(accountA, accountB) != 100) {
                throw new IllegalArgumentException("Invalid state");
            }
        }

        long total() {
            return Math.addExact(accountA, accountB);
        }
    }

    private final AtomicReference<State> current =
            new AtomicReference<>(new State(100, 0));

    boolean transfer(boolean fromA, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        while (true) {
            State old = current.get();
            long from = fromA ? old.accountA() : old.accountB();
            long to = fromA ? old.accountB() : old.accountA();

            if (from < amount) {
                return false;
            }

            long nextFrom = from - amount;
            long nextTo = Math.addExact(to, amount);
            State next = fromA
                    ? new State(nextFrom, nextTo)
                    : new State(nextTo, nextFrom);

            if (current.compareAndSet(old, next)) {
                return true;
            }
            // Có thread khác commit trước: đọc state mới và thử lại.
        }
    }

    State snapshot() {
        return current.get();
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicStateTransferDemo ledger = new AtomicStateTransferDemo();
        Runnable work = () -> {
            for (int i = 0; i < 100; i++) {
                if (!ledger.transfer(true, 1)
                        || !ledger.transfer(false, 1)) {
                    throw new AssertionError("Transfer unexpectedly failed");
                }
            }
        };

        Thread first = new Thread(work, "first");
        Thread second = new Thread(work, "second");
        first.start();
        second.start();
        first.join();
        second.join();

        State result = ledger.snapshot();
        System.out.println("A=" + result.accountA());
        System.out.println("B=" + result.accountB());
        System.out.println("Total=" + result.total());
    }
}
~~~

Chạy JDK 17+:

~~~bash
javac AtomicStateTransferDemo.java
java AtomicStateTransferDemo
~~~

Stdout:

~~~text
A=100
B=0
Total=100
~~~

**Tại sao một CAS đủ cho nhóm trường?**

- `State` là record bất biến chỉ chứa hai primitive `long`; constructor kiểm tra số dư không âm và tổng bằng 100, nên mỗi instance hợp lệ.
- Tất cả writer đi qua `current.compareAndSet(old, next)`. Một CAS thành công thay **duy nhất một reference** từ snapshot cũ sang snapshot mới.
- Reader dùng một lần `current.get()` rồi đọc `accountA` và `accountB` cùng từ một object; không ghép hai trạng thái.
- Nếu contender khác cập nhật trước, CAS thất bại; vòng lặp đọc lại snapshot. Nhờ đó writer không ghi đè một bản cập nhật mới bằng tính toán từ state đã lỗi thời.
- Không có side effect bên ngoài trong đoạn tính candidate, nên có thể retry an toàn trong phạm vi ví dụ.
- Cả hai thread thực hiện số cặp chuyển cân bằng như ví dụ khóa; state cuối cùng xác định.

**Điểm quan trọng:** giải pháp này thành công không phải vì “AtomicReference có phép giao dịch nhiều object” mà vì chúng ta **đổi mô hình dữ liệu**: hai balance không còn là hai mutable object độc lập; chúng là hai thành phần của **một immutable aggregate state** được công bố bằng **một atomic reference**. Nếu hai tài khoản vẫn có setters công khai riêng, một CAS trên reference thứ ba không bảo vệ được các đường cập nhật đó.

### 8.2 CAS loop có thể retry và không miễn phí

Một vòng CAS không khóa monitor nhưng có các chi phí và giới hạn:

- Contention cao làm CAS thất bại nhiều lần, dẫn đến retry, CPU và allocation cho candidate bị bỏ.
- Không bảo đảm một thread cụ thể được phục vụ trong thời gian hữu hạn; **starvation** vẫn có thể xảy ra.
- Object mới được tạo mỗi lần thử; GC và cache behavior phụ thuộc workload.
- Hàm tính `next` phải **thuần hoặc có side effect có thể lặp an toàn**. Các method cập nhật dựa trên `getAndUpdate` cũng có thể gọi update function nhiều lần.
- Một record chứa reference đến object mutable không tự bất biến sâu. Collection/phần tử mutable cần copy hoặc ownership protocol riêng.
- Thuật toán sử dụng reference/giá trị tái sử dụng có thể gặp bài toán **ABA** khi cần phân biệt lịch sử. Trong ví dụ, mỗi state được tạo mới và không tái sử dụng object cũ; nếu nghiệp vụ cần versioning, hãy thêm version vào state.

Với nhiều field và invariant phức tạp, `synchronized` có thể dễ hiểu, dễ audit và đạt hiệu năng tốt hơn một CAS loop đầy allocation. Không chọn “lock-free” chỉ vì tên gọi.

## 9. AtomicReference trên một reference không tự làm object graph bên dưới atomic

Ví dụ sai:

~~~java
class Pair {
    int a;
    int b;
}

AtomicReference<Pair> ref = new AtomicReference<>(new Pair());
Pair pair = ref.get();
pair.a = 70;
pair.b = 30;
~~~

Đây là mảnh minh họa. `ref.get()` là atomic **đối với reference**, nhưng hai assignment trực tiếp lên fields của cùng `Pair` là thao tác mutable bình thường, không được bảo vệ bởi CAS. Reader khác có thể nhìn thấy `a = 70` và `b` còn cũ.

Để atomic reference publish một transaction logic, phải:

1. Tạo **State mới độc lập** và hoàn chỉnh.
2. Không mutate State đã publish.
3. Commit bằng một phép `set` hợp lệ nếu chỉ có một writer và protocol cho phép, hoặc CAS nếu nhiều writer cần tránh lost update.
4. Reader lấy reference **một lần** cho toàn snapshot.
5. Không cho code ngoài bỏ qua owner và mutate state khác.

Ngay cả `AtomicReference<List<Item>>` cũng có thể chứa một `ArrayList` mutable; chỉ atomic reference không giúp danh sách đó thread-safe. Nếu cần snapshot bất biến, dùng copy có chủ đích và bảo đảm **cả các phần tử** có semantics immutable phù hợp.

## 10. Composite operations và `ConcurrentHashMap`: phạm vi vẫn quan trọng

Nhiều API concurrent có các operation atomic **trong phạm vi một key** hoặc một mapping:

~~~java
ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
counts.merge("paid", 1, Integer::sum);
~~~

Đây là mảnh minh họa, giả định đã import `ConcurrentHashMap`. `merge` áp dụng thao tác cập nhật mapping tương ứng theo hợp đồng map. Nhưng nghiệp vụ cần cập nhật **hai key cùng nhau**:

~~~java
counts.merge("available", -1, Integer::sum);
counts.merge("reserved", 1, Integer::sum);
~~~

vẫn là **hai operation**. Reader có thể lấy một giá trị sau lần merge thứ nhất và giá trị còn lại trước lần merge thứ hai. `ConcurrentHashMap` không hứa transaction toàn map chỉ vì các method đơn lẻ thread-safe.

Ngay cả khi `compute` hoặc `merge` chạy atomic đối với một key, code dùng mapping function để sửa thêm object/key ngoài phạm vi đó có thể tạo race, deadlock hoặc vi phạm hợp đồng API. Đừng làm I/O/blocking lâu trong mapping function.

Nếu invariant thực sự gồm nhiều key, hãy chọn lại **transaction boundary**: một lock cấp owner bảo vệ operation và snapshot, hoặc một immutable aggregate object dưới một key/reference, hoặc transaction của lớp dữ liệu thích hợp.

### 10.1 `LongAdder` và metrics không phải transaction

`LongAdder` có thể giảm contention cho các bộ đếm thống kê nhiều writer bằng cách phân tán cập nhật; `sum()` trong lúc có concurrent updates **không phải atomic snapshot**. Nó hợp với metrics chấp nhận giá trị xấp xỉ ở thời điểm đọc, không phù hợp để giữ invariant tài khoản/quotas đòi độ chính xác tức thời.

Tương tự, nhiều `AtomicInteger` độc lập có thể đúng nếu chúng là **những metrics độc lập** và người đọc không cần tổng nhất quán. Vấn đề chỉ xuất hiện khi nghiệp vụ cam kết các giá trị đó phải cùng thuộc một trạng thái.

## 11. Liveness: nhiều CAS không tự mang lại tiến triển toàn nghiệp vụ

CAS thường là nền tảng của các thuật toán **non-blocking**, nhưng cần phân biệt:

- **Lock-free:** thuật toán theo định nghĩa progress tương ứng bảo đảm toàn hệ thống có tiến triển trong điều kiện mô hình của thuật toán; không bảo đảm một thread cụ thể không starvation.
- **Wait-free:** mọi operation hoàn tất trong một số bước hữu hạn theo mô hình, là bảo đảm mạnh hơn.
- **Obstruction-free:** một thread hoàn tất nếu được chạy đủ lâu mà không có cạnh tranh.

Không thể kết luận một business operation gồm **hai CAS trên hai biến** là “lock-free transaction” chỉ vì từng CAS riêng lẻ lock-free. Nếu CAS thứ nhất thành công rồi thread bị preempt/cancel trước CAS thứ hai, state nghiệp vụ vẫn dở dang.

Một multi-word CAS/transactional memory algorithm đúng thường cần cơ chế **descriptor, giúp hoàn tất (helping), versioning hoặc rollback** với lập luận tinh vi. Java SE `AtomicInteger`/`AtomicReference` mặc định **không cung cấp API transaction đa địa chỉ**. Tự viết thuật toán như vậy chỉ để tránh một lock thường làm tăng rủi ro rất lớn.

CAS loop với nhiều contender còn có thể retry liên tục; cần kiểm tra retry rate, tail latency và khả năng hủy. “Không dùng monitor” không đồng nghĩa “không có vấn đề liveness”.

## 12. Exception, interruption và rollback: atomic từng bước chưa đủ

Giả sử code thực hiện:

~~~java
accountA.addAndGet(-30);
sendNetworkMessage();  // Có thể throw hoặc timeout.
accountB.addAndGet(30);
~~~

Đây là mảnh minh họa, chưa có khai báo `sendNetworkMessage`. Nếu network call ném exception, chương trình có thể dừng sau khi trừ A mà chưa cộng B. Chuyển hai `addAndGet` thành hai CAS không thay đổi failure window này.

### 12.1 Vì sao rollback thủ công cũng có thể sai?

Một ý tưởng là:

~~~java
try {
    accountA.addAndGet(-30);
    accountB.addAndGet(30);
} catch (RuntimeException e) {
    accountA.addAndGet(30);
    throw e;
}
~~~

Mảnh này **không phải transaction an toàn**: giữa các bước, thread khác có thể cập nhật A/B; rollback cộng 30 không nhất thiết trả lại đúng trạng thái nghiệp vụ trước đó. Thậm chí nếu thao tác credit B đã thành công rồi exception phát sinh ở nơi khác, rollback A không có cơ sở để quyết định có phải rollback B hay không.

Một rollback đúng cần xác định **chính xác trạng thái đã commit**, isolation của concurrent operations và cách xử lý lỗi trong lúc rollback. Với external side effect, còn có nguy cơ một lệnh đã được phía xa xử lý nhưng caller chỉ nhìn thấy timeout.

### 12.2 Exception safety với lock

`synchronized` hoặc `ReentrantLock` tự bảo vệ atomic observation **nếu mọi reader/writer cùng protocol**, nhưng không tự rollback dữ liệu khi exception:

~~~java
synchronized (lock) {
    accountA -= amount;
    long nextB = Math.addExact(accountB, amount); // Có thể throw.
    accountB = nextB;
}
~~~

Nếu `addExact` throw sau khi đã trừ, monitor nhả khi exception thoát, để lại trạng thái không hợp lệ. Hãy **tính và validate mọi giá trị có thể throw trước commit**, rồi thực hiện các assignment đơn giản trong critical section:

~~~java
synchronized (lock) {
    if (accountA < amount) {
        return false;
    }
    long nextA = accountA - amount;
    long nextB = Math.addExact(accountB, amount);
    accountA = nextA;
    accountB = nextB;
    return true;
}
~~~

Đây là mảnh method minh họa, giả định `amount > 0` đã được validate, các trường dùng `long` và method trả boolean. Tính đúng của cả hai assignment được bảo vệ bởi lock; reader không thể xen vào giữa hai gán nếu dùng cùng lock.

### 12.3 Interruption không phải transaction rollback

`Thread.interrupt()` là tín hiệu hợp tác. Nó không quay ngược những write đã thực hiện. Một operation được thiết kế tốt nên chỉ kiểm tra interruption/cancellation ở **điểm an toàn**, hoặc chuẩn bị khả năng rollback/compensation phù hợp. Không chia operation tài chính thành nhiều primitive rồi hy vọng interrupt sẽ đưa hệ thống trở lại trạng thái cũ.

## 13. Crash consistency và durability: lock/CAS không ghi được dữ liệu bền vững

Trong một JVM đang chạy, lock hoặc CAS có thể bảo vệ **in-memory state**. Nhưng nếu JVM/process chết sau khi ghi một object mà trước khi ghi object khác, state ngoài RAM hoặc log bên ngoài có thể đã phản ánh một phần operation.

Đừng nhầm:

| Khả năng | AtomicInteger / CAS | Một lock JVM | Transaction dữ liệu bền vững |
| --- | --- | --- | --- |
| Atomic một biến/reference trong RAM | Có, theo operation | Có nếu truy cập dưới protocol | Phụ thuộc storage |
| Atomic nhiều field trong RAM | Không nếu nhiều atomics độc lập; có thể gộp immutable state vào một reference | Có nếu tất cả access cùng lock | Phụ thuộc transaction boundary |
| Isolation giữa nhiều JVM/process | Không | Không | Cần cơ chế cấp storage/distributed |
| Rollback khi exception | Không tự động | Không tự động | Có thể được hệ thống giao dịch hỗ trợ theo hợp đồng |
| Durability sau process crash | Không | Không | Cần cơ chế lưu bền vững |
| Network I/O exactly-once | Không | Không | Cần protocol riêng; không mặc định được bảo đảm |

Một giao dịch database có thể cung cấp atomicity/isolation/durability theo cấu hình và isolation level; không phải mọi database hoặc thao tác ngoài transaction đều cho cùng bảo đảm. Nếu chuyển tiền giữa **hai datastore độc lập** hoặc giữa database và message broker, đây là bài toán transaction nhiều hệ thống, không thể giải chỉ bằng monitor hay atomic class trong ứng dụng.

Các giải pháp ở cấp rộng hơn có thể bao gồm transaction database, optimistic version checks, idempotency keys, durable outbox, saga/compensating actions hay distributed transaction khi hệ thống hỗ trợ. Mỗi cách có trade-off và failure model riêng. Trong bài này chỉ cần nhớ **phạm vi lock/CAS không vượt khỏi những object mà chính giao thức của chúng kiểm soát trong một JVM**.

## 14. Atomicity, isolation, visibility và linearizability

Các từ này liên quan nhưng không đồng nghĩa:

| Thuật ngữ | Ý nghĩa thực tế | Ví dụ |
| --- | --- | --- |
| **Atomicity** | Một operation được quan sát như một thay đổi không bị chia đôi trong phạm vi đã định. | Transfer cập nhật hai số dư dưới một lock. |
| **Isolation** | Các operation đồng thời không nhìn thấy những trạng thái trung gian không hợp lệ theo contract. | Reader cặp account lấy cùng hai lock. |
| **Visibility / happens-before** | Hiệu ứng ghi được công bố và quan sát theo quy tắc JMM. | Unlock → lock sau đó trên cùng monitor. |
| **Linearizability** | Mỗi operation có thể coi như có hiệu lực tại một điểm giữa invocation và response, giữ thứ tự real-time của operations không chồng lấn. | CAS thành công thay `AtomicReference<State>`. |
| **Durability** | Trạng thái đã commit sống sót qua một số loại lỗi/crash theo hợp đồng lưu trữ. | Ghi transaction vào hệ thống lưu bền vững. |

`AtomicLong.addAndGet` có một điểm tuyến tính hóa cho **một biến**. Hai operation addAndGet liên tiếp có **hai điểm**; không có điểm tuyến tính hóa chung của transfer nếu không thêm một giao thức khác.

Một synchronized method làm transfer nhưng reader của account B bỏ qua lock cũng không tạo isolation mong muốn: contract chỉ đúng với những participant tuân thủ lock. Khi bàn về “transaction trong RAM”, luôn chỉ rõ những operation nào nằm trong phạm vi bảo đảm.

## 15. Khi nào các atomics độc lập vẫn là lựa chọn đúng?

Không nên rút ra kết luận rằng mọi lần xuất hiện nhiều AtomicInteger đều sai.

Chúng phù hợp khi **những state thực sự độc lập** hoặc hợp đồng cho phép snapshot gần đúng. Ví dụ:

- Bộ đếm số request thành công và thất bại phục vụ dashboard, chấp nhận số tổng đọc trong lúc cập nhật hơi lệch.
- Hai feature flags độc lập, được cập nhật riêng và không yêu cầu chuyển trạng thái nguyên tử cùng nhau.
- Nhiều counters phục vụ throughput monitoring, không dùng làm hạn mức hoặc accounting chính xác.
- Cấu trúc dữ liệu concurrent có mỗi node/key độc lập theo invariant đã thiết kế, không yêu cầu một snapshot toàn cục.

Ngược lại, nếu một điều kiện kiểu `a + b == capacity`, `paid ⇒ paymentId != null` hoặc `available >= 0` phải đúng tại **mọi thời điểm được observer nhìn thấy**, cần cập nhật/đọc với transaction boundary chung.

**Tính đúng đắn phụ thuộc hợp đồng quan sát.** Một dashboard chấp nhận eventual consistency khác hẳn một hệ thống cấp quota không được vượt hạn mức.

## 16. Performance và chi phí của giải pháp

| Kỹ thuật | Ưu điểm | Chi phí/giới hạn | Khi cân nhắc |
| --- | --- | --- | --- |
| Một `synchronized` lock cho aggregate | Đơn giản, dễ chứng minh correctness, bảo vệ nhiều field. | Contention có thể tuần tự hóa các operation không liên quan nếu lock quá rộng. | Default tốt cho invariant nội bộ một owner. |
| `ReentrantLock` | Timeout, interruptible acquisition, Condition; giữ invariant qua critical section. | Phải unlock trong finally, dễ thêm nhánh lỗi. | Cần policy lấy lock/condition. |
| Khóa hai object theo ID | Cho phép cặp object không giao nhau chạy song song. | Deadlock nếu lock order sai; snapshot toàn cục khó hơn. | Nhiều account/object độc lập nhưng có transfer giữa chúng. |
| `AtomicReference<ImmutableState>` CAS | Một điểm commit cho aggregate, read snapshot đơn giản. | Allocation, retry, starvation, logic candidate phải thuần. | State gọn, immutable, multi-writer optimistic update. |
| Hai `AtomicLong` độc lập | Rẻ và dễ dùng cho hai biến không có invariant chung. | Không bảo vệ transaction đa biến. | Metrics độc lập hoặc consistency gần đúng được cho phép. |
| Giao dịch storage/database | Có thể bảo vệ dữ liệu bền vững và nhiều record theo hợp đồng. | I/O, transaction contention, isolation/configuration complexity. | Invariant thuộc storage hoặc nhiều process. |

Không có công thức “CAS nhanh hơn synchronized”. Nếu state chỉ gồm vài trường và updates nhiều, một lock ngắn có thể hiệu quả và dễ bảo trì hơn một vòng CAS liên tục tạo object mới. Nếu nhiều reader ít writer, immutable snapshot có thể hấp dẫn. Hãy đo **throughput, p95/p99 latency, lock blocked time, CAS retry rate, GC allocation** và độ đúng của invariant dưới workload tương ứng.

## 17. Cách kiểm thử transaction nhiều object

Một test chỉ so sánh tổng số dư **sau khi mọi thread đã join** có thể bỏ sót khoảng hở giữa debit và credit. Ví dụ demo đầu có tổng cuối đúng 100, nhưng một reader giữa chừng thấy 70.

Các bước kiểm thử:

1. **Định nghĩa invariant chính xác**: tổng bảo toàn, số dư không âm, mỗi transfer thành công làm thay đổi đúng hai balance.
2. **Định nghĩa snapshot contract**: reader có quyền nhìn trạng thái khi transfer đang chạy không? Nếu có, phải thấy snapshot trước hoặc sau một transfer hoàn chỉnh.
3. **Dùng latch/barrier tạo interleaving**: cố ý dừng writer giữa hai bước để kiểm tra reader không thấy dở dang. Đối với thiết kế đã sửa, reader phải dùng API snapshot chuẩn.
4. **Stress test nhiều writer**: chuyển tiền qua lại với nhiều thread, cùng kiểm tra số dư và tổng.
5. **Chèn lỗi ở điểm có thể throw**: overflow, validation, callback, interruption hoặc network timeout; xác nhận operation thất bại vẫn giữ invariant.
6. **Kiểm tra mọi đường cập nhật**: setters, admin operations, reset, scheduled jobs, background tasks. Chỉ một đường bypass lock có thể phá toàn bộ protocol.
7. **Đo hiệu năng sau khi correctness đạt**: tránh đánh đổi dữ liệu đúng để đổi một con số benchmark hấp dẫn.
8. **Nhận biết giới hạn test**: chạy nhiều lần thấy đúng không chứng minh mọi interleaving đều an toàn. Code review trên invariant, lock ownership và HB vẫn là bắt buộc.

### 17.1 Test lỗi bằng countdown giữa hai bước

`AtomicTransferGapDemo` là một test đối chứng: với thiết kế hai atomic độc lập, reader chủ động được đưa vào giữa các update và quan sát total=70. Khi sửa bằng `Ledger.transfer` hoặc `AtomicReference<State>`, không còn API để nhìn một nửa update: thread đọc `snapshot()` theo protocol sẽ thấy một snapshot hợp lệ.

Nếu cần test một operation đang giữ lock, tạo latch để biết worker đã vào critical section rồi cho reader gọi `snapshot()`; reader sẽ chờ cho đến khi operation nhả lock. **Không đặt một latch khiến worker chờ tín hiệu từ reader trong khi reader lại đang chờ chính lock worker giữ**, vì đó sẽ là deadlock do test tạo ra.

### 17.2 Test overflow

Thử các biên gần `Long.MAX_VALUE` hoặc `Long.MIN_VALUE` và xác nhận phép tính checked xảy ra **trước commit**. Java `long` không an toàn khỏi overflow chỉ vì nó 64-bit; với tiền tệ cần kiểm tra đơn vị, miền giá trị và số học phù hợp. Một transaction đồng bộ hoàn hảo vẫn có thể sai nghiệp vụ nếu overflow.

## 18. Những ngộ nhận thường gặp

**“Từng phép cộng là atomic thì chuỗi hai phép cộng cũng atomic.”** Sai. Hai operation có hai điểm commit và có thể bị observer chen vào giữa.

**“CAS của A thành công nghĩa là transfer A→B đã hoàn thành.”** Sai. Nó chỉ chứng minh một thay đổi trên A đã thành công.

**“Đọc lại hai lần và so sánh có thể thay transaction.”** Không tổng quát. Có thể gặp ABA hoặc các writer khác không được quan sát; cần protocol snapshot/versioning có chứng minh.

**“Đưa hai object vào AtomicReference là đủ.”** Chỉ khi object graph được publish đúng, thực sự bất biến và mọi mutation đi qua một reference/protocol. Thay reference không tự ngăn thay fields của object cũ.

**“Hai getter synchronized riêng là một snapshot.”** Sai. Writer có thể chạy giữa hai lần getter. Cần một operation snapshot cùng lock hoặc một immutable aggregate.

**“Đã có lock nên exception tự rollback.”** Sai. Monitor được nhả khi exception thoát, nhưng các phép gán đã chạy vẫn giữ nguyên. Cần tính/validate trước commit hoặc giao thức rollback.

**“Bỏ lock và dùng nhiều CAS sẽ tự thành lock-free transaction.”** Sai. Progress guarantee của một CAS không ghép thành transaction đa địa chỉ; thuật toán đúng thường phức tạp hơn nhiều.

**“AtomicReference thay được database transaction.”** Không. Nó không tạo durability, isolation giữa process hay rollback các external side effect.

**“Thread.join giúp sửa phép chuyển tiền bị dở dang.”** Join giúp caller quan sát worker đã kết thúc và memory effects của nó; không hoàn tất bước credit bị bỏ qua hoặc sửa invariant.

**“Không thấy tổng sai trong stress test nghĩa là đã chứng minh correctness.”** Không. Interleaving lỗi có thể rất hiếm. Dùng protocol reasoning và test có điều phối bằng latch.

## 19. Quy trình chọn giải pháp theo phạm vi

| Bài toán | Điều kiện | Cách tiếp cận |
| --- | --- | --- |
| Một balance cần cộng/trừ chính xác | Không liên quan invariant ngoài balance | Một `AtomicLong` hoặc lock, kiểm tra amount/overflow. |
| Hai field trong cùng object luôn phải cùng trạng thái | Mọi reader/writer do một owner kiểm soát | `synchronized`/`ReentrantLock` bao trùm cả check, update và snapshot. |
| Nhiều field thuộc một snapshot immutable | Có thể gói thành một object không đổi sau publish | `AtomicReference<State>` CAS, mọi writer qua một reference. |
| Hai Account độc lập cần chuyển tiền | Có ID bất biến, lock ownership rõ | Lấy cả hai lock theo thứ tự toàn cục; đọc snapshot cặp theo cùng protocol. |
| Nhiều keys trong ConcurrentHashMap có invariant chung | Atomic per-key không đủ | Thiết kế owner/transaction boundary chung, không dùng hai `merge` độc lập. |
| Nhiều record cùng một database | Cần isolation/durability của storage | Transaction của database theo isolation thích hợp. |
| Database và dịch vụ ngoài cùng tham gia | Có network/crash/duplicate delivery | Thiết kế idempotency, transaction/outbox/compensation theo failure model. |
| Metrics nhiều counter độc lập | Có thể chấp nhận snapshot gần đúng | Atomic/LongAdder riêng cho mỗi metric. |

**Quy tắc thực hành:** chọn **đơn vị sở hữu invariant** trước, rồi đảm bảo cả cập nhật và quan sát cùng đi qua đơn vị đó. Nếu không thể xác định được ai sở hữu toàn bộ invariant, bài toán cần đổi mô hình dữ liệu/transaction boundary trước khi chọn API Atomic hay lock.

## 20. Câu hỏi tự kiểm tra

**1.** Hai `AtomicLong`, mỗi lần `addAndGet` atomic. Chuyển 30 từ A sang B có atomic không?  
**Không.** Có khoảng giữa lần trừ A và lần cộng B; một observer có thể thấy tổng sai.

**2.** CAS giúp A không bị rút quá số dư. Vì sao vẫn chưa đủ cho transfer?  
**Vì** điều kiện và cập nhật của B không nằm trong cùng atomic operation. CAS của A không commit B.

**3.** Hai balance nằm trong cùng một Ledger. Giải pháp dễ chứng minh nhất?  
**Một private lock** bảo vệ cả chuyển tiền và snapshot; tính/validate trước khi commit.

**4.** Nếu mỗi Account có lock riêng, tránh deadlock khi chuyển A↔B bằng cách nào?  
**Lấy cả hai lock theo thứ tự toàn cục ổn định**, ví dụ ID duy nhất; mọi call path cùng protocol.

**5.** Vì sao một `AtomicReference<State>` có thể đủ khi hai balance thành fields của immutable State?  
**Vì** một lần CAS thay toàn bộ reference đến snapshot; reader lấy một reference duy nhất, không đọc hai object mutable riêng.

**6.** Nếu `State` có field `List` mutable và caller sửa list sau publish thì snapshot còn bất biến không?  
**Không.** Record/final reference không tự tạo bất biến sâu của object graph.

**7.** Có thể gọi remote API trong lambda của `getAndUpdate` rồi hy vọng chỉ chạy một lần không?  
**Không.** Hàm có thể bị gọi nhiều lần khi tranh chấp; side effect cần được tách hoặc làm idempotent theo thiết kế.

**8.** `synchronized` có tự rollback khi `Math.addExact` ném exception giữa hai gán không?  
**Không.** JVM nhả monitor, nhưng các gán đã thực hiện vẫn tồn tại; phải tính trước commit.

**9.** `ConcurrentHashMap.merge` hai key liên tiếp có tạo transaction toàn map không?  
**Không.** Mỗi merge có phạm vi riêng, không tự bảo vệ invariant của hai key.

**10.** Vì sao CAS/lock trong RAM không thay transaction database?  
**Vì** chúng không bảo đảm durability hoặc isolation giữa các process và external resources.

**11.** Nếu total cuối luôn đúng sau join, liệu từng observer giữa transfer cũng thấy total đúng?  
**Chưa chắc.** Cần test và chứng minh snapshot contract trong lúc operation đang chạy.

**12.** Khi nào nhiều atomics độc lập là lựa chọn hợp lý?  
**Khi** các giá trị độc lập theo nghiệp vụ hoặc contract chấp nhận snapshot gần đúng, không đòi transaction liên biến.

**Ghi nhớ:** Atomic/CAS là cơ chế mạnh để bảo vệ **một biến hoặc một reference**. Nó chỉ bảo vệ giao dịch nhiều field khi bạn **gộp toàn bộ invariant thành một đơn vị cập nhật nguyên tử** và buộc mọi writer/reader tuân thủ protocol đó. Nếu nhiều object độc lập phải cập nhật nhất quán, cần một transaction boundary rõ ràng: khóa chung, thứ tự đa khóa, immutable aggregate CAS, hoặc cơ chế giao dịch ở lớp dữ liệu tương ứng. **Từng bước atomic không có nghĩa toàn bộ quy trình atomic.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-012">Chọn atomic, lock hay thread confinement</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn atomic, lock hay thread confinement trong Java Concurrency

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

- Phân biệt **atomic variables**, **khóa (lock)** và **thread confinement** theo mục tiêu bảo vệ dữ liệu, không chỉ theo cú pháp Java.
- Xác định **bất biến (invariant)** cần giữ, phạm vi cập nhật nguyên tử, số lượng thread có quyền sở hữu và quan hệ *happens-before* trước khi chọn cơ chế.
- Dùng **AtomicInteger**, **AtomicLong**, **AtomicReference** và **compareAndSet (CAS)** cho phép cập nhật độc lập hoặc chuyển đổi một trạng thái được đóng gói bất biến.
- Dùng **synchronized** hay **ReentrantLock** khi một quyết định cần bảo vệ nhiều phép đọc/ghi trong cùng critical section; hiểu lý do phải đọc và ghi theo cùng một giao thức khóa.
- Tổ chức **thread confinement**, **thread-local state** hoặc một worker sở hữu trạng thái để giảm nhu cầu chia sẻ mutable state; nhận biết giới hạn của **ThreadLocal**, thread pool, chuyển giao quyền sở hữu và callback.
- Tránh những suy luận sai: “atomic nghĩa là cả nghiệp vụ nguyên tử”, “volatile thay thế khóa”, “ThreadLocal tự động làm mọi thứ thread-safe”, hoặc “không có data race thì chắc chắn đúng”.
- Viết, chạy và kiểm thử Java 17+ theo đặc tả kết quả thay vì dựa vào sleep hoặc thứ tự log không xác định.

Bài học thuộc **Java concurrency nói chung**, trong nhóm **Atomic & CAS**. Các cơ chế chuyên biệt của Spring có chương riêng, không nằm trong phạm vi bài này.

## 2. Ba lựa chọn xử lý cùng một vấn đề

Giả sử nhiều thread cần xử lý yêu cầu đặt vé. Một cách thiết kế dùng biến nguyên tử cho số vé còn lại; cách khác giữ khóa khi kiểm tra số vé, ghi đơn và cập nhật tổng; cách thứ ba **chỉ một worker được quyền thay đổi** trạng thái đặt vé và các thread khác gửi yêu cầu đến worker đó.

Cả ba đều có thể hợp lệ nếu **đặc tả và giao thức sở hữu dữ liệu** phù hợp. Không có một lựa chọn “nhanh nhất” hoặc “đúng nhất” cho mọi bài toán.

| Lựa chọn | Ý tưởng trung tâm | Dùng tốt khi | Cần cảnh giác |
| --- | --- | --- | --- |
| **Atomic / CAS** | Một thao tác nguyên tử lên một biến hoặc một reference đại diện trạng thái | Bộ đếm, cờ, CAS chuyển trạng thái, snapshot bất biến | Chuỗi nhiều thao tác atomic riêng vẫn có logic race |
| **Lock** | Chỉ một thread mỗi lần thực hiện critical section bảo vệ bất biến | Nhiều trường liên quan, check–then–act, thao tác cần đọc rồi sửa | Deadlock, giữ khóa lâu, chọn sai lock, bỏ sót nhánh unlock |
| **Thread confinement** | Không cho nhiều thread đồng thời chạm vào mutable state | Bộ xử lý tuần tự, dữ liệu tạm riêng thread, ownership rõ | Reference bị rò, nhiều worker cùng truy cập, thread pool và chuyển thread |

**Lời giải bắt đầu từ câu hỏi “cái gì bắt buộc phải đúng?”**, không bắt đầu từ việc chọn AtomicInteger hay synchronized vì quen thuộc.

## 3. Câu hỏi thiết kế trước khi chọn cơ chế

Hãy trả lời theo thứ tự:

1. **Mutable state nằm ở đâu?** Là một số nguyên, một cặp số dư, một hàng đợi hay cả object graph?
2. **Thread nào được đọc hoặc ghi?** Một thread duy nhất, vài worker trong cùng JVM, hay nhiều JVM/process?
3. **Bất biến là gì?** Ví dụ tổng tiền được bảo toàn, stock không âm, một job chỉ được nhận một lần.
4. **Quyết định cần nguyên tử gồm mấy bước?** Chỉ increment; hay đọc stock, kiểm tra, trừ rồi ghi kết quả?
5. **Có chuyển giao ownership không?** Task được gửi qua queue, callback chạy trên thread nào, có trả về mutable object không?
6. **Cần blocking hay tránh blocking?** Hệ thống có thể chấp nhận contention, CAS retry, hoặc phải có đường timeout/hủy?
7. **Có external side effect không?** Ghi DB, file, gửi email hoặc gọi dịch vụ không trở thành transaction chỉ nhờ Java atomic/lock.
8. **Cần đo đạc gì?** Throughput, contention, fairness, latency đuôi, bộ nhớ và độ phức tạp bảo trì.

Nếu chỉ **một giá trị độc lập** cần cập nhật nguyên tử, atomic thường đơn giản. Nếu **nhiều trường phải đổi cùng nhau**, lock hoặc một snapshot bất biến được thay bằng CAS có thể phù hợp. Nếu có thể **không chia sẻ trạng thái để tránh đồng bộ hóa**, thread confinement thường giảm độ phức tạp. Đó là điểm khởi đầu, không phải quy tắc cứng.

## 4. Nền tảng: atomicity, visibility, ordering và happens-before

Các vấn đề concurrency thường trộn lẫn ba thuộc tính:

- **Atomicity:** thao tác cần thiết có diễn ra như một đơn vị không bị xen giữa hay không?
- **Visibility:** thread khác có nhìn thấy các ghi đã thực hiện không?
- **Ordering:** những thao tác nào bắt buộc xảy ra trước thao tác khác?

**Java Memory Model (JMM)** dùng quan hệ **happens-before** để quy định khả năng quan sát và thứ tự của các hành động.

Một vài quan hệ quan trọng:

- Các hành động trước **Thread.start()** happens-before các hành động của thread mới.
- Các hành động trong thread hoàn tất happens-before hành động của thread khác sau khi **join()** kết thúc do thread đích đã hoàn thành.
- **Unlock** một monitor happens-before lần **lock tiếp theo** trên monitor đó.
- Việc ghi một biến **volatile** happens-before các lần đọc volatile tương ứng về sau theo synchronization order.
- Các lớp atomic sử dụng ngữ nghĩa bộ nhớ theo hợp đồng API, và những thao tác như **compareAndSet** còn thực hiện kiểm tra–đổi giá trị một cách nguyên tử.

**Thread.sleep()**, **Thread.yield()** và việc log xuất hiện theo thứ tự “có vẻ đúng” không thay thế happens-before.

### Volatile không biến một chuỗi bước thành atomic

~~~java
// Minh họa lỗi trong lớp có field chia sẻ:
volatile int count;

// Hai thread cùng gọi:
count++; // Vẫn gồm đọc, cộng và ghi: không phải increment nguyên tử.
~~~

Volatile phù hợp với một số **cờ trạng thái** hoặc **công bố reference**, nhưng **count++** vẫn cần AtomicInteger, synchronized hay giải pháp sở hữu riêng nếu phải giữ số đếm chính xác.

### Không có data race vẫn có thể sai logic

~~~java
// Minh họa logic race: stock là AtomicInteger đã được tạo.
if (stock.get() > 0) {
    stock.decrementAndGet();
}
~~~

Từng lời gọi AtomicInteger là thread-safe, nhưng hai thread có thể cùng thấy stock bằng 1 rồi đều trừ, khiến stock thành -1. **Bất biến “stock không âm” bị phá** vì check–then–act không nguyên tử.

## 5. Atomic: khi một biến là đơn vị quyết định phù hợp

Các lớp trong **java.util.concurrent.atomic** cung cấp các phép đọc, ghi, tăng, cộng, CAS và cập nhật hàm trên một đơn vị trạng thái.

Ví dụ:

| API | Nhu cầu thường gặp | Giới hạn cần nhớ |
| --- | --- | --- |
| **AtomicInteger / AtomicLong** | Bộ đếm, sequence, trạng thái số | Không làm nhiều biến khác nhau thành một transaction |
| **AtomicBoolean** | Cờ chuyển trạng thái một lần | Boolean không mô tả nhiều giai đoạn nghiệp vụ phức tạp |
| **AtomicReference<T>** | Công bố hoặc CAS reference đến một snapshot | Đối tượng T cần được thiết kế bất biến hoặc đồng bộ khi sửa |
| **AtomicIntegerArray** và các atomic array | Cập nhật phần tử có bảo đảm atomic tương ứng | Nhiều phần tử không tự được cập nhật cùng lúc |
| **LongAdder** | Thống kê tăng cộng dưới contention cao | **sum() không phải atomic snapshot** khi đang cập nhật |

**CAS** là phép so sánh giá trị hiện tại với giá trị kỳ vọng và chỉ ghi giá trị mới nếu điều kiện so sánh thành công. CAS có thể thất bại vì thread khác vừa cập nhật; chương trình thường cần thử lại. Không coi CAS retry là miễn phí hoặc bảo đảm mọi thread được phục vụ công bằng.

### Ví dụ 1: AtomicInteger cộng chính xác

Tệp **AtomicCounterChoiceDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterChoiceDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();

        Runnable increment = () -> {
            for (int i = 0; i < 10_000; i++) {
                counter.incrementAndGet();
            }
        };

        Thread first = new Thread(increment, "first");
        Thread second = new Thread(increment, "second");

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("count = " + counter.get());
    }
}
~~~

~~~bash
javac -encoding UTF-8 AtomicCounterChoiceDemo.java
java AtomicCounterChoiceDemo
~~~

Stdout:

~~~text
count = 20000
~~~

**Vì sao chọn atomic?** Mỗi yêu cầu chỉ cần tăng một counter dùng chung, không có bất biến trải trên nhiều trường. **incrementAndGet()** bảo đảm tăng nguyên tử; **join()** chỉ làm main chờ đủ hai worker trước khi in, chứ không phải cơ chế sửa lost update.

Nếu counter chỉ phục vụ thống kê nhiều luồng, không cần đọc ngay một tổng chính xác trong lúc cập nhật, có thể cân nhắc LongAdder; nhưng không thay AtomicLong bằng LongAdder khi API đòi một phép **getAndIncrement**, sequence tuyến tính hoặc snapshot ngay lập tức.

## 6. Atomic CAS: kiểm tra điều kiện và cập nhật trong cùng phép

Giả sử có đúng **1 vé** và hai thread cùng đặt vé. Bất biến: không chấp nhận hơn 1 yêu cầu và số vé còn lại không âm.

Nếu chỉ dùng **get()** rồi **decrementAndGet()**, hai thread có thể cùng vượt điều kiện. CAS giải quyết bằng cách **chỉ trừ nếu giá trị chưa đổi**.

Tệp **AtomicReservationDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicReservationDemo {
    static boolean reserve(AtomicInteger stock) {
        while (true) {
            int seen = stock.get();
            if (seen <= 0) {
                return false;
            }

            if (stock.compareAndSet(seen, seen - 1)) {
                return true;
            }
            // Có thread đã đổi stock: đọc lại và thử lại.
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicInteger stock = new AtomicInteger(1);
        AtomicInteger accepted = new AtomicInteger();

        Runnable buyer = () -> {
            if (reserve(stock)) {
                accepted.incrementAndGet();
            }
        };

        Thread first = new Thread(buyer, "buyer-1");
        Thread second = new Thread(buyer, "buyer-2");
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("accepted = " + accepted.get());
        System.out.println("remaining = " + stock.get());
    }
}
~~~

~~~bash
javac -encoding UTF-8 AtomicReservationDemo.java
java AtomicReservationDemo
~~~

Stdout:

~~~text
accepted = 1
remaining = 0
~~~

CAS đảm bảo chỉ một chuyển đổi **1 → 0** thành công. Thread còn lại nhận trạng thái 0 và từ chối. Không cần dựa vào thread nào chạy trước.

**Giới hạn quan trọng:** Ví dụ chỉ quản lý số vé trong bộ nhớ của **một JVM**. Nếu sau khi CAS thành công còn gọi thanh toán, ghi đơn hàng hoặc phát thông báo, các side effect đó **không nằm trong một transaction cùng CAS**. Hãy phân biệt **giữ chỗ đã được cấp quyền** với **giao dịch nghiệp vụ hoàn tất** và thiết kế retry, rollback/compensation, idempotency tương ứng.

### Vì sao CAS loop có thể khó bảo trì?

Nếu logic chuyển đổi liên quan nhiều trường mutable, nested collection, điều kiện phức tạp hoặc thao tác cần I/O, việc xây state mới rồi CAS có thể tốn allocation, phải lặp lại nhiều lần và dễ sinh side effect trùng nếu đặt hành động bên ngoài vào trong vòng retry.

Một nguyên tắc: **hàm tính trạng thái mới trong CAS loop nên thuần (không có side effect không thể lặp)**. Việc có một CAS thất bại không có nghĩa hàm đã được gọi đúng một lần.

## 7. Nhiều trường vẫn có thể dùng CAS nếu đóng gói vào một snapshot bất biến

Không nên hiểu “atomic chỉ dùng cho một field” theo nghĩa tuyệt đối. Có thể đóng gói **cả bộ bất biến** vào một **record immutable** và thay **một reference** bằng CAS. Nhờ đó mọi reader nhận **snapshot nhất quán**.

Ví dụ hai trường **available** và **reserved** có bất biến:

- available không âm.
- reserved không âm.
- available + reserved luôn bằng tổng ban đầu.

Tệp **AtomicSnapshotDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicReference;

public class AtomicSnapshotDemo {
    record Inventory(int available, int reserved) {}

    static boolean reserveOne(AtomicReference<Inventory> ref) {
        while (true) {
            Inventory old = ref.get();

            if (old.available() <= 0) {
                return false;
            }

            Inventory next = new Inventory(
                    old.available() - 1,
                    old.reserved() + 1
            );

            if (ref.compareAndSet(old, next)) {
                return true;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<Inventory> ref =
                new AtomicReference<>(new Inventory(100, 0));

        Runnable task = () -> {
            for (int i = 0; i < 50; i++) {
                reserveOne(ref);
            }
        };

        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(task, "worker-" + i);
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        Inventory end = ref.get();
        System.out.println("available = " + end.available());
        System.out.println("reserved = " + end.reserved());
        System.out.println(
                "total = " + (end.available() + end.reserved())
        );
    }
}
~~~

~~~bash
javac -encoding UTF-8 AtomicSnapshotDemo.java
java AtomicSnapshotDemo
~~~

Stdout:

~~~text
available = 0
reserved = 100
total = 100
~~~

**Vì sao đúng?** Mỗi chuyển đổi CAS thay **một reference** tới đối tượng Inventory đã hoàn tất xây dựng. Reader quan sát một phiên bản đầy đủ, không thấy trạng thái trung gian available đã giảm nhưng reserved chưa tăng. Bốn worker có tổng 200 lần thử giữ chỗ, trong khi chỉ có 100 vé; các lần thử còn lại từ chối.

**Điều kiện bắt buộc:**

- Inventory là **immutable** trong ví dụ: record chỉ chứa hai giá trị int, không có mutable collection lộ ra ngoài.
- Tất cả thay đổi có ý nghĩa với bất biến đều đi qua **cùng AtomicReference**.
- Không có thao tác bất hồi phục trong CAS loop.
- Người đọc cần snapshot nhất quán thì chỉ đọc **một lần ref.get()**, rồi dùng các field của cùng snapshot; đừng đọc reference hai lần để lấy hai field rồi coi đó là một phiên bản.

Nếu state rất lớn, allocation/GC hoặc CAS retry dưới contention có thể tốn chi phí. Với logic nhiều bước, khóa có thể dễ hiểu hơn.

### ABA và danh tính reference

CAS có thể gặp **ABA**: giá trị chuyển A→B→A, nên một thread thấy “vẫn là A” dù trạng thái đã qua một lần thay đổi. Điều này quan trọng khi semantics phụ thuộc lịch sử hoặc version, nhất là với cấu trúc lock-free.

Nếu cần nhận biết mọi lần thay đổi, có thể gắn **version** trong state immutable hoặc xét **AtomicStampedReference**. Với CAS trên reference đến các record immutable **mới được tạo mỗi lần** như ví dụ, từng lần thay thế dùng object mới nên không tự quay lại cùng reference cũ, nhưng không vì vậy mà mọi hệ thống dùng AtomicReference đều miễn nhiễm ABA.

## 8. Lock: bảo vệ một critical section nhiều bước

Khi quyết định gồm nhiều trường phải được cập nhật cùng nhau, **synchronized** hoặc **ReentrantLock** thường trực quan hơn CAS state phức tạp. Một lock bảo vệ **bất biến**, chứ không phải đơn thuần một biến.

Ví dụ kho có hai trường **available** và **reserved**. Nếu dùng hai AtomicInteger độc lập:

~~~java
// Mã minh họa lỗi nếu muốn snapshot nhất quán:
available.decrementAndGet();
reserved.incrementAndGet();
~~~

Mỗi phép cập nhật riêng lẻ đều atomic nhưng người đọc có thể nhìn thấy trạng thái **đã giảm available nhưng chưa tăng reserved**. Một ReentrantLock dùng chung cho các phương thức đọc/ghi sẽ tránh trường hợp đó.

### Mẫu ReentrantLock đúng

~~~java
// Mã minh họa trong một lớp có ReentrantLock lock và shared state.
lock.lock();
try {
    // Đọc điều kiện + cập nhật toàn bộ bất biến.
} finally {
    lock.unlock();
}
~~~

**finally** rất quan trọng: nếu code ném exception hoặc return sớm, thread phải nhả lock. Ngược lại, **tryLock()** trả false hay **lockInterruptibly()** ném InterruptedException **trước khi lấy được khóa** thì không được unlock khóa mình chưa sở hữu.

### Ví dụ 4: khóa bảo vệ stock và số lượng đã đặt

Tệp **LockedInventoryDemo.java**:

~~~java
import java.util.concurrent.locks.ReentrantLock;

public class LockedInventoryDemo {
    static final class Inventory {
        private final ReentrantLock lock = new ReentrantLock();
        private int available = 100;
        private int reserved;

        boolean reserveOne() {
            lock.lock();
            try {
                if (available == 0) {
                    return false;
                }

                available--;
                reserved++;
                return true;
            } finally {
                lock.unlock();
            }
        }

        int[] snapshot() {
            lock.lock();
            try {
                return new int[] {available, reserved};
            } finally {
                lock.unlock();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Inventory inventory = new Inventory();

        Runnable task = () -> {
            for (int i = 0; i < 50; i++) {
                inventory.reserveOne();
            }
        };

        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(task, "worker-" + i);
            workers[i].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        int[] end = inventory.snapshot();
        System.out.println("available = " + end[0]);
        System.out.println("reserved = " + end[1]);
        System.out.println("total = " + (end[0] + end[1]));
    }
}
~~~

~~~bash
javac -encoding UTF-8 LockedInventoryDemo.java
java LockedInventoryDemo
~~~

Stdout:

~~~text
available = 0
reserved = 100
total = 100
~~~

**Điểm khác AtomicSnapshotDemo:** Inventory giữ hai trường **mutable** riêng biệt, nhưng mọi lần đọc/ghi liên quan đến bất biến đều đi qua **cùng ReentrantLock**. Trong critical section, check rồi giảm available và tăng reserved được thực hiện như một đơn vị đối với các thread tuân thủ giao thức.

**Lock phù hợp khi:**

- Cần bảo vệ nhiều field hoặc một collection có thao tác phức tạp.
- Có nhiều điều kiện phải kiểm tra trước khi commit.
- Logic cần sửa cùng lúc nhiều vùng nhớ mà CAS trên snapshot trở nên rườm rà.
- Cần đường chờ thông qua **Condition** (với ReentrantLock) hoặc monitor wait/notify.
- Có yêu cầu điều phối cụ thể như tryLock, chờ có timeout hoặc lock acquisition có thể ngắt.

**Lock không tự tạo transaction cho DB**, cũng không bảo vệ các instance ở JVM khác. Không giữ khóa qua cuộc gọi mạng hoặc I/O dài khi có thể tách các bước an toàn, vì tăng contention và nguy cơ deadlock.

### synchronized hay ReentrantLock?

| Tiêu chí | synchronized | ReentrantLock |
| --- | --- | --- |
| Nhả khóa khi thoát scope | Tự động nhả monitor | Phải đặt unlock trong finally |
| Reentrant | Có | Có |
| Memory visibility | Monitor unlock/lock có happens-before | Lock/unlock có ngữ nghĩa tương ứng |
| Thử lấy khóa không chờ | Không có cú pháp try-enter | tryLock() |
| Chờ lấy khóa có timeout | Không trực tiếp | tryLock(timeout, unit) |
| Ngắt khi đang chờ acquisition | Không ném InterruptedException khi vào monitor | lockInterruptibly() |
| Chờ condition | Object.wait/notify | Condition.await/signal |
| Fairness cấu hình | Không | Có tùy chọn, nhưng không bảo đảm công bằng tuyệt đối |

**Ưu tiên lựa chọn đơn giản nhất đáp ứng yêu cầu**. Không khẳng định ReentrantLock luôn nhanh hơn synchronized; hiệu quả phụ thuộc JIT, contention và thời gian giữ khóa.

## 9. Thread confinement: giảm chia sẻ thay vì khóa nhiều hơn

**Thread confinement** là cách tổ chức ownership để một mutable state **chỉ được truy cập bởi một thread** trong giai đoạn sử dụng. Nếu không có truy cập đồng thời từ thread khác, bản thân state đó có thể không cần atomic hoặc lock.

Các kiểu confinement:

1. **Stack/local confinement:** biến cục bộ và object được tạo trong phương thức, không để reference thoát sang thread khác. Không phải cứ “biến local” là an toàn nếu nó trỏ đến **đối tượng chia sẻ**.
2. **Thread-local confinement:** mỗi thread có bản sao dữ liệu riêng, có thể dùng **ThreadLocal<T>**. Không mặc nhiên giúp tổng hợp kết quả trên nhiều thread.
3. **Ownership confinement:** một worker/actor/event loop được giao **quyền duy nhất** cập nhật state; những thread khác gửi message/task và nhận kết quả qua giao thức đồng bộ.

Điểm cốt lõi là **quyền truy cập**, không phải chỉ đặt một biến vào ThreadLocal. Nếu reference đến object mutable bị gửi ra ngoài, quyền sở hữu có thể bị phá.

### Ví dụ 5: một worker sở hữu counter, không cần khóa trong counter

Tệp **ConfinedCounterDemo.java**:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ConfinedCounterDemo {
    static final class Counter {
        private int value;

        void increment() {
            value++; // An toàn nếu chỉ owner thread sửa.
        }

        int snapshot() {
            return value;
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService owner = Executors.newSingleThreadExecutor();
        Counter counter = new Counter();

        try {
            for (int i = 0; i < 1_000; i++) {
                owner.submit(counter::increment);
            }

            Future<Integer> result = owner.submit(counter::snapshot);
            System.out.println("value = " + result.get());
        } finally {
            owner.shutdown();
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ConfinedCounterDemo.java
java ConfinedCounterDemo
~~~

Stdout:

~~~text
value = 1000
~~~

**Vì sao đúng?**

- Executor một thread thực hiện các task đã submit theo thứ tự tuần tự; các lần **counter.increment()** không chạy chồng nhau.
- Main **không trực tiếp đọc/ghi counter.value** trong lúc owner xử lý. Thay vào đó main gửi task đọc **counter.snapshot()** tới chính owner, rồi lấy kết quả bằng **Future.get()**.
- Task snapshot được submit sau 1.000 task tăng từ cùng thread; trong executor một worker, nó chạy sau những task ấy nên trả 1.000.
- Future.get() cung cấp kết quả và quan hệ đồng bộ hóa tương ứng cho caller. Không cần synchronized trong Counter **vì giao thức ownership** đã loại bỏ truy cập đồng thời đến state đó.

**Ranh giới phải giữ:** Nếu main hoặc thread thứ ba gọi trực tiếp counter.increment() trong lúc executor đang sử dụng counter, confinement bị phá; code lại có nguy cơ race. Nếu dùng một thread pool nhiều worker thay vì **newSingleThreadExecutor()**, các task không còn được đảm bảo chạy trên cùng một thread theo mô hình này.

Đừng coi executor một worker là transaction hoặc durable queue. Nó không làm side effect bên ngoài trở thành atomic; kết quả lỗi trong task cần được xử lý qua Future, và lifecycle executor cần shutdown phù hợp.

## 10. ThreadLocal: trạng thái riêng cho từng thread, nhưng không phải per-request

**ThreadLocal<T>** cung cấp một giá trị gắn với **thread hiện tại**. Mỗi thread có thể nhận một object riêng từ **withInitial()**. Nó hữu ích cho buffer tạm, bộ đếm tạm hoặc ngữ cảnh thật sự có thread affinity.

~~~java
// Minh họa trong một lớp có các import tương ứng:
private static final ThreadLocal<StringBuilder> BUFFER =
        ThreadLocal.withInitial(StringBuilder::new);

// Trong phần mã chạy của một thread:
StringBuilder buffer = BUFFER.get();
try {
    buffer.append("work");
    // Dùng buffer trong chính thread này.
} finally {
    BUFFER.remove();
}
~~~

Đây là đoạn minh họa, không phải ứng dụng độc lập.

**Những giới hạn quan trọng:**

- **ThreadLocal không biến một shared mutable object thành riêng tư** nếu initializer trả về cùng một object được chia sẻ hoặc reference bị trao giữa threads.
- **Thread pool tái sử dụng thread:** một task kết thúc rồi task khác chạy trên cùng thread có thể thấy ThreadLocal còn sót lại; dùng **remove() trong finally** khi scope ngữ cảnh kết thúc.
- **ThreadLocal là per-thread, không phải per-request hay per-logical-task.** Một công việc di chuyển sang thread khác qua executor/callback không tự mang theo ThreadLocal.
- Cần tổng hợp dữ liệu từ nhiều thread thì phải có bước **merge** hoặc cơ chế đồng bộ riêng. Các giá trị ThreadLocal riêng lẻ không tự tạo một tổng toàn cục chính xác.
- ThreadLocal còn sống theo thread nếu không được dọn dẹp, có thể làm giữ reference/tài nguyên không mong muốn ở thread sống lâu.

Nếu bài toán là “tất cả task cập nhật một số dư dùng chung”, đừng dùng ThreadLocal rồi giả định số dư chung tự được bảo vệ. Thay vào đó xác định ownership hoặc cơ chế tổng hợp có đồng bộ hóa.

## 11. Confinement không miễn trừ safe publication và ownership transfer

Thread confinement an toàn khi quyền sở hữu rõ ràng. Khi dữ liệu được chuyển từ thread A sang thread B:

- Trước khi chuyển giao, A có thể tự sửa object.
- Khi giao quyền cho B, phải dùng một **giao thức giao tiếp an toàn** (ví dụ BlockingQueue, submit task tới ExecutorService, CompletableFuture với hợp đồng phù hợp).
- Sau khi giao quyền, A không tiếp tục sửa object đó đồng thời nếu B đang sử dụng, trừ khi có đồng bộ hóa riêng.
- Nếu cần nhiều người đọc, có thể gửi **snapshot immutable** thay vì object mutable.
- Nếu object được trả về cho caller rồi vẫn tiếp tục bị owner sửa, cần xem nó là shared mutable state chứ không còn confined.

### Ví dụ giao tiếp producer/consumer

Một producer có thể tạo các message bất biến và đưa vào **BlockingQueue**. Consumer sở hữu trạng thái tổng hợp và cập nhật tuần tự. Queue có các bảo đảm giao tiếp và memory consistency do API quy định. Nhưng nếu producer giữ tham chiếu tới một message mutable rồi sửa sau khi put, consumer có thể lại thấy race trên nội dung message; queue không “đóng băng” object.

**Confinement** thường đơn giản hơn khi task/message là immutable hoặc chỉ chứa dữ liệu không chia sẻ mutable.

### Confinement theo giai đoạn

Một thread có thể xây object hoàn chỉnh rồi trao **ownership độc quyền** sang thread khác. Đây là *ownership transfer*, không phải object buộc phải sống mãi trên cùng thread. Điều quan trọng là **không có hai chủ đồng thời và việc chuyển giao có safe publication**.

## 12. Bảng quyết định nhanh theo tình huống

| Tình huống | Ưu tiên xem xét | Lý do |
| --- | --- | --- |
| Tổng số request được ghi nhận chính xác, cập nhật độc lập | AtomicLong hoặc AtomicInteger | Một increment nguyên tử |
| Counter thống kê rất nhiều thread, chỉ cần đọc tổng ước lượng tại thời điểm đang cập nhật | LongAdder | Phân tán điểm tranh chấp; sum() không là snapshot nguyên tử khi có update |
| Một trạng thái PENDING → RUNNING chỉ được một thread nhận | AtomicReference/AtomicInteger CAS | Transition một biến có điều kiện nguyên tử |
| “Nếu còn vé thì trừ một” trên một biến số lượng | CAS loop | Gộp check và update |
| Stock và reserved phải cùng cập nhật | Lock hoặc AtomicReference tới immutable snapshot | Bảo vệ bất biến nhiều trường |
| Nhiều bước kiểm tra/gỡ lỗi hoặc thay đổi cấu trúc dữ liệu lớn | synchronized/ReentrantLock | Critical section rõ và dễ lập luận |
| Buffer riêng cho mỗi thread, không trao reference | Local/ThreadLocal confinement | Tránh chia sẻ mutable state |
| Một stream sự kiện được xử lý bởi một owner | Single-thread executor/actor-style ownership | Tuần tự hóa cập nhật |
| Cần cập nhật nhiều DB records bởi nhiều instance | Transaction/conditional update tại DB | Java lock/atomic chỉ có phạm vi JVM |
| Cần snapshot cấu hình thay nguyên khối | Volatile/AtomicReference tới immutable snapshot | Đọc một reference để thấy phiên bản nhất quán |
| Cần await condition hoặc acquisition timeout | ReentrantLock/Condition, hoặc blocking primitive phù hợp | API chờ/tín hiệu có hợp đồng rõ |
| Dữ liệu vừa được xử lý bởi thread pool vừa bị caller sửa trực tiếp | Phải thay đổi thiết kế ownership hoặc đồng bộ | Không còn confinement |

**Chọn theo bất biến lớn nhất**, không theo tên class. Một hệ thống có thể kết hợp: owner thread xử lý task, AtomicLong cho metrics độc lập, lock cho một cấu trúc chia sẻ khác. Từng cơ chế cần ranh giới sở hữu rõ ràng.

## 13. Hiệu năng: đừng giả định atomic luôn nhanh hơn lock

Trong tải nhẹ, synchronized hoặc ReentrantLock có thể rất đủ dùng. Trong tải nặng, CAS loop trên cùng một atomic có thể retry nhiều lần; một lock cũng có thể gây contention và chuyển ngữ cảnh. Confinement giảm tranh chấp trên state nhưng **giới hạn throughput theo tốc độ một owner**, có thể trở thành bottleneck.

| Cơ chế | Chi phí có thể phát sinh | Điểm cần đo |
| --- | --- | --- |
| Atomic/CAS | Cache-line contention, retry, spinning, allocation snapshot | CAS failures, throughput, latency |
| Lock | Lock contention, chờ, chuyển context, fairness overhead | Thời gian giữ/chờ lock, tail latency |
| Confinement | Queueing delay, chi phí message, bottleneck một owner | Queue length, latency chờ, tốc độ tiêu thụ |
| ThreadLocal | Nhiều bản sao bộ nhớ, cleanup/thread reuse | Memory footprint, lifecycle |

Không nên chạy benchmark bằng cách lặp một lần hoặc thêm Thread.sleep() rồi kết luận thứ tự hiệu năng. Nên thử dưới workload đại diện, với nhiều mức cạnh tranh, thời gian warmup/JIT và báo cáo cả throughput lẫn latency. Những lựa chọn cải thiện một metric có thể làm metric khác xấu đi.

Một **LongAdder.sum()** đang diễn ra trong lúc nhiều thread cập nhật có thể không phản ánh một thời điểm nguyên tử. Nếu cần số chính xác phục vụ cấp ID, check quota hoặc quyết định kinh doanh, hãy dùng cơ chế có hợp đồng đúng, không dùng LongAdder chỉ vì throughput cao.

## 14. Các anti-pattern cần tránh

### Một AtomicInteger không bảo vệ nhiều field khác

~~~java
// Minh họa sai nếu yêu cầu available + reserved luôn nhất quán:
AtomicInteger available = new AtomicInteger(10);
AtomicInteger reserved = new AtomicInteger();

available.decrementAndGet();
reserved.incrementAndGet();
~~~

Hai phép atomic có thể bị reader khác chen vào giữa. Sửa bằng lock bảo vệ toàn bộ thao tác, hoặc CAS trên immutable snapshot đại diện cả hai trường.

### Hai lock khác nhau cho cùng state

~~~java
// Minh họa lỗi nếu mỗi caller tạo lock riêng:
void update() {
    synchronized (new Object()) {
        shared++;
    }
}
~~~

Không có cùng monitor qua các lần gọi, nên không bảo vệ shared. Dùng một đối tượng khóa **private final** dùng chung cho tất cả đường truy cập liên quan.

### Check rồi act không nguyên tử

~~~java
// Minh họa lỗi:
if (jobStatus.get() == PENDING) {
    jobStatus.set(RUNNING);
}
~~~

Ví dụ giả định jobStatus là AtomicReference có các giá trị trạng thái PENDING/RUNNING định nghĩa trước. Hai thread đều có thể thấy PENDING và thực thi cùng job. Dùng **compareAndSet(PENDING, RUNNING)** rồi chỉ thread thành công mới được bắt đầu xử lý.

### Đọc state có lock nhưng ghi không lock

Nếu writer giữ lock còn reader đọc plain field ngoài khóa, reader không tham gia cùng giao thức đồng bộ hóa. Sử dụng cùng lock cho cả các đường truy cập liên quan, hoặc thiết kế khác có happens-before đúng.

### Dùng ThreadLocal để “cách ly” object chia sẻ

Nếu nhiều ThreadLocal giữ **cùng reference đến một ArrayList mutable**, những thread đó vẫn có thể sửa cùng object. ThreadLocal chỉ phân tách **slot theo thread**, không tự copy sâu object.

### Trả về mutable reference từ owner

Một owner thread sở hữu HashMap, nhưng trả chính HashMap đó cho caller rồi tiếp tục sửa. Caller có thể tự sửa hoặc iterate cùng lúc, phá confinement. Hãy trả immutable copy/snapshot hoặc dùng API gửi command tới owner.

### Giữ lock khi gọi I/O dài

~~~java
lock.lock();
try {
    // Gọi mạng, sleep, chờ Future.get(), callback không kiểm soát...
} finally {
    lock.unlock();
}
~~~

Mã minh họa này không phải chương trình độc lập. Nó có thể làm contention hoặc deadlock nếu thread khác cần lock để hoàn tất điều kiện chờ. Chỉ giữ khóa cho phần bắt buộc thuộc bất biến; không tự động tách critical section nếu điều đó phá atomicity.

### Nghĩ “không có data race” đồng nghĩa đúng nghiệp vụ

Một thuật toán có thể dùng toàn AtomicInteger nhưng check-then-act vẫn sai. Một actor có thể xử lý message tuần tự nhưng vẫn ghi cùng một đơn hai lần do duplicate command. Java concurrency hỗ trợ bảo đảm dữ liệu, **không tự định nghĩa idempotency/transaction**.

## 15. Chiến lược kết hợp và ranh giới hệ thống

### Atomic cho metrics, lock cho bất biến, owner cho pipeline

Một dịch vụ có thể:

- Dùng một worker sở hữu trạng thái đang xây dựng báo cáo.
- Dùng AtomicLong cho số task đã tiếp nhận từ nhiều thread.
- Dùng một lock cho cache nhỏ bắt buộc cập nhật nhiều field cùng nhau.
- Công bố snapshot bất biến cho người đọc qua AtomicReference.

Đây là các **miền đồng bộ khác nhau**. Không vì một miền dùng AtomicReference mà bỏ khóa ở miền khác.

### Giới hạn giữa nhiều JVM/process

**AtomicInteger, ReentrantLock, synchronized và ThreadLocal** hoạt động trên các đối tượng trong một JVM. Nếu nhiều tiến trình cùng ghi một tài khoản trong cơ sở dữ liệu, cần đồng bộ hóa **tại kho dữ liệu chung**, như cập nhật có điều kiện, optimistic locking/version, constraint, transaction hoặc cơ chế coordination phù hợp.

Ví dụ CAS trong JVM có thể ngăn hai thread đặt cùng một vé **trong process đó**, nhưng không ngăn hai instance khác nhau mỗi bên bán một vé cuối cùng từ một cache cục bộ. Hãy đặt bất biến “không oversell toàn hệ thống” ở nơi có thẩm quyền quản lý lượng vé thật.

### Cancellation và retry

Nếu một task bị interrupt hoặc timeout, trạng thái atomic/lock/confinement không tự rollback hành động trước đó. Xác định rõ:

- Quyền xử lý được cấp vào thời điểm nào?
- Đã commit side effect chưa?
- Nếu retry có thể xử lý trùng không?
- Kết quả đến muộn từ attempt cũ có được phép ghi đè trạng thái mới không?

Trả lời các câu hỏi này là phần của thiết kế nghiệp vụ; đừng kỳ vọng một Java Lock tự cung cấp exactly-once execution.

## 16. Kiểm thử tính đúng đắn

Viết test theo bất biến và kết quả quan sát được:

1. **AtomicCounterChoiceDemo**: sau khi hai thread join, count luôn bằng 20.000.
2. **AtomicReservationDemo**: với 1 vé và hai người mua, accepted=1, remaining=0.
3. **AtomicSnapshotDemo**: với 100 vé và 200 lần yêu cầu, available=0, reserved=100 và tổng=100.
4. **LockedInventoryDemo**: giữ cùng bất biến của ví dụ snapshot nhưng dùng khóa bảo vệ hai trường.
5. **ConfinedCounterDemo**: mọi increment chạy trên owner và snapshot được owner thực hiện sau các command, kết quả 1.000.

Khi thiết kế test bổ sung:

- Dùng **CountDownLatch** để tạo điểm bắt đầu cạnh tranh, nhưng đừng dùng latch thay cho synchronization của code cần kiểm thử.
- Thử cả nhiều số thread, mức contention và tình huống exception/interrupt.
- Với lock, kiểm tra các nhánh **return/throw** vẫn giải phóng khóa; với tryLock chỉ unlock khi đã acquire thành công.
- Với confinement, cố tình kiểm tra không có đường gọi trực tiếp trái ownership, và các message/immutable snapshot được công bố an toàn.
- Với CAS, kiểm tra điều kiện thất bại/retry, không đưa side effect không idempotent vào hàm cập nhật có thể bị gọi lại.
- Không suy ra “chắc chắn thread-safe” chỉ vì stress test chạy vài lần không lỗi; cần lập luận theo JMM và bất biến.

Thứ tự log giữa các worker có thể không xác định; đừng gắn stdout cứng cho những đoạn không đồng bộ có thể xen kẽ. Năm ví dụ trên chỉ in tổng hợp **sau khi có giao thức completion rõ ràng**, nên có stdout xác định trong điều kiện chạy bình thường.

## 17. Checklist ra quyết định

**Trước khi viết code:**

- [ ] Tôi có một định nghĩa bất biến cụ thể và biết biến nào thuộc bất biến đó.
- [ ] Tôi biết tất cả writer/reader và các ranh giới thread/JVM có liên quan.
- [ ] Tôi có thể tránh chia sẻ mutable state hoàn toàn hay không?
- [ ] Nếu chọn atomic, thao tác atomic bao phủ **toàn bộ quyết định** cần thiết không?
- [ ] Nếu chọn CAS trên snapshot, state có thật sự immutable và không có side effect trong retry không?
- [ ] Nếu chọn lock, mọi đường truy cập đều giữ **cùng lock**, không tạo lock riêng tùy caller.
- [ ] Với ReentrantLock, mọi acquisition thành công đều được nhả đúng owner trong finally.
- [ ] Nếu nhiều lock, có thứ tự khóa nhất quán để tránh circular wait.
- [ ] Nếu chọn ThreadLocal, có cleanup trên thread pool và không nhầm per-thread với per-request.
- [ ] Nếu chọn owner thread, reference mutable có bị rò sang thread khác không?
- [ ] Có giao thức completion/safe publication cho dữ liệu được gửi giữa threads không?
- [ ] Có chiến lược riêng cho DB, side effect, timeout, retry và cancellation không?

## 18. Câu hỏi tự kiểm tra

**1. Hai thread tăng một bộ đếm dùng chung, không liên quan trạng thái nào khác. Ưu tiên gì?**  
Có thể dùng **AtomicInteger.incrementAndGet()** hoặc AtomicLong. Nếu chỉ là thống kê cộng nhiều dưới contention cao và không cần snapshot tuyến tính, cân nhắc LongAdder.

**2. Có hai field stock và reserved, sau mỗi cập nhật phải luôn thỏa stock + reserved = total. Hai AtomicInteger riêng là đủ không?**  
Không. Reader có thể quan sát hai field ở các mốc khác nhau. Dùng một lock bao phủ cập nhật/đọc hoặc một AtomicReference tới immutable snapshot.

**3. Một worker tuần tự hóa toàn bộ cập nhật vào object mutable. Có cần synchronized trong object không?**  
Không nhất thiết nếu ownership confinement là thật: không thread khác truy cập đồng thời và chuyển giao công việc/kết quả có safe publication.

**4. ThreadLocal có đảm bảo một request chỉ chạy trên đúng một thread không?**  
Không. ThreadLocal gắn với thread thực thi, không gắn với request; task chuyển thread sẽ không tự mang giá trị. Thread pool có thể tái sử dụng dữ liệu còn sót nếu không remove().

**5. CAS có thể thất bại dù code của thread hiện tại không có bug không?**  
Có. Thread khác có thể cập nhật trước, khiến giá trị hiện tại khác expected. Cần retry hoặc nhánh xử lý phù hợp.

**6. Vì sao CAS loop không nên gọi API thanh toán ở trong vòng retry?**  
Vì mỗi lần retry có thể thực thi logic trước CAS nhiều lần dù chỉ một CAS thành công. Side effect có thể bị lặp; cần tách bước nghiệp vụ theo giao thức đúng.

**7. synchronized và ReentrantLock có thể bảo vệ trạng thái ở hai JVM không?**  
Không. Java lock chỉ có hiệu lực với những thread cùng dùng đối tượng khóa tương ứng trong JVM. Dữ liệu liên tiến trình cần cơ chế ở nguồn chia sẻ.

**8. Một ứng dụng vừa dùng AtomicLong vừa dùng ReentrantLock có sai kiến trúc không?**  
Không. Các biến/nhóm bất biến khác nhau có thể cần cơ chế khác nhau, miễn là ranh giới và ownership rõ ràng.

**9. Nếu dùng lock cho writer nhưng reader đọc field thường ngoài lock, có được bảo đảm thấy cập nhật không?**  
Không thể suy ra chỉ từ lock của writer. Reader phải tham gia cùng giao thức hoặc có một quan hệ happens-before khác được thiết kế đúng.

**10. Khi nào nên ưu tiên cách đơn giản hơn về mặt bảo trì?**  
Khi yêu cầu hiệu năng chưa được chứng minh, **synchronized nhỏ và rõ ràng** có thể tốt hơn CAS state machine phức tạp; khi có thể đảm bảo ownership một worker, tránh chia sẻ có thể còn đơn giản hơn cả hai.

## 19. Tổng kết

**Atomic** phù hợp khi toàn bộ quyết định có thể được thực hiện bằng một primitive nguyên tử hoặc một CAS trên snapshot bất biến; **lock** phù hợp khi cần bảo vệ một critical section gồm nhiều bước và nhiều trường; **thread confinement** phù hợp khi có thể thiết kế **một chủ sở hữu duy nhất** cho mutable state và chỉ trao dữ liệu qua giao thức rõ ràng.

Lựa chọn đúng được đánh giá theo **bất biến, happens-before, ownership, ranh giới JVM và side effect**, không chỉ theo số dòng code hay hiệu năng được đồn đoán. Khi cần, kết hợp các cơ chế trong những miền trạng thái tách biệt; nhưng luôn xác định ai được đọc, ai được ghi, cập nhật nào phải nguyên tử và cách kiểm chứng kết quả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-013">AtomicIntegerArray, AtomicLongArray và AtomicReferenceArray: cập nhật từng phần tử</a>

<details>
<summary>Xem chi tiết</summary>

# AtomicIntegerArray, AtomicLongArray và AtomicReferenceArray: cập nhật từng phần tử trong Java

> **Ý chính:** Ba lớp thuộc `java.util.concurrent.atomic` cho phép nhiều thread **đọc, ghi, trao đổi, tăng/cộng hoặc compare-and-set (CAS) nguyên tử tại một chỉ số cụ thể** mà không cần tự khóa cả mảng cho từng thao tác. Nhưng **atomic trên từng phần tử không có nghĩa toàn bộ mảng hay một nhóm phần tử là một transaction**. Đối với `AtomicReferenceArray<E>`, atomicity thuộc về **tham chiếu ở một ô**, không tự bảo vệ trạng thái mutable bên trong object.

Bài thuộc nhóm **Atomic & CAS** của Java concurrency nói chung. Các ví dụ ưu tiên Java 17+ và API Java SE; không bàn đến Spring.

## 1. Vấn đề: các phần tử trong mảng dùng chung có thể bị cập nhật cạnh tranh

Giả sử nhiều thread thống kê số lượt truy cập theo loại sự kiện. Một mảng thường có thể biểu diễn các bộ đếm:

~~~java
// Đoạn minh họa, không phải chương trình độc lập.
int[] counts = new int[4];

void record(int bucket) {
    counts[bucket]++; // Không atomic khi nhiều thread cập nhật cùng bucket.
}
~~~

`counts[bucket]++` có ba bước logic: **đọc → cộng → ghi**. Hai thread cùng đọc `10`, cùng tính `11` rồi cùng ghi `11` sẽ làm mất một cập nhật. Đối với các phần tử khác chỉ số, một update ở phần tử A không nhất thiết xung đột dữ liệu với phần tử B, nhưng toàn bộ thuật toán vẫn phải xem xét invariant và cách công bố mảng.

Một số cách giải quyết là đặt `synchronized` quanh mọi cập nhật, dùng một dãy `AtomicInteger`, hoặc dùng các lớp **atomic array** có API thống nhất cho từng slot. Atomic array thường giúp mô tả các bộ đếm/trạng thái độc lập được truy cập theo index mà không phải tạo một đối tượng atomic riêng cho mỗi phần tử.

### Ba lớp và kiểu dữ liệu

| Lớp | Mỗi phần tử lưu | Ví dụ phù hợp |
| --- | --- | --- |
| `AtomicIntegerArray` | `int` | Histogram, số lần retry theo nhóm, trạng thái số nguyên độc lập |
| `AtomicLongArray` | `long` | Bộ đếm 64-bit, sequence theo partition, tích lũy số lượng lớn |
| `AtomicReferenceArray<E>` | Tham chiếu kiểu `E` | Các slot giữ immutable snapshot, object handle, state machine theo ô |

Cả ba đều có **độ dài cố định sau khi tạo**. API không phải `List` có thể thêm/xóa phần tử. Một phần tử là một vị trí do `index` xác định, thường `0 <= index < length()`; dùng index không hợp lệ sẽ gây lỗi giới hạn chỉ số theo API.

### Hình dung phạm vi atomicity

~~~text
AtomicIntegerArray:  [slot 0] [slot 1] [slot 2] [slot 3]
                        ^         ^
                        |         |
                   CAS/getAndAdd  incrementAndGet

Mỗi operation nguyên tử cho CHÍNH SLOT tương ứng.
Không có "transaction tự động" bao trùm slot 0 + slot 1.
~~~

Không nên đọc câu “atomic array” thành “mọi phép đọc toàn mảng là atomic”. Tên lớp biểu đạt một **tập hợp các phần tử có thao tác atomic**, không biến một vòng `for` đọc 100 phần tử thành snapshot đồng thời.

## 2. Các phương thức quan trọng

### Nhóm thao tác chung

| API | Ý nghĩa | Phạm vi atomic |
| --- | --- | --- |
| `length()` | Số phần tử cố định của atomic array | Thuộc tính cấu trúc, không phải cập nhật |
| `get(i)` | Đọc giá trị một phần tử với ngữ nghĩa volatile của API | Một slot |
| `set(i, value)` | Ghi giá trị một phần tử với ngữ nghĩa volatile của API | Một slot |
| `lazySet(i, value)` | Ghi theo release semantics (ở Java hiện đại) | Một slot; không thay cho CAS |
| `getAndSet(i, value)` | Trao đổi, trả giá trị cũ | Một slot |
| `compareAndSet(i, expected, update)` | Ghi có điều kiện khi giá trị hiện tại khớp expected, trả boolean | Một slot |
| `getAndUpdate(i, function)` | Biến đổi nguyên tử, trả giá trị cũ | Một slot, hàm có thể chạy lại |
| `updateAndGet(i, function)` | Biến đổi nguyên tử, trả giá trị mới | Một slot, hàm có thể chạy lại |

`AtomicIntegerArray` và `AtomicLongArray` có thêm các API số học như `getAndIncrement(i)`, `incrementAndGet(i)`, `getAndDecrement(i)`, `decrementAndGet(i)`, `getAndAdd(i, delta)` và `addAndGet(i, delta)`. Chúng còn có `getAndAccumulate`/`accumulateAndGet` để kết hợp giá trị cũ và một giá trị đầu vào.

**Lưu ý phiên bản:** Các API memory-ordering như `getPlain`, `setPlain`, `getAcquire`, `setRelease`, `compareAndExchange` có các phiên bản tương ứng trong Java hiện đại. Bài này tập trung `get/set` và phép cập nhật RMW chuẩn Java 17; khi tối ưu memory ordering ở mức thấp, phải đọc đúng contract riêng từng phương thức trong JDK API.

### Không ghép `get` và `set` rồi tưởng là nguyên tử

~~~java
// Sai về compound update nếu nhiều writer cùng chỉ số.
int previous = counts.get(2);
counts.set(2, previous + 1);
~~~

Dù `get` và `set` **từng cái** có bảo đảm atomic/visibility, giữa hai lời gọi có khoảng trống để thread khác cập nhật slot 2. Muốn tăng nguyên tử, dùng `incrementAndGet(2)` hoặc một CAS loop đúng.

Tương tự, `volatile int[] shared` chỉ làm **tham chiếu mảng** có volatile semantics, không biến `shared[i]++` thành atomic. Các phần tử của `int[]` cũng không tự trở thành volatile chỉ vì biến trỏ đến mảng là volatile.

## 3. Cấu tạo và khởi tạo mảng atomic

Có hai cách thường dùng:

~~~java
// Snippet API, không phải chương trình độc lập.
AtomicIntegerArray ints = new AtomicIntegerArray(4);
AtomicLongArray longs = new AtomicLongArray(new long[]{2L, 4L, 6L});
AtomicReferenceArray<String> names =
        new AtomicReferenceArray<>(new String[]{"a", "b"});
~~~

Dùng constructor độ dài tạo các slot ban đầu bằng **giá trị mặc định**: `0` cho `int`/`long`, `null` cho tham chiếu. Dùng constructor nhận mảng thì giá trị từng slot ban đầu được **sao chép** vào storage của atomic array.

Điểm quan trọng:

- `new AtomicIntegerArray(sourceIntArray)` **không** bọc trực tiếp mảng `sourceIntArray`. Sửa mảng nguồn về sau không làm atomic array đổi theo.
- `new AtomicLongArray(sourceLongArray)` cũng sao chép giá trị primitive.
- `new AtomicReferenceArray<>(sourceRefs)` sao chép **các tham chiếu**, không clone các object được tham chiếu. Nếu object mutable được sửa từ nguồn, cùng object vẫn có thể bị nhìn thấy qua cả hai đường.
- `length()` không đổi; cần một cấu trúc thay kích cỡ động thì phải thiết kế lại (ví dụ publication một reference mới tới toàn cấu trúc hoặc container concurrent khác).

### Ví dụ 1 — kiểm tra việc sao chép mảng nguồn

Tạo tệp `AtomicArrayCopy.java` (Java 17+):

~~~java
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class AtomicArrayCopy {
    public static void main(String[] args) {
        int[] original = {1, 2};
        AtomicIntegerArray values = new AtomicIntegerArray(original);

        original[0] = 100;
        values.incrementAndGet(1);

        System.out.println("original = " + original[0] + "," + original[1]);
        System.out.println("atomic = " + values.get(0) + "," + values.get(1));

        StringBuilder mutable = new StringBuilder("start");
        StringBuilder[] references = {mutable};
        AtomicReferenceArray<StringBuilder> refs =
                new AtomicReferenceArray<>(references);
        references[0] = new StringBuilder("other");
        mutable.append("-changed");

        System.out.println("reference array slot = " + references[0]);
        System.out.println("atomic ref slot = " + refs.get(0));
    }
}
~~~

Chạy:

~~~bash
javac AtomicArrayCopy.java
java AtomicArrayCopy
~~~

Đầu ra:

~~~text
original = 100,2
atomic = 1,3
reference array slot = other
atomic ref slot = start-changed
~~~

Phần primitive chứng minh hai mảng **có storage riêng**. Phần reference cho thấy việc gán lại `references[0]` **không** thay đổi slot trong atomic array, nhưng object `mutable` còn được cả bên lưu tham chiếu tới nó; mutate `StringBuilder` bằng alias vẫn làm nội dung object thay đổi. **AtomicReferenceArray không làm StringBuilder thread-safe.**

Các thao tác lên `StringBuilder` trong ví dụ chạy tuần tự để đầu ra xác định; nếu nhiều thread đồng thời sửa cùng `StringBuilder` mà không có synchronization riêng, vẫn có nguy cơ data race.

## 4. AtomicIntegerArray — tăng từng bucket một cách nguyên tử

Mỗi slot của `AtomicIntegerArray` giống một bộ đếm `int` có các API RMW. Thay vì thao tác `int[]` dùng chung, thread gọi `incrementAndGet(index)` để tăng một slot nguyên tử. Các thread cập nhật slot **khác nhau** không cần một exclusive lock bảo vệ toàn bộ mảng ở tầng ứng dụng.

### Ví dụ 2 — nhiều thread tăng vào cùng một index

Tạo `AtomicIntegerBuckets.java` (Java 17+):

~~~java
import java.util.concurrent.atomic.AtomicIntegerArray;

public class AtomicIntegerBuckets {
    public static void main(String[] args) throws InterruptedException {
        AtomicIntegerArray buckets = new AtomicIntegerArray(2);
        Thread[] workers = new Thread[4];

        for (int i = 0; i < workers.length; i++) {
            final int bucket = i % 2;
            workers[i] = new Thread(() -> {
                for (int j = 0; j < 10_000; j++) {
                    buckets.incrementAndGet(bucket);
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("bucket 0 = " + buckets.get(0));
        System.out.println("bucket 1 = " + buckets.get(1));
        System.out.println("total = "
                + (buckets.get(0) + buckets.get(1)));
    }
}
~~~

Chạy:

~~~bash
javac AtomicIntegerBuckets.java
java AtomicIntegerBuckets
~~~

Đầu ra xác định nếu không có lỗi bên ngoài:

~~~text
bucket 0 = 20000
bucket 1 = 20000
total = 40000
~~~

Hai worker cùng cập nhật bucket 0; hai worker còn lại cùng cập nhật bucket 1. `incrementAndGet(bucket)` tránh mất cập nhật trên từng bucket. Main `join()` tất cả worker **trước khi** tổng kết nên mọi lần tăng đã hoàn tất.

Nếu main vừa đọc slot 0 vừa đọc slot 1 **trong khi worker còn cập nhật**, tổng từ hai lần `get` không phải một snapshot atomic cùng thời điểm. Ở ví dụ, `join` khiến dữ liệu ổn định nên tổng sau đó chính xác.

### Khác biệt các phép tăng

~~~java
AtomicIntegerArray counters = new AtomicIntegerArray(new int[]{7});

int old = counters.getAndIncrement(0); // old = 7; slot = 8
int now = counters.incrementAndGet(0); // now = 9; slot = 9
int beforeAdd = counters.getAndAdd(0, 5); // beforeAdd = 9; slot = 14
~~~

Đây là snippet minh họa. `getAndX` trả **giá trị trước cập nhật**, `XAndGet` trả **giá trị sau cập nhật**. Khi code logic dựa vào số vé/sequence, nhầm hai kiểu return value có thể gây off-by-one.

**Overflow:** phép cộng `int`/`long` của API arithmetic tuân theo ngữ nghĩa số nguyên Java (có thể wrap khi tràn), không tự ném exception chỉ vì quá giới hạn. Nếu cần giới hạn/cấm overflow, có thể dùng CAS loop với `Math.addExact` trong hàm tính thuần hoặc lock và xử lý ngoại lệ theo yêu cầu nghiệp vụ.

## 5. AtomicLongArray — bộ đếm 64-bit theo slot

`AtomicLongArray` có API số học tương tự nhưng mỗi ô là `long`. Nó phù hợp khi phạm vi số lớn hơn `int`, ví dụ thống kê bytes theo partition hoặc tổng đơn vị tiền nhỏ nhất (với policy overflow phù hợp).

### Ví dụ 3 — tổng bytes từ các worker

Tạo `AtomicLongPartitions.java` (Java 17+):

~~~java
import java.util.concurrent.atomic.AtomicLongArray;

public class AtomicLongPartitions {
    public static void main(String[] args) throws InterruptedException {
        AtomicLongArray bytes = new AtomicLongArray(2);

        Thread a = new Thread(() -> {
            for (int i = 0; i < 10_000; i++) {
                bytes.getAndAdd(0, 100L);
            }
        });
        Thread b = new Thread(() -> {
            for (int i = 0; i < 10_000; i++) {
                bytes.addAndGet(0, 200L);
            }
        });
        Thread c = new Thread(() -> {
            for (int i = 0; i < 10_000; i++) {
                bytes.incrementAndGet(1);
            }
        });

        a.start();
        b.start();
        c.start();
        a.join();
        b.join();
        c.join();

        System.out.println("partition 0 = " + bytes.get(0));
        System.out.println("partition 1 = " + bytes.get(1));
    }
}
~~~

Chạy:

~~~bash
javac AtomicLongPartitions.java
java AtomicLongPartitions
~~~

Đầu ra:

~~~text
partition 0 = 3000000
partition 1 = 10000
~~~

Hai worker cạnh tranh cùng slot 0 nhưng cập nhật atomic bằng `getAndAdd` và `addAndGet`; worker thứ ba cập nhật slot 1. Cả hai API “getAndAdd” và “addAndGet” đều **cập nhật nguyên tử**, chỉ khác giá trị trả về.

Từng phần tử là `long`; không nên xem cả `AtomicLongArray` là một bộ đếm 128-bit hoặc một kết quả “tổng nhiều slot” atomic. Nếu chỉ có **một** số duy nhất, `AtomicLong` có thể đơn giản hơn. Nếu chỉ đếm thống kê dưới contention cao và không cần snapshot chính xác giữa lúc update, `LongAdder` cũng là ứng viên, nhưng `sum()` của nó không phải snapshot atomic khi đang có writer.

## 6. CAS trên một index: tránh cập nhật dựa trên giá trị đã cũ

`compareAndSet(index, expected, update)` kiểm tra và cập nhật **trong một operation**. Với primitive array, so sánh theo **giá trị primitive**; với reference array, CAS so sánh theo **identity của tham chiếu**, không gọi `equals`.

Mô hình:

~~~text
old = array.get(i)            // Đọc snapshot của slot i
new = f(old)                  // Tính candidate
if array.compareAndSet(i, old, new):
    success                   // Chỉ ghi nếu slot i vẫn bằng old
else:
    retry from current value  // Không ghi đè dựa trên snapshot cũ
~~~

Một CAS loop phù hợp khi phép biến đổi không có API chuyên dụng hoặc cần ràng buộc điều kiện. Với phép tăng thuần, `incrementAndGet` thường ngắn và ít lỗi hơn tự viết vòng CAS.

### Ví dụ 4 — CAS loop cho giá trị không vượt trần

Tạo `BoundedAtomicSlot.java` (Java 17+):

~~~java
import java.util.concurrent.atomic.AtomicIntegerArray;

public class BoundedAtomicSlot {
    private static boolean reserve(
            AtomicIntegerArray used, int index, int capacity) {
        while (true) {
            int current = used.get(index);

            if (current >= capacity) {
                return false;
            }
            if (used.compareAndSet(index, current, current + 1)) {
                return true;
            }
            // Có contention; đọc lại slot để quyết định từ dữ liệu mới.
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicIntegerArray used = new AtomicIntegerArray(1);
        Thread[] workers = new Thread[4];
        AtomicIntegerArray accepted = new AtomicIntegerArray(1);

        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    if (reserve(used, 0, 250)) {
                        accepted.incrementAndGet(0);
                    }
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }
        System.out.println("used = " + used.get(0));
        System.out.println("accepted = " + accepted.get(0));
    }
}
~~~

Chạy:

~~~bash
javac BoundedAtomicSlot.java
java BoundedAtomicSlot
~~~

Đầu ra:

~~~text
used = 250
accepted = 250
~~~

Bốn thread cố đặt tổng cộng 400 chỗ vào một slot có capacity 250. Chỉ thread CAS thành công mới tăng `used`, và lần kiểm tra `current >= capacity` ngăn vượt trần. Mỗi phép reserve thành công tăng đúng 1; không có lost update.

**Chú ý:** Việc cập nhật `accepted` ở ví dụ chỉ để kiểm nghiệm, **không** là một transaction nguyên tử cùng với `used` nếu có reader khác quan sát khi worker vẫn chạy. Sau `join`, hai giá trị đều bằng số lần thành công. Khi cần invariant giữa hai biến luôn đúng ở thời điểm bất kỳ, dùng một state nguyên tử chung hoặc khóa đúng phạm vi.

## 7. AtomicReferenceArray<E> — thay nguyên tử **tham chiếu** của từng ô

`AtomicReferenceArray<E>` phù hợp nếu mỗi slot giữ một object/handle hoặc snapshot. Các thao tác như `get(i)`, `set(i, newRef)`, `getAndSet(i, newRef)`, `compareAndSet(i, expectedRef, newRef)` cập nhật **tham chiếu ở ô i**, không sâu bên trong object.

Giả sử mỗi slot là một immutable state:

~~~java
record Slot(String owner, int version) {}
AtomicReferenceArray<Slot> slots = new AtomicReferenceArray<>(2);
slots.set(0, new Slot("worker-a", 1));
~~~

Snippet trên chưa độc lập vì cần import và một phương thức/class chứa nó. Nếu nhiều thread thay slot 0 qua CAS, mỗi cập nhật reference là nguyên tử và reader có thể nhận một phiên bản snapshot, không nhận “nửa tham chiếu”.

### CAS reference sử dụng `==`, không sử dụng `equals()`

Hai object có nội dung bằng nhau nhưng **khác identity** sẽ không khớp trong `compareAndSet` nếu expected là object khác.

Tạo `ReferenceCasIdentity.java` (Java 17+):

~~~java
import java.util.concurrent.atomic.AtomicReferenceArray;

public class ReferenceCasIdentity {
    public static void main(String[] args) {
        String initial = new String("same");
        AtomicReferenceArray<String> slots =
                new AtomicReferenceArray<>(new String[]{initial});
        String equalButDistinct = new String("same");

        System.out.println("equals = " + initial.equals(equalButDistinct));
        System.out.println("same identity = " + (initial == equalButDistinct));

        boolean failed = slots.compareAndSet(0, equalButDistinct, "new");
        boolean succeeded = slots.compareAndSet(0, initial, "new");

        System.out.println("CAS different ref = " + failed);
        System.out.println("CAS original ref = " + succeeded);
        System.out.println("current = " + slots.get(0));
    }
}
~~~

Chạy:

~~~bash
javac ReferenceCasIdentity.java
java ReferenceCasIdentity
~~~

Đầu ra:

~~~text
equals = true
same identity = false
CAS different ref = false
CAS original ref = true
current = new
~~~

`new String("same")` được dùng riêng để minh họa hai object có `equals` true nhưng `==` false; không phải khuyến nghị tạo String bằng constructor trong nghiệp vụ. Khi muốn CAS trên trạng thái immutable, thường lấy **chính reference hiện tại** bằng `get(i)` rồi tính new state từ reference đó; không dựng một object “equal” và mong CAS thành công.

## 8. AtomicReferenceArray + immutable snapshot + updateAndGet

Với mỗi slot giữ một immutable object, `updateAndGet(i, f)` có thể tạo ra một state mới **từ state hiện tại của đúng ô i**. Phương thức tự retry khi bị tranh chấp, nên có thể tránh việc tự viết CAS loop. Nhưng **hàm f có thể được gọi nhiều lần**.

### Ví dụ 6 — phiên bản hóa state độc lập theo index

Tạo `ReferenceArrayUpdate.java` (Java 17+):

~~~java
import java.util.concurrent.atomic.AtomicReferenceArray;

public class ReferenceArrayUpdate {
    record CounterState(long count, int version) {}

    public static void main(String[] args) throws InterruptedException {
        AtomicReferenceArray<CounterState> slots =
                new AtomicReferenceArray<>(
                        new CounterState[]{
                                new CounterState(0, 0),
                                new CounterState(0, 0)
                        });

        Runnable updateFirst = () -> {
            for (int i = 0; i < 1_000; i++) {
                slots.updateAndGet(0, old ->
                        new CounterState(old.count() + 1, old.version() + 1));
            }
        };

        Thread a = new Thread(updateFirst);
        Thread b = new Thread(updateFirst);
        Thread c = new Thread(() -> {
            for (int i = 0; i < 500; i++) {
                slots.updateAndGet(1, old ->
                        new CounterState(old.count() + 2, old.version() + 1));
            }
        });

        a.start();
        b.start();
        c.start();
        a.join();
        b.join();
        c.join();

        CounterState first = slots.get(0);
        CounterState second = slots.get(1);
        System.out.println("first = " + first.count() + "/" + first.version());
        System.out.println("second = " + second.count() + "/" + second.version());
    }
}
~~~

Chạy:

~~~bash
javac ReferenceArrayUpdate.java
java ReferenceArrayUpdate
~~~

Đầu ra:

~~~text
first = 2000/2000
second = 1000/500
~~~

Trong mỗi slot, cặp `count` và `version` nằm **trong cùng một record bất biến**, và reference được thay bằng một operation atomic. Vì vậy reader `get(slot)` lấy **một snapshot nhất quán của cặp field** thuộc cùng một phiên bản. Hai slot vẫn độc lập: đọc slot 0 rồi slot 1 không tạo một snapshot cùng thời điểm của cả hai.

**Hàm cập nhật phải không có side effect:** hai thread có thể cùng lấy snapshot A; khi CAS của một thread thành công, thread kia cần tính lại từ snapshot mới. Vì vậy lambda có thể được áp dụng nhiều lần. Không gọi API gửi email, trừ tiền ngoài hệ thống, tăng một counter khác hoặc log mà bạn kỳ vọng “đúng một lần” bên trong lambda.

Trong ví dụ, tạo `new CounterState(...)` là tác dụng nội bộ của phép tính candidate; nếu CAS thất bại, object candidate không được publish. Công thức phải có tính thuần và không dựa vào dữ liệu mutable không được đồng bộ ở bên ngoài.

## 9. AtomicReferenceArray không bảo vệ mutation của object được tham chiếu

Đây là lỗi phổ biến:

~~~java
// Mã minh họa không an toàn; không phải chương trình độc lập.
AtomicReferenceArray<MutableCounter> slots =
        new AtomicReferenceArray<>(1);
slots.set(0, new MutableCounter());

void increment() {
    slots.get(0).value++; // Cập nhật field bên trong không atomic.
}
~~~

`slots.get(0)` đọc **reference** nguyên tử/volatile; ngay sau đó, `value++` vẫn là RMW trên shared mutable object. Nếu hai thread cùng cập nhật object đó, chúng có thể lost update. Nếu object còn bị mutate sau khi publish, cần synchronization riêng, atomic fields của chính object, ownership isolation hoặc **thay toàn object bằng immutable snapshot** qua CAS như phần trước.

**Không lẫn lộn:**

- Atomicity của **slot reference**: đọc/ghi/tráo reference không bị xé thành “nửa pointer”.
- Atomicity của **field bên trong**: tùy lớp object và protocol riêng.
- Atomicity của **nhiều slot**: không được ba lớp atomic array cung cấp trực tiếp.

Dùng `AtomicReferenceArray<AtomicInteger>` không làm điều trên sai: nếu mỗi slot chứa `AtomicInteger` và caller gọi `incrementAndGet()` trên object thì phép tăng của object được atomic bởi **AtomicInteger**, không phải bởi tính chất “reference array” ở bên ngoài. Cần cân nhắc chi phí và tính nhất quán khi reference slot có thể bị thay ngay trong lúc caller đang tăng object cũ.

## 10. Không có transaction nhiều phần tử

Giả sử cần chuyển 10 đơn vị từ tài khoản 0 sang tài khoản 1, invariant là **tổng phải luôn bằng 200**. Một mảng atomic cho hai slot có thể cập nhật mỗi slot riêng lẻ, nhưng không bảo vệ cả hai như **một** chuyển khoản:

~~~java
AtomicIntegerArray balances =
        new AtomicIntegerArray(new int[]{100, 100});

balances.addAndGet(0, -10);
balances.addAndGet(1, 10);
~~~

Giữa hai lời gọi, thread khác có thể đọc `90` ở slot 0 và `100` ở slot 1, thấy tổng `190`. Từng lần update atomic nhưng invariant tổng không được giữ tại mọi điểm quan sát.

### Ví dụ 7 — chứng minh khoảng trống giữa hai slot một cách xác định

Tạo `CrossSlotNotAtomic.java` (Java 17+):

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class CrossSlotNotAtomic {
    public static void main(String[] args) throws InterruptedException {
        AtomicIntegerArray balances =
                new AtomicIntegerArray(new int[]{100, 100});
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);

        Thread transfer = new Thread(() -> {
            balances.addAndGet(0, -10);
            firstUpdated.countDown();
            try {
                resume.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            balances.addAndGet(1, 10);
        });

        transfer.start();
        firstUpdated.await();

        System.out.println("during = "
                + (balances.get(0) + balances.get(1)));

        resume.countDown();
        transfer.join();

        System.out.println("after = "
                + (balances.get(0) + balances.get(1)));
    }
}
~~~

Chạy:

~~~bash
javac CrossSlotNotAtomic.java
java CrossSlotNotAtomic
~~~

Stdout khi không bị ngắt bên ngoài:

~~~text
during = 190
after = 200
~~~

`CountDownLatch` giữ writer lại **giữa hai cập nhật**, nên main thực sự quan sát một trạng thái trung gian; đây không phải kết quả do may rủi scheduler. Thậm chí nếu reader đọc hai slot trong một vòng `for` rất nhanh, cũng không có snapshot nguyên tử giữa các slot.

### Các giải pháp nếu invariant trải trên nhiều phần tử

- **Dùng một lock bao cả writer và reader:** mọi thao tác chuyển khoản, đọc tổng/snapshot và kiểm tra invariant đều dùng **cùng monitor hoặc Lock**. Reader muốn snapshot nhất quán phải tham gia protocol ấy; không đủ nếu chỉ writer khóa.
- **Immutable whole-state + AtomicReference<State>:** nếu state nhỏ và dễ sao chép, giữ cả hai balance trong một record hoặc object bất biến và thay thế **một reference** bằng CAS. Cập nhật toàn state có một linearization point theo reference.
- **Transactional/concurrent data structure chuyên dụng:** khi nghiệp vụ có nhiều giao dịch, lựa chọn cấu trúc/bộ đồng bộ phải phù hợp invariant. Không cố “khóa từng slot độc lập” rồi tuyên bố bảo toàn cả nhóm.
- **Phân biệt metric snapshot và transaction:** với thống kê gần đúng, hai `get` liên tiếp có thể chấp nhận; với tiền, quota tổng, inventory nhiều kho, thường không.

Một ví dụ state nguyên khối đơn giản:

~~~java
// Snippet minh họa; không phải chương trình độc lập.
record Pair(int left, int right) {}
AtomicReference<Pair> state =
        new AtomicReference<>(new Pair(100, 100));

Pair after = state.updateAndGet(old ->
        new Pair(old.left() - 10, old.right() + 10));
~~~

Một phép `updateAndGet` thay toàn reference nên reader `state.get()` lấy một `Pair` nhất quán. Cần xử lý điều kiện số dư, overflow, retry và chính sách nghiệp vụ trong phép tính thuần; không gọi side effect ngoài transaction trong lambda.

## 11. Vấn đề ABA khi CAS tham chiếu

CAS kiểm tra *giá trị hiện tại có bằng expected hay không*, không kiểm tra toàn bộ **lịch sử** của slot. Ví dụ một reference trong slot có thể đổi A → B → A; một thread đang giữ expected A thấy CAS thành công vì slot lại trỏ về đúng A, dù có thay đổi trung gian. Đây là vấn đề **ABA** đối với các thuật toán cần phát hiện mọi thay đổi.

Với object immutable và mỗi version tạo object mới, quay lại **đúng cùng reference A** có thể ít xuất hiện, nhưng không thể lấy xác suất đó làm bảo đảm thuật toán. Khi cần phát hiện version đã đổi, có thể gắn **version/token** trong immutable state và CAS trên **object state chứa cả dữ liệu + version**; hoặc dùng các lớp như `AtomicStampedReference` theo tình huống (lớp này không phải atomic array).

Lưu ý rằng CAS trên primitive `int` cũng có thể gặp A → B → A về **giá trị**. Vấn đề chỉ nguy hiểm khi logic cần biết có thay đổi trung gian; không phải mọi counter CAS đều có lỗi ABA.

## 12. Memory visibility, ordering và hàm update

Atomic arrays cung cấp các bảo đảm bộ nhớ rõ ràng cho từng operation:

- `get(i)` đọc với hiệu ứng **volatile read**, `set(i,v)` ghi với hiệu ứng **volatile write** theo tài liệu Java hiện đại.
- `compareAndSet` và các operation RMW chuẩn có hiệu ứng đồng bộ do API chỉ định; không phải chỉ là một phép so sánh Java thông thường.
- `lazySet`/`setRelease` có **release semantics**, không tương đương `set` ở tất cả các trường hợp; dùng khi thiết kế memory-ordering và giao thức đọc phù hợp.
- `getPlain`/`setPlain`/`getOpaque`/`setOpaque`/`getAcquire`/`setRelease` có contract riêng và **không nên thay tùy ý** chỉ để tối ưu.
- Một phép `get(i)` không cung cấp snapshot nguyên tử cho tất cả các `j != i`.
- Thread dùng `join`/`Future.get` để chờ công việc kết thúc trước khi kiểm chứng kết quả cuối, chứ không đọc dữ liệu đang thay đổi rồi gọi đó là kết quả cuối.

Một hiểu lầm khác: “`updateAndGet` sẽ gọi lambda đúng một lần”. Không đúng. Trong cạnh tranh, hàm có thể được gọi lại. Các hàm `UnaryOperator`/`IntUnaryOperator`/`LongUnaryOperator` hoặc binary operator dùng trong accumulate nên **side-effect-free**, không sửa object đầu vào và không dựa trên số lần gọi.

### Vòng lặp CAS không bảo đảm mọi thread tiến triển công bằng

Atomic operations có thể tránh việc chờ một monitor truyền thống đối với từng slot, nhưng điều đó **không tự bảo đảm** tất cả thread sẽ hoàn tất trong thời gian giới hạn. Một thread dưới contention cao có thể retry nhiều lần; scheduler và chi phí cache coherence vẫn ảnh hưởng latency. Lock-free ở mức thuật toán không đồng nghĩa wait-free hoặc “không có chi phí đồng bộ”.

## 13. Kiểm tra index và chia sẻ trách nhiệm

Các mảng atomic thường được tạo một lần rồi truyền đến worker. Để tránh lỗi:

1. Xác định **mapping index** rõ: mỗi index đại diện loại sự kiện, partition, client hay một state machine nào?
2. Kiểm tra index từ input nếu dữ liệu đến từ bên ngoài; `length()` cố định không bảo vệ trước index âm hoặc vượt giới hạn.
3. Nếu nhiều thread cập nhật **cùng slot**, dùng một atomic RMW như `incrementAndGet`/CAS khi cần.
4. Nếu chỉ một thread ghi một slot và nhiều thread đọc, vẫn cần đánh giá publication và memory visibility; atomic arrays cung cấp API phù hợp.
5. Không giả định mỗi index tương ứng một object độc lập trong `AtomicReferenceArray`: hai slot có thể trỏ tới **cùng mutable object**, và mọi mutation của object đó là bài toán khác.
6. Không cho caller giữ đường tham chiếu tới **mảng nguồn** rồi tưởng đang sửa trực tiếp atomic array; constructor đã sao chép storage/các reference.
7. Với index chứa `null` trong reference array, `get(i)` có thể trả `null`; code `updateAndGet` phải xử lý null khi phù hợp, tránh `NullPointerException`.
8. Không viết logic phụ thuộc vào `toString()` của atomic array là snapshot nguyên tử: việc đọc từng phần tử khi có writer có thể thấy nhiều thời điểm.

### Những lỗi thiết kế thường gặp

| Lỗi | Hệ quả | Sửa đúng |
| --- | --- | --- |
| `int[] counts` với `counts[i]++` concurrent | Lost updates | `AtomicIntegerArray.incrementAndGet(i)` |
| `AtomicIntegerArray.get(i)` + `set(i,new)` | Compound action vẫn race | `getAndAdd`, `updateAndGet` hoặc CAS |
| `volatile int[]` rồi `array[i]++` | Volatile reference không làm element atomic | Dùng atomic array/lock |
| `AtomicReferenceArray` chứa `ArrayList` rồi sửa list | List mutation không thread-safe | Immutable value, copy, lock hoặc concurrent collection đúng |
| CAS expected mới dựng mà chỉ `equals` với current | CAS thất bại do reference identity khác | Dùng reference actual từ `get(i)` |
| Update lambda gọi API ngoài tiến trình | Có thể thực hiện side effect nhiều lần vì retry | Hàm update thuần; tách side effect |
| Hai slot được cập nhật bằng hai atomic calls, giả định transaction | Reader thấy trạng thái trung gian | Lock nhóm hoặc AtomicReference tới whole-state |
| Đọc nhiều slot rồi tổng kết khi writer đang chạy | Không phải snapshot nguyên tử | Join trước hoặc thiết kế snapshot |
| Coi `LongAdder.sum()` là snapshot strict | Số liệu có thể không bao hết concurrent updates | AtomicLong/lock nếu cần snapshot nhất quán |
| Dùng nhiều atomic slots mà không xác định index ownership | Khó kiểm soát invariants | Document index mapping, encapsulate API |
| Dùng atomic array cho toàn bộ business workflow có IO | Một slot atomic không bảo vệ side effects | State machine/lock/transaction phù hợp |
| Benchmark một lần rồi nói luôn nhanh hơn | JIT, contention, cache layout và scheduling gây nhiễu | Benchmark đại diện, kiểm chứng correctness trước |

## 14. Hiệu năng: atomic theo slot không có nghĩa không còn tranh chấp

Vì nhiều thread có thể cùng CAS một slot, hiệu năng có thể bị chi phối bởi contention và cache coherence:

- **Cùng index “nóng”**: nhiều thread chạy CAS trên một slot, CAS retry và chuyển cache line có thể khiến throughput giảm.
- **Nhiều index độc lập**: thường giảm tranh chấp về mặt logic, nhưng bố cục bộ nhớ có thể khiến các slot gần nhau chia sẻ cache line (*false sharing*), tùy implementation/JVM/CPU. Không nên khẳng định atomic array đã loại bỏ false sharing.
- **Memory footprint**: Atomic*Array khác với tạo một `AtomicInteger`/`AtomicLong` riêng cho mỗi slot; chi phí bộ nhớ và cơ chế layout phải được đo nếu quan trọng.
- **Workload read-heavy vs write-heavy**: Nếu hầu hết đọc, immutable snapshot + volatile hoặc data structure chuyên biệt đôi khi hiệu quả hơn; nếu write rất nhiều, `LongAdder`/sharding theo nhu cầu thống kê có thể đáng thử.
- **Số lượng slot**: array kích cỡ cố định phù hợp khi mapping ổn định; map đồng thời phù hợp khi index/key động.

Đừng suy đoán từ câu “lock-free thì luôn nhanh”. Hãy đo throughput, p95/p99 latency, CAS failure/retry nếu instrument được, allocations và trạng thái hệ thống trên dữ liệu đại diện. Có thể dùng JMH cho microbenchmark được warmup và chạy nhiều lần.

### Khi nào dùng lớp nào?

| Bài toán | Hướng triển khai |
| --- | --- |
| N bộ đếm `int` độc lập, index cố định | `AtomicIntegerArray` |
| N bộ đếm `long` độc lập, giá trị lớn | `AtomicLongArray` |
| N state immutable theo slot, cần đổi reference nguyên tử | `AtomicReferenceArray<State>` |
| Một bộ đếm duy nhất | `AtomicInteger` / `AtomicLong` |
| Tổng thống kê tăng dưới contention cao, đọc approximate | `LongAdder`/`LongAdder[]` có chính sách publication đúng |
| Toàn cấu trúc cần snapshot consistent, update nhiều field/slot | Khóa chung hoặc `AtomicReference<ImmutableWholeState>` |
| Key động, cập nhật độc lập theo key | `ConcurrentHashMap`/atomic methods |
| Nhiều thao tác ngoài hệ thống phải “all-or-nothing” | Cần giao thức transaction/state-machine khác; atomic array không đủ |

## 15. Tự kiểm tra bằng câu hỏi

**Câu 1.** Bốn thread cùng thực hiện `counts.incrementAndGet(0)` mỗi thread 1.000 lần, sau khi tất cả `join`, kết quả ở slot 0 là bao nhiêu?  
**Đáp án:** `4000`, nếu không có cập nhật khác/exception bên ngoài. Mỗi increment trên slot 0 là RMW atomic.

**Câu 2.** `AtomicLongArray` có tự bảo đảm tổng của slot 0 và slot 1 được đọc như một snapshot không?  
**Đáp án:** Không. Hai `get` là hai operation riêng; writer có thể chen giữa.

**Câu 3.** `AtomicReferenceArray<Person>` có làm `person.age++` atomic không?  
**Đáp án:** Không. Atomicity của slot reference không bảo vệ field mutable của object.

**Câu 4.** Hai `String` có cùng nội dung nhưng khác reference; CAS expected là string mới với `equals` true có thành công không?  
**Đáp án:** Không nếu current vẫn là reference cũ; CAS reference dựa trên identity `==`.

**Câu 5.** `updateAndGet(i, f)` có gọi `f` đúng một lần?  
**Đáp án:** Không được giả định. `f` có thể chạy nhiều lần vì retry dưới contention; phải tránh side effects.

**Câu 6.** Khi nhận `int[] a` ở constructor `new AtomicIntegerArray(a)`, cập nhật `a[0]` có thay đổi atomic slot 0 không?  
**Đáp án:** Không. Giá trị mảng nguồn được sao chép lúc khởi tạo.

**Câu 7.** Khi tạo `AtomicReferenceArray<Mutable>` từ mảng nguồn, nó có sao chép sâu tất cả object không?  
**Đáp án:** Không. Nó sao chép reference theo slot; cùng object mutable có thể vẫn được nhiều nơi dùng.

**Câu 8.** Muốn không cho hai balance có tổng khác 200 ngay cả trong lúc chuyển tiền, có đủ gọi `addAndGet(0,-10)` rồi `addAndGet(1,10)` không?  
**Đáp án:** Không. Dùng cùng một lock bảo vệ update và snapshot, hoặc giữ toàn state trong một reference bất biến được cập nhật atomic.

**Câu 9.** Vì sao `getAndIncrement` và `incrementAndGet` khác nhau?  
**Đáp án:** Đều cập nhật nguyên tử, nhưng method đầu trả giá trị cũ, method sau trả giá trị mới.

**Câu 10.** Một ô bị cập nhật cực nhiều bằng CAS. “Không dùng synchronized” đã chắc chắn hết contention chưa?  
**Đáp án:** Chưa. CAS có thể retry, tốn cache coherence và thread có thể bị trì hoãn; cần đo trên workload thật.

## Tài liệu chính thức

- [`AtomicIntegerArray` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicIntegerArray.html): constructors, per-element arithmetic/CAS, memory ordering.
- [`AtomicLongArray` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicLongArray.html): long updates theo index.
- [`AtomicReferenceArray` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReferenceArray.html): reference swaps/CAS/update theo index.
- [`AtomicReference` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html): thay toàn state bằng một reference.
- [`AtomicStampedReference` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicStampedReference.html): tham chiếu kèm stamp cho một số thuật toán cần version.
- [`LongAdder` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html): lựa chọn thống kê trong contention cao, `sum` không là snapshot atomic.
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): visibility, ordering, happens-before.

**Ghi nhớ:** `AtomicIntegerArray`, `AtomicLongArray` và `AtomicReferenceArray` giúp cập nhật **mỗi slot** bằng API atomic. Phạm vi nguyên tử **không mở rộng tự động** sang nhiều slot, toàn object graph hay side effects. Chọn đúng operation (`incrementAndGet`, `getAndAdd`, `compareAndSet`, `updateAndGet`) và xác định rõ invariant bạn thực sự phải bảo vệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-and-cas-014">Hàm updateAndGet, accumulateAndGet và nguy cơ tác dụng phụ khi CAS phải thử lại</a>

<details>
<summary>Xem chi tiết</summary>

# updateAndGet, accumulateAndGet và nguy cơ tác dụng phụ khi CAS phải thử lại

## 1. Vấn đề: một lời gọi cập nhật không có nghĩa lambda chỉ chạy một lần

Trong Java concurrency, `AtomicInteger`, `AtomicLong` và `AtomicReference<V>` cung cấp các phương thức cập nhật dựa trên **giá trị hiện tại** mà không cần tự viết vòng `compareAndSet` (CAS) cho mỗi trường hợp. Hai phương thức trọng tâm:

~~~java
int updated = counter.updateAndGet(old -> old + 1);

int total = counter.accumulateAndGet(
        5, (current, delta) -> current + delta);
~~~

Cả hai thao tác cập nhật **giá trị atomic**. Tuy nhiên, việc cập nhật nguyên tử **không đồng nghĩa** hàm lambda được gọi đúng một lần. Khi có thread khác cập nhật cạnh tranh, một lần tính toán có thể dựa trên giá trị cũ và thất bại khi commit bằng CAS; JDK có thể **gọi lại hàm** với giá trị mới rồi thử lại.

Vì thế tài liệu JDK yêu cầu hàm `updateFunction` và `accumulatorFunction` là **side-effect-free** (không có tác dụng phụ). Nếu lambda gửi email, ghi log nghiệp vụ, ghi cơ sở dữ liệu, cộng một metric riêng hoặc sửa mutable object, những hành động đó có thể xảy ra **nhiều lần cho một lần cập nhật thành công**.

Bài học này giúp bạn:

- Hiểu chữ ký và kết quả trả về của `updateAndGet`, `getAndUpdate`, `accumulateAndGet` và `getAndAccumulate` trên số nguyên và object reference.
- Giải thích vì sao CAS thất bại dẫn đến retry, và tại sao việc **hàm được đánh giá** khác với việc **candidate đã được commit**.
- Biết quy tắc áp dụng `UnaryOperator` và `BinaryOperator`, thứ tự đối số (`current` trước, `x` sau), điều kiện để phép biến đổi thread-safe.
- Dùng immutable state và immutable input để thiết kế hàm cập nhật thuần, bảo vệ invariant khi nhiều writer tranh chấp.
- Tái hiện một lần cập nhật thực tế nhưng lambda được chạy **hai lần** bằng ví dụ Java 17+ có lịch chạy điều khiển.
- Chọn nơi phù hợp để ghi log, gửi sự kiện hoặc thực hiện I/O; hiểu giới hạn “thực hiện side effect sau CAS thành công”.
- Phân biệt `updateAndGet` với `set`, `getAndSet`, `getAndIncrement`, `LongAdder`, `synchronized` và các phương án đồng bộ khác.

**Phạm vi:** Java SE/JVM concurrency, Java Memory Model (JMM), Atomic & CAS. Các ví dụ chạy độc lập với **JDK 17 trở lên**.

## 2. Bộ API cơ bản và ý nghĩa giá trị trả về

### 2.1. AtomicInteger và AtomicLong

Với `AtomicInteger`:

~~~java
int newValue = atomic.updateAndGet(old -> old + 1);

int oldValue = atomic.getAndUpdate(old -> old + 1);

int newTotal = atomic.accumulateAndGet(
        delta, (current, amount) -> current + amount);

int previous = atomic.getAndAccumulate(
        delta, (current, amount) -> current + amount);
~~~

`AtomicLong` tương tự nhưng dùng kiểu `long`. Hàm một đối số cho `AtomicInteger` là `IntUnaryOperator`, hai đối số là `IntBinaryOperator`; `AtomicLong` dùng `LongUnaryOperator` và `LongBinaryOperator`. Tất cả đều thuộc Java standard library, không cần thêm thư viện.

| API | Đối số của hàm biến đổi | Giá trị trả về | Ghi nhớ |
|---|---|---|---|
| `updateAndGet(fn)` | Giá trị hiện tại `old` | **Giá trị mới** sau lần cập nhật thành công | Update → return new |
| `getAndUpdate(fn)` | Giá trị hiện tại `old` | **Giá trị cũ** trước lần cập nhật thành công | Return old → update |
| `accumulateAndGet(x, fn)` | `fn(current, x)` | **Giá trị mới** | Hợp nhất current với x |
| `getAndAccumulate(x, fn)` | `fn(current, x)` | **Giá trị cũ** | Hợp nhất, nhưng trả old |
| `getAndIncrement()` | Không truyền hàm | Giá trị cũ, rồi tăng 1 nguyên tử | Chuyên biệt cho counter |
| `incrementAndGet()` | Không truyền hàm | Giá trị mới sau tăng 1 nguyên tử | Chuyên biệt cho counter |
| `compareAndSet(expect, next)` | Hai giá trị ứng viên | `true`/`false` | Tự kiểm soát CAS/retry |
| `getAndSet(next)` | Giá trị thay thế | Giá trị cũ | Thay **vô điều kiện**, không tính từ old |

Ở nhóm update functions, giá trị trả về là **giá trị thuộc lần cập nhật đã commit**, không phải giá trị của một lần đánh giá lambda thất bại. Trong thời điểm nhiều thread cập nhật, một lần `get()` khác diễn ra sau đó vẫn có thể quan sát giá trị mới hơn; kết quả trả về không khóa giá trị ở trạng thái mãi mãi.

### 2.2. AtomicReference<V>

Khi giá trị được bảo vệ là **một object reference**, dùng:

~~~java
AtomicReference<State> current = new AtomicReference<>(initial);

State newState = current.updateAndGet(
        old -> new State(old.count() + 1, old.version() + 1));

State oldState = current.getAndUpdate(
        old -> new State(old.count() + 1, old.version() + 1));

State afterMerge = current.accumulateAndGet(
        delta, (old, increment) -> merge(old, increment));
~~~

Đây là các **đoạn trích trong một class/method**; `State`, `initial`, `delta` và `merge` phải được ứng dụng định nghĩa. Với `AtomicReference<V>`, các hàm là `UnaryOperator<V>` và `BinaryOperator<V>`. Do đó `accumulateAndGet(x, function)` nhận **x cùng kiểu V**, khác `AtomicInteger` nhận một `int`.

**Chú ý:** AtomicReference bảo vệ việc thay **tham chiếu**; nó không làm `ref.get().mutableField++` trở thành an toàn. Khi cập nhật object state bằng functional APIs, nên tạo **immutable snapshot mới**, tránh sửa object cũ đang được các reader khác sử dụng.

## 3. CAS loop và lý do một function có thể bị gọi lại

Một mô hình logic dễ hiểu cho `updateAndGet(fn)` là:

~~~java
while (true) {
    Value before = atomic.get();
    Value candidate = fn.apply(before);
    if (atomic.compareAndSet(before, candidate)) {
        return candidate;
    }
    // Có tranh chấp: lấy current mới và thử lại.
}
~~~

Đây là **pseudocode Java-like**, với `Value` là kiểu giả định và `atomic` là atomic holder; không phải implementation nguyên văn của JDK. JDK có thể tối ưu và dùng các biến thể CAS/VarHandle khác nhau. Điều quan trọng từ hợp đồng API là: **function có thể được áp dụng lại** nếu lần cập nhật dự kiến không thành công do contention.

### Interleaving: lambda bị gọi hai lần nhưng chỉ cập nhật một lần

Giả sử `counter = 0`:

| Mốc | Thread A: `updateAndGet(x -> x + 1)` | Thread B | Counter |
|---|---|---|---|
| 1 | Đọc old = 0 | — | 0 |
| 2 | Gọi lambda lần 1, candidate = 1 | — | 0 |
| 3 | — | `incrementAndGet()`, commit 0 → 1 | 1 |
| 4 | CAS expected 0 → 1 **thất bại** | — | 1 |
| 5 | Đọc lại old = 1 | — | 1 |
| 6 | Gọi lambda lần 2, candidate = 2 | — | 1 |
| 7 | CAS expected 1 → 2 **thành công** | — | 2 |

Kết quả của A là **2**, nghĩa là phép tăng của A thực sự được commit **một lần**. Nhưng hàm do A cung cấp được tính **hai lần**. Nếu trong hàm có `sendEmail()` thì có thể gửi hai email. Nếu trong hàm có `auditCounter.incrementAndGet()` thì auditCounter có thể tăng hai lần, dù chỉ có một lần tăng của A vào state chính.

### Sự khác biệt giữa ba loại sự kiện

1. **Lambda được thực thi:** công thức tính một candidate; chưa chắc candidate được publish.
2. **CAS thành công:** candidate đã được commit thành một cập nhật atomic.
3. **Tác dụng phụ bên ngoài:** email, database, log hay sự kiện là hệ thống riêng; có thể thực thi kể cả khi CAS thất bại, và không được rollback bởi CAS.

Tính đúng yêu cầu không nhầm ba sự kiện này.

## 4. Ví dụ chạy độc lập 1: tái hiện lambda chạy hai lần một cách xác định

Ví dụ Java 17+ dùng `CountDownLatch` để ép updater A tính candidate từ 0 rồi tạm dừng. Main tăng counter từ 0 lên 1 trước khi cho A tiếp tục. Vì thế CAS đầu của A phải thất bại; A đọc lại và gọi lambda lần hai. Biến `functionCalls` cố ý là **side effect minh họa lỗi**, không phải mẫu production.

Lưu tệp **AtomicFunctionRetryDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicFunctionRetryDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger value = new AtomicInteger(0);
        AtomicInteger functionCalls = new AtomicInteger(0);
        CountDownLatch firstEvaluation = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);

        Thread updater = new Thread(() -> {
            value.updateAndGet(old -> {
                int callNumber = functionCalls.incrementAndGet();

                if (callNumber == 1) {
                    firstEvaluation.countDown();
                    try {
                        resume.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }

                return old + 1;
            });
        }, "retry-updater");

        updater.start();
        firstEvaluation.await();

        // Main commit một update cạnh tranh trong lúc A tạm dừng.
        value.incrementAndGet();  // 0 -> 1
        resume.countDown();        // A tiếp tục, CAS cũ sẽ thất bại.
        updater.join();

        System.out.println("value=" + value.get());
        System.out.println("functionCalls=" + functionCalls.get());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AtomicFunctionRetryDemo.java
java AtomicFunctionRetryDemo
~~~

Output trong điều kiện không có interrupt bên ngoài:

~~~text
value=2
functionCalls=2
~~~

**Tại sao output có thể dự đoán?**

- `firstEvaluation.await()` ở main chỉ kết thúc sau khi lambda của A đã **được gọi lần đầu**, đọc `old=0` và báo tới latch.
- Trong lần đầu, A chưa trả candidate; A chờ `resume`. Main thực hiện `value.incrementAndGet()`, commit giá trị `1`.
- Main mở `resume`. Khi A tiếp tục thử commit candidate tính từ `0`, CAS mong đợi `0` thất bại vì current là `1`.
- A phải tính lại từ current = `1`, gọi lambda lần nữa; candidate 2 được commit.
- Main `join()` trước khi đọc kết quả. Không dùng `sleep()` để đoán lịch chạy.

**Đây là anti-pattern có chủ đích để giảng dạy:** lambda thực hiện `functionCalls.incrementAndGet()` và gọi `await()` trong lần đầu. Không đưa việc đợi latch, gọi external service, thay đổi metric hoặc side effect như vậy vào hàm cập nhật của hệ thống thật. Nếu một updater bị chặn bên trong callback, nó có thể kéo dài thời gian thao tác, làm tăng contention và gây rủi ro không mong muốn. Bài chỉ dùng latch để minh họa rõ hành vi retry.

## 5. Ví dụ chạy độc lập 2: updateAndGet và accumulateAndGet đúng cách

Ví dụ dưới dùng `AtomicInteger`, trong đó cả hai lambda chỉ tính giá trị mới từ đối số, **không cập nhật biến bên ngoài**.

Lưu tệp **AtomicFunctionBasicsDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicFunctionBasicsDemo {
    public static void main(String[] args) {
        AtomicInteger counter = new AtomicInteger(10);

        int afterUpdate = counter.updateAndGet(old -> old + 2);
        int afterAccumulate = counter.accumulateAndGet(
                5, (current, delta) -> current + delta);

        int oldFromGetAndUpdate =
                counter.getAndUpdate(old -> old * 2);

        int oldFromGetAndAccumulate = counter.getAndAccumulate(
                3, (current, delta) -> current - delta);

        System.out.println("updateAndGet=" + afterUpdate);
        System.out.println("accumulateAndGet=" + afterAccumulate);
        System.out.println("oldUpdate=" + oldFromGetAndUpdate);
        System.out.println("oldAccumulate=" + oldFromGetAndAccumulate);
        System.out.println("final=" + counter.get());
    }
}
~~~

Lệnh:

~~~bash
javac AtomicFunctionBasicsDemo.java
java AtomicFunctionBasicsDemo
~~~

Output:

~~~text
updateAndGet=12
accumulateAndGet=17
oldUpdate=17
oldAccumulate=34
final=31
~~~

Giải thích theo thứ tự **một thread**:
- Bắt đầu `10`.
- `updateAndGet(x -> x + 2)` cập nhật thành `12` và trả `12`.
- `accumulateAndGet(5, (current, delta) -> current + delta)` cập nhật thành `17` và trả `17`.
- `getAndUpdate(x -> x * 2)` thay thành `34` nhưng trả **old=17**.
- `getAndAccumulate(3, (current, delta) -> current - delta)` thay thành `31` nhưng trả **old=34**.
- `get()` cuối đọc `31`.

Với nhiều threads, mỗi lời gọi atomic có một thứ tự cập nhật hợp lệ nhưng **thứ tự giữa các thread không cố định**. Đừng dự đoán output của một bài test concurrent nếu chưa kiểm soát thứ tự.

### Tại sao thứ tự đối số của accumulateAndGet quan trọng?

Hợp đồng hàm hai đối số là:

~~~java
(current, x) -> newValue
~~~

`current` **luôn là giá trị của atomic hiện hành ở lần đánh giá đó**, còn `x` là đối số update bạn truyền. Với phép cộng, đổi thứ tự hai đối số không làm đổi kết quả. Với phép trừ, nối chuỗi hoặc phép chuyển trạng thái không giao hoán, đổi thứ tự sẽ **làm thay đổi logic**.

~~~java
AtomicInteger n = new AtomicInteger(10);
n.accumulateAndGet(3, (current, amount) -> current - amount);
// 10 - 3 = 7
~~~

Đoạn trên là minh họa trong method có import phù hợp, không phải chương trình `main` độc lập. Không viết nhầm `(amount, current) -> amount - current` nếu nghiệp vụ thật cần trừ lượng mới vào số hiện có.

## 6. Hàm cập nhật side-effect-free là thế nào?

Trong bối cảnh này, một lambda tốt nên có tính **thuần (pure) đối với shared state**:
- Chỉ dùng giá trị `old/current` và đối số dữ liệu bất biến của lời gọi.
- Tính kết quả mới **không ghi ra bên ngoài**: không sửa field, List/Map dùng chung, không tăng counter khác, không ghi file, không gửi event.
- Không gọi I/O, transaction, callback có tác dụng phụ hoặc thao tác có thể blocking lâu.
- Không làm thay đổi chính object `old` nếu object này được các readers khác giữ.
- Không phụ thuộc vào mutable state ngoài được thay đổi không đồng bộ giữa các lần retry.
- Dễ đánh giá lại nhiều lần mà vẫn không gây hại cho hệ thống.

Ví dụ tốt:

~~~java
AtomicInteger balance = new AtomicInteger(100);
int newBalance = balance.accumulateAndGet(
        20, (current, amount) -> Math.addExact(current, amount));
~~~

Đoạn trích trên có phép cộng với kiểm soát overflow. `Math.addExact` là tính toán thuần; nếu phát hiện overflow thì ném `ArithmeticException` trước khi commit candidate của lần thử đang xét.

Ví dụ không tốt:

~~~java
AtomicInteger sum = new AtomicInteger();
AtomicInteger logCounter = new AtomicInteger();

sum.updateAndGet(old -> {
    logCounter.incrementAndGet(); // side effect: có thể nhiều hơn 1
    return old + 1;
});
~~~

Cả hai atomic counters riêng đều thread-safe cho từng thao tác, **nhưng toàn bộ nghiệp vụ không có atomicity giữa hai counters**. Khi `sum` gặp contention và callback chạy lại, logCounter có thể tăng nhiều hơn số lần update `sum` thật sự thành công.

### Một hàm chỉ đọc clock/random cũng cần xem xét

~~~java
state.updateAndGet(old ->
        new State(old.value(), System.nanoTime()));
~~~

Lệnh gọi `System.nanoTime()` không trực tiếp ghi shared state trong ứng dụng, nhưng hàm không còn là một transformation ổn định từ cùng `old`: mỗi retry có thể cho timestamp khác. Nếu nghiệp vụ cần thời điểm bắt đầu của **một request** thay vì thời điểm một lần retry, hãy tính timestamp **trước** khi gọi atomic update và truyền giá trị bất biến vào hàm. Tương tự cho random input, generated ID và dữ liệu dùng để đảm bảo idempotency.

Không phải mọi sự không xác định đều làm atomicity sai, nhưng nó làm kết quả khó diễn giải, kiểm tra và khôi phục. Tách lựa chọn đầu vào khỏi pha retry là cách dễ kiểm soát hơn.

## 7. Ví dụ với AtomicReference và immutable object state

Khi cần thay nhiều field có quan hệ, `AtomicReference<State>` + immutable record thường thích hợp. Hàm cập nhật tạo **snapshot mới**, tránh sửa snapshot cũ.

Lưu tệp **AtomicStateFunctionDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicReference;

public class AtomicStateFunctionDemo {
    record Inventory(int available, int reserved) {
        Inventory {
            if (available < 0 || reserved < 0) {
                throw new IllegalArgumentException("negative stock");
            }
        }
    }

    private final AtomicReference<Inventory> current =
            new AtomicReference<>(new Inventory(10, 0));

    Inventory addStock(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        return current.updateAndGet(old -> new Inventory(
                Math.addExact(old.available(), amount),
                old.reserved()));
    }

    Inventory transferToReserved(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        return current.updateAndGet(old -> {
            if (old.available() < amount) {
                return old; // không đủ: không thay đổi trạng thái
            }
            return new Inventory(
                    old.available() - amount,
                    Math.addExact(old.reserved(), amount));
        });
    }

    Inventory snapshot() {
        return current.get();
    }

    public static void main(String[] args) {
        AtomicStateFunctionDemo store = new AtomicStateFunctionDemo();

        store.transferToReserved(3);
        store.addStock(2);
        Inventory after = store.transferToReserved(4);
        Inventory finalState = store.snapshot();

        System.out.println("after=" + after.available()
                + "/" + after.reserved());
        System.out.println("final=" + finalState.available()
                + "/" + finalState.reserved());
    }
}
~~~

Lệnh chạy:

~~~bash
javac AtomicStateFunctionDemo.java
java AtomicStateFunctionDemo
~~~

Output khi main chạy tuần tự:

~~~text
after=5/7
final=5/7
~~~

Diễn biến:
1. Ban đầu `available=10`, `reserved=0`.
2. `transferToReserved(3)` tạo snapshot `(7,3)`.
3. `addStock(2)` tạo snapshot `(9,3)`.
4. `transferToReserved(4)` tạo snapshot `(5,7)`.
5. `snapshot()` trả reference tới một immutable Inventory với **hai field thuộc cùng một phiên bản**.

Nếu nhiều thread cập nhật cùng một instance, mỗi lời gọi `updateAndGet` có thể tính lại từ snapshot mới khi tranh chấp. Vì callback chỉ đọc `old`, `amount` và tạo object mới, retry không gây side effect ngoài. Khi số hàng không đủ, callback trả `old`, nên trạng thái bất biến giữ nguyên.

**Cảnh báo semantic:** `transferToReserved` trả về Inventory, không trả một cờ thành công riêng. Nếu caller cần phân biệt “có đặt được không”, đừng đoán từ identity hay hai lần `get()` ở bên ngoài khi vẫn có writers; hãy thiết kế API CAS loop riêng để trả outcome có nghĩa hoặc đặt kết quả nghiệp vụ trong chính state/transition thích hợp. Hàm cập nhật được chạy lại có thể trả cùng object `old`, và callback không phải nơi được phép ghi log “đã đặt chỗ” trước commit.

### Vì sao không sửa trực tiếp old?

Sai:

~~~java
AtomicReference<MutableInventory> ref = ...;
ref.updateAndGet(old -> {
    old.available -= 1;
    old.reserved += 1;
    return old;
});
~~~

Đây là đoạn minh họa lỗi, `MutableInventory` giả định là mutable class của ứng dụng. Các mutation xảy ra **trước CAS**, có thể thấy bởi reader khác, và có thể lặp nhiều lần nếu CAS retry. AtomicReference còn đang trỏ tới **chính object old**, nên CAS trên cùng reference không ngăn data race trên field. Dùng immutable state với candidate mới hoặc khóa toàn bộ mutation hợp lệ.

## 8. accumulateAndGet: kết hợp current với đầu vào của một lần gọi

Với `AtomicInteger`/`AtomicLong`, `accumulateAndGet(x, function)` thích hợp khi nghiệp vụ là “áp dụng một lượng delta vào state hiện tại”, ví dụ cộng dồn, lấy max/min, cập nhật bitmask hoặc giới hạn tăng giảm.

~~~java
AtomicInteger max = new AtomicInteger(0);

max.accumulateAndGet(12, Math::max);
max.accumulateAndGet(7, Math::max);
// giá trị cuối là 12, không cần khóa riêng cho hai lần cập nhật
~~~

Hàm là `(current, incoming) -> Math.max(current, incoming)`. Nếu không còn writer cạnh tranh ở thời điểm đọc, kết quả là 12.

Ví dụ về một giá trị có giới hạn:

~~~java
AtomicInteger used = new AtomicInteger(0);
int limit = 100;
int updated = used.accumulateAndGet(5,
        (current, amount) -> Math.min(limit, current + amount));
~~~

Đây là minh họa cho giới hạn tổng đơn giản; cần xử lý overflow nếu phép `current + amount` có thể vượt phạm vi int. Đặc biệt, nếu tài nguyên cần trả kết quả **“đã cấp thành công bao nhiêu”** hoặc từ chối atomic khi quá limit, API chỉ trả giá trị mới không tự đáp ứng toàn bộ hợp đồng nghiệp vụ. Bạn có thể cần CAS loop và một return type diễn đạt outcome.

### Phép kết hợp không phải lúc nào cũng giao hoán

Với hai thread gọi `accumulateAndGet` khác nhau:
- Các updates được commit theo một thứ tự tuyến tính hóa hợp lệ.
- Nếu phép hợp nhất giao hoán và kết hợp được, tổng cuối có thể độc lập với thứ tự.
- Nếu là phép không giao hoán như trừ hai lần với chính sách phức tạp, gắn chuỗi, ưu tiên trạng thái hoặc clamp với điều kiện, **kết quả cuối có thể phụ thuộc thứ tự commit**.
- Atomicity của từng operation **không hứa** thứ tự giữa các callers chạy đồng thời.

Tương tự, không phải cứ dùng accumulator là có thể đổi thứ tự operands hoặc tách nhiều transitions thành một mà vẫn giữ nguyên nghiệp vụ.

## 9. Dùng getAndUpdate hay updateAndGet khi nào?

Khác biệt quan trọng nhất là **giá trị trả về**, không phải độ an toàn:

~~~java
AtomicInteger n = new AtomicInteger(10);

int previous = n.getAndUpdate(old -> old + 1); // 10
int updated = n.updateAndGet(old -> old + 1);   // 12
~~~

`previous` biểu thị giá trị trước lần cập nhật atomic tương ứng. Trong concurrency, nếu cần biết số thứ tự duy nhất được cấp cho từng request, đôi khi `getAndIncrement()` phù hợp hơn vì trả số cũ chính xác của từng lần tăng.

Không nên dùng `getAndUpdate` chỉ để “giảm số lần gọi lambda”; nó cũng có thể retry và tính lại function. Hai lựa chọn có cùng nguyên tắc không side effect.

### AtomicReference và việc so sánh bằng identity

`AtomicReference.compareAndSet(expected, newValue)` so sánh current reference với expected theo **`==`**, không dùng `equals()`. Khi một update function tạo snapshot mới từ expected và CAS thất bại, JDK có thể tính lại với reference current mới.

Nếu hai immutable objects có cùng nội dung nhưng là hai references khác nhau, CAS với expected cũ thất bại như thường. Điều này tốt trong các state transition dựa trên version; nhưng cần nhớ vấn đề **ABA** khi reference từng đổi A→B→chính A cũ rồi quay lại. Nếu thuật toán yêu cầu biết mọi thay đổi trung gian, `AtomicStampedReference` hoặc versioned protocol có thể cần thiết. Các convenience update methods không tự giải quyết mọi bài toán ABA.

## 10. Ghi log hay sự kiện ở đâu cho đúng?

Giải pháp tối thiểu là **không thực hiện side effect trong callback**. Thay vì:

~~~java
balance.updateAndGet(old -> {
    sendReceipt(); // có thể bị gọi nhiều lần khi CAS retry
    return old - amount;
});
~~~

hãy tính/cập nhật một cách thuần, sau đó thực hiện side effect ở một vị trí có hợp đồng rõ ràng:

~~~java
int updated = balance.updateAndGet(old -> old - amount);
sendReceiptAfterSuccessfulUpdate(updated);
~~~

Đây là **pseudocode tích hợp**, tên `sendReceiptAfterSuccessfulUpdate` giả định do ứng dụng định nghĩa. Việc đặt send sau khi update thành công **tránh side effect bị lặp chỉ vì callback CAS retry**, nhưng **không tạo transaction atomic** giữa lần cập nhật trong JVM và việc gửi receipt bên ngoài.

Có ít nhất hai trường hợp cần giải quyết:
1. State đã commit, sau đó gửi thông điệp **thất bại**.
2. Process crash sau commit nhưng trước khi gửi; hoặc gửi thành công nhưng client không nhận được xác nhận và gửi lại request.

Nếu nghiệp vụ yêu cầu “đúng một lần” xuyên qua persistence/queue/email, chỉ dùng AtomicInteger hay AtomicReference **không đủ**. Cần thiết kế outbox/transaction/idempotency/durable processing theo hợp đồng của tài nguyên bên ngoài. Không được hứa exactly-once chỉ vì code gọi send sau `updateAndGet`.

### Không được gửi email khi state transition bị từ chối

Ví dụ chuyển tiền không đủ số dư: nếu callback chỉ trả `old` khi không đủ tiền nhưng caller gửi email “thành công” sau khi updateAndGet return, đó vẫn là logic sai. Cần **phân biệt outcome commit và outcome nghiệp vụ** một cách nguyên tử.

Một hướng dùng **CAS loop thủ công**, nơi callback chỉ tính toán, và hàm trả boolean khi kiểm tra điều kiện/commit thành công:

~~~java
boolean withdraw(AtomicInteger balance, int amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException("amount must be positive");
    }

    while (true) {
        int before = balance.get();
        if (before < amount) {
            return false;
        }

        int after = before - amount;
        if (balance.compareAndSet(before, after)) {
            return true;
        }
    }
}
~~~

Đây là method Java hợp lệ trong một class có imports tương ứng. Toàn bộ check-then-decrement được bảo vệ bằng CAS; caller có thể gửi thông báo thành công **chỉ nếu method trả true**. Cũng ở đây, thông báo ngoài JVM vẫn không phải một phần atomic transaction với state; phải thêm giao thức nếu cần độ bền.

### Metric cho attempts khác metric cho successful commits

Việc tăng một bộ đếm `lambdaInvocations` bên trong callback có thể dùng **trong ví dụ kiểm thử để quan sát retry**, nhưng không nên ghi số “business operations completed” ở đó. Nếu cần đo contention, hãy tách metrics **CAS attempts/failures** khỏi **committed updates** và công bố rõ chúng là metrics kỹ thuật. Với tiện ích `updateAndGet`, không nhất thiết có API công khai cho số CAS retries của từng lời gọi; tự viết CAS loop khi cần quan sát/chính sách cụ thể.

## 11. Lambda capture và tính nhất quán của input

Hàm cập nhật thường capture một biến ngoài lambda:

~~~java
int delta = 5;
AtomicInteger total = new AtomicInteger();

total.accumulateAndGet(delta,
        (current, incoming) -> current + incoming);
~~~

`delta` là local effectively final, có một giá trị cố định cho lời gọi. Đó là pattern hợp lý. Khi capture một **tham chiếu tới object mutable**:

~~~java
List<String> changes = mutableList;
state.updateAndGet(old -> calculate(old, changes));
~~~

đoạn này **không tự an toàn**: dù biến `changes` effectively final, nội dung List có thể bị thread khác thay đổi trong lúc lambda chạy hoặc chạy lại, khiến kết quả không xác định. Để tránh, tạo immutable input snapshot **trước khi cập nhật** nếu cần:

~~~java
List<String> immutableChanges = List.copyOf(mutableList);
state.updateAndGet(old -> calculate(old, immutableChanges));
~~~

Đây là các đoạn minh họa trong một method; `calculate` và `state` do ứng dụng định nghĩa. `List.copyOf` bảo vệ cấu trúc List khỏi mutation qua reference gốc ở tầng container; nếu các phần tử của List là mutable objects, phải xét copy sâu/immutable elements.

Các trường hợp đáng lưu ý:
- Đọc đồng hồ, random, UUID hoặc remote data **bên trong** lambda có thể tạo candidate khác nhau qua các lần retry. Nếu cần input nhất quán cho một request, lấy chúng **trước** callback.
- Gọi API không thread-safe từ callback là rủi ro ngay cả khi không trực tiếp mutate atomic state.
- Nếu function được giữ và tái sử dụng, đừng dựa vào mutable shared fields bị thay đổi theo thời điểm để áp cùng một transformation.
- Constructor của immutable candidate cần giữ invariant; nếu bên trong constructor phát sinh side effect, nó cũng có thể được gọi lại khi retry.

## 12. AtomicReference.updateAndGet và nested mutable state

Hãy phân biệt hai việc:

**Đúng:** tạo immutable object mới rồi CAS thay reference.

~~~java
record Position(int x, int y) {}

AtomicReference<Position> position =
        new AtomicReference<>(new Position(0, 0));

position.updateAndGet(old ->
        new Position(old.x() + 1, old.y() + 1));
~~~

Reader có thể lấy một `Position snapshot = position.get()` rồi đọc x/y từ **cùng một phiên bản**.

**Sai:** thay đổi object cũ và trả lại nó.

~~~java
class MutablePosition {
    int x, y;
}

AtomicReference<MutablePosition> position =
        new AtomicReference<>(new MutablePosition());

position.updateAndGet(old -> {
    old.x++;
    old.y++;
    return old;
});
~~~

Trong ví dụ sai, nhiều threads có thể cùng sửa `old` trước bất kỳ CAS nào; reader cũng có thể thấy x đã tăng nhưng y chưa tăng. Nếu hàm phải retry, mutation có thể xảy ra nhiều lần. `AtomicReference` không biến mutable object thành transaction.

**Snapshot chỉ có giá trị khi thực sự immutable.** Với record chứa `List` hoặc `Map` mutable, bên đọc vẫn có thể thấy nội dung bị thay đổi sau publication. Dùng defensive copy, immutable nested values và ownership rõ ràng.

### Các field khác nhau trong cùng object

Nếu một invariant trải qua hai field `available` và `reserved`, hai `AtomicInteger` riêng cũng không tự tạo một operation atomic cho cả cặp. Có thể gói hai giá trị trong một immutable record và thay một reference, hoặc dùng lock chung cho toàn bộ check/update nếu state phức tạp. Atomicity của update function áp dụng cho **giá trị của một atomic variable**, không tự mở rộng sang tài nguyên thứ hai.

## 13. Java Memory Model và giới hạn quan sát

Theo tài liệu JDK, các phương thức update của atomic classes có **memory effects theo `VarHandle.compareAndSet`**; `get()` trên AtomicReference/AtomicInteger có semantics đọc volatile tương ứng, và `set()` có semantics ghi volatile tương ứng.

Khi một writer hoàn tất xây dựng immutable candidate và publish thành công qua atomic update, thread khác đọc được reference đã publish có thể quan sát các ghi phù hợp theo JMM. Tuy nhiên:

- **Happens-before/visibility** không làm cho callback trở thành “chạy một lần duy nhất”.
- Atomic update không cấm thread khác commit một update mới **ngay sau khi** bạn nhận kết quả.
- Không nên đọc một snapshot hai lần để ghép các field của hai phiên bản nếu bạn cần tính nhất quán. Lấy reference một lần, lưu local và đọc đủ field từ đó.
- Nếu object được publish nhưng tiếp tục mutate thiếu đồng bộ, lợi ích safe publication ban đầu không đủ để bảo vệ các mutation sau.
- Một CAS loop có thể tiến bộ toàn cục trong nhiều tình huống, nhưng **không cam kết từng thread riêng lẻ không bị starvation** dưới contention.

Cẩn thận với khẳng định “lock-free nghĩa là không block ở đâu”: update function của người dùng có thể tự thực hiện I/O hoặc chờ lock (dù rất không nên), khiến toàn lời gọi bị block. Các thuật toán atomic chỉ cung cấp memory/atomic semantics cho lõi cập nhật theo API, không kiểm soát mọi việc trong callback.

## 14. Hiệu năng, retry và lựa chọn giải pháp

Các convenience methods giúp code ngắn gọn, nhưng **không miễn phí** dưới tranh chấp:

| Yếu tố | Điều có thể xảy ra | Hành động hợp lý |
|---|---|---|
| Nhiều threads cùng cập nhật một giá trị | CAS thất bại, callback được tính lại | Đảm bảo function thuần, giảm độ phức tạp tính toán mỗi retry. |
| Lambda tạo immutable object lớn | Những candidates thất bại tạo allocation/GC | Đo allocation, cân nhắc mô hình dữ liệu rẻ hơn hoặc lock. |
| Lambda xử lý công việc dài | Retry nhân nhiều lần CPU work | Chuyển công việc độc lập ra trước, giữ transform nhỏ. |
| Nhiều writers và contention rất cao | Throughput/tail latency có thể kém | Benchmark cả Atomic*, Lock, sharding/aggregation theo workload. |
| Counters đơn giản | Lambda có thể không cần thiết | Dùng `incrementAndGet()`, `addAndGet()` theo ý nghĩa. |
| Metric nhiều writers, đọc tổng quan sát | Contention trên một AtomicLong | Cân nhắc `LongAdder`; `sum()` không phải atomic snapshot khi đang cập nhật. |
| Invariant nhiều trường phức tạp | CAS loop khó giữ đúng và candidate copy tốn | Cân nhắc `synchronized` hoặc `ReentrantLock` với vùng bảo vệ đúng. |
| Side effects cùng state | Atomic update không bao trùm tài nguyên ngoài | Thiết kế protocol nghiệp vụ, durability, idempotency. |

### Khi nào dùng AtomicInteger.accumulateAndGet thay cho addAndGet?

Nếu chỉ cộng một lượng delta:

~~~java
int value = counter.addAndGet(delta);
~~~

`addAndGet` rõ nghĩa và không cần callback tùy biến. `accumulateAndGet` phù hợp khi có phép kết hợp như `Math.max`, clamping có kiểm soát hoặc một transition có logic nhiều hơn phép cộng đơn giản. Không nên dùng callback phức tạp cho tất cả các counter chỉ vì API tổng quát hơn.

### Khi nào dùng synchronized?

Nếu update phải bảo vệ nhiều trường mutable, callback khó làm thuần, hoặc chi phí copy ứng viên lớn, một critical section dưới khóa chung có thể đơn giản và phù hợp hơn. Tuy nhiên, giữ lock khi gọi external I/O có thể khiến callers khác chờ lâu. Chọn theo invariant, đo contention và đặc điểm workload; không kết luận CAS luôn nhanh hơn lock hay ngược lại.

## 15. Một ví dụ logic cần tránh: side effect trước khi biết commit

Giả sử muốn “gửi một message cho mỗi lần chuyển state từ PENDING sang COMPLETED”. Sai:

~~~java
status.updateAndGet(old -> {
    if (old == State.PENDING) {
        publishMessage("completed");
        return State.COMPLETED;
    }
    return old;
});
~~~

Đây là đoạn trích không độc lập; `status`, `State` và `publishMessage` được ứng dụng định nghĩa. Với contention, callback có thể đọc PENDING, gửi message, rồi CAS thất bại vì thread khác đã chuyển state. Khi retry, callback lại gặp COMPLETED; kết quả cuối không có commit PENDING→COMPLETED từ caller ban đầu, nhưng message đã bị gửi.

Một cách tiếp cận trong **một JVM** là dùng `compareAndSet(State.PENDING, State.COMPLETED)` để biết **thread nào giành quyền transition**:

~~~java
boolean transitioned =
        status.compareAndSet(State.PENDING, State.COMPLETED);

if (transitioned) {
    publishMessage("completed");
}
~~~

Đoạn minh họa giả định State là enum; CAS dựa trên identity của constants enum. Cách này tránh publishMessage khi caller **không** thành công trong CAS, nhưng có giới hạn rất quan trọng:

- Nếu process crash sau CAS thành công nhưng trước publishMessage, event bị bỏ lỡ.
- Nếu publishMessage đã gửi nhưng xác nhận bị mất, retry có thể dẫn đến gửi lặp ở cấp hệ thống ngoài.
- AtomicReference/AtomicInteger không làm giao dịch của hệ thống khác trở nên atomic với RAM.
- Nếu request đến lần nữa sau khi state đã COMPLETED, caller nên xử lý idempotently.

Muốn đảm bảo thông báo bền vững, cần giao thức transaction/outbox hoặc một thiết kế có cam kết phù hợp ở hệ thống lưu trữ; không hứa exactly-once xuyên mọi tài nguyên chỉ bằng CAS.

## 16. Các lỗi phổ biến và cách sửa

**Lỗi 1 — Hiểu “atomic update” thành “lambda chạy đúng một lần”.** Sai; callback có thể được đánh giá lại sau thất bại do contention. Ghi nhận candidate computation và commit là hai sự kiện khác nhau.

**Lỗi 2 — Gửi email, log nghiệp vụ hoặc tăng counter phụ trong lambda.** Side effects có thể xảy ra nhiều lần. Đưa side effects ra ngoài callback và thiết kế commit/outcome cùng retry bền vững nếu cần.

**Lỗi 3 — Đọc old bằng `get()`, tính toán rồi gọi `set(candidate)`.** Dù get/set riêng lẻ atomic, toàn bộ read–modify–write không atomic, có thể lost update. Dùng update function hoặc CAS loop.

**Lỗi 4 — Nhầm `getAndUpdate` với `updateAndGet`.** Phương thức đầu trả **old**, phương thức sau trả **new**; cả hai có thể retry.

**Lỗi 5 — Đảo ngược đối số của accumulateAndGet.** Thứ tự luôn là `(current, x)`. Với phép không giao hoán, đảo thứ tự làm sai logic.

**Lỗi 6 — Lấy `AtomicReference.get()` rồi sửa object mutable.** Reference an toàn không làm các field mutable an toàn. Dùng immutable snapshots hoặc đồng bộ khác.

**Lỗi 7 — Để function thay đổi old object rồi return old.** Mutation có thể lộ trước commit và lặp trên retry. Tạo candidate object mới, không sửa phiên bản đã publish.

**Lỗi 8 — Capture mutable List/Map/array từ ngoài callback.** Giá trị lambda có thể thay đổi qua các lần retry; dùng immutable input snapshot hoặc ownership rõ ràng.

**Lỗi 9 — Nhầm return từ updateAndGet với “tôi sở hữu giá trị hiện tại mãi”.** Thread khác có thể cập nhật ngay sau đó; kết quả chỉ gắn với điểm commit của lời gọi.

**Lỗi 10 — Dùng updateAndGet để thực hiện check-then-act với outcome nhập nhằng.** Nếu callback trả old khi không đủ điều kiện, caller cần cách diễn đạt chính xác có transition hay không; cân nhắc CAS loop trả boolean/outcome.

**Lỗi 11 — Không kiểm tra overflow.** `AtomicInteger` và `AtomicLong` không tự chống overflow. Nếu invariant yêu cầu không wrap, dùng `Math.addExact` hoặc validation thích hợp.

**Lỗi 12 — Đếm số lần callback chạy để suy ra số cập nhật thành công.** Hai con số có thể khác nhau vì CAS retry. Chỉ đếm commit theo contract thích hợp.

**Lỗi 13 — Tưởng phép cập nhật nhiều AtomicReferences là một transaction.** Chúng là các atomic variables riêng; invariant nhiều field có thể cần một immutable snapshot hoặc lock chung.

**Lỗi 14 — Tối ưu sớm bằng CAS callback rất nặng.** Trong contention, mỗi lần thất bại lãng phí việc tạo object/tính toán; đo thực tế trước khi chọn.

**Lỗi 15 — Cho rằng một bài stress test đúng là bằng chứng không có race.** Lịch chạy, JIT và CPU khác nhau có thể cho interleaving khác. Kiểm tra bằng case xác định, invariant và nhiều mức contention.

## 17. Checklist review trước khi merge code

1. **Atomic variable nào?** Một `AtomicInteger`, `AtomicLong` hay một `AtomicReference<State>` thật sự là nguồn state dùng chung?
2. **Hàm có thuần không?** Không I/O, không mutation ra ngoài, không log nghiệp vụ, không update metric phụ, không sửa old object?
3. **Có thể retry không?** Đã giả định đúng rằng function được gọi 1..N lần cho một successful update?
4. **Kết quả trả về là old hay new?** `getAnd*` trả old; `*AndGet` trả new.
5. **accumulator có thứ tự đúng?** `(current, incoming)`, không đảo operands với phép không giao hoán.
6. **Input bất biến?** Captured args có bị thay đổi đồng thời không? Immutable snapshot đủ sâu chưa?
7. **Invariant được bảo vệ ở đâu?** Candidate có được validation và publish qua một atomic commit phù hợp không?
8. **Có side effect sau commit không?** Nếu có, đã xử lý khả năng crash, gửi trùng, mất event hoặc lỗi ngoài JVM chưa?
9. **Có phân biệt business rejection với CAS contention không?** Caller phải biết có áp dụng thay đổi hay không.
10. **Có overflow và exception không?** Hàm ném trước commit; nếu nó đã có side effect thì không tự rollback.
11. **Đã kiểm tra contention thực tế chưa?** CAS retries, CPU, allocation/GC, throughput và p95/p99 latency?
12. **Một update có phải thật sự cần lambda không?** Với phép đơn giản, ưu tiên API chuyên biệt như `addAndGet`.

## 18. Bài tập thực hành và đáp án

**Câu 1.** Hai thread cùng gọi `AtomicInteger.updateAndGet(x -> x + 1)` trên cùng counter. Mỗi lời gọi có thể chạy lambda mấy lần?

**Đáp án:** Có thể một hoặc nhiều lần. Một thread có thể tính từ giá trị cũ, thua CAS, rồi gọi lại lambda với giá trị mới. Mỗi lời gọi thành công chỉ commit một update, không bảo đảm chỉ một lần đánh giá lambda.

**Câu 2.** `AtomicInteger.getAndUpdate(x -> x + 10)` trả giá trị nào?

**Đáp án:** Giá trị **cũ** ngay trước lần cập nhật đã commit. `updateAndGet` sẽ trả giá trị **mới**.

**Câu 3.** `n.accumulateAndGet(3, (current, amount) -> current - amount)` với n ban đầu 10, không có tranh chấp, trả gì?

**Đáp án:** 7. Đối số đầu callback là current=10, đối số thứ hai là amount=3.

**Câu 4.** Vì sao `emailService.send()` bên trong `updateAndGet` là nguy hiểm, dù state atomic cuối đúng?

**Đáp án:** Callback có thể được áp dụng lại khi CAS thất bại, nên email gửi nhiều lần; CAS thất bại không hoàn tác email đã gửi.

**Câu 5.** Một lambda không có side effect nhưng đọc một mutable List từ thread khác. Có đủ an toàn không?

**Đáp án:** Chưa chắc. Input có thể đổi giữa các lần retry, hoặc chính việc đọc List không thread-safe. Cần ownership, immutable snapshot hoặc cơ chế đồng bộ thích hợp.

**Câu 6.** Vì sao `AtomicReference<State>` có thể cập nhật hai field nguyên tử với immutable record, nhưng `AtomicReference<MutableState>` với mutation field thì không?

**Đáp án:** Record mới được tạo hoàn chỉnh rồi publish bằng **một reference swap**; reader giữ một phiên bản bất biến. Với MutableState, field thay đổi **bên trong object đang chia sẻ**, không qua CAS của reference.

**Câu 7.** Nếu lambda có thể bị gọi lại, làm sao tạo một request ID cố định cho cả lời gọi?

**Đáp án:** Tạo ID **trước khi** bắt đầu update, đưa nó làm immutable input vào callback. Không phát ID mới cho mỗi retry nếu business contract yêu cầu một ID trên một request.

**Câu 8.** Sau một `updateAndGet` thành công rồi gửi event ngoài JVM, có bảo đảm event đúng một lần bất kể process crash không?

**Đáp án:** Không. Atomic state trong JVM và hệ thống ngoài là hai tài nguyên; cần persistence/transaction/idempotency/outbox hoặc thiết kế tương ứng nếu yêu cầu độ bền và phát sự kiện đáng tin cậy.

## 19. Tài liệu API gốc

- **AtomicInteger (Java SE 21):** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html
- **AtomicLong (Java SE 21):** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html
- **AtomicReference (Java SE 21):** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html
- **AtomicStampedReference (Java SE 21):** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/AtomicStampedReference.html
- **VarHandle (Java SE 21):** https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/VarHandle.html
- **JLS `17 — Java Memory Model:** https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html

**Kết luận:** `updateAndGet` và `accumulateAndGet` giúp diễn đạt **atomic read–modify–write** ngắn gọn; `getAndUpdate` và `getAndAccumulate` có cùng tính nguyên tử nhưng trả kết quả **trước** cập nhật. Trong mọi trường hợp, hàm callback có thể chạy **nhiều lần** do CAS retry: chỉ dùng **phép biến đổi thuần** từ current và immutable input để tạo candidate. Side effects, business outcome, retry bền vững và transaction ngoài JVM phải được xử lý bằng giao thức riêng, không đặt cược vào một lời gọi lambda chạy đúng một lần.


</details>

- [Quay lại đầu trang](#back-to-top)

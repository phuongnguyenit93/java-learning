<a id="back-to-top"></a>

# Race Conditions

## Menu
- [Shared mutable state tạo ra tranh chấp ra sao?](#race-conditions-001)
- [Interleaving và tính không xác định của kết quả](#race-conditions-002)
- [Lost update trong thao tác tăng biến đếm](#race-conditions-003)
- [Read-modify-write không phải một thao tác atomic](#race-conditions-004)
- [Check-then-act và lỗi kiểm tra rồi thao tác](#race-conditions-005)
- [Broken invariant khi cập nhật nhiều biến liên quan](#race-conditions-006)
- [TOCTOU và lỗi đồng thời theo thời điểm](#race-conditions-007)
- [Không an toàn khi dùng collection thường chung nhiều luồng](#race-conditions-008)
- [Phân biệt data race, race condition và logic race](#race-conditions-009)
- [Minh họa lỗi bằng test điều phối thời điểm thực thi](#race-conditions-010)
- [Phương án sửa: ownership, locking, atomic, immutable state](#race-conditions-011)

## <a id="race-conditions-001">Shared mutable state tạo ra tranh chấp ra sao?</a>

<details>
<summary>Xem chi tiết</summary>

# Shared mutable state tạo ra tranh chấp (race condition) ra sao trong Java?

Khi nhiều thread cùng truy cập **trạng thái có thể thay đổi** (*shared mutable state*), kết quả có thể phụ thuộc vào thứ tự và thời điểm các thao tác xen kẽ. Nếu thiết kế không có quy tắc đồng bộ phù hợp, ứng dụng có thể mất dữ liệu cập nhật, đọc giá trị cũ, phá vỡ điều kiện bất biến hoặc cho ra kết quả khác nhau giữa các lần chạy. Đây là nguồn gốc của nhiều **race condition** trong Java concurrency.

Bài học tập trung vào Java thuần và Java Memory Model (JMM), không phụ thuộc vào framework. Mục tiêu là hiểu **điều kiện nào khiến một dữ liệu trở nên nguy hiểm khi chia sẻ**, vì sao thao tác tưởng chừng đơn giản như `count++` có thể sai, cách chọn công cụ bảo vệ và cách phát hiện lỗi.

## 1. Ba từ khóa: shared, mutable, concurrent

- **Shared (được chia sẻ):** ít nhất hai thread có thể truy cập cùng một vùng trạng thái. Ví dụ, cùng tham chiếu tới một instance `Counter`, một `static` field, một collection hoặc một đối tượng được đưa vào executor.
- **Mutable (có thể thay đổi):** ít nhất một thread có thể sửa trạng thái đó sau khi tạo. Một tham chiếu `final` không đồng nghĩa nội dung object bên trong là bất biến.
- **Concurrent access (truy cập đồng thời về mặt thời gian):** các thread có thể thực hiện thao tác với trạng thái chia sẻ mà không có một thứ tự hoàn toàn được áp đặt bởi chương trình.

Nếu dữ liệu chỉ nằm trong local variable của một thread và không bị chia sẻ qua object/closure, thông thường không có tranh chấp trên chính local variable đó. Nếu một object được chia sẻ nhưng **thực sự bất biến** và đã được công bố an toàn (*safely published*), nhiều thread có thể đọc mà không cần khóa trên mỗi lần đọc.

Ngược lại, nếu ít nhất một thread ghi dữ liệu mà các thread khác cũng đọc hoặc ghi, ta phải xem xét **tính nguyên tử (atomicity), khả năng nhìn thấy (visibility), thứ tự bộ nhớ (ordering)** và tính nhất quán của **nhiều field liên quan**.

### Ví dụ dễ gặp

```java
class Counter {
    int value;

    void increment() {
        value++;
    }
}
```

Đây là **đoạn minh họa chưa có `main`**. Mỗi thread gọi `increment()` trên **cùng một instance** sẽ tranh chấp trường `value`. Ngay cả khi `value++` viết trên một dòng Java, nó không phải thao tác cập nhật nguyên tử.

## 2. Race condition khác data race thế nào?

**Race condition** là lỗi mà tính đúng đắn phụ thuộc vào thứ tự/timing của những hành động có thể xen kẽ. Khái niệm rộng này bao gồm logic kiểm tra rồi hành động, công bố sự kiện sai thứ tự, hoặc cập nhật trạng thái thiếu một giao thức đồng bộ.

**Data race** trong mô hình bộ nhớ Java xuất hiện khi hai thao tác truy cập cùng một biến, ít nhất một là ghi, mà giữa hai thao tác **không có quan hệ happens-before** bảo đảm thứ tự. Các thao tác đọc/ghi thường trên field chia sẻ không được bảo vệ có thể tạo data race.

Hai khái niệm liên quan nhưng **không đồng nhất**:

- `int count` được hai thread tăng bằng `count++` không đồng bộ: vừa là data race vừa thường là race condition dẫn đến **lost update**.
- Một `ConcurrentHashMap` có thể an toàn cho từng `get`/`put`, nhưng hai thread cùng chạy “kiểm tra chưa có key rồi mới thêm” vẫn có **race ở cấp nghiệp vụ**, dù collection bảo vệ các thao tác đơn lẻ.
- Một giao thức cập nhật gồm nhiều biến có thể vẫn sai về logic dù từng biến được khai báo `volatile` hoặc dùng atomic riêng.
- Hai thread chỉ đọc một object bất biến đã được chia sẻ đúng cách không tạo race chỉ vì chạy đồng thời.

**Không phải mọi thay đổi thứ tự đều là lỗi.** Nếu hệ thống cho phép bất kỳ thứ tự nào và mọi kết quả đều đáp ứng hợp đồng, đó là concurrency hợp lệ. Race condition đáng sửa khi thứ tự xen kẽ có thể làm vi phạm yêu cầu.

## 3. `count++` thực chất là read–modify–write

Ở cấp ý nghĩa, `count++` bao gồm ít nhất ba bước:

1. **Read:** đọc giá trị hiện tại `count`.
2. **Compute:** tính `count + 1`.
3. **Write:** ghi kết quả mới trở lại `count`.

Giả sử hai thread A và B cùng thấy `count = 0`:

| Bước | Thread A | Thread B | Giá trị chia sẻ |
| --- | --- | --- | --- |
| 1 | Đọc `0` | | `0` |
| 2 | | Đọc `0` | `0` |
| 3 | Tính `1` và ghi `1` | | `1` |
| 4 | | Tính `1` và ghi `1` | `1` |

Hai lần tăng hợp lệ về nghiệp vụ phải tạo `2`, nhưng cuối cùng chỉ có `1`. Một lần cập nhật đã bị **ghi đè/mất**. Đây là *lost update*.

Không được giả định compiler/JIT/CPU bắt buộc thực thi đúng ba lệnh máy như mô tả. Bảng là mô hình logic của phép read–modify–write: **Java không bảo đảm `count++` trên một field thường là nguyên tử**, nên kết quả sai có thể xảy ra khi hai thread chạy trên cùng dữ liệu.

### Thí nghiệm Java 17: tạo lost update xác định

Đoạn dưới dùng hai `CountDownLatch` để bảo đảm **cả hai thread đọc `0` trước khi bất kỳ thread nào ghi**. Nhờ đó, minh họa không phụ thuộc vào may rủi của scheduler và có stdout cố định.

Lưu thành `LostUpdateExample.java`:

```java
import java.util.concurrent.CountDownLatch;

public class LostUpdateExample {
    static class Counter {
        int value = 0;
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch mayWrite = new CountDownLatch(1);

        Runnable increment = () -> {
            int snapshot = counter.value;
            bothRead.countDown();

            try {
                mayWrite.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            counter.value = snapshot + 1;
        };

        Thread a = new Thread(increment, "worker-A");
        Thread b = new Thread(increment, "worker-B");

        a.start();
        b.start();

        bothRead.await();
        mayWrite.countDown();

        a.join();
        b.join();
        System.out.println("Ket qua: " + counter.value);
    }
}
```

Chạy:

```bash
javac LostUpdateExample.java
java LostUpdateExample
```

Stdout khi chạy bình thường, không bị ngắt từ bên ngoài:

```text
Ket qua: 1
```

**Vì sao kết quả ổn định?** Mỗi worker lưu kết quả đọc vào local variable `snapshot` trước khi báo `bothRead.countDown()`. Main chỉ mở cổng ghi `mayWrite.countDown()` sau khi cả hai đọc xong. Như vậy, mỗi worker dùng lại snapshot bằng `0` rồi ghi `1`. `join()` bảo đảm main đợi cả hai kết thúc trước khi in.

**Lưu ý quan trọng về mẫu thí nghiệm:** các latch đã tạo quan hệ đồng bộ đủ để kiểm soát lịch đọc và ghi, nên không dùng chính ví dụ này để khẳng định có data race theo định nghĩa hình thức của JMM trên từng truy cập `value`. Nó **chứng minh sự thất bại của thao tác cập nhật logic** khi tách read và write, ngay cả khi các bước được sắp thứ tự có chủ đích. Trong code thực tế, gọi `counter.value++` từ nhiều thread mà không đồng bộ là tình huống có data race và lost update có thể xuất hiện không dự đoán được. Thí nghiệm trên cố tình ép thứ tự nguy hiểm để minh họa sự cố.

## 4. Những lỗi còn lại: visibility, ordering và invariant

### 4.1. Visibility: thread không nhất thiết thấy bản ghi mới ngay

```java
class WorkerState {
    boolean stopped = false;
}
```

Nếu thread A đổi `stopped = true` trong khi thread B lặp đọc `stopped` mà không có đồng bộ, JMM **không bảo đảm** B quan sát giá trị mới kịp thời, hoặc thậm chí quan sát trước khi vòng lặp kết thúc. JIT và CPU có thể tối ưu, giữ dữ liệu trong register/cache, hoặc thực hiện các thao tác bộ nhớ theo các thứ tự hợp lệ khi thiếu happens-before. Không nên giải thích lỗi bằng cách khẳng định mọi CPU đều “cache mãi không refresh”; điểm cốt lõi là **thiếu bảo đảm của mô hình bộ nhớ**.

Một giải pháp cho **cờ trạng thái đơn giản**:

```java
class WorkerState {
    volatile boolean stopped = false;
}
```

Một phép ghi `volatile` và phép đọc `volatile` tương ứng tạo các bảo đảm visibility và ordering cần thiết. Nhưng `volatile` **không tự làm `count++` nguyên tử**, cũng không bảo vệ một chuỗi điều kiện và cập nhật trên nhiều field.

### 4.2. Ordering và happens-before

JMM cho phép JVM/CPU sắp xếp lại một số thao tác miễn là vẫn tuân thủ các ràng buộc của mô hình bộ nhớ. Không có happens-before, thread khác có thể quan sát trạng thái không đúng với giả định “mọi dòng lệnh đều xuất hiện theo đúng thứ tự trong source”.

**Happens-before** là quan hệ cho phép suy luận về visibility và thứ tự giữa các hành động:

- **Program order:** trong cùng một thread, hành động trước happens-before hành động sau theo thứ tự chương trình.
- **Monitor:** giải phóng monitor bằng `synchronized` happens-before lần lấy monitor đó sau này.
- **Volatile:** ghi vào một field `volatile` happens-before lần đọc tiếp theo của chính field đó theo quy tắc đồng bộ.
- **Start:** gọi `Thread.start()` happens-before các hành động trong thread được khởi động.
- **Termination/join:** các hành động của thread kết thúc happens-before thread khác phát hiện kết thúc thông qua `join()` hoàn thành thành công.
- **Các tiện ích `java.util.concurrent`:** ví dụ, hành động trước `ExecutorService.submit` có bảo đảm truyền đến hành động của task, và hành động task xảy ra trước việc caller lấy kết quả bằng `Future.get()` theo hợp đồng memory consistency của API.

Happens-before có tính **bắc cầu**. Nó khác thứ tự thời gian trên đồng hồ: “A chạy sớm hơn B” chưa đủ để tạo quan hệ happens-before giữa hai thread.

### 4.3. Invariant: cập nhật nhiều field phải thống nhất

Giả sử object lưu `available` và `reserved` với bất biến `available + reserved == total`. Nếu một thread cập nhật từng field riêng lẻ và thread khác đọc xen vào, reader có thể thấy **trạng thái chuyển tiếp** không thỏa bất biến, ngay cả khi các phép ghi từng field riêng biệt nguyên tử.

Các cách bảo vệ phù hợp gồm: giữ **cùng một khóa** cho toàn bộ thao tác cập nhật và đọc snapshot; đưa các field vào một **immutable snapshot object** rồi công bố bằng một reference an toàn; hoặc thiết kế thao tác atomic có đủ dữ liệu để bảo toàn invariant. Dùng hai `AtomicInteger` độc lập không tự khiến một transaction gồm cả hai field trở thành nguyên tử.

## 5. Sửa lost update bằng `synchronized`

`synchronized` tạo mutual exclusion trên một monitor: tại một thời điểm, chỉ một thread sở hữu monitor đó và chạy critical section được bảo vệ. Việc nhả/lấy monitor đồng thời cung cấp quan hệ happens-before cho visibility.

Lưu thành `SynchronizedCounterExample.java`:

```java
public class SynchronizedCounterExample {
    static class Counter {
        private int value = 0;

        synchronized void increment() {
            value++;
        }

        synchronized int getValue() {
            return value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();

        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                counter.increment();
            }
        };

        Thread first = new Thread(task);
        Thread second = new Thread(task);

        first.start();
        second.start();

        first.join();
        second.join();

        System.out.println("Ket qua: " + counter.getValue());
    }
}
```

```bash
javac SynchronizedCounterExample.java
java SynchronizedCounterExample
```

Stdout:

```text
Ket qua: 200000
```

Ở đây **cả hai thread gọi cùng instance `counter`**, nên cùng sử dụng monitor `this` khi gọi `increment()`. Không thể có hai thread cùng thực hiện `value++` trong critical section tại một thời điểm; 200.000 lần tăng được giữ nguyên. `getValue()` cũng dùng khóa đó; `join()` còn bảo đảm main đã đợi worker kết thúc.

**Các điều kiện cần nhớ:**

- Tất cả đường đọc/ghi tới state đang bảo vệ phải tuân theo **cùng một quy tắc khóa**. Nếu code khác vẫn đọc trực tiếp `value` mà không đồng bộ, thiết kế có thể tiếp tục sai.
- `synchronized` trên **hai instance khác nhau** là hai monitor khác nhau. Không bảo vệ được một `static` mutable field chung nếu mỗi thread khóa một object riêng.
- Hạn chế critical section: không giữ khóa khi gọi HTTP/DB, `sleep` hoặc chờ tác vụ khác nếu không cần; khóa lâu gây contention, latency, có thể dẫn tới deadlock.
- `synchronized` hỗ trợ reentrancy; ra khỏi khối dù ném exception cũng tự nhả monitor, nhưng trạng thái nghiệp vụ đã cập nhật một phần vẫn có thể cần xử lý.

## 6. Sửa counter đơn lẻ bằng `AtomicInteger`

Khi state cần bảo vệ chỉ là một phép tăng/giảm hoặc compare-and-set đơn giản, `AtomicInteger` cung cấp các thao tác cập nhật nguyên tử với memory semantics được quy định, thường phù hợp hơn một monitor do ta tự quản lý.

Lưu thành `AtomicCounterExample.java`:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterExample {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger count = new AtomicInteger();

        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                count.incrementAndGet();
            }
        };

        Thread first = new Thread(task);
        Thread second = new Thread(task);

        first.start();
        second.start();

        first.join();
        second.join();

        System.out.println("Ket qua: " + count.get());
    }
}
```

```bash
javac AtomicCounterExample.java
java AtomicCounterExample
```

Stdout:

```text
Ket qua: 200000
```

`incrementAndGet()` thực hiện phép tăng nguyên tử; mỗi lần tăng hợp lệ được tính đúng một lần. Dưới tranh chấp, triển khai có thể phải thử lại cập nhật. `AtomicInteger` không khiến cả một chuỗi “đọc giá trị → gọi API → ghi trạng thái khác” trở thành atomic transaction.

**`volatile int value; value++;` vẫn sai** vì `volatile` bảo đảm visibility/ordering cho từng truy cập field, không gộp read–modify–write thành một hành động nguyên tử.

### `LongAdder` có hợp lý hơn?

Khi rất nhiều thread cùng cộng một bộ đếm thống kê, `LongAdder` có thể cải thiện throughput dưới contention bằng cách phân tán cập nhật. Tuy nhiên, `sum()` **không phải snapshot nguyên tử** đối với các cập nhật đang đồng thời. Dùng nó cho metrics/counters chịu tải cao khi không cần giá trị chính xác tuyến tính hóa tại mọi thời điểm; với điều kiện nghiệp vụ cần “nếu số dư đủ thì trừ”, `LongAdder` không phải giải pháp.

## 7. `Lock`, `ReentrantLock` và điều kiện đồng bộ

`java.util.concurrent.locks.ReentrantLock` có thể thay monitor nội tại khi cần `tryLock`, timeout, khóa interruptible hoặc nhiều `Condition`. Nó cũng cần một quy tắc dùng **cùng lock** cho mọi truy cập cùng state.

Đoạn mã minh họa (không phải chương trình độc lập):

```java
private final ReentrantLock lock = new ReentrantLock();
private int balance = 100;

public boolean withdraw(int amount) {
    if (amount < 0) throw new IllegalArgumentException();
    lock.lock();
    try {
        if (balance < amount) return false;
        balance -= amount;
        return true;
    } finally {
        lock.unlock();
    }
}
```

Thêm import `java.util.concurrent.locks.ReentrantLock` và đặt các field/phương thức trong một class. Điều kiện kiểm tra và việc trừ tiền **phải nằm trong cùng critical section**. Nếu đưa `if (balance < amount)` ra ngoài khóa, hai thread có thể cùng thấy đủ tiền rồi cùng rút.

Với `ReentrantLock`, nhất thiết mở khóa trong `finally`; quên `unlock()` có thể làm thread khác đợi vô hạn. Nếu sử dụng nhiều lock, quy định thứ tự lấy lock thống nhất để giảm nguy cơ deadlock.

## 8. Một collection thread-safe vẫn có thể có race ở cấp nghiệp vụ

Xem ý tưởng sau:

```java
if (!map.containsKey(key)) {
    map.put(key, createValue());
}
```

Ngay cả khi `map` là `ConcurrentHashMap`, hai thread vẫn có thể cùng quan sát key chưa tồn tại rồi cùng tính `createValue()`, sau đó ghi đè kết quả của nhau. **Mỗi thao tác riêng an toàn, chuỗi thao tác lại chưa atomic.**

Nếu mục tiêu là tính giá trị theo key khi chưa có, hãy cân nhắc:

```java
Value value = map.computeIfAbsent(key, k -> createValue());
```

Các snippet chỉ minh họa; giả định `map` có kiểu `ConcurrentHashMap<Key, Value>` và `createValue()` trả `Value` hợp lệ. `computeIfAbsent` của `ConcurrentHashMap` thực hiện thao tác cập nhật tương ứng theo bảo đảm atomic của API; hàm mapping nên ngắn, không gây recursion cập nhật cùng map và tránh side effect khó lặp lại. Nếu việc tạo giá trị đòi hỏi gọi mạng/DB dài hoặc tạo side effect bên ngoài, phải thiết kế cẩn thận về khóa, lỗi, retry và idempotency.

Các trường hợp tương tự cần thao tác atomic có chủ đích:

- `if (!list.contains(item)) list.add(item)`: check-then-act có thể trùng phần tử dù dùng `Collections.synchronizedList` mà không giữ khóa quanh cả chuỗi.
- `if (stock > 0) stock--`: kiểm tra và giảm tồn kho phải thống nhất; chỉ `volatile` không đủ.
- `map.get(key)` rồi `map.put(key, transformed)`: là read–modify–write; ưu tiên `compute`/`merge` khi đúng semantics.
- Trạng thái order/booking nhiều bước: một phép atomic trên một field chưa đủ giữ toàn bộ invariant.

## 9. Các cách giảm hoặc tránh shared mutable state từ thiết kế

### 9.1. Thread confinement: không chia sẻ dữ liệu cần sửa

Dữ liệu chỉ thuộc một thread hoặc một task, các phép cập nhật nội bộ không tranh với thread khác:

```java
Runnable job = () -> {
    int localCount = 0;
    for (int i = 0; i < 10; i++) {
        localCount++;
    }
    System.out.println(localCount);
};
```

Đây là đoạn minh họa chưa có `main`. Nếu mỗi task có một `localCount` riêng thì không có data race trên biến đó. **Nhưng các object mà local variable tham chiếu tới vẫn có thể bị chia sẻ**; local reference không biến object chia sẻ thành private.

### 9.2. Immutability và defensive copying

Ưu tiên giá trị bất biến: record với các thành phần bất biến, `String`, cấu trúc value object, immutable collection hoặc immutable snapshot. `List.copyOf(source)` tạo danh sách không cho sửa cấu trúc qua reference được trả về, nhưng **không deep-copy các phần tử mutable bên trong**. `final List<Item>` cũng chỉ ngăn gán lại reference, không ngăn thay đổi list.

Đối tượng bất biến vẫn cần được **công bố an toàn** khi đưa từ thread tạo sang thread dùng. Không để tham chiếu `this` “thoát ra ngoài” constructor trước khi xây dựng hoàn tất; dùng cơ chế chuyển giao có happens-before (queue, synchronized, volatile reference, concurrent collection, hoặc fork/join theo hợp đồng API).

### 9.3. Message passing, ownership và partition

Chia state theo key/shard, giao mỗi phần cho một worker quản lý; các thread khác gửi message qua `BlockingQueue` thay vì cùng sửa một object. Thiết kế này đổi bài toán “ai giữ lock?” thành “ai sở hữu state và message nào được phép cập nhật?”. Mọi giới hạn queue, xử lý shutdown và error recovery vẫn cần được thiết kế.

### 9.4. Immutable snapshot thay cho nhiều field mutable

Nếu UI/readers cần quan sát một nhóm giá trị nhất quán, có thể tạo object snapshot bất biến mới và cập nhật **một reference** với `volatile` hoặc `AtomicReference`. Reader thấy hoặc snapshot trước, hoặc snapshot sau, thay vì trộn hai phần. Khi có nhiều writer cần sửa dựa trên giá trị cũ, `AtomicReference.compareAndSet` hoặc khóa vẫn cần để tránh mất cập nhật.

## 10. Chọn giải pháp cho từng dạng tranh chấp

| Yêu cầu | Giải pháp thường hợp lý | Không nên hiểu nhầm |
| --- | --- | --- |
| Một flag được đọc bởi thread khác | `volatile boolean`, `AtomicBoolean` hoặc cơ chế đồng bộ cao hơn | `volatile` không tự tạo cơ chế chờ/đánh thức hoặc lifecycle |
| Counter số lượng chính xác | `AtomicInteger`/`AtomicLong` hoặc `synchronized` | `volatile int++` không an toàn |
| Counter thống kê rất nhiều writer | `LongAdder` nếu chấp nhận `sum()` không snapshot nguyên tử | Không dùng cho các điều kiện tiền tệ/tồn kho đòi hỏi quyết định atomic |
| Điều kiện và cập nhật trên nhiều field | `synchronized` / `Lock` quanh toàn bộ invariant | Mỗi field là một atomic riêng chưa đủ |
| Map nhiều thread cập nhật từng key | `ConcurrentHashMap` với `compute`, `merge`, `putIfAbsent` đúng hợp đồng | `containsKey` + `put` riêng lẻ vẫn có race |
| Producer/consumer truyền dữ liệu | `BlockingQueue` hoặc cơ chế message passing | Queue thread-safe không tự bảo đảm mọi điều kiện nghiệp vụ |
| Nhiều reader, ít writer và snapshot | Immutable object + safe publication | `final` reference không biến nội dung mutable thành immutable |
| Mỗi task chỉ có dữ liệu cục bộ | Thread confinement/task ownership | Tránh vô tình đưa reference mutable cho thread khác |

Không có “khóa tốt nhất” cho mọi tình huống. Chọn theo **invariant cần bảo vệ**, số writer, mức contention, yêu cầu fairness, latency, khả năng block và cấu trúc code. Sau khi chọn công cụ, hãy chỉ rõ **mọi đường truy cập nào phải tham gia cùng quy tắc**.

## 11. Hai tầng an toàn: atomic operation và atomic workflow

Ví dụ giao dịch trừ tiền:

```java
if (balance.get() >= amount) {
    balance.addAndGet(-amount);
}
```

Giả sử `balance` là `AtomicInteger`; hai thao tác `get` và `addAndGet` đều atomic riêng lẻ, nhưng **cả chuỗi không phải một thao tác nguyên tử**. Hai thread có thể cùng thấy đủ tiền và cùng trừ, khiến số dư âm.

Nếu invariant chỉ dựa trên một số nguyên, có thể dùng vòng compare-and-set (CAS):

```java
boolean withdraw(AtomicInteger balance, int amount) {
    if (amount < 0) throw new IllegalArgumentException();
    while (true) {
        int current = balance.get();
        if (current < amount) return false;
        if (balance.compareAndSet(current, current - amount)) {
            return true;
        }
    }
}
```

Đây là snippet Java 17+ dùng import `java.util.concurrent.atomic.AtomicInteger`, cần đặt trong class. `compareAndSet` chỉ thành công nếu giá trị hiện tại còn đúng `current`; nếu thread khác đã thay đổi, vòng lặp đọc lại và kiểm tra điều kiện. Trên contention cao vòng lặp có thể phải retry nhiều lần.

**Ranh giới của CAS:** nếu transaction liên quan nhiều object, kết nối DB, gọi HTTP hoặc invariant phức tạp, một CAS trên `AtomicInteger` không thể bao trùm tất cả. Nếu function cập nhật trong vòng retry có side effect bên ngoài, nó có thể chạy nhiều lần dẫn đến tác dụng phụ bị lặp. Phải quản lý transaction, idempotency và failure semantics ở tầng thích hợp.

## 12. Happens-before và “đồng bộ đúng chỗ”

Một số suy luận thường bị hiểu sai:

**“Tôi đã gọi `join()` ở cuối, vậy các cập nhật cùng lúc chắc chắn đúng.”** `join()` giúp thread gọi quan sát kết quả sau khi worker kết thúc. Nó **không sửa những lost update xảy ra khi worker tranh nhau sửa state trước đó**.

**“Có `Thread.sleep(10)` giữa hai dòng thì chắc không race.”** Delay không tạo hợp đồng đồng bộ; một lịch chạy khác, máy nhanh/chậm khác hoặc JIT khác sẽ làm kết quả thay đổi.

**“Đặt một field `volatile` thì toàn bộ object thread-safe.”** `volatile` chỉ áp dụng semantics lên trường được khai báo. Tính nguyên tử của các cập nhật ghép và invariant nhiều field vẫn cần khóa, CAS trên snapshot hoặc cơ chế riêng.

**“Tôi khóa trong writer, reader chỉ đọc nên không cần khóa.”** Nếu reader đọc field thường mà không tham gia đồng bộ hoặc cơ chế công bố tương đương, reader có thể thấy dữ liệu cũ hoặc snapshot không nhất quán.

**“Đổi `HashMap` sang `ConcurrentHashMap` là mọi nghiệp vụ đúng.”** Collection bảo đảm theo phạm vi các operation của nó. Giao thức check–then–act hay transaction nhiều map vẫn cần atomic workflow.

**“Race chỉ xảy ra khi thật sự chạy song song trên nhiều CPU.”** Ngay trên một core, scheduler có thể ngắt sau read rồi chuyển sang thread khác, tạo interleaving sai. Parallelism làm interleaving phong phú hơn, nhưng không phải điều kiện cần của race.

## 13. Kiểm thử và tìm race condition

Race condition thường khó tái hiện vì phụ thuộc lịch chạy; một test chạy đúng 1.000 lần **không chứng minh** không có race.

1. **Chỉ ra state được chia sẻ:** field/static field, collection, cache, config object, object đưa vào executor, callback; xác định ai đọc và ai ghi.
2. **Viết invariant:** `completed <= submitted`, `balance >= 0`, `sum == expected`, “mỗi key chỉ được khởi tạo một lần”, “hai field luôn tương ứng cùng phiên bản”.
3. **Kiểm tra mọi lối truy cập:** field thường có được dùng ngoài lock không? Lock có cùng instance không? Có check–then–act hoặc nhiều atomic riêng lẻ không?
4. **Thiết kế test có phối hợp:** `CountDownLatch`, `CyclicBarrier`, `Phaser` giúp đồng thời kích hoạt các nhánh nhạy cảm. Dùng timeouts để test không treo nếu có lỗi.
5. **Chạy stress dưới tải và trên nhiều cấu hình:** nhiều thread, task count lớn, scheduler/CPU khác nhau, lặp lại trong môi trường CI. Theo dõi invariant và log lỗi, không chỉ stdout rời rạc.
6. **Dùng JFR/profiling/thread dumps để tìm lock contention và deadlock:** các công cụ này hữu ích cho tranh chấp và treo nhưng **một thread dump không chứng minh có hoặc không có data race**.
7. **Review theo JMM và hợp đồng API:** thread-safe collection, atomic classes, publication, interrupt/cancellation, visibility. Kiểm thử không thay thế lập luận về happens-before.

### Khi debug, hỏi cụ thể

- Chính xác **biến/object nào được chia sẻ**? Cùng instance hay mỗi thread có bản sao?
- Thread nào ghi? Thread nào đọc? Có ít nhất một truy cập không được bao trong cùng cơ chế đồng bộ?
- Có bước “đọc điều kiện → tính toán → ghi” bị tách rời không?
- Các thread dựa vào thời gian (`sleep`, retry) hay dựa vào một tín hiệu có hợp đồng happens-before?
- Các thao tác cập nhật có thể bị retry? Có side effect không thể chạy hai lần?
- Một giải pháp đã xóa race nhưng gây lock contention, deadlock hay throughput kém chưa?

## 14. Các sai lầm thường gặp

| Sai lầm | Lỗi có thể xảy ra | Cách sửa |
| --- | --- | --- |
| Nhiều thread cập nhật `int count` bằng `count++` | Lost update | `AtomicInteger.incrementAndGet` hoặc khóa chung |
| Khai báo `volatile int count` rồi tiếp tục `count++` | Vẫn lost update | Dùng atomic read–modify–write |
| Mỗi thread đồng bộ trên object khác nhau | Không có mutual exclusion chung | Sở hữu và chia sẻ đúng cùng lock bảo vệ state |
| Writer dùng `synchronized`, reader không dùng | Visibility/invariant không bảo đảm | Reader tham gia cùng giao thức hoặc đọc snapshot an toàn |
| `if (map.get(k) == null) map.put(k, ...)` | Hai thread cùng tạo/ghi đè | Dùng operation atomic phù hợp như `computeIfAbsent` |
| Dùng `Thread.sleep` để “đợi thread kia ghi xong” | Flaky, không có happens-before cần thiết | Dùng `join`, latch, future, queue |
| Chia sẻ mutable object qua field `final` rồi nghĩ nó bất biến | Các field bên trong vẫn bị sửa đồng thời | Immutable design hoặc đồng bộ rõ ràng |
| Khóa rộng bao gồm I/O chậm | Contention, latency hoặc deadlock | Giảm phạm vi khóa, thiết kế ownership và invariant |
| CAS loop chứa thao tác gọi API có side effect | Retry có thể lặp side effect | Tách hiệu ứng khỏi retry, dùng transaction/idempotency |
| Chỉ test bằng một lịch chạy có sẵn | Race ít gặp bị bỏ sót | Stress test + lập luận theo JMM |
| Dùng nhiều `AtomicInteger` cho một invariant nhiều field | Snapshot giữa các field không nhất quán | Khóa chung hoặc immutable state snapshot được công bố atomic |
| Tin “đổi thành thread-safe collection” là xong | Race ở workflow vẫn còn | Chọn operation atomic ở mức nghiệp vụ |

## 15. Bài tập tự kiểm tra

**Câu 1.** Hai thread chỉ đọc cùng một `List<String>` đã tạo xong, nhưng không ai thay đổi list. Có nhất thiết xảy ra race?  
Không; nếu list được công bố an toàn và các phần tử/collection không bị sửa sau đó, các thao tác đọc đồng thời có thể an toàn. Kiểm tra cả việc publication, vì “không sửa sau khi tạo” chưa thay thế việc đưa reference sang thread khác đúng cách.

**Câu 2.** `volatile int x` có làm phép `x = x + 1` atomic?  
Không. Phép đó vẫn là read–modify–write với khả năng hai thread dùng cùng giá trị cũ.

**Câu 3.** Chỉ một writer, nhiều reader thì cần đồng bộ không?  
Có thể cần. Reader phải nhìn thấy thay đổi đúng thời điểm và thấy invariant nhất quán. Tùy yêu cầu, dùng `volatile`, khóa hoặc immutable snapshot.

**Câu 4.** `AtomicInteger` có đảm bảo “số dư không âm” khi hai thread cùng kiểm tra `get()` rồi trừ bằng `addAndGet(-amount)`?  
Không. Hai operation atomic riêng lẻ không bảo vệ điều kiện. Cần compare-and-set loop hoặc khóa quanh cả chuỗi.

**Câu 5.** Vì sao ví dụ `LostUpdateExample` in đúng `1` ở mọi lần chạy bình thường?  
Vì cả hai thread đều bị buộc đọc giá trị `0` rồi mới được ghi; lần ghi sau ghi đè giá trị `1` bằng chính `1`. Cổng đồng bộ khiến ví dụ xác định, không phụ thuộc xác suất interleaving.

**Câu 6.** Có thể giải quyết race chỉ bằng cách tăng thread pool hoặc đổi sang virtual thread?  
Không. Thay đổi mô hình lập lịch có thể làm race xuất hiện thường xuyên hoặc ít thường xuyên hơn nhưng không làm giao thức dữ liệu trở nên an toàn. Virtual thread và platform thread cùng cần tuân thủ JMM.

## 16. Checklist thực hành

Trước khi chia sẻ một mutable object cho nhiều thread, hãy trả lời:

- **Ai sở hữu dữ liệu?** Có thể tránh chia sẻ bằng confinement, immutable snapshots hoặc queue/message passing không?
- **Dữ liệu nào cần atomicity?** Một field, một số nguyên, một map entry hay toàn bộ invariant nhiều field?
- **Giao thức happens-before là gì?** `synchronized`, `volatile`, `Lock`, atomic, thread-safe queue, `Future` hoặc safe publication?
- **Tất cả reader/writer có tham gia cùng giao thức?** Có đường code đọc trực tiếp không khóa, hoặc dùng một khóa khác không?
- **Workflow có check–then–act hoặc read–modify–write không?** Có API atomic cho toàn bộ thao tác hay phải dùng lock/CAS?
- **Test có xác minh invariant và nhiều interleaving không?** Có timeout, log ngữ cảnh và quan sát lock contention không?
- **Giải pháp có tác dụng phụ nào?** Lock quá rộng, deadlock, retry lặp side effect, memory pressure hoặc throughput giảm?

**Ghi nhớ:** vấn đề không nằm ở việc nhiều thread cùng tồn tại, mà ở chỗ **nhiều thread có thể đọc/ghi cùng trạng thái có thể thay đổi mà không có quy tắc phối hợp đủ mạnh cho invariant cần giữ**. Xóa chia sẻ khi có thể; nếu buộc phải chia sẻ, chọn một cơ chế đồng bộ có phạm vi rõ ràng và chứng minh mọi đường truy cập đều tuân thủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="race-conditions-002">Interleaving và tính không xác định của kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Interleaving và tính không xác định của kết quả trong Java Concurrency

> **Phạm vi:** race conditions trong Java nói chung: thứ tự xen kẽ thao tác của các thread, kết quả phụ thuộc lịch thực thi, cách Java Memory Model (JMM) ảnh hưởng đến quan sát, và cách tạo tính đúng đắn bằng đồng bộ hóa. Không đi vào các cơ chế concurrency riêng của Spring.

## 1. Học xong bài này, bạn cần nắm được gì?

- Giải thích **interleaving** (xen kẽ các bước thực thi) và vì sao thứ tự thực thi giữa các thread không được mặc định cố định.
- Phân biệt **thứ tự không xác định** với **kết quả sai do race condition**; một chương trình đồng thời có thể cho thứ tự khác nhau nhưng vẫn bảo toàn mọi yêu cầu.
- Phân tích một phép `counter++` thành nhiều bước, chỉ ra lịch thực thi dẫn đến **lost update**.
- Hiểu giới hạn của mô hình xen kẽ đơn giản: Java Memory Model cho phép những quan sát liên quan visibility và reorderings nếu thiếu ràng buộc *happens-before*.
- Nhận biết và sửa các lỗi check-then-act, read-modify-write, không giữ invariant và lệ thuộc vào `sleep/yield`.
- Tạo ví dụ tái hiện race một cách có kiểm soát, rồi xác minh giải pháp bằng `synchronized`, `Lock`, atomic classes hoặc cơ chế truyền dữ liệu thích hợp.

**Ý tưởng trung tâm:** concurrency cho phép nhiều thread tiến triển trong những khoảng thời gian giao nhau. *Scheduler* quyết định thread nào có cơ hội chạy, nhưng **tính đúng đắn** phải được thiết kế bằng các quy tắc chia sẻ trạng thái và thứ tự có thể chứng minh được, không dựa vào việc “thường thấy thread A chạy trước B”.

## 2. Interleaving là gì?

Giả sử hai thread thực hiện những chuỗi bước logic riêng:

- Thread A: `A1 → A2 → A3`.
- Thread B: `B1 → B2 → B3`.

Nếu không có quan hệ đồng bộ buộc thứ tự chéo, một cách mô tả để học là: các bước của A và B có thể xen kẽ theo nhiều lịch khác nhau, **trong khi thứ tự phụ thuộc bắt buộc bên trong từng thread vẫn được tôn trọng theo ngữ nghĩa chương trình**.

Ví dụ các lịch trừu tượng:

```text
Lịch 1: A1 A2 A3 B1 B2 B3
Lịch 2: A1 B1 A2 B2 A3 B3
Lịch 3: B1 A1 B2 B3 A2 A3
```

Không có nghĩa máy luôn chạy một bước rồi dừng để chuyển thread; đây là **mô hình suy luận**. Trên máy nhiều CPU, các thao tác thực tế có thể diễn ra đồng thời. JIT, CPU và hệ thống bộ nhớ còn có thể làm thứ tự quan sát các truy cập dữ liệu **khác với cách đọc đơn giản từng dòng mã nguồn** nếu thiếu đồng bộ phù hợp. Một interleaving “hợp lý” để minh họa lost update chưa phải tập hợp đầy đủ mọi hành vi JMM cho chương trình có data race.

Vì sao lịch khác nhau giữa các lần chạy?

1. Hệ điều hành có thể preempt hoặc xếp lịch các platform thread ở những thời điểm khác nhau.
2. Số CPU khả dụng, tải hệ thống, GC, thời điểm xử lý I/O và contention thay đổi.
3. Các phép đồng bộ như lock acquisition, park/unpark hoặc đợi queue tác động đến thứ tự tiến triển.
4. Java không có hợp đồng rằng thứ tự gọi `start()` quyết định thứ tự thread vào `run()` hay hoàn thành.

**Non-determinism** nghĩa là không thể suy ra một thứ tự hoặc kết quả quan sát duy nhất từ các bảo đảm đang có. Nó không đồng nghĩa với “ngẫu nhiên phân phối đều”: một lịch có thể xuất hiện thường xuyên trên máy của bạn nhưng **vẫn không được đảm bảo**.

## 3. Lost update: hai thread cùng tăng một biến

Đoạn sau là **mã minh họa nằm trong một class**, giả sử `counter` là một trường `int` được nhiều thread chia sẻ:

```java
counter++;
```

Về ý nghĩa read-modify-write, hãy tách nó thành:

```text
R: đọc counter vào giá trị tạm
C: cộng thêm 1
W: ghi giá trị mới về counter
```

Trên Java, `counter++` **không tự động là một thao tác nguyên tử**. Hai thread có thể cùng đọc giá trị cũ, tính riêng rồi ghi đè kết quả của nhau.

Giả sử ban đầu `counter=0`, A và B đều thực hiện một lần tăng:

| Bước của lịch (mô hình tuần tự nhất quán) | Thread A | Thread B | Giá trị chia sẻ |
|---|---|---|---|
| 1 | Đọc `0` vào `a` | — | `0` |
| 2 | — | Đọc `0` vào `b` | `0` |
| 3 | Tính `a+1` và ghi `1` | — | `1` |
| 4 | — | Tính `b+1` và ghi `1` | `1` |

**Kết quả = 1, dù có hai lần tăng.** Đó là *lost update*: cập nhật của một thread bị cập nhật dựa trên dữ liệu cũ của thread khác ghi đè. Nếu A hoàn thành đầy đủ trước khi B đọc, kết quả có thể là 2.

Ở mô hình đơn giản mỗi thread gồm **đọc (R) rồi ghi (W)**, có 6 lịch giữ nguyên thứ tự R-before-W của từng thread. Hai lịch tuần tự `A_R A_W B_R B_W` và `B_R B_W A_R A_W` cho kết quả 2; bốn lịch còn lại đều có cả hai lần đọc trước lần ghi cuối và cho kết quả 1. Đây là cách đếm **chỉ của mô hình 4 bước ấy**; không phải xác suất mất dữ liệu “4/6” của JVM, vì scheduler không chọn đều các lịch và JMM rộng hơn giả định này.

Một lỗi tương tự xảy ra khi nhiều thread cập nhật số dư, bộ đếm tồn kho, sequence ID, số lượng chỗ trống, trạng thái đặt chỗ hoặc các trường có invariant liên quan nhau. Đọc đúng một giá trị ở thời điểm nào đó không đảm bảo thao tác **kiểm tra rồi cập nhật** sẽ đúng khi thread khác cùng thay đổi dữ liệu.

## 4. Phân biệt nondeterministic order, race condition và data race

Ba khái niệm dễ bị trộn lẫn:

| Khái niệm | Định nghĩa thực dụng | Có nhất thiết là lỗi? |
|---|---|---|
| **Interleaving** | Các bước/thao tác của nhiều thread được sắp xen kẽ theo một lịch thực thi. | Không. |
| **Nondeterministic order** | Thứ tự completion/print/giành lock có thể khác nhau giữa các lần chạy. | Không; nhiều ứng dụng chỉ cần invariant và kết quả tập hợp đúng. |
| **Race condition** | Kết quả hoặc hành vi đúng/sai phụ thuộc vào quan hệ thời gian/thứ tự giữa các hoạt động đồng thời chưa được phối hợp đúng. | Thường là lỗi correctness. |
| **Data race** theo JMM | Hai truy cập xung đột tới cùng biến (ít nhất một ghi), không có thứ tự *happens-before* thích hợp giữa chúng và không được phối hợp bằng các bảo đảm đồng bộ cần thiết. | Là dấu hiệu nghiêm trọng của chia sẻ trạng thái không an toàn, nhưng không đồng nhất với mọi race ở mức nghiệp vụ. |

Ví dụ một chương trình cho phép task A và B hoàn tất theo bất kỳ thứ tự nào nhưng ghi kết quả riêng vào hai vị trí độc lập, sau đó `join()` và tính tổng: **lịch có thể không xác định, tổng vẫn đúng**.

Ngược lại, ví dụ “nếu chưa có thì tạo” có thể sai ngay cả khi dùng cấu trúc dữ liệu thread-safe cho từng lệnh:

```java
// Đoạn minh họa: seen là một ConcurrentHashMap<String, Boolean>
// Hai lệnh riêng lẻ an toàn, nhưng cặp kiểm tra + thêm không là một thao tác nguyên tử.
if (!seen.containsKey(key)) {
    seen.put(key, Boolean.TRUE);
    performSideEffect();
}
```

Hai thread cùng thấy “chưa có”, rồi cả hai thực hiện side effect. Giải pháp khi muốn **chỉ một thread thắng việc thêm key**:

```java
if (seen.putIfAbsent(key, Boolean.TRUE) == null) {
    performSideEffect();
}
```

Đây vẫn là **đoạn mã minh họa**, không phải chương trình đầy đủ: `seen`, `key` và `performSideEffect()` được khai báo trong ngữ cảnh bên ngoài. `putIfAbsent` giúp quyết định duy nhất một thread nhận “quyền làm” trong bộ nhớ, nhưng nếu side effect thất bại thì còn phải có **chính sách retry/rollback/idempotency**; không suy ra thao tác bên ngoài trở thành giao dịch nguyên tử với map. Nếu map có thể đã chứa key với value `null` thì cần xét lại API; `ConcurrentHashMap` không cho phép null key/value.

## 5. Vì sao thay đổi một dòng code có thể làm bug biến mất?

Nhiều race condition chỉ lộ ra dưới những lịch “hiếm”:

- Thêm `System.out.println` khiến thay đổi thời gian thực thi và bản thân `PrintStream` có đồng bộ ở nhiều đường đi; biểu hiện race có thể đổi.
- Debugger, logging, profiler, độ dài vòng lặp hoặc tốc độ CPU làm thread chạm vào vùng nguy hiểm ở thời điểm khác.
- Gọi `Thread.sleep(10)` có thể làm một lịch thường gặp hơn nhưng không tạo quy tắc ordering cho dữ liệu chia sẻ.
- `Thread.yield()` chỉ là gợi ý, không buộc scheduler cấp lượt cho thread cụ thể; không bảo đảm fairness hay tính đúng đắn.
- Chạy cùng binary nhiều lần không nhất thiết thấy tất cả lịch có thể xảy ra; nhiều test chạy thành công **không chứng minh** chương trình race-free.

Đừng “sửa” race bằng cách chèn thời gian ngủ tùy tiện. Hãy xác định biến chung, những lệnh đọc/ghi cần được xem như một giao dịch logic, và quan hệ đồng bộ nào phải thiết lập giữa chúng.

## 6. Java Memory Model: xen kẽ các lệnh chưa đủ để giải thích mọi thứ

Một **interleaving tuần tự nhất quán** (*sequential consistency*) tưởng tượng tất cả memory operation xảy ra theo một thứ tự tổng, vẫn tôn trọng thứ tự chương trình của mỗi thread. Mô hình đó hữu ích để thấy lost update, nhưng **Java không hứa rằng mọi chương trình chứa data race đều quan sát như một lịch xen kẽ đơn giản các dòng Java**.

Java Memory Model (JLS `17.4) phân biệt các quan hệ quan trọng:

- **Program order:** thứ tự các action theo thực thi của một thread, dùng để định nghĩa `happens-before`; các tối ưu vẫn có thể sắp xếp lại thao tác vật lý miễn đáp ứng những bảo đảm quan sát cần thiết.
- **Synchronization order:** thứ tự các synchronization actions như volatile access và lock/unlock theo JMM.
- **Synchronizes-with:** quan hệ được quy định giữa các action, ví dụ nhả monitor rồi một thread khác lấy cùng monitor.
- **Happens-before (HB):** quan hệ bắc cầu tạo từ program order và các cạnh *synchronizes-with*. Nếu A HB B, B phải chịu các ràng buộc nhất định về visibility/ordering do A tạo ra.

Một số quan hệ thực dụng cần nhớ:

1. Các action trong một thread có program order theo mô hình JMM.
2. **Unlock** monitor `m` *synchronizes-with* lần **lock** tiếp theo trên chính monitor `m`.
3. **Ghi** vào biến `volatile` *synchronizes-with* các lần **đọc** sau đó của cùng biến theo synchronization order.
4. Gọi `Thread.start()` HB các action của thread mới. Mọi action của một thread HB việc thread khác phát hiện thread đó đã kết thúc bằng cách như `join()` trả về thành công.
5. Các API `java.util.concurrent` có hợp đồng memory-consistency riêng (ví dụ `CountDownLatch`, `BlockingQueue`, `Future.get()`); đọc đúng tài liệu của primitive đang sử dụng.

**Ví dụ visibility:**

```java
// Đoạn minh họa là các trường/method thuộc cùng class.
private int value;
private volatile boolean ready;

void publish() {
    value = 42;
    ready = true;  // volatile write
}

void consume() {
    if (ready) {   // volatile read
        System.out.println(value);
    }
}
```

Nếu `consume` đọc được `ready == true` từ thao tác publish tương ứng, các ghi trước volatile write (bao gồm `value=42`) được công bố tới luồng đọc theo HB. Tuy nhiên, nếu consumer đọc `ready == false` thì nó không in gì; không có bảo đảm consumer được scheduler chạy *sau* publish. Nếu có thêm ghi vào `value/ready` theo một giao thức khác, phải phân tích lại cụ thể. Đây là đoạn minh họa **không độc lập biên dịch** và không thay thế giao thức phối hợp cho nhiều lần publish.

**Sai lầm quan trọng:** khai báo `volatile int counter` **không khiến `counter++` nguyên tử**. Volatile cung cấp quy tắc visibility/ordering cho các access riêng lẻ, nhưng phép tăng vẫn là chuỗi đọc–tính–ghi; hai thread có thể cùng đọc rồi ghi đè. Khi cần tăng nguyên tử, dùng atomic operation hoặc lock bảo vệ toàn bộ read-modify-write.

**Data-race-free → dễ suy luận hơn:** nếu toàn bộ truy cập chia sẻ gây xung đột được đồng bộ đúng theo JMM, execution được bảo đảm nhất quán theo mô hình tuần tự thích hợp (*sequentially consistent for correctly synchronized programs*). Điều này **không làm lịch chạy thành cố định**; có thể có nhiều thứ tự hợp lệ và nhiều output hợp lệ, nhưng không phá vỡ invariant đã thiết kế.

## 7. Thực hành Java 17+: buộc xuất hiện một lịch mất cập nhật

Ví dụ dưới đây cố tình dùng `CyclicBarrier` để ép **hai thread đều đọc giá trị 0 trước khi bất kỳ thread nào ghi**. Điều này làm lỗi lost update có thể quan sát nhất quán trong bài thực hành; nó **không** ngụ ý mọi lần tăng thiếu đồng bộ đều mất cập nhật.

Phần thứ hai thay bằng `AtomicInteger.incrementAndGet()`. Phần cuối dùng `StringBuffer` (mỗi lần append được đồng bộ) để cho thấy **thứ tự A/B không cố định nhưng dữ liệu không hỏng**.

Tạo file **`InterleavingLab.java`**:

```java
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

public class InterleavingLab {
    private static int counter;

    private static void runPair(Runnable task) throws InterruptedException {
        Thread first = new Thread(task, "first");
        Thread second = new Thread(task, "second");
        first.start();
        second.start();
        first.join();
        second.join();
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted at barrier", e);
        } catch (BrokenBarrierException e) {
            throw new IllegalStateException("Barrier broken", e);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        counter = 0;
        CyclicBarrier bothRead = new CyclicBarrier(2);
        runPair(() -> {
            int snapshot = counter;
            awaitBarrier(bothRead);
            counter = snapshot + 1;
        });
        System.out.println("lostUpdate=" + counter);

        AtomicInteger atomicCounter = new AtomicInteger();
        runPair(() -> atomicCounter.incrementAndGet());
        System.out.println("atomic=" + atomicCounter.get());

        StringBuffer completionOrder = new StringBuffer();
        Thread a = new Thread(() -> completionOrder.append('A'));
        Thread b = new Thread(() -> completionOrder.append('B'));
        a.start();
        b.start();
        a.join();
        b.join();
        System.out.println("order=" + completionOrder);
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 InterleavingLab.java
java InterleavingLab
```

Một lần chạy đã xác minh:

```text
lostUpdate=1
atomic=2
order=AB
```

**Những gì có thể khẳng định khi thực thi bình thường:**

- `lostUpdate=1`: hai worker đều đọc `counter=0` rồi chờ nhau ở barrier. Cả hai đều ghi `1`, nên cuối cùng chỉ thấy một lần tăng. Đó là một lịch được ép có kiểm soát.
- `atomic=2`: mỗi `incrementAndGet()` là một phép tăng atomic. Hai phép được áp dụng thành công, `join()` trả về rồi mới đọc, nên tổng là 2.
- Dòng thứ ba là **`order=AB` hoặc `order=BA`**; thứ tự phụ thuộc lịch chạy. `StringBuffer.append` làm từng append an toàn trong bối cảnh này, và `join()` chờ hai thread hoàn thành. Không có bảo đảm bạn sẽ quan sát cả hai thứ tự trên một máy.

**Vì sao cần barrier?** Nếu chỉ viết hai thread cùng `counter++` rồi chạy vài lần, nhiều lần bạn sẽ thấy đúng 2 và tưởng code an toàn. `CyclicBarrier(2)` ép điểm đồng bộ **sau bước đọc, trước bước ghi**, mô phỏng chính lịch gây mất cập nhật. Nó không phải giải pháp sửa biến `counter`, vì ý định ở phần đầu là minh họa lỗi.

**Vì sao dùng `join()`?** Nếu main in kết quả ngay sau hai lệnh `start()`, nó có thể đọc biến trước khi worker chạy xong; đây là một vấn đề ordering bổ sung làm phép thử khó hiểu. `join()` thành công bảo đảm worker kết thúc trước khi main kiểm tra tổng và thiết lập quan hệ HB phù hợp.

**Chú ý giới hạn demo:** vòng `awaitBarrier()` dùng interrupt và broken-barrier exception để báo lỗi môi trường; trong tình huống thread chết/trễ bất thường, các worker có thể không hoàn tất. Không dùng barrier để bù cho một điều kiện đồng bộ nghiệp vụ chưa được định nghĩa. Việc sử dụng `StringBuffer` ở phần ba phục vụ minh họa order; hệ thống thật có thể dùng queue hoặc cấu trúc data phù hợp.

## 8. Làm sao tạo kết quả đúng khi lịch chạy vẫn thay đổi?

### Cách 1: `synchronized` bảo vệ **toàn bộ** thao tác

```java
private int counter;
private final Object guard = new Object();

void increment() {
    synchronized (guard) {
        counter++; // read-modify-write nằm trong cùng critical section
    }
}

int snapshot() {
    synchronized (guard) {
        return counter;
    }
}
```

Đây là các **thành viên minh họa trong một class**. Mọi thread cập nhật/đọc trạng thái được bảo vệ phải tuân thủ cùng monitor `guard`. `synchronized` tạo mutual exclusion và các cạnh HB giữa unlock/lock. Hai lần tăng cho kết quả 2 sau khi cả hai hoàn thành; thread nào lấy monitor trước vẫn có thể khác nhau. Nếu nhiều trường cần invariant chung, bảo vệ toàn bộ nhóm kiểm tra/cập nhật bằng cùng lock.

### Cách 2: `AtomicInteger` cho một state độc lập

```java
private final AtomicInteger counter = new AtomicInteger();

void increment() {
    counter.incrementAndGet();
}

int snapshot() {
    return counter.get();
}
```

Đây là các thành viên minh họa trong một class có import `java.util.concurrent.atomic.AtomicInteger`. `incrementAndGet()` thực hiện phép tăng atomically với memory semantics của volatile read/write tương ứng. Đúng khi mục tiêu là cập nhật một biến nguyên tử. **Không** tự bảo vệ invariant liên quan hai atomic khác nhau, ví dụ “`balanceA + balanceB` phải bất biến” qua hai phép trừ/cộng tách biệt.

### Cách 3: `ReentrantLock` khi cần điều khiển acquisition

```java
private final ReentrantLock lock = new ReentrantLock();
private int counter;

void increment() {
    lock.lock();
    try {
        counter++;
    } finally {
        lock.unlock();
    }
}
```

Đây là đoạn mã thành viên class, cần import `java.util.concurrent.locks.ReentrantLock`. Tương tự `synchronized`, cả thao tác logic được đưa vào critical section. `ReentrantLock` còn có `tryLock()`, `lockInterruptibly()` và `Condition`; dùng khi thiết kế thực sự cần các tính năng đó, không mặc định nhanh hơn intrinsic monitor.

### Cách 4: Thay cách chia sẻ dữ liệu

Không phải bài toán nào cũng cần tất cả worker cùng viết một biến:

- **Thread confinement:** mỗi worker tích lũy kết quả cục bộ, sau `Future.get()`/`join()` thì một nơi gộp kết quả.
- **Immutable data / message passing:** đẩy message qua `BlockingQueue` và chỉ một consumer sở hữu/mutate trạng thái đích.
- **Partition theo key:** mỗi nhóm key được xử lý bởi một owner riêng; cần quy tắc khi phối hợp giữa partitions.
- **Concurrent collections:** dùng thao tác atomic ở cấp API (`putIfAbsent`, `compute`, `merge`) khi cần chính xác một chuyển đổi trạng thái, thay vì nối nhiều lệnh đọc–ghi riêng lẻ.

Mục tiêu là thiết lập một **linearization point** hoặc critical section có thể chỉ ra để biết phép cập nhật logic xảy ra như một đơn vị. “Mỗi method thread-safe” không đồng nghĩa một **chuỗi nhiều method** đã atomic.

## 9. Determinism của kết quả và determinism của thứ tự

**Deterministic result**: với cùng input và điều kiện cam kết của chương trình, output có thể được bảo đảm là như nhau, dù lịch bên trong khác. Ví dụ hai worker cộng những phần khác nhau vào `AtomicInteger` và main đọc sau `join()`.

**Nondeterministic order nhưng hợp lệ**: hai task độc lập lần lượt append A hoặc B vào một buffer đồng bộ; chuỗi có thể `AB` hoặc `BA`. Nếu yêu cầu hệ thống là “có đúng một A và một B”, cả hai đều đúng.

**Nondeterministic result do thiết kế thiếu ordering**: hai worker cùng update shared mutable data không đồng bộ, nên kết quả phụ thuộc lịch và có thể vi phạm invariant.

**Muốn kết quả phải theo thứ tự?** Hãy mô tả ràng buộc rõ ràng:

- A phải hoàn thành trước B xử lý kết quả của A: dùng `Future.get()`, `join()`, `CountDownLatch` hoặc phối hợp thông điệp phù hợp.
- Các sự kiện phải được ghi theo sequence: gán sequence bằng cơ chế nguyên tử/lock, rồi sắp xếp theo sequence (nhớ rằng “thứ tự cấp số” và “thứ tự hiệu ứng bên ngoài hoàn thành” có thể khác nhau).
- Cập nhật cơ sở dữ liệu/tài nguyên ngoài JVM phải có giao dịch, điều kiện ghi, idempotency hoặc cơ chế phối hợp riêng. Java lock trong một process không tự bảo vệ nhiều process.

**Đừng ép thứ tự khi không cần:** đồng bộ quá mức biến chương trình gần như tuần tự, tăng contention và giảm throughput. Thiết kế đồng thời tốt thường **cho phép nhiều lịch**, nhưng bảo đảm mọi lịch được phép đều giữ invariant.

## 10. Kiểm thử race condition theo cách có bằng chứng

Chỉ chạy mã một lần rồi thấy đúng không chứng minh thread safety. Một chiến lược phù hợp:

1. **Xác định invariant.** Ví dụ sau khi N worker hoàn thành đúng K phép tăng mỗi worker, tổng phải là `N × K`; dữ liệu không được âm; mỗi event chỉ được xử lý một lần.
2. **Xác định điểm chia sẻ.** Liệt kê thao tác read/write, nguồn phát sự kiện, lock và quan hệ HB; tách rõ biến độc lập với invariant liên quan nhiều biến.
3. **Tạo điểm giao tranh có kiểm soát.** `CountDownLatch` để đồng thời bắt đầu, `CyclicBarrier` để buộc chạm một checkpoint; đừng dùng `sleep()` như tín hiệu chính xác.
4. **Kiểm tra kết quả và khả năng tiến triển.** Assertion về giá trị, số lần xử lý, data integrity; timeout cho bài test để nhận ra deadlock/hang.
5. **Stress ở cấu hình khác nhau.** Thay số worker, số vòng lặp, số lõi khả dụng, JDK/JIT và tốc độ tải. Chạy nhiều lần để tăng khả năng gặp lịch nguy hiểm; thất bại là bằng chứng bug, thành công không phải chứng minh toán học.
6. **Dùng công cụ đúng chức năng.** [OpenJDK jcstress](https://github.com/openjdk/jcstress) giúp thiết kế litmus tests về các outcome được phép theo JMM. JFR, thread dump và profiler giúp xác định contention/timing; **chúng không tự chứng minh chương trình race-free**.
7. **Khóa hành vi bằng contract.** Nếu dùng một primitive cấp cao, đọc bảo đảm memory consistency của nó; không thay bằng API khác chỉ vì benchmark nhanh hơn.

**Lưu ý benchmark:** số lần chạy hoặc `System.nanoTime()` không đo “mức độ an toàn” của concurrency. Một thực thi sai có thể nhanh hơn vì đã bỏ đồng bộ cần thiết. Chỉ so tốc độ giữa các phiên bản **đều đúng** trên invariant và workload tương đương.

### Ví dụ lịch kiểm tra rất ngắn

Với mô hình `counter++` ban đầu bằng 0:

| Lịch trừu tượng | Thứ tự đọc/ghi | Kết quả mong đợi trong mô hình |
|---|---|---|
| A xong rồi B | `A_R A_W B_R B_W` | `2` |
| Hai thread cùng đọc trước ghi | `A_R B_R A_W B_W` | `1` |
| B xong rồi A | `B_R B_W A_R A_W` | `2` |

Tác dụng của bảng này là **lập luận ra tình huống lỗi**, không dự đoán xác suất lịch hay tập hợp chính xác mọi quan sát dưới mọi tối ưu JMM. Với dữ liệu có race, chỉ mã nguồn “đọc rồi ghi” không đủ làm chứng cứ về visibility.

## 11. Những hiểu lầm phổ biến

1. **“Có hai thread nên chắc chắn chạy song song.”** Concurrency không đồng nghĩa chạy cùng lúc; một CPU cũng có thể xen kẽ tiến trình.
2. **“Gọi `start` của A trước B thì A sẽ chạy trước.”** Sai: `start()` không áp đặt thứ tự completion hay mọi action giữa hai thread.
3. **“`counter++` là một dòng nên atomic.”** Sai: là read-modify-write, có thể mất cập nhật.
4. **“Đặt `volatile` là hết lost update.”** Sai: volatile không gộp phép đọc/tính/ghi thành một phép nguyên tử.
5. **“Chương trình in AB nhiều lần chứng minh không thể in BA.”** Sai: lịch quan sát được phụ thuộc thực thi; absence of evidence không phải cam kết về thứ tự.
6. **“Thứ tự output biến đổi nghĩa là có race bug.”** Sai: order không cố định có thể là yêu cầu hợp lệ, miễn các invariant và visibility được bảo đảm.
7. **“Dùng `ConcurrentHashMap` thì mọi chuỗi contains/put/side effect đã atomic.”** Sai: mỗi lời gọi an toàn riêng lẻ không tạo giao dịch cho cả chuỗi.
8. **“`sleep` hoặc `yield` là synchronization.”** Sai: chúng không tạo HB thích hợp cho các cập nhật chia sẻ chỉ vì đã trì hoãn thread.
9. **“`join` làm một phép tăng trước đó trở nên atomic.”** Sai: join giúp main nhìn thấy kết quả sau khi worker kết thúc, nhưng không ngăn hai worker ghi đè nhau trong lúc chạy.
10. **“Test pass nhiều lần là đủ chứng minh thread safety.”** Sai: chỉ là bằng chứng của những lịch đã được quan sát; cần suy luận invariants, HB và atomicity.
11. **“Lock mọi thứ là tốt nhất.”** Không nhất thiết: lock quá rộng có thể gây contention, deadlock hoặc latency cao; phải bảo vệ chính xác invariant và đo hiệu năng.
12. **“JMM chỉ là scheduler luân phiên các dòng source.”** Sai: JMM quy định visibility/ordering của memory actions; tối ưu và hành vi data race khiến mô hình source-line interleaving không đầy đủ.

## 12. Câu hỏi tự kiểm tra kèm đáp án

**Câu 1.** A và B mỗi thread gọi `counter++` một lần trên biến `int` chia sẻ bắt đầu bằng 0, không có synchronization. Trong mô hình xen kẽ đọc/ghi tuần tự, có thể được 1 và 2 không?  
**Đáp án:** Có. Cả hai cùng đọc 0 rồi ghi 1 gây lost update; nếu một thread hoàn tất trước khi thread kia đọc thì được 2. Phân tích đầy đủ theo JMM cần xét quan hệ đồng bộ/visibility, không chỉ lịch đọc mã nguồn.

**Câu 2.** Thay `int counter` bằng `volatile int counter` rồi tiếp tục dùng `counter++` có bảo đảm 2 không?  
**Đáp án:** Không. Các access volatile có ordering/visibility, nhưng biểu thức tăng vẫn gồm đọc rồi ghi riêng biệt.

**Câu 3.** Hai task độc lập ghi A/B vào `StringBuffer` và main đọc sau `join()`. Có thể `AB` và `BA` không? Đó có nhất thiết là bug không?  
**Đáp án:** Có hai thứ tự hợp lệ; không phải bug nếu contract chỉ cần đủ hai ký tự mà không yêu cầu thứ tự.

**Câu 4.** Sau khi thread A gọi `start()` trước thread B, có thể B hoàn thành trước A không?  
**Đáp án:** Có. Thứ tự gọi start không quyết định lịch CPU và thời điểm kết thúc.

**Câu 5.** Vì sao gọi `join()` trước khi đọc kết quả từ worker là quan trọng nhưng chưa đủ sửa lost update?  
**Đáp án:** Join tạo trật tự worker hoàn tất trước reader và visibility phù hợp; các cập nhật không atomic giữa hai worker vẫn có thể ghi đè nhau.

**Câu 6.** Một chương trình dùng `ConcurrentHashMap.containsKey`, nếu chưa có thì `put` và gửi email. Tại sao có thể gửi hai email?  
**Đáp án:** Hai thread cùng qua nhánh “chưa có”. Thao tác điều kiện phải được atomically quyết định, và side effect ngoài map cần idempotency/retry policy tương ứng.

**Câu 7.** Nếu một race chỉ xuất hiện trên máy production, có nên thêm log/sleep để tái hiện rồi coi đó là bản sửa không?  
**Đáp án:** Không. Log/sleep thay đổi timing, có thể che mất lỗi. Hãy mô hình hóa invariant, ép checkpoint có kiểm soát và bổ sung synchronization đúng.

**Câu 8.** Cần tăng một bộ đếm độc lập từ nhiều thread: chọn `AtomicInteger` hay `synchronized`?  
**Đáp án:** Cả hai có thể đúng. `AtomicInteger.incrementAndGet` thường gọn cho một phép tăng độc lập; `synchronized` phù hợp khi phải bảo vệ nhiều thao tác/trường như một invariant. Quyết định theo semantics và đo tải thực tế.

## Tài liệu tham khảo

- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): thread actions, data races, `happens-before`, correctly synchronized programs.
- [Java SE 17 — Thread](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): `start()`, `join()`, semantics và vòng đời thread.
- [Java SE 17 — AtomicInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): atomic read-modify-write.
- [Java SE 17 — CyclicBarrier](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CyclicBarrier.html): rendezvous, barrier action và memory consistency.
- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): phối hợp các giai đoạn và memory consistency.
- [Java SE 17 — ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): thao tác map hỗ trợ concurrent access.
- [OpenJDK jcstress](https://github.com/openjdk/jcstress): bộ công cụ thực nghiệm và litmus tests về tính đồng thời/JMM.

**Kết luận học tập:** đừng cố dự đoán một lịch chạy duy nhất. Hãy xác định mọi kết quả mà contract cho phép, các invariant cần bất biến, và các quan hệ atomicity/happens-before bắt buộc. Khi đó chương trình vẫn được lập lịch linh hoạt mà kết quả vẫn đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="race-conditions-003">Lost update trong thao tác tăng biến đếm</a>

<details>
<summary>Xem chi tiết</summary>

# Lost update trong thao tác tăng biến đếm — Java Concurrency

## 1. Mục tiêu bài học

Giả sử hai luồng cùng tăng biến đếm chung một lần. Ta kỳ vọng biến tăng từ 0 lên 2. Tuy nhiên, có lúc kết quả chỉ là 1. Một thao tác tăng đã bị **mất** vì hai luồng đọc cùng giá trị cũ và lần ghi sau ghi đè kết quả của lần ghi trước. Hiện tượng này gọi là **lost update**, một loại **race condition** rất phổ biến.

Sau bài này, bạn sẽ:

- Giải thích vì sao \`count++\` gồm nhiều bước logic và không bảo đảm atomicity giữa các thread.
- Vẽ được interleaving gây mất cập nhật, phân biệt tính nguyên tử, visibility và ordering trong Java Memory Model.
- Tái hiện lost update **một cách có kiểm soát** bằng chương trình Java 17+ với kết quả dự đoán chính xác.
- Sửa bằng \`synchronized\`, \`Lock\`, \`AtomicInteger\`/CAS và \`LongAdder\`, lựa chọn giải pháp theo yêu cầu đọc/ghi.
- Nhận diện các bẫy như \`volatile int count\`, \`get()+incrementAndGet()\`, khóa sai đối tượng, tăng trong nhiều executor và đọc kết quả khi tác vụ chưa hoàn tất.

**Phạm vi:** Java SE, Java Memory Model và API đồng bộ hóa của JDK. Các ví dụ chạy độc lập bằng Java 17 trở lên.

## 2. Từ một dòng mã tới ba bước read–modify–write

Xét ví dụ:

~~~java
class Counter {
    int count = 0;

    void increment() {
        count++;
    }
}
~~~

\`count++\` tương đương về **ngữ nghĩa tính toán** với:

~~~java
int oldValue = count;   // (1) đọc
int newValue = oldValue + 1; // (2) tính giá trị mới
count = newValue;       // (3) ghi
~~~

Ba bước này chỉ là **mô hình để giải thích**, không khẳng định máy ảo phải phát ra đúng ba lệnh máy hoặc không thể tối ưu. Điều quan trọng là toàn bộ thao tác read–modify–write **không tự động trở thành một hành động nguyên tử** khi nhiều thread cùng truy cập trạng thái chia sẻ.

Với nhiều thread, mỗi thread có thể đọc trước khi thread kia ghi. Kết quả sau cùng phụ thuộc thứ tự xen kẽ (interleaving) và các ràng buộc đồng bộ. Java bảo đảm tính nguyên tử của một lần đọc hoặc ghi \`int\` thông thường, nhưng **không** bảo đảm atomicity cho toàn bộ biểu thức \`count++\`.

### Interleaving gây mất một lần tăng

Ban đầu \`count = 10\`; hai thread T1 và T2 cùng thực hiện một lần tăng:

| Bước | Thread T1 | Thread T2 | Biến chung |
|---|---|---|---|
| 1 | Đọc \`count = 10\` vào biến tạm | — | 10 |
| 2 | — | Đọc \`count = 10\` vào biến tạm | 10 |
| 3 | Tính \`10 + 1 = 11\` | — | 10 |
| 4 | — | Tính \`10 + 1 = 11\` | 10 |
| 5 | Ghi \`count = 11\` | — | 11 |
| 6 | — | Ghi \`count = 11\` | 11 |

Kết quả mong đợi: **12**. Kết quả trong lịch chạy này: **11**. Hai thao tác được gọi, nhưng chỉ có một đơn vị tăng được phản ánh trong trạng thái cuối.

~~~text
                  T1                           T2
                  │                            │
             read count=10                     │
                  │                       read count=10
            compute 10+1                       │
                  │                       compute 10+1
             write count=11                    │
                  │                       write count=11
                  └─────────────┬──────────────┘
                                ▼
                         count cuối = 11
                     (mong đợi là 12)
~~~

Không phải mọi lần chạy đều mất cập nhật: nếu T1 hoàn tất toàn bộ quá trình tăng trước khi T2 đọc thì \`count\` có thể là 12. Vì vậy race condition không đồng nghĩa lỗi xảy ra ở mỗi lần thực thi.

## 3. Lost update, data race và Java Memory Model

**Race condition** là kết quả không mong muốn do thứ tự/tương tác giữa các thực thi đồng thời. **Data race** theo Java Memory Model là một cặp truy cập xung đột tới cùng biến, từ các thread khác nhau, có ít nhất một thao tác ghi, nhưng không được sắp xếp bởi quan hệ happens-before. Một chương trình có thể có race condition gây lỗi logic ngay cả khi các truy cập riêng lẻ được đồng bộ đúng cách nhưng **chuỗi hành động** không được bảo vệ như một thao tác toàn vẹn.

Ba khái niệm cần phân biệt:

| Thuộc tính | Câu hỏi | \`count++\` thường thiếu gì? |
|---|---|---|
| **Atomicity (tính nguyên tử)** | Toàn bộ cập nhật có diễn ra như một bước không thể chen ngang? | Không. Hai thread có thể đọc cùng giá trị và ghi đè nhau. |
| **Visibility (khả năng nhìn thấy)** | Giá trị được ghi bởi thread này có xuất hiện đúng lúc ở thread khác? | Với trường không đồng bộ, không có bảo đảm nhìn thấy cập nhật đúng lúc. |
| **Ordering (thứ tự bộ nhớ)** | Những thao tác nào phải được quan sát theo quan hệ happens-before? | Không có quan hệ đồng bộ cần thiết giữa các lần tăng racy. |

Việc một lần đọc/ghi \`int\` riêng lẻ là atomic **không kéo theo** toàn bộ biểu thức \`count++\` là atomic. Việc bảo đảm visibility cũng **không tự động** đảm bảo atomicity.

### Vì sao \`volatile\` không sửa được lost update?

~~~java
class VolatileCounter {
    volatile int count;

    void increment() {
        count++; // VẪN có lost update
    }
}
~~~

\`volatile\` cung cấp ngữ nghĩa visibility và ordering: ghi volatile và đọc volatile tương ứng tạo các quan hệ happens-before theo quy tắc của Java Memory Model. Nhưng hai thread vẫn có thể đọc cùng giá trị (dù là giá trị mới nhất tại lần đọc), cùng tính \`+1\` và cùng ghi đè lên biến. Không có khóa hoặc thao tác read–modify–write nguyên tử để loại bỏ xen kẽ đó.

Ví dụ:

~~~text
volatile count = 7
T1: read 7
T2: read 7
T1: write 8
T2: write 8
=> count = 8, mất một update
~~~

Ghi nhớ: \`volatile\` phù hợp trong một số trường hợp cờ trạng thái độc lập như \`stopRequested\`; nó không thay thế \`synchronized\` hoặc atomic API cho phép tăng biến đếm chính xác.

### Tại sao sleep(), yield() hay việc chạy nhanh/chậm không bảo vệ được?

\`Thread.sleep(...)\` chỉ yêu cầu thread ngủ một khoảng thời gian; \`Thread.yield()\` là gợi ý cho scheduler. Không lời gọi nào biến \`count++\` thành thao tác atomic hoặc tạo critical section. Thêm \`sleep\` có thể làm lỗi dễ quan sát hơn trong một bài thử, nhưng **không chứng minh** cách sửa đúng. CPU nhiều core, JIT, cache và tải máy làm lịch chạy thay đổi; “đã chạy 1.000 lần không lỗi” vẫn không đủ để khẳng định thread-safe.

## 4. Ví dụ tái hiện lỗi với kết quả xác định

Cách thử \`2 thread × 100_000 lần count++\` thường gặp có thể cho kết quả thấp hơn 200.000, nhưng **không ổn định**: nhiều lần chạy vẫn ra 200.000. Để hiểu chính xác một lost update, ta chủ động cho cả hai thread **đọc biến trước**, sau đó mới mở cổng cho cả hai ghi.

Lưu tệp **LostUpdateDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class LostUpdateDemo {
    private static int count = 0;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch bothHaveRead = new CountDownLatch(2);
        CountDownLatch allowWriting = new CountDownLatch(1);

        Runnable increment = () -> {
            int snapshot = count;
            bothHaveRead.countDown(); // báo đã đọc count

            try {
                allowWriting.await(); // chưa được ghi khi thread kia chưa đọc
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            count = snapshot + 1; // ghi dựa trên snapshot cũ
        };

        Thread first = new Thread(increment, "first");
        Thread second = new Thread(increment, "second");

        first.start();
        second.start();

        bothHaveRead.await(); // bảo đảm cả hai đã đọc count = 0
        allowWriting.countDown(); // mở cổng ghi
        first.join();
        second.join();

        System.out.println("expected=2");
        System.out.println("actual=" + count);
        System.out.println("lost=" + (2 - count));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac LostUpdateDemo.java
java LostUpdateDemo
~~~

Output trong điều kiện bình thường, không có tác động interrupt từ bên ngoài:

~~~text
expected=2
actual=1
lost=1
~~~

**Giải thích tại sao đây là kết quả xác định:**

1. \`bothHaveRead\` bắt đầu tại 2. Mỗi worker đọc \`count\` vào một **biến cục bộ riêng**, rồi đếm xuống.
2. Main chỉ mở \`allowWriting\` sau khi biết **cả hai lần đọc đã xong**. Trước thời điểm đó, không worker nào được ghi \`count\`.
3. Vì \`count\` ban đầu là 0 và chưa có thread nào ghi, cả hai snapshot là 0.
4. Sau khi cổng mở, mỗi worker chỉ ghi \`0 + 1 = 1\`. Dù ai ghi trước, giá trị cuối đều là 1.
5. \`join()\` chờ hai worker kết thúc trước khi main đọc kết quả. \`join\` cũng tạo quan hệ happens-before từ các hành động của worker tới thread chờ hoàn tất.

**Điểm rất quan trọng:** CountDownLatch ở ví dụ này **dùng để dàn dựng một interleaving gây lỗi**, không dùng để sửa lỗi. Việc chờ cả hai thread đọc xong rồi mới cho ghi làm mất update trở nên chắc chắn. Muốn sửa phải bảo vệ thao tác **đọc + tính + ghi** trong một atomic operation hoặc critical section chung.

## 5. Cách sửa thứ nhất: dùng synchronized

Một monitor lock chung có thể bảo đảm chỉ một thread tại một thời điểm chạy toàn bộ phần read–modify–write.

~~~java
class SynchronizedCounter {
    private int count;

    public synchronized void increment() {
        count++;
    }

    public synchronized int get() {
        return count;
    }
}
~~~

\`synchronized\` trên một instance method dùng monitor của chính đối tượng (\`this\`). Khi T1 giữ monitor, T2 muốn vào \`increment()\` trên **cùng instance** phải đợi. Khi T1 rời critical section, T2 mới đọc giá trị đã cập nhật. Unlock của monitor và lock kế tiếp trên cùng monitor thiết lập happens-before, hỗ trợ cả mutual exclusion lẫn visibility.

**Điều kiện:** Mọi truy cập liên quan phải tuân theo giao thức đồng bộ đúng. Nếu có phương thức khác vẫn ghi trực tiếp vào \`count\` mà không giữ cùng monitor, giao thức không còn bảo đảm.

### Khóa riêng (private lock) nếu cần tách khỏi monitor đối tượng

~~~java
class LockedCounter {
    private final Object lock = new Object();
    private int count;

    void increment() {
        synchronized (lock) {
            count++;
        }
    }

    int get() {
        synchronized (lock) {
            return count;
        }
    }
}
~~~

Dùng \`private final Object lock\` ngăn code ngoài vô tình khóa cùng monitor do \`synchronized(this)\`. Các phương thức \`increment\` và \`get\` phải dùng **cùng đối tượng lock**.

Sai lầm thường thấy:

~~~java
void brokenIncrement() {
    synchronized (new Object()) { // mỗi lần gọi tạo một lock riêng
        count++;
    }
}
~~~

Đoạn mã trên là **minh họa lỗi** bên trong một class có trường \`count\`; nó không giải quyết race giữa những lời gọi khác nhau. Thread nào cũng giữ monitor mới của chính nó nên không có mutual exclusion trên biến chung.

### Dùng ReentrantLock khi cần kiểm soát rõ hơn

~~~java
import java.util.concurrent.locks.ReentrantLock;

class ReentrantCounter {
    private final ReentrantLock lock = new ReentrantLock();
    private int count;

    void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();
        }
    }

    int get() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }
}
~~~

\`ReentrantLock\` cho phép các khả năng như \`tryLock()\`, \`lockInterruptibly()\` và các \`Condition\` khi có nhu cầu cụ thể. Cần luôn đặt \`unlock()\` trong \`finally\` sau khi lấy lock thành công. Với một biến đếm đơn giản, \`synchronized\` thường rõ ràng và ít lỗi hơn.

## 6. Cách sửa thứ hai: AtomicInteger và atomic read–modify–write

Java cung cấp \`java.util.concurrent.atomic.AtomicInteger\` cho một giá trị \`int\` cần cập nhật nguyên tử mà không phải viết monitor lock cho từng lần tăng.

~~~java
import java.util.concurrent.atomic.AtomicInteger;

class AtomicCounter {
    private final AtomicInteger count = new AtomicInteger();

    void increment() {
        count.incrementAndGet();
    }

    int get() {
        return count.get();
    }
}
~~~

\`incrementAndGet()\` thực hiện một thao tác tăng nguyên tử với ngữ nghĩa memory visibility phù hợp. Mỗi cập nhật thành công có một **linearization point**: các lần tăng có thể được hiểu như có thứ tự tuần tự hợp lệ dù nhiều thread gọi đồng thời.

Các API thường gặp:

| Phương thức | Ý nghĩa |
|---|---|
| \`get()\` | Lấy giá trị hiện tại với ngữ nghĩa đọc volatile của API atomic. |
| \`set(v)\` | Ghi giá trị với ngữ nghĩa ghi volatile. |
| \`getAndIncrement()\` | Tăng 1 nguyên tử; **trả về giá trị cũ**. |
| \`incrementAndGet()\` | Tăng 1 nguyên tử; **trả về giá trị mới**. |
| \`addAndGet(delta)\` | Cộng \`delta\` nguyên tử; trả về giá trị mới. |
| \`getAndAdd(delta)\` | Cộng \`delta\` nguyên tử; trả về giá trị cũ. |
| \`compareAndSet(expected, update)\` | Nếu giá trị còn bằng \`expected\`, thử đổi thành \`update\` nguyên tử; trả boolean thành công/thất bại. |

Việc triển khai thường dựa trên cơ chế atomic/CAS do JVM và phần cứng hỗ trợ. CAS có thể phải thử lại dưới contention. Không suy diễn từ tên phương thức rằng mỗi lần tăng luôn hoàn thành trong thời gian cố định hoặc hoàn toàn không có chi phí.

**Không làm thế này để tăng atomic counter:**

~~~java
AtomicInteger count = new AtomicInteger();
int old = count.get();
count.set(old + 1); // hai lệnh atomic riêng nhưng tổ hợp KHÔNG atomic
~~~

Hai thread vẫn có thể \`get() = 0\`, rồi cùng \`set(1)\`. Dù từng lời gọi \`get\` và \`set\` có memory semantics đúng, chuỗi read–modify–write tổng thể vẫn không được bảo vệ. Phải dùng \`incrementAndGet()\`, \`getAndIncrement()\`, \`addAndGet()\` hoặc một vòng CAS đúng.

## 7. Cách sửa thứ ba: LongAdder khi nhiều thread tranh cùng counter

\`LongAdder\` tối ưu trường hợp **rất nhiều thread thường xuyên cộng dồn** cùng một chỉ số. Thay vì luôn cập nhật một vị trí đơn lẻ như \`AtomicLong\`, cách triển khai có thể chia các cập nhật ra nhiều ô nội bộ để giảm contention, rồi tổng hợp khi đọc.

~~~java
import java.util.concurrent.atomic.LongAdder;

class MetricsCounter {
    private final LongAdder requests = new LongAdder();

    void recordRequest() {
        requests.increment();
    }

    long total() {
        return requests.sum();
    }
}
~~~

Lợi thế và giới hạn:

- \`increment()\`/ \`add(n)\` phù hợp cho chỉ số như tổng số request, số sự kiện, số lượt truy cập có tần suất cao.
- \`sum()\` **không phải atomic snapshot** khi các thread khác vẫn đang cập nhật: giá trị đọc có thể không bao gồm các cập nhật xảy ra đồng thời.
- \`sumThenReset()\` cũng không phải lựa chọn an toàn để thu thập số liệu chính xác khi các thao tác cập nhật vẫn diễn ra đồng thời; tài liệu lưu ý chỉ dùng khi chắc chắn không có cập nhật đồng thời nếu cần giá trị đầy đủ.
- \`LongAdder\` không cung cấp \`compareAndSet\` trên tổng; vì thế không phù hợp nếu cần quy tắc “chỉ tăng khi giá trị chưa đạt giới hạn”.
- Lợi ích hiệu năng phụ thuộc workload, độ contention, số core, memory và JDK; không khẳng định luôn nhanh hơn \`AtomicLong\`.

**Chọn cách nào?**

| Yêu cầu | Lựa chọn thường hợp lý | Lý do |
|---|---|---|
| Một counter chính xác, cập nhật và đọc liên tục | \`AtomicInteger\`/\`AtomicLong\` | Atomic increment, API đơn giản, cung cấp CAS. |
| Nhiều trường dữ liệu phải giữ một bất biến chung | \`synchronized\`/\`Lock\` | Bảo vệ toàn bộ thao tác nhiều bước dưới cùng critical section. |
| Counter thống kê có nhiều writer, chỉ cần tổng hợp theo thời điểm | \`LongAdder\` | Giảm contention, chấp nhận \`sum()\` không phải snapshot nguyên tử khi đang ghi. |
| Cần thao tác có điều kiện như \`count < limit\` rồi tăng | CAS loop hoặc lock | Kiểm tra và cập nhật phải là một hành động logic nguyên tử. |
| Tình huống đơn luồng không chia sẻ biến | \`int\`/\`long\` thường | Không cần trả chi phí đồng bộ khi thực sự không có truy cập đồng thời. |

## 8. Ví dụ độc lập: so sánh ba cách tăng biến đếm an toàn

Chương trình dưới đây dùng hai thread, mỗi thread tăng 100.000 lần. Nó không dựa vào may rủi race để thành công: cả ba bộ đếm đều bảo vệ update. \`join()\` đảm bảo đã hoàn tất tất cả lần tăng trước khi đọc kết quả.

Lưu tệp **SafeCountersDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class SafeCountersDemo {
    interface Counter {
        void increment();
        long value();
    }

    static class SynchronizedCounter implements Counter {
        private long count;

        @Override
        public synchronized void increment() {
            count++;
        }

        @Override
        public synchronized long value() {
            return count;
        }
    }

    static class AtomicCounter implements Counter {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public void increment() {
            count.incrementAndGet();
        }

        @Override
        public long value() {
            return count.get();
        }
    }

    static class AdderCounter implements Counter {
        private final LongAdder count = new LongAdder();

        @Override
        public void increment() {
            count.increment();
        }

        @Override
        public long value() {
            return count.sum();
        }
    }

    static void repeat(Counter counter) {
        for (int i = 0; i < 100_000; i++) {
            counter.increment();
        }
    }

    static long run(Counter counter) throws InterruptedException {
        Thread t1 = new Thread(() -> repeat(counter), "worker-1");
        Thread t2 = new Thread(() -> repeat(counter), "worker-2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        return counter.value();
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("synchronized=" + run(new SynchronizedCounter()));
        System.out.println("atomic=" + run(new AtomicCounter()));
        System.out.println("longAdder=" + run(new AdderCounter()));
    }
}
~~~

Biên dịch, chạy:

~~~bash
javac SafeCountersDemo.java
java SafeCountersDemo
~~~

Output:

~~~text
synchronized=200000
atomic=200000
longAdder=200000
~~~

Tại sao \`LongAdder.sum()\` ở đây cho kết quả đầy đủ dù không phải atomic snapshot? Vì main đợi cả hai thread kết thúc qua \`join()\`; khi gọi \`sum()\` **không còn writer nào đang cập nhật**. Trong chương trình khác, nếu đọc \`sum()\` khi worker vẫn chạy, kết quả là tổng quan sát được tại một khoảng thời gian và có thể khác tổng tức thời.

**Không coi ba lượt đo này là benchmark.** Thứ tự chạy, warmup JIT, biến động OS và kích cỡ workload có thể làm thời gian thay đổi. Mục đích ví dụ là chứng minh tính đúng đắn, không chứng minh \`LongAdder\` nhanh hơn bao nhiêu phần trăm.

## 9. Lưu ý nâng cao: một chuỗi nhiều API atomic vẫn có thể sai

Một thiết kế dễ gặp là giới hạn số lượt đặt chỗ:

~~~java
AtomicInteger booked = new AtomicInteger();
int capacity = 10;

if (booked.get() < capacity) {
    booked.incrementAndGet(); // SAI nếu hai thread cùng vào nhánh if
}
~~~

Mặc dù \`get()\` và \`incrementAndGet()\` mỗi cái đều atomic, **phép kiểm tra điều kiện + tăng** không atomic. Khi \`booked=9\`, hai thread đều thấy \`9<10\` và đều tăng: cuối cùng có thể thành **11**, vi phạm sức chứa 10.

### Sửa bằng vòng compareAndSet

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class CapacityCounter {
    private final AtomicInteger booked = new AtomicInteger();

    public boolean tryBook(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0");
        }
        while (true) {
            int current = booked.get();
            if (current >= capacity) {
                return false;
            }
            if (booked.compareAndSet(current, current + 1)) {
                return true;
            }
            // Có cập nhật cạnh tranh: đọc lại trạng thái mới rồi thử lại.
        }
    }

    public int count() {
        return booked.get();
    }

    public static void main(String[] args) {
        CapacityCounter counter = new CapacityCounter();
        System.out.println(counter.tryBook(1));
        System.out.println(counter.tryBook(1));
        System.out.println(counter.count());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CapacityCounter.java
java CapacityCounter
~~~

Output:

~~~text
true
false
1
~~~

CAS thành công chỉ khi giá trị vẫn là \`current\` tại bước cập nhật; nếu thread khác đã thay đổi giá trị, lời gọi thất bại và vòng lặp thử lại dựa trên trạng thái mới. Mẫu này bảo vệ bất biến \`count <= capacity\` khi tất cả thay đổi có liên quan đi qua cùng giao thức, biến \`capacity\` được truyền nhất quán và không có cập nhật bên ngoài. Ví dụ xử lý một bộ đếm in-memory trong một JVM; nó không tự đồng bộ dữ liệu với hệ thống ngoài hoặc nhiều process.

**Một caution về nhiều trạng thái:** Một số bất biến liên quan tới nhiều biến, nhiều object hoặc tài nguyên bên ngoài không thể sửa bằng một \`AtomicInteger\`. Khi kiểm tra và cập nhật nhiều trường phải nhất quán, hãy thiết kế critical section chung, state bất biến thay thế bằng CAS, hoặc cơ chế phối hợp thích hợp cho phạm vi tài nguyên đó.

## 10. Lock, atomic và volatile dưới góc nhìn happens-before

### synchronized

Một thread rời monitor (\`unlock\`) happens-before một thread khác vào lại (\`lock\`) **chính monitor đó**. Trong cùng critical section, mutual exclusion giúp không bị hai thread cùng read–modify–write trên biến đếm. Đọc/ghi ngoài lock, hoặc dùng hai đối tượng lock khác nhau, làm mất bảo đảm đồng bộ này.

### AtomicInteger

Các thao tác \`get()\`, \`set()\` và atomic update có memory effect được định nghĩa; \`incrementAndGet()\` là một update nguyên tử. Dùng nhiều lời gọi tách rời như \`get()\` rồi \`set(...)\` sẽ tạo khoảng chen ngang. Do vậy cần chọn thao tác cập nhật phù hợp với yêu cầu **của cả hành động nghiệp vụ**.

### volatile

Visibility và ordering của volatile giúp thread quan sát cập nhật theo quy tắc happens-before nhưng không cấp mutual exclusion cho read–modify–write. \`volatile int count++\` vẫn racy. Ngược lại, một cờ \`volatile boolean running\` chỉ cần đọc/ghi trạng thái độc lập có thể phù hợp nếu không có bất biến phức tạp.

### join(), CountDownLatch và Future.get()

Đây là công cụ **phối hợp vòng đời và đồng bộ quan sát**, không tự bảo vệ phần tăng sai:

- Sau khi một thread kết thúc thành công và thread khác \`join()\` vào nó, các hành động của thread hoàn tất happens-before các hành động sau \`join()\` của thread chờ.
- \`CountDownLatch\` cung cấp quan hệ happens-before qua \`countDown()\` và \`await()\` theo hợp đồng API; latch thường giúp xác định thời điểm bắt đầu, hoàn tất hoặc dàn dựng thứ tự.
- Với \`Future\`, các hành động của tác vụ bất đồng bộ happens-before những hành động sau \`Future.get()\` tương ứng. Nhưng \`Future.get()\` không làm cho biến đếm đã được tăng bởi nhiều worker tự nhiên trở thành atomic.

Chỉ \`join()\` sau khi **tất cả việc ghi đã hoàn thành** giúp main đọc kết quả cuối có cơ sở. Nếu không chờ, việc in biến đếm có thể thiếu cả những lần tăng mà worker còn chưa chạy xong — đây là vấn đề thời điểm quan sát bên cạnh lost update.

## 11. Các lỗi phổ biến và cách nhận diện

### Lỗi 1: Biến chung là static nên được “bảo vệ”

\`static\` chỉ làm trường thuộc class, nghĩa là nhiều instance có thể cùng chia sẻ giá trị. Nó **không** có thuộc tính atomic hoặc synchronized. \`final\` trên tham chiếu counter cũng không làm các thao tác thay đổi trạng thái bên trong counter trở nên thread-safe.

### Lỗi 2: Mỗi thread giữ một Counter khác nhau

Nếu mỗi thread tăng instance riêng, bạn không còn thử nghiệm lost update trên cùng một biến. Khi thiết kế dự kiến tổng chung, cần đúng một shared counter được truyền cho các worker. Đồng thời nếu dùng \`synchronized\`, tất cả các worker cần đồng bộ trên cùng instance/monitor.

### Lỗi 3: synchronized đúng phương thức nhưng các đường truy cập khác bỏ khóa

Một phương thức synchronized không cứu được những lời ghi trực tiếp đến trường không được bảo vệ. Khuyến nghị \`private\` field và API cập nhật nhất quán. Tránh để lộ tham chiếu mutable state rồi thay đổi nó từ nơi khác mà không giữ lock.

### Lỗi 4: Dùng volatile cho phép tăng hoặc kiểm tra-điều kiện-rồi-tăng

\`volatile\` không cung cấp atomic read–modify–write; \`AtomicInteger.get()\` cộng với \`AtomicInteger.set()\` cũng không. Chọn \`incrementAndGet\`, CAS hoặc lock bao toàn thao tác.

### Lỗi 5: Hai thread tự start mà không join nhưng đã kiểm tra count

\`start()\` chỉ yêu cầu chạy, không chờ kết thúc. Nếu main in kết quả ngay sau khi start, giá trị có thể nhỏ vì chưa chạy xong, dù đã dùng counter atomic. Đợi \`join()\`, \`Future.get()\`, \`CountDownLatch\` theo đúng semantics của bài.

### Lỗi 6: Dùng int cho tổng lớn bất chấp overflow

\`int\` có phạm vi -2^31 tới 2^31-1. Khi vượt phạm vi, phép cộng số nguyên Java overflow theo quy tắc số nguyên 32-bit; counter có thể thành số âm dù mọi update đã atomic. \`AtomicInteger\` **không tránh overflow**. Chọn \`long\`, \`AtomicLong\`, \`LongAdder\` hoặc thiết kế giới hạn/overflow policy theo yêu cầu.

### Lỗi 7: Dùng LongAdder như số dư chính xác tức thời

\`LongAdder.sum()\` khi writer vẫn chạy không phải snapshot nguyên tử. Với metric cộng dồn, đây là tradeoff hợp lý; với một invariant cần quyết định “nếu còn chỗ thì tăng”, phải dùng atomic CAS hoặc lock.

### Lỗi 8: Cho rằng race chỉ có trên nhiều CPU core

Ngay cả trên một core, scheduler vẫn có thể chuyển thread giữa các bước. Nhiều core tăng khả năng chạy đồng thời nhưng không phải điều kiện bắt buộc cho lost update. Thread scheduling và Java Memory Model đều là phần của bài toán.

### Lỗi 9: Nhầm mất cập nhật với vấn đề thứ tự log

Log in ra theo thứ tự bất định giữa các thread không nhất thiết là lost update. Hãy kiểm tra **trạng thái/invariant** của shared counter sau khi các task hoàn tất. Phải có một kỳ vọng chính xác: N worker × M lần tăng thì giá trị cần đạt N×M nếu không có overflow và không bỏ/cancel task.

### Lỗi 10: Dùng benchmark nhanh để kết luận giải pháp “tốt nhất”

\`synchronized\` có thể rất phù hợp vì đơn giản, \`AtomicInteger\` phù hợp thao tác một counter, \`LongAdder\` phù hợp metric nhiều writer. Chi phí thực tế thay đổi theo contention, số core, runtime và tỷ lệ đọc/ghi; kiểm tra correctness trước, benchmark đúng workload sau.

## 12. Hướng dẫn thử nghiệm và chẩn đoán

Một chiến lược kiểm tra bài bản gồm hai lớp:

**Lớp 1 — thử nghiệm interleaving có điều khiển:** Dùng \`CountDownLatch\` như ví dụ đầu để chứng minh hai thread thật sự có thể đọc cùng snapshot và ghi đè nhau. Đây là **minh chứng logic xác định**, không chỉ chờ scheduler tạo may rủi.

**Lớp 2 — thử nghiệm dưới tải:** Chạy nhiều task cùng tăng một counter chung, start đồng thời bằng barrier/latch, sau đó join hoặc đợi Future. Đối chiếu với \`expected = threads × incrementsPerThread\`. Lặp trên nhiều cấu hình. Kết quả đúng của một lần stress **không chứng minh** mã không có race; kết quả sai là bằng chứng có vấn đề. Khi viết test đồng thời cần bảo đảm task không bị cancel/interrupt ngoài dự kiến và phép tính expected không overflow.

Để đi sâu hơn, có thể dùng công cụ chuyên biệt như **OpenJDK jcstress** nhằm kiểm tra các kết quả quan sát được dưới nhiều interleaving/JMM, hoặc xem thread dump/JFR khi nghi ngờ contention/blocked threads. Thread dump chủ yếu cho biết trạng thái đang chờ; nó không tự chứng minh \`count++\` atomic. Trong mã production, luôn xem cả dữ liệu counter, tỷ lệ lỗi, logic retry và các cập nhật bị bỏ.

### Checklist review code

- **Một giá trị chia sẻ?** Xác nhận tất cả worker dùng cùng counter, không clone biến hoặc tự tạo counter riêng.
- **Có read–modify–write?** \`++\`, \`--\`, \`x += n\`, \`map.put(key, map.get(key)+1)\` đều cần đánh giá tính nguyên tử.
- **Có conditional update?** \`if (count < limit) count++\` cần atomicity cả kiểm tra lẫn cập nhật.
- **Đồng bộ ở đâu?** Cùng monitor, cùng Lock, atomic API hoặc một giao thức thread confinement đúng.
- **Đọc kết quả lúc nào?** Chờ công việc xong; phân biệt đọc snapshot trong lúc có writer với đọc tổng cuối.
- **Kiểu dữ liệu có đủ rộng?** Kiểm tra overflow, giới hạn nghiệp vụ và yêu cầu metric.
- **Có cập nhật nhiều trường?** Một atomic counter không tự giữ tính nhất quán của mọi trường liên quan.

## 13. Bài tập tự kiểm tra

**Câu 1.** Hai thread mỗi thread tăng \`count\` 10.000 lần với \`volatile int count\`. Giá trị có chắc bằng 20.000 không?

**Đáp án:** Không. \`volatile\` giúp visibility và ordering nhưng không làm \`++\` atomic; interleaving có thể gây mất update.

**Câu 2.** Một thread sử dụng \`counter.incrementAndGet()\`, thread khác dùng \`counter.get(); counter.set(old + 1)\` trên cùng \`AtomicInteger\`. Có thread-safe không?

**Đáp án:** Không. Đường \`get()+set()\` vẫn là compound update không atomic; mọi writer phải tuân theo cùng giao thức cập nhật đúng.

**Câu 3.** \`synchronized\` trên hai instance khác nhau có bảo vệ cùng một biến \`static\` không?

**Đáp án:** Không, nếu mỗi instance dùng monitor \`this\` riêng. Phải dùng **chung một lock** cho biến \`static\`, hoặc atomic field phù hợp.

**Câu 4.** \`LongAdder.sum()\` có phải snapshot chính xác trong lúc nhiều worker đang gọi \`increment()\`?

**Đáp án:** Không. Nó phù hợp tổng metric có cập nhật đồng thời, nhưng không bảo đảm snapshot atomic. Sau khi mọi worker kết thúc và đã join, sum phản ánh các lần tăng đã hoàn thành.

**Câu 5.** Vì sao \`Future.get()\` không đủ để tránh lost update?

**Đáp án:** \`get()\` giúp đợi task và quan sát kết quả/ngoại lệ với memory consistency tương ứng; nếu chính tác vụ đã làm sai \`count++\` song song, chờ hoàn tất không khôi phục cập nhật đã mất.

## 14. Kết luận và tài liệu tra cứu

Một biến đếm chung cần đáp ứng **hai điều kiện độc lập**: cập nhật phải nguyên tử nếu nhiều thread cùng tăng; kết quả phải được quan sát khi đã có quan hệ đồng bộ và tại đúng thời điểm. \`count++\` thông thường thiếu tính nguyên tử. \`volatile\` chỉ giải quyết phần visibility/ordering, không khóa ba bước đọc–tính–ghi. Chọn \`synchronized\`/\`Lock\` khi thao tác nhiều bước cần bất biến chung, \`AtomicInteger\` hoặc \`AtomicLong\` khi cập nhật một counter cần chính xác và có thể CAS, \`LongAdder\` cho metric tăng nhiều với chấp nhận snapshot không nguyên tử trong lúc còn writer.

Tài liệu API chuẩn:

- Java Language Specification §17 — Threads and Locks: https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html
- \`AtomicInteger\`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html
- \`LongAdder\`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html
- \`CountDownLatch\`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html
- \`ReentrantLock\`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html

**Ghi nhớ cốt lõi:** *Một thao tác đọc nguyên tử cộng với một thao tác ghi nguyên tử không tự tạo thành một phép tăng nguyên tử.* Khi sửa lost update, phải bảo vệ toàn bộ read–modify–write — và bảo vệ cả điều kiện liên quan nếu có.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="race-conditions-004">Read-modify-write không phải một thao tác atomic</a>

<details>
<summary>Xem chi tiết</summary>

# Read-modify-write không phải một thao tác atomic trong Java

> **Ý chính:** Các biểu thức `counter++`, `counter += 1` hay `counter = counter + 1` trông như một câu lệnh nhưng thực chất cần **đọc giá trị hiện tại → tính giá trị mới → ghi trả lại**. Khi nhiều thread cùng cập nhật một trạng thái chia sẻ mà không bảo vệ **toàn bộ chuỗi thao tác**, một cập nhật có thể bị mất (*lost update*). `volatile` giúp về visibility và ordering, nhưng **không biến phép tăng gồm nhiều bước thành atomic**.

Bài học thuộc nhóm **Race Conditions** trong Java concurrency nói chung. Các ví dụ sử dụng Java SE (Java 17+), `Thread`, `synchronized` và `java.util.concurrent`; không xét cơ chế của Spring.

## 1. Atomic nghĩa là gì trong tình huống này?

Một thao tác atomic, đối với các thread cùng quan sát trạng thái liên quan, có hiệu ứng như thể nó diễn ra **trọn vẹn ở một thời điểm logic**; các thread khác không quan sát trạng thái trung gian của chính thao tác đó. Khi nói một cập nhật kiểu read-modify-write (RMW) là atomic, ta muốn việc **đọc và ghi dựa trên giá trị đã đọc** là một đơn vị không thể bị một phép cập nhật cạnh tranh chen vào làm mất hiệu lực.

Ví dụ cần tăng biến đếm chung từ `7` lên `8`:

~~~text
1. READ:   old = counter    // old = 7
2. MODIFY: next = old + 1   // next = 8
3. WRITE:  counter = next   // counter = 8
~~~

Một thread đơn lẻ có thể làm đúng. Nhưng hai thread cùng làm những bước này mà không có đồng bộ thì lịch xen kẽ có thể khiến **cả hai đọc cùng một giá trị cũ** và cùng ghi kết quả giống nhau.

Phân biệt những điều thường bị gộp nhầm:

| Thuộc tính | Câu hỏi | Có bảo đảm RMW atomic không? |
| --- | --- | --- |
| **Atomic read/write riêng lẻ** | Một lần đọc/ghi một giá trị có bị đọc nửa chừng không? | **Không đủ.** Đọc và ghi vẫn là hai thời điểm. |
| **Visibility** | Thread khác có thấy thay đổi đã được công bố không? | **Không đủ.** Thấy giá trị mới không ngăn hai thread cùng đọc giá trị cũ. |
| **Ordering / happens-before** | Những hành động nào phải được quan sát theo trật tự nhất định? | **Không tự bảo đảm** toàn bộ phép RMW là một khối duy nhất. |
| **Atomic RMW** | Một cập nhật cạnh tranh có thể chen giữa phần đọc và phần ghi không? | **Có**, đây chính là bảo đảm cần để tránh lost update đối với một counter. |
| **Transaction nhiều biến** | Nhiều trường có được cập nhật như một invariant duy nhất không? | Một atomic counter **không đủ**; phải bảo vệ toàn bộ invariant. |

Từ “atomic” luôn phải đi kèm **phạm vi**: atomic cho một biến, một entry map, hay một cập nhật trên nhiều trường? Một thao tác có thể atomic trong phạm vi hẹp nhưng không atomic đối với logic nghiệp vụ lớn hơn.

## 2. Race condition và lost update diễn ra như thế nào?

Giả sử `counter = 7`, hai thread A và B đồng thời thực hiện `counter++`:

| Bước thời gian | Thread A | Thread B | Giá trị chung |
| --- | --- | --- | --- |
| 1 | Đọc `counter`, nhận `7` | | `7` |
| 2 | | Đọc `counter`, nhận `7` | `7` |
| 3 | Tính `7 + 1 = 8` | | `7` |
| 4 | | Tính `7 + 1 = 8` | `7` |
| 5 | Ghi `8` | | `8` |
| 6 | | Ghi `8` | `8` |

Sau **hai lần tăng**, kết quả mong đợi là `9` nhưng thực tế có thể là `8`. Một cập nhật đã bị mất: lần ghi sau dựa trên một snapshot cũ và không tính đến cập nhật đã xảy ra.

Đây là **lost-update race** trên dữ liệu dùng chung, không phải lỗi do thread nào “chạy quá nhanh”, cũng không liên quan đến thứ tự ưu tiên scheduling. Thêm `Thread.sleep` chỉ thay đổi cơ hội xen kẽ; không chữa nguyên nhân.

Các biểu thức dưới đây đều là read-modify-write đối với biến `int` chia sẻ:

~~~java
counter++;
++counter;
counter += 1;
counter = counter + 1;
~~~

Với `counter++` và `++counter`, giá trị trả về của biểu thức khác nhau (giá trị cũ so với giá trị mới), **nhưng cả hai đều cần RMW**. Trong mã Java, “một câu lệnh”, “một toán tử” hay “một dòng source” không đồng nghĩa “một thao tác atomic”. Bytecode, JIT và CPU có thể triển khai khác nhau; điều phải dựa vào là **hợp đồng Java Memory Model/API**, không phải suy luận máy móc từ số lệnh assembly hay bytecode.

### Race condition khác data race như thế nào?

- **Race condition**: kết quả hoặc tính đúng đắn phụ thuộc vào cách các sự kiện đồng thời xen kẽ; lost update là ví dụ điển hình.
- **Data race** trong ngôn ngữ Java Memory Model nói về các truy cập xung đột trên cùng biến mà không được sắp xếp bằng quan hệ *happens-before* thích hợp, khi ít nhất một truy cập là ghi và các truy cập không thuộc loại được đồng bộ như `volatile`. 
- Một thiết kế có thể mắc **race condition logic dù không có data race trên từng truy cập**. Ví dụ dùng `volatile int` khiến các lần đọc/ghi riêng lẻ có ngữ nghĩa đồng bộ, nhưng việc tách đọc rồi ghi vẫn cho phép lost update. Vì thế “đã hết data race” và “thuật toán đúng” là hai nhận định khác nhau.

## 3. Ví dụ 1 — tái hiện lost update một cách xác định

Thay vì chạy vòng lặp tăng counter hàng triệu lần rồi hy vọng race xuất hiện, ta **chủ động đồng bộ điểm xen kẽ** để cả hai thread đều đọc giá trị cũ trước khi một trong hai được phép ghi.

Tạo tệp `LostUpdate.java` (Java 17+):

~~~java
import java.util.concurrent.CountDownLatch;

public class LostUpdate {
    private static volatile int counter = 0;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch bothHaveRead = new CountDownLatch(2);
        CountDownLatch allowWrite = new CountDownLatch(1);

        Runnable increment = () -> {
            int oldValue = counter;  // READ: cả hai sẽ đọc 0.
            bothHaveRead.countDown();

            try {
                allowWrite.await();  // Đợi đến khi cả hai đã đọc.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            counter = oldValue + 1;  // WRITE: cả hai ghi 1.
        };

        Thread first = new Thread(increment, "first");
        Thread second = new Thread(increment, "second");

        first.start();
        second.start();

        bothHaveRead.await();  // Chắc chắn cả hai đã lấy snapshot.
        allowWrite.countDown();

        first.join();
        second.join();

        System.out.println("expected = 2");
        System.out.println("actual = " + counter);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac LostUpdate.java
java LostUpdate
~~~

Kết quả khi không có interruption bên ngoài:

~~~text
expected = 2
actual = 1
~~~

**Giải thích bảo đảm:**

1. Mỗi worker đọc `counter` vào biến local `oldValue`, rồi giảm latch `bothHaveRead`.
2. Main đợi `bothHaveRead.await()` hoàn tất, tức cả hai đã đọc trước khi main gọi `allowWrite.countDown()`.
3. Khi được phép ghi, mỗi worker đều ghi `0 + 1`. Hai lần ghi cùng giá trị khiến kết quả cuối là `1`, bất kể thứ tự hai lần ghi.
4. Main `join()` cả hai thread trước khi in, bảo đảm các phép ghi đã hoàn tất và các hiệu ứng từ worker có quan hệ *happens-before* thích hợp với phần quan sát sau `join()`.

Việc đặt `volatile` ở `counter` là **cố ý**: ngay cả khi mỗi lần đọc/ghi có ngữ nghĩa visibility phù hợp của volatile, hai bước `READ` và `WRITE` vẫn tách biệt, không tạo một cập nhật atomic. Bản thân `CountDownLatch` ở ví dụ dùng để **ép lịch xen kẽ nhằm chứng minh lỗi**, không tự sửa lost update. Trong phiên bản thực tế khi muốn tăng nguyên tử, đừng tách một phép tính thành hai thời điểm mà không bảo vệ chúng.

## 4. `volatile` bảo đảm gì, và không bảo đảm gì?

Trong Java, `volatile` tạo ra các bảo đảm về **visibility và ordering** cho truy cập đến biến được khai báo volatile. Đặc biệt, một lần ghi volatile có quan hệ *synchronizes-with* các lần đọc tiếp theo của cùng biến volatile theo quy tắc Java Memory Model. Các lần truy cập volatile được xét trong trật tự đồng bộ phù hợp, và những read/write volatile riêng lẻ có ngữ nghĩa atomic trên giá trị được truy cập.

Tuy nhiên:

~~~java
// Minh họa lỗi logic, không phải chương trình độc lập.
private static volatile int counter = 0;

static void increment() {
    counter++; // Đọc volatile -> cộng 1 -> ghi volatile.
}
~~~

Đoạn này **vẫn có thể lost update**. `volatile` không khóa các thread khác trong khoảng giữa đọc và ghi, không thay cho `synchronized`, `Lock` hoặc phương thức atomic RMW như `AtomicInteger.incrementAndGet()`.

Đừng nhầm những trường hợp khác:

- `volatile boolean stopped` có thể là cờ báo dừng đơn giản khi một thread ghi và các thread khác đọc, nếu logic chỉ yêu cầu publish/observe cờ.
- `volatile int counter` với **nhiều writer** dùng `counter++` không bảo vệ đủ cập nhật.
- Đọc một `volatile long` là atomic ở mức **một lần read**; vẫn không khiến `value++` atomic.
- `final` và khả năng công bố an toàn đối tượng thuộc cơ chế khác; chúng không tự biến phép tăng trên trạng thái mutable thành atomic.

Khi phân tích concurrency, hỏi hai câu riêng: “Thread có thấy giá trị đúng/được công bố chưa?” và “Có thể ai đó cập nhật ở giữa read và write không?”. `volatile` giải quyết được một số vấn đề của câu thứ nhất, không giải quyết câu thứ hai.

## 5. Cách sửa 1 — khóa toàn bộ read-modify-write bằng `synchronized`

Một monitor lock chung giúp chỉ một thread tại một thời điểm đi qua critical section được bảo vệ bởi **cùng khóa**. Với counter, đặt phép đọc, tính và ghi trong cùng một khối khóa.

Tạo `SynchronizedCounter.java` (Java 17+):

~~~java
public class SynchronizedCounter {
    private static final Object LOCK = new Object();
    private static int counter = 0;

    private static void increment() {
        synchronized (LOCK) {
            counter++;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Runnable work = () -> {
            for (int i = 0; i < 100_000; i++) {
                increment();
            }
        };

        Thread first = new Thread(work, "first");
        Thread second = new Thread(work, "second");
        first.start();
        second.start();

        first.join();
        second.join();
        System.out.println("counter = " + counter);
    }
}
~~~

Chạy:

~~~bash
javac SynchronizedCounter.java
java SynchronizedCounter
~~~

Kết quả:

~~~text
counter = 200000
~~~

Lý do đúng:

- Mỗi lần `increment()` lấy **cùng** `LOCK` nên hai thread không thể cùng thực hiện phần RMW tại một thời điểm.
- Monitor unlock có quan hệ *happens-before* với lần lock tiếp theo cùng monitor, cho phép thread vào sau quan sát cập nhật từ thread trước.
- `join()` bảo đảm main đợi đủ 200.000 lần gọi `increment()` trước khi đọc/in kết quả.
- Biến `counter` không bắt buộc là `volatile` nếu **mọi truy cập cạnh tranh** đều theo cùng nguyên tắc đồng bộ và đọc kết quả được điều phối đúng như ở đây.

Dùng **khóa đúng và nhất quán** rất quan trọng. Khóa ở từng thread bằng một `new Object()` khác nhau là vô ích. Nếu một đoạn ghi sử dụng `synchronized(LOCK)` nhưng đoạn đọc/ghi cạnh tranh khác truy cập trần vào `counter`, thiết kế vẫn có thể sai. Nên để trường chia sẻ là `private` và đóng gói đường cập nhật trong một API rõ ràng.

### Khi nào dùng `ReentrantLock`?

`java.util.concurrent.locks.ReentrantLock` cũng có thể bảo vệ toàn bộ RMW, hữu ích nếu cần `tryLock`, khóa có timeout, interruptible lock acquisition hoặc điều kiện `Condition`. Mẫu đúng:

~~~java
// Ví dụ minh họa; lock là một ReentrantLock dùng chung.
lock.lock();
try {
    counter++;
} finally {
    lock.unlock();
}
~~~

Không chỉ khóa phần `counter = ...` sau khi đã đọc outside lock; **đọc để tính giá trị mới cũng phải nằm trong cùng critical section**. `finally` bảo đảm release lock ngay cả khi công việc bên trong ném exception.

## 6. Cách sửa 2 — dùng atomic read-modify-write từ `AtomicInteger`

Khi chỉ cần thao tác atomic trên **một biến đếm**, `AtomicInteger` thường diễn đạt ý định rõ ràng hơn so với một monitor dành riêng cho counter. Các phương thức quan trọng:

| API | Hiệu ứng atomic | Giá trị trả về |
| --- | --- | --- |
| `getAndIncrement()` | Tăng 1 | Giá trị **trước khi** tăng |
| `incrementAndGet()` | Tăng 1 | Giá trị **sau khi** tăng |
| `getAndAdd(delta)` | Cộng `delta` | Giá trị cũ |
| `addAndGet(delta)` | Cộng `delta` | Giá trị mới |
| `compareAndSet(expected, update)` | Chỉ ghi `update` nếu giá trị hiện tại bằng `expected` | `true` khi cập nhật thành công |
| `updateAndGet(operator)` | Dùng hàm biến đổi, tự thử lại nếu có cạnh tranh | Giá trị mới |

Tạo `AtomicCounter.java` (Java 17+):

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounter {
    private static final AtomicInteger counter = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        Runnable work = () -> {
            for (int i = 0; i < 100_000; i++) {
                counter.incrementAndGet();
            }
        };

        Thread first = new Thread(work, "first");
        Thread second = new Thread(work, "second");
        first.start();
        second.start();

        first.join();
        second.join();

        System.out.println("counter = " + counter.get());
    }
}
~~~

Chạy:

~~~bash
javac AtomicCounter.java
java AtomicCounter
~~~

Kết quả:

~~~text
counter = 200000
~~~

`incrementAndGet()` bảo vệ một cập nhật RMW trên **chính giá trị của đối tượng atomic đó**. Khi nhiều thread gọi, cập nhật trên biến atomic không bị ghi đè mất theo tình huống cả hai cùng lấy một snapshot cũ rồi ghi lại thiếu kiểm tra.

**Điểm dễ sai:** đọc bằng `counter.get()` rồi tự tính và gọi `counter.set(...)` là **hai operation riêng**, không phải RMW atomic:

~~~java
// Sai nếu nhiều writer cạnh tranh:
int oldValue = counter.get();
counter.set(oldValue + 1);
~~~

Dù `get()` và `set()` từng cái có ngữ nghĩa thread-safe, tổ hợp trên vẫn có cửa sổ giữa chúng. Phải dùng **đúng phương thức atomic RMW**, chẳng hạn `incrementAndGet()` hoặc `compareAndSet(...)` trong vòng lặp phù hợp.

## 7. CAS: làm rõ cách atomic cập nhật khi giá trị có thể thay đổi

**Compare-and-set (CAS)** kiểm tra một giá trị kỳ vọng và chỉ thay đổi khi nó vẫn khớp, trong một thao tác atomic. Mô hình:

~~~text
current = atomic.get()
next    = f(current)
if atomic.compareAndSet(current, next):
    cập nhật thành công
else:
    giá trị đã thay đổi hoặc phép CAS thất bại
    -> đọc lại và thử tính từ trạng thái mới
~~~

Tạo `CasCounter.java` (Java 17+):

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class CasCounter {
    private static final AtomicInteger counter = new AtomicInteger();

    private static void incrementWithCas() {
        while (true) {
            int previous = counter.get();
            int updated = previous + 1;
            if (counter.compareAndSet(previous, updated)) {
                return;
            }
            // Có cạnh tranh: thử lại với giá trị hiện thời.
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Runnable work = () -> {
            for (int i = 0; i < 100_000; i++) {
                incrementWithCas();
            }
        };

        Thread first = new Thread(work);
        Thread second = new Thread(work);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("counter = " + counter.get());
    }
}
~~~

Chạy:

~~~bash
javac CasCounter.java
java CasCounter
~~~

Kết quả:

~~~text
counter = 200000
~~~

Phương thức `compareAndSet(previous, updated)` không cho phép ghi dựa trên một giá trị đã cũ mà không kiểm tra lại. Khi một thread khác thay đổi counter giữa `get` và CAS, CAS thất bại và vòng lặp phải tính lại từ trạng thái hiện thời. Kết quả chính xác kể cả khi lịch chạy xen kẽ bất kỳ, miễn các thread hoàn thành.

### Lưu ý khi dùng `updateAndGet()` hoặc CAS loop

~~~java
int newValue = counter.updateAndGet(current -> current + 1);
~~~

Đây là cách ngắn gọn để diễn đạt biến đổi atomic. Tuy nhiên, hàm update **có thể được áp dụng nhiều lần** vì thư viện phải retry khi bị cạnh tranh. Do đó:

- Hàm update nên **thuần (pure)**, không tạo side effect bên ngoài như ghi cơ sở dữ liệu, gửi thông báo, cộng một counter khác hay ghi log mà bạn kỳ vọng chỉ thực hiện đúng một lần.
- Không đọc trạng thái mutable bên ngoài mà thiếu nhất quán rồi kỳ vọng CAS trên một biến bảo vệ luôn dữ liệu ngoài biến đó.
- Không biến một vòng CAS có thể retry thành giao thức nhiều tài nguyên khi chưa thiết kế cơ chế đồng bộ chung.
- Nếu có yêu cầu tuyệt đối về thứ tự hoạt động bên ngoài, cần điều phối riêng; atomic trên một biến không tự đảm bảo *exactly-once* cho các side effect.

**CAS không đồng nghĩa mọi thuật toán CAS đều đơn giản hoặc hoàn toàn không có vấn đề cạnh tranh.** Với các cấu trúc trạng thái phức tạp, còn có vấn đề như ABA (giá trị đi A → B → A khiến CAS đơn thuần không nhận biết sự thay đổi trung gian), retry nhiều dưới contention, starvation và yêu cầu công bố trạng thái đúng. Với phép tăng counter `int` đơn giản, `AtomicInteger.incrementAndGet()` thường là lựa chọn dễ đọc hơn tự viết loop.

## 8. Atomic một biến không đủ cho invariant nhiều biến

Giả sử tài khoản có hai số `debit` và `credit` cần được cập nhật cùng nhau để bảo đảm invariant tổng số bằng 0. Dùng hai `AtomicInteger` riêng rồi làm:

~~~java
// Chỉ minh họa tình huống sai về tính nguyên tử của invariant.
debit.addAndGet(10);
credit.addAndGet(-10);
~~~

Từng phép cập nhật có thể atomic riêng, nhưng thread khác vẫn có thể quan sát trạng thái **ở giữa hai phép**. Nếu logic yêu cầu cả hai thay đổi như **một transaction nhất quán**, hãy bảo vệ bằng cùng một `synchronized`/`Lock`, hoặc biểu diễn toàn bộ trạng thái bằng một đối tượng immutable và thay thế thông qua `AtomicReference<State>` với CAS toàn bộ state.

Ví dụ nếu muốn giữ invariant `debit + credit == 0` trong một snapshot:

~~~java
// Minh họa kiểu dữ liệu cho một state bất biến.
record BalanceState(int debit, int credit) {}
~~~

Một `AtomicReference<BalanceState>` có thể giữ một phiên bản trạng thái nhất quán và cập nhật cả hai trường trong một CAS nếu tính toán đúng và retry từ state mới. Không đủ nếu các nguồn sự thật còn nằm ngoài đối tượng state đó; atomicity chỉ áp dụng đúng vùng đã được thiết kế.

Câu hỏi đúng không phải “dùng lớp `Atomic*` chưa?”, mà là **tập dữ liệu cần cập nhật nhất quán có nằm trọn trong cùng một thao tác atomic không?**

## 9. RMW trên collection cũng có thể bị lost update

Sai lầm không chỉ có ở biến `int`. Với một map chia sẻ:

~~~java
// Minh họa lỗi logic, không phải chương trình độc lập.
// map là ConcurrentHashMap<String, Integer>.
Integer current = map.get("jobs");
int next = (current == null ? 0 : current) + 1;
map.put("jobs", next);
~~~

`ConcurrentHashMap.get()` và `put()` từng cái là thao tác concurrent-safe theo hợp đồng, nhưng **get → tính → put** không nhất thiết atomic khi thread khác cũng cập nhật cùng key. Hai thread có thể đọc cùng giá trị cũ và cuối cùng chỉ tăng 1 lần.

`ConcurrentHashMap.merge`/`compute` cung cấp thao tác cập nhật atomic cho một key theo API. Tạo `MapCounter.java` (Java 17+):

~~~java
import java.util.concurrent.ConcurrentHashMap;

public class MapCounter {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, Integer> counts =
                new ConcurrentHashMap<>();

        Runnable work = () -> {
            for (int i = 0; i < 100_000; i++) {
                counts.merge("jobs", 1, Integer::sum);
            }
        };

        Thread first = new Thread(work);
        Thread second = new Thread(work);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("jobs = " + counts.get("jobs"));
    }
}
~~~

Chạy:

~~~bash
javac MapCounter.java
java MapCounter
~~~

Kết quả:

~~~text
jobs = 200000
~~~

Ở đây `merge("jobs", 1, Integer::sum)` thực hiện cập nhật cho một key theo hợp đồng atomic của `ConcurrentHashMap`. Nếu key vắng mặt, giá trị ban đầu là `1`; khi tồn tại, hàm gộp cộng thêm 1. Hàm cập nhật nên ngắn gọn và không cố cập nhật đệ quy những mapping khác trong cùng map. Đừng lầm `ConcurrentHashMap` làm **mọi chuỗi thao tác** trên nhiều key tự động atomic.

Tương tự, ngay cả với `Collections.synchronizedMap(...)`, chỉ đồng bộ từng method call không tự bảo vệ **toàn bộ compound action** nếu bạn đọc rồi ghi ở hai lời gọi tách biệt. Cần một vùng khóa chung hoặc thao tác nguyên tử được API hỗ trợ.

## 10. Khi nào dùng `LongAdder` thay cho `AtomicLong`?

`LongAdder` được thiết kế để giảm contention khi **nhiều thread cập nhật một thống kê dạng tổng**. Ví dụ:

~~~java
// Minh họa, biến adder dùng chung.
java.util.concurrent.atomic.LongAdder adder =
        new java.util.concurrent.atomic.LongAdder();
adder.increment();
long snapshot = adder.sum();
~~~

`LongAdder.increment()` phù hợp cho các lần ghi thống kê cạnh tranh; nhưng `sum()` **không phải snapshot atomic** đối với một loạt cập nhật đang diễn ra đồng thời. Giá trị đọc có thể không bao gồm một số cập nhật đồng thời. Nếu một quyết định nghiệp vụ phụ thuộc vào *“giá trị counter chính xác tại một thời điểm tuyến tính hóa”*, hãy cân nhắc `AtomicLong` hoặc cơ chế đồng bộ rõ ràng.

| Nhu cầu | Công cụ thường phù hợp | Cần lưu ý |
| --- | --- | --- |
| Counter chính xác, phép tăng/giảm atomic trên một biến | `AtomicInteger` / `AtomicLong` | `get` rồi `set` vẫn không phải RMW atomic |
| Bảo vệ nhiều hành động hay nhiều trường thành một critical section | `synchronized` / `ReentrantLock` | Mọi đường truy cập cạnh tranh phải dùng cùng quy tắc khóa |
| Tăng tổng thống kê với nhiều writer và contention cao | `LongAdder` | `sum()` khi có cập nhật đồng thời không phải snapshot atomic |
| Cập nhật giá trị cho một key của concurrent map | `ConcurrentHashMap.merge/compute` | Atomic theo key; không tạo transaction nhiều key |
| Thay một state bất biến gồm nhiều trường bằng CAS | `AtomicReference<ImmutableState>` | Update function thuần; hiểu retry và invariant |
| Không có trạng thái chia sẻ | Biến local, immutable object, message passing | Đơn giản nhất; tránh đồng bộ không cần thiết |

Không nên mặc định `LongAdder` “nhanh hơn” cho mọi workload; kiểm tra contention, số luồng, tần suất đọc và yêu cầu consistency trước khi đổi.

## 11. Kết quả và độ đúng: cần kiểm chứng khác nhau

### Vì sao stress test một triệu lần có thể “pass”?

Một đoạn như sau có thể đôi lúc in đúng kết quả, dù logic không an toàn:

~~~java
// Minh họa lỗi: sharedCounter++ không được bảo vệ.
for (int i = 0; i < 1_000_000; i++) {
    sharedCounter++;
}
~~~

Nếu hai thread ít đụng nhau do scheduler hoặc JIT/CPU/tải tình cờ, lỗi không nhất thiết xuất hiện. Một kết quả đúng ở 100 hoặc 10.000 lần chạy **không chứng minh chương trình thread-safe**. Ngược lại, ví dụ `LostUpdate` ở phần 3 điều khiển lịch xen kẽ bằng latch để tạo một phản ví dụ xác định.

### Cách kiểm chứng một sửa lỗi RMW

1. **Viết invariant.** Ví dụ: sau khi hai thread mỗi thread tăng đúng 100.000 lần và đều kết thúc bình thường, counter phải là 200.000.
2. **Chỉ ra linearization point.** Trong `synchronized`, phép cập nhật diễn ra khi thread độc quyền lock; với `AtomicInteger.incrementAndGet()`, API đảm bảo phép tăng nguyên tử trên biến đó. Đừng coi `Thread.sleep()` là linearization point.
3. **Chờ đúng kết thúc.** Dùng `Thread.join()` hoặc `Future.get()`. Không đọc kết quả rồi đoán task chắc đã hoàn thành vì thời gian chờ đủ lâu.
4. **Thử xen kẽ có chủ đích.** `CountDownLatch`/`CyclicBarrier` giúp đồng bộ các pha; cần thiết kế test tránh timeout vô hạn khi test thất bại.
5. **Kiểm tra đường truy cập khác.** Một writer không dùng lock, hay một đoạn `get()+set()` còn sót, đủ làm invariant bị phá.
6. **Đánh giá hiệu năng riêng.** Khi đã đúng, mới đo throughput/latency và contention bằng benchmark phù hợp (ví dụ JMH). Không đánh đổi correctness lấy một lần benchmark thuận lợi.

### Một vài hiểu nhầm cần tránh

- **“`++` là một toán tử, nên atomic.”** Toán tử ở source không tạo hợp đồng atomic cho shared RMW.
- **“Đổi thành `volatile int` là xong.”** Visibility/order khác với atomic cho chuỗi đọc–tính–ghi.
- **“`AtomicInteger.get()` và `set()` đều atomic, nên cả tổ hợp cũng atomic.”** Không; cần `incrementAndGet()`, CAS hoặc phương thức RMW tương ứng.
- **“Mỗi thread lock một object cũng được.”** Không; phải đồng bộ qua cùng lock bảo vệ cùng state.
- **“HashMap/ConcurrentHashMap có get và put thread-safe thì chuỗi get–put chắc chắn atomic.”** Không; phải dùng `merge/compute` hoặc protocol thích hợp.
- **“Một atomic counter bảo vệ tất cả invariant trên hai biến.”** Không; phạm vi atomicity phải khớp phạm vi invariant.
- **“Dữ liệu sau `join()` đúng thì quá trình cập nhật chắc atomic.”** `join()` giúp chờ và thấy các ghi trước khi worker kết thúc; nó không sửa các lost update đã xảy ra giữa worker.
- **“CAS lambda chỉ chạy đúng một lần.”** Một phép update CAS có thể retry; lambda có side effect dễ bị thực hiện lặp.
- **“LongAdder.sum() luôn cho snapshot tức thì chính xác khi còn writer.”** Không; sum không phải atomic snapshot dưới concurrent updates.

## 12. Tự kiểm tra

**Câu 1.** Hai thread cùng dùng `volatile int x` và mỗi thread gọi `x++` 1.000 lần. Sau khi `join` cả hai, `x` có chắc bằng 2.000?  
**Đáp án:** Không. `volatile` bảo đảm ngữ nghĩa truy cập từng lần, nhưng `x++` vẫn là read-modify-write có thể chen giữa.

**Câu 2.** Có hai `AtomicInteger` là `a` và `b`. `a.incrementAndGet()` rồi `b.decrementAndGet()` có atomic đối với invariant `a + b` không?  
**Đáp án:** Không. Từng cập nhật atomic riêng, nhưng thread khác có thể quan sát trạng thái giữa hai lần cập nhật.

**Câu 3.** Đổi `counter++` thành `atomic.get(); atomic.set(old + 1);` có giải quyết lost update không?  
**Đáp án:** Không. Đây vẫn là hai phương thức độc lập; dùng `incrementAndGet()` hoặc CAS loop.

**Câu 4.** Với `ConcurrentHashMap<String,Integer>`, hai thread cùng `get("k")` rồi `put("k", old + 1)` có luôn đếm đủ không?  
**Đáp án:** Không. Dùng một `merge("k", 1, Integer::sum)` cho phép tăng theo hợp đồng cập nhật atomic một key.

**Câu 5.** Muốn đọc tổng thống kê approximate khi nhiều thread tăng, chọn `LongAdder` hợp lý không? Muốn snapshot chính xác cho quyết định nghiệp vụ có nên dựa mù vào `sum()` không?  
**Đáp án:** Có thể dùng `LongAdder` cho thống kê throughput cao, nhưng `sum()` dưới concurrent update không phải snapshot atomic.

**Câu 6.** Trong thí nghiệm `LostUpdate`, vì sao mọi lần chạy bình thường đều in `actual = 1`, dù scheduler khác nhau?  
**Đáp án:** Cả hai thread được buộc đọc 0 trước khi có bất kỳ ghi nào. Sau release, cả hai cùng ghi 1; thứ tự hai lần ghi không thay đổi kết quả.

## Tài liệu tham khảo chính thức

- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Java Memory Model, synchronization, happens-before và hành vi tranh chấp.
- [`AtomicInteger` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): các phép tăng và CAS nguyên tử.
- [`LongAdder` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html): counter phân tán khi cạnh tranh cao và giới hạn của `sum()`.
- [`ConcurrentHashMap` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): các cập nhật `compute`, `merge` theo key.
- [`CountDownLatch` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): điều phối các pha và memory consistency.
- [`ReentrantLock` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html): mutual exclusion, điều khiển lock nâng cao.

**Ghi nhớ:** Một lần đọc atomic + một phép tính + một lần ghi atomic **không tạo thành một read-modify-write atomic**. Với shared mutable state, hãy dùng đúng primitive bảo vệ **cả cập nhật** và đúng phạm vi invariant.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="race-conditions-005">Check-then-act và lỗi kiểm tra rồi thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# Check-then-act và lỗi kiểm tra rồi thao tác trong Java Concurrency

**Check-then-act** là mẫu mã “kiểm tra điều kiện, rồi thực hiện hành động dựa trên kết quả kiểm tra”. Trong chương trình chạy một luồng, hai bước thường được hiểu là nối tiếp. Trong chương trình nhiều luồng, một luồng khác có thể **thay đổi trạng thái dùng làm điều kiện ngay sau bước kiểm tra và trước bước thao tác**. Khi đó hành động được thực hiện dựa trên thông tin đã lỗi thời, tạo thành một dạng **race condition**.

Ví dụ tưởng như hợp lý:

```java
// Trích đoạn minh họa, giả định 'cache' đã được khai báo.
if (!cache.containsKey("config")) {    // Check
    cache.put("config", loadConfig());   // Act
}
```

Nếu hai thread đều thấy key chưa tồn tại, **cả hai có thể chạy `loadConfig()` và ghi kết quả**. Ngay cả khi bản thân `containsKey()` và `put()` đều thread-safe, cặp thao tác này vẫn chưa chắc là một giao dịch nguyên tử (*atomic operation*). Mục tiêu là nhận diện đúng khoảng thời gian nguy hiểm đó, hiểu vì sao các giải pháp “kiểm tra rồi ghi” đơn thuần thất bại, và chọn cơ chế Java thích hợp để gộp kiểm tra với hành động.

## 1. Check-then-act là gì?

Một điều kiện có thể kiểm tra trạng thái rồi quyết định thực hiện hành động:

- “Nếu chưa có cấu hình trong cache thì tạo cấu hình.”
- “Nếu còn một suất thì đặt chỗ và giảm số suất.”
- “Nếu số lượng nhỏ hơn giới hạn thì tăng số lượng.”
- “Nếu tài nguyên chưa được khởi tạo thì khởi tạo nó.”

Vấn đề nằm ở **tính toàn vẹn của cả chuỗi** `check → act`, không nhất thiết nằm ở từng lệnh riêng lẻ. Mỗi lệnh có thể hợp lệ khi chạy độc lập, nhưng việc xen kẽ giữa các thread phá vỡ điều kiện mà hành động đang giả định.

Giả sử một tài nguyên chỉ được phép tạo **một lần**:

| Thời điểm | Thread A | Thread B | Trạng thái chung |
|---|---|---|---|
| T0 |  |  | Chưa có tài nguyên. |
| T1 | Đọc: chưa có. |  | Chưa có. |
| T2 |  | Đọc: chưa có. | Chưa có. |
| T3 | Tạo tài nguyên A. |  | Đã tạo A. |
| T4 |  | Tạo tài nguyên B. | Đã tạo thêm B. |
| T5 | Ghi A vào cache. |  | Cache chứa A. |
| T6 |  | Ghi B vào cache. | Cache chứa B; A bị ghi đè. |

Sau cùng cache có thể chỉ chứa một giá trị, nhưng điều đó **không chứng minh việc khởi tạo chỉ diễn ra một lần**. Nếu việc tạo có tác dụng phụ (kết nối, ghi dữ liệu, phát sự kiện, cấp tài nguyên), tác dụng phụ có thể xảy ra hai lần.

Tên kỹ thuật *TOCTOU* (*time of check to time of use*) cũng diễn tả kiểu lỗi này: trạng thái được xác minh tại một thời điểm nhưng được sử dụng tại thời điểm khác, khi sự thật có thể đã thay đổi.

## 2. Những thuộc tính cần phân biệt

**Atomicity (tính nguyên tử):** tập thao tác quan trọng được thực hiện như một đơn vị đối với các luồng cùng cạnh tranh, không cho một luồng khác quan sát hoặc can thiệp vào trạng thái trung gian theo cách phá vỡ bất biến. Với check-then-act, cần bảo đảm **kiểm tra và cập nhật** thuộc cùng cơ chế phối hợp.

**Visibility (tính hiển thị):** giá trị do luồng này ghi được luồng khác nhìn thấy theo các quan hệ đồng bộ hóa trong Java Memory Model. `volatile` hoặc `AtomicInteger.get()` có thể giúp việc đọc thấy giá trị mới, **nhưng không tự biến hai hành động tách rời thành một thao tác nguyên tử**.

**Ordering (thứ tự):** `happens-before` ràng buộc khả năng quan sát và thứ tự bộ nhớ giữa các thao tác liên quan. Có `happens-before` đúng cho một phép ghi/đọc vẫn không làm cho `if (condition) act()` tự động an toàn nếu không có cách bảo vệ toàn bộ quyết định.

**Invariant (bất biến):** quy tắc luôn phải đúng, chẳng hạn `available >= 0`, một mã chỉ được cấp một lần, hoặc `available + reserved == capacity`. Cách sửa phải bảo vệ **bất biến**, chứ không chỉ sửa một dòng code trông có vẻ nguy hiểm.

Cần tách hai loại lỗi thường đi cùng nhau: *data race* theo Java Memory Model là truy cập xung đột tới cùng biến thiếu đồng bộ hóa/happens-before; còn *race condition* là kết quả phụ thuộc vào lịch xen kẽ không mong muốn. Ví dụ hai thao tác riêng lẻ trên `ConcurrentHashMap` có thể không tạo data race trong cấu trúc nội bộ, nhưng vẫn tạo **race condition ở cấp logic** nếu điều kiện và hành động không được gộp.

## 3. Ví dụ tái hiện lỗi với ConcurrentHashMap

Dùng `ConcurrentHashMap` để **loại bỏ lỗi truy cập cấu trúc dữ liệu không an toàn** và cô lập đúng lỗi check-then-act: `get()` và `put()` đều thread-safe riêng lẻ, nhưng hai lệnh không tự gộp thành một hành động. Hai `CountDownLatch` không cần thiết; chỉ một latch đếm hai lượt kiểm tra đã đủ để cố tình tạo lịch xen kẽ sai.

Tạo tệp **`CheckThenActRace.java`**:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class CheckThenActRace {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
        AtomicInteger initializations = new AtomicInteger();
        CountDownLatch bothSawMissing = new CountDownLatch(2);

        Runnable worker = () -> {
            if (cache.get("config") == null) {
                bothSawMissing.countDown();

                try {
                    bothSawMissing.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                int attempt = initializations.incrementAndGet();
                cache.put("config", "created-" + attempt);
            }
        };

        Thread first = new Thread(worker, "first");
        Thread second = new Thread(worker, "second");

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("Initializations: " + initializations.get());
        System.out.println("Cache entries: " + cache.size());
    }
}
```

Chạy với JDK 17+:

```bash
javac CheckThenActRace.java
java CheckThenActRace
```

Kết quả:

```text
Initializations: 2
Cache entries: 1
```

Ở đây cả hai thread cùng đọc `null`, sau đó mỗi thread giảm `bothSawMissing` rồi chờ cho tới khi **cả hai đã đi qua bước kiểm tra**. Vì không thread nào ghi vào cache trước thời điểm đó, cả hai đều đi vào nhánh tạo giá trị. `AtomicInteger` chỉ bảo đảm phép đếm `initializations` an toàn, **không** bảo vệ điều kiện `get() == null`. Hai lần `put()` có thể diễn ra theo thứ tự khác nhau, nên ví dụ không in giá trị cuối cùng (giá trị đó không cố định).

Bài học quan trọng: thay `HashMap` bằng `ConcurrentHashMap` giải quyết an toàn truy cập map, **không tự sửa** chuỗi `get() → if → put()` ở tầng nghiệp vụ.

## 4. Sửa lỗi khởi tạo cache bằng một thao tác nguyên tử

Với `ConcurrentHashMap`, `computeIfAbsent(key, mappingFunction)` cung cấp thao tác nguyên tử cho trường hợp “nếu key chưa có giá trị thì tính và gắn giá trị”. Trong cùng một thời kỳ key chưa được gắn và không bị xóa/chỉnh từ nơi khác, các thread cạnh tranh sẽ chia sẻ kết quả khởi tạo thay vì mỗi thread tự chạy hàm tính.

Tạo tệp **`AtomicCacheInitialization.java`**:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCacheInitialization {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
        AtomicInteger initializations = new AtomicInteger();
        CountDownLatch startTogether = new CountDownLatch(1);

        Runnable worker = () -> {
            try {
                startTogether.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            cache.computeIfAbsent("config", key -> {
                initializations.incrementAndGet();
                return "created";
            });
        };

        Thread first = new Thread(worker, "first");
        Thread second = new Thread(worker, "second");
        first.start();
        second.start();

        startTogether.countDown();
        first.join();
        second.join();

        System.out.println("Initializations: " + initializations.get());
        System.out.println("Cache entries: " + cache.size());
        System.out.println("Config: " + cache.get("config"));
    }
}
```

```bash
javac AtomicCacheInitialization.java
java AtomicCacheInitialization
```

Kết quả:

```text
Initializations: 1
Cache entries: 1
Config: created
```

Khác ví dụ lỗi, phép “kiểm tra key có mặt chưa và tính giá trị nếu thiếu” nằm trong **một phương thức có hợp đồng nguyên tử**. Với `ConcurrentHashMap.computeIfAbsent`, hàm ánh xạ được áp dụng tối đa một lần cho mỗi lời gọi cần tạo mapping theo hợp đồng của map; các lời gọi đồng thời vào cùng key không tạo nhiều giá trị thành công cho cùng đợt thiếu key như ví dụ trên.

**Các điều kiện không được bỏ qua:**

- `mappingFunction` nên ngắn gọn, không gây chặn kéo dài và không được sửa chính map đang được tính mapping. Các cập nhật khác có thể bị chặn trong quá trình tính.
- Nếu hàm trả về `null` hoặc ném ngoại lệ, mapping mới sẽ không được thiết lập; lời gọi tương lai có thể tính lại. Nếu mapping bị xóa sau khi tạo, lần gọi sau cũng có thể tạo lại.
- Hợp đồng giữa các kiểu `Map` không hoàn toàn giống nhau: `Map.computeIfAbsent` mặc định **không tự bảo đảm đồng bộ hóa/nguyên tử**. Khi dựa vào tính nguyên tử, hãy dùng và đọc hợp đồng của triển khai đồng thời cụ thể, như `ConcurrentHashMap`.
- `putIfAbsent(key, value)` cũng có thể đặt giá trị một cách nguyên tử nếu key vắng mặt, nhưng khi viết `putIfAbsent(key, expensiveCreate())`, biểu thức `expensiveCreate()` được đánh giá **trước khi** gọi phương thức; nhiều thread vẫn có thể tạo giá trị thừa. Chọn `computeIfAbsent` khi cần tránh tính toán dư và có thể đáp ứng các giới hạn của mapping function.

## 5. Lỗi “kiểm tra giới hạn rồi tăng”: AtomicInteger không tự sửa hai lệnh

Xét yêu cầu “chỉ có tối đa một suất”. Đoạn sau có thể sai ngay cả khi `booked` là `AtomicInteger`:

```java
// Mã minh họa KHÔNG an toàn khi nhiều thread cạnh tranh.
if (booked.get() < 1) {
    booked.incrementAndGet();
}
```

Hai thread có thể đều đọc `0 < 1`, rồi đều tăng. `incrementAndGet()` là nguyên tử cho **một phép tăng**, nhưng toàn bộ “đọc để kiểm tra → tăng” thì chưa. Không có chuyện `AtomicInteger` tự khóa toàn bộ mọi đoạn mã xung quanh nó.

Một cách đúng với **một biến trạng thái** là vòng lặp compare-and-set (CAS): lấy giá trị cũ, kiểm tra điều kiện, rồi chỉ ghi giá trị mới nếu giá trị vẫn đúng bằng giá trị đã đọc. Nếu CAS thất bại do thread khác thắng trước, đọc lại và kiểm tra lại.

Tạo tệp **`AtomicReservation.java`**:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicReservation {
    private static boolean reserveOne(AtomicInteger available) {
        while (true) {
            int current = available.get();

            if (current <= 0) {
                return false;
            }

            if (available.compareAndSet(current, current - 1)) {
                return true;
            }
            // Giá trị đã đổi: đọc lại, kiểm tra lại bất biến.
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicInteger available = new AtomicInteger(1);
        AtomicInteger accepted = new AtomicInteger();

        Runnable request = () -> {
            if (reserveOne(available)) {
                accepted.incrementAndGet();
            }
        };

        Thread first = new Thread(request);
        Thread second = new Thread(request);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("Accepted: " + accepted.get());
        System.out.println("Available: " + available.get());
    }
}
```

```bash
javac AtomicReservation.java
java AtomicReservation
```

Kết quả:

```text
Accepted: 1
Available: 0
```

Dù thứ tự thread nào thắng không xác định, kết quả tổng luôn đúng: chỉ một CAS có thể đổi `1 → 0`; thread còn lại đọc `0` hoặc CAS thất bại và đọc lại, nên không giảm dưới 0. Đây là cách kết hợp **check + act** tại cùng một điểm nguyên tử cho một biến.

Có thể cân nhắc `AtomicInteger.updateAndGet` hoặc `getAndUpdate` nếu biểu thức cập nhật đủ rõ, nhưng hàm truyền vào **có thể được gọi lại** khi CAS tranh chấp: không đặt hiệu ứng phụ như gửi email, ghi file hay tăng một counter độc lập bên trong lambda cập nhật. Mã CAS ở ví dụ đặt việc tăng `accepted` chỉ sau khi CAS thực sự thành công.

## 6. Với nhiều biến liên quan, dùng cùng một vùng bảo vệ

CAS trên một `AtomicInteger` không tự bảo vệ bất biến có **nhiều trường**. Ví dụ, hệ thống theo dõi `available` và `reserved`, cần duy trì `available + reserved == capacity`. Nếu cập nhật một biến rồi thread khác đọc biến còn lại giữa chừng, nó có thể thấy trạng thái tạm thời không hợp lệ.

Một cách đơn giản là đưa cả điều kiện lẫn các thay đổi liên quan vào cùng một vùng `synchronized` trên **cùng đối tượng khóa**.

Tạo tệp **`SynchronizedReservation.java`**:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class SynchronizedReservation {
    static final class Inventory {
        private int available = 1;
        private int reserved = 0;

        synchronized boolean reserve() {
            if (available == 0) {
                return false;
            }

            available--;
            reserved++;
            return true;
        }

        synchronized String snapshot() {
            return "Available: " + available
                    + ", Reserved: " + reserved;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Inventory inventory = new Inventory();
        AtomicInteger accepted = new AtomicInteger();

        Runnable request = () -> {
            if (inventory.reserve()) {
                accepted.incrementAndGet();
            }
        };

        Thread first = new Thread(request);
        Thread second = new Thread(request);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("Accepted: " + accepted.get());
        System.out.println(inventory.snapshot());
    }
}
```

```bash
javac SynchronizedReservation.java
java SynchronizedReservation
```

Kết quả:

```text
Accepted: 1
Available: 0, Reserved: 1
```

Cả hai thread dùng cùng đối tượng `inventory`. Chỉ một thread được thực thi `reserve()` trong monitor của đối tượng tại một thời điểm. Vì kiểm tra và cả hai phép cập nhật ở **cùng vùng bảo vệ**, thread thứ hai sẽ thấy số suất đã giảm và không thể đặt vượt giới hạn. Phương thức `snapshot()` cũng dùng monitor đó để quan sát hai trường nhất quán.

Trong code thật, **mọi đường đọc/ghi cùng bất biến** đều phải tuân theo cơ chế bảo vệ. Hai vùng `synchronized` dùng hai object khóa khác nhau không bảo vệ nhau. Nếu có nhu cầu `tryLock`, timeout hoặc khóa ngắt được, `ReentrantLock` là lựa chọn; luôn gọi `unlock()` trong `finally`:

```java
lock.lock();
try {
    // Kiểm tra điều kiện và cập nhật trạng thái được bảo vệ.
} finally {
    lock.unlock();
}
```

Đây là trích đoạn minh họa, giả định `lock` là `ReentrantLock` đã được tạo. Tránh thực hiện thao tác I/O hay chờ không kiểm soát trong critical section khi không cần thiết, vì giữ khóa lâu sẽ làm giảm khả năng tiến triển và có thể gây deadlock nếu phối hợp khóa sai thứ tự.

## 7. Chọn công cụ phù hợp cho từng bất biến

| Yêu cầu check-then-act | Công cụ Java thường phù hợp | Chú ý |
|---|---|---|
| Tăng một bộ đếm không có điều kiện | `AtomicInteger.incrementAndGet()` | Đây đã là một phép tăng nguyên tử; không cần tự `get() + set()`. |
| Giảm quota nếu còn số dư | Vòng CAS với `compareAndSet`, hoặc một phép update nguyên tử phù hợp | Kiểm tra lại điều kiện sau CAS thất bại; không chạy side effect trước khi thắng CAS. |
| Khởi tạo key vắng mặt trong map đồng thời | `ConcurrentHashMap.computeIfAbsent()` | Mapping function ngắn, không sửa cùng map, xử lý null/exception. |
| Chỉ chèn nếu key vắng mặt và đã có sẵn giá trị | `ConcurrentHashMap.putIfAbsent()` | Chèn nguyên tử, nhưng xây dựng tham số trước lời gọi có thể bị lặp. |
| Đọc/sửa nhiều trường phải nhất quán | `synchronized` hoặc `ReentrantLock` cho toàn bộ giao dịch nội bộ | Cùng khóa, cùng đối tượng trạng thái, mọi đường truy cập tuân thủ. |
| Tạo tài nguyên thuộc hệ thống bên ngoài (file/database) | Thao tác nguyên tử hoặc ràng buộc giao dịch do **hệ thống đó** cung cấp | Khóa trong JVM không bảo vệ các tiến trình/JVM khác. |

**“Thread-safe collection” không tương đương “thread-safe business operation”.** Với `ConcurrentHashMap`, từng phương thức có thể thread-safe nhưng chuỗi `containsKey() → put()` vẫn lỗi; với `Collections.synchronizedMap`, muốn thực hiện hành động hợp thành phải giữ đúng monitor bao trùm toàn bộ chuỗi theo hợp đồng và mọi code truy cập liên quan phải phối hợp.

## 8. TOCTOU ngoài bộ nhớ JVM

Check-then-act cũng xảy ra với file và hệ thống ngoài tiến trình:

```java
// Không an toàn nếu dùng để tự bảo đảm rằng file còn vắng mặt.
if (!Files.exists(path)) {
    Files.createFile(path);
}
```

Đây là trích đoạn minh họa; giả định đã có `Path path` và import thích hợp. Một tiến trình khác có thể tạo file sau `exists()` nhưng trước `createFile()`. `Files.createFile(path)` **tự thực hiện bước kiểm tra và tạo một cách nguyên tử với các hoạt động filesystem khác ảnh hưởng thư mục**, theo hợp đồng của API; nếu file đã tồn tại, API có thể ném `FileAlreadyExistsException`. Vì vậy với yêu cầu “chỉ tạo nếu chưa có”, hãy gọi trực tiếp thao tác nguyên tử và xử lý kết quả:

```java
try {
    Files.createFile(path);
    // Tạo file thành công.
} catch (FileAlreadyExistsException e) {
    // File đã có: xử lý theo nghiệp vụ.
}
```

Trích đoạn này cần `import java.nio.file.Files;`, `import java.nio.file.FileAlreadyExistsException;`, và `Path path` phù hợp; trong phương thức thực tế còn cần xử lý `IOException`.

Đối với dữ liệu trong database, “SELECT để xem mã có tồn tại không, sau đó INSERT” có cùng khoảng đua. Một khóa `synchronized` ở **một JVM** không thể ngăn một ứng dụng hoặc JVM khác chèn cùng mã. Bất biến cần được thực thi tại nơi sở hữu dữ liệu bằng unique constraint, transaction, hoặc thao tác upsert/conditional write có tính nguyên tử theo hợp đồng hệ thống đó. Đây là nguyên tắc phối hợp xuyên tiến trình, không yêu cầu dùng framework Java cụ thể.

## 9. Các giải pháp tưởng đúng nhưng chưa đủ

**Thêm `volatile` cho một cờ kiểm tra rồi đặt cờ:**

```java
// Chỉ minh họa ý tưởng; 'initialized' là một biến volatile.
if (!initialized) {
    initialized = true;
    initialize();
}
```

Hai thread vẫn có thể cùng đọc `false` rồi cùng chạy `initialize()`; `volatile` chỉ cung cấp quy tắc hiển thị/thứ tự cho truy cập cờ, **không gộp check và act**. Thậm chí nếu cờ đã thành `true` trước khi `initialize()` hoàn tất, thread khác có thể lầm tưởng đối tượng đã sẵn sàng. Cần cơ chế khởi tạo đúng hợp đồng, chẳng hạn synchronization, holder idiom hoặc công cụ concurrent phù hợp.

**Dùng `Thread.sleep()` để “giảm xác suất” race:** thay đổi lịch chạy chỉ có thể che lỗi; không bảo đảm kết quả đúng. Race có thể xuất hiện trở lại khi tải tăng, chạy trên máy khác hoặc JVM lập lịch khác.

**Chỉ khóa một đoạn:** nếu code khóa khi ghi nhưng bước kiểm tra ở ngoài, giữa hai bước vẫn có khoảng đua. Cần bảo vệ toàn bộ `if (condition) update()` khi điều kiện và cập nhật phụ thuộc nhau.

**Khóa ở hai nơi khác nhau:** `synchronized(lockA)` không ngăn thread khác đi vào `synchronized(lockB)`. Các thao tác trên một bất biến phải cùng tuân thủ một giao thức đồng bộ rõ ràng.

**Đưa tác dụng phụ vào phép thử lại CAS:** nếu phép tính bị thực hiện nhiều lần khi tranh chấp, các tác dụng phụ như gửi yêu cầu HTTP, ghi log nghiệp vụ, tạo đơn hàng có thể lặp. Hãy tách việc quyết định thành công của cập nhật trạng thái với việc thực hiện tác dụng phụ, và thiết kế idempotency hoặc giao dịch nếu cần độ tin cậy xuyên hệ thống.

**Chỉ kiểm tra kích thước map cuối:** kích thước bằng 1 không chứng minh chỉ một lần khởi tạo như ví dụ đầu; cần đo cả số lần tạo hoặc tác dụng phụ quan trọng.

## 10. Kiểm thử và chẩn đoán race condition

Race phụ thuộc lịch thực thi; việc chạy thử “một nghìn lần không lỗi” không chứng minh logic đúng. Cách kiểm thử hiệu quả là:

1. **Xác định bất biến** phải được giữ: số lượng không âm, một key có một giá trị khởi tạo, hoặc số lần thực hiện tác dụng phụ được phép.
2. **Điều khiển lịch chạy tại điểm nguy hiểm** bằng `CountDownLatch`, `CyclicBarrier` hoặc cơ chế phối hợp rõ ràng. Ví dụ đầu cố ý buộc cả hai thread nhìn thấy giá trị thiếu trước khi cho ghi.
3. **Chờ hoàn tất** bằng `join()` hay một primitive phù hợp, rồi kiểm tra bất biến và số lần tác dụng phụ xảy ra. Đừng dựa vào `sleep()` để giả định worker đã xong.
4. **Thử tải và nhiều lịch chạy** để bổ trợ, không thay thế phân tích tính nguyên tử. Sử dụng thread dump/profiler khi nghi có lock contention hoặc deadlock.
5. **Kiểm tra tất cả đường cập nhật**, kể cả code khác gọi trực tiếp setter, cập nhật map, xóa key hoặc sửa trường ngoài khóa.

Khi mô tả lỗi, hãy ghi rõ: trạng thái ban đầu, hai phép kiểm tra độc lập, thời điểm xen kẽ, bất biến bị vi phạm, và **điểm nguyên tử** mà cách sửa cung cấp. Đây là bằng chứng rõ hơn việc chỉ nói “thêm synchronized sẽ hết lỗi”.

## 11. Tự kiểm tra kiến thức

1. **Vì sao `ConcurrentHashMap.get()` và `put()` đều an toàn mà đoạn `if (get == null) put` vẫn lỗi?** Vì cặp thao tác riêng rẽ không nguyên tử: giá trị có thể đổi giữa lúc đọc và lúc ghi.
2. **`volatile` sửa được check-then-act không?** Không, nếu phải kết hợp nhiều bước; nó không tự tạo một giao dịch nguyên tử.
3. **Có phải `AtomicInteger.incrementAndGet()` luôn đủ khi yêu cầu “chỉ tăng nếu còn dưới giới hạn”?** Không; phải thực hiện kiểm tra điều kiện và cập nhật trong một cơ chế nguyên tử như CAS loop hoặc khóa.
4. **Vì sao `computeIfAbsent()` thường phù hợp hơn `putIfAbsent(key, expensiveCreate())` cho lazy initialization?** Vì `expensiveCreate()` được tính trước lời gọi `putIfAbsent`, còn hàm mapping của `ConcurrentHashMap.computeIfAbsent` được phối hợp trong thao tác nguyên tử.
5. **Khi cập nhật hai trường có chung bất biến, một `AtomicInteger` bảo vệ được cả hai không?** Không; cần biểu diễn trạng thái nguyên tử thích hợp hoặc bảo vệ cùng một vùng cập nhật bằng khóa/chế độ phối hợp chung.
6. **Một khóa Java có ngăn JVM khác tạo trùng file hoặc bản ghi database không?** Không. Bất biến phải được bảo đảm bởi thao tác nguyên tử/ràng buộc của tài nguyên chung.
7. **Vì sao kết quả cuối cache có một phần tử chưa đủ chứng minh chương trình đúng?** Vì hai lần tạo/ghi vẫn có thể xảy ra, gây tác dụng phụ thừa trước khi map chỉ giữ lại một giá trị.

**Ghi nhớ:** check-then-act chỉ đúng khi **điều kiện và hành động phụ thuộc điều kiện được bảo vệ trong cùng một thao tác nguyên tử** theo phạm vi sở hữu dữ liệu. Chọn API atomic cho một biến, thao tác nguyên tử của concurrent collection cho một key, khóa nhất quán cho nhiều trường, và cơ chế nguyên tử của hệ thống ngoài JVM khi tài nguyên nằm bên ngoài tiến trình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="race-conditions-006">Broken invariant khi cập nhật nhiều biến liên quan</a>

<details>
<summary>Xem chi tiết</summary>

# Broken invariant khi cập nhật nhiều biến liên quan trong Java Concurrency

## 1. Vấn đề: từng biến đúng nhưng trạng thái tổng thể sai

Một đối tượng có thể chứa nhiều trường dữ liệu mà **ý nghĩa nghiệp vụ chỉ đúng khi các trường thỏa một quan hệ với nhau**. Quan hệ bắt buộc đó gọi là **bất biến (invariant)**.

Ví dụ một sổ cái có hai số dư:

- `left = 70`, `right = 30`;
- tổng tiền phải luôn bằng `100`;
- chuyển `20` từ `left` sang `right` phải biến trạng thái `(70, 30)` thành `(50, 50)`.

Bất biến ở đây là:

```text
left >= 0
right >= 0
left + right == 100
```

Nếu mã thực hiện tuần tự hai phép gán:

```java
left -= 20;  // Trạng thái trung gian: left=50, right=30
right += 20; // Trạng thái cuối: left=50, right=50
```

thì sau phép gán đầu, tổng tạm thời bằng `80`. Khi hai thread chạy đồng thời, **reader có thể quan sát trạng thái trung gian** `(50, 30)` và thấy tổng không còn bằng `100`. Đây là hiện tượng **broken invariant khi cập nhật nhiều biến liên quan**.

Các ví dụ gần gũi khác:

| Dữ liệu liên quan | Bất biến cần duy trì | Trạng thái sai có thể bị nhìn thấy |
| --- | --- | --- |
| `start` và `end` | `start <= end` | Reader thấy `start` mới và `end` cũ, thành `start > end` |
| `used` và `capacity` | `0 <= used <= capacity` | Tăng `used` trước khi tăng `capacity` khiến `used > capacity` |
| `items` và `itemCount` | `itemCount == items.size()` | Danh sách đã thay đổi nhưng số lượng chưa cập nhật |
| `debit` và `credit` | Tổng ghi nợ/ghi có của giao dịch cân bằng | Reader chỉ thấy một vế giao dịch |
| `configVersion` và `configData` | Dữ liệu ứng với đúng phiên bản | Đọc version mới với data cũ |

Trọng tâm không nằm ở cú pháp `+=` hoặc `-=`. Ngay cả khi **mỗi phép đọc/ghi một trường đều nguyên tử**, việc đọc/ghi **một nhóm nhiều trường** vẫn chưa chắc nguyên tử. Hệ thống cần một cơ chế khiến các thread khác chỉ nhìn thấy **những trạng thái nhất quán**.

## 2. Race condition liên quan đến bất biến là gì?

**Race condition** xuất hiện khi kết quả hoặc tính đúng đắn phụ thuộc vào thứ tự xen kẽ không được kiểm soát giữa các hoạt động đồng thời. Với invariant nhiều trường, trình tự sau gây lỗi:

```text
Trạng thái ban đầu: left=70, right=30, tổng=100

Thread writer                          Thread reader
----------------------------           -----------------------------
left = 50
                                       đọc left  -> 50
                                       đọc right -> 30
                                       tính tổng -> 80 (SAI)
right = 50

Trạng thái cuối: left=50, right=50, tổng=100
```

Nhìn riêng từng phép gán, không có giá trị nào bị “ghi nửa chừng”. Tuy nhiên, **reader đã thấy một tổ hợp chưa được công bố như một giao dịch hoàn chỉnh**.

Cần phân biệt ba khái niệm:

1. **Atomicity của từng trường**: một thao tác đọc/ghi đơn lẻ có thể nguyên tử theo Java Memory Model, nhưng điều đó không khiến cả chuỗi `left -= amount; right += amount` thành một thao tác nguyên tử.
2. **Visibility (khả năng nhìn thấy)**: khi writer ghi, reader có được bảo đảm quan sát bản cập nhật hay không? `volatile`, monitor hoặc các biến atomic cung cấp quan hệ bộ nhớ nhất định.
3. **Consistency (tính nhất quán)**: các giá trị reader nhìn thấy có cùng thuộc **một trạng thái hợp lệ** hay không? Đây là yêu cầu mạnh hơn visibility của từng trường.

Ví dụ reader thấy chính xác `left = 50` và `right = 30`. Hai phép đọc đều có thể được thực hiện “đúng” về mặt từng giá trị, nhưng snapshot tổng thể vẫn sai.

## 3. Minh chứng có thể chạy: `volatile` từng trường vẫn bị broken invariant

Ví dụ dưới sử dụng hai trường `volatile` và `CountDownLatch` để cố ý dừng writer **giữa hai phép cập nhật**. Đây là cách chứng minh một snapshot không nhất quán mà không phụ thuộc may rủi của bộ lập lịch.

Tạo tệp `BrokenInvariantDemo.java`:

```java
import java.util.concurrent.CountDownLatch;

public class BrokenInvariantDemo {
    private static final class Balances {
        volatile int left = 70;
        volatile int right = 30;
    }

    public static void main(String[] args) throws InterruptedException {
        Balances balances = new Balances();
        CountDownLatch firstWriteDone = new CountDownLatch(1);
        CountDownLatch allowSecondWrite = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            balances.left -= 20;
            firstWriteDone.countDown();

            try {
                allowSecondWrite.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            balances.right += 20;
        }, "writer");

        writer.start();
        firstWriteDone.await();

        System.out.println("Trong lúc cập nhật: "
                + balances.left + " + " + balances.right
                + " = " + (balances.left + balances.right));

        allowSecondWrite.countDown();
        writer.join();

        System.out.println("Sau khi cập nhật: "
                + balances.left + " + " + balances.right
                + " = " + (balances.left + balances.right));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac BrokenInvariantDemo.java
java BrokenInvariantDemo
```

Kết quả khi không có yêu cầu interrupt bên ngoài:

```text
Trong lúc cập nhật: 50 + 30 = 80
Sau khi cập nhật: 50 + 50 = 100
```

**Giải thích bằng thứ tự sự kiện:**

1. Writer cập nhật `left` từ `70` thành `50`.
2. `firstWriteDone.countDown()` cho main biết phép ghi thứ nhất đã hoàn thành. `main` được đánh thức ở `await()`.
3. Writer đứng ở `allowSecondWrite.await()` nên **chưa thể cập nhật `right`**.
4. Main đọc `(left, right) = (50, 30)`. Tổng `80` phá vỡ bất biến `left + right == 100`.
5. Main phát tín hiệu cho writer, rồi đợi `join()`; sau khi worker hoàn tất, cặp số dư là `(50, 50)` và tổng trở lại `100`.

Hai latch tạo các quan hệ *happens-before* cần thiết; do đó lỗi không chỉ là do cache hay đọc dữ liệu cũ. `volatile` buộc các truy cập trường riêng lẻ tuân theo các bảo đảm về visibility và thứ tự của volatile, **nhưng không kết hợp hai trường thành một cập nhật nguyên tử**.

**Điểm cần lưu ý:** `balances.left -= 20` và `balances.right += 20` là các phép đọc–sửa–ghi (read-modify-write), vốn cũng không nguyên tử nếu có nhiều writer. Trong ví dụ này cố ý chỉ có một writer để tách rõ lỗi **snapshot nhiều trường** khỏi lỗi **lost update** do nhiều writer.

Ví dụ ngăn writer tiếp tục để minh họa lỗi một cách xác định. Trong hệ thống thực tế, hai lệnh ghi có thể xảy ra rất sát nhau, nhưng sự xen kẽ của reader ở giữa **vẫn có thể xảy ra**; “thử nhiều lần chưa thấy sai” không phải bằng chứng an toàn.

## 4. Broken invariant khác gì lost update?

Hai lỗi thường xuất hiện gần nhau, nhưng cần xử lý ở đúng cấp độ:

**Lost update** là khi nhiều writer cạnh tranh cập nhật cùng một giá trị. Ví dụ hai thread cùng thực hiện `counter++`, cả hai đọc `10` và cuối cùng chỉ lưu `11` thay vì `12`. Một `AtomicInteger.incrementAndGet()` hoặc khóa phù hợp có thể sửa riêng thao tác tăng đó.

**Broken invariant nhiều trường** là khi một trạng thái hợp lệ đòi hỏi **nhiều giá trị phải thay đổi như một đơn vị logic**. Kể cả hai trường độc lập được lưu dưới dạng `AtomicInteger`, reader vẫn có thể nhìn thấy `left` đã trừ mà `right` chưa cộng. Hai thao tác:

```java
left.addAndGet(-20);
right.addAndGet(20);
```

vẫn là **hai thao tác atomic riêng biệt**. `AtomicInteger` ngăn mất cập nhật trên từng trường (khi dùng nhất quán), nhưng không biến chúng thành một giao dịch nhiều trường.

Với các ứng dụng cần chuyển nhiều giá trị có quan hệ, luôn đặt câu hỏi: **“Đơn vị nhất quán cần bảo vệ là từng giá trị hay cả tập giá trị?”** Nếu yêu cầu là cả tập, cơ chế đồng bộ phải bao phủ cả lúc cập nhật lẫn lúc đọc snapshot.

## 5. Sửa bằng `synchronized`: cùng một monitor bảo vệ thao tác cập nhật và đọc

Giải pháp dễ kiểm chứng là dùng **một monitor duy nhất** cho cả thao tác chuyển tiền và thao tác lấy snapshot. Khi writer vào `synchronized`, reader cần cùng monitor phải chờ đến khi writer rời vùng bảo vệ.

Hai điều kiện bắt buộc:

- **Writer** phải cập nhật toàn bộ nhóm trường liên quan trong một vùng được bảo vệ.
- **Reader** phải đọc toàn bộ nhóm trường đó trong một vùng được bảo vệ **bởi cùng monitor**.

Chỉ khóa writer mà để reader đọc các trường tự do vẫn có thể nhìn thấy trạng thái trung gian. Chỉ khóa reader mà writer không tuân thủ cùng cơ chế cũng không có tác dụng bảo vệ bất biến.

Tạo tệp `SynchronizedInvariantDemo.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class SynchronizedInvariantDemo {
    private static final class Ledger {
        private int left = 70;
        private int right = 30;

        synchronized void transferLeftToRight(int amount) {
            if (amount < 0 || amount > left) {
                throw new IllegalArgumentException("invalid transfer");
            }
            left -= amount;
            right += amount;
        }

        synchronized void transferRightToLeft(int amount) {
            if (amount < 0 || amount > right) {
                throw new IllegalArgumentException("invalid transfer");
            }
            right -= amount;
            left += amount;
        }

        synchronized int total() {
            return left + right;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Ledger ledger = new Ledger();
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean invariantBroken = new AtomicBoolean(false);

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 10_000; i++) {
                    ledger.transferLeftToRight(1);
                    ledger.transferRightToLeft(1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "writer");

        Thread reader = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 10_000; i++) {
                    if (ledger.total() != 100) {
                        invariantBroken.set(true);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "reader");

        writer.start();
        reader.start();
        start.countDown();

        writer.join();
        reader.join();

        System.out.println("Broken: " + invariantBroken.get());
        System.out.println("Final total: " + ledger.total());
    }
}
```

```bash
javac SynchronizedInvariantDemo.java
java SynchronizedInvariantDemo
```

Kết quả khi các thread không bị interrupt:

```text
Broken: false
Final total: 100
```

Mỗi thao tác chuyển tiền cập nhật hai trường khi giữ monitor của `Ledger`. `total()` lấy **chính monitor đó**, nên không thể đọc xen giữa hai phép cập nhật bên trong một lần `transfer...()`. Chương trình có thể xen kẽ thứ tự gọi giữa writer và reader, nhưng mọi giá trị tổng được đọc đều hợp lệ. `AtomicBoolean` chỉ dùng để chuyển kết quả kiểm tra từ reader sang main một cách an toàn; nó **không** phải cơ chế chính bảo vệ hai số dư.

Cách triển khai bằng `synchronized` có đặc điểm:

- Không cần `volatile` cho các trường nếu **mọi truy cập** đều được bảo vệ nhất quán bằng monitor này.
- Monitor có tính loại trừ lẫn nhau: tại một thời điểm, một thread giữ monitor, thread khác muốn lấy monitor phải chờ.
- Khi nhả monitor rồi thread khác lấy lại cùng monitor, Java Memory Model cung cấp quan hệ *happens-before* cho khả năng nhìn thấy dữ liệu.
- Kiểm tra hợp lệ phải nằm **cùng vùng khóa** với việc cập nhật. Nếu kiểm tra `amount <= left` bên ngoài khóa, giá trị `left` có thể thay đổi trước lúc thực hiện giao dịch.
- Không trả về tham chiếu tới một đối tượng nội bộ có thể bị sửa tự do bên ngoài khóa. Trả một snapshot bất biến hoặc giá trị sao chép.

### Vì sao reader không nên đọc từng trường bằng hai getter đồng bộ độc lập?

Giả sử `getLeft()` và `getRight()` đều `synchronized` nhưng caller thực hiện:

```java
int a = ledger.getLeft();
int b = ledger.getRight();
```

Đây là đoạn minh họa (không phải chương trình hoàn chỉnh). Khóa được nhả sau `getLeft()`, nên writer có thể chuyển tiền **giữa hai lời gọi getter**. Lúc đó caller ghép `a` ở trạng thái cũ với `b` ở trạng thái mới, tạo tổng sai dù **mỗi getter riêng lẻ đều thread-safe**.

Cách đúng là cung cấp **một phương thức snapshot** đọc cả hai trường trong cùng một critical section:

```java
record Balances(int left, int right) {}

synchronized Balances snapshot() {
    return new Balances(left, right);
}
```

Đoạn này minh họa phương thức được đặt **bên trong lớp chứa hai trường `left/right`**; `record Balances` là kiểu dữ liệu bất biến được định nghĩa ngoài phương thức. `record` yêu cầu Java 16+ và phù hợp với Java 17+. Caller nhận được một cặp giá trị cùng thuộc một thời điểm được bảo vệ bởi khóa. Sau khi khóa nhả, snapshot vẫn không đổi dù dữ liệu gốc tiếp tục được cập nhật.

## 6. Sửa bằng một immutable snapshot được công bố nguyên tử

Một cách khác là **không cập nhật lần lượt các trường công khai**. Thay vào đó:

1. Đọc một trạng thái bất biến hiện tại.
2. Tính toàn bộ trạng thái kế tiếp trong bộ nhớ cục bộ.
3. Kiểm tra các invariant trên trạng thái mới.
4. Công bố **một tham chiếu** tới trạng thái mới bằng cơ chế atomic/volatile phù hợp.

Reader chỉ lấy **một tham chiếu snapshot** rồi truy cập các trường của cùng snapshot. Vì đối tượng snapshot bất biến, reader không thể quan sát một nửa cập nhật bên trong nó.

### Ví dụ với `AtomicReference` và `record`

Tạo tệp `AtomicSnapshotDemo.java` (Java 17+):

```java
import java.util.concurrent.atomic.AtomicReference;

public class AtomicSnapshotDemo {
    record Balances(int left, int right) {
        Balances {
            if (left < 0 || right < 0 || left + right != 100) {
                throw new IllegalArgumentException("broken invariant");
            }
        }

        int total() {
            return left + right;
        }
    }

    public static void main(String[] args) {
        AtomicReference<Balances> state =
                new AtomicReference<>(new Balances(70, 30));

        state.updateAndGet(current ->
                new Balances(current.left() - 20, current.right() + 20));

        Balances snapshot = state.get();
        System.out.println("left=" + snapshot.left());
        System.out.println("right=" + snapshot.right());
        System.out.println("total=" + snapshot.total());
    }
}
```

```bash
javac AtomicSnapshotDemo.java
java AtomicSnapshotDemo
```

Kết quả:

```text
left=50
right=50
total=100
```

`record Balances` tạo một giá trị bất biến biểu diễn **cả cặp**. Constructor kiểm tra trạng thái trước khi đối tượng hợp lệ được công bố. `AtomicReference.updateAndGet` thực hiện cập nhật tham chiếu theo cơ chế atomic; nếu nhiều writer cạnh tranh, hàm tính trạng thái kế tiếp có thể bị gọi lại. Vì vậy lambda cập nhật phải **không có side effect** như gửi email, ghi log nghiệp vụ không thể lặp lại hoặc gọi hệ thống bên ngoài.

Lưu ý: thay thế một tham chiếu `volatile Balances snapshot` cũng có thể cung cấp công bố nguyên tử **cho một writer** nếu writer tạo trạng thái mới hoàn chỉnh rồi gán vào trường volatile. Nhưng nếu nhiều writer đồng thời đọc trạng thái cũ và cùng gán trạng thái mới, một cập nhật có thể ghi đè kết quả của cập nhật khác. `AtomicReference` với CAS/retry giải quyết dạng cạnh tranh cập nhật đó ở cấp tham chiếu khi mọi writer dùng đúng giao thức.

**Bẫy khi đọc:** không làm thế này nếu cần snapshot nhất quán:

```java
int a = state.get().left();
int b = state.get().right();
```

Hai lần `state.get()` có thể trả về hai thế hệ snapshot khác nhau khi writer công bố cập nhật giữa chúng. Dù mỗi snapshot tự nó hợp lệ, ghép hai trường từ hai snapshot khác nhau vẫn có thể sai. Hãy lấy `Balances snapshot = state.get();` **một lần**, rồi đọc `snapshot.left()` và `snapshot.right()`.

## 7. Còn dùng `ReentrantLock` hoặc `ReadWriteLock` thì sao?

`ReentrantLock` có khả năng bảo vệ cùng một invariant giống `synchronized` nếu tất cả reader và writer cùng tuân thủ giao thức khóa. Ưu điểm là hỗ trợ thử lấy khóa, thời hạn chờ, một số cơ chế `Condition` và khả năng điều khiển hủy trong lúc đợi khóa.

Mẫu cơ bản:

```java
lock.lock();
try {
    validateTransfer();
    updateAllRelatedFields();
} finally {
    lock.unlock();
}
```

Đây là **mã minh họa**, trong đó `lock` là `ReentrantLock`, còn hai phương thức là logic của ứng dụng. Cả phương thức đọc snapshot cũng phải lấy **cùng khóa**; khóa viết mà đọc không khóa vẫn không an toàn. Luôn mở khóa trong `finally`.

`ReentrantReadWriteLock` có thể cho phép nhiều reader đồng thời lấy read lock và chỉ một writer giữ write lock. Nó có thể hữu ích khi đọc nhiều, ghi ít, critical section có chi phí đáng kể; nhưng có thêm độ phức tạp và không mặc nhiên nhanh hơn `synchronized`. Không thay đổi loại khóa chỉ vì một microbenchmark ngắn.

Dù dùng khóa nào, yêu cầu về invariant vẫn như nhau: một cập nhật logic phải được thực hiện và quan sát như **một đơn vị nhất quán**.

## 8. Không thể chỉ thêm `volatile` hoặc từng `AtomicInteger`

### 8.1. `volatile` giải quyết điều gì?

Một trường `volatile` có những bảo đảm về visibility và thứ tự theo Java Memory Model. Ví dụ luồng khác đọc lại cùng trường volatile có thể nhìn thấy giá trị được công bố phù hợp với thứ tự đồng bộ. Nhưng hai trường:

```java
volatile int left;
volatile int right;
```

không tạo thành “cặp volatile”. Reader vẫn có thể quan sát `left` mới và `right` cũ. Đó chính là điều ví dụ `BrokenInvariantDemo` chứng minh.

Tương tự, một `volatile boolean ready` chỉ báo hiệu trạng thái đã được công bố **nếu giao thức writer/reader được thiết kế đúng**, chẳng hạn writer ghi toàn bộ dữ liệu rồi đặt `ready = true` và reader chỉ đọc sau khi thấy cờ. Nó không tự khắc phục việc writer tiếp tục cập nhật nhiều trường trong lúc reader đang đọc, và không thay thế synchronization nếu có nhiều lần cập nhật hay nhiều writer.

### 8.2. Vì sao mỗi trường atomic vẫn không đủ?

`AtomicInteger left` và `AtomicInteger right` bảo vệ các phép cập nhật riêng. Tuy nhiên, một reader lấy `left.get()` rồi `right.get()` vẫn có thể thấy dữ liệu từ hai thời điểm khác nhau. Không có một thao tác `getBothAtomically()` chung giữa hai `AtomicInteger` độc lập.

Muốn đảm bảo một snapshot nhất quán, dùng cùng monitor quanh cả cập nhật và đọc, hoặc gộp trạng thái vào một đối tượng bất biến rồi cập nhật tham chiếu nguyên tử.

### 8.3. Khóa writer mà không khóa reader

```java
synchronized (lock) {
    left -= amount;
    right += amount;
}
// Ở thread khác:
int total = left + right; // Đọc ngoài khóa: không có snapshot nhất quán.
```

Các dòng trên minh họa hai ngữ cảnh khác nhau. Với kiểu trường thường (`non-volatile`), đọc ngoài khóa còn thiếu bảo đảm visibility và có thể tạo data race. Ngay cả khi đổi hai trường thành volatile, việc đọc tách rời vẫn có nguy cơ broken invariant. **Hai phía của giao thức phải thống nhất**.

### 8.4. Khóa từng phép gán riêng lẻ

```java
synchronized (lock) {
    left -= amount;
}
synchronized (lock) {
    right += amount;
}
```

Mỗi phép gán được bảo vệ nhưng khóa được nhả **ở giữa**, cho reader cơ hội vào và quan sát trạng thái sai. Khóa phải bao trùm **toàn bộ nhóm thay đổi liên quan**, không chỉ từng phép đơn lẻ.

## 9. Exception, validation và bất biến ngay trong critical section

Một `synchronized` block không tự “rollback” trạng thái khi mã ném exception. Nếu writer cập nhật trường thứ nhất, rồi phép tính hoặc gọi API thứ hai ném lỗi, writer sẽ rời vùng khóa và nhả monitor — **trạng thái sai có thể trở thành trạng thái công khai lâu dài**.

Ví dụ nguy hiểm:

```java
synchronized (lock) {
    left -= amount;        // Đã thay đổi.
    checkExternalRule();   // Nếu ném exception...
    right += amount;       // ...không chạy.
}
```

Đoạn trên là mã minh họa. Để tránh, **kiểm tra mọi điều kiện có thể kiểm tra trước khi bắt đầu công bố thay đổi**, tính giá trị đích bằng biến cục bộ, rồi mới commit cả nhóm trường khi biết trạng thái mới hợp lệ:

```java
synchronized (lock) {
    if (amount < 0 || amount > left) {
        throw new IllegalArgumentException("invalid amount");
    }

    int nextLeft = Math.subtractExact(left, amount);
    int nextRight = Math.addExact(right, amount);

    if (nextLeft < 0 || nextRight < 0) {
        throw new IllegalStateException("invalid balance");
    }

    left = nextLeft;
    right = nextRight;
}
```

Đây là đoạn minh họa nằm trong phương thức của đối tượng sở hữu `left`, `right` và `lock`. `Math.subtractExact` và `Math.addExact` phát hiện overflow thay vì âm thầm tràn số. Mọi kiểm tra và phép tính có thể ném exception diễn ra **trước hai phép gán**, nên nếu lỗi, không trường nào thay đổi. Hai phép gán cuối vẫn có một trạng thái trung gian ở mức nội bộ, nhưng khi mọi reader đều lấy cùng lock, trạng thái đó không thể bị quan sát từ các thread khác theo giao thức này.

Nếu cần gọi dịch vụ ngoài, ghi tệp hoặc phát sự kiện trong giao dịch, hãy xem xét tính nguyên tử của **toàn bộ hệ thống**; `synchronized` chỉ điều phối thread trong JVM chứ không rollback tài nguyên bên ngoài.

## 10. Quan hệ với Java Memory Model

Java Memory Model (JMM) cung cấp các quy tắc để quyết định những thao tác nào có bảo đảm về thứ tự/visibility giữa thread. Với invariant nhiều trường, một số nguyên tắc then chốt:

- **Happens-before**: thao tác nhả một monitor xảy ra trước thao tác lấy tiếp theo trên cùng monitor; do đó reader lấy cùng monitor sẽ quan sát các ghi cần thiết của writer đã hoàn thành.
- **Volatile**: bảo đảm visibility/thứ tự ở cấp các thao tác volatile, nhưng không tạo tự động một “transaction” trên nhiều biến.
- **AtomicReference**: có thể công bố một snapshot bất biến thành một thao tác cập nhật tham chiếu atomic; nếu reader lấy tham chiếu **một lần**, nó đọc tất cả trường từ cùng snapshot.
- **Thread.start / Thread.join**: tạo các quan hệ happens-before nhất định, hữu ích khi chuyển trạng thái giữa giai đoạn trước/sau một worker. Nhưng nếu reader và writer thực sự đang chạy đồng thời, `join()` không bảo vệ các lần đọc giữa hai phép gán của writer.

Một cách diễn đạt khác là **linearizability**: từ góc nhìn của các lời gọi đồng thời, thao tác chuyển tiền nên có vẻ xảy ra tại một **điểm commit duy nhất**. Khi `transfer` và `snapshot` được bảo vệ cùng một monitor, các lời gọi đó có thể được sắp thứ tự thành các trạng thái hợp lệ. Khi dùng immutable snapshot + atomic CAS, điểm công bố tham chiếu là điểm chuyển trạng thái.

**Không nhầm linearizability của một thao tác trong một đối tượng Java với tính nguyên tử của giao dịch phân tán**. Nếu thao tác vừa sửa RAM vừa yêu cầu ngân hàng, mạng hay cơ sở dữ liệu khác cập nhật, các hệ thống đó cần cơ chế giao dịch/bù trừ của riêng chúng.

## 11. Thiết kế API khó dùng sai

Một số cải tiến giúp invariant dễ được giữ đúng ngay từ cấu trúc chương trình:

1. **Đóng gói trường liên quan** trong một lớp như `Ledger`. Không để caller sửa trực tiếp `left` hoặc `right`.
2. **Định nghĩa invariant thành câu rõ ràng**: ví dụ “mọi snapshot công khai phải có tổng tiền đúng và số dư không âm”.
3. **Expose thao tác nghiệp vụ** như `transfer(amount)` thay vì cho caller gọi `setLeft()` rồi `setRight()`.
4. **Expose một snapshot đầy đủ** thay vì hai getter đọc ở hai thời điểm khác nhau khi caller cần tính toán liên quan giữa chúng.
5. **Dùng một chiến lược đồng bộ nhất quán**. Không có nghĩa chỉ dùng một khóa cho toàn hệ thống; khóa nên bảo vệ đúng nhóm dữ liệu có cùng invariant.
6. **Kiểm tra lỗi trước khi commit** và bảo đảm exception không để lại trạng thái công khai sai.
7. **Kiểm thử bằng lịch chạy có kiểm soát** (latch/barrier) để chủ động lộ race condition, rồi bổ sung stress test khi cần. Stress test không thay thế lập luận về tính đúng đắn.

Chọn giải pháp theo phạm vi sở hữu dữ liệu: một đối tượng chia sẻ trong JVM có thể dùng monitor, một trạng thái nhỏ bất biến nhiều reader có thể dùng `AtomicReference`, còn tài nguyên nằm ngoài JVM cần cơ chế nhất quán của tầng lưu trữ/giao tiếp đó.

## 12. Chẩn đoán khi nghi ngờ broken invariant

Nếu thấy giá trị tổng, cặp `start/end`, phiên bản cấu hình hoặc số lượng không khớp trong log, hãy kiểm tra theo thứ tự:

- **Invariant là gì?** Viết dưới dạng mệnh đề có thể đánh giá: `left + right == 100`, `version == payload.version()`, v.v.
- **Có bao nhiêu trường phải thay đổi cùng nhau?** Vẽ các phép ghi theo thứ tự thực thi và liệt kê trạng thái trung gian.
- **Ai đọc những trường đó?** Reader có lấy từng getter tách rời? Có giữ cùng monitor không? Có lấy nhiều lần một tham chiếu thay đổi được không?
- **Tất cả writer có tuân theo cùng giao thức không?** Một đường cập nhật bỏ qua khóa là đủ tạo lỗ hổng.
- **Có exception giữa chừng không?** Mã có tính hết trạng thái mới trước khi commit không?
- **Có nhiều writer cạnh tranh không?** Nếu có, một phép gán volatile hay một cặp atomic đơn lẻ chưa giải quyết được lost update.
- **Có giới hạn ngoài JVM không?** Khóa Java chỉ bảo vệ các thread cùng dùng khóa đó, không thể khóa một tiến trình khác chỉ bằng cách tạo `new Object()`.

Cần phân biệt log bị thu thập ở hai thời điểm (ví dụ log `left` trước và `right` sau cập nhật) với snapshot thật sự mà chương trình sử dụng. Nếu muốn log tổng thể nhất quán, log một snapshot đầy đủ được đọc theo cùng giao thức đồng bộ.

## 13. Bài tập tự kiểm tra

**Tình huống A.** Hai trường `volatile int min`, `volatile int max` phải bảo đảm `min <= max`. Writer cập nhật `min = 8` rồi `max = 10` từ trạng thái `(1, 5)`. Reader có thể thấy `min=8,max=5` không?

**Trả lời:** Có. Mỗi trường volatile riêng lẻ không khiến hai phép ghi thành một thao tác. Nếu invariant phải luôn đúng với reader, cần đồng bộ nhóm cập nhật và nhóm đọc, hoặc công bố một snapshot bất biến `(8,10)`.

**Tình huống B.** `left` và `right` đều là `AtomicInteger`. Writer gọi `left.addAndGet(-20)` rồi `right.addAndGet(20)`. Invariant tổng có được bảo đảm không?

**Trả lời:** Không. Hai phép cộng/trừ riêng được thực hiện nguyên tử nhưng có một khoảng giữa chúng mà tổng bị lệch. Gộp dữ liệu và bảo vệ giao dịch/snapshot như một đơn vị logic.

**Tình huống C.** Writer thực hiện cả hai phép cập nhật trong `synchronized(lock)`, reader chỉ gọi hai getter mà mỗi getter `synchronized(lock)` riêng. Reader có thể nhìn thấy hai thế hệ khác nhau không?

**Trả lời:** Có. Monitor được nhả giữa hai getter; writer có thể cập nhật trong khoảng đó. Cần một getter `snapshot()` lấy cả hai dưới **một lần giữ lock**.

**Tình huống D.** Writer đã khóa đúng và kiểm tra input ban đầu, nhưng sau khi gán `left` thì một hàm bên ngoài ném exception. Khóa có tự rollback `left` không?

**Trả lời:** Không. Monitor vẫn được nhả khi thoát do exception nhưng không hoàn tác phép ghi đã xảy ra. Tính toán/kiểm tra trước commit; không thực hiện thao tác ngoài có thể thất bại giữa các phép gán trạng thái liên quan.

**Tình huống E.** `AtomicReference<Balances>` giữ một record bất biến. Reader gọi `state.get().left()` và `state.get().right()` tại hai thời điểm. Có chắc chắn snapshot nhất quán không?

**Trả lời:** Không. Hai lần `get()` có thể trả về hai record khác nhau. Lấy một biến cục bộ `Balances snapshot = state.get();` rồi đọc tất cả trường từ biến đó.

**Tình huống F.** Khi nào invariant có thể tạm sai ở bên trong `synchronized` mà API vẫn bảo đảm cho caller thấy trạng thái đúng?

**Trả lời:** Khi mọi lần quan sát trạng thái có liên quan đều phải lấy cùng monitor, không có callback/rò rỉ trạng thái giữa chừng, và mọi đường thoát (kể cả exception) chỉ công bố trạng thái hợp lệ. Reader không thể xen vào giữa các phép gán khi writer giữ monitor; tuy nhiên vẫn nên tính/kiểm tra trước khi gán để tránh lỗi để lại trạng thái sai.

## 14. Tổng kết

**Broken invariant khi cập nhật nhiều biến liên quan** xảy ra vì một thay đổi logic được triển khai bằng nhiều phép đọc/ghi, cho phép các thread khác nhìn thấy một tổ hợp giá trị không thuộc trạng thái hợp lệ. Từng phép gán đúng, từng trường `volatile` hoặc từng `AtomicInteger` **không bảo đảm** toàn bộ nhóm trường nhất quán.

Có hai hướng sửa thường dùng trong Java: **khóa toàn bộ thao tác cập nhật và đọc snapshot bằng cùng monitor/lock**, hoặc **tạo một snapshot bất biến và công bố nguyên tử tham chiếu tới snapshot đó**. Trước khi commit, phải kiểm tra đầu vào, xử lý khả năng exception, và xác định rõ tất cả writer/reader tuân theo cùng giao thức. Hãy bảo vệ **invariant của đối tượng như một đơn vị**, thay vì chỉ bảo vệ từng biến đơn lẻ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="race-conditions-007">TOCTOU và lỗi đồng thời theo thời điểm</a>

<details>
<summary>Xem chi tiết</summary>

# TOCTOU và lỗi đồng thời theo thời điểm trong Java

## 1. TOCTOU là gì?

**TOCTOU (Time Of Check To Time Of Use)** là một loại race condition xảy ra khi chương trình **kiểm tra một điều kiện tại thời điểm T1** rồi **dùng kết quả kiểm tra để hành động tại T2**, trong khi trạng thái có thể đã thay đổi giữa hai thời điểm đó.

Ví dụ: kiểm tra “kho còn ít nhất một sản phẩm”, sau đó trừ một sản phẩm. Hai thread đều thấy số lượng là 1; trước khi thread nào trừ, cả hai đều vượt qua phép kiểm tra. Nếu mỗi thread tiếp tục trừ theo kết quả cũ, số lượng có thể xuống -1 và hai yêu cầu đều được chấp nhận dù chỉ có một đơn vị hàng.

```text
Trạng thái ban đầu: stock = 1

Thread A                   Thread B
---------                  ---------
check stock > 0 → true
                           check stock > 0 → true
                           ... trạng thái có thể đổi ...
stock = stock - 1
                           stock = stock - 1

Kết quả có thể sai: hai lượt thành công, stock = -1
```

**Lỗi cốt lõi không nằm ở việc phép kiểm tra sai tại T1**. Kiểm tra có thể hoàn toàn đúng vào lúc thực hiện, nhưng **không còn là căn cứ hợp lệ cho thao tác ở T2**. Độ dài của khoảng cách T1–T2 không cần lớn: một lệnh máy khác, một lần chuyển ngữ cảnh, thao tác I/O hoặc lời gọi phương thức đều có thể mở cơ hội thay đổi trạng thái.

TOCTOU có thể xảy ra giữa các thread trong cùng JVM, giữa các tiến trình, giữa ứng dụng và hệ thống tệp, hoặc giữa nhiều ứng dụng truy cập một tài nguyên chung. Chủ đề bài này là **cơ chế đồng thời nói chung trong Java** và cách xử lý bằng API Java/JDK; không phụ thuộc vào framework.

## 2. Phân biệt TOCTOU với data race và stale read

- **TOCTOU** mô tả lỗi ở cấp **logic kiểm tra–sử dụng**: điều kiện từng đúng nhưng trở nên lỗi thời trước khi hành động. Nó có thể xảy ra ngay cả khi từng phép đọc hoặc ghi riêng lẻ là atomic.
- **Data race theo Java Memory Model** xuất hiện khi hai hành động truy cập cùng biến, ít nhất một hành động ghi, không có quan hệ happens-before thích hợp và không phải đều là thao tác volatile. Data race có thể dẫn tới hành vi quan sát khó suy luận theo mô hình bộ nhớ.
- **Stale read** là việc thread quan sát giá trị cũ hoặc dùng một snapshot đã cũ. Stale read có thể góp phần gây TOCTOU, nhưng không phải tất cả lỗi TOCTOU đều do vi phạm visibility.
- **Atomicity (tính nguyên tử)** ở đây là yêu cầu xem toàn bộ quyết định “nếu hợp lệ thì thực hiện” như **một thao tác logic không bị chen ngang ở điểm có thể làm sai bất biến**.

Ví dụ `AtomicInteger.get()` và `AtomicInteger.decrementAndGet()` đều là thao tác atomic riêng lẻ, nhưng tổ hợp “`get() > 0` rồi `decrementAndGet()`” **không tự trở thành một thao tác atomic**. Tương tự, `volatile` có thể giải quyết visibility cho một biến tín hiệu, nhưng không khóa trạng thái trong khoảng giữa kiểm tra và sử dụng.

Không thể sửa một lỗi TOCTOU chỉ bằng cách đặt `Thread.sleep()`, `Thread.yield()` hoặc tăng priority: các thao tác đó không tạo ra nguyên tử tính cho toàn bộ đoạn quyết định.

## 3. Ví dụ Java 17+: tái hiện lỗi bằng lịch chạy có kiểm soát

Ví dụ dưới đây dùng `CountDownLatch` để **cố ý bảo đảm cả hai thread đều kiểm tra trước khi thread nào thực hiện hành động**. Nhờ vậy đầu ra của phần lỗi được tái hiện xác định thay vì phải “hy vọng” scheduler vô tình tạo ra race.

Tạo tệp `TocTouInventory.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class TocTouInventory {

    private static boolean reserveWithCas(AtomicInteger stock) {
        while (true) {
            int observed = stock.get();
            if (observed <= 0) {
                return false;
            }
            if (stock.compareAndSet(observed, observed - 1)) {
                return true;
            }
            // Trạng thái đã đổi: đọc lại và thử lần nữa.
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicInteger unsafeStock = new AtomicInteger(1);
        AtomicInteger unsafeAccepted = new AtomicInteger();

        CountDownLatch bothChecked = new CountDownLatch(2);
        CountDownLatch continueAfterCheck = new CountDownLatch(1);

        Runnable unsafeReservation = () -> {
            boolean wasAvailable = unsafeStock.get() > 0;
            bothChecked.countDown();

            try {
                continueAfterCheck.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            if (wasAvailable) {
                unsafeStock.decrementAndGet();
                unsafeAccepted.incrementAndGet();
            }
        };

        Thread a = new Thread(unsafeReservation, "unsafe-A");
        Thread b = new Thread(unsafeReservation, "unsafe-B");
        a.start();
        b.start();

        bothChecked.await();
        continueAfterCheck.countDown();
        a.join();
        b.join();

        System.out.println("Unsafe accepted: " + unsafeAccepted.get());
        System.out.println("Unsafe stock: " + unsafeStock.get());

        AtomicInteger safeStock = new AtomicInteger(1);
        AtomicInteger safeAccepted = new AtomicInteger();

        Runnable safeReservation = () -> {
            if (reserveWithCas(safeStock)) {
                safeAccepted.incrementAndGet();
            }
        };

        Thread c = new Thread(safeReservation, "safe-C");
        Thread d = new Thread(safeReservation, "safe-D");
        c.start();
        d.start();
        c.join();
        d.join();

        System.out.println("CAS accepted: " + safeAccepted.get());
        System.out.println("CAS stock: " + safeStock.get());
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac TocTouInventory.java
java TocTouInventory
```

Khi chương trình hoàn tất bình thường, đầu ra xác định:

```text
Unsafe accepted: 2
Unsafe stock: -1
CAS accepted: 1
CAS stock: 0
```

**Giải thích phần lỗi:**

1. `unsafeStock` khởi đầu bằng 1. Hai thread cùng đọc `unsafeStock.get() > 0` trước khi vượt qua `continueAfterCheck.await()`.
2. `bothChecked.await()` ở `main` bảo đảm cả hai đã lưu `wasAvailable = true`. Sau đó `main` mới mở chốt cho cả hai tiếp tục.
3. Hai lần `decrementAndGet()` đều atomic; vì thế chúng lần lượt đưa số lượng từ 1 xuống 0 rồi -1. Lỗi vẫn xảy ra vì **quyết định có được trừ hay không dựa trên hai kết quả kiểm tra đã cũ**.
4. `unsafeAccepted` trở thành 2. Không có yêu cầu nào bị từ chối mặc dù chỉ tồn tại một đơn vị hàng.

**Giải thích phần sửa:** `compareAndSet(observed, observed - 1)` chỉ ghi khi giá trị hiện tại còn đúng bằng giá trị đã đọc. Nếu thread khác đã thay đổi `stock`, CAS trả về `false`; vòng lặp đọc lại. Chỉ một thread có thể chuyển 1 → 0 thành công; thread còn lại quan sát 0 và trả về `false`. Thao tác làm giảm và điều kiện “đã quan sát giá trị nào” được liên kết ở **điểm tuyến tính hóa (linearization point)** của CAS thành công.

Lưu ý: `safeAccepted.incrementAndGet()` được gọi sau CAS thành công; đây là bộ đếm thống kê của ví dụ. Trong ứng dụng cần cập nhật **nhiều trường dữ liệu như một transaction**, chỉ CAS trên số lượng không tự bảo đảm mọi trường đều cập nhật nguyên tử với nhau.

## 4. Vì sao `check-then-act` nguy hiểm?

Mẫu phổ biến:

```java
if (conditionIsTrue()) {
    performAction();
}
```

Đây là **đoạn minh họa**, chưa có định nghĩa cho hai phương thức. Nó chỉ an toàn khi ít nhất một trong các điều kiện dưới đây được chứng minh:

- Trạng thái liên quan là **bất biến** trong khoảng cần quan sát.
- Chỉ có một tác nhân được phép thay đổi trạng thái trong phạm vi cần xét.
- Có **lock đồng nhất** bảo vệ cả phép kiểm tra và hành động, và mọi tác nhân có quyền sửa đều tuân thủ cùng giao thức lock.
- Có API **atomic check-and-act** (như CAS hoặc `putIfAbsent`) thực hiện quyết định bằng một thao tác có bảo đảm.
- Hệ thống tài nguyên bên ngoài cung cấp giao thức bảo đảm tương đương, chẳng hạn ràng buộc duy nhất hoặc một transaction phù hợp.

Nếu chỉ bảo vệ phép `check` mà thả lock trước `act`, lỗ hổng vẫn còn. Tương tự, **hai khối `synchronized` tách rời** trên cùng monitor chưa chắc đủ nếu giữa chúng có đoạn nhường monitor cho thread khác. Cần bảo vệ **toàn bộ bất biến**, không chỉ từng lời gọi API.

### Ví dụ với `synchronized`

```java
final class Stock {
    private int remaining = 1;

    public synchronized boolean reserveOne() {
        if (remaining == 0) {
            return false;
        }
        remaining--;
        return true;
    }

    public synchronized int remaining() {
        return remaining;
    }
}
```

Đây là **class độc lập có thể dùng trong một chương trình khác**, không có `main`. Khi mọi truy cập cập nhật `remaining` đều đi qua monitor của **cùng đối tượng `Stock`**, check và decrement là một critical section; hai thread tranh cùng đơn vị hàng không thể đều thành công. Với ví dụ này, điều kiện `remaining == 0` đủ vì `remaining` khởi đầu không âm và chỉ giảm qua `reserveOne()`; trong thiết kế tổng quát, dùng `remaining <= 0` phòng trạng thái đầu vào không hợp lệ.

**Giới hạn:** `synchronized` của JVM chỉ phối hợp các thread dùng cùng monitor trong JVM đó. Nó không khóa được tiến trình khác, JVM khác hoặc một dịch vụ bên ngoài không tham gia cùng giao thức.

## 5. TOCTOU với cấu trúc dữ liệu concurrent

Một `ConcurrentHashMap` bảo đảm an toàn cho các thao tác được tài liệu hóa, **không có nghĩa tổ hợp nhiều lời gọi độc lập đều nguyên tử**.

### Cách dễ sai: kiểm tra rồi ghi

```java
// Minh họa, giả sử map đã được khai báo:
if (!map.containsKey("invoice-7")) {
    map.put("invoice-7", "processing");
}
```

Giữa `containsKey` và `put`, thread khác có thể đã đặt cùng key. Hai thread đều tưởng rằng mình là người đầu tiên. Thậm chí khi các thao tác trên map đều thread-safe, **quy tắc “chỉ một người được đăng ký”** vẫn có thể bị vi phạm ở mức logic.

### Cách đúng khi cần đăng ký một key duy nhất

Dùng `putIfAbsent` để gộp phép “chưa có thì đặt” thành thao tác nguyên tử theo key.

Tạo tệp `PutIfAbsentExample.java`:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PutIfAbsentExample {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, String> registrations =
                new ConcurrentHashMap<>();
        AtomicInteger winners = new AtomicInteger();

        Runnable register = () -> {
            String previous = registrations.putIfAbsent(
                    "invoice-7", Thread.currentThread().getName());
            if (previous == null) {
                winners.incrementAndGet();
            }
        };

        Thread a = new Thread(register, "A");
        Thread b = new Thread(register, "B");
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("Successful registrations: " + winners.get());
        System.out.println("Key exists: "
                + registrations.containsKey("invoice-7"));
    }
}
```

Chạy:

```bash
javac PutIfAbsentExample.java
java PutIfAbsentExample
```

Đầu ra:

```text
Successful registrations: 1
Key exists: true
```

Owner thực tế có thể là `A` hoặc `B`, tùy lịch chạy; ví dụ chủ ý **không in owner** vì không thể dự đoán ai thắng. `putIfAbsent` là đúng cho **quy tắc có một giá trị được đăng ký cho key** trong map. Nó không tạo một transaction bao gồm cả các side effect bên ngoài map.

**Cẩn thận với `computeIfAbsent`:** API có thể phù hợp để tạo giá trị theo key, nhưng mapping function không nên thực hiện những tác dụng phụ khó lặp lại, gọi API có thể tái nhập cập nhật map hoặc chạy tác vụ lâu; cần xem hợp đồng cụ thể của `ConcurrentHashMap` và cách xử lý lỗi. Đừng chuyển một quy tắc cần transaction đa tài nguyên thành “một hàm lambda trong map” rồi cho rằng toàn bộ đều atomic.

## 6. TOCTOU với file system: `exists()` rồi thao tác

Đây là một trong những trường hợp điển hình, bởi hệ thống tệp có thể bị thay đổi bởi **process khác**, không chỉ thread trong JVM.

### Cách dễ sai

```java
// Minh họa: path đã được khai báo.
if (!java.nio.file.Files.exists(path)) {
    java.nio.file.Files.writeString(path, "new file");
}
```

Một tiến trình khác có thể tạo `path` **sau** `exists(path)` nhưng **trước** `writeString(path, ...)`. Nếu lời gọi ghi sử dụng tùy chọn mặc định phù hợp với hành vi tạo hoặc cắt ngắn tệp, thao tác có thể tác động lên tệp mà tiến trình khác vừa tạo. Ngược lại, khi `exists(path)` trả về `true`, file có thể đã bị xóa hoặc thay thế ngay trước lần sử dụng.

**Hai lưu ý quan trọng:**

- `Files.exists(path)` trả về `false` không đồng nghĩa chắc chắn “file không tồn tại”: nếu không xác định được sự tồn tại vì lỗi truy cập hoặc I/O, kết quả có thể là `false`. `Files.notExists(path)` không phải phủ định logic tuyệt đối của `exists(path)`; cả hai có thể cùng `false`.
- Việc kiểm tra quyền, loại file, thuộc tính, tên đích hoặc symbolic link trước một thao tác sau đó cũng có thể tạo TOCTOU khi đối tượng tại đường dẫn bị thay thế.

### Dùng thao tác nguyên tử “chỉ tạo nếu chưa tồn tại”

Trong Java NIO, `Files.createFile(path)` yêu cầu việc kiểm tra file chưa tồn tại và tạo file là một thao tác atomic đối với các hoạt động hệ thống tệp có thể tác động tới thư mục theo hợp đồng API. Nếu file đã tồn tại, `FileAlreadyExistsException` là kết quả cần xử lý, thay vì tự kiểm tra trước rồi ghi.

Tạo tệp `CreateOnlyOnce.java`:

```java
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CreateOnlyOnce {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("toc-demo-");
        Path file = directory.resolve("receipt.txt");

        try {
            Files.createFile(file);
            System.out.println("First creation succeeded");

            try {
                Files.createFile(file);
                System.out.println("Unexpected duplicate");
            } catch (FileAlreadyExistsException e) {
                System.out.println("Duplicate rejected");
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }
}
```

Biên dịch và chạy:

```bash
javac CreateOnlyOnce.java
java CreateOnlyOnce
```

Đầu ra nếu hệ thống tệp cho phép các thao tác và không có tác nhân bên ngoài can thiệp vào thư mục tạm:

```text
First creation succeeded
Duplicate rejected
```

Ví dụ tạo **thư mục tạm mới**, không thao tác trên file thật của người học. Việc dọn dẹp có thể ném `IOException` nếu môi trường không cho xóa hoặc file bị thay đổi; đó là lỗi I/O cần xử lý theo yêu cầu ứng dụng.

Nếu mục tiêu là mở file **chỉ khi file chưa tồn tại**, `StandardOpenOption.CREATE_NEW` trong API mở kênh/ghi file có ngữ nghĩa tạo mới có điều kiện tương tự. Nếu cần cập nhật nội dung nhiều bước, việc “tạo tên duy nhất” **không** tự biến mọi lần ghi tiếp theo thành transaction. Cần thiết kế ghi tạm, xác nhận, commit, đồng bộ và xử lý lỗi theo ngữ nghĩa mong muốn.

### TOCTOU có yếu tố bảo mật

Giả sử chương trình kiểm tra một đường dẫn nằm trong thư mục cho phép rồi mới mở file. Nếu giữa hai thao tác có người thay đổi liên kết hoặc thành phần đường dẫn, chương trình có thể sử dụng **đối tượng khác** với đối tượng đã kiểm tra. Đây là vấn đề **check-path-then-open**, có thể nghiêm trọng khi mã có quyền truy cập file lớn hơn người điều khiển đường dẫn.

Biện pháp phụ thuộc hệ điều hành và nhà cung cấp file system: giới hạn quyền thư mục, không tin path do đối tượng không tin cậy kiểm soát, ưu tiên thao tác gắn với **đối tượng đã mở** hoặc directory handle thay vì kiểm tra tên rồi mở lại, sử dụng lựa chọn không theo symbolic link khi phù hợp, và cân nhắc `SecureDirectoryStream` nếu provider có hỗ trợ. `NOFOLLOW_LINKS` trên **một** phép kiểm tra hoặc thao tác không mặc nhiên làm an toàn toàn bộ chuỗi thao tác đa bước; cần xét cả thành phần đường dẫn, quyền sửa thư mục và phạm vi bảo đảm của API.

## 7. TOCTOU khi nhiều process hoặc dịch vụ cùng dùng tài nguyên

Lock trên đối tượng Java giải quyết tranh chấp giữa những thread **cùng tuân thủ lock đó** trong cùng JVM. Nó không tự cung cấp điều kiện atomic xuyên JVM hoặc xuyên dịch vụ.

Các ví dụ thường gặp:

| Quy tắc nghiệp vụ | Cách kiểm tra rồi hành động dễ lỗi | Điểm bảo đảm cần có |
| --- | --- | --- |
| Một định danh chỉ được tạo một lần | `exists(id)` rồi `insert(id)` | Ràng buộc unique, ghi có điều kiện hoặc giao thức atomic của nơi lưu trữ |
| Không bán quá số lượng tồn | `read(stock) > 0` rồi `write(stock-1)` | Atomic conditional update, compare-and-swap hoặc transaction phù hợp |
| Mỗi job chỉ một worker nhận | `isPending(job)` rồi `markRunning(job)` | Atomic claim có điều kiện, lease hoặc cơ chế queue có bảo đảm |
| File chỉ được tạo một lần | `Files.exists` rồi `writeString` | Atomic create-if-absent như `createFile` hoặc `CREATE_NEW` |
| Một người dùng được truy cập tài nguyên | `checkPermission` rồi sử dụng handle/tài nguyên | Quyền được kiểm tra ở thao tác có hiệu lực hoặc trên đối tượng đã xác định |

Một **transaction** chỉ có ích nếu mức cô lập, lock, điều kiện cập nhật và ràng buộc của nơi lưu trữ thực sự bảo vệ bất biến. Không thể kết luận “đã ở trong transaction” là hết TOCTOU mà không xem chính xác loại thao tác và mức isolation.

**Idempotency** và unique key cũng có tác dụng hữu ích đối với xử lý retry; tuy nhiên idempotency cần được áp dụng tại điểm ghi hoặc tại nơi được dùng làm nguồn chân lý. Một cache kiểm tra rồi gửi request không bảo đảm ngăn trùng tác vụ nếu nhiều tác nhân đồng thời.

## 8. Cách lựa chọn cơ chế khắc phục

### 8.1. Gộp phép kiểm tra và hành động thành một thao tác atomic

Đây thường là cách rõ ràng nhất nếu API hỗ trợ:

- `AtomicInteger.compareAndSet(expected, update)` hoặc `AtomicReference.compareAndSet(...)` khi cập nhật một trạng thái đơn có thể biểu diễn bằng giá trị/đối tượng atomic.
- `ConcurrentHashMap.putIfAbsent`, `replace(key, expected, newValue)`, `compute` hoặc `merge` khi cập nhật theo key trong map, tuân theo hợp đồng cụ thể của từng API.
- `Files.createFile` hoặc mở với `CREATE_NEW` khi cần tạo file nếu chưa tồn tại.
- Conditional update hoặc unique constraint của chính tài nguyên bên ngoài khi có nhiều JVM/process.

Một phép atomic giải quyết **đúng bất biến nó bao phủ**. CAS trên một trường không bảo đảm atomic cho đồng thời một file, một request mạng và một bản ghi khác.

### 8.2. Giữ lock xuyên toàn bộ đoạn quyết định

Nếu một đối tượng mutable có nhiều trường liên quan, dùng cùng một `synchronized` monitor hoặc `Lock` để bảo vệ check và update có thể đơn giản hơn việc cố ghép nhiều atomic variables. Tất cả đường ghi phải tuân theo cùng cơ chế; nếu có tác nhân bên ngoài thay đổi dữ liệu, lock cục bộ không đủ.

Trong đoạn critical section, hạn chế I/O chậm hoặc gọi code không kiểm soát để tránh giữ lock quá lâu, tăng tranh chấp hoặc tạo deadlock. Đừng “tách khóa” bằng cách mở khóa giữa check và update nếu chưa có cơ chế xác thực lại khi cập nhật.

### 8.3. Dùng version hoặc optimistic concurrency

Khi một đối tượng có nhiều trường, có thể đọc trạng thái kèm **version**, chuẩn bị thay đổi và chỉ ghi nếu version vẫn khớp. Nếu thất bại, đọc lại và thử lại theo giới hạn. Đây là cơ chế lạc quan dựa trên **điểm cập nhật có điều kiện** ở nguồn chân lý; chỉ so sánh version trong JVM rồi ghi không điều kiện vào hệ thống ngoài vẫn có TOCTOU.

### 8.4. Xử lý trường hợp điều kiện không còn đúng

TOCTOU không biến mất nếu chỉ phát hiện lỗi rồi tiếp tục như thành công. Phải định nghĩa:

1. Khi claim thất bại: trả thông báo “đã có người nhận”, retry với dữ liệu mới, hay bỏ qua?
2. Khi timeout hoặc interrupt: có thể xác định thao tác đã xảy ra chưa; có cần reconcile?
3. Khi phát sinh side effect một phần: có cơ chế rollback, bù trừ hoặc idempotency không?
4. Khi nhiều tài nguyên tham gia: bất biến nằm ở đâu, tác nhân nào có quyền thay đổi, và thao tác nào quyết định thành công?

Đối với CAS loop, tránh retry vô hạn nếu yêu cầu có thời hạn và áp lực cạnh tranh cao; đo độ tranh chấp và dùng backoff/giới hạn phù hợp khi cần.

## 9. Kiểm thử và phát hiện TOCTOU

Lỗi timing thường hiếm xuất hiện ở máy phát triển nhưng bùng phát khi tăng tải. **Không thể chứng minh chương trình hết race chỉ bằng việc chạy một lần hoặc thêm `sleep()`.**

Cách kiểm thử có giá trị:

1. Xác định **bất biến** cần giữ: số lượng không âm, mỗi key có tối đa một chủ sở hữu, file không bị ghi đè, chỉ người có quyền được sử dụng tài nguyên.
2. Liệt kê **khe thời gian**: sau khi đọc trạng thái, sau khi kiểm tra điều kiện, trước khi ghi hoặc trước khi mở file.
3. Dùng `CountDownLatch`/`CyclicBarrier` trong test để buộc các thread tiến đến cùng một mốc rồi mới tiếp tục, giống ví dụ tồn kho.
4. Thực hiện stress tests trên nhiều thread và nhiều kích thước đầu vào; theo dõi sai bất biến, ngoại lệ, bế tắc, throughput và tỉ lệ retry.
5. Với hệ thống ngoài JVM, thử **nhiều tiến trình độc lập** cùng truy cập, kể cả trên file system hoặc nơi lưu trữ tương ứng. Lock cục bộ không mô phỏng được các tác nhân ngoài.
6. Kiểm tra từng cơ chế khắc phục ở **điểm atomic thực tế**, thay vì chỉ tin vào tên phương thức “thread-safe”. Phải xem cả toàn bộ chuỗi logic.
7. Dùng JFR, thread dump, logging có thread name và thông tin tranh chấp để tìm đường chạy; nhưng không dựa vào log để bắt buộc thứ tự thực thi, vì quan sát cũng có thể thay đổi timing.

Ví dụ `CountDownLatch` trong bài tạo lịch xen kẽ **có chủ đích**, giúp test TOCTOU lặp lại được. Tập hợp kiểm thử vẫn chỉ bổ trợ cho thiết kế đúng; chứng cứ mạnh nhất đến từ việc chứng minh bất biến được bảo vệ bởi một thao tác/giao thức có bảo đảm.

## 10. Những sai lầm thường gặp

- **“Đã dùng `AtomicInteger` nên không có TOCTOU.”** Sai nếu check và act là hai thao tác atomic riêng: `get()` rồi `decrementAndGet()` vẫn có khe thời gian.
- **“Đã dùng `ConcurrentHashMap` nên `containsKey` rồi `put` là atomic.”** Sai: an toàn của từng phương thức không tự hợp thành tính nguyên tử cho cặp lời gọi.
- **“Thêm `volatile` là đủ.”** `volatile` xử lý visibility/order nhất định, không biến mọi logic nhiều bước thành atomic.
- **“Chèn `sleep(100)` sẽ tránh race.”** Sleep thay đổi lịch chạy, có thể che lỗi hoặc làm lỗi dễ xảy ra hơn; nó không bảo vệ bất biến.
- **“Bọc mỗi bước trong `synchronized` riêng là đủ.”** Sai nếu lock được thả giữa check và act; thread khác có thể sửa trạng thái trong khoảng hở.
- **“Lock Java xử lý được tác nhân ngoài JVM.”** Sai: process khác không bị ràng buộc bởi monitor Java cục bộ.
- **“`Files.exists(path)` bảo đảm lần mở tiếp theo dùng đúng file đó.”** Sai: đối tượng tại đường dẫn có thể thay đổi hoặc không thể kiểm tra chính xác.
- **“Thử lại ngay là vô hại.”** Retry có thể tạo side effect trùng nếu không biết lần đầu đã commit hay chưa; cần idempotency và giao thức xác nhận rõ.
- **“Không thấy lỗi trong 10.000 lần chạy thì an toàn.”** Không đủ: lịch chạy khác và workload khác vẫn có thể lộ race.

## 11. Checklist áp dụng vào mã Java

Trước bất kỳ đoạn **check → use**, tự hỏi:

1. **Trạng thái nào đang được kiểm tra?** Đó là biến trong JVM, một map, file, bản ghi hay tài nguyên dịch vụ?
2. **Ai có thể thay đổi nó?** Thread cùng JVM, process khác, hệ thống tệp, dịch vụ từ xa hay người dùng khác?
3. **Khoảng T1–T2 có cho phép thay đổi không?** Nếu có, kết quả kiểm tra đã cũ có gây vi phạm bất biến không?
4. **Điểm atomic nằm ở đâu?** CAS, `synchronized` xuyên toàn bộ đoạn, `putIfAbsent`, create-if-absent, unique constraint hay conditional update?
5. **Các thành phần cùng tham gia có dùng đúng giao thức đó không?** Một khóa cục bộ không bảo vệ một tài nguyên do bên ngoài quản lý.
6. **Khi tranh chấp hoặc thất bại sẽ làm gì?** Retry, từ chối, timeout, rollback, bù trừ, idempotency và kiểm tra lại kết quả.
7. **Có kiểm thử chứng minh bất biến không?** Cố ý tạo interleaving, stress test, và kiểm tra hiệu quả dưới nhiều tác nhân.

**Ghi nhớ:** một điều kiện **đúng lúc kiểm tra** không bảo đảm còn đúng **lúc sử dụng**. Sửa TOCTOU bằng cách bảo vệ **toàn bộ quyết định** tại nơi sở hữu trạng thái và xử lý trường hợp điều kiện thay đổi, thay vì cố đoán hoặc điều khiển tốc độ scheduler.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="race-conditions-008">Không an toàn khi dùng collection thường chung nhiều luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Không an toàn khi dùng collection thường chung nhiều luồng trong Java

Một `ArrayList`, `HashMap` hay `HashSet` hoạt động đúng khi được sử dụng theo hợp đồng API. Tuy nhiên, khi **nhiều thread cùng truy cập một collection có thể thay đổi**, đặc biệt khi ít nhất một thread sửa nội dung hoặc cấu trúc, các collection thông thường trong `java.util` **không tự cung cấp cơ chế đồng bộ cần thiết**. Hậu quả có thể là mất phần tử, đếm sai, đọc dữ liệu cũ, vi phạm bất biến nội bộ hoặc gặp exception ngoài dự đoán. Không phải lỗi nào cũng biểu hiện khi chạy thử vài lần.

Bài này nằm trong nhóm **Race Conditions**, tập trung vào **Java concurrency nói chung**: xác định ranh giới thread safety, phân tích race condition, chọn phương thức bảo vệ và dùng collection thay thế phù hợp. Không bàn về thành phần concurrency riêng của Spring Framework.

## 1. Tại sao collection thông thường không mặc định thread-safe?

Phần lớn collection thông dụng của Java được thiết kế để cung cấp thao tác hiệu quả khi chương trình **quản lý việc truy cập đúng cách**. Ví dụ:

- `ArrayList<E>`: danh sách dựa trên mảng động, có các trường trạng thái nội bộ như số phần tử và vùng lưu trữ.
- `HashMap<K,V>`: bảng băm có bucket và cấu trúc liên kết phục vụ tra cứu/chèn/xóa.
- `HashSet<E>`: set dựa trên một `HashMap` để lưu các phần tử duy nhất.
- `LinkedList<E>`: danh sách liên kết, cập nhật kích thước và các liên kết nút khi thêm/xóa.
- `TreeMap<K,V>`, `TreeSet<E>`: cấu trúc cây phải duy trì thứ tự và invariant khi cập nhật.
- `ArrayDeque<E>`, `PriorityQueue<E>`: duy trì head/tail hoặc heap khi thêm/lấy phần tử.

Các cấu trúc này **không được thiết kế để tự chịu được mọi kiểu sửa đổi concurrent**. Một thao tác `list.add(x)` nhìn ở mức API là một lời gọi, nhưng triển khai có thể phải đọc kích thước hiện tại, kiểm tra dung lượng, ghi phần tử và cập nhật số phần tử. Khi hai thread xen kẽ các bước, không có bảo đảm mặc định rằng cả hai lời gọi tạo ra hiệu ứng như đã chạy tuần tự theo một thứ tự nào đó.

Điều cần phân biệt:

| Tình huống | Nhận định an toàn |
| --- | --- |
| Một thread sở hữu `ArrayList` và chỉ thread đó sửa/đọc | Thông thường an toàn về chia sẻ giữa thread vì không có cạnh tranh |
| Nhiều thread chỉ đọc collection **không bị sửa nữa** và tham chiếu đã được công bố an toàn | Có thể an toàn nếu nội dung được công bố đầy đủ và không có mutable value bị sửa ngoài ý muốn |
| Một thread đang `add/remove`, thread khác đang `get/iterate` | Không an toàn nếu thiếu cơ chế phối hợp |
| Nhiều thread cùng `add/remove` | Không an toàn nếu thiếu cơ chế phối hợp |
| Collection bọc bằng `Collections.synchronizedList` | Các thao tác đơn lẻ qua wrapper được đồng bộ; iteration và chuỗi thao tác vẫn cần xem xét riêng |
| Dùng `ConcurrentHashMap` hoặc `BlockingQueue` đúng hợp đồng | Có bảo đảm concurrent riêng của từng API; không có nghĩa mọi chuỗi nghiệp vụ đều atomic |

**`final` không đồng nghĩa thread-safe.** Ví dụ `private final List<String> tasks = new ArrayList<>();` chỉ ngăn việc gán lại tham chiếu `tasks`. `tasks.add(...)` vẫn thay đổi chính list được chia sẻ.

## 2. Race condition khi hai thread cùng thay đổi collection

**Race condition** là khi tính đúng đắn của chương trình phụ thuộc vào thứ tự thực thi và thời điểm các thao tác từ nhiều thread xen kẽ nhau, trong khi chương trình chưa áp đặt đủ quy tắc phối hợp.

Hình dung hai thread cùng thêm phần tử vào một list. Đây là sơ đồ logic đơn giản hóa, không phải mô tả từng lệnh bytecode của mọi JDK:

```text
Ban đầu: size = 10

Thread A                          Thread B
đọc size = 10
                                  đọc size = 10
ghi phần tử A tại index 10
                                  ghi phần tử B tại index 10
cập nhật size thành 11
                                  cập nhật size thành 11

Tổng cộng đã gọi add() hai lần
nhưng một phần tử có thể bị ghi đè / số đếm bị sai.
```

Sự cố có thể diễn ra theo lịch khác nhau: thỉnh thoảng kết quả đúng, thỉnh thoảng sai hoặc phát sinh exception. Không nên khẳng định sẽ xảy ra chính xác kết quả nào trên một phiên bản JDK cụ thể; đó là điểm nguy hiểm của chương trình có data race.

Một ví dụ dễ mắc:

```java
List<Integer> results = new ArrayList<>();

// Nhiều task chạy cùng lúc:
results.add(result);
```

Đây là đoạn minh họa, không phải chương trình đầy đủ. Nếu `results` là **cùng một instance** và nhiều worker cùng `add`, mỗi lần gọi không tự an toàn chỉ vì các giá trị `result` là immutable.

**Data race và visibility:** Không chỉ thao tác sửa gặp vấn đề. Nếu thread A sửa list còn thread B đọc kích thước mà không có quan hệ happens-before, B không có bảo đảm thấy cập nhật tại thời điểm mong muốn. Việc đọc một trường sau `Thread.sleep(...)` không tự thiết lập quan hệ đồng bộ với thread ghi.

### Một ví dụ cố ý không an toàn để nhận diện lỗi

Đoạn sau thể hiện **anti-pattern**, không dùng làm lời khẳng định stdout:

```java
import java.util.ArrayList;
import java.util.List;

final class UnsafeListExample {
    private final List<Integer> results = new ArrayList<>();

    void record(int value) {
        results.add(value); // Không an toàn nếu gọi đồng thời từ nhiều thread.
    }

    int count() {
        return results.size(); // Cũng có vấn đề nếu thread khác đang sửa.
    }
}
```

Không nên cố gắng tái hiện lỗi bằng cách thêm nhiều `Thread.sleep` hoặc phụ thuộc một giá trị in ra. Sự không an toàn nằm ở việc **hợp đồng `ArrayList` không bảo đảm các thao tác concurrent này**, dù có lần thử tình cờ trả về đủ phần tử.

## 3. Các kiểu lỗi điển hình theo collection

### `ArrayList` và `LinkedList`: ghi đè, kích thước sai, iteration không ổn định

Khi nhiều thread cùng `add`/`remove`, các cập nhật số phần tử và cấu trúc lưu trữ có thể đua nhau. Đọc theo chỉ số có thể không tương ứng với trạng thái mong đợi. Iteration trong lúc thread khác sửa cấu trúc có thể gặp `ConcurrentModificationException`, bỏ qua/quan sát không ổn định hoặc có hành vi khác không được bảo đảm.

Với `LinkedList`, thay đổi liên kết nút và kích thước cũng phải giữ invariant; chia sẻ không đồng bộ không giải quyết được vấn đề.

### `HashMap` và `HashSet`: mất update, kết quả truy vấn không đáng tin

`HashMap` cần duy trì bucket và các phần tử tương ứng. Trong lúc resize, put hoặc remove, việc một thread khác đọc/sửa cùng map thiếu đồng bộ có thể thấy dữ liệu không như mong đợi. `HashSet` dựa trên map nên cũng không miễn nhiễm.

Tránh lưu truyền các khẳng định tuyệt đối như "`HashMap` luôn rơi vào vòng lặp vô hạn khi resize": cách triển khai khác giữa các phiên bản Java và lỗi cụ thể không phải điều API bảo đảm. Kết luận đủ chắc chắn là **không được dựa vào thao tác concurrent thiếu đồng bộ trên `HashMap`**.

### `TreeMap`, `TreeSet`, `PriorityQueue` và `ArrayDeque`

Mỗi cấu trúc đều giữ một số bất biến nội bộ (cây cân bằng, heap, vị trí đầu/cuối). Khi nhiều thread thay đổi, bảo đảm đó phụ thuộc cách truy cập được phối hợp. Sử dụng chúng mà không bảo vệ không trở nên an toàn chỉ vì API `add` hoặc `put` ngắn gọn.

Nếu cần queue producer–consumer đa luồng, cân nhắc `BlockingQueue` hoặc `ConcurrentLinkedQueue` thay vì `ArrayDeque` dùng chung.

### Collection chứa đối tượng mutable

Ngay cả khi dùng collection có cơ chế đồng bộ, **đối tượng bên trong vẫn là vấn đề riêng**:

```java
ConcurrentHashMap<String, ArrayList<String>> map = new ConcurrentHashMap<>();
// ... các thread cùng lấy một value và gọi value.add(...)
```

`ConcurrentHashMap` bảo vệ cấu trúc map theo hợp đồng của nó; một `ArrayList` lưu trong map không nhờ vậy trở thành thread-safe. Nếu có nhiều writer, phải bảo vệ value, đổi cách lưu hoặc thay cấu trúc sở hữu.

## 4. `ConcurrentModificationException` không phải bộ phát hiện data race

Collection thường có các iterator **fail-fast**: nếu phát hiện sửa đổi cấu trúc ngoài ý iterator trong quá trình duyệt, chúng có thể ném `ConcurrentModificationException`.

Ví dụ **ngay cả một thread** cũng có thể gặp exception:

Lưu thành `FailFastDemo.java`:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FailFastDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>(List.of("A", "B"));
        Iterator<String> iterator = names.iterator();

        names.add("C"); // Sửa cấu trúc ngoài iterator.

        try {
            iterator.next();
            System.out.println("Unexpected");
        } catch (java.util.ConcurrentModificationException e) {
            System.out.println("Detected a structural modification");
        }
    }
}
```

```bash
javac FailFastDemo.java
java FailFastDemo
```

Stdout của ví dụ:

```text
Detected a structural modification
```

Tình huống này diễn ra trong **một thread**, cho thấy exception liên quan đến cách sử dụng iterator, không phải luôn chứng minh có truy cập nhiều thread.

Trong môi trường concurrent:

- Fail-fast **best effort**, không bảo đảm ném exception cho mọi sửa đổi từ thread khác.
- **Không ném exception cũng không chứng minh chương trình thread-safe**.
- Catch `ConcurrentModificationException` để bỏ qua và tiếp tục **không sửa** sự thiếu đồng bộ.
- Nếu cần xóa phần tử khi đang duyệt trong một thread, `Iterator.remove()` (nếu iterator hỗ trợ) là cách hợp lệ hơn việc dùng `list.remove(...)` bên ngoài iterator. Nếu có nhiều thread, vẫn cần chính sách đồng bộ hoặc collection thích hợp.

Không sử dụng exception này như cơ chế điều khiển luồng hoặc cách "thử xem collection có an toàn hay không".

## 5. Ba yếu tố cần bảo đảm: atomicity, visibility và invariant

Khi bảo vệ một collection dùng chung, phải xác định **những gì cần nhất quán**, không chỉ "thêm lock cho mọi chỗ".

**Atomicity:** Một thao tác hoặc một chuỗi thao tác nghiệp vụ có thể được quan sát như một hành động không bị xen ngang tại điểm cần thiết hay không? Ví dụ `if (!set.contains(x)) set.add(x)` là check-then-act: nếu hai thread cùng kiểm tra trước khi thêm, điều kiện "chỉ một thread nhận quyền xử lý x" có thể bị phá, kể cả khi từng thao tác `contains` và `add` riêng lẻ đều an toàn.

**Visibility:** Sau khi thread A cập nhật collection, thread B có cách nhìn thấy cập nhật ấy được Java Memory Model bảo đảm không? Quan hệ happens-before có thể đến từ cùng monitor lock, các thao tác concurrent collection theo hợp đồng, `Thread.start/join`, `Future.get` hoặc cơ chế đồng bộ thích hợp.

**Invariant:** Dữ liệu có quan hệ phải được duy trì tại thời điểm bên ngoài được phép quan sát. Ví dụ một nghiệp vụ phải đảm bảo tổng số phần tử trong hai collection bằng N; khóa riêng từng collection có thể chưa đủ để biến việc chuyển phần tử giữa hai collection thành một giao dịch atomic.

Mục tiêu không phải luôn khóa toàn cục mọi thứ, mà là **xác định ranh giới thao tác logic cần atomic và đường công bố dữ liệu đúng**.

## 6. Cách 1: không chia sẻ collection mutable nếu không cần

Đây là cách thường dễ chứng minh đúng nhất: mỗi task tạo collection của riêng mình và trả về một kết quả hoàn chỉnh.

```java
static List<String> transform(List<String> input) {
    List<String> local = new ArrayList<>();

    for (String value : input) {
        local.add(value.toUpperCase());
    }

    return List.copyOf(local);
}
```

Đây là đoạn minh họa phương thức, yêu cầu import `java.util.List` và `java.util.ArrayList` nếu đặt trong class độc lập.

Ở đây worker là chủ sở hữu `local` trong lúc xây dựng. Kết quả được công bố là một list không cho sửa qua API; với các `String` immutable thì dữ liệu bên trong cũng không bị sửa. **Điều kiện:** `input` phải được dùng theo một giao thức phù hợp; nếu thread khác đồng thời sửa `input` trong lúc transform, vấn đề đầu vào vẫn tồn tại. `List.copyOf` cũng không làm an toàn một bản copy đang đua với writer bên ngoài.

Một dạng thiết kế khác là **single-writer ownership**: nhiều producer gửi công việc/giá trị qua queue, chỉ một consumer sở hữu và cập nhật collection tích lũy. Khi đó collection không cần bảo vệ cho nhiều writer vì chỉ một thread được chạm vào nó. Nếu consumer muốn chia sẻ kết quả, tạo snapshot immutable hoặc công bố bằng cơ chế đồng bộ phù hợp.

## 7. Cách 2: bảo vệ collection bằng cùng một khóa

Nếu bắt buộc nhiều thread cùng sử dụng một `ArrayList` hay `HashMap` và muốn giữ invariant qua nhiều thao tác, cách trực tiếp là bảo vệ tất cả các đường truy cập liên quan bằng **cùng monitor** hoặc **cùng lock**.

Ví dụ Java 17+ chạy được, không phụ thuộc thứ tự worker:

Lưu thành `LockedListDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LockedListDemo {
    private static final class SharedResults {
        private final List<Integer> values = new ArrayList<>();

        synchronized void add(int value) {
            values.add(value);
        }

        synchronized List<Integer> snapshot() {
            return List.copyOf(values);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        SharedResults shared = new SharedResults();

        Runnable work = () -> {
            for (int i = 0; i < 1_000; i++) {
                shared.add(i);
            }
        };

        Thread first = new Thread(work);
        Thread second = new Thread(work);

        first.start();
        second.start();
        first.join();
        second.join();

        List<Integer> result = new ArrayList<>(shared.snapshot());
        Collections.sort(result);

        System.out.println("Size: " + result.size());
        System.out.println("Zeros: " + Collections.frequency(result, 0));
        System.out.println("Last: " + result.get(result.size() - 1));
    }
}
```

```bash
javac LockedListDemo.java
java LockedListDemo
```

Stdout chính xác:

```text
Size: 2000
Zeros: 2
Last: 999
```

Phân tích:

- Cả hai worker gọi `shared.add` nhưng **cùng phải giữ monitor của instance `SharedResults`**. Trong vùng khóa, `ArrayList` chỉ được một thread sửa tại một thời điểm.
- `snapshot()` dùng cùng monitor để copy dữ liệu; không đọc list đang bị thread khác cập nhật. `List.copyOf` trả về list không cho sửa qua API, phù hợp cho kết quả gồm `Integer` immutable.
- `join()` đảm bảo main đợi hai worker hoàn tất. Sắp xếp bản sao sau đó giúp stdout không phụ thuộc lịch chạy xen kẽ.
- Để thật sự giữ quyền kiểm soát, `values` là `private` và không có getter trả trực tiếp mutable list. Nếu để tham chiếu nội bộ thoát ra rồi client tự gọi `add`, client có thể bỏ qua khóa và phá thiết kế.

### Khóa cho từng thao tác chưa chắc bảo vệ phép toán tổng hợp

Ví dụ sau **vẫn có race logic**, dù cả `size()` và `add()` đã được đồng bộ:

```java
if (shared.size() < limit) {
    shared.add(item);
}
```

Hai thread có thể cùng thấy dung lượng chưa đầy rồi cùng thêm. Nếu yêu cầu là "chỉ thêm khi chưa vượt giới hạn", phải đặt cả kiểm tra và thêm **trong cùng critical section**, chẳng hạn cung cấp phương thức `tryAddWithinLimit(item, limit)` được `synchronized`. Một API thread-safe về thao tác đơn lẻ chưa tự làm một chuỗi check-then-act thành atomic.

## 8. Cách 3: dùng `Collections.synchronizedXxx` đúng cách

Các wrapper như:

- `Collections.synchronizedList(new ArrayList<>())`
- `Collections.synchronizedMap(new HashMap<>())`
- `Collections.synchronizedSet(new HashSet<>())`

đồng bộ các thao tác qua wrapper theo hợp đồng. Thích hợp khi cần tiếp tục dùng API collection truyền thống nhưng muốn một mức bảo vệ đơn giản.

**Bẫy quan trọng: duyệt iterator vẫn phải đồng bộ thủ công** trên chính wrapper trong suốt quá trình duyệt. Nếu không, thread khác có thể sửa collection giữa các bước duyệt.

Lưu thành `SynchronizedIterationDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SynchronizedIterationDemo {
    public static void main(String[] args) {
        List<String> items =
                Collections.synchronizedList(new ArrayList<>());

        items.add("Java");
        items.add("Concurrency");

        synchronized (items) {
            for (String item : items) {
                System.out.println(item);
            }
        }
    }
}
```

```bash
javac SynchronizedIterationDemo.java
java SynchronizedIterationDemo
```

Stdout chính xác:

```text
Java
Concurrency
```

Trong bài toán có nhiều thread, tất cả code phải **truy cập qua wrapper**, không giữ và sửa trực tiếp underlying `ArrayList`/`HashMap` sau khi bọc. Khi dùng iterator, spliterator, stream hoặc các chuỗi thao tác cần một góc nhìn nhất quán, phải dựa vào hướng dẫn đồng bộ của wrapper và khóa đúng đối tượng. Không nên giữ khóa khi chạy tác vụ I/O lâu hoặc callback không kiểm soát vì sẽ chặn các thao tác khác.

### Vì sao chỉ bọc `synchronizedList` chưa đủ?

Ví dụ:

```java
List<String> items =
        Collections.synchronizedList(new ArrayList<>());

if (!items.contains("A")) {
    items.add("A");
}
```

Hai lời gọi `contains` và `add` đơn lẻ đều được wrapper bảo vệ, nhưng **giữa hai lần gọi** thread khác có thể thay đổi list. Nếu yêu cầu là "A chỉ xuất hiện một lần", dùng một vùng:

```java
synchronized (items) {
    if (!items.contains("A")) {
        items.add("A");
    }
}
```

hoặc chọn `Set` thích hợp và sử dụng thao tác nguyên tử mà API của cấu trúc concurrent đó cung cấp.

## 9. Cách 4: dùng collection được thiết kế cho concurrency

`java.util.concurrent` cung cấp nhiều cấu trúc với các đặc tính riêng. Chọn theo **bài toán và kiểu thao tác**, không chỉ theo chữ "concurrent".

| Nhu cầu | API thường phù hợp | Điểm cần nhớ |
| --- | --- | --- |
| Map cập nhật và đọc từ nhiều thread | `ConcurrentHashMap<K,V>` | Có API atomic theo key, iterator weakly consistent, không cho khóa/value `null` |
| Set cần thao tác concurrent | `ConcurrentHashMap.newKeySet()` | Dùng cho membership, thêm/xóa an toàn qua API set |
| Queue nhiều producer/consumer, không chờ khi rỗng | `ConcurrentLinkedQueue<E>` | Non-blocking queue, `poll()` có thể trả `null` |
| Queue cần producer/consumer chờ và có thể giới hạn dung lượng | `ArrayBlockingQueue<E>` | `put` chờ khi đầy, `take` chờ khi rỗng |
| Queue chờ với cấu hình dung lượng | `LinkedBlockingQueue<E>` | Nên cân nhắc capacity hữu hạn, tránh tăng hàng đợi không kiểm soát |
| Danh sách chủ yếu đọc, ghi hiếm | `CopyOnWriteArrayList<E>` | Iterator snapshot, mỗi mutation thường phải sao chép mảng; ghi nhiều sẽ đắt |
| Map cần duy trì thứ tự khóa có concurrent access | `ConcurrentSkipListMap<K,V>` | Cấu trúc sorted concurrent, chi phí khác `ConcurrentHashMap` |
| Set cần duy trì thứ tự | `ConcurrentSkipListSet<E>` | Sorted set concurrent, không phải `HashSet` |
| Tích lũy số liệu với contention cao | `LongAdder` hoặc `LongAccumulator` | Không phải collection; hỗ trợ thống kê nhưng `sum()` dưới concurrent updates không là snapshot atomic |

Không có một collection duy nhất thay thế tối ưu mọi trường hợp. Cần cân nhắc thứ tự dữ liệu, chi phí ghi, backpressure, memory footprint và tính atomic của thao tác nghiệp vụ.

### `ConcurrentHashMap`: dùng `merge` cho cập nhật theo key

Một race phổ biến là:

```java
Integer old = map.get(key);
map.put(key, old == null ? 1 : old + 1);
```

Ngay cả khi `map` là `ConcurrentHashMap`, **chuỗi get rồi put không tự atomic**: hai worker có thể cùng lấy số cũ rồi ghi cùng số mới.

Hãy dùng API update atomic theo key, ví dụ `merge`. Lưu thành `ConcurrentMapCounterDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentMapCounterDemo {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, Integer> counts =
                new ConcurrentHashMap<>();

        Runnable work = () -> {
            for (int i = 0; i < 10_000; i++) {
                counts.merge("done", 1, Integer::sum);
            }
        };

        Thread first = new Thread(work);
        Thread second = new Thread(work);

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("Done: " + counts.get("done"));
    }
}
```

```bash
javac ConcurrentMapCounterDemo.java
java ConcurrentMapCounterDemo
```

Stdout chính xác:

```text
Done: 20000
```

`merge("done", 1, Integer::sum)` cập nhật giá trị **theo key** bằng một operation của `ConcurrentHashMap`, tránh lost update của cặp `get/put` trong ví dụ. Hai `join()` đảm bảo việc đọc số cuối xảy ra sau khi cả hai worker hoàn tất.

**Lưu ý:** hàm truyền vào `compute/merge` nên ngắn, rõ ràng và không gọi các cập nhật map khác theo kiểu có thể gây tái nhập hoặc phụ thuộc chéo. Hợp đồng mỗi phương thức quyết định loại bảo đảm; đừng suy từ "map thread-safe" rằng toàn bộ giao dịch nhiều key hoặc thao tác trên mutable value cũng atomic.

### Trường hợp set unique

Thay cho chuỗi `if (!set.contains(id)) set.add(id)` để quyết định "chỉ một worker xử lý id", dùng kết quả của chính thao tác `add` trên một set concurrent:

```java
Set<String> seen = ConcurrentHashMap.newKeySet();

if (seen.add(id)) {
    // Chỉ thread có lần add thành công cho id mới xử lý nhánh này.
}
```

Đây là đoạn minh họa: cần import `java.util.Set`, `java.util.concurrent.ConcurrentHashMap` và cung cấp `id`. Điều kiện `add` là một bước atomic theo hợp đồng của set concurrent, nhưng logic "chỉ chạy business action một lần trong toàn hệ thống" còn phụ thuộc việc lỗi, retry và trạng thái bên ngoài được thiết kế ra sao.

## 10. Queue cho nhiều producer: chuyển quyền thay vì sửa chung một list

Khi nhiều thread tạo kết quả và một thread tổng hợp, thay vì cho tất cả thread `list.add(...)` vào cùng một `ArrayList`, có thể dùng **producer–consumer**:

```text
Producer A --+
             +--> BlockingQueue --> Consumer sở hữu ArrayList tổng hợp
Producer B --+                          |
                                        +--> kết quả cuối sau khi hoàn tất
```

Producer chỉ gửi message; consumer duy nhất quản lý list. `BlockingQueue` đảm nhiệm đồng bộ và chuyển giao dữ liệu giữa thread. Dùng queue có capacity hữu hạn giúp **backpressure** khi producer nhanh hơn consumer.

Ví dụ độc lập Java 17+; hai producer gửi các số và một consumer tính tổng. Mỗi producer gửi đúng 1.000 phần tử, caller dùng `Future.get()` đợi cả hai producer hoàn thành rồi đưa sentinel `-1` để consumer dừng.

Lưu thành `ProducerConsumerDemo.java`:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ProducerConsumerDemo {
    public static void main(String[] args) throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(32);
        ExecutorService pool = Executors.newFixedThreadPool(3);

        try {
            Future<?> first = pool.submit(() -> {
                for (int i = 0; i < 1_000; i++) {
                    queue.put(1);
                }
                return null;
            });

            Future<?> second = pool.submit(() -> {
                for (int i = 0; i < 1_000; i++) {
                    queue.put(1);
                }
                return null;
            });

            Future<Integer> consumer = pool.submit(() -> {
                int sum = 0;

                while (true) {
                    int item = queue.take();
                    if (item == -1) {
                        return sum;
                    }
                    sum += item;
                }
            });

            first.get();
            second.get();
            queue.put(-1); // Chỉ gửi sau khi cả hai producer xong.

            System.out.println("Total: " + consumer.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

```bash
javac ProducerConsumerDemo.java
java ProducerConsumerDemo
```

Stdout chính xác trên đường chạy thành công:

```text
Total: 2000
```

Hai producer có thể cùng cập nhật **queue được thiết kế thread-safe**, còn biến `sum` chỉ thuộc consumer, nên không cần mutex quanh `sum`. `Future.get()` xác nhận cả hai producer hoàn thành thành công trước khi main đặt sentinel. Queue FIFO bảo đảm sentinel được lấy sau các phần tử đã `put` thành công trước đó; ví dụ có đúng một consumer và dữ liệu chỉ là số `1` nên `-1` không trùng dữ liệu.

Đây là mô hình thành công đơn giản. Trong ứng dụng thực, nếu một producer thất bại trong lúc consumer đang `take()`, code điều phối phải có giao thức xử lý lỗi/hủy và đánh thức consumer phù hợp; `shutdownNow()` chỉ yêu cầu interruption, không phải biện pháp cưỡng bức dừng mọi thao tác. Không nên copy nguyên mẫu sentinel này sang hệ thống nhiều consumer mà không sửa giao thức kết thúc.

### Queue không tự khiến object bên trong immutable

Nếu producer gửi một `ArrayList` rồi **tiếp tục sửa cùng instance** trong khi consumer đọc, data race vẫn tồn tại. Quy tắc an toàn là **chuyển quyền sở hữu** cho consumer và producer ngừng sử dụng object, hoặc gửi một bản sao bất biến như `List.copyOf(...)` (sau khi việc tạo snapshot được bảo vệ phù hợp).

## 11. `CopyOnWriteArrayList`: an toàn khi chủ yếu đọc

`CopyOnWriteArrayList` thích hợp cho cấu hình dạng tập listeners hoặc danh sách nhỏ được đọc/duyệt thường xuyên, cập nhật hiếm. Mỗi thay đổi cấu trúc thường tạo mảng lưu trữ mới, còn iterator giữ **ảnh chụp tại thời điểm tạo iterator**.

Lưu thành `CopyOnWriteDemo.java`:

```java
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class CopyOnWriteDemo {
    public static void main(String[] args) {
        CopyOnWriteArrayList<String> listeners =
                new CopyOnWriteArrayList<>();

        listeners.add("A");
        listeners.add("B");

        Iterator<String> snapshot = listeners.iterator();
        listeners.add("C");

        while (snapshot.hasNext()) {
            System.out.println("Snapshot: " + snapshot.next());
        }

        System.out.println("Current size: " + listeners.size());
    }
}
```

```bash
javac CopyOnWriteDemo.java
java CopyOnWriteDemo
```

Stdout:

```text
Snapshot: A
Snapshot: B
Current size: 3
```

Đây là hành vi snapshot được API thiết kế, không phải lỗi mất dữ liệu. Iterator của `CopyOnWriteArrayList` không dùng cơ chế fail-fast theo cách iterator của `ArrayList` làm và không hỗ trợ `remove()` trên iterator. Người dùng cần hiểu **snapshot không thấy các thay đổi xảy ra sau khi tạo iterator**.

Không nên dùng `CopyOnWriteArrayList` cho workload ghi rất nhiều hoặc danh sách lớn bị thay đổi liên tục, vì chi phí sao chép và cấp phát mảng có thể cao. Nó cũng không biến đối tượng mutable được lưu làm phần tử thành immutable.

## 12. Immutable collection, unmodifiable view và safe publication

Ba khái niệm rất dễ bị nhầm:

1. **Mutable collection:** nội dung có thể thay đổi qua API (ví dụ `ArrayList`).
2. **Unmodifiable view:** caller không sửa qua view được, **nhưng underlying collection có thể vẫn đổi**.
3. **Immutable snapshot:** bản sao không cho phép sửa cấu trúc qua API và tách khỏi những thay đổi sau này của collection đầu vào; phần tử mutable vẫn là trách nhiệm riêng.

Xem ví dụ Java 17+:

Lưu thành `SnapshotVsViewDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SnapshotVsViewDemo {
    public static void main(String[] args) {
        List<String> mutable = new ArrayList<>(List.of("A"));

        List<String> view = Collections.unmodifiableList(mutable);
        List<String> snapshot = List.copyOf(mutable);

        mutable.add("B");

        System.out.println("View: " + view);
        System.out.println("Snapshot: " + snapshot);
    }
}
```

```bash
javac SnapshotVsViewDemo.java
java SnapshotVsViewDemo
```

Stdout chính xác:

```text
View: [A, B]
Snapshot: [A]
```

`Collections.unmodifiableList(mutable)` là **view**, nên nó phản ánh thay đổi được thực hiện qua `mutable`. `List.copyOf(mutable)` tạo một list unmodifiable không phản ánh lần `add` sau trong trường hợp đầu vào mutable như ví dụ.

Nhưng chú ý:

- `List.copyOf` **không hợp thức hóa việc copy trong lúc thread khác đang sửa list không an toàn**. Cần ngừng writer, giữ lock khi chụp, hoặc dùng protocol publication phù hợp.
- Collection unmodifiable có thể chứa **phần tử mutable**: nếu là `List<Account>` và các `Account` vẫn bị sửa, không thể gọi toàn bộ object graph là deep-immutable.
- `List.of(...)` và `List.copyOf(...)` từ chối phần tử `null`; `List.copyOf` có thể tái sử dụng một instance unmodifiable phù hợp, nên không được dựa vào identity của snapshot.
- Sau khi xây xong dữ liệu, vẫn cần **safe publication** để thread khác nhìn thấy trạng thái đầy đủ theo Java Memory Model, ví dụ qua lời gọi `Thread.start()` đúng thứ tự, queue đồng bộ, volatile reference hoặc monitor lock.

Thay một tham chiếu trỏ tới **immutable snapshot** đôi khi đơn giản hơn cố đồng bộ từng lần đọc và ghi nhiều field trong một mutable collection.

## 13. Parallel streams: tránh side effect trên collection dùng chung

Một lỗi phổ biến trong Java là viết:

```java
List<Integer> output = new ArrayList<>();

IntStream.range(0, 100_000)
        .parallel()
        .forEach(output::add);
```

Đây là **anti-pattern** khi `output` là `ArrayList` dùng chung. `parallel()` có thể khiến nhiều worker cùng gọi `add`, trong khi `ArrayList` không an toàn với nhiều writer. Thứ tự phần tử không được bảo đảm, và chương trình còn có nguy cơ thiếu phần tử hoặc lỗi.

Một cách đúng là dùng phép **collect** được thư viện hỗ trợ thay vì sửa side effect vào list chung. Lưu thành `ParallelCollectDemo.java`:

```java
import java.util.List;
import java.util.stream.IntStream;

public class ParallelCollectDemo {
    public static void main(String[] args) {
        List<Integer> result = IntStream.range(0, 10_000)
                .parallel()
                .boxed()
                .toList();

        long sum = result.stream()
                .mapToLong(Integer::longValue)
                .sum();

        System.out.println("Size: " + result.size());
        System.out.println("First: " + result.get(0));
        System.out.println("Sum: " + sum);
    }
}
```

```bash
javac ParallelCollectDemo.java
java ParallelCollectDemo
```

Stdout chính xác:

```text
Size: 10000
First: 0
Sum: 49995000
```

`IntStream.range` có encounter order, và `Stream.toList()` giữ encounter order khi có, kể cả pipeline có `parallel()`. Kết quả `toList()` là list unmodifiable. Bản thân pipeline chịu trách nhiệm thu thập đúng, không yêu cầu người viết tự gọi `add` từ nhiều thread vào cùng `ArrayList`.

**Đừng suy diễn rằng tất cả thao tác stream đều tự bảo vệ side effect.** Nếu callback `map/forEach/peek` vẫn sửa một object ngoài pipeline, thread safety của object đó vẫn phải được xử lý. Muốn tổng hợp theo key dưới concurrency, cân nhắc `Collectors.groupingByConcurrent` theo yêu cầu về thứ tự và kiểu kết quả; đọc hợp đồng collector thay vì tự tạo map và cập nhật không đồng bộ.

## 14. Trường hợp read-only: nhiều thread đọc có an toàn không?

Nếu collection được **khởi tạo hoàn chỉnh trước khi công bố**, sau đó không còn thread nào thay đổi cấu trúc hoặc nội dung được đọc, nhiều thread đọc song song thường không cần lock quanh mỗi lần đọc.

Tuy nhiên, phải đồng thời đáp ứng:

- Không có writer concurrent (kể cả writer "ẩn" giữ alias tới cùng instance).
- Tham chiếu được chuyển tới reader bằng cơ chế **safe publication** theo Java Memory Model. Chỉ gán tham chiếu vào một field ordinary trong lúc các thread khác đang chạy, rồi hy vọng các reader tự thấy ngay, không phải giao thức publication đầy đủ.
- Những value hoặc phần tử mutable bên trong không bị thay đổi theo cách xung đột khi reader đang dùng.
- Không giả định một **unmodifiable view** đồng nghĩa underlying collection bất biến.

Vì vậy, "một collection không có ai gọi `add` lúc này" khác với "một snapshot immutable được công bố đúng". Hãy chọn thiết kế dễ giải thích ownership và publication.

## 15. Những thao tác tưởng an toàn nhưng không atomic ở cấp nghiệp vụ

| Mẫu code | Vấn đề | Hướng xử lý |
| --- | --- | --- |
| `if (!list.contains(x)) list.add(x)` | Check và add có khoảng xen kẽ | Giữ chung một lock hoặc dùng set concurrent với `add` atomic |
| `map.put(key, map.get(key) + 1)` | Hai worker có thể ghi đè increment | `ConcurrentHashMap.merge/compute` hoặc khóa |
| `if (queue.size() < limit) queue.add(x)` | Kiểm tra dung lượng và thêm không là một giao dịch | Queue bounded với `offer/put` phù hợp |
| `for (x : synchronizedList) ...` không khóa | Iterator có thể race với writer | `synchronized(list)` trong suốt iteration hoặc dùng snapshot |
| `map.get(key).add(value)` khi value là `ArrayList` | Map thread-safe không bảo vệ mutable value | Bảo vệ value, dùng immutable replacement hoặc owner duy nhất |
| `if (map.isEmpty()) launchCleanup()` | Trạng thái có thể đổi ngay sau kiểm tra | Giao thức đồng bộ hóa trạng thái/điểm đóng thích hợp |
| `collection.size()` được đọc trong lúc nhiều writer chạy | Không nhất thiết là snapshot nghiệp vụ | Đợi worker hoàn tất hoặc khóa/thiết kế snapshot phù hợp |

**Thread-safe ở mức method không đồng nghĩa thread-safe ở mức workflow.** Nếu yêu cầu liên quan nhiều bước và nhiều collection, hãy xác định ranh giới atomicity và phương thức phối hợp phù hợp, thay vì chỉ thay `HashMap` bằng `ConcurrentHashMap` rồi cho rằng mọi invariant đã được bảo vệ.

## 16. Lỗi thiết kế thường gặp

### 16.1. Dùng `volatile List<T>` là đủ

`volatile` trên tham chiếu list giúp về visibility/ordering đối với thao tác đọc-ghi chính **tham chiếu** đó. Nó **không biến các thao tác `add/remove` bên trong `ArrayList` thành atomic**. Nếu thiết kế dùng immutable snapshot và thay mới cả tham chiếu qua `volatile`, đó là một phương án khác với việc nhiều worker cùng sửa list hiện tại.

### 16.2. Gọi `Collections.synchronizedList` rồi chuyển tiếp raw list

Nếu nơi khác còn giữ tham chiếu tới underlying `ArrayList` và tự sửa nó, các phép truy cập bên ngoài wrapper không tuân theo khóa của wrapper. Cần kiểm soát alias, encapsulation và chỉ công bố API đã bảo vệ.

### 16.3. Bọc collection nhưng trả iterator ra ngoài lock

Tạo iterator dưới lock rồi duyệt **sau khi đã nhả lock** chưa làm cả quá trình duyệt atomic. Với synchronized wrapper, phải giữ đúng monitor trong quá trình traversal khi cần loại trừ writer. Một giải pháp khác là copy snapshot dưới lock rồi xử lý snapshot bên ngoài.

### 16.4. Sử dụng `ConcurrentModificationException` làm bằng chứng duy nhất

Không có exception không chứng minh thread safety. Có exception không nhất thiết nghĩa là có nhiều thread; một thread sửa trực tiếp list khi đang duyệt cũng đủ.

### 16.5. Tin rằng `ConcurrentHashMap` làm an toàn mọi value

Value mutable cần chính sách riêng. Một `ArrayList` được lấy từ concurrent map vẫn là `ArrayList` thông thường.

### 16.6. Dùng `CopyOnWriteArrayList` cho đường ghi rất nóng

Sao chép mảng mỗi lần mutation khiến workload ghi nhiều có thể tốn thời gian và bộ nhớ. Nó thích hợp cho đọc nhiều/ghi ít, không phải mọi bài toán.

### 16.7. Đợi bằng `Thread.sleep` rồi đọc collection

Sleep không bảo đảm worker đã xong hoặc các write đã được công bố theo yêu cầu. Dùng `join()`, `Future.get()`, latch, queue hoặc phương thức đồng bộ thích hợp.

### 16.8. Biến chung qua static field

Một collection `static` có thể được truy cập từ nhiều phần chương trình và khó kiểm soát ownership. `private static final List<?>` chỉ ngăn thay tham chiếu; không ngăn sửa list. Cân nhắc giới hạn scope, dùng dependency cụ thể và trao immutable snapshot thay vì trạng thái mutable toàn cục.

### 16.9. Không giới hạn queue và công việc đang chờ

Một queue concurrent không blocking có thể không có capacity cố định. Nếu producer chạy nhanh hơn consumer, backlog tăng có thể dẫn đến tiêu thụ bộ nhớ cao. Thread safety chỉ nói về tính đúng của truy cập đồng thời, không bảo đảm quản lý tải hoặc tài nguyên.

### 16.10. Nhầm kiểm thử stress với chứng minh đúng

Chạy 10.000 lần mà chưa thấy lỗi không chứng minh không có race. Dùng đúng hợp đồng API, kiểm tra invariants và quan hệ happens-before. Stress test vẫn hữu ích để phát hiện bug, nhưng không thay thế lý luận về thread safety.

## 17. Hướng dẫn chọn giải pháp cho tình huống thực tế

| Bối cảnh | Thiết kế nên cân nhắc | Vì sao |
| --- | --- | --- |
| Mỗi tác vụ tự xây danh sách kết quả | Local `ArrayList` rồi trả snapshot | Không có shared mutable state trong lúc xây |
| Cần đọc một cấu hình hiếm khi cập nhật | Immutable snapshot và safe publication | Reader không phải tranh lock ở mỗi lần đọc |
| Nhiều worker cập nhật một map theo key | `ConcurrentHashMap.compute/merge` | Hợp đồng hỗ trợ cập nhật atomic theo key |
| Nhiều worker gửi item, một worker tổng hợp | `BlockingQueue` và consumer sở hữu state | Tách producer khỏi mutable accumulator, có thể tạo backpressure |
| Duyệt danh sách listeners thường xuyên, sửa ít | `CopyOnWriteArrayList` | Iterator snapshot, phù hợp tỷ lệ đọc cao |
| Cần duy trì invariant qua hai collection | Một lock bảo vệ toàn bộ transaction hoặc giao thức owner | Chỉ concurrent riêng từng collection chưa đủ |
| Cần danh sách thường nhưng muốn khóa đơn giản | Encapsulated `synchronized` hoặc synchronized wrapper đúng cách | Dễ kiểm soát quyền truy cập nếu discipline thống nhất |
| Cần ordered map concurrent | `ConcurrentSkipListMap` | Có ordering và bảo đảm concurrent theo hợp đồng |

**Câu hỏi quyết định** không phải chỉ "chọn lớp nào", mà là:

1. Bao nhiêu thread thực sự đọc hoặc sửa **cùng một instance**?
2. Có thể tránh chia sẻ mutable data, dùng immutable snapshot hoặc owner duy nhất không?
3. Invariant nghiệp vụ thuộc một thao tác hay nhiều thao tác?
4. API dự kiến có bảo đảm atomic đúng ở ranh giới đó không?
5. Consumer và producer có giao thức hoàn tất, lỗi, timeout/backpressure không?
6. Nếu nhiều thread cùng truy cập, quan hệ happens-before nào bảo đảm visibility?
7. Các value/element mutable có được bảo vệ riêng không?
8. Chọn giải pháp này có phù hợp tỷ lệ đọc/ghi và dung lượng dữ liệu không?

## 18. Tự kiểm tra kiến thức

**Câu 1:** Hai worker đều gọi `list.add(x)` trên cùng một `ArrayList`; có nên dựa vào `list.size()` bằng tổng số lần thêm không?

**Trả lời:** Không nếu không có đồng bộ. Các thao tác cập nhật cấu trúc của `ArrayList` không được bảo đảm an toàn với nhiều writer. Cần một owner, khóa hoặc collection concurrent phù hợp.

**Câu 2:** Nếu dùng `Collections.synchronizedList`, một vòng `for-each` trên list đã đủ an toàn chưa?

**Trả lời:** Chưa chắc. Cần giữ đúng monitor của wrapper trong toàn bộ traversal khi có writer concurrent, hoặc duyệt một snapshot được tạo an toàn.

**Câu 3:** `ConcurrentHashMap` có giải quyết phép `get` rồi `put` để tăng counter atomic không?

**Trả lời:** Không. Dùng `merge`/`compute` theo key, một counter atomic được thiết kế phù hợp hoặc một vùng khóa bao trọn.

**Câu 4:** `List.copyOf` có bảo đảm deep-immutable không?

**Trả lời:** Không. Nó tạo list không sửa được qua API và tách khỏi thay đổi cấu trúc tiếp theo của input mutable trong điều kiện copy hợp lệ, nhưng các phần tử mutable vẫn có thể thay đổi.

**Câu 5:** Có thể kết luận "không có data race" vì không thấy `ConcurrentModificationException` không?

**Trả lời:** Không. Fail-fast có tính best effort, có thể không ném exception. Sự vắng mặt của exception không chứng minh thread safety.

**Câu 6:** Nếu nhiều producer gửi công việc tới một consumer duy nhất, tại sao consumer có thể dùng `ArrayList` thường?

**Trả lời:** Vì consumer là owner duy nhất và không có thread khác sửa/đọc list đó trong lúc mutation. Producer chỉ giao tiếp qua queue an toàn; khi công bố kết quả cho thread khác cần snapshot/publication đúng cách.

**Câu 7:** Một `ConcurrentHashMap<String, ArrayList<Event>>` có an toàn nếu nhiều worker sửa cùng value không?

**Trả lời:** Không tự động. Concurrent map bảo vệ thao tác map; từng value là `ArrayList` thường cần giao thức đồng bộ, immutable replacement hoặc thay đổi ownership.

## 19. Điều cần ghi nhớ

- `ArrayList`, `HashMap`, `HashSet` và nhiều collection thông thường **không tự thread-safe cho truy cập concurrent có mutation**.
- Một race có thể làm sai dữ liệu mà không báo exception; `ConcurrentModificationException` **không** phải bộ xác nhận thread safety.
- `synchronized` hoặc một lock thống nhất có thể bảo vệ **cả chuỗi thao tác** và invariant; synchronized wrapper vẫn cần khóa khi iteration.
- `ConcurrentHashMap`, `BlockingQueue` và `CopyOnWriteArrayList` giải quyết **các nhu cầu khác nhau**; đọc kỹ hợp đồng atomicity, visibility, snapshot và chi phí của từng API.
- **Immutable snapshot**, task-local collection và **single-writer ownership** thường giảm sự phức tạp tốt hơn chia sẻ mutable state tự do.
- Kiểm soát alias của collection, mutable element, safe publication, lifecycle và backpressure cũng là một phần của tính đúng đắn.

Khi thiết kế xử lý đa luồng trong Java, hãy bắt đầu bằng câu hỏi **"Ai sở hữu và có quyền thay đổi collection này?"**. Nếu câu trả lời là nhiều thread, cần chỉ rõ giao thức đồng bộ hoặc chọn đúng concurrent collection **và đúng thao tác atomic**, thay vì mặc định một collection thường sẽ tự xử lý cạnh tranh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="race-conditions-009">Phân biệt data race, race condition và logic race</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt data race, race condition và logic race trong Java

## 1. Mục tiêu và phạm vi

Sau bài học, bạn có thể:

- Phân biệt **data race** theo quan hệ *happens-before* trong Java Memory Model (JMM), **race condition** theo tính đúng đắn của hành vi, và **logic race** ở cấp thao tác hoặc quy tắc nghiệp vụ.
- Giải thích vì sao có race condition **ngay cả khi không có data race**, cũng như vì sao data race có thể chưa biểu hiện thành lỗi trong một lần chạy.
- Nhận ra các mẫu lỗi **read–modify–write**, **check–then–act** và **time-of-check to time-of-use (TOCTOU)**.
- Chọn đúng cơ chế Java như **synchronized**, **AtomicInteger.compareAndSet()**, cấu trúc concurrent, hoặc giao thức trao tín hiệu để bảo vệ một *bất biến* (invariant).
- Tạo ví dụ có lịch xen kẽ được kiểm soát để tái hiện lỗi, hiểu giới hạn của stress test, và kiểm tra kết quả bằng điều kiện nghiệp vụ thay vì chỉ nhìn thứ tự log.

Phạm vi là **Java concurrency nói chung** ở mức JDK và JMM. Những cơ chế riêng của Spring thuộc chương khác.

## 2. Ba thuật ngữ có phạm vi khác nhau

### 2.1. Data race: các truy cập xung đột thiếu thứ tự happens-before

Trong thuật ngữ JMM, một **data race** xuất hiện khi có **hai truy cập xung đột** tới cùng một biến, do các thread khác nhau thực hiện, **ít nhất một truy cập là ghi**, và **không có quan hệ happens-before** sắp thứ tự giữa hai truy cập đó.

Ví dụ kinh điển là nhiều thread cùng thao tác một trường **int** thông thường mà không có cơ chế đồng bộ hóa thích hợp.

~~~java
// Đoạn minh họa, không phải chương trình độc lập:
int count = 0;
// Thread A và thread B cùng thực hiện:
count++; // Đọc, tính toán, rồi ghi; không phải phép tăng nguyên tử.
~~~

Kết quả có thể mất cập nhật (*lost update*). Một lịch thực thi có thể là:

~~~text
Ban đầu count = 0
Thread A: đọc count -> 0
Thread B: đọc count -> 0
Thread A: tính 0 + 1 -> 1
Thread B: tính 0 + 1 -> 1
Thread A: ghi count = 1
Thread B: ghi count = 1
Cuối cùng count = 1 dù đã yêu cầu tăng hai lần.
~~~

Đây là lịch minh họa lỗi; thread có thể xen kẽ theo các cách khác nhau. Hậu quả không chỉ là mất cập nhật: đọc dữ liệu cũ, vi phạm bất biến giữa các trường và hành vi khó dự đoán theo JMM cũng có thể xuất hiện.

**Data race là khái niệm về truy cập bộ nhớ và đồng bộ hóa**, không phải nhãn kết luận nghiệp vụ đã thất bại. Có data race trong chương trình là dấu hiệu thiết kế nguy hiểm; lỗi có thể không bộc lộ trong một lần thử.

### 2.2. Race condition: kết quả đúng hay sai phụ thuộc cách xen kẽ

**Race condition** là tình huống mà hành vi quan trọng của chương trình phụ thuộc vào thứ tự/thời điểm các hoạt động xảy ra, nhưng chương trình **không thực thi ràng buộc thứ tự hoặc điều kiện nguyên tử cần thiết**. Khi một lịch xen kẽ hợp lệ dẫn tới vi phạm yêu cầu, đó là race condition gây lỗi.

Ví dụ:

- Hai worker cùng xử lý một job cần **chỉ xử lý một lần**.
- Hai người cùng giữ chỗ cuối cùng dù kho chỉ còn một.
- Một thread kiểm tra tài nguyên tồn tại, nhưng thread khác thay đổi tài nguyên trước lúc sử dụng.
- Hai giao dịch cùng đọc số dư, mỗi giao dịch tự cho rằng vẫn đủ tiền.

**Race condition rộng hơn data race.** Nó có thể liên quan bộ nhớ chia sẻ thiếu đồng bộ, nhưng cũng có thể xảy ra với những thao tác riêng lẻ đều thread-safe, thậm chí giữa các tiến trình hoặc máy khác nhau.

Không nên gọi mọi thứ tự bất định là lỗi. Ví dụ hai worker chỉ in hai dòng log không phụ thuộc nhau, rồi cùng hoàn thành, có thể cho hai thứ tự hợp lệ mà vẫn đúng đặc tả.

### 2.3. Logic race: đứt gãy ở quy tắc phối hợp nhiều bước

**Logic race** là cách gọi thường dùng cho race condition ở **logic ứng dụng**: mỗi thao tác nhỏ có thể an toàn, nhưng **chuỗi thao tác** không được bảo vệ như một quyết định thống nhất.

Mẫu điển hình:

~~~text
1. Kiểm tra điều kiện P có đúng không?
2. Một thread khác thay đổi trạng thái liên quan.
3. Tiếp tục hành động dựa trên kết quả kiểm tra cũ.
~~~

Ví dụ: **stock.get()** và **stock.decrementAndGet()** đều là thao tác thread-safe của **AtomicInteger**, nhưng **“nếu còn hàng thì trừ đúng một đơn vị”** không tự trở thành một giao dịch nguyên tử khi tách thành hai lời gọi.

**Lưu ý thuật ngữ:** *Data race* có định nghĩa kỹ thuật trong JMM. *Race condition* là khái niệm rộng về tính đúng đắn. *Logic race* không phải một loại enum hay thuật ngữ chuẩn được JMM định nghĩa độc lập; ở bài này nó chỉ các race condition làm sai **bất biến và quy trình nhiều bước**. Tên gọi có thể thay đổi giữa các tài liệu, nhưng mẫu lỗi và cách sửa vẫn vậy.

## 3. So sánh ngắn và quan hệ giữa chúng

| Góc nhìn | Data race | Race condition | Logic race |
| --- | --- | --- | --- |
| Điều đang xét | Truy cập cùng biến thiếu thứ tự happens-before | Kết quả phụ thuộc lịch thực thi và có thể vi phạm yêu cầu | Quy tắc nhiều bước bị xen kẽ làm sai |
| Định nghĩa | Có cơ sở hình thức trong JMM | Phụ thuộc đặc tả và yêu cầu đúng đắn | Một cách gọi thực hành của race condition |
| Bắt buộc có dữ liệu chung bị đọc/ghi không an toàn? | Có truy cập xung đột không được HB sắp thứ tự | Không | Không; có thể dùng toàn API atomic/thread-safe |
| Phát hiện từ code? | Xem read/write, synchronization và happens-before | Xem bất biến, hợp đồng nghiệp vụ, lịch xen kẽ | Xem check–then–act và transition |
| Ví dụ | Ghi chung trường int không đồng bộ | Hai đơn cùng nhận suất cuối cùng | Kiểm tra stock.get() rồi decrementAndGet() |
| Cách xử lý | Tạo đồng bộ hóa/atomicity/ownership đúng | Chốt tính nguyên tử, thứ tự hoặc giao thức theo đặc tả | Gộp quyết định thành thao tác nguyên tử/transaction |

Quan hệ thường gặp:

~~~text
               RACE CONDITIONS
              /               \
     Có data race         Không có data race
      (truy cập bộ nhớ)    (ví dụ logic check-then-act
                            trên các biến atomic)
                        \
                         LOGIC RACE là tên gọi
                         cho lỗi quy tắc nhiều bước
~~~

Đừng hiểu sơ đồ này như các tập hợp loại trừ nhau: **một logic race có thể đồng thời chứa data race** nếu cả thao tác lẫn dữ liệu chung đều thiếu đồng bộ. Ngược lại, một data race không nhất thiết tạo ra vi phạm nghiệp vụ quan sát được ở mọi lịch thực thi.

### Ba tình huống để tự phân loại

| Tình huống | Nhận xét |
| --- | --- |
| Hai thread thực hiện **plainCounter++** không đồng bộ để cộng tổng chính xác | Có data race và có race condition gây mất cập nhật |
| Hai thread dùng **AtomicInteger.get()** rồi **decrementAndGet()** nếu còn hàng | Không thiếu an toàn ở từng thao tác atomic, nhưng có logic race và race condition |
| Hai thread độc lập cùng log “finished”, thứ tự dòng đổi qua các lần chạy | Thứ tự bất định; không phải bug nếu đặc tả không yêu cầu thứ tự |

## 4. Nền tảng JMM: visibility, atomicity và ordering

Một chương trình concurrency phải phân biệt ba câu hỏi:

1. **Visibility**: một thread có nhìn thấy cập nhật từ thread khác không?
2. **Atomicity**: thao tác nhiều bước có bị thread khác chen vào phần giữa không?
3. **Ordering**: những thao tác nào phải xảy ra trước những thao tác khác?

JMM dùng quan hệ **happens-before** để định nghĩa các bảo đảm nhìn thấy bộ nhớ và thứ tự. Những quy tắc thường dùng:

- Các thao tác trước **Thread.start()** trong thread tạo có happens-before tới thao tác trong thread được khởi động.
- Các hành động của worker có happens-before các hành động của thread khác sau khi **join()** trả về thành công vì worker đã kết thúc.
- **Unlock** một monitor có happens-before với **lock** tiếp theo trên cùng monitor.
- Ghi vào biến **volatile** có happens-before với lần đọc volatile tiếp theo tương ứng trong synchronization order.
- Các thao tác của **AtomicInteger**, **AtomicReference** có hợp đồng bộ nhớ và tính nguyên tử tương ứng; **compareAndSet()** còn kiểm tra và thay đổi trong một thao tác có điều kiện.
- Các tiện ích concurrent như **CountDownLatch** và **BlockingQueue** cung cấp quan hệ bộ nhớ được quy định trong hợp đồng API khi dùng đúng.

**Thread.sleep()**, **Thread.yield()** và thứ tự xuất hiện của log không tạo happens-before cho dữ liệu chia sẻ. Tăng thời gian ngủ chỉ làm lỗi ít/tăng khả năng xuất hiện theo lịch chạy, không chứng minh chương trình đã đúng.

### volatile giúp gì và không giúp gì?

~~~java
// Đoạn minh họa, không phải chương trình độc lập:
volatile boolean stopRequested = false;
volatile int counter = 0;
~~~

- **stopRequested** có thể dùng làm cờ yêu cầu dừng đơn giản nếu mọi đọc/ghi có liên quan đều theo giao thức đã thiết kế.
- Tuy nhiên, **counter++** vẫn gồm đọc, cộng và ghi; khai báo **volatile** không biến ba bước đó thành một phép tăng nguyên tử.
- Nếu cần cộng chính xác dưới cạnh tranh, dùng **AtomicInteger.incrementAndGet()**, **LongAdder** khi phù hợp, hoặc khóa bảo vệ trạng thái.

**Không có data race** là điều kiện quan trọng cho lập luận dựa trên JMM, nhưng vẫn **chưa đủ** để kết luận bất biến nghiệp vụ đúng. Ta còn phải xét tính nguyên tử của **cả chuỗi quyết định**.

## 5. Ví dụ Java 17+: tái hiện data race có kiểm soát

Thử chạy vòng lặp **counter++** hàng triệu lần là cách hay nhìn thấy lost update, nhưng đôi khi kết quả tình cờ vẫn đúng. Để minh họa một lịch lỗi có kết quả xác định, ta sử dụng **CountDownLatch** chỉ nhằm buộc cả hai thread **đọc giá trị cũ trước khi ghi**.

Tệp **DataRaceDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class DataRaceDemo {
    static class Counter {
        int value = 0; // Trạng thái chung không được bảo vệ.
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch releaseWrites = new CountDownLatch(1);

        Runnable increment = () -> {
            int next = counter.value + 1;
            bothRead.countDown();

            try {
                releaseWrites.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }

            counter.value = next; // Hai ghi xung đột, không có thứ tự HB.
        };

        Thread a = new Thread(increment, "counter-a");
        Thread b = new Thread(increment, "counter-b");
        a.start();
        b.start();

        bothRead.await();
        releaseWrites.countDown();

        a.join();
        b.join();
        System.out.println("counter = " + counter.value);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 DataRaceDemo.java
java DataRaceDemo
~~~

Stdout (không có interrupt bên ngoài):

~~~text
counter = 1
~~~

**Diễn giải từng bước:**

1. Cả A và B lấy **counter.value = 0** để tính **next = 1** trước khi main giải phóng latch **releaseWrites**.
2. Sau tín hiệu, mỗi worker gán **counter.value = 1**. Hai phép ghi này không được sắp thứ tự bằng happens-before với nhau, nên là các truy cập xung đột thuộc **data race**.
3. **a.join()** và **b.join()** bảo đảm main chỉ đọc sau khi hai worker đã hoàn thành; giá trị cuối là **1**, trong khi yêu cầu tăng hai lần lẽ ra phải cho **2**.
4. Các latch **chỉ ép lịch xen kẽ để tái hiện lỗi**, không bảo vệ phần ghi và không giải quyết bất biến “mỗi lần tăng phải được cộng”.

Mặc dù ví dụ in kết quả xác định, **data race không bắt buộc luôn in sai** trong các chương trình thông thường. Lịch chạy khác có thể cho kết quả đúng tình cờ. Việc hai thread cùng viết một giá trị hoặc nhiều lần test không phát hiện lỗi không chứng minh an toàn.

### Sửa bằng phép tăng nguyên tử

Tệp **AtomicCounterDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicCounterDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        Runnable increment = counter::incrementAndGet;

        Thread a = new Thread(increment, "counter-a");
        Thread b = new Thread(increment, "counter-b");
        a.start();
        b.start();

        a.join();
        b.join();
        System.out.println("counter = " + counter.get());
    }
}
~~~

~~~bash
javac -encoding UTF-8 AtomicCounterDemo.java
java AtomicCounterDemo
~~~

Stdout:

~~~text
counter = 2
~~~

Hai phép **incrementAndGet()** có tính nguyên tử; không còn lost update cho bộ đếm này. **join()** bảo đảm main đọc sau khi cả hai tăng. Nếu bất biến cần bảo vệ **nhiều trường cùng lúc**, một AtomicInteger đơn lẻ không tự giải quyết toàn bộ vấn đề.

## 6. Ví dụ logic race dù mọi thao tác riêng lẻ đều atomic

Một kho còn **1 sản phẩm**. Quy tắc nghiệp vụ yêu cầu:

> Chỉ nhận đặt hàng nếu kho còn hàng; không bao giờ nhận hai đơn khi chỉ còn một sản phẩm.

Mã sai:

~~~java
// Đoạn minh họa: stock là một AtomicInteger đã được tạo trước đó.
if (stock.get() > 0) {
    stock.decrementAndGet();
    acceptOrder(); // Hàm nghiệp vụ giả định, chưa được định nghĩa.
}
~~~

**get()**, **decrementAndGet()** đều thread-safe, nhưng **cặp thao tác kiểm tra rồi trừ** không nguyên tử. Hai thread có thể cùng đọc stock = 1 rồi mỗi thread trừ một lần. Kết quả **stock = -1** và hai đơn được chấp nhận. Đây là **logic race** và **race condition**, dù không cần data race trên biến stock.

### Ví dụ 2: ép cả hai cùng vượt qua điều kiện kiểm tra

Tệp **LogicRaceDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class LogicRaceDemo {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger stock = new AtomicInteger(1);
        AtomicInteger accepted = new AtomicInteger(0);
        CountDownLatch bothChecked = new CountDownLatch(2);

        Runnable reserve = () -> {
            if (stock.get() > 0) {
                bothChecked.countDown();
                try {
                    bothChecked.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }

                stock.decrementAndGet();
                accepted.incrementAndGet();
            }
        };

        Thread a = new Thread(reserve, "buyer-a");
        Thread b = new Thread(reserve, "buyer-b");
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("accepted = " + accepted.get());
        System.out.println("stock = " + stock.get());
    }
}
~~~

~~~bash
javac -encoding UTF-8 LogicRaceDemo.java
java LogicRaceDemo
~~~

Stdout (không có interrupt từ bên ngoài):

~~~text
accepted = 2
stock = -1
~~~

**Vì sao ví dụ đáng chú ý?**

- Trước khi có decrement, cả hai thread đều thấy stock = 1. Latch khiến mỗi thread chờ thread kia sau khi đã vượt qua kiểm tra, nên lịch vi phạm bất biến được tái hiện có chủ đích.
- **AtomicInteger** giữ mỗi lần cập nhật riêng lẻ nguyên tử, nên không bị lost update theo kiểu plain int: cả hai lần decrement đều có hiệu lực và kho đi từ 1 xuống -1.
- Lỗi nằm ở khoảng trống giữa **đọc điều kiện** và **hành động**. Đây là **time-of-check to time-of-use (TOCTOU)**.
- Việc tăng số thread, thêm **sleep()** hoặc **yield()** có thể đổi xác suất lỗi nhưng không sửa quy tắc.

**Không nên dùng đoạn code trên để đặt hàng thật**; đây là ví dụ cố ý vi phạm nghiệp vụ nhằm nhận ra logic race.

## 7. Sửa logic race bằng CAS bảo vệ cả quyết định

Ta thay **check rồi decrement** bằng một chu trình **compare-and-set (CAS)**: đọc số lượng hiện tại, từ chối nếu bằng 0, và chỉ trừ khi giá trị vẫn đúng bằng số đã đọc. Nếu thread khác đã thay đổi stock, thử lại.

Tệp **SafeReservationDemo.java**:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

public class SafeReservationDemo {

    static boolean tryReserve(AtomicInteger stock) {
        while (true) {
            int observed = stock.get();
            if (observed <= 0) {
                return false;
            }
            if (stock.compareAndSet(observed, observed - 1)) {
                return true;
            }
            // Một thread khác đã đổi stock; đọc lại và thử lại.
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicInteger stock = new AtomicInteger(1);
        AtomicInteger accepted = new AtomicInteger(0);

        Runnable reserve = () -> {
            if (tryReserve(stock)) {
                accepted.incrementAndGet();
            }
        };

        Thread a = new Thread(reserve, "buyer-a");
        Thread b = new Thread(reserve, "buyer-b");
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("accepted = " + accepted.get());
        System.out.println("stock = " + stock.get());
    }
}
~~~

~~~bash
javac -encoding UTF-8 SafeReservationDemo.java
java SafeReservationDemo
~~~

Stdout:

~~~text
accepted = 1
stock = 0
~~~

Hai thread có thể thử CAS cùng lúc, nhưng chỉ một phép chuyển **1 → 0** thành công; thread còn lại thấy 0 và từ chối. Kết quả đúng bất biến trong phạm vi **một AtomicInteger trong một JVM**, với giả định các cập nhật stock khác cũng đi qua giao thức tương thích.

**Điều kiện quan trọng:** Ví dụ chỉ giữ **số lượng hàng trong bộ nhớ**. Nếu thao tác còn gồm ghi đơn hàng, trừ tiền hoặc ghi DB, một CAS trên stock **không làm các side effect bên ngoài nguyên tử**. Cần thiết kế transaction, idempotency, hoặc cơ chế commit/compensation phù hợp tại ranh giới dữ liệu thật.

### Có thể dùng synchronized khi quyết định liên quan nhiều trường

Ví dụ minh họa API lớp, không phải chương trình độc lập:

~~~java
final class Inventory {
    private int stock;
    private int reserved;

    Inventory(int initialStock) {
        stock = initialStock;
    }

    synchronized boolean reserveOne() {
        if (stock <= 0) {
            return false;
        }
        stock--;
        reserved++;
        return true;
    }

    synchronized int remaining() {
        return stock;
    }

    synchronized int reservedCount() {
        return reserved;
    }
}
~~~

Phương thức **reserveOne()** giữ cùng một monitor trong suốt bước kiểm tra và cập nhật cả hai trường. Những phương thức khác truy cập hai trường cũng phải dùng giao thức khóa tương thích. Nếu chỉ khóa quanh **stock--** mà để **if (stock > 0)** ngoài khóa, logic race vẫn còn.

Với bộ nhớ chia sẻ, điểm mấu chốt là **phạm vi atomicity của bất biến**, không phải số lượng từ khóa **synchronized** hay số lượng trường khai báo **volatile**.

## 8. Mẫu lỗi check–then–act trong API concurrent

Sử dụng một cấu trúc thread-safe không có nghĩa **mọi tổ hợp nhiều lời gọi** trên nó tự nguyên tử.

### ConcurrentHashMap: containsKey() rồi put()

~~~java
// Đoạn minh họa logic race:
// map là ConcurrentHashMap<String, String> đã được khai báo.
if (!map.containsKey("job-42")) {
    map.put("job-42", "RUNNING");
}
~~~

Mỗi lời gọi **containsKey()** và **put()** là thread-safe, nhưng hai thread có thể cùng thấy khóa chưa tồn tại rồi cùng làm việc. Nếu chỉ muốn **ghi một giá trị khi khóa chưa có**, dùng:

~~~java
// Đoạn minh họa: putIfAbsent là thao tác có điều kiện nguyên tử.
String previous = map.putIfAbsent("job-42", "RUNNING");
if (previous == null) {
    // Lần ghi này đã thành công với key chưa có trước đó.
}
~~~

Lưu ý: nếu cả hai thread đã **gọi tác vụ tốn kém hoặc side effect ở trước putIfAbsent()**, putIfAbsent không thể hoàn tác hoặc ngăn việc bên ngoài đã chạy trùng. Cần đặt quyết định cấp quyền làm việc đúng vị trí trong giao thức. Khi cần khởi tạo giá trị theo key, **computeIfAbsent()** có ngữ nghĩa khác với cặp **get/put**, nhưng mapping function phải tuân thủ hợp đồng của collection và không nên chứa side effect không kiểm soát.

### Concurrent collection: kiểm tra rỗng rồi lấy

~~~java
// Minh họa logic race, queue đã khai báo trước:
if (!queue.isEmpty()) {
    Object item = queue.poll();
    // item vẫn có thể null nếu thread khác lấy phần tử trước.
}
~~~

Nếu phải nhận đúng một phần tử, hãy làm việc với kết quả **poll()** và xử lý **null** khi rỗng; nếu cần đợi có phần tử, cân nhắc **BlockingQueue.take()**. Hai lời gọi **isEmpty() + poll()** không biến thành một quyết định giao hàng nguyên tử.

### Data race và logic race có thể cùng tồn tại

Nếu **stock** là plain int không được khóa, đoạn **if(stock > 0) stock--;** có thể chứa **cả data race** lẫn logic race. Thay nó bằng AtomicInteger nhưng giữ nguyên hai bước **get() rồi decrementAndGet()** chỉ sửa khía cạnh truy cập đồng bộ hóa của từng thao tác, **chưa sửa bất biến**.

## 9. Race condition không chỉ nằm trong RAM

TOCTOU cũng tồn tại trong hệ thống file, database, mạng và các quy trình liên tiến trình:

- Kiểm tra file tồn tại, sau đó mở file: một tác nhân khác có thể xóa/thay thế file trong khoảng giữa.
- Đọc đơn hàng đang PENDING, sau đó cập nhật RUNNING: worker khác có thể đã nhận cùng đơn trước khi cập nhật.
- Kiểm tra số lượng trong DB rồi phát sinh đơn ở một transaction khác: hai request có thể cùng qua kiểm tra.
- Nhận một thông điệp rồi xử lý; khi thông điệp được gửi lại, side effect có thể bị thực hiện lần hai nếu không có idempotency.

Các trường hợp này có thể xảy ra **giữa hai tiến trình/JVM**, không cần hai thread chia sẻ cùng một trường Java. **AtomicInteger**, **synchronized** trong một JVM không bảo vệ dữ liệu đã được các process khác cùng thao tác.

Giải pháp phải nằm ở **nơi bất biến được quản lý**:

| Ranh giới tranh chấp | Cơ chế thường phù hợp |
| --- | --- |
| Một biến nguyên tử trong cùng JVM | AtomicInteger/AtomicReference CAS |
| Nhiều biến chia sẻ trong cùng JVM | synchronized/Lock hoặc thiết kế trạng thái bất biến |
| Hàng đợi giữa các thread | BlockingQueue và hợp đồng giao tiếp của nó |
| Dữ liệu chung giữa nhiều instance | Transaction, unique constraint, optimistic concurrency/version hoặc cập nhật có điều kiện tại DB |
| Thao tác có side effect lặp do retry | Idempotency key, trạng thái attempt, chống xử lý trùng theo hợp đồng |

Đây là các lựa chọn về **bảo toàn điều kiện đúng đắn**, không phải phép thay thế máy móc lẫn nhau. Ví dụ, ràng buộc duy nhất (*unique constraint*) bảo vệ tính duy nhất ở DB, nhưng không tự bảo đảm một cuộc gọi dịch vụ bên ngoài được thực hiện đúng một lần.

## 10. Điều kiện đúng đắn: hãy viết invariant trước khi chọn khóa

Trước khi sửa một race condition, viết ra điều gì **phải luôn đúng**:

- Bộ đếm nhận đủ hai lần tăng phải cho **2**.
- Nếu kho ban đầu có 1 sản phẩm, **accepted <= 1** và **stock >= 0**.
- Một job chỉ được một worker chuyển từ **PENDING** sang **RUNNING** tại mỗi lượt xử lý.
- Sau khi nhận yêu cầu hủy, một tác vụ không được tự đánh dấu **SUCCEEDED** nếu chính sách hủy đã thắng cuộc cạnh tranh.

Sau đó xác định **điểm tuyến tính hóa (linearization point)**: một điểm logic mà thao tác có thể coi như diễn ra nguyên tử đối với các thread khác.

Trong ví dụ **SafeReservationDemo**, phép **compareAndSet(1, 0)** thành công là điểm xác nhận quyền giữ một sản phẩm. Với một transaction ở database, điểm cấp quyền có thể là lệnh cập nhật có điều kiện **ảnh hưởng đúng một bản ghi** được commit theo giao thức của hệ thống lưu trữ. Điểm tuyến tính hóa không được tùy tiện gán bằng một dòng log hay lời gọi **sleep()**.

Không phải mọi bài toán đều cần linearizability toàn phần; một số phép tính dùng eventual consistency hoặc xử lý chấp nhận lặp. Nhưng **quy tắc chấp nhận sai số và thứ tự nào** phải được đặc tả rõ, không để lịch chạy tình cờ quyết định.

### Phân biệt atomicity, thread safety và business correctness

- **Atomicity của phép đơn:** một incrementAndGet() không bị chia thành các bước nhìn thấy riêng đối với biến AtomicInteger đó.
- **Thread safety của thành phần:** ConcurrentHashMap giữ đúng các bảo đảm của từng operation đã tài liệu hóa.
- **Atomicity của chuỗi nghiệp vụ:** “đọc tồn kho, giữ hàng, ghi đơn, trả xác nhận” cần giao thức nhiều bước riêng.
- **Correctness:** kết quả cuối tuân thủ đặc tả ngay cả khi có cạnh tranh, timeout, retry, hủy hoặc crash (nếu chúng thuộc phạm vi yêu cầu).

Không thể suy ra mục thứ ba và thứ tư chỉ từ hai mục đầu.

## 11. Tại sao sleep(), yield(), log và một lần test không đủ?

Phát sinh lỗi race có thể phụ thuộc một cửa sổ thời gian rất ngắn. Khi thêm **System.out.println(...)**, JIT, I/O và scheduler có thể làm lịch chạy thay đổi. Một lỗi mất đi khi bật logging không chứng minh đã sửa.

Các cách sau **không phải giải pháp đúng**:

~~~java
// Mã minh họa anti-pattern; không phải chương trình độc lập:
if (ready) {
    Thread.sleep(10); // Không làm check-then-act nguyên tử.
    doWork();
}

while (!ready) {
    Thread.yield(); // Không đảm bảo visibility hoặc progress.
}
~~~

Trong ví dụ trên, **ready**, **doWork()** là thành phần giả định; lệnh sleep có checked exception cần xử lý. Nếu ready là plain field được sửa từ thread khác, cả việc quan sát cờ cũng có thể thiếu bảo đảm bộ nhớ.

Để kiểm thử có cơ sở:

1. **Viết invariant** và liệt kê các tình huống đồng thời có thể vi phạm.
2. **Kiểm soát xen kẽ bằng latch/barrier**, ép hai thread cùng đến vị trí nhạy cảm như các ví dụ trong bài. Dùng các công cụ này để tái hiện lỗi, không coi barrier tự sửa tính nguyên tử của thao tác.
3. **Lặp stress test** với nhiều thread và tải khác nhau; ghi lại seed/cấu hình nếu có. Mọi lượt pass vẫn không phải chứng minh chương trình không có race.
4. **Kiểm tra trạng thái cuối** và đếm side effect, thay vì chỉ kiểm tra “không ném exception”.
5. **Rà soát happens-before và critical section**: lock nào bảo vệ trường nào, đường cập nhật nào chưa đi qua lock/CAS, phần check/use có bị tách không?
6. **Xét retry, cancellation và side effect muộn** nếu công việc có nhiều lượt thử hoặc nhiều process. Không để kết quả attempt cũ ghi đè trạng thái attempt mới.

Lập luận tính đúng đắn dựa trên hợp đồng đồng bộ hóa và invariant là yếu tố chính; test là công cụ phát hiện và củng cố, không thay thế lập luận đó.

## 12. Sai lầm thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| Data race và race condition là một | Data race nói về truy cập bộ nhớ thiếu HB; race condition nói về phụ thuộc lịch gây vi phạm yêu cầu |
| Mọi race condition đều có data race | Logic race với toàn thao tác atomic vẫn có thể sai |
| Dùng AtomicInteger thì cả nghiệp vụ an toàn | Mỗi operation atomic không khiến chuỗi check–then–act atomic |
| Một lần chạy ra giá trị đúng nghĩa là không có data race | Có thể chỉ do lịch chạy tình cờ |
| Hai dòng log đảo thứ tự luôn là lỗi race | Chỉ là lỗi nếu thứ tự đó vi phạm đặc tả |
| volatile làm **count++** nguyên tử | volatile không gộp read–modify–write |
| sleep/yield bảo đảm thread khác cập nhật xong | Không cung cấp giao thức đồng bộ hoặc bảo đảm scheduler |
| ConcurrentHashMap làm mọi cặp get/put an toàn như một transaction | Chỉ các thao tác có hợp đồng nguyên tử tương ứng mới có bảo đảm đó |
| synchronized trên từng hàm riêng là đủ | Nếu bất biến trải qua hai hàm, vẫn có khe xen kẽ giữa hai lần khóa |
| AtomicInteger bảo vệ tồn kho được chia sẻ giữa nhiều JVM | AtomicInteger chỉ quản lý biến trong một JVM |
| Test nhiều lần không lỗi tức là không còn race | Lịch chưa gây lỗi có thể chưa xuất hiện; cần chứng minh theo invariant và HB |

## 13. Bài tập suy luận

**Bài 1.** Hai thread cùng tăng một **plain int** 1.000 lần nhưng kết quả lần chạy này đúng 2.000. Có chứng minh không có data race?  
**Đáp án:** Không. Các phép đọc/ghi xung đột vẫn không có đồng bộ hóa phù hợp; lịch chạy tình cờ có thể không làm mất cập nhật. Dùng atomic increment hoặc khóa bảo vệ toàn phép tăng.

**Bài 2.** Hai thread gọi **stock.get() > 0** rồi **stock.decrementAndGet()**, mỗi lời gọi atomic. Tại sao stock vẫn có thể âm?  
**Đáp án:** Điều kiện được kiểm tra trên một snapshot, nhưng không có bảo đảm giữ giá trị cho đến lần trừ. Cần CAS loop hoặc critical section bao quanh cả check và update.

**Bài 3.** Một thread dùng **synchronized** quanh hàm kiểm tra, thread khác dùng synchronized quanh hàm trừ, đều trên cùng lock. Đã đủ ngăn oversell chưa?  
**Đáp án:** Chưa nếu check và trừ ở hai critical section riêng biệt. Thread khác có thể chen vào khoảng giữa. Toàn bộ quyết định phải nằm trong cùng một critical section hoặc thao tác có điều kiện nguyên tử tương đương.

**Bài 4.** Hai worker ghi vào **ConcurrentHashMap** và không có data race trên các operation. Vậy có thể chắc chắn một job chỉ chạy một lần không?  
**Đáp án:** Không. Nếu check job chưa có và thực thi trước bước cấp quyền, hai worker vẫn có thể cùng làm việc. Phải thiết kế cấp quyền nguyên tử và điều kiện xử lý lại/idempotency.

**Bài 5.** Nếu chương trình không dùng shared mutable state mà chỉ nhận hai phản hồi mạng đến theo thứ tự bất định, còn có race condition không?  
**Đáp án:** Có thể, nếu kết quả nghiệp vụ đòi thứ tự hoặc tính duy nhất nhưng code xử lý tùy theo phản hồi đến trước mà không có luật. Nó không nhất thiết là JMM data race.

**Bài 6.** Vì sao cần CountDownLatch trong **LogicRaceDemo**?  
**Đáp án:** Để ép hai thread đi qua bước kiểm tra khi stock vẫn là 1 rồi mới cho phép trừ. Nhờ vậy kết quả oversell tái hiện chắc chắn. Latch không sửa bất biến check–then–act.

**Bài 7.** Một biến **AtomicInteger** cho kết quả đúng sau khi hai worker join. Join có phải thứ đã ngăn lost update không?  
**Đáp án:** Không. **incrementAndGet()/CAS** bảo đảm cập nhật nguyên tử; **join()** chỉ giúp caller đợi các worker kết thúc rồi quan sát kết quả đã hoàn thành.

## 14. Tổng kết

- **Data race**: truy cập xung đột từ nhiều thread trên cùng biến, ít nhất một ghi, thiếu thứ tự happens-before theo JMM.
- **Race condition**: lịch xen kẽ có thể làm kết quả vi phạm một yêu cầu đúng đắn; không nhất thiết có data race.
- **Logic race**: race condition trong cách phối hợp nhiều bước của quy tắc ứng dụng, điển hình là **check–then–act/TOCTOU**.
- **Cách sửa** phải xuất phát từ invariant: xác định dữ liệu và phạm vi quyết định cần nguyên tử, dùng synchronization/CAS/transaction hoặc giao thức trao quyền ở đúng ranh giới.
- **Hãy kiểm chứng lịch xen kẽ và trạng thái cuối**, nhưng đừng dùng sleep, yield hoặc test may mắn để thay thế hợp đồng đồng bộ hóa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="race-conditions-010">Minh họa lỗi bằng test điều phối thời điểm thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Minh họa lỗi race condition bằng test điều phối thời điểm thực thi trong Java

Race condition là lỗi mà kết quả của chương trình phụ thuộc vào thứ tự và thời điểm các thao tác đồng thời xen kẽ nhau. Vì hệ điều hành và JVM có quyền lên lịch thread theo nhiều cách, một đoạn code sai có thể chạy đúng hàng nghìn lần rồi mới lỗi. **Một test “chạy nhiều thread rồi hy vọng đụng nhau” không đủ để chứng minh hoặc tái hiện một race cụ thể.** Ta có thể chủ động tạo ra lịch thực thi nguy hiểm bằng công cụ đồng bộ để chứng minh một invariant bị vi phạm, rồi chạy cùng tình huống trên thiết kế đã sửa.

Bài học tập trung vào **Java concurrency thuần** và các công cụ có sẵn trong `java.util.concurrent`. Mục tiêu là biết cách viết **test điều phối interleaving**, giải thích từng điểm chờ, quan sát lỗi một cách có kiểm soát, đặt timeout tránh treo suite test, chuyển lỗi từ worker về test runner và hiểu giới hạn của loại test này. Đây không phải bài hướng dẫn một framework cụ thể.

## 1. Vì sao race condition khó tái hiện?

Giả sử hai thread cùng tăng một biến:

```java
class UnsafeCounter {
    int count;

    void increment() {
        count++;
    }
}
```

Đây là **đoạn minh họa**, chưa có `main`. `count++` là thao tác read–modify–write về mặt logic: đọc giá trị cũ, cộng một, ghi kết quả mới. Nó **không nguyên tử** đối với các thread khác nếu chỉ là field `int` thông thường. Nếu A và B đều đọc giá trị `0` trước khi một trong hai ghi, cả hai có thể ghi lại `1`; sau hai lần gọi `increment()`, tổng chỉ là `1` thay vì `2`.

| Thời điểm logic | Worker A | Worker B | `count` |
| --- | --- | --- | --- |
| Ban đầu | | | `0` |
| 1 | Đọc `0` | | `0` |
| 2 | | Đọc `0` | `0` |
| 3 | Ghi `0 + 1` | | `1` |
| 4 | | Ghi `0 + 1` | `1` |

Đây là **lost update**: một lần cập nhật về nghiệp vụ không được tính vào trạng thái cuối. Bảng mô tả các bước ở cấp logic, không khẳng định JIT phải tạo đúng ba lệnh CPU cho phép `count++`.

Khi viết test thông thường:

```java
Thread a = new Thread(counter::increment);
Thread b = new Thread(counter::increment);
a.start();
b.start();
a.join();
b.join();
```

test có thể thấy `2` vì A kết thúc trước B bắt đầu, hoặc các bước không chồng lên nhau. Thêm vòng lặp hàng triệu lần làm tăng cơ hội gặp lỗi nhưng **không đảm bảo** cùng một interleaving sẽ xuất hiện. `Thread.sleep(1)` cũng không áp đặt thứ tự cần thiết: scheduler không bảo đảm thread nào chạy ngay khi thời gian ngủ kết thúc.

**Cách kiểm thử tốt hơn cho một lịch lỗi đã biết:** chia thao tác logic thành các bước có thể kiểm soát; đặt *rendezvous* (điểm gặp) sau khi cả hai worker đọc trạng thái và trước khi cả hai ghi. Khi điều kiện gặp đã thỏa, cho phép chúng tiếp tục. Test sẽ lặp lại đúng loại lịch nguy hiểm mà ta muốn minh họa.

## 2. Xác định invariant trước khi dựng test

Một test race có giá trị khi nêu rõ điều kiện đúng cần được bảo toàn:

| Nghiệp vụ | Invariant cần giữ | Race có thể tạo |
| --- | --- | --- |
| Hai lần tăng counter | Sau hai increment thành công, giá trị tăng đúng `2` | Lost update, kết quả chỉ tăng `1` |
| Một ghế chỉ bán một lần | Có tối đa một xác nhận đặt chỗ thành công | Hai người cùng thấy còn ghế và được xác nhận |
| Trừ hàng tồn kho | Không bán quá số lượng tồn thực tế | Nhiều thread cùng qua kiểm tra tồn kho |
| Chỉ khởi tạo cache entry một lần | Một key không có hai lần khởi tạo nghiệp vụ gây side effect | Hai thread cùng check rồi cùng create |
| Cập nhật hai trường liên quan | Reader quan sát một snapshot thỏa bất biến | Reader thấy dữ liệu của hai phiên bản |
| Quản lý vòng đời | Sau khi shutdown, không nhận công việc mới trái hợp đồng | Check trạng thái rồi submit bị xen vào bởi shutdown |

**Phân biệt:** *data race* là khái niệm của Java Memory Model khi có các truy cập xung đột mà không có quan hệ happens-before thích hợp. *Race condition* rộng hơn, bao gồm lỗi nghiệp vụ do các **thao tác đơn lẻ đều thread-safe nhưng tổ hợp của chúng không atomic**. Test điều phối có thể làm lộ lỗi check–then–act ngay cả khi từng bước đã dùng cấu trúc đồng bộ.

Đừng chỉ assert “không ném exception”. Với race, nhiều kết quả sai vẫn trả về thành công; test phải xác minh số lượng cập nhật, số xác nhận, số dư hoặc invariant cụ thể.

## 3. Bộ công cụ Java để điều phối thời điểm

| Công cụ | Vai trò khi test | Điểm cần nhớ |
| --- | --- | --- |
| `CountDownLatch(n)` | Chờ đủ `n` sự kiện rồi mới mở một giai đoạn | Bộ đếm chỉ giảm, **không reset**; thích hợp cổng bắt đầu/kết thúc một lần |
| `CyclicBarrier(n)` | Buộc `n` participant tới cùng điểm trước khi ai tiếp tục | Tái sử dụng qua nhiều vòng; nếu một participant timeout/interrupt, barrier có thể bị **broken** |
| `Phaser` | Điều phối nhiều pha, số participant có thể thay đổi | Linh hoạt hơn, nhưng phức tạp hơn latch/barrier |
| `Thread.join()` | Chờ một thread kết thúc | Không bảo đảm thread đã đi qua checkpoint mong muốn trước khi kết thúc |
| `Future.get(timeout, unit)` | Nhận kết quả/exception của worker và có deadline | Timeout khi đợi không tự đồng nghĩa worker đã bị hủy |
| `ExecutorService` với số worker đủ | Chạy các participant | Pool có ít worker hơn số task cùng đợi barrier có thể tự gây deadlock |
| `AtomicInteger`/`AtomicBoolean` | Ghi nhận kết quả hoặc tín hiệu từ nhiều worker an toàn | Không tự khiến một workflow gồm nhiều bước trở thành atomic |
| `Semaphore` | Giới hạn số worker được phép đi qua một vùng | Không tạo rendezvous toàn bộ participant như barrier |

Một cách biểu diễn lịch bằng barrier:

```text
Worker A: READ count=0 -----> [barrier] -----> WRITE 1
Worker B: READ count=0 -----> [barrier] -----> WRITE 1
                                  |
                            đủ 2 participant
                                  |
                         cả hai được tiếp tục
```

Khi A đến điểm gặp trước, nó phải đợi B; khi B đến, cả hai được phép tiếp tục. **Điểm quan trọng là tất cả READ đã xong trước khi bất kỳ WRITE nào bắt đầu.** Ta không cần và không thể quyết định chính xác worker nào chạy trước sau khi barrier mở; trong ví dụ này cả hai đều ghi cùng giá trị `1` nên kết quả vẫn xác định.

### Khác nhau giữa điều phối test và sửa production

- **Điều phối test** tạo một lịch thực thi có chủ đích để khám phá hoặc chứng minh lỗi. Nó *có thể tạo thêm happens-before* và ảnh hưởng những interleaving khác.
- **Sửa production** bảo đảm thao tác nghiệp vụ đúng trước **mọi interleaving được hợp đồng cho phép**, bằng mutual exclusion, CAS, atomic API, immutable state, message passing hoặc thiết kế ownership phù hợp.
- Nếu test ép cả hai worker đọc một snapshot cũ rồi vẫn được quyền ghi dựa trên snapshot ấy, đó là lỗi **giao thức read–modify–write**, nhưng không thể suy diễn máy móc rằng test này chứng minh mọi truy cập là *data race hình thức* theo JMM. Latch/barrier tự chúng là primitive đồng bộ và thiết lập những quan hệ happens-before.

## 4. Ví dụ Java 17 hoàn chỉnh: ba phép thử điều phối

Chúng ta sẽ chạy ba tình huống trong cùng một chương trình Java 17:

1. **Lost update bị ép xuất hiện:** hai worker đọc chung giá trị `0`, cùng chờ barrier rồi ghi `1`; test kiểm tra rằng hai lần tăng chỉ tạo `1`, tức invariant đúng `2` bị vi phạm.
2. **Đặt chỗ trùng:** hai worker cùng kiểm tra “chưa có ai đặt” trước barrier, sau đó mỗi worker xác nhận một chỗ; số xác nhận thành công thành `2`, vượt quá giới hạn `1`.
3. **Sửa bằng critical section:** dùng `synchronized` để gộp check–then–set thành một thao tác. Hai worker vẫn thử đặt, nhưng chỉ một worker được xác nhận.

Tạo tệp **`CoordinatedRaceTest.java`**:

```java
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CoordinatedRaceTest {
    private static final int TIMEOUT_SECONDS = 5;

    static class Counter {
        int value = 0;
    }

    static class UnsafeSeat {
        boolean booked = false;
    }

    static class SafeSeat {
        private boolean booked = false;

        synchronized boolean bookOnce() {
            if (booked) {
                return false;
            }
            booked = true;
            return true;
        }
    }

    private static void assertEquals(int expected, int actual, String name) {
        if (actual != expected) {
            throw new AssertionError(
                    name + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void runTwo(Callable<Void> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = pool.submit(task);
            Future<Void> second = pool.submit(task);

            first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            second.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void reproduceLostUpdate() throws Exception {
        Counter counter = new Counter();
        CyclicBarrier bothRead = new CyclicBarrier(2);

        runTwo(() -> {
            int snapshot = counter.value; // Cả hai đọc 0
            bothRead.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            counter.value = snapshot + 1;
            return null;
        });

        // Chủ động xác nhận lịch lỗi đã được tái hiện.
        assertEquals(1, counter.value, "lost update");
        System.out.println("lost update: actual=1, correct=2");
    }

    private static void reproduceDoubleBooking() throws Exception {
        UnsafeSeat seat = new UnsafeSeat();
        AtomicInteger confirmations = new AtomicInteger();
        CyclicBarrier bothChecked = new CyclicBarrier(2);

        runTwo(() -> {
            boolean available = !seat.booked; // Cả hai thấy còn chỗ
            bothChecked.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (available) {
                seat.booked = true;
                confirmations.incrementAndGet();
            }
            return null;
        });

        assertEquals(2, confirmations.get(), "double booking");
        System.out.println("double booking: actual=2, allowed=1");
    }

    private static void verifyFixedBooking() throws Exception {
        SafeSeat seat = new SafeSeat();
        AtomicInteger confirmations = new AtomicInteger();

        runTwo(() -> {
            if (seat.bookOnce()) {
                confirmations.incrementAndGet();
            }
            return null;
        });

        // Đây là invariant nghiệp vụ cần bảo toàn.
        assertEquals(1, confirmations.get(), "fixed booking");
        System.out.println("fixed booking: actual=1, allowed=1");
    }

    public static void main(String[] args) throws Exception {
        reproduceLostUpdate();
        reproduceDoubleBooking();
        verifyFixedBooking();
    }
}
```

Biên dịch và chạy:

```bash
javac CoordinatedRaceTest.java
java CoordinatedRaceTest
```

**Stdout chính xác khi chương trình hoàn thành bình thường:**

```text
lost update: actual=1, correct=2
double booking: actual=2, allowed=1
fixed booking: actual=1, allowed=1
```

Nếu môi trường không chạy được đủ participant hoặc task bị interrupt/timeout, phép thử **thất bại bằng exception** thay vì in kết quả sai lệch như thể thành công. Lý do có `Future.get(timeout)` và `CyclicBarrier.await(timeout)` là bảo vệ test khỏi chờ vô hạn, không phải xác lập một khoảng ngủ dùng để gây race.

### Phân tích từng phép thử

**Phép 1 — lost update**

- `counter` là một instance chung cho hai worker.
- `snapshot` là biến local riêng của từng lần chạy `Callable`.
- Mỗi worker đọc `counter.value` trước khi đến `bothRead.await(...)`.
- Barrier mở khi hai worker đều đến, nên tại thời điểm đọc không ai đã ghi.
- Cả hai có `snapshot == 0`, đều ghi `1`; trạng thái cuối `1`, dù có hai lần tăng về nghiệp vụ.

Đoạn code cố tình biến `counter.value++` thành read và write riêng để **đặt checkpoint giữa hai bước**. Đó là một *test harness* mô phỏng thao tác không atomic; nó không có nghĩa trong production phải viết phép tăng thành hai dòng để mới sinh race. Với `counter.value++` thực, lịch đọc chồng lên nhau vẫn có thể xảy ra khi thiếu đồng bộ.

**Phép 2 — check–then–act**

- `seat.booked` là cờ dùng chung ban đầu `false`.
- Cả hai lấy `available = !seat.booked` trước barrier và đều được `true`.
- Sau barrier, mỗi worker tin thông tin cũ vẫn đúng; hai worker cùng xác nhận đặt chỗ.
- Dùng `AtomicInteger confirmations` chỉ để **đo chính xác hai xác nhận**, tránh chính biến đếm trong test bị lost update che mất lỗi. `AtomicInteger` ở đây **không làm cờ `booked` an toàn**.

Race nghiệp vụ xảy ra vì “kiểm tra còn chỗ” và “đặt chỗ” là hai bước không nguyên tử. Nó khác lỗi tăng counter nhưng cùng bản chất: **quyết định dựa trên snapshot đã bị các thread khác làm thay đổi**.

**Phép 3 — gộp thao tác thành một critical section**

- `SafeSeat.bookOnce()` bảo vệ cả hai bước `if (booked)` và `booked = true` bằng **monitor của cùng một instance `seat`**.
- Một worker vào trước thấy `false`, đổi sang `true` và trả `true`.
- Worker còn lại chỉ vào sau khi monitor được nhả, nhìn thấy `true` và trả `false`.
- Bất kể thứ tự worker, đúng một xác nhận được tăng.

**Vì sao test sửa lỗi không đặt barrier trong `bookOnce()`?** Nếu một worker đã lấy monitor rồi chờ `CyclicBarrier(2)` ngay **bên trong** method `synchronized`, worker thứ hai không thể vào method để tới barrier. Test sẽ tự gây deadlock/timeout, không phải do code production sai. Điều phối trước khi tranh khóa hoặc quan sát kết quả sau khi cả hai hoàn tất, **không đặt rendezvous trong critical section được thực thi độc quyền**.

### Vì sao không dùng `assert` mặc định của Java?

Java `assert` có thể bị vô hiệu khi không bật tùy chọn JVM `-ea`. Hàm `assertEquals` riêng ở trên ném `AssertionError` vô điều kiện khi khác giá trị mong đợi, nên test không bị “vô hiệu hóa” vì thiếu tham số chạy. Khi đưa sang JUnit, có thể dùng `org.junit.jupiter.api.Assertions.assertEquals`; ví dụ hiện tại **không cần thư viện test ngoài JDK**.

## 5. Hai kiểu điều phối quan trọng: rendezvous và cổng một chiều

### 5.1. `CyclicBarrier`: mọi người đều tới checkpoint

Dùng barrier khi muốn bảo đảm hai hay nhiều thread đều đã hoàn thành một pha trước khi bất kỳ thread nào bước sang pha tiếp theo:

```java
CyclicBarrier barrier = new CyclicBarrier(2);

Runnable action = () -> {
    try {
        // Pha A: đọc hoặc quan sát trạng thái
        barrier.await(5, TimeUnit.SECONDS);
        // Pha B: cập nhật dựa trên trạng thái đã đọc
    } catch (Exception e) {
        throw new RuntimeException(e);
    }
};
```

Đoạn trên chỉ là **snippet**, chưa có `main` hay worker. `await(timeout)` có thể ném `InterruptedException`, `BrokenBarrierException` hoặc `TimeoutException`; ví dụ gộp vào một nhánh lỗi để minh họa cấu trúc, nhưng trong ứng dụng thật cần xử lý interrupt phù hợp.

- `CyclicBarrier(2)` đòi **hai lần gọi `await` trong cùng một thế hệ (generation)**, không phải chỉ một worker.
- Nếu chỉ có một worker trong pool và task thứ nhất đợi barrier, task thứ hai đang chờ trong queue không bao giờ đến barrier; có timeout thì test thất bại nhanh hơn, nhưng vẫn là lỗi điều phối.
- Barrier có thể tái sử dụng nhiều vòng. Nếu một participant lỗi, thoát sớm hoặc timeout, barrier bị broken; cần xử lý tình trạng ấy trước khi reuse.
- Hợp đồng memory consistency của `CyclicBarrier` hỗ trợ chuyển giao dữ liệu giữa các pha; do đó **không dùng barrier để chứng minh thiếu visibility tự phát trên một field**.

### 5.2. `CountDownLatch`: main điều khiển khi nào mở cổng

Một lịch test thường gặp gồm ba mốc: hai task đã sẵn sàng → main ra lệnh bắt đầu → main đợi cả hai kết thúc. Ví dụ độc lập Java 17:

Tệp `StartGateExample.java`:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class StartGateExample {
    public static void main(String[] args) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger completed = new AtomicInteger();

        Runnable task = () -> {
            try {
                ready.countDown();
                start.await();
                completed.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };

        Thread first = new Thread(task, "first");
        Thread second = new Thread(task, "second");
        first.start();
        second.start();

        if (!ready.await(5, TimeUnit.SECONDS)) {
            start.countDown();
            throw new IllegalStateException("Workers not ready");
        }
        start.countDown();

        if (!done.await(5, TimeUnit.SECONDS)) {
            first.interrupt();
            second.interrupt();
            throw new IllegalStateException("Workers not finished");
        }

        first.join();
        second.join();
        System.out.println("completed=" + completed.get());
    }
}
```

```bash
javac StartGateExample.java
java StartGateExample
```

Stdout khi hoàn thành bình thường:

```text
completed=2
```

**Vai trò của từng latch:**

- `ready`: mỗi worker báo đã đến checkpoint trước khi main mở cổng.
- `start`: main quyết định khi nào các worker được tiếp tục. Một `countDown()` mở cổng cho **tất cả** waiter.
- `done`: đếm worker đã đi qua khối `finally` để main không kết luận sớm.
- `AtomicInteger`: ghi số task hoàn thành không bị lost update.

Ví dụ dùng `start.await()` không timeout ở worker để minh họa cổng, nhưng main **chắc chắn mở cổng trên đường thành công hoặc khi `ready` timeout**; khi dùng trong test suite phức tạp, có thể thay bằng timed await và quản lý việc hủy để hạn chế thread còn sống sau thất bại. `CountDownLatch` dùng một lần; muốn tổ chức giai đoạn tiếp theo, tạo latch mới hoặc chọn `Phaser`.

**Khác biệt chính:** `CyclicBarrier` tự mở khi đủ participant; `CountDownLatch` cho phép một hoặc nhiều thread bên ngoài mở cổng chủ động. Với race cần ép cả hai *đã đọc xong* trước khi ghi, barrier thường trực tiếp hơn; với test cần main giữ tất cả worker tại start gate, latch phù hợp hơn.

## 6. Kiểm soát checkpoint đúng vị trí

Để tái hiện race đã nghi ngờ, cần phân rã thao tác ở đúng ranh giới:

| Race muốn kiểm tra | Điểm dừng phù hợp | Điều quan sát sau khi mở |
| --- | --- | --- |
| Lost update | Sau khi mỗi worker đọc counter, trước khi ghi | Counter tăng thiếu |
| Double booking | Sau khi cả hai đọc `available=true`, trước khi đặt | Có hơn một confirmation |
| Cache init trùng | Sau khi cả hai xác định cache miss, trước khi put/create | Create chạy nhiều lần |
| Stock oversell | Sau khi hai worker thấy còn hàng, trước khi trừ | Đã bán vượt tồn |
| Shutdown vs submit | Sau khi caller đọc trạng thái hoạt động, trước khi submit | Operation có thể vượt quy tắc vòng đời |
| Snapshot nhiều field | Giữa bước cập nhật field A và field B | Reader thấy invariant bị phá vỡ |

**Điều phối không đồng nghĩa với việc can thiệp vào tất cả mã production.** Trong test có thể đưa logic gây nghi ngờ vào một class nhỏ có hook/checkpoint hoặc test harness tái tạo thuật toán với cùng các bước. Tuy nhiên, test có hook chỉ chứng minh hành vi trên đường đi đã được dựng. Với module thực tế cần bổ sung integration test trên đúng implementation đang chạy để tránh test chỉ kiểm tra một bản sao thuật toán.

Ví dụ một thiết kế test seam rõ ràng có thể tách `readSnapshot`, `applyUpdate` hoặc dùng interface callback do test cung cấp **giữa hai thao tác vốn chưa được đồng bộ**. Cẩn thận: nếu thêm callback hoặc barrier **bên trong một critical section đúng** để ép hai thread gặp nhau, test có thể tự làm deadlock; test phải tôn trọng locking contract.

## 7. Thiết kế test không flaky: kiểm soát lỗi và kết thúc worker

### 7.1. Vì sao cần đưa exception từ worker về main?

Exception xảy ra trong `Runnable` có thể chỉ xuất hiện ở stderr của thread đó. Nếu test runner không chờ/thu kết quả đúng cách, test có thể thông báo pass dù worker thất bại. Với `ExecutorService.submit` và `Future.get()`, lỗi từ `Callable` được chuyển thành `ExecutionException` ở bên gọi, giúp case test fail thay vì im lặng.

`Future.get(timeout)` còn ngăn main đợi vô hạn. Khi timeout, phải xử lý hậu quả: hủy task, interrupt nếu thích hợp, đóng executor, và điều tra xem worker có đang giữ lock hoặc chờ một barrier không thể đủ participant. **Timeout chỉ giới hạn thời gian caller chờ, không tự động làm task ngừng chạy.** `shutdownNow()` cố gắng interrupt các task còn chạy; nó không bảo đảm mọi task hợp tác dừng ngay.

### 7.2. Luôn đặt deadline cho rendezvous có nguy cơ thiếu participant

Có thể dùng:

```java
barrier.await(5, TimeUnit.SECONDS);
```

hoặc:

```java
if (!ready.await(5, TimeUnit.SECONDS)) {
    throw new AssertionError("Missing worker");
}
```

Đây là hai **snippet**, giả sử `barrier` và `ready` đã được khởi tạo phù hợp. Deadline giúp test **thất bại có tín hiệu rõ ràng** nếu worker không tới checkpoint. Chọn timeout đủ cho máy CI chậm và báo lỗi chính xác; đừng dùng timeout nhỏ tới mức tạo lỗi ngẫu nhiên chỉ vì scheduler.

### 7.3. Chỉ assert sau khi tác vụ liên quan đã hoàn tất

Đọc một counter khi hai worker còn đang ghi có thể tạo thêm race ngay trong test. Hãy chờ `Future.get()` hoặc `Thread.join()` hoàn tất rồi mới kiểm tra kết quả. Các API này cũng có memory-consistency guarantees hỗ trợ quan sát thay đổi của worker theo hợp đồng Java.

### 7.4. Không để `Thread.sleep` điều khiển thứ tự

`sleep` làm thread tạm ngừng một khoảng tối thiểu tùy scheduler; nó không nói “thread B chắc chắn đã đọc state” hay “thread A đã qua checkpoint”. Nếu test cần chứng minh hai worker đều sẵn sàng, dùng latch/barrier. Có thể dùng `sleep` để mô phỏng I/O trong test hiệu năng, nhưng **không dùng nó thay chứng cứ đồng bộ**.

### 7.5. Đặt cổng với số participant chính xác

Một `CyclicBarrier(3)` nhưng chỉ có hai worker gọi `await` sẽ không mở. Nếu main cũng gọi `await` thì tính main là participant thứ ba; nếu main không gọi thì chỉ đặt `2`. Nếu pool có một worker trong khi hai task phải gặp cùng barrier, task thứ nhất chiếm worker rồi đợi task thứ hai mãi. Trong chương trình chính, `newFixedThreadPool(2)` đảm bảo có đủ worker cho hai participant cùng chờ.

### 7.6. Kiểm soát interruption

`InterruptedException` là tín hiệu ngắt cần được chuyển tiếp hoặc được tôn trọng. Nếu không thể ném tiếp trong `Runnable`, khôi phục `Thread.currentThread().interrupt()` và thoát. Nếu ignore interrupt trong vòng đợi vô hạn, `shutdownNow` có thể không giúp test đóng hết worker. Với `CyclicBarrier`, thread bị interrupt có thể làm barrier bị broken; test nên thất bại rõ ràng thay vì giả định mọi participant đã tiếp tục.

## 8. Tại sao kết quả test ở ví dụ là xác định?

Ba phép thử trong `CoordinatedRaceTest` không dùng timing ngẫu nhiên để áp đặt thứ tự. Độ chắc chắn dựa vào:

1. Cả hai worker được submit vào pool hai thread.
2. Cả hai worker **đọc giá trị cần dùng trước khi gọi `barrier.await()`**.
3. Không worker nào ghi vào state liên quan trước khi cả hai đã tới barrier.
4. Sau khi barrier mở, thứ tự ghi có thể khác nhau nhưng ở tình huống lỗi cả hai đều ghi cùng giá trị (counter `1`, seat `true`).
5. `Future.get()` trả về sau khi cả hai task hoàn tất; main mới kiểm tra và in.
6. `AtomicInteger confirmations` ngăn chính công cụ đo số xác nhận bị race.

Ví dụ giả định không có interrupt ngoài ý muốn, không hết deadline và máy đủ tài nguyên hoàn thành task. Nếu một điều kiện vận hành đó không được đáp ứng, test có thể fail/timeout. **Không nên biến cam kết “cùng một lịch logic” thành cam kết JVM sẽ luôn hoàn thành trong chính xác 5 giây** ở mọi môi trường.

Đối với counter, barrier tạo ra thứ tự mà các lần đọc xảy ra trước các lần ghi. Vì vậy test này đặc biệt tốt để chứng minh **sự thiếu atomicity của cả workflow**, nhưng không mô phỏng đầy đủ tập hợp interleaving và tái sắp xếp mà JMM cho phép khi hoàn toàn thiếu đồng bộ. Không dùng nó để chứng minh visibility bug hoặc reorder bug chỉ bằng một kết quả.

## 9. Viết một test phải FAIL với code lỗi rồi PASS sau khi sửa

Trong ví dụ đầu, `assertEquals(1, counter.value)` xác nhận **test harness đã tái hiện lỗi**, nên bản thân chương trình vẫn in kết quả và không fail. Khi biến tình huống thành regression test, **điều kiện kỳ vọng phải là invariant đúng của nghiệp vụ**:

```java
// Trong một regression test dành cho counter đúng:
if (counter.value != 2) {
    throw new AssertionError(
            "Lost update: expected=2, actual=" + counter.value);
}
```

Đây là snippet đặt sau khi đã đợi mọi worker hoàn thành. Với implementation lỗi, assertion này fail có chủ đích; sau khi sửa thao tác increment bằng `synchronized` hoặc `AtomicInteger.incrementAndGet()`, test tương ứng phải pass.

**Không nên chỉ thay một câu lệnh ghi trong test đã dựng lịch lỗi rồi kỳ vọng kết quả tự đổi.** Một test được thiết kế để **cả hai cố ý đọc trước và sau đó ghi snapshot cũ** sẽ vẫn tạo lost update ngay cả nếu từng lần đọc/ghi là atomic. Phải thay **đơn vị thao tác nghiệp vụ** thành thao tác atomic, chẳng hạn thay cả cặp “đọc snapshot → ghi snapshot+1” bằng `AtomicInteger.incrementAndGet()`, hoặc bảo vệ toàn bộ chuỗi read–modify–write bằng cùng monitor. Nếu cố chèn `CyclicBarrier(2)` **bên trong** `synchronized` bao toàn bộ read–modify–write, test sẽ deadlock vì worker thứ hai không thể vào critical section.

Một chiến lược test gọn:

- **Reproduction test:** dựng interleaving làm lỗi xuất hiện trên phiên bản không an toàn; chứng minh hiểu chính xác cửa sổ race.
- **Regression test:** tạo nhiều worker thử **operation thật**; assert invariant đúng trên phiên bản đã sửa. Có thể dùng start gate để chúng bắt đầu gần nhau, nhưng không chèn barrier giữa các bước của thao tác đã được sửa thành atomic.
- **Stress/integration test:** chạy nhiều lịch hơn trên code thực tế để tăng độ bao phủ; dùng timeout và reporting tốt.
- **Code review/JMM reasoning:** xác nhận tính đúng đắn của cơ chế đồng bộ. Một số lịch test pass không chứng minh mọi lịch đều an toàn.

## 10. Race từ hai thao tác riêng đều thread-safe

Điều phối rất hữu ích khi phát hiện race ở cấp nghiệp vụ dù từng method an toàn.

Giả sử một map thread-safe:

```java
// Snippet minh họa, giả định map là ConcurrentHashMap<Key, Value>.
if (!map.containsKey(key)) {
    map.put(key, createValue());
}
```

`containsKey` và `put` có các bảo đảm thread-safety riêng, nhưng hai thread có thể cùng quan sát key chưa tồn tại rồi cùng gọi `createValue()`. Cả hai có thể gây side effect (ghi file, gọi dịch vụ, phát sự kiện) dù map cuối cùng chỉ giữ một value.

Test điều phối phù hợp: cho hai thread cùng hoàn tất `containsKey` với kết quả false, cho chúng đợi barrier, sau đó thả để gọi `createValue`. Dùng `AtomicInteger` bên trong test để đếm số lần khởi tạo; invariant có thể là “side effect tạo mới chỉ xảy ra một lần”. Nếu yêu cầu chỉ là tính và chèn value khi vắng mặt, `ConcurrentHashMap.computeIfAbsent` có thể giải quyết một phần bài toán theo hợp đồng API. Tuy nhiên side effect bên ngoài cần xem xét retry, thất bại, idempotency và khóa giữa nhiều hệ thống.

Đặt barrier sau `containsKey` **mô phỏng** interleaving; không dùng barrier làm cách sửa race trong code nghiệp vụ. Với invariant nhiều resource, sửa có thể đòi transaction thay vì một operation atomic trên collection.

## 11. Phân biệt test lịch lỗi và test visibility/JMM

Không phải mọi lỗi concurrency đều có thể tái hiện xác định bằng một barrier:

- **Lost update/check–then–act:** có thể điều phối hai worker cùng đọc trạng thái cũ trước khi ghi, nên lịch sai dễ tạo xác định.
- **Visibility:** ví dụ một thread quay vòng đọc field thường trong khi thread khác ghi. Thêm latch/barrier giữa lần ghi và lần đọc có thể tạo happens-before rồi **che đi đúng vấn đề cần test**.
- **Reordering/unsafe publication:** nhiều kết quả phụ thuộc tối ưu JIT, kiến trúc CPU và JMM. Thêm `println`, `sleep`, logging, debugger hoặc khóa thường làm thay đổi thời điểm và kết quả.
- **Deadlock:** có thể dựng test với hai monitor và checkpoint để minh họa chu trình chờ, nhưng không nên để test suite thật treo vĩnh viễn. Dùng deadline, thread daemon/isolation phù hợp và quan sát `ThreadMXBean` khi cần.

Với các câu hỏi tinh vi về JMM như “hai phép đọc có thể thấy cặp giá trị nào?”, “một field không `volatile` có thể đọc stale value ra sao?”, test cần nhiều lịch cùng mô hình đánh giá kết quả hợp lệ, thường gọi là *litmus test*. Công cụ chuyên biệt như **OpenJDK jcstress** giúp kiểm tra nhiều outcome trên các runtime/kiến trúc, nhưng cũng không biến việc không thấy một outcome thành chứng minh nó bất khả thi. Không dùng kết quả của một máy làm định nghĩa JMM.

**Điểm khác nhau quan trọng:** *coordinated test* thường chứng minh một **lịch logic cụ thể đủ làm lộ bug**; *stress/litmus test* khám phá thêm lịch và outcome có thể xuất hiện. Để kết luận code an toàn, cần lập luận atomicity, happens-before và invariant, không chỉ dựa vào thống kê pass.

## 12. Kiểm thử đa giai đoạn với `Phaser` khi nào?

Một race phức tạp có nhiều pha: chuẩn bị dữ liệu → các worker cùng đọc → các worker áp dụng cập nhật → main kiểm tra. `Phaser` có thể điều phối nhiều vòng, cho phép đăng ký/hủy participant động và đợi tiến sang phase tiếp theo. Ví dụ ý tưởng:

```java
Phaser phaser = new Phaser(2);

// Cả hai worker hoàn thành giai đoạn chuẩn bị.
phaser.arriveAndAwaitAdvance();

// Cả hai worker hoàn thành giai đoạn đọc.
phaser.arriveAndAwaitAdvance();

// Sau đó mới sang giai đoạn ghi/cập nhật.
```

Đoạn này là **snippet** (cần import `java.util.concurrent.Phaser`, tạo participant đúng và chạy từ hai worker). `arriveAndAwaitAdvance()` chờ toàn bộ parties; nếu một worker không tới, test có thể kẹt. Với test nhỏ có một checkpoint, `CyclicBarrier` hoặc `CountDownLatch` thường dễ hiểu hơn. Chỉ tăng độ phức tạp khi số pha hoặc số participant thực sự cần.

## 13. Các lỗi thường gặp trong chính test concurrency

| Sai lầm | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Dùng `Thread.sleep(10)` để “chắc” worker kia chạy | Flaky vì scheduler không có hợp đồng thứ tự | Dùng latch/barrier với checkpoint cụ thể |
| Chạy hai task trên pool một worker nhưng đợi barrier hai parties | Task đầu giữ worker, task sau không được chạy | Cấp đủ worker hoặc đổi thiết kế đồng bộ |
| Đặt rendezvous trong `synchronized`/lock cần cho worker khác | Tự gây deadlock | Đặt checkpoint ngoài critical section |
| Chỉ quan sát stdout, không assert invariant | Có lỗi vẫn thông báo pass | Assert giá trị đúng sau `join/get` |
| Assert giá trị ngay sau `start()` | Main đọc quá sớm; còn data race trong test | Đợi worker kết thúc hoặc checkpoint đủ |
| Worker ném exception nhưng main không thu về | Test runner có thể bỏ qua lỗi | `Future.get`/cơ chế gom lỗi và fail test |
| Barrier không timeout | Test có thể treo mãi khi thiếu participant | Timed await, thông báo nguyên nhân rõ |
| Timed get nhưng không xử lý cleanup | Worker lỗi còn sống, làm ô nhiễm test khác | Cancel/interrupt, đóng executor, lifecycle hợp lý |
| Đếm confirmation bằng `int++` không đồng bộ | Chính biến đo cũng mất update | `AtomicInteger`/khóa cho công cụ đo |
| Hai worker thực ra dùng hai instance state riêng | Test không hề thử shared state | Truyền đúng **cùng** instance cho các task |
| Chỉ test phiên bản mô phỏng thuật toán | Production có code khác, lỗi chưa được sửa | Tăng kiểm thử trên operation thật |
| Gán “pass trên CI” là an toàn theo JMM | Một tập lịch hữu hạn không chứng minh mọi lịch | Dùng reasoning về đồng bộ và invariant |
| Thêm barrier để thử visibility | Barrier có thể tạo happens-before, che bug | Dùng litmus/stress phù hợp, review JMM |
| Dùng `assert` mà không bật `-ea` | Assertion có thể không chạy | JUnit assertion hoặc throw `AssertionError` trực tiếp |
| Phụ thuộc thứ tự hai dòng log của worker | Output thay đổi hợp lệ giữa các lần chạy | Gom kết quả, in từ main sau khi `Future.get` |

## 14. Quy trình thiết kế một test điều phối hữu ích

1. **Chọn một invariant cụ thể**: “chỉ một người được đặt một ghế”, “2 increment tạo ra 2”, “không có âm kho”.
2. **Chỉ ra state và operation thật**: object nào chia sẻ giữa threads? Đường đọc, kiểm tra và ghi nằm ở đâu?
3. **Vẽ một lịch thực thi gây sai** trên giấy (A đọc → B đọc → A ghi → B ghi). Nếu không vẽ được, cần điều tra logic trước.
4. **Đặt checkpoint tối thiểu** ngay tại ranh giới quyết định outcome. Dùng barrier nếu cần tất cả worker hoàn thành cùng một pha; latch khi cần main mở cổng.
5. **Cấp đủ participant và worker**; tránh checkpoint nằm trong lock cần cho người đến sau.
6. **Chọn kết quả quan sát an toàn**: dùng `AtomicInteger` cho số lần event, hoặc thu `Future` và assert sau khi hoàn thành. Không để instrumentation tạo thêm lost update.
7. **Đặt deadline và cleanup**: timed `await`, timed `get`, cleanup executor, báo lỗi chi tiết khi barrier broken hoặc timeout.
8. **Tái hiện lỗi với test reproduction**, phân biệt “test cố tình xác nhận bug” với “regression test phải pass khi code đúng”.
9. **Sửa đúng đơn vị atomicity**: đọc–sửa–ghi cùng lock, atomic operation, transaction hoặc tách ownership. Đừng sửa bằng delay hay tăng thread.
10. **Chạy regression và stress trên implementation thật**, review happens-before, visibility và trạng thái shared khác còn sót.
11. **Giữ test độc lập**: không tái sử dụng barrier bị broken, counter hoặc mutable static state giữa các case nếu không reset đúng cách.
12. **Ghi rõ khả năng của test**: nó chứng minh một interleaving cụ thể và expected outcome, không bảo đảm mọi lịch trên mọi CPU/JVM.

## 15. Câu hỏi tự kiểm tra

**1. Vì sao 100.000 lần chạy `count++` trên nhiều thread đều cho kết quả đúng vẫn chưa chứng minh thread-safe?** Vì test chỉ thực hiện một tập lịch hạn chế; không có happens-before bảo vệ phép read–modify–write, nên lost update vẫn có thể xuất hiện dưới interleaving khác.

**2. Muốn cả hai thread đọc `count=0` trước khi ghi, chọn gì?** `CyclicBarrier(2)` đặt sau bước đọc và trước bước ghi, hoặc hai latch để main đợi đủ hai tín hiệu đã đọc rồi mở cổng ghi.

**3. Vì sao không đặt `barrier.await()` trong method `synchronized` mà cả hai thread phải vào?** Thread đầu sở hữu monitor và chờ người thứ hai; người thứ hai chờ chính monitor đó nên không thể tới barrier. Test tự deadlock.

**4. Nếu dùng `volatile int count`, có hết lost update không?** Không. `volatile` bảo đảm visibility/ordering của từng truy cập, không làm chuỗi đọc–cộng–ghi trở thành một operation atomic.

**5. Vì sao cần dùng `AtomicInteger` đếm số lần xác nhận đặt ghế trong test?** Để phép đo không tự mất cập nhật. Nếu dùng `int confirmations++` không đồng bộ, chính số xác nhận có thể bị báo thiếu khiến race nghiệp vụ bị che khuất.

**6. `Future.get(timeout)` ném `TimeoutException` nghĩa là task đã dừng chưa?** Chưa chắc. Chỉ caller hết thời gian chờ; cần hủy/interrupt và quản lý lifecycle task, còn các I/O không nhất thiết đáp ứng interrupt ngay.

**7. Hai lần gọi `ConcurrentHashMap.containsKey` rồi `put` có an toàn cho invariant “chỉ tạo một lần” không?** Không nhất thiết. Các operation riêng thread-safe nhưng check–then–act chưa atomic; dùng API atomic phù hợp hoặc thiết kế tránh side effect lặp.

**8. Có thể dùng barrier để chứng minh một bug visibility do field thiếu `volatile` không?** Cẩn thận: bản thân barrier tạo đồng bộ/happens-before nên có thể che đúng lỗi muốn thử. Với JMM memory-ordering, cần thiết kế litmus test phù hợp.

## 16. Tổng kết áp dụng

Một test race tốt có **ba phần tách bạch**: (1) *invariant* mô tả điều đúng, (2) *interleaving* mô tả một đường đi cụ thể có thể phá điều đúng, và (3) *orchestration* khiến đường đi ấy xảy ra có kiểm soát. `CountDownLatch` và `CyclicBarrier` là công cụ test giúp quan sát hoặc ép các checkpoint; chúng không tự sửa logic production.

Đối với lỗi read–modify–write và check–then–act, điều phối từng bước cho phép tái hiện lost update/double booking gần như ngay lập tức trên một ví dụ chạy được, thay vì lặp may rủi hàng nghìn lần. Sau khi thấy lỗi, phải đưa **toàn bộ invariant** vào cùng cơ chế nguyên tử (`synchronized`, `Lock`, CAS/atomic operation hoặc ownership phù hợp), rồi kiểm tra lại trên operation thực. Race condition có thể được **phát hiện** nhờ test; tính đúng đắn lâu dài cần được **giải thích** bằng hợp đồng đồng bộ và Java Memory Model.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="race-conditions-011">Phương án sửa: ownership, locking, atomic, immutable state</a>

<details>
<summary>Xem chi tiết</summary>

# Sửa race condition trong Java: ownership, locking, atomic và immutable state

## Mục tiêu của bài học

**Race condition** xuất hiện khi tính đúng đắn của kết quả phụ thuộc vào thứ tự xen kẽ giữa các thao tác đồng thời nhưng chương trình thiếu một giao thức phối hợp đủ mạnh. Với trạng thái chia sẻ, dấu hiệu thường là **lost update**, kiểm tra điều kiện rồi hành động trên dữ liệu đã thay đổi, hoặc quan sát một tổ hợp các trường không nhất quán. Bài này trình bày bốn chiến lược sửa ở cấp **Java SE / Java Memory Model**, không sử dụng tính năng riêng của Spring:

1. **Ownership:** một owner có quyền cập nhật mutable state; bên khác gửi yêu cầu.
2. **Locking:** các thread truy cập qua cùng lock để bảo vệ một invariant (bất biến) nghiệp vụ.
3. **Atomic:** dùng thao tác nguyên tử như `incrementAndGet`, `compareAndSet` hoặc `getAndUpdate`.
4. **Immutable state:** không sửa object đã công bố; thay bằng snapshot mới thông qua một cơ chế publish/update an toàn.

Bạn sẽ học cách nhận diện phạm vi trạng thái cần bảo vệ, hiểu atomicity/visibility/ordering, chọn giải pháp theo invariant, và tự chạy các ví dụ **Java 17+** với kết quả có thể kiểm chứng.

## 1. Xác định race và invariant trước khi chọn giải pháp

Cho một counter đơn giản:

~~~java
class Counter {
    int count;

    void increment() {
        count++;
    }
}
~~~

`count++` về mặt logic gồm **đọc → cộng một → ghi**. Hai thread có thể xen kẽ:

~~~text
Ban đầu count = 0

Thread A              Thread B
--------              --------
đọc 0
                      đọc 0
tính 1                tính 1
ghi 1
                      ghi 1

Đã gọi increment 2 lần nhưng count cuối có thể là 1.
~~~

Đây là **lost update**. Nếu các truy cập vào biến thường thiếu happens-before và có ít nhất một ghi, còn có thể là **data race** theo Java Memory Model. Hai khái niệm liên quan nhưng khác nhau:

- *Data race*: truy cập bộ nhớ xung đột giữa các thread không được sắp thứ tự bằng happens-before cần thiết.
- *Race condition*: tính đúng đắn bị phụ thuộc timing/ordering; có thể tồn tại **ngay cả khi dùng `volatile` hoặc từng phép đọc/ghi là atomic**, vì cả chuỗi thao tác nghiệp vụ vẫn chưa nguyên tử.

Bài toán phải được mô tả bằng **invariant**. Chẳng hạn: số lần tăng thành công phải bằng giá trị counter; khi bán hàng `available >= 0`; và nếu ban đầu có 500 sản phẩm thì `available + sold == 500`. Với một tài khoản, thao tác “nếu đủ tiền thì trừ” phải được xem là **một đơn vị logic**. Khi invariant gồm hai trường, đồng bộ riêng từng trường không tự bảo vệ tổ hợp của chúng.

### Ví dụ Java 17: volatile vẫn mất cập nhật

Tạo tệp **`LostUpdateDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;

public class LostUpdateDemo {
    private static volatile int count = 0;

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch allowWrite = new CountDownLatch(1);

        Runnable increase = () -> {
            int observed = count;
            bothRead.countDown();

            try {
                allowWrite.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            count = observed + 1;
        };

        Thread a = new Thread(increase);
        Thread b = new Thread(increase);
        a.start();
        b.start();

        bothRead.await();
        allowWrite.countDown();
        a.join();
        b.join();

        System.out.println("expected=2 actual=" + count);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac LostUpdateDemo.java
java LostUpdateDemo
~~~

Stdout khi hai thread không bị interrupt:

~~~text
expected=2 actual=1
~~~

Hai latch cố tình đảm bảo mỗi thread **đã đọc giá trị 0** trước khi thread nào được phép ghi. `volatile` giúp đọc/ghi đơn lẻ có semantics visibility/ordering rõ ràng, nhưng **không biến ba thao tác đọc–tính–ghi thành một thao tác atomic**. Đây là thí nghiệm xác định thay vì dựa vào may rủi của scheduler.

## 2. Ba khía cạnh phải giải quyết: visibility, atomicity, ordering

| Thuộc tính | Vấn đề | Cách hiểu |
| --- | --- | --- |
| **Visibility** | Thread khác có nhìn thấy ghi mới không? | Giá trị chung phải được publish/đọc theo giao thức bộ nhớ hợp lệ. |
| **Atomicity** | Một cập nhật nghiệp vụ có bị xen vào giữa không? | Check-then-act phải được bảo vệ cùng nhau. |
| **Ordering** | Những thao tác nào được bảo đảm xuất hiện trước những thao tác khác? | Dựa vào happens-before chứ không đoán scheduler. |

Một số quan hệ **happens-before** trong Java:
- Unlock một monitor xảy ra trước lock tiếp theo trên **cùng monitor**.
- Ghi `volatile` xảy ra trước các lần đọc sau đó của cùng biến trong thứ tự đồng bộ.
- Hành động xảy ra trước `Thread.start()` happens-before hành động của thread được khởi chạy.
- Các hành động của thread xảy ra trước việc một thread khác tiếp tục sau `join()` thành công.
- Các API `ExecutorService`, `Future` và concurrent collections có các bảo đảm memory consistency được tài liệu hóa; ví dụ việc chuẩn bị dữ liệu trước `submit` happens-before hành động của task, và hành động task happens-before việc sử dụng kết quả sau `Future.get()`.

Chỉ có visibility **không bảo đảm atomicity**: `volatile int count` vẫn không sửa được `count++`. Đồng thời, một phép CAS đơn lẻ được thực hiện đúng có thể giải quyết cả visibility và cập nhật nguyên tử của **một vị trí trạng thái**, nhưng không tự tạo transaction trên hai đối tượng riêng rẽ.

## 3. Chiến lược 1 — Ownership / confinement

### 3.1. Một owner chịu trách nhiệm cập nhật

Thay vì cho nhiều thread tự sửa một mutable object, giao quyền ghi cho **một owner**. Các bên khác gửi command/message; owner xử lý lần lượt:

~~~text
Producer A --- command ---\
Producer B --- command ----> Queue --> Owner --> mutable state
Producer C --- command ---/

Chỉ owner sửa trạng thái. Producer không giữ đường ghi trực tiếp.
~~~

Nếu một state được tạo và chỉ được sử dụng trong đúng một thread, đó là **thread confinement**. Một `ExecutorService` với một worker hoạt động tại một thời điểm có thể trở thành bộ xử lý command tuần tự. Lưu ý `newSingleThreadExecutor()` bảo đảm **tối đa một task chạy mỗi lúc**, nhưng khi worker chết, executor có thể thay thế bằng Java thread mới. Thiết kế phụ thuộc **tính tuần tự**, không phụ thuộc một thread ID cố định mãi mãi.

### 3.2. Ví dụ chạy được: counter có một owner

Tạo file **`OwnerCounterDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class OwnerCounterDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService owner = Executors.newSingleThreadExecutor();
        final int[] counter = {0};

        try {
            List<Future<?>> writes = new ArrayList<>();
            for (int i = 0; i < 1_000; i++) {
                writes.add(owner.submit(() -> counter[0]++));
            }

            // Lệnh đọc cũng đi qua owner; main không đọc array trực tiếp.
            Future<Integer> result = owner.submit(() -> counter[0]);

            for (Future<?> write : writes) {
                write.get();
            }

            System.out.println("count=" + result.get());
        } finally {
            owner.shutdown();
            if (!owner.awaitTermination(1, TimeUnit.MINUTES)) {
                owner.shutdownNow();
            }
        }
    }
}
~~~

Chạy:

~~~bash
javac OwnerCounterDemo.java
java OwnerCounterDemo
~~~

Stdout:

~~~text
count=1000
~~~

`counter[0]++` không trở thành atomic về bản chất. Nó đúng ở đây vì **không có hai updater cùng lúc** và mọi task được thực thi tuần tự theo thứ tự submit của executor. Việc đọc diễn ra trong một task gửi sau tất cả tác vụ cập nhật. `Future.get()` giúp chờ và phát hiện lỗi task, tránh trường hợp test báo thành công khi một task ném exception.

Để ownership **thật sự** loại race, phải thỏa:
- Không có một phương thức ngoài owner hoặc một tham chiếu mutable bị rò rỉ cho bên khác để sửa state.
- Mọi cập nhật và lần đọc cần snapshot nhất quán đều đi qua owner hoặc qua immutable snapshot publish an toàn.
- Chuyển quyền sở hữu một buffer phải có giao thức rõ: bên gửi **không còn sửa buffer** sau khi bàn giao.
- Command queue cần quản lý capacity/backpressure; một owner xử lý chậm có thể làm hàng đợi tăng vô hạn nếu producer liên tục gửi.
- Không block owner bằng cách chờ `Future.get()` của một command khác chỉ **chính owner đó** mới có thể thực thi: đây là mẫu tự deadlock.

**Phù hợp:** trạng thái session riêng, actor-like component, bộ tổng hợp event, processing theo một key, logic nhiều trường có thể tuần tự hóa.

**Hạn chế:** owner có thể thành bottleneck; một command I/O rất chậm giữ các command sau đợi. Nếu chia trạng thái thành nhiều owner/partition, bất biến đi qua nhiều partition sẽ cần giao thức phối hợp bổ sung.

### 3.3. ThreadLocal không tự tạo ownership theo task

`ThreadLocal` cho mỗi Java thread một giá trị riêng, nhưng thread pool có thể tái sử dụng worker giữa nhiều task. Nếu giữ dữ liệu request trong ThreadLocal rồi quên `remove()` trong `finally`, task sau có thể nhìn thấy dữ liệu cũ. Hơn nữa một công việc có thể chuyển qua nhiều thread. Chỉ dùng ThreadLocal cho trạng thái đúng nghĩa gắn với thread, không đồng nhất nó với ownership của nghiệp vụ.

## 4. Chiến lược 2 — Locking

### 4.1. Bảo vệ toàn bộ invariant bằng cùng monitor

Khi nhiều thread phải trực tiếp đọc/ghi state, có thể bảo vệ critical section bằng `synchronized`:

~~~java
private final Object lock = new Object();
private int count;

void increment() {
    synchronized (lock) {
        count++;
    }
}

int currentCount() {
    synchronized (lock) {
        return count;
    }
}
~~~

Mutual exclusion đảm bảo một thread ở trong critical section tại một thời điểm trên monitor này. Unlock rồi lock cùng monitor còn tạo happens-before giúp đọc thấy dữ liệu đúng theo quy tắc bộ nhớ.

**Khóa cả đường đọc có liên quan.** Nếu writer dùng synchronized nhưng reader đọc một mutable field trực tiếp ngoài lock, reader có thể thiếu visibility hoặc thấy trạng thái giữa nhiều cập nhật. Một getter có khóa khác với setter cũng không tự giúp.

### 4.2. Check-then-act phải cùng một vùng khóa

Sai:

~~~java
if (account.getBalance() >= amount) {
    account.setBalance(account.getBalance() - amount);
}
~~~

Dù `getBalance()` và `setBalance()` riêng lẻ đều synchronized, sau bước kiểm tra có thể có một thread khác đổi số dư. Đúng hơn là một nghiệp vụ `withdrawIfEnough(amount)` kiểm tra và trừ dưới **cùng một lock**.

### 4.3. Ví dụ Java 17: invariant hai trường

Tạo file **`LockedInventoryDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LockedInventoryDemo {
    private static final class Inventory {
        private int available = 500;
        private int sold;

        synchronized boolean sellOne() {
            if (available == 0) {
                return false;
            }
            available--;
            sold++;
            return true;
        }

        synchronized Snapshot snapshot() {
            return new Snapshot(available, sold);
        }
    }

    private record Snapshot(int available, int sold) {}

    public static void main(String[] args) throws Exception {
        Inventory inventory = new Inventory();
        ExecutorService pool = Executors.newFixedThreadPool(10);

        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int worker = 0; worker < 10; worker++) {
                futures.add(pool.submit(() -> {
                    int successes = 0;
                    for (int i = 0; i < 100; i++) {
                        if (inventory.sellOne()) {
                            successes++;
                        }
                    }
                    return successes;
                }));
            }

            int successes = 0;
            for (Future<Integer> future : futures) {
                successes += future.get();
            }

            Snapshot s = inventory.snapshot();
            System.out.printf(
                    "available=%d sold=%d successes=%d invariant=%b%n",
                    s.available(), s.sold(), successes,
                    s.available() >= 0 && s.available() + s.sold() == 500);
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
javac LockedInventoryDemo.java
java LockedInventoryDemo
~~~

Stdout khi 10 task hoàn tất không có lỗi:

~~~text
available=0 sold=500 successes=500 invariant=true
~~~

**Vì sao đúng?** Mỗi lần `sellOne()` kiểm tra và thay đổi cả `available`/`sold` trong **một critical section**. Sau 500 lần bán thành công, tồn kho bằng 0; 500 nỗ lực còn lại trả `false`. `snapshot()` cũng giữ cùng monitor, nên người đọc thấy một phiên bản cùng lúc của hai trường. Đặt `synchronized` riêng trên mỗi phép đọc hoặc sửa một field sẽ không tương đương.

### 4.4. ReentrantLock và vấn đề liveness

`ReentrantLock` cung cấp thao tác có timeout, lock có thể bị interrupt và `Condition` để chờ predicate; hình thức điển hình là:

~~~java
lock.lock();
try {
    // Kiểm tra và cập nhật đầy đủ invariant.
} finally {
    lock.unlock();
}
~~~

Đây là đoạn minh họa trong một method có `lock` kiểu `ReentrantLock` đã khởi tạo. Không để exception làm bỏ quên `unlock()`. Khi cần giới hạn thời gian đợi lock, dùng `tryLock(timeout, unit)` và **xử lý trường hợp không lấy được lock**. `ReentrantLock` không được mặc định là nhanh hơn `synchronized`: hãy đo contention và chọn theo semantics cần thiết.

Những rủi ro của locking:
- **Deadlock:** Thread A giữ lock X, đợi Y; Thread B giữ Y, đợi X. Quy định **thứ tự lấy lock toàn cục** hoặc dùng một lock chung cho invariant có liên hệ.
- **Contention:** critical section quá dài khiến nhiều thread phải đợi; p99 latency có thể tăng.
- **Giữ lock khi gọi I/O chậm:** cả các thread khác cùng cần lock bị kẹt, dù CPU rảnh.
- **Đọc ngoài lock:** một bên không đi qua cùng giao thức sẽ phá bằng chứng correctness.
- **Callback trong lock:** method bên ngoài có thể tự gọi ngược hoặc đợi tài nguyên ngoài ý muốn; giảm phạm vi lock khi có thể nhưng vẫn phải giữ toàn bộ invariant nguyên tử.

Đặc biệt, `Thread.sleep()` trong khối `synchronized` **không tự nhả monitor**; để chờ điều kiện nên dùng `Object.wait()`/`Condition.await()` đúng hợp đồng, kiểm tra predicate bằng `while` sau khi được đánh thức.

## 5. Chiến lược 3 — Atomic operations

Các lớp `java.util.concurrent.atomic` như `AtomicInteger`, `AtomicLong`, `AtomicBoolean`, `AtomicReference` hỗ trợ thao tác nguyên tử lên **một vị trí trạng thái**. Ví dụ:

~~~java
java.util.concurrent.atomic.AtomicInteger counter =
        new java.util.concurrent.atomic.AtomicInteger();

counter.incrementAndGet();      // Một lần tăng nguyên tử.
int snapshot = counter.get();   // Một lần đọc theo atomic semantics.
~~~

Không tương đương:

~~~java
counter.set(counter.get() + 1); // Hai thao tác riêng, vẫn có lost update.
~~~

Một object Atomic không khiến **chuỗi gọi nhiều phương thức** tự trở thành một transaction.

### 5.1. Ví dụ atomic cập nhật có điều kiện

Tạo file **`AtomicInventoryDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicInventoryDemo {
    public static void main(String[] args) throws Exception {
        AtomicInteger available = new AtomicInteger(500);
        ExecutorService pool = Executors.newFixedThreadPool(10);

        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int worker = 0; worker < 10; worker++) {
                futures.add(pool.submit(() -> {
                    int successes = 0;
                    for (int i = 0; i < 100; i++) {
                        int previous = available.getAndUpdate(
                                current -> current > 0 ? current - 1 : current);
                        if (previous > 0) {
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

            System.out.printf(
                    "available=%d sold=%d invariant=%b%n",
                    available.get(), sold, available.get() + sold == 500);
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
javac AtomicInventoryDemo.java
java AtomicInventoryDemo
~~~

Stdout:

~~~text
available=0 sold=500 invariant=true
~~~

`getAndUpdate` trả **giá trị trước cập nhật**; mỗi phép bán thành công chỉ khi giá trị đó lớn hơn 0. Không bao giờ có một cập nhật đi từ 0 xuống -1. Hàm lambda cập nhật phải không có **side effect**: thư viện có thể gọi lại hàm khi compare-and-set thất bại do contention. Nếu trong lambda gửi hóa đơn, cập nhật database hay tăng một counter khác, những việc ấy có thể xảy ra nhiều hơn số lần cập nhật được chấp nhận.

Ở ví dụ này, `sold` được tính **sau khi mọi `Future.get()` hoàn tất**. Vì vậy kết quả cuối đúng, nhưng trong lúc các task vẫn đang xử lý, `available` và một counter `sold` riêng biệt **không nhất thiết tạo snapshot nguyên tử**.

### 5.2. Compare-and-set (CAS)

CAS kiểm tra “trạng thái hiện tại vẫn đúng bằng giá trị tôi vừa đọc chứ?” rồi cập nhật nguyên tử nếu đúng:

~~~text
old = đọc state
next = tính state mới từ old
CAS(old, next)
    thành công -> thao tác có hiệu lực
    thất bại  -> thread khác đã thay đổi state, đọc/tính lại
~~~

Ví dụ phương thức độc lập có thể đặt vào một class:

~~~java
static boolean takeOne(
        java.util.concurrent.atomic.AtomicInteger remaining) {
    while (true) {
        int current = remaining.get();
        if (current <= 0) {
            return false;
        }
        if (remaining.compareAndSet(current, current - 1)) {
            return true;
        }
    }
}
~~~

CAS bảo đảm giá trị bị giảm một lần nếu còn dương tại thời điểm thành công. Khi nhiều thread cạnh tranh, có thể phải retry nhiều lần; **lock-free không đồng nghĩa wait-free hoặc không bao giờ tốn CPU**.

### 5.3. Hai atomic riêng không bảo vệ một invariant xuyên hai trường

Sai khi cần snapshot nhất quán:

~~~java
AtomicInteger available = new AtomicInteger(500);
AtomicInteger sold = new AtomicInteger(0);

available.decrementAndGet(); // Atomic riêng.
sold.incrementAndGet();      // Atomic riêng.
~~~

Ở giữa hai câu lệnh, reader có thể thấy `available + sold == 499`. Nếu yêu cầu là “mọi observer phải thấy invariant luôn đúng”, lựa chọn phù hợp là một **lock** bao cả cập nhật và đọc, một **owner** quản lý mọi trường, hoặc gộp trường vào một **immutable snapshot** trong `AtomicReference`.

`LongAdder` hữu ích cho counter đo lường có contention cao, nhưng `sum()` khi đang cập nhật đồng thời **không phải snapshot nguyên tử**; nó không phù hợp trực tiếp với số dư hay invariant cần exact observation. `ConcurrentHashMap.compute` hỗ trợ thao tác cập nhật theo từng key; không tự bảo đảm transaction xuyên nhiều key.

## 6. Chiến lược 4 — Immutable state và atomic publication

### 6.1. “Immutable” có nghĩa gì?

Một object immutable thực sự không có trạng thái quan sát được bị thay đổi sau khi xây dựng. Java 17 hỗ trợ `record` để diễn đạt value object:

~~~java
record Inventory(int available, int sold) {}
~~~

Với hai trường `int`, mỗi instance là một snapshot không thể thay đổi. Đọc hai field từ **cùng instance** giúp thấy một phiên bản nhất quán mà không phải khóa chính instance đó.

Tuy nhiên:
- Record tạo trường final, **không tự deep-immutable**. `record Data(int[] values) {}` vẫn chứa mảng có thể bị sửa.
- `final List<Person>` không khiến từng `Person` mutable bên trong immutable.
- Bản thân immutable object không ngăn **hai writer công bố hai phiên bản mới cạnh tranh nhau**, làm mất cập nhật.
- Cần **safe publication** cho reference (ví dụ `volatile`, `AtomicReference`, synchronized, queue/Future có memory consistency), và nếu cần read-modify-write của nhiều writer thì dùng CAS/lock/owner.

### 6.2. Ví dụ Java 17: AtomicReference cập nhật snapshot chứa hai trường

Tạo tệp **`ImmutableSnapshotDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ImmutableSnapshotDemo {
    private record Inventory(int available, int sold) {}

    public static void main(String[] args) throws Exception {
        AtomicReference<Inventory> state =
                new AtomicReference<>(new Inventory(3, 0));
        ExecutorService pool = Executors.newFixedThreadPool(4);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() ->
                        state.updateAndGet(old ->
                                old.available() > 0
                                        ? new Inventory(
                                                old.available() - 1,
                                                old.sold() + 1)
                                        : old)));
            }

            for (Future<?> future : futures) {
                future.get();
            }

            Inventory snapshot = state.get();
            System.out.printf(
                    "available=%d sold=%d invariant=%b%n",
                    snapshot.available(), snapshot.sold(),
                    snapshot.available() >= 0
                            && snapshot.available() + snapshot.sold() == 3);
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
javac ImmutableSnapshotDemo.java
java ImmutableSnapshotDemo
~~~

Stdout:

~~~text
available=0 sold=3 invariant=true
~~~

Một lần `updateAndGet` có thể retry phép tính snapshot nhưng chỉ publish một kết quả theo semantics atomic của reference. Tám yêu cầu bán tranh chấp trên ba sản phẩm; ba yêu cầu đầu tiên được chấp nhận theo một thứ tự nào đó, năm yêu cầu còn lại không thay đổi state. Cặp `available/sold` luôn được tạo **cùng nhau** trong một instance.

**Quy tắc đọc snapshot:**

~~~java
Inventory view = state.get();
int available = view.available();
int sold = view.sold();
~~~

Đây là đoạn minh họa bên trong phạm vi nhìn thấy class `Inventory` và biến `state`. Không nên gọi `state.get()` riêng lẻ để lấy từng trường nếu cần phiên bản chung:

~~~java
int available = state.get().available();
int sold = state.get().sold(); // Có thể lấy một phiên bản mới hơn!
~~~

Khi writer cập nhật giữa hai lần `get()`, reader có thể ghép giá trị từ hai snapshot khác nhau, tạo ra invariant “sai” dù **mỗi snapshot riêng** luôn đúng.

### 6.3. Immutable + volatile, khi nào đủ?

Ví dụ field chỉ dùng để publish cấu hình mới sau khi đã xây xong:

~~~java
private volatile Config current = new Config("v1", 5);

record Config(String name, int limit) {}

void publish(Config next) {
    current = next;
}

Config snapshot() {
    return current;
}
~~~

Đây là đoạn **thành viên class**, không phải tệp chạy độc lập; trong Java, khi viết thành class thực tế hãy bố trí record và field ở vị trí hợp lệ. `volatile` đảm bảo visibility/publication của tham chiếu, và mỗi reader lấy một snapshot immutable. Nếu có **hai writer cùng đọc `current`, tính phiên bản mới dựa trên phiên bản cũ rồi gán lại**, thao tác read-modify-write vẫn có thể lost update. Dùng `AtomicReference.updateAndGet` hoặc lock cho trường hợp đó.

### 6.4. Defensive copy

Một record chứa mutable list hoặc array không tự immutable sâu. Nếu chỉ chứa `String` (immutable), có thể tạo bản sao cấu trúc:

~~~java
import java.util.List;

public record Names(List<String> values) {
    public Names {
        values = List.copyOf(values);
    }
}
~~~

Đây là một **record độc lập** lưu trong file `Names.java` nếu cần compile; `List.copyOf` tạo list không hỗ trợ sửa cấu trúc và tách khỏi việc sửa list đầu vào. Nó **không tự copy sâu** các phần tử mutable trong list. Cũng không thể dựa vào `Collections.unmodifiableList(original)` để tách khỏi nguồn dữ liệu vì wrapper chỉ ngăn sửa qua wrapper; nếu `original` vẫn bị sửa ở nơi khác thì view thay đổi.

## 7. So sánh lựa chọn

| Khía cạnh | Ownership | Locking | Atomic | Immutable snapshot |
| --- | --- | --- | --- | --- |
| Nguyên tắc | Một chủ sở hữu cập nhật | Bảo vệ critical section | CAS/thao tác nguyên tử | Thay giá trị mới, không sửa bản cũ |
| Phù hợp | Command queue, actor-like, state theo partition | Nhiều trường, logic check-then-act | Counter, flag, state gói trong một vị trí | Read-mostly, cấu hình, bộ state liên quan |
| Writer đồng thời | Đi qua owner tuần tự | Cạnh tranh cùng lock | Retry CAS khi cần | Cần atomic reference/lock/owner để thay |
| Reader nhất quán | Đọc qua owner/snapshot | Đọc qua cùng lock | `get()` trên atomic value | Một lần lấy tham chiếu snapshot |
| Rủi ro | Backlog, owner bottleneck | Contention, deadlock, giữ lock quá lâu | Retry nhiều, update function side effect | Allocation, mutable nested state, publish sai |
| Câu hỏi cần hỏi | Có đường bypass owner? | Tất cả đường truy cập có dùng cùng lock? | Invariant có nằm trong một giá trị? | Snapshot có immutable thật và publish đúng? |

Phương án có thể **kết hợp**: một owner quản lý mutable state và trả snapshot immutable; `AtomicReference` tham chiếu tới immutable record; lock bảo vệ việc xây và công bố cấu hình phức tạp. Chọn theo phạm vi invariant và cách đọc/ghi, không chọn theo khẩu hiệu “lock-free luôn nhanh”.

## 8. Từ bài toán đến thiết kế

### Trường hợp A — metric counter độc lập

Nếu chỉ cần tăng một biến nguyên tử, `AtomicLong.incrementAndGet()` đơn giản. Nếu workload ghi cực nhiều và cho phép số liệu snapshot xấp xỉ, `LongAdder` đáng đo thử. **Không** dùng hai lần `get/set` rồi hi vọng atomic.

### Trường hợp B — tồn kho không âm

Check-then-decrement là **một thao tác nghiệp vụ**. Dùng synchronized method, CAS loop trên AtomicInteger, hoặc owner xử lý message. Nếu đồng thời cập nhật `available` và `sold` và reader phải thấy đúng tổng ở mọi lúc, dùng lock chung hoặc immutable snapshot + AtomicReference.

### Trường hợp C — cấu hình có nhiều field

Tạo một record immutable chứa tất cả giá trị liên quan; cập nhật bằng cách thay toàn bộ reference `volatile` khi chỉ publish snapshot có sẵn, hoặc CAS/owner nếu có nhiều writer tính phiên bản mới từ phiên bản cũ. Reader lấy **một lần tham chiếu** rồi sử dụng thống nhất.

### Trường hợp D — shared mutable collection

Đừng cho rằng `Collections.synchronizedList` biến mọi vòng lặp thành atomic. Theo hợp đồng của wrapper, việc duyệt thường cần khóa đúng trên chính list:

~~~java
synchronized (list) {
    for (String item : list) {
        System.out.println(item);
    }
}
~~~

Đây là đoạn minh họa với `list` là synchronized list đã được tạo. Với read-mostly, có thể cân nhắc `CopyOnWriteArrayList`; với map cập nhật riêng từng key, cân nhắc `ConcurrentHashMap.compute`. Nếu invariant đi qua **nhiều phần tử/key**, các primitive riêng lẻ không đủ để gọi toàn bộ chuỗi là atomic.

### Trường hợp E — chuyển state giữa thread

Cho producer bàn giao buffer qua queue. Nếu producer tiếp tục sửa buffer sau khi `put()`, quyền sở hữu chưa thực sự độc quyền dù queue có memory consistency. Cần defensive copy, immutable value, hoặc giao thức rõ khi nào producer được phép sửa tiếp.

## 9. Những cách “sửa” dễ làm race còn nguyên

1. **`volatile` cho counter rồi giữ `counter++`.** Visibility tốt hơn, read-modify-write vẫn không atomic.
2. **Khóa bằng `synchronized(new Object())`.** Mỗi lần lại có monitor riêng; thread khác không bị chặn. Dùng một lock field ổn định được chia sẻ.
3. **Chỉ synchronized getter/setter.** `set(get()+1)` vẫn có khoảng trống cho thread khác chen vào. Gói cả nghiệp vụ thành một method atomic.
4. **Writer dùng lock, reader không dùng.** Reader có thể thấy phiên bản không nhất quán hoặc thiếu visibility; dùng cùng lock hoặc immutable snapshot publish an toàn.
5. **Mỗi field một lock khác nhau cho invariant tổng.** Từng field đúng nhưng reader có thể lấy một phần phiên bản cũ, một phần phiên bản mới.
6. **`AtomicInteger` nhưng gọi `set(get()+1)`.** Vẫn là hai thao tác riêng; dùng `incrementAndGet` hoặc CAS/update.
7. **Hai AtomicInteger rồi coi cả cặp là transaction.** Giữa hai lệnh cập nhật vẫn có trạng thái trung gian; dùng một snapshot nguyên tử.
8. **Callback CAS có side effect.** CAS có thể retry và gọi lại function; hàm tính state phải pure, không tự thanh toán/gửi email.
9. **Record chứa mảng mutable rồi gọi là immutable.** `final` chỉ giữ tham chiếu, không đóng băng phần tử; cần defensive copy hoặc đổi representation.
10. **`volatile` reference nhưng hai writer read-then-set.** Mất cập nhật vẫn có thể xảy ra; dùng CAS/lock/owner.
11. **Dùng `Thread.sleep` để đợi “thread kia cập nhật xong”.** Sleep không tạo happens-before; dùng `join`, latch, future hoặc primitive đúng mục đích.
12. **ThreadLocal gắn dữ liệu request trong pool không cleanup.** Worker tái sử dụng có thể làm rò dữ liệu sang task khác; gọi `remove()` trong `finally`.
13. **Để callback/network I/O chậm bên trong lock.** Giữ các thread khác chờ dài, làm tăng latency và rủi ro deadlock.
14. **Thấy một lượt stress test đúng rồi kết luận không có race.** Timing khác có thể lộ lỗi; cần giải thích invariant và quan hệ đồng bộ, kết hợp test để phát hiện regression.

### Một ví dụ deadlock cần tránh

~~~text
Thread A: giữ lock X rồi đợi lock Y
Thread B: giữ lock Y rồi đợi lock X

=> Không thread nào lấy được lock còn thiếu.
~~~

Để chuyển tiền giữa hai object có lock riêng, cần thứ tự khóa thống nhất (ví dụ theo ID duy nhất và quy tắc tie-break) hoặc một lock chung bảo vệ transaction nếu kiến trúc cho phép. Nếu state nằm trên nhiều process/host, primitive Java thread-level **không đủ** cho atomicity phân tán.

## 10. Cách kiểm chứng giải pháp

1. **Ghi rõ invariant bằng điều kiện quan sát được**, ví dụ `available >= 0` và `available + sold == initial`.
2. **Liệt kê mọi đường đọc/ghi**: caller, worker, callback, getter, map access, event handler và cleanup. Một đường bypass giao thức có thể phá toàn bộ.
3. **Xác định đơn vị thao tác nguyên tử**: command của owner, critical section, CAS hoặc lần thay AtomicReference thành công. Nếu một thao tác nhiều bước cần atomic, phải bao đúng cả chuỗi.
4. **Kiểm tra safe publication**: object được tạo xong trước khi thread khác đọc chưa? Có `volatile`/lock/queue/Future hoặc quan hệ happens-before phù hợp không?
5. **Thiết kế test tái hiện interleaving**, ví dụ CountDownLatch ở demo đầu thay vì sleep chờ may mắn. Đặt nhiều thread tranh chấp và kiểm tra invariant.
6. **Đọc kết quả task bằng `Future.get()`** để phát hiện exception. Chỉ đợi executor shutdown mà không kiểm tra Future có thể che lỗi task.
7. **Kiểm tra snapshot trong lúc cập nhật**, không chỉ trạng thái sau cùng. Một hệ thống có thể kết thúc với tổng đúng nhưng reader giữa chừng thấy tổng sai.
8. **Thử cancellation/failure**: khóa có nhả trong `finally` không? Owner xử lý queue backlog thế nào? CAS lambda có side effect không? Immutable graph có mutable data bị rò không?
9. **Đo performance sau khi đã đúng**: throughput thành công, p95/p99 latency, lock contention, queue depth, CAS retries, CPU/GC. Thực hiện warm-up, lặp nhiều lượt và giữ workload tương đương. Benchmark không thay thế chứng minh correctness.

Các công cụ như thread dump, Java Flight Recorder (JFR), profiler và metrics giúp quan sát contention hay blocked thread. Không có công cụ chạy thử nào bảo đảm tìm được mọi interleaving sai; dựa trên Java Memory Model và invariant mới là nền tảng.

## 11. Câu hỏi luyện tập

1. Hai thread cùng chạy `volatile int x; x++;`. Có còn race không? **Có**, read-modify-write chưa nguyên tử.
2. Vì sao `synchronized getBalance` và `synchronized setBalance` vẫn không đủ cho `if(getBalance()>=n) setBalance(getBalance()-n)`? **Lock bị nhả giữa các lời gọi, invariant không được bảo vệ trọn vẹn.**
3. Một owner chạy trên `newSingleThreadExecutor` có chắc Java thread ID không đổi suốt đời executor? **Không.** Executor đảm bảo tuần tự, có thể thay worker sau lỗi.
4. Tại sao callback trong `AtomicReference.updateAndGet` phải tránh side effect? **Có thể được chạy lại khi CAS cạnh tranh.**
5. `AtomicInteger available` và `AtomicInteger sold` riêng lẻ có đảm bảo `available + sold == 500` luôn đúng khi reader đọc đồng thời? **Không.**
6. Với immutable record trong `AtomicReference`, vì sao cần `Inventory s = state.get()` rồi đọc cả hai trường từ `s`? **Hai lần `get()` riêng có thể lấy hai phiên bản khác nhau.**
7. Record `Data(int[] numbers)` có deep immutable không? **Không**, array vẫn mutable.
8. Nếu state do một owner sửa, tại sao vẫn phải giới hạn queue command? **Producer có thể gửi nhanh hơn owner xử lý, gây backlog và OOM.**
9. Tại sao `Thread.sleep(100)` không sửa race? **Không tạo quan hệ happens-before hay mutual exclusion.**
10. Hai lock cần lấy cùng lúc phải làm gì để tránh deadlock? **Quy định thứ tự khóa nhất quán hoặc đổi kiến trúc khóa phù hợp.**
11. Khi nào `LongAdder` phù hợp hơn `AtomicLong`? **Với bộ đếm thống kê cập nhật rất nhiều, có contention và không yêu cầu tổng snapshot nguyên tử tại mọi thời điểm.**
12. Nếu một cách sửa làm kết quả đúng nhưng p99 latency tăng mạnh, cần đo gì? **Critical section, lock contention, queue depth, CPU, GC, offered load và throughput thành công.**

## Ghi nhớ

Bắt đầu từ **trạng thái chia sẻ và invariant nghiệp vụ**. **Ownership** loại bỏ writer cạnh tranh; **locking** bao trọn nghiệp vụ khi nhiều thread cùng cần access; **atomic** giải quyết cập nhật trên một biến hoặc một snapshot CAS; **immutable state** giúp các reader nhìn thấy phiên bản không thay đổi và kết hợp tốt với safe publication. Cần đảm bảo **atomicity, visibility, ordering, liveness** theo đúng phạm vi. Một từ khóa `volatile`, `synchronized` hay `AtomicInteger` dùng sai chỗ có thể khiến chương trình trông an toàn trong vài lần chạy mà vẫn chứa race.

## Tài liệu tham khảo

- [Java Language Specification 17, Chapter 17: Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Java Memory Model, happens-before, data race.
- [Java SE 17 — AtomicInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): atomic update và CAS.
- [Java SE 17 — AtomicReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html): cập nhật snapshot.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): thực thi task và memory consistency.
- [Java SE 17 — ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): atomic update theo key.
- [Java SE 17 — LongAdder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html): counter với contention.


</details>

- [Quay lại đầu trang](#back-to-top)

<a id="back-to-top"></a>

# Thread Safety

## Menu
- [Thế nào là một class thread-safe?](#thread-safety-001)
- [Stateless objects và tránh trạng thái chia sẻ](#thread-safety-002)
- [Immutability và immutable object graph](#thread-safety-003)
- [Thread confinement và ownership rõ ràng](#thread-safety-004)
- [Encapsulation của trạng thái cần đồng bộ](#thread-safety-005)
- [Safe publication qua final, volatile và khóa](#thread-safety-006)
- [Defensive copy và snapshot bất biến](#thread-safety-007)
- [Giữ nhất quán invariant khi nhiều trường liên quan](#thread-safety-008)
- [Thread-safe collection và compound action chưa atomic](#thread-safety-009)
- [Document thread-safety contract cho public API](#thread-safety-010)
- [Nhận diện mutable collaborator làm phá vỡ thread safety](#thread-safety-011)

## <a id="thread-safety-001">Thế nào là một class thread-safe?</a>

<details>
<summary>Xem chi tiết</summary>

# Thế nào là một class thread-safe?

Một **class thread-safe** (lớp an toàn luồng) là lớp có các đối tượng vẫn thực hiện **đúng hợp đồng đã công bố** khi nhiều thread truy cập đồng thời, với mọi cách xen kẽ thực thi hợp lệ, mà người gọi **không phải tự thêm đồng bộ hóa bên ngoài** cho những thao tác được lớp cam kết an toàn.

Điều kiện “đúng” không chỉ là không ném exception: dữ liệu phải giữ được bất biến, những cập nhật không được mất, và các kết quả phải tuân theo quy tắc hiển thị bộ nhớ của **Java Memory Model (JMM)**. Không nhất thiết mọi thread thấy kết quả theo một thứ tự cố định; hợp đồng có thể cho phép thứ tự không xác định, snapshot nhất quán hoặc trạng thái nhất quán cuối cùng. Điều quan trọng là mọi quan sát vẫn **phù hợp với hợp đồng**.

Ví dụ: một class cho phép tối đa hai lượt đặt chỗ có thể để bất kỳ hai trong ba thread giành được suất. Nhưng bất biến `used <= 2` phải luôn đúng. Nếu đôi khi cả ba đều thành công, class đã vi phạm thread safety.

## 1. Bắt đầu từ trạng thái, bất biến và ranh giới thao tác

Trước khi chọn `synchronized` hay `AtomicInteger`, cần nhận diện:

- **Mutable shared state:** trường nào có thể thay đổi và được nhiều thread dùng? Có biến `static`, object được truyền vào, collection hay dependency bên ngoài?
- **Invariant:** những quan hệ nào phải luôn đúng? Ví dụ `0 <= used <= limit` hoặc `available + reserved == total`.
- **Atomic operation:** thao tác nào phải thực hiện như một đơn vị không thể chen ngang về mặt logic? “Kiểm tra còn suất rồi đặt” là một thao tác, dù mã có nhiều câu lệnh.
- **Publication:** đối tượng và kết quả được công bố cho thread khác bằng cách nào? Việc xây dựng hoàn tất chưa, có quan hệ *happens-before* hợp lệ chưa?
- **Encapsulation:** caller có thể lấy tham chiếu tới collection/array mutable nội bộ và sửa ngoài vùng bảo vệ không?

Class an toàn cần giữ đúng bất biến **trên tất cả đường đọc/ghi có liên quan**, bao gồm public method, callback, thao tác khởi tạo và đối tượng được chia sẻ cho bên ngoài.

| Hiện tượng | Vì sao nguy hiểm? |
|---|---|
| *Lost update* | Hai thread cùng đọc giá trị cũ và cùng ghi, làm mất một lượt cập nhật. |
| *Check-then-act race* | Hai thread thấy cùng điều kiện đúng rồi cùng thực hiện hành động chỉ cho phép một lần. |
| *Stale read* | Thread đọc dữ liệu mà không có quan hệ đồng bộ bảo đảm thấy cập nhật cần thiết. |
| *Inconsistent snapshot* | Đọc nhiều trường giữa lúc thread khác đang cập nhật, tạo tổ hợp không hợp lệ. |
| *Representation exposure* | Caller được sửa trực tiếp state nội bộ, vượt qua khóa/bất biến của class. |

**Thread-safe không phải một modifier đặc biệt.** `final`, `volatile` hoặc `synchronized` trên vài thành phần chưa đủ để chứng minh toàn bộ class an toàn.

## 2. Những chiến lược chính

### Stateless: không có mutable state chia sẻ

```java
public final class StatelessFormatter {
    public String greet(String name) {
        return "Hello, " + name.trim();
    }
}
```

Đây là class hợp lệ nhưng chỉ là đoạn minh họa (không có `main`). Mỗi lần gọi sử dụng tham số và biến tạm trên stack của lần gọi. `String` immutable; class không giữ field thay đổi. Nhiều thread có thể gọi `greet` đồng thời.

**Ngoại lệ:** “không có field instance” chưa đủ nếu phương thức bí mật dùng một `static HashMap` mutable, bộ đếm toàn cục hoặc dependency không an toàn. Stateless thật sự phải không phụ thuộc vào shared mutable state thiếu phối hợp.

### Immutable: trạng thái không thể bị sửa sau construction

Immutable object thường là lựa chọn rất mạnh để chia sẻ: dữ liệu được xây dựng hoàn chỉnh, không có setter hoặc đường tham chiếu cho phép sửa sâu, và được công bố theo quy tắc JMM. `final` trên một field tham chiếu chỉ cố định **tham chiếu**, không làm đối tượng đằng sau nó bất biến.

Một `record` có `List<String>` chưa tự trở thành immutable nếu giữ nguyên một `ArrayList` caller truyền vào. Cần sao chép phòng vệ, và nếu phần tử mutable thì còn cần sao chép/thiết kế sâu hơn. Ví dụ chạy được ở mục 5.

### Mutable nhưng được phối hợp đúng

Một mutable class vẫn có thể thread-safe nếu toàn bộ state chung được truy cập theo một giao thức nhất quán, chẳng hạn:

- `synchronized` hoặc `ReentrantLock` bao trùm một vùng găng (*critical section*) bảo vệ invariant;
- `AtomicInteger` / `AtomicReference` / compare-and-set (CAS) để cập nhật trạng thái nguyên tử phù hợp;
- collection concurrent với các phép thao tác nguyên tử **đúng hợp đồng cụ thể**;
- thay toàn bộ immutable snapshot qua một tham chiếu được công bố/cập nhật nguyên tử.

Không có một cơ chế tối ưu cho mọi tình huống; điều cần bảo vệ là **thao tác logic** và những quan sát do API công bố.

## 3. Visibility không đồng nghĩa atomicity: volatile vẫn có thể lỗi

`volatile` cung cấp quy tắc hiển thị/thứ tự đọc ghi cho biến đó. Nhưng `count++` không phải một phép cập nhật nguyên tử; nó gồm đọc, tính, ghi. Nếu hai thread đều đọc `0` rồi cùng ghi `1`, một lần tăng bị mất.

Ví dụ sau dùng latch để buộc hai thread cùng đọc giá trị trước khi ghi, khiến lỗi dễ tái hiện hơn ví dụ chỉ chạy hàng triệu vòng.

Tạo tệp **`VolatileCounterRace.java`**:

```java
import java.util.concurrent.CountDownLatch;

public class VolatileCounterRace {
    static final class Counter {
        private volatile int value;
        private final CountDownLatch bothRead = new CountDownLatch(2);

        void increment() throws InterruptedException {
            int oldValue = value;
            bothRead.countDown();
            bothRead.await();
            value = oldValue + 1;
        }

        int get() {
            return value;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        Runnable task = () -> {
            try {
                counter.increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread first = new Thread(task);
        Thread second = new Thread(task);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("Expected increments: 2");
        System.out.println("Actual value: " + counter.get());
    }
}
```

```bash
javac VolatileCounterRace.java
java VolatileCounterRace
```

Đầu ra trong lần chạy bình thường, không bị interrupt:

```text
Expected increments: 2
Actual value: 1
```

Hai thread cùng đọc `0` trước khi latch mở; sau đó cả hai ghi `1`. `volatile` không gộp hai bước đọc–sửa–ghi. Latch chỉ phục vụ thí nghiệm để tạo lịch xen kẽ; class `Counter` vẫn không phải bộ đếm thread-safe. Khi chỉ cần tăng một biến, `AtomicInteger.incrementAndGet()` hoặc một vùng `synchronized` phù hợp có thể giải quyết.

## 4. Mutable class an toàn: khóa toàn bộ check-then-act

Giả sử class giới hạn số lượt được cấp: `0 <= used <= limit`. Cần kiểm tra còn suất và tăng used trong cùng một thao tác nguyên tử về mặt logic.

Tạo tệp **`SafeQuota.java`**:

```java
import java.util.concurrent.atomic.AtomicInteger;

public final class SafeQuota {
    private final int limit;
    private int used;

    public SafeQuota(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0");
        }
        this.limit = limit;
    }

    public synchronized boolean tryAcquire() {
        if (used >= limit) {
            return false;
        }
        used++;
        return true;
    }

    public synchronized int used() {
        return used;
    }

    public int limit() {
        return limit;
    }

    public static void main(String[] args) throws InterruptedException {
        SafeQuota quota = new SafeQuota(2);
        AtomicInteger accepted = new AtomicInteger();

        Runnable task = () -> {
            if (quota.tryAcquire()) {
                accepted.incrementAndGet();
            }
        };

        Thread a = new Thread(task);
        Thread b = new Thread(task);
        Thread c = new Thread(task);
        a.start();
        b.start();
        c.start();
        a.join();
        b.join();
        c.join();

        System.out.println("Accepted: " + accepted.get());
        System.out.println("Used: " + quota.used());
        System.out.println("Limit: " + quota.limit());
    }
}
```

```bash
javac SafeQuota.java
java SafeQuota
```

Kết quả:

```text
Accepted: 2
Used: 2
Limit: 2
```

**Điều tạo ra bảo đảm:** `used` là `private`; mọi đường đọc/ghi `used` sử dụng monitor của cùng đối tượng `quota`. Bên trong `tryAcquire()`, không thread nào có thể chen giữa phép kiểm tra `used >= limit` và `used++`. `used()` cũng sử dụng monitor để đọc trạng thái nhất quán. `limit` không đổi sau constructor và đã được kiểm tra hợp lệ.

Ba thread cạnh tranh nên không bảo đảm thread nào thắng. Tuy nhiên, đúng hai lời gọi nhận `true`. `accepted` là `AtomicInteger` để chương trình demo đếm lượt thành công, và `join()` bảo đảm main chỉ đọc khi cả ba thread hoàn tất.

`synchronized` trong instance method khóa **chính instance đó**. Hai đối tượng `SafeQuota` khác nhau có monitor và trạng thái riêng: chúng không tự phối hợp thành một hạn mức toàn cục. Nếu một invariant là chung của nhiều đối tượng/JVM, phải thiết kế điểm sở hữu/trọng tài chung tương ứng.

## 5. Immutable class và defensive copy

Tạo tệp **`ImmutableSettings.java`**:

```java
import java.util.ArrayList;
import java.util.List;

public class ImmutableSettings {
    public record Settings(List<String> roles) {
        public Settings {
            roles = List.copyOf(roles);
        }
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>();
        source.add("reader");

        Settings settings = new Settings(source);
        source.add("writer");

        System.out.println("Roles: " + settings.roles());
        try {
            settings.roles().add("admin");
        } catch (UnsupportedOperationException e) {
            System.out.println("External modification rejected");
        }
    }
}
```

```bash
javac ImmutableSettings.java
java ImmutableSettings
```

Kết quả:

```text
Roles: [reader]
External modification rejected
```

Compact constructor của record thay tham số `roles` bằng danh sách do `List.copyOf` tạo ra. Caller sửa `source` không ảnh hưởng trạng thái record; accessor `roles()` không cung cấp một list có thể thêm phần tử. Vì các phần tử là `String` immutable, dữ liệu có thể được đọc đồng thời an toàn sau khi tạo và công bố đúng cách.

**Đừng tổng quát hóa quá mức:** `List.copyOf` không sao chép sâu phần tử. Với `List<MutableOrder>`, thread khác vẫn có thể sửa trường trong từng `MutableOrder` nếu còn tham chiếu. `Collections.unmodifiableList(original)` chỉ là view không cho sửa qua view; nếu `original` thay đổi, view có thể quan sát thay đổi đó. Immutable phải xét **toàn bộ đồ thị trạng thái có thể truy cập**.

## 6. AtomicInteger: thread-safe cho phép cập nhật đơn

Tạo tệp **`AtomicCounter.java`**:

```java
import java.util.concurrent.atomic.AtomicInteger;

public final class AtomicCounter {
    private final AtomicInteger count = new AtomicInteger();

    public int increment() {
        return count.incrementAndGet();
    }

    public int value() {
        return count.get();
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicCounter counter = new AtomicCounter();
        Runnable task = () -> {
            for (int i = 0; i < 1000; i++) {
                counter.increment();
            }
        };

        Thread a = new Thread(task);
        Thread b = new Thread(task);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("Value: " + counter.value());
    }
}
```

```bash
javac AtomicCounter.java
java AtomicCounter
```

Kết quả:

```text
Value: 2000
```

`incrementAndGet()` là một phép tăng nguyên tử; sau hai lần `join()`, main quan sát đúng 2.000 lần cập nhật. Nhưng mã `if (counter.value() < max) counter.increment()` bên phía caller vẫn **không nguyên tử như một thao tác hợp thành**. Hai thread có thể cùng kiểm tra thành công rồi cùng tăng.

Khi chỉ có một biến chứa toàn bộ invariant, CAS loop với `compareAndSet(old, new)` có thể kết hợp kiểm tra và cập nhật: nếu thread khác đổi giá trị giữa chừng, CAS thất bại và phải **đọc lại, kiểm tra lại điều kiện**. Với `AtomicReference.updateAndGet`, hàm update có thể được gọi lại khi tranh chấp nên phải tránh side effect như gửi dữ liệu, tạo hóa đơn hay thao tác ngoài JVM trong lambda.

## 7. Thread-safe từng method không bảo đảm thao tác của client

Xét class có `get()` và `increment()` đều synchronized. Đoạn phía client dưới đây vẫn sai nếu muốn giới hạn số lần tăng:

```java
// Mã minh họa KHÔNG an toàn; counter/limit được giả định đã khai báo.
if (counter.get() < limit) {
    counter.increment();
}
```

Hai thread có thể đều hoàn thành `get()` và cùng thấy “chưa tới giới hạn” trước khi bất kỳ thread nào gọi `increment()`. Từng method an toàn, nhưng **chuỗi check → act không phải một transaction nguyên tử**. Thiết kế API nên đưa trọn quyết định vào method như `SafeQuota.tryAcquire()`, đồng thời nói rõ những gì class cam kết.

Ví dụ tương tự với concurrent collection:

```java
// Mã minh họa, cache là ConcurrentHashMap<String, String>.
if (!cache.containsKey("key")) {
    cache.put("key", expensiveCreate());
}
```

`containsKey` và `put` đều có bảo đảm thread safety riêng, nhưng hai thread có thể cùng không thấy key rồi cùng gọi `expensiveCreate()`. Trong `ConcurrentHashMap`, `computeIfAbsent(key, mappingFunction)` có hợp đồng nguyên tử phù hợp cho **tính giá trị nếu key chưa có**. Hàm mapping nên ngắn, không tự thay đổi chính map đang tính và không dùng tác dụng phụ không thể lặp lại trong trường hợp ràng buộc nghiệp vụ yêu cầu hơn một thao tác map.

`putIfAbsent(key, expensiveCreate())` cũng cần thận trọng: Java đánh giá `expensiveCreate()` **trước khi** gọi `putIfAbsent`, nên việc tạo tài nguyên có thể lặp dù phép gắn giá trị là nguyên tử.

## 8. Bất biến nhiều field cần một giao thức nhất quán

Giả sử có `available` và `reserved`, bất biến:

```text
available >= 0
reserved >= 0
available + reserved == total
```

Nếu thread giảm `available` trước rồi mới tăng `reserved`, thread khác đọc giữa hai phép gán có thể thấy tổng sai. Dùng hai `AtomicInteger` cho hai field **không tự tạo một snapshot nguyên tử cho cả hai**.

Một giải pháp đơn giản là bảo vệ mọi cập nhật và phép đọc trạng thái ghép bằng cùng monitor:

```java
// Trích đoạn method trong một class, không độc lập.
private int available = 10;
private int reserved;

public synchronized boolean reserve() {
    if (available == 0) {
        return false;
    }
    available--;
    reserved++;
    return true;
}

public synchronized String snapshot() {
    return "available=" + available + ", reserved=" + reserved;
}
```

`snapshot()` và `reserve()` dùng cùng khóa instance, nên không thể quan sát trạng thái đang cập nhật dở. Một phương án khác là đóng gói **tất cả** trường liên quan vào một object immutable rồi cập nhật toàn bộ tham chiếu qua `AtomicReference.compareAndSet` (thay cả snapshot tại một thời điểm). Chọn cách phù hợp độ phức tạp và tải thực tế.

Với `ReentrantLock`, luôn giải phóng khóa trong `finally`:

```java
lock.lock();
try {
    // Kiểm tra và cập nhật cùng invariant dưới một lock.
} finally {
    lock.unlock();
}
```

Đây là trích đoạn minh họa, giả định `lock` là một `ReentrantLock` đã khởi tạo. Hai thao tác bảo vệ bằng **hai khóa khác nhau** không loại trừ nhau. Không gọi code ngoài không kiểm soát hoặc làm I/O lâu trong vùng khóa nếu không cần thiết: việc giữ khóa có thể làm tăng contention hoặc deadlock.

## 9. Safe publication theo Java Memory Model

Bản thân object được xây dựng đúng vẫn cần được **công bố** cho thread khác qua một đường có bảo đảm bộ nhớ phù hợp. Các cơ chế thường gặp:

- Khởi tạo static của class theo hợp đồng JVM;
- ghi/đọc `volatile` hoặc `AtomicReference`;
- giải phóng/chiếm lại cùng monitor hoặc lock;
- truyền object cho thread mới sau khi đã tạo hoàn chỉnh: các thao tác trước `Thread.start()` happens-before các thao tác bên trong thread mới;
- trao đổi qua cấu trúc concurrent có hợp đồng đồng bộ như `BlockingQueue`.

Các trường `final` còn có bảo đảm khởi tạo đặc biệt khi object **được xây dựng đúng cách**. Tránh để `this` thoát khỏi constructor:

```java
// Đoạn minh họa NGUY HIỂM trong constructor.
public SomeClass() {
    new Thread(() -> useFieldsOfThis()).start();
    importantField = 42;
}
```

Worker có thể truy cập `importantField` trước khi constructor gán giá trị. Quan hệ từ `start()` chỉ bao trùm những hành động **trước** lời gọi start, không bảo vệ phép gán sau đó. Hãy xây dựng đầy đủ object trước khi công bố và khởi động thread dùng nó.

Đừng hiểu `final` trên một tham chiếu là “mọi dữ liệu phía sau tham chiếu đều bất biến”; `volatile` trên tham chiếu cũng không làm các thay đổi sâu trong object thành một phép cập nhật nguyên tử.

## 10. Encapsulation: không lộ collection mutable

Một thiết kế nhìn như dùng khóa đầy đủ có thể bị phá vỡ bằng getter trả về tham chiếu mutable thật:

```java
// Trích đoạn minh họa KHÔNG an toàn.
private final List<String> tasks = new ArrayList<>();

public List<String> tasks() {
    return tasks; // Caller có thể tự add/remove không theo giao thức khóa.
}
```

Nếu muốn xuất một snapshot độc lập khi mọi mutation đang được bảo vệ bằng monitor instance, có thể làm:

```java
public synchronized List<String> tasksSnapshot() {
    return List.copyOf(tasks);
}
```

Hai trích đoạn này cần import `java.util.List` và `java.util.ArrayList`; ví dụ chỉ dùng phần tử `String` immutable. Nếu phần tử mutable thì còn phải tránh lộ tham chiếu tới chính phần tử đó.

Tương tự, `Collections.synchronizedList` đồng bộ các method riêng nhưng một **lượt duyệt/iteration** cần được phối hợp theo hợp đồng collection. `CopyOnWriteArrayList` lại có iterator snapshot, đổi chi phí ghi để có lượt duyệt ổn định. Đừng suy một cách chung chung rằng “mọi thao tác trên mọi concurrent collection đều nguyên tử như một giao dịch”.

## 11. Phân biệt các thuộc tính concurrency

| Thuộc tính | Ý nghĩa | Điều cần nhớ |
|---|---|---|
| **Thread safety** | Truy cập concurrent vẫn đúng contract và invariant. | Mục tiêu tổng thể của class. |
| Atomicity | Một thao tác không bị xen ngang theo nghĩa logic được cam kết. | Cần cho phép cập nhật nhiều bước. |
| Visibility | Thread khác thấy hiệu ứng bộ nhớ theo JMM. | Cần nhưng chưa đủ cho check-then-act. |
| Immutability | Không sửa được trạng thái sau khi tạo. | Một chiến lược chia sẻ an toàn khi xây dựng/công bố đúng. |
| Linearizability | Mỗi thao tác có thể coi như xảy ra tại một điểm giữa lời gọi và trả về. | Hợp đồng mạnh hữu ích, nhưng không bắt buộc mọi API concurrent đều có. |
| Liveness | Hệ thống/thread có tiến triển, tránh chờ vô hạn. | Khác với safety; khóa có thể bảo vệ dữ liệu nhưng gây deadlock. |
| Lock-free | Hợp đồng tiến triển không phụ thuộc một khóa loại trừ truyền thống. | Không phải từ đồng nghĩa của thread-safe. |
| Reentrancy | Có thể gọi vào lại hàm trong những điều kiện nhất định. | Không mặc nhiên chứng minh an toàn dữ liệu shared. |

Một class có thể thread-safe mà **thứ tự kết quả không cố định**; ví dụ không biết thread nào lấy được suất, nhưng không ai được vượt hạn mức. Ngược lại, code có lịch chạy luôn giống nhau trong vài phép thử vẫn có thể race khi tải hoặc lịch chạy thay đổi.

## 12. Những lỗi khiến class không an toàn dù “có khóa”

1. Khóa setter nhưng getter đọc field không đồng bộ.
2. Mỗi method dùng một object khóa khác nhau; `synchronized(new Object())` mỗi lần gọi không phối hợp giữa các thread.
3. Tách kiểm tra và cập nhật thành hai public method cho caller tự ghép.
4. Trả mutable list/array/map nội bộ cho caller; caller sửa ngoài khóa.
5. Để `this` thoát trong constructor trước khi state hoàn chỉnh.
6. Cho phép subclass override các method bảo vệ invariant mà không xác định rõ contract kế thừa.
7. Gọi callback không kiểm soát khi đang giữ khóa, có thể tạo vòng phụ thuộc khóa.
8. Giả định `volatile` làm nguyên tử nhiều field hoặc `AtomicInteger` bảo vệ cả một transaction nhiều trạng thái.
9. Nghĩ mọi dependency cũng thread-safe chỉ vì lớp bao bên ngoài có khóa.
10. Dựa vào thứ tự `Thread.start()` hoặc `Thread.sleep()` để giả định kết quả theo một thứ tự cố định.

Đối với class có `static` shared mutable state, hãy xét thêm **phạm vi nhiều instance**. Hai instance của class có hai monitor `this` khác nhau; chúng không bảo vệ được cùng một trường `static` nếu chỉ khóa theo instance. Dùng một chiến lược thích hợp cho state thực sự chung hoặc loại bỏ shared global state.

## 13. Quy trình thiết kế và kiểm thử class thread-safe

**Thiết kế:** trước hết ghi ra invariant và những method public cam kết. Xác định state mutable, ai có thể giữ alias và mọi đường đọc/ghi. Nếu có thể, ưu tiên stateless hoặc immutable. Nếu phải mutable, chọn một cơ chế bảo vệ tương ứng phạm vi invariant. Đưa trọn quyết định nghiệp vụ vào một method như `tryAcquire()`, bảo vệ cả đọc/ghi liên quan, công bố object theo JMM và nói rõ semantics của API khi concurrent.

**Kiểm thử:** xây dựng những lịch xen kẽ có chủ đích bằng `CountDownLatch` hoặc `CyclicBarrier`, chờ mọi worker bằng `join()` hay primitive thích hợp rồi kiểm tra invariant và số tác dụng phụ. Lặp dưới tải khác nhau để tăng độ bao phủ. Một test chạy hàng nghìn lần không thấy lỗi **không thể tự chứng minh** class thread-safe; kết luận dựa vào cả thiết kế/happens-before và chứng cứ kiểm thử.

Đừng dùng `Thread.sleep()` để tự “chứng minh” hai công việc đã xong hay giả định race không thể xảy ra. Nếu nghi deadlock hoặc contention, xem thread dump và phân tích lock acquisition. Một class có safety vẫn có thể gặp vấn đề liveness; cần đánh giá thời hạn chờ, interrupt/cancellation và thứ tự chiếm khóa.

**Giới hạn phạm vi:** một monitor trong một JVM không khóa được file hay database đang được JVM khác ghi. Bất biến xuyên tiến trình cần thao tác nguyên tử, transaction hoặc unique constraint của hệ thống sở hữu tài nguyên đó. Việc đồng bộ đúng trong Java chỉ là một phần nếu dữ liệu còn được sửa bên ngoài tiến trình.

## 14. Câu hỏi tự kiểm tra

1. **`private volatile int count` với `count++` có thread-safe không?** Không. `volatile` không biến đọc–sửa–ghi thành nguyên tử.
2. **Vì sao `SafeQuota.tryAcquire()` an toàn?** Vì kiểm tra và tăng `used` được thực hiện dưới cùng monitor; mọi đường truy cập `used` cần tuân thủ giao thức đó.
3. **Nếu hai method đều synchronized, việc gọi `get()` rồi `increment()` có nguyên tử không?** Không. Thread khác có thể chen giữa hai lần gọi.
4. **Class không có field instance có chắc an toàn không?** Không, nếu nó sử dụng static mutable state hoặc dependency không an toàn.
5. **`final List<String>` có luôn immutable không?** Không; danh sách phía sau tham chiếu vẫn có thể mutable hoặc bị thay đổi qua alias khác.
6. **`List.copyOf` có sao chép sâu object trong danh sách không?** Không. Với phần tử mutable cần chiến lược thêm.
7. **Hai `AtomicInteger` có tự bảo vệ invariant liên quan cả hai trường không?** Không; từng phép cập nhật nguyên tử nhưng toàn bộ trạng thái ghép chưa chắc nhất quán.
8. **Một class thread-safe có thể deadlock không?** Có. Safety về dữ liệu và liveness là hai thuộc tính khác nhau.
9. **`ConcurrentHashMap` có biến mọi đoạn nhiều lời gọi lên map thành transaction không?** Không; cần chọn thao tác nguyên tử theo hợp đồng, chẳng hạn `computeIfAbsent` cho trường hợp thích hợp.
10. **Khi nào caller phải dùng khóa ngoài một class?** Khi cần phối hợp một chuỗi thao tác vượt phạm vi atomicity mà class cam kết và hợp đồng có cách hỗ trợ; tốt hơn nên cân nhắc API hợp thành có bảo vệ invariant ngay trong class.

**Ghi nhớ:** class thread-safe **giữ đúng hợp đồng khi nhiều thread truy cập đồng thời**. Hãy xác định trạng thái và invariant, bảo vệ toàn bộ thao tác logic, đóng gói mutable state, công bố dữ liệu an toàn theo JMM, và phân biệt tính an toàn của *mỗi method* với tính nguyên tử của *chuỗi thao tác do caller tự ghép*.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-002">Stateless objects và tránh trạng thái chia sẻ</a>

<details>
<summary>Xem chi tiết</summary>

# Stateless objects và tránh trạng thái chia sẻ trong Java Concurrency

## 1. Vì sao thiết kế stateless giúp thread-safe?

Một tình huống quen thuộc: nhiều thread cùng gọi một đối tượng dùng chung. Nếu mỗi lời gọi ghi dữ liệu tạm của chính nó vào một field của đối tượng, lời gọi sau có thể ghi đè dữ liệu mà lời gọi trước chưa sử dụng xong. Đó là một dạng race condition thường gặp, dù từng phương thức trông rất đơn giản.

**Thiết kế stateless (không giữ trạng thái thay đổi theo từng tác vụ)** giúp loại bỏ nhiều nguồn race ngay từ cấu trúc chương trình: mỗi lần gọi nhận đầu vào, tính toán bằng biến cục bộ và trả kết quả; dữ liệu đang xử lý không được lưu trong các instance/static fields dùng chung. Khi không có mutable state chung, không cần khóa chỉ để bảo vệ dữ liệu trung gian của các lời gọi độc lập.

Sau bài này, bạn sẽ:

- Phân biệt **stateless object**, **immutable object**, **thread-confined state** và **thread-safe stateful object**.
- Xác định đúng nơi tồn tại shared mutable state, kể cả static field, collection, đối tượng được truyền tham chiếu, lambda capture và dependency bên ngoài.
- Nhận diện lỗi lưu dữ liệu theo tác vụ vào field; hiểu tại sao synchronized từng getter/setter không cứu được chuỗi hành động phụ thuộc nhau.
- Chuyển thiết kế sang truyền dữ liệu qua tham số, dùng biến local, giá trị trả về, immutable snapshots và kết hợp kết quả sau khi task hoàn thành.
- Biết giới hạn: stateless Java object vẫn có thể gây lỗi concurrency nếu gọi dependency không thread-safe hoặc thao tác lên tài nguyên ngoài có trạng thái.
- Biên dịch/chạy ví dụ Java 17+ để thấy rõ lỗi và cách sửa, đồng thời có checklist review code.

Phạm vi bài học chỉ là **Java SE/JVM concurrency**, không phụ thuộc vào framework.

## 2. Ba câu hỏi phải tách biệt

Ba khái niệm sau liên quan nhau nhưng **không đồng nghĩa**:

| Khái niệm | Đặc điểm | Ví dụ |
|---|---|---|
| **Stateless** | Không giữ mutable data giữa các lần gọi có thể ảnh hưởng kết quả của lần gọi khác; dữ liệu tác vụ nằm trong tham số/local/result. | Hàm chuẩn hóa chuỗi chỉ nhận đầu vào và tạo chuỗi mới. |
| **Immutable** | Trạng thái một đối tượng không thay đổi sau khi xây dựng đúng cách; có thể có state cấu hình cố định. | Record chứa String, int; BigDecimal; một policy có final immutable settings. |
| **Thread-safe** | Có hợp đồng hoạt động đúng khi nhiều thread truy cập theo cách được hỗ trợ. Có thể stateless, immutable hoặc stateful được đồng bộ. | AtomicInteger là thread-safe nhưng **stateful**; counter có synchronized method cũng stateful. |

Một đối tượng **strictly stateless** không cần lưu state phụ thuộc từng lời gọi. Tuy vậy, trong thực tế người ta còn gọi một *service stateless* khi nó giữ **immutable configuration** cố định (ví dụ tỷ lệ thuế), bởi nó không lưu tiến trình hay dữ liệu theo từng request. Để diễn đạt chính xác: service đó **không giữ mutable per-call state**, chứ không phải hoàn toàn không có field.

**Thread safety là thuộc tính của toàn bộ giao thức sử dụng đối tượng**, không chỉ của tên class hay annotation. Một phương thức có thể không giữ field nhưng vẫn không an toàn nếu nó sửa một List dùng chung, gọi static mutable state hoặc tương tác với dependency không thread-safe.

### Phân biệt các phạm vi dữ liệu

~~~text
                 Một instance dùng chung
             ┌───────────────────────────┐
Thread A ───▶│ method(inputA)            │
             │   localA = ...            │──▶ resultA
             │                           │
Thread B ───▶│ method(inputB)            │
             │   localB = ...            │──▶ resultB
             └───────────────────────────┘
                  KHÔNG có mutable
                 per-call field chung

Khác với:
             ┌───────────────────────────┐
Thread A ───▶│ this.currentInput = "A"  │
Thread B ───▶│ this.currentInput = "B"  │
             │ dùng this.currentInput    │
             └───────────────────────────┘
                Cùng đọc/ghi một field
~~~

Hai biến local độc lập trong hai lượt thực thi không trở thành một biến dùng chung chỉ vì hai thread cùng gọi một instance method. Mỗi lời gọi có các local variables và tham số riêng về mặt ngữ nghĩa. Nhưng **giá trị tham chiếu** trong local có thể trỏ tới cùng một object mutable; chỉ tên biến là local không tự bảo đảm object được tham chiếu là riêng.

## 3. Trạng thái chia sẻ xuất hiện từ đâu?

Một giá trị trở thành nguồn rủi ro khi có các điều kiện:

1. Có thể được nhiều thread truy cập trong khoảng thời gian chồng lấn.
2. Ít nhất một thread có thể **thay đổi** cùng trạng thái đó.
3. Không có ownership hoặc giao thức đồng bộ bảo đảm cho những truy cập cạnh tranh.

Các nguồn shared state phổ biến:

| Nơi tồn tại | Ví dụ | Cần kiểm tra |
|---|---|---|
| Instance field | this.lastInput, this.buffer | Cùng instance có được nhiều worker dùng chung không? |
| Static field | static int requestCount | Nhiều instance vẫn cùng ghi một class-level field. |
| Collection truyền vào method | List, Map, Set, array | Các caller có giữ cùng một tham chiếu và sửa đồng thời không? |
| Object nằm trong collection | Map<String, MutableValue> | Map thread-safe cũng không tự làm value mutable thành thread-safe. |
| Dependency giữ bên trong service | client, formatter, accumulator, cache | Dependency có state mutable và được dùng đồng thời không? |
| Lambda/closure | Runnable bắt giữ một List, AtomicInteger hoặc object | Biến local được capture có thể trỏ tới shared mutable object. |
| File, DB, socket, process ngoài | ghi cùng tệp, update cùng bản ghi, gửi cùng session | Dữ liệu ngoài JVM có thứ tự và bất biến riêng. |
| ThreadLocal | dữ liệu riêng theo thread | Không phải stateless; worker pool có thể giữ state giữa nhiều task. |

Đặc biệt, **final chỉ khóa tham chiếu field**, không đóng băng đối tượng mà nó trỏ tới:

~~~java
final class UnsafeTags {
    private final java.util.List<String> tags =
            new java.util.ArrayList<>();

    void add(String tag) {
        tags.add(tag); // vẫn thay đổi ArrayList dùng chung
    }
}
~~~

Đây là đoạn class minh họa, không có main độc lập. Field final không thể đổi sang List khác, nhưng nội dung ArrayList vẫn mutable. Nếu nhiều thread cùng gọi add, ArrayList không cung cấp bảo đảm thread-safe.

## 4. Ví dụ lỗi: lưu input của từng tác vụ vào field

Giả sử một formatter phục vụ nhiều cuộc gọi:

~~~java
class StatefulLabelService {
    private String lastLabel;

    void remember(String label) {
        lastLabel = label;
    }

    String render() {
        return "[" + lastLabel + "]";
    }
}
~~~

Caller mong rằng chuỗi gọi remember("A") rồi render() sẽ trả [A]. Nhưng nếu một thread khác kịp gọi remember("B") trước render(), kết quả thành [B]. Đây là lỗi **cross-request interference**: dữ liệu của tác vụ này lẫn sang tác vụ khác.

Một suy nghĩ sai: “Nếu thêm synchronized cho remember và render thì ổn.” Việc khóa mỗi method riêng lẻ chỉ làm mỗi method có mutual exclusion khi chạy; **cặp** remember → render vẫn có thể xen kẽ với tác vụ khác:

~~~text
Thread A                  Thread B
remember("A")
                          remember("B")
render() -> "[B]"
                          render() -> "[B]"
~~~

Ngay cả khi cả remember() và render() đều synchronized, lịch thực thi trên vẫn hợp lệ vì monitor được nhả giữa hai lần gọi. Muốn giữ tính nhất quán của **cả chuỗi**, phải thiết kế một atomic method hoặc một lock bao toàn sequence. Giải pháp đơn giản hơn ở đây là **không lưu lastLabel trong shared field**.

## 5. Ví dụ chạy độc lập: dàn dựng lỗi và thiết kế stateless

Ví dụ sau dùng CountDownLatch để tạo một lịch chạy xác định. Thread A ghi "A", thread B ghi đè "B", sau đó cả hai đọc lại một field chung. Sau đó chương trình cho hai thread dùng **cùng một StatelessLabelService** nhưng mỗi lần gọi giữ input trong tham số; kết quả đúng với từng caller.

Lưu tệp **StatelessServiceDemo.java**:

~~~java
import java.util.concurrent.CountDownLatch;

public class StatelessServiceDemo {
    static final class StatefulLabelService {
        private String lastLabel;

        void remember(String label) {
            lastLabel = label;
        }

        String render() {
            return "[" + lastLabel + "]";
        }
    }

    static final class StatelessLabelService {
        String format(String label) {
            String normalized = label.trim();
            return "[" + normalized + "]";
        }
    }

    public static void main(String[] args) throws InterruptedException {
        StatefulLabelService bad = new StatefulLabelService();
        CountDownLatch aWritten = new CountDownLatch(1);
        CountDownLatch bWritten = new CountDownLatch(1);
        String[] badResults = new String[2];

        Thread a = new Thread(() -> {
            try {
                bad.remember("A");
                aWritten.countDown();
                bWritten.await();
                badResults[0] = bad.render();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "bad-A");

        Thread b = new Thread(() -> {
            try {
                aWritten.await();
                bad.remember("B");
                bWritten.countDown();
                badResults[1] = bad.render();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "bad-B");

        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("stateful A=" + badResults[0]);
        System.out.println("stateful B=" + badResults[1]);

        StatelessLabelService good = new StatelessLabelService();
        String[] goodResults = new String[2];
        Thread c = new Thread(
                () -> goodResults[0] = good.format("A"), "good-A");
        Thread d = new Thread(
                () -> goodResults[1] = good.format("B"), "good-B");

        c.start();
        d.start();
        c.join();
        d.join();

        System.out.println("stateless A=" + goodResults[0]);
        System.out.println("stateless B=" + goodResults[1]);
    }
}
~~~

Chạy với JDK 17+:

~~~bash
javac StatelessServiceDemo.java
java StatelessServiceDemo
~~~

Kết quả trong điều kiện không có interrupt bên ngoài:

~~~text
stateful A=[B]
stateful B=[B]
stateless A=[A]
stateless B=[B]
~~~

**Lý do output xác định:**

- \`aWritten\` buộc B đợi A ghi xong, rồi B ghi "B" trước khi A tiếp tục. \`bWritten\` buộc A đợi B ghi xong rồi mới đọc field, vì vậy A thấy "B".
- B đọc ngay sau khi ghi "B"; không có lần ghi nào khác. Vì thế hai kết quả stateful đều là [B].
- Hai thread good gọi \`format\` trên **cùng instance**, nhưng method chỉ sử dụng \`label\` và local \`normalized\`, tạo String mới; không có shared mutable field để ghi đè.
- Mỗi thread ghi vào **vị trí riêng** của mảng kết quả; main đọc sau khi join tất cả các thread tương ứng. \`join()\` cung cấp quan hệ happens-before để main quan sát kết quả đã hoàn tất.
- Latch được dùng **để dàn dựng trình tự lỗi**, không phải để sửa một shared state. Cách sửa thực sự ở phần good là loại bỏ field chứa dữ liệu per-call.

Ví dụ intentionally đơn giản, nhằm chứng minh **đúng/sai về tính độc lập của lượt gọi**, không phải benchmark. Không dùng \`Thread.sleep\` để tạo một “race chắc chắn” vì thời gian ngủ không bảo đảm thứ tự thực thi.

## 6. Refactor: biến method thành một giao dịch đầu vào → đầu ra

Thay vì:

~~~java
class Formatter {
    private String currentUser;

    void setUser(String user) {
        currentUser = user;
    }

    String greeting() {
        return "Hello, " + currentUser;
    }
}
~~~

Thiết kế lại:

~~~java
class Formatter {
    String greeting(String user) {
        String normalized = user.strip();
        return "Hello, " + normalized;
    }
}
~~~

Method mới có hai đặc tính:

1. Dữ liệu \`user\` được đưa vào **lời gọi cụ thể**, không cần một lượt ghi vào instance để chuẩn bị cho lượt đọc tiếp theo.
2. Kết quả được **trả về trực tiếp** cho caller. Không tồn tại \`lastResult\` hoặc \`currentUser\` bị tác vụ khác ghi đè.

Ở đây \`String\` bất biến, \`strip()\` trả String phù hợp chứ không sửa input. Vì vậy các caller không chia sẻ mutable working state qua method.

**Lưu ý thiết kế API:** Một method xử lý nhiều tham số có thể nhận một immutable input object (record) để giữ ngữ nghĩa rõ ràng. Tránh thiết kế hai bước \`setContext(x)\` rồi \`execute()\` trên object được dùng chung, vì đó là một temporal coupling dễ tạo race khi nhiều thread cùng gọi.

## 7. Stateless service có immutable configuration

Trong ứng dụng thật, một object có thể cần cấu hình cố định: tỷ lệ, quy tắc định dạng, giới hạn mặc định, danh sách chính sách bất biến. Giữ immutable config không giống lưu per-request state.

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

final class PriceCalculator {
    private final BigDecimal taxRate;

    PriceCalculator(BigDecimal taxRate) {
        this.taxRate = Objects.requireNonNull(taxRate);
    }

    BigDecimal total(BigDecimal amount) {
        Objects.requireNonNull(amount);
        return amount.multiply(BigDecimal.ONE.add(taxRate))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
~~~

\`PriceCalculator\` có **state cấu hình bất biến** là \`taxRate\`, nhưng không ghi trạng thái riêng cho từng phép tính. \`BigDecimal\` là immutable; phép tính tạo giá trị mới. Nếu object được khởi tạo và **publish an toàn** cho các worker, nhiều thread có thể gọi \`total()\` đồng thời mà không cần lock bảo vệ chính việc tính toán này.

### Ví dụ độc lập với ExecutorService

Lưu **StatelessPriceDemo.java**:

~~~java
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class StatelessPriceDemo {
    static final class PriceCalculator {
        private final BigDecimal taxRate;

        PriceCalculator(BigDecimal taxRate) {
            this.taxRate = Objects.requireNonNull(taxRate);
        }

        BigDecimal total(BigDecimal amount) {
            Objects.requireNonNull(amount);
            return amount.multiply(BigDecimal.ONE.add(taxRate))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    public static void main(String[] args) throws Exception {
        PriceCalculator calculator =
                new PriceCalculator(new BigDecimal("0.08"));

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<BigDecimal> a = workers.submit(() ->
                    calculator.total(new BigDecimal("100.00")));
            Future<BigDecimal> b = workers.submit(() ->
                    calculator.total(new BigDecimal("50.00")));

            System.out.println("A=" + a.get());
            System.out.println("B=" + b.get());
        } finally {
            workers.shutdown();
        }
    }
}
~~~

Lệnh:

~~~bash
javac StatelessPriceDemo.java
java StatelessPriceDemo
~~~

Output:

~~~text
A=108.00
B=54.00
~~~

**Vì sao an toàn cho hai task?**

- Calculator được khởi tạo đầy đủ **trước** khi được capture vào Runnable/Callable gửi cho executor. Hợp đồng memory consistency của ExecutorService đảm bảo các hành động trước lúc submit happens-before hành động của task; tác vụ nhận được trạng thái constructor đã thiết lập.
- \`taxRate\` là final reference tới BigDecimal immutable. Không task nào cập nhật nó.
- Mỗi lần gọi \`total\` dùng tham số riêng, tạo kết quả BigDecimal mới và không ghi instance field.
- \`Future.get()\` đợi task hoàn tất, đồng thời tạo quan hệ happens-before để caller quan sát kết quả.
- Việc in theo thứ tự \`a.get()\` rồi \`b.get()\` không yêu cầu task A hoàn thành trước B. Hai task có thể được lập lịch theo bất kỳ thứ tự nào.
- \`shutdown()\` đóng đường nhận task mới; ví dụ đã gọi get() cho cả hai Future trước khi rời try. Với vòng đời thực tế còn cần timeout/hủy và phương án khi tác vụ lỗi hoặc không kết thúc.

Điểm quan trọng: **final reference không đủ nếu object cấu hình vẫn mutable**. Ví dụ \`private final ArrayList<String> rules\` vẫn cho phép sửa \`rules\` sau constructor, có thể gây race. Hãy sao chép/đóng băng đúng độ sâu cần thiết và dùng immutable elements khi cần.

## 8. Các pattern tránh mutable shared state

### Pattern A — Đưa dữ liệu theo tác vụ vào tham số

Thay \`this.input = x; process()\` bằng \`process(x)\`; trả giá trị thay vì ghi \`this.lastResult\`.

~~~java
String normalize(String input) {
    return input.strip().toLowerCase(java.util.Locale.ROOT);
}
~~~

Đoạn method minh họa này không lưu state và tạo String mới. Locale.ROOT hữu ích khi cần chuẩn hóa kỹ thuật không phụ thuộc locale của người dùng; việc chọn quy tắc normalize thực tế phải theo domain.

### Pattern B — Đưa giá trị trung gian vào biến local

~~~java
long sumSquares(int[] source) {
    long total = 0;
    for (int n : source) {
        total += (long) n * n;
    }
    return total;
}
~~~

\`total\` riêng cho mỗi invocation, nên không cần lock cho \`total\`. **Tuy nhiên**, caller có thể sửa mảng \`source\` trong lúc method đọc, tạo vấn đề snapshot dữ liệu. Nếu muốn kết quả ổn định, yêu cầu input không bị sửa đồng thời hoặc tạo bản sao trước khi tính. Cũng cần xác định chính sách overflow của phép cộng long khi dữ liệu rất lớn.

### Pattern C — Thread confinement hoặc task confinement

Một object mutable có thể an toàn nếu **chỉ một task/thread sở hữu nó** và không chia sẻ trước khi kết thúc. Ví dụ, mỗi task tạo StringBuilder riêng:

~~~java
String makeLabel(String first, String second) {
    StringBuilder local = new StringBuilder();
    local.append(first).append(" / ").append(second);
    return local.toString();
}
~~~

StringBuilder không synchronized và không thread-safe khi chia sẻ tùy tiện, nhưng **không có race** nếu chỉ dùng nó trong một invocation, không escape sang thread khác và không tồn tại shared reference. Không vì có class mutable mà bắt buộc dùng StringBuffer.

### Pattern D — Immutable input/result snapshot

~~~java
record JobInput(String id, int quantity) {}
record JobResult(String id, long doubledQuantity) {}

JobResult calculate(JobInput input) {
    return new JobResult(
            input.id(),
            (long) input.quantity() * 2
    );
}
~~~

\`record\` gọn, có final components và thích hợp biểu diễn input/result. Tuy nhiên record **chỉ shallow immutable** nếu component là đối tượng mutable. \`record Payload(List<String> items)\` không tự chặn caller sửa chính List truyền vào. Có thể dùng \`List.copyOf(items)\` trong constructor phù hợp, đồng thời đảm bảo phần tử trong List cũng có tính bất biến cần thiết.

### Pattern E — Tổng hợp kết quả sau khi task hoàn thành

Nếu mỗi task cần tính một giá trị riêng, hãy để nó trả giá trị thay vì cùng ghi vào field \`total\` của service. Caller có thể kết hợp \`Future\` results sau khi chờ hoàn tất, hoặc dùng stream reduction phù hợp với phép toán.

~~~java
// Đoạn trích trong method có List<Future<Long>> futures:
long total = 0;
for (java.util.concurrent.Future<Long> future : futures) {
    total = Math.addExact(total, future.get());
}
~~~

Đây là **đoạn trích**, \`futures\` được tạo ở code gọi và method phải khai báo/handle checked exceptions của get(). \`Math.addExact\` giúp phát hiện overflow thay vì âm thầm wraparound.

### Pattern F — Shared state không tránh được thì quản lý bằng protocol rõ ràng

Không phải mọi bài toán đều xóa được state. Metrics, cache, hàng đợi, session, inventory và tài nguyên bên ngoài có state nghiệp vụ thực. Khi đó:
- \`AtomicInteger\`/\`AtomicLong\` cho counter cập nhật nguyên tử đơn giản.
- \`LongAdder\` cho metric có nhiều writer, chấp nhận \`sum()\` không phải snapshot nguyên tử khi còn cập nhật.
- \`ConcurrentHashMap\` hoặc collection concurrent phù hợp nếu nhiều thread truy cập cấu trúc chung; không cho rằng sequence \`containsKey\` rồi \`put\` tự atomic.
- \`synchronized\`, \`ReentrantLock\`, CAS loop hoặc một owner thread để duy trì invariant nhiều bước.
- Bounded queue, timeout và backpressure khi task được giao qua các luồng.

Mục tiêu của stateless design là **giảm tối đa state chia sẻ không cần thiết**, không phủ nhận nhu cầu state hợp lệ.

## 9. Những hiểu nhầm cần tránh

### “Không có field” nghĩa là thread-safe trong mọi hoàn cảnh

Sai. Ví dụ:

~~~java
void append(java.util.List<String> target, String text) {
    target.add(text);
}
~~~

Method này không có instance field, nhưng hai thread có thể truyền **cùng ArrayList** vào target. Nếu ArrayList bị thêm phần tử đồng thời mà không có đồng bộ thích hợp, chương trình vẫn không thread-safe.

Tính an toàn phải xét luôn **ownership và thread-safety của đối tượng được truyền vào**. Stateless service chỉ loại bỏ một nguồn shared state nội bộ.

### “Mỗi thread có local variable” nghĩa là mọi object nó dùng đều riêng

Sai. Hai biến local \`List<String> mine\` có thể cùng trỏ đến một List, vì biến local lưu **tham chiếu**, không tạo bản copy của object. Đừng nhầm bảo vệ biến tham chiếu local với bảo vệ object trên heap.

### “final” biến mutable object thành immutable

Sai. \`final Map<String, String> settings = new HashMap<>()\` vẫn cho phép \`settings.put(...)\`. Muốn trạng thái bất biến, cần xây dựng cấu trúc không thể sửa hoặc không để lộ khả năng sửa ra bên ngoài. \`Map.copyOf\` tạo unmodifiable map tại tầng map, nhưng nếu values là mutable objects thì chúng vẫn có thể thay đổi.

### “synchronized mọi getter/setter là đủ”

Không hẳn. Nếu logic yêu cầu \`setUser(x)\` rồi \`getGreeting()\` tương ứng, khóa từng method riêng vẫn cho thread khác chen vào giữa. Thay bằng method stateless một bước hoặc bảo vệ **cả quy trình** dưới một critical section đúng đối tượng lock.

### “static counter chỉ có một bản nên an toàn”

Không. Chính vì là một bản dùng chung nên \`static int count; count++\` càng cần xem xét cạnh tranh. Static không cung cấp atomicity, visibility hay mutual exclusion.

### “Sử dụng ThreadLocal thì trở thành stateless”

Không. ThreadLocal **lưu state** gắn với thread hiện tại. Với thread pool, worker sống lâu có thể dùng lại cho nhiều task và giữ giá trị của task trước. Khi dùng ThreadLocal phải xác định phạm vi, khởi tạo và \`remove()\` trong finally khi phù hợp. ThreadLocal không thay thế API truyền context rõ ràng khi có thể tránh dùng shared state.

### “Dùng concurrent collection là mọi workflow đã atomic”

Không. Cấu trúc như ConcurrentHashMap cung cấp các hành động thread-safe theo API của nó; nhiều lời gọi tuần tự vẫn có thể bị interleave. Khi invariant là “nếu chưa có thì tạo”, cần chọn atomic operation như \`computeIfAbsent\` hoặc putIfAbsent đúng yêu cầu; mapping function phải tuân thủ hợp đồng và tránh tác dụng phụ không mong muốn.

### “Object immutable thì mọi hệ thống liên quan tự nhất quán”

Không. Immutable snapshot bảo vệ **bên trong dữ liệu đã chụp**, nhưng bản ghi bên ngoài JVM, network request và filesystem vẫn có concurrency semantics riêng. Một service không giữ field nhưng gọi hệ thống ngoài có thể vẫn gặp lost update hoặc race ở phía tài nguyên ngoài.

## 10. Stateless vs thread-safe stateful: chọn giải pháp đúng

| Tình huống | Cách tiếp cận | Vì sao |
|---|---|---|
| Format/validate/compute theo từng đầu vào | Stateless method, local variables | Không cần lưu intermediate per-call fields. |
| Có cấu hình đọc nhiều, ít thay đổi | Immutable configuration, safe publication | Chia sẻ an toàn cấu hình cố định. |
| Công việc gồm nhiều bước cần context | Immutable request context truyền vào method/task | Tránh setContext() trên shared instance. |
| Mỗi task cần mutable buffer tạm | Tạo buffer riêng trong task | Thread confinement, không chia sẻ. |
| Đếm tổng số lượt gọi | AtomicLong/LongAdder | Đây là state nghiệp vụ có thật, không thể “giả stateless”. |
| Cache dùng chung | Concurrent map và cơ chế cập nhật đúng | Cần chính sách capacity, consistency, eviction và đồng bộ khi cập nhật. |
| Nhiều giá trị phải nhất quán cùng lúc | Immutable snapshot thay thế nguyên tử hoặc lock chung | Giữ invariant đa trường trong một protocol. |
| Dữ liệu request bị đẩy vào mutable dependency | Tái thiết kế dependency hoặc bảo vệ truy cập | Service stateless bề mặt vẫn có thể race ở tầng dưới. |

Một quy trình ra quyết định hiệu quả:

1. **State đó có thật sự cần sống qua lời gọi?** Nếu không, chuyển thành parameter/local/result.
2. **Nếu cần chia sẻ, có thể immutable sau khi xây dựng không?** Nếu có, publish an toàn và không mutate.
3. **Nếu cần thay đổi, ai là owner và invariant nào cần giữ?** Chọn lock/CAS/concurrent data structure cho toàn bộ thao tác liên quan.
4. **Bao nhiêu thread sẽ truy cập cùng object?** Đo contention và profile trước khi tối ưu.
5. **Tài nguyên ngoài có state không?** Thiết kế protocol phù hợp cả bên ngoài JVM.

## 11. Publication, Java Memory Model và tính đúng đắn

Loại bỏ mutable per-call fields làm giảm race nội bộ, nhưng **an toàn công bố object** vẫn quan trọng. Nếu một object có immutable configuration, các thread khác phải nhận được reference của nó qua con đường publication được JMM bảo đảm.

Những cách thông dụng gồm: khởi tạo qua static initialization an toàn; truyền object cho executor task sau khi khởi tạo xong; đồng bộ/volatile reference thích hợp; hoặc xuất bản đối tượng được thiết kế immutable đúng cách, không để \`this\` escape trong constructor.

Ví dụ với \`ExecutorService.submit\`: các hành động trong thread trước khi submit task happens-before hành động bên trong task theo hợp đồng memory consistency của ExecutorService. Vì vậy, một instance với immutable config được khởi tạo trước khi submit có thể được dùng bởi nhiều tasks một cách an toàn cho phần đọc cấu hình đó.

\`final\` fields có quy tắc bảo đảm khởi tạo riêng trong JMM khi constructor hoàn thành đúng cách. Nhưng final không bảo vệ nội dung object mutable được field trỏ tới, và không biện minh cho việc để \`this\` escape trước khi constructor hoàn tất. Nếu thiết kế cho chia sẻ, nên ưu tiên ownership rõ ràng và tránh publication mập mờ.

### Happens-before và việc đọc kết quả

Khi task trả về dữ liệu vào \`Future\`, hợp đồng \`Future.get()\` giúp caller thấy các hành động của task đã hoàn tất. Tương tự, những hành động của thread trước khi kết thúc happens-before thread khác quay về từ \`join()\` tương ứng.

**Nhưng join/get không sửa race đã diễn ra trong task**: nếu hai task cùng ghi \`this.lastResult\`, chờ chúng xong chỉ giúp bạn thấy một trong các giá trị cuối. Cách đúng là không dùng field kết quả chung nếu mỗi task cần kết quả riêng.

## 12. Một ví dụ thiết kế sai thường khó nhận ra: mutable helper

Giả sử một service không có counter hay user field, nhưng giữ một StringBuilder để “tái sử dụng bộ nhớ”:

~~~java
class SharedBuilderFormatter {
    private final StringBuilder builder = new StringBuilder();

    String format(String name) {
        builder.setLength(0);
        builder.append("Hello ");
        builder.append(name);
        return builder.toString();
    }
}
~~~

Đây là một **đối tượng có mutable shared state** và không thread-safe. Hai thread gọi format đồng thời có thể xen kẽ setLength/append/toString, trả sai chuỗi hoặc gây lỗi tùy tình huống. \`final\` chỉ bảo đảm field builder không bị trỏ sang StringBuilder khác.

Cách sửa tự nhiên:

~~~java
class LocalBuilderFormatter {
    String format(String name) {
        StringBuilder local = new StringBuilder();
        local.append("Hello ").append(name);
        return local.toString();
    }
}
~~~

Mỗi invocation có buffer riêng. Đây là ví dụ điển hình của việc **tránh chia sẻ** thay vì áp lock cho một tài nguyên vốn không cần chung. JVM có thể tối ưu allocation trong nhiều trường hợp, nhưng không nên tuyên bố runtime chắc chắn loại bỏ được mọi allocation; hãy benchmark nếu hiệu năng allocation là vấn đề thực tế.

Nếu workload rất nặng và cần pooling thực sự, pooling phải được thiết kế có ownership, reset, exception handling và lifecycle rõ ràng. Đó là tradeoff phức tạp, không phải mặc định tối ưu.

## 13. Ranh giới với external state và side effects

Một phương thức Java không có field vẫn có thể gọi database, filesystem, socket hoặc một remote service để ghi dữ liệu. Về mặt **trạng thái nội bộ của object**, nó có thể stateless; về mặt **hiệu ứng nghiệp vụ**, nó không phải pure function và có thể cạnh tranh với các lời gọi khác.

Ví dụ minh họa:

~~~java
void transfer(AccountRepository repository,
              String from, String to, long amount) {
    // Các bước thực tế phải có transaction/atomicity theo hợp đồng
    // của nguồn dữ liệu. Chỉ vì method không giữ field không đủ.
}
~~~

Đây là **đoạn chữ ký minh họa**, các kiểu AccountRepository và logic chuyển tiền không được định nghĩa ở đây. Không coi nó là chương trình chạy được. Ý nghĩa: khi nhiều worker thao tác cùng external account, stateless Java object không thể bảo đảm invariant số dư. Cần giao thức ở đúng nơi sở hữu dữ liệu (atomic operation, transaction, versioning hoặc thiết kế khác theo hệ thống đó).

Trong phạm vi một JVM, cùng nguyên tắc áp dụng với file, shared cache, concurrent collection và resource pool. **Tránh state trong service** không đồng nghĩa xóa hết state khỏi hệ thống; nó khiến nơi sở hữu và bảo vệ state trở nên rõ ràng hơn.

## 14. Khi nào nên giữ state có chủ đích?

Một số đối tượng nên là stateful:

- **Counter/metric:** số yêu cầu hoặc sự kiện; dùng atomic/adder với semantics quan sát thích hợp.
- **Queue/channel:** danh sách công việc đang chờ, với capacity/backpressure và hợp đồng producer–consumer.
- **Cache:** chia sẻ kết quả, giới hạn dung lượng, hết hạn và nhất quán theo key.
- **State machine:** trạng thái chuyển qua các giai đoạn, phải giữ invariant giữa các transition.
- **Rate limiter:** trạng thái cấp phép/tokens, cần phối hợp atomic.

Điều quan trọng là **đừng nhét state vào một service xử lý thuần túy chỉ vì muốn tái sử dụng object**. Nếu state thật sự cần tồn tại, hãy để nó thuộc một component có responsibility và synchronization policy rõ ràng.

Ví dụ đơn giản:

~~~java
import java.util.concurrent.atomic.AtomicLong;

final class RequestMetrics {
    private final AtomicLong handled = new AtomicLong();

    void recordHandled() {
        handled.incrementAndGet();
    }

    long handledSoFar() {
        return handled.get();
    }
}
~~~

\`RequestMetrics\` là một **stateful, thread-safe counter** cho thao tác tăng/lấy một giá trị. Nó không trở thành stateless chỉ vì dùng \`AtomicLong\`. Nếu một nghiệp vụ cần nhất quán giữa metric này và nhiều state khác, cần thêm giao thức phù hợp; atomic update của một counter không làm tất cả thao tác bên ngoài atomic.

## 15. Checklist review code theo hướng loại bỏ chia sẻ

Khi gặp một class được dùng đồng thời, kiểm tra theo thứ tự:

1. **Có mutable instance fields không?** Đặc biệt \`currentUser\`, \`lastResult\`, \`StringBuilder\`, \`ArrayList\`, \`HashMap\`, \`SimpleDateFormat\` hoặc bộ đệm nội bộ. Với Java date/time hiện đại, ưu tiên immutable API như \`DateTimeFormatter\`.
2. **Có static mutable fields không?** Một class “không field instance” vẫn có thể chia sẻ state qua static.
3. **Method có thực sự dùng input và local không?** Dữ liệu per-call có được trả về thay vì đưa vào shared field?
4. **Input có tham chiếu tới object mutable dùng chung không?** Local parameter không bảo đảm object là riêng. Cần quy ước owner/copy/immutable.
5. **Dependency được gọi có thread-safe không?** Một service không đổi state vẫn có thể gọi helper mutable.
6. **Có nhiều bước phụ thuộc nhau không?** Chuỗi set rồi get, check rồi act, read rồi write cần một invariant cấp operation.
7. **Kết quả của các task được gom ở đâu?** Ưu tiên return/Future/reduction; tránh lastResult trên shared instance.
8. **Object có được publish an toàn không?** Constructor hoàn tất, state immutable không bị sửa sau publish.
9. **ThreadLocal có được dọn không?** Nếu cần ThreadLocal, xác định rõ owner, thời điểm set/remove và nguy cơ giữ dữ liệu qua worker reuse.
10. **Có resource bên ngoài dùng chung không?** File, connection, cache, hệ thống dữ liệu có state và contract riêng.
11. **Đã kiểm tra tính đúng trước benchmark chưa?** Việc thêm synchronized có thể làm lỗi biến mất, nhưng stateless refactor thường còn loại bỏ cả temporal coupling khó hiểu.
12. **Đã mô tả rõ giới hạn concurrency của API chưa?** Stateless không tự giới hạn số task, memory, queue, connection hay CPU.

## 16. Bài tập tự kiểm tra

**Câu 1.** Một object có field \`private final BigDecimal rate\` được set từ constructor và không thay đổi, method \`calculate(x)\` chỉ dùng local. Có thể gọi chung từ nhiều thread không?

**Đáp án:** Có, nếu calculator được khởi tạo và publish an toàn, BigDecimal immutable, method không sửa shared state và không dùng dependency không an toàn. Đây là service **không giữ mutable per-call state** dù có immutable configuration.

**Câu 2.** Method không có field nhưng nhận \`List<String> input\` và gọi \`input.sort(...)\`. Nó có tự động thread-safe không?

**Đáp án:** Không. Nếu nhiều thread đưa cùng mutable list vào thì method có thể thay đổi shared input đồng thời. Cần ownership/copy hoặc đồng bộ phù hợp.

**Câu 3.** Hai method \`synchronized setUser()\` và \`synchronized getGreeting()\` trên cùng instance. Caller A gọi set("A"), caller B gọi set("B"), rồi A gọi get(). Có thể trả B không?

**Đáp án:** Có. Mỗi method atomic tương đối với monitor, nhưng lock đã nhả giữa hai method. Chuỗi set+get không atomic.

**Câu 4.** Một service có \`private final StringBuilder builder\`, mỗi method gọi setLength(0) rồi append, rồi toString. Đây có phải stateless không?

**Đáp án:** Không. Builder là mutable shared field. Đưa StringBuilder vào biến local trong method hoặc thiết kế ownership phù hợp.

**Câu 5.** Một \`ThreadLocal<UserContext>\` dùng trong worker pool có biến service thành stateless không?

**Đáp án:** Không. Nó vẫn lưu state theo thread, có thể tồn tại qua nhiều task của cùng worker và cần cleanup đúng cách.

**Câu 6.** Khi hai task cần cộng kết quả, cách nào giảm shared state: cùng tăng field \`sum\` không đồng bộ, hay mỗi task trả giá trị và caller tổng hợp sau \`Future.get()\`?

**Đáp án:** Mỗi task trả giá trị và caller tổng hợp sau get() thường đơn giản và tránh race trên field sum. Cần xử lý exception, hủy, overflow và số lượng task theo yêu cầu thực tế.

## 17. Tài liệu đối chiếu

- **Java Language Specification §17 — Threads and Locks:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html
- **ExecutorService:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html
- **Future:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html
- **CountDownLatch:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html
- **AtomicLong:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html
- **ConcurrentHashMap:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html
- **ThreadLocal:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html

**Điểm cần nhớ:** Giải pháp đơn giản nhất cho nhiều race condition là **không chia sẻ mutable state nếu không cần**. Đưa dữ liệu tác vụ vào tham số/local/result và giữ immutable configuration giúp một instance phục vụ nhiều thread an toàn hơn, giảm lock và giảm việc phải suy luận về interleaving. Nếu state chung là một phần thật sự của nghiệp vụ, hãy nêu rõ owner, invariant và cơ chế đồng bộ để bảo vệ nó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-003">Immutability và immutable object graph</a>

<details>
<summary>Xem chi tiết</summary>

# Immutability và immutable object graph trong Java Concurrency

## 1. Vì sao bất biến giúp chương trình đa luồng dễ đúng hơn?

Khi nhiều thread cùng truy cập một đối tượng có thể thay đổi, mỗi thread phải trả lời các câu hỏi: ai được ghi, ai được đọc, đọc ở thời điểm nào, cần khóa nào và dữ liệu có đồng bộ không? Nếu đối tượng đã được **khởi tạo hoàn chỉnh và không còn thay đổi**, các thread chỉ đọc sẽ không cạnh tranh để sửa trạng thái của đối tượng đó. Điều này giảm đáng kể số tình huống gây **data race**, trạng thái trung gian và vi phạm bất biến nghiệp vụ.

**Immutability (tính bất biến)** nghĩa là trạng thái quan sát được của một đối tượng không thay đổi sau khi được tạo. **Immutable object graph (đồ thị đối tượng bất biến)** mở rộng ý tưởng này tới những đối tượng có thể truy cập qua các trường tham chiếu, phần tử collection, mảng, object lồng nhau và các đường dẫn khác bắt đầu từ một đối tượng gốc.

Ví dụ, một cấu hình có cấu trúc:

```text
AppConfig
  ├── name: String
  ├── limits: Map<String, Integer>
  └── endpoints: List<Endpoint>
                       ├── host: String
                       └── tags: List<String>
```

Muốn cấu hình này thực sự bất biến theo quan sát của caller, không chỉ trường `name` mà cả nội dung `limits`, `endpoints`, từng `Endpoint` và `tags` cũng phải được bảo vệ khỏi sửa đổi về sau. Chỉ thêm `final` vào ba trường của `AppConfig` là **chưa đủ** nếu chúng trỏ tới `HashMap`, `ArrayList` hoặc `Endpoint` mutable.

Mục tiêu của bài: phân biệt các mức bất biến, xác định biên của đồ thị object, tạo và công bố snapshot an toàn trong Java, nhận diện các lỗ hổng do aliasing, collection lồng nhau và cập nhật đồng thời. Đây là nội dung về **Java concurrency nói chung**.

## 2. Bốn khái niệm thường bị nhầm lẫn

| Khái niệm | Bảo đảm thực sự | Ví dụ | Điều chưa được bảo đảm |
| --- | --- | --- | --- |
| **Final field** | Không thể gán lại trường qua mã Java thông thường sau khởi tạo | `final List<String> items` | Danh sách được tham chiếu không tự trở thành bất biến |
| **Unmodifiable view** | Tham chiếu view không cho phép sửa qua các method thông thường của nó | `Collections.unmodifiableList(source)` | Nếu `source` còn mutable, view có thể phản ánh thay đổi của source |
| **Unmodifiable snapshot** | Một collection không cho sửa qua API của nó và không tiếp tục phản ánh thay đổi cấu trúc của collection nguồn | `List.copyOf(source)` | Các phần tử object bên trong có thể vẫn mutable |
| **Immutable object graph** | Trạng thái quan sát được của toàn bộ cấu trúc reachable không thay đổi sau khi công bố | Record với `String`, số nguyên, danh sách sao chép của các phần tử bất biến | Vẫn cần cách khởi tạo và công bố tham chiếu an toàn |

Ví dụ `final List<String>` có thể rất an toàn nếu giá trị gán thực sự là một danh sách bất biến của các `String` bất biến, nhưng từ khóa `final` **tự nó không chứng minh được điều đó**.

Cần phân biệt thêm:

- **Shallow immutability (bất biến nông):** các trường trực tiếp không được gán lại, hoặc collection không cho sửa cấu trúc qua API đang dùng; các object bên trong vẫn có thể thay đổi.
- **Deep immutability (bất biến sâu):** mọi phần trạng thái quan sát được qua các tham chiếu có liên quan đều không đổi. Phải kiểm tra cả các đối tượng lồng nhau và bí danh tham chiếu (alias).
- **Effective immutability (bất biến theo cách sử dụng):** một object vốn có API sửa đổi nhưng sau khi công bố được bảo đảm không còn bị sửa nữa. Đây là một *giao thức* cần được toàn bộ code tuân thủ, khó kiểm chứng hơn immutable type thực sự.
- **Read-only reference (tham chiếu chỉ đọc):** caller không thể sửa qua tham chiếu ấy, nhưng có thể có chủ sở hữu khác vẫn sửa được object gốc. Read-only không tự đồng nghĩa immutable.

## 3. Object graph và vấn đề aliasing

Java lưu các object trên heap; trường kiểu tham chiếu trỏ tới object khác. Nhiều biến có thể cùng trỏ đến một object: đó là **aliasing (nhiều tham chiếu chung một object)**.

```text
owner.source ───┐
                ├──────> ArrayList ───> MutableItem
snapshot.items ─┘                         ↑
                                         │
caller.savedItem ─────────────────────────┘
```

Nếu constructor chỉ làm `this.items = source`, caller vẫn có `source` để thêm/xóa phần tử. Nếu constructor gọi `List.copyOf(source)`, caller không thể sửa cấu trúc snapshot thông qua `source`, nhưng nếu phần tử `MutableItem` vẫn được chia sẻ, caller có thể sửa nó qua `savedItem`. Cả hai đường sửa đều phải được xét.

Hãy đặt hai câu hỏi riêng:

1. **Có ai còn giữ tham chiếu tới collection mutable mà snapshot đang dùng không?** Nếu có, phải copy collection.
2. **Có ai còn giữ tham chiếu tới các phần tử mutable trong collection không?** Nếu có, copy collection chưa đủ: cần biến phần tử thành giá trị bất biến hoặc sao chép sâu theo hợp đồng.

Đồ thị có thể là DAG, có chia sẻ các object con, hoặc thậm chí có chu trình (A tham chiếu B, B tham chiếu A). Không phải mọi domain đều cần “deep copy” mọi thứ; vấn đề cần giải quyết là **đường nào cho phép trạng thái quan sát được thay đổi**. Với đồ thị có chu trình, deep copy phải có cơ chế nhận diện các object đã sao chép để bảo toàn quan hệ và tránh đệ quy vô hạn.

## 4. Lỗi đơn giản nhất: `final` giữ tham chiếu, không đóng băng object

Tạo tệp `FinalReferenceIsMutableDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class FinalReferenceIsMutableDemo {
    private static final class Store {
        final List<String> items;

        Store(List<String> items) {
            this.items = items;
        }
    }

    public static void main(String[] args) {
        List<String> source = new ArrayList<>();
        source.add("A");

        Store store = new Store(source);
        source.add("B");

        System.out.println("store size=" + store.items.size());
        store.items.add("C");
        System.out.println("source size=" + source.size());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac FinalReferenceIsMutableDemo.java
java FinalReferenceIsMutableDemo
```

Kết quả:

```text
store size=2
source size=3
```

Trường `items` là `final` nhưng cả `store.items` và `source` trỏ tới **cùng ArrayList**. Một phía sửa thì phía kia quan sát được thay đổi. Nếu nhiều thread sử dụng danh sách đó và một thread sửa trong khi thread khác đọc mà thiếu đồng bộ, có nguy cơ data race hoặc hành vi không được bảo đảm theo hợp đồng của collection.

Phần nào sai về thiết kế? Constructor đã **cho phép sở hữu dữ liệu tiếp tục được chia sẻ** ngoài ý muốn. Khắc phục tối thiểu là tạo bản sao collection độc lập khi xây dựng object và không để lộ đường sửa về sau.

## 5. `Collections.unmodifiableList` khác `List.copyOf`

Hai API có mục đích và phạm vi khác nhau:

`Collections.unmodifiableList(source)` tạo một **view** không cho sửa qua view. Nếu `source` vẫn được sửa, view có thể phản ánh các thay đổi đó. Nó không phải bản chụp tách rời collection nguồn.

`List.copyOf(source)` (Java 10+) trả một danh sách **không sửa được**, có nội dung là snapshot các phần tử của collection nguồn tại thời điểm tạo theo hợp đồng của API. Nó không tiếp tục phụ thuộc vào việc source thêm/xóa phần tử. Nếu nguồn đã là một danh sách không sửa được thích hợp, implementation có thể tái sử dụng instance; không nên dựa vào việc nhận được instance mới. `List.copyOf` cũng **không chấp nhận phần tử null**.

Tạo tệp `ViewVsCopyDemo.java`:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ViewVsCopyDemo {
    public static void main(String[] args) {
        List<String> source = new ArrayList<>(List.of("A"));

        List<String> view = Collections.unmodifiableList(source);
        List<String> snapshot = List.copyOf(source);

        source.add("B");

        System.out.println("view=" + view);
        System.out.println("snapshot=" + snapshot);

        try {
            snapshot.add("C");
        } catch (UnsupportedOperationException e) {
            System.out.println("snapshot cannot be modified");
        }
    }
}
```

```bash
javac ViewVsCopyDemo.java
java ViewVsCopyDemo
```

Kết quả:

```text
view=[A, B]
snapshot=[A]
snapshot cannot be modified
```

Đây là **bất biến về cấu trúc collection**. Trong ví dụ, phần tử `String` cũng bất biến, nên snapshot không có object con mutable.

Các API tương tự: `Set.copyOf`, `Map.copyOf`, `List.of`, `Set.of`, `Map.of`. Chúng tạo/trả về collection không sửa được, nhưng cần chú ý quy tắc về `null`, phần tử trùng lặp, khóa trùng và thứ tự duyệt. Đặc biệt, đừng suy luận thứ tự duyệt `Map.copyOf` hay `Set.copyOf` sẽ giữ đúng thứ tự insertion nếu hợp đồng không bảo đảm.

**Giới hạn:** `copyOf` sao chép *tham chiếu phần tử*, không tự sao chép sâu các object được tham chiếu. Nếu một phần tử mutable còn được giữ bởi caller, snapshot vẫn quan sát được thay đổi của phần tử ấy.

## 6. Một snapshot shallow vẫn có thể thay đổi từ bên trong

Tạo tệp `ShallowCopyDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class ShallowCopyDemo {
    record Snapshot(List<StringBuilder> names) {
        Snapshot {
            names = List.copyOf(names);
        }
    }

    public static void main(String[] args) {
        StringBuilder element = new StringBuilder("An");
        List<StringBuilder> source = new ArrayList<>();
        source.add(element);

        Snapshot snapshot = new Snapshot(source);
        source.clear();
        element.append("-updated");

        System.out.println("snapshot size=" + snapshot.names().size());
        System.out.println("snapshot value=" + snapshot.names().get(0));
    }
}
```

```bash
javac ShallowCopyDemo.java
java ShallowCopyDemo
```

Kết quả:

```text
snapshot size=1
snapshot value=An-updated
```

Mặc dù `Snapshot` là `record` và đã dùng `List.copyOf`, phần tử bên trong vẫn là một `StringBuilder` mutable được chia sẻ. Xóa danh sách `source` không ảnh hưởng đến kích thước snapshot, nhưng sửa `element` thay đổi chuỗi mà snapshot nhìn thấy.

Bài học: một **record không tự bảo đảm bất biến sâu**, bởi các component của record có thể là mảng, collection hoặc object mutable. Tính bất biến của bản đồ object phụ thuộc vào **kiểu và đường sửa của mọi object reachable**, không chỉ tên kiểu gốc.

Trong ứng dụng thực tế, `StringBuilder` vốn không thread-safe. Nếu một thread sửa nó trong khi thread khác gọi `toString()` mà thiếu đồng bộ, kết quả còn có nguy cơ bị data race. Ví dụ này chạy tuần tự để minh họa việc alias tồn tại chắc chắn, không cố tạo một cuộc đua ngẫu nhiên.

## 7. Thiết kế một immutable object graph bằng record và copy phòng vệ

Mô hình phổ biến là **giá trị bất biến ở lá + snapshot collection ở mỗi tầng + không rò rỉ tham chiếu mutable**.

Tạo tệp `DeepImmutableSnapshotDemo.java` (JDK 17+):

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DeepImmutableSnapshotDemo {
    record Member(String name, int score) {
        Member {
            Objects.requireNonNull(name, "name");
            if (score < 0) {
                throw new IllegalArgumentException("score");
            }
        }
    }

    record TeamSnapshot(String teamName, List<Member> members) {
        TeamSnapshot {
            Objects.requireNonNull(teamName, "teamName");
            members = List.copyOf(members);
        }
    }

    public static void main(String[] args) {
        List<Member> source = new ArrayList<>();
        source.add(new Member("An", 10));
        source.add(new Member("Binh", 20));

        TeamSnapshot snapshot = new TeamSnapshot("Core", source);
        source.add(new Member("Chi", 30));

        System.out.println("source size=" + source.size());
        System.out.println("snapshot size=" + snapshot.members().size());
        System.out.println("first=" + snapshot.members().get(0).name());

        try {
            snapshot.members().add(new Member("Dung", 40));
        } catch (UnsupportedOperationException e) {
            System.out.println("snapshot is unmodifiable");
        }
    }
}
```

```bash
javac DeepImmutableSnapshotDemo.java
java DeepImmutableSnapshotDemo
```

Kết quả:

```text
source size=3
snapshot size=2
first=An
snapshot is unmodifiable
```

**Vì sao đồ thị ở đây bất biến theo nội dung của nó?**

1. `Member` là record có thành phần `String` và `int`; `String` bất biến, `int` là giá trị nguyên thủy. Không có trường mutable được lộ.
2. `TeamSnapshot` giữ `teamName` là `String` và `members` là `List<Member>`.
3. Compact constructor của `TeamSnapshot` chuyển `members` thành `List.copyOf(members)`. Sau khi constructor xong, thay đổi cấu trúc của `source` không ảnh hưởng đến danh sách snapshot.
4. Mỗi phần tử trong danh sách là `Member` bất biến. Không có đường nào từ snapshot cho phép đổi tên hoặc điểm của một `Member`.
5. Caller nhận danh sách qua accessor của record nhưng không thể thêm/xóa phần tử của snapshot bằng API danh sách.

Một dạng công thức:

```text
Immutable parent
    + immutable fields
    + defensive copies of incoming collections
    + immutable elements recursively
    + no mutable state leaked through accessors
    = immutable observable object graph
```

Đây là nguyên tắc thiết kế, không phải phép tính tự động. Nếu thay `Member(String, int)` bằng `Member(String, List<StringBuilder>)`, bạn phải kiểm tra tiếp danh sách và từng `StringBuilder`. Nếu trả về trực tiếp một mảng nội bộ, cần sao chép cả lúc nhận lẫn lúc trả, hoặc thay mảng bằng cấu trúc bất biến phù hợp.

### 7.1. Vì sao dùng compact constructor của record có ích?

Trong `record TeamSnapshot(String teamName, List<Member> members)`, compact constructor có thể chuẩn hóa tham số `members` bằng `members = List.copyOf(members)`. Sau khi phần thân compact constructor hoàn tất bình thường, Java tự gán giá trị tham số đã chuẩn hóa cho field tương ứng của record. Điều này giúp mọi instance mới tuân theo cùng hợp đồng snapshot.

Tuy vậy, record vẫn có thể tự định nghĩa accessor/custom method làm lộ object mutable; record chỉ là cơ chế khai báo state carrier với field final, không phải công cụ kiểm định deep immutability.

### 7.2. Constructor phải kiểm tra invariant của toàn bộ trạng thái

Nếu bất biến của một số điểm là tổng điểm không âm hoặc mỗi mã thành viên là duy nhất, nên kiểm tra những điều kiện đó **trước khi công bố snapshot**. Việc validation xảy ra trên dữ liệu đã sao chép hoặc dữ liệu không thể bị người khác thay đổi trong lúc validation sẽ dễ suy luận hơn.

Đặc biệt, nếu caller truyền vào một `ArrayList` mà **thread khác đang sửa đồng thời** khi constructor gọi `List.copyOf`, bản copy không tự bảo đảm snapshot logic nhất quán: đọc một collection đang bị sửa thiếu đồng bộ vẫn có thể là data race. **Copy cần thực hiện khi nguồn ổn định, hoặc dưới cùng cơ chế đồng bộ bảo vệ nguồn.**

## 8. Collection lồng nhau: cần sao chép tại mỗi tầng mutable

Giả sử:

```java
Map<String, List<String>> permissions;
```

Nếu chỉ gọi `Map.copyOf(permissions)`, map gốc không còn được cấu trúc snapshot dùng trực tiếp; tuy nhiên các giá trị kiểu `List<String>` vẫn có thể trỏ tới **những danh sách mutable**. Caller có thể thực hiện `permissions.get("admin").add("delete")`, làm nội dung danh sách trong snapshot đổi theo.

Một cách tạo immutable graph là copy các danh sách giá trị trước, sau đó copy map:

```java
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

static Map<String, List<String>> immutablePermissions(
        Map<String, List<String>> source) {
    Objects.requireNonNull(source);
    Map<String, List<String>> copy = new HashMap<>();
    for (var entry : source.entrySet()) {
        copy.put(
                Objects.requireNonNull(entry.getKey()),
                List.copyOf(entry.getValue()));
    }
    return Map.copyOf(copy);
}
```

Đây là **đoạn phương thức minh họa**; đặt trong một lớp Java thích hợp. Hàm này chỉ tạo deep-enough snapshot vì key và phần tử là `String` bất biến. Nếu giá trị map là `List<MutableUser>`, phải chuyển từng `MutableUser` thành kiểu bất biến hoặc snapshot riêng.

### 8.1. Chuỗi copy phải giữ một thời điểm logic khi cần invariant

Copy nested map trong khi writer khác đang cập nhật **các cấu trúc liên quan** có thể sinh kết quả trộn giữa nhiều phiên bản. Nếu đối tượng nguồn có invariant xuyên qua nhiều collection, lấy bản copy trong một vùng đồng bộ đọc, hoặc tổ chức nguồn đã là immutable snapshot rồi công bố thay thế nguyên khối.

### 8.2. Không phải mọi đồ thị đều cần deep copy đệ quy

Một đối tượng dịch vụ có thể tham chiếu logger dùng chung, bộ đếm atomic hay cache mutable. Khi đó toàn bộ graph vật lý không bất biến. Có thể vẫn thiết kế **configuration snapshot bất biến** tách khỏi các tài nguyên mutable. Hãy xác định chính xác **ranh giới dữ liệu yêu cầu bất biến**, rồi bảo vệ mọi đường truy cập *trong ranh giới ấy*. Gọi một object bất biến sâu trong khi nó vẫn lộ cache mutable là mô tả không chính xác.

## 9. Mảng, `Date` và các kiểu mutable cần sao chép phòng vệ hai chiều

Với kiểu mảng, `final byte[] data` chỉ giữ tham chiếu cuối cùng, không cấm thay `data[i]`. Nếu constructor giữ thẳng mảng đầu vào, caller sửa mảng sau khi tạo object sẽ làm dữ liệu bên trong đổi. Nếu getter trả thẳng mảng nội bộ, caller cũng sửa được từ phía ra.

Tạo tệp `DefensiveArrayDemo.java`:

```java
import java.util.Objects;

public class DefensiveArrayDemo {
    static final class Packet {
        private final byte[] payload;

        Packet(byte[] input) {
            payload = Objects.requireNonNull(input).clone();
        }

        byte[] payloadCopy() {
            return payload.clone();
        }

        int firstUnsignedByte() {
            return Byte.toUnsignedInt(payload[0]);
        }
    }

    public static void main(String[] args) {
        byte[] source = {7, 8};
        Packet packet = new Packet(source);

        source[0] = 99;
        byte[] returned = packet.payloadCopy();
        returned[0] = 55;

        System.out.println("source first=" + source[0]);
        System.out.println("returned first=" + returned[0]);
        System.out.println("packet first=" + packet.firstUnsignedByte());
    }
}
```

```bash
javac DefensiveArrayDemo.java
java DefensiveArrayDemo
```

Kết quả:

```text
source first=99
returned first=55
packet first=7
```

Hai lần `clone()` tạo quyền sở hữu riêng cho mảng. Trường `payload` vẫn trỏ tới object mảng mutable ở mức Java, nhưng lớp `Packet` không để caller có đường sửa mảng nội bộ và bản thân lớp cũng không thay đổi nó; vì vậy trạng thái của `Packet` **bất biến theo quan sát của người sử dụng** trong giao thức này.

Mảng hai chiều `byte[][]` phức tạp hơn: gọi `clone()` trên mảng ngoài chỉ copy tham chiếu tới từng mảng con. Cần clone các mảng con nếu chúng mutable, và cân nhắc đối tượng phần tử khi dùng `Object[]`.

Tương tự, `java.util.Date` là mutable; giữ tham chiếu đầu vào/đưa tham chiếu nội bộ ra getter có thể làm state thay đổi. Có thể copy `Date` cẩn thận hoặc ưu tiên các kiểu immutable trong `java.time` như `Instant`, `LocalDate` và `LocalDateTime` khi chúng đáp ứng nghiệp vụ. Không thay kiểu chỉ vì tên “Date”: còn phải xét ý nghĩa timezone và dữ liệu của domain.

## 10. Immutable object khác unmodifiable object ở điểm nào?

Một **unmodifiable interface/view** ngăn caller thao tác sửa qua một API cụ thể. Một **immutable object** bảo đảm trạng thái của chính nó không thay đổi sau khi tạo theo hợp đồng của kiểu, bất kể có bao nhiêu đường tham chiếu được cung cấp hợp lệ.

Ví dụ `Collections.unmodifiableList` chặn `view.add(x)` nhưng dữ liệu thay đổi qua danh sách gốc vẫn xuất hiện trên view. Đây là read-only view.

`List.copyOf` tạo một danh sách có cấu trúc không sửa được, tách khỏi các thay đổi cấu trúc sau đó của nguồn. Nhưng nếu phần tử mutable, graph phía dưới chưa immutable.

Một object `final class Person { private final Address address; }` chỉ bất biến sâu nếu `Address` cũng bất biến hoặc được copy/bao bọc để không ai còn đường sửa. `final class` ngăn kế thừa lớp, không làm object con bất biến.

Đánh giá tính bất biến là đánh giá **cả kiểu, các đường gán tham chiếu và API công khai**, không chỉ xem có các từ khóa `final` hay `unmodifiable` hay không.

## 11. Bất biến và thread safety: mối quan hệ chính xác

Đối tượng bất biến được xây dựng đúng cách là một công cụ mạnh của **thread safety**, vì nhiều reader có thể đọc mà không cần đồng bộ để bảo vệ chống lại việc object bị sửa — miễn là chúng nhận được object hợp lệ qua một con đường công bố có hợp đồng rõ.

Nhưng cần phân biệt:

1. **Object đã bất biến?** Sau constructor, state nào còn được phép thay đổi?
2. **Object đã được khởi tạo đúng cách?** Có để lộ `this` trong constructor không? Có trả ra đối tượng nửa khởi tạo không?
3. **Tham chiếu đã được công bố an toàn?** Thread khác làm sao nhận object? Qua `volatile`, khóa, concurrent queue, `Thread.start()`, class initialization, hoặc cơ chế chuẩn khác?
4. **Các reader đọc cùng một snapshot?** Nếu một phép tính cần nhiều trường, reader có lấy cùng một object snapshot một lần không?
5. **Có tài nguyên mutable bên ngoài ranh giới snapshot?** Nếu có, phần việc đó vẫn phải được đồng bộ theo giao thức riêng.

### 11.1. `final` có bảo đảm khởi tạo đặc biệt, nhưng không thay safe publication toàn diện

Java Memory Model có bảo đảm đặc biệt đối với giá trị các **instance final fields** của đối tượng được khởi tạo đúng cách: khi constructor hoàn tất mà không để `this` thoát sớm, giá trị final đã khởi tạo có ngữ nghĩa visibility riêng. Đây là nền tảng để các immutable value object hoạt động đáng tin cậy.

Dẫu vậy, chỉ dựa vào final-field guarantee không tạo ra một giao thức chia sẻ đầy đủ: nó không bảo đảm reader nhận tham chiếu vào thời điểm cần thiết, không bảo vệ trạng thái non-final/mutable, và không thay thế các quan hệ đồng bộ cần thiết cho cập nhật sau khởi tạo. Safe publication qua một cơ chế rõ ràng giúp chứng minh trọn vẹn hơn.

Không bắt đầu thread ngay trong constructor để đưa `this` ra ngoài rồi mặc định final fields đã hoàn thành; đó là **this escape**, có thể làm thread khác quan sát state chưa khởi tạo.

### 11.2. Mỗi lần thay snapshot cần công bố nguyên tử

Mô hình thường dùng:

```text
Reader A ---snapshot v1---> [immutable graph v1]
Reader B ---snapshot v1---> [immutable graph v1]

Writer: tạo graph v2 hoàn chỉnh
        validate
        atomic publication (đổi một tham chiếu)

Reader C ---snapshot v2---> [immutable graph v2]
Reader A vẫn giữ v1 hợp lệ, không bị sửa sau lưng.
```

Vì hai graph không bị sửa sau khi công bố, reader lấy được v1 vẫn sử dụng v1 ổn định; reader lấy v2 dùng trạng thái mới. Điểm cần điều phối là **tham chiếu nào được công bố**, không phải khóa từng phép đọc mọi trường con của một graph mutable.

## 12. Ví dụ cập nhật snapshot nguyên tử từ nhiều writer

Một đồ thị bất biến cũng giúp xử lý **cập nhật nhiều trường phải đi cùng nhau**, ví dụ hai số dư có tổng luôn bằng 100. Mỗi writer tạo snapshot mới hợp lệ rồi thực hiện cập nhật atomic ở cấp tham chiếu. Reader lấy tham chiếu **một lần** để không ghép hai thế hệ khác nhau.

Tạo tệp `AtomicImmutableStateDemo.java`:

```java
import java.util.concurrent.atomic.AtomicReference;

public class AtomicImmutableStateDemo {
    record Balances(int left, int right) {
        Balances {
            if (left < 0 || right < 0 || left + right != 100) {
                throw new IllegalArgumentException("invalid balances");
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicReference<Balances> current =
                new AtomicReference<>(new Balances(80, 20));

        Runnable moveOne = () -> {
            for (int i = 0; i < 10; i++) {
                current.updateAndGet(old ->
                        new Balances(old.left() - 1, old.right() + 1));
            }
        };

        Thread a = new Thread(moveOne, "writer-a");
        Thread b = new Thread(moveOne, "writer-b");
        a.start();
        b.start();
        a.join();
        b.join();

        Balances snapshot = current.get();
        System.out.println("left=" + snapshot.left());
        System.out.println("right=" + snapshot.right());
        System.out.println("total=" + (snapshot.left() + snapshot.right()));
    }
}
```

```bash
javac AtomicImmutableStateDemo.java
java AtomicImmutableStateDemo
```

Kết quả:

```text
left=60
right=40
total=100
```

Hai writer mỗi người thực hiện 10 lần chuyển một đơn vị, tổng cộng chuyển 20. `AtomicReference.updateAndGet` có thể thử lại lambda khi các writer cùng cập nhật; mọi lần thành công đều chuyển từ một snapshot hợp lệ sang snapshot hợp lệ khác. Không có một object `Balances` nào bị sửa tại chỗ.

**Điểm cần lưu ý:** hàm truyền cho `updateAndGet` có thể được thực thi **nhiều lần cho một cập nhật thành công** do CAS retry. Vì vậy không ghi log mang side effect không thể lặp, gọi dịch vụ ngoài, gửi thông điệp hay trừ tài khoản thật *bên trong lambda*. Chỉ tính state kế tiếp từ state cũ; các side effect phải có cơ chế điều phối/transaction riêng.

`left + right == 100` là invariant nội bộ của record, nhưng arithmetic int có thể overflow trong ứng dụng lớn. Ví dụ dùng các số nhỏ; nếu domain có số lớn, cần dùng kiểm tra tràn `Math.addExact` hoặc kiểu số phù hợp và validation tổng thể.

**Vấn đề khi đọc:** làm thế này là sai nếu cần snapshot đồng nhất:

```java
int left = current.get().left();
int right = current.get().right();
```

Đây là **đoạn minh họa**, giả định có biến `current` như ví dụ trên. Hai lần `get()` có thể thấy v1 và v2 khác nhau, dù cả hai snapshot riêng đều hợp lệ. Thay bằng:

```java
Balances snapshot = current.get();
int left = snapshot.left();
int right = snapshot.right();
```

Lấy cùng snapshot một lần cho toàn bộ phép tính. `AtomicReference` giải quyết tranh chấp công bố, còn **đối tượng snapshot bất biến** bảo đảm dữ liệu bên trong một phiên bản không đổi.

## 13. Đồ thị bất biến không giải quyết mọi vấn đề concurrency

Một immutable graph giúp chia sẻ dữ liệu đọc an toàn, nhưng **không tự giải quyết** các vấn đề sau:

- **Lost update giữa writer:** nếu writer A và B cùng đọc v1, tính hai bản v2A/v2B rồi mỗi bên gọi `volatileReference = newValue`, một thay đổi có thể ghi đè thay đổi kia. Nếu update phụ thuộc state cũ, dùng CAS/updateAndGet hoặc lock.
- **Thao tác xuyên nhiều tham chiếu:** nếu hai biến `currentConfig` và `currentRules` phải đổi đồng thời, cập nhật riêng từng `volatile` vẫn có thể cho reader tổ hợp sai. Gộp chúng trong **một snapshot gốc**.
- **I/O và tài nguyên ngoài JVM:** snapshot bộ nhớ không tự biến thao tác ghi file/database/network thành transaction nguyên tử. Cần cơ chế của hệ thống lưu trữ/giao tiếp.
- **Chi phí copy:** tạo snapshot mới có thể tốn CPU và bộ nhớ nếu object graph lớn. Có thể dùng persistent data structures chia sẻ cấu trúc bất biến, copy-on-write ở cấp phù hợp hoặc thiết kế domain nhỏ hơn, nhưng phải đo lường.
- **Tác dụng phụ từ accessor/callback:** một “immutable object” có method trả về socket, mutable map hay phát sự kiện tùy thời điểm chưa chắc có hành vi thuần; tách dữ liệu bất biến khỏi dịch vụ có side effect.
- **Object con có mutable cache:** nếu graph có `HashMap` cache được cập nhật ngầm, graph không còn bất biến sâu theo nghĩa vật lý. Nếu cache chỉ là chi tiết ẩn, bản thân cache vẫn phải thread-safe và không làm thay đổi giá trị quan sát được theo hợp đồng object.
- **Lifecycle của snapshot:** giữ vô hạn tham chiếu tới hàng triệu snapshot cũ có thể tốn bộ nhớ. Immutable không đồng nghĩa không cần chính sách vòng đời, thu hồi tham chiếu và kiểm soát bộ nhớ.

Khi dữ liệu thay đổi liên tục với tần suất cao và graph lớn, có thể phù hợp hơn khi dùng một trạng thái mutable được bảo vệ bằng khóa. Không có một lựa chọn duy nhất cho mọi workload.

## 14. Các lỗi thiết kế hay gặp

### 14.1. Dùng `final` ở mọi trường rồi tuyên bố thread-safe

```java
final class Registry {
    private final java.util.Map<String, String> values =
            new java.util.HashMap<>();

    void put(String key, String value) {
        values.put(key, value);
    }
}
```

Đây là lớp minh họa. Trường `values` không bị gán lại, nhưng `HashMap` vẫn mutable và không hỗ trợ ghi/đọc đồng thời không đồng bộ theo hợp đồng. Cần khóa, concurrent map phù hợp, hoặc thay tham chiếu tới một map snapshot bất biến; lựa chọn phụ thuộc invariant nhiều trường hay chỉ hoạt động theo key.

### 14.2. Dùng `List.copyOf` nhưng phần tử vẫn mutable

```java
List<StringBuilder> snapshot = List.copyOf(source);
```

Snapshot cố định cấu trúc danh sách nhưng các `StringBuilder` vẫn có thể đổi. Chuyển phần tử sang `String`/record bất biến, hoặc copy sâu theo nhu cầu.

### 14.3. Constructor copy input nhưng getter trả lại nội bộ mutable

```java
class Payload {
    private final byte[] bytes;

    Payload(byte[] input) {
        bytes = input.clone();
    }

    byte[] getBytes() {
        return bytes; // Rò rỉ mảng nội bộ.
    }
}
```

Lớp minh họa này có phòng vệ **đầu vào** nhưng không phòng vệ **đầu ra**. Caller sửa `getBytes()[0]` sẽ thay dữ liệu bên trong. Sửa bằng `return bytes.clone()` hoặc API truy cập giá trị không cho sửa.

### 14.4. Bọc `Collections.unmodifiableMap` nhưng giữ map gốc và tiếp tục sửa

Một unmodifiable view không tạo snapshot; nếu object gốc tiếp tục thay đổi, reader thấy dữ liệu thay đổi theo. Dùng `Map.copyOf` trên nguồn ổn định (và copy các giá trị mutable lồng nhau) khi cần snapshot bất biến.

### 14.5. Gộp snapshot nhưng vẫn đọc nhiều lần tham chiếu mutable

```java
String host = config.get().host();
int port = config.get().port();
```

Nếu `config` được thay nguyên snapshot giữa hai `get`, caller có thể lấy host v1 và port v2. Lấy `Config snapshot = config.get()` một lần, rồi đọc mọi trường cần thiết từ cùng snapshot.

### 14.6. Tạo immutable object từ một collection đang bị sửa đồng thời

Dùng `List.copyOf(sharedArrayList)` trong khi một thread khác gọi `sharedArrayList.add(...)` không tạo bảo đảm snapshot nhất quán và không biến bản đọc source thành an toàn. Cần nguồn ổn định, lock chia sẻ, hoặc một dữ liệu nguồn đã là immutable snapshot.

### 14.7. Quên validation và exception trước khi công bố

Nếu constructor/factory cho phép tạo một snapshot có invariant sai hoặc để con trỏ tới object đang khởi tạo thoát ra trước khi hoàn tất, không thể dựa vào từ khóa `final` để cứu hợp đồng. Hãy tạo bản hoàn chỉnh, validation, rồi mới công bố tham chiếu sau khi constructor trả về thành công.

## 15. Chiến lược lựa chọn trong ứng dụng Java

| Tình huống | Thiết kế xuất phát phù hợp | Điểm cần kiểm chứng |
| --- | --- | --- |
| Value object nhỏ, chỉ đọc sau khi tạo | Record/class với final fields, thành phần bất biến | Không chứa tham chiếu mutable bị rò rỉ |
| Cấu hình có danh sách/map lồng nhau | Copy phòng vệ từng tầng mutable, immutable phần tử | Đường alias từ caller và nested collections |
| Cần lưu một mảng binary nhưng không cho sửa từ ngoài | Clone input và clone output | Mảng lồng nhau cần copy sâu hơn |
| Cấu hình được thay theo phiên bản, nhiều reader | Một immutable root snapshot + `volatile` hoặc `AtomicReference` | Reader lấy tham chiếu một lần; có nhiều writer hay không |
| Nhiều writer biến đổi state dựa vào bản cũ | CAS/updateAndGet trên immutable snapshot hoặc lock | Lambda retry phải không có side effect |
| State rất lớn, thay đổi liên tục | Mutable state có lock phù hợp hoặc cấu trúc chia sẻ bất biến | Hiệu năng, invariant, nguy cơ deadlock và chi phí copy |
| Giao dữ liệu qua queue giữa các worker | Immutable message + `BlockingQueue` | Công bố an toàn, không sửa message sau khi gửi |
| Tham chiếu tới tài nguyên có vòng đời như socket | Tách tài nguyên mutable khỏi immutable config | Quyền sở hữu, đóng tài nguyên và phối hợp thread |

Hãy chọn dựa trên **đơn vị trạng thái phải nhất quán**, tần suất đọc/ghi, kích thước dữ liệu và khả năng kiểm chứng hợp đồng. Một lớp bất biến nhỏ thường dễ kiểm thử và suy luận hơn một object mutable được nhiều thread chia sẻ; nhưng chi phí tạo snapshot liên tục có thể quan trọng khi graph lớn.

## 16. Tự kiểm tra

**Câu 1.** `final List<MutablePerson> people` có làm object chứa nó bất biến sâu không?

**Trả lời:** Không. `final` chỉ giữ tham chiếu. Collection có thể mutable; ngay cả khi không sửa được cấu trúc collection, từng `MutablePerson` vẫn có thể bị sửa qua alias khác. Cần copy và/hoặc chuyển phần tử thành giá trị bất biến theo hợp đồng.

**Câu 2.** `Collections.unmodifiableList(source)` và `List.copyOf(source)` khác nhau gì khi `source` được thêm phần tử?

**Trả lời:** View không sửa được thường phản ánh thay đổi của `source`; `List.copyOf` cung cấp snapshot cấu trúc tách khỏi những sửa đổi cấu trúc sau đó của source. Cả hai đều không tự đóng băng object phần tử mutable.

**Câu 3.** Record `record Response(List<StringBuilder> values) {}` có bất biến sâu không?

**Trả lời:** Không. Record có component field final nhưng danh sách và `StringBuilder` có thể mutable. Cần copy cấu trúc và thay phần tử bằng kiểu bất biến hoặc tạo bản sao phù hợp.

**Câu 4.** Nếu constructor đã clone `byte[]` input, có thể trả thẳng `byte[]` field qua getter không?

**Trả lời:** Không khi muốn giữ bất biến theo quan sát. Getter trả tham chiếu mutable cho caller sửa phần tử bên trong; phải copy ở đầu ra hoặc cung cấp accessor không cho sửa.

**Câu 5.** Hai writer cùng tạo bản config mới từ cùng bản cũ rồi gán một trường volatile. Điều gì có thể xảy ra?

**Trả lời:** Mỗi phép gán tham chiếu là một lần công bố, nhưng thay đổi logic của một writer có thể bị writer khác ghi đè. Cần CAS/updateAndGet hoặc khóa khi update phụ thuộc state cũ.

**Câu 6.** Một record bất biến có hai trường tương quan. Reader gọi hai lần `current.get()` để lấy mỗi trường. Có bảo đảm lấy hai trường từ cùng thế hệ không?

**Trả lời:** Không. Writer có thể thay root snapshot ở giữa. Reader phải lấy một tham chiếu snapshot duy nhất rồi đọc cả hai trường từ snapshot đó.

**Câu 7.** Vì sao copy `Map<String, List<String>>` một tầng bằng `Map.copyOf` chưa bảo đảm immutable graph?

**Trả lời:** Map snapshot giữ các tham chiếu tới danh sách giá trị; nếu danh sách giá trị mutable, caller có thể sửa chúng. Cần copy danh sách tại từng value, rồi mới copy map. Vì các phần tử là `String`, khi đó graph giá trị không còn phần tử mutable.

**Câu 8.** Bất biến object có khiến mọi thread tự động nhận tham chiếu tới object ấy không?

**Trả lời:** Không. Tính bất biến giải quyết việc state không bị thay đổi sau tạo. Đường **công bố** và chuyển tham chiếu cần một hợp đồng đồng bộ riêng khi ứng dụng yêu cầu khả năng nhìn thấy và tiến trình nhận dữ liệu chắc chắn.

## 17. Tổng hợp

**Immutability** giúp trạng thái được chia sẻ qua các thread ổn định và giảm nhu cầu khóa khi đọc. Nhưng tính bất biến chỉ đáng tin khi xem xét **toàn bộ đồ thị trạng thái quan sát được**: trường final, collection không sửa được, phần tử bên trong, mảng, các đối tượng con và mọi alias còn đường sửa.

Để tạo một **immutable object graph** trong Java, hãy tạo trạng thái đầy đủ trong constructor/factory, kiểm tra invariant, sao chép phòng vệ tại mọi biên mutable, chỉ giữ hoặc trả ra những giá trị không thể bị sửa qua alias khác, và công bố một root snapshot hoàn chỉnh bằng cơ chế bộ nhớ phù hợp. Khi state cần thay đổi, tạo phiên bản mới và thay **một tham chiếu** (hoặc dùng khóa để giữ nhất quán). Một snapshot bất biến không tự loại bỏ việc quản lý nhiều writer, I/O, hủy task hay tài nguyên ngoài JVM, nhưng giúp phần dữ liệu chia sẻ trở nên đơn giản và đáng tin hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-004">Thread confinement và ownership rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# Thread confinement và ownership rõ ràng trong Java Concurrency

> **Phạm vi:** thiết kế thread-safe bằng cách **giới hạn nơi truy cập mutable state** và **quy định một chủ sở hữu rõ ràng** của dữ liệu. Nội dung áp dụng cho Java SE (Java 17+), với các API chuẩn như `ExecutorService`, `Future`, `BlockingQueue` và `ThreadLocal`. Các vấn đề triển khai riêng của Spring được dành cho chương riêng.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích **thread confinement**: một đối tượng có thể thay đổi chỉ được sử dụng bởi một thread tại một thời điểm, và vì sao điều đó có thể loại bỏ nhu cầu khóa khi thao tác nội bộ.
- Phân biệt **thread confinement**, **task confinement**, **ownership**, **immutable data**, **shared mutable state** và **safe publication**.
- Nhận ra lúc nào một tham chiếu **escape** ra ngoài vùng kiểm soát, làm mất confinement dù đối tượng được tạo trong biến local.
- Thiết kế việc **bàn giao ownership** qua queue, `Future.get()`, `join()` hoặc giao thức có *happens-before*; tránh vừa chuyển quyền vừa tiếp tục sửa object.
- Dùng `ThreadLocal` đúng với thread pool, biết khi nào phải `remove()` và vì sao thread-local **khác** dữ liệu chỉ sống trong một request/task.
- Chọn giữa **single owner**, dữ liệu bất biến, chia partition và khóa/atomic phù hợp với invariant của bài toán.
- Kiểm tra tính đúng đắn, tránh data race, sai lệch visibility, thứ tự xử lý bất định, aliasing và rò rỉ trạng thái giữa các task.

**Tư tưởng cốt lõi:** thay vì để nhiều thread trực tiếp cập nhật cùng một cấu trúc rồi cố khóa mọi chỗ, có thể thiết kế để **chỉ một thread sở hữu phần mutable state đó**, còn thread khác chuyển công việc hoặc bản dữ liệu độc lập đến chủ sở hữu. Ownership phải được bảo đảm bằng API và luồng dữ liệu thực tế; một lời chú thích “chỉ thread này dùng” không tự tạo an toàn.

## 2. Vì sao hạn chế chia sẻ thường đơn giản hơn chia sẻ có khóa?

Trong chương trình tuần tự, một `ArrayList`, `HashMap` hay `StringBuilder` có thể được thay đổi thoải mái nếu không có truy cập đồng thời. Khi nhiều thread chia sẻ cùng object mutable mà không có giao thức phối hợp, những thao tác như đọc rồi ghi, kiểm tra rồi cập nhật hoặc thay đổi nhiều trường có thể xen kẽ và phá invariant.

Ví dụ giả định cả hai thread cùng làm:

```java
// Minh họa chưa an toàn khi truy cập đồng thời:
List<Integer> numbers = new ArrayList<>();
// Thread A: numbers.add(1);
// Thread B: numbers.add(2);
```

Đoạn này cần import `java.util.ArrayList` và `java.util.List`; các dòng thread là **mô tả**, không phải chương trình độc lập. `ArrayList` không bảo đảm thread safety cho nhiều writer đồng thời. Không nên kỳ vọng kết quả luôn chứa đủ cả hai phần tử hoặc cấu trúc vẫn ở trạng thái nhất quán.

Có hai hướng thiết kế phổ biến:

1. **Chia sẻ có kiểm soát:** nhiều thread truy cập trạng thái chung nhưng dùng cùng lock/atomic/concurrent collection phù hợp với invariant.
2. **Giảm chia sẻ:** mỗi worker xử lý state riêng; khi cần trao đổi, gửi giá trị bất biến, snapshot hoặc command tới một owner có trách nhiệm duy nhất.

Confinement loại bỏ cuộc đua trên đối tượng **chỉ khi điều kiện không có truy cập đồng thời ngoài owner thật sự được giữ**. Nó không tự biến các thao tác I/O hay giao dịch nhiều service thành nguyên tử, không làm scheduler xác định và không thay thế những ràng buộc ordering cần thiết ở ranh giới chuyển dữ liệu.

## 3. Các khái niệm cần phân biệt

| Khái niệm | Ý nghĩa | Ví dụ |
|---|---|---|
| **Thread confinement** | Một object mutable được dùng trong phạm vi một thread, không bị thread khác truy cập khi còn mutable. | Một `StringBuilder` chỉ có worker hiện tại dùng. |
| **Task confinement** | Dữ liệu và logic riêng của một task, không bị task khác truy cập trong khi thực thi. | `ArrayList` local tạo bên trong `Callable.call()`. |
| **Ownership** | Ai có **quyền đọc/ghi**, khi nào quyền đó bắt đầu/kết thúc và API nào là con đường truy cập hợp lệ. | Consumer duy nhất sửa sổ cái; producer chỉ gửi command. |
| **Ownership transfer** | Quyền xử lý một object chuyển từ bên gửi sang bên nhận qua một ranh giới rõ ràng. | Producer đưa message vào `BlockingQueue`, sau đó không sửa mutable message đó nữa. |
| **Immutable object** | Nội dung quan sát được không đổi sau khi xây dựng và công bố đúng. | `String`, `Integer`, record chỉ chứa các thành phần immutable. |
| **Safe publication** | Cách làm một đối tượng và trạng thái khởi tạo của nó được thread khác quan sát theo bảo đảm của Java Memory Model. | Dữ liệu gửi qua `BlockingQueue` hoặc kết quả đọc qua `Future.get()`. |
| **Shared mutable state** | Một object có thể thay đổi và nhiều thread cùng có đường truy cập. | Cùng một `HashMap` được nhiều worker cập nhật. |

**Confinement là một thuộc tính của đường đi tham chiếu và cách sử dụng**, không phải kiểu dữ liệu. Cùng một `ArrayList` có thể an toàn khi hoàn toàn local, nhưng trở thành shared mutable state khi đưa tham chiếu của nó cho worker khác trong lúc owner cũ vẫn sửa.

**Ownership ở đây là quy ước thiết kế** do chương trình thực thi và kiểm chứng. Java không mặc định có hệ thống kiểu sở hữu/borrow checker tự ngăn bạn chia sẻ object mutable tùy ý. Muốn quy ước đủ mạnh, cần private fields, immutable messages, API giới hạn quyền sửa, quy trình handoff và các test/invariant thích hợp.

## 4. Ba mức confinement thường gặp

### 4.1. Biến local và object chỉ sử dụng trong một lời gọi

```java
String buildLine(String name, int score) {
    StringBuilder builder = new StringBuilder();
    builder.append(name).append(':').append(score);
    return builder.toString();
}
```

Đây là **method minh họa** đặt trong một class. `builder` được tạo, dùng, rồi bỏ trong một invocation; method trả ra `String` bất biến. Nếu lời gọi của hai thread không chia sẻ tham chiếu `builder`, mỗi invocation có state riêng và không cần `synchronized` quanh `append`.

**Cẩn thận:** “biến local” không đồng nghĩa “object private”:

```java
// Minh họa nguy cơ thoát tham chiếu (escape).
ArrayList<String> local = new ArrayList<>();
someSharedObject.store(local); // Thread khác có thể nhận đúng object này.
local.add("changed");          // Không còn cơ sở khẳng định nó bị confined.
```

Đoạn trên giả định `someSharedObject` có method `store`, không phải class chạy độc lập. Việc tham chiếu thoát ra ngoài **có thể** dẫn tới chia sẻ; nguy cơ xảy ra nếu đối tượng được dùng đồng thời và không có phối hợp. Để an toàn, hoặc giữ nó hoàn toàn local, hoặc chuyển **snapshot bất biến** hay handoff có hợp đồng rõ ràng.

Một object được tạo trong constructor cũng không tự an toàn: nếu để `this` thoát ra trong khi object chưa khởi tạo xong (đăng ký callback, start thread giữ `this`, đưa vào global registry), thread khác có thể quan sát trạng thái chưa hoàn chỉnh. Hoàn thành khởi tạo rồi mới công bố bằng cơ chế phù hợp.

### 4.2. Một worker sở hữu state suốt giai đoạn thực thi

Một worker xử lý từng phần dữ liệu trong collection local:

```java
// Bên trong Callable<List<String>>.call()
List<String> results = new ArrayList<>();
for (String item : input) {
    results.add(transform(item));
}
return List.copyOf(results);
```

Đây là **đoạn code minh họa**, `input` và `transform` do bài toán cung cấp. Mỗi task tạo `results` riêng, không thread nào đồng thời mutate nó. `List.copyOf` tạo danh sách không cho thay đổi cấu trúc; để dữ liệu **bất biến sâu**, bản thân các phần tử `String` ở ví dụ cũng phải bất biến. Nếu phần tử là những bean mutable, copy danh sách **không** tự đóng băng các bean.

### 4.3. Một chủ sở hữu lâu dài cho phần trạng thái chung

Ví dụ một consumer quản lý số dư trong một biến chỉ tồn tại ở thread của nó. Các producer không sở hữu số dư; họ gửi lệnh `Add(amount)` qua queue. Chỉ consumer có quyền áp dụng lệnh, do đó **mọi cập nhật số dư nối tiếp trong một owner**. Chương trình bên ngoài không lấy reference tới biến mutable để sửa trực tiếp.

Đây là mô hình **single-writer / actor-style ownership** ở mức thiết kế, có thể xây bằng Java SE `BlockingQueue`. Không cần gọi một framework actor để có nguyên tắc này.

Single owner thường giúp đơn giản hóa invariant của chính state mà owner nắm giữ. Tuy nhiên, nếu các state khác nhau cần commit nguyên tử với nhau, ownership mỗi phần riêng **không tự cung cấp transaction xuyên owner**.

## 5. Ownership rõ ràng: ai được làm gì, tại thời điểm nào?

Một bản thiết kế tốt nên có câu trả lời cụ thể cho các câu hỏi:

1. **Ai tạo object?** Ai giữ bản tham chiếu mutable ban đầu?
2. **Ai được sửa?** Một thread, một task, một consumer lâu dài, hay nhiều thread qua cùng lock?
3. **Khi nào quyền chuyển?** Khi đặt vào queue, khi `Future` hoàn thành, khi hoàn tất `join`, hay sau thao tác đồng bộ nào?
4. **Bên gửi còn được làm gì?** Có được tiếp tục sửa message sau `put()` không? Nếu không, cách nào ngăn các đường ghi ngoài ý muốn?
5. **Bên nhận biết dữ liệu đã sẵn sàng bằng gì?** Có quan hệ happens-before để quan sát giá trị đúng không?
6. **Lúc kết thúc ai dọn tài nguyên?** Thread sở hữu có đóng file/socket/context và báo lỗi cho bên gọi không?

### Một quy ước owner nên biểu diễn qua API

Ví dụ API ở mức ý tưởng:

```java
interface LedgerCommands {
    void submitCredit(long amount) throws InterruptedException;
    // Không trả về mutable Map<Long, Account> nội bộ để caller tự sửa.
}
```

Đây là **interface minh họa**. Producer chỉ submit, consumer sở hữu dữ liệu. Nếu cần lấy kết quả, cung cấp một **snapshot**, `Future` hoặc response message với quy tắc memory visibility và lifecycle rõ ràng. Đừng trả về reference đến `HashMap`/`ArrayList` nội bộ rồi trông chờ caller “nhớ không sửa”.

## 6. Ownership transfer khác việc chia sẻ cùng tham chiếu

Khi hai thread lần lượt xử lý một mutable object, có thể dùng **handoff**:

```text
Thread producer                              Thread consumer
------------------------------------------   -----------------------------------
Tạo object, owner = producer
Thiết lập đầy đủ dữ liệu
Đưa message vào BlockingQueue
Không sửa hoặc đọc-mutate object sau handoff
                                             queue.take() nhận message
                                             owner = consumer
                                             Đọc/ghi state theo contract
                                             Tạo snapshot / gửi phản hồi
```

Với `BlockingQueue`, các hành động xảy ra trong thread trước khi đặt phần tử vào queue *happen-before* các hành động trong thread khác sau khi truy cập hoặc lấy phần tử đó khỏi queue, theo hợp đồng memory consistency của `java.util.concurrent`. Điều đó bảo đảm **publication/visibility** qua queue.

Nhưng handoff có **hai điều kiện độc lập**:

- **Visibility/order:** bên nhận phải quan sát được dữ liệu đã chuẩn bị trước khi gửi; queue/Future/lock có thể cung cấp HB.
- **Exclusive ownership:** bên gửi **không tiếp tục mutate** object sau khi bàn giao nếu bên nhận có thể đang dùng nó; queue **không** làm biến mất các tham chiếu còn lưu ở producer.

Sai nếu producer vẫn sửa `message` sau `queue.put(message)` và consumer đọc đồng thời: queue bảo đảm những ghi **trước put**, không biến mọi ghi **sau put** thành an toàn. Nếu cần cho cả hai bên cùng đọc, dùng immutable object/snapshot; nếu cần sửa ở cả hai, phải có giao thức chuyển quyền qua lại hoặc bảo vệ mọi truy cập bằng synchronization thích hợp.

Đây là khác biệt giữa **safe publication** và **ongoing thread safety**: một object được công bố đúng vẫn có thể bị data race khi cùng mutate về sau.

### Làm rõ aliasing

Hai biến khác tên vẫn có thể trỏ **cùng object**:

```java
ArrayList<Integer> first = new ArrayList<>();
List<Integer> second = first;
second.add(1);
System.out.println(first); // [1]
```

Đoạn có thể biên dịch trong một method (cần import `List`, `ArrayList`). Đây là aliasing bình thường, không tự là race. Nhưng nếu thread A dùng `first` và thread B dùng `second` đồng thời, confinement bị phá vì đó là **một object duy nhất**. Muốn tạo hai bản độc lập, dùng copy với semantics phù hợp; shallow copy chưa tách các phần tử mutable.

## 7. Safe publication và Java Memory Model

Confinement không cần đồng bộ cho mọi bước cập nhật **bên trong một owner**. Nhưng khi dữ liệu chuyển owner hoặc được công bố, Java Memory Model vẫn quan trọng.

Một số ranh giới hợp lệ thường dùng:

| Ranh giới | Bảo đảm liên quan | Dùng đúng khi |
|---|---|---|
| `Thread.start()` | Hành động trước `start()` HB hành động trong thread được start. | Chuẩn bị dữ liệu trước khi thread bắt đầu. |
| `Thread.join()` thành công | Hành động của thread đã kết thúc HB thread khác sau khi xác nhận kết thúc qua join. | Owner hoàn thành rồi caller đọc/thu kết quả. |
| `ExecutorService.submit()` → `Future.get()` | Hành động trước submit HB task; hành động task HB các hành động sau `Future.get()` thành công. | Trả kết quả từ worker về caller. |
| `BlockingQueue.put/offer` → `take/poll` nhận phần tử | Hành động trước khi đặt phần tử HB hành động sau khi lấy/truy cập phần tử ở thread khác. | Gửi dữ liệu/command giữa owner. |
| `synchronized` unlock → lock cùng monitor | Release/Acquire tạo `synchronizes-with` và HB theo thứ tự. | Chia sẻ mutable state có khóa. |
| `volatile` write → read phù hợp | Bảo đảm ordering/visibility liên quan biến volatile. | Cờ/công bố đơn giản có giao thức đúng, không có compound atomicity. |

HB bảo đảm visibility/ordering của các hành động liên quan. **HB không đồng nghĩa object tự động immutable, và không buộc các thread hoàn thành theo một lịch duy nhất.**

Với `Future.get()`, caller nên kiểm tra lỗi `ExecutionException`, `InterruptedException`; lời gọi bị hủy/không hoàn tất không thể xem như đã có kết quả sẵn sàng. Trong các API trả kết quả có hiệu lực thông qua hợp đồng cụ thể, đọc lại JavaDoc tương ứng thay vì suy rằng một lần sleep đã đủ cho phép nhận object.

**Trường `final` và immutable objects:** Java có các bảo đảm đặc biệt về final fields khi object được xây dựng đúng và không làm `this` escape trong constructor. Nhưng `final List<T> items` chỉ làm tham chiếu `items` không được gán lại; nếu list còn mutable và bị chia sẻ, phần tử vẫn có thể thay đổi. Hãy phân biệt **immutable reference**, **immutable container**, và **deeply immutable object graph**.

## 8. Ví dụ thực hành Java 17+: task-local data và single owner

Chương trình có hai phần:

- Hai `Callable` mỗi cái xây một `ArrayList<Integer>` **riêng** rồi trả `List.copyOf(local)`. Caller lấy hai kết quả bằng `Future.get()` và tự gộp ở thread main.
- Một thread duy nhất sở hữu biến `balance`. Main gửi lệnh cộng qua `ArrayBlockingQueue`; consumer là **nơi duy nhất** cập nhật `balance`. Giá trị `Integer` gửi qua queue là immutable.

Tạo file **`OwnershipDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class OwnershipDemo {
    static final class BuildChunk implements Callable<List<Integer>> {
        private final int first;
        BuildChunk(int first) { this.first = first; }

        @Override
        public List<Integer> call() {
            ArrayList<Integer> local = new ArrayList<>();
            local.add(first);
            local.add(first + 1);
            return List.copyOf(local);
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<List<Integer>> one = executor.submit(new BuildChunk(1));
            Future<List<Integer>> two = executor.submit(new BuildChunk(3));

            List<Integer> merged = new ArrayList<>();
            merged.addAll(one.get());
            merged.addAll(two.get());
            System.out.println("merged=" + merged);
            System.out.println("sum=" + merged.stream().mapToInt(Integer::intValue).sum());
        } finally {
            executor.shutdown();
        }

        BlockingQueue<Integer> inbox = new ArrayBlockingQueue<>(4);
        Thread owner = new Thread(() -> {
            int balance = 0; // Chỉ thread này có quyền thay đổi.
            try {
                while (true) {
                    int command = inbox.take();
                    if (command == Integer.MIN_VALUE) break; // Dừng sau các lệnh.
                    balance += command;
                }
                System.out.println("ownerTotal=" + balance);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "account-owner");

        owner.start();
        inbox.put(5);
        inbox.put(7);
        inbox.put(Integer.MIN_VALUE);
        owner.join();
    }
}
```

Chạy với JDK 17 hoặc mới hơn:

```bash
javac --release 17 OwnershipDemo.java
java OwnershipDemo
```

**Stdout đã kiểm chứng:**

```text
merged=[1, 2, 3, 4]
sum=10
ownerTotal=12
```

### Vì sao kết quả đúng dù scheduler không cố định?

1. Mỗi `BuildChunk.call()` chỉ tạo và sửa danh sách `local` **trong worker của chính task đó**; các worker không chia sẻ danh sách mutable.
2. `List.copyOf(local)` trả về danh sách không hỗ trợ sửa cấu trúc. Từng phần tử là `Integer` immutable, nên kết quả ở đây không chứa object mutable cần phối hợp bổ sung.
3. Main gọi `one.get()` và `two.get()` theo **thứ tự cố định** rồi mới `addAll` vào `merged` (do main sở hữu). Dù task thứ hai chạy xong trước, thứ tự gộp vẫn là `[1, 2, 3, 4]`. `Future.get()` cho caller ranh giới hoàn tất và memory consistency phù hợp.
4. `balance` là biến local nằm trong lambda của consumer và **không tham chiếu ra ngoài** để thread khác sửa. Các command gửi trong queue được consumer lấy theo thứ tự FIFO của `ArrayBlockingQueue`.
5. Main gửi `5`, `7` rồi sentinel `Integer.MIN_VALUE`. Consumer xử lý hai số đầu, sau đó dừng; kết quả luôn `12` khi chương trình hoàn thành bình thường.
6. `owner.join()` chờ receiver kết thúc trước khi main thoát; chương trình không phụ thuộc vào delay hay `sleep()`.

**Giới hạn chủ đích của ví dụ:** sentinel `Integer.MIN_VALUE` là tín hiệu kết thúc được dành riêng, nên hệ thống thực phải bảo đảm giá trị này **không là dữ liệu hợp lệ**, hoặc nên dùng kiểu thông điệp tách riêng như `record Add(int amount)` và `enum Stop`. Ví dụ chỉ có **một producer** để làm rõ thứ tự FIFO; với nhiều producer, các message từ producer khác nhau có thể xen kẽ, và việc xác định thứ tự nghiệp vụ cần contract rõ. Nếu consumer ném exception, hệ thống thật phải báo lỗi thay vì chỉ im lặng: có thể dùng `Future`, kênh phản hồi hoặc completion signal. `shutdown()` ở đây dừng nhận task mới cho executor; trong ứng dụng dài hạn cần có chiến lược await/close theo lifecycle và xử lý cancellation.

**Điều quan trọng:** không có `synchronized` quanh mỗi lần cộng `balance`, vì không có thread thứ hai cập nhật biến đó. Nhưng `BlockingQueue` vẫn là primitive đồng bộ giữa các thread. “Không cần lock cho state local” **không có nghĩa mọi giao tiếp xuyên thread đều không cần đồng bộ**.

## 9. ThreadLocal: một bản giá trị gắn với mỗi thread

`ThreadLocal<T>` cấp cho **mỗi thread** một value riêng theo cùng một `ThreadLocal` key. Nó hữu ích cho state phụ trợ đòi hỏi gắn với thread, chẳng hạn buffer ngắn hạn không muốn truyền qua nhiều lớp method, nhưng cần hiểu giới hạn.

Ví dụ:

```java
private static final ThreadLocal<StringBuilder> BUFFER =
        ThreadLocal.withInitial(StringBuilder::new);

static String format(String name, int quantity) {
    StringBuilder builder = BUFFER.get();
    try {
        builder.setLength(0);
        builder.append(name).append(':').append(quantity);
        return builder.toString();
    } finally {
        BUFFER.remove();
    }
}
```

Đây là **các thành viên minh họa của một class**; yêu cầu import mặc định `java.lang` là đủ. `BUFFER.get()` trong mỗi thread trả về bản `StringBuilder` của thread đó. Vì mỗi `StringBuilder` chỉ được dùng bởi thread gắn với nó và không được công bố cho thread khác, thao tác append không cần synchronized.

**Vì sao dùng `remove()`?** Worker của `ExecutorService` có thể được tái sử dụng cho nhiều task khác nhau; giá trị `ThreadLocal` có thể sống cùng worker và gây rò trạng thái, bộ nhớ hoặc dữ liệu của task trước sang task sau. `remove()` ở `finally` giúp xóa binding của thread hiện tại kể cả khi task ném exception. Với use case chỉ một method đơn giản, một biến local `new StringBuilder()` thường rõ ràng hơn và tránh quản lý lifecycle của `ThreadLocal`.

### Những giới hạn quan trọng của ThreadLocal

- **Thread-local không đồng nghĩa request-local/task-local.** Hai request chạy tuần tự trên cùng pooled thread có thể thấy cùng value nếu không reset/remove; một request chuyển sang thread khác lại thấy value khác.
- `ThreadLocal` không copy sâu object được gán vào. Nếu hai thread gọi `set(sharedMutableObject)` với **cùng tham chiếu object**, cả hai vẫn có thể race trên object đó dù ThreadLocal keys độc lập.
- `ThreadLocal` không tạo khóa/transaction cho resource dùng chung (file, DB, socket, static map).
- `InheritableThreadLocal` có semantics kế thừa khác, không biến state thành an toàn khi dữ liệu mutable được chia sẻ; không giả định nó tự truyền đúng qua thread pool.
- Đừng dùng ThreadLocal để giấu tham số nghiệp vụ quan trọng đến mức không thể biết owner, cleanup hoặc đường truyền dữ liệu. Context explicit thường dễ kiểm thử hơn.
- Với **virtual threads (Java 21+)**, ThreadLocal vẫn tồn tại nhưng mỗi virtual thread có lifecycle riêng; tạo số lượng cực lớn thread kèm state nặng có thể tốn bộ nhớ. Không thiết kế logic dựa vào “thread nhất định được tái sử dụng” khi dùng virtual threads.

## 10. Single-writer ownership so với synchronized / atomic

Confinement là một **lựa chọn thiết kế**, không phải luôn là thay thế tối ưu cho các cơ chế khác.

| Tình huống | Cách tiếp cận phù hợp | Điều phải chứng minh |
|---|---|---|
| Mỗi task tính một kết quả độc lập | Local state rồi immutable result/`Future.get()` | Không escape mutable object; kết quả công bố đúng |
| Nhiều producer phát command, một state owner | `BlockingQueue` + single consumer | Đúng ordering, backlog/backpressure, shutdown, error handling |
| Nhiều task truy cập cùng một invariant cần đáp ứng tức thời | `synchronized` / `ReentrantLock` | Mọi read/write liên quan dùng cùng protocol |
| Bộ đếm độc lập cần cập nhật từ nhiều thread | `AtomicInteger` / `LongAdder` (theo ngữ nghĩa cần) | Atomicity và snapshot semantics phù hợp |
| Mỗi key có thể xử lý độc lập | Partition/sharding theo key và owner | Không có cập nhật xuyên partition phá invariant |
| Dữ liệu chia sẻ để đọc, không thay đổi | Immutable snapshot công bố an toàn | Không giữ tham chiếu mutable lồng bên trong |
| Một ít buffer phụ trợ theo thread | Local variable hoặc `ThreadLocal` | Không chia sẻ reference, có cleanup nếu dùng pool |

**Thread confinement không có nghĩa “thread affinity là miễn phí”.** Chuyển tất cả request qua một owner làm giảm cạnh tranh cập nhật nhưng có thể tạo queue bottleneck, tăng latency, tràn hàng đợi hoặc khiến một thread phải gánh quá nhiều việc. Với CPU-bound tasks độc lập, partition thành nhiều owner có thể khai thác nhiều core hơn.

**Dùng một owner cho một invariant** khi tính đúng đắn đơn giản hơn; nếu state phải cập nhật atomically cùng nhiều owner khác, có thể cần giao thức phối hợp phức tạp hơn. Đừng chia nhỏ ownership chỉ vì muốn thêm parallelism khi không giữ được invariant.

## 11. Common pitfalls: “chỉ một thread sửa” vẫn có thể sai

### 11.1. Một phía dùng lock, phía còn lại đọc trực tiếp

```java
// Minh họa sai khi truy cập cùng trạng thái không theo một protocol.
synchronized (guard) {
    sharedValue++;
}
// Ở thread khác: int value = sharedValue; // đọc không có cùng khóa/HB
```

Đây là đoạn code minh họa với `guard` và `sharedValue` đã khai báo. Writer khóa đúng không đủ nếu reader đọc mutable state mà không có quan hệ HB. Confinement chỉ đúng khi reader **không đọc trực tiếp** trong giai đoạn chỉ owner sở hữu; hãy gửi snapshot hoặc dùng cùng synchronization protocol.

### 11.2. Bàn giao xong vẫn sửa object

```java
// Minh họa sai nếu producer tiếp tục mutate sau publication.
List<String> message = new ArrayList<>();
message.add("before");
queue.put(message);
message.add("after"); // consumer có thể đang đọc message.
```

Đây là đoạn minh họa trong method `throws InterruptedException`, với `queue` giả định là `BlockingQueue<List<String>>`. `put` bảo đảm visibility cho những hành động **trước put**; nó không biến việc đồng thời sửa object thành an toàn. Khắc phục bằng immutable `List.copyOf(message)`, ownership handoff dứt khoát hoặc lock bao phủ tất cả read/write.

### 11.3. Trả reference tới state nội bộ

```java
class Owner {
    private final List<String> data = new ArrayList<>();

    List<String> getData() {
        return data; // caller có thể mutate mà owner không biết.
    }
}
```

Đây là class minh họa cần import `java.util.List`, `java.util.ArrayList`. `private` chỉ bảo vệ **field reference** khỏi truy cập trực tiếp, còn method đã trả object mutable ra ngoài. Dùng snapshot `List.copyOf(data)` khi đang ở đúng owner hoặc có khóa phù hợp; nếu element mutable, cũng cần snapshot/copy đến độ sâu mà contract yêu cầu.

### 11.4. Dùng ThreadLocal để giữ “owner” của request trong pool

Nếu một task đặt dữ liệu vào ThreadLocal rồi task tiếp theo có khả năng chạy trên **worker khác**, request context không theo task. Nếu task tiếp theo chạy trên **worker cũ**, giá trị có thể còn sót từ request trước. Do đó ThreadLocal không phải công cụ đảm bảo ownership theo request; dùng tham số explicit, immutable context, hoặc API truyền context có lifecycle rõ ràng.

### 11.5. Chỉ có một writer nhưng nhiều reader không phối hợp

**Single-writer không tự suy ra mọi reader đều thread-safe**. Nếu writer sửa một `HashMap` trong khi reader khác duyệt nó mà không đồng bộ, vẫn có data race; một writer chỉ giúp tránh *writer–writer race*, không tự loại bỏ *writer–reader race*. Cần immutable snapshot, copy-on-write, read/write locking hoặc protocol mà readers không truy cập trạng thái đang mutate.

### 11.6. Giữ tham chiếu mutable trong immutable wrapper

`List.copyOf(list)` làm **danh sách kết quả không thể add/remove**, nhưng nếu phần tử là `Order` mutable, `get(0).setStatus(...)` vẫn có thể thay đổi đối tượng. Java `record` cũng chỉ mặc định làm các component fields `private final`; record chứa `List<MutableItem>` không tự deep immutable. Cần defensive copy, immutable element types và constructor/accessor phù hợp.

### 11.7. Tưởng thứ tự lấy việc là thứ tự xong việc

Queue FIFO cho một consumer xác định thứ tự **nhận** command theo thứ tự enqueue, nhưng hệ thống có nhiều producer có thứ tự enqueue tổng có thể thay đổi; thêm I/O bất đồng bộ có thể làm các side effect hoàn tất khác thứ tự. Nếu nghiệp vụ cần total order, sequence number hoặc một owner điều phối, phải định nghĩa rõ các điểm cấp thứ tự và hoàn thành.

## 12. Vòng đời và lỗi của owner

Thiết kế owner không dừng ở câu “consumer là thread duy nhất sửa state”. Nó còn cần trả lời các tình huống thực:

1. **Khởi động:** owner được tạo trước khi nhận command, hay queue có thể đệm command trong lúc owner khởi động?
2. **Dừng:** caller có gửi tín hiệu kết thúc sau mọi producer không? Có drain queue hay bỏ task? Sentinel có thể xung đột với dữ liệu thật không?
3. **Backpressure:** queue có giới hạn hay không? `put()` block khi đầy, `offer(timeout)` có thể báo thất bại; chính sách đó phải rõ.
4. **Exception:** nếu handler lỗi, owner có cần phản hồi lỗi cho caller và vẫn giữ state nhất quán không? Exception không tự hoàn tác cập nhật đã làm.
5. **Cancellation/interrupt:** một worker đợi `take()` có thể bị interrupt; phải xử lý cleanup và trạng thái kết thúc, không nuốt `InterruptedException`.
6. **Resource ownership:** ai chịu trách nhiệm đóng resource (`AutoCloseable`) và vào thời điểm nào? Đừng để producer đóng resource trong khi consumer còn đang dùng.
7. **Memory:** queue có thể tích lũy rất nhiều message nếu producer nhanh hơn consumer. Dùng bounded queue và quan sát queue length, processing time, latency, rejection/backpressure.

Với `ExecutorService`, gọi `shutdown()` ngừng nhận task mới nhưng không tự chờ mọi task hoàn tất. Dùng `Future.get()` với task quan trọng hoặc `awaitTermination()` khi cần ranh giới đóng sạch; xử lý `InterruptedException`/`ExecutionException`/timeout theo contract. Không dùng `Thread.sleep()` để thay thế việc đồng bộ lifecycle.

## 13. Thiết kế ownership rõ ràng khi có nhiều worker

Có một số mô hình tổ chức thông dụng:

**Map-reduce cục bộ:** mỗi worker tạo accumulator riêng; sau `Future.get`, main merge. Ưu điểm: ít contention. Nếu tổng hợp cần kết quả chính xác trên kiểu số có thể overflow hoặc số thực không kết hợp tuyệt đối, phải định nghĩa thứ tự và chính sách độ chính xác.

**Partition theo key:** `hash(key)` hoặc quy tắc định tuyến đưa tất cả update của cùng key tới owner duy nhất. Các key độc lập có thể chạy song song. Cần ổn định routing khi resize/rebalance và đồng bộ khi chuyển quyền sở hữu partition.

**Event loop / actor-style:** một thread xử lý tuần tự queue command và sở hữu state. Rất phù hợp khi state có nhiều trường liên quan, nhưng phải tránh blocking I/O dài khiến queue bị tắc. Nếu cần async I/O, phải thiết kế callback/reentry và thứ tự áp dụng kết quả.

**Snapshot publishing:** owner tạo một snapshot hoàn chỉnh rồi công bố an toàn cho nhiều reader. Snapshot immutable cho phép reader đọc không khóa trong suốt vòng đời snapshot; thế hệ tiếp theo được tạo ở nơi riêng rồi thay snapshot theo cơ chế publication phù hợp (ví dụ volatile reference đến immutable snapshot). Đây là chiến lược tốt cho đọc nhiều/ghi ít, nhưng có chi phí copy và không tạo transaction với các side effect bên ngoài.

### Sơ đồ lựa chọn nhanh

```text
Có mutable state cần xử lý?
    |
    +-- Không: immutable data / pure function -> chia sẻ đọc an toàn khi công bố đúng
    |
    +-- Có
         |
         +-- Chỉ một task dùng, không escape? -> task-local / thread confinement
         |
         +-- Có nhiều producer, một nơi cập nhật state?
         |       -> single owner + queue/command + backpressure
         |
         +-- Nhiều thread cần cập nhật cùng invariant?
                 -> synchronized / Lock / atomic / dữ liệu phân vùng
                    theo đúng granularity và contract
```

Sơ đồ là hướng dẫn kiến trúc ban đầu; không phải thuật toán cho mọi bài toán. Việc lựa chọn còn phụ thuộc throughput, latency, dữ liệu cần nhất quán, failure recovery và nhu cầu nhiều process.

## 14. Thread confinement và Java 21+ virtual threads

Virtual threads dùng mô hình rất nhiều thread Java nhẹ hơn platform threads, nhưng **quy tắc ownership và data race vẫn áp dụng**:

- Một mutable object chỉ được một virtual thread dùng và không escape vẫn có thể confined.
- `ThreadLocal` gắn với từng virtual thread; khác với tập nhỏ các worker platform thread tái sử dụng. Mỗi value lớn nhân với số lượng virtual thread lớn có thể tốn bộ nhớ.
- Đừng suy rằng “virtual thread có ít overhead” đồng nghĩa có thể chia sẻ `ArrayList/HashMap` mutable giữa chúng mà không khóa.
- Bounded queues, backpressure và state partitioning vẫn cần thiết khi downstream hữu hạn.

Ví dụ thực hành của bài **chạy trên Java 17** với platform threads. Virtual threads là cơ chế được chuẩn hóa từ Java 21; không cần dùng chúng để hiểu thread confinement.

## 15. Kiểm thử và đánh giá kiến trúc confinement

### Kiểm chứng correctness

1. Vẽ **ownership map**: mỗi object mutable, ai tạo, ai được đọc, ai được ghi, nơi bàn giao, nơi kết thúc lifecycle.
2. Kiểm tra **escape paths**: return reference, field static, listener/callback, lambda capture, shared collection, queue, constructor leak.
3. Với mỗi handoff, xác định **happens-before** nào làm dữ liệu đã chuẩn bị hiển thị cho receiver.
4. Kiểm tra bên gửi **có còn được phép truy cập/mutate** sau handoff không; dùng immutable snapshot hoặc không giữ reference nếu cần.
5. Stress test nhiều producers/consumers, nhiều thứ tự completion, cancellation và queue full; assert invariant, số message được xử lý, đúng lỗi/timeout.
6. Test cleanup `ThreadLocal.remove()` với pooled workers chạy nhiều task nối tiếp; chú ý tránh rò dữ liệu xuyên task.
7. Đo throughput, latency p95/p99, queue depth, CPU và memory. Nếu một owner thành bottleneck, cân nhắc partition theo invariant có thể tách an toàn.

### Ví dụ assertion có ý nghĩa

Nếu gửi 100 lệnh tăng `1` qua một consumer và chờ completion, tổng phải bằng 100. Nhưng nếu có khả năng retry lệnh, phải xác định lệnh được áp dụng đúng một lần hay có idempotency key; single-owner chỉ làm quá trình cập nhật tuần tự, **không tự mang lại exactly-once delivery**.

Với mô hình task-local + merge, kiểm tra đầy đủ phần tử, không trùng/bỏ sót, và input không bị worker thay đổi ngoài ý muốn. Tránh test “chạy 1000 lần không crash” làm bằng chứng duy nhất cho thread safety.

## 16. Các lỗi hiểu nhầm thường gặp

1. **“Object được tạo trong biến local thì chắc chắn chỉ một thread dùng.”** Sai nếu tham chiếu escape vào shared field, callback, queue hoặc được truyền cho thread khác khi vẫn đang mutate.
2. **“Đã dùng BlockingQueue thì có thể sửa message sau put.”** Sai; queue đảm bảo publication cho hành động trước handoff, không bảo vệ những ghi ngoài owner sau đó.
3. **“Một writer duy nhất nên reader nào cũng đọc được không khóa.”** Sai; writer–reader data race vẫn tồn tại nếu chia sẻ mutable state thiếu synchronization.
4. **“ThreadLocal = task-local = request-local.”** Sai; thread pool tái sử dụng thread và task có thể đổi worker.
5. **“List.copyOf làm tất cả element bất biến.”** Sai; copy container có thể vẫn chứa reference đến element mutable.
6. **“final field nghĩa là object immutable.”** Sai; `final List` vẫn có thể chứa và trỏ tới cấu trúc mutable.
7. **“Dùng synchronized quanh worker là bắt buộc để thread-safe.”** Không, nếu worker chỉ sửa state riêng và dùng handoff có memory semantics rõ; lock có thể không cần ở phần xử lý local.
8. **“Single owner bảo đảm mọi tác vụ hoàn thành đúng thứ tự gửi.”** Còn phụ thuộc thứ tự enqueue của nhiều producer, xử lý async và contract phản hồi.
9. **“Future trả ra dữ liệu đồng nghĩa worker đã bảo đảm mọi side effect ngoài JVM thành công.”** Không; `Future.get()` cho kết quả/ngoại lệ của task, không phải transaction ở database hay network.
10. **“Không dùng lock nghĩa là không có đồng bộ.”** Các primitive như `BlockingQueue`/`Future` vẫn phối hợp thread và mang memory guarantees.
11. **“Chỉ cần nhanh là tốt.”** Phải ưu tiên invariant, visibility, lifecycle và tính đúng đắn; sau đó mới đo contention/throughput/latency.
12. **“Thread confinement tự động ngăn deadlock.”** Nó giảm nhu cầu dùng lock cho state local, nhưng owner vẫn có thể kẹt đợi phản hồi từ owner khác hoặc blocking queue, tạo vòng phụ thuộc và không tiến triển.

## 17. Câu hỏi tự kiểm tra và đáp án

**Câu 1.** Thread A tạo `ArrayList` trong một method, truyền tham chiếu đó cho B rồi tiếp tục `add` trong khi B duyệt list. Có thread confinement không?  
**Đáp án:** Không. Object đã escape và có hai bên truy cập đồng thời. Cần bàn giao ownership dứt khoát, tạo snapshot immutable, hoặc phối hợp mọi read/write.

**Câu 2.** Vì sao `BlockingQueue.put(message)` hữu ích nhưng chưa đủ nếu producer vẫn sửa `message` sau đó?  
**Đáp án:** Queue có HB từ hành động trước put tới hành động receiver sau take, nhưng không tự ngăn data race trên những cập nhật sau khi put. Phải dừng mutate hoặc dùng protocol khác.

**Câu 3.** Hai `Callable` mỗi cái tự xây `ArrayList<Integer>` rồi trả `List.copyOf`; main đợi `Future.get()` trước khi gộp. Có cần khóa từng lần `local.add()` không?  
**Đáp án:** Không, nếu mỗi danh sách thật sự chỉ thuộc worker của nó; `get` tạo ranh giới hoàn tất và visibility. Main gộp trên một list mà main sở hữu.

**Câu 4.** Một pooled thread dùng `ThreadLocal<StringBuilder>` cho task trước, task sau tiếp tục dùng cùng worker. Có thể còn state không mong muốn không?  
**Đáp án:** Có. `ThreadLocal` gắn với thread và có thể tồn tại qua nhiều task. Reset state và `remove()` trong `finally` theo lifecycle.

**Câu 5.** Một object có `private final List<Order> orders`; caller lấy bản `List.copyOf(orders)`. Có bảo đảm caller không thể thay đổi `Order` bên trong không?  
**Đáp án:** Không. `List.copyOf` chỉ bảo vệ cấu trúc list, không deep-freeze phần tử mutable. Cần immutable `Order` hoặc defensive deep snapshot.

**Câu 6.** Tại sao single-writer thường giảm lock contention nhưng không bảo đảm luôn nhanh hơn shared-state có khóa?  
**Đáp án:** Single consumer có thể thành bottleneck, queue có chi phí/hàng đợi và producer cần backpressure. Kết quả phụ thuộc tải và khả năng partition.

**Câu 7.** Handoff bằng `Thread.sleep(100)` giữa producer và consumer có đủ để công bố dữ liệu an toàn không?  
**Đáp án:** Không. Sleep không tạo một quan hệ HB phù hợp cho việc chia sẻ state; dùng queue, lock, volatile protocol, `Future` hoặc primitive có contract đúng.

**Câu 8.** Nếu một thread sở hữu `balance` nhưng nhiều thread có thể đọc trực tiếp qua getter không đồng bộ, còn được xem là confinement an toàn không?  
**Đáp án:** Không, vì dữ liệu mutable được chia sẻ với reader thiếu giao thức. Trả snapshot qua owner hoặc bảo vệ reader bằng memory-consistency protocol.

**Câu 9.** Muốn chia một chủ sở hữu thành bốn để tăng hiệu năng, điều kiện quan trọng nhất là gì?  
**Đáp án:** Partition state theo invariant độc lập và bảo đảm key/message luôn đến đúng owner; các giao dịch xuyên partition cần giao thức riêng.

**Câu 10.** `volatile Snapshot current` trỏ tới snapshot thực sự immutable, owner xây snapshot mới rồi gán `current = snapshot`. Reader chỉ đọc snapshot hiện tại. Có thể tránh lock ở reader không?  
**Đáp án:** Có, nếu snapshot được khởi tạo/công bố đúng, toàn bộ object graph được đọc là immutable, và reader lấy reference `volatile` một lần cho lần xử lý. Gán volatile tạo publication/visibility; không nên để owner mutate snapshot cũ đang được reader sử dụng.

## Tài liệu tham khảo chính thức

- [Java SE 17 — java.util.concurrent package](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html): bộ primitive concurrency và memory-consistency properties.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): task submission và lifecycle.
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): completion và kết quả từ task.
- [Java SE 17 — BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): mô hình producer-consumer, blocking và memory consistency.
- [Java SE 17 — ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): per-thread values, `withInitial`, `get` và `remove`.
- [Java SE 17 — List.copyOf](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection)): danh sách không hỗ trợ mutate cấu trúc, lưu ý phần tử mutable.
- [JLS `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Java Memory Model, data race, happens-before và monitor.
- [Java SE 17 — Thread](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): `start`, `join` và thread lifecycle.

**Điều cần nhớ:** muốn dựa vào thread confinement để có thread safety, hãy kiểm soát **mọi alias đến mutable object**, quy định **ai được sửa khi nào**, và tạo **ranh giới publication/handoff có happens-before**. Khi ownership rõ ràng, nhiều cập nhật local không cần khóa; sự phối hợp giữa các owner vẫn cần cơ chế tin cậy và xác minh invariants.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-005">Encapsulation của trạng thái cần đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Encapsulation của trạng thái cần đồng bộ trong Java

## 1. Tại sao encapsulation là nền tảng của thread safety?

**Encapsulation** (đóng gói) là việc một đối tượng giữ quyền kiểm soát trạng thái của mình và chỉ cho phép bên ngoài tác động thông qua các thao tác có hợp đồng rõ ràng. Trong lập trình đồng thời Java, đóng gói còn là cách bảo đảm **mọi truy cập có khả năng xung đột đều tuân theo cùng một quy tắc đồng bộ**.

Một class có thể đặt trường ở mức `private` nhưng **chưa chắc thread-safe**. Nếu getter trả về chính đối tượng mutable bên trong, caller có thể sửa nó mà không đi qua lock. Nếu có phương thức khóa khi ghi nhưng đọc dữ liệu chung không qua cơ chế đồng bộ phù hợp, class vẫn có thể có data race hoặc đọc trạng thái thiếu nhất quán.

Mục tiêu không đơn thuần là “bọc `synchronized` vào mọi phương thức”. Mục tiêu là:

1. Xác định **bất biến (invariant)** của trạng thái: ví dụ tồn kho không âm; tổng tiền trong hai ví vẫn bảo toàn; danh sách chỉ được sửa qua một API.
2. Xác định **ai sở hữu trạng thái mutable** và ai có thể đọc/ghi nó.
3. Đặt **toàn bộ thao tác liên quan đến cùng bất biến** dưới một giao thức bảo vệ phù hợp: khóa chung, primitive atomic, immutable state thay nguyên tử hoặc thread confinement.
4. Không để tham chiếu mutable hoặc quyền thao tác lên lock thoát ra ngoài theo cách phá vỡ giao thức đó.
5. Công bố kết quả, snapshot hoặc dữ liệu trao đổi với thread khác bằng quan hệ happens-before phù hợp.

Đây là chủ đề Java concurrency nói chung. Các kỹ thuật trong bài dùng Java 17+ và API JDK tiêu chuẩn.

## 2. State, invariant và synchronization policy

Một **state** là tập dữ liệu mà hành vi của đối tượng phụ thuộc vào. State có thể nằm trong các trường primitive, object tham chiếu, collection hoặc các object lồng nhau.

**Invariant** là điều kiện phải luôn đúng ở các điểm mà trạng thái được xem là hợp lệ. Ví dụ:

- `remaining >= 0` đối với kho không cho bán quá số lượng.
- `fromBalance + toBalance == total` trong một lần chuyển tiền nội bộ.
- Một mã định danh chỉ có một bản ghi đang hoạt động.
- Các trường `name` và `version` của một snapshot phải thuộc cùng lần cập nhật.

**Synchronization policy** là quy ước để mọi truy cập liên quan tuân thủ, chẳng hạn “mọi đọc/ghi hai trường tiền phải giữ `lock` của đối tượng”, hoặc “trạng thái được công bố dưới dạng immutable snapshot qua `AtomicReference`”.

Một class muốn thực sự đóng gói tính thread-safe phải kiểm soát được **đường vào trạng thái**. Chỉ một phương thức bỏ qua policy là có thể làm mất bảo đảm của toàn bộ đối tượng.

### Phạm vi bất biến quyết định phạm vi khóa

Nếu `quantity` và `reserved` phải thỏa `quantity >= reserved`, hai phép cập nhật trên hai `AtomicInteger` riêng **chưa** bảo đảm bất biến liên trường ở mọi thời điểm đọc. Nếu cần thay hai trường như một đơn vị logic, có thể:

- Bảo vệ cả hai dưới **cùng một lock** và cung cấp phương thức cập nhật/snapshot đồng bộ.
- Lưu toàn bộ state trong một immutable object rồi thay tham chiếu bằng `AtomicReference.compareAndSet(...)`, tính toán lại khi CAS thất bại.
- Dùng một chủ sở hữu thread duy nhất và gửi thông điệp đến chủ sở hữu, nếu mô hình ứng dụng phù hợp.

Không nên chọn số lượng khóa hoặc số lượng trường atomic chỉ theo số trường dữ liệu. Hãy bắt đầu từ bất biến cần được bảo vệ.

## 3. Những dạng “rò rỉ trạng thái” thường gặp

### 3.1. Getter trả về tham chiếu mutable nội bộ

Tạo tệp `EscapedList.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class EscapedList {
    private final List<String> items = new ArrayList<>();

    public synchronized void add(String item) {
        items.add(item);
    }

    public synchronized int size() {
        return items.size();
    }

    // Lỗi thiết kế: trả ra chính list mutable đang được bảo vệ.
    public synchronized List<String> items() {
        return items;
    }

    public static void main(String[] args) {
        EscapedList store = new EscapedList();
        store.add("A");

        List<String> leaked = store.items();
        leaked.clear(); // Bỏ qua mọi phương thức đồng bộ của store.

        System.out.println("Store size: " + store.size());
    }
}
```

Chạy trên Java 17+:

```bash
javac EscapedList.java
java EscapedList
```

Đầu ra:

```text
Store size: 0
```

Đoạn mã chạy tuần tự đã đủ chứng minh lỗi **mất quyền kiểm soát state**: caller xóa dữ liệu mà không qua API `add` hoặc một chính sách chỉnh sửa nào của store. Khi có nhiều thread, hành động `leaked.clear()` có thể xảy ra song song với `add()`/`size()`. Việc `items()` có khóa chỉ bảo vệ **thời điểm trả tham chiếu**; sau khi phương thức kết thúc, tham chiếu vẫn trỏ tới chính list nội bộ.

**Sửa:** giữ list private và chỉ trả về một bản sao phù hợp; cung cấp các thao tác đọc/ghi có đồng bộ trên đối tượng sở hữu state.

### 3.2. Getter trả về mảng, map hoặc object mutable

Các mẫu nguy hiểm về mặt thiết kế:

```java
// Ví dụ minh họa, các trường được giả sử đã khai báo.
public byte[] buffer() { return internalBuffer; }
public Map<String, String> entries() { return mutableMap; }
public MutableConfig config() { return internalConfig; }
```

Trả về tham chiếu cho phép caller viết vào object nội bộ mà class không quan sát hay đồng bộ đúng. `private final byte[] internalBuffer` cũng **không bảo vệ nội dung mảng**: `final` chỉ ngăn gán lại tham chiếu trường, không làm mảng bất biến.

Nếu cần trao đổi dữ liệu mutable, cân nhắc sao chép khi **nhận vào** và **trả ra**, sử dụng value object bất biến, hoặc chuyển quyền sở hữu một cách rõ ràng. Kể cả constructor nhận `List` từ caller, lưu thẳng tham chiếu đó có thể khiến caller vẫn có quyền sửa state về sau.

### 3.3. Unmodifiable view không phải immutable snapshot

`Collections.unmodifiableList(backingList)` tạo một view không cho sửa qua view, nhưng **backing list vẫn thay đổi được** nếu chủ sở hữu hoặc tham chiếu khác sửa nó. Vì vậy, view này có thể phản ánh các thay đổi tương lai và không thay thế việc chụp snapshot trong một lock.

`List.copyOf(source)` tạo danh sách không chỉnh sửa được và không phản ánh thêm/xóa phần tử sau đó từ source. Nhưng nó là **shallow copy**: nếu phần tử là các object mutable, caller vẫn có thể sửa nội dung object đó. Ngoài ra, `List.copyOf` không chấp nhận phần tử null.

Đối với `List<String>`, chuỗi bất biến giúp ví dụ snapshot trở nên đơn giản. Với object mutable phức tạp, hãy chuyển sang DTO/value object bất biến hoặc sao chép sâu theo ngữ nghĩa miền dữ liệu khi cần.

## 4. Java 17+: encapsulation bằng API giữ nguyên invariant

Tạo tệp `SafeInventory.java`:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class SafeInventory {
    private int remaining = 5;

    // Toàn bộ check và decrement nằm trong cùng critical section.
    public synchronized boolean reserveOne() {
        if (remaining <= 0) {
            return false;
        }
        remaining--;
        return true;
    }

    public synchronized int available() {
        return remaining;
    }

    public static void main(String[] args) throws InterruptedException {
        SafeInventory inventory = new SafeInventory();
        AtomicInteger accepted = new AtomicInteger();
        Thread[] workers = new Thread[12];

        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                if (inventory.reserveOne()) {
                    accepted.incrementAndGet();
                }
            }, "buyer-" + i);
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("Accepted: " + accepted.get());
        System.out.println("Remaining: " + inventory.available());
    }
}
```

Biên dịch và chạy:

```bash
javac SafeInventory.java
java SafeInventory
```

Đầu ra khi các worker hoàn tất bình thường:

```text
Accepted: 5
Remaining: 0
```

**Vì sao kết quả luôn đúng?**

- `remaining` là `private` và không có setter tự do; code bên ngoài chỉ có thể yêu cầu `reserveOne()` hoặc đọc `available()`.
- `reserveOne()` giữ monitor của chính đối tượng `inventory` trong suốt đoạn **kiểm tra còn hàng → trừ hàng**. Không thread nào khác có thể chen vào giữa hai bước này nếu cũng đi qua API của đối tượng.
- `available()` dùng **cùng monitor** để đọc giá trị, vì thế có quan hệ đồng bộ phù hợp với những lần cập nhật.
- Mười hai worker có thể được scheduling theo mọi thứ tự; chỉ năm lần reserve thành công. `AtomicInteger accepted` cho phép ghi nhận số lần chấp nhận từ nhiều thread mà không làm mất lượt.
- `join()` giúp `main` đợi tất cả worker trước khi in kết quả.

Trong class này, bất biến `remaining >= 0` được giữ đúng qua mỗi lần gọi API. **Điều kiện thiết kế** là không có đường khác chỉnh sửa `remaining` không tuân theo policy; một setter riêng lẻ không kiểm tra hoặc việc lộ tham chiếu tới state mutable sẽ phá vỡ bảo đảm.

### Check–then–act phải nằm trong cùng thao tác

Một API kiểu `available()` rồi `reserveOne()` là an toàn nếu caller **không giả định** kết quả `available()` trước đó sẽ vẫn đúng. Chỉ `reserveOne()` mới là điểm quyết định chấp nhận/từ chối. Nếu thay bằng hai phương thức `hasStock()` và `decrement()` không ràng buộc, hai caller có thể đều qua check rồi cùng giảm số lượng. Một khóa riêng cho từng phương thức **không đủ** để biến tổ hợp do caller thực hiện thành nguyên tử.

Cách thiết kế tốt là **đưa thao tác nghiệp vụ cần atomic vào cùng phương thức của đối tượng sở hữu state**.

## 5. Lock nội bộ, policy nhất quán và khả năng giữ kiểm soát

`synchronized` instance method khóa monitor của `this`. Điều này đúng với ví dụ `SafeInventory` nhưng bất kỳ code nào có tham chiếu đến đối tượng cũng có thể viết `synchronized (inventory)`, gây tranh chấp hoặc giữ lock ngoài dự kiến. Khi muốn kiểm soát lock rõ hơn, dùng **private lock object**:

```java
final class Counter {
    private final Object lock = new Object();
    private int value;

    public void increment() {
        synchronized (lock) {
            value++;
        }
    }

    public int get() {
        synchronized (lock) {
            return value;
        }
    }
}
```

Đây là class phụ trợ có thể biên dịch trong một file chứa class không public, không có `main`. `lock` không được trả ra ngoài, không gán lại và không dùng để đồng bộ cho những đối tượng không liên quan. Với cùng lock, mọi lần cập nhật và đọc `value` được bảo vệ, và monitor unlock/lock tạo quan hệ happens-before thích hợp.

Các quy tắc quan trọng:

1. **Ghi rõ state nào lock nào bảo vệ.** Nếu có nhiều lock, phải rõ mỗi invariant phụ thuộc những trường nào; đừng khóa riêng từng trường rồi tưởng có snapshot nhất quán liên trường.
2. **Bảo vệ cả đường đọc và đường ghi.** Đọc không khóa một biến đang bị thread khác ghi có thể tạo data race; getter tuy đơn giản vẫn phải theo policy hoặc dùng dữ liệu đã publish an toàn.
3. **Giữ lock đủ lâu để bảo vệ invariant**, rồi thả càng sớm càng tốt. Không cố tình mở khóa giữa check và update.
4. **Tránh callback không kiểm soát trong critical section.** Callback có thể gọi lại API, chờ I/O, giữ lock khác hoặc tạo chu trình deadlock.
5. **Không xuất bản lock nội bộ.** Trả `lock`, lấy một đối tượng công khai làm monitor hoặc dùng chuỗi literal làm lock làm giảm khả năng kiểm soát tranh chấp.
6. **Tài nguyên bên ngoài cần giao thức riêng.** Monitor trong một JVM không bảo vệ một database, file hay service có thể bị tiến trình/JVM khác thay đổi.

### `volatile` và `AtomicInteger` không thay thế mọi lock

`volatile` tạo một số bảo đảm visibility/ordering, thích hợp với cờ đơn giản theo giao thức rõ ràng, nhưng `count++` trên `volatile int` vẫn không atomic. `AtomicInteger.incrementAndGet()` phù hợp với một bộ đếm độc lập; nhiều biến atomic riêng không tạo một transaction bao phủ invariant giữa chúng.

Ngược lại, việc dùng lock cho mọi trường đơn lẻ cũng chưa chắc cần thiết. Trạng thái bất biến hoặc confined trong một thread không đòi lock ở mọi phép đọc. Chọn cơ chế theo điều kiện chia sẻ và invariant thực sự.

## 6. Encapsulation khi trả snapshot cho caller

Nếu caller muốn xem nội dung collection, đừng trả thẳng mutable backing collection. Có thể tạo snapshot **trong vùng đã khóa** rồi công bố snapshot bất biến ra ngoài.

Tạo tệp `SnapshotStore.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class SnapshotStore {
    private final Object lock = new Object();
    private final List<String> items = new ArrayList<>();

    public void add(String value) {
        synchronized (lock) {
            items.add(value);
        }
    }

    public List<String> snapshot() {
        synchronized (lock) {
            return List.copyOf(items);
        }
    }

    public static void main(String[] args) {
        SnapshotStore store = new SnapshotStore();
        store.add("A");

        List<String> oldSnapshot = store.snapshot();
        store.add("B");

        System.out.println("Old: " + oldSnapshot);
        System.out.println("Current: " + store.snapshot());

        try {
            oldSnapshot.add("C");
        } catch (UnsupportedOperationException expected) {
            System.out.println("Snapshot is read-only");
        }
    }
}
```

Chạy:

```bash
javac SnapshotStore.java
java SnapshotStore
```

Đầu ra:

```text
Old: [A]
Current: [A, B]
Snapshot is read-only
```

**Phân tích:**

- `snapshot()` giữ cùng `lock` mà `add()` dùng, nên bản sao được tạo trong một thời điểm logic nhất quán với các thao tác sửa của store.
- `List.copyOf(items)` trả danh sách không chỉnh sửa được, tách riêng cấu trúc phần tử khỏi những lần thêm/xóa về sau của `items`.
- `oldSnapshot` không tự biến thành `[A, B]` sau khi store thay đổi; caller không thể thực hiện `add()` trên snapshot.
- Phần tử ở đây là `String` bất biến. Nếu mỗi phần tử là `MutableOrder`, `List.copyOf` **không sao chép sâu** các order; vẫn phải thiết kế riêng việc chụp bất biến của từng phần tử.

**Chi phí:** snapshot tốn thời gian và bộ nhớ theo số phần tử; nếu dữ liệu lớn, tần suất đọc cao hoặc cần stream nhiều bản ghi, cần cân nhắc pagination, read-only interface có hợp đồng phù hợp, copy-on-write hoặc cách truyền dữ liệu khác. Một snapshot nhất quán có thể đáng giá hơn chi phí sao chép khi nó ngăn việc lộ state.

### Defensive copy cả lúc nhận dữ liệu vào

Một constructor chỉ viết `this.items = input` có thể giữ tham chiếu tới collection mà caller vẫn quản lý. Nếu mục tiêu là sở hữu dữ liệu, có thể tạo `new ArrayList<>(input)` hoặc `List.copyOf(input)` theo nhu cầu mutable/immutable. **Nhớ copy lúc vào và lúc ra** nếu cả hai hướng đều có thể làm state bị sửa ngoài giao thức.

## 7. Invariant gồm nhiều trường: một snapshot phải đồng bộ với cập nhật

Tạo tệp `AccountBookDemo.java`:

```java
public class AccountBookDemo {
    record Balances(int from, int to) {
        int total() {
            return from + to;
        }
    }

    private static final class AccountBook {
        private int from = 100;
        private int to = 0;

        public synchronized boolean transfer(int amount) {
            if (amount <= 0 || amount > from) {
                return false;
            }
            from -= amount;
            to += amount;
            return true;
        }

        public synchronized Balances snapshot() {
            return new Balances(from, to);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AccountBook book = new AccountBook();
        Thread first = new Thread(() -> book.transfer(30));
        Thread second = new Thread(() -> book.transfer(20));

        first.start();
        second.start();
        first.join();
        second.join();

        Balances result = book.snapshot();
        System.out.println(result);
        System.out.println("Total: " + result.total());
    }
}
```

Chạy với Java 17+ (có hỗ trợ `record`):

```bash
javac AccountBookDemo.java
java AccountBookDemo
```

Đầu ra:

```text
Balances[from=50, to=50]
Total: 100
```

Hai thread có thể gọi `transfer` theo thứ tự bất kỳ nhưng **mỗi lần chuyển được thực hiện trong cùng monitor**. Một lần chuyển không để lộ trạng thái trung gian từ khi trừ `from` đến khi cộng `to` cho caller dùng API đồng bộ. Phương thức `snapshot()` giữ monitor đó để sao chép **cùng lúc** hai giá trị vào immutable `record Balances`. Caller không thể sửa trực tiếp `from` và `to`.

Nếu viết hai getter `getFrom()` và `getTo()` đều synchronized nhưng caller gọi tách rời, một transfer có thể diễn ra **giữa hai lời gọi**. Hai kết quả mỗi cái đúng tại thời điểm đọc vẫn có thể không tạo cùng một snapshot. Khi invariant đi qua nhiều trường, nên trả về một snapshot được chụp trong **một** critical section.

Ví dụ sử dụng số nguyên nhỏ để tập trung vào concurrency; khi xử lý tiền thật còn cần kiểu tiền và quy tắc nghiệp vụ phù hợp. Lock trong ví dụ chỉ bảo vệ trạng thái của một đối tượng `AccountBook` trong JVM, không là giao dịch liên hệ thống.

## 8. Safe publication và ownership của object

Ngay cả một class có tất cả phương thức đồng bộ, quá trình **tạo và công bố tham chiếu** cũng cần được xét. Đừng cho `this` “thoát ra” trước khi constructor hoàn tất, ví dụ khởi động thread giữ tham chiếu `this` từ constructor hoặc đăng ký callback vào một đối tượng bên ngoài khi state chưa sẵn sàng.

`final` có quy tắc JMM đặc biệt trong quá trình khởi tạo đúng, nhưng `final List<?>` chỉ giữ cố định **tham chiếu** và không làm các phần tử hoặc nội dung list trở thành bất biến. Một object đã tạo xong có thể được trao qua `Thread.start()`, `BlockingQueue`, `volatile`/`AtomicReference`, `synchronized` hoặc các cơ chế khác có memory-consistency guarantees phù hợp.

**Thread confinement:** nếu toàn bộ state mutable chỉ được truy cập bởi một thread sở hữu, có thể không cần lock cho state đó. Các thread khác gửi thông điệp/tác vụ đến owner thay vì trực tiếp viết state. Điều kiện là không có tham chiếu mutable bị chia sẻ ngoài dự định.

**Immutable state:** nếu object không có mutable state sau khi tạo đúng cách và tham chiếu được công bố an toàn, nhiều reader có thể dùng nó mà không phải khóa cho từng lần đọc. Điều này thường giúp code dễ suy luận hơn.

**Shared mutable state:** nếu nhiều thread được phép thay đổi object, policy đồng bộ phải được chỉ định theo object sở hữu state và thực thi ở mọi API liên quan.

## 9. API “thread-safe” không có nghĩa mọi tổ hợp API đều atomic

Một collection concurrent thường chỉ bảo đảm những thao tác được mô tả trong hợp đồng của nó. Ví dụ dùng `ConcurrentHashMap`:

```java
// Minh họa: map đã là ConcurrentHashMap<String, String>.
if (!map.containsKey("job-1")) {
    map.put("job-1", "running");
}
```

Đây là **snippet không độc lập**. Dù `containsKey` và `put` thread-safe khi gọi riêng, hai thread có thể cùng thấy key chưa tồn tại và cùng quyết định đăng ký. Nếu invariant là “chỉ một người đăng ký”, phải dùng `putIfAbsent` hoặc một thao tác atomic theo key phù hợp.

Tương tự, các phương thức riêng lẻ `size()` và `remove()` của một wrapper synchronized không tự biến chuỗi `if (size() > 0) remove()` thành atomic đối với các thread khác. Nếu cần hành động có điều kiện, hãy **đóng gói cả check và act** vào một API của chủ sở hữu state và bảo vệ trọn đoạn đó.

### Các callback và hàm do caller cung cấp

Một API nhận `Consumer`, `Function` hoặc comparator từ caller có thể gián tiếp chạy mã tùy ý khi đang giữ lock. Callback có thể chặn I/O, gọi lại chính đối tượng, cố lấy lock khác hoặc gây deadlock. Hãy xác định rõ callback được chạy trong hay ngoài critical section và đánh giá tính đúng đắn nếu chạy trên một snapshot ngoài lock.

Ví dụ, “copy snapshot dưới lock rồi áp dụng callback ngoài lock” thường tránh chạy code lạ khi giữ lock; nhưng chỉ đúng khi thao tác thực sự cho phép làm việc trên snapshot cũ. Nếu callback phải cập nhật chính state dựa trên điều kiện vừa đọc, việc thả lock trước khi ghi có thể lại tạo TOCTOU. Không có một lựa chọn mặc định phù hợp mọi invariant.

## 10. Encapsulation giúp bảo đảm đồng bộ ở ranh giới nào?

| Phạm vi | Encapsulation/lock Java có thể bảo vệ? | Ghi chú |
| --- | --- | --- |
| Nhiều thread cùng dùng một instance trong một JVM | Có, khi mọi đường truy cập tuân theo policy | Cùng lock hoặc primitive atomic phù hợp |
| Nhiều instance, mỗi instance có lock riêng | Chỉ state của từng instance | Không bảo vệ shared static state nếu policy khác |
| Nhiều JVM/process truy cập cùng file | Không tự động | Cần cơ chế nguyên tử/lock của hệ thống tệp phù hợp |
| Nhiều ứng dụng cập nhật cùng nơi lưu trữ | Không tự động | Cần transaction/conditional update/unique constraint tại nguồn chân lý |
| Snapshot immutable đã công bố an toàn | Thường không cần khóa khi chỉ đọc | Không được chứa hoặc để lộ mutable element thiếu bảo vệ |

Một biến `static` mutable có thể được tất cả instance cùng chia sẻ. Nếu mỗi instance dùng `synchronized` trên `this` để bảo vệ biến static, các instance có **monitor khác nhau** và không loại trừ lẫn nhau. Khi cần bảo vệ state static chia sẻ, phải chọn một policy chung tương ứng với phạm vi state đó.

## 11. Các sai lầm thường gặp

- **“Trường private đồng nghĩa thread-safe.”** Sai khi API vẫn trả tham chiếu mutable, callback lộ state hoặc truy cập khác bỏ qua policy.
- **“Thêm synchronized vào getter list là đủ.”** Sai nếu getter trả backing list; caller tiếp tục sửa ngoài lock.
- **“final làm list hoặc mảng bất biến.”** Sai: final cố định tham chiếu trường; nội dung object vẫn có thể mutable.
- **“unmodifiableList là bản sao không đổi.”** Sai: view không cho sửa qua nó nhưng backing collection có thể thay đổi.
- **“List.copyOf sao chép sâu mọi phần tử.”** Sai: nó sao chép cấu trúc list, không tự sao chép object mutable ở từng phần tử.
- **“Đã synchronized từng getter nên snapshot nhiều trường nhất quán.”** Sai nếu các getter chạy trong hai critical section riêng và có update ở giữa.
- **“Đã dùng volatile/atomic cho từng field nên invariant nhiều field luôn đúng.”** Sai nếu cần nguyên tử tính giữa các trường.
- **“Chỉ khóa writer là đủ.”** Sai nếu reader đọc cùng state không có giao thức visibility/ordering phù hợp.
- **“Một lock private giải quyết được sửa đổi từ service khác.”** Sai: monitor chỉ phối hợp những thread dùng monitor đó trong JVM.
- **“Gọi callback trong synchronized luôn an toàn.”** Không hẳn: callback không kiểm soát có thể kéo dài thời gian giữ lock hoặc tạo vòng chờ.
- **“Đặt Thread.sleep trong critical section giúp thread khác kịp cập nhật.”** Sai: sleep không nhả monitor và không làm thuật toán tự an toàn.

## 12. Checklist thiết kế class thread-safe

1. **Liệt kê state và invariant:** những trường nào liên quan nhau, giá trị hợp lệ là gì, ai được quyền sửa?
2. **Chỉ định chủ sở hữu:** có một đối tượng kiểm soát cập nhật hay state bị chia sẻ ngoài ý muốn?
3. **Định nghĩa synchronization policy:** cùng lock cho mọi truy cập có liên quan, atomic cho phép cập nhật đơn, hoặc immutable/thread-confined state?
4. **Kiểm tra API vào/ra:** constructor có giữ tham chiếu mutable của caller không? Getter có trả object/mảng/map/list nội bộ không?
5. **Đóng gói thao tác nghiệp vụ:** check–then–act hoặc read–modify–write cần atomic nên nằm trong một phương thức được bảo vệ.
6. **Đảm bảo snapshot nhất quán:** khi nhiều trường tạo thành một invariant, sao chép chúng trong cùng critical section thành immutable result.
7. **Xem xét callback và vòng đời:** không để `this` escape khi chưa khởi tạo xong, hạn chế chạy code lạ và chờ I/O trong khi giữ lock.
8. **Kiểm tra khả năng mở rộng:** đo contention, chi phí copy snapshot, độ dài critical section; không đổi đúng đắn lấy hiệu năng dự đoán.
9. **Kiểm thử các lịch xen kẽ:** dùng nhiều worker, `CountDownLatch`, invariant assertions và stress test; việc chạy qua vài lần không chứng minh không có race.
10. **Giữ đúng phạm vi bảo đảm:** lock cục bộ không tự bảo vệ process khác hoặc nguồn dữ liệu bên ngoài.

**Ghi nhớ:** thread safety là thuộc tính của **toàn bộ giao thức truy cập trạng thái**, không phải của từ khóa `private` hay một vài phương thức `synchronized` đứng riêng lẻ. Encapsulation hiệu quả giữ quyền cập nhật bên trong đối tượng, bảo vệ cùng một invariant bằng chính sách đồng bộ nhất quán và chỉ công bố dữ liệu cho bên ngoài qua những API không làm lộ mutable state.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-006">Safe publication qua final, volatile và khóa</a>

<details>
<summary>Xem chi tiết</summary>

# Safe publication qua final, volatile và khóa trong Java

> **Mục tiêu:** Biết khi nào một đối tượng đã khởi tạo được **công bố an toàn** để thread khác đọc được trạng thái của nó; phân biệt bảo đảm đặc biệt của `final` với bảo đảm *happens-before* của `volatile` và khóa; chọn đúng cách chia sẻ **immutable snapshot** hoặc bảo vệ **mutable state** trong Java.

Bài thuộc nhóm **Thread Safety**, sử dụng Java SE và Java Memory Model (JMM). Các ví dụ chạy với Java 17+, không phụ thuộc vào Spring hoặc cơ chế lifecycle/DI của framework.

## 1. Safe publication là một yêu cầu của thread safety

Giả sử thread A dựng một object rồi thread B bắt đầu sử dụng. Trong A, constructor ghi các field theo thứ tự nguồn; nhưng **thứ tự chạy theo đồng hồ không tự tạo quan hệ quan sát bộ nhớ** giữa hai thread nếu mã thiếu đồng bộ.

Một đối tượng được **safely published** khi đường truyền tham chiếu từ thread tạo sang thread sử dụng có cơ chế bảo đảm những hành động khởi tạo cần thiết được quan sát theo quan hệ *happens-before* phù hợp. Safe publication giúp tránh tình trạng consumer thấy một tham chiếu nhưng không có bảo đảm nhìn thấy những giá trị mà producer đã ghi trong construction.

~~~text
Thread A                                   Thread B
--------                                   --------
new Config(...)       (1) construction
... writes fields
publish reference     (2) publication  ---> receive reference
                                           read initialized fields
                                           (3) subsequent use
~~~

Cần tách ba giai đoạn:

1. **Construction:** thiết lập invariant bên trong object, ví dụ `host != null` và `timeout > 0`.
2. **Publication:** truyền tham chiếu qua một giao thức có bảo đảm Java Memory Model.
3. **Subsequent mutation:** nếu object còn thay đổi sau khi đã được công bố, cần cơ chế riêng để tránh data race, lost update hoặc snapshot không nhất quán.

**Safe publication không đồng nghĩa object bất biến, và cũng không tự biến mọi thao tác tương lai trên object thành thread-safe.** Nó chủ yếu nói về việc consumer có thể tin vào các ghi đã thực hiện **trước thời điểm publication** được đồng bộ.

### Vì sao `ref != null` chưa phải bằng chứng?

Hãy xét đoạn cố ý thiếu đồng bộ (mã minh họa, không phải chương trình độc lập):

~~~java
class Holder {
    static Settings shared; // Field thường; không volatile, không khóa.

    static void create() {
        shared = new Settings("service-a", 30);
    }

    static void consume() {
        Settings local = shared;
        if (local != null) {
            use(local.host, local.timeoutSeconds);
        }
    }
}
~~~

Nếu hai phương thức ở hai thread khác nhau và không có cạnh đồng bộ nào khác, việc đọc `shared` là một truy cập racy với ghi `shared`. Thấy reference khác null không làm cho các ghi field thông thường trong constructor tự động *happens-before* consumer. Producer hoàn thành constructor trước theo thời gian thực **không đủ** để chứng minh consumer luôn thấy đầy đủ state.

Đừng yêu cầu đoạn racy trên phải in một kết quả “sai” cụ thể: máy/JVM thường có thể tình cờ quan sát đúng rất nhiều lần. Phép thử thành công không thay thế quy tắc JMM.

## 2. Bảng so sánh ba cơ chế

| Cơ chế | Điều nó thực sự bảo đảm | Phạm vi phù hợp | Điều **không** được suy ra |
| --- | --- | --- | --- |
| `final` field | Bảo đảm **final-field semantics** sau khi object được dựng đúng cách; các final field có giá trị đã gán trong constructor dù có một số kiểu publication không đồng bộ | Value object và immutable snapshot | Không tạo khóa, không bảo vệ mutation của object bên trong field final, không sửa `this` escape |
| `volatile` reference | Ghi volatile và lần đọc volatile tương ứng tạo ordering/visibility, nhờ đó publish các ghi trước lần ghi volatile | Công bố/thay thế snapshot bất biến, cờ trạng thái đơn giản | Không khiến các field thường trong object luôn thread-safe; không atomic cho chuỗi read-modify-write |
| `synchronized` / `Lock` | Critical section loại trừ lẫn nhau và cạnh *happens-before* giữa release/acquire cùng cơ chế khóa | Dữ liệu mutable, nhiều field phải cập nhật/đọc đồng bộ; chuyển giao reference qua khóa | Không bảo vệ reader/writer nào bỏ qua khóa hoặc khóa một object khác |

Trong thực tế, các cơ chế thường **phối hợp**: object có final fields để giữ immutable state, được công bố qua volatile reference; hoặc object mutable được tạo và đọc/sửa dưới cùng một lock.

## 3. Nền tảng: happens-before quan trọng hơn “chạy trước”

JMM mô tả các quan hệ *synchronizes-with* và *happens-before* (HB). Khi **một ghi cần thiết HB một lần đọc** ở thread khác, mô hình có căn cứ về visibility/order của ghi đó. HB có tính bắc cầu: nếu A HB B và B HB C, thì A HB C.

Các cạnh thường dùng:

- **Program order:** thao tác trước trong một thread HB thao tác sau trong cùng thread.
- **Monitor:** unlock của `synchronized(lock)` HB lần lock tiếp theo trên **cùng monitor**.
- **Volatile:** ghi volatile HB các lần đọc volatile tiếp theo của cùng biến theo synchronization order.
- **`Thread.start()`:** hành động trước `start()` HB hành động của thread mới.
- **`Thread.join()`:** hành động trong thread đã kết thúc HB hành động sau `join()` hoàn thành thành công của thread đợi.
- **Concurrent utilities:** các API như `BlockingQueue`, `ExecutorService.submit`/`Future.get` có quy tắc memory-consistency riêng.
- **Class initialization:** JVM bảo đảm khởi tạo lớp được điều phối để thread sử dụng class đã khởi tạo theo đúng đặc tả.

**Không có cạnh HB chỉ vì có `sleep()`, log “thread A trước thread B”, đọc đồng hồ, đặt priority cao, hoặc gọi `System.out.println()` rồi suy luận dữ liệu ngoài luồng.** Một thư viện I/O có thể dùng khóa nội bộ để in, nhưng đó không phải hợp đồng đồng bộ cho state nghiệp vụ của ta.

Sơ đồ publication bằng volatile:

~~~text
A: tạo Config -> ghi các field -> volatile WRITE ref
                                         |
                                synchronizes-with
                                         v
B:                               volatile READ ref -> đọc field
~~~

Nếu consumer dùng tham chiếu nhận qua cạnh đồng bộ ấy, các ghi tạo state trong A được công bố đến B. Nếu consumer lấy cùng object qua một **đường rò rỉ khác**, phải xét đường đó riêng.

## 4. `final`: giá trị ổn định và quy tắc *freeze*

Một field `final` chỉ được gán theo những quy tắc khởi tạo của Java. JMM có **final-field semantics**: đối với một object được **constructed correctly**, khi constructor kết thúc có hành động *freeze* liên quan đến final fields. Các thread khác được bảo đảm quan sát giá trị mà constructor đã gán cho các final fields theo quy định ở JLS `17.5, ngay cả trong một số tình huống reference được chia sẻ qua data race.

~~~java
// Một class minh họa cho immutable value object.
final class Endpoint {
    final String host;
    final int port;

    Endpoint(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port");
        }
        this.host = host;
        this.port = port;
    }
}
~~~

Ở đây các thành phần `String` và `int` không mang mutable state nội bộ do caller sửa; class không thể subclass và reference không được công bố trong constructor. Một instance đã dựng xong là value object có trạng thái ổn định.

### “Constructed correctly” gồm những gì?

**Không cho `this` thoát khỏi constructor trước khi construction hoàn tất.** Các hành vi đáng ngờ:

- Đưa `this` vào static field/registry mà thread khác có thể đọc.
- Truyền `this` tới listener/callback có thể chạy ngay.
- Gửi lambda capture `this` đến executor ngay trong constructor.
- `new Thread(() -> use(this)).start()` trước khi các field khác được gán.
- Gọi phương thức overridable trong constructor; subclass có thể được gọi khi field của subclass chưa dựng xong.

Nếu một thread đọc object **trước constructor hoàn thành**, bảo đảm final-field sau freeze không thể sửa những lần đọc đã xảy ra trước đó. Ngay cả khi field là final, đừng cho consumer chạm vào object khi constructor đang chạy.

### `final` không có nghĩa bất biến sâu (*deeply immutable*)

~~~java
final class Names {
    final java.util.List<String> values;

    Names(java.util.List<String> values) {
        this.values = values; // Vẫn giữ list của caller!
    }
}
~~~

Field `values` không được gán lại, nhưng một thread khác có thể sửa `ArrayList` được tham chiếu, làm rối cấu trúc hoặc gây data race. An toàn hơn khi cần snapshot chỉ đọc:

~~~java
final class Names {
    final java.util.List<String> values;

    Names(java.util.List<String> source) {
        this.values = java.util.List.copyOf(source);
    }
}
~~~

`List.copyOf` tạo list không cho mutate qua API thường, đồng thời tách cấu trúc list khỏi mutable list nguồn. Tuy nhiên, nếu phần tử là các object mutable, `List.copyOf` **không tự copy sâu từng phần tử**. Cần cân nhắc copy/thiết kế immutable cả cây dữ liệu hoặc áp dụng synchronization phù hợp.

### `final` giúp visibility; không thay thế cả một giao thức chia sẻ

Bảo đảm đặc biệt cho final field **không đồng nghĩa** mọi ordinary field khác của object được publish an toàn. Nó cũng không tạo cơ chế chờ một tham chiếu xuất hiện, không cung cấp ordering cho các mutation sau constructor và không làm chuỗi thao tác trên nhiều object trở thành atomic.

**Thói quen tốt:** dùng final fields để biểu đạt state không đổi, và vẫn chọn một **publication path rõ ràng** như volatile, khóa, class initialization hoặc concurrent queue.

## 5. Ví dụ Java 17: value object có final field được công bố trước `Thread.start()`

Tạo tệp `FinalConfigDemo.java`:

~~~java
public class FinalConfigDemo {
    private static final class Config {
        final String host;
        final int timeoutSeconds;

        Config(String host, int timeoutSeconds) {
            this.host = host;
            this.timeoutSeconds = timeoutSeconds;
        }

        String describe() {
            return host + ":" + timeoutSeconds;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Config config = new Config("api.internal", 15);

        Thread reader = new Thread(
                () -> System.out.println("config = " + config.describe()));

        reader.start();
        reader.join();
    }
}
~~~

Chạy:

~~~bash
javac FinalConfigDemo.java
java FinalConfigDemo
~~~

Stdout:

~~~text
config = api.internal:15
~~~

Mã này có **hai điểm tốt**: `Config` đã hoàn tất construction, có final fields; hơn nữa, các ghi chuẩn bị object trước `reader.start()` HB các hành động của thread reader. Do đó **không dùng kết quả ví dụ để chứng minh final một mình đã thay thế mọi kỹ thuật publication**. Đây là mẫu thực tế dễ đọc, tránh thử nghiệm unsafe publication vốn không có stdout xác định.

Việc `reader.join()` cũng bảo đảm main đợi reader kết thúc trước khi thoát, nhưng nó không phải cơ chế publication từ main sang reader; hướng publication ở đây đến từ `start()`.

## 6. `volatile`: công bố an toàn một reference sau construction

Khi producer xây xong object, sau đó ghi reference vào field `volatile`, reader đọc chính volatile field đó sẽ tham gia cơ chế đồng bộ. Những ghi trước lần volatile write của producer được quan sát qua volatile read phù hợp của consumer.

Ví dụ:

~~~java
// Dạng cấu trúc minh họa; các field và method ở cùng class.
private static volatile Config latest;

static void replaceConfig() {
    Config complete = new Config("primary", 20);
    latest = complete; // Volatile WRITE sau construction.
}

static Config currentConfig() {
    return latest; // Volatile READ.
}
~~~

Nếu `Config` là immutable snapshot, reader có thể lấy `Config local = currentConfig()` một lần rồi sử dụng nhiều field nhất quán của **cùng snapshot**. Khi nhà xuất bản thay `latest` bằng snapshot mới, reader khác có thể bắt gặp bản cũ hoặc mới tùy lần đọc, nhưng không thấy “một nửa” của phép gán reference và không phải xử lý object đang được mutate từng field.

### Ví dụ Java 17: thay snapshot qua volatile

Tạo `VolatileSnapshotDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;

public class VolatileSnapshotDemo {
    private record Settings(String host, int retries) {}

    private static volatile Settings current;
    private static final CountDownLatch readerStarted =
            new CountDownLatch(1);

    public static void main(String[] args) throws InterruptedException {
        Thread reader = new Thread(() -> {
            readerStarted.countDown();
            Settings snapshot;
            while ((snapshot = current) == null) {
                Thread.onSpinWait();
            }
            System.out.println(
                    snapshot.host() + " / retries=" + snapshot.retries());
        }, "reader");

        reader.start();
        readerStarted.await();

        Settings built = new Settings("primary", 3);
        current = built; // Ghi volatile SAU khi khởi tạo.
        reader.join();
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac VolatileSnapshotDemo.java
java VolatileSnapshotDemo
~~~

Đầu ra khi thực thi bình thường:

~~~text
primary / retries=3
~~~

`readerStarted` chỉ điều phối điểm reader đã bắt đầu để minh họa có hai thread. Consumer lặp lại thao tác **volatile read** cho tới khi thấy reference. Khi nhận được `current`, giá trị từ `built` đã được dựng xong được công bố qua volatile write/read và có thể đọc đúng.

**Vòng spin chỉ dùng trong demo.** Trong ứng dụng cần chờ có timeout/cancel/không đốt CPU, dùng `BlockingQueue`, `CountDownLatch`, `Condition`, `Future` hoặc tiện ích tương tự thay vì quay vô hạn. JMM cung cấp các bảo đảm visibility; thời gian thread được OS lập lịch và tiến triển không phải hard real-time guarantee.

### `volatile` không làm toàn bộ object tự động thread-safe

~~~java
// Minh họa lỗi logic, không phải chương trình độc lập.
class MutableState {
    int count;
}
volatile MutableState state = new MutableState();

void increment() {
    state.count++; // Vẫn là read-modify-write trên ordinary int.
}
~~~

Mỗi lần đọc field `state` có ngữ nghĩa volatile reference, nhưng phép `count++` trên object đang được chia sẻ không tự atomic. Hai writer vẫn có thể lost update. Ghi volatile vào **reference** không biến mọi field của referent thành volatile hoặc atomic, và không khóa các thao tác sau publication.

Nếu state phải thay đổi, hãy **thay thế toàn bộ immutable snapshot** qua volatile write, hoặc bảo vệ các field mutable bằng lock/atomic thích hợp.

### Hai writer: volatile replacement chưa bảo đảm read-modify-write

~~~java
// Mã minh họa sai nếu nhiều writer làm đồng thời.
Settings old = current;
current = new Settings(old.host(), old.retries() + 1);
~~~

Dù `current` là volatile, hai writer có thể cùng đọc một snapshot cũ rồi cùng ghi snapshot mới, mất một cập nhật. Muốn bảo đảm update theo state hiện tại, dùng cùng lock hoặc `AtomicReference<Settings>.updateAndGet(...)`/CAS với hàm update thuần. **Publication của snapshot mới** và **atomicity của việc tạo snapshot từ state hiện tại** là hai yêu cầu khác nhau.

## 7. `synchronized`: publication và truy cập state qua cùng monitor

`synchronized` không chỉ ngăn hai thread đi qua cùng monitor một lúc; **unlock** của producer có cạnh HB với **lock** tiếp theo của consumer trên chính monitor ấy. Những ghi vào ordinary fields bên trong critical section được reader quan sát đúng khi reader tuân theo cùng protocol.

Tạo `MonitorPublicationDemo.java`:

~~~java
public class MonitorPublicationDemo {
    private static final Object LOCK = new Object();
    private static Profile shared; // Field ordinary, không volatile.

    private record Profile(String name, int level) {}

    private static void publish(Profile ready) {
        synchronized (LOCK) {
            shared = ready;
            LOCK.notifyAll();
        }
    }

    private static Profile awaitProfile() throws InterruptedException {
        synchronized (LOCK) {
            while (shared == null) {
                LOCK.wait();
            }
            return shared;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread consumer = new Thread(() -> {
            try {
                Profile local = awaitProfile();
                System.out.println(local.name() + " level " + local.level());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        consumer.start();
        publish(new Profile("Ada", 7));
        consumer.join();
    }
}
~~~

Chạy:

~~~bash
javac MonitorPublicationDemo.java
java MonitorPublicationDemo
~~~

Đầu ra trong luồng chạy bình thường:

~~~text
Ada level 7
~~~

`publish` và `awaitProfile` đều dùng **cùng `LOCK`**. Producer công bố `Profile` sau constructor, consumer chờ condition trong monitor đó. `notifyAll()` đánh thức các thread đang chờ; **vòng `while` là bắt buộc về mặt pattern**, vì `Object.wait()` có thể thức dậy tự phát hoặc bị đánh thức khi điều kiện chưa thỏa.

Để ý: consumer có thể lock trước producer hoặc ngược lại; hai thứ tự đều đúng. Không cần `Thread.sleep` để tạo ra kết quả.

### Khi nào thêm `volatile` vào `shared`?

**Không cần** nếu *mọi đường đọc/ghi tham chiếu* đều được đồng bộ đúng theo cùng lock. Việc thêm volatile không sửa một đường đọc bên ngoài lock dùng sai protocol hoặc thiếu mutual exclusion cho nhiều field phải được đọc cùng nhau.

Nếu consumer giữ một reference tới object **mutable** rồi đọc nó ngoài lock trong khi producer tiếp tục sửa, phải bảo vệ việc sử dụng state đó hoặc chuyển sang immutable snapshot. Mẫu trên an toàn, thuận tiện vì `Profile` có state bất biến.

## 8. `ReentrantLock`: bản chất HB tương tự lock/unlock

`ReentrantLock` là lựa chọn khác khi cần `tryLock()`, lock interruptible, timeout hoặc `Condition`. Những lần `unlock()`/`lock()` trên cùng một lock cung cấp hiệu ứng đồng bộ tương đương monitor phù hợp.

~~~java
// Đoạn minh họa phương thức, không phải chương trình độc lập.
// LOCK là một ReentrantLock được chia sẻ bởi cả writer và reader.
LOCK.lock();
try {
    shared = new Profile("Ada", 7);
} finally {
    LOCK.unlock();
}
~~~

Reader cũng cần:

~~~java
LOCK.lock();
try {
    Profile snapshot = shared;
    // Chỉ dùng snapshot ngoài lock nếu nó bất biến hoặc có protocol riêng.
} finally {
    LOCK.unlock();
}
~~~

**Lock không có tác dụng nếu mỗi thread tạo một `new ReentrantLock()` riêng**, hoặc một đường đọc/ghi khác bỏ qua khóa. Đọc state đã được copy immutable ra khỏi vùng lock có thể an toàn nếu snapshot đã được khởi tạo đúng, nhưng việc tiếp tục mutation trên object chung thì vẫn cần protection.

Sử dụng `finally` để giải phóng khóa trong mọi đường trả về hoặc ném exception.

## 9. So sánh bằng tình huống: final, volatile hay khóa?

| Tình huống | Lựa chọn nên cân nhắc | Vì sao |
| --- | --- | --- |
| Value object không đổi sau constructor | `final` fields, class/record immutable; công bố qua HB | Giá trị rõ ràng và dễ chia sẻ |
| Cấu hình có nhiều field, thay bản mới theo từng đợt, reader chủ yếu đọc | `volatile` reference đến immutable snapshot | Reader lấy một snapshot rồi đọc nhất quán |
| Một biến stop flag do một writer cập nhật, reader kiểm tra | `volatile boolean`, hoặc `AtomicBoolean` nếu cần RMW | Publication/visibility cho một tín hiệu |
| Nhiều thread phải sửa nhiều field mutable đồng bộ | `synchronized` hoặc `ReentrantLock` trên cùng state | Lock giữ invariant trong critical section |
| Phải tính snapshot mới từ snapshot hiện tại với nhiều writer | Lock hoặc CAS qua `AtomicReference<ImmutableState>` | Chặn lost update |
| Task producer truyền object sang consumer | `BlockingQueue`, executor/Future theo hợp đồng | Vừa có luồng giao nhận vừa có HB |
| Singleton Java khởi tạo lười, không framework | Initialization-on-demand holder | JVM đảm bảo class initialization an toàn |
| Nested object có mutable fields | Defensive copy, confinement, lock/atomic cho mutation | `final` reference không đủ |
| Constructor có callback hoặc start thread | Refactor để không làm `this` escape | Publication trước freeze/hoàn thành construction nguy hiểm |

**Chọn cơ chế từ invariant và đường truy cập thực tế**, không chọn dựa vào “field được đọc nhiều nên thêm volatile cho yên tâm”.

## 10. Ví dụ về *constructor escape*: final cũng có giới hạn

Hãy tưởng tượng constructor này:

~~~java
// Mã minh họa phản-pattern; không phải chương trình độc lập.
class BadStart {
    final int number;

    BadStart() {
        new Thread(() -> consume(number)).start();
        number = 42;
    }
}
~~~

Đừng sử dụng pattern này. Việc khởi động thread có thể khiến `this` bị lộ cho consumer **trước khi constructor hoàn tất và trước khi final field có giá trị dự định**. Hơn nữa, với khai báo Java như trên, compiler có thể từ chối tham chiếu tới blank final field từ lambda trước khi field được definite-assigned; để trình bày lỗi ở mức nguyên lý, ví dụ chuẩn hơn là có một **method instance** đọc field, và thread gọi method ấy:

~~~java
// Mã minh họa nguy cơ về lifecycle, không yêu cầu chạy.
class UnsafeStarter {
    final int number;

    UnsafeStarter() {
        new Thread(this::inspect).start(); // Rò rỉ this quá sớm.
        number = 42;
    }

    void inspect() {
        consume(number);
    }

    void consume(int value) {
        // Consumer có thể quan sát số chưa được khởi tạo.
    }
}
~~~

Ngay cả khi tham chiếu `this` được truyền tới thread mới bằng `start()`, HB của `start()` chỉ bao phủ các hành động **trước** lời gọi start, không tự bao gồm phép gán `number = 42` xảy ra **sau**. Đọc giá trị mặc định do khởi tạo chưa hoàn tất là một rủi ro thực tế; không được yêu cầu stdout cụ thể hay đếm xác suất vì phụ thuộc scheduling.

### Thiết kế đúng: tạo xong rồi mới kích hoạt

~~~java
// Cấu trúc minh họa, không phải chương trình độc lập.
final class ReadyService {
    final int number;

    ReadyService() {
        number = 42;
    }

    void inspect() {
        System.out.println(number);
    }
}

// Bên ngoài constructor:
ReadyService service = new ReadyService();
new Thread(service::inspect).start();
~~~

Ở đây object hoàn tất construction trước khi thread mới bắt đầu; những hành động chuẩn bị trước `start` HB hành động của thread đó. Với API đăng ký callback/listener, tương tự: tạo object xong rồi mới đăng ký qua một protocol được đồng bộ.

**Ngoài early escape, gọi phương thức overridable trong constructor cũng có thể phơi trạng thái subclass chưa khởi tạo xong**; ưu tiên class `final` cho immutable value object hoặc tránh gọi hành vi overridable từ constructor.

## 11. Một phương án khác: class initialization công bố singleton

Java class initialization tự có quy tắc đồng bộ. Có thể tận dụng *initialization-on-demand holder* mà không tự viết DCL:

~~~java
// HolderPublicationDemo.java — Java 17+
public class HolderPublicationDemo {
    static final class Service {
        final String endpoint;

        Service(String endpoint) {
            this.endpoint = endpoint;
        }
    }

    private static final class Holder {
        static final Service INSTANCE = new Service("local");
    }

    static Service service() {
        return Holder.INSTANCE;
    }

    public static void main(String[] args) {
        Service a = service();
        Service b = service();
        System.out.println("same = " + (a == b));
        System.out.println("endpoint = " + b.endpoint);
    }
}
~~~

Chạy:

~~~bash
javac HolderPublicationDemo.java
java HolderPublicationDemo
~~~

Stdout:

~~~text
same = true
endpoint = local
~~~

Mã trên dựa vào class initialization của JVM để tạo singleton an toàn và chỉ một lần, miễn constructor không làm `this` escape. Đây là lựa chọn gọn khi cần một singleton immutable Java thuần.

**Static `final` không tự khiến referent thread-safe:** nếu `INSTANCE` là một mutable service có method sửa state, các method đó vẫn cần thiết kế concurrency đúng, mặc dù reference đã công bố an toàn.

## 12. Double-checked locking: tại sao reference phải volatile?

Lazy initialization thường được viết kiểu:

~~~java
// KHÔNG an toàn nếu instance được đọc ngoài lock mà không volatile.
private static Service instance;

static Service instance() {
    if (instance == null) {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new Service();
            }
        }
    }
    return instance;
}
~~~

Reader ở phép kiểm tra đầu tiên **không lấy lock**; nếu `instance` là field ordinary, nó thiếu cạnh HB với thread đã ghi trong synchronized. Lỗi này không được sửa bằng cách “constructor chắc đã xong theo thời gian thực”.

Đối với Java 5+, một dạng DCL an toàn là (ví dụ cấu trúc class, không phải tệp độc lập):

~~~java
private static volatile Service instance;
private static final Object LOCK = new Object();

static Service instance() {
    Service local = instance;
    if (local == null) {
        synchronized (LOCK) {
            local = instance;
            if (local == null) {
                local = new Service();
                instance = local; // Volatile write sau construction.
            }
        }
    }
    return local;
}
~~~

Nhờ `volatile`, phép đọc ngoài lock có quan hệ memory visibility thích hợp với phép ghi publication, đồng thời chỉ một thread đi qua critical section khởi tạo khi instance còn null. `local` tránh đọc field volatile lại không cần thiết ở đường nhanh. **Điều kiện:** constructor không rò rỉ `this`, mọi đường khởi tạo phải tuân theo protocol này và các thao tác mutable của service sau khởi tạo vẫn được bảo vệ riêng.

Nếu không cần tối ưu lazy singleton theo pattern này, dùng holder ở phần trước thường dễ kiểm tra hơn.

## 13. Publication qua API concurrency đã có sẵn

Không phải lần chuyển tham chiếu nào cũng cần tự tạo `volatile` field. Một số API tiêu chuẩn đưa ra memory consistency rõ ràng.

- **`BlockingQueue`:** các hành động của producer **trước khi đặt** phần tử vào queue HB các hành động của consumer **sau khi lấy** phần tử đó.
- **`ExecutorService.submit` → task → `Future.get()`:** những hành động trước khi submit HB các hành động của task; các hành động của task HB những hành động sau khi thread khác lấy kết quả bằng `Future.get()`.
- **`Thread.start`/`Thread.join`:** cung cấp các cạnh HB đúng hướng cho các dữ liệu được chuẩn bị trước start hoặc sản sinh bởi thread trước khi join hoàn tất.
- **`ConcurrentHashMap`:** các thao tác trên mapping cung cấp memory-consistency và khả năng cập nhật nguyên tử theo hợp đồng; không có nghĩa cả chuỗi thao tác nhiều key là một transaction.

### Ví dụ Java 17: chuyển snapshot qua `BlockingQueue`

Tạo `QueueHandoffDemo.java`:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class QueueHandoffDemo {
    private record Message(String title, int version) {}

    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Message> queue = new ArrayBlockingQueue<>(1);

        Thread consumer = new Thread(() -> {
            try {
                Message message = queue.take();
                System.out.println(message.title() + " v" + message.version());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        consumer.start();
        Message ready = new Message("config", 2);
        queue.put(ready);
        consumer.join();
    }
}
~~~

Chạy:

~~~bash
javac QueueHandoffDemo.java
java QueueHandoffDemo
~~~

Stdout:

~~~text
config v2
~~~

Queue vừa là kênh giao nhận có điều phối, vừa có bảo đảm HB. Không cần một volatile reference riêng chỉ để chuyển message này. Nhưng nếu producer tiếp tục **sửa nội dung mutable** của message sau `put`, sự sửa ấy không tự được bảo vệ bởi queue; nên dùng immutable message hoặc chuyển quyền sở hữu rõ ràng.

## 14. Safe publication khác thread safety của toàn bộ đối tượng

Xem ba thiết kế:

**Thiết kế A — immutable object được publish:** 

~~~java
record Snapshot(String host, int port) {}
volatile Snapshot current;
~~~

Reader lấy local snapshot và chỉ đọc các field bất biến. Nếu producer thay reference bằng snapshot mới, reader đã giữ snapshot cũ có thể tiếp tục sử dụng nhất quán. Tuy nhiên, nếu nhiều writer tính snapshot mới từ snapshot cũ, cần lock/CAS để tránh mất cập nhật.

**Thiết kế B — mutable object publish qua volatile rồi sửa thường:**

~~~java
final class MutableCounter {
    int value;
}
volatile MutableCounter counter;
~~~

Publication an toàn của reference chỉ bảo đảm quan sát các ghi trước publication. `counter.value++` ở nhiều thread vẫn là race. Thêm `volatile` vào `value` cũng không làm `++` atomic; dùng `AtomicInteger.incrementAndGet()` hoặc lock cho đúng.

**Thiết kế C — mutable object dùng chung qua một monitor:**

~~~java
// Minh họa protocol: tất cả truy cập vào state đều dùng LOCK.
synchronized (LOCK) {
    state.balance += amount;
    state.version++;
}
~~~

Nếu invariant yêu cầu `balance` và `version` được cập nhật/đọc cùng nhau, monitor bảo vệ toàn bộ tổ hợp. Cần reader đọc **cả hai** dưới cùng lock, hoặc lấy một immutable snapshot dưới lock rồi sử dụng ngoài lock.

Đừng dùng `final` trên **reference** rồi nghĩ state mutable của referent trở thành một immutable snapshot. Safe publication chỉ là *bước đầu* của thread safety khi có trạng thái chung thay đổi.

## 15. Lưu ý về `final`, record, defensive copy và object graph

Đối với object graph có nhiều tầng, câu hỏi không chỉ là object ngoài cùng có field final hay không:

~~~java
record Team(java.util.List<Member> members) {}
// Nếu Member có mutable field hoặc caller còn sửa list,
// riêng cú pháp record không chứng minh cả graph immutable.
~~~

Các quy tắc cần nhớ:

1. `record` tạo các component field là final nhưng một component tham chiếu object mutable vẫn có thể bị sửa.
2. Bản sao shallow của collection chỉ bảo vệ **cấu trúc collection**, không sao chép sâu mọi phần tử.
3. Với object bất biến thật sự, chọn value types bất biến, thực hiện defensive copy khi xây dựng và không expose mutable internals qua getters.
4. `final` phù hợp để giữ invariant sau construction, nhưng **đừng cho `this` thoát sớm**.
5. Các mutation sau publication cần khóa/atomic/ownership dù các tham chiếu dẫn tới state đó là final.
6. Nếu dùng nhiều trường thay đổi cùng nhau, việc volatile riêng từng field không tự bảo đảm một snapshot nhất quán của toàn bộ nhóm.

Một API rõ ràng thường nhận immutable input, dựng một snapshot mới, và publish snapshot qua một điểm đồng bộ duy nhất.

## 16. Cách suy luận và kiểm tra một đường publication

Khi review code có object chia sẻ giữa thread, hãy làm theo thứ tự:

1. **Vẽ đường reference:** object được tạo ở đâu, constructor làm gì, field/listener/queue nào chuyển tham chiếu cho reader?
2. **Kiểm tra construction:** invariant đã sẵn sàng trước khi reference thoát chưa? Có `this` escape, start thread trong constructor, callback, overridable method?
3. **Phân loại state:** field nào `final`; phần nào mutable; có nested mutable object, collection, array hay cache?
4. **Tìm cạnh HB:** volatile write/read cùng biến, unlock/lock cùng monitor, queue handoff, submit/get, start/join hay class init? Chỉ ra cụ thể trước/sau của cạnh.
5. **Kiểm tra tất cả reader và writer:** có đường bypass lock/volatile không? Có thread giữ direct reference từ registry không đồng bộ?
6. **Kiểm tra sau publication:** object có bị sửa tiếp không? Có compound action cần atomicity hoặc invariant nhiều field không?
7. **Chọn giải pháp nhỏ, rõ:** immutable snapshot + volatile cho read-mostly; monitor/Lock cho mutable invariant; concurrent utility cho giao nhận; atomic/CAS cho state cập nhật độc lập.
8. **Xác minh correctness trước hiệu năng:** lập luận theo JMM và invariant; test có latch để điều khiển interleaving nếu cần; đừng kết luận an toàn từ stress test pass.

### Lỗi thường gặp

- **“Constructor chạy xong theo đồng hồ là đủ.”** Không: reader phải có cơ sở publication/happens-before thích hợp.
- **“`final` làm object graph bất biến sâu.”** Không: final field có thể tham chiếu mutable collection/object.
- **“`volatile` reference làm mọi field thường đều volatile.”** Không: nó publish các ghi đã xảy ra trước; mutation sau đó phải được bảo vệ.
- **“Một `counter++` trên volatile state là atomic.”** Không: read-modify-write cần atomic API hoặc khóa.
- **“`synchronized` ở hai nơi là đủ dù khác monitor.”** Không: HB thông qua monitor phải dùng cùng khóa.
- **“Publish trước constructor xong vẫn ổn nếu reference volatile.”** Không: volatile không publish những ghi chưa xảy ra.
- **“DCL thiếu volatile nhưng bên writer dùng lock rồi.”** Không: reader fast path ngoài lock không đồng bộ với writer.
- **“`Thread.sleep` chờ đủ lâu là publication an toàn.”** Không: ngủ không tạo HB cho các field.
- **“Concurrent queue đảm bảo object mutable an toàn mãi về sau.”** Không: cần quy tắc mutation/ownership sau handoff.
- **“Safe publication đã giải quyết race trên mọi operation.”** Không: phải kiểm tra thread safety của cả API sau khi object được chia sẻ.

## 17. Câu hỏi tự kiểm tra

**Câu 1:** `final Config config` được gán đúng trong constructor, nhưng `Config` chứa `ArrayList` mutable. Có thể để nhiều thread thêm phần tử không khóa chỉ vì field `config` là final không?  
**Đáp án:** Không. Final cố định reference, không làm mutation của `ArrayList` thread-safe.

**Câu 2:** Producer xây `Config` rồi ghi vào `volatile Config current`; consumer đọc `current` vào một biến local rồi đọc các field immutable. Vì sao consumer thấy state đúng?  
**Đáp án:** Publication đi qua volatile write/read, tạo cạnh HB phù hợp giữa các ghi constructor và các lần đọc field trong consumer.

**Câu 3:** Dùng `volatile MutableState state` rồi nhiều thread gọi `state.count++`. Có tránh lost update không?  
**Đáp án:** Không. Volatile ở reference không biến RMW trên field `count` thành atomic.

**Câu 4:** Producer ghi `shared` trong `synchronized(lockA)`, consumer đọc trong `synchronized(lockB)`. Có đủ không?  
**Đáp án:** Không, nếu lockA và lockB khác nhau và không có cơ chế HB khác.

**Câu 5:** `this` được đưa ra registry ở đầu constructor rồi cuối constructor mới gán final fields. Final-field semantics có bảo vệ những lần đọc xảy ra khi constructor đang chạy không?  
**Đáp án:** Không. Những lần đọc có thể xảy ra **trước khi gán giá trị/freeze**; cần tránh early escape.

**Câu 6:** Khi đổi một cấu hình gồm ba trường, reader cần một snapshot có ba giá trị cùng phiên bản. Nên publish từng field volatile riêng hay một immutable object qua một volatile reference?  
**Đáp án:** Thường ưu tiên một immutable snapshot được publish bằng một volatile reference, để reader giữ và đọc cùng một phiên bản.

**Câu 7:** Một worker xử lý message nhận từ `BlockingQueue`. Producer phải tự thêm volatile cho từng field của message immutable chỉ để consumer thấy những ghi trước `put` không?  
**Đáp án:** Không; queue cung cấp memory-consistency cho thao tác giao nhận theo hợp đồng. Mutation sau `put` là chuyện khác.

**Câu 8:** Khi nào nên dùng monitor thay vì chỉ volatile?  
**Đáp án:** Khi có mutable shared state hoặc nhiều hành động/field cần được bảo vệ như **một invariant**, hoặc nhiều writer phải thực hiện compound operation nguyên tử.

## Tài liệu API và đặc tả chính thức

- [Java Language Specification `17.4 — Memory Model, Java SE 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4): synchronization actions, happens-before, data race.
- [Java Language Specification `17.5 — final Field Semantics, Java SE 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.5): freeze và điều kiện correctly constructed.
- [Java Language Specification `12.4 — Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4): khởi tạo class.
- [`Thread` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html): `start()` và `join()`.
- [`BlockingQueue` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): memory consistency cho producer–consumer.
- [`ExecutorService` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html), [`Future` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): giao nhận và kết quả task.
- [`AtomicReference` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html): atomic replacement và CAS.

**Ghi nhớ:** `final` bảo vệ những giá trị được khởi tạo đúng cách theo final-field semantics; `volatile` công bố state trước write cho reader đồng bộ; khóa bảo vệ cả publication lẫn truy cập mutable state khi các thread dùng chung protocol. Luôn hoàn tất **construction trước publication**, rồi quyết định riêng cách bảo vệ **mutation sau publication**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-007">Defensive copy và snapshot bất biến</a>

<details>
<summary>Xem chi tiết</summary>

# Defensive copy và snapshot bất biến trong Java concurrency

## 1. Vấn đề cần giải quyết: cùng giữ một tham chiếu mutable

Trong chương trình Java nhiều thread, lỗi có thể xuất hiện khi hai thành phần vô tình dùng chung một đối tượng có thể thay đổi (*mutable object*). Một thành phần nghĩ mình đã lưu một cấu hình ổn định, nhưng thread khác vẫn giữ tham chiếu đến cấu hình ban đầu và sửa nó. Ngay cả khi không xảy ra lỗi đồng thời tức thời, thiết kế đó làm mất quyền kiểm soát đối với trạng thái và khiến việc chứng minh thread safety khó khăn.

Hai kỹ thuật quan trọng:

- **Defensive copy (sao chép phòng vệ):** tạo một bản sao thích hợp ở ranh giới nhận/trả dữ liệu để bên ngoài không thể thay đổi trạng thái nội bộ thông qua tham chiếu được chia sẻ.
- **Immutable snapshot (ảnh chụp bất biến):** biểu diễn trạng thái tại một thời điểm bằng một giá trị bất biến sau khi tạo; người đọc có thể giữ snapshot đó trong khi người ghi tạo và công bố phiên bản mới.

Tính **bất biến** của một snapshot giúp tránh race trên *nội dung* của snapshot. Nhưng **việc công bố tham chiếu snapshot sang thread khác vẫn cần đúng Java Memory Model**, ví dụ thông qua trường `volatile`, `AtomicReference`, monitor lock hoặc các cơ chế truyền dữ liệu có bảo đảm happens-before.

Bài học tập trung vào Java nói chung: các collection, object, record, lock, volatile và atomic API trong JDK. Không cần framework để áp dụng các nguyên tắc này.

### Mục tiêu sau bài

1. Phân biệt sao chép độc lập, unmodifiable view, immutable value, shallow copy và deep copy.
2. Biết phải copy khi **nhận dữ liệu** và khi **trả dữ liệu** ở đúng ranh giới sở hữu.
3. Dùng `List.copyOf`, `Set.copyOf`, `Map.copyOf`, constructor bất biến và defensive getter một cách đúng đắn.
4. Tạo snapshot nhất quán từ trạng thái nhiều biến và công bố snapshot an toàn cho reader.
5. Hiểu giới hạn: bản sao collection không tự sao chép các phần tử mutable, unmodifiable không có nghĩa thread-safe, snapshot có chi phí bộ nhớ và có thể cũ theo thời gian.

## 2. Lỗi aliasing: hai tham chiếu trỏ đến cùng object

**Aliasing** xảy ra khi hai biến trỏ đến một object. Với object mutable, tác động từ một biến có thể được nhìn thấy qua biến còn lại:

~~~java
// Minh họa trong một phương thức Java, không phải chương trình độc lập.
List<String> original = new ArrayList<>();
original.add("read");

List<String> alias = original;  // Không copy.
alias.add("write");

// original giờ chứa ["read", "write"].
~~~

Khi đưa `original` vào constructor rồi giữ nguyên tham chiếu, caller có thể sửa nội bộ đối tượng thông qua biến ban đầu. Nếu getter trả thẳng một `ArrayList` bên trong, caller cũng có thể sửa trạng thái sau khi nhận object.

Trong ứng dụng nhiều thread, hai đường sửa đó đặc biệt nguy hiểm:

- Một reader đang duyệt collection, writer sửa đồng thời có thể gây kết quả không nhất quán, exception hoặc data race tùy loại collection.
- Một component định nghĩa invariant như “cấu hình luôn chứa một tập quyền đã kiểm tra”, nhưng caller thêm/bớt phần tử sau khi validation.
- Một luồng công bố cấu hình, luồng khác đọc nhiều trường ở những thời điểm khác nhau và ghép thành một trạng thái chưa từng tồn tại.

Giải pháp bắt đầu từ **quyền sở hữu**: ai được sửa dữ liệu, ai chỉ đọc, và tại ranh giới nào cần sao chép hoặc truyền một giá trị bất biến.

## 3. Unmodifiable view khác defensive copy

Một *unmodifiable view* chặn thao tác sửa **qua view**, nhưng có thể vẫn phản ánh thay đổi ở backing collection. Trong khi đó, `List.copyOf` tạo một danh sách không thể sửa qua giao diện danh sách và không còn bị ảnh hưởng bởi **thay đổi cấu trúc** của nguồn mutable sau lúc copy.

### Ví dụ 1 — view “chỉ đọc” vẫn thay đổi theo nguồn (Java 17+)

Tạo tệp `ViewVsSnapshotDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ViewVsSnapshotDemo {
    public static void main(String[] args) {
        List<String> source = new ArrayList<>();
        source.add("alpha");

        List<String> liveView =
            Collections.unmodifiableList(source);
        List<String> snapshot = List.copyOf(source);

        source.add("beta");

        System.out.println("view = " + liveView);
        System.out.println("snapshot = " + snapshot);

        try {
            snapshot.add("gamma");
        } catch (UnsupportedOperationException error) {
            System.out.println("snapshot rejects add");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ViewVsSnapshotDemo.java
java ViewVsSnapshotDemo
~~~

Stdout:

~~~text
view = [alpha, beta]
snapshot = [alpha]
snapshot rejects add
~~~

Giải thích:

- `Collections.unmodifiableList(source)` tạo view không cho thêm/xóa qua view; khi `source` được sửa trực tiếp, view thấy nội dung mới.
- `List.copyOf(source)` ghi nhận danh sách phần tử tại thời điểm copy, vì vậy sau khi thêm `beta` vào `source`, cấu trúc danh sách snapshot không đổi.
- Cả hai đối tượng đều từ chối `add` qua giao diện của chúng. Việc cùng từ chối `add` **không chứng minh cùng có snapshot semantics**.
- `List.copyOf` có thể tái sử dụng một danh sách đầu vào vốn đã thuộc họ unmodifiable phù hợp; **không dựa vào việc kết quả luôn có identity khác nguồn**. Điều cần bảo đảm là snapshot không bị thay đổi cấu trúc bởi nguồn mutable sau khi copy.

| Cách tạo | Có sửa qua kết quả được không? | Nguồn thay đổi cấu trúc sau đó có làm kết quả đổi? | Phần tử mutable bên trong có được copy không? |
| --- | --- | --- | --- |
| `alias = source` | Có, nếu nguồn mutable | Có | Không |
| `Collections.unmodifiableList(source)` | Không qua view | **Có**, view phản ánh backing list | Không |
| `new ArrayList<>(source)` | Có, bản sao là mutable | Không (với thao tác cấu trúc trên nguồn sau copy) | Không |
| `List.copyOf(source)` | Không | Không, nếu nguồn là mutable thông thường | **Không**, chỉ copy cấu trúc |
| `List.of("A", "B")` | Không | Không có backing list mutable bên ngoài | Không áp dụng với `String` bất biến |

Bản sao cấu trúc không tự bảo đảm snapshot nhất quán nếu **nguồn đang bị thread khác sửa trong lúc copy**. Cần phối hợp khi thực hiện copy, như khóa, cơ chế snapshot của dữ liệu nguồn hoặc thiết kế công bố bất biến ngay từ đầu.

### Các giới hạn của `copyOf`

- `List.copyOf`, `Set.copyOf` và `Map.copyOf` có trong Java 10+, nên dùng được với Java 17.
- Các factory/copy method này **không chấp nhận phần tử null**; với map, cả key lẫn value null cũng không được chấp nhận. Khi nguồn có null, có thể gặp `NullPointerException`.
- Kết quả là unmodifiable collection ở cấp collection, **không phải deep immutable** nếu phần tử hoặc value vẫn mutable.
- Không phụ thuộc vào identity hay implementation class của kết quả, cũng không dùng kết quả như monitor lock có identity ổn định.
- `Set.copyOf` đối với input collection có phần tử trùng bằng `equals` chỉ giữ một đại diện; **không bảo đảm chọn đại diện nào**. Không dùng nó khi việc giữ đủ bản ghi trùng hoặc thứ tự là yêu cầu nghiệp vụ.
- `Map.copyOf` nhận một `Map` và không biến nested map/list thành bất biến; thứ tự duyệt của map cũng không được mặc định giống bản gốc.

## 4. Copy ở ranh giới vào và ra của đối tượng

Một lớp muốn bảo vệ trạng thái nội bộ thường cần hai phía:

1. **Copy in:** constructor/setter nhận một collection hoặc object mutable từ caller thì copy trước khi lưu.
2. **Copy out:** getter không để caller sửa nội bộ qua reference trả về. Có thể trả trực tiếp **immutable value** đã được bảo đảm an toàn; nếu nội bộ vẫn mutable, phải trả bản sao thích hợp.

### Ví dụ 2 — lớp immutable với copy in và giá trị trả ra an toàn

Tạo tệp `ImmutableConfigDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;

public class ImmutableConfigDemo {
    static final class WorkerConfig {
        private final List<String> permissions;

        WorkerConfig(List<String> requestedPermissions) {
            this.permissions = List.copyOf(requestedPermissions);
        }

        List<String> permissions() {
            return permissions;
        }
    }

    public static void main(String[] args) {
        List<String> input = new ArrayList<>();
        input.add("read");

        WorkerConfig config = new WorkerConfig(input);
        input.add("delete");

        System.out.println("config = " + config.permissions());

        try {
            config.permissions().add("write");
        } catch (UnsupportedOperationException error) {
            System.out.println("getter result is unmodifiable");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ImmutableConfigDemo.java
java ImmutableConfigDemo
~~~

Stdout:

~~~text
config = [read]
getter result is unmodifiable
~~~

Vì `WorkerConfig` giữ một `final List<String>` được tạo bằng `List.copyOf` và phần tử là `String` bất biến, caller không sửa được nội dung qua input hoặc getter. **Getter không nhất thiết cần tạo thêm một bản sao** khi dữ liệu bên trong đã là immutable value có thể chia sẻ an toàn. Copy lặp lại mỗi lần đọc chỉ làm tăng allocation mà không cung cấp bảo vệ bổ sung trong trường hợp này.

Từ khóa `final` bảo đảm **tham chiếu trường không bị gán lại**, nhưng không tự biến object mà nó trỏ tới thành immutable. Nếu lưu thẳng một `ArrayList` mutable vào `final List`, caller vẫn có thể sửa các phần tử và cấu trúc của danh sách đó bằng tham chiếu khác.

### Khi nội bộ cần mutable, getter phải bảo vệ

Ví dụ một lớp lưu `ArrayList` và cập nhật dưới khóa. Getter muốn trả snapshot thì cũng phải copy **khi giữ cùng khóa**:

~~~java
// Minh họa trong cùng một class; không phải chương trình độc lập.
private final List<String> events = new ArrayList<>();

public synchronized void addEvent(String event) {
    events.add(event);
}

public synchronized List<String> snapshotEvents() {
    return List.copyOf(events);
}
~~~

Với đoạn trên, copy được thực hiện khi đã khóa object hiện tại; các thao tác writer trong cùng class cũng phải theo **cùng monitor**. Đây là điều cần thiết để snapshot không bị sửa ở giữa phép copy. Nếu getter trả `Collections.unmodifiableList(events)`, caller sẽ nhận một view luôn thay đổi theo những lần thêm tiếp theo, không phải snapshot.

## 5. Shallow copy không bảo vệ phần tử mutable

`List.copyOf` chỉ sao chép cấu trúc collection. Nếu một phần tử chứa trạng thái mutable, cả nguồn và snapshot vẫn giữ **cùng tham chiếu phần tử**.

~~~java
// Minh họa bên trong phương thức, không phải chương trình độc lập.
StringBuilder item = new StringBuilder("A");
List<StringBuilder> snapshot = List.copyOf(List.of(item));

item.append("B");
// snapshot.get(0).toString() giờ là "AB".
~~~

`snapshot.add(...)` bị chặn, nhưng `snapshot.get(0).append(...)` vẫn sửa object phần tử. Vì vậy snapshot chưa đạt **deep immutability**.

### Ví dụ 3 — chuyển phần tử mutable thành immutable data (Java 17+)

Tạo tệp `DeepCopyBoundaryDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;

public class DeepCopyBoundaryDemo {
    static final class MutableLabel {
        private String text;

        MutableLabel(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }

        void rename(String value) {
            text = value;
        }
    }

    static final class LabelSnapshot {
        private final List<String> values;

        LabelSnapshot(List<MutableLabel> input) {
            List<String> copy = new ArrayList<>();
            for (MutableLabel label : input) {
                copy.add(label.text());
            }
            values = List.copyOf(copy);
        }

        List<String> values() {
            return values;
        }
    }

    public static void main(String[] args) {
        MutableLabel label = new MutableLabel("alpha");
        List<MutableLabel> source = new ArrayList<>();
        source.add(label);

        LabelSnapshot snapshot = new LabelSnapshot(source);
        label.rename("beta");
        source.add(new MutableLabel("gamma"));

        System.out.println("snapshot = " + snapshot.values());
        System.out.println("current = " + label.text());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac DeepCopyBoundaryDemo.java
java DeepCopyBoundaryDemo
~~~

Stdout:

~~~text
snapshot = [alpha]
current = beta
~~~

Trong constructor, từng `MutableLabel` được **chuyển thành giá trị `String` bất biến**, sau đó danh sách kết quả được tạo bất biến. Vì vậy thay đổi cấu trúc nguồn và thay đổi `label` sau khi constructor chạy đều không ảnh hưởng tới snapshot.

**Quan trọng:** ví dụ minh họa copy khi nguồn không bị sửa đồng thời trong lúc constructor thực thi. Nếu một writer khác đang gọi `rename` ngay trong quá trình copy, bạn cần đồng bộ hoặc giao thức snapshot từ nguồn để có một thời điểm quan sát nhất quán. Deep copy đơn thuần không làm một phép đọc nhiều object trở thành atomic.

### Phân biệt shallow/deep copy

| Nhu cầu | Sao chép phù hợp |
| --- | --- |
| `List<String>` với phần tử bất biến | `List.copyOf` thường đủ |
| `List<MutableOrder>` dùng chung với writer | Copy từng `MutableOrder` thành immutable DTO/snapshot rồi `List.copyOf` |
| Mảng `byte[]` nhận từ bên ngoài | `clone()`/`Arrays.copyOf` khi nhận; copy lại khi trả |
| Mảng object chứa phần tử mutable | Copy mảng **và** từng object mutable bên trong nếu muốn deep copy |
| Record có field `List<MutableThing>` | Record mặc định chỉ bất biến ở reference; phải copy/chuẩn hóa nội dung |

Với graph object có vòng tham chiếu, tài nguyên như socket hoặc file, “deep copy mọi thứ” có thể không khả thi hoặc không có nghĩa. Khi đó thiết kế immutable projection chỉ chứa dữ liệu cần đọc thường rõ ràng hơn sao chép tùy tiện toàn bộ object graph.

## 6. Array, Date, record và các bẫy bất biến thường gặp

### 6.1. Mảng luôn có thể sửa

Trường `private final byte[] key` không có nghĩa nội dung mảng không thể thay đổi. Nếu caller còn giữ tham chiếu mảng, họ có thể sửa `key[0]` sau khi đưa vào constructor.

Một mẫu phòng vệ:

~~~java
// Minh họa trong class chứa byte[]; không phải chương trình độc lập.
private final byte[] bytes;

public Payload(byte[] input) {
    this.bytes = input.clone();
}

public byte[] bytes() {
    return bytes.clone();
}
~~~

`clone()` với mảng primitive tạo bản sao độc lập của các giá trị primitive. Với mảng object, `clone()` chỉ sao chép danh sách tham chiếu, **không clone từng object con**. Khi dùng mảng lưu bí mật, còn phải thiết kế vòng đời dữ liệu, quyền truy cập và chính sách xóa nhạy cảm phù hợp; clone không tự giải quyết quản lý bí mật.

### 6.2. Các kiểu ngày giờ

`java.util.Date` là mutable; trả thẳng `Date` từ getter cho phép caller sửa bằng `setTime(...)`. Có thể copy qua `new Date(original.getTime())` ở ranh giới vào/ra hoặc chuyển sang kiểu bất biến trong `java.time` như `Instant`, `LocalDate` khi đúng ý nghĩa nghiệp vụ.

### 6.3. Record không tự động deep immutable

Java record làm các **component fields** trở thành `private final`, đồng thời sinh accessor, nhưng object được tham chiếu vẫn có thể mutable.

~~~java
// Minh họa Java 17+, không phải chương trình độc lập.
record Batch(List<String> items) {
    Batch {
        items = List.copyOf(items);
    }
}
~~~

Trong compact canonical constructor, tham số `items` được gán lại thành bản sao unmodifiable trước khi lưu vào field được sinh. Với `String` bất biến, record trên tạo value object phù hợp để chia sẻ. Nếu `items` chứa object mutable, phải chuyển/copy phần tử nữa. Nếu record chứa mảng, accessor mặc định trả tham chiếu mảng; có thể cần override accessor để copy out.

Không nên nhầm **immutable class** với “mọi field đều `final`”. Tính bất biến phải được xét đệ quy ở phần **trạng thái có thể quan sát và sửa**.

## 7. Snapshot bất biến và tính nhất quán tại một thời điểm

Một snapshot có ý nghĩa khi nó biểu diễn **một trạng thái hợp lệ** tại một thời điểm chuyển giao. Giả sử trạng thái hệ thống có hai biến `left` và `right`, với invariant `left + right == 100`. Nếu reader đọc `left` rồi writer cập nhật, sau đó reader đọc `right`, reader có thể ghép giá trị từ hai thời điểm thành tổng không bằng 100.

Ngay cả khi cả hai biến đều là `volatile`, việc đọc lần lượt **không tạo snapshot atomic của cả cặp**.

Hai chiến lược thường gặp:

1. **Copy dưới khóa:** reader giữ cùng lock mà writer dùng trong toàn bộ quá trình copy; snapshot phản ánh một trạng thái không bị writer chen vào.
2. **Single immutable state object:** writer tạo một object trạng thái mới hoàn chỉnh rồi công bố cả tham chiếu bằng `volatile` hoặc `AtomicReference`; reader đọc tham chiếu một lần và dùng chính object đó cho mọi field.

Phân biệt **immutability** và **publication**:

- Không sửa object snapshot sau khi tạo giúp reader không đua nhau cập nhật nội dung.
- Công bố tham chiếu qua cơ chế có happens-before giúp reader được bảo đảm thấy trạng thái đã khởi tạo.
- Một reader có thể giữ snapshot phiên bản cũ trong khi writer công bố phiên bản mới; đó là đặc tính mong muốn trong nhiều thiết kế, không phải lỗi visibility.
- Snapshot không có nghĩa đọc “dữ liệu mới nhất ở mọi thời điểm”; nó có nghĩa đọc **một phiên bản nhất quán**.

## 8. Ví dụ 4 — copy snapshot trong synchronized (Java 17+)

Tạo tệp `LockedSnapshotDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;

public class LockedSnapshotDemo {
    static final class EventStore {
        private final List<String> events = new ArrayList<>();

        synchronized void add(String value) {
            events.add(value);
        }

        synchronized List<String> snapshot() {
            return List.copyOf(events);
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        EventStore store = new EventStore();
        store.add("created");

        List<String> firstSnapshot = store.snapshot();

        Thread writer = new Thread(() -> {
            store.add("processed");
        }, "event-writer");

        writer.start();
        writer.join();

        System.out.println("old = " + firstSnapshot);
        System.out.println("new = " + store.snapshot());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac LockedSnapshotDemo.java
java LockedSnapshotDemo
~~~

Stdout:

~~~text
old = [created]
new = [created, processed]
~~~

`add` và `snapshot` dùng cùng monitor của `EventStore`, nên không có writer chen vào giữa lúc `snapshot` copy list. `firstSnapshot` giữ trạng thái cũ ngay cả khi writer thêm `processed`. `join()` xác nhận writer đã hoàn tất trước khi chụp snapshot thứ hai.

Điều này khác với việc giữ `Collections.unmodifiableList(events)`: view đó sẽ phản ánh phần tử `processed` trong cả hai lần đọc.

Một snapshot copy đúng dưới khóa vẫn có chi phí: mỗi lần `snapshot()` cần duyệt/cấp phát tỉ lệ với kích thước collection. Nếu reader nhiều hơn writer rất lớn, có thể cân nhắc công bố nguyên khối immutable state để reader chỉ đọc tham chiếu.

## 9. Công bố snapshot qua volatile hoặc AtomicReference

### 9.1. Một reference volatile cho cả state

Ví dụ cấu trúc ý tưởng:

~~~java
// Minh họa trong một class; cấu hình immutable đã được tạo hợp lệ.
private volatile ConfigSnapshot current;

// Writer:
current = newSnapshot;

// Reader:
ConfigSnapshot local = current;
// Tiếp tục đọc tất cả field qua cùng biến local.
~~~

**Gán một reference** là một thao tác nguyên tử; khi dùng `volatile`, nó còn cung cấp ràng buộc visibility/ordering cho việc công bố. Nếu reader đọc `current` một lần rồi đọc các field của `local`, các field đó thuộc **cùng phiên bản**. Nếu reader gọi `current.a()` rồi `current.b()` ở hai lần đọc reference khác nhau, writer có thể thay snapshot giữa hai lần và reader tự ghép hai phiên bản — lỗi ở cách đọc.

`volatile` **không tự tránh lost update khi có nhiều writer** làm read–modify–write theo kiểu “đọc current, tạo bản mới, gán current”. Với nhiều writer tranh chấp, nên dùng khóa hoặc CAS/`AtomicReference.updateAndGet` với logic cập nhật thuần.

### 9.2. Ví dụ 5 — snapshot bất biến cập nhật bằng CAS (Java 17+)

Tạo tệp `AtomicSnapshotDemo.java`:

~~~java
import java.util.concurrent.atomic.AtomicReference;

public class AtomicSnapshotDemo {
    record Inventory(int available, int reserved, long version) {
        Inventory {
            if (available < 0 || reserved < 0) {
                throw new IllegalArgumentException("Negative stock");
            }
        }

        int total() {
            return available + reserved;
        }
    }

    public static void main(String[] args) {
        AtomicReference<Inventory> state =
            new AtomicReference<>(new Inventory(10, 0, 0));

        Inventory before = state.get();

        state.updateAndGet(old ->
            new Inventory(
                old.available() - 3,
                old.reserved() + 3,
                old.version() + 1
            )
        );

        Inventory after = state.get();

        System.out.println(
            "before=" + before + ", total=" + before.total()
        );
        System.out.println(
            "after=" + after + ", total=" + after.total()
        );
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AtomicSnapshotDemo.java
java AtomicSnapshotDemo
~~~

Stdout:

~~~text
before=Inventory[available=10, reserved=0, version=0], total=10
after=Inventory[available=7, reserved=3, version=1], total=10
~~~

`Inventory` là record chỉ chứa ba giá trị primitive; sau khi tạo không có trường nội bộ mutable. `AtomicReference.get()` trả về một snapshot có thể chia sẻ, còn `updateAndGet` thử thay thế toàn bộ reference bằng snapshot mới dựa trên giá trị hiện tại. Một reader giữ `before` sẽ tiếp tục thấy phiên bản 0, còn reader lấy `after` thấy phiên bản 1. Tổng `available + reserved` nhất quán ở mỗi phiên bản.

**Giới hạn quan trọng:**

- Hàm truyền vào `updateAndGet` **có thể bị thực thi nhiều lần** nếu tranh chấp; chỉ đặt tính toán thuần bên trong, không gửi request/ghi file/tạo side effect cần chạy đúng một lần.
- Nếu một update có thể làm `available` âm, constructor trong ví dụ ném exception. Trong ứng dụng thực cần kiểm tra điều kiện nghiệp vụ và quyết định kết quả thất bại theo hợp đồng rõ ràng.
- CAS bảo vệ việc thay **cả một reference**, không biến side effect bên ngoài như database write thành giao dịch nguyên tử.
- Giữ bản snapshot cũ lâu sẽ giữ dữ liệu của phiên bản đó trong bộ nhớ đến khi không còn tham chiếu.
- Với trường hợp chỉ có một writer và reader nhiều, có thể dùng `volatile` để công bố snapshot hoàn chỉnh; CAS hữu ích khi nhiều writer phải phối hợp cập nhật trạng thái dựa trên phiên bản hiện tại.

### Luồng đọc và ghi

~~~text
Writer:
  đọc current v1
       |
  tạo state v2 bất biến
       |
  công bố v2 bằng volatile/CAS
       |
Reader A: giữ v1, đọc nhất quán v1
Reader B: lấy v2, đọc nhất quán v2
~~~

Không có yêu cầu writer phải chờ mọi reader hoàn tất trước khi tạo phiên bản mới. Đây là lý do kiểu thiết kế copy-on-write rất hữu ích khi **đọc nhiều, ghi ít**.

## 10. Bảo đảm thread safety khi sao chép

### 10.1. Copy khi nguồn đang bị sửa là một bài toán riêng

Một lỗi thường gặp là:

~~~java
// Minh họa không an toàn nếu source là ArrayList đang bị sửa đồng thời.
List<String> snapshot = List.copyOf(source);
~~~

Nếu một writer khác sửa `source` trong lúc copy, bạn chưa có một snapshot **nhất quán theo thời điểm**. Trên collection không hỗ trợ concurrent modification, quá trình duyệt/copy còn có thể gặp exception hoặc hành vi không phù hợp kỳ vọng.

Cần quy định rõ:

- **Nguồn được sở hữu độc quyền:** không thread nào khác sửa trong lúc copy.
- **Nguồn bảo vệ bằng lock:** writer và snapshot cùng dùng một monitor/lock.
- **Nguồn là immutable state:** reader chỉ chụp lại reference một lần, không phải duyệt collection mutable đang thay đổi.
- **Nguồn là concurrent collection:** phải đọc hợp đồng cụ thể của operation. “Concurrent” không mặc nhiên nghĩa một lần copy phản ánh một khoảnh khắc atomic của toàn bộ collection.

Ví dụ, iterator của `ConcurrentHashMap` thường có tính **weakly consistent**: có thể phản ánh một phần cập nhật đồng thời mà không ném `ConcurrentModificationException` theo kiểu fail-fast. Tạo một `Map.copyOf` từ concurrent map đang đổi không tự bảo đảm snapshot tuyến tính hóa của toàn bộ map nếu yêu cầu nhất quán nhiều entry.

### 10.2. CopyOnWriteArrayList là một lựa chọn chuyên biệt

`CopyOnWriteArrayList` tạo bản sao mảng nền khi có thao tác sửa cấu trúc và cung cấp iterator kiểu **snapshot** không bị tác động bởi cập nhật cấu trúc về sau. Nó hợp với dữ liệu **đọc/duyệt nhiều, ghi ít**, như danh sách listener.

Đổi lại, mỗi lần ghi có thể cần copy mảng nền; khi list lớn hoặc ghi liên tục, chi phí cấp phát, bộ nhớ và CPU có thể cao. Snapshot iterator chỉ bảo vệ nội dung **mảng tham chiếu** tại thời điểm iterator được tạo, không làm các object phần tử mutable trở nên bất biến.

`CopyOnWriteArrayList` là container concurrency, còn immutable snapshot là **một kiểu biểu diễn trạng thái**. Tùy bài toán, một `AtomicReference<List<ImmutableValue>>` được thay trọn gói có thể dễ kiểm soát phiên bản hơn.

### 10.3. Consistency và freshness là hai yêu cầu khác nhau

| Tính chất | Ý nghĩa |
| --- | --- |
| Consistent snapshot | Các trường thuộc cùng một phiên bản hợp lệ |
| Freshness | Snapshot có mới đến mức nào so với writer hiện tại |
| Atomic publication | Reader thấy reference của phiên bản cũ hoặc mới, không có trạng thái xây dựng dở |
| Thread-safe source | Cơ chế đọc/ghi của nguồn không vi phạm quy tắc đồng bộ |
| Deep immutability | Toàn bộ nội dung có thể quan sát không thể bị sửa thông qua alias khác |

Một snapshot cũ có thể hoàn toàn nhất quán nhưng không đủ mới cho yêu cầu nghiệp vụ. Ví dụ giao diện hiển thị cấu hình có thể chấp nhận chậm một phiên bản; kiểm tra tồn kho để đặt hàng có thể cần kiểm tra lại gần thời điểm ghi, phối hợp điều kiện atomically với việc cập nhật.

## 11. Chi phí bộ nhớ, hiệu năng và thiết kế

Defensive copy và immutable snapshot giúp giảm chia sẻ mutable, nhưng mỗi lần copy lớn cần thời gian, bộ nhớ và GC.

**Nên đánh giá:**

1. **Tần suất ghi:** Với nhiều writer, copy toàn bộ collection cho mỗi lần sửa có thể tốn kém. Cân nhắc khóa, phân vùng trạng thái hoặc cấu trúc dữ liệu phù hợp.
2. **Tần suất đọc:** Khi reader rất nhiều và trạng thái ít đổi, công bố immutable snapshot bằng một reference có thể tránh lock trên đường đọc.
3. **Kích thước snapshot:** Copy một list hàng triệu phần tử cho mỗi request là một chi phí lớn; có thể chuyển sang phân trang, snapshot theo phần, persistent data structure hoặc giới hạn scope.
4. **Thời gian giữ phiên bản cũ:** Nhiều reader giữ snapshot cũ làm tăng lượng dữ liệu sống trong heap. Theo dõi heap occupancy và GC pressure.
5. **Chất lượng tính bất biến:** Nếu phần tử vẫn mutable, mọi tối ưu chia sẻ snapshot có thể mất ý nghĩa.
6. **Thời điểm copy:** Copy khi nhận dữ liệu, trước khi công bố hay mỗi lần getter? Trả một immutable value đã được công bố an toàn thường rẻ hơn copy lặp lại.
7. **Yêu cầu phiên bản:** Snapshot có cần `version` hoặc timestamp để người dùng nhận biết độ mới và tránh ghi đè một update của phiên bản khác không?

Không có quy tắc “copy mọi object mọi lúc”. Phương án tốt phụ thuộc vào ownership, mức độ đồng thời, yêu cầu nhất quán và phép đo trên workload thực tế.

## 12. Lỗi phổ biến và cách sửa

| Sai lầm | Vì sao sai | Hướng sửa |
| --- | --- | --- |
| `this.items = input` rồi tin rằng caller không sửa | Caller giữ alias | Copy vào hoặc chuyển ownership rõ ràng |
| Getter trả `ArrayList` nội bộ | Caller sửa thẳng state | Trả immutable snapshot hoặc defensive copy |
| `Collections.unmodifiableList(mutableList)` và gọi đó là snapshot | View phản ánh sửa đổi từ backing list | `List.copyOf` tại thời điểm được đồng bộ thích hợp |
| `List.copyOf(List<MutableItem>)` và gọi đó là deep immutable | Phần tử vẫn chia sẻ reference mutable | Copy/project từng phần tử thành immutable value |
| Record có `byte[]` rồi cho rằng record hoàn toàn immutable | Field final nhưng mảng sửa được | Copy mảng trong constructor và accessor |
| Copy collection trong lúc writer sửa không khóa | Snapshot có thể không nhất quán hoặc lỗi | Copy dưới cùng lock hoặc thiết kế immutable source |
| Gán hai field volatile và gọi đó là snapshot nguyên tử | Reader có thể ghép hai phiên bản | Gộp vào một immutable state object và công bố một reference |
| Writer đọc volatile state rồi gán state mới không CAS | Nhiều writer có thể mất update | Dùng lock hoặc `AtomicReference.compareAndSet/updateAndGet` |
| Dùng `AtomicReference` nhưng update function có side effect | CAS retry làm side effect lặp | Hàm biến đổi thuần, side effect sau khi cập nhật thành công |
| Dùng `CopyOnWriteArrayList` cho workload ghi liên tục | Mỗi ghi có chi phí copy cấu trúc | Đo tải, chọn collection/lock khác phù hợp |
| Tin `final` làm object graph immutable | `final` chỉ khóa việc gán tham chiếu | Thiết kế bất biến đệ quy ở dữ liệu quan sát được |
| Dựa vào `Thread.sleep` để chờ snapshot “ổn định” | Sleep không tạo HB/atomicity | Dùng lock, latch, future, volatile/CAS đúng giao thức |

## 13. Checklist review và tự kiểm tra

Khi đánh giá một lớp chia sẻ giữa nhiều thread:

- **Ownership:** Caller nào còn giữ tham chiếu tới object đầu vào? Có thể sửa object đó sau constructor không?
- **Copy in:** Constructor/setter có chụp dữ liệu độc lập trước khi lưu không? Nếu dữ liệu lồng nhau mutable, copy có đủ sâu không?
- **Copy out:** Getter có vô tình trả mutable object nội bộ không? Nếu trả immutable snapshot, các phần tử đã thực sự immutable chưa?
- **Consistency:** Nếu nhiều field phải tuân theo một invariant, snapshot có được tạo dưới cùng lock hoặc từ một immutable state duy nhất không?
- **Publication:** Writer và reader có giao thức happens-before rõ ràng qua `volatile`, CAS, lock, queue, start/join hoặc primitive khác không?
- **Concurrency:** Có writer nào sửa nguồn trong lúc copy mà không phối hợp không?
- **Performance:** Tần suất copy, kích thước snapshot và thời gian giữ phiên bản cũ có phù hợp không?
- **Tests:** Có kiểm tra thay đổi nguồn sau copy, thay đổi phần tử mutable, writer/readers chạy đồng thời và invariant của snapshot không?

### Câu hỏi 1

`List<String> view = Collections.unmodifiableList(source)`. Một thread khác sửa `source`. `view` có còn giữ đúng nội dung lúc tạo không?

**Đáp án:** Không được bảo đảm. `view` là unmodifiable view, phản ánh backing source. Nếu việc sửa và đọc diễn ra đồng thời không đúng giao thức, còn có thể vi phạm thread safety.

### Câu hỏi 2

`List<MutableOrder> snapshot = List.copyOf(orders)`. Có thể khẳng định tất cả `MutableOrder` bên trong không thể thay đổi nữa không?

**Đáp án:** Không. `copyOf` không clone/deep-copy các order. Cần chuyển chúng thành immutable projection hoặc defensive copy từng phần tử theo ownership phù hợp.

### Câu hỏi 3

Reader có thể đọc `current.left()` và ngay sau đó `current.right()`, trong khi `current` là reference volatile trỏ tới state bất biến và writer thay nó được không?

**Đáp án:** Hai lần truy cập `current` riêng biệt có thể lấy **hai phiên bản**. Để được snapshot nhất quán, gán `State local = current` một lần rồi đọc cả `local.left()` và `local.right()`.

### Câu hỏi 4

Vì sao `AtomicReference.updateAndGet` không nên nhận một lambda vừa tạo state mới vừa ghi log nghiệp vụ tính tiền?

**Đáp án:** CAS có thể retry khi tranh chấp, khiến lambda được gọi nhiều lần cho một update thành công. Hàm biến đổi state phải thuần; side effect phải có cơ chế đảm bảo riêng.

### Câu hỏi 5

Nếu một thread giữ một immutable snapshot cũ trong khi writer công bố snapshot mới, có phải thread đó bị data race không?

**Đáp án:** Không, nếu snapshot thực sự bất biến và được công bố an toàn. Reader đang sử dụng phiên bản cũ nhất quán. Yêu cầu đọc bản mới đến đâu là **freshness** nghiệp vụ, cần được xác định riêng.

**Ghi nhớ:** Defensive copy bảo vệ **ranh giới sở hữu dữ liệu**; immutable snapshot bảo vệ **tính nhất quán của phiên bản được đọc**; safe publication bảo đảm **thread khác nhìn thấy phiên bản đã xây dựng hợp lệ**. Muốn đạt thread safety cần kết hợp cả ba khi bài toán yêu cầu, đồng thời đánh giá chi phí và độ mới của snapshot.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-008">Giữ nhất quán invariant khi nhiều trường liên quan</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ nhất quán invariant khi nhiều trường liên quan trong Java Concurrency

**Invariant** (bất biến trạng thái) là điều kiện phải luôn đúng ở những thời điểm trạng thái được phép quan sát hoặc sử dụng. Trong chương trình đa luồng, invariant thường liên quan **nhiều trường cùng lúc**: tổng tiền trong hai ví bằng 100, số chỗ còn trống cộng số chỗ đã đặt bằng sức chứa ban đầu, khoảng `start <= end`, hoặc một đơn hàng có trạng thái `PAID` thì phải có mã thanh toán tương ứng.

Nếu hai trường được cập nhật riêng rẽ, thread khác có thể nhìn thấy một **trạng thái trung gian không hợp lệ**, ngay cả khi từng phép đọc/ghi riêng lẻ là atomic hoặc các trường đều `volatile`. Điều kiện đúng ở đầu và cuối phương thức chưa đủ nếu thread khác được phép quan sát giữa chừng. Cần xác định **ranh giới của một chuyển trạng thái**, bảo vệ mọi dữ liệu tham gia invariant, và cung cấp cách đọc một snapshot nhất quán.

Bài học thuộc **Thread Safety của Java Concurrency nói chung**; tập trung vào đồng bộ trong bộ nhớ JVM, Java Memory Model, khóa, immutable state và atomic reference. Các tích hợp riêng với Spring không thuộc phạm vi.

## 1. Invariant khác điều kiện riêng lẻ của từng trường

Giả sử một kho phiếu có hai số đếm:

- `available`: số phiếu chưa giữ chỗ;
- `reserved`: số phiếu đã giữ chỗ.

Ban đầu `available = 100`, `reserved = 0`. Yêu cầu nghiệp vụ là:

~~~text
available >= 0
reserved >= 0
available + reserved == 100
~~~

Mỗi lần đặt chỗ thành công phải chuyển một phiếu từ `available` sang `reserved`. Trước và sau phép chuyển, tổng vẫn bằng 100:

~~~text
Trước:  available=100, reserved=0, tổng=100
Sau:    available=99,  reserved=1, tổng=100
~~~

Nhưng mã sau **không thực hiện một chuyển trạng thái nguyên tử**:

~~~java
if (available > 0) {
    available--;
    reserved++;
}
~~~

Có ít nhất ba rủi ro khác nhau:

1. **Check-then-act race:** hai thread cùng thấy `available > 0` rồi đều trừ; kết quả có thể âm hoặc số đặt vượt sức chứa.
2. **Intermediate state:** thread đọc có thể quan sát sau `available--` nhưng trước `reserved++`, thấy tổng 99 dù giá trị hợp lệ ở đầu/cuối đều là 100.
3. **Visibility/ordering:** nếu truy cập biến thường mà thiếu quan hệ happens-before, thread đọc còn có thể quan sát các cập nhật theo cách không đáp ứng hợp đồng mong muốn.

Cách sửa phải bao trùm **cả điều kiện, tất cả trường được thay đổi và cách quan sát các trường**. Chỉ bọc một phép tăng, chỉ thêm `volatile`, hoặc chỉ đồng bộ phần writer đều chưa đủ.

### 1.1 Invariant và giai đoạn được phép quan sát

Trong một critical section, mã có thể tạm thời đi qua trạng thái trung gian:

~~~java
available--;
reserved++;
~~~

Nếu **mọi thread khác chỉ có thể đọc cả hai trường khi giữ cùng lock**, không thread nào bên ngoài có thể nhìn thấy trạng thái trung gian đó; invariant được duy trì tại ranh giới public của operation. Nhưng nếu một getter đọc `available` hoặc `reserved` mà **không lấy lock**, nó có thể làm lộ intermediate state.

Điều này dẫn đến nguyên tắc:

> Một invariant liên quan nhiều trường cần được giữ đúng tại mọi điểm quan sát hợp lệ. Mỗi thao tác đọc/ghi có khả năng ảnh hưởng invariant phải tuân thủ **cùng giao thức đồng bộ**.

Không có nghĩa phải dùng một mutex toàn ứng dụng. Ta chỉ cần một **ranh giới sở hữu và đồng bộ chung cho cùng một invariant**.

## 2. Ví dụ xác định: hai volatile fields vẫn lộ trạng thái không nhất quán

`volatile` bảo đảm hiệu ứng visibility/ordering cho access đến trường volatile. Nó **không nhóm** hai trường thành một giao dịch nguyên tử.

Để tái hiện chính xác thời điểm trạng thái bị lệch mà không dựa vào may rủi scheduler, dùng hai `CountDownLatch` để tạm dừng writer sau lần trừ đầu tiên.

Tạo tệp `VolatilePairInvariantDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;

public final class VolatilePairInvariantDemo {
    private static final class Counters {
        volatile int available = 100;
        volatile int reserved = 0;
    }

    public static void main(String[] args) throws InterruptedException {
        Counters counters = new Counters();
        CountDownLatch debited = new CountDownLatch(1);
        CountDownLatch continueWriter = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            counters.available--;
            debited.countDown();

            try {
                continueWriter.await();
                counters.reserved++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "writer");

        writer.start();
        debited.await();

        System.out.println("During update: "
                + (counters.available + counters.reserved));

        continueWriter.countDown();
        writer.join();

        System.out.println("After update: "
                + (counters.available + counters.reserved));
    }
}
~~~

Biên dịch và chạy trên JDK 17 trở lên:

~~~bash
javac VolatilePairInvariantDemo.java
java VolatilePairInvariantDemo
~~~

Trong lần chạy bình thường, không có external interruption:

~~~text
During update: 99
After update: 100
~~~

**Vì sao ví dụ xác định?** Writer trừ `available` rồi gọi `debited.countDown()`; main đợi `debited.await()` thành công rồi đọc hai trường, trong khi writer chưa thể tăng `reserved` vì đang đợi `continueWriter`. Main sau đó mở cổng và join writer. Latch chỉ tạo lịch xen kẽ xác định cho demo. **Lỗi thiết kế là hai lần cập nhật nằm trong hai bước mà reader có thể quan sát ở giữa.**

Ở đây mỗi field là volatile, nhưng tại điểm quan sát giữa hai bước, dữ liệu thật sự có trạng thái `available=99, reserved=0`. Đây không phải vấn đề “CPU chưa flush cache”; đây là **thiếu atomicity ở cấp nhóm trường**. Nếu chỉ sửa visibility mà không sửa ranh giới operation, invariant vẫn có thể bị phá.

**Giới hạn của demo:** khi có interruption ngoài làm writer thoát sớm, kết quả sau cùng có thể khác. Đoạn này nhằm giải thích atomicity, chưa phải giải pháp production. Một operation thật phải định nghĩa chính sách hủy, lỗi và rollback để không để trạng thái dở dang.

## 3. Xác định nhóm trường nào phải thay đổi cùng nhau

Trước khi chọn lock, hãy viết rõ các điều kiện bất biến của object hoặc subsystem:

| Ví dụ | Các trường liên quan | Điều kiện phải đúng |
| --- | --- | --- |
| Sức chứa | `available`, `reserved` | Cả hai không âm; tổng bằng capacity. |
| Tài khoản nội bộ | `checking`, `savings` | Tổng nội bộ bảo toàn khi chuyển tiền giữa ví; không âm. |
| Khoảng thời gian | `start`, `end` | `start <= end` trong mỗi snapshot được công bố. |
| Trạng thái thanh toán | `status`, `paymentId` | Khi `status == PAID`, ID thanh toán tồn tại. |
| Bộ đếm thống kê | `success`, `failure`, `processed` | Nếu số processed được cập nhật cùng một giao dịch logic thì `success + failure == processed` tại điểm quan sát. |
| Cấu hình | `host`, `port`, `version` | Mọi lần đọc lấy các giá trị thuộc **cùng một phiên bản**. |

Những ví dụ là **mô hình nghiệp vụ minh họa**: trong hệ thống thật, invariant có thể khác (chẳng hạn sự kiện hủy đặt chỗ làm thay đổi capacity, hoặc một giao dịch thanh toán đang ở trạng thái trung gian `PENDING` hợp lệ). Hãy xác định **trạng thái nào được phép tồn tại**, khi nào được phép publish, và trạng thái trung gian nào bị cấm quan sát.

Một quy trình tốt:

1. Liệt kê tất cả trường và object tham gia invariant.
2. Tìm **mọi điểm ghi**, kể cả constructor, setter, callback, thread nền, scheduler và hàm reset.
3. Tìm **mọi điểm đọc**: getter, report, logging, serializer, thuật toán khác.
4. Quy định một operation có hiệu lực cùng lúc ở đâu (điểm tuyến tính hóa nếu API cần linearizability).
5. Buộc mọi đường truy cập liên quan tuân thủ **một giao thức thống nhất**.
6. Kiểm tra lỗi, exception, interruption và các branch sớm vẫn bảo toàn invariant.

Không nên chỉ sửa phương thức writer được nhìn thấy đầu tiên rồi bỏ qua getter hoặc những đường đọc khác.

## 4. Giải pháp cơ bản: một lock bảo vệ toàn bộ invariant

`synchronized` là lựa chọn trực tiếp và dễ chứng minh khi nhiều trường của cùng một object phải cập nhật cùng nhau. Một lock riêng thuộc object bảo vệ **toàn bộ** state; caller chỉ sử dụng các method đã đồng bộ.

Tạo tệp `LockedInvariantDemo.java`:

~~~java
public final class LockedInvariantDemo {
    private static final class ReservationLedger {
        private final Object lock = new Object();
        private int available = 100;
        private int reserved;

        boolean reserveOne() {
            synchronized (lock) {
                if (available == 0) {
                    return false;
                }
                available--;
                reserved++;
                return true;
            }
        }

        Snapshot snapshot() {
            synchronized (lock) {
                return new Snapshot(available, reserved);
            }
        }
    }

    private record Snapshot(int available, int reserved) {
        int total() {
            return available + reserved;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ReservationLedger ledger = new ReservationLedger();

        Runnable reserve = () -> {
            for (int i = 0; i < 100; i++) {
                ledger.reserveOne();
            }
        };

        Thread first = new Thread(reserve, "first");
        Thread second = new Thread(reserve, "second");
        first.start();
        second.start();
        first.join();
        second.join();

        Snapshot result = ledger.snapshot();
        System.out.println("Available: " + result.available());
        System.out.println("Reserved: " + result.reserved());
        System.out.println("Total: " + result.total());
    }
}
~~~

Lệnh chạy Java 17+:

~~~bash
javac LockedInvariantDemo.java
java LockedInvariantDemo
~~~

Stdout:

~~~text
Available: 0
Reserved: 100
Total: 100
~~~

### 4.1 Vì sao bảo toàn invariant?

- Mỗi lời gọi `reserveOne()` giữ cùng monitor `lock` trong suốt **check + decrement + increment**. Không có hai lời gọi `reserveOne` nào chen vào giữa các bước của nhau.
- Khi không còn phiếu, method trả `false` mà không sửa state. Điều kiện `available >= 0` được giữ.
- Một lần reserve thành công biến trạng thái từ `(a, r)` sang `(a-1, r+1)`. Tổng không đổi: `(a-1)+(r+1)=a+r`.
- `snapshot()` lấy **chính lock đó**, tạo một cặp giá trị đồng thời được đọc ở ranh giới hợp lệ. Reader không thấy trạng thái sau trừ nhưng trước cộng.
- Hai worker mỗi worker thử đặt tối đa 100 lần; tổng thể có đúng 100 lượt thành công, bất kể thread nào được scheduler chọn trước. Sau `join()`, main thấy các cập nhật đã hoàn thành.
- `Snapshot` là một `record` bất biến gồm primitive components, nên có thể đọc bên ngoài lock sau khi được trả về mà không thấy hai trường bị thay đổi riêng rẽ.

### 4.2 Điểm tuyến tính hóa và cách đọc atomic

Với API `reserveOne()`, toàn bộ chuyển trạng thái được bảo vệ bởi lock. Có thể coi operation có hiệu lực tại một điểm bên trong critical section trước khi nhả lock. Cách quan sát kết quả qua `snapshot()` luôn tương ứng một trạng thái trước hoặc sau một operation hoàn chỉnh.

**Sai lầm phổ biến** là đồng bộ writer nhưng không đồng bộ reader:

~~~java
synchronized (lock) {
    available--;
    reserved++;
}

// Một getter ngoài lock ở thread khác:
int total = available + reserved; // Không có snapshot/đồng bộ đúng.
~~~

Đây là **mảnh code lỗi minh họa**. Dùng một getter `getAvailable()` có lock và một getter `getReserved()` cũng có lock **riêng từng lần** chưa đủ để ghép thành một snapshot: giữa hai lần gọi, writer vẫn có thể thực hiện operation mới. Cần một method `snapshot()` đọc **tất cả trường liên quan dưới cùng một lần lock**.

## 5. Encapsulation: sở hữu state quan trọng hơn từ khóa synchronized

Thread safety bị phá nếu caller có thể sửa một trong các trường mà không dùng cùng protocol.

Nên giữ state ở mức `private`, tránh trả trực tiếp mutable object tham gia invariant, và cung cấp operation ở cấp nghiệp vụ:

~~~java
final class Range {
    private final Object lock = new Object();
    private int start;
    private int end;

    Range(int start, int end) {
        if (start > end) {
            throw new IllegalArgumentException("start > end");
        }
        this.start = start;
        this.end = end;
    }

    void move(int newStart, int newEnd) {
        if (newStart > newEnd) {
            throw new IllegalArgumentException("start > end");
        }
        synchronized (lock) {
            start = newStart;
            end = newEnd;
        }
    }

    Snapshot snapshot() {
        synchronized (lock) {
            return new Snapshot(start, end);
        }
    }

    record Snapshot(int start, int end) {}
}
~~~

Đây là **mảnh class minh họa**, không có `main`. Mặc dù bên trong `move` có khoảnh khắc `start` đổi trước `end`, code bên ngoài chỉ có thể quan sát qua `snapshot` dưới cùng lock. Việc kiểm tra `newStart <= newEnd` có thể đặt trước lock vì phụ thuộc **chỉ vào hai tham số immutable theo giá trị**, không phụ thuộc state đang thay đổi.

Ngược lại, điều kiện kiểu “nếu `start` hiện tại bằng X thì đổi `end`” phải được kiểm tra **bên trong cùng critical section** với phép cập nhật; kiểm tra trạng thái chia sẻ ngoài lock sẽ tạo *check-then-act race*.

### 5.1 Trả snapshot thay vì tham chiếu mutable

Nếu object quản lý `List<Order>` hoặc `Map<String, Item>`, một getter trả thẳng collection mutable có thể cho caller sửa trạng thái không qua lock. Chỉ bọc `synchronized` quanh getter không ngăn được sửa đổi sau khi reference được trả ra.

Đưa ra bản snapshot copy có chủ đích:

~~~java
List<String> snapshot() {
    synchronized (lock) {
        return List.copyOf(items);
    }
}
~~~

Mảnh minh họa giả định `items`, `lock` đã khai báo và items không chứa null (vì `List.copyOf` không chấp nhận phần tử null). Danh sách trả về không cho caller sửa cấu trúc danh sách, nhưng **nếu phần tử bên trong mutable**, copy này chỉ là copy *nông*. Muốn công bố snapshot bất biến sâu, cần phần tử immutable hoặc sao chép dữ liệu thành value objects phù hợp.

Không để `this` hoặc reference đến state còn đang xây dựng thoát khỏi constructor, vì thread khác có thể quan sát object khi invariant khởi tạo chưa hoàn tất.

## 6. ReentrantLock khi cần nhiều điều khiển hơn

`java.util.concurrent.locks.ReentrantLock` có các bảo đảm mutual exclusion và memory effect tương tự monitor khi lock/unlock đúng cách, đồng thời hỗ trợ timed acquisition, interruptible lock và điều kiện `Condition`.

Ví dụ mảnh class dùng `tryLock`, deadline chờ và `finally`:

~~~java
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

final class TimedLedger {
    private final ReentrantLock lock = new ReentrantLock();
    private int available = 100;
    private int reserved;

    boolean reserveOne() throws InterruptedException {
        if (!lock.tryLock(200, TimeUnit.MILLISECONDS)) {
            return false; // Không lấy được lock trong khoảng chờ.
        }
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
}
~~~

Đây là **mảnh code minh họa không có main**, nhưng có thể biên dịch cùng import tương ứng. Cần phân biệt `false` vì hết hạn lấy lock với `false` vì hết quota nếu hợp đồng nghiệp vụ cần nêu rõ nguyên nhân.

Những nguyên tắc bắt buộc:

- Luôn nhả lock trong `finally` ngay sau khi đã acquire thành công.
- Mọi đường đọc state cũng phải dùng **cùng ReentrantLock**; một getter không lock có thể làm lộ trạng thái trung gian.
- `tryLock(timeout)` giới hạn thời gian **chờ lấy lock**, không giới hạn thời gian thực hiện critical section.
- Nếu `InterruptedException` xuất hiện trước khi lấy lock, không được gọi `unlock()` khi thread chưa sở hữu lock.
- Fair lock `new ReentrantLock(true)` có thể thay đổi thứ tự phục vụ khi tranh chấp, nhưng **không** bảo đảm thời hạn hoàn thành; fairness không sửa invariant sai.

Không nên chọn `ReentrantLock` chỉ vì tin rằng nó “thread-safe hơn synchronized”. Đầu tiên hãy thiết kế đúng ownership và invariant; chọn primitive dựa trên nhu cầu timeout, interruption, điều kiện đợi hoặc đo lường tải.

## 7. Immutable state và AtomicReference: cập nhật cả nhóm như một giá trị

Thay vì lưu hai biến mutable độc lập, có thể gom chúng vào một **value object bất biến**, rồi lưu duy nhất **reference** đến state hiện tại. Mọi lần cập nhật tạo ra một snapshot mới, sau đó thay reference bằng CAS.

Tạo `AtomicInvariantDemo.java`:

~~~java
import java.util.concurrent.atomic.AtomicReference;

public final class AtomicInvariantDemo {
    private record State(int available, int reserved) {
        int total() {
            return available + reserved;
        }
    }

    private final AtomicReference<State> state =
            new AtomicReference<>(new State(100, 0));

    boolean reserveOne() {
        while (true) {
            State current = state.get();
            if (current.available() == 0) {
                return false;
            }

            State next = new State(
                    current.available() - 1,
                    current.reserved() + 1
            );
            if (state.compareAndSet(current, next)) {
                return true;
            }
            // Có contender thay state; đọc lại snapshot rồi thử CAS.
        }
    }

    State snapshot() {
        return state.get();
    }

    public static void main(String[] args) throws InterruptedException {
        AtomicInvariantDemo ledger = new AtomicInvariantDemo();

        Runnable reserve = () -> {
            for (int i = 0; i < 100; i++) {
                ledger.reserveOne();
            }
        };

        Thread first = new Thread(reserve, "first");
        Thread second = new Thread(reserve, "second");
        first.start();
        second.start();
        first.join();
        second.join();

        State result = ledger.snapshot();
        System.out.println("Available: " + result.available());
        System.out.println("Reserved: " + result.reserved());
        System.out.println("Total: " + result.total());
    }
}
~~~

Lệnh chạy Java 17+:

~~~bash
javac AtomicInvariantDemo.java
java AtomicInvariantDemo
~~~

Stdout:

~~~text
Available: 0
Reserved: 100
Total: 100
~~~

### 7.1 Vì sao một AtomicReference giải quyết được nhiều trường?

- `State` là `record` bất biến chứa **cả hai** số đếm. Một reader gọi `state.get()` một lần sẽ giữ reference đến **một snapshot đầy đủ**.
- Writer tạo `next` dựa trên một snapshot hiện tại, giữ invariant trong chính snapshot mới.
- `compareAndSet(current, next)` chỉ thành công nếu reference vẫn là `current`. Một thread thành công là **điểm tuyến tính hóa** của operation thay state.
- Nếu contender đã thay state trước, CAS thất bại; writer bỏ candidate cũ, đọc lại và tính trạng thái mới. Vì thế không ghi đè một cập nhật hợp lệ của thread khác.
- Reader không thấy bước `available--` tách rời bước `reserved++`: các trường của state mới đã được tạo xong **trước khi** reference được công bố.
- Với giả định các giá trị nằm trong miền hợp lệ và không có writer ngoài protocol, invariant `available + reserved == 100` được giữ trong mọi `State` đã publish.

### 7.2 Giới hạn và trade-off của CAS

Một CAS loop không bảo đảm một thread cụ thể sẽ thành công trong số lần thử hữu hạn dưới tranh chấp lớn; có thể xảy ra **starvation** dù toàn hệ thống vẫn tiến triển. Không thực hiện I/O, gửi email, trừ tiền ngoài JVM hoặc side effect không thể lặp an toàn bên trong hàm tính `next`: CAS có thể thất bại và logic phải chạy lại.

Mỗi lần thành công tạo một `State` mới, gây allocation và áp lực GC. Với state nhỏ và read-heavy, immutable snapshot có thể rất phù hợp; với cập nhật cực nhiều hoặc state phức tạp, cần đo performance so với lock.

Không gọi hai lần `state.get()` rồi ghép một trường từ snapshot cũ với một trường từ snapshot mới:

~~~java
int a = state.get().available();
int b = state.get().reserved(); // Có thể thuộc phiên bản khác.
~~~

Đây là mảnh mã lỗi minh họa. Đúng là:

~~~java
State oneSnapshot = state.get();
int a = oneSnapshot.available();
int b = oneSnapshot.reserved();
~~~

Hai phép đọc từ cùng `oneSnapshot` giữ consistency của phiên bản đó. Với state chứa reference đến collection mutable, record không tự tạo bất biến sâu; cần đảm bảo state và object graph bên trong thực sự immutable theo hợp đồng.

### 7.3 ABA và thay đổi theo phiên bản

`compareAndSet` của `AtomicReference` so sánh reference hiện tại với expected reference. Khi tái dùng mutable object/reference hoặc state chuyển A→B→A, thuật toán phức tạp có thể gặp **ABA problem**: giá trị trông giống ban đầu nhưng lịch sử đã thay đổi. Ví dụ phía trên tạo mỗi `State` mới và không tái sử dụng các instance cũ, nên CAS của các thao tác này phân biệt những lần cập nhật theo identity một cách phù hợp.

Nếu nghiệp vụ yêu cầu biết **version** hoặc phát hiện bất kỳ thay đổi trung gian, có thể lưu version trong state mới và tăng cùng operation, hoặc dùng `AtomicStampedReference` khi đúng nhu cầu. Không thêm version chỉ vì “có concurrency”; đầu tiên xác định yêu cầu thông tin lịch sử.

## 8. Tại sao nhiều AtomicInteger độc lập chưa đủ?

Sai lầm thường gặp:

~~~java
AtomicInteger available = new AtomicInteger(100);
AtomicInteger reserved = new AtomicInteger();

if (available.get() > 0) {
    available.decrementAndGet();
    reserved.incrementAndGet();
}
~~~

Mỗi phép `get()`, `decrementAndGet()` và `incrementAndGet()` có atomicity theo **một biến**. Nhưng toàn bộ đoạn vẫn tồn tại:

- **Check-then-act race:** hai thread đều thấy `available > 0` và cùng giảm.
- **Snapshot torn:** reader có thể quan sát trạng thái sau khi available giảm nhưng trước khi reserved tăng.
- **Inconsistency khi lỗi:** nếu có exception hoặc hủy giữa hai bước, biến thứ hai có thể không được cập nhật.

Ngay cả sửa check-then-act bằng `available.updateAndGet(...)` hoặc vòng CAS riêng cho `available` cũng không khiến phép tăng `reserved` nằm trong cùng transaction. Muốn bảo vệ **invariant liên quan cả hai biến**, hãy dùng một critical section chung hoặc gói cả hai vào một immutable state dưới `AtomicReference<State>`.

Các bộ đếm **độc lập theo thiết kế** là trường hợp khác: ví dụ số request thành công và số request thất bại được dùng cho metrics cuối kỳ, và hệ thống chấp nhận snapshot tạm thời lệch khi cập nhật. Khi không có invariant phải đúng ở mọi điểm đọc, nhiều atomic counters có thể hợp lý. Điều này cần được nêu rõ trong hợp đồng quan sát, không thể mặc định.

## 9. Chọn synchronized, Lock, immutable snapshot hay atomic CAS

| Kỹ thuật | Điểm mạnh | Chi phí/hạn chế | Phù hợp khi |
| --- | --- | --- | --- |
| `synchronized` với private lock | Dễ hiểu, dễ chứng minh atomic chuyển trạng thái nhiều trường; memory visibility rõ. | Thread có thể block khi contention; cần tuân thủ lock ở mọi đường đọc/ghi. | State nhỏ/vừa, invariant phức tạp, cần correctness rõ ràng. |
| `ReentrantLock` | Timeout, interruptible acquisition, Condition, có thể đo contention. | Phải unlock chính xác trong finally; tăng số nhánh lỗi/lifecycle. | Cần policy lấy lock/timeout phức tạp. |
| Immutable snapshot + `volatile` reference | Reader lấy snapshot nhất quán mà không block trên lock đọc. | Nếu có nhiều writer thì phép thay reference đơn giản có thể mất cập nhật. | Một writer publish cấu hình/snapshot hoàn chỉnh, nhiều reader. |
| Immutable snapshot + `AtomicReference` CAS | Cập nhật **cả nhóm state** atomically bằng thay reference; reader đọc một snapshot. | Retry khi contention, allocation, phức tạp nếu có side effects. | State gọn, bất biến, optimistic update. |
| `ReadWriteLock` | Cho nhiều reader cùng đọc và writer cập nhật độc quyền theo policy. | Dễ có contention, writer starvation tùy policy, overhead cao hơn lock thường. | Đọc nhiều, write ít, vùng đọc đủ lớn để có lợi. |
| `StampedLock` | Có cơ chế optimistic read + validate phù hợp một số workload. | Protocol khó hơn, không reentrant; phải validate đúng, không dùng kết quả thiếu nhất quán trước validate. | Workload đo đạc chứng minh lợi ích và team hiểu protocol. |

`volatile` trên từng field không thay thế atomic snapshot. Với `StampedLock`, optimistic read có thể thấy trạng thái trung gian trong lúc writer thay đổi; chỉ dùng kết quả sau validation thành công, và không dereference/đưa ra side effect dựa trên dữ liệu có thể thiếu nhất quán trước validate. Đừng dùng optimistic read để thao tác trên object graph mutable nếu không hiểu rủi ro của việc quan sát dữ liệu đang thay đổi.

**Lựa chọn mặc định:** ưu tiên một private lock và API nghiệp vụ. Khi profiler cho thấy bottleneck cụ thể, cân nhắc immutable snapshot/atomic reference hoặc primitive khác theo mô hình tải. Một thiết kế dễ chứng minh đúng thường rẻ hơn chi phí sửa lỗi race khó tái hiện.

## 10. Bất biến liên quan nhiều object: lock ordering và transaction boundary

Khi invariant trải qua **hai object độc lập**, đồng bộ trong từng object riêng lẻ không bảo đảm một operation đi qua cả hai là atomic. Ví dụ chuyển tiền giữa account A và B phải:

1. Kiểm tra số dư A có đủ.
2. Trừ A.
3. Cộng B.
4. Cho reader quan sát một trạng thái nhất quán.

Nếu chỉ `synchronized` riêng trên `withdraw` của A và `deposit` của B, thread khác có thể đọc cặp tài khoản giữa hai bước hoặc có thể có update competing. Muốn atomic đối với cả hai, cần một **protocol sở hữu cả hai**.

### 10.1 Cùng một ledger lock

Nếu cùng một ledger quản lý cả A và B, giải pháp đơn giản là một lock ledger:

~~~java
final class TransferLedger {
    private final Object lock = new Object();
    private int accountA = 100;
    private int accountB = 100;

    boolean transferAToB(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        synchronized (lock) {
            if (accountA < amount) {
                return false;
            }
            accountA -= amount;
            accountB += amount;
            return true;
        }
    }

    int total() {
        synchronized (lock) {
            return accountA + accountB;
        }
    }
}
~~~

Đây là **mảnh class minh họa**, không có main. Nếu trạng thái ban đầu tổng 200, mọi lần chuyển hợp lệ giữ tổng 200; reader gọi `total()` theo cùng lock. Mỗi phép chuyển có hiệu lực như một operation đối với state nội bộ ledger.

### 10.2 Nếu phải lấy nhiều lock, cần thứ tự toàn cục

Giả sử thread T1 khóa account A rồi B, còn T2 khóa B rồi A: có thể **deadlock** nếu mỗi thread giữ một lock và chờ lock còn lại. Một quy tắc phổ biến là cấp **ID bất biến và duy nhất** cho account rồi luôn lấy lock theo thứ tự ID tăng, bất kể hướng chuyển tiền.

~~~java
Account first = from.id < to.id ? from : to;
Account second = from.id < to.id ? to : from;

synchronized (first.lock) {
    synchronized (second.lock) {
        // Kiểm tra from.balance và thực hiện cả hai cập nhật.
    }
}
~~~

Đây là **mảnh minh họa**; `Account` có `id` duy nhất, bất biến, `lock` và `balance`, và đoạn này chưa xử lý trường hợp `from == to`, validation hay mã runnable. Để an toàn, mọi operation có thể cùng lấy hai lock phải dùng **cùng thứ tự**, và tất cả đường đọc snapshot hai account cũng tuân thủ protocol. Nếu ID có thể trùng, cần tie-breaker nhất quán; không dựa vào thứ tự tham số truyền vào.

Không nên khóa object công khai có thể bị code khác `synchronized` tùy ý; private lock giúp tránh can thiệp ngoài mong muốn. Tránh thực hiện I/O hoặc gọi callback không kiểm soát được trong khi giữ nhiều lock.

### 10.3 Một JVM không thay thế giao dịch qua nhiều hệ thống

`synchronized`, `ReentrantLock` và `AtomicReference` chỉ phối hợp trạng thái trong **một JVM/object graph được chúng quản lý**. Nếu invariant gồm hàng trong database, message broker, file, nhiều process hoặc dịch vụ mạng, lock JVM **không** biến toàn bộ thao tác thành distributed transaction.

Các invariant liên hệ nhiều hệ thống cần cơ chế phù hợp cấp đó: transaction database, optimistic concurrency/versioning, idempotency, quy trình compensating action hoặc thiết kế eventual consistency có trạng thái trung gian được định nghĩa rõ. Đây là **giới hạn phạm vi** của kỹ thuật thread safety, không phải lý do đưa framework cụ thể vào bài này.

## 11. Exception safety: không publish trạng thái dở dang

Khóa giúp ngăn thread khác xen vào giữa các phép cập nhật, nhưng **không tự rollback** nếu mã bên trong critical section ném exception giữa chừng.

Ví dụ nguy hiểm:

~~~java
synchronized (lock) {
    available--;
    mayThrow();     // Nếu lỗi ở đây...
    reserved++;     // ... không bao giờ được thực thi.
}
~~~

Mảnh minh họa giả định biến và method đã khai báo. `synchronized` vẫn **nhả monitor** khi exception thoát, khiến thread khác có thể đọc một state đã lệch invariant.

Các hướng xử lý:

- **Validate trước khi mutate:** kiểm tra tham số, giới hạn số học, precondition trước cập nhật. Kiểm tra phụ thuộc state phải đặt trong lock hoặc trong CAS computation dựa trên snapshot.
- **Prepare then commit:** tính toán state mới trong biến local/value object, kiểm chứng invariant trước khi công bố. Dùng assignment một immutable reference hoặc CAS để commit.
- **Rollback trong lock khi thực sự cần:** nếu operation nhiều bước có thể lỗi, viết cleanup/rollback được kiểm chứng; đừng mặc định `finally` nhả lock nghĩa rollback đã xảy ra.
- **Không đặt side effect không thể đảo ngược** (gửi email, phát thông báo, thực hiện remote payment) giữa nhiều bước cập nhật state trong một CAS loop hoặc critical section rồi giả định có thể retry an toàn.
- **Định nghĩa cancellation:** thread bị interrupt giữa một operation phải rời đi ở điểm an toàn, hoặc hoàn thành/rollback đúng protocol. Không nuốt `InterruptedException` rồi bỏ state nửa chừng.

Ví dụ prepare-then-commit đơn giản:

~~~java
synchronized (lock) {
    if (available <= 0) {
        return false;
    }
    int nextAvailable = available - 1;
    int nextReserved = Math.addExact(reserved, 1);
    // Toàn bộ tính toán/validation hoàn tất trước khi sửa state.
    available = nextAvailable;
    reserved = nextReserved;
    return true;
}
~~~

Đây là mảnh minh họa bên trong method trả `boolean`. `Math.addExact` ném `ArithmeticException` khi overflow; vì exception xảy ra **trước** những assignment, state cũ được giữ. Khi đã bắt đầu commit, đảm bảo chỉ còn các phép gán primitive không ném exception và không có callback ngoài được gọi giữa chừng. Do lock bảo vệ reader, state dở dang nội bộ giữa hai phép gán không bị lộ ra ngoài.

Nếu có nhiều phép toán phức tạp hoặc object mutable liên quan, dùng **một immutable candidate state** để commit qua reference thường dễ kiểm tra exception safety hơn.

## 12. Integer overflow và miền hợp lệ của invariant

Khi điều kiện `a + b == total` dùng số nguyên Java, phải xét **overflow**. Với `int`, cộng hai số gần `Integer.MAX_VALUE` có thể wrap qua giá trị âm. Một invariant kiểm tra bằng `a+b` thông thường đôi khi đánh giá sai do overflow.

Các giải pháp:

- Xác định miền hợp lệ và biên dữ liệu rõ ràng.
- Dùng `Math.addExact` / `Math.subtractExact` khi muốn overflow gây exception.
- Chọn `long` nếu miền dữ liệu lớn; `long` cũng có giới hạn và có thể overflow.
- Trong bài toán tiền tệ, dùng đơn vị nhỏ nhất với `long` có miền kiểm tra, hoặc kiểu số thích hợp khi cần độ chính xác thập phân; không dùng `double` để đại diện tiền chính xác.
- Kiểm tra amount dương, không vượt hạn mức, cùng currency/đơn vị và mọi điều kiện business **trước khi commit**.

Lưu ý rằng thread safety và tính đúng của số học là hai chiều khác nhau. Một operation được đồng bộ hoàn hảo nhưng cho phép overflow vẫn có thể phá invariant.

## 13. Tránh deadlock, starvation và lock contention

Để giữ invariant, có thể cần khóa; nhưng một giao thức lock sai có thể khiến ứng dụng không hoàn tất:

- **Lock ordering:** lấy nhiều lock theo thứ tự toàn cục để giảm nguy cơ vòng chờ.
- **Lock scope:** chỉ giữ lock khi đọc/ghi shared state thật cần atomic; không giữ qua I/O không có timeout, callback người dùng hoặc chờ `Future.get()` dài.
- **No blocking self-dependency:** đừng gọi `Thread.join()` trong khi giữ lock mà worker cần để kết thúc.
- **Fairness:** fair lock có thể đổi cách phục vụ contender nhưng không tự bảo đảm tail latency hay hạn định thời gian của task.
- **Contention:** một lock duy nhất có thể thành điểm nghẽn khi rất nhiều thread cùng cập nhật. Trước khi tách lock, phải kiểm tra invariant còn nằm gọn trong từng phần không.
- **Deadlock detection:** thread dump và `ThreadMXBean` có thể hỗ trợ tìm deadlock; không phát hiện được không có nghĩa chương trình không có starvation, livelock hoặc chờ I/O.

Đặc biệt, **tách một lock thành hai lock riêng cho hai field** để tăng throughput có thể làm mất chính invariant đang bảo vệ. Khi một invariant buộc cập nhật nhiều field cùng lúc, sharding cần tái thiết kế ranh giới state chứ không chỉ đổi tên lock.

## 14. Đọc snapshot nhất quán và Linearizability

Trong một concurrent API, một snapshot nên đại diện **một trạng thái có thể tồn tại tại một thời điểm hợp lệ**. Nếu API cần *linearizability*, mỗi operation đã hoàn tất phải có thể được xem như có hiệu lực tại một điểm nằm giữa khi được gọi và khi trả về, đồng thời giữ thứ tự thực tế của các lời gọi không chồng lấn.

- Với `synchronized`, update và snapshot cùng lấy một lock nên có thể giải thích operation/snapshot trong thứ tự lock.
- Với `AtomicReference<State>`, CAS thành công là điểm tuyến tính hóa của update, còn một lần `get()` là điểm quan sát snapshot.
- Với hai getter riêng biệt, dù mỗi getter synchronized, cặp kết quả caller ghép có thể lấy từ **hai thời điểm khác nhau**.

Ví dụ lỗi:

~~~java
int available = ledger.getAvailable();  // Một lần khóa rồi mở.
int reserved = ledger.getReserved();    // Lần khóa khác.
int total = available + reserved;       // Có thể ghép hai version.
~~~

Hai getter tự bảo vệ từng biến chưa đủ cho **snapshot của cả nhóm**. API nên trả một `record Snapshot(int available, int reserved)` duy nhất dưới một lần lock, hoặc một immutable state được lấy từ một reference atomic.

### 14.1 Snapshot consistency khác metric eventually consistent

Không phải mọi cặp counter đều cần snapshot linearizable. Với telemetry/metrics, đọc hai bộ đếm độc lập hơi lệch tại thời điểm cập nhật có thể **được chấp nhận theo hợp đồng**. Trong kế toán số dư hoặc cấp phát quota, điều đó thường không được phép.

Trước khi tăng chi phí synchronization, đặt câu hỏi: “Có người dùng/operation nào dựa vào tổng này để đưa ra quyết định phải đúng ngay không?” Nếu có, bảo đảm atomic snapshot. Nếu chỉ là dashboard gần đúng, cần ghi rõ consistency model được phép.

## 15. Kiểm thử invariant đa trường có ý nghĩa

Test concurrency có giá trị khi nó kiểm tra **điều kiện nghiệp vụ** dưới các interleaving có chủ đích.

### 15.1 Kiểm tra sau cạnh tranh

Với ledger có 100 phiếu và 2 thread mỗi thread gọi `reserveOne()` 100 lần, sau `join()` cần assert:

~~~text
available >= 0
reserved >= 0
available + reserved == 100
reserved == 100
~~~

Cả hai ví dụ `LockedInvariantDemo` và `AtomicInvariantDemo` cho kết quả xác định này, bất kể thứ tự lịch chạy. Nếu chỉ assert “không có exception”, test sẽ bỏ sót oversubscription và lost update.

### 15.2 Kiểm tra trong khi writer hoạt động

Một lỗi phổ biến là chỉ kiểm tra state cuối. Nếu writer thực hiện `available--` rồi `reserved++` và reader lấy snapshot giữa chừng, kết quả cuối vẫn bằng 100 nhưng invariant của **mọi snapshot quan sát được** đã bị phá.

Dùng `CountDownLatch` hoặc barrier để buộc reader chạy đúng thời điểm nhạy cảm, giống `VolatilePairInvariantDemo`. Test đối với thiết kế đã sửa phải dùng **public snapshot API** và xác nhận reader không bao giờ nhận tổng 99. Test phải có cơ chế mở latch/giải phóng worker trong `finally` để không làm tiến trình test kẹt.

### 15.3 Kiểm tra exception và cancellation

Cần gây lỗi tại các bước có khả năng throw: validation, chuẩn bị candidate state, callback ngoài hoặc lúc chờ lock. Kết quả hợp lệ phải là:

- operation thành công và **toàn bộ state** được commit;
- hoặc operation thất bại và state vẫn nhất quán theo chính sách đã định.

Đừng chỉ assert exception được ném; hãy assert invariant **sau exception**, sau cancellation và sau timeout.

### 15.4 Kiểm tra interleaving và quan sát runtime

Chạy nhiều thread, tăng tranh chấp và lặp lại stress test; quan sát throughput, p95/p99 latency, queue depth và contention khi tải thực. Công cụ **jcstress** của OpenJDK có thể giúp xây dựng những thử nghiệm concurrency nhỏ và phân loại outcome cho phép/cấm.

Nhưng test pass không chứng minh không có race. Code review phải vẽ đúng những access và quan hệ happens-before; test là chứng cứ bổ trợ chứ không thay chứng minh về giao thức.

## 16. Những lỗi phổ biến

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Mỗi field dùng `volatile` rồi update lần lượt. | Visibility không nhóm nhiều lần ghi thành một transaction. | Một lock chung hoặc immutable aggregate state. |
| Mỗi field dùng một `AtomicInteger`. | Atomic ở cấp biến, không ở cấp invariant đa trường. | AtomicReference của một state, hoặc lock toàn operation. |
| Chỉ synchronized setter, getter không lock. | Reader có thể thấy trạng thái trung gian và thiếu HB. | Đọc snapshot dưới cùng protocol. |
| Hai getter synchronized riêng rồi ghép kết quả. | Update có thể diễn ra giữa hai getter; snapshot bị trộn version. | Một getter snapshot trả cả nhóm. |
| Trả mutable collection từ getter có lock. | Caller sửa collection sau khi lock đã nhả. | Immutable/deep snapshot hoặc API cập nhật do owner quản lý. |
| Lấy hai lock theo thứ tự tham số truyền vào. | Hai thread truyền A,B và B,A có thể deadlock. | Thứ tự khóa toàn cục, tie-breaker khi cần. |
| Cho exception xảy ra giữa hai assignment rồi tự động nhả lock. | Nhả lock không rollback state dở dang. | Validate/prepare trước, commit an toàn, rollback khi cần. |
| Tính `int a+b` mà không xét overflow. | Wrap-around có thể phá invariant dù synchronization đúng. | Giới hạn miền hoặc số học checked. |
| Đưa side effect không idempotent trong vòng CAS. | CAS thất bại gây retry và side effect có thể lặp. | Tách chuẩn bị state và side effect; thiết kế idempotency. |
| Lock JVM để đồng bộ database/nhiều service. | Lock chỉ bảo vệ state và participants trong JVM. | Transaction/versioning/idempotency ở đúng cấp hệ thống. |
| Chỉ test state cuối cùng. | Có thể bỏ sót snapshot trung gian bị lệch. | Test cả snapshot khi đang cập nhật, dùng latch/barrier. |

## 17. Checklist thiết kế một object thread-safe nhiều trường

Trước khi công bố object cho nhiều thread, tự trả lời:

1. **Invariant:** các công thức/quan hệ nào giữa các field phải đúng tại ranh giới public?
2. **Ownership:** object/class nào sở hữu cả nhóm state và quyền sửa?
3. **Writers:** mọi phương thức và thread có thể thay state là gì? Có đường truy cập không qua protocol không?
4. **Readers:** reader có thể lấy một snapshot nhất quán không, hay ghép nhiều getter?
5. **Atomic boundary:** check, validate và tất cả assignment đã nằm chung một critical section/CAS candidate chưa?
6. **HB:** phép ghi công bố và lần đọc sau đó có quan hệ happens-before hợp lệ không?
7. **Failure:** exception, overflow, interruption, timeout và early return có giữ invariant không?
8. **Multiple objects:** có cần lock ordering, một ledger owner hoặc transaction cấp cao hơn không?
9. **Performance:** lock contention hoặc CAS retry có thực sự là bottleneck qua đo đạc không?
10. **Evidence:** có test cạnh tranh, test snapshot trung gian, test lỗi và review invariant không?

### Câu hỏi tự kiểm tra

**1.** Hai trường `available` và `reserved` đều volatile. Writer giảm available rồi tăng reserved. Reader có thể thấy tổng sai tạm thời không?  
**Có.** Hai volatile writes là hai hành động riêng, không thành một atomic transition.

**2.** Vì sao khóa mỗi phép setter riêng nhưng ghép hai getter vẫn sai?  
**Vì** một writer có thể cập nhật giữa hai lần getter. Muốn một snapshot, phải đọc cả hai dưới một critical section hoặc cùng immutable state.

**3.** Cùng một lock trong `reserveOne()` và `snapshot()` giải quyết hai vấn đề nào?  
**Mutual exclusion** cho cập nhật nguyên tử ở ranh giới method, và **happens-before** qua unlock→lock cho visibility.

**4.** Dùng hai `AtomicInteger`, mỗi thao tác atomically đúng, có bảo toàn invariant `a+b=100` tại mọi snapshot không?  
**Không.** Atomic ở cấp từng field; tổng là invariant liên biến.

**5.** Khi nào `AtomicReference<State>` phù hợp?  
Khi toàn bộ state có thể biểu diễn bằng snapshot immutable, mỗi update tạo candidate và công bố bằng một CAS; cần chấp nhận chi phí allocation/retry và tránh side effect trong vòng CAS.

**6.** Vì sao `finally { lock.unlock(); }` chưa đủ để xử lý exception safety?  
Nó nhả lock nhưng không tự hoàn tác phép ghi đã xảy ra trước exception. State dở dang có thể trở nên quan sát được sau khi unlock.

**7.** Hai account có lock riêng: thread A lấy lock 1→2, thread B lấy 2→1. Rủi ro gì?  
**Deadlock.** Dùng thứ tự lấy lock toàn cục hoặc một owner lock cho invariant.

**8.** Khi chuyển tiền giữa hai database ở hai process, `synchronized` trên một service có bảo đảm tổng tiền không đổi không?  
**Không.** Cần giao thức dữ liệu/transaction ở cấp hệ thống, vì monitor của một JVM không bao phủ các process khác.

**9.** Vì sao test chỉ kiểm tra số dư cuối có thể bỏ sót lỗi?  
Vì state tạm thời có thể bị lộ không nhất quán nhưng được phục hồi trước thời điểm test đọc. Phải kiểm tra snapshot trong lúc operation đang diễn ra.

**10.** Chọn phương pháp ban đầu nào cho invariant đa trường trong một object?  
Thường dùng **private lock và API nghiệp vụ** vì dễ chứng minh đúng. Chuyển sang immutable snapshot/CAS hoặc lock chuyên biệt khi có yêu cầu và bằng chứng hiệu năng.

**Ghi nhớ:** Giữ invariant đa trường là bài toán **thiết kế chuyển trạng thái nguyên tử và quan sát snapshot nhất quán**. Mọi writer và reader phải tuân thủ cùng ranh giới ownership và đồng bộ; `volatile` hay atomic trên từng field không thay thế được giao thức bảo vệ **toàn bộ quan hệ giữa chúng**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-009">Thread-safe collection và compound action chưa atomic</a>

<details>
<summary>Xem chi tiết</summary>

# Thread-safe collection và compound action chưa atomic trong Java

## Mục tiêu và phạm vi

Một collection được quảng bá là **thread-safe** không có nghĩa **mọi đoạn chương trình sử dụng collection đó đều atomic**. Hai lời gọi phương thức an toàn nếu xét riêng vẫn có thể bị một thread khác chen vào giữa. Vì thế, một chuỗi gồm “kiểm tra rồi thay đổi”, “đọc rồi tăng”, “đếm rồi truy cập”, hay “cập nhật hai key liên quan” có thể vi phạm bất biến nghiệp vụ dù collection không bị hỏng cấu trúc nội bộ.

Bài này tập trung vào **Java concurrency nói chung**: `Collections.synchronizedList/Map`, `ConcurrentHashMap`, `ConcurrentLinkedQueue`, `BlockingQueue`, `CopyOnWriteArrayList`, khóa ngoài và snapshot immutable. Không dùng API, annotation hoặc cấu hình riêng của Spring vì phần đó được học trong chương Spring khác.

Sau bài, bạn có thể:
- Diễn giải chính xác **thread safety**, **atomicity**, **visibility**, **compound action** và **linearization point** khi thao tác trên collection.
- Phân biệt “một method thread-safe” với “một chuỗi nhiều method là transaction”.
- Tìm và sửa lỗi `containsKey` → `put`, `get` → `put`, `isEmpty` → `remove/take`, `size` → `get` và iteration đồng thời.
- Chọn đúng API nguyên tử như `putIfAbsent`, `compute`, `merge`, `remove(key,value)`, `replace(key,old,new)`, `poll` hoặc bảo vệ trọn compound action bằng cùng lock.
- Biết vì sao **atomic theo một key** không đồng nghĩa **atomic trên nhiều key**, và vì sao iterator **weakly consistent** hoặc **snapshot** không phải giao dịch nhất quán toàn collection.
- Chạy ví dụ **Java 17+** với stdout được kiểm soát để thấy race xảy ra và cách sửa.

## 1. “Thread-safe” đang bảo đảm điều gì?

Trong Java, các collection thread-safe được thiết kế để **nhiều thread có thể truy cập theo một hợp đồng concurrency đã định** mà không vi phạm các bảo đảm của cấu trúc dữ liệu. Nhưng hợp đồng khác nhau tùy loại collection và từng phương thức:

| Collection / wrapper | Tính chất cốt lõi | Giới hạn cần hiểu |
| --- | --- | --- |
| `Collections.synchronizedList(...)` | Đồng bộ nhiều thao tác trên một monitor của wrapper | Vòng lặp và chuỗi nhiều lời gọi thường cần tự `synchronized(list)` trên wrapper |
| `Collections.synchronizedMap(...)` | Bảo vệ các thao tác theo hợp đồng wrapper | `get` rồi `put` là hai thao tác riêng nếu không khóa ngoài toàn chuỗi |
| `ConcurrentHashMap<K,V>` | Cho phép nhiều thao tác đồng thời; hỗ trợ update atomic **theo key** | Không tự biến cập nhật nhiều key thành transaction; iteration không phải snapshot cố định |
| `ConcurrentLinkedQueue<E>` | Queue không khóa theo thuật toán phù hợp concurrency; `poll` lấy một phần tử nếu có theo một thao tác | `isEmpty` → `poll` và `peek` → `poll` không là một thao tác nguyên tử |
| `BlockingQueue<E>` | `put/take/offer/poll` phối hợp producer–consumer, có semantics chờ/timeout | Kiểm tra `size/isEmpty` rồi `take` không khóa tình trạng queue ở thời điểm kiểm tra |
| `CopyOnWriteArrayList<E>` | Tạo bản sao mảng khi cập nhật, iterator là snapshot | Iterator không phản ánh cập nhật sau khi được tạo; mỗi lần sửa có chi phí copy |
| `List.of` / `List.copyOf` | Danh sách không cho sửa cấu trúc sau khi tạo | Các object mutable **bên trong** có thể vẫn thay đổi; cần safe publication thích hợp |

Không có câu khẳng định chung nào rằng “mọi phép gọi trên mọi thread-safe collection đều là linearizable transaction” nếu chưa xét **contract của phương thức và phạm vi dữ liệu**. Tương tự, `Collections.synchronizedList` và `ConcurrentHashMap` đều an toàn theo mục đích thiết kế nhưng chúng **khác nhau về khóa, cách iteration và atomic API**.

### Một phép thao tác và một bất biến

Giả sử map lưu số lượt truy cập:

~~~java
Integer old = visits.get("home");
visits.put("home", old + 1);
~~~

Ngay cả khi `visits.get` và `visits.put` đều được đồng bộ riêng lẻ, phép **“tăng đúng một đơn vị”** chưa được bảo đảm. Hai thread có thể cùng đọc 0 rồi cùng ghi 1. Collection vẫn toàn vẹn, nhưng **kết quả nghiệp vụ sai**.

**Bất biến (invariant)** là điều kiện phải đúng sau một thao tác nghiệp vụ; ví dụ: “sau N lần tăng thành công, counter tăng đúng N”, “một job chỉ được một worker claim”, hoặc “tổng tiền giữa hai tài khoản không đổi”. Muốn sửa compound action, phải xác định **toàn bộ các bước cần bảo vệ để giữ invariant**.

## 2. Atomicity khác visibility và thread-safe method

Hình dung hai thread cùng chạy:

~~~text
Thread A                       Thread B
--------                       --------
old = map.get("count")  -> 0
                               old = map.get("count") -> 0
map.put("count", 1)
                               map.put("count", 1)

Hai lần get an toàn; hai lần put an toàn.
Kết quả sau hai lần cộng có thể vẫn là 1.
~~~

Trong Java Memory Model:

- **Visibility:** thay đổi do một thread tạo ra có được thread khác quan sát đúng theo một quan hệ happens-before hay không.
- **Atomicity:** các bước của một thao tác logic có thể bị xen giữa không; có một thời điểm quan sát mà toàn bộ thao tác đã có hiệu lực hay không.
- **Ordering:** những hành động nào được đặt trước/sau theo hợp đồng đồng bộ, không phụ thuộc đơn thuần thứ tự dòng code của các thread.
- **Linearization point:** thời điểm tưởng tượng mỗi thao tác atomic có hiệu lực như một thao tác đơn lẻ, giúp suy luận thứ tự các thao tác cạnh tranh.

Một thao tác `get` có thể hoàn toàn thread-safe và nhìn thấy giá trị mới nhất theo hợp đồng của collection, nhưng **sau khi `get` trả về**, một thread khác vẫn có quyền thay đổi collection. Do đó, kết quả kiểm tra ở `t1` không tự còn đúng khi thread thực hiện hành động ở `t2`.

**Điểm quan trọng:** nếu cần tính đúng đắn cho cả chuỗi, hãy dùng **một compound API được bảo đảm atomic**, hoặc **một lock/giao thức ownership bảo vệ tất cả các bước**. Chỉ chọn collection thread-safe mà không đổi đoạn code check-then-act là chưa đủ.

## 3. Ví dụ xác định: synchronizedMap vẫn có lost update

`Collections.synchronizedMap` cung cấp wrapper đồng bộ cho các thao tác theo contract, nhưng đoạn `get` rồi `put` gọi tách rời vẫn có một khoảng trống. Ví dụ sau cố tình buộc cả hai thread **đọc trước khi thread nào ghi** để lỗi không phụ thuộc may rủi scheduler.

Tạo file **`SynchronizedMapRaceDemo.java`**:

~~~java
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class SynchronizedMapRaceDemo {
    public static void main(String[] args) throws InterruptedException {
        Map<String, Integer> counts =
                Collections.synchronizedMap(new HashMap<>());
        counts.put("views", 0);

        CountDownLatch readByBoth = new CountDownLatch(2);
        CountDownLatch allowWrite = new CountDownLatch(1);

        Runnable increase = () -> {
            int old = counts.get("views");
            readByBoth.countDown();

            try {
                allowWrite.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            counts.put("views", old + 1);
        };

        Thread first = new Thread(increase, "writer-1");
        Thread second = new Thread(increase, "writer-2");
        first.start();
        second.start();

        readByBoth.await();
        allowWrite.countDown();
        first.join();
        second.join();

        System.out.println("expected=2 actual=" + counts.get("views"));
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac SynchronizedMapRaceDemo.java
java SynchronizedMapRaceDemo
~~~

Stdout nếu các thread hoàn thành bình thường:

~~~text
expected=2 actual=1
~~~

Tại sao kết quả xác định là 1?
1. Map ban đầu lưu `"views" -> 0`.
2. Cả hai thread gọi `get` và nhận 0 trước khi được mở latch `allowWrite`.
3. Mỗi thread tính `0 + 1` rồi gọi `put`; hai thao tác ghi riêng có thể hợp lệ nhưng đều đặt giá trị 1.
4. `join` khiến main chờ cả hai hoàn tất trước khi đọc và in kết quả.

Đây là **race ở cấp thao tác nghiệp vụ**, không phải bằng chứng wrapper `synchronizedMap` hỏng cấu trúc. Latch chỉ phục vụ tái hiện sai lầm; code production không dùng latch theo cách này để “sửa” counter.

## 4. Giải pháp A: khóa toàn bộ compound action

### 4.1. Một lock cho cả đọc và sửa

Với `Collections.synchronizedMap`, có thể dùng monitor của chính wrapper:

~~~java
Map<String, Integer> counts =
        java.util.Collections.synchronizedMap(new java.util.HashMap<>());

synchronized (counts) {
    counts.put("views", counts.getOrDefault("views", 0) + 1);
}
~~~

Đây là **đoạn mã minh họa trong một method**; biến `counts` cần được chia sẻ đúng nơi. Vì wrapper đồng bộ trên monitor của nó cho các method được bảo vệ, khối `synchronized(counts)` bao cả chuỗi kiểm tra–cập nhật. Khi thread khác cũng sử dụng wrapper theo contract, nó không thể xen vào giữa chuỗi này bằng các thao tác được đồng bộ trên cùng monitor.

Nếu dùng wrapper `Collections.synchronizedMap(backing)`, **không tiếp tục cho các thread khác truy cập trực tiếp `backing`**: họ có thể vượt qua wrapper và phá toàn bộ giao thức. Nếu nhiều đường truy cập dùng lock khác nhau, chúng cũng không phối hợp.

### 4.2. Vòng lặp synchronizedList cần cùng monitor

Một hiểu lầm khác: mọi method của `Collections.synchronizedList` được đồng bộ, nên vòng `for-each` cũng tự an toàn. Thực tế iterator thường được tạo rồi sử dụng qua nhiều bước. Một thread khác có thể sửa list giữa lúc iterator đang duyệt.

~~~java
List<String> names =
        java.util.Collections.synchronizedList(new java.util.ArrayList<>());

names.add("An");
names.add("Bình");

synchronized (names) {
    for (String name : names) {
        System.out.println(name);
    }
}
~~~

Đây là đoạn code trong một method, không phải một chương trình độc lập. **Khóa trên chính wrapper `names`** trong toàn bộ iteration, miễn tất cả thread cùng dùng wrapper đó cho các thao tác cạnh tranh. Tương tự, nếu gọi `Iterator`/`Spliterator`/`Stream` trên wrapper, cần tuân theo tài liệu đồng bộ của collection và giữ lock phù hợp khi đang duyệt.

**Không dựa vào `ConcurrentModificationException` để “phát hiện” hoặc ngăn race.** Với các collection dùng fail-fast iterator, exception là cơ chế best-effort báo lỗi sử dụng, không phải hàng rào đồng bộ; việc *không* ném exception không có nghĩa vòng duyệt an toàn.

### 4.3. Check-then-act trên list

Sai khi concurrent removal vẫn có thể xảy ra:

~~~java
if (names.size() > 0) {
    String first = names.get(0);
}
~~~

Ngay sau `size() > 0`, một thread khác có thể xóa phần tử đầu. Dù `size()` và `get(0)` đều được đồng bộ riêng, `get(0)` có thể ném `IndexOutOfBoundsException`.

Cách sửa khi muốn đọc phần tử đầu theo snapshot hợp lệ:

~~~java
synchronized (names) {
    if (!names.isEmpty()) {
        String first = names.get(0);
        System.out.println(first);
    }
}
~~~

Nếu nghiệp vụ thực sự cần **lấy phần tử ra để không ai lấy trùng**, tốt hơn là chọn API queue `poll()` atomic (phần tiếp theo) thay vì tự ghép `isEmpty` + `remove(0)`.

### 4.4. Khóa đúng phạm vi invariant

Một monitor chung giúp bảo vệ cả các bước đọc/ghi, nhưng phạm vi quá rộng có thể gây contention. Không nên giữ lock trong lúc gọi network I/O, callback không kiểm soát, hoặc xử lý lâu nếu bất biến không cần. Ngược lại, thu nhỏ lock đến mức **đánh mất tính nguyên tử** lại làm race xuất hiện.

Cách kiểm tra một giải pháp khóa:
- **Tất cả** writer và reader quan trọng có dùng **cùng lock** không?
- Critical section có bao trọn check và update không?
- Có đường truy cập trực tiếp backing collection vượt lock không?
- Có ngoại lệ làm lock không được nhả (với `ReentrantLock` cần `unlock()` trong `finally`)?
- Có nhiều lock được lấy theo thứ tự khác nhau dẫn đến deadlock không?

## 5. Giải pháp B: dùng compound operation nguyên tử theo đúng contract

Đối với `ConcurrentHashMap`, một số thao tác gom cả check/update thành một lời gọi có semantics atomic theo key. Ví dụ:

| Nhu cầu | Mẫu dễ sai | Phương thức phù hợp |
| --- | --- | --- |
| Chỉ đặt giá trị khi key chưa tồn tại | `if (!containsKey(k)) put(k,v)` | `putIfAbsent(k,v)` |
| Tăng giá trị gắn với một key | `put(k, get(k)+1)` | `compute(k, fn)` hoặc `merge(k, 1, Integer::sum)` |
| Xóa key nếu giá trị vẫn là bản đã quan sát | `if (get(k).equals(v)) remove(k)` | `remove(k,v)` |
| Đổi giá trị nếu vẫn bằng giá trị cũ | `if (get(k).equals(old)) put(k,next)` | `replace(k, old, next)` |
| Tạo value cho key thiếu | `if (get(k)==null) put(k, factory())` | `computeIfAbsent(k, factory)` khi contract phù hợp |

Trong `ConcurrentHashMap`, khóa/thuật toán nội bộ hỗ trợ atomicity của những cập nhật này **cho một key**, nhưng một thao tác liên quan **hai key** hoặc một collection khác không tự thành transaction.

**Cẩn thận với `null`:** `ConcurrentHashMap` không cho key/value `null`. `compute`/`merge` dùng kết quả `null` theo semantics xóa hoặc không có mapping tùy method. Nếu nghiệp vụ cần lưu “giá trị null là trạng thái hợp lệ”, hãy thiết kế sentinel/Optional/value object thay vì lén đưa `null` vào map.

### 5.1. Ví dụ Java 17: ConcurrentHashMap.compute sửa lost update

Tạo file **`ConcurrentMapAtomicDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ConcurrentMapAtomicDemo {
    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, Integer> counts =
                new ConcurrentHashMap<>();
        counts.put("views", 0);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> tasks = new ArrayList<>();

            for (int worker = 0; worker < 8; worker++) {
                tasks.add(pool.submit(() -> {
                    for (int i = 0; i < 1_000; i++) {
                        counts.compute("views", (key, oldValue) ->
                                oldValue == null ? 1 : oldValue + 1);
                    }
                }));
            }

            for (Future<?> task : tasks) {
                task.get();
            }

            System.out.println("expected=8000 actual=" + counts.get("views"));
            System.out.println("keys=" + counts.size());
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
javac ConcurrentMapAtomicDemo.java
java ConcurrentMapAtomicDemo
~~~

Stdout khi mọi task thành công:

~~~text
expected=8000 actual=8000
keys=1
~~~

Tám task, mỗi task tăng một nghìn lần: tổng cộng 8.000 phép cập nhật. `ConcurrentHashMap.compute` bảo đảm cập nhật tính toán đối với **một mapping key** được thực hiện nguyên tử theo contract của class. Các thread có thể tranh chấp và chờ khi cập nhật key này, nhưng không có interleaving giữa `get` và `put` của caller vì đoạn read-modify-write đã được đưa vào một compound method.

**Điều kiện dùng đúng `compute`:**
- Hàm remapping phải ngắn, đáng tin cậy, không block quá lâu và nên không có tác dụng phụ đối với bên ngoài.
- Không sửa những mapping khác của cùng map trong chính callback; tài liệu `ConcurrentHashMap` cấm cố gắng cập nhật các mapping khác từ hàm compute trong một số trường hợp và có thể phát hiện recursive update không bao giờ hoàn tất.
- Không giả định logic thêm hai key hoặc cập nhật database bên ngoài đã được bao trong thao tác atomic.
- `Future.get()` giúp main biết mọi tác vụ thực sự hoàn thành và phát hiện exception, thay vì chỉ đọc một giá trị khi pool còn chạy.

Với một implementation khác chỉ tuân theo interface `Map` hoặc `ConcurrentMap`, cần đọc **đúng contract class**; không suy toàn bộ bảo đảm atomic mạnh của `ConcurrentHashMap` sang mọi custom map.

### 5.2. putIfAbsent và câu chuyện “chỉ một người thắng”

Thay vì:

~~~java
if (!jobs.containsKey(id)) {
    jobs.put(id, "running");
}
~~~

dùng:

~~~java
String previous = jobs.putIfAbsent(id, "running");
boolean claimed = (previous == null);
~~~

Đây là đoạn trong một method khi `jobs` là `ConcurrentHashMap<String,String>` và `id` là một `String` khác null. Chỉ caller đầu tiên đặt mapping từ absent sang `"running"` được trả về `null` theo hợp đồng. Các caller sau nhận giá trị hiện có.

**Nhưng:** nếu trạng thái cần chuyển từ `"queued"` sang `"running"` chứ không phải absent → present, `putIfAbsent` không phải API đúng. Khi đó dùng `replace(id,"queued","running")` để thực hiện **compare-and-update** theo giá trị kỳ vọng, hoặc `compute` khi logic phức tạp hơn.

Thao tác atomic trên map chỉ bảo đảm **trạng thái của map**. Nếu sau `putIfAbsent` phải gửi email, ghi database, gọi HTTP, việc bên ngoài đó **không tự thuộc cùng một transaction**. Cần thiết kế idempotency, retry và bù trừ cho side effect nghiệp vụ theo phạm vi hệ thống.

### 5.3. merge cho bộ đếm theo key

Với `ConcurrentHashMap<String, Integer>`, có thể viết ngắn:

~~~java
counts.merge("views", 1, Integer::sum);
~~~

Nếu key chưa có, đặt 1; nếu đã có giá trị thì kết hợp old và 1. Phép cập nhật một mapping là atomic theo hợp đồng `ConcurrentHashMap`. Tự viết `getOrDefault` rồi `put` làm mất ý nghĩa này.

Với thống kê cập nhật cực nhiều, một mô hình khác là map từ key sang `LongAdder`. Tuy nhiên `LongAdder.sum()` khi có cập nhật đồng thời **không phải một snapshot nguyên tử**. Chỉ chọn nó nếu nghiệp vụ chấp nhận giá trị quan sát xấp xỉ trong lúc cập nhật; số dư, giới hạn tín dụng và quyết định “chỉ bán khi còn hàng” thường cần invariant chặt hơn.

## 6. Giải pháp C: lấy phần tử khỏi queue bằng một thao tác

Một lỗi check-then-act phổ biến trong consumer:

~~~java
if (!queue.isEmpty()) {
    String task = queue.remove();
    process(task);
}
~~~

Đây là đoạn minh họa với `queue` và `process` đã khai báo. Ngay sau `isEmpty() == false`, một consumer khác có thể lấy hết phần tử. Khi đó `remove()` trên queue trống ném `NoSuchElementException`. Nếu dùng `take()` sau `isEmpty()`, thread có thể **bị chờ** khi phần tử đã bị thread khác lấy.

Khi không muốn chờ, dùng **`poll()` duy nhất**:

~~~java
String task = queue.poll();
if (task != null) {
    process(task);
}
~~~

`poll()` đồng thời kiểm tra và lấy một phần tử ở đầu queue (hoặc trả `null` nếu trống) theo hợp đồng của queue. Với `ConcurrentLinkedQueue`, `null` không phải phần tử hợp lệ, nên `null` biểu thị không lấy được task.

### Ví dụ Java 17: hai consumer, một phần tử

Tạo file **`QueueClaimDemo.java`**:

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class QueueClaimDemo {
    public static void main(String[] args) throws Exception {
        ConcurrentLinkedQueue<String> queue =
                new ConcurrentLinkedQueue<>();
        queue.add("job-1");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Callable<String> claim = () -> {
                start.await();
                return queue.poll();
            };

            Future<String> first = pool.submit(claim);
            Future<String> second = pool.submit(claim);

            start.countDown();
            String a = first.get();
            String b = second.get();

            int claimed = (a != null ? 1 : 0) + (b != null ? 1 : 0);
            int empty = (a == null ? 1 : 0) + (b == null ? 1 : 0);

            System.out.printf(
                    "claimed=%d empty=%d remaining=%d%n",
                    claimed, empty, queue.size());
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
javac QueueClaimDemo.java
java QueueClaimDemo
~~~

Stdout:

~~~text
claimed=1 empty=1 remaining=0
~~~

Không dự đoán thread nào thắng. Nhưng vì chỉ có **một phần tử** và mỗi worker gọi `poll()` đúng một lần, chỉ một worker nhận `"job-1"`, worker còn lại nhận `null`. `Future.get()` đảm bảo main đọc kết quả sau khi cả hai thao tác hoàn tất.

**Lưu ý về kiểu queue:** `BlockingQueue.take()` cố tình **chờ** khi trống; `poll(timeout, unit)` chờ có hạn, `poll()` không chờ. Chọn theo nhu cầu consumer. Không dùng `isEmpty()` trước `take()` như một bảo đảm rằng `take()` sẽ không block.

## 7. Iteration: fail-fast, weakly consistent và snapshot

Mỗi họ collection có hợp đồng iterator khác nhau; “thread-safe” không đồng nghĩa “đang duyệt toàn bộ collection ở một thời điểm duy nhất”.

| Kiểu quan sát | Thường gặp | Ý nghĩa |
| --- | --- | --- |
| Fail-fast (best effort) | `ArrayList`, `HashMap` iterator thông thường | Có thể ném `ConcurrentModificationException` nếu bị thay đổi cấu trúc ngoài iterator; không phải cơ chế bảo đảm an toàn khi chạy đồng thời |
| Iterator cần đồng bộ ngoài | `Collections.synchronizedList/Map` | Giữ đúng monitor của wrapper trong **toàn bộ lần duyệt** theo hợp đồng |
| Weakly consistent | `ConcurrentHashMap`, `ConcurrentLinkedQueue` | Không fail-fast do update đồng thời; có thể thấy một số update, bỏ qua một số update; không phải snapshot atomic |
| Snapshot iterator | `CopyOnWriteArrayList` | Nhìn trạng thái mảng tại lúc iterator được tạo, không thấy các thay đổi sau đó |

### 7.1. Ví dụ Java 17: snapshot iterator của CopyOnWriteArrayList

Tạo file **`CopyOnWriteSnapshotDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CopyOnWriteSnapshotDemo {
    public static void main(String[] args) {
        CopyOnWriteArrayList<String> names =
                new CopyOnWriteArrayList<>();
        names.add("A");

        Iterator<String> before = names.iterator();
        names.add("B");

        List<String> seen = new ArrayList<>();
        before.forEachRemaining(seen::add);

        System.out.println("iterator=" + seen);
        System.out.println("current=" + names);
    }
}
~~~

Chạy:

~~~bash
javac CopyOnWriteSnapshotDemo.java
java CopyOnWriteSnapshotDemo
~~~

Stdout:

~~~text
iterator=[A]
current=[A, B]
~~~

Iterator được tạo trước khi list thêm B, nên nó duyệt snapshot chứa A. List hiện tại có A và B. Đây là hành vi có chủ đích: vòng duyệt ổn định, ít phải phối hợp reader/writer, rất phù hợp tình huống **đọc nhiều, ghi ít**.

Đổi lại, mỗi update cấu trúc thường phải tạo/copy mảng; ghi thường xuyên hoặc list rất lớn có thể tạo chi phí allocation/copy đáng kể. Iterator snapshot của `CopyOnWriteArrayList` không hỗ trợ `Iterator.remove()`; cố xóa qua iterator sẽ ném `UnsupportedOperationException`.

### 7.2. Weakly consistent không đồng nghĩa ảnh chụp nhất quán

`ConcurrentHashMap.entrySet().iterator()` có thể duyệt trong lúc thread khác cập nhật map mà không cần khóa ngoài kiểu synchronizedMap. Nhưng các entry thu được **không nhất thiết là snapshot tại cùng một thời điểm**. Ví dụ, hệ thống chuyển tiền từ key A sang key B bằng hai cập nhật độc lập; một vòng duyệt có thể thấy A sau khi trừ và B trước khi cộng, dù cả hai thao tác riêng trên map đều an toàn.

Do đó:
- Dùng weakly consistent iterator để quan sát/monitor **chấp nhận biến động**.
- Nếu cần báo cáo tài chính hoặc invariant nhiều key tại một thời điểm, phải thiết kế đồng bộ/snapshot riêng.
- Đừng dùng `size()`, `isEmpty()` hoặc kết quả aggregate trong lúc mutation đồng thời làm bằng chứng tuyệt đối cho một quyết định đa bước. Các giá trị có thể lỗi thời ngay khi trả về.
- Không dựa vào `ConcurrentModificationException` để kiểm soát logic.

## 8. Atomic theo một key vẫn chưa atomic qua nhiều key

Giả sử lưu tài khoản A và B trong một `ConcurrentHashMap<String,Integer>`. Ta cần chuyển 70 đơn vị từ A sang B, ban đầu `A=100, B=0`, với invariant `A+B=100`.

~~~java
balances.compute("A", (key, old) -> old - 70);
balances.compute("B", (key, old) -> old + 70);
~~~

Hai lời gọi `compute` đều có thể atomic **cho key tương ứng**. Nhưng ở giữa chúng, một reader có thể thấy `A=30, B=0` và kết luận tổng là 30. Nếu một thao tác khác xen vào hoặc bước thứ hai ném exception, hệ thống có thể còn sai về nghiệp vụ.

**Không giả định có thể dùng một callback `ConcurrentHashMap.compute` để sửa key khác trong map** nhằm biến hai key thành một transaction; cách đó không thuộc hợp đồng an toàn của callback, có thể gây lỗi hoặc vấn đề tiến triển.

### 8.1. Sửa bằng một lock bảo vệ trọn invariant

Tạo file **`MultiKeyTransferDemo.java`**:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MultiKeyTransferDemo {
    private record Balances(int a, int b) {}

    private static final class Ledger {
        private int a = 100;
        private int b = 0;

        synchronized boolean transferAToB(int amount) {
            if (amount <= 0 || a < amount) {
                return false;
            }
            a -= amount;
            b += amount;
            return true;
        }

        synchronized Balances snapshot() {
            return new Balances(a, b);
        }
    }

    public static void main(String[] args) throws Exception {
        Ledger ledger = new Ledger();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first =
                    pool.submit(() -> ledger.transferAToB(70));
            Future<Boolean> second =
                    pool.submit(() -> ledger.transferAToB(70));

            int accepted = (first.get() ? 1 : 0) +
                    (second.get() ? 1 : 0);

            Balances view = ledger.snapshot();
            System.out.printf(
                    "accepted=%d A=%d B=%d total=%d%n",
                    accepted, view.a(), view.b(), view.a() + view.b());
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
javac MultiKeyTransferDemo.java
java MultiKeyTransferDemo
~~~

Stdout:

~~~text
accepted=1 A=30 B=70 total=100
~~~

Hai task đồng thời cố chuyển 70, nhưng lock của **cùng đối tượng Ledger** buộc mỗi lần kiểm tra–trừ–cộng diễn ra nguyên tử với các thao tác khác của Ledger. Chỉ một lần chuyển thành công; lần kia thấy `a=30` và trả `false`. `snapshot()` cũng khóa monitor đó, nên không quan sát trạng thái ở giữa bước trừ và bước cộng.

Ví dụ dùng hai trường để làm nổi bật invariant; nếu trạng thái thực nằm trong map thì có thể bảo vệ **mọi cập nhật và snapshot** qua một lock của service sở hữu map. Một lựa chọn khác là chứa cả A/B trong **một immutable record** rồi cập nhật record qua `AtomicReference.compareAndSet`/`updateAndGet`. Chọn dựa vào độ phức tạp invariant, tần suất ghi, contention và phạm vi dữ liệu.

**Ranh giới cần nhớ:** `synchronized`/`AtomicReference` của một JVM chỉ phối hợp các thread **trong cùng tiến trình**. Nếu hai process cùng sửa một database hoặc một tài nguyên phân tán, bạn cần giao thức giao dịch/phối hợp riêng của hệ thống đó; thread-safe collection không giải quyết concurrency liên tiến trình.

## 9. Kiểm tra kỹ các compound action thường gặp

### 9.1. containsKey + put

~~~java
if (!map.containsKey(key)) {
    map.put(key, value);
}
~~~

Một thread khác có thể chèn key sau `containsKey`. Với `ConcurrentHashMap`, dùng `putIfAbsent` khi cần **chỉ đặt nếu absent**. Nếu giá trị `null` có semantics đặc biệt, đọc contract cụ thể.

### 9.2. get + put để tăng counter

~~~java
map.put(key, map.getOrDefault(key, 0) + 1);
~~~

Nếu map bị nhiều writer sửa, có lost update. Dùng `compute`/`merge` của `ConcurrentHashMap`, hoặc khóa toàn chuỗi nếu dùng synchronized wrapper.

### 9.3. contains + add để tránh trùng trong list

~~~java
if (!list.contains(item)) {
    list.add(item);
}
~~~

Hai thread có thể cùng thấy chưa có rồi cùng thêm. Nếu invariant là **set không có phần tử trùng**, dùng cấu trúc `Set` thích hợp (ví dụ `ConcurrentHashMap.newKeySet()`) và một lời gọi `add(item)` có semantics atomic cho membership. Nếu vẫn cần thứ tự và cho phép duplicate theo các quy tắc khác, phải khóa toàn chuỗi dưới một monitor thống nhất.

### 9.4. size + get hoặc isEmpty + remove

~~~java
if (list.size() > index) {
    return list.get(index);
}
~~~

Một thread khác có thể xóa hoặc sắp lại list sau kiểm tra. Với synchronizedList, khóa wrapper cho toàn bộ chuỗi. Nếu dùng queue để nhận một phần tử, dùng `poll()` thay cho check-then-remove.

### 9.5. peek + poll

~~~java
String expectedHead = queue.peek();
String removed = queue.poll();
~~~

Hai lời gọi an toàn riêng nhưng thread khác có thể lấy head ở giữa; `removed` không nhất thiết bằng `expectedHead`. Nếu chỉ muốn **claim phần tử**, chỉ dùng kết quả `poll()`. Nếu phải “kiểm tra head thỏa điều kiện mới lấy”, cần một API/giao thức nguyên tử phù hợp, như một lock bảo vệ cả thao tác hoặc queue có semantics riêng được thiết kế cho yêu cầu đó.

### 9.6. Iteration + update

~~~java
for (String value : list) {
    if (shouldRemove(value)) {
        list.remove(value);
    }
}
~~~

Đây là đoạn minh họa có `shouldRemove`. Với một `ArrayList` bình thường, gọi `list.remove` trong enhanced for có thể gây `ConcurrentModificationException` ngay cả trong **một thread**. Với concurrent list, semantics tùy loại; iterator snapshot không sửa được snapshot. Hãy dùng `removeIf` nếu contract của collection đáp ứng chính xác phạm vi atomic bạn cần, hoặc khóa và thao tác qua `Iterator.remove()` khi collection hỗ trợ. **Không suy ra** mọi implementation của `removeIf` đều atomic so với mọi update khác.

## 10. Chọn công cụ theo yêu cầu

| Yêu cầu thực tế | Cách tiếp cận |
| --- | --- |
| Một writer, nhiều thread cần gửi command | Ownership + queue/actor-like executor |
| Tăng counter độc lập trong map | `ConcurrentHashMap.compute/merge`; hoặc map key -> `LongAdder` nếu chấp nhận snapshot không nguyên tử |
| Đăng ký một key nếu chưa có | `putIfAbsent` |
| Chuyển trạng thái theo giá trị cũ | `replace(key, expected, next)` hoặc `compute` |
| Nhiều consumer tranh một task | `BlockingQueue.take/poll` hoặc `ConcurrentLinkedQueue.poll` |
| Đọc nhiều, ít thay đổi, iteration ổn định | `CopyOnWriteArrayList` hoặc publish immutable list |
| Synchronized wrapper và một đoạn nhiều bước | `synchronized(wrapper)` trọn đoạn, mọi đường truy cập cùng giao thức |
| Cập nhật/snapshot nhất quán nhiều trường hoặc key | Lock chung hoặc immutable aggregate + `AtomicReference` |
| Báo cáo xấp xỉ khi cập nhật đồng thời | Weakly consistent iteration, metric snapshot có giới hạn rõ |
| Transaction đi qua nhiều JVM/database | Giao thức đồng bộ/giao dịch ngoài phạm vi collection Java |

**Không có giải pháp tốt nhất cho mọi workload.** Lock bảo vệ invariant lớn nhưng có contention; `ConcurrentHashMap` có concurrency tốt theo key nhưng không cung cấp transaction nhiều key; copy-on-write tối ưu iteration read-heavy nhưng tốn copy khi ghi; queue giải quyết công việc theo phần tử nhưng không tự xử lý consistency giữa hai hệ thống.

## 11. Liveness và hiệu năng sau khi sửa race

Sửa đúng atomicity mới là bước đầu. Còn cần kiểm tra **liveness** và throughput:

- **Khóa quá rộng:** nhiều thread chờ dù làm việc trên dữ liệu độc lập. Có thể chia state thành các partition độc lập nếu invariant cho phép.
- **Deadlock:** hai thread lấy nhiều lock theo thứ tự ngược nhau. Dùng thứ tự khóa toàn cục và giới hạn đường gọi callback trong lock.
- **Compute callback chạy lâu:** `ConcurrentHashMap.compute` có thể chặn các update cạnh tranh liên quan trong lúc callback còn chạy; tránh I/O hoặc tính toán nặng trong callback.
- **Backpressure:** producer gửi công việc vào queue nhanh hơn consumer lấy ra, làm tăng backlog và latency. Chọn queue hữu hạn và chính sách từ chối/timeout khi phù hợp.
- **Copy-on-write nhiều ghi:** thường tạo chi phí copy và áp lực GC. Không dùng cho list bị sửa liên tục, dung lượng lớn, chỉ vì muốn iteration dễ.
- **CAS retry:** giải pháp nguyên tử không tự có latency thấp khi contention cao. Đo số retry và p95/p99 trước khi kết luận.
- **Không có snapshot atomic:** thống kê `size()`, duyệt map hoặc đọc nhiều key trong khi có update vẫn có thể cho ảnh quan sát không nhất quán; cần nêu rõ tính approximate.

Khi đo hiệu năng, luôn dùng workload có cùng kích thước, warm-up thích hợp, chờ task hoàn tất, theo dõi **throughput thành công**, latency, contention, CPU, GC và lỗi. **Một kết quả nhanh nhưng sai invariant không phải tối ưu hóa hợp lệ.**

## 12. Cách kiểm chứng thread-safe code của riêng bạn

1. **Viết invariant thành điều kiện cụ thể**: “mỗi job chỉ claim một lần”, “counter bằng số increment thành công”, “tổng A+B không đổi”.
2. **Liệt kê mọi đường đọc/ghi**, bao gồm iterator, callback, getter, snapshot, thread background và API bên ngoài có thể sửa backing collection.
3. **Chỉ rõ phạm vi atomic** của từng operation cần có hiệu lực như một lần duy nhất. Hỏi “giữa hai lời gọi method này, thread khác có thể chen vào không?”.
4. **Đọc đúng contract implementation**, không chỉ interface. `Map` thông thường, synchronized wrapper và `ConcurrentHashMap` có những bảo đảm khác nhau.
5. **Chọn API một bước hoặc khóa trọn chuỗi**: `putIfAbsent`, `compute`, `poll`; hoặc cùng lock/ownership/immutable snapshot khi invariant rộng hơn.
6. **Thiết kế test race có điều phối**, dùng `CountDownLatch`/`CyclicBarrier` để tạo interleaving nguy hiểm. Không dùng `Thread.sleep` như bằng chứng ordering.
7. **Chờ và kiểm tra mọi task** qua `Future.get()`/`join()`; nếu task trong executor thất bại mà không đọc Future, test có thể báo xanh sai.
8. **Kiểm tra trong lúc cập nhật**, không chỉ sau khi tất cả task kết thúc. Một snapshot nhiều field có thể sai tạm thời rồi đúng trở lại.
9. **Thử lỗi và cancellation**, đảm bảo `finally` nhả lock/đóng tài nguyên; queue không tăng không giới hạn và callback compute không mang side effect bất ngờ.
10. **Đo hiệu năng khi đã đúng**, xem cả contention, backlog, CPU/GC và p99 chứ không chỉ số operations/s.

Stress test có thể phát hiện regression nhưng không chứng minh vắng mặt mọi race. Lập luận trên **Java Memory Model, bất biến nghiệp vụ và contract của collection** là nền tảng.

## 13. Những hiểu lầm thường gặp

1. **“Collection thread-safe nên cả đoạn code sử dụng nó thread-safe.”** Sai khi đoạn đó chứa compound action nhiều lời gọi.
2. **“SynchronizedMap không thể có lost update.”** Wrapper bảo vệ operation theo contract; `get` và `put` tách rời vẫn có thể mất cập nhật.
3. **“Thấy key không tồn tại thì chắc chắn put sẽ là người đầu tiên.”** Một thread khác có thể chèn key sau check; dùng `putIfAbsent`.
4. **“ConcurrentHashMap giúp chuyển tiền giữa hai key atomic.”** `compute` atomic theo mapping/key, không tự gom hai key.
5. **“size() > 0 đảm bảo get(0) sẽ thành công.”** Thread khác có thể xóa phần tử sau lúc đọc size.
6. **“isEmpty() == false đảm bảo take() sẽ không chờ.”** Consumer khác có thể lấy hết queue; chọn `poll/take` theo contract.
7. **“Concurrent iterator cho snapshot nhất quán.”** Weakly consistent iterator không giữ một phiên bản cố định toàn map.
8. **“Iterator không ném ConcurrentModificationException nghĩa là an toàn.”** Fail-fast chỉ best effort; không phải synchronization.
9. **“CopyOnWriteArrayList thấy ngay phần tử mới trong iterator cũ.”** Iterator là snapshot; tạo iterator mới nếu muốn bản mới.
10. **“Atomic theo key là atomic với database và service ngoài.”** Side effect ngoài map không tự được transaction hóa.
11. **“Một HashMap được bọc synchronized rồi có thể cho caller khác dùng backing map trực tiếp.”** Bypass wrapper phá giao thức.
12. **“Dùng sleep để tránh thread chen vào.”** Sleep không lập happens-before hay mutual exclusion.
13. **“Không cần kiểm tra Future.”** Task có thể ném exception và kết quả test sau cùng không phản ánh đủ lỗi.
14. **“Chỉ sửa writer là đủ.”** Reader snapshot nhiều trường cũng phải tuân theo cơ chế nhất quán.

## 14. Câu hỏi tự kiểm tra

1. Hai thread làm `get("x")` rồi `put("x", old+1)` trên `Collections.synchronizedMap`. Có mất cập nhật không? **Có**, vì hai thao tác riêng không được gói thành một critical section.
2. API nào phù hợp để chỉ chèn `id -> "running"` khi chưa có mapping trong `ConcurrentHashMap`? **`putIfAbsent`**.
3. API nào phù hợp để tăng counter gắn với một key trong `ConcurrentHashMap`? **`compute` hoặc `merge`**, theo contract atomic của class.
4. Vì sao callback `compute` không nên gọi I/O chậm hoặc sửa các mapping khác cùng map? **Có thể giữ những update cạnh tranh phải đợi và vi phạm ràng buộc callback/tiến triển**.
5. Với `ConcurrentLinkedQueue`, tại sao `peek` rồi `poll` không bảo đảm lấy đúng phần tử vừa nhìn thấy? **Một consumer khác có thể lấy head ở giữa**.
6. Nếu hai consumer `poll` cùng lúc trên queue chỉ có một item, bao nhiêu consumer có thể nhận item đó? **Tối đa một**.
7. Cách duyệt `Collections.synchronizedList` khi có modifier khác dùng cùng wrapper? **Giữ `synchronized(list)` xuyên suốt iteration**.
8. `ConcurrentHashMap` iterator có phải ảnh snapshot atomic không? **Không**, là weakly consistent.
9. `CopyOnWriteArrayList` iterator tạo trước `list.add("B")` có thấy B không? **Không**, iterator đọc snapshot cũ.
10. Một invariant là `balanceA + balanceB == 100`. Vì sao hai lần `compute` riêng không bảo vệ invariant đối với observer? **Có khoảng trung gian giữa hai cập nhật**.
11. Vì sao test chỉ kiểm tra dữ liệu sau khi mọi thread kết thúc có thể bỏ sót lỗi? **Trong lúc chạy, reader có thể thấy trạng thái trung gian sai rồi sau cùng lại đúng**.
12. Điều gì phải đi cùng lời khẳng định “đây là collection thread-safe”? **Nêu rõ contract của từng operation, iterator, giới hạn compound action và invariant cần bảo vệ**.

## Kết luận

**Thread-safe collection bảo vệ những thao tác mà hợp đồng của chính collection bảo đảm; nó không tự biến một chuỗi thao tác nghiệp vụ thành một transaction.** Với compound action, hãy xác định invariant và phạm vi dữ liệu: dùng `ConcurrentHashMap.compute/putIfAbsent/replace` cho cập nhật **một key**, `poll` cho **claim một phần tử queue**, `synchronized(wrapper)` cho **một chuỗi trên synchronized collection**, và **lock chung/ownership/immutable snapshot** cho invariants nhiều trường hoặc key.

Cần luôn phân biệt thread safety, atomicity và snapshot consistency; đo độ đúng trước rồi mới đo hiệu năng.

## Tham khảo Java SE 17

- [Collections.synchronizedList / synchronizedMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html): yêu cầu đồng bộ ngoài khi iteration và hợp đồng synchronized wrapper.
- [ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): `compute`, `merge`, `putIfAbsent`, iterator weakly consistent.
- [ConcurrentLinkedQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html): `poll` và semantics concurrent queue.
- [BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): `take`, `offer`, `poll`, timeout.
- [CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html): snapshot iterators và write-copy tradeoff.
- [Java Language Specification 17, Chapter 17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): threads, locks, happens-before và Java Memory Model.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-010">Document thread-safety contract cho public API</a>

<details>
<summary>Xem chi tiết</summary>

# Document thread-safety contract cho public API trong Java

Một public API có thể trả về đúng giá trị khi được gọi tuần tự nhưng vẫn gây race condition, deadlock hoặc kết quả khó dự đoán khi nhiều thread gọi cùng lúc. Người dùng thư viện **không thể suy ra thread safety chỉ từ tên lớp, từ `synchronized` xuất hiện ở vài phương thức, hoặc từ việc mã chạy ổn trong kiểm thử đơn luồng**. Vì vậy, API công khai cần một **thread-safety contract**: lời cam kết có thể kiểm chứng về những kiểu truy cập đồng thời được hỗ trợ, kết quả quan sát được, nghĩa vụ của caller và giới hạn của chính implementation.

Một hợp đồng tốt trả lời rõ:

- **Ai được gọi đồng thời?** Nhiều thread có thể gọi cùng instance, cùng method, hay chỉ những phương thức nhất định?
- **Điều gì được bảo đảm?** Atomicity, visibility, ordering, snapshot consistency và các invariant nào?
- **Điều gì không được bảo đảm?** Thứ tự giữa các lời gọi concurrent, tính nguyên tử của chuỗi lời gọi, fairness hay thời gian phản hồi?
- **Caller có trách nhiệm gì?** Khóa bên ngoài, không sửa input trong khi API xử lý, không chia sẻ mutable object trả về, không tái nhập callback?
- **API có chờ, gọi callback, quản lý tài nguyên hoặc vòng đời không?** Có thể block, interrupt, đóng, hủy hoặc nhận kết quả sau khi đóng như thế nào?

Bài học này áp dụng cho **Java concurrency nói chung** (Java Memory Model, `java.lang`, `java.util.concurrent` và Javadoc). Không bàn về cơ chế concurrency riêng của Spring Framework.

## 1. Thread-safety contract là một phần của hợp đồng API

Một public API thường đã mô tả tham số, kiểu trả về, exception và tiền/hậu điều kiện. Nhưng trong chương trình đa luồng, điều đó chưa đủ.

Ví dụ:

```java
public interface Counter {
    long get();
    long incrementAndGet();
}
```

Đây chỉ là giao diện minh họa, chưa phải chương trình có `main`. Người dùng cần biết:

1. Có thể gọi `incrementAndGet()` từ hai thread cùng lúc không?
2. Nếu hai lời gọi bắt đầu khi bộ đếm bằng 5, chúng có trả về hai giá trị khác nhau (`6` và `7`) hay có thể cùng trả `6`?
3. `get()` đang chạy cùng một lần tăng được phép thấy giá trị nào?
4. Hai phép `get(); incrementAndGet();` có được coi là một giao dịch không?
5. Có thể dùng một `Counter` từ thread khác sau khi xây dựng xong mà cần thêm bước publication không?

Một tài liệu chỉ ghi "Thread-safe" không thể trả lời hết các câu hỏi đó. Hợp đồng nên nói **điểm tuyến tính hóa về mặt quan sát**, giới hạn của từng phương thức và điều không được cam kết.

**Thread safety là thuộc tính của một cách sử dụng cụ thể.** Một class có thể thread-safe với phương thức đọc và ghi đơn lẻ nhưng không đảm bảo giao dịch gồm nhiều lời gọi. Một class khác yêu cầu người gọi phải đặt tất cả thao tác dưới một lock. Không thể áp dụng một nhãn duy nhất cho mọi interaction mà không xét phạm vi cam kết.

## 2. Phân loại mức an toàn trước khi viết hợp đồng

Một bộ thuật ngữ thực hành giúp người viết nhất quán:

| Mức độ | Ý nghĩa đối với caller | Ví dụ cách mô tả |
| --- | --- | --- |
| **Immutable** | Sau khi xây dựng, trạng thái quan sát được không đổi; các thành phần bên trong cũng phải được xử lý đúng | "Instances are immutable and safe to share after construction." |
| **Thread-safe** | Các thao tác được chỉ định có thể được gọi đồng thời theo các cam kết ghi rõ | "All public methods support concurrent calls on the same instance." |
| **Conditionally thread-safe** | Chỉ an toàn nếu caller đáp ứng điều kiện về khóa, thứ tự, callback hoặc phạm vi | "Iteration requires the caller to hold the documented lock." |
| **Thread-compatible** | Nhiều thread có thể dùng nếu caller tự đồng bộ các truy cập xung đột | "Concurrent access requires external synchronization." |
| **Not thread-safe** | Không có bảo đảm truy cập đồng thời; thường giới hạn một owner | "Do not access an instance concurrently from multiple threads." |
| **Thread-confined** | Instance hoặc mutable state chỉ thuộc một thread trong một giai đoạn sở hữu | "Each instance must be accessed only from its creating thread." |

Đây là **thuật ngữ mô tả thiết kế**; Java SE không có một annotation chuẩn bắt buộc như `@ThreadSafe` cho mọi public API. Một số thư viện có annotation riêng, nhưng annotation chỉ hữu ích khi có định nghĩa và tài liệu đi kèm. Trong Javadoc, nên đặt lời cam kết rõ ràng bằng ngôn ngữ con người, kể cả khi dự án có annotation.

Cần phân biệt thêm:

- **Unmodifiable** không đồng nghĩa **immutable sâu (deeply immutable)**. `Collections.unmodifiableList(list)` có thể phản ánh sửa đổi của underlying list; một record chứa `ArrayList` vẫn có thể có state mutable.
- **Stateless** không tự chứng minh toàn bộ object graph an toàn. Phương thức không có field riêng vẫn có thể ghi một `static Map` hoặc sử dụng collaborator không thread-safe.
- **Synchronized từng method** chưa chứng minh tổ hợp nhiều method là atomic.
- **Có lock bên trong** không bảo đảm loại bỏ deadlock, reentrancy bất lợi hay starvation.

## 3. Ghi rõ đối tượng và phạm vi được bảo vệ

Hợp đồng cần chỉ rõ đang nói đến **một instance**, **nhiều instance có chung tài nguyên**, hay **trạng thái process-wide**.

Ví dụ hai instance `Counter` giữ hai `AtomicLong` riêng thì mỗi instance có thể thread-safe độc lập. Nhưng nếu cả hai instance dùng chung một mutable static cache, việc lock theo `this` ở mỗi instance không thể tự bảo vệ cache chung.

Một đoạn Javadoc mức class có thể viết:

```java
/**
 * A thread-safe counter.
 *
 * <p>All public methods may be called concurrently on the same instance.
 * Each increment is atomic, and get() observes a value from one atomic read.
 * Separate Counter instances have independent state.
 *
 * <p>A sequence such as get() followed by incrementAndGet()
 * is not an atomic transaction.
 */
public final class Counter {
    // Implementation goes here.
}
```

Đây là **mẫu Javadoc minh họa**, không phải class độc lập để chạy. Mô tả class cho người đọc một nền chung; từng method vẫn cần diễn đạt những ngoại lệ hoặc bảo đảm hẹp hơn.

Nếu API dùng shared global resource, hãy ghi rõ. Ví dụ: "All instances share one cache; changes from one instance may be visible through another." Điều này quan trọng hơn việc chỉ viết "instances are thread-safe".

## 4. Những thuộc tính cần diễn đạt riêng

### Atomicity: thao tác logic có không bị xen giữa?

Nếu `incrementAndGet()` là atomic, hai thread cùng tăng một bộ đếm ban đầu 5 thì kết quả sẽ tương ứng hai bước tăng riêng biệt, cho dù thứ tự caller nhận kết quả phụ thuộc scheduling. Nhưng chuỗi:

```java
if (counter.get() < limit) {
    counter.incrementAndGet();
}
```

**không tự atomic**. Thread khác có thể thay đổi counter giữa `get()` và `incrementAndGet()`. Nếu có invariant "không vượt giới hạn", API nên cung cấp một phương thức như `tryIncrementWithinLimit(limit)` với bảo đảm atomic trên cả kiểm tra và thay đổi.

Một thread-safe class cần phân biệt **method atomicity** với **operation composition**.

### Visibility: lời gọi khác có nhìn thấy cập nhật?

Một contract có thể bảo đảm tác động của operation đã hoàn thành sẽ được các thao tác đồng bộ tiếp theo quan sát theo ngữ nghĩa của nó. Nếu chỉ ghi "synchronized" mà không giải thích đối tượng bảo vệ, caller vẫn có thể không biết ý nghĩa liên thread.

Java Memory Model định nghĩa các quan hệ **happens-before**. Ví dụ `synchronized` trên cùng monitor hoặc thao tác `AtomicLong` có semantics tương ứng giúp bảo đảm visibility, ordering. Tuy nhiên, contract nên ghi theo hành vi API mà caller cần, thay vì buộc caller biết implementation dùng loại lock nào.

### Ordering: kết quả đồng thời có thứ tự ra sao?

Một `ConcurrentHashMap` cho phép nhiều thread cập nhật, nhưng không cam kết iterator là một snapshot đóng băng hoặc duyệt key theo thứ tự chèn. Với một queue FIFO, cần mô tả FIFO được áp dụng cho **những lần enqueue có thứ tự xác định**, chứ không hứa thứ tự "theo giờ hệ thống" giữa hai producer chạy concurrent.

### Snapshot consistency

`snapshot()` có thể có nhiều nghĩa:

- Snapshot **nhất quán tại một điểm** của collection dưới cùng lock.
- Một view **unmodifiable** của dữ liệu vẫn đang thay đổi.
- Một iterator **weakly consistent**, có thể phản ánh một phần cập nhật concurrent theo hợp đồng.
- Một ảnh chụp chỉ gồm một số field, trong khi field khác có thể đổi độc lập.

Tên `snapshot` không thay thế cam kết về thời điểm, nội dung và tính bất biến của kết quả.

### Liveness và blocking

Một API được bảo vệ đúng dữ liệu vẫn có thể block vô hạn khi chờ tài nguyên. Đối với public API, hãy nói phương thức có thể block trong trường hợp nào, có timeout không, có hỗ trợ interruption/cancellation không, có giữ lock trong callback không và trạng thái có thể thay đổi khi thoát do exception không.

**Không cam kết lock-free/wait-free** chỉ vì dùng `AtomicInteger` hoặc concurrent collection ở một phần code: mức tiến triển phải xét **toàn operation**, các vòng retry và những lời gọi phụ thuộc.

## 5. Linearizability: ngôn ngữ chính xác cho nhiều API concurrent

Một operation được gọi là **linearizable** nếu mỗi lời gọi có thể được coi như có một thời điểm tác động đơn lẻ nằm giữa **lúc bắt đầu** và **lúc kết thúc** lời gọi, và thứ tự này tôn trọng thứ tự thực của các operation không chồng lấn. Đây là thuộc tính mạnh và rất hữu ích cho counter, map operation, atomic register và queue.

Ví dụ hai lần `incrementAndGet()` trên cùng `AtomicLong` là các thao tác atomic; nếu chúng trả `6` và `7`, có thể giải thích như một thứ tự tuần tự phù hợp, không cần chỉ định thread nào đi trước. Nếu một lời gọi kết thúc hoàn toàn trước lời gọi tiếp theo bắt đầu, thứ tự tuyến tính hóa phải phù hợp với quan hệ đó.

Cần viết contract đúng mức:

- "Each `incrementAndGet()` is atomic" là cam kết từng operation.
- "A call to `get()` returns the value from one atomic read" không có nghĩa hai lần `get` liên tiếp tạo snapshot cho cả chuỗi.
- "Concurrent calls may complete in either order" giải thích điều không có ordering guarantee.
- "The final count equals the number of completed successful increments" cần được hiểu khi **không còn increment concurrent hoặc các update khác** và tất cả task liên quan đã hoàn tất.

Không phải mọi API concurrent đều cam kết linearizability cho mọi phương thức. Một báo cáo aggregate từ nhiều counter độc lập có thể chỉ là trạng thái **eventually consistent** hoặc **weakly consistent** theo tài liệu. Hãy dùng đúng từ; "thread-safe" không tự đồng nghĩa "mọi read là global snapshot linearizable".

## 6. Ví dụ đầy đủ: public counter với contract Javadoc

Lưu thành `AtomicCounterContractDemo.java` (Java 17+):

```java
import java.util.concurrent.atomic.AtomicLong;

public class AtomicCounterContractDemo {

    /**
     * A counter safe for concurrent use by multiple threads.
     *
     * <p>Each incrementAndGet() and get() is an atomic operation.
     * Each completed increment contributes exactly one to the value.
     * Calls from different threads may be ordered differently.
     *
     * <p>Multiple calls do not form an atomic transaction.
     * In particular, get() followed by incrementAndGet() does not
     * reserve a value or enforce a maximum.
     */
    public static final class Counter {
        private final AtomicLong value = new AtomicLong();

        /**
         * Atomically increments the value and returns the updated value.
         * Concurrent invocations may return in any order to callers.
         *
         * @return the value after this increment
         */
        public long incrementAndGet() {
            return value.incrementAndGet();
        }

        /**
         * Returns one atomic observation of the current counter.
         * The value may change immediately after this method returns.
         *
         * @return the observed value
         */
        public long get() {
            return value.get();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();

        Runnable increment10k = () -> {
            for (int i = 0; i < 10_000; i++) {
                counter.incrementAndGet();
            }
        };

        Thread first = new Thread(increment10k);
        Thread second = new Thread(increment10k);

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("Total: " + counter.get());
    }
}
```

Biên dịch/chạy:

```bash
javac AtomicCounterContractDemo.java
java AtomicCounterContractDemo
```

Stdout chính xác:

```text
Total: 20000
```

**Phân tích contract:**

1. Mức class khẳng định nhiều thread có thể dùng **cùng một instance**.
2. `AtomicLong.incrementAndGet()` làm mỗi phép tăng atomic; chương trình không bị mất increment dù hai worker xen kẽ.
3. Không cam kết thread nào nhận một giá trị cụ thể khi chạy concurrent. Thứ tự thực hiện và nhận kết quả do scheduling.
4. `Thread.join()` thành công trước khi main đọc tổng, nên tại thời điểm đó cả hai worker đã hoàn tất. Việc in 20.000 có căn cứ từ number of increments và API atomic, không dựa vào timing.
5. `get()` đơn lẻ thread-safe; một tổ hợp `get` rồi `incrementAndGet` **không thành transaction**. Javadoc nhấn mạnh giới hạn để caller không tự chế kiểm tra giới hạn sai.

Không cần ghi trong Javadoc rằng implementation sử dụng `AtomicLong` nếu đó không phải phần giao diện mà caller được dựa vào. Quan trọng hơn là cam kết atomicity/ordering còn đúng nếu sau này thay implementation bằng lock thích hợp.

## 7. Hợp đồng của từng phương thức phải khớp logic nghiệp vụ

Giả sử một API quản lý danh sách entry có giới hạn dung lượng. Hai method riêng:

```java
int size();
void add(String entry);
```

dù đều `synchronized` **không tự bảo đảm** client thực hiện được:

```java
if (registry.size() < capacity) {
    registry.add(entry);
}
```

Nếu hai caller cùng thấy size bằng `capacity - 1`, cả hai có thể cố thêm. Một public API tốt nên trực tiếp biểu diễn bất biến:

```java
boolean tryAdd(String entry);
```

Trong tài liệu, ghi rõ:

- `tryAdd` **atomically checks capacity and adds** trong cùng operation.
- Trả `true` chỉ khi element được thêm thành công; `false` khi đã đầy ở điểm kiểm tra atomic.
- Hai caller cạnh tranh chỗ cuối có thể nhận kết quả khác nhau; không hứa caller khởi động trước nhất định thắng.
- Không thể dùng `size()` để "giữ chỗ". `size` chỉ quan sát tại một thời điểm, và giá trị có thể đổi ngay khi method trả về.

Điều này chuyển tính đúng đắn từ một chuỗi khó đồng bộ ở phía client vào một operation có hợp đồng rõ ràng.

## 8. Ví dụ đầy đủ: class thread-safe nhưng iterator/snapshot cũng phải được mô tả

Lưu thành `RegistryContractDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RegistryContractDemo {

    /**
     * A bounded, thread-safe collection of String entries.
     *
     * <p>All methods may be called concurrently on the same instance.
     * Each tryAdd() atomically checks the bound and adds an entry.
     * snapshot() returns an unmodifiable, independent list of values
     * captured while holding the same lock as the writers.
     *
     * <p>Entries may appear in either order when calls overlap.
     * Calling size() and then tryAdd() is not an atomic transaction.
     * Entries are immutable String objects.
     */
    public static final class Registry {
        private final int capacity;
        private final List<String> entries = new ArrayList<>();

        public Registry(int capacity) {
            if (capacity < 0) {
                throw new IllegalArgumentException("capacity < 0");
            }
            this.capacity = capacity;
        }

        /**
         * Atomically inserts a non-null entry if room remains.
         *
         * @return true if inserted, false if the registry is full
         * @throws NullPointerException if entry is null
         */
        public synchronized boolean tryAdd(String entry) {
            Objects.requireNonNull(entry, "entry");
            if (entries.size() >= capacity) {
                return false;
            }
            entries.add(entry);
            return true;
        }

        /**
         * Returns one atomic size observation.
         * The size may change immediately after this method returns.
         */
        public synchronized int size() {
            return entries.size();
        }

        /**
         * Returns an unmodifiable snapshot in insertion order as
         * observed while holding the lock.
         *
         * <p>The returned list is not a live view of this registry.
         * Later inserts do not change this snapshot.
         */
        public synchronized List<String> snapshot() {
            return List.copyOf(entries);
        }
    }

    public static void main(String[] args) throws Exception {
        Registry registry = new Registry(2_000);
        ExecutorService pool = Executors.newFixedThreadPool(4);

        try {
            @SuppressWarnings("unchecked")
            Future<?>[] jobs = new Future<?>[4];

            for (int worker = 0; worker < 4; worker++) {
                final int id = worker;
                jobs[worker] = pool.submit(() -> {
                    for (int i = 0; i < 500; i++) {
                        if (!registry.tryAdd(id + "-" + i)) {
                            throw new IllegalStateException("Unexpected full");
                        }
                    }
                });
            }

            for (Future<?> job : jobs) {
                job.get();
            }

            List<String> snapshot = registry.snapshot();
            System.out.println("Entries: " + registry.size());
            System.out.println("Snapshot size: " + snapshot.size());
            System.out.println("Can add more: " + registry.tryAdd("extra"));
        } finally {
            pool.shutdown();
        }
    }
}
```

Chạy:

```bash
javac RegistryContractDemo.java
java RegistryContractDemo
```

Stdout trên đường chạy bình thường:

```text
Entries: 2000
Snapshot size: 2000
Can add more: false
```

**Vì sao contract đáng tin?**

- `tryAdd` thực hiện cả kiểm tra capacity và thao tác `add` trong cùng monitor (`this`). Khi nhiều caller tranh chấp vị trí cuối, chỉ một caller có thể thêm, các caller còn lại thấy cập nhật.
- `size` và `snapshot` dùng **cùng monitor**, do đó thấy cấu trúc list nhất quán với thao tác `tryAdd`. Không để reference tới mutable `ArrayList` nội bộ thoát khỏi class.
- `List.copyOf` tạo một danh sách không cho chỉnh qua API và độc lập với những thêm mới về sau; trong ví dụ `String` immutable nên không có mutable element cần đồng bộ thêm.
- Bốn worker gửi 2.000 lần thêm rồi `Future.get()` được gọi cho cả bốn, do đó main không in số lượng trước khi các task thành công. Thứ tự chuỗi theo worker là không xác định, nhưng số lượng là xác định.
- `snapshot` có insertion order tại một thời điểm được khóa, song **không cam kết** các lời gọi của worker concurrent sẽ đi theo số id hoặc thứ tự bắt đầu thread.
- `size()` đọc hiện thời và `tryAdd()` là hai operation khác nhau; một client tự ghép chúng không thể suy ra có transaction.

**Thiết kế tài liệu phải nói đến ownership và mutable return value.** Nếu `snapshot()` trả về chính `entries` hoặc trả một unmodifiable *view* trên `entries`, lời hứa "independent snapshot" sẽ sai. Implementation và Javadoc phải khớp nhau.

## 9. Hợp đồng thread-safe khác với immutable và read-only view

So sánh ba API trả dữ liệu:

```java
List<String> directView();       // Trả reference mutable?
List<String> unmodifiableView(); // View phản ánh thay đổi của nguồn?
List<String> snapshot();         // Bản sao tại một thời điểm?
```

Đây là chữ ký minh họa, chưa nêu implementation. Nếu API trả một tham chiếu mutable mà caller có thể tự sửa, không thể tiếp tục khẳng định class giữ được invariant nội bộ trừ khi có cơ chế bảo vệ tương ứng.

Một `Collections.unmodifiableList(source)` chỉ chặn mutation **qua view**; nếu `source` còn được sửa ở nơi khác thì view cũng phản ánh thay đổi. Một `List.copyOf(source)` tạo list không chỉnh sửa được, nhưng việc copy chính `source` vẫn cần diễn ra trong điều kiện hợp lệ nếu source đang bị writer khác cập nhật.

Tài liệu nên ghi:

- Kết quả là **live view** hay **snapshot**?
- Kết quả có **unmodifiable** không? Khi nào nội dung có thể thay đổi?
- Nếu phần tử là object mutable, caller có được sửa phần tử hay không?
- Caller có được giữ kết quả và sử dụng trên thread khác không, và điều kiện safe publication là gì?
- API có cam kết ordering hoặc consistency khi lấy nhiều snapshot liên tiếp không?

Một collection thread-safe bao quanh mutable values **không tự làm mọi value thread-safe**. Ví dụ `ConcurrentHashMap<String, ArrayList<String>>` bảo vệ thao tác map theo hợp đồng nhưng các `ArrayList` bên trong vẫn cần chính sách riêng.

## 10. Compound actions: điều cần cảnh báo rõ nhất trong tài liệu

Ngay cả `ConcurrentHashMap` cũng cần phân biệt giữa **một phương thức atomic** với **chuỗi thao tác client tự ghép**.

```java
// Có race logic nếu hai thread cùng cập nhật một key.
Integer before = counts.get(key);
counts.put(key, before == null ? 1 : before + 1);
```

Với `ConcurrentHashMap`, từng thao tác `get` và `put` được hỗ trợ concurrent, nhưng **get-then-put** không tự là một transaction. Tài liệu API nên chỉ ra thao tác dành cho yêu cầu này:

```java
counts.merge(key, 1, Integer::sum);
```

`merge` là operation phối hợp theo key được `ConcurrentHashMap` bảo đảm theo hợp đồng. Tuy vậy, nếu cập nhật cần invariant xuyên **nhiều key**, `merge` trên một key không giải quyết transaction nhiều key.

Với listener list, thao tác "kiểm tra chưa đăng ký rồi đăng ký" cũng cần semantics rõ. Với registry giới hạn dung lượng, `tryAdd` phải kiểm tra và thêm trong cùng critical section. Với tài khoản, "đủ số dư rồi trừ" phải là một operation được bảo vệ như một đơn vị.

Đề nghị dùng cụm diễn đạt chính xác:

> "Individual operations are safe for concurrent calls. Client-composed sequences are not atomic unless explicitly documented."

Nếu public API cho phép callback hoặc iterator, cần nói cả những operation **vượt qua ranh giới của một lần gọi method**.

## 11. Iterator và stream: caller có cần khóa ngoài không?

Một phương thức trả `Iterator<E>`, `Stream<E>` hoặc live view có thể kéo việc đọc dữ liệu ra **sau** khi method tạo iterator đã trả về. Nếu lock chỉ được giữ trong method tạo iterator, nhiều bước duyệt tiếp theo vẫn có thể xảy ra khi writer đang sửa.

Ví dụ `Collections.synchronizedList(...)` cho phép đồng bộ nhiều operation đơn lẻ qua wrapper, nhưng caller thường **phải khóa thủ công trên wrapper trong suốt iteration** khi có writer concurrent:

```java
List<String> list =
        Collections.synchronizedList(new ArrayList<>());

synchronized (list) {
    for (String value : list) {
        use(value);
    }
}
```

Đây là đoạn minh họa; cần import `java.util.List`, `java.util.ArrayList`, `java.util.Collections`, và định nghĩa `use`. Nếu tài liệu API chỉ ghi "synchronized list" mà không nêu yêu cầu khóa khi iteration, client có thể dùng sai.

Một API với `ConcurrentHashMap` hoặc `CopyOnWriteArrayList` có **kiểu iteration khác**:

- `ConcurrentHashMap`: iterator weakly consistent; không phải snapshot atomic của toàn map.
- `CopyOnWriteArrayList`: iterator giữ một snapshot mảng tại lúc tạo; các mutation sau đó không xuất hiện trong lần duyệt này, và iterator không hỗ trợ `remove`.
- `Collections.synchronizedList`: phải đọc hợp đồng về manual synchronization khi iteration.

**Documentation phải nêu semantics thực sự của kiểu kết quả**, không chỉ tên implementation class. Nếu muốn người dùng khỏi quản lý lock, cân nhắc cung cấp `snapshot()` hoặc phương thức xử lý có scope được kiểm soát.

## 12. Callback và listener: điểm khó trong public contract

Một API có thể an toàn trong lúc cập nhật collection nội bộ nhưng vẫn gọi **mã của client** trong callback. Lúc ấy phải tài liệu hóa:

1. Callback chạy trên thread nào: caller thread, một worker riêng, hay một executor được cung cấp?
2. Có thể **nhiều callback chạy concurrent** trên cùng listener object không?
3. Callback có được gọi lại các method của API (reentrancy) không?
4. Có giữ lock nội bộ khi gọi callback không?
5. Nếu callback ném exception, xử lý tiếp các listener còn lại như thế nào?
6. Khi `removeListener` chạy concurrent với `publish`, listener đã bị xóa có thể vẫn nhận event đang diễn ra không?
7. Có bảo đảm ordering, exactly-once hoặc delivery trong trường hợp lỗi/hủy không?

Ví dụ API dùng `CopyOnWriteArrayList` và gọi callback ngay trên thread của người gọi. Đây là class minh họa đầy đủ (cần đặt vào file `ListenerRegistry.java` nếu sử dụng):

```java
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A thread-safe registry of listeners.
 *
 * <p>addListener, removeListener and publish may be called concurrently.
 * A publish call invokes callbacks synchronously on its caller's thread,
 * in the list order captured when its iteration begins.
 *
 * <p>Two concurrent publish calls may invoke the same listener
 * concurrently. A remove does not cancel a callback already included
 * in an in-progress publish snapshot.
 *
 * <p>Callbacks execute without an internal registry lock being held.
 * If a callback throws a RuntimeException, it propagates to the
 * publish caller and later callbacks in that publication are skipped.
 */
public final class ListenerRegistry {
    private final CopyOnWriteArrayList<Consumer<String>> listeners =
            new CopyOnWriteArrayList<>();

    public void addListener(Consumer<String> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    public void publish(String event) {
        for (Consumer<String> listener : listeners) {
            listener.accept(event);
        }
    }
}
```

**Vì sao cách ghi này quan trọng?** `CopyOnWriteArrayList` bảo vệ cấu trúc danh sách và iterator snapshot, nhưng **không tự bảo vệ mã callback**. Hai caller cùng gọi `publish` có thể khiến cùng listener nhận hai callback đồng thời. Nếu listener tự sửa state thường, nó phải tự bảo vệ state hoặc API cần thay đổi cách dispatch.

Ví dụ này không có `main` nên không có stdout. Nó biên dịch độc lập với JDK 17+, và thể hiện một contract cụ thể: callbacks trên caller thread, có thể đồng thời, không có lock nội bộ lúc gọi callback, exception lan ra và dừng lượt publish đó. Nếu đổi implementation sang executor async, hợp đồng về thread, ordering, completion và lỗi cũng phải đổi.

**Lưu ý:** Tự gọi mã client trong một vùng `synchronized` có thể kéo dài thời gian giữ lock, tạo reentrancy khó phân tích hoặc deadlock khi callback gọi ngược vào một API khác. Cần chủ động thiết kế thời điểm callback được gọi và công bố ràng buộc đó.

## 13. Thread ownership, aliasing và tham số mutable

Một public API có thể nhận `List<T>`, `byte[]` hoặc một object mutable từ caller. Hợp đồng phải làm rõ API:

- **Copy ngay** để sở hữu dữ liệu riêng?
- **Giữ reference** và dùng về sau?
- **Chuyển quyền sở hữu** trong một pipeline producer–consumer?
- **Cho phép caller sửa input** trong khi method đang chạy hay không?
- Có đồng bộ với những thread khác đang sửa input không?

Ví dụ:

```java
public final class TaskBatch {
    private final List<String> tasks;

    public TaskBatch(List<String> tasks) {
        this.tasks = List.copyOf(tasks);
    }

    public List<String> tasks() {
        return tasks;
    }
}
```

Class này dùng `List` nên cần import `java.util.List` nếu viết thành file độc lập. Constructor tạo snapshot của danh sách đầu vào (với những `String` immutable), sau đó không sửa. Nhưng **caller không được đồng thời sửa một ordinary ArrayList đầu vào trong lúc constructor đang copy** nếu muốn kết quả có ý nghĩa. Một contract nên ghi rằng constructor yêu cầu đầu vào ổn định trong quá trình copy và cấm null theo hợp đồng `List.copyOf`.

Nếu không copy mà giữ raw reference, Javadoc cần cảnh báo caller phải duy trì bất biến và quy tắc đồng bộ cho alias đó. Đặt field `final` chỉ ngăn gán lại tham chiếu, không làm nội dung mutable an toàn.

### Safe publication

Một object được xây dựng đúng và mang immutable state giúp giảm nhiều race, nhưng vẫn nên nói **caller phải công bố instance đúng cách** giữa các thread khi thiết kế có data chia sẻ phức tạp. Các cơ chế như thread start, volatile reference, lock, concurrent queue hoặc completion primitive có quy tắc happens-before.

Với immutable class được xây dựng đúng, các `final` fields có bảo đảm đặc biệt theo JMM nếu constructor không làm `this` thoát quá sớm. Tuy vậy, chúng không "đóng băng" một mutable object graph được đưa ra ngoài.

## 14. Blocking, timeout, interruption và cancellation thuộc public contract

Một API thread-safe vẫn có thể **block**, và tính đúng của caller phụ thuộc cách method trả về hay dừng.

Ví dụ nếu `take()` chờ queue có phần tử, tài liệu cần nêu:

- `take()` **có thể block vô thời hạn** khi queue rỗng.
- Phương thức có phản hồi `Thread.interrupt()` bằng `InterruptedException` không?
- API có overload timeout không; timeout tính theo đơn vị nào?
- Khi timeout xảy ra, có item nào đã bị tiêu thụ không?
- Khi cancel một task đang chạy, state đã thay đổi một phần có được rollback không?
- Khi thread bị ngắt, caller có cần khôi phục interrupt flag không?

Không được hứa "cancellation luôn dừng ngay". `Future.cancel(true)` thường chỉ gửi yêu cầu interruption khi có thể; task phải hợp tác với cancellation, và thao tác I/O có hợp đồng riêng.

Một đoạn Javadoc cho phương thức blocking nên có dạng:

```java
/**
 * Waits for the next message.
 *
 * <p>Concurrent producers and consumers are supported.
 * This method may block while the queue is empty.
 *
 * @return the next available message
 * @throws InterruptedException if interrupted while waiting
 */
public String take() throws InterruptedException {
    return queue.take();
}
```

Đây là **một method minh họa** trong class giả định có field `queue` kiểu `BlockingQueue<String>`; không phải chương trình độc lập. Hợp đồng về `InterruptedException` đến từ API được dùng bên trong. Nếu implementation sau này đổi sang busy-wait hoặc nuốt interruption, Javadoc không còn đúng.

**Timeout** và **fairness** là hai khái niệm khác nhau. Một phương thức có timeout chỉ giới hạn thời gian đợi theo hợp đồng, không cam kết người gọi nào thắng tranh chấp hoặc được xử lý theo thứ tự đăng ký.

## 15. Close và lifecycle: thread-safe không đồng nghĩa dùng được mọi lúc

Khi một public API có `close()`, `shutdown()`, `start()` hoặc `stop()`, cần định nghĩa state machine đủ rõ.

Ví dụ:

```text
NEW -> OPEN -> CLOSING -> CLOSED
```

Đây chỉ là một mô hình minh họa; không phải API nào cũng có đủ bốn state.

Các câu hỏi phải tài liệu hóa:

- Có được gọi `close()` đồng thời từ nhiều thread không, có **idempotent** không?
- Khi `close` bắt đầu, một `submit` vừa chạy song song được chấp nhận hay từ chối dựa vào **điểm quyết định atomic** nào?
- Các task đã nhận trước `close` có hoàn thành không? Có bị hủy không?
- Thread đang block trong `take` hoặc `await` được đánh thức thế nào?
- `close` tự block đợi mọi task hay chỉ gửi tín hiệu?
- Sau khi đóng, `getSnapshot` có còn dùng được? Nếu không, ném exception nào?
- Callback đã bắt đầu trước khi đóng có thể tiếp tục sau khi `close` trả về không?

**Không suy ra semantics từ tên method.** `ExecutorService.shutdown()` chẳng hạn ngừng nhận task mới nhưng thường cho task đã gửi chạy đến hoàn tất; `shutdownNow()` cố gắng dừng các task đang chạy và trả lại các task chưa bắt đầu, không bảo đảm tất cả thread dừng ngay. Nếu viết wrapper cho executor, tài liệu của wrapper phải nói chính sách lifecycle riêng của nó.

Một contract tránh deadlock phải xác định client có được gọi `close` từ callback/worker do chính API quản lý hay không. Một `close` chờ tất cả worker kết thúc có thể deadlock nếu gọi từ chính worker cần được đợi.

## 16. Điều kiện external synchronization cần được viết chính xác

Với một public API **không tự thread-safe**, chỉ nói "synchronize access externally" vẫn thiếu thông tin. Caller cần biết **khóa nào** và **phạm vi nào**.

Mẫu hợp đồng tốt:

> "Instances are not thread-safe. If an instance is accessed from multiple threads, the caller must synchronize all accesses to its mutable state, including reads, writes, iteration and compound operations, using the same lock. No mutable reference to internal state may be modified outside that lock."

Nếu class cung cấp một lock công khai, phải cân nhắc rủi ro cho client khi dùng lock đó cho logic khác. Nếu object nội bộ không bị lộ, dễ hơn là class tự bọc mọi thao tác bằng một lock riêng hoặc cung cấp các operation atomic mức nghiệp vụ.

Hãy tránh hứa "thread-safe if caller synchronizes" mà không định nghĩa cách phối hợp với những thread writer nội bộ do class tự tạo: caller có thể khóa trên `this` trong khi implementation dùng một lock riêng, khiến khóa ngoài không thực sự đồng bộ với worker.

### Điều kiện safe publication không phải khóa cho mọi mutation

Nếu object chỉ được khởi tạo rồi **không bao giờ thay đổi**, nó có thể được chia sẻ để đọc sau publication đúng. Nhưng nếu caller sau đó gọi một setter, các read khác cần quy tắc thêm. Hợp đồng nên phân biệt "safely published immutable state" và "concurrent mutation supported".

## 17. Memory consistency effects: mô tả ở mức hành vi

JMM dùng quan hệ **happens-before** để xác định các đảm bảo về visibility và ordering. Một class public không nhất thiết phải phơi bày chi tiết internal locking, nhưng tài liệu nên dựa vào các hiệu ứng memory consistency đã được bảo đảm bởi Java:

| Cơ chế | Liên hệ với public contract |
| --- | --- |
| `synchronized` cùng monitor | Unlock happens-before lần lock tiếp theo trên monitor đó |
| `volatile` write/read đúng giao thức | Volatile write happens-before volatile read tiếp theo phù hợp với cùng biến |
| `AtomicLong` operations | Có ngữ nghĩa atomic/volatile tương ứng cho field giá trị |
| `Thread.start()` | Những hành động trước `start()` happens-before hành động trong thread mới |
| `Thread.join()` thành công | Hành động của worker happens-before hành động của caller sau khi join |
| `ExecutorService.submit` và `Future.get` | Hành động trước submit HB task; task actions HB caller sau get |
| `BlockingQueue` | Hành động trước khi đưa item vào queue HB hành động sau khi consumer lấy item ra |
| `CountDownLatch` | Hành động trước `countDown()` HB hành động sau `await()` trả về phù hợp |

**Cạnh happens-before khác với scheduling guarantee.** HB giúp giải thích visibility/order. Nó không hứa task chạy trong một số mili giây xác định hay sẽ không bị starvation. Một API có thể đúng về memory safety mà vẫn cần timeout/backpressure và chính sách liveness.

Đối với public API, nên chuyển những cơ chế này thành điều caller cần: "Sau khi `awaitCompletion()` trả về bình thường, các kết quả của những tác vụ đã hoàn tất trước tín hiệu được công bố an toàn cho thread gọi", **nếu implementation thực sự bảo đảm** điều đó theo cơ chế sử dụng.

## 18. Error handling và exception khi gọi đồng thời

Khi một method ném exception, caller cần biết **tác động lên state**:

- Nếu `tryAdd` ném `NullPointerException` do input null, operation có để lại thay đổi gì không?
- Nếu callback ném exception, những listener đã được gọi trước đó có tạo side effect rồi không?
- Nếu một operation ghi qua nhiều tài nguyên và bị interrupt, có **partial commit** không?
- Nếu có retry, operation có **idempotent** theo key hoặc request id không?
- Exception từ worker có được lưu vào future hay bị bỏ qua?

"Thread-safe" không đồng nghĩa "mọi operation đều rollback khi có lỗi". **Atomicity** về tác động đồng thời và **exception safety** là hai thuộc tính cần tách riêng. Một API có thể linearizable trên các operation thành công nhưng vẫn cần tài liệu trạng thái khi operation thất bại.

Ví dụ trong `RegistryContractDemo`, `Objects.requireNonNull(entry)` chạy trước kiểm tra/thêm; input null ném `NullPointerException` mà không thêm item. Tài liệu method xác nhận điều này. Với method có callback trước khi commit, yêu cầu khác và cần cam kết rõ.

Nếu exception xảy ra trong task được gửi qua `ExecutorService.submit`, `Future.get()` thường phản ánh thất bại thông qua `ExecutionException`. Một API bọc future không nên âm thầm biến thất bại thành "completed successfully".

## 19. Tài liệu interface và extension points: ai chịu trách nhiệm thread safety?

Khi public API là interface hoặc abstract class được người khác implement, contract phải chỉ ra:

- **Caller có thể dựa vào điều gì** khi sử dụng một implementation bất kỳ?
- **Implementer bắt buộc bảo đảm điều gì** để tuân thủ interface?
- Những default methods có giả định về thread safety của primitive operations không?
- Callback hoặc extension hooks có được gọi concurrent không?
- Một implementation được phép chọn locking strategy khác nhưng vẫn giữ semantics không?

Nếu interface nói mọi implementation phải thread-safe, một implementation dùng `ArrayList` ordinary mà không đồng bộ sẽ **vi phạm interface contract**, dù code biên dịch hợp lệ.

Có thể dùng Javadoc `@implSpec` để mô tả yêu cầu dành cho implementation khi thích hợp; `@apiNote` để bổ sung hướng dẫn sử dụng và `@implNote` cho lưu ý triển khai. Tuy nhiên, các bảo đảm caller phải biết nên xuất hiện rõ trong **Javadoc công khai chính**, không bị giấu trong chú thích riêng về implementation.

Một mẫu mô tả trách nhiệm:

```java
/**
 * Concurrent key-value store.
 *
 * <p>Implementations must support concurrent invocations of get()
 * and put() on the same instance.
 * Each operation must be atomic for the specified key.
 * A read-modify-write sequence spanning two calls is not atomic.
 *
 * @param <K> key type
 * @param <V> value type
 */
public interface ConcurrentStore<K, V> {
    V get(K key);
    V put(K key, V value);
}
```

Đây là ví dụ hợp đồng interface, không có `main`. Một API thực tế cũng phải xác định nghĩa của null, thông báo lỗi, ordering và phạm vi key tương đương. Tránh hứa atomicity cấp key mà implementation không bảo đảm hoặc không có cách kiểm chứng.

## 20. Chọn vị trí viết và cấu trúc Javadoc

Nên viết thread-safety contract ở **cấp class/interface**, sau đó bổ sung ở method khi có khác biệt.

| Vị trí | Nên mô tả |
| --- | --- |
| Javadoc của class/interface | Phân loại mức thread safety; phạm vi instance/global; các bảo đảm chung; điều kiện caller |
| Constructor/factory | Input ownership; safe publication; có copy hay giữ reference; instance đã usable ngay chưa |
| Method state-changing | Atomicity; ordering; kết quả tranh chấp concurrent; lỗi; điều kiện sau khi method trả về |
| Method read/snapshot/iterator | Snapshot hay live view; consistency; ordering; mutable elements; có cần lock ngoài không |
| Callback/subscription | Thread gọi callback; concurrent/reentrant; ordering; exception; removal semantics |
| Blocking/lifecycle method | Blocking/timeout/interruption; close/cancel/stop; idempotency và công việc đang chạy |
| Extension point | Nghĩa vụ implementer; được gọi đồng thời hay chỉ một thread; cách propagate errors |

Một mẫu câu cấp class ngắn và hữu ích:

> "Instances are safe for concurrent use. Each method performs one atomic operation as documented. No atomicity is guaranteed across multiple calls. Returned snapshots are unmodifiable and do not reflect later updates. Callbacks may run concurrently on caller threads and must themselves be thread-safe."

Câu này **chỉ đúng** nếu implementation thực sự có những hành vi đó. Không copy nguyên văn cho mọi class; hãy chọn đúng lời cam kết, thay vì hứa quá rộng.

## 21. Public API chỉ cung cấp read operations có cần contract không?

Có. Một API tưởng chỉ đọc vẫn có thể:

- **Lazy initialize** cache lần đầu, ghi vào field hoặc map nội bộ.
- Cập nhật thống kê truy cập trong mỗi lần đọc.
- Đọc một mutable resource bên ngoài mà thread khác đang sửa.
- Trả iterator/live view bị writer khác thay đổi.
- Gọi callback/logging collaborator có trạng thái mutable.
- Lưu kết quả vào một bộ nhớ đệm theo instance/global.

Ví dụ `public List<String> getNames()` có thể tạo snapshot mỗi lần gọi, hoặc trả live list. Hai cách có contract rất khác nhau. **Không có setter trong public API không chứng minh implementation không có mutation**.

Ngược lại, một class chỉ giữ immutable state và có constructor đúng, không để tham chiếu mutable thoát ra ngoài, thường dễ chứng minh thread-safe hơn. Tài liệu vẫn nên nói rõ input/output ownership và publication assumption.

## 22. Đặt tên method để phản ánh semantics, rồi ghi contract chi tiết

Tên method giúp người dùng dự đoán nhưng không thay thế tài liệu:

- `snapshot()` gợi ý bản sao độc lập ở một điểm; cần nói rõ mức nhất quán và mutable elements.
- `tryAdd(...)` gợi ý trả kết quả khi cạnh tranh; cần nói atomicity và điều kiện false.
- `await(...)` gợi ý có thể block; cần nói timeout/interruption và điều kiện đã đạt khi trả về.
- `submit(...)` gợi ý không nhất thiết hoàn tất ngay; cần nói executor, ordering, rejection và lifecycle.
- `close()` gợi ý giải phóng tài nguyên; cần nói concurrency với ongoing operations và tính idempotent.
- `addListener/removeListener` gợi ý đăng ký callback; cần nói snapshot, concurrency, exception và số lần delivery.

Nếu method có tên rất mạnh như `getConsistentSnapshot()`, implementation phải chứng minh tính nhất quán của toàn bộ tập dữ liệu. Tránh đặt tên dựa trên mong muốn khi chưa có cơ chế bảo đảm.

## 23. Kiểm tra contract bằng các trường hợp concurrent

Một tài liệu tốt phải có khả năng **đối chiếu với implementation và kiểm thử**, dù stress test không thay thế proof về JMM.

Nhóm tình huống nên kiểm tra:

1. **Hai writer trên cùng instance:** không lost update, duplicate ngoài ý muốn hoặc violation invariant.
2. **Reader và writer concurrent:** read trả một trạng thái cho phép bởi contract; snapshot không bị phá khi writer tiếp tục.
3. **Hai call ghép:** kiểm tra tài liệu có cảnh báo không atomic; nếu cần transaction, có operation đúng cấp độ không.
4. **Race với close/cancel:** xác định điểm chấp nhận/reject task và cách đánh thức waiter.
5. **Callback reentrant:** callback gọi ngược API có deadlock, xếp hàng không mong muốn hoặc vi phạm ordering không.
6. **Callback concurrent:** cùng listener có bị gọi đồng thời không, và tài liệu có mô tả điều đó không.
7. **Input mutable và alias:** caller sửa input sau khi gửi có làm API đổi kết quả không; khi copy concurrent có hành vi không được bảo đảm không.
8. **Failure/interrupt/timeout:** trạng thái còn đúng, permit/lock được giải phóng, kết quả lỗi được truyền về đúng nơi không.
9. **Visibility:** caller sử dụng proper publication/completion primitive và đọc được dữ liệu đã cam kết sau điểm chờ.
10. **Đổi implementation:** kiểm tra hợp đồng hành vi vẫn đúng nếu thay `synchronized` bằng `Lock`, thay cấu trúc lưu trữ hay đổi executor.

Đừng viết test chỉ "chạy 1.000 lần và không có exception" rồi kết luận thread-safe. Race condition phụ thuộc scheduling; một test pass không có nghĩa mọi kết quả không hợp lệ đã bị loại trừ. Lập luận về invariant và happens-before là nền tảng, test nhằm kiểm tra và phát hiện vi phạm.

### Kiểm tra contract như checklist review

Khi review một public API, hãy đọc Javadoc **như người chỉ biết tài liệu mà không được xem code**:

- Có biết được có thể gọi đồng thời trên **cùng instance** hay không?
- Có biết được thao tác nào atomic, thao tác nào chỉ thread-safe riêng lẻ?
- Có biết kết quả trả về thuộc snapshot, live view hay eventual consistency không?
- Có biết contract của callback, iterator, close và failure không?
- Có biết phải cung cấp external lock hoặc ownership discipline nào không?
- Có thể thay implementation mà không làm hỏng lời hứa đã công bố không?

Nếu thiếu câu trả lời cho một hành vi thường gặp, public contract cần bổ sung. Nếu tài liệu hứa điều implementation chưa bảo đảm, cần sửa logic hoặc thu hẹp lời hứa trước khi phát hành.

## 24. Những lỗi thường gặp khi document thread safety

**Lỗi 1 — Chỉ ghi "Thread-safe".** Không chỉ rõ method nào atomic, callback có thể concurrent hay không, snapshot có nhất quán không. Người đọc dễ suy luận quá mức.

**Lỗi 2 — Ghi "Not thread-safe" nhưng không nói caller phải làm gì.** Nếu cần external synchronization, phải xác định những truy cập nào cùng chịu khóa, kể cả iteration và compound operations.

**Lỗi 3 — Coi `synchronized` là toàn bộ contract.** `synchronized` trên mỗi method không bảo đảm chuỗi nhiều method atomic; mã có thể còn callback hoặc alias ngoài khóa.

**Lỗi 4 — Hứa FIFO cho hai producer concurrent mà không định nghĩa thứ tự.** FIFO chỉ có nghĩa trên một thứ tự enqueue có thể xác định; không đồng nghĩa "caller A bắt đầu trước thì event A luôn tới trước".

**Lỗi 5 — Hứa snapshot trong khi trả unmodifiable view.** View có thể đổi theo underlying collection. Snapshot phải có đường copy và publication phù hợp.

**Lỗi 6 — Cho rằng concurrent collection bảo vệ mutable values.** `ConcurrentHashMap<K, ArrayList<V>>` không khiến value `ArrayList` thread-safe.

**Lỗi 7 — Không nêu threading model của callback.** Listener có thể bị gọi concurrent trên cùng instance từ nhiều caller; state của listener cần tự bảo vệ.

**Lỗi 8 — Không nêu block/interrupt.** Caller không biết phương thức có thể chờ vô hạn, hay hủy task có làm operation hoàn tất không.

**Lỗi 9 — Ghi "lock-free" hoặc "non-blocking" theo cảm tính.** Đó là thuộc tính tiến triển có định nghĩa riêng; không thể suy từ một biến `volatile` hoặc một atomic field.

**Lỗi 10 — Lộ internal mutable state.** Getter trả raw list/map giúp client bỏ qua lock, vi phạm invariants và làm lời hứa thread-safe không còn đúng.

**Lỗi 11 — Bỏ quên constructor hoặc static shared state.** Một instance có thể được dùng an toàn sau construction nhưng constructor giữ reference tới mutable input, hoặc nhiều instance chia sẻ cache global chưa được bảo vệ.

**Lỗi 12 — Tài liệu không cập nhật khi đổi implementation.** Chuyển từ callback đồng bộ sang async, hoặc từ snapshot sang weakly consistent iterator là thay đổi hành vi mà caller có thể phụ thuộc; cần xem như thay đổi contract.

## 25. Mẫu thread-safety contract có thể áp dụng

Đây là **khung nội dung** để áp dụng cho một public class Java, không phải template phải điền đầy đủ bằng mọi giá:

```text
Thread safety:
  [Thread-safe | Conditionally thread-safe | Thread-confined | Not thread-safe]

Scope:
  Safe for concurrent calls on [the same instance | independent instances |
  shared global state]. Specify any exceptions.

Operation semantics:
  [Method A] is atomic with respect to [...]
  [Method B] returns [a snapshot | a live view | weakly consistent data].
  Multi-call sequences are [not atomic | atomic under documented protocol].

Input/output ownership:
  Inputs are [copied | retained | transferred].
  Returned values are [immutable snapshots | mutable caller-owned |
  shared views]. Specify mutable element responsibilities.

Blocking and callbacks:
  [Method] may block under [...], supports interruption/timeout [...]
  Callbacks run on [...], may be concurrent/reentrant [...],
  and exceptions are handled by [...].

Lifecycle:
  Concurrent close/submit/cancel is defined by [...]
  Accepted work and waiters after close behave as [...]

Caller obligations:
  [No external lock | Lock X around reads/writes/iteration |
  Do not mutate input while processing | Confine instances to thread X]

Unsupported guarantees:
  No guarantee of [cross-call atomicity | callback total order |
  fairness | a globally consistent aggregate snapshot | ...].
```

Giá trị của mẫu này là **làm lộ các giả định** trước khi khách hàng sử dụng API. Khi một hạng mục không áp dụng (ví dụ class không có callback), không cần thêm section vô nghĩa vào Javadoc. Nhưng nếu API có operation blocking hoặc trả mutable value, những điểm đó cần được diễn đạt rõ.

## 26. Tổng kết và câu hỏi tự kiểm tra

**Câu 1:** Class có `synchronized void add(...)` và `synchronized int size()`. Client gọi `if (size() < max) add(x)` có bảo đảm không vượt max?

**Không.** Hai lời gọi được bảo vệ riêng, nhưng có khoảng xen kẽ. Cần một operation atomic gói cả check và update, như `tryAdd`.

**Câu 2:** `ConcurrentHashMap` thread-safe, vậy `map.get(key).add(value)` khi value là `ArrayList` có thread-safe không?

**Không tự động.** Contract map chỉ bảo vệ các operation trên map theo API; mutable value vẫn cần protection hoặc ownership riêng.

**Câu 3:** Một API gọi callback trên caller thread. Hai caller gọi `publish` cùng lúc có thể chạy cùng một callback đồng thời không?

**Có thể**, nếu implementation cho phép và không áp đặt serialization. Phải ghi rõ việc callback có thể concurrent, và người viết callback phải đảm bảo state của mình an toàn.

**Câu 4:** Javadoc ghi "returns an unmodifiable list" đã đủ để caller suy ra snapshot không đổi theo nguồn chưa?

**Chưa.** Unmodifiable *view* có thể phản ánh mutation của nguồn. Hãy ghi rõ live view hay independent snapshot, và semantics của mutable elements.

**Câu 5:** Một API thread-safe có thể block vô thời hạn không?

**Có.** Thread safety liên quan tính đúng của concurrent access; blocking/liveness là thuộc tính khác. Cần tài liệu riêng về chờ, timeout, interruption và lifecycle.

**Câu 6:** Vì sao annotation `@ThreadSafe` không thay cho Javadoc?

Vì annotation không tự xác định ranh giới atomicity, ordering, snapshot, callback, input ownership hoặc shutdown. Hơn nữa Java SE không cung cấp một annotation chuẩn duy nhất bắt buộc cho tất cả API.

**Câu 7:** Khi thay implementation từ lock sang atomic variable, có được giữ nguyên contract không?

**Được nếu** tất cả semantics đã hứa cho caller vẫn đúng, gồm atomicity, visibility, ordering, error behavior, lifecycle và output ownership. Contract mô tả điều caller được dựa vào, không cần cố định chi tiết khóa nội bộ.

Một public API hữu ích là API cho phép người gọi trả lời được: **tôi có thể gọi đồng thời hay không, thao tác nào là một đơn vị nguyên tử, kết quả nào được cam kết và nghĩa vụ đồng bộ nào thuộc về tôi?** Khi trả lời được các câu đó bằng Javadoc chính xác và implementation tuân thủ, thread-safety contract trở thành một công cụ ngăn race condition và bảo vệ khả năng thay thế implementation trong tương lai.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="thread-safety-011">Nhận diện mutable collaborator làm phá vỡ thread safety</a>

<details>
<summary>Xem chi tiết</summary>

# Nhận diện mutable collaborator làm phá vỡ thread safety trong Java

Một class có thể dùng `private`, `final` và `synchronized` đúng cú pháp nhưng vẫn **không thread-safe** vì nó phụ thuộc vào một đối tượng có thể thay đổi mà thread khác cũng giữ tham chiếu. Đối tượng phụ thuộc đó gọi là **collaborator**. Nếu collaborator có **mutable state** (trạng thái có thể thay đổi) và không được bảo vệ bởi cùng một giao thức đồng bộ, các giả định an toàn của class bên ngoài có thể bị phá vỡ.

Ví dụ thường gặp là một class có khóa bảo vệ `List` nhưng nhận `ArrayList` từ caller rồi lưu nguyên tham chiếu, hoặc trả thẳng list nội bộ qua getter. Caller có thể chỉnh list mà không lấy khóa của class. Khóa bên trong dù hoàn hảo cũng **không thể điều phối đường truy cập bên ngoài mà nó không kiểm soát**.

Bài này tập trung vào **Java concurrency nói chung**: object graph, aliasing, encapsulation, memory visibility, defensive copying, immutable snapshots, thread-safe collaborators và cách kiểm tra hợp đồng đồng bộ. Các cơ chế riêng của Spring nằm ngoài phạm vi.

## 1. “Mutable collaborator” là gì?

**Collaborator** là object mà một thành phần gọi hoặc dùng để thực hiện công việc: dependency truyền qua constructor/setter, một collection được lưu vào field, cache, formatter, callback, clock, client, repository, listener hoặc một object chứa những object khác. Collaborator có thể là thư viện Java, class tự viết hoặc API của hệ thống khác.

**Mutable collaborator** là collaborator có trạng thái nội bộ hoặc dữ liệu tham chiếu tới có thể thay đổi sau khi được chia sẻ. Ví dụ:

- `ArrayList`, `HashMap`, `HashSet` và phần tử mutable bên trong collection.
- `StringBuilder`, `StringBuffer`, `java.util.Date`, `Calendar`, `ByteBuffer`.
- `SimpleDateFormat` (có trạng thái định dạng và không an toàn để nhiều thread dùng đồng thời khi không đồng bộ).
- Một object cấu hình có setter, bộ đếm, cache, registry hoặc worker tự quản lý state.
- Một collaborator trả mutable data qua `getItems()`, `getConfig()`, `getBuffer()`, kể cả khi bản thân accessor được đồng bộ.
- Một callback hoặc listener lưu lại tham chiếu tới object mà component nghĩ mình sở hữu riêng.

Điểm nguy hiểm là **nhiều thread có thể tiếp cận cùng mutable object** và ít nhất một thread có thể thay đổi nó theo đường mà class chính không kiểm soát.

### Object graph và alias

```java
List<String> supplied = new ArrayList<>();
Registry registry = new Registry(supplied);
```

Nếu constructor `Registry` chỉ gán `this.entries = supplied`, thì `registry.entries` và `supplied` là **hai tham chiếu tới cùng một object**. Đây là *aliasing*, không phải hai bản sao độc lập. Thread bên ngoài vẫn có thể gọi `supplied.add(...)`, kể cả sau khi `registry` đã được công bố cho các thread khác.

Một trường `private final List<String> entries` không chặn điều đó: `private` chỉ hạn chế đường truy cập theo ngôn ngữ; `final` bảo đảm không gán lại **tham chiếu** qua field sau khi xây dựng, không làm nội dung `List` bất biến.

### Khi nào collaborator không gây vấn đề?

- Một collaborator được **thread-confined**: chỉ một thread sở hữu, không escape sang thread khác.
- Collaborator được thiết kế **bất biến thực sự**, đã được công bố an toàn, và không chứa nhánh object mutable bị lộ.
- Collaborator tự thread-safe theo **đúng operation cần dùng**, hoặc tất cả thread tham gia cùng cơ chế khóa/giao thức nhất quán.
- Class sở hữu **bản sao riêng** của dữ liệu nhận vào và chỉ phát hành immutable value/snapshot, nên caller không thể mutate state nội bộ qua alias.

Một mutable object chưa chắc có bug; cần xét **nó được chia sẻ với ai, đường nào có thể mutate, invariant nào đang được bảo vệ và có happens-before phù hợp không**.

## 2. Vì sao một class “đã khóa hết method” vẫn sai?

Xem class minh họa sau (snippet, chưa có `main`):

```java
import java.util.List;

class Registry {
    private final List<String> entries;

    Registry(List<String> supplied) {
        this.entries = supplied; // Lưu nguyên alias của caller
    }

    synchronized int size() {
        return entries.size();
    }

    synchronized void add(String value) {
        entries.add(value);
    }

    synchronized List<String> entries() {
        return entries; // Lộ tham chiếu nội bộ
    }
}
```

Mỗi lời gọi `size()`, `add()`, `entries()` sử dụng monitor của cùng instance `Registry`. Nhưng:

```java
List<String> callerList = new ArrayList<>();
Registry registry = new Registry(callerList);

callerList.add("ngoai-khoa");          // Đi vòng qua monitor Registry
registry.entries().clear();           // Getter trả thẳng list nội bộ
```

Đây cũng là snippet, cần import `java.util.ArrayList`, `java.util.List` và class `Registry` bên trên. Cả hai thay đổi đều **không lấy monitor của Registry**. `synchronized` trên getter chỉ bảo vệ khoảng thời gian tạo/trả reference; nó **không mang khóa theo reference** tới các lời gọi `add`, `clear` diễn ra sau này.

Một số hậu quả có thể xảy ra nếu các thread thực sự truy cập đồng thời không đồng bộ:

1. **Data race** trên trạng thái nội bộ của collection/element do không có quan hệ happens-before.
2. **Mất invariant**: class kỳ vọng số phần tử, tổng, thứ tự hoặc uniqueness chỉ thay đổi qua method được kiểm soát, nhưng collaborator bị sửa bên ngoài.
3. **Visibility sai**: thread giữ khóa class vẫn không được bảo đảm thấy cập nhật qua một đường không dùng chung khóa như một chu trình đồng bộ đúng.
4. **Exception hoặc dữ liệu không nhất quán**: khi iterate và bị sửa đồng thời có thể gặp `ConcurrentModificationException`, bỏ sót phần tử, hoặc nhận kết quả không định trước. Không dựa vào fail-fast iterator làm cơ chế bảo vệ: exception **không được bảo đảm** xảy ra mọi lần.
5. **Cấp quyền ngoài ý muốn**: caller có thể mutate trạng thái private bằng alias, ngay cả khi tất cả method public của class đã cấm hoặc kiểm tra thao tác đó.

**Quy tắc:** tính an toàn của một thành phần phụ thuộc vào **mọi đường truy cập đến cùng mutable state**, không chỉ những method nhìn thấy trong class chính.

## 3. Thí nghiệm Java 17: phá invariant dù owner đang giữ khóa

Ví dụ dưới dùng hai `CountDownLatch` để tạo lịch rõ ràng. Worker đang giữ monitor của object `UnsafeInventory` khi nó chờ main sửa trực tiếp danh sách `seed` mà constructor đã giữ nguyên alias. Sau đó worker quan sát danh sách đã đổi **trong chính critical section** của nó.

Ta chủ động đặt thứ tự hai lần đọc quanh lần sửa; chương trình chứng minh **khóa của Inventory không bảo vệ được đường sửa ngoài**, chứ không cần dựa vào một data race ngẫu nhiên.

Tạo `LeakyInventoryExample.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class LeakyInventoryExample {
    static class UnsafeInventory {
        private final List<String> items;

        UnsafeInventory(List<String> supplied) {
            this.items = supplied;
        }

        synchronized int countChangesWhileLocked(
                CountDownLatch entered, CountDownLatch externalChanged)
                throws InterruptedException {
            int before = items.size();
            entered.countDown();

            if (!externalChanged.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("External update timed out");
            }

            return items.size() - before;
        }

        synchronized int size() {
            return items.size();
        }

        synchronized List<String> getItems() {
            return items;
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> seed = new ArrayList<>(List.of("A"));
        UnsafeInventory inventory = new UnsafeInventory(seed);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch externalChanged = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            Future<Integer> difference = pool.submit(
                    () -> inventory.countChangesWhileLocked(
                            entered, externalChanged));

            if (!entered.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Worker did not enter lock");
            }

            seed.add("B"); // Không hề lấy monitor của inventory
            externalChanged.countDown();

            System.out.println("changedWhileLocked="
                    + difference.get(5, TimeUnit.SECONDS));

            inventory.getItems().add("C"); // Alias bị lộ từ getter
            System.out.println("sizeAfterEscapedMutation="
                    + inventory.size());
        } finally {
            externalChanged.countDown();
            pool.shutdownNow();
        }
    }
}
```

Biên dịch/chạy bằng Java 17+:

```bash
javac LeakyInventoryExample.java
java LeakyInventoryExample
```

Stdout khi chương trình hoàn tất bình thường:

```text
changedWhileLocked=1
sizeAfterEscapedMutation=3
```

**Tại sao kết quả xác định?**

- Main ban đầu có một phần tử `"A"`; constructor lưu đúng object `seed`, không copy.
- Worker lấy monitor của `inventory`, đọc size `1` rồi báo `entered.countDown()`. Worker **chưa nhả monitor** vì đang chờ `externalChanged` ngay trong synchronized method.
- Main nhận tín hiệu `entered` rồi gọi `seed.add("B")` trực tiếp. Main không cần lấy monitor của `inventory`; kết quả size trở thành `2`.
- Main phát `externalChanged.countDown()`; worker chờ xong, đọc size `2` khi **vẫn đang giữ monitor** và trả `2 - 1 = 1`.
- Sau `Future.get`, main gọi getter (đã được synchronized) nhưng nhận reference nội bộ; rồi `add("C")` **sau khi getter đã nhả monitor**, size thành `3`.

Các latch thiết lập thứ tự đọc/sửa trong **thí nghiệm có kiểm soát**. Vì vậy không dùng kết quả này làm chứng cứ một data race không có happens-before đã thực sự xảy ra ở các bước đo; nó chứng minh lỗi **ownership/encapsulation**: code bên ngoài có thể sửa tài nguyên mà class tưởng đang độc quyền.

**Bài học chính:** bên trong method `synchronized` không có nghĩa tất cả collaborator được bảo vệ. Khóa chỉ phát huy khi **mọi actor thay đổi state đều tuân thủ cùng lock**. Chính đường truy cập trực tiếp qua `seed` và getter đã phá hợp đồng đó.

## 4. Hai cửa ngõ phổ biến: đầu vào và đầu ra

### 4.1. Constructor/setter giữ nguyên input mutable

```java
class UnsafeSettings {
    private final List<String> hosts;

    UnsafeSettings(List<String> hosts) {
        this.hosts = hosts; // Caller vẫn giữ reference
    }
}
```

Nếu caller đổi `hosts` sau khi tạo `UnsafeSettings`, state của object cũng đổi. Việc đánh dấu field `final` chỉ làm reference không thể được gán lại từ chính class; không hề sao chép cấu trúc hay phần tử.

**Cách xử lý:** khi component muốn sở hữu dữ liệu, sao chép input vào cấu trúc riêng tại ranh giới ownership. Nếu không cần mutate nội bộ, `List.copyOf(input)` cho một danh sách không sửa được và không chia sẻ **cấu trúc list** để caller thay đổi; nhưng lưu ý các phần tử vẫn có thể mutable. Nếu cần mutate nội bộ, dùng `new ArrayList<>(input)` (copy nông) và bảo vệ mọi lần truy cập nội bộ bằng một giao thức khóa phù hợp.

**Điều kiện của copy:** nguồn phải ổn định trong lúc sao chép hoặc caller phải phối hợp. Copy một `ArrayList` bị thread khác sửa **ngay trong lúc constructor đang copy** không tự an toàn. Thường cần contract “input không bị sửa trong lúc handoff”, hoặc snapshot được lấy dưới lock của owner nguồn.

### 4.2. Getter trả thẳng mutable object

```java
synchronized List<String> getEntries() {
    return internalEntries;
}
```

Đây là snippet, giả sử có field `internalEntries`. Getter được khóa **chỉ trong thời gian method thực thi**. Caller sau đó cầm reference và có thể `clear`, `add`, iterate hay chuyển reference sang thread khác.

**Cách xử lý:** cung cấp method operation có kiểm soát (`add`, `remove`, `getSnapshot`) thay vì cho phép mutate toàn bộ data structure. Khi trả danh sách chỉ đọc, dùng snapshot như `List.copyOf(internalEntries)` **khi đang giữ lock** (nếu phần tử bất biến). Trả một *live view* là một lựa chọn API khác, đòi hợp đồng đồng bộ riêng; đừng mặc định gọi nó là snapshot.

### 4.3. Setter và callback cũng làm lộ collaborator

Một getter không phải nơi duy nhất alias có thể escape:

- Setter nhận mutable object rồi giữ nguyên reference.
- Method đưa `this`, collection nội bộ, buffer hoặc mutable entry vào callback/listener bên ngoài; listener có thể giữ lại.
- Iterator, `Map.Entry`, sublist, view từ `Map.keySet()` / `values()` / `entrySet()` có thể liên kết tới collection nguồn.
- `Collections.unmodifiableList(backing)` cấm sửa **qua view**, nhưng owner vẫn có thể sửa `backing` và view phản ánh thay đổi: đó là *unmodifiable live view*, không nhất thiết là snapshot.
- `ByteBuffer.slice()` / `duplicate()` thường chia sẻ vùng dữ liệu backing với buffer gốc; các object wrapper khác nhau không chứng minh bộ nhớ dữ liệu độc lập.
- Closure/lambda hoặc asynchronous task capture tham chiếu mutable có thể kéo dài lifetime và đưa object sang thread khác.

Nên rà soát mọi ranh giới mà **reference** đi qua, không chỉ mọi lệnh `synchronized`.

## 5. Java 17: sửa bằng defensive copy và immutable snapshot

Một bản sửa trực tiếp: copy danh sách input khi tạo, mọi mutation nội bộ qua method có khóa, và getter tạo bản snapshot không sửa được. Ví dụ này dùng phần tử `String` bất biến, nên copy cấu trúc list là đủ cho phạm vi trạng thái minh họa.

Tạo `SafeInventoryExample.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SafeInventoryExample {
    static class SafeInventory {
        private final List<String> items;

        SafeInventory(List<String> source) {
            this.items = new ArrayList<>(source);
        }

        synchronized void add(String item) {
            items.add(Objects.requireNonNull(item));
        }

        synchronized int size() {
            return items.size();
        }

        synchronized List<String> snapshot() {
            return List.copyOf(items);
        }
    }

    public static void main(String[] args) {
        List<String> input = new ArrayList<>(List.of("A"));
        SafeInventory inventory = new SafeInventory(input);

        input.add("B"); // Không thay đổi bản copy bên trong
        System.out.println("afterCallerMutation=" + inventory.size());

        List<String> oldSnapshot = inventory.snapshot();
        inventory.add("C");

        System.out.println("snapshotSize=" + oldSnapshot.size());
        System.out.println("currentSize=" + inventory.size());

        try {
            oldSnapshot.add("D");
            System.out.println("snapshotMutable=true");
        } catch (UnsupportedOperationException expected) {
            System.out.println("snapshotMutable=false");
        }
    }
}
```

Chạy:

```bash
javac SafeInventoryExample.java
java SafeInventoryExample
```

Stdout chính xác:

```text
afterCallerMutation=1
snapshotSize=1
currentSize=2
snapshotMutable=false
```

**Giải thích:**

- `new ArrayList<>(source)` tạo **cấu trúc list riêng** ngay tại constructor. Caller thêm `"B"` vào list ban đầu không còn sửa inventory.
- `synchronized` trên `add`, `size` và `snapshot` sử dụng **cùng monitor** để bảo vệ những thao tác trên state sở hữu riêng.
- `List.copyOf(items)` trả collection không hỗ trợ thay đổi cấu trúc qua reference được trả; khi inventory thêm `"C"`, snapshot cũ vẫn size `1`.
- Vì phần tử là `String` bất biến, caller không thể mutate element để làm đổi nội dung snapshot/inventory qua alias phần tử. Với phần tử là object mutable, cần bảo vệ thêm tầng phần tử.
- Nếu contract cho phép `null` trong list thì `List.copyOf` không phù hợp vì từ chối phần tử null; điều chỉnh representation hoặc validate input rõ ràng thay vì giả định mọi collection chấp nhận null.

**Không phải lúc nào cũng cần khóa:** nếu class chỉ lưu một immutable list được công bố an toàn, không cho update về sau và các phần tử cũng immutable, reader có thể đọc mà không cần khóa quanh từng lần truy cập. Ví dụ ở trên cố ý cho phép mutation qua `add` nên lựa chọn một monitor chung để dạy rõ ownership.

## 6. Bẫy lớn: defensive copy nông không bảo vệ phần tử mutable

`new ArrayList<>(source)` và `List.copyOf(source)` đều không tự *deep-copy* các object bên trong. Với một `List<MutableUser>`, cả list copy lẫn list gốc vẫn tham chiếu **cùng User instances**. Vì vậy một class có thể đã copy collection nhưng vẫn lộ collaborator ở tầng phần tử.

Tạo `ShallowCopyExample.java`:

```java
import java.util.List;

public class ShallowCopyExample {
    static class MutableUser {
        private String name;

        MutableUser(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }

        void rename(String name) {
            this.name = name;
        }
    }

    record UserView(String name) { }

    public static void main(String[] args) {
        MutableUser user = new MutableUser("An");
        List<MutableUser> copied = List.copyOf(List.of(user));

        user.rename("Binh");
        System.out.println("copiedElementName="
                + copied.get(0).name());

        List<UserView> valueSnapshot = copied.stream()
                .map(u -> new UserView(u.name()))
                .toList();

        user.rename("Chi");
        System.out.println("valueSnapshotName="
                + valueSnapshot.get(0).name());
        System.out.println("originalName=" + user.name());
    }
}
```

Chạy Java 17+:

```bash
javac ShallowCopyExample.java
java ShallowCopyExample
```

Stdout:

```text
copiedElementName=Binh
valueSnapshotName=Binh
originalName=Chi
```

List `copied` là **unmodifiable**, nhưng phần tử `MutableUser` có method `rename`. Khi sửa object `user`, `copied.get(0)` quan sát tên mới vì đây vẫn là **cùng instance**. Sau đó chương trình tạo `UserView` chứa `String` (bất biến), nên việc đổi tên `user` lần tiếp theo không làm đổi snapshot giá trị.

Đây là thí nghiệm tuần tự để giải thích alias; trong môi trường multi-thread thực tế, nếu một thread rename còn thread khác đọc tên không có đồng bộ, vấn đề visibility cũng phát sinh.

### Làm sao thiết kế owner thực sự an toàn khi element mutable?

- Tại ranh giới vào: nếu owner cần giữ mutable entity, tạo **bản sao độc lập của từng element**, hoặc chuyển thành immutable value object. Hợp đồng phải nói rõ có deep copy hay không.
- Trong owner: bảo vệ **mọi cập nhật và mọi snapshot** bằng cùng lock, hoặc tổ chức state thành immutable aggregate và thay reference theo cơ chế atomic.
- Tại ranh giới ra: chỉ trả **value snapshot** (ví dụ record chứa `String`, `int` và những value immutable) hoặc defensive copies không chia sẻ mutable child; đừng trả mutable entity nội bộ.
- Nếu record chứa `List<MutableUser>`, tên `record` không tự deep-freeze list và element. Record chỉ tạo field final và một số method tự sinh; cần defensive copy/validation trong constructor và accessor khi có mutable member.
- Copy càng sâu càng tốn CPU/memory; cân nhắc immutable model từ thiết kế, thay vì copy toàn bộ graph ở mọi request.

## 7. Đồng bộ đúng class nhưng sai lock identity

Giả sử một `Account` giữ một mutable collaborator `Ledger`. Account khóa `this`, nhưng code khác trực tiếp sửa Ledger hoặc khóa `ledger` thay vì `account`:

```java
class Account {
    private final Ledger ledger;

    Account(Ledger ledger) {
        this.ledger = ledger;
    }

    synchronized void debit(int amount) {
        ledger.debit(amount);
    }
}
```

Đây là snippet, `Ledger` chưa được định nghĩa; không phải chương trình độc lập. `synchronized` trên `Account.debit` chỉ chặn những caller cũng muốn sở hữu monitor **của cùng Account instance**. Nó **không buộc** code có reference tới `ledger` phải lấy monitor ấy. Hai `Account` khác nhau có thể cùng tham chiếu một `ledger` nhưng lấy **hai monitor khác nhau**. Vì vậy trạng thái Ledger vẫn có thể bị cập nhật đồng thời.

Ba cách giải, tùy ownership và hợp đồng:

1. **Account sở hữu Ledger độc quyền:** constructor nhận dữ liệu dạng value và tạo Ledger nội bộ; không phát hành reference Ledger, chỉ cung cấp operation/snapshot.
2. **Ledger được chia sẻ và tự thread-safe:** tất cả operation Ledger phải có contract atomicity/visibility phù hợp. Nhưng nếu nghiệp vụ cần “kiểm tra số dư rồi trừ”, phải bảo vệ **cả chuỗi**, không chỉ từng method đơn lẻ.
3. **Một lock/protocol chung cho mọi actor:** chỉ khi tất cả đường truy cập thật sự tuân thủ cùng lock có thể bảo vệ compound invariant. Truyền một lock object ra khắp hệ thống có chi phí thiết kế và nguy cơ deadlock; nếu quản lý khó, thiết kế ownership/encapsulation thường rõ ràng hơn.

### Lock order và gọi collaborator ngoài ý muốn

Nếu Account giữ lock A và gọi collaborator bên ngoài mà collaborator lại gọi ngược Account hoặc lấy lock B, một thread khác có thể giữ B rồi đợi A, tạo vòng chờ deadlock. Đặc biệt nguy hiểm khi gọi **user-supplied callback** hoặc I/O không có thời hạn trong critical section. Cố gắng tạo immutable event snapshot **bên trong** lock rồi gọi callback **sau khi nhả lock** nếu nghiệp vụ cho phép. Khi callback chạy ngoài lock, phải định nghĩa rõ **thứ tự event giữa nhiều thread** nếu consumer phụ thuộc thứ tự.

## 8. Thread-safe collaborator chưa đủ cho mọi invariant

Một collaborator được ghi trong tài liệu là “thread-safe” không có nghĩa toàn bộ chuỗi sử dụng nó đều nguyên tử.

### Ví dụ `ConcurrentHashMap`

```java
if (!cache.containsKey(key)) {
    cache.put(key, createValue());
}
```

Đây là snippet, giả sử `cache` là `ConcurrentHashMap<Key, Value>`. Từng operation `containsKey`, `put` có bảo đảm riêng. Tuy nhiên hai thread vẫn có thể cùng thấy key chưa tồn tại rồi đều `createValue()`; trạng thái cuối map có thể chỉ giữ một value nhưng **side effect tạo dữ liệu đã xảy ra hai lần**.

Có thể dùng `computeIfAbsent(key, mappingFunction)` hoặc `putIfAbsent` nếu semantics của chúng đáp ứng đúng nhu cầu. Mapping function của `ConcurrentHashMap.computeIfAbsent` cần ngắn, rõ ràng và tránh tự sửa map theo đường dẫn dễ gây deadlock/lỗi. Nếu `createValue()` gọi DB hoặc API bên ngoài có side effect, cần xem xét transaction/idempotency và failure/retry, không giả định một lời gọi map giải quyết toàn bộ.

### Ví dụ `Collections.synchronizedList`

`Collections.synchronizedList(list)` bảo vệ các method đơn lẻ của wrapper bằng monitor tương ứng. Nhưng:

```java
List<String> syncList =
        java.util.Collections.synchronizedList(new ArrayList<>());

synchronized (syncList) {
    for (String item : syncList) {
        System.out.println(item);
    }
}
```

Đây là snippet cần import `java.util.List`, `java.util.ArrayList`; nó minh họa pattern theo tài liệu JDK: **khi duyệt collection bằng iterator, caller phải đồng bộ thủ công trên đúng wrapper list** để tránh mutation xen kẽ qua wrapper trong lúc iterate. Nếu ai đó còn giữ **backing list gốc** và sửa trực tiếp ngoài wrapper, vòng khóa này vẫn bị bypass. Tốt nhất là không để backing mutable reference có thể bị dùng ngoài hợp đồng.

### Ví dụ thread-safe operation nhưng mutable value

`ConcurrentHashMap<String, MutableUser>` an toàn ở cấp thao tác map, nhưng không khiến `MutableUser.rename(...)` thread-safe. Lấy reference từ map và sửa object không đồng bộ vẫn có thể race. Concurrent collection chỉ bảo vệ phạm vi mà API cam kết; mọi **value object** và invariant liên quan phải được xem xét riêng.

## 9. Visibility và safe publication không thay thế ownership

Java Memory Model cung cấp các quan hệ *happens-before* (HB). Ví dụ:

- Nhả monitor happens-before lần lấy monitor **tiếp theo trên cùng monitor**.
- Ghi vào `volatile` field happens-before lần đọc sau của chính field đó theo quy tắc JMM.
- Hành động trước `Thread.start()` happens-before hành động trong thread được khởi động.
- Hành động trong worker happens-before caller quan sát thread kết thúc qua `Thread.join()` phù hợp.
- Các tiện ích `java.util.concurrent` có memory-consistency effects theo hợp đồng riêng.

Các cơ chế ấy giúp dữ liệu được công bố và quan sát theo thứ tự đã chỉ định. Nhưng **safe publication chỉ giải quyết việc chuyển giao trạng thái đã xây dựng**, không bảo vệ các đợt mutation về sau nếu nhiều thread còn giữ alias mutable.

Ví dụ:

```java
class Holder {
    volatile List<String> values;
}
```

Gán một reference `ArrayList` vào `volatile values` giúp các thread khác thấy reference mới theo semantics của volatile; **không biến `values.add` hoặc `values.clear` thành thread-safe**, và không bảo đảm iteration đang diễn ra không bị sửa xen ngang. Nếu muốn thay một snapshot bất biến, writer tạo object list immutable mới rồi gán `volatile values = List.copyOf(...)`, còn reader lấy **một reference** và chỉ đọc snapshot đó. Nhưng vẫn phải bảo đảm element immutable hoặc copy đúng mức, và nhiều writer muốn hợp nhất thay đổi có thể cần CAS/lock để không mất cập nhật.

### `final` field đảm bảo gì?

Một properly constructed object với `final` fields có những bảo đảm riêng về initialization safety. Tuy nhiên:

- `final` **không tạo mutual exclusion** cho mọi thao tác sau constructor.
- `final List<T>` không làm mọi object `T` bất biến.
- Nếu `this` escape trong constructor (đăng ký listener hoặc chạy thread quá sớm), các giả định publication có thể bị phá vỡ.
- Nếu constructor nhận mutable collaborator từ thread khác, caller vẫn có thể mutate sau đó; `final` field chỉ khóa reference, không khóa quyền sở hữu object.

**Câu hỏi đúng:** không chỉ “reference được publish thế nào?”, mà còn “ai được phép mutate **toàn bộ graph có thể truy cập từ reference** sau publication?”.

## 10. Các collaborator dễ bị bỏ qua trong review

| Collaborator | Vì sao có thể mutable/nguy hiểm | Cách xử lý thường hợp lý |
| --- | --- | --- |
| `List`/`Map` truyền qua constructor | Caller giữ alias, sửa không theo khóa owner | Copy cấu trúc, xác định quyền sở hữu |
| Phần tử mutable trong immutable list | Cấu trúc cố định, object element vẫn thay đổi | Immutable value/deep copy theo nhu cầu |
| Getter trả map/list nội bộ | Escape reference sau khi getter đã unlock | Trả snapshot hoặc API operation |
| `Collections.unmodifiableList(backing)` | Read-only *view* vẫn đổi khi backing đổi | `List.copyOf` hoặc copy dưới lock để có snapshot |
| `Map.keySet()`, `entrySet()`, `subList()` | View thường liên kết tới backing collection | Trả snapshot riêng nếu cần cách ly |
| `Date`, `Calendar` | Thời điểm/cấu hình có thể thay đổi | Ưu tiên `java.time` immutable types như `Instant`, `LocalDate` |
| `SimpleDateFormat` | Chứa mutable formatting state | `DateTimeFormatter` (immutable, thread-safe theo hợp đồng API) |
| `StringBuilder` | Buffer có thể mutate và không tự thread-safe | Per-task builder hoặc tạo bản String immutable |
| `StringBuffer` | Method có synchronized nhưng chuỗi nhiều bước chưa atomic | Giữ đúng lock quanh cả workflow hoặc confinement |
| `ByteBuffer`/backing array | Slice/duplicate có thể chia sẻ bytes | Copy data khi cần ownership riêng, áp giao thức khóa |
| Listener/callback | Có thể lưu reference và mutate về sau, hoặc reentrant | Truyền immutable event, xác định lock/order/lifecycle |
| Cache có mutable value | Cache thread-safe chỉ bảo vệ map operation | Value immutable hoặc bảo vệ chính value |
| `ThreadLocal<T>` chứa mutable object | Hạn chế chia sẻ giữa thread, nhưng worker pool tái sử dụng thread | Xóa bằng `remove()` đúng vòng đời; không truyền value sang thread khác |
| Một mutable config object | Setter thay đổi cấu hình khi task đang đọc | Immutable configuration snapshot, volatile/atomic publish |
| API client/session mutable | Hợp đồng thread safety có thể bị giới hạn theo method hoặc instance | Đọc docs, mỗi task một client/session nếu cần, giới hạn lifecycle |

**Không mặc định mỗi tên class nói lên thread-safety.** Một thư viện có thể cho phép gọi một số operation đồng thời nhưng không bảo đảm kết quả của một chuỗi liên quan nhiều call, hoặc đòi không mutate input collection trong thời gian operation.

## 11. Phương pháp nhận diện mutable collaborator trong codebase

Thay vì chỉ tìm từ khóa `synchronized`, hãy vẽ một **bản đồ ownership của mutable state**.

### Bước 1: Tìm tất cả mutable state trực tiếp và gián tiếp

Các field không `final`, collection, cache, cấu hình, bộ đệm, listener lists, object có setter và mutable value objects đều là ứng viên. Với mỗi field reference, xem **nội dung object** có thể thay đổi không, bao gồm children ở nhiều tầng.

### Bước 2: Truy ngược ai tạo/giữ reference

Hỏi: tham chiếu đến từ constructor, setter, factory, một singleton static, getter thư viện hay kết quả cache? Caller còn cầm reference sau lúc trao cho component không? Nếu nhận object từ ngoài mà không copy/ownership transfer rõ ràng, phải coi khả năng alias vẫn còn.

### Bước 3: Tìm mọi đường escape

Rà soát return values, callback arguments, listener registrations, getters, iterators/views, lambdas/async tasks, method tham số `Consumer`/`Function`, object được cho vào shared collection, và cả việc trả `this`. Một reference nội bộ thoát khỏi class có thể được sử dụng ở thread khác mà class không thấy.

### Bước 4: Đọc hợp đồng thread-safety của collaborator

Các câu cần biết:

- Thread-safe **toàn bộ instance** hay chỉ một số method?
- Mutating operation có linearizable/atomic không? Những thao tác ghép nào không được bảo đảm?
- Có yêu cầu caller đồng bộ khi iterate không?
- API trả **copy, snapshot, live view hay alias**?
- Có được chia sẻ instance giữa nhiều thread hay chỉ thread confinement?
- Có yêu cầu input không bị mutate trong lúc method chạy?
- Nếu callback được gọi khi giữ lock, có thể reenter component không?

Không có tài liệu, không nên giả định mutable collaborator tự an toàn.

### Bước 5: Liệt kê invariant và ranh giới bảo vệ

Ví dụ “size không bao giờ vượt capacity”, “số dư không âm”, “một key tạo một lần”, “các field thuộc cùng version”. Xác định **toàn bộ read–check–write** nằm trong một operation atomic/critical section hay bị chia thành nhiều lời gọi có thể xen kẽ. Chỉ khóa `add` không đủ nếu `canAdd` được kiểm tra riêng rồi mới gọi `add`.

### Bước 6: Kiểm tra lock identity và happens-before

Cùng một object mutable được truy cập thông qua nhiều wrapper/owner khác nhau? Chúng có dùng **cùng khóa** không? Có đoạn read không khóa, direct access từ caller hoặc đọc qua view? Có volatile/safe publication đảm bảo visibility đúng cách không? Không suy ra thread-safe chỉ từ việc “tôi thấy một lock ở đâu đó”.

### Bước 7: Dựng test ép đường bypass

Test có thể giữ khóa owner ở thread A rồi yêu cầu thread B chỉnh collaborator qua alias. Nếu B vẫn thay đổi được state, khóa owner không kiểm soát tất cả đường sửa. Dùng `CountDownLatch`/`CyclicBarrier` để điều phối, timeout để test không treo, `Future.get` để đưa lỗi worker về test runner. Test không thay thế phân tích Java Memory Model nhưng minh họa chính xác lỗ hổng thiết kế.

### Bước 8: Đánh giá cách sửa và chi phí

Có thể chuyển sang immutable value, defensive copy, interface chỉ đọc, thread confinement, message passing, hoặc một synchronized/atomic operation bao trọn invariant. Xem thêm chi phí copy lớn, memory, GC, throughput, lock contention, deadlock và thứ tự callback. Đừng chỉ sửa một getter rồi quên constructor alias.

## 12. Hai lỗi “sửa một nửa” thường gặp

### 12.1. Chỉ bọc `Collections.unmodifiableList`

```java
List<String> backing = new ArrayList<>(List.of("A"));
List<String> view = java.util.Collections.unmodifiableList(backing);

backing.add("B");
// view.size() bây giờ bằng 2.
```

Đây là snippet, cần import `java.util.List` và `java.util.ArrayList`. Caller không gọi `view.add` được nhưng vẫn có thể gọi `backing.add`. Nếu muốn snapshot, tạo **một cấu trúc giá trị độc lập** từ backing tại thời điểm mong muốn, ví dụ `List.copyOf(backing)` khi nguồn ổn định hoặc được bảo vệ thích hợp.

### 12.2. Chỉ dùng `volatile` trên reference

```java
private volatile List<String> shared = new ArrayList<>();
```

Đây là khai báo field trong một class. `volatile` hữu ích cho **công bố/thay reference**, nhưng không khiến các method nội bộ của `ArrayList` an toàn khi nhiều thread mutate đồng thời. Tăng/giảm visibility không tự tạo mutual exclusion. Nếu muốn copy-on-write thủ công, writer có thể copy trạng thái cũ, sửa bản copy rồi publish immutable snapshot mới; với **nhiều writer**, cần khóa chung hoặc CAS loop để tránh một writer ghi đè snapshot của writer khác.

## 13. Immutable snapshot và publish theo version

Một thiết kế có nhiều reader, ít writer có thể giữ state thành immutable record và công bố toàn bộ bằng một reference volatile:

```java
import java.util.List;

record InventoryView(int version, List<String> items) {
    InventoryView {
        items = List.copyOf(items);
    }
}

class SnapshotInventory {
    private volatile InventoryView current =
            new InventoryView(0, List.of());

    InventoryView read() {
        return current;
    }

    synchronized void add(String item) {
        InventoryView old = current;
        List<String> next = new java.util.ArrayList<>(old.items());
        next.add(item);
        current = new InventoryView(old.version() + 1, next);
    }
}
```

Đây là **snippet có thể biên dịch nếu đặt trong file phù hợp**, nhưng chưa có `main`. Record constructor ép copy cấu trúc list vào một immutable representation (các phần tử ở đây là `String`). Reader chỉ cần gọi `read()` **một lần** để lấy cùng version và contents; writer đồng bộ khi thực hiện read–modify–publish, tránh lost update giữa nhiều writer. Khi ghi `current` volatile, state được công bố theo hợp đồng JMM. Khi snapshot đã được công bố, reader không cần nắm khóa writer để đọc snapshot đó.

Nếu dữ liệu lớn và cập nhật liên tục, copy toàn bộ `List` mỗi lần thêm có thể tốn memory/CPU; cân nhắc persistent data structures, phân vùng ownership hoặc cơ chế đồng bộ khác. Nếu phần tử mutable, copy list vẫn là nông: cần thiết kế immutable element hoặc deep copy theo hợp đồng.

## 14. Gọi callback từ một object thread-safe

Một component có thể sở hữu state an toàn nhưng gọi listener theo cách làm lộ nó:

```java
synchronized void notifyListener(Consumer<List<String>> listener) {
    listener.accept(internalItems);
}
```

Đây là snippet, giả định import `java.util.function.Consumer`, `java.util.List` và có field `internalItems`. Listener do caller cung cấp có thể giữ reference để dùng sau, hoặc ngay trong callback gọi `clear()`. Khóa của method không giới hạn hành động của listener sau khi method trả về; còn callback reentrant hoặc chặn lâu trong lúc owner giữ monitor có thể gây latency và deadlock.

Pattern an toàn hơn khi hợp với nghiệp vụ:

```java
void publish(Consumer<List<String>> listener) {
    List<String> snapshot;
    synchronized (this) {
        snapshot = List.copyOf(internalItems);
    }
    listener.accept(snapshot);
}
```

Đây cũng là snippet, giả định phần tử immutable và list chỉ cần snapshot. Listener có thể giữ reference `snapshot` mà không làm thay đổi state nội bộ; đồng thời owner đã nhả monitor trước khi gọi code bên ngoài. Nếu nhiều thread publish các snapshot khác nhau, **thứ tự callback hoàn thành không mặc nhiên là thứ tự state được cập nhật**; nếu consumer yêu cầu thứ tự, cần queue/sequence number hoặc giao thức serialization rõ ràng.

## 15. Test gì để xác nhận sửa đúng?

**Nhóm kiểm tra ownership (có thể chạy tuần tự):**

1. Tạo input mutable, truyền vào constructor, thay đổi input. State owner có đổi ngoài ý muốn không?
2. Lấy getter/snapshot, thử mutate collection (nếu API nói immutable) và mutate phần tử. State owner có đổi không?
3. Lấy hai snapshot tại hai thời điểm: snapshot cũ có bị thay đổi sau một update mới không?
4. Thử view từ `subList`, `keySet`, `entrySet`, buffer slice, callback reference; đường nào còn trỏ về backing state?
5. Nếu input được clone, thử cấu trúc lồng nhiều tầng; copy nông có phù hợp hợp đồng không?

**Nhóm kiểm tra concurrency:**

1. Dùng hai thread và latch để ép đường sửa ngoài xảy ra khi owner giữ monitor; quan sát invariant. Ví dụ `LeakyInventoryExample` đã kiểm tra đúng loại bypass này.
2. Tạo stress test nhiều writer/reader trên **operation thực** và assert invariant sau khi các worker hoàn thành (có timeout và fail-fast reporting).
3. Đừng dùng `Thread.sleep` để “chắc chắn” gặp race; dùng checkpoint có điều kiện rõ.
4. Với shared map/list, thử compound operation (check–then–act), iteration khi mutation và mutable element, không chỉ thử `add`/`get` riêng.
5. Một test qua nhiều lần không chứng minh không có data race; cần code review theo HB, lock ownership và hợp đồng API.
6. Nếu dùng virtual thread (Java 21+) thay platform thread, các quy tắc về mutable collaborator, publication và atomicity **không thay đổi**.

Cần lưu ý thêm: test orchestration có thể tạo **happens-before mới** và che một số lỗi visibility. Nó rất tốt để chứng minh đường bypass và lỗi logic đã biết, nhưng không đủ để chứng minh tất cả interleaving/JMM outcome. Các vấn đề memory ordering tinh vi có thể cần stress/litmus test chuyên biệt.

## 16. Checklist nhận diện nhanh khi đọc code

Đối với mỗi class tuyên bố “thread-safe”, hãy kiểm tra:

- **Object graph:** class chỉ có primitive/immutable values, hay nắm mutable collaborator?
- **Nguồn tham chiếu:** collaborator được tự tạo độc quyền hay truyền vào từ ngoài và caller còn giữ alias?
- **Khả năng escape:** getter, callback, iterator, live view, element, listener hoặc async task có trả/giữ tham chiếu mutable không?
- **Phạm vi copy:** copy cấu trúc hay copy cả phần tử/child objects? Có cần deep copy không?
- **Quy tắc khóa:** mọi đường đọc/ghi có dùng **đúng cùng lock identity**, hoặc collaborator tự bảo vệ đủ phạm vi operation?
- **Hợp đồng compound operation:** check–then–act, read–modify–write, invariant nhiều field có thể bị xen ngang không?
- **Visibility:** state được công bố bằng HB nào? `final`/`volatile` có đang bị ngộ nhận là mutex không?
- **Thread-safe collection:** bản thân value/element có mutable và phải đồng bộ riêng không?
- **Callback:** có gọi code bên ngoài khi giữ khóa? Có truyền reference live/nhận side effect bất ngờ không?
- **Kiểm thử:** đã sửa cả đường constructor lẫn đường getter chưa? Test có chứng minh snapshot không bị ảnh hưởng khi caller sửa input/element không?

## 17. Những ngộ nhận cần tránh

| Ngộ nhận | Thực tế |
| --- | --- |
| “Tất cả method đều `synchronized`, vậy class an toàn.” | Nếu mutable collaborator bị lộ hoặc được sửa qua alias ngoài monitor, lock không kiểm soát được toàn bộ state. |
| “Field `private final` là bất biến.” | Chỉ reference không được gán lại; object được tham chiếu vẫn có thể mutable. |
| “Getter `synchronized` trả list là thread-safe.” | Khóa chỉ giữ trong getter; caller dùng reference sau khi getter return không được khóa. |
| “`List.copyOf` copy cả element.” | Chỉ tạo cấu trúc unmodifiable; element mutable vẫn được chia sẻ. |
| “`Collections.unmodifiableList` là snapshot.” | Thường là live view của backing list; backing vẫn có thể thay đổi. |
| “`ConcurrentHashMap` bảo vệ luôn giá trị của map.” | Nó không làm các value object mutable trở nên thread-safe. |
| “Collaboration chỉ là method call.” | Truyền và lưu **reference** cũng trao khả năng truy cập mutable state. |
| “Safe publication giải quyết mọi truy cập về sau.” | Nó bảo đảm handoff thích hợp; mutation sau đó vẫn cần giao thức đồng bộ. |
| “Có `volatile List` thì `add/clear` đã an toàn.” | Volatile reference không tạo mutual exclusion cho collection operations. |
| “Lock ở wrapper nào cũng được.” | Các actor phải dùng đúng cùng lock, hoặc API collaborator có bảo đảm riêng đủ mạnh. |
| “Chỉ cần viết test concurrent vài lần.” | Test có thể bỏ sót lịch nguy hiểm; ownership và happens-before vẫn phải được chứng minh. |
| “Một immutable record luôn deep-immutable.” | Record chứa mutable references cần thiết kế defensive copy/immutable children phù hợp. |

## 18. Câu hỏi tự kiểm tra

**1. Vì sao `private final ArrayList<String> list` không bảo đảm thread-safety?** Vì `final` không cấm thay đổi nội dung list. Nếu reference được chia sẻ, nhiều thread vẫn có thể mutate; khóa và ownership vẫn cần rõ ràng.

**2. Constructor dùng `new ArrayList<>(input)`, nhưng element là `MutableUser`. Đã cách ly hoàn toàn chưa?** Chưa. Copy cấu trúc list vẫn chia sẻ reference tới từng `MutableUser`. Cần immutable element, deep copy hoặc cơ chế đồng bộ đối với element.

**3. Một getter đồng bộ trả `List` nội bộ. Caller có cần đồng bộ khi gọi `list.add` không?** Getter đã nhả monitor khi trả về; caller có thể mutate không theo khóa và phá invariant. Nên tránh lộ reference và cung cấp operation/snapshot với contract rõ.

**4. Owner khóa `this`, collaborator tự khóa bằng monitor khác. Có an toàn không?** Chỉ khi contract của collaborator và mọi compound invariant đều được bảo vệ phù hợp. Hai monitor khác nhau tự chúng không tạo mutual exclusion chung đối với một state cần cập nhật nguyên tử.

**5. `List.copyOf` có an toàn khi input bị một thread khác sửa ngay lúc copy không?** Không mặc nhiên. Cần hợp đồng để input không bị concurrent modification hoặc snapshot dưới cơ chế đồng bộ thích hợp.

**6. Nếu `ConcurrentHashMap` thread-safe, `map.get(k).setName(...)` có thread-safe không?** Không thể kết luận. `setName` tác động lên value mutable; phải xét thread-safety riêng của value.

**7. Dùng `volatile` cho collaborator field có sửa được race trên mutable list không?** Không. Volatile đảm bảo publication/reference visibility, không bảo vệ in-place mutation của list.

**8. Vì sao ví dụ `LeakyInventoryExample` có thể chứng minh lỗi dù dùng latch tạo happens-before?** Vì điều cần chứng minh là **main vẫn sửa được state qua alias khi owner đang giữ monitor**. Latch kiểm soát thứ tự, và kết quả cho thấy việc khóa owner không tạo quyền sở hữu độc quyền với tài nguyên bị lộ.

**9. Khi nào snapshot list đủ, khi nào cần deep copy?** Snapshot list đủ khi elements immutable và invariant chỉ phụ thuộc cấu trúc. Nếu elements mutable, phải xem điều kiện sử dụng và tạo immutable value/deep copy cho phần dữ liệu cần cách ly.

**10. Vì sao nên gọi callback bên ngoài lock?** Callback có thể chạy lâu, reentrant, lấy khóa khác hoặc lưu lại reference. Khi khả thi, tạo immutable event dưới lock rồi thông báo ngoài lock; nếu cần thứ tự event, bổ sung cơ chế riêng.

**Nguyên tắc nhớ lâu:** *Thread safety là thuộc tính của toàn bộ giao thức truy cập **cùng trạng thái mutable**, kể cả mọi collaborator và alias có thể chạm vào nó.* Một field `final` hay một method `synchronized` chỉ bảo vệ phần mà chúng thật sự kiểm soát. Xác định ownership, ngăn reference mutable escape, sao chép/công bố immutable values khi phù hợp và bảo vệ compound invariant bằng cùng cơ chế đồng bộ.

</details>

- [Quay lại đầu trang](#back-to-top)

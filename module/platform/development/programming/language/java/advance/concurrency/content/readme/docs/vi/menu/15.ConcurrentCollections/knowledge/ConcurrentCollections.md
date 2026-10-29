<a id="back-to-top"></a>

# Concurrent Collections

## Menu
- [Vì sao ArrayList và HashMap không an toàn khi truy cập chung?](#concurrent-collections-001)
- [ConcurrentHashMap: putIfAbsent(), compute(), merge()](#concurrent-collections-002)
- [Atomic compound operations của ConcurrentHashMap](#concurrent-collections-003)
- [Weakly consistent iteration và snapshot semantics](#concurrent-collections-004)
- [CopyOnWriteArrayList: ưu và nhược điểm khi ghi nhiều](#concurrent-collections-005)
- [ConcurrentLinkedQueue và ConcurrentLinkedDeque](#concurrent-collections-006)
- [ArrayBlockingQueue so với LinkedBlockingQueue](#concurrent-collections-007)
- [PriorityBlockingQueue, DelayQueue và SynchronousQueue](#concurrent-collections-008)
- [ConcurrentSkipListMap và ConcurrentSkipListSet](#concurrent-collections-009)
- [Collections.synchronizedMap/List và yêu cầu khóa khi duyệt](#concurrent-collections-010)
- [Concurrent queue, blocking queue và backpressure](#concurrent-collections-011)
- [Chọn collection theo read/write ratio và ordering](#concurrent-collections-012)
- [ConcurrentHashMap: chính sách null, computeIfAbsent() và giới hạn hàm ánh xạ](#concurrent-collections-013)
- [Giá trị mutable trong ConcurrentHashMap vẫn cần hợp đồng thread safety riêng](#concurrent-collections-014)
- [CopyOnWriteArraySet và tính chất snapshot của iterator khi có ghi](#concurrent-collections-015)
- [TransferQueue và BlockingDeque: chuyển giao trực tiếp và thao tác hai đầu](#concurrent-collections-016)

## <a id="concurrent-collections-001">Vì sao ArrayList và HashMap không an toàn khi truy cập chung?</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao ArrayList và HashMap không an toàn khi truy cập chung?

`ArrayList` và `HashMap` thuộc nhóm collection quen thuộc nhất của Java. Chúng phù hợp để quản lý dữ liệu trong một thread, hoặc khi **ứng dụng tự bảo vệ mọi truy cập chia sẻ bằng một giao thức đồng bộ đúng**. Nhưng nếu nhiều thread đồng thời đọc/ghi một instance mà không phối hợp, chúng **không cung cấp thread safety**: có thể mất cập nhật, quan sát dữ liệu không nhất quán, gặp exception, nhận kết quả sai hoặc phá vỡ những giả định về vòng đời dữ liệu.

Không có nghĩa “cứ hai thread cùng dùng `ArrayList`/`HashMap` là lập tức lỗi”. Nếu collection được **khởi tạo xong, công bố an toàn, rồi chỉ đọc** mà không có thread nào mutate, nhiều reader có thể sử dụng nó. Rủi ro chủ yếu xuất hiện khi có **mutable shared state** và giao thức đồng bộ không đáp ứng được cả ba điều kiện: **atomicity, visibility và ordering**.

Bài giải thích cụ thể những cấu trúc nội bộ dễ gặp race, tại sao `volatile` không sửa được chúng, khác biệt giữa “một method thread-safe” và “một workflow atomic”, cách tạo test có interleaving xác định và cách chọn `Collections.synchronizedList/Map`, `CopyOnWriteArrayList`, `ConcurrentHashMap`, immutable collection hoặc ownership.

Phạm vi: **Java concurrency nói chung**, Java 17+, không phụ thuộc Spring hoặc framework.

## 1. Thread-safe là gì trong ngữ cảnh collection?

Một collection thread-safe cần có **hợp đồng rõ ràng** cho các thao tác concurrent: những thao tác nào được phép gọi, tính nguyên tử của từng thao tác, người đọc quan sát giá trị bằng cơ chế memory-consistency nào, iterator thấy dữ liệu như thế nào, và compound operations có cần khóa bên ngoài không.

Một số tiêu chí kiểm tra:

| Tiêu chí | Câu hỏi |
| --- | --- |
| **Atomicity** | Hai thread cùng `add`/`put` có làm mất tác vụ hoặc ghi đè một cập nhật không? |
| **Visibility** | Thread đọc có quan sát được state đã được thread khác công bố theo Java Memory Model không? |
| **Ordering** | Có happens-before giữa cập nhật và việc đọc dựa vào cập nhật đó không? |
| **Structural invariants** | `size`, backing array, buckets, links, resize state có còn nhất quán khi nhiều writers thao tác không? |
| **Compound atomicity** | `contains` rồi `add`, hoặc `get` rồi `put`, có là một quyết định atomic không? |
| **Iteration semantics** | Iterator là fail-fast, snapshot, weakly consistent hay được bảo vệ bởi cùng lock? |
| **Lifecycle/ownership** | Ai được phép mutate, khi nào collection được publish và lúc nào kết thúc sử dụng? |

**Thread-safe không có nghĩa mọi chuỗi method call đều atomic.** Ví dụ một map có `get` và `put` thread-safe riêng lẻ vẫn có thể mắc lỗi nghiệp vụ khi code viết `get`, tính giá trị mới, rồi `put`. Cả chuỗi read–modify–write phải được đồng bộ hoặc thay bằng một operation atomic phù hợp.

## 2. Cấu trúc bên trong `ArrayList` vì sao dễ race?

`ArrayList<E>` về mặt khái niệm lưu dữ liệu trong **mảng nền** và duy trì thông tin số phần tử hiện có. Khi `add(E)`, nó cần phối hợp ít nhất các việc:

1. Xác định vị trí tiếp theo từ `size`.
2. Nếu cần, mở rộng hoặc thay backing array.
3. Ghi phần tử vào mảng tại vị trí mới.
4. Cập nhật `size` và thông tin structural modification phục vụ iterator.

Các bước đó **không được bảo vệ bằng mutual exclusion** trong một `ArrayList` thông thường. Nếu thread A và B cùng gọi `add`:

```text
Ban đầu: size = 0

Thread A                       Thread B
--------------------------     --------------------------
đọc size = 0                   đọc size = 0
chọn vị trí 0                  chọn vị trí 0
ghi phần tử A ở vị trí 0       ghi phần tử B ở vị trí 0
cập nhật size                  cập nhật size

Kết quả có thể không chứa đầy đủ cả A và B.
```

Đây là **một interleaving để giải thích**, không phải một lịch duy nhất hoặc pseudocode chính xác từng bytecode của mọi phiên bản JDK. Các triệu chứng có thể gồm **mất phần tử, sai kích thước, ô null bất ngờ, exception** hoặc những kết quả khó lặp. Khi đồng thời tăng capacity, các writers còn có thể tranh chấp việc thay mảng nền.

Một số điểm quan trọng:

- `new ArrayList<>(1000)` chỉ đặt **capacity dự kiến**, không biến các lệnh `add` thành thread-safe.
- `Collections.synchronizedList(...)` bao bọc các method bằng synchronization, nhưng **vẫn cần lock ngoài** cho nhiều thao tác kết hợp hoặc vòng lặp qua iterator.
- `ArrayList.set(index, value)` không làm mọi cách truy cập đồng thời an toàn; phải phân tích index ownership, không có structural mutation, publication và visibility. Không lấy trường hợp mỗi thread ghi ô riêng làm bằng chứng `ArrayList` nói chung thread-safe.
- Một biến reference kiểu `volatile List<T>` chỉ bảo đảm semantics của **reference**, không tự đồng bộ các field bên trong `ArrayList`.

### `size()` có thể là nguồn sự thật khi concurrent writes không?

Không nên. Nếu các thread mutate `ArrayList` mà không có happens-before hoặc mutual exclusion, việc đọc `size()` ở thread khác không bảo đảm phản ánh một snapshot hợp lệ. Ví dụ “thấy size=5” không chứng minh năm phần tử tương ứng đã được công bố và đọc một cách an toàn trong một giao thức không đồng bộ.

## 3. Cấu trúc bên trong `HashMap` vì sao không an toàn?

`HashMap<K,V>` quản lý một bảng buckets. Tùy hash và kích thước, các entries trong bucket có thể ở cấu trúc liên kết hoặc cây; khi số entries vượt ngưỡng, map có thể **resize** và phân phối lại entries sang table mới. `put` cần phối hợp việc tìm key, cập nhật hoặc thêm node, thay bucket links và cập nhật các thống kê như `size`.

Khi nhiều threads cùng `put`, `remove`, `compute` hoặc mutate state mà không có synchronization:

- Hai lần thêm khác key có thể cùng thay đổi cấu trúc hoặc cùng trigger resize.
- Một lần `put` có thể đè giá trị được tính từ snapshot cũ của một thread khác.
- Reader có thể không quan sát được cập nhật đúng cách do thiếu happens-before.
- Iteration trên structure đang bị thay đổi có thể phát sinh `ConcurrentModificationException` hoặc nhìn thấy dữ liệu không nhất quán; exception không được bảo đảm xuất hiện.
- Nội bộ buckets/links/size có thể bị cập nhật với các interleavings không thuộc hợp đồng an toàn của map.

**Không dùng `HashMap` cho concurrent structural mutations chỉ vì “key nào cũng khác nhau”.** Mutations khác key vẫn có thể cùng động tới table, resize, size và bucket metadata. Ngay cả khi table đủ capacity, Java API vẫn không cam kết an toàn cho nhiều writers không đồng bộ.

### Lưu ý về những câu chuyện “HashMap vô hạn vòng lặp”

Một số ví dụ nổi tiếng trong tài liệu cũ mô tả tình huống cấu trúc liên kết bị rối khi nhiều threads cùng resize một `HashMap` ở các phiên bản JDK cũ. Đây là ví dụ lịch sử, **không nên tuyên bố mọi JDK 17 sẽ nhất định bị vòng lặp vô hạn**. Kết luận đáng tin cậy hơn: `HashMap` **không có hợp đồng concurrent writes**, nên kết quả không được bảo đảm. Dựa vào tài liệu và synchronization đúng thay vì trông chờ một biểu hiện hỏng cụ thể.

## 4. Hai nhóm lỗi cần tách riêng

### 4.1. Data race / unsafe structural mutation

Ví dụ hai threads cùng gọi `ArrayList.add` lên một list chưa được bảo vệ, hoặc một thread `HashMap.put` trong khi thread khác đọc/duyệt map. Những method không được thiết kế cho concurrent access có thể đọc/ghi cùng mutable fields mà thiếu synchronization.

Hậu quả không chỉ là sai “business logic”; dữ liệu nội bộ của collection và khả năng hiển thị cập nhật đều có thể trở nên không nhất quán theo Java Memory Model.

### 4.2. Check–then–act / read–modify–write không nguyên tử

Ngay cả khi từng method **được khóa riêng**, workflow sau vẫn sai nếu muốn chỉ thêm duy nhất một giá trị:

```java
if (!list.contains("job-A")) {
    list.add("job-A");
}
```

Snippet này không độc lập; `list` là danh sách được chia sẻ. Hai threads có thể đều thấy chưa có `job-A`, sau đó cả hai cùng thêm. Nếu dùng `Collections.synchronizedList` nhưng không bọc toàn bộ `contains + add` trong **một khóa chung**, lỗi logic vẫn xảy ra.

Với map:

```java
Integer old = map.get("hits");
map.put("hits", old == null ? 1 : old + 1);
```

Đây cũng là snippet. Hai threads có thể cùng thấy `old=0`, rồi cả hai put `1`: **lost update**. Dùng `Collections.synchronizedMap` và gọi từng method riêng vẫn chưa làm cả chuỗi atomic.

**Phân biệt hai tầng lỗi:** “Collection tự bảo vệ internal state” và “Toàn bộ business operation bảo toàn invariant” là hai câu hỏi khác nhau. `ConcurrentHashMap` giải quyết rất nhiều nhu cầu đồng thời ở tầng collection, nhưng một workflow phức tạp gồm nhiều keys/resources vẫn cần thiết kế atomicity tổng thể.

## 5. Java 17: test điều phối để thấy check–then–act sai

Nhiều bài demo thử “chạy hai threads thật nhanh” rồi hy vọng lost update. Cách đó dễ flaky. Ví dụ sau cố tình cho hai threads **đọc snapshot cũ trước**, rồi **ghi lần lượt** theo latches. Điều này tạo kết quả sai **có thể dự đoán**, đồng thời không cố kích hoạt corruption nội bộ bằng concurrent structural writes.

Tạo tệp **`CheckThenActTrapDemo.java`**:

```java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CheckThenActTrapDemo {
    public static void main(String[] args) throws Exception {
        List<String> names = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();

        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch firstWritten = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Void> first = () -> {
            boolean absent = !names.contains("A");
            int old = counts.getOrDefault("hits", 0);
            bothRead.countDown();
            if (!bothRead.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Read rendezvous failed");
            }

            try {
                if (absent) names.add("A");
                counts.put("hits", old + 1);
            } finally {
                firstWritten.countDown();
            }
            return null;
        };

        Callable<Void> second = () -> {
            boolean absent = !names.contains("A");
            int old = counts.getOrDefault("hits", 0);
            bothRead.countDown();
            if (!bothRead.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Read rendezvous failed");
            }

            if (!firstWritten.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("First write missing");
            }
            if (absent) names.add("A");
            counts.put("hits", old + 1);
            return null;
        };

        try {
            Future<Void> a = pool.submit(first);
            Future<Void> b = pool.submit(second);
            a.get(5, TimeUnit.SECONDS);
            b.get(5, TimeUnit.SECONDS);

            System.out.println("listCount=" + names.size());
            System.out.println("mapCount=" + counts.get("hits"));
            System.out.println("expectedMapCount=2");
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac CheckThenActTrapDemo.java
java CheckThenActTrapDemo
```

Stdout khi cả hai worker hoàn thành trong deadline:

```text
listCount=2
mapCount=1
expectedMapCount=2
```

**Cách chứng minh output:**

1. `bothRead` buộc A và B **đều đọc** list rỗng, map chưa có key `hits` trước khi có writer nào được tiếp tục.
2. Worker thứ nhất thêm `"A"` và ghi `hits=1`.
3. `firstWritten` bảo đảm worker thứ hai **chỉ ghi sau** khi worker thứ nhất đã làm xong writes.
4. B vẫn dùng **snapshot cũ**: `absent=true`, `old=0`. Nó thêm một `"A"` nữa và ghi đè `hits=1`.
5. Hai business actions nhưng map còn `1`, list có hai phần tử trùng nhau. Giao thức latches làm kết quả xác định mà không cần `Thread.sleep`.

**Phân tích chính xác giới hạn của thí nghiệm:** Hai threads thực hiện **reads trước writes**, và các **writes được tuần tự hóa** bởi latch. Vì vậy ví dụ này chủ động minh họa **compound check–then–act không nguyên tử**, **không phải** một bằng chứng rằng cấu trúc bên trong `ArrayList` hoặc `HashMap` đã bị corruption do hai writers đồng thời. Để kiểm tra unsafe concurrent structural writes cần stress/lincheck/JMM reasoning riêng; không dùng một kết quả “may mắn đúng” để hợp thức hóa code không thread-safe.

## 6. Cách sửa 1: dùng một khóa cho toàn bộ workflow

Nếu collection thường được chia sẻ cho nhiều threads, có thể dùng **intrinsic monitor** bảo vệ tất cả reads/writes liên quan.

```java
private final Object lock = new Object();
private final List<String> names = new ArrayList<>();

boolean addIfAbsent(String name) {
    synchronized (lock) {
        if (names.contains(name)) {
            return false;
        }
        names.add(name);
        return true;
    }
}
```

Đây là snippet trong một class, cần import `List`/`ArrayList`. Khi mọi code đọc/ghi list đều dùng **cùng lock**, hai threads không thể đồng thời quyết định `name` chưa tồn tại. Phép `contains + add` trở thành một critical section.

Map counter tương tự:

```java
private final Object lock = new Object();
private final Map<String, Integer> counts = new HashMap<>();

int increment(String key) {
    synchronized (lock) {
        int next = counts.getOrDefault(key, 0) + 1;
        counts.put(key, next);
        return next;
    }
}
```

Đây là snippet trong class khác hoặc phần class phù hợp; không khai báo lại các field cùng tên trong một class. **Tất cả paths** đọc/ghi state phục vụ invariant phải theo protocol lock: nếu một thread lén gọi `counts.put` không khóa, bảo đảm bị phá.

### Lock granularity

Một khóa chung đơn giản, dễ chứng minh correctness, nhưng có thể gây contention nếu rất nhiều threads cập nhật **những keys độc lập**. Khi đó `ConcurrentHashMap` thường là lựa chọn tốt. Tuy nhiên nếu invariant trải trên **hai hoặc nhiều collections** (ví dụ ghi order và trừ quota), một lock hoặc transaction có phạm vi đủ lớn vẫn có thể cần thiết.

**Không giữ lock khi làm I/O dài, gọi callback không đáng tin hoặc `Future.get`** nếu không có lý do; điều đó có thể tạo deadlock và tail latency. Hãy tách phần tính toán/side effect sao cho invariant vẫn đúng.

## 7. Cách sửa 2: `Collections.synchronizedList` và `synchronizedMap`

JDK có các wrapper:

```java
List<String> names =
        java.util.Collections.synchronizedList(
                new java.util.ArrayList<>());

Map<String, Integer> counts =
        java.util.Collections.synchronizedMap(
                new java.util.HashMap<>());
```

Các phương thức collection được wrapper đồng bộ theo hợp đồng. Đối với các operation đơn lẻ như `add/get/put` qua wrapper, đây là cách đơn giản để bảo vệ state nội bộ.

**Nhưng:** synchronization của từng call **không tự động gộp** một cặp method thành transaction:

```java
if (!names.contains("A")) {
    names.add("A");
}
```

Dù `names` ở đây là synchronized wrapper, hai threads vẫn có thể xen vào giữa `contains` và `add`. Để atomic:

```java
synchronized (names) {
    if (!names.contains("A")) {
        names.add("A");
    }
}
```

Với map:

```java
synchronized (counts) {
    int old = counts.getOrDefault("hits", 0);
    counts.put("hits", old + 1);
}
```

**Điều kiện:** mọi caller phải sử dụng **chính wrapper** và khóa tương ứng. Nếu còn giữ reference tới `new ArrayList<>()`/`new HashMap<>()` gốc rồi mutate qua reference đó, wrapper không thể bảo vệ những paths đi vòng.

### Iteration của synchronized wrapper vẫn cần khóa ngoài

Theo JDK documentation của synchronized collection wrappers, khi duyệt bằng `Iterator`/`Spliterator` hoặc stream, caller phải manually synchronize trên wrapper trong suốt traversal:

```java
synchronized (names) {
    for (String name : names) {
        consume(name);
    }
}
```

Đây là snippet với `consume` là method của ứng dụng. Tương tự:

```java
synchronized (counts) {
    for (Map.Entry<String, Integer> e
            : counts.entrySet()) {
        inspect(e);
    }
}
```

Đây là snippet, `inspect` do ứng dụng định nghĩa. Chỉ gọi `names.iterator()` trong synchronized rồi ra ngoài mới `next()` là **chưa đủ** nếu writers có thể mutate giữa các bước.

Nếu callback trong iteration đắt hoặc blocking, có thể **copy snapshot dưới lock** rồi iterate ngoài lock. Snapshot chỉ chụp references/values tại một thời điểm; nếu elements mutable và bị sửa sau đó, cần thêm immutable value/ownership.

## 8. Cách sửa 3: `ConcurrentHashMap` và atomic map operations

`ConcurrentHashMap<K,V>` được thiết kế cho concurrent access. Nó cho phép nhiều reads và updates theo contract, không dùng một global monitor cho mọi operation như synchronized wrapper ở nhiều trường hợp. Nó có những operation như:

- `putIfAbsent(key, value)`: chỉ đặt nếu key chưa có, theo một update atomic phù hợp.
- `computeIfAbsent(key, mappingFunction)`: tính value nếu key chưa mapped và cập nhật theo contract atomic.
- `compute(key, remappingFunction)`: tính lại value dựa trên mapping hiện hành cho key đó.
- `merge(key, value, remappingFunction)`: kết hợp một giá trị mới với giá trị hiện tại một cách atomic trên mapping được xử lý.
- `replace(key, oldValue, newValue)`: CAS-like conditional replacement cho mapping.
- `remove(key, value)`: chỉ xóa nếu còn mapped tới value mong đợi.

Thay vì:

```java
Integer old = concurrentMap.get("hits");
concurrentMap.put("hits", old == null ? 1 : old + 1);
```

vẫn có **lost update logic** vì hai thao tác atomic riêng biệt không thành một transaction, hãy dùng:

```java
concurrentMap.merge("hits", 1, Integer::sum);
```

Đây là snippet, `concurrentMap` là `ConcurrentHashMap<String,Integer>`. `merge` bảo vệ operation cập nhật mapping ở key đó. Với cùng một key, hai calls `merge("hits",1,Integer::sum)` hợp lệ sẽ cho tổng tăng hai lần thay vì cùng ghi đè snapshot.

**Không suy ra `ConcurrentHashMap` giải quyết mọi invariant nhiều keys.** Ví dụ kiểm tra `mapA` còn quota rồi ghi `mapB` ở một map khác không trở thành atomic chỉ nhờ hai maps đều là concurrent maps. Cần lock, transaction, state machine hoặc mô hình ownership cho toàn workflow.

### Một số giới hạn quan trọng

- `ConcurrentHashMap` không cho phép **null keys/values**, khác `HashMap`. Code dùng `map.get(key)==null` để phân biệt absent và “mapped to null” phải điều chỉnh.
- Iterator/view thường **weakly consistent**, có thể quan sát một phần cập nhật diễn ra trong lúc duyệt; nó không phải snapshot toàn map được đóng băng.
- Mapping/remapping function của `compute/merge` nên **ngắn, thuần về mặt business effect và không blocking lâu**. Trong một số tình huống, other updates có thể bị ảnh hưởng trong lúc computation diễn ra; không gọi lại chính map theo cách recursive có nguy cơ bị cấm hoặc deadlock.
- Method có suffix `computeIfAbsent` không phải nơi thích hợp để gửi email/charge payment với giả định exactly-once toàn hệ thống; lỗi process/network và vòng đời dữ liệu vẫn cần thiết kế.
- Compound operations trên nhiều entries vẫn không có atomic snapshot mặc định.

### `putIfAbsent` khác `containsKey` rồi `put`

```java
String old = concurrentMap.putIfAbsent("id-7", "reserved");
if (old == null) {
    // Thread này đã đặt mapping mới khi key trước đó absent.
}
```

Đây là snippet; giả định `concurrentMap` có type `ConcurrentHashMap<String,String>`. So với `containsKey + put`, method này có atomicity đúng cho **một key**. Nhưng khi “đặt chỗ” còn phải ghi DB, trừ quota, thanh toán và gửi email, việc map nhận key mới **không** chứng minh toàn bộ nghiệp vụ đã commit.

## 9. Cách sửa 4: `CopyOnWriteArrayList` cho read-mostly

`CopyOnWriteArrayList<E>` phù hợp với nhiều readers và số lần thêm/xóa thấp. Mỗi structural write tạo một backing array mới theo thiết kế copy-on-write; reader và iterator có thể quan sát **snapshot** đã tồn tại ở thời điểm tạo iterator.

Ưu điểm:
- Traversal không ném `ConcurrentModificationException` theo kiểu iterator fail-fast của `ArrayList`.
- Iteration không yêu cầu giữ global monitor bao quanh toàn bộ vòng đọc.
- Hữu ích cho danh sách listeners/configurations nhỏ, ít thay đổi.

Nhược điểm:
- `add/remove` tốn chi phí copy array và allocation, không phù hợp write-heavy hoặc list quá lớn.
- Iterator snapshot **không tự thấy** elements được thêm sau khi iterator được tạo.
- Elements mutable bên trong list **không tự trở nên immutable/thread-safe**.
- Một check–then–act dùng hai methods vẫn không mặc nhiên atomic. `CopyOnWriteArrayList.addIfAbsent` là operation riêng có nghĩa phù hợp hơn nếu mục tiêu là thêm value tối đa một lần.

Ví dụ:

```java
CopyOnWriteArrayList<String> listeners =
        new CopyOnWriteArrayList<>();

listeners.addIfAbsent("listener-A");
listeners.addIfAbsent("listener-A");
// Chỉ một element "listener-A" sau hai calls tuần tự.
```

Đây là snippet, cần import `java.util.concurrent.CopyOnWriteArrayList`. Nếu cần bảo đảm uniqueness theo **business key** trên các objects có semantics khác, phải phân tích `equals` và lựa chọn structure/operation phù hợp.

## 10. Java 17: giải pháp đồng thời và iterator snapshot

Chương trình sau chạy hai producers, mỗi worker cập nhật `ConcurrentHashMap.merge` và `Collections.synchronizedList.add` 500 lần. Main chỉ đọc kết quả sau khi `Future.get` xác nhận cả hai đã xong. Nó cũng minh họa iterator snapshot của `CopyOnWriteArrayList`.

Tạo **`ConcurrentCollectionsFixDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ConcurrentCollectionsFixDemo {
    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, Integer> counts =
                new ConcurrentHashMap<>();
        List<Integer> values =
                Collections.synchronizedList(new ArrayList<>());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Void> worker = () -> {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start gate failed");
            }
            for (int i = 0; i < 500; i++) {
                counts.merge("hits", 1, Integer::sum);
                values.add(i);
            }
            return null;
        };

        try {
            Future<Void> a = pool.submit(worker);
            Future<Void> b = pool.submit(worker);
            start.countDown();
            a.get(5, TimeUnit.SECONDS);
            b.get(5, TimeUnit.SECONDS);

            long sum = 0;
            synchronized (values) {
                for (int value : values) {
                    sum += value;
                }
            }

            CopyOnWriteArrayList<String> copyOnWrite =
                    new CopyOnWriteArrayList<>();
            copyOnWrite.add("before");
            Iterator<String> snapshot = copyOnWrite.iterator();
            copyOnWrite.add("after");

            System.out.println("mapCount=" + counts.get("hits"));
            System.out.println("listSize=" + values.size());
            System.out.println("listSum=" + sum);
            System.out.println("snapshotFirst=" + snapshot.next());
            System.out.println("snapshotHasNext=" + snapshot.hasNext());
            System.out.println("currentSize=" + copyOnWrite.size());
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Chạy JDK 17+:

```bash
javac ConcurrentCollectionsFixDemo.java
java ConcurrentCollectionsFixDemo
```

Stdout bình thường:

```text
mapCount=1000
listSize=1000
listSum=249500
snapshotFirst=before
snapshotHasNext=false
currentSize=2
```

**Giải thích:**

- `ConcurrentHashMap.merge` bảo đảm hai threads không mất increment trên key `hits`.
- Wrapper `synchronizedList` bảo vệ từng `add`, nên list cuối có đủ 1.000 elements.
- Việc iterate được bao bởi `synchronized (values)`; khi hai workers đã hoàn thành, kết quả tổng mỗi dãy 0..499 được ghi hai lần: `2 × (499 × 500 / 2) = 249500`.
- Iterator copy-on-write được tạo **trước** khi thêm `"after"`, nên snapshot còn một element `"before"`, nhưng collection hiện tại có hai.
- `Future.get(5,SECONDS)` đặt deadline cho bài demo và propagate worker exceptions; code dùng `shutdownNow()` trong finally như cleanup pool. Deadline không hứa hủy mọi loại công việc blocking trong production.

**Đúng nhưng chưa phải tối ưu:** Với workload nhiều writes, `synchronizedList` có thể trở thành điểm contention, còn `CopyOnWriteArrayList` có chi phí sao chép. Chọn đúng collection theo pattern sử dụng, không áp một tên “concurrent” cho mọi tình huống.

## 11. `ConcurrentModificationException`: không phải “cơ chế bảo vệ thread”

Iterator của `ArrayList` và `HashMap` thường có behavior **fail-fast**: nếu cấu trúc collection được sửa ở ngoài iterator trong khi duyệt, nó có thể ném `ConcurrentModificationException`. Tuy nhiên tài liệu JDK nhấn mạnh không thể bảo đảm phát hiện tất cả concurrent modifications; fail-fast là cơ chế **best-effort để phát hiện bug**, không được dùng làm protocol correctness.

Một chương trình một thread cũng có thể ném exception:

Tạo **`FailFastIsNotSafetyDemo.java`**:

```java
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

public class FailFastIsNotSafetyDemo {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        names.add("A");
        names.add("B");

        Iterator<String> iterator = names.iterator();
        names.add("C"); // Structural modification outside iterator.

        boolean detected = false;
        try {
            iterator.next();
        } catch (ConcurrentModificationException e) {
            detected = true;
        }

        System.out.println("detected=" + detected);
        System.out.println("size=" + names.size());
    }
}
```

Biên dịch/chạy:

```bash
javac FailFastIsNotSafetyDemo.java
java FailFastIsNotSafetyDemo
```

Stdout bình thường trên JDK 17 cho thứ tự này:

```text
detected=true
size=3
```

Ví dụ **không có hai threads** nhưng vẫn ném `ConcurrentModificationException` do structural modification sau khi iterator được tạo. Điều đó chứng minh tên exception **không đồng nghĩa có race giữa hai thread**.

Với mutation đồng thời thực sự, đôi khi sẽ ném exception, đôi khi không. **Không viết logic kiểu “nếu chưa thấy ConcurrentModificationException thì list an toàn”**. Bảo vệ traversal bằng cùng lock, dùng snapshot hoặc collection có iteration semantics phù hợp.

### Iterator của synchronized wrapper

`Collections.synchronizedList`/`synchronizedMap` bảo vệ methods nhưng iterator **không tự khóa cả vòng duyệt**. Phải synchronize thủ công trên wrapper khi lấy iterator và duyệt. Trong khi đó, `CopyOnWriteArrayList` có snapshot iterator; `ConcurrentHashMap` có weakly consistent iterator. Cả hai không có cùng semantics và không thể thay nhau tùy tiện nếu yêu cầu một snapshot logic duy nhất.

## 12. `volatile` có sửa được `ArrayList`/`HashMap` không?

Ví dụ:

```java
private volatile List<String> shared = new ArrayList<>();

void add(String value) {
    shared.add(value);
}
```

Đây là snippet trong class. `volatile` áp dụng cho **việc đọc/ghi reference `shared`**, không tự đặt monitor/CAS vào các thao tác `ArrayList.add`. Nếu hai threads cùng gọi `add`, chúng vẫn đang mutate **cùng object** với những field không được thiết kế concurrent.

Tương tự:

```java
private volatile Map<String, Integer> map = new HashMap<>();
```

không khiến `map.put` thread-safe.

### Khi volatile giúp được: publish snapshot, không mutate sau publish

Một thiết kế khác là:

```java
private volatile List<String> snapshot = List.of();

void replaceAllNames(List<String> source) {
    snapshot = List.copyOf(source);
}

List<String> readSnapshot() {
    return snapshot;
}
```

Đây là snippet trong class, dùng `java.util.List`. Ý tưởng là **tạo collection mới** rồi publish reference qua volatile; readers quan sát một list snapshot được tạo đầy đủ, không mutate list ấy. Tuy nhiên, nếu nhiều writers cùng thay snapshot dựa vào state cũ, cần lock/CAS để tránh **lost update giữa các snapshots**. Ngoài ra `List.copyOf` là **shallow immutability** đối với elements: nếu element là object mutable được chia sẻ, nó vẫn có thể bị mutate bên ngoài.

**Nhận xét:** immutable snapshot phù hợp với cấu hình/read-mostly; không phải thay thế cho concurrent updates từng phần tử ở workload write-heavy.

## 13. Read-only concurrent access có thể đúng khi nào?

Một `ArrayList`/`HashMap` **không bị mutate nữa** sau khi xây dựng có thể được nhiều threads chỉ đọc, với điều kiện object và dữ liệu liên quan được **safely published**.

Một số cơ chế publication có hợp đồng phù hợp:

- Khởi tạo trước `Thread.start()` rồi truyền reference an toàn tới các threads được start sau đó.
- Publish bằng `volatile` hoặc `AtomicReference`.
- Công bố dưới `synchronized`/`Lock` và readers đọc với cùng cơ chế.
- Truyền qua `BlockingQueue`/concurrent structures với memory-consistency contract phù hợp.
- Dùng immutable values/`List.of`/`Map.of` khi không cần mutate.

**Không có nghĩa “chỉ đọc thì khỏi nghĩ visibility”.** Nếu một thread tạo và mutate map rồi rò reference sang thread khác qua field thường không đồng bộ, reader có thể chưa thấy một trạng thái được công bố đúng.

### Immutable collection không tự làm immutable elements

`List.of(person1, person2)` không cho thêm/xóa/thay phần tử qua list API, nhưng nếu `person1` là object mutable được một thread khác sửa, dữ liệu bên trong vẫn có race. Immutable collection bảo vệ **cấu trúc collection**; data model bên trong vẫn phải tuân theo ownership/immutability/JMM.

### `Collections.unmodifiableList` không phải synchronized snapshot

`Collections.unmodifiableList(mutableList)` thường chỉ tạo **read-only view** của underlying list. Nếu một thread khác giữ `mutableList` và thay đổi nó, view có thể thấy updates và các vấn đề tương ứng. Đừng gọi một unmodifiable view của mutable shared state là “immutable thread-safe” khi dữ liệu nền còn bị mutate.

## 14. `HashMap.get` đồng thời với `HashMap.put` có an toàn?

Không có bảo đảm thread safety nếu thread A đang mutate map còn thread B `get` mà không đồng bộ. Dù B “chỉ đọc”, cùng object đang bị sửa nên có thể không thấy value mới hoặc quan sát không nhất quán theo JMM. Không nên dùng `HashMap` như một concurrent cache chỉ vì “cache miss thỉnh thoảng không sao”.

Giải pháp tùy contract:

- **Concurrent mutable cache:** dùng `ConcurrentHashMap` với API atomic `computeIfAbsent`, `putIfAbsent` khi cần.
- **Snapshot config/map hiếm thay đổi:** build map mới, immutable copy, publish an toàn bằng volatile/atomic reference.
- **Nhiều keys liên quan trong một transaction:** dùng lock chung hoặc state snapshot có invariant nhiều keys.
- **Cache có eviction, expiry, loading, capacity:** cân nhắc implementation cache chuyên dụng phù hợp; `ConcurrentHashMap` không tự cung cấp TTL, bounded memory hoặc exactly-once external load.

### Vì sao `getOrDefault + put` vẫn sai ngay cả với concurrent map?

`ConcurrentHashMap.getOrDefault` và `put` đều có semantics phù hợp khi gọi riêng, nhưng một thread vẫn có thể chen vào giữa chúng. Muốn tăng counter, dùng `merge` hoặc `compute` trên cùng key; muốn chỉ đăng ký nếu chưa có, dùng `putIfAbsent`. **Thread-safe method** không tự động nâng mọi business sequence lên atomicity.

## 15. HashMap key, hashCode và mutable keys

Ngay cả trong **một thread**, dùng object mutable làm key mà `equals/hashCode` thay đổi sau khi `put` có thể khiến `get` không còn tìm được mapping. Đây là bug **khác** với concurrency.

Trong nhiều threads, rủi ro lớn hơn: nếu các thread đồng thời mutate các fields ảnh hưởng `hashCode/equals` của key đang nằm map, việc tìm key và tính toán hash có thể không nhất quán. **ConcurrentHashMap không chữa mutable-key bug**. Hãy chọn immutable keys hoặc ngữ nghĩa khóa ổn định trong suốt thời gian key ở map.

Ví dụ minh họa:

```java
record Key(String region, long id) {}
```

Java 17 `record` với các components bất biến về reference/primitive là lựa chọn hợp lý cho key đơn giản. Nhưng record chứa mutable object vẫn có thể có rủi ro nếu thành phần đó bị sửa.

**Một nguyên tắc độc lập:** không mutate các state quyết định `equals/hashCode` của key sau khi thêm vào hash-based map.

## 16. Tối ưu lựa chọn collection theo workload

| Nhu cầu | Lựa chọn thường phù hợp | Điều phải hiểu |
| --- | --- | --- |
| Một thread sở hữu danh sách | `ArrayList` | Không cần chi phí concurrent abstraction |
| Nhiều readers sau khi xây dựng xong | Safely published immutable/snapshot | Không mutate sau publish, chú ý mutable elements |
| Nhiều threads mutate list, order/index | `Collections.synchronizedList` + lock protocol | Compound operations/iteration cần lock chung |
| Danh sách listeners nhỏ, rất ít writes | `CopyOnWriteArrayList` | Write copy tốn memory/CPU; iteration snapshot |
| Concurrent map nhiều keys độc lập | `ConcurrentHashMap` | Operations atomic theo key khi dùng đúng method |
| Map ít truy cập, dễ khóa toàn bộ | `Collections.synchronizedMap` | Tránh bỏ qua wrapper và compound atomicity |
| Ordered concurrent set/map | `ConcurrentSkipListMap/Set` khi cần ordering | Chi phí và semantics khác hash map |
| Counter theo key | `ConcurrentHashMap.merge/compute` | Tránh `get + put` |
| Nhiều writes vào cùng một hot counter | `AtomicLong`/`LongAdder` tùy semantics | LongAdder không phải atomic snapshot khi updates concurrent |
| Multi-object invariant mạnh | Shared lock/transaction/immutable aggregate CAS | Một atomic method trên một map chưa đủ |

**Đừng thay mọi `ArrayList` bằng `CopyOnWriteArrayList` và mọi `HashMap` bằng `ConcurrentHashMap` một cách máy móc.** Nếu instance chỉ dùng trong method của một worker và không escape, thread-safe collection không cần thiết. Nếu cần toàn bộ invariant giữa nhiều collections, riêng việc chọn tên collection cũng không đủ.

## 17. Đồng bộ theo một khóa: tại sao có thể vẫn deadlock?

Nếu dùng synchronized wrapper hoặc một shared monitor, phải bảo đảm lock order rõ. Ví dụ thread A giữ `lockA` rồi gọi vào code cần `lockB`, còn thread B giữ `lockB` rồi chờ `lockA`: có nguy cơ deadlock. Nếu giữ lock trong lúc gọi I/O dài, các threads khác bị trì hoãn.

Chính sách tốt:

1. Chỉ giữ lock khi đọc/sửa **đúng state cần bảo vệ**.
2. Giữ một **thứ tự lấy locks** nhất quán trên toàn ứng dụng khi cần nhiều locks.
3. Không gọi callback tùy ý hay tác vụ blocking trong critical section nếu tránh được.
4. Không expose backing collection mutable cho code bên ngoài đi vòng protocol.
5. Đóng gói access qua methods có invariant rõ ràng, thay vì giao toàn bộ list/map cho mọi nơi.
6. Khi dùng synchronization wrapper, khóa trên **wrapper chính nó** nếu contract iterator yêu cầu, không tự chọn một monitor khác ở caller.

Nếu code có nhiều readers, một `ReadWriteLock` có thể giúp trong vài workload nhưng **không luôn nhanh hơn** một monitor hoặc `ConcurrentHashMap`. Đo dưới tải thực.

## 18. Không dựa vào test stress đơn lẻ để chứng minh thread safety

Một test kiểu:

```java
List<Integer> list = new ArrayList<>();
for (int i = 0; i < 10_000; i++) {
    new Thread(() -> list.add(1)).start();
}
```

là **snippet sai để dùng trong code sản phẩm**: nó tạo quá nhiều threads, không đợi chúng hoàn tất, không định nghĩa shutdown và làm stress sai đối tượng. Có lần bạn sẽ thấy kết quả trùng kỳ vọng, có lần không; có thể OOM hoặc lỗi unrelated tới `ArrayList`.

Để kiểm thử có ích:

- **Định nghĩa invariant:** không mất job, không duplicate booking, count đúng, không phá thứ tự nếu contract yêu cầu.
- **Ép interleavings:** dùng `CountDownLatch`/`CyclicBarrier` đặt checkpoint trước read–modify–write, như `CheckThenActTrapDemo`.
- **Stress thực sự:** dùng số workers hữu hạn, `Future.get(timeout)`, kiểm tra kết quả/tính nhất quán, lặp nhiều lần nhưng không gọi “không lỗi” là proof.
- **Kiểm tra publication/JMM:** reason về happens-before và data races; một stress test trên một CPU/JDK không bao phủ mọi lịch.
- **Phân biệt test nội bộ với business logic:** check–then–act có thể sai ngay cả khi các structural writes được serialize; unsafe concurrent structural mutation có thể xảy ra ngoài test deterministic.
- **Dùng công cụ đúng:** JMH dành cho performance benchmark, không dùng kết quả benchmark để chứng minh thread safety; JCStress hoặc test điều phối hỗ trợ kiểm tra interleavings/memory effects, nhưng vẫn cần lý luận đúng contract.
- **Không chạy forever:** đặt timeout, cleanup executor, và báo exception workers về thread chính.

### Test không nên bắt `ConcurrentModificationException` làm dấu hiệu bắt buộc

Fail-fast chỉ best-effort; một mutation concurrent có thể không bị iterator phát hiện. Ngược lại CME cũng xuất hiện khi **cùng thread** mutate list trong lúc dùng iterator sai cách. Hãy assert invariant và đồng bộ đúng thay vì mong exception báo lỗi.

## 19. Ba nguyên tắc chính từ Java Memory Model

### 19.1. Mutual exclusion/atomicity

Một lock chung khiến cùng lúc chỉ một thread thực hiện critical section, hoặc một operation atomic (như `ConcurrentHashMap.merge`) cập nhật đúng key theo contract. Không có atomicity, hai writers có thể dựa trên snapshot cũ.

### 19.2. Visibility và happens-before

Nếu writer thay state rồi publish bằng volatile/atomic/lock hoặc concurrent queue, reader quan sát state theo happens-before phù hợp. Nếu không, một reader `HashMap.get` có thể không thấy state mới một cách đáng tin, và đọc `ArrayList.size` không đồng nghĩa có snapshot hợp lệ.

### 19.3. Ordering

Compiler, JIT và CPU có thể thực hiện các tối ưu theo Java Memory Model. Không dùng ý tưởng “trên máy tôi hai lệnh luôn chạy theo thứ tự nguồn” để suy ra thread khác thấy các field theo thứ tự đó mà không có đồng bộ hợp lệ.

Một reference `final` hoặc list/map container bất biến có thể hỗ trợ thiết kế, nhưng các yêu cầu safe publication và mutation ownership vẫn phải được phân tích cho từng object.

## 20. Những lỗi phổ biến

| Lỗi | Vì sao sai | Sửa bằng gì? |
| --- | --- | --- |
| Coi `ArrayList.add` là atomic trong multi-writer | `size`/backing array không được bảo vệ | Lock hoặc thread-safe collection |
| Coi `HashMap.put` an toàn vì keys khác nhau | Resize, table, size là shared structure | `ConcurrentHashMap` hoặc lock |
| `volatile List/Map` rồi mutate nội bộ | Volatile chỉ áp dụng cho reference access | Bảo vệ mutable object |
| `Collections.synchronizedList` rồi `contains + add` không khóa | Compound operation vẫn xen ngang | `synchronized(list)` bao toàn bộ |
| Iterator của synchronized wrapper không khóa | Lệnh `next` chạy trong lúc writer mutate | Khóa wrapper trong toàn bộ traversal |
| `ConcurrentHashMap.get + put` để tăng counter | Hai method riêng không gộp atomic | `merge`/`compute` |
| Dùng mutable key thay đổi `hashCode` | Mapping không còn tra cứu ổn định | Immutable/stable keys |
| Nghĩ `ConcurrentModificationException` luôn xuất hiện | Fail-fast best-effort | Không dựa vào exception cho correctness |
| Nghĩ không thấy CME thì an toàn | Data races có thể im lặng | Chọn đúng synchronization contract |
| Chỉ test `Thread.sleep` | Scheduling không xác định | Latch/barrier có deadline |
| Coi `ConcurrentHashMap` là transaction multi-key | Per-key update không bảo vệ toàn workflow | Lock/transaction/aggregate state |
| Nghĩ `CopyOnWriteArrayList` miễn phí | Writes copy toàn mảng | Chỉ dùng read-mostly |
| Dùng `List.copyOf` nhưng elements mutable | Immutability nông | Deep immutable/ownership |
| Dùng `Collections.unmodifiableList` trên list còn mutate | View không phải snapshot | Snapshot/immutable publication |
| Expose backing map qua getter | Caller có thể bypass protocol | Trả snapshot hoặc interface hẹp |
| Giữ lock khi gọi I/O/callback lâu | Contention/deadlock | Giảm critical section |
| Chọn collection “concurrent” là xong | Business invariant có thể nhiều steps | Thiết kế atomic workflow |
| Coi stress không lỗi là proof | Thiếu bao phủ mọi interleaving | Reason JMM + tests có chủ đích |

## 21. Câu hỏi tự kiểm tra

**1. Vì sao `new ArrayList<>(1000)` không an toàn khi hai threads `add`?** Capacity có sẵn không biến việc chọn index, ghi array và cập nhật size thành một atomic operation.

**2. Nếu hai threads `HashMap.put` vào hai keys khác nhau, có luôn an toàn không?** Không. Chúng vẫn mutate shared buckets/table/size và có thể cùng resize.

**3. Hai readers chỉ đọc một `HashMap` có an toàn không?** Có thể, nếu map đã được xây dựng và **công bố an toàn** rồi không bị mutate, cùng với dữ liệu elements được quản lý đúng.

**4. `volatile Map` có làm `map.put` thread-safe không?** Không. Volatile reference không bảo vệ mutable fields bên trong map.

**5. Vì sao `Collections.synchronizedList` vẫn có thể thêm duplicate?** `contains` và `add` là hai synchronized method calls tách biệt. Cần lock chung bao cả workflow hoặc API atomic phù hợp.

**6. `ConcurrentHashMap` có làm `get + put` tăng counter atomic không?** Không. Dùng `merge("hits",1,Integer::sum)` hoặc `compute`.

**7. Thấy `ConcurrentModificationException` có chắc có hai threads không?** Không. Cùng thread mutate ngoài iterator cũng có thể ném.

**8. Không thấy `ConcurrentModificationException` thì list/map an toàn?** Không. Fail-fast là best-effort và không phải safety mechanism.

**9. Iterator của `CopyOnWriteArrayList` có thấy element thêm sau khi tạo iterator không?** Không; nó duyệt snapshot của thời điểm tạo iterator.

**10. `ConcurrentHashMap` iterator có là transaction snapshot tuyệt đối không?** Không. Nó weakly consistent và có thể quan sát một phần updates khi đang iterate.

**11. `Collections.unmodifiableList` có phải immutable snapshot không?** Không nhất thiết. Nó có thể là view trên underlying mutable list.

**12. Khi nào `HashMap` vẫn hợp lý trong code concurrent?** Khi map chỉ thuộc một thread, hoặc immutable sau safe publication, hoặc mọi reads/writes mutable đều được bảo vệ bởi giao thức synchronization hợp lệ.

**13. Dùng `ConcurrentHashMap.putIfAbsent` có giúp tránh race đăng ký một key không?** Có đối với atomic mapping cho key, nhưng không tự tạo transaction gồm DB/payment/resource bên ngoài.

**14. Vì sao test hai threads với latches mà vẫn có thể sai logic ngay cả khi writes serialize?** Vì cả hai đã đọc cùng snapshot cũ trước khi lần write đầu xảy ra. Đây là check–then–act race, không cần concurrent writes cùng thời điểm.

**15. Muốn cập nhật nhiều keys cùng một invariant thì dùng gì?** Cần lock/transaction/immutable aggregate và state machine phù hợp; các atomic operations trên từng key không tự tạo atomic multi-key operation.

## 22. Checklist lựa chọn và triển khai

1. **Collection có bị share cho nhiều threads không?** Nếu không, dùng `ArrayList`/`HashMap` bình thường.
2. **Có mutation sau khi publish không?** Nếu không, dùng safely published immutable/read-only snapshot.
3. **Có nhiều concurrent writers không?** Chọn synchronization hoặc collection có đúng hợp đồng.
4. **Invariant chỉ liên quan một key hay nhiều keys?** `ConcurrentHashMap.merge/compute` phù hợp per-key, lock/transaction cho multi-state.
5. **List cần snapshot iteration hay cập nhật liên tục?** `CopyOnWriteArrayList` cho read-mostly; synchronized list cho patterns khác.
6. **Có compound check–then–act không?** Chọn atomic method hoặc khóa chung bao toàn bộ.
7. **Iteration qua wrapper có được khóa đầy đủ không?** `synchronized(wrapper)` khi cần.
8. **Có reference tới backing collection bị lộ ra ngoài không?** Ngăn bypass protocol.
9. **Elements/keys có mutable không?** Ổn định `equals/hashCode`, cân nhắc deep immutability.
10. **Có thời gian chờ/side effects trong critical section không?** Giảm hoặc tổ chức lại.
11. **Có test với latches và invariant không?** Không phụ thuộc may rủi của `Thread.sleep`.
12. **Có test failure/timeout/executor cleanup không?** Không bỏ worker sống mãi.
13. **Có đo contention, throughput và allocation trước khi tối ưu không?** Correctness đi trước.
14. **Có giải thích memory visibility và publication cho readers không?** Thread safety không chỉ là khóa writes.
15. **Có chọn đúng iteration semantics không?** Fail-fast khác weakly consistent và snapshot.

**Kết luận:** `ArrayList` và `HashMap` **không tự bảo vệ mutable internal state khi được nhiều threads đọc/ghi mà thiếu synchronization**. Lỗi có hai tầng: **data race/structural inconsistency** do mutations không an toàn, và **business check–then–act không nguyên tử** do ghép nhiều operations tách biệt. Cách chữa phải dựa vào invariant: dùng một lock chung, synchronized wrapper kèm external lock khi iterate/compound, `ConcurrentHashMap` với `merge/compute/putIfAbsent`, `CopyOnWriteArrayList` cho read-mostly, hoặc immutable snapshot được công bố an toàn. Quan trọng nhất là **thiết kế chính sách ownership và happens-before cho toàn workflow**, không chỉ đổi tên collection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-002">ConcurrentHashMap: putIfAbsent(), compute(), merge()</a>

<details>
<summary>Xem chi tiết</summary>

# ConcurrentHashMap: `putIfAbsent()`, `compute()` và `merge()` trong Java

## 1. Vấn đề: một Map được nhiều thread cập nhật

Trong Java concurrency, `Map<K,V>` thường dùng để lưu cấu hình theo key, số lần truy cập, trạng thái phiên làm việc, cache hoặc các phép tổng hợp. Khi nhiều thread cùng truy cập, cần phân biệt hai yêu cầu:

1. **Cấu trúc Map an toàn:** các thao tác không làm hỏng cấu trúc dữ liệu nội bộ khi nhiều thread đọc/ghi.
2. **Một thay đổi nghiệp vụ nguyên tử:** phép “kiểm tra → tính toán → cập nhật” phải không bị thread khác chen vào làm mất cập nhật hoặc phá điều kiện.

Một `HashMap` thông thường **không thread-safe** khi nhiều thread cùng sửa. Chỉ đổi thành `ConcurrentHashMap` giúp các thao tác của Map có cơ chế đồng thời phù hợp; tuy nhiên không có nghĩa **mọi chuỗi lệnh ứng dụng** trên Map đều trở thành một transaction.

Ví dụ phản diện:

```java
Integer old = counts.get("java");
counts.put("java", old == null ? 1 : old + 1);
```

Đây là đoạn minh họa, `counts` được nhiều thread dùng chung. Hai worker có thể cùng đọc `5`, đều tính `6` và ghi `6`. Kết quả mất một lần tăng (**lost update**) ngay cả khi `counts` là `ConcurrentHashMap`.

Giải pháp là dùng những thao tác **nguyên tử theo key**, như:

- `putIfAbsent(key, value)`: chỉ thêm khi key chưa có ánh xạ.
- `compute(key, remappingFunction)`: tính lại và cập nhật/xóa giá trị theo **giá trị hiện tại** của key.
- `merge(key, value, remappingFunction)`: thêm giá trị ban đầu nếu key vắng mặt; nếu đã có thì kết hợp với giá trị cũ.

Đây là bài học thuộc mục **Concurrent Collections** trong Java concurrency nói chung, không cần bất kỳ framework nào.

## 2. Vì sao `get()` rồi `put()` không đủ?

Giả sử ban đầu `counts["A"] == 10`:

```text
Thread 1                         Thread 2
--------                         --------
get("A") -> 10
                                 get("A") -> 10
tính 11
                                 tính 11
put("A", 11)
                                 put("A", 11)

Kết quả: 11, mặc dù có hai yêu cầu tăng.
```

`ConcurrentHashMap.get` và `put` riêng lẻ đều được thiết kế cho truy cập đồng thời, nhưng **khoảng giữa chúng không được khóa như một giao dịch nghiệp vụ**.

Vấn đề tương tự xuất hiện khi viết:

```java
if (!map.containsKey(key)) {
    map.put(key, candidate);
}
```

Hai thread có thể cùng thấy key vắng mặt rồi lần lượt `put` hai ứng viên khác nhau. Thread ghi sau ghi đè thread trước; cả hai caller có thể tưởng rằng mình là người khởi tạo.

Không nên tự sửa bằng một cờ `boolean initialized` hay `volatile` rời rạc nếu vẫn thiếu một thao tác nguyên tử gộp kiểm tra với thay đổi. Với `ConcurrentHashMap`, hãy dùng API đúng **ngữ nghĩa của nghiệp vụ**.

## 3. Ba API nhìn cạnh nhau

| API | Khi key vắng mặt | Khi key đã có giá trị | Giá trị trả về | Khi muốn xóa |
| --- | --- | --- | --- | --- |
| `putIfAbsent(k, v)` | Gắn `v` mới | Giữ nguyên giá trị cũ | Giá trị cũ nếu có; `null` nếu thêm thành công | API này không xóa |
| `compute(k, f)` | Gọi `f(k, null)` | Gọi `f(k, oldValue)` | Giá trị mới hoặc `null` | `f` trả `null` |
| `merge(k, v, f)` | Gắn `v` mà **không gọi `f`** | Gọi `f(oldValue, v)` | Giá trị sau khi hợp nhất hoặc `null` | `f` trả `null` |

Ba API có những hợp đồng khác nhau. Chọn dựa trên ý nghĩa nghiệp vụ, không chỉ vì chúng đều nghe như “cập nhật Map”.

**Điểm bắt buộc:** `ConcurrentHashMap` **không cho phép key hoặc value là `null`**. Gọi `putIfAbsent(null, ...)`, `putIfAbsent(key, null)` hay `merge(key, null, ...)` đều không hợp lệ. Trong `compute`, `null` được dùng làm **tín hiệu vắng mặt/xóa mapping**, không phải giá trị `null` được lưu trong Map.

Trong bài này, mọi ví dụ dùng JDK 17 trở lên và kiểu `ConcurrentHashMap` trực tiếp để dựa trên hợp đồng mạnh của implementation này. Không suy diễn rằng mọi implementation `ConcurrentMap` khác có cùng cách thực thi hàm callback.

## 4. `putIfAbsent()`: chỉ một caller cài đặt khi key chưa tồn tại

Chữ ký:

```java
V putIfAbsent(K key, V value);
```

Ngữ nghĩa:

- Nếu key chưa có mapping, gắn `value` và trả `null`.
- Nếu key đã có mapping, **không thay thế**, trả giá trị đang có.
- Toàn thao tác “kiểm tra key vắng mặt + ghi giá trị” diễn ra **nguyên tử** cho key đó.
- Do `ConcurrentHashMap` cấm value `null`, giá trị trả về `null` biểu thị **không có giá trị cũ** và lời gọi đã cài mapping do nó cung cấp.

Một use case: đăng ký ID duy nhất, cài config mặc định, khởi tạo một đối tượng có thể dùng lại hoặc chọn người thắng trong cuộc tranh chấp khởi tạo.

### Ví dụ 1: quan sát kết quả trả về

Tạo tệp `PutIfAbsentBasicsDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;

public class PutIfAbsentBasicsDemo {
    public static void main(String[] args) {
        ConcurrentHashMap<String, String> services =
                new ConcurrentHashMap<>();

        String first = services.putIfAbsent("search", "node-A");
        String second = services.putIfAbsent("search", "node-B");

        System.out.println("first previous=" + first);
        System.out.println("second previous=" + second);
        System.out.println("current=" + services.get("search"));
        System.out.println("size=" + services.size());
    }
}
```

Chạy:

```bash
javac PutIfAbsentBasicsDemo.java
java PutIfAbsentBasicsDemo
```

Kết quả:

```text
first previous=null
second previous=node-A
current=node-A
size=1
```

Giải thích:

- Lần đầu key `"search"` chưa có, ghi `"node-A"` và trả `null`.
- Lần hai key đã tồn tại, `"node-B"` **không** được gắn; trả về `"node-A"`.
- Map có đúng một key.

### Khi nào `putIfAbsent` chưa đủ?

Nếu mục đích là **cộng thêm 1 vào giá trị đang có**, `putIfAbsent(key, 1)` chỉ có tác dụng ở lần đầu. Những lần sau giá trị bị giữ nguyên, **không tăng**.

Nếu muốn “khởi tạo nếu vắng mặt, còn đã có thì sử dụng đối tượng cũ”, `putIfAbsent` phù hợp; nếu việc tạo candidate rất tốn kém, nên cân nhắc `computeIfAbsent` để tránh dựng một candidate mới trong mọi lần gọi khi key đã tồn tại.

Chú ý: `putIfAbsent` nhận **value đã được tính từ trước**, nghĩa là hai thread có thể **đều tốn chi phí tạo candidate** trước khi một thread thắng. API chỉ đảm bảo một mapping được cài, không đảm bảo chỉ một constructor/call bên ngoài từng được thực hiện.

## 5. `putIfAbsent` khi hai thread tranh cùng key

Giả sử hai worker cùng muốn cài key `"leader"`. Không thể dự đoán **thread tên gì thắng** vì scheduler quyết định thứ tự. Nhưng có thể kiểm chứng chắc chắn rằng **chỉ một lời gọi** báo thành công.

Tạo tệp `PutIfAbsentRaceDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class PutIfAbsentRaceDemo {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, String> map =
                new ConcurrentHashMap<>();
        AtomicInteger installed = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        Runnable job = () -> {
            try {
                start.await();
                if (map.putIfAbsent("leader", "ready") == null) {
                    installed.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread a = new Thread(job, "candidate-A");
        Thread b = new Thread(job, "candidate-B");

        a.start();
        b.start();
        start.countDown();
        a.join();
        b.join();

        System.out.println("installed=" + installed.get());
        System.out.println("value=" + map.get("leader"));
        System.out.println("size=" + map.size());
    }
}
```

```bash
javac PutIfAbsentRaceDemo.java
java PutIfAbsentRaceDemo
```

Output khi worker không bị interrupt ngoài ý muốn:

```text
installed=1
value=ready
size=1
```

Dù cả hai thread có thể thử cùng lúc, tại key `leader`, chỉ một phép cài mapping từ trạng thái vắng mặt thành `"ready"` thành công. Không cần dự đoán ai thắng vì hai ứng viên cài cùng giá trị để output ổn định.

**Không đồng nhất “một caller cài mapping” với “một tác vụ nghiệp vụ đã chạy duy nhất”.** Nếu hai thread gọi API ngoài, tạo file, gửi email hay ghi database **trước** khi `putIfAbsent`, side effect đã có thể xảy ra hai lần. Hãy thực hiện side effect dựa trên kết quả thắng/thua hoặc một giao thức idempotency phù hợp.

## 6. `compute()`: đọc giá trị hiện tại và quyết định mapping mới

Chữ ký:

```java
V compute(
        K key,
        BiFunction<? super K, ? super V, ? extends V> remappingFunction);
```

Hàm callback nhận:

1. `key` đang được cập nhật.
2. `oldValue` hiện tại; với `ConcurrentHashMap`, `oldValue == null` biểu thị **key vắng mặt**.

Callback trả:

- Giá trị `newValue != null`: cài hoặc thay mapping.
- `null`: không giữ mapping cho key (nếu đang tồn tại thì **xóa**; nếu chưa có thì vẫn vắng mặt).

Với `ConcurrentHashMap.compute`, toàn bộ thao tác của **một key** được thực hiện nguyên tử theo hợp đồng; callback được gọi một lần trong mỗi lời gọi method này. Các cập nhật khác trong Map có thể bị chặn trong khi callback đang chạy, vì vậy callback phải **ngắn và đơn giản**.

### Ví dụ 2: cài, cập nhật và xóa bằng compute

Tạo tệp `ComputeBasicsDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;

public class ComputeBasicsDemo {
    public static void main(String[] args) {
        ConcurrentHashMap<String, Integer> map =
                new ConcurrentHashMap<>();

        Integer first = map.compute("quota",
                (key, oldValue) ->
                        oldValue == null ? 10 : oldValue + 10);

        Integer second = map.compute("quota",
                (key, oldValue) ->
                        oldValue == null ? 10 : oldValue + 10);

        Integer removed = map.compute("quota",
                (key, oldValue) -> null);

        System.out.println("first=" + first);
        System.out.println("second=" + second);
        System.out.println("removed=" + removed);
        System.out.println("contains=" + map.containsKey("quota"));
        System.out.println("size=" + map.size());
    }
}
```

```bash
javac ComputeBasicsDemo.java
java ComputeBasicsDemo
```

Output:

```text
first=10
second=20
removed=null
contains=false
size=0
```

Trình tự:

- Lần đầu `oldValue == null`, callback trả `10`.
- Lần thứ hai `oldValue == 10`, callback trả `20`.
- Lần cuối callback trả `null`, xóa mapping.
- Map không lưu `"quota" -> null`; nó **không còn key**.

**Phân biệt:** `compute(k, (key, old) -> null)` là một thao tác xóa có điều kiện theo callback; nếu không cần tính toán, `remove(k)` thường rõ ràng hơn.

## 7. Tại sao `compute` giải quyết lost update?

Giả sử hai thread cùng thực hiện:

```java
counts.compute("java",
        (key, old) -> old == null ? 1 : old + 1);
```

Mỗi lời gọi là một phép cập nhật nguyên tử **theo key "java"**. Một lời gọi áp dụng từ `10 -> 11`; lời gọi còn lại áp dụng tiếp `11 -> 12`, chứ không thể cả hai cùng ghi `11` dựa trên snapshot lỗi thời theo cách `get`/`put`.

### Ví dụ 3: 20.000 lần cập nhật cạnh tranh

Tạo tệp `ComputeCounterDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class ComputeCounterDemo {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, Integer> counts =
                new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);

        Runnable job = () -> {
            try {
                start.await();
                for (int i = 0; i < 10_000; i++) {
                    counts.compute("java", (key, old) ->
                            old == null ? 1 : old + 1);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread first = new Thread(job);
        Thread second = new Thread(job);
        first.start();
        second.start();
        start.countDown();

        first.join();
        second.join();

        System.out.println("java=" + counts.get("java"));
        System.out.println("size=" + counts.size());
    }
}
```

```bash
javac ComputeCounterDemo.java
java ComputeCounterDemo
```

Output khi không có interrupt ngoài ý muốn:

```text
java=20000
size=1
```

Hai worker mỗi người thực hiện 10.000 lần tăng. Không sử dụng lock bên ngoài hay biến đếm global. `compute` bảo vệ phép đọc–sửa–ghi đối với key đích.

**Giới hạn:** phép cộng kiểu `int` có thể overflow; nếu business yêu cầu số không được vượt giới hạn, hãy kiểm tra ngay trong callback hoặc dùng kiểu số phù hợp. Đồng thời, Map không tự bảo vệ object mutable được lưu làm value nếu những code path khác sửa các field bên trong object đó.

## 8. `merge()`: khởi tạo hoặc gộp giá trị chỉ bằng một lời gọi

Chữ ký:

```java
V merge(
        K key,
        V value,
        BiFunction<? super V, ? super V, ? extends V> remappingFunction);
```

Khác `compute`, callback của `merge` **không nhận key**. Hai tham số callback lần lượt là:

1. **Giá trị cũ** đang lưu trong Map.
2. **Giá trị đầu vào** `value` được truyền trong lời gọi `merge`.

Khi key **vắng mặt**, `merge` đặt ngay giá trị đầu vào `value` mà **không gọi callback**. Khi key đã có giá trị, hàm remapping sẽ tính giá trị mới. Nếu callback trả `null`, mapping bị xóa.

Use case điển hình: counter, word frequency, tổng tiền theo mã sản phẩm, cộng dồn thống kê, kết hợp hai tập kết quả bất biến.

### Ví dụ 4: merge để tăng và xóa

Tạo tệp `MergeBasicsDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;

public class MergeBasicsDemo {
    public static void main(String[] args) {
        ConcurrentHashMap<String, Integer> map =
                new ConcurrentHashMap<>();

        int first = map.merge("hits", 1, Integer::sum);
        int second = map.merge("hits", 4, Integer::sum);
        int third = map.merge("hits", 5, Integer::sum);

        Integer removed = map.merge(
                "hits", 2, (oldValue, incoming) -> null);

        System.out.println("first=" + first);
        System.out.println("second=" + second);
        System.out.println("third=" + third);
        System.out.println("removed=" + removed);
        System.out.println("contains=" + map.containsKey("hits"));

        Integer again = map.merge("hits", 7, Integer::sum);
        System.out.println("again=" + again);
    }
}
```

```bash
javac MergeBasicsDemo.java
java MergeBasicsDemo
```

Output:

```text
first=1
second=5
third=10
removed=null
contains=false
again=7
```

Tại sao callback không chạy ở lần đầu? Vì key `hits` chưa có, `merge("hits", 1, ...)` cài `1` trực tiếp. Sau đó `Integer::sum` gộp giá trị cũ với giá trị đầu vào.

Ở lần xóa, callback nhận `(10, 2)` nhưng cố tình trả `null`, nên key bị xóa. Sau đó `merge("hits", 7, ...)` thấy key vắng mặt, lại gắn `7`.

**Không coi merge trả `null` là Map chứa một giá trị null.** `ConcurrentHashMap` không có value null; trả `null` ở đây là tín hiệu xóa mapping.

## 9. Bảng tình huống cùng một key

Giả sử bắt đầu từ Map rỗng, sau đó dùng một key `k`:

| Thao tác | Nếu trước đó key vắng mặt | Nếu trước đó `k -> 4` |
| --- | --- | --- |
| `putIfAbsent(k, 7)` | Ghi `7`, trả `null` | Giữ `4`, trả `4` |
| `compute(k, (key,v) -> v==null ? 1 : v+1)` | Ghi `1`, trả `1` | Ghi `5`, trả `5` |
| `merge(k, 3, Integer::sum)` | Ghi `3`, trả `3` | Ghi `7`, trả `7` |
| `compute(k, (key,v) -> null)` | Vẫn vắng, trả `null` | Xóa mapping, trả `null` |
| `merge(k, 3, (a,b) -> null)` | Ghi `3`, **không gọi callback** | Xóa mapping, trả `null` |

Dòng cuối là một bẫy dễ gặp: nếu bạn định dùng `merge` làm “xóa key”, callback trả null **chỉ chạy khi key đã có giá trị**. Trường hợp key vắng mặt, `merge` sẽ cài value đầu vào. Khi chỉ muốn xóa key, dùng `remove` hoặc `compute` theo điều kiện thích hợp.

## 10. Dùng `merge` để đếm tần suất từ nhiều thread

Giả sử nhiều task đang đọc các đoạn văn bản độc lập và cần đếm số lần xuất hiện từng từ. Với `ConcurrentHashMap<String,Integer>`:

```java
frequency.merge(word, 1, Integer::sum);
```

đã gom việc “nếu chưa có thì đặt 1, nếu đã có thì tăng 1” thành một thao tác nguyên tử theo từ khóa.

### Ví dụ 5: word frequency song song, output ổn định

Tạo tệp `MergeWordFrequencyDemo.java`:

```java
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class MergeWordFrequencyDemo {
    public static void main(String[] args) throws InterruptedException {
        String[] words = {
                "java", "map", "java", "atomic",
                "map", "java", "thread", "atomic"
        };
        ConcurrentHashMap<String, Integer> frequency =
                new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);

        Runnable even = () -> {
            try {
                start.await();
                for (int i = 0; i < words.length; i += 2) {
                    frequency.merge(words[i], 1, Integer::sum);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Runnable odd = () -> {
            try {
                start.await();
                for (int i = 1; i < words.length; i += 2) {
                    frequency.merge(words[i], 1, Integer::sum);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread first = new Thread(even);
        Thread second = new Thread(odd);
        first.start();
        second.start();
        start.countDown();
        first.join();
        second.join();

        System.out.println("frequencies="
                + new TreeMap<>(frequency));
    }
}
```

```bash
javac MergeWordFrequencyDemo.java
java MergeWordFrequencyDemo
```

Output khi không có interrupt ngoài ý muốn:

```text
frequencies={atomic=2, java=3, map=2, thread=1}
```

`ConcurrentHashMap` **không cam kết thứ tự duyệt**. `new TreeMap<>(frequency)` chỉ dùng sau khi **cả hai worker đã `join()`**, nên snapshot đã hoàn tất và TreeMap sắp xếp các key `String` theo thứ tự tự nhiên trước khi in.

Nếu liên tục tạo `new TreeMap<>(frequency)` trong khi các producer vẫn đang cập nhật, không thể mặc định đó là một snapshot nguyên tử của mọi key tại cùng một thời điểm.

### Tại sao không dùng một `HashMap` với `synchronized`?

Hoàn toàn có thể dùng khóa chung quanh mọi thao tác, ví dụ `synchronized(lock)`, để đảm bảo correctness. `ConcurrentHashMap` là lựa chọn tiện dụng khi muốn nhiều thread thao tác trên các key khác nhau mà không phải khóa toàn bộ Map bằng monitor của ứng dụng. Hiệu năng phụ thuộc workload, số key nóng và contention; **không khẳng định ConcurrentHashMap luôn nhanh hơn mọi cách khóa**.

## 11. `compute()` khác `merge()` khi nào nên chọn?

Chọn `compute` khi phép cập nhật:

- Cần biết **key** trong callback.
- Cần đọc giá trị cũ và áp dụng **điều kiện phức tạp**.
- Cần cho phép kết quả `null` để xóa key.
- Muốn xây lại một value bất biến từ value cũ.
- Cần xử lý cả trường hợp key vắng mặt và hiện hữu theo **hai nhánh logic khác nhau**.

Ví dụ giảm tồn kho, xóa mapping khi số lượng về 0:

```java
stock.compute("pencil", (key, current) -> {
    if (current == null || current <= 0) {
        throw new IllegalStateException("out of stock");
    }
    return current == 1 ? null : current - 1;
});
```

Đây là đoạn minh họa. Nó coi key vắng mặt là hết hàng và dùng việc xóa mapping khi hết số lượng. Đây chỉ là một cách biểu diễn; có domain cần giữ mapping số lượng 0 để báo cáo, khi đó nên trả `0` thay vì `null`.

Chọn `merge` khi logic tự nhiên là **lấy giá trị đầu vào rồi kết hợp với giá trị đang có**:

```java
totals.merge(productId, amount, Long::sum);
```

Đây là đoạn minh họa với `totals` là `ConcurrentHashMap<String,Long>`, `productId` là `String`, `amount` là `Long` không null. Nếu chưa có key thì ghi `amount`. Nếu đã có thì cộng vào số cũ.

Chọn `putIfAbsent` khi không muốn thay đổi giá trị hiện tại:

```java
RegistryEntry previous = entries.putIfAbsent(id, candidate);
```

Đây là đoạn minh họa với `entries` và `candidate` được tạo hợp lệ. Nếu cần kiểm tra object nào thắng, dùng `previous == null`; nếu không, có thể dùng lại previous.

## 12. Callback của `compute`/`merge` cần ngắn và không gây side effect ngoài

Với `ConcurrentHashMap`, các lời gọi `compute` và `merge` thực hiện toàn phần một cập nhật nguyên tử theo key; tài liệu API chỉ rõ callback được gọi một lần cho mỗi **lời gọi method** đó. Tuy nhiên, khi callback chạy, **các thao tác cập nhật khác có thể bị chặn**; một callback lâu làm tăng contention và rủi ro liveness.

Không nên:

```java
map.compute(key, (k, old) -> {
    callRemotePaymentService();
    Thread.sleep(5000);
    return expensiveRebuild(old);
});
```

Đây là **pseudocode phản ví dụ**, không phải mã chạy độc lập: `Thread.sleep` yêu cầu xử lý `InterruptedException`; các helper do ứng dụng định nghĩa. Trọng tâm là **không đặt I/O chặn, đợi dài hoặc side effect bên ngoài vào callback**.

Những vấn đề:

- Thread khác cập nhật những mapping liên quan có thể phải chờ.
- Nếu callback ném unchecked exception, lần tính mapping có thể không commit; **side effect đã thực hiện ngoài Map không được rollback**.
- Nếu callback gọi API bên ngoài và bên ngoài callback đó sửa Map hoặc chờ một task cũng cần Map, dễ tạo vòng chờ/deadlock.
- Nếu chính code ứng dụng retry lời gọi `compute` sau lỗi, side effect ngoài Map có thể chạy lại; **“callback một lần mỗi invocation” không có nghĩa side effect một lần suốt vòng đời nghiệp vụ**.

Cách tiếp cận tốt hơn là tạo dữ liệu đầu vào ngoài callback (nếu an toàn), sau đó callback chỉ tính trạng thái mới, commit nguyên tử trong Map, và điều phối side effect riêng với idempotency/transaction nếu cần.

**Không tuyên bố hai hệ thống trở thành transaction:** cập nhật `ConcurrentHashMap` trong RAM và gửi email/ghi database không thể tự commit nguyên tử cùng nhau chỉ vì một lời gọi `compute` thành công.

## 13. Không cập nhật chính Map đó từ callback

Javadoc của `ConcurrentHashMap` cảnh báo rằng **remapping function không nên cố cập nhật các mapping khác của cùng Map** trong khi đang chạy. Một số trường hợp cập nhật đệ quy có thể bị phát hiện và ném `IllegalStateException`; các trường hợp khác có thể gây blocking hoặc vòng chờ không mong muốn.

Ví dụ cần tránh:

```java
map.compute("a", (key, old) -> {
    map.put("b", "new value"); // Không nên cập nhật cùng map tại callback.
    return "done";
});
```

Đây là đoạn phản ví dụ. **Đừng dựa vào việc code có vẻ chạy được trên một bộ key/bucket cụ thể**: chi tiết bucket, hash collision, version JDK và cơ chế cập nhật nội bộ không phải contract được phép khai thác.

Nếu cần cập nhật **hai key như một giao dịch**, đừng ép bằng nested `compute` trên cùng Map. Hãy dùng một khóa nghiệp vụ bao trùm, một immutable aggregate lưu dưới **một key**, hoặc một cấu trúc đồng bộ/transaction phù hợp.

Đặc biệt cần tránh đợi `Future.get()` trong callback nếu task mà bạn đang chờ phải cập nhật một key trên Map ấy. Đây là một cách tạo **thread-starvation/blocking dependency** khó phân tích.

## 14. Trả null, ném exception và callback không cập nhật

Với `compute`:

- **Trả value không null:** ánh xạ mới được gắn.
- **Trả null:** mapping bị xóa hoặc giữ vắng mặt.
- **Callback ném exception:** lời gọi ném exception ra caller; mapping tại key đó **không được thay đổi bởi lần tính lỗi đó** theo contract.
- **Map chứa value mutable:** thay mapping không làm cho mọi sửa đổi tiếp theo bên trong object an toàn.

Với `merge`:

- Nếu key vắng, thêm input `value` mà không gọi callback.
- Nếu key có, gọi callback với `oldValue` và `incomingValue`.
- Nếu callback trả null, xóa mapping.
- Nếu callback ném exception, mapping tại key đó không được cập nhật bởi lần hợp nhất lỗi đó.

### Ví dụ 6: exception không tự commit giá trị mới

Tạo tệp `ComputeFailureDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;

public class ComputeFailureDemo {
    public static void main(String[] args) {
        ConcurrentHashMap<String, Integer> map =
                new ConcurrentHashMap<>();
        map.put("balance", 100);

        try {
            map.compute("balance", (key, old) -> {
                if (old < 200) {
                    throw new IllegalStateException("insufficient");
                }
                return old - 200;
            });
        } catch (IllegalStateException e) {
            System.out.println("error=" + e.getMessage());
        }

        System.out.println("balance=" + map.get("balance"));
    }
}
```

```bash
javac ComputeFailureDemo.java
java ComputeFailureDemo
```

Output:

```text
error=insufficient
balance=100
```

Callback throw, nên lần `compute` không ghi giá trị mới cho `balance`. Nếu callback đã gọi một dịch vụ ngoài JVM **trước** khi throw, dịch vụ đó có thể đã thay đổi; Map không tự rollback dịch vụ ngoài. Mã ví dụ chỉ thực hiện kiểm tra và tính toán nội bộ, nên không có side effect như vậy.

## 15. Atomicity chỉ theo từng key, không theo toàn Map

Đây là một giới hạn quan trọng của cả ba API. Giả sử Map lưu hai số dư:

```text
left = 100
right = 0
Bất biến: left + right = 100
```

Nếu chuyển 10 đơn vị bằng hai thao tác:

```java
accounts.compute("left", (k, v) -> v - 10);
accounts.compute("right", (k, v) -> v + 10);
```

thì **mỗi key được cập nhật nguyên tử riêng**, nhưng một reader có thể đọc giữa hai lời gọi và thấy `left + right == 90`.

Không có cách gọi `compute` **trên hai key độc lập** để tự nhiên biến chúng thành một transaction duy nhất. Nếu invariant liên quan nhiều key, phải bảo vệ toàn bộ thao tác bằng một cơ chế chung hoặc thiết kế một immutable aggregate dưới **một key**.

### Ví dụ 7: nhìn thấy trạng thái trung gian một cách xác định

Tạo tệp `MultiKeyInvariantDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class MultiKeyInvariantDemo {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, Integer> accounts =
                new ConcurrentHashMap<>();
        accounts.put("left", 100);
        accounts.put("right", 0);

        CountDownLatch leftUpdated = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            accounts.compute("left", (k, v) -> v - 10);
            leftUpdated.countDown();
            try {
                proceed.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            accounts.compute("right", (k, v) -> v + 10);
        });

        writer.start();
        leftUpdated.await();

        int during = accounts.get("left")
                + accounts.get("right");
        System.out.println("during=" + during);

        proceed.countDown();
        writer.join();

        int after = accounts.get("left")
                + accounts.get("right");
        System.out.println("after=" + after);
    }
}
```

```bash
javac MultiKeyInvariantDemo.java
java MultiKeyInvariantDemo
```

Output nếu không có interrupt ngoài ý muốn:

```text
during=90
after=100
```

`leftUpdated.await()` đảm bảo thao tác giảm `left` đã commit. `proceed` giữ writer chưa được cập nhật `right`. Main nhìn thấy tổng trung gian `90`. Sau khi main mở latch, writer tăng `right` và tổng trở lại `100`.

**Không có data race trong container theo nghĩa thao tác Map bị hỏng**; vấn đề là **logic nghiệp vụ gồm hai bước không có atomicity xuyên key**.

Để sửa, có thể biểu diễn hai số dư trong một record bất biến `Balances(left,right)` và dùng `compute("accounts", ...)` để thay **cả record** trong một key; hoặc dùng khóa chung cho cả đọc và ghi aggregate. Với database, cần transaction của hệ thống lưu trữ, không chỉ khóa trong JVM.

## 16. Immutable value giúp reader có snapshot nhất quán

Một pattern tốt: lưu **value bất biến** thay vì object mutable nhiều field. Mỗi `compute` tạo một object mới từ state cũ và thay nguyên một reference dưới cùng key.

Ví dụ `Profile`:

```java
record Profile(int version, String city) {}
```

Record giữ component final; ở ví dụ chỉ chứa `int` và `String` nên hợp lý để xem là bất biến. Nếu record chứa `List` mutable, nó **không tự động deep immutable**.

### Ví dụ 8: tăng version atomically trong một record

Tạo tệp `ComputeImmutableStateDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class ComputeImmutableStateDemo {
    record Profile(int version, String city) {}

    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, Profile> profiles =
                new ConcurrentHashMap<>();
        profiles.put("u1", new Profile(0, "Ha Noi"));

        CountDownLatch start = new CountDownLatch(1);

        Runnable job = () -> {
            try {
                start.await();
                for (int i = 0; i < 500; i++) {
                    profiles.compute("u1", (key, old) ->
                            new Profile(
                                    old.version() + 1,
                                    old.city()));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        Thread a = new Thread(job);
        Thread b = new Thread(job);
        a.start();
        b.start();
        start.countDown();
        a.join();
        b.join();

        Profile snapshot = profiles.get("u1");
        System.out.println("version=" + snapshot.version());
        System.out.println("city=" + snapshot.city());
    }
}
```

```bash
javac ComputeImmutableStateDemo.java
java ComputeImmutableStateDemo
```

Output khi không có interrupt ngoài ý muốn:

```text
version=1000
city=Ha Noi
```

Mỗi worker cập nhật version 500 lần, `compute` trên cùng key `u1` bảo đảm không lost update. Mỗi lần tạo `Profile` mới, không sửa bản cũ. Reader lấy một `Profile snapshot = profiles.get("u1")` rồi đọc cả `version` và `city` từ **cùng object**.

Nếu reader gọi `profiles.get("u1").version()` rồi `profiles.get("u1").city()` trong hai lượt riêng biệt, kết quả có thể đến từ hai phiên bản khác nhau khi writer còn hoạt động. **Lấy một snapshot reference cục bộ khi cần nhiều trường nhất quán cùng phiên bản.**

## 17. Mutable values: Map an toàn không đồng nghĩa value an toàn

Hãy xem:

```java
ConcurrentHashMap<String, ArrayList<String>> lists =
        new ConcurrentHashMap<>();
lists.put("events", new ArrayList<>());

lists.get("events").add("A");
```

Đây là đoạn minh họa, cần import `java.util.ArrayList` và `ConcurrentHashMap`. Nếu nhiều thread cùng lấy `ArrayList` đó rồi gọi `add`, container Map an toàn **không bảo vệ các thao tác sửa bên trong ArrayList**.

Các lựa chọn:

- Dùng **immutable copy-on-write value** rồi `compute(key, ...)` để thay reference một cách nguyên tử.
- Dùng một concurrent collection phù hợp làm value nếu bài toán thực sự cần sửa trực tiếp đồng thời.
- Dùng khóa gắn với object/aggregate theo giao thức chung.
- Phân quyền một owner duy nhất sửa dữ liệu, chỉ xuất snapshot bất biến cho reader.

Ví dụ bất biến theo key:

```java
events.compute("topic", (key, old) -> {
    List<String> next = new ArrayList<>(
            old == null ? List.of() : old);
    next.add("new event");
    return List.copyOf(next);
});
```

Đây là đoạn minh họa, cần khai báo `events` là `ConcurrentHashMap<String,List<String>>` và import `List`, `ArrayList`. `List.copyOf` tạo list không thể sửa cấu trúc qua API thông thường, nhưng **không deep-copy phần tử mutable**. Trong ví dụ phần tử `String` bất biến nên cách tiếp cận hợp lý.

**Lưu ý:** ở mỗi lần update, copy toàn bộ danh sách cũ tốn chi phí `O(n)` theo kích thước list. Nếu lượng sự kiện lớn hoặc cập nhật rất thường xuyên, mô hình bất biến copy-on-write cho một key có thể không hiệu quả; phải chọn cấu trúc/ownership khác.

## 18. `computeIfAbsent`: gần với putIfAbsent nhưng có callback

Dù trọng tâm bài là `putIfAbsent`, `compute` và `merge`, `computeIfAbsent` là API thường xuất hiện ngay bên cạnh:

```java
map.computeIfAbsent(key, k -> createValue(k));
```

Nó tính mapping cho key **chỉ khi key vắng mặt**, rồi cài value nếu mapping function trả **khác null**. Nếu trả null, không cài mapping. Với `ConcurrentHashMap`, toàn invocation atomic theo key và mapping function được gọi một lần trong invocation khi cần tính; callback phải ngắn và không sửa cùng Map đệ quy.

So sánh:

| Ý định | API gợi ý |
| --- | --- |
| Candidate đã có sẵn, chỉ cài nếu chưa có | `putIfAbsent(k, candidate)` |
| Tạo candidate **khi chưa có** | `computeIfAbsent(k, factory)` |
| Luôn tính lại từ trạng thái hiện tại, có thể xóa | `compute(k, remap)` |
| Có một giá trị đầu vào để gộp với giá trị cũ | `merge(k, incoming, combine)` |

### Ví dụ `LongAdder` cho frequency map

`LongAdder` thường hữu ích khi **nhiều thread liên tục tăng một bộ đếm nóng**, vì nội bộ có thể phân tán tranh chấp hơn một atomic counter đơn lẻ.

```java
ConcurrentHashMap<String, LongAdder> counts =
        new ConcurrentHashMap<>();

counts.computeIfAbsent(
        "java", key -> new LongAdder()).increment();

long total = counts.get("java").sum();
```

Đây là đoạn minh họa, cần import `java.util.concurrent.ConcurrentHashMap` và `java.util.concurrent.atomic.LongAdder`. `computeIfAbsent` cài một `LongAdder` nếu vắng mặt; sau đó caller tăng chính bộ đếm đó.

**Phân biệt tính nhất quán:**

- `LongAdder.increment()` hỗ trợ cập nhật đồng thời.
- `LongAdder.sum()` trong lúc các thread khác vẫn tăng **không phải một snapshot nguyên tử toàn bộ cập nhật**, nên phù hợp thống kê/monitoring khi chấp nhận một lần đọc tức thời không hoàn toàn đồng bộ.
- Nếu cần quyết định nghiệp vụ dựa trên **giá trị chính xác tại một điểm tuyến tính hóa**, nên xem xét primitive khác và giao thức phù hợp.

`computeIfAbsent` chỉ giải quyết **khởi tạo adder**, không biến việc đọc `sum()` và đặt điều kiện nhiều bước thành một transaction.

## 19. Atomicity và thứ tự bộ nhớ theo Java Memory Model

`ConcurrentHashMap` cung cấp bảo đảm đồng thời để các thread có thể truy cập cùng Map an toàn và quan sát mapping được công bố. Một thao tác update thành công có các quan hệ happens-before phù hợp đối với những lần truy cập nhận value từ mapping đó theo hợp đồng concurrent collection.

Một hệ quả thực hành: worker có thể tạo một **object đã được khởi tạo hoàn chỉnh**, đưa nó vào `ConcurrentHashMap`, và worker khác lấy object ấy bằng `get(key)` sau khi mapping được công bố sẽ có bảo đảm bộ nhớ để nhìn thấy những ghi xảy ra trước khi công bố.

Nhưng đừng suy diễn quá mức:

- **An toàn công bố không biến mutable value thành immutable.**
- Một reader lấy tham chiếu rồi writer bên ngoài sửa fields chưa được đồng bộ vẫn có thể tạo data race.
- Một chuỗi `get(A)`, `get(B)` không tự tạo snapshot nguyên tử của hai key.
- Một lượt duyệt `entrySet()` khi Map đang thay đổi không tạo giao dịch ảnh chụp toàn bộ Map.
- Chỉ khi đúng cùng Map và giao thức cập nhật hợp lệ, API atomic theo key mới có thể được dùng để chứng minh các cập nhật nghiệp vụ tương ứng.

Khi cần tính nhất quán chặt chẽ, hãy chỉ rõ **đơn vị nguyên tử**: một mapping, một value aggregate hay một nhóm nhiều mapping đòi hỏi khóa/protocol.

## 20. Duyệt ConcurrentHashMap: weakly consistent, không phải snapshot bất biến

`ConcurrentHashMap` hỗ trợ iterator và các thao tác duyệt trong lúc Map còn bị cập nhật. Iterator của nó thuộc loại **weakly consistent**: không ném `ConcurrentModificationException` chỉ vì các thread khác đang thay đổi Map, và có thể phản ánh một số thay đổi đồng thời.

Điều **không** được đảm bảo: một lượt duyệt cho thấy chính xác tất cả key/value tại một thời điểm duy nhất, theo một thứ tự ổn định.

Ví dụ:

```java
for (var entry : map.entrySet()) {
    System.out.println(entry.getKey() + "=" + entry.getValue());
}
```

Đây là đoạn minh họa, `map` là `ConcurrentHashMap`. Nếu Map đang thay đổi, output có thể khác nhau giữa các lần chạy; không thể dùng kết quả của vòng duyệt làm bằng chứng bất biến xuyên toàn Map.

Các API như `size()`, `mappingCount()`, `containsValue()` cũng cần được hiểu theo điều kiện cập nhật đồng thời. Khi Map đang chịu ghi mạnh, các phép thống kê/tổng hợp toàn Map thường phù hợp **monitoring** hơn quyết định nghiệp vụ yêu cầu snapshot nhất quán.

Nếu cần báo cáo chính xác sau một batch, hãy đợi mọi producer hoàn thành (ví dụ `Thread.join()`, `Future.get()`) rồi tạo bản sao để trình bày. Nếu writer vẫn chạy, cần khóa, versioning/snapshot protocol hoặc một cách cô lập dữ liệu phù hợp để đảm bảo consistency.

## 21. Key phải có equals/hashCode ổn định

`ConcurrentHashMap` là hash-based map. Nó cần `hashCode()` và `equals()` của key phù hợp trong suốt thời gian key được dùng. Nếu object key bị sửa các trường tham gia hash/equality sau khi đưa vào Map, thao tác `get()`/`remove()` có thể không tìm ra mapping như mong đợi.

Ví dụ sai:

```java
final class MutableKey {
    int id;

    MutableKey(int id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MutableKey other
                && other.id == id;
    }
}
```

Đây là lớp minh họa. Nếu dùng `new MutableKey(1)` làm key, rồi đổi `id` thành `2` sau `put`, hash/equality của key thay đổi. `ConcurrentHashMap` thread-safe **không thể tự khôi phục một key mutable đã thay đổi identity logic**.

Ưu tiên:

```java
record Key(int id) {}
```

Với `record Key(int id)`, component primitive `int` là final, thuận tiện làm key ổn định. Với record chứa mảng hoặc object mutable, vẫn phải xét deep immutability và equals/hashCode của component.

Ngoài ra, callback `compute` hoặc `merge` không nên dựa vào một key có hashCode thay đổi khi đang xử lý.

## 22. Khả năng scale và key nóng

`ConcurrentHashMap` cho phép nhiều thao tác trên các key khác nhau có thể tiến triển đồng thời. Nhưng **tất cả thread cập nhật cùng một key** vẫn tranh chấp trên cùng đơn vị nguyên tử đó.

Ví dụ 100 thread đều gọi:

```java
counts.merge("global", 1, Integer::sum);
```

thì key `global` là điểm nóng. Không có phép màu phân tán phép tính trên một key ra nhiều core mà vẫn giữ một biến `Integer` với một thứ tự cập nhật nguyên tử.

Các hướng tối ưu, tùy bài toán:

1. **Sharding theo key:** mỗi nhóm dữ liệu có key riêng nếu nghiệp vụ cho phép; giảm contention.
2. **LongAdder:** phù hợp số liệu tăng nhiều, đọc tổng không cần linearizable snapshot dưới cập nhật đồng thời.
3. **Batching:** mỗi worker gom số local, rồi merge một lần mỗi batch thay vì một lần mỗi phần tử.
4. **Immutable aggregate:** giúp đọc snapshot của một key nhất quán nếu value có nhiều field.
5. **Thay thuật toán:** nếu workload quá nhỏ hoặc chỉ một thread, Map concurrent có overhead không cần thiết.

Đừng benchmark bằng một lần `System.nanoTime()` trên mảng vài phần tử rồi kết luận thư viện A “nhanh hơn luôn”. Với JVM cần warm-up, cùng dữ liệu, nhiều lần chạy và công cụ như JMH nếu đo vi mô.

## 23. Xử lý contention, khóa nội bộ và hàm callback dài

Một số thao tác đọc của `ConcurrentHashMap` được thiết kế rất hiệu quả khi nhiều thread; các cập nhật phức tạp như `compute`/`merge` có thể phải phối hợp các writer ở vùng liên quan. Không nên viết logic nghiệp vụ giả định “`ConcurrentHashMap` hoàn toàn lock-free trong mọi phương thức”.

Mỗi `compute`/`merge` có thể cần chặn các update cạnh tranh trong lúc callback đang chạy. Vì vậy:

- Callback chỉ làm phép tính nhanh, không gọi remote API.
- Không giữ một khóa nghiệp vụ khác rồi chờ callback ở key khác theo vòng phụ thuộc.
- Không tạo dependency “callback A đợi task B, task B cần callback A”.
- Không chỉnh các mapping khác của chính Map trong callback.
- Không thiết kế callback có side effect rồi mong một exception của callback sẽ rollback mọi hệ thống khác.

Nếu cần giữ một invariant trên nhiều key, chọn chiến lược rõ ràng: khóa ngoài cho toàn aggregate, immutable value dưới một key, actor/single-owner, hoặc transaction ở tầng lưu trữ. Sử dụng các callback theo key chỉ vì “Map concurrent” không đủ để chứng minh workflow an toàn.

## 24. Lỗi thường gặp và cách sửa

### 24.1. `get()` rồi `put()` để tăng counter

**Sai:** hai thread đọc cùng old value rồi cùng ghi kết quả mới. **Sửa:** dùng `merge(k, 1, Integer::sum)` hoặc `compute(k, (key, old) -> old == null ? 1 : old + 1)`.

### 24.2. `containsKey()` rồi `put()` để khởi tạo

**Sai:** check-then-act không nguyên tử. **Sửa:** dùng `putIfAbsent(k, candidate)` hoặc `computeIfAbsent(k, factory)`.

### 24.3. Dùng `putIfAbsent` để cộng dồn

**Sai:** lần đầu có thể gắn `1`, những lần sau không tăng nữa. **Sửa:** `merge` hoặc `compute` tùy logic.

### 24.4. Quên kiểm tra `null` trong callback `compute`

Khi key vắng, `oldValue == null`. Nếu viết `oldValue + 1` sẽ `NullPointerException` do unboxing. **Sửa:** xử lý nhánh vắng mặt rõ ràng.

### 24.5. Truyền null để lưu vào ConcurrentHashMap

`ConcurrentHashMap` không cho phép key/value null. **Sửa:** chọn sentinel/value type không null hoặc `remove` nếu muốn biểu thị không còn mapping.

### 24.6. Nhầm `merge` với `compute` khi key vắng

`merge` không gọi callback khi key vắng, mà gắn input value trực tiếp. **Sửa:** nếu cần xử lý cả absent/present bằng callback, dùng `compute`.

### 24.7. Trả null từ callback nhưng mong Map lưu null

Trả null làm xóa/không tạo mapping. **Sửa:** dùng một object thể hiện trạng thái “empty” nếu nghiệp vụ cần giữ key.

### 24.8. Gọi I/O hoặc thao tác chờ dài trong callback

Callback dài có thể block cập nhật cạnh tranh, tạo lock contention. **Sửa:** chuyển I/O ra ranh giới giao thức riêng và giữ callback ngắn.

### 24.9. Cập nhật Map từ chính callback

Nested update có thể bị cấm hoặc gây liveness bug. **Sửa:** không sửa các mapping khác trong remapping function; xử lý đa key theo một thiết kế khác.

### 24.10. Nghĩ update hai key là một transaction

Hai `compute` có hai bước nguyên tử khác nhau. **Sửa:** gộp immutable aggregate dưới một key hoặc đồng bộ toàn thao tác.

### 24.11. Dùng mutable ArrayList làm value mà không đồng bộ

Map an toàn không làm `ArrayList.add` an toàn. **Sửa:** concurrent collection phù hợp hoặc copy-on-write immutable value qua `compute`.

### 24.12. Coi iterator là một snapshot nhất quán

Duyệt Map đang bị ghi là weakly consistent. **Sửa:** cần quiescence/snapshot protocol nếu muốn số liệu chính xác.

### 24.13. Dùng key mutable

Hash/equality thay đổi làm lookup sai. **Sửa:** dùng key bất biến với `equals`/`hashCode` ổn định.

### 24.14. Bỏ qua overflow

`Integer::sum` có thể overflow nếu đếm vượt phạm vi int. **Sửa:** dùng `long` hoặc kiểm tra `Math.addExact` theo yêu cầu domain, và có chiến lược khi vượt ngưỡng.

### 24.15. Nghĩ `LongAdder.sum()` là snapshot nguyên tử trong lúc ghi

`LongAdder` tối ưu throughput hơn trong một số workload nhưng tổng đọc đồng thời không phải snapshot tuyến tính hóa. **Sửa:** chọn atomic counter/giao thức chốt dữ liệu nếu quyết định nghiệp vụ cần exact point-in-time value.

### 24.16. Bỏ qua exception của callback

Nếu callback throw, mapping không commit lần cập nhật đó. Caller phải phân biệt lỗi input, lỗi số học, lỗi runtime và xử lý có kiểm soát; không trả “success” như thể thay đổi đã xảy ra.

## 25. Bài tập thực hành

**Bài tập 1: đếm theo key.** Dùng `ConcurrentHashMap<String,Integer>` để đếm tần suất các từ từ bốn producer. Triển khai hai cách `compute` và `merge`, đợi các worker bằng `join()`/`Future.get()` rồi so sánh kết quả.

**Bài tập 2: khởi tạo winner duy nhất.** Hai thread cùng `putIfAbsent("leader", candidate)`, ghi lại caller nào nhận `null`. Không hardcode ai thắng; assert chỉ có đúng một lần cài mapping.

**Bài tập 3: decrement đến zero.** Dùng `compute` cho một key tồn kho. Khi current == 1, trả `null` để xóa; khi current > 1, trừ 1; khi key vắng, báo out-of-stock. Kiểm tra số âm, retry policy và exception.

**Bài tập 4: giá trị bất biến.** Thay `String` đơn lẻ bằng record `Config(version, url)` và cập nhật version/url trong **một `compute`**. Reader gọi `get()` một lần rồi dùng snapshot.

**Bài tập 5: invariant nhiều key.** Mô phỏng chuyển 10 đơn vị từ key A sang B bằng hai `compute`. Tạo latch để reader đọc giữa hai bước, chứng minh tổng có thể lệch; sau đó sửa bằng một immutable aggregate hoặc lock chung.

**Bài tập 6: callback nguy hiểm.** Thử đặt một thao tác chờ dài trong callback và quan sát giảm throughput (trên môi trường test). Không dùng cách đó trong production. Viết lại để callback chỉ tính state.

**Bài tập 7: kiểm thử mảng kết quả.** Dùng `TreeMap` sau khi tất cả producer đã dừng để có output key sắp xếp ổn định. Thử duyệt trước khi chờ producer và giải thích vì sao kết quả có thể khác nhau.

**Bài tập 8: throughput.** So sánh `merge(k,1,Integer::sum)`, `computeIfAbsent(k,()->LongAdder).increment()` (sửa đúng lambda chỉ nhận key) và batching local. Benchmark với cùng lượng key và contention; không chỉ đo một lần chạy nhỏ.

## 26. Câu hỏi tự kiểm tra

**1. Vì sao `ConcurrentHashMap` không làm `get` rồi `put` trở thành một lần tăng nguyên tử?**

Vì hai lời gọi có khoảng trống để thread khác cập nhật. Từng API riêng thread-safe không ghép hai lời gọi thành transaction.

**2. `putIfAbsent(k, v)` trả `null` nghĩa là gì?**

Với `ConcurrentHashMap` không cho phép null value, nghĩa là không có mapping trước đó và lời gọi đã cài `v`.

**3. Hai thread cùng `putIfAbsent` một key vắng có thể cùng trả `null` không?**

Không nếu key không bị xóa xen vào và không có cập nhật làm nó vắng trở lại. Với cuộc đua cài một lần như ví dụ, chỉ một caller thắng.

**4. `compute(k, (key, old) -> null)` làm gì?**

Xóa mapping nếu có, hoặc giữ key vắng mặt; Map không lưu giá trị null.

**5. `merge("hits", 3, Integer::sum)` khi key vắng mặt có gọi `Integer::sum` không?**

Không. Nó gắn `3` trực tiếp.

**6. `merge(k, 3, (old, incoming) -> null)` khi key vắng mặt có xóa không?**

Không. Nó thêm `3`. Callback chỉ chạy nếu key đã có value.

**7. Có nên gọi API thanh toán từ callback `compute` không?**

Không nên. Callback có thể chặn các update liên quan, và side effect ngoài Map không rollback nếu lần cập nhật lỗi hoặc nghiệp vụ retry.

**8. Một `ConcurrentHashMap<String, ArrayList<String>>` có bảo vệ được `get(k).add(...)` đồng thời không?**

Không. Thread safety của Map không truyền vào các field/method mutable của value.

**9. Hai lần `compute` cập nhật hai key có bảo đảm invariant xuyên cả hai không?**

Không. Cần một cơ chế atomicity chung hoặc gộp state dưới một key.

**10. Vì sao một record bất biến dưới một key dễ đọc nhất quán hơn nhiều key rời rạc?**

Reader lấy một reference snapshot duy nhất, chứa các field thuộc cùng phiên bản được công bố nguyên tử bằng một update của mapping.

**11. Có thể coi `entrySet()` trong lúc Map bị ghi là ảnh chụp chính xác tại một thời điểm không?**

Không. Iterator weakly consistent; có thể phản ánh một số thay đổi đồng thời, không hứa snapshot tuyến tính hóa toàn Map.

**12. `computeIfAbsent` và `putIfAbsent` khác nhau ở đâu về việc tạo candidate?**

`putIfAbsent` nhận value đã được tạo trước. `computeIfAbsent` gọi mapping function khi key vắng mặt để tạo value; phải giữ callback ngắn, không side effect không kiểm soát.

**13. `LongAdder.sum()` dưới contention có phải giá trị snapshot nguyên tử tuyệt đối không?**

Không. Nó hữu ích cho thống kê cộng dồn, nhưng không phải snapshot đồng thời tuyến tính hóa.

**14. Nếu callback `compute` ném `IllegalStateException`, mapping có được cập nhật từ lần tính lỗi đó không?**

Không; thao tác thất bại và mapping tại key không bị cập nhật do lần gọi đó. Side effect ngoài Map đã xảy ra trước exception thì không được tự rollback.

**15. Tại sao key mutable là nguy hiểm dù dùng ConcurrentHashMap?**

Vì `equals`/`hashCode` có thể thay đổi sau khi chèn; Map có thể không tìm lại mapping như mong đợi.

**16. ConcurrentHashMap có luôn nhanh hơn một khóa `synchronized` không?**

Không. Kết quả phụ thuộc workload, số key nóng, contention, độ dài callback, bộ nhớ/cache và chi phí thực tế.

## 27. Tổng kết

**`putIfAbsent`**, **`compute`** và **`merge`** là ba thao tác nền tảng để thực hiện những thay đổi **nguyên tử theo từng key** của `ConcurrentHashMap`:

1. **`putIfAbsent(k,v)`:** chỉ cài giá trị khi key chưa có, không ghi đè giá trị cũ; phù hợp đăng ký/khởi tạo.
2. **`compute(k,f)`:** tính lại state từ giá trị hiện tại và có thể xóa bằng `null`; phù hợp validation, biến đổi state, immutable snapshots.
3. **`merge(k,v,f)`:** nếu chưa có key thì cài `v`; nếu có thì gộp với giá trị cũ; phù hợp counter và tổng hợp.

Chúng sửa được các race condition kiểu **check-then-act** và **read-modify-write** trong phạm vi **một mapping**, nhưng không biến các chuỗi thao tác nhiều key thành transaction, không tự bảo vệ mutable object chứa trong value và không đảm bảo side effect ngoài JVM atomic cùng Map.

Để dùng an toàn: giữ key/value không null, key có hash/equality ổn định, callback ngắn và không cập nhật lại Map từ bên trong, hiểu semantics của `null`/exception, chọn primitive phù hợp khi key nóng, và đợi các worker hoàn tất trước khi tạo báo cáo toàn Map cần độ chính xác. **Thread-safe collection là công cụ; tính đúng đắn vẫn phụ thuộc giao thức dữ liệu và invariant của ứng dụng.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-003">Atomic compound operations của ConcurrentHashMap</a>

<details>
<summary>Xem chi tiết</summary>

# Atomic compound operations của ConcurrentHashMap trong Java Concurrency

> **Phạm vi:** Java SE concurrency nói chung, thuộc **Concurrent Collections**. Bài giải thích vì sao các thao tác riêng lẻ của `ConcurrentHashMap` thread-safe nhưng chuỗi đọc–quyết định–ghi không tự nguyên tử; cách sử dụng `putIfAbsent`, `remove(key,value)`, `replace`, `computeIfAbsent`, `computeIfPresent`, `compute` và `merge`; ranh giới nguyên tử theo từng khóa, việc sử dụng `LongAdder`, an toàn của remapping function, các lỗi thường gặp và kiểm thử Java 17. Những phần chỉ thuộc Spring được dành cho chương khác.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Phân biệt **thread-safe method** với **atomic compound operation**: `get` và `put` đều an toàn khi gọi riêng lẻ nhưng `get → tính toán → put` không phải một thao tác nguyên tử.
- Chỉ ra lịch xen kẽ gây **lost update**, check-then-act, duplicate initialization hoặc stale conditional update, ngay cả khi dùng một `ConcurrentHashMap`.
- Chọn đúng API: `putIfAbsent` cho khởi tạo nếu thiếu; `replace(key, oldValue, newValue)`/`remove(key, value)` cho cập nhật có điều kiện; `compute*`/`merge` cho biến đổi dựa trên giá trị hiện tại.
- Hiểu khác biệt giữa **mapping tồn tại/không tồn tại** và semantics `null` trong `ConcurrentHashMap`, bao gồm việc trả `null` từ remapping function có thể khiến mapping không được thêm hoặc bị xóa.
- Giải thích vì sao những thao tác compound này có bảo đảm **nguyên tử trên một mapping/khóa**, nhưng không tạo một giao dịch nguyên tử cho nhiều khóa, các collection khác hoặc side effects bên ngoài JVM.
- Thiết kế remapping function **ngắn, đơn giản, không làm I/O lâu, không cập nhật đệ quy cùng map, không dựa vào side effect để đo số lần thực thi**.
- Áp dụng mẫu `computeIfAbsent(key, k -> new LongAdder()).increment()` để đếm đồng thời, đồng thời nêu rõ giới hạn của `LongAdder.sum()` và nguy cơ thay thế/xóa mapping trong lúc các thread khác còn dùng counter cũ.
- Kiểm thử Java 17 có trình tự điều khiển để chứng minh lost update của `get/put` và kết quả chính xác khi dùng `merge` dưới nhiều threads.
- Đọc đúng các số liệu `size()`, `mappingCount()`, iterator, bulk operations và memory effects mà không coi chúng là snapshot transaction.

**Thông điệp cốt lõi:** `ConcurrentHashMap` giải quyết an toàn cho cấu trúc map và cung cấp **những phép biến đổi atomic theo từng khóa**. Nó **không tự biến mọi đoạn code có nhiều lời gọi map thành một giao dịch nguyên tử**.

## 2. Một method thread-safe khác một workflow thread-safe

Giả sử map giữ số lượt truy cập:

```java
ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
counts.put("java", 0);
```

Đây là đoạn code trong một method với import `java.util.concurrent.ConcurrentHashMap`. Hai lời gọi trên được thực thi trong cùng thread để tạo dữ liệu ban đầu.

Nhiều người viết:

```java
int old = counts.get("java");
counts.put("java", old + 1);
```

Đây là **mã minh họa không đồng bộ đúng cho phép tăng**. `get()` là thread-safe và `put()` cũng thread-safe, nhưng giữa chúng có một khoảng thời gian để thread khác chạy. Cả hai có thể đọc cùng một giá trị cũ rồi cùng ghi kết quả mới giống nhau.

```text
Ban đầu: count = 0

Thread T1                   Thread T2
-------------------------   -------------------------
get("java") -> 0
                            get("java") -> 0
put("java", 1)
                            put("java", 1)

Kết quả cuối: 1
Số lượt tăng dự kiến: 2
```

Đây là **lost update** do **read–modify–write không nguyên tử**, không phải vì ConcurrentHashMap “không thread-safe”. Mỗi operation riêng lẻ vẫn đúng theo hợp đồng concurrent collection của Java.

Một cách sửa đúng:

```java
counts.merge("java", 1, Integer::sum);
```

Đây là đoạn code trong method, với `counts` đã có. `merge` tính toán và cập nhật mapping hiện tại như **một compound operation atomic theo khóa**. Nếu hai threads cùng tăng cùng khóa, hai kết quả được áp dụng theo một thứ tự nguyên tử hợp lệ; không xảy ra lost update do `get/put` tách rời.

### Cần xác định invariant trước khi chọn primitive

- **Chỉ cần “nếu khóa chưa tồn tại thì đăng ký”** → `putIfAbsent`.
- **Chỉ cập nhật nếu giá trị hiện tại còn bằng giá trị đã quan sát** → `replace(key, expected, update)`.
- **Chỉ xóa nếu mapping chưa bị thay đổi** → `remove(key, expectedValue)`.
- **Cần tính toán từ giá trị hiện tại** → `compute`, `computeIfPresent`, `merge`.
- **Cần tạo object một lần nếu khóa vắng mặt** → `computeIfAbsent`.
- **Cần nguyên tử trên hai khóa hoặc database + map** → các API kể trên **chưa đủ**; cần thiết kế synchronization/transaction khác.

## 3. Atomic compound operation là gì?

Một **compound operation** gồm nhiều quyết định liên quan nhau, chẳng hạn “đọc hiện tại, kiểm tra điều kiện, tính giá trị tiếp theo, cập nhật”. Nếu thực hiện bằng các lời gọi tách biệt:

```text
get(key) -> decide -> put(key, next)
```

thì state có thể thay đổi giữa các bước. Khi dùng API atomic tương ứng, **quyết định và cập nhật được phối hợp thành một operation nguyên tử trên mapping**.

`ConcurrentHashMap` hỗ trợ các compound updates theo khóa thông qua `ConcurrentMap` và các overrides cụ thể. Không nên suy từ interface `Map` chung hoặc từ việc một map đồng bộ từng method rằng **mọi default implementation của compute/merge** đều có cùng mức bảo đảm; ở đây đang nói chính xác về **`ConcurrentHashMap` của Java SE 17**.

Ví dụ:

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
map.put("votes", 0);
map.compute("votes", (key, current) -> current + 1);
```

Đoạn code trong method, import `ConcurrentHashMap`. Với khóa `votes` đã tồn tại, `compute` lấy mapping hiện tại, tính từ giá trị đó và cập nhật dưới bảo đảm nguyên tử của operation. Nhiều threads gọi `compute` cùng khóa sẽ không bị kiểu mất cập nhật như hai lời gọi `get`/`put`.

### Nguyên tử đến đâu?

**Có:** một lời gọi `compute/merge/replace/putIfAbsent` thành công có effect trên **mapping đang liên quan** như một atomic operation theo contract.

**Không tự có:** một transaction của cả map; atomicity của hai khóa khác nhau; atomicity của việc thêm entry vào map rồi ghi database; atomicity của các trường bên trong object mutable được lưu làm value.

Đừng đánh đồng “mọi thao tác trên map đều concurrency-safe” với “mọi business invariant dùng map đều được đảm bảo”.

## 4. Vì sao cần ConcurrentHashMap cho compound operations?

`HashMap` không bảo đảm thread safety khi nhiều writers sửa cùng lúc. `ConcurrentHashMap` bảo vệ cấu trúc và cung cấp các phép cập nhật atomic có điều kiện theo mapping. Iterator của map là weakly consistent, không tạo snapshot nhất quán của toàn map.

Ở đây, câu hỏi cốt lõi là **điều kiện và cập nhật có nằm trong một phép atomic duy nhất hay không**. `ConcurrentHashMap` không tạo transaction nhiều key, không cho null key/value; các khác biệt nền tảng với `HashMap` đã được giải thích ở bài mở đầu chương.

## 5. Bảng chọn nhanh các thao tác nguyên tử

| API | Khi nào thực hiện cập nhật? | Giá trị trả về / ý nghĩa | Điểm cần nhớ |
|---|---|---|---|
| `putIfAbsent(k, v)` | Chỉ khi khóa chưa có mapping | Giá trị cũ, hoặc `null` nếu absent | Không ghi đè giá trị đã có |
| `remove(k, expectedValue)` | Chỉ khi mapping hiện tại bằng expectedValue | `boolean` | Compare-and-remove theo khóa/giá trị |
| `replace(k, expectedValue, newValue)` | Chỉ khi giá trị hiện tại khớp expectedValue | `boolean` | Compare-and-replace theo equality |
| `replace(k, newValue)` | Chỉ nếu khóa đã có mapping | Giá trị cũ hoặc `null` | Không tạo mapping mới |
| `computeIfAbsent(k, function)` | Chỉ tính và thêm khi absent | Giá trị hiện có/mới, có thể `null` nếu function trả null | Atomic lazy initialization |
| `computeIfPresent(k, function)` | Chỉ tính khi present | Giá trị mới hoặc `null` | Function trả null → xóa mapping |
| `compute(k, function)` | Tính trên current, kể cả absent | Giá trị mới hoặc `null` | Function nhận current là null nếu absent |
| `merge(k, value, function)` | Nếu absent, thêm value; nếu present, kết hợp với current | Giá trị kết quả hoặc `null` nếu mapping bị xóa | Thường hợp counter/aggregation |
| `replaceAll(function)` | Áp function cho các mappings | `void` | **Không** biến toàn bộ map thành một transaction snapshot |

Bảng áp dụng cho `ConcurrentHashMap` Java SE 17. `null` trong các API remapping là **tín hiệu điều khiển được định nghĩa riêng**; nó không có nghĩa CHM lưu null value trong map.

## 6. putIfAbsent: khởi tạo nếu còn thiếu

Sử dụng:

```java
ConcurrentHashMap<String, String> registry = new ConcurrentHashMap<>();
String first = registry.putIfAbsent("job-42", "CREATED");
String again = registry.putIfAbsent("job-42", "DUPLICATE");
System.out.println(first); // null
System.out.println(again); // CREATED
```

Đây là code trong method, với import `ConcurrentHashMap`. Lần đầu khóa chưa tồn tại nên giá trị CREATED được gắn vào, return `null`. Lần hai khóa đã có, method **không ghi đè**, trả giá trị cũ là CREATED.

### Vì sao không viết containsKey rồi put?

```java
if (!registry.containsKey("job-42")) {
    registry.put("job-42", "CREATED");
}
```

Đây là **mẫu check-then-act không nguyên tử**. Hai threads có thể đều thấy khóa absent rồi đều ghi; lần ghi sau sẽ ghi đè. Nếu cần “chỉ một writer thắng”, dùng `putIfAbsent`.

### Khởi tạo có side effect: putIfAbsent chưa chắc đủ

```java
ExpensiveValue candidate = loadFromNetwork();
ExpensiveValue winner = map.putIfAbsent(key, candidate);
```

Đây là code minh họa cần `ExpensiveValue`, `map`, `key`, `loadFromNetwork()`. Dù insert chỉ thắng một lần, **loadFromNetwork()` đã chạy trước `putIfAbsent`** ở mỗi thread gọi. Nhiều threads có thể cùng làm I/O tốn kém, dù chỉ một object được giữ.

`computeIfAbsent` cho phép gắn computation vào điều kiện absent một cách atomic theo khóa; tuy nhiên việc chuyển I/O chậm vào mapping function có thể làm blocking các updates khác và không tự mang durability/timeout. Cần cân nhắc mô hình loading riêng.

## 7. remove(key, value): xóa có điều kiện

`remove(key)` xóa mapping theo khóa. Nếu bạn đã đọc một giá trị trước đó và **chỉ muốn xóa khi mapping chưa đổi**, dùng:

```java
boolean removed = registry.remove("job-42", "CREATED");
```

Đây là code minh họa, `registry` đã định nghĩa ở phần trước. `true` chỉ khi mapping hiện tại khớp giá trị expected theo semantics của map. Nếu thread khác đã chuyển trạng thái sang RUNNING, lời xóa với expected CREATED sẽ trả false; bạn không xóa nhầm state mới.

**Sai lầm cần tránh:**

```java
if ("CREATED".equals(registry.get("job-42"))) {
    registry.remove("job-42"); // Có thể xóa state mới vừa bị thread khác ghi!
}
```

Đoạn này là **mã minh họa lỗi**: thread khác có thể đổi CREATED→RUNNING giữa `get` và `remove`.

Conditional remove có ích cho invalidating cached entries, cleanup một phiên bản đã biết hoặc xóa marker sau khi hoàn tất—nhưng nếu value có thể quay lại giá trị equal cũ, còn phải xét **ABA/generation** ở tầng business.

## 8. replace(key, old, new): compare-and-swap theo mapping

`ConcurrentHashMap.replace(key, expectedValue, newValue)` kiểm tra giá trị hiện tại của một khóa, rồi chỉ thay nếu nó khớp expected.

```java
ConcurrentHashMap<String, String> states = new ConcurrentHashMap<>();
states.put("order", "NEW");
boolean claimed = states.replace("order", "NEW", "PROCESSING");
boolean stale = states.replace("order", "NEW", "FAILED");
```

Đây là code minh họa. `claimed` sẽ true, `stale` sẽ false nếu không có thread khác can thiệp giữa hai phép gọi.

`replace(key, newValue)` là một overload khác: nó chỉ yêu cầu key **đã tồn tại**, không kiểm tra expected old value; return giá trị cũ hoặc null nếu absent.

### Equality thay vì identity

`replace(key, oldValue, newValue)` và `remove(key, value)` sử dụng **value equality (`equals`)** theo semantics map, không phải reference CAS identity `==` của `AtomicReference`. Nếu hai object khác instance nhưng equals bằng nhau, conditional replace có thể coi là khớp. Điều này rất quan trọng khi dùng enum, record, string hoặc value object có equality theo field.

**Cảnh giác:** nếu business invariant cần “đúng generation đã đọc”, equality giữa hai value objects ở hai thời điểm khác nhau chưa chắc đủ. Có thể cần immutable version field trong value, token/generation ID hoặc một giao thức optimistic versioning riêng.

### Compare-and-replace có xử lý toàn bộ workflow không?

Không. Nó chỉ bảo vệ một cập nhật mapping. Nếu caller đọc nhiều khóa A/B rồi chỉ replace A, value B có thể thay đổi trong khoảng đó. Khi business invariant phụ thuộc nhiều khóa, phải bảo vệ/kiểm chứng cả tập dependency.

## 9. computeIfAbsent: lazy initialization nguyên tử

`computeIfAbsent(key, mappingFunction)` là công cụ thường dùng khi muốn tạo value **nếu và chỉ nếu key đang absent**.

```java
ConcurrentHashMap<String, List<String>> buckets = new ConcurrentHashMap<>();
List<String> javaBucket = buckets.computeIfAbsent(
        "java", k -> new CopyOnWriteArrayList<>());
```

Đây là code trong method, cần imports `ConcurrentHashMap`, `List`, `CopyOnWriteArrayList`. Với khóa java absent, mapping function tạo list mới rồi map công bố một reference đến list đó. Nếu key đã có value, function không cần chạy.

Theo JavaDoc `ConcurrentHashMap` Java 17, **toàn bộ method invocation được thực hiện nguyên tử**, và mapping function được áp dụng **nhiều nhất một lần cho mỗi lần invocation / khóa tương ứng** trong quá trình tính toán của lời gọi. Các updates cạnh tranh trên map có thể phải chờ computation hoàn tất.

### Bốn trường hợp cần phân biệt

1. **Khóa present:** trả value hiện có, mapping function không được gọi.
2. **Khóa absent, function trả non-null:** thêm mapping mới rồi trả value đó.
3. **Khóa absent, function trả null:** **không thêm mapping** và trả null; lần gọi sau có thể thử tính lại.
4. **Function ném unchecked exception/Error:** exception được truyền cho caller theo contract, mapping chưa được thiết lập bởi lời gọi đó; ứng dụng phải xử lý failure.

**Không lưu null value vào ConcurrentHashMap.** Đặt null thông qua `put` bị cấm; việc `computeIfAbsent` trả null là kết quả “không tạo mapping”, không phải chứa null trong map.

### Có được coi computeIfAbsent là cache loader production?

Có thể dùng cho object initialization **nhẹ và nhanh**, nhưng không nên mặc định đặt network call/blocking I/O lâu vào function:

```java
// Minh họa nguy cơ: slow I/O trong mapping function.
CacheValue value = cache.computeIfAbsent(key, k -> loadRemote(k));
```

Đây là snippet, `CacheValue`/`cache`/`loadRemote` do ứng dụng định nghĩa. Một số threads cập nhật mapping bị ảnh hưởng có thể bị block khi computation dài. Nếu remote call timeout hoặc ném lỗi, key không được cache thành công và nhiều caller đến sau có thể thử lại, tạo stampede.

Các phương án thiết kế có thể gồm lưu `CompletableFuture<Value>` như một **placeholder cho in-flight computation**, dùng cache có đồng bộ loading và policy timeout/eviction, hoặc tách rate limit của upstream. Những phương án này cũng cần xử lý failed Future/retry/eviction riêng; CHM không tự giải quyết toàn bộ caching lifecycle.

## 10. computeIfPresent: chỉ xử lý giá trị đã tồn tại

`computeIfPresent(key, remappingFunction)` chỉ gọi function khi mapping hiện có.

```java
ConcurrentHashMap<String, Integer> attempts = new ConcurrentHashMap<>();
attempts.put("job", 1);
attempts.computeIfPresent("job", (k, current) -> current + 1);
```

Đây là code minh họa. Sau lời gọi, giá trị job thành 2.

Nếu key absent, function không chạy và map không tạo entry mới. Nếu function trả `null`, mapping hiện tại bị **xóa**:

```java
attempts.computeIfPresent("job", (k, current) -> null);
// "job" absent sau lời gọi nếu không có thread khác thêm lại.
```

Đây là minh họa hợp lệ theo contract CHM: null là một cách yêu cầu remove, không phải null value được lưu.

### Tình huống TTL/cleanup

Khi muốn xóa entry dựa trên thuộc tính value cũ mà không tách check-then-remove:

```java
cache.computeIfPresent(key, (k, entry) ->
        entry.expiredAt(now) ? null : entry);
```

Đây là đoạn minh họa, cần `cache`, `key`, `entry` có method `expiredAt` và `now` được xác định. Quyết định xóa mapping hiện tại dựa trên snapshot value mà function nhận **ngay trong compound operation**. Nhưng thời gian hệ thống/TTL policy và tính mutable của `entry` cần được định nghĩa; nếu entry mutable bị sửa ngoài map, function không tự khóa object.

## 11. compute: remapping tổng quát kể cả absent

`compute(key, (k, oldValue) -> newValue)` được gọi cả khi key present và absent. Nếu absent, `oldValue == null`. Đây là lý do phải xử lý null nếu cần tạo giá trị ban đầu.

```java
ConcurrentHashMap<String, Integer> counters = new ConcurrentHashMap<>();
counters.compute("java", (key, current) ->
        current == null ? 1 : current + 1);
```

Đây là code trong method. Kết quả java=1. Gọi thêm lần nữa cho java=2; mỗi lần đưa ra quyết định theo current mapping ở thời điểm thao tác atomic.

**Semantics trả về null:**

- Nếu mapping cũ tồn tại, function trả null → **xóa mapping**.
- Nếu mapping absent, function trả null → **không tạo mapping**.
- Nếu function ném unchecked exception hoặc Error, lỗi đi ra caller và mapping chưa được update bởi lời gọi đó.

### Không dùng compute để làm I/O hay transaction ngoài JVM

`compute` có thể serialize/coordinate các update cùng key trong map, nhưng function có thể cần được thực thi trong cơ chế đồng bộ nội bộ. Không nên gọi database transaction dài, gửi email, gọi API, hoặc đợi một task khác mà cần cập nhật cùng map từ function.

`compute` không biến tác động ngoài map thành nguyên tử cùng entry. Nếu function đã gửi email rồi xảy ra lỗi trước khi trả, state map có thể không thay đổi còn email đã gửi. Thiết kế phải dựa trên idempotency/outbox/transaction riêng nếu nghiệp vụ đòi hỏi.

## 12. merge: mẫu đơn giản nhất cho phép cộng và aggregation

`merge(key, value, remappingFunction)` có hai nhánh:

- **Absent:** đưa trực tiếp `value` (phải non-null) vào map; **không gọi remappingFunction**.
- **Present:** gọi `remappingFunction.apply(currentValue, value)`; nếu trả non-null thì ghi kết quả; nếu trả null thì **xóa mapping**.

```java
ConcurrentHashMap<String, Integer> totals = new ConcurrentHashMap<>();
totals.merge("clicks", 1, Integer::sum);
totals.merge("clicks", 1, Integer::sum);
```

Đây là code trong method. Kết quả clicks = 2.

### Vì sao merge chống lost update?

Giả sử có 4 threads, mỗi thread gọi `totals.merge("clicks", 1, Integer::sum)` 1.000 lần. Các lần cập nhật cùng khóa được thực thi với compound semantics nguyên tử. Nếu không overflow int và tất cả lời gọi hoàn tất, kết quả cuối là **4.000**.

Điều này khác bản cũ:

```java
// Sai khi nhiều threads tăng cùng key:
totals.put("clicks", totals.get("clicks") + 1);
```

Đây là **mã lỗi minh họa**: get/put có thể interleave và làm mất cập nhật.

### Tính đúng của phép toán

Nếu bạn dùng `Integer::sum` với `Integer`, phép cộng có thể overflow khi tổng vượt giới hạn 32-bit. Atomically applied updates **không** tự bảo đảm không overflow. Nếu yêu cầu miền giá trị lớn hơn, dùng `Long`/`LongAdder` hoặc kiểm tra bounds theo contract.

Nếu remappingFunction không giao hoán/kết hợp (ví dụ ghép String hoặc cập nhật state machine), thứ tự các atomic updates giữa threads có thể ảnh hưởng kết quả hợp lệ. Map bảo đảm atomicity theo khóa, **không áp đặt thứ tự nghiệp vụ** của mọi producer.

### merge và null

`merge(k, null, f)` không hợp lệ trên `ConcurrentHashMap`; `value` phải non-null. Nhưng `f` có thể trả null để yêu cầu remove một mapping tồn tại. Khi absent, `f` không được gọi; `value` được thêm trực tiếp.

## 13. Ví dụ Java 17 chạy độc lập: so sánh lỗi và các thao tác atomic

Ví dụ này có năm phần, tất cả đều được kiểm thử bằng `javac --release 17`:

1. Buộc hai threads đọc cùng count=0 trước khi cả hai ghi bằng `get/put`; kết quả lost update là 1 **một cách xác định**, không phụ thuộc may rủi scheduler.
2. Chạy bốn threads đồng thời, mỗi thread `merge` 1.000 lần; kết quả 4.000.
3. Thử `putIfAbsent`, `replace(key,old,new)` và `remove(key,value)`.
4. Dùng `computeIfAbsent` khởi tạo một `LongAdder` chung, bốn threads increment 1.000 lần; chứng minh factory chạy một lần trong workload đã cho.
5. Dùng `compute`/`computeIfPresent` và function trả null để xóa/không thêm mapping.

Tạo file **`ConcurrentMapAtomicDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class ConcurrentMapAtomicDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            ConcurrentHashMap<String, Integer> broken = new ConcurrentHashMap<>();
            broken.put("count", 0);
            CountDownLatch bothRead = new CountDownLatch(2);
            CountDownLatch mayWrite = new CountDownLatch(1);
            List<Future<?>> unsafeTasks = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                unsafeTasks.add(pool.submit(() -> {
                    int old = broken.get("count");
                    bothRead.countDown();
                    mayWrite.await();
                    broken.put("count", old + 1);
                    return null;
                }));
            }
            bothRead.await();
            mayWrite.countDown();
            for (Future<?> future : unsafeTasks) future.get();
            System.out.println("getThenPut=" + broken.get("count"));

            ConcurrentHashMap<String, Integer> totals = new ConcurrentHashMap<>();
            List<Future<?>> safeTasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                safeTasks.add(pool.submit(() -> {
                    for (int j = 0; j < 1000; j++) {
                        totals.merge("count", 1, Integer::sum);
                    }
                    return null;
                }));
            }
            for (Future<?> future : safeTasks) future.get();
            System.out.println("mergeTotal=" + totals.get("count"));

            ConcurrentHashMap<String, String> registry = new ConcurrentHashMap<>();
            System.out.println("firstInsert=" + (registry.putIfAbsent("id", "A") == null));
            System.out.println("secondReturned=" + registry.putIfAbsent("id", "B"));
            System.out.println("compareReplace=" + registry.replace("id", "A", "C"));
            System.out.println("staleReplace=" + registry.replace("id", "A", "D"));
            System.out.println("conditionalRemove=" + registry.remove("id", "C"));

            ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
            AtomicInteger factoryCalls = new AtomicInteger();
            List<Future<?>> counterTasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                counterTasks.add(pool.submit(() -> {
                    for (int j = 0; j < 1000; j++) {
                        counts.computeIfAbsent("java", k -> {
                            factoryCalls.incrementAndGet();
                            return new LongAdder();
                        }).increment();
                    }
                    return null;
                }));
            }
            for (Future<?> future : counterTasks) future.get();
            System.out.println("factoryCalls=" + factoryCalls.get());
            System.out.println("longAdderSum=" + counts.get("java").sum());

            ConcurrentHashMap<String, Integer> statuses = new ConcurrentHashMap<>();
            statuses.compute("attempts", (k, v) -> v == null ? 1 : v + 1);
            statuses.computeIfPresent("attempts", (k, v) -> v + 1);
            System.out.println("computeAndPresent=" + statuses.get("attempts"));
            statuses.computeIfPresent("attempts", (k, v) -> null);
            System.out.println("removedByNull=" + !statuses.containsKey("attempts"));
            System.out.println("absentOnNull=" +
                    (statuses.computeIfAbsent("missing", k -> null) == null
                            && !statuses.containsKey("missing")));
        } finally {
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 ConcurrentMapAtomicDemo.java
java ConcurrentMapAtomicDemo
```

**Stdout chính xác đã xác minh:**

```text
getThenPut=1
mergeTotal=4000
firstInsert=true
secondReturned=A
compareReplace=true
staleReplace=false
conditionalRemove=true
factoryCalls=1
longAdderSum=4000
computeAndPresent=2
removedByNull=true
absentOnNull=true
```

### Phân tích phần lost update có lịch thực thi kiểm soát

Hai tasks bắt đầu trong pool có **bốn workers**. Mỗi task:

1. Đọc `broken.get("count")` khi giá trị ban đầu là 0.
2. Gọi `bothRead.countDown()`.
3. Chờ `mayWrite.await()`.
4. Sau khi main đã nhận cả hai tín hiệu đã đọc, main gọi `mayWrite.countDown()`.
5. Cả hai tasks ghi `old + 1`; vì old đều là 0, cả hai cùng ghi 1.

Dù task nào ghi trước, giá trị cuối luôn là **1**. Đây là một test xác định chứng minh chính xác **hai method thread-safe không ghép thành atomic increment**.

### Phân tích merge 4.000 lần

Bốn tasks, mỗi task chạy vòng 1.000 lần, gọi:

```java
totals.merge("count", 1, Integer::sum);
```

Tổng 4.000 updates. Mỗi update được thực hiện với semantics nguyên tử theo mapping `count`, nên không mất lần tăng do concurrent interleaving. `Future.get()` ở main chờ các tasks hoàn tất, sau đó mới đọc kết quả. Vì tổng 4.000 nằm trong phạm vi int, không có overflow trong ví dụ.

### Phân tích registry

- `putIfAbsent("id","A")` lần đầu trả null; biểu thức in `firstInsert=true`.
- `putIfAbsent("id","B")` tiếp theo trả giá trị cũ A, không ghi đè; `secondReturned=A`.
- `replace("id","A","C")` thành công và map giữ C.
- `replace("id","A","D")` thất bại vì expected A đã stale.
- `remove("id","C")` xóa có điều kiện và trả true.

Tất cả các operation registry trong demo chạy tuần tự ở main để stdout không phụ thuộc scheduler. Đường concurrent behavior tương tự có thể được kiểm tra bằng latch nếu muốn kiểm thử tranh chấp cụ thể.

### Phân tích computeIfAbsent + LongAdder

Bốn threads gọi `computeIfAbsent("java", factory).increment()`. Vì key java được thêm và **không bị xóa hoặc replace** trong suốt test, một `LongAdder` duy nhất được share, factory chạy một lần trong tình huống đã kiểm tra, và 4.000 increments cộng dồn.

**Đừng suy từ `factoryCalls=1` của demo rằng mọi invocation trong đời ứng dụng sẽ luôn chỉ gọi factory đúng một lần tổng cộng:** nếu key bị remove, function trả null hoặc computation thất bại, những lời gọi sau có thể chạy factory lại. Bảo đảm phải đọc theo **mỗi key, mỗi operation và trạng thái mapping**.

### Phân tích null return

`compute("attempts",...)` khởi tạo thành 1; `computeIfPresent` tăng lên 2. `computeIfPresent` trả null thì **loại mapping**; `computeIfAbsent("missing", k -> null)` không tạo mapping. Hai biểu thức `removedByNull` và `absentOnNull` đều true.

## 14. LongAdder và computeIfAbsent: một mẫu counter hiệu quả

Javadoc `ConcurrentHashMap` có mẫu frequency map gần giống:

```java
ConcurrentHashMap<String, LongAdder> freq = new ConcurrentHashMap<>();
freq.computeIfAbsent(word, k -> new LongAdder()).increment();
```

Đây là đoạn minh họa với `word` định nghĩa bên ngoài, cần import `ConcurrentHashMap` và `java.util.concurrent.atomic.LongAdder`.

### Tại sao hai tầng đồng bộ?

1. **Map:** `computeIfAbsent` bảo đảm việc tạo/nhận reference counter cho key được điều phối atomically.
2. **Counter:** `LongAdder.increment()` thực hiện cập nhật thread-safe trên chính counter object theo API LongAdder.

Không phải `computeIfAbsent` bảo vệ toàn bộ thời gian thread đang gọi `increment()`. Khi function trả về, việc tăng diễn ra **ngoài** compound operation của map, nhưng LongAdder có concurrency semantics riêng.

### Caveat khi map bị remove hoặc replace

Giả sử thread T1 lấy reference LongAdder cũ:

```java
LongAdder oldCounter = freq.computeIfAbsent(word, k -> new LongAdder());
```

Sau đó T2 xóa mapping `word` khỏi map và tạo LongAdder mới. T1 vẫn có reference `oldCounter` và gọi `increment()` trên object **không còn được map trỏ tới**. Không có data race trong chính LongAdder, nhưng **logical count theo key có thể bị mất** khi bạn đọc current mapping mới.

Vì vậy mẫu này hoạt động tốt khi lifecycle của counter ổn định và **không xóa/replace counter trong lúc có updater đang dùng**. Nếu cần reset/rotate counters trong thời gian threads đang increment, hãy thiết kế epoch/generation ownership, snapshot/hand-off hoặc đồng bộ rõ ràng.

### `LongAdder.sum()` có phải snapshot atomic không?

Không. `sum()` trả tổng hiện tại được tính từ các phần của LongAdder, và **không phải snapshot nguyên tử tuyệt đối** khi các thread khác còn increment đồng thời. Nó thích hợp cho thống kê hiệu năng cao, nhưng không dùng làm tín hiệu transaction/sequence cần một giá trị tuyến tính hóa chính xác giữa concurrent writers.

Trong demo, main gọi `Future.get()` cho **tất cả** worker tasks xong rồi mới gọi `sum()`, nên không còn writer song song và kết quả 4.000 là xác định.

### Khi nào dùng AtomicLong thay LongAdder?

- `AtomicLong` phù hợp với counter cần các phép `compareAndSet`, `getAndIncrement` và giá trị đọc tức thời có semantics atomic của một biến.
- `LongAdder` có thể giảm contention đối với thống kê có rất nhiều threads cùng tăng, đổi lại `sum()` không phải một atomic snapshot khi updates đang diễn ra.
- Với counter nhỏ hoặc contention thấp, chọn loại đơn giản nhất rồi đo, không mặc định LongAdder luôn nhanh hơn.

## 15. Đừng mutate map bên trong remapping function

JavaDoc của `ConcurrentHashMap.compute/computeIfAbsent/computeIfPresent/merge` yêu cầu computation ngắn, đơn giản và **không nên cố cập nhật các mapping khác của cùng map ngay trong computation**. Việc update đệ quy có thể gây blocking hoặc một `IllegalStateException` khi JVM phát hiện recursive update không bao giờ hoàn tất.

Ví dụ sai:

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
map.computeIfAbsent("a", k -> {
    map.put("b", 1); // Không làm việc này bên trong mapping function.
    return 2;
});
```

Đây là **ví dụ thiết kế cần tránh**, không có cam kết chương trình luôn ném exception trong mọi interleaving/keys. Bởi cách map điều phối cập nhật, việc thay một key khác có thể vô tình gây contention/reentrancy vấn đề; không phụ thuộc vào việc một lần test cho qua.

Ví dụ sai rõ hơn:

```java
map.computeIfAbsent("a", k ->
        map.computeIfAbsent("a", x -> 1)); // Recursive update.
```

Đây là đoạn minh họa lỗi. Nếu một update đệ quy có thể bị phát hiện như non-terminating recursion, `ConcurrentHashMap` có thể ném `IllegalStateException`; không dùng exception đó làm một kiểu flow control thông thường.

**Nguyên tắc:** function nên là phép biến đổi thuần túy theo `key, current`, có thời gian chạy ngắn, không block, không thực hiện side effect không đảo ngược, không gọi những method cập nhật chính map đó. Nếu cần cập nhật nhiều khóa, hãy tách ra giao thức khác.

## 16. Vì sao slow remapping function có thể làm giảm throughput?

Một số compound methods của CHM giữ coordination với những update cạnh tranh khi remapping function đang thực hiện.

```java
// Minh họa anti-pattern: I/O dài trong compute.
map.compute(key, (k, old) -> {
    sleepOrCallRemoteService();
    return transform(old);
});
```

Đây là pseudocode Java minh họa; `sleepOrCallRemoteService` và `transform` do application định nghĩa.

Một computation lâu có thể làm **những updates khác bị block**, đặc biệt khi chúng cạnh tranh trên cùng mapping hoặc phần state nội bộ bị ảnh hưởng. Không được tuyên bố “compute block toàn bộ ConcurrentHashMap cho mọi key” — CHM được thiết kế cho concurrency giữa những keys; mức ảnh hưởng phụ thuộc các va chạm và implementation.

### Điều nên làm

- Chuẩn bị dữ liệu có thể tính **ngoài function** nếu không cần snapshot current.
- Nếu tính toán bắt buộc phụ thuộc current, làm phần **nhanh và thuần túy** trong `compute`.
- Với I/O dài, thường cần một state machine, placeholder Future, cache loading framework hoặc task queue để tách transaction thời gian dài khỏi map update.
- Đo tail latency và contention khi traffic thật, không chỉ throughput trung bình.
- Đặt deadline/timeout theo API ngoài map, không dùng map lock làm biện pháp chờ network bất tận.

## 17. Hàm mapping có thể được gọi lại không?

Không nên áp một câu trả lời cho mọi loại map hoặc mọi operation.

- Với **`ConcurrentHashMap.computeIfAbsent` Java SE 17**, function được áp dụng at most once cho key ở một invocation theo contract. Nếu key đã có mapping, function không chạy. Nhưng nếu lần trước trả null hoặc fail, lần invocation sau có thể gọi lại.
- Với `compute`/`merge` trong CHM, một invocation được điều phối atomically cho mapping; đừng giả định rằng function được dùng như một callback của transaction manager ngoài JVM hoặc sẽ được retry theo business policy của ứng dụng.
- Với một số `Map` khác, default methods triển khai bằng `get`/`put` không nhất thiết có cùng atomicity; phải đọc API của **class thực tế**.
- Với **các retry loop do caller viết**, function/task có thể chạy nhiều lần qua nhiều invocations; không đặt side effects không idempotent bên trong mà không kiểm soát.

Câu hỏi quan trọng hơn “function gọi mấy lần?” là: **function có side effect nào mà việc chạy trong một critical update làm thay đổi correctness hay throughput không?** Ngay cả một lần gọi duy nhất cũng có thể gây vấn đề nếu nó gửi tiền, xóa file hoặc chờ lock vòng phụ thuộc.

## 18. Multi-key invariants: những gì ConcurrentHashMap không tự làm

Giả sử bạn muốn chuyển 10 đơn vị từ tài khoản A sang B, cả hai nằm trong cùng ConcurrentHashMap:

```java
balances.compute("A", (k, a) -> a - 10);
balances.compute("B", (k, b) -> b + 10);
```

Đây là **đoạn minh họa không đủ an toàn cho giao dịch**: `balances` có các entries phù hợp và balance không null. Mỗi `compute` trên một key là atomic, **nhưng cả cặp không phải một atomic operation**. Một reader giữa hai lời gọi có thể thấy tổng tiền bị giảm 10, hoặc failure giữa hai lời gọi khiến A giảm mà B chưa tăng.

### Các giải pháp khả thi tùy yêu cầu

1. **External lock cho toàn bộ invariant:** bảo vệ cả hai updates và các readers cần snapshot nhất quán bằng cùng lock. Nếu nhiều locks, dùng thứ tự khóa ổn định để tránh deadlock.
2. **Một immutable aggregate state:** lưu một object chứa cả A và B trong `AtomicReference<ImmutableLedger>`, CAS thay nguyên object sau khi validate; bảo đảm cập nhật trên **một reference**. Phù hợp khi size/invariant cho phép.
3. **Transaction ở lớp lưu trữ:** nếu dữ liệu cần durability/consistency xuyên JVM hoặc database, dùng transaction phù hợp.
4. **Event sourcing/serial owner:** serialize commands qua một owner cập nhật ledger và công bố snapshots nhất quán; cần xử lý failure/recovery.

Không có magic `ConcurrentHashMap.computeAllKeysAtomically()` trong API chuẩn. Các bulk methods không biến nhiều keys thành transaction.

### replaceAll không phải global atomic snapshot

`replaceAll((k,v) -> ...)` áp một function lên các mappings, nhưng khi những threads khác đang mutate, nó không tạo **một lần chuyển toàn bộ map nguyên tử** như thay immutable snapshot. Nếu business invariant yêu cầu tất cả keys đổi cùng thời điểm, đừng dùng `replaceAll` làm transaction.

## 19. Iteration, size, mappingCount và snapshot semantics

`ConcurrentHashMap` cho phép iteration trong khi concurrent updates mà không ném `ConcurrentModificationException` theo kiểu fail-fast của HashMap; iterator có **weakly consistent semantics**.

Điều đó nghĩa là:

- Iterator có thể phản ánh một số updates concurrent tùy thời điểm quan sát, không phải một snapshot transaction của toàn bộ map.
- `size()` và `mappingCount()` hữu ích cho thống kê/monitoring, nhưng nếu threads đang update liên tục thì giá trị có thể mang tính ước lượng/transient.
- `mappingCount()` trả kiểu `long`, hữu ích khi mapping count có thể vượt giới hạn biểu diễn bởi `int` của `size()`.
- Một check như `if (map.size()==0) {...}` không giữ map “rỗng” cho phần code tiếp theo; key có thể được thêm ngay sau check.
- Các bulk operations `forEach`, `search`, `reduce` không áp dụng một global lock để tạo snapshot cô lập theo transaction.

Nếu cần snapshot ổn định, có thể phối hợp **external synchronization/quiescence protocol**, immutable snapshots hoặc kiến trúc versioned state. `new HashMap<>(concurrentMap)` có thể tạo một bản copy tại một giai đoạn, nhưng nếu writers đang chạy, bản copy đó **không mặc nhiên là một global point-in-time snapshot**.

## 20. Null, absent và giá trị mặc định

`ConcurrentHashMap` không cho phép null key và null value:

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
// map.put(null, 1);       // NullPointerException
// map.put("a", null);      // NullPointerException
```

Đây là đoạn minh họa với hai dòng cố tình được comment để không làm chương trình crash.

Việc cấm null tạo ra một ý nghĩa rõ ràng cho `get(key) == null`: tại thời điểm đọc, không có mapping tương ứng, khác `HashMap` có thể lưu null value.

Nhưng `get(key)==null` vẫn chỉ là **snapshot tạm thời**, không phải quyền đặt chỗ key. Hai threads cùng đọc null rồi gọi put vẫn có thể race. Để tạo nếu absent, gọi `putIfAbsent` hoặc `computeIfAbsent`.

### null từ compute/merge là điều khiển, không phải data value

| API | Return null từ function | Hành vi đối với map |
|---|---|---|
| `computeIfAbsent` | Không tạo entry | Key vẫn absent |
| `computeIfPresent` | Xóa entry hiện có | Key absent sau operation, trừ update concurrent sau đó |
| `compute` | Xóa nếu present; không thêm nếu absent | Key absent |
| `merge` | Xóa entry khi remappingFunction của present key trả null | Key absent |

**Không được viết code dựa vào “null value” đang nằm trong CHM.** Nếu business có hai trạng thái “unknown” và “absent”, phải dùng một wrapper/enum/record riêng để thể hiện unknown bằng non-null object.

## 21. ABA và value equality khi dùng conditional replace

Một tình huống tinh vi: T1 đọc value A. T2 thay A→B→A (giá trị cuối **equals** A). T1 thực hiện:

```java
boolean updated = map.replace(key, expectedA, next);
```

Đây là đoạn minh họa với `map`, `key`, `expectedA` và `next` đã định nghĩa.

Nếu mapping hiện tại equals expectedA, phép replace có thể thành công dù giữa đó đã có thay đổi. Đây là một hình thức **ABA ở cấp logic của optimistic compare/update**. Nếu business invariant yêu cầu “không một ai thay đổi mapping kể từ khi tôi đọc”, kiểm tra equality của value cuối có thể chưa đủ.

Một cách xử lý là gắn **generation/version** vào immutable value:

```java
record VersionedState(long generation, String status) { }

ConcurrentHashMap<String, VersionedState> states = new ConcurrentHashMap<>();
VersionedState observed = states.get("job");
if (observed != null) {
    VersionedState next = new VersionedState(
            observed.generation() + 1, "RUNNING");
    boolean changed = states.replace("job", observed, next);
}
```

Đây là code trong method với `record` (Java 17 hỗ trợ). `record` sinh equality dựa trên components, nên version khác nhau làm expected value khác nhau. **Điều kiện:** mọi đường thay mapping phải giữ quy tắc tăng generation, không reset/reuse generation khi reader cũ còn tồn tại.

Không dùng versioning nếu invariant thực sự chỉ cần current state equality; thêm phức tạp mà không mang thêm correctness cần thiết.

## 22. Mutable values: ConcurrentHashMap không khóa object bên trong

`ConcurrentHashMap<K, V>` an toàn cho **cấu trúc key→reference**. Nếu value là một `ArrayList`, `HashMap`, POJO mutable hoặc counter thường, việc giữ reference trong CHM **không tự làm phương thức của value thread-safe**.

```java
ConcurrentHashMap<String, ArrayList<String>> map = new ConcurrentHashMap<>();
map.put("queue", new ArrayList<>());
map.get("queue").add("item"); // Race nếu nhiều threads add cùng ArrayList.
```

Đây là **code minh họa thiếu an toàn**, cần imports tương ứng. Nhiều threads có thể cùng mutate `ArrayList` không synchronized dù container là CHM.

Giải pháp:

- Dùng value immutable và thay bằng atomic `compute` hoặc `replace`.
- Dùng value thread-safe phù hợp, ví dụ `LongAdder`, `ConcurrentLinkedQueue` hoặc một lock-protected object.
- Dùng ownership transfer để một thread là owner duy nhất của mutable value tại một thời điểm.
- Đừng trả mutable object cho caller để caller mutate tùy ý nếu state phải tuân thủ invariant được map update quản lý.

### compute có bảo vệ mutation bên trong value không?

Trong một `compute` theo key, logic của function được phối hợp với **các atomic mapping updates cùng key**. Nhưng nếu thread khác đã giữ reference đến value mutable và sửa field trực tiếp **không thông qua compute/lock cùng protocol**, nó vẫn có thể race. Bởi vậy, chỉ sử dụng mutable value được bao bọc đúng giao thức, hoặc ưu tiên immutable replacement.

## 23. Memory consistency trong ConcurrentHashMap

Java concurrent collections có memory consistency guarantees: hành động trong một thread **trước khi công bố object vào concurrent collection** *happen-before* những hành động trong thread khác **sau khi truy cập hoặc lấy object đó** qua collection theo hợp đồng liên quan.

Trong `ConcurrentHashMap`, một lần `get(key)` trả một giá trị non-null có quan hệ memory ordering phù hợp với insertion/update tương ứng của mapping, cho phép công bố object đã chuẩn bị an toàn nếu dùng đúng protocol.

Ví dụ:

```java
record Item(int id, String payload) {}
ConcurrentHashMap<String, Item> items = new ConcurrentHashMap<>();
items.put("order-1", new Item(1, "created"));
// Thread khác get("order-1") rồi dùng immutable record.
```

Đây là đoạn code trong method, với import `ConcurrentHashMap`, record Java 17. Nếu thread khác đọc được Item đã được publish qua map đúng cách, các giá trị đã khởi tạo trước publication được quan sát theo contract.

Nhưng:

1. CHM không làm mutable fields của object tự có happens-before nếu threads tiếp tục sửa chúng **ngoài** map.
2. Đọc `get` ở hai keys khác nhau không tự tạo **snapshot nhất quán** của một business transaction hai khóa.
3. Việc `Future.get()` chờ workers trong demo tạo completion/visibility ở main; nó **không** tự sửa race `get+put` diễn ra trước đó giữa workers.
4. `Thread.sleep()` không là synchronization mechanism thay thế `compute/merge/replace`.
5. Atomic mapping update là điểm phối hợp **trên reference/value mapping**; nó không làm side effect database, network hoặc file system trở nên atomic với map.

Phân biệt atomicity, visibility và consistency của invariant giúp chọn đúng công cụ.

## 24. Rejection và exception từ remapping function

Các method mapping có thể ném ngoại lệ:

- `NullPointerException` khi key/function/value bắt buộc non-null bị truyền null.
- `IllegalStateException` trong một số recursive update có thể phát hiện theo contract.
- Runtime exception hoặc Error do mapping/remapping function ném. Với `compute`/`merge`/`computeIfAbsent`, mapping không được cập nhật bởi lời gọi bị lỗi theo semantics được công bố.
- Application-specific runtime exception do kiểm tra invariant và cố tình throw.

Ví dụ:

```java
try {
    map.compute("job", (k, old) -> {
        if (old != null && old < 0) {
            throw new IllegalStateException("negative state");
        }
        return old == null ? 1 : old + 1;
    });
} catch (IllegalStateException e) {
    reportFailure(e);
}
```

Đây là code minh họa với `map` kiểu `ConcurrentHashMap<String,Integer>`, `reportFailure` do ứng dụng định nghĩa. Khi function ném, không được coi cập nhật đã thành công. Nhưng một side effect mà function làm **trước khi throw** có thể đã xảy ra, nên đừng đặt tác động không đảo ngược trong đó.

### Không nhầm failed update với rejected executor task

Các exceptions trên xảy ra trong lời gọi map. Nếu code gọi map từ một task `ExecutorService.submit()`, ngoại lệ đó có thể được đóng gói trong `Future` và caller quan sát qua `ExecutionException.getCause()`. Việc đó thuộc đường xử lý lỗi task, không thay đổi semantics atomic của CHM. Không nhầm `RejectedExecutionException` của thread pool với một “rejection” từ CHM; ConcurrentHashMap compound methods không dùng rejection handler của executor.

## 25. So sánh với Collections.synchronizedMap và ConcurrentSkipListMap

### Collections.synchronizedMap

Một map được wrap:

```java
Map<String, Integer> synchronizedMap =
        Collections.synchronizedMap(new HashMap<>());
```

Đây là code trong method, cần imports `Map`, `Collections` và `HashMap`. Wrapper đồng bộ các method, nhưng **nhiều lời gọi tách biệt** vẫn cần synchronized block của caller nếu muốn một compound operation.

```java
synchronized (synchronizedMap) {
    int old = synchronizedMap.get("count");
    synchronizedMap.put("count", old + 1);
}
```

Đây là ví dụ đúng về việc dùng **cùng monitor của wrapper** cho cả hai bước. Cần đảm bảo **mọi access khác liên quan cùng invariant** cũng tuân thủ giao thức khóa. So với CHM, một global lock có thể làm các keys độc lập tranh chấp mạnh hơn dưới workload concurrent.

### ConcurrentSkipListMap

`ConcurrentSkipListMap` là một ordered concurrent map dựa trên sort order của keys. Nó phù hợp khi cần sorted/navigable operations. Nhưng **không suy rằng mọi phương thức compute của mọi ConcurrentMap** có cùng implementation atomic semantics như CHM: hãy đọc JavaDoc của class thực tế và xác định yêu cầu per-key vs multi-key.

### AtomicReference với immutable whole-state

Nếu business invariant phụ thuộc nhiều keys và số lượng dữ liệu tương đối nhỏ/có thể copy, một `AtomicReference<ImmutableState>` cho phép CAS thay **toàn bộ snapshot state** có thể phù hợp hơn CHM. Chi phí copy và contention cần cân nhắc; không có giải pháp nào luôn tốt nhất.

## 26. Hiệu năng và thiết kế key

Atomic compound operations có chi phí coordination. Không có một “benchmark winner” chung.

Những yếu tố ảnh hưởng:

- Số threads truy cập **cùng một hot key** hay phân tán trên nhiều keys.
- Độ dài remapping function.
- Chất lượng phân bố `hashCode()` và collisions.
- Tần suất resize, số lượng entries, memory/GC pressure.
- Chi phí tạo object cho mỗi update, boxing `Integer`, churn của entries.
- Tính chất workload CPU-bound/I/O-bound.
- Mức contention thực, kích thước concurrency trong executor.
- Phương án dùng `LongAdder` hay immutable value replacement.

### Hot key và LongAdder

Nếu mọi thread cùng `merge("globalCount", 1, Integer::sum)`, update vào **cùng mapping** tạo contention. Đối với thống kê frequency, đặt `LongAdder` làm value giúp nhiều threads increment trên counter có thiết kế giảm contention.

Nhưng `LongAdder.sum()` không phải snapshot atomic khi updates đang tiếp diễn, và việc map entry bị reset/replace cần ownership/epoch protocol. Không dùng LongAdder cho sequence ID hoặc invariant cần một giá trị CAS xác định nếu API không phù hợp.

### Key phải có equality/hash ổn định

Nếu key là object mutable và `hashCode()/equals()` phụ thuộc những field bị thay đổi sau khi insert, truy vấn có thể không tìm đúng entry và hành vi logical map bị phá, dù collection thread-safe. Ưu tiên immutable keys như String, record bất biến hoặc identifier có equality ổn định.

### Đừng benchmark bằng một lần System.nanoTime

Khi so sánh `merge`, `compute`, `AtomicLong` và `LongAdder`, dùng workload thực và benchmark methodology phù hợp (ví dụ JMH). Một vài microseconds khác biệt trong một lần thử không đủ kết luận. Quan trọng hơn: chỉ so sánh những phương án **đều đúng về invariant**.

## 27. Kiểm thử concurrent updates đáng tin

Test concurrency nên có hai lớp:

### A. Test invariant và interleaving cụ thể

1. **get→put lost update:** buộc hai threads cùng đọc 0 trước khi ghi; kết quả phải cho thấy lý do code sai (demo đã làm).
2. **merge increments:** bốn threads × N increments; sau khi mọi Future hoàn tất, giá trị bằng 4N.
3. **putIfAbsent contest:** N threads cùng cố insert một key; xác minh chỉ một mapping cuối cùng và giá trị thuộc một candidate hợp lệ, không khẳng định thread nào thắng.
4. **Conditional replace race:** T1 và T2 cùng expected old; tại most một thao tác replace sang hai next values khác nhau thành công nếu không có thao tác đưa value về old ở giữa.
5. **computeIfAbsent one-key initialization:** nhiều threads cùng yêu cầu một key absent; tạo một object value non-null ổn định và quan sát factory invocation theo contract.
6. **Null return:** compute/computeIfPresent/merge trả null đúng nhánh sẽ xóa mapping; computeIfAbsent trả null không insert.
7. **Exception:** mapping function throw; xác nhận không có mapping mới do chính lời gọi đó.
8. **Mutable value race:** test sự khác biệt giữa map atomicity và value operations không thread-safe.
9. **Multi-key transfer:** stress reader/writer để thấy per-key updates không làm tổng của hai account bất biến tại mọi thời điểm.
10. **Lifecycle:** không xóa/reuse LongAdder khi threads vẫn giữ reference nếu invariant yêu cầu count không mất.

### B. Stress và instrumentation

- Dùng `CountDownLatch`/`CyclicBarrier` đặt checkpoint, thay vì đoán bằng `Thread.sleep()`.
- Dùng `Future.get(timeout, unit)` để test có deadline và lấy exception của worker.
- Kiểm tra tổng đúng sau khi **tất cả** writers hoàn tất; nếu đọc trước thì bạn có thể quan sát một giá trị trung gian hợp lệ.
- Với contention cao, ghi metrics về throughput/latency, không log mỗi operation làm lệch workload.
- Test keys có hash collisions hợp lệ và key classes có equals/hashCode chuẩn.
- Nếu cần khẳng định algorithm hoàn toàn linearizable, có thể kết hợp event histories/model checking; “stress test chạy được một lần” chưa phải formal proof.

**Không thử “sửa” lost update bằng `Thread.sleep` giữa `get` và `put`.** Nó chỉ làm race dễ xảy ra hơn/chuyển timing; giải pháp là thao tác compound đúng.

## 28. Những lỗi triển khai phổ biến

1. **Dùng get rồi put để tăng counter.** Hai lời gọi thread-safe không tạo atomic increment; dùng `merge`/`compute` hoặc counter atomic.
2. **Dùng containsKey rồi put làm lazy initialization.** Check-then-act race; dùng `putIfAbsent`/`computeIfAbsent`.
3. **Tạo expensive object trước putIfAbsent rồi tưởng factory chạy một lần.** Nhiều threads có thể tạo trùng candidates, chỉ insert một winner.
4. **Đặt I/O dài trong computeIfAbsent.** Mapping function có thể block competing updates; cân nhắc loading protocol riêng.
5. **Mutate map trong remapping function.** Recursive/cross-key update có thể gây lỗi hoặc blocking khó đoán.
6. **Cho rằng computeIfAbsent trả null là lưu null.** Không: mapping không được thiết lập.
7. **Cho rằng computeIfPresent gọi function ngay cả khi absent.** Không: chỉ present mapping.
8. **Cho rằng merge gọi remapping function cả khi absent.** Không: khi absent, dùng value supplied trực tiếp.
9. **Nhầm replace(key,newValue) với replace(key,expected,newValue).** Overload đầu không kiểm tra expected value.
10. **Nhầm remove(key) với remove(key,expectedValue).** Remove chỉ theo key có thể xóa state mới.
11. **Cho rằng value equality nghĩa là đúng generation.** A→B→A có thể tạo stale compare/update; cần version nếu invariant đòi.
12. **Dùng HashMap trong nhiều threads mà không synchronization.** Không có thread safety của CHM.
13. **Dùng Collections.synchronizedMap nhưng không giữ lock cho nhiều operations.** Compound sequence vẫn có race nếu không khóa cả chuỗi.
14. **Dùng LongAdder rồi xóa key trong lúc threads vẫn increment old counter.** Có thể mất logical updates dù LongAdder thread-safe.
15. **Cho rằng LongAdder.sum là snapshot atomic khi concurrent writes.** Không; cần quiescence hoặc cơ chế khác nếu yêu cầu đọc chính xác tại một thời điểm.
16. **Cho rằng compute bảo vệ mutable fields của object được lưu.** Chỉ mapping được coordinated; direct mutation ngoài protocol vẫn race.
17. **Dùng replaceAll để làm transaction đổi mọi keys.** Không có global atomic update.
18. **Dùng size()/isEmpty() làm chốt quyết định không đổi.** Concurrency có thể thay đổi ngay sau snapshot.
19. **Cho rằng iterator tạo snapshot cô lập.** CHM iterator weakly consistent.
20. **Dùng mutable key với hashCode thay đổi sau insert.** Làm key lookup sai.
21. **Đặt network/email side effect trong remapping function.** Operation map không cùng transaction với external side effect.
22. **Bỏ qua exception từ mapper.** Mapping không update, nhưng external side effect trước exception có thể đã xảy ra.
23. **Cho rằng method có chữ atomic nghĩa mọi business state atomic.** Scope của operation theo một mapping, không theo cả workflow.
24. **Đo size trước rồi tin map còn trống lúc put.** Dùng atomic admission method thay check-then-act.
25. **Giả định thứ tự hoàn tất của threads trong merge có ý nghĩa nghiệp vụ.** Với function không giao hoán, thứ tự khác nhau tạo kết quả khác; phải thiết kế ordering nếu cần.
26. **Tăng boxed Integer quá lớn mà không xét overflow/allocations.** Counter có miền và hiệu năng constraints riêng.
27. **Dùng computeIfAbsent làm cache lỗi vĩnh viễn.** Nếu function throw/null, không có mapping; caller sau có thể chạy lại; cần failure/negative cache policy khi cần.
28. **Dùng putIfAbsent cho “execute only once” external effect.** Chỉ mapping insertion là atomic, external effect cần idempotency và recovery.
29. **Đọc get(key) non-null rồi tin nó không thay đổi trong lúc xử lý.** Mapping có thể đổi; dùng conditional replace/compute nếu cần validate khi commit.
30. **Coi ConcurrentHashMap là distributed lock.** Nó là in-process concurrent collection, không cung cấp cross-process mutual exclusion/durability.

## 29. Checklist review code production

Trước khi merge một đoạn code dùng `ConcurrentHashMap`, kiểm tra:

- **Invariant:** một khóa độc lập hay phụ thuộc nhiều keys/resources?
- **Read–modify–write:** có bất kỳ đoạn `get` rồi `put/remove` mà mục đích là atomic không?
- **API:** đã chọn đúng `putIfAbsent`/`replace`/`remove(k,v)`/`compute*`/`merge`?
- **Absent:** function có xử lý current null đúng khi key vắng mặt?
- **Null result:** return null có vô tình xóa mapping hoặc không tạo mapping không?
- **Side effect:** mapping/remapping function có I/O, lock, network, writes ngoài map hoặc recursive update?
- **Duration:** computation trong function có ngắn, bounded và không làm contention nghiêm trọng?
- **Equality:** expected value có dùng equals thích hợp? Cần generation để ngăn ABA không?
- **Value safety:** object mutable trong map có được bảo vệ bằng cơ chế riêng không?
- **LongAdder lifecycle:** có xóa/reset counter trong khi threads vẫn giữ reference không?
- **Visibility:** state được chuẩn bị trước publication có immutable/ownership protocol đúng?
- **Capacity:** số keys có thể tăng không giới hạn và gây memory pressure không?
- **Metrics:** hot keys, collision, latency, memory, mapper failures và throughput có được theo dõi?
- **Testing:** có test interleaving bằng latch và test kết quả sau Future.get? Có stress cùng key và nhiều keys?
- **Failure semantics:** nếu operation throw sau side effect trước đó, recovery/idempotency ra sao?
- **Compatibility:** API có sẵn trong JDK triển khai (ví dụ Java 17) không?

## 30. Câu hỏi tự kiểm tra và đáp án

**Câu 1.** `ConcurrentHashMap.get` và `put` đều thread-safe. Tại sao `put(k, get(k)+1)` vẫn có thể mất cập nhật?  
**Đáp án:** Vì hai lời gọi tách biệt. Hai threads có thể cùng đọc giá trị cũ rồi cùng ghi một giá trị mới; không có atomic read–modify–write.

**Câu 2.** Muốn tăng counter int cho một key, lời gọi đơn giản là gì?  
**Đáp án:** `map.merge(key, 1, Integer::sum)`, với miền int không overflow và không cần multi-key transaction.

**Câu 3.** Hai threads đều gọi `putIfAbsent(k, newValue)`, chuyện gì được bảo đảm?  
**Đáp án:** Một mapping chỉ được thêm theo điều kiện absent; nếu đã present, call sau không ghi đè mà trả giá trị hiện tại theo contract.

**Câu 4.** Tại sao `putIfAbsent(key, loadExpensive())` có thể tính toán trùng?  
**Đáp án:** Biểu thức argument `loadExpensive()` chạy **trước** lời gọi putIfAbsent ở mỗi thread; method chỉ bảo vệ insertion.

**Câu 5.** `computeIfAbsent` function trả null thì key có được lưu với null value không?  
**Đáp án:** Không. ConcurrentHashMap không cho phép null value; mapping không được thêm.

**Câu 6.** `computeIfPresent` function trả null khi key đã tồn tại có nghĩa gì?  
**Đáp án:** Yêu cầu xóa mapping của key đó như phần của compound update.

**Câu 7.** `merge` khi key absent có gọi remapping function không?  
**Đáp án:** Không. Nó đặt supplied non-null value trực tiếp.

**Câu 8.** `replace(k, old, new)` so old bằng identity hay equality?  
**Đáp án:** Theo value equality của map (equals), không phải CAS reference identity.

**Câu 9.** `remove(k, v)` khác `remove(k)` thế nào?  
**Đáp án:** Nó chỉ xóa nếu mapping hiện tại khớp expected value; giúp tránh xóa một state đã bị thread khác thay đổi.

**Câu 10.** Một `compute` trên A và `compute` trên B có cùng tạo transaction atomic hai khóa không?  
**Đáp án:** Không. Mỗi compound update là atomic theo khóa, không phải một commit chung.

**Câu 11.** Vì sao remapping function không nên cập nhật chính map đệ quy?  
**Đáp án:** Có thể vi phạm contract, tạo blocking/non-termination hoặc `IllegalStateException` khi recursive update bị phát hiện.

**Câu 12.** Một remapping function gọi API network rồi trả value có thể gây vấn đề gì?  
**Đáp án:** Làm competing updates chờ lâu, có timeout/partial side effect không nằm trong transaction của map, và gây retry/consistency phức tạp.

**Câu 13.** Mẫu `computeIfAbsent(k, ignored -> new LongAdder()).increment()` có hai nguồn thread safety nào?  
**Đáp án:** CHM phối hợp initialization của mapping; LongAdder bảo vệ increment trên counter object.

**Câu 14.** Nếu thread khác remove key rồi tạo LongAdder mới, thread đã giữ old LongAdder có thể tiếp tục increment không?  
**Đáp án:** Có. Nó tăng object cũ không còn được map trỏ tới, nên logical count theo key có thể bị mất.

**Câu 15.** `LongAdder.sum()` là atomic snapshot hoàn hảo khi writers vẫn đang increment không?  
**Đáp án:** Không. Nó là phép lấy tổng không phải snapshot atomic trong lúc update concurrent.

**Câu 16.** `ConcurrentHashMap.size() == 0` chứng minh map sẽ tiếp tục rỗng tới sau lời gọi tiếp theo không?  
**Đáp án:** Không. Snapshot/estimate có thể thay đổi ngay; cần atomic operation/giao thức khóa khác.

**Câu 17.** Iterator của ConcurrentHashMap có snapshot nhất quán theo một thời điểm chung không?  
**Đáp án:** Không. Nó weakly consistent và có thể phản ánh một số thay đổi concurrent.

**Câu 18.** `computeIfAbsent` có phải đảm bảo external API chỉ được gọi một lần vĩnh viễn cho key không?  
**Đáp án:** Không. Mapping có thể bị remove, computation có thể fail/null, các invocations sau có thể thử lại, và external side effects không có exactly-once guarantee.

**Câu 19.** Hai value objects bằng nhau theo equals nhưng thuộc hai generation khác nhau. `replace(k, old, new)` có phát hiện generation không?  
**Đáp án:** Không nếu equality không chứa generation. Cần version field/immutable state nếu invariant phải phân biệt thế hệ.

**Câu 20.** Vì sao test demo dùng latch cho `get/put` nhưng Future.get cho `merge`?  
**Đáp án:** Latch buộc hai threads cùng đọc giá trị cũ để chứng minh lost update một cách xác định; Future.get chờ tất cả updates atomic kết thúc trước khi xác minh 4.000.

## 31. Bài tập thực hành nâng cao

### Bài tập A — khởi tạo một cache entry

Cho 20 threads yêu cầu cùng một key absent. Viết hai phiên bản:

- `putIfAbsent(k, expensiveFactory())` và đo số lần factory chạy.
- `computeIfAbsent(k, k2 -> cheapFactory())` và đo số lần function chạy khi mapping được giữ ổn định.

Giải thích vì sao factory có side effect external là bài toán khác với initialization một object trong map. Hãy dùng `CountDownLatch`/`Future.get(timeout)` để phối hợp test, không dựa vào sleep.

### Bài tập B — state transition

Tạo `record JobState(long generation, String status)`. Nhiều threads cố chuyển `NEW→CLAIMED` bằng `replace(k, expected, next)`. Chứng minh chỉ một thread có thể thắng cho cùng expected state khi không có phép đưa state quay lại expected. Sau đó thử mô phỏng ABA và bổ sung generation.

### Bài tập C — word frequency

Tạo frequency map từ một tập từ, các threads chia input, dùng `computeIfAbsent(word, k -> new LongAdder()).increment()`. Chờ tất cả Futures rồi xác minh tổng số từ. Thử đọc `sum()` trong khi writers còn chạy và giải thích vì sao snapshot chưa chắc linearizable.

### Bài tập D — cleanup có điều kiện

Dùng `computeIfPresent` để xóa entry nếu record hết hạn. Test một thread cập nhật bản ghi mới đúng lúc thread khác muốn cleanup, đảm bảo bản ghi mới không bị xóa do `get+remove` stale check.

### Bài tập E — nhiều khóa

Mô phỏng chuyển điểm A→B và một reader đọc tổng A+B liên tục. Chứng minh hai `compute` tách biệt không cung cấp atomic snapshot hai khóa. Thiết kế lại bằng một immutable aggregate state hoặc lock chung, rồi test invariant.

## 32. Tài liệu tham khảo chính thức

- [Java SE 17 — ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): đặc tả atomic `computeIfAbsent`, `computeIfPresent`, `compute`, `merge`, bulk ops, null, mappingCount, weakly consistent views và `LongAdder` frequency map.
- [Java SE 17 — ConcurrentMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html): semantics của `putIfAbsent`, conditional remove/replace và interface contract.
- [Java SE 17 — Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html): bản đồ key/value, equality và các default methods.
- [Java SE 17 — LongAdder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html): increment/sum và giới hạn snapshot khi concurrent updates.
- [Java SE 17 — AtomicLong](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicLong.html): atomic counter và CAS.
- [Java SE 17 — CountDownLatch](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): coordination tạo interleaving trong test và memory consistency.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): submit/shutdown và lifecycle.
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): completion, exception và memory effects khi `get`.
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): happens-before, data races và memory ordering.

**Kết luận:** `ConcurrentHashMap` không làm `get → kiểm tra → put` tự nguyên tử. Hãy dùng **compound operation đúng theo invariant và theo khóa**: `putIfAbsent` để insert-if-missing, `replace/remove` có expected value cho compare-and-update, `compute*` để remap theo current, và `merge` để cộng/aggregate. Hàm remapping phải ngắn, không recursive update và không đặt side effect không idempotent. Nếu state phụ thuộc nhiều khóa, mutable referents hoặc external systems, phải thiết kế đồng bộ/transaction/lifecycle bổ sung — thread safety của map **không thay thế** correctness của toàn bộ workflow.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-004">Weakly consistent iteration và snapshot semantics</a>

<details>
<summary>Xem chi tiết</summary>

# Weakly consistent iteration và snapshot semantics trong Java Concurrent Collections

## 1. Khi collection bị thay đổi trong lúc đang duyệt

Một collection không chỉ được dùng để lưu dữ liệu; rất nhiều xử lý đồng thời phải **duyệt** dữ liệu: gửi thông báo cho người đang kết nối, thu thập metric, quét cache, tìm task chờ xử lý, hoặc tạo báo cáo. Khi một thread duyệt, thread khác có thể `add`, `remove` hoặc `put`.

Ba câu hỏi cần phân biệt:

1. Iterator có **ném lỗi** khi collection bị thay đổi hay không?
2. Iterator có **thấy các thay đổi** xảy ra sau khi được tạo hay không?
3. Các phần tử iterator thấy có tạo thành **một trạng thái nhất quán tại cùng một thời điểm** hay không?

Không thể suy từ câu trả lời thứ nhất rằng câu trả lời thứ ba cũng là “có”. Một iterator không ném `ConcurrentModificationException` **không đồng nghĩa** nó cung cấp một snapshot nguyên tử.

Trong Java, các cơ chế cần hiểu bao gồm:

- **Fail-fast iterator**: nhiều collection thông thường, như `ArrayList` và `HashMap`, có iterator cố gắng phát hiện sửa đổi cấu trúc không phù hợp khi đang duyệt.
- **Weakly consistent iterator**: nhiều concurrent collection, như `ConcurrentHashMap`, `ConcurrentLinkedQueue` và `ConcurrentSkipListMap`, cho phép duyệt đồng thời với cập nhật, có thể phản ánh một phần thay đổi và không hứa snapshot tại một thời điểm.
- **Snapshot iterator**: `CopyOnWriteArrayList` và `CopyOnWriteArraySet` dùng một ảnh chụp cấu trúc tại **thời điểm iterator được tạo**, nên các cập nhật cấu trúc sau đó không xuất hiện trong lượt duyệt ấy.
- **Explicit snapshot**: chương trình chủ động tạo bản sao có bảo đảm phù hợp với bất biến của dữ liệu. Chỉ copy một concurrent collection **không tự động** làm bản sao thể hiện một thời điểm nguyên tử của toàn collection.

Bài tập trung vào **Java concurrency và Concurrent Collections nói chung**, không đi vào framework.

### Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích chính xác fail-fast, weak consistency và snapshot semantics.
2. Biết vì sao `ConcurrentHashMap` duyệt được khi có update nhưng không bảo đảm nhìn mọi cập nhật.
3. Dùng `CopyOnWriteArrayList` khi khối lượng đọc/duyệt nhiều hơn cập nhật, hiểu chi phí copy-on-write và giới hạn mutable elements.
4. Phân biệt snapshot có cấu trúc ổn định với snapshot **nhất quán nhiều trường/nhiều khóa**.
5. Dùng khóa chung, immutable state hoặc protocol versioning khi báo cáo cần nhìn trạng thái tổng thể nhất quán.
6. Thiết kế test có output xác định thay vì giả định thứ tự scheduling hoặc thứ tự duyệt concurrent.
7. Chọn collection dựa trên workload, độ chính xác, performance và semantics của dữ liệu.

## 2. Bảng so sánh nhanh

| Cơ chế | Ví dụ Java | Sửa đổi đồng thời khi duyệt | Có thể thấy update mới? | Một snapshot cấu trúc cố định? |
| --- | --- | --- | --- | --- |
| Fail-fast | `ArrayList`, `HashMap` | Không được hỗ trợ tự do theo iterator thông thường; có thể ném `ConcurrentModificationException` | Không có contract duyệt đồng thời ổn định | Không |
| Weakly consistent | `ConcurrentHashMap` | Được thiết kế để duyệt cùng lúc với update | **Có thể**, không bảo đảm thấy tất cả | **Không** |
| Weakly consistent | `ConcurrentLinkedQueue` | Được hỗ trợ | Có thể | Không |
| Weakly consistent, có thứ tự khóa | `ConcurrentSkipListMap` | Được hỗ trợ | Có thể | Không |
| Snapshot iterator | `CopyOnWriteArrayList` | Update cấu trúc sau khi tạo iterator không làm thay đổi lượt duyệt | Không, đối với cấu trúc array đã chụp | **Có**, snapshot của array |
| Snapshot iterator | `CopyOnWriteArraySet` | Tương tự copy-on-write list | Không, đối với cấu trúc tập đã chụp | **Có**, snapshot cấu trúc |
| External synchronization | `Collections.synchronizedList` và lock chung | Phải tự giữ monitor khi duyệt | Không thay đổi trong lúc giữ lock nếu mọi writer tuân thủ | Có thể dựng snapshot dưới lock |
| Immutable copy | `List.copyOf` / `Map.copyOf` | Bản sao không cho cập nhật cấu trúc | Không thay đổi cấu trúc sau khi tạo | Có, **nếu nguồn được chụp nhất quán** |

Ở đây, “snapshot cấu trúc” không có nghĩa object phần tử bên trong bất biến. Ví dụ list đã copy chứa một `Box` mutable thì `Box.value` vẫn có thể thay đổi dù iterator vẫn chỉ trỏ tới cùng các `Box` ban đầu.

**Điểm quyết định:** chọn loại iterator theo câu hỏi nghiệp vụ cần trả lời, không đơn giản theo mong muốn “không ném exception”.

## 3. Fail-fast iterator: phát hiện lỗi, không phải giao thức đồng bộ

Nhiều collection không concurrent ghi nhận một bộ đếm thay đổi cấu trúc. Iterator có thể so sánh trạng thái mong đợi với trạng thái thực tế và ném `ConcurrentModificationException` khi phát hiện thay đổi không phù hợp.

Ví dụ rủi ro:

~~~java
// Minh họa không an toàn; không phải chương trình độc lập.
List<String> list = new ArrayList<>(List.of("A", "B"));
for (String item : list) {
    if (item.equals("A")) {
        list.add("C");  // Sửa cấu trúc list ngoài Iterator.remove().
    }
}
~~~

Với `ArrayList` thường gặp, điều này có thể gây `ConcurrentModificationException` tại thao tác iterator kế tiếp. Tuy nhiên **Javadoc nhấn mạnh fail-fast là best effort**: không thể bảo đảm mọi trường hợp race đều phát hiện được. Vì thế **đừng dùng exception đó như cơ chế correctness hoặc tín hiệu đồng bộ**.

### 3.1. “Không ném CME” không chứng minh an toàn

Nếu chạy code sai trên `ArrayList` nhiều lần mà không thấy `ConcurrentModificationException`, chương trình vẫn có thể có data race, quan sát dữ liệu không nhất quán hoặc gặp kết quả không xác định. Ngược lại, `ConcurrentHashMap` không ném CME khi duyệt đồng thời **vì nó có hợp đồng iterator khác**, chứ không phải vì biến mọi thao tác nhiều phần tử thành nguyên tử.

Một tập hợp các thao tác `get`/`put` trên map thread-safe cũng chưa đủ để bảo vệ invariant của nhiều key. “Thread-safe collection” không đồng nghĩa “thread-safe workflow”.

### 3.2. Cập nhật thông qua iterator

Với một số iterator của collection thông thường, `Iterator.remove()` là cách hợp lệ để xóa phần tử hiện tại. Nhưng quyền hỗ trợ `remove()` phụ thuộc implementation. Ví dụ snapshot iterator của `CopyOnWriteArrayList` **không hỗ trợ** `remove()`, `set()` hay `add()` và sẽ ném `UnsupportedOperationException`.

Không áp dụng một mẫu xóa iterator chung cho mọi collection; hãy xem contract của loại iterator thực sự đang dùng.

## 4. Weakly consistent iteration là gì?

Iterator weakly consistent cho phép tiến triển khi collection có update đồng thời mà không có cùng fail-fast behavior như `ArrayList`. Nó thường:

- Duyệt được cùng lúc với thread khác thêm, xóa hoặc thay giá trị.
- Không ném `ConcurrentModificationException` chỉ vì collection được thay đổi đồng thời.
- Có thể thấy một số trạng thái từ lúc iterator được tạo **và** một số thay đổi sau đó.
- Không hứa thấy toàn bộ thay đổi, hoặc rằng mọi phần tử được trả ra thuộc cùng một trạng thái tại một thời điểm.
- Không tự khóa cả collection để cung cấp point-in-time snapshot.

Từ **weakly** nói về bảo đảm quan sát khi duyệt, **không** đồng nghĩa bộ lưu trữ underlying “không thread-safe”. `ConcurrentHashMap.get/put/compute` có các hợp đồng thread-safety và atomicity riêng cho thao tác cụ thể. Chỉ iterator toàn map là không cho một giao dịch đọc nhiều key nhất quán tại một điểm thời gian.

### 4.1. Một lịch xen kẽ có thể khiến kết quả trộn thời điểm

~~~text
Thời điểm       Thread reader                      Thread writer
t0              Tạo iterator trên map
t1              Đọc key A và value cũ
t2                                                 Thay A, thêm C, xóa B
t3              Đọc một số entry khác
t4              Iterator hoàn thành
~~~

Kết quả của reader có thể chứa một phần value cũ và một phần value mới, có thể thấy C hoặc không. Không được diễn giải nó như “trạng thái map ở đúng t0” hoặc “trạng thái map ở đúng t4”.

Để audit/report yêu cầu một thời điểm cắt rõ ràng, cần snapshot protocol mạnh hơn. Để quan sát gần đúng danh sách kết nối hay metric, weak iteration có thể rất phù hợp.

## 5. ConcurrentHashMap: các thao tác atomic không biến iterator thành snapshot

`ConcurrentHashMap<K,V>` được thiết kế để nhiều thread cập nhật và đọc map đồng thời. `keySet()`, `values()`, `entrySet()` cung cấp các view có iterator **weakly consistent**. Iterator và spliterator không ném CME khi map thay đổi đồng thời.

### 5.1. Không có một khóa toàn map mà iterator giữ suốt thời gian duyệt

Một iterator có thể lần lượt quan sát nhiều bucket/entry ở những thời điểm khác nhau. Vì thế:

~~~java
// Minh họa trong method, không phải chương trình độc lập.
for (Map.Entry<String, Integer> e : concurrentMap.entrySet()) {
    audit(e.getKey(), e.getValue());
}
~~~

Đoạn này có thể phù hợp để **đọc gần đúng** hoặc làm công việc có thể chịu cập nhật đồng thời. Nó không phù hợp nếu audit bắt buộc chính xác 100% tại một timestamp chung, ví dụ tổng tài sản nhiều tài khoản phải bằng bất biến.

### 5.2. `size()`, `mappingCount()` và aggregate

Khi có update concurrent, các phép tổng hợp như `size()`, `mappingCount()`, `isEmpty()` hay các bulk reductions không nên được coi là snapshot giao dịch toàn map. `mappingCount()` trả `long` để có khả năng biểu diễn kích thước vượt giới hạn `int`, nhưng vẫn có thể chỉ là **ước lượng khi đang sửa đồng thời**.

Không viết workflow:

~~~java
// Sai nếu coi size và nội dung là một transaction.
int expected = map.size();
for (var e : map.entrySet()) {
    process(e);
}
assert processedExactly(expected);
~~~

Map có thể thay đổi giữa lời gọi `size()` và lần đọc từng entry. Số phần tử nhìn thấy cũng có thể thay đổi khi đang duyệt.

### 5.3. `compute`/`merge` chỉ giúp atomic ở đúng phạm vi

`ConcurrentHashMap.compute(key, ...)` hay `merge` có bảo đảm atomic cho cập nhật mapping của **key tương ứng** theo contract. Một chuỗi thay đổi `keyA` rồi `keyB` vẫn có thể bị reader chen vào giữa. Không suy luận tính nguyên tử tổng thể từ việc từng thao tác riêng là thread-safe.

### 5.4. Không có thứ tự iteration giống insertion order

Không dùng `ConcurrentHashMap` như `LinkedHashMap` để suy ra thứ tự insertion. Ngay cả khi iterator chạy không có writer, thứ tự duyệt không phải hợp đồng “theo thời gian put”; khi có writer, càng không dùng thứ tự đầu ra làm lịch sử giao dịch.

Nếu cần thứ tự key, cân nhắc `ConcurrentSkipListMap` với các semantics riêng; nhưng **sort order vẫn không đồng nghĩa point-in-time snapshot**.

## 6. Ví dụ 1 — weak iterator chấp nhận cập nhật (Java 17+)

Để tránh đoán “iterator có thấy phần tử vừa put hay không”, ví dụ chỉ in những điều có thể kiểm tra chắc chắn. Ta tạo iterator, đọc một phần tử, cập nhật map ở một thread khác, rồi hoàn tất lượt duyệt.

Tạo tệp `WeakIteratorDemo.java`:

~~~java
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WeakIteratorDemo {
    public static void main(String[] args)
            throws InterruptedException {
        ConcurrentHashMap<Integer, String> map =
            new ConcurrentHashMap<>();

        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        Iterator<Map.Entry<Integer, String>> iterator =
            map.entrySet().iterator();

        if (iterator.hasNext()) {
            iterator.next();
        }

        Thread writer = new Thread(() -> {
            map.put(4, "four");
            map.remove(2);
        }, "map-writer");

        writer.start();
        writer.join();

        int visitedAfterUpdate = 0;
        while (iterator.hasNext()) {
            iterator.next();
            visitedAfterUpdate++;
        }

        System.out.println("iteration completed = true");
        System.out.println("map has key 4 = " + map.containsKey(4));
        System.out.println("map has key 2 = " + map.containsKey(2));

        // Không in visitedAfterUpdate thành một hằng số:
        // weak iterator có thể thấy một tập entry tùy thời điểm.
        if (visitedAfterUpdate < 0) {
            throw new AssertionError("Impossible count");
        }
    }
}
~~~

Biên dịch/chạy bằng JDK 17+:

~~~bash
javac WeakIteratorDemo.java
java WeakIteratorDemo
~~~

Stdout:

~~~text
iteration completed = true
map has key 4 = true
map has key 2 = false
~~~

Ở đây writer hoàn tất trước khi main đọc hai điều kiện `containsKey`, nên trạng thái cuối của map rõ ràng. Nhưng **tập entry mà iterator đã thấy** không được cố định trong output: không nên khẳng định nó chắc chắn đã thấy key 4 hoặc không thấy key 2.

Chương trình không bắt `ConcurrentModificationException` vì không cần “dập lỗi”; chính iterator của `ConcurrentHashMap` được thiết kế cho mẫu duyệt concurrent. Lưu ý ví dụ sắp đặt thay đổi sau khi iterator được tạo và trước khi lượt duyệt kết thúc; nó không chứng minh một lịch thực thi duy nhất mà mọi JVM phải chọn.

### Khi nào weak iteration là lựa chọn tốt?

- Quét session/cache phục vụ **tác vụ best-effort**; một session vừa tham gia có thể được xử lý trong lượt sau.
- Thu thập metric xấp xỉ, dashboard không cần một thời điểm chốt sổ.
- Quét để dọn entry cũ với điều kiện xóa atomic phù hợp, ví dụ `remove(key, expectedValue)` khi cần tránh xóa nhầm entry đã được thay thế.
- Danh sách tài nguyên thay đổi thường xuyên, miễn tính đúng đắn của mỗi hành động không dựa vào việc lượt duyệt nhìn **đủ mọi entry**.

**Không phù hợp** nếu một lần duyệt phải duy nhất/đủ mọi giao dịch tính đến cutoff, hoặc kết quả là nguồn xác định chính thức cho đối soát.

## 7. ConcurrentLinkedQueue và ConcurrentSkipListMap

### 7.1. ConcurrentLinkedQueue

`ConcurrentLinkedQueue<E>` là queue concurrent không chặn, triển khai dựa trên các thao tác nguyên tử thích hợp. Iterator của nó **weakly consistent**: có thể cùng tồn tại với `offer`/`poll` của thread khác và không ném CME chỉ vì queue được thay đổi.

Nhưng một lượt duyệt queue không phải phép “lấy từng phần tử để xử lý đúng một lần”. Nếu muốn một worker **nhận quyền sở hữu** một công việc, nên dùng `poll()`/`remove()` hoặc `BlockingQueue.take()` theo mô hình queue, không chỉ for-each rồi giả định task đã được claim.

`size()` của `ConcurrentLinkedQueue` có thể phải duyệt phần tử và kết quả có thể không chính xác nếu queue bị thay đổi khi đếm. Không dùng `size()` của queue đó như reservation counter hay phương pháp backpressure chính xác.

### 7.2. ConcurrentSkipListMap

`ConcurrentSkipListMap` cung cấp map concurrent có **sorted key order** theo comparator/natural order. Iterator của nó cũng weakly consistent, không tạo snapshot nguyên tử của tất cả key/value.

Có key order không có nghĩa có **time order** của update. Khi tập key thay đổi trong lúc quét, tính đúng đắn vẫn phải được xác định theo contract của thao tác riêng. Muốn audit theo mốc thời gian, bạn cần transaction/snapshot/versioning, không chỉ một sorted concurrent map.

### 7.3. Weakly consistent không phải tính ngữ nghĩa giống nhau cho mọi class

Đừng áp dụng “có thể thấy một số cập nhật” thành lời cam kết cụ thể rằng iterator nào cũng phải thấy ít nhất một cập nhật sau khi tạo. Mỗi collection có chi tiết traversal, ordering, removal hỗ trợ và race contract riêng.

Đọc Javadoc chính xác của collection cụ thể. Chúng có thể cùng được xếp vào nhóm “weakly consistent” nhưng khác nhau về thứ tự key, hỗ trợ null, chi phí đếm hoặc chức năng remove.

## 8. Snapshot iterator của CopyOnWriteArrayList

`CopyOnWriteArrayList<E>` giữ các phần tử trong một array, cập nhật cấu trúc bằng cách **tạo/công bố array mới**. Khi tạo iterator, nó gắn với **array tại thời điểm iterator được tạo**.

Nếu thread khác thêm, xóa hoặc thay một vị trí trong list sau đó, iterator cũ vẫn đi trên array mà nó đã lấy lúc đầu. Nó không ném CME do thay đổi cấu trúc đồng thời, nhưng **khác weakly consistent iterator**: nó không quan sát các cập nhật cấu trúc về sau trong lượt duyệt đó.

### 8.1. Ví dụ 2 — snapshot của iterator ổn định (Java 17+)

Tạo tệp `CopyOnWriteSnapshotDemo.java`:

~~~java
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CopyOnWriteSnapshotDemo {
    public static void main(String[] args) {
        CopyOnWriteArrayList<String> list =
            new CopyOnWriteArrayList<>(List.of("A", "B"));

        Iterator<String> before = list.iterator();

        list.add("C");
        list.set(0, "X");

        System.out.print("iterator snapshot = [");
        boolean first = true;
        while (before.hasNext()) {
            if (!first) {
                System.out.print(", ");
            }
            System.out.print(before.next());
            first = false;
        }
        System.out.println("]");
        System.out.println("current list = " + list);

        try {
            list.iterator().remove();
        } catch (UnsupportedOperationException expected) {
            System.out.println("iterator remove unsupported");
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac CopyOnWriteSnapshotDemo.java
java CopyOnWriteSnapshotDemo
~~~

Stdout:

~~~text
iterator snapshot = [A, B]
current list = [X, B, C]
iterator remove unsupported
~~~

**Tại sao?**

1. Iterator `before` chụp tham chiếu tới array chứa A, B tại thời điểm được tạo.
2. `add("C")` và `set(0, "X")` tạo/công bố cấu trúc list mới; iterator `before` vẫn dùng array cũ.
3. Lời gọi `iterator().remove()` không được hỗ trợ, vì iterator snapshot không phải một view chỉnh sửa cấu trúc hiện tại.
4. Không có thread phụ nên stdout xác định. Khi có nhiều thread, thuộc tính snapshot của iterator vẫn áp dụng, nhưng thời điểm tạo iterator so với update do scheduler quyết định.

**Lưu ý:** `set(0, ...)` trong `CopyOnWriteArrayList` vẫn là một thao tác cập nhật list theo cơ chế của implementation; snapshot của iterator cũ không tự thay đổi.

### 8.2. Vì sao CopyOnWriteArrayList thích hợp khi nhiều đọc, ít ghi?

- Đọc/duyệt có thể chạy trên array snapshot mà không phải giữ lock của writer trong suốt lượt duyệt.
- Lượt duyệt phản ánh một cấu trúc ổn định, dễ suy luận cho danh sách listener/configuration bất biến về mặt membership.
- Thêm/xóa/sửa cấu trúc thường đòi **copy array**, làm tăng allocations và chi phí theo kích thước list.
- Nhiều cập nhật trong lúc list lớn tạo write amplification, áp lực GC và tốn bộ nhớ tạm cho những iterator cũ còn giữ array cũ.
- Không phù hợp khi mutation cực nhiều hoặc dữ liệu quá lớn mà phải copy liên tục.
- Iterator snapshot không nhất thiết phản ánh listener vừa thêm ngay tức thì; thiết kế cần chấp nhận độ trễ nhìn thấy ở các lượt duyệt tiếp theo.

### 8.3. Snapshot iterator không cho deep immutability

Một array snapshot có thể giữ các **tham chiếu tới object mutable**. Những object được trỏ tới có thể tiếp tục bị sửa thông qua tham chiếu khác. Vì vậy snapshot ổn định về **membership và vị trí reference**, không tự đóng băng trạng thái bên trong từng phần tử.

Đây là khác biệt quan trọng khi một list chứa `Account` hoặc `Session` mutable. Lượt duyệt có thể thấy cùng tập account, nhưng số dư/thuộc tính bên trong mỗi account có thể thay đổi giữa các lần đọc.

## 9. Ví dụ 3 — immutable structural copy vẫn shallow (Java 17+)

Tạo tệp `ShallowSnapshotDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.List;

public class ShallowSnapshotDemo {
    static final class Box {
        int value;

        Box(int value) {
            this.value = value;
        }
    }

    public static void main(String[] args) {
        Box box = new Box(1);
        List<Box> original = new ArrayList<>();
        original.add(box);

        List<Box> snapshot = List.copyOf(original);

        original.add(new Box(2));
        box.value = 9;

        System.out.println(
            "snapshot size = " + snapshot.size()
        );
        System.out.println(
            "snapshot first value = " + snapshot.get(0).value
        );
        System.out.println(
            "original size = " + original.size()
        );
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac ShallowSnapshotDemo.java
java ShallowSnapshotDemo
~~~

Stdout:

~~~text
snapshot size = 1
snapshot first value = 9
original size = 2
~~~

`List.copyOf(original)` tạo một list unmodifiable có **cấu trúc độc lập** với những thay đổi add/remove sau này của `original`. Nhưng cả hai list vẫn tham chiếu cùng `Box` ban đầu; khi `box.value` đổi từ 1 sang 9, snapshot đọc 9.

Để có **deep snapshot** khi cần, chuyển phần tử sang một value type bất biến (record chứa các field primitive/immutable) hoặc sao chép sâu theo đúng ownership. Cần tránh chỉ copy lớp ngoài và tuyên bố toàn bộ object graph đã bất biến.

### 9.1. `List.copyOf` và null

`List.copyOf` không chấp nhận phần tử `null`. Nếu nguồn có null, nó ném `NullPointerException`. Không thay `new ArrayList<>(source)` bằng `List.copyOf(source)` trong một hệ thống cho phép null mà không kiểm tra contract.

### 9.2. `Map.copyOf` / `Set.copyOf`

`Map.copyOf` và `Set.copyOf` cũng tạo collection unmodifiable, có yêu cầu riêng về null. Nhưng **chỉ vì copy đã xong và kết quả không sửa được không có nghĩa dữ liệu nguồn được đọc trong một transaction nguyên tử** nếu writer vẫn thay đổi nguồn trong lúc copy.

## 10. Sự khác nhau giữa snapshot copy và một snapshot nhất quán tại một thời điểm

Có ít nhất ba mức độ người ta cùng gọi là “snapshot”:

1. **Snapshot về membership/reference**: iterator duyệt tập reference ổn định, như `CopyOnWriteArrayList.iterator()`.
2. **Snapshot bản sao cấu trúc**: có một `List`/`Map` mới không thay đổi khi nguồn add/remove sau này, nếu quá trình copy đã thành công.
3. **Snapshot nhất quán về bất biến**: tất cả field/entry liên quan cùng mô tả một trạng thái nghiệp vụ hợp lệ tại **một thời điểm logic**; không trộn phần cũ và phần mới.

Mức 1 và mức 2 **không tự động suy ra mức 3**.

### 10.1. Vì sao `new HashMap<>(concurrentMap)` có thể chưa đủ?

`ConcurrentHashMap` cho phép các thao tác lấy từng entry khi writer còn hoạt động. Constructor copy có thể đi qua nguồn bằng traversal mà không giữ giao dịch đọc toàn map. Bản sao kết quả có cấu trúc độc lập, nhưng có thể chứa một tập entry được quan sát ở nhiều thời điểm khác nhau.

Một snapshot **bất biến sau khi tạo** vẫn có thể đã **không nhất quán tại thời điểm tạo**. Hai thuộc tính này khác nhau.

### 10.2. CopyOnWriteArrayList có thể snapshot cấu trúc mà vẫn có dữ liệu mutable

Nếu list lưu các record bất biến chứa toàn bộ dữ liệu cần báo cáo, snapshot iterator rất mạnh về membership. Nhưng nếu list lưu mutable object tham chiếu ngoài, bản thân những object đó có thể đổi. Một ảnh chụp các con trỏ không đồng nghĩa ảnh chụp toàn bộ giá trị sâu.

## 11. Ví dụ 4 — đọc hai key ConcurrentHashMap tạo kết quả không có ở bất kỳ thời điểm nào (Java 17+)

Ví dụ này **ép một interleaving có kiểm soát** bằng latch để chứng minh sự khác biệt giữa atomic operation theo key và atomic snapshot nhiều key. Không phụ thuộc timing hay scheduler may rủi.

Ban đầu A=60, B=40, tổng =100. Writer đổi A thành 40 rồi đổi B thành 60. Reader cố tình đọc A **trước** hai update và đọc B **sau** hai update:

~~~text
Ban đầu       A = 60, B = 40, tổng = 100
Reader        đọc A = 60
Writer        ghi A = 40     -> tổng thực tế tạm thời = 80
Writer        ghi B = 60     -> tổng thực tế = 100
Reader        đọc B = 60
Reader        ghép A+B = 120 (không phải trạng thái map tại bất kỳ mốc trên)
~~~

Tạo tệp `NonAtomicSnapshotDemo.java`:

~~~java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class NonAtomicSnapshotDemo {
    public static void main(String[] args)
            throws InterruptedException {
        ConcurrentHashMap<String, Integer> map =
            new ConcurrentHashMap<>();
        map.put("A", 60);
        map.put("B", 40);

        CountDownLatch firstRead = new CountDownLatch(1);
        CountDownLatch writerDone = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            try {
                firstRead.await();
                map.put("A", 40);
                map.put("B", 60);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                writerDone.countDown();
            }
        }, "balance-writer");

        writer.start();

        int oldA = map.get("A");
        firstRead.countDown();
        writerDone.await();
        writer.join();

        int newB = map.get("B");

        System.out.println("mixed total = " + (oldA + newB));
        System.out.println(
            "final total = " + (map.get("A") + map.get("B"))
        );
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac NonAtomicSnapshotDemo.java
java NonAtomicSnapshotDemo
~~~

Stdout khi không có interrupt ngoại lai:

~~~text
mixed total = 120
final total = 100
~~~

Kết quả không có nghĩa `ConcurrentHashMap` hỏng thread-safety. Mọi `get`/`put` riêng lẻ tuân theo contract, nhưng **một chuỗi nhiều lời gọi get không phải một transaction**. Nếu thay hai `get` bằng quá trình copy map trong lúc có writer, không có bảo đảm snapshot toàn map sẽ là trạng thái nguyên tử nhất quán.

Đây là bài học về **rủi ro của aggregate read**. Dữ liệu nghiệp vụ yêu cầu tổng luôn bằng 100 còn cần **atomic update của cặp số dư** ở phía writer; ở ví dụ writer thực hiện hai `put` riêng nên trong khoảng giữa cũng có một trạng thái tổng tạm thời 80. Cả read protocol lẫn write protocol đều cần thiết kế nếu phải giữ invariant xuyên nhiều key.

## 12. Tạo snapshot nhất quán bằng cùng một khóa

Khi mọi writer và reader tuân theo một **khóa chung**, một thao tác tạo copy có thể chạy trong cùng critical section mà không bị writer can thiệp. Đó là khác biệt so với việc chỉ gọi `List.copyOf` không khóa trên nguồn đang thay đổi.

### 12.1. Synchronized collection vẫn cần khóa khi duyệt

`Collections.synchronizedList(...)` biến các method riêng lẻ thành synchronized theo wrapper. Nhưng khi thực hiện phép duyệt nhiều bước, **caller phải đồng bộ hóa thủ công trên wrapper đó**:

~~~java
// Mẫu Java 17+, không phải chương trình độc lập.
List<String> shared =
    Collections.synchronizedList(new ArrayList<>());

List<String> snapshot;
synchronized (shared) {
    snapshot = List.copyOf(shared);
}

// Dùng snapshot ngoài khóa.
for (String item : snapshot) {
    use(item);
}
~~~

Mã cần import `java.util.List`, `java.util.Collections` và `java.util.ArrayList`. Điều kiện then chốt là **mọi writer đi qua cùng wrapper**, không giữ và sửa underlying `ArrayList` từ bên ngoài. Nếu reference tới backing list bị lộ và có writer dùng khóa khác hoặc không khóa, bảo đảm nhất quán vẫn bị phá.

`Collections.unmodifiableList(shared)` thì khác: nó chỉ chặn chỉnh sửa **qua reference của view đó** và vẫn phản ánh update từ `shared`. Nó không tạo snapshot. `List.copyOf` tạo bản sao cấu trúc độc lập khi nguồn được đọc an toàn.

### 12.2. Ví dụ 5 — copy dưới monitor và giữ snapshot ổn định (Java 17+)

Tạo tệp `LockedSnapshotDemo.java`:

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LockedSnapshotDemo {
    public static void main(String[] args) {
        List<Integer> shared =
            Collections.synchronizedList(
                new ArrayList<>(List.of(1, 2))
            );

        List<Integer> snapshot;
        synchronized (shared) {
            snapshot = List.copyOf(shared);
        }

        shared.add(3);

        System.out.println("snapshot = " + snapshot);
        System.out.println("current = " + shared);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac LockedSnapshotDemo.java
java LockedSnapshotDemo
~~~

Stdout:

~~~text
snapshot = [1, 2]
current = [1, 2, 3]
~~~

Vì lấy `snapshot` trong `synchronized (shared)`, mọi update đồng thời thực hiện qua wrapper phải đợi lock nhả. Sau đó `shared.add(3)` thay đổi nguồn, nhưng cấu trúc của snapshot vẫn giữ [1, 2]. Ví dụ dùng số nguyên immutable, nên không có vấn đề phần tử mutable bị đổi sau khi snapshot tạo.

**Hiệu năng:** Copy chiếm thời gian và bộ nhớ tỉ lệ số phần tử; trong lúc copy, writer phải chờ. Nếu cần snapshot rất thường xuyên trên list lớn, mô hình này có thể gây contention; chọn giải pháp khác tùy SLA.

### 12.3. Có thể dùng ReadWriteLock không?

Có. Một `ReadWriteLock` cho phép nhiều reader đồng thời giữ read lock và ngăn writer giữ write lock khi reader đang copy. Nhưng **mọi thay đổi** cần dùng đúng write lock, mọi snapshot reader cần giữ read lock trong suốt lần đọc toàn trạng thái. Không được lock chỉ quanh từng `get` riêng lẻ rồi unlock giữa các phần của snapshot.

Cần chú ý writer starvation, fairness, lock ordering và chi phí giữ lock quá lâu. Nếu dữ liệu là immutable snapshot, đôi khi một `AtomicReference` đơn giản hơn.

## 13. Snapshot nhất quán bằng immutable state + AtomicReference

Một mô hình khác là tạo một **object bất biến chứa toàn bộ trạng thái cần nhất quán**, sau đó thay **một reference duy nhất** một cách nguyên tử. Reader chỉ lấy reference một lần, nên luôn có một state đầy đủ nhất quán.

Ví dụ hai số dư luôn có tổng 100:

~~~java
// Hình dung cấu trúc Java record:
record State(int a, int b) {}
AtomicReference<State> current =
    new AtomicReference<>(new State(60, 40));
~~~

Writer tính state mới rồi dùng `compareAndSet` hoặc `updateAndGet` để thay cả cặp. Reader lấy `current.get()` một lần; các field trong record là final primitive và không thay đổi sau khi tạo.

### 13.1. Ví dụ 6 — coherent immutable snapshot (Java 17+)

Tạo tệp `AtomicStateSnapshotDemo.java`:

~~~java
import java.util.concurrent.atomic.AtomicReference;

public class AtomicStateSnapshotDemo {
    record State(int left, int right) {
        State {
            if (left < 0 || right < 0 || left + right != 100) {
                throw new IllegalArgumentException("Invalid state");
            }
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        AtomicReference<State> current =
            new AtomicReference<>(new State(60, 40));

        Thread first = new Thread(() ->
            current.updateAndGet(s ->
                new State(s.left() - 10, s.right() + 10)
            )
        );

        Thread second = new Thread(() ->
            current.updateAndGet(s ->
                new State(s.left() - 20, s.right() + 20)
            )
        );

        first.start();
        second.start();
        first.join();
        second.join();

        State snapshot = current.get();

        System.out.println("left = " + snapshot.left());
        System.out.println("right = " + snapshot.right());
        System.out.println(
            "snapshot total = "
                + (snapshot.left() + snapshot.right())
        );
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac AtomicStateSnapshotDemo.java
java AtomicStateSnapshotDemo
~~~

Stdout:

~~~text
left = 30
right = 70
snapshot total = 100
~~~

Dù hai thread cập nhật theo thứ tự nào, mỗi hàm giảm left và tăng right cùng amount (10 hoặc 20). `AtomicReference.updateAndGet` dùng cơ chế cập nhật atomic và có thể **gọi lại hàm biến đổi** khi cạnh tranh; hàm lambda ở ví dụ thuần túy, không có side effect ngoài việc dựng `State` mới. Khi hai cập nhật hoàn tất, left = 60 - 10 - 20 = 30, right = 40 + 10 + 20 = 70.

Mỗi record `State` tạo ra có invariant tổng 100; reader giữ **một reference bất biến** nên không thể trộn left của state cũ với right của state mới như ví dụ map.

**Cảnh báo:** `record` chỉ làm các field của record là final; nếu field của record là reference tới `List` hoặc một object mutable, record **không tự deep-immutable**. Cần copy phòng vệ cho các field mutable.

### 13.2. Tại sao AtomicReference tốt cho snapshot ít write?

Khi write tạo một object immutable mới rồi thay reference, reader có thể đọc nhanh mà không cần giữ khóa khi duyệt state đã lấy. Nhưng mỗi update phải cấp phát state mới, tăng áp lực GC. CAS có thể retry và hàm cập nhật có thể chạy nhiều lần, vì thế **không đặt side effect** như gửi mạng/trừ tiền ở trong lambda `updateAndGet`.

Nếu state rất lớn hoặc write cực thường xuyên, chi phí copy sẽ lớn. Cần đo và cân nhắc partitioning, lock hoặc thiết kế ownership phù hợp.

## 14. Snapshot với versioning và retry: dùng khi nào?

Một giao thức nâng cao là đọc **version**, đọc dữ liệu, rồi đọc version lần nữa; nếu version thay đổi thì retry. Ví dụ ý tưởng:

~~~text
reader:
  v1 = version
  data = readAll()
  v2 = version
  if v1 != v2: retry
~~~

Tuy nhiên **đoạn này chưa đủ để trở thành một protocol đúng**:

- Nếu writer có thể bắt đầu update mà version chưa thay đổi, reader có thể quan sát state dở nhưng hai version bằng nhau.
- Cần quy định version **đánh dấu cả lúc bắt đầu và kết thúc write**, thường phân biệt trạng thái “đang ghi” với “ổn định”.
- Cần memory-ordering phù hợp để ngăn reorder/hide update.
- Mọi writer phải tuân thủ protocol; nếu có writer khác sửa dữ liệu mà không đổi version, snapshot có thể sai.
- Reader retry vô hạn dưới contention gây starvation; cần deadline/backoff/fallback lock.
- Nếu dữ liệu gồm field có data race, một kiểu seqlock tự viết trong Java có thể sai JMM dù thuật toán trông hợp lý.

Vì vậy, với dữ liệu nghiệp vụ quan trọng, **ưu tiên primitive đã có contract rõ**: cùng khóa cho toàn bộ snapshot hoặc immutable object được publish bằng `AtomicReference`. Chỉ tự làm versioned snapshot khi đã phân tích memory model và test kỹ.

## 15. Iterator weak và snapshot ảnh hưởng performance ra sao?

### 15.1. Chi phí duyệt của ConcurrentHashMap

Weak iteration cho phép không block writer trên toàn map trong lúc duyệt. Nó hữu ích khi writer cập nhật liên tục và reader có thể chấp nhận kết quả không phải point-in-time.

Nhưng nếu reader liên tục quét toàn bộ map lớn để đếm/sort, công việc vẫn tốn CPU và cấp phát ngoài. Nếu cần metric tần suất cao, có thể dùng accumulator/counter riêng với semantics phù hợp; không nên quét toàn map chỉ vì concurrent iterator “không khóa”.

### 15.2. Chi phí của CopyOnWriteArrayList

Với list có N phần tử, thêm/xóa/sửa cấu trúc thường copy array, chi phí thường tăng theo N. Iterator snapshot rẻ khi tạo và duyệt mà không cần khóa writer lâu, nhưng array cũ có thể bị giữ trong bộ nhớ bởi iterator sống lâu.

Mẫu phù hợp: tập **listener/subscriber** ít thay đổi nhưng được duyệt rất thường xuyên, mỗi listener là reference được quản lý hợp lý.

Mẫu không phù hợp: queue dữ liệu thay đổi hàng nghìn lần mỗi giây, list rất lớn, hoặc cập nhật cấu trúc liên tục trong lúc duyệt.

### 15.3. Copy snapshot dưới lock

Một snapshot copy dưới lock thường có chi phí O(N) cho một list N phần tử. Nếu phải tạo snapshot liên tục, writer có thể bị khóa trong lúc copy. Khi cần báo cáo tức thời hiếm hoi và tính nhất quán là bắt buộc, đây vẫn là giải pháp rất rõ ràng và dễ chứng minh.

### 15.4. Snapshot immutable bằng CAS

Reader có thể rất rẻ (đọc một reference rồi làm việc trên state bất biến). Writer chịu chi phí dựng state mới, CAS và retry dưới contention. Nếu đọc rất nhiều và ghi ít/không nhiều, lựa chọn này đáng cân nhắc.

**Không có “collection nhanh nhất” cho mọi bài toán.** Phải đo throughput, latency, allocation/GC, contention, dữ liệu dài hạn, thời gian giữ iterator và yêu cầu nhất quán.

## 16. Một số khái niệm dễ nhầm

### 16.1. Linearizability của một thao tác không biến nhiều thao tác thành transaction

Một `ConcurrentHashMap.put(key, value)` có thể có điểm hiệu lực nguyên tử của **một mapping**. Nhưng đọc A rồi B là **hai thao tác**; writer có thể chen giữa. Không có bảo đảm linearizability của một snapshot nhiều key nếu protocol không cung cấp.

### 16.2. Weakly consistent không đồng nghĩa eventually consistent theo nghĩa hệ phân tán

Weakly consistent iterator nói về **traversal concurrent trong cùng JVM**, không phải cam kết về replication, lag, eventual convergence hoặc phân tán đa máy. Nó không tự bảo đảm “nếu chờ đủ lâu, iterator hiện tại sẽ thấy mọi update”. Một iterator đơn có thể kết thúc trước khi update đến.

### 16.3. Snapshot iterator không đồng nghĩa snapshot toàn object graph

`CopyOnWriteArrayList` chụp array reference; các phần tử mutable bên trong vẫn có thể thay đổi. Muốn deep snapshot cần dữ liệu immutable/defensive copy sâu thích hợp.

### 16.4. Unmodifiable view khác independent copy

~~~java
// Minh họa, không phải chương trình độc lập.
List<Integer> source = new ArrayList<>(List.of(1, 2));
List<Integer> view = Collections.unmodifiableList(source);
List<Integer> copy = List.copyOf(source);

source.add(3);

// view thấy [1, 2, 3]; copy vẫn [1, 2].
~~~

`Collections.unmodifiableList` là view không cho chỉnh sửa **qua view**; nó vẫn sống cùng backing list. `List.copyOf` là copy unmodifiable có cấu trúc độc lập. Trên nguồn đang bị sửa concurrent, **cả việc duyệt view và việc copy** vẫn cần giao thức đồng bộ phù hợp.

### 16.5. Immutable snapshot có thể “cũ” nhưng vẫn đúng

Một snapshot được tạo ở t0 và dùng tới t2 có thể đã **stale** so với trạng thái hiện tại. Điều đó không có nghĩa nó không coherent: nó có thể đại diện chính xác cho **một thời điểm hợp lệ**. Ngược lại, một lượt đọc gần đây từ nhiều key có thể “fresh hơn” từng phần nhưng tạo một tổ hợp **chưa từng tồn tại**.

Hai yêu cầu độc lập:

- **Freshness:** dữ liệu mới đến mức nào?
- **Consistency:** dữ liệu có cùng thuộc một trạng thái hợp lệ tại một thời điểm logic không?

Ứng dụng phải quyết định cần cả hai hay chỉ một trong hai, cùng deadline và dung sai phù hợp.

## 17. Kiểm thử concurrent iteration đúng cách

### 17.1. Đừng hardcode thứ tự key không được API bảo đảm

Test sau không hợp lệ nếu map đang bị cập nhật đồng thời:

~~~java
// SAI về kỳ vọng; chỉ minh họa.
assertEquals(List.of("A", "B", "C"),
             new ArrayList<>(concurrentMap.keySet()));
~~~

Kết quả weak iterator có thể thiếu/thấy update mới tùy thời điểm; thứ tự key của `ConcurrentHashMap` cũng không phải insertion order. Test nên xác nhận những **invariant được contract bảo đảm**, không áp một thứ tự ngẫu nhiên.

### 17.2. Dùng CountDownLatch để điều phối các thời điểm quan trọng

Muốn chứng minh có thể đọc dữ liệu trộn thời điểm, ví dụ `NonAtomicSnapshotDemo` dùng latch để ép reader đọc A cũ trước khi writer cập nhật, rồi đọc B mới sau đó. Nhờ vậy output kiểm chứng được mà không trông chờ race ngẫu nhiên.

Muốn kiểm thử snapshot iterator, tạo iterator trước khi update và duyệt sau update; `CopyOnWriteSnapshotDemo` hoàn toàn xác định vì không cần scheduler.

### 17.3. Phân loại test

| Mục tiêu | Phép kiểm tra |
| --- | --- |
| Weak iterator không fail-fast khi update | Chạy traversal cùng update; không cố định tập phần tử đã thấy |
| Correctness mỗi map operation | Test contract `put/get/compute/remove(key,value)` |
| Snapshot iterator cố định cấu trúc | Tạo iterator, update list, xác minh iterator còn nội dung cũ |
| Snapshot element immutability | Thay đổi mutable element qua alias; xác nhận cần deep copy |
| Coherent snapshot đa trường | Test invariant dưới concurrent writer |
| Cancellation khi quét | Thêm deadline/interrupt ở workflow thực tế |
| Performance | Đo read/write mix, copy allocation, GC, duration |
| Không bỏ sót xử lý task | Dùng queue ownership/poll/ack, không chỉ weakly iterate |

Một bài test chạy thành công không chứng minh mọi interleaving an toàn; cần lập luận từ API và Java Memory Model. Với workload mà việc bỏ sót một phần tử có hậu quả, đừng dựa vào iterator weak để làm cơ chế phân phối công việc.

## 18. Checklist lựa chọn

| Yêu cầu thực tế | Cách tiếp cận phù hợp |
| --- | --- |
| Map nhiều writer/reader, lượt quét gần đúng là đủ | `ConcurrentHashMap` weak iteration |
| Queue nhiều producer/consumer, mỗi item cần một owner xử lý | `BlockingQueue.take` hoặc `ConcurrentLinkedQueue.poll` |
| Duyệt danh sách listener rất thường, thay đổi danh sách hiếm | `CopyOnWriteArrayList` |
| Duyệt với key được sắp thứ tự nhưng update concurrent | `ConcurrentSkipListMap`, chấp nhận weak consistency |
| Snapshot của list phải độc lập với nguồn | `List.copyOf` sau khi đọc nguồn an toàn |
| Cần snapshot nhiều field luôn giữ invariant | Lock chung hoặc `AtomicReference<ImmutableState>` |
| Iterator không muốn thấy update sau khi tạo | Snapshot iterator, nếu semantics shallow phù hợp |
| Cần trạng thái cực mới và nhất quán tại deadline | Giao thức transaction/snapshot riêng có điểm hiệu lực rõ |
| Nguồn dữ liệu mutable và copy không có khóa | **Không** mặc định coi copy là point-in-time |
| Phải process đủ, đúng một lần mỗi task | Ownership/ack protocol, không dựa vào traversal |

### Câu hỏi tự kiểm tra

**1.** Duyệt `ConcurrentHashMap.entrySet()` trong lúc writer thêm key mà không ném `ConcurrentModificationException`. Có thể kết luận iterator đã thấy tất cả key mới không?

Không. Iterator weakly consistent có thể quan sát một số cập nhật nhưng không hứa thấy toàn bộ; kết quả cũng không phải một snapshot nguyên tử.

**2.** `CopyOnWriteArrayList.iterator()` được tạo trước khi `add("C")`. Iterator đó có thấy C không?

Không. Snapshot iterator dùng array tại thời điểm tạo iterator, nên update cấu trúc sau đó không được phản ánh trong lượt duyệt ấy.

**3.** Có thể gọi `iterator.remove()` trên snapshot iterator của `CopyOnWriteArrayList` không?

Không. Nó ném `UnsupportedOperationException`; iterator không phải một cursor để sửa array hiện tại.

**4.** `List.copyOf` có đóng băng mọi object mutable trong phần tử không?

Không. Nó copy cấu trúc và giữ reference; muốn giữ nguyên dữ liệu sâu cần immutable element hoặc deep copy đúng nghĩa.

**5.** Hai thao tác `ConcurrentHashMap.get("A")` và `get("B")` đều thread-safe. Có đảm bảo cặp kết quả là snapshot cùng thời điểm không?

Không. Writer có thể thay đổi state giữa hai lần get; ví dụ trong bài tạo tổng 120 dù các mốc thực tế là 100, 80, 100.

**6.** Vì sao `Collections.unmodifiableList` không phải snapshot?

Vì đó là view trên backing list, vẫn phản ánh update qua reference khác. Nó chỉ cấm mutation thông qua view, không tạo bản sao độc lập.

**7.** `ConcurrentSkipListMap` có sorted key order. Có vì thế mà snapshot nhiều key nguyên tử không?

Không. Sorted traversal và point-in-time consistency là hai tính chất khác nhau.

**8.** Nếu cần snapshot nhất quán của hai biến thay đổi cùng nhau, có những cách nào?

Dùng cùng lock bao trùm **cả cập nhật lẫn đọc snapshot**, hoặc thay một immutable object chứa cả hai biến bằng một `AtomicReference`. Mọi đường writer/reader phải tuân thủ giao thức.

**9.** Một immutable snapshot có thể cũ không?

Có. Tính nhất quán nói snapshot có đại diện cho một state hợp lệ hay không; độ mới nói nó gần hiện tại đến mức nào. Snapshot nhất quán có thể stale.

**10.** Có nên dùng weak iterator để phân phát mỗi task đúng một lần?

Không chỉ dựa vào iteration. Nên có cơ chế **claim/ownership** qua queue `poll/take` hoặc trạng thái atomic và acknowledgment.

**11.** Khi nào copy-on-write dễ gây vấn đề hiệu năng?

Khi list lớn và cập nhật cấu trúc thường xuyên. Mỗi lần update có thể phải copy array; các snapshot cũ còn được iterator giữ cũng có thể gây áp lực bộ nhớ.

**12.** Lời gọi `size()` rồi duyệt `ConcurrentHashMap` có bảo đảm số item duyệt đúng bằng size đọc trước đó không?

Không khi có update concurrent. `size()` và lượt duyệt là những lần quan sát khác nhau, không tạo transaction.

## 19. Tài liệu API và kết luận

Các hợp đồng chính thức của JDK nên đối chiếu theo phiên bản Java đang sử dụng:

- [ConcurrentHashMap — JDK 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)
- [ConcurrentLinkedQueue — JDK 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html)
- [ConcurrentSkipListMap — JDK 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html)
- [CopyOnWriteArrayList — JDK 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)
- [Collections.synchronizedList — JDK 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html)
- [List.copyOf — JDK 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html)

**Ghi nhớ:**

~~~text
Fail-fast iterator:
  Phát hiện sửa đổi cấu trúc không phù hợp theo best effort.
  Không phải cơ chế phối hợp thread.

Weakly consistent iterator:
  Có thể duyệt song song với update.
  Không bảo đảm thấy tất cả update.
  Không phải point-in-time snapshot.

CopyOnWriteArrayList snapshot iterator:
  Giữ cấu trúc array tại lúc tạo iterator.
  Không thấy thay đổi cấu trúc sau đó.
  Không deep-copy các object mutable.

Coherent business snapshot:
  Cần giao thức nhiều field/key (lock chung, immutable state, ...).
  Đúng invariant và có điểm quan sát nhất quán.
~~~

**Kết luận:** Một collection thread-safe có thể cho phép iterator duyệt đồng thời với writer, nhưng **tính thread-safe của từng thao tác** không đồng nghĩa **nhất quán của cả một báo cáo**. Chọn giữa weak iteration, snapshot cấu trúc và snapshot nghiệp vụ dựa vào mức độ chấp nhận dữ liệu cũ, yêu cầu invariant và chi phí cập nhật/copy của hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-005">CopyOnWriteArrayList: ưu và nhược điểm khi ghi nhiều</a>

<details>
<summary>Xem chi tiết</summary>

# CopyOnWriteArrayList: ưu và nhược điểm khi ghi nhiều

## 1. Bài toán cần giải quyết

`CopyOnWriteArrayList<E>` thuộc `java.util.concurrent`, triển khai giao diện `List<E>` theo chiến lược **copy-on-write**: khi cần thay đổi cấu trúc hoặc phần tử của danh sách, thao tác cập nhật thường tạo một mảng nền mới rồi công bố mảng mới cho các luồng đọc. Trong khi đó, luồng đọc có thể truy cập mảng hiện tại mà không phải khóa danh sách cho mỗi lần đọc.

Đây là lựa chọn tốt khi **số lần đọc/duyệt vượt xa số lần sửa**, ví dụ danh sách listener hoặc cấu hình ít thay đổi. Ngược lại, nếu thêm, xóa, thay thế phần tử liên tục, việc sao chép mảng, cấp phát bộ nhớ và tranh chấp giữa các luồng ghi có thể khiến nó đắt hơn nhiều lựa chọn khác.

Mục tiêu bài học: hiểu tính nhất quán của đọc/duyệt, thấy chi phí thực sự của mỗi lần ghi, và biết khi nào nên dùng hoặc thay thế `CopyOnWriteArrayList`. Phạm vi là Java concurrency nói chung.

## 2. Cơ chế hoạt động

Giả sử mảng nền ban đầu là `[A, B, C]`:

1. Một luồng đọc gọi `iterator()`, iterator giữ tham chiếu đến **mảng tại thời điểm tạo iterator**.
2. Luồng ghi gọi `add(D)`. Nó đồng bộ thao tác ghi, cấp phát một mảng mới, sao chép các tham chiếu phần tử từ mảng cũ và thêm `D`.
3. Luồng ghi công bố mảng `[A, B, C, D]`. Những lời gọi đọc mới có thể thấy trạng thái đã được công bố.
4. Iterator cũ tiếp tục đi qua `[A, B, C]`. Nó không thấy `D`, không bị thay đổi giữa lúc duyệt và không ném `ConcurrentModificationException` do danh sách bị sửa song song.

`CopyOnWriteArrayList` dùng cơ chế công bố mảng có bảo đảm hiển thị giữa các luồng; những thao tác sửa trên cùng danh sách được phối hợp để không làm mất cập nhật. Trong cách triển khai JDK 17, luồng ghi sử dụng một khóa nội bộ, còn các thao tác đọc thông thường lấy trạng thái mảng đã công bố. Không hiểu nhầm rằng mọi thao tác cấp cao gồm **nhiều lời gọi riêng biệt** đều tự động trở thành một giao dịch nguyên tử.

**Snapshot ở đây là snapshot của mảng tham chiếu**, không phải bản sao sâu của từng object. Nếu phần tử là object có thể thay đổi, một luồng sửa trực tiếp trường của object vẫn có thể ảnh hưởng đến dữ liệu mà iterator cũ nhìn thấy; thread safety của object đó cần được đảm bảo riêng.

## 3. Chi phí đọc và ghi

| Thao tác | Đặc điểm/chi phí điển hình | Ý nghĩa |
| --- | --- | --- |
| `get(index)` | O(1), không sao chép toàn mảng | Phù hợp với đọc theo chỉ số. |
| `iterator()` rồi duyệt | Tạo iterator O(1), duyệt n phần tử O(n) | Một lần duyệt thấy một ảnh chụp ổn định của các tham chiếu phần tử. |
| `add(element)`, `add(index, element)` | O(n) để tạo mảng mới và sao chép tham chiếu | Danh sách càng lớn, mỗi lần thêm càng tốn chi phí. |
| `remove(index)` hoặc xóa phần tử tồn tại | Thường O(n) để tìm/dịch và tạo mảng mới | Xóa từng phần tử nhiều lần dễ sinh nhiều mảng trung gian. |
| `set(index, value)` | Thường O(n) khi cần thay phần tử trong mảng mới | Không coi `set` như cập nhật O(1) kiểu `ArrayList`. |
| `contains(value)` | O(n) tìm tuyến tính | Không nên dùng để kiểm tra thành viên với tần suất rất cao trên danh sách lớn. |
| `addIfAbsent(value)` | Tìm phần tử và, nếu thiếu, thêm theo cơ chế an toàn cho luồng; thường O(n) | Tránh lỗi cạnh tranh của cặp `contains` + `add` nhưng không giải quyết chi phí sao chép. |

Các độ phức tạp trên diễn tả chi phí phổ biến của những thao tác thực sự làm thay đổi dữ liệu, không khẳng định mọi nhánh triển khai đều sao chép mảng (ví dụ xóa phần tử không tồn tại có thể không tạo mảng mới). Những phép cập nhật hàng loạt như `addAll`, `removeIf` hoặc `clear` có hành vi và chi phí riêng; khi có thể, gom thay đổi hợp lý thường tốt hơn gọi `add`/`remove` từng phần tử.

**Vì sao ghi nhiều rất đắt?** Với n phần tử, mỗi lần thêm thành công thường phải sao chép khoảng n tham chiếu. Nếu có m thao tác thêm độc lập ở kích thước gần n, lượng công việc sao chép xấp xỉ O(m × n), chưa kể đồng bộ, cấp phát và dọn rác. Nếu thêm lần lượt n phần tử vào danh sách ban đầu rỗng bằng n lời gọi `add`, tổng số tham chiếu được sao chép có bậc O(n²).

Ví dụ ước lượng: một mảng chứa 100.000 tham chiếu có phần tham chiếu khoảng 0,4 MB nếu JVM dùng tham chiếu 4 byte, hoặc 0,8 MB nếu dùng 8 byte, chưa tính header/căn chỉnh. Một nghìn lần cập nhật thành công mỗi giây ở quy mô đó có thể tạo ra hàng trăm MB phân bổ mảng mỗi giây. Đây là **ước lượng minh họa theo cấu hình JVM**, không phải kết quả benchmark hay dự đoán độ trễ cụ thể. Các object phần tử không bị clone sâu; cái được sao chép chủ yếu là các tham chiếu.

## 4. Ưu điểm trong bài toán đọc nhiều, ghi ít

- **Đọc ít tranh chấp với ghi:** các thao tác đọc thông thường không cần giữ khóa ghi; luồng khác có thể công bố phiên bản mảng mới trong lúc một iterator cũ đang duyệt.
- **Iterator ổn định:** không cần bọc toàn bộ vòng lặp trong `synchronized` chỉ để chống danh sách bị sửa đồng thời; iterator không gặp `ConcurrentModificationException` do thay đổi cấu trúc của danh sách.
- **Thứ tự và API `List`:** giữ thứ tự chèn và hỗ trợ truy cập theo index, hữu ích khi thứ tự listener có ý nghĩa.
- **Đơn giản cho danh sách đăng ký ít đổi:** listener, callback, handler, cấu hình chỉ thỉnh thoảng được bổ sung/xóa nhưng đọc rất thường xuyên.
- **Một số thao tác tiện ích cho cạnh tranh:** `addIfAbsent` và `addAllAbsent` giúp thêm phần tử chưa tồn tại mà không cần tự ghép kiểm tra và cập nhật thành hai lời gọi tách rời.

Các lợi ích này chỉ bù được chi phí ghi khi workload phù hợp. “Thread-safe” không đồng nghĩa với “nhanh trong mọi tình huống đa luồng”.

## 5. Nhược điểm khi ghi nhiều

### 5.1. Sao chép O(n) cho nhiều cập nhật

Giả sử danh sách có 50.000 listener và mỗi event lại thêm/xóa nhiều listener. Các thao tác ghi có thể liên tiếp sao chép mảng tham chiếu lớn. Kích thước càng tăng, tổng chi phí CPU và băng thông bộ nhớ càng đáng kể. Đặc biệt không nên dùng `CopyOnWriteArrayList` làm một danh sách công việc mà các luồng liên tục `add`/`remove` như queue.

### 5.2. Cấp phát và áp lực garbage collection

Các phiên bản mảng cũ trở thành đối tượng có thể thu gom khi không còn ai giữ tham chiếu. Tuy nhiên iterator snapshot đang sống có thể **giữ mảng cũ lâu hơn**, nên nhiều iterator lâu sống cùng với cập nhật liên tục làm tăng lượng bộ nhớ tồn tại đồng thời. Thông lượng cấp phát cao có thể kéo theo nhiều lần GC và biến động độ trễ.

### 5.3. Tranh chấp giữa các writer

Nhiều luồng có thể đọc thuận tiện, nhưng các thao tác sửa vẫn phải phối hợp/tuần tự hóa đối với cùng danh sách. Nếu các writer hoạt động dày đặc, chúng cạnh tranh nhau trong phần công việc ghi vốn đã có sao chép O(n); tăng số writer không bảo đảm tăng thông lượng.

### 5.4. Iterator không thấy thay đổi mới

Iterator tạo trước khi thêm/xóa vẫn thấy snapshot cũ. Điều này là ưu điểm cho việc duyệt nhất quán, nhưng không phù hợp nếu thuật toán yêu cầu một vòng lặp **ngay lập tức quan sát** dữ liệu vừa thay đổi. Mỗi lần gọi `iterator()` mới lấy một snapshot tại thời điểm riêng; không có lời hứa tất cả các vòng lặp chạy song song nhìn thấy cùng trạng thái.

### 5.5. Không sửa trực tiếp qua iterator

`Iterator.remove()` và `ListIterator.remove()`, `ListIterator.set()`, `ListIterator.add()` không được hỗ trợ; chúng ném `UnsupportedOperationException`. Nếu cần xóa, hãy thao tác qua chính danh sách, ví dụ `removeIf(predicate)`; iterator đã tạo trước đó vẫn duyệt snapshot cũ.

### 5.6. Đừng nhầm an toàn của danh sách với an toàn của từng phần tử

`CopyOnWriteArrayList<Account>` bảo vệ cấu trúc danh sách; nó không tự làm cho phép cộng trừ trên `Account.balance` an toàn. Tương tự, mã kiểu `if (!list.contains(x)) list.add(x);` gồm hai lời gọi riêng, có thể bị luồng khác xen vào. Với mục đích thêm nếu vắng mặt, dùng `addIfAbsent(x)`; với các quy tắc nghiệp vụ nhiều bước, cần thiết kế đồng bộ/phối hợp thích hợp ở cấp nghiệp vụ.

## 6. Ví dụ Java 17+: snapshot và thao tác thêm an toàn

Tạo tệp `CowSnapshotDemo.java`:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CowSnapshotDemo {
    public static void main(String[] args) {
        CopyOnWriteArrayList<String> names =
                new CopyOnWriteArrayList<>(List.of("An", "Bình"));

        Iterator<String> beforeAdd = names.iterator();
        names.add("Chi");

        List<String> snapshot = new ArrayList<>();
        beforeAdd.forEachRemaining(snapshot::add);

        System.out.println("Snapshot: " + snapshot);
        System.out.println("Current : " + names);
        System.out.println("Add Chi again: " + names.addIfAbsent("Chi"));
        System.out.println("Add Dũng: " + names.addIfAbsent("Dũng"));
        System.out.println("Final   : " + names);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 CowSnapshotDemo.java
java CowSnapshotDemo
```

Kết quả xác định:

```text
Snapshot: [An, Bình]
Current : [An, Bình, Chi]
Add Chi again: false
Add Dũng: true
Final   : [An, Bình, Chi, Dũng]
```

Iterator `beforeAdd` giữ mảng trước khi thêm `Chi`. Lời gọi `names.add("Chi")` cập nhật danh sách nhưng không thay ảnh chụp đó. `addIfAbsent("Chi")` trả `false` vì `Chi` đã tồn tại; `addIfAbsent("Dũng")` thêm mới và trả `true`.

## 7. Ví dụ nhiều luồng: dùng addIfAbsent thay cho contains + add

Tạo tệp `CowConcurrentDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CowConcurrentDemo {
    public static void main(String[] args) throws Exception {
        CopyOnWriteArrayList<String> jobs =
                new CopyOnWriteArrayList<>(List.of("seed"));
        int writers = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < writers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    jobs.addIfAbsent("job-42");
                    return null;
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }

        long count = jobs.stream().filter("job-42"::equals).count();
        System.out.println("Count: " + count);
        System.out.println("Jobs : " + jobs);
    }
}
```

```bash
javac -encoding UTF-8 CowConcurrentDemo.java
java CowConcurrentDemo
```

```text
Count: 1
Jobs : [seed, job-42]
```

Cả bốn worker được mở cổng bằng `CountDownLatch` rồi cùng thử thêm cùng một giá trị. `addIfAbsent` bảo đảm thao tác “kiểm tra và thêm nếu vắng mặt” không tạo bốn bản sao phần tử trên danh sách này. Giá trị trả về chỉ phản ánh worker nào thêm được; kết quả cuối có đúng một `job-42`. Trường hợp dùng `if (!jobs.contains("job-42")) jobs.add("job-42");` sẽ có nguy cơ hai worker cùng thấy “chưa có” rồi cùng thêm. Dù tránh trùng lặp bằng `addIfAbsent`, nếu nhiều giá trị khác nhau liên tục được thêm vào danh sách lớn thì chi phí copy-on-write vẫn còn.

## 8. Chọn cấu trúc khác khi workload đổi

| Nhu cầu thực tế | Lựa chọn cân nhắc | Điều phải chú ý |
| --- | --- | --- |
| Danh sách listener nhỏ/vừa, gần như chỉ đọc | `CopyOnWriteArrayList` | Thích hợp nhất khi duyệt áp đảo cập nhật và chấp nhận snapshot. |
| Danh sách cần thêm/xóa liên tục, có truy cập theo index | `ArrayList` được bảo vệ bởi `synchronized` hoặc `ReentrantReadWriteLock` | Phải bảo vệ **toàn bộ** thao tác phức hợp và vòng duyệt; đọc có thể bị khóa. Đo đạc theo workload. |
| Cần hàng đợi nhiều producer/consumer | `ConcurrentLinkedQueue`, hoặc `BlockingQueue` như `LinkedBlockingQueue`/`ArrayBlockingQueue` | Queue có semantics FIFO/nhận việc, không phải `List` theo index. Chọn bounded queue nếu cần giới hạn backlog. |
| Cần lưu trữ theo key, cập nhật và tra cứu đồng thời | `ConcurrentHashMap<K,V>` | Semantics map, không giữ thứ tự list; phù hợp hơn nếu chính nhu cầu là lookup theo key. |
| Cần tập hợp các phần tử duy nhất, thao tác thêm/xóa thường xuyên | `ConcurrentHashMap.newKeySet()` | Không có thứ tự index như `List`; iterator có tính nhất quán yếu thay vì snapshot cố định. |

Không có cấu trúc nào là bản thay thế 1:1 cho mọi yêu cầu. Trước khi đổi, xác định rõ: có cần thứ tự phần tử và index không, iterator phải thấy trạng thái nào, mức độ đồng thời ra sao, có cần giới hạn bộ nhớ/queue không, và thao tác nào cần tính nguyên tử.

### Một lỗi chọn cấu trúc thường gặp

Nếu cần đăng ký/hủy đăng ký listener rất thường xuyên, nhưng mỗi lần phát sự kiện chỉ duyệt một lần, `CopyOnWriteArrayList` có thể trở thành điểm nghẽn. Không nên chỉ tăng thread pool để khắc phục: các writer vẫn tuần tự hóa và sao chép. Hãy đánh giá khả năng đổi cấu trúc, giảm tần suất cập nhật, gom các cập nhật hợp lý, hoặc cung cấp snapshot bất biến được công bố theo lô khi yêu cầu ứng dụng cho phép.

## 9. Cách đánh giá bằng đo đạc

Đừng quyết định chỉ dựa trên tên “concurrent”. Thu thập tải đại diện và đo:

1. **Tỷ lệ đọc/ghi**: số `get`/duyệt so với `add`/`remove`/`set` trên một đơn vị thời gian.
2. **Kích thước danh sách**: trung bình, p95 và mức cực đại, bởi chi phí copy phụ thuộc số phần tử.
3. **Số reader/writer đồng thời**: xác định luồng ghi có tranh chấp nhiều hay không.
4. **Độ trễ và thông lượng**: đặc biệt p95/p99 của thao tác ghi và của đường xử lý chính.
5. **Cấp phát và GC**: allocation rate, heap occupancy và pause time khi cập nhật liên tục.
6. **Yêu cầu nhất quán**: iterator snapshot có phù hợp không, hay cần thấy thay đổi mới/lập tức?

Có thể dùng JMH cho microbenchmark các thao tác Java và benchmark tích hợp cho luồng xử lý thực tế. Benchmark cần warm-up JVM, kích thước dữ liệu thực tế, nhiều reader/writer, kiểm soát dead-code elimination và so sánh cùng đặc tả nhất quán. Không lấy một vòng lặp `System.nanoTime()` đơn giản làm kết luận hiệu năng.

## 10. Câu hỏi tự kiểm tra và kết luận

**Hỏi:** Đọc nhiều, ghi hiếm, cần callback theo thứ tự: dùng gì?  
**Đáp:** `CopyOnWriteArrayList` là ứng viên hợp lý; iterator snapshot giúp duyệt ổn định ngay cả khi có luồng đăng ký/hủy đăng ký.

**Hỏi:** Có 200.000 phần tử, nhiều writer liên tục `add` và `remove`: vì sao chậm?  
**Đáp:** Mỗi cập nhật thành công thường phải sao chép lượng lớn tham chiếu, cấp phát mảng mới và phối hợp các writer; thêm áp lực GC và snapshot cũ có thể giữ bộ nhớ.

**Hỏi:** Iterator tạo trước `list.add(x)` có thấy `x` không?  
**Đáp:** Không. Iterator đó đi trên mảng cũ. Iterator mới tạo sau khi `add` hoàn tất có thể thấy `x` nếu chưa bị thay đổi tiếp.

**Hỏi:** Có thể gọi `iterator.remove()` để xóa phần tử không?  
**Đáp:** Không; phương thức này ném `UnsupportedOperationException`. Hãy sửa qua API của danh sách, chẳng hạn `removeIf`.

**Hỏi:** `CopyOnWriteArrayList` có làm mọi object bên trong thread-safe không?  
**Đáp:** Không. Nó bảo vệ cấu trúc danh sách và việc công bố tham chiếu, không tự đồng bộ trạng thái mutable bên trong object.

**Ghi nhớ:** `CopyOnWriteArrayList` đánh đổi **chi phí ghi và bộ nhớ** để có **đọc/duyệt ổn định, ít tranh chấp**. Nó thường phát huy lợi thế ở mô hình *read-mostly*; với workload *write-heavy*, hãy xem xét lại cấu trúc dữ liệu và đo đạc cả độ trễ, lượng cấp phát lẫn yêu cầu nhất quán.

Tài liệu chuẩn: [Oracle Java 17 API — CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-006">ConcurrentLinkedQueue và ConcurrentLinkedDeque</a>

<details>
<summary>Xem chi tiết</summary>

# ConcurrentLinkedQueue và ConcurrentLinkedDeque trong Java

> **Trọng tâm:** `ConcurrentLinkedQueue<E>` là **hàng đợi FIFO thread-safe, không giới hạn dung lượng ở tầng API, sử dụng thuật toán non-blocking**; `ConcurrentLinkedDeque<E>` là **deque thread-safe, cũng unbounded**, cho phép thêm/lấy ở **hai đầu**. Các thao tác riêng lẻ như `offer/poll` hoặc `offerFirst/pollLast` có thể được nhiều thread gọi đồng thời mà không cần bao chúng bằng một monitor của ứng dụng. Nhưng **không blocking** không đồng nghĩa “tự đợi khi rỗng”, và **thread-safe collection** không đồng nghĩa mọi chuỗi nhiều thao tác, snapshot, quota hoặc object được chứa đều trở nên nguyên tử.

Bài thuộc nhóm **Concurrent Collections**, phạm vi **Java concurrency nói chung** (Java SE 17+). Không bàn đến Spring.

## 1. Bối cảnh: vì sao không dùng ArrayDeque hoặc LinkedList tùy tiện?

Trong một pipeline producer–consumer, nhiều producer cùng gửi việc, nhiều consumer cùng nhận. Nếu chia sẻ một `ArrayDeque`/`LinkedList` thông thường rồi gọi `add/remove` đồng thời mà không đồng bộ, chương trình có thể rơi vào data race, mất cập nhật, nhìn thấy trạng thái thiếu nhất quán, hoặc làm sai cấu trúc dữ liệu.

Một phương án là dùng `synchronized` quanh mọi truy cập; một phương án khác là chọn collection được thiết kế cho concurrent access.

~~~text
Nhiều producers                               Nhiều consumers
    P1 ── offer(J1) ─┐                 ┌─ poll() ──> C1
    P2 ── offer(J2) ─┼─> Queue FIFO ───┤
    P3 ── offer(J3) ─┘                 └─ poll() ──> C2

Mỗi phần tử được dequeue tối đa một lần bởi một poll thành công.
Nhưng thứ tự xử lý/xong việc giữa C1 và C2 không được đảm bảo.
~~~

`ConcurrentLinkedQueue` phù hợp khi nhiều thread cần trao đổi phần tử theo hướng vào **đuôi**, lấy từ **đầu**. Khi phải thao tác cả hai đầu, dùng `ConcurrentLinkedDeque`.

**Phân biệt quan trọng:** Cả hai là concurrent collections **không chặn** (*non-blocking*); chúng không phải `BlockingQueue` / `BlockingDeque`. Khi rỗng, `poll()` trả `null` ngay, không đợi producer. Khi đầy theo nghĩa bộ nhớ thực tế, không có một limit vận hành được cấu hình sẵn cho `offer` trả `false` và tạo backpressure.

## 2. Hai lớp: giống và khác nhau ở đâu?

| Thuộc tính | `ConcurrentLinkedQueue<E>` | `ConcurrentLinkedDeque<E>` |
| --- | --- | --- |
| Vai trò | FIFO queue: thêm cuối, lấy đầu | Deque: thêm/lấy đầu và cuối |
| Interface chính | `Queue<E>` | `Deque<E>`, đồng thời là `Queue<E>` |
| Dữ liệu lưu | Các phần tử `E`, node liên kết | Các phần tử `E`, node liên kết hai hướng về mặt giao diện |
| Thao tác thường gặp | `offer`, `poll`, `peek` | `offerFirst`, `offerLast`, `pollFirst`, `pollLast`, `push`, `pop` |
| Chấp nhận null? | **Không** | **Không** |
| Capacity giới hạn bởi API? | Unbounded | Unbounded |
| `put()` / `take()` chờ? | **Không có** | **Không có** |
| Iterator | *Weakly consistent*, không fail-fast | *Weakly consistent*, thêm `descendingIterator` |
| `size()` | O(n), không là snapshot tin cậy dưới concurrent modifications | O(n), tương tự |
| Bulk operations | Không mặc nhiên atomic trên toàn tập | Không mặc nhiên atomic trên toàn tập |
| Phù hợp | Nhiều producers/consumers, FIFO handoff không chặn | Work list hai đầu, FIFO/LIFO, lấy việc từ hai đầu |

“Unbounded” ở đây nói về **không có capacity hữu hạn định nghĩa sẵn** cho phương thức enqueue; không phải JVM có vô hạn heap. Chứa quá nhiều work có thể giữ lại object graph lớn và gây `OutOfMemoryError`.

### Sơ đồ Queue và Deque

~~~text
ConcurrentLinkedQueue:
  head                         tail
   v                            v
 [A] -> [B] -> [C] -> [D] -> [E]
  ^                           |
  | poll()                    +-- offer(F) vào cuối

ConcurrentLinkedDeque:
  first                         last
    v                            v
  [A] <-> [B] <-> [C] <-> [D] <-> [E]
    ^                            ^
    |                            |
 pollFirst()/offerFirst()    pollLast()/offerLast()
~~~

Các sơ đồ diễn tả **logic của API**, không mô tả chi tiết con trỏ/node hoặc từng bước CAS trong implementation OpenJDK. Không suy từ hình vẽ rằng mọi operation đều là O(1) trong tình trạng contention hoặc cleanup nội bộ.

## 3. API Queue: offer, poll, peek và phương thức ném exception

Với `ConcurrentLinkedQueue<E>`:

| Phương thức | Ý nghĩa | Nếu queue trống |
| --- | --- | --- |
| `offer(e)` | Thêm phần tử vào đuôi, thường trả `true` | Không áp dụng; `null` bị từ chối |
| `add(e)` | Thêm vào đuôi | Không áp dụng |
| `poll()` | Lấy **và xóa** phần tử đầu | Trả `null` |
| `peek()` | Đọc phần tử đầu nhưng **không xóa** | Trả `null` |
| `remove()` (không tham số) | Lấy và xóa phần tử đầu | Ném `NoSuchElementException` |
| `element()` | Đọc đầu mà không xóa | Ném `NoSuchElementException` |
| `remove(object)` | Xóa một phần tử khớp `equals` nếu tìm thấy | Trả boolean |
| `isEmpty()` | Hỏi có rỗng tại thời điểm quan sát | Có thể đổi ngay sau lời gọi |

Không nhầm hai overload `remove()` và `remove(Object)`: một cái lấy head và có thể ném exception khi rỗng, cái còn lại tìm/xóa phần tử theo giá trị.

### Ví dụ 1 — FIFO, peek và null

Tạo `LinkedQueueBasics.java` (Java 17+):

~~~java
import java.util.concurrent.ConcurrentLinkedQueue;

public class LinkedQueueBasics {
    public static void main(String[] args) {
        ConcurrentLinkedQueue<String> queue =
                new ConcurrentLinkedQueue<>();

        System.out.println("offer A = " + queue.offer("A"));
        System.out.println("offer B = " + queue.offer("B"));

        System.out.println("peek = " + queue.peek());
        System.out.println("poll 1 = " + queue.poll());
        System.out.println("poll 2 = " + queue.poll());
        System.out.println("poll empty = " + queue.poll());

        try {
            queue.offer(null);
        } catch (NullPointerException expected) {
            System.out.println("null rejected = true");
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac LinkedQueueBasics.java
java LinkedQueueBasics
~~~

Stdout xác định:

~~~text
offer A = true
offer B = true
peek = A
poll 1 = A
poll 2 = B
poll empty = null
null rejected = true
~~~

`peek` không xóa A; `poll` xóa A rồi B, và `poll` tiếp trả `null`. Cấm `null` làm element là cần thiết vì API đã dùng `null` để biểu thị queue rỗng. Trong một protocol end-of-stream, hãy dùng **sentinel non-null** có type rõ ràng, không dùng null làm poison pill.

`poll()==null` không báo hệ thống đã hết work mãi mãi: producer có thể `offer` một item ngay sau lúc poll trả về. Muốn kết thúc consumer, cần một completion protocol (producer-done, sentinel, lifecycle coordinator), không chỉ kiểm tra rỗng một lần.

## 4. FIFO của Queue không phải thứ tự hoàn thành của nhiều consumer

FIFO nói rằng những phần tử **được enqueue theo một thứ tự xác định** sẽ được lấy từ head theo thứ tự đó. Khi nhiều producer đồng thời `offer`, **thứ tự xen kẽ giữa producers không cố định**.

Ví dụ:

~~~text
Producer P1: offer(A1) -> offer(A2)
Producer P2: offer(B1) -> offer(B2)

Một thứ tự enqueue hợp lệ:
 A1, B1, B2, A2
Một thứ tự khác:
 B1, A1, A2, B2

Không được mặc định toàn bộ A chạy trước toàn bộ B.
~~~

Trong mỗi producer, các lần `offer` được thực hiện theo thứ tự chương trình; nhưng tổng thứ tự giữa các producer do interleaving quyết định. Khi nhiều consumer cùng `poll`, item được một consumer lấy khỏi queue không còn được `poll` bởi consumer khác. Tuy vậy consumer A có thể xử lý chậm hơn consumer B; **completion order có thể khác dequeue order**.

Điều này quyết định cách viết test: kiểm tra **tập hợp job, số lần nhận và kết quả cuối**, không in/log name thread rồi gán một trình tự cố định cho scheduler.

## 5. Ví dụ 2 — ba producer, hai consumer, không mất hoặc trùng item

Ví dụ này dùng hai consumer `poll` **đồng thời** khi ba producer `offer` các ID độc nhất. Một cờ hoàn tất cho biết **tất cả producer đã kết thúc**. Khi queue đang tạm rỗng nhưng producer chưa xong, consumer chưa được phép thoát.

Tạo `ConcurrentQueueWorkers.java` (Java 17+):

~~~java
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentQueueWorkers {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentLinkedQueue<Integer> work =
                new ConcurrentLinkedQueue<>();
        Set<Integer> unique = ConcurrentHashMap.newKeySet();
        AtomicBoolean producersDone = new AtomicBoolean(false);
        AtomicInteger processed = new AtomicInteger();

        Runnable consumerLogic = () -> {
            while (true) {
                Integer item = work.poll();

                if (item != null) {
                    unique.add(item);
                    processed.incrementAndGet();
                    continue;
                }
                if (producersDone.get()) {
                    return; // Không còn producer gửi thêm item.
                }
                Thread.yield(); // Demo ngắn; không là backpressure.
            }
        };

        Thread[] consumers = {
                new Thread(consumerLogic, "consumer-1"),
                new Thread(consumerLogic, "consumer-2")
        };
        for (Thread consumer : consumers) {
            consumer.start();
        }

        Thread[] producers = new Thread[3];
        for (int p = 0; p < producers.length; p++) {
            final int producerId = p;
            producers[p] = new Thread(() -> {
                for (int i = 0; i < 1_000; i++) {
                    work.offer(producerId * 1_000 + i);
                }
            });
            producers[p].start();
        }

        for (Thread producer : producers) {
            producer.join();
        }
        producersDone.set(true);

        for (Thread consumer : consumers) {
            consumer.join();
        }

        System.out.println("processed = " + processed.get());
        System.out.println("unique = " + unique.size());
        System.out.println("queue empty = " + work.isEmpty());
    }
}
~~~

Chạy:

~~~bash
javac ConcurrentQueueWorkers.java
java ConcurrentQueueWorkers
~~~

Stdout sau khi tất cả threads hoàn tất:

~~~text
processed = 3000
unique = 3000
queue empty = true
~~~

Mỗi producer đưa 1.000 ID riêng, tổng 3.000 item. Mỗi `poll` thành công loại **một** item; hai consumer không cùng nhận chính item ấy. `unique` ghi nhận tập hợp ID đã nhận; `processed` đếm số lần xử lý. Sau `join` tất cả producer, main đặt cờ volatile/atomic `producersDone=true`; vì không còn người thêm item, consumer có thể thoát khi poll trả null. `join` consumer rồi mới tổng kết.

**Đây là demo về tính đúng đắn, không phải kiểu consumer loop tốt cho production.** `Thread.yield()` không có bảo đảm thời gian đợi và có thể gây CPU spinning. Khi cần đợi item đến mà không tốn CPU, thường nên sử dụng **`BlockingQueue.take()`** với completion protocol rõ ràng. Cách dùng `ConcurrentLinkedQueue` phù hợp hơn khi caller có việc khác để làm nếu poll rỗng, hoặc hệ thống có một notification/event-loop protocol được kiểm soát.

### Điều gì xảy ra nếu không có producersDone?

Nếu mỗi consumer chỉ `while ((item = queue.poll()) != null)` thì có thể thoát **quá sớm** trong lúc producer chưa kịp enqueue item đầu tiên, hoặc giữa hai lần enqueue. Vì vậy phải tách hai câu hỏi:

- “Queue đang rỗng ở thời điểm này?”
- “Không bao giờ còn producer phát thêm work?”

Chỉ câu thứ hai mới giúp xác định điểm đóng pipeline.

## 6. Deque: hai đầu và ba mô hình truy cập

`ConcurrentLinkedDeque<E>` triển khai `Deque<E>`. Từ *deque* là *double-ended queue*: người dùng có thể thêm hoặc lấy ở head và tail.

Các phương thức quan trọng:

| Nhóm | Đầu (`first`) | Cuối (`last`) |
| --- | --- | --- |
| Thêm | `addFirst(e)` / `offerFirst(e)` | `addLast(e)` / `offerLast(e)` |
| Lấy/xóa khi có, trả null nếu rỗng | `pollFirst()` | `pollLast()` |
| Xem, không xóa, trả null nếu rỗng | `peekFirst()` | `peekLast()` |
| Lấy/xóa, ném exception nếu rỗng | `removeFirst()` | `removeLast()` |
| Xem, ném exception nếu rỗng | `getFirst()` | `getLast()` |
| Xóa theo giá trị | `removeFirstOccurrence(o)` | `removeLastOccurrence(o)` |

Các phép `Queue.offer(e)`, `Queue.poll()`, `Queue.peek()` khi áp dụng trên Deque tương đương **offerLast / pollFirst / peekFirst** theo hướng FIFO.

Ngoài ra `Deque.push(e)` tương đương thêm vào đầu, còn `Deque.pop()` lấy và xóa ở đầu (ném `NoSuchElementException` nếu rỗng), tạo phong cách **LIFO stack**.

~~~text
FIFO queue trên deque:
  offerLast(A), offerLast(B), offerLast(C)
  pollFirst() -> A, rồi B, rồi C

LIFO stack trên deque:
  push(A), push(B), push(C)   (thêm đầu)
  pop() -> C, rồi B, rồi A

Đọc hai đầu:
  pollFirst()  -> lấy phần tử đầu
  pollLast()   -> lấy phần tử cuối
~~~

Đừng nhầm stack push/pop của `Deque` với `java.util.Stack` legacy; `Deque` là interface hiện đại cho cả FIFO và LIFO. Tuy nhiên khi nhiều thread cùng thao tác, đừng dựa trên một thứ tự pop/poll ngẫu nhiên như một lịch chạy cố định.

## 7. Ví dụ 3 — dùng Deque làm FIFO, LIFO và xóa phần tử trùng

Tạo `LinkedDequeBasics.java` (Java 17+):

~~~java
import java.util.concurrent.ConcurrentLinkedDeque;

public class LinkedDequeBasics {
    public static void main(String[] args) {
        ConcurrentLinkedDeque<String> deque =
                new ConcurrentLinkedDeque<>();

        deque.offerLast("A");
        deque.offerLast("B");
        deque.offerFirst("urgent");

        System.out.println("first = " + deque.peekFirst());
        System.out.println("last = " + deque.peekLast());

        System.out.println("pop = " + deque.pop());
        System.out.println("pollLast = " + deque.pollLast());
        System.out.println("pollFirst = " + deque.pollFirst());
        System.out.println("empty = " + deque.isEmpty());

        deque.push("one");
        deque.push("two");
        System.out.println("stack pop = " + deque.pop());

        deque.clear();
        deque.offerLast("X");
        deque.offerLast("Y");
        deque.offerLast("X");

        System.out.println("remove last X = "
                + deque.removeLastOccurrence("X"));
        System.out.println("first after remove = " + deque.pollFirst());
        System.out.println("last after remove = " + deque.pollLast());
    }
}
~~~

Chạy:

~~~bash
javac LinkedDequeBasics.java
java LinkedDequeBasics
~~~

Stdout:

~~~text
first = urgent
last = B
pop = urgent
pollLast = B
pollFirst = A
empty = true
stack pop = two
remove last X = true
first after remove = X
last after remove = Y
~~~

Cả ba item đầu tiên được lấy đúng theo vị trí logic. Với phần stack, `push("two")` đặt nó trước `"one"` nên `pop()` trả `two`. Sau khi clear và thêm X,Y,X, `removeLastOccurrence("X")` xóa **X ở cuối**, không phải X ở đầu. Đây là chương trình chạy tuần tự để stdout xác định; tính đúng đắn khi nhiều writers đến từ contract concurrent collection, không phải từ việc ví dụ chỉ có main thread.

### Chọn hướng thao tác một cách nhất quán

Nếu consumer luôn lấy `pollFirst` nhưng producer dùng `offerFirst` cho “priority work”, các job mới có thể liên tục đứng trước job cũ và làm job cũ bị trì hoãn rất lâu. Đó là chính sách nghiệp vụ **LIFO/priority tương đối**, không còn FIFO chung.

Nếu cần thứ tự ưu tiên thực sự theo comparator, `PriorityBlockingQueue` hoặc một cơ chế scheduling chuyên biệt có thể phù hợp hơn. ConcurrentLinkedDeque không tự sắp xếp theo priority và không bảo đảm fairness của từng task.

## 8. Ví dụ 4 — hai consumer lấy ở hai đầu cùng một Deque

Một thiết kế có thể dùng consumer A lấy đầu và consumer B lấy cuối để phân tán công việc; mỗi `pollFirst`/`pollLast` thành công loại đúng một phần tử. Nhưng **đừng gọi đó là implementation của work-stealing ForkJoinPool**: work-stealing có protocol, placement, task queues và scheduling riêng, không chỉ “một deque với hai thread”.

Tạo `TwoEndedConsumers.java` (Java 17+):

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TwoEndedConsumers {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentLinkedDeque<Integer> deque =
                new ConcurrentLinkedDeque<>();
        for (int id = 1; id <= 10; id++) {
            deque.offerLast(id);
        }

        ConcurrentLinkedQueue<Integer> consumed =
                new ConcurrentLinkedQueue<>();

        Thread headWorker = new Thread(() -> {
            Integer id;
            while ((id = deque.pollFirst()) != null) {
                consumed.offer(id);
            }
        });
        Thread tailWorker = new Thread(() -> {
            Integer id;
            while ((id = deque.pollLast()) != null) {
                consumed.offer(id);
            }
        });

        headWorker.start();
        tailWorker.start();
        headWorker.join();
        tailWorker.join();

        List<Integer> ordered = new ArrayList<>(consumed);
        Collections.sort(ordered);

        System.out.println("consumed = " + ordered);
        System.out.println("remaining empty = " + deque.isEmpty());
    }
}
~~~

Chạy:

~~~bash
javac TwoEndedConsumers.java
java TwoEndedConsumers
~~~

Stdout:

~~~text
consumed = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
remaining empty = true
~~~

Để output không phụ thuộc OS scheduling, demo **pre-populate cả 10 item trước khi chạy consumer**, rồi sort kết quả sau `join`. Không in tên thread đã nhận item nào: một worker có thể lấy nhiều item trước khi worker kia được CPU, nhưng tập hợp cuối vẫn phải đủ 10 item, không trùng.

Lưu ý: nếu producer có thể thêm item **sau** lúc consumer thấy deque rỗng, vòng lặp trên vẫn có thể thoát sớm. Với hệ thống live, cần giao thức completion như phần queue phía trên hoặc một blocking structure phù hợp.

## 9. Không-blocking có ý nghĩa gì?

Các lớp này dùng thuật toán đồng bộ dựa trên các primitive như CAS để hỗ trợ thao tác concurrent mà không yêu cầu caller acquire một lock ứng dụng bao quanh `offer/poll`. Riêng `ConcurrentLinkedQueue` được Javadoc mô tả là dùng thuật toán **non-blocking** dựa trên công trình của Michael & Scott; `ConcurrentLinkedDeque` cũng cung cấp các concurrent insertion/removal/access operations an toàn.

Tuy nhiên:

- **Không-blocking khác không-chờ-vĩnh-viễn ở tầng ứng dụng.** Nếu bạn tự viết vòng chờ cho tới khi nhận item, chính vòng đó vẫn có thể đợi mãi khi producer không gửi.
- **Không-blocking khác wait-free.** Một thread riêng lẻ có thể bị trì hoãn/retry dưới contention hoặc scheduling; không có cam kết nó hoàn tất mọi operation sau số bước cố định.
- **Không-blocking khác không dùng CPU.** Một poll loop rất nóng có thể tiêu CPU, dù không bị mutex block.
- **Không-blocking khác không có memory cost.** Nodes/objects, garbage collection và cache-coherence traffic vẫn có chi phí.
- **Không-blocking khác không thể bị starvation.** Fairness giữa các consumer và tốc độ xử lý không được những lớp này bảo đảm.
- **Không-blocking khác atomic batch.** `offer(x)` và `offer(y)` là hai operation; thread khác có thể chen vào.

Vì vậy một concurrent queue có thể rất hiệu quả với workload phù hợp, nhưng benchmark thực tế vẫn cần xét contention, throughput, tail latency, allocation rate và payload retention.

## 10. Khi nào poll() trả null và tại sao busy spinning có thể nguy hiểm?

`ConcurrentLinkedQueue.poll()` hoặc `ConcurrentLinkedDeque.pollFirst()` trả `null` **ngay** khi không tìm thấy item ở thời điểm operation. Chúng không có `take()` để block chờ. Nếu viết:

~~~java
// Snippet cảnh báo, không phải chương trình hoàn chỉnh.
while (true) {
    Job job = queue.poll();
    if (job != null) {
        process(job);
    }
}
~~~

Một consumer không có job sẽ liên tục poll một queue rỗng; nó có thể tốn một lõi CPU đáng kể, gây can nhiễu các thread khác, và vẫn **không có termination protocol**.

Những hướng thiết kế:

1. Nếu nhiệm vụ đúng nghĩa **chờ đến khi có item**, dùng `BlockingQueue.take()` (hoặc `BlockingDeque.takeFirst/takeLast`).
2. Nếu cần poll không chặn, caller có thể **làm việc hữu ích khác** khi queue rỗng.
3. Nếu event loop phải poll, dùng một chiến lược yield/park/backoff có giới hạn và **cơ chế tín hiệu đánh thức** thích hợp; không lấy `Thread.sleep` như giải pháp duy nhất cho latency.
4. Nếu consumer cần kết thúc, dùng producer-completion signal/poison pill phù hợp, không chỉ kiểm tra `isEmpty`.
5. Nếu task có deadline, cân nhắc timed `poll(timeout, unit)` của **BlockingQueue**, không phải một overload có sẵn trên ConcurrentLinkedQueue.

### Bảng lựa chọn nhanh

| Nhu cầu | Cấu trúc phù hợp để cân nhắc |
| --- | --- |
| FIFO, non-blocking poll từ nhiều producers/consumers | `ConcurrentLinkedQueue` |
| Hai đầu, non-blocking, không cần capacity limit | `ConcurrentLinkedDeque` |
| FIFO, consumer muốn **block khi rỗng**, có optional capacity | `LinkedBlockingQueue` |
| FIFO bounded queue, backpressure cụ thể | `ArrayBlockingQueue` |
| Deque, consumer muốn block và có capacity | `LinkedBlockingDeque` |
| Handoff trực tiếp khi producer và consumer gặp nhau | `SynchronousQueue` |
| Trạng thái chia sẻ theo key | `ConcurrentHashMap` |
| Có priority ordering | `PriorityBlockingQueue` (cũng cần thiết kế overload riêng) |

Không có một cấu trúc “luôn nhanh nhất”. Kiểu thứ tự, chờ, capacity, fairness và khả năng drain là phần của yêu cầu nghiệp vụ.

## 11. `size()` O(n) và không được dùng như admission control

`ConcurrentLinkedQueue` và `ConcurrentLinkedDeque` dùng nodes liên kết, nên `size()` **không phải O(1)** như nhiều collection phổ biến. Việc tính size có thể phải duyệt các nodes: O(n). Nếu nhiều thread thêm/xóa đồng thời, số đếm nhận được có thể **không phản ánh một snapshot nhất quán**.

Hai hiểu nhầm phổ biến:

- “Queue thread-safe thì `size()` luôn là số chính xác của queue tại một mốc chung.” Không.
- “Tôi dùng `if (queue.size() < LIMIT) queue.offer(x)` là đã giới hạn queue.” Không.

Ngay cả nếu size chính xác ở thời điểm **đọc**, giữa check và offer, thread khác có thể thêm item. Với nhiều producers, limit bị vượt.

### Ví dụ 5 — hai producer cùng vượt giới hạn giả 1 item

Tạo `UnsafeCapacityCheck.java` (Java 17+):

~~~java
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class UnsafeCapacityCheck {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentLinkedQueue<Integer> queue =
                new ConcurrentLinkedQueue<>();
        CountDownLatch bothPassedCheck = new CountDownLatch(2);
        CountDownLatch allowEnqueue = new CountDownLatch(1);

        Runnable producer = () -> {
            if (queue.size() < 1) { // SAI: check rồi offer không atomic.
                bothPassedCheck.countDown();
                try {
                    allowEnqueue.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                queue.offer(1);
            }
        };

        Thread a = new Thread(producer);
        Thread b = new Thread(producer);
        a.start();
        b.start();

        bothPassedCheck.await();
        allowEnqueue.countDown();
        a.join();
        b.join();

        System.out.println("size after offers = " + queue.size());
        System.out.println("limit respected = " + (queue.size() <= 1));
    }
}
~~~

Chạy:

~~~bash
javac UnsafeCapacityCheck.java
java UnsafeCapacityCheck
~~~

Stdout khi không có interruption ngoài:

~~~text
size after offers = 2
limit respected = false
~~~

`bothPassedCheck.await()` buộc **cả hai thread kiểm tra size=0 trước khi ai được offer**. Sau khi mở latch, cả hai offer thành công, queue chứa 2 item mặc dù “giới hạn” dự kiến chỉ là 1. Kết quả là lỗi *check-then-act*, không phải lỗi của CAS bên trong `ConcurrentLinkedQueue`.

**Giải pháp đúng:** Khi cần capacity hữu hạn và cơ chế từ chối/chờ, sử dụng `ArrayBlockingQueue(cap)`, `LinkedBlockingQueue(cap)` hoặc `LinkedBlockingDeque(cap)`. Nếu buộc phải dùng concurrent linked structure cùng `Semaphore` để giới hạn in-flight, phải thiết kế acquisition/release-once cho toàn lifecycle (producer exception, dequeue, cancellation, shutdown), không chỉ dùng `size`.

`size()` có thể phù hợp cho **debug/monitoring thô** khi hiểu chi phí và độ chính xác, đặc biệt khi collection đã **quiescent** (không còn concurrent mutation). Nó không phải một primitive đồng bộ.

## 12. `peek()` rồi `poll()`: hai operation không phải một transaction

Một lỗi khá phổ biến:

~~~java
// MÃ SAI về check-then-act khi có nhiều consumer.
if (queue.peek() != null) {
    Job job = queue.poll();
    process(job); // job vẫn có thể null!
}
~~~

`peek()` và `poll()` riêng lẻ an toàn, nhưng consumer khác có thể lấy item trong khoảng giữa. `peek()` không giữ chỗ/phong tỏa phần tử cho caller. Cách đúng hơn:

~~~java
Job job = queue.poll();
if (job != null) {
    process(job);
}
~~~

Hãy gắn điều kiện xử lý với **chính kết quả poll** thay vì suy luận từ một peek cũ.

### Ví dụ 6 — chứng minh race peek-then-poll bằng latch

Tạo `PeekThenPollRace.java` (Java 17+):

~~~java
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class PeekThenPollRace {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentLinkedQueue<Integer> queue =
                new ConcurrentLinkedQueue<>();
        queue.offer(42);

        CountDownLatch bothPeeked = new CountDownLatch(2);
        CountDownLatch allowPoll = new CountDownLatch(1);
        AtomicInteger gotItem = new AtomicInteger();
        AtomicInteger gotNull = new AtomicInteger();

        Runnable consumer = () -> {
            if (queue.peek() != null) {
                bothPeeked.countDown();
                try {
                    allowPoll.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                Integer item = queue.poll();
                if (item == null) {
                    gotNull.incrementAndGet();
                } else {
                    gotItem.incrementAndGet();
                }
            }
        };

        Thread a = new Thread(consumer);
        Thread b = new Thread(consumer);
        a.start();
        b.start();

        bothPeeked.await();
        allowPoll.countDown();
        a.join();
        b.join();

        System.out.println("got item = " + gotItem.get());
        System.out.println("got null = " + gotNull.get());
    }
}
~~~

Chạy:

~~~bash
javac PeekThenPollRace.java
java PeekThenPollRace
~~~

Stdout:

~~~text
got item = 1
got null = 1
~~~

Hai consumer **đều thấy 42 trong peek**, nhưng sau đó chỉ một consumer có thể thành công khi poll; consumer kia nhận null. Nhờ latch điều khiển thời điểm poll, ví dụ không phụ thuộc “đôi khi scheduler may mắn tạo race”. Điều cần nhớ: **mỗi method thread-safe không hợp thành một transaction thread-safe ở tầng nghiệp vụ**.

## 13. Iterator và Spliterator weakly consistent

Hai lớp cung cấp iterator/spliterator có khả năng chạy trong khi collection bị thread khác sửa. Javadoc gọi là **weakly consistent**:

- Không fail-fast bằng `ConcurrentModificationException` chỉ vì có đồng thời add/remove.
- Có thể quan sát một số thay đổi xảy ra trong lúc iterator đang chạy.
- Không có nghĩa iterator là snapshot cố định tại lúc được tạo.
- Không bảo đảm nhìn thấy **mọi** item được thêm sau lúc iterator được tạo.
- Không được coi traversal là nguyên tử hoặc có một thời điểm duy nhất mô tả toàn bộ kết quả.
- `ConcurrentLinkedDeque.descendingIterator()` cho phép duyệt hướng từ cuối về đầu nhưng cũng weakly consistent.
- `spliterator()` cung cấp một cách duyệt song song/partition traversal phù hợp hợp đồng, không biến source đang mutate thành snapshot toàn phần.

Một ví dụ không nên viết test khẳng định số phần tử luôn chính xác trong lúc đang enqueue:

~~~java
// Snippet minh họa, không độc lập.
Iterator<Integer> it = queue.iterator();
queue.offer(99);
while (it.hasNext()) {
    System.out.println(it.next()); // Không kết luận chắc có/không thấy 99.
}
~~~

Với `ConcurrentLinkedQueue`, contract iterator cung cấp thêm bảo đảm về những phần tử đã có trong queue từ khi iterator được tạo; tuy nhiên điều này **không** trao quyền dùng iterator làm một snapshot toán học của toàn bộ dữ liệu đang biến đổi. Đọc kỹ phạm vi bảo đảm trong Javadoc thay vì gọi mọi hành vi là “hoàn toàn ngẫu nhiên”.

### Khi nào muốn snapshot thật?

- Nếu **mọi producers/consumers đã dừng**, sao chép/duyệt queue khi không còn mutation cho kết quả ổn định hơn; ví dụ sau `join` tất cả threads.
- Nếu muốn **một transaction snapshot** giữa lúc có writers, concurrent queue một mình không đủ; cần một protocol khóa/snapshot/versioning hoặc một data model khác.
- Nếu chỉ cần monitoring gần đúng, có thể lấy số liệu approximate và đánh dấu là estimate.
- Nếu cần tổng tiền/inventory/billing nhất quán, không dùng `forEach` trên queue đang mutate làm bằng chứng cân đối giao dịch.

### Ví dụ 7 — duyệt snapshot sau khi worker đã dừng

Tạo `StableQueueTraversal.java` (Java 17+):

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class StableQueueTraversal {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentLinkedQueue<Integer> queue =
                new ConcurrentLinkedQueue<>();

        Thread producer = new Thread(() -> {
            queue.offer(1);
            queue.offer(2);
            queue.offer(3);
        });
        producer.start();
        producer.join();

        List<Integer> snapshot = new ArrayList<>(queue);
        System.out.println("snapshot = " + snapshot);
        System.out.println("queue unchanged = " + !queue.isEmpty());
    }
}
~~~

Chạy:

~~~bash
javac StableQueueTraversal.java
java StableQueueTraversal
~~~

Stdout:

~~~text
snapshot = [1, 2, 3]
queue unchanged = true
~~~

Đây là snapshot ổn định vì **producer đã `join` và không có thread khác sửa queue**. Constructor `new ArrayList<>(queue)` tự nó không tạo transaction trước writers đang chạy. `join` còn tạo quan hệ happens-before từ producer đã hoàn tất tới main, bên cạnh semantics publication của concurrent queue.

## 14. Bulk operations không nguyên tử trên toàn collection

Các thao tác như `addAll`, `removeIf`, `forEach`, `clear` hoặc duyệt và xử lý hàng loạt có thể tương tác với nhiều phần tử. Javadoc cảnh báo các bulk operations thêm/xóa/quan sát nhiều elements **không được bảo đảm atomic** trong presence of concurrent modifications.

Ví dụ:

~~~java
// MÃ MINH HỌA, không có transaction batch.
queue.addAll(List.of("A", "B", "C"));
~~~

Nếu consumer đang chạy, nó có thể quan sát một phần các item đã được thêm trước khi `addAll` trả về. Không kết luận rằng “một batch A,B,C xuất hiện đồng thời như một transaction”.

Tương tự, `removeIf(predicate)` có thể xem/xóa các phần tử trong một khoảng thời gian; những item được producer thêm đồng thời có thể nằm ngoài đợt duyệt hoặc chưa được xử lý theo cách bạn mong muốn. `clear()` không phải cơ chế “đóng queue” và không khóa producers khỏi enqueue thêm.

Với những trường hợp cần **atomic batch publication**, có thể đặt cả batch vào **một immutable holder object** (ví dụ `Queue<List<Job>>`) rồi offer holder đó thành một phần tử; cần thiết kế ai xử lý batch, idempotency, memory footprint, failure policy và mức atomic ở tầng nghiệp vụ. Một queue operation chỉ đảm bảo tính nguyên tử khi **đưa một reference tới batch** vào queue, không thực hiện transaction các side effects bên trong batch.

## 15. Memory visibility và happens-before

Concurrent collections được thiết kế không chỉ để tránh corrupt structure mà còn để cung cấp hiệu ứng **memory consistency** cho việc chuyển giao object qua collection.

Theo Java SE 17 Javadoc của `ConcurrentLinkedQueue` và `ConcurrentLinkedDeque`: những hành động của một thread **trước khi đặt object vào collection** *happen-before* những hành động của thread khác **sau khi access hoặc remove chính element đó** từ collection.

~~~text
Producer:
  Task t = new Task(7, "ready");
  queue.offer(t);
      |         publication qua concurrent collection
      +-------------- happens-before --------------+
                                                   |
Consumer:                                          v
  Task received = queue.poll();
  // Khi nhận được t, các ghi trước offer được publish phù hợp.
~~~

Điều này rất hữu ích cho producer–consumer. Không cần giữ cùng một `synchronized` ở bên ngoài **chỉ để publish một object đã khởi tạo đầy đủ**, nếu mọi trao đổi qua concurrent collection đúng contract.

Nhưng cần phân biệt:

- **Safe publication** của state đã chuẩn bị trước enqueue.
- **Mutation sau publication** của object ấy.

Khi producer `offer` một `ArrayList` rồi tiếp tục sửa list trong lúc consumer đọc, queue **không** bảo vệ các sửa đổi đó. Tương tự, việc một element là `AtomicReference<Mutable>` không làm object mutable bên trong trở nên thread-safe. Nên dùng immutable values, defensive copy, ownership transfer không sửa thêm, hoặc synchronization riêng cho mutable state.

### Ví dụ 8 — chuyển record immutable qua queue

Tạo `QueueSafePublication.java` (Java 17+):

~~~java
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class QueueSafePublication {
    record Message(int id, String payload) {}

    public static void main(String[] args) throws InterruptedException {
        ConcurrentLinkedQueue<Message> queue =
                new ConcurrentLinkedQueue<>();
        CountDownLatch published = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            Message message = new Message(7, "ready");
            queue.offer(message);
            published.countDown();
        });

        producer.start();
        published.await();

        Message received = queue.poll();
        System.out.println("id = " + received.id());
        System.out.println("payload = " + received.payload());

        producer.join();
    }
}
~~~

Chạy:

~~~bash
javac QueueSafePublication.java
java QueueSafePublication
~~~

Stdout:

~~~text
id = 7
payload = ready
~~~

`Message` là record chứa hai component bất biến: một primitive và một `String` immutable. Producer tạo record đầy đủ **trước offer**, consumer nhận reference qua queue. Trong ví dụ, `CountDownLatch` chỉ điều phối thời điểm để output chắc chắn có item; ngay cả khi consumer poll liên tục và nhận thành công không dùng latch, semantics của concurrent queue vẫn hỗ trợ publication của những ghi xảy ra trước offer.

Đừng lấy code này làm bằng chứng rằng queue tự đảm bảo atomic transaction giữa **nhiều** messages hoặc tự phục hồi khi producer bị crash trước offer.

## 16. Queue rỗng, hoàn tất producer và graceful shutdown

Không có `close()` chung trên `ConcurrentLinkedQueue`/`ConcurrentLinkedDeque` để mọi consumer đang poll tự nhận end-of-stream. Muốn dừng có trật tự, cần định nghĩa **protocol hoàn tất**.

Một mô hình:

~~~text
RUNNING
  | producer gửi work
  v
STOP_ACCEPTING
  | ngừng nhận work mới; đợi producer hiện hữu enqueue xong
  v
PRODUCERS_FINISHED
  | gửi tín hiệu STOP hoặc bật flag completion đúng quy tắc
  v
DRAINING
  | consumer xử lý work đã nhận
  v
CONSUMERS_FINISHED
  | join/ack tất cả consumers; kiểm tra failure/in-flight
  v
TERMINATED
~~~

Có vài lựa chọn:

1. **Completion flag:** Coordinator đợi producer rồi đặt `AtomicBoolean done=true`. Consumer không thoát khi poll null **trước** lúc flag true; sau khi flag true, queue rỗng có thể là điều kiện kết thúc. Ví dụ `ConcurrentQueueWorkers` dùng cách này.
2. **Poison-pill sentinel:** Sau khi mọi producer kết thúc, enqueue các STOP elements theo protocol; nếu N consumers cùng poll, cần tính số tokens/ownership và cách bảo đảm mọi consumer nhận được tín hiệu.
3. **BlockingQueue:** Khi consumer cần chờ thay vì poll vòng, dùng `BlockingQueue.take()` cùng sentinel/completion logic. Cần xử lý capacity đầy và cancellation khi shutdown.
4. **Quản lý in-flight:** Consumer có thể đã poll item ra khỏi queue nhưng chưa xử lý xong. `isEmpty()` không phải bằng chứng mọi task đã hoàn tất; cần `join`/`Future.get`/ack từ tasks.

Khi một consumer nhận STOP và thoát nhưng vẫn còn item được producer thêm **sau STOP**, item có thể bị bỏ lại. Do đó phải đóng admission/đợi producer trước khi phát signal. Đây là giao thức **ứng dụng**, không phải tính chất tự động của concurrent queue.

## 17. Backpressure và rủi ro unbounded memory

Một điểm phân biệt lớn với bounded blocking queue:

- `ConcurrentLinkedQueue.offer(e)` không có limit cấu hình; đối với item hợp lệ, nó được thiết kế để enqueue thay vì trả `false` khi đã tới một capacity nghiệp vụ.
- `ConcurrentLinkedDeque.offerFirst/offerLast(e)` cũng không có bound.
- Nếu producer nhanh hơn consumer lâu dài, object tồn đọng có thể tăng, làm heap retention, GC pressure và latency tăng.
- Task có thể giữ reference tới payload, closure hoặc cấu trúc lớn; bộ nhớ tiêu thụ không chỉ bằng chi phí node.
- Nếu memory cạn, JVM có thể gặp `OutOfMemoryError`; đây không phải “rejection policy” dễ khôi phục.
- `isEmpty()` và `size()` không cung cấp admission control nguyên tử.

Ví dụ đơn giản: producer trung bình gửi 10.000 item/s trong khi consumer chỉ xử lý được 8.000 item/s. Backlog tăng khoảng 2.000 item/s trong giai đoạn ấy; sau 5 phút có thể tồn đọng khoảng 600.000 item, trước khi xét load variation và retries. Đây là phép tính định hướng, không phải dự báo hard guarantee.

**Hướng giải quyết:** dùng bounded structure khi cần hạn chế work đang chờ; đặt rate limiting, explicit rejection, backpressure, giới hạn in-flight, deadlines hoặc load shedding có chủ đích. “Non-blocking” tự nó không ngăn overload.

### Không dùng ConcurrentLinkedDeque làm priority queue vô hạn

Một số hệ thống `offerFirst(urgent)` để ưu tiên job mới, `offerLast(normal)` cho job thường. Cách này đơn giản nhưng nếu urgent đến liên tục, normal có thể chờ rất lâu. Nó cũng không hỗ trợ mức priority khác nhau bằng comparator, deadline scheduling hay fairness giữa categories. Cần đánh giá work starvation và SLA trước khi chọn thiết kế.

## 18. Khi nào chọn BlockingQueue hoặc BlockingDeque?

`ConcurrentLinkedQueue` và `ConcurrentLinkedDeque` **không có** các method `put`, `take` hay timed `offer/poll` do `BlockingQueue` cung cấp. Nếu điều bạn cần là producer **chờ khi đầy** và consumer **ngủ khi rỗng**, chọn loại có blocking semantics phù hợp.

| Trường hợp | Công cụ dễ phù hợp |
| --- | --- |
| Một consumer không muốn spin khi rỗng | `LinkedBlockingQueue.take()` |
| Cần capacity cố định, kiểm soát memory | `ArrayBlockingQueue(capacity)` |
| Cần hai đầu và blocking wait | `LinkedBlockingDeque(capacity)` |
| Cần chờ tối đa 1 giây rồi timeout | `BlockingQueue.poll(1, TimeUnit.SECONDS)` |
| Không cần blocking, poll nhanh, nhiều thread | `ConcurrentLinkedQueue.poll()` |
| Non-blocking hai đầu với head/tail semantics | `ConcurrentLinkedDeque.pollFirst/pollLast` |

### Ví dụ 9 — BlockingQueue thay thế polling nóng

Tạo `BlockingQueueConsumer.java` (Java 17+):

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class BlockingQueueConsumer {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);

        Thread worker = new Thread(() -> {
            try {
                String first = queue.take();
                String second = queue.take();
                System.out.println("received = " + first + "," + second);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        worker.start();
        queue.put("A");
        queue.put("B");
        worker.join();
    }
}
~~~

Chạy:

~~~bash
javac BlockingQueueConsumer.java
java BlockingQueueConsumer
~~~

Stdout:

~~~text
received = A,B
~~~

Nếu queue rỗng, `take()` đợi cho tới khi producer gửi item hoặc thread bị interrupt. Nếu bounded queue đầy, `put` cũng có thể đợi capacity. Đây là **blocking producer–consumer protocol**, không phải hoạt động của `ConcurrentLinkedQueue`.

Không nên dùng ví dụ này cho một consumer vô hạn mà không có phương án shutdown: `take()` có thể đợi mãi nếu không còn producer. Cần sentinel/interrupt/cancellation đúng lifecycle.

## 19. Deque và cách dùng trong work-stealing có giới hạn

Các work-stealing schedulers thường có khái niệm worker sở hữu một deque công việc, còn worker khác có thể lấy bớt nhiệm vụ từ đầu đối diện. `ConcurrentLinkedDeque` cung cấp thao tác hai đầu thread-safe, nhưng **không đủ để tự tạo một ForkJoinPool**:

- Một work-stealing scheduler cần quyết định task placement, ownership và queue ordering.
- Cần cơ chế đánh thức workers khi có work, tránh busy spinning không kiểm soát.
- Cần completion counting, task dependencies, cancellation và exception reporting.
- Cần chính sách bound memory và workload admission.
- Cần quản lý lifecycle của executor và worker thread.
- Cần kiểm soát fairness, starvation, ưu tiên và thời điểm shutdown.
- Cần đo contention, locality, cache misses và overhead của việc đưa/lấy task.

Trong ứng dụng, nếu chỉ cần chạy một thuật toán Fork/Join đệ quy trên CPU, hãy cân nhắc `ForkJoinPool` và `RecursiveTask` thay vì xây scheduler riêng từ một concurrent deque. `ConcurrentLinkedDeque` hữu ích khi **điều khiển hai đầu là yêu cầu trực tiếp của data flow**, không phải vì tên gọi trông giống work-stealing.

## 20. Bulk iteration, toArray và snapshot semantics

`toArray()` hoặc `new ArrayList<>(queue)` sao chép những phần tử được duyệt ở một thời điểm thực thi. Nhưng nếu producers/consumers vẫn sửa collection:

- Chúng **không** tự tạo một transaction snapshot.
- Có thể phản ánh những trạng thái ở các thời điểm khác nhau.
- Thứ tự và số lượng có thể biến đổi khi traversal diễn ra.
- Các object được copy là **references**, không deep clones. Nếu object mutable vẫn bị writer khác sửa, bản copy không làm chúng immutable.
- Iterator weakly consistent không đảm bảo một snapshot toàn cục phù hợp để làm financial reconciliation.

Nếu cần chụp snapshot để debug, đợi một điểm *quiescent* do coordinator xác nhận hoặc dùng data structure/protocol khác. Không dùng `size` rồi `toArray` và coi hai kết quả phải luôn khớp trong lúc concurrent mutation.

### `descendingIterator()` trên ConcurrentLinkedDeque

`deque.descendingIterator()` duyệt ngược từ tail về head. Ví dụ với deque chứa [A,B,C] và không có concurrent writer, chiều forward là A,B,C; chiều descending là C,B,A. Khi writer thêm/xóa đồng thời, chiều duyệt vẫn tuân contract weak consistency chứ không tạo snapshot.

Nếu cần thực thi business logic “xử lý mọi element đúng một lần” giữa lúc concurrent update, **iterator không phải consumer queue protocol**. Dùng `pollFirst`/`pollLast` để lấy quyền sở hữu item và xử lý, cùng ack/completion policy.

## 21. Object mutable trong queue: thread-safe container không đủ

Đây là ví dụ lỗi:

~~~java
// Snippet minh họa không an toàn nếu có mutation cạnh tranh.
class MutableTask {
    int attempts;
}

ConcurrentLinkedQueue<MutableTask> queue =
        new ConcurrentLinkedQueue<>();

MutableTask task = new MutableTask();
queue.offer(task);
task.attempts++; // Producer tiếp tục mutate sau publication.
~~~

Nếu consumer đồng thời đọc `attempts` hoặc cũng tăng `attempts`, các truy cập này có thể là data race. `offer` đảm bảo publication của trạng thái **trước** offer theo contract, không tự bảo vệ mutation **sau** offer.

Cách làm:

- Ưu tiên **record/immutable task description** như `Message(int id,String payload)`.
- Nếu dùng object mutable, thực hiện **ownership transfer** rõ: producer không sửa sau khi enqueue; consumer sở hữu object khi poll thành công.
- Nếu cùng object vẫn được nhiều threads dùng, đồng bộ field bên trong bằng lock, atomics hoặc protocol đúng.
- Nếu consumer cần trả kết quả cho producer, dùng một kênh/result `Future`/ack phù hợp, không để cả hai tự sửa field thường không khóa.
- Tránh tạo hai queue chứa cùng reference tới mutable object rồi nghĩ mỗi queue bảo vệ độc lập.

### Đừng nhầm visibility với atomicity của workflow

Even if a consumer correctly sees `attempts=0` at time of receiving item, việc thêm 1 vào attempts cùng lúc với thread khác vẫn có thể lost update. Queue không biến `attempts++` thành atomic RMW. Dùng `AtomicInteger`/lock/immutable replacement khi cần.

## 22. Test concurrency: kiểm chứng invariant, tránh phụ thuộc scheduler

Các ví dụ trong bài chọn đầu ra cố định bằng một trong các cách:

- **Chạy tuần tự:** ví dụ API cơ bản, để thứ tự FIFO/LIFO hiển nhiên.
- **Join tất cả threads:** tổng kết sau khi không còn producer/consumer tác động.
- **Tạo ID độc nhất và sort kết quả:** không suy đoán consumer nào được phần tử nào.
- **CountDownLatch:** điều phối chính xác một interleaving cho lỗi check-then-act.
- **Immutable record:** tránh data race ở state được trao qua queue.
- **CountDownLatch completion flag:** phân biệt queue tạm rỗng với producers đã kết thúc.

Nhưng hãy nhớ các demo có `await()` không timeout được viết để làm rõ semantics trên đường chạy bình thường; **test suite production nên dùng timed waits**, `try/finally` cleanup, và fail-fast nếu thread không tiến triển. Không dựa vào `Thread.sleep(100)` rồi đoán chắc race đã xảy ra.

### Những metric đáng theo dõi

- Enqueued/sec và dequeued/sec, tính cả từng đầu deque khi có ý nghĩa.
- End-to-end work latency (submit → start → complete), không chỉ poll latency.
- Pending/in-flight counts qua counter/ack protocol riêng nếu cần chính xác.
- Heap retained bởi queued payloads, node allocations và GC.
- CPU usage của consumer khi queue rỗng, đặc biệt poll loops.
- Duplicate processing/missing job hoặc số ack theo nghiệp vụ.
- Cancellation, retry, dead-letter và shutdown duration.
- Contention và tail latency trong workload thật.

Không tự cài một exact counter bằng `size()` mỗi khi hiển thị dashboard; phép duyệt O(n) trên queue dài có thể tự tạo chi phí đáng kể và vẫn không đảm bảo số đếm đồng bộ với producers.

## 23. Các lỗi thường gặp

| Lỗi | Hệ quả | Cách làm đúng |
| --- | --- | --- |
| Dùng `ArrayDeque` thường với nhiều writer | Data race/corruption | `ConcurrentLinkedDeque` hoặc lock phù hợp |
| Hiểu `ConcurrentLinkedQueue.poll()` như `take()` | Consumer spin hoặc thoát sớm | Dùng blocking collection khi cần chờ |
| Enqueue `null` làm sentinel | `NullPointerException` | Sentinel non-null rõ kiểu |
| Dựa vào `queue.size() < capacity` | Race, vượt hạn mức, O(n) | Bounded queue hoặc admission protocol |
| `if (peek()!=null) poll()` rồi tin non-null | Consumer khác có thể lấy mất | Kiểm tra chính kết quả `poll()` |
| Dùng `forEach`/`toArray` làm snapshot chính xác | Weakly consistent, concurrent modifications | Quiescent point hoặc snapshot protocol |
| Nghĩ `addAll` xuất hiện nguyên tử | Consumer có thể thấy một phần batch | Đưa một immutable batch holder vào một slot hoặc lock thích hợp |
| Một thread sửa object sau khi `offer` | Data race với consumer | Immutable values hoặc ownership transfer |
| Cho rằng FIFO tức completion FIFO nhiều consumers | Xử lý hoàn thành khác thứ tự dequeue | Ack/result ordering nếu cần |
| Poll vòng vô hạn khi rỗng | Tốn CPU và không có termination | `BlockingQueue.take()` hoặc wait/notification protocol |
| Dùng unbounded queue với producer quá nhanh | Heap, GC, latency tăng | Capacity, backpressure, deadlines |
| Tin deque tự sắp xếp priority | Job thường có thể bị đói | Priority/scheduling cấu trúc phù hợp |
| Dựa vào iterator để “claim job” | Có thể xử lý cùng item bằng nhiều callers | `poll` để chuyển quyền sở hữu |
| Dùng `clear` như close/shutdown | Producer vẫn có thể enqueue lại | Stop admission + completion protocol |
| Không phân biệt active với queued tasks | `isEmpty` không có nghĩa xử lý xong | Count in-flight, await ack |
| Nghĩ non-blocking = wait-free | Không có bound số bước cho từng caller | Hiểu progress guarantee và contention |
| Tự implement work-stealing chỉ với CLDeque | Thiếu scheduler/lifecycle/fairness | Dùng ForkJoinPool khi phù hợp |

## 24. Câu hỏi tự kiểm tra

**1.** `ConcurrentLinkedQueue` có phải FIFO không?  
**Đáp án:** Có, phần tử được thêm vào tail và poll ở head. Nhưng nếu nhiều producers cùng enqueue, thứ tự xen kẽ giữa họ không cố định.

**2.** `poll()` trên queue rỗng chặn cho tới khi có item?  
**Đáp án:** Không. Nó trả `null` ngay; dùng `BlockingQueue.take()` nếu cần chờ.

**3.** `ConcurrentLinkedQueue.offer(null)` có được không?  
**Đáp án:** Không, ném `NullPointerException`. Null được dùng làm dấu hiệu rỗng của poll/peek.

**4.** Tại sao `size()` không phải primitive giới hạn queue?  
**Đáp án:** Nó là O(n), có thể không chính xác khi đang có concurrent modifications, và check-then-offer không atomic.

**5.** Có thể chắc rằng `peek()!=null` ngay sau đó `poll()!=null` không?  
**Đáp án:** Không. Consumer khác có thể poll phần tử đó trước; phải kiểm tra trực tiếp giá trị poll.

**6.** `ConcurrentLinkedDeque.push(x)` và `pop()` dùng đầu nào?  
**Đáp án:** Đầu first/head; tạo LIFO stack. `offerLast` và `pollFirst` tạo FIFO.

**7.** Một consumer pollFirst và một consumer pollLast trên deque có thể nhận cùng một item không?  
**Đáp án:** Một phần tử cụ thể không thể bị cả hai poll thành công đồng thời; mỗi successful removal lấy một element khỏi deque.

**8.** Iterator đang chạy có ném `ConcurrentModificationException` khi producer tiếp tục add?  
**Đáp án:** Không theo contract iterator weakly consistent của hai concurrent collections; nhưng không tạo snapshot nguyên tử.

**9.** `addAll(List.of(a,b,c))` có nguyên tử trên cả batch không?  
**Đáp án:** Không có bảo đảm đó; consumer có thể nhìn thấy một phần khi operation còn đang thực hiện.

**10.** `ConcurrentLinkedQueue` có bảo đảm memory visibility từ producer đến consumer qua element không?  
**Đáp án:** Có, hành động trước khi producer đặt element vào queue happens-before hành động sau khi consumer access/remove element ấy theo API; mutation sau publication vẫn cần protocol riêng.

**11.** Khi `queue.isEmpty()` true, có thể kết luận pipeline đã done không?  
**Đáp án:** Không. Producer có thể gửi thêm, hoặc consumer đã lấy item nhưng còn đang xử lý. Cần completion/ack protocol.

**12.** Một application muốn producer đợi khi queue đầy, consumer đợi khi queue rỗng và giới hạn 128 item. Nên chọn gì?  
**Đáp án:** Ví dụ `new ArrayBlockingQueue<>(128)` hoặc `LinkedBlockingQueue<>(128)`, với `put/take` và shutdown protocol; không tự đặt limit bằng `ConcurrentLinkedQueue.size`.

**13.** Khi task mới “urgent” luôn được `offerFirst` và consumer `pollFirst`, các task “normal” có thể bị trì hoãn dài không?  
**Đáp án:** Có. Đây không phải priority/fairness scheduler hoàn chỉnh; cần đánh giá starvation.

**14.** Queue thread-safe có khiến `job.count++` trên object lấy ra cũng thread-safe?  
**Đáp án:** Không. Phải bảo vệ mutable object hoặc giao quyền sở hữu rõ.

## Tài liệu tham khảo chính thức

- [`ConcurrentLinkedQueue` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html): FIFO, non-blocking, weakly consistent iterator, size O(n), bulk operations và memory consistency.
- [`ConcurrentLinkedDeque` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedDeque.html): deque hai đầu, non-blocking concurrency, weak iterators, size và bulk semantics.
- [`Queue` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html): `offer/poll/peek`, `add/remove/element`.
- [`Deque` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Deque.html): first/last, stack và queue mappings.
- [`BlockingQueue` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html): blocking/timed methods và capacity.
- [`ArrayBlockingQueue` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html): FIFO bounded queue.
- [`LinkedBlockingDeque` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingDeque.html): blocking hai đầu, optional capacity.
- [Java Language Specification `17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Java Memory Model và happens-before.

**Ghi nhớ:** `ConcurrentLinkedQueue` giải bài toán FIFO concurrent không blocking; `ConcurrentLinkedDeque` giải bài toán thao tác concurrent ở hai đầu. Cả hai **không** phải blocking queues, **không** có capacity limit tự động, **không** biến `size/iterator/bulk operation` thành snapshot nguyên tử, và **không** bảo vệ mutation của object được chứa. Khi yêu cầu liên quan shutdown, quota hoặc nhiều bước, hãy thiết kế protocol rõ ràng ngoài bản thân collection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-007">ArrayBlockingQueue so với LinkedBlockingQueue</a>

<details>
<summary>Xem chi tiết</summary>

# ArrayBlockingQueue so với LinkedBlockingQueue trong Java Concurrency

## 1. Bài toán: chọn hàng đợi blocking phù hợp

Trong mô hình **producer–consumer**, producers sinh công việc rồi đưa vào một hàng đợi dùng chung; consumers lấy công việc ra để xử lý. Khi producers nhanh hơn consumers, hệ thống cần quyết định điều gì xảy ra với những công việc chưa xử lý: **chờ**, **từ chối**, hay **tích lũy backlog**.

Java cung cấp hai lựa chọn rất phổ biến trong `java.util.concurrent`:

- `ArrayBlockingQueue<E>`: hàng đợi **FIFO, dung lượng cố định**, lưu phần tử bằng một mảng, có tùy chọn fairness cho các threads đang chờ.
- `LinkedBlockingQueue<E>`: hàng đợi **FIFO, dựa trên linked nodes**, có thể chọn giới hạn dung lượng; **nếu không truyền capacity**, giới hạn danh nghĩa là `Integer.MAX_VALUE`.

Cả hai đều implement `BlockingQueue<E>`; đều hỗ trợ `put()` và `take()` có thể **chờ** khi hàng đợi đầy/rỗng. Điểm khác nhau quan trọng nằm ở **cấu trúc lưu trữ, cách giới hạn backlog, chi phí cấp phát, concurrency giữa producer/consumer và ảnh hưởng đến thread pool**.

Sau bài này, bạn sẽ:

1. Phân biệt bản chất **bounded** của ArrayBlockingQueue với **optionally bounded** của LinkedBlockingQueue.
2. Sử dụng đúng `add`, `offer`, `put`, `offer(timeout)`, `poll`, `take` và `poll(timeout)`.
3. Hiểu cả hai là FIFO **trong phạm vi thứ tự phần tử vào/ra queue**, không đảm bảo thứ tự hoàn thành của nhiều workers.
4. Giải thích fairness của ArrayBlockingQueue, các locks và chi phí lưu trữ của hai implementation.
5. Biết vì sao LinkedBlockingQueue không giới hạn thực tế có thể gây backlog/heap pressure và khiến `maximumPoolSize` của ThreadPoolExecutor không phát huy.
6. Áp dụng backpressure, bounded capacity, timeout, cancellation, poison pill và shutdown producer–consumer.
7. Chạy các ví dụ Java 17+ minh họa dung lượng/FIFO, giao nhận blocking, interruption và tương tác ThreadPoolExecutor.
8. Lựa chọn theo benchmark thay vì khẳng định một queue luôn nhanh hơn.

**Phạm vi:** Java SE/JVM concurrency nói chung, không phụ thuộc framework. Ví dụ dùng JDK 17+.

## 2. BlockingQueue là hợp đồng chung

`BlockingQueue<E>` mở rộng `Queue<E>` để hỗ trợ các thao tác thêm/lấy phần tử khi điều kiện chưa cho phép thực hiện ngay. Có **bốn cách phản ứng** với queue đầy hoặc rỗng.

| Thao tác | Ném exception | Giá trị đặc biệt tức thì | Chờ vô thời hạn | Chờ có timeout |
|---|---|---|---|---|
| Thêm phần tử | `add(e)` | `offer(e)` | `put(e)` | `offer(e, timeout, unit)` |
| Lấy và xóa phần tử | `remove()` | `poll()` | `take()` | `poll(timeout, unit)` |
| Chỉ xem đầu queue | `element()` | `peek()` | Không có trong interface | Không có trong interface |

Ý nghĩa cần nhớ:

- `add(e)`: nếu không chèn được do giới hạn dung lượng, có thể ném `IllegalStateException`. Không dùng như cách bình thường để chờ chỗ.
- `offer(e)`: thử ngay. Trả `true` nếu đã thêm; trả `false` nếu **hiện không còn chỗ**, không chờ.
- `put(e)`: có thể block cho tới khi có chỗ; ném `InterruptedException` nếu thread bị interrupt trong lúc chờ theo hợp đồng.
- `offer(e, timeout, unit)`: chờ tối đa khoảng thời gian cho phép; trả false nếu không có chỗ trong hạn; cũng có thể ném InterruptedException.
- `remove()`: lấy đầu queue, ném `NoSuchElementException` nếu rỗng.
- `poll()`: lấy đầu queue hoặc trả `null` nếu rỗng, không chờ.
- `take()`: chờ cho tới khi có phần tử, hoặc ném InterruptedException khi bị yêu cầu hủy.
- `poll(timeout, unit)`: chờ tối đa khoảng thời gian, trả null khi không có phần tử trong hạn.

**Không thêm `null` vào BlockingQueue.** Cả hai class ném `NullPointerException` cho phần tử null. Vì vậy `poll()==null` được dùng để biểu thị **không có phần tử** thay vì một giá trị hợp lệ của queue.

~~~java
BlockingQueue<String> queue = new ArrayBlockingQueue<>(2);

queue.put("task-A");                   // có thể chờ khi đầy
boolean accepted = queue.offer("task-B"); // thử ngay
String first = queue.take();            // có thể chờ khi rỗng
String second = queue.poll();           // trả null nếu rỗng
~~~

Đây là **đoạn trích trong một method**, cần imports phù hợp và khai báo/handle `InterruptedException` cho `put/take`. Ví dụ không có threads cạnh tranh, nhưng những method này vẫn giữ contract blocking của chúng.

## 3. ArrayBlockingQueue: mảng cố định với capacity bắt buộc

Khởi tạo:

~~~java
BlockingQueue<String> queue =
        new ArrayBlockingQueue<>(100);
~~~

`ArrayBlockingQueue` là FIFO queue có **capacity cố định** được chỉ định khi tạo. Giá trị capacity phải ít nhất 1. Khi đã khởi tạo, không có `setCapacity` để thay đổi kích cỡ của mảng theo ý muốn.

Hình dung queue capacity 4:

~~~text
Mảng backing cố định 4 slots
┌────┬────┬────┬────┐
│ A  │ B  │    │    │
└────┴────┴────┴────┘
  ↑   ↑
  head/tail tiến theo vòng khi insert/remove

size = 2
remainingCapacity = 2

put(C) → đưa C vào slot trống
put(D) → queue đầy (size=4)
put(E) → producer phải đợi có consumer lấy bớt
~~~

ArrayBlockingQueue dùng cơ chế **circular array** với các chỉ số đưa phần tử vào/lấy phần tử ra. Việc có mảng cố định mang lại vài đặc điểm:

- Không cần cấp phát một **node wrapper mới** cho mỗi lần enqueue như linked nodes thông thường.
- Số reference slots của queue được cấp phát gần mức capacity ngay từ đầu; một queue capacity rất lớn vẫn có chi phí backing array ngay khi tạo, kể cả rỗng.
- Khi size đạt capacity, `offer` trả false, còn `put` đợi. Điều này tạo **backpressure** nếu producers dùng blocking API đúng cách.
- Cấu trúc có giới hạn giúp dự toán số **phần tử đang chờ**; tuy nhiên bản thân từng phần tử có thể rất lớn, nên capacity không tự đảm bảo memory usage dưới bất kỳ ngân sách nào.
- ArrayBlockingQueue có tùy chọn **fairness** cho những producers/consumers đang chờ quyền truy cập queue.

Constructor fairness:

~~~java
BlockingQueue<String> fairQueue =
        new ArrayBlockingQueue<>(100, true);
~~~

Tham số `fair=true` áp dụng chính sách FIFO cho các threads **đang chờ** khi thêm/lấy phần tử. Mặc định fairness là false. Tài liệu JDK cho biết bật fairness thường **giảm throughput** nhưng có thể giảm độ biến thiên thời gian đợi và tránh starvation giữa các waiter trong một số workload.

**Fairness không phải FIFO completion order của những task được consumers thực thi sau khi `take`.** Consumer A lấy task trước nhưng xử lý mất 20 giây; consumer B lấy task sau nhưng hoàn tất sau 2ms. B có thể xong trước A.

## 4. LinkedBlockingQueue: liên kết nodes, capacity tùy chọn

Khởi tạo có giới hạn:

~~~java
BlockingQueue<String> bounded =
        new LinkedBlockingQueue<>(100);
~~~

Khởi tạo không truyền giới hạn:

~~~java
BlockingQueue<String> effectivelyUnbounded =
        new LinkedBlockingQueue<>();
~~~

Theo Javadoc Java 17, constructor không tham số có capacity **`Integer.MAX_VALUE`**. Điều này **không đồng nghĩa vô hạn thực sự**: queue vẫn chịu giới hạn số nguyên, heap, overhead mỗi node và khả năng producer tạo/giữ đối tượng. Trên thực tế, chương trình có thể hết memory **rất lâu trước khi** đạt giới hạn danh nghĩa.

LinkedBlockingQueue sử dụng các **linked nodes** được tạo động khi thêm phần tử, trừ khi việc thêm sẽ vượt capacity. Mô hình:

~~~text
head → [node A] → [node B] → [node C] → tail
           ↑          ↑          ↑
         tham chiếu đến payload/task

Capacity = 100 (nếu truyền 100)
Size = 3
Remaining = 97

Không truyền capacity:
  bound danh nghĩa = Integer.MAX_VALUE
  queue vẫn có thể giữ backlog rất lớn
~~~

Điểm chú ý:

- LinkedBlockingQueue **có thể bounded** nếu gọi constructor với capacity dương. Không được nói mọi LinkedBlockingQueue đều unbounded.
- Nó **không** có tham số fairness boolean trong API constructor tiêu chuẩn tương đương `ArrayBlockingQueue(int, boolean)`.
- Mỗi lần chèn có thể cần node mới; tăng allocation và áp lực GC dưới một số workloads.
- Đổi lại, với producer/consumer hoạt động đồng thời, cấu trúc lock phân tách có thể cho throughput tốt trong nhiều tình huống.
- Oracle JDK mô tả linked queues **thường có throughput cao hơn** array-based queues nhưng **hiệu năng kém dễ dự đoán hơn** trong nhiều ứng dụng concurrent. Đây là xu hướng được tài liệu mô tả, **không phải benchmark guarantee**.
- Khi queue lớn, các nodes và references giữ payload trong heap; payload có thể không được garbage-collected cho tới khi bị lấy khỏi queue hoặc xóa.

### LinkedBlockingQueue cũng có backpressure nếu được bounded

~~~java
BlockingQueue<Job> workQueue =
        new LinkedBlockingQueue<>(500);
~~~

Đây là đoạn method giả định `Job` là class ứng dụng. Với capacity 500, hành vi khi đầy tương tự về mặt BlockingQueue contract: `offer` trả false, `put` chờ, timed `offer` có thể timeout.

Khả năng bounded vs unbounded là lựa chọn **constructor**, không phải thuộc tính bất biến của mọi implementation linked queue.

## 5. Bảng đối chiếu hai class

| Tiêu chí | ArrayBlockingQueue | LinkedBlockingQueue |
|---|---|---|
| Cấu trúc | Mảng vòng (circular array) | Linked nodes |
| Thứ tự | FIFO | FIFO |
| Capacity | **Bắt buộc**, cố định sau tạo | **Tùy chọn**; truyền capacity hoặc mặc định Integer.MAX_VALUE |
| Constructor không tham số | Không có | Có |
| `put` khi đầy | Block, interruptible | Block nếu đạt bound |
| `take` khi rỗng | Block, interruptible | Block, interruptible |
| `offer` khi đầy | False | False nếu bounded và đầy |
| `poll` khi rỗng | Null | Null |
| Chấp nhận null | Không | Không |
| Optional fairness | Có, `new ArrayBlockingQueue<>(n, true)` | Không có tham số fairness constructor tương tự |
| Đồng bộ nội bộ | Một `ReentrantLock` quản lý queue + điều kiện notEmpty/notFull | Locks riêng cho put và take + atomic count |
| Storage overhead | Backing array reference slots cấp sẵn | Linked nodes cấp phát động |
| Lưu lượng lớn | Có thể tốt, tùy contention/fairness | Có thể throughput cao hơn trong một số tình huống |
| Memory predictability | Dễ chặn backlog bằng capacity | Dễ chặn bằng capacity **nếu truyền bound**; nguy cơ nếu để mặc định |
| `ThreadPoolExecutor` khi queue đầy | Có thể mở rộng workers tới max/reject | Bounded: tương tự; mặc định: queue nhận lâu, max ít phát huy |

**Không có “winner” tuyệt đối.** Dựa trên tỷ lệ producers/consumers, memory, kích thước task, thời gian giữ queue, CPU/NUMA, contention và GC, kết quả benchmark có thể thay đổi.

## 6. Locking nội bộ: tại sao throughput có thể khác?

### ArrayBlockingQueue: một ReentrantLock

Một ArrayBlockingQueue dùng một lock bảo vệ các operations quan trọng trên mảng, cùng conditions `notEmpty` và `notFull`. Về nguyên tắc, cùng lock đó phối hợp cả enqueue và dequeue.

~~~text
Producer          single queue lock        Consumer
   |                    |                      |
   +---- enqueue ------►|                      |
                        |◄------ dequeue ------+
                        |
            notEmpty / notFull conditions
~~~

Khi nhiều producers và consumers hoạt động đồng thời, các thao tác trên queue có thể cạnh tranh lock chung. Tuy nhiên, việc giữ queue đơn giản, không tạo node mới cho từng item và có locality khác có thể giúp throughput trong một số workload.

### LinkedBlockingQueue: hai locks cho hai đầu

Implementation JDK của LinkedBlockingQueue dùng một lock cho phía `put` và một lock cho phía `take`, phối hợp bằng `AtomicInteger count` và conditions thích hợp.

~~~text
Producer                 LinkedBlockingQueue                Consumer
   |                       putLock                              |
   +---- enqueue ----------►|                                   |
                            |     AtomicInteger count          |
                            |            |                     |
                            |         takeLock ◄----- dequeue--+
~~~

Cách tách lock có thể cho **một producer và một consumer** tiến hành thêm/lấy đồng thời trong nhiều trường hợp, cải thiện throughput. Nhưng mỗi node được tạo/thu hồi và đồng bộ về size có overhead; cũng có những operations cần phối hợp cả hai locks.

**Đừng suy rằng hai locks = luôn nhanh gấp đôi.** Throughput phải đo với cùng workload, JVM, GC, number of producers/consumers, capacity, CPU, queue depth và kích thước phần tử.

## 7. Fairness: có trên ArrayBlockingQueue, không đồng nghĩa priority

~~~java
ArrayBlockingQueue<String> queue =
        new ArrayBlockingQueue<>(8, true);
~~~

`fair=true` hướng tới thứ tự **FIFO của các threads đã bị block** khi insert/remove. Nó không:
- biến queue thành priority queue;
- đảm bảo mọi task được xử lý xong theo FIFO;
- đảm bảo mọi thread có cùng CPU time;
- làm cho requests mới được ưu tiên theo thời điểm đến ở cấp ứng dụng;
- bảo đảm mọi thao tác không blocking có latency y hệt nhau;
- làm database/I/O của worker trở thành fair.

Đặc biệt, fairness của **queue access** không phải fairness của toàn bộ `ThreadPoolExecutor`. ThreadPoolExecutor còn có worker creation, scheduling, queueing, rejection và task duration.

Khi nào cân nhắc bật fairness?
- Có nhiều producers/consumers thường xuyên bị block tại queue.
- Muốn giảm khả năng một thread phải đợi quá lâu trong tình huống tranh lock.
- Chấp nhận tradeoff throughput/latency.
- Đã benchmark với đặc điểm tải thật.

Với đa số workload thông thường, bắt đầu từ mặc định `fair=false` rồi đo là hợp lý.

## 8. API quan trọng: backpressure phụ thuộc cách caller dùng queue

Giả sử capacity=2, hai task đã chiếm queue:

| Lời gọi thêm | Kết quả | Tác động lên producer |
|---|---|---|
| `queue.add(third)` | `IllegalStateException` khi queue đầy | Producer phải xử lý lỗi |
| `queue.offer(third)` | `false` | Producer có thể reject/route elsewhere |
| `queue.put(third)` | Block cho tới khi có chỗ hoặc bị interrupt | Producer bị chậm lại, tạo backpressure |
| `queue.offer(third, 100, MILLISECONDS)` | Chờ tối đa 100ms; false nếu quá hạn | Có timeout cho việc tiếp nhận |

Không thể nói “bounded queue tự làm producer chậm” trong mọi trường hợp. Nó làm producer chậm **nếu ứng dụng dùng `put` hoặc timed `offer` và chấp nhận đợi**. Dùng `offer` không chờ sẽ chuyển áp lực thành **từ chối**, còn dùng `add` có thể ném exception.

Tương tự phía consumer:

~~~java
String item = queue.take(); // chờ khi rỗng
String now = queue.poll();  // null ngay nếu rỗng
String maybe = queue.poll(1, TimeUnit.SECONDS);
~~~

Đoạn trích trong method khai báo/handle InterruptedException cho `take` và timed `poll`.

### Hai loại “trống”

1. Queue đang rỗng **tại thời điểm kiểm tra** không có nghĩa producers đã kết thúc.
2. Producers đã kết thúc không có nghĩa queue đang rỗng; còn nhiều tasks cần xử lý.

Do đó phải có protocol **kết thúc producer–consumer** rõ ràng (poison pill, close flag kèm timeout, executor shutdown hoặc ownership khác) thay vì suy từ một lần `queue.isEmpty()`.

## 9. Ví dụ độc lập 1: capacity, FIFO và offer

Chương trình chạy cùng một kịch bản trên ArrayBlockingQueue capacity=2 và LinkedBlockingQueue capacity=2. Nó xác nhận cả hai implement contract tương đương ở các thao tác cơ bản.

Lưu **BlockingQueueComparisonDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BlockingQueueComparisonDemo {
    private static void check(String name, BlockingQueue<Integer> queue)
            throws InterruptedException {
        queue.add(10);
        queue.add(20);

        boolean thirdAccepted = queue.offer(30);
        int remaining = queue.remainingCapacity();

        int first = queue.take();
        boolean laterAccepted = queue.offer(30);
        int second = queue.take();
        int third = queue.take();

        System.out.println(name + ": offerWhenFull=" + thirdAccepted
                + ", remainingWhenFull=" + remaining
                + ", offerAfterTake=" + laterAccepted
                + ", fifo=" + first + "," + second + "," + third);
    }

    public static void main(String[] args) throws InterruptedException {
        check("array", new ArrayBlockingQueue<>(2));
        check("linked", new LinkedBlockingQueue<>(2));
    }
}
~~~

Biên dịch/chạy với JDK 17+:

~~~bash
javac BlockingQueueComparisonDemo.java
java BlockingQueueComparisonDemo
~~~

Output chính xác khi không có thread khác truy cập hai queues:

~~~text
array: offerWhenFull=false, remainingWhenFull=0, offerAfterTake=true, fifo=10,20,30
linked: offerWhenFull=false, remainingWhenFull=0, offerAfterTake=true, fifo=10,20,30
~~~

Giải thích: mỗi queue nhận 10 và 20 thì đầy. `offer(30)` trả false, không thêm phần tử. `take()` lấy 10; sau đó `offer(30)` trả true. Hai lần take kế tiếp trả 20 và 30. `remainingCapacity()` được chụp **ngay khi đầy** nên bằng 0.

**Bài học:** Cùng `BlockingQueue` contract, code producer–consumer có thể chuyển giữa hai implementations mà không đổi ý nghĩa cơ bản của `put/take/offer/poll`; nhưng memory/performance/queue policy sẽ khác.

## 10. Ví dụ độc lập 2: producer bị chặn ở put, consumer giải phóng chỗ

Chương trình dùng một queue capacity=1 và một producer. Queue đã chứa `"first"` trước khi producer cố thêm `"second"`. Main nhận một tín hiệu bắt đầu, lấy phần tử đầu, sau đó đợi producer hoàn tất và lấy phần tử mới. Kết quả không phụ thuộc tên thread hay `sleep`.

Lưu **QueueProducerConsumerDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueueProducerConsumerDemo {
    private static void run(String name, BlockingQueue<String> queue)
            throws InterruptedException {
        queue.put("first");
        CountDownLatch producerStarted = new CountDownLatch(1);
        AtomicBoolean producerInserted = new AtomicBoolean(false);

        Thread producer = new Thread(() -> {
            producerStarted.countDown();
            try {
                queue.put("second");
                producerInserted.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, name + "-producer");

        producer.start();
        producerStarted.await();

        // Có thể producer đã vào put() hoặc chưa, nhưng queue vẫn đầy
        // cho tới khi main lấy "first".
        String first = queue.take();
        producer.join();
        String second = queue.take();

        System.out.println(name + ": " + first + " -> " + second
                + ", inserted=" + producerInserted.get());
    }

    public static void main(String[] args) throws InterruptedException {
        run("array", new ArrayBlockingQueue<>(1));
        run("linked", new LinkedBlockingQueue<>(1));
    }
}
~~~

Chạy:

~~~bash
javac QueueProducerConsumerDemo.java
java QueueProducerConsumerDemo
~~~

Output:

~~~text
array: first -> second, inserted=true
linked: first -> second, inserted=true
~~~

**Vì sao an toàn?**

- Queue capacity=1 đã chứa first, nên second **không thể chèn thành công trước khi first được lấy**.
- Main đợi signal producer đã bắt đầu, rồi lấy first. Nếu producer đã vào `put`, nó được đánh thức khi queue có chỗ; nếu chưa vào `put`, nó sẽ chèn sau.
- Main dùng `producer.join()` để biết producer đã hoàn tất trước khi lấy second; không dựa vào một số mili giây `sleep`.
- `producerInserted` chỉ được đặt true **sau** khi put thành công.
- Tính đúng của ví dụ giả định không có yêu cầu interrupt ngoài và chương trình được cấp lịch chạy bình thường. Một `put` trên queue đầy có thể **chờ**; ở đây consumer luôn giải phóng chỗ.

Đừng viết test “sau `Thread.sleep(50)` chắc chắn producer đang WAITING”: scheduler không đảm bảo thời điểm đó. Latch thể hiện thứ tự điều phối, còn bản chất capacity đảm bảo không có chỗ cho second trước take.

## 11. Interruption và hủy khi đang chờ hàng đợi

Cả `put` lẫn `take` đều là các điểm chờ hỗ trợ `InterruptedException`. Khi một worker được yêu cầu shutdown, logic thường cần thoát một cách hợp tác:

~~~java
void consume(BlockingQueue<Job> queue) {
    while (!Thread.currentThread().isInterrupted()) {
        try {
            Job job = queue.take();
            process(job);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
    }
}
~~~

Đây là method minh họa tích hợp, `Job` và `process` do ứng dụng định nghĩa. `take` bị interrupt khi chờ thì method restore flag và return. Không bắt `InterruptedException` rồi âm thầm lặp vô hạn, vì điều đó có thể làm worker không phản hồi shutdown.

### Ví dụ độc lập 3: producer bị interrupt khi queue đầy

Lưu **QueueInterruptionDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueueInterruptionDemo {
    private static void run(String name, BlockingQueue<String> queue)
            throws InterruptedException {
        queue.put("original");
        CountDownLatch producerStarted = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        Thread producer = new Thread(() -> {
            producerStarted.countDown();
            try {
                queue.put("new");
            } catch (InterruptedException e) {
                cancelled.set(true);
                Thread.currentThread().interrupt();
            }
        }, name + "-producer");

        producer.start();
        producerStarted.await();
        producer.interrupt();
        producer.join();

        System.out.println(name + ": cancelled=" + cancelled.get()
                + ", remaining=" + queue.take());
    }

    public static void main(String[] args) throws InterruptedException {
        run("array", new ArrayBlockingQueue<>(1));
        run("linked", new LinkedBlockingQueue<>(1));
    }
}
~~~

Chạy:

~~~bash
javac QueueInterruptionDemo.java
java QueueInterruptionDemo
~~~

Output:

~~~text
array: cancelled=true, remaining=original
linked: cancelled=true, remaining=original
~~~

Queue capacity=1 đầy với original. Producer chưa thể thành công khi thêm new; main gửi interrupt trước khi có consumer lấy original. Dù interrupt đến **ngay trước** hay **trong khi** put đang chờ, put phản ứng bằng InterruptedException; không có "new" được thêm. Producer đánh dấu cancelled rồi kết thúc; main join trước khi đọc trạng thái. Sau đó main lấy original vẫn đang trong queue.

**Giới hạn:** Interruption là **tín hiệu hợp tác**, không phải rollback cho side effects đã thực hiện ở các bước khác. Nếu producer thực hiện ghi database rồi mới put, việc bị interrupt khi put không tự hoàn tác database write.

## 12. ThreadPoolExecutor: khác biệt ảnh hưởng trực tiếp số workers

Khi hai queues được dùng làm `workQueue` của `ThreadPoolExecutor`, sự khác biệt về capacity có ảnh hưởng lớn đến việc executor tạo thêm workers.

Javadoc mô tả chính sách tiếp nhận task:

1. Khi workers dưới `corePoolSize`, ưu tiên tạo worker mới.
2. Khi đã đạt core, ưu tiên `workQueue.offer(task)`.
3. Chỉ khi queue không nhận được task mới thử tạo extra worker tới `maximumPoolSize`.
4. Nếu không queue được và không tạo worker thêm được, dùng `RejectedExecutionHandler`.

~~~text
execute(task)
    |
    +-- workers < core ? --------> tạo core worker
    |
    +-- queue.offer(task) ? -----> xếp hàng
    |
    +-- workers < max ? ---------> tạo extra worker
    |
    +---------------------------> reject/handler
~~~

Sơ đồ là **mô hình admission**, không mô tả đầy đủ các kiểm tra lại khi shutdown hoặc race. Điểm cốt lõi: **max không được dùng chỉ vì queue đang dài**.

### ArrayBlockingQueue(capacity) với ThreadPoolExecutor

Với `new ArrayBlockingQueue<>(10)`, queue có khả năng từ chối offer khi đủ 10 tasks chờ. Nếu worker count vẫn dưới max, executor có thể tạo thêm workers để chạy những task mới đến. Nếu cả max workers và queue đều đầy, handler được gọi.

### LinkedBlockingQueue() không truyền capacity

Với `new LinkedBlockingQueue<>()`, queue có capacity danh nghĩa rất lớn. Sau khi có core workers, những task mới thường tiếp tục được queue, **không kích hoạt tăng workers tới max** trong điều kiện bình thường. Nếu producer nhanh hơn consumer, memory và queue wait time có thể tăng rất lớn.

### LinkedBlockingQueue(capacity) với ThreadPoolExecutor

Với `new LinkedBlockingQueue<>(10)`, queue **cũng đầy** khi đạt capacity 10. Vì thế nó có thể kích hoạt extra workers/rejection **tương tự** ArrayBlockingQueue có capacity 10. Lựa chọn array hay linked **không tự quyết định** max có phát huy; quan trọng là **bound có thực sự được cấu hình hay không**.

Đừng dùng `maximumPoolSize=100` rồi kết luận pool sẽ tăng tới 100 nếu queue mặc định vẫn đủ chỗ.

## 13. Ví dụ độc lập 4: cùng core/max nhưng queue dẫn tới hành vi khác nhau

Lưu **QueueExecutorComparisonDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class QueueExecutorComparisonDemo {
    private static Runnable gated(
            CountDownLatch started, CountDownLatch release) {
        return () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    private static void boundedArray() throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 2, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy()
        );
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch extraStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        boolean rejected = false;

        try {
            pool.execute(gated(firstStarted, release));
            firstStarted.await();
            pool.execute(() -> { }); // occupies one queue slot
            pool.execute(gated(extraStarted, release)); // extra worker
            extraStarted.await();
            try {
                pool.execute(() -> { }); // no more queue or workers
            } catch (RejectedExecutionException e) {
                rejected = true;
            }

            System.out.println("array: workers=" + pool.getPoolSize()
                    + ", queued=" + pool.getQueue().size()
                    + ", rejected=" + rejected);
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    private static void unboundedLinked() throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 2, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadPoolExecutor.AbortPolicy()
        );
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            pool.execute(gated(firstStarted, release));
            firstStarted.await();
            pool.execute(() -> { });
            pool.execute(() -> { });

            System.out.println("linked: workers=" + pool.getPoolSize()
                    + ", queued=" + pool.getQueue().size()
                    + ", maximum=" + pool.getMaximumPoolSize());
        } finally {
            release.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        boundedArray();
        unboundedLinked();
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac QueueExecutorComparisonDemo.java
java QueueExecutorComparisonDemo
~~~

Output khi không có external interruption và workers không bị lỗi ngoài dự kiến:

~~~text
array: workers=2, queued=1, rejected=true
linked: workers=1, queued=2, maximum=2
~~~

**Tại sao?**

- **Array:** worker lõi đã được giữ ở latch, task thứ hai vào queue capacity=1. Task thứ ba làm queue offer thất bại nên executor tạo extra worker thứ hai. Cả hai workers còn bận, queue vẫn đầy, task thứ tư được chuyển qua AbortPolicy và bị reject.
- **Linked mặc định:** worker lõi đang bị giữ bận, hai task tiếp theo đều được queue vì capacity rất lớn. `maximumPoolSize=2` không có nghĩa executor phải tạo worker thứ hai khi queue vẫn nhận.
- Các latch giữ workers đúng thời điểm in, không phụ thuộc `Thread.sleep()`; sau đó main release các workers rồi shutdown pool.

**Thử nghiệm tiếp:** Thay `new LinkedBlockingQueue<>()` bằng `new LinkedBlockingQueue<>(1)` trong nhánh linked và chỉnh kịch bản tương tự nhánh array. Khi bounded, linked queue cũng có thể đầy và kích hoạt extra worker/rejection theo cùng admission rules.

**Không suy “array luôn tăng workers còn linked không bao giờ tăng”.** Hai hành vi khác nhau trong demo vì **capacity được chọn khác**, không phải chỉ vì backing representation.

## 14. Memory: queue bounded không có nghĩa total application memory bounded

Với ArrayBlockingQueue:
- Backing array có số slots cố định, thuận tiện dự toán overhead ở cấp queue.
- Nhưng mỗi element có thể giữ một object graph lớn. 100 tasks mỗi task capture 100 MB dữ liệu vẫn có thể vượt heap.
- Task đang chạy trên workers **không nằm trong work queue**, nên tổng số “requests in system” còn gồm active tasks.

Với LinkedBlockingQueue:
- Mỗi enqueue có thể tạo một linked node và giữ references tới payload.
- Nếu queue mặc định rất lớn, backlog tích lũy không được điều tiết ở cấp application.
- Nếu cấu hình capacity có hạn, số **items chờ** vẫn bị giới hạn, nhưng node allocation/GC có thể ảnh hưởng throughput.
- Khi task bị cancel nhưng vẫn ở queue, có thể còn giữ references cho tới khi được remove/thu hồi theo cơ chế quản lý; không mặc định cancel tự dọn mọi cấu trúc queue ngay.

Dự toán capacity tối thiểu cần xem:

~~~text
Outstanding work =
  running tasks + queued tasks + upstream pending tasks

Memory budget xấp xỉ:
  queue overhead
  + payloads trong queued tasks
  + state của running tasks
  + các buffers/caches khác
~~~

Công thức là mô hình định tính, **không phải số byte bảo đảm**. Muốn định lượng cần đo object sizes, sampling profiler, heap occupancy, allocation rate, queue depth và GC trên workload thật.

### Queue length cao là dấu hiệu gì?

Một queue dài cho thấy producer đang vượt qua năng lực xử lý trong một khoảng thời gian, hoặc consumers đang bị block/lỗi/chậm. Không nhất thiết queue implementation kém. Các nguyên nhân thường gồm:
- remote I/O chậm, connection pool nhỏ;
- số consumers ít;
- producer burst quá lớn;
- tasks giữ lock lâu hoặc retry liên tục;
- downstream rate limit;
- thiếu cancellation cho công việc đã hết hạn.

Tăng capacity có thể **giấu lỗi bằng cách tăng thời gian chờ**, không làm throughput tự tăng.

## 15. Backpressure: thiết kế từ hệ quả khi đầy

Hãy định nghĩa rõ yêu cầu nghiệp vụ:

1. **Được phép chặn producer?** Dùng `put` hoặc timed `offer`; tránh block event loop hoặc thread quan trọng tới deadline.
2. **Được phép từ chối?** Dùng `offer`, thông báo “quá tải” hoặc đường retry có kiểm soát.
3. **Phải xử lý mọi task?** Cần durable messaging/ack/retry protocol ngoài queue RAM nếu phải chịu process crash.
4. **Có hạn chờ?** Dùng timed `offer` và `poll`; nếu quá hạn, trả outcome rõ ràng.
5. **Có thể bỏ việc hết hạn?** Đặt deadline/expiry logic theo nghiệp vụ; queue không tự biết một HTTP request đã hết thời hạn.
6. **Có nhiều consumers không?** Chọn số consumers theo throughput thực, connection limit và memory budget.
7. **Tác vụ phụ thuộc nhau?** Tránh producer/consumer giữ tài nguyên rồi block trong queue tạo wait-cycle.

`BlockingQueue` không phải database transaction, không có persistent acknowledgment và không bảo đảm exactly-once execution khi process crash. Các operations queue an toàn trong JVM, nhưng sự bền vững/tính duy nhất xuyên hệ thống phải được xử lý riêng.

## 16. Shutdown producer–consumer đúng cách

`BlockingQueue` **không có phương thức close() chuẩn trong interface**. Một consumer đang ở `take()` trên queue rỗng có thể chờ mãi nếu producers đã kết thúc mà không có shutdown protocol.

Các cách hay gặp:
- **Poison pill:** enqueue một giá trị đặc biệt mà consumer nhận diện để thoát.
- **Interrupt:** khi phù hợp, interrupt consumer, để `take` ném InterruptedException và task cleanup.
- **Timed poll + lifecycle flag:** consumer thức dậy định kỳ, kiểm tra producerDone và queue đã được drain hay chưa.
- **ExecutorService.shutdown()/awaitTermination():** quản lý workers khi queue là work queue của executor, nhưng caller cần biết khác biệt giữa shutdown và shutdownNow.

### Poison pill không phải null

Vì `BlockingQueue` không nhận null, không dùng null làm sentinel được enqueue. Dùng đối tượng sentinel cụ thể hoặc một kiểu wrapper:

~~~java
sealed interface Message permits Data, Stop {}

record Data(String payload) implements Message {}

record Stop() implements Message {}
~~~

Đây là các khai báo kiểu Java 17 (cần đặt trong file/class phù hợp). Consumer kiểm tra `instanceof Stop` để thoát. Trong ứng dụng nhiều consumers, thông thường phải có **đủ stop tokens** cho số consumers cần kết thúc, hoặc một protocol broadcast/lifecycle tương đương.

**Đừng gửi stop quá sớm:** nếu producer còn có thể enqueue công việc sau stop token, consumer có thể dừng trước khi xử lý toàn bộ dữ liệu. Cần thứ tự “producers đã ngừng sinh việc → xác định queue drain/stop protocol → consumers kết thúc”.

### Không tin queue.isEmpty() một mình

~~~java
if (queue.isEmpty()) {
    return; // Có thể sai: producer chưa xong, item mới sẽ đến.
}
~~~

Đây là anti-pattern khi consumer được kỳ vọng chạy cho tới khi công việc toàn hệ thống hoàn tất. `isEmpty` chỉ nói trạng thái tại một thời điểm; không có transaction kết hợp với lifecycle của producers. Nếu cần chờ work mới, dùng `take`; nếu cần điều kiện kết thúc, dùng tín hiệu owner và kiểm tra đồng bộ thích hợp.

### Giới hạn của drainTo

`drainTo(collection)` chuyển nhiều phần tử đang có từ queue vào collection cho caller xử lý. Nó có thể giảm overhead so với gọi take nhiều lần trong một số workload. Tuy nhiên:
- Không có nghĩa các producers đồng thời bị dừng.
- Không đảm bảo toàn bộ queue còn rỗng sau khi drain vì producer mới có thể enqueue.
- Nếu collection đích ném exception trong quá trình chuyển, tình trạng một số phần tử đã được chuyển có thể làm cần xử lý recovery.
- Không gọi `drainTo(queue)` để chuyển vào chính nó; API từ chối/không hợp lệ.

## 17. FIFO và thứ tự thực thi: hai khái niệm khác nhau

Cả hai queues cung cấp FIFO cho các phần tử theo thứ tự chèn vào queue. Nhưng với **hai consumers**, thứ tự **xử lý xong** không được đảm bảo:

~~~text
Queue ban đầu: A, B

Consumer 1 take(A), xử lý 10 giây.
Consumer 2 take(B), xử lý 1 giây.

B có thể hoàn tất trước A
dù A được lấy ra khỏi queue trước B.
~~~

Tương tự nếu queue là `ThreadPoolExecutor.workQueue`, một task có thể được đưa trực tiếp tới extra worker thay vì xếp hàng khi queue đầy; thứ tự start/completion toàn pool không phải FIFO tuyệt đối.

Nếu nghiệp vụ yêu cầu xử lý **cùng một entity theo đúng thứ tự**, cân nhắc:
- Một consumer tuần tự cho mỗi partition/key.
- Phân vùng theo key và serialize từng partition.
- Sequence/version checking ở nơi commit kết quả.
- Idempotent operations và ordering protocol xuyên hệ thống.

Fairness flag của ArrayBlockingQueue không tự giải quyết thứ tự commit của nhiều workers.

## 18. Thread safety không có nghĩa mọi tổ hợp operations là atomic

Cả hai queues bảo vệ các operations của chúng theo hợp đồng concurrent collection; nhưng thao tác ghép như sau có thể bị tranh chấp:

~~~java
if (!queue.isEmpty()) {
    E item = queue.poll(); // vẫn có thể null
    use(item);
}
~~~

Đây là **pseudocode Java-like**, `E` và `use` giả định được định nghĩa. Giữa `isEmpty` và `poll`, một consumer khác có thể lấy phần tử cuối.

Giải pháp:
- Nếu cần lấy ngay nếu có, dùng **một `poll()`**, kiểm tra `null`.
- Nếu cần chờ, dùng **`take()`** hoặc timed `poll`.
- Nếu cần “kiểm tra rồi cập nhật nhiều tài nguyên cùng lúc”, thiết kế giao thức/lock phù hợp; BlockingQueue không làm mọi state ngoài queue thành atomic.
- Không dựa vào `remainingCapacity()` rồi mới `offer()` như một phép giữ chỗ. Producer khác có thể chiếm chỗ giữa hai lời gọi.

### Memory visibility/happens-before

Theo contract concurrent collections, **các hành động trong producer trước khi đặt đối tượng vào queue happen-before các hành động của consumer sau khi truy cập/lấy phần tử đó ra**. Vì vậy producer khởi tạo payload trước `put` và consumer nhận payload qua `take` sẽ có bảo đảm visibility phù hợp.

Tuy nhiên, nếu producer **tiếp tục sửa object mutable sau khi enqueue** mà thiếu đồng bộ, vẫn có thể data race. Queue publish reference an toàn; nó không biến mọi mutation tương lai của object thành thread-safe.

~~~java
record Event(String message) {}

BlockingQueue<Event> queue = new ArrayBlockingQueue<>(8);
queue.put(new Event("ready"));
Event received = queue.take();
System.out.println(received.message());
~~~

Đây là đoạn trích trong method có exception handling. Event record với thành phần immutable String phù hợp để chuyển ownership. Nếu event chứa List mutable, cần defensive copy/immutable snapshot để tránh thay đổi sau publication.

## 19. Benchmark và tuning: không kết luận chỉ từ tên implementation

Oracle Javadoc mô tả LinkedBlockingQueue thường có throughput cao hơn ArrayBlockingQueue trong nhiều tình huống, nhưng ít dễ dự đoán hơn. Điều này không đồng nghĩa:
- luôn nhanh hơn trên mọi CPU/JDK;
- có latency thấp hơn ở mọi percentile;
- có footprint thấp hơn;
- luôn ít contention hơn;
- có thể bỏ qua memory bounds.

Một thử nghiệm đúng cần:
1. So sánh **cùng capacity** khi nghiên cứu cấu trúc array vs linked.
2. Giữ cùng số producers/consumers.
3. Giữ cùng kích thước/phần việc mỗi item.
4. Đo throughput, p50/p95/p99 enqueue/dequeue latency, allocation rate và GC.
5. Tách case queue thường rỗng, thường đầy và dao động giữa hai trạng thái.
6. Thử fairness false/true nếu dùng ArrayBlockingQueue.
7. Thử bounded/unbounded **như hai policy riêng**, không gộp khác biệt vào so sánh tốc độ implementation.
8. Đo thread blocking time, context switches, heap usage và connection/resource pressure.
9. Dùng công cụ benchmark phù hợp như JMH khi cần số liệu nghiêm túc; không tự viết một vòng `System.nanoTime` duy nhất rồi kết luận.
10. Kiểm tra cả shutdown/cancellation/overload behavior, không chỉ happy path.

Trong một số hệ thống, giảm queue capacity tạo rejection sớm hơn nhưng **bảo vệ memory và tail latency** tốt hơn. Đây có thể là kết quả đúng về mặt vận hành ngay cả khi throughput cực đại giảm nhẹ.

## 20. Bảng quyết định: nên dùng loại nào?

| Tình huống | Gợi ý khởi đầu | Lý do |
|---|---|---|
| Cần giới hạn backlog rõ ràng, tối thiểu node allocation | `ArrayBlockingQueue<>(capacity)` | Backing array cố định, bounded từ đầu |
| Cần policy công bằng cho các waiter | `ArrayBlockingQueue<>(capacity, true)` | Có fairness flag; phải đánh đổi throughput |
| Muốn linked queue nhưng vẫn cần backpressure | `LinkedBlockingQueue<>(capacity)` | Optional bound, FIFO, put/take đầy đủ |
| Workload producer/consumer cạnh tranh cao | Benchmark cả hai **cùng capacity** | Linked có thể throughput tốt nhưng không đảm bảo trên mọi hệ thống |
| Dùng ThreadPoolExecutor với max cần phát huy khi queue đầy | **Một bounded queue**, array hoặc linked | Queue phải hết capacity mới kích hoạt extra workers |
| Muốn hấp thụ backlog không giới hạn thực tế | Cần cân nhắc kỹ, thường **không mặc định** | Linked không giới hạn capacity dễ tăng memory/latency |
| Muốn direct handoff, không giữ backlog | Xem `SynchronousQueue` | Một lựa chọn khác, không phải Array/Linked |
| Muốn queue không blocking cho nhiều producers/consumers | Xem `ConcurrentLinkedQueue` | Không có `take/put` blocking semantics |
| Muốn ưu tiên task theo priority | Xem `PriorityBlockingQueue` | Không phải FIFO như hai queue ở đây |
| Cần persistence/ack/retry sau process crash | Broker/durable store + protocol phù hợp | In-memory BlockingQueue không cung cấp durability |

Quyết định thực tế thường phụ thuộc **giới hạn queue** và **policy khi đầy** nhiều hơn chênh lệch tối ưu giữa array và linked.

## 21. Các lỗi code review hay gặp

**Lỗi 1 — Gọi LinkedBlockingQueue mặc định là “vô hạn thật”.** Capacity danh nghĩa là Integer.MAX_VALUE nhưng memory có hạn.

**Lỗi 2 — Nói LinkedBlockingQueue luôn unbounded.** Constructor `new LinkedBlockingQueue<>(n)` tạo bounded queue.

**Lỗi 3 — Nói ArrayBlockingQueue tự tăng capacity.** Capacity cố định sau khi tạo; không có auto-resize.

**Lỗi 4 — Gọi `offer` rồi không kiểm tra boolean.** Khi queue đầy, task có thể bị bỏ mà application tưởng đã nhận.

**Lỗi 5 — Dùng `put` ngay trong event-loop hoặc request thread không được phép block.** Khi queue đầy, caller có thể chờ lâu.

**Lỗi 6 — Bật fairness và mặc định mọi tasks sẽ hoàn tất đúng FIFO.** Fairness của threads chờ không quyết định thời gian xử lý.

**Lỗi 7 — Cấu hình ThreadPoolExecutor max rất lớn với unbounded LinkedBlockingQueue rồi nghĩ pool tự mở rộng.** Executor ưu tiên queue sau core.

**Lỗi 8 — Đặt capacity lớn để “sửa” consumer chậm.** Backlog tăng/latency tăng, throughput không tự tốt lên.

**Lỗi 9 — Dùng `null` như poison pill.** BlockingQueue không nhận null.

**Lỗi 10 — Consumer thấy `isEmpty()==true` thì thoát dù producers còn chạy.** Race với việc enqueue mới.

**Lỗi 11 — Nuốt InterruptedException trong vòng `take/put`.** Worker có thể không phản hồi shutdown.

**Lỗi 12 — Dựa vào `remainingCapacity` rồi `offer` và cho rằng có chỗ chắc chắn.** Check-then-act giữa hai threads không atomic.

**Lỗi 13 — Đem queue throughput benchmark ở hai capacity quá khác rồi quy cho cấu trúc array vs linked.** Cần so sánh cùng workload/policy.

**Lỗi 14 — Giữ mutable payload sau enqueue rồi thay field không đồng bộ.** Visibility khi publish không bảo vệ mutation tiếp theo.

**Lỗi 15 — Dùng `size()` làm điều kiện điều phối chính xác giữa nhiều threads.** Snapshot có thể đổi ngay; không phải reservation.

**Lỗi 16 — Nghĩ queue rỗng thì không có active tasks.** Worker có thể đang xử lý phần tử đã dequeue.

**Lỗi 17 — Dùng ArrayBlockingQueue capacity hàng triệu mà không tính cost backing array.** Mảng reference slots được cấp phát theo capacity.

**Lỗi 18 — Chọn LinkedBlockingQueue vì nghĩ “không có lock”.** Nó là blocking queue có đồng bộ locks; không phải lock-free.

**Lỗi 19 — Cho rằng FIFO queue nghĩa đúng một lần xử lý.** Task có thể throw, process crash hoặc caller retry; không có durable ack.

**Lỗi 20 — Đặt poison pill quá sớm hoặc thiếu tokens cho consumers.** Có thể mất việc cuối hoặc để consumers khác chờ vô hạn.

## 22. Checklist trước khi triển khai

- [ ] Queue có **capacity rõ ràng** và được chọn theo memory/latency budget?
- [ ] Nếu dùng LinkedBlockingQueue không tham số, đã hiểu bound=Integer.MAX_VALUE và rủi ro backlog?
- [ ] Đã phân biệt nhu cầu `offer` (không chờ), `put` (chờ), timed offer và `add` (exception)?
- [ ] Producer được phép block tới bao lâu? Có timeout/cancellation?
- [ ] Consumer dùng take/poll đúng với lifecycle và deadline?
- [ ] Có cần fairness giữa các waiter không? Đã đo tradeoff?
- [ ] Payloads có immutable/ownership rõ ràng sau enqueue?
- [ ] Có xử lý InterruptedException không nuốt tín hiệu?
- [ ] Có signal kết thúc producer–consumer (poison pill/interrupt/flag) hợp lệ?
- [ ] Queue có nhiều consumers và cần bảo toàn ordering nghiệp vụ không?
- [ ] Nếu là ThreadPoolExecutor workQueue, đã kiểm tra core→queue→max→reject?
- [ ] RejectedExecutionHandler có báo rõ task không được nhận không?
- [ ] Đã đo queue size, time-in-queue, throughput và tail latency?
- [ ] Có tính cả tasks đang chạy, backlog upstream và memory payload?
- [ ] Có case tất cả consumers block/throw, producers tiếp tục sinh việc?
- [ ] Đã load-test saturation và process shutdown, không chỉ throughput bình thường?

## 23. Câu hỏi tự kiểm tra và đáp án

**Câu 1.** `new LinkedBlockingQueue<>()` có phải một queue không bao giờ đầy?

**Đáp án:** Không. Capacity mặc định là `Integer.MAX_VALUE`, và heap có giới hạn. Ứng dụng có thể hết memory trước khi chạm tới bound danh nghĩa.

**Câu 2.** Cả hai queue đều FIFO, vậy hai consumers có chắc hoàn tất công việc theo đúng thứ tự lấy khỏi queue không?

**Đáp án:** Không. FIFO mô tả thứ tự phần tử được lấy; processing duration khác nhau làm completion có thể đảo.

**Câu 3.** `ArrayBlockingQueue<>(2)` đã có 2 items. `offer(newItem)` làm gì?

**Đáp án:** Trả `false` ngay, không block và không thêm phần tử.

**Câu 4.** Cùng queue đó, `put(newItem)` làm gì?

**Đáp án:** Có thể chờ đến khi consumer lấy bớt item, hoặc ném InterruptedException nếu thread bị interrupt khi chờ.

**Câu 5.** Vì sao ArrayBlockingQueue thường có memory predictability tốt cho queue capacity?

**Đáp án:** Nó dùng một backing array kích thước cố định, không tạo linked node cho mỗi item. Nhưng payloads giữ trong slots và active tasks vẫn tiêu thụ bộ nhớ.

**Câu 6.** LinkedBlockingQueue có thể bounded không?

**Đáp án:** Có: `new LinkedBlockingQueue<>(capacity)` với capacity dương.

**Câu 7.** Một ThreadPoolExecutor core=2 max=10 dùng unbounded LinkedBlockingQueue. Khi hai workers đều bận, tasks mới có làm pool tự tăng lên 10 ngay không?

**Đáp án:** Thông thường không. Work queue tiếp tục nhận tasks, executor ưu tiên queue sau khi đạt core.

**Câu 8.** Nếu muốn max phát huy khi backlog đạt giới hạn, có nhất thiết phải dùng ArrayBlockingQueue không?

**Đáp án:** Không. `LinkedBlockingQueue<>(n)` cũng bounded và có thể kích hoạt thêm workers khi đầy.

**Câu 9.** Fairness=true có đảm bảo business tasks kết thúc FIFO không?

**Đáp án:** Không. Fairness là chính sách với threads đang đợi queue access, không phải thứ tự hoàn tất của worker computation.

**Câu 10.** Vì sao không nên dùng `queue.isEmpty()` để quyết định consumer thoát khi producer còn hoạt động?

**Đáp án:** Producer có thể thêm item ngay sau khi kiểm tra; cần lifecycle/termination protocol riêng.

**Câu 11.** Với BlockingQueue, `poll()==null` có thể là một phần tử null thực sự được enqueue không?

**Đáp án:** Không. Queue không nhận null; null là tín hiệu không có phần tử tại thời điểm poll.

**Câu 12.** Đã có capacity bounded, vậy toàn bộ JVM có chắc không thể OOM không?

**Đáp án:** Không. Payloads có thể lớn, running tasks/upstream queues cũng dùng memory, ngoài ra còn nhiều nguồn cấp phát khác.

## 24. Tài liệu Java chính thức

- **ArrayBlockingQueue (Java 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html
- **LinkedBlockingQueue (Java 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html
- **BlockingQueue (Java 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html
- **ThreadPoolExecutor (Java 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
- **Concurrent Collections memory consistency (Java 17):** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html
- **Java Language Specification, Threads and Locks:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html

**Kết luận:** `ArrayBlockingQueue` và `LinkedBlockingQueue` đều là **FIFO BlockingQueue**, nhưng Array sử dụng **mảng cố định**, có fairness flag, còn Linked dùng **linked nodes** và chỉ bounded nếu **bạn chỉ định capacity** (mặc định Integer.MAX_VALUE). Chọn đúng cần xét **backpressure, queue capacity, allocation/GC, contention, task latency và tương tác với ThreadPoolExecutor**. Đừng suy unbounded backlog là giải pháp quá tải; hãy đặt giới hạn và chính sách khi đầy phù hợp với nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-008">PriorityBlockingQueue, DelayQueue và SynchronousQueue</a>

<details>
<summary>Xem chi tiết</summary>

# PriorityBlockingQueue, DelayQueue và SynchronousQueue trong Java Concurrency

Trong các **concurrent collections** của Java, không phải queue nào cũng tuân theo quy tắc “thêm vào cuối, lấy ở đầu” theo FIFO. Có queue chọn phần tử theo **độ ưu tiên**, queue chỉ cho phép lấy phần tử **sau thời điểm hết hạn**, và queue **không lưu phần tử nào**, chỉ chuyển giao trực tiếp giữa hai thread.

Ba kiểu cần phân biệt:

- **`PriorityBlockingQueue<E>`**: hàng đợi có tính thread-safe, lấy phần tử theo **thứ tự ưu tiên** do `Comparable` hoặc `Comparator` quy định. Không có capacity giới hạn được cấu hình trong API.
- **`DelayQueue<E extends Delayed>`**: hàng đợi các phần tử có thời điểm đến hạn. `take()` chỉ trả **phần tử ở đầu khi delay của nó đã hết**; phần tử khác đã có trong queue nhưng chưa đến hạn vẫn chưa được lấy.
- **`SynchronousQueue<E>`**: **hàng đợi dung lượng bằng 0**. Mỗi lần `put` phải gặp một thao tác `take` tương ứng; không tồn tại backlog lưu bên trong queue.

Cả ba thuộc `java.util.concurrent`, triển khai `BlockingQueue<E>`, giúp phối hợp giữa producer và consumer mà không phải tự viết `wait/notify`. Nhưng **cách chờ, cách đặt hàng và khả năng tiếp nhận công việc** khác nhau rất lớn. Bài tập trung vào Java core, không phụ thuộc Spring hay thư viện ngoài JDK. Ví dụ biên dịch bằng Java 17+.

## 1. BlockingQueue là hợp đồng chung, không phải cùng hành vi

`BlockingQueue<E>` đưa ra các nhóm phương thức:

| Nhu cầu | Không chờ | Chờ vô thời hạn | Chờ có thời hạn |
|---|---|---|---|
| Thêm phần tử | `offer(e)` trả boolean; `add(e)` có thể ném khi không nhận được | `put(e)` | `offer(e, timeout, unit)` |
| Lấy và xóa phần tử | `poll()` trả phần tử hoặc `null`; `remove()` có thể ném nếu không có phần tử | `take()` | `poll(timeout, unit)` |
| Quan sát phần tử đầu | `peek()` | Không có thao tác chờ tương ứng | Không có thao tác chờ tương ứng |

Đối với một bounded FIFO queue như `ArrayBlockingQueue`, `put` chờ khi đầy và `take` chờ khi rỗng. Nhưng ba queue trong bài có những điều kiện riêng:

- **PriorityBlockingQueue:** không có bound do ứng dụng đặt, nên `put` thường **không chờ vì đầy**; `take` chờ khi queue rỗng.
- **DelayQueue:** không có bound do ứng dụng đặt, `put` thường không chờ vì đầy; `take` chờ khi **không có phần tử nào đã đến hạn ở đầu**. Queue có `size() > 0` vẫn có thể khiến `take` tiếp tục chờ.
- **SynchronousQueue:** không có slot lưu, nên `put` chờ **consumer ghép cặp**, `take` chờ **producer ghép cặp**; `offer(e)` không timeout thường trả `false` nếu ngay lúc đó không có consumer sẵn sàng nhận.

Không queue nào trong ba kiểu chấp nhận `null` làm phần tử. Vì vậy `poll()` trả `null` là một tín hiệu “chưa lấy được”, nhưng lý do có thể là **rỗng, chưa đến hạn hoặc không có giao dịch ghép cặp**.

## 2. PriorityBlockingQueue: phần tử có ưu tiên cao được lấy trước

`PriorityBlockingQueue` là một blocking queue xây dựng trên cơ chế ưu tiên kiểu heap. Thứ tự đầu queue được xác định bằng:

1. **Natural ordering** khi kiểu `E` triển khai `Comparable<E>`, hoặc
2. **Comparator** truyền vào constructor.

Giá trị “cao ưu tiên” **không mặc định là số lớn hơn**. Với comparator tăng dần theo `priority`, số nhỏ hơn sẽ đứng trước. Nếu muốn số lớn hơn lấy trước, có thể đảo comparator.

```java
PriorityBlockingQueue<Integer> queue =
        new PriorityBlockingQueue<>();

queue.offer(30);
queue.offer(10);
queue.offer(20);
// Ba lần poll tiếp theo cho: 10, 20, 30.
```

Đây là **trích đoạn**, cần import `java.util.concurrent.PriorityBlockingQueue`. Tác vụ thêm theo 30 → 10 → 20 nhưng được lấy theo 10 → 20 → 30.

### Những đặc tính cần nhớ

- **Không phải FIFO:** queue chọn phần tử theo thứ tự comparator/natural ordering.
- **Không tự ổn định thứ tự cho phần tử bằng nhau:** hai phần tử có `compare(a,b)==0` không được bảo đảm lấy theo thứ tự enqueue.
- **Không bounded theo capacity của người dùng:** constructor nhận `initialCapacity` chỉ chỉ định **dung lượng lưu trữ ban đầu**, không phải giới hạn cứng.
- **`remainingCapacity()` trả `Integer.MAX_VALUE`** theo hợp đồng, không có nghĩa heap JVM còn từng ấy phần tử trống.
- **Có thể gặp `OutOfMemoryError`** nếu producer liên tục enqueue nhanh hơn consumer lấy ra.
- **Iterator không cam kết trả phần tử theo thứ tự ưu tiên.** Muốn xử lý đúng thứ tự, hãy dùng `poll()`/`take()` (hoặc sao chép và sort để hiển thị).
- **Khóa sắp xếp phải ổn định khi phần tử nằm trong queue:** nếu sửa field ảnh hưởng comparator sau `offer`, cấu trúc heap không tự reorder theo thay đổi đó.

### Ví dụ 1 — Ưu tiên và tie-breaker ổn định

Khi nhiều task có cùng priority, ta thêm **sequence** vào comparator để tạo thứ tự xác định:

Tạo file **`PriorityQueueDemo.java`**:

```java
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

public class PriorityQueueDemo {
    record Job(int priority, long sequence, String name) {
    }

    public static void main(String[] args) throws InterruptedException {
        Comparator<Job> order =
                Comparator.comparingInt(Job::priority)
                          .thenComparingLong(Job::sequence);

        PriorityBlockingQueue<Job> queue =
                new PriorityBlockingQueue<>(11, order);

        queue.offer(new Job(5, 0, "report"));
        queue.offer(new Job(1, 2, "hotfix"));
        queue.offer(new Job(1, 1, "alert"));
        queue.offer(new Job(3, 3, "email"));

        while (!queue.isEmpty()) {
            Job job = queue.take();
            System.out.println(job.name() + " (" + job.priority() + ")");
        }

        System.out.println("Remaining capacity: "
                + queue.remainingCapacity());
    }
}
```

```bash
javac PriorityQueueDemo.java
java PriorityQueueDemo
```

Kết quả:

```text
alert (1)
hotfix (1)
email (3)
report (5)
Remaining capacity: 2147483647
```

**Vì sao đúng?** Comparator sắp tăng theo priority; nếu bằng nhau thì sắp theo sequence. `alert` và `hotfix` đều có priority=1 nhưng sequence=1 được lấy trước sequence=2. `Job` là record chỉ chứa `int`, `long`, `String` nên key sắp xếp không bị mutate sau enqueue. Biến `11` trong constructor là *initialCapacity*, dù queue vừa lấy bốn item thì `remainingCapacity` vẫn theo contract của queue không bounded.

Trong code production, sequence thường được cấp bằng `AtomicLong.getAndIncrement()` để các producer đồng thời có sequence duy nhất. Nếu đòi thứ tự tuyệt đối giữa các producer, phải định nghĩa rõ thứ tự nộp và cách gán sequence. Không tự suy thứ tự log từ nhiều thread chính là thứ tự offer của queue.

## 3. PriorityBlockingQueue không bảo đảm “task priority cao chạy xong trước”

Giả sử consumer A đã lấy task ưu tiên thấp và bắt đầu xử lý. Sau đó một task ưu tiên rất cao được enqueue. Queue **không preempt** task đang chạy ở consumer A để dành CPU cho task mới.

```text
t0: worker A lấy low-priority task và bắt đầu chạy.
t1: producer thêm high-priority task.
t2: worker B hoặc A ở vòng lấy tiếp nhận high-priority task.
t3: thứ tự hoàn thành còn phụ thuộc thời gian chạy.
```

Do đó cần phân biệt:

- **Priority of dequeue:** phần tử nào được lấy trước trong tập phần tử đang chờ?
- **Priority of execution/completion:** task nào bắt đầu, tiêu tốn CPU hoặc kết thúc trước?

`PriorityBlockingQueue` chỉ trực tiếp quyết định quy tắc **lấy từ queue**, không thay thế cơ chế ưu tiên thread của JVM/OS, không hủy tác vụ đã chạy và không đảm bảo hoàn thành có thứ tự. Nếu nhiều thread cùng `take()`, lời lấy của chúng có thể diễn ra gần nhau; lịch thực thi các task vẫn thay đổi.

### Vấn đề starvation

Nếu liên tục có item priority cao đến queue, các item ưu tiên thấp có thể bị chờ rất lâu (**starvation**). Queue không tự “aging” tăng độ ưu tiên của tác vụ đã chờ lâu. Khi thiết kế scheduler, có thể cân nhắc deadline, quota, aging, nhiều queue tách luồng hoặc giới hạn tốc độ nộp; việc đó vượt ra ngoài comparator đơn giản.

**Không thay field priority khi task đang nằm trong queue** để triển khai aging một cách ngây thơ. Cách an toàn cần một giao thức lấy ra/cập nhật/đưa lại hoặc cấu trúc scheduler được thiết kế cho priority động.

## 4. DelayQueue: lấy phần tử chỉ khi đã đến hạn

`DelayQueue<E extends Delayed>` giữ các object triển khai `Delayed`. Interface này yêu cầu:

```java
long getDelay(TimeUnit unit);
int compareTo(Delayed other);
```

Đây là **chữ ký phương thức**, không phải một class chạy độc lập. `Delayed` mở rộng `Comparable<Delayed>`. Trong một implementation thông thường:

- Mỗi phần tử lưu **deadline** bất biến được tính từ một mốc `System.nanoTime()` cộng thêm khoảng delay.
- `getDelay(unit)` trả khoảng thời gian còn lại; giá trị **≤ 0** nghĩa đã đến hạn.
- `compareTo` sắp phần tử theo deadline để **deadline sớm nhất ở đầu queue**.
- `take()` chỉ trả phần tử khi phần tử đứng đầu đã hết delay; nếu chưa, nó chờ.
- `poll()` trả `null` khi đầu queue chưa đến hạn, **ngay cả khi `size() > 0`**.

`DelayQueue` lưu nhiều item nhưng *không tự chạy task* khi đến hạn. Nó chỉ làm cho item đủ điều kiện được lấy; vẫn cần một consumer đang gọi `take()`/`poll` rồi tự thực hiện hành động nghiệp vụ.

### Đồng hồ và trật tự

Dùng `System.nanoTime()` để biểu diễn **thời hạn tương đối** cho delay, vì nó thích hợp đo khoảng thời gian, không phải thời điểm lịch. Không so sánh trực tiếp một deadline nanoTime với `System.currentTimeMillis()`: hai giá trị có ý nghĩa khác nhau.

Nếu dùng `System.nanoTime() + delayNanos`, cần để ý overflow với delay cực lớn hoặc các deadline được tạo xa nhau bất thường. Với thời hạn thông thường nhỏ hơn phạm vi khoảng thời gian an toàn, code dưới đây đủ minh họa. Trong ứng dụng thực, chuẩn hóa đơn vị `TimeUnit` và quy định delay âm/0 có được chấp nhận không.

## 5. Ví dụ 2 — DelayQueue với thời điểm hết hạn xác định

Ví dụ thêm phần tử **không theo thứ tự đến hạn**, rồi lấy bằng `take()`. Để stdout ổn định, deadline được tạo từ cùng một mốc, khác nhau rõ ràng; không in số mili giây thực tế, vì thời gian scheduling thay đổi giữa các lần chạy.

Tạo file **`DelayQueueDemo.java`**:

```java
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class DelayQueueDemo {
    static final class Message implements Delayed {
        private final String name;
        private final long deadlineNanos;

        Message(String name, long deadlineNanos) {
            this.name = name;
            this.deadlineNanos = deadlineNanos;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long remaining = deadlineNanos - System.nanoTime();
            return unit.convert(remaining, TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            Message that = (Message) other;
            return Long.compare(this.deadlineNanos, that.deadlineNanos);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        DelayQueue<Message> queue = new DelayQueue<>();
        long now = System.nanoTime();

        queue.offer(new Message(
                "later", now + TimeUnit.MILLISECONDS.toNanos(150)));
        queue.offer(new Message(
                "ready", now - TimeUnit.MILLISECONDS.toNanos(10)));
        queue.offer(new Message(
                "soon", now + TimeUnit.MILLISECONDS.toNanos(50)));

        System.out.println("First: " + queue.take().name);
        System.out.println("Second: " + queue.take().name);
        System.out.println("Third: " + queue.take().name);
        System.out.println("Remaining: " + queue.size());
    }
}
```

```bash
javac DelayQueueDemo.java
java DelayQueueDemo
```

Kết quả:

```text
First: ready
Second: soon
Third: later
Remaining: 0
```

**Diễn giải:** cả ba phần tử được enqueue trước khi gọi take. `ready` đã quá hạn, đứng đầu và được trả ngay; `soon` có deadline sớm hơn `later`, nên được lấy tiếp khi đến hạn. `take()` có thể chờ tới lúc deadline tới, không phải cứ queue có phần tử là lấy ngay. Thời gian thực tế có thể thay đổi vì JVM/scheduler, nhưng **thứ tự theo deadline đã khai báo không đổi**.

`compareTo` ở ví dụ giả định mọi phần tử trong queue là cùng class `Message`; đây là đúng với `DelayQueue<Message>`. Nếu triển khai Delayed để trộn nhiều loại object, cần định nghĩa comparator tương thích, đối xứng, truyền tính và phản ánh đúng độ ưu tiên.

## 6. DelayQueue và ScheduledExecutorService có giống nhau?

Chúng liên quan về ý tưởng **đến hạn**, nhưng khác trách nhiệm:

| Công cụ | Chịu trách nhiệm gì? | Không tự làm gì? |
|---|---|---|
| `DelayQueue<E extends Delayed>` | Lưu item theo deadline, chỉ cho phép consumer lấy sau khi hết delay. | Không tự thực thi `Runnable`, không tạo scheduler worker. |
| `ScheduledExecutorService.schedule(...)` | Lên lịch thực thi `Runnable`/`Callable` sau delay. | Không đảm bảo bắt đầu đúng từng nano giây do lịch chạy. |
| `ScheduledThreadPoolExecutor` | Executor có worker và queue công việc hẹn giờ nội bộ. | Không tự bảo đảm business deadline của tác vụ hoặc I/O timeout. |

`ScheduledThreadPoolExecutor` dùng cơ chế queue trì hoãn nội bộ có liên quan ý tưởng **delayed work**, nhưng không nên suy rằng constructor của nó nhận một `DelayQueue` do người dùng cung cấp theo cách `ThreadPoolExecutor` nhận `BlockingQueue<Runnable>`.

Các trường hợp dùng DelayQueue trực tiếp: cache entry hết hạn, thông báo retry không được xử lý trước deadline, item cần được đưa vào pipeline sau thời gian chờ, timeout task do ứng dụng tự quản lý. Tuy nhiên, với **scheduler chỉ cần gọi Runnable vào một thời điểm**, `ScheduledExecutorService` thường đơn giản hơn.

### Một item có “delay 0” nghĩa là gì?

Nếu `getDelay(NANOSECONDS) <= 0`, item đủ điều kiện được lấy; **không** có nghĩa nó tự biến khỏi queue hoặc tự chạy. Consumer phải gọi `take()`/`poll()`. Nếu không có consumer, item đã đến hạn vẫn nằm trong queue và `size()` vẫn tính nó.

`drainTo(collection)` của `DelayQueue` chỉ chuyển các phần tử **đã hết delay** theo hợp đồng, không lấy các phần tử chưa đến hạn như một lệnh “drain toàn bộ bộ nhớ”. Một thao tác `clear()` (nếu được dùng) khác với chuyển quyền xử lý item đã đủ điều kiện.

## 7. DelayQueue: điều kiện chờ, tín hiệu và cancellation

Khi producer thêm một item có deadline **sớm hơn** item đang đứng đầu, consumer đang `take()` cần được đánh thức để đánh giá lại thời hạn đầu queue. `DelayQueue` xử lý điều phối này trong implementation, thay vì buộc bạn viết `Condition.awaitNanos()` bằng tay.

Cần lưu ý:

- `peek()` có thể trả phần tử **chưa đến hạn**; `poll()` khi chưa đến hạn lại trả `null`.
- `size() > 0` không đảm bảo `poll() != null`.
- `remainingCapacity()` là `Integer.MAX_VALUE`, nên không có bounded backlog tự nhiên; quá nhiều item có thể làm tăng heap.
- Nếu item thay đổi `deadlineNanos` **sau enqueue**, heap không tự sắp xếp lại. Giữ deadline immutable.
- Nếu consumer bị interrupt khi đang `take()`, nó có thể ném `InterruptedException`; code ứng dụng phải quản lý lifecycle và cleanup phù hợp.
- Nếu item bị hủy, cần định nghĩa rõ: xóa khỏi queue, đánh dấu canceled để consumer bỏ qua, hoặc để nguyên chờ rồi bỏ; không có một protocol cancellation nghiệp vụ tự động áp dụng cho mọi Delayed.
- Nếu có nhiều consumer, một item chỉ được lấy bởi một consumer; thứ tự **bắt đầu/hoàn tất xử lý** còn tùy scheduling.

Nếu cần **đúng một lần** thực thi qua nhiều JVM, DelayQueue in-memory không phải giải pháp đủ: tiến trình chết có thể làm mất item, và queue không cung cấp transaction/khôi phục.

## 8. SynchronousQueue: dung lượng 0, truyền tay trực tiếp

`SynchronousQueue<E>` khác hai queue trước về bản chất. Nó không chứa backlog nào, mà mỗi `put(e)` phải được ghép với một `take()` đang hoặc sắp đợi. Có thể hình dung một cuộc **rendezvous** giữa producer và consumer:

```text
Producer                             Consumer
--------                             --------
queue.put("work") ---- chờ ---->    queue.take()
                                        /
              ---- transfer item ------/
                    cả hai tiếp tục
```

Hợp đồng cơ bản:

- `put(e)` có thể chặn cho tới khi **một consumer nhận** e.
- `take()` có thể chặn cho tới khi **một producer gửi** e.
- `offer(e)` không chờ chỉ thành công nếu có một consumer phù hợp **sẵn sàng nhận ngay**.
- `offer(e, timeout, unit)` có thể đợi một khoảng cho có consumer; nếu không được ghép cặp sẽ trả false.
- `poll()` không chờ chỉ thành công khi có producer có thể chuyển giao ngay; `poll(timeout, unit)` đợi tới hạn cho producer.
- `size()` luôn là 0; `isEmpty()` luôn true; `remainingCapacity()` luôn 0; `peek()` luôn null.
- Không có phần tử tồn tại *giữa* lời gửi và lời nhận: chuyển giao xảy ra khi hai bên được ghép cặp.

**Đừng viết `if (queue.isEmpty()) ...` như cách kiểm tra xem SynchronousQueue có consumer đang chờ không.** Vì nó luôn báo empty theo contract, bất kể có thread đang đợi ở hai phía.

### Fairness

Constructor `new SynchronousQueue<>(true)` yêu cầu chính sách **fair** cho thứ tự thread đợi, còn mặc định là non-fair. Fairness ảnh hưởng việc chọn cặp waiter khi có cạnh tranh; nó **không tạo dung lượng**, không đảm bảo task chạy xong theo thứ tự và không biến chuyển giao thành giao dịch bền vững.

Fair mode có thể có đánh đổi hiệu năng tùy contention; không mặc định chọn nó hoặc bỏ nó mà không hiểu yêu cầu.

## 9. Ví dụ 3 — SynchronousQueue: offer thất bại, put chờ consumer

Ví dụ đầu tiên gọi `offer` khi chưa có consumer nên chắc chắn trả false. Sau đó tạo consumer, cho main `put` một item; `put` chỉ hoàn tất khi consumer nhận. `CountDownLatch` đồng bộ bước bắt đầu và `join()` bảo đảm kết quả in cuối không phụ thuộc thứ tự thread.

Tạo file **`SynchronousHandoffDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicReference;

public class SynchronousHandoffDemo {
    public static void main(String[] args) throws InterruptedException {
        SynchronousQueue<String> queue = new SynchronousQueue<>();
        System.out.println("Offer without consumer: "
                + queue.offer("early"));

        CountDownLatch consumerReady = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        Thread consumer = new Thread(() -> {
            consumerReady.countDown();
            try {
                received.set(queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        consumer.start();
        consumerReady.await();

        queue.put("delivered");
        consumer.join();

        System.out.println("Received: " + received.get());
        System.out.println("Queue size: " + queue.size());
        System.out.println("Remaining capacity: "
                + queue.remainingCapacity());
    }
}
```

```bash
javac SynchronousHandoffDemo.java
java SynchronousHandoffDemo
```

Kết quả khi không có interrupt ngoài:

```text
Offer without consumer: false
Received: delivered
Queue size: 0
Remaining capacity: 0
```

`consumerReady.countDown()` nằm ngay trước `take()`; main có thể gọi `put()` trước khi consumer thật sự vào take, nhưng khi đó **main chờ**, không mất item. Sau khi hai phía bắt cặp, `put` hoàn tất, `join` bảo đảm consumer đã ghi `received`. Đây là mô hình **backpressure tại lúc handoff**: producer không thể “đẩy vào queue cho xong” khi chưa có người nhận.

Không xem `SynchronousQueue` là queue capacity=1. Với capacity 1, producer có thể lưu một phần tử khi consumer chưa có mặt; còn SynchronousQueue **không lưu nổi một item** và luôn cần một phía giao dịch đối ứng.

## 10. Timeout và interrupt trên SynchronousQueue

Handoff có thể chờ rất lâu nếu không có đối tác. Vì vậy cancellation và timeout đặc biệt quan trọng:

```java
boolean transferred =
        queue.offer("work", 200, TimeUnit.MILLISECONDS);
```

Đây là trích đoạn cần khai báo `queue` và xử lý `InterruptedException`. Khi không có consumer trong thời gian đó, `transferred` trở thành false; nếu có consumer và ghép cặp thành công, trả true.

Một producer/consumer đợi trong `put`/`take` có thể bị interrupt. Thread nên xử lý `InterruptedException` theo chính sách lifecycle: ném tiếp, hoặc khôi phục cờ `Thread.currentThread().interrupt()` rồi thoát; **không nuốt interrupt và lặp vô hạn**.

Nếu bên gửi `put` kết thúc **thành công**, đã có consumer ghép cặp nhận phần tử. Nhưng điều đó **không** chứng minh consumer đã xử lý xong phần việc — consumer có thể vừa nhận item và đang tiếp tục chạy. Nếu cần xác nhận hoàn tất nghiệp vụ, dùng `Future`, latch, acknowledgement hoặc transaction riêng.

Mọi thao tác `offer/poll` không chờ đều bị ảnh hưởng bởi **thời điểm hai thread gặp nhau**; không nên viết test kỳ vọng nonblocking offer sẽ luôn true chỉ vì đã start một consumer. Một test đúng phải điều phối trạng thái chờ hoặc chọn `put/take` phù hợp.

## 11. SynchronousQueue trong ThreadPoolExecutor

`ThreadPoolExecutor` dùng `BlockingQueue<Runnable>` làm **work queue**. Trình tự tiếp nhận task thông thường:

1. Khi số worker thấp hơn `corePoolSize`, executor cố tạo worker để chạy task.
2. Sau khi đạt core, nó gọi `workQueue.offer(task)`.
3. Nếu `offer` thất bại, nó cố tạo thêm worker tới `maximumPoolSize`.
4. Nếu không thể nhận task và không thêm worker được, `RejectedExecutionHandler` quyết định việc từ chối.

Với **`SynchronousQueue`**, `offer(task)` thất bại khi không có worker rảnh đang chờ nhận trực tiếp. Vì queue không có slot, executor thường **tăng worker thay vì tích lũy backlog** khi có thêm task đang chạy, cho tới khi đạt maximum.

Điều này đối lập với `LinkedBlockingQueue` mặc định capacity lớn: sau khi đạt core, queue thường nhận task nên executor không cần tạo non-core worker trong đường admission thông thường.

### Ví dụ 4 — Hai worker đang bận, task thứ ba bị reject

Ta cấu hình core=1, max=2, `SynchronousQueue` và `AbortPolicy`. Hai task dùng latch để giữ worker bận; task thứ ba không có worker rảnh và pool đã đạt max nên bị từ chối. Nhờ điều phối latch, stdout không phụ thuộc tình cờ vào tốc độ task.

Tạo file **`SynchronousExecutorDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SynchronousExecutorDemo {
    private static Runnable blockingTask(
            CountDownLatch started, CountDownLatch release) {
        return () -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 2, 0, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.AbortPolicy()
        );

        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try {
            pool.execute(blockingTask(firstStarted, release));
            firstStarted.await();

            pool.execute(blockingTask(secondStarted, release));
            secondStarted.await();

            boolean rejected = false;
            try {
                pool.execute(() -> { });
            } catch (RejectedExecutionException e) {
                rejected = true;
            }

            System.out.println("Pool size: " + pool.getPoolSize());
            System.out.println("Queue size: " + pool.getQueue().size());
            System.out.println("Third rejected: " + rejected);
        } finally {
            release.countDown();
            pool.shutdown();
        }

        boolean terminated = pool.awaitTermination(
                5, TimeUnit.SECONDS);
        System.out.println("Terminated: " + terminated);
        System.out.println("Completed worker tasks: "
                + pool.getCompletedTaskCount());
    }
}
```

```bash
javac SynchronousExecutorDemo.java
java SynchronousExecutorDemo
```

Kết quả bình thường:

```text
Pool size: 2
Queue size: 0
Third rejected: true
Terminated: true
Completed worker tasks: 2
```

Task thứ nhất chiếm core worker, task thứ hai không thể xếp hàng nên pool tạo worker thứ hai. Cả hai đều giữ ở `release` nên task thứ ba không tìm được worker đang đợi handoff và max=2 đã đạt: `AbortPolicy` ném `RejectedExecutionException`. Sau `release.countDown()`, hai worker hoàn tất; pool shutdown rồi `awaitTermination` xác nhận kết thúc.

Đừng suy rằng queue size=0 là “không có tác vụ”. Ở SynchronousQueue, **queue size luôn 0**, ngay cả khi nhiều worker đang chạy; để giám sát pool phải xem cả `getActiveCount()`, throughput, rejection và trạng thái lifecycle.

## 12. Vì sao Executors.newCachedThreadPool() cần thận trọng?

`Executors.newCachedThreadPool()` dùng cấu hình theo mô hình **direct handoff** với `SynchronousQueue`, có khả năng tạo nhiều worker mới khi chưa có worker rảnh để nhận task; giới hạn số thread tối đa rất cao theo implementation thông thường.

Ưu điểm: các task ngắn, phân bố không đều có thể được nhận nhanh, worker nhàn rỗi được tái sử dụng và dọn sau thời gian keep-alive.

Rủi ro: nếu task chặn lâu hoặc tốc độ nộp vượt xa khả năng hoàn tất, pool có thể tạo **quá nhiều thread**, gây tiêu thụ stack memory, context switching, tắc tài nguyên downstream hoặc `OutOfMemoryError`. Không tự xem cached pool là giới hạn năng lực hay backpressure an toàn.

Trong một dịch vụ cần giới hạn tài nguyên, có thể sử dụng `ThreadPoolExecutor` với **max worker cụ thể** và `SynchronousQueue`, cùng một rejection policy rõ ràng. Hoặc dùng bounded queue nếu muốn chấp nhận một lượng backlog hữu hạn. Lựa chọn dựa trên latency, CPU/I/O, hạn mức kết nối và SLA, không chỉ trên tên queue.

## 13. So sánh trực diện ba queue

| Tiêu chí | `PriorityBlockingQueue` | `DelayQueue` | `SynchronousQueue` |
|---|---|---|---|
| Lý do lấy phần tử | Theo **priority/comparator**. | Đầu queue đã **hết delay**. | Có producer và consumer **ghép cặp trực tiếp**. |
| Lưu phần tử trong queue | Có. | Có. | **Không**, capacity=0. |
| Dung lượng có bound qua constructor | Không. | Không. | Cố định bằng **0**. |
| `offer(e)` không chờ | Thường nhận nếu đủ bộ nhớ. | Thường nhận nếu đủ bộ nhớ. | Chỉ thành công nếu có consumer phù hợp ngay. |
| `take()` chờ khi | Queue rỗng. | Chưa có item đã đến hạn. | Chưa có producer ghép cặp. |
| `poll()` trả null khi | Rỗng. | Rỗng hoặc đầu chưa đến hạn. | Không có producer ghép cặp ngay. |
| `size()` | Số item đang lưu. | Số item gồm cả chưa đến hạn. | Luôn 0. |
| `remainingCapacity()` | `Integer.MAX_VALUE`. | `Integer.MAX_VALUE`. | 0. |
| Thứ tự FIFO tuyệt đối | Không; ưu tiên quyết định. | Không; deadline quyết định. | Không có backlog để FIFO item. |
| Fairness constructor | Không có option fair tương đương ArrayBlockingQueue. | Không có option fair công khai. | Có `SynchronousQueue(boolean fair)`. |
| Dùng điển hình | Ưu tiên công việc chờ, triage theo rank. | Hẹn giờ, retry delay, expire item. | Handoff đồng bộ, pool mở worker theo nhu cầu. |
| Rủi ro chính | Starvation priority thấp, memory backlog. | Memory backlog, hiểu sai thời điểm đến hạn. | Producer/consumer chờ vô hạn hoặc pool tạo nhiều worker. |

**Chọn queue theo điều kiện được phép lấy phần tử**, không chỉ vì cả ba đều triển khai BlockingQueue.

## 14. Java Memory Model và an toàn khi chuyển giao item

Các concurrent collection như BlockingQueue có hợp đồng memory consistency: những hành động của producer **trước khi đưa object vào queue** có quan hệ *happens-before* với những hành động của consumer **sau khi truy cập hoặc lấy object đó ra**.

Ví dụ ý tưởng:

```java
// Trích đoạn producer.
Message message = new Message();
message.payload = 42;
queue.put(message);

// Trích đoạn consumer (ở thread khác).
Message received = queue.take();
System.out.println(received.payload);
```

Đoạn này giả định `Message` và `queue` được định nghĩa thích hợp. Quan hệ happens-before bảo đảm dữ liệu đã được chuẩn bị **trước khi đưa vào queue** được chuyển giao theo quy tắc Java Memory Model (JMM). Điều đó **không biến mọi mutation sau khi enqueue thành thread-safe**.

- Với PriorityBlockingQueue, field ảnh hưởng comparator nên **immutable** khi phần tử đang nằm trong heap.
- Với DelayQueue, deadline nên **immutable** để thời điểm đã xếp hàng không bị thay đổi ngoài giao thức.
- Với SynchronousQueue, handoff trao quyền truy cập item cho consumer, nhưng producer tiếp tục sửa cùng object song song vẫn cần quy tắc ownership/synchronization.
- Nếu item giữ reference tới mutable data ở nơi khác, an toàn phải xét theo toàn bộ object graph chứ không chỉ reference của item.

Dùng `record`, final fields, defensive copy hoặc quy tắc chuyển quyền sở hữu sẽ giảm lỗi dữ liệu khi trao đổi giữa nhiều thread.

## 15. Timeout, interruption và shutdown không giống nhau

Cả ba queue đều có thao tác chờ có thể bị interrupt:

- `take()` có thể ném `InterruptedException` khi thread đang đợi bị interrupt.
- `put()` có thể chờ trong SynchronousQueue, nhưng với PriorityBlockingQueue/DelayQueue không bị chặn vì capacity theo hợp đồng unbounded; vẫn có contract interruption của interface khi sử dụng.
- `offer(timeout)`/`poll(timeout)` cho một thời hạn theo caller, nhưng lý do chưa ghép cặp/chưa đến hạn vẫn phụ thuộc từng queue.
- BlockingQueue **không có phương thức `close()` thống nhất**; muốn tắt producer–consumer cần sentinel, interrupt, timeout hoặc lifecycle state tùy thiết kế.

**Cancellation không tự rollback item đã chuyển giao.** Với SynchronousQueue, khi producer `put` xong, consumer đã nhận item nhưng có thể chưa xử lý. Với DelayQueue, item đã hết hạn nhưng chưa có consumer vẫn có thể nằm trong queue. Với PriorityBlockingQueue, task đã bị consumer lấy ra không tự trở lại queue chỉ vì worker bị interrupt.

Khi dùng queue cùng executor, owner phải quản lý `shutdown()`, `shutdownNow()`, `awaitTermination()` và tác vụ còn chờ theo hợp đồng của executor. Không được nhầm queue rỗng với pool đã hoàn tất mọi task.

## 16. Lỗi phổ biến khi dùng ba queue

| Sai lầm | Vì sao sai | Hướng sửa |
|---|---|---|
| Coi PriorityBlockingQueue là FIFO. | Order theo priority; item cùng priority không ổn định thứ tự. | Comparator có tie-breaker sequence nếu cần. |
| Thay đổi field priority sau offer. | Heap không tự heapify lại khi field mutable đổi. | Dùng key immutable hoặc remove/cập nhật/reinsert đúng giao thức. |
| Coi initialCapacity của PriorityBlockingQueue là bound. | Array có thể mở rộng; không phải giới hạn backlog. | Tự giới hạn producer/permit hoặc chọn queue bounded. |
| In iterator và kết luận đó là priority order. | Iterator không cam kết thứ tự. | Poll/take hoặc snapshot rồi sort. |
| Cho rằng priority cao đang chờ sẽ preempt task đã chạy. | Queue không quản lý CPU hay tác vụ đã dequeue. | Thiết kế scheduler/preemption/cancellation riêng. |
| Dùng DelayQueue nhưng Delayed.compareTo không nhất quán getDelay. | Head không phản ánh deadline sớm nhất. | So sánh deadline bất biến theo cùng thước đo. |
| Gọi `poll()` khi DelayQueue.size()>0 và nghĩ chắc lấy được. | Item có thể chưa hết delay. | Dùng take/timed poll và kiểm tra kết quả. |
| Nghĩ DelayQueue tự chạy Runnable đúng giờ. | Queue chỉ lưu và làm đủ điều kiện dequeue. | Dùng ScheduledExecutorService nếu cần tự chạy task. |
| Đổi deadline của Delayed object khi đã enqueue. | Queue không tự reorder. | Dùng immutable deadline; reinsert hoặc scheduler có protocol cập nhật. |
| Nghĩ SynchronousQueue có một slot buffer. | Capacity luôn bằng 0. | Dùng ArrayBlockingQueue(1) khi muốn lưu một phần tử. |
| Gọi SynchronousQueue.offer mà chưa có consumer. | Thường trả false ngay, item không được giữ. | Dùng put hoặc timed offer nếu muốn đợi đối tác. |
| Dựa vào SynchronousQueue.size() để biết số task pool. | Size luôn 0 dù worker đang xử lý. | Đo active tasks, completed, rejection, throughput. |
| Dùng cached pool với blocking task vô hạn. | Có thể tạo rất nhiều worker và cạn tài nguyên. | Giới hạn max thread, timeout và rejection/backpressure. |
| Nuốt InterruptedException khi đang chờ. | Hủy không có hiệu lực, có thể chờ vô hạn. | Ném tiếp hoặc khôi phục flag rồi thoát theo contract. |
| Chọn queue chỉ theo throughput một microbenchmark. | Không xét latency, backlog, ordering, memory, fairness. | Đo workload thật và xác định contract trước. |

## 17. Lựa chọn đúng theo tình huống

**Tình huống A — Xử lý công việc theo mức khẩn cấp:** Nếu task cần được lấy theo một `priority` đã biết và ít đổi, `PriorityBlockingQueue` là lựa chọn phù hợp. Cần kiểm soát starvation và giới hạn backlog vì queue không bounded theo constructor.

**Tình huống B — Retry sau 5 giây:** Nếu item chỉ được worker nhận lại sau deadline, `DelayQueue<Delayed>` giúp lưu item và chờ đúng lúc. Nếu chỉ muốn scheduler **tự chạy** một callback, dùng `ScheduledExecutorService`.

**Tình huống C — Bên gửi phải chờ bên nhận:** Nếu không muốn backlog mà cần transfer trực tiếp giữa hai bên, `SynchronousQueue` có ý nghĩa. Cần timeout/interrupt khi không có đối tác.

**Tình huống D — Executor không lưu backlog, tạo worker tới mức giới hạn:** `ThreadPoolExecutor` với `SynchronousQueue`, `corePoolSize`, `maximumPoolSize` và rejection policy rõ ràng. Nếu cần một backlog nhỏ thay vì handoff thuần, dùng bounded queue phù hợp.

**Tình huống E — Chỉ cần producer–consumer FIFO với capacity:** Dùng `ArrayBlockingQueue` hoặc `LinkedBlockingQueue(capacity)`, **không ép** một trong ba queue của bài giải quyết sai bài toán.

Trong mọi trường hợp, quyết định còn tùy CPU/I/O, số producer/consumer, độ trễ, mức độ quan trọng của ordering, lượng backlog chấp nhận được và cách dừng ứng dụng.

## 18. Checklist thiết kế và kiểm thử

1. **Định nghĩa điều kiện dequeue:** theo priority, theo deadline hay chỉ khi có người nhận?
2. **Xác định capacity:** unbounded theo API có nguy cơ heap; zero-capacity cần giao thức handoff.
3. **Định nghĩa so sánh:** comparator ổn định cho PriorityBlockingQueue; `compareTo` nhất quán deadline cho DelayQueue.
4. **Không mutate key khi đã enqueue:** immutable priority/deadline giúp cấu trúc ưu tiên đúng.
5. **Xác định timeout:** caller có thể đợi bao lâu, và nếu không có dữ liệu/đối tác thì làm gì?
6. **Có xử lý InterruptedException:** không nuốt yêu cầu hủy trong vòng lặp không giới hạn.
7. **Định nghĩa completion:** đã dequeue/handoff khác đã xử lý nghiệp vụ xong.
8. **Quản lý lifecycle:** producer–consumer không có `BlockingQueue.close()` chung; executor phải shutdown theo owner.
9. **Theo dõi overload:** queue depth/age cho priority/delay; active workers và rejection cho SynchronousQueue/executor.
10. **Test ordering đúng:** với priority tie-breaker, deadline khác nhau rõ ràng và các latch bảo đảm lịch chạy.
11. **Test cancellation:** khi consumer không nhận, khi item chưa đến hạn, khi shutdown đang diễn ra.
12. **Kiểm thử cả tính đúng lẫn hiệu năng:** starvation, heap, CPU, GC, latency và throughput.
13. **Đừng suy thứ tự hoàn thành từ thứ tự dequeue:** nhiều worker xử lý đồng thời sẽ có thời lượng khác nhau.
14. **Không tự dùng concurrent queue làm durable broker:** cần persistence/idempotency nếu dữ liệu phải sống qua JVM restart.

## 19. Câu hỏi tự kiểm tra

1. **PriorityBlockingQueue có FIFO không?** Không; phần tử lấy ra theo comparator/natural priority. Nếu bằng nhau, thứ tự không được bảo đảm.
2. **`new PriorityBlockingQueue<>(100)` có chặn thêm ở 100 item không?** Không; 100 là initial capacity.
3. **Vì sao cần tie-breaker sequence?** Để có thứ tự xác định giữa các task cùng priority nếu nghiệp vụ yêu cầu.
4. **Nếu sửa priority ngay trong object đang ở queue thì heap có tự reorder?** Không.
5. **DelayQueue cần interface gì?** `Delayed` với `getDelay(TimeUnit)` và `compareTo`.
6. **DelayQueue.size()>0 có bảo đảm poll trả item không?** Không; item đứng đầu có thể chưa hết delay.
7. **DelayQueue có tự gọi Runnable sau deadline không?** Không, chỉ cho phép consumer lấy item đến hạn.
8. **SynchronousQueue có bao nhiêu slot lưu?** Bằng 0, không có backlog.
9. **SynchronousQueue.offer(e) không timeout có luôn nhận item?** Không; cần có consumer đối ứng sẵn sàng.
10. **SynchronousQueue.put(e) xong có nghĩa consumer đã xử lý xong?** Không; chỉ cho biết handoff đã xảy ra.
11. **SynchronousQueue.size() phản ánh active worker không?** Không; luôn bằng 0.
12. **Vì sao SynchronousQueue làm ThreadPoolExecutor dễ mở non-core worker?** Vì queue không giữ task; offer thất bại khi không có worker rảnh nhận.
13. **`newCachedThreadPool()` có nguy cơ gì khi nhiều blocking task?** Pool có thể mở rất nhiều worker, gây cạn tài nguyên.
14. **BlockingQueue có close() chung không?** Không; phải thiết kế lifecycle/cancellation riêng.
15. **Queue memory consistency bảo đảm gì?** Hành động trước enqueue/handoff happens-before hành động sau dequeue/nhận tương ứng; không bảo vệ mutation cạnh tranh sau đó.
16. **Khi nào nên dùng ArrayBlockingQueue thay vì ba queue trên?** Khi chỉ cần FIFO bounded producer–consumer thông thường.

**Ghi nhớ:** `PriorityBlockingQueue` sắp theo **mức ưu tiên**, `DelayQueue` chờ **đến hạn**, `SynchronousQueue` yêu cầu **hai thread chuyển giao trực tiếp mà không có bộ đệm**. Cùng là BlockingQueue nhưng ordering, capacity, timeout, backpressure và tác động tới executor khác nhau; lựa chọn phải bắt đầu từ hợp đồng công việc.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-009">ConcurrentSkipListMap và ConcurrentSkipListSet</a>

<details>
<summary>Xem chi tiết</summary>

# ConcurrentSkipListMap và ConcurrentSkipListSet trong Java

Trong Java concurrency, không phải collection nào được nhiều thread truy cập cũng chỉ cần lưu và tìm kiếm phần tử. Một số nghiệp vụ còn cần **duy trì khóa được sắp xếp**, hỏi phần tử gần nhất, duyệt theo thứ tự hoặc làm việc với một **khoảng khóa** trong khi thread khác đang cập nhật. Hai cấu trúc thuộc `java.util.concurrent` giải quyết nhóm nhu cầu đó là:

- **`ConcurrentSkipListMap<K,V>`:** map concurrent **có thứ tự theo khóa**, triển khai các hợp đồng `ConcurrentNavigableMap`, `NavigableMap` và `SortedMap`; hỗ trợ tìm khóa trước/sau và range view.
- **`ConcurrentSkipListSet<E>`:** set concurrent **có thứ tự phần tử**, triển khai `NavigableSet` và `SortedSet`; hỗ trợ thêm/xóa/tìm hàng xóm và range view.

Cả hai dựa trên ý tưởng **skip list**, cấu trúc nhiều mức liên kết giúp tìm kiếm, thêm và xóa với **độ phức tạp kỳ vọng O(log n)**. Các thao tác trên collection được thiết kế cho truy cập concurrent mà không cần khóa toàn bộ cấu trúc ở phía người dùng trong những trường hợp thông thường.

Nhưng từ "concurrent" **không** có nghĩa rằng mọi chuỗi nhiều thao tác là transaction, iterator là snapshot bất biến, số lượng phần tử luôn được đọc trong O(1), hoặc các object value mutable tự động trở nên thread-safe. Bài này phân tích những ranh giới đó bằng Java 17+, chú trọng giải thích API, ví dụ chạy được và lỗi thiết kế thường gặp.

**Phạm vi:** **Concurrent Collections** trong **Java concurrency nói chung**. Không dùng Spring-specific collection, annotation hoặc scheduler; phần liên quan tới Spring được dành cho chương riêng.

## 1. Vì sao cần một concurrent collection có thứ tự?

Giả sử hệ thống giữ các nhiệm vụ theo timestamp:

```text
1700000100 -> task A
1700000120 -> task B
1700000190 -> task C
```

Trong lúc worker A thêm nhiệm vụ mới, worker B cần tìm nhiệm vụ có timestamp **lớn nhất không vượt deadline**; worker C cần duyệt khoảng timestamp. `ConcurrentHashMap` giúp truy cập concurrent theo key nhưng **không duy trì thứ tự khóa** để thực hiện `floorKey`, `ceilingKey` hay `subMap`.

`TreeMap` có thứ tự và NavigableMap API, nhưng bản thân nó **không an toàn khi nhiều thread cùng sửa/đọc mà không có đồng bộ phù hợp**. Chỉ thêm `volatile` cho biến trỏ tới `TreeMap` cũng không làm các thao tác nội bộ thread-safe.

`ConcurrentSkipListMap` đáp ứng hai yêu cầu **đồng thời**:

1. Các operation đơn lẻ có hợp đồng concurrent.
2. Khóa được tổ chức theo **natural ordering** hoặc một `Comparator` đã cung cấp.

`ConcurrentSkipListSet` là cấu trúc tương tự khi chỉ quan tâm **tập khóa/phần tử**, không cần value.

## 2. Skip list: hình dung cấu trúc nhiều tầng

Skip list duy trì thứ tự tại tầng thấp nhất và dùng các "đường tắt" tại các tầng cao hơn:

```text
Level 3: HEAD ----------------------> 50
Level 2: HEAD ----------> 30 ------> 50
Level 1: HEAD -> 10 ---> 30 -> 40 -> 50
Level 0: HEAD -> 10 -> 20 -> 30 -> 40 -> 50 -> 60
                        ^
                   ascending keys
```

Sơ đồ chỉ minh họa ý tưởng; số tầng thực tế và cách giữ liên kết là chi tiết triển khai. Khi tìm `40`, thuật toán có thể đi qua đường tắt ở tầng cao rồi đi xuống thay vì quét tuần tự mọi nút từ đầu.

Ý nghĩa:

- Khóa/phần tử được truy cập theo thứ tự tăng dần.
- Search, insert, remove có **expected average O(log n)**, không phải cam kết hard worst-case cho mọi lịch chạy và mọi input.
- Triển khai concurrent phải phối hợp những thay đổi liên kết giữa nhiều thread; không được suy rằng toàn bộ collection dùng một monitor chung chặn mọi hoạt động.
- Skip list dùng thêm con trỏ/chỉ mục để đổi lấy khả năng điều hướng có thứ tự.
- Thứ tự chỉ là **thứ tự comparator**, không phải thứ tự thread đến hoặc thứ tự gọi `put`.

Đừng viết business logic phụ thuộc vào chiều cao tầng hoặc quyết định ngẫu nhiên nội bộ của một node; API bảo đảm quan hệ có thứ tự và các operation, không bảo đảm hình dạng graph.

## 3. Khởi tạo `ConcurrentSkipListMap`

Chỉ cần import:

```java
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentNavigableMap;
```

Một vài dạng khởi tạo:

```java
ConcurrentSkipListMap<Integer, String> byId =
        new ConcurrentSkipListMap<>();

ConcurrentSkipListMap<String, Integer> reverse =
        new ConcurrentSkipListMap<>(
                java.util.Comparator.reverseOrder());
```

Đây là các declaration minh họa trong một scope hợp lệ. Với constructor không có comparator, key phải có **natural ordering** tương thích, thường bằng `Comparable`; ví dụ `Integer`, `Long`, `String`.

Khi truyền comparator, map dùng comparator đó để sắp xếp và xác định **hai key có cùng vị trí logic hay không**. Do vậy, comparator không chỉ ảnh hưởng cách in ra, mà ảnh hưởng luôn semantics lookup/duplicate keys.

Nếu nhiều thread dùng cùng map, có thể chia sẻ reference sau khi khởi tạo theo một cơ chế publication phù hợp: truyền qua constructor, final field, hoặc thông qua executor/concurrent mechanism. Bản thân class concurrent không khiến một reference chưa được công bố an toàn tự động có visibility tốt.

## 4. Các thao tác map cơ bản

Nhóm operation thường dùng:

| Operation | Ý nghĩa |
| --- | --- |
| `put(key,value)` | Gắn value, trả value cũ nếu có |
| `get(key)` | Đọc mapping hiện tại |
| `remove(key)` | Xóa mapping nếu tồn tại |
| `putIfAbsent(key,value)` | Chỉ thêm nếu chưa có mapping theo contract |
| `remove(key,value)` | Chỉ xóa nếu mapping hiện tại khớp value mong đợi |
| `replace(key,old,new)` | Thay có điều kiện theo value |
| `computeIfAbsent(key,f)` | Tạo value khi chưa có theo cơ chế remapping |
| `computeIfPresent(key,f)` | Cập nhật khi key hiện có |
| `compute(key,f)` | Tính mapping mới từ mapping hiện tại |
| `merge(key,value,f)` | Kết hợp value mới với value cũ nếu đã có |

Các operation có tính chất update nguyên tử theo **một mapping/key** theo hợp đồng tương ứng. Điều đó **không tạo transaction xuyên hai key**.

Ví dụ sai khi cần invariant nhiều key:

```java
if (map.get("a") > 0) {
    map.put("a", map.get("a") - 1);
    map.put("b", map.get("b") + 1);
}
```

Đây là **anti-pattern minh họa**; `map` giả định đã có hai key/value. Ba lần đọc/ghi liên tiếp không tạo atomic transfer. Thread khác có thể xen vào, khiến mất cập nhật hoặc invariant tổng `a+b` bị vi phạm. Khi cần transaction nhiều key, thiết kế đồng bộ hoặc state object nguyên tử cấp cao hơn.

## 5. Ví dụ Java 17+: thứ tự, lookup và key lân cận

Lưu thành `SkipListMapBasicsDemo.java`:

```java
import java.util.concurrent.ConcurrentSkipListMap;

public class SkipListMapBasicsDemo {
    public static void main(String[] args) {
        ConcurrentSkipListMap<Integer, String> map =
                new ConcurrentSkipListMap<>();

        map.put(30, "thirty");
        map.put(10, "ten");
        map.put(50, "fifty");
        map.put(20, "twenty");

        System.out.println("Keys: " + map.navigableKeySet());
        System.out.println("First: " + map.firstKey());
        System.out.println("Last: " + map.lastKey());
        System.out.println("Floor 25: " + map.floorKey(25));
        System.out.println("Ceiling 25: " + map.ceilingKey(25));
        System.out.println("Lower 20: " + map.lowerKey(20));
        System.out.println("Higher 20: " + map.higherKey(20));
        System.out.println("Value at 30: " + map.get(30));
    }
}
```

Chạy:

```bash
javac SkipListMapBasicsDemo.java
java SkipListMapBasicsDemo
```

Stdout chính xác khi không có thread khác sửa map:

```text
Keys: [10, 20, 30, 50]
First: 10
Last: 50
Floor 25: 20
Ceiling 25: 30
Lower 20: 10
Higher 20: 30
Value at 30: thirty
```

Thứ tự key là **tăng dần theo `Integer.compareTo`**, dù thứ tự `put` ban đầu là 30, 10, 50, 20. Bốn operation `floorKey`, `ceilingKey`, `lowerKey` và `higherKey` có semantics riêng, không nên thay bằng cách lấy "phần tử gần nhất" rồi đoán.

## 6. Phân biệt `lower/floor/ceiling/higher`

Với một key mục tiêu `k`:

| Operation | Quan hệ với `k` | Nếu không có ứng viên |
| --- | --- | --- |
| `lowerKey(k)` | Khóa lớn nhất **nhỏ hơn k** | `null` |
| `floorKey(k)` | Khóa lớn nhất **nhỏ hơn hoặc bằng k** | `null` |
| `ceilingKey(k)` | Khóa nhỏ nhất **lớn hơn hoặc bằng k** | `null` |
| `higherKey(k)` | Khóa nhỏ nhất **lớn hơn k** | `null` |

Ví dụ keys `[10,20,30]`:

```text
lowerKey(20)   -> 10
floorKey(20)   -> 20
ceilingKey(20) -> 20
higherKey(20)  -> 30

floorKey(5)    -> null
ceilingKey(35) -> null
```

Không đánh đồng `floor` với **làm tròn số thực**; tên này chỉ quan hệ thứ tự. Với comparator tùy chỉnh, "nhỏ hơn" và "lớn hơn" đều **theo comparator**, không phải nhất thiết theo giá trị số hoặc thứ tự chữ cái mặc định.

Nếu cần cả key và value tương ứng trong **một entry**, dùng `lowerEntry`, `floorEntry`, `ceilingEntry` hoặc `higherEntry`. Việc lấy key rồi gọi `get(key)` hai lần là **hai operation khác nhau**; dưới concurrent removal, value có thể đã biến mất giữa hai lần đọc.

## 7. First/last và polling entry: đọc khác với xóa

Map cung cấp:

- `firstKey()`, `lastKey()`: lấy key đầu/cuối, ném `NoSuchElementException` nếu map đang rỗng.
- `firstEntry()`, `lastEntry()`: trả một entry tương ứng, hoặc `null` khi không có.
- `pollFirstEntry()`, `pollLastEntry()`: **xóa và trả** entry đầu/cuối theo thứ tự.
- `descendingMap()`: view điều hướng theo thứ tự ngược.
- `navigableKeySet()`, `descendingKeySet()`: view các keys.

`pollFirstEntry` đặc biệt hữu ích khi nhiều worker muốn **claim phần tử có khóa ưu tiên nhỏ nhất**: mỗi lần xóa một entry là một operation, nên không cần tự viết `firstEntry` rồi `remove` tách rời. Nhưng:

- Worker khác có thể thêm key nhỏ hơn **ngay sau** lần poll.
- Không thể suy rằng đã poll được "phần tử nhỏ nhất của cả tương lai".
- Nếu task cần kiểm soát deadline và chờ khi map rỗng, CSLM **không phải blocking queue**: phải kết hợp signaling/condition hoặc dùng cấu trúc scheduler thích hợp.
- Khi có comparator tùy chỉnh, "first" là **nhỏ nhất theo comparator**.

## 8. Ví dụ Java 17+: poll và descending view

Lưu thành `SkipListPollDemo.java`:

```java
import java.util.concurrent.ConcurrentSkipListMap;

public class SkipListPollDemo {
    public static void main(String[] args) {
        ConcurrentSkipListMap<Integer, String> map =
                new ConcurrentSkipListMap<>();

        map.put(5, "five");
        map.put(1, "one");
        map.put(3, "three");

        System.out.println("Ascending: " + map.navigableKeySet());
        System.out.println("Descending: " + map.descendingKeySet());

        var first = map.pollFirstEntry();
        var last = map.pollLastEntry();

        System.out.println("Polled first key: " + first.getKey());
        System.out.println("Polled last key: " + last.getKey());
        System.out.println("Remaining keys: " + map.navigableKeySet());
    }
}
```

```bash
javac SkipListPollDemo.java
java SkipListPollDemo
```

Stdout chính xác:

```text
Ascending: [1, 3, 5]
Descending: [5, 3, 1]
Polled first key: 1
Polled last key: 5
Remaining keys: [3]
```

`pollFirstEntry()` và `pollLastEntry()` **làm thay đổi** map. Ở chương trình không có writer cạnh tranh, kết quả lần lượt là 1 và 5. Nếu dùng `firstEntry` thay cho `pollFirstEntry`, entry đầu vẫn nằm trong map.

Những entry trả về từ operation điều hướng là các bản ghi **đại diện mapping tại thời điểm quan sát**, không nên coi `Map.Entry.setValue` trên chúng là cách cập nhật mapping trong collection; muốn thay value, hãy dùng API map như `replace` hoặc `compute`.

## 9. `ConcurrentSkipListSet`: chỉ cần phần tử đã sắp xếp

Khi không cần value, dùng `ConcurrentSkipListSet<E>`. Nó hỗ trợ:

- `add(e)` và `remove(e)` theo membership.
- `contains(e)` để kiểm tra phần tử.
- `first()` và `last()`.
- `lower(e)`, `floor(e)`, `ceiling(e)`, `higher(e)`.
- `pollFirst()` và `pollLast()` để lấy và xóa đầu/cuối.
- `subSet`, `headSet`, `tailSet` và `descendingSet`.

Giống map, set sắp xếp theo natural ordering hoặc comparator. Hai phần tử **so sánh bằng 0 theo comparator** được coi tương đương cho membership, ngay cả khi `equals` nói khác.

`ConcurrentSkipListSet` thích hợp cho ordered unique IDs, watermark candidates, timestamp markers hay tập token cần truy vấn theo khoảng. Nó **không phải multiset**: nếu cần đếm số lần xuất hiện của cùng một key, phải dùng map key → count với update phù hợp.

## 10. Ví dụ Java 17+: các operation của set

Lưu thành `SkipListSetDemo.java`:

```java
import java.util.concurrent.ConcurrentSkipListSet;

public class SkipListSetDemo {
    public static void main(String[] args) {
        ConcurrentSkipListSet<Integer> set =
                new ConcurrentSkipListSet<>();

        System.out.println("Added 20: " + set.add(20));
        set.add(10);
        set.add(30);
        System.out.println("Duplicate 20: " + set.add(20));
        System.out.println("Sorted: " + set);
        System.out.println("Floor 25: " + set.floor(25));
        System.out.println("Ceiling 25: " + set.ceiling(25));
        System.out.println("Polled first: " + set.pollFirst());
        System.out.println("Remaining: " + set);
    }
}
```

```bash
javac SkipListSetDemo.java
java SkipListSetDemo
```

Stdout chính xác:

```text
Added 20: true
Duplicate 20: false
Sorted: [10, 20, 30]
Floor 25: 20
Ceiling 25: 30
Polled first: 10
Remaining: [20, 30]
```

`add(20)` lần hai trả `false` vì 20 đã tồn tại theo comparator. Set không lưu duplicate. `pollFirst` vừa trả 10 vừa xóa 10. Các output in collection trực tiếp chỉ được cam kết trong ví dụ vì không có thread khác đang sửa.

## 11. Map và set đều không chấp nhận `null`

Hai class này **không cho key/element `null`**; `ConcurrentSkipListMap` cũng **không cho value `null`**.

Lý do quan trọng ở cấp API: `map.get(key) == null` được dùng để biểu thị **không có mapping**, không phải "mapping tồn tại nhưng có value null". Trong môi trường concurrent, để null vừa biểu thị absent vừa biểu thị một value hợp lệ sẽ làm các thao tác conditional khó xác định.

Ví dụ sai:

```java
ConcurrentSkipListMap<String, String> map =
        new ConcurrentSkipListMap<>();

map.put("ok", null); // NullPointerException
map.put(null, "x");  // NullPointerException
```

Đây là ví dụ lỗi minh họa, không phải chương trình xử lý exception hoàn chỉnh.

Thay vì lưu null:

- Sử dụng `Optional<T>` trong **value object** nếu cần biểu thị trạng thái thiếu dữ liệu có chủ đích.
- Dùng một record/value type như `Status(MISSING)` hoặc enum sentinel.
- Hoặc xóa mapping khi không còn giá trị hợp lệ, theo contract nghiệp vụ.

**Không nhầm** giữa "method trả null vì không có neighbor/key" và "collection cho phép lưu null". `floorKey` trả null nếu không có ứng viên, nhưng key null không phải một key hợp lệ.

## 12. Comparator quyết định cả thứ tự lẫn tính duy nhất

Với natural order, `String` có thứ tự lexicographic, `Integer` theo trị số. Khi truyền `Comparator` tùy chỉnh, collection dùng **kết quả compare** để điều hướng và quyết định hai keys/elements tương đương.

Một comparator sai có thể gây lỗi logic:

```java
Comparator<String> byLength =
        Comparator.comparingInt(String::length);
```

Nếu chỉ so độ dài, hai string khác nội dung như `"ab"` và `"cd"` có kết quả compare 0, nên trong ordered map chúng được đối xử như một key logic.

Nếu muốn phân biệt những string có cùng độ dài, bổ sung tie-breaker:

```java
Comparator<String> byLengthThenText =
        Comparator.comparingInt(String::length)
                  .thenComparing(Comparator.naturalOrder());
```

Đây là các snippet minh họa (cần import `java.util.Comparator` trong source). Comparator còn phải có tính nhất quán cần thiết: phản xạ cho tương đương, nhất quán dấu, bắc cầu. Nếu comparator dựa trên state mutable thay đổi theo thời gian, cấu trúc có thứ tự có thể không tìm được key như mong đợi.

## 13. Ví dụ Java 17+: comparator làm hai key thành một

Lưu thành `SkipListComparatorDemo.java`:

```java
import java.util.Comparator;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

public class SkipListComparatorDemo {
    public static void main(String[] args) {
        ConcurrentSkipListMap<String, Integer> map =
                new ConcurrentSkipListMap<>(
                        String.CASE_INSENSITIVE_ORDER);

        map.put("Alpha", 1);
        map.put("ALPHA", 2);

        System.out.println("Case-insensitive size: " + map.size());
        System.out.println("Lookup alpha: " + map.get("alpha"));

        Comparator<String> byLengthThenText =
                Comparator.comparingInt(String::length)
                          .thenComparing(Comparator.naturalOrder());

        ConcurrentSkipListSet<String> set =
                new ConcurrentSkipListSet<>(byLengthThenText);

        set.add("bb");
        set.add("c");
        set.add("a");

        System.out.println("Length order: " + set);
    }
}
```

```bash
javac SkipListComparatorDemo.java
java SkipListComparatorDemo
```

Stdout chính xác:

```text
Case-insensitive size: 1
Lookup alpha: 2
Length order: [a, c, bb]
```

Trong map case-insensitive, `"Alpha"` và `"ALPHA"` so sánh bằng 0; `put` lần hai cập nhật value của **một key logic** thay vì tạo hai entry. Với set, tie-breaker giúp `"a"` và `"c"` không bị coi là một phần tử chỉ vì cả hai dài 1.

**Lưu ý contract SortedMap/SortedSet:** ordering nên **consistent with equals** nếu muốn tuân thủ semantics chung của `Map`/`Set` dựa trên `equals`. Comparator không nhất quán có thể vẫn chạy nhưng behavior khi đối chiếu với collection khác gây bất ngờ. Không được giả định `equals` và `compare` luôn cùng định nghĩa "trùng nhau".

## 14. Range view: `subMap`, `headMap` và `tailMap`

Một ưu điểm so với `ConcurrentHashMap` là truy vấn khoảng theo khóa:

```java
map.subMap(fromKey, true, toKey, false);
map.headMap(toKey, false);
map.tailMap(fromKey, true);
```

Đây là snippet minh họa. Các overload nhận `boolean inclusive` cho phép chỉ rõ giới hạn có bao gồm key biên hay không.

Quan trọng: **kết quả là view được backing bởi map gốc**, không phải snapshot sao chép. Điều đó nghĩa là:

- Thay đổi hợp lệ qua view có thể tác động map gốc.
- Thay đổi map gốc nằm trong range sẽ hiện lên khi quan sát view sau đó.
- Thread khác sửa map gốc trong lúc bạn duyệt view thì iterator vẫn có semantics weakly consistent.
- Một key ngoài giới hạn range không thể được thêm qua view như thể nó nằm trong phạm vi hợp lệ; operation có thể ném `IllegalArgumentException`.
- Một view không tự đóng băng tập key của một khoảnh khắc.

Một API nhận range view rồi dùng nó làm **báo cáo snapshot chính xác tại một thời điểm** sẽ sai yêu cầu nếu các writer vẫn hoạt động.

## 15. Ví dụ Java 17+: range view là live/backed

Lưu thành `SkipListRangeViewDemo.java`:

```java
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class SkipListRangeViewDemo {
    public static void main(String[] args) {
        ConcurrentSkipListMap<Integer, String> map =
                new ConcurrentSkipListMap<>();

        for (int i = 1; i <= 6; i++) {
            map.put(i, "v" + i);
        }

        ConcurrentNavigableMap<Integer, String> middle =
                map.subMap(2, true, 5, false);

        System.out.println("Middle before: "
                + middle.navigableKeySet());

        middle.remove(3);
        map.put(4, "changed");

        System.out.println("Main after remove: "
                + map.navigableKeySet());
        System.out.println("Middle sees update: "
                + middle.get(4));

        boolean rejected = false;
        try {
            middle.put(6, "outside");
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        System.out.println("Outside key rejected: " + rejected);
    }
}
```

```bash
javac SkipListRangeViewDemo.java
java SkipListRangeViewDemo
```

Stdout chính xác:

```text
Middle before: [2, 3, 4]
Main after remove: [1, 2, 4, 5, 6]
Middle sees update: changed
Outside key rejected: true
```

`subMap(2, true, 5, false)` gồm 2, 3, 4; loại 5. `middle.remove(3)` xóa trong map gốc. `map.put(4, "changed")` sửa qua map gốc và view đọc được value mới. `middle.put(6,...)` vượt giới hạn view nên bị từ chối.

**Kết luận:** range view là cách giới hạn namespace key, không phải cơ chế snapshot-isolation.

## 16. Range view trên `ConcurrentSkipListSet`

Với set, các API tương ứng là:

```java
ConcurrentSkipListSet<Integer> set =
        new ConcurrentSkipListSet<>();

set.add(10);
set.add(20);
set.add(30);
set.add(40);

NavigableSet<Integer> range =
        set.subSet(20, true, 40, false);
```

Đây là snippet minh họa (cần import `java.util.NavigableSet`). `range` chứa 20 và 30 ở thời điểm đó; thay đổi trên range hoặc set có thể phản ánh ở cả hai.

`headSet(to, inclusive)` giữ phía dưới bound; `tailSet(from, inclusive)` giữ phía trên bound. Các overload không có boolean thường có semantics biên mặc định theo hợp đồng `SortedSet`: `headSet` loại cận trên, `tailSet` gồm cận dưới, `subSet(from,to)` gồm from và loại to.

Với comparator đảo thứ tự, điều kiện giới hạn cũng được diễn giải **theo comparator**. Không lấy natural numeric ordering để suy nghĩa "from" và "to" của một descending view.

## 17. Weakly consistent iterator là gì?

Iterator của các concurrent skip list collections là **weakly consistent**:

- Có thể duyệt khi thread khác đang thêm/xóa mà **không dùng cơ chế fail-fast `ConcurrentModificationException` như iterator của nhiều collection thường**.
- Nó có thể phản ánh **một phần** thay đổi concurrent; không phải snapshot cố định.
- Không bảo đảm mọi phần tử được thêm sau khi iterator được tạo đều xuất hiện.
- Không bảo đảm kết quả quan sát được là một ảnh chụp atomic tại một thời điểm.
- Các thao tác xảy ra đồng thời có thể khiến nội dung iteration khác nhau giữa các lần chạy.
- Iteration vẫn đi theo quy tắc thứ tự sắp xếp của collection trong phạm vi hành vi iterator, nhưng không suy rằng kết quả có cùng tập phần tử với `keySet()` ở một thời điểm duy nhất.

**Hai điều cần tránh:**

1. Dùng iterator rồi giả định có một "bản sao của map đúng lúc bắt đầu".
2. Bắt `ConcurrentModificationException` để xây vòng retry và tưởng đó là cách dùng đúng của loại collection này.

Nếu cần snapshot ổn định để xử lý số liệu tài chính hoặc quyết định transaction, hãy thiết kế một cơ chế snapshot/version riêng hoặc **quiesce writers**; chỉ dùng collection concurrent không đủ.

## 18. Ví dụ Java 17+: iterator hoạt động khi writer cập nhật

Lưu thành `WeakIteratorDemo.java`:

```java
import java.util.Iterator;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class WeakIteratorDemo {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentSkipListMap<Integer, String> map =
                new ConcurrentSkipListMap<>();

        map.put(1, "one");
        map.put(2, "two");
        map.put(3, "three");

        Iterator<Integer> iterator =
                map.navigableKeySet().iterator();

        CountDownLatch begin = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                begin.await();
                while (iterator.hasNext()) {
                    iterator.next();
                }
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "reader");

        Thread writer = new Thread(() -> {
            try {
                begin.await();
                map.put(4, "four");
                map.remove(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "writer");

        reader.start();
        writer.start();
        begin.countDown();
        reader.join();
        writer.join();

        System.out.println("Reader finished without error: "
                + (failure.get() == null));
        System.out.println("Final keys: "
                + map.navigableKeySet());
    }
}
```

```bash
javac WeakIteratorDemo.java
java WeakIteratorDemo
```

Stdout khi không có interruption ngoài chương trình:

```text
Reader finished without error: true
Final keys: [1, 3, 4]
```

**Không in và không hứa reader đã nhìn thấy key nào.** Nó có thể đã thấy 2 trước khi xóa, hoặc đã thấy 4 sau khi thêm, tùy scheduler. Đó chính là weak consistency. Sau khi hai thread `join`, map không còn writer, nên `Final keys` là trạng thái ổn định và có thể dự đoán.

Trong ví dụ này, `failure` được ghi từ reader qua `AtomicReference`; main đọc sau join. Việc catch `Throwable` là lựa chọn có giới hạn **để báo QA của demo**, không phải khuyến nghị nuốt mọi exception/Error trong ứng dụng production.

## 19. `size()` không nhất thiết là O(1) hoặc chính xác tức thời

Với `ConcurrentSkipListMap` và `ConcurrentSkipListSet`, `size()` không có cùng đặc tính như `ArrayList.size()` hay một bộ đếm atomic lưu sẵn.

Javadoc nhấn mạnh:

- Việc xác định size có thể yêu cầu **duyệt qua các phần tử**.
- Khi có nhiều thread concurrent update, size trả về có thể **không phản ánh số phần tử ở một thời điểm duy nhất theo kỳ vọng của caller**.
- Kết quả chỉ nên xem là **ước lượng quan sát** trong lúc collection thay đổi.
- Gọi `size()` nhiều lần trong hot path có thể tốn chi phí cao trên map/set lớn.
- Đừng dựa vào `if (map.size() < limit) map.put(...)` để tạo hard capacity.

Nếu cần giới hạn capacity, dùng bounded queue, semaphore, admission control hay một protocol atomic riêng. Concurrent sorted map **không có max-capacity enforcement tích hợp**.

**Quan trọng:** ngay cả khi đọc size cho ra 100, thread khác có thể thêm/xóa trước khi bạn hành động dựa trên con số đó. Đó là vấn đề time-of-check-to-time-of-use, không chỉ hiệu năng.

## 20. Atomic update theo khóa: `putIfAbsent`, `replace`, `remove(key,value)`

Một pattern sai:

```java
if (!map.containsKey(key)) {
    map.put(key, value);
}
```

Đây là snippet minh họa. Với nhiều threads, hai thread cùng thấy absent rồi đều `put`; một value ghi đè value khác.

Dùng API có điều kiện:

```java
V previous = map.putIfAbsent(key, newValue);
```

`previous == null` biểu thị đã có thể gắn mapping mới khi absent theo contract của map (vì value null không được lưu). Tương tự:

```java
boolean changed = map.replace(key, expectedOldValue, newValue);
boolean removed = map.remove(key, expectedValue);
```

Các snippet là ký pháp tổng quát, phải thay kiểu `V`/`key` và khai báo ở code thật.

Những operation này hữu ích để làm **compare-and-update theo một key**. Nhưng `putIfAbsent` không đủ cho mục tiêu "key phải chưa tồn tại trong toàn bộ khoảng thời gian task thực thi"; một thread khác có thể xóa mapping ngay sau khi thêm.

## 21. Ví dụ Java 17+: conditional updates không mất cập nhật

Lưu thành `ConditionalSkipListDemo.java`:

```java
import java.util.concurrent.ConcurrentSkipListMap;

public class ConditionalSkipListDemo {
    public static void main(String[] args) {
        ConcurrentSkipListMap<String, Integer> map =
                new ConcurrentSkipListMap<>();

        Integer first = map.putIfAbsent("job", 10);
        Integer second = map.putIfAbsent("job", 20);

        boolean replacedWrong = map.replace("job", 7, 30);
        boolean replacedRight = map.replace("job", 10, 30);

        boolean removedWrong = map.remove("job", 20);
        boolean removedRight = map.remove("job", 30);

        System.out.println("First insertion was absent: "
                + (first == null));
        System.out.println("Second saw old value: " + second);
        System.out.println("Wrong replace: " + replacedWrong);
        System.out.println("Right replace: " + replacedRight);
        System.out.println("Wrong remove: " + removedWrong);
        System.out.println("Right remove: " + removedRight);
        System.out.println("Map empty: " + map.isEmpty());
    }
}
```

```bash
javac ConditionalSkipListDemo.java
java ConditionalSkipListDemo
```

Stdout chính xác:

```text
First insertion was absent: true
Second saw old value: 10
Wrong replace: false
Right replace: true
Wrong remove: false
Right remove: true
Map empty: true
```

Ví dụ không có cạnh tranh thực tế nhưng cho thấy **hợp đồng conditional update** thay cho cặp thao tác "đọc rồi ghi". Trong ứng dụng concurrent, cần xử lý nhánh `false` hoặc value hiện tại do thread khác thay đổi, chứ không được tự khẳng định operation chắc thành công.

## 22. `compute`, `merge` và function có thể được gọi lại

Các API như:

```java
map.compute(key, (k, oldValue) -> nextValue(oldValue));

map.merge(key, 1, Integer::sum);

map.computeIfAbsent(key, k -> buildValue(k));
```

giúp thể hiện update theo key thay vì tách `get`/`put`. Tuy nhiên, cần phân biệt với một transaction có callback **chỉ chạy đúng một lần**.

Đối với `ConcurrentSkipListMap`, logic update concurrent có thể **thử lại** do thread khác thay đổi mapping. Javadoc các remapping operation cảnh báo function **không nhất thiết được áp dụng một lần atomically như người dùng kỳ vọng**; vì vậy function nên:

- **Không có side effect không thể lặp lại** (gửi email, trừ tiền, gọi API remote).
- Không phụ thuộc việc mỗi lần gọi callback tương ứng một commit thành công.
- Chỉ tính từ input sang output, đủ nhanh, không block lâu.
- Không tự thay đổi cùng map/key một cách gây dependency hoặc recursion khó kiểm soát.
- Chú ý `null`: với những remapping method cho phép return null, null có thể mang nghĩa **remove/no mapping**, không phải value hợp lệ.

Ví dụ nếu muốn tăng bộ đếm một mapping:

```java
map.merge("counter", 1, Integer::sum);
```

Phép cộng số nguyên **không có side effect** nên phù hợp khi implementation phải retry. Nhưng `Integer` có thể overflow nếu counter lớn; cân nhắc `Long` hoặc policy giới hạn.

## 23. Ví dụ Java 17+: nhiều thread dùng `merge`

Lưu thành `ConcurrentSkipListMergeDemo.java`:

```java
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CountDownLatch;

public class ConcurrentSkipListMergeDemo {
    public static void main(String[] args)
            throws InterruptedException {
        ConcurrentSkipListMap<String, Integer> map =
                new ConcurrentSkipListMap<>();

        CountDownLatch start = new CountDownLatch(1);
        Thread[] workers = new Thread[4];

        for (int t = 0; t < workers.length; t++) {
            workers[t] = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                for (int i = 0; i < 500; i++) {
                    map.merge("counter", 1, Integer::sum);
                }
            });
            workers[t].start();
        }

        start.countDown();

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("Counter: " + map.get("counter"));
        System.out.println("Keys: " + map.navigableKeySet());
    }
}
```

```bash
javac ConcurrentSkipListMergeDemo.java
java ConcurrentSkipListMergeDemo
```

Stdout trên đường chạy bình thường, không có interruption ngoài chương trình:

```text
Counter: 2000
Keys: [counter]
```

Bốn thread cùng bắt đầu từ latch, mỗi thread thực hiện 500 lần `merge`: tổng logic 2.000. Một biểu thức `map.put("counter", map.get("counter")+1)` không có atomicity tương đương và có thể làm mất increment khi concurrent.

Không kết luận thứ tự 2.000 lần callback hoặc worker nào thắng CAS trước. Chỉ **kết quả cuối của mapping** được chứng minh. `Thread.join()` giúp main đợi toàn bộ worker trước khi đọc kết quả.

## 24. Value mutable không tự trở nên thread-safe

Map được thiết kế an toàn cho concurrent operations lên **mapping**, nhưng object value có thể là kiểu mutable thông thường:

```java
ConcurrentSkipListMap<String, ArrayList<String>> map =
        new ConcurrentSkipListMap<>();
```

Đây là snippet minh họa, cần import `ArrayList`. Nếu nhiều thread lấy cùng một `ArrayList` rồi gọi `add`, collection value đó **không tự đồng bộ** chỉ vì nó được đặt trong concurrent map.

Các lựa chọn:

1. Dùng value **immutable**, cập nhật bằng `compute`/`replace` theo key.
2. Dùng value có thiết kế thread-safe riêng nếu nhiều thread cần chỉnh tại chỗ.
3. Dùng per-key lock hoặc transaction-level protocol khi cần sửa nhiều field nhất quán.
4. Không rò rỉ value mutable ra caller có thể sửa tự do.
5. Nếu value là record immutable, vẫn cần chú ý field reference tới object mutable bên trong record.

**Safe publication của reference** và **thread safety của mutation sau publication** là hai vấn đề khác nhau. Một put/get concurrent có memory consistency phù hợp nhưng không biến internal state của value thành atomic object.

## 25. "Snapshot" khác "weakly consistent view"

Một lệnh:

```java
var snapshot = new java.util.TreeMap<>(map);
```

tạo một object `TreeMap` mới, nhưng **nếu map nguồn đang bị sửa trong lúc sao chép**, tập mapping được đọc có thể đến từ nhiều thời điểm khác nhau. Đây không tự động là snapshot atomic của map concurrent.

Để có **snapshot đúng tại một điểm logical**, cần thêm giao thức:

- Tạm dừng/điều phối writers bằng lock bên ngoài mà **mọi writer** đều tuân thủ.
- Hoặc sử dụng versioning/MVCC, immutable snapshot swap hay một kiến trúc lưu trữ có snapshot semantics.
- Hoặc chấp nhận **eventual/approximate snapshot** nếu bài toán chỉ cần thống kê hoặc hiển thị sơ bộ.

Sao chép sau khi **toàn bộ writers đã hoàn tất và join** thì tập kết quả ổn định; trong trường hợp đó có thể dùng `new TreeMap<>(map)` để làm bản sao không còn bị ảnh hưởng bởi các thay đổi sau này của map nguồn (trừ việc values mutable vẫn được tham chiếu).

Đừng gọi việc copy là "linearizable snapshot" trong lúc writer active mà không có giao thức bổ sung.

## 26. `descendingMap` và các view đảo thứ tự

`descendingMap()` tạo một view theo thứ tự ngược của map. `descendingSet()` làm điều tương tự với set. Đây là **backed view**, không phải copy.

Ví dụ:

```java
var reverse = map.descendingMap();
```

Nếu original có keys `[1,3,5]`, reverse duyệt `[5,3,1]`. Nhưng nếu thread khác thêm `4` vào map gốc, reverse view cũng phản ánh trạng thái live theo thời điểm quan sát.

Với workload chỉ cần descending một lần, cân nhắc cost của iterator. Tài liệu Java cho biết ascending-ordered views/iterators có thể hiệu quả hơn descending variants trên những implementation này; không nên mặc định hai chiều có cùng đặc tính performance.

Mỗi update qua reverse view vẫn ảnh hưởng cùng backing map. Không được dùng reverse view để tạo một independent index với lifecycle riêng.

## 27. So sánh với các collection khác

| Collection | Concurrent access | Có thứ tự sorted/navigable? | Đặc điểm nổi bật |
| --- | --- | --- | --- |
| `HashMap` | Không an toàn cho concurrent mutation không đồng bộ | Không | Hash table thông thường |
| `TreeMap` | Không tự thread-safe | Có | Balanced tree, single-thread hoặc external lock |
| `ConcurrentHashMap` | Có | Không | Lookup/update theo key, throughput cao cho unordered keys |
| `ConcurrentSkipListMap` | Có | **Có** | Sorted concurrent map, neighbor/range navigation |
| `HashSet` | Không tự an toàn cho concurrent mutation | Không | Tập thông thường |
| `TreeSet` | Không tự thread-safe | Có | Sorted set, external synchronization khi chia sẻ mutable |
| `ConcurrentSkipListSet` | Có | **Có** | Sorted concurrent set, NavigableSet |
| `CopyOnWriteArraySet` | Có | Không duy trì sorted ordering | Thích hợp nhiều read, rất ít write; copy khi thay đổi |

Chọn theo **nhu cầu query** và **đặc tính workload**. Nếu chỉ cần get/put ngẫu nhiên theo key mà không cần thứ tự, `ConcurrentHashMap` thường đơn giản và có throughput/overhead hợp lý hơn. Nếu cần `floorEntry` hoặc range-based queries concurrent, `ConcurrentSkipListMap` có lợi thế API rõ ràng.

Không chọn skip list vì tên "concurrent" rồi bỏ qua thực tế rằng metadata/order maintenance cũng tốn memory và CPU hơn một hash table thông thường.

## 28. Không dùng CSLM như một hàng đợi blocking có deadline

Một map có khóa timestamp dễ khiến người viết:

```java
while (true) {
    var first = map.firstEntry();
    if (first != null && first.getKey() <= now()) {
        map.remove(first.getKey());
        runTask(first.getValue());
    }
}
```

Đây là anti-pattern minh họa, `now`/`runTask` chưa định nghĩa.

Các vấn đề:

- Vòng lặp busy-spin khi không có công việc.
- `firstEntry` rồi `remove(key)` không atomic claim. Thread khác có thể đã lấy/xóa/đổi mapping; vẫn có nguy cơ xử lý duplicate hoặc lấy value đã cũ.
- Nếu deadline ở tương lai, không có operation chờ tới đúng deadline.
- Nếu task thất bại, không có lifecycle retry/requeue rõ.
- `now()` nên dùng time source phù hợp với semantics lịch; clock adjustments có thể ảnh hưởng deadline.
- Nếu cần chính xác thứ tự chạy khi nhiều worker, chỉ có sorted order không đủ để bảo đảm global execution ordering.

`DelayQueue` hoặc `ScheduledExecutorService` thường phù hợp hơn cho scheduled delayed work; `PriorityBlockingQueue` dùng ưu tiên và blocking take nhưng **không tự chờ tới thời điểm deadline**. Nếu bắt buộc dùng map như index, phải thiết kế signaling và claim atomic bằng `pollFirstEntry`/conditional methods theo giao thức.

## 29. Độ phức tạp và lựa chọn hiệu năng

Bức tranh khái quát:

| Operation | Đặc tính thường gặp |
| --- | --- |
| `get/put/remove` theo key | Expected O(log n) |
| `floor/ceiling/lower/higher` | Expected O(log n) |
| Duyệt toàn bộ n phần tử | O(n) cho một traversal không đổi |
| `size()` | Có thể cần duyệt và tốn O(n), kém ổn định dưới concurrent update |
| Query range có k kết quả | Chi phí định vị + traversal k phần tử, nhưng concurrent changes làm kết quả quan sát không phải snapshot |
| `containsValue` của map | Có thể cần scan, không nên dùng như lookup index thứ hai |

Trên workload thực, chi phí còn phụ thuộc:

- Nhiều writer cùng đụng một key hay trải đều khắp key space.
- Comparator có tốn CPU hay không.
- Khóa dài, mutable hoặc có hash/compare không ổn định.
- Tỷ lệ range navigation so với random get/put.
- Có nhiều descending traversals hay không.
- Kích thước map, memory footprint của skip-list indexes và cache locality.
- Thread count, contention, allocation và garbage collector.
- Số thao tác composite đòi hỏi external synchronization.

**Độ phức tạp kỳ vọng** không phải chứng nhận mọi operation có upper bound thời gian cố định. Benchmarks nên được chạy với dữ liệu và contention tương ứng môi trường production; không dùng microbenchmark 10 keys để suy kết luận cho hàng triệu phần tử.

## 30. Những lỗi thường gặp

**1. "ConcurrentSkipListMap giống ConcurrentHashMap nhưng tự in sorted."** Không. Nó có NavigableMap/range semantics và cấu trúc khác; chọn theo truy vấn và chi phí.

**2. "Comparator chỉ ảnh hưởng display."** Sai. `compare(a,b)==0` có thể khiến hai key khác `equals` được coi cùng một logical key.

**3. "Có thể lưu null như ở HashMap."** Sai. CSLM cấm null key/value; CSLS cấm null element.

**4. "Weakly consistent iterator là snapshot tại lúc gọi iterator()."** Sai. Nó có thể phản ánh thay đổi concurrent.

**5. "Iterator sẽ luôn nhìn thấy mọi update xảy ra sau khi bắt đầu."** Không. Không có guarantee như vậy.

**6. "size() là đếm O(1) và hoàn toàn chính xác khi nhiều writer."** Sai. Có thể cần traversal và không là atomic snapshot.

**7. "if (get==null) put là atomic."** Sai. Dùng `putIfAbsent` hoặc remapping operation theo contract.

**8. "compute function luôn chạy đúng một lần."** Không nên giả định; remapping có thể retry, nên tránh side effect.

**9. "Map concurrent làm mọi ArrayList value thread-safe."** Sai. Value mutable phải có synchronization riêng.

**10. "subMap tạo bản sao độc lập."** Sai. Nó là view backed bởi map gốc.

**11. "Có thể put key ngoài range qua subMap."** Không. Out-of-range insertion bị từ chối.

**12. "firstEntry rồi remove là giống pollFirstEntry."** Sai. Cặp thao tác bị thread khác xen vào.

**13. "Set có thứ tự lưu được nhiều phần tử comparator-equal."** Sai. Comparator bằng 0 định nghĩa membership như một phần tử.

**14. "Concurrent collections tự bảo đảm transaction nhiều key."** Sai. Một chuỗi nhiều thao tác vẫn có thể có interleaving.

**15. "ConcurrentSkipListMap là hàng đợi blocking tự thức khi thêm item."** Không. Nó không có take/await như BlockingQueue.

**16. "Đọc lastKey từ map rỗng trả null."** Sai. `lastKey()` ném `NoSuchElementException`; `lastEntry()` trả null khi rỗng.

**17. "descendingMap là bản sao, không bị update ngoài ảnh hưởng."** Sai. Đó là live view.

**18. "Một map sorted concurrent tự đảm bảo thứ tự xử lý nghiệp vụ giữa threads."** Không. Nó bảo đảm thứ tự điều hướng khóa, không lập lịch thực thi tasks.

**19. "Chỉ cần volatile reference tới TreeMap để update an toàn."** Sai. Visibility của reference không bảo vệ nội bộ các phép put/remove.

**20. "copy map bằng new TreeMap là snapshot atomic dù writer đang sửa."** Sai. Cần snapshot protocol riêng nếu đòi hỏi tính nhất quán tại một thời điểm.

## 31. Checklist khi dùng trong ứng dụng thực

1. **Có thực sự cần sorted/navigable queries không?** Nếu không, thử `ConcurrentHashMap`.
2. **Comparator có đúng và immutable không?** Nhất quán với equals khi cần contract Map/Set phổ thông.
3. **Key có immutable không?** Không thay đổi trường tham gia comparator sau khi thêm.
4. **Có null được đưa vào không?** Map/set này từ chối null; chuyển trạng thái missing thành value type rõ.
5. **Các thao tác multi-key có invariant không?** Cần coordination/transaction riêng.
6. **Có lặp entry trong lúc writer hoạt động không?** Weakly consistent chứ không snapshot.
7. **Có cần report chính xác size không?** Đừng dùng `size` để chứng minh capacity/transaction.
8. **Có dùng subMap/subSet không?** Nhớ view backed và inclusive flags.
9. **Có lấy firstEntry rồi xóa tách rời không?** Nếu cần claim đầu nhỏ nhất, cân nhắc `pollFirstEntry`.
10. **Remapping callback có side effect không?** Nếu có, chuyển side effect ra khỏi retryable function.
11. **Value có mutable không?** Chọn immutable replacement hoặc đồng bộ nội bộ.
12. **Có cần blocking/deadline semantics không?** Skip list map/set không thay BlockingQueue.
13. **Workload có nhiều descending iterations không?** Benchmark thực tế; ascending thường có lợi.
14. **Có bảo vệ dữ liệu sau khi copy không?** Shallow copy không deep-copy mutable values.
15. **Có kiểm tra trường hợp empty không?** `firstKey/lastKey` có thể throw, `firstEntry/lastEntry` có thể trả null.
16. **Có monitor/lock ngoài cần coordination không?** Mọi writer phải tuân cùng lock nếu muốn atomic snapshot ngoài collection.
17. **Có cần cleanup/shutdown không?** Collection không sở hữu worker threads; quản lý executor theo vòng đời riêng.
18. **Đã test với contention và ordering thực tế chưa?** Dùng latches/joins, kiểm tra invariant thay vì đoán lịch chạy.

## 32. Câu hỏi tự kiểm tra

**Câu 1.** `ConcurrentSkipListMap` khác `ConcurrentHashMap` ở đâu?

CSLM duy trì **thứ tự sorted theo key** và có NavigableMap/range operations. CHM phục vụ concurrent map theo hash, không duy trì sorted key order.

**Câu 2.** `ConcurrentSkipListSet` có cho phép thêm hai String khác nội dung nhưng `Comparator.compare` trả 0 không?

Không. Đối với membership của ordered set, chúng là một phần tử logic theo comparator.

**Câu 3.** Với keys `[10,20,30]`, `floorKey(25)` và `higherKey(20)` là gì?

Lần lượt 20 và 30.

**Câu 4.** `subMap(2,true,5,false)` chứa key nào trong map `[1,2,3,4,5,6]`?

Các key 2, 3, 4. Nó là **view live**, không phải snapshot.

**Câu 5.** Tại sao iterator không ném fail-fast exception khi writer cập nhật không chứng minh nó là snapshot?

Vì nó **weakly consistent**: traversal có thể phản ánh một số cập nhật nhưng không cam kết ảnh chụp atomic.

**Câu 6.** Vì sao `size()` không thích hợp để enforce max-capacity dưới contention?

Nó có thể traversal, trả giá trị không ổn định khi writer hoạt động, và check-then-put vẫn có race.

**Câu 7.** Map cho `null` làm value không?

Không. Null value bị cấm, giúp `get()==null` biểu thị absent.

**Câu 8.** `map.putIfAbsent` tốt hơn `if(!map.containsKey(k)) map.put(k,v)` như thế nào?

Một operation conditional theo key tránh race giữa separate check và put.

**Câu 9.** Nếu value là `ArrayList`, map concurrent có bảo vệ các lời gọi `add` đồng thời không?

Không. Thread safety của mutable value là trách nhiệm riêng.

**Câu 10.** `map.firstEntry()` rồi `map.remove(key)` có tương đương `pollFirstEntry` dưới concurrency không?

Không. Thread khác có thể xen vào giữa hai lời gọi. `pollFirstEntry` tích hợp lấy và xóa entry theo operation.

**Câu 11.** `merge(key, 1, Integer::sum)` có thể dùng để tăng counter của một key từ nhiều thread không?

Có, phù hợp với pure remapping function và update semantics, nhưng phải xét overflow và contention.

**Câu 12.** Có thể dùng CSLM như một `DelayQueue` chỉ bằng việc chọn timestamp làm key không?

Không. CSLM không cung cấp blocking wait theo deadline hoặc lifecycle của task. Cần scheduler/queue và protocol thích hợp.

## 33. Tổng kết

- **`ConcurrentSkipListMap`** là sorted concurrent map theo key; **`ConcurrentSkipListSet`** là sorted concurrent set theo element. Cả hai cung cấp navigation/range API, không chấp nhận null và dùng comparator hoặc natural ordering.
- **Skip list** hỗ trợ tìm/thêm/xóa với expected logarithmic cost; không phải một ordered hash map có thứ tự ngẫu nhiên.
- **`lower/floor/ceiling/higher`**, `pollFirst/pollLast`, `descendingMap/descendingSet` và `subMap/subSet` là những API cốt lõi khi cần ordered queries.
- **Range views là live/backed**, không phải snapshot. Iterator **weakly consistent**, có thể duyệt cùng concurrent updates nhưng không cho một ảnh chụp atomic.
- **`size()` có thể tốn O(n) và không ổn định khi writer active**; không dùng để thực thi hard capacity.
- **Conditional update/remapping** giúp thao tác theo một key không bị check-then-act race; callback nên thuần vì có thể retry.
- **Comparator quyết định equivalence**; key mutable, comparator thiếu tie-breaker hoặc không nhất quán với equals dễ gây lỗi tìm kiếm/membership.
- **Concurrent collection không tự bảo vệ mutable values hay transaction nhiều key**. Cần data ownership/locking/immutable state phù hợp.
- Nếu chỉ cần unordered concurrent key/value, `ConcurrentHashMap` có thể hợp hơn; nếu cần blocking/delayed execution, dùng queue/scheduler có semantics phù hợp.

Câu hỏi chọn công cụ nên là: **"Tôi cần truy vấn theo thứ tự khóa trong lúc dữ liệu được cập nhật đồng thời, hay chỉ cần lookup theo key? Tôi cần một view weakly consistent, hay cần snapshot/transaction nhất quán?"**. Trả lời rõ hai câu hỏi ấy sẽ quyết định nên dùng `ConcurrentSkipListMap`/`ConcurrentSkipListSet`, hay cấu trúc concurrency khác của Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-010">Collections.synchronizedMap/List và yêu cầu khóa khi duyệt</a>

<details>
<summary>Xem chi tiết</summary>

# Collections.synchronizedMap/List và yêu cầu khóa khi duyệt

## 1. Bài toán: collection thông thường không tự an toàn khi nhiều thread truy cập

`ArrayList` và `HashMap` được thiết kế chủ yếu cho truy cập thông thường. Nếu một thread sửa chúng trong khi thread khác đọc hoặc sửa mà không có cơ chế đồng bộ phù hợp, chương trình có thể gặp race condition, kết quả không nhất quán hoặc hành vi ngoài mong đợi. Không nên suy luận rằng chỉ vì một lời gọi `get()` hoặc `add()` nhìn đơn giản thì việc dùng cùng một collection từ nhiều thread là an toàn.

Java cung cấp các wrapper đồng bộ trong `java.util.Collections`:

```java
Map<String, Integer> map =
        Collections.synchronizedMap(new HashMap<>());

List<String> list =
        Collections.synchronizedList(new ArrayList<>());
```

Các phương thức trên tạo **wrapper** bao quanh collection gốc. Wrapper dùng một monitor chung để đồng bộ các thao tác do nó cung cấp, như `get`, `put`, `add`, `remove` hoặc `size`. Đây là lựa chọn hữu ích khi muốn sử dụng giao diện `Map`/`List` quen thuộc với một cơ chế khóa đơn giản.

Tuy nhiên, **mỗi lời gọi được đồng bộ không đồng nghĩa một chuỗi nhiều lời gọi là nguyên tử**. Và một điểm rất quan trọng: **khi duyệt bằng `Iterator`, enhanced `for`, `Spliterator` hoặc `Stream`, người dùng thường phải chủ động khóa wrapper trong toàn bộ thời gian duyệt**.

Bài học này tập trung vào Java Concurrency nói chung, không liên quan đến Spring.

## 2. Wrapper đồng bộ hoạt động như thế nào?

Ví dụ:

```java
List<String> raw = new ArrayList<>();
List<String> safe = Collections.synchronizedList(raw);
```

Ý tưởng là mọi truy cập chia sẻ tới dữ liệu phải đi qua `safe`. Phần triển khai của wrapper sử dụng khóa nội bộ nhất quán để bảo vệ các lời gọi của nó. Với wrapper trả về trực tiếp từ `Collections.synchronizedList(...)` hoặc `synchronizedMap(...)`, **khóa bên ngoài cần sử dụng chính đối tượng wrapper**:

```java
synchronized (safe) {
    // Nhiều thao tác liên quan được giữ dưới cùng monitor.
    safe.add("a");
    safe.add("b");
}
```

Đồng bộ kiểu này cung cấp các quan hệ *happens-before* khi thread nhả monitor và thread khác lấy cùng monitor. Nhờ đó, dữ liệu mà wrapper bảo vệ có thể được nhìn thấy đúng theo những quy tắc đồng bộ Java. Tuy nhiên:

- Wrapper **không tự bảo vệ** các truy cập đi thẳng qua `raw` nếu code khác vẫn giữ tham chiếu đến collection gốc.
- Wrapper không sửa được logic nhiều bước bị race nếu không khóa trọn chuỗi.
- Nó không làm một phần tử mutable bên trong collection trở nên thread-safe. Chẳng hạn, một `Person` có trường thay đổi vẫn cần cơ chế đồng bộ riêng cho trạng thái của `Person`.
- Nó không biến collection thành snapshot bất biến hay cho phép iterator duyệt an toàn trong lúc các thread khác sửa mà không có khóa.
- Tất cả thread muốn được bảo vệ bởi cùng hợp đồng phải thống nhất sử dụng đúng wrapper/monitor.

Để tránh đường truy cập không đồng bộ, nên tạo collection gốc và wrapper cùng lúc, rồi **chỉ công bố wrapper** cho các thread khác. Công bố tham chiếu an toàn cũng là một phần của thiết kế chia sẻ dữ liệu: dùng các cơ chế như khởi tạo trước khi start thread, trường `final` được xây dựng đúng hoặc giao nhận qua cấu trúc concurrent thích hợp.

## 3. Điều gì được bảo vệ bởi synchronizedMap và synchronizedList?

### Một lời gọi phương thức

```java
Map<String, Integer> counts =
        Collections.synchronizedMap(new HashMap<>());
counts.put("ok", 1);        // Một lời gọi được đồng bộ.
Integer value = counts.get("ok"); // Một lời gọi được đồng bộ.
```

Từng lời gọi ở trên được wrapper bảo vệ bằng monitor của nó. Tương tự, với `synchronizedList`, các thao tác `add`, `get`, `set`, `remove`, `size` thông thường được đồng bộ ở cấp phương thức.

Từ Java 8, các wrapper trong JDK cũng triển khai đồng bộ cho nhiều phương thức mặc định phù hợp, chẳng hạn `Map.computeIfAbsent`, `Map.merge`, `Map.forEach` hoặc `List.replaceAll`. Khi dùng một phương thức đơn lẻ, nên đọc hợp đồng chính xác của phương thức đó, thay vì tự gộp nó với nhiều lời gọi tách rời.

### Nhiều lời gọi liên tiếp

Ví dụ logic không an toàn về tính nguyên tử:

```java
if (!counts.containsKey("hits")) {
    counts.put("hits", 0);
}
counts.put("hits", counts.get("hits") + 1);
```

Đây là fragment minh họa. Mỗi bước `containsKey`, `get` và `put` có khóa riêng, nhưng **các thread khác có thể xen vào giữa chúng**. Đặc biệt, hai thread đều đọc cùng giá trị cũ và cùng ghi giá trị mới, gây mất lượt tăng.

Khi cần biến nhiều thao tác thành một đơn vị logic, đồng bộ **bao trọn chuỗi**:

```java
synchronized (counts) {
    counts.put("hits", counts.getOrDefault("hits", 0) + 1);
}
```

Lúc này các thread khác dùng cùng wrapper/monitor không thể chen vào giữa `getOrDefault` và `put`. Cần giữ phần nằm trong khóa đủ ngắn để tránh tăng thời gian chờ.

Một lựa chọn khác, khi bài toán thực sự là cập nhật theo khóa và cần đồng thời cao, là `ConcurrentHashMap` với `compute`, `merge` hoặc `AtomicInteger` đúng cách. Không phải lúc nào cần giữ `synchronizedMap`.

## 4. Quy tắc quan trọng nhất: phải khóa toàn bộ vòng duyệt

JDK quy định rõ: khi duyệt collection đồng bộ qua `Iterator`, `Spliterator` hoặc `Stream`, người dùng phải thực hiện thao tác duyệt trong khối `synchronized` trên wrapper phù hợp. Bởi vì việc tạo iterator hoặc lấy stream không làm cho **cả chuỗi bước duyệt** được khóa nguyên tử.

### 4.1. Duyệt synchronizedList

**Sai:**

```java
List<String> items =
        Collections.synchronizedList(new ArrayList<>());
items.add("A");
items.add("B");

for (String item : items) { // Không có khóa bao trọn traversal.
    System.out.println(item);
}
```

Vòng `for-each` thực chất gọi `iterator()` và nhiều bước `hasNext()`/`next()`. Một thread khác có thể sửa list giữa các bước. Việc mỗi lời gọi `items.add(...)` được đồng bộ **không bảo đảm** traversal không bị race.

**Đúng:**

```java
synchronized (items) {
    for (String item : items) {
        System.out.println(item);
    }
}
```

Trong thời gian vòng lặp chạy, các thread sửa list thông qua chính wrapper `items` phải chờ monitor được nhả. Đây là cách tạo một vùng quan sát nhất quán theo hợp đồng sử dụng chung monitor.

### 4.2. Duyệt synchronizedMap

**Sai:**

```java
Map<String, Integer> scores =
        Collections.synchronizedMap(new HashMap<>());
scores.put("A", 10);
scores.put("B", 20);

for (Map.Entry<String, Integer> entry : scores.entrySet()) {
    System.out.println(entry.getKey() + "=" + entry.getValue());
}
```

**Đúng:**

```java
synchronized (scores) {
    for (Map.Entry<String, Integer> entry : scores.entrySet()) {
        System.out.println(entry.getKey() + "=" + entry.getValue());
    }
}
```

Với các view của map như `keySet()`, `entrySet()` và `values()`, khóa cần lấy là **map wrapper gốc**, tức `scores` trong ví dụ.

Không dùng `synchronized(scores.entrySet())` để thay thế. View được trả về có thể chia sẻ mutex nội bộ với map gốc, nhưng monitor của chính đối tượng view không nhất thiết là monitor đang bảo vệ những lời gọi trên wrapper. Hợp đồng API yêu cầu khóa `scores`.

### 4.3. Với Iterator, Spliterator và Stream

Áp dụng cùng nguyên tắc:

```java
synchronized (items) {
    Iterator<String> it = items.iterator();
    while (it.hasNext()) {
        String value = it.next();
        // Xử lý ngắn, không tạo side effect phức tạp.
        System.out.println(value);
    }
}
```

Và với stream:

```java
long count;
synchronized (items) {
    count = items.stream()
            .filter(s -> !s.isBlank())
            .count();
}
```

Chú ý: cần giữ khóa **đến khi terminal operation hoàn tất**, chứ không chỉ khóa khi tạo `stream()`. Với `parallelStream()`, việc giữ monitor ở thread gọi chưa tự bảo đảm an toàn cho callback chạy ở các worker khác nếu chúng truy cập trạng thái ngoài. Cân nhắc snapshot rồi mới xử lý song song, thay vì cố giữ khóa dài qua một pipeline phức tạp.

### 4.4. Vì sao fail-fast iterator không thay thế khóa?

Iterator của `ArrayList` và `HashMap` thông thường có cơ chế phát hiện sửa đổi cấu trúc để ném `ConcurrentModificationException` trong một số tình huống. Đây là **phát hiện lỗi theo best effort**, không phải cơ chế đồng bộ và không bảo đảm luôn ném exception nếu có race.

Điều sai lầm là viết:

```java
try {
    for (String item : items) {
        use(item);
    }
} catch (ConcurrentModificationException ignored) {
    // Không biến thao tác duyệt trở nên an toàn.
}
```

Đây là fragment, `use(...)` do ứng dụng định nghĩa. Việc bắt exception chỉ che giấu vấn đề. Giải pháp là khóa đúng hoặc đổi thiết kế collection/traversal. Ngay cả khi không thấy `ConcurrentModificationException`, kết quả duyệt không khóa vẫn có thể thiếu, lặp hoặc không nhất quán khi có sửa đổi đồng thời.

## 5. Chọn monitor nào khi duyệt view và subList?

Một lỗi tinh vi là khóa nhầm đối tượng được trả về từ method tạo view.

| Collection dùng để duyệt | Monitor cần khóa |
| --- | --- |
| `Collections.synchronizedList(list)` trực tiếp | Chính đối tượng synchronized-list wrapper |
| `list.subList(from, to)` trả từ synchronized-list | **List wrapper gốc** đã tạo sublist |
| `Collections.synchronizedMap(map)` trực tiếp | Chính synchronized-map wrapper |
| `map.keySet()` | **Map wrapper gốc** |
| `map.entrySet()` | **Map wrapper gốc** |
| `map.values()` | **Map wrapper gốc** |

Ví dụ:

```java
List<Integer> numbers = Collections.synchronizedList(
        new ArrayList<>(List.of(1, 2, 3, 4)));

List<Integer> tail = numbers.subList(2, 4);

synchronized (numbers) { // Khóa wrapper gốc, không phải tail.
    for (Integer n : tail) {
        System.out.println(n);
    }
}
```

View có liên hệ với collection gốc; thay đổi ở collection gốc có thể làm view thay đổi hoặc vô hiệu hóa sublist theo hợp đồng `List`. Không nên công bố view ra nhiều thread mà không kiểm soát rõ monitor và vòng đời.

Ví dụ map:

```java
Map<String, Integer> map =
        Collections.synchronizedMap(new HashMap<>());
Set<String> keys = map.keySet();

synchronized (map) {
    for (String key : keys) {
        System.out.println(key);
    }
}
```

Việc `keys` có thể là một synchronized view không làm thay đổi quy tắc khóa: **dùng `map`** cho toàn bộ traversal.

## 6. Ví dụ Java 17 chạy độc lập: lost update và duyệt đúng khóa

Chương trình này tạo một race **có điều khiển** bằng `CountDownLatch`. Hai task cùng đọc giá trị cũ trước khi được phép ghi; kết quả chắc chắn cho thấy mất một lượt tăng dù `get` và `put` đều là method synchronized. Sau đó chương trình chuyển sang khóa ngoài bao trọn thao tác đọc–sửa–ghi và kiểm tra được hai lượt tăng.

Nửa sau minh họa cách duyệt synchronized-list và synchronized-map với kết quả ổn định. Để không phụ thuộc thứ tự duyệt của `HashMap`, ta tạo snapshot bên trong khóa rồi sắp xếp snapshot sau khi nhả khóa.

Lưu tệp **`SynchronizedCollectionsDemo.java`**:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SynchronizedCollectionsDemo {
    public static void main(String[] args) throws Exception {
        Map<String, Integer> counts =
                Collections.synchronizedMap(new HashMap<>());
        counts.put("hits", 0);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch mayWrite = new CountDownLatch(1);

        try {
            Callable<Void> unsafeIncrement = () -> {
                int old = counts.get("hits");  // Riêng get() là an toàn.
                bothRead.countDown();
                mayWrite.await();             // Cả hai đã đọc old=0.
                counts.put("hits", old + 1);   // Riêng put() là an toàn.
                return null;
            };

            Future<Void> first = pool.submit(unsafeIncrement);
            Future<Void> second = pool.submit(unsafeIncrement);

            bothRead.await();
            mayWrite.countDown();
            first.get();
            second.get();
            System.out.println("Separate get/put = " + counts.get("hits"));

            counts.put("hits", 0);
            Callable<Void> safeIncrement = () -> {
                synchronized (counts) {
                    int old = counts.get("hits");
                    counts.put("hits", old + 1);
                }
                return null;
            };

            first = pool.submit(safeIncrement);
            second = pool.submit(safeIncrement);
            first.get();
            second.get();
            System.out.println("Atomic get/put = " + counts.get("hits"));
        } finally {
            mayWrite.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }

        List<String> words = Collections.synchronizedList(
                new ArrayList<>(List.of("beta", "alpha")));
        List<String> wordSnapshot = new ArrayList<>();

        synchronized (words) {
            for (String word : words) {
                wordSnapshot.add(word);
            }
        }
        Collections.sort(wordSnapshot);
        System.out.println("List snapshot = "
                + String.join(",", wordSnapshot));

        Map<String, Integer> scores =
                Collections.synchronizedMap(new HashMap<>());
        scores.put("b", 2);
        scores.put("a", 1);
        List<String> entries = new ArrayList<>();

        synchronized (scores) {
            for (Map.Entry<String, Integer> entry : scores.entrySet()) {
                entries.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        Collections.sort(entries);
        System.out.println("Map snapshot = " + String.join(",", entries));
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac SynchronizedCollectionsDemo.java
java SynchronizedCollectionsDemo
```

Stdout xác định:

```text
Separate get/put = 1
Atomic get/put = 2
List snapshot = alpha,beta
Map snapshot = a=1,b=2
```

Giải thích:

- Trong thử nghiệm đầu, `bothRead` buộc **cả hai task đọc `0`** rồi mới được phép ghi. Do đó mỗi task đều ghi `1`; không có việc kết quả tình cờ là `2` vì một thread chạy hết trước.
- Ở thử nghiệm tiếp, mỗi task giữ monitor `counts` suốt thao tác lấy giá trị cũ và ghi giá trị mới. Không thread nào có thể chen giữa hai bước, nên kết quả là `2`.
- List và Map đều tạo snapshot trong khối `synchronized` rồi sắp xếp/in snapshot sau khi nhả khóa. Như vậy code bên ngoài khóa không giữ monitor lâu và kết quả in không lệ thuộc thứ tự của `HashMap`.
- Hai lần `Future.get()` khiến main chờ task xong, đồng thời phát hiện exception nếu task gặp lỗi. `shutdown()` ngừng pool nhận task mới; `awaitTermination` dùng để kết thúc gọn trong demo.

Đây là ví dụ giáo dục có `CountDownLatch` chủ động tạo tình huống race. Trong ứng dụng thực tế, ta không dùng latch để làm chậm mọi lần tăng biến; ví dụ chỉ cho thấy **method-level synchronization khác transaction-level synchronization**.

## 7. Snapshot: giảm thời gian giữ khóa và tránh duyệt trên collection đang đổi

Nếu thao tác duyệt có nhiều việc chậm (ghi file, gọi dịch vụ ngoài, tính toán tốn CPU), giữ khóa xuyên suốt có thể khiến tất cả thread muốn sửa collection bị chặn. Khi cần xem một ảnh chụp nhất quán tại một thời điểm, tạo bản sao **trong khi giữ khóa**, rồi xử lý bản sao bên ngoài:

```java
List<String> snapshot;
synchronized (items) {
    snapshot = new ArrayList<>(items);
}

for (String value : snapshot) {
    slowOperation(value);
}
```

Fragment này giả định `items` là synchronized-list wrapper và `slowOperation` do ứng dụng định nghĩa. Tác dụng: khóa chỉ được giữ trong lúc sao chép cấu trúc list. Tuy nhiên **snapshot này là shallow copy**: nếu phần tử là object mutable, các đối tượng phần tử vẫn có thể bị sửa bởi thread khác. Muốn ảnh chụp dữ liệu bất biến thực sự, cần sao chép/đóng băng phần tử hoặc thiết kế immutable phù hợp.

Tương tự với map:

```java
Map<String, Integer> snapshot;
synchronized (scores) {
    snapshot = new HashMap<>(scores);
}
for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
    slowOperation(entry);
}
```

Bản sao map sau khi nhả khóa không tự cập nhật khi `scores` thay đổi. Đây thường là điều mong muốn cho báo cáo hoặc xử lý nhất quán theo một thời điểm, nhưng cần chấp nhận dữ liệu có thể cũ ngay sau snapshot.

### So sánh thời gian giữ khóa

| Chiến lược | Tính nhất quán | Ảnh hưởng producer |
| --- | --- | --- |
| Duyệt trực tiếp trong `synchronized(list)` | Không thread tuân thủ monitor chen vào sửa trong lúc duyệt | Thread sửa/đọc qua wrapper có thể chờ đến hết traversal |
| Tạo snapshot trong khóa, xử lý ngoài khóa | Snapshot ổn định về cấu trúc sau khi copy | Producer chỉ chờ trong thời gian copy |
| Duyệt không khóa trên synchronized wrapper | Không được bảo đảm an toàn khi sửa đồng thời | Có race; không sử dụng |
| Dùng collection concurrent có iterator yếu nhất quán hoặc snapshot | Phụ thuộc hợp đồng từng loại | Thường giảm blocking, đổi lấy semantics và chi phí khác |

Snapshot hữu ích khi phép duyệt dài hơn chi phí sao chép; nếu collection khổng lồ hoặc việc tạo bản sao gây áp lực bộ nhớ/GC, cần cân nhắc thiết kế riêng.

## 8. Synchronized wrapper khác concurrent collection ở điểm nào?

`Collections.synchronizedMap/List` dùng một **khóa chung** cho các lời gọi cần đồng bộ. Đây là mô hình dễ hiểu nhưng contention có thể cao khi nhiều thread liên tục truy cập.

| Lựa chọn | Phù hợp | Lưu ý về traversal và cập nhật |
| --- | --- | --- |
| `Collections.synchronizedMap(new HashMap<>())` | Muốn semantics gần HashMap, hỗ trợ khóa ngoài cho chuỗi nhiều bước | Duyệt view phải khóa map gốc |
| `Collections.synchronizedList(new ArrayList<>())` | Muốn semantics gần ArrayList và khóa ngoài rõ ràng | Duyệt iterator/list phải khóa list wrapper |
| `ConcurrentHashMap<K,V>` | Nhiều thread đọc/cập nhật độc lập, cần giảm contention | Iterator *weakly consistent*, có thể phản ánh một phần thay đổi; không cần khóa toàn map để tránh race nội bộ |
| `CopyOnWriteArrayList<E>` | Đọc/duyệt nhiều, sửa rất ít | Iterator là snapshot tại lúc tạo; mỗi sửa thường phải copy mảng, chi phí lớn |
| Collection immutable (`List.of`, `Map.of`, copy phù hợp) | Chỉ đọc sau công bố an toàn | Không hỗ trợ cập nhật; phần tử mutable vẫn cần lưu ý |

`ConcurrentHashMap` có các phép cập nhật nguyên tử theo khóa như `compute`, `merge` và `putIfAbsent`. Nhưng nó **không** bảo đảm một phép duyệt toàn map là snapshot nguyên tử trong khi các thread vẫn cập nhật. Ngoài ra, `ConcurrentHashMap` không cho phép key/value `null`, khác với `HashMap` (và wrapper synchronizedMap bao quanh HashMap).

`CopyOnWriteArrayList` thích hợp với ít ghi, nhiều lượt duyệt, ví dụ danh sách listener hiếm khi thay đổi. Các iterator của nó dựa trên snapshot mảng và không hỗ trợ `remove()` qua iterator; mỗi thay đổi mảng có overhead sao chép.

`Collections.unmodifiableList` và `Collections.unmodifiableMap` chỉ chặn sửa **qua view**, không tự thêm khóa và không biến backing collection mutable thành thread-safe. Không dùng “unmodifiable” như một cách thay thế synchronization cho dữ liệu vẫn bị thread khác chỉnh sửa.

### Khi nào synchronized wrapper vẫn là lựa chọn hợp lý?

- Dữ liệu nhỏ hoặc lượng tranh chấp thấp.
- Muốn giữ ngữ nghĩa và API `HashMap`/`ArrayList`, gồm hành vi key/value `null` tương ứng.
- Cần thao tác nhiều bước với monitor có thể khóa bên ngoài.
- Không cần throughput cao khi có rất nhiều thread đọc/ghi đồng thời.

Khi throughput hoặc latency giảm vì một monitor chung, cân nhắc concurrent collection hoặc đổi thiết kế dữ liệu — không nên chỉ thêm nhiều thread.

## 9. Những thao tác nhiều bước khác dễ bị race

### 9.1. Kiểm tra rồi thêm vào List

```java
if (!items.contains("task-A")) {
    items.add("task-A");
}
```

Hai thread có thể đều thấy chưa tồn tại và cùng thêm. Muốn “thêm duy nhất một lần” theo hợp đồng list:

```java
synchronized (items) {
    if (!items.contains("task-A")) {
        items.add("task-A");
    }
}
```

Nếu mục tiêu thực sự là tập hợp duy nhất, `Set` phù hợp hơn; có thể dùng `Collections.synchronizedSet` hoặc concurrent set tùy nhu cầu.

### 9.2. Kiểm tra kích thước rồi xóa phần tử đầu

```java
if (!items.isEmpty()) {
    String first = items.remove(0);
}
```

Giữa `isEmpty()` và `remove(0)`, thread khác có thể xóa phần tử; khi đó code có thể ném `IndexOutOfBoundsException` dù từng method synchronized.

```java
String first = null;
synchronized (items) {
    if (!items.isEmpty()) {
        first = items.remove(0);
    }
}
```

Đây là fragment; với thao tác producer/consumer cần chờ đến khi có item, `BlockingQueue` thường diễn đạt đúng nghiệp vụ hơn một list được đồng bộ.

### 9.3. Duyệt và xóa có kiểm soát

Khi cần duyệt và xóa phần tử theo điều kiện, dùng iterator trong khóa:

```java
synchronized (items) {
    Iterator<String> it = items.iterator();
    while (it.hasNext()) {
        if (it.next().startsWith("old-")) {
            it.remove();
        }
    }
}
```

Không nên đồng thời gọi `items.remove(...)` từ một thread khác mà không giữ cùng monitor, cũng không sửa backing list gốc trực tiếp. `Iterator.remove()` hợp lệ tùy collection và trạng thái iterator; wrapper synchronized-list quanh `ArrayList` cho phép thao tác này khi đúng điều kiện của Iterator.

### 9.4. Sử dụng compute/merge thay vì chuỗi thủ công?

Nếu wrapper và JDK của bạn hỗ trợ `Map.merge` hoặc `compute` với đồng bộ hóa trên wrapper, một phép gọi đơn có thể thay thao tác nhiều bước. Ví dụ:

```java
counts.merge("hits", 1, Integer::sum);
```

Với `Collections.synchronizedMap` trong JDK hiện đại, phương thức `merge` được wrapper đồng bộ trong quá trình thực hiện. Đây là cách rõ ràng hơn so với `get` rồi `put` riêng biệt khi cập nhật một khóa. Tuy nhiên callback trong `compute/merge` có thể chạy trong lúc giữ monitor; không nên thực hiện I/O lâu, gọi code ngoài không kiểm soát hoặc tạo phụ thuộc khóa vòng trong callback.

## 10. Deadlock và thời gian giữ khóa

Mô hình khóa bằng monitor dễ dẫn tới deadlock nếu nhiều thread giữ các khóa theo thứ tự khác nhau.

Ví dụ fragment minh họa nguy cơ:

```java
// Thread 1
synchronized (listA) {
    synchronized (listB) {
        transferAtoB();
    }
}

// Thread 2
synchronized (listB) {
    synchronized (listA) {
        transferBtoA();
    }
}
```

Nếu hai thread lấy khóa ngoài cùng lúc, chúng có thể chờ nhau vô hạn. Hai hàm chuyển dữ liệu chỉ là ký hiệu minh họa, không phải chương trình độc lập. Giải pháp là áp dụng **một thứ tự khóa nhất quán** hoặc một lock cấp ứng dụng được thiết kế rõ.

Một vòng lặp:

```java
synchronized (items) {
    for (String item : items) {
        sendOverNetwork(item);
    }
}
```

có thể khóa list trong thời gian network chậm, khiến producer dừng rất lâu. Nếu không cần giữ collection ổn định tới tận lúc gửi, hãy sao chép snapshot trong khóa rồi gửi ở ngoài khóa. Đừng gọi callback không kiểm soát, chờ Future hoặc thực hiện I/O dài trong monitor mà không đánh giá nguy cơ latency/deadlock.

Các phương thức đồng bộ nội bộ của wrapper không có cơ chế timeout khi giành monitor. Nếu cần locking có timeout, có thể phải dùng thiết kế dựa trên `ReentrantLock.tryLock(...)` hoặc collection khác; đừng cố dùng vòng lặp `sleep` để giả lập thời hạn cho `synchronized`.

## 11. Lưu ý về memory visibility và publication

Monitor bảo đảm quan hệ *happens-before* từ lần unlock tới lần lock sau trên **cùng monitor**. Vì vậy, nếu nhiều thread chỉ đọc/ghi nội dung collection qua wrapper đúng cách, những cập nhật tương ứng được quan sát theo quy tắc bộ nhớ Java.

Nhưng những điều sau vẫn phải có thiết kế riêng:

1. **Backing collection bị lộ:** có thread giữ `raw` và thay đổi mà không khóa, phá vỡ hợp đồng.
2. **Phần tử mutable:** wrapper chỉ bảo vệ việc cấu trúc collection truy cập/chỉnh sửa qua nó; trường trong object chứa bên trong cần synchronization hoặc thiết kế immutable.
3. **Sử dụng nhiều monitor khác nhau:** `synchronized(raw)` và `synchronized(wrapper)` là hai khóa khác nhau nếu wrapper là object khác backing; khóa sai không đảm bảo bảo vệ trước tất cả cập nhật.
4. **Bỏ khóa khi lấy Iterator:** iterator không tự giữ lock từ lúc tạo đến lúc `next()`.
5. **Khởi tạo/công bố wrapper không an toàn:** một reference có thể được chia sẻ sai cách nếu không có quan hệ happens-before để thread đọc thấy state được xây dựng đầy đủ.

Đối với dữ liệu chỉ đọc sau khởi tạo, xây dựng immutable snapshot và công bố an toàn thường giảm đáng kể nhu cầu khóa.

## 12. Cách kiểm thử concurrency cho synchronizedMap/List

Không nên viết test chỉ dựa vào `Thread.sleep(10)` để “hy vọng” hai thread chạm race. Thời gian thực thi phụ thuộc JIT, OS scheduler, CPU và tải hệ thống.

Nên dùng:

- `CountDownLatch` để điều khiển hai thread cùng đọc rồi mới ghi, như chương trình ở phần 6.
- `ExecutorService` và `Future.get()` để đợi task hoàn tất và báo lỗi task.
- Nhiều lượt thực thi để kiểm tra invariants dưới tải nhưng không dựa hoàn toàn vào số lượt để chứng minh race không thể xảy ra.
- Test riêng cho compound action như “contains-then-add”, “read-modify-write”, “check-then-remove”.
- Test duyệt `Iterator`, map views và `subList` khi thread khác sửa; khẳng định rằng khóa ngoài đúng mang lại hợp đồng mong muốn thay vì chờ một `ConcurrentModificationException` ngẫu nhiên.
- Các phép đo thời gian giữ khóa, số thread chờ, p95 latency nếu collection là điểm nghẽn.

Đừng dùng `ConcurrentModificationException` làm tín hiệu đồng bộ: nó có thể xảy ra hoặc không xảy ra dưới race; hành vi không phải một hợp đồng quan sát nhất quán.

## 13. Checklist khi dùng synchronizedMap/List

1. **Wrapper được tạo một lần** và là đường truy cập chia sẻ chính; không để backing mutable collection bị sửa ngoài wrapper.
2. **Lời gọi đơn** như `get/add/put/remove` sử dụng synchronization của wrapper.
3. **Thao tác kết hợp** cần atomicity thì bọc toàn chuỗi bằng `synchronized(wrapper)` hoặc dùng một phép atomic phù hợp.
4. **Duyệt Iterator, Spliterator, Stream** trong khối `synchronized(wrapper)` theo hợp đồng API.
5. **Map views** (`keySet/entrySet/values`) luôn duyệt dưới khóa map wrapper gốc.
6. **List subList** duyệt dưới khóa list wrapper gốc.
7. **Tránh giữ khóa lâu** khi có I/O, callback, chờ hoặc tính toán nặng; cân nhắc snapshot.
8. **Phần tử mutable** cần được bảo vệ độc lập nếu các thread sửa trạng thái của chính phần tử.
9. **Kiểm soát thứ tự khóa** nếu thao tác liên quan nhiều wrapper khác nhau.
10. **Chọn đúng collection** nếu số thread và tần suất cập nhật cao: `ConcurrentHashMap`, `CopyOnWriteArrayList` hoặc `BlockingQueue` có thể có semantics phù hợp hơn.

## 14. Câu hỏi tự kiểm tra

**1. Hai thread cùng gọi `map.get()` và `map.put()` trên synchronizedMap có tạo thành một phép tăng nguyên tử không?**  
Không. Mỗi lời gọi riêng được đồng bộ, nhưng thread khác có thể xen giữa các bước. Khóa toàn read-modify-write hoặc dùng operation nguyên tử thích hợp.

**2. Vì sao enhanced for trên synchronizedList cần khóa ngoài?**  
Enhanced for dùng Iterator với nhiều bước; wrapper không giữ monitor trong toàn traversal khi caller chỉ lấy Iterator rồi duyệt.

**3. Khi duyệt `map.entrySet()`, khóa `entrySet()` hay `map`?**  
Khóa **synchronized-map wrapper gốc** (`map`). Khóa view riêng không thay thế được hợp đồng này.

**4. Nếu dùng `list.subList(...)` rồi duyệt, khóa nào đúng?**  
Khóa **synchronized-list wrapper gốc**, không chỉ khóa sublist được trả về.

**5. Nếu chưa từng gặp `ConcurrentModificationException`, vòng duyệt không khóa đã an toàn chưa?**  
Chưa. Fail-fast detection là best-effort, không chứng minh không có race.

**6. Snapshot có bảo vệ việc sửa trạng thái của từng object bên trong không?**  
Không khi snapshot chỉ sao chép tham chiếu. Phần tử mutable vẫn chia sẻ cùng object.

**7. Vì sao `ConcurrentHashMap` không cần khóa toàn map để iterator tránh lỗi nội bộ?**  
Nó có iterator weakly consistent được thiết kế cho cập nhật đồng thời. Nhưng iterator không cho một snapshot nguyên tử của toàn map.

**8. Khi nào `CopyOnWriteArrayList` hữu ích hơn synchronizedList?**  
Khi traversal/đọc rất nhiều, cập nhật rất hiếm và chấp nhận chi phí copy mảng trên mỗi lần sửa.

**9. Gọi `Collections.unmodifiableList(raw)` có biến `raw` thành thread-safe không?**  
Không. View unmodifiable chỉ chặn sửa qua view; backing collection vẫn có thể bị sửa và cần synchronization.

## Tài liệu tham khảo

- [Java SE 17 — Collections](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html)
- [Java SE 17 — List](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html)
- [Java SE 17 — Map](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)
- [Java SE 17 — ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)
- [Java SE 17 — CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-011">Concurrent queue, blocking queue và backpressure</a>

<details>
<summary>Xem chi tiết</summary>

# Concurrent queue, blocking queue và backpressure trong Java

## 1. Tại sao hàng đợi thread-safe chưa đủ?

Trong một hệ thống có **producer** tạo công việc và **consumer** xử lý công việc, hàng đợi (queue) giúp tách hai tốc độ thực thi. Producer có thể thêm tác vụ mà không cần đợi consumer xử lý xong từng tác vụ; consumer lấy các mục trong queue để làm việc.

~~~text
    Producer A ----\
                    \         +-----------------+        +-------------+
    Producer B ------> ------>| Shared Queue    |------->| Consumer(s) |
                    /         +-----------------+        +-------------+
    Producer C ----/
~~~

Vấn đề có ba lớp khác nhau:

1. **Thread safety:** các thao tác thêm/lấy từ nhiều thread không phá hỏng cấu trúc hàng đợi, không làm mất phần tử chỉ vì race nội bộ.
2. **Blocking / signaling:** producer có thể đợi khi queue đầy; consumer có thể đợi khi queue trống, thay vì tự spin liên tục.
3. **Backpressure:** hệ thống có cơ chế **giới hạn, làm chậm hoặc từ chối** công việc đầu vào khi downstream không theo kịp, nhằm không tiêu thụ tài nguyên vô hạn.

**Concurrent queue** giải quyết chủ yếu vấn đề 1. **Blocking queue** cung cấp các primitive cho vấn đề 1 và 2. **Backpressure** là chiến lược kiểm soát tải toàn tuyến dựa trên queue, giới hạn xử lý đồng thời, admission control, timeout và chính sách quá tải; không tự động xuất hiện chỉ vì dùng một collection có chữ “concurrent”.

Bài này nói về Java Concurrency nói chung, sử dụng thư viện chuẩn `java.util.concurrent` mà không gắn với một framework cụ thể.

## 2. Queue, ConcurrentLinkedQueue và BlockingQueue khác gì?

**`Queue<E>`** là interface cơ bản trong Collections Framework. Nó quy định các thao tác thêm, lấy, xem phần tử ở đầu queue nhưng không mặc định bảo đảm thread-safe hoặc hỗ trợ đợi.

**`ConcurrentLinkedQueue<E>`** là hàng đợi FIFO thread-safe, **không giới hạn capacity nội tại**, với các thao tác thêm/lấy không chờ đến khi queue đầy/trống. Thuật toán đồng thời của nó dựa trên các primitive non-blocking. `offer(e)` thêm phần tử và thông thường trả `true`; `poll()` trả `null` nếu lúc đó trống. Không dùng `null` làm phần tử.

**`BlockingQueue<E>`** là interface mở rộng Queue, bổ sung **`put`/`take`** để chờ và các overload **`offer(timeout)`/`poll(timeout)`** để chờ có hạn. BlockingQueue có cả implementation **bounded** và **không có giới hạn dung lượng hữu ích theo cấu hình**. Vì vậy “BlockingQueue” không đồng nghĩa với “hàng đợi luôn có backpressure”.

| Khía cạnh | `ConcurrentLinkedQueue` | `BlockingQueue` với implementation bounded |
|---|---|---|
| Thread-safe cho thao tác queue | Có | Có |
| Khi trống, `poll()` | Trả `null` ngay | Trả `null` ngay |
| Đợi có phần tử bằng `take()` | Không có phương thức này | Có |
| Khi đầy | Không có giới hạn capacity nội tại | `offer()` có thể trả false; `put()` đợi |
| Đợi còn chỗ có timeout | Không có | `offer(e, timeout, unit)` |
| Cơ chế kiểm soát tải | Cần giới hạn ngoài | Có thể tạo backpressure với capacity và chính sách phù hợp |
| Ví dụ thích hợp | Danh sách thông báo nội bộ cần truy xuất non-blocking, lượng vào đã được giới hạn | Producer–consumer với hàng chờ bounded và giao thức overload rõ |

**Không phải mọi Queue giữ FIFO.** `PriorityBlockingQueue` ưu tiên theo ordering của phần tử/comparator, không phải FIFO; `DelayQueue` cho phép lấy phần tử khi hết độ trễ. Cần chọn theo hợp đồng **thứ tự**, **capacity** và **blocking** cụ thể.

## 3. Bốn kiểu xử lý khi BlockingQueue không thể đáp ứng ngay

Tài liệu Java mô tả bốn nhóm thao tác:

| Hành động | Ném ngoại lệ ngay | Giá trị đặc biệt ngay | Chờ không giới hạn thời gian | Chờ có timeout |
|---|---|---|---|---|
| **Thêm** | `add(e)` | `offer(e)` → boolean | `put(e)` | `offer(e, time, unit)` → boolean |
| **Lấy và xóa đầu** | `remove()` | `poll()` → E hoặc `null` | `take()` → E | `poll(time, unit)` → E hoặc `null` |
| **Chỉ xem đầu** | `element()` | `peek()` → E hoặc `null` | Không có | Không có |

### Thao tác thêm

- `add(e)`: nếu queue bị giới hạn và đầy, thông thường ném `IllegalStateException`; phù hợp khi thất bại được coi là điều bất thường.
- `offer(e)`: **không chờ** chỗ trống. Trên queue bounded, trả `false` nếu không thêm được vì đầy. Đừng bỏ qua giá trị trả về: bỏ qua có thể làm **mất công việc âm thầm**.
- `put(e)`: có thể **block vô thời hạn** tới khi có capacity; có thể ném `InterruptedException` nếu caller bị ngắt trong khi đợi.
- `offer(e, timeout, unit)`: chờ tối đa một thời hạn; trả false nếu không có chỗ trong hạn. Đây thường là một primitive tốt khi có SLA/deadline.

### Thao tác lấy

- `poll()`: lấy và xóa đầu ngay nếu có; trả `null` nếu trống.
- `take()`: chờ cho đến khi có phần tử; là thao tác phổ biến của consumer thread.
- `poll(timeout, unit)`: chờ có hạn và trả `null` nếu không có phần tử; phù hợp khi consumer còn cần định kỳ kiểm tra trạng thái stop.
- `peek()`: chỉ xem đầu, không xóa; không dùng cho mô hình “nhận việc để xử lý” vì nhiều consumer có thể thấy cùng một phần tử.

Tất cả các triển khai `BlockingQueue` chuẩn **không chấp nhận null**; `null` là giá trị biểu thị không lấy được từ `poll()`. Không dùng null làm poison pill.

**Timeout không phải hủy downstream:** khi `offer(timeout)` trả false, **phần tử đó chưa được nhận vào queue**, caller phải quyết định trả lỗi, retry có giới hạn hoặc lưu ở nơi khác. Khi `poll(timeout)` trả null, chỉ biết không lấy được trong thời hạn, không có nghĩa hệ thống đã kết thúc.

## 4. Chọn implementation phù hợp

| Implementation | Capacity | Thứ tự/chức năng | Lưu ý thực tế |
|---|---|---|---|
| `ConcurrentLinkedQueue` | Không bounded nội tại | FIFO, thao tác non-blocking | Không tạo admission backpressure; `size()` O(n), không nên polling liên tục |
| `ArrayBlockingQueue(capacity)` | **Bounded bắt buộc** | FIFO, backing array | Dễ đặt ngân sách phần tử; có tùy chọn fairness, có thể giảm throughput |
| `LinkedBlockingQueue(capacity)` | Bounded nếu **truyền capacity** | FIFO, linked nodes | Constructor không capacity mặc định gần như không giới hạn hữu ích (`Integer.MAX_VALUE`) |
| `SynchronousQueue` | **0 phần tử chờ** | Rendezvous trao trực tiếp producer–consumer | `put` cần có consumer nhận; không có buffer; `offer` có thể thất bại khi không có người nhận |
| `PriorityBlockingQueue` | Không bounded nội tại | Priority | Không bảo đảm FIFO; `put` không chờ vì queue đầy theo một capacity cố định |
| `DelayQueue` | Không bounded nội tại | Chỉ lấy phần tử đã hết delay | Phù hợp scheduling; không thay thế bounded work queue |
| `LinkedBlockingDeque(capacity)` | Bounded nếu cấu hình | Thêm/lấy hai đầu | Dùng nếu thực sự cần deque; cân nhắc fairness và starvation của tác vụ |

**Cẩn thận với default:** `new LinkedBlockingQueue<>()` dùng capacity `Integer.MAX_VALUE`. Dù là BlockingQueue, producer thông thường **không bị chặn vì đầy** trước khi áp lực bộ nhớ trở thành vấn đề. Muốn giới hạn thực sự, sử dụng ví dụ `new LinkedBlockingQueue<>(1000)` hoặc `new ArrayBlockingQueue<>(1000)`.

**`remainingCapacity()` không giữ chỗ:** kết quả là một snapshot có thể lỗi thời ngay vì producer khác cùng đang thêm. Tránh kiểu `if (queue.remainingCapacity() > 0) queue.add(task)`. Dùng một lời gọi nguyên tử theo hợp đồng: `offer`, `put` hoặc `offer(timeout)`.

## 5. Ví dụ Java 17: các thao tác non-blocking và bounded

Ví dụ này cố ý chỉ có một thread để đầu ra xác định, minh họa hợp đồng API trước khi đi vào đa luồng.

Lưu **QueueApiDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class QueueApiDemo {
    public static void main(String[] args) throws Exception {
        ConcurrentLinkedQueue<String> concurrent =
                new ConcurrentLinkedQueue<>();
        concurrent.offer("A");
        concurrent.offer("B");

        System.out.println("nonblocking peek=" + concurrent.peek());
        System.out.println("nonblocking poll=" + concurrent.poll());
        System.out.println("nonblocking next=" + concurrent.poll());

        ArrayBlockingQueue<Integer> bounded = new ArrayBlockingQueue<>(2);
        System.out.println("bounded add 1=" + bounded.offer(1));
        System.out.println("bounded add 2=" + bounded.offer(2));
        System.out.println("bounded add 3=" + bounded.offer(3));
        System.out.println("bounded remaining=" + bounded.remainingCapacity());
        System.out.println("bounded take=" + bounded.take());
        System.out.println("bounded offer 3=" + bounded.offer(3));
        System.out.println("bounded poll 2=" + bounded.poll());
        System.out.println("bounded poll 3=" + bounded.poll());
    }
}
~~~

Chạy:

~~~bash
javac QueueApiDemo.java
java QueueApiDemo
~~~

Stdout:

~~~text
nonblocking peek=A
nonblocking poll=A
nonblocking next=B
bounded add 1=true
bounded add 2=true
bounded add 3=false
bounded remaining=0
bounded take=1
bounded offer 3=true
bounded poll 2=2
bounded poll 3=3
~~~

Điểm cần hiểu: `offer(3)` thất bại khi `ArrayBlockingQueue` đầy là tín hiệu **không nhận thêm**. Sau khi consumer `take()` lấy một phần tử, có một vị trí trống và `offer(3)` thành công. Trong một hệ thống có nhiều producer, không được suy luận trạng thái capacity sau một lời gọi quan sát riêng mà không có phép thêm thật.

## 6. Backpressure xảy ra khi nào?

Ký hiệu:

- `λ` (*lambda*): tốc độ công việc đến, ví dụ **requests/giây**.
- `μ` (*mu*): tốc độ consumer xử lý được ở tải hiện tại.
- `Q`: số công việc đang **chờ trong queue**.
- `K`: capacity tối đa của queue.
- `I`: số công việc **đang được xử lý**, không còn nằm trong queue.

Khi `λ > μ` kéo dài, lượng tồn đọng tăng xấp xỉ theo chênh lệch `λ - μ` cho tới khi queue đầy hoặc tài nguyên cạn. Một queue unbounded chỉ **dịch thời điểm quá tải** từ producer sang bộ nhớ, GC và độ trễ chờ; nó không làm consumer nhanh hơn.

~~~text
Incoming rate λ = 150 tasks/s
Processing rate μ = 100 tasks/s
Backlog growth ≈ 50 tasks/s

Sau 20 s: thêm khoảng 1.000 task còn đợi
(nếu tốc độ ổn định và không có cơ chế giới hạn khác).
~~~

Với queue capacity `K = 200` và bắt đầu trống, nếu chênh lệch thực sự là 50 task/s và các điều kiện lý tưởng giữ nguyên, queue có thể đầy sau khoảng 4 giây. Đây là mô hình gần đúng, **không phải công thức chọn capacity production**: tốc độ thực tế biến thiên, có burst, công việc tốn thời gian khác nhau và queue có thể được làm rỗng trong lúc nhận tải.

Khi hệ thống ổn định, **Little's Law** biểu diễn quan hệ trung bình `L = λ × W`: số mục trung bình trong **một phần hệ thống được xác định rõ** bằng throughput trung bình nhân với thời gian lưu trú trung bình. Nếu tính queue riêng, dùng **thời gian chờ queue**; nếu tính cả đang xử lý, dùng **thời gian trong hệ thống**. Không lấy queue length nhân throughput để suy ra throughput, cũng không áp dụng máy móc khi backlog tăng không ổn định.

**Backpressure** nghĩa là khi downstream chậm, upstream phải **nhận biết và thay đổi hành vi**: bị block, chờ có hạn, giảm tốc, bị từ chối, bỏ tác vụ không quan trọng hoặc chuyển sang tầng lưu bền vững. Queue bounded chỉ tạo **điểm phát hiện áp lực**; chính sách ở điểm đó quyết định hệ thống có thực sự tự bảo vệ không.

## 7. Ví dụ Java 17: chứng minh put/take có thể block

Thay vì dùng `Thread.sleep()` để đoán thứ tự, chương trình dưới đây dùng **CountDownLatch**. Queue capacity 1 đã chứa `first`, vì vậy một producer gọi `put("second")` chưa thể hoàn tất cho đến khi main lấy `first`. Sau đó, khi queue trống, một consumer chờ bằng `take()` và chỉ hoàn tất sau khi main thêm `third`.

Lưu **BlockingBackpressureDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BlockingBackpressureDemo {
    public static void main(String[] args) throws Exception {
        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch putting = new CountDownLatch(1);
        CountDownLatch taking = new CountDownLatch(1);

        try {
            queue.put("first");

            Future<?> producer = executor.submit(() -> {
                putting.countDown();
                queue.put("second");
                return null;
            });

            putting.await();
            System.out.println("producer done before space=" + producer.isDone());
            System.out.println("take=" + queue.take());
            producer.get(5, TimeUnit.SECONDS);
            System.out.println("next=" + queue.take());

            Future<String> consumer = executor.submit(() -> {
                taking.countDown();
                return queue.take();
            });

            taking.await();
            System.out.println("consumer done while empty=" + consumer.isDone());
            queue.put("third");
            System.out.println("consumer got=" + consumer.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
~~~

Chạy:

~~~bash
javac BlockingBackpressureDemo.java
java BlockingBackpressureDemo
~~~

Stdout khi worker hoàn thành trong thời hạn kiểm tra:

~~~text
producer done before space=false
take=first
next=second
consumer done while empty=false
consumer got=third
~~~

**Tại sao logic chắc chắn?** Trước `queue.take()` đầu tiên, `queue` đầy nên producer không thể hoàn tất `put("second")`. Trước `queue.put("third")`, queue trống và consumer không thể hoàn tất `take()`. Hai latch chỉ xác nhận worker đã đến vùng mã tương ứng, tránh race do đoán thời điểm worker bắt đầu.

Đây là **backpressure tự nhiên ở vị trí producer**: producer bị chặn thay vì tạo một backlog vô hạn. Tuy nhiên, `put()` có thể chờ vô hạn nếu không còn consumer, nên môi trường có yêu cầu latency/deadline thường nên cân nhắc `offer(timeout)` và quản lý shutdown/interrupt.

## 8. Thiết kế chính sách khi queue đầy

Một queue bounded muốn hoạt động tốt phải đi cùng **chính sách overload rõ ràng**, không chỉ một con số capacity.

### A. Chờ vô thời hạn: put(e)

Phù hợp với pipeline nội bộ có cam kết consumer tiếp tục hoạt động, producer được phép chờ và không có SLA đầu vào quá nghiêm ngặt. Cần có cách dừng/interruption; một worker producer bị kẹt mãi trong `put()` có thể khiến shutdown không hoàn tất.

### B. Chờ có hạn: offer(e, timeout, unit)

Đây thường là lựa chọn an toàn cho ingestion có deadline:

~~~java
// MÃ MINH HỌA: queue là BlockingQueue<Job>, job là một tác vụ.
if (!queue.offer(job, 100, TimeUnit.MILLISECONDS)) {
    // Không nhận thêm vào queue trong deadline.
    rejectOrDefer(job);
}
~~~

Đoạn code trên **không phải chương trình độc lập**; `Job` và `rejectOrDefer` là logic ứng dụng giả định. Lời gọi overload có timeout cũng có thể ném `InterruptedException`. Cần xử lý theo chính sách caller; tránh nuốt interrupt.

### C. Từ chối nhanh: offer(e) trả false

Thích hợp khi upstream có cơ chế retry/backoff, thông điệp lỗi tải cao hoặc mô hình load shedding. Không nên gửi lại yêu cầu **ngay lập tức vô hạn**, vì retry storm có thể làm quá tải nặng hơn.

### D. Loại bỏ/hợp nhất công việc

Với metric tổng hợp, telemetry hoặc trạng thái cập nhật liên tục, có thể **coalesce** nhiều bản cập nhật thành bản mới nhất, hoặc drop những event không quan trọng theo chính sách minh bạch. Không áp dụng cách này cho thanh toán, giao dịch hoặc công việc không được phép mất. Mọi drop cần metric và giải thích ý nghĩa nghiệp vụ.

### E. Lưu bền vững và xử lý sau

Khi không thể mất dữ liệu, có thể dùng cơ chế lưu trữ bền vững với retry và xác nhận tiêu thụ. **BlockingQueue chỉ là hàng đợi trong bộ nhớ JVM**, không tự bảo đảm xử lý sau crash, không cung cấp durable acknowledgement, deduplication hay exactly-once processing.

### F. Giới hạn concurrency ở downstream

Queue bounded chưa giới hạn triệt để số request in-flight, connection, task phụ hoặc retry đang hoạt động ở các tầng khác. Có thể cần `Semaphore` để giới hạn số thao tác đồng thời và admission control tại cửa vào. Đặt queue capacity, worker count và downstream concurrency theo tài nguyên thực sự thay vì chọn độc lập.

## 9. Ví dụ Java 17: offer có timeout và quyết định từ chối

Với queue capacity 1 đang đầy và **không có consumer**, `offer("late", 30ms)` không thể thêm, trả false khi hết thời hạn. Sau khi lấy phần tử cũ, producer có thể thử lại.

Lưu **TimedOfferDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TimedOfferDemo {
    public static void main(String[] args) throws Exception {
        ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(1);
        queue.put("first");

        boolean accepted = queue.offer(
                "late", 30, TimeUnit.MILLISECONDS);
        System.out.println("accepted while full=" + accepted);
        System.out.println("oldest=" + queue.take());

        boolean acceptedAfterDrain = queue.offer(
                "late", 30, TimeUnit.MILLISECONDS);
        System.out.println("accepted after drain=" + acceptedAfterDrain);
        System.out.println("received=" + queue.take());
    }
}
~~~

Chạy:

~~~bash
javac TimedOfferDemo.java
java TimedOfferDemo
~~~

Stdout:

~~~text
accepted while full=false
oldest=first
accepted after drain=true
received=late
~~~

Đây là ví dụ **đơn giản về timeout trên queue**, không phải chính sách retry hoàn chỉnh. Trong production, phải xác định còn đủ thời gian theo **deadline toàn request** không, và có thể retry bao nhiêu lần. Nếu không có capacity sau timeout thì caller cần trả về trạng thái “chưa được nhận” hoặc thực hiện hành động khác có thể quan sát được.

## 10. Ví dụ Java 17: pipeline nhiều consumer và graceful shutdown

Một pipeline cần giải quyết cả **hàng đợi bounded** lẫn **lifecycle**. Ví dụ dưới đây có một producer, hai consumer và capacity 2. Producer gửi số từ 1 đến 6. Sau khi producer hoàn tất việc đưa tất cả số vào queue, main đặt **hai poison pill** (một cho mỗi consumer). Các consumer xử lý bình phương và ngừng khi gặp sentinel.

Để stdout ổn định, chương trình **sắp xếp kết quả** sau khi cả hai consumer kết thúc, thay vì in ngay trên worker vốn có thứ tự không xác định.

Lưu **BoundedPipelineDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BoundedPipelineDemo {
    private static final int STOP = Integer.MIN_VALUE;

    public static void main(String[] args) throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);
        ConcurrentLinkedQueue<Integer> processed = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(3);

        Callable<Void> consume = () -> {
            while (true) {
                int value = queue.take();
                if (value == STOP) {
                    return null;
                }
                processed.add(value * value);
            }
        };

        try {
            Future<Void> first = executor.submit(consume);
            Future<Void> second = executor.submit(consume);
            Future<?> producer = executor.submit(() -> {
                for (int i = 1; i <= 6; i++) {
                    queue.put(i);
                }
                return null;
            });

            producer.get(); // Tất cả phần tử dữ liệu đã được nhận.
            queue.put(STOP);
            queue.put(STOP); // Một sentinel cho mỗi consumer.

            first.get();
            second.get();

            List<Integer> sorted = new ArrayList<>(processed);
            Collections.sort(sorted);
            System.out.println("processed=" + sorted);
            System.out.println("count=" + sorted.size());
        } finally {
            executor.shutdownNow();
        }
    }
}
~~~

Chạy:

~~~bash
javac BoundedPipelineDemo.java
java BoundedPipelineDemo
~~~

Stdout:

~~~text
processed=[1, 4, 9, 16, 25, 36]
count=6
~~~

**Đặc điểm đúng:**

- `queue.put(i)` sẽ chờ khi queue capacity 2 đầy, làm producer chậm theo khả năng tiêu thụ.
- Mọi consumer cùng `take()`, mỗi phần tử được một consumer lấy khỏi queue (không có chuyện hai consumer cùng `take()` một phần tử).
- `ConcurrentLinkedQueue` dùng để **thu thập kết quả của ví dụ**, không làm work queue đầu vào; dữ liệu hữu hạn chỉ có sáu kết quả. Nó không thay thế một output queue bounded nếu kết quả cũng có thể tăng vô hạn trong ứng dụng thật.
- `producer.get()` phải hoàn tất **trước** khi gửi sentinel; nếu gửi sentinel sớm, consumer có thể dừng trước khi nhận hết dữ liệu.
- Hai sentinel dành cho hai consumer. Nếu consumer count khác, cách gửi poison pill cũng phải phù hợp.

**Giới hạn của ví dụ:** Poison pill chỉ là một giao thức dừng **tự chọn**, không phải tính năng của BlockingQueue. Sentinel phải là giá trị **không thể nhầm với dữ liệu thật**. Nếu có nhiều producer, chỉ được phát sentinel khi tất cả producer đã ngừng gửi. Nếu consumer fail trước khi nhận sentinel và queue đầy, producer gửi sentinel có thể kẹt; production cần giám sát failure, timeout, đóng nhận việc, cancellation và quy trình xử lý phần tử còn tồn đọng.

## 11. ThreadPoolExecutor: bounded queue và cơ chế từ chối

Khi dùng `ThreadPoolExecutor`, hàng đợi của executor đóng vai trò **buffer chờ chạy task**, nhưng không đồng nghĩa task đang chạy. Cấu hình quan trọng bao gồm:

- `corePoolSize`, `maximumPoolSize`.
- `BlockingQueue<Runnable> workQueue` với capacity hữu hạn hay không.
- `RejectedExecutionHandler` được gọi khi executor không thể nhận thêm tác vụ theo quy tắc pool hoặc khi đang shutdown.

Các chính sách từ chối tiêu biểu:

| Chính sách | Hành vi khi saturated | Lưu ý |
|---|---|---|
| `AbortPolicy` | Ném `RejectedExecutionException` | Caller phải xử lý và trả trạng thái overload rõ ràng |
| `CallerRunsPolicy` | Thực thi task **ngay trong thread gọi execute** khi executor chưa shutdown | Làm chậm caller, tạo backpressure nhưng có thể ảnh hưởng độ trễ request/UI; khi đã shutdown, chính sách không chạy tác vụ |
| `DiscardPolicy` | Bỏ task bị từ chối | Dễ mất công việc âm thầm nếu không đo lường |
| `DiscardOldestPolicy` | Bỏ một task cũ từ queue rồi thử gửi lại | Dữ liệu có thể mất, cần hiểu thứ tự/priority và quy tắc nghiệp vụ |

**Bẫy:** `Executors.newFixedThreadPool(n)` theo implementation chuẩn dùng một **LinkedBlockingQueue không giới hạn capacity hữu ích**. Dù số worker là n, queue vẫn có thể phình lên khi tiếp nhận nhanh hơn xử lý. Nếu cần kiểm soát lượng backlog, tạo `ThreadPoolExecutor` với queue bounded và rejection policy rõ ràng.

### Ví dụ Java 17: CallerRunsPolicy làm chậm bên gửi

Lưu **CallerRunsDemo.java**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CallerRunsDemo {
    public static void main(String[] args) throws Exception {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.CallerRunsPolicy());

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean executedByCaller = new AtomicBoolean();
        Thread caller = Thread.currentThread();

        try {
            Future<?> busy = pool.submit(() -> {
                started.countDown();
                release.await();
                return null;
            });
            started.await();

            Future<?> queued = pool.submit(() -> "queued");
            pool.execute(() -> executedByCaller.set(
                    Thread.currentThread() == caller));

            System.out.println("caller ran=" + executedByCaller.get());
            release.countDown();
            busy.get();
            queued.get();
            System.out.println("all accepted work finished=true");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
~~~

Chạy:

~~~bash
javac CallerRunsDemo.java
java CallerRunsDemo
~~~

Stdout:

~~~text
caller ran=true
all accepted work finished=true
~~~

Worker duy nhất đang đợi latch, queue capacity 1 đã có tác vụ thứ hai. Khi gọi `execute` tác vụ thứ ba, executor đang saturated, nên `CallerRunsPolicy` thực thi tác vụ **ngay trên thread caller**, không đưa thêm vào queue. Điều này có thể tạo backpressure cho một producer đồng bộ, nhưng nếu caller là thread xử lý HTTP/UI, thời gian request cũng tăng. Nếu caller chịu trách nhiệm nhận tiếp nhiều yêu cầu thì chính sách có thể ảnh hưởng rộng hơn dự kiến.

**Quan trọng:** `CallerRunsPolicy` không phải đảm bảo “không bao giờ mất task” khi pool shutdown; hãy kiểm tra lifecycle và dùng rejection policy phù hợp với yêu cầu độ tin cậy.

## 12. Hạn chế số request in-flight bằng Semaphore

Một queue bounded chỉ giới hạn **mục đang chờ trong queue**, chưa chắc giới hạn tổng tất cả phần việc đang chạy ở các pool phụ hoặc số outbound request tới database. Nếu tải downstream là nút thắt, dùng `Semaphore` để giới hạn số phép xử lý có thể bắt đầu đồng thời:

~~~java
// MINH HỌA (không phải class độc lập):
if (!slots.tryAcquire(100, TimeUnit.MILLISECONDS)) {
    rejectRequest();
    return;
}
try {
    callDownstreamWithTimeout();
} finally {
    slots.release();
}
~~~

Ở đây `slots` là `Semaphore` đã khai báo, `rejectRequest()` và `callDownstreamWithTimeout()` là logic nghiệp vụ giả định. Điểm then chốt là **chỉ release khi đã acquire thành công**, và release trong `finally` để không rò permit khi tác vụ lỗi.

Không nên giữ permit trong khi một công việc chưa đến lượt xử lý **nếu permit chỉ dành cho giới hạn xử lý đang chạy**. Nếu muốn giới hạn **toàn bộ hệ thống đang tiếp nhận**, bao gồm queued + running, định nghĩa rõ phạm vi permit và acquire trước admission; release khi công việc thật sự xong hoặc bị loại bỏ. Việc chọn vị trí acquire quyết định bạn đang giới hạn thành phần nào.

## 13. Thiết kế shutdown và xử lý interruption

**`BlockingQueue` không có phương thức `close()` chung**. Vì vậy producer/consumer phải tự có protocol kết thúc: cờ trạng thái + interrupt; sentinel/poison pill; hoặc quản lý vòng đời `ExecutorService` và `Future`.

Một quy trình có kiểm soát:

1. **Chặn producer mới** hoặc chuyển hệ thống sang trạng thái STOPPING.
2. **Dừng hoặc chờ producer hiện tại** theo policy; không để producer tiếp tục enqueue sau khi đã đặt sentinel.
3. Quyết định **drain queue** (xử lý hết) hay **discard** (bỏ có ghi nhận). Không có đáp án chung.
4. Nếu drain, bảo đảm consumer còn sống đủ để lấy việc; chỉ phát tín hiệu stop **sau khi** producer không còn thêm việc.
5. Chờ consumer hoàn tất có **deadline**; nếu quá hạn, cân nhắc interrupt và phương án xử lý các mục chưa xong.
6. Phân biệt “queue trống” với “mọi công việc đã xử lý thành công”: consumer có thể đã lấy phần tử nhưng vẫn đang chạy hoặc bị lỗi.

Khi `put()`/`take()`/`offer(timeout)` ném `InterruptedException`, đừng nuốt rồi loop vô hạn. Thông thường truyền tiếp exception; hoặc khôi phục cờ interrupt bằng `Thread.currentThread().interrupt()` rồi thoát nếu catch tại chỗ. Chỉ gọi `shutdownNow()` không đảm bảo mọi side effect bên ngoài được rollback hoặc task bị kill tức thì.

**Poison pill** có rủi ro khi số consumer động, khi một consumer chết, khi queue đầy hoặc khi có nhiều producer chưa ngừng. Cần xác định policy và ownership trước khi dùng để shutdown trong production.

## 14. Monitoring: nhìn vào toàn bộ pipeline

Các metric có giá trị:

- **Ingress rate** và **completion rate**, theo từng loại công việc.
- **Queue depth** và **queue capacity**, đặc biệt là tỷ lệ chiếm dụng `depth / capacity`.
- **Queue wait time** (p50/p95/p99), **processing time** và **end-to-end latency**.
- Số lượng **active consumer/worker**, số yêu cầu **in-flight** và **downstream saturation**.
- Tần suất `offer` trả false, **put blocked duration**, timeout, rejection, drop và retry.
- Số task lỗi, số lần phải shutdown/escalation và backlog tồn sau shutdown.

`ConcurrentLinkedQueue.size()` là thao tác **O(n)**, có thể phản ánh kết quả không chính xác nếu queue bị sửa đồng thời; không polling nó với tần suất cao để làm metric chi tiết. Với queue khác, `size()` hoặc `remainingCapacity()` cũng chỉ là snapshot cho quan sát, **không phải chỗ đã được giữ trước cho một producer**.

Nếu backlog tăng liên tục, cần kiểm tra liệu do ingress rate tăng, consumer chậm, downstream timeout/lock contention, số worker giảm hay do retry đang khuếch đại tải. **Tăng capacity** chỉ làm tăng thời gian chịu burst và thường tăng độ trễ chờ; nó không sửa được bottleneck trong trạng thái quá tải kéo dài.

## 15. Những lỗi thường gặp

| Sai lầm | Hệ quả | Cách xử lý |
|---|---|---|
| Dùng `ConcurrentLinkedQueue` rồi nghĩ sẽ tự backpressure | Producer tiếp tục thêm, backlog tăng | Giới hạn admission bên ngoài hoặc dùng bounded BlockingQueue |
| `new LinkedBlockingQueue<>()` mà không đặt capacity | Queue có thể tăng đến áp lực bộ nhớ | Truyền capacity hữu hạn |
| Bỏ qua `offer()` trả false | Task không được nhận nhưng không ai biết | Xử lý rejection/drop/retry có metric |
| Gọi `put()` trên thread phải đáp ứng deadline ngắn | Có thể chờ vô hạn | Dùng `offer(timeout)` với deadline rõ |
| Busy-spin `while (queue.poll() == null) {}` | Đốt CPU | Dùng `take()`, `poll(timeout)` hoặc tín hiệu phù hợp |
| Dùng `peek()` thay `take()/poll()` để nhận việc | Nhiều consumer có thể xử lý cùng mục | Dùng thao tác lấy và xóa |
| Đọc `size()` rồi mới `add()` như một giao dịch | Race giữa kiểm tra và insert | Dùng `offer/put` theo hợp đồng |
| Nghĩ bounded queue là bảo đảm không mất dữ liệu | Crash, consumer lỗi, drop vẫn gây mất | Định nghĩa durability/ack/retry riêng |
| Gửi poison pill trước khi producer xong | Consumer dừng, dữ liệu còn lại bị bỏ | Hoàn tất producer trước khi gửi sentinel |
| Retry không backoff khi queue đầy | Retry storm, tải tăng thêm | Budget retry và backoff có jitter |
| Coi queue empty là tất cả task xong | Task có thể đang chạy hoặc fail | Theo dõi in-flight và completion |
| Cứ tăng queue capacity để giải quyết overload | Latency/heap tăng, bottleneck còn đó | Giới hạn ingress, tối ưu consumer/downstream |

## 16. Checklist chọn cơ chế và bài tập suy luận

Trước khi đưa pipeline vào sản xuất, hãy trả lời:

1. Queue cần FIFO, ưu tiên, delay hay rendezvous? Có dùng đúng implementation không?
2. Capacity được **giới hạn hữu hạn** ở đâu? Nếu không, hệ thống có ngân sách heap và cơ chế upstream admission riêng chưa?
3. Producer sẽ **chờ, chờ có hạn, bị từ chối, drop hoặc lưu bền vững** khi queue đầy?
4. Consumer xử lý nhanh hơn tốc độ nhập **trung bình dài hạn** không? Có đo thực tế không?
5. Đã giới hạn số công việc đang xử lý, connection và request downstream riêng chưa?
6. Đã có timeout, retry budget, backoff và idempotency phù hợp cho work item chưa?
7. Có đo queue wait/processing/end-to-end latency, rejection và memory/GC không?
8. Shutdown có thể dừng producer, drain/discard, dừng consumer và thông báo phần việc chưa xong một cách có kiểm soát không?
9. Khi một consumer chết, ai phát hiện? Có cơ chế retry, quarantine hoặc dead-letter nếu nghiệp vụ cần không?
10. Nếu backlog tăng 50 task/s trong 60 giây, queue sẽ thêm bao nhiêu mục; hệ thống sẽ phản ứng ở thời điểm nào?

**Ghi nhớ:** **Concurrent queue** cung cấp thao tác thread-safe và thường không chặn; **BlockingQueue** cung cấp cơ chế chờ khi trống/đầy tùy implementation và capacity; **backpressure** là việc **điều chỉnh lượng công việc được nhận** khi downstream không theo kịp. Trong Java, hãy kết hợp **bounded queue**, `offer/put/take` đúng ngữ nghĩa, deadline, rejection policy, giới hạn in-flight và quan sát end-to-end thay vì kỳ vọng một queue giải quyết mọi vấn đề.

## 17. Tài liệu API chuẩn Java

- [Java SE 17 – Queue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Queue.html)
- [Java SE 17 – BlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/BlockingQueue.html)
- [Java SE 17 – ConcurrentLinkedQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html)
- [Java SE 17 – ThreadPoolExecutor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-012">Chọn collection theo read/write ratio và ordering</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn Concurrent Collection theo read/write ratio và yêu cầu ordering trong Java

## 1. Tại sao một collection thread-safe chưa chắc là lựa chọn đúng?

Java có nhiều collection hỗ trợ truy cập đồng thời: `ConcurrentHashMap`, `ConcurrentSkipListMap`, `CopyOnWriteArrayList`, `ConcurrentLinkedQueue`, `LinkedBlockingQueue`, `ArrayBlockingQueue`, `PriorityBlockingQueue`, các synchronized wrappers và nhiều cấu trúc khác. Chúng đều giải quyết một phần của bài toán chia sẻ dữ liệu giữa thread, nhưng **không có collection nào tối ưu cho mọi mô hình đọc/ghi và mọi loại thứ tự**.

Chẳng hạn:
- Một danh sách cấu hình được đọc hàng triệu lần và cập nhật vài lần/ngày phù hợp với cơ chế snapshot, dù mỗi lần sửa tốn sao chép.
- Một map đếm sự kiện cập nhật liên tục không nên dùng copy-on-write list hoặc khóa toàn cục một cách máy móc.
- Một tác vụ xử lý theo **FIFO** không thể tùy tiện thay bằng priority queue: hợp đồng dequeue đã đổi.
- Một map phải duyệt key theo **thứ tự tăng dần** khác với map chỉ cần lookup nhanh theo key.
- Một queue dùng để điều tiết producer khi consumer chậm cần **capacity/backpressure**, điều mà queue không giới hạn không thể tự cung cấp.

**Ý chính:** quyết định chọn collection phải dựa trên **loại thao tác đọc/ghi, số thread cạnh tranh, chi phí phần tử, yêu cầu ordering, iterator consistency, boundedness/backpressure và invariant nghiệp vụ**, không chỉ tỉ lệ “80% đọc, 20% ghi”.

Bài này thuộc **Concurrent Collections** của **Java Advance - concurrency** và tập trung vào **“Chọn collection theo read/write ratio và ordering”** trong Java SE 17+. Không sử dụng framework hay API riêng của Spring vì phần concurrency của Spring có chương riêng.

Sau bài, bạn có thể:
1. Phân loại workload thành read-mostly, write-heavy, mixed, queue/stream và sorted/range query.
2. Phân biệt **insertion order**, **key/comparator order**, **FIFO**, **priority order**, **encounter order**, **fairness** và **thứ tự toàn cục dưới concurrency**.
3. Chọn giữa `ConcurrentHashMap` và `ConcurrentSkipListMap`; giữa `CopyOnWriteArrayList` và list dùng đồng bộ; giữa các queue nonblocking, blocking, bounded và priority.
4. Hiểu **snapshot, weakly consistent và fail-fast iteration**, tránh tin iterator của concurrent collection là một transaction/snapshot nhất quán tuyệt đối.
5. Nhận diện atomicity của **một phương thức**, nguy cơ **check-then-act**, và thao tác nhiều collection/key chưa atomic.
6. Thiết kế thử nghiệm benchmark theo read/write ratio thực và kiểm chứng correctness, thứ tự và backpressure.
7. Chạy các chương trình Java 17 đầy đủ, với đầu ra tất định khi đã kiểm soát concurrency.

## 2. Read/write ratio phải được đo theo loại thao tác

Một tỉ lệ như `read:write = 95:5` chỉ có ý nghĩa nếu xác định rõ mẫu số và loại đọc/ghi. Ví dụ:

- `get(key)` là một thao tác đọc ngắn, khác với `forEach` quét cả map có một triệu entries.
- `contains` hoặc `size` có chi phí và ý nghĩa khác với `take()` trên queue.
- `add(element)` vào copy-on-write list thường tạo một bản sao backing array, khác `put` vào ConcurrentHashMap.
- `computeIfAbsent` là một thao tác có khả năng cập nhật atomically một mapping, không đơn giản giống một lần read rồi một lần write.
- `removeIf` là thao tác bulk nhiều phần tử, không có nghĩa “một write” về chi phí.
- Đọc trên list theo chỉ số là khác với iterate snapshot; viết ở đầu list khác viết ở cuối list.

### Ba workload cơ bản

**Read-mostly:** nhiều `get/contains/iteration`, ít `put/add/remove`. Ví dụ danh sách listeners đăng ký lúc startup, cấu hình được publish thỉnh thoảng, metadata hiếm thay đổi.

**Mixed:** nhiều readers và writers hoạt động đồng thời, thường get/put/remove/compute. Ví dụ registry session, cache key-value, trạng thái user đang online.

**Write-heavy:** nhiều threads liên tục update, enqueue/dequeue hoặc increment. Ví dụ event counters, work queues, state transitions, logging intake.

**Không cố gắng đo read/write ratio khi chưa biết read là snapshot hay point lookup và write là append, replace hay remove**. Một workload 99% đọc nhưng mỗi lần đọc phải giữ lock và đi qua toàn bộ dữ liệu vẫn có contention/latency đáng kể.

### Tỉ lệ chưa đủ; cần thêm các biến

Một phiếu workload nên ghi ít nhất:

| Câu hỏi | Ví dụ | Vì sao quyết định collection |
| --- | --- | --- |
| Đọc theo cách nào? | `get(key)`, `get(index)`, `forEach` | Chi phí truy cập và iterator semantics khác nhau |
| Ghi theo cách nào? | `add`, `remove`, `merge`, `compute` | Copy, contention và atomicity khác nhau |
| Read/write ratio tại peak? | 99:1, 70:30, 20:80 | Bình quân ngày có thể che burst write |
| Số thread đồng thời? | 2, 16, 200 | Mức độ contention và lock behavior |
| Kích thước trung bình/cao nhất? | 100, 100.000, 10 triệu | Copy-on-write và memory footprint |
| Phần tử nặng bao nhiêu? | Ref nhỏ vs object graph lớn | Chi phí allocation, GC, cache |
| Có ordering gì? | FIFO, sorted key, priority, insertion | Loại collection và comparator |
| Cần snapshot nhất quán không? | Tất cả phần tử cùng một thời điểm | Weakly consistent iterator có thể không đủ |
| Có cần backpressure không? | Capacity 1.000, producers phải chờ | Bounded BlockingQueue |
| Có deadline/hủy không? | `poll(timeout)`, interruption | Chọn API blocking/timed phù hợp |
| Có invariant đa key? | Chuyển tiền giữa hai tài khoản | Atomic per-key không đủ |
| Thứ tự giữa nhiều producers? | Sequence toàn cục/ordering per-source | Queue FIFO không định nghĩa mọi race |

Không có “ngưỡng 90% reads” được JDK bảo đảm cho `CopyOnWriteArrayList`. Phải tính **write cost ~ kích thước collection** và **read pattern**, rồi đo trên workload thật.

## 3. Nhiều nghĩa của ordering: dễ chọn sai nhất

Từ “có thứ tự” trong Java concurrency có thể chỉ các hợp đồng hoàn toàn khác nhau.

### 3.1. Insertion order

Trong một collection tuần tự như `LinkedHashMap` hoặc `LinkedHashSet`, iteration có thể giữ thứ tự insertion (tùy mode và contract). Nhưng chúng **không tự thread-safe**. Dùng `LinkedHashMap` trực tiếp từ nhiều threads đọc/ghi là không an toàn.

Nếu cần **insertion-order snapshot**, có thể chụp dữ liệu trong một critical section hoặc publish một immutable snapshot sau mỗi batch cập nhật, tùy write frequency. Không nên thay `ConcurrentHashMap` bằng `LinkedHashMap` rồi cho nhiều writers chạy song song mà không đồng bộ.

### 3.2. Sorted order theo key/comparator

`ConcurrentSkipListMap<K,V>` duy trì key trong **thứ tự sắp xếp** theo natural ordering hoặc comparator, hỗ trợ `firstKey`, `lastKey`, `floorKey`, `ceilingKey`, `subMap`, `headMap`, `tailMap`.

**Sorted by key không phải insertion order.** Insert key 30 rồi 10 rồi 20, iterator theo khóa tăng dần vẫn đi 10, 20, 30. Nếu comparator không phân biệt được hai key (`compare(a,b)==0`), chúng có thể được xem là cùng một key theo ordering map; cần comparator nhất quán với domain identity.

`ConcurrentSkipListSet` cho yêu cầu tập hợp sorted keys tương tự. Đổi lại, ordered/range operations thường có chi phí và cấu trúc khác hash table; không mặc định nhanh hơn `ConcurrentHashMap` cho point lookup.

### 3.3. FIFO — first in, first out

`ConcurrentLinkedQueue`, `LinkedBlockingQueue` và `ArrayBlockingQueue` có **hợp đồng FIFO** cho những phần tử đã enqueue theo thứ tự xác định.

Nhưng khi nhiều producer gọi `offer()` đồng thời, **thứ tự tuyệt đối giữa hai lời gọi chồng lấn** không tự được xác định bởi thứ tự log, timestamp do thread riêng lấy, hoặc thứ tự worker được OS lập lịch. Các thao tác thành công có một thứ tự hiệu lực theo giao thức queue; nếu business đòi “thứ tự request nhận tại gateway”, phải định nghĩa sequence ở **đúng điểm giao nhận** và áp dụng xử lý phù hợp.

FIFO của hàng đợi cũng **không bảo đảm thứ tự hoàn tất** khi nhiều consumer xử lý song song:

~~~text
Queue lấy ra lần lượt: A, rồi B.
Consumer 1 xử lý A mất 500ms.
Consumer 2 xử lý B mất 10ms.
Kết quả B có thể được công bố trước kết quả A.
~~~

Muốn output hoàn tất theo thứ tự dequeue, cần một tầng reorder/sequence/commit riêng, hoặc xử lý tuần tự.

### 3.4. Priority order

`PriorityBlockingQueue` sắp xếp phần tử theo **priority/natural ordering hoặc comparator**, không theo FIFO nói chung. Phần tử có priority cao hơn theo comparator lấy ra trước; trong Java `PriorityQueue` semantics thường phần tử nhỏ nhất theo comparator đứng đầu.

**Không có bounded capacity thực sự do constructor cung cấp trong `PriorityBlockingQueue`** theo kiểu `ArrayBlockingQueue`. Nó được mô tả là unbounded về cấu trúc (tất nhiên bị giới hạn bởi tài nguyên bộ nhớ). `put()` không tạo backpressure như bounded queue.

Với priority bằng nhau, `PriorityBlockingQueue` **không bảo đảm thứ tự ổn định**. Nếu cần tie-breaker, đưa sequence vào comparator. Dù vậy, khi workers lấy item song song, **thứ tự hoàn thành** vẫn có thể khác thứ tự priority.

### 3.5. Fairness của thread chờ khác thứ tự item

`ArrayBlockingQueue(int capacity, boolean fair)` hỗ trợ fairness policy cho **thứ tự cấp quyền truy cập cho các thread producer/consumer đang chờ**. `fair=true` không có nghĩa:
- Queue trở thành priority queue.
- Tác vụ được xử lý xong theo thứ tự enqueue trong hệ thống nhiều consumer.
- Mọi producer có thể chạy đồng thời mà tạo ra một timestamp ordering ngoài queue.

Fairness thường đánh đổi throughput để giảm biến động/starvation của những thread bị chặn; cần cân nhắc latency và contention thực tế.

### 3.6. Encounter order / snapshot order

`CopyOnWriteArrayList` giữ thứ tự các phần tử như List trong **snapshot backing array** tại lúc tạo iterator; iterator không phản ánh write sau đó. Trong khi đó `ConcurrentHashMap` không có meaningful key iteration order. `ConcurrentSkipListMap` iterator có sorted key order nhưng có thể weakly consistent khi writer đồng thời thay đổi map.

**Kết luận:** trước khi hỏi “collection nào ordered?”, phải trả lời **ordered theo cái gì, tại thời điểm nào, với concurrent writers ra sao và output cần giữ thứ tự nào**.

## 4. Ma trận chọn map: ConcurrentHashMap hay ConcurrentSkipListMap?

| Tiêu chí | `ConcurrentHashMap<K,V>` | `ConcurrentSkipListMap<K,V>` |
| --- | --- | --- |
| Thread-safe truy cập concurrent | Có | Có |
| Key iteration order | **Không bảo đảm** | Theo comparator/natural order tăng dần |
| Point lookup key | Hash-based; thường phù hợp get/put/compute nhanh | Sorted structure; hỗ trợ range/navigation |
| Range `subMap/headMap` | Không phải API cốt lõi | Có |
| `firstKey/ceilingKey` | Không hỗ trợ như NavigableMap | Có |
| Iterators với writes đồng thời | Weakly consistent | Weakly consistent, nhưng theo key ordering của view |
| Global atomic snapshot | Không; aggregate reads có thể thấy trạng thái đang đổi | Không; nhiều thao tác bulk không atomic |
| `null` keys/values | Không hỗ trợ | Không hỗ trợ |
| Multi-key transaction | Không có sẵn | Không có sẵn |
| Common use | Registry, counters, cache lookup | Leaderboard key-sorted, time index, range query |

**Ưu tiên `ConcurrentHashMap` khi:** mục tiêu chính là map lookup/update theo khóa, không yêu cầu thứ tự duyệt; dùng `putIfAbsent`, `compute`, `merge`, conditional `replace` theo key để tránh check-then-act.

**Ưu tiên `ConcurrentSkipListMap` khi:** yêu cầu nghiệp vụ **đọc theo thứ tự key hoặc lấy đoạn khóa** là phần cốt lõi; bạn chấp nhận overhead của cấu trúc sorted.

### Những điều cả hai đều KHÔNG cung cấp

- Không tự tạo một transaction atomic giữa **hai key khác nhau**.
- Không bảo đảm `size()` rồi `get()` phản ánh cùng một snapshot khi writers chạy.
- Không biến mọi vòng for-each thành **snapshot tức thời** của toàn map.
- Không tự bảo đảm các object mutable được lưu trong value thread-safe.
- Không cho phép map key có comparator/hashCode/equals không ổn định do mutable fields bị thay đổi.

### Hash order ngẫu nhiên quan sát được không phải contract

Trong một lần chạy, bạn có thể thấy iterator của `ConcurrentHashMap` xuất key trùng thứ tự insert hoặc như tăng dần. Đó chỉ là hiện tượng tại dữ liệu/hash state ấy, **không phải API guarantee**. Thay JDK, resize, key distribution hoặc interleaving là có thể khác.

Khi chỉ cần in báo cáo **theo thứ tự key tại một thời điểm sau khi writers đã dừng**, có thể copy sang `TreeMap` để sắp xếp. Nếu writers vẫn còn chạy, việc copy từ `ConcurrentHashMap` không tự là một **atomic snapshot**.

## 5. Ví dụ 1 — ConcurrentHashMap write-heavy, đọc kết quả sau join

Bài toán: bốn thread cập nhật các bộ đếm `apple` và `banana`, mỗi thread thêm 1.000 lần vào từng key. Mẫu an toàn dùng `merge`, thực hiện read-modify-write **atomically per key**.

Tạo file **`ConcurrentMapRatioDemo.java`**:

~~~java
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ConcurrentMapRatioDemo {
    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, Integer> counts =
                new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(4);

        try {
            Future<?>[] jobs = new Future<?>[4];
            for (int w = 0; w < jobs.length; w++) {
                jobs[w] = pool.submit(() -> {
                    for (int i = 0; i < 1_000; i++) {
                        counts.merge("apple", 1, Integer::sum);
                        counts.merge("banana", 1, Integer::sum);
                    }
                });
            }

            for (Future<?> job : jobs) {
                job.get();
            }

            Map<String, Integer> sortedAfterCompletion =
                    new TreeMap<>(counts);

            System.out.println("apple=" + counts.get("apple"));
            System.out.println("banana=" + counts.get("banana"));
            System.out.println("sorted=" + sortedAfterCompletion);
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy với JDK 17+:

~~~bash
javac ConcurrentMapRatioDemo.java
java ConcurrentMapRatioDemo
~~~

Stdout khi không có exception/interruption bên ngoài:

~~~text
apple=4000
banana=4000
sorted={apple=4000, banana=4000}
~~~

**Giải thích:**
- `merge("apple",1,Integer::sum)` là phép cập nhật per-key theo contract `ConcurrentHashMap`, tránh mẫu lỗi `get` rồi `put` riêng rẽ.
- Bốn workers, mỗi worker 1.000 lần, nên mỗi key có giá trị 4.000.
- Main chờ đủ `Future.get()` trước khi copy map; khi ấy **không còn writer của demo đang hoạt động**, nên snapshot bản copy ổn định.
- `TreeMap` **chỉ dùng để xuất kết quả sorted** sau khi workers dừng; bản sao không thay map gốc thành ordered.
- Không in trực tiếp `counts.toString()` và tuyên bố một thứ tự, vì `ConcurrentHashMap` không có ordering contract.

### Nếu workload là counter thực sự

Nếu nhiều thread cùng cộng vào **một key nóng**, cập nhật `Integer` bằng `merge` có thể bị contention. `ConcurrentHashMap<String,LongAdder>` với `computeIfAbsent(k, x -> new LongAdder()).increment()` là pattern đếm tần suất phổ biến. Nhưng việc đọc `LongAdder.sum()` trong khi writes đang diễn ra **không phải atomic snapshot** toàn bộ updates; và nếu bạn remove/replace entry `LongAdder`, cần định nghĩa ownership/lifecycle để không làm mất increment.

Không áp dụng LongAdder cho bài toán cần transaction đa key hoặc đòi số đếm tại mọi thời điểm phải chính xác linearizable trên nhiều biến.

## 6. Ví dụ 2 — Sorted key và range query với ConcurrentSkipListMap

Cần lưu các key số nguyên và duyệt theo thứ tự tăng dần, bất kể thứ tự insert. `ConcurrentSkipListMap` giữ ordering theo key.

Tạo file **`SortedConcurrentMapDemo.java`**:

~~~java
import java.util.concurrent.ConcurrentSkipListMap;

public class SortedConcurrentMapDemo {
    public static void main(String[] args) {
        ConcurrentSkipListMap<Integer, String> map =
                new ConcurrentSkipListMap<>();

        map.put(30, "thirty");
        map.put(10, "ten");
        map.put(20, "twenty");
        map.put(40, "forty");

        System.out.println("keys=" + map.navigableKeySet());
        System.out.println("first=" + map.firstKey());
        System.out.println("ceiling25=" + map.ceilingKey(25));
        System.out.println("range=" + map.subMap(20, true, 40, false));
    }
}
~~~

Chạy:

~~~bash
javac SortedConcurrentMapDemo.java
java SortedConcurrentMapDemo
~~~

Stdout (không có concurrent writer trong demo):

~~~text
keys=[10, 20, 30, 40]
first=10
ceiling25=30
range={20=twenty, 30=thirty}
~~~

Khi nhiều writers đồng thời thay đổi map:
- Navigation/range views vẫn tuân thủ comparator ordering cho các phần tử chúng quan sát.
- Iterator là **weakly consistent**, có thể thấy hoặc không thấy một số cập nhật đồng thời.
- `subMap` tạo **view theo range**, không phải một bản sao immutable hoặc một snapshot nguyên tử của cả range.
- Nếu business yêu cầu một báo cáo range tại **cùng một thời điểm logic**, cần một giao thức snapshot/locking/versioning ngoài collection.

### Comparator quyết định “hai key có giống nhau không”

Ví dụ dùng comparator so sánh chuỗi không phân biệt hoa/thường:

~~~java
ConcurrentSkipListMap<String, Integer> m =
        new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
m.put("ABC", 1);
m.put("abc", 2);
~~~

Đây là snippet; do comparator coi hai chuỗi bằng nhau về ordering, hai `put` tác động tới **cùng một mapping theo comparator**, không tạo hai key độc lập theo cách `String.equals`. Comparator cần được chọn từ **định nghĩa định danh dữ liệu**, không chỉ từ tiêu chí hiển thị.

## 7. Danh sách read-mostly: CopyOnWriteArrayList

`CopyOnWriteArrayList<E>` có cách đánh đổi khác biệt:
- Các phép cập nhật như `add` hoặc `set` thường tạo **bản sao backing array** rồi publish phiên bản mới.
- Reads/iteration có thể truy cập một backing array ổn định mà không cần lock của writer cho từng phần tử.
- Iterator theo **snapshot** tại lúc tạo. Các thêm/xóa/sửa sau đó **không hiện trong iterator cũ**.
- Iterator không hỗ trợ `remove/set/add` qua chính iterator, các thao tác đó ném `UnsupportedOperationException`.
- List giữ **thứ tự theo index của phiên bản snapshot**, không phải sorted key.
- `null` phần tử được phép theo hợp đồng class, khác nhiều concurrent maps/queues.

**Tốt khi:** dữ liệu tương đối nhỏ, reads/iterations rất nhiều, writers hiếm, cần iterator không bị cản trở bởi cập nhật đồng thời. Ví dụ danh sách listeners, callbacks, feature flags được publish từng đợt.

**Không tốt khi:** collection lớn và phải liên tục add/remove/set, vì mỗi update có chi phí copy gần theo kích thước array và áp lực allocation/GC. Một write burst làm ratio trung bình read-heavy không còn phản ánh chi phí đỉnh.

### Snapshot có bất biến gì?

Snapshot iterator cố định **tập reference và thứ tự phần tử** của backing array ở thời điểm tạo. Nó **không deep-copy object được tham chiếu**. Nếu một element là mutable bean và thread khác sửa bean ấy, snapshot list **không đóng băng trạng thái bên trong bean**.

Đây là khác biệt giữa:
- **Snapshot của cấu trúc List**.
- **Immutable snapshot của toàn bộ object graph**.

Nếu cần snapshot bất biến thật, hãy dùng **immutable elements** (ví dụ records với field immutable) hoặc sao chép sâu theo yêu cầu nghiệp vụ.

## 8. Ví dụ 3 — Snapshot iterator và write tới CopyOnWriteArrayList

Tạo file **`CopyOnWriteReadMostlyDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CopyOnWriteReadMostlyDemo {
    public static void main(String[] args) {
        CopyOnWriteArrayList<String> listeners =
                new CopyOnWriteArrayList<>();

        listeners.add("alpha");
        listeners.add("beta");

        Iterator<String> snapshot = listeners.iterator();
        listeners.add("gamma");
        listeners.remove("alpha");

        List<String> oldView = new ArrayList<>();
        snapshot.forEachRemaining(oldView::add);

        System.out.println("snapshot=" + oldView);
        System.out.println("current=" + listeners);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac CopyOnWriteReadMostlyDemo.java
java CopyOnWriteReadMostlyDemo
~~~

Stdout:

~~~text
snapshot=[alpha, beta]
current=[beta, gamma]
~~~

**Vì sao?**
1. Iterator `snapshot` được tạo khi list chứa `alpha,beta`.
2. `add("gamma")` và `remove("alpha")` thay đổi phiên bản backing array hiện hành.
3. Iterator cũ vẫn đi qua các reference của phiên bản cũ.
4. Khi in chính `listeners`, ta thấy nội dung hiện tại `beta,gamma`.

Ví dụ thực thi tuần tự để tách biệt semantics snapshot khỏi scheduler. Khi writers chạy đồng thời, iterator cũ vẫn dùng snapshot của cấu trúc list, nhưng như đã nói **không đóng băng field mutable của element**.

### Iterator.remove() không hỗ trợ

~~~java
Iterator<String> iterator = listeners.iterator();
iterator.remove(); // UnsupportedOperationException
~~~

Đây là snippet minh họa. Nếu cần xóa, sử dụng phương thức collection `listeners.remove(x)` hoặc thao tác update thích hợp, hiểu rằng chúng có thể tạo bản sao backing array mới.

### Read-heavy không có nghĩa “CopyOnWrite cho mọi map”

`CopyOnWriteArrayList` là **List**, không phải bản thay thế cho `ConcurrentHashMap`:
- Lookup một phần tử bằng `contains` có thể quét tuyến tính.
- Không có `get(key)` như map.
- Duplicates, vị trí list và callback-order có semantics riêng.
- Mỗi lần write copy backing array là đánh đổi quan trọng.

Nếu bạn có một map lớn read-mostly, cân nhắc **immutable map snapshot publish qua `volatile`/`AtomicReference`**, hoặc `ConcurrentHashMap` nếu vẫn có nhiều incremental updates, tùy chi phí snapshot và yêu cầu atomic read.

## 9. List có nhiều writes: synchronizedList, locking hay kiến trúc khác?

Nếu các write vào List diễn ra thường xuyên, sao chép toàn bộ array mỗi lần có thể quá đắt. Một phương án là:

~~~java
List<String> list =
        Collections.synchronizedList(new ArrayList<>());
~~~

Snippet cần `import java.util.*;`. Wrapper dùng synchronization bảo vệ **từng lời gọi method** qua wrapper, nhưng không tự biến **nhiều lời gọi** thành một transaction. Ví dụ:

~~~java
if (!list.contains("x")) {
    list.add("x");
}
~~~

Hai thread có thể cùng thấy chưa chứa x rồi cùng thêm, tạo duplicate. Nếu cần atomic check-then-add phải giữ **cùng monitor** của list quanh toàn bộ sequence:

~~~java
synchronized (list) {
    if (!list.contains("x")) {
        list.add("x");
    }
}
~~~

**Iterator của synchronizedList yêu cầu đồng bộ thủ công khi đang traverse**:

~~~java
synchronized (list) {
    for (String value : list) {
        consume(value);
    }
}
~~~

Đây là snippet với `consume` là method đã tồn tại. Nếu dùng collection view/subList, phải xem đúng tài liệu wrapper và monitor cần dùng. Nếu callback `consume` chậm, việc giữ monitor trong suốt iteration có thể block writers lâu; có thể **copy snapshot dưới lock** rồi iterate ngoài lock khi phù hợp.

### Khi nào thay đổi kiến trúc thay vì khóa List?

- Nếu liên tục append và drain, dùng **queue** thay List với `add` + `remove(0)`.
- Nếu cần lookup theo ID, dùng **map** thay List rồi scan.
- Nếu cần theo key order, dùng **sorted concurrent map/set**.
- Nếu cần nhiều reader đọc một phiên bản ổn định trong khi writers build batch mới, dùng **immutable snapshot + atomic publication**.
- Nếu cần index-stable mutation đồng thời, cần khóa/ownership rõ; không có một concurrent list tổng quát thay thế mọi `ArrayList` với chi phí không đáng kể.

**Ordering của List** còn cần nói rõ: index order của List không phải completion order của các thread. Nếu nhiều writers cùng `add`, thứ tự cuối là thứ tự mà cập nhật được chấp nhận trong cơ chế collection, không phải thứ tự timestamp logic tự phát của các thread.

## 10. Weakly consistent iteration khác snapshot và fail-fast

Ba kiểu hành vi thường gặp:

| Kiểu iterator | Đặc tính | Ví dụ |
| --- | --- | --- |
| **Fail-fast (best effort)** | Có thể ném `ConcurrentModificationException` khi phát hiện structural modification trái phép; không phải cơ chế safety guarantee | `ArrayList` iterator khi bị sửa không đúng quy tắc |
| **Weakly consistent** | Không ném CME chỉ vì cập nhật đồng thời; có thể phản ánh một số thay đổi trong lúc duyệt, không tạo snapshot toàn cục | `ConcurrentHashMap`, `ConcurrentLinkedQueue`, `ConcurrentSkipListMap` |
| **Snapshot** | Duyệt backing snapshot lúc tạo; không thấy thay đổi cấu trúc sau đó | `CopyOnWriteArrayList` |

### Một hiểu lầm nghiêm trọng

**“Không ném ConcurrentModificationException” không có nghĩa iterator đã nhìn được tất cả records đúng tại một thời điểm.** Với weakly consistent iterator, dữ liệu có thể được quan sát theo tiến trình cập nhật; nó hợp lý cho monitoring, scanning hoặc best-effort operations, nhưng không tự phù hợp cho báo cáo phải phản ánh cùng một thời điểm logic.

**“Fail-fast” cũng không phải một biện pháp đồng bộ.** `ConcurrentModificationException` là best-effort bug detector, không bảo đảm được ném trong mọi race. Không viết test correctness dựa vào việc “chắc chắn sẽ ném CME”.

### Snapshot của CopyOnWriteArrayList không phải snapshot của hai collection

Nếu bạn có một `CopyOnWriteArrayList` và một `ConcurrentHashMap`, đọc iterator list rồi đọc map không tự tạo **multi-collection atomic snapshot**. Nếu invariant yêu cầu hai nguồn cùng một epoch/version, cần publish một immutable aggregate state dưới một reference, dùng lock hoặc thiết kế snapshot/transaction riêng.

## 11. Các queue: FIFO, priority, blocking và boundedness

| Collection | Order khi lấy | Blocking API | Bounded? | Gợi ý |
| --- | --- | --- | --- | --- |
| `ConcurrentLinkedQueue<E>` | FIFO | `offer/poll` nonblocking; không có `take` | Unbounded về cấu trúc | Throughput event handoff với kiểm soát tải bên ngoài |
| `LinkedBlockingQueue<E>` | FIFO | `put/take/offer(timeout)/poll(timeout)` | Có capacity tùy chọn; mặc định thực tế rất lớn | Producer-consumer cần block khi trống |
| `ArrayBlockingQueue<E>` | FIFO | `put/take` và timed methods | **Capacity cố định** | Backpressure và giới hạn bộ nhớ rõ |
| `LinkedBlockingDeque<E>` | Hai đầu; FIFO khi dùng `putLast/takeFirst` | Blocking ở hai đầu | Capacity tùy chọn | Work deque, đầu/cuối khác semantics |
| `PriorityBlockingQueue<E>` | Comparator/natural priority | `take` khi trống | **Không có bounded capacity thực tế** | Scheduler theo priority, không FIFO |
| `SynchronousQueue<E>` | Handoff trực tiếp, không buffer | `put/take` đợi đối tác | **Capacity 0** | Rendezvous giữa producer-consumer |
| `LinkedTransferQueue<E>` | FIFO với transfer semantics | `transfer` có thể đợi consumer nhận | Unbounded về cấu trúc | Phối hợp direct transfer khi phù hợp |

Ở đây “unbounded” không có nghĩa vô hạn bộ nhớ: chương trình vẫn có thể bị `OutOfMemoryError` khi producers nhanh hơn consumers và không có backpressure.

### Queue có FIFO ≠ các task xử lý xong FIFO

Nếu một queue trả item A trước B nhưng có hai consumers, B có thể hoàn tất trước A. Đây là câu hỏi về **processing/commit order**, không phải contract dequeue của queue.

### Bounded queue là cơ chế bảo vệ tài nguyên

Với `ArrayBlockingQueue<>(capacity)`:
- `put` chờ khi full.
- `offer` trả `false` khi full.
- `offer(timeout)` đợi có giới hạn.
- `take` chờ khi empty.
- `poll` trả `null` khi empty.
- `poll(timeout)` chờ có hạn.

Đây là các lựa chọn backpressure/timeout theo nghiệp vụ. Khi producer vượt khả năng consumer, một bounded queue ngăn tăng backlog không kiểm soát, nhưng nếu mọi producer đều block trong thread pool nhỏ, bạn phải xét **thread-starvation deadlock và throughput**. Backpressure là một chính sách toàn pipeline, không chỉ một constructor.

### Priority có thể gây starvation

Nếu liên tục có item priority cao, các item priority thấp có thể đợi rất lâu. `PriorityBlockingQueue` không tự bảo đảm **aging/fairness theo tuổi item**. Nếu business cần ngăn starvation, hãy thiết kế comparator/aging hoặc scheduler phù hợp, chú ý comparator nhất quán và tính đồng bộ của priority fields.

## 12. Ví dụ 4 — FIFO, capacity và priority: ba yêu cầu khác nhau

Tạo file **`QueueOrderingDemo.java`**:

~~~java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;

public class QueueOrderingDemo {
    public static void main(String[] args) {
        ArrayBlockingQueue<String> fifo =
                new ArrayBlockingQueue<>(2, true);

        boolean first = fifo.offer("A");
        boolean second = fifo.offer("B");
        boolean third = fifo.offer("C");

        System.out.println("fifoAccepted=" +
                first + "," + second + "," + third);
        System.out.println("fifoPoll1=" + fifo.poll());
        System.out.println("fifoPoll2=" + fifo.poll());

        PriorityBlockingQueue<Integer> priority =
                new PriorityBlockingQueue<>();
        priority.offer(30);
        priority.offer(10);
        priority.offer(20);

        System.out.println("priorityPoll1=" + priority.poll());
        System.out.println("priorityPoll2=" + priority.poll());
        System.out.println("priorityPoll3=" + priority.poll());
    }
}
~~~

Chạy:

~~~bash
javac QueueOrderingDemo.java
java QueueOrderingDemo
~~~

Stdout:

~~~text
fifoAccepted=true,true,false
fifoPoll1=A
fifoPoll2=B
priorityPoll1=10
priorityPoll2=20
priorityPoll3=30
~~~

**Phân tích:**
- FIFO bounded capacity 2: `offer("C")` bị từ chối khi đang full, không block. Đây là backpressure có thể quan sát và xử lý.
- `fair=true` nói về **fairness của các thread chờ truy cập**, không đổi thứ tự A rồi B thành priority order.
- `PriorityBlockingQueue` lấy số nhỏ nhất theo natural comparator trước, dù insert theo thứ tự 30,10,20.
- Ví dụ đơn thread để test contract; khi nhiều producers cạnh tranh, thứ tự enqueue thực tế chịu ảnh hưởng timing, và khi nhiều consumers, thứ tự hoàn tất là vấn đề khác.
- Không dựa vào `PriorityBlockingQueue.iterator()` để xuất một dãy **sorted**; iterator của priority queue không nhất thiết duyệt theo thứ tự priority. Muốn quan sát lấy theo priority, dùng `poll/take` hoặc tạo snapshot rồi sort.

### Các phần tử priority bằng nhau

Giả sử hai item có cùng priority. `PriorityBlockingQueue` không bảo đảm chúng được lấy FIFO theo thời điểm insert. Nếu cần tie-break, đưa một **sequence number** vào item và comparator:

~~~java
record Job(int priority, long sequence, String payload) {}

Comparator<Job> comparator =
        Comparator.comparingInt(Job::priority)
                  .thenComparingLong(Job::sequence);
~~~

Đây là snippet, cần import `java.util.Comparator`. Nếu nhiều producers cần sequence duy nhất, sử dụng `AtomicLong.getAndIncrement()` ở điểm xác định ordering. Tuy nhiên **lúc lấy sequence và lúc enqueue không là một transaction**, nên nếu business yêu cầu thứ tự ingress tuyệt đối, hãy thiết kế chính sách gán sequence/tie-break tại đúng ranh giới tiếp nhận.

## 13. Ví dụ 5 — ConcurrentLinkedQueue và nhiều consumers: claim bằng poll()

Một queue FIFO concurrent cho phép nhiều workers tranh lấy phần tử. Nếu muốn mỗi item được một consumer nhận, dùng **chính thao tác `poll()` để claim**, không dùng check-then-act kiểu `if (!queue.isEmpty()) queue.remove()`.

Tạo file **`ConcurrentQueueClaimDemo.java`**:

~~~java
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentQueueClaimDemo {
    public static void main(String[] args) throws Exception {
        ConcurrentLinkedQueue<Integer> queue =
                new ConcurrentLinkedQueue<>();

        for (int i = 1; i <= 1_000; i++) {
            queue.offer(i);
        }

        AtomicInteger processed = new AtomicInteger();
        AtomicInteger sum = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(4);

        try {
            Future<?>[] workers = new Future<?>[4];
            for (int w = 0; w < workers.length; w++) {
                workers[w] = pool.submit(() -> {
                    Integer value;
                    while ((value = queue.poll()) != null) {
                        processed.incrementAndGet();
                        sum.addAndGet(value);
                    }
                });
            }

            for (Future<?> worker : workers) {
                worker.get();
            }

            System.out.println("processed=" + processed.get());
            System.out.println("sum=" + sum.get());
            System.out.println("remaining=" + queue.size());
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac ConcurrentQueueClaimDemo.java
java ConcurrentQueueClaimDemo
~~~

Stdout:

~~~text
processed=1000
sum=500500
remaining=0
~~~

**Chứng minh kết quả:**
1. Queue được nạp sẵn 1.000 số `1..1000`, không có producer trong giai đoạn consumer chạy.
2. Mỗi `poll()` thành công lấy đúng một item. Không có hai consumer cùng nhận một phần tử từ cùng thao tác dequeue.
3. Các consumer dừng khi `poll()` trả `null`; vì không có producer mới, đó là điều kiện kết thúc hợp lệ của demo.
4. `Future.get()` đợi đủ workers; `AtomicInteger` bảo vệ `processed/sum` khi cập nhật đa thread.
5. Tổng dãy là `1000*1001/2=500500`; queue sau đó rỗng.

**Trong production có producer liên tục:** `poll()==null` chỉ có nghĩa **hiện tại** không có item để lấy, không có nghĩa producer sẽ không gửi thêm. Cần lifecycle như `closed`, poison pill, scheduler/wakeup hoặc một `BlockingQueue.take()` thích hợp. Cũng đừng dùng `queue.size()` của `ConcurrentLinkedQueue` để quyết định chính xác một business invariant giữa nhiều writers: size có thể tốn O(n) và thay đổi trong lúc đếm.

### FIFO không áp đặt thời gian hoàn tất

Ngay cả khi queue cung cấp item theo FIFO, bốn workers có thể xử lý và cập nhật aggregate theo thứ tự bất kỳ. Chỉ kết quả cuối sau `get()` là xác định, không có stdout “worker 1 xử lý 1, worker 2 xử lý 2” nào được bảo đảm.

## 14. Ví dụ 6 — Bounded BlockingQueue và producer-consumer

Để có **backpressure** và chờ dữ liệu không busy-poll, `ArrayBlockingQueue` hoặc `LinkedBlockingQueue` với capacity rõ là lựa chọn phổ biến.

Tạo file **`BoundedPipelineDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class BoundedPipelineDemo {
    public static void main(String[] args) throws Exception {
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> producer = pool.submit(() -> {
                try {
                    queue.put(1);
                    queue.put(2);
                    queue.put(3);
                    queue.put(-1); // Sentinel kết thúc cho một consumer.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            Future<List<Integer>> consumer = pool.submit(() -> {
                List<Integer> received = new ArrayList<>();
                while (true) {
                    int value = queue.take();
                    if (value == -1) {
                        return received;
                    }
                    received.add(value);
                }
            });

            producer.get();
            System.out.println("received=" + consumer.get());
            System.out.println("remaining=" + queue.size());
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
~~~

Chạy:

~~~bash
javac BoundedPipelineDemo.java
java BoundedPipelineDemo
~~~

Stdout khi không có interruption/lỗi bên ngoài:

~~~text
received=[1, 2, 3]
remaining=0
~~~

**Phân tích:**
- Queue capacity 2 khiến producer **có thể phải đợi** nếu consumer chưa lấy phần tử; đây là backpressure thay vì tăng memory backlog không giới hạn.
- Một producer đưa `1,2,3,-1` theo thứ tự, một consumer lấy theo FIFO nên List nhận `[1,2,3]`.
- Sentinel chỉ dành cho ví dụ **một consumer**, và -1 được định nghĩa là giá trị không phải dữ liệu thực.
- Nếu có N consumers và muốn dùng poison pills, cần thiết kế số tín hiệu kết thúc phù hợp, không chỉ một sentinel; hoặc dùng một giao thức `closed`/coordinator riêng.
- Nếu producer bị interrupt trước khi gửi sentinel, consumer có thể chờ mãi; trong production cần timeout/cancellation/shutdown robust.
- Dùng `shutdownNow()` khi tất cả futures đã kết thúc trong demo chỉ là cleanup executor; không nên ngầm dùng nó để cắt ngang task nghiệp vụ còn đang commit.

### Khi nào dùng LinkedBlockingQueue?

`LinkedBlockingQueue` có node-based FIFO và capacity tùy chọn. `new LinkedBlockingQueue<>()` mặc định capacity là `Integer.MAX_VALUE`, vì vậy nếu producers có thể nhanh hơn consumers kéo dài, nên truyền **capacity hữu hạn** để bảo vệ hệ thống.

`ArrayBlockingQueue` dùng một backing array với capacity cố định; dễ kiểm soát memory footprint của các references. Không kết luận ArrayBlockingQueue luôn nhanh hơn LinkedBlockingQueue: workload contention, operation patterns, allocation, GC và fairness đều ảnh hưởng.

## 15. Chọn queue theo yêu cầu thực tế

### 15.1. Nonblocking handoff

Nếu producer và consumer nên hoạt động mà không chờ queue có item, dùng `ConcurrentLinkedQueue.offer/poll`. Caller tự quyết định khi `poll()==null`, ví dụ ngừng batch, đợi signal ở tầng khác hoặc quay lại scheduler. Không dùng vòng:

~~~java
while (queue.isEmpty()) {
    // quay nóng không timeout / không chờ hiệu quả
}
~~~

Đây là anti-pattern khi không có chiến lược backoff/yield hoặc event notification; nó có thể ngốn CPU.

### 15.2. Blocking FIFO có backpressure

Nếu thiếu item thì consumer chờ, queue đầy thì producer phải chờ, dùng `BlockingQueue` có capacity:
- `ArrayBlockingQueue`: fixed bounded array, có tùy chọn fairness.
- `LinkedBlockingQueue(capacity)`: linked FIFO với capacity cấu hình.

Chọn timed `offer/poll` nếu có deadline hoặc không muốn block vô hạn. Khi shutdown, chuyển trạng thái ở đúng protocol và đánh thức các workers đang chờ.

### 15.3. Direct handoff

`SynchronousQueue` không giữ item trong buffer. Producer `put` chờ consumer tương ứng nhận; consumer `take` chờ producer. Đây là mô hình **rendezvous**, không phải queue có backlog. Có thể hữu ích khi muốn handoff trực tiếp và không cho tích tụ item, nhưng phải tránh deadlock do cả hai phía chạy trong pool quá nhỏ.

### 15.4. Priority scheduling

`PriorityBlockingQueue` dùng khi thứ tự ưu tiên là hợp đồng thực sự. Một câu hỏi cần trả lời: nếu item có priority bằng nhau, có cần FIFO hay không? Nếu có, bổ sung sequence/tie-breaker nhất quán. Nếu task priority thấp không được phép starve, cần aging hoặc scheduler cao hơn.

### 15.5. Double-ended queue

`LinkedBlockingDeque`/`ConcurrentLinkedDeque` cho phép thêm/lấy hai đầu, phù hợp work queue/deque khi có semantics head/tail rõ. Nếu chỉ dùng `addLast/pollFirst`, có hành vi FIFO; nếu dùng `push/pop` thì có hành vi stack LIFO. “Deque là ordered” không mô tả đủ contract.

## 16. Atomicity: thread-safe collection không tự tạo transaction

Một collection thread-safe bảo vệ hợp đồng của từng thao tác, nhưng các bước ghép lại có thể không atomic.

### 16.1. Check-then-act trên Map

Sai:

~~~java
if (!map.containsKey(key)) {
    map.put(key, value);
}
~~~

Hai writers có thể cùng thấy key chưa tồn tại rồi cùng put; điều kiện “chỉ một writer được tạo” bị vi phạm. Với concurrent map, dùng `putIfAbsent` nếu chỉ cần đặt mapping khi vắng:

~~~java
V old = map.putIfAbsent(key, value);
~~~

Đây là snippet có kiểu `K/V` và `map` phù hợp. Nếu value cần tính, dùng `computeIfAbsent` với mapping function không sửa chính map một cách đệ quy và tránh thực hiện I/O/side effect lâu khi contract của implementation yêu cầu hàm ngắn.

### 16.2. Update counter theo key

Sai:

~~~java
map.put(key, map.get(key) + 1);
~~~

Từng lời gọi get/put có thể thread-safe nhưng tổ hợp không atomic. Dùng `compute` hoặc `merge` cho mapping một key, hoặc `LongAdder` cho frequency counter khi semantics phù hợp.

### 16.3. Atomic per-key không bằng atomic multi-key

Giả sử chuyển 100 đồng từ tài khoản A sang B lưu trong ConcurrentHashMap:

~~~java
balances.compute("A", (k, v) -> v - 100);
balances.compute("B", (k, v) -> v + 100);
~~~

Hai cập nhật per-key có thể atomic từng cái nhưng có thời điểm observer thấy A đã giảm còn B chưa tăng. Nếu invariant đòi tổng tiền không đổi ở **mọi snapshot nghiệp vụ**, cần một lock/giao thức transaction bảo vệ cả hai key, hoặc publish một immutable aggregate state bằng atomic reference. Không có một ConcurrentHashMap method đơn lẻ tạo transaction đa key cho bạn.

### 16.4. size/isEmpty không thay thế claim

Ở queue concurrent, `isEmpty()` rồi `remove()` có race với consumer khác. Dùng `poll()` để claim một item; với yêu cầu chờ, dùng `take()`. Ở map đồng thời ghi, `size()` và `containsValue()` không luôn phù hợp làm decision-critical status dưới writes đang diễn ra; thường chỉ hữu ích cho monitoring/estimation.

### 16.5. Mutable value vẫn cần protection

`ConcurrentHashMap<String,ArrayList<Event>>` không làm thao tác trên các `ArrayList<Event>` nằm bên trong tự trở nên thread-safe. Collection bảo vệ **map structure**; object nằm ở value có contract riêng. Chọn immutable values, concurrent value type hoặc một giao thức lock/ownership rõ ràng.

## 17. Snapshot nhất quán và publish immutable state

Khi yêu cầu là “rất nhiều readers, writer cập nhật theo batch và mỗi reader cần **một phiên bản dữ liệu nhất quán**”, có thể tốt hơn dùng **immutable snapshot** thay vì collection mutable concurrent trực tiếp.

Pattern:

~~~java
record Config(int version, Map<String, String> options) {}

AtomicReference<Config> current = new AtomicReference<>(
        new Config(1, Map.of("mode", "safe")));

Config before = current.get();
Config next = new Config(2, Map.of("mode", "fast"));
current.set(next);
~~~

Đây là snippet cần imports `java.util.Map` và `java.util.concurrent.atomic.AtomicReference`. `Map.of` tạo immutable map; object `Config` là record giữ reference bất biến trong ví dụ.

- Reader lấy `Config snapshot = current.get()` một lần, rồi dùng **chính object đó** cho mọi field cần nhất quán.
- Writer xây cả trạng thái mới **trước** khi publish bằng `set`/`compareAndSet`.
- Một lần read reference là atomic, phù hợp đọc nhiều/write ít khi cần snapshot coherent nhiều field.
- Nếu nhiều writers cạnh tranh, dùng `compareAndSet`/update loop để không làm lost update; `set` mù có thể ghi đè state của writer khác.
- Không nên dùng mutable map cũ rồi sửa sau khi publish; lúc đó reader vẫn có thể thấy state thay đổi.

**CopyOnWriteArrayList** tự có snapshot của backing array theo iterator, còn immutable aggregate publication giúp tổ chức **nhiều cấu trúc liên quan** cùng một version. Đây là hai lựa chọn khác nhau, tùy invariant.

## 18. Benchmark và read/write ratio: đo cái gì?

Để chọn tốt không thể chỉ nhìn tên “Concurrent”. Hãy đo workload đại diện theo nhiều tỉ lệ và mức contention:
- 99:1, 90:10, 50:50, 10:90 và burst writes, nhưng chỉ dùng chúng như **kịch bản thử**, không là mốc tối ưu mặc định.
- Point lookup, index read, full iteration/range query, add/remove, put/compute/merge.
- Dữ liệu nhỏ/vừa/lớn, kích thước element/value thực tế, số keys nóng.
- 1, 2, 4, 8, ... threads tùy máy và CPU quota.
- Tốc độ producer/consumer, capacity/backpressure và tỉ lệ dropped/blocked operations.
- Ordering correctness: sorted keys, FIFO dequeue, priority tie-break, snapshot epoch và result completion order.
- Allocation/GC, p95/p99 latency, throughput, CPU utilization, lock contention và memory footprint.

### Ma trận thí nghiệm gợi ý

| Workload | Collection đối chiếu | Metric chính |
| --- | --- | --- |
| 99% list iteration, write hiếm | `CopyOnWriteArrayList` vs synchronized immutable snapshot | p99 iteration latency, write copy cost, GC |
| Frequent map get/merge | `ConcurrentHashMap` vs `ConcurrentSkipListMap` (chỉ khi ordering thật sự cần) | Throughput per-key, contention, latency |
| Sorted range query | `ConcurrentSkipListMap` vs sorted snapshot publication | Range latency, freshness, memory |
| FIFO handoff | `ConcurrentLinkedQueue` vs bounded `BlockingQueue` | Consumer wakeup, throughput, CPU spin |
| Bounded producer-consumer | `ArrayBlockingQueue` vs `LinkedBlockingQueue(capacity)` | Queue wait time, backlog, memory |
| Priority tasks | `PriorityBlockingQueue` + policy | Ordering, starvation, latency theo priority |
| Snapshot read của nhiều field | Immutable aggregate `AtomicReference` | Coherent versions, write latency |

### JMH và đo điều kiện hệ thống

Với microbenchmark, nên dùng **JMH**:
- Warm-up JVM/JIT trước khi đo.
- Tránh dead-code elimination bằng cách consume kết quả.
- Tránh đo log/console trong hot path.
- Dùng nhiều iterations/forks, kiểm tra correctness ngoài benchmark.
- Ghi rõ VM, OS, CPU, core/threads, GC và collection sizes.
- Cẩn trọng với benchmark có chung một lock nóng hoặc key nóng gây méo tỉ lệ.

Không trình bày một kết quả “ConcurrentHashMap nhanh hơn SkipListMap 3 lần” khi chưa đo đúng workload. Cũng không lấy một sample `System.nanoTime()` làm kết luận phổ quát.

### Chọn workload theo thời điểm cao điểm, không chỉ trung bình

Một cấu hình có 99% đọc trong cả ngày có thể có đợt rollout cập nhật toàn bộ list trong một giây. Với CopyOnWriteArrayList, mỗi add/remove copy array; một burst viết liên tục vào list lớn có thể tạo allocation spike. Nếu ứng dụng có **batch update**, có thể build một immutable snapshot mới rồi publish một lần thay vì 10.000 lần `add`.

## 19. Hướng dẫn lựa chọn theo tình huống

### Tình huống A: Cache theo key, không cần ordering

**Chọn điểm xuất phát:** `ConcurrentHashMap<K,V>`. Nếu giá trị được compute khi thiếu, dùng `computeIfAbsent` phù hợp và callback ngắn. Nếu cần eviction/TTL/LRU thực sự, concurrent hash map đơn thuần **không tự có cơ chế cache eviction**, cần abstraction cache đúng hợp đồng.

### Tình huống B: Danh sách listeners read-many/write-rare

**Chọn điểm xuất phát:** `CopyOnWriteArrayList<E>`. Iterator nhìn snapshot callback list; một listener đăng ký hoặc xóa giữa lúc dispatch không thay đổi iterator đang chạy. Nếu listener object mutable hoặc callback có side effect, bản thân list không bảo đảm thread-safety cho listener.

### Tình huống C: Registry theo ID có duyệt sorted/range

**Chọn điểm xuất phát:** `ConcurrentSkipListMap<K,V>`. Nếu point lookup chiếm đa số và sorted report rất hiếm, cân nhắc `ConcurrentHashMap` + snapshot sort ở lúc báo cáo (khi chấp nhận được freshness/consistency policy).

### Tình huống D: Producer/consumer FIFO có giới hạn memory

**Chọn điểm xuất phát:** `ArrayBlockingQueue` hoặc `LinkedBlockingQueue(capacity)`. Chọn `put/take` nếu chấp nhận chờ, `offer/poll(timeout)` nếu cần deadline. Đặt policy cho full queue: block, reject, drop, retry hay shed load.

### Tình huống E: Handoff nonblocking với backlog được kiểm soát bên ngoài

**Chọn điểm xuất phát:** `ConcurrentLinkedQueue`. Dùng `poll` để claim item. Nếu backlog có nguy cơ tăng vô hạn mà không có cơ chế điều tiết, đừng chọn unbounded queue chỉ vì throughput tốt ở benchmark nhỏ.

### Tình huống F: Work queue ưu tiên

**Chọn điểm xuất phát:** `PriorityBlockingQueue` khi comparator order là yêu cầu chính. Thiết kế sequence cho tie-breaking và aging chống starvation nếu cần. Vì không bounded theo constructor, phải bổ sung admission control/backpressure ở tầng ngoài.

### Tình huống G: Đọc cả một phiên bản cấu hình nhất quán

**Chọn điểm xuất phát:** immutable aggregate snapshot được publish qua `AtomicReference`/`volatile`, hoặc `CopyOnWriteArrayList` khi chỉ cần versioned list of refs. Nếu snapshot quá lớn và updates thường xuyên, chi phí copy có thể quá đắt.

### Tình huống H: Insertion order phải được giữ cùng multi-step atomic mutation

Không có “ConcurrentLinkedHashMap của JDK 17” trong bộ collection chuẩn tương đương `LinkedHashMap` có sẵn ordering và thread-safety tổng quát. Có thể dùng synchronized wrapper và giữ lock cho toàn bộ sequence, hoặc thiết kế immutable state publication, hoặc chọn một thư viện có contract rõ. **Không mặc định `ConcurrentHashMap` giữ insertion order.**

## 20. Lỗi thường gặp

| Lỗi | Hậu quả | Hướng xử lý |
| --- | --- | --- |
| Chọn collection chỉ vì read/write ratio trung bình | Bỏ qua iteration cost, burst và contention | Đo workload theo operation và peak |
| Dùng `ConcurrentHashMap` rồi dựa vào thứ tự iterator | Output không có ordering contract | `ConcurrentSkipListMap` hoặc sorted snapshot |
| Nhầm sorted key với insertion order | Dữ liệu hiển thị theo key, không theo arrival | Chọn đúng ordering semantics |
| Nhầm FIFO với completion order | Nhiều consumers xử lý xong khác dequeue order | Sequence/reorder/serial commit |
| Nhầm `fair=true` với priority order | Chính sách chờ thread không đổi thứ tự item | Phân biệt fairness với queue comparator |
| Dùng `PriorityBlockingQueue` nhưng cần bounded backpressure | Backlog có thể tăng vượt giới hạn memory | Admission control/bounded design |
| Dùng `CopyOnWriteArrayList` cho write-heavy large list | Copy array và GC tăng mạnh | Chọn lock/batch snapshot/kiến trúc khác |
| Cho rằng snapshot iterator deep-copy elements | Mutable fields vẫn có thể thay đổi | Immutable elements/deep snapshot |
| Dùng `Collections.synchronizedList` rồi iterate không khóa | Contract iteration chưa được bảo vệ | `synchronized(list)` quanh traverse |
| Dùng `containsKey` + `put` để đảm bảo single creator | Check-then-act race | `putIfAbsent/computeIfAbsent` |
| Dùng `get` + `put` để increment | Lost update | `compute/merge/LongAdder` |
| Dùng `size/isEmpty` làm điều kiện claim trong concurrent queue | Race, size có thể tốn O(n) | `poll/take` |
| Dùng `ConcurrentHashMap<String,ArrayList>` và tưởng value an toàn | Value vẫn mutable không thread-safe | Bảo vệ value/immutable structure |
| Dùng weakly consistent iterator làm atomic audit snapshot | Thiếu/mix updates theo epoch | Snapshot/version/transaction |
| Tin fail-fast CME bảo vệ correctness | Exception là best-effort detector | Đồng bộ hoặc concurrent collection đúng |
| Gọi `poll()==null` coi là stream kết thúc khi producer còn chạy | Mất dữ liệu đến muộn | Closed flag/sentinel/wakeup protocol |
| Sử dụng `PriorityBlockingQueue.iterator()` làm sorted traversal | Iterator không hứa thứ tự priority | Drain bằng `poll` hoặc snapshot sort |
| Dùng comparator phụ thuộc mutable priority field | Invariant heap/tree có thể không cập nhật | Immutable priority; remove/reinsert |
| Trộn nhiều key updates và coi atomic per-key là transaction | Observer thấy intermediate states | Lock/immutable aggregate/transaction |
| Đặt queue unbounded cho burst không kiểm soát | Memory pressure/OOM | Capacity + backpressure |
| Coi mọi concurrent collection hỗ trợ `null` | NPE/hợp đồng mơ hồ | Kiểm tra class contract |
| Không quản lý executor và timeout khi demo queue block | Thread treo khi không còn producer | Lifecycle/poison pill/interrupt/timeout |

## 21. Checklist trước khi chốt thiết kế

1. **Định nghĩa read/write ratio:** theo method calls, bytes, thời gian hay business events?
2. **Peak workload:** burst add/remove có phá giả định read-mostly không?
3. **Ordering:** cần key sorted, insertion order, FIFO, priority, hay output completion order?
4. **Consistency:** chấp nhận weakly consistent hay cần snapshot cùng epoch?
5. **Point lookup vs full scan:** số reads rất cao nhưng mỗi read đắt không?
6. **Phần tử mutable:** collection có bảo vệ dữ liệu bên trong value không?
7. **Write atomicity:** chỉ một key hay invariant nhiều key/collection?
8. **Capacity/backpressure:** có thể chấp nhận backlog tăng không?
9. **Blocking policy:** chờ, timeout, drop hay reject khi queue đầy?
10. **Số producers/consumers:** concurrency cao có gây lock contention không?
11. **Priority starvation:** low priority có cơ chế aging không?
12. **Fairness:** cần giảm starvation của thread chờ hơn là tối đa throughput?
13. **Cancel/shutdown:** mọi blocking consumer có đường kết thúc không?
14. **Null semantics:** collection có cho null không? `poll()==null` dùng để báo empty?
15. **Key consistency:** comparator/equals/hashCode có ổn định và đúng domain identity?
16. **Versioning:** cần publish immutable snapshot đồng bộ nhiều field không?
17. **Memory:** allocation/GC/backlog khi write burst?
18. **Benchmark:** có baseline tuần tự và kiểm tra correctness?
19. **Mục tiêu tối ưu:** throughput, p95 latency, memory hay fairness? Không phải lúc nào cùng tối ưu.
20. **Operations contract:** API có cung cấp atomic compound method phù hợp?
21. **Ordered output:** nếu có nhiều consumers, reorder/commit policy nằm ở đâu?
22. **Validation:** đã thử đồng thời writers và readers, edge cases, timeout và shutdown chưa?

## 22. Câu hỏi tự kiểm tra và đáp án

1. `ConcurrentHashMap` có duyệt theo insertion order không? **Không**, không có ordering guarantee.
2. Khi cần `firstKey/ceilingKey/subMap` đồng thời với writes, nên xem class nào? **`ConcurrentSkipListMap`**.
3. Sorted by key có đồng nghĩa FIFO không? **Không**, một bên theo comparator, một bên theo enqueue order.
4. `CopyOnWriteArrayList.iterator()` có thấy item thêm sau lúc iterator được tạo không? **Không**, iterator dùng snapshot backing array.
5. Snapshot iterator có đóng băng field của mutable element không? **Không**, chỉ cấu trúc tham chiếu của list.
6. Một list đọc 99%, nhưng writer cập nhật 1 triệu phần tử bằng các `add` riêng, chắc chắn CopyOnWrite tốt nhất không? **Không**, write copies/GC có thể rất lớn.
7. `Collections.synchronizedList` tự đồng bộ toàn vòng for-each không? **Không**, phải giữ monitor wrapper đúng khi traverse.
8. `ConcurrentLinkedQueue` có `take()` không? **Không**, dùng `poll` và giao thức chờ/lifecycle riêng hoặc chọn BlockingQueue.
9. Queue FIFO có bảo đảm workers xử lý xong FIFO không? **Không**, khi có nhiều consumers.
10. `ArrayBlockingQueue` fair=true có sắp xếp item theo priority không? **Không**, fairness nói về truy cập của thread đang chờ.
11. `PriorityBlockingQueue` có capacity hữu hạn như ArrayBlockingQueue không? **Không**, cần admission control riêng.
12. Hai item có cùng comparator priority, có chắc lấy theo FIFO không? **Không**, tie order không được bảo đảm.
13. `PriorityBlockingQueue.iterator()` có sorted order không? **Không** được hứa như thứ tự `poll`.
14. `ConcurrentSkipListMap.subMap` có tạo snapshot bất biến không? **Không**, đó là một view theo range.
15. Weakly consistent iterator có là atomic snapshot không? **Không**.
16. Không có `ConcurrentModificationException` có chứng minh các updates được đọc cùng epoch không? **Không**.
17. `ConcurrentHashMap.merge` giúp sửa bug nào? **Atomic read-modify-write trên một key**, tránh get+put race.
18. Hai `compute` cập nhật key A và B có tự làm transaction không? **Không**.
19. `ConcurrentHashMap<String,ArrayList>` có tự bảo vệ add vào mỗi ArrayList không? **Không**.
20. Khi nhiều consumer tranh một item, nên claim bằng `isEmpty()` rồi `remove()` hay `poll()`? **`poll()`**.
21. `poll()==null` khi producer đang chạy có nghĩa stream đã kết thúc không? **Không**, chỉ trống tại thời điểm lấy.
22. `ConcurrentLinkedQueue.size()` có luôn O(1) và chính xác dưới writes không? **Không**, có thể duyệt và kết quả thay đổi.
23. Khi cần read snapshot nhiều field nhất quán cùng version, lựa chọn nào? **Publish immutable aggregate qua atomic reference/lock/transaction phù hợp**.
24. ConcurrentSkipListMap có cho `null` keys/values không? **Không**.
25. `CopyOnWriteArrayList` có cho phép `null` elements không? **Có**.
26. Tại sao LinkedBlockingQueue không capacity cấu hình có thể nguy hiểm? **Default rất lớn; backlog có thể làm cạn memory**.
27. Map cần LRU/TTL cache hoàn chỉnh, ConcurrentHashMap có tự eviction không? **Không**.
28. Vì sao nên đo write bursts thay vì chỉ read/write ratio trung bình? **Burst có thể tạo write-copy/lock/backlog spikes**.
29. Với priority bằng nhau và cần ổn định, phải làm gì? **Dùng sequence number làm tie-breaker, thiết kế assignment đúng điểm ordering**.
30. Mục tiêu tối ưu được ưu tiên là gì? **Dựa trên SLO: throughput, p99 latency, memory, fairness và correctness, không chỉ tên collection**.

## 23. Tổng kết

**Không chọn concurrent collection chỉ vì nó “thread-safe”.** Hãy đi từ yêu cầu:
- **Read-mostly List + snapshot iteration:** cân nhắc `CopyOnWriteArrayList` hoặc immutable snapshot publication.
- **Mixed/write-heavy Map không cần order:** `ConcurrentHashMap` với các thao tác atomic per-key.
- **Sorted/range key:** `ConcurrentSkipListMap` hoặc `ConcurrentSkipListSet`.
- **FIFO nonblocking handoff:** `ConcurrentLinkedQueue` nếu có admission control/lifecycle thích hợp.
- **FIFO blocking + capacity/backpressure:** `ArrayBlockingQueue` hoặc `LinkedBlockingQueue(capacity)`.
- **Priority:** `PriorityBlockingQueue` với comparator, tie-breaker và kiểm soát backlog ngoài.
- **Nhiều field cần snapshot cùng epoch:** immutable aggregate + atomic publication hoặc transaction.

Một read/write ratio chỉ là **điểm bắt đầu**. Ordering, atomicity, iteration consistency, boundedness, deadline, cancellation và nguyên tắc ownership mới quyết định đúng sai. Sau khi chọn ứng viên, hãy **kiểm tra hợp đồng Java 17, đo trên workload thực và xác minh invariant nghiệp vụ**.

## Tài liệu tham khảo Java SE 17

- [ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): atomic per-key operations và weakly consistent iteration, không ordering.
- [ConcurrentSkipListMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListMap.html): sorted key views, navigation/range, weakly consistent.
- [ConcurrentSkipListSet](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentSkipListSet.html): sorted set concurrent.
- [CopyOnWriteArrayList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CopyOnWriteArrayList.html): snapshot iterator, read-mostly tradeoff.
- [ConcurrentLinkedQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentLinkedQueue.html): FIFO nonblocking queue, weak consistency và chi phí `size()`.
- [ArrayBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ArrayBlockingQueue.html): bounded FIFO, fairness.
- [LinkedBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/LinkedBlockingQueue.html): FIFO, optional capacity, blocking.
- [PriorityBlockingQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/PriorityBlockingQueue.html): comparator ordering, unbounded và iterator order.
- [SynchronousQueue](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/SynchronousQueue.html): direct handoff.
- [Collections.synchronizedList](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collections.html#synchronizedList(java.util.List)): manual synchronization for iteration.
- [Java Language Specification `17](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): memory model và happens-before.
- [JMH — Java Microbenchmark Harness](https://openjdk.org/projects/code-tools/jmh/): benchmark Java có kiểm soát.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-013">ConcurrentHashMap: chính sách null, computeIfAbsent() và giới hạn hàm ánh xạ</a>

<details>
<summary>Xem chi tiết</summary>

# ConcurrentHashMap: chính sách null, computeIfAbsent() và giới hạn hàm ánh xạ

## 1. Bối cảnh: nhiều thread cùng truy cập một Map

`ConcurrentHashMap<K,V>` là một triển khai của `ConcurrentMap` trong `java.util.concurrent`, phù hợp khi nhiều thread cùng cần đọc và cập nhật ánh xạ key–value. Khác với `HashMap` thông thường, nó thiết kế các thao tác của mình để sử dụng đồng thời, không yêu cầu ứng dụng khóa toàn bộ map quanh mỗi lời gọi đơn lẻ.

Tuy nhiên, **thread-safe không có nghĩa mọi chuỗi thao tác của caller đều nguyên tử**. Hai câu hỏi đặc biệt quan trọng:

1. **Vì sao `ConcurrentHashMap` không cho phép key hoặc value là `null`?**
2. **`computeIfAbsent()` bảo đảm điều gì và tại sao hàm ánh xạ (*mapping function*) bị giới hạn?**

Đây là kiến thức Java concurrency nói chung trên **Java 17+**, không liên quan đến một framework cụ thể.

Ví dụ ban đầu:

```java
ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
String value = map.computeIfAbsent("language", key -> "Java");
```

Đây là **snippet**, cần import `java.util.concurrent.ConcurrentHashMap`. Nếu key chưa tồn tại, hàm ánh xạ được sử dụng để tính giá trị. Map sẽ ghi giá trị không-null theo hợp đồng nguyên tử và trả về giá trị hiện có hoặc vừa tạo.

Điều không được suy ra: bất kỳ đoạn mã nào đặt trong lambda của `computeIfAbsent` cũng an toàn, mọi thao tác trên object được trả về đều thread-safe, hoặc việc khởi tạo giá trị đã tạo ra một transaction cho cả chương trình.

## 2. Chính sách null: cả key lẫn value đều bị cấm

`ConcurrentHashMap` **không cho phép `null` làm key và không cho phép `null` làm value**. Những API cần một key/value hợp lệ thường ném `NullPointerException` nếu được truyền null tại vị trí bị cấm.

Ví dụ:

```java
map.put(null, "value"); // NullPointerException.
map.put("key", null);   // NullPointerException.
map.get(null);          // NullPointerException.
```

Đây là snippet (giả sử `map` đã được khai báo); không chạy nối tiếp trong demo bình thường nếu không xử lý exception. `putIfAbsent`, `replace`, `merge`, `computeIfAbsent` và các API liên quan cũng có quy tắc kiểm tra key, value và function theo hợp đồng của từng phương thức.

### Vì sao thiết kế này có ý nghĩa trong concurrency?

Với map thông thường có thể lưu value null, một lời gọi `get(key) == null` có hai cách hiểu:

- Key **không có** trong map.
- Key có trong map nhưng value của nó là `null`.

Trong môi trường đồng thời, việc gọi thêm `containsKey(key)` **sau** `get(key)` để phân biệt cũng không tạo ra một snapshot nguyên tử: thread khác có thể thay map giữa hai lời gọi.

`ConcurrentHashMap` loại bỏ nhập nhằng này bằng cách **không bao giờ lưu ánh xạ key → null**. Bởi vậy khi `get(key)` trả null với key hợp lệ, tại thời điểm đọc không có mapping value hợp lệ cho key ấy. Nhưng **thread khác vẫn có thể thêm mapping ngay sau đó**: null không bảo đảm trạng thái vắng mặt sẽ tồn tại mãi.

Chính sách null cũng hữu ích trong các bulk operation, nơi giá trị null thường biểu thị “không có kết quả” cho một phép transform/filter theo hợp đồng của API.

### Ví dụ Java 17+ hoàn chỉnh: key/value null và kết quả null

Tạo file `ConcurrentHashMapNullDemo.java`:

```java
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashMapNullDemo {
    public static void main(String[] args) {
        ConcurrentHashMap<String, String> map =
                new ConcurrentHashMap<>();

        try {
            map.put(null, "x");
        } catch (NullPointerException expected) {
            System.out.println("Null key rejected");
        }

        try {
            map.put("x", null);
        } catch (NullPointerException expected) {
            System.out.println("Null value rejected");
        }

        String calculated = map.computeIfAbsent(
                "missing", key -> null);

        System.out.println("Returned null: "
                + (calculated == null));
        System.out.println("Missing key stored: "
                + map.containsKey("missing"));

        ConcurrentHashMap<String, Optional<String>> negativeCache =
                new ConcurrentHashMap<>();
        negativeCache.computeIfAbsent(
                "absent-item", key -> Optional.empty());

        System.out.println("Negative result stored: "
                + negativeCache.containsKey("absent-item"));
    }
}
```

Biên dịch/chạy:

```bash
javac ConcurrentHashMapNullDemo.java
java ConcurrentHashMapNullDemo
```

Đầu ra xác định:

```text
Null key rejected
Null value rejected
Returned null: true
Missing key stored: false
Negative result stored: true
```

**Phân biệt rất quan trọng:**

- `map.put("x", null)` **ném NPE**: đây là yêu cầu ghi value null.
- `computeIfAbsent("missing", key -> null)` **không ném NPE chỉ vì kết quả lambda là null**. Nó trả null và **không ghi mapping**.
- Nếu muốn lưu trạng thái “đã tra cứu nhưng không tìm thấy”, có thể lưu một **value khác null** đại diện cho kết quả vắng mặt, như `Optional.empty()`. Đây gọi là *negative caching*; cần định nghĩa chính sách làm mới hoặc hết hạn để tránh cache kết quả âm quá lâu.

`Optional` chỉ là một cách biểu diễn, không bắt buộc. Trong API nghiệp vụ có nhiều trường hợp lỗi/không tìm thấy/đang chờ, một immutable result type rõ ràng có thể phù hợp hơn.

## 3. Tại sao `get` + `put` không thay được `computeIfAbsent`?

Giả sử hai thread cùng muốn khởi tạo một giá trị khi key chưa tồn tại.

Cách làm **không nguyên tử**:

```java
// Snippet KHÔNG an toàn để đảm bảo chỉ khởi tạo một lần.
String old = map.get("setting");
if (old == null) {
    String created = expensiveCreate();
    map.put("setting", created);
}
```

Đoạn trên giả sử `map` và `expensiveCreate()` đã được khai báo, không phải một chương trình độc lập.

Một interleaving có thể xảy ra:

```text
Thread A                         Thread B
--------                         --------
get("setting") -> null
                                 get("setting") -> null
expensiveCreate() -> "A"
                                 expensiveCreate() -> "B"
put("setting", "A")
                                 put("setting", "B")
```

Dù `ConcurrentHashMap.get` và `put` riêng lẻ đều được đồng bộ theo hợp đồng, **tổ hợp “kiểm tra vắng mặt → tạo → ghi” bị tách làm nhiều bước**. Hai thread có thể tạo trùng giá trị, thực thi side effect hai lần, thậm chí ghi đè kết quả nhau.

`putIfAbsent(key, value)` đảm bảo **thao tác ghi có điều kiện** là nguyên tử, nhưng nếu bạn **tính value trước khi gọi**, hai thread vẫn có thể thực hiện phép tính đắt đỏ:

```java
// Snippet: hai thread có thể cùng tính trước, dù chỉ một giá trị được ghi.
String computed = expensiveCreate();
map.putIfAbsent("setting", computed);
```

`computeIfAbsent(key, mappingFunction)` đưa phép **tính cho trường hợp vắng mặt** vào giao thức phối hợp của chính `ConcurrentHashMap`.

## 4. Hợp đồng của `ConcurrentHashMap.computeIfAbsent`

Chữ ký chính:

```java
V computeIfAbsent(
        K key,
        Function<? super K, ? extends V> mappingFunction)
```

Đây là chữ ký rút gọn; không phải file Java độc lập.

### Theo từng trường hợp

| Trạng thái key | Hành vi của `computeIfAbsent` | Kết quả |
| --- | --- | --- |
| Key đã gắn value không-null | Không chạy mapping function | Trả value đang có |
| Key vắng mặt, function trả value không-null | Ánh xạ key → value được thiết lập nguyên tử | Trả value đã thiết lập |
| Key vắng mặt, function trả `null` | Không thiết lập mapping | Trả `null` |
| Key vắng mặt, function ném unchecked exception hoặc `Error` | Không thiết lập mapping từ lần tính đó; exception truyền ra | Caller thấy lỗi |
| Key là `null` | Không hợp lệ | `NullPointerException` |
| Mapping function là `null` | Không hợp lệ | `NullPointerException` |
| Function cố cập nhật đệ quy vào map | Vi phạm hợp đồng | Có thể bị phát hiện và ném `IllegalStateException`; không được dựa vào khả năng phát hiện mọi trường hợp |

**Bảo đảm quan trọng trong JDK 17:** lời gọi `computeIfAbsent` của `ConcurrentHashMap` được thực hiện **nguyên tử** đối với mapping tương ứng. Theo hợp đồng, mapping function chỉ được gọi **một lần trong lần gọi cần tính** khi key đang vắng mặt; nếu key đã có thì không gọi.

Không được diễn giải thành “hàm này chỉ chạy đúng một lần trong **toàn bộ vòng đời ứng dụng**”. Nếu lần đầu trả null, ném lỗi, hoặc mapping sau đó bị xóa, lời gọi tương lai có thể phải tính lại. Với mỗi key, chỉ một mapping có thể được thiết lập ở một thời điểm theo giao thức của map; đó không phải guarantee *exactly-once* cho một side effect ngoài map trên toàn hệ thống.

**Lưu ý về lớp cụ thể:** `Map.computeIfAbsent` là một **default method** với những giới hạn bảo đảm tùy implementation. Không khái quát atomicity của `ConcurrentHashMap.computeIfAbsent` sang `HashMap` hay mọi triển khai `Map` khác. Khi viết code nhận `Map<K,V>` từ bên ngoài, phải xác định rõ loại map/hợp đồng concurrent thực tế.

## 5. Java 17+: nhiều thread khởi tạo cùng một key

Ta dùng một `CountDownLatch` để phát lệnh bắt đầu đồng thời cho tám thread, và `AtomicInteger` đếm số lần mapping function được thực sự gọi. Mỗi thread lấy value của cùng key.

Tạo file `AtomicMapInitialization.java`:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicMapInitialization {
    public static void main(String[] args)
            throws InterruptedException {
        ConcurrentHashMap<String, String> map =
                new ConcurrentHashMap<>();
        AtomicInteger creations = new AtomicInteger();
        AtomicInteger received = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] workers = new Thread[8];

        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                try {
                    start.await();
                    String value = map.computeIfAbsent("topic", key -> {
                        creations.incrementAndGet();
                        return "Java";
                    });
                    if ("Java".equals(value)) {
                        received.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            workers[i].start();
        }

        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("Received: " + received.get());
        System.out.println("Mapping calls: " + creations.get());
        System.out.println("Entries: " + map.size());
        System.out.println("Stored: " + map.get("topic"));
    }
}
```

Chạy:

```bash
javac AtomicMapInitialization.java
java AtomicMapInitialization
```

Đầu ra khi không có interrupt từ bên ngoài:

```text
Received: 8
Mapping calls: 1
Entries: 1
Stored: Java
```

**Tại sao kết quả không phụ thuộc scheduling?**

1. Không có code xóa hoặc thay mapping của key `topic`.
2. Function luôn trả một chuỗi không-null, nên mapping được thiết lập thành công.
3. Trong tình huống nhiều thread cạnh tranh cùng key, `ConcurrentHashMap` điều phối `computeIfAbsent` để mapping function không bị chạy đồng thời nhiều lần cho **chính ánh xạ đang được tạo**. Sau khi mapping tồn tại, các lời gọi sau chỉ trả value.
4. Tám thread đều nhận value `Java`. `AtomicInteger` bảo đảm việc đếm số lần gọi và số lượt nhận không bị lost update.
5. `join()` của main chờ tất cả worker hoàn tất trước khi in.

**Cảnh báo về side effect:** ví dụ dùng `creations.incrementAndGet()` để **đo việc gọi hàm**. Không suy rằng đưa một thao tác ghi cơ sở dữ liệu hoặc gửi email vào mapping function sẽ trở thành exactly-once toàn hệ thống; nếu mapping không được thiết lập thành công hoặc bị xóa về sau, các lần gọi khác có thể lặp lại side effect.

## 6. Giới hạn quan trọng: mapping function phải ngắn, đơn giản, không sửa chính map

Javadoc của `ConcurrentHashMap.computeIfAbsent` nêu rõ: **toàn bộ invocation nguyên tử**, và **một số update operation từ thread khác có thể bị chặn trong khi hàm tính đang chạy**. Vì vậy mapping function nên **ngắn và đơn giản**.

Đặc biệt, **mapping function không được sửa map đang được compute trong quá trình tính**. Đây không chỉ là lời khuyên tối ưu hiệu năng; đó là ràng buộc hợp đồng API.

### Những việc không nên làm bên trong lambda

- Gọi `map.put(...)`, `remove(...)`, `compute(...)` hoặc `computeIfAbsent(...)` trên **cùng map**, dù key khác.
- Đệ quy yêu cầu tạo **cùng key** đang được compute.
- Gọi callback không kiểm soát mà callback có thể sửa cùng map.
- Đợi task ở một executor mà task đó lại cần cập nhật hoặc compute cùng map.
- Thực hiện I/O lâu, gọi HTTP, chờ database, sleep dài hoặc khóa một resource không bảo đảm tiến triển.
- Tạo nhiều object rất nặng hoặc làm logic nghiệp vụ lâu ngay dưới cơ chế mapping.

Ví dụ **sai**:

```java
// Snippet CẤM thực hiện trong cùng ConcurrentHashMap.
// map đã khai báo, đoạn này không dùng làm demo chạy.
map.computeIfAbsent("a", key -> {
    map.put("b", "side-effect");
    return "value-a";
});
```

Đoạn trên vi phạm hợp đồng **không sửa chính map trong lúc tính**. Tùy key, trạng thái bảng, implementation và đường tranh chấp, có thể bị chặn, gặp lỗi hoặc hành vi không mong muốn. **Không** thiết kế dựa vào việc “chạy thử thấy vẫn được”.

### Đệ quy cùng key

```java
// Snippet NGUY HIỂM: có thể ném IllegalStateException.
map.computeIfAbsent("a", key ->
        map.computeIfAbsent("a", nested -> "nested"));
```

Javadoc quy định có thể ném `IllegalStateException` nếu phát hiện được một recursive update khiến computation không thể kết thúc. Không dùng exception này như cách điều khiển luồng bình thường; sửa thiết kế để việc khởi tạo không đệ quy cập nhật cùng map.

### Vì sao hàm chậm ảnh hưởng thread khác?

Map cần bảo vệ tính nguyên tử của việc cài mapping. Khi function đang tính value, các update khác có thể phải chờ một số vùng/trường hợp tranh chấp. Điều này **không** đồng nghĩa toàn map bị khóa một monitor toàn cục cho mọi hoạt động: nhiều thao tác đọc và cập nhật ở các phần độc lập có thể vẫn tiến triển. Nhưng không có cam kết hàm chậm chỉ làm chậm đúng một key.

**Quy tắc an toàn:** dùng lambda như một bước **tạo value cục bộ nhanh, không side effect lên cùng map**. Nếu cần tải dữ liệu nặng, nên cân nhắc lưu một **đối tượng đại diện cho công việc** rồi cho công việc chạy **ngoài mapping function**, hoặc dùng một executor/cache được thiết kế cho tải bất đồng bộ.

## 7. Khi computeIfAbsent trả null hoặc ném lỗi: có thể tính lại lần sau

Một kết quả null không thể được đưa vào `ConcurrentHashMap` vì value null bị cấm. Tương tự, khi mapping function ném một exception, lần tính không ghi mapping của key.

Tạo file `MappingFailureDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MappingFailureDemo {
    public static void main(String[] args) {
        ConcurrentHashMap<String, String> map =
                new ConcurrentHashMap<>();
        AtomicInteger calls = new AtomicInteger();

        try {
            map.computeIfAbsent("item", key -> {
                calls.incrementAndGet();
                throw new IllegalStateException("cannot initialize");
            });
        } catch (IllegalStateException expected) {
            System.out.println("First computation failed");
        }

        System.out.println("Absent after exception: "
                + !map.containsKey("item"));

        String value = map.computeIfAbsent("item", key -> {
            calls.incrementAndGet();
            return "recovered";
        });

        System.out.println("Recovered: " + value);
        System.out.println("Mapping calls: " + calls.get());

        String missing = map.computeIfAbsent("nil", key -> null);
        System.out.println("Null result: " + (missing == null));
        System.out.println("Nil stored: " + map.containsKey("nil"));
    }
}
```

Biên dịch/chạy:

```bash
javac MappingFailureDemo.java
java MappingFailureDemo
```

Đầu ra:

```text
First computation failed
Absent after exception: true
Recovered: recovered
Mapping calls: 2
Null result: true
Nil stored: false
```

Lần đầu function ném lỗi và key không được ghi. Lần sau key vẫn vắng mặt nên function chạy **lại**, lần này trả value. Trường hợp `nil` trả null cũng không lập mapping.

**Đây là điểm phá vỡ cách nghĩ “computeIfAbsent đảm bảo chỉ tính một lần mãi mãi”**. Nó chỉ phối hợp việc tính/ghi khi cần theo hợp đồng của map và **lần invocation**. Nếu nghiệp vụ cần khởi tạo đúng một lần bất kể process restart hay lỗi, phải có giao thức idempotency hoặc transaction ở nguồn chân lý tương ứng.

## 8. Ví dụ phổ biến: frequency map bằng `LongAdder`

Javadoc của `ConcurrentHashMap` có cách dùng rất hữu ích: map từ key sang bộ đếm. Với nhiều thread, tạo `LongAdder` cho key vắng mặt và tăng chính bộ đếm đó.

```java
frequencies.computeIfAbsent(key, k -> new LongAdder())
           .increment();
```

Đây là snippet; `frequencies`, `key` và import `LongAdder` chưa khai báo ở đây. Điểm hay là:

- `computeIfAbsent` chỉ chịu trách nhiệm **đặt một LongAdder dùng chung** cho key khi chưa có.
- `LongAdder.increment()` thực hiện cập nhật an toàn với nhiều thread vào **chính object LongAdder**.
- Không cần đưa `increment()` vào bên trong mapping function, vì điều đó khiến việc **đếm mỗi sự kiện** phụ thuộc vào key có vắng mặt hay không. Nếu key đã có sẵn, mapping function **không chạy** và sự kiện sẽ không được đếm.
- Không dùng `Integer` thường rồi `get()+1`/`put()`; giá trị immutable Integer không biến cặp read–modify–write thành atomic.

### Java 17+: bốn worker cùng tăng bộ đếm

Tạo file `ConcurrentFrequencyDemo.java`:

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class ConcurrentFrequencyDemo {
    public static void main(String[] args)
            throws InterruptedException {
        ConcurrentHashMap<String, LongAdder> frequencies =
                new ConcurrentHashMap<>();
        Thread[] workers = new Thread[4];

        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < 1_000; j++) {
                    frequencies.computeIfAbsent(
                            "java", key -> new LongAdder())
                            .increment();
                }
            });
            workers[i].start();
        }

        for (Thread worker : workers) {
            worker.join();
        }

        System.out.println("Keys: " + frequencies.size());
        System.out.println("Java count: "
                + frequencies.get("java").sum());
    }
}
```

Chạy:

```bash
javac ConcurrentFrequencyDemo.java
java ConcurrentFrequencyDemo
```

Đầu ra:

```text
Keys: 1
Java count: 4000
```

Mọi worker tăng **cùng một LongAdder** gắn với key `java`. Khi main đã `join()` đủ bốn worker, tổng cuối là 4.000. Trong lúc nhiều thread còn đang cập nhật, `LongAdder.sum()` **không phải một snapshot nguyên tử tuyệt đối**; nó thích hợp cho thống kê/đếm khi contention cao nhưng không nên dùng làm điều kiện kiểm tra chính xác cho transaction nghiệp vụ.

### Cạm bẫy khi remove/replace counter

Nếu một thread gọi `frequencies.remove("java")` trong lúc thread khác đã lấy `LongAdder` cũ rồi tiếp tục `increment()`, lượt tăng có thể xảy ra trên **object đã bị gỡ khỏi map**. Map bảo đảm thao tác thiết lập/lấy mapping riêng lẻ, không tự “khóa sống” object được trả về cho toàn bộ quá trình dùng tiếp.

Nếu cần mọi increment được tính chính xác xuyên qua việc reset/rotate counter, phải thiết kế giao thức reset riêng. Đừng đồng thời xóa/thay `LongAdder` trong map rồi suy rằng mọi thread đã chuyển sang bộ đếm mới.

## 9. Atomic mapping không có nghĩa value được thread-safe

`ConcurrentHashMap<K,V>` bảo vệ **cấu trúc ánh xạ key → value**, nhưng không tự bảo vệ **trạng thái mutable bên trong V**.

Ví dụ nguy hiểm:

```java
// Snippet: list là ArrayList mutable không có synchronization riêng.
ConcurrentHashMap<String, java.util.ArrayList<String>> map =
        new ConcurrentHashMap<>();

java.util.ArrayList<String> list = map.computeIfAbsent(
        "users", key -> new java.util.ArrayList<>());

// Nhiều thread cùng gọi list.add(...) là KHÔNG an toàn.
list.add("user-1");
```

`computeIfAbsent` có thể tạo và công bố **một List object dùng chung**, nhưng `ArrayList.add` không trở nên thread-safe chỉ vì List nằm trong `ConcurrentHashMap`.

Lựa chọn tùy yêu cầu:

- Value là **immutable object** khi đọc nhiều và thay nguyên value bằng `compute`/`replace`/CAS-map operation.
- Value là **atomic/concurrent structure** như `LongAdder`, `ConcurrentLinkedQueue` hoặc `CopyOnWriteArrayList` nếu hành vi của nó thực sự phù hợp.
- Dùng khóa riêng hoặc đóng gói state trong một class có policy đồng bộ rõ.
- Nếu cần atomicity của **nhiều key cùng lúc**, cơ chế riêng lẻ của `computeIfAbsent` **không tạo một transaction đa key**.

Ngay cả với một value immutable, quy tắc lấy tham chiếu rồi cập nhật map cũng cần xử lý trường hợp mapping bị thay giữa hai bước nếu đó là điều kiện nghiệp vụ.

## 10. Không nên đưa I/O và expensive computation trực tiếp vào mapping function

Một pattern ban đầu trông đơn giản:

```java
// Snippet KHÔNG KHUYẾN NGHỊ nếu loadFromRemote() có thể chờ lâu.
Result result = map.computeIfAbsent(
        key, k -> loadFromRemote(k));
```

`Result`, `map`, `key` và `loadFromRemote` chưa khai báo trong snippet này. Vấn đề:

1. Mapping function có thể chạy lâu hoặc chờ I/O không có timeout.
2. Một số update của thread khác trên map có thể bị chặn trong giai đoạn compute.
3. Nếu I/O cần một callback cập nhật cùng map, có thể xuất hiện deadlock/recursive update.
4. Khi function trả null, không cache được kết quả âm; những lời gọi sau lặp lại load.
5. Khi function ném lỗi, không cache được failure; các yêu cầu kế tiếp có thể gây **retry storm**.
6. Một side effect bên ngoài không được rollback nếu sau đó mapping thất bại.

Khi giá trị cần khởi tạo nặng, có thể dùng **placeholder** đại diện cho một phép tính, cài placeholder nhanh vào map rồi thực hiện công việc thật ở bên ngoài vùng mapping function. Cách này vẫn cần chính sách timeout, cancellation, lỗi và eviction.

### Java 17+: memoization bằng `FutureTask` được cài nhanh

Tạo file `ConcurrentMemoDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentMemoDemo {
    private final ConcurrentHashMap<String, FutureTask<String>> cache =
            new ConcurrentHashMap<>();
    private final AtomicInteger loads = new AtomicInteger();

    public String load(String key)
            throws InterruptedException, ExecutionException {
        FutureTask<String> task = cache.computeIfAbsent(
                key,
                k -> new FutureTask<>(() -> {
                    loads.incrementAndGet();
                    return k.toUpperCase(Locale.ROOT);
                })
        );

        // Chạy bên ngoài mapping function: run() chỉ thực thi
        // callable một lần dù nhiều thread cùng gọi.
        task.run();

        try {
            return task.get();
        } catch (ExecutionException e) {
            cache.remove(key, task); // Gỡ đúng instance thất bại.
            throw e;
        }
    }

    public static void main(String[] args) throws Exception {
        ConcurrentMemoDemo memo = new ConcurrentMemoDemo();
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                calls.add(() -> memo.load("java"));
            }

            List<Future<String>> results = executor.invokeAll(calls);
            boolean allMatch = true;
            for (Future<String> result : results) {
                allMatch &= "JAVA".equals(result.get());
            }

            System.out.println("All equal: " + allMatch);
            System.out.println("Load calls: " + memo.loads.get());
            System.out.println("Cache entries: " + memo.cache.size());
        } finally {
            executor.shutdown();
        }
    }
}
```

Biên dịch/chạy:

```bash
javac ConcurrentMemoDemo.java
java ConcurrentMemoDemo
```

Đầu ra khi không có interrupt từ bên ngoài:

```text
All equal: true
Load calls: 1
Cache entries: 1
```

**Giải thích:**

1. `computeIfAbsent` **chỉ tạo `FutureTask`**, một object nhỏ đại diện cho lần tính, và nhanh chóng cài vào map.
2. Hàm tính thật `k.toUpperCase(...)` và bộ đếm `loads` nằm **bên trong Callable của `FutureTask`**, được chạy bởi `task.run()` **sau khi `computeIfAbsent` đã trả về**.
3. Nếu nhiều caller cùng lấy một key, họ cùng nhận một `FutureTask`. `FutureTask.run()` được thiết kế để callable bên trong chỉ chạy **một lần** cho một instance task; các caller khác có thể `get()` chờ kết quả.
4. `FutureTask.get()` công bố kết quả hoặc ném `ExecutionException`. Nếu task thất bại, mã dùng `cache.remove(key, task)` để chỉ gỡ mapping **nếu nó vẫn đang trỏ đến đúng task này**, giúp lần gọi sau có thể thử lại.
5. Không cần suy đoán lịch của sáu worker: kết quả của tác vụ là `JAVA` và `FutureTask` duy nhất cho key giữ số lượt chạy là 1.

**Giới hạn của ví dụ:** việc `task.run()` diễn ra **trên thread caller** nào đó; nó có thể chặn caller nếu load nặng. Nếu muốn chạy trên executor riêng, phải thiết kế cơ chế **chỉ submit một lần**, không tự động gọi `executor.submit(task)` từ mọi caller sau `computeIfAbsent` vì như vậy có thể lặp việc submit không cần thiết. Cũng phải quan tâm timeout, hủy nhiệm vụ, retry sau lỗi và giới hạn số task cache.

Trong hệ thống production, một cache library có loader, expiry, size limit và quan sát thống kê thường dễ bảo trì hơn một thuật toán memoization tự viết. Đừng biến ví dụ `FutureTask` thành cache vô hạn mà không có eviction hoặc chính sách lỗi.

## 11. Đặt `computeIfAbsent()` cạnh các API remapping khác

Bài này đi sâu vào **null policy và mapping function**. Các API khác chỉ cần đối chiếu theo trạng thái mapping:

| API | Khi nào áp dụng hàm | Khi hàm trả null |
| --- | --- | --- |
| `computeIfAbsent(k, fn)` | Khi key vắng; hàm nhận key | Không thêm mapping |
| `computeIfPresent(k, fn)` | Khi key hiện diện; hàm nhận key và value cũ | Xóa mapping |
| `compute(k, fn)` | Khi key có hoặc không; value cũ có thể null | Không thêm hoặc xóa mapping |
| `merge(k, v, fn)` | Nếu absent gắn v; nếu present gọi fn(old, v) | Xóa mapping |

Các method có contract atomic theo mapping liên quan nhưng không bảo vệ side effect khác; callback không nên sửa chính map trong lúc remapping. Cách chọn `compute` và `merge` cho cập nhật thông dụng đã được triển khai, kiểm chứng ở bài API trước đó.

## 12. Tính nguyên tử theo key, không phải snapshot toàn map

ConcurrentHashMap hỗ trợ concurrency cao nhưng không cam kết mọi phép kiểm tra gồm **nhiều key** đều là một transaction:

```java
// Snippet, KHÔNG tạo transaction giữa hai key.
map.computeIfAbsent("left", key -> "L");
map.computeIfAbsent("right", key -> "R");
```

Thread khác có thể quan sát thấy `left` đã được thiết lập trong khi `right` chưa được thiết lập. Nếu invariant của ứng dụng là “cả hai mapping cùng thay đổi hoặc không mapping nào thay đổi”, cần thiết kế khác: một immutable aggregate value dưới **một key**, một lock ngoài, hoặc cơ chế transaction ở tầng dữ liệu phù hợp.

### Map views và iteration

`keySet()`, `values()`, `entrySet()` của `ConcurrentHashMap` cung cấp các view có thể dùng khi cập nhật đồng thời; iterator có tính **weakly consistent**: nó không ném `ConcurrentModificationException` chỉ vì những cập nhật thông thường đồng thời, nhưng **không** bảo đảm một snapshot tĩnh toàn map.

Các câu như “lặp ra tất cả key chính xác tại một thời điểm duy nhất” không nên dựa vào iterator của `ConcurrentHashMap` trong khi writer đang cập nhật. Nếu cần snapshot nhất quán theo nghiệp vụ, phải tạm dừng writer theo giao thức của ứng dụng, dùng immutable snapshot hoặc lựa chọn thiết kế thích hợp.

`size()` và `mappingCount()` khi map đang bị sửa đồng thời mang ý nghĩa đo trạng thái **có thể thay đổi/ước lượng**, không phải phép đồng bộ xác định số task có thể được nhận. `mappingCount()` trả `long` để biểu diễn số lượng ánh xạ lớn hơn phạm vi `int` và cũng được xem là estimate dưới concurrent updates.

## 13. Visibility, publication và sự an toàn của object được trả về

ConcurrentHashMap cung cấp quan hệ memory-consistency cho các thao tác truy cập ánh xạ: một phép cập nhật thành công cho key có thể được quan sát qua một phép đọc thấy value đó với những bảo đảm đồng bộ tương ứng.

Tuy nhiên:

- Map bảo vệ **cách công bố reference** cho một value; nó không ngăn một thread khác **sửa field mutable của object ấy** sau khi reference được công bố.
- Nếu value có nhiều field liên quan, invariant trong object cần được giữ bằng immutable design, lock hoặc atomic state riêng.
- Dùng `final` trong một immutable value type/record giúp thiết kế dễ suy luận, nhưng **record chứa một `List` mutable vẫn có thể bị sửa bên ngoài** nếu không defensive copy.
- Một thread có thể gọi `get(k)`, nhận value cũ, rồi thread khác gọi `replace(k, newValue)`. Việc thread đầu tiếp tục sử dụng value cũ không đồng nghĩa map đã giữ snapshot toàn cục.
- Với `computeIfAbsent`, giá trị do lambda tạo ra nên được **khởi tạo đầy đủ trước khi trả về**; không để object bị rò rỉ từ constructor hoặc callback khi state chưa hoàn chỉnh.

### Mutable value vs immutable snapshot

Ví dụ cho mapping một giá trị bất biến:

```java
// Snippet Java 17+: record Position(int x, int y) {} đã khai báo.
ConcurrentHashMap<String, Position> positions =
        new ConcurrentHashMap<>();
positions.compute("current",
        (key, oldValue) -> new Position(10, 20));
```

Không phải file độc lập vì `Position` chưa khai báo. Khi đã có mapping, reader có thể lấy object `Position` chứa hai primitive final đã được tạo xong; nếu writer muốn đổi cả hai cùng lúc, nó có thể thay **một reference value** qua `compute` hoặc `replace`, thay vì sửa hai trường mutable riêng lẻ ngoài khóa.

**Không được suy ra** update nhiều key trở thành nguyên tử chỉ vì từng value là record; ranh giới atomic của map vẫn là mapping riêng.

## 14. Một số sai lầm thường gặp

1. **“ConcurrentHashMap cũng cho phép null như HashMap.”** Sai: cấm cả key null và value null.
2. **“`computeIfAbsent(k, f)` trả null nghĩa map đã lưu null.”** Sai: không thiết lập mapping.
3. **“`get(k) == null` bảo đảm key không xuất hiện ở thời điểm sau đó.”** Sai: thread khác có thể cập nhật ngay.
4. **“`get` rồi `put` là một bước atomic vì map concurrent.”** Sai: đây là check–then–act gồm hai lời gọi.
5. **“`putIfAbsent` giúp tránh tính hai giá trị đắt đỏ từ trước.”** Không nhất thiết: nếu cả hai caller tính giá trị **trước** putIfAbsent thì vẫn tính hai lần.
6. **“Mapping function chỉ chạy đúng một lần cho key trong suốt vòng đời process.”** Sai nếu mapping bị xóa, function trả null hoặc ném lỗi.
7. **“Lambda có thể thoải mái gọi `put` cho key khác của cùng map.”** Sai: mapping function không được sửa map đang tính.
8. **“Đệ quy cùng key có thể dùng để khởi tạo phụ thuộc.”** Sai: có thể ném `IllegalStateException` hoặc làm thuật toán không tiến triển.
9. **“Đưa HTTP/DB vào computeIfAbsent là mặc nhiên an toàn và tối ưu.”** Sai: function dài có thể cản trở update khác, gây contention và retry/timeout phức tạp.
10. **“Value trả về đã tự thread-safe.”** Sai: `ArrayList`, `HashMap` và mutable object vẫn cần bảo vệ.
11. **“Đặt `LongAdder.increment()` trong lambda là đếm mọi lần gọi.”** Sai: lambda không chạy khi key đã hiện diện.
12. **“`LongAdder.sum()` luôn là snapshot nguyên tử giữa concurrent updates.”** Sai: không nhất thiết.
13. **“`computeIfAbsent` có thể transactionally cập nhật nhiều key.”** Sai: atomicity chỉ áp dụng ở ranh giới của phép update tương ứng.
14. **“Iteration trên ConcurrentHashMap luôn là snapshot cố định.”** Sai: weakly consistent, có thể phản ánh cập nhật đồng thời.
15. **“ConcurrentHashMap.size()` là cách chặn nhận thêm task chính xác tuyệt đối.”** Sai: kích thước quan sát không phải phép admission atomic.
16. **“Dùng `Optional.empty()` nghĩa cache tự có TTL.”** Sai: đó chỉ là một value đại diện; expiry/eviction phải thiết kế riêng.
17. **“Một FutureTask trong map bảo đảm mọi side effect ngoài process exactly once.”** Sai: FutureTask điều phối một instance task trong JVM; lỗi/restart/retry cần giao thức khác.

## 15. Checklist trước khi triển khai

1. **Xác định null có ý nghĩa gì.** Nếu cần negative caching, dùng value đại diện không-null; không gọi `put(k,null)`.
2. **Chọn ranh giới atomic đúng.** Khởi tạo giá trị một key dùng `computeIfAbsent`; tăng counter dùng `merge` hoặc `LongAdder`; invariant đa key cần thiết kế khác.
3. **Đừng tách check–then–act.** Không viết `if (get == null) put(...)` rồi mong chỉ một thread khởi tạo.
4. **Mapping function nên ngắn, đơn giản, thuần về map.** Không sửa cùng map, không đệ quy cập nhật, tránh I/O/lock/callback chờ lâu.
5. **Phân biệt trả null và ném exception.** Cả hai để key vắng mặt nếu trước đó chưa có mapping; lời gọi sau có thể tính lại.
6. **Kiểm tra side effect.** Việc tính lại sau thất bại/removal có thể lặp thao tác bên ngoài; cần idempotency khi nghiệp vụ yêu cầu.
7. **Value có tự thread-safe không?** Dùng atomic/concurrent value hoặc immutable snapshot nếu nhiều thread tiếp tục sửa/đọc.
8. **Không xóa counter đang được dùng rồi cho rằng mọi update sẽ chuyển sang counter mới.** Có thể cần reset protocol.
9. **Khi work nặng, cân nhắc placeholder.** Cài `FutureTask`/future nhanh rồi tính ngoài mapping function; xử lý exception, timeout và eviction.
10. **Không phụ thuộc thứ tự iterator khi cần snapshot toàn map.** Dùng thiết kế snapshot riêng.
11. **Theo dõi contention và giá trị thực tế.** Đo mapping latency, tỷ lệ failure/retry, số key, memory và cache hit.
12. **Kiểm tra JVM/API mục tiêu.** Các ví dụ ở đây chạy với Java 17+; tham khảo Javadoc theo đúng JDK triển khai.
13. **Thử concurrency theo lịch đa dạng.** Dùng latch để phối hợp test, không dùng `sleep` làm bằng chứng không có data race.
14. **Dọn dẹp đúng vòng đời.** Pool và cache phải có owner; tránh giữ bản ghi lỗi hoặc giá trị không còn hợp lệ vô hạn.

## 16. Tài liệu chính thức

- [ConcurrentHashMap — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html)
- [ConcurrentMap — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentMap.html)
- [Map.computeIfAbsent — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Map.html)
- [LongAdder — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html)

**Tóm lại:** `ConcurrentHashMap` cấm key/value null để việc truy cập đồng thời có một tín hiệu vắng mặt không nhập nhằng. `computeIfAbsent` giúp thiết lập mapping **nguyên tử** khi key chưa có, nhưng function có thể không ghi mapping khi trả null hoặc ném lỗi, và **không được sửa chính map trong lúc tính**. Giữ mapping function nhanh, dùng loại value có giao thức thread safety rõ ràng, tránh đẩy I/O dài vào computation, và không xem atomicity của một key là transaction cho nhiều key hoặc nhiều side effect.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-014">Giá trị mutable trong ConcurrentHashMap vẫn cần hợp đồng thread safety riêng</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị mutable trong ConcurrentHashMap vẫn cần hợp đồng thread safety riêng

## 1. Mục tiêu bài học

Sau bài học này, bạn có thể:

- Giải thích **ConcurrentHashMap** bảo vệ **các thao tác trên cấu trúc ánh xạ key → value**, nhưng không tự bảo vệ những field hoặc collection mutable nằm bên trong value.
- Phân biệt **atomic map operation**, **an toàn của object được trả về từ get()**, **safe publication**, **visibility**, **atomicity của cập nhật nghiệp vụ**, và **snapshot nhất quán**.
- Phát hiện lỗi khi nhiều thread cùng dùng **map.get(key).count++**, **map.get(key).list.add(...)** hoặc chuỗi **get → mutate → put** mà không có đồng bộ hóa phù hợp.
- Chọn đúng giải pháp: **AtomicInteger / AtomicLong**, **LongAdder**, **synchronized value**, **immutable record + compute/merge**, **copy-on-write immutable collection**, hoặc thiết kế ownership.
- Phân tích các giới hạn của **computeIfAbsent**, **compute**, **computeIfPresent**, **merge**, **putIfAbsent** và **replace** khi value bị sửa từ những reference bên ngoài.
- Nhận biết các lỗi tinh vi: thay value trong lúc thread khác giữ reference cũ, **remove/reset race**, thay đổi object ảnh hưởng đến equals/hashCode, **compound invariant nhiều key**, và việc lầm tưởng weakly consistent iteration là snapshot.
- Viết và chạy Java 17+ với các bài kiểm thử có điều phối bằng CountDownLatch và kết quả đầu ra xác định; không dùng sleep để “làm race xuất hiện”.

Bài thuộc nhóm **Concurrent Collections**, phạm vi là **Java concurrency nói chung**. Những cấu hình hoặc thành phần liên quan riêng tới Spring được trình bày ở chương khác.

## 2. Tuyên bố quan trọng nhất

**ConcurrentHashMap<K,V> giúp nhiều thread thao tác trên map đồng thời, nhưng V không tự trở thành thread-safe chỉ vì được lưu trong ConcurrentHashMap.**

Ví dụ:

~~~java
// Đoạn minh họa; cần import các kiểu tương ứng.
ConcurrentHashMap<String, Counter> map =
        new ConcurrentHashMap<>();
map.put("orders", new Counter());

Counter c = map.get("orders");
c.count++;  // Không được bảo vệ bởi ConcurrentHashMap.
~~~

Đây không phải chương trình chạy độc lập: Counter là kiểu mutable giả định có field count.

Một thao tác **map.get("orders")** có thể lấy an toàn reference của Counter đã được publish. Nhưng sau khi lấy được reference, **c.count++** là một chuỗi **đọc → cộng → ghi** trên object Counter. Nếu nhiều thread cùng chạy, có nguy cơ **lost update** dù tất cả các lời gọi get() đều hợp lệ.

Hãy coi hai lớp bảo đảm là **hai hợp đồng khác nhau**:

| Tầng | Cái được bảo vệ | Cái không tự được bảo vệ |
| --- | --- | --- |
| ConcurrentHashMap | Quan hệ key → value và các thao tác đồng thời theo API của map | Các field và collection mutable bên trong V |
| Value object | Phụ thuộc thiết kế V: immutable, atomic, lock, confinement... | Không tự có bảo đảm chỉ vì reference nằm trong map |
| Quy trình nghiệp vụ | Phụ thuộc invariant và giao thức transaction/đồng bộ | Không tự được đảm bảo bởi một lời gọi get() hoặc put() |

**Slogan dễ nhớ:** *Thread-safe map không có nghĩa thread-safe object graph.*

## 3. ConcurrentHashMap thực sự đảm bảo những gì?

**ConcurrentHashMap** trong java.util.concurrent là một Map hỗ trợ truy cập đồng thời mà không khóa toàn bộ map cho mỗi thao tác. Các operation độc lập như **get**, **put**, **remove**, **putIfAbsent**, **replace**, **compute**, **merge** có hợp đồng tương ứng.

Một số tính chất cần nhớ:

- **get(key)** trả về value hiện tại hoặc null khi không có mapping; nó **không sao chép** value và không làm một mutable value bất biến.
- **put(key, value)** thay thế ánh xạ cho key, nhưng không biến các thao tác trước/sau trên chính value thành atomic.
- **putIfAbsent** có thể đặt value nếu key chưa có mapping; khác với get rồi put thủ công khi nhiều thread tranh cùng key.
- **replace(key, expected, newValue)** chỉ thay mapping khi điều kiện được đáp ứng; không tự “đóng băng” object mà các thread khác đã lấy reference.
- **compute/computeIfPresent/merge** cung cấp cách cập nhật mapping tương ứng một cách nguyên tử theo key khi dùng đúng hợp đồng.
- ConcurrentHashMap **không hỗ trợ key null hoặc value null**. Với những phương thức remapping, trả **null** thường mang ý nghĩa xóa/không tạo mapping theo API cụ thể.
- Các phép duyệt có tính chất **weakly consistent**, có thể phản ánh một phần thay đổi đồng thời và không tạo snapshot nhất quán tức thời của toàn map.
- Các phép size, mappingCount và thống kê trên map đang thay đổi phù hợp cho giám sát/ước lượng; không dùng chúng thay giao thức admission-control chính xác.

**Map-level atomicity** chỉ cho biết chuyện gì xảy ra với **mapping**. Nó không nói rằng object value bị khóa trong toàn bộ vòng đời kể từ lần get đầu tiên.

## 4. Safe publication không đồng nghĩa an toàn cho những lần sửa tiếp theo

Giả sử một thread xây dựng Counter rồi **put** vào ConcurrentHashMap. Thread khác sau đó **get** thấy Counter đó. Các memory consistency effects của concurrent collection giúp công bố trạng thái đã hoàn thành trước khi đưa object vào map theo hợp đồng API.

Điều này **khác hẳn** việc thread xây dựng tiếp tục sửa Counter sau khi put và thread đọc cũng sửa nó cùng lúc:

~~~text
Thread A                          Thread B
--------                          --------
Counter c = new Counter();
c.count = 10;
map.put("x", c);  --------->
                                  Counter ref = map.get("x");
c.count++;   (A)                  ref.count++;  (B)
        |                              |
        +------ Cùng object mutable ---+
                        |
              Không có atomic increment.
~~~

Publication chỉ giải quyết **khả năng quan sát dữ liệu đã được publish** ở một ranh giới đồng bộ; nó không tạo monitor vĩnh viễn quanh object hay một quan hệ happens-before giữa mọi sửa đổi về sau.

### Vì sao final field chưa đủ?

~~~java
final class Holder {
    final Counter counter = new Counter();
}
~~~

Dù field **counter** là final, object **Counter** vẫn có thể bị sửa. **Final reference** ngăn việc gán lại reference đó qua field của Holder sau khi xây dựng; nó không làm những field bên trong Counter thành final hoặc thread-safe.

Tương tự, **Collections.unmodifiableList** chỉ tạo view ngăn sửa thông qua view. Nếu còn reference tới list mutable gốc, thread khác vẫn có thể sửa nó. **List.copyOf** tạo một danh sách không sửa qua API, nhưng nếu phần tử của list là object mutable thì đây vẫn chỉ là **shallow immutability**.

## 5. Ví dụ 1: tái hiện lost update một cách xác định

Race condition không phải lúc nào xảy ra khi chạy thử. Để chứng minh lỗi mà **không dựa vào may mắn của scheduler**, hai thread cùng đọc count trước khi được phép ghi. Vì cả hai cùng đọc 0, cả hai cùng ghi 1.

Tệp **UnsafeMutableValueDemo.java**:

~~~java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class UnsafeMutableValueDemo {
    static final class Counter {
        int value;
    }

    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, Counter> map =
                new ConcurrentHashMap<>();
        map.put("orders", new Counter());

        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch allowWrite = new CountDownLatch(1);

        Runnable increment = () -> {
            Counter counter = map.get("orders");
            int before = counter.value;

            bothRead.countDown();
            try {
                allowWrite.await();
                counter.value = before + 1;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };

        Thread first = new Thread(increment, "first");
        Thread second = new Thread(increment, "second");
        first.start();
        second.start();

        bothRead.await();
        allowWrite.countDown();
        first.join();
        second.join();

        System.out.println("expected = 2");
        System.out.println("actual = " + map.get("orders").value);
    }
}
~~~

~~~bash
javac -encoding UTF-8 UnsafeMutableValueDemo.java
java UnsafeMutableValueDemo
~~~

Stdout:

~~~text
expected = 2
actual = 1
~~~

**Phân tích:**

1. Map publish một Counter ban đầu bằng 0.
2. Hai thread lấy **cùng reference Counter** qua get().
3. Mỗi thread đọc 0 vào biến cục bộ before, rồi báo bothRead.
4. Main chỉ mở allowWrite sau khi **cả hai thread đã đọc**.
5. Cả hai ghi **before + 1 = 1**; tổng cuối vẫn 1 thay vì 2.
6. **join()** cho main biết cả hai thread đã hoàn thành trước khi đọc.
7. Latch tạo một lịch xen kẽ có kiểm soát; không dựa vào Thread.sleep().

Đây là mô hình đơn giản của **lost update**. Trong ứng dụng thật, chỉ viết **counter.value++** có thể làm bug ít tái hiện hơn nhưng bản chất vẫn là read-modify-write không nguyên tử.

**Không sửa bằng cách gọi map.put("orders", counter) sau khi tăng**: nếu hai thread cùng sửa Counter trước put, thao tác put thread-safe không hồi phục được increment bị mất.

## 6. Giải pháp A: lưu giá trị atomic thay cho counter mutable thường

Khi mỗi key chỉ cần một phép tăng độc lập, đặt **AtomicInteger** hoặc **AtomicLong** làm value là lựa chọn trực tiếp.

~~~java
// Minh họa:
ConcurrentHashMap<String, AtomicInteger> counts =
        new ConcurrentHashMap<>();

counts.computeIfAbsent("orders", k -> new AtomicInteger())
      .incrementAndGet();
~~~

Hai bảo đảm khác nhau phối hợp:

- **computeIfAbsent** giúp có một mapping cho key, tránh get-null rồi put tự viết.
- **incrementAndGet()** bảo đảm phép tăng trên **AtomicInteger value** là nguyên tử.

Lưu ý **computeIfAbsent không làm incrementAndGet thành một phần của mapping operation**; sau khi lấy được AtomicInteger, thao tác tăng được thực hiện trên object value. Điều này vẫn đúng khi các thread chia sẻ **cùng AtomicInteger ổn định cho key đó**.

### Ví dụ 2: AtomicInteger cho từng key

Tệp **AtomicValueMapDemo.java**:

~~~java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicValueMapDemo {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, AtomicInteger> map =
                new ConcurrentHashMap<>();

        Runnable worker = () -> {
            for (int i = 0; i < 10_000; i++) {
                map.computeIfAbsent(
                        "orders", k -> new AtomicInteger()
                ).incrementAndGet();
            }
        };

        Thread first = new Thread(worker);
        Thread second = new Thread(worker);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("orders = " + map.get("orders").get());
    }
}
~~~

~~~bash
javac -encoding UTF-8 AtomicValueMapDemo.java
java AtomicValueMapDemo
~~~

Stdout:

~~~text
orders = 20000
~~~

Hai thread cùng tăng một AtomicInteger dùng chung; mỗi increment không bị lost update. Nhưng ví dụ giả định **không có thread thứ ba remove hoặc replace mapping "orders" trong lúc tăng**.

### Bẫy remove và replace

Hình dung:

~~~text
T1: AtomicInteger old = map.get("orders");
T2: map.remove("orders");
T2: map.put("orders", new AtomicInteger());
T1: old.incrementAndGet();
~~~

T1 vừa tăng **old** sau khi nó đã bị gỡ khỏi map. Bản ghi trong map hiện tại không phản ánh increment ấy. Mỗi thao tác atomic vẫn đúng **trên object mà nó tác động**, nhưng nghiệp vụ kiểu “tổng tất cả orders bất kể reset” bị sai.

Nếu có yêu cầu **reset/removal đồng thời với increment**, phải định nghĩa rõ semantics (epoch, generation, version hoặc cập nhật mapping bằng compute cùng một giao thức), không chỉ thay value bằng atomic.

## 7. Giải pháp B: LongAdder cho thống kê tranh chấp cao

**LongAdder** là value phổ biến cho bộ đếm được nhiều thread tăng, đặc biệt khi contention cao. Mẫu thường gặp:

~~~java
// Minh họa: ConcurrentHashMap<String, LongAdder> frequencies
frequencies.computeIfAbsent(
        "java", k -> new LongAdder()
).increment();
~~~

**LongAdder.sum()** trả tổng quan sát được của các ô đếm bên trong. Khi **có cập nhật đồng thời**, sum() **không phải atomic snapshot**; có thể không phản ánh tất cả cập nhật đang xảy ra theo một thời điểm tuyến tính.

### Ví dụ 3: LongAdder sau khi hai worker hoàn tất

Tệp **LongAdderMapDemo.java**:

~~~java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class LongAdderMapDemo {
    public static void main(String[] args) throws InterruptedException {
        ConcurrentHashMap<String, LongAdder> map =
                new ConcurrentHashMap<>();

        Runnable work = () -> {
            for (int i = 0; i < 5_000; i++) {
                map.computeIfAbsent(
                        "events", key -> new LongAdder()
                ).increment();
            }
        };

        Thread a = new Thread(work);
        Thread b = new Thread(work);
        a.start();
        b.start();
        a.join();
        b.join();

        System.out.println("events = " + map.get("events").sum());
    }
}
~~~

~~~bash
javac -encoding UTF-8 LongAdderMapDemo.java
java LongAdderMapDemo
~~~

Stdout:

~~~text
events = 10000
~~~

**sum() sau join** được gọi khi mọi cập nhật minh họa đã hoàn tất, nên kết quả xác định. Nếu main gọi sum() giữa lúc hai worker đang cập nhật, không được sử dụng số đó làm transaction quota chính xác.

| Công cụ | Ưu điểm | Điểm cần chú ý |
| --- | --- | --- |
| AtomicInteger | Increment, get, compareAndSet; thích hợp quyết định một biến | Contention nếu nhiều thread sửa cùng điểm |
| AtomicLong | Như AtomicInteger nhưng miền long | Vẫn chỉ bảo vệ một giá trị |
| LongAdder | Tăng throughput trong một số workload thống kê tranh chấp cao | sum() đang có update không phải snapshot nguyên tử |
| Plain int trong value | Đơn giản khi chỉ một thread sở hữu hoặc lock bao phủ | Không tự an toàn khi nhiều writer |

**Cùng bẫy remove/replace như AtomicInteger:** nếu thread giữ LongAdder cũ và increment sau khi mapping bị thay, cập nhật có thể không còn được phản ánh ở value mới. **sumThenReset()** cũng không phải transaction ngăn mọi concurrent increments; muốn chia epoch và reset chuẩn xác, cần giao thức riêng.

## 8. Giải pháp C: immutable value và cập nhật ánh xạ bằng compute

Với bất biến bao gồm **nhiều field**, có thể đặt **immutable record** làm value và dùng **compute** hoặc **computeIfPresent** để thay **toàn bộ mapping** theo key.

Ví dụ Inventory có hai số: available và reserved. Bất biến:

- available không âm;
- reserved không âm;
- **available + reserved** luôn bằng tổng ban đầu;
- một yêu cầu giữ chỗ chỉ thay hai số **cùng lúc**.

Nếu đặt hai AtomicInteger riêng trong value rồi cập nhật lần lượt, một reader có thể thấy trạng thái trung gian. Thay vào đó, mỗi lần update tạo một **record mới** và thay **một mapping** bằng compute.

### Ví dụ 4: immutable record + computeIfPresent

Tệp **ImmutableInventoryMapDemo.java**:

~~~java
import java.util.concurrent.ConcurrentHashMap;

public class ImmutableInventoryMapDemo {
    record Inventory(int available, int reserved) {}

    static void reserveOne(
            ConcurrentHashMap<String, Inventory> inventory) {
        inventory.computeIfPresent("book", (key, old) -> {
            if (old.available() == 0) {
                return old;
            }
            return new Inventory(
                    old.available() - 1,
                    old.reserved() + 1
            );
        });
    }

    public static void main(String[] args)
            throws InterruptedException {
        ConcurrentHashMap<String, Inventory> map =
                new ConcurrentHashMap<>();
        map.put("book", new Inventory(100, 0));

        Runnable buyer = () -> {
            for (int i = 0; i < 50; i++) {
                reserveOne(map);
            }
        };

        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(buyer);
            threads[i].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        Inventory end = map.get("book");
        System.out.println("available = " + end.available());
        System.out.println("reserved = " + end.reserved());
        System.out.println(
                "total = " + (end.available() + end.reserved())
        );
    }
}
~~~

~~~bash
javac -encoding UTF-8 ImmutableInventoryMapDemo.java
java ImmutableInventoryMapDemo
~~~

Stdout:

~~~text
available = 0
reserved = 100
total = 100
~~~

**Vì sao đúng?**

1. Inventory chỉ chứa hai primitive int và là **immutable record** trong ví dụ.
2. Mọi phép reserveOne cho **cùng key "book"** đi qua computeIfPresent.
3. Hàm remapping dùng **snapshot old nhất quán của mapping đó**, tạo một Inventory mới hoặc giữ nguyên old nếu không còn vé.
4. Thay mapping được thực hiện theo ngữ nghĩa nguyên tử của computeIfPresent với key tương ứng; các thread không thể cùng commit hai cập nhật dựa trên cùng phiên bản cũ mà làm mất lượt.
5. Reader gọi **map.get("book") một lần**, rồi đọc cả available/reserved trên **cùng snapshot**.
6. Bốn worker thử tổng 200 lần, nhưng chỉ có 100 vé; kết quả cuối vẫn nhất quán.

**Điều kiện:** không được có đường khác sửa mutable state bên trong Inventory. Ở ví dụ này record chỉ có int nên không có reference mutable lộ ra. Nếu record chứa ArrayList có thể sửa từ bên ngoài, **record không tự tạo deep immutability**.

### Đọc hai lần không phải snapshot

~~~java
// Minh họa sai nếu cần dữ liệu cùng một version:
int available = map.get("book").available();
int reserved = map.get("book").reserved();
~~~

Giữa hai lần get, writer có thể thay mapping, khiến hai field được lấy từ **hai Inventory khác nhau**. Hãy:

~~~java
Inventory snapshot = map.get("book");
int available = snapshot.available();
int reserved = snapshot.reserved();
~~~

Mảnh mã trên giả định Inventory đã được khai báo và mapping không null. Một lần get lấy **một object snapshot**; nếu field của nó immutable, các getter sau đó cùng phản ánh phiên bản đó.

## 9. compute không tự khóa mọi object value

Một hiểu nhầm đặc biệt nguy hiểm:

~~~java
// Minh họa: nếu Counter là mutable,
map.compute("x", (key, counter) -> {
    counter.value++;
    return counter;
});
~~~

Bản thân remapping operation được phối hợp theo key. Nhưng nếu thread T2 đang giữ reference cũ:

~~~java
Counter same = map.get("x");
same.value++; // T2 sửa ngoài compute.
~~~

Thì T2 **không bị buộc phải lấy cùng khóa nội bộ** với compute của T1. Việc compute quản lý mapping không khiến mọi caller cầm Counter phải đồng bộ trên object đó.

**Không thiết kế quanh khóa nội bộ của ConcurrentHashMap như một lock công khai cho value.** Muốn mutation in-place an toàn, mọi đường truy cập đến value phải tham gia một giao thức đồng bộ **do ứng dụng định nghĩa và kiểm soát**.

### Remapping function nên ngắn và có kiểm soát

Các API compute/merge có thể phải khóa/điều phối những cập nhật khác trên cùng vùng map trong quá trình remapping. Vì vậy:

- Không gọi mạng hoặc blocking I/O dài bên trong remapping function.
- Không thực hiện side effect không thể retry/idempotent mà chưa xem xét hậu quả khi exception hoặc request gọi lại.
- Không dùng remapping function để cập nhật đệ quy các mapping của **chính map đó**; những thao tác recursive update bị cấm hoặc không có bảo đảm tiến triển và có thể gây IllegalStateException.
- Xử lý rõ ngoại lệ của remapping function: nếu function ném, mapping có thể giữ nguyên theo hợp đồng operation, nhưng side effect đã gây ra bên ngoài không tự rollback.
- **computeIfAbsent** phù hợp để xây value khi key chưa có mapping; sau khi tạo ra một mutable value, mọi lần sử dụng nó vẫn cần thread safety.

**Không coi compute là một giao dịch bao phủ database, external API hoặc nhiều key khác nhau.**

## 10. Giải pháp D: bảo vệ value bằng khóa riêng

Khi value có logic mutable phức tạp, một thiết kế hợp lệ là **value tự quản lý synchronized/lock** trong các method của nó. Tất cả reader/writer phải dùng **cùng protocol**.

~~~java
// Minh họa một value object:
final class SafeCounter {
    private int value;

    synchronized void increment() {
        value++;
    }

    synchronized int get() {
        return value;
    }
}
~~~

Mỗi method synchronized giữ monitor của SafeCounter, ngăn hai thread đồng thời sửa field value thông qua các method ấy; unlock/lock có quan hệ happens-before tương ứng.

**Những điều kiện quan trọng:**

- Field mutable phải **private** hoặc không thể sửa ngoài method được bảo vệ.
- Reader cũng phải dùng synchronized getter hoặc cơ chế visibility phù hợp, không đọc raw field ngoài khóa.
- Nếu một invariant bao trùm nhiều field, cần đặt **toàn bộ check–then–act trong một critical section**, không chỉ synchronized từng getter/setter rồi ghép ở caller.
- Nếu nhiều object value cùng tham gia một nghiệp vụ, một lock per-value có thể không bảo vệ invariant xuyên nhiều key.
- Nếu mapping có thể bị **remove/replace**, thread đang giữ reference value cũ vẫn có thể gọi method trên object cũ. Giao thức khóa của object cũ không tự đồng bộ với value mới.

### Ví dụ 5: value tự bảo vệ dữ liệu

Tệp **SynchronizedValueMapDemo.java**:

~~~java
import java.util.concurrent.ConcurrentHashMap;

public class SynchronizedValueMapDemo {
    static final class SafeCounter {
        private int value;

        synchronized void increment() {
            value++;
        }

        synchronized int snapshot() {
            return value;
        }
    }

    public static void main(String[] args)
            throws InterruptedException {
        ConcurrentHashMap<String, SafeCounter> map =
                new ConcurrentHashMap<>();

        map.put("jobs", new SafeCounter());

        Runnable worker = () -> {
            for (int i = 0; i < 10_000; i++) {
                map.get("jobs").increment();
            }
        };

        Thread first = new Thread(worker);
        Thread second = new Thread(worker);
        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println(
                "jobs = " + map.get("jobs").snapshot()
        );
    }
}
~~~

~~~bash
javac -encoding UTF-8 SynchronizedValueMapDemo.java
java SynchronizedValueMapDemo
~~~

Stdout:

~~~text
jobs = 20000
~~~

**Tại sao đúng?** Cả hai thread tăng một SafeCounter ổn định và cùng sử dụng monitor của object đó; main đọc qua synchronized snapshot() sau khi join.

**Giới hạn giả định:** mapping "jobs" không bị xóa hoặc thay trong lúc worker chạy. Nếu cần reset, hãy định nghĩa rõ reset đồng bộ với increment, chẳng hạn qua per-key mapping computation hoặc epoch/state object dùng chung theo lifetime thích hợp.

### Dùng ReentrantLock thay synchronized khi nào?

Nếu value cần **tryLock**, timeout, interruptible acquisition, Condition hoặc nhiều critical section có chính sách riêng, **ReentrantLock** có thể phù hợp. Mẫu là **lock.lock() → try → finally unlock()**. Không dùng lock khác nhau mỗi lần gọi; tất cả reader/writer liên quan phải tham gia cùng lock được giữ ổn định trong value.

Không khẳng định ReentrantLock luôn nhanh hơn synchronized. Hiệu quả phụ thuộc contention và workload; ưu tiên cách đơn giản đúng contract.

## 11. Giải pháp E: copy-on-write cho collection nằm trong value

Giả sử map lưu **List<String>** theo key:

~~~java
ConcurrentHashMap<String, java.util.List<String>> map =
        new ConcurrentHashMap<>();

map.put("logs", new java.util.ArrayList<>());
map.get("logs").add("A"); // Unsafe khi có nhiều writer đồng thời.
~~~

ConcurrentHashMap chỉ bảo vệ **reference List** được map giữ, không bảo vệ **ArrayList.add()**. Hai thread có thể tranh kích cỡ, dữ liệu bên trong ArrayList, gây mất phần tử hoặc lỗi cấu trúc.

Một giải pháp cho danh sách **không quá lớn, tần suất ghi thấp** là copy-on-write ở cấp mapping:

~~~java
// Minh họa một mapping mutation:
map.compute("logs", (key, oldList) -> {
    java.util.List<String> next = new java.util.ArrayList<>(
            oldList == null ? java.util.List.of() : oldList
    );
    next.add("new");
    return java.util.List.copyOf(next);
});
~~~

- Mỗi lần compute, code tạo list mới, không sửa list cũ.
- List được publish là **unmodifiable** và phần tử String là immutable.
- Reader có thể giữ reference list cũ như một **snapshot** không bị writer thay đổi.
- Những writer cùng key cập nhật mapping qua compute nên không mất phần tử do thao tác get–modify–put tách rời.
- Chi phí **O(n) copy mỗi lần thêm** có thể rất lớn khi danh sách dài hoặc ghi dày đặc; không coi đây là giải pháp hiệu năng chung.

### Ví dụ 6: thêm vào immutable list bằng compute

Tệp **ImmutableListMapDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ImmutableListMapDemo {
    public static void main(String[] args)
            throws InterruptedException {
        ConcurrentHashMap<String, List<String>> map =
                new ConcurrentHashMap<>();

        map.put("events", List.of());

        Runnable add = () -> {
            for (int i = 0; i < 100; i++) {
                map.compute("events", (key, old) -> {
                    List<String> next = new ArrayList<>(old);
                    next.add("e");
                    return List.copyOf(next);
                });
            }
        };

        Thread first = new Thread(add);
        Thread second = new Thread(add);
        first.start();
        second.start();
        first.join();
        second.join();

        List<String> result = map.get("events");
        System.out.println("size = " + result.size());
        System.out.println("immutable = " + rejectsMutation(result));
    }

    static boolean rejectsMutation(List<String> list) {
        try {
            list.add("unexpected");
            return false;
        } catch (UnsupportedOperationException ex) {
            return true;
        }
    }
}
~~~

~~~bash
javac -encoding UTF-8 ImmutableListMapDemo.java
java ImmutableListMapDemo
~~~

Stdout:

~~~text
size = 200
immutable = true
~~~

**Vì sao đúng?** Mỗi phép compute cho cùng key đọc list hiện tại, sao chép, thêm một phần tử và publish list mới. Không writer nào sửa list đã publish. Hai thread chạy 100 lần, kết quả là 200 chuỗi e.

**Lưu ý:** “immutable = true” chỉ chứng minh list API không cho phép thêm; với các phần tử String thì nội dung cũng immutable. Nếu phần tử là một mutable Person/Address, **List.copyOf không deep-copy từng phần tử**. Reader vẫn có thể thấy những thay đổi ngoài ý muốn trên object phần tử.

### Có nên dùng CopyOnWriteArrayList?

**CopyOnWriteArrayList** là collection thread-safe riêng, có snapshot iterator và cơ chế copy khi sửa; có thể phù hợp khi **đọc rất nhiều và ghi ít**. Nếu value trực tiếp là CopyOnWriteArrayList, cập nhật list qua API của chính nó được đồng bộ theo hợp đồng. Nhưng chi phí copy khi ghi và khả năng remove/replace mapping vẫn cần được xét.

Không nhầm **copy-on-write list object** với **compute + thay immutable mapping**: hai kỹ thuật có ranh giới ownership và snapshot khác nhau.

## 12. Concurrent collection làm value cũng cần chọn đúng thao tác

Một số value vốn là concurrent collection:

- **ConcurrentLinkedQueue<E>** hỗ trợ thêm/lấy phần tử đồng thời.
- **ConcurrentHashMap<K2,V2>** lồng trong map có các thao tác thread-safe ở cấp nested map.
- **ConcurrentHashMap.newKeySet()** cung cấp set concurrent.
- **CopyOnWriteArrayList<E>** hữu ích khi đọc nhiều/ghi ít.
- **Collections.synchronizedList(list)** dùng một monitor bao quanh các method, nhưng **khi iterate** cần đồng bộ ngoài theo đúng hợp đồng wrapper.

Ngay cả với concurrent collection làm value, **chuỗi thao tác nhiều bước** vẫn có thể sai:

~~~java
// Minh họa logic race với concurrent collection:
if (!queue.isEmpty()) {
    Item first = queue.poll();
    // first vẫn có thể là null nếu thread khác lấy trước.
}
~~~

Đây là đoạn minh họa giả định queue và Item tồn tại. Để lấy phần tử nếu có, **poll() rồi kiểm tra null** là một operation phù hợp. Nếu cần chờ phần tử, dùng **BlockingQueue.take()** hoặc poll(timeout), không tự spin kiểm tra isEmpty().

**Atomic của từng method không suy ra atomic của toàn bộ workflow.**

## 13. putIfAbsent, computeIfAbsent và sự ổn định của value

Mẫu dễ mắc lỗi:

~~~java
// Minh họa có race khi nhiều thread cùng tạo Counter:
if (map.get(key) == null) {
    map.put(key, new Counter());
}
~~~

Hai thread có thể cùng thấy null rồi cùng put hai Counter khác nhau; thread thứ nhất có thể đã tăng Counter của mình nhưng mapping bị thread thứ hai thay thế. Đây là **lost reference/update do check–then–act**.

**putIfAbsent** cho phép đặt mapping khi chưa có và trả value cũ nếu đã tồn tại. **computeIfAbsent** cho phép tạo value khi thiếu dưới hợp đồng atomic của map cho key đó.

~~~java
// Minh họa:
AtomicInteger counter = map.computeIfAbsent(
        key, k -> new AtomicInteger()
);
counter.incrementAndGet();
~~~

Nếu các key **không bị xóa/thay thế đồng thời**, mọi thread nhận cùng value hiện diện ổn định và thực hiện atomic increment.

### Những quy tắc dành cho mapping function

- Không thực hiện I/O dài hoặc gọi dịch vụ khác bên trong mapping function, vì có thể chặn những update liên quan.
- Không sửa đệ quy mapping của cùng ConcurrentHashMap bên trong remapping callback; JDK có thể ném IllegalStateException khi phát hiện recursive update không thể hoàn tất.
- Không đặt side effect nghiệp vụ không idempotent vào callback mà không hiểu chính xác thời điểm commit và đường lỗi.
- Không trả null khi muốn giữ một value null; ConcurrentHashMap không hỗ trợ null mapping, và null có ý nghĩa bỏ/không tạo mapping tùy API.
- Khi **computeIfAbsent trả một value mutable**, nhiệm vụ bảo vệ field của value **chưa hoàn thành**; phải tiếp tục dùng atomic/lock/confinement.

## 14. Bẫy get → mutate → put và replace

Một anti-pattern:

~~~java
// Minh họa:
Counter c = map.get("x");
c.count++;
map.put("x", c);
~~~

**put() không làm c.count++ atomic.** Nếu hai thread cầm cùng c, phép tăng có thể mất như ví dụ 1. Nếu hai thread mỗi người tạo một bản sao Counter rồi put, người ghi sau có thể ghi đè kết quả người trước.

### Dùng replace có điều kiện

Có thể đọc một **immutable value**, tạo phiên bản mới, rồi gọi:

~~~java
// Minh họa với value immutable:
boolean swapped = map.replace(key, oldValue, newValue);
~~~

Nếu false, cần đọc lại và tính lại. Nhưng **replace(key, oldValue, newValue)** dựa trên điều kiện so sánh value theo hợp đồng Map/ConcurrentHashMap, không phải một CAS reference thuần khi equals khác danh tính. Nếu value dùng **equals trên mutable fields**, việc so sánh có thể có các vấn đề khi field thay đổi đồng thời.

Trong nhiều trường hợp **compute** cho cùng key diễn đạt dễ hiểu hơn một vòng optimistic replace. Nếu cần version/historical change detection, cân nhắc **version trong immutable value** và giao thức rõ ràng.

### equals/hashCode của mutable value

Hash key quan trọng vì map định vị mapping bằng key. **Mutable key** mà equals/hashCode thay đổi khi ở trong map có thể làm lookup khó hoặc sai. Với **value**, việc equals phụ thuộc mutable field có thể làm điều kiện remove(key, expected) và replace(key, expected, replacement) bất ổn dưới cập nhật đồng thời.

Nên dùng **immutable key có equals/hashCode ổn định**. Với conditional replace/remove, cũng nên hiểu rõ equality semantics của value và tránh mutation không đồng bộ.

## 15. Multi-key invariants không tự nguyên tử

ConcurrentHashMap hỗ trợ cập nhật **mapping một key** theo hợp đồng operation. Nhưng nghiệp vụ có thể cần thay hai key cùng lúc.

Ví dụ tài khoản A và B:

~~~text
balance[A] = 100
balance[B] = 200
Tổng tiền phải luôn = 300.

Chuyển 10 từ A sang B:
  A -> 90
  B -> 210
~~~

Nếu sử dụng hai compute độc lập:

~~~java
// Minh họa: hai bước tách rời, không là transaction nhiều key.
balances.compute("A", (k, old) -> old - 10);
balances.compute("B", (k, old) -> old + 10);
~~~

Một thread đọc trong khoảng giữa có thể thấy A=90 và B=200; tổng tạm là 290. Hai lời compute độc lập không tạo một **atomic multi-key transaction**.

Các lựa chọn:

- **Lock** bao phủ cả lần update và các lần đọc cần snapshot, với cùng giao thức cho mọi đường truy cập liên quan.
- Gói cả A/B vào **một immutable aggregate value** dưới **một key** và thay bằng compute/CAS phù hợp nếu bài toán cho phép.
- Thiết kế **owner thread** tuần tự hóa toàn bộ state của hệ thống nhỏ.
- Nếu trạng thái nằm ở **database** hoặc nhiều JVM, dùng transaction/conditional update/optimistic version ở kho dữ liệu có thẩm quyền.

### Atomic snapshot cho cả map không miễn phí

Một vòng lặp đọc hết ConcurrentHashMap đang thay đổi có thể thấy những mapping thuộc các mốc khác nhau. Weakly consistent iterator **không tạo snapshot giao dịch của toàn map**. Nếu cần một ảnh nhất quán trên nhiều key, cần giao thức snapshot riêng.

**Không dùng map.size() rồi so sánh quota** trong chuỗi get → check → put như thể đó là một transaction: nhiều thread có thể xen giữa các bước.

## 16. Stable value identity, reset và generation

Một lớp lỗi không xuất hiện trong ví dụ chỉ có increment là **reset**:

~~~text
Thread 1: lấy reference V1 từ map.get(key)
Thread 2: map.replace(key, V2)
Thread 1: tăng V1
Thread 3: đọc map.get(key) -> V2

Tăng của Thread 1 không còn hiển thị trong mapping hiện hành.
~~~

Trường hợp này không có nghĩa AtomicInteger bị lỗi. Nó cho thấy **atomic theo object** khác **atomic theo key qua các thế hệ value**.

Để thiết kế reset rõ ràng:

1. Định nghĩa **nghĩa của reset**: xóa mọi lượt cũ, bắt đầu thế hệ mới, hay lấy snapshot rồi tiếp tục đếm?
2. Chọn một **version/generation** bất biến cùng giá trị để thread cập nhật kiểm tra đúng đời.
3. Đưa **increment và reset** qua cùng một giao thức per-key mapping computation nếu cần linearizable semantics theo key.
4. Hoặc giữ value object cố định và làm reset/increment dưới cùng lock nếu contract cho phép.
5. Đảm bảo reader hiểu số đọc là của **phiên bản nào**.
6. Nếu có side effect ở DB hoặc nhiều JVM, Java map không tự tạo transaction xuyên process.

**Đừng hứa một bộ đếm phản ánh tất cả increment trong lịch sử** nếu hệ thống đồng thời cho phép remove/recreate cùng key mà không có quy tắc về increment đã cầm reference cũ.

## 17. Value object thoát ra ngoài và encapsulation

Giả sử class Cache giữ một ConcurrentHashMap và trả chính value mutable cho caller:

~~~java
// Minh họa class không nên trả reference trực tiếp:
public List<String> getEvents(String key) {
    return events.get(key); // Caller có thể add/remove.
}
~~~

Nếu caller nhận ArrayList và sửa trực tiếp, mọi invariant mà Cache tưởng chỉ được kiểm soát qua compute đã bị phá. Dù Cache writer khác dùng compute chuẩn, caller vẫn có thể mutate list nằm trong mapping hiện hành.

Các cách bảo vệ API:

- Trả **immutable snapshot** hoặc read-only DTO với phần tử immutable.
- Chỉ expose **operation có ngữ nghĩa** như addEvent(key, event), reserve(key), snapshot(key).
- Không đưa ra reference đến lock, collection mutable hay object nội bộ nếu caller có thể sửa ngoài giao thức.
- Nếu trả live concurrent collection, ghi rõ contract: caller được phép làm thao tác nào, phép duyệt và snapshot có consistency mức nào.
- Thiết kế **immutable value** từ đầu để giảm diện tích API phải đồng bộ hóa.

**Encapsulation là một phần của thread safety.** Không thể kiểm soát mọi mutation nếu bất kỳ caller nào cũng giữ reference mutable rồi ghi tùy ý.

## 18. Các lỗi với nested object graph

Đôi khi value bản thân là một object có field final nhưng trỏ tới object mutable:

~~~java
record UserStats(String user, List<Integer> values) {}
~~~

Record này không tự immutable sâu. Nếu values trỏ tới ArrayList được share và caller khác add vào, stats vẫn thay đổi một cách quan sát được dù field values là final.

Để có snapshot thực sự:

~~~java
record UserStats(String user, List<Integer> values) {
    UserStats {
        values = List.copyOf(values);
    }
}
~~~

Nếu Integer immutable, bản sao list là một tầng bất biến phù hợp. Nếu phần tử là mutable object, cần **defensive copy sâu hơn hoặc immutable element type** theo yêu cầu.

Các nested map/list tương tự: ConcurrentHashMap ở tầng ngoài không bảo vệ inner HashMap, ArrayList hoặc mutable element mà thread có thể chia sẻ.

### SynchronizedList là giải pháp đúng tới đâu?

**Collections.synchronizedList(list)** bảo vệ từng method được gọi trên wrapper. Nhưng khi duyệt:

~~~java
// Minh họa với synchronizedList đã tạo:
synchronized (synchronizedList) {
    for (String value : synchronizedList) {
        process(value);
    }
}
~~~

Cần giữ monitor tương ứng quanh iteration theo hợp đồng wrapper. Nếu caller giữ reference list gốc rồi sửa ngoài wrapper, vẫn phá giao thức đồng bộ hóa.

## 19. Chọn giải pháp theo invariant

| Nhu cầu | Lựa chọn khởi đầu | Giới hạn cần nhớ |
| --- | --- | --- |
| Tăng số lượt độc lập theo key | ConcurrentHashMap<String, AtomicInteger> | Remove/replace cùng lúc cần semantics riêng |
| Thống kê cộng nhiều dưới contention | ConcurrentHashMap<String, LongAdder> | sum() đang cập nhật không là atomic snapshot |
| Nhiều field phải đổi nguyên khối trong cùng key | Immutable record + compute/computeIfPresent | Không bao phủ nhiều key hoặc external side effect |
| Mutable object với critical section phức tạp | Synchronized method / ReentrantLock trong value | Mọi reader/writer phải dùng cùng giao thức |
| List nhỏ đọc nhiều, ghi ít | Immutable list + compute / CopyOnWriteArrayList | Copy mỗi lần ghi, shallow immutability |
| Queue nhận event nhiều thread | ConcurrentLinkedQueue / BlockingQueue | Chọn theo có cần blocking/backpressure |
| Cập nhật trạng thái của hai key cùng lúc | Lock cho aggregate hoặc transaction ở nơi lưu dữ liệu | compute từng key không là multi-key atomicity |
| Một owner xử lý mọi update | Single-thread executor/actor-style ownership | Caller không được mutate state trực tiếp |
| Đọc snapshot của toàn map đang thay đổi | Explicit snapshot protocol, lock/version/aggregate | Weakly consistent iteration không đủ |

**Mấu chốt chọn lựa:** xác định **đơn vị bất biến** cần bảo vệ. Nếu bất biến nằm hoàn toàn trong một AtomicInteger, chọn atomic. Nếu nằm trên hai field của một record, dùng immutable snapshot + compute hoặc lock. Nếu nằm trên nhiều key hoặc external system, cần giao thức cấp cao hơn.

## 20. Thread confinement vẫn là một lựa chọn

Thay vì nhiều thread trực tiếp sửa value trong map, có thể dùng **một worker sở hữu từng nhóm state**; các thread khác gửi command cho worker, sau đó đọc kết quả qua Future hoặc message.

Ưu điểm:

- Không có nhiều writer đồng thời trên cùng mutable object.
- Invariant nhiều bước có thể diễn đạt tuần tự.
- Giảm nhu cầu khóa fine-grained ở mỗi field.

Nhược điểm:

- Một worker có thể thành bottleneck nếu tải cao.
- Queue và shutdown cần kiểm soát để không mất request.
- Caller không được bypass owner bằng cách gọi value.setXXX trực tiếp.
- Nếu nhiều process/JVM cùng thao tác DB, owner trong một JVM không đủ.

**ConcurrentHashMap có thể vẫn dùng làm registry tra cứu owner**, nhưng correctness của mutable state do **ownership protocol** quyết định.

## 21. Performance: thread-safe không có nghĩa luôn nhanh

Có nhiều cách đúng đắn nhưng trade-off khác nhau:

- **AtomicInteger** có thể chịu contention khi mọi thread cùng tăng một key nóng.
- **LongAdder** thường cải thiện throughput cho thống kê hot key, đổi lại sum đang cập nhật không tuyến tính.
- **compute + immutable record** dễ giữ snapshot nhất quán nhưng tạo object mới và có thể làm nhiều update cùng key tuần tự.
- **synchronized value** đơn giản cho critical section rõ ràng, nhưng giữ khóa lâu gây contention.
- **Copy-on-write list** thuận tiện cho đọc nhiều, ghi ít; thêm một item vào list lớn có thể cần copy O(n).
- **Nested concurrent collections** có overhead riêng và không tự bảo vệ compound operation.

Khi benchmark:

1. Xác định invariant và kiểm chứng kết quả trước.
2. Đo trên cả **hot key** và **nhiều key phân tán**.
3. Tách CPU-bound operation khỏi blocking I/O.
4. Warm up JVM và chạy nhiều lần, tránh benchmark một lần.
5. Đo throughput, p95/p99 latency, contention, allocations/GC và memory.
6. Cân nhắc **JMH** cho microbenchmark quan trọng, tránh dead-code elimination.
7. Đánh giá độ phức tạp và khả năng review code, không chỉ tốc độ tối đa.

**Một giải pháp nhanh nhưng đọc snapshot sai vẫn không hợp lệ.**

## 22. Kiểm thử concurrency đúng cách

**Test logic, không test xác suất.**

### Dùng CountDownLatch để dàn lịch lost update

Ví dụ 1 đảm bảo cả hai thread đã đọc cùng giá trị cũ trước khi được phép ghi. Đây là phép chứng minh rõ ràng rằng map-level thread safety không giải quyết lost update.

### Dùng join/Future để kiểm tra kết quả cuối

Ví dụ 2–6 chỉ in sau khi toàn bộ writer kết thúc. Vì vậy stdout thể hiện một trạng thái terminal đã biết, tránh phỏng đoán thời điểm thread scheduler chạy.

### Stress test và model checking

Stress test với số luồng lớn, nhiều key và nhiều lần chạy có thể giúp phát hiện bug, nhưng **không chứng minh code luôn đúng**. Phải kết hợp lập luận theo:

- Mọi đường **read/write** đối với value.
- **Happens-before** và visibility của các update.
- Tính nguyên tử của **toàn bộ thao tác nghiệp vụ**.
- Trường hợp **remove/replace/reset** cùng lúc.
- Deep immutability và API value reference escaping.
- Quy tắc exception/retry/cancellation và tính idempotent của side effect.

### Các phép thử bổ sung nên có

- Hai thread cùng compute cho một key.
- Một thread đọc snapshot trong lúc một thread khác update.
- Một thread remove/replace trong lúc người khác giữ reference cũ.
- Nhiều thread vừa gọi computeIfAbsent vừa tăng counter.
- Duyệt map trong lúc writer thay mappings: chỉ assert contract weakly consistent, không assert snapshot ổn định.
- Nested list/map và phần tử mutable.
- Compound invariant nhiều key.
- ExecutionException/InterruptedException và cleanup trên đường lỗi nếu ứng dụng có task bất đồng bộ.

## 23. Anti-pattern tóm tắt

| Anti-pattern | Vì sao sai | Hướng khắc phục |
| --- | --- | --- |
| map.get(key).count++ | Field của V không atomic | AtomicInteger hoặc synchronized value |
| map.get(key).list.add(x) với ArrayList | ArrayList không thread-safe | Concurrent collection hoặc immutable copy-on-write |
| get → mutate → put | Mutation xảy ra ngoài map atomic operation | compute với immutable replacement hoặc lock phù hợp |
| get null → put(new Counter()) | Hai thread có thể tạo/hủy mất reference | computeIfAbsent/putIfAbsent |
| compute rồi mutate same value ngoài compute | Internal map synchronization không bảo vệ external references | Encapsulate mutation, immutable value, cùng lock |
| record chứa ArrayList thì cho là immutable | Record chỉ shallow immutable | Defensive copy và phần tử immutable |
| Map value là LongAdder thì reset an toàn tự động | Old reference có thể được tăng sau replace | Generation/epoch/reset protocol |
| size() rồi put để giới hạn quota | Chuỗi check–then–act không atomic | Admission control/permit riêng |
| Hai compute trên hai key là transaction | Có trạng thái trung gian nhiều key | Lock/aggregate/DB transaction |
| Iterator của CHM là snapshot giao dịch | Weakly consistent | Giao thức snapshot phù hợp |
| Trả mutable value trực tiếp cho caller | Caller có thể bypass mọi invariant | Trả snapshot/operation thay vì live mutable |
| equals của value dựa vào field sửa đồng thời | Điều kiện replace/remove khó bảo đảm | Immutable value, version và equality semantics ổn định |
| Tưởng volatile int++ an toàn | Visibility không tạo read-modify-write atomic | Atomic increment hoặc lock |
| Dùng synchronized getter rồi writer không khóa | Giao thức reader/writer không đồng nhất | Tất cả đường truy cập phải dùng cùng lock |
| Đổi mọi thứ sang concurrent collection | Compound invariant vẫn có thể sai | Chọn atomic unit đúng theo nghiệp vụ |

## 24. Checklist review code

**Trạng thái và ownership:**

- [ ] Value có immutable thật không hay chỉ final reference?
- [ ] Có nested object mutable nào lộ ra ngoài không?
- [ ] Caller có giữ reference live value và sửa trực tiếp không?
- [ ] Key có immutable equals/hashCode ổn định không?
- [ ] Mapping có thể remove/replace/reset trong lúc value đang được dùng không?

**Đồng bộ hóa:**

- [ ] Mỗi phép increment/check–then–act có đơn vị atomic đúng không?
- [ ] Có chuỗi thao tác trên nhiều field cần cùng critical section không?
- [ ] Nếu dùng synchronized/ReentrantLock, mọi reader/writer đều dùng cùng object lock?
- [ ] Nếu dùng compute, callback có ngắn, không recursive update hoặc I/O chờ dài?
- [ ] Nếu dùng LongAdder, caller có hiểu sum() đang cập nhật không phải snapshot nguyên tử?
- [ ] Nếu dùng immutable snapshot, reader chỉ get một reference cho các field cùng phiên bản?
- [ ] Nếu dùng collection thread-safe bên trong V, có compound operation nào vẫn cần khóa không?

**Nghiệp vụ và vận hành:**

- [ ] Có invariant trải nhiều key hoặc nhiều JVM/DB không?
- [ ] Có chính sách generation/version khi reset?
- [ ] Có phân biệt current mapping với old reference còn được người khác giữ?
- [ ] Có stress test, deterministic concurrency test và xử lý exception?
- [ ] Đã đo throughput và contention dưới workload hot-key thực tế?
- [ ] API không hứa điều gì CHM không cung cấp, chẳng hạn snapshot toàn map hay transaction nhiều key?

## 25. Câu hỏi tự kiểm tra

**1. ConcurrentHashMap<String, Counter> có làm Counter.count++ an toàn không?**  
Không. Map bảo vệ mapping; phép tăng trên field int mutable cần cơ chế riêng.

**2. Hai thread lấy cùng value rồi gọi AtomicInteger.incrementAndGet() có đúng không?**  
Có, nếu cùng AtomicInteger và chỉ cần increment nguyên tử trên object ấy. Nhưng remove/replace mapping có thể khiến increment trên old reference không còn được phản ánh ở map hiện hành.

**3. compute(key, ...) có bảo đảm mọi sửa đổi trên value object ngoài callback cũng được khóa không?**  
Không. Nó phối hợp mapping theo key trong lời gọi compute, không cưỡng chế những thread có reference value phải dùng cùng đồng bộ hóa.

**4. LongAdder.sum() có phải atomic snapshot lúc nhiều thread đang increment không?**  
Không. Đọc tổng sau khi các writer đã kết thúc thì phù hợp để lấy kết quả cuối.

**5. Với hai field available và reserved phải cùng thay đổi, có nên dùng hai AtomicInteger riêng không?**  
Nếu yêu cầu reader thấy invariant nhất quán, không nên cập nhật tách rời mà không có giao thức bổ sung. Dùng immutable record + compute hoặc lock bao phủ cả hai.

**6. List.copyOf(list) có làm object phần tử bên trong list immutable không?**  
Không. Nó không deep-copy mutable elements; cần immutable element hoặc defensive copy phù hợp.

**7. Vì sao đọc map.get(key) hai lần để lấy hai field có thể sai?**  
Mapping có thể bị thay giữa hai lần đọc, làm kết hợp hai phiên bản. Hãy lấy snapshot immutable một lần.

**8. Dùng computeIfAbsent rồi get() của AtomicInteger có giải quyết reset concurrent không?**  
Không tự động. Thread có thể cầm old value rồi cập nhật sau khi mapping thay thế.

**9. Hai compute cho hai key có tạo multi-key transaction không?**  
Không. Reader có thể thấy trạng thái trung gian; cần giao thức cấp cao hơn.

**10. SynchronizedList làm việc iterate hoàn toàn tự động thread-safe không?**  
Từng method được wrapper đồng bộ, nhưng iteration cần đồng bộ ngoài theo hợp đồng để tránh các cập nhật xen giữa.

**11. Đọc toàn bộ ConcurrentHashMap bằng forEach có phải ảnh chụp tại một thời điểm không?**  
Không. Duyệt là weakly consistent và có thể thấy một phần các cập nhật đồng thời.

**12. Khi nào nên ưu tiên immutable value?**  
Khi muốn readers lấy snapshot nhất quán dễ kiểm chứng và updates có thể diễn đạt bằng thay mapping nguyên khối qua compute/merge.

**13. Vì sao put sau khi mutate không làm mutation trở nên atomic?**  
Vì mutation đã xảy ra trên object trước put và có thể tranh với thread khác; put chỉ cập nhật mapping.

**14. Nếu một key có hot counter lớn, nên luôn dùng LongAdder không?**  
Không. Nếu cần CAS/check-quota/linearizable get-and-increment hoặc reset chính xác, AtomicLong hoặc giao thức khác có thể đúng hơn.

**15. Điều gì quan trọng hơn việc chọn class concurrent?**  
Xác định rõ bất biến, đơn vị atomic, quyền sở hữu mutable state, memory consistency và các đường đọc/ghi.

## 26. Tổng kết

**ConcurrentHashMap chỉ giải quyết thread safety ở cấp map/mapping**, không tự biến Counter, ArrayList, nested HashMap hay object graph đặt trong value thành thread-safe. Một lời gọi map.get có thể an toàn, nhưng code tiếp tục **sửa object trả về** vẫn cần hợp đồng riêng.

Các lựa chọn phổ biến:

1. **AtomicInteger/AtomicLong** cho một giá trị có read-modify-write nguyên tử.
2. **LongAdder** cho thống kê cập nhật nhiều, chấp nhận giới hạn snapshot.
3. **Immutable record + compute/merge** để cập nhật trọn invariant trong một key.
4. **synchronized/ReentrantLock** để bảo vệ mutable value theo cùng giao thức đọc/ghi.
5. **Immutable copy-on-write hoặc concurrent collection** cho nested collection, phù hợp với mẫu tải.
6. **Owner/transaction cấp cao hơn** khi invariant trải nhiều key hoặc nhiều process.

Hãy luôn kiểm tra **get → mutate → put**, object reference escaping, remove/replace/reset, shallow immutability, weakly consistent iteration và side effect bên ngoài. **Sự an toàn của collection bên ngoài không thể thay thế thiết kế đúng cho trạng thái mutable bên trong value.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-015">CopyOnWriteArraySet và tính chất snapshot của iterator khi có ghi</a>

<details>
<summary>Xem chi tiết</summary>

# CopyOnWriteArraySet và tính chất snapshot của iterator khi có ghi trong Java

## 1. Bài toán: đọc Set trong khi thread khác thêm hoặc xóa

Một ứng dụng Java có thể giữ tập hợp các listener, subscriber, policy đang hoạt động hoặc một nhóm giá trị cấu hình. Một thread cần **duyệt tất cả phần tử**, trong khi thread khác có thể **đăng ký mới hoặc gỡ bỏ** một phần tử.

Nếu dùng `HashSet` và sửa đồng thời không có đồng bộ, chương trình không thread-safe. Nếu duyệt một `HashSet`/`ArrayList` thông thường mà có thay đổi cấu trúc sau khi iterator được tạo, iterator **có thể** ném `ConcurrentModificationException` theo cơ chế fail-fast. Đó không phải công cụ đồng bộ và không phải cách đáng tin để phát hiện mọi race.

Java cung cấp `java.util.concurrent.CopyOnWriteArraySet<E>` cho những tình huống **đọc/duyệt thường xuyên, ghi ít**, nơi caller chấp nhận rằng một lượt duyệt nhìn thấy **ảnh chụp tập phần tử tại thời điểm tạo iterator**, thay vì những thay đổi mới nhất.

Ý tưởng:

```text
Thời điểm t0: Set hiện tại = [A, B, C]
                       |
               reader tạo iterator I
                       |
                       v
          I giữ tham chiếu tới snapshot [A, B, C]

Thời điểm t1: writer add(D)
             Set hiện tại = [A, B, C, D]
             I vẫn duyệt    [A, B, C]

Thời điểm t2: writer remove(B)
             Set hiện tại = [A, C, D]
             I vẫn duyệt    [A, B, C]

Thời điểm t3: reader tạo iterator J
             J duyệt        [A, C, D]
```

**Điểm cốt lõi:** iterator được tạo ở t0 không phải “kết quả truy vấn sống” tự thay đổi ở t1, t2. Nó là **snapshot iterator**.

Bài này thuộc **Concurrent Collections** của **Java concurrency nói chung**, sử dụng API Java 17+, không phụ thuộc framework.

## 2. CopyOnWriteArraySet là gì?

`CopyOnWriteArraySet<E>` là một tập hợp `Set` được triển khai dựa trên `CopyOnWriteArrayList`.

Các đặc điểm chính:

1. **Không chứa hai phần tử bằng nhau theo `equals()`.** Nếu thêm một phần tử bằng phần tử đã có, `add` trả `false`.
2. **Thread-safe** cho các thao tác công khai của collection khi nhiều thread thêm, xóa, đọc hoặc duyệt theo hợp đồng của lớp.
3. Các thao tác ghi làm thay đổi tập phần tử thường phải tạo **mảng backing mới** (copy-on-write), thay vì sửa mảng backing mà iterator cũ đang tham chiếu.
4. Iterator giữ snapshot cấu trúc tập phần tử **tại thời điểm iterator được tạo**. Những lần ghi sau không đổi danh sách phần tử mà iterator cũ sẽ duyệt.
5. Iterator không hỗ trợ `remove()`, `set()` hoặc `add()` như một số kiểu iterator/list iterator khác; cố gọi thao tác sửa qua iterator có thể ném `UnsupportedOperationException`.
6. `CopyOnWriteArraySet` **cho phép phần tử `null`**, khác `ConcurrentHashMap.newKeySet()`.
7. Không cung cấp thứ tự sắp xếp theo comparator. Các phần tử được duyệt theo thứ tự lưu trong backing list — thường phản ánh thứ tự thêm phần tử — **không phải theo thứ tự từ điển hay hash**.
8. Vì phải tìm phần tử bằng cách so sánh tuyến tính, `contains`, tìm phần tử trùng và nhiều phép Set có chi phí phụ thuộc số phần tử; không nên coi đây là hash set có tra cứu `O(1)`.

Nên hiểu đúng tên:

- **Copy:** thao tác ghi cần copy backing array khi thực sự thay đổi nội dung.
- **On write:** phép đọc và việc tạo iterator thường không cần copy toàn bộ mảng.
- **ArraySet:** đặc tính Set được duy trì trên một cấu trúc giống mảng/list, không phải bảng băm.

Không có nghĩa mỗi lời gọi `add` chắc chắn tạo mảng mới: ví dụ thêm phần tử **đã tồn tại** không cần tạo một bản sao chứa dữ liệu y hệt. Trọng tâm là **khi nội dung tập thay đổi, thay mảng backing thay vì sửa mảng cũ đang được các iterator tham chiếu**.

## 3. Cơ chế mảng backing được công bố an toàn

Một cách hình dung cấp khái niệm:

```text
Reader I ----> backing array v1: [A, B]

Writer add(C):
  tạo mảng mới v2: [A, B, C]
  công bố v2 là backing array hiện hành

Reader I ----> v1: [A, B]           (không đổi)
Reader J ----> v2: [A, B, C]        (được tạo sau update)
```

Trong `CopyOnWriteArrayList` bên dưới, mảng hiện hành được công bố qua cơ chế đồng bộ/visibility thích hợp; các phép ghi được phối hợp để không làm mất thay đổi cấu trúc. Iterator lấy tham chiếu tới **mảng hiện hành tại lúc khởi tạo** và giữ tham chiếu đó cho đến khi iterator không còn được sử dụng.

Hệ quả:

- Reader có thể duyệt mảng snapshot mà không cần giữ một monitor khóa suốt cả vòng lặp.
- Writer có thể thay đổi collection hiện hành mà không chỉnh sửa mảng snapshot của reader.
- Iterator cũ **không cần restart** và **không ném `ConcurrentModificationException` chỉ vì Set được sửa**.
- Sau khi writer công bố mảng mới, iterator **được tạo sau đó** có thể nhìn thấy tập mới theo hợp đồng của collection.
- Mảng cũ vẫn có thể được giữ trong bộ nhớ khi iterator cũ còn tham chiếu tới nó.

Đây không phải cơ chế “copy cho từng reader”. Một iterator mới thường chỉ **chụp tham chiếu tới mảng hiện hành**, nên tạo iterator/duyệt nhanh khi không có nhiều ghi. Chi phí copy chủ yếu xuất hiện ở phía **ghi làm thay đổi cấu trúc**.

## 4. Ví dụ 1: snapshot được tạo trước khi ghi

Tạo tệp `CopyOnWriteSnapshotBasicsDemo.java`:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public class CopyOnWriteSnapshotBasicsDemo {
    static List<String> collect(Iterator<String> iterator) {
        List<String> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    public static void main(String[] args) {
        CopyOnWriteArraySet<String> set =
                new CopyOnWriteArraySet<>(
                        List.of("A", "B", "C"));

        Iterator<String> oldIterator = set.iterator();

        set.add("D");
        set.remove("B");
        set.add("E");

        System.out.println("old snapshot="
                + collect(oldIterator));
        System.out.println("current="
                + new ArrayList<>(set));
        System.out.println("new snapshot="
                + collect(set.iterator()));
    }
}
```

Biên dịch/chạy trên **JDK 17+**:

```bash
javac CopyOnWriteSnapshotBasicsDemo.java
java CopyOnWriteSnapshotBasicsDemo
```

Kết quả:

```text
old snapshot=[A, B, C]
current=[A, C, D, E]
new snapshot=[A, C, D, E]
```

Tại thời điểm gọi `set.iterator()` đầu tiên, tập có A, B, C. Sau đó:

- `add("D")` công bố tập có D.
- `remove("B")` gỡ B khỏi tập hiện hành.
- `add("E")` thêm E.
- Nhưng `oldIterator` vẫn giữ snapshot của tập cũ nên **vẫn trả B**, đồng thời **không trả D hay E**.
- Iterator mới được tạo sau các thay đổi duyệt tập hiện tại.

**Một hiểu nhầm:** gọi `oldIterator.next()` **lần đầu sau khi writer đã ghi** không có nghĩa nó sẽ đọc Set hiện hành. Snapshot được cố định khi gọi **`iterator()`**, không phải khi gọi `next()`.

## 5. Ví dụ 2: thật sự có một writer thread

Một ví dụ một thread có thể minh họa hợp đồng, nhưng để hiểu đúng concurrency, hãy tạo iterator **trước khi một thread khác cập nhật**, rồi dùng `Thread.join()` để biết writer đã hoàn tất.

Tạo tệp `ConcurrentSnapshotDemo.java`:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;

public class ConcurrentSnapshotDemo {
    static List<String> readAll(Iterator<String> iterator) {
        List<String> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    public static void main(String[] args) throws InterruptedException {
        CopyOnWriteArraySet<String> set =
                new CopyOnWriteArraySet<>(
                        List.of("alpha", "beta"));

        Iterator<String> beforeWrite = set.iterator();
        CountDownLatch start = new CountDownLatch(1);

        Thread writer = new Thread(() -> {
            try {
                start.await();
                set.add("gamma");
                set.remove("alpha");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "writer");

        writer.start();
        start.countDown();
        writer.join();

        System.out.println("before write="
                + readAll(beforeWrite));
        System.out.println("after write="
                + readAll(set.iterator()));
    }
}
```

```bash
javac ConcurrentSnapshotDemo.java
java ConcurrentSnapshotDemo
```

Kết quả khi không có interrupt ngoài ý muốn:

```text
before write=[alpha, beta]
after write=[beta, gamma]
```

Điểm quan trọng:

- Main tạo `beforeWrite` trước khi writer bắt đầu `add/remove`.
- Writer thay collection; main đợi `join()` rồi quan sát tập hiện hành.
- Iterator cũ vẫn trả phần tử của snapshot đã giữ.
- Hai lượt duyệt là hai **phiên bản có chủ đích**, không phải kết quả ngẫu nhiên phụ thuộc lịch chạy.

Nếu bỏ `writer.join()` và tạo iterator mới đồng thời với các cập nhật, iterator mới có thể nhìn thấy phiên bản trước, giữa hoặc sau chuỗi ghi, tùy thời điểm tạo. **Mỗi iterator vẫn nhất quán với đúng một phiên bản của backing array**, nhưng không được kỳ vọng nó luôn là phiên bản mới nhất khi nhiều writer đang tiếp tục hoạt động.

## 6. Iterator snapshot khác iterator fail-fast như thế nào?

Ba nhóm hành vi dễ nhầm:

| Kiểu collection/iterator | Khi collection bị sửa sau khi tạo iterator | Điều phải lưu ý |
| --- | --- | --- |
| `HashSet`, `ArrayList` thông thường | Iterator thường fail-fast và **có thể** ném `ConcurrentModificationException` | Fail-fast là best-effort, không làm collection thread-safe |
| `CopyOnWriteArraySet` | Iterator duyệt **snapshot cố định**; không bị thay đổi bởi ghi sau đó | Có thể thấy phần tử đã bị gỡ, không thấy phần tử mới |
| `ConcurrentHashMap.newKeySet()` | Iterator **weakly consistent**; có thể thấy một số thay đổi đồng thời | Không phải snapshot bất biến của một thời điểm |
| `Collections.synchronizedSet(...)` | Cần **đồng bộ bên ngoài** khi duyệt iterator theo tài liệu API | Không tự cho iterator snapshot không cần khóa |

**Không dùng `ConcurrentModificationException` để điều khiển chương trình.** Một iterator fail-fast phát hiện sửa đổi ở mức best-effort; sự vắng mặt của exception không chứng minh code đọc/ghi đồng thời là an toàn.

### Snapshot vs weakly consistent

Snapshot iterator của `CopyOnWriteArraySet` có tập phần tử rõ tại **lúc tạo iterator**. Nếu writer thêm X sau thời điểm ấy, snapshot cũ không có X.

Iterator weakly consistent như iterator của một số concurrent collection khác có thể phản ánh **một phần** thay đổi khi duyệt. Nó hữu ích cho các workload có nhiều writer, nhưng không đưa ra cam kết “chính xác tất cả phần tử tại thời điểm iterator khởi tạo”.

Hãy chọn semantics dựa trên nhu cầu của caller:

- Muốn **mỗi lượt xử lý có danh sách đối tượng cố định** → snapshot iterator.
- Muốn xử lý một lượng dữ liệu lớn, ghi thường xuyên, chấp nhận iteration không phản ánh toàn cục tức thời → concurrent key set / collection thích hợp.
- Muốn snapshot thật sự của nhiều cấu trúc liên quan → có thể cần một giao thức đồng bộ/đóng băng dữ liệu riêng; không phải chỉ chọn iterator bất kỳ.

## 7. Iterator cũ không cho phép `remove()`

Điểm khác một số iterator của collection thường: iterator của `CopyOnWriteArraySet` **không hỗ trợ chỉnh collection thông qua iterator**. Nếu muốn xóa một phần tử, gọi phương thức trên **Set**, không gọi `iterator.remove()`.

Tạo tệp `SnapshotIteratorRemoveDemo.java`:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public class SnapshotIteratorRemoveDemo {
    public static void main(String[] args) {
        CopyOnWriteArraySet<String> set =
                new CopyOnWriteArraySet<>(List.of("one", "two"));

        Iterator<String> iterator = set.iterator();
        System.out.println("first=" + iterator.next());

        try {
            iterator.remove();
        } catch (UnsupportedOperationException e) {
            System.out.println("iterator.remove unsupported=true");
        }

        System.out.println("after iterator.remove="
                + new ArrayList<>(set));

        set.remove("one");
        System.out.println("after set.remove="
                + new ArrayList<>(set));
    }
}
```

```bash
javac SnapshotIteratorRemoveDemo.java
java SnapshotIteratorRemoveDemo
```

Output:

```text
first=one
iterator.remove unsupported=true
after iterator.remove=[one, two]
after set.remove=[two]
```

`iterator.remove()` ném `UnsupportedOperationException`. Trong khi đó `set.remove("one")` là thao tác hợp lệ, có thể tạo mảng backing mới. Iterator cũ không bị thay đổi vì việc xóa qua Set này.

Trong những trường hợp cần lọc nhiều phần tử, có thể dùng phương thức hỗ trợ của collection như `removeIf` hoặc một chiến lược thay toàn bộ snapshot. Nhưng hãy cân nhắc chi phí copy/write và cách ứng dụng muốn điều phối thay đổi, thay vì gọi `remove` từng phần tử trên một Set lớn có nhiều writer.

## 8. Snapshot giữ cấu trúc tập phần tử, không deep-copy đối tượng

Đây là một ranh giới rất quan trọng.

`CopyOnWriteArraySet` snapshot **mảng tham chiếu phần tử**. Nó không tự clone từng object. Nếu một phần tử là mutable object, cả iterator cũ và collection hiện hành vẫn có thể giữ **cùng tham chiếu** đến object đó.

```text
snapshot array v1: [ reference -> Item(id=1, name="old") ]
current  array v2: [ reference -> Item(id=1, name="old"), Item2 ]

Nếu Item.name bị sửa thành "new":
snapshot v1 vẫn chứa Item(id=1), nhưng quan sát Item.name có thể là "new".
```

Vì vậy:

- Snapshot bảo vệ **thành viên của Set ở một thời điểm**.
- Snapshot **không bảo vệ trạng thái mutable bên trong mỗi thành viên**.
- Nếu object được sửa từ thread khác mà không có đồng bộ, vẫn có thể có **data race/visibility problem** ở fields của object đó.
- Muốn view thực sự bất biến, ưu tiên element immutable hoặc bản sao sâu phù hợp, và công bố chúng an toàn.

### Ví dụ 3: mutable element thay đổi cả trong snapshot cũ

Tạo tệp `MutableElementSnapshotDemo.java`:

```java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public class MutableElementSnapshotDemo {
    static final class Item {
        final int id;
        String name;

        Item(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Item item && id == item.id;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }

        @Override
        public String toString() {
            return id + ":" + name;
        }
    }

    static List<String> describe(Iterator<Item> iterator) {
        List<String> result = new ArrayList<>();
        iterator.forEachRemaining(
                item -> result.add(item.toString()));
        return result;
    }

    public static void main(String[] args) {
        Item first = new Item(1, "old");
        CopyOnWriteArraySet<Item> set =
                new CopyOnWriteArraySet<>();
        set.add(first);

        Iterator<Item> snapshot = set.iterator();

        first.name = "new";
        set.add(new Item(2, "second"));

        System.out.println("old snapshot=" + describe(snapshot));
        System.out.println("current=" + describe(set.iterator()));
    }
}
```

```bash
javac MutableElementSnapshotDemo.java
java MutableElementSnapshotDemo
```

Output:

```text
old snapshot=[1:new]
current=[1:new, 2:second]
```

Ví dụ này sửa field `name` **tuần tự trong một thread**, nên không có race về visibility. Nó cho thấy snapshot cũ vẫn giữ cùng object `first`, vì vậy `name` có thể đổi từ `old` thành `new` ngay trong lúc membership snapshot không đổi.

Ở hệ thống nhiều thread, nếu thật sự cần sửa name đồng thời, phải có `volatile`, lock, immutable replacement hoặc cơ chế đồng bộ phù hợp. **Không lấy copy-on-write của Set làm bảo đảm an toàn cho mọi trường của Item.**

## 9. Tính duy nhất theo equals(), không phải theo hash bucket

`CopyOnWriteArraySet` tuân thủ semantics của `Set`: các phần tử trùng nhau theo `equals()` không được thêm lần thứ hai. Không giống `HashSet`, nó không dựa vào bảng băm để thực hiện mọi lookup, mà kiểm tra phần tử trong backing array/list.

Do đó:

- `new String("Java")` và một `String` có cùng nội dung đã lưu thường được coi là phần tử trùng.
- Thứ tự phần tử không phải thứ tự của `hashCode()`.
- `contains(element)` có chi phí tuyến tính theo số phần tử trong trường hợp chung.
- Nếu `equals()` đắt, các lần add/contains cũng có thể đắt.
- Nếu `equals()` không ổn định vì các trường mutable bị sửa, logic duy nhất của Set có thể không còn đúng như mong muốn.

### Ví dụ 4: duplicate, null và thứ tự duyệt

Tạo tệp `CopyOnWriteSetEqualityDemo.java`:

```java
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public class CopyOnWriteSetEqualityDemo {
    public static void main(String[] args) {
        CopyOnWriteArraySet<String> set =
                new CopyOnWriteArraySet<>();

        boolean first = set.add("apple");
        boolean duplicate = set.add(new String("apple"));
        boolean nullFirst = set.add(null);
        boolean nullAgain = set.add(null);

        System.out.println("first=" + first);
        System.out.println("duplicate=" + duplicate);
        System.out.println("null first=" + nullFirst);
        System.out.println("null again=" + nullAgain);
        System.out.println("elements=" + new ArrayList<>(set));
    }
}
```

```bash
javac CopyOnWriteSetEqualityDemo.java
java CopyOnWriteSetEqualityDemo
```

Output:

```text
first=true
duplicate=false
null first=true
null again=false
elements=[apple, null]
```

Hai `String` khác instance nhưng bằng nhau theo `equals()`, nên lần thêm thứ hai trả false. `null` được cho phép **một lần** trong `CopyOnWriteArraySet`. Điều này khác `ConcurrentHashMap.newKeySet()`, vì Map bên dưới không cho key null.

Không suy ra rằng mọi concurrent Set đều cho phép null. Luôn xem **contract của implementation được chọn**, đặc biệt khi chuyển từ `CopyOnWriteArraySet` sang một concurrent set khác.

## 10. Mutation của phần tử ảnh hưởng tới equals()

Một loại lỗi khó hơn deep-snapshot issue: khi một phần tử `Item` sử dụng **trường mutable trong `equals()`**, việc sửa trường đó có thể làm thay đổi quan hệ bằng nhau đối với các phần tử khác.

Ví dụ lớp có `equals()` dựa vào `name`, đưa `Item("A")` vào Set rồi sửa `name` thành `"B"`:

- Kết quả `contains(new Item("A"))` có thể chuyển từ true thành false.
- `contains(new Item("B"))` có thể chuyển từ false thành true.
- Nếu đã có một phần tử khác `Item("B")`, Set có thể rơi vào trạng thái có hai object hiện **bằng nhau** về logic dù được thêm hợp lệ ở thời điểm khác nhau.
- Nếu thread khác sửa trường đó không đồng bộ, kiểm tra equality còn có data race.

**Khuyến nghị:** dùng phần tử có định danh/equality **ổn định** khi nằm trong Set. Với record chỉ chứa `String`, primitive hoặc value object bất biến, việc giữ ngữ nghĩa `equals()` ổn định thường đơn giản hơn.

Cẩn trọng với record chứa `List`/array mutable: record không tự bảo đảm **deep immutability** của toàn bộ object graph. Nếu element thay đổi về nội dung, snapshot chỉ còn là ảnh chụp **tham chiếu**.

## 11. Ví dụ thực tế: danh sách listener/event subscriber

Một tập listener nhỏ, ít đăng ký/hủy đăng ký nhưng được duyệt rất nhiều lần là tình huống phù hợp với copy-on-write.

Giả sử một hệ thống nhận sự kiện và mỗi lần nhận cần gọi những listener **thuộc snapshot của lượt dispatch đó**. Nếu listener A gỡ B và thêm C ngay trong lúc dispatch, lựa chọn semantics rõ ràng là:

- Lượt hiện tại vẫn xử lý A, B theo snapshot cũ.
- C chỉ xuất hiện ở **lượt dispatch kế tiếp**.
- Việc B được xóa khỏi Set **không hủy callback đang nằm trong snapshot**.

### Ví dụ 5: thay đổi Set ngay trong một vòng duyệt

Tạo tệp `ListenerSnapshotDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

public class ListenerSnapshotDemo {
    public static void main(String[] args) {
        CopyOnWriteArraySet<String> listeners =
                new CopyOnWriteArraySet<>(List.of("A", "B"));

        List<String> firstRound = new ArrayList<>();

        for (String listener : listeners) {
            firstRound.add(listener);
            if (listener.equals("A")) {
                listeners.remove("B");
                listeners.add("C");
            }
        }

        List<String> secondRound = new ArrayList<>();
        for (String listener : listeners) {
            secondRound.add(listener);
        }

        System.out.println("first round=" + firstRound);
        System.out.println("second round=" + secondRound);
    }
}
```

```bash
javac ListenerSnapshotDemo.java
java ListenerSnapshotDemo
```

Output:

```text
first round=[A, B]
second round=[A, C]
```

Vòng `for-each` đầu tiên tạo iterator một lần khi bắt đầu. Dù code trong vòng xóa B và thêm C, iterator của vòng này vẫn duyệt snapshot `[A, B]`. Vòng thứ hai lấy snapshot mới nên duyệt `[A, C]`.

Đây là một lợi thế lớn khi listener có quyền đăng ký/hủy đăng ký trong callback mà bạn không muốn giữ khóa trong suốt quá trình gọi người đăng ký.

### Giới hạn của pattern listener

- `unsubscribe(B)` **không đảm bảo B không còn được gọi ở lượt dispatch đã bắt đầu**.
- Nếu phải đảm bảo “xóa xong thì chắc chắn không có callback B nào đang chạy”, cần thêm giao thức quản lý **in-flight callbacks**, state/cancel/acknowledgment. Copy-on-write Set riêng lẻ không cung cấp cam kết đó.
- Nếu callback ném exception, phải quyết định có tiếp tục các listener khác không; Set không tự xử lý exception.
- Một callback chạy lâu làm vòng dispatch chậm; Set chỉ giải quyết cách **duyệt và cập nhật membership**, không lên lịch callback.
- Nếu listener giữ resource ngoài JVM (socket, file), việc xóa khỏi Set không tự đóng resource đó.

## 12. Snapshot iterator và điểm nhìn “mới nhất”

Một iterator cung cấp **tập phần tử đã tồn tại tại thời điểm tạo**. Nó không phải API lấy “tập mới nhất bất kể writer đang làm gì”.

Có hai trường hợp khác nhau:

**A. Writer đã hoàn tất và được phối hợp rõ:** nếu thread A thực hiện các update, thread B đợi A bằng `join()`/latch rồi mới gọi `set.iterator()`, B lấy snapshot của tập hiện hành **sau chuỗi update đó** (giả sử không có writer thứ ba cạnh tranh).

**B. Reader tạo iterator đồng thời với writer:** thứ tự giữa việc chụp tham chiếu mảng và việc công bố mảng mới quyết định iterator nhìn thấy phiên bản trước hay sau. Không được dự đoán dựa trên `Thread.sleep(...)` hoặc tên thread.

**C. Nhiều writer liên tục:** iterator tạo tại một điểm có thể nhìn thấy một phiên bản trung gian giữa các thao tác add/remove riêng rẽ. Ví dụ cập nhật A rồi B không trở thành một giao dịch nguyên tử chung chỉ vì sử dụng CopyOnWriteArraySet.

Lời khuyên: nếu nhiều thay đổi phải xuất hiện **cùng một lúc theo invariant nghiệp vụ**, có thể xây một immutable snapshot toàn bộ mới và công bố nó qua một `AtomicReference` hoặc áp dụng một giao thức khóa/transaction phù hợp. Nhiều lời gọi `set.add` / `set.remove` liên tiếp vẫn là **nhiều** thay đổi riêng.

## 13. Độ phức tạp thời gian và bộ nhớ

Một bảng chi phí xấp xỉ với tập có `n` phần tử:

| Thao tác | Chi phí thường gặp | Lý do |
| --- | --- | --- |
| `iterator()` | `O(1)` để nhận tham chiếu snapshot | Không copy toàn mảng khi chỉ tạo iterator |
| Duyệt toàn bộ `n` phần tử | `O(n)` | Đi qua từng phần tử của snapshot |
| `contains(x)` | `O(n)` | Tìm tuyến tính bằng equality |
| `add(x)` khi chưa có | `O(n)` | Kiểm tra trùng và tạo/copy backing array mới |
| `add(x)` khi đã có | `O(n)` worst-case cho tìm kiếm | Không cần thêm/copy để cài phần tử mới |
| `remove(x)` khi có | `O(n)` | Tìm phần tử rồi tạo array mới thiếu phần tử |
| `size()` | Thường `O(1)` | Dựa vào độ dài mảng hiện hành |
| Mỗi iterator còn sống | Giữ tham chiếu tới mảng snapshot | Có thể trì hoãn GC của các mảng cũ |

Bảng mô tả hình thái cơ bản, không phải cam kết cứng về từng nano giây hoặc mọi implementation/JDK. Các phép toán bulk và tối ưu nội bộ có thể có chi phí khác.

**Về bộ nhớ:** nếu Set có `n` tham chiếu, một lần thay đổi membership thường cần tạo backing array `O(n)`. Nếu nhiều iterator cũ còn sống, nhiều phiên bản array có thể cùng tồn tại trong heap. Các element object có thể được nhiều array version tham chiếu, nhưng nếu một element đã bị xóa khỏi Set hiện hành mà vẫn nằm trong snapshot cũ, nó **vẫn được giữ sống** cho tới khi snapshot/iterator không còn tham chiếu.

Không nhầm “copy mảng tham chiếu” với “clone tất cả element object”: chi phí cấp phát array là đáng kể nhưng không phải tạo bản sao sâu của mọi object.

## 14. Khi ghi thường xuyên, chi phí copy tích lũy

Giả sử có `n = 100_000` phần tử và mỗi giây thêm/xóa hàng chục nghìn phần tử. Với copy-on-write:

- Mỗi thay đổi thành viên có thể copy một mảng hàng chục/hàng trăm nghìn tham chiếu.
- Nhiều phiên bản mảng tạm làm tăng lượng allocation và áp lực GC.
- Writer phải phối hợp trong cơ chế nội bộ, không phải các thao tác ghi nào cũng chạy đồng thời độc lập.
- Nếu ghi liên tục, lợi ích reader không cần khóa có thể không bù chi phí writer.

Đây là lý do `CopyOnWriteArraySet` thường phù hợp:

- Ít phần tử hoặc kích thước vừa phải.
- **Đọc/duyệt nhiều, ghi rất ít**.
- Mỗi lượt duyệt muốn membership ổn định.
- Ưu tiên tính đơn giản của iteration và chấp nhận eventual visibility theo lượt snapshot.

Và thường **không phù hợp**:

- Cập nhật membership liên tục với tần suất cao.
- Set rất lớn và nhiều writer.
- Bài toán cần độ trễ thấp nhất cho mỗi lần add/remove.
- Workload cần một “live view” phản ánh phần tử mới ngay trong lượt duyệt hiện tại.

Không nên kết luận chỉ từ tên “concurrent” rằng collection này phù hợp cho mọi tình huống nhiều thread.

## 15. So sánh với ConcurrentHashMap.newKeySet()

Một lựa chọn khác là:

```java
Set<String> names = ConcurrentHashMap.newKeySet();
```

Đây là đoạn minh họa, cần import `java.util.Set` và `java.util.concurrent.ConcurrentHashMap`.

### Khác biệt chính

| Tiêu chí | `CopyOnWriteArraySet` | `ConcurrentHashMap.newKeySet()` |
| --- | --- | --- |
| Cấu trúc nền | Mảng copy-on-write | Bảng băm concurrent |
| Tìm kiếm `contains` | Tuyến tính theo số phần tử | Thường hiệu quả gần hằng số, phụ thuộc phân bố hash |
| Thêm/xóa thường xuyên | Tốn copy mảng, không lý tưởng cho n lớn | Thường thích hợp hơn cho workload update nhiều |
| Iterator | **Snapshot**, cố định membership tại lúc tạo | **Weakly consistent**, có thể phản ánh một số thay đổi |
| Thứ tự duyệt | Theo backing list/thứ tự thêm trong tình huống thông thường | Không đảm bảo thứ tự |
| `null` | **Cho phép** một phần tử null | **Không cho phép** null |
| Chi phí đọc/duyệt lặp lại | Không cần khóa dài của reader; snapshot ổn định | Iterator ít overhead của full copying, nhưng semantics khác |
| Phù hợp điển hình | Listener/config nhỏ, đọc nhiều ghi ít | Set nhiều key, truy cập/ghi đồng thời với số lượng lớn |

Không nói `ConcurrentHashMap.newKeySet()` “luôn nhanh hơn”. Với Set rất nhỏ, việc phải hash/đi qua cấu trúc khác có thể không mang lợi ích rõ; và nếu bắt buộc có snapshot cố định, dùng weakly consistent iterator có thể đòi thêm cơ chế snapshot riêng.

Vấn đề không chỉ hiệu năng mà là **ngữ nghĩa lặp (iteration semantics)**: cần thấy tập ở thời điểm bắt đầu hay một tập có thể phản ánh cập nhật đồng thời?

## 16. So sánh với Collections.synchronizedSet()

Có thể tạo:

```java
Set<String> set =
        Collections.synchronizedSet(new HashSet<>());
```

Đây là đoạn minh họa, cần import `java.util.*`. Wrapper cung cấp đồng bộ cho nhiều phép toán của Set; tuy nhiên **iterator không tự có snapshot semantics**. Tài liệu Java yêu cầu đồng bộ thủ công khi duyệt iterator của synchronized collection:

```java
synchronized (set) {
    for (String value : set) {
        use(value);
    }
}
```

Đây là đoạn minh họa, `use` là hàm ứng dụng. Việc dùng monitor của `set` suốt vòng duyệt chỉ đúng khi **mọi đường sửa qua cùng wrapper và cùng giao thức đồng bộ**. Nếu caller giữ iterator nhưng rời synchronized block sớm, hoặc có code bypass wrapper để sửa HashSet gốc, bảo đảm bị phá.

Khác biệt vận hành:

- `synchronizedSet` có thể khiến writer **đợi đến khi vòng duyệt dài hoàn thành** vì reader giữ monitor.
- `CopyOnWriteArraySet` cho phép writer thay backing array trong khi reader vẫn duyệt snapshot cũ, không cần khóa suốt lượt duyệt.
- `synchronizedSet` thường tránh copy toàn mảng cho từng lần add/remove, nhưng có overhead locking.
- Cả hai có thể đúng; hãy chọn theo tần suất đọc/ghi, thời gian duyệt, yêu cầu snapshot và mức contention.

Nếu `use(value)` gọi I/O chậm bên trong synchronized block, writer có thể chờ lâu. Đây là một lý do mẫu listener thường thích snapshot iterator.

## 17. So sánh với ConcurrentSkipListSet

`ConcurrentSkipListSet` cung cấp một concurrent sorted/navigable set, thường hữu ích khi ứng dụng yêu cầu:

- Duyệt phần tử **theo comparator/thứ tự tự nhiên**.
- Tìm phần tử gần nhất (`lower`, `higher`, `floor`, `ceiling`).
- Xử lý các range view có thứ tự.

Nó dùng cấu trúc skip list đồng thời, không phải backing array copy-on-write. Iterator của nó thuộc loại **weakly consistent**, khác snapshot iterator của `CopyOnWriteArraySet`; thông thường không cho phép phần tử null.

Nếu bài toán cần “danh sách listener theo thứ tự đăng ký” và ít thay đổi, `CopyOnWriteArraySet` có thể tiện. Nếu bài toán cần “tất cả key tăng dần và thao tác range” dưới concurrent updates, `ConcurrentSkipListSet` thường phù hợp hơn.

Đừng chọn `CopyOnWriteArraySet` rồi kỳ vọng `sort()` hay comparator có tác dụng tự động lên Set — nó không phải `SortedSet`.

## 18. Quy tắc về `add`, `remove` và return value

`CopyOnWriteArraySet` vẫn tuân theo các hợp đồng `Set` cơ bản:

- `add(e)` trả `true` nếu Set thay đổi vì thêm phần tử chưa có.
- `add(e)` trả `false` khi đã có phần tử bằng e.
- `remove(e)` trả `true` nếu có và xóa được phần tử.
- `remove(e)` trả `false` nếu không có phần tử phù hợp.
- `contains(e)` kiểm tra membership tại một thời điểm của collection.
- `size()` cho kích thước tập hiện hành lúc gọi.

Có thể dùng return value của `add` để tránh thực hiện side effect ở caller thua **trong một cuộc đua đăng ký**:

```java
if (listeners.add(listener)) {
    reportNewRegistration(listener);
}
```

Đây là đoạn minh họa. Nhưng hãy hiểu giới hạn: việc `add` và việc báo cáo là **hai thao tác riêng**. Nếu thread khác `remove`/`add` cùng listener ở giữa, hoặc báo cáo thất bại, không thể suy ra exactly-once side effect trong mọi tình huống.

Nếu nghiệp vụ cần exactly-once registration ngoài JVM, hãy định nghĩa ID, transaction/idempotency và protocol phù hợp; copy-on-write chỉ xử lý membership trong một Set tại JVM.

## 19. Mảng snapshot cũ có thể giữ object đã bị xóa

Điểm thường bị bỏ qua ở production là **retained memory**. Giả sử:

1. Có Set chứa 10.000 listener object.
2. Thread A tạo iterator rồi giữ nó trong một tác vụ chờ rất lâu.
3. Thread B gỡ 9.000 listener.
4. Set hiện hành chỉ còn 1.000 listener, nhưng iterator của A vẫn giữ **mảng snapshot với 10.000 tham chiếu cũ**.
5. Các listener đã gỡ có thể **chưa được GC** nếu còn được snapshot cũ giữ và không có tham chiếu khác.

Vì vậy:

- Tránh giữ iterator/snapshot quá lâu mà không cần thiết.
- Tránh cho iterator escape khỏi scope/lifecycle dự kiến khi nó chứa tham chiếu tới object lớn.
- Không mặc định `set.remove(e)` đồng nghĩa object `e` lập tức có thể thu gom.
- Nếu có rất nhiều iterator sống đồng thời cùng những lần ghi lớn, allocation và retained heap có thể tăng.

Đây là tradeoff của snapshot semantics. Nó không phải memory leak vô hạn trong mọi trường hợp; khi các iterator cũ không còn được tham chiếu, mảng cũ và element đã bị gỡ có thể được GC theo các điều kiện còn lại.

## 20. Kết hợp CopyOnWriteArraySet với immutable element

Một pattern đơn giản và đáng tin:

```java
record Subscriber(String id, String channel) {}
CopyOnWriteArraySet<Subscriber> subscribers =
        new CopyOnWriteArraySet<>();
```

Đây là đoạn minh họa. `Subscriber` chứa hai `String` bất biến, record component là final, nên từng element không bị sửa tại chỗ.

Khi muốn đổi channel:

```java
Subscriber oldValue = new Subscriber("u-1", "news");
Subscriber newValue = new Subscriber("u-1", "alerts");

subscribers.remove(oldValue);
subscribers.add(newValue);
```

Đây là đoạn minh họa **không phải transaction**: giữa remove và add, observer có thể thấy tạm thời không có old/new, còn snapshot cũ vẫn giữ old. Nếu membership phải chuyển **nguyên tử qua nhiều bước**, không nên kỳ vọng hai lời gọi Set là một thao tác duy nhất; có thể thay cả immutable aggregate qua một `AtomicReference` hoặc dùng cơ chế điều phối khác.

**Bẫy về equals:** record `Subscriber(id, channel)` so sánh cả id và channel. Nếu nghiệp vụ xem **id là định danh duy nhất**, hai record cùng id nhưng khác channel không bằng nhau, nên Set có thể chứa cả hai. Cần chọn mô hình định danh phù hợp: custom equals/hashCode chỉ theo id, Map theo id, hoặc một value aggregate mà key là id.

Đây là một lỗi semantic, không phải race condition: collection chỉ có thể giữ tính duy nhất theo **equals() mà element định nghĩa**, không theo quy tắc nghiệp vụ nằm ngoài equals.

## 21. Thread safety và Java Memory Model

`CopyOnWriteArraySet` thuộc `java.util.concurrent` và sử dụng cơ chế công bố backing array an toàn. Các hành động trong thread trước khi đặt một object vào concurrent collection có quan hệ visibility phù hợp đối với thread khác sau khi lấy/quan sát object đó qua collection, theo hợp đồng memory consistency của các concurrent collection.

Thực tế:

- Một thread xây xong phần tử rồi `set.add(item)`.
- Thread khác tạo iterator mới, lấy đúng `item` đó từ snapshot.
- Thread đọc có cơ sở visibility để thấy những state đã khởi tạo **trước khi phần tử được công bố**.

**Không suy rộng thành bảo đảm deep immutable**:

- Nếu writer tiếp tục sửa `item.name` sau khi đã add, phải có đồng bộ riêng cho state mutable.
- Nếu một phần tử chứa `List` mutable, việc copy array của Set không copy cấu trúc list bên trong.
- Nếu một method đọc `set.contains(x)` rồi dùng state mutable của `x` không đồng bộ, Map/Set thread-safe không bảo vệ phép đọc fields đó.
- Một iterator cũ vẫn thấy member của snapshot cũ ngay cả khi Set hiện hành đã remove member.
- Các lần `add`/`remove` riêng biệt không tạo transaction nhiều phần tử.

Nên ưu tiên element immutable và thiết kế rõ ai sở hữu việc thay đổi membership.

## 22. “Snapshot” có phải snapshot của toàn ứng dụng không?

Không. Nó chỉ là snapshot cấu trúc của **chính CopyOnWriteArraySet đó**.

Ví dụ một service có:

```text
CopyOnWriteArraySet<Listener> listeners
ConcurrentHashMap<String, Config> configs
AtomicInteger version
```

Một lượt `listeners.iterator()` không tạo ảnh chụp nhất quán cùng thời điểm của `configs` hoặc `version`. Nếu một nghiệp vụ cần “listeners và configs cùng phiên bản”, phải thiết kế đơn vị snapshot ở tầng cao hơn, có thể bằng một immutable record chứa cả hai và công bố qua một reference duy nhất.

Đừng trộn:

- **Snapshot iterator:** tập reference element được cố định khi iterator hình thành.
- **Snapshot element:** mọi field/đồ thị bên trong element bất biến theo version.
- **Snapshot transaction:** nhiều cấu trúc dữ liệu được đọc/cập nhật theo một điểm nhất quán.

Chúng là ba khái niệm khác nhau. CopyOnWriteArraySet trực tiếp giải quyết khái niệm **đầu tiên**, hai khái niệm sau đòi hỏi thiết kế dữ liệu và protocol bổ sung.

## 23. Những lỗi thường gặp

### 23.1. Nghĩ iterator cũ nhìn thấy mọi `add` mới

Sai. Snapshot cố định khi `iterator()` được tạo. Để nhận membership mới nhất, tạo iterator mới ở thời điểm phù hợp.

### 23.2. Nghĩ `remove(e)` khiến iterator cũ không còn trả e

Sai. Iterator cũ vẫn có thể trả e nếu e có trong snapshot tại lúc tạo, dù Set hiện hành đã xóa e.

### 23.3. Gọi `iterator.remove()`

Không được hỗ trợ; ném `UnsupportedOperationException`. Xóa qua Set hoặc chọn cấu trúc khác nếu cần khả năng sửa iterator.

### 23.4. Mong iterator tự cung cấp deep-copy

Sai. Nó giữ tham chiếu phần tử. Element mutable có thể thay đổi nội dung ngay trong snapshot cũ.

### 23.5. Dùng CopyOnWriteArraySet cho hàng triệu phần tử với ghi liên tục

Có thể gây chi phí copy mảng, allocation và GC lớn. Xem xét concurrent hash-backed set, cấu trúc partition hoặc thiết kế khác.

### 23.6. Nghĩ `contains` có độ phức tạp O(1) như HashSet

CopyOnWriteArraySet dùng backing list, kiểm tra equality tuyến tính. Với Set lớn và nhiều lookup, chi phí có thể đáng kể.

### 23.7. Dùng key/element có equality mutable

Nếu element thay đổi trường dùng cho equals, tính duy nhất và membership có thể không còn phản ánh thiết kế nghiệp vụ.

### 23.8. Dùng `Collections.synchronizedSet` nhưng không synchronized suốt vòng iterator

Wrapper synchronized không tự làm iteration thành snapshot. Theo hợp đồng, phải giữ monitor phù hợp trong suốt thời gian duyệt.

### 23.9. Dùng weakly consistent iterator rồi tưởng có snapshot

`ConcurrentHashMap.newKeySet()` có iterator với semantics khác. Nó có thể phản ánh một số thay đổi trong lúc duyệt; không cam kết tập phần tử cố định từ lúc tạo.

### 23.10. Nghĩ hủy đăng ký listener lập tức dừng mọi callback

Không. Một dispatch đã chụp snapshot có thể tiếp tục gọi listener đã được remove. Cần protocol quản lý in-flight nếu yêu cầu.

### 23.11. Duy trì iterator quá lâu

Snapshot cũ giữ backing array và references, có thể làm retention object cũ lâu hơn mong muốn.

### 23.12. Nghĩ nhiều lần add/remove là một transaction

Mỗi thao tác có thể là một thay đổi nguyên tử riêng. Một reader tạo snapshot giữa hai lời gọi có thể thấy trạng thái trung gian.

### 23.13. Dùng `null` không kiểm tra contract giữa các Set

CopyOnWriteArraySet cho phép null, nhưng không phải concurrent set nào cũng cho phép, ví dụ `ConcurrentHashMap.newKeySet()` không cho phép null.

### 23.14. Bỏ qua ngoại lệ/timeout của callback

Iterator ổn định không đồng nghĩa callback trong vòng lặp luôn thành công. Nếu xử lý sự kiện lỗi, cần quyết định tiếp tục listener sau, dừng dispatch, retry hoặc ghi nhận outcome.

### 23.15. Benchmark chỉ có một lần chạy

Hiệu năng phụ thuộc kích thước Set, tỷ lệ đọc/ghi, số writer, phân bố element và thời gian sống iterator. Benchmark JVM cần warm-up và nhiều lần đo; không kết luận chỉ từ microbenchmark không kiểm soát.

## 24. Khi nào nên dùng CopyOnWriteArraySet?

**Rất phù hợp khi:**

- Tập listener/subscriber hoặc danh sách observer tương đối nhỏ.
- Vòng đọc/duyệt xảy ra cực kỳ thường xuyên.
- Đăng ký/hủy đăng ký ít xảy ra.
- Mỗi lượt dispatch muốn danh sách đối tượng **cố định từ đầu đến cuối**.
- Không muốn giữ một lock chung trong cả callback dài.
- Element bất biến hoặc có synchronization riêng cho state mutable.

**Nên cân nhắc cách khác khi:**

- Nhiều writer thay đổi Set liên tục hoặc số lượng phần tử rất lớn.
- Cần lookup `contains` rất nhanh trên tập có nhiều key.
- Muốn iterator có thể phản ánh một phần thay đổi đang xảy ra thay vì snapshot cũ.
- Cần thứ tự sắp xếp tự nhiên/comparator và range queries.
- Muốn thay đổi nhiều element cùng một transaction.
- Cần hỗ trợ xóa trực tiếp bằng iterator.

Một cách quyết định thực tế: xác định **read/write ratio**, kích thước n, thời gian duyệt, yêu cầu membership snapshot, độ trễ ghi có chấp nhận được và lượng bộ nhớ cho old snapshots. Chọn collection theo **semantics và workload**, không chỉ vì nó có tên “Concurrent”.

## 25. Câu hỏi tự kiểm tra

**Câu 1.** Một Set chứa A,B. Tạo iterator I. Sau đó add C, remove A. I sẽ duyệt gì?

**Đáp án:** I vẫn duyệt snapshot ban đầu A,B. Iterator mới được tạo sau cập nhật sẽ nhìn thấy B,C, giả sử không có writer khác.

**Câu 2.** Gọi `I.next()` lần đầu sau các thay đổi có làm I chụp snapshot mới không?

**Đáp án:** Không. Snapshot được cố định khi gọi `set.iterator()`, không phải khi gọi `next()`.

**Câu 3.** Tại sao iterator không ném ConcurrentModificationException chỉ vì writer add/remove?

**Đáp án:** Nó duyệt mảng snapshot không bị writer sửa; writer thay backing array mới. Không có thay đổi cấu trúc ngay trong mảng snapshot của iterator.

**Câu 4.** Có thể gọi `iterator.remove()` để xóa phần tử vừa duyệt không?

**Đáp án:** Không. Iterator của CopyOnWriteArraySet không hỗ trợ remove, sẽ ném UnsupportedOperationException.

**Câu 5.** Nếu một element mutable có `name` bị sửa sau khi iterator được tạo, snapshot có giữ được name cũ không?

**Đáp án:** Không. Snapshot giữ reference element, không deep-copy object. Nếu sửa đồng thời, còn cần đồng bộ cho field đó.

**Câu 6.** `CopyOnWriteArraySet` có cho phép null không?

**Đáp án:** Có. Set vẫn chỉ cho phép một phần tử null theo semantics của Set.

**Câu 7.** CopyOnWriteArraySet là một hash set có lookup O(1) không?

**Đáp án:** Không. Nó dựa trên CopyOnWriteArrayList; contains và kiểm tra equality nói chung cần tìm tuyến tính.

**Câu 8.** Vì sao chi phí add/remove có thể cao với Set lớn?

**Đáp án:** Khi thay đổi membership, writer thường phải tạo/copy backing array mới cỡ O(n), đồng thời có chi phí tìm phần tử.

**Câu 9.** Iterator của ConcurrentHashMap.newKeySet() giống snapshot iterator của CopyOnWriteArraySet không?

**Đáp án:** Không. Nó weakly consistent, có thể phản ánh một số thay đổi đồng thời, không chụp cố định membership ngay lúc tạo.

**Câu 10.** Một listener bị remove trong lúc dispatch đang duyệt snapshot. Có chắc nó không còn được gọi trong lượt đó không?

**Đáp án:** Không. Nếu listener đã có trong snapshot, lượt dispatch vẫn có thể gọi nó. Muốn bảo đảm hủy cả in-flight callback phải có protocol bổ sung.

**Câu 11.** Tại sao synchronizedSet cần synchronized thủ công khi duyệt?

**Đáp án:** Iterator không tự giữ monitor cho toàn vòng duyệt; nếu muốn ngăn concurrent structural modifications qua cùng wrapper, caller phải khóa theo hợp đồng của collection.

**Câu 12.** Snapshot có làm hai thao tác add(A) và remove(B) thành transaction không?

**Đáp án:** Không. Một iterator tạo giữa hai thao tác có thể thấy trạng thái trung gian.

**Câu 13.** Vì sao một iterator lâu sống có thể làm heap giữ phần tử đã xóa?

**Đáp án:** Nó còn giữ mảng snapshot cũ chứa reference tới các phần tử ấy; GC chỉ có thể thu hồi khi không còn tham chiếu sống thích hợp.

**Câu 14.** Có cần copy cả Set mỗi lần tạo iterator để được snapshot không?

**Đáp án:** Không. CopyOnWriteArraySet thường chụp reference tới backing array hiện hành; chi phí copy chủ yếu ở ghi thay đổi membership.

**Câu 15.** Nếu hai listener có cùng id nhưng khác channel và record equals xét cả hai field, Set có đảm bảo chỉ một listener theo id không?

**Đáp án:** Không. Tính duy nhất theo `equals()` của element, không theo id riêng. Cần thiết kế equality/key theo nghiệp vụ.

## 26. Tổng kết

**`CopyOnWriteArraySet` phù hợp với tập phần tử tương đối nhỏ, được đọc/duyệt rất nhiều và ít thay đổi.** Khi writer thêm hoặc xóa phần tử, collection có thể tạo backing array mới; iterator đã tồn tại tiếp tục duyệt **mảng snapshot cũ**, không phản ánh thay đổi mới và không bị `ConcurrentModificationException` do các thao tác ghi trên Set.

Những quy tắc cần nhớ:

1. **Snapshot xuất hiện tại `iterator()`**, không phải ở lần gọi `next()` đầu tiên.
2. Ghi sau đó **không thêm/xóa phần tử trong iterator cũ**.
3. `iterator.remove()` không được hỗ trợ; hãy sửa collection qua API của Set.
4. Snapshot chỉ giữ **tham chiếu thành viên**, **không deep-copy element** và không tự làm field mutable thread-safe.
5. Add/remove làm thay đổi membership thường tốn `O(n)` cho copy mảng; contains cũng tìm tuyến tính.
6. Iterator cũ có thể giữ mảng và object đã bị gỡ lâu hơn dự kiến; tránh giữ snapshot quá lâu.
7. So với `ConcurrentHashMap.newKeySet()` có iterator weakly consistent, CopyOnWriteArraySet trả **một danh sách membership cố định cho cả lượt duyệt**.
8. Khi nghiệp vụ cần in-flight cancellation, transaction nhiều phần tử hoặc snapshot nhiều cấu trúc, phải có giao thức ở tầng ứng dụng.

Một ví dụ sử dụng phù hợp là **listener registry**: rất nhiều lượt dispatch nhưng rất hiếm lượt đăng ký/hủy đăng ký. Khi hiểu rõ thời điểm chụp snapshot, giới hạn của mutable elements và chi phí mỗi lần ghi, bạn có thể dùng CopyOnWriteArraySet đúng như công cụ chuyên biệt của **Java Concurrent Collections**, thay vì coi nó là một Set concurrent “tốt nhất cho mọi trường hợp”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections-016">TransferQueue và BlockingDeque: chuyển giao trực tiếp và thao tác hai đầu</a>

<details>
<summary>Xem chi tiết</summary>

# TransferQueue và BlockingDeque: chuyển giao trực tiếp và thao tác hai đầu

Trong Java Concurrency, `BlockingQueue<E>` cho phép nhiều thread truyền dữ liệu qua một hàng đợi có cơ chế đồng bộ và chờ. Hai interface mở rộng giúp giải quyết những bài toán đặc biệt:

- **`TransferQueue<E>`** bổ sung khái niệm **chuyển giao tới consumer**: producer có thể chọn giữa đưa phần tử vào queue như thông thường và **đợi cho đến khi một consumer thực sự nhận phần tử đó**.
- **`BlockingDeque<E>`** (double-ended queue) bổ sung **hai đầu vào/ra**: producer và consumer có thể thêm/lấy từ đầu hoặc cuối, theo FIFO, LIFO, hoặc một giao thức ưu tiên đơn giản do ứng dụng quyết định.

Implementation thường dùng của JDK:

- `LinkedTransferQueue<E>`: concurrent queue không có capacity giới hạn cấu hình, triển khai `TransferQueue<E>`.
- `LinkedBlockingDeque<E>`: concurrent deque có thể **bounded** bằng capacity trong constructor; nếu không truyền, capacity theo API là `Integer.MAX_VALUE`.

Hai interface này **khác `SynchronousQueue`** và không thay thế mọi `BlockingQueue` khác. Muốn dùng đúng cần hiểu rõ **enqueue**, **dequeue**, **handoff**, **xử lý xong**, **backpressure**, **thứ tự thực thi** và **lifecycle**. Bài tập trung hoàn toàn vào Java/JDK concurrency, dùng ví dụ độc lập với Java 17+, không phụ thuộc framework bên ngoài.

## 1. Từ BlockingQueue đến hai cơ chế mở rộng

Xét pipeline producer–consumer đơn giản:

```text
Producer                  Queue                    Consumer
---------                 -----                    --------
tạo item  ------------>   lưu hoặc handoff  ---->  nhận item
                                                     |
                                                     v
                                               xử lý nghiệp vụ
```

Có **ba mốc khác nhau**:

1. **Đã gửi vào queue:** queue đã chấp nhận item, nhưng chưa chắc consumer nhận.
2. **Đã được consumer lấy ra:** consumer đã thực hiện thao tác nhận hợp lệ; chưa chắc xử lý xong.
3. **Đã xử lý xong:** consumer đã hoàn thành logic nghiệp vụ và cleanup tương ứng.

`TransferQueue.transfer(e)` hỗ trợ đợi tới **mốc thứ 2**, chứ **không tự tạo acknowledgement cho mốc thứ 3**. `BlockingDeque` không bổ sung acknowledgement; nó bổ sung **quyền chọn đầu nào** để gửi hoặc nhận.

Khi có nhiều producer/consumer, việc chọn đầu và thời điểm handoff là một phần của thiết kế đồng bộ. Những khái niệm này không nên được đồng nhất với “task Future đã hoàn tất” hoặc “transaction đã commit”.

## 2. TransferQueue là gì?

`TransferQueue<E>` **extends `BlockingQueue<E>`**. Nó giữ những phương thức quen thuộc như `offer`, `put`, `take`, `poll`, đồng thời bổ sung:

| API của TransferQueue | Ý nghĩa |
|---|---|
| `transfer(e)` | Chuyển phần tử tới một consumer, **chờ** cho đến khi consumer nhận. Có thể bị interrupt. |
| `tryTransfer(e)` | **Chỉ** chuyển trực tiếp nếu có consumer phù hợp đang đợi; không chờ. Trả `true` nếu chuyển được, `false` nếu chưa có ai nhận ngay. |
| `tryTransfer(e, timeout, unit)` | Đợi tối đa khoảng thời gian cho consumer nhận, trả `true` khi handoff, `false` nếu hết hạn. Có thể ném `InterruptedException`. |
| `hasWaitingConsumer()` | Trả thông tin consumer có thể đang chờ để nhận trực tiếp, **mang tính thời điểm**. |
| `getWaitingConsumerCount()` | Số lượng consumer đang đợi **ước lượng**, dùng để giám sát/heuristic, không để đảm bảo một giao dịch tiếp theo. |

Các method `take()` và `poll()` của `BlockingQueue` vẫn là phía **consumer**. Nếu producer gọi `transfer(e)` trong lúc chưa có consumer, nó có thể phải đợi và phần tử có thể được đăng ký trong queue để consumer lấy sau; khi consumer lấy, transfer mới có thể hoàn tất.

### offer(), put() và transfer() không tương đương

Một `LinkedTransferQueue<String>` có thể nhận phần tử bằng:

```java
queue.offer("event");      // Enqueue; không đợi consumer xử lý/lấy.
queue.put("event");        // Với LinkedTransferQueue, không chờ vì queue đầy.
queue.transfer("event");   // Đợi một consumer nhận phần tử.
```

Đây là trích đoạn cần khai báo `queue` và xử lý `InterruptedException`. Cả ba đưa dữ liệu tới consumer theo contract nhưng **cách producer biết lúc nào được tiếp tục là khác nhau**. Không đọc `transfer()` thành “đợi consumer xử lý xong”: consumer có thể đang tính toán rất lâu sau khi lấy phần tử.

### Điều kiện của tryTransfer(e)

`tryTransfer(e)` không timeout là một giao dịch **không chờ**. Nếu không có consumer có thể nhận ngay:

- Trả `false`.
- **Không lưu item lại trong queue**.
- Không tạo một lần gửi trì hoãn sẽ tự diễn ra khi consumer xuất hiện.

Đây là khác biệt lớn với `offer(e)` trên `LinkedTransferQueue`: `offer` có thể enqueue dù chưa có consumer, còn `tryTransfer` không chờ thì chỉ thành công nếu chuyển ngay được.

## 3. LinkedTransferQueue: unbounded, trực tiếp khi có thể

`LinkedTransferQueue<E>` là implementation thread-safe của `TransferQueue<E>`, hỗ trợ nhiều producer và consumer. Những tính chất quan trọng:

- **Không có constructor capacity** để đặt bound như `ArrayBlockingQueue<>(capacity)`.
- `remainingCapacity()` là `Integer.MAX_VALUE` theo hợp đồng queue không bounded; **không có nghĩa bộ nhớ vô hạn**.
- `offer()` và `put()` không bị chặn vì đầy capacity; nếu backlog tăng quá mức vẫn có nguy cơ thiếu bộ nhớ.
- `transfer()` cung cấp cơ chế **đợi consumer nhận**, là một hình thức đồng bộ khác với enqueue thông thường.
- `tryTransfer()` không chờ phù hợp với tình huống chỉ muốn gửi nếu consumer đang sẵn sàng.
- Các phép kiểm tra như `hasWaitingConsumer()`, `getWaitingConsumerCount()` và `size()` là quan sát **có thể thay đổi ngay** khi đọc; `size()` của queue liên kết đồng thời có thể tốn công duyệt, không nên dùng làm điều kiện tính đúng của giao dịch.
- Iterator thuộc nhóm **weakly consistent**: không ném `ConcurrentModificationException` chỉ vì update đồng thời, nhưng không nên kỳ vọng snapshot cố định khi các thread đang sửa queue.

**Không dùng** `if (queue.hasWaitingConsumer()) queue.tryTransfer(e)` như một bảo đảm không thất bại: consumer có thể bị đánh thức/nhận item khác trước lần `tryTransfer`. Nếu yêu cầu là “chuyển nếu có thể ngay”, chỉ cần tin vào **giá trị trả về của `tryTransfer`**.

## 4. Ví dụ 1 — tryTransfer() và transfer() khác nhau ra sao?

Chương trình đầu tiên thử `tryTransfer` lúc chưa có consumer; phép thử phải thất bại và không enqueue. Sau đó tạo một producer gọi `transfer()`, main trở thành consumer và nhận bằng `take()`.

Tạo file **`TransferQueueBasics.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class TransferQueueBasics {
    public static void main(String[] args) throws InterruptedException {
        TransferQueue<String> queue = new LinkedTransferQueue<>();
        System.out.println("Immediate transfer: "
                + queue.tryTransfer("not-sent"));
        System.out.println("Queue empty: " + queue.isEmpty());

        CountDownLatch producerEntered = new CountDownLatch(1);
        AtomicBoolean transferReturned = new AtomicBoolean();

        Thread producer = new Thread(() -> {
            producerEntered.countDown();
            try {
                queue.transfer("payload");
                transferReturned.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        producer.start();
        producerEntered.await();

        String received = queue.take();
        producer.join();

        System.out.println("Received: " + received);
        System.out.println("Transfer returned: "
                + transferReturned.get());
        System.out.println("Remaining: " + queue.size());
    }
}
```

```bash
javac TransferQueueBasics.java
java TransferQueueBasics
```

Kết quả trong lần chạy bình thường, không có external interrupt:

```text
Immediate transfer: false
Queue empty: true
Received: payload
Transfer returned: true
Remaining: 0
```

**Tại sao kết quả xác định?**

1. `tryTransfer("not-sent")` được gọi trước khi có bất kỳ consumer nào, vì vậy trả false và không giữ item lại.
2. Producer bắt đầu rồi gọi `transfer("payload")`. Main có thể gọi `take()` trước hay sau thời điểm producer thực sự đi vào transfer; cả hai trường hợp đều ghép cặp thành công khi hai phía có mặt.
3. `producer.join()` đợi producer kết thúc, nên `transferReturned` chắc chắn là true sau khi handoff thành công.
4. `size() == 0` vì item duy nhất đã được consumer lấy ra. Không có lời hứa rằng producer bị chặn bao nhiêu mili giây; lịch chạy thread là không xác định.

Ở hệ thống thực tế, producer nên có đường xử lý interruption và timeout nếu handoff có thể chờ lâu. Demo cố ý đơn giản để quan sát contract.

## 5. Timed transfer: giới hạn thời gian đợi consumer

Nếu không chắc consumer có xuất hiện, dùng:

```java
boolean accepted = queue.tryTransfer(
        message, 500, TimeUnit.MILLISECONDS);
```

Đây là trích đoạn, cần khai báo `queue`/`message` và bắt hoặc ném `InterruptedException`. Khi hết hạn chưa có consumer nhận, phương thức trả false. Nếu đã có handoff, trả true. Thời hạn này giới hạn **đợi consumer nhận**, không giới hạn thời gian consumer xử lý.

### Ví dụ 2 — Timed tryTransfer thất bại rồi thành công

Chương trình trước tiên đợi một thời gian rất ngắn trong khi **chưa tạo consumer**, nên chắc chắn không có người nhận. Sau đó khởi động consumer, dùng timed transfer để hai phía gặp nhau mà không phụ thuộc việc consumer đã gọi `take()` sớm hay muộn hơn producer.

Tạo file **`TransferQueueTimeout.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicReference;

public class TransferQueueTimeout {
    public static void main(String[] args) throws InterruptedException {
        TransferQueue<String> queue = new LinkedTransferQueue<>();

        boolean noConsumer = queue.tryTransfer(
                "ignored", 30, TimeUnit.MILLISECONDS);
        System.out.println("Transfer without consumer: "
                + noConsumer);

        CountDownLatch ready = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();

        Thread consumer = new Thread(() -> {
            ready.countDown();
            try {
                received.set(queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        consumer.start();
        ready.await();

        boolean transferred = queue.tryTransfer(
                "delivered", 5, TimeUnit.SECONDS);
        consumer.join();

        System.out.println("Timed transfer succeeded: "
                + transferred);
        System.out.println("Consumer received: "
                + received.get());
        System.out.println("Queue empty: " + queue.isEmpty());
    }
}
```

```bash
javac TransferQueueTimeout.java
java TransferQueueTimeout
```

Kết quả trong lần chạy bình thường, consumer được lập lịch trong thời hạn năm giây:

```text
Transfer without consumer: false
Timed transfer succeeded: true
Consumer received: delivered
Queue empty: true
```

**Giải thích:** không có consumer nào khi gọi timed transfer đầu, nên nó trả false và không enqueue. Ở lượt hai, main được phép chờ tối đa năm giây cho consumer lấy item; `ready` giúp biết consumer đã khởi động, nhưng không tự chứng minh consumer đã đi vào `take()` đúng thời điểm. Timed transfer tự xử lý việc **đợi giao cặp**. Nếu consumer không được CPU trong thời hạn, phương thức vẫn có thể trả false, vì timeout **không bảo đảm phía khác chạy đúng hẹn**; đây là lý do ví dụ ghi rõ điều kiện scheduling.

Không dựa vào `Thread.sleep()` để “canh consumer đã chờ” rồi gọi `tryTransfer()` không timeout. Việc đó tạo test race. Nếu cần chắc chắn chuyển khi consumer đến, dùng `transfer()` hoặc timed variant với xử lý timeout.

## 6. Handoff không đồng nghĩa business completion

Một producer gọi `transfer(item)` có thể trở về ngay sau khi consumer **nhận** item, ngay cả khi consumer mới bắt đầu xử lý và chưa hoàn tất.

```text
Producer                         Consumer
--------                         --------
transfer(item) ----- handoff ---> take()
    |                                |
    v                                v
trở về                      process(item) ... còn chạy
```

Khi cần xác nhận consumer **đã xử lý xong**, phải bổ sung cơ chế acknowledgement như `CompletableFuture`, `CountDownLatch`, một Future riêng, thông điệp kết quả hoặc transaction ở tầng nghiệp vụ. Không đồng nhất queue transfer với hoàn thành giao dịch bên ngoài JVM.

### Ví dụ 3 — Transfer trả về trước khi consumer xử lý xong

Tạo file **`TransferIsNotProcessing.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TransferIsNotProcessing {
    public static void main(String[] args) throws InterruptedException {
        TransferQueue<String> queue = new LinkedTransferQueue<>();
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch transferReturned = new CountDownLatch(1);
        CountDownLatch finishProcessing = new CountDownLatch(1);
        AtomicReference<String> item = new AtomicReference<>();
        AtomicBoolean processed = new AtomicBoolean();

        Thread producer = new Thread(() -> {
            try {
                queue.transfer("job");
                transferReturned.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                item.set(queue.take());
                received.countDown();
                finishProcessing.await();
                processed.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();

        received.await();
        transferReturned.await();

        System.out.println("Received: " + item.get());
        System.out.println("Processed at handoff: "
                + processed.get());

        finishProcessing.countDown();
        producer.join();
        consumer.join();

        System.out.println("Processed after release: "
                + processed.get());
    }
}
```

```bash
javac TransferIsNotProcessing.java
java TransferIsNotProcessing
```

Đầu ra trong lần chạy bình thường:

```text
Received: job
Processed at handoff: false
Processed after release: true
```

Main đợi cả hai tín hiệu: consumer đã nhận và producer đã được `transfer()` cho trở về. Tuy nhiên, `finishProcessing` vẫn đóng, nên consumer **chưa thể đặt `processed=true`**. Sau khi main mở latch và `join()` consumer, xử lý mới được xác nhận hoàn tất. Bài học quan trọng: **hàng đợi xác nhận nhận item, không tự xác nhận xử lý nghiệp vụ**.

Trong production, nếu processing có thể thất bại, cần ghi trạng thái success/failure riêng thay vì một boolean. Nếu consumer đột ngột chết sau khi nhận item, producer đã có thể hoàn thành transfer mà công việc vẫn chưa hoàn tất; cần retry/acknowledgement theo hợp đồng hệ thống.

## 7. TransferQueue khác SynchronousQueue ở đâu?

Cả hai đều có khái niệm producer gặp consumer, nhưng **không giống nhau**.

| Đặc điểm | `LinkedTransferQueue` | `SynchronousQueue` |
|---|---|---|
| Có thể chứa backlog? | **Có**, nếu dùng `offer()`/`put()` hoặc phần tử transfer đang chờ người nhận. | **Không**, capacity bằng 0. |
| `put(e)` | Chèn phần tử, không phải chờ consumer chỉ vì queue đầy. | Chờ consumer nhận trực tiếp. |
| `offer(e)` không timeout | Thường enqueue thành công nếu đủ tài nguyên. | Chỉ thành công nếu có consumer đối ứng ngay. |
| `transfer(e)` | Có; chờ consumer nhận. | Không có method tên `transfer`; `put(e)` tạo handoff. |
| `tryTransfer(e)` | Có; chỉ gửi nếu consumer đang đợi. | Không có method tên `tryTransfer`; `offer(e)` có semantics handoff tương tự ở mức điều kiện nhận ngay. |
| `remainingCapacity()` | `Integer.MAX_VALUE`. | 0. |
| Khi consumer chưa xuất hiện | `offer` có thể để lại backlog; `transfer` sẽ chờ. | `put` chờ, `offer` thất bại. |

**Chọn theo contract:** cần linh hoạt giữa enqueue bình thường và đợi consumer thật sự nhận → TransferQueue. Cần **cấm mọi backlog**, chỉ cho handoff ghép cặp → SynchronousQueue.

Không được xem việc queue chưa có người nhận là lý do tự động chọn TransferQueue: nếu ứng dụng cần giới hạn tài nguyên và không chấp nhận backlog vô hạn, phải tự đặt cơ chế backpressure hoặc chọn queue bounded khác.

## 8. BlockingDeque là gì?

`Deque<E>` (double-ended queue) cho phép thêm hoặc lấy phần tử từ **cả đầu và cuối**. `BlockingDeque<E>` **extends `BlockingQueue<E>` và `Deque<E>`**, bổ sung các phương thức có thể chờ khi đầu vào đầy/đầu ra rỗng.

Hình dung:

```text
                   LinkedBlockingDeque
       HEAD                                 TAIL
        |                                     |
putFirst(v) --> [urgent] [normal1] [normal2] <-- putLast(v)
takeFirst() <-- [urgent] [normal1] [normal2] --> takeLast()
        ^                                     ^
       đầu                                   cuối
```

Phía producer có thể thêm ở đầu/càng gần `HEAD` để những consumer lấy đầu nhận item sớm hơn. Hoặc một thread có thể dùng `push/pop` để mô phỏng LIFO stack. **Đây là quyền chọn thứ tự lấy trong deque, không phải cơ chế tự ưu tiên CPU của Java scheduler.**

## 9. Bảng API hai đầu của BlockingDeque

| Chức năng | Đầu (first/head) | Cuối (last/tail) |
|---|---|---|
| Thêm, báo lỗi khi đầy | `addFirst(e)` | `addLast(e)` |
| Thêm không chờ, trả boolean | `offerFirst(e)` | `offerLast(e)` |
| Thêm, chờ có chỗ | `putFirst(e)` | `putLast(e)` |
| Thêm, chờ tối đa timeout | `offerFirst(e, timeout, unit)` | `offerLast(e, timeout, unit)` |
| Lấy, báo lỗi khi rỗng | `removeFirst()` | `removeLast()` |
| Lấy không chờ, trả null khi rỗng | `pollFirst()` | `pollLast()` |
| Lấy, chờ có item | `takeFirst()` | `takeLast()` |
| Lấy, chờ tối đa timeout | `pollFirst(timeout, unit)` | `pollLast(timeout, unit)` |
| Xem, không xóa | `peekFirst()` | `peekLast()` |
| Xóa một occurrence | `removeFirstOccurrence(e)` | `removeLastOccurrence(e)` |

Hành vi trên các overload không chờ/timeout khác nhau khi queue **đầy** hoặc **rỗng**, giống quy ước của `BlockingQueue`. Với queue bounded, `offerFirst`/`offerLast` trả false khi đầy; `putFirst`/`putLast` chờ có chỗ; `takeFirst`/`takeLast` chờ có item.

### Phương thức kế thừa của BlockingQueue dùng đầu nào?

Khi dùng `BlockingDeque` như một `BlockingQueue`, các phương thức thông thường có quy ước:

- `add(e)`, `offer(e)`, `put(e)` tương ứng **thêm cuối** (`addLast/offerLast/putLast`).
- `remove()`, `poll()`, `take()` tương ứng **lấy đầu** (`removeFirst/pollFirst/takeFirst`).
- `element()`, `peek()` xem **đầu**.

Do đó dùng `put()` và `take()` thông thường trên deque sẽ cho đường FIFO theo thứ tự enqueue thành công, trong khi dùng `push()`/`pop()` sẽ tạo đường LIFO.

## 10. LinkedBlockingDeque: bounded, hai đầu và thread-safe

`LinkedBlockingDeque<E>` là implementation có các node liên kết, hỗ trợ nhiều thread đồng thời thêm/lấy ở hai đầu, với:

```java
BlockingDeque<String> bounded = new LinkedBlockingDeque<>(100);
BlockingDeque<String> defaultCapacity = new LinkedBlockingDeque<>();
```

Đây là trích đoạn, cần import `java.util.concurrent.BlockingDeque` và `LinkedBlockingDeque`.

- Constructor có `capacity` tạo **giới hạn số phần tử** (phải lớn hơn 0), áp dụng **chung cho cả hai đầu**.
- Constructor không tham số có capacity `Integer.MAX_VALUE` theo hợp đồng; không có nghĩa bộ nhớ vô hạn.
- Hai đầu **dùng chung một tổng capacity**; không phải mỗi đầu có capacity riêng.
- Không có built-in priority comparator cho deque; ứng dụng tự quyết định đầu nào được thêm và đầu nào được lấy.
- Không có fairness constructor tương đương `ArrayBlockingQueue(capacity, true)`; không được tự suy thứ tự ưu tiên công bằng giữa các producer/consumer đang chờ.
- `null` không được phép; `pollFirst/Last()` trả null để biểu thị không có phần tử.
- Iterator là weakly consistent đối với sửa đổi đồng thời, không được coi như snapshot bất biến.
- Không có hard guarantee mọi task chèn ở đầu sẽ **chạy xong trước** các task ở cuối: các consumer có thể đã lấy item khác hoặc đang xử lý.

Nếu dung lượng ngắn, deque có thể tạo backpressure ở cả đầu và cuối. Tuy nhiên, vẫn phải chọn rõ quyền ưu tiên giữa các loại công việc: **liên tục thêm ở đầu** có thể làm những item chờ ở cuối bị đói (*starvation*).

## 11. Ví dụ 4 — FIFO, LIFO và thao tác hai đầu

Dùng một thread để kiểm tra chính xác thứ tự: đặt “urgent” ở đầu, “normal” ở cuối, thử thêm khi capacity đầy, lấy từ hai phía, rồi dùng `push/pop` như stack.

Tạo file **`BlockingDequeBasics.java`**:

```java
import java.util.concurrent.LinkedBlockingDeque;

public class BlockingDequeBasics {
    public static void main(String[] args) throws InterruptedException {
        LinkedBlockingDeque<String> deque =
                new LinkedBlockingDeque<>(3);

        System.out.println("Add normal1: "
                + deque.offerLast("normal1"));
        System.out.println("Add urgent: "
                + deque.offerFirst("urgent"));
        System.out.println("Add normal2: "
                + deque.offerLast("normal2"));
        System.out.println("Add overflow: "
                + deque.offerFirst("overflow"));

        System.out.println("Take first: " + deque.takeFirst());
        System.out.println("Take last: " + deque.takeLast());
        System.out.println("Take remaining: " + deque.takeFirst());

        deque.push("one");
        deque.push("two");
        System.out.println("Pop 1: " + deque.pop());
        System.out.println("Pop 2: " + deque.pop());
        System.out.println("Remaining: " + deque.size());
    }
}
```

```bash
javac BlockingDequeBasics.java
java BlockingDequeBasics
```

Kết quả:

```text
Add normal1: true
Add urgent: true
Add normal2: true
Add overflow: false
Take first: urgent
Take last: normal2
Take remaining: normal1
Pop 1: two
Pop 2: one
Remaining: 0
```

**Giải thích:** sau ba lần `offer`, deque từ HEAD đến TAIL là `[urgent, normal1, normal2]`. Queue đã đầy nên `offerFirst("overflow")` không làm thay đổi thứ tự. `takeFirst` lấy urgent, `takeLast` lấy normal2, còn normal1 ở giữa. Sau đó `push("one")` đặt one ở đầu, `push("two")` đặt two phía trước one; `pop()` lấy từ đầu nên ra two rồi one.

LIFO ở đây là hành vi **deque được dùng như stack**, không phải API nào cũng đột nhiên trở thành LIFO. Nếu dùng `offerLast()` và `takeFirst()` thông thường, đường lấy là FIFO.

## 12. push()/pop() khác addFirst()/removeFirst() thế nào?

`Deque.push(e)` về ngữ nghĩa tương đương **`addFirst(e)`**, còn `pop()` tương đương **`removeFirst()`**. Các API này **không có tham số timeout**. Với `LinkedBlockingDeque` bounded, nếu đầy, `push`/ `addFirst` có thể ném `IllegalStateException`; nếu rỗng, `pop`/ `removeFirst` có thể ném `NoSuchElementException`.

Khi cần block và đợi có chỗ/dữ liệu, hãy dùng `putFirst()` và `takeFirst()`. Khi chỉ muốn thử một lần không chờ, dùng `offerFirst()` và `pollFirst()`.

Cần phân biệt:

```java
deque.push(item);       // Thêm đầu; nếu đầy có thể ném.
deque.offerFirst(item); // Thêm đầu; trả false nếu đầy.
deque.putFirst(item);   // Thêm đầu; có thể chờ nếu đầy.
```

Đây là trích đoạn với `deque` và `item` đã khai báo; `putFirst` có thể ném `InterruptedException`. Những lựa chọn này quan trọng khi producer không được block trên event loop hoặc request thread.

## 13. Ví dụ 5 — putFirst() chờ vì deque bounded đầy

Chương trình dùng deque capacity=1; main đặt “existing” vào trước. Producer cố `putFirst("urgent")` nhưng không có chỗ, nên phải đợi main lấy item cũ. Mục tiêu là minh họa **backpressure** ở đầu và cuối dùng chung capacity.

Tạo file **`BlockingDequeBackpressure.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockingDequeBackpressure {
    public static void main(String[] args) throws InterruptedException {
        LinkedBlockingDeque<String> deque =
                new LinkedBlockingDeque<>(1);

        deque.putLast("existing");
        CountDownLatch producerEntered = new CountDownLatch(1);
        AtomicBoolean producerFinished = new AtomicBoolean();

        Thread producer = new Thread(() -> {
            producerEntered.countDown();
            try {
                deque.putFirst("urgent");
                producerFinished.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        producer.start();
        producerEntered.await();

        String old = deque.takeLast();
        producer.join();
        String urgent = deque.takeFirst();

        System.out.println("Removed old: " + old);
        System.out.println("Received next: " + urgent);
        System.out.println("Producer finished: "
                + producerFinished.get());
        System.out.println("Queue empty: " + deque.isEmpty());
    }
}
```

```bash
javac BlockingDequeBackpressure.java
java BlockingDequeBackpressure
```

Kết quả trong lần chạy bình thường:

```text
Removed old: existing
Received next: urgent
Producer finished: true
Queue empty: true
```

**Tại sao?** Khi producer được start, deque vẫn đang chứa existing nên `putFirst("urgent")` không thể hoàn tất nếu item cũ chưa được lấy. Main gọi `takeLast()`, giải phóng slot; producer đưa urgent vào đầu và kết thúc, được `join()` xác nhận. Sau đó main lấy urgent và thấy queue rỗng.

`producerEntered` chỉ xác nhận producer vào trước lời gọi `putFirst`; nó **không được dùng làm bằng chứng producer đã chắc chắn bị block** tại đúng thời điểm main chờ. Tuy nhiên, invariant capacity=1 và item existing có sẵn bảo đảm urgent không thể thêm trước khi item existing được lấy, nên kết quả không dựa vào lịch chạy tình cờ.

## 14. BlockingDeque có phù hợp làm hàng đợi ưu tiên không?

Có thể dùng một deque để tách công việc mức ưu tiên đơn giản:

```java
if (urgent) {
    deque.putFirst(job);
} else {
    deque.putLast(job);
}

// Consumer ưu tiên đầu hàng:
Job next = deque.takeFirst();
```

Đây là trích đoạn, cần định nghĩa `deque`, `job`, `urgent` và xử lý `InterruptedException`. Tuy nhiên, việc này **không phải priority scheduling tổng quát**:

- Hai urgent mới đều `putFirst` nên urgent mới hơn có thể nằm **trước** urgent cũ hơn (LIFO trong nhóm urgent).
- Nếu liên tục có urgent, normal đã ở cuối có thể bị **starvation**.
- Consumer có thể đã lấy normal trước khi urgent đến; không có preemption.
- Nhiều consumer chạy song song có thể hoàn tất task khác thứ tự lấy.
- Không có comparator để sắp theo nhiều mức ưu tiên hoặc deadline.

Nếu cần **priority theo comparator**, cân nhắc `PriorityBlockingQueue`. Nếu muốn một số lượng slot **dành riêng** cho công việc khẩn cấp, một deque bounded với tổng capacity không tự cung cấp quota theo lớp công việc; cần semaphore, nhiều queue hoặc logic admission riêng.

## 15. Deque và work-stealing: giống bề ngoài nhưng không tương đương ForkJoinPool

Trong các thuật toán song song, một deque có thể lưu task theo mô hình:

- Worker ưu tiên xử lý công việc mới ở một đầu.
- Worker khác có thể lấy công việc từ đầu đối diện để cân bằng tải.

Đây là **ý tưởng của work-stealing deque**, nhưng **không có nghĩa `LinkedBlockingDeque` chính là cơ chế deque nội bộ của `ForkJoinPool`**. `ForkJoinPool` có cấu trúc hàng đợi và cơ chế scheduling riêng được tối ưu cho `ForkJoinTask`, không thể thay thế một-một bằng cách đặt `LinkedBlockingDeque<Runnable>` rồi suy ra cùng hiệu năng hoặc cùng quy tắc.

Khi tự làm một scheduler dùng BlockingDeque, phải thiết kế thêm:

1. Quyền sở hữu và trách nhiệm worker.
2. Chính sách lấy từ đầu/cuối.
3. Vòng đời queue, shutdown/cancellation và poison pill hoặc tín hiệu đóng.
4. Xử lý exception/timeout của task.
5. Điều phối starvation/fairness và giám sát backlog.
6. Hạn mức worker, memory và backpressure.

Nếu bài toán là đệ quy chia để trị CPU-bound, nên xem `ForkJoinPool` như một công cụ sẵn có thay vì tự tái tạo work-stealing engine từ BlockingDeque.

## 16. Deque trong ThreadPoolExecutor và điểm cần tránh

`ThreadPoolExecutor` nhận constructor có `BlockingQueue<Runnable>`. Vì `BlockingDeque<Runnable>` extends `BlockingQueue<Runnable>`, có thể dùng:

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
        2, 4, 30, TimeUnit.SECONDS,
        new LinkedBlockingDeque<Runnable>(100),
        new ThreadPoolExecutor.AbortPolicy()
);
```

Đây là trích đoạn, cần import `java.util.concurrent.*` và quản lý `shutdown()`. Tuy nhiên, **`execute()` của ThreadPoolExecutor theo contract bình thường dùng `workQueue.offer(command)`**, tức là thêm về cuối của BlockingDeque; worker lấy qua phương thức `take()`, tương ứng lấy từ đầu. Vì vậy cấu hình trên hoạt động như **FIFO bounded work queue thông thường**, không tự biến pool thành priority scheduler hai đầu.

**Không khuyến nghị trực tiếp gọi `executor.getQueue().putFirst(task)` để “ưu tiên task”**: thao tác trực tiếp trên queue bỏ qua một phần admission/lifecycle, kiểm tra trạng thái shutdown và chính sách rejection của `execute()`. Nếu cần priority scheduler cho executor, cần thiết kế API submit/admission có contract rõ ràng hoặc dùng cấu trúc/Executor phù hợp, không chỉ thêm task vào deque đang được executor quản lý.

Cũng cần nhớ: với queue bounded, khi số core workers đã đủ, pool thử enqueue; chỉ khi queue không nhận thêm được thì mới thử non-core worker tới maximum. Với unbounded deque, non-core worker có thể không được tạo dưới tải bình thường vì queue tiếp tục nhận task.

## 17. Cancellation, interrupt và shutdown

Cả hai loại queue đều có những thao tác có thể **chờ vô thời hạn**:

- `TransferQueue.transfer(e)`: đợi consumer nhận.
- `TransferQueue.tryTransfer(e, timeout, unit)`: đợi có giới hạn.
- `BlockingDeque.putFirst/putLast(e)`: đợi chỗ trống khi bounded deque đầy.
- `BlockingDeque.takeFirst/takeLast()`: đợi phần tử khi deque rỗng.
- `BlockingDeque.offerFirst/Last(e, timeout, unit)` và `pollFirst/Last(timeout, unit)`: đợi có giới hạn.

Các method chờ có thể ném `InterruptedException`. Worker nên:

- Chấp nhận/ném tiếp interruption khi method có hợp đồng đó.
- Nếu bắt exception để kết thúc mà không ném tiếp, thường khôi phục flag bằng `Thread.currentThread().interrupt()`.
- Không nuốt exception rồi gọi lại `transfer` hoặc `take` vô hạn.
- Đặt timeout khi caller không thể chờ vô hạn.
- Quy định rõ cleanup của resource do producer/consumer sở hữu.

**Một điểm khó:** nếu producer bị interrupt đúng lúc consumer cũng nhận một item, phải dựa vào hợp đồng giao dịch thực tế, không tự đoán kết quả của một thao tác cắt ngang luôn là “chắc chắn chưa gửi”. Với công việc có side effect, dùng ID/idempotency hoặc acknowledgement riêng nếu cần retry an toàn.

`TransferQueue` và `BlockingDeque` **không có phương thức `close()` thống nhất**. Để dừng worker, có thể dùng cancellation flag, interruption, sentinel/poison pill hoặc lifecycle của executor. Poison pill phải được thiết kế cho số consumer và thứ tự lấy phù hợp; với deque có hai đầu, chèn sentinel sai đầu có thể khiến consumer bỏ qua item cần xử lý hoặc dừng quá sớm.

## 18. JMM: công bố dữ liệu an toàn qua queue

Theo hợp đồng memory consistency của `BlockingQueue`, các hành động xảy ra **trước khi producer đưa một object vào queue** có quan hệ *happens-before* với các hành động của consumer **sau khi truy cập/lấy object đó ra** bằng queue. Điều này áp dụng cho các implementation đúng contract của `TransferQueue` và `BlockingDeque`.

Ví dụ minh họa:

```java
// Producer (trích đoạn):
Message message = new Message();
message.payload = 42;
queue.put(message);

// Consumer trong thread khác:
Message item = queue.take();
System.out.println(item.payload);
```

Đoạn này chưa độc lập, cần class `Message`, queue phù hợp và xử lý `InterruptedException`. Việc chuyển giao qua queue giúp consumer thấy trạng thái object đã chuẩn bị trước enqueue/handoff. Nhưng **không tự làm các mutation sau khi chuyển giao trở nên thread-safe**: producer tiếp tục sửa cùng object trong lúc consumer đọc vẫn cần đồng bộ hoặc ownership rõ ràng.

Đặc biệt:

- Với `LinkedTransferQueue`, `transfer(e)` chờ nhận không đồng nghĩa object đã immutable; tránh producer tiếp tục sửa khi consumer xử lý.
- Với `LinkedBlockingDeque`, hai đầu cùng chia sẻ collection được đồng bộ hóa, nhưng các object bên trong có thể vẫn mutable và cần giao thức riêng.
- Với các phép thử `hasWaitingConsumer()`, `size()`, `remainingCapacity()`, đừng dùng giá trị quan sát làm bước cấp phép duy nhất cho một hành động cạnh tranh. Chính `offer/tryTransfer/put/take` mới quyết định thành công tại thời điểm thao tác.

Nếu có kết quả xử lý cần báo về producer, dùng Future/latch/queue trả lời hoặc primitive đồng bộ khác; happens-before của việc **nhận input** không tự công bố **kết quả xử lý chưa xảy ra**.

## 19. Những lỗi thường gặp

| Lỗi | Vì sao sai? | Cách xử lý |
|---|---|---|
| Cho rằng `transfer(e)` nghĩa consumer xử lý xong. | Nó chỉ chờ consumer nhận item. | Dùng acknowledgement riêng cho business completion. |
| Gọi `tryTransfer(e)`, thấy false, nghĩ item sẽ được nhận sau. | Thất bại không enqueue. | Dùng `offer`, `transfer` hoặc timed variant theo yêu cầu. |
| Gọi `offer(e)` rồi tưởng đợi được consumer. | `offer` chỉ enqueue khi chấp nhận. | Dùng `transfer(e)` nếu cần đợi nhận. |
| Dùng `hasWaitingConsumer()` rồi cho rằng tryTransfer chắc chắn true. | State có thể thay đổi trước lời chuyển giao. | Dựa trên kết quả trực tiếp của tryTransfer. |
| Cho rằng LinkedTransferQueue có bound theo constructor. | Queue không có capacity giới hạn do người dùng đặt. | Giới hạn producer bằng semaphore/quota hoặc dùng queue bounded khác. |
| Dùng `size()` để quyết định admission chính xác. | Snapshot race và có thể tốn duyệt. | Dùng API nguyên tử hoặc giới hạn riêng. |
| Nhầm `BlockingDeque.put(e)` với thêm đầu. | Nó thêm **cuối**, giống `putLast`. | Dùng `putFirst` khi cần ưu tiên đầu. |
| Nghĩ `push()` chờ có chỗ khi deque đầy. | Push tương đương addFirst, có thể ném. | Dùng `putFirst` hoặc timed offerFirst. |
| Nghĩ `pop()` chờ khi deque rỗng. | Pop tương đương removeFirst, có thể ném. | Dùng `takeFirst` hoặc timed pollFirst. |
| Nghĩ `LinkedBlockingDeque<>(2)` có hai slot mỗi đầu. | Tổng capacity chỉ là hai. | Quản lý một capacity chung cho cả deque. |
| Dùng putFirst liên tục và tưởng mọi normal task sẽ tới lượt. | Task ở cuối có thể bị starvation. | Thiết kế quota/fairness/aging riêng. |
| Nghĩ task thêm đầu sẽ preempt task đã chạy. | Deque chỉ quyết định thứ tự lấy item chưa dequeue. | Không dựa vào queue để ngắt tác vụ đang chạy. |
| Trực tiếp thêm task vào `executor.getQueue()`. | Bỏ qua executor admission/rejection/lifecycle. | Dùng execute/submit hoặc scheduler thiết kế riêng. |
| Dùng deque làm work-stealing và cho rằng giống ForkJoinPool. | ForkJoinPool có cơ chế riêng, tối ưu cho ForkJoinTask. | Sử dụng đúng abstraction/concurrency model. |
| Nuốt InterruptedException và tiếp tục chờ mãi. | Hủy không được tôn trọng. | Ném tiếp hoặc restore interrupt rồi thoát. |
| Dừng consumer bằng sentinel không xét đầu lấy. | Có thể dừng trước khi xử lý item còn tồn. | Thiết kế rõ thứ tự/số sentinel và completion. |

## 20. Chọn API theo bài toán

| Nhu cầu | Lựa chọn cân nhắc | Vì sao |
|---|---|---|
| Producer muốn enqueue ngay, không cần consumer đã nhận. | `LinkedTransferQueue.offer(e)` hoặc queue phù hợp. | Không đợi handoff. |
| Producer muốn chờ tới khi consumer nhận, không cần xử lý xong. | `TransferQueue.transfer(e)`. | Chờ mốc receipt, có thể interrupt. |
| Chỉ gửi nếu hiện tại có consumer sẵn sàng. | `TransferQueue.tryTransfer(e)`. | Không chờ, không enqueue khi thất bại. |
| Chờ consumer tối đa N ms. | `tryTransfer(e, N, unit)`. | Handoff có timeout, phải xử lý false. |
| Cấm toàn bộ backlog, luôn handoff trực tiếp. | `SynchronousQueue`. | Capacity bằng 0, không có queue lưu. |
| Có backlog bounded, cần thêm/lấy ở hai đầu. | `LinkedBlockingDeque<>(capacity)`. | Hai đầu và backpressure. |
| Chỉ cần FIFO bounded. | `ArrayBlockingQueue` hoặc `LinkedBlockingQueue(capacity)`. | Đơn giản hơn deque khi không cần hai đầu. |
| Cần ưu tiên theo comparator. | `PriorityBlockingQueue` với comparator rõ. | Deque không tự sort nhiều priority. |
| Cần đợi task hoàn tất, biết thành công/lỗi. | `Future`, `CompletableFuture` hoặc acknowledgement riêng. | Queue transfer không đại diện kết quả xử lý. |

**Khác biệt cốt lõi:** TransferQueue giúp producer chọn **enqueue hay đợi consumer nhận**; BlockingDeque giúp producer/consumer chọn **đầu hay cuối**. Chúng giải quyết **hai chiều khác nhau** của bài toán concurrent coordination.

## 21. Checklist thiết kế và kiểm thử

1. **Xác định completion:** đã enqueue, đã nhận hay đã xử lý xong?
2. **Chọn giao thức transfer:** offer/put cho gửi thông thường, transfer/tryTransfer cho handoff.
3. **Có giới hạn chờ:** dùng timed transfer/timed deque operations hoặc cancellation khi có nguy cơ deadlock/liveness.
4. **Không suy state từ snapshot:** hasWaitingConsumer/size/remainingCapacity chỉ phục vụ quan sát.
5. **Giới hạn memory:** LinkedTransferQueue và default LinkedBlockingDeque có khả năng giữ backlog rất lớn; theo dõi arrival và service rate.
6. **Chọn đầu deque:** urgent ở first, normal ở last, consumer lấy first; hiểu LIFO trong nhóm urgent và starvation.
7. **Đặt capacity hợp lý:** bounded LinkedBlockingDeque có tổng capacity, không có quota mỗi đầu.
8. **Xác định đối tượng sở hữu:** ai enqueue, ai consume, ai dừng worker và ai cleanup?
9. **Không lặp retry vô hạn:** false/timeout/interruption cần đường xử lý rõ.
10. **Bảo vệ mutable payload:** immutable object/record hoặc giao thức ownership và đồng bộ sau handoff.
11. **Dùng lifecycle:** queue không có close chung; executor shutdown và chờ termination khi là owner.
12. **Kiểm thử có điều phối:** latch để tránh kết quả theo lịch chạy, kiểm tra timeout, cancel, queue đầy và consumer biến mất.
13. **Tách handoff khỏi acknowledgement:** consumer nhận không nghĩa đã thành công; nghiệp vụ quan trọng cần xác nhận riêng.
14. **Đừng lẫn với ForkJoin work-stealing:** hai đầu là primitive, không phải một scheduler hoàn chỉnh.
15. **Đo production:** queue depth, wait duration, backlog age, số transfer timeout, active consumers, throughput và GC.

## 22. Câu hỏi tự kiểm tra

1. **TransferQueue mở rộng interface nào?** `BlockingQueue<E>`.
2. **`transfer(e)` chờ điều gì?** Chờ có consumer nhận phần tử, không đợi xử lý xong.
3. **`tryTransfer(e)` false có enqueue item không?** Không.
4. **`tryTransfer(e, timeout, unit)` trả false khi nào?** Không có handoff trong thời hạn.
5. **`hasWaitingConsumer()` có bảo đảm lời tryTransfer kế tiếp thành công?** Không, vì trạng thái cạnh tranh thay đổi.
6. **LinkedTransferQueue có bounded capacity bằng constructor không?** Không.
7. **`offer(e)` trên LinkedTransferQueue có đợi consumer không?** Không; có thể enqueue.
8. **TransferQueue và SynchronousQueue có giống nhau không?** Không; TransferQueue có khả năng backlog với offer/put, SynchronousQueue capacity=0.
9. **BlockingDeque có hai đầu nào?** First/head và last/tail.
10. **`put(e)` trên BlockingDeque thêm ở đâu?** Ở cuối, tương đương putLast.
11. **`take()` trên BlockingDeque lấy ở đâu?** Ở đầu, tương đương takeFirst.
12. **`push()`/`pop()` tương đương phương thức nào?** addFirst/removeFirst; không tự block khi đầy/rỗng.
13. **`LinkedBlockingDeque<>(3)` có ba slot cho mỗi đầu không?** Không, tổng cộng ba phần tử.
14. **Tại sao putFirst nhiều có thể gây starvation?** Các phần tử cũ ở cuối bị liên tục chen trước bởi phần tử mới.
15. **BlockingDeque có tự thực hiện priority scheduling cho ThreadPoolExecutor không?** Không; executor thông thường enqueue cuối và take đầu.
16. **Có thể thêm task bằng executor.getQueue().putFirst để ưu tiên không?** Không nên; bỏ qua admission/lifecycle của executor.
17. **Handoff cung cấp happens-before gì?** Các hành động producer trước khi đưa item vào queue happens-before các hành động consumer sau khi lấy item.
18. **Nhận item có nghĩa nghiệp vụ đã commit không?** Không; cần acknowledgement/transaction riêng.
19. **Cần gì để dừng worker đang đợi transfer/take?** Interruption, timeout hoặc giao thức lifecycle phù hợp.
20. **Khi nào LinkedBlockingDeque tốt hơn BlockingQueue FIFO đơn giản?** Khi có nhu cầu rõ ràng thêm/lấy từ cả hai đầu.

**Ghi nhớ:** **`TransferQueue` điều khiển thời điểm producer biết consumer đã nhận; `BlockingDeque` điều khiển việc thêm/lấy ở hai đầu.** Đừng nhầm handoff với hoàn tất nghiệp vụ, hai đầu với cơ chế priority tự động, hoặc queue thread-safe với payload mutable luôn an toàn. Chọn API theo hợp đồng phối hợp, timeout, dung lượng và lifecycle của hệ thống.


</details>

- [Quay lại đầu trang](#back-to-top)

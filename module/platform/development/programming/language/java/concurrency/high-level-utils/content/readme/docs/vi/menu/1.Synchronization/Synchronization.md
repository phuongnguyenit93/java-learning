<a id="back-to-top"></a>

# Synchronization trong Java

## Menu
- [1. synchronized và intrinsic monitor](#synchronized-monitor)
- [2. ReentrantLock: explicit locking](#reentrant-lock)
- [3. ReadWriteLock](#read-write-lock)
- [4. ReadWriteLock fairness, starvation và khi nào đáng dùng](#read-write-lock-policy)
- [5. StampedLock và optimistic read](#stamped-lock)
- [6. StampedLock không phải ReadWriteLock nhanh hơn](#stamped-lock-limitations)
- [7. Atomic variables và CAS](#atomic-cas)
- [8. LongAdder và LongAccumulator](#adder-accumulator)
- [9. Concurrent Collections](#concurrent-collections)
- [10. Chọn công cụ nào?](#synchronization-choice)
- [11. Các experiment của phần Synchronization](#synchronization-experiments)

Phần trước đã học các vấn đề cần giải quyết: atomicity, visibility, ordering và progress.

Phần này tập trung vào các công cụ dùng để bảo vệ shared mutable state.

Mental model:

```text
shared mutable state
        ↓
chọn synchronization strategy phù hợp
        ↓
synchronized / Lock / Atomic / Concurrent Collection
```

Không có một công cụ luôn tốt nhất cho mọi tình huống.

## <a id="synchronized-monitor">1. synchronized và intrinsic monitor</a>

<details>
<summary>Click for details</summary>

Mỗi object Java có thể được dùng làm monitor cho `synchronized`.

```java
synchronized (lock) {
    // critical section
}
```

Khi một Thread đang giữ monitor, Thread khác muốn vào critical section sử dụng cùng monitor phải chờ.

`synchronized` cung cấp hai thứ quan trọng:

- mutual exclusion: tại một thời điểm chỉ Thread giữ monitor được chạy critical section;
- memory-consistency guarantee khi release/acquire cùng monitor.

### Method và block

```java
public synchronized void update() {
}
```

dùng monitor của `this`.

`static synchronized` dùng monitor của chính `Class` object:

```java
public static synchronized void updateGlobal() {
}
```

Vì vậy cần phân biệt:

```text
instance synchronized method
→ lock this

static synchronized method
→ lock MyClass.class
```

Hai instance khác nhau không chặn nhau chỉ vì cùng gọi một instance `synchronized` method; chúng chỉ cạnh tranh nếu thực sự dùng cùng monitor.

Trong khi:

```java
private final Object lock = new Object();

public void update() {
    synchronized (lock) {
    }
}
```

cho phép chọn monitor riêng và giữ scope khóa nhỏ hơn.

### Demo

Tham khảo:

```text
SynchronizationController#synchronizedCounter()
GET /synchronization/synchronized-counter
```

Nhiều worker cùng increment một counter nhưng method increment được `synchronized`.

Kết quả kỳ vọng:

```text
expected = actual
```

**Kết luận:** `synchronized` có thể biến critical section thành vùng mutual exclusion và tạo happens-before thông qua monitor acquire/release.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reentrant-lock">2. ReentrantLock: explicit locking</a>

<details>
<summary>Click for details</summary>

`ReentrantLock` cung cấp cùng mục tiêu mutual exclusion nhưng thêm các khả năng điều khiển mà `synchronized` không có trực tiếp:

- `tryLock()`;
- `tryLock(timeout, unit)`;
- `lockInterruptibly()`;
- fairness option;
- nhiều `Condition` trên cùng một lock.

Rule quan trọng:

```java
lock.lock();
try {
    // critical section
} finally {
    lock.unlock();
}
```

Nếu quên `unlock()`, lock có thể bị giữ vô thời hạn.

### Vì sao gọi là Reentrant?

Reentrant nghĩa là **Thread đang giữ lock có thể acquire lại chính lock đó** mà không tự deadlock.

`ReentrantLock` theo dõi hold count:

```text
lock() lần 1 → holdCount = 1
lock() lần 2 → holdCount = 2
unlock()      → holdCount = 1
unlock()      → holdCount = 0
```

Intrinsic monitor của `synchronized` cũng có tính reentrant.

Tham khảo:

```text
SynchronizationController#reentrantLock()
GET /synchronization/reentrant-lock
```

**Kết luận:** reentrancy cho phép một call chain trên cùng Thread đi qua nhiều method cùng acquire một lock mà không tự khóa chính mình; nhưng số lần release vẫn phải khớp số lần acquire.

### tryLock demo

Tham khảo:

```text
SynchronizationController#tryLock()
GET /synchronization/try-lock
```

Worker A giữ lock. Worker B chỉ chờ một khoảng hữu hạn bằng `tryLock(timeout)`.

Điểm cần quan sát:

```text
holderAcquired = true
contenderAcquiredWhileHeld = false
workersTerminated = true
```

**Kết luận:** `tryLock` cho phép xây dựng timeout/fallback thay vì chờ lock vô hạn.

### lockInterruptibly demo

Tham khảo:

```text
SynchronizationController#interruptibleLock()
GET /synchronization/interruptible-lock
```

Một worker đang chờ lock bằng `lockInterruptibly()` được gửi interrupt.

Kết quả kỳ vọng:

```text
waiterInterrupted = true
waiterAcquired = false
```

**Kết luận:** explicit lock có thể hỗ trợ cancellation trong lúc chờ lock.

### Fair và unfair lock

`new ReentrantLock(true)` yêu cầu fairness policy mạnh hơn bản mặc định.

Fairness không nên được đánh giá bằng thứ tự vài dòng log. Scheduler và thời điểm Thread thực sự gọi `lock()` có thể khác thứ tự `start()`.

Fair lock thường đổi throughput lấy khả năng hạn chế barging/starvation. Cần benchmark theo workload thật nếu lựa chọn này quan trọng.

Không nên dùng một benchmark vài trăm hay vài nghìn Thread rồi kết luận tuyệt đối rằng:

```text
fair = không bao giờ starvation
unfair = chắc chắn starvation
```

Fairness là **acquisition policy** của lock, không phải lời hứa về thứ tự `Thread.start()`, thứ tự scheduler chạy Thread hay thứ tự log xuất hiện.

Một footgun quan trọng: với fair `ReentrantLock`, `tryLock()` **không timeout** không bắt buộc tuân theo fairness policy. Nó có thể acquire ngay nếu lock đang available dù có Thread khác đã chờ lâu hơn. Ngược lại, timed `tryLock(timeout, unit)` honor fairness policy của fair lock khi phải xếp hàng chờ.

Mental model:

```text
new ReentrantLock(true)
→ lock()/lockInterruptibly()/timed tryLock() dùng fair admission policy phù hợp

untimed tryLock()
→ opportunistic immediate acquisition
→ có thể barge
```

Vì vậy không dùng untimed `tryLock()` rồi suy luận rằng mọi acquisition của fair lock đều FIFO-like.

### Fair/unfair policy demo

Tham khảo:

```text
SynchronizationController#reentrantLockPolicy()
GET /synchronization/reentrant-lock-policy
```

Experiment cố ý không benchmark timing. Nó chỉ quan sát contract cấu hình:

```text
defaultIsFair = false
explicitUnfairIsFair = false
explicitFairIsFair = true
untimedTryLockHonorsFairness = false
timedTryLockHonorsFairness = true
fairnessGuaranteesThreadStartOrder = false
fairnessEliminatesSchedulingVariance = false
fairnessHasPotentialThroughputCost = true
```

**Kết luận:** dùng fairness khi semantics về giảm barging/starvation đáng giá hơn throughput cost; nếu performance quan trọng phải benchmark workload thật, không suy từ vài lần chạy endpoint.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="read-write-lock">3. ReadWriteLock</a>

<details>
<summary>Click for details</summary>

`ReentrantReadWriteLock` tách quyền truy cập thành:

```text
Read Lock
→ nhiều reader có thể cùng giữ

Write Lock
→ exclusive
```

Quy tắc cơ bản:

| A đang giữ | B muốn | Kết quả |
| --- | --- | --- |
| Read | Read | Có thể cùng chạy |
| Read | Write | Write phải chờ |
| Write | Read | Read phải chờ |
| Write | Write | Write phải chờ |

Tham khảo:

```text
SynchronizationController#readWriteLock()
GET /synchronization/read-write-lock
```

Experiment cho hai reader cùng giữ read lock rồi khởi động writer.

Điểm cần quan sát:

```text
maxConcurrentReaders >= 2
writerAcquiredWhileReadersHeld = false
writerEventuallyAcquired = true
```

### Lock downgrading

`ReentrantReadWriteLock` hỗ trợ **downgrade** từ write lock sang read lock theo flow:

```text
hold write lock
→ acquire read lock
→ release write lock
→ tiếp tục giữ read lock
```

Chiều ngược lại **không được hỗ trợ như một lock upgrade**. Một Thread đang giữ read lock không thể dựa vào việc acquire write lock để "nâng cấp" quyền giữ lock mà vẫn giữ nguyên read lock.

Mental model cần nhớ:

```text
write → read
→ supported downgrade

read → write
→ upgrade không được support
→ phải thiết kế lại flow, release read lock rồi cạnh tranh write lock theo protocol phù hợp
```

Tham khảo experiment riêng:

```text
SynchronizationController#readWriteLockDowngrade()
GET /synchronization/read-write-lock-downgrade
```

Experiment giữ write lock, acquire thêm read lock rồi release write lock. Một writer khác vẫn phải chờ cho tới khi read lock downgrade được release.

Sau đó cùng experiment giữ **chỉ read lock** rồi thử `writeLock().tryLock(timeout)`. Kết quả kỳ vọng:

```text
readToWriteUpgradeSucceeded = false
readToWriteUpgradeSupported = false
```

**Kết luận:** downgrade giữ continuity của quyền đọc sau khi kết thúc mutation; `ReentrantReadWriteLock` không hỗ trợ read → write upgrade. Nếu cần write access, flow phải release/re-coordinate thay vì giữ read lock và chờ nâng cấp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="read-write-lock-policy">4. ReadWriteLock fairness, starvation và khi nào đáng dùng</a>

<details>
<summary>Click for details</summary>

`ReentrantReadWriteLock` mặc định là **non-fair**; có thể tạo fair lock bằng:

```java
new ReentrantReadWriteLock(true);
```

Fairness không có nghĩa `Thread.start()` trước chắc chắn acquire trước. Nó liên quan policy phục vụ các Thread đang cạnh tranh lock, và đổi lại thường có throughput cost.

Tương tự `ReentrantLock`, các non-blocking `tryLock()` của `ReadLock`/`WriteLock` không nên được dùng như bằng chứng rằng fair mode luôn được honor ở mọi acquisition API. Chúng có thể acquire ngay nếu lock state cho phép, bất kể waiter đang xếp hàng. Nếu fairness là requirement của protocol, phải chọn acquisition API có semantics phù hợp thay vì chỉ dựa vào constructor `new ReentrantReadWriteLock(true)`.

Với workload read-heavy, non-fair policy có thể cho throughput tốt nhưng writer có thể phải chờ lâu nếu reader liên tục xuất hiện. Vì vậy không nên chọn `ReadWriteLock` chỉ vì nghe rằng "nhiều reader chạy song song".

Nó đáng cân nhắc khi:

- read nhiều hơn write đáng kể;
- critical section đọc đủ lớn để concurrency mang lại lợi ích;
- contention thật sự tồn tại;
- benchmark/workload cho thấy lợi ích lớn hơn overhead quản lý hai loại lock.

Tham khảo:

```text
SynchronizationController#readWriteLockPolicy()
GET /synchronization/read-write-lock-policy
```

Experiment không cố chứng minh fairness bằng thứ tự log. Nó quan sát trực tiếp policy được cấu hình:

```text
defaultIsFair = false
explicitFairIsFair = true
untimedReadTryLockHonorsFairness = false
untimedWriteTryLockHonorsFairness = false
fairnessGuaranteesThreadStartOrder = false
```

**Kết luận:** fairness là policy, không phải benchmark shortcut; starvation/throughput phải được đánh giá theo workload thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stamped-lock">5. StampedLock và optimistic read</a>

<details>
<summary>Click for details</summary>

`StampedLock` hỗ trợ:

- write lock;
- read lock;
- optimistic read.

Optimistic read không giữ read lock trong toàn bộ đoạn đọc. Reader lấy một stamp, đọc snapshot rồi gọi `validate(stamp)`.

```text
tryOptimisticRead()
        ↓
đọc snapshot
        ↓
validate(stamp)
   ├─ true  → snapshot vẫn hợp lệ
   └─ false → fallback sang read lock thật
```

Tham khảo:

```text
SynchronizationController#stampedLock()
GET /synchronization/stamped-lock
```

Writer được cố tình cho xen vào giữa optimistic read.

Kết quả kỳ vọng:

```text
optimisticStampValid = false
fallbackValue = updatedValue
```

**Kết luận:** optimistic read là chiến lược đọc rồi kiểm chứng, không phải "đọc mà luôn an toàn không cần validate".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="stamped-lock-limitations">6. StampedLock không phải ReadWriteLock nhanh hơn</a>

<details>
<summary>Click for details</summary>

`StampedLock` có một số semantics quan trọng cần nhớ:

- **không reentrant**: Thread đang giữ write lock không được giả định có thể acquire lại như `ReentrantLock`;
- các `Lock` view của `StampedLock` không cung cấp `Condition`;
- programmer phải quản lý `long stamp` và unlock đúng mode;
- optimistic read luôn cần `validate(stamp)` trước khi tin snapshot;
- khi cancellation/interrupt quan trọng, cần chọn API interruptible phù hợp thay vì mặc định mọi acquisition đều cancellable.

Tham khảo:

```text
SynchronizationController#stampedLockLimitations()
GET /synchronization/stamped-lock-limitations
```

Experiment giữ write lock rồi gọi `tryWriteLock()` lần nữa trên cùng Thread. Vì lock không reentrant, lần thử thứ hai không thành công. Experiment cũng gọi `newCondition()` qua read-lock view và quan sát operation không được support.

```text
sameThreadReentrantTryWriteLockSucceeded = false
reentrant = false
conditionSupported = false
```

**Kết luận:** chỉ dùng `StampedLock` khi optimistic/read semantics thật sự đáng giá hơn complexity tăng thêm.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="atomic-cas">7. Atomic variables và CAS</a>

<details>
<summary>Click for details</summary>

Atomic classes phù hợp cho nhiều state transition nhỏ như counter/reference update.

Ví dụ:

```java
AtomicInteger count = new AtomicInteger();
count.incrementAndGet();
```

Mental model CAS:

```text
đọc expected value
        ↓
tính new value
        ↓
compare-and-set
   ├─ thành công → hoàn tất
   └─ thất bại  → đọc lại và retry
```

Atomic không đồng nghĩa mọi compound business rule trở nên thread-safe. Nếu invariant trải qua nhiều object/field, có thể vẫn cần locking hoặc thiết kế khác.

### Demo compareAndSet

Tham khảo:

```text
SynchronizationController#atomicCas()
GET /synchronization/atomic-cas
```

Experiment bắt đầu với:

```text
AtomicInteger = 10
```

rồi thử hai CAS:

```text
compareAndSet(10, 20)
→ true
→ value = 20

compareAndSet(10, 30)
→ false vì expected=10 đã stale
→ value vẫn = 20
```

Điểm quan trọng là CAS không có nghĩa "set nếu thích". Update chỉ thành công nếu state hiện tại vẫn đúng expected value/reference mà caller dựa vào để tính transition.

### AtomicReference và ABA

CAS trên reference thường so sánh giá trị hiện tại với expected reference.

Một vấn đề nâng cao là ABA:

```text
A → B → A
```

CAS chỉ nhìn thấy giá trị cuối lại là A và có thể không biết state đã thay đổi ở giữa. Các abstraction như `AtomicStampedReference` có thể kèm version/stamp khi cần phát hiện trường hợp này.

Experiment `/atomic-cas` tạo đúng sequence:

```text
AtomicReference
A → B → A
stale observer vẫn giữ expected reference A
compareAndSet(A, C)
→ thành công trong experiment này
```

vì plain CAS chỉ thấy reference hiện tại lại chính là `A`; nó không mang theo history rằng state đã đi qua `B`.

Sau đó cùng sequence được chạy với `AtomicStampedReference`:

```text
A, stamp=0
→ B, stamp=1
→ A, stamp=2

CAS bằng expected A + stale stamp=0
→ false
```

**Kết luận:** stamp/version giúp phát hiện "giá trị nhìn giống cũ nhưng history đã thay đổi". Không phải mọi use case CAS đều có ABA problem; chỉ thêm versioning khi invariant thực sự cần biết intermediate transition.

### Atomic classes đáng biết

Không cần học thuộc toàn bộ package, nhưng nên nhận ra các nhóm:

| Nhóm | Ví dụ | Mục tiêu |
| --- | --- | --- |
| Primitive-like | `AtomicInteger`, `AtomicLong`, `AtomicBoolean` | state transition đơn giản |
| Reference | `AtomicReference`, `AtomicStampedReference`, `AtomicMarkableReference` | atomic reference / versioned reference |
| Array | `AtomicIntegerArray`, `AtomicLongArray`, `AtomicReferenceArray` | atomic access theo element |
| High-contention counter | `LongAdder`, `DoubleAdder` | update nhiều, aggregate sau |
| Accumulator | `LongAccumulator`, `DoubleAccumulator` | reduction function như max/min/sum |
| Field updater | `AtomicIntegerFieldUpdater`, ... | atomic update field chuyên biệt |

Module dùng hai experiment đại diện:

```text
SynchronizationController#atomicCas()
→ CAS / AtomicReference / ABA / AtomicStampedReference

SynchronizationController#atomicTools()
→ AtomicInteger / LongAdder / LongAccumulator
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="adder-accumulator">8. LongAdder và LongAccumulator</a>

<details>
<summary>Click for details</summary>

`LongAdder` hữu ích cho counter có contention cao. Nó phân tán update vào nhiều cell rồi tổng hợp bằng `sum()`.

### Giới hạn khi đọc tổng trong lúc vẫn có writer

`sum()` **không bảo đảm atomic snapshot** của toàn bộ counter khi các Thread khác vẫn update. Không nên diễn giải thành chỉ có một độ trễ cố định hoặc luôn trả một snapshot tại một thời điểm duy nhất.

Khi không còn concurrent update, `sum()` trả tổng chính xác. Vì vậy experiment `atomicTools()` chờ `join()` toàn bộ worker trước khi đọc tổng:

```text
4 workers × 1.000 increments
→ join tất cả workers
→ atomicInteger = longAdder = expectedCount = 4.000
```

Kết quả bằng nhau sau join không chứng minh `LongAdder` thay thế được mọi use case của `AtomicLong`.
Dùng adder cho thống kê/metrics; với sequence ID hoặc admission kiểu "chỉ cho tối đa N request vào", cần atomic state transition hoặc primitive phù hợp như Semaphore. Không dùng `sum() < limit` rồi `increment()` như một thao tác nguyên tử.

Tham khảo contract: [LongAdder.sum() — Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html#sum()).

### LongAccumulator

`LongAccumulator` tổng quát hóa ý tưởng bằng một accumulator function.

Accumulator function nên phù hợp với cách kết quả trung gian được kết hợp. Các phép như `sum`, `max`, `min` là ví dụ dễ reasoning.

Không nên dùng một phép toán phụ thuộc thứ tự hoặc không kết hợp được rồi kỳ vọng kết quả giống xử lý tuần tự.

Ngoài ra accumulator function nên **side-effect-free**. Implementation có thể phải áp dụng lại function trong quá trình concurrent update/retry, nên không được coi mỗi lần function được gọi là một business action "exactly once".

Không viết kiểu:

```java
(current, value) -> {
    sendEmail();
    auditLog();
    return Math.max(current, value);
}
```

Side effect phải nằm ngoài reduction function; function nên chỉ tính result từ các argument của nó.

Tham khảo:

```text
SynchronizationController#atomicTools()
GET /synchronization/atomic-tools
```

Demo sử dụng:

- `AtomicInteger` cho exact counter;
- `LongAdder` cho concurrent additions;
- `LongAccumulator` với phép `max`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="concurrent-collections">9. Concurrent Collections</a>

<details>
<summary>Click for details</summary>

Concurrent collections đóng gói synchronization strategy vào data structure.

Một số nhóm quan trọng:

| Collection | Mục tiêu |
| --- | --- |
| `ConcurrentHashMap` | key/value concurrent access |
| `ConcurrentSkipListMap` | concurrent sorted map |
| `CopyOnWriteArrayList` | read-heavy, write ít, snapshot iteration |
| `ConcurrentLinkedQueue` | non-blocking concurrent queue |
| `BlockingQueue` | queue có semantics chờ; học sâu ở Coordination |

### Atomic compound methods

Với `ConcurrentHashMap`, ưu tiên method thể hiện compound operation nguyên tử ở mức collection:

```java
map.merge(key, 1, Integer::sum);
map.computeIfAbsent(key, k -> newValue());
map.putIfAbsent(key, value);
```

Thay vì tự viết `get()` rồi `put()` và giả định hai operation tách rời trở thành atomic.

### CopyOnWrite snapshot

Iterator của `CopyOnWriteArrayList` quan sát snapshot tại thời điểm iterator được tạo. Ghi mới tạo backing array mới nên phù hợp khi read nhiều hơn write đáng kể.

Điểm đánh đổi là mỗi mutation có thể phải copy backing array. Vì vậy collection này không phù hợp với write-heavy workload hoặc collection rất lớn chỉ vì read path thuận tiện.

### ConcurrentLinkedQueue

`poll()` không block. Khi queue rỗng nó trả `null`.

Do đó consumer không nên viết busy loop vô hạn chỉ gọi `poll()` nếu không có backoff/termination strategy.

`size()` của `ConcurrentLinkedQueue` không nên được coi là cheap constant-time metric cho hot path. Nếu chỉ cần biết queue có phần tử hay không, ưu tiên semantics phù hợp như `isEmpty()` thay vì polling `size()` liên tục.

Iterator của nhiều concurrent collection là **weakly consistent**: nó có thể phản ánh một phần update concurrent mà không fail-fast như collection thường, nhưng không nên suy luận rằng nó là snapshot tuyệt đối. `CopyOnWriteArrayList` là trường hợp khác vì iterator của nó thực sự dùng snapshot backing array.

### Một số API/type đáng nhận ra

Không cần biến chapter này thành API catalog, nhưng nên biết các operation giúp tránh pattern "check rồi update" không atomic trên `ConcurrentHashMap`:

```text
putIfAbsent
compute
computeIfAbsent
computeIfPresent
merge
replace(key, oldValue, newValue)
```

Các type cùng họ cũng đáng nhận ra:

```text
CopyOnWriteArraySet
ConcurrentSkipListSet
```

Chúng không thay đổi mental model chính: chọn collection dựa trên access pattern và semantics cần có, không chỉ vì tên có chữ `Concurrent`.

### ConcurrentSkipListMap

`ConcurrentSkipListMap` phù hợp khi cần đồng thời:

- concurrent access;
- key luôn được quan sát theo sorted order;
- các navigation operation như `firstKey`, `lastKey`, range view.

Nó không phải "ConcurrentHashMap nhưng nhanh hơn"; hai collection phục vụ access pattern khác nhau.

Tham khảo:

```text
SynchronizationController#concurrentSkipList()
GET /synchronization/concurrent-skip-list
```

Demo cho nhiều Thread insert key theo thứ tự cố tình đảo ngược, sau đó quan sát key set cuối vẫn sorted.

Tham khảo:

```text
SynchronizationController#concurrentCollections()
GET /synchronization/concurrent-collections
```

Experiment dùng collection cục bộ và luôn kết thúc, không tạo consumer daemon chạy vô hạn như demo cũ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-choice">10. Chọn công cụ nào?</a>

<details>
<summary>Click for details</summary>

```text
state đơn giản / counter
→ Atomic / LongAdder

critical section nhiều bước
→ synchronized hoặc Lock

read-heavy shared structure
→ cân nhắc ReadWriteLock / CopyOnWrite tùy pattern

sorted concurrent map
→ ConcurrentSkipListMap

map concurrent thông thường
→ ConcurrentHashMap

optimistic read chuyên biệt
→ StampedLock nếu complexity đáng giá
```

Không chọn tool chỉ dựa trên câu "cái này nhanh hơn". Correctness trước, sau đó đo contention/workload thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="synchronization-experiments">11. Các experiment của phần Synchronization</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint |
| --- | --- | --- |
| `#synchronized-monitor` | `SynchronizationController#synchronizedCounter()` | `GET /synchronization/synchronized-counter` |
| `#reentrant-lock` | `SynchronizationController#tryLock()` | `GET /synchronization/try-lock` |
| `#reentrant-lock` | `SynchronizationController#interruptibleLock()` | `GET /synchronization/interruptible-lock` |
| `#reentrant-lock` | `SynchronizationController#reentrantLock()` | `GET /synchronization/reentrant-lock` |
| `#reentrant-lock` | `SynchronizationController#reentrantLockPolicy()` | `GET /synchronization/reentrant-lock-policy` |
| `#read-write-lock` | `SynchronizationController#readWriteLock()` | `GET /synchronization/read-write-lock` |
| `#read-write-lock` | `SynchronizationController#readWriteLockDowngrade()` | `GET /synchronization/read-write-lock-downgrade` |
| `#read-write-lock-policy` | `SynchronizationController#readWriteLockPolicy()` | `GET /synchronization/read-write-lock-policy` |
| `#stamped-lock` | `SynchronizationController#stampedLock()` | `GET /synchronization/stamped-lock` |
| `#stamped-lock-limitations` | `SynchronizationController#stampedLockLimitations()` | `GET /synchronization/stamped-lock-limitations` |
| `#atomic-cas` | `SynchronizationController#atomicCas()` | `GET /synchronization/atomic-cas` |
| `#adder-accumulator` | `SynchronizationController#atomicTools()` | `GET /synchronization/atomic-tools` |
| `#concurrent-collections` | `SynchronizationController#concurrentCollections()` | `GET /synchronization/concurrent-collections` |
| `#concurrent-collections` | `SynchronizationController#concurrentSkipList()` | `GET /synchronization/concurrent-skip-list` |

</details>

- [Quay lại đầu trang](#back-to-top)

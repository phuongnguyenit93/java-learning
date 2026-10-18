<a id="back-to-top"></a>

# Concurrency Problems & Java Memory Model

## Menu
- [1. Shared Mutable State, Atomicity và Race Condition](#shared-state-race-condition)
- [2. Visibility và Java Memory Model](#jmm-visibility)
- [3. Happens-Before: guarantee để truyền visibility](#happens-before)
- [4. Safe Publication, final field và this escape](#safe-publication-final)
- [5. volatile: Visibility + Ordering, không phải Atomicity](#volatile-publication)
- [6. Ordering và Instruction Reordering](#ordering-reordering)
- [7. Deadlock, Livelock và Starvation](#progress-problems)
- [8. Coffman conditions và phòng tránh Deadlock bằng lock ordering](#deadlock-prevention)
- [9. Tổng hợp Atomicity, Visibility, Ordering và Progress](#problem-summary)
- [10. Các experiment của phần Concurrency Problems](#problem-experiments)

Phần này trả lời câu hỏi quan trọng nhất trước khi học synchronization:

> Vì sao một đoạn code đúng khi chạy một Thread lại có thể sai khi nhiều Thread cùng chạy?

Mental model xuyên suốt:

```text
Shared Mutable State
        ↓
Concurrent Access
        ↓
Atomicity / Visibility / Ordering problems
        ↓
Java Memory Model + Happens-Before
        ↓
Synchronization tools ở chapter tiếp theo
```

Sau phần này cần phân biệt được:

- race condition và data race;
- atomicity và visibility;
- visibility và ordering;
- happens-before là guarantee của Java Memory Model, không phải mô tả cache CPU;
- `volatile` giải quyết visibility/order nhưng không biến compound operation thành atomic;
- deadlock, livelock và starvation đều là liveness problem nhưng khác nhau về biểu hiện.

## <a id="shared-state-race-condition">1. Shared Mutable State, Atomicity và Race Condition</a>

<details>
<summary>Click for details</summary>

### Shared Mutable State là gì?

Một object trở thành shared mutable state khi:

```text
shared
→ nhiều Thread có thể truy cập cùng object

mutable
→ object đó có state có thể thay đổi
```

Ví dụ:

```java
class Counter {
    int value;
}
```

Nếu hai Thread cùng thao tác trên `value`, kết quả có thể phụ thuộc vào cách các thao tác xen kẽ nhau.

### Vì sao count++ không atomic?

`count++` nhìn giống một thao tác, nhưng về mặt logic là một read-modify-write sequence:

```text
read count
    ↓
count + 1
    ↓
write count
```

Hai Thread có thể cùng đọc một giá trị cũ rồi cùng ghi kết quả mới dựa trên snapshot đó.

Ví dụ:

```text
ban đầu count = 0

Thread A đọc 0
Thread B đọc 0

Thread A ghi 1
Thread B ghi 1

kỳ vọng tuần tự = 2
thực tế = 1
```

Đây là **lost update**.

### Race condition khác data race như thế nào?

**Race condition** là vấn đề correctness: kết quả đúng/sai phụ thuộc vào thứ tự xen kẽ các thao tác mà chương trình chưa kiểm soát.

**Data race** có nghĩa cụ thể trong JMM: hai Thread truy cập cùng một variable, ít nhất một truy cập là write, và hai truy cập xung đột đó không được sắp thứ tự bởi happens-before.

Ví dụ:

| Code | Vấn đề |
| --- | --- |
| Hai Thread cùng `count++` trên field `int` thường, không đồng bộ | Có data race và có thể lost update |
| Hai Thread cùng `count++` trên field `volatile int` | Từng read/write có volatile semantics, nhưng compound increment vẫn có race condition |
| `get()` rồi `put()` riêng rẽ trên `ConcurrentHashMap` để tăng counter | Collection bảo vệ từng operation; chuỗi check/read/update vẫn có thể race |

Vì vậy **không có data race chưa đủ để bảo đảm mọi business operation nguyên tử**. Cần xác định phạm vi invariant cần bảo vệ.
Hai endpoint `race-condition` và `volatile-not-atomic` bên dưới giúp đối chiếu: cả hai đều có thể lost update, dù cơ chế đồng bộ của field khác nhau.

### Demo trong module

Tham khảo controller:

```text
ConcurrencyProblemController#raceCondition()
```

Endpoint:

```text
GET /concurrency/problem/race-condition
```

Experiment cố tình chia thao tác increment thành hai phase:

```text
Thread A đọc snapshot
Thread B đọc snapshot
        ↓
cả hai mới được phép ghi
```

Việc điều phối này chỉ dùng để làm race **deterministic** cho mục đích học tập.

Kết quả kỳ vọng:

```text
snapshotA = 0
snapshotB = 0
expectedSequentialResult = 2
actualResult = 1
lostUpdates = 1
```

**Kết luận:** race condition không có nghĩa là kết quả lúc nào cũng sai. Nó có nghĩa là correctness phụ thuộc vào interleaving/timing mà code không kiểm soát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jmm-visibility">2. Visibility và Java Memory Model</a>

<details>
<summary>Click for details</summary>

Visibility trả lời câu hỏi:

> Nếu Thread A đã ghi một giá trị, dựa vào guarantee nào để Thread B chắc chắn nhìn thấy giá trị đó?

Không nên giải thích Java Memory Model bằng mental model đơn giản kiểu:

```text
Thread A ghi RAM
Thread B đọc RAM
```

Java không yêu cầu programmer suy luận trực tiếp từ L1/L2/L3 cache hay register của CPU.

Thứ programmer cần dựa vào là **Java Memory Model (JMM)**.

JMM định nghĩa những điều JVM phải đảm bảo về:

- visibility;
- ordering;
- synchronization actions;
- happens-before relationship.

Nếu giữa hai action không tồn tại một synchronization guarantee phù hợp, việc một Thread "đã ghi rồi" theo thời gian thực không tự động đảm bảo Thread khác phải quan sát giá trị mới theo cách chương trình mong muốn.

### Stale data

Stale data là trường hợp một Thread tiếp tục quan sát state cũ trong khi một Thread khác đã cập nhật state.

Điểm quan trọng:

```text
không thấy stale data trong một lần chạy
≠
code đã thread-safe
```

Một bug visibility có thể phụ thuộc JVM optimization, architecture, timing và cách chương trình được compile/run.

Vì vậy không nên thiết kế bài học dựa trên mục tiêu:

> "Chạy cho đến khi stale data xuất hiện thì mới chứng minh được JMM."

Điều cần học là **guarantee**, không phải săn một hiện tượng ngẫu nhiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="happens-before">3. Happens-Before: guarantee để truyền visibility</a>

<details>
<summary>Click for details</summary>

Happens-before không đơn giản là "A xảy ra trước B theo đồng hồ".

Nếu action A happens-before action B thì những effect của A được đảm bảo visible với B theo Java Memory Model.

Một số rule nền tảng:

### Program order

Trong một Thread, các action trước đó happens-before các action sau đó theo program order semantics.

### Thread start rule

Những action xảy ra trước:

```java
thread.start();
```

happens-before các action trong Thread được start.

### Thread join rule

Mọi action trong worker Thread happens-before code tiếp tục sau khi một Thread khác `join()` worker thành công.

### Monitor rule

Unlock trên một monitor happens-before lần lock monitor đó sau này.

### Volatile rule

Write vào một volatile variable happens-before một subsequent read của chính volatile variable đó theo rule của JMM.

### Transitivity

Nếu:

```text
A happens-before B
B happens-before C
```

thì:

```text
A happens-before C
```

### Higher-level java.util.concurrent cũng mang memory guarantees

Happens-before không dừng ở `synchronized`, `volatile`, `start()` và `join()`.

Các abstraction trong `java.util.concurrent` xây higher-level guarantee trên cùng memory model. Một số edge rất thực tế:

```text
actions trước Executor.execute/submit(task)
→ happen-before actions trong task khi task bắt đầu chạy

actions của asynchronous computation
→ happen-before code sau Future.get() thành công ở Thread khác

actions trước CountDownLatch.countDown()
→ happen-before actions sau await() thành công

actions trước Semaphore.release()
→ happen-before actions sau acquire() thành công trên cùng synchronizer

put/access qua concurrent collection phù hợp
→ có publication guarantee được API định nghĩa cho element đó
```

Điều này rất quan trọng về mental model: dùng `Executor`, `Future`, synchronizer hay concurrent collection không có nghĩa programmer rời khỏi JMM; các API đó cung cấp những synchronization edge cấp cao hơn để programmer không phải tự dựng tất cả bằng volatile/monitor.

### Demo start / join

Tham khảo:

```text
ConcurrencyProblemController#startJoinHappensBefore()
```

Endpoint:

```text
GET /concurrency/problem/happens-before-start-join
```

Experiment có flow:

```text
request thread ghi beforeStart = 7
        ↓
worker.start()
        ↓
worker đọc beforeStart
worker ghi writtenByWorker = 42
        ↓
worker.join()
        ↓
request thread đọc writtenByWorker
```

Kết quả kỳ vọng:

```text
workerObservedBeforeStart = 7
parentObservedAfterJoin = 42
```

**Kết luận:** `start()` và `join()` không chỉ điều phối lifecycle. Chúng còn mang memory-consistency guarantee.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="safe-publication-final">4. Safe Publication, final field và this escape</a>

<details>
<summary>Click for details</summary>

Việc tạo xong một object ở Thread A không tự động có nghĩa Thread B có thể đọc object đó qua bất kỳ shared reference nào và mặc định nhận đầy đủ mọi guarantee cần thiết.

Điều cần xác định là **reference tới object được publish sang Thread khác bằng boundary nào**.

Một số publication boundary quen thuộc đã xuất hiện trong module:

```text
ghi reference vào volatile field
→ volatile happens-before edge

ghi/đọc dưới cùng monitor hoặc Lock
→ synchronization edge

đặt object vào concurrent collection bằng API có memory-consistency guarantee
→ publication theo contract của collection

chuẩn bị object trước Thread.start()
→ start rule

chuẩn bị object trước Executor.execute/submit(...)
→ executor submission guarantee
```

### final field có initialization-safety đặc biệt

Java Memory Model có rule đặc biệt cho `final` field.

Ví dụ:

```java
final class UserSnapshot {
    private final String id;
    private final int level;

    UserSnapshot(String id, int level) {
        this.id = id;
        this.level = level;
    }
}
```

Nếu object được **construct đúng cách** và reference `this` không escape trước khi constructor hoàn tất, JMM cung cấp initialization-safety mạnh hơn cho các `final` field được gán trong constructor.

Mental model nên giữ:

```text
constructor hoàn tất đúng cách
        ↓
final fields được freeze theo JMM
        ↓
Thread khác có được reference tới object
        ↓
có guarantee đặc biệt để thấy giá trị final đã được khởi tạo
```

Điều này là một lý do immutable object với `final` fields rất hữu ích trong concurrent design.

Nhưng không nên suy thành:

```text
object có vài final fields
→ toàn bộ object tự động thread-safe
→ mọi mutable field / mutation sau constructor đều được publish an toàn
```

`final` bảo vệ semantics của final field initialization; nó không thay thế synchronization cho mutable shared state sau đó.

### this escape: publish object quá sớm

Một object bị **this escape** khi reference của chính nó có thể bị Thread khác quan sát trước khi constructor hoàn tất.

Ví dụ nguy hiểm về mặt design:

```java
class Listener {
    private final String configuration;

    Listener(EventBus bus) {
        bus.register(this); // this đã escape
        this.configuration = "READY";
    }
}
```

Nếu `register(this)` làm reference này trở nên reachable từ Thread khác ngay lập tức, Thread đó có thể quan sát object trong lúc construction chưa hoàn thành. Đây là lý do constructor nên hoàn thành invariant trước khi object được publish ra ngoài.

Pattern dễ reasoning hơn:

```text
construct hoàn chỉnh
→ thiết lập invariant
→ constructor return
→ publish object qua boundary rõ ràng
```

### Vì sao không có endpoint "unsafe publication"?

Tương tự reordering/stale-read demo, partially initialized observation của code có data race không phải outcome deterministic mà endpoint có thể bảo đảm tái hiện trên mọi JVM/machine.

Không quan sát được lỗi trong 10.000 lần chạy cũng không chứng minh unsafe publication là đúng.

Vì vậy module dùng **positive guarantees** để học:

```text
final-field initialization safety
volatile / monitor / Lock
Thread.start / join
Executor / Future / concurrent collection
```

thay vì săn một execution ngẫu nhiên của code không có guarantee.

**Kết luận:** concurrency design tốt không chỉ hỏi "object đã được tạo chưa?", mà còn hỏi **object được publish sang Thread khác bằng memory-consistency boundary nào** và **có escape trước khi construction hoàn tất hay không**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="volatile-publication">5. volatile: Visibility + Ordering, không phải Atomicity</a>

<details>
<summary>Click for details</summary>

`volatile` thường được dùng khi một variable đóng vai trò publication/signal.

Ví dụ:

```java
int data = 0;
volatile boolean ready = false;

// writer
data = 42;
ready = true;

// reader
if (ready) {
    System.out.println(data);
}
```

Mental model nên dùng:

```text
write data = 42
        ↓ program order
write volatile ready = true
        ↓ volatile happens-before
read volatile ready == true
        ↓ program order
read data
```

Nhờ transitivity, reader thấy `ready == true` theo đúng volatile synchronization thì các write trước đó của writer cũng được publication đúng theo JMM.

Không cần diễn giải bằng câu:

```text
volatile = luôn đọc thẳng RAM
```

Đây không phải contract mà Java programmer cần dựa vào.

### Demo volatile publication

Tham khảo:

```text
ConcurrencyProblemController#volatilePublication()
```

Endpoint:

```text
GET /concurrency/problem/volatile-publication
```

Kết quả kỳ vọng:

```text
publishedData = 42
readerObservedData = 42
ready = true
```

**Kết luận:** volatile flag có thể publish những write xảy ra trước nó tới reader đọc flag đó sau này.

### volatile không làm count++ atomic

Một biến có thể là `volatile` nhưng compound operation vẫn race:

```java
volatile int count;

count++;
```

Visibility của read/write không biến toàn bộ read-modify-write sequence thành một action atomic.

Tham khảo:

```text
ConcurrencyProblemController#volatileIsNotAtomic()
```

Endpoint:

```text
GET /concurrency/problem/volatile-not-atomic
```

Experiment dùng cùng kỹ thuật deterministic lost-update như race demo, nhưng field được khai báo `volatile`.

Kết quả vẫn là:

```text
expectedSequentialResult = 2
actualResult = 1
```

**Kết luận:** `volatile` giải quyết một lớp vấn đề memory visibility/ordering; nó không thay thế `AtomicInteger`, `synchronized` hay lock cho compound update.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="ordering-reordering">6. Ordering và Instruction Reordering</a>

<details>
<summary>Click for details</summary>

Compiler, JIT và CPU có quyền tối ưu execution miễn là behavior quan sát được vẫn tuân thủ Java Memory Model.

Vì vậy programmer không nên suy luận:

```text
dòng 1 nằm trên dòng 2 trong source
→ Thread khác chắc chắn quan sát dòng 1 trước dòng 2
```

nếu giữa các Thread không có synchronization rule tương ứng.

Ví dụ message publication sai:

```java
int data = 0;
boolean ready = false;

// writer
data = 42;
ready = true;

// reader
if (ready) {
    use(data);
}
```

Không có volatile/lock/other happens-before edge giữa writer và reader.

Code như vậy có **data race**.

### Vì sao module không tạo endpoint "chắc chắn bắt được reordering"?

Một stress test có thể đôi lúc quan sát outcome lạ và đôi lúc không.

Nhưng:

```text
không quan sát được outcome lạ
≠
code được JMM đảm bảo đúng
```

Do đó chapter này dùng `volatile-publication` như positive experiment: thay vì săn behavior không được đảm bảo, ta chứng minh cách tạo ra một guarantee rõ ràng.

**Kết luận:** correctness đa luồng phải dựa trên happens-before, không dựa trên xác suất "máy của tôi chạy đúng".

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="progress-problems">7. Deadlock, Livelock và Starvation</a>

<details>
<summary>Click for details</summary>

Atomicity/visibility/ordering chủ yếu nói về **correctness của state**.

Deadlock/livelock/starvation nói về **progress của hệ thống**.

### Deadlock

Deadlock xảy ra khi các Thread tạo thành dependency cycle và không Thread nào có thể tiếp tục.

Kịch bản kinh điển:

```text
Thread A giữ Lock A
Thread A đợi Lock B

Thread B giữ Lock B
Thread B đợi Lock A
```

Đây là circular wait.

Một deadlock thật nếu tạo trực tiếp trong HTTP demo sẽ để lại Thread bị kẹt lâu dài, nên module không tạo intentional permanent deadlock.

Thay vào đó dùng experiment bounded mô phỏng đúng hình dạng dependency rồi dùng `tryLock()` để thoát.

Tham khảo:

```text
ConcurrencyProblemController#deadlockRisk()
```

Endpoint:

```text
GET /concurrency/problem/deadlock-risk
```

Kết quả kỳ vọng:

```text
threadAHasFirstLock = true
threadBHasFirstLock = true
threadASecondLockAcquired = false
threadBSecondLockAcquired = false
recoveredWithoutLeak = true
```

**Kết luận:** endpoint này chứng minh circular-lock dependency và deadlock avoidance, không giả vờ rằng timeout chính là deadlock.

### Livelock

Livelock xảy ra khi các Thread vẫn chạy và liên tục phản ứng với nhau nhưng không tạo được progress.

Ví dụ mental model:

```text
A thấy B đang thử
→ A lùi lại

B thấy A đang thử
→ B lùi lại

cả hai thử lại cùng lúc
→ lặp lại
```

Tham khảo:

```text
ConcurrencyProblemController#boundedLivelock()
```

Endpoint:

```text
GET /concurrency/problem/livelock
```

Demo cố tình giữ hai worker phản ứng đối xứng trong số vòng hữu hạn rồi kết thúc.

Kết quả kỳ vọng:

```text
progressMade = false
workerABackoffs > 0
workerBBackoffs > 0
workersTerminated = true
```

Nó minh họa **active but no progress** mà không để lại infinite loop.

### Starvation

Starvation xảy ra khi một Thread có thể tiếp tục về lý thuyết nhưng trong thực tế liên tục không nhận được CPU/lock/resource cần thiết trong thời gian rất dài.

Khác biệt nhanh:

| Problem | Thread có hoạt động? | System có progress? |
| --- | --- | --- |
| Deadlock | Không thể tiến tiếp | Không |
| Livelock | Có, phản ứng liên tục | Không |
| Starvation | Một số Thread khác vẫn tiến | Một Thread có thể bị bỏ đói |

Starvation phụ thuộc scheduler/lock policy và workload, nên không nên dùng vài dòng `println` để "chứng minh" nó. Fair/unfair lock sẽ được học kỹ ở phần Synchronization.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="deadlock-prevention">8. Coffman conditions và phòng tránh Deadlock bằng lock ordering</a>

<details>
<summary>Click for details</summary>

Deadlock kinh điển cần đồng thời bốn điều kiện thường gọi là **Coffman conditions**:

1. **Mutual exclusion**: resource chỉ có một owner tại một thời điểm.
2. **Hold and wait**: Thread giữ resource này trong khi chờ resource khác.
3. **No preemption**: resource không bị cưỡng bức lấy khỏi owner hiện tại.
4. **Circular wait**: tồn tại vòng chờ A → B → ... → A.

Không phải lúc nào cũng có thể loại bỏ ba điều kiện đầu. Một chiến lược thực dụng là phá **circular wait** bằng **global lock ordering**.

Ví dụ, nếu toàn application thống nhất:

```text
mọi nơi cần cả Lock A và Lock B
→ luôn acquire A trước
→ rồi mới acquire B
```

thì không được viết một code path khác acquire `B → A`.

Tham khảo:

```text
ConcurrencyProblemController#deadlockPrevention()
GET /concurrency/problem/deadlock-prevention
```

Experiment cho hai worker cạnh tranh cùng hai lock nhưng cả hai đều dùng thứ tự:

```text
A → B
```

Kết quả kỳ vọng:

```text
completedWorkers = 2
circularWaitPossibleByDesign = false
workersTerminated = true
```

**Kết luận:** `tryLock(timeout)` là một cách thoát khỏi wait nguy hiểm; **consistent lock ordering** là một cách thiết kế để loại bỏ circular-wait dependency ngay từ đầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="problem-summary">9. Tổng hợp Atomicity, Visibility, Ordering và Progress</a>

<details>
<summary>Click for details</summary>

| Nhóm vấn đề | Câu hỏi | Ví dụ |
| --- | --- | --- |
| Atomicity | Một operation có bị xen giữa không? | lost update với `count++` |
| Visibility | Thread khác có được đảm bảo thấy write không? | stale flag/data |
| Ordering | Các action được Thread khác quan sát theo guarantee nào? | publication không có happens-before |
| Progress | Thread/system có tiếp tục hoàn thành công việc không? | deadlock, livelock, starvation |

Mental model cuối cùng:

```text
Concurrency correctness
    ├── state correctness
    │      ├── atomicity
    │      ├── visibility
    │      └── ordering
    │
    └── progress / liveness
           ├── deadlock
           ├── livelock
           └── starvation
```

Không có một keyword duy nhất giải quyết mọi vấn đề.

Chapter tiếp theo mới bắt đầu học các công cụ cụ thể:

```text
synchronized
Lock
Atomic
Concurrent Collection
...
```

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="problem-experiments">10. Các experiment của phần Concurrency Problems</a>

<details>
<summary>Click for details</summary>

| README section | Controller method | Endpoint | Mục đích |
| --- | --- | --- | --- |
| `#shared-state-race-condition` | `ConcurrencyProblemController#raceCondition()` | `GET /concurrency/problem/race-condition` | Tạo lost update deterministic |
| `#happens-before` | `ConcurrencyProblemController#startJoinHappensBefore()` | `GET /concurrency/problem/happens-before-start-join` | Quan sát memory guarantee của start/join |
| `#volatile-publication` | `ConcurrencyProblemController#volatilePublication()` | `GET /concurrency/problem/volatile-publication` | Publish plain data thông qua volatile flag |
| `#volatile-publication` | `ConcurrencyProblemController#volatileIsNotAtomic()` | `GET /concurrency/problem/volatile-not-atomic` | Chứng minh volatile không làm compound update atomic |
| `#progress-problems` | `ConcurrencyProblemController#deadlockRisk()` | `GET /concurrency/problem/deadlock-risk` | Quan sát circular lock dependency nhưng cleanup được |
| `#deadlock-prevention` | `ConcurrencyProblemController#deadlockPrevention()` | `GET /concurrency/problem/deadlock-prevention` | Phá circular wait bằng global lock ordering |
| `#progress-problems` | `ConcurrencyProblemController#boundedLivelock()` | `GET /concurrency/problem/livelock` | Minh họa active-without-progress theo cách bounded |

Sau phần này cần tự trả lời được:

1. Vì sao `count++` có thể mất update?
2. Vì sao race condition không đồng nghĩa "lần nào chạy cũng sai"?
3. JMM giải quyết vấn đề gì?
4. Happens-before khác thứ tự thời gian như thế nào?
5. `volatile` đảm bảo gì và không đảm bảo gì?
6. Vì sao không nên giải thích volatile chỉ bằng "đọc/ghi trực tiếp RAM"?
7. Vì sao một stress test không bắt được reordering không chứng minh code đúng?
8. Deadlock, livelock và starvation khác nhau ở điểm nào?
9. Bốn Coffman conditions là gì và global lock ordering phá điều kiện nào?
10. Safe publication khác gì với việc chỉ "đã new object xong"?
11. `final` field cung cấp guarantee gì, và vì sao `this` escape trong constructor là design nguy hiểm?

</details>

- [Quay lại đầu trang](#back-to-top)

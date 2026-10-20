<a id="back-to-top"></a>

# Trạng thái và đồng thời

## Menu
- [Aspect dùng chung với nhiều lời gọi đồng thời](#aspect-concurrency-001)
- [Biến khả biến, tranh chấp dữ liệu và tính nhìn thấy](#aspect-concurrency-002)
- [Tách dữ liệu cấu hình bất biến khỏi trạng thái mỗi lần gọi](#aspect-concurrency-003)
- [Trạng thái theo luồng và giới hạn với nhóm luồng](#aspect-concurrency-004)
- [Dọn dữ liệu ngữ cảnh để tránh rò rỉ giữa các yêu cầu](#aspect-concurrency-005)
- [Truyền ngữ cảnh qua tác vụ bất đồng bộ có kiểm soát](#aspect-concurrency-006)
- [Khóa trong advice và nguy cơ deadlock hoặc tăng tranh chấp](#aspect-concurrency-007)
- [Tái nhập, lời gọi lồng nhau và aspect tự can thiệp](#aspect-concurrency-008)
- [Thiết kế aspect an toàn khi chạy song song](#aspect-concurrency-009)

## <a id="aspect-concurrency-001">Aspect dùng chung với nhiều lời gọi đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Aspect dùng chung với nhiều lời gọi đồng thời

Một **aspect** trong **Aspect-Oriented Programming (AOP – lập trình hướng khía cạnh)** có thể được áp dụng cho rất nhiều join point và nhiều lời gọi. Nếu cùng **một instance aspect** được dùng chung, các advice của nó có thể được thực thi **đồng thời trên nhiều thread**. Điều này thường xảy ra với các concern như logging, tracing, metrics, authorization, cache, retry, transaction observation và audit.

**Vấn đề không nằm ở việc “dùng chung” tự nó nguy hiểm**, mà nằm ở việc nhiều lần gọi cùng truy cập **trạng thái mutable** của aspect hoặc dependency mà không có hợp đồng đồng thời phù hợp. Một aspect dùng chung nhưng không giữ trạng thái biến đổi giữa các lời gọi có thể rất dễ an toàn. Ngược lại, chỉ một field như `currentUser`, `startedAt`, `lastRequest` hoặc `int count` đã có thể gây **ghi đè dữ liệu, sai thời gian đo, mất lượt cập nhật hoặc rò dữ liệu giữa người dùng**.

Mục tiêu của bài:
- Hiểu sự khác nhau giữa **aspect instance scope** và **invocation scope**.
- Biết những field/đường dependency nào thực sự được chia sẻ.
- Phân biệt **race condition**, **visibility**, **atomicity**, **ordering** và **reentrancy**.
- Chọn **biến cục bộ**, **đối tượng bất biến**, **atomic**, **khóa**, **state store** hoặc **context propagation** đúng yêu cầu.
- Bảo toàn kết quả, ngoại lệ, side effect và yêu cầu bảo mật khi aspect chạy đồng thời.
- Kiểm chứng bằng ví dụ **Java 17 chạy độc lập** cho hai lời gọi đồng thời trên cùng aspect instance: bộ đếm không an toàn mất lượt, bộ đếm `AtomicInteger` cho kết quả đúng.

Bài tập trung vào **AOP nói chung, cả cơ bản và nâng cao**. Ví dụ dùng wrapper Java để diễn đạt advice/continuation; không phải mọi nền tảng AOP dùng cùng cơ chế proxy, cú pháp pointcut hay vòng đời instance.

## 1. Khi nào một aspect thực sự “dùng chung”?

Trong AOP:
- **Aspect** nhóm một hoặc nhiều pointcut/advice cho một concern.
- **Join point** là điểm chương trình có thể được can thiệp.
- **Pointcut** chọn những điểm phù hợp.
- **Advice** là hành vi before, after, around hoặc dạng khác được hỗ trợ.
- **Aspect instance scope/lifecycle** xác định **instance nào** chạy advice cho một lần gọi.
- **Invocation** là một lần thực thi qua điểm can thiệp cụ thể.

Một aspect có thể được cấp một instance chung cho tất cả lời gọi phù hợp, một instance theo target, theo đối tượng thực thi, theo flow hoặc cách khác nếu mô hình hỗ trợ.

~~~text
Thread T1 ── invocation A ──┐
                            │
Thread T2 ── invocation B ──┼──> cùng SharedAspect instance
                            │        |
Thread T3 ── invocation C ──┘        +--> advice(...)
                                     +--> shared fields?
                                     +--> dependencies?
~~~

**Một instance không có nghĩa mỗi lần chỉ một thread được vào.** Nếu không có cơ chế khóa/serialization, các advice trong object dùng chung có thể chạy xen kẽ.

Cũng không được giả định **một instance được tạo cho mỗi target sẽ chỉ có một thread**: một target dùng chung vẫn có thể nhận nhiều lời gọi đồng thời. Ngược lại, một aspect dùng chung giữa nhiều target không nhất thiết chứa shared mutable state.

### 1.1 Scope của instance khác scope của dữ liệu

Có ít nhất bốn phạm vi cần tách:
1. **Per-invocation:** biến cục bộ, tham số và context chỉ sống trong một lần advice.
2. **Per-aspect-instance:** field và dependency được lưu trong instance; sống qua nhiều lời gọi dùng instance đó.
3. **Per-target/per-flow/per-principal:** state được phân vùng theo đối tượng hoặc ngữ cảnh nghiệp vụ, có thể lưu trong/ngoài aspect.
4. **External/global/distributed:** cache, metrics aggregator, database hoặc store chung qua nhiều instance/process.

Bài toán đồng thời chỉ được giải khi xác định **ai thật sự đọc/ghi cùng một state**. Một `private final Repository repository` có thể trỏ đến một repository mutable dùng chung; `final` chỉ làm tham chiếu field không đổi, không biến đối tượng đích thành immutable.

### 1.2 Shared aspect và singleton không đồng nghĩa stateless

Một instance dùng chung có thể:
- **Stateless:** advice chỉ đọc args/current context, dùng biến local, gửi event qua sink an toàn.
- **Stateful được kiểm soát:** lưu `AtomicInteger`, immutable policy snapshot hoặc một máy trạng thái có khóa.
- **Stateful không an toàn:** lưu `currentRequest` trong field để dùng sau `proceed`, dùng `HashMap` mutable đồng thời không bảo vệ, tăng `int` không đồng bộ.

Cách tổ chức các concern không chỉ nằm ở số lượng object được tạo. Nếu aspect A stateless nhưng gọi service B có mutable state và B không an toàn đa luồng, toàn đường gọi vẫn có nguy cơ race.

## 2. Tại sao nhiều lời gọi có thể chồng lấn?

Xem một around advice được dùng chung:

~~~text
around invocation:
    before()
    result = proceed()
    after(result)
    return result
~~~

Khi T1 đang chờ I/O trong `proceed()`, T2 có thể vào **cùng instance advice** và thực hiện một invocation khác. Nếu `before` của hai lời gọi cùng sửa một field, invocation sau có thể thay đổi dữ liệu invocation trước vẫn cần.

Một thứ tự thực thi hợp lệ:

~~~text
T1: before(A) ------ proceed(A) -------- after(A)
T2:       before(B) ------ proceed(B) ------ after(B)
                   ^ hai invocation chồng lấn
~~~

Không có bảo đảm advice được chạy tuần tự chỉ vì nó là một phương thức của cùng object. Thứ tự dòng log giữa thread cũng không tự chứng minh lỗi. Cần kiểm tra **bất biến dữ liệu** và **thứ tự trong từng invocation**, không đòi mọi thread dùng một thứ tự tổng giả.

## 3. Sai lầm kinh điển: field chứa state riêng của mỗi lời gọi

Giả sử một aspect đo thời gian:

~~~java
// MẢNH MINH HỌA LỖI: không phải chương trình chạy độc lập.
class UnsafeTimingAspect {
    private long startedAtNanos;

    String around(java.util.function.Supplier<String> next) {
        startedAtNanos = System.nanoTime();
        try {
            return next.get();
        } finally {
            long elapsed = System.nanoTime() - startedAtNanos;
            record(elapsed);
        }
    }

    void record(long nanos) {
        // chuyển cho bộ nhận metric
    }
}
~~~

Nếu T1 bắt đầu A, ghi `startedAtNanos=t1`, sau đó T2 bắt đầu B và ghi `startedAtNanos=t2`, T1 kết thúc sẽ lấy **t2** làm mốc cho A. Sai dù bản thân `System.nanoTime()` hoạt động đúng.

**Sửa bằng biến cục bộ theo invocation**:

~~~java
// MẢNH MINH HỌA: mỗi stack frame có startedAtNanos riêng.
class SafeTimingAspect {
    String around(java.util.function.Supplier<String> next) {
        long startedAtNanos = System.nanoTime();
        try {
            return next.get();
        } finally {
            long elapsed = System.nanoTime() - startedAtNanos;
            record(elapsed);
        }
    }

    void record(long nanos) {
        // bộ nhận metric chịu trách nhiệm thread safety
    }
}
~~~

Biến `startedAtNanos` thuộc **mỗi lần gọi**, không bị T2 ghi đè trong frame của T1. Với lời gọi lồng nhau, mỗi frame cũng có local riêng. Nếu `record` ghi vào một sink mutable dùng chung, sink vẫn cần hợp đồng đồng thời của riêng nó.

### 3.1 Cùng nguyên lý với identity và dữ liệu nhạy cảm

Sai lầm nguy hiểm hơn:

~~~text
aspect.currentUser = callerUser
proceed()
authorizeUsing(aspect.currentUser)
~~~

Invocation B có thể thay `currentUser` trong lúc invocation A đang chạy, khiến kiểm tra quyền A dùng danh tính B. **Không bao giờ dùng một field dùng chung để lưu principal/tenant/request payload tạm của lời gọi hiện tại.**

Phải lấy identity từ ngữ cảnh đã xác thực của chính invocation đó và mang qua đường xử lý bằng cơ chế đáng tin. Nếu cần cache policy theo user, key phải gắn **principal, action, resource, version/expiry** phù hợp; tránh nhầm thông tin giữa người dùng/tenant.

## 4. Race condition, atomicity, visibility và ordering

Bốn khái niệm thường bị gọi chung là “thread safety” nhưng giải quyết những việc khác nhau.

### 4.1 Race condition

Kết quả phụ thuộc vào thứ tự xen kẽ các thao tác của nhiều thread. Ví dụ `calls++` trên một field chung có thể mất cập nhật vì hai thread cùng đọc giá trị cũ, cùng cộng, rồi cùng ghi.

~~~text
Ban đầu calls = 0

T1 reads 0         T2 reads 0
T1 computes 1      T2 computes 1
T1 writes 1        T2 writes 1

Mong đợi 2, thực tế có thể chỉ là 1.
~~~

Đây là **lost update**. Không phải cứ chạy thử một lần thấy kết quả 2 là code an toàn; race có thể chỉ lộ dưới một lịch thực thi khác.

### 4.2 Atomicity

Một thao tác hoặc nhóm thao tác phải được quan sát là **không chia cắt** theo hợp đồng.

- `AtomicInteger.incrementAndGet()` là thao tác tăng có bảo đảm nguyên tử cho một biến.
- `counter++` trên `int` thông thường không có atomic read-modify-write.
- `volatile int counter` cũng **không** khiến `counter++` thành thao tác nguyên tử.
- Với bất biến **nhiều field**, bảo vệ từng field bằng các atomic riêng chưa chắc đủ để bảo đảm chúng thay đổi **cùng nhau**.

Ví dụ nếu `successes + failures == attempts` cần luôn đúng tại một snapshot được công bố, ba bộ đếm độc lập có thể bị đọc ở các thời điểm xen kẽ khác nhau. Cần chọn hợp đồng snapshot/locking/immutable state phù hợp.

### 4.3 Visibility

Một thread ghi giá trị mới không có nghĩa thread khác **luôn thấy ngay** nếu không có quan hệ đồng bộ hóa đúng theo **Java Memory Model (JMM)**. Các cơ chế như `synchronized`, atomic/volatile với điều kiện thích hợp, `Future.get()`, các thao tác của thư viện concurrent cung cấp quan hệ **happens-before** theo hợp đồng của chúng.

**Không sửa race bằng `Thread.sleep(...)` hoặc thêm logging**. Chúng có thể thay đổi timing nhưng không phải thiết kế đồng bộ có thể chứng minh đúng.

### 4.4 Ordering

Compiler/JVM/CPU có thể thực hiện tối ưu hóa và tái sắp xếp trong giới hạn JMM. Điều cần xác định là **thứ tự quan sát được** giữa các thread qua cơ chế đồng bộ, không phải giả định source code ở mọi thread sẽ được đọc như một chuỗi chung tuyệt đối.

Một aspect đọc policy mutable với nhiều field cần snapshot nhất quán để tránh:
- Đọc `limit` phiên bản mới nhưng `policyVersion` phiên bản cũ.
- Đọc `enabled` true trong before và false ở after theo hai cấu hình khác nhau.
- Áp dụng tenant của invocation A cho dữ liệu B.

### 4.5 Deadlock và contention

Thêm khóa để ngăn race vẫn có thể gây lỗi:
- **Deadlock:** aspect A giữ L1 rồi gọi B cần L2; thread khác giữ L2 rồi gọi A cần L1.
- **Lock convoy/contension:** một global lock bao quanh `proceed()` làm tất cả request bị tuần tự hóa; đặc biệt tệ khi target thực hiện I/O.
- **Reentrancy:** advice cùng thread tái nhập một chuỗi khác; khóa hoặc state machine phải tính tới điều này.
- **Starvation:** một nhóm thread liên tục không được thực thi phần quan trọng.

Không khóa toàn bộ lời gọi chỉ vì cần tăng một bộ đếm. Nếu đủ, chỉ bảo vệ **một thao tác cập nhật nhỏ**; để `proceed` thực thi ngoài khóa.

## 5. Bảng chọn kỹ thuật lưu state

| Nhu cầu | Cách tiếp cận | Giới hạn cần nhớ |
| --- | --- | --- |
| Thời gian bắt đầu một invocation | Biến local | Không kéo dài qua async completion nếu không giữ context đúng |
| Cấu hình chỉ đọc | Object immutable dùng chung | Những object tham chiếu bên trong cũng phải immutable theo hợp đồng |
| Bật/tắt tính năng động | `AtomicReference<Policy>` hoặc giá trị volatile thích hợp | Snapshot phải nhất quán, không thay nhiều mutable field riêng lẻ |
| Đếm attempts chính xác trên một JVM | `AtomicInteger` / `AtomicLong` | Theo phạm vi instance; không tự là tổng toàn cụm |
| Tổng metric chịu ghi rất nhiều | `LongAdder` | `sum()` dưới cạnh tranh không là snapshot nguyên tử toàn bộ |
| Ánh xạ key tới state | `ConcurrentHashMap` và thao tác atomic phù hợp | Một chuỗi nhiều thao tác/map entries không tự là transaction |
| Máy trạng thái nhiều field | `synchronized`, lock hoặc immutable CAS state | Phải bảo toàn bất biến cho toàn nhóm field |
| Ngữ cảnh một request logic | Context tường minh, snapshot bất biến | Thread-local không tự đi theo async hoặc remote request |
| Cache/rate limit toàn cụm | Store chuyên trách/distributed coordination | Chọn consistency, TTL, partition, failure mode |
| Trạng thái theo target | Per-target instance hoặc registry keyed đúng domain | Một target vẫn có thể bị nhiều thread cùng gọi |
| Kênh log/metric | Sink có thread-safe contract | Không giả định mọi logger/exporter tự có cùng bảo đảm |

Không có lựa chọn “đồng thời tốt nhất” cho mọi aspect. Phương án phụ thuộc vào **bất biến cần giữ**, tốc độ, contention, vòng đời và khả năng giải thích cho người vận hành.

## 6. Ví dụ Java 17: hai lời gọi, một aspect instance, kết quả xác định

Ví dụ dưới đây có **hai lời gọi đồng thời** đi qua **cùng một instance aspect**. Một aspect lưu bộ đếm `int` và cố ý đồng bộ điểm đọc của hai thread bằng `CyclicBarrier`, để minh họa **lost update** một cách xác định. Một aspect khác dùng `AtomicInteger` đếm đúng số attempts. Một aspect không trạng thái chỉ dùng local `label` theo invocation.

**Quan trọng:** `CyclicBarrier` bên trong `UnsafeCounterAspect` là **thiết bị thử nghiệm tạo lịch xen kẽ**, tuyệt đối không phải kỹ thuật nên dùng trong advice production; nếu chỉ có một lời gọi, barrier hai bên có thể khiến nó chờ mãi. Việc sử dụng barrier đảm bảo **hai task đã đọc cùng giá trị cũ trước khi có task nào ghi**, nên có thể quan sát chính xác kết quả sai.

Lưu file `SharedAspectConcurrencyDemo.java`:

~~~java
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class SharedAspectConcurrencyDemo {
    @FunctionalInterface
    interface Work {
        String execute(String id);
    }

    static final class Business implements Work {
        @Override
        public String execute(String id) {
            if ("bad".equals(id)) {
                throw new IllegalArgumentException("bad request");
            }
            return "ok:" + id;
        }
    }

    // Chỉ dùng cho demo 2 thread: cố ý tạo lost update.
    static final class UnsafeCounterAspect {
        private int calls;
        private final CyclicBarrier bothHaveRead = new CyclicBarrier(2);

        void before() {
            int previouslySeen = calls;

            try {
                bothHaveRead.await();
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            } catch (BrokenBarrierException error) {
                throw new IllegalStateException(error);
            }

            calls = previouslySeen + 1;
        }

        int calls() {
            return calls;
        }
    }

    // Một instance dùng chung, nhưng cập nhật count bằng atomic.
    static final class SafeCounterAspect {
        private final AtomicInteger attempts = new AtomicInteger();

        String around(Supplier<String> proceed) {
            attempts.incrementAndGet();
            return proceed.get();
        }

        int attempts() {
            return attempts.get();
        }
    }

    // Stateless: mỗi invocation có một biến cục bộ.
    static final class StatelessLabelAspect {
        String around(String id, Supplier<String> proceed) {
            String localLabel = "[" + id + "]";
            return localLabel + proceed.get();
        }
    }

    static Work withAll(
            Work target,
            UnsafeCounterAspect unsafe,
            SafeCounterAspect safe,
            StatelessLabelAspect labels) {
        return id -> {
            unsafe.before();
            return labels.around(
                    id, () -> safe.around(() -> target.execute(id)));
        };
    }

    static Work withoutUnsafe(
            Work target,
            SafeCounterAspect safe,
            StatelessLabelAspect labels) {
        return id -> labels.around(
                id, () -> safe.around(() -> target.execute(id)));
    }

    public static void main(String[] args) throws Exception {
        Work target = new Business();

        UnsafeCounterAspect unsafe = new UnsafeCounterAspect();
        SafeCounterAspect safe = new SafeCounterAspect();
        StatelessLabelAspect labels = new StatelessLabelAspect();

        Work shared = withAll(target, unsafe, safe, labels);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first =
                    executor.submit(() -> shared.execute("A"));
            Future<String> second =
                    executor.submit(() -> shared.execute("B"));

            // Lấy Future theo thứ tự đã submit, không theo lịch thread.
            System.out.println("RESULT " + first.get());
            System.out.println("RESULT " + second.get());
        } finally {
            executor.shutdownNow();
        }

        System.out.println("UNSAFE calls=" + unsafe.calls());
        System.out.println("SAFE attempts=" + safe.attempts());

        // Nhánh lỗi dùng cùng safe/labels, không dùng barrier demo.
        Work safeOnly = withoutUnsafe(target, safe, labels);
        System.out.println("CASE error");
        try {
            safeOnly.execute("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }
        System.out.println("SAFE attempts=" + safe.attempts());
    }
}
~~~

Biên dịch và chạy với **JDK 17+**:

~~~bash
javac SharedAspectConcurrencyDemo.java
java SharedAspectConcurrencyDemo
~~~

Stdout được xác định bởi mã và hai `Future.get()`:

~~~text
RESULT [A]ok:A
RESULT [B]ok:B
UNSAFE calls=1
SAFE attempts=2
CASE error
CALLER bad request
SAFE attempts=3
~~~

### 6.1 Vì sao bộ đếm không an toàn bằng 1?

Trong `UnsafeCounterAspect.before()`:
1. T1 đọc `calls=0` vào `previouslySeen`.
2. T2 đọc `calls=0` vào `previouslySeen`.
3. Barrier cho cả hai qua sau khi **cùng đọc**.
4. T1 ghi `1`; T2 cũng ghi `1`, theo thứ tự nào cũng cho giá trị cuối `1`.
5. `Future.get()` hoàn thành giúp thread chính quan sát các hành động của task theo hợp đồng concurrent library.

Ở production không có barrier, `calls++` có thể đôi khi cho 2 và đôi khi mất lượt. Kịch bản được dựng để **chứng minh lịch gây mất cập nhật**, chứ không dự đoán mọi lần chạy thật luôn mất đúng một lượt.

### 6.2 Vì sao bộ đếm an toàn bằng 2 rồi 3?

`SafeCounterAspect` lưu **một** `AtomicInteger`. Cả T1 và T2 gọi `incrementAndGet()`, hai increment được đồng bộ nguyên tử, nên sau khi cả hai hoàn tất, `attempts=2`.

Sau đó `safeOnly.execute("bad")` cũng đi qua **cùng instance safe**, tăng lên 3 **trước khi target ném ngoại lệ**. Vì thế đây là bộ đếm **attempts**, không phải successes. Nếu muốn đếm thành công, phải tăng sau khi `proceed.get()` trả về bình thường và đặt tên metric phù hợp.

### 6.3 Vì sao kết quả A và B không bị lẫn?

`StatelessLabelAspect` tạo `localLabel` ở **stack frame của invocation**. T1 giữ `[A]`, T2 giữ `[B]`, không có field mutable như `lastId` để hai thread tranh nhau. Có một instance `labels` duy nhất được dùng bởi cả hai mà kết quả vẫn đúng.

`Future.get()` được gọi theo thứ tự `first`, `second` để stdout có thứ tự **cố ý**, không giả định task A thực hiện xong trước task B.

### 6.4 Đây là AOP mô phỏng, không là weaving toàn cục

Chương trình dùng **wrapper** để lắp chuỗi advice vào `Work.execute`. Nó không khai báo pointcut của một bộ weaver hay instrument bytecode của `Business`. Nếu code gọi trực tiếp `target.execute(...)`, nó **bypass các wrapper**. Trong mô hình weaving trực tiếp method execution, tập join point có thể khác.

Tính an toàn của `AtomicInteger` không phụ thuộc vào cú pháp AOP; nó là đặc tính của cơ chế cập nhật state trong Java. Phạm vi áp dụng advice mới quyết định **những lần gọi nào được đếm**.

## 7. Around advice, `proceed` và trạng thái từng invocation

Một around advice thường cần nhớ:
- Mốc bắt đầu.
- Số lần retry trong **lần gọi logic** này.
- Context tracing.
- Một status ghi sau completion.
- Thông tin để cleanup trong `finally`.

Nếu mọi dữ liệu này chỉ thuộc một invocation, dùng local/closure thường đơn giản nhất:

~~~java
// MẢNH MINH HỌA, không phải lớp chạy độc lập.
String around(java.util.function.Supplier<String> proceed) {
    long start = System.nanoTime();
    try {
        return proceed.get();
    } finally {
        long duration = System.nanoTime() - start;
        emitLatency(duration);
    }
}
~~~

`finally` chạy khi `proceed` trả bình thường hoặc ném exception đồng bộ, nên thích hợp cho cleanup quanh đoạn xử lý **đồng bộ**. Nếu method trả `CompletionStage` ngay, `finally` có thể chạy **trước khi tác vụ bất đồng bộ hoàn tất**. Muốn đo end-to-end completion, cần buộc xử lý vào completion hook hoặc API async phù hợp.

### 7.1 Không dùng shared field làm continuation

Sai hướng:

~~~text
aspect.currentProceed = thisInvocation.proceed
aspect.currentArgs = args
...
aspect.currentProceed()
~~~

T2 có thể thay `currentProceed` khi T1 đang giữ instance aspect, khiến T1 tiếp tục lời gọi của T2 hoặc dùng args sai. Đây là lỗi nghiêm trọng cả về tính đúng đắn và bảo mật.

Mỗi invocation nên giữ continuation/args/context trong **tham số hoặc local state**, trừ khi một state holder có key/scope và đồng bộ đúng được thiết kế rõ ràng.

### 7.2 Multiple proceeds và retry

Trong một số mô hình AOP, around advice có thể gọi continuation nhiều lần. Mỗi lần có thể lặp nghiệp vụ, side effects, transaction hoặc advice bên trong. Nếu cần retry:
- State số attempt nên thuộc **một request logic** hoặc workflow được xác định.
- Dùng deadline và giới hạn attempts.
- Phân loại lỗi transient, không retry vô hạn.
- Phân biệt `requestCount`, `attemptCount`, `executionCount`.
- Yêu cầu idempotency/dedup nếu thao tác có tác dụng phụ.
- Kiểm tra thứ tự `Retry(Transaction(Target))` và `Transaction(Retry(Target))`.

Một bộ đếm dùng chung đúng thread-safe vẫn có thể **đếm sai đơn vị**, nếu đặt vào sai vị trí của chuỗi advice.

## 8. Đồng thời không có nghĩa mọi advice chạy cùng một lúc

Một aspect có thể thực hiện một thao tác blocking, nhưng không nên giả định toàn bộ hệ thống bị tuần tự hóa. Cần xét ranh giới:
- Advice dùng shared state và khóa quanh đoạn cập nhật nhỏ.
- Advice gọi `proceed` có I/O lâu.
- Interceptor khác bên ngoài có thể short-circuit.
- Một callback từ target có thể quay lại advice trên cùng hoặc thread khác.
- Một logger/exporter có queue và thread nội bộ.

Ví dụ chuỗi:

~~~text
Authorization(Cache(Metrics(Target)))
~~~

Nếu Cache trả hit, Metrics bên trong không được vào. **Thread-safe** không làm Metrics tự thấy cache hit. Nếu Auth dùng shared boolean `lastAllowed` thiếu phân vùng, cache hit còn có thể dùng quyền của caller khác.

Vì vậy kiểm thử cần cả **concurrency correctness** lẫn **coverage correctness**: đúng invocation được vào, đúng thứ tự/nhánh, state đúng scope.

## 9. Chọn AtomicInteger, LongAdder, locks hay immutable snapshot?

### 9.1 AtomicInteger cho một counter chính xác

~~~java
import java.util.concurrent.atomic.AtomicInteger;

final class AttemptCounter {
    private final AtomicInteger attempts = new AtomicInteger();

    int recordAttempt() {
        return attempts.incrementAndGet();
    }

    int snapshot() {
        return attempts.get();
    }
}
~~~

Đây là **mảnh khai báo riêng**. `incrementAndGet` có linearization point và trả số mới; phù hợp khi cần thứ tự cập nhật trên một biến. Nếu có nhiều JVM, mỗi object đếm **trong phạm vi instance/JVM**, không tạo một counter phân tán.

### 9.2 LongAdder cho thống kê contention cao

~~~java
import java.util.concurrent.atomic.LongAdder;

final class ThroughputCounter {
    private final LongAdder attempts = new LongAdder();

    void increment() {
        attempts.increment();
    }

    long approximateSnapshot() {
        return attempts.sum();
    }
}
~~~

Đây là **mảnh riêng**, không thay thế lớp trong demo. `LongAdder` tối ưu cho nhiều cập nhật tranh chấp; giá trị `sum()` khi đang concurrent **không phải snapshot nguyên tử tuyệt đối**. Không dùng nó khi cần cấp ID duy nhất hoặc đảm bảo đếm chính xác tại mọi thời điểm xen kẽ.

### 9.3 Khóa cho bất biến nhiều field

Một máy trạng thái circuit breaker có thể đồng thời cập nhật `status`, `failures`, `openedAt`. Nếu các field phải nhất quán như một giao dịch trong bộ nhớ, cần khóa hoặc chuyển trạng thái immutable bằng CAS đúng cách. Chỉ thêm `volatile` riêng lẻ chưa đảm bảo invariant toàn bộ.

**Tránh giữ khóa khi gọi `proceed` hoặc gọi service không kiểm soát**, vì có thể gây deadlock, reentrancy và contention cực lớn. Thông thường cập nhật state ngắn trong vùng bảo vệ, rồi gọi nghiệp vụ ngoài khóa.

### 9.4 Cấu hình động bất biến

Nếu advice cần policy có thể thay đổi:

~~~java
record Policy(boolean traceEnabled, int limit) {}

java.util.concurrent.atomic.AtomicReference<Policy> current =
        new java.util.concurrent.atomic.AtomicReference<>(
                new Policy(false, 100));
~~~

Đây là **mảnh khai báo**, được đặt trong context phù hợp của lớp hoặc method. Một invocation có thể lấy `Policy policy = current.get()` **một lần**, rồi dùng snapshot ấy xuyên suốt xử lý. Tránh lần đọc trước lấy trace flag phiên bản cũ, lần đọc sau lấy limit phiên bản mới mà logic yêu cầu chúng đi cùng nhau.

Cấu hình mới phải được validate trước khi publish; `AtomicReference` không làm đối tượng mutable chứa bên trong tự thread-safe.

## 10. ThreadLocal, context và bảo mật trong thread pool

Một phương pháp quen thuộc là dùng `ThreadLocal` để giữ correlation ID. Nhưng `ThreadLocal` là **per Java thread**, không phải per logical request. Thread pool có thể tái sử dụng thread cho request khác, và một request async có thể nhảy qua nhiều thread.

Nếu quên cleanup, user B có thể đọc context cũ của user A. Nếu lưu token hoặc secret, hậu quả nghiêm trọng hơn một dòng log sai.

Mẫu quản lý context:

~~~java
// MẢNH MINH HỌA: TRACE_ID được khai báo trong cùng lớp.
static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

static void withTrace(String id, Runnable action) {
    String previous = TRACE_ID.get();
    TRACE_ID.set(id);
    try {
        action.run();
    } finally {
        if (previous == null) {
            TRACE_ID.remove();
        } else {
            TRACE_ID.set(previous);
        }
    }
}
~~~

Phục hồi `previous` giúp nested scope đúng khi một advice tái nhập. Không chỉ `set(null)` rồi mặc định không còn resource; `remove()` thể hiện ý định xóa value của thread khi không có scope ngoài.

**Quan trọng:** code trên không tự truyền context sang task thực thi trên executor khác. Đối với async, phải chọn cơ chế context propagation được hỗ trợ và truyền **dữ liệu tối thiểu**, không giữ nguyên cả HTTP request, user object hoặc secrets quá vòng đời cần thiết.

### 10.1 ThreadLocal không phải cách sửa shared-field race tổng quát

Chuyển `currentUser` từ field aspect sang `ThreadLocal` có thể tránh ghi đè giữa hai thread **trong một đoạn đồng bộ**, nhưng chưa giải quyết:
- Async completion trên thread khác.
- Scheduler dùng lại thread cho request khác.
- Nested invocation có context khác.
- Context cần hủy sau cancellation.
- Data propagation qua process/remote call.

Nếu có thể, **truyền context như tham số** hoặc snapshot của invocation vẫn dễ chứng minh hơn.

## 11. Reentrancy: một lời gọi có thể kích hoạt chính advice lại

Một LoggingAspect có pointcut quá rộng có thể gọi `Logger.write()`, mà `write()` cũng nằm trong tập điểm được chọn:

~~~text
Business -> LoggingAdvice
              |
              v
           Logger.write()
              |
              v
           LoggingAdvice (again)
              |
              +--> ...
~~~

Điều này có thể tạo đệ quy vô hạn ngay cả **một thread**. Khi nhiều thread, số sự kiện có thể tăng nhanh hơn và làm các shared buffers/queues quá tải.

**Giải pháp ưu tiên:** loại những helper/sink hạ tầng khỏi phạm vi advice quan sát chung, hoặc dùng một sink tầng thấp không quay lại đường proxy/weaving tương tự. Không dùng cờ guard để bỏ qua authorization, validation hay audit bắt buộc.

Nếu dùng reentrancy guard cho một concern **được phép giảm bớt**, phải restore context trong `finally` và tính tới async/thread pool. Một guard theo thread không chặn vòng đi qua queue/remote service rồi quay lại thread khác.

### 11.1 Tái nhập khác với concurrent invocation

- **Concurrency:** T1 và T2 vào cùng aspect instance.
- **Reentrancy:** một invocation kích hoạt lời gọi lồng nhau quay vào advice khi invocation ngoài chưa hoàn tất.
- **Recursion:** một đường gọi quay lại chính nó; có thể có base case hợp lệ hoặc vô hạn.

Một aspect có thể thread-safe cho nhiều thread nhưng **không reentrant-safe**. Ví dụ khóa `ReentrantLock` cho phép cùng thread lấy lại khóa, nhưng state machine của advice vẫn có thể bị ghi đè hoặc logic đi vào nhánh sai.

## 12. Stateful aspect theo target vẫn có thể bị gọi đồng thời

~~~text
Target A ----> Aspect instance A [counter A]
Target B ----> Aspect instance B [counter B]
~~~

Phân vùng state theo target giúp A/B không giẫm dữ liệu nhau. Tuy nhiên, nếu target A được nhiều thread dùng chung, counter A vẫn là shared mutable state. Per-target **không tự là per-invocation** và không bảo đảm thread safety.

Các câu hỏi cần hỏi:
- Một target có nhiều proxy trỏ tới không? State nên theo **proxy** hay **target**?
- Hai object biểu diễn cùng logical account có cần **chia sẻ** rate limit không?
- State được key theo **object identity** hay ID nghiệp vụ?
- Object target có thể bị garbage collect không, hay registry giữ strong reference mãi?
- Khi đổi target/phiên bản aspect, state cũ có migrate/reset không?
- Nếu cần tổng state toàn cụm, một aspect instance trong JVM có đủ không?

Không dùng `Map<Target, State>` vô hạn mà không có lifecycle/eviction. Nếu map giữ strong references, nó có thể giữ target sống và gây rò bộ nhớ; weak-key cũng cần hiểu giá trị có tham chiếu ngược tới key hay không.

## 13. Dữ liệu mutable bên ngoài aspect

Một advice có thể trông stateless:

~~~text
around invocation:
    policy.check(args)
    metrics.increment()
    return proceed()
~~~

Nhưng `policy` và `metrics` là dependency. Nếu chúng dùng `HashMap`, `ArrayList` hoặc bộ đếm mutable không đồng bộ trong một đối tượng dùng chung, đường gọi vẫn nguy hiểm.

Cần kiểm tra **toàn bộ chuỗi**:
1. Aspect instance và fields.
2. Các helper được tái sử dụng.
3. Metrics/logging/cache store.
4. Các callback và event listeners.
5. Context/ThreadLocal.
6. Target và trạng thái bên trong target (nếu advice gọi tới).
7. Bất kỳ global/static registry hoặc class loader scope nào.

**Dùng `final` cho tham chiếu** hữu ích cho immutability của cấu trúc tham chiếu, nhưng không tự giải quyết mutable state bên trong object đích.

## 14. Phạm vi đo metrics trong nhiều aspect đồng thời

Một hệ thống có thể có:

~~~text
Metrics(Retry(Transaction(Business)))
Retry(Transaction(Metrics(Business)))
~~~

Ở cấu trúc đầu, Metrics có thể đếm **lời gọi logic** và đo tổng thời gian qua nhiều attempts. Ở cấu trúc sau, Metrics có thể đếm **từng attempt**, tùy ranh giới continuation của công cụ.

Khi có nhiều request đồng thời, counter an toàn mới chỉ đảm bảo **không mất cập nhật**. Nó không thể tự đảm bảo **đếm đúng đơn vị** nếu đặt sai chỗ.

Tên các số liệu nên rõ:
- `incoming_requests`: số request qua boundary đã chọn.
- `attempts`: tổng số lượt thử có thể gồm retry.
- `successful_calls`: số invocation trả bình thường theo định nghĩa.
- `target_executions`: số lần target thật được vào.
- `cache_hits`: lần trả từ cache, có thể không chạy target.
- `completed_operations`: hoàn tất business hay async completion, không chỉ trả future.

Nếu outer cache short-circuit, metric bên trong không chạy; nếu logging dùng thread-local trace ID từ request trước, attribution có thể sai dù counter nguyên tử.

## 15. Đồng thời, exception và cleanup

Một shared aspect có thể cùng lúc xử lý nhánh thành công của T1 và nhánh lỗi của T2. Vì vậy:
- Không lưu `lastException` dùng chung như thông tin lỗi của invocation hiện tại.
- Không lưu `success=true` vào field aspect rồi dùng trong `finally` nếu một invocation khác có thể thay nó.
- Dùng `try/finally` cho cleanup **riêng invocation**.
- Nếu advice phát sinh exception, không vô tình che exception gốc, đặc biệt từ logging/exporter không bắt buộc.
- Nếu checked exceptions/return type bị advice biến đổi, tuân thủ hợp đồng phương thức.
- Không thay toàn bộ return value/exception chỉ để “khắc phục race” nếu như vậy làm sai nghiệp vụ.

Với phương thức trả `CompletionStage`, phần advice sau khi nhận stage **chưa chắc là kết thúc của công việc**. Muốn ghi lỗi/latency của công việc async, phải xử lý đúng callback completion và propagation, không gắn state tạm vào một shared field rồi đợi callback.

### 15.1 Khác nhau giữa fail-open và fail-closed

Một logging/metrics concern không bắt buộc có thể có chính sách **bỏ qua ghi nhận khi sink lỗi**, nhưng một aspect kiểm tra quyền hoặc audit bắt buộc có hợp đồng khác. Không dùng cùng một `try/catch` nuốt mọi lỗi của mọi concern.

Nếu policy bảo mật cần fail-closed, việc một shared mutable flag race dẫn tới “allow” khi không kiểm chứng được là lỗi nghiêm trọng. Quy tắc bảo mật phải độc lập với trạng thái tình cờ của invocation trước.

## 16. Chiến lược kiểm thử concurrent advice

Một test đơn luồng có thể pass hàng nghìn lần nhưng vẫn không phát hiện race. Cần kiểm tra:

| Ca | Điều cần chứng minh |
| --- | --- |
| Nhiều thread dùng chung aspect instance | Không ghi đè invocation-local state |
| Counter đơn giản | Tổng attempts không bị lost update |
| Trả giá trị của A/B cùng lúc | Không đổi args/result giữa hai caller |
| Target throw trong một thread | Error state không rò sang thread khác |
| Nested invocation | Local context không bị ghi đè bởi lần lồng nhau |
| Snapshot config giữa call | Mỗi invocation dùng một policy nhất quán |
| Cùng target, nhiều caller | Per-target state được đồng bộ đúng |
| Thread pool tái sử dụng | Không mang context/user từ request trước |
| Async completion ở thread khác | Tracing/cleanup đúng thời điểm, context đúng |
| Retry nhiều lần | Không double-count sai định nghĩa |
| Cache hit/short-circuit | Coverage và security không bị bỏ qua |
| Logging sink lỗi | Không tạo vòng reentrancy/log storm |
| Lock order khác nhau | Không deadlock |
| Nhiều JVM | State local và distributed aggregate được phân biệt |
| Rollout aspect mới | State migrate/reset theo chính sách, không trộn phiên bản |
| Sensitive input | Không log hoặc giữ token/PII ngoài hợp đồng |

### 16.1 Dùng điểm đồng bộ để tái hiện race một cách có kiểm soát

`CountDownLatch`, `CyclicBarrier` và `Future.get()` giúp viết bài kiểm thử có các điểm xen kẽ rõ thay vì may rủi do scheduler. Nhưng **đừng chèn barrier bắt buộc hai bên vào advice production**: một request cô lập có thể bị chặn vĩnh viễn.

Để kiểm tra code thực tế, dùng stress tests với nhiều lần lặp, các assertions về **bất biến cuối**, race testing phù hợp và giám sát thread dumps khi nghi deadlock. Một test chỉ thấy log đúng thứ tự không thay thế kiểm tra dữ liệu.

### 16.2 Không tạo yêu cầu thứ tự tổng vô ích

Hai invocation độc lập có thể hoàn tất theo thứ tự B rồi A. Điều cần giữ là:
- Kết quả của A thuộc về A, kết quả B thuộc về B.
- Thứ tự advice của **mỗi invocation** đúng hợp đồng.
- Shared counters/state thỏa invariant.
- Không lộ context hoặc dữ liệu.
- Những thao tác có quan hệ nghiệp vụ (transaction/order) được đồng bộ thích hợp.

Trừ khi ứng dụng **có yêu cầu thứ tự toàn cục** thật sự, không đặt một global lock quanh mọi advice chỉ để log hiển thị thành một hàng đẹp.

## 17. Checklist thiết kế shared aspect

Trước khi triển khai một aspect dùng chung:

- [ ] Xác định aspect được khởi tạo **một lần, theo target, flow hay scope nào**.
- [ ] Xác định **những thread nào** có thể vào instance đồng thời.
- [ ] Liệt kê tất cả instance/static fields và mutable dependencies.
- [ ] Đưa request-local data về tham số/local/immutable context.
- [ ] Không giữ principal/tenant/current request trong shared field tạm.
- [ ] Chọn atomic/lock/immutable snapshot cho state thật sự phải chia sẻ.
- [ ] Không hiểu nhầm `volatile` là bảo đảm `counter++` nguyên tử.
- [ ] Với nhiều field, thiết kế invariant và cách đọc snapshot nhất quán.
- [ ] Không giữ khóa khi gọi `proceed` hoặc callback ngoài không kiểm soát.
- [ ] Biết số liệu đang đếm request, attempt, execution hay success.
- [ ] Nếu dùng `ThreadLocal`, restore/remove trong `finally` và kiểm tra async.
- [ ] Kiểm tra pointcut không tự chặn logger/metric sink gây recursion.
- [ ] Giới hạn cache/registry và quản lý retention, eviction, memory.
- [ ] Không suy rằng một AtomicInteger giải quyết state toàn cụm.
- [ ] Test concurrency, reentrancy, exception, cancellation, short-circuit.
- [ ] Đo overhead/lock contention và hoạt động hạ tầng quan sát.
- [ ] Kiểm tra chính sách bảo mật và audit không bị bypass.
- [ ] Có cách phát hiện lỗi, rollback và kiểm chứng version aspect lúc deploy.

## 18. Những ngộ nhận phổ biến

1. **“Một aspect instance thì advice tự chạy tuần tự.”** Không: nhiều thread có thể vào cùng object.
2. **“Một aspect stateless là không có tác dụng phụ.”** Không: có thể gửi log/metric nhưng không giữ mutable invocation state.
3. **“`final` field đồng nghĩa dependency thread-safe.”** Sai nếu object được tham chiếu vẫn mutable.
4. **“`volatile int x; x++` là atomic.”** Sai: tăng gồm nhiều bước.
5. **“Counter `AtomicInteger` là số liệu toàn cụm.”** Không nếu mỗi JVM có instance riêng.
6. **“LongAdder luôn cho snapshot chính xác tức thời.”** Không với các lần tăng đang chạy.
7. **“Per-target nghĩa chỉ một thread.”** Một target dùng chung vẫn có concurrent calls.
8. **“Dùng `ThreadLocal` sẽ là per-request trong mọi hệ thống.”** Thread pool/async phá giả định đó.
9. **“Cứ dùng `synchronized` bao `proceed` là giải quyết hết.”** Có thể gây contention và deadlock.
10. **“Lời gọi sau của T1 chắc dùng `startedAt` của T1.”** Không nếu mốc được giữ trong shared field.
11. **“Một lúc chỉ có một after advice chạy.”** Nhiều invocation có thể đi vào after/error/finally đồng thời.
12. **“Concurrent logs không theo thứ tự nghĩa dữ liệu sai.”** Phải kiểm tra invariant, không suy từ log xen kẽ.
13. **“Retry chỉ cần đếm attempts nguyên tử.”** Còn idempotency và transaction boundary.
14. **“Kết quả async trả về là công việc đã hoàn tất.”** Thường chưa; cần completion hook đúng.
15. **“Guard bỏ qua advice tái nhập phù hợp mọi concern.”** Có thể bỏ qua security/audit bắt buộc.
16. **“Không thấy race ở test lần đầu nghĩa an toàn.”** Race có thể cần interleaving hiếm.
17. **“Chọn ConcurrentHashMap là mọi thao tác nhiều bước tự atomic.”** Không: phải dùng phép cập nhật/bảo vệ đúng hợp đồng.
18. **“Càng ít aspect instance càng luôn nhanh.”** Contention của shared mutable state có thể lớn hơn chi phí tạo/lưu instance.
19. **“Đọc config mới nhiều lần trong một request luôn tốt.”** Có thể tạo một request với policy nửa cũ nửa mới.
20. **“Bảo vệ counter là bảo vệ toàn bộ hợp đồng nghiệp vụ.”** Cần cả coverage, result/exception, side effects, privacy và scope đúng.

## 19. Câu hỏi ôn tập

**Câu 1.** Một instance aspect dùng chung cho 100 threads, advice chỉ tính duration bằng biến local rồi gọi metrics sink thread-safe. Đây có phải một thiết kế hợp lý?

**Đáp:** có thể hợp lý. Biến local không bị các invocation khác ghi đè; metrics sink phải thật sự giữ hợp đồng thread-safe, và phạm vi đo phải phù hợp sync/async.

**Câu 2.** Tại sao `volatile int calls` cộng bằng `calls++` chưa giải quyết lost update?

**Đáp:** volatile giúp visibility/ordering theo quy tắc JMM, nhưng read–modify–write không trở thành một thao tác nguyên tử. Dùng `AtomicInteger.incrementAndGet()` hoặc đồng bộ phù hợp.

**Câu 3.** Có một aspect per-target với counter trên target A, nhiều thread cùng gọi A. Có cần đồng bộ counter không?

**Đáp:** có nếu yêu cầu cập nhật chính xác. Per-target chỉ phân vùng state giữa A/B, không phân vùng giữa các thread trên cùng A.

**Câu 4.** Aspect stateless gọi một helper ghi vào `HashMap` mutable dùng chung không khóa. Toàn chương trình có an toàn không?

**Đáp:** không thể kết luận an toàn. Stateless của bản thân aspect không thay thế thread-safety của dependency được gọi.

**Câu 5.** Nếu advice dùng `ThreadLocal` cho correlation ID rồi xử lý async qua thread pool, có tự động giữ ID đúng không?

**Đáp:** không. Phải dùng cơ chế context propagation phù hợp và cleanup khi hoàn thành, kể cả cancellation/error.

**Câu 6.** Trong ví dụ, hai lần gọi concurrent đều chạy xong nhưng `unsafe.calls() == 1`. Điều gì được chứng minh?

**Đáp:** mất cập nhật do hai invocation cùng đọc 0 rồi cùng ghi 1. `Future.get()` không biến những bước cập nhật trước đó thành nguyên tử; nó chỉ giúp đợi/quan sát kết quả task.

**Câu 7.** Một `SafeCounterAspect` tăng `attempts` trước `proceed`, target ném lỗi. Counter có tăng không?

**Đáp:** có. Vì đây là counter cho **attempts**, không phải successes; một lần gọi đi tới aspect rồi ném lỗi vẫn là attempt.

**Câu 8.** Nếu Metrics nằm trong Cache, cache hit trả sớm, AtomicInteger có tự đếm cache hit không?

**Đáp:** không. Advice không được gọi trên nhánh đó. Thread-safety không thay thế đúng vị trí interception.

**Câu 9.** Shared AuthorizationAspect giữ `currentUser` field cho mỗi invocation. Nguy cơ gì?

**Đáp:** người dùng khác có thể ghi đè field khi concurrent, khiến kiểm tra quyền sai hoặc rò dữ liệu. Identity phải thuộc context của invocation và được xác minh từ nguồn tin cậy.

**Câu 10.** Khi nào nên dùng `LongAdder` thay `AtomicLong`?

**Đáp:** khi mục tiêu là thống kê tổng có contention cao và chấp nhận semantics snapshot của `sum()`; không dùng LongAdder nếu cần sequence ID hoặc yêu cầu nguyên tử cho từng giá trị trả về.

## 20. Kết luận

**Aspect dùng chung với nhiều lời gọi đồng thời** là thiết kế bình thường trong AOP, miễn các đường gọi có hợp đồng chia sẻ state rõ ràng:

1. **Giữ dữ liệu của invocation trong local/immutable context**, không đặt `currentRequest` hoặc `startedAt` vào shared field.
2. **Chia sẻ state có chủ đích**, chọn atomic, lock, state store hoặc immutable snapshot theo bất biến cần giữ.
3. **Phân biệt phạm vi và đơn vị đo**: per-instance, per-target, per-request, per-thread và toàn cụm không đồng nghĩa.
4. **Xem cả dependencies và lifecycle**: ThreadLocal, async, proxy bypass, reentrancy, caches, logging sinks và generated advice đều có thể làm thay đổi giả định đồng thời.
5. **Kiểm thử interleaving, outcome và security**: không chỉ thấy chương trình trả kết quả một lần là đủ.

Ghi nhớ: **một aspect dùng chung không bắt buộc phải có shared mutable state; nếu thật sự cần state dùng chung, hãy chứng minh đồng thời đúng bằng cấu trúc dữ liệu, bất biến và kiểm thử, không dựa vào lịch thực thi tình cờ.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-concurrency-002">Biến khả biến, tranh chấp dữ liệu và tính nhìn thấy</a>

<details>
<summary>Xem chi tiết</summary>

# Biến khả biến, tranh chấp dữ liệu và tính nhìn thấy trong AOP

## 1. Một aspect có thể phục vụ nhiều luồng

Trong **Aspect-Oriented Programming (AOP)**, aspect mô tả một mối quan tâm xuyên cắt qua **pointcut** (quy tắc chọn join point) và **advice** (logic áp dụng). Logging, tracing, authorization, metrics, cache, retry hoặc audit có thể được kích hoạt từ rất nhiều invocation đồng thời. Nếu cùng một **aspect/interceptor instance** chứa **mutable fields**, các lời gọi đến từ nhiều luồng có thể đọc và ghi cùng vùng trạng thái.

~~~text
Thread A -> Target.method -> Shared aspect object
                                  |
                                  +-- mutable counter
                                  +-- mutable cache
                                  +-- current request field
                                  |
Thread B -> Target.method --------+
~~~

Bốn câu hỏi khác nhau cần hiểu:

1. **Mutability:** trạng thái có thể thay đổi sau khi khởi tạo hay không?
2. **Data race:** các memory accesses xung đột có thiếu quan hệ đồng bộ hóa thích hợp không?
3. **Visibility/ordering:** luồng khác có được bảo đảm nhìn thấy những writes đã diễn ra theo **Java Memory Model (JMM)** không?
4. **Atomicity:** một operation như tăng counter hay kiểm tra rồi cập nhật có được thực hiện như một bước không thể bị xen ngang về mặt logic không?

Ngoài data race còn có **race condition ở tầng nghiệp vụ**, khi từng thao tác riêng lẻ thread-safe nhưng chuỗi check-then-act không được bảo vệ như một đơn vị.

**Điểm quan trọng:** AOP không tự làm advice thread-safe. Việc bytecode đã weave, proxy được tạo, hay pointcut match chính xác không làm biến mất các quy tắc concurrency của Java.

Bài trình bày AOP nói chung, không mặc định mọi runtime đều dùng một aspect instance singleton. Java 17 được dùng cho demo chạy độc lập, không cần một compiler AOP cụ thể.

## 2. Phân loại trạng thái trong lời gọi AOP

| Nơi giữ trạng thái | Ví dụ | Hệ quả |
|---|---|---|
| Local trong một advice invocation | startNanos, local attempt index | Mỗi invocation có local riêng; object mà nó tham chiếu vẫn có thể chia sẻ |
| Field của aspect/interceptor | calls, currentActor, Map | Nếu aspect instance shared thì nhiều threads dùng chung |
| Field của target | balance, mutable list | Có thể bị nhiều threads thao tác, độc lập với cách weave |
| Collaborator của aspect | logger buffer, cache service | Có thể mutable dù reference field là final |
| Static/global state | static counter, registry | Có thể chia sẻ qua nhiều instances |
| ThreadLocal | request context theo thread | Có lifecycle và propagation riêng, không tự theo task async |

Một **final reference** trỏ đến mutable object vẫn không bảo đảm object đó immutable:

~~~java
final List<String> events = new ArrayList<>();
~~~

Đây là snippet minh họa cần imports/class context. Final làm reference field không được gán lại sau khi khởi tạo hợp lệ, nhưng ArrayList vẫn có thể bị mutate bởi nhiều luồng và không thread-safe nếu dùng đồng thời mà không có synchronization.

### 2.1. Local variables không biến shared object thành local

~~~java
List<String> localRef = sharedList;
localRef.add("event");
~~~

Biến localRef được giữ riêng theo invocation, nhưng các threads vẫn có thể cùng sửa **sharedList**. Cần quan sát **object graph** chứ không chỉ vị trí khai báo reference.

### 2.2. Stateless advice là ưu tiên hữu ích

Nếu một advice chỉ nhận argument, đo thời gian bằng biến local, gọi proceed rồi ghi vào một logger thread-safe được thiết kế đúng, việc review concurrency thường đơn giản hơn so với mutable fields như lastUser, lastStartTime hoặc currentRequest.

Tuy nhiên một advice không có mutable field vẫn có thể sử dụng **shared target** hoặc **mutable collaborator** không thread-safe.

## 3. Data race và race condition khác nhau

Ví dụ **data race**:

~~~java
int calls;

void before() {
    calls++;
}
~~~

Nhiều threads cùng increment một field không có synchronization; các memory accesses read/write xung đột có thể thiếu happens-before. Kết quả có thể mất cập nhật.

Ví dụ **race condition logic**:

~~~text
Thread A: account.hasFunds(100) -> true
Thread B: account.hasFunds(100) -> true
Thread A: account.withdraw(100)
Thread B: account.withdraw(100)
~~~

Giả sử các methods hasFunds và withdraw được synchronized **riêng từng method**, điều đó chưa bảo vệ nguyên tử toàn bộ chuỗi check-then-withdraw. Nếu invariant là “không bao giờ rút quá số dư”, cần một operation hoặc transaction đúng ở tầng sở hữu invariant.

Điều tương tự xảy ra trong advice:

- Rate limit kiểm tra counter rồi tăng counter.
- Cache kiểm tra miss rồi gọi target và put.
- Dedup đọc trạng thái rồi ghi marker.
- Security kiểm tra resource state rồi tiến hành update.
- Retry logic đọc shared attempt number rồi thay.
- Metrics cập nhật nhiều fields vốn cần nhất quán.

**Thao tác thread-safe riêng lẻ không tự biến workflow nhiều bước thành atomic.**

## 4. Lost update với phép tăng counter

Biểu thức:

~~~java
calls++;
~~~

Có ý nghĩa đọc giá trị cũ, tính cộng một, rồi ghi lại. Hai threads có thể xen kẽ:

~~~text
calls = 0
A đọc calls = 0
B đọc calls = 0
A tính 1
B tính 1
A ghi 1
B ghi 1
Kết quả: calls = 1 dù có hai lượt gọi
~~~

Nếu aspect dùng calls để đếm calls, metric sẽ thiếu. Nếu dùng nó để điều khiển số lần retry hoặc giới hạn truy cập, lỗi có thể ảnh hưởng quyết định nghiệp vụ.

### 4.1. Dùng volatile vẫn có thể mất lượt

~~~java
volatile int calls;

void before() {
    calls++;
}
~~~

Volatile có những bảo đảm về visibility và ordering, nhưng **không hợp nhất read–modify–write thành một thao tác nguyên tử**. Hai threads vẫn có thể cùng đọc 0 rồi ghi 1.

Đây là ranh giới quan trọng nhất của chủ đề: **visibility không bằng atomicity**.

## 5. Java Memory Model và happens-before

**Java Memory Model** mô tả quan hệ giữa các actions ở các threads, những đồng bộ hóa được nhận diện và những giá trị các reads có thể quan sát. Khái niệm **happens-before** cho phép lý giải việc một write được bảo đảm hiển thị đối với một read khác khi có quan hệ thích hợp.

Một số nguồn happens-before tiêu biểu:

- Program order giữa các actions trong cùng thread theo quy tắc JMM.
- Unlock monitor rồi lock tiếp theo trên **cùng monitor**.
- Volatile write rồi volatile read tương ứng theo synchronization order.
- Thread.start và Thread.join theo quy tắc JMM.
- Các concurrent APIs có memory-consistency effects, ví dụ Future.get, CountDownLatch và concurrent collections theo contract.
- Safe publication của object qua synchronized, volatile hoặc cách thiết lập final fields hợp lệ.

**Không suy rằng “happens-before” là đồng hồ thời gian thực duy nhất.** Đó là quan hệ ngữ nghĩa trong memory model.

### 5.1. Khi thiếu happens-before

Một thread có thể đọc stale state hoặc quan sát những giá trị mà code không hề dự tính là có thể thấy trong execution thiếu đồng bộ. Compiler/JIT/CPU được phép tối ưu trong phạm vi JMM; không được giả định các writes sẽ tự hiện ra đúng lúc chỉ vì chương trình chạy lâu.

Cũng không nên mô tả sai rằng “Java luôn giữ một bản copy vĩnh viễn cho mỗi thread”. Điều đúng cần nắm là **không có bảo đảm visibility cần thiết** nếu dùng shared mutable state sai.

### 5.2. Happens-before chưa đủ để bảo vệ compound invariant

Một volatile field có thể publish một immutable config snapshot, nhưng ba volatile fields riêng có thể bị đọc tại các thời điểm khác nhau, không tạo một snapshot nhất quán.

Một shared invariant gồm balance + pending + version thường cần lock hoặc atomic immutable state transition phù hợp, không chỉ các field được đánh dấu volatile.

## 6. Volatile dùng đúng chỗ

Ví dụ stop flag:

~~~java
private volatile boolean stopped;

void stop() {
    stopped = true;
}

void runLoop() {
    while (!stopped) {
        // Định kỳ kiểm tra điều kiện dừng.
    }
}
~~~

Đây là code minh họa, không là chương trình độc lập. Volatile hỗ trợ visibility/ordering cho flag, nhưng không tự hủy một I/O blocking hoặc một task đang chờ lock; cancellation/interruption phải được thiết kế phù hợp.

### 6.1. Volatile reference không bảo vệ nội dung object

~~~java
volatile MutableConfig config;
~~~

Nếu object MutableConfig tiếp tục được mutate từ nhiều threads không có synchronization, volatile chỉ trên reference **không khiến toàn bộ object graph thread-safe**.

Một mẫu tốt hơn là xây dựng **immutable snapshot**, publish reference và không mutate snapshot sau đó.

### 6.2. Config reload và in-flight invocation

Nếu policy thay đổi trong lúc một advice đang xử lý request, nên quyết định invocation có cần dùng một **config version nhất quán** hay có thể quan sát nhiều versions. Một local reference capture từ volatile Policy ở đầu invocation thường giúp dùng một snapshot duy nhất.

## 7. AtomicInteger và AtomicLong

Khi cần counter chính xác theo một instance:

~~~java
private final AtomicInteger calls = new AtomicInteger();

void before() {
    calls.incrementAndGet();
}
~~~

Đây là snippet cần import AtomicInteger và class context. incrementAndGet là atomic update cho counter. AtomicInteger/AtomicLong còn có các operation như get, compareAndSet, getAndIncrement.

Nhưng **AtomicInteger không tự biến một workflow thành transaction**. Ví dụ:

~~~text
if (counter.get() < limit):
    counter.incrementAndGet()
    allow()
~~~

Hai threads có thể cùng thấy dưới limit, sau đó cùng increment. Cần một CAS loop hoặc cơ chế synchronization để check-and-reserve thành **một quyết định logic nguyên tử**.

### 7.1. CAS và side effects

Với compare-and-set, một thread có thể phải thử lại khi expected value không còn đúng. Không đưa side effects không idempotent (charge, send email) vào phần tính toán có thể chạy lại mà không có giao thức an toàn.

### 7.2. Exact counter và lifecycle

Counter thuộc **aspect instance**. Nếu runtime có nhiều aspect instances (theo target, caller hoặc một scope riêng), đọc một counter chỉ đếm invocation vào instance đó, không mặc nhiên là tổng số toàn ứng dụng.

## 8. LongAdder dùng khi nào?

LongAdder hữu ích cho metric counter được cập nhật nhiều trong môi trường contention cao.

~~~java
private final LongAdder calls = new LongAdder();

void before() {
    calls.increment();
}
~~~

LongAdder thường được dùng để tăng throughput cập nhật, nhưng **sum không phải atomic snapshot đối với concurrent updates**. Vì vậy nó không phải lựa chọn phù hợp mặc định cho logic quyết định hard limit từng request.

Không khẳng định LongAdder luôn nhanh hơn AtomicLong; cần benchmark workload thật, tần suất đọc/ghi và yêu cầu consistency.

## 9. Synchronized và monitor identity

Khi cần bảo vệ compound state, một critical section có thể cần dùng synchronized:

~~~java
private final Object lock = new Object();
private long total;
private long failures;

void record(boolean failed) {
    synchronized (lock) {
        total++;
        if (failed) {
            failures++;
        }
    }
}
~~~

Đoạn mã minh họa cần class context. Nếu reader muốn snapshot nhất quán của total/failures, nó cũng cần cùng synchronization hoặc cơ chế snapshot có bảo đảm tương đương.

### 9.1. Proxy và target có thể là hai locks

Trong mô hình proxy wrapper P và target T là hai objects khác nhau:

~~~text
synchronized(P) khóa monitor P
synchronized(T) khóa monitor T
~~~

Hai locks không hợp nhất dù P.equals(T) được override trả true. Nếu advice khóa proxy còn target khóa chính target, chúng có thể không bảo vệ cùng invariant.

### 9.2. Giữ lock qua proceed là một quyết định lớn

~~~text
synchronized(sharedLock):
    proceed()  // Có thể I/O, lock khác hoặc callback
~~~

Cách này có thể serialize toàn bộ requests, tăng tail latency hoặc deadlock. Nếu chỉ đếm calls, chọn atomic counter/synchronized ngắn thay vì giữ global lock cho toàn operation.

### 9.3. Deadlock và lock order

~~~text
Thread A: giữ L1 rồi chờ L2
Thread B: giữ L2 rồi chờ L1
~~~

Nếu một aspect và target cùng lấy locks theo thứ tự ngược, có thể deadlock. Đặt chính sách lock order thống nhất và tránh gọi code không kiểm soát trong critical sections.

## 10. ReentrantLock và ownership

Với ReentrantLock:

~~~java
lock.lock();
try {
    return proceed();
} finally {
    lock.unlock();
}
~~~

Đây là snippet minh họa, cần định nghĩa lock/proceed. Chỉ unlock sau khi acquire thành công. Với tryLock hoặc lockInterruptibly cần kiểm tra return/InterruptedException và thread ownership.

ReentrantLock hỗ trợ cùng thread reenter theo contract, nhưng không tự giải quyết **AOP recursion** nếu một logging advice gọi logger được pointcut chọn lại.

## 11. ConcurrentHashMap: an toàn cấu trúc chưa chắc an toàn nghiệp vụ

Một mutable HashMap không phù hợp cho concurrent mutations không đồng bộ. Có thể dùng ConcurrentHashMap:

~~~java
ConcurrentHashMap<String, AtomicInteger> perOperation =
    new ConcurrentHashMap<>();

void count(String operation) {
    perOperation
        .computeIfAbsent(operation, key -> new AtomicInteger())
        .incrementAndGet();
}
~~~

Đây là snippet cần imports/class context. Nó phù hợp đếm theo key khi chấp nhận consistency theo từng atomic counter.

Tuy nhiên:

- Không phải mọi sequence read–check–put đều atomic.
- Không có transaction atomic qua nhiều keys.
- Values mutable bên trong vẫn phải được bảo vệ đúng.
- Iteration và tổng hợp counts dưới concurrent mutation không tự cho một snapshot cùng thời điểm.
- Mapping callbacks cần tuân thủ API và tránh recursive update vào cùng map hoặc I/O kéo dài.

Nếu cache dùng map để deduplicate một side effect không idempotent, còn cần chính sách **single-flight/idempotency**, không chỉ chọn concurrent collection.

## 12. Ví dụ Java 17: tạo lost update xác định và sửa bằng AtomicInteger

Chương trình **AspectConcurrencyDemo.java** dùng một object RacyAdvice có **volatile int calls**, một AtomicAdvice và một ContextAdvice. Nó mô phỏng around invocation nhưng không tự dệt bytecode.

Để tạo kết quả ổn định, **CyclicBarrier** khiến cả hai threads đọc calls trước khi ghi lại. Không dùng sleep hoặc kỳ vọng scheduler tình cờ gây race.

~~~java
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class AspectConcurrencyDemo {
    @FunctionalInterface
    interface Work {
        int execute();
    }

    static final class RacyAdvice {
        volatile int calls;
        final CyclicBarrier barrier = new CyclicBarrier(2);

        int around(Work work) {
            int before = calls;
            try {
                barrier.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (BrokenBarrierException e) {
                throw new IllegalStateException(e);
            }
            calls = before + 1;
            return work.execute();
        }
    }

    static final class AtomicAdvice {
        final AtomicInteger calls = new AtomicInteger();

        int around(Work work) {
            calls.incrementAndGet();
            return work.execute();
        }
    }

    static final class ContextAdvice {
        final ThreadLocal<String> context = new ThreadLocal<>();

        void within(String label, Runnable body) {
            String previous = context.get();
            context.set(label);
            try {
                body.run();
            } finally {
                if (previous == null) {
                    context.remove();
                } else {
                    context.set(previous);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            RacyAdvice racy = new RacyAdvice();
            Future<Integer> r1 = pool.submit(() -> racy.around(() -> 7));
            Future<Integer> r2 = pool.submit(() -> racy.around(() -> 7));
            int sum1 = r1.get() + r2.get();
            System.out.println("racy-results=" + sum1);
            System.out.println("racy-calls=" + racy.calls);

            AtomicAdvice safe = new AtomicAdvice();
            Future<Integer> a1 = pool.submit(() -> safe.around(() -> 7));
            Future<Integer> a2 = pool.submit(() -> safe.around(() -> 7));
            int sum2 = a1.get() + a2.get();
            System.out.println("atomic-results=" + sum2);
            System.out.println("atomic-calls=" + safe.calls.get());
        } finally {
            pool.shutdown();
        }

        ContextAdvice scope = new ContextAdvice();
        scope.within("outer", () -> {
            System.out.println("context-outer=" + scope.context.get());
            scope.within("inner", () ->
                System.out.println("context-inner=" + scope.context.get())
            );
            System.out.println("context-restored=" + scope.context.get());
        });
        System.out.println("context-after=" + scope.context.get());

        try {
            scope.within("failure", () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException e) {
            System.out.println("caught=" + e.getMessage());
        }
        System.out.println("context-after-failure=" + scope.context.get());
    }
}
~~~

Biên dịch và chạy bằng JDK 17:

~~~bash
javac --release 17 AspectConcurrencyDemo.java
java AspectConcurrencyDemo
~~~

**Stdout đã chạy kiểm chứng:**

~~~text
racy-results=14
racy-calls=1
atomic-results=14
atomic-calls=2
context-outer=outer
context-inner=inner
context-restored=outer
context-after=null
caught=boom
context-after-failure=null
~~~

### 12.1. Vì sao racy-calls = 1?

Hai invocation dùng **cùng instance RacyAdvice**. Mỗi thread đọc calls = 0 vào local before. Barrier đảm bảo cả hai đã đến đây trước khi một thread tiếp tục ghi. Cả hai sau đó thực hiện calls = 0 + 1, nên kết quả là 1.

**Barrier chỉ là công cụ test** ép một interleaving; không nên đặt một barrier đợi đúng hai threads bên trong advice thật. Nó cũng không làm phép increment trở thành atomic.

### 12.2. Vì sao atomic-calls = 2?

AtomicInteger.incrementAndGet thực hiện atomic update cho counter. Hai invocations đóng góp đủ hai increments; Future.get chờ completion và bảo đảm các memory consistency effects theo API.

**Không nên hiểu điều này là toàn bộ advice đã thread-safe** nếu những fields khác vẫn dùng mutable HashMap hoặc unsafe state.

### 12.3. Vì sao context-inner rồi context-restored?

ContextAdvice dùng ThreadLocal:

- Outer lưu previous null rồi set outer.
- Inner lưu previous outer rồi set inner.
- Inner finally restore outer.
- Outer finally remove vì trước đó null.

Đây là cách tránh làm mất outer context khi advice lồng nhau.

### 12.4. Vì sao context-after-failure null?

Body ném IllegalStateException nhưng finally trong within vẫn chạy. Context bị remove trước khi lỗi được caller catch. Điều này tránh rò request/user/tracing context khi worker thread được tái sử dụng.

### 12.5. Những giới hạn của demo

Nó chứng minh lost update trong một interleaving cụ thể, không chứng minh race luôn xuất hiện nếu bỏ barrier. Nó không đo hiệu năng. Context nested chạy ở một thread, không chứng minh ThreadLocal tự truyền sang task chạy ở executor. Nó là Java wrapper minh họa, không phải engine AOP cụ thể.

## 13. ThreadLocal: state theo thread, không theo request bất biến

Một advice sai:

~~~java
class UnsafeTrace {
    String currentUser;

    void before(String user) {
        currentUser = user;
    }

    void after() {
        System.out.println(currentUser);
    }
}
~~~

Nếu một UnsafeTrace instance phục vụ hai threads, request A có thể set user A, request B set user B, rồi request A log nhầm user B. Với security context, lỗi có thể nghiêm trọng hơn sai tracing.

### 13.1. Ưu tiên local state

Nếu around advice cần start timestamp và operation ID để tạo metric, lưu chúng trong **local variables của chính invocation** là cách đơn giản nhất. Nó tránh việc có một field currentStartTime chung giữa requests.

### 13.2. Khi cần ThreadLocal

ThreadLocal có thể phù hợp nếu nhiều helper sâu trong **cùng thread** cần truy cập context mà không nhận param trực tiếp. Nhưng phải:

- Set khi vào scope.
- Lưu previous value nếu có outer scope.
- Restore/remove trong finally.
- Tránh để request mới thấy context cũ trên thread pool.
- Không đặt shared mutable object vào ThreadLocal rồi coi nó được bảo vệ.

### 13.3. InheritableThreadLocal không sửa thread pool propagation

InheritableThreadLocal có semantics tại child thread creation. Pool threads được tạo trước hoặc tái sử dụng, nên context lúc task chạy không nhất thiết khớp context lúc thread được tạo. Không dựa vào nó như propagation tự động an toàn cho ExecutorService.

## 14. Async: context phải đi qua một boundary khác

~~~text
Caller Thread:
    set traceId = A
    submit task
    restore traceId cũ

Worker Thread:
    run task trên một thread khác
~~~

ThreadLocal không tự gắn traceId A sang worker. Cần capture một **immutable context snapshot** hoặc truyền parameters, sau đó install context hợp lệ ở worker trong một scope try/finally, rồi restore worker's previous value.

Đừng chuyển một mutable Map chưa thread-safe qua worker rồi cho hai threads cùng mutate. Nếu code trả CompletableFuture ngay lập tức, Logging after-returning của method khởi tạo future chỉ ghi nhận **submission/handle return**, không tự có nghĩa async task đã hoàn tất.

### 14.1. Thread-bound resource

Không được cleanup ReentrantLock, transaction context hoặc resource có thread affinity trên một thread khác nếu contract không cho phép. Context propagation khác resource ownership, cần thiết kế riêng.

### 14.2. Cancellation/exception

Một future bị cancel không bảo đảm worker đã ngừng dùng resource ngay. Phải xử lý completion/error/cancellation và cleanup đúng scope. Đừng nuốt InterruptedException hoặc làm rò ThreadLocal khi task throw.

## 15. Aspect instance lifecycle không đồng nghĩa thread confinement

Một số mô hình AOP hỗ trợ instance scope theo global/singleton-like, **per-this**, **per-target** hoặc **per-control-flow**. Những scope này phụ thuộc ngôn ngữ/engine và join point semantics.

Ví dụ một aspect **per-target** không được tự coi thread-safe:

~~~text
Thread A ----+
             +----> Shared target T ----> Aspect instance X
Thread B ----+
~~~

Cùng target T có thể được hai threads gọi đồng thời, nên mutable fields của X vẫn chia sẻ. Per-this gắn theo vai trò this của join point, cũng không tự đồng nghĩa “mỗi thread một aspect”.

Một aspect instance per-invocation có thể tránh shared instance field nhưng không bảo vệ **global collaborators, static caches hay target state**. Phải xác định object nào thực sự được chia sẻ.

## 16. Immutable snapshot và safe publication

Nếu policy có ba fields version, limit, enabled, đừng update từng mutable field rồi mong reader nhìn một trạng thái nhất quán.

Ví dụ một immutable record chứa primitives:

~~~java
record Policy(int version, int limit, boolean enabled) {}
~~~

Publish object mới qua volatile reference:

~~~java
private volatile Policy policy = new Policy(1, 10, true);

void reload() {
    policy = new Policy(2, 20, true);
}

void handle() {
    Policy snapshot = policy;
    // Dùng snapshot trong phạm vi invocation này.
}
~~~

Đây là snippets cần class context, không phải một chương trình độc lập. Volatile publication làm reader dùng một object Policy đã chuẩn bị; capture snapshot một lần giúp một request không đọc trộn nhiều versions.

Nếu Policy chứa reference đến mutable List, cần copy/bảo vệ List; một record không tự deep-immutable toàn bộ object graph.

### 16.1. Tránh this escape

Không publish reference đến một object đang xây dựng ra shared registry hoặc thread khác trước khi constructor hoàn tất mà không có protocol đúng. Final-field semantics có điều kiện; unsafe publication làm mất những bảo đảm reader cần.

## 17. Cache, dedup và atomicity của side effects

Một caching advice thường:

~~~text
if cache contains key:
    return cached
value = proceed()
cache[key] = value
return value
~~~

Hai threads có thể cùng miss và cùng proceed. Nếu target tạo side effect, có thể có duplicate. Chuyển HashMap thành ConcurrentHashMap bảo vệ các map operations theo API nhưng **không tự làm cả workflow đúng một lần**.

Cần xác định:

- Duplicate computation có được chấp nhận không?
- Có cần single-flight theo key không?
- Loader/mapping function có side effects hoặc reentrancy không?
- Cached value có immutable/thread-safe không?
- Có transaction version/invalidation policy không?
- Cache key dùng business identity hay proxy object reference?

Nếu caching có thể short-circuit target, counter advice **bên ngoài cache** sẽ khác counter advice **bên trong cache**. Phải đặt tên metric đúng boundary.

## 18. Metrics phải gắn đúng pointcut và precedence

Một request có thể đi qua RetryAround và thực thi target nhiều attempts:

~~~text
Caller -> RetryAround -> Target
                | 
                +-- proceed #1 fails
                +-- proceed #2 succeeds
~~~

Nếu một counter nằm ngoài Retry, nó có thể tăng một lần; nếu nằm bên trong, nó có thể tăng cho mỗi attempt. Cả hai có thể đúng theo mục đích metric nhưng không có cùng ý nghĩa.

Nên tách:

- Logical request count.
- Advice invocation count.
- Target execution count.
- Retry attempt count.
- Transaction committed count.
- Failure count theo từng boundary.

Atomic increments không khắc phục **sai điểm quan sát**. Khi đọc nhiều counters để tạo invariant, những reads riêng lẻ không nhất thiết là atomic snapshot của cả tập.

## 19. Lock scope, deadlock và reentrancy

Một aspect có thể vô tình serialize cả hệ thống nếu dùng shared lock bao proceed:

~~~text
synchronized(globalLock):
    log
    proceed()  // Có thể gọi I/O hoặc cần lock khác
~~~

Khi proceed chậm, mọi requests tranh lock. Khi target gọi ngược một method advice cần lock khác, có thể deadlock.

Một giải pháp là đặt critical section ngắn quanh đúng shared invariant; lưu thời gian và attempt number ở local state; chỉ dùng lock cho các operations thực sự cần phối hợp.

Nếu logging helper cũng được pointcut match, advice có thể reenter chính nó và tạo recursion. Reentrancy guard cần scope riêng và restore trong finally, không dùng một global boolean không synchronized.

## 20. Kiểm thử concurrency và visibility

Một test chạy thành công một lần không chứng minh code không có race.

Nên kết hợp:

1. **Deterministic interleavings** bằng CyclicBarrier/CountDownLatch để tái hiện read-modify-write sai.
2. **Load/stress tests** với nhiều threads và traffic patterns.
3. **Invariant assertions** (ví dụ reservations không vượt limit, counters không mất increments).
4. **Negative tests** cho pointcut không nên match.
5. **Nested ThreadLocal tests** cho previous context restore.
6. **Thread pool reuse** để phát hiện context leak sang request sau.
7. **Async propagation tests** giữa caller và Executor worker.
8. **Config reload tests** trong lúc nhiều invocations đang đọc.
9. **Concurrent cache tests** trên same key và nhiều keys.
10. **Lock order/deadlock tests** nếu có nested locks.
11. **Retry/exception fault injection** để xác nhận finally cleanup.
12. **Metrics semantics tests** khi cache hit, short-circuit hoặc retry làm số target calls khác số advice events.

### 20.1. Stress test không thay thế JMM reasoning

Một race có thể không xuất hiện ở một JVM run do scheduling. Cần chứng minh thuật toán đúng dưới interleavings được phép và sử dụng memory consistency primitives có contract rõ.

### 20.2. Benchmark khác correctness test

Nếu cần so chi phí volatile, AtomicLong, LongAdder và synchronized, đo bằng JMH hoặc benchmark tương đương với warmup, forks và workload hợp lệ. Ví dụ println hoặc một vòng main tùy ý không cho kết luận overhead đáng tin.

## 21. Bảng quyết định

| Nhu cầu | Công cụ/thiết kế | Cảnh báo |
|---|---|---|
| Per-invocation timestamp/attempt | Local variable | Object tham chiếu vẫn có thể shared |
| Counter chính xác một biến | AtomicInteger/AtomicLong | Compound invariant vẫn cần bảo vệ |
| Counter thống kê contention cao | LongAdder | sum không atomic snapshot khi đang updates |
| Nhiều fields có invariant chung | synchronized/Lock | Cùng lock cho read và write |
| Cache/counters theo key | ConcurrentHashMap + atomic values | Compound workflow không tự atomic |
| Config reload | Immutable snapshot + volatile reference | Không mutate snapshot sau publication |
| Context cùng thread | ThreadLocal + save/restore finally | Thread pool cleanup bắt buộc |
| Async context | Explicit immutable context handoff | Không tự kéo theo ThreadLocal |
| Per-target aspect state | Phân tích sharing của target | Một target có thể nhiều threads |
| Security context | Per-request immutable object | Không lưu currentUser trong shared field |
| Global circuit breaker | Atomic state machine/lock | Nhiều conditions cần invariant |
| Dedup side effects | Idempotency/single-flight protocol | ConcurrentMap một mình không đủ |

## 22. Các lỗi thường gặp

**Lỗi 1:** Coi aspect luôn dùng một thread và shared fields là local state.

**Lỗi 2:** Dùng volatile int với ++ rồi tin increment atomic.

**Lỗi 3:** Coi AtomicInteger khiến mọi state của advice thread-safe.

**Lỗi 4:** Coi final reference biến object mutable thành immutable.

**Lỗi 5:** Đưa HashMap không thread-safe vào shared aspect và cập nhật đồng thời.

**Lỗi 6:** Cho rằng ConcurrentHashMap làm mọi multi-key operation atomic.

**Lỗi 7:** Coi per-target aspect instance như per-thread instance.

**Lỗi 8:** Chuyển một shared object reference vào local rồi tin nó đã thread-confined.

**Lỗi 9:** Tin ThreadLocal tự propagate qua Executor/CompletableFuture.

**Lỗi 10:** Remove ThreadLocal ở nested scope và làm mất context của outer scope.

**Lỗi 11:** Đưa shared mutable List vào ThreadLocal rồi coi List thread-safe.

**Lỗi 12:** Dùng synchronized(proxy) và synchronized(target) như cùng một monitor.

**Lỗi 13:** Đếm outer advice mà đặt metric name là target executions.

**Lỗi 14:** Atomic get rồi atomic increment và coi check-then-act atomic.

**Lỗi 15:** Đo race bằng Thread.sleep rồi kết luận không có vấn đề.

**Lỗi 16:** Dùng volatile reference nhưng mutate những fields trong object không synchronized.

**Lỗi 17:** Giữ global lock qua network call và tạo contention/deadlock.

**Lỗi 18:** Quên cleanup context khi advice/target throw.

**Lỗi 19:** Coi thread-safe aspect nghĩa target cũng thread-safe.

**Lỗi 20:** Coi successful stress test đủ chứng minh không có data race.

## 23. Quy trình thiết kế aspect an toàn

1. Xác định scope của aspect/interceptor, target, proxy handler và collaborator.
2. Liệt kê mutable fields, maps, counters, globals, context objects.
3. Xác định reader/writer threads cho từng state.
4. Xác định **invariant**: exact counter, approximate metric, hard limit, config version, per-request context.
5. Ưu tiên stateless advice và local variables cho state riêng invocation.
6. Dùng volatile cho publication đúng ngữ nghĩa, không dùng nó như lock.
7. Dùng Atomic* cho atomic updates của từng biến theo contract.
8. Dùng synchronized/Lock hoặc một state machine đúng cho multi-step invariant.
9. Dùng concurrent containers cùng một giao thức compound operation đúng.
10. Dùng immutable context/snapshots khi cần chia sẻ dữ liệu.
11. Restore/remove ThreadLocal bằng finally; test nested và thread pool reuse.
12. Thiết kế context propagation qua async boundaries.
13. Tránh giữ lock lâu qua proceed hoặc các I/O khó kiểm soát.
14. Thử interleavings, retry, cancellation và failure paths.
15. Đo performance sau khi correctness đã được kiểm chứng.

## 24. Câu hỏi ôn tập

**Câu 1.** Volatile calls++ có thể mất updates không?

**Đáp:** Có. Read–modify–write không phải một atomic operation.

**Câu 2.** Vì sao demo racy-calls bằng 1?

**Đáp:** Barrier ép cả hai threads đọc 0 trước khi ghi 1.

**Câu 3.** Vì sao demo atomic-calls bằng 2?

**Đáp:** AtomicInteger.incrementAndGet được dùng cho hai increments.

**Câu 4.** Một AtomicInteger làm toàn bộ advice thread-safe chưa?

**Đáp:** Chưa nếu còn shared mutable collaborators hoặc multi-field invariants.

**Câu 5.** final ArrayList có immutable không?

**Đáp:** Không. Reference final và collection immutable là hai chuyện khác.

**Câu 6.** Aspect per-target có thể được nhiều threads dùng đồng thời không?

**Đáp:** Có, khi target được nhiều threads gọi.

**Câu 7.** Local variable trỏ vào shared Map có loại bỏ race trên Map không?

**Đáp:** Không, Map object vẫn dùng chung.

**Câu 8.** Vì sao inner ThreadLocal phải restore previous?

**Đáp:** Để không xóa context của outer scope.

**Câu 9.** CompletableFuture tự mang ThreadLocal từ caller sang worker không?

**Đáp:** Không mặc định; cần context propagation rõ ràng.

**Câu 10.** Atomic read rồi atomic increment có đảm bảo rate limit không?

**Đáp:** Không nếu check và increment chưa là một quyết định logic atomic.

**Câu 11.** LongAdder.sum có snapshot atomic khi đang cập nhật không?

**Đáp:** Không bảo đảm atomic snapshot.

**Câu 12.** ConcurrentHashMap có đảm bảo mọi compound workflow an toàn không?

**Đáp:** Không, chỉ những operations có contract thread-safe tương ứng.

**Câu 13.** synchronized(P) và synchronized(T) có cùng monitor nếu P khác T không?

**Đáp:** Không.

**Câu 14.** Outer advice count có luôn bằng target method executions không?

**Đáp:** Không, vì retry/cache/short-circuit hoặc nested points.

**Câu 15.** Test race bằng sleep có đủ không?

**Đáp:** Không. Dùng deterministic scheduling/synchronization và invariant reasoning.

**Câu 16.** Volatile policy reference có khiến mutable fields bên trong luôn an toàn không?

**Đáp:** Không. Cần immutable snapshot hoặc synchronization thích hợp.

## 25. Sơ đồ ghi nhớ

~~~text
                Multiple threads
                       |
                       v
             Shared aspect instance
                       |
            +----------+----------+
            |          |          |
            v          v          v
         Local      Mutable     ThreadLocal
         per call   shared      per thread
            |          |          |
            |       +--+------+   +-- restore scope
            |       |         |
            v       v         v
          Simple  volatile   Atomic / Lock
                  visibility state consistency
                  != atomic
                       |
                       v
              Test pointcut semantics
             Retry, async, exception,
              publication, happens-before
~~~

**Kết luận:** một aspect có thể dùng chung mutable state qua nhiều luồng, gây lost update, visibility bugs, context mixing và race ở tầng nghiệp vụ. Cần phân biệt volatile visibility với atomic read–modify–write, dùng AtomicInteger/LongAdder/synchronized/Lock đúng ngữ nghĩa, quản lý ThreadLocal theo scope và chủ động propagate immutable context qua async. Thread safety của advice, target và collaborator là các điều kiện riêng biệt; kiểm thử phải bao cả concurrency interleavings lẫn số lượt advice/target thực sự diễn ra.

## Tài liệu tham khảo

- [Java Language Specification 17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): JMM, happens-before, synchronization, volatile.
- [Java SE 17 — AtomicInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): atomic operations.
- [Java SE 17 — LongAdder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/LongAdder.html): counters under contention.
- [Java SE 17 — ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): thread-bound context.
- [Java SE 17 — ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): concurrent keyed state.
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): completion và memory effects.
- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): aspect instantiation, pointcut và advice trong một ngôn ngữ AOP cụ thể.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-concurrency-003">Tách dữ liệu cấu hình bất biến khỏi trạng thái mỗi lần gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Tách dữ liệu cấu hình bất biến khỏi trạng thái mỗi lần gọi trong Aspect

## 1. Vấn đề thực sự: cùng một aspect xử lý nhiều lần gọi

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, một **aspect** đóng gói **pointcut** và **advice** cho mối quan tâm cắt ngang: audit, logging, tracing, kiểm tra chính sách, đo thời gian, retry hoặc kiểm soát truy cập. Trong nhiều mô hình, một **aspect instance được dùng chung** để xử lý nhiều join point hoặc nhiều lần xuất hiện của cùng một join point.

Điều đó không có nghĩa mọi dữ liệu mà advice sử dụng cũng nên đặt vào một **field dùng chung**.

Một advice có thể cần **hai loại dữ liệu có vòng đời hoàn toàn khác nhau**:

- **Cấu hình/chính sách (configuration/policy):** những giá trị làm nền cho hành vi, ví dụ giới hạn số lần retry, mẫu định danh method được theo dõi, nhãn metric, ngưỡng thời gian, những nhóm lỗi được phép chuyển đổi. Chúng thường được đọc bởi nhiều invocation và có thể được biểu diễn thành **bất biến (immutable)**.
- **Trạng thái của một lượt gọi (per-invocation state):** mã tương quan của lượt hiện tại, thời điểm bắt đầu, argument đã chọn, số retry đã thử, kết quả tạm, Throwable đang xử lý. Những giá trị này **phải gắn đúng lượt invocation**, không vô tình bị lượt khác ghi đè.

Một thiết kế tốt thường có dạng:

```text
                      Aspect dùng chung
                    +--------------------+
                    | ConfigStore        |
                    | Policy snapshot    |
                    | Helpers stateless  |
                    +---------+----------+
                              |
             +----------------+----------------+
             |                |                |
        Invocation A     Invocation B     Invocation C
        +------------+   +------------+   +------------+
        | config@v1  |   | config@v2  |   | config@v2  |
        | start_A    |   | start_B    |   | start_C    |
        | args_A     |   | args_B     |   | args_C     |
        | result_A   |   | result_B   |   | error_C    |
        +------------+   +------------+   +------------+
```

**Nguyên tắc trung tâm:** *Dùng cấu hình bất biến có thể chia sẻ an toàn; chọn một snapshot nhất quán cho từng lượt gọi; giữ những dữ liệu biến đổi riêng của lượt gọi trong local variables hoặc invocation context có vòng đời rõ ràng.*

> **Phạm vi:** Bài này trình bày kiến thức AOP cơ bản và nâng cao nói chung, không gắn vào một công cụ cụ thể. “Around advice”, “before/after advice”, “join point”, “proxy” và “weaving” là các khái niệm tổng quát; từng công cụ có API, instantiation policy và mức hỗ trợ khác nhau. Ví dụ Java 17+ dùng Java Dynamic Proxy để thực nghiệm **nguyên tắc tách cấu hình và trạng thái**, không giả định mọi AOP đều triển khai bằng proxy.

## 2. Phân loại dữ liệu trước khi quyết định đặt ở đâu

Một aspect có thể cần tới ít nhất bốn nhóm dữ liệu:

| Nhóm | Ví dụ | Phạm vi sở hữu hợp lý |
| --- | --- | --- |
| **Cấu hình tương đối ổn định** | `maxAttempts`, ngưỡng log, tập prefix cấm | Immutable snapshot dùng chung |
| **Metadata bất biến** | Tên method, chữ ký, quy tắc pointcut đã phân tích | Read-only metadata/cache có key chuẩn |
| **State mỗi invocation** | `startNanos`, `currentId`, `attempt`, selected argument | Biến local trong around hoặc invocation context |
| **State tổng hợp lâu dài** | Tổng số lời gọi, circuit state, cache counters | Shared component có concurrency contract |
| **State theo request/task** | Trace ID, actor/context đã xác minh | Context với propagation và cleanup riêng |
| **State theo target** | Cache riêng target, counter per-object | Per-target association nếu phù hợp |

Không nên gom tất cả thành một object lớn tên `AspectContext` rồi gắn lên singleton aspect. Khi đó các field per-invocation sẽ trở thành dữ liệu chia sẻ ngoài ý muốn.

### Một trường hợp dễ nhầm

```java
// Ví dụ sai về phạm vi state, không phải chương trình độc lập.
final class LoggingAspect {
    private final int maxLength = 120; // Cấu hình có thể dùng chung.
    private String currentUser;        // Sai nếu dùng cho từng request.
    private long startNanos;           // Sai nếu dùng cho từng invocation.
}
```

`maxLength` là một giá trị cấu hình; nếu không đổi theo thời gian thì việc dùng chung hoàn toàn hợp lý. Nhưng `currentUser` và `startNanos` có ý nghĩa với **một hoạt động cụ thể**. Nếu hai lời gọi chạy đồng thời hoặc lồng nhau, một invocation có thể đọc giá trị của invocation khác.

## 3. Immutable config không phải “mọi field đều phải final”

Một class có field `final` không tự động bất biến sâu:

```java
// Minh họa antipattern.
final class WeakPolicy {
    final java.util.Set<String> forbiddenPrefixes;

    WeakPolicy(java.util.Set<String> source) {
        this.forbiddenPrefixes = source; // Giữ alias mutable của caller.
    }
}
```

Caller tạo một `HashSet` rồi truyền vào constructor. Nếu `WeakPolicy` giữ **chính reference đó**, caller còn có thể `source.add("X")` về sau, làm “cấu hình” thay đổi **không đi qua cơ chế publish/reload**.

**`final` bảo vệ việc gán lại reference của field, không tự ngăn object được reference tới bị mutate.**

### Các điều kiện thường cần cho một snapshot bất biến

1. Field không được reassign sau khi xây dựng.
2. Những mutable collection được **defensive copy** hoặc dùng persistent immutable structure thích hợp.
3. Không để lộ mutable array/list/map bên trong qua getter.
4. Các phần tử lồng nhau cũng phải bất biến hoặc được sao chép phù hợp.
5. Constructor xác minh invariant: giới hạn không âm, tên phiên bản hợp lệ, không có element null ngoài quy tắc, policy không mâu thuẫn.
6. Không để `this` “thoát” khỏi constructor trước khi khởi tạo xong.
7. Khi reload, tạo **snapshot mới** thay vì chỉnh sửa snapshot đang có nhiều caller sử dụng.

### Java 17 record là công cụ tiện lợi, nhưng chỉ shallowly immutable

```java
// Một ví dụ độc lập về data holder.
record Policy(String version, int maxLength,
              java.util.Set<String> prefixes) {
    Policy {
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength");
        }
        prefixes = java.util.Set.copyOf(prefixes);
    }
}
```

Record có các component fields ngầm là `final`, và `Set.copyOf` tạo hoặc dùng một unmodifiable set theo contract. Với `Set<String>`, cả cấu trúc set lẫn `String` elements đều có semantics bất biến thích hợp.

Nhưng nếu component là `List<MutableRule>`, `List.copyOf` **chỉ copy cấu trúc ngoài**; một `MutableRule` bên trong vẫn có thể bị thay đổi. Vì vậy phải xem **toàn bộ object graph**, không chỉ keyword `record` hoặc `final`.

### Dữ liệu bất biến là lời hứa thiết kế

Một getter trả mảng `byte[]` nội bộ có thể cho caller sửa trực tiếp nội dung. Nếu cấu hình chứa array hay map lồng nhau, cần dùng defensive copies ở cả **đầu vào** và **đầu ra** khi cần thiết; với nested elements mutable, copy sâu hoặc chuyển thành immutable types phù hợp.

## 4. Tại sao phải tách config khỏi per-call state?

Một cấu hình có thể được dùng bởi nhiều caller và tồn tại hàng giờ, trong khi state của một invocation thường sống vài microseconds đến seconds.

```text
Shared configuration lifetime:
|-------------------v1-------------------|-----v2------|

Invocation A:
           |------- state A -------|

Invocation B:
                |---state B---|

Invocation C:
                                          |---state C---|
```

Nếu trộn hai loại này:

- Reload cấu hình có thể ghi đè dữ liệu một invocation đang chạy.
- Invocation B có thể ghi đè `startNanos` hoặc `currentId` của A.
- Một request object được giữ trong field shared có thể sống lâu hơn request, gây retention và rò dữ liệu.
- Khó phân biệt “version policy của lượt gọi A” với “policy hiện hành khi A kết thúc”.
- Một aspect shared trở thành tập hợp mutable field đòi synchronized phức tạp.
- Test không xác định được lỗi do visibility/race, do policy update hay do join point không được can thiệp.

Tách scope giúp **cấu hình chia sẻ an toàn** và **dữ liệu của từng invocation cô lập** mà không nhất thiết phải tạo aspect object mới cho mỗi call.

## 5. Sai lầm cổ điển: field `currentRequestId` trên aspect dùng chung

```java
// KHÔNG an toàn nếu một instance được nhiều invocation dùng.
final class UnsafeAdvice {
    String currentRequestId;

    void before(String id) {
        currentRequestId = id;
    }

    void after() {
        System.out.println(currentRequestId);
    }
}
```

Hai thread có thể xen kẽ:

```text
Thread A: before("A") -> currentRequestId = "A"
Thread B: before("B") -> currentRequestId = "B"
Thread A: after()     -> đọc "B" (sai)
```

Ngay cả **một thread duy nhất**, lời gọi lồng nhau cũng có thể gây lỗi:

```text
before(outer) -> currentRequestId = "outer"
   target.outer()
      before(inner) -> currentRequestId = "inner"
      after(inner)
after(outer) -> vẫn đọc "inner" (sai)
```

Đây là **reentrancy**, không chỉ là multi-thread race.

Thêm `volatile` hay `AtomicReference<String>` chỉ làm một số thao tác đọc/ghi có semantics đồng bộ phù hợp; nó **không biến một slot dùng chung thành nhiều slot theo invocation**. Tương tự, chỉ synchronized các setter/getter không tạo ra correlation identity.

**Giải pháp cốt lõi là thay đổi nơi lưu state**, không chỉ thêm khóa.

## 6. Dùng local variables cho around advice

Một around advice thường có thể bao trọn invocation và gọi `proceed`:

```java
// Pseudocode của around advice; proceed() phụ thuộc API AOP cụ thể.
Object around(Operation joinPoint, PolicyStore policies)
        throws Throwable {
    Policy policy = policies.snapshot(); // Chọn một phiên bản ở đầu lời gọi.
    String invocationId = joinPoint.id(); // Local của riêng lời gọi.
    long startedAt = System.nanoTime();   // Local của riêng lời gọi.

    try {
        validate(joinPoint, policy);
        return joinPoint.proceed();
    } finally {
        record(invocationId, policy.version(),
                System.nanoTime() - startedAt);
    }
}
```

`Operation` và `PolicyStore` trong đoạn pseudocode thể hiện ý tưởng, không phải các API chuẩn AOP của Java SE. Điểm cần học là:

- `policy` được **lấy một lần** từ provider ở entry.
- `invocationId` và `startedAt` là local variables của stack frame hiện tại.
- Invocation lồng nhau tạo **stack frame khác** nên không ghi đè local của outer.
- `finally` chạy trên đường return và throw theo Java semantics.
- Snapshot được dùng trong cả validation và ghi nhận outcome của **cùng invocation**.

Một aspect singleton hoàn toàn có thể là **stateless về dữ liệu mỗi lời gọi** mặc dù method advice sử dụng nhiều local variables.

### `System.nanoTime` là mốc duration

`System.nanoTime()` phù hợp đo **elapsed time** trong cùng JVM, không phải timestamp lịch/UNIX. Nếu ghi elapsed ở `finally`, phải đảm bảo hiểu duration đo phạm vi around (có thể bao gồm advice khác, retry hoặc thời gian chờ) chứ không nhất thiết riêng business method body.

## 7. Cấu hình bất biến vẫn cần công bố an toàn

Giả sử chương trình muốn đổi policy từ `v1` sang `v2` khi đang có nhiều thread gọi advice. Có hai yêu cầu:

1. **Snapshot mới phải được tạo hoàn chỉnh, hợp lệ trước khi công bố.**
2. **Caller phải lấy snapshot có semantics visibility/atomicity phù hợp.**

Một pattern thường gặp trong Java:

```java
final class PolicyStore {
    private final java.util.concurrent.atomic.AtomicReference<Policy>
            active;

    PolicyStore(Policy initial) {
        active = new java.util.concurrent.atomic.AtomicReference<>(
                java.util.Objects.requireNonNull(initial));
    }

    Policy snapshot() {
        return active.get();
    }

    void reload(Policy next) {
        active.set(java.util.Objects.requireNonNull(next));
    }
}
```

`AtomicReference.set` có semantics volatile write; `get` có semantics volatile read. Kết hợp với **snapshot immutable, constructed đúng**, một invocation sẽ nhận một reference tới object đã được công bố an toàn. Thay `active` bằng `volatile Policy active` và gán atomic một reference cũng có thể đáp ứng bài toán **đổi cả snapshot** nếu không cần compare-and-set.

**Tính atomic của reference không làm nội dung object mutable tự an toàn.** Nếu code giữ cùng `Policy` rồi sửa `HashMap` bên trong từ thread khác, vấn đề race/inconsistency vẫn còn.

### Safe publication khác atomic update của từng field

Nếu dùng các field cấu hình riêng:

```java
// Ví dụ CÓ THỂ TẠO TRẠNG THÁI LỆCH khi reload từng field.
volatile int maxLength;
volatile String policyVersion;
volatile java.util.Set<String> forbiddenPrefixes;
```

Updater lần lượt thay ba field; caller có thể thấy `maxLength` mới nhưng `policyVersion` cũ. Volatile cho từng field **không tạo giao dịch atomic giữa nhiều field**.

Một snapshot immutable gom các field liên quan cho phép **thay thế một reference duy nhất**, giúp các giá trị đi cùng một version.

## 8. Snapshot một lần cho mỗi invocation: quy tắc quan trọng nhất

Một anti-pattern:

```java
// Mã minh họa sai về tính nhất quán xuyên thời điểm.
if (store.snapshot().maxLength() >= text.length()) {
    log("allow@" + store.snapshot().version());
}
```

Nếu config reload **giữa hai lần `snapshot()`**, quyết định được đưa ra theo `v1` nhưng log lại gắn nhãn `v2`. Đó là lỗi **mixed-version decision**.

Giải pháp:

```java
Policy policy = store.snapshot();

if (text.length() <= policy.maxLength()) {
    log("allow@" + policy.version());
}
```

Lưu `policy` vào local **một lần** rồi dùng xuyên suốt lần gọi. Nếu provider chỉ publish immutable snapshots, invocation giữ được consistency nội bộ ngay cả khi một thread khác đã reload.

### Hệ quả thời gian

- Invocation A bắt đầu dưới `v1`, chọn snapshot v1.
- Configuration được reload sang `v2` khi A còn đang chạy.
- Invocation B bắt đầu sau reload, có thể chọn v2.
- A hoàn tất dựa trên v1; B dùng v2.
- Log/metric nên ghi **policy version thực sự đã được A/B sử dụng**, không luôn lấy current version ở thời điểm kết thúc.

**Đây là snapshot consistency theo lời gọi**, không phải yêu cầu mọi invocation cùng thấy một phiên bản.

## 9. Ví dụ Java 17+ thực thi được: immutable snapshot và state theo lời gọi

Ví dụ tiếp theo dùng **Java Dynamic Proxy** để mô phỏng một aspect shared. Mục tiêu là **nhìn thấy trực tiếp**:

1. `Policy` chứa cấu hình bất biến có defensive copy.
2. `PolicyStore` dùng `AtomicReference` để publish nguyên snapshot.
3. Handler dùng một `Policy` local và một `String text` local **cho từng lần `invoke`**.
4. Target cố ý reload `v1 → v2` giữa một outer invocation.
5. Target gọi qua proxy một lần nữa để tạo nested invocation mới.
6. **Outer advice tiếp tục dùng v1**, inner advice nhận v2.
7. Policy v2 chặn dữ liệu không hợp lệ mà không làm nhiễm state của invocation khác.

Đây là demo **kiểm chứng ngữ nghĩa**, không phải cú pháp pointcut của một engine AOP nhất định. Lời gọi qua wrapper proxy là điều kiện để handler chạy; các đường gọi raw khác không tự được intercept.

### 9.1. Tệp `AspectConfigStateDemo.java`

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class AspectConfigStateDemo {
    interface Service {
        String execute(String text);
    }

    record Policy(String version, int maxLength,
                  Set<String> forbiddenPrefixes) {
        Policy {
            Objects.requireNonNull(version);
            Objects.requireNonNull(forbiddenPrefixes);
            if (maxLength < 1) {
                throw new IllegalArgumentException("maxLength");
            }
            forbiddenPrefixes = Set.copyOf(forbiddenPrefixes);
        }
    }

    static final class PolicyStore {
        private final AtomicReference<Policy> active;

        PolicyStore(Policy initial) {
            active = new AtomicReference<>(initial);
        }

        Policy snapshot() {
            return active.get();
        }

        void reload(Policy next) {
            active.set(Objects.requireNonNull(next));
        }
    }

    static final class Target implements Service {
        private final PolicyStore store;
        private Service self;

        Target(PolicyStore store) {
            this.store = store;
        }

        void bind(Service proxy) {
            self = proxy;
        }

        @Override
        public String execute(String text) {
            System.out.println("target " + text);

            if ("outer".equals(text)) {
                store.reload(new Policy("v2", 3, Set.of("X")));
                System.out.println("config.reload v2");
                return "O:" + self.execute("in");
            }

            return "ok:" + text;
        }
    }

    static final class Advice implements InvocationHandler {
        private final Service target;
        private final PolicyStore store;

        Advice(Service target, PolicyStore store) {
            this.target = target;
            this.store = store;
        }

        @Override
        public Object invoke(Object proxy, Method method,
                             Object[] args) throws Throwable {
            Policy policy = store.snapshot(); // Một snapshot/call.
            String text = (String) args[0];   // State local/call.

            System.out.println("advice.before " + text
                    + "@" + policy.version());
            try {
                boolean forbidden =
                        policy.forbiddenPrefixes().stream()
                                .anyMatch(text::startsWith);

                if (text.length() > policy.maxLength() || forbidden) {
                    throw new IllegalArgumentException(
                            "blocked " + text);
                }

                Object result;
                try {
                    result = method.invoke(target, args);
                } catch (InvocationTargetException wrapped) {
                    throw wrapped.getCause();
                }

                System.out.println("advice.after " + text
                        + "@" + policy.version());
                return result;
            } finally {
                System.out.println("advice.finally " + text
                        + "@" + policy.version());
            }
        }
    }

    static Service proxy(Service target, PolicyStore store) {
        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[]{Service.class},
                new Advice(target, store));
    }

    public static void main(String[] args) {
        Set<String> original = new HashSet<>(Set.of("X"));
        PolicyStore store = new PolicyStore(
                new Policy("v1", 8, original));

        original.add("B");
        System.out.println("config.copied "
                + !store.snapshot()
                        .forbiddenPrefixes().contains("B"));

        Target target = new Target(store);
        Service intercepted = proxy(target, store);
        target.bind(intercepted);

        System.out.println("caller "
                + intercepted.execute("outer"));
        System.out.println("caller "
                + intercepted.execute("OK"));

        for (String value : new String[]{"abcd", "X"}) {
            try {
                intercepted.execute(value);
            } catch (IllegalArgumentException error) {
                System.out.println("caller.error "
                        + error.getMessage());
            }
        }
    }
}
```

### 9.2. Biên dịch và chạy

Yêu cầu **JDK 17 trở lên**. Đặt tệp `AspectConfigStateDemo.java` trong thư mục hiện hành:

```bash
javac --release 17 -encoding UTF-8 AspectConfigStateDemo.java
java AspectConfigStateDemo
```

**Standard output đã được đối chiếu khi biên dịch và chạy thực tế:**

```text
config.copied true
advice.before outer@v1
target outer
config.reload v2
advice.before in@v2
target in
advice.after in@v2
advice.finally in@v2
advice.after outer@v1
advice.finally outer@v1
caller O:ok:in
advice.before OK@v2
target OK
advice.after OK@v2
advice.finally OK@v2
caller ok:OK
advice.before abcd@v2
advice.finally abcd@v2
caller.error blocked abcd
advice.before X@v2
advice.finally X@v2
caller.error blocked X
```

Ví dụ sử dụng một thread để **thứ tự output xác định**; nó minh họa nested/reentrant invocation và thay snapshot trong khi outer chưa kết thúc. Mô hình tương tự cũng hỗ trợ concurrent invocations nếu các thành phần chia sẻ tuân thủ concurrency contract, nhưng demo không hứa thứ tự stdout khi chạy nhiều thread.

### 9.3. Giải thích `config.copied true`

`original` là một `HashSet` có thể sửa. Constructor `Policy` dùng `Set.copyOf` để lấy một cấu trúc không cho sửa. Khi code gọi `original.add("B")`, policy v1 **không** thấy prefix B. Đây là ví dụ về **defensive copy lúc tạo snapshot**.

Nó không có nghĩa mọi `Set.copyOf` thực hiện deep copy các elements. Ở đây element là `String` nên không có mutable fields bị lộ qua element.

### 9.4. Tại sao outer có `v1` nhưng inner có `v2`?

Outer gọi `intercepted.execute("outer")`:

1. Handler lấy một `Policy` snapshot **v1** vào local `policy`.
2. Handler xác minh `outer` (5 ký tự) trong ngưỡng v1 = 8.
3. Target in `target outer`.
4. Target gọi `store.reload(new Policy("v2", 3, ...))`.
5. Target dùng `self.execute("in")`; `self` được bind tới **proxy**.
6. Nested handler invocation lấy snapshot **v2** mới, kiểm `"in"` và hoàn tất.
7. Quay về outer handler, local `policy` vẫn trỏ v1; phần after/finally in `@v1`.

Kết quả:

```text
advice.before outer@v1
...
advice.before in@v2
...
advice.after outer@v1
advice.finally outer@v1
```

**Không có data race trong ví dụ**, nhưng vẫn tồn tại tình huống đổi phiên bản trong khi một call đang chạy. Chính local snapshot đã giữ vững nhất quán theo invocation. Nếu handler liên tục gọi `store.snapshot()` ở before/after, log outer rất dễ gắn nhãn v2 dù quyết định allow ban đầu dựa trên v1.

### 9.5. Dùng snapshot v2 với các lời gọi sau

Sau khi outer kết thúc, `PolicyStore` giữ v2:

- `"OK"` có 2 ký tự, không bắt đầu bằng prefix `X` nên được chấp nhận.
- `"abcd"` có 4 ký tự, vượt maxLength 3 nên bị chặn.
- `"X"` bị chặn bởi `forbiddenPrefixes` dù độ dài hợp lệ.

Mỗi invocation có local `text` và `policy` riêng. Handler ghi `finally` đúng version ngay cả khi ném `IllegalArgumentException`.

### 9.6. Demo này có thực sự dùng immutable config?

Có, trong phạm vi dữ liệu đã biểu diễn:

- `Policy` là record có version, int và unmodifiable `Set<String>`.
- `Policy` không cung cấp setter.
- Caller sửa `original` không đổi nội dung policy.
- Reload tạo **Policy object mới**, thay vì sửa set/fields của object cũ.
- `AtomicReference` công bố reference mới.
- Handler đọc reference **một lần mỗi invocation**.

Nếu policy trong thực tế chứa mutable rule object, map lồng nhau hoặc byte arrays, phải củng cố defensive copy tương ứng. **Không suy rộng rằng mọi record đều deep immutable.**

### 9.7. Vì sao target phải bind lại proxy?

Để cố ý tạo **nested intercepted call**, ví dụ gán `self` trỏ tới proxy và target gọi `self.execute("in")`. Nếu target gọi `this.execute("in")` với wrapper proxy đứng ngoài, lời gọi nội bộ có thể bỏ qua handler. Đây là vấn đề **interception coverage**, khác với việc cấu hình có immutable hay không.

Không nên dùng self-proxy trong business code chỉ để giải quyết mọi trường hợp; đây là dụng cụ minh họa reentrancy. Một cơ chế bytecode weaving execution có thể quan sát các đường gọi khác nếu join point được dệt.

### 9.8. Giới hạn cần hiểu

Ví dụ chưa triển khai đầy đủ pointcut DSL, around advice tiêu chuẩn, cấu hình từ file, async completion hay distributed reload. Nó dùng một `PolicyStore` duy nhất và policy được thay **giữa lúc target đang chạy** chỉ để kiểm chứng snapshot consistency. Trong ứng dụng thực, reload thường do component cấu hình bên ngoài điều khiển.

Lỗi `IllegalArgumentException` do handler ném chỉ chứng minh policy ở ranh giới proxy. Một caller có reference target gốc có thể gọi thẳng mà không qua kiểm tra này; **proxy không tự tạo security boundary không thể bypass**.

## 10. Dùng immutable snapshot không có nghĩa mọi caller thấy bản mới ngay tức khắc

Sau khi `active.set(v2)` hoàn tất, các invocation **đọc snapshot về sau** qua cơ chế phù hợp có thể thấy v2. Nhưng invocation **đã lấy v1 trước đó** vẫn có thể tiếp tục dùng v1.

Đây là **snapshot-at-entry semantics**. Nó rất hữu ích cho logging, metric policy và nhiều loại validation, vì một method không đổi tiêu chí giữa chừng. Tuy nhiên, không phải chính sách nào cũng được phép chấp nhận “dùng cấu hình cũ đến hết lời gọi”.

### Trường hợp yêu cầu policy luôn mới nhất

Ví dụ:

- Token vừa bị thu hồi.
- Một permission thay đổi giữa lúc xử lý tác vụ dài.
- Một circuit breaker cần phản ứng tức thời với trạng thái runtime.
- Một global shutdown flag.

Nếu yêu cầu bắt buộc là **kiểm tra quyền ngay tại thời điểm hành động nhạy cảm**, một snapshot lấy quá sớm có thể không đủ. Có thể cần revalidation tại đúng boundary, deadline/cancellation hoặc cơ chế khác được thiết kế riêng.

**Snapshot consistency và policy freshness là hai thuộc tính khác nhau.** Đừng chọn “mỗi invocation luôn dùng snapshot ban đầu” cho chính sách bảo mật nếu business/security contract yêu cầu quyết định khác.

### Phân biệt config với live state

Một `Policy` immutable có thể nói `allowedRoles` hoặc `limit`. Nhưng **số request đang hoạt động, trạng thái circuit breaker, token revocation list động** có thể là live state cần cơ chế concurrency và consistency khác, không nhất thiết nên đóng cứng chung một snapshot vĩnh viễn.

## 11. Cấu hình theo phiên bản và quan sát có thể kiểm chứng

Khi có reload, một trường `version` hoặc `revision` là dữ liệu hữu ích:

```text
policy.version = "2026-10-19T08:00Z#42"
invocation A -> selectedVersion = #42
reload
invocation B -> selectedVersion = #43
```

Version giúp trả lời:

- Invocation A bị từ chối bởi rule phiên bản nào?
- Log before/after có cùng policy không?
- Lỗi có xuất hiện sau một lần reload cụ thể?
- Rollback có tác động tới những invocation bắt đầu sau đó không?
- Cache metadata/predicate có được invalidated theo policy version không?

**Version phải đi cùng chính snapshot được dùng để đưa ra quyết định**, không lấy lại “current version” chỉ lúc ghi log.

### Không dùng version để che nội dung config thiếu chính xác

Hai snapshot có cùng version nhưng khác nội dung là lỗi quản lý configuration identity. Có thể dùng monotonic revision hoặc fingerprint phù hợp. Nếu một component đọc nhiều nguồn config, phải xác định lúc nào các nguồn tạo thành **một phiên bản policy hợp lệ** và chỉ publish sau khi validate đầy đủ.

## 12. Reload: xây dựng mới, xác thực rồi swap

Một chuỗi reload phù hợp:

```text
Source config (file/env/remote/CLI)
            |
         Parse
            |
      Validate rules
            |
    Build immutable Policy
            |
   Publish atomic reference
            |
   Subsequent invocations read new snapshot
```

**Không cập nhật dần snapshot đang được dùng**:

```java
// Thiết kế không phù hợp khi mutable state được nhiều caller đọc.
policy.allowedSet.clear();
policy.allowedSet.addAll(newAllowed);
policy.maxAttempts = newLimit;
```

Trong khoảng giữa các thao tác, advice có thể thấy một policy thiếu rules hoặc gồm nửa cũ/nửa mới.

### Publish thất bại thì sao?

Nếu parse/validate config mới thất bại, thường nên **giữ lại snapshot hợp lệ hiện tại**, ghi diagnostic an toàn, rồi báo trạng thái reload thất bại. Không tự publish một snapshot nửa chừng hoặc null nếu runtime không chấp nhận.

Đối với hệ thống yêu cầu fail-closed, chính sách khi reload lỗi có thể nghiêm ngặt hơn. Quan trọng là decision ấy được xác định trước, không vô tình do `NullPointerException` hoặc shared map bị xóa.

## 13. Nhiều updater cần quy tắc tranh chấp

`AtomicReference.set` thay thế cả snapshot an toàn, nhưng khi có **hai thread reload đồng thời**:

```text
Updater A: đọc revision 10, xây v11A
Updater B: đọc revision 10, xây v11B
Updater B: set(v11B)
Updater A: set(v11A)
```

A có thể ghi đè B dù quyết định của B mới hơn theo một tiêu chí bên ngoài. Đây là vấn đề **ordering/version policy**, không phải atomicity của một reference.

Nếu yêu cầu **optimistic concurrency**, có thể cần `compareAndSet`, expected revision, monotonic version hoặc một writer/queue duy nhất. Nhưng **CAS loop** cũng phải xét việc build lại snapshot, validate điều kiện mới và cách xử lý thất bại. Đừng dùng `set` rồi tuyên bố hệ thống có tính nhất quán giao dịch ở mức nhiều tác giả.

## 14. Trạng thái của before/after tách rời: một vấn đề khác

Nếu một AOP engine tách **before advice** và **after advice** thành hai method khác nhau:

```text
before -> nhận snapshot và mốc thời gian
target -> chạy
after  -> cần dùng đúng snapshot/mốc của before
```

Local variable của `before` không tự tồn tại trong stack frame của `after`.

Giải pháp tùy API:

- **Around advice** bao trọn target, dùng local variables.
- **Invocation context object** được engine cung cấp để liên hệ các phase.
- Một wrapper/interceptor pipeline tự tạo state rồi chuyển cho các phase.
- Một **stack state theo flow** chỉ khi before/after cùng thread, có LIFO semantics và cleanup đúng.
- Context correlation riêng nếu hoàn tất async xảy ra sau khi method đã return.

**Không dùng field `lastStartNanos` hoặc `lastPolicy` trên singleton aspect** để nối hai method advice; sẽ hỏng khi có concurrency/reentrancy.

### Correlation key

Hai lượt gọi cùng method, cùng target, cùng thread hoặc cùng request vẫn có thể là **hai invocation riêng**. Method name, target ID và thread ID không phải invocation ID duy nhất. Nếu một engine không hỗ trợ context per-invocation trực tiếp, cần một cơ chế correlation có ràng buộc rõ và có cleanup.

## 15. `ThreadLocal` không phải mặc định per-invocation

`ThreadLocal` lưu giá trị theo **thread**, không theo lời gọi:

```java
// Minh họa dữ liệu theo thread.
static final ThreadLocal<CallContext> CURRENT =
        new ThreadLocal<>();
```

Nếu outer advice đặt context A, inner advice cùng thread đặt context B, sau khi inner trả về outer có thể đọc nhầm B nếu không restore.

Trong thread pool, worker có thể phục vụ request R1 rồi R2; nếu quên `remove()` hoặc restore, R2 có nguy cơ thấy dữ liệu của R1.

### Pattern restore scope

```java
// Pseudocode trong method có work được truyền vào.
CallContext previous = CURRENT.get();
try {
    CURRENT.set(new CallContext(...));
    work.run();
} finally {
    if (previous == null) {
        CURRENT.remove();
    } else {
        CURRENT.set(previous);
    }
}
```

`CallContext` và `work` trong snippet là thành phần do ứng dụng định nghĩa, không phải chương trình chạy độc lập. Pattern này minh họa cách restore trên **cùng thread**; nó chưa tự xử lý context đi qua executor hoặc các nested flow phi-LIFO.

**Với một around advice đồng bộ, local variables thường đơn giản, dễ đúng hơn ThreadLocal.**

## 16. Bất đồng bộ: local state có thể cần được capture

Một method trả `CompletableFuture<T>` có thể return trước khi computation hoàn tất. Nếu around advice đo tới **method return**, nó không nhất thiết đo end-to-end latency đến khi future complete.

```text
method call
   |
   +-- return CompletableFuture<T> --- around synchronous exits
                    |
                    +-- worker computes
                    |
                    +-- Complete(value) / error / cancel
```

Nếu mục tiêu là quan sát completion, có thể tạo immutable per-operation context và capture nó trong callback:

```java
// Minh họa: original là CompletableFuture<String> đã có sẵn.
long startedAt = System.nanoTime();
Policy policy = store.snapshot();

java.util.concurrent.CompletableFuture<String> observed =
        original.whenComplete((value, error) -> {
            recordCompletion(
                    policy.version(),
                    System.nanoTime() - startedAt,
                    error);
        });
```

`recordCompletion` là hàm quan sát do ứng dụng định nghĩa. Lưu ý:

- `whenComplete` tạo một stage phụ, với semantics exception riêng.
- Nếu callback ném lỗi, stage trả về có thể bị ảnh hưởng theo API.
- Captured policy là snapshot per-operation, không tự chuyển mọi ThreadLocal context sang worker.
- Nếu callback sống lâu, closure có thể giữ reference tới dữ liệu/target hơn dự kiến.
- Cần xử lý cancellation/retry/timeouts và phân biệt *method-return* với *future-completion*.

### Context propagation và security

Trace ID của request có thể cần truyền qua worker; đó là **request context**, không đồng nghĩa state timer của một method. Dữ liệu được propagate có thể bị giả mạo nếu không có boundary xác thực. Có cùng context object không tự chứng minh tác vụ được cấp quyền.

## 17. Trạng thái mỗi invocation không có nghĩa phải tạo aspect mới

Một shared aspect có thể hoàn toàn stateless về per-call state:

```text
Shared Advice instance
  |
  +-- call 1 -> local Policy v1, local start 1
  +-- call 2 -> local Policy v2, local start 2
  +-- call 3 -> local Policy v2, local start 3
```

Việc Java tạo **stack frame riêng cho từng method invocation** giúp local variables tách biệt kể cả cùng một object advice.

Tạo aspect instance mới mỗi lần cũng có thể hoạt động, nhưng tăng chi phí khởi tạo và không giải quyết shared dependency/static mutable state bên ngoài.

**Trục lifecycle aspect khác trục lifecycle invocation state.**

### Per-target aspect cũng có thể bị race

Nếu hai thread cùng gọi target X, một aspect per-target gắn với X vẫn có thể phục vụ cả hai. Vì thế field `currentOperation` trên per-target aspect cũng sai nếu state cần per-call. Cô lập **theo target** không tự cô lập **theo invocation**.

## 18. Immutable policy khác state máy trạng thái dùng chung

Có những concern AOP cần state dùng chung **thực sự biến đổi theo thời gian**, ví dụ:

- Circuit breaker lưu open/half-open/closed.
- Rate limiter giữ quota/token counters.
- Bulkhead theo dõi số request đang chạy.
- Audit sink có queue/hạn mức.
- Global metric counters.
- Cache chứa entry được thêm/xóa.

Các thành phần này không phải “config immutable”; chúng cần **concurrency contract** riêng: atomic fields, locks, concurrent data structures hoặc state machine có giới hạn.

Một cấu trúc tốt có thể là:

```text
Immutable Policy snapshot:
   maxConcurrent = 20
   timeoutMillis = 500

Shared mutable runtime limiter:
   activeCalls = 7
   atomic acquire/release

Per-invocation:
   permitAcquired = true
   startedAtNanos = ...
```

**Không để đồng nhất “mọi thứ shared đều immutable”** làm mất chức năng của shared runtime counters. Thay vào đó tách ba loại có mục đích rõ.

## 19. Concurrency: `volatile`, `AtomicReference` và khóa dùng cho việc gì?

| Công cụ Java | Hữu ích cho | Không tự giải quyết |
| --- | --- | --- |
| `final` + object immutable | Xây dựng snapshot không đổi | Deep immutability nếu giữ mutable references |
| `volatile Policy` | Công bố/đọc reference snapshot | Atomic multi-object transaction, mutable policy internals |
| `AtomicReference<Policy>` | Atomic get/set/CAS của reference | Đúng ordering giữa nhiều updater nếu không có policy revision |
| `AtomicLong`, `LongAdder` | Bộ đếm dùng chung | Correlation của nhiều field per-invocation |
| `synchronized` | Bảo vệ invariant trong vùng khóa | Chọn đúng scope cho một invocation |
| `ConcurrentHashMap` | Concurrent map operations | Tính đúng của key, lifecycle và multi-step invariant |
| Local variables | State tách theo stack frame | Kéo dài ngữ cảnh qua async không có carrier |

Một solution đúng thường kết hợp **immutable config + AtomicReference for publication + local state + shared counter chuyên dụng**. Không ép mọi vấn đề vào một primitive duy nhất.

### Không nhầm visibility với semantic consistency

Một `volatile currentId` luôn có thể được đọc dưới memory visibility phù hợp, nhưng vẫn **có thể là ID của request khác**. Đọc được giá trị mới nhất không đồng nghĩa đọc được giá trị đúng của invocation mình.

## 20. Config metadata cache và invocation cache khác nhau

Một shared aspect có thể cache những thứ tương đối ổn định:

- `Method` metadata.
- Resolved annotation policy.
- Precompiled expression hoặc regex.
- Join point signature.
- Type hierarchy resolution.
- Config snapshot revision.

Nhưng `startNanos`, input cụ thể hoặc kết quả hiện tại **không phải metadata cache chung**.

### Cache key khi có reload

Giả sử một method được cho phép ở policy v1 nhưng bị từ chối ở v2. Nếu cache decision chỉ theo `methodName`, kết quả cũ có thể được dùng lại sau reload.

Một key hợp lý có thể gồm:

```text
(method identity, policyRevision, targetType, relevant static metadata)
```

Nếu quyết định còn phụ thuộc runtime args, tenant hoặc caller identity, không được bỏ những yếu tố ấy khỏi semantics. Không phải lúc nào cũng nên cache kết quả dynamic predicate.

### Class loader và module scope

Trong ứng dụng modular/plugin, cùng tên class có thể được định nghĩa bởi class loaders khác nhau, tạo type identity khác nhau. Cache theo chuỗi class name đơn thuần có thể trộn metadata từ các loader khác. Chọn key phù hợp và có eviction/invalidation khi loader/plugin reload.

## 21. Tránh rò dữ liệu qua cấu hình

Một policy object đôi khi chứa:

- Secret/token.
- Địa chỉ endpoint.
- Danh sách quyền.
- Identifier tenant.
- Chính sách mask log.
- Retry/backoff settings.

Immutable không đồng nghĩa **được phép log công khai**. Nếu in `policy.toString()` trực tiếp, record có thể để lộ dữ liệu nhạy cảm qua generated `toString`.

Nên chỉ log những trường cần thiết như **revision**, một vài nhãn an toàn, kết quả policy không nhạy cảm. Không đưa raw secrets vào shared aspect fields hoặc invocation context nếu không cần. Với key material, lifecycle zeroization có vấn đề riêng mà immutable Java String/record không tự giải quyết.

## 22. Cấu hình cho nhiều tenant hoặc nhiều request

Một snapshot **toàn cục duy nhất** có thể không đúng nếu tenant A và B dùng chính sách khác nhau. Có thể xây:

```text
Immutable registry snapshot:
    tenant A -> PolicyA@revision 3
    tenant B -> PolicyB@revision 7

Invocation A:
    tenantId verified -> select PolicyA@3

Invocation B:
    tenantId verified -> select PolicyB@7
```

Tuy nhiên việc lấy `tenantId` phải dựa vào context đã được xác thực; không cho phép người gọi tự chọn policy “dễ hơn” bằng cách gửi một key tùy ý.

Nếu policy per-tenant được reload độc lập, cần định nghĩa semantics revision theo tenant và phạm vi atomic update: thay từng policy hay thay cả registry snapshot. Tránh map mutable bị thay từng entry mà caller thấy trạng thái trung gian ngoài hợp đồng.

### Policy hierarchy

Khi config kết hợp default, environment, tenant override và request override, nên:

1. Xác định precedence.
2. Validate toàn bộ kết quả.
3. Tạo **effective immutable policy**.
4. Dùng effective policy snapshot cho invocation.
5. Giữ metadata về nguồn/revision nếu cần debug.

Không viết advice theo kiểu đọc mỗi key từ nhiều nguồn mutable khác nhau **giữa lúc xử lý** rồi giả định tất cả cùng một cấu hình nhất quán.

## 23. Version snapshot trong log, metric và error policy

Một invocation thất bại nên có thể trả lời:

- Nó đã chọn phiên bản policy nào?
- Nếu retry, mỗi attempt dùng cùng policy hay refresh?
- Nếu reject vì rule, rule/code nào quyết định?
- Nếu reload giữa lúc chạy, đã sử dụng cấu hình cũ hay mới?
- Lỗi được chuyển đổi theo exception mapping policy version nào?
- Metric bucket và log line có tương quan cùng invocation ID không?

**Nên ghi config version đã chọn trong per-invocation context.** Đừng gọi `store.snapshot().version()` ở đoạn after/finally chỉ để tạo nhãn bởi có thể lúc ấy đã reload.

### Version và metric cardinality

Gắn revision duy nhất cho từng micro-update vào metric labels có thể làm cardinality tăng mạnh. Có thể dùng revision trong log/trace debug và nhãn metric tổng quát hơn tùy kiến trúc quan sát. Không chọn key high cardinality như full request ID cho mọi metric time-series.

## 24. Lỗi trong validation, reload và advice

Tách config và per-call state giúp xác định ranh giới lỗi:

**Reload error:** không parse/validate được policy mới → không publish snapshot nửa hoàn chỉnh, xử lý theo policy.

**Invocation validation error:** snapshot đã hợp lệ nhưng input không hợp lệ → advice có thể từ chối theo contract, không làm đổi global policy.

**Advice infrastructure error:** logger, reflection, interceptor helper gặp lỗi → không nhầm là policy deny/allow.

**Target error:** target ném exception → có thể preserve/map theo rule đã snapshot.

Nếu advice dùng `finally` để log duration/outcome, lỗi logger có thể thay outcome. Cần chính sách ưu tiên lỗi target và audit theo tính bắt buộc của concern.

### Không để một request sửa config toàn cục

Một request input không hợp lệ không nên có quyền gọi `store.reload(...)` chỉ vì advice có thể truy cập provider. Trong demo, target reload để phơi bày snapshot semantics; trong kiến trúc thực, **config ownership** nên thuộc thành phần quản trị riêng.

## 25. Sự khác biệt giữa proxy và bytecode weaving

Một Java Dynamic Proxy chỉ chạy interceptor khi caller gọi **proxy object** trên method được proxy công bố. Nếu caller gọi raw target trực tiếp, interceptor không chạy; cấu hình dù bất biến và state dù tách đúng cũng không làm proxy bao phủ mọi đường gọi.

```text
caller -> proxy -> advice(store.snapshot) -> target
caller -------------------------------> raw target  [bypass]
```

Một engine AOP dệt **execution join point** vào bytecode target có thể bao phủ các đường khác, nếu class/method thuộc weaving scope, pointcut khớp và engine hỗ trợ.

**Thiết kế state tốt không thay thế yêu cầu kiểm chứng interception coverage.**

### Call vs execution

- Một **call join point** tại caller có thể quan sát vị trí phát lời gọi.
- Một **execution join point** tại callee có thể quan sát lúc method body chạy.
- Trạng thái per-invocation phải gắn đúng loại sự kiện mà advice thực sự xử lý.
- Nếu quanh cùng một thao tác có nhiều advice call/execution, có thể có **nhiều invocation contexts**, không tự đồng nhất chúng thành một.

## 26. Aspect instance dùng chung không tự làm target thread-safe

Giả sử aspect đã tách local state chính xác, nhưng target có field:

```java
// Ví dụ business target không an toàn đa luồng.
class CounterService {
    private int count;

    int next() {
        return ++count;
    }
}
```

Hai thread vẫn có thể lost updates trong target. **Thread safety của aspect và thread safety của target là hai vấn đề riêng**.

Ngược lại, một target bất biến không làm advice an toàn nếu advice giữ một shared `currentId`. Phải kiểm tra cả hai bên của interception boundary.

## 27. Per-call state và quanh exception/retry

Với retry advice, bộ đếm `attempt` nên là local của **caller invocation đang retry**:

```java
// Pseudocode around; loại lỗi và API proceed do ứng dụng xác định.
int attempt = 0;
Policy policy = store.snapshot();
for (;;) {
    attempt++;
    try {
        return proceed();
    } catch (TransientFailure error) {
        if (attempt >= policy.maxAttempts()) {
            throw error;
        }
        backoff(attempt);
    }
}
```

Nếu `attempt` là một field singleton, invocation B có thể tăng nó khiến A dừng sai số lần retry. Nếu config được reload trong lúc A đang retry, policy “snapshot once” khiến A giữ một giới hạn nhất quán giữa các attempt. **Điều đó phải là contract có chủ đích**, không phải kết quả ngẫu nhiên.

Không retry mù quáng nếu target có side effect không idempotent, nếu thao tác bị hủy hoặc lỗi thuộc nhóm không thể phục hồi.

## 28. Trạng thái có thể là một object riêng, không nhất thiết nhiều biến local

Một invocation phức tạp có thể giữ:

```java
// Java 17 minh họa data holder bất biến.
record CallContext(
        String operationId,
        String configRevision,
        long startedAtNanos
) { }
```

Một context object tạo **mới cho từng lượt gọi** và giữ ở local/closure tương ứng vẫn là per-invocation. Nhưng nếu một aspect singleton tạo `new CallContext(...)` rồi lưu vào field `currentContext` dùng chung, lỗi state mixing quay trở lại.

**Tên class “InvocationContext” không tự tạo scope per-invocation.** Ownership, cách phân phối reference và lifetime mới quyết định.

### Mutable per-call object

Một mutable state object dùng riêng cho một invocation có thể hợp lệ (ví dụ chứa số attempt và accumulated errors) nếu nó không được chia sẻ sai. Khi truyền vào nhiều thread, phải xác định thread confinement hoặc synchronization. Bất biến vẫn hữu ích cho config, nhưng không bắt buộc mọi per-call working state đều bất biến.

## 29. Không ghép before và after bằng một shared field

Một cặp advice tách rời:

```text
before(call A) -> start_A
before(call B) -> start_B
after(call A)  -> cần start_A
after(call B)  -> cần start_B
```

Nếu dùng `Aspect.start`, B ghi đè A. Nếu dùng `ThreadLocal<Long>` đơn lẻ, nested calls trên cùng thread có thể ghi đè. Cần một trong các cơ chế:

- Around advice với local start.
- Join-point invocation context được engine hỗ trợ.
- Stack theo flow đúng LIFO và cleanup.
- Per-invocation map có key thực sự duy nhất, bộ nhớ có giới hạn và cleanup mọi đường.
- Explicit carrier/context qua async callback.

Không dùng method name, target identity hoặc thread ID đơn thuần để giả làm invocation key nếu recursion hoặc concurrent calls có thể xảy ra.

## 30. Kiểm thử deterministically, không chỉ hy vọng race xuất hiện

Ví dụ chạy nhiều thread ngẫu nhiên có thể không lộ lỗi field chung ngay. Có thể dùng `CountDownLatch` hoặc `CyclicBarrier` để ép interleaving:

```text
A: capture snapshot v1, pause before target
Reload: publish v2
B: capture snapshot v2, execute
A: resume, finish after/finally using v1
```

Test cần assert:

- A log policy version v1 ở before và after.
- B log v2.
- A không đọc per-call ID của B.
- Reload không mutate v1 đã được A giữ.
- Target return/exception đúng contract.
- Nếu validation rule thay giữa chừng, hành vi theo snapshot semantics đã định.

**Reentrancy test một thread** như trong demo đã đủ bắt một số lỗi, còn multi-thread tests giúp phát hiện data races và publication bugs.

## 31. Ma trận kiểm thử

| Trường hợp | Điều phải xác minh |
| --- | --- |
| Snapshot chứa immutable scalars | Không đổi sau khi khởi tạo |
| Caller sửa Set đã đưa vào constructor | Policy không thay vì đã defensive copy |
| Snapshot có nested mutable element | Được chuyển thành immutable hoặc copy đúng |
| Getter trả array/list | Không để lộ mutable internal state |
| Reload hợp lệ | Publish nguyên snapshot đúng một lần |
| Reload cấu hình lỗi | Snapshot cũ vẫn hợp lệ theo policy |
| Hai updater chạy đồng thời | Có quy tắc revision/order rõ |
| A lấy v1, reload, A hoàn tất | A vẫn dùng v1 nội bộ |
| B bắt đầu sau reload | B chọn v2 theo policy |
| Nested invocation trong cùng thread | Local state outer không bị inner ghi đè |
| Hai invocation concurrent | Mỗi call giữ đúng args/timer/config |
| Shared aspect singleton | Local state cô lập dù một aspect instance |
| Per-target aspect | Hai calls cùng target vẫn có state riêng |
| Before/after riêng | Context mapping đúng invocation, cleanup đủ |
| Thread pool reuse | Không giữ context request trước |
| Future hoàn tất muộn | Phân biệt method return và async completion |
| Retry và config reload | Số lần thử/phiên bản theo contract |
| Cấu hình đa tenant | Không lẫn policy khác tenant, key xác thực |
| Cache metadata sau reload | Key/invalidation tính tới revision |
| Logging/metric sink lỗi | Không vô tình che target error |
| Raw target bypass proxy | Nhận diện thiếu interception, không gán lỗi cho state |
| Class loader/plugin reload | Cache không trộn type identity, không leak loader |
| Permission revocation khẩn cấp | Freshness policy đáp ứng yêu cầu bảo mật |

### Đo những thứ độc lập

Một test tốt phân biệt:

1. **Aspect invocation count** (lần advice thực sự chạy).
2. **Target execution count** (lần business logic chạy).
3. **Config snapshot selected** (version/identity tại entry).
4. **Per-call state** (argument, ID, timestamp, attempt).
5. **Final caller outcome** (return/exception/async completion).

Nhìn duy nhất một dòng log có thể không biết lỗi do thiếu advice, sai policy hay log sink bị tắt.

## 32. Chẩn đoán khi dữ liệu cấu hình hoặc state bị sai

**Triệu chứng: cùng một lời gọi in v1 ở before, v2 ở after.**

- Có gọi `store.snapshot()` nhiều lần trong cùng invocation không?
- Có mutate object policy cũ không?
- `version` có thuộc cùng object snapshot với rules không?

**Triệu chứng: request A có trace ID của B.**

- Có field `currentRequestId` dùng chung không?
- Có nested advice/reentrancy không?
- Có `ThreadLocal` không restore/remove?
- Có callback giữ/copy sai context không?

**Triệu chứng: reload thỉnh thoảng gây giá trị policy lộn xộn.**

- Có update nhiều mutable fields từng cái?
- Có publish trước khi validate/build xong?
- Có nhiều updater làm overwrite stale revision?
- Có mutable nested list/map bị share từ bên ngoài?

**Triệu chứng: policy đúng nhưng một số method không được kiểm tra.**

- Lời gọi có đi qua proxy/weaving boundary?
- Pointcut có khớp method thực tế?
- Có self-invocation hoặc raw target bypass?
- Có module/class loader/plugin khác ngoài scope?

**Triệu chứng: memory tăng sau nhiều request.**

- Aspect singleton có field giữ request/argument/result không?
- Callback có capture object graph lớn?
- Cache có eviction/bounds?
- Registry per-target có giữ strong references?
- ThreadLocal có remove/restore sau task không?

Phân loại đúng **config consistency**, **invocation isolation**, **interception coverage** và **lifetime management** trước khi sửa.

## 33. Các anti-pattern cần tránh

1. **`final Map` là đủ bất biến.** Reference final nhưng nội dung map vẫn có thể sửa.
2. **Record bảo đảm deep immutable.** Record chỉ bảo đảm component fields final; elements có thể mutable.
3. **Cứ dùng `Collections.unmodifiableMap(source)` là đã copy.** Wrapper read-only có thể vẫn phản ánh mutation của source bên dưới.
4. **Cấu hình reload bằng `clear` rồi `addAll` vào set đang dùng.** Caller có thể quan sát trạng thái giữa chừng.
5. **Mỗi field config có volatile thì cả config atomic.** Không tạo transaction nhiều field.
6. **Lấy config mỗi lần cần để luôn mới.** Có thể tạo mixed-version trong một invocation.
7. **Lấy config một lần luôn an toàn cho mọi security decision.** Freshness/revocation policy có thể đòi revalidation.
8. **Lưu `currentId` trong aspect singleton.** Concurrency/reentrancy làm state bị ghi đè.
9. **Dùng `AtomicReference<CurrentCall>` cho mọi request.** Vẫn chỉ có một slot shared.
10. **`synchronized` getter/setter bảo đảm correlation.** Scope vẫn sai.
11. **Một ThreadLocal mặc định đại diện invocation.** Nested call/thread pool có thể lẫn state.
12. **Shared aspect không thể dùng local state.** Java method frame vẫn là per-call.
13. **Per-target aspect tự thread-safe.** Một target vẫn phục vụ nhiều threads.
14. **Per-call aspect tự cô lập static field.** Static thuộc class identity.
15. **Lấy policy revision ở after bằng current snapshot.** Có thể sai revision đã dùng khi decision.
16. **Metadata cache chỉ cần method name.** Overload/type/loader/revision có thể làm cache sai.
17. **Capturing cả request vào callback không tốn lifetime.** Có thể giữ dữ liệu lâu.
18. **Proxy tồn tại nghĩa là mọi direct/self call đều chạy advice.** Phải kiểm chứng đường interception.
19. **Cứ dùng immutable snapshot là hệ thống thread-safe.** Shared runtime state và target có thể còn race.
20. **Mỗi invocation phải tạo một policy object mới.** Immutable snapshot có thể share; chỉ per-call working state cần cô lập.
21. **Không cần policy version trong log.** Khó chẩn đoán khi reload giữa các calls.
22. **Dùng một bộ đếm request trong local, sau đó coi là metric tổng.** Local không tự aggregate.
23. **Reload lỗi vẫn publish policy nửa hợp lệ.** Caller có thể đưa ra quyết định sai.
24. **Set.copyOf luôn deep-copy object bên trong.** Chỉ là unmodifiable snapshot của cấu trúc set; mutable elements vẫn cần xử lý.
25. **Cấu hình bất biến mặc nhiên không chứa bí mật.** Generated `toString` hoặc log vẫn có thể lộ secret.
26. **Nếu config mới publish, mọi in-flight operation tức khắc chuyển sang v2.** Snapshot-at-entry cố ý giữ policy đã chọn.
27. **Hai reload đồng thời dùng set là có ordering đúng ý nghiệp vụ.** Atomic swap không giải quyết stale overwrite.
28. **Scope request bằng ThreadLocal tự truyền qua executor.** Thread khác có context riêng.
29. **Dùng chung một `InvocationContext` rồi ghi đè fields.** Tên context không tạo ownership.
30. **Bài test không ném exception là đủ.** Phải test error/finally/async/cancel/reload.

## 34. Checklist triển khai một aspect có cấu hình có thể reload

1. **Phân loại mọi field:** immutable config, shared runtime counter, target state, invocation state hay request context?
2. **Định nghĩa policy type:** field nào được yêu cầu, invariant nào cần validate?
3. **Loại bỏ mutable aliases:** copy input collection, nested mutable components và bảo vệ getter.
4. **Dùng snapshot immutable:** không mutate snapshot đang được chia sẻ.
5. **Công bố an toàn:** dùng `AtomicReference` hoặc `volatile` reference phù hợp.
6. **Xác định concurrency của reload:** một writer, CAS hay monotonic revision?
7. **Chọn snapshot một lần trong invocation** nếu yêu cầu policy consistency theo entry.
8. **Gắn policy version vào context/log** của invocation ấy.
9. **Giữ state per-call ở local/appropriate scope:** timer, arguments, retry attempts, Throwable.
10. **Nếu before/after tách:** có invocation correlation đúng hoặc dùng around.
11. **Nếu async:** chọn nghĩa của completion, carrier, cleanup, cancellation.
12. **Nếu có live security state:** nêu rõ freshness requirements, không dựa mù quáng vào snapshot cũ.
13. **Thiết kế advice ordering:** mapping/retry/authorization/log dùng snapshot thế nào?
14. **Kiểm chứng proxy/weaving coverage:** mọi đường bắt buộc có đi qua advice không?
15. **Test race/reentrancy/reload:** dùng latches, nested calls và deterministic asserts.
16. **Quan sát memory/caches:** eviction, loader lifetime, sensitive-data retention.

Đừng chọn `AtomicReference` hoặc `ThreadLocal` như một quy tắc áp dụng vô điều kiện. Chọn chúng sau khi đã xác định rõ **ai sở hữu state và cần nhìn thấy state tại thời điểm nào**.

## 35. Bài tập thực hành

1. Biên dịch và chạy `AspectConfigStateDemo.java`, đối chiếu từng dòng stdout.
2. Vì sao `config.copied true`? Nếu bỏ `Set.copyOf`, giá trị có thể thay thế nào?
3. Thêm `"B"` vào `forbiddenPrefixes` của config mới và dự đoán kết quả lời gọi `execute("B")`.
4. Đổi handler để gọi `store.snapshot().version()` ở `finally` thay cho `policy.version()`. Dự đoán dòng log của outer khi reload.
5. Thêm field `String currentText` trên `Advice`, dùng field này ở finally. Vì sao nested call tạo output sai?
6. Đổi `Target.self.execute("in")` thành `this.execute("in")`. Vì sao không còn nested proxy advice trong mô hình wrapper?
7. Dùng hai thread với `CountDownLatch`: A đọc v1 rồi chờ, updater set v2, A tiếp tục. Kiểm tra A vẫn dùng v1.
8. Tạo 1000 invocations concurrent qua một shared advice; ghi số lời gọi vào `LongAdder` và per-call ID trong local, không dùng shared `currentId`.
9. Thêm `int attempts` vào immutable policy, và retry attempt count local trong advice; reload giữa retry, mô tả policy áp dụng.
10. Thêm hai updater cùng tạo version từ một revision cũ, rồi đề xuất `compareAndSet` để tránh stale overwrite.
11. Tạo record chứa `List<MutableRule>`; chứng minh `List.copyOf` không ngăn sửa một `MutableRule` bên trong.
12. Tạo defensive-copy getter cho `byte[]` trong một policy; test sửa array trả về không đổi policy.
13. Tạo một policy đa tenant có mapping tenant → immutable policy; cách nào ngăn tenant A chọn policy B?
14. Tạo một before/after advice tách rời, dùng invocation scope/stack để giữ đúng start time khi có recursion.
15. Dùng một executor một thread phục vụ hai request nối tiếp; cho ThreadLocal tồn tại giữa hai task rồi sửa bằng restore/remove.
16. Tạo `CompletableFuture` hoàn tất sau return; so sánh timer tới method return và timer tới completion.
17. Tạo metric cache key theo method + revision; reload rule rồi đảm bảo cache không dùng result cũ.
18. Thiết kế chính sách token revocation: điều kiện nào đòi revalidation giữa một invocation dài thay vì snapshot-at-entry?
19. Thêm logging policy version vào error path của demo; đảm bảo validation error vẫn in version của invocation đã chọn.
20. Viết ma trận QA có ít nhất một case cho defensive copy, multi-field update, nested invocation, concurrent reload, async completion, direct target bypass và sensitive-data logging.

**Tự kiểm tra:** Vì sao `final` không đủ để tạo immutable object graph? Tại sao một `AtomicReference<Policy>` có thể publish snapshot nguyên vẹn nhưng không giải quyết mọi stale update? Nếu A bắt đầu ở v1 và reload sang v2 trong lúc A chạy, before/after của A nên in gì? Per-invocation local state giải quyết lỗi nào mà volatile field không giải quyết được? Per-target aspect có chắc tách state của hai lượt gọi cùng target không? Vì sao request context qua executor phải propagate riêng? Khi nào snapshot-at-entry không đủ cho policy bảo mật?

## Kết luận

**Tách cấu hình bất biến khỏi trạng thái mỗi lần gọi** giúp AOP vừa **chia sẻ policy an toàn** vừa **cô lập dữ liệu động của từng invocation**. Cấu hình cần được xây dựng đầy đủ, defensive-copy các thành phần mutable, validate rồi **publish nguyên snapshot**; mỗi advice invocation thường nên **đọc một snapshot tại entry** và gắn version vào local/context của mình. Mốc thời gian, input, attempt, kết quả và lỗi nên thuộc đúng invocation thay vì nằm trong shared mutable fields.

Trong những hệ thống có reload, nhiều thread, nested calls hoặc callback bất đồng bộ, cần phân biệt **snapshot consistency**, **config freshness**, **request/task context**, **state dùng chung thực sự** và **interception coverage**. Không có một primitive Java hay một scope aspect đơn lẻ giải quyết tất cả. Thiết kế đúng luôn bắt đầu từ câu hỏi: **dữ liệu này thuộc về ai, sống bao lâu, được cập nhật bởi ai, và người đọc phải thấy phiên bản nào?**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-concurrency-004">Trạng thái theo luồng và giới hạn với nhóm luồng</a>

<details>
<summary>Xem chi tiết</summary>

# Trạng thái theo luồng và giới hạn với nhóm luồng trong Aspect/AOP

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), một **aspect** thường cần dữ liệu để thực hiện advice: mã tương quan của lượt gọi, thời điểm bắt đầu đo thời lượng, thông tin truy vết, cờ chống tái nhập, hoặc trạng thái đã mở một scope. Khi nhiều lời gọi chạy đồng thời, câu hỏi then chốt là **trạng thái nào thuộc một lời gọi, trạng thái nào thuộc một luồng (thread), và trạng thái nào được chia sẻ?**

Một lựa chọn phổ biến để giữ thông tin gắn với thread trên JVM là **`ThreadLocal<T>`**. Mỗi thread truy cập cùng một đối tượng `ThreadLocal` sẽ có **giá trị riêng của thread đó**. Nhờ vậy, một advice có thể đọc dữ liệu mà một bước bên ngoài thiết lập trên **cùng thread** mà không cần thêm tham số vào mọi phương thức. Tuy nhiên, `ThreadLocal` **không phải request scope tự động**, không phải đối tượng per-invocation và không tự truyền context qua executor, future hay mọi kiểu công việc bất đồng bộ.

Vấn đề càng quan trọng khi dùng **nhóm luồng (thread pool)**: worker thường sống lâu và được **tái sử dụng cho nhiều tác vụ khác nhau**. Nếu advice hoặc một bộ chặn đặt dữ liệu vào `ThreadLocal` mà không dọn sau khi xử lý, tác vụ sau có thể đọc dữ liệu của tác vụ trước. Đây vừa là lỗi về tính đúng, vừa là nguy cơ giữ tham chiếu trong bộ nhớ và rò rỉ dữ liệu giữa những yêu cầu.

**Nguyên tắc xuyên suốt:** lựa chọn vòng đời trạng thái theo **đơn vị công việc cần bảo vệ**, không tự đồng nhất **thread**, **lượt gọi advice**, **dynamic control flow**, **logical request** hay **transaction**. Khi phải dùng `ThreadLocal`, cần xác định chủ sở hữu scope, hỗ trợ lời gọi lồng nhau, **khôi phục/xóa trong `finally`**, và quy định rõ ranh giới async/thread pool.

Bài trình bày **Aspect/AOP cơ bản và nâng cao nói chung**. Cơ chế instantiation và weaving của mỗi công nghệ có thể khác nhau. Ví dụ dùng Java 17 và JDK dynamic proxy để minh họa một cách cài AOP qua proxy; nó không đại diện cho mọi loại join point hoặc cho một bộ bytecode weaver cụ thể.

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

1. Giải thích khác biệt giữa **trạng thái dùng chung của aspect instance**, **trạng thái cục bộ của một lần advice** và **trạng thái theo thread**.
2. Hiểu `ThreadLocal` lưu dữ liệu theo **danh tính thread**, không theo yêu cầu HTTP hay tác vụ nghiệp vụ.
3. Phân biệt **per-thread**, **per-control-flow**, **per-target/per-this** và **per-invocation** khi mô hình AOP hỗ trợ các chính sách tương ứng.
4. Mô tả điều gì xảy ra khi các worker thread trong pool được tái sử dụng, đặc biệt khi `set()` thiếu `remove()`.
5. Thiết kế scope theo nguyên tắc **lưu giá trị trước → thiết lập giá trị mới → thực thi → khôi phục** trên cả đường return và throw.
6. Hiểu giới hạn của `InheritableThreadLocal`, executor, callback, `CompletableFuture`, parallel processing và các mô hình bất đồng bộ.
7. Xác định khi nào cần truyền một **context snapshot** hoặc mã tương quan bất biến qua ranh giới task thay vì chia sẻ toàn bộ object mutable.
8. Nhận diện race condition do field trên singleton aspect, rò bộ nhớ qua thread pool và nguy cơ bảo mật của dữ liệu còn sót.
9. Chạy ví dụ Java 17 có JDK proxy, nested scope, lỗi nghiệp vụ, worker tái sử dụng và explicit propagation.
10. Thiết kế kiểm thử cho trạng thái theo thread, scope lồng nhau, thời điểm dọn và độ phủ join point.

## 2. Tách ba loại trạng thái trước khi chọn công cụ

### 2.1. Biến cục bộ theo một lần gọi advice

Một around advice đo thời gian có thể khai báo biến cục bộ:

```text
around invocation:
    start = monotonicClock()
    try:
        return proceed()
    finally:
        record(monotonicClock() - start)
```

Đây là **giả mã**, không phải API Java hoàn chỉnh. Biến `start` gắn với **một lần advice chạy**; hai lời gọi đồng thời không dùng cùng một biến cục bộ của cùng stack frame.

Nếu chỉ cần thời gian của một lần gọi, **biến cục bộ** thường đơn giản hơn `ThreadLocal`: không phải quản lý gắn/dỡ ngữ cảnh trên worker và không vô ý truyền dữ liệu sang những lời gọi khác.

### 2.2. Field trên aspect instance

Nếu một aspect dùng chung có:

```java
final class UnsafeAspect {
    String currentRequestId;

    void before(String id) {
        currentRequestId = id;
    }
}
```

Đây là **lớp minh họa**, không có `main` hoặc API AOP tự động. Nếu một instance được dùng bởi hai thread, A đặt `currentRequestId="A"`, B đặt `"B"`, rồi A đọc field thì có thể thấy `"B"`. Đây là race condition và rủi ro lẫn ngữ cảnh.

Một aspect dùng chung vẫn phù hợp với **trạng thái thật sự dùng chung**, ví dụ một bộ đếm thread-safe có chính sách rõ ràng; không phù hợp để tự giữ một “request hiện tại” duy nhất cho tất cả thread.

### 2.3. Trạng thái theo thread

Với:

```java
private static final ThreadLocal<String> REQUEST_ID =
        new ThreadLocal<>();
```

Thread A có thể `set("A")`, thread B có thể `set("B")`; mỗi thread đọc lại giá trị của mình. Trên **cùng thread**, nhiều phương thức hoặc advice được gọi liên tiếp có thể đọc chung context ấy.

**Giới hạn:** nếu thread A xử lý request A xong nhưng không `remove()`, rồi xử lý request C, C có thể đọc lại giá trị A. Việc không lẫn với **thread B** không có nghĩa không lẫn giữa **hai tác vụ tuần tự trên A**.

### 2.4. So sánh phạm vi

| Nơi lưu | Đơn vị gắn dữ liệu | Ưu điểm | Giới hạn |
| --- | --- | --- | --- |
| Biến cục bộ của advice | Một lần advice invocation | Tách biệt tự nhiên, cleanup cùng call stack | Không tự chia sẻ qua nhiều join point |
| Tham số/context tường minh | Lượt gọi hoặc logical operation | Dễ nhìn ownership và truyền qua API | Phải chuyền dữ liệu qua các tầng cần dùng |
| Field singleton aspect | Một instance sống dài | Phù hợp trạng thái tổng hợp có đồng bộ | Nguy cơ race và giữ request ngoài ý muốn |
| `ThreadLocal` | Một thread | Thuận tiện cho call stack đồng bộ trên cùng thread | Thread pool tái sử dụng; async chuyển thread không tự thấy |
| Per-target/per-this aspect | Đối tượng tương ứng nếu mô hình hỗ trợ | State theo đối tượng | Không tương đương mỗi request |
| Per-control-flow aspect | Một dynamic flow được chọn nếu mô hình hỗ trợ | Có thể giữ state theo lần đi vào root | Không mặc định lan qua async/thread khác |
| Context theo task/request | Một đơn vị nghiệp vụ được định nghĩa | Phù hợp async khi có propagation | Phải thiết kế capture, restore, ownership, expiry |

**Không có lựa chọn tốt nhất cho mọi trạng thái.** Bắt đầu với biến cục bộ hoặc context tường minh; chỉ đưa trạng thái sang cơ chế sống dài hơn khi thật sự cần các điểm can thiệp khác nhau cùng truy cập.

## 3. Cơ chế `ThreadLocal` trong Java

Một `ThreadLocal<T>` đóng vai trò **khóa** để mỗi thread có một giá trị gắn riêng. Các thao tác cơ bản:

- `get()`: đọc giá trị liên kết với **thread hiện tại**. Với `new ThreadLocal<>()`, chưa gán thì thường nhận `null`.
- `set(value)`: thiết lập giá trị cho thread hiện tại.
- `remove()`: bỏ mapping của thread hiện tại; lần `get()` sau có thể dùng `initialValue()`/supplier nếu có.
- `ThreadLocal.withInitial(supplier)`: tạo giá trị ban đầu khi thread gọi `get()` mà chưa có giá trị, theo hợp đồng Java.

Không nên coi `get()` là tra cứu theo request ID. Nếu hai request đi qua cùng thread ở những thời điểm khác nhau, chúng có thể dùng **cùng entry của ThreadLocal** nếu code không thay/xóa đúng cách.

### 3.1. `withInitial` không biến giá trị thành per-request

Ví dụ:

```java
ThreadLocal<StringBuilder> scratch =
        ThreadLocal.withInitial(StringBuilder::new);
```

Mỗi thread có thể tạo một `StringBuilder` lần đầu, rồi **tái sử dụng cùng builder qua nhiều tác vụ** nếu không `remove()` hoặc reset. Nếu buffer chứa dữ liệu lớn của request trước, nội dung/bộ nhớ có thể tồn tại dài hơn mong muốn.

Đừng suy rằng supplier của `withInitial` chạy lại mỗi khi bắt đầu một logical request. Nó hoạt động theo trạng thái mapping của **thread**.

### 3.2. Weak key không tự bảo đảm value được giải phóng

Cách giữ entry nội bộ của JVM có thể sử dụng tham chiếu yếu tới key `ThreadLocal`, nhưng value trong entry vẫn có thể được giữ **mạnh** cho tới khi được dọn bởi hành vi của map/thread. Điều này khiến việc tạo vô hạn các `ThreadLocal` ngắn hạn trên worker lâu sống có nguy cơ giữ object ngoài ý muốn.

Không nên dựa vào việc key biến mất hoặc gọi `System.gc()` để thay `remove()`. Hành vi dọn phụ thuộc cài đặt và hoạt động tương lai của thread; **cleanup có chủ đích tại biên công việc** đáng tin hơn.

### 3.3. Không đồng nhất `set(null)` với `remove()`

`set(null)` có thể để lại một entry có value null; `remove()` xóa liên kết của thread. Với `withInitial`, kết quả `get()` sau hai thao tác này còn có thể khác nhau: sau `remove()`, supplier có thể chạy lại; sau `set(null)`, `get()` có thể trả null mà không gọi supplier.

Khi muốn dọn sạch context, dùng `remove()` theo hợp đồng. Khi cần phân biệt “không có mapping” và “giá trị null hợp lệ”, cần một biểu diễn trạng thái rõ hoặc một scope abstraction.

## 4. Vì sao nhóm luồng làm lỗi trở nên khó phát hiện?

Một nhóm luồng thường có số worker hữu hạn và tái sử dụng:

```text
Time 1: Worker-1 handles Request-A
            CONTEXT.set(A)
            returns without remove()

Time 2: Worker-1 handles Request-B
            CONTEXT.get()  -> A (stale!)

Time 3: Worker-2 handles Request-C
            CONTEXT.get()  -> null or own value
```

Request B có thể thấy dữ liệu A; Request C lại không có lỗi. Tùy task được scheduler giao cho thread nào, lỗi có thể **không ổn định**, xuất hiện ngẫu nhiên dưới tải.

Một worker thread có thể sống suốt vòng đời ứng dụng, vì vậy dữ liệu stale trong ThreadLocal có thể tiếp tục reachable tới khi được dọn hoặc worker kết thúc. Nếu value giữ request context, user data, credentials, payload, target hay resource, có thể gây **rò rỉ ngữ cảnh và giữ bộ nhớ**.

### 4.1. Cùng một pool, hai loại nguy cơ

**Nguy cơ tính đúng:** tác vụ sau đọc trạng thái cũ và áp sai correlation ID, identity hoặc thông tin kiểm tra quyền.

**Nguy cơ vòng đời:** thread còn sống giữ object mà request trước đã hoàn tất, làm nó không được GC thu gom đúng nhu cầu; nó có thể tiếp tục giữ một đồ thị dữ liệu lớn.

Cùng một lỗi thiếu teardown có thể tạo cả hai. Một heap không tăng mãi không có nghĩa không nguy hiểm: chỉ một identity sai được tái sử dụng cũng có thể gây lỗi bảo mật.

### 4.2. Không sửa bằng cách “luôn dùng nhiều thread hơn”

Tăng kích thước pool không dọn context. Nó chỉ làm lỗi phân bố khác và có thể tăng số entry được giữ. Đổi executor, restart thường xuyên hoặc dùng `System.gc()` cũng không thay thế hợp đồng scope.

Biện pháp gốc là **không giữ state request sau biên kết thúc công việc** và không coi worker là request.

## 5. Scope an toàn: lưu trước, thiết lập, khôi phục

Nếu nhiều advice hoặc phương thức trong **cùng call stack đồng bộ** cần context, hãy mô hình hóa một scope:

```text
previous = CONTEXT.get()
CONTEXT.set(current)
try:
    execute body
finally:
    if no previous:
        CONTEXT.remove()
    else:
        CONTEXT.set(previous)
```

Đây là giả mã. Nó có hai điểm mạnh:

1. Dọn state trên đường **return lẫn throw**.
2. Cho phép **nested scope** trả lại state của outer thay vì luôn xóa mọi thứ.

### 5.1. Vì sao `remove()` vô điều kiện không đủ cho nested scope?

Ví dụ:

```text
Outer starts: context = A
  Inner starts: context = B
  Inner finishes: remove()
Outer continues: context = missing (A lost)
```

Nếu inner **khôi phục A** sau khi kết thúc, outer vẫn có dữ liệu đúng. Cách dọn nên là **khôi phục trạng thái trước đó** theo ownership, không chỉ “xóa mọi context khi tôi xong”.

Nếu code chỉ cho phép một scope top-level và không bao giờ lồng, `remove()` trong `finally` thường đủ; nhưng đừng mở rộng giả định đó sang luồng điều khiển phức tạp.

### 5.2. Scope theo token và `AutoCloseable`

Trong Java có thể tạo một class scope nhận context mới, ghi lại context trước đó, rồi triển khai `AutoCloseable.close()` để khôi phục. Sử dụng `try-with-resources` sẽ giúp đường thoát rõ ràng.

Tuy nhiên, **không được tự gọi `close()` từ thread khác** với thread đã mở nếu scope dùng `ThreadLocal`: như vậy nó sẽ tác động vào ThreadLocal của **thread đang đóng**, không phải thread đã thiết lập trước đó. Một thiết kế mạnh có thể kiểm tra owner thread và thứ tự LIFO khi cho phép nested scope.

### 5.3. Nếu khởi tạo scope thất bại?

Nếu việc kiểm tra quyền, cấp context hoặc bắt đầu tài nguyên ném lỗi **trước khi scope hoạt động**, cần biết có phần nào đã được cấp và cần rollback không. Không `pop()` một stack nếu `push()` chưa thành công; không `remove()` context của parent vì một scope con không mở xong.

Không dùng một `finally` mơ hồ không biết chính xác ownership để dọn những tài nguyên do thành phần khác tạo.

## 6. Per-thread khác per-control-flow và per-invocation

Một số mô hình AOP có chính sách tạo instance **theo dynamic control flow**, ví dụ các thuật ngữ `cflow`/`percflow` trong những ngôn ngữ AOP hỗ trợ chúng. Đây **không phải** từ khóa của Java hay API chung cho mọi cơ chế.

Cần phân biệt:

- **`cflow(P)`** có thể dùng để *lọc join point* nằm trong call flow của điểm P.
- **`percflow(P)`** trong mô hình hỗ trợ có thể định nghĩa **một aspect instance cho mỗi activation** của flow P.
- **`ThreadLocal`** chỉ cung cấp **dữ liệu riêng cho thread đang chạy**; nó không tự tạo per-flow instance khi P được vào hoặc tự xóa khi P thoát.
- **Per-invocation** có thể dùng biến cục bộ trong advice; không cần ThreadLocal nếu chỉ một lần advice dùng state.

Một thread có thể thực hiện nhiều flow **tuần tự**, hoặc có flow **lồng nhau**. Vì vậy `ThreadLocal<Aspect>` đặt một giá trị duy nhất và không hỗ trợ restore có thể làm hỏng per-flow semantics khi inner kết thúc.

### 6.1. Call stack không tự đi qua biên async

```text
Thread-A: request.handle()
             |
             +--> executor.submit(task)
             |
             +--> return

Thread-B:                 task.run()
```

Task chạy trên B có **một call stack khác**. Không có quy tắc chung rằng `cflow` đồng bộ của A tự kéo dài qua B, và một ThreadLocal trong A cũng không tự có mặt trong B.

Nếu một công nghệ AOP hỗ trợ context propagation hoặc asynchronous-aware flow, cần kiểm tra ngữ nghĩa thực tế. Không dùng một ví dụ ThreadLocal để suy rộng thành hành vi của mọi bộ weaving.

### 6.2. Mục tiêu là logical request hay thread?

Một logical request có thể bao gồm nhiều task; cùng một thread có thể chạy nhiều logical request. Nếu trạng thái phải thuộc request thay vì thread, có thể nên dùng **context object tường minh** hoặc một cơ chế truyền context được thiết kế cho async, với biên dọn xác định.

ThreadLocal chỉ là **một phương tiện lưu dữ liệu trên đoạn đồng bộ trong thread**, không phải câu trả lời tự động cho lifecycle xuyên task.

## 7. `InheritableThreadLocal` và giới hạn với thread pool

Java có `InheritableThreadLocal<T>` cho phép một thread mới tạo kế thừa giá trị từ parent tại thời điểm tạo thread, theo hợp đồng tương ứng. Điều này **không đồng nghĩa** tự truyền context sang mọi task được submit cho thread pool.

Vì sao?

1. Worker trong pool có thể đã được tạo **trước khi** request có context.
2. Một worker có thể được tạo trong bối cảnh request A rồi được tái sử dụng cho B; giá trị inherited khi tạo thread không tự đổi theo từng `submit`.
3. Nếu giá trị kế thừa là một object mutable, các thread có thể chia sẻ tham chiếu tới cùng object theo mặc định hoặc theo quy tắc `childValue` của triển khai.
4. Dữ liệu nhạy cảm từ parent có thể vô tình tồn tại trên thread con lâu hơn dự kiến.

Vì thế `InheritableThreadLocal` không phải giải pháp chung cho truyền request context vào executor. Với nhóm luồng sống lâu, cần **gói công việc với context capture/restore tại thời điểm submit/run**, hoặc truyền dữ liệu rõ qua tham số.

### 7.1. Capture context khi nào?

Có ít nhất hai thời điểm:

- **Submit time:** caller tạo task khi đang giữ correlation ID; có thể snapshot ID ở thời điểm này.
- **Execution time:** worker nhận task về sau, có thể đã chạy công việc khác; đọc ThreadLocal của worker không nói gì về caller đã submit.

Một propagation wrapper đúng thường capture **metadata cần thiết từ caller tại submit**, sau đó mở scope **trong worker khi task chạy** và khôi phục state cũ khi task kết thúc.

Nếu một task được submit sau khi request scope đã đóng, việc capture lúc đó có thể ra dữ liệu khác; không nên âm thầm đoán request “gần nhất”.

### 7.2. Tránh capture toàn bộ đối tượng request

Không phải mọi thứ trong context đều nên truyền:

- Truyền **correlation ID bất biến** có thể đủ cho tracing.
- Truyền đối tượng identity/authorization phải dựa trên hợp đồng tin cậy và thời hạn hiệu lực.
- Truyền connection, lock, mutable transaction hoặc request-scoped resource sang task chạy muộn có thể làm nó bị dùng sau khi đóng.
- Truyền `ThreadLocal` value là cùng một mutable object sang nhiều worker có thể tạo race condition; việc value nằm trong ThreadLocal ở mỗi worker **không tự sao chép sâu object**.

Thiết kế propagation phải nói rõ **trường dữ liệu nào được copy**, quyền sở hữu ra sao và khi nào bị xóa.

## 8. Mô hình AOP qua proxy: trạng thái được đặt ở đâu?

Một JDK dynamic proxy có thể chặn lời gọi phương thức **qua giao diện của proxy**. Giả sử handler tạo scope:

```text
caller -> proxy handler
           |
           +--> open ThreadLocal scope
           +--> invoke target
           +--> close scope in finally
           |
        result / error
```

Các phương thức chạy đồng bộ trên cùng thread bên trong target có thể đọc ThreadLocal. Tuy nhiên:

- Caller giữ **đối tượng gốc** và gọi trực tiếp có thể bypass proxy và scope.
- **Self-invocation** `this.otherMethod()` trong target có thể không quay lại proxy; nếu scope được mở ở entry bên ngoài thì method bên trong vẫn có thể đọc context, nhưng **advice riêng của method bên trong** có thể không được kích hoạt.
- JDK proxy không tự chặn field access, constructor, private method hoặc mọi loại join point.
- Handler phải phân biệt những phương thức thuộc pointcut và tránh áp dụng scope nhầm cho `Object` methods hoặc overload khác.
- Nếu một interceptor ngoài short-circuit trước khi handler/guard cần thiết chạy, scope có thể không được mở.
- Nếu around advice gọi tiếp nhiều lần để retry, phải xác định scope mở **mỗi attempt** hay cho cả logical request.

### 8.1. Thứ tự context và retry

```text
ContextScope(Retry(Target))
```

Có thể mở một context cho logical request và giữ nó qua nhiều attempts.

```text
Retry(ContextScope(Target))
```

Có thể mở context mới cho mỗi attempt nếu phần đuôi của chain được chạy lại. Mỗi phương án có ý nghĩa khác về tracing span, timing, identity snapshot và cleanup.

Không mặc định thứ tự trong một bộ AOP; phải kiểm tra precedence của cơ chế sử dụng và **lời gọi thực tế tới `proceed`**.

### 8.2. Cleanup có được bảo đảm khi target throw?

Nếu handler dùng:

```text
scope = open()
try:
    return invokeTarget()
finally:
    scope.close()
```

thì scope đóng trên đường return/throw thông thường. Nhưng nếu `open()` đã cấp một phần tài nguyên rồi lỗi trước `try`, phải có chiến lược cleanup cho cấp phát từng phần. Nếu `close()` tự ném lỗi, nó có thể **che lỗi nghiệp vụ gốc**; hợp đồng phải quy định cách xử lý.

## 9. Ví dụ Java 17 chạy độc lập: proxy, scope lồng nhau, thread pool và propagation

Chương trình sau minh họa bốn tình huống bằng **JDK thuần**:

1. Proxy mở scope theo ID của một lần gọi, rồi khôi phục context trước đó kể cả khi target ném ngoại lệ.
2. Nested scope `outer` bao một lời gọi proxy `inner`: khi lời gọi trong kết thúc, trạng thái của outer được khôi phục.
3. Một `newSingleThreadExecutor()` chạy hai task tuần tự trên **cùng worker**: task đầu cố ý để lại `ThreadLocal`, task sau nhìn thấy giá trị stale.
4. Caller giữ context `parent` và submit task: worker **không tự nhận** context ấy; một wrapper tường minh đưa correlation ID sang worker rồi dọn khi task hoàn tất.

Ví dụ chỉ minh họa dữ liệu tương quan là chuỗi, không truyền identity bảo mật hoặc transaction. Nó không phải một hệ AOP tự động biến đổi bytecode.

Lưu toàn bộ thành `ThreadStateAopDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadStateAopDemo {
    private static final ThreadLocal<String> CONTEXT =
            new ThreadLocal<>();

    static String current() {
        String id = CONTEXT.get();
        return id == null ? "none" : id;
    }

    static final class Scope implements AutoCloseable {
        private final String previous;
        private final Thread owner;
        private boolean closed;

        Scope(String id) {
            previous = CONTEXT.get();
            owner = Thread.currentThread();
            CONTEXT.set(id);
        }

        @Override
        public void close() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("wrong owner thread");
            }
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }

    interface WorkPort {
        String handle(String id);
    }

    static final class WorkService implements WorkPort {
        @Override
        public String handle(String id) {
            System.out.println("TARGET " + id + " context=" + current());
            if ("bad".equals(id)) {
                throw new IllegalArgumentException("bad input");
            }
            return "OK:" + id;
        }
    }

    static Object callTarget(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static WorkPort wrap(WorkPort target) {
        return (WorkPort) Proxy.newProxyInstance(
                WorkPort.class.getClassLoader(),
                new Class<?>[] { WorkPort.class },
                (proxy, method, args) -> {
                    if (!method.getName().equals("handle")
                            || method.getParameterCount() != 1
                            || method.getParameterTypes()[0] != String.class) {
                        return callTarget(method, target, args);
                    }

                    String id = (String) args[0];
                    try (Scope scope = new Scope(id)) {
                        System.out.println("ENTER " + id
                                + " context=" + current());
                        try {
                            Object result = callTarget(method, target, args);
                            System.out.println("RETURN " + id
                                    + " context=" + current());
                            return result;
                        } catch (Throwable error) {
                            System.out.println("THROW " + id + " "
                                    + error.getClass().getSimpleName());
                            throw error;
                        }
                    }
                });
    }

    public static void main(String[] args) throws Exception {
        WorkPort proxy = wrap(new WorkService());

        try (Scope outer = new Scope("outer")) {
            System.out.println("OUTER before " + current());
            System.out.println("OUTER result " + proxy.handle("inner"));
            System.out.println("OUTER after " + current());
        }
        System.out.println("MAIN after outer=" + current());

        try {
            proxy.handle("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }
        System.out.println("MAIN after error=" + current());

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> {
                CONTEXT.set("stale");
                System.out.println("UNSAFE set " + current());
                // Cố ý không remove() để tái hiện lỗi.
            }).get();

            pool.submit(() -> {
                System.out.println("REUSED saw " + current());
                CONTEXT.remove();
            }).get();

            try (Scope parent = new Scope("parent")) {
                System.out.println("PARENT " + current());

                pool.submit(() ->
                        System.out.println("ASYNC default " + current())
                ).get();

                String captured = current();
                pool.submit(() -> {
                    try (Scope transfer = new Scope(captured)) {
                        System.out.println("ASYNC explicit " + current());
                    }
                    System.out.println("ASYNC cleaned " + current());
                }).get();

                System.out.println("PARENT after tasks " + current());
            }
        } finally {
            pool.shutdown();
        }

        System.out.println("MAIN final " + current());
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 ThreadStateAopDemo.java
java ThreadStateAopDemo
```

**Stdout dự kiến chính xác** khi chạy chương trình này:

```text
OUTER before outer
ENTER inner context=inner
TARGET inner context=inner
RETURN inner context=inner
OUTER result OK:inner
OUTER after outer
MAIN after outer=none
ENTER bad context=bad
TARGET bad context=bad
THROW bad IllegalArgumentException
CALLER bad input
MAIN after error=none
UNSAFE set stale
REUSED saw stale
PARENT parent
ASYNC default none
ASYNC explicit parent
ASYNC cleaned none
PARENT after tasks parent
MAIN final none
```

### 9.1. Vì sao `OUTER after outer` đúng?

`main` mở `Scope("outer")`, sau đó gọi `proxy.handle("inner")`. Proxy đọc `previous="outer"`, thiết lập `"inner"`, gọi target rồi đóng scope. `Scope.close()` **khôi phục `"outer"`**, không xóa toàn bộ state của main thread.

Nếu handler chỉ gọi `CONTEXT.remove()` khi `inner` kết thúc, dòng `OUTER after` sẽ sai: scope cha vẫn đang chạy nhưng dữ liệu đã bị mất.

### 9.2. Vì sao lỗi `bad` không để lại context?

`WorkService.handle("bad")` ném `IllegalArgumentException`. `callTarget` bóc `InvocationTargetException` để giữ nguyên lỗi gốc; handler ghi `THROW` rồi truyền lỗi. Dù lỗi xảy ra, `try-with-resources` vẫn gọi `Scope.close()` cho scope đã mở.

Vì trước `bad` main thread không có context, close gọi `remove()`, do đó `MAIN after error=none`.

### 9.3. Vì sao `REUSED saw stale` xuất hiện?

`Executors.newSingleThreadExecutor()` tuần tự thực hiện các task trên một worker (trừ trường hợp cần thay worker do lỗi). Trong ví dụ các task hoàn thành bình thường, và `Future.get()` giúp main đợi task trước xong mới gửi/kiểm tra tiếp.

Task thứ nhất đặt `"stale"` và cố ý **không dọn**. Task tiếp theo chạy trên worker đó và thấy giá trị cũ. Nó gọi `remove()` để những task sau bắt đầu sạch.

Phần “UNSAFE” là đoạn **cố tình sai để học**, không phải mẫu code nên dùng trong hệ thật.

### 9.4. Vì sao `ASYNC default none`?

Main thread đang giữ `"parent"` nhưng worker thread không tự thừa hưởng `ThreadLocal` thông thường. Task async đọc ThreadLocal của **chính worker**. Worker đã được dọn từ task trước, nên nó thấy `none`.

### 9.5. Vì sao explicit propagation không làm lẫn context?

Main thread capture **chuỗi correlation ID** `"parent"`, submit một task sử dụng `new Scope(captured)` **trên worker**. Worker thấy `parent` trong scope; khi task kết thúc, scope khôi phục previous của worker (ở ví dụ là null), rồi `ASYNC cleaned none`.

Main thread vẫn ở scope parent, nên `PARENT after tasks parent`; sau khi parent close, `MAIN final none`.

**Điều quan trọng:** wrapper không chuyển trực tiếp toàn bộ ThreadLocalMap và không chia sẻ một object request mutable. Nó truyền **một giá trị đơn giản, được lựa chọn có chủ đích**.

### 9.6. Vì sao `Scope` giữ `owner`?

Một `Scope` dựa trên ThreadLocal chỉ được đóng ở thread đã mở. Nếu đóng trên thread khác, nó sẽ restore dữ liệu trong ThreadLocal của **thread đang gọi close**, không phải của owner. Kiểm tra `Thread.currentThread() != owner` giúp phát hiện việc dùng sai mô hình trong ví dụ.

Ví dụ vẫn không có stack token kiểm tra thứ tự LIFO giữa nhiều scope; cần sử dụng `try-with-resources` đúng cấu trúc. Một abstraction sản xuất có thể cần chống đóng sai thứ tự, khởi tạo/đóng lặp, hủy task và logging lỗi cleanup có hệ thống.

### 9.7. Giới hạn của chương trình

- JDK dynamic proxy chỉ chặn lời gọi đi qua **giao diện `WorkPort`**; không weave bytecode của `WorkService`.
- Handler lọc phương thức `handle(String)`; lời gọi trực tiếp vào target gốc sẽ không đi qua handler.
- `Scope` là mô hình thủ công, không có bộ AOP tự lựa chọn pointcut theo annotation, field access hay constructor.
- Ví dụ chạy tuần tự trong mỗi task; không minh họa một shared mutable context được truy cập đồng thời từ hai thread.
- Correlation ID là chuỗi mô phỏng; không phải giấy phép kiểm tra quyền hay chứng nhận identity.
- `ThreadLocal` dùng giá trị null để biểu thị “không có scope” trong bản học tập; nếu null có nghĩa nghiệp vụ hợp lệ, cần type/marker rõ hơn.
- `pool.shutdown()` sau các `get()` hoàn tất giúp kết thúc executor; ứng dụng thật có lifecycle executor riêng.
- Không được kết luận mọi `ThreadLocal` đều gây leak: leak xuất hiện khi **ownership/lifetime/teardown sai**, không phải chỉ vì API tồn tại.

## 10. Khi có nhiều executor, callback và parallel processing

Không phải mọi tác vụ chạy trên thread nhận request ban đầu:

- `ExecutorService.submit`: task có thể chạy trên worker khác, ngay cả khi code được gọi bên trong một method đang có context.
- `CompletableFuture.supplyAsync`: phần việc có thể được lên lịch trên executor riêng hoặc common pool, tùy overload.
- Callback không có ràng buộc về thread nhất định theo mọi API; không được giả định thread của producer luôn chạy consumer.
- `parallelStream` có thể phân chia công việc ra nhiều worker; ThreadLocal của thread gọi không phải state tường minh cho toàn bộ tập.
- Event loop/reactive-style processing có thể xen kẽ nhiều logical requests trên ít thread; lưu request vào ThreadLocal mà không ràng buộc callback dễ dẫn đến sai context.
- Timer, scheduler hoặc message listener có thể chạy trên thread quản lý riêng.

Bất cứ khi nào luồng điều khiển **có thể đổi thread**, phải xác định rõ cơ chế capture/restore; nếu không biết task đang chạy ở đâu, không dùng `ThreadLocal.get()` làm nguồn sự thật của logical request.

### 10.1. `CompletableFuture` và thời điểm kết thúc

Một method trả `CompletableFuture<Result>` có thể trả về **trước khi** công việc hoàn tất. Nếu around advice mở ThreadLocal ở method đó và đóng scope ngay sau return, worker hoàn thành future về sau không còn call stack đồng bộ của method.

Nếu advice muốn ghi metric **thời gian hoàn thành tác vụ**, phải theo dõi completion stage hoặc cơ chế tương ứng theo hợp đồng, không chỉ đo thời gian method trả future.

### 10.2. Có nên truyền ThreadLocal qua mọi task?

Không nhất thiết. Truyền quá nhiều context có thể:

- Giữ request/session/credential ngoài vòng đời;
- Đưa dữ liệu nhạy cảm tới worker không cần dùng;
- Kéo dài tài nguyên đã đóng;
- Gây race nếu giá trị là object mutable dùng chung;
- Làm hành vi khó tái hiện khi một task chạy rất muộn;
- Khó dọn đúng nếu worker bị hủy/cancel giữa chừng.

Chỉ capture **những trường cần thiết**, ưu tiên các giá trị bất biến, xác định thời hạn và người sở hữu. Khi cần đầy đủ transaction context hoặc authorization, dùng giao thức/hợp đồng rõ, không copy tùy tiện.

## 11. ThreadLocal và an toàn bộ nhớ

Một worker sống lâu có thể giữ `ThreadLocal` value qua nhiều lượt xử lý. Nếu value tham chiếu:

```text
Thread pool worker (long-lived)
     |
     v
ThreadLocal value
     |
     v
RequestContext
     |
     +--> Large payload
     +--> Session data
     +--> Target object
     +--> Resource handle
```

thì các object liên quan có thể còn reachable ngoài thời gian cần thiết. Đây có thể là **unintended retention**, dù heap không nhất thiết tăng mãi: một field giữ request gần nhất có thể chỉ giữ một đồ thị lớn, còn một map không giới hạn có thể giữ hàng nghìn đồ thị.

Để điều tra:

1. Tạo workload tuần tự nhiều task trên cùng pool.
2. Kiểm tra rằng sau mỗi task, **context cục bộ của task không còn tồn tại**.
3. Quan sát số lượng object còn sống và paths to GC roots bằng công cụ heap analysis khi cần.
4. Tìm ThreadLocalMap, callback, registry hoặc cache đang giữ value.
5. Sửa tại nơi **thiết lập scope** hoặc nơi capture object, không chỉ gọi `System.gc()`.

Một reference cycle không tự là memory leak trong tracing GC nếu không còn reachable từ root. Vấn đề là đường tham chiếu mạnh từ worker sống lâu tới dữ liệu đã hết vòng đời.

### 11.1. `ThreadLocal.remove()` có giải quyết mọi tài nguyên không?

Không. Nó bỏ liên kết giá trị trong ThreadLocal của thread hiện tại. Nếu value chứa một socket/file/transaction còn mở, chỉ remove mapping **không tự đóng tài nguyên**.

Hợp đồng phải nêu ai thực hiện `close()`, `rollback()`, `unlock()` hoặc hủy registration nếu cần. Thứ tự cleanup cũng quan trọng: tài nguyên phải được đóng đúng owner và không còn được task async dùng.

### 11.2. Scope kéo dài quá lâu

Nếu code mở một scope bao quanh toàn bộ vòng lặp worker:

```text
openScope(requestA)
while (workerAlive):
    processNextTask()
closeScope()
```

scope đã bị đặt sai ranh giới. Mỗi task B/C sẽ vẫn nhìn thấy dữ liệu A. Đối với dữ liệu per-task, cần scope riêng **cho mỗi lượt thực thi**, không phải scope theo vòng đời worker.

## 12. ThreadLocal không tự bảo đảm an toàn đồng thời

Dù từng thread có giá trị riêng, **giá trị đó có thể là cùng một object** nếu chương trình đưa cùng tham chiếu vào nhiều thread.

Ví dụ:

```text
SharedMutableContext shared = ...
Thread A: TL.set(shared)
Thread B: TL.set(shared)
```

Đây là giả mã. A và B đọc `TL.get()` trên thread của mình, nhưng cả hai đều nhận **cùng object `shared`**. Các phép ghi vào field mutable của object đó vẫn cần đồng bộ hoặc một thiết kế sở hữu duy nhất.

`ThreadLocal` bảo vệ **mapping theo thread**, không tự bảo vệ **nội dung mutable** được các thread chia sẻ bằng tham chiếu.

### 12.1. Aspect singleton và `ThreadLocal`

Một aspect instance dùng chung có thể giữ field:

```java
private final ThreadLocal<String> requestId =
        new ThreadLocal<>();
```

Các thread có entry riêng, nhưng cần làm rõ:

- Field `ThreadLocal` sống theo aspect instance và có thể rất lâu;
- Mỗi thread được set phải được restore/remove theo scope;
- Không được trao nhầm context của thread sang async worker;
- Nếu aspect tạo một `ThreadLocal` mới cho **mỗi invocation** và không cleanup, có thể tăng số key/value rác trên worker;
- Nếu nhiều aspect cần cùng context, nên có abstraction ownership chung, không để mỗi aspect tự remove state do aspect khác đặt.

Một lựa chọn kiến trúc là một **scope/interceptor ngoài cùng** sở hữu việc thiết lập/dọn context, những advice bên trong chỉ **đọc** thông qua API có hợp đồng rõ. Việc ghi hoặc thay đổi context nên giới hạn cho những thành phần được phép.

### 12.2. Dữ liệu bảo mật tuyệt đối không lấy từ ThreadLocal stale

Nếu `currentUser` hoặc `authorized` được để lại từ request trước, request sau có thể được xử lý dưới danh tính/quyền sai. Không nên coi một cờ trong ThreadLocal là quyết định bảo mật đáng tin nếu nó có thể được set bởi luồng không xác thực hoặc có đường bypass.

Với chính sách bắt buộc, xác thực/ủy quyền phải được bảo vệ tại ranh giới đáng tin cậy, dùng dữ liệu có nguồn gốc và thời hạn rõ, và thất bại an toàn khi context thiếu/không hợp lệ.

## 13. Context propagation và owner

Một wrapper propagation có thể thực hiện bốn bước:

```text
Caller at submit:
    captured = immutableSnapshot(allowedContextFields)

Worker at run:
    previous = context.current()
    context.install(captured)
    try:
        task.run()
    finally:
        context.restore(previous)
```

Đây là giả mã; không phải API chuẩn của AOP. Những câu hỏi phải có đáp án:

- **Capture khi nào?** lúc tạo task hay lúc submit?
- **Điều gì được copy?** correlation ID, tenant, identity hay object mutable?
- **Điều gì không được copy?** session lớn, stream, lock, open transaction?
- **Thời hạn là gì?** task chạy sau khi request đã kết thúc có còn hợp lệ không?
- **Ai dọn?** wrapper, scheduler hay task body?
- **Scope lồng nhau?** restore previous hay remove?
- **Nhiều task con?** chia sẻ parent metadata và độc lập state từng task ra sao?
- **Task bị cancel trước khi run?** có cần hủy callback/retention của snapshot không?
- **Task lỗi?** wrapper có cleanup trong `finally` không?
- **Worker đã có context của một task trước?** phải khôi phục context trước đó theo ownership.

### 13.1. Không chuyển một mutable request object nguyên vẹn

Nếu một `RequestContext` chứa danh tính, thông tin giao dịch, payload và connection rồi được capture trong `Runnable`, nó có thể ở lại trong queue cho tới khi task chạy hoặc bị hủy. Nếu queue trì hoãn, payload bị giữ lâu. Nếu task được phân nhánh, mutable data có thể bị dùng đồng thời.

Chỉ dùng context tối thiểu; đặt chính sách giới hạn queue/deadline và giải phóng những registration/closure khi task không còn cần.

### 13.2. Task scope khác thread scope

Ở thread pool, một worker có thể thực thi nhiều task lần lượt. Một wrapper đúng phải mở context cho **mỗi lần task chạy**, không set một lần khi worker được tạo.

Một task có thể có nested subtasks; nếu chạy trực tiếp trên cùng thread, scope nested phải được khôi phục; nếu chạy thread khác, cần truyền context riêng theo hợp đồng. Không dùng các khái niệm này thay cho nhau.

## 14. AOP per-control-flow và `cflow` cần được diễn giải riêng

Một số mô hình AOP có pointcut theo **dynamic control flow** và cơ chế tạo aspect instance theo flow. Điều đó khác với lưu một giá trị trong ThreadLocal thuần.

```text
Root.handle(A)                 -> Flow F1
   |
   +-- child.method()          -> advice dùng flow F1
   |
   +-- Root.handle(B)          -> có thể tạo nested F2
         |
         +-- child.method()    -> advice theo F2
   |
   +-- child.method()          -> trở lại F1
```

Nếu công nghệ hỗ trợ **per-control-flow instantiation**, chính sách xác định khi nào có instance F1/F2 và advice thuộc instance nào. `ThreadLocal` có thể được một công cụ sử dụng bên trong, nhưng **không phải bằng chứng mọi công nghệ triển khai per-flow bằng ThreadLocal**.

### 14.1. Truyền context không tự kéo dài một dynamic control flow

Một wrapper chuyển trace ID từ thread A sang B chỉ chuyển dữ liệu đã chọn; nó **không** tự làm cho call stack ở B trở thành phần bên dưới method execution root trên A trong ngữ nghĩa của một pointcut `cflow` truyền thống.

Nếu cần theo dõi logical operation xuyên async, phải dùng mô hình propagation hỗ trợ, không chỉ tạo một chuỗi giống nhau ở hai thread rồi coi đó là native `cflow`.

### 14.2. Scope per-thread vs per-invocation với lời gọi lồng nhau

Nếu một advice đọc ThreadLocal trong scope outer rồi nested advice ghi một ID khác, nó phải khôi phục outer. Nhưng một aspect per-invocation có thể giữ mỗi ID trong **biến cục bộ** của lời gọi và không cần ThreadLocal. Chỉ chọn per-thread khi có nhiều thành phần **trên cùng thread** cần truy cập context mà không tiện chuyền tham số.

## 15. Những sai lầm dễ gặp

| Sai lầm | Hậu quả | Cách tránh |
| --- | --- | --- |
| Coi ThreadLocal là request scope | Context stale ở worker tái sử dụng | Tách scope theo từng task/request, teardown rõ |
| Quên `remove()` sau `set()` | Lẫn dữ liệu và giữ object | `finally`/try-with-resources |
| Inner scope luôn gọi `remove()` | Mất outer context | Lưu và khôi phục previous |
| Dùng `set(null)` như tương đương `remove()` | Để lại entry/khác `withInitial` | Dùng remove khi muốn xóa mapping |
| Dùng `InheritableThreadLocal` để truyền request qua pool | Giá trị kế thừa lúc tạo worker, không theo submit | Snapshot + scope trong task |
| Cho rằng ThreadLocal bảo vệ mutable value | Hai thread vẫn có thể chia sẻ cùng object | Immutable snapshot hoặc đồng bộ rõ |
| Lưu identity trong field singleton aspect | Race, lẫn quyền, retention | Context per-invocation đáng tin |
| Mở scope khi tạo worker thay vì khi chạy task | Task sau thừa hưởng context cũ | Scope trên từng `Runnable/Callable` |
| Tạo ThreadLocal mới liên tục và bỏ quên | Stale entries/value giữ bộ nhớ | Tái sử dụng key có owner, cleanup đầy đủ |
| Thay ThreadLocal bằng static global | Lẫn giữa các thread | Thiết kế theo ownership, không dùng state chung tùy tiện |
| Propagate cả request mutable sang async | Race, giữ payload/tài nguyên | Chỉ dữ liệu tối thiểu, TTL/ownership rõ |
| Đóng scope ở thread khác | Khôi phục sai ThreadLocal | Gắn owner thread và đóng đúng thread |
| Tưởng proxy tự bao phủ self-invocation | Advice mong đợi không chạy | Kiểm tra đường gọi/proxy/weaver thực |
| Chỉ test đường thành công | Rò context khi throw | Test return, throw, cancel/timeout |
| Chỉ quan sát tổng heap | Khó thấy retention hoặc lẫn identity | Kiểm tra context thực tế và path to GC root |
| Tưởng cùng correlation ID nghĩa là cùng request hợp lệ | Dữ liệu stale/tampering | Xác minh nguồn gốc, phạm vi và thời hạn |
| Dùng future return làm completion | Dọn quá sớm hoặc đo sai | Theo dõi completion stage thực tế |

## 16. Ma trận kiểm thử gợi ý

| Tình huống | Điều cần chứng minh |
| --- | --- |
| Lời gọi qua proxy trên thread chính | Scope mở, target thấy context, scope đóng |
| Target trả về bình thường | Result giữ nguyên, ThreadLocal restore |
| Target ném ngoại lệ | Lỗi gốc được truyền, context không rò |
| Nested scope | Inner tạm thay state; outer được khôi phục |
| Hai request tuần tự trên một worker | Request sau không nhìn thấy state cũ |
| Nhiều worker đồng thời | Mỗi thread có mapping riêng, không dùng chung mutable value trái hợp đồng |
| Cố ý quên cleanup | Regression test phát hiện giá trị stale |
| Worker task không có propagation | Không mặc định thấy context của caller |
| Propagation được bật rõ | Worker thấy snapshot được cấp, sau task sạch |
| Caller sau submit | Scope gốc không bị worker sửa |
| Dùng `InheritableThreadLocal` với pool | Không dựa vào inheritance per submit |
| `withInitial` + `remove()` | Giá trị được khởi tạo lại theo hợp đồng |
| Async completion sau return | Không dùng context đã hết hạn hoặc resource đóng |
| Task bị cancel/timeout | Snapshot/callback và cleanup theo lifecycle thực |
| Lời gọi trực tiếp target | Không tự nhận advice của proxy |
| Self-invocation | Hiểu điểm nào bypass interception |
| Nhiều overload cùng tên | Bộ lọc chữ ký không chọn sai |
| Dữ liệu quyền bắt buộc | Không chấp nhận context thiếu/stale |
| Hoán đổi thứ tự retry/context scope | Phạm vi per-attempt/per-request đúng |
| Heap sau workload | Không giữ request graph qua worker sống lâu trái ý định |

### 16.1. Test nên kiểm tra giá trị, không dựa vào timing GC

Một test rõ ràng với `newSingleThreadExecutor()` có thể đặt giá trị ở task thứ nhất và đọc nó ở task thứ hai. Dùng `Future.get()` để buộc thứ tự, không cần `Thread.sleep()`.

Đối với memory leak, test hành vi có thể kiểm tra `ThreadLocal` đã được dọn và registry/callback đã được tháo; heap dump/path to GC root hữu ích khi phân tích retention, nhưng **không** dùng việc gọi `System.gc()` rồi chờ object biến mất trong khoảng thời gian cứng làm tiêu chí duy nhất.

### 16.2. Test giá trị trước và sau nested scope

Một bộ test tối thiểu:

```text
Set A
  scope B:
    assert current == B
  close B
  assert current == A
close A
assert current == none
```

Đây là giả mã. Cần bổ sung đường throw: nếu scope B ném lỗi, trạng thái A vẫn được khôi phục, lỗi gốc vẫn tới caller và không có context B còn sống ở worker.

## 17. Tài nguyên, bảo mật và vận hành

### 17.1. Không lưu đối tượng nhạy cảm nếu chỉ cần correlation ID

Một aspect tracing có thể chỉ cần correlation ID đã chuẩn hóa. Lưu cả token đăng nhập, `HttpRequest`, credential, payload hay connection trong ThreadLocal sẽ mở rộng đồ thị retention và rủi ro heap dump/log.

Metadata quan sát nên được allowlist, hạn chế độ dài, có TTL/owner theo logical task và được xử lý theo chính sách riêng tư. Correlation ID không phải chứng cứ ủy quyền.

### 17.2. Readiness và cleanup lỗi

Nếu cơ chế mở scope bắt buộc không thể hoạt động, cần có chính sách có được đi tiếp target hay không. Đặc biệt với guard bảo mật, không thể âm thầm bỏ qua khi hệ thống AOP hoặc proxy không được cấu hình đúng.

Nếu thao tác cleanup có lỗi, cần phân loại: lỗi observer có thể được cô lập để không che ngoại lệ target, còn lỗi giải phóng transaction/lock có thể tạo trạng thái chưa chắc chắn. Không có một `catch(Throwable)` đúng cho mọi tình huống.

### 17.3. Đo trạng thái theo đơn vị phù hợp

Đừng dùng “số thread hiện có” làm số request đang xử lý. Một worker có thể tuần tự làm nhiều task; một task có thể fan out sang nhiều thread. Các chỉ số hữu ích thường là **số scope được mở/đóng**, số task thiếu context bắt buộc, số lần stale context bị phát hiện, số propagation bị từ chối và số resource còn mở.

Không ghi toàn bộ ThreadLocal values để debug: nó có thể chứa dữ liệu nhạy cảm và bản ghi quá lớn.

## 18. Bài tập vận dụng có đáp án

### Bài 1 — Hai tác vụ, một worker

Task A đặt `CURRENT.set("A")` rồi kết thúc. Task B được pool giao cho đúng worker ấy và gọi `CURRENT.get()`. Có chắc thấy null không?

**Đáp án:** Không. Nếu A không remove/restore, B có thể thấy `"A"`. Thread pool tái sử dụng thread, trong khi ThreadLocal gắn với thread chứ không gắn với mỗi task.

### Bài 2 — Nested scope

Outer đặt context `A`. Inner đặt `B` rồi đóng. Nếu inner chỉ gọi `remove()`, điều gì sai?

**Đáp án:** State A của outer bị mất. Cần lưu previous trước khi set B, sau khi inner kết thúc phải restore A; chỉ remove nếu trước đó thật sự không có scope.

### Bài 3 — Worker async

Main thread giữ correlation ID `Q-7`, rồi submit task sang `ExecutorService`. ThreadLocal thông thường có tự xuất hiện trong worker không?

**Đáp án:** Không. Phải truyền snapshot dữ liệu cần thiết và mở scope trên worker, sau đó cleanup/restore khi task kết thúc.

### Bài 4 — InheritableThreadLocal

Một worker của thread pool được tạo trước request. Request A sau đó `set("A")` vào `InheritableThreadLocal` trên thread gọi và submit task. Worker có chắc thấy A không?

**Đáp án:** Không. Kế thừa giá trị gắn với lúc **thread được tạo**, không tự cập nhật theo mỗi task submit. Pool tái sử dụng làm giả định ấy sai.

### Bài 5 — Shared object

Thread A và B đều `TL.set(sharedMutableContext)`. Mỗi thread có mapping riêng. Có cần bảo vệ race khi sửa field trong `sharedMutableContext` không?

**Đáp án:** Có. Mapping riêng không tạo bản sao của object. Cả hai vẫn giữ cùng tham chiếu và có thể ghi/đọc đè lên nhau.

### Bài 6 — Lỗi nghiệp vụ

Around advice mở scope, target ném lỗi. Nếu dọn context chỉ ở nhánh after-returning, hậu quả?

**Đáp án:** Nhánh throw không gọi after-returning, nên context có thể bị giữ trong worker. Dùng finally/teardown tương ứng và truyền lỗi gốc.

### Bài 7 — Dữ liệu quyền

Một ThreadLocal còn giữ `authorized=true` từ task trước. Có được xem nó là quyền của task hiện tại không?

**Đáp án:** Không. Đây là dữ liệu stale không có nguồn xác thực hợp lệ cho task hiện tại. Bảo vệ effect bằng guard đáng tin, phân biệt thiếu/không hợp lệ, và dọn context theo scope.

### Bài 8 — Per-flow hay per-thread?

Một thread xử lý hai lời gọi root riêng biệt. Nếu muốn aspect instance riêng cho từng root, chỉ dùng một `ThreadLocal<Aspect>` set một lần khi worker sinh ra có đủ không?

**Đáp án:** Không. Phải tạo/kích hoạt/khôi phục instance theo từng **root activation**, không theo cả vòng đời worker. Nếu công nghệ hỗ trợ per-control-flow, dùng hợp đồng đó; nếu không, tự quản lý context scope rõ ràng.

### Bài 9 — Dùng `set(null)`

Một `ThreadLocal` tạo bằng `withInitial`. Sau `set(null)`, gọi `get()` có giống sau `remove()` rồi `get()` không?

**Đáp án:** Không nhất thiết. `set(null)` để value null; `remove()` bỏ mapping và lần `get()` sau có thể gọi supplier để tạo giá trị mới.

### Bài 10 — Proxy bypass

Một aspect qua JDK proxy mở ThreadLocal khi `WorkPort.handle` được gọi, nhưng người dùng gọi trực tiếp `new WorkService().handle()`. Có scope của proxy không?

**Đáp án:** Không. Đường gọi trực tiếp target bỏ qua handler của proxy. Cần kiểm tra đường cấp tham chiếu và loại join point được cơ chế hỗ trợ; không dùng ThreadLocal như bằng chứng mọi lời gọi đã được can thiệp.

### Bài 11 — Khối async trả future

Một advice mở scope, gọi method trả `CompletableFuture`, rồi đóng scope ở after-returning. Tác vụ future chạy muộn trên worker có nằm trong scope đó không?

**Đáp án:** Không thể mặc định. Method đã trả future nhưng tác vụ chưa chắc hoàn thành; cần context propagation và lifecycle theo completion/worker phù hợp.

## 19. Ghi nhớ

- **Trạng thái theo thread không đồng nghĩa trạng thái theo request, flow hay invocation.** Thread có thể phục vụ nhiều task, còn một request có thể dùng nhiều thread.
- **`ThreadLocal` tách mapping theo thread**, không tự deep-copy value mutable và không tự truyền giá trị sang executor.
- **Nhóm luồng tái sử dụng worker**: quên restore/remove gây context stale, lẫn dữ liệu bảo mật và giữ object ngoài ý muốn.
- **Nested scope phải khôi phục trạng thái cha**; cleanup sau return/throw là một phần hợp đồng của advice.
- **`InheritableThreadLocal` không giải quyết propagation per-task của thread pool** và có nguy cơ copy tham chiếu mutable sai phạm vi.
- Khi có async, **capture dữ liệu tối thiểu từ caller và cài scope trên worker khi task chạy**, rồi restore/cleanup.
- Chỉ dùng trạng thái theo thread khi nhiều điểm trên **cùng synchronous call stack** thật sự cần đọc chung context; ưu tiên **biến cục bộ và context tường minh** nếu đủ.
- AOP qua proxy có **đường bypass**, và bytecode weaving có mô hình điểm can thiệp riêng; không xem một giá trị ThreadLocal là bằng chứng chính sách đã áp ở mọi đường.
- Với chính sách quan trọng, phải kiểm thử **đúng scope, lỗi, nested calls, thread reuse, propagation, memory retention và độ phủ join point**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-concurrency-005">Dọn dữ liệu ngữ cảnh để tránh rò rỉ giữa các yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Dọn dữ liệu ngữ cảnh để tránh rò rỉ giữa các yêu cầu trong AOP

> **Mục tiêu:** Hiểu cách advice quản lý ngữ cảnh theo yêu cầu (request context), vì sao **ThreadLocal không tự là request scope**, cách dữ liệu của request A có thể xuất hiện trong request B khi thread được tái sử dụng, và cách mở/đóng scope an toàn. Phân biệt **xóa ở root boundary** với **khôi phục ngữ cảnh cha ở nested boundary**; phân tích ngoại lệ, retry, cancellation, callback bất đồng bộ, class loader, bộ nhớ và dữ liệu nhạy cảm. Hai ví dụ Java 17 có thể biên dịch/chạy độc lập với stdout xác định. Phạm vi là Aspect-Oriented Programming (AOP) cơ bản và nâng cao nói chung; những hành vi phụ thuộc công cụ được nêu có điều kiện.

## 1. Tình huống điển hình: dữ liệu của A theo sang B

Một server có thread pool và ghi requestId vào context cho log:

~~~text
Worker thread T1
  Request A:
    set(requestId = "REQ-A")
    execute A
    exception xảy ra
    quên xóa context

  Request B:
    code đọc context từ T1
    nhận "REQ-A" thay vì ngữ cảnh của B
~~~

Hậu quả không chỉ là log sai. Dữ liệu tenant, actor, correlation ID hoặc một object lớn gắn A có thể bị giữ lại hoặc được sử dụng nhầm ở B. Nếu context còn được dùng để quyết định policy bảo mật, lỗi có thể trở nên nghiêm trọng.

**Quy tắc chính:** một scope đã cài ngữ cảnh phải có **đường đóng scope** đúng, ngay cả khi hoạt động thất bại. Việc request đã xử lý xong **không đồng nghĩa** ThreadLocal entry đã được xóa.

## 2. Hợp đồng cleanup: dữ liệu nào phải biến mất sau scope?

Bài về trạng thái theo luồng đã giải thích cơ chế `ThreadLocal` và việc thread pool tái sử dụng worker. Ở đây hãy chuyển kiến thức đó thành **hợp đồng có thể kiểm thử**: sau khi một scope do advice mở kết thúc, worker phải thấy lại **đúng trạng thái trước khi mở scope**, kể cả khi nghiệp vụ ném ngoại lệ.

Một scope quản lý context cần xác định đủ năm yếu tố:

| Yếu tố | Câu hỏi khi review |
| --- | --- |
| **Owner** | Advice nào thực hiện enter và chịu trách nhiệm close? |
| **Boundary** | Bắt đầu và kết thúc tại method call, execution, task hay async completion? |
| **Value** | Những trường context nào được phép cài, có giữ object lớn hay secret không? |
| **Exit paths** | Return, target throw, advice throw, timeout, cancel và nested call được xử lý ra sao? |
| **Postcondition** | Sau close, binding là `previous` hoặc **không còn binding**, đúng như trước enter |

Ví dụ nếu trước scope hiện tại chưa có trace ID, postcondition là không còn trace ID của request vừa xử lý. Nếu trước đó có một trace ID cha, postcondition là **khôi phục ID cha**, không được xóa cha và cũng không được giữ ID con.

## 3. Lựa chọn đúng ranh giới kết thúc

Một advice around một method không tự động sở hữu toàn bộ vòng đời logical request. Phạm vi có thể là:

- **Method invocation:** enter trước khi chuyển tiếp method, exit khi method return/throw.
- **Task execution:** enter trên thread thực thi task và exit khi callable/runnable kết thúc.
- **Async logical operation:** có thể kéo dài qua nhiều thread/stage; context snapshot sống đến một completion có quy tắc.
- **Nested operation:** scope trong hoạt động tạm thời, sau đó phục hồi scope ngoài.

Nếu method trả `CompletionStage` đang pending, `finally` của method sẽ chạy khi **method trả stage**, không đợi công việc nền hoàn tất. Nếu tài nguyên cần còn tồn tại cho task nền, advice phải bàn giao ownership hoặc gắn lifecycle đúng task; không giữ binding của thread gọi đến khi task khác hoàn thành.

Điều cần ghi vào thiết kế không phải tên API advice, mà là **điểm bắt đầu, điểm đóng, chủ thể đóng và điều kiện task thực sự hoàn thành**.

## 4. Ma trận cleanup theo đường đi

| Tình huống | Rủi ro cụ thể | Postcondition cần chứng minh |
| --- | --- | --- |
| Method trả bình thường | Scope con vẫn hiện diện trong worker | Binding cũ được phục hồi |
| Target ném ngoại lệ | Dòng cleanup sau `proceed` bị bỏ qua | Close chạy từ `finally`, exception gốc giữ đúng |
| Before/setup ném lỗi | Một phần context đã cài, nhưng chưa vào vùng `try` | Mọi phần đã cài thành công đều được dọn |
| Scope con đóng | `remove` vô điều kiện xóa cha | Context cha được phục hồi |
| Advice ghi log khi close thất bại | Lỗi phụ che nguyên nhân gốc | Có chính sách lỗi và đường ghi lỗi riêng |
| Task bị cancel trước khi chạy | Không có scope nào được worker mở | Không có cleanup giả trên một thread không sở hữu scope |
| Task đã bắt đầu rồi bị interrupt | Thoát có thể bị trì hoãn hoặc không xảy ra ngay | Cleanup theo thực tế đường unwind, có deadline giám sát |
| Worker được tái sử dụng | Request B đọc context A | Test task B trên đúng worker sau A |

Khác với lỗi data race giữa hai thread cùng ghi một biến chung, **stale context** là lỗi theo thời gian trên *cùng một worker*: request trước kết thúc nhưng binding vẫn còn. Bài kiểm thử phải phản ánh chính cơ chế đó.

## 5. Phân biệt xóa binding với đặt giá trị rỗng

`ThreadLocal.get()`, `set(...)` và `remove()` làm việc trên binding của **thread hiện tại**. Hai thao tác sau không nên được coi là giống hệt nhau:

```java
// Mảnh minh họa; biến LOCAL được giả định đã khai báo.
LOCAL.set(null); // Vẫn có phép gán giá trị null vào ThreadLocal.
LOCAL.remove();  // Gỡ binding hiện tại.
```

Với `ThreadLocal.withInitial(supplier)`, sau `remove()`, một lần `get()` có thể gọi lại supplier để tạo giá trị ban đầu; do đó phép kiểm tra `get()==null` chỉ là đủ cho trường hợp dùng `new ThreadLocal<>()` với giá trị ban đầu mặc định null. Nếu **null** tự nó là giá trị nghiệp vụ hợp lệ và cần phân biệt “không có binding”, phải thiết kế một representation riêng hoặc scope abstraction có lưu trạng thái tồn tại trước đó.

Đừng cố làm cleanup từ thread gọi chỉ vì task đã hoàn tất: `remove()` trên main thread không dọn được binding của executor worker. Cần đóng scope **trên đúng thread đã cài binding**.

## 6. Đặt cleanup gần thao tác cài context nhất

Một thiết kế dễ chứng minh hơn tách rời `before=set` rồi `afterReturning=remove` là tạo một **scope có owner rõ ràng**. Mẫu đồng bộ:

```java
// Mã minh họa, không phải chương trình độc lập.
Context previous = CURRENT.get();
CURRENT.set(context);
try {
    return proceed();
} finally {
    if (previous == null) CURRENT.remove();
    else CURRENT.set(previous);
}
```

Mẫu trên đúng khi `null` biểu diễn “không có context trước đó”; nếu dùng `ThreadLocal` có initializer hoặc `null` là một trạng thái quan trọng, scope manager phải lưu đầy đủ trạng thái cần phục hồi. Điều quan trọng là **mỗi lần cài có một nơi sở hữu việc phục hồi** và không có nhánh return/exception nào lách qua cleanup.

Nếu việc chuẩn bị bao gồm nhiều bước có thể lỗi, không đặt tất cả setup trước vùng bảo vệ rồi hy vọng cuối cùng sẽ cleanup. Mỗi resource/binding đã mở phải được bảo vệ từ lúc nó được thiết lập thành công.

## 7. Root scope khác nested scope ở postcondition

`Root scope` được tạo khi chưa có context. Kết thúc root cần bỏ binding do root tạo.

`Nested scope` thay tạm context A bằng B. Khi B thoát, **A phải vẫn đang hiện diện**:

```text
trước root: none
  root enter: A
    child enter: B
    child close: A
  root close: none
```

Một scope manager có thể lưu previous, kiểm soát thứ tự đóng LIFO và ngăn double-close hoặc close từ thread khác. Không đủ chỉ gọi `remove()` mỗi lần vì child sẽ xóa luôn context của parent; nhưng cũng không được để child giữ binding B khi parent đã tiếp tục.

Với các lời gọi AOP lồng nhau, test cả trình tự **A → B → A → none**, không chỉ “B thấy B” khi đang chạy.

## 8. Phân quyền sở hữu: ai cài, ai chỉ đọc?

Nếu Trace, Audit và Metrics cùng sử dụng một context, nên định nghĩa một **owner** mở/đóng scope và các consumer chỉ đọc. Nếu mỗi consumer tự `remove()` khi xong việc của mình, một consumer có thể xóa context trong lúc phần khác vẫn cần.

```text
ContextOwner.enter(A)
    Trace.read(A)
    Audit.read(A)
    Metrics.read(A)
ContextOwner.close()  -> previous
```

Một consumer muốn thêm thông tin tạm (ví dụ child span) có thể tạo scope con của chính nó, nhưng phải phục hồi đúng trước khi trả điều khiển về owner ngoài. Không dùng một cờ static hoặc shared instance để biết “context hiện tại”, vì dữ liệu thuộc từng invocation và có thể chạy đồng thời.

## 9. Chốt tiêu chí review trước ví dụ runnable

Các ví dụ tiếp theo kiểm chứng hai tính chất khác nhau:

1. **Phát hiện bug:** task A ném lỗi, để lại `ThreadLocal`; task B trên cùng worker đọc nhầm A. Việc sau cùng gọi cleanup chứng minh lý do lỗi có thể ẩn khi chỉ thử trên thread mới.
2. **Ngăn bug:** scope có enter/close, lưu previous và đóng trong `finally`; nested scope và worker propagation dùng snapshot đúng chủ sở hữu.

Sau khi chạy ví dụ, chỉ coi cleanup đạt yêu cầu khi xác minh được **trạng thái sau scope và task tiếp theo**, ngoài việc lời gọi nghiệp vụ trả kết quả. Điều này phân biệt bài về **cleanup contract và leak prevention** với bài trước về khái niệm `ThreadLocal`/thread pools.

## 10. Ví dụ Java 17 số 1 — chứng minh ThreadLocal leak

Chương trình cố ý để task A quên cleanup rồi ném exception. Task B chạy trên **cùng worker thread** và đọc A. Sau đó B xóa; C mở scope có try/finally; D xác nhận dữ liệu đã sạch.

Đây là Java thuần để mô phỏng lỗi trong advice/interceptor, **không phải mã của một bộ dệt AOP**.

Lưu file **ThreadLocalLeakDemo.java**:

~~~java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadLocalLeakDemo {
    private static final ThreadLocal<String> REQUEST =
            new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var failed = executor.submit(() -> {
                REQUEST.set("REQ-A");
                System.out.println("[A] context=" + REQUEST.get());
                // Cố tình thiếu finally/remove để chứng minh bug.
                throw new IllegalStateException("A failed");
            });

            try {
                failed.get();
            } catch (ExecutionException error) {
                System.out.println("[main] A failed: "
                        + error.getCause().getMessage());
            }

            executor.submit(() -> {
                System.out.println("[B] stale=" + REQUEST.get());
                REQUEST.remove();
                System.out.println("[B] cleaned=" + REQUEST.get());
            }).get();

            executor.submit(() -> {
                REQUEST.set("REQ-C");
                try {
                    System.out.println("[C] active=" + REQUEST.get());
                } finally {
                    REQUEST.remove();
                    System.out.println("[C] cleaned=" + REQUEST.get());
                }
            }).get();

            executor.submit(() -> {
                System.out.println("[D] context=" + REQUEST.get());
            }).get();
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Chạy với JDK 17:

~~~bash
javac ThreadLocalLeakDemo.java
java ThreadLocalLeakDemo
~~~

**Stdout:**

~~~text
[A] context=REQ-A
[main] A failed: A failed
[B] stale=REQ-A
[B] cleaned=null
[C] active=REQ-C
[C] cleaned=null
[D] context=null
~~~

### 10.1. Tại sao B nhận được A?

Single-thread executor sử dụng một worker cho các task tuần tự. A để lại REQUEST trong worker, dù Future của A thất bại. B đọc từ worker ấy nên thấy stale value.

Một thread pool lớn hơn có thể khiến lỗi **không tái hiện ổn định** vì task B rơi vào worker khác; nó không làm thiết kế thiếu cleanup trở nên an toàn.

### 10.2. Main không thể dọn hộ worker

failed.get chạy trên main thread, còn REQUEST.set chạy trên worker thread. Nếu main gọi REQUEST.remove, nó dọn entry của main chứ **không xóa entry trên worker**.

Mỗi thread-local binding phải được dọn trong thread sở hữu nó, hoặc hệ thống phải dùng một mô hình khác với lifecycle tường minh.

### 10.3. Vì sao D sạch?

B gọi remove trên worker, C mở và đóng trong cùng worker bằng finally, D kiểm tra get là null. **Test âm sau khi scope đóng** quan trọng ngang test dương khi scope đang hoạt động.

## 11. Vì sao before/after-returning không luôn đủ

Một thiết kế:

~~~text
before advice: set context
after-returning advice: remove context
~~~

có thể dọn được normal return nhưng bỏ sót exceptional exit nếu advice thứ hai chỉ chạy khi không có lỗi.

An toàn hơn là quanh advice sở hữu đồng thời enter và exit, hoặc một cơ chế after/finally có semantics được công cụ bảo đảm. Không tự ghép một before trên join point này với một after trên điểm khác nếu lifecycle của hai điểm không khớp tuyệt đối.

Khi nhiều aspect phối hợp, precedence còn ảnh hưởng thứ tự enter/close. Context owner cần bao phủ các consumer mà nó phục vụ.

## 12. Scope object trong Java

Một API scope có thể cho phép:

~~~text
scope = enter(newContext)
try:
    execute action
finally:
    scope.close()
~~~

Với Java, **AutoCloseable** và try-with-resources giúp đóng scope trên normal/exceptional exit.

Scope cần nhớ:

- **previous:** binding trước khi vào.
- **installed:** binding vừa đặt.
- **owner thread:** thread đã mở.
- **closed:** ngăn close lặp.
- **LIFO:** tránh đóng outer trước inner.

Một scope không nên được chuyền qua thread khác để close. ThreadLocal.remove của thread khác không xử lý entry của owner và còn có thể ảnh hưởng context riêng của thread đang close.

## 13. Ví dụ Java 17 số 2 — scope lồng nhau và async propagation

Ví dụ dùng record bất biến làm Context, Scope có ownership và restore, cùng một worker từ executor.

**Đây là Java thuần minh họa request-context lifecycle; không có weaving tự động.**

Lưu **ContextScopeDemo.java**:

~~~java
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ContextScopeDemo {
    record Context(String requestId, String actor) {}

    private static final ThreadLocal<Context> CURRENT =
            new ThreadLocal<>();

    static String label() {
        Context c = CURRENT.get();
        return c == null ? "none" : c.requestId() + "/" + c.actor();
    }

    static Scope enter(Context value) {
        return new Scope(value);
    }

    static final class Scope implements AutoCloseable {
        private final Thread owner;
        private final Context previous;
        private final Context installed;
        private boolean closed;

        Scope(Context value) {
            owner = Thread.currentThread();
            previous = CURRENT.get();
            installed = Objects.requireNonNull(value);
            CURRENT.set(installed);
        }

        @Override
        public void close() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("wrong thread");
            }
            if (closed) {
                throw new IllegalStateException("already closed");
            }
            if (CURRENT.get() != installed) {
                throw new IllegalStateException("close out of order");
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            System.out.println("[main] before=" + label());
            try (Scope outer = enter(new Context("R-1", "alice"))) {
                System.out.println("[outer] context=" + label());

                try (Scope inner = enter(
                        new Context("R-1-child", "service"))) {
                    System.out.println("[inner] context=" + label());
                }
                System.out.println("[outer] restored=" + label());

                Context snapshot = CURRENT.get();
                Future<?> task = executor.submit(() -> {
                    System.out.println("[worker] before=" + label());
                    try (Scope workerScope = enter(snapshot)) {
                        System.out.println("[worker] inside=" + label());
                    }
                    System.out.println("[worker] after=" + label());
                });
                task.get();
            }
            System.out.println("[main] after=" + label());
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ContextScopeDemo.java
java ContextScopeDemo
~~~

**Stdout:**

~~~text
[main] before=none
[outer] context=R-1/alice
[inner] context=R-1-child/service
[outer] restored=R-1/alice
[worker] before=none
[worker] inside=R-1/alice
[worker] after=none
[main] after=none
~~~

### 13.1. Inner phải restore outer

Khi outer enter, previous là null. Khi inner enter, previous là Context R-1/alice. Khi inner close, nó set lại previous. Sau khi outer close, vì previous của outer là null nên remove.

Nếu inner luôn remove, outer mất binding dù scope của outer chưa kết thúc.

### 13.2. Worker không tự nhận ThreadLocal từ main

Context từ main được chụp dưới dạng record bất biến rồi **truyền tường minh vào lambda**. Worker tự enter và close theo thread của worker. Điều đó không có nghĩa có một ThreadLocal value được chia sẻ giữa hai thread.

Snapshot trong ví dụ có cả actor để minh họa. Trong sản phẩm, nên truyền **tối thiểu những trường được phép và cần thiết**, không mặc định truyền danh tính người dùng tới mọi tác vụ nền.

### 13.3. Vì sao kiểm tra thread owner và thứ tự close?

Nếu close trên thread khác, ThreadLocal.remove tác động tới thread đó thay vì owner. Kiểm tra owner giúp phát hiện bug.

Nếu outer close trước inner, state bị phục hồi sai; kiểm tra installed identity phát hiện sai thứ tự trong các trường hợp này. Dù cơ chế scope phát hiện lỗi, code thực tế vẫn phải bảo đảm **mở và đóng đúng thứ tự** để giữ tính đúng đắn.

## 14. Propagation bất đồng bộ không tự xảy ra

Một logical request có thể đi qua:

~~~text
Thread T1: bắt đầu request
    install context A
    submit task
    close context A trên T1

Thread T2: chạy task
    install snapshot phù hợp
    do work
    close snapshot trên T2

Thread T3: chạy completion callback
    install approved snapshot
    handle completion
    close trên T3
~~~

Không được giữ context của T1 mãi chỉ để chờ async task hoàn tất: T1 có thể đã quay lại pool và bắt đầu request B.

Từng **execution boundary trên mỗi thread** phải có scope riêng. Logical request lifetime có thể dài hơn bất kỳ thread-local binding đơn lẻ nào.

## 15. Future trả về không đồng nghĩa tác vụ hoàn tất

Một quanh advice bọc method trả CompletableFuture có thể chạy finally ngay sau khi method trả handle. Công việc thực tế sau đó vẫn tiếp tục và có thể thất bại trên thread khác.

Vì vậy:

- Phạm vi **method return** khác phạm vi **future completion**.
- Nếu callback cần context, nó phải nhận một snapshot đúng quyền và tự quản lý scope.
- Callback success, failure, timeout và cancellation đều cần chính sách ngữ cảnh.
- Không tự giả định callback sẽ chạy trong một thread cụ thể.
- Context sau completion cần được dọn ở thread nơi callback thực sự chạy.

ThreadLocal chỉ là một cách lưu ambient context trong call stack, không phải cơ chế truyền tự động qua mọi kiểu async.

## 16. Cancellation, interruption và timeout

Một task có thể bị cancel hoặc interrupt. Nếu nó đã enter scope và sau đó đi qua finally, cleanup có thể diễn ra. Nhưng **cancel không bảo đảm task dừng ngay lập tức**.

Các khả năng cần phân tích:

- Task chưa được chạy: không có scope worker nào được mở.
- Task đang chạy và phản ứng với interrupt: finally có thể cleanup.
- Task bị kẹt trong I/O không phản ứng ngay: scope vẫn tồn tại cho tới khi thoát.
- Caller timeout và kết thúc chờ: worker vẫn có thể tiếp tục.
- Callback nhận cancellation: cần scope riêng nếu dùng context.

Caller gọi remove trên thread của nó **không thể dọn** ThreadLocal entry của một worker khác. Hủy tác vụ và dọn binding là hai trách nhiệm riêng.

## 17. Cẩn thận với InheritableThreadLocal

**InheritableThreadLocal** cung cấp cơ chế khởi tạo value cho thread con khi thread được tạo. Nhưng request được giao vào **thread pool đã có worker từ trước** không đồng nghĩa với tạo thread con mới cho request ấy.

Vì thế InheritableThreadLocal không thể được coi là giải pháp **per-task context propagation** mặc định.

Ngoài ra, giá trị được kế thừa có thể không có lifecycle giống logical request, và người dùng có thể nhầm dữ liệu được copy lúc tạo thread với dữ liệu current context mới nhất.

Thiết kế đúng cần xét **thời điểm thread được tạo, task được giao và scope được đóng**.

## 18. Memory leak do giá trị ThreadLocal

Một context có thể giữ nhiều dữ liệu:

~~~text
Worker thread
   |
   ThreadLocal entry
      |
      RequestContext
         |
         +-- payload
         +-- user profile
         +-- buffers
~~~

Nếu worker sống lâu, entry không được xóa có thể giữ cả object graph lâu hơn request.

Ở các Java implementation thường dùng, ThreadLocalMap có key yếu nhưng value vẫn có thể được giữ mạnh khi entry chưa được dọn. Việc key ThreadLocal không còn được tham chiếu không có nghĩa value được thu hồi ngay.

Do đó **remove đúng thời điểm** vừa ngăn logical leak vừa giảm retention. Một requestId nhỏ vẫn cần dọn vì nó có thể gây sai log, dù ảnh hưởng bộ nhớ không lớn.

## 19. Context giữa các aspect có chung owner

Ví dụ ba aspect cùng dùng context:

~~~text
ContextOwner
    Trace reads
    Audit reads
    Metrics reads
ContextOwner closes
~~~

Nếu Metrics tự remove context, Trace/Audit sau nó có thể không nhìn thấy context nữa.

Hãy định nghĩa owner:

1. Ai tạo context?
2. Ai được đọc?
3. Ai được tạo scope con?
4. Scope con phải restore về đâu?
5. Ai đóng root scope?
6. Khi nào consumer cuối đã hoàn tất?
7. Có ranh giới async nào cần snapshot không?

Precedence của các around advice có thể quyết định chính sách nào bao ngoài; thứ tự phải dựa trên **bảo đảm của công cụ AOP**, không dựa vào tên class hoặc vị trí file.

## 20. Retry và multiple proceed

Một around advice có thể gọi continuation nhiều lần nếu cơ chế cho phép. Nếu context owner nằm ngoài Retry:

~~~text
ContextOwner enter A
   Retry
      attempt 1
      attempt 2
ContextOwner close A
~~~

Cả hai attempts có thể dùng chung request context. Nếu scope được mở trong mỗi attempt, từng attempt có thể có context phụ nhưng phải được đóng riêng.

Không đặt cleanup ở vị trí chỉ được gọi **một lần** nếu đã mở nhiều scope trong các attempts. Một thiết kế cần đảm bảo **mỗi enter có đúng một close** và data owner rõ.

## 21. Exception trong cleanup có thể che lỗi gốc

Original có thể ném exception E, nhưng cleanup của advice tự ném F trong finally, khiến Caller thấy F thay vì E.

ThreadLocal.remove tự nó đơn giản; nguy cơ tăng nếu cleanup còn gọi logger, serializer, remote sink hoặc resource close.

Một scope context nên có **close path tối giản và có thể kiểm thử**. Nếu cần đóng tài nguyên phức tạp, hãy có chính sách primary/suppressed exceptions phù hợp, tránh làm mất nguyên nhân nghiệp vụ.

Nếu scope phát hiện bị đóng sai thread hoặc sai LIFO, đó là lỗi sử dụng API cần được điều tra; không nên nuốt ngầm chỉ để log trông sạch.

## 22. Không đưa dữ liệu nhạy cảm vào ambient context tùy tiện

Nếu context chứa actor hoặc tenant, một code nhánh B vô tình đọc A có thể tạo vấn đề phân tách thông tin.

Cần:

- Chỉ cài actor/tenant đã được xác thực theo một ranh giới tin cậy.
- Không dùng một requestId tự khai báo làm chứng cứ quyền truy cập.
- Giới hạn các trường truyền qua async boundary.
- Sử dụng snapshot bất biến, không chia sẻ mutable map tùy tiện.
- Không giữ raw token, cookie hoặc payload nếu không cần.
- Không log toàn bộ context bằng toString ở mọi advice.
- Có chính sách khi currentContext không tồn tại.

**Missing context** không được giải quyết bằng cách đọc lại stale context của request trước.

## 23. Logging diagnostic context cũng cần cleanup

Các hệ logging có thể cung cấp diagnostic context để gắn requestId hoặc traceId vào log. Khi cơ chế dựa trên thread, lỗi thread reuse có thể gây log của B mang ID của A.

Một aspect xử lý logging context nên:

- Mở binding tại ranh giới đã chọn.
- Chỉ thêm metadata cần thiết.
- Cho consumer phù hợp đọc.
- Restore previous hoặc remove khi rời scope.
- Dọn cả đường exception.
- Propagate snapshot có chọn lọc cho async task.
- Không tin rằng logger tự quản lý request lifecycle.

Không giả định mọi thư viện logging có cùng API hoặc cùng cách lưu trữ; đây là nguyên lý về **scope và ownership**.

## 24. Unit test cần có cả ca âm

Chỉ kiểm tra:

~~~text
Inside request A: context=A
~~~

chưa chứng minh cleanup.

Cần kiểm tra:

~~~text
After A: context=none hoặc previous
B trên cùng worker: không nhìn thấy A
~~~

Những ca tối thiểu:

| Ca | Kỳ vọng |
|---|---|
| A hoàn tất bình thường | Entry A được dọn |
| A ném RuntimeException | Finally vẫn cleanup khi rời scope |
| Inner scope B kết thúc | Previous A được khôi phục |
| Root scope kết thúc | Không còn A |
| Request B chạy cùng worker sau A | B không nhìn thấy A |
| Hai threads song song | Entry không lẫn nhau |
| Worker task hoàn thành exceptionally | Worker scope được đóng |
| Callback chạy trên executor | Context vào đúng nơi, cleanup đúng nơi |
| Cancellation/timeout | Không giả định Caller dọn được worker |
| Scope close sai thread | Lỗi ownership được phát hiện |
| Scope close sai thứ tự | Không âm thầm làm hỏng context |
| Aspect ngoài short-circuit | Không để context mồ côi |
| Retry nhiều attempts | Scope không bị đóng thiếu/thừa |
| Không có context | Có hành vi theo hợp đồng, không dùng stale data |
| Context chứa object lớn | Không giữ sống quá lâu ngoài ý định |

Executor có **một worker thread** giúp kiểm thử leak ổn định. Nếu sử dụng nhiều worker, việc B chạy ở thread khác có thể che bug.

## 25. Quy trình chẩn đoán khi log B mang ID của A

1. Xác định dữ liệu requestId được lưu ở đâu.
2. Phân biệt ThreadLocal với global/static và per-target aspect state.
3. Xác định thread thực hiện set và get.
4. Xem executor có tái sử dụng thread không.
5. Xác định advice nào sở hữu việc mở context.
6. Kiểm tra tất cả đường thoát khỏi scope.
7. Kiểm tra finally, try-with-resources hoặc after/finally semantics.
8. Xem nested advice có restore previous hay chỉ remove.
9. Kiểm tra handler nào tự remove context của owner khác.
10. Xem context có được truyền có chủ đích qua executor hay không.
11. Kiểm tra callback success/failure/timeout/cancel.
12. Kiểm tra use of InheritableThreadLocal.
13. Kiểm tra stale value hoặc value giữ object graph lớn.
14. Tạo test với hai request trên cùng single-thread executor.
15. Sửa tại **scope owner**, không chèn remove tùy ý trong mọi consumer.

Không sửa lỗi chỉ bằng cách thay tên correlation ID trong log. Nếu binding vẫn rò, các consumers khác vẫn có thể đọc nhầm.

## 26. Lỗi thường gặp

**Lỗi 1: set mà không remove.** Thread pool giữ context A cho B.

**Lỗi 2: chỉ cleanup trên normal return.** Exceptional path để lại stale entry.

**Lỗi 3: nested scope chỉ remove.** Làm mất context cha.

**Lỗi 4: close Scope trên thread khác.** Dọn nhầm thread, owner vẫn rò.

**Lỗi 5: singleton aspect field lưu actor hiện tại.** Nhiều invocation dùng chung field.

**Lỗi 6: dùng per-target state làm request scope.** Cùng Service target xử lý nhiều request.

**Lỗi 7: assume ThreadLocal tự lan truyền qua executor.** Worker không nhất thiết có context.

**Lỗi 8: dùng InheritableThreadLocal với thread pool như per-task scope.** Giá trị có thể lỗi thời.

**Lỗi 9: callback không mở/đóng scope.** Rò ngữ cảnh sau completion.

**Lỗi 10: Caller remove hộ worker.** Chỉ dọn entry của Caller thread.

**Lỗi 11: truyền raw context không kiểm duyệt.** Lộ dữ liệu và kéo dài object lifetime.

**Lỗi 12: cleanup logger ném lỗi rồi che lỗi gốc.** Phải thiết kế error contract.

**Lỗi 13: chỉ test context bên trong request.** Không phát hiện dữ liệu còn sót sau request.

**Lỗi 14: coi context là quyền truy cập.** Actor không được xác thực có thể bị dùng sai.

## 27. Khi nào nên truyền context tường minh?

Trong chuỗi async phức tạp, có thể chọn explicit parameters:

~~~text
process(Context ctx, Input input)
  -> validate(ctx, input)
  -> submitAsync(ctx, input)
~~~

Ưu điểm là rõ dữ liệu được đưa tới đâu, dễ test và ít phụ thuộc ambient state. Nhược điểm là cần truyền xuyên nhiều tầng.

ThreadLocal có thể thuận tiện trong synchronous call stack nếu mở/đóng scope chặt chẽ; explicit context có thể phù hợp khi một logical request di chuyển qua nhiều executor.

Không có cơ chế thắng mọi tình huống. Cần dựa trên **execution model, concurrency, security và lifecycle ownership**.

## 28. Checklist thiết kế một advice quản lý context

- [ ] Xác định logical request boundary thực tế.
- [ ] Chỉ đặt thông tin được phép và cần thiết.
- [ ] Có một owner chịu trách nhiệm enter/close.
- [ ] Có quy tắc root-remove và nested-restore.
- [ ] Dùng finally hoặc scope có cleanup bảo đảm theo ranh giới.
- [ ] Không đóng ThreadLocal scope từ thread khác.
- [ ] Không dùng shared mutable static currentRequest.
- [ ] Không coi per-target aspect state là per-request.
- [ ] Không giả định ThreadLocal tự propagation sang executor.
- [ ] Có snapshot đã kiểm duyệt ở async boundary.
- [ ] Worker/callback tự enter/close trong thread thực thi.
- [ ] Đường exception, retry và short-circuit đều được kiểm thử.
- [ ] Cancellation/timeout được phân biệt với cleanup.
- [ ] Không để cleanup che lỗi nghiệp vụ ngoài hợp đồng.
- [ ] Có test trên thread pool reuse.
- [ ] Kiểm tra context sau request không còn dữ liệu của request trước.
- [ ] Không dùng requestId hay actor không đáng tin như quyền truy cập.

## 29. Bài tập tự kiểm tra

### Câu 1: ThreadLocal và thread pool

A set ngữ cảnh rồi ném lỗi, quên cleanup. B chạy sau trên cùng worker thread. B có thể đọc A không?

**Đáp án:** có. ThreadLocal theo thread, không theo Future hoặc request.

### Câu 2: Main remove giúp worker

Main gọi ThreadLocal.remove sau khi Future của A thất bại. Entry ở worker chắc chắn được dọn không?

**Đáp án:** không. Remove chỉ tác động tới entry của thread gọi.

### Câu 3: Nested scope

A enter, B enter rồi B dùng remove. A có còn context A không?

**Đáp án:** không nếu B không restore previous. Nested scope phải phục hồi A.

### Câu 4: Per-target và per-request

Một target Service chung phục vụ A và B. Per-target aspect instance có tự chứa context riêng mỗi request không?

**Đáp án:** không. Trạng thái của target có thể sống qua nhiều request.

### Câu 5: Future chưa hoàn tất

Method trả CompletableFuture, quanh advice close context khi method return. Future sau đó hoàn thành trên thread khác. Callback có tự thấy context ban đầu không?

**Đáp án:** không có bảo đảm. Callback cần scope/context propagation riêng.

### Câu 6: InheritableThreadLocal

Dùng InheritableThreadLocal có tự lấy context hiện hành của từng task vào worker của thread pool không?

**Đáp án:** không. Kế thừa xảy ra theo thread creation, không phải mỗi lần submit task.

### Câu 7: Cancellation

Caller timeout rồi close scope của Caller. Worker vẫn chạy. Context worker đã sạch chưa?

**Đáp án:** chưa chắc. Worker phải cleanup scope của chính nó khi thực sự thoát.

### Câu 8: Scope owner

Audit chỉ đọc context do Tracing tạo. Audit có nên remove nó sau khi ghi xong không?

**Đáp án:** không. Owner của context phải quản lý đóng scope; consumer chỉ đọc không tự xóa.

### Câu 9: Finally và exception

Original ném IOException, cleanup logger trong finally ném RuntimeException. Có nguy cơ che IOException không?

**Đáp án:** có. Nên giữ cleanup đơn giản và có chính sách lỗi rõ.

### Câu 10: Kiểm thử tối thiểu

Chỉ kiểm tra trong A có requestId A đã đủ chưa?

**Đáp án:** chưa. Phải kiểm tra sau A đã sạch, và request B trên cùng thread không nhìn thấy A.

## 30. Tổng kết

**Dọn dữ liệu ngữ cảnh để tránh rò rỉ giữa các yêu cầu** là một trách nhiệm lifecycle riêng, không tự được hoàn thành chỉ vì advice có thể đọc và đặt request metadata.

Những nguyên tắc cần nhớ:

- **ThreadLocal là thread-scoped, không phải request-scoped**.
- **Thread pool reuse** khiến dữ liệu request trước tồn tại trong request sau nếu quên dọn.
- **Mỗi enter cần một close**; root scope remove, nested scope restore previous.
- **Chỉ owner đóng scope**, consumer không tự dọn context của người khác.
- **Thread khác không thể dọn binding của owner bằng ThreadLocal.remove**.
- **Async task và callback** phải có scope riêng và propagation có chủ đích.
- **Context bất biến, tối thiểu và có nguồn đáng tin** giúp giảm rủi ro chia sẻ sai.
- **Per-target state và singleton aspect fields** không phải nơi phù hợp cho dữ liệu request hiện tại.
- **Finally không thay thế toàn bộ cancellation/deadline management** và cleanup cũng cần có error contract.
- **Test âm trên cùng worker sau request** mới chứng minh state không còn rò.

**Câu hỏi cốt lõi của mỗi advice quản lý context:** *Ai mở scope này, nó thuộc thread/execution boundary nào, và ai chắc chắn phục hồi hoặc xóa đúng dữ liệu trước khi một yêu cầu khác dùng lại môi trường đó?*


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-concurrency-006">Truyền ngữ cảnh qua tác vụ bất đồng bộ có kiểm soát</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền ngữ cảnh qua tác vụ bất đồng bộ có kiểm soát trong AOP

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, advice thường cần biết thông tin về lần xử lý hiện tại: mã tương quan (correlation/trace ID), phạm vi quan sát, tên operation, thời hạn, tenant đã được xác minh hoặc chính sách áp dụng cho một lời gọi. Những dữ liệu này thường được gọi là **ngữ cảnh thực thi (execution context)**. Ở luồng gọi **đồng bộ** trong cùng một thread, có thể truyền trực tiếp qua tham số, local variable hoặc một context holder được quản lý cẩn thận.

Tuy nhiên, một phương thức nghiệp vụ có thể **gửi tác vụ sang executor**, trả về `Future` hoặc `CompletionStage`, rồi công việc thật tiếp tục trên **thread khác**. Khi đó, ngữ cảnh từ caller **không tự xuất hiện** trong thread chạy tác vụ. Nếu advice chỉ đặt một giá trị vào `ThreadLocal` ở thread ban đầu rồi giả định tác vụ bất đồng bộ sẽ đọc được, kết quả có thể là mất trace, gắn sai tenant, dùng thông tin của request cũ hoặc ghi audit sai chủ thể.

Một cách giải quyết có kiểm soát gồm các bước:

1. **Capture:** lấy snapshot ngữ cảnh được phép truyền tại **ranh giới tạo/gửi tác vụ**.
2. **Propagate:** gắn snapshot đó vào tác vụ bằng wrapper, tham số hoặc API hỗ trợ.
3. **Install/activate:** cài snapshot vào phạm vi thực thi của thread đang chạy **trong lúc tác vụ thực sự thực hiện**.
4. **Execute:** cho phần nghiệp vụ và advice cần context chạy bên trong phạm vi ấy.
5. **Restore/clear:** trong `finally`, khôi phục giá trị có từ trước hoặc xóa context, kể cả khi có ngoại lệ/cancellation.
6. **Verify:** kiểm thử task bình thường, lồng nhau, lỗi, thread pool tái sử dụng và các nhánh async khác.

**Hai quy tắc cốt lõi:** context phải thuộc **đúng invocation/logical operation**, không phải một field dùng chung của aspect; và phải **chủ động xác định ranh giới, dữ liệu, thời điểm và thời hạn truyền**, không truyền toàn bộ request object hoặc credentials theo quán tính.

Bài này nói về **AOP cơ bản và nâng cao nói chung**, không áp đặt một framework riêng. Ví dụ Java 17 thuần dùng một lớp wrapper mô phỏng advice tại thời điểm scheduling; nó không phải một bộ weaver tự can thiệp mọi lời gọi hoặc một cơ chế pointcut phổ quát.

## 1. Những loại ngữ cảnh cần phân biệt

| Loại dữ liệu | Ví dụ | Có nên tự động truyền? | Điều kiện |
| --- | --- | --- | --- |
| Correlation/trace ID | `trace-42` | Có thể | Format hợp lệ, độ dài giới hạn, ngăn dữ liệu nhạy cảm |
| Span/operation context | Parent trace/span, sampling flag | Có thể | Tuân thủ ngữ nghĩa span và thời điểm completion |
| Tenant identity đã xác minh | Tenant ID | Chỉ khi nghiệp vụ cần | Lấy từ nguồn tin cậy, không tin một chuỗi client tùy ý |
| Auth principal/token | Credential, access token | **Không mặc định** | Security design rõ, least privilege, expiry/revocation |
| Deadline/cancellation | Hạn chót, tín hiệu hủy | Thường cần mô hình riêng | Truyền và kiểm tra ở điểm thích hợp |
| Locale/timezone | Ngôn ngữ định dạng | Khi ảnh hưởng kết quả hợp đồng | Bất biến hoặc snapshot rõ |
| Transaction context | Định danh giao dịch, connection | **Không tự sao chép** | Transaction có ownership/thread binding và commit rules riêng |
| Logging context | Operation/category đã duyệt | Có thể | Dọn sạch, giới hạn field, không truyền PII |
| Mutable request/response object | Toàn bộ HTTP request, payload | Nên tránh | Vòng đời, thread safety, quyền truy cập, retention |

**Không có một “context toàn năng”.** Một trace ID phục vụ tương quan sự kiện; nó **không xác nhận danh tính người dùng**. Một tenant ID do caller tự truyền vào cũng không phải giấy phép truy cập dữ liệu của tenant ấy. Một advice bảo mật phải luôn xác minh identity/authorization tại ranh giới đáng tin, theo hợp đồng cụ thể.

### 1.1 Ngữ cảnh join point và ngữ cảnh nghiệp vụ

AOP có thể đưa tới advice những giá trị:
- `args`: tham số của invocation hiện tại.
- `target`: đối tượng đích của điểm đang xét khi có.
- `this`: đối tượng đang thực thi, tùy loại join point và mô hình.
- Metadata của phương thức/chữ ký.
- Context riêng của một trace hoặc thao tác logic.

Không phải dữ liệu nào ở join point cũng cần đưa sang async task. Truyền toàn bộ `args`, `target` hay một object proxy sang nơi chạy sau có thể **giữ object sống lâu hơn cần thiết**, làm rò thông tin hoặc phá thread safety. Chỉ copy các field được phép và thực sự dùng ở phía tác vụ.

### 1.2 Context khác shared aspect state

Một aspect dùng chung có thể nhận hàng nghìn invocation đồng thời. Nếu có field `currentTraceId`, T2 có thể ghi đè khi T1 chưa xử lý xong. Khi mang context sang task:
- **Sai:** lưu trace/tenant hiện tại vào field của shared aspect.
- **Đúng hơn:** capture một object **bất biến** cho invocation, rồi truyền nó qua wrapper/task boundary.
- Nếu phải giữ state theo một workflow, cần scope/key/lifecycle rõ, không dùng một biến global tùy tiện.

## 2. Vì sao ThreadLocal không tự truyền ngữ cảnh?

`ThreadLocal<T>` trong Java cung cấp một value **riêng cho mỗi Java thread**. Khi caller T1 chạy:

~~~text
Thread T1 (request handler):
    ThreadLocal.trace = "A"
    executor.submit(task)
~~~

Tác vụ có thể chạy trên worker T2:

~~~text
Thread T2 (executor worker):
    read ThreadLocal.trace
    => không tự có "A"
~~~

Một thread trong pool thường đã tồn tại từ trước, hoặc được tạo ở một thời điểm không trùng với caller. **Không có cam kết ThreadLocal của T1 được copy sang T2**.

### 2.1 Thread pool tái sử dụng còn nguy hiểm hơn mất context

Nếu một task cài `trace=A` trên worker nhưng **không xóa**:
1. Task A hoàn tất.
2. Executor tái sử dụng **chính worker** cho task B.
3. Task B đọc `trace=A` mặc dù không thuộc request A.

Lúc đó dữ liệu có thể bị **gắn sai correlation, sai tenant, sai audit subject**, dẫn tới lỗi bảo mật hoặc truy vết rất khó phát hiện. Dùng một worker cho nhiều task là chủ ý của thread pool, không phải bug executor.

### 2.2 Không đồng nhất per-thread với per-request

Một logical request:
- Có thể đi qua **nhiều thread** vì async fan-out, callback, timeout handler.
- Có thể có **nhiều child tasks** chạy song song.
- Có thể kết thúc ở thread khác thread đã bắt đầu.

Một thread:
- Có thể phục vụ **nhiều request** nối tiếp nhau.
- Có thể xử lý nested flow với context tạm thời khác.
- Có thể chạy task sau khi request ban đầu đã hết hạn.

Vì vậy **request context cần được mô hình hóa theo hoạt động logic**, còn ThreadLocal (nếu dùng) chỉ là **cơ chế gắn tạm vào phạm vi của thread hiện tại**.

## 3. Capture → wrap → restore: giao thức truyền có kiểm soát

Một giao thức cơ bản:

~~~text
Caller T1                            Executor worker T2
    |                                       |
    | context A (verified & minimal)        |
    |                                       |
    | capture snapshot A                    |
    | ---- submit(wrapped task A) --------> |
    |                                       | save previous context P
    |                                       | install snapshot A
    |                                       | run task + applicable advice
    |                                       | finally restore P (or remove)
    |                                       |
    | <----------- Future result --------- |
    | caller scope exits; restore caller    |
~~~

Những khác biệt quan trọng:
- **Capture** xảy ra ở **caller thread tại thời điểm scheduling**, không phải lúc task bắt đầu chạy.
- **Install** xảy ra ở **worker thread khi task bắt đầu chạy**.
- **Restore** xảy ra trong `finally` của chính worker.
- Việc restore ở caller **không** dọn context trên worker; mỗi bên có scope riêng.
- Khi tác vụ chạy nhiều thread hoặc chia ra child tasks, mỗi ranh giới cần propagation theo thiết kế.
- Truyền đúng context **không tự bảo đảm** task còn được quyền thực hiện thao tác tại thời điểm chạy; các policy có expiry/revocation phải được tái kiểm tra nếu hợp đồng yêu cầu.

### 3.1 Vì sao phải snapshot?

Nếu advice giữ tham chiếu tới một map mutable `currentContext` rồi submit:
- Caller hoặc invocation B có thể thay map trước khi task A bắt đầu.
- Một task có thể thấy field phiên bản cũ xen field phiên bản mới.
- Có thể chia sẻ object request hết hạn hoặc dữ liệu không còn hợp lệ.

Một object bất biến chứa **một tập field có chủ đích** tạo ranh giới rõ hơn. Snapshot không nhất thiết là "copy toàn bộ object"; cần chọn field, validate và tránh chứa các tham chiếu mutable chưa kiểm soát.

### 3.2 Vì sao restore chứ không chỉ clear?

Giả sử worker đang chạy một context P (ví dụ một lời gọi lồng nhau), sau đó tạm gọi một đoạn code với context A. Khi kết thúc A, phải **khôi phục P**, không luôn luôn remove:

~~~text
worker initially: P
  enter nested scope A
    current: A
  exit nested scope A
worker current again: P
~~~

Nếu trước đó không có context, `remove()` hợp lý. Nếu có context, `set(previous)` giữ đúng scope lồng nhau. Đây là cùng nguyên tắc **push/pop trạng thái**, không xóa tùy tiện context của phạm vi ngoài.

### 3.3 Phạm vi exception và cancellation

`try/finally` là yêu cầu tối thiểu: task có thể return, throw hoặc thực hiện cleanup. Với cancel:
- Nếu task **chưa chạy**, wrapper không cài ThreadLocal; không có gì phải dọn trên worker.
- Nếu task **đang chạy và bị ngắt**, cleanup trong `finally` chạy khi control thực sự thoát scope; không được mặc định `cancel(true)` tức khắc giết task.
- Nếu task **không đáp ứng interruption hoặc bị kẹt**, context có thể còn trong worker trong lúc task tiếp tục chạy. Do đó cần timeout, interrupt handling và task ownership phù hợp.
- Với callback bất đồng bộ được đăng ký về sau, scope hiện tại đóng rồi **không đủ** để giữ context cho callback; phải xử lý chính boundary đó.

## 4. Java 17 chạy độc lập: có truyền, không truyền, nested và exception

Ví dụ dưới đây dùng:
- `TraceContext` là Java record bất biến, chỉ chứa **traceId** và **tenantId giả**.
- `ContextScope` quản lý ThreadLocal bằng `with(snapshot, Callable)` có **restore trong finally**.
- `PropagationAdvice.prepare` **capture ngữ cảnh ở thời điểm gửi task** và trả một `Callable` đã bọc.
- `Executors.newSingleThreadExecutor()` để **tái sử dụng cùng worker**, giúp chứng minh không rò context.
- `Future.get()` và chỉ in ở thread chính để **stdout có thứ tự xác định**, không phụ thuộc lịch print giữa các task.
- Trường hợp raw task không bọc, task có bọc, scope lồng nhau, task ném lỗi và tác vụ sau cùng không có context.

Mẫu này mô phỏng cách **một advice ở boundary scheduling** có thể bọc task. Nó **không tự dệt bytecode** vào mọi `Executor.submit`; caller phải dùng wrapper (hoặc một cơ chế instrumentation phù hợp) ở các boundary được chọn.

Tạo file `AsyncContextAdviceDemo.java`:

~~~java
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AsyncContextAdviceDemo {
    record TraceContext(String traceId, String tenantId) {}

    static final class ContextScope {
        private static final ThreadLocal<TraceContext> CURRENT =
                new ThreadLocal<>();

        static TraceContext current() {
            return CURRENT.get();
        }

        static String describe() {
            TraceContext c = current();
            if (c == null) {
                return "trace=none tenant=none";
            }
            return "trace=" + c.traceId() + " tenant=" + c.tenantId();
        }

        static <T> T with(TraceContext next, Callable<T> task)
                throws Exception {
            TraceContext previous = CURRENT.get();

            if (next == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(next);
            }

            try {
                return task.call();
            } finally {
                if (previous == null) {
                    CURRENT.remove();
                } else {
                    CURRENT.set(previous);
                }
            }
        }
    }

    // Advice mô phỏng tại boundary gửi tác vụ.
    static final class PropagationAdvice {
        static <T> Callable<T> prepare(Callable<T> task) {
            TraceContext snapshot = ContextScope.current();
            return () -> ContextScope.with(snapshot, task);
        }
    }

    public static void main(String[] args) throws Exception {
        TraceContext a = new TraceContext("A", "alpha");
        TraceContext b = new TraceContext("B", "beta");

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            String sync = ContextScope.with(
                    a, ContextScope::describe);
            System.out.println("SYNC " + sync);

            Future<String> raw = ContextScope.with(
                    a, () -> pool.submit(
                            () -> "RAW " + ContextScope.describe()));
            System.out.println(raw.get());

            Future<String> wrapped = ContextScope.with(
                    a, () -> pool.submit(
                            PropagationAdvice.prepare(
                                    () -> "SAFE "
                                            + ContextScope.describe())));
            System.out.println(wrapped.get());

            Future<String> nested = ContextScope.with(
                    a, () -> pool.submit(
                            PropagationAdvice.prepare(() -> {
                                String before = ContextScope.describe();
                                String inner = ContextScope.with(
                                        b, ContextScope::describe);
                                String after = ContextScope.describe();
                                return "NESTED before=[" + before
                                        + "] inner=[" + inner
                                        + "] after=[" + after + "]";
                            })));
            System.out.println(nested.get());

            Future<String> failure = ContextScope.with(
                    a, () -> pool.submit(
                            PropagationAdvice.<String>prepare(() -> {
                                throw new IllegalStateException("boom");
                            })));

            try {
                failure.get();
            } catch (ExecutionException error) {
                Throwable cause = error.getCause();
                System.out.println("ERROR "
                        + cause.getClass().getSimpleName()
                        + ":" + cause.getMessage());
            }

            Future<String> following = pool.submit(
                    () -> "POST " + ContextScope.describe());
            System.out.println(following.get());
        } finally {
            pool.shutdownNow();
        }

        System.out.println("MAIN " + ContextScope.describe());
    }
}
~~~

Biên dịch và chạy bằng **JDK 17 trở lên**:

~~~bash
javac AsyncContextAdviceDemo.java
java AsyncContextAdviceDemo
~~~

Stdout xác định:

~~~text
SYNC trace=A tenant=alpha
RAW trace=none tenant=none
SAFE trace=A tenant=alpha
NESTED before=[trace=A tenant=alpha] inner=[trace=B tenant=beta] after=[trace=A tenant=alpha]
ERROR IllegalStateException:boom
POST trace=none tenant=none
MAIN trace=none tenant=none
~~~

### 4.1 Giải thích `RAW`: worker không kế thừa ThreadLocal

Caller T1 có context A khi submit `raw`, nhưng `raw` không được bọc. Thread do executor sử dụng có ThreadLocal của **chính nó**, ban đầu không có context nên in:

~~~text
RAW trace=none tenant=none
~~~

Đây là **hành vi đúng theo Java ThreadLocal**, không phải lỗi executor. Không dùng sự vắng context trong worker để suy rằng caller đã mất context; hai thread có holder khác nhau.

### 4.2 Giải thích `SAFE`: capture ở caller, install trên worker

`PropagationAdvice.prepare` được gọi **bên trong `ContextScope.with(a,...)` của caller**, nên capture `TraceContext("A","alpha")`. Nó trả một callable mới để khi worker chạy sẽ:
1. Save context trước đó (ở ví dụ là null).
2. Set context A.
3. Thực hiện business callable.
4. Restore null bằng `remove()` trong finally.

Kết quả:

~~~text
SAFE trace=A tenant=alpha
~~~

Như vậy, ngữ cảnh không được “copy thần kỳ” bởi ThreadLocal; **wrapper chủ động truyền qua ranh giới task**.

### 4.3 Giải thích nested: trả lại context ngoài

Bên trong task A, `ContextScope.with(b,...)` tạm thay A bằng B và gọi một đoạn logic. Khi đóng scope B, `finally` khôi phục A. Vì thế cùng một worker in:
- Before: A.
- Inner: B.
- After: A.

Điều này đặc biệt quan trọng khi advice trong một request cần tạm sử dụng một **scope quan sát phụ**, nhưng không được phép làm rò context phụ sang phần còn lại của request.

### 4.4 Giải thích error: cleanup vẫn xảy ra

Task lỗi ném `IllegalStateException("boom")`. `Future.get()` báo `ExecutionException` chứa lỗi gốc. Dù target ném lỗi, `ContextScope.with(...)` vẫn thực hiện `finally`.

Task tiếp theo trên cùng **single-thread executor worker** in `POST trace=none tenant=none`. Đây là bằng chứng kiểm tra cleanup trong demo. Nếu wrapper bỏ `finally`, task tiếp theo có thể vô tình thấy A.

### 4.5 Giải thích MAIN: caller cũng restore

`ContextScope.with(a,...)` trên caller đã restore giá trị trước đó sau khi submit mỗi task. Vì vậy sau toàn bộ demo, thread main không giữ A hoặc B:

~~~text
MAIN trace=none tenant=none
~~~

**Cả hai phía đều cần scope đúng:** worker cleanup không thay thế caller cleanup, và ngược lại.

### 4.6 Giới hạn của ví dụ

- Dùng **wrapper Java 17 thuần**, không dùng pointcut/weaver để tự động chọn mọi lệnh submit.
- Context chỉ gồm **dữ liệu giả**; không đưa access token/credential thật vào ThreadLocal.
- Không có propagation sang process khác hoặc qua network.
- `ExecutorService` chỉ giúp chạy tác vụ, không tự xử lý trách nhiệm security/tracing của ứng dụng.
- `shutdownNow()` ở cuối được gọi sau các `Future.get()` trong demo; trong production cần quản lý vòng đời executor theo chủ sở hữu của nó, không shutdown một pool dùng chung sau mỗi request.
- Ví dụ bắt `ExecutionException` để minh họa; các lỗi `InterruptedException`, `CancellationException` và tình huống task bị treo cần xử lý theo chính sách thực tế.
- Không giữ context qua toàn bộ thời gian của một chuỗi `CompletionStage` tự động; mỗi boundary tiếp tục cần lựa chọn propagation phù hợp.

## 5. Ranh giới AOP: intercept ở submission hay execution?

Có ít nhất hai thời điểm khác nhau:

**A. Scheduling/submission boundary:** caller gọi `submit(task)` và advice có thể capture **ngữ cảnh ở caller** rồi bọc `task`. Đây là thời điểm phù hợp để lấy snapshot.

**B. Worker execution boundary:** task bắt đầu chạy. Advice có thể cài snapshot vào worker, thực thi task và dọn context trong finally.

Nếu chỉ instrument `Runnable.run()` hoặc một method execution xảy ra **trên worker** mà không mang snapshot sang, advice chỉ thấy **context đang có của worker**, không tự biết context của caller nào đã submit task.

~~~text
Caller (T1)              Task queue              Worker (T2)
  context A
     |
     | capture A
     v
  wrap task(A)  ----------> queued  ----------> activate A
                                                  |
                                                  v
                                             business + advice
                                                  |
                                                  v
                                             restore previous
~~~

### 5.1 Mỗi loại join point cho ngữ cảnh khác nhau

Trong một số mô hình AOP, pointcut chọn method-call join point ở phía caller hoặc method-execution join point ở phía callee. Một proxy/wrapper cũng có thể chỉ nhận những lời gọi đi qua đối tượng trung gian.

Nếu lời gọi scheduling bypass proxy, advice đặt trên proxy submit **không được gọi**; nếu task chạy qua một đường không được instrument, execution advice cũng không tự xuất hiện. Cần kiểm tra đúng **tập join point được chọn và đường gọi thực tế**.

### 5.2 Không nhầm `this`, `target`, `args` với context đang truyền

`this` và `target` có ý nghĩa theo loại join point; `args` là tham số invocation. Một callable có thể capture một object target qua closure nhưng không có nghĩa mọi dữ liệu trong object đó là **execution context hợp lệ để chuyển sang thread khác**.

Giữ một reference target lớn trong task có thể:
- Kéo dài vòng đời object và tài nguyên.
- Giữ một session/context request đã đóng.
- Làm nhiều thread truy cập một object không thread-safe.
- Gây nhầm identity nếu thao tác chạy sau khi trạng thái target đã đổi.

Giới hạn context trong một **hợp đồng truyền riêng** thay vì coi mọi captured reference là "ngữ cảnh".

## 6. CompletableFuture và callback: mỗi stage là một boundary

Một luồng bất đồng bộ có thể:

~~~text
Caller
  |
  +--> supplyAsync(task1)
             |
             +--> thenApply(transform)
             |
             +--> thenApplyAsync(transform2, executorB)
             |
             +--> whenComplete(audit)
~~~

Không thể mặc định **mọi stage chạy trên cùng thread**:
- Stage không có hậu tố `Async` có thể được kích hoạt bởi thread hoàn tất stage trước hoặc thread gắn continuation theo hợp đồng API.
- Stage có hậu tố `Async` sử dụng một executor tương ứng hoặc default asynchronous execution facility.
- Thời điểm callback được gắn so với thời điểm stage trước hoàn thành có thể ảnh hưởng thread thực thi của callback.

Do đó **ThreadLocal hiện tại ở stage trước không tự thành context của stage tiếp theo**.

### 6.1 Cách tiếp cận với stage async

Ở mỗi continuation cần context:
1. **Capture** một snapshot được phê duyệt ở ranh giới lập stage.
2. Bọc lambda/callback để **install/restore** khi callback thực thi.
3. Truyền rõ executor nếu yêu cầu execution policy cụ thể.
4. Định nghĩa context của child task: kế thừa trace parent hay tạo span con?
5. Không chia sẻ mutable tracing state không đồng bộ giữa sibling branches.
6. Cleanup theo completion, error và cancellation.

Ví dụ ý tưởng:

~~~text
snapshot = captureAllowedContext()
stage = async(task)
stage2 = stage.thenApplyAsync(
    value -> withContext(snapshot, () -> transform(value)),
    chosenExecutor
)
~~~

Đây là **giả mã**, không phải một đoạn Java độc lập. Nếu dùng API Java có checked exceptions, adapter lambda phải map lỗi sang dạng phù hợp với `CompletionStage` (ví dụ `CompletionException`), không ném checked exception tùy tiện ra khỏi `Function`.

### 6.2 Không lấy context từ thread đăng ký callback một cách mù quáng

Giả sử callback được đăng ký **sau khi request ban đầu đã kết thúc**. Nếu lúc đó thread đang xử lý một request khác, việc “capture current ThreadLocal” tại callback registration có thể lấy **sai ngữ cảnh**. Context của stage nên được gắn với **logical operation** hoặc capture ở thời điểm chính xác đã định trong kiến trúc.

Ví dụ khi fan-out từ request A sang ba task, nên có snapshot A rõ ràng để từng child tạo span con riêng (nếu tracing model yêu cầu), thay vì đọc `ThreadLocal` của một worker bất kỳ vào lúc nó nhận việc.

### 6.3 Trả Future khác với hoàn thành công việc

Một method có thể trả `CompletionStage` rất nhanh, trong khi nghiệp vụ vẫn chạy. Với một around advice:

~~~text
before
result = proceed()   // trả stage
after-returning       // chạy khi stage đã được trả
~~~

`after-returning` trong phạm vi **lời gọi trả stage** chưa chắc ghi nhận **thành công cuối của tác vụ**. Muốn kết luận thành công/lỗi của async operation, phải quan sát **completion** và map đúng exception/cancellation.

Không giữ `startTime` hoặc `lastFuture` trong shared aspect field rồi chờ task, vì các request đồng thời có thể ghi đè. Dùng local/immutable operation context hoặc state keyed theo logical operation có vòng đời được quản lý.

## 7. Fan-out, fan-in và ngữ cảnh cha/con

Một request có thể phát nhiều child tasks:

~~~text
Request A [trace A]
      |
      +--> Task A1 [parent A, child span 1]
      +--> Task A2 [parent A, child span 2]
      +--> Task A3 [parent A, child span 3]
      |
      v
   join results
~~~

Các child có thể chia sẻ **trace correlation** của parent, nhưng không có nghĩa được dùng chung **một span mutable đang ghi** hoặc **một biến `currentChild`** của shared aspect. Mỗi child nên có context riêng theo ngữ nghĩa tracing, trong đó liên kết parent/child rõ ràng.

Ở bước fan-in, cần quyết định:
- Context của phép kết hợp là context parent hay tạo span mới?
- Phép tính có tiếp tục khi một child lỗi hoặc bị hủy không?
- Nếu một child hoàn tất sau timeout, có được phát event nữa không?
- Error aggregation là một lỗi chung hay danh sách lỗi con?
- Ai chịu trách nhiệm cleanup state thuộc request parent?

**Dùng correlation ID không giải quyết mọi câu hỏi trên.** Nó chỉ hỗ trợ nối các sự kiện; không cho phép một child tự sử dụng identity hoặc quyền của parent ngoài hợp đồng.

## 8. Ngữ cảnh tại ranh giới dịch vụ từ xa

Truyền context qua **network/process boundary** khác với copy `ThreadLocal`:
- Phải serialize những metadata được phép vào giao thức.
- Bên nhận cần validate format, version, kích thước và nguồn dữ liệu.
- Trace context bên ngoài có thể bị client giả mạo; không dùng nó như token xác thực.
- Authentication/authorization phải dựa vào cơ chế tin cậy độc lập.
- Không tự truyền raw access token, session object hoặc dữ liệu cá nhân chỉ vì caller có chúng trong context.
- Bên nhận phải áp dụng chính sách tenant và quyền theo dữ liệu đáng tin.

Một nguyên tắc hữu ích: **propagate correlation, revalidate authority**. Nếu một identity đã được xác thực và được phép ủy quyền, phải dùng cơ chế ủy quyền chính thức có ràng buộc audience/scope/expiry, không thay thế bằng chuỗi tenant ID do trace gửi đến.

### 8.1 Baggage/metadata phải có ngân sách

Một số hệ thống truyền nhiều field phụ trong context. Nếu chấp nhận mọi field tùy ý, dữ liệu có thể tăng không giới hạn qua các hop, làm nặng request hoặc vô tình mang bí mật đi xa. Nên:
- Allowlist key.
- Giới hạn tổng kích thước và độ dài value.
- Dùng encoding hợp lệ.
- Không truyền object graph, stack trace, response body tùy tiện.
- Định nghĩa TTL/hop budget nếu nghiệp vụ cần.
- Có chiến lược bỏ những field không quan trọng khi vượt ngân sách.

## 9. Thiết kế API để không phụ thuộc ngầm vào ThreadLocal

Một cách dễ kiểm thử là truyền context **tường minh**:

~~~java
// MẢNH MINH HỌA, không phải chương trình chạy độc lập.
record OperationContext(String traceId, String tenantId) {}

interface AsyncOperation {
    java.util.concurrent.CompletionStage<String> run(
            OperationContext context, String requestId);
}
~~~

Ưu điểm:
- Caller nhìn thấy dependency context trong chữ ký.
- Không cần đoán worker thread.
- Mỗi child task có thể nhận snapshot riêng.
- Dễ viết test cho identity, deadline, trace propagation.
- Ít rủi ro thread pool giữ state của request trước.

Nhược điểm:
- Phải truyền tham số qua nhiều tầng.
- Một số thư viện/legacy API có chữ ký không thể thay.
- Code cần một abstraction để tránh passing context không cần thiết vào những domain functions thuần.

Khi AOP được dùng để giảm boilerplate, lời khuyên không phải "luôn truyền mọi context bằng tay", mà là **thiết kế contract ngữ cảnh rõ**, rồi chọn giữa explicit parameter, scoped context holder, wrapper, hoặc API hỗ trợ sao cho các boundary được quản lý nhất quán.

### 9.1 Context object nên bất biến

Java record là một lựa chọn tiện cho các field bất biến về mặt tham chiếu. Nhưng record chứa `List` hoặc `Map` mutable vẫn có thể bị sửa nội dung bên trong. Khi snapshot chứa collection, cần tạo **immutable copy** hoặc dùng kiểu bất biến theo hợp đồng.

Không giữ reference đến request body, stream, connection hoặc object có lifecycle ngắn trong context sống lâu. Điều này có thể gây leak, xử lý trên tài nguyên đã đóng, hoặc sử dụng dữ liệu sau khi quyền đã hết hạn.

## 10. Chính sách propagation theo từng loại concern

### 10.1 Tracing

- Capture trace context tại đúng operation boundary.
- Tạo parent/child span hợp lệ khi fan-out.
- Kết thúc span ở **async completion**, không khi trả future nếu đo toàn operation.
- Đảm bảo một span không được kết thúc hai lần do callback và timeout cạnh tranh.
- Tránh dùng global `currentSpan` field của shared aspect.

### 10.2 Logging

- Chỉ truyền một vài field an toàn như traceId, operationName.
- Không in raw `args`, token hoặc payload.
- Dọn logging context ở worker trong finally.
- Không để logging advice tự instrument chính sink của nó rồi tạo vòng recursion.
- Kết hợp với structured events và correlation ID để không dựa vào thứ tự dòng log giữa thread.

### 10.3 Authorization

- Không dùng traceId/tenantId từ context như bằng chứng đã xác thực.
- Nếu cần người đại diện để chạy tác vụ, sử dụng delegation model có quyền tối thiểu, expiry và revocation rõ.
- Revalidate quyền tại thời điểm tác vụ thực thi nếu chính sách yêu cầu.
- Nếu request kết thúc/cancel trước khi child chạy, xác định tác vụ có còn được phép tiếp tục không.
- Không để shared aspect field giữ `lastAuthorizedUser` rồi gán cho child task.

### 10.4 Transaction

- Transaction context có thể gắn với thread/resource và có vòng đời riêng.
- Không copy `ThreadLocal` transaction tùy tiện sang executor: connection, lock, transaction manager có thể không cho phép truy cập đa luồng như vậy.
- Cân nhắc ranh giới transaction **mới** cho async task hoặc hợp đồng propagation được nền tảng hỗ trợ.
- Định nghĩa rollback/error khi parent/child hoàn tất khác thời điểm.

### 10.5 Retry, deadline và cancellation

- Correlation context có thể được dùng lại qua retry, nhưng mỗi attempt cần event/ID phân biệt.
- Một deadline có thể được truyền dưới dạng thời điểm tuyệt đối hoặc budget có quy tắc; tránh reset deadline ở mỗi hop khiến request chạy mãi.
- Propagation context không tự hủy task. Cần cancellation signal, xử lý interruption và cleanup phù hợp.
- Nếu retry task trong worker khác, phải đảm bảo **cùng logical operation context hợp lệ** được dùng, không capture ngẫu nhiên thread local của worker retry.

## 11. Lỗi thường gặp khi triển khai

| Triệu chứng | Nguyên nhân khả dĩ | Cách xác minh | Hướng sửa |
| --- | --- | --- | --- |
| Task async mất trace ID | Không capture ở submission | So sánh caller vs worker context | Bọc task bằng snapshot hợp lệ |
| Task B mang trace của A | Quên restore/clear ThreadLocal | Chạy hai task nối tiếp trên cùng pool worker | `finally` restore previous/remove |
| Nested task mất parent context | Luôn `remove()` khi thoát scope con | Kiểm tra before/inner/after | Save/restore previous |
| Một child mang tenant sai | Dùng shared mutable field | Concurrent test fan-out | Immutable context per invocation |
| Context còn trên caller sau submit | Caller cài nhưng không restore | Assert context sau khi gửi task | Dùng scope `try/finally` |
| Trace kết thúc sớm | After-returning lúc trả stage | Đo thời điểm stage completion | Hook completion phù hợp |
| Callback stage tiếp theo mất context | Chưa bọc continuation mới | Kiểm tra từng stage boundary | Propagate cho mỗi callback cần |
| Ngữ cảnh cũ sau timeout | Task chạy tiếp hoặc cleanup thiếu | Test timeout/cancel/interrupt | Cancellation policy và finally |
| Log nhầm người dùng | Identity lấy từ trace metadata | Security test nhiều tenant | Dùng trusted authorization source |
| Tăng RAM sau tải cao | Context giữ request/target lớn | Heap analysis/retention | Minimal immutable fields, release |
| Context từ luồng khác không thấy | ThreadLocal không xuyên executor | Task raw vs wrapped | Explicit wrapper/context API |
| Số lần gọi advice sai | Proxy bypass hoặc nested advice | Trace submission/execution join points | Kiểm tra actual interception scope |
| Async error không được ghi | Chỉ có after-returning đồng bộ | Test failing future/stage | Theo dõi completion/error |
| Request context bị trộn | Capture reference mutable | Stress test xen kẽ | Snapshot bất biến |
| Điều kiện security đã hết hiệu lực | Snapshot quyền quá lâu | Test revoke trong hàng đợi | Revalidate khi execute nếu cần |

## 12. Kiểm thử propagation một cách có chứng cứ

Không đủ để assert “từng có dòng trace A trong log”. Hãy phân biệt **đúng boundary, đúng scope, đúng lifetime và không rò sang task khác**.

| Trường hợp | Kết quả kỳ vọng |
| --- | --- |
| Caller có context A, raw task trên pool | Không mặc định có A |
| Caller có A, wrapped task | Worker thấy A trong task |
| Caller đổi sang B sau scheduling A | Task A vẫn dùng snapshot A nếu thiết kế vậy |
| Hai child A1/A2 song song | Mỗi child giữ context riêng đúng parent |
| Nested scope B trong task A | Sau B phải restore A |
| Task ném exception | Worker cleanup và caller thấy lỗi nguyên nhân |
| Task bị cancel trước khi chạy | Không cài context trên worker |
| Task bị cancel lúc đang chạy | Cleanup khi task thực sự thoát scope |
| Pool tái sử dụng worker | Task không context không thấy context cũ |
| `CompletionStage` chain | Mỗi continuation cần context có chủ đích |
| Retry ở worker khác | Giữ correlation hợp lệ, phân biệt attempts |
| Deadline hết hạn | Không tiếp tục hành động trái policy |
| Multi-tenant | Không lẫn identity/tenant giữa các request |
| Sensitive context | Không truyền token/secret không cần thiết |
| Logging sink lỗi | Không gây recursion và rò context |
| Parent request kết thúc | Child có ownership/lifetime rõ |
| Direct/proxy calls | Biết lời gọi nào thực sự có advice |

### 12.1 Kiểm thử thứ tự không cần dựa vào scheduler

Dùng `Future.get()` hoặc cơ chế phối hợp task giúp assert **kết quả sau khi hoàn tất**, không đòi log từ T1/T2 phải in cùng thứ tự ở mọi máy. Với concurrency test, chú trọng:
- Giá trị context tại worker đúng với snapshot lúc submission.
- Không có context sau khi rời scope.
- Không có cross-request contamination.
- Những bất biến concurrency/security luôn đúng.

### 12.2 Thử trường hợp thất bại, không chỉ happy path

Một số bug chỉ xuất hiện ở nhánh lỗi:
- Callback làm exception rồi quên clear.
- Task bị interrupt khi đang chờ I/O.
- Retry tạo task mới từ worker nhưng không mang context đúng.
- Logging sink ném lỗi làm advice bỏ qua `finally` không đúng.
- Async stage hoàn tất exceptional nhưng audit chỉ gắn vào success path.

Đặt cleanup trong `finally` của phạm vi đã cài context, không chỉ ở sau lệnh `return` của nhánh thành công.

## 13. Reentrancy, pointcut và scope của advice

Một aspect có thể tự kích hoạt khi code truyền context gọi API helper cũng bị pointcut chọn. Ví dụ:

~~~text
Business call
    -> Advice captures context
        -> Logger/TaskWrapper helper
            -> Pointcut cũng chọn helper
                -> Advice captures context again
                    -> ...
~~~

Đây là vấn đề **reentrancy/cycle**, không phải do ThreadLocal thiếu visibility. Sửa bằng:
- Chọn pointcut có phạm vi nghiệp vụ rõ.
- Loại helper/infrastructure nội bộ khỏi concern quan sát chung khi được phép.
- Tách kênh event/sink theo dependency một chiều.
- Không tạo wrapper lặp không có điều kiện dừng.
- Kiểm thử recursion/stack depth, không cố chữa bằng global boolean dùng chung.

Nếu phải dùng reentrancy guard, chọn scope đúng, restore trong finally và **không dùng để bỏ qua security/audit bắt buộc**.

### 13.1 Bỏ qua proxy khác với mất context

Khi caller giữ target gốc và gọi trực tiếp, advice trên proxy có thể không chạy. Khi advice có chạy nhưng task được gửi không bọc, worker có thể mất context. Hai nguyên nhân khác nhau:
1. **Advice không được kích hoạt tại boundary.**
2. **Advice đã kích hoạt nhưng không thực hiện propagation tới boundary tiếp theo.**

Muốn chẩn đoán chính xác, ghi sự kiện tối thiểu tại **submission intercept** và **worker execution**; không chỉ xem log nghiệp vụ của một task.

## 14. An toàn đa luồng của shared aspect

Một aspect instance dùng chung có thể đồng thời chuẩn bị tasks của request A/B. Sai lầm:

~~~text
sharedAspect.currentContext = contextA
submit(taskA)

sharedAspect.currentContext = contextB
submit(taskB)

taskA reads sharedAspect.currentContext  // có thể thành B
~~~

Không dùng field mutable chung làm chỗ giữ request-specific snapshot. Một wrapper `Callable` được tạo cho từng submission và capture **final/effectively final reference tới immutable snapshot** là cách đơn giản, tách A/B tự nhiên.

Nếu context chứa mutable map dù reference final, cả hai child vẫn có thể sửa nó. Dùng immutable copy hoặc xác định synchronization/ownership.

### 14.1 Policy cập nhật lúc request đang chạy

Cấu hình AOP có thể đổi động. Snapshot context A tại submission không tự bao gồm policy mới nhất khi task chạy sau một giờ. Phải xác định:
- Field nào được snapshot để giữ consistency **một logical operation**.
- Field nào phải **đọc lại khi execute** vì quyền, rate limit hoặc safety.
- Có cần policy version hoặc expiry trong context không?
- Nếu operation bị revoke, tác vụ queued có phải bị hủy?

Đây là quyết định **ngữ nghĩa nghiệp vụ**, không phải chỉ chọn `AtomicReference` hay ThreadLocal.

### 14.2 Nhiều process

ThreadLocal và object context trong JVM không đi qua process/network. Với message queue, phải serialize metadata tối thiểu trong envelope, validate ở consumer và kết hợp trusted authentication theo giao thức. Đừng coi trace baggage do người gửi tự khai là authority.

## 15. Hiệu năng và vòng đời

Mỗi task wrapper/context snapshot có chi phí:
- Tạo wrapper và snapshot.
- Set/remove ThreadLocal hoặc cài context theo cơ chế tương đương.
- Quản lý queue, callback, retention.
- Gắn span/event tại các boundary.
- Tăng nguy cơ memory leak nếu closure giữ object lớn.

Thường chi phí này nhỏ so với I/O của tác vụ, nhưng không thể khẳng định “miễn phí”. Với rất nhiều tasks nhỏ, nên đo overhead thực tế và chỉ propagate **field cần thiết**.

Một tác vụ queued có thể nằm rất lâu trước khi thực thi. Nếu closure giữ reference tới request object, response stream, user entity lớn hoặc session, request không được GC đúng lúc. Thay vào đó lưu **ID/context nhỏ có TTL và ownership rõ**, đóng resource riêng trước khi trả về.

### 15.1 Context lifetime khác task lifetime

Có các thời điểm:
1. Request nhận vào.
2. Tác vụ được scheduling.
3. Caller request trả response.
4. Tác vụ mới bắt đầu chạy.
5. Tác vụ hoàn tất/hủy.

Nếu task chạy ở (4) sau (3), vẫn cần biết nó có quyền làm việc **sau khi request hoàn tất** không. Với audit/notification nền có ownership riêng, điều đó có thể hợp lệ; với thao tác yêu cầu transaction/request-bound resource, có thể không. Context propagation là cơ chế truyền dữ liệu, **không quyết định chính sách sống còn của tác vụ**.

## 16. Những ngộ nhận cần tránh

1. **“ThreadLocal của caller tự sang mọi executor worker.”** Không.
2. **“Một request luôn chạy trên một thread.”** Async có thể chuyển thread.
3. **“Thread pool luôn tạo thread mới cho request.”** Pool thường tái sử dụng thread.
4. **“Chỉ set context trên worker, không cần clear.”** Có nguy cơ rò sang task tiếp theo.
5. **“Clear về null là đủ cho mọi scope.”** Nested scope cần restore previous.
6. **“Copy toàn bộ request object là nhanh nhất.”** Có thể vi phạm lifetime, thread safety và privacy.
7. **“Trace ID chứng minh user đã đăng nhập.”** Không, đây là metadata tương quan.
8. **“Tenant ID từ baggage đủ để phân quyền.”** Không, cần trusted identity/policy.
9. **“Bọc task đầu tiên là mọi callback đều có context.”** Không, mỗi async stage có boundary riêng.
10. **“After-returning của method trả Future báo tác vụ đã thành công.”** Không mặc định.
11. **“`cancel(true)` luôn dừng task tức thì.”** Không bảo đảm.
12. **“`InheritableThreadLocal` giải quyết thread pools.”** Không phù hợp mặc định với worker tái sử dụng và lifecycle riêng.
13. **“Stateless aspect không cần lo context.”** Nó vẫn có thể gọi dependency cần dữ liệu của invocation.
14. **“Đặt context vào shared aspect field để child đọc.”** Có race/cross-request contamination.
15. **“Mọi async lỗi đều xuất hiện ở thread submit.”** Thường được biểu diễn qua Future/Stage completion.
16. **“Cứ truyền transaction connection qua thread khác.”** Không an toàn nếu thiếu hợp đồng transaction/resource cụ thể.
17. **“Context truyền tới worker đồng nghĩa được cấp quyền.”** Propagation không phải authorization.
18. **“Chỉ cần log thấy một trace ID là chứng minh đúng.”** Phải kiểm tra trace đúng request và không rò sang task khác.
19. **“Dùng `finally` ở caller đủ dọn worker.”** Hai thread có scopes độc lập.
20. **“Tăng context fields không có chi phí.”** Có overhead, retention và nguy cơ truyền bí mật.

## 17. Checklist triển khai AOP async propagation

- [ ] Xác định những **join point** và boundary thật cần capture/activate context.
- [ ] Phân biệt **submission** và **execution** trên thread khác.
- [ ] Thiết kế **minimal immutable context** với field được phê duyệt.
- [ ] Không coi correlation ID là security identity.
- [ ] Capture ở thời điểm scheduling đúng logical operation.
- [ ] Bọc `Runnable`/`Callable`/callback/stage phù hợp; không giả định tự truyền.
- [ ] Set context tại worker và restore previous trong `finally`.
- [ ] Xóa ThreadLocal khi không có previous; tránh thread pool leak.
- [ ] Kiểm tra nested scope, fan-out/fan-in và callback ở thread khác.
- [ ] Định nghĩa outcome của Future/Stage tách với return đồng bộ.
- [ ] Kiểm tra ngoại lệ, cancellation, timeout, retry, interruption.
- [ ] Không truyền transaction/resource thread-bound khi chưa được hỗ trợ.
- [ ] Với identity/tenant, lấy từ nguồn đáng tin và kiểm tra policy khi cần.
- [ ] Có expiry, revocation và ownership cho queued/background task.
- [ ] Chỉ serialize metadata được phép khi qua network/message queue.
- [ ] Test worker reuse: task không context không thấy dữ liệu task trước.
- [ ] Tránh shared mutable currentContext field trong aspect.
- [ ] Kiểm tra pointcut không tự chọn helper/sink gây reentrancy.
- [ ] Giới hạn baggage, size, retention và dữ liệu nhạy cảm.
- [ ] Test coverage: task qua proxy/advice và direct/bypass paths.
- [ ] Đo chi phí context wrappers, logging, metrics và memory.
- [ ] Viết regression test cho đúng context của từng invocation.

## 18. Bài tập tự kiểm tra

**Câu 1.** Caller đặt trace A trong ThreadLocal, submit một Callable thô vào thread pool. Worker có chắc thấy A không?

**Đáp:** không. ThreadLocal thuộc thread; muốn worker thấy A phải propagate qua boundary bằng cơ chế được thiết kế.

**Câu 2.** Task A cài context A lên worker rồi ném ngoại lệ. Nếu không cleanup, task B trên cùng worker có thể gặp gì?

**Đáp:** B có thể thấy context A còn sót, gây sai tracing hoặc lộ thông tin giữa request. Cần restore/remove trong finally.

**Câu 3.** Tại sao phải capture lúc submit thay vì đọc ThreadLocal khi worker bắt đầu?

**Đáp:** lúc worker bắt đầu, context trong thread worker có thể null hoặc thuộc task khác; snapshot của logical operation phải được chuyển từ caller qua task wrapper/metadata.

**Câu 4.** Vì sao `restore previous` an toàn hơn luôn `remove` cho nested scope?

**Đáp:** nếu một scope ngoài đang có context P, scope con A phải trả quyền điều khiển với context P; remove sẽ xóa cả ngữ cảnh hợp lệ của scope ngoài.

**Câu 5.** Có thể dùng traceId làm khóa kiểm tra quyền thay cho principal được xác thực không?

**Đáp:** không. TraceId là metadata tương quan, có thể bị giả mạo. Authorization phải dựa vào nguồn nhận dạng và policy đáng tin.

**Câu 6.** Một method AOP trả `CompletionStage`; `after-returning` của lời gọi chạy ngay khi trả stage. Nó đã đo đúng thời điểm task hoàn tất chưa?

**Đáp:** chưa chắc. Muốn đo completion phải gắn quan sát vào điểm kết thúc stage, phân biệt success, exception và cancellation.

**Câu 7.** Nếu một task gốc fan-out sang ba tasks trên executor khác, chỉ truyền context ở task gốc có đủ không?

**Đáp:** không mặc định. Cần cơ chế propagation có chủ đích tại từng boundary cần context, tạo ngữ nghĩa parent/child phù hợp.

**Câu 8.** Có nên gửi toàn bộ access token và raw request object vào tracing baggage qua hệ thống từ xa?

**Đáp:** không. Chỉ truyền metadata tối thiểu theo allowlist; authority phải được xác minh bằng giao thức bảo mật riêng.

**Câu 9.** Trong Java demo, tại sao `POST` in `trace=none` ngay sau task lỗi?

**Đáp:** `ContextScope.with` dọn context trong `finally`, nên worker không giữ A cho task kế tiếp dù task trước ném lỗi.

**Câu 10.** Một shared aspect dùng field `currentContext` để các task đọc sau. Tại sao nguy hiểm?

**Đáp:** nhiều invocations concurrent có thể ghi đè field; task A đọc context B. Capture snapshot riêng và immutable cho từng submission.

## 19. Kết luận

**Truyền ngữ cảnh qua tác vụ bất đồng bộ có kiểm soát** là bài toán **phạm vi và vòng đời của dữ liệu theo logical operation**, không chỉ là “đặt ThreadLocal”.

Một thiết kế đúng phải:
1. **Capture** dữ liệu tối thiểu, đã được phép, tại ranh giới scheduling.
2. **Propagate** snapshot qua task/callback bằng hợp đồng rõ ràng.
3. **Activate** ở worker khi tác vụ thực sự chạy.
4. **Restore/clear** chính xác trong `finally`, kể cả nested và exception paths.
5. **Phân biệt tracing với security**, đồng bộ với bất đồng bộ, stage return với completion, local thread với process khác.
6. **Kiểm thử không rò ngữ cảnh**, cùng với correctness, authorization, timeout, cancellation và retention.

Ghi nhớ: **AOP giúp tách concern truyền context ra khỏi nghiệp vụ**, nhưng chỉ an toàn khi mọi boundary được chọn và mọi dữ liệu được truyền **có scope, ownership, quyền truy cập và cơ chế cleanup rõ ràng**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-concurrency-007">Khóa trong advice và nguy cơ deadlock hoặc tăng tranh chấp</a>

<details>
<summary>Xem chi tiết</summary>

# Khóa trong advice và nguy cơ deadlock hoặc tăng tranh chấp

## 1. Khi concern cắt ngang vô tình trở thành nút thắt

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), **pointcut** chọn các *join point* mà công cụ cho phép can thiệp; **advice** bổ sung hành vi tại các điểm ấy, chẳng hạn logging, audit, metrics, phân quyền, retry, cache hoặc quản lý tài nguyên. Một aspect có thể được áp cho rất nhiều target methods và nhiều threads đồng thời. Vì vậy, chỉ một thao tác khóa tưởng như nhỏ trong advice cũng có thể trở thành **điểm nghẽn của cả ứng dụng**.

Ví dụ một around advice đo thời gian nhưng sử dụng **một khóa dùng chung**:

~~~text
Request A ----> [LOCK global] ---> advice.before
                                   |
                                   +-- proceed() ----> Target A
                                   |                   (gọi DB/HTTP, mất 500 ms)
                                   |
                                   +-- advice.after ---> [UNLOCK]

Request B ----> [WAIT global] ------------------------> chỉ được vào sau A
Request C ----> [WAIT global] ------------------------> chờ sau B ...
~~~

Nếu giữ khóa **xuyên suốt lời gọi `proceed()`**, mọi hoạt động mục tiêu — kể cả I/O hoặc lời gọi tới aspect khác — trở thành **một phần của critical section**. Thời gian nắm giữ khóa không còn chỉ là thời gian cập nhật biến đếm; nó phụ thuộc thời gian của toàn bộ nghiệp vụ.

Hai hậu quả lớn:

1. **Contention (tranh chấp):** nhiều requests cùng cần khóa nên phải chờ, throughput giảm và tail latency tăng.
2. **Deadlock (bế tắc):** hai hoặc nhiều threads giữ khóa/tài nguyên khác nhau rồi chờ vòng tròn; nếu không có cơ chế timeout/interrupt/recovery tương ứng, tiến trình xử lý có thể đứng mãi.

Ngoài ra còn có **starvation (đói tài nguyên)**, **priority inversion**, lock convoys và các failure modes liên quan shutdown/async. Bài học phân tích chúng trong ngữ cảnh **khóa được sử dụng bên trong advice hoặc interceptor**, không giới hạn ở một công nghệ AOP cụ thể.

**Nguyên tắc quan trọng:** thiết kế advice nên coi `proceed()` là **mã không nằm dưới quyền kiểm soát trực tiếp của advice**. Nếu giữ một lock qua `proceed()`, advice đang giữ lock trong khi gọi một thành phần có thể gọi lại, chờ I/O, lấy lock khác, ném ngoại lệ hoặc chuyển việc sang thread khác.

## 2. Các thuật ngữ và sự khác biệt

| Khái niệm | Ý nghĩa chính | Dấu hiệu |
| --- | --- | --- |
| **Lock / mutex** | Điều phối quyền vào critical section của một tài nguyên | Một thread giữ, thread khác phải đợi hoặc bị từ chối |
| **Critical section** | Đoạn code phải được bảo vệ vì truy cập trạng thái chia sẻ | Đọc-sửa-ghi cấu trúc mutable |
| **Lock scope** | Phạm vi thời gian từ acquire đến release | Scope càng rộng, càng dễ contention |
| **Lock ownership** | Thread/ngữ cảnh đang chịu trách nhiệm release | Phải rõ ai nắm và ai mở |
| **Contention** | Nhiều bên cạnh tranh cùng một lock | BLOCKED/WAITING, queue, p95/p99 tăng |
| **Deadlock** | Các bên mắc kẹt trong chu trình chờ tài nguyên | Không có progress dù công việc còn |
| **Starvation** | Một bên không được phục vụ đủ lâu vì scheduling hoặc cạnh tranh | Một số requests trễ bất thường |
| **Priority inversion** | Bên ưu tiên cao bị kẹt sau bên ưu tiên thấp đang giữ tài nguyên | Tính phản hồi trái kỳ vọng |
| **Reentrancy** | Cùng thread có thể lấy lại cùng lock hỗ trợ tái nhập | Tránh self-deadlock một loại, không chữa lock inversion |
| **Lock ordering** | Quy tắc thứ tự nhất quán khi cần nhiều locks | Luôn lấy A trước B |
| **Try-lock / timed acquire** | Không chờ vô hạn; thất bại/timeout nếu không lấy được lock | Giúp tránh chờ không kiểm soát |
| **Lock convoy** | Nhiều threads nối hàng sau một holder chậm | Latency tăng dây chuyền |
| **Happens-before** | Quan hệ bộ nhớ theo Java Memory Model | Giúp thấy write, không đồng nghĩa deadlock-free |

### 2.1. Contention không phải deadlock

Nếu A đang giữ khóa 50 ms, B chờ 50 ms rồi tiếp tục, đó là contention bình thường. Nếu A giữ khóa X chờ Y, còn B giữ Y chờ X, cả hai có thể không bao giờ đi tiếp: đó là deadlock thực.

Một **timeout** có thể giải phóng hoặc báo lỗi cho một phía, nhưng nó không làm thuật toán tự đúng: retry ngay có thể tạo livelock hoặc làm tăng tải. Cần hợp đồng xử lý khi không lấy được khóa.

### 2.2. Starvation cũng khác deadlock

Một thread có thể chờ rất lâu vì các thread khác liên tục thắng lock dù ứng dụng vẫn có progress. Đây là **starvation**, không nhất thiết có vòng chờ bế tắc. `ReentrantLock` có tùy chọn fairness nhưng lock công bằng thường có đánh đổi về throughput; `tryLock()` không tham gia cùng mọi chính sách fairness như blocking acquisition.

### 2.3. Scope của khóa khác scope của aspect

Một aspect instance có thể là global/shared, per-target hoặc per-invocation; nhưng **một lock object cụ thể** mới quyết định ai cạnh tranh với ai. Hai aspects khác nhau có thể cùng dùng một lock; hai instances của cùng aspect có thể giữ các locks riêng. Ngược lại, một static lock có thể bao nhiều target trong cùng vùng classloader/runtime scope.

Không được suy từ “có một aspect definition” rằng “chỉ có một mutex toàn hệ thống”.

## 3. Cấu trúc advice rủi ro nhất: giữ khóa qua `proceed()`

Đoạn sau là **pseudocode Java**, không phải chương trình độc lập:

~~~java
Object around(Proceed next) throws Throwable {
    globalLock.lock();
    try {
        auditBefore();
        Object result = next.proceed();
        auditAfter(result);
        return result;
    } finally {
        globalLock.unlock();
    }
}
~~~

Trông có vẻ cẩn thận vì đã `unlock()` trong `finally`. Nó giải quyết việc **rò lock khi method ném lỗi bình thường**, nhưng **không** giải quyết vấn đề **nắm khóa quá lâu**.

Trong lúc giữ `globalLock`:

- `auditBefore()` có thể gọi một client logging chậm.
- `next.proceed()` có thể gọi DB, network hoặc chờ một lock khác.
- Một advice bên trong có thể thực hiện retry và gọi target nhiều lần.
- Target có thể gọi ngược vào code cần `globalLock` từ **thread khác**.
- Một callback bất đồng bộ có thể phải chờ `globalLock` trước khi đánh thức thread đang giữ nó.
- `auditAfter()` có thể gửi dữ liệu ra ngoài và làm kéo dài thời gian giữ khóa.

Vì vậy, **dùng `finally` là điều kiện cần để release đúng, không phải bằng chứng critical section đã thiết kế hợp lý**.

### 3.1. Phạm vi khóa nên đủ nhỏ để bảo vệ invariant

Một mẫu tốt hơn cho metrics đơn giản:

~~~java
long startNanos = System.nanoTime();
try {
    return next.proceed();
} finally {
    durationCounter.add(System.nanoTime() - startNanos);
}
~~~

Đây là **mã minh họa**: `next` và `durationCounter` cần được cung cấp; giả sử `durationCounter` là một cấu trúc cập nhật đồng thời phù hợp (ví dụ `LongAdder`). Thời điểm đo là **local variable theo invocation**, không cần lock giữ qua toàn target.

Nếu thật sự cần cập nhật shared state phức tạp, hãy **chỉ khóa khi đọc-sửa-ghi phần state đó**, rồi release **trước khi** gọi `proceed()` nếu hợp đồng nghiệp vụ cho phép. Không giải phóng sớm khi nó phá invariant cần bảo vệ; khi không thể làm hẹp scope, có thể phải đổi kiến trúc hoặc ownership thay vì chỉ xóa lock.

## 4. Lock-order inversion: cách deadlock phát sinh từ nhiều aspect

Giả sử hai concerns có khóa riêng:

- `Lock A` dùng để bảo vệ audit registry.
- `Lock B` dùng để bảo vệ cache metadata.

Hai đường gọi có thể tạo thứ tự đối nghịch:

~~~text
Thread T1 (call path X)        Thread T2 (call path Y)
----------------------         ----------------------
acquire A                      acquire B
proceed -> ask for B           proceed -> ask for A
WAIT for B                     WAIT for A
(holding A)                    (holding B)
~~~

Đây là **circular wait**. Không quan trọng khóa được đặt trong hai aspects riêng hay target; đường gọi tổng thể mới quyết định thứ tự thực.

### 4.1. Bốn điều kiện thường dùng để nhận diện deadlock

Bộ điều kiện Coffman mô tả deadlock tài nguyên có thể xảy ra khi cùng tồn tại:

1. **Mutual exclusion**: một tài nguyên không được chia sẻ đồng thời.
2. **Hold and wait**: đang giữ tài nguyên này trong khi chờ tài nguyên khác.
3. **No preemption**: tài nguyên không bị thu hồi cưỡng bức an toàn.
4. **Circular wait**: có chu trình bên giữ/chờ lẫn nhau.

Phá một điều kiện là một chiến lược phòng tránh; trong ứng dụng Java, chiến lược hay dùng là **thống nhất thứ tự khóa** hoặc **không giữ khóa trong lúc chờ lời gọi ngoài/lock khác**.

### 4.2. Thứ tự khóa nhất quán

Nếu mọi đường đi cần cả A và B đều làm:

~~~text
acquire A
  acquire B
    critical operation
  release B
release A
~~~

thì chu trình A→B và B→A bị loại bỏ **đối với chính hai khóa và mọi đường tuân thủ quy tắc này**.

Lưu ý: một quy tắc lock ordering **không tự phòng deadlock trên tài nguyên khác**, ví dụ DB locks hoặc thread pool, nếu các đường chờ đó vẫn có vòng. Cần xem toàn bộ đồ thị phụ thuộc ở mức hệ thống.

### 4.3. Trật tự advice có thể tạo trật tự khóa

Giả sử `AUDIT` bao `CACHE`:

~~~text
AUDIT: lock A
   proceed() -> CACHE: lock B
   CACHE: unlock B
AUDIT: unlock A
~~~

Đường này có thứ tự A→B. Nếu ở một join point khác `CACHE` đứng ngoài `AUDIT`, đường thứ hai có thể yêu cầu B→A và tạo deadlock khi chạy đồng thời.

Vì vậy **precedence của các aspects** không chỉ đổi thứ tự log; nó có thể đổi lock graph. Không được giả định source declaration order luôn là precedence; phải kiểm tra engine và cấu hình thứ tự thực sự.

## 5. Reentrancy: khóa tái nhập không có nghĩa an toàn mọi nơi

Java intrinsic monitor (qua `synchronized`) là **reentrant**. `ReentrantLock` cũng cho phép **cùng thread** nắm lại lock của mình, giữ một hold count rồi release tương ứng.

Ví dụ minh họa:

~~~java
synchronized void first() {
    second();
}
synchronized void second() {
    // Cùng monitor trên this: cùng thread có thể vào lại.
}
~~~

Đoạn này nằm trong ngữ cảnh instance class và dùng để minh họa cú pháp, không phải chương trình độc lập. Nếu `first()` và `second()` đều khóa trên `this` của **cùng object**, cùng thread có thể vào `second` khi còn giữ monitor.

Nhưng:

- **Reentrant không có nghĩa không deadlock:** T1 giữ A cần B; T2 giữ B cần A vẫn có thể kẹt.
- **Reentrant không có nghĩa không contention:** threads khác vẫn phải chờ holder.
- **Reentrant không có nghĩa trạng thái advice theo invocation tự an toàn:** một instance dùng chung có thể có field mutable bị recursion ghi đè.
- **Reentrant không sửa được callback khác thread:** nếu một thread giữ lock rồi chờ Future mà Future completion cần lock từ thread khác, callback có thể không chạy.
- **Reentrancy không chống lỗi đếm hold:** với `ReentrantLock`, mỗi lần lock thành công cần một lần unlock phù hợp.

Với recursion hoặc nested join points, ngoài lock cần kiểm tra **per-invocation context và ordering** để tránh advice tái nhập vô hạn.

## 6. Trong advice có nên dùng `synchronized` hay `ReentrantLock`?

| Thuộc tính | Intrinsic monitor (`synchronized`) | `ReentrantLock` |
| --- | --- | --- |
| Tái nhập trên cùng thread | Có | Có |
| Thả khi thoát critical section | JVM thả khi ra khỏi monitor block/method | Người viết phải gọi `unlock` trong `finally` |
| Timed lock acquisition | Không có tham số timeout trực tiếp cho `synchronized` | Có `tryLock(timeout, unit)` |
| Lock interruptibly | Không hỗ trợ interrupt blocking monitor acquisition theo cách `lockInterruptibly` | Có `lockInterruptibly()` |
| Nonblocking attempt | Không có `tryMonitorEnter` trong API Java thông thường | Có `tryLock()` |
| Fairness option | Không đặt fairness bằng tham số monitor | `ReentrantLock(boolean fair)` |
| Condition variables | `wait/notify/notifyAll` trên monitor | `newCondition()` với `await/signal` |
| Diagnostic/metrics | Có trạng thái monitor trong thread dumps | Ownable synchronizers có thể được JVM tooling quan sát |
| Nguy cơ thiết kế | Hold quá rộng, lock order inversion, deadlock | Tương tự, cộng thêm lỗi quên `unlock` |

**Không có lựa chọn nào tự tốt hơn trong mọi tình huống.** Nếu một counter dùng `LongAdder` hay `AtomicLong` là đủ, mutex còn có thể không cần. Nếu cần một invariant nhiều fields, mutex hoặc một cấu trúc immutable publication có thể thích hợp hơn.

### 6.1. `tryLock()` không đồng nghĩa giữ an toàn tự động

~~~java
if (!lock.tryLock()) {
    throw new IllegalStateException("busy");
}
try {
    updateProtectedState();
} finally {
    lock.unlock();
}
~~~

Đây là **pseudocode Java không độc lập** vì `lock` và `updateProtectedState()` chưa được định nghĩa. Nó cho thấy nonblocking acquisition, nhưng việc **thất bại lấy lock** là một nhánh chức năng mới. Với thao tác tiền tệ hay quyền truy cập, không thể trả success giả hoặc bỏ update vì `tryLock()` thất bại; cần chính sách rõ: retry, queue, error/busy response hoặc một cách tổ chức khác.

### 6.2. Timed lock acquisition

~~~java
boolean acquired = lock.tryLock(200, TimeUnit.MILLISECONDS);
if (!acquired) {
    throw new TimeoutException("lock acquisition timed out");
}
try {
    updateProtectedState();
} finally {
    lock.unlock();
}
~~~

Mã là **minh họa không độc lập**, cần imports, `lock` và method body phù hợp. `tryLock(timeout, unit)` có thể ném `InterruptedException`, vì vậy một API chứa đoạn này phải khai báo hoặc xử lý; `TimeoutException` trong `java.util.concurrent` cũng là checked exception.

Timeout giúp **giới hạn thời gian chờ** trong một đường code nhưng **không chứng minh thao tác đã/ chưa commit** ở những tài nguyên khác. Đừng timeout rồi gọi lại một operation có side effects mà không biết trạng thái.

## 7. Bốn nguy cơ khi khóa được lắp vào concern cắt ngang

### 7.1. Tạo khóa global ngoài ý định

Một metrics advice có thể được gắn vào mọi repository/service method. Nếu tất cả dùng cùng `static synchronized` method để ghi sample, hàng trăm đường gọi độc lập trở thành một queue cho một monitor.

**Hệ quả:** throughput của toàn ứng dụng phụ thuộc một critical section, dù nghiệp vụ thực tế có thể xử lý song song.

Giải pháp có thể là **LongAdder**, per-thread/per-core buffering, batch export, hoặc một worker tiếp nhận events qua queue. Phải đánh giá độ chính xác của counters, backpressure và memory, không mặc định queue luôn tốt hơn.

### 7.2. Giữ lock khi làm I/O

Bên trong advice có thể ghi log đến disk, gửi HTTP telemetry hoặc gọi một secret provider. I/O có tail latency rất lớn; nếu còn giữ lock, các request khác phải chờ theo thời gian của call chậm nhất.

Giải pháp: snapshot dữ liệu tối thiểu dưới lock, release, sau đó thực hiện I/O nếu hợp đồng cho phép. Nếu cần bảo đảm thứ tự/atomicity giữa update và I/O, dùng cơ chế queue/outbox/sequence chuyên biệt chứ không chỉ mở rộng một Java mutex qua network.

### 7.3. Gọi callback hoặc `proceed()` trong lock

Callback có thể thực thi bất kỳ code đã đăng ký, lấy lock khác hoặc gọi quay về component ban đầu. `proceed()` còn có thể chứa toàn bộ aspect chain.

Vì vậy **khóa trước khi gọi code bên ngoài phạm vi kiểm soát** là một rủi ro lớn. Chỉ làm vậy khi đã phân tích toàn bộ lock graph, có yêu cầu nhất quán cụ thể và không tồn tại cách thiết kế hẹp hơn.

### 7.4. Khóa để “sửa” race nhưng làm nghẽn cả hệ thống

Một mutable field `startedAt` trên shared timing aspect có thể bị threads ghi đè. Bọc **toàn target** trong khóa đúng là làm hai calls không xen kẽ, nhưng cũng phá concurrency của target.

Giải pháp đúng về scope là **lưu mốc thời gian trong local variable per invocation**. Cần sửa mô hình trạng thái trước khi tăng đồng bộ hóa cưỡng bức.

## 8. Ví dụ Java 17 hoàn chỉnh: contention, lock inversion và giải pháp

Ví dụ này dùng **JDK thuần**, không sửa bytecode và không tự nhận là một AOP engine. Hàm `lockedAdvice` đóng vai một around wrapper để minh họa vì sao nắm khóa xuyên `next.call()` có hại.

Bốn kiểm thử:

1. **HELD_ACROSS_PROCEED:** thread A giữ `gate` khi `next.call()` chờ tín hiệu; thread chính thử lấy cùng `gate` bằng `tryLock` và bị từ chối. Đây là **contention có kiểm soát**.
2. **EXCEPTION:** `next.call()` ném `IllegalArgumentException`; `finally` vẫn release `gate`, caller nhận lỗi gốc.
3. **INVERSION:** hai threads giữ hai locks khác nhau, sau đó mỗi bên `tryLock` khóa đang bị bên kia giữ. Cả hai lần thử thất bại. Nếu thay `tryLock` bằng blocking `lock` ở chính hai vị trí, đường chờ sẽ có nguy cơ deadlock. Demo **không để hai threads thật sự deadlock**.
4. **ORDERED:** hai threads cùng lấy lock theo thứ tự trái→phải; cả hai hoàn tất mà không có lock-order inversion trong phép thử.

`CountDownLatch` buộc những điểm tương tranh diễn ra xác định để output không phụ thuộc việc `Thread.sleep()` may mắn tạo race. Lệnh `join(5000)` và chờ latch hữu hạn giúp test không treo vô hạn trong điều kiện chạy bình thường.

Tạo file `AdviceLockingDemo.java`:

~~~java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public final class AdviceLockingDemo {
    @FunctionalInterface
    interface Proceed { String call() throws Exception; }

    static String lockedAdvice(ReentrantLock gate, Proceed next)
            throws Exception {
        gate.lock();
        try {
            return next.call();
        } finally {
            gate.unlock();
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void await(CountDownLatch latch) throws InterruptedException {
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("latch timeout");
        }
    }

    static void inverseAttempt(ReentrantLock first, ReentrantLock second,
                               CountDownLatch firstHeld,
                               CountDownLatch bothAttempted,
                               AtomicInteger refused,
                               AtomicReference<Throwable> error) {
        first.lock();
        try {
            firstHeld.countDown();
            await(firstHeld);
            boolean acquired = second.tryLock();
            if (acquired) {
                second.unlock();
                throw new AssertionError("unexpected second lock acquisition");
            }
            refused.incrementAndGet();
            bothAttempted.countDown();
            await(bothAttempted);
        } catch (Throwable t) {
            error.compareAndSet(null, t);
        } finally {
            first.unlock();
        }
    }

    static void ordered(ReentrantLock first, ReentrantLock second,
                        AtomicInteger completed) {
        first.lock();
        try {
            second.lock();
            try {
                completed.incrementAndGet();
            } finally {
                second.unlock();
            }
        } finally {
            first.unlock();
        }
    }

    public static void main(String[] args) throws Exception {
        ReentrantLock gate = new ReentrantLock();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> workerError = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                result.set(lockedAdvice(gate, () -> {
                    inside.countDown();
                    await(release);
                    return "done";
                }));
            } catch (Throwable t) {
                workerError.set(t);
            }
        }, "locked-advice");
        worker.start();
        await(inside);
        boolean competitorBlocked = !gate.tryLock();
        if (!competitorBlocked) gate.unlock();
        release.countDown();
        worker.join(5000);
        require(!worker.isAlive(), "worker did not stop");
        require(workerError.get() == null, "worker failed");
        require(competitorBlocked && "done".equals(result.get()),
                "lock was not held across proceed");
        System.out.println("HELD_ACROSS_PROCEED competitorBlocked=true");

        String errorMessage;
        try {
            lockedAdvice(gate, () -> {
                throw new IllegalArgumentException("invalid");
            });
            throw new AssertionError("expected business exception");
        } catch (IllegalArgumentException ex) {
            errorMessage = ex.getMessage();
        }
        require(!gate.isLocked(), "lock leaked after exception");
        System.out.println("EXCEPTION lockReleased=true error=" + errorMessage);

        ReentrantLock left = new ReentrantLock();
        ReentrantLock right = new ReentrantLock();
        CountDownLatch bothHeld = new CountDownLatch(2);
        CountDownLatch bothTried = new CountDownLatch(2);
        AtomicInteger refused = new AtomicInteger();
        AtomicReference<Throwable> inversionError = new AtomicReference<>();
        Thread a = new Thread(() -> inverseAttempt(left, right, bothHeld,
                bothTried, refused, inversionError), "left-then-right");
        Thread b = new Thread(() -> inverseAttempt(right, left, bothHeld,
                bothTried, refused, inversionError), "right-then-left");
        a.start();
        b.start();
        a.join(5000);
        b.join(5000);
        require(!a.isAlive() && !b.isAlive(), "inversion threads stuck");
        require(inversionError.get() == null, "inversion test failed");
        require(refused.get() == 2, "expected two refusals");
        System.out.println("INVERSION tryLockFailures=2");

        AtomicInteger completed = new AtomicInteger();
        Thread c = new Thread(() -> ordered(left, right, completed));
        Thread d = new Thread(() -> ordered(left, right, completed));
        c.start();
        d.start();
        c.join(5000);
        d.join(5000);
        require(!c.isAlive() && !d.isAlive(), "ordered threads stuck");
        require(completed.get() == 2, "expected two ordered completions");
        System.out.println("ORDERED completed=2");
        System.out.println("ALL ASSERTIONS PASSED");
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 AdviceLockingDemo.java
java AdviceLockingDemo
~~~

Đầu ra đã được kiểm chứng bằng biên dịch và chạy thực:

~~~text
HELD_ACROSS_PROCEED competitorBlocked=true
EXCEPTION lockReleased=true error=invalid
INVERSION tryLockFailures=2
ORDERED completed=2
ALL ASSERTIONS PASSED
~~~

### 8.1. Tại sao `competitorBlocked=true`?

`lockedAdvice` giữ `gate` và gọi `next.call()`. Code target mô phỏng báo `inside.countDown()`, sau đó chờ `release`. Main thread đã biết worker ở **bên trong `proceed`** khi thử `gate.tryLock()`.

Vì lock vẫn thuộc worker, main thread không lấy được lock. Đây là phép kiểm trực tiếp việc một advice giữ lock trong lúc target bị chặn. Không cần gây deadlock để chứng minh contention: chỉ một target chậm đã đủ khiến đường khác chờ.

### 8.2. Vì sao exception không làm leak lock?

`lockedAdvice` dùng `try/finally`. Khi `next.call()` ném `IllegalArgumentException`, stack unwinding đi qua `finally { gate.unlock(); }`. Caller bắt được lỗi `"invalid"` và `gate.isLocked()` là false.

**Bài học:** `finally` quan trọng để không rò lock, nhưng không làm mất rủi ro giữ khóa quá rộng khi target chạy lâu.

### 8.3. Vì sao cả hai lần `tryLock` đều thất bại?

Thread A giữ `left`; B giữ `right`. `bothHeld` bảo đảm cả hai đã lấy khóa đầu tiên. Sau đó A thử `right`, B thử `left`, đều đang thuộc thread còn lại.

`bothTried` khiến mỗi thread **tiếp tục giữ khóa đầu tiên** cho đến khi cả hai đã thử khóa thứ hai; nhờ đó không có race làm một thread release sớm rồi thread khác bất ngờ lấy được lock.

`tryLock()` ở đây không chờ. Nó trả false, nên hai threads có thể dọn dẹp và thoát. Nếu dùng `second.lock()` để chờ không giới hạn ở cùng vị trí, có thể tạo vòng chờ thật.

### 8.4. `ORDERED completed=2` nghĩa là gì?

Cả hai threads gọi `ordered(left,right,completed)`, nghĩa là luôn lấy **left rồi right**. Thread có thể phải chờ `left` nhưng không tạo đường `right→left` trong hai worker này. Khi mỗi thread vào cả hai critical sections, counter tăng; cuối test bằng 2.

Không suy rộng kết quả này thành “mọi hệ thống sẽ deadlock-free”: còn callback, locks khác, DB locks hoặc asynchronous dependencies nằm ngoài hai khóa được test.

### 8.5. Giới hạn của demo

- Đây là code Java trực tiếp mô phỏng around wrapper, **không phải bytecode weaving** và không có pointcut engine.
- Nó cố ý **không tạo deadlock thật**. Dùng blocking `lock()` thay `tryLock()` trong thử nghiệm inversion có thể khiến cả tiến trình treo; không làm vậy trong test suite bình thường.
- Các timeouts 5 giây giúp test có ngưỡng thoát, không phải SLA latency sản phẩm.
- `AtomicReference` và `AtomicInteger` dùng để chuyển status/đếm giữa threads trong demo; chúng không bảo vệ mọi business invariant.
- Nội dung output được in bởi main sau các joins nên không phụ thuộc thứ tự scheduling của các threads.
- Test không đo throughput thực tế. Performance phải được benchmark riêng dưới tải đại diện.
- Với failure khiến thread ở lại sau `join(5000)`, test ném `AssertionError`; một test harness sản xuất nên có timeout/cleanup cấp process để tránh có worker không kết thúc do bug mới.

## 9. Rút ngắn critical section đúng cách

Sai lầm hay gặp:

~~~java
globalLock.lock();
try {
    long start = System.nanoTime();
    Object value = next.proceed();
    metrics.add(System.nanoTime() - start);
    return value;
} finally {
    globalLock.unlock();
}
~~~

Đây là pseudocode chưa độc lập. Khóa đã bọc `proceed`, khiến một tác vụ DB/HTTP chậm chặn mọi caller cần cùng lock.

Nếu metrics chỉ cần cập nhật counter độc lập:

~~~java
long start = System.nanoTime();
try {
    return next.proceed();
} finally {
    metrics.add(System.nanoTime() - start);
}
~~~

Ở đây giả định `metrics.add` có concurrency contract phù hợp. Không có lock giữ qua target; `start` chỉ thuộc một invocation.

Nếu update cần đọc-sửa-ghi nhiều field:

~~~text
1. Lấy dữ liệu đầu vào cần thiết
2. Acquire lock bảo vệ state chung
3. Update invariant nhanh, không I/O
4. Release lock trong finally
5. Gọi external observer hoặc proceed ngoài lock, nếu hợp đồng cho phép
~~~

Thứ tự trên **không phải luôn hoán đổi được**. Nếu business yêu cầu một lock bảo vệ toàn operation, phải xem xét **phân vùng state, transaction resource, message passing hoặc thiết kế lại API**. Không chỉ “đưa proceed ra ngoài lock” mà vô tình phá atomicity bảo mật hay dữ liệu.

### 9.1. Snapshot rồi xuất event

Một advice ghi audit có thể copy một **snapshot bất biến** của metadata dưới lock, sau đó release rồi gửi tới queue. Việc gửi queue có thể lỗi: cần policy backpressure/drop/retry và phân biệt “đã xếp hàng” với “đã giao thành công”.

### 9.2. Granularity: một khóa global hay khóa theo key?

Nếu các invocations liên quan nhiều target độc lập, một lock toàn cục gây contention không cần thiết. Khóa theo `accountId`, `tenantId` hoặc target identity có thể cho phép parallelism giữa các keys.

Nhưng keyed locks cũng có các nguy cơ:

- Không được tạo vô hạn locks trong registry mà không có eviction.
- Nếu một operation cần nhiều keys, phải áp dụng **thứ tự ổn định** cho toàn bộ keys.
- Eviction trong lúc có thread đang dùng có thể tạo **hai locks khác nhau cho cùng key**, phá mutual exclusion.
- Hash-striping giảm số lock objects nhưng có thể gây **false contention** khi hai keys trùng stripe.
- Key phải chuẩn hóa và không chứa dữ liệu nhạy cảm trong logs.
- Với nhiều JVM nodes, per-key lock trong RAM không bảo vệ các node khác; có thể cần DB constraint/transaction/distributed coordination.

### 9.3. Dùng cấu trúc concurrency thay vì khóa thủ công

Nhu cầu | Gợi ý cân nhắc | Giới hạn cần nhớ
--- | --- | ---
Đếm sự kiện | `LongAdder` hoặc `AtomicLong` | LongAdder không phải một snapshot tuyến tính hóa hoàn hảo khi updates đang diễn ra
Giữ immutable config | `final`, immutable objects, safe publication | Thay cấu hình runtime cần chiến lược phiên bản hóa
Map concurrent | `ConcurrentHashMap` | Các cập nhật qua nhiều keys/objects không tự atomic cùng nhau
Serialize việc theo owner | Actor/single-thread queue | Queue tạo backlog/backpressure, tăng latency
Cập nhật nhiều fields đồng bộ | Lock có scope hẹp hoặc immutable snapshot | Phải đảm bảo mọi writer/readers cùng contract
Cross-process uniqueness | Database unique constraint/transaction | Phụ thuộc isolation và failure recovery
Giới hạn số tác vụ đồng thời | Semaphore/bulkhead | Đây là điều phối concurrency, không tự tạo atomicity nghiệp vụ

Không dùng một kỹ thuật chỉ vì nó “lock-free”. Các thuật toán CAS/retry cũng có thể tranh chấp mạnh, thất bại dưới tải cao hoặc không giữ invariant cần thiết.

## 10. Đổi lock order do advice precedence

Khi hai around advices cùng khớp method:

~~~text
Order 1: AUDIT(lock A) -> CACHE(lock B) -> TARGET
Order 2: CACHE(lock B) -> AUDIT(lock A) -> TARGET
~~~

Một công cụ AOP có thể có quy tắc precedence cho toàn bộ aspect hoặc advice. Cần kiểm:

- Thứ tự bên ngoài/bên trong của từng lời gọi.
- Có các pointcuts khác nhau áp thứ tự khác không.
- Một advice có short-circuit, nên đôi lúc **không bao giờ vào lớp trong**.
- Retry có gọi `proceed` nhiều lần trong lúc giữ outer lock không.
- Class đang được woven hay caller đi qua proxy nào.
- Aspect ở ranh giới call có thể gọi một method khác và tái nhập pointcut hay không.

Thứ tự lớp advice **chính là một phần của lock acquisition graph** nếu advice lấy lock. Thay đổi version/cấu hình của weaving hoặc proxy chain cần chạy lại các bài test lock-order và integration.

## 11. Self-invocation, recursion và advice tự gọi lại

Một proxy chỉ quan sát lời gọi đi qua wrapper; method `this.otherMethod()` bên trong target có thể bypass proxy ngoài trong nhiều mô hình. Trong khi đó, code weaving ở execution join point có thể chạy cả khi self-invocation nếu class đã instrument đúng.

Điều này ảnh hưởng locks:

- Một lần vào advice thực tế có thể lấy lock một lần.
- Recursion hoặc execution weaving có thể gọi lại **chính advice** ở mức sâu hơn.
- Với reentrant lock, cùng thread lấy lại được, nhưng hold count tăng; phải unlock tương ứng.
- Với một non-reentrant mutex tùy thư viện, self-acquisition có thể treo ngay cả trên một thread.
- Nếu advice tự gọi một method cũng match pointcut mà không có điều kiện loại trừ, recursion có thể lặp vô hạn.

**Không sửa vấn đề bằng một cờ `boolean inAdvice` toàn cục** nếu nhiều threads cần can thiệp độc lập. Nếu dùng guard, phải xác định scope (thread, invocation, control flow), restore trong `finally` và xét async propagation.

## 12. Lock và các công việc bất đồng bộ

Một lỗi thiết kế nguy hiểm:

~~~text
Thread A:
  acquire LOCK
  submit asynchronous task
  wait Future.get()

Worker thread B:
  run task
  attempt acquire LOCK
  blocked

Thread A vẫn chờ Future -> vòng chờ qua khóa + Future
~~~

Ngay cả khi không có hai khóa A/B cổ điển, **wait graph** vẫn có chu trình qua executor, callback, latch hoặc Future. Một thread có thể giữ lock và đợi callback, còn callback cần chính lock đó.

Một advice gọi `proceed()` rồi `join()` một Future trong lúc giữ lock có nguy cơ tạo vòng chờ tương tự. Vì vậy:

- Không giữ mutex qua `Future.get()`/`join()` trừ khi đã chứng minh dependency graph an toàn.
- Không gọi callback do người dùng đăng ký trong critical section một cách tùy tiện.
- Nếu trả `CompletableFuture`, “method return” chưa phải task completion; quản lý trạng thái riêng cho completion nếu advice cần.
- Không `unlock()` `ReentrantLock` từ thread khác: lock có **thread ownership** và cách đó không hợp lệ.
- `ThreadLocal` context không tự lan sang mọi executor; nếu cần propagation phải có cơ chế chuyên biệt.
- Nếu cần bounded concurrency, semaphore, queue hoặc executor limits có thể phù hợp hơn mutex giữ xuyên một workflow bất đồng bộ.

### 12.1. Cẩn thận với `synchronized` và asynchronous callbacks

Mặc dù Java monitors là reentrant trên cùng thread, callback chạy ở một thread khác **không được hưởng reentrancy** của holder. Thực tế bug có thể chỉ xuất hiện khi callback đổi từ synchronous sang asynchronous.

## 13. Ngoại lệ, timeout, interrupt và bảo toàn hợp đồng

### 13.1. Mỗi acquire thành công phải có release tương ứng

Mẫu cơ bản với `ReentrantLock`:

~~~java
lock.lock();
try {
    runProtectedOperation();
} finally {
    lock.unlock();
}
~~~

Đây là pseudocode chưa độc lập. Nếu `runProtectedOperation()` return, throw hoặc bị ngắt bởi exception thông thường, `unlock()` vẫn chạy khi Java unwinds scope.

**Không** dùng:

~~~java
lock.lock();
runProtectedOperation();
lock.unlock();
~~~

Nếu operation throw, lock bị giữ lại và có thể làm requests sau chờ mãi.

### 13.2. Không unlock khi chưa lấy được lock

Với `tryLock()`:

~~~java
boolean acquired = lock.tryLock();
if (!acquired) {
    return failedToAcquireResult();
}
try {
    return runProtectedOperation();
} finally {
    lock.unlock();
}
~~~

Đây là pseudocode. Nếu `tryLock` trả false mà vẫn gọi `unlock()`, có thể ném `IllegalMonitorStateException` hoặc làm lỗi luồng xử lý. Nhớ rõ nhánh acquired.

### 13.3. Xử lý interrupt đúng ngữ nghĩa

Các API blocking như `lockInterruptibly()` và `tryLock(timeout, unit)` có thể ném `InterruptedException`. Nếu advice bắt và xử lý mà không ném lại, cần khôi phục interrupt flag khi phù hợp bằng `Thread.currentThread().interrupt()`, để tầng ngoài nhận biết cancellation.

Không catch rồi bỏ qua interrupt, tiếp tục chờ vô hạn; có thể làm shutdown bị treo.

### 13.4. Có timeout không có nghĩa an toàn retry

Một timeout chờ lock không cho biết target đã commit từ một attempt trước hay chưa. Khi retry logic và lock coordination cùng tồn tại, phải phân biệt:

- **Timeout trước khi vào critical section:** target có thể chưa chạy trên lần thử hiện tại.
- **Timeout khi đang chờ kết quả operation khác:** effect có thể đã xảy ra.
- **Timeout sau khi commit nhưng trước ACK:** outcome có thể chưa rõ.

Idempotency/reconciliation cần được thiết kế tại ranh giới nghiệp vụ, không thể giải quyết chỉ bằng `tryLock(100, MILLISECONDS)`.

## 14. Chẩn đoán deadlock và contention trong JVM

Khi một hệ thống chậm, đừng chỉ xem CPU. Một chương trình bị deadlock có thể có **CPU thấp**, nhưng requests không tiến triển.

### 14.1. Thread dump và lock owners

Một thread dump có thể cho biết:

- Thread ở `BLOCKED` khi chờ intrinsic monitor.
- Thread đang `WAITING` hoặc `TIMED_WAITING` trên condition, park, Future hoặc synchronizer.
- Stack traces cho biết advice/target đang nắm hoặc chờ tài nguyên.
- Ownable synchronizers/monitor ownership khi JVM tooling có dữ liệu.
- Một vòng chờ locks nếu diagnostic có hỗ trợ phát hiện.

**Không kết luận mọi `WAITING` là deadlock.** Một worker có thể chờ queue rỗng theo thiết kế, một Future có thể chưa completed hợp lệ. Cần kết hợp owner, stack traces, tiến triển và thời gian.

### 14.2. `ThreadMXBean` và các giới hạn

Java `java.lang.management.ThreadMXBean` có các chức năng để phát hiện một số dạng deadlock giữa monitors và ownable synchronizers, ví dụ `findDeadlockedThreads()` trên JVM hỗ trợ. `findMonitorDeadlockedThreads()` tập trung vào deadlock monitor.

Nhưng một vòng chờ qua HTTP, DB, message queue hoặc callbacks có thể **không được phát hiện như JVM monitor deadlock**. Không dùng `null` từ một detector làm bằng chứng hệ thống không thể deadlock.

### 14.3. Quan sát performance

Một số tín hiệu:

- Thread blocked time và số contention events.
- Lock wait time, hold time và hàng đợi đợi.
- Throughput theo requests/second.
- P50, P95, P99 latency của từng API boundary.
- Số in-flight requests, executor queue, available workers.
- Tỷ lệ timeouts/acquisition failures và số retries.
- Độ trễ của sink metrics/audit trong khi advice giữ lock.
- Contended locks theo target/key và theo pointcut/aspect.

Java Flight Recorder hoặc profiler có thể giúp quan sát một số sự kiện monitor/park theo khả năng cấu hình. Nếu bật tracing lock mức chi tiết cao trong production, cần đánh giá overhead và tránh log payload nhạy cảm.

### 14.4. Ghi nhận ngữ cảnh AOP đúng cách

Nếu nghi lock trong advice, log/trace nên có:

- Tên aspect/advice hoặc interceptor layer.
- Join point signature/kind khi hệ thống cung cấp.
- Định danh invocation không nhạy cảm.
- Lock identity/lock category không chứa thông tin bí mật.
- Thời điểm acquire attempt/acquired/released.
- Đường đi `proceed` hoặc callback đang chờ.
- Trạng thái timeout, interrupt, exception.

Chỉ ghi “method X slow” thường không đủ để biết thời gian ở **lock wait**, **target** hay **sink I/O**.

## 15. Ước lượng vì sao một khóa global làm tăng latency

Giả sử một advice có một khóa dùng chung và **phần giữ khóa trung bình** là `H` giây. Dù target có thể xử lý song song, critical section được phục vụ **tối đa khoảng 1/H lượt mỗi giây** trong mô hình đơn giản lý tưởng, trước khi tính overhead và scheduling.

Ví dụ nếu `H=0,05s` (50 ms), throughput cực đại của **khóa tuần tự** khoảng 20 lượt/s. Nếu một lời gọi giữ lock qua một HTTP request kéo dài 500 ms, các requests đứng sau có thể phải chờ theo đuôi rất dài, dù CPU còn rảnh.

Đây là **ước lượng minh họa**, không phải SLA bảo đảm: contention phụ thuộc arrivals, variability, number of cores, lock implementation và queueing. Tail latency có thể tăng mạnh khi utilization tiến gần capacity; p99 quan trọng hơn chỉ nhìn trung bình.

**Tối ưu đúng:** giảm **thời gian nắm khóa** và giảm **số bên cần cùng khóa**. Chỉ tăng thread pool có thể làm nhiều threads cùng tranh chấp hơn mà throughput vẫn bị lock giới hạn.

### 15.1. Một shared lock tạo hiệu ứng toàn cục

Nếu cùng một metrics aspect bao hàng nghìn methods, một nút chậm có thể làm trễ mọi method không liên quan. Đôi khi người vận hành nhầm đây là DB saturation vì stack traces của nhiều requests nằm trong business methods. Hãy kiểm lock owner và thứ tự advice trước khi tăng connection pool.

### 15.2. Số lock nhiều hơn không phải lúc nào cũng tốt

Sharding locks theo keys có thể tăng parallelism, nhưng cũng phức tạp hóa ownership, eviction và multi-lock operations. Một concurrent data structure tốt hoặc thiết kế không chia sẻ state thường đơn giản và an toàn hơn.

## 16. Lock ordering và transaction/database locks

Một lời advice mở database transaction rồi gọi target trong lúc giữ Java mutex có thể tạo thứ tự tài nguyên:

~~~text
Thread A: Java lock X -> DB row lock Y
Thread B: DB row lock Y -> callback needs Java lock X
~~~

Chương trình có thể deadlock **xuyên qua JVM và database**. `ThreadMXBean` có thể nhìn thấy thread chờ DB client nhưng không tự đọc được DB lock owners để phát hiện chu trình đầy đủ.

Cần:

- Định nghĩa thứ tự lấy locks qua Java, DB và resources.
- Không làm external I/O khi giữ Java mutex nếu có thể.
- Giữ transactions ngắn và sử dụng isolation phù hợp.
- Sử dụng DB deadlock detection/timeout theo database cụ thể.
- Xử lý rollback/retry đúng loại lỗi và idempotency.
- Không retry toàn bộ thao tác khi timeout mà không rõ commit outcome.

**Vị trí của transaction advice** và `proceed` có thể thay đổi thời điểm resource được nắm giữ. Điều đó phải nằm trong kiểm thử integration, không chỉ unit test của từng aspect.

## 17. Test matrix cho khóa trong advice

| Ca thử | Điều cần chứng minh |
| --- | --- |
| Advice đo metrics, target chạy chậm | Thread khác không bị lock global nếu không cần |
| Advice giữ lock xuyên `proceed` | Competitor phải chờ hoặc `tryLock` báo busy, như demo |
| Target throw | Lock release trong `finally`; exception gốc được bảo toàn |
| Advice before throw | Có cleanup cho resource đã acquire trước lúc before |
| Advice after throw | Lock vẫn release; caller outcome theo contract |
| Đường A→B và B→A | Có policy ngăn lock inversion |
| Hai threads lấy locks theo cùng order | Hoàn tất, không circular wait trong test |
| Timeout khi lấy lock | Nhánh failure không `unlock` lock chưa sở hữu |
| Thread bị interrupt | Exit/cancellation theo contract; không giữ lock |
| Recursion cùng thread | Hold count/reentrancy và state không hỏng |
| Callback chạy trên thread khác | Không có vòng lock↔Future/condition |
| Many concurrent requests | P95/p99, throughput và wait time đáp ứng mục tiêu |
| One slow external sink | Không chặn hàng loạt nghiệp vụ không liên quan |
| Cache/short-circuit | Không giữ khóa ngoài ý định; target không chạy khi bị chặn |
| Retry nhiều attempts | Lock hold time không phình theo tất cả attempts nếu không chủ đích |
| Proxy bypass | Test đúng đường caller, không giả định advice luôn chạy |
| Class đã woven | Kiểm scope pointcut, advice precedence và join points thật |
| Shutdown in-flight | Không release resource sai thread hoặc làm treo shutdown |

### 17.1. Phải kiểm concurrency thật

Unit test advice trên một thread có thể chứng minh logic `finally`, nhưng **không chứng minh** không có deadlock khi hai threads lấy khóa theo thứ tự ngược. Dùng latches, barriers hoặc controlled scheduler để ép interleavings cần khảo sát.

Không dùng `Thread.sleep(10)` như bằng chứng chắc chắn tạo được tình huống đối đầu. Nếu test tạo real deadlock để kiểm detector, nên cách ly trong process có watchdog/timeout và chẩn đoán rõ, tránh làm treo cả CI suite.

### 17.2. Assert outcome, không chỉ logs

Một test khóa tốt cần xác minh:

- Caller result/exception vẫn đúng.
- Target thực thi đúng số lần; retry không làm duplicate side effect.
- Lock được release khi success và exceptional exit.
- No progress stall sau timeout hoặc cancellation.
- Những callers không liên quan không bị serialized ngoài ý định.
- Shared state giữ invariant.
- Không có long-lived registry/ThreadLocal rò references sau request.

## 18. Quy trình review một aspect có khóa

1. **Tìm tất cả đường acquire:** `synchronized`, `ReentrantLock.lock`, `tryLock`, condition wait, semaphore hoặc locks ẩn trong client.
2. **Xác định lock identity:** global, per-classloader, per-target, per-key hay per-invocation?
3. **Vẽ critical section:** từ acquire tới release; đánh dấu `proceed()`, external calls, callbacks, `join/get`, retry.
4. **Liệt kê state/invariant được bảo vệ:** nếu chỉ một counter, cân nhắc atomic/concurrent primitive.
5. **Lập đồ thị thứ tự khóa:** locks trong advice, target, DB, event bus và callback.
6. **Kiểm reentrancy:** method được pointcut chọn có thể gọi lại advice không?
7. **Kiểm exception path:** mọi acquire thành công có release trong `finally` không?
8. **Kiểm interrupt/timeout:** nhánh không lấy được lock làm gì? Có làm đổi policy bảo mật/nhất quán không?
9. **Kiểm async:** có thread khác cần khóa khi caller đang chờ Future không?
10. **Kiểm scope AOP thật:** pointcut match đúng, proxy/weaver không bypass, precedence phù hợp.
11. **Đo dưới tải:** contention, tail latency, throughput và wait/hold time.
12. **Giữ regression tests:** đổi thứ tự aspect hoặc cập nhật agent/interceptor phải chạy lại kịch bản lock inversion.
13. **Kiểm shutdown:** không dùng unlock sai thread, không block vô hạn khi drain in-flight calls.
14. **Tài liệu hóa hợp đồng:** lock chỉ bảo vệ tài nguyên nào, chính sách khi busy, số attempts và failure semantics.

## 19. Những sai lầm phổ biến

1. **“Có `finally` thì advice giữ lock qua target an toàn hoàn toàn.”** Sai: vẫn có contention và deadlock do code bên trong.
2. **“Khóa global chỉ chậm concern metrics.”** Sai: nếu khóa bao `proceed`, mọi target được chọn có thể bị serialized.
3. **“`ReentrantLock` không thể deadlock.”** Sai: reentrancy chỉ xử lý self-acquisition cùng thread; lock inversion vẫn nguy hiểm.
4. **“`synchronized` xấu còn `ReentrantLock` luôn nhanh.”** Không có kết luận chung.
5. **“`tryLock()` thất bại thì cứ coi request success.”** Sai nếu invariant nghiệp vụ chưa được đảm bảo.
6. **“Timeout bảo đảm operation chưa commit.”** Sai nếu đang chờ outcome từ tài nguyên khác.
7. **“Thay `lock()` bằng `tryLock()` tự giải quyết mọi vấn đề.”** Không: nhánh busy và retry cần thiết kế.
8. **“`unlock()` từ callback thread khác được.”** `ReentrantLock` có ownership theo thread; không unlock tùy tiện.
9. **“Hai aspect khác nhau không thể deadlock với nhau.”** Sai nếu chúng giữ khóa trong nested call graph.
10. **“Order pointcut không ảnh hưởng khóa.”** Sai: precedence có thể đảo thứ tự A→B thành B→A.
11. **“Thread WAITING chắc là deadlock.”** Không; có thể là chờ công việc hợp lệ.
12. **“Detector không thấy deadlock nghĩa là hệ thống an toàn.”** Không nếu vòng chờ qua DB/network/Future.
13. **“Nhiều threads hơn luôn tăng throughput.”** Không khi một khóa global giới hạn critical section.
14. **“Lock per-key không thể leak.”** Registry có thể tích lũy hoặc xóa key sai thời điểm.
15. **“Java lock bảo vệ mọi process.”** Không bảo vệ các JVM khác.
16. **“Dùng ThreadLocal để lưu lock holder sẽ tự theo async task.”** Không có bảo đảm chung.
17. **“Một metrics aspect cần field `startedAt` shared.”** Dùng local per invocation thường đúng hơn.
18. **“Giữ lock qua I/O cho chắc thứ tự event.”** Có thể làm latency rất lớn; cần cơ chế order/delivery phù hợp.
19. **“Lock timeout và retry không ảnh hưởng business side effects.”** Có thể tạo repeated executions hoặc unknown outcome.
20. **“Recursion chỉ xảy ra trong target.”** Advice có thể tự gọi method cũng match pointcut, tạo reentrancy.
21. **“Không bao giờ cần đo lock hold time.”** Chỉ đo target duration có thể bỏ sót delay trong advice.
22. **“Khóa công bằng luôn tốt hơn.”** Fairness có tradeoff với throughput/scheduling.
23. **“Mọi AOP engine hỗ trợ cùng loại join point.”** Phụ thuộc proxy/weaver và phạm vi instrumentation.
24. **“Unit test advice đơn luồng chứng minh deadlock-free.”** Cần concurrency integration tests.
25. **“Unlock trong finally tạo atomicity cho thao tác.”** Release mutex không phải rollback transaction.
26. **“Mọi advice chỉ chạy một lần khi có một logical request.”** Retry/recursion/nested join points có thể tạo nhiều entries.
27. **“Mọi lock contention có thể giải quyết bằng timeouts ngắn.”** Có thể gây lỗi hàng loạt và retry storm.
28. **“Ghi lỗi lock nên log mọi payload.”** Có nguy cơ lộ dữ liệu nhạy cảm và tăng I/O đúng lúc hệ thống đang nghẽn.

## 20. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `globalLock.lock(); try { proceed(); } finally { unlock(); }` có thể làm chậm toàn ứng dụng?  
**Đáp án:** Vì lock bị giữ xuyên suốt target và các advice bên trong, cả khi có I/O chậm hoặc chờ lock khác.

**Câu 2.** `finally` giải quyết vấn đề gì và không giải quyết vấn đề gì?  
**Đáp án:** Giúp release lock khi unwinding bình thường, nhưng không rút ngắn critical section hay tự ngăn circular wait.

**Câu 3.** T1 giữ A chờ B, T2 giữ B chờ A. Đây là loại lỗi gì?  
**Đáp án:** Lock-order inversion tạo circular wait và nguy cơ deadlock.

**Câu 4.** Vì sao reentrant lock không đảm bảo deadlock-free?  
**Đáp án:** Tái nhập chỉ cho cùng thread lấy lại lock nó sở hữu; không ngăn nhiều threads chờ chéo các locks khác nhau.

**Câu 5.** Trong ví dụ Java, vì sao `INVERSION tryLockFailures=2` không khiến ứng dụng treo?  
**Đáp án:** Cả hai thread dùng nonblocking `tryLock()` cho lock thứ hai, nhận false rồi release; latches giữ trạng thái ổn định khi thử.

**Câu 6.** Vì sao `ORDERED completed=2`?  
**Đáp án:** Hai workers lấy các locks theo cùng thứ tự left→right, hoàn tất cả hai calls mà không có inversion trong test.

**Câu 7.** Một shared timer aspect có field `start`. Có cần khóa toàn target để đo đúng không?  
**Đáp án:** Thường không; dùng local start time theo invocation tránh race mà không serialize nghiệp vụ.

**Câu 8.** Có nên lấy `ReentrantLock` rồi chờ `CompletableFuture.join()` khi callback cần cùng khóa?  
**Đáp án:** Không; có thể tạo chu trình chờ lock ↔ Future qua các threads.

**Câu 9.** `tryLock` trả false. Có được gọi `unlock` không?  
**Đáp án:** Không, trừ khi thread đang sở hữu lock từ một acquisition khác có hợp đồng rõ. Thông thường chỉ unlock nếu lần lấy này thành công.

**Câu 10.** `ThreadMXBean.findDeadlockedThreads()` trả null có chứng minh không có deadlock qua DB lock không?  
**Đáp án:** Không. Nó không thấy đầy đủ mọi tài nguyên ngoài JVM.

**Câu 11.** Khi hai pointcuts có thứ tự precedence khác nhau, cần kiểm tra gì thêm ngoài logs?  
**Đáp án:** Đồ thị lock acquisition, critical section, đường retry/short-circuit, contention và tác dụng phụ nghiệp vụ.

**Câu 12.** Lock per-key có hai rủi ro đặc trưng nào?  
**Đáp án:** Registry leak hoặc eviction sai làm hai locks riêng xuất hiện cho cùng key; multi-key operations cũng có thể deadlock khi order không nhất quán.

**Câu 13.** Tại sao throughput không nhất thiết tăng khi mở rộng thread pool?  
**Đáp án:** Nếu một lock global giới hạn số critical sections hoàn tất mỗi giây, thêm threads chủ yếu tăng số bên xếp hàng.

**Câu 14.** Kiểm thử nào phù hợp để phát hiện lock inversion mà không làm treo CI?  
**Đáp án:** Dùng latches/barriers tạo hai locks được giữ đồng thời, thử lock thứ hai bằng nonblocking `tryLock` rồi assert cả hai thất bại và dọn dẹp; bổ sung monitoring và integration tests cho chain thật.

## 21. Tổng kết

**Một khóa đặt trong advice có phạm vi ảnh hưởng rộng hơn vẻ ngoài của nó**, vì aspect có thể áp lên nhiều methods/targets và `proceed()` có thể chạy toàn bộ nghiệp vụ cùng những concerns khác. Giữ khóa quá rộng làm tăng contention, giảm throughput, tăng tail latency và mở đường cho deadlock qua lock-order inversion, callbacks, Future hoặc tài nguyên bên ngoài JVM.

Hướng tiếp cận an toàn là **định nghĩa state/invariant thực sự cần bảo vệ**, dùng **scope khóa ngắn nhất đúng hợp đồng**, tránh giữ lock qua I/O hay gọi code không kiểm soát, thống nhất thứ tự lấy nhiều locks, release trong `finally`, và xử lý timeout/interrupt một cách có chủ đích. Ưu tiên local state per invocation, concurrent counters hoặc thiết kế ownership phù hợp thay vì dùng mutex toàn cục để che race của một field shared.

Sau cùng, phải **kiểm thử trên đường AOP thực tế**: đúng proxy/weaving coverage, đúng precedence, concurrency interleavings và failure paths. Một test chạy đơn luồng có thể chứng minh logic advice nhưng không chứng minh deadlock-free hoặc throughput đủ tốt. Kết hợp kiểm thử hữu hạn có điều khiển với thread dumps, lock wait/hold metrics và benchmarks dưới tải đại diện để tránh biến một concern bổ sung thành nút thắt cổ chai của hệ thống.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-concurrency-008">Tái nhập, lời gọi lồng nhau và aspect tự can thiệp</a>

<details>
<summary>Xem chi tiết</summary>

# Tái nhập, lời gọi lồng nhau và aspect tự can thiệp trong AOP

## 1. Bức tranh chung: tại sao advice có thể gọi lại chính nó?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, một **aspect** đóng gói các mối quan tâm xuyên suốt như ghi log, kiểm toán, đo thời gian, kiểm tra quyền, cache hoặc quản lý trạng thái. **Pointcut** lựa chọn các **join point** được công cụ AOP hỗ trợ; **advice** thực hiện hành vi trước, sau hoặc bao quanh những điểm ấy.

AOP làm đường thực thi có thêm một hoặc nhiều lớp can thiệp. Một lời gọi tưởng như đơn giản:

```text
caller -> businessMethod()
```

có thể trở thành:

```text
caller
  -> Advice A
      -> Advice B
          -> businessMethod()
      <- Advice B
  <- Advice A
```

Khi business method tiếp tục gọi một method khác, hoặc **bản thân advice** gọi một method cũng khớp pointcut, chuỗi can thiệp có thể đi vào lại cùng aspect. Hậu quả có thể là hành vi hợp lệ, log lồng nhau, đếm sai, vòng lặp vô hạn, tràn stack, deadlock hoặc phát sinh lỗi khó truy vết.

Có ba hiện tượng cần phân biệt:

1. **Nested calls — lời gọi lồng nhau:** đang ở trong một thao tác thì gọi tiếp thao tác khác. Không nhất thiết là lỗi và không nhất thiết gọi lại cùng method.
2. **Reentrancy — tái nhập:** cùng một thành phần/logic có thể được đi vào lần nữa trước khi lần trước kết thúc, trực tiếp hoặc gián tiếp.
3. **Self-interception — aspect tự can thiệp:** một lời gọi do advice tạo ra lại khớp pointcut, khiến chính advice hoặc một chain liên quan được kích hoạt lại.

Còn một trường hợp dễ nhầm là **self-invocation bypass**: target tự gọi `this.inner()` nhưng lời gọi ấy **không đi qua proxy bên ngoài**, khiến một advice mong đợi **không chạy**. Đây gần như là vấn đề đối nghịch với self-interception: một phía can thiệp **quá nhiều**, phía kia can thiệp **không đủ**.

Bài này đi từ cơ bản tới nâng cao, trình bày cách nhận diện, thiết kế guard, kiểm soát state theo invocation, xử lý lỗi và kiểm thử. Các ví dụ Java 17 dùng để minh họa cơ chế, **không coi một engine AOP hay kiểu weaving cụ thể là chuẩn cho mọi công cụ**.

## 2. Thuật ngữ và ranh giới

| Khái niệm | Định nghĩa thực hành | Ví dụ |
| --- | --- | --- |
| **Call stack** | Chuỗi những lời gọi đang hoạt động trong một thread | `outer → inner → repository` |
| **Nested invocation** | Một invocation bắt đầu khi invocation khác chưa kết thúc | `outer` gọi `inner` |
| **Recursion** | Một thao tác quay lại cùng thao tác hoặc cùng chu trình gọi | `f → f` hoặc `f → g → f` |
| **Reentrancy** | Thành phần được đi vào lại trong lúc trạng thái lần trước còn hoạt động | Advice logging lại kích hoạt advice logging |
| **Self-invocation** | Method của object gọi một method khác trên chính receiver | `this.inner()` |
| **Self-interception** | Advice gọi thao tác mà pointcut chọn, rồi bị áp advice lần nữa | Advice → logger → cùng pointcut → advice |
| **Pointcut scope** | Tập join point được biểu thức/cấu hình lựa chọn | Execution của một nhóm method |
| **Interception boundary** | Nơi engine gắn logic advice vào đường chạy | Proxy, call site, method body hoặc loader |
| **Proceed/continuation** | Cách around advice chuyển điều khiển vào phần bên trong | `proceed()` |
| **Invocation-local context** | Thông tin riêng cho một lần gọi | Depth, trace ID, snapshot và timing |
| **Reentrancy guard** | Quy tắc ngăn hoặc phân nhánh khi đi lại vào vùng không được phép | Tránh trace logger tự kích hoạt |

**Nested call không đồng nghĩa recursion.** Ví dụ `checkout() → validate() → save()` là lồng nhau nhưng không tạo vòng.

**Recursion không mặc nhiên sai.** Hàm tính cây, parser hoặc duyệt đồ thị có thể đệ quy hợp lệ. Sai lầm nằm ở việc **advice vô tình tạo chu trình không có điều kiện dừng** hoặc trộn state giữa các mức sâu.

**Reentrancy cũng không luôn là lỗi.** Một aspect có thể có chủ đích quan sát từng lời gọi lồng nhau. Vấn đề là hợp đồng của nó có được thiết kế để xử lý nhiều lần vào đồng thời hoặc lồng nhau hay không.

## 3. Biểu đồ ba đường gọi khác nhau

### 3.1. Lời gọi lồng nhau hợp lệ

```text
Caller
  -> AOP before(outer)
      -> business outer()
          -> business inner()
      <- business outer returns
  <- AOP after(outer)
```

Tùy pointcut và cơ chế weaving, `inner` có thể nhận advice riêng hoặc không.

### 3.2. Advice tự kích hoạt do logging

```text
Caller -> service.save()
            |
            v
        LoggingAdvice
            |
            v
        logger.write()
            |
            v
       LoggingAdvice    <-- cùng pointcut lại khớp
            |
            v
        logger.write()
            |
           ...
```

Nếu không có exclusion/guard phù hợp và chu trình không có điều kiện dừng, stack có thể tiếp tục lớn cho đến khi hết tài nguyên hoặc runtime chặn.

### 3.3. Self-invocation bypass proxy

```text
Caller -> Proxy -> target.outer()
                    |
                    +--> target.this.inner()
                          |
                          +--> không đi qua Proxy
```

Ở kiểu proxy **giữ target riêng**, inner không nhất thiết được can thiệp dù caller vào outer qua proxy. Nếu weaving trực tiếp vào body của inner hoặc một subclass proxy dùng receiver khác, hành vi có thể khác.

Vì thế cần xác định **cả điểm phù hợp với pointcut lẫn đường gọi thực tế** trước khi kết luận advice thừa hay thiếu.

## 4. Lời gọi lồng nhau và chiều sâu của chain

Giả sử một aspect đo thời gian ở cả `OrderService.submit` và `Inventory.reserve`:

```text
submit()
  -> reserve()
  -> charge()
```

Nếu mỗi method đều được advice, một trace hợp lệ có thể là:

```text
enter submit, depth=0
  enter reserve, depth=1
  exit reserve, depth=1
  enter charge, depth=1
  exit charge, depth=1
exit submit, depth=0
```

**Depth** cho biết số invocation đang được theo dõi trong một ngữ cảnh thực thi cụ thể. Nó không tự đại diện cho độ sâu **nghiệp vụ** của toàn hệ thống phân tán, và cũng không phải một ID duy nhất.

### 4.1. Đếm số invocation khác đếm số request

Một request gọi ba method được advice có thể tạo ba invocation và sáu event enter/exit. Nếu báo cáo “request count” tăng ở mỗi method, hệ thống sẽ đếm thừa.

Cần phân biệt:

- **Request ID:** thao tác logic ngoài cùng.
- **Invocation/span ID:** một lần vào join point cụ thể.
- **Join-point identity:** loại/chữ ký hoặc vị trí mã được can thiệp.
- **Depth:** chỉ báo mức lồng nhau của stack/chain đang quan sát.
- **Attempt ID:** lượt thử lại khi retry.

Không gộp các danh tính này thành một biến `counter` dùng chung.

### 4.2. Duration bao gồm hay loại trừ thời gian con?

Nếu `submit()` gọi `reserve()`, duration của submit từ lúc vào đến lúc ra thường **bao gồm** thời gian reserve. Tổng durations của mọi method có thể **lớn hơn** thời gian wall-clock của request do đếm lặp thời gian lồng nhau.

Hai khái niệm:

- **Inclusive time:** từ enter đến exit, bao cả lời gọi con.
- **Exclusive/self time:** thời gian của đoạn được đo sau khi loại trừ đúng các khoảng thuộc lời gọi con phù hợp.

Muốn tính exclusive time cần một cấu trúc stack/interval nhất quán. Đừng chỉ cộng duration của tất cả span rồi gọi đó là tổng độ trễ request.

## 5. Tái nhập là thuộc tính của trạng thái, không chỉ của tên method

Hãy xem một advice:

```text
before any service method:
    beginTime = now()
```

Nếu `beginTime` là field mutable chung trên singleton aspect, invocation lồng nhau có thể ghi đè giá trị trước khi invocation ngoài kết thúc:

```text
outer enters -> beginTime = T0
inner enters -> beginTime = T1
inner exits  -> now - T1
outer exits  -> now - T1  // sai, cần T0
```

Ngay cả khi chỉ một thread, trạng thái vẫn sai. Đây là **lỗi reentrancy**, không nhất thiết cần race giữa nhiều thread.

### 5.1. Hai mức lỗi khác nhau

- **Nested-state corruption:** một thread vào lại advice trước khi advice ngoài kết thúc, làm ghi đè state.
- **Concurrent-state corruption:** hai thread độc lập sửa cùng field của aspect, làm trộn invocation và gây race.

Dùng `synchronized` có thể xử lý một số race giữa thread nhưng **không tự sửa** thiết kế snapshot sai khi lồng nhau trong cùng thread. Đồng thời, `synchronized` trên method Java có tính **reentrant** cho cùng thread, nên lock không tự chặn vòng lặp self-interception.

### 5.2. Stack theo invocation

Nếu cần giữ thời gian bắt đầu, có thể dùng ngăn xếp per-thread cho lời gọi đồng bộ, hoặc ngữ cảnh riêng cho từng invocation. Mỗi lần enter đẩy frame; mỗi lần exit lấy đúng frame của nó.

Tuy nhiên với **async**, frame có thể kết thúc trên thread khác: per-thread stack không còn đủ; cần context truyền theo task và định nghĩa completion boundary.

## 6. Aspect tự can thiệp: các nguồn tạo vòng lặp

### 6.1. Logging

Advice gọi `logger.log()`, nhưng pointcut chọn cả logger. Logger lại bị cùng advice kích hoạt.

### 6.2. Metrics

Advice đo thời gian và gọi `metrics.increment()`, còn pointcut chọn cả các method metrics. Nếu mỗi lần tăng metric lại tạo metric mới, sẽ nhân lên vô hạn hoặc tới một giới hạn ngoài ý định.

### 6.3. Audit và persistence

Audit advice ghi audit record qua repository; pointcut chọn mọi repository method, bao gồm chính thao tác ghi audit. Khi đó một lần audit có thể sinh thêm audit vô tận.

### 6.4. Security lookups

Authorization advice tra quyền bằng một service/repository nằm trong pointcut yêu cầu authorization. Nếu việc tra quyền lại cần tự kiểm quyền qua cùng đường ấy, có thể tạo **chu trình phụ thuộc**.

### 6.5. Serialization và `toString()`

Advice log `args.toString()`. Nếu `toString` hoặc getter bên trong bị intercept, logic ghi log có thể tự kích hoạt. Ngoài recursion, `toString()` còn có thể làm I/O, giữ lock hoặc lộ dữ liệu nhạy cảm.

### 6.6. Exception reporting

Advice bắt lỗi rồi gửi lỗi tới monitoring client; nếu client cũng khớp pointcut hoặc ném lỗi khác, đường xử lý exception có thể tạo **exception storm** hoặc che lỗi gốc.

Các trường hợp trên cho thấy **dependency của advice cũng nằm trên call graph**. Khi chọn pointcut, cần kiểm tra cả những method mà advice sẽ gọi, không chỉ method nghiệp vụ.

## 7. Tái nhập trực tiếp, gián tiếp và qua nhiều aspect

**Tái nhập trực tiếp:**

```text
AuditAdvice -> audit() -> AuditAdvice -> audit() -> ...
```

**Tái nhập gián tiếp:**

```text
AuditAdvice
  -> LoggingFacade
      -> Repository.saveLog
          -> AuditAdvice
```

**Chu trình qua hai aspect:**

```text
Aspect A -> helperB() -> Aspect B -> helperA() -> Aspect A
```

Không nhất thiết có một method gọi lại chính nó bằng tên. Một chu trình dài có thể qua cache, logger, serializer, callback hoặc retry.

### 7.1. Phân tích call graph

Để điều tra, đánh dấu từng cạnh:

| Cạnh | Chủ thể gọi | Thao tác được gọi | Có thể được aspect chọn? |
| --- | --- | --- | --- |
| 1 | Caller | Business.execute | Có |
| 2 | Advice | Logger.write | Có/Không theo pointcut |
| 3 | Logger | Storage.append | Có/Không |
| 4 | Storage | Hook.notify | Có/Không |
| 5 | Hook | Business.execute | Có thể tạo chu trình |

Nếu có đường từ **advice trở lại chính advice** mà không có điều kiện dừng hoặc exclusion, nguy cơ recursion tồn tại.

### 7.2. Chu trình có điều kiện dừng khác chu trình vô hạn

Một advice có thể cố ý thực hiện thao tác phụ chỉ ở mức ngoài cùng rồi bỏ qua phần phụ ở các mức lồng nhau. Khi đó có một chu trình call graph tiềm năng, nhưng **guard** ngăn tái nhập không kiểm soát.

Điều cần chứng minh là guard **đúng phạm vi** và không bỏ sót lời gọi hợp lệ khác.

## 8. Hai chiến lược chính: thu hẹp pointcut và guard runtime

### 8.1. Exclusion tĩnh

Nếu aspect dành để audit các nghiệp vụ, thường không nên chọn chính method ghi audit:

```text
include:
  business operations cần audit

exclude:
  audit sink, logger, advice helpers, instrumentation package
```

Đây là mô tả trung lập công cụ, không phải DSL chạy được.

Ưu điểm:

- Không tốn guard ở các điểm đã loại.
- Dễ lập báo cáo pointcut matched/unmatched.
- Giảm nguy cơ instrumentation gọi lại instrumentation.
- Cho phép kiểm thử tập điểm cần chọn một cách độc lập.

Giới hạn:

- Một call graph thay đổi có thể đưa helper khác vào tập khớp.
- Overload/annotation/package wildcard có thể đổi theo refactor.
- Không phải mọi chu trình được xác định hoàn toàn từ thông tin tĩnh.
- Dynamic guard vẫn có thể cần khi cùng method phục vụ cả lời gọi phụ và lời gọi nghiệp vụ.

### 8.2. Guard động

Một guard runtime có thể nhận biết đang trong **đường emission/logging của chính advice** rồi bỏ qua chỉ phần instrumentation không được tái nhập.

Mã giả:

```text
if currentlyEmittingTrace:
    invokeOriginalWithoutTrace()
else:
    currentlyEmittingTrace = true
    try:
        emitTrace()
    finally:
        currentlyEmittingTrace = false
    proceedNormally()
```

Nhưng mã giả này phải được hiện thực cẩn thận:

- Nếu biến dùng chung cho mọi thread, request khác có thể bị bỏ instrumentation.
- Nếu đặt guard quá rộng quanh toàn bộ `proceed`, những lời gọi nghiệp vụ lồng nhau hợp lệ cũng có thể bị tắt advice.
- Nếu không reset trong `finally`, một lỗi logging có thể khiến tất cả lời gọi sau bị bỏ qua.
- Nếu một helper có nhiều đường gọi khác nhau, cần phân biệt **vì sao** helper được gọi, không chỉ tên method.

**Ưu tiên loại khỏi pointcut các helper có thể xác định tĩnh**, và dùng guard để xử lý phần tái nhập thực sự động, với phạm vi tối thiểu.

## 9. Guard quá rộng làm mất các advice hợp lệ

Xem thiết kế sau:

```text
if insideAnyAdvice:
    invokeTargetDirectly()
else:
    insideAnyAdvice = true
    try:
        proceed()
    finally:
        insideAnyAdvice = false
```

Khi `outer` gọi `inner` trong lúc `outer` được advice bao, cờ `insideAnyAdvice` vẫn true. Vì vậy advice của `inner` bị bỏ qua, dù `inner` là một operation được phép và cần audit riêng.

Tức là ta “chữa” recursion bằng cách **xóa luôn nested instrumentation hợp lệ**.

Thiết kế tốt hơn:

- Tách **guard của việc phát trace/log** khỏi **ngữ cảnh depth của lời gọi nghiệp vụ**.
- Xác định những method helper mà aspect gọi trong quá trình tự quan sát.
- Chỉ bỏ qua instrumentation trên **đường tái nhập nội bộ** cần ngăn.
- Cho phép nested business invocation vẫn được chọn và ghi nhận.
- Kiểm thử cả đường self-interception lẫn nested call bình thường.

Một boolean chung tên `alreadyInAspect` thường không đủ diễn tả mọi trường hợp.

## 10. Java 17 runnable: nested advice, self-interception và guard

Ví dụ dưới sử dụng **JDK dynamic proxy** để tạo một interceptor. Nó không phải bộ dệt AOP tổng quát và không áp một DSL pointcut. Ta cố ý thiết kế:

1. `outer` được gọi qua proxy và có advice.
2. Trong `outer`, target gọi `this.local` **trực tiếp**, nên không có advice riêng cho `local`.
3. Target gọi `self.inner`, trong đó `self` trỏ tới proxy, nên `inner` tạo một invocation advice **lồng bên trong**.
4. Advice ghi trace thông qua **chính proxy** bằng `proxy.trace(...)`. Lời gọi này tái nhập handler, và một `ThreadLocal` guard bỏ qua **instrumentation của trace** chứ không tắt advice của `inner`.
5. Có đường exception từ `inner`; depth được dọn trong `finally`.
6. Một lời gọi trực tiếp target thể hiện bypass proxy.

Tạo file `NestedAdviceDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class NestedAdviceDemo {

    interface Operations {
        String outer(String id);
        String inner(String id);
        void local(String id);
        void trace(String message);
    }

    static final class Core implements Operations {
        private Operations self;

        void setSelf(Operations self) {
            this.self = self;
        }

        @Override
        public String outer(String id) {
            System.out.println("business:outer:" + id);
            this.local(id);   // Không đi qua proxy ngoài.
            return self.inner(id); // Cố ý đi qua proxy.
        }

        @Override
        public String inner(String id) {
            System.out.println("business:inner:" + id);
            if ("ERR".equals(id)) {
                throw new IllegalStateException("inner failed");
            }
            return "OK:" + id;
        }

        @Override
        public void local(String id) {
            System.out.println("business:local:" + id);
        }

        @Override
        public void trace(String message) {
            System.out.println("business:trace:" + message);
        }
    }

    public static void main(String[] args) {
        Core target = new Core();

        ThreadLocal<Integer> depth =
                ThreadLocal.withInitial(() -> 0);
        ThreadLocal<Boolean> emitting =
                ThreadLocal.withInitial(() -> false);

        Operations proxy = (Operations) Proxy.newProxyInstance(
                Operations.class.getClassLoader(),
                new Class<?>[] { Operations.class },
                (Object p, Method method, Object[] values) -> {
                    String name = method.getName();

                    // Chỉ bỏ instrumentation trên đường trace tự phát sinh.
                    if ("trace".equals(name) && emitting.get()) {
                        System.out.println("guard:trace-reentry");
                        try {
                            return method.invoke(target, values);
                        } catch (InvocationTargetException wrapped) {
                            throw wrapped.getCause();
                        }
                    }

                    int level = depth.get();
                    System.out.println("advice:enter:" + name
                            + ":depth=" + level);
                    depth.set(level + 1);

                    try {
                        emitting.set(true);
                        try {
                            ((Operations) p).trace(name);
                        } finally {
                            emitting.remove();
                        }

                        try {
                            Object result = method.invoke(target, values);
                            System.out.println("advice:success:" + name);
                            return result;
                        } catch (InvocationTargetException wrapped) {
                            Throwable original = wrapped.getCause();
                            System.out.println("advice:throw:" + name
                                    + ":" + original.getClass().getSimpleName());
                            throw original;
                        }
                    } finally {
                        if (level == 0) {
                            depth.remove();
                        } else {
                            depth.set(level);
                        }
                        System.out.println("advice:exit:" + name
                                + ":depth=" + level);
                    }
                }
        );

        target.setSelf(proxy);

        System.out.println("case:ok");
        System.out.println("caller:" + proxy.outer("A"));

        System.out.println("case:error");
        try {
            proxy.outer("ERR");
        } catch (IllegalStateException error) {
            System.out.println("caller:error:" + error.getMessage());
        }

        System.out.println("case:direct");
        System.out.println("caller:" + target.inner("B"));
    }
}
```

Biên dịch/chạy với JDK 17 hoặc mới hơn:

```bash
javac NestedAdviceDemo.java
java NestedAdviceDemo
```

**Đầu ra xác định:**

```text
case:ok
advice:enter:outer:depth=0
guard:trace-reentry
business:trace:outer
business:outer:A
business:local:A
advice:enter:inner:depth=1
guard:trace-reentry
business:trace:inner
business:inner:A
advice:success:inner
advice:exit:inner:depth=1
advice:success:outer
advice:exit:outer:depth=0
caller:OK:A
case:error
advice:enter:outer:depth=0
guard:trace-reentry
business:trace:outer
business:outer:ERR
business:local:ERR
advice:enter:inner:depth=1
guard:trace-reentry
business:trace:inner
business:inner:ERR
advice:throw:inner:IllegalStateException
advice:exit:inner:depth=1
advice:throw:outer:IllegalStateException
advice:exit:outer:depth=0
caller:error:inner failed
case:direct
business:inner:B
caller:OK:B
```

### 10.1. Vì sao có `guard:trace-reentry`?

Mỗi lần handler ghi event, nó cố ý gọi `((Operations) p).trace(name)` **qua chính proxy**. Điều đó lại đi vào handler. Vì cờ `emitting` đang true và method là `trace`, handler chuyển tiếp tới target **mà không phát thêm event advice**.

Nếu bỏ guard nhưng vẫn để advice cho `trace` tự gọi `trace`, sẽ có chu trình:

```text
handler(outer)
 -> proxy.trace
 -> handler(trace)
    -> proxy.trace
    -> handler(trace)
       ...
```

Không nên cố tình chạy phiên bản thiếu guard trong môi trường production để “xem nó tràn stack”.

### 10.2. Vì sao `inner` vẫn nhận advice?

Guard `emitting` chỉ được bật **trong khối phát trace** và được gỡ trước khi `method.invoke(target, values)` gọi business. Khi target.outer gọi `self.inner`, flag này đã được gỡ, nên `inner` vẫn có advice riêng.

Đây là điểm khác biệt chủ chốt giữa **guard hẹp, theo đường tự ghi trace** và **guard toàn bộ business continuation** gây bỏ sót nested advice hợp lệ.

### 10.3. Vì sao `local` không có advice?

`Core.outer` gọi `this.local(id)` trên target. Lời gọi này **không qua proxy** dù `local` có trong interface `Operations`. Vì vậy có event business nhưng không có `advice:enter:local`.

Nếu thay bằng `self.local(id)`, trong ví dụ này nó sẽ đi qua proxy, nhưng việc sửa mọi self-invocation thành tự gọi proxy không phải giải pháp tổng quát: nó có thể thay đổi semantics, tạo vòng lặp và làm rối ownership/identity. Một bộ dệt bytecode trực tiếp còn có phạm vi khác.

### 10.4. Depth và finally trên nhánh lỗi

Ở ca `ERR`, `inner` ném `IllegalStateException`. Handler của `inner` đọc `InvocationTargetException.getCause()`, ghi lỗi rồi ném nguyên nhân. Finally của `inner` khôi phục depth cho invocation ngoài trước khi finally của `outer` khôi phục về trạng thái ban đầu.

Kết quả `advice:exit:inner:depth=1` và `advice:exit:outer:depth=0` chứng minh việc **dọn context theo mức lồng nhau** dù business ném lỗi.

### 10.5. Hạn chế cần nêu của ví dụ

- Đây là **JDK interface proxy**, không phải một Aspect compiler.
- `self` được cố ý trỏ tới proxy chỉ để minh họa đường nested interception; trong ứng dụng thật việc cấp proxy self-reference cần xem lifecycle, khả năng tái nhập và ownership.
- `ThreadLocal` thích hợp để minh họa **lời gọi đồng bộ trên cùng thread**; nó không tự động mang context qua thread pool hoặc coroutine/task.
- Nếu `target.trace` thực hiện I/O và cũng gọi proxy ở đường khác, guard trong ví dụ có thể cần tinh chỉnh theo call graph thực tế.
- `Core` không được thiết kế để mutation đồng thời; ví dụ chỉ in event, không chứng minh hệ thống có thread safety.
- Không có sự bảo đảm từ ví dụ rằng mọi engine AOP cho phép cùng cơ chế proceed/override/self-call.

## 11. Mẫu thiết kế guard an toàn theo mục đích

Một guard hợp lý cần trả lời:

1. **Đang ngăn tái nhập vào thao tác nào?** Logger, audit sink, metrics exporter hay toàn bộ aspect?
2. **Tái nhập theo đường nào?** Do advice gọi helper, do business gọi lại, hay do callback bất đồng bộ?
3. **Phạm vi nào?** Một invocation, một thread, một request hay toàn tiến trình?
4. **Guard được bật từ đâu tới đâu?** Chỉ khi emit telemetry hay suốt business body?
5. **Ai dọn guard khi có exception?** `finally` hoặc cơ chế scope tương ứng.
6. **Khi guard active thì làm gì?** Bỏ log, gọi target trực tiếp, trả fallback hay báo lỗi?
7. **Một method vừa là helper vừa là business operation thì sao?** Cần dựa trên ngữ cảnh, không chỉ tên method.
8. **Có rủi ro mất audit bắt buộc không?** Nếu có, không được tùy ý skip.
9. **Có thread/task handoff không?** Nếu có, cần mô hình context khác hoặc bổ sung.

### 11.1. Cờ boolean không đủ cho nested state độc lập

Nếu advice cần nhiều mức nested mỗi mức giữ thông tin, `ThreadLocal<Boolean>` có thể chỉ biểu diễn “đang ở trong vùng X”. Một `ThreadLocal<Integer>` depth hoặc stack frame có thể phù hợp hơn khi cần **phân biệt từng invocation**.

Đừng dùng depth như cơ chế để tự động bỏ mọi invocation có `depth>0`. Đó là một chính sách riêng, có thể làm mất nested audit hợp lệ.

### 11.2. Exception trong phần đặt guard

Nếu đoạn đặt guard thành công nhưng đoạn phát log ném exception trước khi reset, một cờ còn sót sẽ làm những lời gọi sau trên cùng thread bị bỏ qua. Trong thread pool, lỗi này có thể ảnh hưởng request khác chạy trên thread được tái sử dụng.

Dùng `try/finally` để reset **ngay trong scope ngắn nhất**.

### 11.3. Tránh bỏ guard của một scope ngoài

Nếu hai scope cùng dùng một cờ boolean, scope trong gọi `remove()` có thể vô tình xóa guard do scope ngoài đang sở hữu. Cần một cơ chế **lưu và khôi phục trạng thái trước đó**, depth counter, hoặc token/scope theo stack.

Ví dụ khái niệm:

```text
previous = guard.get()
guard.set(true)
try:
    emit()
finally:
    restore(previous)
```

Việc `restore` có thể là `remove` hoặc `set(previous)` tùy trạng thái ban đầu. Nếu context có thể tái nhập nested chính phần emission, không nên thiết kế một cờ duy nhất mà không phân biệt ownership.

## 12. Vì sao ThreadLocal hữu ích nhưng không phải lời giải phổ quát?

Trong Java 17, `ThreadLocal<T>` gắn một giá trị riêng với từng thread. Với call stack **đồng bộ, không chuyển thread**, nó phù hợp để theo dõi depth hoặc guard.

Những giới hạn:

- Một thread pool có thể tái sử dụng thread cho request khác; không `remove()` đúng cách có thể làm rò context.
- Một request có thể tiếp tục ở thread khác; `ThreadLocal` hiện tại **không tự truyền** qua nhiệm vụ mới.
- Một thread có thể thực hiện nhiều logical operations nối tiếp; thread identity không phải request ID.
- Advice có thể gọi đệ quy trong cùng thread; một field `startTime` duy nhất trong ThreadLocal vẫn bị ghi đè nếu không dùng stack.
- Kích thước state lớn và vòng đời dài có thể làm tăng giữ tham chiếu không mong muốn.
- `InheritableThreadLocal` cũng không tự giải quyết semantics của thread pool, task cancellation hoặc context propagation.

### 12.1. Khi nào dùng explicit invocation context?

Nếu cần nhiều thông tin hơn một cờ:

```text
InvocationContext:
  traceId
  invocationId
  parentInvocationId
  depth
  pointcutId
  adviceId
  startedAt
  guardFlags
```

Đây là schema minh họa, không phải Java API có sẵn.

Một context riêng cho invocation có thể được truyền vào advice chain hoặc lưu trong cấu trúc scope phù hợp. Khi async, cần quyết định **context nào được kế thừa, context nào là mới, ai sở hữu vòng đời và ai cleanup**.

## 13. Lồng nhau, đệ quy và giá trị trạng thái “old”

Một aspect kiểm invariant có thể chụp snapshot `oldState` trước mỗi method. Với recursion:

```text
outer enters: old = S0
inner enters: old = S1
inner exits: compare against S1
outer exits: compare against S0
```

Nếu cả hai snapshot được lưu vào cùng một field mutable, inner ghi đè old của outer. Hậu điều kiện của outer sẽ sai dù business đúng.

Cách khắc phục:

- Snapshot đặt trong local variable của **mỗi around invocation** nếu engine cho phép.
- Hoặc một stack per invocation/thread với push/pop cân bằng.
- Hoặc context riêng được truyền theo continuation.
- Không dùng một field singleton dùng chung cho nhiều request.

### 13.1. Local variable giúp reentrancy-safe theo stack frame

Trong Java, mỗi lần gọi một method có bản local variables riêng. Nếu around advice được hiện thực là **một method invocation mới** ở mỗi lần vào, giá trị local lưu trên frame đó không bị ghi đè bởi lời gọi lồng nhau.

Tuy nhiên, object tham chiếu mà local variable giữ vẫn có thể mutable/chia sẻ. Dùng local reference không tự bảo đảm snapshot bất biến hay thread safety.

## 14. Around advice và số lần gọi proceed

Một around advice có thể điều khiển continuation:

```text
around operation:
    before
    value = proceed()
    after
    return value
```

Trong một số công cụ, quanh-advice có thể **không proceed**, hoặc **proceed nhiều lần** để retry. Không phải mọi công cụ hoặc loại join point đều có cùng quyền.

Với nested calls, cần đếm theo từng **attempt**:

```text
RetryAround
  attempt 1 -> InnerAdvice -> Business throws
  attempt 2 -> InnerAdvice -> Business returns
```

Nếu một aspect đếm “một operation” theo số lần vào InnerAdvice, nó có thể ghi **hai** event cho cùng một request logic.

### 14.1. Không để guard chặn cả lần retry hợp lệ

Một cờ `insideAspect=true` đặt quanh toàn bộ retry có thể khiến attempt 2 bỏ qua advice cần thiết. Phải quyết định chính sách được áp dụng theo:

- Logical request (một lần).
- Mỗi invocation.
- Mỗi retry attempt.
- Mỗi lần business side effect.
- Completion cuối của request.

Sự lựa chọn thuộc **hợp đồng của aspect**, không phải chi tiết tùy tiện của implementation.

### 14.2. Retry và tính idempotent

Nếu target đã thực hiện side effect rồi một after advice ném lỗi, retry từ lớp ngoài có thể lặp side effect. Guard không làm rollback. Với operation không idempotent, cần xác định điều kiện retry và cơ chế chống trùng thích hợp.

## 15. Phối hợp nhiều aspect và rủi ro tái nhập chéo

Giả sử:

```text
Audit(
    Authorization(
        Business
    )
)
```

Authorization cần đọc quyền từ repository. Repository cũng nằm trong pointcut Audit:

```text
Audit.enter(business)
  -> Authorization.checkPermission()
      -> repository.findPermissions()
          -> Audit.enter(repository)
```

Đây **có thể là nested audit hợp lệ**, nếu kiểm toán cả truy vấn quyền là chủ đích.

Nhưng nếu Audit ghi event qua `repository.saveAudit` và method này lại khớp Audit, thì có thể có **self-interception** nguy hiểm.

### 15.1. Không loại mọi repository method theo thói quen

Loại toàn bộ repository khỏi pointcut có thể mất event cần kiểm toán. Tốt hơn phân loại:

- **Audit sink writes:** helper nội bộ nên được loại khỏi instrumentation tương ứng, hoặc có guard chuyên biệt.
- **Business repository calls:** có thể vẫn cần advice.
- **Authorization lookups:** có thể audit theo một chính sách khác, tránh leak dữ liệu nhạy cảm.
- **Operational logging:** cần quy tắc dữ liệu/độ sâu riêng.

Exclusion nên **bảo toàn yêu cầu nghiệp vụ**, không chỉ làm hết stack overflow.

### 15.2. Thứ tự aspect thay đổi vòng tái nhập

Nếu một logging aspect ở ngoài security aspect, lời gọi kiểm quyền có thể tạo log trước khi quyền được xác nhận. Nếu security ở ngoài, nó có thể từ chối trước khi log bên trong bắt đầu. Muốn ghi nhận cả attempt bị từ chối, cần chọn vị trí có thể nhìn thấy nhánh ấy và bảo vệ dữ liệu nhạy cảm.

## 16. Reentrancy guard và cờ “đang trong cflow”

Một số mô hình pointcut hỗ trợ điều kiện liên quan **control flow**, thường được mô tả bằng các khái niệm như “đang ở trong luồng điều khiển do join point X khởi tạo”. Trong công cụ có hỗ trợ, nó có thể giúp chọn/bỏ chọn điểm theo call stack động.

Ví dụ **mã giả**, không phải cú pháp chung:

```text
auditBusinessPointcuts
AND NOT withinControlFlow(auditLoggerEmission)
```

Mục tiêu là không audit những lời gọi phát sinh từ chính việc ghi audit.

Các điểm cần chú ý:

- Loại theo **control flow** khác loại theo **declaring type** hoặc tên method.
- Một helper có thể được gọi cả từ business lẫn từ advice; loại theo ngữ cảnh có thể phù hợp hơn loại hoàn toàn theo class.
- Một số pointcut control-flow có overhead runtime.
- Control flow trên một call stack đồng bộ không tự tương đương **logical flow** của task bất đồng bộ.
- Một mô hình cflow có thể bao cả chính điểm bắt đầu hoặc chỉ các điểm nằm sâu bên trong; cần đọc semantics cụ thể.
- Dynamic pointcut không loại bỏ nhu cầu viết test cho recursion và bypass.

### 16.1. Phân biệt cflow và thread-local guard

Thread-local guard là cách **implementation** theo thread trong một ví dụ. Control-flow pointcut là khả năng **ngôn ngữ/engine AOP** trong một số công cụ. Hai thứ có thể cùng ý tưởng nhưng **không tương đương về ngữ nghĩa** trong mọi môi trường, đặc biệt khi có nested join points, async và class loader.

## 17. Lời gọi callback làm tái nhập một cách bất ngờ

Một business method có thể gọi callback được truyền vào:

```text
Business.execute()
  -> listener.onEvent()
      -> Business.execute()    <-- quay lại trước khi lần đầu thoát
```

Kể cả không có aspect, đây đã là **reentrancy**. AOP thêm một lớp can thiệp có thể:

- Làm callback thực hiện dưới lock của advice.
- Tăng số lần log hoặc metric.
- Gây kiểm tra state ở thời điểm object chưa ổn định.
- Kích hoạt transaction lồng nhau.
- Ném lỗi ở callback rồi được advice ngoài xử lý theo cách không ngờ.

Nếu object chỉ giữ invariant tại public method exit, callback được gọi giữa mutation có thể quan sát trạng thái trung gian. Vì vậy hợp đồng callback và điểm phát sự kiện phải rõ.

### 17.1. Không gọi callback không đáng tin dưới lock

Một advice giữ lock rồi gọi plugin callback có thể tạo chu trình khóa hoặc reentrancy. Tốt hơn cân nhắc sao chép event, thoát critical section, rồi dispatch callback theo giao thức phù hợp, nếu invariant và ordering cho phép.

## 18. Đồng bộ hóa: synchronized là reentrant nhưng không chống mọi lỗi

Java `synchronized` cho phép cùng một thread vào lại monitor mà nó đang giữ. Điều này hữu ích cho một số thiết kế recursion nhưng cũng có nghĩa:

```text
synchronized advice
  -> synchronized advice
      -> synchronized advice
          ...
```

vẫn có thể tái nhập trên **cùng thread**, không bị chặn bởi monitor.

Một số lỗi khác:

- **Deadlock:** Aspect A giữ lock X rồi gọi B lấy Y; luồng khác giữ Y rồi muốn X.
- **Lock convoy:** nhiều lời gọi nhỏ chờ cùng monitor vì advice dùng một lock toàn cục.
- **Priority inversion:** thao tác quan trọng đợi I/O logging dưới lock.
- **Callback under lock:** callback tự gọi lại method hoặc tham gia vào chu trình khóa khác.
- **Lock mismatch:** `synchronized(proxy)` không đồng nhất monitor với `synchronized(target)` trong mô hình proxy giữ target riêng.

Không nên thêm synchronized vào toàn bộ advice để “sửa recursion” mà không vẽ call graph và ownership của lock.

## 19. Transaction lồng nhau và trạng thái nghiệp vụ

Một advice giao dịch có thể mở scope khi vào method và đóng scope khi thoát. Khi method được advice gọi một operation khác cũng cần transaction, hệ thống có **nested invocation**.

Những câu hỏi phải giải quyết:

- Inner operation tham gia transaction hiện tại hay mở một scope độc lập?
- Rollback của inner có buộc outer rollback không?
- Khi inner bị short-circuit, outer transaction còn hợp lệ không?
- Retry mở transaction mới mỗi attempt hay dùng transaction cũ?
- Exception từ after advice có xảy ra **sau khi commit** không?
- Một lời gọi đi vòng proxy có bỏ qua transaction boundary mong đợi không?

**Không có câu trả lời chung**: propagation và nested transaction semantics thuộc hạ tầng giao dịch cụ thể. AOP chỉ mang cách kết hợp logic; nó không tự quyết định atomicity.

### 19.1. Không lấy “depth>0” làm bằng chứng đã có transaction

Một method được lồng trong lời gọi khác không có nghĩa nó thuộc cùng transaction hay mọi side effect đều được bảo vệ. Depth của AOP call stack và transaction state là hai loại trạng thái khác nhau.

## 20. Ngoại lệ từ advice tự can thiệp và cách bảo toàn lỗi gốc

Giả sử business ném lỗi `E1`. Advice after-throwing ghi log, nhưng logger cũng bị intercept, tạo lỗi `E2`.

```text
Business throws E1
  -> ErrorAdvice
       -> LoggingAdvice reenters
            -> Logger throws E2
  -> Caller sees E2?
```

Nếu code không quản lý rõ, caller có thể chỉ thấy E2, còn E1 bị che.

Các lựa chọn thiết kế:

- Nếu logging **best effort**, bắt đúng lỗi phụ trợ trong phạm vi logging, giữ E1.
- Nếu audit là **bắt buộc**, có thể phải công bố một lỗi tổng hợp hoặc trạng thái fail-closed, nhưng không tùy ý đánh mất thông tin E1.
- Nếu muốn wrap, giữ original trong `cause`.
- Nếu có lỗi phụ, cân nhắc `suppressed` theo ngữ nghĩa Java phù hợp.
- Không bắt `Throwable` rộng rồi trả thành công trừ khi có hợp đồng đặc biệt và lý do hợp lệ.

### 20.1. Finally và dọn guard

Một cleanup `finally` tự ném exception có thể che lỗi cũ trong Java. Vì vậy dọn context nên **đơn giản, đáng tin và ít side effect**, không đưa I/O hoặc logic phức tạp vào đó khi không cần.

### 20.2. Exception path có thể làm lệch depth

Nếu decrement depth chỉ nằm trong đường return bình thường, một exception sẽ làm depth còn cao. Invocation tiếp theo trên cùng thread có thể bị đánh nhầm là nested, hoặc bị guard bỏ qua.

Dùng `finally` để khôi phục depth, bao gồm cả khi target hoặc advice khác ném lỗi.

## 21. Lời gọi lồng nhau trong lập trình bất đồng bộ

Với method trả `CompletableFuture`, có ít nhất hai chiều “lồng nhau”:

1. **Call-stack nesting:** invocation đồng bộ trên một thread trước khi method trả.
2. **Logical-operation nesting:** một task/callback được tạo trong operation trước nhưng hoàn tất ở thread khác về sau.

Một advice after-returning quanh method trả Future có thể chạy **trước khi Future hoàn tất**. Vì vậy:

- Depth trong `ThreadLocal` được gỡ khi method trả, dù logical task vẫn hoạt động.
- Callback có thể chạy trên thread khác mà không có context gốc.
- Exception ở completion stage không nhất thiết đi qua catch đồng bộ ở call site.
- Một callback có thể tạo một operation mới, khiến concept parent-child theo logical trace khác call stack.
- Scheduler có thể thực thi callback ngay trên thread hiện tại hoặc thread khác tùy API và trạng thái stage.

### 21.1. Dùng explicit correlation context

Nếu muốn trace nghiệp vụ xuyên async:

```text
traceId = request identity
spanId = current operation
parentSpanId = creating operation
taskId = asynchronous work item
```

Khi tạo task, truyền một **snapshot context** phù hợp vào callback hoặc executor. Định nghĩa parent-child theo **lifecycle task**, không dựa duy nhất vào depth của ThreadLocal.

### 21.2. Đừng sao chép cờ guard không suy nghĩ

Nếu copy nguyên `emitting=true` sang async task, task có thể tắt instrumentation dù nó là operation nghiệp vụ độc lập. Guard cho **đường emission hiện tại** thường có lifetime ngắn và không được truyền như trace ID thông thường.

Một context propagation thiết kế tốt phân biệt thông tin **nên mang qua thread** với các guard nội bộ **không nên rò ra scope khác**.

## 22. Virtual threads và khả năng tương thích mô hình context

Java 17 là nền tảng của ví dụ runnable. Các khả năng thread mới của Java ở các phiên bản sau có thể thay đổi chiến lược hiệu năng và context propagation, nhưng **không làm biến mất vấn đề reentrancy**.

Dù dùng thread nền tảng, thread pool, virtual thread hoặc async stage, luôn cần giải đáp:

- Một logical invocation có đổi thread không?
- Guard được gắn vào thread hay logical operation?
- Có khả năng executor tái sử dụng thread không?
- Cleanup có diễn ra khi task cancel/timeout không?
- Nested business calls có được instrumentation đầy đủ không?

Tránh đồng nhất mô hình invocation với một lựa chọn scheduling cụ thể.

## 23. Hiệu năng: recursion instrumentation có thể nhân tải đột biến

Một advice tưởng như chỉ thêm một log line vào mỗi method có thể làm chi phí tăng đáng kể khi chính logger nằm trong tập bị chọn.

Ví dụ trực giác:

```text
Một business call -> 1 log
Log method lại được advice -> thêm 1 log
Mỗi log mới lại được advice -> thêm log ...
```

Với chu trình không có điều kiện dừng, không phải overhead tuyến tính; hệ thống có thể nhanh chóng cạn stack, CPU hoặc memory.

Kể cả không vô hạn, instrumentation từng nested helper cũng có thể gây:

- Quá nhiều log/event.
- Trùng thời lượng inclusive.
- Tăng allocations và GC.
- Contention trên sink/logger.
- Latency cao ở hot path.
- Lộ thêm dữ liệu từ các method không chủ ý audit.
- Giữ stack/context quá lâu.

### 23.1. Không đo overhead bằng cách in stdout trong hot loop

`System.out.println` có thể áp đảo chi phí proxy/pointcut. Muốn benchmark dispatch cần dùng workload đại diện, warm-up, đo throughput/latency và tách chi phí I/O. Tuy nhiên trong ví dụ học tập, stdout giúp kiểm **thứ tự sự kiện**, không dùng làm số đo hiệu năng production.

## 24. Quy trình điều tra khi log lặp vô hạn

1. **Dừng hoặc cô lập luồng lỗi** trong môi trường an toàn nếu hệ thống đang tạo storm.
2. **Xác định điểm vào ban đầu**: business method nào khiến advice bắt đầu?
3. **Tìm chuỗi lặp** trong stack trace: advice → logger/metric/audit → advice.
4. **Vẽ call graph** của các helper mà advice gọi.
5. **Đối chiếu pointcut và weaving scope**: logger/helper có bị chọn ngoài ý định?
6. **Kiểm tra thứ tự aspect**: advice khác có gọi helper tạo vòng không?
7. **Kiểm tra around continuation**: có gọi `proceed` nhiều lần không?
8. **Kiểm tra guard**: có đặt đúng vị trí, reset bằng finally và scope đủ hẹp không?
9. **Kiểm tra self-invocation**: lời gọi nào qua proxy, lời nào trực tiếp?
10. **Tạo test nhỏ** chỉ chạy một operation và thu event vào bộ đệm.
11. **Kiểm thử đường lỗi**: logging sink failure, target exception, cleanup.
12. **So sánh before/after** bằng tập join point được match, sự kiện và số lần target chạy.

Không thử “sửa” bằng cách tăng stack size rồi coi hệ thống an toàn. Tăng tài nguyên có thể chỉ trì hoãn việc cạn kiệt do vòng lặp.

### 24.1. Dấu hiệu trong stack trace

Có thể thấy một mẫu frame lặp:

```text
Interceptor.invoke(...)
Logger.write(...)
Interceptor.invoke(...)
Logger.write(...)
...
```

Đây là **stack trace giả lập mang tính minh họa**, không phải output cam kết của JDK. Tên class/frame cụ thể phụ thuộc proxy/weaver, compiler và runtime.

## 25. Quy trình điều tra khi nested advice bị thiếu

1. Xác định method ngoài và trong có thuộc tập pointcut không.
2. Xác định receiver ở lời gọi trong: proxy, target hay subclass instance.
3. Kiểm tra có sử dụng `this.inner()` trên target riêng không.
4. Kiểm tra guard có bỏ **mọi depth>0** hay không.
5. Kiểm tra exclusion quá rộng có loại helper nghiệp vụ không.
6. Kiểm tra dynamic guard theo args/type/control flow.
7. Kiểm tra around ngoài có gọi `proceed` không.
8. Kiểm tra error/short-circuit có dừng đường đi trước inner không.
9. Kiểm tra class loader hoặc bytecode weaving coverage nếu không dùng proxy.
10. Dùng event collector khẳng định **số lần vào mỗi join point**, không chỉ sự tồn tại của một log.

Có lúc thiếu nested advice là **đúng yêu cầu** để tránh duplicate audit. Nhưng quyết định ấy phải được mô tả như một chính sách intentional, không là hệ quả vô tình của một cờ guard.

## 26. Ma trận kiểm thử tái nhập và lời gọi lồng nhau

| Ca kiểm thử | Điều cần xác nhận |
| --- | --- |
| Một method qua proxy | Advice enter/exit đúng một lần |
| Outer gọi inner qua proxy | Inner có advice nếu policy yêu cầu |
| Outer gọi `this.inner` trên raw target | Hiểu và xác nhận bypass nếu dùng interface delegation |
| Advice gọi logger cũng thuộc pointcut | Không có infinite self-interception |
| Guard đang phát trace | Chỉ skip đường trace tự phát sinh |
| Business nested khi không phát trace | Không bị guard loại nhầm |
| Target ném exception | Depth/guard được cleanup, lỗi gốc giữ đúng |
| Logger ném exception | Chính sách lỗi phụ trợ được áp dụng, guard không kẹt |
| Một invocation sau exception | Depth trở về 0/giá trị nền |
| Direct raw target call | Không tạo proxy advice ngoài ý định |
| Recursion hợp lệ nhiều mức | Context từng frame độc lập |
| Hai thread đồng thời | Không trộn trace/depth/request state |
| Async continuation ở thread khác | Context được truyền theo task nếu yêu cầu |
| Retry nhiều attempt | Đếm đúng logical request và attempts |
| Multiple aspects | Không tạo vòng audit ↔ security/metrics |
| Callback reenters operation | Không phá invariant, lock hoặc state |
| Instrumentation helper đổi tên | Exclusion/test vẫn phát hiện chu trình mới |
| Disable guard trong test kiểm soát | Chỉ dùng mô phỏng/cấu trúc kiểm tra an toàn, không chạy vòng vô hạn thật |

### 26.1. Test theo event sequence

Trong một test đồng bộ, thu event vào danh sách:

```text
outer.enter[0]
  inner.enter[1]
  inner.exit[1]
outer.exit[0]
```

Nếu guard chỉ chặn trace self-interception, các event outer và inner vẫn hiện diện. Ngoài thứ tự, cần assert:

- Số lần target thật chạy.
- Số lần advice phát trace.
- Số lần guard skip.
- Không để depth lệch sau lỗi.
- Không có event extra do helper tự kích hoạt.

### 26.2. Test idempotency của cleanup

Nếu cleanup được gọi lặp do lỗi hoặc retry, nó phải bảo toàn tính nhất quán. Ví dụ `ThreadLocal.remove()` không ném lỗi vì chưa có giá trị trong tình huống thông thường, nhưng một callback cleanup tùy chỉnh có thể không idempotent. Hãy thiết kế lifecycle rõ.

### 26.3. Tránh unit test chỉ kiểm kết quả return

Một operation có thể trả đúng `"OK"` dù advice log bị nhân đôi mười lần hoặc audit bị bỏ qua ở nested call. Cần test **hành vi aspect** độc lập với kết quả nghiệp vụ.

## 27. Những sai lầm phổ biến

**“Lời gọi lồng nhau là recursion.”** Không. Recursion cần chu trình gọi; nested call đơn giản không nhất thiết quay lại cùng method.

**“Mọi recursion đều là bug.”** Không. Recursion có điểm dừng và state đúng là hợp lệ.

**“Aspect tự can thiệp chỉ xảy ra khi advice gọi chính method của nó.”** Không. Chu trình có thể qua logger, repository, callback hoặc aspect khác.

**“Một cờ boolean global giải quyết tái nhập.”** Nguy hiểm: nó có thể làm thread khác bỏ qua advice và tạo race.

**“Chỉ dùng synchronized là chặn được self-interception.”** Sai: Java monitor có tính reentrant với cùng thread.

**“Bật guard suốt proceed là an toàn.”** Có thể làm mất nested business advice hợp lệ.

**“Guard chỉ cần set true mà không reset nếu exception.”** Sai: context còn kẹt sẽ gây lỗi lâu dài trên thread tái sử dụng.

**“ThreadLocal tự truyền đúng qua mọi async task.”** Sai. Context propagation phải thiết kế theo task/thread boundary.

**“Thấy depth=2 nghĩa là cùng một request ở mọi trường hợp.”** Không. Depth không phải trace ID; retry và async có semantics khác.

**“Self-invocation luôn đi qua advice.”** Không với proxy delegation vào target riêng; receiver quyết định đường gọi.

**“Self-invocation luôn bỏ qua advice.”** Cũng không đúng cho mọi kỹ thuật weaving hoặc subclass receiver.

**“Loại toàn bộ helper khỏi pointcut là luôn tốt.”** Chưa chắc: có helper thật sự cần audit. Phạm vi phải dựa vào chính sách.

**“Ngoại lệ từ logger có thể tùy ý thay business exception.”** Không nếu hợp đồng là observability best effort; phải giữ primary cause theo policy.

**“Log trùng nghĩa business đã chạy trùng.”** Không nhất thiết: một operation có thể được advice nhiều tầng mà body chỉ chạy một lần. Cần đếm riêng business invocation.

**“Không có log inner nghĩa inner không chạy.”** Sai khi inner được gọi trực tiếp target hoặc bị guard/exclusion loại.

**“Chỉnh wildcard là sửa được mọi recursion.”** Nếu chu trình nằm ở runtime condition/retry hoặc nhiều aspect, cần phân tích call graph và scope.

## 28. Bài tập có đáp án

### Bài 1 — Logger tự kích hoạt

Advice dùng `logger.write()`, pointcut lại chọn mọi execution của method có tên `write` trong package chứa logger. Có thể xảy ra gì?

**Đáp án:** logger có thể bị cùng advice kích hoạt lại, tạo recursion hoặc event storm. Cần giới hạn pointcut, loại đúng logger helper hoặc guard đường emission.

### Bài 2 — Nested calls có bị mất vì guard?

Một interceptor đặt `active=true` trước `proceed` và mọi method sau thấy active đều bỏ advice. `outer` gọi `inner` qua proxy. `inner` có được audit không?

**Đáp án:** Có thể **không**, vì cờ quá rộng. Nếu business policy cần audit inner, phải thu hẹp guard vào đường tự can thiệp, không tắt toàn bộ nested instrumentation.

### Bài 3 — Timer bị ghi đè

Aspect singleton dùng field `startTime`; outer và inner đều được advice. Vì sao duration outer có thể sai?

**Đáp án:** inner ghi đè mốc outer. Mỗi invocation cần local snapshot hoặc frame/context riêng.

### Bài 4 — `synchronized`

Advice đồng bộ hóa trên một monitor nhưng tự gọi lại cùng advice trong một thread. Monitor có tự ngăn recursion không?

**Đáp án:** Không. Java monitor là reentrant theo thread. Vẫn cần exclusion/guard/điểm dừng.

### Bài 5 — Handler lỗi rồi depth không trở về 0

Code chỉ giảm depth sau khi `proceed` trả bình thường. Nếu business ném lỗi, request sau sẽ thế nào?

**Đáp án:** Depth còn sai, có thể bị coi là nested và lọc nhầm. Cleanup cần trong `finally`.

### Bài 6 — Proxy và `this.inner()`

Caller qua interface proxy gọi `target.outer()`; bên trong target gọi `this.inner()`. Handler của proxy có chắc nhận `inner` không?

**Đáp án:** Không trong mô hình proxy ủy quyền vào target riêng. Lời gọi nội bộ chạy trên target, không quay lại proxy.

### Bài 7 — Cflow và async

Một pointcut loại những thao tác “đang trong call stack của logging”. Một callback async chạy ở thread khác. Có thể giả định bộ lọc cflow thấy cùng stack không?

**Đáp án:** Không. Logical async flow và đồng bộ call stack khác nhau. Phải xem semantics công cụ và context propagation.

### Bài 8 — Retry và số lần side effect

Retry ngoài chạy `proceed` hai lần. Inner advice ghi “save succeeded” mỗi attempt. Có thể có bao nhiêu audit event và business execution?

**Đáp án:** Tùy nơi audit nằm, có thể có hai lần inner advice và hai business attempts. Contract phải phân biệt logical request và attempt, tránh giả định đúng một lần.

### Bài 9 — Callback tái nhập dưới lock

Business giữ lock rồi gọi plugin callback; callback gọi ngược business. Nguy cơ?

**Đáp án:** Reentrancy vào trạng thái chưa ổn định, đệ quy không dừng hoặc deadlock qua lock khác. Phải xác định callback boundary và thiết kế trạng thái/lock phù hợp.

### Bài 10 — Trace guard cần truyền sang Future?

Một advice set `emitting=true` khi ghi log đồng bộ rồi tạo async task nghiệp vụ. Có nên truyền cờ này sang task?

**Đáp án:** Thường không. Đây là guard nội bộ cho đường emission ngắn, không phải thuộc tính nghiệp vụ. Trace ID hoặc correlation context có thể cần truyền, nhưng guard phải đúng vòng đời.

## 29. Checklist thiết kế AOP an toàn với tái nhập

- [ ] Hiểu rõ call graph gồm cả method mà **advice tự gọi**.
- [ ] Phân biệt nested call, recursion, reentrancy và self-invocation bypass.
- [ ] Xác định join-point kind, pointcut scope và interception boundary.
- [ ] Không đưa logger/audit sink/metrics helper vào pointcut ngoài chủ đích.
- [ ] Exclusion dựa trên chính sách và có kiểm thử, không chỉ wildcard tùy tiện.
- [ ] Guard runtime chỉ ngăn đường tái nhập cần thiết.
- [ ] Không tắt mọi nested business advice bằng một cờ “inside aspect”.
- [ ] Dùng state per invocation/frame khi cần depth, timing, snapshot.
- [ ] Không lưu mutable per-call state vào singleton field chung.
- [ ] Dọn guard và depth bằng `finally` ngay cả khi có exception.
- [ ] Nếu guard lồng nhau, khôi phục đúng state scope ngoài.
- [ ] Dùng `ThreadLocal` với cleanup và hiểu giới hạn thread pool/async.
- [ ] Không coi thread ID/depth là correlation ID duy nhất.
- [ ] Có policy rõ với logging/audit sink failure và primary exception.
- [ ] Không ghi dữ liệu nhạy cảm trong telemetry để debug recursion.
- [ ] Có test một lời gọi đơn, nested call, self-call qua proxy/direct.
- [ ] Có test business error và logger error.
- [ ] Có test hai thread chạy đồng thời và task async khi phù hợp.
- [ ] Có test retry/proceed nhiều lần và idempotency.
- [ ] Có test callback dưới lock hoặc trong transaction nếu tồn tại.
- [ ] Có test nhiều aspect kết hợp tránh vòng A → B → A.
- [ ] Có trace riêng cho business count, advice count và guard skip.
- [ ] Đo overhead trên workload thật, không chỉ dựa vào stdout demo.
- [ ] Biết rollback/disable instrumentation khi phát sinh recursion storm.
- [ ] Đã mô tả những nested invocation bị bỏ qua **có chủ đích** trong hợp đồng aspect.

## 30. Kết luận

**Tái nhập** xảy ra khi cùng logic được đi vào lần nữa trước khi lần trước hoàn tất; **lời gọi lồng nhau** là hình thức tổ chức call stack hợp lệ, còn **aspect tự can thiệp** là tình huống advice kích hoạt một đường gọi lại bị aspect chọn. Ba khái niệm liên quan nhưng không đồng nhất. Đồng thời, với proxy dựa trên target riêng, một `this.inner()` có thể **bypass** interception — vấn đề trái chiều với self-interception quá mức.

Để thiết kế đúng, hãy đặt câu hỏi: **ai gọi ai, receiver nào thực sự chạy, join point nào được chọn, state thuộc invocation nào và guard cần chặn chính xác đường nào?** Ưu tiên pointcut rõ ràng, loại trừ helper nội bộ có căn cứ, dùng guard hẹp được dọn trong `finally`, quản lý state theo frame/invocation, và tách context cho async.

Một hệ thống AOP đáng tin không chỉ tránh stack overflow; nó còn **giữ đủ nested advice hợp lệ**, không đếm trùng request, không che lỗi gốc, không rò ngữ cảnh giữa thread và có kiểm thử chứng minh cả những đường **phải được can thiệp** lẫn những đường **cố ý không can thiệp**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-concurrency-009">Thiết kế aspect an toàn khi chạy song song</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế aspect an toàn khi chạy song song

## 1. Vì sao aspect dễ trở thành điểm lỗi đồng thời?

Trong **Aspect-Oriented Programming (AOP)**, một aspect có thể can thiệp vào nhiều join point và nhiều đối tượng nghiệp vụ. Một advice thường trông rất ngắn: đo thời gian, ghi trace, tăng bộ đếm, thêm thông tin request, kiểm tra chính sách rồi tiếp tục vào target. Nhưng cùng một **aspect instance** có thể được nhiều threads gọi **đồng thời**, đặc biệt khi aspect có phạm vi dùng chung hoặc gắn với một target được gọi song song.

Nếu aspect giữ dữ liệu của từng lần gọi trong **field dùng chung**, các invocations có thể ghi đè dữ liệu của nhau. Nếu advice dùng `ThreadLocal` nhưng không cleanup, thread pool có thể chuyển dữ liệu request này sang request sau. Nếu dùng lock quá rộng quanh `proceed()`, aspect có thể vô tình biến một target vốn xử lý song song thành xử lý tuần tự, tăng tail latency hoặc tạo deadlock. Nếu advice ghi metric sau `proceed()` nhưng không có `finally`, exception có thể làm bộ đếm đang chạy không được giảm.

Một thiết kế an toàn phải trả lời đồng thời các câu hỏi:

1. **Aspect instance được chia sẻ với ai?** Một module, nhiều targets, một target, một invocation hay một control-flow scope?
2. **State nào có thể dùng chung?** Configuration bất biến, aggregate metric, cache có kiểm soát hay dữ liệu thuộc từng request?
3. **State của mỗi invocation sống bao lâu?** Tới khi method return, tới async task completion hay tới khi logical operation kết thúc?
4. **Tính nhất quán nào được yêu cầu?** Atomic update cho một counter, invariant nhiều field, snapshot nhất quán hay thứ tự có ý nghĩa nghiệp vụ?
5. **Advice có thay đổi hành vi target không?** Serialization do lock, retry, caching, timeout, exception transformation, callback và context propagation?
6. **Làm sao kiểm thử thực sự có overlap và chứng minh cleanup đúng?** Không dựa vào `Thread.sleep()` hoặc vài dòng log tình cờ.

**Trọng tâm:** An toàn đồng thời của AOP không chỉ là “đổi `int` sang `AtomicInteger`”. Nó là sự phối hợp giữa **state ownership**, **memory visibility**, **synchronization**, **lifetime**, **reentrancy**, **async boundaries**, **error semantics** và **độ phủ interception**.

### Mục tiêu học tập

Sau bài này bạn có thể:
- Phân biệt **stateless advice**, **shared aspect state**, **per-target state** và **per-invocation state**.
- Nhận diện race trên `lastStartNanos`, `currentUser`, bộ đếm, cache, shared builder và mutable collection.
- Dùng local variables, immutable configuration, `AtomicInteger`, `LongAdder`, concurrent collections và lock đúng trường hợp.
- Giải thích vì sao **atomicity** khác **visibility**, **thread safety** khác **đúng nghĩa dữ liệu**, và **per-target** khác **per-thread**.
- Viết around advice có cleanup trên success/failure và không biến exception thành false success.
- Dùng `ThreadLocal` với `try/finally`, hiểu nested scope, thread pools và vấn đề context propagation qua async.
- Biết rủi ro của việc giữ lock khi gọi `proceed()`, việc retry/recursion và self-invocation bypass proxy.
- Đọc và chạy **Java 17** với hai invocations được đồng bộ bằng `CountDownLatch`, chứng minh kết quả và counters chính xác.
- Thiết kế test về concurrent invocation, visibility, exception, cancellation, shutdown và giới hạn bộ nhớ.

Nội dung chỉ xét các cơ chế AOP cơ bản và nâng cao nói chung. Tên scope và hành vi đồng thời của từng công cụ cần được kiểm chứng bằng tài liệu/kiểm thử của chính công cụ ấy.

## 2. Hiểu các đối tượng tồn tại trong một lần can thiệp

Một hệ thống có thể đồng thời có:

| Thành phần | Ví dụ | Có phải riêng cho mỗi lời gọi không? |
| --- | --- | --- |
| **Aspect definition** | Quy tắc pointcut và advice code. | Không nhất thiết có runtime instance. |
| **Aspect instance** | Object chứa cấu hình và bộ đếm. | Có thể chia sẻ giữa nhiều threads/targets. |
| **Target** | Một service xử lý công việc. | Có thể singleton hoặc được tạo cho mỗi request. |
| **Proxy/interceptor chain** | Wrapper chuyển tiếp tới target. | Có thể được dùng chung; phụ thuộc mô hình. |
| **Advice invocation** | Một lần thực thi advice tại join point. | Là một lần chạy cụ thể. |
| **Invocation-local state** | `startNanos`, `correlationId`, `attemptId`. | Có thể tách riêng mỗi lượt bằng local variables. |
| **Logical task state** | Trace context dùng tới async completion. | Có thể sống lâu hơn method invocation. |
| **Shared aggregate** | Tổng số calls/failures, histogram. | Có thể đồng thời được cập nhật. |

**Một aspect instance không đồng nghĩa một lời gọi.** Một aspect dùng chung có thể nhận hàng nghìn invocations đang xen kẽ giữa nhiều threads. Cũng không thể suy rằng per-target aspect an toàn: cùng một target có thể được nhiều threads gọi cùng lúc.

### 2.1. “Không có field mutable” là một điểm khởi đầu tốt

Một advice có thể nhận arguments, lấy `long started=System.nanoTime()`, gọi continuation rồi tính duration bằng **local variable**. Mỗi invocation có call frame riêng, tránh việc hai lời gọi ghi đè `started` của nhau. Việc aspect instance được dùng chung không biến local variable thành một field dùng chung.

Tuy nhiên, local variable có thể tham chiếu một **mutable object shared** như một `ArrayList` toàn cục. An toàn hay không còn phụ thuộc **object được reference**, không chỉ tên biến local.

### 2.2. State của aspect và state của target là hai vấn đề độc lập

Một aspect thread-safe không khiến target tự thread-safe. Nếu target có `int balance` và hai threads cùng sửa không đồng bộ, lỗi vẫn xảy ra sau `proceed()`. Ngược lại, một target hoàn toàn an toàn vẫn có thể bị advice làm sai kết quả/exception hoặc gây race ở logging/counters.

Khi đánh giá, viết rõ invariant cho **cả target lẫn aspect** và những tương tác giữa chúng.

## 3. Đề xuất kiến trúc: hai lớp trạng thái, ba vòng đời

Sau khi đã hiểu race condition và Java Memory Model, hãy dựng một aspect quan sát production theo hợp đồng. Ví dụ một hệ thống tiếp nhận hàng nghìn thao tác độc lập, aspect dùng chung cần đếm số invocation đang chạy, theo dõi thời lượng và ghi outcome an toàn.

Tách **dữ liệu để quyết định** khỏi **dữ liệu để quan sát**:

```text
Shared Aspect instance
   |
   +-- Immutable policy snapshot   (read-only; có revision)
   +-- Shared concurrent aggregates (inFlight / failures / throughput)
   +-- Bounded telemetry sink       (queue có giới hạn và policy lỗi)
   |
Each advice invocation
   +-- local startNanos
   +-- local operation identity (đã lọc)
   +-- snapshot policy at entry
   +-- result/error của đúng invocation
   |
Async continuation, nếu có
   +-- minimal immutable context đã capture
   +-- ownership/deadline/cleanup theo logical task
```

Các vòng đời phải được nêu rõ: **aspect/module** sở hữu shared aggregates và sink; **mỗi invocation** sở hữu local state; **mỗi task bất đồng bộ** sở hữu context và quá trình hoàn tất của chính nó. Không biến mọi thứ thành field vì chúng được sử dụng bởi cùng một aspect definition.

## 4. Viết bất biến trước khi chọn công cụ đồng bộ hóa

Thay vì bắt đầu bằng “dùng lock hay atomic?”, hãy viết điều phải luôn đúng trong một phạm vi xác định.

| Bất biến | Owner | Bằng chứng kiểm thử |
| --- | --- | --- |
| Mỗi lời gọi đã vào advice tăng in-flight một lần | Scope advice | Khi hai lời gọi overlap, in-flight đạt 2 |
| Mỗi scope đã vào sẽ giảm in-flight đúng một lần | Scope advice | Sau success và throw, in-flight trở lại 0 |
| Mỗi outcome quan sát được quy về đúng lời gọi | Context theo invocation | A không nhận trace ID/result của B |
| Kết quả/ngoại lệ caller giữ đúng hợp đồng | Advice wrapper | Trả đúng hoặc ném đúng ngoại lệ gốc |
| Snapshot policy trong một invocation nhất quán | Immutable snapshot cục bộ | Reload giữa chừng không tạo nửa phiên bản cũ/mới |
| Sink hết dung lượng có policy rõ ràng | Owner hạ tầng | Đo được drop/backpressure/error, không phát sinh vòng instrumentation |
| Shutdown chỉ kết thúc khi phần việc cần hoàn tất đã được xử lý theo policy | Owner cấp module | Không đóng shared sink vì một request kết thúc |

Nhiều bất biến tồn tại **ở ranh giới khác nhau**. Ví dụ in-flight đếm scope advice đồng bộ có thể trở về 0 khi method trả một `CompletionStage` còn pending; nếu cần số logical tasks đang chạy, phải gắn phép tăng/giảm với completion của task, không tái sử dụng metric scope đồng bộ rồi đổi nhãn.

## 5. Số liệu tổng hợp: chọn semantics trước độ hạt atomic

Từ bảng bất biến, mỗi metric cần mô tả **đơn vị đếm, thời điểm tăng/giảm và mức nhất quán khi đọc**:

- `invocationsStarted`: số lần join point thật sự đi vào advice được chọn; nếu một request qua nhiều join points thì có thể có nhiều lượt.
- `inFlight`: số scopes đã mở nhưng chưa hoàn tất theo ranh giới đã định nghĩa.
- `failures`: số scopes kết thúc bằng exception theo góc nhìn của observer; lỗi bị xử lý ở tầng trong có thể không được đếm tại tầng ngoài.
- `duration`: thời gian quanh đúng scope; nếu cần end-to-end async duration, mốc kết thúc phải theo task completion.

`AtomicInteger` thích hợp với những chuyển trạng thái cần đọc giá trị chính xác và CAS của **một biến**; `LongAdder` hữu ích cho cập nhật tổng throughput cao khi không đòi `sum()` là snapshot tuyến tính trong lúc vẫn ghi. Dù mỗi counter được cập nhật an toàn, **đọc ba counter liên tiếp không tạo ra một transaction snapshot**. Nếu báo cáo bắt buộc có bất biến liên trường tại một thời điểm, cần thêm cơ chế snapshot/quiescence phù hợp.

Đừng gắn user ID, request ID hoặc giá trị cardinality không giới hạn làm nhãn metric; chúng có thể gây bùng nổ series và kéo dài vòng đời dữ liệu nhạy cảm.

## 6. Một invocation cần một hợp đồng đi qua bốn ranh giới

Xét chuỗi `Observe(Policy(Business))`. Khi áp dụng bất biến cho một lời gọi, đặt các câu hỏi theo đúng thứ tự:

1. **Admission:** pointcut/proxy/weaving có chọn đúng join point không? Bỏ qua proxy hoặc match cả logger có thể làm metrics thiếu hoặc đệ quy.
2. **Entry:** đã capture policy revision và thông tin cần thiết một lần chưa? Counter in-flight đã tăng sau khi chắc chắn scope được mở chưa?
3. **Continuation:** method gốc được gọi bao nhiêu lần? Retry, cache hit, short-circuit và nested advice có làm đổi đơn vị đếm không?
4. **Exit:** return, exception, timeout/cancel, callback failure và cleanup có cùng owner theo đúng lifetime không?

```text
on selected call:
  snapshot minimal policy + invocation identity
  mark entry / update inFlight
  try:
      invoke next according to contract
  catch original:
      observe failure best effort, preserve original
      rethrow original
  finally:
      close owned scope and update aggregate
```

Đây là **giả mã synchronous**. Không có engine AOP chung nào bảo đảm `proceed` được gọi một lần nếu around advice chủ động short-circuit hoặc retry. Nếu telemetry failure bắt buộc làm hỏng thao tác, cần ghi rõ hợp đồng; với logging tùy chọn nên xử lý lỗi phụ để không che exception gốc.

## 7. Shared map, cache và registry cần owner riêng

Một `ConcurrentHashMap` bảo đảm một số thao tác đồng thời trên **cấu trúc map**, nhưng không làm cho value mutable tự thread-safe hay cho một chuỗi nhiều bước thành transaction.

Một cache trong aspect cần làm rõ:

| Quyết định | Ví dụ câu hỏi |
| --- | --- |
| Identity | Key theo method, target identity, tenant, config revision hay full arguments? |
| Isolation | Có cache dữ liệu của tenant A rồi trả cho B không? |
| Consistency | Điều kiện invalidation khi policy hoặc target thay đổi? |
| Size | TTL, capacity và eviction có giới hạn không? |
| Duplication | Khi nhiều threads cache miss, có cho chạy song song hay cần single-flight? |
| Ownership | Nếu value giữ target/classloader, ai xóa khi owner hết vòng đời? |

Một `computeIfAbsent` có thể giải quyết một số race tạo entry, nhưng mapping function không nên thực hiện blocking I/O hay gọi đệ quy vào cùng map khi API cấm hoặc cơ chế đồng bộ bên trong có thể gây vấn đề. Với nhu cầu “chỉ một computation trên mỗi key”, cần thiết kế single-flight và đường timeout/exception rõ ràng, thay vì suy từ tên concurrent map.

## 8. Scope nhỏ, cleanup đúng và telemetry không làm chậm nghiệp vụ

Một advice tốt để đo/ghi sự kiện nên ngắn gọn: thu thập những trường tối thiểu, cập nhật counter, chuyển tiếp nghiệp vụ và giải phóng đúng scope. Nếu phải đồng bộ hóa aggregate, chỉ khóa **đoạn cập nhật thật sự cần bảo vệ**, không giữ mutex chung xuyên `proceed()` hoặc một lời ghi log qua mạng.

Đối với logging/tracing tùy chọn:

- Chọn trường được phép thay vì serialize toàn bộ request/result.
- Không ghi event khi đang giữ lock bảo vệ nghiệp vụ, trừ khi invariant yêu cầu.
- Dùng queue có giới hạn và chính sách backpressure/drop rõ ràng nếu xuất bất đồng bộ.
- Loại trừ logger/exporter khỏi pointcut để ngăn advice tự can thiệp.
- Phục hồi scope/context trong `finally`; không giữ `ThreadLocal` từ request này qua request khác.
- Xử lý lỗi formatter/sink theo hợp đồng để không thay outcome nghiệp vụ ngoài chủ đích.

Đối với audit hoặc policy bắt buộc, “best effort” có thể không đáp ứng yêu cầu; phải thiết kế độ bền, mức nhất quán và failure policy riêng. Phần tiếp theo sử dụng ví dụ Java 17 để **kiểm chứng invariant in-flight và kết quả của hai lời gọi cùng chạy**, rồi mở rộng test cho lỗi, concurrency, shutdown và async.

## 9. Ví dụ Java 17 runnable: hai lời gọi thật sự chồng lấn và thống kê an toàn

Chương trình **`ConcurrentAspectDemo.java`** mô phỏng một aspect quan sát bằng JDK dynamic proxy:

- Có **một target** và **một aspect state dùng chung**.
- Hai worker threads gọi `proxy.execute("A")` và `proxy.execute("B")`.
- Target dùng `CountDownLatch(2)` để bảo đảm **cả hai invocations đồng thời vào target trước khi cả hai được phép trả**.
- Advice dùng `LongAdder` cho các bộ đếm tổng, `AtomicInteger` cho số invocation đang chạy và max overlap.
- Các exceptions reflective được gỡ khỏi `InvocationTargetException` để caller thấy lỗi gốc.
- Một lời gọi `"BAD"` sau khi hai calls thành công minh họa cleanup trên failure.
- Các threads **không in log trong lúc chạy song song**; main chỉ in results sau `Future.get()`, nên output không phụ thuộc scheduling giữa A và B.

**Quan trọng:** JDK dynamic proxy trong ví dụ là một hình thức interception tại interface boundary, **không phải bytecode weaving**. Nó chỉ quan sát các calls đi qua proxy.

Tạo file **`ConcurrentAspectDemo.java`**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public class ConcurrentAspectDemo {
    interface Service {
        String execute(String id);
    }

    static final class Target implements Service {
        private final CountDownLatch bothEntered = new CountDownLatch(2);

        @Override
        public String execute(String id) {
            bothEntered.countDown();
            try {
                bothEntered.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", interrupted);
            }

            if ("BAD".equals(id)) {
                throw new IllegalArgumentException("bad id");
            }
            return "OK:" + id;
        }
    }

    static final class MetricsAspect {
        final LongAdder calls = new LongAdder();
        final LongAdder successes = new LongAdder();
        final LongAdder failures = new LongAdder();
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();
    }

    static Service observed(Service target, MetricsAspect aspect) {
        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] { Service.class },
                (proxy, method, args) -> {
                    String invocationId = (String) args[0]; // local từng call
                    if (invocationId == null) {
                        throw new IllegalArgumentException("null id");
                    }

                    aspect.calls.increment();
                    int concurrent = aspect.inFlight.incrementAndGet();
                    aspect.maxInFlight.accumulateAndGet(concurrent, Math::max);

                    try {
                        Object value = method.invoke(target, args);
                        aspect.successes.increment();
                        return value;
                    } catch (InvocationTargetException wrapped) {
                        aspect.failures.increment();
                        throw wrapped.getCause();
                    } finally {
                        aspect.inFlight.decrementAndGet();
                    }
                }
        );
    }

    public static void main(String[] args) throws Exception {
        MetricsAspect aspect = new MetricsAspect();
        Target target = new Target();
        Service proxy = observed(target, aspect);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> proxy.execute("A"));
            Future<String> second = pool.submit(() -> proxy.execute("B"));

            // Hai targets phải cùng đến latch trước khi được trả.
            // Future.get() cũng bảo đảm đọc kết quả sau completion.
            System.out.println("RESULT A=" + first.get());
            System.out.println("RESULT B=" + second.get());

            try {
                proxy.execute("BAD");
            } catch (IllegalArgumentException ex) {
                System.out.println("RESULT ERROR=" + ex.getMessage());
            }

            System.out.println("STATS calls=" + aspect.calls.sum()
                    + " success=" + aspect.successes.sum()
                    + " failure=" + aspect.failures.sum()
                    + " inFlight=" + aspect.inFlight.get()
                    + " maxInFlight=" + aspect.maxInFlight.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac ConcurrentAspectDemo.java
java ConcurrentAspectDemo
~~~

**Stdout kỳ vọng, đúng thứ tự:**

~~~text
RESULT A=OK:A
RESULT B=OK:B
RESULT ERROR=bad id
STATS calls=3 success=2 failure=1 inFlight=0 maxInFlight=2
~~~

### 9.1. Vì sao chắc chắn hai lời gọi có overlap?

Target có `CountDownLatch(2)`. Invocation đầu tiên gọi `countDown()` rồi chờ `await()`. Chỉ khi invocation thứ hai cũng gọi `countDown()`, latch mới bằng 0 và hai invocations có thể tiếp tục. Cả hai handler đã tăng `inFlight` trước khi target chờ, và invocation đầu không thể giảm `inFlight` trước khi invocation thứ hai đã tăng.

Vì thế `maxInFlight` **chắc chắn đạt ít nhất 2**. Có đúng 2 worker threads và không có invocation khác trước khi cả hai futures hoàn tất, nên max là **2**. Đây là bằng chứng đồng thời mạnh hơn dùng `Thread.sleep` hay quan sát thứ tự log ngẫu nhiên.

### 9.2. Vì sao kết quả `RESULT A` luôn được in trước `RESULT B`?

Main gọi `first.get()` rồi in A, sau đó gọi `second.get()` và in B. Dù thread nào hoàn tất trước, thứ tự **in của main** luôn là A rồi B. Không được hiểu output này là “worker A hoàn thành trước B”.

`Future.get()` giúp đợi task completion và cung cấp những bảo đảm memory-visibility cần thiết để main đọc kết quả tasks.

### 9.3. Vì sao có 3 calls, 2 successes, 1 failure?

Hai calls A/B đi qua proxy, target trả `OK:A` và `OK:B`, nên `successes` tăng 2. Call `BAD` được thực hiện sau, vẫn qua proxy; target ném `IllegalArgumentException`. `Method.invoke` bọc lỗi bằng `InvocationTargetException`, handler gỡ `getCause()`, tăng `failures` rồi ném lại. Caller bắt đúng loại lỗi `IllegalArgumentException`.

`calls` tăng một lần ở mỗi invocation đi qua handler, tổng 3. Số này được tính **sau khi cả ba invocations đã kết thúc**, không phải snapshot đang tranh chấp concurrent updates.

### 9.4. Vì sao `inFlight=0` sau khi có lỗi?

`inFlight.decrementAndGet()` nằm trong `finally`. Nó chạy khi target trả bình thường và khi `Method.invoke` ném `InvocationTargetException`. Sau ba lời gọi, active count trở về 0.

Đây là thói quen quan trọng: mọi state “đang hoạt động” phải có **một điểm cleanup chắc chắn** khi rời synchronous advice scope. Nếu advice có thể return early hoặc throw ở đoạn setup trước `try`, cần điều chỉnh biên cleanup cho mọi đường đã thực sự tăng counter.

### 9.5. Vì sao `LongAdder` phù hợp demo?

`calls/successes/failures` chỉ cần tổng aggregate sau khi tasks đã hoàn tất; không dùng mỗi increment làm ID, cũng không đòi snapshot nhất quán tại mọi microsecond. `LongAdder` phù hợp cho dạng metrics này.

`inFlight` lại cần giá trị vừa tăng tại mỗi invocation để tính **max overlap**; dùng `AtomicInteger.incrementAndGet()` và `accumulateAndGet(Math::max)` phù hợp.

### 9.6. Code thể hiện state nào là riêng từng invocation?

`invocationId` là **local variable** của callback handler; invocation A và B có local riêng. Nó không được lưu vào shared aspect field, vì vậy không có `aspect.currentId` để thread A/B ghi đè. `MetricsAspect` chỉ chứa aggregate counters được đồng bộ qua API atomic/concurrent.

### 9.7. Ranh giới và giả định demo

- Latch gồm **đúng hai arrivals** dùng để chứng minh overlap; nếu chỉ gửi một call, method sẽ đợi mãi. Không dùng latch này trong service production; đây là **test harness**.
- `ExecutorService` có hai workers; `Future.get()` có thể block nếu test bị lỗi. Một integration test thực nên có **timeout** và thông báo thất bại rõ để tránh treo.
- Method `Service.execute` chỉ đại diện cho một interface method; handler chưa định nghĩa riêng `equals/hashCode/toString` hay pointcut matching phức tạp. Trên API thật, cần chính sách cho Object methods, overloads và reflection.
- Trường hợp input null bị từ chối **trước khi đếm calls** theo một chính sách minh họa; vì vậy định nghĩa metric calls của demo là “các invocation có input id hợp lệ đã qua bước validation”. Nếu phải đếm cả rejected calls, di chuyển phép đếm ra boundary khác và test lại.
- `MetricsAspect` không ghi logs per-thread và không gắn metrics trực tiếp vào target. Direct call tới `target.execute` bỏ qua handler của proxy.
- Không có async `CompletionStage` trong demo; `ExecutorService` chỉ dùng để tạo hai concurrent synchronous invocations, cho phép stdout được quyết định bởi main.
- Không chứng minh `LongAdder.sum()` là snapshot tuyến tính khi updates còn đang chạy; ở đây đọc **sau completion**.
- Đây là AOP qua proxy, không phải một công cụ source/bytecode weaving; không áp kết quả về self-call hoặc class identity cho mọi engine.

## 10. Vì sao đặt một lock quanh toàn bộ proceed có thể gây lỗi?

Một cách “sửa race” vội vàng:

~~~java
synchronized (aspect) {
    return proceed();
}
~~~

Đây chỉ là **mã minh họa**: giả định `aspect` và `proceed()` được khai báo trong một advice. Nó khiến một aspect instance dùng chung trở thành **nút thắt khóa** cho mọi target invocation nếu tất cả cần lock đó.

Hậu quả có thể là:
- Tăng throughput contention, p95/p99 latency.
- Một method I/O chậm khiến các methods khác cùng aspect phải chờ.
- Blocking trong `proceed()` dẫn tới deadlock khi một lời gọi cần callback từ công việc cũng phải qua lock.
- Reentrancy/lock ordering với locks của target hoặc các aspects khác trở nên khó quản lý.
- Dễ che giấu data race của aspect mà không sửa ownership sai của state.

**Giải pháp ưu tiên:** tách state per-invocation thành local, dùng atomic primitives cho aggregate, giới hạn scope lock chỉ quanh **bất biến chung thật sự cần bảo vệ**. Nếu business contract bắt buộc serial execution, khóa rộng có thể hợp lý, nhưng đó là **quyết định ngữ nghĩa** cần được công bố và đo.

### 10.1. Lock scope nên nhỏ

Đừng giữ lock chỉ để log I/O hoặc đợi network. Nếu phải bảo vệ một map và value, thao tác khóa chỉ nên thực hiện phần chuyển trạng thái tối thiểu, sau đó thả lock trước khi gọi các API ngoài.

### 10.2. Lock ordering giữa nhiều aspects

Nếu aspect A luôn khóa L1 rồi L2, còn aspect B khóa L2 rồi L1, hai threads có thể deadlock. Khi nhiều advice cùng join point, **precedence/ordering** không chỉ tác động log mà còn thứ tự acquire locks. Nên đặt chính sách lock order toàn hệ thống hoặc tránh nested locks qua continuation.

### 10.3. Reentrancy và callback

`synchronized` trên Java monitor là reentrant cho cùng thread, nhưng điều này **không** làm mọi pattern callback an toàn. Một callback chạy ở thread khác không có cùng reentrant ownership; lock đang giữ có thể ngăn callback cần để task hiện tại kết thúc.

## 11. ThreadLocal: cách dùng đúng và vì sao vẫn có thể rò

Một `ThreadLocal<TraceContext>` gắn giá trị với **thread hiện tại**, không gắn tự nhiên với logical request hay aspect instance. Khi thread pool worker xử lý request A rồi B, một giá trị không được dọn có thể “rò” sang B.

### 11.1. Scope có restore/remove trong finally

Mã minh họa Java:

~~~java
// Giả định TRACE và ctx, proceed() đã tồn tại.
TraceContext previous = TRACE.get();
TRACE.set(ctx);
try {
    return proceed();
} finally {
    if (previous == null) {
        TRACE.remove();
    } else {
        TRACE.set(previous);
    }
}
~~~

Kỹ thuật giữ previous value cho **nested scopes**. Nếu chỉ `remove()` vô điều kiện ở inner advice, nó có thể làm mất context outer còn đang cần sau khi inner return.

Ở production, cần định nghĩa rõ semantics nếu một scope trước đó đã `set(null)` có ý nghĩa khác “chưa có value”; đoạn minh họa xử lý cả hai như không có context.

### 11.2. Thread pool không có nghĩa request-local

Một `ExecutorService` tái sử dụng worker threads. Nếu advice viết `TRACE.set(request)` nhưng chỉ `remove()` ở nhánh success, request A ném lỗi rồi B được thực thi trên cùng thread có thể nhìn thấy A. Vấn đề bao gồm cả **dữ liệu riêng tư**, không chỉ log sai.

### 11.3. Không truyền ThreadLocal “tự động” qua async

`CompletableFuture.supplyAsync()` hoặc callback có thể chạy trên thread khác. Value trong `ThreadLocal` của caller không tự có mặt ở worker. `InheritableThreadLocal` cũng không phải giải pháp tổng quát cho thread pools, vì inheritance xảy ra lúc tạo thread, không theo mỗi logical task.

Cần snapshot **dữ liệu tối thiểu cần truyền** và thiết lập/restore trong scope của task tại thread mới, hoặc dùng abstraction context propagation có hợp đồng rõ. Không giữ context của caller mở mãi trong pool thread để chờ task completion.

### 11.4. Scope theo logical operation khác scope theo thread

Một logical operation có thể đi qua các threads T1, T2 và T3. Ngược lại một thread T1 có thể chạy nhiều operations nối tiếp. Nếu requirement là trace theo operation, gắn state vĩnh viễn vào T1 là sai owner. Đây là khác biệt nền tảng giữa **per-thread aspect state** và **per-control-flow/task state**.

## 12. Async và CompletionStage: aspect cần biết mình đo đến mốc nào

Method:

~~~java
CompletionStage<String> fetchAsync(String key);
~~~

trả về một **handle**; tác vụ có thể hoàn tất sau khi method return. Around advice đồng bộ:

~~~text
started = now()
result = proceed()         # có thể return CompletionStage đang pending
record(now() - started)     # đo invocation/dispatch latency
return result
~~~

không tự đo tới khi stage completed. Nếu muốn đo end-to-end, observer có thể đăng ký callback dựa trên một **per-task snapshot** `started` và operation ID, sau đó record outcome khi task completed.

### 12.1. Tạo dependent stage có thể đổi semantics

Trong Java, `stage.whenComplete(observer)` trả **một dependent stage**; bản thân lời gọi đăng ký callback không thay đổi stage gốc. Nếu advice return dependent stage thay original, lỗi ném ra từ observer có thể ảnh hưởng outcome của dependent stage.

Nếu mục tiêu chỉ quan sát và cần giữ identity/lifecycle của handle, có thể đăng ký callback rồi **return original**. Nhưng vẫn phải quyết định xử lý lỗi của observer và tránh giữ unbounded callbacks với futures không bao giờ complete.

### 12.2. Không làm blocking join chỉ để “dễ lấy result”

Một advice gọi `join()` trên future pending có thể block caller thread, đổi API async thành synchronous wait, tạo deadlock khi executor không còn workers và làm tail latency xấu. Async completion cần được xử lý như async, không chỉ có `try/catch` quanh method return.

### 12.3. Cancellation và timeout

Một future bị cancel không bảo đảm I/O đang chạy dừng tức thì; callback/metrics cleanup phải dựa trên trạng thái task và ownership hợp lệ. Một timeout observer không được coi là phép hủy remote action đã cam kết. Nếu aspect giữ in-flight task count, phải quy định chính xác mốc decrement: khi method return handle, khi task completed, khi caller cancel hay khi resource thực sự dừng.

### 12.4. Bounded state

Nếu một aspect lưu `Map<Id,TaskContext>` cho mọi pending task, các tasks treo có thể giữ memory vô hạn. Cần quota, timeout/expiry, cleanup trên success/error/cancel và bảo đảm không giữ toàn bộ arguments/target khi chỉ cần một ID và timestamp.

## 13. Reentrancy và recursion trong aspect

Một aspect có thể kích hoạt chính nó gián tiếp:

~~~text
Service method
  -> Metrics advice
      -> MetricsExporter.record()
          -> Metrics advice
              -> MetricsExporter.record()
                  -> ...
~~~

Nếu pointcut chọn cả các methods của metrics exporter, instrumentation có thể đệ quy vô hạn hoặc làm số liệu trùng. Nên cấu hình exclude phù hợp hoặc dùng một cơ chế **reentrancy guard** với lifecycle đúng, theo khả năng engine.

### 13.1. Guard bằng ThreadLocal phải cleanup

Một guard thread-scoped có thể lưu `Boolean` đang ở trong advice:

~~~text
if guard == true:
    proceedWithoutMetrics()
else:
    guard = true
    try:
        metrics(); proceed()
    finally:
        restorePreviousGuard()
~~~

Đây là giả mã. Cần lưu previous state để hỗ trợ nesting và không giữ guard vĩnh viễn sau error. Nhưng với async task được chuyển thread, guard thread-local không tự bao logical operation. Ngoài ra, một advice được dệt trực tiếp vào code khác proxy boundary có semantics riêng.

### 13.2. Guard có thể che nested operations quan trọng

Không tự bỏ metric mọi nested call chỉ vì “tránh recursion”. Nếu yêu cầu đo nested service calls, chỉ loại những đường instrumentation gây vòng lặp hoặc phân loại event rõ; một guard quá rộng có thể làm thiếu telemetry.

### 13.3. Advice chạy nhiều lần do retry

Một around advice thực hiện retry có thể gọi continuation nhiều lần, nếu engine cho phép. Nếu nested metric nằm bên trong retry, nó có thể chạy mỗi attempt; nếu ở ngoài, nó đo logical operation. Phải định nghĩa rõ lúc nào tăng `calls`, `attempts`, `failures` và `inFlight`, tránh đếm một lỗi nhiều lần do nhiều advice cùng catch/rethrow.

## 14. Exception handling và độ an toàn của advice

Một observer không nên tự nhiên biến lỗi nghiệp vụ thành success hoặc loại lỗi khác chỉ vì chạy song song.

### 14.1. Gỡ wrapper reflection đúng cách

Với JDK dynamic proxy, `Method.invoke` bọc exception từ target trong `InvocationTargetException`. Handler thường lấy `getCause()` rồi ném lại nguyên lỗi khi mục tiêu là transparency.

Nếu interface có checked exceptions, phải giữ đúng throws contract của proxy; không tùy ý ném checked type không được khai báo rồi mong caller luôn nhận trực tiếp.

### 14.2. Metrics có thể khác nếu advice throw trước khi target chạy

Một advice before validation có thể throw trước `proceed()`. Nếu increment `calls` sau validation, metric không đếm denied attempts. Nếu increment trước, nó đếm cả denied attempts. Đây là **định nghĩa measurement boundary**, không phải mặc định một cách đúng cho mọi hệ thống.

Trong ví dụ Java 17, handler kiểm tra null ID trước khi increment. Vì thế `calls` có nghĩa là **valid-id intercepted invocations**, không phải mọi API entry.

### 14.3. Lỗi của instrumentation không nên che lỗi target nếu policy là best effort

Một `finally` gọi log sink mà sink tự throw có thể thay exception target hoặc return value. Nếu telemetry được thiết kế best-effort, cần cách cô lập/ghi nhận lỗi logger, không nuốt mọi `Throwable` rồi để mất lỗi nghiêm trọng.

Với audit bắt buộc, chính sách có thể ngược lại: ghi audit thất bại khiến operation bị từ chối. Nhưng đây là một **quyết định hợp đồng**, phải được nói rõ và test ở cả single-thread lẫn concurrent load.

### 14.4. Handler và shared fields cần lifecycle rõ

Khi aspect shutdown, nếu vẫn có invocations active, việc đóng exporter/buffer quá sớm có thể làm advice errors. Một `closed` flag atomic có thể giúp quyết định nhận mới/flush sau khi active về 0, nhưng chỉ một flag không tự bảo đảm invariant đa bước. Cần owner và shutdown protocol hợp lệ.

## 15. Proxy, weaving và độ phủ đồng thời

Một proxy interface chặn các lời gọi **đi qua proxy**. Target tự gọi `this.inner()` hoặc caller gọi target trực tiếp có thể bypass handler của delegating proxy. Điều đó làm metrics thiếu, nhưng không nhất thiết làm target chạy tuần tự hay an toàn hơn.

Một weaver source/bytecode có thể chèn hook trực tiếp vào method execution, có thể quan sát cả internal call nếu join point được weave. Tuy nhiên:
- Không phải mọi công cụ hỗ trợ cùng loại join point.
- Scope weaving có thể bỏ qua classes/loader.
- Thứ tự nhiều aspects có thể làm thay đổi lock acquisition.
- Hook bytecode có thể được nhiều threads chạy đồng thời như thường.
- Dệt trực tiếp không tự biến mutable state của aspect thành thread-safe.

### 15.1. Không dùng synchronized wrapper như “bản vá” cho target tùy tiện

Một proxy khóa mọi lời gọi có thể làm giảm race của target, nhưng direct-target call hoặc self-call bypass proxy vẫn có thể không chịu lock đó. Nếu business invariant cần serialization, bảo vệ ở **trusted concurrency boundary** phù hợp và test mọi entry path, không dựa vào “tất cả callers chắc chắn qua proxy” khi chưa chứng minh.

### 15.2. Object identity và locks

`synchronized(proxy)` và `synchronized(target)` dùng **hai monitors khác nhau** nếu proxy/target là objects riêng. Nếu một số callers khóa target và số khác khóa proxy, chúng không điều phối cùng một critical section. Đây là lỗi substitutability/concurrency rất khó nhận ra.

### 15.3. Advice có thể đổi thread-affinity

Một around advice tự đưa target execution sang worker thread có thể vi phạm API vốn đòi chạy trên caller thread (UI, actor, thread-confined resource). Tính an toàn không chỉ là state không race, mà còn giữ đúng thread-affinity, ordering và cancellation semantics đã công bố.

## 16. Performance, contention và quan sát đúng khi tải cao

Một advice thread-safe vẫn có thể gây bottleneck.

### 16.1. Những metric cần đo

- Số concurrent invocations, in-flight high-water mark.
- Latency của advice trước/sau target.
- CPU contention hoặc thời gian chờ locks.
- Throughput, p50/p95/p99 và tail amplification.
- Logging queue depth/dropped events.
- Cache hit/miss/collision/eviction.
- Số tasks pending và context entries giữ trong registry.
- Time spent in metrics exporter/serializer.
- GC retention do aspects/handlers/callbacks.

Một global lock bảo đảm không mất update nhưng có thể serialize toàn ứng dụng. Một lock-free counter giảm contention nhưng vẫn có thể tạo quá nhiều logs hoặc cardinality.

### 16.2. Không dùng wall-clock làm duration monotonic

`System.currentTimeMillis()` có thể chịu ảnh hưởng wall-clock adjustments. Với elapsed time trong một JVM, dùng `System.nanoTime()` và tính chênh lệch; không dùng giá trị tuyệt đối `nanoTime` như timestamp để lưu liên tiến trình.

### 16.3. LongAdder và snapshot

`LongAdder.sum()` trong lúc updates đang chạy không phải snapshot atomic. Nếu dashboard đọc `calls`, `success`, `failure` ở thời điểm hơi khác nhau, tổng có thể tạm thời không khớp. Trong test của bài, đọc sau khi workers hoàn tất để nhận con số xác định.

### 16.4. Sampling và data privacy

Ghi args/result của mọi concurrent call có thể chứa secrets và chiếm nhiều băng thông. Dùng sampling/bounded queues/sanitization nhưng phải phân biệt **sampled trace** với **đếm sự kiện bắt buộc**. Metrics cardinality theo raw request ID có thể tăng vô hạn; đừng đưa per-request state vào metric labels tùy tiện.

## 17. Kiểm thử aspect chạy song song đúng cách

### 17.1. Test bắt buộc phải có overlap

Chỉ chạy 1.000 tasks trong thread pool không chứng minh có hai advice cùng hoạt động tại một thời điểm. Chúng có thể chạy nối tiếp rất nhanh. Dùng:
- `CountDownLatch` để buộc nhiều workers vào một barrier.
- `CyclicBarrier` nếu cần nhiều vòng, kèm timeout.
- `Phaser` nếu số participants/lifecycle phức tạp.
- Bắt thời điểm `inFlight >= 2` trong một critical region có kiểm soát.

Tránh `Thread.sleep` như phép chứng minh timing: máy chậm/nhanh hoặc scheduling khác có thể làm test flaky.

### 17.2. Ma trận test

| Trường hợp | Kỳ vọng |
| --- | --- |
| Hai calls cùng vào target | `maxInFlight >= 2` khi không chủ đích serialize. |
| N calls thành công | `successes=N`, tổng theo policy, inFlight về 0. |
| Một target throw | Exception type/cause giữ đúng, failure +1, inFlight về 0. |
| Before advice throw | Không có target invocation; counters đúng theo boundary khai báo. |
| Around advice skip | Không có target execution; result/failure theo policy. |
| Retry attempts | Phân biệt logical count và attempts. |
| Target tự gọi nội bộ | Proxy có thể không thấy inner; kết quả test phải theo mechanism. |
| Reentrant advice | Không recursion vô hạn; nested context restore đúng. |
| ThreadLocal cleanup | Request B không nhìn context A trên reused worker. |
| Async stage pending | Không đếm task completion ở method return nếu policy end-to-end. |
| Async error/cancel | Completion context được giải phóng đúng. |
| Concurrent map/cache | Không tạo value trùng ngoài contract; map bounded. |
| Shared target state | Target invariants giữ đúng dưới concurrent calls. |
| Shutdown with active calls | Không đóng resource khi advice vẫn đang dùng nếu contract cấm. |
| High contention | Throughput/tail latency còn trong ngân sách. |

### 17.3. Test exception bằng loại lỗi thật

Với JDK proxy, muốn assert handler bảo toàn `IllegalArgumentException`, nên chạy method target ném lỗi đó rồi kiểm loại/cause ở caller; không chỉ assert “có exception bất kỳ”.

### 17.4. Test ThreadLocal cùng một worker thread

Để chứng minh cleanup, có thể dùng **single-thread executor** cho hai requests liên tiếp: A set context rồi throw, B đọc context lúc bắt đầu và phải thấy giá trị mặc định đúng, không phải A. Nếu dùng 2 threads không kiểm soát, B có thể chạy trên thread khác và test vẫn pass dù leak trên A thread.

### 17.5. Test concurrency không nên dựa vào thứ tự stdout của workers

Hai threads có thể in xen kẽ; nếu output order không phải yêu cầu nghiệp vụ, assert **counts/outcomes** và happens-before được thiết kế. Ví dụ runnable trong bài in từ main sau `Future.get`, nhờ đó stdout là deterministic nhưng vẫn xác nhận overlap qua latch và `maxInFlight`.

### 17.6. Dùng timeout cho barrier trong test thực

`CountDownLatch.await()` không timeout trong ví dụ tối giản có thể treo nếu worker thứ hai không chạy. Khi biến demo thành test CI, dùng `await(timeout, unit)` và `Future.get(timeout, unit)`, báo lỗi rõ khi thiếu participant; đảm bảo shutdown trong `finally`.

## 18. Thiết kế shutdown, lifecycle và không giữ state vô hạn

### 18.1. Aspect shared thường sống lâu hơn một target

Một exporter/registry chung có thể phục vụ nhiều target. Khi target A kết thúc, không được đóng exporter khi B vẫn đang dùng. Cần owner cấp module/service và shutdown ordering hợp lệ.

### 18.2. Per-target state cũng có thể bị nhiều threads cùng gọi

Nếu một target singleton nhận 20 threads, per-target aspect state là **state dùng chung cho 20 concurrent calls**. Không coi “per-target” là “per-request”. Muốn đo request riêng, dùng per-invocation context.

### 18.3. Memory retention

Một global registry giữ `Map<Target,State>` với strong keys không được remove có thể làm target còn reachable vô hạn. Một aspect lưu `lastThrowable` hoặc `lastArgs` có thể giữ object graph chứa secrets. Cần cleanup, bounded cardinality và quyền sở hữu rõ.

### 18.4. Không dựa vào GC để đóng resource

Nếu aspect giữ file, socket, listener hay callback registration, giải phóng theo `close/unregister/cancel` của owner, không chờ finalization/GC có thời điểm không xác định.

### 18.5. Shutdown đồng thời với advice

Nếu shutdown có thể chạy cùng lúc với invocations, xác định chính sách:
- Chặn invocations mới trước hay sau khi đánh dấu closing?
- Đợi in-flight về 0 hay deadline?
- Flush/close metrics buffer khi nào?
- Một invocation sau khi shutdown bắt đầu phải fail loại lỗi nào?
- Nếu close tự ném, có được che lỗi target đang xử lý không?

Cần state machine/lifecycle contract, không chỉ thêm một `volatile boolean closed` rồi bỏ qua các chuyển trạng thái.

## 19. Những lỗi phổ biến trong AOP đồng thời

**“Aspect dùng chung nên lưu `startTime` vào field cho tiện.”** Sai khi nhiều calls chồng lấn; dùng per-invocation local state.

**“Đánh dấu field là volatile đủ sửa mọi race.”** Không. Volatile không làm compound update atomic và không tách context từng invocation.

**“Per-target aspect chắc chắn chỉ có một thread.”** Không. Một target singleton có thể nhận nhiều workers.

**“LongAdder luôn cho tổng chính xác tại mọi thời điểm.”** Không. `sum()` không phải atomic snapshot khi updates đồng thời.

**“AtomicInteger cho từng field bảo đảm invariant giữa tất cả fields.”** Không. Cần giao thức cập nhật/đọc đồng bộ ở phạm vi invariant.

**“ConcurrentHashMap nghĩa là check-then-put tự atomic.”** Không; nhiều operations độc lập có thể interleave.

**“Dùng synchronized quanh toàn bộ proceed là tối ưu.”** Không mặc định; có thể gây contention/deadlock và làm thay đổi throughput semantics.

**“Một ThreadLocal là state per-request.”** Không với thread pools/async; phải restore/remove.

**“InheritableThreadLocal luôn truyền đúng request context tới executor.”** Không; thread pool reuse khiến inheritance không đại diện mỗi task.

**“Finally chạy khi async future hoàn tất.”** Không nếu finally bao synchronous method invocation đã return stage.

**“Future cancelled nghĩa là remote work chắc chắn dừng.”** Không; phụ thuộc task/resource contract.

**“Advice nhận Throwable nào cũng nên catch rồi return success để tránh lỗi.”** Sai; che lỗi nghiệp vụ và có thể mất invariants.

**“Logging ở nhiều aspects trước/sau luôn có một thứ tự toàn hệ thống.”** Không với concurrent invocations; chỉ có quan hệ order trong từng flow theo thiết kế.

**“Proxy chặn mọi lời gọi của target.”** Không với direct target/self-invocation trong delegating proxy.

**“Dệt trực tiếp method body sẽ tự làm state aspect thread-safe.”** Không. Hook có thể được nhiều threads chạy song song.

**“Demo có hai futures thì chắc chắn hai methods cùng chạy.”** Không nếu không có barrier/overlap evidence.

**“Một test happy path chứng minh aspect an toàn.”** Không; cần throw, timeout, cancellation, overlap, reentrancy và shutdown.

## 20. Bài tập vận dụng có đáp án

### Bài 1 — Timer dùng chung

Hai threads A/B cùng dùng một aspect có field `long started`. B overwrite sau khi A ghi; A tính duration sai. Chỉ thêm `volatile` có sửa không?

**Đáp án:** Không. Visible value vẫn có thể là của B; dùng start local theo mỗi invocation.

### Bài 2 — Bộ đếm

100 threads cùng chạy `int count++` trong shared aspect. Có chắc count tăng 100 không?

**Đáp án:** Không. Dùng `AtomicInteger`, `LongAdder` hoặc synchronization phù hợp với semantics.

### Bài 3 — Snapshot metrics

`calls` và `successes` là LongAdder. Khi tasks đang chạy, `calls.sum()` có thể không bằng `successes.sum()+failures.sum()`. Có nhất thiết bug không?

**Đáp án:** Không. Còn in-flight calls và các reads không phải snapshot atomic; xác minh boundary trước khi kết luận.

### Bài 4 — Target singleton

Một aspect per-target gắn với singleton service. Có thể lưu `currentUser` mutable trong field của aspect không?

**Đáp án:** Không an toàn nếu service nhận concurrent users; dùng invocation/task context, không dùng field per-target chung cho per-request data.

### Bài 5 — Thread pool

Advice `ThreadLocal.set(user)` rồi target throw; cleanup chỉ ở nhánh success. Request sau có thể thấy user cũ không?

**Đáp án:** Có nếu worker thread được tái sử dụng. Dùng finally để restore/remove.

### Bài 6 — Lock rộng

Aspect dùng `synchronized` bao trọn một method I/O chậm. Tại sao có thể giảm throughput?

**Đáp án:** Invocations cùng lock bị serialize và đợi I/O; cần rút ngắn critical section hoặc đổi ownership, trừ khi serialization là hợp đồng chủ đích.

### Bài 7 — Barrier

Hai worker threads được tạo nhưng không có synchronization nào, test có chắc maxInFlight=2 không?

**Đáp án:** Không. Scheduler có thể chạy xong A rồi mới chạy B. Dùng latch/barrier phù hợp như chương trình demo.

### Bài 8 — Error cleanup

Around advice tăng inFlight trước proceed, target ném exception. Nếu decrement chỉ ở đường return thì sao?

**Đáp án:** inFlight bị giữ cao sai; đặt cleanup trong finally sau khi tăng.

### Bài 9 — Async

Method trả future pending. Advice decrement inFlight trong finally ngay sau return. Điều đó đúng không?

**Đáp án:** Đúng **nếu** metric định nghĩa inFlight của synchronous invocation; sai nếu metric định nghĩa số logical tasks còn pending. Phải ghi rõ scope.

### Bài 10 — Proxy bypass

Một target gọi `this.inner()`, aspect interceptor chỉ trên proxy inner. Có tăng counter inner không?

**Đáp án:** Không trong delegating proxy path ấy. Phải test coverage hoặc đổi boundary/weaving, không sửa bằng atomic counter.

### Bài 11 — LongAdder dùng làm ID

Aspect gọi `adder.increment(); id=adder.sum()` để cấp unique ID dưới concurrent traffic. Có đúng không?

**Đáp án:** Không bảo đảm uniqueness/linearizable sequence. Dùng atomic increment-and-get hoặc ID allocator phù hợp.

### Bài 12 — Cache map

Hai threads cùng `containsKey` false rồi cùng load network, `put`. ConcurrentHashMap có bảo đảm chỉ một load không?

**Đáp án:** Không. Cần single-flight/compute policy đúng semantics, xử lý lỗi và bounded cache.

### Bài 13 — Shared data leak

Một aspect ghi `lastArgs` vào field để debug, chạy 1 triệu requests. Rủi ro?

**Đáp án:** Ghi đè giữa concurrent invocations, giữ reference tới dữ liệu nhạy cảm/large object và tạo race. Giữ snapshot tối thiểu hoặc dùng logging có sanitization và bounded buffering.

### Bài 14 — Log order

Log của request B xuất hiện giữa hai dòng của request A. Có chứng minh advice precedence sai không?

**Đáp án:** Không. Các invocations song song có thể xen kẽ. Phải dùng correlation ID và kiểm thứ tự **trong từng invocation**.

### Bài 15 — Lock monitor

Một caller dùng `synchronized(proxy)`, caller khác dùng `synchronized(target)` với JDK delegating proxy. Có dùng cùng lock không?

**Đáp án:** Không nếu là hai objects khác nhau. Synchronization boundary phải rõ.

## 21. Checklist review một aspect production

1. **Ownership:** Aspect instance dùng chung ở scope nào: module, target, proxy, thread, invocation?
2. **State classification:** Field nào immutable, shared mutable, per-target, per-call hoặc per-task?
3. **Happens-before:** Dữ liệu publish giữa threads được bảo đảm visibility thế nào?
4. **Atomicity:** Counters/CAS updates có đúng yêu cầu không?
5. **Multi-field invariants:** Có cần lock/state machine/consistent snapshot không?
6. **Metric semantics:** Đếm logical calls, attempts, target executions hay async tasks?
7. **Cleanup:** Có decrement/restore/close trên normal return, exception và cancellation?
8. **ThreadLocal:** Có restore previous value, remove ở outer scope và không leak thread pools?
9. **Async:** Return-handle khác task completion; context propagation và stage ownership thế nào?
10. **Reentrancy:** Advice có thể tự chọn lại chính nó qua logging/exporter/callback không?
11. **Locks:** Lock ordering, contention, deadlock, không giữ lock qua blocking proceed?
12. **Cache:** Key đầy đủ/đúng security scope, bounded eviction, thundering herd policy?
13. **Memory:** Aspect có giữ target/args/Throwable/future references quá lâu không?
14. **Proxy coverage:** Caller có bypass proxy bằng direct target hoặc self-invocation không?
15. **Weaving coverage:** Classes/join points thực sự được weave và có nhiều transformer xung đột không?
16. **Error transparency:** Original exception/result có được bảo toàn theo contract?
17. **Return types:** Primitive/null/Optional/async stage không bị thay ngoài ý định?
18. **Target thread safety:** Target có an toàn độc lập với aspect không?
19. **Load tests:** Có kiểm overlap bằng latch/barrier và benchmark p99, contention, memory?
20. **Bounded tests:** Barriers/Futures có timeout trong CI để tránh treo?
21. **Lifecycle:** Close/shutdown khi còn active calls, metrics flush và registration cleanup?
22. **Privacy:** Log/context có bị trộn user hoặc lộ dữ liệu khi chạy song song?
23. **Integration:** Kiểm thử với đúng engine/runtime/config/classloader, không suy từ JDK proxy demo?
24. **Rollback:** Có thể tắt/rollback aspect an toàn nếu phát hiện regression đồng thời?

## Kết luận

Thiết kế aspect an toàn khi chạy song song bắt đầu bằng việc **đặt state đúng scope**. Giữ dữ liệu của từng invocation trong local/context riêng, chia sẻ cấu hình bất biến qua safe publication, dùng atomic/concurrent primitives cho aggregate state và dùng synchronization đúng mức cho bất biến nhiều bước. Đặc biệt, **per-target không có nghĩa per-thread**, `volatile` không làm compound operations atomic, `LongAdder.sum()` không phải atomic snapshot lúc updates đang diễn ra, và `ThreadLocal` không tương đương request context trong executor.

Với around advice, cần bảo toàn **result/exception** và cleanup trong `finally`; với async, phải tách method return khỏi task completion; với proxy, phải xác minh call path để metrics/enforcement không bị bypass. Một aspect đáng tin là aspect vừa **thread-safe**, vừa giữ đúng **hợp đồng nghiệp vụ, performance, lifecycle, security và observability** khi nhiều invocations xảy ra đồng thời, được chứng minh bằng test có overlap thật chứ không dựa trên vận may scheduling.


</details>

- [Quay lại đầu trang](#back-to-top)

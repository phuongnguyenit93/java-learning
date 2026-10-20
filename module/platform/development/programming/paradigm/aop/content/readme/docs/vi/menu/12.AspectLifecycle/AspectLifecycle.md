<a id="back-to-top"></a>

# Vòng đời aspect

## Menu
- [Aspect dùng chung và aspect được tạo theo ngữ cảnh](#aspect-lifecycle-001)
- [Vòng đời gắn với từng đối tượng khi công cụ hỗ trợ](#aspect-lifecycle-002)
- [Vòng đời gắn với luồng điều khiển khi công cụ hỗ trợ](#aspect-lifecycle-003)
- [Thời điểm tạo, khởi tạo và giải phóng aspect](#aspect-lifecycle-004)
- [Chọn aspect không trạng thái hoặc có trạng thái](#aspect-lifecycle-005)
- [Quan hệ giữa vòng đời aspect và đối tượng được can thiệp](#aspect-lifecycle-006)
- [Lưu trạng thái theo lời gọi thay vì trạng thái dùng chung](#aspect-lifecycle-007)
- [Nguy cơ rò rỉ bộ nhớ và giữ đối tượng ngoài ý muốn](#aspect-lifecycle-008)

## <a id="aspect-lifecycle-001">Aspect dùng chung và aspect được tạo theo ngữ cảnh</a>

<details>
<summary>Xem chi tiết</summary>

# Aspect dùng chung và aspect được tạo theo ngữ cảnh

## 1. Vì sao vòng đời của aspect quan trọng?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, một *aspect* đóng gói các **pointcut** và **advice** cho một mối quan tâm cắt ngang: ghi log, audit, đo thời gian, tracing, kiểm soát truy cập hoặc quan sát lỗi. Khi một join point được chọn và advice thực thi, ta thường quan tâm advice *làm gì*. Nhưng còn một câu hỏi quyết định thiết kế:

**Đoạn advice này đang chạy trên instance aspect nào? Instance ấy tồn tại trong bao lâu, và những lượt thực thi nào chia sẻ trạng thái của nó?**

Một aspect có thể được dùng chung trong toàn bộ một phạm vi AOP, hoặc có nhiều instance được gắn với **đối tượng, luồng điều khiển, phiên xử lý hay từng lượt can thiệp**. Sự lựa chọn ảnh hưởng trực tiếp tới:

- **Trạng thái:** counter, cache, correlation ID, dữ liệu tạm được lưu ở đâu?
- **Concurrency:** bao nhiêu thread hoặc task có thể truy cập một instance cùng lúc?
- **Isolation:** thông tin của một đối tượng/yêu cầu có lọt sang đối tượng/yêu cầu khác không?
- **Lifecycle:** instance được tạo lúc nào, tái sử dụng bao lâu và được giải phóng ra sao?
- **Hiệu năng:** chi phí tạo instance, tra cứu association, đồng bộ hóa, GC và retention.
- **Semantics:** hai join point giống nhau có sử dụng cùng trạng thái advice hay không?

**Nguyên tắc cốt lõi:** *vòng đời của aspect instance không nhất thiết trùng với vòng đời target, proxy, thread, request hoặc một lần gọi phương thức*. Muốn biết đúng, phải xác định **mô hình instantiation** mà công cụ AOP và kiến trúc ứng dụng thực sự hỗ trợ.

> **Phạm vi:** Bài này giải thích AOP cơ bản và nâng cao nói chung. Những tên thường gặp như *singleton aspect*, *per-object*, *per-target*, *per-control-flow*, *per-invocation* được dùng làm mô hình phân tích. Một số công cụ cung cấp các chính sách instantiation có tên/cú pháp riêng; những công cụ khác chỉ có aspect dùng chung hoặc yêu cầu tự xây scope. Không có một cú pháp pointcut hoặc một cách tạo aspect bắt buộc cho mọi AOP engine.

## 2. Phân biệt aspect type, aspect instance và advice invocation

Ba khái niệm rất dễ bị nhầm:

1. **Aspect type:** định nghĩa chứa advice, pointcut và các field/method hỗ trợ. Nó giống một blueprint về hành vi.
2. **Aspect instance:** một object cụ thể chứa **trạng thái instance** của aspect type. Có thể có một hoặc nhiều object như vậy.
3. **Advice invocation:** một lần chạy advice tại join point đã khớp. **Nhiều lần chạy** có thể dùng **cùng một aspect instance**, hoặc mỗi lần dùng một instance khác theo mô hình được chọn.

```text
Aspect type: AuditAspect
       |
       +------ instance #A ------> advice chạy lần 1
       |                       \--> advice chạy lần 2
       |
       +------ instance #B ------> advice chạy lần 3
```

Vì vậy, việc log xuất hiện ba lần **không chứng minh** đã tạo ba aspect instance. Ngược lại, một aspect instance có thể tồn tại từ lâu mà chưa chạy advice nào vì chưa có join point khớp.

### Field nằm ở đâu?

```java
// Minh họa ý tưởng; không phải chương trình Java độc lập.
final class CounterAspect {
    private int count;

    void before() {
        count++;
    }
}
```

`count` là **instance field**. Nếu dùng chung một `CounterAspect` thì tất cả lời gọi vào object đó cùng tác động lên một field. Nếu mỗi target có aspect riêng, mỗi object có field riêng. Nhưng **`static` field** vẫn là trạng thái cấp class trong phạm vi class identity/class loader tương ứng, không tự tách theo từng aspect instance.

Do đó, đổi từ singleton sang per-target **không cô lập một static mutable field** mà mọi instance cùng truy cập.

## 3. Bảng tổng quan các mô hình vòng đời

| Mô hình | Đơn vị chia sẻ state | Thời gian sống điển hình | Điều cần chú ý |
| --- | --- | --- | --- |
| **Aspect dùng chung** | Nhiều join point/target trong phạm vi một instance chung | Bằng phạm vi manager/weaver/runtime đã xác định | Concurrency, state leakage, phạm vi “chung” thực tế |
| **Per target object** | Mỗi target có aspect instance riêng | Theo association với target hoặc registry | Nhiều caller/thread vẫn có thể dùng một target |
| **Per object bound at join point** | Mỗi object được binding theo semantics của engine | Theo object và tập join point phù hợp | `this`/`target` có thể khác nhau |
| **Per control flow** | Mỗi flow động bắt đầu ở một join point gốc | Từ lúc vào flow đến lúc thoát flow | Reentrancy, recursion, async/thread boundary |
| **Per invocation/join point occurrence** | Mỗi lần can thiệp một aspect/state riêng | Khoảng một lần thực thi advice/chain | Chi phí cấp phát, không tích lũy state giữa lần gọi |
| **Per task/request/session** | Một scope nghiệp vụ do ứng dụng/runtime định nghĩa | Theo task/request/session | Phải có carrier, propagation và cleanup rõ |
| **Per type/class** | Một aspect instance cho type được chọn | Theo runtime class/loader và association | Type identity, inheritance, class loader/reload |

Đây là những **khả năng thiết kế**, không phải danh sách chắc chắn xuất hiện trong mọi công cụ. Một engine hỗ trợ per-object mà không hỗ trợ per-flow vẫn là AOP hợp lệ. Một proxy handler cũng có thể dùng một field tham chiếu tới aspect singleton hoặc một nhà cung cấp aspect theo target.

### Một lời gọi có thể liên quan nhiều state scope

Ví dụ request R gọi target T trong thread W, trong đó target được gọi qua proxy P. Hệ thống có thể đồng thời có:

- Một aspect singleton ghi metric tổng thể.
- Một aspect instance gắn với T.
- Một trace context gắn với R.
- Một temporary timing state tạo cho lượt gọi hiện tại.

**Không cần chọn một kiểu lifecycle duy nhất cho toàn bộ ứng dụng.** Quan trọng là xác định dữ liệu nào thuộc scope nào và tránh gộp state khác bản chất.

## 4. Aspect dùng chung: một instance cho nhiều điểm can thiệp

Một **shared aspect** dùng cùng một instance để xử lý nhiều join point trong một phạm vi được quản lý. Trường hợp đơn giản:

```text
                   Aspect: SharedAudit #1
                   /         |         \
                  v          v          v
Target A.save()    Target B.save()    Target C.delete()
```

Những lần advice chạy ở cả A, B, C có thể cùng gọi method trên instance `SharedAudit #1`. Loại vòng đời này phù hợp khi advice:

- **Không giữ dữ liệu request mutable** trong field instance.
- Dùng hàm gần như thuần: nhận metadata/argument/context rồi xuất kết quả.
- Có cache/metric **được thiết kế dùng chung** và được đồng bộ hóa đúng.
- Giữ dependency bất biến tới một logger, metrics sink hoặc policy provider thread-safe.
- Không cần nhớ “lượt gọi trước của chính target này” qua instance fields.

### “Dùng chung” không nhất thiết là một instance cho cả JVM

Một chương trình có thể có nhiều:

- Class loader định nghĩa cùng một aspect type ở các domain khác nhau.
- Weaver hoặc proxy provider độc lập.
- Module layer/plugin thế hệ khác nhau.
- Runtime scope riêng của một test/application/plugin.
- Instance do người viết tự tạo bằng `new` ngoài cơ chế cung cấp aspect.

Vì thế, “singleton aspect” cần gắn với **phạm vi singleton nào**. Không nên lấy “trên toàn bộ tiến trình chắc chỉ có một aspect object” làm tiên đề khi kiến trúc có nhiều loader/manager.

### Ví dụ đúng khi stateless

```java
// Mã minh họa: ví dụ một advice stateless.
final class LoggingAspect {
    void before(String operationName) {
        System.out.println("begin " + operationName);
    }
}
```

Nếu instance chỉ có logic và các dependency an toàn, nó có thể dùng chung mà ít rủi ro. Bản thân `operationName` là **biến local của mỗi lần gọi**, không nằm trong field chia sẻ.

## 5. Shared aspect có state: nguy cơ race và lẫn dữ liệu

Xét:

```java
// KHÔNG an toàn nếu một instance dùng đồng thời mà thiếu đồng bộ.
final class UnsafeAspect {
    private String currentRequestId;
    private int count;

    void before(String requestId) {
        currentRequestId = requestId;
        count++;
    }

    void after() {
        System.out.println(currentRequestId);
    }
}
```

Hai thread có thể xen kẽ:

```text
Thread A: before("REQ-A") -> currentRequestId = REQ-A
Thread B: before("REQ-B") -> currentRequestId = REQ-B
Thread A: after()         -> đọc REQ-B  [SAI]
```

Không nhất thiết gặp lỗi ngay; đây là **data race** và **logical cross-request contamination**. `count++` cũng là thao tác đọc–tăng–ghi, không phải phép tăng atomic cho shared mutable state.

**Một aspect dùng chung không phù hợp để giữ dữ liệu riêng của một lời gọi trong instance field không được quản lý scope.** Để giữ dữ liệu tạm thời, ưu tiên:

- Biến local trong một around advice bao trọn lượt gọi.
- Parameter của advice hoặc một immutable context được truyền rõ.
- State gắn với invocation nếu công cụ hỗ trợ.
- Thread/task context có carrier và cleanup rõ nếu thực sự cần truyền ngầm.
- Concurrency primitives phù hợp cho **state dùng chung thật**, ví dụ `AtomicLong` hoặc `LongAdder` cho một số loại đếm.

### Atomic counter không sửa được lỗi nhầm request

Đổi `count` thành `AtomicInteger` có thể giải quyết lost update của counter, nhưng **không** giải quyết việc `currentRequestId` bị ghi đè giữa các lời gọi. Thread safety cần xét **toàn bộ bất biến dữ liệu**, không chỉ một phép tăng.

### Scope đúng đôi khi đơn giản là local variable

Nếu concern đo thời gian:

```java
// Minh họa logic around; proceed() là API giả định của công cụ.
long startNanos = System.nanoTime();
try {
    return proceed();
} finally {
    recordDuration(System.nanoTime() - startNanos);
}
```

`startNanos` là biến local theo invocation, vì vậy ngay cả khi một instance aspect dùng chung, từng lần chạy riêng biệt có local state của nó. Đây thường là thiết kế đơn giản hơn so với tạo một aspect instance mới chỉ để lưu thời điểm bắt đầu.

## 6. Aspect theo target: một instance gắn với một object đích

Một mô hình khác tạo hoặc chọn một **aspect instance riêng cho mỗi target object** phù hợp.

```text
Target A ------> Aspect[A] ---- calls của A
Target B ------> Aspect[B] ---- calls của B
Target C ------> Aspect[C] ---- calls của C
```

Với mỗi target, aspect có thể giữ:

- Counter số lần target được gọi.
- Cache cục bộ cho **chính target ấy**.
- Metadata đã tính trước từ cấu hình/type của target.
- Trạng thái quan sát riêng cho target.
- Resource phụ thuộc vòng đời target, nếu cách cleanup được thiết kế đúng.

### State được cô lập theo đối tượng, không theo request

Nếu hai thread cùng gọi **Target A**, chúng **vẫn chia sẻ Aspect[A]**. Per-target **không làm state tự thread-safe**.

Nếu một request xử lý nhiều target A và B, nó **không tự có một aspect instance chung theo request**. Ngược lại, nếu một target được dùng lại qua nhiều request, aspect gắn với target cũng sống qua nhiều request. **Per-target ≠ per-request**.

### Association: dùng identity hay equals?

Hai target object có thể trả `true` trong `equals()` nhưng vẫn là hai instance khác nhau. Nếu mục tiêu là **mỗi object identity có aspect riêng**, một `HashMap<Target,Aspect>` dựa trên `equals/hashCode` có thể vô tình gộp hai instance.

Một mô hình gắn theo **object identity** cần sử dụng semantics identity, ví dụ `IdentityHashMap` trong một demo tuần tự; trong hệ thống đa luồng cần giải pháp identity-aware và thread-safe phù hợp.

**Không tự coi object “có cùng mã khách hàng” là cùng target instance.** Business identity và object identity là hai khái niệm khác nhau.

## 7. “Per this” và “per target”: không được đánh đồng

Một số mô hình AOP cho phép gắn aspect instance với **đối tượng được binding là `this`** hoặc **đối tượng được binding là `target`** tại những join point khớp.

Hai biến ngữ cảnh này phụ thuộc loại join point và engine:

- **`this`:** thường liên quan tới object mà join point đang xảy ra *trong ngữ cảnh của nó*.
- **`target`:** thường liên quan tới receiver/đối tượng đích của lời gọi hoặc execution.

Trong một số tình huống method call, **caller object** và **callee receiver** là hai object khác nhau; ở một số execution join point, chúng có thể là cùng đối tượng thực thi. Với static context, constructor, reflective invocation hoặc proxy, semantics còn phụ thuộc mô hình join point của engine.

Ví dụ khái niệm:

```text
OrderWorkflow.submit()
     |
     +---- call ----> PaymentService.charge()
          caller object: OrderWorkflow
          target object: PaymentService
```

Nếu cơ chế AOP diễn giải `this` theo caller context của **method call join point**, còn `target` theo receiver `PaymentService`, “per-this” và “per-target” có thể tạo **hai hệ association khác nhau**.

**Đừng suy đoán một câu cú pháp per-object luôn gắn vào target class.** Đọc đúng semantics binding mà engine hỗ trợ cho từng loại join point.

### Có phải cứ tạo object là tạo aspect tương ứng ngay?

Không nhất thiết. Một số mô hình tạo aspect **khi join point đầu tiên thỏa điều kiện**, không phải ngay lúc constructor target chạy. Một object chưa từng đi qua pointcut có thể **không có aspect instance được tạo**. Điều này liên quan trực tiếp tới memory footprint và semantics “lần đầu”.

## 8. Aspect theo luồng điều khiển: per-control-flow

**Dynamic control flow** là chuỗi thực thi đang diễn ra, ví dụ lời gọi `Checkout.placeOrder()` và những method được gọi đồng bộ bên trong nó. Một số engine cung cấp kiểu tạo aspect instance **cho từng lượt vào một luồng điều khiển được chọn**.

```text
Flow #1: checkout(orderA)
  AspectFlow #1
     -> validate()
     -> reserve()
     -> charge()

Flow #2: checkout(orderB)
  AspectFlow #2
     -> validate()
     -> reserve()
     -> charge()
```

AspectFlow #1 và #2 tách state theo **lượt vào flow**, không phải theo target hay theo tên thread tự thân. Ý nghĩa “bắt đầu” và “kết thúc” của flow do điều kiện pointcut và engine xác định.

### Flow nằm trong stack đồng bộ

Một luồng điều khiển động thường dựa trên stack/quan hệ gọi đang hoạt động. Nếu `checkout` gửi `Runnable` sang một executor, stack worker **khác** stack caller. Không thể mặc định aspect instance của flow caller **tự chuyển** sang worker.

Muốn giữ state theo tác vụ/request qua async, cần thiết kế **context propagation** hoặc API scope riêng. Điều đó khác việc engine tự hỗ trợ per-control-flow tại join point đồng bộ.

### Nested flow và recursion

Nếu một flow gốc gọi lại chính nó hoặc mở một flow con:

```text
checkout(A)            -> Aspect flow A
   |
   +-- checkout(B)     -> Aspect flow B ? hay vẫn A ?
   |
   +-- return
```

Câu trả lời phụ thuộc **chính xác quy tắc instantiation** của công cụ (ví dụ phân biệt điều kiện bao gồm hay loại trừ chính join point mở flow) và cách scope lồng nhau được quản lý. Không tự giả định mỗi lời gọi cùng tên đều tạo một aspect mới, cũng không giả định chỉ có một aspect cho toàn bộ recursion.

Thiết kế đúng cần mô tả:

1. Join point nào **khởi tạo** flow?
2. Các join point **bên trong** sử dụng instance nào?
3. Khi flow lồng nhau, stack aspect instance có tách lớp không?
4. Exit bình thường, throw hoặc cancel xử lý cleanup ra sao?
5. Có propagate qua thread/task hay không?

### `per-control-flow` không đồng nghĩa `ThreadLocal`

Một engine có thể sử dụng cấu trúc gắn theo thread để quản lý stack flow, nhưng **ngữ nghĩa** là *scope theo lượt vào flow*. `ThreadLocal` là một API lưu giá trị **theo thread**; thread pool có thể chạy nhiều flow nối tiếp và một thread có thể chứa nhiều scope lồng nhau. Chỉ `ThreadLocal.set(new Aspect())` mà không push/pop/restore theo flow không đủ để tạo mô hình per-control-flow đúng.

## 9. Mô hình mỗi lượt gọi: per-invocation

Một số hệ thống cho phép **state riêng cho từng advice invocation**; nếu muốn, ta có thể tạo một object xử lý mới cho mỗi lần interception.

```text
Service.process("A") -> Aspect #1 -> state #1
Service.process("B") -> Aspect #2 -> state #2
Service.process("C") -> Aspect #3 -> state #3
```

Lợi điểm:

- Dữ liệu mutable của mỗi lượt gọi không bị chia sẻ với lượt khác nếu không trỏ tới resource chung.
- Lifetime đơn giản trong một advice synchronous: tạo → dùng → bỏ reference.
- Thích hợp cho work state nhỏ, chain state hoặc điều kiện chỉ tồn tại trong một lần proceed.

Đánh đổi:

- Tốn cấp phát và khởi tạo nếu làm rất thường xuyên.
- Cache không tích lũy giữa lần gọi.
- Có thể tạo nhiều instance vô ích nếu stateless aspect vốn có thể dùng chung.
- Một object per-invocation **không tự cô lập shared dependencies** hay target object mutable.

**Không cần luôn tạo aspect mới để có local state mới.** Một shared around advice với biến local thường đạt cùng sự cô lập invocation mà không phát sinh toàn bộ một instance aspect mỗi lần.

## 10. Aspect theo request, session hoặc tác vụ: context-managed scope

Trong ứng dụng nhiều tầng, người viết đôi khi muốn “một aspect instance cho mỗi request”. Đây là **một mô hình scope nghiệp vụ**, nhưng không nhất thiết trùng với một kiểu instantiation được AOP engine cung cấp.

```text
Request R1
   -> Task A (thread #1)
   -> Task B (thread #3)
   -> callback (thread #2)

Request R2
   -> Task C (thread #1)
```

Một request có thể chuyển qua nhiều thread; một thread có thể xử lý nhiều request nối tiếp. Do đó:

**Per request ≠ per thread; per task ≠ per thread; per flow theo stack ≠ per request bất đồng bộ.**

Nếu muốn aspect state theo request, cần:

- Xác định **request identity** và lifecycle bắt đầu/kết thúc.
- Tạo/đăng ký aspect state vào một **context carrier** hoặc scope manager.
- Truyền context rõ qua executor/callback/message nếu cần.
- Khôi phục previous context ở worker và cleanup bằng `finally`.
- Xử lý cancellation, exception, timeout và retry.
- Không cho thread pool giữ state request cũ sang task mới.

### Một warning về bảo mật

Không đưa toàn bộ session hay credential vào aspect field dùng chung để “tiện access”. Một aspect đọc identity từ context đã xác thực tại boundary cần thiết **không có nghĩa** context ấy tự bảo đảm authorization. Phải tránh rò metadata giữa yêu cầu và thực thi kiểm soát quyền tại ranh giới đáng tin.

## 11. Ví dụ Java 17+ chạy được: shared, per-target và per-invocation

Ví dụ này dùng **Java Dynamic Proxy** để mô phỏng một cơ chế AOP nhỏ, chỉ sử dụng Java SE. Mỗi proxy nhận một **`Supplier<AspectState>`** để quyết định aspect instance nào được dùng **tại thời điểm interception**.

Chúng ta lần lượt triển khai:

1. **Shared:** hai proxy cho hai target dùng **cùng một `AspectState`**.
2. **Per-target:** mỗi target có **một state riêng**, được tra cứu theo object identity.
3. **Per-invocation:** mỗi lần handler được gọi sẽ tạo **một state mới**.
4. **Raw target và self-invocation:** minh họa nơi proxy advice **không có cơ hội chạy**.

Không có DSL pointcut đầy đủ trong demo; handler mô phỏng before advice cho những method đi qua proxy. Demo **không khẳng định mọi công cụ AOP tự hỗ trợ đúng ba chính sách này**. Đây là cách tách rõ **lifecycle của aspect state** khỏi **lifecycle target/proxy**.

### 11.1. File `AspectScopeDemo.java`

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class AspectScopeDemo {

    interface Service {
        String process(String value);
        String outer(String value);
    }

    static final class Target implements Service {
        private final String id;

        Target(String id) {
            this.id = id;
        }

        @Override
        public String process(String value) {
            System.out.println("target[" + id + "].process " + value);
            return "ok:" + id + ":" + value;
        }

        @Override
        public String outer(String value) {
            System.out.println("target[" + id + "].outer " + value);
            // this trỏ tới Target: lời gọi nội bộ bỏ qua wrapper proxy.
            return this.process(value + "!");
        }
    }

    static final class AspectState {
        private final String name;
        private int count;

        AspectState(String name) {
            this.name = name;
        }

        void before(String event) {
            count++;
            System.out.println(
                    "advice[" + name + "] #" + count + " " + event);
        }
    }

    static Service wrap(
            Service target,
            Supplier<AspectState> aspectProvider) {
        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] { Service.class },
                (proxy, method, arguments) -> {
                    AspectState aspect = aspectProvider.get();
                    aspect.before(
                            method.getName() + ":" + arguments[0]);
                    return callTarget(method, target, arguments);
                }
        );
    }

    static Object callTarget(
            Method method, Service target, Object[] arguments)
            throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    public static void main(String[] args) {

        Target a = new Target("A");
        Target b = new Target("B");

        System.out.println("== shared ==");
        AspectState shared = new AspectState("shared");
        Service sharedA = wrap(a, () -> shared);
        Service sharedB = wrap(b, () -> shared);

        System.out.println("caller " + sharedA.process("a1"));
        System.out.println("caller " + sharedB.process("b1"));
        System.out.println("caller " + sharedA.outer("a2"));

        System.out.println("== per-target ==");
        // Demo chạy tuần tự, nên IdentityHashMap đủ để minh họa
        // phân biệt hai instance target. Không dùng như map đa luồng.
        Map<Service, AspectState> perTarget = new IdentityHashMap<>();

        Service scopedA = wrap(a, () -> perTarget.computeIfAbsent(
                a, ignored -> new AspectState("target-A")));
        Service scopedB = wrap(b, () -> perTarget.computeIfAbsent(
                b, ignored -> new AspectState("target-B")));

        System.out.println("caller " + scopedA.process("a3"));
        System.out.println("caller " + scopedA.process("a4"));
        System.out.println("caller " + scopedB.process("b2"));

        System.out.println("== per-invocation ==");
        Service eachCall = wrap(
                a, () -> new AspectState("invocation"));

        System.out.println("caller " + eachCall.process("a5"));
        System.out.println("caller " + eachCall.process("a6"));

        System.out.println("== raw ==");
        System.out.println("caller " + a.process("a7"));
    }
}
```

Chạy với **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 AspectScopeDemo.java
java AspectScopeDemo
```

**Standard output dự kiến:**

```text
== shared ==
advice[shared] #1 process:a1
target[A].process a1
caller ok:A:a1
advice[shared] #2 process:b1
target[B].process b1
caller ok:B:b1
advice[shared] #3 outer:a2
target[A].outer a2
target[A].process a2!
caller ok:A:a2!
== per-target ==
advice[target-A] #1 process:a3
target[A].process a3
caller ok:A:a3
advice[target-A] #2 process:a4
target[A].process a4
caller ok:A:a4
advice[target-B] #1 process:b2
target[B].process b2
caller ok:B:b2
== per-invocation ==
advice[invocation] #1 process:a5
target[A].process a5
caller ok:A:a5
advice[invocation] #1 process:a6
target[A].process a6
caller ok:A:a6
== raw ==
target[A].process a7
caller ok:A:a7
```

### 11.2. Đọc nhánh shared

`sharedA` và `sharedB` wrap **hai target khác nhau** nhưng `aspectProvider.get()` luôn trả **cùng `shared`**. Vì vậy count lần lượt là **#1, #2, #3** — không reset khi đổi target.

Tuy nhiên, ở `sharedA.outer("a2")`:

- Handler thấy `outer` và tăng shared count tới #3.
- Target A thực thi `outer`.
- Target gọi `this.process("a2!")` nội bộ.
- Lời gọi nội bộ **không qua wrapper proxy** nên **không thêm** dòng `advice[shared] ... process:a2!`.

Vì thế **vòng đời aspect** và **proxy coverage** là hai trục độc lập. Dùng chung một aspect không làm proxy quan sát được mọi method execution bên trong target.

### 11.3. Đọc nhánh per-target

`perTarget` là `IdentityHashMap` để mô phỏng association theo **object identity**. Lần đầu lời gọi qua `scopedA`, `computeIfAbsent` tạo `AspectState("target-A")`. Lần kế tiếp trên target A dùng lại instance đó, count thành **#2**. Target B được tạo instance riêng nên count bắt đầu **#1**.

Demo dùng map này **ở một luồng tuần tự**. `IdentityHashMap` **không thread-safe** và không phải gợi ý triển khai trực tiếp cho một AOP runtime đa luồng. Trong production, cần một scope manager có lifecycle, synchronization và chiến lược tránh memory retention thích hợp.

### 11.4. Đọc nhánh per-invocation

Supplier tạo `new AspectState("invocation")` **mỗi khi handler chạy**. Vì mỗi state vừa được tạo có count ban đầu bằng 0, hai lời gọi đều in **#1**. Không có trạng thái tích lũy giữa hai lần `process`.

### 11.5. Đọc nhánh raw

`a.process("a7")` đi trực tiếp vào target, không qua bất kỳ proxy nào. Vì vậy dù target A từng có proxy shared, per-target và per-invocation, **lời gọi raw không dùng một aspect state nào trong ba proxy**.

Wrap một target **không thay đổi chính target** thành proxy và không thu hồi các raw references đã tồn tại.

### 11.6. Mô hình per-target được minh họa, không phải tự động AOP

`IdentityHashMap` trong ví dụ là một **scope registry do người viết tự tạo**, không phải một cơ chế instantiation chuẩn bắt buộc. Một engine AOP có hỗ trợ per-object có thể quản lý association, GC và cleanup theo cách khác, hoặc chỉ tạo instance khi một pointcut khớp lần đầu. Ví dụ chỉ minh họa **hệ quả của việc cùng target dùng cùng aspect state**.

## 12. Kiểm thử vòng đời: số lượt advice không phải số aspect instance

Có thể thêm log vào constructor `AspectState` để đếm số object được tạo. Nhưng nên tách hai metric:

1. **Aspect instance created:** một object aspect được tạo.
2. **Advice invocation count:** bao nhiêu lượt advice đã chạy.

Với chương trình trên:

| Scope | Số instance phục vụ lượt gọi minh họa | Số lượt advice |
| --- | ---: | ---: |
| Shared | 1 | 3 |
| Per-target | 2 | 3 |
| Per-invocation | 2 | 2 |
| Raw call | 0 instance aspect mới được sử dụng | 0 |

Bảng chỉ tính những hành vi ở đúng phần demo tương ứng. Bản thân object `AspectState` có thể được khởi tạo trước, lúc lookup hoặc lúc interception tùy cách provider được viết. **Không đồng nhất “có log 3 lần” với “tạo 3 aspect”.**

### State lifecycle và target lifecycle khác nhau

Target A được tạo **trước** cả proxy per-target và per-invocation. Aspect per-target của A chỉ được tạo **khi Supplier được gọi lần đầu**. Một engine hỗ trợ per-target native có thể có semantics tạo ở điểm khác; phải đọc contract công cụ chứ không suy từ ví dụ.

## 13. Aspect instance được tạo lúc nào?

Các khả năng thường gặp:

- **Eager:** aspect instance được tạo lúc hệ thống AOP khởi tạo.
- **Lazy first-match:** instance được tạo khi join point đầu tiên phù hợp xảy ra.
- **On object association:** instance được gắn vào target khi object được đăng ký/khởi tạo hoặc ở lần match đầu tiên.
- **On flow entry:** tạo khi một luồng điều khiển động bắt đầu.
- **On invocation:** tạo mỗi lượt advice.
- **On explicit scope begin:** ứng dụng mở request/task scope và tạo state.

Điểm cần phân biệt là:

**Thời điểm object đích được tạo ≠ thời điểm pointcut match ≠ thời điểm aspect được tạo ≠ thời điểm advice chạy.**

Nếu khởi tạo aspect có side effect — mở connection, đăng ký listener, đọc cấu hình — lựa chọn eager/lazy có ảnh hưởng. Một aspect được tạo khi first match nhưng target không bao giờ được gọi sẽ không có side effect khởi tạo đó. Ngược lại, việc khởi tạo eager có thể xảy ra dù pointcut chưa bao giờ khớp.

### Tránh side effect nặng trong constructor aspect

Một aspect dùng chung có thể tồn tại dài hạn, nhưng không nên dựa vào constructor để tự động mở resource mà không có cơ chế đóng. Một aspect per-invocation có thể tạo rất nhiều instance, nên constructor đắt đỏ gây overhead lớn.

Tốt hơn là thiết kế các dependency/resource với ownership và lifecycle riêng: **ai cấp, ai dùng, ai đóng**, thay vì suy luận tất cả resource sống cùng aspect.

## 14. Khi nào aspect instance bị hủy?

Java dùng garbage collection dựa trên khả năng còn được tham chiếu. **Không có callback hủy aspect instance chuẩn bắt buộc cho mọi mô hình AOP**. Một aspect gắn với target có thể bị giữ lâu nếu registry giữ reference mạnh tới target hoặc aspect.

Các trường hợp:

- Shared aspect thường sống tới khi scope manager/provider không còn giữ nó.
- Per-target aspect có thể sống cùng target nếu association cho phép GC cùng nhau.
- Per-invocation aspect có thể được GC sau khi lời gọi và mọi reference liên quan kết thúc.
- Per-control-flow aspect có thể trở nên không còn được tham chiếu khi flow kết thúc và scope stack được dọn.
- Per-request aspect có thể bị giữ lại nếu context lưu trong thread pool hoặc callback chưa được cleanup.

**Kết thúc logic của scope không đồng nghĩa object đã được GC ngay.** GC không phải một cơ chế đảm bảo cleanup tức thì của file handle, transaction hoặc connection.

### Weak references và memory leak

Một per-target registry dùng `HashMap<Target,Aspect>` mạnh có thể giữ cả target và aspect sống vô thời hạn khi ứng dụng không còn dùng target. `WeakHashMap` có thể hữu ích trong một số trường hợp, nhưng:

- Nó sử dụng semantics `equals/hashCode` chứ không tự trở thành weak identity map.
- Nếu **value aspect tham chiếu mạnh ngược về target**, key yếu có thể vẫn không được thu gom như mong muốn do đường tham chiếu qua map value.
- Concurrency và tính nhất quán của scope vẫn cần thiết kế riêng.
- Một engine AOP có thể có cách quản lý association/GC không dựa trên map thông thường.

Không chọn `WeakHashMap` chỉ vì “weak sẽ hết leak”. Phải kiểm tra **reference graph** và lifetime toàn hệ thống.

## 15. Reentrancy: cùng aspect được gọi lồng nhau

**Reentrancy** xảy ra khi một lời gọi advice/target khiến cùng aspect tham gia một join point khác **trước khi lượt trước kết thúc**. Đây không nhất thiết là concurrency đa luồng.

```text
Thread A:
  Aspect.before(method A)
      |
      +--> call method B
              |
          Aspect.before(method B)
      |
  Aspect.after(method A)
```

Với shared aspect, mọi lời gọi lồng nhau có thể cùng truy cập một instance. Nếu advice dùng field `currentOperation` để giữ trạng thái tạm, lời gọi B ghi đè nó ngay trong **cùng thread** — lỗi vẫn xảy ra dù không có thread race.

### Một giải pháp: state theo invocation stack

- Biến local của advice nếu code bao trọn invocation.
- Stack state theo flow với push/pop và `finally`.
- Context object gắn rõ vào join point chain nếu engine hỗ trợ.
- Tránh một field toàn cục “current advice call” trên singleton.

**Thread-safety không đồng nghĩa reentrancy-safety.** Một `synchronized` method có thể giữ lock trong lời gọi đệ quy cùng thread, nhưng state “current operation” vẫn có thể bị ghi đè về mặt logic. Khóa không thay thế mô hình scope đúng.

## 16. Concurrency: per-target vẫn có thể bị truy cập song song

Một target singleton được sử dụng bởi nhiều thread:

```text
Thread 1 --> Target X --> Aspect[X]
Thread 2 --> Target X --> Aspect[X]
Thread 3 --> Target X --> Aspect[X]
```

Dù Aspect[X] **không chia sẻ với Target Y**, nó **vẫn được chia sẻ giữa ba thread** nếu cùng xử lý X. Nếu giữ một mutable counter không atomic, vẫn có lost updates.

Ngược lại, per-invocation state tự tạo cho mỗi lượt handler có thể cô lập các field của nó, nhưng target X vẫn có thể có race. **Vòng đời aspect không làm target thread-safe.**

### Chọn concurrency primitive theo ý nghĩa dữ liệu

| State cần quản lý | Phương án phù hợp thường gặp | Giới hạn |
| --- | --- | --- |
| Metric tổng số lượt gọi | `LongAdder` hoặc atomic counter | Không giải quyết tương quan giữa nhiều field |
| Cấu hình dùng chung | Immutable snapshot; publish an toàn | Reload phải có semantics nhất quán |
| Cache chung | Concurrent map/cache chuyên dụng | Loader, eviction, TTL và initialization có thể race |
| Dữ liệu mỗi invocation | Local variable / invocation context | Không tự đi qua async boundary |
| Dữ liệu mỗi target | Association theo object + synchronization phù hợp | Nhiều thread vẫn có thể cùng target |
| Dữ liệu mỗi request | Explicit context + propagation | Cần cleanup, quyền truy cập và lifetime rõ |

**Không dùng `volatile int count` để mong `count++` atomic**: volatile bảo đảm một số thuộc tính visibility/order, nhưng phép tăng vẫn là read–modify–write có thể tranh chấp.

## 17. Một shared aspect có thể chứa cache không?

Có, nếu cache **thực sự thuộc scope dùng chung**, key đủ phân biệt ngữ cảnh và việc truy cập an toàn.

Ví dụ, metadata cache theo method signature/type thường dùng lại được qua nhiều invocation. Nhưng **kết quả nghiệp vụ phụ thuộc request** không nên được cache chỉ theo tên method.

### Cache key là một phần của scope

Nếu per-target aspect cache theo field `lastResult`, hai lượt gọi trên cùng target nhưng argument khác có thể trả dữ liệu sai. Nếu shared aspect cache theo `methodName`, nhiều target có method cùng tên bị gộp.

Một cache đúng cần xem:

- **Object identity hay business identity?**
- Method overload và signature.
- Argument/value ảnh hưởng tới kết quả.
- Request/tenant/permission context nếu có.
- Version config, type loader/module, class generation.
- TTL, invalidation, memory bounds và concurrency.

**Lifecycle aspect là một chiều của cache lifetime, không thay thế cache correctness.**

## 18. State tĩnh và dependency chia sẻ làm scope bị “thủng”

Giả sử mỗi target có aspect instance riêng:

```java
// Mã minh họa:
final class PerTargetAspect {
    private static final java.util.List<String> GLOBAL_EVENTS =
            new java.util.ArrayList<>();

    void before(String name) {
        GLOBAL_EVENTS.add(name);
    }
}
```

Các instance khác nhau vẫn dùng **cùng `GLOBAL_EVENTS`** trong cùng một class identity. `ArrayList` lại không thread-safe cho nhiều thao tác đồng thời. Vì vậy “per-target aspect” không ngăn cross-target data mixing nếu state được giữ trong field static hoặc dependency singleton mutable.

Một aspect mới cũng có thể chia sẻ cùng `Logger`, `AuditSink`, `CacheService` hay `PolicyProvider`. Điều này có thể là chủ đích tốt, nhưng **cần phân biệt identity của aspect và identity của dependency**.

### Quy tắc thiết kế

- State **đặc trưng cho aspect instance** → instance field trong scope tương ứng.
- State **đặc trưng cho mỗi lượt gọi** → local/invocation context.
- State **đặc trưng cho target** → per-target scope hoặc key theo target.
- State **toàn cục/thống kê** → shared component được đồng bộ đúng.
- Secret/identity request → context rõ, không cất vào shared mutable field.

## 19. Gắn aspect với proxy hay với target?

Một proxy và target có thể là hai Java object khác nhau. Nếu scope manager dùng **proxy identity** làm key, tạo hai proxy cho cùng một target có thể sinh hai aspect instance. Nếu dùng **target identity** làm key, cả hai proxy có thể chia sẻ một aspect.

```text
           Proxy P1 ----+
                        |
                        v
                      Target T
                        ^
                        |
           Proxy P2 ----+
```

**Per-proxy:** `Aspect[P1]` khác `Aspect[P2]`.

**Per-target:** cả hai đường dùng `Aspect[T]` theo cùng identity target.

**Shared:** tất cả dùng một instance chung, kể cả target khác.

Ba cách này đều có thể có trong một thiết kế proxy custom. Không nên dùng từ “per-target” nhưng lại key bằng proxy trừ khi contract định nghĩa proxy chính là target được binding trong join point đó.

### Nested proxies

Nếu proxy B wrap proxy A, B có thể có aspect riêng của nó; A có aspect khác. Một lời gọi qua B có thể kích hoạt cả hai. Vòng đời hai aspect có thể khác, ví dụ tracing shared ở ngoài và audit per-target ở trong. Cần xác định precedence để biết điều gì chạy trước/sau và có bypass nào không.

## 20. Vòng đời aspect và loại advice

**Before/after/around** mô tả **thời điểm và quyền can thiệp**, không tự quyết định **aspect instantiation model**.

Một before advice có thể chạy trên shared aspect, per-target aspect hoặc một aspect per-flow. Một around advice có thể là singleton nhưng dùng local state cho mỗi invocation. Vì vậy:

- **“Around advice” ≠ “aspect mới mỗi lần gọi”.**
- **“After advice” ≠ “aspect bị hủy sau khi method kết thúc”.**
- **“Before advice được chạy lần đầu” ≠ “aspect chắc chắn vừa được tạo”.**
- **“Aspect singleton” ≠ “mọi target đều dùng một proxy duy nhất”.**

Một cách phân tích đầy đủ đặt các lựa chọn trên **hai trục**:

| Trục | Câu hỏi |
| --- | --- |
| **Advice kind** | Before, after-return, after-throw, finally, around? |
| **Aspect lifecycle** | Shared, per-object, per-flow, per-invocation hoặc scope khác? |

Ngoài ra còn trục thứ ba: **weaving/interception coverage**. Aspect được tạo đúng scope nhưng join point không được can thiệp thì advice vẫn không chạy.

## 21. Pointcut động và thời điểm chọn instance

Một pointcut có thể phụ thuộc:

- Method/type metadata tĩnh.
- Receiver/target object.
- Runtime argument values.
- Control-flow context.
- Annotation hoặc cấu hình.
- Trạng thái do engine/hook hỗ trợ.

Nếu aspect được tạo **trước** khi dynamic condition được đánh giá, số instance có thể khác so với một engine chỉ tạo instance **sau khi match**. Nếu pointcut condition thay đổi theo thời gian, association có thể tồn tại dù lượt gọi sau không còn thỏa điều kiện.

Ví dụ: “tạo aspect cho target T sau lần đầu target đi qua pointcut P”. Sau đó:

- Một số lần gọi T không khớp P nên không dùng advice.
- Association với T có thể vẫn còn.
- Những lần gọi T khớp P về sau có thể dùng lại aspect cũ.

**Không coi “có association rồi” là mọi lời gọi của target đều kích hoạt advice**. Matching và aspect instantiation là hai cơ chế cần phân tích riêng.

## 22. Per-control-flow: cleanup phải theo đúng stack

Một cách minh họa phạm vi flow:

```text
enter Flow A
  push Aspect[A]
  |
  +-- enter Flow B
  |      push Aspect[B]
  |      execute B
  |      pop Aspect[B] in finally
  |
  execute A
  pop Aspect[A] in finally
```

Khi exception thoát khỏi Flow B, cleanup vẫn phải pop đúng scope để Flow A lấy lại aspect instance của nó. Nếu chỉ đặt một biến thread-local đơn và quên restore, Flow A có thể đọc nhầm Aspect[B].

### Pseudocode quản lý scope

```java
// Minh họa logic; không phải chương trình độc lập.
AspectState previous = CURRENT.get();
AspectState current = new AspectState("flow");
try {
    CURRENT.set(current);
    proceed();
} finally {
    if (previous == null) {
        CURRENT.remove();
    } else {
        CURRENT.set(previous);
    }
}
```

Đây chỉ là hình thức lưu/khôi phục **một context slot** cho flow đồng bộ. Một per-flow engine thực tế có thể dùng stack riêng và khác nhau về join point nào tạo instance. Không tự kết luận đoạn này triển khai đầy đủ mọi semantics per-control-flow.

## 23. Async/task handoff: không thể đồng nhất flow với request

Giả sử:

```text
Thread A:
  request R starts
  Aspect[flow-A] created
  submit(task)
  request R returns

Thread B:
  run(task)
    -> target.method()
```

Trong mô hình dựa trên **đường gọi đồng bộ**, worker B không còn nằm trong stack A. Dù về nghiệp vụ task thuộc request R, nó không tự được dùng Aspect[flow-A]. Nếu cần một aspect/state **theo request xuyên thread**, phải thiết kế carrier có thể truyền context từ A sang B.

Cũng cần quyết định **task sống lâu hơn request** thì aspect state còn hợp lệ không. Nếu callback chạy sau khi request kết thúc, việc giữ reference tới toàn bộ request object/aspect có thể gây memory retention hoặc truy cập resource đã đóng.

### Propagation không đồng nghĩa authorization

Một request ID hoặc actor ID được truyền tới worker chỉ là dữ liệu. Các kiểm tra quyền thực tế phải dựa trên dữ liệu đã được xác minh ở boundary phù hợp. Không coi “worker nhận được cùng aspect instance” là một bằng chứng đủ rằng quyền bảo mật còn hiệu lực.

## 24. Aspect theo type/class và class loader identity

Một số engine hoặc thiết kế custom có thể tạo aspect instance **mỗi runtime type** được chọn. Điều này khác với:

- **Per-target:** mỗi object instance.
- **Shared singleton:** một object cho toàn bộ scope manager.
- **Per-type:** mỗi `Class<?>` hoặc type association.

Nếu class `Service` được định nghĩa bởi hai class loader khác nhau, JVM có thể coi đó là **hai runtime types khác nhau** dù tên binary giống nhau. Tương tự, các module layer/plugin có thể có vòng đời khác nhau.

Một per-type registry cần quan tâm:

- Class identity chứ không chỉ chuỗi tên class.
- Loader/module layer.
- Inheritance/override và class được chọn bởi pointcut.
- Cache invalidation khi class loader unload/reload.
- Reference mạnh từ registry tới `Class<?>` và loader gây memory leak.

**“Một aspect mỗi tên class”** không nhất thiết tương đương **“một aspect mỗi runtime type”** trong hệ thống modular/plugin.

## 25. Nếu aspect tự tạo thêm aspect thì sao?

Một shared aspect có thể gọi `new SomeAspect()` trực tiếp hoặc khởi tạo helper tạo instance phụ. Nhưng instance mới ấy **không mặc nhiên được AOP engine đăng ký** như aspect instance được dùng cho những join point đã weave.

Tương tự, target có thể được tạo ngoài proxy manager. **Object existence ≠ registration/association với join point runtime**.

Nếu công cụ AOP quản lý instance riêng, cần dùng contract chính thức để xác định:

- Ai tạo aspect?
- Ai chọn instance tại join point?
- Có được tự khởi tạo thủ công không?
- Tự tạo một object cùng class aspect có làm advice được gọi trên object ấy không?
- Instance đó được cleanup thế nào?

Không tự sửa lỗi “aspect sai vòng đời” bằng cách gọi `new Aspect()` rải rác trong business code, vì có thể tạo state không được dùng hoặc làm lệch semantics.

## 26. Memory and performance: shared không luôn tốt hơn

| Tiêu chí | Shared | Per-target | Per-invocation |
| --- | --- | --- | --- |
| Số instance aspect | Thường ít | Tỷ lệ với số target có association | Tỷ lệ với số invocation |
| Chi phí tạo | Thấp sau khởi tạo | Theo target/first match | Mỗi lần gọi |
| Khả năng cache theo target | Cần key | Thuận tiện | Thường kém |
| Concurrency state | Có thể nhiều thread cùng instance | Nhiều thread cùng target vẫn chia sẻ | State instance riêng mỗi invocation |
| Nguy cơ giữ state quá lâu | Cao với field request | Cao với registry giữ target | Thấp nếu không escape |
| Nguy cơ cấp phát/GC | Thường thấp | Theo số target | Cao hơn trên hot path |
| Cleanup | Theo scope chung | Theo association/lifetime target | Theo invocation/lifecycle phụ |
| Debug scope | Cần phân biệt target/request trong log | Dễ theo từng target | Dễ theo mỗi call |

Không nên tối ưu chỉ theo số object:

- Một shared aspect giữ một map lớn không giới hạn có thể tiêu thụ nhiều bộ nhớ hơn nhiều aspect nhỏ được giải phóng đúng.
- Per-invocation allocation có thể rẻ trong một số trường hợp nhưng tốn trên hot path.
- Per-target association cần bảo đảm không giữ references sau khi target ngừng sử dụng.
- Advice chạy ở mọi lượt method thường nhạy cảm với overhead nhỏ lặp lại.

**Đo thực tế** với tốc độ gọi, số target và lifetime của hệ thống, không chọn scope chỉ vì tên nghe “nhẹ”.

## 27. Chọn scope theo concern

### 27.1. Logging không giữ request state

Nếu chỉ cần ghi tên method và vài metadata, **shared/stateless aspect** thường hợp lý. Dữ liệu mỗi request truyền vào advice dưới dạng argument/context, không nằm trong field mutable.

### 27.2. Metric tổng lượt gọi

Một shared aspect với **concurrency-safe metric recorder** có thể phù hợp. Nhưng mỗi metric label cần tránh cardinality quá lớn. Nếu metric theo target instance, không nên dùng mỗi `System.identityHashCode` làm label công khai không giới hạn.

### 27.3. State giám sát riêng từng target

Một aspect per-target có thể giữ các giá trị liên quan target. Cần xem target dùng qua nhiều thread/request không, và có cơ chế cleanup association không.

### 27.4. Tracing và correlation

Một aspect shared có thể đọc trace context theo invocation/task; không cần lưu trace ID trong instance field shared. Nếu có per-flow model, nó chỉ phản ánh đúng **flow semantics** mà engine định nghĩa, không tự xuyên async.

### 27.5. Retry/fallback

Around advice có thể dùng state retry riêng cho **mỗi invocation** (số lần thử, deadline) ngay trong local variables. Không nên giữ `currentRetryAttempt` trong shared aspect field vì nhiều caller có thể đang retry cùng lúc.

### 27.6. Audit bắt buộc

Scope của aspect **không bảo đảm coverage**. Audit cần ranh giới có thể kiểm chứng rằng các entry point phù hợp đều được can thiệp; shared/per-target chỉ quyết định nơi state nằm sau khi advice chạy. Không coi “một aspect instance cho mỗi request” là biện pháp bảo mật thay cho kiểm soát quyền.

## 28. Thiết kế cấu hình và dependency cho lifecycle rõ ràng

Một hệ thống AOP có thể tách:

```text
Aspect definition
       |
       v
Aspect instance provider
       |
       +-- shared instance
       +-- instance by target identity
       +-- instance by invocation
       +-- instance by context key
       |
       v
Advice dispatcher
       |
       v
Join point / Target
```

**Instance provider** quyết định vòng đời. **Advice dispatcher** quyết định dùng instance nào khi join point khớp. **Pointcut matcher** quyết định join point có được chọn. **Target manager** quản lý object nghiệp vụ. Bốn thành phần có thể gộp trong một công cụ, nhưng tách chúng về mặt khái niệm giúp debug rõ ràng.

### Dependency bất biến hay mutable?

Một aspect instance có thể giữ các dependency như logger, policy snapshot hoặc metrics sink. Nếu dependency mutable được share nhiều scope, phải có contract thread safety. Ngược lại, nếu mỗi aspect instance mở một resource nặng, per-invocation có thể tạo quá nhiều resource. Tối ưu bằng cách **tách resource dùng chung khỏi state riêng**, không cưỡng ép cả aspect theo cùng lifecycle của resource.

## 29. Khi aspect dùng chung vẫn cần scope tại lời gọi

Với một shared around advice, có thể truyền invocation context tường minh qua chain:

```text
caller
  -> shared around advice
       create InvocationContext #1
       proceed(context #1)
       cleanup #1

caller khác
  -> shared around advice
       create InvocationContext #2
       proceed(context #2)
       cleanup #2
```

Mô hình này đạt cả hai:

- **Tái sử dụng code/dependency** của shared aspect.
- **Cô lập mutable state** của từng lượt gọi.

Một số công cụ có API join point/context object, số khác yêu cầu lập trình viên tự tạo immutable record hoặc local variables. Không nhất thiết phải chuyển toàn bộ aspect thành per-invocation để đạt isolation.

### Không phải mọi concern cần lưu state

Một advice xác thực input thuần có thể nhận argument rồi ném lỗi hoặc tiếp tục mà không cần field. Nếu mọi dữ liệu đều là input/output của từng lần gọi, **stateless shared aspect thường dễ reasoning, dễ test và ít lỗi concurrency**.

## 30. Vòng đời aspect và exception/finally

Một aspect instance per-flow hoặc per-invocation có thể sở hữu temporary scope. Khi target ném lỗi:

- Advice around/after-throwing có thể quan sát exception nếu cơ chế hỗ trợ.
- Cleanup phải chạy trong `finally` ở boundary phù hợp.
- Scope manager phải restore instance trước đó nếu nested.
- Exception từ cleanup cần có chính sách, tránh vô tình che lỗi target.
- Không giữ reference tới Throwable/request lớn sau khi scope kết thúc.

Nếu aspect instance vẫn tồn tại sau khi scope logic kết thúc vì bị cache hoặc ThreadLocal giữ reference, có thể gây retention. Điều này không tự có nghĩa advice tiếp tục được gọi; **lifetime tham chiếu và thời gian được phép sử dụng là hai khái niệm khác nhau**.

## 31. Vòng đời aspect trong proxy và bytecode weaving

Mô hình proxy có thể giữ một aspect instance trong invocation handler. Khi tạo proxy, ta có thể quyết định instance cố định hay `Supplier` động. Nhưng bytecode weaving trực tiếp tại method execution có thể sử dụng **một cơ chế instantiation/association khác**.

| Câu hỏi | Proxy/interceptor | Bytecode weaving |
| --- | --- | --- |
| Instance aspect lấy từ đâu? | Handler/provider hoặc dependency của proxy | Runtime của weaver/association được engine tạo |
| Có thể chia sẻ một instance qua nhiều target? | Có, nếu handler dùng cùng instance | Phụ thuộc cơ chế instantiation |
| Có thể gắn instance theo target? | Có nếu tự có registry/scope | Có thể nếu engine hỗ trợ per-object |
| Đường raw target có advice không? | Thường không qua wrapper proxy | Có thể nếu execution bytes đã weave |
| Self-invocation có được quan sát không? | Wrapper proxy thường bỏ qua | Có thể ở execution/call join point được weave |
| `new Aspect()` có tự đăng ký không? | Không tự | Không tự |
| Vòng đời có gắn chặt thread không? | Chỉ khi provider tự định nghĩa như vậy | Tùy engine/context policy |

**Không đồng nhất khái niệm “aspect instance” với “InvocationHandler object”.** Một handler có thể chứa một aspect, nhiều aspect hoặc chọn aspect động. Một aspect có thể được gọi qua nhiều handler.

## 32. Thứ tự advice và việc chọn aspect instance

Khi nhiều aspect cùng khớp, thứ tự advice có thể quyết định **khi nào một provider được yêu cầu**. Ví dụ:

```text
OuterAround
   |
   +-- nếu skip proceed: InnerAdvice không chạy
   |
   +-- nếu proceed: InnerAdvice chọn instance và chạy
```

Nếu inner aspect được tạo **lazy khi advice của nó được gọi**, việc outer quanh lời gọi không proceed khiến inner không được tạo trong tình huống đó. Nhưng nếu inner aspect được tạo **eager**, instance có thể đã tồn tại dù không chạy advice.

Vì thế không thể dùng một kết luận “advice không chạy nên aspect chắc chưa được tạo” nếu chưa biết initialization model.

### Ordering khác association

- **Precedence:** aspect/advice nào bao ngoài, chạy trước/sau?
- **Instantiation:** instance nào được dùng?
- **Pointcut matching:** join point có được chọn không?
- **Proceed behavior:** target/inner advice có thực sự được gọi không?

Chúng tương tác nhưng cần kiểm thử riêng.

## 33. Kiểm thử lifecycle: ma trận cần có

| Tình huống | Điều phải xác minh |
| --- | --- |
| Hai method cùng target, shared aspect | State được dùng chung đúng scope |
| Hai target khác nhau, shared aspect | State tổng có tăng qua cả hai target không? |
| Hai target khác nhau, per-target | Instance/state có tách đúng object identity? |
| Hai proxy cho một target | Scope per-proxy hay per-target đúng contract? |
| Hai object `equals() == true` nhưng khác identity | Registry có gộp sai instance không? |
| Một target được gọi từ hai thread | State per-target có thread-safe không? |
| Hai request trên cùng worker | Có rò context từ request trước không? |
| Một request đi qua hai thread | State theo request có được truyền hoặc tạo lại đúng không? |
| Hai flow lồng nhau | Stack aspect có restore scope ngoài đúng không? |
| Flow kết thúc bằng exception | Có cleanup association/context không? |
| Lời gọi recursive/reentrant | State “current invocation” có bị ghi đè không? |
| Around bỏ qua proceed | Inner aspect/advice có được tạo/chạy đúng semantics không? |
| Raw target gọi trực tiếp | Proxy advice có bị bypass như dự kiến? |
| Self-invocation trong wrapper proxy | Advice inner có thực sự chạy không? |
| Target được GC/unregister | Per-target registry có giữ target không cần thiết? |
| Plugin/class loader reload | Per-type/per-target cache có giữ loader cũ không? |
| Aspect bị tạo eager/lazy | Constructor có chạy đúng thời điểm? |
| Config/reload | Instance cũ dùng policy mới hay snapshot cũ? |
| Retry quanh target | Instance nào đếm lần thử và lần gọi của caller? |
| Exception từ advice | Cleanup và outcome caller đúng contract? |

Một bài test nên quan sát **instance identity**, **state value**, **advice invocation count** và **target execution count** riêng. Nếu chỉ nhìn thấy một dòng log, khó xác định đó là instance cũ hay mới.

### Một số cách kiểm tra trong Java

Có thể gắn một **monotonic test ID** cho mỗi `AspectState` trong phạm vi test để phân biệt object; hoặc so sánh `==` giữa những reference aspect nếu provider công bố hợp lệ. Không nên dùng `hashCode()` hay `System.identityHashCode` làm bằng chứng **duy nhất** về sự duy nhất của instance, vì hash có thể trùng.

## 34. Các lỗi phổ biến khi chọn vòng đời aspect

1. **“Aspect khai báo một lần thì JVM chỉ có một instance.”** Không chắc; phụ thuộc manager, loader và instantiation policy.
2. **“Một lần advice chạy là một aspect được tạo.”** Advice invocation khác aspect creation.
3. **“Per-target là per-request.”** Một target có thể phục vụ nhiều request.
4. **“Per-target an toàn trong đa luồng.”** Hai thread cùng dùng target vẫn có thể cùng vào một aspect.
5. **“Per-flow là ThreadLocal.”** Scope flow cần lifecycle/stack; ThreadLocal chỉ là một carrier gắn với thread.
6. **“Per-flow tự đi theo executor.”** Call stack đồng bộ không tự chuyển sang worker.
7. **“Một singleton aspect không thể dùng local state.”** Local variables là state theo lời gọi, tách biệt field dùng chung.
8. **“Dùng AtomicInteger bảo vệ mọi state.”** Nó chỉ giải quyết một số thao tác trên counter.
9. **“Đổi aspect thành per-invocation là sửa được target race.”** Target/dependency mutable vẫn có thể được chia sẻ.
10. **“Static field được cô lập theo aspect instance.”** Static thuộc class identity, không thuộc object instance.
11. **“Map per-target key theo equals luôn đúng.”** Nếu scope là object identity thì equals-based map có thể gộp sai.
12. **“WeakHashMap luôn tránh leak.”** Strong reference qua value hoặc cấu trúc khác có thể giữ key.
13. **“Aspect theo target sẽ được tạo ngay lúc new Target.”** Có thể chỉ tạo ở lần join point match đầu tiên.
14. **“Một proxy có một aspect instance.”** Handler có thể dùng provider động hoặc nhiều aspects.
15. **“Có aspect shared là mọi lời gọi raw target đều có advice.”** Proxy coverage là một vấn đề khác.
16. **“Aspect instance kết thúc thì resource tự đóng ngay.”** GC không thay cho cleanup rõ ràng.
17. **“Scope request là một thread.”** Thread pool, async và callback phá giả định ấy.
18. **“Đủ counter metric là đủ kiểm chứng lifecycle.”** Cần cả creation count, instance identity và invocation count.
19. **“Thay lifecycle không ảnh hưởng logic.”** Các field/cache/ordering có thể làm outcome khác.
20. **“Cứ dùng scope hẹp nhất là tối ưu.”** Cấp phát, resource và association overhead có thể tăng lớn.
21. **“Shared state sai vì luôn nguy hiểm.”** Shared immutable/stateless hoặc concurrency-safe state có thể là lựa chọn tốt.
22. **“Một aspect theo context chắc được engine hỗ trợ.”** Phải kiểm tra loại scope native hoặc tự xây lifecycle rõ.
23. **“Một class loader mới vẫn dùng singleton instance của class loader cũ.”** Class identity/scope provider có thể khác nhau.
24. **“Một instance per-target tự xóa khi target không còn dùng.”** Registry/reference graph quyết định retention.

## 35. Bài tập tự luyện

1. Chạy `AspectScopeDemo.java` và giải thích tại sao shared count tăng #1 → #2 → #3 dù lời gọi đi qua hai target A/B.
2. Trong nhánh shared, đổi thứ tự gọi B trước A. Dự đoán thứ tự count mà không thay đổi number of instances.
3. Thêm `scopedA.outer("x")` trong nhánh per-target. Có tăng count cho method `process("x!")` nội bộ không? Vì sao?
4. Bỏ proxy và gọi trực tiếp `a.process("z")`; so sánh số target execution và advice invocation.
5. Tạo một proxy thứ hai cho target A nhưng dùng cùng `perTarget` map. State target-A còn được dùng chung không? Khi nào việc key bằng proxy thay đổi kết quả?
6. Thay `IdentityHashMap` bằng `HashMap`, đồng thời cho hai target có `equals/hashCode` bằng nhau. Phân tích nguy cơ gộp association.
7. Đổi `AspectState.count` thành `AtomicInteger`. Biện pháp này sửa vấn đề gì và còn vấn đề nào không được sửa?
8. Thiết kế shared aspect ghi trace ID của mỗi invocation: dùng local/context thay vì mutable field `currentTraceId`.
9. Vẽ timeline hai lời gọi lồng nhau trên cùng thread, minh họa lý do một field `currentOperation` có thể sai dù không có concurrency.
10. Tạo một per-flow scope stack bằng `ThreadLocal<Deque<AspectState>>` và `try/finally`; kiểm thử nested flow và exception.
11. Chuyển một công việc thuộc flow caller sang `ExecutorService`. Giải thích dữ liệu nào không tự có trên worker và trách nhiệm của context carrier.
12. Xây một per-request provider cho nhiều callback trên nhiều thread. Xác định rõ begin/end, cancellation và cleanup.
13. Tạo registry per-target với weak references. Vẽ reference graph có value quay về target và chỉ ra vì sao có thể leak.
14. Thêm một aspect dùng chung chứa cache method metadata. Chọn cache key phù hợp trong hệ thống có overload và nhiều class loader.
15. So sánh metric “số aspect instance tạo ra” với “số lượt target method được gọi”; trường hợp nào hai số này có thể rất khác?
16. Thiết kế một integration test cho hai target được dùng song song. Xác minh per-target aspect cô lập state giữa target nhưng vẫn concurrency-safe trên cùng target.
17. Phân tích một ứng dụng plugin reload nhiều lần: aspect per-type và cache phải quản lý class loader lifecycle ra sao?
18. Đổi provider trong demo thành `() -> new AspectState("new")` cho cả hai proxy. Scope thật là per-target hay per-invocation? Giải thích theo thời điểm provider được gọi.
19. Thêm outer around advice có thể không `proceed`. Aspect inner tạo lazy có được khởi tạo ở đường skip không?
20. Nêu một tình huống nên dùng shared/stateless aspect thay vì per-invocation dù cần đo thời gian chính xác từng lời gọi.
21. Lập bảng thiết kế cho ba concern: logging, audit theo target, tracing theo request; chọn scope của aspect, scope của state và cơ chế cleanup.
22. Thiết kế policy khi target được dispose nhưng proxy còn sống; tìm những reference có thể giữ aspect không giải phóng.
23. Giải thích vì sao một aspect per-target **không tự** ngăn lời gọi `this.inner()` bypass wrapper proxy.
24. Đề xuất test phân biệt (a) advice không match, (b) instance aspect đã có nhưng advice không chạy, (c) lazy aspect chưa được tạo.

**Tự kiểm tra:** Aspect type, aspect instance và advice invocation khác nhau thế nào? Khi nào shared aspect có thể stateless mà vẫn giữ local state mỗi lượt? Per-target có bảo đảm an toàn đa luồng không? Vì sao per-flow không tự xuyên qua executor? Tại sao `static` field không được cô lập bởi per-invocation? Một target có hai proxy có thể dùng một hay hai aspect instance tùy điều gì? Khi nào weak key vẫn dẫn tới memory leak? Làm sao kiểm tra lifecycle mà không nhầm với số log?

## Kết luận

**Aspect dùng chung và aspect được tạo theo ngữ cảnh khác nhau ở phạm vi gắn state và thời gian sống của instance, không chỉ ở số lần advice được gọi.** Aspect shared phù hợp khi stateless, immutable hoặc dùng state được thiết kế chia sẻ đúng; aspect per-target/per-object gắn state với object tương ứng nhưng vẫn cần concurrency control; aspect per-flow/per-invocation cô lập state ở phạm vi động hẹp hơn nhưng cần quản lý nested scope, cleanup và ranh giới bất đồng bộ. Không có lựa chọn “luôn tốt nhất” cho mọi concern.

Khi thiết kế AOP, hãy quyết định tường minh **instance được tạo khi nào, key association là gì, những join point nào được dùng cùng instance, ai giữ reference, ai cleanup, và có bao nhiêu thread/task có thể cùng truy cập**. Sau đó kiểm thử **creation count, instance identity, advice invocations, target executions, raw/proxy bypass và exception paths**. Tách biệt lifecycle với pointcut matching và weaving/interception coverage là chìa khóa để Aspect thực thi đúng, không rò ngữ cảnh và có thể mở rộng an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-lifecycle-002">Vòng đời gắn với từng đối tượng khi công cụ hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Vòng đời gắn với từng đối tượng khi công cụ hỗ trợ trong AOP

> **Mục tiêu:** Hiểu cách một số mô hình Aspect-Oriented Programming (AOP) gắn **một aspect instance với một đối tượng cụ thể**, thay vì chia sẻ một instance duy nhất cho toàn bộ ứng dụng. Phân biệt singleton, per-object, per-this, per-target, per-proxy, per-invocation, per-type và per-control-flow. Xác định thời điểm tạo, tái sử dụng, giải phóng state, cùng rủi ro về object identity, đồng thời, garbage collection và độ bao phủ của pointcut. Các ví dụ Java 17 dùng Java thuần để minh họa; không giả định mọi công cụ AOP có cùng API hoặc cùng ngữ nghĩa.

## 1. Tại sao cần vòng đời aspect theo đối tượng?

Giả sử một ứng dụng quản lý hai worker A và B. Ta muốn đếm riêng số lần hoạt động được quan sát trên từng worker.

~~~text
A.run("x")
A.run("y")
B.run("z")
~~~

Nếu aspect có duy nhất một bộ đếm, các lần được đánh số 1, 2 và 3. Nếu muốn **đếm theo từng object**, kết quả phải là A #1, A #2 và B #1.

Bài toán không nằm ở cú pháp advice chạy trước hay sau, mà ở **vòng đời của instance giữ trạng thái advice**. Có hệ AOP hỗ trợ gắn instance của aspect với mỗi object thỏa điều kiện; hệ khác chỉ đưa ra interceptor chung, buộc người triển khai tự quản lý state.

Điểm cần nhớ: **mỗi object một state** không đồng nghĩa **mỗi lần gọi tạo một state mới**. Các lời gọi của cùng object thường muốn **tái sử dụng** state của object ấy.

## 2. Ba cấp độ cần tách bạch

| Cấp độ | Ý nghĩa | Ví dụ |
|---|---|---|
| **Aspect type** | Định nghĩa mối quan tâm xuyên suốt và advice | Một loại aspect Counter |
| **Aspect instance** | Một đối tượng cụ thể chứa trạng thái của aspect type | Counter dành riêng cho A |
| **Advice invocation** | Một lần advice chạy tại điểm được chọn | Lần gọi A thứ hai |

Sơ đồ:

~~~text
CounterAspect (định nghĩa)
     |
     +--- Aspect instance for A: count=2
     |           |              |
     |       invocation 1   invocation 2
     |
     +--- Aspect instance for B: count=1
                 |
             invocation 1
~~~

Nếu nhầm aspect instance với advice invocation, người viết dễ tạo mới bộ đếm ở mọi lần gọi khiến số luôn bằng 1. Nếu nhầm aspect instance với aspect type, người viết có thể dùng biến static và làm trộn trạng thái A/B.

## 3. Các khái niệm AOP liên quan

**Join point** là điểm/sự kiện trong mô hình thực thi mà cơ chế AOP có thể nhận diện. **Pointcut** là điều kiện chọn các điểm ấy. **Advice** là hành vi bổ sung. **Weaving** hoặc một cơ chế interception tương ứng giúp advice được thực thi ở đúng ranh giới. **Aspect instance lifecycle** quyết định instance nào được sử dụng khi advice chạy.

Cần trả lời bốn câu khác nhau:

1. Công cụ có hỗ trợ loại join point cần quan sát không?
2. Pointcut có chọn điểm trong lần thực thi này không?
3. Đường gọi có đi qua proxy hoặc mã đã được dệt không?
4. Nếu advice chạy, aspect instance nào nhận lời gọi?

Một registry theo target chỉ giải câu số 4. Nó không thể tạo thêm một loại join point mà công cụ không hỗ trợ, cũng không làm lời gọi trực tiếp tới target tự quay qua proxy.

## 4. Những chiến lược vòng đời thường gặp

| Chiến lược | Phạm vi dùng chung state |
|---|---|
| Singleton | Một instance chung trong phạm vi do mô hình xác định |
| Per-object | Một instance cho mỗi object đủ điều kiện |
| Per-target | Một instance theo object đóng vai target của join point |
| Per-this | Một instance theo object đóng vai this của join point |
| Per-proxy | Một state riêng cho từng đối tượng proxy hoặc wrapper |
| Per-invocation | Một state/context mới cho mỗi lần thực thi |
| Per-control-flow | Một state cho phạm vi luồng điều khiển được chọn |
| Per-type | Một instance theo type/phạm vi type |

Các tên per-this và per-target là thuật ngữ có thể gặp trong một số ngôn ngữ AOP; **không có bảo đảm mọi công cụ đều công khai chính xác cùng cách khởi tạo**.

Một aspect quan sát thời gian có thể dùng state **per-object** để lưu tổng thời lượng và state **per-invocation** để lưu mốc bắt đầu từng lần gọi. Hai vòng đời có thể cùng tồn tại trong một thiết kế.

## 5. Singleton aspect khác per-object

Một singleton có thể chứa:

~~~text
total = 0
on every selected join point:
    total = total + 1
~~~

Khi A gọi hai lần và B gọi một lần, tổng là 3. Đây là kết quả đúng nếu mục tiêu là **thống kê toàn cục**.

Nếu mục tiêu là thống kê theo object, cần có association theo A và B. Một singleton chứa map từ object sang state có thể **mô phỏng một phần** hành vi per-object, nhưng vẫn phải tự giải quyết danh tính object, cleanup, concurrency và thời điểm tạo mapping.

Không mặc định singleton-with-map hoàn toàn tương đương với cơ chế per-object aspect instantiation bản địa: bộ máy AOP có thể quy định những thời điểm association khác, API tra cứu aspect instance khác và cách garbage collection khác.

## 6. Per-this và per-target có thể là hai chính sách khác nhau

Một số mô hình join point phân biệt:

- **This**: object được mô hình gọi là this tại join point.
- **Target**: object được mô hình gọi là target/receiver.

Ở một method-call join point, this có thể liên quan object đang thực thi lời gọi, trong khi target là receiver được gọi. Tại method-execution join point, hai vai trò có thể khác hoặc trùng theo quy tắc của mô hình.

~~~text
Caller C   -------- call -------->   Target T
    |                                 |
  per-this(C)                       per-target(T)
~~~

Vì vậy per-this không tự đồng nghĩa per-target. Đặc biệt, **this của một join point** không nhất thiết là biến Java this trong đoạn mã đang được người đọc nhìn tới.

Một số loại join point không có receiver instance thích hợp, ví dụ static method. Không nên giả định mọi điểm đều có object target để gắn một aspect instance.

## 7. Per-proxy khác per-target

Hai proxy có thể ủy quyền cho cùng một đối tượng nghiệp vụ A.

~~~text
Proxy P1 -----+
              +----> Target A
Proxy P2 -----+
~~~

Nếu state nằm bên trong P1 và P2, mỗi proxy có thể đếm riêng từ 1. Nếu chính sách là **một aspect instance duy nhất theo A**, cả hai proxy cần dùng chung một association theo target identity.

Một chuỗi proxy bọc proxy cũng đặt ra câu hỏi proxy nào được xem là target ở mỗi tầng. Không được gọi chung “per-object” mà không nói rõ **object làm khóa** là proxy gần nhất, target cuối cùng hay object this trong join point model.

## 8. Per-invocation khác per-object

Per-object state tồn tại qua nhiều lời gọi của cùng một đối tượng. Per-invocation state chỉ dành cho một lần xuất hiện động.

Ví dụ:

~~~text
State gắn Worker A:
    totalCalls = 12
    totalDurationNanos = 10000000

Lần gọi hiện tại:
    startNanos
    currentError
    safeArgumentSummary
~~~

Nếu đưa startNanos của từng lần gọi vào một trường chung của aspect A, hai lời gọi đồng thời hoặc lồng nhau có thể ghi đè mốc thời gian của nhau.

Giải pháp là giữ trạng thái tạm **trong local variable hoặc invocation context**, còn số liệu tổng hợp có thể ở aspect instance theo đối tượng.

## 9. Per-control-flow, per-type và per-thread

**Per-type** gắn state theo class hoặc một phạm vi kiểu, không theo từng instance của class. Hai đối tượng khác nhau cùng loại có thể dùng chung state type.

**Per-control-flow** gắn state với một lần thực thi trong vùng luồng điều khiển đã được chọn, không phải với một object sống lâu qua nhiều request.

**Per-thread** gắn state theo thread; một thread pool có thể dùng cùng thread cho nhiều request, còn một hoạt động bất đồng bộ có thể di chuyển qua nhiều thread.

~~~text
Thread T1: A.run(), B.run()
Thread T2: A.run()

Per-object:  hai lời gọi A có thể dùng chung State A
Per-thread:  T1 và T2 không dùng chung thread state
Per-call:    mỗi run có context riêng
~~~

Không sử dụng ThreadLocal làm giải pháp per-target mặc định: ThreadLocal trả lời câu hỏi “đang ở thread nào”, không phải “đang thao tác object nào”.

## 10. Vòng đời bắt đầu khi nào?

Một mô hình instantiation theo object có thể hoạt động lazily:

~~~text
T0: Target A được tạo
T1: A tồn tại, chưa có join point khớp
T2: Một join point thỏa điều kiện association trên A
T3: Aspect instance của A được tạo hoặc truy xuất
T4: Advice chạy bằng instance đó
T5: Những lần phù hợp sau dùng lại instance
T6: Không còn association theo hợp đồng → có thể thu hồi
~~~

Đây là **một mô hình khái niệm**, không phải trình tự bắt buộc của mọi engine.

Không nên suy rằng mỗi lần new target sẽ lập tức new aspect instance. Một công cụ có thể chỉ khởi tạo khi pointcut tạo association khớp lần đầu. Có thể có object chưa bao giờ được liên kết vì nó không phát sinh điểm tương ứng.

## 11. Pointcut tạo association có thể khác pointcut chạy advice

Một số mô hình phân biệt:

1. **Điều kiện để tạo hoặc gắn aspect instance với object**.
2. **Những join point mà advice trong instance ấy cần chạy**.

Pseudocode:

~~~text
associationCondition:
    target là Worker
    AND có lần thực thi run(...) phù hợp

adviceCondition:
    một điểm thao tác khác trên target đã được liên kết
~~~

Đây không phải cú pháp của công cụ AOP cụ thể.

Điều này ảnh hưởng giải thích trường hợp object A đã tồn tại lâu nhưng advice vẫn chưa có state; có thể association pointcut chưa bao giờ khớp. Ngược lại, aspect instance đã tồn tại cũng không có nghĩa mọi method của A đều được advice chọn.

## 12. Ví dụ Java 17 số 1: một state trên mỗi wrapper

Ví dụ đầu tiên dùng decorator thuần Java để chứng minh hai wrapper của cùng target **không tự dùng chung bộ đếm**.

Lưu **PerWrapperLifecycleDemo.java**:

~~~java
public class PerWrapperLifecycleDemo {
    interface Task {
        String run(String value);
    }

    static final class RealTask implements Task {
        private final String name;

        RealTask(String name) {
            this.name = name;
        }

        @Override
        public String run(String value) {
            System.out.println("[business] " + name + " " + value);
            return name + ":" + value;
        }
    }

    static final class ObservedTask implements Task {
        private final Task target;
        private final String wrapperName;
        private int calls;

        ObservedTask(Task target, String wrapperName) {
            this.target = target;
            this.wrapperName = wrapperName;
        }

        @Override
        public String run(String value) {
            int number = ++calls;
            System.out.println("[advice " + wrapperName
                    + "] before #" + number);
            try {
                return target.run(value);
            } finally {
                System.out.println("[advice " + wrapperName
                        + "] after #" + number);
            }
        }
    }

    public static void main(String[] args) {
        Task target = new RealTask("A");
        Task w1 = new ObservedTask(target, "W1");
        Task w2 = new ObservedTask(target, "W2");

        System.out.println("[caller] " + w1.run("x"));
        System.out.println("[caller] " + w1.run("y"));
        System.out.println("[caller] " + w2.run("z"));
    }
}
~~~

Chạy:

~~~bash
javac PerWrapperLifecycleDemo.java
java PerWrapperLifecycleDemo
~~~

**Stdout:**

~~~text
[advice W1] before #1
[business] A x
[advice W1] after #1
[caller] A:x
[advice W1] before #2
[business] A y
[advice W1] after #2
[caller] A:y
[advice W2] before #1
[business] A z
[advice W2] after #1
[caller] A:z
~~~

### 12.1. Giải thích

Cả W1 và W2 ủy quyền cho cùng target A, nhưng trường calls nằm **trong mỗi wrapper**. Vì thế W1 đi tới #2, còn W2 bắt đầu #1.

Đây là **per-wrapper**, không tự là per-target. Nếu chính sách yêu cầu state duy nhất cho A, phải bảo đảm chỉ dùng một wrapper hoặc có cơ chế liên kết state chung theo đúng target A.

### 12.2. Lưu ý về finally và đồng thời

Finally chạy khi hoạt động rời phạm vi normal/exceptional trong trường hợp unwinding thông thường. Nó không hứa chạy nếu tiến trình bị kết thúc cưỡng bức hoặc hoạt động không bao giờ thoát. Nếu chính logging trong finally ném lỗi, nó còn có thể che ngoại lệ gốc.

Trường int calls trong ví dụ không thread-safe. Bài minh họa lời gọi tuần tự để stdout xác định, không nên dùng phép ++calls không đồng bộ trong hệ nhiều thread.

## 13. Ví dụ Java 17 số 2: registry theo đúng target identity

Bây giờ hai proxy khác nhau phải cùng truy xuất state nếu chúng trỏ tới **cùng object target**. Chúng ta dùng **IdentityHashMap** có đồng bộ để phân biệt object identity với equals.

**Đây là Java thuần mô phỏng association theo target**, không phải engine AOP tự dệt bytecode. Registry dùng strong references và cần release tường minh.

Lưu **PerTargetRegistryDemo.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;

public class PerTargetRegistryDemo {
    interface Job {
        String run(String value);
    }

    static final class JobImpl implements Job {
        private final String name;

        JobImpl(String name) {
            this.name = name;
        }

        @Override
        public String run(String value) {
            System.out.println("[business] " + name + " " + value);
            return name + ":" + value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof JobImpl;
        }

        @Override
        public int hashCode() {
            return 17;
        }
    }

    static final class State {
        private int calls;

        synchronized int next() {
            return ++calls;
        }
    }

    static final class Registry {
        private final IdentityHashMap<Job, State> states =
                new IdentityHashMap<>();

        synchronized State stateFor(Job target) {
            State state = states.get(target);
            if (state == null) {
                state = new State();
                states.put(target, state);
            }
            return state;
        }

        synchronized void release(Job target) {
            states.remove(target);
        }

        synchronized int size() {
            return states.size();
        }
    }

    static Job wrap(Job target, String label, Registry registry) {
        return (Job) Proxy.newProxyInstance(
                Job.class.getClassLoader(),
                new Class<?>[] {Job.class},
                (proxy, method, args) -> {
                    int n = registry.stateFor(target).next();
                    System.out.println("[advice] " + label + " #" + n);
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
        );
    }

    public static void main(String[] args) {
        Registry registry = new Registry();
        JobImpl a = new JobImpl("A");
        JobImpl b = new JobImpl("B");

        System.out.println("a.equals(b)=" + a.equals(b));

        Job a1 = wrap(a, "A", registry);
        Job a2 = wrap(a, "A", registry);
        Job b1 = wrap(b, "B", registry);

        System.out.println("[caller] " + a1.run("x"));
        System.out.println("[caller] " + a2.run("y"));
        System.out.println("[caller] " + b1.run("z"));
        System.out.println("registry size=" + registry.size());

        registry.release(a);
        System.out.println("registry size after release A="
                + registry.size());

        System.out.println("[caller] " + a1.run("again"));
        System.out.println("registry size end=" + registry.size());
    }
}
~~~

Chạy:

~~~bash
javac PerTargetRegistryDemo.java
java PerTargetRegistryDemo
~~~

**Stdout:**

~~~text
a.equals(b)=true
[advice] A #1
[business] A x
[caller] A:x
[advice] A #2
[business] A y
[caller] A:y
[advice] B #1
[business] B z
[caller] B:z
registry size=2
registry size after release A=1
[advice] A #1
[business] A again
[caller] A:again
registry size end=2
~~~

### 13.1. Tại sao A có #1 rồi #2?

Hai proxy a1 và a2 dùng chung target object a. Registry tra cứu bằng đúng reference a, vì thế cả hai lời gọi dùng chung State A.

B là object khác, có State B và bắt đầu #1. Đây là hành vi **per-target** được tự định nghĩa rõ trong demo.

### 13.2. Tại sao a.equals(b) bằng true vẫn tạo hai state?

Phương thức equals trong demo cố tình coi mọi JobImpl là bằng nhau. Nhưng A và B không phải cùng reference. IdentityHashMap dùng **identity** chứ không dùng logical equality, vì vậy tách riêng state của hai object.

Override equals như vậy chỉ nhằm dạy khác biệt; không phải mẫu equality nên dùng trong phần mềm thực tế.

### 13.3. Tại sao release rồi A chạy lại có #1?

registry.release(a) gỡ association cũ, nhưng target a và proxy a1 vẫn tồn tại. Khi a1.run("again") gọi registry.stateFor(a), một State A mới được tạo, nên số đếm bắt đầu lại.

Bài học: **thời điểm release phải phù hợp với vòng đời sử dụng**, không chỉ kết thúc một lời gọi. Nếu release quá sớm, object đang sống có thể bị gắn lại state mới và mất lịch sử quan sát.

### 13.4. Giới hạn của registry demo

IdentityHashMap giữ strong reference tới target. Nếu không release, target/state có thể sống lâu hơn cần thiết. synchronized bảo vệ thao tác trên map và việc tăng đếm, nhưng không tự cung cấp giao thức release an toàn khi có in-flight invocations. Handler chỉ quan sát những lời gọi **đi qua proxy**.

Object methods như equals, hashCode và toString của proxy có thể được chuyển tới handler theo cơ chế Java Dynamic Proxy; ví dụ chỉ gọi run trên proxy. Production handler phải xử lý riêng những method ấy và tránh recursion.

## 14. Identity khác equality và business ID

Có ba cách nhận diện thường bị trộn:

1. **Reference identity:** a và b thật sự là cùng object; kiểm tra bằng ==.
2. **Logical equality:** a.equals(b) là true theo implementation.
3. **Business identity:** hai object có thể cùng mã tài khoản, đơn hàng hoặc entity ID.

Một per-object lifecycle theo reference identity sẽ coi hai instance khác nhau là hai đối tượng, ngay cả khi chúng có cùng business ID.

Nếu nghiệp vụ yêu cầu trạng thái chung theo entity ID, đó là một **chính sách keyed by business identity**, không nên tự gọi là per-object runtime instance. ID có thể bị tái sử dụng hoặc đại diện nhiều bản sao object trong nhiều request.

## 15. Strong references và rò rỉ bộ nhớ

Một singleton registry chứa map với strong keys có thể giữ sống hàng nghìn target đã không còn được ứng dụng dùng:

~~~text
Registry
   |
   +---strong---> Target A
   |
   +---strong---> State A
~~~

Garbage collector không thể thu hồi Target A nếu Registry vẫn có strong reference. Đây là điểm dễ bỏ qua khi dùng per-target state.

Có thể quản lý bằng explicit release, scope được đóng đúng lúc hoặc một dạng association yếu phù hợp nếu công cụ/cấu trúc dữ liệu hỗ trợ. Không được suy rằng bộ máy AOP tự dọn mọi per-object instance khi target hết được dùng.

## 16. WeakHashMap không mặc nhiên là weak identity map

WeakHashMap của Java sử dụng **weak keys** nhưng vẫn so sánh khóa theo **equals/hashCode**. Nó khác IdentityHashMap.

Cần chú ý:

- Hai object equals=true vẫn có thể bị gộp theo một entry.
- Value có thể giữ strong reference tới chính key, làm key không được thu hồi.
- WeakHashMap không tự thread-safe.
- GC không chạy vào thời điểm xác định.
- Entry biến mất vì GC không phải callback đáng tin để đóng resource.

Nếu yêu cầu là weak association **theo reference identity**, cần cấu trúc riêng có weak reference, cơ chế so sánh identity, xử lý key đã bị thu hồi và thread safety, hoặc dùng cơ chế của công cụ đã được kiểm chứng.

## 17. Garbage collection không thay thế close/release

Không nên dựa vào lúc target hoặc aspect instance bị GC để:

- Đóng connection/socket/file ở thời điểm bắt buộc.
- Cam kết ghi audit cuối cùng.
- Commit/rollback transaction.
- Giải phóng ngay tài nguyên có quota.
- Báo rằng toàn bộ nghiệp vụ đã kết thúc.

GC có thời điểm không xác định. Một resource cần cleanup đúng thời điểm phải có scope hoặc close/release tường minh. Per-object aspect lifecycle giải quyết **association của state**, không phải mọi loại vòng đời tài nguyên.

## 18. Đồng thời trên cùng target

Hai thread cùng gọi target A có thể dùng chung một aspect instance A.

~~~text
Thread T1: A.run("x")
Thread T2: A.run("y")
~~~

Nếu aspect state chứa int calls và dùng ++calls không đồng bộ, số cuối có thể mất cập nhật. Có thể dùng AtomicInteger cho bộ đếm đơn giản hoặc synchronized/lock cho invariant phức tạp.

Per-object **không đồng nghĩa per-thread**. Mỗi object có một state không có nghĩa state chỉ được một thread chạm tới.

### 18.1. Synchronized không giải quyết mọi release race

Một thread có thể lấy State A, thread khác release mapping, rồi thread thứ ba tạo State A mới. Trong lúc đó có thể có hai state đang được sử dụng cho cùng một target.

Nếu hợp đồng cần bảo đảm tuyệt đối một state cho mọi in-flight call, phải có quy tắc quản lý lifetime, shutdown hoặc khóa theo scope, không chỉ synchronized cho từng method tra map.

## 19. Lời gọi lồng nhau và reentrancy

Một target A có thể chạy outer rồi gọi inner:

~~~text
A.outer()
    -> A.inner()
~~~

Nếu cả hai được advice quan sát trên cùng aspect A, việc lưu currentStartTime hay currentArguments trong một field đơn giản của aspect sẽ bị lời gọi trong ghi đè.

Vì vậy dùng **local variable hoặc per-invocation context** cho dữ liệu lần gọi. Chỉ lưu **aggregate counters** hay trạng thái lâu dài trong aspect A và bảo vệ cập nhật khi cần.

Lỗi reentrancy có thể xuất hiện ngay trên một thread, không đòi hỏi đồng thời nhiều thread.

## 20. Self-invocation và giới hạn của proxy

Với một interface proxy, client gọi proxy.outer(), handler chuyển tiếp tới target.outer(); bên trong target dùng this.inner() thì lời gọi inner thường **không đi qua proxy**.

~~~text
Client -> Proxy -> Target.outer()
                       |
                       +-> this.inner()
~~~

Registry per-target có thể hoạt động đúng nhưng không thấy một advice invocation riêng cho inner.

Một cơ chế weaving ở method execution có thể quan sát inner nếu mã liên quan đã được dệt. Một subclass proxy có thể có tình huống virtual dispatch quay tới override trên proxy instance. Vì vậy không tuyên bố mọi self-invocation đều bỏ qua mọi loại AOP.

Cần kiểm tra **join point model, dispatch và runtime path**, không chỉ association map.

## 21. Pointcut và vòng đời là hai tầng độc lập

Một aspect per-target instance không có nghĩa mọi method của target sẽ được advice quan sát.

Nếu pointcut chỉ chọn run(...) mà target còn có getName(), getState() và reset(), những lời gọi không khớp không tự tạo advice invocation. Một cơ chế cũng có thể chọn kiểu method execution mà proxy chỉ cung cấp interception ở lời gọi interface.

Nếu object A chưa có một join point khớp pointcut tạo association, instance aspect của nó có thể **chưa được tạo** trong mô hình lazy.

Không dùng wildcard pointcut để bù cho loại join point mà công cụ không hỗ trợ: biểu thức rộng không biến constructor/field access thành method interception.

## 22. Vấn đề dữ liệu riêng tư và state lâu dài

Một per-object aspect có thể tồn tại lâu và giữ tham chiếu tới object, đối số hoặc ngoại lệ. Việc đó có rủi ro:

- Giữ dữ liệu bí mật trong heap lâu hơn cần thiết.
- Vô tình lưu toàn bộ object graph lớn.
- Lưu Throwable có stack trace hoặc tham chiếu tới các object khác.
- Dùng business ID nhạy cảm làm metric label.
- Nhầm trạng thái nghiệp vụ cốt lõi với trạng thái quan sát.

Nên lưu dữ liệu tối thiểu như count, aggregateDuration hoặc mã lỗi đã chuẩn hóa. Những đối số, exception và mốc thời gian **của lần gọi hiện tại** nên ở scope ngắn hạn.

Đừng dùng aspect per-object để ẩn các quy tắc nghiệp vụ bắt buộc trong một ranh giới proxy có thể bị bỏ qua.

## 23. Một aspect instance có thể bị gọi lại bao nhiêu lần?

Cần phân biệt ba bộ đếm:

- **Số lần join point được chọn và advice chạy.**
- **Số lần continuation/proceed được gọi.**
- **Số lần phần gốc thực sự thực thi.**

Một around advice có thể short-circuit, không gọi proceed. Một advice khác có thể retry phần tiếp theo khi được cơ chế cho phép. Do đó một lần advice không nhất thiết tương ứng một lần chạy phần gốc.

Nếu aspect per-object dùng counter để đo nghiệp vụ, hãy xác định đang đếm **sự kiện nào**, đồng thời kiểm tra return value, exception và thứ tự nhiều advice.

## 24. Tương tác nhiều aspect với vòng đời khác nhau

Một lần method execution có thể được quan sát bởi:

~~~text
Singleton Metrics Aspect
     |
Per-target Counter Aspect
     |
Control-flow Trace Aspect
     |
Original method
~~~

Sơ đồ là một thứ tự minh họa, không phải thứ tự mặc định của các hệ AOP.

Aspect singleton có thể đo tổng số lượt, per-target giữ thống kê A/B riêng, còn control-flow giữ correlation context của một yêu cầu. Khi một around advice bao ngoài short-circuit, advice bên trong có thể không chạy và một association chưa chắc được tạo.

Không suy số lần aspect instance được tạo từ số lần advice xuất hiện nếu chưa xác minh chính sách instantiation và nesting.

## 25. Ma trận kiểm thử per-object lifecycle

| Tình huống | Kỳ vọng kiểm chứng |
|---|---|
| A gọi hai lần | Cùng State A được dùng lại |
| B gọi lần đầu | State B độc lập, bắt đầu từ 1 |
| Hai proxy cùng target A | Chia sẻ hay tách state đúng thiết kế |
| Hai target equals=true nhưng identity khác | Nếu yêu cầu identity, phải có hai state |
| Object chưa khớp join point tạo association | Lazy instance có được tạo đúng thời điểm? |
| Release rồi gọi lại A | Reassociation/reset đúng hợp đồng |
| A được gọi đồng thời | Counter/invariant không hỏng |
| A có lời gọi lồng nhau | Context per-invocation không ghi đè |
| Caller gọi target trực tiếp | Độ bao phủ đúng với proxy/weaver thực tế |
| Self-invocation | Có hay không có join point độc lập đúng mô hình |
| Exception từ phần gốc | Advice không tự nuốt hoặc thay lỗi ngoài chủ đích |
| Object hết được dùng | Association không giữ sống mãi nếu mục tiêu cần thu hồi |

Unit test cho registry chỉ chứng minh **association logic**. Cần integration test với công cụ AOP thực để chứng minh **độ bao phủ join point**.

## 26. Những lỗi phổ biến

**Lỗi 1 — State mới mỗi lần gọi:** mọi invocation bắt đầu từ 1; đó là per-call, không phải per-target.

**Lỗi 2 — Biến static chung:** A và B dùng cùng counter dù cần số riêng.

**Lỗi 3 — Nhầm proxy identity với target identity:** hai proxy cùng target vẫn có hai bộ state.

**Lỗi 4 — HashMap thay IdentityHashMap:** hai object logical equals bị gộp không đúng ngữ nghĩa.

**Lỗi 5 — WeakHashMap như weak-identity map:** weak key không làm thay đổi equality semantics.

**Lỗi 6 — Registry strong keys không cleanup:** giữ object sống và gây memory leak.

**Lỗi 7 — Dựa vào GC đóng resource:** không có thời điểm bảo đảm.

**Lỗi 8 — currentStartTime là field dùng chung của aspect:** lời gọi đồng thời/lồng nhau ghi đè.

**Lỗi 9 — Release khi vẫn còn in-flight calls:** hai state khác nhau có thể tồn tại trong một khoảng thời gian.

**Lỗi 10 — Nhầm this và target:** association theo caller khác association theo receiver.

**Lỗi 11 — Quên đường gọi bỏ qua proxy:** thiếu advice invocation dù nghiệp vụ đã chạy.

**Lỗi 12 — Mặc định aspect được tạo cùng target constructor:** có mô hình tạo lazy theo join point phù hợp.

**Lỗi 13 — Lưu raw arguments và Throwable mãi:** dữ liệu nhạy cảm/object graph bị giữ lâu.

**Lỗi 14 — Dùng per-object aspect làm hàng rào bảo mật duy nhất:** pointcut/weaving path có thể không bao phủ mọi entry point bắt buộc.

## 27. Lưu ý hiệu năng và số lượng aspect instance

Nếu ứng dụng có vài object sống lâu, per-object state thường dễ hiểu. Nếu có hàng trăm nghìn object ngắn hạn, mỗi object được liên kết một state có thể tạo chi phí đáng kể:

- Cấp phát state mới và lookup mapping.
- Đồng bộ trong registry.
- Retention của target và dữ liệu liên quan.
- GC, heap footprint và cleanup.
- Logging/metrics có cardinality quá lớn.
- Tăng chi phí đối với method có tần suất gọi cao.

Đừng dùng per-object khi chỉ cần một bộ đếm chung theo loại operation. Ngược lại, nếu requirement cần state độc lập theo target thật sự, đừng thay bằng singleton global chỉ vì rẻ hơn.

Có thể đo số association còn sống, tốc độ tạo state, heap/GC pressure, contention và chênh lệch giữa số lần nghiệp vụ và số lần advice.

## 28. Quy trình thiết kế một aspect theo đối tượng

1. Xác định rõ state thuộc về object runtime nào.
2. So sánh per-object với singleton, per-type và per-call.
3. Xác định join point được công cụ hỗ trợ.
4. Xác định this/target/proxy identity theo mô hình.
5. Chọn điều kiện pointcut tạo association.
6. Xác định instance tạo sớm hay lazy.
7. Xác định các advice dùng lại state.
8. Xác định cấu trúc registry hoặc cơ chế instantiation của AOP.
9. Xác định lifecycle cleanup/release và garbage collection.
10. Tách aggregate state khỏi per-invocation context.
11. Thiết kế đồng thời, nested calls và release khi đang hoạt động.
12. Kiểm thử hai proxy cùng target.
13. Kiểm thử equals=true nhưng object identity khác.
14. Kiểm thử các đường ngoài proxy/weaving.
15. Đảm bảo advice quan sát không đổi return/exception ngoài chủ đích.
16. Đo chi phí vận hành và viết rõ giới hạn chưa được cam kết.

## 29. Câu hỏi tự kiểm tra

### Câu 1: Hai proxy cùng target

P1 và P2 cùng bọc A, mỗi proxy có bộ đếm riêng. Sau một lần gọi qua mỗi proxy, có chắc counter target A là 2 trong một state chung không?

**Đáp án:** không. Có thể P1 là 1 và P2 là 1; đó là per-wrapper.

### Câu 2: Equals và identity

A và B được tạo riêng, nhưng equals trả true. Nếu lifecycle theo reference identity, chúng có dùng chung state không?

**Đáp án:** không. IdentityHashMap giúp mô phỏng sự phân biệt đó.

### Câu 3: Aspect khởi tạo lúc target new

Có bắt buộc mọi per-object aspect instance xuất hiện ngay khi object target được tạo không?

**Đáp án:** không. Nhiều mô hình có thể tạo lazily khi join point phù hợp khớp.

### Câu 4: Strong key registry

Một singleton registry giữ target làm strong key. Nếu không release, target có thể bị giữ sống không?

**Đáp án:** có. Registry giữ tham chiếu tới target.

### Câu 5: Hai thread và một counter

Hai thread cùng cập nhật trường int calls bằng ++calls. Có bảo đảm tăng hai lần không?

**Đáp án:** không. Cần atomic/synchronized phù hợp.

### Câu 6: Per-this và per-target

Một method-call join point có caller C và target T. Có chắc per-this và per-target chọn cùng object không?

**Đáp án:** không. Phụ thuộc mô hình join point.

### Câu 7: Self-invocation

Một proxy bọc A.outer(), bên trong target gọi this.inner(). Không thấy advice inner; có chắc inner không chạy?

**Đáp án:** không. Có thể là lời gọi nội bộ không qua proxy.

### Câu 8: Thời gian bất đồng bộ

Advice đo thời gian từ vào method tới khi method trả một future. Đây có phải thời gian thực sự hoàn tất công việc không?

**Đáp án:** không. Cần một ranh giới hoàn tất của tác vụ nếu muốn đo vòng đời công việc.

### Câu 9: Cleanup bằng GC

Có nên trông chờ per-object aspect bị GC để đóng transaction đúng lúc?

**Đáp án:** không. GC không có thời điểm xác định; cleanup bắt buộc cần cơ chế tường minh.

### Câu 10: Singleton vs per-target

Một singleton với map theo target có thể tạo số đếm per-target. Có thể tuyên bố nó tương đương mọi ngữ nghĩa per-target instantiation của AOP không?

**Đáp án:** không. Cần kiểm tra thời điểm association, API, identity, cleanup, pointcut và concurrency.

## 30. Kết luận

**Vòng đời per-object** là việc liên kết trạng thái hoặc instance aspect với **một đối tượng xác định**, trong phạm vi mà công cụ AOP thực sự hỗ trợ. Nó không đồng nghĩa một instance mỗi lần gọi, một instance mỗi proxy hay một instance mỗi thread.

Muốn làm đúng phải xác định ba tầng riêng:

1. **Loại join point và pointcut**: lần xuất hiện nào được chọn?
2. **Đường can thiệp**: lời gọi có thật sự đi qua proxy hoặc vùng mã đã weaving?
3. **Association và vòng đời**: state được gắn theo this, target, proxy hay identity khác; khi nào tạo, dùng lại và giải phóng?

Per-object state có thể hữu ích cho số liệu hoặc policy riêng của từng đối tượng, nhưng mang theo trách nhiệm về concurrency, reentrancy, strong/weak references, memory leak và cleanup. Khi hiểu rõ các ranh giới đó, người thiết kế có thể lựa chọn giữa cơ chế per-object có sẵn của công cụ và một giải pháp registry/decorator có hợp đồng tường minh, thay vì phụ thuộc vào giả định “mỗi object tự động có một aspect”.

**Câu hỏi cần trả lời khi đánh giá thiết kế:** *“Aspect instance này thuộc về object nào, sự kiện nào tạo liên kết đó, những lời gọi nào được dùng lại state, và điều gì đảm bảo liên kết không tồn tại quá lâu hoặc biến mất quá sớm?”*


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-lifecycle-003">Vòng đời gắn với luồng điều khiển khi công cụ hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Vòng đời gắn với luồng điều khiển khi công cụ hỗ trợ trong Aspect/AOP

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), **aspect** đóng gói những hành vi cắt ngang như ghi nhận thời lượng, theo dõi một thao tác nghiệp vụ hoặc thống kê các điểm can thiệp. Một câu hỏi quan trọng là: **trạng thái của aspect tồn tại trong bao lâu, và có được chia sẻ giữa những lần gọi không?**

Một số mô hình AOP có thể tạo **một instance aspect cho mỗi luồng điều khiển động được chọn**. Khi một điểm bắt đầu (*entry join point*) khớp điều kiện, cơ chế tạo hoặc kích hoạt một instance gắn với **lần đi vào luồng đó**; những điểm can thiệp nằm trong phạm vi động thích hợp có thể dùng cùng instance. Khi luồng điều khiển rời phạm vi, instance đó không còn là instance đang hoạt động của luồng ấy. Cách tổ chức này thường được gọi là **per-control-flow aspect lifecycle**, hoặc **`percflow`** trong những ngôn ngữ AOP có cơ chế tương ứng.

Điều này **khác** với chỉ dùng biểu thức **`cflow`** để lọc pointcut. `cflow` có thể kiểm tra một điểm đang thuộc một luồng gọi được chọn, còn một cơ chế **per-control-flow instantiation** quyết định **tạo và quản lý instance aspect theo mỗi lần đi vào luồng**. Một hệ thống có thể hỗ trợ kiểm tra luồng điều khiển nhưng **không** hỗ trợ khởi tạo aspect instance theo luồng. Không được coi mọi cách triển khai proxy/interceptor hoặc mọi bộ weaving đều có khả năng giống nhau.

Bài này chỉ nói về Aspect/AOP cơ bản và nâng cao nói chung. Ví dụ cú pháp thuộc một ngôn ngữ AOP cụ thể sẽ được ghi rõ phạm vi hỗ trợ; ví dụ **Java 17 thuần** mô phỏng lifecycle để hiểu hành vi và có thể biên dịch bằng JDK mà không cần một weaver riêng.

## 1. Mục tiêu học tập

Sau bài, bạn có thể:

1. Phân biệt **aspect definition**, **aspect instance** và **activation/lifetime** theo một luồng điều khiển.
2. Hiểu **dynamic control flow** khác phạm vi mã nguồn tĩnh và khác “một HTTP request” hoặc “một thread” theo nghĩa mặc định.
3. Phân biệt pointcut **`cflow`**, **`cflowbelow`**, chính sách tạo instance kiểu **`percflow`** và cơ chế tạo instance khác.
4. Trình bày những gì xảy ra khi có **nhiều lời gọi lồng nhau, đệ quy, ngoại lệ, trả về sớm và cleanup**.
5. Biết khi nào hai join point chia sẻ cùng instance, và khi nào phải tạo/khôi phục instance theo phạm vi mới.
6. Đánh giá nguy cơ chia sẻ trạng thái toàn cục, rò rỉ `ThreadLocal`, truyền sai ngữ cảnh qua async và tác động của thứ tự interceptor.
7. Đọc ví dụ Java 17 mô phỏng per-flow instance qua một ngăn xếp theo luồng, với đầu ra xác định.
8. Thiết kế kiểm thử phân biệt **instance aspect per-flow** và **chỉ lọc pointcut theo control flow**, đồng thời giữ nguyên kết quả và ngoại lệ nghiệp vụ.

## 2. Các thuật ngữ phải tách bạch

| Khái niệm | Nghĩa | Ví dụ |
| --- | --- | --- |
| **Join point** | Điểm mà mô hình AOP cho phép can thiệp | Thực thi phương thức `handle()` |
| **Pointcut** | Điều kiện chọn những join point phù hợp | “Thực thi `dao.load()` khi đang nằm trong `handle()`” |
| **Advice** | Hành vi bổ sung chạy tại điểm được chọn | Tăng bộ đếm, ghi trace |
| **Aspect definition** | Bản mô tả khía cạnh và quy tắc của nó | Khai báo một `TraceAspect` |
| **Aspect instance** | Một đối tượng có trạng thái chứa bộ đếm/tài nguyên | Instance dành cho `handle` lần thứ nhất |
| **Instantiation policy** | Quy tắc xác định có bao nhiêu instance và chúng gắn với cái gì | Singleton, per-target, per-control-flow nếu được hỗ trợ |
| **Dynamic control flow** | Chuỗi gọi đang diễn ra từ một điểm vào trong runtime | `handle → validate → load` |
| **Activation** | Khoảng mà một instance có hiệu lực trong phạm vi | Từ lúc vào root tương ứng tới khi rời root |
| **Scope cleanup** | Dỡ liên kết instance khỏi ngữ cảnh hoạt động | Pop khỏi stack khi root kết thúc |

**Một định nghĩa aspect không đồng nghĩa một instance duy nhất.** Công nghệ có thể dùng một instance toàn chương trình, nhiều instance theo đối tượng, hoặc nhiều instance theo phạm vi luồng điều khiển. Cũng không phải cứ có một phương thức gọi tên `begin` thì aspect tự động được tạo tại đó: điều kiện chọn root và khả năng instantiation phải được công cụ hỗ trợ.

## 3. Dynamic control flow là gì?

Hãy xem lời gọi đồng bộ:

```text
main()
  |
  +-- Checkout.handle("A")
        |
        +-- Validation.check()
        |
        +-- Catalog.find()
              |
              +-- Database.read()
```

Trong khi `Checkout.handle("A")` **đang hoạt động trên ngăn xếp lời gọi**, những điểm bên trong như `Catalog.find()` hoặc `Database.read()` thuộc **luồng điều khiển động** bắt đầu từ `handle("A")`, theo mô hình kiểm tra luồng điều khiển phù hợp.

Điều khác biệt với bộ lọc **theo tên package hoặc vị trí mã nguồn** là dynamic control flow phản ánh **đường gọi thực tế ở runtime**:

- Một phương thức trong package dữ liệu có thể nằm trong flow của `handle()` ở lần gọi thứ nhất.
- Cùng phương thức đó khi được gọi độc lập từ công việc nền có thể **không nằm** trong flow của `handle()`.
- Một phương thức thuộc package khác vẫn có thể tham gia flow, nếu đường thực thi đi tới nó.
- Thời điểm lời gọi bắt đầu và kết thúc thay đổi tập điểm đang nằm trong flow.

### 3.1. Ví dụ hai lần gọi cùng mã nhưng context khác nhau

```text
Lần 1: main -> handle(A) -> dao.load(A)
Lần 2: main -> dao.load(B)
```

Nếu pointcut yêu cầu `dao.load` **đang ở bên trong dynamic control flow của `handle`**, lần 1 có thể khớp; lần 2 không khớp. Đó là lựa chọn dựa trên **call stack đang chạy**, không chỉ dựa vào tên của `dao.load`.

Một điều kiện kiểu `within(package)` hoặc `withincode(method)` (nếu được hỗ trợ) thường nói về **phạm vi tĩnh của mã**, không thể mặc nhiên thay cho dynamic flow. Các bộ lọc tĩnh và động có thể phối hợp nhưng không đồng nhất.

### 3.2. Dynamic flow khác đối tượng và khác thread

Một `Checkout` instance có thể xử lý nhiều yêu cầu. Nếu dùng **per-target aspect**, các lần xử lý cùng target có thể dùng chung aspect instance theo chính sách đó. Nếu dùng **per-control-flow aspect**, các lần đi vào root khác nhau có thể có **các instance riêng**, dù target giống nhau.

Một thread cũng có thể xử lý **nhiều root lần lượt**, hoặc root lồng nhau. Vì vậy “per-control-flow” không có nghĩa “mỗi thread chỉ có một aspect instance vĩnh viễn”.

## 4. `cflow`, `cflowbelow` và chính sách tạo instance

Một số ngôn ngữ AOP có các khái niệm như sau:

- **`cflow(P)`**: chọn điểm đang ở **trong dynamic control flow của một điểm khớp P**, thường gồm cả chính điểm gốc P theo ngữ nghĩa của công cụ.
- **`cflowbelow(P)`**: chọn điểm **bên dưới** P trong dynamic call flow, thường không gồm chính điểm gốc.
- **`percflow(P)`**: chính sách **tạo aspect instance theo mỗi lần luồng điều khiển được xác lập từ P** nếu hệ AOP hỗ trợ.
- **`percflowbelow(P)`**: biến thể per-flow theo phạm vi bên dưới điểm gốc nếu được công nghệ hỗ trợ; chi tiết về thời điểm tạo/khả dụng cần kiểm tra trong ngôn ngữ đó.

Các tên được nêu là **thuật ngữ/cú pháp thuộc những mô hình AOP cụ thể**, không phải từ khóa Java chuẩn và không xuất hiện trong mọi framework.

### 4.1. Pointcut động không nhất thiết tạo instance mới

Giả sử một aspect dùng chung toàn ứng dụng có advice:

```text
khi dao.load() nằm trong cflow(handle()):
    globalCounter += 1
```

Đây là **giả mã**. Điều kiện `cflow` quyết định **khi nào advice chạy**, nhưng `globalCounter` có thể vẫn nằm trên **một instance aspect dùng chung**, không có bộ đếm độc lập cho mỗi `handle()`.

Muốn “mỗi lần vào `handle()` có một bộ đếm riêng”, phải có **chính sách cấp instance hoặc trạng thái theo flow**. Chỉ thêm `cflow` vào pointcut không tự biến bộ đếm dùng chung thành bộ đếm riêng cho mỗi lần root.

### 4.2. Instance theo flow có điểm khởi tạo và điểm ngừng hiệu lực

Mô hình tổng quát:

```text
Root join point P entered
          |
          v
Create/activate Aspect instance F1
          |
          +--> join point A -> advice on F1
          |
          +--> join point B -> advice on F1
          |
          +--> join point C -> advice on F1
          |
Root P returns / exits with error
          |
          v
Deactivate association with F1
```

Tùy cơ chế, việc tạo object thật có thể xảy ra tại một mốc cụ thể của điểm vào và việc dỡ liên kết có thể được tối ưu theo cách khác; người học nên coi **lifetime logic** là điều kiện cốt lõi.

**Dỡ liên kết với flow** không đồng nghĩa JVM lập tức hủy object hoặc gọi một “destructor” tự động. Java dùng garbage collection; nếu có tham chiếu ở nơi khác, đối tượng vẫn có thể còn sống. Tài nguyên như file, khóa hoặc kết nối vẫn cần quy tắc đóng riêng.

## 5. Luồng điều khiển lồng nhau và đệ quy

Xét:

```text
handle(outer)                 -> Flow F1
  |
  +-- dao.load()              -> aspect F1
  |
  +-- handle(inner)           -> Flow F2, nếu P lại khớp
        |
        +-- dao.load()        -> aspect F2 trong phạm vi inner
  |
  +-- dao.save()              -> quay lại F1
```

Trong một chính sách **mỗi lần đi vào root tạo instance riêng**, root inner tạo F2 dù outer F1 vẫn đang hoạt động. Khi inner kết thúc, context hoạt động quay về F1; F2 không còn là instance đang hoạt động cho lời gọi tiếp theo của outer.

### 5.1. Vì sao cần cấu trúc stack?

Với các root lồng nhau, một biến toàn cục đơn `currentAspect` không đủ. Nếu inner gán F2 và kết thúc bằng cách đặt biến về `null`, nó sẽ làm mất liên kết tới F1 đang tiếp tục chạy.

Một **stack** của các activation cho phép:

1. Vào F1 → **push(F1)**.
2. Vào F2 → **push(F2)**.
3. Điểm bên trong sử dụng **top = F2**, nếu chính sách chọn instance đang hoạt động gần nhất.
4. Ra F2 → **pop(F2)**.
5. Tiếp tục F1 → **top = F1**.
6. Ra F1 → **pop(F1)**.

Đây là **mô hình triển khai để hiểu**, không phải bằng chứng mọi công cụ duy trì cấu trúc stack giống vậy trong bộ nhớ.

### 5.2. Đệ quy có làm tạo nhiều instance không?

Nếu một phương thức root gọi lại chính nó và điều kiện root khớp ở **mỗi lần thực thi**, chính sách per-entry có thể tạo nhiều activation lồng nhau. Nhưng nếu điểm vào được mô tả bằng điều kiện **bên dưới/loại trừ những lần gọi nội bộ** hoặc cơ chế có semantics riêng, số instance có thể khác.

Đừng trả lời “một instance cho mọi recursion” hoặc “mỗi depth chắc chắn một instance” khi chưa xác định **P là loại join point nào** và quy tắc instantiation của công nghệ cụ thể.

### 5.3. Phân biệt instance được chọn với tập instance còn sống

Khi F2 nằm trong F1, cả hai activation có thể **đang nằm trong call stack**. Việc advice tại một join point được áp lên F2, F1 hay cả hai phụ thuộc **ngữ nghĩa của aspect instantiation và pointcut**. Một mô hình Java tự xây có thể chọn chỉ **instance trên đỉnh stack**. Một công cụ AOP có quy tắc advice dispatch khác có thể cần cách diễn giải riêng.

Vì vậy ví dụ Java ở phần sau mô phỏng **một kiểu áp dụng xác định**, không tuyên bố nó tương đương tuyệt đối với mọi biến thể per-flow.

## 6. Vòng đời qua các đường return, throw và around advice

Một root có thể:

- Trả về bình thường;
- Trả về `null` bình thường;
- Hoàn tất phương thức `void`;
- Ném một ngoại lệ;
- Được around advice chặn trước khi thực thi đích;
- Được around advice thử lại nhiều lần.

Đối với một flow scope đã **thực sự được mở**, phải xác định thời điểm **dỡ liên kết** trên cả nhánh return và throw. Cấu trúc mô hình:

```text
onRootEnter:
    flowInstance = activateFlow()
    try:
        return continueInsideRoot()
    finally:
        deactivateFlow(flowInstance)
```

Đây là **giả mã**, không phải API chuẩn của AOP. Đặt cleanup trong `finally` giải thích cách giữ cân bằng số lượt vào/ra trên những đường điều khiển thông thường của Java.

### 6.1. Nếu lỗi xảy ra trước khi khởi tạo?

Nếu quá trình tạo instance, bind context hoặc kiểm tra quyền ném lỗi **trước khi flow được xác lập**, chưa có instance hợp lệ để dỡ. Nếu một phần tài nguyên đã được cấp trước lỗi đó, phải có cơ chế rollback/cleanup riêng cho phần đã cấp.

Không nên đưa `pop()` vô điều kiện trong mọi nhánh mà không biết đã `push()` thành công hay chưa; thao tác sai có thể xóa instance của flow cha.

### 6.2. Nếu cleanup tự ném lỗi?

Một `finally` hoặc advice dọn dẹp ném lỗi mới có thể che ngoại lệ gốc từ nghiệp vụ. Với context thuần quan sát, mục tiêu nên là **giữ nguyên kết quả/ngoại lệ**, ghi nhận lỗi thu dọn theo chính sách phù hợp và tránh để hệ theo dõi phá hợp đồng chính.

Không suy rằng instance per-flow tự quản lý đóng kết nối, kết thúc transaction hoặc rollback. **Lifetime của aspect instance** và **vòng đời các tài nguyên mà aspect sở hữu** liên quan nhưng khác nhau.

### 6.3. Around advice làm thay đổi ranh giới quan sát

Một around advice có thể không gọi `proceed`, hoặc gọi nhiều lần nếu API cho phép. Khi đó:

- Nếu root join point **không được đi vào** do lời gọi bị chặn ở một lớp ngoài, flow tương ứng có thể không hình thành.
- Nếu mỗi lần `proceed` thật sự đi vào lại root, có thể hình thành nhiều flow/activation, tùy loại join point và thứ tự aspect.
- Nếu root được mở **bên ngoài** khối retry, những lần thử bên trong có thể cùng một flow.
- Nếu root nằm **bên trong** retry, các attempt có thể được nhìn như những lần vào riêng.

Cần quan sát thứ tự thực sự của chuỗi aspect trước khi quyết định per-flow instance thuộc **logical request** hay **mỗi attempt**.

## 7. So sánh với các kiểu lifetime khác

| Chiến lược | Dữ liệu được gắn với | Mô hình sử dụng thường gặp |
| --- | --- | --- |
| **Shared/singleton aspect** | Một instance dùng chung theo phạm vi mà công cụ định nghĩa | Chính sách thống kê không giữ context từng request |
| **Per-target** | Một instance cho mỗi target phù hợp | Bộ đếm/trạng thái gắn một đối tượng nghiệp vụ |
| **Per-this** | Một instance theo đối tượng `this` trong ngữ nghĩa join point | Theo dõi trạng thái đối tượng phía ngữ cảnh |
| **Per-type** | Một instance theo loại, khi mô hình hỗ trợ | Thống kê theo class hoặc nhóm loại |
| **Per-control-flow** | Một instance cho mỗi activation của dynamic flow đã chọn | Bộ đếm tạm thời của một lần xử lý |
| **Manual scope** | Một token/context do ứng dụng tự quản | Khi công nghệ AOP không có per-flow instantiation |

Một per-flow aspect không đồng nghĩa “tất cả dữ liệu của một yêu cầu web”. Yêu cầu web có thể chạy qua nhiều thread/tác vụ hoặc nhiều root; một root có thể do lời gọi batch, scheduler hay chương trình desktop tạo ra.

**Chọn scope theo nhu cầu dữ liệu**, không theo tên quen thuộc của framework. Nếu chỉ cần biến đếm cục bộ trong một phương thức, không nhất thiết phải thêm AOP state management.

## 8. Cú pháp minh họa thuộc AspectJ: `percflow` và `cflow`

Một ví dụ **chỉ để đọc cú pháp của ngôn ngữ AspectJ**:

```java
package demo;

public aspect FlowCounter percflow(
        execution(* demo.RequestHandler.handle(..))) {

    private int visits = 0;

    before(): execution(* demo.DataStore.load(..)) {
        visits++;
        System.out.println("loads in this flow=" + visits);
    }
}
```

Các tên lớp trong `demo` là ví dụ; đây là **mã aspect riêng, không biên dịch bằng `javac` thường**, và cần bộ compiler/weaver hiểu cú pháp AspectJ cùng những lớp đích phù hợp.

Đọc ý nghĩa ở mức kiến trúc:

1. `execution(* demo.RequestHandler.handle(..))` xác định **root** dùng cho chiến lược instantiation.
2. `percflow(...)` yêu cầu một **aspect instance theo flow** mà công nghệ đó định nghĩa.
3. Field `visits` thuộc **instance aspect**, không phải field static toàn chương trình.
4. Advice ở `DataStore.load(..)` theo dõi các điểm khớp trong phạm vi áp dụng của instance ấy.
5. Khi không có flow phù hợp, không tự có instance đang hoạt động để thực hiện advice có scope này.

Không nên suy từ ví dụ rằng mọi lần gọi `load` đều được đếm: chỉ những điểm được weave và thuộc phạm vi đã định mới có khả năng kích hoạt advice.

### 8.1. Chỉ dùng `cflow` thì khác gì?

Một aspect dùng chung có thể có điều kiện:

```text
pointcut observeLoad:
    execution(DataStore.load(..))
    AND cflow(execution(RequestHandler.handle(..)))
```

Đây là **giả mã**. Nó mô tả việc lọc `load` theo call flow, nhưng **không tự quyết định** instance aspect riêng cho từng `handle`. Nếu mục tiêu là một bộ đếm riêng cho từng lượt root, phải dùng **instantiation policy** tương ứng hoặc thiết kế scope thủ công.

### 8.2. `cflowbelow` khác `cflow` ở biên root

Khi cần tính cả chính điểm root hay chỉ các điểm dưới root, điều kiện chọn phải thể hiện rõ. Ví dụ “đếm mọi `handle` thuộc flow của `handle`” có thể gặp **khớp chính root**, trong khi “chỉ đếm những điểm bên trong handle” cần cách chọn bên dưới thích hợp.

Không tráo `cflow` và `cflowbelow` chỉ vì chúng giống tên. Ngữ nghĩa cụ thể phụ thuộc loại join point, việc lồng nhau và quy tắc của công nghệ.

### 8.3. Đây không phải quy tắc của mọi AOP

Một cơ chế proxy chỉ quanh phương thức giao diện có thể không thể tạo **instance aspect theo mọi điểm call trong stack** hoặc can thiệp field/constructor tùy ý. Khi không có per-flow policy, có thể tự tạo **scope thủ công** hoặc dùng một mô hình tracing rõ ràng — nhưng không gọi đó là native `percflow` của công cụ.

## 9. Ví dụ Java 17 chạy được: mô phỏng vòng đời theo flow

Để thấy rõ **mỗi lượt vào root → instance riêng → join point bên trong → cleanup**, ví dụ tự xây một bộ scope qua `ThreadLocal<Deque<FlowAspect>>`. Nó hoạt động với JDK thuần, không phụ thuộc compiler/weaver AOP.

**Phạm vi minh họa:** `inFlow` là một **ranh giới root chủ động**, `observe` là mô phỏng lời gọi advice tại các điểm chọn, và `FlowAspect` là một instance có trạng thái theo flow. Đây **không phải** hệ AOP tự phát hiện join point; nó mô tả một phương án quản lý activation và khôi phục parent trong mã đồng bộ.

Tạo tệp `ControlFlowAspectDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ControlFlowAspectDemo {
    static final class FlowAspect {
        private final String label;
        private int events;

        FlowAspect(String label) {
            this.label = label;
            System.out.println("CREATE " + label);
        }

        void observe(String point) {
            events++;
            System.out.println("[" + label + "] " + point + " #" + events);
        }
    }

    private static final ThreadLocal<Deque<FlowAspect>> ACTIVE =
            new ThreadLocal<>();

    static FlowAspect current() {
        Deque<FlowAspect> stack = ACTIVE.get();
        return stack == null ? null : stack.peek();
    }

    static void business(String point) {
        FlowAspect aspect = current();
        if (aspect == null) {
            System.out.println("NO_SCOPE " + point);
        } else {
            aspect.observe("business:" + point);
        }
    }

    static <T> T inFlow(String label, Callable<T> operation)
            throws Exception {
        Deque<FlowAspect> stack = ACTIVE.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            ACTIVE.set(stack);
        }

        FlowAspect aspect = new FlowAspect(label);
        stack.push(aspect);
        try {
            aspect.observe("entry");
            return operation.call();
        } finally {
            aspect.observe("exit");
            FlowAspect removed = stack.pop();
            if (removed != aspect) {
                throw new IllegalStateException("scope stack corrupted");
            }
            if (stack.isEmpty()) {
                ACTIVE.remove();
            }
            System.out.println("END " + label);
        }
    }

    public static void main(String[] args) throws Exception {
        business("outside");

        inFlow("outer", () -> {
            business("A");
            inFlow("inner", () -> {
                business("B");
                return null;
            });
            business("C");
            return null;
        });

        try {
            inFlow("failed", () -> {
                business("D");
                throw new IllegalArgumentException("invalid");
            });
        } catch (IllegalArgumentException error) {
            System.out.println("CAUGHT " + error.getMessage());
        }
        business("after");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            inFlow("parent", () -> {
                business("before-task");
                Future<?> future = executor.submit(
                        () -> business("worker"));
                future.get();
                business("after-task");
                return null;
            });
        } finally {
            executor.shutdown();
        }
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac -encoding UTF-8 ControlFlowAspectDemo.java
java ControlFlowAspectDemo
```

Đầu ra dự kiến chính xác:

```text
NO_SCOPE outside
CREATE outer
[outer] entry #1
[outer] business:A #2
CREATE inner
[inner] entry #1
[inner] business:B #2
[inner] exit #3
END inner
[outer] business:C #3
[outer] exit #4
END outer
CREATE failed
[failed] entry #1
[failed] business:D #2
[failed] exit #3
END failed
CAUGHT invalid
NO_SCOPE after
CREATE parent
[parent] entry #1
[parent] business:before-task #2
NO_SCOPE worker
[parent] business:after-task #3
[parent] exit #4
END parent
```

### 9.1. Vì sao `outer` và `inner` có bộ đếm riêng?

Mỗi lần gọi `inFlow` tạo một **FlowAspect mới**. Sau `CREATE outer`, `outer` được `push` vào stack. Khi `inFlow("inner")` chạy, instance `inner` được push lên trên `outer`. `current()` lấy phần tử trên đỉnh, nên điểm `business:B` ghi vào bộ đếm inner.

Khi inner kết thúc, `finally` pop instance inner. Lời gọi `business:C` sau đó lại dùng outer, tiếp tục từ `#2` lên `#3`. Đây là cơ chế **khôi phục activation cha** thay vì làm mất context cha.

### 9.2. Vì sao nhánh lỗi vẫn kết thúc flow?

Ở `inFlow("failed")`, `operation.call()` ném `IllegalArgumentException`. Khối `finally` vẫn ghi `exit`, pop instance và in `END failed`, rồi ngoại lệ tiếp tục được truyền ra ngoài tới `catch` trong `main`.

`NO_SCOPE after` chứng minh không còn instance đang hoạt động trên thread chính sau lỗi. Nếu quên pop khi throw, một lần gọi độc lập có thể vô tình nhìn thấy instance cũ.

### 9.3. Vì sao worker thread in `NO_SCOPE`?

`ExecutorService` chạy lambda trên thread khác. `ThreadLocal` của parent thread **không tự truyền** sang thread trong executor. Bởi vậy `business("worker")` không thấy `FlowAspect parent` và in `NO_SCOPE worker`. `future.get()` bảo đảm dòng worker xuất hiện trước `after-task` trong output đã cho.

Đây là **mô phỏng bằng ThreadLocal**, không phải phát biểu mọi hình thức dynamic cflow qua async đều giống hệt. Một công nghệ AOP hoặc tracing có propagation hỗ trợ tác vụ bất đồng bộ có thể đưa ra hợp đồng riêng. Không thể mặc định một định nghĩa per-flow sẽ sống qua ranh giới thread chỉ vì thao tác đã được khởi chạy bên trong root.

### 9.4. Vì sao dùng `ACTIVE.remove()`?

Khi scope cuối của thread kết thúc, ví dụ gọi `ACTIVE.remove()` để bỏ liên kết ThreadLocal tới stack. Đây là một thực hành quan trọng với thread pool sống lâu: giữ những đối tượng per-request trong ThreadLocal sau khi xử lý xong có thể giữ tham chiếu thừa hoặc làm lẫn dữ liệu vào yêu cầu tiếp theo.

Nếu outer còn hoạt động khi inner kết thúc, không được remove toàn bộ ThreadLocal; chỉ pop inner để giữ parent.

### 9.5. Giới hạn của ví dụ

- `inFlow` được gọi **tường minh**, không được weaver tự chèn.
- `business()` đóng vai trò một join point quan sát thủ công.
- `FlowAspect` chỉ giữ một bộ đếm nhỏ; không mô phỏng tất cả precedence hoặc khả năng dispatch advice của mọi ngôn ngữ AOP.
- Chương trình **không an toàn để chia sẻ một `FlowAspect` mutable giữa nhiều thread** nếu không thêm quy tắc đồng thời; ví dụ cố ý không truyền nó sang worker.
- `Callable<T>` được dùng để cho phép nhánh return hoặc throw có thể kiểm chứng.
- `ThreadLocal` không tự cung cấp tracing xuyên tiến trình và không tự đồng nhất với request scope.
- `ExecutorService` được dùng trong một chương trình hữu hạn; `shutdown()` giúp kết thúc executor khi thao tác đã hoàn tất.
- Ví dụ dùng các nhãn không chứa dữ liệu nhạy cảm; thực tế phải kiểm soát log và thời hạn lưu thông tin theo chính sách.

## 10. Lồng flow với proxy và thứ tự interceptor

Nếu hệ AOP dựa vào interceptor quanh phương thức root, vị trí của interceptor tạo flow quyết định phạm vi nó bao quanh.

Giả sử:

```text
FlowScope(Retry(Target))
```

FlowScope được mở **một lần** cho một logical request bên ngoài Retry. Nếu Retry gọi `proceed` hai lần, cả hai attempt có thể dùng **cùng một scope**, miễn đường gọi tiếp tục ở phần bên trong FlowScope và mô hình instantiation hoạt động như vậy.

Ngược lại:

```text
Retry(FlowScope(Target))
```

Mỗi attempt đi vào FlowScope có thể tạo **một instance riêng**, nếu continuation thực sự chạy lại phần đuôi. Điều này ảnh hưởng bộ đếm và tài nguyên.

### 10.1. Không có precedence đúng cho mọi trường hợp

Đặt authorization bên ngoài cache có thể giúp kiểm tra quyền trước cache hit; đặt scope đo thời gian bên ngoài retry có thể đo cả chuỗi; đặt một scope transaction trong retry có thể tạo transaction mới theo mỗi attempt. Nhưng thứ tự đúng phụ thuộc **hợp đồng nghiệp vụ** và khả năng thực tế của cơ chế.

Đừng quyết định lifespan aspect chỉ vì “thứ tự khai báo file A trước file B”. Phải xác minh precedence/registration và test đúng đường.

### 10.2. Bắt đầu flow ở method call hay method execution?

Nếu root được chọn tại **method call** của người gọi, phạm vi có thể gắn với vị trí phát sinh lời gọi. Nếu root ở **method execution**, phạm vi gắn với thân phương thức được chạy. Một proxy phương thức thường chỉ thấy lời gọi đi qua nó, nên khác bộ weaver có thể chọn method-call join point trong bytecode.

Cần nêu rõ root là loại join point nào. Tên phương thức giống nhau chưa chứng minh root activation giống nhau.

## 11. Phạm vi thread, bất đồng bộ và propagation

Phạm vi dynamic call stack đồng bộ thường có ranh giới dễ xác định: bước vào root, gọi các hàm con, rồi rời root. Công việc bất đồng bộ làm mô hình phức tạp hơn:

```text
Thread A:  handle() -> schedule(task) -> return
Thread B:                               task.run()
```

Tại thời điểm `task.run()` chạy, `handle()` trên thread A có thể đã kết thúc. Không có quan hệ **ngăn xếp lời gọi đồng bộ** từ `handle()` trên A sang B.

### 11.1. Truyền context thủ công không phải tự động có per-flow semantics

Một chương trình có thể **capture** trace ID hoặc token context rồi chuyển sang tác vụ async. Tuy nhiên:

- Nếu capture **toàn bộ instance aspect mutable**, hai thread có thể cập nhật cùng bộ đếm gây race condition.
- Nếu flow cha kết thúc rồi tài nguyên bị đóng, worker dùng instance cũ có thể truy cập tài nguyên đã đóng.
- Nếu worker là thread pool, cần khôi phục context trước đó và dọn đúng lúc để tránh lẫn yêu cầu.
- Nếu task bị hủy, thất bại hoặc chạy muộn, chính sách dọn dẹp không thể chỉ dựa vào `finally` của hàm lập lịch.
- Nếu propagation chỉ truyền **mã tương quan bất biến**, có thể cần một scope aspect riêng ở thread worker, với quan hệ cha/con được mô tả tường minh.

Một công cụ hỗ trợ async-aware control-flow có thể có ngữ nghĩa khác. Cần xem hợp đồng thay vì suy từ cách AOP đồng bộ hoạt động.

### 11.2. Không tự đồng nhất cflow với HTTP request

Một HTTP request có thể gồm nhiều callback bất đồng bộ và thread. Một thread có thể phục vụ nhiều request lần lượt. Một cuộc gọi root có thể xảy ra từ command-line, batch job hoặc test.

Nếu yêu cầu thực sự là “mỗi logical request có một state”, nên dùng **request/context propagation** phù hợp; per-control-flow chỉ giải quyết đúng khi ranh giới của nó trùng yêu cầu và công nghệ có khả năng duy trì scope cần thiết.

### 11.3. Virtual thread và cấu trúc tác vụ

Java có thể dùng các mô hình thực thi khác nhau, gồm thread platform và virtual thread ở các phiên bản phù hợp. Việc một context được cất theo thread không biến nó thành context theo logical request, nhất là khi tác vụ phân nhánh hoặc chuyển sang một thread khác.

Dù sử dụng loại thread nào, hãy xác định **ai sở hữu instance, ai truyền context và khi nào dọn**. Không dựa vào số lượng thread hoặc cơ chế pooling để suy ra số instance per-flow.

## 12. Tài nguyên, vòng đời object và giới hạn “kết thúc”

### 12.1. Kết thúc activation không phải thu hồi bộ nhớ tức thì

Khi root thoát, instance không còn là instance active trong luồng điều khiển. Nếu không còn tham chiếu nào khác, object có thể được thu gom rác về sau. Nhưng **garbage collection không có nghĩa chạy đúng lúc để giải phóng file/khóa/kết nối**.

Nếu aspect sở hữu tài nguyên, phải có cơ chế `close`/`finally` hoặc hợp đồng quản lý lifecycle tương ứng.

### 12.2. Scope theo flow chưa chắc là transaction

Bắt đầu/kết thúc instance theo flow không tự mở hoặc commit transaction. Nếu aspect quản lý giao dịch, còn phải xác định rollback, lỗi sau commit, retry, tài nguyên ngoài cơ sở dữ liệu và các hành vi async.

Ném ngoại lệ từ một join point bên trong flow cũng không chứng minh **mọi hiệu ứng trước đó đã rollback**. Không ghi log “đã hoàn tác” chỉ vì flow scope kết thúc.

### 12.3. Những trường hợp không bảo đảm cleanup

Trong Java, `finally` có thể chạy trên return hoặc throw thông thường sau khi vào phạm vi, nhưng không bảo đảm chạy nếu JVM bị kết thúc cưỡng bức, mất điện, hoặc chương trình không bao giờ rời vùng ấy.

Với tác vụ dùng `Future`, lời gọi tạo future có thể return trong khi công việc còn chạy. Dọn instance theo phương thức tạo future **không tương đương** dọn sau completion của future.

## 13. Hiệu năng và chi phí của theo dõi control flow

Kiểm tra `cflow` ở các join point được gọi rất nhiều lần có thể phát sinh chi phí duy trì trạng thái hoặc kiểm tra bối cảnh. Một chính sách per-flow còn phải tạo, gắn và dỡ instance ở những lượt root tương ứng.

Các khoản chi phí có thể là:

- Đánh dấu điểm vào/ra của flow;
- Theo dõi depth hoặc stack activation;
- Truy cập state theo thread/context;
- Khởi tạo object và quản lý lifetime;
- Kiểm tra điều kiện động tại join point;
- Thực thi advice body: logging, network call, serialization, ghi metric.

Không thể kết luận `percflow` luôn chậm hơn một mô hình khác mà không đo công cụ và workload cụ thể. Hành vi của advice, tần suất join point và khả năng tối ưu của weaver có thể chi phối lớn hơn bản thân việc theo dõi flow.

### 13.1. Tránh tạo instance không cần thiết

Nếu chỉ cần đo tổng thời gian của một phương thức, biến thời gian cục bộ trong around advice có thể đã đủ. Per-flow aspect có ích hơn khi **nhiều điểm nằm trong cùng luồng cần chia sẻ một state có vòng đời riêng**.

Nếu chỉ dùng `cflow` như điều kiện lọc và không cần state per-flow, đừng tạo instance mới chỉ để đạt cùng hiệu ứng lọc.

### 13.2. Đừng log mọi điểm chỉ để chứng minh lifetime

Một pointcut quá rộng có thể làm tăng overhead và rò thông tin. Nên chọn **join point thật sự cần** cùng dữ kiện tối thiểu, và dùng test riêng để xác minh scope, thay vì ghi toàn bộ arguments/results của mọi phương thức.

## 14. Ma trận kiểm thử lifecycle theo flow

| Kịch bản | Mong đợi |
| --- | --- |
| Join point ngoài root | Không có per-flow instance hoạt động cho chính sách đó |
| Lần vào root thứ nhất | Tạo hoặc kích hoạt instance F1 |
| Hai join point cùng root | Có thể chia sẻ F1 nếu đúng ngữ nghĩa công cụ |
| Lần vào root thứ hai sau khi F1 kết thúc | Instance mới F2, không giữ bộ đếm F1 |
| Root lồng nhau | Quy tắc instance riêng/khôi phục outer rõ ràng |
| Đệ quy | Số lần activation đúng theo điều kiện root |
| Root trả về bình thường | Dỡ instance khỏi scope |
| Root ném ngoại lệ | Dỡ instance và giữ ngoại lệ gốc |
| Root bị chặn bởi advice ngoài | Xác định flow có thực sự được vào không |
| Retry bao ngoài hoặc bên trong flow scope | Đếm instance/attempt đúng theo thứ tự |
| Hai thread đồng thời | Không lẫn dữ liệu scope |
| Thread pool tái sử dụng | Không để lại context từ yêu cầu trước |
| Task async được lịch từ flow | Không tự suy có cùng instance trên thread khác |
| Kết quả `null` hoặc `void` | Không nhầm normal return với exception |
| Pointcut `cflow` trên singleton | Không tự coi là per-flow instance |
| Lời gọi bypass proxy/weaving | Phát hiện điểm không được can thiệp |
| Lỗi cleanup | Không âm thầm che lỗi nghiệp vụ trái hợp đồng |

### 14.1. Kiểm thử danh tính instance, không chỉ số log

Một log “dao.load executed” có thể xuất hiện đúng khi dùng `cflow` nhưng chưa chứng minh đã có instance riêng cho từng root. Để kiểm tra **per-flow lifecycle**, cần xác minh:

1. Trong cùng flow, những điểm yêu cầu chia sẻ state thấy cùng instance/bộ đếm.
2. Giữa hai flow tách biệt, state được tách riêng.
3. Với flow lồng nhau, quy tắc chọn instance phù hợp được thực thi.
4. Sau return/throw, instance cũ không “rò” vào lời gọi tiếp theo.
5. Khi có concurrency, trạng thái không bị ghi đè bởi flow khác.

### 14.2. Chẩn đoán thiếu hoặc thừa instance

Nếu mỗi lần `dao.load()` lại có instance mới dù nằm trong một `handle`, có thể đang dùng chính sách **per-join-point** tự tạo thay vì per-root. Nếu mọi `handle` cùng ghi vào một bộ đếm, có thể aspect là singleton hoặc dữ liệu nằm trong field static.

Nếu scope chỉ hoạt động ở một số đường, kiểm tra root pointcut, loại join point, điều kiện động, phạm vi weaving/proxy và đường async. **Sửa pointcut và sửa instantiation policy là hai loại sửa khác nhau.**

## 15. Lỗi phổ biến và cách tránh

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Coi `cflow` là lệnh tạo aspect instance | Vẫn có state chung, bộ đếm lẫn nhau | Phân biệt filtering với instantiation |
| Coi per-flow đồng nghĩa per-thread | Dữ liệu từ nhiều root trong cùng thread bị lẫn | Gắn với lần vào/ra root, hỗ trợ nesting |
| Dùng một biến `currentAspect` global | Flow đồng thời và nested ghi đè | Dùng context/stack theo đúng phạm vi |
| Không pop khi ném lỗi | Thread sau dùng nhầm instance cũ | Cleanup trong finally sau activation thành công |
| Pop sai khi inner kết thúc | Mất context của outer | Kiểm tra thứ tự LIFO/ownership |
| Giữ instance mutable qua async không đồng bộ | Race condition hoặc dùng tài nguyên đã đóng | Truyền dữ liệu phù hợp và quản lý lifecycle riêng |
| Tin ThreadLocal tự truyền sang executor | Không có context ở worker | Propagation tường minh nếu hợp đồng yêu cầu |
| Dùng `cflowbelow` khi cần tính cả root | Bỏ sót điểm gốc | Xác nhận điều kiện biên của pointcut |
| Không tính recursion/nested root | Số instance sai | Test activation theo từng depth |
| Đóng tài nguyên ở cuối hàm tạo future | Công việc async còn sử dụng tài nguyên | Dọn theo completion thật |
| Coi object GC như dọn tài nguyên đúng lúc | Rò file/kết nối/khóa | Close/finally theo ownership |
| Cho rằng proxy chặn mọi join point | Không thấy lời gọi trực tiếp hoặc field access | Kiểm chứng model/proxy/weaving |
| Log mọi đối số vì cần cflow debug | Chi phí cao, lộ dữ liệu | Chỉ thu trường cần thiết, áp quyền truy cập |
| Dùng per-flow cho dữ liệu toàn cục không cần tách | Tăng chi phí và phức tạp | Chọn lifetime đơn giản đủ đáp ứng |
| Không xét thứ tự Retry và FlowScope | State per-request/per-attempt bị nhầm | Test precedence và số lần proceed |

## 16. Bài tập vận dụng có đáp án

### Bài 1 — Lọc theo cflow hay tạo state riêng?

Một aspect dùng chung có pointcut chọn `dao.load` trong `cflow(handle)` và field `int count`. Hai lần gọi `handle` có chắc sử dụng hai bộ đếm riêng không?

**Đáp án:** Không. `cflow` chỉ quyết định nơi áp dụng điều kiện. Nếu field nằm trong một singleton aspect, hai lần handle có thể cùng cập nhật một field. Muốn bộ đếm độc lập, cần per-flow instantiation hoặc state theo flow được quản lý rõ.

### Bài 2 — Nested root

Luồng F1 gọi bên trong một root mới tạo F2. F2 kết thúc; F1 còn chạy. Cách quản lý context đúng là gì?

**Đáp án:** Nếu mô hình dùng ngăn xếp, push F2 khi vào inner, pop F2 khi thoát rồi khôi phục F1 trên đỉnh. Không đặt current về null khi F1 vẫn đang hoạt động.

### Bài 3 — Ngoại lệ

Root mở flow F1 rồi target ném `IllegalArgumentException`. Chỉ dọn flow ở after-returning có đúng không?

**Đáp án:** Không. Nhánh exception không chạy after-returning. Scope phải được dỡ trên các đường thoát tương ứng, thường dùng hành vi finally/after-always hoặc cơ chế tương đương nếu công nghệ hỗ trợ; giữ ngoại lệ gốc.

### Bài 4 — Thread pool

Root chạy trên thread A và submit một task sang executor thread B. Instance per-flow có chắc hiện diện trên B không?

**Đáp án:** Không thể mặc định. Dynamic call stack đồng bộ của A không tự trở thành call stack của B. Cần cơ chế propagation hỗ trợ hoặc một scope mới theo nhiệm vụ và phải quản lý cleanup/concurrency.

### Bài 5 — Retry

Với `FlowScope(Retry(Target))` và `Retry(FlowScope(Target))`, mỗi cấu trúc có thể tạo bao nhiêu instance khi có hai attempts?

**Đáp án:** Trong mô hình continuation cho phép chạy lại phần đuôi, cấu trúc đầu thường mở một flow ngoài Retry; cấu trúc sau có thể mở một flow cho mỗi attempt. Kết quả chính xác phải kiểm tra loại join point và ngữ nghĩa công cụ.

### Bài 6 — Lấy state theo đối tượng hay flow?

Cùng một `OrderService` instance xử lý hai lời gọi `handle(A)` và `handle(B)` tách biệt. Nếu muốn state không dùng chung giữa A và B, nên mặc định per-target hay per-flow?

**Đáp án:** Per-target có thể chia sẻ state vì target giống nhau. Per-flow phù hợp hơn khi A và B là hai root activation độc lập trong mô hình được hỗ trợ. Nếu yêu cầu là logical request xuyên async, cần thiết kế propagation vượt ra ngoài call stack đơn thuần.

### Bài 7 — Gọi bên ngoài root

`dao.load()` được gọi trực tiếp từ `main` mà không qua `handle()`. Advice được ràng buộc theo per-flow của `handle` có chắc chạy không?

**Đáp án:** Không. Nếu không có một activation `handle` phù hợp, không có instance per-flow đang hoạt động theo chính sách đó. Còn một aspect singleton có pointcut khác có thể vẫn quan sát lời gọi; phải phân biệt định nghĩa và scope.

### Bài 8 — Thời điểm object biến mất

Khi flow F1 kết thúc, JVM có phải gọi destructor và hủy object aspect ngay lập tức không?

**Đáp án:** Không. Kết thúc activation là dỡ liên kết logic khỏi flow; garbage collection xảy ra theo chính sách JVM nếu object không còn reachable. Tài nguyên ngoài bộ nhớ phải được đóng theo lifecycle riêng.

### Bài 9 — Cflowbelow

Bạn chỉ muốn áp advice cho các điểm **bên trong** root chứ không áp lên chính root. Điều kiện `cflow` và `cflowbelow` có thể khác ra sao?

**Đáp án:** Trong những mô hình có hai điều kiện ấy, `cflowbelow` thường loại chính điểm root và chọn những điểm ở bên dưới; `cflow` thường bao gồm root. Cần xem ngữ nghĩa và loại join point của công nghệ cụ thể.

### Bài 10 — Chứng minh per-flow

Một test chỉ kiểm tra “mỗi lần load có log” có đủ chứng minh per-flow instance đúng không?

**Đáp án:** Không. Cần kiểm tra nhận dạng/state giữa các điểm trong cùng root, sự tách biệt giữa hai root, nested/recursion, return/throw cleanup và hành vi khi thread được tái sử dụng.

## 17. Ghi nhớ

- **Dynamic control flow** là quan hệ lời gọi đang thực sự diễn ra, không phải chỉ vị trí package hoặc một đối tượng cụ thể.
- **`cflow`/`cflowbelow`** là điều kiện chọn điểm theo luồng; **`percflow`** nếu được hỗ trợ là **chính sách tạo instance aspect gắn với mỗi flow**.
- Per-flow instance có **lifetime logic theo activation**; nó không đồng nghĩa singleton, per-target, per-thread vĩnh viễn hay request scope xuyên async.
- Lời gọi lồng nhau hoặc đệ quy đòi cơ chế **khôi phục activation cha**, thường dễ hình dung qua stack.
- Khi root thoát qua **return hoặc throw**, liên kết scope phải được dỡ đúng đường; việc cleanup và thu hồi tài nguyên ngoài bộ nhớ là trách nhiệm riêng.
- **Thread/async boundary** có thể ngắt call flow đồng bộ; context không tự truyền, và chia sẻ instance mutable giữa thread cần an toàn đồng thời.
- **Thứ tự aspect/proxy và số lần proceed** quyết định một scope có bao toàn bộ logical request hay được mở cho từng attempt.
- Để kiểm chứng per-flow lifecycle, phải đo **instance/state theo từng root**, không chỉ việc log advice có xuất hiện hay không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-lifecycle-004">Thời điểm tạo, khởi tạo và giải phóng aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Thời điểm tạo, khởi tạo và giải phóng aspect

## 1. Vì sao cần hiểu vòng đời của aspect?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, một **aspect** tổ chức các mối quan tâm cắt ngang như ghi audit, đo thời gian, kiểm soát truy cập và tracing. **Pointcut** chọn những *join point* có thể can thiệp; **advice** mô tả hành vi được thực thi tại đó. Tuy nhiên, khái niệm “một aspect” không tự nói rõ **đối tượng chứa trạng thái advice tồn tại trong bao lâu, được tạo lúc nào và được giải phóng bởi ai**.

Đây là ba việc khác nhau:

1. **Tạo (creation/instantiation):** có một instance của thành phần aspect, interceptor hoặc state holder; các field nhận giá trị mặc định và constructor hoặc cơ chế tương đương có thể chạy.
2. **Khởi tạo (initialization):** chuẩn bị cấu hình, các phụ thuộc và tài nguyên cần dùng cho advice, ví dụ bộ đếm, sink metrics hoặc trạng thái theo target. Việc này có thể nằm trong constructor hoặc một bước riêng.
3. **Giải phóng (disposal/cleanup):** ngừng tiếp nhận thao tác mới khi thích hợp, đóng tài nguyên, flush có kiểm soát, loại đăng ký interceptor và loại bỏ tham chiếu sở hữu. Đây **không đồng nghĩa chỉ chờ garbage collection (GC)**.

**Không có một vòng đời duy nhất áp dụng cho tất cả công cụ AOP.** Một engine weaving có thể hỗ trợ một instance dùng chung, instance gắn với từng đối tượng, hoặc một phạm vi điều khiển động; một proxy có thể được tạo mỗi khi factory chạy; một advice không có trạng thái có thể không cần instance đặc biệt. Phải đọc mô hình instantiation và ownership thực tế của công nghệ đang sử dụng.

### Tình huống mở đầu

Một hệ thống đo thời gian gọi `paymentService.pay()` nhiều lần. Nếu aspect lưu `startedAt` trong field dùng chung, hai request đồng thời có thể ghi đè mốc nhau. Nếu tạo một metrics client cho **mỗi invocation** nhưng không đóng nó, hệ thống rò tài nguyên. Nếu đóng shared metrics client ngay khi một target được dừng, các target khác có thể bị lỗi. Cả ba đều là lỗi **chọn sai vòng đời và chủ sở hữu** hơn là lỗi cú pháp pointcut.

Bài này tập trung vào **vòng đời aspect AOP nói chung**: thời điểm instance được tạo, initialization, trạng thái, phạm vi, threading, dọn dẹp, proxy/weaving, classloader, bất đồng bộ và kiểm thử. Ví dụ Java 17 chạy độc lập là **mô phỏng các phạm vi quản lý vòng đời bằng JDK proxy**, không tự nhận là bộ bytecode weaver.

## 2. Năm mốc thời gian dễ bị nhập làm một

Một luồng AOP có thể trải qua những mốc:

~~~text
T0: Định nghĩa aspect, pointcut và advice trong source/config
 |
T1: Kết hợp AOP: build-time weaving / load-time weaving / tạo proxy
 |
T2: Tạo một aspect instance hoặc state holder, nếu mô hình yêu cầu
 |
T3: Khởi tạo tài nguyên, hoàn tất registration và sẵn sàng nhận call
 |
T4: Join point xuất hiện -> advice thực thi (nhiều lần)
 |
T5: Ngừng sử dụng phạm vi, cleanup/disposal; GC có thể diễn ra muộn hơn
~~~

**Thời điểm weaving không bắt buộc là thời điểm tạo aspect instance.** Compile-time weaving có thể hoàn tất trước khi chương trình chạy; mã đã dệt chỉ tạo instance hay gọi advice khi một class được khởi tạo hoặc một join point xuất hiện. Load-time weaving có thể biến đổi class trước lúc chạy method; việc biến đổi bytecode cũng không tự có nghĩa “constructor của aspect chạy đúng một lần lúc class load”.

Ở chiều khác, một proxy có thể được tạo lúc khởi động nhưng chỉ **gọi advice** khi request đến. Một proxy được factory tạo theo yêu cầu có thể được tạo muộn hơn. Một aspect **stateless** có thể dùng hàm static hoặc object shared mà không có tài nguyên cần đóng.

Vì vậy, phải tách tối thiểu bốn câu hỏi:

- **Installation:** pointcut và advice được lắp/biên dịch/dệt vào đường chạy vào lúc nào?
- **Instantiation:** khi nào và bao nhiêu instance/state holder được tạo?
- **Invocation:** một advice thực sự chạy bao nhiêu lần, trong phạm vi nào?
- **Disposal:** sự kiện nào kết thúc quyền sở hữu và ai chịu trách nhiệm cleanup?

Không trả lời những câu này bằng suy đoán dựa trên từ “Aspect” hoặc từ “singleton”. **Scope phụ thuộc engine và cách cấu hình**, không phải một quy tắc phổ quát của AOP.

## 3. Vòng đời cơ bản và ý nghĩa các giai đoạn

| Giai đoạn | Hoạt động có thể xảy ra | Rủi ro nếu hiểu sai |
| --- | --- | --- |
| **Definition** | Viết pointcut, advice, luật thứ tự | Tưởng định nghĩa đã tự chạy advice |
| **Installation/weaving** | Biến đổi bytecode, tạo proxy, đăng ký interceptor | Pointcut không được đưa vào đúng đường gọi |
| **Creation** | Cấp phát instance, thiết lập field mặc định | Tạo nhiều instance ngoài dự kiến |
| **Initialization** | Constructor, inject dependency, mở tài nguyên có giới hạn | Object bị đưa ra dùng trước khi sẵn sàng |
| **Activation** | Advice chạy tại join point | Nhầm số lần advice với số instance |
| **Operation** | Nhiều lời gọi, đồng thời, retry, nested invocation | State dùng chung bị ghi đè hoặc không thread-safe |
| **Deactivation** | Ngừng chọn/gọi advice, ngừng tạo request mới | In-flight call vẫn còn dùng tài nguyên |
| **Disposal** | Cleanup, close, flush, unregister | Rò tài nguyên, mất dữ liệu hoặc đóng quá sớm |
| **GC/unloading** | JVM thu gom object/classloader khi không còn giữ | Không phải lời hứa thời điểm cleanup |

Một constructor chạy đúng một lần **cho mỗi object được tạo**, nhưng có thể có nhiều object. Một advice `before` có thể chạy hàng nghìn lần trên **cùng aspect instance**. Ngược lại, một per-invocation state có thể được tạo mỗi lần advice chạy. Nếu cần kiểm chứng, đếm **constructor, advice entries và close** độc lập.

## 4. Scope quyết định số instance và vòng đời

Các mô hình quản lý aspect/state có thể được khái quát như sau. Tên gọi cụ thể ở các engine có thể khác; chỉ dùng chúng như **kiểu phạm vi kiến trúc**, không khẳng định API của mọi thư viện.

| Scope/strategy | Một instance tồn tại cho | Mốc tạo điển hình | Mốc giải phóng có thể có |
| --- | --- | --- | --- |
| **Application/global** | Một vùng ứng dụng hoặc một registry | Startup hoặc lần dùng đầu tiên | Shutdown/đóng registry |
| **Per-type / classloader** | Một class hoặc một classloader scope | Lúc initialization của class hoặc lazy lookup | Classloader scope kết thúc; cleanup tường minh nếu có tài nguyên |
| **Per-target** | Một đối tượng nghiệp vụ được quản lý | Khi target được instrument hoặc proxy được tạo | Khi target/handle bị bỏ đăng ký, đóng hoặc được thu hồi |
| **Per-proxy** | Một wrapper được tạo riêng | Khi factory tạo proxy | Khi wrapper owner đóng/hủy |
| **Per-invocation** | Một lần advice/join point invocation | Khi vào phạm vi gọi | `finally` hoặc sau khi completion theo định nghĩa |
| **Per-control-flow** | Một chuỗi lời gọi động có điểm bắt đầu/chấm dứt | Khi vào luồng điều khiển đã chọn | Khi rời luồng, kể cả exceptional exit |
| **Pooled / keyed** | Một tập instance tái sử dụng hoặc theo khóa | Tạo trước hoặc theo nhu cầu | Khi pool/key hết hạn và không còn người dùng |
| **Stateless/static** | Có thể không cần instance trạng thái riêng | Tùy cách biên dịch/gọi | Thường không có cleanup gắn với state, nhưng dependency bên ngoài vẫn có |

**Một instance per target không có nghĩa một invocation.** Target có thể nhận nhiều request. **Một instance shared không có nghĩa advice chỉ chạy một lần.** Instance và invocation là hai trục riêng.

### 4.1. Global hoặc singleton-like aspect

Một state holder chung có thể tiết kiệm khởi tạo tài nguyên đắt tiền và tập trung metrics. Nó cũng là nơi nguy hiểm khi lưu dữ liệu gắn với **một lời gọi** trong field mutable:

~~~java
// Pseudocode cấu trúc có vấn đề: một field dùng chung
class SharedTimer {
    long startedAt;

    void before() {
        startedAt = System.nanoTime();
    }

    void after() {
        record(System.nanoTime() - startedAt);
    }
}
~~~

Đây là **đoạn minh họa không độc lập** vì `record` chưa được định nghĩa. Nếu hai thread chạy `before` xen kẽ, chúng có thể dùng sai mốc thời gian. Hãy lưu dữ liệu theo invocation trong local variable, context đúng scope hoặc cấu trúc concurrency phù hợp. Với bộ đếm chia sẻ, `AtomicLong`/`LongAdder` có thể cần thiết, nhưng lựa chọn còn tùy số liệu và mức chính xác.

### 4.2. Per-target aspect

Một per-target aspect có thể giữ cấu hình liên quan đối tượng được quan sát: tenant policy, danh tính kỹ thuật của target, hoặc tài nguyên riêng. Nhưng per-target state có thể dẫn tới:

- Số instance tăng theo số target, không theo số request.
- Một target được tái sử dụng nhiều lần sẽ dùng chung state, nên cần thread-safety.
- Nếu registry nắm tham chiếu mạnh tới target mãi, target có thể không được GC.
- Nếu aspect giữ target, dùng `WeakHashMap<Target,Aspect>` **không luôn tránh rò rỉ**: value aspect có tham chiếu ngược mạnh tới key target có thể tạo đường giữ sống qua map.
- Khi target được đóng, phải quản lý reference count/in-flight calls trước khi giải phóng resource.
- Khi proxy lồng nhau, “mỗi target” có thể được hiểu là object bên trong hay proxy bên ngoài — phải xác định bằng ownership.

### 4.3. Per-invocation aspect hoặc context

Mỗi lời gọi tạo một scope riêng giúp tránh lẫn dữ liệu khi đồng thời và recursion. Nhưng không cần tạo client mạng, thread pool hoặc resource đắt tiền **mỗi lần** nếu có thể dùng shared client thread-safe. Thông thường, context ngắn hạn chứa start time, invocation ID, counters riêng, trong khi sink metrics là dependency sống dài hơn.

Mẫu:

~~~java
long start = System.nanoTime();
try {
    return proceed();
} finally {
    recordElapsed(System.nanoTime() - start);
}
~~~

Đây là pseudocode Java minh họa, không độc lập (`proceed`/`recordElapsed` phải được cung cấp). `start` là local variable theo lần gọi, tránh chia sẻ qua thread. Nếu `recordElapsed` ném lỗi trong `finally`, nó có thể che result/exception gốc: phải định nghĩa lỗi sink theo chính sách phù hợp.

### 4.4. Per-control-flow

Một scope theo luồng điều khiển động có thể bắt đầu khi bước xử lý chính diễn ra rồi bao gồm các join point lồng trong nó. Khái niệm này có thể mang tên riêng ở những công nghệ AOP khác nhau. Nó **không tự đồng nghĩa per-thread** và cũng không đảm bảo bám theo asynchronous continuation qua thread khác.

Ví dụ:

~~~text
enter checkout() -> context A created
  validate()     -> uses context A
  reserve()      -> uses context A
leave checkout() -> context A disposed

enter checkout() again -> context B created
~~~

Nếu `checkout` gọi lại chính nó (recursion), thiết kế phải xác định một scope cho luồng ngoài hay scope lồng riêng; không có quy tắc chung. Nếu công việc chuyển sang executor thread, context propagation cần được thiết kế rõ, không dựa vào `ThreadLocal` tự động đi qua mọi thread.

## 5. “Khi nào aspect được tạo?” phụ thuộc runtime thực tế

Một số chiến lược có thể gặp:

- **Eager creation:** registry/factory tạo các instance lúc startup để phát hiện lỗi cấu hình sớm.
- **Lazy creation:** instance được tạo khi có lần gọi/target đầu tiên cần advice.
- **On-demand proxy:** wrapper được tạo khi factory cần cấp một đối tượng cho caller.
- **Class initialization:** static holder được JVM khởi tạo ở sự kiện class initialization, không phải chắc chắn ngay khi file class được nạp.
- **Instrumented code lookup:** mã đã woven truy xuất aspect instance từ một registry hoặc factory; thời điểm registry tạo instance phụ thuộc thiết kế.
- **Per-flow creation:** instance được tạo khi luồng điều khiển liên quan được khởi đầu.
- **Explicit ownership:** người gọi tạo một đối tượng advice hoặc handler và truyền nó vào factory; lifecycle theo code quản lý đối tượng.

**Class loading khác class initialization.** Trong Java, một lớp có thể được nạp/định nghĩa nhưng chưa thực hiện static initialization. Cần tránh kết luận “load-time weaver đã biến đổi class thì constructor mọi aspect đã chạy”.

Tương tự, class đã dệt ở compile-time **không nhất thiết** tạo aspect instance ngay lúc JVM khởi động. Việc weaving tạo khả năng can thiệp; việc tạo instance do mã/hợp đồng runtime quyết định.

## 6. Constructor, khởi tạo và trạng thái sẵn sàng

Một instance được tạo không có nghĩa nó đã sẵn sàng sử dụng. Có thể có các bước:

~~~text
allocate
  -> initialize fields
  -> run constructor
  -> wire validated dependencies
  -> register for interception
  -> expose to callers
~~~

Đây là một **lifecycle thiết kế**, không phải quy tắc bắt buộc của Java hay mọi engine. Thứ tự Java object construction có những quy tắc riêng về superclass/field initializers, nhưng ở mức ứng dụng cần quan tâm **không công bố instance khi chưa hoàn tất khởi tạo**.

### 6.1. Khởi tạo tài nguyên sớm hay muộn?

**Sớm:** có thể fail-fast nếu thiếu endpoint metrics hay cấu hình, nhưng kéo dài startup và có thể cấp phát resource không dùng đến.

**Muộn:** giảm công việc startup, nhưng lỗi khởi tạo có thể xuất hiện ngay trên request đầu tiên. Nếu nhiều thread đồng thời cùng lazy-create, cần đảm bảo chỉ một object được tạo hoặc có cách xử lý nhiều lần đúng; đừng dùng cờ boolean không đồng bộ rồi giả định an toàn.

### 6.2. Không để `this` thoát khi constructor chưa kết thúc

Đăng ký một callback/interceptor mang `this` với hệ thống khác trong constructor trước khi tất cả field được khởi tạo có thể làm thread khác gọi advice trên một object chưa sẵn sàng. Nếu có khả năng callback chạy ngay, thiết kế nên tách `construct`, `initialize`, `register` với điều kiện sẵn sàng rõ ràng.

### 6.3. Constructor bị lỗi

Nếu constructor hoặc init hook ném ngoại lệ:

- Target/registry không nên công bố instance chưa hoàn tất.
- Những tài nguyên đã cấp phát trước lúc lỗi phải có cleanup phù hợp, ví dụ try/catch hoặc resource ownership cục bộ.
- Không tự đánh dấu aspect “đã init” nếu bước init chưa thành công.
- Cần rõ retry khởi tạo có được phép không: đối với stateful resource, retry có thể cần cleanup từ lần thất bại trước.

## 7. Phân biệt vòng đời aspect với vòng đời target, proxy và join point

Bốn đối tượng/hiện tượng có thể không sống cùng khoảng thời gian:

~~~text
Application lifespan:   |---------------------------------------|
Global aspect:          |---------------------------close------|
Target #1:                  |-------------|
Proxy for target #1:        |---------------close|
Invocation #1:                 |-|
Invocation #2:                       |-|
Target #2:                             |-------------|
~~~

- Target có thể được tạo trước proxy.
- Proxy có thể bị ngừng sử dụng trước khi target bị GC.
- Aspect shared có thể phục vụ nhiều target và sống lâu hơn chúng.
- Aspect per-target có thể được tạo cùng proxy và đóng cùng owner của proxy.
- Per-invocation scope mở/đóng **nhiều lần** khi target còn tồn tại.
- Join point là **điểm thực thi**, không phải một Java object buộc phải có lifecycle riêng.

Trong một engine bytecode weaving, khái niệm “proxy instance” có thể hoàn toàn không tồn tại ở đường gọi. Đừng thiết kế disposal chỉ dựa trên việc “proxy đã chết” nếu advice được cài vào class và dùng state registry độc lập.

## 8. Ví dụ Java 17 chạy độc lập: ba phạm vi, hai target, ba lời gọi

Chương trình dùng **JDK interface proxy**, **không** dùng bytecode weaving. Nó mô phỏng ba phạm vi nhằm kiểm chứng thời điểm tạo và dọn dẹp:

1. `GlobalTrace` được tạo một lần, được cả hai target sử dụng, và đóng ở cuối vùng ứng dụng.
2. `TargetAspect` được tạo riêng khi một `ManagedJob` được thiết lập, gắn với một target.
3. `InvocationScope` được tạo **mỗi lần lời gọi đi qua proxy**, đóng ngay sau lời gọi bằng `try-with-resources`, kể cả khi target ném exception.

Code còn minh họa `AtomicBoolean` để `close()` của `ManagedJob` là idempotent theo phạm vi demo; `Method.invoke` bóc `InvocationTargetException` để giữ nguyên ngoại lệ nghiệp vụ. Ví dụ chạy **tuần tự** và đóng owner sau khi tất cả invocation kết thúc. Trong ứng dụng đồng thời, chỉ cờ `closed` **không** đủ để xử lý graceful shutdown; phải chờ các lời gọi đang chạy kết thúc trước khi đóng resource chúng dùng.

Tạo file `AspectLifecycleDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AspectLifecycleDemo {
    interface Job { String run(int units); }

    static final class Core implements Job {
        public String run(int units) {
            System.out.println("BUSINESS run " + units);
            if (units <= 0) throw new IllegalArgumentException("units must be positive");
            return "ok:" + units;
        }
    }

    static final class GlobalTrace implements AutoCloseable {
        GlobalTrace() { System.out.println("GLOBAL create+init"); }
        void before() { System.out.println("GLOBAL before"); }
        public void close() { System.out.println("GLOBAL close"); }
    }

    static final class TargetAspect implements AutoCloseable {
        final String name;
        TargetAspect(String name) {
            this.name = name;
            System.out.println("TARGET create+init " + name);
        }
        void before() { System.out.println("TARGET before " + name); }
        void returning() { System.out.println("TARGET returning " + name); }
        void throwing(Throwable t) {
            System.out.println("TARGET throwing " + name + " " + t.getClass().getSimpleName());
        }
        public void close() { System.out.println("TARGET close " + name); }
    }

    static final class InvocationScope implements AutoCloseable {
        final String name;
        InvocationScope(String name) {
            this.name = name;
            System.out.println("CALL open " + name);
        }
        public void close() { System.out.println("CALL release " + name); }
    }

    static final class ManagedJob implements AutoCloseable {
        final TargetAspect aspect;
        final Job proxy;
        final AtomicBoolean closed = new AtomicBoolean();
        ManagedJob(String name, Job target, GlobalTrace global) {
            aspect = new TargetAspect(name);
            proxy = (Job) Proxy.newProxyInstance(
                    Job.class.getClassLoader(),
                    new Class<?>[]{Job.class},
                    (p, method, args) -> {
                        if (closed.get()) throw new IllegalStateException("proxy closed");
                        try (InvocationScope scope = new InvocationScope(name)) {
                            global.before();
                            aspect.before();
                            try {
                                Object result = invoke(target, method, args);
                                aspect.returning();
                                return result;
                            } catch (Throwable error) {
                                aspect.throwing(error);
                                throw error;
                            }
                        }
                    });
        }
        Job api() { return proxy; }
        public void close() {
            if (closed.compareAndSet(false, true)) aspect.close();
        }
    }

    static Object invoke(Job target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    public static void main(String[] args) {
        try (GlobalTrace global = new GlobalTrace()) {
            try (ManagedJob first = new ManagedJob("A", new Core(), global);
                 ManagedJob second = new ManagedJob("B", new Core(), global)) {
                System.out.println("CALLER " + first.api().run(2));
                try {
                    second.api().run(0);
                } catch (IllegalArgumentException error) {
                    System.out.println("CALLER " + error.getMessage());
                }
                System.out.println("CALLER " + first.api().run(1));
            }
        }
    }
}
~~~

Lệnh với JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 AspectLifecycleDemo.java
java AspectLifecycleDemo
~~~

Đầu ra đã xác minh bằng biên dịch và chạy chương trình thật:

~~~text
GLOBAL create+init
TARGET create+init A
TARGET create+init B
CALL open A
GLOBAL before
TARGET before A
BUSINESS run 2
TARGET returning A
CALL release A
CALLER ok:2
CALL open B
GLOBAL before
TARGET before B
BUSINESS run 0
TARGET throwing B IllegalArgumentException
CALL release B
CALLER units must be positive
CALL open A
GLOBAL before
TARGET before A
BUSINESS run 1
TARGET returning A
CALL release A
CALLER ok:1
TARGET close B
TARGET close A
GLOBAL close
~~~

### 8.1. Vì sao có một `GLOBAL` nhưng hai `TARGET`?

`main()` tạo một `GlobalTrace` trong try-with-resources ngoài. Hai `ManagedJob` dùng cùng đối tượng ấy, nên chỉ xuất hiện **một** dòng `GLOBAL create+init`. Constructor của mỗi `ManagedJob` lại tạo một `TargetAspect` riêng, nên có `TARGET create+init A` và `TARGET create+init B`.

Ba lần gọi `run()` không tạo thêm `GlobalTrace` hay `TargetAspect`. **Số lần advice thực thi không nhất thiết bằng số instance aspect.**

### 8.2. Vì sao có ba lần `CALL open` và ba lần `CALL release`?

Mỗi lần lời gọi qua proxy tạo một `InvocationScope` mới. Hai lần của A và một lần của B tương ứng ba scopes. `try-with-resources` đóng scope khi rời khối, kể cả ở lần `run(0)` ném `IllegalArgumentException`.

Đây là mô phỏng **per-invocation context** thông qua Java object lifetime, không khẳng định mọi AOP engine tạo một Java object cho mỗi join point.

### 8.3. Vì sao `TARGET close B` xuất hiện trước `TARGET close A`?

Trong Java try-with-resources, các resources cùng danh sách được đóng **theo thứ tự ngược thứ tự khai báo**. `second` được tạo sau `first` nhưng đóng trước `first`. Sau khi phạm vi trong kết thúc, `GlobalTrace` ở phạm vi ngoài mới được đóng.

Vì sao quan trọng? Nếu `TargetAspect` dựa vào `GlobalTrace` như shared sink, target aspects cần được đóng trong khi sink còn sống. Cấu trúc owner scopes của ví dụ sắp xếp việc đóng theo cách ấy.

### 8.4. Ngoại lệ nghiệp vụ có bị thay đổi không?

Không. `Core.run(0)` ném `IllegalArgumentException`; `invoke()` bóc `InvocationTargetException` do Reflection sinh ra; handler ghi `TARGET throwing`, `InvocationScope.close()` chạy, rồi caller bắt đúng loại exception gốc. **Không cần catch và nuốt ngoại lệ nghiệp vụ chỉ để cleanup.**

Lưu ý: nếu `close()` của resource cũng ném exception, Java try-with-resources có cơ chế **suppressed exceptions** khi đã có lỗi chính. Một implementation cleanup có side effect phức tạp phải kiểm thử cả lỗi disposal, tránh giả định close luôn vô hại.

### 8.5. Giới hạn minh họa của demo

- Instance lifetime được code chủ động quản lý; không chứng minh một weaver cụ thể hỗ trợ `per-target` hay `per-control-flow` bằng ngôn ngữ pointcut.
- `GlobalTrace` không thực sự là singleton JVM; chỉ là một object được tạo một lần trong ví dụ. Chạy `main()` hai lần sẽ tạo hai object.
- Proxy chỉ thấy lời gọi đi qua `api()`; target gọi trực tiếp sẽ không có scope/advice do proxy này tạo.
- `AtomicBoolean` giúp `close` idempotent trong code demo nhưng **không ngăn một invocation đã qua cổng kiểm tra đồng thời với việc close**.
- `TargetAspect.throwing()` chỉ in loại lỗi; code không biến đổi result hoặc exception của target.
- Việc in log sync là để học; production nên xem xét chi phí I/O, dữ liệu nhạy cảm và thread safety.
- Nếu một phương thức trả `CompletableFuture`, `InvocationScope` trong handler này sẽ đóng khi Future được **trả về**, không chờ tác vụ bên trong hoàn tất.

## 9. Khởi tạo với lazy instance: chi phí và tính an toàn

Nếu một aspect instance được tạo **lần đầu khi join point khớp**, cần xem:

1. Cùng một lần khớp có thể xảy ra trên nhiều thread cùng lúc không?
2. Factory có đảm bảo single initialization cho phạm vi intended không?
3. Nếu init thất bại, có retry lần sau hay ghi nhớ trạng thái failed?
4. Nếu target biến mất trước lần gọi đầu, liệu aspect có cần được tạo không?
5. Nếu policy bị tắt rồi bật lại, object cũ được tái sử dụng hay tạo mới?
6. Nếu aspect có tài nguyên đóng, ai nắm ownership khi factory tạo lazy?

Mô hình double-checked locking chỉ an toàn khi thực hiện đúng quy tắc Java Memory Model, ví dụ field `volatile` cùng quy trình initialization phù hợp. Nếu không có nhu cầu học/viết primitive concurrency, nên ưu tiên một lazy holder/factory đã có bảo đảm rõ ràng.

Tránh để advice tự kiểm tra `if (client == null) client = new Client()` trên một field dùng chung mà không đồng bộ. Hai request có thể tạo hai clients, leak một client, hoặc công bố instance chưa khởi tạo đầy đủ.

## 10. Cleanup: GC không thay thế `close()`

Java **garbage collection** quyết định thu gom bộ nhớ của các object không còn reachable. GC không hứa thời điểm chính xác object được thu hồi. Tài nguyên như socket, file descriptor, thread, executor, batch buffer hoặc subscription nên có **đường đóng tường minh** khi cần.

Các khái niệm cần phân biệt:

- **Object unreachable:** chương trình không còn đường tham chiếu mạnh có thể truy cập object.
- **Resource disposed:** socket/file/client/subscription đã được đóng theo hợp đồng.
- **Classloader unloadable:** toàn bộ classloader và các class liên quan không còn bị giữ sống.
- **Advice deactivated:** không còn lời gọi mới kích hoạt advice theo registry/pointcut.
- **In-flight invocation completed:** các lời gọi đã bắt đầu dùng tài nguyên đã thực sự kết thúc.

Một aspect có thể bị deactivated nhưng còn in-flight calls. Nếu đóng resource ngay tại thời điểm deactivation, lời gọi đang chạy có thể mất sink hoặc context. Vì vậy, với shutdown thực tế thường cần:

~~~text
STOP_ACCEPTING_NEW_CALLS
        |
WAIT_FOR_IN_FLIGHT_TO_FINISH (with bounded deadline)
        |
FLUSH / CLOSE PER-TARGET RESOURCES
        |
CLOSE SHARED RESOURCES
        |
UNREGISTER / RELEASE REFERENCES
~~~

Đây là **quy trình ý niệm**; triển khai chính xác phụ thuộc resource và cơ chế registration. Không được mặc định `AtomicBoolean closed` tự triển khai toàn bộ trình tự graceful shutdown.

## 11. Sở hữu tài nguyên: ai tạo thì ai đóng?

Một nguyên tắc hữu ích là gắn trách nhiệm cleanup với **owner** đã thiết lập vòng đời:

| Resource | Scope phù hợp có thể cân nhắc | Owner/disposal điển hình |
| --- | --- | --- |
| Metrics client chia sẻ | Application/global | Application registry hoặc service manager |
| Tham số cấu hình riêng target | Per-target | Target proxy manager hoặc factory |
| Stopwatch start time | Per-invocation | Local scope, kết thúc trong `finally` |
| Trace span | Per-invocation / async task | Span owner, kết thúc đúng sự kiện completion |
| Subscription/listener | Per-target hoặc application | Thành phần đã đăng ký subscription |
| Executor/thread pool | Thường scope dài được chia sẻ có kiểm soát | Executor owner gọi shutdown |
| Buffer batch logs | Theo sink instance | Sink owner flush rồi close |
| Security context | Theo request/control-flow | Request context owner, có cleanup chính xác |

Không để per-invocation advice tự đóng một client dùng chung: request sau sẽ gặp lỗi. Cũng không để per-target aspect giữ **một request context mãi trong field**, vì target có thể phục vụ nhiều requests khác nhau.

### 11.1. Có thể có nhiều owners cùng gọi close?

Nên thiết kế **một owner logic rõ ràng**. Nếu hoàn cảnh có khả năng `close()` được gọi nhiều lần, `close` nên có hợp đồng idempotent hoặc xử lý repeat call rõ; song idempotent `close` không đồng nghĩa với việc an toàn khi bị gọi đồng thời cùng `use()`.

### 11.2. Khi cleanup cũng thất bại

Nếu đang rời advice vì exception nghiệp vụ mà `close()` ném thêm lỗi, cần giữ ngữ nghĩa lỗi gốc. Try-with-resources hỗ trợ suppressed exception trong những trường hợp thích hợp. Nếu tự viết `finally`, phải xem lỗi cleanup có thể che lỗi ban đầu hay không.

Đối với observability sink theo chính sách **fail-open**, có thể ghi lỗi cleanup ra kênh riêng và không làm hỏng nghiệp vụ; nếu policy có tính bắt buộc, cần định nghĩa failure semantics rõ. Không catch mọi `Throwable` rồi âm thầm bỏ qua.

## 12. Concurrency: aspect instance dùng chung không đồng nghĩa thread-safe

Nếu một aspect phục vụ nhiều target hoặc nhiều invocations, **các field mutable** có thể bị truy cập đồng thời. Một số quy tắc:

- **State theo invocation:** lưu trong local variables của advice, hoặc object context không chia sẻ tùy tiện.
- **State theo target:** tạo riêng per-target nhưng vẫn cần bảo vệ nếu cùng target bị gọi nhiều thread.
- **Shared counters:** dùng atomic/concurrent data structures phù hợp.
- **Immutable configuration:** ưu tiên cấu hình bất biến được công bố an toàn.
- **External clients:** chỉ chia sẻ nếu library contract cho phép concurrent calls.
- **Shutdown:** không chỉ set boolean và đóng sink; phải phân định rõ các invocation đang chạy.
- **Recursion:** một advice instance có thể được tái nhập (reentrant) trên cùng thread, nên field `currentInvocation` dùng chung cũng có nguy cơ hỏng dù không có multithreading.
- **ThreadLocal:** cần cleanup trong `finally` khi dùng thread pool; không tự truyền qua task async/thread khác.

### Mốc bắt đầu đo thời gian

Sai lầm:

~~~java
class SharedTimingState {
    long started;
    void enter() { started = System.nanoTime(); }
    long exit() { return System.nanoTime() - started; }
}
~~~

Đây là ví dụ logic có thể gặp lỗi; không có chương trình test độc lập. Nếu thread A gọi `enter`, thread B ghi `started` sau đó thread A gọi `exit`, elapsed của A dùng mốc của B.

Cách đúng về scope là `long started` cục bộ trong một lần `around` hoặc một đối tượng per-invocation đúng hợp đồng. Không nhất thiết tạo mới cả **aspect instance** để có state per-invocation; aspect shared có thể dùng local variables.

## 13. Aspect theo control-flow và vấn đề `ThreadLocal`

Một control-flow scope có thể gắn với hoạt động đang diễn ra, ví dụ “trong thời gian xử lý giao dịch X”. Nhưng Java có:

- Nested method calls.
- Recursion.
- Thread pools.
- `CompletableFuture` và callback.
- Virtual threads hoặc execution contexts khác, tùy JDK.
- Cancellation, timeout, exceptional completion.

Một `ThreadLocal` chỉ gắn state với thread cụ thể; nó không tự thể hiện “ngữ cảnh logic X” xuyên mọi scheduler và async boundary. Nếu dùng `ThreadLocal` để mô phỏng control-flow, cần đảm bảo:

~~~java
Context previous = context.get();
try {
    context.set(current);
    runWork();
} finally {
    if (previous == null) context.remove();
    else context.set(previous);
}
~~~

Đây là **mã Java minh họa không độc lập** vì `Context`, `context` và `runWork()` chưa được định nghĩa. Kỹ thuật “lưu previous/khôi phục trong finally” quan trọng khi có nested context. Nhưng nó **chưa giải quyết** việc truyền context qua thread khác; muốn làm điều đó phải thiết kế propagation riêng theo executor/task semantics.

Đặc biệt, không được để một control-flow aspect giả định luôn kết thúc khi một method trả Future. **Method return** có thể xảy ra trước **task completion**.

## 14. Bất đồng bộ: thời điểm giải phóng nào mới đúng?

Giả sử:

~~~java
CompletableFuture<String> loadAsync() {
    return CompletableFuture.supplyAsync(() -> compute());
}
~~~

Đoạn này là **minh họa chưa độc lập** vì `compute()` chưa được định nghĩa.

Nếu around advice mở một invocation scope trước `proceed` và đóng trong `finally` ngay khi `proceed` trả `CompletableFuture`, nó đã dọn dẹp scope của **method return**, không phải scope của **tác vụ đến khi hoàn tất**.

Muốn quản lý lifecycle dài đến completion:

1. Phải định nghĩa completion event rõ: successful, exceptional hoặc cancellation.
2. Gắn callback/handle thích hợp vào Future hoặc abstraction bất đồng bộ tương ứng.
3. Không đóng tài nguyên mà callback còn cần trước completion.
4. Đảm bảo cleanup được thực hiện đúng một lần nếu completion và cancellation race.
5. Xác định ai sở hữu tài nguyên nếu caller hủy/bỏ Future.
6. Tránh block `join()` trong after-returning chỉ để đóng resource; làm vậy có thể phá API async.

Ví dụ minh họa:

~~~java
long start = System.nanoTime();
CompletableFuture<String> future = startAsync();
future.whenComplete((result, error) -> {
    long nanos = System.nanoTime() - start;
    observeCompletion(result, error, nanos);
});
~~~

Đây không phải một chương trình độc lập (`startAsync` và `observeCompletion` cần được triển khai). `whenComplete` trả một stage mới; lỗi do callback có thể ảnh hưởng stage phụ thuộc. Nếu giữ tài nguyên cho callback, cần đảm bảo completion callback đó không bị mất theo policy và luôn có nơi quản lý lỗi của chính callback.

**Không gộp** hai nghĩa “vòng đời invocation của method” và “vòng đời operation bất đồng bộ”; lựa chọn phụ thuộc metric/audit mà aspect chịu trách nhiệm.

## 15. Proxy, weaving và vòng đời đối tượng không đồng nhất

### 15.1. Với proxy

Factory thường sở hữu hoặc cấp proxy; state handler có thể gắn với proxy, target hoặc một registry riêng. Phạm vi quan sát bị giới hạn bởi **đường caller dùng proxy**.

Nếu caller giữ target raw và gọi trực tiếp, handler proxy không có invocation nào để mở/đóng context. Một target có thể tiếp tục tồn tại sau khi proxy bị đóng. Phải quy định rõ `close()` tác động tới proxy/handler, target thật hay cả hai, tránh đóng một target shared ngoài ý định.

### 15.2. Với code/bytecode weaving

Advice có thể được gắn vào method execution hoặc join point khác khi class được dệt. Một method woven có thể chạy dù không có wrapper proxy. Trạng thái advice có thể nằm trong field được sinh, registry, helper class hoặc cấu trúc engine quản lý.

Vì thế không nên khẳng định “mỗi target được weaving sẽ tự có constructor aspect riêng”. Một weaver có thể tạo instance per-object, dùng singleton hoặc không lưu state, tùy mô hình instantiation hỗ trợ.

### 15.3. Với load-time weaving

Class transformation thường xảy ra trước khi JVM định nghĩa class. Nhưng aspect instance có thể được tạo muộn hơn. Nếu class đã nạp trước khi transformer được thiết lập, nó có thể không được instrument bởi LTW thông thường; hỗ trợ retransformation/redefinition nếu có là cơ chế riêng có giới hạn.

### 15.4. Với nhiều classloaders

Trong JVM, class identity gắn với **tên class và defining classloader**. Nếu một holder static được khai báo trong class do hai classloaders khác nhau định nghĩa, có thể tồn tại những copies riêng của class và static state. Do đó từ “một singleton cho cả JVM” thường quá mạnh: hãy nói **một instance trong registry/classloader scope cụ thể** theo khả năng thực tế.

Nếu một registry toàn cục nắm tham chiếu mạnh tới classloader hoặc class từ plugin đã gỡ, việc unload có thể bị cản. Cleanup đúng cần unregister aspect/transformer/callback và loại những reference không còn cần.

## 16. Tính tuần tự và thứ tự teardown

Một hệ thống có nhiều tầng:

~~~text
Application
  SharedMetrics
    TargetAspect #1
      InvocationScope #A
    TargetAspect #2
      InvocationScope #B
~~~

Nếu `InvocationScope` còn ghi metrics lúc đóng, shared sink phải **còn sống** cho tới khi mọi scope liên quan hoàn tất. Thứ tự đóng hợp lý thường là:

1. Dừng admission/lời gọi mới.
2. Đợi các calls đang chạy hoàn tất hoặc tới deadline đã xác định.
3. Đóng các per-invocation/per-flow contexts còn sở hữu.
4. Đóng các per-target registrations/aspects.
5. Flush và đóng shared services.
6. Bỏ đăng ký và giải phóng tham chiếu còn lại.

Đây là quy trình **mang tính thiết kế**; có resource cần đóng theo thứ tự khác. Điều quan trọng là tạo **dependency graph**: nếu A cần B trong `close()`, đừng đóng B trước A.

Trong ví dụ `AspectLifecycleDemo`, resource list của try-with-resources tự đóng theo thứ tự ngược: target B rồi target A, cuối cùng global. Điều đó chứng minh trật tự cleanup **do owner scopes quyết định**, không phải mặc nhiên JVM luôn đóng aspects theo cùng một thứ tự.

## 17. Rủi ro memory leak và quản lý registry

Một registry instance per-target có thể trở thành nơi giữ sống target vô hạn.

Những mối nguy:

- `Map<Target, Aspect>` là map thường, giữ mạnh cả key và value.
- `WeakHashMap<Target, Aspect>` nhưng aspect value giữ lại chính target key; weak key không còn đủ giúp entry biến mất nếu value reachable qua map lại tham chiếu ngược key.
- Handler đăng ký với event bus toàn cục nhưng không unregister.
- `ThreadLocal` giữ context trên thread pool lâu dài.
- Static cache có key classloader hoặc `Class<?>` làm plugin classloader không unload.
- Future callbacks giữ tham chiếu tới target, aspect và payload tới khi hoàn tất; nếu Future không bao giờ complete, chúng có thể ở lại.
- Một aspect tạo executor/thread mới và không shutdown.
- Một proxy bị quên dispose dù giữ subscription/resource.

**Giải pháp tốt nhất bắt đầu bằng ownership và scope**, không chỉ thêm weak reference. Xác định điều gì tạo instance, ai biết khi nào target không dùng nữa, và khi nào một reference cần được loại khỏi registry.

## 18. Khi nào cần có `close()` hay disposal hook?

Không phải aspect nào cũng cần cleanup riêng.

**Không có tài nguyên:** advice chỉ đọc tham số và ghi counter thread-safe vào một sink đã được application sở hữu thì per-invocation scope có thể kết thúc khi method return; không cần tạo `AutoCloseable` mỗi lần.

**Có tài nguyên riêng:** nếu aspect sở hữu file, socket, listener, subscription hoặc buffer riêng, cần quy tắc dọn dẹp rõ. `AutoCloseable` là một lựa chọn Java thuận tiện nhưng không phải API bắt buộc của mọi AOP engine.

**Có tài nguyên chia sẻ:** aspect chỉ **mượn** một metrics client thì không tự đóng client khi một target hoàn tất. Application owner đóng nó vào shutdown.

**Có asynchronous work:** nếu advice mở span cho task kéo dài, phải đóng ở completion/cancellation theo contract thay vì return của method tạo task.

Với retry/timeout/cancellation, hãy xác định event nào làm scope kết thúc để tránh close hai lần hoặc leak.

## 19. Kiểm thử vòng đời theo scope

Nên có **ba bộ đếm khác nhau**:

- `createdCount`: số instance/state holders tạo thành công.
- `adviceEntryCount`: số lần advice được gọi.
- `closedCount`: số instance/resource đóng đúng hợp đồng.

Ví dụ, nếu có một global, hai targets và ba invocations:

| Scope | Tạo dự kiến | Kích hoạt dự kiến | Đóng dự kiến |
| --- | ---: | ---: | ---: |
| GlobalTrace | 1 | 3 lần `before` | 1 |
| TargetAspect A | 1 | 2 lần `before` | 1 |
| TargetAspect B | 1 | 1 lần `before` | 1 |
| InvocationScope | 3 | 3 | 3 |

Cần nhớ “một advice đã in log” không phải bằng chứng nó chỉ có một instance. Muốn kiểm tra scope, đưa định danh **instance** và **invocation** khác nhau vào test, tránh dùng object `hashCode` làm bằng chứng tuyệt đối về danh tính.

### 19.1. Ma trận test quan trọng

| Ca thử | Kỳ vọng |
| --- | --- |
| Tạo factory nhưng chưa gọi method | Đúng policy eager/lazy, không phát sinh advice invocation |
| Gọi một target hai lần | Per-target instance tái sử dụng nếu scope yêu cầu |
| Tạo hai targets | Hai per-target states độc lập, shared dependency không bị tạo lại |
| Method thành công | Open/close invocation cân bằng, return được bảo toàn |
| Method ném exception | Close vẫn chạy, ngoại lệ gốc không mất |
| Advice có lỗi khi init | Không public instance chưa sẵn sàng; cleanup tài nguyên đã mở |
| Close được gọi hai lần | Theo hợp đồng idempotence, không đóng tài nguyên hai lần |
| Close đồng thời với invocation | Quy tắc ngừng tiếp nhận và chờ in-flight rõ ràng |
| Lời gọi đệ quy | Nested contexts không ghi đè state |
| Nhiều thread cùng target | Shared/per-target state được đồng bộ đúng |
| Truy cập bằng raw target bỏ qua proxy | Không mong đợi proxy advice thực thi |
| Method trả Future chưa xong | Scope method return và scope task completion được phân biệt |
| Classloader bị gỡ | Registry và callback không giữ references cũ |
| Bật/tắt aspect runtime | Không leak registration/state sau mỗi lần thay cấu hình |
| Shutdown | Per-target đóng trước shared sink nếu cần |
| Two advice có chung tài nguyên | Không đóng shared resource bởi advice không sở hữu |

### 19.2. Test với lỗi disposal

Cần thử trường hợp close resource thất bại, đặc biệt khi target đã ném exception. Với try-with-resources của Java, có thể kiểm tra lỗi chính và `getSuppressed()` để hiểu ngoại lệ dọn dẹp. Nếu tự viết `finally`, cần đảm bảo policy không vô tình che nguyên nhân nghiệp vụ.

### 19.3. Test lazy initialization dưới concurrency

Nhiều request đồng thời gọi lần đầu có thể bộc lộ race không xảy ra khi test đơn luồng. Sử dụng concurrency test với barrier hoặc latch phù hợp để xác nhận chỉ số lượng instance mong muốn được tạo và không ai nhận được object khởi tạo dở. Không dùng `Thread.sleep()` ngắn rồi khẳng định đó là test race đầy đủ.

## 20. Thực hành thiết kế an toàn

Một quy trình áp dụng được:

1. **Định nghĩa policy scope:** application, per-target, per-invocation hay per-flow; đừng chọn theo tên annotation.
2. **Xác định chủ sở hữu:** ai tạo aspect/state holder và ai gọi dispose?
3. **Tách installation khỏi instance creation:** weaver/proxy được thiết lập lúc nào và state được tạo lúc nào?
4. **Liệt kê phụ thuộc và tài nguyên:** thành phần nào shared, thành phần nào owned?
5. **Chọn init eager hay lazy:** cân nhắc startup, lỗi init và số instance.
6. **Giữ data theo invocation ở scope ngắn:** local vars/context, không field global mutable.
7. **Định nghĩa concurrency và reentrancy:** nếu per-target được chia sẻ giữa threads, đồng bộ ra sao?
8. **Chọn shutdown protocol:** chặn invocation mới, đợi in-flight, đóng từ scope nhỏ tới owner lớn.
9. **Phân biệt method return với async completion:** scope cần đóng khi nào?
10. **Kiểm soát classloader/registry:** unregister callbacks, dọn caches và tránh reference cycles qua maps.
11. **Bảo toàn behavior nghiệp vụ:** advice chỉ quan sát không đổi return, ngoại lệ hay số lần target được gọi.
12. **Integration test chính đường can thiệp:** một unit test gọi method advice không chứng minh proxy/weaving gắn đúng.
13. **Ghi metrics cho lifecycle:** số instances được tạo/đóng, active scopes, queues, errors và resource usage.
14. **Kiểm thử khi bật/tắt/redeploy:** không để handler cũ sống song song với handler mới ngoài ý định.

Với hệ thống lớn, nên có một **lifecycle owner registry** rõ ràng thay vì để mỗi advice tự mở và đóng tài nguyên một cách phân tán.

## 21. Các lỗi hiểu sai thường gặp

1. **“Weaving xảy ra tại build nên aspect instance cũng được tạo ở build.”** Không đúng; đó là hai thời điểm khác nhau.
2. **“Có một aspect definition thì chỉ có một instance.”** Phụ thuộc scope và engine.
3. **“Một aspect singleton chỉ chạy một lần.”** Instance shared có thể được gọi hàng nghìn lần.
4. **“Per-target có nghĩa per-invocation.”** Target thường có nhiều calls.
5. **“Constructor chạy xong thì mọi dependency đã sẵn sàng.”** Có thể còn init/registration riêng.
6. **“GC sẽ tự gọi close ngay khi không còn dùng.”** GC không bảo đảm cleanup tài nguyên đúng lúc.
7. **“Một `WeakHashMap` luôn tránh leak per-target state.”** Không đúng khi value giữ key hoặc có reference khác.
8. **“Một flag closed là đủ cho graceful shutdown.”** Không; còn in-flight invocations và race với cleanup.
9. **“ThreadLocal tự theo context qua Future và executor.”** Không tự động trong mọi đường async.
10. **“Method trả Future là task đã hoàn tất.”** Không; scope theo completion có thể dài hơn.
11. **“Mọi exception được ghi trong advice là lỗi từ target.”** Có thể là lỗi init, sink hoặc cleanup.
12. **“Một aspect shared có thể lưu `startedAt` dùng chung.”** Không an toàn với concurrency/reentrancy.
13. **“Dùng proxy là thay đổi target bytecode và lifecycle target.”** Không bắt buộc; wrapper có owner riêng.
14. **“LTW khởi tạo mọi aspect ngay khi classload.”** Không có quy tắc chung.
15. **“Một instance đóng thì shared metrics client nên đóng theo.”** Chỉ owner của shared client được quyết định.
16. **“Chạy unit test advice đủ chứng minh lifecycle thật.”** Cần integration test với đường proxy/weaving và container/application owner thực tế.
17. **“Classloader không liên quan singleton.”** Static state của classes từ các defining loaders khác nhau có thể tách biệt.
18. **“Đóng aspect không cần xét lỗi cleanup.”** Cleanup có thể ném exception và che lỗi chính hoặc gây mất dữ liệu.
19. **“Phạm vi per-control-flow chỉ là per-thread.”** Context logic và thread vật lý không phải luôn cùng một phạm vi.
20. **“Số events bằng số aspect instances.”** Events, advice entries, target invocations và created instances là các số đếm khác nhau.

## 22. Câu hỏi tự kiểm tra

**Câu 1.** Aspect được dệt tại compile-time, constructor instance chạy khi nào?  
**Trả lời:** Tùy cơ chế instantiation runtime của công cụ; không mặc định chạy trong lúc build.

**Câu 2.** Một instance global, hai target, ba lời gọi: số lần advice chạy có nhất thiết bằng một không?  
**Trả lời:** Không. Instance được tạo một lần nhưng advice có thể chạy ba lần hoặc theo số join points đã chọn.

**Câu 3.** Per-target aspect có luôn an toàn thread không?  
**Trả lời:** Không. Hai thread có thể đồng thời gọi cùng target và cùng aspect state.

**Câu 4.** Vì sao không để stopwatch start time trong field của một shared aspect?  
**Trả lời:** Calls đồng thời hoặc reentrant có thể ghi đè mốc, tính elapsed sai. Dùng local/per-invocation context.

**Câu 5.** Một aspect sở hữu socket. GC có phải là cơ chế dispose đủ tin cậy không?  
**Trả lời:** Không. Nên có lifecycle owner và đóng tường minh theo hợp đồng.

**Câu 6.** Tại sao `Target close B` trước `Target close A` trong ví dụ?  
**Trả lời:** Java try-with-resources đóng resources theo thứ tự ngược khai báo, khi ra khỏi scope.

**Câu 7.** Ngoại lệ target có làm `InvocationScope` không được đóng không?  
**Trả lời:** Không trong ví dụ: try-with-resources đảm bảo close khi rời khối theo cơ chế Java bình thường.

**Câu 8.** Có thể dùng một `AtomicBoolean closed` rồi ngay lập tức đóng sink trong ứng dụng nhiều thread không?  
**Trả lời:** Chưa đủ. Invocation đã bắt đầu có thể còn đang dùng sink; cần chính sách chờ in-flight và đồng bộ.

**Câu 9.** Với `CompletableFuture`, nên đóng context khi method return hay khi task complete?  
**Trả lời:** Phụ thuộc ý nghĩa scope. Nếu đo method return thì đóng khi return; nếu đo toàn task thì quản lý tới completion/cancellation.

**Câu 10.** `WeakHashMap<Target,Aspect>` có tự tránh leak nếu `Aspect` giữ mạnh `Target` không?  
**Trả lời:** Không chắc; value có thể giữ key reachable qua map. Cần xem toàn bộ đường tham chiếu.

**Câu 11.** Tại sao một class static holder có thể không phải singleton chung cả JVM?  
**Trả lời:** Class được định nghĩa riêng bởi những classloaders khác nhau có thể có static state riêng.

**Câu 12.** Làm sao chứng minh lifecycle đúng trong hệ AOP thật?  
**Trả lời:** Đếm creation/advice entry/disposal riêng, chạy integration tests qua đúng proxy/weaving, bao gồm success, throw, lazy init, concurrency, shutdown, async và bypass.

## 23. Tổng kết

**Vòng đời aspect không chỉ là lúc advice chạy.** Cần phân biệt rõ **thời điểm cài đặt pointcut/weaving**, **lúc tạo instance**, **lúc khởi tạo tài nguyên**, **số lần lời gọi kích hoạt advice**, và **lúc giải phóng scope/tài nguyên**. Các scope ứng dụng, per-target, per-invocation, per-control-flow hay stateless có điểm mạnh và rủi ro khác nhau; không có quy tắc khởi tạo/huỷ chung cho mọi engine AOP.

Thiết kế đúng phải xuất phát từ **ownership**: state per-invocation nằm ở phạm vi ngắn; per-target state được quản lý theo target; shared resources sống tới khi mọi consumer kết thúc. Cleanup cần tường minh và an toàn trước lỗi/đồng thời, không phó mặc cho GC. Với async, phân biệt method return và task completion; với weaving/proxy, kiểm chứng instance lifecycle thực tế và đường join point đã được can thiệp. **Bằng chứng tốt nhất là integration tests đo riêng creation, advice invocation và disposal, đồng thời xác minh result/exception và số lần nghiệp vụ không đổi ngoài hợp đồng**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-lifecycle-005">Chọn aspect không trạng thái hoặc có trạng thái</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn aspect không trạng thái hoặc có trạng thái trong vòng đời AOP

Khi thiết kế một **aspect** trong **Aspect-Oriented Programming (AOP – lập trình hướng khía cạnh)**, quyết định quan trọng không chỉ là **advice chạy ở join point nào**, mà còn là **aspect có giữ trạng thái giữa các lần advice được thực thi hay không**. Quyết định này ảnh hưởng trực tiếp tới phạm vi dùng chung, vòng đời đối tượng, tính an toàn đa luồng, khả năng mở rộng, kết quả kiểm thử và nguy cơ giữ dữ liệu quá lâu.

Quy tắc khởi đầu thường hữu ích: **ưu tiên aspect không trạng thái (stateless) cho những concern độc lập giữa các lời gọi**, và chỉ dùng **aspect có trạng thái (stateful)** khi có yêu cầu nghiệp vụ hoặc cơ chế rõ ràng phải nhớ thông tin giữa các sự kiện. Nếu cần trạng thái, phải xác định **trạng thái thuộc về ai, tồn tại trong bao lâu, được đồng bộ thế nào và được hủy khi nào**.

Đừng đồng nhất ba điều sau:

1. **Số instance của aspect** – dùng một instance chung, tạo theo target, theo đối tượng đang thực thi, theo luồng điều khiển hay theo một cơ chế khác.
2. **Tính có trạng thái của hành vi** – advice có giữ dữ liệu mutable sống qua nhiều lần gọi không?
3. **Vị trí thật của trạng thái** – field trong aspect, một service/cache bên ngoài, `ThreadLocal`, map keyed theo target hay biến cục bộ của một lần invocation.

Một aspect được cấu hình **chỉ có một instance** vẫn có thể hoàn toàn stateless. Ngược lại, một aspect chỉ có field `final` trỏ tới **đối tượng mutable dùng chung** vẫn có thể mang trạng thái gián tiếp. Vấn đề cốt lõi là **ngữ nghĩa dữ liệu và vòng đời**, không phải chỉ số lượng instance.

## 1. Nhắc lại: aspect, advice, join point và vòng đời

Trong AOP:

- **Aspect** là đơn vị tổ chức một concern cắt ngang, chẳng hạn tracing, validation, audit, retry hoặc cache.
- **Join point** là điểm chương trình mà mô hình AOP có thể can thiệp, ví dụ method call hoặc method execution khi được hỗ trợ.
- **Pointcut** xác định những join point phù hợp.
- **Advice** là đoạn hành vi chạy tại điểm được chọn: before, after, around hoặc biến thể khác tùy mô hình.
- **Weaving** kết hợp aspect/advice với chương trình bằng compiler, biến đổi binary, load-time hoặc một cơ chế trung gian phù hợp.
- **Aspect instance và lifecycle** mô tả khi nào đối tượng chứa advice/state được tạo, được gắn vào phạm vi nào, dùng chung với ai và khi nào kết thúc.

**Pointcut selection khác aspect instantiation.** Pointcut xác định nơi có thể chạy advice; cơ chế vòng đời xác định **instance nào** phục vụ lần thực thi ấy. Hai join point cùng match một pointcut có thể sử dụng cùng một instance hoặc hai instance khác nhau, tùy chính sách khởi tạo aspect.

Một hình dung đơn giản:

~~~text
Nhiều invocation cùng pointcut
          |
          v
   Chọn aspect instance
     /         |         \
    /          |          \
shared      per-target   per-flow
instance     instance    instance
    |           |           |
  advice      advice      advice
    |           |           |
  state?      state?      state?
~~~

Dấu hỏi là cố ý: **mỗi phạm vi instance đều có thể thiết kế theo hướng stateless hoặc stateful**, nếu mô hình cung cấp phạm vi đó. Không phải cứ per-target là bắt buộc có bộ đếm, hoặc cứ shared là bắt buộc có biến toàn cục.

## 2. Định nghĩa “không trạng thái” và “có trạng thái”

### 2.1 Aspect không trạng thái – stateless

Một advice stateless **không phụ thuộc vào dữ liệu mutable được nó giữ lại từ những invocation trước** để quyết định kết quả hiện tại. Nó có thể dùng:
- Tham số của invocation hiện tại.
- Chữ ký, target, ngữ cảnh join point hiện tại.
- Biến cục bộ trong advice.
- Giá trị cấu hình bất biến.
- Một dependency được sử dụng theo hợp đồng phù hợp, ví dụ clock hoặc emitter chỉ nhận sự kiện.

Giả mã:

~~~text
before each selected invocation:
    operation = safeOperationName(currentJoinPoint)
    recordEvent(operation, "START")
~~~

Không có bộ đếm hay `lastUser` tồn tại xuyên suốt các lần gọi. Các biến cục bộ được tạo lại cho mỗi invocation, nên không tự gây tranh chấp giữa hai thread chỉ vì cùng dùng một aspect instance.

**Lưu ý:** stateless **không nhất thiết là hàm thuần (pure function)**. Một advice có thể gửi log/metric (tác dụng phụ) mà bản thân không giữ trạng thái giữa các invocation. Việc service log ở bên ngoài có bộ đệm hoặc tài nguyên không làm biến cục bộ trong advice trở thành bộ đếm, nhưng dependency ấy vẫn có yêu cầu thread safety riêng.

### 2.2 Aspect có trạng thái – stateful

Một aspect có trạng thái lưu thông tin **vượt quá vòng đời của một invocation**, chẳng hạn:
- Số lần phương thức của một đối tượng đã được gọi.
- Một máy trạng thái nghiệp vụ tích lũy qua nhiều sự kiện.
- Token bucket/rate limit theo một chủ thể.
- Thời điểm lần cuối một tài nguyên được truy cập.
- Cache có vòng đời được quản lý.
- Số lần thất bại liên tiếp để quyết định circuit breaker.
- Trạng thái một phiên theo dõi hoặc một luồng điều khiển.

Ví dụ giả mã:

~~~text
state calls = 0

before each selected invocation:
    calls = calls + 1
    emit("calls", calls)
~~~

Đây là stateful vì kết quả invocation thứ hai phụ thuộc **lịch sử** của invocation thứ nhất. Nhưng code này **chưa bảo đảm an toàn đa luồng**: tăng một số nguyên là thao tác read-modify-write; nếu nhiều thread dùng cùng instance, có thể mất lượt cập nhật.

### 2.3 Trạng thái cục bộ theo invocation không làm aspect tự thành stateful

~~~text
around invocation:
    start = monotonicTimeNow()
    try:
        return proceed()
    finally:
        duration = monotonicTimeNow() - start
        emitLatency(duration)
~~~

`start` và `duration` thuộc **mỗi lần gọi**, dù có nhiều invocation chạy song song trên một instance aspect. Chúng không phải field sống qua nhiều lần gọi. Một timing advice có thể stateless dù nó sử dụng các giá trị thay đổi trong thân advice.

Phải tách:
- **Ephemeral invocation state**: cục bộ, kết thúc theo invocation.
- **Persistent aspect state**: duy trì qua các invocation.
- **External shared state**: trạng thái nằm ở một thành phần khác nhưng advice truy cập được.

## 3. Vì sao mặc định stateless thường dễ đúng hơn?

Một aspect stateless có lợi thế:

1. **Ít chia sẻ mutable state:** giảm race condition, lost updates và nhu cầu khóa.
2. **Dễ tái sử dụng:** một instance có thể phục vụ nhiều target/invocation nếu mô hình cho phép.
3. **Dễ kiểm thử:** kết quả ít phụ thuộc thứ tự chạy các test trước.
4. **Dễ mở rộng:** không cần đồng bộ hoặc chuyển trạng thái tích lũy giữa nhiều aspect instances.
5. **Vòng đời đơn giản:** không có state của user/session phải dọn khi target mất tham chiếu.
6. **Giảm rủi ro lộ dữ liệu:** không giữ tham số, token hoặc object liên quan cá nhân lâu hơn cần thiết.
7. **Ít ảnh hưởng đến reentrancy:** lời gọi advice lồng nhau không đè lên một field như `currentRequest`.
8. **Rõ ràng hơn khi retry:** mỗi invocation tạo local context riêng thay vì dùng một biến đếm/mốc thời gian toàn cục khó phân biệt.

Ví dụ concern thường hợp với stateless:
- Validation dựa trên dữ liệu invocation hiện tại.
- Ghi log an toàn bằng schema cố định.
- Đo thời gian bằng biến cục bộ `start`.
- Ghi một sự kiện audit độc lập vào kênh đã định nghĩa.
- Chuẩn hóa dữ liệu đầu vào theo một hàm không phụ thuộc lịch sử.
- Kiểm tra quyền **dựa trên danh tính đáng tin và policy hiện hành** thông qua một dịch vụ rõ ràng, thay vì ghi `lastAuthorizedUser` vào aspect.

**Ngoại lệ:** Một bộ ghi metrics bên ngoài vẫn có state để tổng hợp số liệu. Điều đó không bắt buộc logic advice giữ state riêng. Ta có thể để hệ thống metrics chuyên dụng quản lý aggregation, đồng bộ và retention.

## 4. Khi nào stateful là hợp lý?

Dùng aspect stateful khi **hợp đồng thực sự yêu cầu nhớ quá khứ** và có thể định nghĩa phạm vi phù hợp.

| Yêu cầu | Trạng thái cần thiết | Phạm vi gợi ý | Điều kiện thiết kế |
| --- | --- | --- | --- |
| Đếm lượt của mỗi target | Counter theo object | Per-target hoặc registry keyed theo target | Xác định identity, đồng thời và cleanup |
| Circuit breaker | Số lỗi, trạng thái CLOSED/OPEN/HALF_OPEN, mốc thời gian | Theo dependency/endpoint được bảo vệ | Đồng bộ chuyển trạng thái, giới hạn retry |
| Rate limiting | Token/đếm trong cửa sổ | Theo principal/tenant/resource tùy hợp đồng | Không dùng một counter chung sai phạm vi |
| Cache | Key → value và TTL | Phạm vi dữ liệu phù hợp | Quyền, consistency, eviction, đồng thời |
| Máy trạng thái workflow | Pha xử lý giữa nhiều sự kiện | Theo workflow/session/correlation | Vòng đời, timeout, thứ tự sự kiện |
| Dedup/idempotency | Key yêu cầu → trạng thái/kết quả | Theo operation/domain có bảo đảm | Chống race và trạng thái đang xử lý |
| Chống lặp lại hành vi | Dấu đã kiểm tra/lần xử lý trước | Theo object hoặc request logic | Tránh biến state thành quyền truy cập |
| Theo dõi thời gian trong một nested flow | Context theo flow | Per-control-flow hoặc context tường minh | Hỗ trợ nested, async propagation, cleanup |

**Phạm vi phải được chọn từ ý nghĩa của dữ liệu.** Ví dụ rate limit theo người dùng không thể dùng chung một counter cho tất cả người dùng rồi cho rằng “đã bảo vệ từng người”. Ngược lại, circuit breaker của một backend dùng chung nhiều caller có thể cần state **chia sẻ theo dependency**, không phải một bộ breaker riêng bị vô hiệu tác dụng cho từng caller.

## 5. Các mô hình vòng đời aspect thường gặp

AOP tổng quát có nhiều phương án; các tên/cú pháp kích hoạt chỉ có ở hệ thống hỗ trợ chúng.

### 5.1 Shared/single instance

Một instance phục vụ nhiều join point hoặc nhiều target:

~~~text
Target A ----              -> Shared aspect instance
Target B ----/
Target C ----/
~~~

- **Stateless + shared**: thường phù hợp log, validation, tracing thuần ngữ cảnh.
- **Stateful + shared**: hợp lý khi cố ý cần bộ đếm toàn cục, circuit breaker theo endpoint hoặc registry chia sẻ; nhưng phải thread-safe và phân vùng đúng dữ liệu.
- **Rủi ro:** field `currentUser`, `lastTarget`, `startTime` trong instance dùng chung rất dễ bị ghi đè bởi invocation khác.

Nếu dùng một instance chung cho audit, không nên có field lưu `mostRecentPassword` hay payload của user gần nhất. Ngoài nguy cơ rò dữ liệu, đây còn là thiết kế khó đồng bộ.

### 5.2 Per-target instance

Một aspect instance được gắn với **target object** có thể phục vụ nhiều invocation trên target đó:

~~~text
Target A ---> Aspect A [callsA, lastSuccessA]
Target B ---> Aspect B [callsB, lastSuccessB]
~~~

Phù hợp khi dữ liệu mô tả **lịch sử của từng target**. Lưu ý:
- Một target có thể được gọi đồng thời bởi nhiều thread. Per-target **không phải** per-thread.
- Cần xác định thế nào là "cùng target": identity hay logical ID?
- Nếu registry giữ key target quá lâu, target sẽ không được garbage collect.
- Nếu có nhiều proxy trỏ cùng target, phải quyết định dùng **một state chung** hay state riêng theo từng proxy.
- Khi target bị thay hoặc reload, state cũ có cần di chuyển hay được bỏ?

### 5.3 Per-executing-object hoặc per-caller

Ở những mô hình AOP phân biệt **`this` của join point** với target, một aspect có thể được gắn theo object đang thực thi. Nhưng ở **method call join point**, `this` phía caller có thể khác target là object nhận lời gọi. Ở **method execution** của một phương thức instance, this thường chính là object đang chạy phần thân.

Không đồng nhất **per-this** với **per-target** nếu ngữ nghĩa join point không bảo đảm hai object là một.

### 5.4 Per-control-flow hoặc theo một luồng xử lý logic

Một số mô hình AOP hỗ trợ instance theo **dynamic control flow**, ví dụ một chuỗi lời gọi lồng nhau hoặc một hoạt động nghiệp vụ.

~~~text
Flow 1: entry -> a -> b -> exit    Aspect state F1
Flow 2: entry -> c -> d -> exit    Aspect state F2
~~~

Trạng thái kiểu này phù hợp cho correlation hoặc theo dõi pha, nhưng phải xử lý **nested flow** và **phát sinh task bất đồng bộ**. Một Java thread có thể phục vụ nhiều request liên tiếp, còn một request có thể chuyển qua nhiều thread. Do đó **per-thread không tự đồng nghĩa per-request hoặc per-flow**.

### 5.5 Per-invocation và trạng thái ngắn hạn

Nếu chỉ cần thời gian bắt đầu hoặc số lần retry **trong cùng một invocation**, đặt trạng thái ở **biến cục bộ/closure của around advice** thường đơn giản hơn tạo aspect instance mới cho từng lời gọi.

Ví dụ:

~~~text
around:
    attempts = 0
    while attempts < maxAttempts:
        attempts++
        try:
            return proceed()
        catch transientError:
            continue
~~~

Đây là **giả mã**, chỉ áp dụng khi nền tảng AOP cho phép continuation được gọi lại theo cách phù hợp. Retry nhiều lần có thể lặp side effects; phải xem idempotency. Biến `attempts` cục bộ thuộc vòng đời một invocation, không chứng minh aspect object là stateful.

## 6. Sai lầm: dùng field của shared aspect làm biến tạm

Mẫu dễ sai:

~~~java
class BadTimingAdvice {
    long startedAtNanos;

    Object around(Proceed next) throws Throwable {
        startedAtNanos = System.nanoTime();
        try {
            return next.call();
        } finally {
            long elapsed = System.nanoTime() - startedAtNanos;
            // Có thể dùng mốc của invocation khác.
        }
    }

    interface Proceed {
        Object call() throws Throwable;
    }
}
~~~

Đây là **mảnh minh họa lỗi thiết kế**, không có chương trình `main`. Nếu một instance được nhiều thread hoặc lời gọi lồng nhau dùng chung, invocation B có thể ghi đè `startedAtNanos` giữa hai bước của invocation A. Kết quả đo A sẽ sai dù `System.nanoTime()` hợp lệ.

Cách tốt hơn dùng biến cục bộ:

~~~java
class BetterTimingAdvice {
    Object around(Proceed next) throws Throwable {
        long startedAtNanos = System.nanoTime();
        try {
            return next.call();
        } finally {
            long elapsed = System.nanoTime() - startedAtNanos;
            // Ghi elapsed qua một API nhận số liệu an toàn.
        }
    }

    interface Proceed {
        Object call() throws Throwable;
    }
}
~~~

Trạng thái `startedAtNanos` được giữ trong **stack frame của mỗi lần gọi**. Lời gọi lồng nhau có stack frame riêng; hai thread cũng có biến cục bộ riêng. Không cần khóa để bảo vệ biến này.

**Không được biến lời khuyên này thành tuyệt đối:** nếu mục đích là đo một hoạt động **trải qua nhiều invocation** thì phải có một context sống đủ lâu hơn, nhưng context nên có phạm vi được đặt tên rõ ràng.

## 7. Java 17 chạy độc lập: stateless tracing và stateful per-target

Ví dụ sau có:
- Hai target nghiệp vụ `Worker A` và `Worker B`.
- Một `TraceAdvice` **không có field mutable**, dùng chung cho cả A và B.
- Hai `CounterAdvice` riêng, lần lượt gắn với A và B. Mỗi counter lưu số **attempts qua wrapper** của target tương ứng.
- Một wrapper nhận invocation, chạy trace, tăng counter rồi chuyển tiếp tới target.
- Nhánh lỗi chứng minh counter đếm **lượt thử**, dù target không trả về thành công.
- Lời gọi trực tiếp tới target chứng minh **state chỉ được cập nhật khi đi qua ranh giới đã lắp advice**.

Đây là Java thuần mô phỏng vòng đời aspect bằng wrapper; nó không tự là một ngôn ngữ khai báo pointcut hoặc bộ bytecode weaver. Nội dung quan trọng là **state thuộc phạm vi nào**.

Tạo file `AspectStateLifecycleDemo.java`:

~~~java
public class AspectStateLifecycleDemo {
    interface Task {
        String run(String request);
    }

    @FunctionalInterface
    interface Proceed {
        String call();
    }

    static final class Worker implements Task {
        private final String name;

        Worker(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }

        @Override
        public String run(String request) {
            System.out.println("BUSINESS " + name + " " + request);
            if ("error".equals(request)) {
                throw new IllegalStateException("boom:" + name);
            }
            return name + ":" + request;
        }
    }

    // Một instance dùng chung. Không giữ state giữa các invocation.
    static final class TraceAdvice {
        String around(Worker target, Proceed next) {
            System.out.println("TRACE before " + target.name());
            try {
                String result = next.call();
                System.out.println("TRACE after " + target.name()
                        + " result=" + result);
                return result;
            } catch (RuntimeException error) {
                System.out.println("TRACE error " + target.name()
                        + " " + error.getClass().getSimpleName());
                throw error;
            }
        }
    }

    // Một instance trên mỗi target theo cách khởi tạo của demo.
    static final class CounterAdvice {
        private final String label;
        private int calls;

        CounterAdvice(String label) {
            this.label = label;
        }

        String around(Proceed next) {
            calls++;
            System.out.println("COUNTER " + label + " calls=" + calls);
            return next.call();
        }

        int calls() {
            return calls;
        }
    }

    static Task instrument(
            Worker target,
            CounterAdvice counter,
            TraceAdvice trace) {
        return request -> trace.around(
                target,
                () -> counter.around(() -> target.run(request)));
    }

    public static void main(String[] args) {
        Worker a = new Worker("A");
        Worker b = new Worker("B");
        TraceAdvice sharedTrace = new TraceAdvice();
        CounterAdvice aCounter = new CounterAdvice("A");
        CounterAdvice bCounter = new CounterAdvice("B");

        Task proxyA = instrument(a, aCounter, sharedTrace);
        Task proxyB = instrument(b, bCounter, sharedTrace);

        System.out.println("CASE first-A");
        System.out.println("RESULT " + proxyA.run("one"));

        System.out.println("CASE first-B");
        System.out.println("RESULT " + proxyB.run("two"));

        System.out.println("CASE second-A");
        System.out.println("RESULT " + proxyA.run("three"));

        System.out.println("STATE A=" + aCounter.calls()
                + " B=" + bCounter.calls());

        System.out.println("CASE failure-B");
        try {
            proxyB.run("error");
        } catch (IllegalStateException error) {
            System.out.println("CALLER " + error.getMessage());
        }

        System.out.println("STATE A=" + aCounter.calls()
                + " B=" + bCounter.calls());

        System.out.println("CASE direct-A");
        System.out.println("DIRECT " + a.run("bypass"));

        System.out.println("STATE A=" + aCounter.calls()
                + " B=" + bCounter.calls());
    }
}
~~~

Biên dịch và chạy bằng **JDK 17+**:

~~~bash
javac AspectStateLifecycleDemo.java
java AspectStateLifecycleDemo
~~~

Stdout chính xác của ví dụ:

~~~text
CASE first-A
TRACE before A
COUNTER A calls=1
BUSINESS A one
TRACE after A result=A:one
RESULT A:one
CASE first-B
TRACE before B
COUNTER B calls=1
BUSINESS B two
TRACE after B result=B:two
RESULT B:two
CASE second-A
TRACE before A
COUNTER A calls=2
BUSINESS A three
TRACE after A result=A:three
RESULT A:three
STATE A=2 B=1
CASE failure-B
TRACE before B
COUNTER B calls=2
BUSINESS B error
TRACE error B IllegalStateException
CALLER boom:B
STATE A=2 B=2
CASE direct-A
BUSINESS A bypass
DIRECT A:bypass
STATE A=2 B=2
~~~

### 7.1 Chứng minh phần stateless

`TraceAdvice` không có instance field mutable. Phương thức `around` nhận **target** và **continuation** của invocation hiện tại, viết sự kiện before, chờ kết quả rồi viết after/error. Một instance `sharedTrace` được cả `proxyA` và `proxyB` dùng mà không cần giữ `currentTarget` hay `lastResult` vào field.

Số lần trace in ra nhiều dòng không biến object này thành stateful. Nó **đang thực hiện tác dụng phụ** với `System.out`, nhưng nội dung bản thân `TraceAdvice` không cần nhớ lịch sử invocation để hoạt động.

### 7.2 Chứng minh phần stateful

`CounterAdvice` có field `calls` thay đổi sau mỗi lần qua wrapper. Hai instance được tạo độc lập:
- `aCounter` chỉ dùng trong wrapper của A.
- `bCounter` chỉ dùng trong wrapper của B.

Sau ba lần gọi bình thường: `A=2`, `B=1`. Lần B ném lỗi vẫn tăng đếm thành `B=2`, bởi code tăng trước khi gọi nghiệp vụ. Đây là **đếm attempts**, không phải số lần **thành công**.

Nếu muốn đếm thành công, phải tăng **sau khi `next.call()` trả về bình thường**, và làm rõ cùng tên metric đếm điều gì.

### 7.3 Vì sao nhánh direct không tăng state?

`a.run("bypass")` gọi trực tiếp target; nó không đi qua `proxyA`, nên không chạy `TraceAdvice` hoặc `CounterAdvice`. Điều này phản ánh **giới hạn của mô hình wrapper**, không phải một thuộc tính của mọi weaving ở method execution.

Nếu chính sách bắt buộc audit **mọi lần** chạy nghiệp vụ, chỉ đặt counter trong một proxy có thể bị bypass là chưa đủ. Cần quản lý mọi đường gọi hoặc chọn cơ chế can thiệp khác phù hợp.

### 7.4 Quan trọng: per-target không tự thread-safe

`CounterAdvice` ở ví dụ dùng `int calls` vì phần demo là **đơn luồng** và output cần xác định. Nếu nhiều thread gọi `proxyA.run(...)` đồng thời, `calls++` có thể bị mất lượt.

Một cách sửa cho phép đếm concurrent attempts trong cùng JVM là dùng `AtomicInteger`:

~~~java
import java.util.concurrent.atomic.AtomicInteger;

final class ThreadSafeCounter {
    private final AtomicInteger calls = new AtomicInteger();

    int incrementAndGet() {
        return calls.incrementAndGet();
    }

    int calls() {
        return calls.get();
    }
}
~~~

Đây là **mảnh khai báo riêng** về counter, không phải phần của file demo. Nó giải quyết **cập nhật counter nguyên tử trên một instance trong JVM**, không tự tạo trạng thái phân tán nhất quán giữa nhiều tiến trình. Cũng không giải quyết tự động logic phức tạp gồm nhiều field phải cập nhật cùng lúc.

## 8. An toàn đa luồng: chọn đúng loại trạng thái và đồng bộ

### 8.1 Shared mutable state là nguồn race condition

Ví dụ tăng biến đếm bằng `calls++` gồm các bước đọc–cộng–ghi không nguyên tử theo nghĩa cần thiết cho counter concurrent. Nếu hai invocation cùng đọc giá trị 5, cả hai có thể ghi 6; một lượt bị mất.

Với state đơn giản, `AtomicInteger`, `AtomicLong` hoặc `LongAdder` có thể phù hợp tùy yêu cầu:
- **AtomicInteger/AtomicLong** hỗ trợ cập nhật nguyên tử và đọc giá trị có ngữ nghĩa rõ.
- **LongAdder** có thể giảm contention khi tăng nhiều, nhưng `sum()` dưới cạnh tranh không phải snapshot nguyên tử toàn bộ cập nhật; không dùng nó như một bộ phát số thứ tự chính xác.
- **ConcurrentHashMap** hữu ích cho registry concurrent, nhưng không tự biến mọi chuỗi nhiều thao tác trên nhiều phần tử thành transaction.

Nếu máy trạng thái có nhiều bước (ví dụ circuit breaker), cần giữ **bất biến toàn trạng thái**. Chỉ đổi từng field thành `volatile` không đảm bảo nhóm field cập nhật nguyên tử.

### 8.2 Per-target vẫn có thể bị nhiều thread chạm tới

Một target có thể là service dùng chung. Nếu 100 thread gọi cùng target, **một CounterAdvice gắn theo target** vẫn nhận 100 concurrent invocations. Vì vậy per-target là **phạm vi logic dữ liệu**, không phải một kỹ thuật đồng bộ.

### 8.3 `volatile` không thay phép tăng nguyên tử

`volatile int counter` cung cấp các bảo đảm visibility/ordering nhất định, nhưng `counter++` vẫn không thành thao tác read-modify-write nguyên tử. Chọn primitive đồng bộ theo **hợp đồng**: đếm chính xác, thống kê gần đúng, cập nhật nhiều biến hay throughput.

### 8.4 Trạng thái bất biến và snapshot

Nếu một aspect cần giữ cấu hình động, có thể dùng object `Policy` bất biến được thay bằng `AtomicReference<Policy>`. Mỗi invocation đọc một snapshot đầu vào nhất quán. Điều này thường an toàn và dễ kiểm thử hơn thay từng field cấu hình mutable.

Tuy nhiên, `AtomicReference` chỉ bảo vệ việc thay **tham chiếu policy**; nó không làm những object mutable mà policy trỏ tới tự trở thành bất biến.

## 9. Trạng thái theo thread, control flow và bất đồng bộ

### 9.1 Vì sao `ThreadLocal` hấp dẫn nhưng rủi ro?

`ThreadLocal<T>` cung cấp một giá trị riêng theo **Java thread**, không theo user hay request logic. Có thể dùng cho một số ngữ cảnh đồng bộ đã được quản lý; nhưng trong thread pool, thread được tái sử dụng cho nhiều request. Nếu quên dọn context, dữ liệu của request trước có thể còn tồn tại ở request sau.

Mẫu tối thiểu:

~~~java
static final ThreadLocal<String> TRACE =
        new ThreadLocal<>();

static void handle(String traceId, Runnable next) {
    String previous = TRACE.get();
    TRACE.set(traceId);
    try {
        next.run();
    } finally {
        if (previous == null) {
            TRACE.remove();
        } else {
            TRACE.set(previous);
        }
    }
}
~~~

Đây là mảnh minh họa có ý thức **khôi phục giá trị trước** cho nested calls. Nó không giải quyết tự động propagation sang thread khác; cũng không cho phép lưu token hoặc bí mật không cần thiết trong TRACE.

### 9.2 Async làm phạm vi flow phức tạp hơn

Một request có thể bắt đầu trên thread T1, chuyển sang executor trên T2, rồi tiếp tục trên T3. Một aspect dùng `ThreadLocal` trên T1 không thể mặc định T2 và T3 nhận cùng context.

Cách tiếp cận an toàn:
- Chỉ truyền **context tối thiểu** có mục đích rõ ràng.
- Dùng cấu trúc bất biến và cơ chế truyền context được hỗ trợ.
- Định nghĩa nơi bắt đầu/kết thúc hoạt động logic, cancellation và cleanup.
- Không giữ trực tiếp request/target lớn chỉ để lấy một correlation ID.
- Với nested hoặc recursive flows, dùng stack/scope thay vì một field `currentFlow` bị ghi đè.

### 9.3 Thread-per-request không phải định nghĩa AOP

Một hệ thống có thể dùng event loop, thread pool, virtual threads hoặc mô hình khác. Ngữ nghĩa **per-control-flow** trong AOP không nhất thiết đồng nghĩa **per-thread**; không chuyển đổi hai khái niệm chỉ vì code chạy đúng trong một demo đơn luồng.

## 10. Vòng đời, tham chiếu và nguy cơ rò bộ nhớ

Một aspect stateful có thể giữ tham chiếu tới target, principal hoặc kết quả trong registry:

~~~text
Shared aspect
    |
    v
Map<Target, State>
    |
    +--> Target A -> Counter A
    +--> Target B -> Counter B
    +--> Target C -> Counter C
~~~

Nếu map giữ **strong references** tới các target đã hết vòng đời, GC không thể thu hồi chúng cho đến khi entry được xóa. Đây là một nguy cơ **memory leak** đặc biệt khi target được tạo nhiều hoặc có dữ liệu lớn.

Một số giải pháp tùy tình huống:
- Gắn state trong object sở hữu có lifecycle rõ.
- Xóa entry khi target được release/dispose.
- Dùng registry có eviction/TTL nếu dữ liệu được phép hết hạn.
- Cân nhắc weak-key association nếu semantics cho phép, nhưng hiểu cách weak reference, equality và vòng đời hoạt động.
- Không dùng `WeakHashMap` rồi mặc định an toàn nếu **value giữ tham chiếu mạnh trở lại key**, khiến key vẫn không thể được thu hồi.
- Đặt giới hạn kích thước và cơ chế dọn dẹp có thể quan sát.

### 10.1 Tránh nhầm identity với logical key

Nếu hai object `CustomerAccount` đại diện cùng một logical account, aspect stateful per-instance sẽ có **hai state khác nhau**. Nếu yêu cầu là rate limit hoặc breaker theo **account ID**, gắn state theo object identity có thể là phạm vi sai.

Ngược lại, hai target objects bằng nhau theo `equals` không nhất thiết là cùng một đối tượng có vòng đời. `HashMap` dùng equals/hashCode; `IdentityHashMap` dùng identity. Lựa chọn phải theo **ngữ nghĩa nghiệp vụ** và quy tắc lifecycle, không theo thói quen dùng loại Map quen thuộc.

### 10.2 Khi nào phải xóa state?

Đặt câu hỏi ngay lúc thiết kế:
- Sau một invocation?
- Sau khi target bị hủy?
- Sau khi workflow hoàn tất?
- Khi session hết hạn?
- Khi tenant/logout/permission thay đổi?
- Sau một khoảng TTL?
- Khi phiên bản aspect mới được triển khai?

Nếu không thể mô tả **thời điểm kết thúc**, nguy cơ state tồn tại lâu hơn hợp đồng là rất lớn.

## 11. Tác động của precedence, short-circuit và retry lên state

Một aspect stateful chỉ quan sát những sự kiện **đi tới nó**.

Ví dụ:

~~~text
Cache(Counter(Target))
~~~

Nếu Cache ở ngoài và có cache hit mà không gọi continuation, Counter bên trong **không tăng**. Nếu mục đích là đếm **mọi yêu cầu**, cấu trúc này sai phạm vi.

~~~text
Counter(Cache(Target))
~~~

Ở cấu trúc thứ hai, Counter ở ngoài nhìn thấy cả cache hit, giả sử mọi caller đều đi qua outer wrapper.

**Không có bộ đếm nào “đúng” tự thân**: cần định nghĩa đang đếm requests, attempts, method executions, successes, failures hay cache misses.

### 11.1 Retry làm số lượng lần thực thi khác số yêu cầu

~~~text
Retry(Counter(Target))
Counter(Retry(Target))
~~~

Nếu Retry gọi continuation nhiều lần, Counter nằm trong có thể tăng theo mỗi attempt; Counter ở ngoài chỉ tăng một lần cho toàn lời gọi logic. Nhưng ngữ nghĩa chính xác còn tùy một cơ chế AOP có cho gọi lại `proceed` và ranh giới transaction/exception như thế nào.

### 11.2 Counter trước hay sau `proceed`?

~~~text
before proceed: attempts++
after successful proceed: successes++
on exception: failures++
finally: completedInvocations++
~~~

Đây là **giả mã**. Nếu một outer advice short-circuit, counter bên trong không được vào; nếu một inner advice short-circuit thành công, counter ngoài có thể vẫn coi đó là success. Cần đặt tên metric theo **phạm vi quan sát**.

### 11.3 State của security advice

Không lưu `lastAuthorizedUser` hoặc một boolean `authorized=true` dùng chung để quyết định lời gọi tiếp theo. Quyền thường phụ thuộc **chủ thể đã xác thực, tài nguyên, hành động và thời điểm**. Nếu dùng cache kết quả phân quyền, cần khóa đầy đủ, timeout/revocation phù hợp và cơ chế chống nhầm người dùng/tenant. Một stateful security aspect thiết kế sai có thể trở thành lỗ hổng nghiêm trọng.

## 12. State có thể nằm ngoài aspect

Một cách thiết kế khác:

~~~text
Stateless advice
      |
      +--> metrics recorder (thread-safe)
      |
      +--> rate limit store (theo key được duyệt)
      |
      +--> correlation provider (context tối thiểu)
~~~

Advice không giữ state theo user/target dưới dạng field mutable. Nó **ủy quyền việc quản lý state** cho thành phần chuyên trách có hợp đồng: API thread-safe, TTL, key scope, concurrency, đo đạc và cleanup.

Cách này thường tốt cho state chia sẻ nhiều instance hoặc nhiều process. Nhưng nói "aspect stateless" **không loại bỏ** yêu cầu kiểm thử độ nhất quán, tính sẵn sàng và bảo mật của store bên ngoài.

### 12.1 Trạng thái trong nhiều JVM

Một `AtomicInteger` trong một JVM chỉ đếm các lời gọi đi qua đúng instance đó. Nếu triển khai bốn process, có thể có bốn counter độc lập. Nếu yêu cầu nghiệp vụ là giới hạn **toàn hệ thống**, phải dùng một giải pháp đồng bộ/distributed store phù hợp, với quy tắc consistency và thất bại được định nghĩa.

Không cố thay điều này bằng biến `static` vì biến static cũng chỉ sống trong phạm vi class loader/JVM liên quan; nó không tự chia sẻ giữa máy hoặc tiến trình.

### 12.2 Cache không phải cứ có state là xấu

Cache stateful có thể tăng hiệu năng đáng kể nếu dữ liệu **được phép tái sử dụng**. Điều kiện tiên quyết gồm:
- Khóa cache đầy đủ theo args, tenant, quyền, version và môi trường khi cần.
- Chính sách TTL/invalidation.
- Chống cache stampede khi nhiều miss đồng thời.
- Không trả dữ liệu stale nếu hợp đồng đòi consistency chặt.
- Giới hạn bộ nhớ/eviction.
- Không bỏ qua một advice authorization nằm bên trong khi cache hit.

Stateful là một **công cụ**, không phải lỗi mặc định. Lỗi là dùng state sai phạm vi hoặc thiếu cơ chế quản lý.

## 13. Vòng đời aspect khi thay đổi cấu hình hoặc phiên bản

Với aspect stateless, thay định nghĩa advice thường tập trung vào **tập join point, precedence và hành vi mới**. Với aspect stateful, còn cần xác định:

1. Instance cũ có tiếp tục sống sau khi deploy/reload hay không?
2. Trạng thái cũ có tương thích kiểu và ý nghĩa với aspect mới không?
3. Counter/cache/policy cần reset, migrate hay giữ nguyên?
4. Những active invocations đang dùng state nào?
5. Class loader cũ có thể bị giữ sống bởi aspect registry hoặc ThreadLocal không?
6. Bảo mật có đòi xóa state gắn với user/session ngay khi logout hoặc revoke không?
7. Có giai đoạn triển khai hai phiên bản aspect đồng thời không?

Không nên mặc định sửa source aspect trong repository là JVM đã cập nhật state. Compile-time/post-compile weaving thường tạo artifact mới; load-time weaving thường áp dụng tại thời điểm class được nạp; runtime proxy có thể đọc config động nếu được thiết kế vậy. **Cập nhật code và chuyển đổi state** là hai bài toán riêng.

## 14. Bảng lựa chọn thực tế

| Tình huống | Phương án nên ưu tiên | Lý do | Kiểm tra thêm |
| --- | --- | --- | --- |
| Ghi log bắt đầu/kết thúc mỗi call | Stateless advice | Không cần nhớ lịch sử | Không log dữ liệu nhạy cảm |
| Đo latency mỗi invocation | Stateless với biến local | Mốc thời gian thuộc từng call | Exception/finally, monotonic clock |
| Đếm attempts theo target | Stateful per-target | Lịch sử gắn target | Thread safety, identity, cleanup |
| Đếm tổng requests theo ứng dụng | Stateless advice + metrics backend, hoặc shared counter có kiểm soát | Aggregation có scope riêng | Multi-JVM, request vs attempt |
| Circuit breaker theo dependency | Stateful theo dependency | Cần trạng thái xuyên nhiều call | Atomic transitions, timeout, half-open |
| Tương quan một request async | Context tường minh/per-logical-flow | Thread có thể thay đổi | Context propagation và cleanup |
| Authorization từng invocation | Stateless kiểm tra policy hiện tại | Tránh nhầm trạng thái user | Trusted identity, tất cả đường vào |
| Cache kết quả đọc | Stateful cache chuyên trách | Cần lưu giá trị giữa call | Key scope, TTL, security, memory |
| Retry limit trong một around invocation | Biến local `attempts` | State chỉ sống trong một call | Side effects, idempotency |
| Giới hạn toàn cụm theo tenant | Shared state store đúng domain | Một instance aspect không đủ | Distributed consistency và failure modes |
| Theo dõi một workflow phức tạp | Stateful theo workflow ID/lifecycle | Trạng thái qua nhiều sự kiện | TTL, persistence, transaction và replay |

### 14.1 Quy tắc quyết định theo thứ tự

**Câu 1:** Có cần nhớ kết quả từ lần gọi trước không? Nếu **không**, ưu tiên stateless.

**Câu 2:** Nếu có, state thuộc **một invocation, target, caller, flow, tenant, dependency hay toàn hệ thống**? Đặt scope theo câu trả lời này.

**Câu 3:** Có nhiều thread/process cùng truy cập không? Chọn đồng bộ và consistency theo hợp đồng.

**Câu 4:** State kết thúc khi nào? Thiết kế cleanup, TTL hoặc lifecycle event.

**Câu 5:** Nếu advice được bypass hoặc short-circuit, state có còn phản ánh đúng đơn vị đo không? Kiểm tra vị trí trong chuỗi.

**Câu 6:** State có thông tin nhạy cảm không? Tối thiểu hóa và bảo vệ quyền truy cập, retention và lưu trữ.

Nếu một trong các câu trên chưa có câu trả lời, việc thêm field vào aspect thường là giải pháp quá sớm.

## 15. Kiểm thử stateless và stateful

Một chiến lược kiểm thử cần phản ánh đúng **phạm vi state**, **thứ tự thực thi** và **bộ dệt/proxy** đang được dùng.

| Phép thử | Stateless | Stateful |
| --- | --- | --- |
| Hai invocation liên tiếp | Không bị ảnh hưởng do state riêng từ invocation trước | Kết quả/counter biến đổi đúng theo lịch sử |
| Hai target độc lập | Không vô tình giữ dữ liệu target cũ | Trạng thái phải tách hay chia theo thiết kế |
| Đồng thời trên một target | Không dùng chung field tạm | Không lost updates/race, giữ invariant |
| Invocation lồng nhau | Local state của frame riêng | Không bị ghi đè state flow ngoài |
| Exception trước/sau `proceed` | Cleanup/log error đúng | Counter success/failure đúng đơn vị |
| Cache hit/short-circuit ngoài | Biết advice nào không được vào | Counters không bị hiểu nhầm là requests |
| Retry nhiều attempt | Local attempt count đúng | State shared không tăng ngoài ý định |
| Direct-call bypass proxy | Biết giới hạn coverage | State chỉ tính những call được instrument |
| Garbage collection/cleanup | Không giữ object không cần | Registry, ThreadLocal, cache được giải phóng |
| Redeploy/reload | Advice mới chạy đúng phiên bản | State reset/migrate theo policy |
| Nhiều JVM | Advice hoạt động ở từng process | State phân tán có quy tắc rõ |
| Sensitive args | Không ghi raw dữ liệu | Không lưu secret qua vòng đời dài |

### 15.1 Đảm bảo số lần đúng hơn dòng log

Nếu đang kiểm tra counter, assert chính **giá trị counter**, không chỉ đọc stdout. Log có thể bị sampling hoặc mất ở exporter. Với async/concurrent, kết quả xuất dòng có thể xen kẽ; nên kiểm tra bất biến cuối hoặc event ID/correlation thay vì dựa vào thứ tự print của nhiều thread.

### 15.2 Dùng test độc lập để tránh order dependence

Một test stateful có thể pass khi chạy riêng, nhưng fail khi chạy sau test khác nếu aspect instance dùng chung chưa reset state. Hãy:
- Tạo instance state mới cho mỗi test khi hợp đồng cho phép.
- Có cơ chế reset/cleanup rõ cho shared state.
- Kiểm tra cả một chuỗi nhiều test để phát hiện state leak.
- Không sửa test cho phụ thuộc vào thứ tự chạy của framework test runner.

## 16. Những sai lầm phổ biến

1. **“Singleton aspect là stateful.”** Sai: số instance và có state là hai thuộc tính khác nhau.
2. **“Aspect chỉ có field `final` chắc chắn stateless.”** Sai nếu field trỏ vào object mutable chứa state.
3. **“Biến local làm aspect stateful.”** Không nếu biến thuộc riêng từng invocation và không sống qua call.
4. **“Per-target thì không có race.”** Sai: một target thường được gọi đồng thời.
5. **“Dùng `volatile` là tăng counter an toàn.”** `counter++` vẫn không nguyên tử.
6. **“`ThreadLocal` đồng nghĩa per-request.”** Sai khi dùng thread pool hoặc async.
7. **“State của target chính là state của aspect.”** Hai loại state có thể liên quan nhưng khác nơi sở hữu và vòng đời.
8. **“Đếm sau `proceed` là đếm mọi lời gọi.”** Nó có thể chỉ đếm successes.
9. **“Đếm trước `proceed` là đếm mọi request từ người dùng.”** Chỉ đếm những call thực sự đi tới advice.
10. **“Một shared counter phản ánh tổng toàn cụm.”** Sai khi có nhiều JVM/instance.
11. **“Dùng map keyed theo target không cần cleanup.”** Có thể giữ strong references khiến target không được GC.
12. **“Weak key tự động giải quyết mọi leak.”** Value có thể tham chiếu lại key hoặc dữ liệu còn bị giữ ở nơi khác.
13. **“Cache hit bên ngoài không ảnh hưởng state của advice bên trong.”** Nó có thể skip phần bên trong hoàn toàn.
14. **“Stateful luôn tốt hơn vì có thêm thông tin.”** Càng nhiều state càng có chi phí đồng bộ, retention, memory và bảo mật.
15. **“Stateless nghĩa là không có side effect.”** Một advice stateless vẫn có thể ghi log và gửi metric.
16. **“Thay aspect code là state cũ tự chuyển.”** State migration/reset là vấn đề riêng khi đổi phiên bản.

## 17. Câu hỏi ôn tập

**Câu 1.** Một aspect có một instance dùng chung và một phương thức before chỉ in tên thao tác từ invocation hiện tại. Nó stateless hay stateful?

**Đáp:** stateless, nếu không giữ giá trị mutable của invocation này để dùng cho lần sau. Việc dùng chung một instance không biến nó thành stateful.

**Câu 2.** Một around advice có biến cục bộ `long startedAt` để đo thời gian; có cần tạo aspect per-invocation không?

**Đáp:** không nhất thiết. Biến cục bộ đã phân biệt từng invocation; một aspect instance có thể xử lý nhiều lời gọi khi các dependency của nó an toàn.

**Câu 3.** Một aspect per-target giữ `int calls`; 10 thread cùng gọi target. Số đếm có chắc chính xác không?

**Đáp:** không nếu tăng bằng `calls++` không đồng bộ. Per-target là phạm vi lưu state, không cung cấp thread safety.

**Câu 4.** Một aspect lưu `lastAuthorizedUser` để kiểm soát quyền cho lần gọi kế tiếp. Vấn đề gì?

**Đáp:** dữ liệu của chủ thể này có thể bị dùng nhầm cho chủ thể khác, gây vi phạm bảo mật. Kiểm tra quyền phải dựa vào identity đáng tin và tài nguyên của chính invocation hiện tại; nếu cache quyền, cần scope và invalidation rõ.

**Câu 5.** `Counter(Cache(Target))` và `Cache(Counter(Target))` đếm cùng một số lần khi có cache hit?

**Đáp:** không nhất thiết. Khi Cache ở ngoài và trả hit, Counter bên trong không chạy.

**Câu 6.** Dùng `ThreadLocal` trong aspect per-thread có bảo đảm trạng thái đi theo một workflow bất đồng bộ qua thread pool không?

**Đáp:** không. Phải truyền context phù hợp một cách tường minh hoặc dùng cơ chế propagation được hỗ trợ, đồng thời cleanup khi hoàn tất.

**Câu 7.** Một registry `Map<Target, State>` giữ state của object đã hết sử dụng có thể gây gì?

**Đáp:** giữ strong reference làm object không được GC, gây memory leak. Cần lifecycle/eviction/disposal hoặc weak association được thiết kế đúng.

**Câu 8.** Khi chạy nhiều JVM, `AtomicInteger` trong một shared aspect có phản ánh tổng số requests toàn hệ thống không?

**Đáp:** không. Nó chỉ phản ánh phạm vi instance/process liên quan. Nếu cần tổng toàn cụm phải có cơ chế aggregation hoặc state chia sẻ được kiểm soát.

## 18. Tài liệu tham khảo

- [Java SE 17 – AtomicInteger](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html): bộ đếm nguyên tử trên một instance.
- [Java SE 17 – ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): trạng thái theo thread và phương thức `remove()`.
- [Java SE 17 – WeakHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html): weak keys và lưu ý value tham chiếu key.
- [Java SE 17 – AtomicReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html): thay một cấu hình bất biến theo snapshot.
- [Java SE 17 – ConcurrentHashMap](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html): bản đồ concurrent và thao tác phối hợp.
- [AspectJ Programming Guide – Aspect Instantiation](https://eclipse.dev/aspectj/doc/released/progguide/semantics-aspects.html): ví dụ về các phạm vi instantiation aspect; cú pháp cụ thể của công cụ chỉ để tham khảo, không bắt buộc cho mô hình AOP tổng quát.

**Kết luận:** chọn **stateless** nếu advice chỉ cần ngữ cảnh của invocation hiện tại; chọn **stateful** khi cần ghi nhớ một lịch sử có ý nghĩa và quản lý được vòng đời. Sau đó xác định **scope thực tế** (shared/per-target/per-executing-object/per-flow/ngoài aspect), **thread safety**, **retention/cleanup**, **coverage của advice** và **ý nghĩa số liệu**. Câu hỏi đúng không phải “aspect có field hay không?”, mà là **“ai sở hữu state, ai có thể thay đổi nó, nó được dùng cho những lần gọi nào và khi nào nó hết hiệu lực?”**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-lifecycle-006">Quan hệ giữa vòng đời aspect và đối tượng được can thiệp</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ giữa vòng đời aspect và đối tượng được can thiệp

## 1. Tư duy cốt lõi

Trong **Aspect-Oriented Programming (AOP)**, aspect mô tả hoặc hiện thực một mối quan tâm xuyên suốt (cross-cutting concern): logging, metrics, tracing, chính sách thực thi, đo thời gian, kiểm soát tài nguyên hay xử lý lỗi ở những **join point** được pointcut chọn. **Đối tượng được can thiệp** (target object) là đối tượng mà hoạt động của nó được quan sát hoặc bao quanh theo cơ chế AOP đang dùng.

Một câu hỏi dễ bị bỏ qua là:

> **Một aspect instance tồn tại bao lâu, nó được dùng chung cho những target nào, trạng thái của nó có tách biệt với từng lần gọi không, và ai chịu trách nhiệm giải phóng khi target hết vòng đời?**

Không có câu trả lời chung cho mọi công cụ AOP. Có hệ thống dùng một aspect instance cho nhiều target; có hệ thống hỗ trợ liên kết một aspect instance theo target/receiver; có hệ thống chỉ quản lý trạng thái trong một invocation; cũng có mô hình gắn trạng thái theo luồng điều khiển hoặc ngữ cảnh thực thi. Thậm chí một aspect khai báo một lần có thể được **dệt vào nhiều join point** mà không đòi hỏi tạo một object aspect Java độc lập cho mỗi lần kích hoạt.

### Mục tiêu học tập

Sau bài học, bạn sẽ:

1. Phân biệt **aspect definition**, **aspect instance**, **target**, **proxy**, **advice invocation** và **state của mỗi lượt gọi**.
2. Hiểu sự khác nhau giữa vòng đời **application/module**, **classloader**, **proxy**, **target**, **thread**, **control-flow scope** và **một invocation**.
3. Phân tích những mô hình scope thường gặp: **shared/per-system**, **per-target**, **per-receiver**, **per-control-flow**, **per-call/per-invocation** mà không mặc định mọi engine hỗ trợ chúng.
4. Biết vì sao **số aspect instances**, **số target objects**, **số proxy objects** và **số advice executions** không nhất thiết bằng nhau.
5. Hiểu ảnh hưởng đến **state, thread safety, reentrancy, memory retention, cleanup, async context, exceptions và kiểm thử**.
6. So sánh vòng đời khi dùng **proxy/decorator** với **source/bytecode weaving**.
7. Chạy một ví dụ **Java 17 tự chứa** mô phỏng shared aspect state, per-target aspect state và per-invocation state bằng JDK dynamic proxy.
8. Thiết kế một chính sách ownership rõ ràng: instance nào được tạo lúc nào, ai giữ reference, state được dọn lúc nào và điều gì được bảo đảm khi target dừng hoạt động.

**Phạm vi:** Đây là kiến thức AOP cơ bản và nâng cao **nói chung**, không suy diễn một cơ chế instantiation/lifecycle cụ thể thành chuẩn bắt buộc mọi công cụ tuân thủ.

## 2. Sáu thực thể cần phân biệt

| Thực thể | Bản chất | Có nhất thiết sống cùng target? |
| --- | --- | --- |
| **Aspect definition** | Khai báo loại hành vi và pointcut/advice, có thể là class, cấu hình hoặc mô tả dùng lúc build. | Không. |
| **Aspect instance** | Một hiện thân runtime giữ logic/state của aspect nếu mô hình cần instance. | Không. Có thể sống lâu hơn/ngắn hơn/nhiều target dùng chung. |
| **Target object** | Đối tượng thực hiện công việc chính. | Là đối tượng có vòng đời riêng của ứng dụng. |
| **Proxy/wrapper** | Đối tượng trung gian nhận lời gọi, có thể giữ target và interceptors. | Có thể có cùng hoặc khác vòng đời với target. |
| **Advice execution** | Một lần chạy hành vi advice khi có join-point occurrence phù hợp. | Có thể xảy ra nhiều lần trong đời một target. |
| **Per-invocation context** | State tạm như start time, trace ID, argument snapshot dùng cho một lượt xử lý. | Thường chỉ sống đến khi lần xử lý kết thúc; async có thể kéo dài đến task completion. |

Ví dụ: ứng dụng có **2 target**, **2 proxy**, **1 aspect instance dùng chung** và **1.000 advice executions** trong một phút. Đây không hề mâu thuẫn. Con số 1.000 là **số lần advice được thực thi**, không phải số lần tạo aspect.

### 2.1. Khai báo aspect không đồng nghĩa đã có runtime instance

Một hệ thống có thể phân tích pointcut ở compile time và chèn logic vào bytecode; một hệ thống khác tạo interceptors ở runtime; một hệ thống khác lại sinh wrapper classes. Do vậy các mốc “aspect được khai báo”, “aspect được đăng ký”, “instance được tạo”, “target được weave” và “advice được chạy” có thể khác nhau.

Không mặc định **đăng ký aspect một lần** nghĩa là đã tạo **một instance cho từng target**. Cũng không mặc định mọi lúc chạy advice cần cấp phát một instance aspect mới.

### 2.2. Aspect không phải target

Aspect thường cung cấp **chính sách xuyên suốt**, không phải bản sao của đối tượng nghiệp vụ. Nếu target bị hủy/không còn reachable, một aspect dùng chung vẫn có thể tiếp tục phục vụ những target khác. Ngược lại, một aspect được gắn riêng với target có thể không còn lý do tồn tại khi target và mapping liên kết của nó được giải phóng.

### 2.3. Interception scope không phải instance scope

Một pointcut rất rộng có thể chọn hàng trăm method, nhưng engine vẫn dùng **một aspect instance** để phục vụ tất cả. Một pointcut hẹp có thể chọn chỉ một method, nhưng method đó được gọi đồng thời bởi nhiều target và có thể cần nhiều state instances. Phân biệt:
- **Điểm nào được chọn?** Pointcut/join-point scope.
- **Aspect state thuộc về ai?** Instantiation/association scope.
- **Sự kiện nào thật sự xảy ra?** Runtime occurrence.
- **State được lưu trong bao lâu?** Lifetime/ownership.

## 3. Những vòng đời liên quan

### 3.1. Vòng đời của application hoặc module

Một aspect instance được tạo lúc application/module khởi tạo có thể phục vụ đến khi module shutdown. Nó thường phù hợp cho hành vi **stateless** hoặc state được đồng bộ hóa/giới hạn hợp lý như counters, configuration snapshot hay thread-safe metrics aggregator.

Nếu aspect giữ reference đến object request/target ngắn hạn trong một field sống suốt ứng dụng, nó có thể vô tình **kéo dài vòng đời** object ấy. Vòng đời dài có lợi cho việc tái sử dụng logic, nhưng yêu cầu kỷ luật state.

### 3.2. Vòng đời của classloader

Một engine có thể dùng cache metadata, aspect instances hoặc registry theo classloader. Với plugin/hot reload, classloader cũ có thể cần được thu hồi; một registry toàn cục giữ `Class`, `Method`, aspect instance hoặc proxy của module cũ có thể góp phần ngăn điều đó xảy ra.

Vì thế “singleton” cần làm rõ singleton **trong phạm vi nào**: một process, một module, một classloader, một registry hay một cấu hình runtime. Không được suy rằng singleton có nghĩa duy nhất trên mọi classloader/process.

### 3.3. Vòng đời của target

Một target có thể được tạo theo request, theo một session, theo một worker hoặc sống lâu như một service. Nếu mô hình AOP gắn aspect **theo target**, số aspect instances có thể tăng khi số target tăng. Tuy nhiên, việc khởi tạo lúc nào và hủy lúc nào còn phụ thuộc cách association và ownership được hiện thực.

### 3.4. Vòng đời của proxy

Một proxy có thể chứa tham chiếu mạnh tới target và một danh sách interceptors. Nếu proxy sống lâu nhưng target về mặt nghiệp vụ đã hết hạn, target vẫn có thể **reachable** qua proxy. Đổi lại, khi proxy chỉ là wrapper dùng trong cùng service lifetime, đó có thể là ownership hoàn toàn hợp lệ.

“Proxy đã được tạo” không đồng nghĩa target body bị biến đổi, và “proxy không còn được dùng” không đảm bảo target được GC ngay nếu còn nơi khác giữ reference.

### 3.5. Vòng đời của thread/control flow

Một aspect hoặc context có thể được tổ chức theo thread hay luồng điều khiển. Đây là hai khái niệm **khác nhau**: một thread pool thread có thể xử lý nhiều requests, và một logical async task có thể chuyển qua nhiều thread. State theo thread không tự trở thành state theo logical request.

### 3.6. Vòng đời của một lần gọi

Một `around advice` có thể tạo biến local `startNanos` cho từng invocation; khi `proceed()` trả hoặc ném lỗi, state tạm thường không còn cần. Dùng local state tránh trộn thời gian bắt đầu giữa các lời gọi đồng thời và không cần thêm aspect instance cho mỗi lượt.

Nếu method trả một future còn pending, **invocation lifetime** có thể kết thúc trước **task lifetime**. State quan sát completion có thể phải sống lâu hơn lời gọi, nhưng chỉ nên giữ một snapshot tối thiểu và có chính sách cleanup rõ.

## 4. Các mô hình liên kết aspect với target

Đây là những **mô hình khái niệm** thường gặp trong nghiên cứu/triển khai AOP; tên cú pháp và điều kiện tạo instance khác nhau giữa các engine.

| Mô hình | Quan hệ với target | Đặc điểm state | Câu hỏi quan trọng |
| --- | --- | --- | --- |
| **Shared / per-system** | Một aspect instance dùng chung cho nhiều target/điểm. | Field instance chia sẻ. | Thread safety, phân nhóm target, rò reference? |
| **Per-target** | Mỗi target đủ điều kiện có state/aspect riêng. | Field theo target. | Association nào xác định target; cleanup khi target hết hạn? |
| **Per-receiver / per-this** | Liên kết với receiver ở một loại join point. | Có thể khác per-target nếu `this` và `target` khác nhau. | Join-point kind nào cung cấp receiver? |
| **Per-control-flow** | Một instance/context theo một luồng điều khiển nhất định. | State theo phiên thực thi được chọn. | Recursion, nested flow và async propagation ra sao? |
| **Per-invocation** | State hoặc instance cho mỗi lần advice chạy. | Không chia sẻ giữa lượt gọi nếu thiết kế đúng. | Cấp phát/cost và lifetime của task? |
| **Per-thread** | Một giá trị/aspect gắn với thread. | Tái sử dụng trong cùng thread. | Thread pool, `ThreadLocal` cleanup, thread migration? |

Một số động cơ AOP có primitive instantiation như per-object hoặc per-control-flow; nhiều động cơ khác **không có loại scope ấy** và người dùng phải tự hiện thực bằng wrapper, mapping hoặc context. Không viết một biểu thức scope như thể nó chắc chắn có ở mọi công cụ.

### 4.1. Shared aspect state: một instance, nhiều target

~~~text
            +-----------------+
Target A -->|                 |
            |  Shared Aspect  | --> advice on selected join points
Target B -->|                 |
            +-----------------+
~~~

Một aspect state có thể giữ cấu hình bất biến và bộ đếm tổng cho mọi target. Nếu aspect chứa field `lastRequest` mutable, request A có thể ghi đè state của B, đồng thời giữ reference đến request cũ lâu hơn mức cần thiết.

**Hợp lý khi:** concern giống nhau cho mọi target, state không cần tách theo target, hoặc state chia sẻ được thiết kế an toàn/giới hạn.

**Cẩn thận:** instance state chia sẻ giữa threads; nhiều advice executions có thể chạy đồng thời. Một counter thường `int count++` không bảo đảm atomic trong concurrent execution.

### 4.2. Per-target state: liên kết một instance theo một target

~~~text
Target A --> Aspect state A
Target B --> Aspect state B
Target C --> Aspect state C
~~~

Ví dụ: muốn đếm số invocation của **từng target**, hoặc cần gắn một context có lifetime cùng target. Cách tạo state có thể lazy khi lần đầu có join point, eager khi wrapper được tạo, hoặc do engine điều phối.

**Không được suy:** “target được tạo là aspect instance chắc chắn được tạo ngay”. Một số hệ thống chỉ cấp phát khi pointcut lần đầu cần hoặc khi một association được thiết lập.

Một mapping per-target không tự bảo đảm state được giải phóng. Nếu global `Map<Target,Aspect>` dùng strong keys và không remove, map có thể giữ target vô hạn. Nếu dùng weak keys, value cũng có thể giữ key gián tiếp; cần xem retained paths.

### 4.3. Per-this khác per-target trong những ngữ cảnh có nhiều receiver

Ở một số join-point models, `this` là đối tượng hiện hành tại vị trí gọi, còn `target` là đối tượng nhận hành vi được gọi. Với **method call join point**, hai vai trò có thể khác nhau; với **method execution**, ngữ nghĩa lại tùy runtime và công cụ.

Ví dụ ý tưởng:

~~~text
Caller C ----calls----> Target T.method()
   |
   +-- this = C ở call-site model được hỗ trợ
                     target = T
~~~

Một state gắn theo caller (per-this) **không đồng nghĩa** state gắn theo callee (per-target). Khi thiết kế per-object aspect, hãy nêu loại join point, nghĩa receiver và quy tắc identity.

### 4.4. Per-control-flow: lifetime theo phiên thực thi, không theo object cố định

Một aspect state có thể bắt đầu khi một control flow cụ thể được chọn rồi dùng cho các join point bên trong luồng ấy. Nếu cùng một target được gọi bởi hai control flows khác nhau, hai state khác nhau có thể cùng tồn tại.

~~~text
Flow 1: operation A -> helper -> target X
         [context flow-1]

Flow 2: operation B -> helper -> target X
         [context flow-2]
~~~

Cần làm rõ khi control flow **bắt đầu và kết thúc**, cơ chế nested/recursive flows và cách engine quản lý context. Đặc biệt trong ứng dụng async, một logical operation chuyển thread có thể không còn phù hợp với quan niệm call stack đơn giản; không tự cho rằng control-flow aspect đi xuyên `CompletableFuture`/executor.

### 4.5. Per-invocation: tách state mỗi lần mà không nhất thiết tạo aspect mới

Nếu chỉ cần đo thời gian:

~~~text
around:
    started = monotonicClock()
    try:
        return proceed()
    finally:
        observeDuration(monotonicClock() - started)
~~~

Chỉ cần biến `started` local là đã có **state riêng cho mỗi invocation**. Không cần tạo một class `TimingAspect` mới cho mỗi lần gọi chỉ để tránh concurrent data races.

Ngược lại, nếu một engine hỗ trợ instance-per-invocation thực, việc tạo instance có thể có chi phí và lifecycle khác. **Per-invocation data** và **per-invocation aspect instance** là hai thiết kế có thể trùng nhau hoặc không.

### 4.6. Per-thread có phải per-request không?

Không. Với thread pool, thread T1 có thể xử lý request A rồi request B. Nếu một `ThreadLocal<Context>` của advice không được `remove()` hoặc restore đúng scope, request B có thể nhìn thấy context A. Một async request cũng có thể sử dụng T1 và T2; một giá trị gắn riêng T1 không tự chuyển đến T2.

Do vậy per-thread phù hợp khi **thật sự cần thread-affine state**, không phải cách mặc định để mô hình hóa mọi logical request.

## 5. Một target có thể có nhiều aspect, một aspect có thể có nhiều target

Cấu hình AOP hiếm khi là quan hệ 1:1 đơn giản.

~~~text
                         +-- Tracing aspect (shared)
Target A --> Proxy A ----|
                         +-- Metrics aspect (shared)
                         +-- State A (per-target)

                         +-- Tracing aspect (shared)
Target B --> Proxy B ----|
                         +-- Metrics aspect (shared)
                         +-- State B (per-target)
~~~

Một lần gọi trên Proxy A có thể kích hoạt cả tracing và metrics; một lần gọi trên Proxy B cũng vậy. Nếu một aspect được chia sẻ, hai target có thể cùng cập nhật số liệu tổng. Nếu một aspect có state per-target, giá trị của A và B tách biệt.

Nhưng **aspect count không quyết định advice count**: nếu A gọi cùng method 50 lần, advice có thể chạy 50 lần; nếu B chưa được gọi thì state B có thể chưa thay đổi. Nếu target A bị gọi trực tiếp bỏ qua proxy, handler có thể không ghi nhận lượt đó, dù body target vẫn chạy.

### 5.1. Thời điểm tạo proxy có thể khác thời điểm tạo target

Một wrapper có thể được tạo khi ứng dụng cấu hình dependencies, còn target đã tồn tại từ trước. Một target khác có thể được tạo theo request rồi được bọc ngay. Một proxy có thể được dùng để đại diện cho nhiều target theo routing logic, tùy cơ chế. Không nhất thiết số lượng proxy = số lượng target.

### 5.2. Aspect được dệt trước runtime có cần instance giống proxy không?

Không nhất thiết. Source/bytecode weaving có thể chèn lời gọi tới logic dùng chung, state static, hoặc một mô hình instantiation do engine quy định. Điểm được biến đổi không bắt buộc lưu reference riêng tới một object aspect cụ thể. Vì vậy khi hỏi “vòng đời aspect”, phải biết engine quản lý **cấu hình/logic** hay **aspect object chứa field**.

## 6. Ownership: ai giữ tham chiếu tới ai?

Vòng đời trong runtime có GC không chỉ phụ thuộc thời điểm constructor chạy. Nó phụ thuộc **reachability** và ownership graph.

Một proxy có thể giữ:
- Target.
- Handler.
- Các aspect/interceptor instances.
- Method metadata, annotation cache hoặc configuration.

Một aspect có thể giữ:
- Registry target → state.
- Listener/subscription.
- Context trong `ThreadLocal`.
- Callback cho future chưa hoàn tất.
- Metrics buffer/cache.
- Last arguments/result/Throwable vô tình sống lâu.

~~~text
Long-lived registry
  -> shared aspect
      -> Map<Target, PerTargetState>
          -> Target A
              -> Large object graph
~~~

Ngay cả khi business logic đã kết thúc sử dụng Target A, một strong reference trong registry vẫn có thể làm A reachable. **GC không xóa một object còn được giữ mạnh chỉ vì domain coi nó đã hết hiệu lực.**

### 6.1. “Đồng vòng đời” không tự xảy ra

Gắn state với target về mặt logic không bảo đảm state sẽ được GC đồng thời với target. Nếu global map giữ strong key là target, target có thể tiếp tục sống nhờ map. Nếu aspect state giữ reference ngược về target, việc chỉ chuyển map thành weak-key map vẫn cần kiểm tra đường giữ reference qua value.

Vòng đời đồng bộ cần được đảm bảo bởi **cách sở hữu reference**, chính sách remove/close hoặc một cấu trúc association có semantics phù hợp — không phải bởi tên “per-target”.

### 6.2. Hủy logic khác thu gom vật lý

Một object có thể được **close/dispose/unregister** tại thời điểm xác định rồi chỉ được GC ở thời điểm bất định. Với tài nguyên như file descriptor, socket hoặc listener, nên có cleanup tường minh theo hợp đồng, không đợi GC. Nếu aspect quản lý một resource dùng chung, **không được đóng nó khi một target ngắn hạn kết thúc** nếu các target khác còn cần.

### 6.3. Không giữ whole target nếu chỉ cần một định danh

Một aspect per-target có thể chỉ cần ghi metric theo **tên loại operation hoặc ID đã giới hạn**, thay vì cất target object vào long-lived map. Nếu cần phân biệt identity mỗi target, cân nhắc id được cấp rõ, weak association hoặc cleanup lifecycle hợp lệ; tránh dùng `System.identityHashCode` làm ID tuyệt đối duy nhất hay ổn định xuyên tiến trình.

## 7. Concurrency khi aspect instance được chia sẻ

Một aspect singleton/shared có thể nhận nhiều invocations đồng thời. Ví dụ sai:

~~~java
class UnsafeTimingAspect {
    long startedNanos;

    void before() {
        startedNanos = System.nanoTime();
    }

    long after() {
        return System.nanoTime() - startedNanos;
    }
}
~~~

Đây là **mã minh họa không độc lập**. Nếu thread A ghi `startedNanos`, rồi thread B ghi đè, A đo thời gian sai. Không nhất thiết cần per-target aspect để sửa: **local variable** trong around invocation sẽ tách riêng lần gọi; nếu cần bộ đếm chung thì dùng primitive concurrency thích hợp.

### 7.1. Khi nào cần synchronization?

- **Config bất biến:** có thể chia sẻ an toàn sau khi publish đúng.
- **Counters chung:** dùng `LongAdder`, atomic variables hoặc đồng bộ hóa phù hợp.
- **State có nhiều field liên quan:** cần bảo đảm invariant, không chỉ mỗi field atomic.
- **Cache:** cân nhắc thread-safe data structure, eviction và cardinality.
- **Per-target state:** vẫn có thể bị nhiều threads truy cập cùng một target; per-target không mặc nhiên thread-safe.
- **Per-invocation local state:** thường cách đơn giản nhất để tránh trộn dữ liệu giữa những lượt gọi.

### 7.2. Có per-target vẫn có race

Nếu 100 threads cùng gọi một target singleton, state riêng của target ấy vẫn dùng chung cho 100 calls. Một counter `int count++` trong per-target aspect có thể mất update. Phạm vi per-target giải quyết **phân nhóm state theo object**, không tự giải quyết **đồng bộ các operations đồng thời**.

### 7.3. Reentrancy và recursion

Aspect của một target có thể tự kích hoạt một method cùng pointcut khi advice gọi logger, metrics hoặc helper:

~~~text
target invocation
  -> advice
      -> metrics.record()
          -> advice
              -> metrics.record()
                  -> ...
~~~

Nếu state được dùng chung hoặc per-target, recursion vẫn có thể xảy ra. Cần tách instrumentation khỏi pointcut liên quan, định nghĩa nested scope và tránh giữ lock khi gọi `proceed()` nếu có nguy cơ deadlock.

## 8. Java 17 runnable: shared, per-target và per-invocation state

Chương trình **`AspectTargetLifetimeDemo.java`** dùng JDK dynamic proxy để mô phỏng ba scope **được thiết kế tường minh**. Đây **không phải** AOP engine hỗ trợ mọi loại per-object/per-control-flow; nó chỉ giúp nhìn thấy **ai sở hữu state** và **state nào dùng lại qua những target/lần gọi khác nhau**.

Thiết kế:

- Có hai target `RealJob("A")` và `RealJob("B")`.
- Cả hai proxy cùng nhận một `AspectState("shared")`.
- Mỗi target proxy có một state riêng: `AspectState("target-A")` hoặc `AspectState("target-B")`.
- Mỗi lần handler được gọi tạo một `AspectState("call")`.
- Các lớp advice bao nhau theo thứ tự **shared → per-target → per-call → target**.
- Một lời gọi trực tiếp vào target A minh họa bypass proxy.
- Một lời gọi ném exception minh họa cleanup/propagation của ba tầng.

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AspectTargetLifetimeDemo {
    interface Job {
        String run(String id);
    }

    static final class RealJob implements Job {
        private final String name;

        RealJob(String name) {
            this.name = name;
        }

        @Override
        public String run(String id) {
            System.out.println("TARGET " + name + " " + id);
            if ("FAIL".equals(id)) {
                throw new IllegalStateException("job failed");
            }
            return name + ":" + id;
        }
    }

    @FunctionalInterface
    interface Proceed {
        Object call() throws Throwable;
    }

    static final class AspectState {
        final String scope;
        int seen;

        AspectState(String scope) {
            this.scope = scope;
        }

        Object around(String operation, Proceed next) throws Throwable {
            seen++;
            System.out.println("ADVICE " + scope + " #" + seen
                    + " " + operation);
            try {
                Object value = next.call();
                System.out.println("RETURN " + scope);
                return value;
            } catch (Throwable original) {
                System.out.println("THROW " + scope + " "
                        + original.getClass().getSimpleName());
                throw original;
            }
        }
    }

    static Object invokeTarget(Job target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static Job proxied(Job target, AspectState shared, AspectState perTarget) {
        return (Job) Proxy.newProxyInstance(
                Job.class.getClassLoader(),
                new Class<?>[] { Job.class },
                (proxy, method, args) -> {
                    AspectState perCall = new AspectState("call");
                    return shared.around(method.getName(),
                            () -> perTarget.around(method.getName(),
                                    () -> perCall.around(method.getName(),
                                            () -> invokeTarget(target, method, args))));
                }
        );
    }

    public static void main(String[] args) {
        RealJob targetA = new RealJob("A");
        RealJob targetB = new RealJob("B");

        AspectState shared = new AspectState("shared");
        AspectState stateA = new AspectState("target-A");
        AspectState stateB = new AspectState("target-B");

        Job proxyA = proxied(targetA, shared, stateA);
        Job proxyB = proxied(targetB, shared, stateB);

        System.out.println("CALLER " + proxyA.run("first"));
        System.out.println("CALLER " + proxyB.run("second"));

        try {
            proxyA.run("FAIL");
        } catch (IllegalStateException ex) {
            System.out.println("CALLER caught " + ex.getMessage());
        }

        System.out.println("CALLER " + targetA.run("DIRECT"));
        System.out.println("COUNTS shared=" + shared.seen
                + " A=" + stateA.seen
                + " B=" + stateB.seen);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac AspectTargetLifetimeDemo.java
java AspectTargetLifetimeDemo
~~~

Stdout kỳ vọng:

~~~text
ADVICE shared #1 run
ADVICE target-A #1 run
ADVICE call #1 run
TARGET A first
RETURN call
RETURN target-A
RETURN shared
CALLER A:first
ADVICE shared #2 run
ADVICE target-B #1 run
ADVICE call #1 run
TARGET B second
RETURN call
RETURN target-B
RETURN shared
CALLER B:second
ADVICE shared #3 run
ADVICE target-A #2 run
ADVICE call #1 run
TARGET A FAIL
THROW call IllegalStateException
THROW target-A IllegalStateException
THROW shared IllegalStateException
CALLER caught job failed
TARGET A DIRECT
CALLER A:DIRECT
COUNTS shared=3 A=2 B=1
~~~

### 8.1. Vì sao `shared.seen` tăng từ 1 tới 3?

Hai proxy dùng chung **cùng một tham chiếu `shared`**. Ba invocation qua proxy (`A:first`, `B:second`, `A:FAIL`) đều vào `shared.around`, nên counter lần lượt là 1, 2, 3. Không phải chương trình tạo ba aspect shared instances; đây là **ba lần gọi trên một instance**.

### 8.2. Vì sao per-target A và B đếm tách biệt?

`stateA` được đưa vào `proxyA`, `stateB` được đưa vào `proxyB`. Chỉ hai lời gọi qua proxy A nên `stateA.seen=2`; chỉ một lời gọi qua proxy B nên `stateB.seen=1`. Hai target có identity khác nhau, và bài mô phỏng một state owner riêng cho mỗi proxy-target association.

**Lưu ý:** Code này không có registry tự động tìm aspect theo target. Việc gắn per-target được lập trình tường minh ngay lúc gọi `proxied`; một AOP engine thật có thể dùng chiến lược association khác.

### 8.3. Vì sao per-call luôn in `#1`?

Mỗi lần handler nhận một invocation, nó tạo **một `new AspectState("call")` mới**. Mỗi instance chỉ được dùng cho lượt gọi đó nên `seen` bắt đầu 0 và trở thành 1. Đây là **mô phỏng instance-per-invocation**, không phải đề xuất cần tạo aspect mới cho mọi call trong production.

Nếu chỉ đo duration, một biến local `startedNanos` thường đủ và tiết kiệm cấp phát hơn.

### 8.4. Vì sao một lỗi tạo ba dòng THROW?

Các advice được lồng như:

~~~text
shared.around(
    targetA.around(
        call.around(
            target.run("FAIL")
        )
    )
)
~~~

Target ném `IllegalStateException`. Tầng `call` bắt để quan sát rồi ném lại; tầng `target-A` bắt cùng lỗi rồi ném lại; tầng `shared` cũng làm thế. Do đó có **ba log THROW cho một exception crossing ba boundaries**.

Đây không tự tương đương ba failure nghiệp vụ độc lập. Khi đo metric, phải xác định muốn đếm **attempt**, **advice boundary** hay **logical operation**, tránh trùng chỉ vì nhiều aspect cùng quan sát một lỗi.

### 8.5. `targetA.run("DIRECT")` có làm shared count tăng không?

Không. Đây là lời gọi **bỏ qua proxy**, nên cả ba tầng handler/advice không chạy; target vẫn trả `A:DIRECT`. Vì thế cuối chương trình counters là `shared=3, A=2, B=1`, không tính direct call.

Giới hạn này thuộc **JDK delegating proxy demo**. Không suy rằng bytecode weaving trực tiếp vào method execution cũng bỏ qua direct-target call.

### 8.6. Vì sao demo không dùng nhiều threads?

`AspectState.seen` là `int` mutable không có synchronization. Trong **chương trình tuần tự** này, output là xác định và dùng để dạy về scope. Nếu dùng nhiều threads trên cùng `shared` hoặc một `perTarget`, `seen++` có thể race. Trong ứng dụng thực, cần bộ đếm phù hợp concurrency như `LongAdder` hoặc `AtomicInteger` và kiểm soát các bất biến phức tạp hơn.

### 8.7. Demo không thể chứng minh GC hoặc automatic disposal

Việc tạo `AspectState("call")` mỗi lần chỉ minh họa **association/usage scope**. Không có `finalize`, `System.gc()` hay kiểm tra “object đã được thu hồi”: GC không có thời gian chạy xác định. Ngoài ra, `shared`, `stateA`, `stateB`, proxy và target còn các reference trong `main`. Không thể dùng dòng COUNTS làm bằng chứng đối tượng đã bị dọn khỏi heap.

## 9. Vòng đời khi dùng proxy so với weaving

### 9.1. Delegating proxy

Một proxy handler thường giữ reference tới target và các interceptors. Cấu trúc có thể là:

~~~text
Application object graph
  -> Proxy
      -> Handler
          -> Target
          -> Aspect A (shared hoặc per-proxy)
          -> Aspect B
~~~

Nếu proxy vẫn reachable, target thường vẫn reachable qua handler. Nếu một shared aspect nằm trong global registry, nó có thể tồn tại kể cả khi một proxy cụ thể không còn sử dụng.

Lúc target được tạo, chưa chắc proxy đã có. Lúc proxy được dựng, chưa chắc aspect object được tạo tại cùng thời điểm (có thể đã có/shared). Khi proxy được GC, aspect shared không nhất thiết cũng được GC.

### 9.2. Class-based proxy

Một số cơ chế tạo subclass/code-generated proxy. Association giữa interceptor, proxy instance và target body có thể khác delegating interface proxy. Đặc biệt self-invocation, `final` methods, `private` methods và receiver semantics phải được kiểm tra theo engine cụ thể.

Không viết định luật “mọi class proxy đều có một aspect instance trên mỗi object”. Thông tin đó thuộc mô hình tạo instance và cách engine lắp interceptor.

### 9.3. Weaving ở source/bytecode/load time

Một weaver có thể chèn logic advice vào target method hoặc call site. Khi đó target class có thể mang hook dù không có object proxy độc lập. Aspect instance có thể được tạo và quản lý ở một registry khác, có thể dùng chung hoặc theo scope.

Vì vậy **vòng đời của target class đã được weave** và **vòng đời của aspect instance** không bắt buộc liên kết qua quan hệ proxy → handler → target.

### 9.4. Load-time instrumentation và classloader lifecycle

Một class được transformer biến đổi lúc load không có nghĩa transformer instance sống cùng mọi target instance. Transformer có thể chỉ thực hiện một bước biến đổi và các target được tạo nhiều lần về sau. Ngoài ra, registry aspect/method metadata giữ reference tới classloader cũ có thể ảnh hưởng unload plugin.

## 10. Thời điểm bắt đầu và kết thúc association

Thay vì đoán, hãy đưa việc quản lý scope thành bốn câu hỏi độc lập:

1. **Creation trigger:** tạo aspect state khi đăng ký aspect, tạo target, tạo proxy, lần đầu pointcut khớp, hay mỗi invocation?
2. **Association key:** cùng state được dùng cho cái gì — process/module, target identity, receiver identity, thread, control flow, invocation ID?
3. **Lifetime boundary:** khi nào association không còn có nghĩa — hết request, đóng target, unload module, task completed/cancelled, application shutdown?
4. **Cleanup owner:** ai thực hiện remove/unregister/close và xử lý lỗi cleanup?

### 10.1. Eager và lazy creation

**Eager:** tạo state ngay khi target/proxy/registry được dựng. Dễ nhận thấy số object và có thể phát hiện lỗi sớm, nhưng tạo state cho những target không bao giờ có join point được chọn.

**Lazy:** tạo khi lần đầu một occurrence thỏa điều kiện. Tiết kiệm khởi tạo nhưng cần xử lý race ở first-use, đặc biệt khi nhiều thread cùng gọi target lần đầu. Hai thread có thể đều thử tạo state; engine hoặc registry phải xác định semantics atomic/publish.

### 10.2. Cleanup không chỉ là xóa một map entry

Nếu aspect per-target đăng ký listener hoặc mở resource, khi association kết thúc cần tháo listener, cancel task, đóng resource hoặc flush trạng thái phù hợp. Không được chỉ xóa mapping mà bỏ quên những external registrations vẫn giữ reference tới aspect và target.

### 10.3. Target shutdown không luôn gọi aspect shutdown

Một target có thể bị thu hồi bởi GC mà không có callback user-level xác định. Không dựa vào `finalize` làm lifecycle hook đáng tin; Java finalization đã bị deprecated và không bảo đảm chạy đúng thời điểm. Với tài nguyên cần cleanup, hãy đặt **close/dispose** rõ ràng hoặc dùng owner cấp cao hơn.

### 10.4. Aspect shared shutdown không thể phụ thuộc target cuối một cách mơ hồ

Nếu aspect shared phục vụ nhiều targets, nó không nên đóng metrics exporter khi target A kết thúc trong lúc B còn dùng. Cần xác định shutdown ở cấp module/application, reference counting nếu thực sự cần, hoặc một owner độc lập quản lý resource shared.

## 11. Một số chiến lược giữ per-target state

### 11.1. Trạng thái nằm trong wrapper

Một decorator/proxy object giữ state cho target mà nó bọc. Nếu chỉ một proxy cho mỗi target và object graph được quản lý, đây là cách đơn giản. Nhưng nếu tạo hai proxy riêng cho **cùng target**, bạn có thể có hai state độc lập dù cùng target. Khi cần state “per-target identity”, mapping ở wrapper có thể không đáp ứng đúng mong muốn.

### 11.2. Registry map theo target identity

Một map có thể tra state theo object target:

~~~text
TargetIdentity -> AspectState
~~~

Cần xem:
- Identity hay equality? Hai target khác nhau nhưng `equals()` bằng nhau có nên chia state?
- Keys mạnh hay yếu?
- Values có giữ lại target?
- Khi nào xóa entry?
- Registry có thread-safe và bounded không?
- Có nhiều classloader/module cùng đăng ký không?

Dùng `WeakHashMap` theo equality không tự tương đương identity-based weak association; còn value mạnh tham chiếu về key có thể làm key không được thu hồi. Nếu cần identity semantics, phải chọn cấu trúc phù hợp và test.

### 11.3. State được tạo khi target constructor chạy

Một số hệ thống có thể gắn state cùng lúc object được tạo hoặc lúc hook constructor được gọi. Nhưng không phải mọi proxy AOP chặn constructor; với JDK interface proxy, target constructor đã chạy trước khi proxy bọc target.

Do vậy không thể nói “per-target aspect được khởi tạo trong constructor target” như một quy tắc chung. Đây là quyết định engine/application.

### 11.4. Cần identity ổn định cho task lẫn target

Nếu target là đối tượng sống dài phục vụ nhiều tasks, một aspect per-target không đủ để phân biệt task A/B đang chạy đồng thời. Có thể cần kết hợp **per-target shared config** với **per-invocation context**:

~~~text
Target-associated aspect:
    immutable policy
    bounded metrics state

Each invocation:
    trace ID
    startedNanos
    error/outcome
~~~

Đây thường là lựa chọn tốt hơn việc đưa `currentRequest` hoặc `lastStart` vào field dùng chung của per-target aspect.

## 12. Async: target lifetime, invocation lifetime và task lifetime

Một method có thể trả `CompletionStage<Result>` ngay rồi tác vụ hoàn tất sau:

~~~text
T0 target method invoked
T1 around advice begins
T2 method returns stage          <-- invocation boundary ends
T3 worker processes task
T4 stage completes               <-- task boundary ends
T5 callback records final outcome
~~~

Mốc T0–T5 minh họa, không phải thứ tự bắt buộc: stage có thể hoàn tất trước T2 hoặc callback có thể chạy trên thread khác.

### 12.1. Aspect per-target vẫn có thể phục vụ nhiều tasks đồng thời

Một service singleton có thể trả nhiều futures. Aspect instance per-target cũng là **một** instance, nhưng mỗi task cần trace ID/timer/outcome riêng. Nếu dùng field `currentFuture` hoặc `startTime`, tasks sẽ ghi đè state của nhau.

### 12.2. Per-call state có thể cần sống sau method return

Nếu chỉ đo invocation latency, cleanup khi `proceed()` return stage có thể đúng. Nếu cần end-to-end latency, một snapshot tối thiểu (`startNanos`, `operationId`) phải được giữ tới completion.

Không capture toàn bộ join-point object, arguments, target hay request nếu callback chỉ cần một ID và timestamp. Nếu future không bao giờ complete, callback có thể giữ dữ liệu lâu; cần timeout/deadline, quota và monitoring.

### 12.3. Thread-local không tự kéo dài theo task

Một `ThreadLocal` của around advice trên caller thread nên được dọn/restore trong scope tương ứng. Worker thread không tự nhận giá trị đó. Gọi `ThreadLocal.remove()` ở completion callback trên worker **không dọn giá trị của caller thread**.

Với task context, dùng propagation được abstraction hỗ trợ hoặc snapshot/restore trên mỗi thread đúng vòng đời; không để request context tồn tại trong thread pool chỉ vì chờ future.

### 12.4. Cancellation khác object disposal

Một future bị cancel có thể hoàn tất trạng thái cancelled nhưng underlying I/O hoặc task đã gửi đi chưa chắc dừng. Việc bỏ callback, đóng resource và release aspect state phải căn theo **hợp đồng cancellation và ownership thực tế**, không chỉ trạng thái một handle.

## 13. Error path và trạng thái của aspect

### 13.1. Exception không tự hủy aspect shared

Nếu một invocation trên target A ném `IllegalStateException`, shared aspect vẫn có thể dùng tiếp cho target B. Advice có thể ghi failure rồi rethrow; điều này không có nghĩa aspect instance đã kết thúc vòng đời.

Trong ví dụ Java 17, sau `proxyA.run("FAIL")`, `shared.seen=3`; B vẫn là state riêng của một target khác. Mục đích của `catch` trong aspect là quan sát/làm chính sách, không nhất thiết là disposal.

### 13.2. Lỗi có thể để lại state sai nếu không cleanup

Nếu aspect ghi `ThreadLocal`, mở span hoặc lock trước `proceed()`, target ném lỗi rồi code chỉ cleanup ở nhánh return thì resource/context có thể rò. Dùng `finally` hoặc scope abstraction phù hợp để dọn trên cả return và throw.

### 13.3. Lỗi của chính advice có thể che lỗi target

Nếu target ném lỗi và logger/cleanup trong advice cũng ném lỗi, caller có thể thấy lỗi instrumentation thay vì lỗi gốc. Chính sách best-effort observability thường ưu tiên giữ exception target; audit bắt buộc có thể đòi hợp đồng khác. Dù chọn chính sách nào, phải nói rõ ai chịu trách nhiệm và test error path.

### 13.4. Mutable state sau một failure

State của per-target aspect có thể sống qua hàng nghìn invocations, gồm cả failure. Nếu một lần gọi làm state rơi vào trạng thái “đang xử lý” mà không reset, các lần sau có thể bị ảnh hưởng.

Thiết kế phải phân biệt:
- **State lịch sử có chủ đích:** count, rolling metrics, bounded errors.
- **State tạm của invocation:** start time, currently active request.
- **State tài nguyên:** open/closed, reference count, listener registrations.

Không gom tất cả vào một mutable field có tuổi thọ dài chỉ vì “aspect quản lý nó”.

## 14. Những vấn đề memory retention và classloader

### 14.1. Shared aspect giữ target ngoài ý định

Một aspect lâu sống có field:

~~~java
Object lastTarget;
Object lastArguments;
Throwable lastError;
~~~

Đây là minh họa về nguy cơ, không phải chương trình độc lập. Dù chỉ một reference, nó có thể giữ cả object graph lớn. Nếu có `List<Object[]>` lưu mọi args, retention có thể tăng theo số invocation.

Nên snapshot dữ liệu tối thiểu như operation name, error type, bounded ID hoặc aggregate metric thay vì lưu target/request/Throwable khi không cần.

### 14.2. Per-target association dùng strong keys

~~~text
GlobalRegistry
   -> Map<Target,AspectState>
         -> Target
         -> State
~~~

Nếu không có chính sách remove, mỗi target mới có thể ở lại. Dù code về mặt khái niệm “per-target”, vòng đời thực có thể trở thành **per-application-retained-forever**.

### 14.3. Proxy và aspect có thể tạo reference cycle

Ví dụ proxy giữ handler, handler giữ target, target giữ proxy tham chiếu ngược. Một cycle trong GC tracing **không tự là leak** nếu toàn bộ cycle không còn reachable từ GC roots. Vấn đề là một registry hoặc thread sống dài vẫn nắm một node của cycle khiến cả graph còn reachable.

Không sửa vấn đề bằng cách cực đoan “không bao giờ có cycle”, mà bằng ownership/cleanup đúng.

### 14.4. Plugin unload

Một aspect registry static ở host giữ metadata `Class` hoặc proxy từ plugin classloader cũ có thể làm classloader cũ không được thu hồi. Nếu hệ thống cho phép unload/reload, cần lifecycle hook để remove registration/cache và kiểm tra retained paths.

### 14.5. Đừng đánh đồng `close` với GC

Một aspect/target được close theo hợp đồng có thể vẫn reachable và chưa GC. Điều cần test deterministic là **resource đã đóng**, listener đã unregister, registry entry đã remove và count pending tasks đã giảm. Đừng viết assertion bắt `System.gc()` phải dọn đúng trong một khoảng ngắn.

## 15. Chọn scope theo yêu cầu thay vì theo thói quen

| Nhu cầu | Scope nên cân nhắc | Vì sao | Điều phải kiểm tra |
| --- | --- | --- | --- |
| Cấu hình bất biến dùng chung mọi service. | Shared/module scope. | Ít duplication, rõ ownership. | Safe publication, module lifetime. |
| Đếm tổng số lần gọi của nhiều target. | Shared counter. | Định nghĩa metric tổng. | Concurrency, cardinality. |
| Đếm riêng mỗi target identity. | Per-target association hoặc key ổn định. | Tách metric theo object. | Identity vs equality, cleanup. |
| Đo duration của mỗi lời gọi. | Local per-invocation state. | Không trộn các invocations. | Error/finally và async boundary. |
| Trace theo logical request qua nhiều methods. | Control-flow/task context. | Tương quan nhiều join points. | Recursion, executor, context propagation. |
| Một resource sống suốt module. | Owner cấp module/shared. | Không đóng khi một target dừng. | Shutdown và reference ownership. |
| Resource riêng target. | Per-target owner với close rõ. | Đồng nhất resource với target. | Dispose khi target không còn dùng. |
| Dữ liệu dùng tới completion của future. | Task-scoped minimal snapshot. | Duration/outcome end-to-end. | Timeout, cancellation, pending retention. |
| Policy tạm dùng trên thread. | Scoped ThreadLocal nếu thực sự cần. | Context nội bộ thread. | Remove/restore và thread pool. |

Không nên tạo per-target aspect chỉ vì method thuộc một class, cũng không nên dùng singleton chỉ vì dễ cấu hình. Chọn dựa trên **đơn vị ý nghĩa của state** và **các ràng buộc thực thi**.

### 15.1. Những câu hỏi chọn nhanh

- State của advice có phụ thuộc **target identity** không?
- Hai target có cần nhìn cùng counter/cache không?
- Hai concurrent invocations của cùng target có cần state độc lập không?
- Có async task sống lâu hơn method call không?
- Có plugin/module unload hoặc short-lived targets không?
- Có những đường caller bypass proxy không?
- Cleanup có liên quan tài nguyên external hay chỉ là giá trị GC-managed?

Nếu câu trả lời là “state chỉ đo một lượt gọi”, ưu tiên state local. Nếu “mỗi target có một bộ dữ liệu riêng”, cân nhắc per-target nhưng vẫn cần cleanup. Nếu “cả ứng dụng cùng dùng một policy”, shared instance có thể đủ khi thread-safe.

## 16. Bộ kiểm thử vòng đời cần có

### 16.1. Positive tests về scope

1. Tạo target A và target B, cùng shared aspect; gọi A, B, A và assert shared counter tăng 3.
2. Tạo per-target state riêng; assert A đếm 2, B đếm 1.
3. Với per-call state, assert mỗi lượt có context mới và không mang dữ liệu lượt trước.
4. Gọi target trực tiếp không qua proxy; assert handler state không đổi trong mô hình JDK proxy.
5. Tạo hai proxy cho **cùng target** nếu có thể; xác nhận semantics per-proxy có khác per-target identity dự kiến hay không.

### 16.2. Error/cleanup tests

- Target throw: advice có ghi failure đúng và truyền exception phù hợp không?
- Cleanup trong `finally` có chạy cả success/failure?
- Advice logging tự lỗi có che lỗi target ngoài chủ đích không?
- Sau failure, shared/per-target state có tiếp tục hợp lệ cho lần gọi sau?
- Nếu nested/reentrant advice, context trước được restore đúng không?

### 16.3. Concurrency tests

- Nhiều threads gọi chung một shared aspect.
- Nhiều threads gọi **cùng** target có per-target aspect.
- Một target xử lý nhiều async tasks đồng thời.
- Lazy per-target state được tạo khi nhiều threads cùng gọi lần đầu.
- Registry không giữ entries tăng vô hạn và không race.

Dùng synchronization/latches để kiểm soát test, không dựa vào `Thread.sleep` như bằng chứng đủ về interleaving. Một test tuần tự pass không chứng minh `int++` an toàn concurrent.

### 16.4. Lifecycle/ownership tests

- Đóng một target không đóng resource shared cần dùng cho target khác.
- Registry xóa state khi target/module được dispose theo hợp đồng.
- Unregister listener/subscription trên complete/error/cancel/shutdown.
- Pending future có deadline/quota, không giữ toàn bộ request object graph.
- Classloader của plugin không bị registry host giữ sau unload.
- Không giả định có deterministic GC; kiểm tra owner/registration/counters và dùng profiler khi cần.

### 16.5. Advice execution không phải aspect construction count

Instrument hai bộ đếm:
- **Aspect instances created**.
- **Advice invocations executed**.

Với 1 shared instance và 100 calls, kết quả có thể là 1 instance, 100 invocations. Nếu muốn per-target, nên đo số association theo target và số lần advice trên mỗi association. Đếm log constructor thay cho log advice sẽ gây suy luận sai.

## 17. Quy trình chẩn đoán khi vòng đời sai mong đợi

### Tình huống A — “State của A bị lẫn sang B”

Kiểm tra có dùng shared aspect instance nhưng lại lưu `currentTarget`, `lastResult` hoặc `startedNanos` trong field không. Nếu thật sự cần per-target association, xác định key đúng. Nếu chỉ cần per-call state, chuyển field thành local.

### Tình huống B — “State của target tăng mãi và không giải phóng”

Kiểm tra registry static, strong-key map, listeners, callbacks, proxy references và pending tasks. Tìm retained path từ GC roots. Không chỉ nhìn số object aspect để kết luận GC lỗi.

### Tình huống C — “Aspect được tạo hai lần cho một target”

Có thể có hai proxy riêng cho cùng target, hai registries, hai classloaders, duplicated configuration hoặc một association rule không theo target identity. Cần vẽ object graph và đo identity, không giả định chỉ dựa tên class.

### Tình huống D — “Target bị đóng nhưng aspect vẫn còn”

Nếu aspect shared phục vụ B/C, nó **nên còn**. Nếu state per-target A vẫn được giữ do mapping/listener, đó có thể là retention cần sửa. Trước khi báo leak, xác định scope được thiết kế và nghĩa của “còn”.

### Tình huống E — “Thread khác đọc nhầm context”

Có thể đang dùng `ThreadLocal` với pool thread và không remove/restore, hoặc callback async không được context propagation. Per-target aspect không tự sửa context của từng task.

### Tình huống F — “Một target gọi trực tiếp nhưng metric không tăng”

Nếu metric nằm trong proxy handler mà code bypass proxy, target body chạy nhưng advice không. Đây là **interception path mismatch**, không phải nhất thiết aspect instance đã hết vòng đời. Kiểm tra wiring và self-invocation.

### Tình huống G — “Nhiều log lỗi cho một exception”

Nhiều advice nested có thể cùng catch/rethrow một error. Đây có thể là nhiều event quan sát cho một failure, không phải nhiều aspect objects mới được tạo. Thiết kế metric phân biệt failure theo boundary/attempt/logical operation.

## 18. Sai lầm thường gặp

**“Một aspect class luôn có đúng một object cho cả JVM.”** Không. Phụ thuộc mô hình instantiation và phạm vi classloader/module.

**“Aspect instance được tạo khi target constructor chạy.”** Không mặc định. Có thể eager, lazy, lúc tạo proxy hoặc do registry/weaver quản lý.

**“Một target luôn có một proxy.”** Không. Có thể không có, có nhiều wrappers hoặc chung một routing proxy tùy cấu trúc.

**“Per-target aspect là thread-safe.”** Không. Một target có thể bị nhiều threads gọi đồng thời.

**“Per-thread state đồng nghĩa per-request state.”** Không với thread pool và async tasks.

**“Có 1.000 advice calls nghĩa là có 1.000 aspect instances.”** Không. Một instance có thể phục vụ nhiều calls.

**“Nếu target bị GC thì aspect shared cũng bị GC.”** Không nếu aspect được owner cấp cao hơn giữ để phục vụ target khác.

**“Per-target association chắc chắn biến mất cùng target.”** Không nếu registry giữ mạnh cả hai.

**“Object cycle luôn là leak.”** Không nếu cycle không còn reachable từ GC roots; vấn đề là đường reference còn sống.

**“Dùng WeakHashMap bảo đảm không giữ target.”** Không nếu value còn strong reference ngược key hoặc nơi khác giữ key.

**“Method trả future nghĩa là invocation state đã bao cả task.”** Không; cần tách method return và completion.

**“Tạo per-call aspect mới là cách duy nhất để state không bị trộn.”** Không. Local variables hoặc scoped context thường là lựa chọn đơn giản hơn.

**“Aspect dùng chung giữ `lastRequest` là vô hại.”** Không nếu request lớn, có dữ liệu riêng tư hoặc concurrency.

**“`finalize` là sự kiện shutdown đáng tin.”** Không. Cleanup quan trọng cần cơ chế tường minh.

**“Dùng proxy có thể bảo đảm mọi call đều có advice.”** Không nếu caller giữ target gốc hoặc self-call không đi qua proxy.

## 19. Bài tập vận dụng có đáp án

### Bài 1 — Hai target, một aspect

Có A, B và một aspect shared có biến đếm. A được gọi 3 lần, B 2 lần, tất cả đi qua handler. Counter của shared có thể là bao nhiêu?

**Đáp án:** 5 nếu mỗi invocation khớp và advice đếm đúng một lần. Không cần 5 aspect instances.

### Bài 2 — Hai proxy bọc cùng target

Tạo proxy P1, P2 cùng bọc target T nhưng mỗi proxy được cấp một `AspectState` mới. Đây có chắc là “per-target identity” không?

**Đáp án:** Không. Hai proxy sở hữu hai state dù cùng target. Đây là association theo proxy/wrapper trong thiết kế đó. Muốn per-target identity cần mapping/ownership bảo đảm cùng T dùng cùng state.

### Bài 3 — Per-target nhưng concurrency

Một target T được 50 threads gọi, aspect per-target có field `int seen` và `seen++`. Có bảo đảm seen tăng đúng 50 không?

**Đáp án:** Không; `seen++` không atomic. Scope per-target không tự cung cấp thread safety.

### Bài 4 — Shared duration

Một shared aspect đặt `startTime` trong field rồi tính duration khi method return. Vì sao sai?

**Đáp án:** Hai concurrent invocations có thể ghi đè start time. Đặt timestamp trong local state của mỗi invocation hoặc scope tương ứng.

### Bài 5 — Target hết hạn nhưng map còn

Một global map chứa `target → aspectState`. Không xóa entry. Target có tự được GC vì request đã hết không?

**Đáp án:** Không thể giả định; strong-key map có thể giữ target reachable. Cần lifecycle/eviction phù hợp.

### Bài 6 — ThreadLocal trong pool

Một thread xử lý request A rồi request B; advice quên `remove()`. Rủi ro nào?

**Đáp án:** B có thể nhìn thấy context A, rò dữ liệu/metric và giữ object của A lâu hơn cần thiết. Với nested scopes phải restore previous value đúng cách.

### Bài 7 — Async completion

Method trả `CompletionStage` ngay, nhưng task hoàn tất sau 2 giây. State nào nên tồn tại tới completion?

**Đáp án:** Nếu đo end-to-end, giữ snapshot nhỏ như ID/timestamp/outcome context đến terminal event; không mặc định giữ toàn bộ args, target hoặc ThreadLocal của caller.

### Bài 8 — Một exception, ba log THROW

Tại sao ví dụ có `THROW call`, `THROW target-A` và `THROW shared` dù target chỉ ném một lỗi?

**Đáp án:** Lỗi thoát qua ba advice nested. Các tầng quan sát cùng Throwable; không phải ba failure độc lập.

### Bài 9 — Direct target call

Gọi target A trực tiếp sau khi tạo proxy A; vì sao `shared.seen` không tăng?

**Đáp án:** Đường gọi không qua JDK dynamic proxy handler, nên advice ở handler không nhận invocation.

### Bài 10 — Plugin unload

Một registry ở host giữ `Method` thuộc plugin classloader đã unload về mặt logic. Hậu quả có thể là gì?

**Đáp án:** Registry có thể giữ metadata/classloader cũ reachable, ảnh hưởng khả năng thu hồi bộ nhớ. Cần scope cache và cleanup khi plugin dừng.

## 20. Checklist thiết kế vòng đời aspect

1. **Định nghĩa:** Aspect là cấu hình/logic được dệt hay runtime object có state?
2. **Creation:** Instance được tạo lúc nào và trigger nào quyết định?
3. **Association:** State gắn với application, module, target, receiver, thread, control flow hay invocation?
4. **Identity:** Per-target xác định theo object identity hay equality?
5. **Ownership:** Ai giữ reference đến aspect, target, proxy và registries?
6. **Cleanup:** Ai thực hiện `close/remove/unregister` và ở mốc nào?
7. **GC:** Có strong references làm target sống ngoài nhu cầu không?
8. **Concurrency:** Aspect shared/per-target có nhận nhiều calls đồng thời không?
9. **Per-call data:** Time/trace/error nằm ở local context hay bị đưa vào shared fields?
10. **Reentrancy:** Có nested advice, recursion và restore context đúng không?
11. **Async:** Invocation return và task completion có khác nhau không?
12. **Thread:** Có dùng `ThreadLocal` với thread pool và executor không?
13. **Exceptions:** Advice failure/target failure có cleanup và bảo toàn contract?
14. **Proxy path:** Mọi caller cần advice có thật đi qua proxy không?
15. **Weaving:** Nếu không dùng proxy, class/join point có thực sự được weave?
16. **Observability:** Có tách instances count, associations count và advice executions count?
17. **Perf:** Số instances/cache entries và allocations có phù hợp tải thực?
18. **Lifecycle tests:** Có kiểm tra success, error, shutdown, unload, concurrent và async?
19. **Security:** Có vô tình giữ request/target/Throwable chứa dữ liệu nhạy cảm?
20. **Compatibility:** Scope và association có thay đổi khi thêm proxy, module, classloader hoặc engine version?

## Kết luận

**Vòng đời của aspect và vòng đời của đối tượng được can thiệp không mặc định trùng nhau.** Một aspect instance có thể dùng chung cho nhiều target; một target có thể được liên kết với state riêng; nhiều advice invocations có thể chạy trên cùng một instance; và per-invocation state có thể được quản lý bằng local variables mà không cần tạo instance mới.

Thiết kế đúng bắt đầu từ câu hỏi **state thuộc về đơn vị nào** — ứng dụng, target, receiver, logical flow, thread hay invocation — rồi xác định **creation trigger, association key, owner, cleanup và concurrency contract**. Với proxy, còn phải chứng minh invocation đi qua handler; với weaving, phải xác nhận engine thực sự cài hook trên join point phù hợp. Với async, phải tách kết thúc method call khỏi kết thúc tác vụ. Một mô hình lifecycle rõ giúp tránh state bị trộn, memory retention, rò context và các giả định sai về việc “aspect được tạo/hủy cùng target”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-lifecycle-007">Lưu trạng thái theo lời gọi thay vì trạng thái dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Lưu trạng thái theo lời gọi thay vì trạng thái dùng chung trong Aspect

## 1. Vấn đề: trạng thái của advice thuộc về ai?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, aspect đóng gói các **pointcut** và **advice** cho mối quan tâm cắt ngang như logging, tracing, đo thời gian, kiểm soát truy cập hoặc audit. Nhiều advice cần ghi nhớ dữ liệu ngắn hạn: thời điểm bắt đầu, mã tương quan, giá trị đối số ban đầu, kết quả trung gian hoặc một cờ cho biết đã thực hiện cleanup.

Câu hỏi quan trọng là: **dữ liệu ấy thuộc về một lần thực thi join point, một target object, một request, một thread, hay thuộc toàn bộ aspect?**

Nếu một aspect instance được dùng chung cho nhiều lượt gọi, việc lưu dữ liệu **riêng của từng lần gọi** trong **field của aspect** có thể gây sai ngay cả khi không có lỗi cú pháp:

```java
// Minh họa vấn đề, không phải chương trình độc lập.
final class IncorrectTimingAspect {
    private long startNanos;

    void before() {
        startNanos = System.nanoTime();
    }

    void after() {
        long elapsed = System.nanoTime() - startNanos;
        record(elapsed);
    }
}
```

Với hai lượt gọi chồng lấn, lần gọi thứ hai có thể ghi đè `startNanos` trước khi lần thứ nhất chạy `after`. Thời gian của lần đầu bị đo từ mốc của lần sau. Dùng `volatile long` không sửa được lỗi gắn sai **một mốc chung** cho **hai lượt invocation**.

**Nguyên tắc:** *Dữ liệu chỉ có ý nghĩa trong một lần thực thi advice/join point nên được giữ trong phạm vi riêng của lần ấy; dữ liệu thực sự thuộc phạm vi dùng chung mới được giữ trong shared aspect state, với quy tắc concurrency tương ứng.*

> **Phạm vi:** Bài này nói về AOP cơ bản và nâng cao nói chung, không phụ thuộc một công cụ cụ thể. “Shared aspect”, “around advice”, “before/after advice”, “join point context”, “control flow” hay “proxy” được dùng theo nghĩa khái niệm. Mỗi hệ thống AOP có thể có API và chính sách instantiation riêng; những ví dụ Java 17+ bên dưới minh họa nguyên tắc quản lý trạng thái, không phải cú pháp pointcut phổ quát.

## 2. Ba trục phải phân biệt

Một phân tích đúng cần tách:

1. **Vòng đời aspect instance:** dùng chung, theo target, theo flow, theo invocation, hoặc scope khác.
2. **Vòng đời dữ liệu:** mốc thời gian riêng một lời gọi, metadata có thể dùng chung, trạng thái của target, request context, session hoặc cache.
3. **Phạm vi interception/weaving:** lời gọi nào thực sự qua advice, chẳng hạn proxy invocation hoặc method execution đã được dệt.

```text
Aspect instance: shared
    |
    +--> Invocation A  -> InvocationState[A]
    |
    +--> Invocation B  -> InvocationState[B]
    |
    +--> Invocation C  -> InvocationState[C]

MetadataCache: shared, immutable/thread-safe
```

**Một shared aspect không buộc phải dùng shared invocation state.** Instance advice có thể tồn tại lâu nhưng mỗi lần method được gọi vẫn có **stack frame và local variables riêng**.

Ngược lại, **một aspect instance mới theo từng invocation** chưa chắc giải quyết mọi vấn đề nếu nó dùng một `static` mutable field hoặc một singleton dependency không thread-safe.

### Scope là ý nghĩa, không chỉ nơi lưu kỹ thuật

| Loại dữ liệu | Scope hợp lý thường gặp | Không nên mặc định |
| --- | --- | --- |
| Thời điểm bắt đầu một method | Một invocation | Field shared `start` |
| Đối số/kết quả tạm cho một advice | Local/invocation context | Field `currentArguments` |
| Tổng lượt gọi toàn dịch vụ | Shared metric recorder thread-safe | Mỗi invocation có bộ đếm riêng và quên tổng hợp |
| Metadata cố định của method | Shared immutable cache có key đúng | Lấy lại Reflection phức tạp vô ích mọi lần |
| Trạng thái riêng target | Per-target hoặc khóa theo identity | Theo request nếu target được chia sẻ |
| Trace/correlation ID | Request/task context được truyền phù hợp | Một field shared hoặc giả định ThreadLocal luôn đi theo task |
| Một retry attempt count | Invocation local của thao tác retry | `currentAttempt` của aspect singleton |
| Session/user context | Scope được xác thực và làm sạch | Thread hoặc aspect singleton mặc định |

Không phải concern nào cũng cần state. Một advice kiểm tra giá trị đối số trực tiếp có thể hoàn toàn stateless.

## 3. Vì sao shared field sai: hai loại chồng lấn

### 3.1. Concurrency giữa hai thread

Giả sử shared aspect có field `currentId`:

```text
Thread A                         Thread B
-----------------------------------------------
before(A):
  currentId = "A"
                                 before(B):
                                   currentId = "B"
after(A):
  log(currentId)   // Ghi "B": sai
                                 after(B):
                                   log(currentId)
```

Các thao tác đọc/ghi cùng một field có thể bị **data race** nếu không đồng bộ. Dù dùng synchronization để giải quyết memory visibility, ý nghĩa “ID nào thuộc invocation nào” vẫn đòi state riêng. Nếu lock chỉ bảo vệ từng phép set/get, sau khi nhả lock thread khác vẫn có thể ghi đè.

### 3.2. Reentrancy ngay trên một thread

**Reentrancy** xảy ra khi một advice/target làm phát sinh thêm join point được advice xử lý **trước khi lượt bên ngoài kết thúc**. Không nhất thiết cần nhiều thread.

```text
before(outer): currentId = outer
  target.outer()
    -> before(inner): currentId = inner
       target.inner()
       after(inner): currentId = inner
after(outer): currentId = inner   // Sai dù chỉ một thread
```

Nếu giữ một field `currentId` chung, outer mất thông tin ngay trong một chuỗi gọi đồng bộ. Thêm `synchronized` quanh mỗi phép đọc/ghi không khôi phục được ID outer. Ngay cả khóa reentrant quanh cả chuỗi vẫn cho phép cùng thread đi vào lần gọi inner; nếu dữ liệu chỉ có một slot, nó vẫn bị ghi đè.

**Cần scope theo lượt vào/ra, không chỉ cơ chế khóa.**

### 3.3. Tại sao `volatile` và atomic không giải quyết correlation?

`volatile` hỗ trợ tính hiển thị của giá trị giữa thread, không biến một field thành nhiều slot cho nhiều invocation. `AtomicReference<String>` cho phép cập nhật atomic một reference, nhưng vẫn chỉ giữ **một reference chung**. Cả hai không tự biết after của invocation A phải đọc snapshot A chứ không phải snapshot B.

Đây là **lỗi mô hình dữ liệu**, không đơn thuần là một phép ghi chưa atomic.

## 4. Giải pháp ưu tiên: local state trong around advice

Khi một **around advice** bao trọn lần gọi và API hỗ trợ `proceed` hoặc cơ chế tương đương, state nên được khai báo **bên trong invocation của advice**:

```java
// Pseudocode Java của around advice, không phải tệp biên dịch độc lập.
Object around(ProceedingJoinPoint joinPoint) throws Throwable {
    long start = System.nanoTime();       // Local riêng lần gọi.
    String operation = joinPoint.name();   // Local riêng lần gọi.
    try {
        return joinPoint.proceed();
    } finally {
        recordDuration(operation, System.nanoTime() - start);
    }
}
```

`ProceedingJoinPoint` ở đây là **tên interface giả định để diễn đạt nguyên tắc**, không phải API tiêu chuẩn của Java SE. Trong công cụ thật, hãy thay bằng API invocation/proceed tương ứng.

Mỗi lần advice được gọi có stack frame riêng:

```text
around(A):
  start_A, operation_A
  proceed(A)

around(B):
  start_B, operation_B
  proceed(B)
```

Hai lời gọi có thể chạy đồng thời hoặc lồng nhau nhưng `start_A` và `start_B` **không phải cùng một biến**.

### Vì sao `finally`?

Nếu mục tiêu là **đóng scope hoặc ghi duration dù thành công hay lỗi**, `finally` thường diễn đạt đúng ý định: phần cleanup chạy ở cả đường return và throw theo semantics Java/AOP tương ứng.

Tuy vậy, **exception từ chính cleanup** có thể che exception của target. Với logging/metric phụ trợ, cần chính sách cô lập lỗi ghi metric. Với audit bắt buộc, lỗi ghi nhận có thể phải thay outcome. Không áp dụng `catch(Throwable) {}` nuốt mọi lỗi làm cách giải quyết mặc định.

### `start` phải được tạo ở đâu?

Đặt `start` trước `proceed` nếu muốn đo **thời gian mà around bao phủ**. Nếu đặt sau `proceed`, bạn chỉ đo thời gian hậu xử lý, không đo thời gian target. Nếu advice bên ngoài có retry, duration đo cả retry; nếu advice nằm bên trong retry, duration của từng attempt có thể khác. Vị trí nesting và ordering tác động tới nghĩa của metric.

## 5. Chọn dữ liệu nào được phép dùng chung

Không phải mọi mutable field đều sai. Một shared aspect có thể chứa **shared state hợp lệ**:

- `LongAdder` đếm tổng lời gọi.
- Một immutable policy snapshot hoặc reference publish an toàn tới cấu hình mới.
- Cache metadata theo method/type, có key chuẩn và synchronization phù hợp.
- Logger hoặc audit sink đã được thiết kế thread-safe.
- Bộ giới hạn tốc độ có state dùng chung đúng theo policy.
- Circuit state chung nếu đó là contract của cơ chế điều khiển lỗi.

Nhưng các field như `currentUser`, `currentRequestId`, `currentResult`, `startTime`, `lastThrowable` hoặc `retryAttempts` thường **không phù hợp** nếu chỉ phục vụ một invocation.

### Ví dụ tổng metric đúng scope

```java
// Một helper Java đơn giản; có thể nằm trong class phù hợp.
final class InvocationCounter {
    private final java.util.concurrent.atomic.LongAdder total =
            new java.util.concurrent.atomic.LongAdder();

    void increment() {
        total.increment();
    }

    long count() {
        return total.sum();
    }
}
```

`LongAdder` hữu ích cho bộ đếm tranh chấp cao; `sum()` không nhất thiết là snapshot nguyên tử tuyệt đối khi concurrent updates vẫn đang diễn ra. Điều này hợp lý cho metric gần đúng tại thời điểm đọc, nhưng không tự phù hợp nếu yêu cầu một bộ đếm transaction chính xác với strong consistency.

**Dùng local state cho dữ liệu riêng và shared concurrency-safe component cho dữ liệu tổng** thường là cách tách trách nhiệm tốt.

## 6. Ví dụ Java 17+ chạy được: field dùng chung sai ngay khi lời gọi lồng nhau

Ví dụ này dùng **Java Dynamic Proxy** làm interceptor AOP nhỏ, không cần thư viện AOP bên ngoài. Nó tạo hai proxy trên cùng một target:

1. **Shared-field proxy** cố giữ `currentId` vào field handler dùng chung; lời gọi outer và inner lồng nhau làm field bị ghi đè.
2. **Invocation-local proxy** giữ `id` trong local variable của từng lần handler được gọi; outer và inner có state riêng.
3. **Nhánh lỗi** minh họa state local vẫn dùng được khi target ném ngoại lệ và `finally` luôn chạy.

Để tạo nested intercepted calls một cách rõ ràng, target được cấp **self reference trỏ tới proxy** và chủ động gọi `self.execute("inner")`. Đây là một lựa chọn **cố ý của demo**. Trong mô hình wrapper proxy phổ biến, `this.execute("inner")` bên trong raw target thường **không** quay lại proxy; nếu không qua proxy thì inner advice cũng không chạy. Hai hiện tượng phải được phân biệt.

### 6.1. Tệp `InvocationStateDemo.java`

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class InvocationStateDemo {

    interface Service {
        String execute(String id);
    }

    static final class Target implements Service {
        private Service self;

        void setSelf(Service self) {
            this.self = self;
        }

        @Override
        public String execute(String id) {
            System.out.println("target.enter " + id);

            if ("outer".equals(id)) {
                self.execute("inner");
            }

            if ("fail".equals(id)) {
                throw new IllegalArgumentException("rejected");
            }

            System.out.println("target.exit " + id);
            return "result:" + id;
        }
    }

    static Object proceed(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    // Cố tình SAI: dùng chung một slot trạng thái cho mọi invocation.
    static final class SharedFieldHandler implements InvocationHandler {
        private final Service target;
        private String currentId;

        SharedFieldHandler(Service target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            currentId = (String) args[0];
            System.out.println("shared.before " + currentId);

            try {
                Object result = proceed(method, target, args);
                System.out.println("shared.after " + currentId);
                return result;
            } catch (Throwable error) {
                System.out.println("shared.error " + currentId);
                throw error;
            } finally {
                System.out.println("shared.finally " + currentId);
            }
        }
    }

    // ĐÚNG: local variable tách riêng cho mỗi lần handler chạy.
    static final class InvocationLocalHandler
            implements InvocationHandler {
        private final Service target;

        InvocationLocalHandler(Service target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            String id = (String) args[0];
            System.out.println("local.before " + id);

            try {
                Object result = proceed(method, target, args);
                System.out.println("local.after " + id);
                return result;
            } catch (Throwable error) {
                System.out.println(
                        "local.error " + id + " "
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                System.out.println("local.finally " + id);
            }
        }
    }

    static Service proxy(Service target, InvocationHandler handler) {
        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] { Service.class },
                handler);
    }

    public static void main(String[] args) {
        System.out.println("== shared field ==");
        Target badTarget = new Target();
        Service bad = proxy(
                badTarget, new SharedFieldHandler(badTarget));
        badTarget.setSelf(bad);
        System.out.println("caller " + bad.execute("outer"));

        System.out.println("== invocation local ==");
        Target goodTarget = new Target();
        Service good = proxy(
                goodTarget, new InvocationLocalHandler(goodTarget));
        goodTarget.setSelf(good);
        System.out.println("caller " + good.execute("outer"));

        System.out.println("== exception ==");
        try {
            good.execute("fail");
        } catch (IllegalArgumentException error) {
            System.out.println("caller caught " + error.getMessage());
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 InvocationStateDemo.java
java InvocationStateDemo
```

**Standard output dự kiến chính xác:**

```text
== shared field ==
shared.before outer
target.enter outer
shared.before inner
target.enter inner
target.exit inner
shared.after inner
shared.finally inner
target.exit outer
shared.after inner
shared.finally inner
caller result:outer
== invocation local ==
local.before outer
target.enter outer
local.before inner
target.enter inner
target.exit inner
local.after inner
local.finally inner
target.exit outer
local.after outer
local.finally outer
caller result:outer
== exception ==
local.before fail
target.enter fail
local.error fail IllegalArgumentException
local.finally fail
caller caught rejected
```

### 6.2. Vì sao shared-field proxy sai?

Khi gọi `bad.execute("outer")`, handler đặt `currentId = "outer"`. Target nhận outer rồi gọi `self.execute("inner")` — reference self đã được đặt thành **proxy**. Handler được gọi lần nữa và ghi `currentId = "inner"`. Khi inner kết thúc, field vẫn là `"inner"`.

Sau khi quay ra outer, `SharedFieldHandler` đọc lại **cùng field** và in:

```text
shared.after inner
shared.finally inner
```

Trong khi hai dòng cuối ấy thực chất thuộc **lượt outer**. Đây là lỗi correlation do **reentrancy**, không cần tạo thread thứ hai.

### 6.3. Vì sao local proxy đúng?

Trong `InvocationLocalHandler.invoke`, mỗi lời gọi tạo **biến local `id` riêng** từ argument của lần ấy. Outer giữ `"outer"` và inner giữ `"inner"`. Khi inner trả về, local variable của outer **không bị ghi đè**.

Do đó:

```text
local.after inner
local.finally inner
...
local.after outer
local.finally outer
```

phản ánh đúng hai invocation. Handler vẫn là **một instance chung** cho hai lời gọi, nhưng state cần correlation nằm trên từng stack frame.

### 6.4. Nhánh lỗi có mất state không?

`good.execute("fail")` ném `IllegalArgumentException("rejected")`. Reflection có thể bọc lỗi trong `InvocationTargetException`, nên hàm `proceed` mở lớp bọc để truyền Throwable gốc. Handler in `local.error fail IllegalArgumentException` rồi `local.finally fail`. Caller cuối cùng thấy đúng `rejected`.

Đoạn ví dụ không nuốt lỗi, không thay return value, và không dùng shared field để quyết định lỗi thuộc invocation nào.

### 6.5. Ý nghĩa của `target.setSelf(proxy)`

Chúng ta **cố ý** để target tự gọi một reference trỏ về proxy nhằm tạo **hai invocation advice lồng nhau**. Nếu thay `self.execute("inner")` bằng `this.execute("inner")` trong target và chỉ dùng proxy wrapper, lời gọi inner thường bypass handler. Khi đó demo không tạo nested advice, nhưng đây là vấn đề **interception coverage**, khác với tính đúng của shared field.

Một bytecode weaver hỗ trợ execution join point có thể can thiệp vào nested execution theo phạm vi khác. Không quy tắc hóa rằng mọi mô hình AOP đều có self-invocation giống Java Dynamic Proxy.

### 6.6. Giới hạn của ví dụ

- Một file Java SE với proxy interface, **không phải AOP engine/pointcut DSL hoàn chỉnh**.
- Handler shared-field cố ý sai để minh họa reentrancy; không dùng nó trong sản xuất.
- Ví dụ sử dụng một thread để có đầu ra xác định; shared-field cũng có lỗi trong đa luồng.
- Logger chỉ dùng `System.out.println`; metric/audit thật cần sink, redaction và error policy thích hợp.
- `id` và kết quả dạng chuỗi rất đơn giản; state mutable phức tạp còn cần ownership/synchronization.
- Không có retry, context propagation hoặc timer được triển khai trong demo chính.
- Không nên biến `self` proxy thành dependency vòng tròn mặc định chỉ để có nested advice; demo dùng nó có chủ đích để phơi bày lỗi.

## 7. Before và after tách rời: local state của hai advice có tự chia sẻ không?

Around advice lý tưởng cho phép giữ state giữa “trước” và “sau” bằng **cùng một stack frame**. Nhưng trong một số hệ thống AOP, người học có thể chỉ có **before advice** và **after advice** là hai phương thức Java riêng.

```text
before(joinPoint) -> tạo start
target(...)
after(joinPoint)  -> cần đọc lại start
```

Một biến local được khai báo **trong before** không tự chuyển thành biến local **của after**. Hai lần gọi advice là hai stack frames khác nhau. Cần một **cơ chế correlation** nếu thực sự phải truyền state từ before sang after.

### Những lựa chọn có thể dùng

1. **Around advice:** giữ state trong một invocation bao trọn target, nếu công cụ hỗ trợ.
2. **Invocation context object:** engine cung cấp một container gắn riêng với lượt gọi; before đặt value, after lấy đúng container ấy.
3. **Explicit scoped state:** một wrapper hoặc pipeline tự tạo object state rồi truyền giữa các hook theo invocation ID.
4. **Flow stack theo thread:** có thể dùng nếu toàn bộ scope đồng bộ cùng thread, và push/pop đúng thứ tự.
5. **Per-request/task context:** phù hợp nếu state cần được chia sẻ qua nhiều join point thuộc cùng request, không chỉ một method.

**Không nên mặc định có thể ghép đúng before và after chỉ bằng tên method, target object hoặc thread ID.** Một method có thể được gọi đệ quy, lồng nhau, chạy đồng thời hoặc cùng thread chạy nhiều request nối tiếp.

### Correlation key phải định danh đúng lượt gọi

Một key `(target,method)` có thể bị trùng giữa hai invocation cùng method trên cùng target. Một key `threadId` không phân biệt hai invocation lồng nhau trên cùng thread. Một key `requestId` có thể gộp nhiều method execution trong cùng request.

Nếu cần lưu “mốc thời gian của lần gọi X”, tốt nhất là có **invocation identity** hoặc một stack/scope bảo toàn cấu trúc lồng nhau. Không dùng identifier có phạm vi rộng hơn để giả làm identifier của từng lượt method.

## 8. Scoped stack cho lời gọi lồng nhau

Nếu một runtime chỉ có before/after và toàn bộ chuỗi chạy đồng bộ trên cùng thread, một cấu trúc stack theo thread có thể tương quan đúng hơn một field đơn:

```text
before outer  -> push State(outer)
before inner  -> push State(inner)
after inner   -> pop State(inner)
after outer   -> pop State(outer)
```

**Thứ tự LIFO** quan trọng. Nếu after chạy không theo thứ tự lồng, hoặc đã có async callback, stack thread có thể không phù hợp.

### Pseudocode minh họa

```java
// Mô tả ý tưởng, không phải chương trình độc lập.
ThreadLocal<Deque<InvocationState>> stack = ...;

void before(JoinPoint point) {
    stack.get().push(new InvocationState(point.id()));
}

void afterFinally(JoinPoint point) {
    InvocationState state = stack.get().pop();
    verifyMatch(state, point);
    if (stack.get().isEmpty()) {
        stack.remove();
    }
}
```

Các type `JoinPoint` và `InvocationState` được dùng như **khái niệm**. Với engine thật, phải xét:

- Advice before có chắc được gọi trước cùng một after-finally không?
- Một exception phát sinh ngay trong before có làm mất pop không?
- Advice có thể bị skip bởi around ở ngoài không?
- Recursive calls/nested join points có tuân theo LIFO không?
- Nếu target chuyển qua async, after chạy ở thread nào?
- Nếu cleanup ném lỗi, stack có vẫn được khôi phục không?

**Nếu không chứng minh được các điều kiện, không coi ThreadLocal stack là cơ chế universal để mô phỏng per-invocation.**

## 9. `ThreadLocal` là storage theo thread, không phải tự động theo lời gọi

`ThreadLocal<T>` cho mỗi thread một giá trị riêng theo object `ThreadLocal`. Nó giúp tránh một phần giao thoa giữa thread, nhưng không tự cung cấp invocation ID hoặc request lifecycle.

```java
// Mã minh họa, có thể nằm trong class.
private static final ThreadLocal<String> CURRENT =
        new ThreadLocal<>();
```

Nếu before đặt `CURRENT.set("A")`, inner advice cùng thread đặt `"B"`, thì sau khi inner kết thúc outer có thể thấy `"B"` nếu không restore. Đây chính là lỗi reentrancy dưới một hình thức khác.

### Scoped restore phải xử lý cả giá trị cũ

Một pattern an toàn hơn khi chỉ cần một slot theo thread:

```java
// Mã minh họa khi đã có Runnable work và ThreadLocal<String> CURRENT.
String previous = CURRENT.get();
try {
    CURRENT.set("current");
    work.run();
} finally {
    if (previous == null) {
        CURRENT.remove();
    } else {
        CURRENT.set(previous);
    }
}
```

Điều này bảo vệ giá trị scope bên ngoài khi flow lồng nhau **trên cùng thread**, nhưng nó chưa tự phân biệt các invocation khi caller cố tình giữ scope song song/bất đồng bộ. Nếu `null` là một giá trị có ý nghĩa khác với “chưa set”, cần một cấu trúc biểu diễn presence riêng.

### Vì sao `remove()` quan trọng trong thread pool?

Thread pool tái sử dụng worker. Nếu một request R1 đặt context vào `ThreadLocal` nhưng quên clear, request R2 trên cùng worker có thể vô tình đọc lại state của R1. Điều này có thể gây **rò dữ liệu** và sai audit/authorization context.

`remove()` giúp tránh giữ reference không cần thiết và tránh contamination, nhưng khi nested scope có giá trị trước đó, phải **restore** chứ không luôn remove vô điều kiện.

## 10. Async và executor: một invocation có thể vượt khỏi stack hiện tại

Một method có thể trả `CompletableFuture<T>` ngay, còn công việc thực chạy trên thread khác:

```java
// Java minh họa, không phải tệp độc lập.
CompletableFuture<String> future =
        CompletableFuture.supplyAsync(() -> compute());
```

Nếu around advice đo từ `before` tới thời điểm **method trả future object**, nó đo **thời gian khởi tạo/schedule**, không nhất thiết đo tới khi computation hoàn tất. `System.nanoTime()` local vẫn đúng cho phạm vi sync của method return, nhưng có thể **không đúng metric nghiệp vụ muốn đo**.

### Tách return của method và completion của task

```text
Caller invokes asyncMethod()
    |
    +-- Method returns CompletableFuture<T> --------> around ends?
              |
              +-- Worker executes later
                          |
                          +-- Future completes with T / error
```

Muốn đo **độ trễ đến completion**, có thể cần gắn callback/stage được cung cấp rõ theo contract. Việc tạo callback mới và trả một future mapped/observed có thể thay một số semantics liên quan identity/cancellation; phải chọn API phù hợp và kiểm thử.

### Capturing local state cho callback

Một callback có thể giữ một **immutable invocation context** được capture:

```java
// Minh họa trong nơi có CompletableFuture<T> future.
long start = System.nanoTime();
String operation = "fetch";
return future.whenComplete((value, error) -> {
    recordCompletion(operation, System.nanoTime() - start, error);
});
```

Đây là đoạn biểu diễn logic, **không phải chương trình độc lập**; kiểu trả về và behavior phụ thuộc declared method contract. `whenComplete` tạo một stage phụ có semantics riêng; nó không có nghĩa mọi failure/cancellation đều xuất hiện giống một return đồng bộ. Code đo còn phải tránh để lỗi của `recordCompletion` làm thay completion ngoài dự kiến.

**Capturing local trong callback** giúp tránh một shared `currentOperation`; nhưng context capture vẫn phải được kiểm soát về lifetime, bảo mật và kích thước dữ liệu.

## 11. Propagation: state nào nên truyền qua thread/task?

Một **invocation-local timer** có thể cần được dùng chỉ trong stack đồng bộ. Nhưng **trace ID của request** có thể cần đi qua nhiều method và task. Hai loại state không cùng scope.

| State | Thường nên nằm ở đâu? | Khi sang worker? |
| --- | --- | --- |
| Mốc đo một method đồng bộ | Local trong around | Không cần truyền nếu metric kết thúc tại return |
| Mốc đo một operation async | Immutable context/callback state | Capture/propagate có chủ đích |
| Trace ID của request | Request/task context | Phải truyền theo cơ chế hỗ trợ |
| Current transaction/resource scope | Boundary do resource owner quản lý | Không tự chuyển; cần contract cụ thể |
| Bộ đếm tổng service | Metric component shared | Dùng shared thread-safe component |
| Retry attempt của một call | Local trong retry around | Theo flow của retry |
| Authorization context | Context đã xác thực, giới hạn thời gian | Truyền và kiểm tra quyền đúng boundary |

**Không cố truyền toàn bộ aspect instance** chỉ để mang một trace ID. Điều này có thể giữ lại object graph, resource và state mutable không thuộc task mới. Nên truyền một **context tối thiểu, bất biến hoặc snapshot có ownership rõ**.

### Một request có nhiều nested calls

Một request R có thể gọi method A, rồi B, rồi C. Cùng trace ID là hợp lý cho cả ba; nhưng **mốc thời gian của A, B, C phải khác nhau**. Dùng một request-level field `startTime` để đo cả ba có thể sai vì chúng chồng lấn.

## 12. Per-invocation state và per-target state khác nhau

Một aspect có thể được tạo theo target: Target X dùng Aspect[X], Target Y dùng Aspect[Y]. Điều đó cô lập state **giữa target**, nhưng hai invocation trên cùng X vẫn có thể cùng dùng Aspect[X].

```text
Target X
   |
   +--> Invocation X1
   +--> Invocation X2
           |
        Aspect[X] (state dùng chung của target)
```

Nếu Aspect[X] có `currentId`, X1 và X2 vẫn có thể ghi đè nhau. **Per-target không thay cho invocation-local state.**

Ngược lại, tạo aspect mới cho mỗi invocation có thể cô lập state instance, nhưng phải xét chi phí tạo object, static fields và dependency dùng chung. Trong nhiều trường hợp, shared aspect + local variables **đơn giản hơn** mà vẫn đúng semantics.

### Khi nào per-target phù hợp?

Một bộ đếm tổng số thao tác của **riêng target X**, cache metadata per-target hoặc cấu hình gắn với identity X có thể phù hợp per-target. Nhưng thông tin “lượt chạy nào đang ở giai đoạn after” thường vẫn cần local/correlated invocation state.

## 13. Static field và singleton dependency phá cô lập nếu dùng sai

```java
// Ví dụ minh họa:
final class PerInvocationAspect {
    private static String LAST_ID;

    void before(String id) {
        LAST_ID = id;
    }
}
```

Dù tạo một `PerInvocationAspect` mới mỗi lần, `LAST_ID` vẫn là **static field** trong phạm vi class identity, được chia sẻ giữa các instance. Vì vậy lỗi correlation vẫn tồn tại.

Tương tự, một aspect instance mới có thể giữ reference đến **cùng một shared mutable helper**. Nếu helper có field `currentRequest`, chuyển scope của aspect không loại bỏ được lỗi ở helper.

### Kiểm tra toàn bộ object graph

Cần hỏi:

- Field trên aspect thuộc scope nào?
- Dependency được tạo mới hay được share?
- Static/global cache có chứa dữ liệu riêng request không?
- Closure/lambda có capture object mutable sống quá scope không?
- Có map lưu invocation context nhưng không remove khi hoàn tất không?
- Có một data structure được chia sẻ ngoài chủ đích ở phía target không?

**Isolation là đặc tính của đường tham chiếu và ownership**, không chỉ cách đặt tên lớp aspect.

## 14. Tính bất biến và snapshot cho per-call state

Một state có thể được biểu diễn bằng record Java 17+:

```java
// Minh họa một record dùng làm dữ liệu per invocation.
record InvocationContext(
        String operation,
        String traceId,
        long startedAtNanos
) { }
```

Một record như vậy là **shallowly immutable** nếu các component không thể bị thay bằng setter. Nhưng nếu component là `List` mutable, record vẫn có thể tham chiếu tới collection bị sửa; **record không tự deep-copy dữ liệu**.

Một immutable invocation context giúp:

- Giảm nguy cơ advice sửa ngầm thông tin của lời gọi khác.
- Truyền/capture context qua callback rõ ràng.
- Viết test với input xác định.
- Tránh một field mutable “current invocation” trên singleton.

Nhưng nó không tự tạo transaction, bảo đảm authorization hay context propagation qua mọi executor.

### Context tối thiểu

Không cần copy toàn bộ argument list hoặc user object cho mọi invocation. Chỉ giữ các trường thật sự cần: operation name, correlation ID không nhạy cảm, mốc thời gian, flags policy. Nếu lưu return object/Throwable vào context sống lâu, có thể kéo dài lifetime của dữ liệu lớn và thông tin nhạy cảm.

## 15. Đo thời gian đúng: đơn vị và đồng hồ

Đối với duration bên trong một JVM, `System.nanoTime()` thường thích hợp vì dùng để đo **elapsed time**, không phải biểu diễn thời gian lịch. Giá trị tuyệt đối `nanoTime` không phải UNIX timestamp và không nên lưu nó như ngày giờ.

```java
// Minh họa trong một around advice.
long start = System.nanoTime();
try {
    return proceed();
} finally {
    long elapsedNanos = System.nanoTime() - start;
    timer.record(elapsedNanos);
}
```

Nếu một invocation khác ghi `start` vào shared field giữa chừng, con số `elapsedNanos` có thể sai dù phép trừ dùng đúng API.

### Những câu hỏi về semantics duration

- Đo từ trước validation hay chỉ phần business method?
- Đo mỗi attempt hay cả tổng retry?
- Bao gồm logging/audit advice khác không?
- Method trả future: đo tới return hay completion?
- Có đo khi target ném exception không?
- Đơn vị output là nanos, microseconds hay milliseconds?
- Có đo cả thời gian chờ lock/IO không?
- Metric sink có overhead ảnh hưởng timing không?

**Local state bảo đảm mốc thuộc đúng invocation; vị trí advice và contract mới quyết định metric đo cái gì.**

## 16. State cho retry không được dùng chung

Một around advice có khả năng gọi `proceed` nhiều lần có thể retry. Bộ đếm số lần thử nên thuộc **lượt gọi bên ngoài đang retry**:

```java
// Pseudocode, không phải chương trình độc lập.
int attempt = 0;
while (true) {
    attempt++;
    try {
        return proceed();
    } catch (TransientFailure error) {
        if (attempt >= maxAttempts) {
            throw error;
        }
    }
}
```

Nếu `attempt` nằm trong field shared, hai invocation retry đồng thời sẽ can thiệp lẫn nhau; caller A có thể bị dừng do số attempt của caller B.

**Điều kiện thêm:** Retry chỉ an toàn với policy lỗi/timeout/idempotency phù hợp. Đối tượng target có thể đã gây side effect trước khi ném timeout; trạng thái per-invocation đúng chưa làm phép retry an toàn nghiệp vụ.

## 17. State cho audit/tracing: correlation phải có ngữ nghĩa

Một audit event có thể cần:

- Loại thao tác.
- ID của target hoặc entity.
- Trạng thái success/failure.
- Trace/correlation ID.
- Thời gian bắt đầu/kết thúc.
- Một số trường dữ liệu được phép ghi.

Từng trường có scope riêng. Ví dụ **trace ID** có thể dùng chung cho request, còn **span ID** gắn một operation cụ thể. Không nên dùng một field `lastTraceId` trên aspect singleton để kết nối mọi callback.

### Lỗi dễ gây rò thông tin

`System.out.println(args)` trong một advice có thể vô tình ghi credential, token hoặc PII. Việc chuyển từ shared field sang per-call context **không giải quyết tự động chính sách bảo vệ dữ liệu**. Cần redaction, giới hạn retention và sink được phép nhận thông tin tương ứng.

Một per-call context capture lớn giữ request object trong callback sống lâu có thể khiến dữ liệu nhạy cảm tồn tại lâu hơn dự tính. Context càng nhỏ, bất biến và có TTL/cleanup rõ càng dễ kiểm soát.

## 18. Cùng một thread nhưng nhiều flow: stack và cleanup

Một thread trong server có thể lần lượt xử lý R1, R2; hoặc một request có thể gọi nested flow:

```text
Thread T:
  enter R1
     enter nested operation
     exit nested operation
  exit R1

  enter R2
  exit R2
```

Nếu dùng một ThreadLocal slot nhưng quên restore trong nested operation, R1 có thể thấy giá trị của nested operation. Nếu quên `remove()` sau R1, R2 có thể nhận dữ liệu R1.

Vì vậy một scoped context cần cân nhắc:

1. **Push/replace** khi bắt đầu.
2. **Restore previous** khi kết thúc.
3. **Remove** khi không còn scope.
4. **Cleanup trong finally** dù target ném lỗi.
5. **Không propagate ngầm** qua ranh giới task nếu chưa có chính sách.
6. **Không dùng lại** context đã kết thúc.

**Một biến local của around** không cần những thao tác quản lý thread này nếu chỉ dùng trong cùng advice invocation. Hãy ưu tiên giải pháp đơn giản nhất phù hợp phạm vi.

## 19. Không tự coi mọi advice chạy trên cùng thread

Một số AOP model quan sát method execution đồng bộ ngay trên thread target; nhưng các hệ thống callback/event/interceptor có thể chuyển xử lý qua thread khác hoặc hẹn sau khi method trả.

Nếu before chạy ở thread A và cleanup callback ở thread B, `ThreadLocal` của A sẽ không tự cho B truy cập đúng state. Cần carrier tường minh hoặc correlation do engine bảo đảm.

**Đừng thiết kế một câu “before luôn đặt ThreadLocal, after luôn lấy được” như chân lý chung** mà không biết chính xác runtime AOP/hook đang sử dụng.

### CompletableFuture completion

Một future có thể hoàn tất thành công, thất bại hoặc bị cancel. Completion callback có thể chạy ở thread hoàn tất hoặc thread do API/executor chọn. Advice đo operation async phải xử lý outcome tương ứng và tránh giữ scope sau completion.

Nếu callback được gắn nhiều lần hoặc retry tái sử dụng state, cần định nghĩa **completion được ghi đúng một lần** theo event semantics mong muốn. Đừng lạm dụng một shared `finished` boolean cho mọi invocation; nếu cần cờ, nó thuộc context cụ thể hoặc một state machine theo operation ID.

## 20. Exception: không để cleanup đổi outcome ngoài chủ đích

Giả sử target ném `IllegalArgumentException`, nhưng advice cố ghi metric rồi sink cũng ném lỗi:

```text
Target throws OriginalError
   |
Advice.finally -> MetricSink throws SinkError
   |
Caller may see SinkError instead of OriginalError
```

Nếu metric chỉ là quan sát phụ trợ, lỗi sink có thể cần được cô lập theo policy phù hợp. Nếu audit bắt buộc, có thể cần fail operation. Không có quy tắc duy nhất cho mọi concern; phải chọn theo yêu cầu.

### Local state vẫn đúng khi exception xảy ra

Với around advice:

```java
// Pseudocode cho một around advice.
String id = currentInvocationId();
try {
    return proceed();
} catch (Throwable error) {
    recordFailure(id, error);
    throw error;
} finally {
    closeScope(id);
}
```

Local `id` vẫn là của invocation đó trên đường throw. Nếu thay bằng shared field, một advice lồng nhau có thể làm `recordFailure` gắn lỗi của outer sang ID inner.

**Không bắt Throwable rồi nuốt mọi lỗi** chỉ để tránh làm hỏng business flow; chú ý cả những lỗi nghiêm trọng không nên bị biến thành success giả.

## 21. Trạng thái theo target không tự giải quyết concurrency

Một target instance X được dùng bởi hai thread:

```text
Thread A --> Target X --> Aspect[X]
Thread B --> Target X --> Aspect[X]
```

Nếu aspect theo target có field `start` hoặc `currentId`, cả hai thread vẫn tranh chấp. Per-target chỉ cô lập **giữa X và Y**, không cô lập **giữa các lần gọi X**.

Một aspect per-control-flow cũng có ngữ nghĩa khác: nó cô lập theo một flow động được engine định nghĩa, thường liên quan call stack. Một flow async không tự nối qua executor.

**Quy tắc:** Scope của state phải khớp **đối tượng logic mà state mô tả**, không phải chỉ khớp một điểm có vẻ gần đúng như target hoặc thread.

## 22. Shared aspect + local state có những lợi ích gì?

1. **Ít state dùng chung để đồng bộ:** mỗi invocation có local variables.
2. **Tránh contamination:** invocation A không đọc nhầm context invocation B.
3. **Giảm chi phí lifecycle:** không cần tạo một aspect object mới chỉ để lưu mốc thời gian.
4. **Dễ kiểm thử:** invocation có input/outcome độc lập.
5. **Hỗ trợ reentrancy:** nested calls có stack frames riêng.
6. **Tương thích nhiều mô hình weaving:** nếu engine gọi cùng một method advice trên shared instance, Java locals vẫn là riêng từng lần gọi.
7. **Dễ reason về failure:** `try/catch/finally` trong cùng invocation thể hiện ranh giới.
8. **Giảm rủi ro memory retention:** không lưu request object trong field sống dài.

Những lợi ích này không có nghĩa shared aspect **luôn đúng**. Nếu concern thật sự cần state per-target/per-flow, hãy dùng scope tương ứng. Nhưng nếu dữ liệu chỉ tồn tại một lời gọi thì local state là lựa chọn mặc định mạnh.

## 23. Trường hợp local state vẫn chưa đủ

### 23.1. Before/after là hai method tách biệt

Local của before không hiện diện trong after. Cần invocation context hoặc scope manager.

### 23.2. Async callback cần dữ liệu sau khi method return

Local có thể được lambda capture, nhưng lifecycle/lifetime của closure cần tương ứng operation async, không chỉ stack đồng bộ.

### 23.3. Nhiều join point thuộc cùng request

Trace ID của request dùng chung cho nhiều invocation; mỗi advice có thể đọc từ request context. Không cần copy mutable state vào singleton field.

### 23.4. State cần giữ qua nhiều lần gọi target

Một counter mỗi target phải sống lâu hơn một invocation; local sẽ reset. Dùng per-target hoặc shared map với key đúng và cleanup.

### 23.5. Kết quả cần aggregate đa luồng

Một local counter riêng không thay cho tổng metric đa thread. Cần component tổng hợp an toàn.

**Không biến “trạng thái theo lời gọi” thành yêu cầu mọi dữ liệu phải local.** Đúng nghĩa là phân loại và gắn vòng đời đúng cho từng dữ liệu.

## 24. Đặt state vào đối tượng context tường minh

Khi có nhiều biến per-invocation hoặc cần truyền qua các helper, tạo context object có thể dễ đọc hơn:

```java
// Java 17+ minh họa data holder bất biến.
record CallInfo(
        String operation,
        String correlationId,
        long startedAtNanos
) { }
```

Một around advice có thể tạo một `CallInfo` rồi truyền tới các hàm đo/ghi log. Với dữ liệu mutable chỉ nằm trong một invocation, có thể dùng một state object riêng nhưng phải bảo đảm **không publish object ấy cho invocation khác**.

### Chú ý ownership

- Ai tạo context?
- Ai được phép sửa?
- Context có thread-safe nếu truyền vào nhiều tasks không?
- Có cần snapshot/copy để tránh bị mutate?
- Khi nào context không còn hợp lệ?
- Có chứa secrets không?
- Có giữ reference tới target/return value lớn hơn cần thiết không?

**Context object không tự biến thành per-invocation chỉ vì được đặt tên `InvocationContext`.** Nếu bạn lưu một instance context duy nhất vào field shared và tái dùng, nó vẫn là shared state.

## 25. Key correlation: identity của join point không đủ

Hai lượt gọi `Service.save("A")` và `Service.save("B")` có thể có:

- Cùng method signature.
- Cùng target object.
- Cùng thread (lồng nhau hoặc chạy nối tiếp).
- Cùng request ID.
- Cùng pointcut match.

Nhưng chúng là **hai invocation khác nhau**. Nếu muốn lưu start time của từng lượt, key phải phân biệt được chúng. Một method metadata key phù hợp **cache reflection metadata**, không phù hợp **lưu timer đang chạy của một lời gọi**.

### Identity và memory

Nếu tạo một map `Map<InvocationId,State>` dùng chung để nối before/after, phải có:

- ID duy nhất trong phạm vi cần thiết.
- Map/thread safety.
- Cleanup trong mọi đường return/throw/cancel.
- Timeout xử lý orphaned state.
- Giới hạn bộ nhớ và số lượng in-flight calls.
- Không tái sử dụng ID quá sớm gây collision.

Trong một lời gọi đồng bộ có around, **local state thường giảm đáng kể những phức tạp này**.

## 26. Kiểm thử concurrency bằng điều khiển thứ tự, không dựa vào may rủi

Một test kiểu “tạo 100 thread và hy vọng lộ race” không đủ chắc. Nên dùng các primitive như `CountDownLatch` hoặc `CyclicBarrier` để buộc thứ tự xen kẽ.

Ví dụ kịch bản kiểm lỗi field dùng chung:

```text
Task A: before(A) -> báo ready -> chờ B
Task B: chờ A ready -> before(B) -> báo done
Task A: sau B done -> after(A) -> kiểm ID có phải A?
```

Nếu aspect dùng một field `currentId`, test có thể quan sát giá trị B ở after(A). Khi chuyển sang local/invocation context, after(A) vẫn có A.

Đừng đưa `Thread.sleep(...)` với thời gian cố định làm cách duy nhất chứng minh race; scheduler khác nhau khiến test flaky.

### Kiểm thử reentrancy còn rẻ hơn

Ví dụ `InvocationStateDemo.java` đã tái hiện lỗi **tất định trên một thread**. Đây là một bài test quan trọng ngay cả khi ứng dụng tưởng chỉ xử lý đồng bộ.

## 27. Ma trận test state theo invocation

| Ca kiểm thử | Điều cần chứng minh |
| --- | --- |
| Hai lời gọi tuần tự | State không bị giữ sai qua lần sau |
| Hai lời gọi concurrent | ID/start/result của từng invocation độc lập |
| Nested call trên cùng thread | Outer state được bảo toàn sau inner |
| Recursion nhiều mức | Mỗi mức có state riêng và unwind đúng |
| Target thành công | Advice dùng đúng state và truyền return đúng contract |
| Target ném lỗi | Error log gắn đúng invocation, cleanup chạy |
| Advice tự ném lỗi | Không để context lẫn sang lượt khác |
| After/cleanup ném lỗi | Outcome được xử lý đúng policy |
| Around không proceed | State vẫn được cleanup khi skip target |
| Around proceed nhiều lần | Đếm attempt khác số invocation bên ngoài |
| Before/after riêng | Correlation key/stack đúng scope |
| ThreadLocal nested | Restore giá trị trước đúng thứ tự |
| Thread pool tái sử dụng worker | Không còn context từ request trước |
| Async future | Đo tới method return hay completion đúng mong muốn |
| Callback bị cancel | Context được giải phóng và outcome được ghi hợp lệ |
| Một target nhiều proxy | Aspect state thuộc proxy, target hay invocation? |
| Per-target aspect | Hai calls cùng target vẫn tách invocation state |
| Raw target bypass proxy | Không nhầm thiếu advice với lỗi state |
| Shared metric counter | Tổng metric đúng, không dùng field per-call |
| Class loader/plugin reload | Cache/context không giữ resource/loader cũ |
| Sensitive arguments | Log không lộ dữ liệu dù per-call state đúng |

Test nên so sánh **state trước và sau**, **số lần advice chạy**, **số lần target thực thi**, **caller thấy kết quả/exception gì**, và **còn reference không mong muốn sau completion không**.

## 28. Chẩn đoán lỗi: state sai hay advice không chạy?

Khi log có ID sai hoặc thiếu dòng, cần phân loại:

**Trường hợp A — Advice chạy nhưng state sai.** Ví dụ field `currentId` bị inner invocation ghi đè. Cần sửa ownership/scope của state.

**Trường hợp B — Advice không chạy.** Có thể lời gọi bypass proxy, pointcut không match, aspect không được weave, hoặc around ngoài không `proceed`. Local state không thể giúp một advice chưa được gọi.

**Trường hợp C — Advice chạy, state đúng nhưng sink không xuất log.** Logging/metric có thể được disable, sampled hoặc thất bại. Cần tách “đã vào advice” khỏi “đã xuất event”.

**Trường hợp D — Advice chạy trên thread khác.** Context propagation chưa đúng, dù proxy/weaving coverage vẫn ổn.

Cách debug:

1. Xác định invocation path và target.
2. Xác định aspect instance identity/lifecycle.
3. Xác định vị trí lưu state, owner và key.
4. Kiểm tra reentrancy/concurrency.
5. Kiểm tra return/throw/finally và cleanup.
6. Kiểm tra async handoff, context carrier.
7. Kiểm tra output sink và masking dữ liệu.
8. Kiểm tra scope manager/cache và memory retention.

Không vội dùng `synchronized` quanh toàn bộ business logic để chữa một lỗi đáng ra giải quyết bằng local variable.

## 29. Những hiểu lầm phổ biến

1. **“Singleton aspect phải dùng singleton state cho mọi thứ.”** Instance dùng chung vẫn có local state riêng mỗi invocation.
2. **“Chỉ có một thread thì không thể sai.”** Nested/reentrant advice vẫn có thể ghi đè field.
3. **“`volatile` làm currentId trở thành per-call.”** Volatile không tạo slot theo invocation.
4. **“`AtomicReference` sửa được log lẫn request.”** Atomic không giải quyết correlation.
5. **“Per-target aspect là per-invocation.”** Một target có thể được gọi nhiều lần đồng thời.
6. **“ThreadLocal là cùng một thứ với request scope.”** Thread pool và async làm hai scope khác nhau.
7. **“ThreadLocal không cần remove vì thread sẽ kết thúc.”** Worker thread có thể sống rất lâu.
8. **“Chỉ remove là đủ với nested flow.”** Phải restore previous context thích hợp.
9. **“Before local variable tự tồn tại ở after.”** Hai advice method có stack frame khác.
10. **“Method name là invocation ID.”** Nhiều lời gọi trùng method và target.
11. **“Aspect per-invocation làm static field riêng từng lần.”** Static theo class identity.
12. **“Capture cả request object vào callback là vô hại.”** Có thể kéo dài lifetime và lộ dữ liệu.
13. **“Method trả future nghĩa là công việc xong.”** Method return khác future completion.
14. **“Local timer chắc chắn đo end-to-end.”** Còn phụ thuộc advice nesting và async boundary.
15. **“Shared aspect không thể giữ cache.”** Cache chung hợp lệ nếu key và concurrency đúng.
16. **“Thêm lock sửa mọi lỗi state.”** Lock không sửa scope gắn sai.
17. **“Lỗi audit chỉ là lỗi log.”** Với audit bắt buộc, policy có thể đòi fail-closed.
18. **“Một proxy là một aspect invocation.”** Một proxy có thể được gọi nhiều lần; handler có state dùng chung.
19. **“Có scope context là tự có security.”** Context có thể stale/giả mạo; quyền cần kiểm tra ở boundary tin cậy.
20. **“Dùng per-call state luôn tiết kiệm bộ nhớ hơn.”** Captured context hoặc map không cleanup có thể giữ dữ liệu rất lâu.
21. **“Một lời gọi ngoài qua proxy thì mọi lời gọi nội bộ cũng qua proxy.”** Với wrapper proxy, `this.inner()` thường bypass.
22. **“Mọi AOP engine đều cung cấp invocation context map.”** Phải kiểm tra API thực tế hoặc tự quản lý scope.
23. **“Một record bảo đảm deep immutability.”** Component có thể tham chiếu object mutable.
24. **“Đúng correlation ID là metric chính xác.”** Còn phải chọn đúng điểm bắt đầu/kết thúc, retry và completion.

## 30. Bài tập thực hành

1. Chạy `InvocationStateDemo.java` và đối chiếu từng dòng stdout.
2. Giải thích tại sao `shared.after inner` xuất hiện **hai lần** dù lần outer đã kết thúc.
3. Đổi `self.execute("inner")` thành `this.execute("inner")` và giải thích vì sao mất nested proxy advice; kết luận này có áp dụng cho mọi bytecode weaving engine không?
4. Thêm một lượt `good.execute("single")`. Dự đoán log local và outcome.
5. Thêm recursion `level3 → level2 → level1`. So sánh shared field và local state.
6. Chuyển `SharedFieldHandler.currentId` thành `volatile String`. Vì sao lỗi logic không đổi?
7. Chuyển sang `AtomicReference<String>`. Kiểm thử lại reentrancy và giải thích vì sao vẫn sai.
8. Viết một around advice đo `System.nanoTime` bằng local variable trong `try/finally`; đo riêng success và throw.
9. Mô phỏng before và after riêng bằng một `ThreadLocal<Deque<State>>`. Thêm test nested + exception + cleanup.
10. Dùng một executor một thread chạy hai request nối tiếp; cố ý để lại ThreadLocal từ request đầu rồi quan sát request sau, sau đó sửa bằng restore/remove.
11. Tạo hai worker thread dùng cùng shared aspect. Điều khiển interleaving bằng latch để chứng minh shared `currentId` sai, local `id` đúng.
12. Tạo một `LongAdder` tổng số advice invocations trong shared component; so sánh với local attempt counter.
13. Thiết kế invocation context chứa operation, trace ID và timestamp, nhưng không chứa password/token.
14. Một method trả `CompletableFuture<String>`. So sánh timer đo tới return của future và timer đo tới completion.
15. Thêm callback error path, xác định cách không để metric sink exception che business exception.
16. Thiết kế một retry around advice có attempt count local, giới hạn lần thử, deadline và idempotency key.
17. Với target X có aspect per-target, cho hai thread gọi X đồng thời; state nào vẫn phải là per-invocation?
18. Thêm static field `lastId` vào một aspect được tạo per-invocation và giải thích vì sao nó làm mất isolation.
19. Xây map `InvocationId → State` cho before/after tương quan. Nêu cleanup trong return, throw, timeout, cancellation và memory bounds.
20. Vẽ flow của request R đi qua executor và callback. Phân biệt context per-request với state per-method invocation.
21. Viết test phân biệt lời gọi không match pointcut, lời gọi bypass proxy và advice đã chạy nhưng logger bị tắt.
22. Đề xuất một phương án audit an toàn khi dữ liệu kết quả chứa thông tin nhạy cảm mà nhiều invocation chạy đồng thời.
23. So sánh dùng biến local `long start` với shared `Map<Thread,Long>` cho một around advice đồng bộ; vì sao biến local đơn giản và chính xác hơn?
24. Thay một shared mutable field `lastThrowable` bằng việc truyền Throwable của invocation hiện tại vào hàm record failure; xem ảnh hưởng tới reentrancy.
25. Thiết kế test ba tầng: **đúng instance/lifecycle**, **đúng correlation state**, **đúng kết quả caller**.

**Tự kiểm tra:** Vì sao một aspect singleton vẫn có thể dùng biến local? Có thể sai trên một thread không? Vì sao `ThreadLocal` không tự thành per-invocation khi có recursion? Trong before/after riêng cần cơ chế nào để nối state đúng? Vì sao `CompletableFuture` làm thay đổi ý nghĩa mốc kết thúc? Nếu per-target aspect có nhiều thread cùng gọi một target, liệu state theo lời gọi đã được cô lập chưa?

## Kết luận

**Lưu trạng thái theo lời gọi thay vì trong field dùng chung là nguyên tắc then chốt để aspect hoạt động đúng khi có concurrency, recursion và reentrancy.** Với một around advice đồng bộ, **local variables cộng `try/finally`** thường là giải pháp đơn giản và tin cậy nhất cho timer, correlation ID tạm và retry attempt. Nếu before/after bị tách rời hoặc xử lý băng qua async/task, phải dùng **invocation context, scope stack hoặc propagation có contract rõ**, cùng cơ chế restore/cleanup đầy đủ.

Không phải mọi state đều thuộc invocation: metric tổng, cấu hình bất biến hoặc cache metadata có thể dùng chung nếu thread-safe; state gắn target hoặc request cần lifecycle tương ứng. Thiết kế tốt phải **xác định rõ owner của mỗi dữ liệu, phạm vi tồn tại, quan hệ với các invocation lồng nhau, khả năng đồng thời và đường cleanup**, rồi kiểm thử bằng các luồng thực thi có cả success, exception và async handoff. Điều này giúp AOP vừa giữ được lợi ích cắt ngang, vừa tránh lẫn dữ liệu giữa những công việc độc lập.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-lifecycle-008">Nguy cơ rò rỉ bộ nhớ và giữ đối tượng ngoài ý muốn</a>

<details>
<summary>Xem chi tiết</summary>

# Nguy cơ rò rỉ bộ nhớ và giữ đối tượng ngoài ý muốn trong vòng đời aspect (AOP)

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), một **aspect** chứa mã cắt ngang (*advice*) áp dụng tại các **điểm can thiệp** (*join point*) được **pointcut** lựa chọn. Aspect có thể tồn tại suốt vòng đời ứng dụng, theo từng đối tượng, từng luồng điều khiển hoặc theo một chính sách instantiation riêng nếu công nghệ hỗ trợ. Chính sự chênh lệch giữa **vòng đời của aspect** và **vòng đời dữ liệu mà advice quan sát** tạo ra nguy cơ rò rỉ bộ nhớ (*memory leak*) hoặc giữ đối tượng lâu hơn dự kiến (*unintended retention*).

Ví dụ, một aspect dùng chung suốt ứng dụng ghi `lastRequest = request` trong field. Sau khi xử lý xong, request lẽ ra không cần thiết nữa, nhưng **aspect vẫn giữ tham chiếu mạnh tới request**. Nếu request còn giữ một payload lớn, session, user context hoặc cây đối tượng, những đối tượng ấy có thể tiếp tục *reachable* từ một gốc GC (*GC root*) và chưa được thu gom.

Nguy cơ không chỉ là một field. Listener đăng ký ở singleton event bus, `ThreadLocal` trên thread pool, cache metadata khóa theo `Class`/`ClassLoader`, callback bất đồng bộ hoặc proxy được lưu trong registry sống lâu đều có thể làm **vòng đời của một đối tượng ngắn hạn bị kéo dài**. Ngược lại, **không phải mọi đối tượng còn sống sau một lời gọi đều là leak**: cache có giới hạn và dữ liệu cần được giữ theo hợp đồng vẫn có thể là retention hợp lệ.

Bài học này trình bày **AOP tổng quát**, phân biệt chiến lược vòng đời aspect với vòng đời object/tài nguyên; đưa ra mô hình tham chiếu, cách phòng tránh, kiểm thử và ví dụ Java 17 chạy độc lập. Khả năng của mỗi cơ chế weaving/proxy, định nghĩa instance per-target/per-flow và cleanup hook khác nhau theo công nghệ; không giả định mọi AOP có cùng API hoặc tự thu hồi tài nguyên hộ người lập trình.

## 1. Mục tiêu học tập

Sau bài, bạn có thể:

1. Phân biệt **memory leak**, **giữ đối tượng ngoài ý muốn**, **tăng bộ nhớ tạm thời** và **rò rỉ tài nguyên ngoài heap**.
2. Giải thích cơ chế **GC root → strong reference → aspect/proxy/context → object ngắn hạn** khiến đối tượng còn reachable.
3. Đánh giá rủi ro của singleton aspect, aspect theo đối tượng, aspect theo luồng điều khiển và các trạng thái lưu trong advice.
4. Nhận diện listener/callback chưa hủy, `ThreadLocal` trên thread pool, cache không giới hạn và class-loader retention.
5. Hiểu vì sao vòng tham chiếu đơn thuần không nhất thiết là leak trong JVM dùng tracing garbage collection.
6. Thiết kế **ownership**, lifecycle, đăng ký/hủy đăng ký, `try/finally`, `AutoCloseable` và giới hạn cache phù hợp.
7. Đọc ví dụ Java 17 cho thấy event bus giữ aspect qua listener và cách một scope `ThreadLocal` được khôi phục khi lồng nhau/throw.
8. Kiểm tra leak bằng phép đo retention và heap analysis, không dựa vào `System.gc()` để khẳng định thu hồi đúng thời điểm.
9. Giữ advice theo dõi đơn giản, ít dữ liệu nhạy cảm và không làm thay kết quả hoặc ngoại lệ nghiệp vụ.

## 2. Memory leak trong Java có nghĩa gì?

Một chương trình Java được JVM thu gom rác, nhưng GC chỉ có thể thu gom những đối tượng **không còn reachable từ các GC root** theo mô hình collector. Các GC root có thể bao gồm những tham chiếu trên stack của thread đang chạy, các trường static, JNI handle hoặc những root nội bộ khác.

Nếu một object không còn cần cho nghiệp vụ nhưng vẫn reachable từ root qua chuỗi tham chiếu, nó **không được thu gom** dù không ai trong logic mong muốn sử dụng nữa.

```text
GC ROOT (static holder)
        |
        v
Long-lived Aspect instance
        |
        +--> lastRequest
                  |
                  v
             RequestContext
                  |
                  v
              BigPayload
```

Nếu `lastRequest` vẫn trỏ tới `RequestContext` sau khi lời gọi xong, object và những object nó tham chiếu có thể còn reachable.

### 2.1. Leak không đồng nghĩa luôn tăng mãi

Một field `lastRequest` giữ **một** request cuối có thể tạo retention không mong muốn nhưng không nhất thiết làm heap tăng mãi theo số request. Ngược lại, một `List<Request>` chỉ thêm mà không xóa có thể tăng tỷ lệ thuận với số lượt gọi.

Cần phân biệt:

| Hiện tượng | Mô tả | Kết luận nên thận trọng |
| --- | --- | --- |
| **Unintended retention** | Dữ liệu đã hết nhu cầu nhưng vẫn được tham chiếu | Cần xem vòng đời và ownership |
| **Unbounded growth** | Số phần tử giữ lại tăng mà không có giới hạn phù hợp | Nguy cơ lớn dẫn tới thiếu bộ nhớ |
| **Valid cache retention** | Dữ liệu còn được giữ có chủ đích trong ngân sách | Chưa chắc là leak |
| **Temporary allocation spike** | Nhiều object được tạo trong một đợt tải | Có thể GC về sau, chưa chứng minh leak |
| **Off-heap/resource leak** | File handle, socket, direct buffer, connection không được trả đúng lúc | Có thể gây lỗi dù heap không tăng |
| **GC cycle** | A trỏ B và B trỏ A, không còn reachable từ root | JVM có thể thu gom chu trình này |

Một **vòng tham chiếu không tự tạo leak** trên JVM dùng tracing GC. Nguy cơ là **đường tham chiếu tới một GC root sống quá lâu**.

### 2.2. Không lấy việc GC chưa chạy làm bằng chứng leak

Sau một lượt request, heap có thể vẫn hiển thị vùng nhớ cũ vì JVM chưa cần thu gom hoặc vẫn giữ vùng heap đã cấp. Quan trọng là **số object còn được giữ sau những đợt GC phù hợp**, đường giữ tham chiếu, tuổi thọ/khả năng tiếp tục tăng và hợp đồng nghiệp vụ.

`System.gc()` chỉ là yêu cầu/hint trong những cấu hình JVM, không bảo đảm object đã được thu gom ngay hoặc một đối tượng cụ thể phải biến mất. Vì thế ví dụ chạy được trong bài **không đo leak bằng cách chờ WeakReference trở thành null**; nó đo cấu trúc tham chiếu và số đăng ký một cách xác định.

## 3. Vì sao vòng đời aspect tạo nguy cơ riêng?

Trong AOP, developer thường viết advice như một đoạn “chạy thêm tại nhiều nơi”, nhưng các field của **aspect instance** có thể sống lâu hơn rất nhiều so với biến cục bộ của mỗi advice invocation.

Giả sử:

```java
class TrackingAspect {
    Object lastArguments;

    void observe(Object[] arguments) {
        lastArguments = arguments;
    }
}
```

Đây là đoạn minh họa, không phải một aspect hoàn chỉnh. Nếu `TrackingAspect` là singleton sống suốt ứng dụng, field `lastArguments` tiếp tục giữ mảng đối số và các object được mảng ấy tham chiếu **sau khi phương thức nghiệp vụ kết thúc**. Nếu array chứa request, payload hoặc kết nối, retention lan theo đồ thị object.

Sửa bằng cách giữ **thông tin nhỏ, bất biến và có chủ đích**, chẳng hạn số lượng lời gọi, loại join point hoặc một ID đã được xử lý an toàn; hoặc giữ dữ liệu trong biến cục bộ và không xuất nó sang một holder sống lâu.

### 3.1. Từng kiểu instantiation có phạm vi retention khác

| Chính sách vòng đời | Gắn instance với | Nguy cơ khi giữ state |
| --- | --- | --- |
| **Shared/singleton aspect** | Một instance dùng chung theo phạm vi công nghệ | Field có thể giữ dữ liệu từ bất kỳ request nào; nguy cơ lẫn dữ liệu concurrent |
| **Per-target/per-this** nếu hỗ trợ | Đối tượng target/`this` tương ứng | Instance sống theo quan hệ giữ target; có thể tạo vòng tham chiếu hoặc retention qua registry |
| **Per-control-flow** nếu hỗ trợ | Một activation luồng điều khiển động | Context không pop/remove khi return/throw; nested flow khôi phục sai; async propagation sai |
| **Per-type** nếu hỗ trợ | Loại/lớp tương ứng | Metadata hoặc cache có thể giữ class loader lâu sau khi cần unload |
| **Per-invocation state** trong biến cục bộ | Một lần advice chạy | Thường ngắn hơn, trừ khi escape vào callback/cache/closure |
| **Custom scope** | Token hoặc context do chương trình cấp | Ownership và teardown do chương trình định nghĩa |

Từ “per-target” **không tự bảo đảm không leak**. Một cache toàn cục ánh xạ target → aspect instance có thể giữ target sống mãi nếu khóa được giữ mạnh. Ngược lại, một aspect có field trỏ target và target trỏ aspect tạo chu trình nhưng **nếu toàn bộ chu trình không reachable từ root**, GC vẫn có thể thu gom.

### 3.2. Instance aspect không phải resource manager tự động

Khi một aspect instance hết phạm vi sử dụng, Java **không gọi một destructor đáng tin theo thời gian thực** để đóng socket hoặc file. Một công nghệ có thể cung cấp callback/lifecycle hook, nhưng phải kiểm tra hợp đồng cụ thể.

Không đồng nhất:

- **Instance không còn active ở flow:** không còn dùng cho luồng đó.
- **Instance không còn reachable:** GC có thể thu gom sau này.
- **Tài nguyên đã được đóng:** phải có hành vi `close`/release đúng quyền sở hữu.

Ba sự kiện có thể xảy ra ở **thời điểm khác nhau**.

## 4. Nguyên nhân 1: field trong aspect dùng chung giữ request

Một singleton aspect tiện cho counter/timing aggregator, nhưng không phù hợp để lưu mọi đối tượng request.

Mã nguy hiểm về mặt cấu trúc:

```java
final class UnsafeAspect {
    private final java.util.List<Object> requests =
            new java.util.ArrayList<>();

    void before(Object request) {
        requests.add(request);
    }
}
```

Đây là code minh họa **không có chương trình `main`**. Nếu `before` được gọi cho mỗi request và danh sách không có chính sách xóa/bounded retention, số phần tử tăng theo thời gian. Hơn nữa, `Object` có thể là một đồ thị dữ liệu lớn, chứ không chỉ một chuỗi ngắn.

Một hướng thiết kế tốt hơn:

```java
final class CountingAspect {
    private final java.util.concurrent.atomic.LongAdder calls =
            new java.util.concurrent.atomic.LongAdder();

    void before() {
        calls.increment();
    }

    long total() {
        return calls.sum();
    }
}
```

`LongAdder` thích hợp cho một số bộ đếm concurrent có tải cao. Giá trị `sum()` có thể không biểu diễn một snapshot tuyến tính hoàn hảo khi có các cập nhật đồng thời, nên không dùng nó làm bằng chứng giao dịch nếu cần consistency mạnh. Nhưng nó tránh việc lưu **toàn bộ request** chỉ để đếm lượt gọi.

### 4.1. Lưu “last request” vẫn có vấn đề

Một trường đơn `lastRequest` giữ lại **ít nhất một request** ngoài vòng đời cần thiết. Nếu chứa credential, dữ liệu người dùng hoặc stream, hậu quả không chỉ là bộ nhớ: còn có rủi ro riêng tư và tài nguyên bị giữ.

Nếu cần debug dữ liệu cuối cùng, hãy đặt **thời hạn, kích thước giới hạn, làm mờ thông tin nhạy cảm, quyền truy cập và mục đích sử dụng**. Không lưu `this`, `target` hoặc `args` nguyên dạng một cách vô thức.

### 4.2. Thread safety là vấn đề kèm theo

Field của aspect singleton có thể bị nhiều advice chạy đồng thời đọc/ghi. Nếu dùng một field `currentRequest`, request A có thể đọc giá trị B đã ghi; đây vừa là lỗi chức năng vừa có thể giữ tham chiếu ngoài ý muốn.

Biến cục bộ theo invocation không có cùng rủi ro chia sẻ trực tiếp; tuy vậy nếu object cục bộ **escape** vào future/listener/cache thì vòng đời của nó lại phụ thuộc holder.

## 5. Nguyên nhân 2: đăng ký listener/callback nhưng không hủy

Một advice có thể đăng ký callback để theo dõi sự kiện, timer hoặc transaction outcome:

```text
Global event bus / dispatcher (sống lâu)
     |
     v
Listener hoặc lambda được đăng ký
     |
     v
Captured aspect / target / request
     |
     v
Payload, session, closure data
```

Với callback dạng `aspect::onEvent`, đối tượng method reference thường giữ **receiver `aspect`**. Khi event bus giữ callback trong danh sách dài hạn, aspect cũng còn reachable. Nếu aspect giữ payload lớn, **cả payload bị giữ theo**.

Đây là dạng retention đặc biệt dễ nhầm vì lời gọi nghiệp vụ đã kết thúc nhưng registry vẫn còn.

### 5.1. Thiết kế registration có quyền đóng

Tốt nhất API đăng ký trả về một **handle có thể hủy**:

```text
registration = bus.register(listener)
try:
    runScopedOperation()
finally:
    registration.close()
```

Đây là giả mã. Việc gọi `close` phải loại listener khỏi registry, xử lý đóng nhiều lần nếu hợp đồng yêu cầu và không làm hỏng phiên xử lý khác.

Không phải mọi listener thuộc scope ngắn: listener cấp ứng dụng tồn tại đến khi ứng dụng dừng có thể là hợp lệ. Nhưng người quản lý phải định nghĩa **lúc nào tháo đăng ký**; không thể dựa vào GC để xóa một callback đang được registry giữ mạnh.

### 5.2. Listener giữ đối tượng thông qua lambda

Ví dụ một lambda:

```java
void subscribe(EventBus bus, RequestContext request) {
    bus.onEvent(event -> audit(request, event));
}
```

Đây là **đoạn minh họa với kiểu chưa định nghĩa**. Lambda capture `request` và có thể capture receiver hoặc biến khác cần cho `audit`. Nếu `bus` sống lâu, nó có thể giữ `request` lâu theo. Việc code nằm trong advice không làm capture đó yếu đi.

Giải pháp là hủy registration đúng lifecycle hoặc truyền **dữ liệu tối thiểu không nhạy cảm** thay vì giữ nguyên request.

## 6. Nguyên nhân 3: `ThreadLocal` chưa được dọn trên thread pool

`ThreadLocal` có thể dùng để lưu context của một luồng thực thi đồng bộ:

```text
Thread pool worker
   |
   +--> ThreadLocal state
           |
           v
        Context / Aspect instance
           |
           v
       Large request graph
```

Thread pool có thể giữ thread sống qua nhiều request. Nếu advice `set(context)` nhưng quên `remove()`/khôi phục giá trị cũ, context có thể ở lại trên thread và bị request sau nhìn thấy. Vấn đề là **retention và rò rỉ ngữ cảnh**, không chỉ heap.

### 6.1. Dọn đúng trong `finally`

Mẫu căn bản khi chắc chắn **không có scope cha**:

```java
CONTEXT.set(context);
try {
    return operation.run();
} finally {
    CONTEXT.remove();
}
```

Đây là **giả mã có tên chưa định nghĩa**. `finally` bao cả đường trả về bình thường và ném ngoại lệ sau khi scope đã vào `try`. Nhưng một tình huống **scope lồng nhau** cần phức tạp hơn: nếu context cha đang tồn tại thì phải **khôi phục cha**, không xóa toàn bộ.

### 6.2. Nested scope: lưu trước và khôi phục sau

```java
Context previous = CONTEXT.get();
CONTEXT.set(current);
try {
    runBody();
} finally {
    if (previous == null) {
        CONTEXT.remove();
    } else {
        CONTEXT.set(previous);
    }
}
```

Đây là đoạn minh họa. Nó giúp outer scope sống tiếp sau khi inner scope kết thúc. Tuy nhiên, nếu `null` có thể là **giá trị context hợp lệ cần phân biệt với không có mapping**, phải có chính sách trạng thái rõ hơn hoặc cấu trúc stack; `ThreadLocal.get()==null` đơn thuần không phân biệt được hai trạng thái.

### 6.3. Vì sao `ThreadLocal` không đồng nghĩa request scope?

Một request có thể chuyển qua nhiều thread; một thread có thể xử lý nhiều request tuần tự. Với executor, giá trị `ThreadLocal` từ thread gửi tác vụ **không tự truyền** sang worker.

Kể cả khi dùng cơ chế propagation có chủ đích, phải có luật **khôi phục trạng thái trước đó trên worker và dọn ở cuối task**. Truyền nguyên instance aspect có dữ liệu mutable sang nhiều luồng cũng đòi hỏi kiểm soát concurrency.

### 6.4. Weak key của ThreadLocal không cứu được strong value

Một số cấu trúc `ThreadLocalMap` của JVM có cách giữ key yếu nhưng value mạnh. Điều đó không có nghĩa quên `remove()` là an toàn: **value vẫn có thể còn bị giữ** qua entry chưa được dọn, nhất là khi thread sống lâu.

Tránh lập luận “ThreadLocal tự được GC khi key mất”, hoặc “ThreadLocal mới cho mỗi request thì không cần cleanup”. Cách phòng tránh đáng tin là **ownership và teardown**.

## 7. Nguyên nhân 4: cache không có giới hạn trong aspect

Aspect thường cache dữ liệu để giảm chi phí reflection hoặc chọn pointcut:

- Metadata `Method`, `Class`, annotation;
- Kết quả phân tích chữ ký;
- Đối tượng proxy/target;
- Thông tin caller;
- Các kết quả nghiệp vụ;
- Idempotency keys hoặc dữ liệu tracking.

Nếu dùng một `static Map<Key, Value>` không có giới hạn, nó có thể giữ cả key và value vô hạn. Việc cache chỉ một vài metadata ở ứng dụng nhỏ có thể hoàn toàn hợp lệ; rủi ro phát sinh khi **số key tăng vô hạn, object lớn hoặc class loader cần được unload**.

### 7.1. Nguy cơ với class loader

Trong môi trường plugin, container hoặc reload:

```text
Application-wide static cache
   |
   +--> Class<?> hoặc Method
           |
           v
       Class metadata / defining ClassLoader
           |
           v
      Classes và dữ liệu có liên quan
```

Nếu cache thuộc một loader sống lâu nhưng key/value giữ lớp được nạp bởi loader ngắn hạn, lớp/loader cũ có thể không được giải phóng đúng thời điểm. Đây là **class-loader retention**, có thể ảnh hưởng heap hoặc vùng metadata, tùy cơ chế JVM.

### 7.2. `WeakHashMap` không phải bảo đảm tuyệt đối

Java có `WeakHashMap` với **weak keys**, nhưng values được giữ theo hợp đồng của map và có thể **tham chiếu ngược lại keys**. Ví dụ:

```text
WeakHashMap weak key -> strongly held value
                              |
                              +--> references the same key
```

Nếu value giữ key mạnh, key vẫn reachable từ map thông qua value; weak key **không giải quyết** vòng giữ đó.

Với cache lâu dài, cần xác định:

- Key và value có giữ đối tượng/class loader của scope ngắn hay không;
- Giá trị có thời hạn hết hạn/giới hạn kích thước;
- Cách xóa khi plugin/module/target kết thúc;
- Mức concurrency và atomicity khi tính/cache;
- Dữ liệu có thể tái tạo sau eviction không;
- Có cần một cache gắn đúng vòng đời class loader thay vì static global không.

### 7.3. Giữ `Method` hoặc `Class` có thể có hệ quả riêng

Một `Method` tham chiếu thông tin lớp khai báo. Một cache toàn cục lưu nhiều `Method` từ class loader được tạo động có thể giữ dữ liệu liên quan lâu hơn mong muốn. Không dùng weak references một cách máy móc; **thiết kế theo ownership của lớp và phạm vi cache** đáng tin hơn.

## 8. Nguyên nhân 5: proxy, target và registry sống lâu

AOP qua proxy tạo thêm các đường tham chiếu:

```text
Long-lived registry
      |
      v
Proxy
      |
      v
Interceptor chain
      |
      +--> Target
      +--> Aspect instance
      +--> Captured context
```

Nếu một global registry giữ proxy mãi, cả interceptor chain và target mà proxy còn trỏ tới cũng có thể bị giữ. Một target còn tham chiếu về proxy tạo **chu trình**, nhưng chu trình đó không tự là leak nếu không còn đường từ GC root.

Điều quan trọng là **registry/static singleton/session map** thực sự đang nắm giữ cái gì, và khi một module hoặc đối tượng hết vòng đời, registry có được cập nhật hay không.

### 8.1. Per-target aspect cũng có thể bị giữ lâu

Một số chiến lược tạo aspect instance theo target hoặc `this`. Nếu cơ chế phải duy trì ánh xạ từ target tới instance và ánh xạ ấy có vòng đời quá dài, nó có thể giữ target hoặc aspect sống ngoài ý muốn.

Không kết luận mọi per-target policy đều dùng strong map gây leak; cách lưu instance/weak reference và teardown là **chi tiết triển khai**. Nhưng trách nhiệm kiểm tra vòng đời target và cơ chế registry vẫn cần thiết.

### 8.2. Self-invocation và đường bypass không phải leak

Gọi thẳng target hoặc `this.otherMethod()` có thể bypass proxy. Đây là **lỗi độ phủ AOP** khác với memory retention. Tuy nhiên nó có thể gián tiếp gây retention nếu advice cleanup chỉ chạy khi đi qua proxy nhưng một đường khác lại cấp tài nguyên mà không được đóng.

Cần tách câu hỏi “pointcut và cơ chế có áp đúng?” khỏi “tài nguyên/trạng thái nào đang được giữ?”. Không sửa leak bằng cách nới wildcard mà không hiểu đường thực thi.

## 9. Nguyên nhân 6: callback, future và tác vụ bất đồng bộ

Một advice có thể đăng ký callback sau khi tạo `CompletableFuture`:

```java
future.whenComplete((result, error) -> {
    // Callback này có thể capture context hoặc instance aspect.
});
```

Đây là đoạn minh họa. Khi future chưa hoàn tất và còn được giữ bởi scheduler, queue hoặc ứng dụng, callback và những object nó capture có thể còn reachable. Ngay cả khi future đã hoàn tất, **mức độ giữ lại phụ thuộc vào các tham chiếu và hiện thực của tác vụ**, không thể suy chỉ từ chữ ký `whenComplete`.

### 9.1. Completion đồng bộ khác completion thực của tác vụ

Một phương thức trả `CompletableFuture` đang pending đã **trả về bình thường**, nên advice kiểu after-returning quanh phương thức tạo future có thể chạy trước khi công việc thực sự xong. Nếu advice dọn context/tài nguyên ngay ở return, task có thể mất context hoặc dùng tài nguyên đã đóng.

Ngược lại, nếu advice giữ context mãi chỉ vì không biết khi nào future xong, nó có thể tạo leak. Phải xác định **owner của tác vụ**, callback completion và điều kiện hủy/timeout phù hợp.

### 9.2. Hủy tác vụ không tự giải phóng mọi tham chiếu

Cancel có thể không hủy ngay công việc phía dưới hoặc callback đã đăng ký. Một task bị treo có thể giữ stack, local variables, closure và những object được capture. Cần ngân sách thời gian, quy tắc cleanup và cơ chế dừng/đối soát theo tính chất tác vụ.

Không dùng một `finally` quanh **hàm lên lịch** rồi khẳng định tài nguyên của **hàm chạy bất đồng bộ** đã được trả.

## 10. Ví dụ Java 17: listener giữ aspect và cách tháo đăng ký

Ví dụ này cố ý dựng **một registry có vòng đời ứng dụng** và các aspect chứa payload minh họa. Nó chứng minh cấu trúc giữ tham chiếu qua **số listener còn đăng ký**, không dựa vào GC chạy đúng lúc.

Chương trình cũng có một mô hình **scope theo luồng** có phục hồi context cha và dọn context khi lỗi. Đây là ví dụ Java thuần mô phỏng vòng đời dữ liệu của aspect; **không phải bộ weaver AOP đầy đủ**.

Lưu vào tệp `AspectRetentionDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AspectRetentionDemo {
    static final class EventBus {
        private final List<Consumer<String>> listeners = new ArrayList<>();

        Registration register(Consumer<String> listener) {
            listeners.add(listener);
            return new Registration(this, listener);
        }

        void unregister(Consumer<String> listener) {
            listeners.remove(listener);
        }

        int count() {
            return listeners.size();
        }

        void publish(String event) {
            for (Consumer<String> listener : List.copyOf(listeners)) {
                listener.accept(event);
            }
        }
    }

    static final class Registration implements AutoCloseable {
        private EventBus bus;
        private Consumer<String> listener;

        Registration(EventBus bus, Consumer<String> listener) {
            this.bus = bus;
            this.listener = listener;
        }

        @Override
        public void close() {
            if (bus != null) {
                bus.unregister(listener);
                bus = null;
                listener = null;
            }
        }
    }

    static final class TraceAspect {
        private final String id;
        private final byte[] snapshot = new byte[64 * 1024];

        TraceAspect(String id) {
            this.id = id;
        }

        void onEvent(String event) {
            System.out.println("EVENT " + id + ":" + event);
        }

        String id() {
            return id;
        }
    }

    private static final EventBus GLOBAL_BUS = new EventBus();
    private static final ThreadLocal<TraceAspect> CURRENT =
            new ThreadLocal<>();

    static void withScope(String name, Runnable action) {
        TraceAspect previous = CURRENT.get();
        CURRENT.set(new TraceAspect(name));
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void main(String[] args) {
        for (int i = 1; i <= 2; i++) {
            TraceAspect aspect = new TraceAspect("leaky-" + i);
            GLOBAL_BUS.register(aspect::onEvent); // Không đóng handle.
        }
        System.out.println("LEAKY retained=" + GLOBAL_BUS.count());
        GLOBAL_BUS.publish("PING");

        EventBus scopedBus = new EventBus();
        for (int i = 1; i <= 2; i++) {
            TraceAspect aspect = new TraceAspect("safe-" + i);
            try (Registration ignored =
                    scopedBus.register(aspect::onEvent)) {
                scopedBus.publish("PING");
            }
            System.out.println("SAFE retained=" + scopedBus.count());
        }

        withScope("outer", () -> {
            System.out.println("CURRENT " + CURRENT.get().id());
            withScope("inner", () ->
                    System.out.println("CURRENT " + CURRENT.get().id()));
            System.out.println("RESTORED " + CURRENT.get().id());
        });
        System.out.println("OUTSIDE "
                + (CURRENT.get() == null ? "none" : "unexpected"));

        try {
            withScope("failed", () -> {
                System.out.println("CURRENT " + CURRENT.get().id());
                throw new IllegalArgumentException("bad input");
            });
        } catch (IllegalArgumentException error) {
            System.out.println("CAUGHT " + error.getMessage());
        }
        System.out.println("AFTER_FAILURE "
                + (CURRENT.get() == null ? "none" : "unexpected"));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 AspectRetentionDemo.java
java AspectRetentionDemo
```

Stdout được xác định theo chương trình:

```text
LEAKY retained=2
EVENT leaky-1:PING
EVENT leaky-2:PING
EVENT safe-1:PING
SAFE retained=0
EVENT safe-2:PING
SAFE retained=0
CURRENT outer
CURRENT inner
RESTORED outer
OUTSIDE none
CURRENT failed
CAUGHT bad input
AFTER_FAILURE none
```

### 10.1. Vì sao `GLOBAL_BUS` giữ aspect?

`GLOBAL_BUS` là static, còn `listeners` là danh sách strong references. Một callback tạo từ `aspect::onEvent` giữ receiver aspect. Đường giữ:

```text
Static GLOBAL_BUS
   -> EventBus.listeners
      -> Consumer callback
         -> TraceAspect
            -> byte[] snapshot
```

Trong vòng lặp “leaky”, code không giữ `Registration` trả về để đóng; listener vẫn tồn tại trong `GLOBAL_BUS`. Sau vòng lặp, biến cục bộ `aspect` không còn là lý do chính khiến đối tượng bị giữ: **registry sống lâu vẫn giữ callback**.

`LEAKY retained=2` **không đo số byte leak thực tế** và không chứng minh JVM đã hoặc chưa chạy GC. Nó cho thấy registry đang giữ hai đăng ký, mỗi callback có đường tham chiếu tới một aspect có snapshot.

### 10.2. Vì sao khối `try-with-resources` tránh giữ listener ngoài scope?

`scopedBus.register(...)` trả một `Registration` triển khai `AutoCloseable`. Khi `try` kết thúc, Java gọi `close()` kể cả khi thân try ném lỗi trong những đường điều khiển được bảo vệ. `close()` xóa đúng callback khỏi list.

Vì vậy mỗi vòng lặp in `SAFE retained=0`. Không cần gọi `System.gc()`, và không có tuyên bố object đã được giải phóng tức thì; chỉ có bảo đảm quan sát được là **registry không còn giữ listener đã đóng**.

### 10.3. Vì sao `Registration.close()` idempotent trong ví dụ?

Sau lần đóng đầu, nó đặt `bus=null` và `listener=null`. Nếu bị gọi lại, nhánh `if (bus != null)` không chạy, tránh unregister hai lần. Đây là một thiết kế tiện lợi cho **handle hủy đăng ký**.

Nếu event bus thật có listener cùng danh tính được đăng ký nhiều lần, concurrency hoặc yêu cầu đóng theo token, cần thiết kế API registration mạnh hơn là `List.remove(listener)`; ví dụ chỉ chạy tuần tự và mỗi listener là một callback riêng.

### 10.4. Vì sao scope inner trả lại outer?

`withScope` lưu `previous=CURRENT.get()` trước khi thay context. Khi inner kết thúc, finally đặt lại `previous=outer`, nên dòng `RESTORED outer` đúng. Khi outer kết thúc mà không có previous, code gọi `CURRENT.remove()`.

Nếu inner luôn gọi `CURRENT.remove()`, outer bị mất context trước khi hoàn tất. Nếu không cleanup khi có ngoại lệ, dòng `AFTER_FAILURE` có thể nhìn thấy state cũ trên thread tái sử dụng.

### 10.5. Điểm hạn chế cần nhớ

- Ví dụ không có AOP compiler, pointcut language, bytecode weaving hoặc proxy tự động.
- `snapshot` chỉ minh họa đồ thị giữ bộ nhớ, không là dữ liệu nghiệp vụ.
- Không dùng GC để xác nhận lifetime vật lý của object.
- `EventBus` dùng `ArrayList`, không an toàn cho thay đổi đăng ký đồng thời; chương trình chỉ chạy tuần tự.
- `ThreadLocal` chỉ chứng minh cơ chế context theo thread và nested scope; không có propagation sang executor/async.
- Cleanup registration không tự hủy effect ngoài hệ thống, file hoặc giao dịch; mỗi tài nguyên có hợp đồng riêng.
- Trong chương trình thật, không nên giữ thông tin nhạy cảm trong snapshot hoặc dump chúng vào log.

## 11. Dùng strong, weak và soft reference thế nào?

Java có một số loại tham chiếu với ngữ nghĩa khác nhau. Cần hiểu chúng trước khi định thay mọi field aspect thành `WeakReference`.

| Loại | Ý nghĩa khái quát | Lưu ý |
| --- | --- | --- |
| **Strong reference** | Giữ object reachable theo cách thông thường | Phù hợp khi object thực sự thuộc vòng đời holder |
| **WeakReference** | Không tự giữ object sống chỉ nhờ weak ref | Có thể được clear khi GC quyết định; không dùng cho dữ liệu bắt buộc phải tồn tại |
| **SoftReference** | Có thể được collector giải phóng theo chính sách bộ nhớ | Không thay thế cache có ngân sách và chính sách eviction |
| **PhantomReference** | Phục vụ cơ chế quan sát giai đoạn thu gom với `ReferenceQueue` | Không phải handle đóng tài nguyên đồng bộ, phải thiết kế phối hợp phù hợp |

Nếu dữ liệu phải còn tồn tại cho một nghiệp vụ đang xử lý, chuyển sang weak ref có thể làm **mất dữ liệu hợp lệ bất ngờ**. Nếu dữ liệu không còn cần, loại bỏ strong reference ở **đúng ownership boundary** thường đơn giản và đúng hơn.

### 11.1. Không dùng weak reference như cách né cleanup

Một callback trong registry không được tháo đăng ký vẫn có thể tiêu thụ tài nguyên hoặc chạy logic không mong muốn, ngay cả khi nó chỉ giữ weak ref tới target. Nếu listener được gọi sau khi target đã biến mất, cần xử lý kết quả null; listener bản thân nó có thể tiếp tục nằm trong registry mãi.

Có thể dùng weak listener khi kiến trúc thật cần và API hỗ trợ, nhưng **hủy đăng ký rõ ràng** vẫn là cơ chế dễ kiểm chứng.

### 11.2. Weak-key cache có vòng tham chiếu qua value

Với `WeakHashMap<K,V>`, key có thể yếu nhưng value thường được map giữ mạnh. Nếu V chứa K thì vẫn có đường mạnh từ map qua value tới K. Người lập trình phải kiểm tra **toàn bộ đồ thị tham chiếu**, không chỉ loại map.

## 12. Làm sao chẩn đoán leak trong ứng dụng AOP?

Một cách chẩn đoán có hệ thống:

1. **Xác định triệu chứng:** tăng heap dùng sau GC, tăng số instance/registrations, độ trễ GC, OOME hay tài nguyên ngoài heap bị cạn.
2. **Tạo workload lặp lại có kiểm soát:** cùng tập request, số vòng tăng, không thay ngẫu nhiên dữ liệu.
3. **Chờ điều kiện ổn định hợp lý:** phân biệt object ngắn hạn với object còn sống thật.
4. **Đo retained objects:** số instance aspect, request context, payload, callback, proxy, cache entry và thread local value.
5. **Lấy heap dump ở môi trường phù hợp:** chú ý dữ liệu nhạy cảm trong dump.
6. **Tìm paths to GC roots:** từ object lẽ ra đã chết theo registry/static field/thread/callback/class loader nào.
7. **Xem dominator tree/retained size:** holder nào chịu trách nhiệm giữ toàn bộ đồ thị lớn.
8. **Kiểm tra sau teardown:** registration đã close? scope đã remove? cache đã evict? module có unload?
9. **Sửa theo owner thực sự:** không chỉ `System.gc()`, đặt object thành null ở một nơi không sở hữu ref hoặc tăng heap.
10. **Chạy lại workload:** so sánh cùng thông số và giữ regression test.

### 12.1. Công cụ chẩn đoán thường gặp trong JVM

Có thể dùng **Java Flight Recorder (JFR)**, công cụ phân tích heap dump, Java Mission Control hoặc các lệnh JDK như `jcmd` theo khả năng môi trường. Ví dụ cú pháp tổng quát trên JDK phù hợp:

```bash
jcmd <PID> GC.class_histogram
jcmd <PID> GC.heap_dump heap.hprof
```

`<PID>` là placeholder cần thay bằng PID thực; đây là **lệnh mẫu để chẩn đoán**, không phải đoạn tự chạy nguyên văn. Tên tùy chọn, quyền truy cập và tác động của lệnh cần kiểm tra theo JDK/JVM đang sử dụng. Heap dump có thể chứa token, thông tin cá nhân hoặc dữ liệu nghiệp vụ; chỉ tạo/lưu/chia sẻ theo chính sách bảo mật và ngân sách vận hành phù hợp.

**Class histogram** cho biết số object/byte theo lớp tại thời điểm chụp, nhưng không tự giải thích **ai đang giữ** chúng. Heap analysis với **path to GC root** và **retained size** giúp truy ra registry/cache/aspect tương ứng.

### 12.2. “Retained size” khác “shallow size”

Một object `TraceAspect` có thể có **shallow size** nhỏ nhưng giữ `byte[]`, request và nhiều object khác. **Retained size** là khái niệm về lượng bộ nhớ có thể trở thành không reachable nếu bỏ một holder khỏi đồ thị (tùy phép phân tích/dominator). Vì thế tìm object có retained size lớn thường hữu ích hơn chỉ nhìn kích thước instance aspect.

### 12.3. Đo theo baseline và chu kỳ lặp

Một bài test tốt có thể:

- Tạo N lần request/bắt đầu và kết thúc scope;
- Kiểm tra số registration sau mỗi vòng phải về **0** nếu không có listener dài hạn;
- Kiểm tra cache không vượt ngân sách được thiết kế;
- Kiểm tra context thread pool không rò sang công việc tiếp theo;
- Chụp/so sánh heap trên nhiều chu kỳ sau khi hệ thống đã khởi động ổn định.

Đừng suy từ một ảnh heap đơn lẻ rằng mọi object lớn đều bị leak. Hãy gắn **kỳ vọng lifetime** với các bằng chứng retention cụ thể.

## 13. Thiết kế ownership rõ ràng để tránh leak

Nguyên tắc thực hành: **cơ chế nào tạo/đăng ký/giữ object thì phải biết điều kiện kết thúc quyền sở hữu của nó**.

| Đối tượng hoặc mối liên kết | Ai thường sở hữu? | Hành vi kết thúc cần cân nhắc |
| --- | --- | --- |
| Listener theo lần gọi | Advice/scope đã đăng ký | `unregister` hoặc `Registration.close()` |
| Context theo thread | Scope mở context | Khôi phục parent hoặc `ThreadLocal.remove()` |
| Cache metadata toàn ứng dụng | Thành phần quản lý cache | Evict theo giới hạn/thời hạn, hủy theo module |
| Proxy của một target | Container/registry tạo proxy | Bỏ tham chiếu khi target không còn được dùng |
| Callback async | Chủ tác vụ và pipeline completion | Kết thúc/cancel theo hợp đồng, tháo callback khi phù hợp |
| Connection/file/lock | Code đã acquire | `close`/`unlock` trên mọi đường thoát hợp lệ |
| Instance aspect per-flow | Cơ chế instantiation/scope | Dỡ binding sau return/throw, xử lý nested/async |

Không phải mọi object phải có `close()`. Một đối tượng Java thuần chỉ cần **không bị giữ mạnh ngoài vòng đời cần thiết**; tài nguyên hữu hạn phải đóng theo hợp đồng riêng.

### 13.1. Đặt state tại mức vòng đời tối thiểu đủ dùng

Nếu advice chỉ cần đo thời gian của **một lần gọi**:

```text
around:
    start = monotonicClock()
    try:
        return proceed()
    finally:
        recordElapsed(monotonicClock() - start)
```

Đây là giả mã. `start` có thể là **biến cục bộ**, không cần field trên singleton aspect. Khi lời gọi kết thúc, biến cục bộ không tự bị giữ tiếp trừ khi nó được capture/escape.

Nếu nhiều join point trong một dynamic flow cần chia sẻ state, per-flow scope có thể phù hợp **khi công nghệ hỗ trợ**, nhưng phải có lifecycle và cleanup đúng.

### 13.2. Tách dữ liệu quan sát khỏi dữ liệu gốc

Thay vì lưu `RequestContext`, đôi khi chỉ cần:

- Tên thao tác đã allowlist;
- Mã tương quan có quy tắc retention rõ;
- Bộ đếm hoặc tổng thời gian;
- Nhãn lỗi mức cao không chứa payload;
- Giá trị đã được rút gọn/ẩn danh theo chính sách.

Lưu các object gốc có thể giữ toàn bộ đồ thị dữ liệu và làm tăng rủi ro lộ thông tin từ heap dump/log.

## 14. Mối liên hệ với pointcut và phạm vi weaving

Một aspect có thể có pointcut rộng áp vào hàng nghìn join point. Điều này tăng **số lần đăng ký hoặc capture object** nếu advice thực hiện thao tác đó mỗi lần.

Mở rộng pointcut mà không thay lifecycle cleanup có thể biến leak nhỏ thành leak đáng kể. Vì vậy cần kiểm tra:

- Những loại join point nào được chọn (method execution/call/field access nếu được hỗ trợ);
- Advice chạy bao nhiêu lần trên một logical request;
- Around advice có `proceed` nhiều lần hay không;
- Có nhiều aspect/ proxy chồng lên nhau không;
- Đường nào bypass cleanup do không qua proxy hoặc lớp chưa được weave;
- Nếu có exception ở before advice, scope cleanup đã được thiết lập chưa.

Đừng cố “sửa leak” chỉ bằng giảm tần suất log mà không xóa **đường giữ tham chiếu mạnh**.

### 14.1. Around advice và retry

```text
Retry(RegisterListener(Target))
RegisterListener(Retry(Target))
```

Hai cấu trúc có thể có số lượt đăng ký khác nhau. Trong cấu trúc đầu, mỗi attempt có thể đăng ký một listener; nếu quên close, số listener giữ lại tăng theo attempts. Trong cấu trúc sau, một scope bên ngoài có thể đăng ký một lần cho toàn yêu cầu nếu hiện thực như vậy.

Thứ tự chỉ là mô hình minh họa; công nghệ có quy tắc precedence riêng. Điều quan trọng là **mỗi lần register thành công phải có teardown đúng số lần và đúng owner**.

### 14.2. Exception và rò tài nguyên

Một advice cấp context, sau đó gọi `proceed()`. Nếu target ném lỗi, cleanup chỉ đặt trong after-returning sẽ không được gọi. Một hành vi kiểu `finally`/after-always phù hợp hơn khi cần giải phóng tài nguyên cho cả return lẫn throw.

Nhưng nếu cấp phát tài nguyên xảy ra **trước khi vào vùng bảo vệ**, lỗi ngay trong cấp phát có thể làm thiếu cleanup. Quy tắc **acquire → enter protected scope → use → cleanup** phải tính cả cấp phát từng phần.

## 15. Concurrent invocation: không lẫn state giữa hai người gọi

Aspect singleton thường được dùng đồng thời. Nếu nó giữ một mảng `Object[] lastArgs` hoặc `Request current` trong field, invocation A và B có thể ghi đè lẫn nhau.

```text
Thread A: before(A) -> aspect.current = A
Thread B: before(B) -> aspect.current = B
Thread A: after()  -> reads B (wrong)
```

Đây là lỗi **race condition/logic** ngay cả khi GC chưa gặp vấn đề. Nếu A và B chứa dữ liệu nhạy cảm, việc lưu state chung còn có thể gây rò rỉ thông tin xuyên người dùng.

Cách thiết kế:

- Dùng biến cục bộ cho dữ liệu theo lần gọi;
- Dùng một context scope với teardown rõ khi cần nhiều join point chia sẻ dữ liệu;
- Dùng cấu trúc concurrent phù hợp cho số liệu **thật sự toàn cục**;
- Giảm object capture và ràng buộc retention;
- Kiểm tra listener/registration có thể close đúng dưới cạnh tranh;
- Không chia sẻ `ArrayList` mutable như EventBus trong ví dụ cho nhiều thread nếu chưa đồng bộ.

## 16. Kiểm thử lifecycle và leak theo tình huống

| Tình huống | Điều cần xác minh |
| --- | --- |
| 1.000 lần gọi bình thường | Không còn 1.000 listener/contexts nếu chỉ cần tạm thời |
| Target ném ngoại lệ | Context và registration được dọn |
| Before advice ném lỗi | Tài nguyên cấp trước lỗi được hoàn tác đúng |
| Nested scope | Inner kết thúc không xóa context outer |
| Thread pool lặp request | Không còn context từ request trước |
| Async task được hủy/timeout | Callback/tài nguyên được xử lý đúng ownership |
| Singleton aspect có field | Không giữ `args`/`target` quá vòng đời |
| Per-target aspect | Không có registry dài hạn giữ các target đã hết hạn |
| Per-flow aspect | Dỡ binding khi root return/throw theo công nghệ |
| Proxy registry | Hủy tham chiếu proxy/target đúng teardown |
| Hot reload module/plugin | Không giữ class loader cũ qua cache/listener |
| Cache nhiều key | Không tăng vượt giới hạn/TTL |
| Weak-key cache | Value không giữ ngược key không cần thiết |
| Retry nhiều attempts | Số register/close cân bằng cho từng attempt |
| Advice chỉ ghi log | Không đổi giá trị/ngoại lệ nghiệp vụ |
| Load cao và concurrency | Không lẫn context, không có race ở cleanup |

### 16.1. Các phép assert hữu ích

Với event bus có API `listenerCount()`, có thể kiểm tra:

```text
before = listenerCount()
openScope(); runOperation(); closeScope()
assert listenerCount() == before
```

Đây là **giả mã**. Với request scope trên thread pool, sau khi task hoàn tất có thể gửi một task kiểm tra trong cùng worker theo một mô hình test phù hợp để bảo đảm context không tồn tại. Khi test memory retention, đối chiếu **path to root** còn mạnh hơn chỉ đếm object.

### 16.2. Khi nào không nên assert object đã được GC?

Nếu test yêu cầu “sau `close()`, `WeakReference.get()` phải thành null trong 50 ms”, test có thể **flaky** vì GC không hứa chạy trong khoảng đó. Thay vào đó, kiểm tra **liên kết đăng ký đã được tháo**, map/scope có kích thước đúng và heap graph không còn đường mạnh sau teardown trong công cụ kiểm tra phù hợp.

Weak references và ReferenceQueue hữu ích cho một số test/phân tích, nhưng cần thiết kế tránh biến thời gian GC thành tiêu chuẩn hành vi cứng.

## 17. Lỗi phổ biến

| Sai lầm | Vì sao nguy hiểm | Cách tránh |
| --- | --- | --- |
| Lưu `Object[] args` vào singleton aspect | Giữ payload/request theo GC root sống lâu | Chỉ lưu dữ liệu tối thiểu hoặc scope-local |
| Thêm mọi request vào list để debug | Tăng không giới hạn | Dùng bounded buffer/metrics |
| Listener đăng ký nhưng không unregister | Event bus giữ callback và captured context | Registration handle + teardown |
| Quên `ThreadLocal.remove()` | Thread pool giữ state/nhầm request | `try/finally`, restore parent |
| Inner scope dùng `remove()` vô điều kiện | Mất outer context | Lưu previous và khôi phục |
| Tin weak key luôn tránh leak | Value có thể giữ ngược key | Kiểm tra graph mạnh, ownership |
| Coi reference cycle tự tạo leak | Chẩn đoán sai nguyên nhân | Xem path tới GC root |
| Cache `Class`/`Method` mãi trong global map | Có thể giữ class loader cũ | Cache theo đúng loader/lifecycle và eviction |
| Giữ proxy trong static registry không dọn | Giữ target/interceptors/graph | Gỡ proxy theo lifecycle |
| Capture request trong future lâu sống | Giữ dữ liệu/tài nguyên quá lâu | Dùng context tối thiểu, completion/cancel |
| Dùng `System.gc()` để “sửa leak” | Không xóa strong reference | Loại bỏ đường giữ ngoài ý muốn |
| Tưởng flow kết thúc tức object biến mất | Nhầm deactivation với GC | Phân biệt logical scope với object reachability |
| Dựa vào GC để đóng file/socket | Không bảo đảm thời điểm đóng | `AutoCloseable`/finally đúng scope |
| Chỉ test đường thành công | Leak trên throw/before failure | Test mọi đường thoát |
| Chỉ tăng heap limit | Che triệu chứng không sửa nguồn giữ | Phân tích retained size/path to root |
| Log nguyên object để chẩn đoán leak | Rò dữ liệu và tăng overhead | Rút gọn, làm mờ dữ liệu, giới hạn quyền |

## 18. Bài tập vận dụng có đáp án

### Bài 1 — Singleton giữ request

Một singleton aspect có field `lastRequest` được gán ở mỗi lời gọi. Sau khi lời gọi cuối kết thúc, request ấy có chắc sẵn sàng để GC không?

**Đáp án:** Không. Nếu aspect còn sống và field vẫn trỏ tới request, request còn reachable; đồ thị các đối tượng request giữ cũng có thể còn reachable. Không nhất thiết leak tăng vô hạn, nhưng đó có thể là retention ngoài ý muốn.

### Bài 2 — Listener method reference

`GLOBAL_BUS.register(aspect::onEvent)` được gọi cho mỗi request mà không lưu handle. Vì sao các aspect không biến mất khi biến cục bộ kết thúc?

**Đáp án:** Event bus giữ callback, callback giữ receiver aspect. Đường mạnh từ global bus khiến aspect vẫn reachable. Cần hủy đăng ký theo đúng vòng đời.

### Bài 3 — Thread pool

Một advice `CONTEXT.set(request)` nhưng quên `remove()`. Thread được pool tái sử dụng. Nguy cơ nào xuất hiện?

**Đáp án:** Request cũ có thể bị giữ và request mới có thể đọc nhầm context cũ. Cleanup nên nằm trong `finally` và xử lý restore parent nếu có nested scope.

### Bài 4 — Cycle

Target có field trỏ proxy, proxy trỏ target. Có phải chắc chắn memory leak không?

**Đáp án:** Không. Chu trình tự nó có thể được tracing GC thu gom nếu không còn reachable từ GC roots. Nếu một registry sống lâu vẫn giữ proxy thì cả chu trình có thể tiếp tục được giữ.

### Bài 5 — WeakHashMap

`WeakHashMap<Class<?>, Metadata>` có value `Metadata` giữ lại chính key `Class<?>`. Weak keys bảo đảm class được thu gom không?

**Đáp án:** Không. Map giữ value mạnh; value trỏ ngược key tạo đường strong reference tới class. Cần xem object graph đầy đủ, giới hạn cache và lifecycle class loader.

### Bài 6 — Flow kết thúc

Một aspect per-control-flow được dỡ khỏi activation khi root trả về. Object phải bị GC ngay và tự đóng connection không?

**Đáp án:** Không. Dỡ scope là hành vi logic; GC không bảo đảm thời điểm và tài nguyên ngoài heap cần cleanup theo hợp đồng riêng.

### Bài 7 — Async

Advice tạo callback future có capture cả request. Future pending rất lâu. Điều gì có thể bị giữ?

**Đáp án:** Callback có thể tiếp tục giữ request và các object liên quan chừng nào nó còn reachable qua future/task/queue. Cần xét thời hạn task, việc hủy/completion và dữ liệu tối thiểu cần capture.

### Bài 8 — Debug leak

Heap histogram hiển thị nhiều instance `RequestContext`. Có thể kết luận singleton aspect là nguyên nhân không?

**Đáp án:** Chưa. Histogram chỉ cho số object theo lớp; cần tìm **paths to GC roots** và holder/dominator thực sự. Nguồn giữ có thể là cache, listener, queue, ThreadLocal hoặc một phần nghiệp vụ khác.

### Bài 9 — Bảo đảm cleanup

Advice `register()` rồi gọi target; chỉ unregister trong after-returning. Khi target ném lỗi sẽ ra sao?

**Đáp án:** After-returning không chạy ở nhánh throw, nên có nguy cơ không unregister. Dùng finally/after-always hoặc handle có teardown rõ trên những đường thoát đã được bảo vệ.

### Bài 10 — Retry và registration

Cấu trúc `Retry(Register(Target))` có hai attempts. Nếu mỗi Register tạo listener nhưng không close, số listener có thể tăng bao nhiêu?

**Đáp án:** Trong mô hình retry gọi lại phần đuôi hai lần, có thể thêm hai listener cho một logical request. Cần cleanup cho **mỗi attempt** hoặc tổ chức một scope ngoài đúng trách nhiệm.

## 19. Ghi nhớ

- **Aspect instance và dữ liệu nó giữ có thể có vòng đời rất khác nhau.** Một holder sống lâu có thể giữ object ngắn hạn ngoài ý muốn.
- **GC dựa trên reachability từ roots**; reference cycle không tự là leak, còn static/listener/ThreadLocal/cache/proxy có thể tạo đường mạnh.
- Singleton/per-target/per-flow là **chiến lược lifecycle**, không tự bảo đảm thu hồi object hoặc đóng tài nguyên.
- **Listener phải được hủy đăng ký đúng lúc**; `ThreadLocal` phải remove/restore; cache cần giới hạn và ownership; async callback cần scope rõ.
- **WeakReference/WeakHashMap không thay thế teardown có chủ đích**, và `System.gc()` không sửa được đường giữ mạnh.
- Với Java 17, ưu tiên **biến cục bộ, `try/finally`, `AutoCloseable` và dữ liệu quan sát tối thiểu** cho các scope ngắn.
- Để chứng minh nguồn leak, hãy tìm **path to GC root và retained size** với workload kiểm soát, không chỉ nhìn con số heap tổng.
- Advice chỉ để quan sát phải giữ **kết quả, ngoại lệ và số lần chạy nghiệp vụ**, không tích trữ request/target không cần thiết hoặc log dữ liệu nhạy cảm.


</details>

- [Quay lại đầu trang](#back-to-top)

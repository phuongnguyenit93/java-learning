<a id="back-to-top"></a>

# Kiểm thử aspect

## Menu
- [Kiểm thử phần xử lý của advice độc lập với bộ dệt](#aspect-testing-001)
- [Kiểm thử pointcut khớp đúng trường hợp mong muốn](#aspect-testing-002)
- [Kiểm thử âm để phát hiện can thiệp ngoài phạm vi](#aspect-testing-003)
- [Kiểm thử kiểu trả về, ngoại lệ và giá trị null](#aspect-testing-004)
- [Kiểm thử thứ tự nhiều aspect và lời gọi lồng nhau](#aspect-testing-005)
- [Kiểm thử lời gọi nội bộ, đường gọi trực tiếp và mã không được dệt](#aspect-testing-006)
- [Kiểm thử trạng thái dùng chung trong điều kiện đồng thời](#aspect-testing-007)
- [Kiểm thử tác vụ bất đồng bộ và ngữ cảnh được truyền](#aspect-testing-008)
- [Kiểm thử hồi quy khi cấu trúc mã thay đổi](#aspect-testing-009)
- [So sánh hành vi khi bật và tắt một aspect](#aspect-testing-010)

## <a id="aspect-testing-001">Kiểm thử phần xử lý của advice độc lập với bộ dệt</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử phần xử lý của advice độc lập với bộ dệt trong AOP

## 1. Bài toán: logic đúng chưa đủ nếu chỉ kiểm thử cả hệ thống dệt

Trong **Aspect-Oriented Programming (AOP)**, một aspect thường kết hợp hai trách nhiệm: **chọn nơi can thiệp** và **thực thi hành vi xuyên suốt**. Một pointcut xác định những join point phù hợp; advice thực hiện logging, đo thời gian, kiểm tra quyền, quản lý scope, retry hoặc một chính sách khác. Bộ dệt (**weaver**) hoặc cơ chế interception có nhiệm vụ đưa advice vào đường chạy của chương trình.

Nếu mọi bài kiểm thử đều phải khởi tạo weaver, nạp lớp đã instrument và chạy một luồng nghiệp vụ lớn, việc kiểm thử logic advice có thể chậm, giòn, khó tạo các trường hợp lỗi và khó xác định nguyên nhân khi test thất bại.

**Mục tiêu của bài:** thiết kế và kiểm thử **phần xử lý bên trong advice** như một thành phần Java thông thường, có thể gọi trực tiếp bằng fake invocation, stub policy, fake clock và bộ ghi sự kiện trong bộ nhớ. Kiểm thử logic không cần bộ dệt; kiểm thử pointcut/weaving được thực hiện riêng ở tầng tích hợp.

Phương pháp này không phủ nhận vai trò của weaving. Nó giải quyết hai câu hỏi khác nhau:

- **Logic advice có đúng không?** Khi cho một join point giả lập với kết quả hoặc exception xác định, advice có xử lý đúng, gọi tiếp đúng số lần, giữ nguyên hợp đồng, cleanup scope và phát event đúng không?
- **Aspect có được áp dụng đúng trong chương trình không?** Pointcut có khớp đúng method, thứ tự advice có đúng, các lời gọi nội bộ có được intercept theo cơ chế đã chọn không?

Một bài test chỉ chứng minh câu đầu tiên **không chứng minh** câu thứ hai. Ngược lại, một test dệt thành công có thể không bao phủ các nhánh lỗi tinh vi của logic advice.

## 2. Đọc đúng các thuật ngữ AOP

| Thuật ngữ | Ý nghĩa đối với kiểm thử |
| --- | --- |
| **Join point** | Điểm thực thi thật có thể được intercept, ví dụ method call/execution |
| **Pointcut** | Điều kiện chọn join point; cần kiểm thử ở mức weaving/integration |
| **Advice** | Hành vi chạy before, after, around, on throwing; có thể tách logic để unit test |
| **Aspect** | Đơn vị tập hợp pointcut/advice, có thể chứa state và quy tắc composition |
| **Weaver** | Cơ chế nối aspect với bytecode/đường thực thi; không bắt buộc cho unit test của phần logic |
| **Proceed** | Tiếp tục tới phần còn lại của chuỗi advice hoặc computation tại join point |
| **Invocation adapter** | Lớp mỏng chuyển dữ liệu của join point thật thành lời gọi tới advice logic |
| **Test double** | Fake/stub/spy thay thế invocation, logger, policy, clock, executor hoặc context |

Một **before advice** thường cần dữ liệu về operation và một handler được gọi ở trước phần thân. Một **around advice** khó hơn vì phải kiểm soát lệnh tiếp tục và bảo toàn kết quả/ngoại lệ. Vì vậy bài này tập trung vào around advice và những trường hợp dễ sai: gọi tiếp hai lần, gọi tiếp khi chưa cho phép, nuốt exception, phát log sai, giữ state xuyên invocation.

## 3. Nguyên tắc kiến trúc: tách phần gắn dệt và phần xử lý

Một thiết kế dễ kiểm thử chia thành hai lớp trách nhiệm:

~~~text
             Bộ dệt / interceptor thật
                        |
                Thin AOP adapter
      (pointcut, binding args, this/target,
       convert proceed into callable)
                        |
                        v
                Advice handler
       (policy, timing, logging, cleanup)
             /          |          \
       Clock fake    Sink fake     Invocation fake
~~~

**Adapter** phụ thuộc vào cú pháp/cơ chế interception; **handler** nên phụ thuộc vào các interface nhỏ có ý nghĩa. Một handler có thể nhận:

- tên operation ổn định;
- metadata đã được binding/validate;
- một đối tượng có khả năng **proceed** đúng hợp đồng;
- đồng hồ dùng đo duration;
- event sink/logger;
- authorization policy hoặc context provider;
- cơ chế tạo/đóng scope.

Nếu adapter chỉ thực hiện chuyển đổi tham số và gọi handler, phần lớn nhánh xử lý có thể được unit test mà không nạp aspect hoặc weaver.

Tuy nhiên, **không cố loại mọi đặc trưng AOP** khỏi test: các điều kiện match pointcut, sự khác nhau giữa call và execution, self-invocation, precedence hoặc per-clause instantiation cần test với cơ chế AOP thực sự.

## 4. Viết hợp đồng cho advice trước khi kiểm thử

Một advice đáng tin có hợp đồng quan sát được. Ví dụ với advice logging:

1. Nếu target trả bình thường, trả **đúng giá trị** đó cho caller.
2. Nếu target ném exception, để **nguyên exception hợp lệ** tới caller.
3. Gọi phần computation tiếp theo **đúng một lần**, không âm thầm lặp nghiệp vụ.
4. Phát một event hoàn tất theo contract của **invocation đồng bộ**.
5. Đo duration bằng đồng hồ đơn điệu, không đòi số nanosecond cố định từ máy thật.
6. Khi sink lỗi, hành vi theo chính sách: logging best-effort không được che lỗi nghiệp vụ; audit bắt buộc có hợp đồng thất bại khác.
7. Không tự log dữ liệu đầu vào/đầu ra nhạy cảm.
8. Với scope, phải cleanup trong `finally` kể cả khi target ném exception.

Với authorization advice, contract khác:

1. Lấy danh tính từ nguồn tin cậy; định danh đúng action/resource.
2. **DENY** khi không đủ quyền hoặc không thể xác định quyền theo chính sách an toàn.
3. Khi DENY, **không gọi proceed**, tức target không được thực hiện side effect.
4. Khi ALLOW, gọi proceed đúng một lần và giữ nguyên result/exception.
5. Không lấy quyền từ dữ liệu caller tự gán hoặc một ThreadLocal chưa được thiết lập đáng tin.

Với retry advice, `proceed` có thể được gọi nhiều lần **có chủ đích**; cần một hợp đồng riêng về idempotency, số attempt, retryable failure, deadline và backoff. Không dùng cùng assertion “proceed luôn bằng 1” cho mọi kiểu advice.

## 5. Đặt seam kiểm thử ở đâu?

**Seam** là điểm có thể thay dependency thật bằng dependency kiểm thử. Những seam phổ biến:

| Dependency thật | Test double | Điều cần xác minh |
| --- | --- | --- |
| Proceed tới computation tiếp theo | Fake invocation/spy action | Số lần gọi, result, exception, thứ tự |
| System clock | Fake monotonic clock | Duration theo các mốc chủ động |
| Logger/telemetry exporter | In-memory event sink | Event count, schema, outcome, không lộ dữ liệu |
| Policy engine | Stub permit/deny/throw | Quyết định ALLOW/DENY, fail closed |
| Context source | Fake trusted context provider | Context hiện hành đúng, không rò |
| Scope manager | Spy open/close | Cleanup dù exception |
| Retry sleeper | Fake sleeper | Attempt count, delay policy, không ngủ thật |
| Executor | Controlled/manual executor | Submission, propagation, completion |
| Repository/resource resolver | Fake metadata provider | Quyền trên đúng resource, lỗi dữ liệu |

Ưu tiên interface nhỏ gắn với hành vi, thay vì mock toàn bộ API reflection/weaver. Một interface có phương thức `T proceed() throws Exception` đã đủ cho nhiều unit test đồng bộ.

Hạn chế của abstraction: join point thật có thể cho phép ném `Throwable`, có return type đặc biệt, hoặc có metadata về target/this/args. Adapter cần giải quyết sự khác biệt đó và được kiểm thử tích hợp, không âm thầm đổi loại ngoại lệ hoặc tham số.

## 6. Ví dụ Java 17 hoàn chỉnh: unit test handler không cần weaver

Chương trình dưới đây chạy **chỉ với JDK 17+**, không cần một thư viện AOP, test framework hay quá trình weaving. Nó minh họa:

- `CheckedAction<T>`: một invocation giả, tương đương khái niệm proceed;
- `ObservationAdvice`: advice logic ghi event cho return/exception;
- `SequenceClock`: đồng hồ giả cho duration hoàn toàn xác định;
- `List<Observed>`: fake event sink để assert event;
- `AuthorizationAdvice`: advice logic kiểm tra policy trước khi gọi tiếp;
- các test bằng assertion tường minh, có stdout xác định.

Trong chương trình thật, một adapter mỏng tại join point sẽ tạo action gọi proceed rồi chuyển cùng metadata vào handler. **Chương trình sau chỉ kiểm thử handler; nó cố ý không tuyên bố kiểm thử weaving.**

Tạo tệp **AdviceLogicUnitDemo.java**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class AdviceLogicUnitDemo {

    @FunctionalInterface
    interface CheckedAction<T> {
        T proceed() throws Exception;
    }

    record Observed(
            String operation,
            String outcome,
            long durationNanos,
            String errorType) { }

    static final class SequenceClock implements LongSupplier {
        private final long[] values;
        private int next;

        SequenceClock(long... values) {
            this.values = values.clone();
        }

        @Override
        public long getAsLong() {
            if (next >= values.length) {
                throw new AssertionError("unexpected clock read");
            }
            return values[next++];
        }
    }

    static final class ObservationAdvice {
        private final LongSupplier monotonicClock;
        private final Consumer<Observed> sink;

        ObservationAdvice(
                LongSupplier monotonicClock,
                Consumer<Observed> sink) {
            this.monotonicClock = Objects.requireNonNull(monotonicClock);
            this.sink = Objects.requireNonNull(sink);
        }

        <T> T around(String operation, CheckedAction<T> action)
                throws Exception {
            Objects.requireNonNull(operation);
            Objects.requireNonNull(action);

            long start = monotonicClock.getAsLong();
            try {
                T value = action.proceed();
                long elapsed = monotonicClock.getAsLong() - start;
                emitBestEffort(new Observed(
                        operation, "returned", elapsed, null));
                return value;
            } catch (Exception error) {
                long elapsed = monotonicClock.getAsLong() - start;
                emitBestEffort(new Observed(
                        operation, "threw", elapsed,
                        error.getClass().getSimpleName()));
                throw error;
            }
        }

        private void emitBestEffort(Observed event) {
            try {
                sink.accept(event);
            } catch (RuntimeException ignored) {
                // Chỉ thích hợp cho telemetry best-effort.
                // Một hệ thống audit bắt buộc cần hợp đồng riêng.
            }
        }
    }

    @FunctionalInterface
    interface Permit {
        boolean allowed(String operation, String resource);
    }

    static final class AuthorizationAdvice {
        private final Permit policy;

        AuthorizationAdvice(Permit policy) {
            this.policy = Objects.requireNonNull(policy);
        }

        <T> T around(
                String operation,
                String resource,
                CheckedAction<T> action) throws Exception {
            if (!policy.allowed(operation, resource)) {
                throw new SecurityException("access denied");
            }
            return action.proceed();
        }
    }

    static final class DomainFailure extends Exception { }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void testSuccess() throws Exception {
        List<Observed> events = new ArrayList<>();
        AtomicInteger proceedCalls = new AtomicInteger();
        ObservationAdvice advice = new ObservationAdvice(
                new SequenceClock(100, 135),
                events::add);

        String value = advice.around("order.submit", () -> {
            proceedCalls.incrementAndGet();
            return "ACCEPTED";
        });

        require("ACCEPTED".equals(value), "result changed");
        require(proceedCalls.get() == 1, "proceed count");
        require(events.equals(List.of(
                new Observed("order.submit", "returned", 35, null))),
                "wrong success event");
        System.out.println("PASS success: result, once, event");
    }

    static void testFailure() throws Exception {
        List<Observed> events = new ArrayList<>();
        AtomicInteger proceedCalls = new AtomicInteger();
        DomainFailure original = new DomainFailure();
        ObservationAdvice advice = new ObservationAdvice(
                new SequenceClock(200, 225),
                events::add);

        boolean caughtSameObject = false;
        try {
            advice.around("order.submit", () -> {
                proceedCalls.incrementAndGet();
                throw original;
            });
        } catch (DomainFailure caught) {
            caughtSameObject = caught == original;
        }

        require(caughtSameObject, "original exception not preserved");
        require(proceedCalls.get() == 1, "proceed count");
        require(events.equals(List.of(
                new Observed("order.submit", "threw", 25,
                        "DomainFailure"))),
                "wrong failure event");
        System.out.println("PASS failure: original exception, event");
    }

    static void testBrokenObserver() throws Exception {
        ObservationAdvice advice = new ObservationAdvice(
                new SequenceClock(0, 10),
                event -> {
                    throw new IllegalStateException("sink unavailable");
                });

        String result = advice.around("order.submit", () -> "OK");
        require("OK".equals(result), "observer changed result");
        System.out.println("PASS observer: result preserved");
    }

    static void testAuthorization() throws Exception {
        AtomicInteger proceedCalls = new AtomicInteger();
        AuthorizationAdvice advice = new AuthorizationAdvice(
                (operation, resource) ->
                        operation.equals("order.approve")
                                && resource.equals("owned"));

        boolean denied = false;
        try {
            advice.around("order.approve", "other", () -> {
                proceedCalls.incrementAndGet();
                return "BAD";
            });
        } catch (SecurityException expected) {
            denied = true;
        }

        require(denied, "missing denial");
        require(proceedCalls.get() == 0, "proceeded after denial");

        String value = advice.around("order.approve", "owned", () -> {
            proceedCalls.incrementAndGet();
            return "APPROVED";
        });

        require("APPROVED".equals(value), "allow result changed");
        require(proceedCalls.get() == 1, "allow must proceed once");
        System.out.println("PASS authorization: deny=0, allow=1");
    }

    public static void main(String[] args) throws Exception {
        testSuccess();
        testFailure();
        testBrokenObserver();
        testAuthorization();
        System.out.println("ALL TESTS PASSED");
    }
}
~~~

Chạy:

~~~bash
javac AdviceLogicUnitDemo.java
java AdviceLogicUnitDemo
~~~

Stdout chính xác:

~~~text
PASS success: result, once, event
PASS failure: original exception, event
PASS observer: result preserved
PASS authorization: deny=0, allow=1
ALL TESTS PASSED
~~~

### Điều gì đã được kiểm chứng?

**testSuccess:** fake invocation trả ACCEPTED, advice trả lại giá trị nguyên vẹn, action được gọi một lần và đúng một event `returned` có duration 35 nanosecond theo đồng hồ giả. Đây là số **giả lập để kiểm thử**, không phải duration đo máy thật.

**testFailure:** action ném một đối tượng DomainFailure cụ thể. Test xác minh caller nhận **chính cùng đối tượng exception** (so sánh tham chiếu), proceed đúng một lần và event `threw` ghi đúng loại lỗi.

**testBrokenObserver:** sink ném IllegalStateException nhưng kết quả nghiệp vụ vẫn OK vì mẫu này quy định **telemetry best-effort**. Không áp dụng mô hình nuốt lỗi này cho audit bắt buộc hoặc những log được coi là bằng chứng nghiệp vụ.

**testAuthorization:** policy từ chối resource "other" trước khi computation chạy, nên proceed count bằng 0. Với resource "owned", policy cho phép, invocation chạy đúng một lần và trả APPROVED.

**Không có weaver trong bốn bài test.** Điều được kiểm thử là các branch và hợp đồng của handler, không phải khả năng AspectJ hoặc một cơ chế AOP cụ thể chặn đúng method.

### Giới hạn quan trọng của ví dụ

Ví dụ dùng `Exception` để có chữ ký Java đơn giản. Một advice quanh API có thể ném `Throwable`, bao gồm `Error`; adapter thực phải có chiến lược lan truyền lỗi tương ứng. Ngoài ra, `ObservationAdvice` trong ví dụ giả định clock có sẵn và monotonic; trường hợp clock selbst ném lỗi, hoặc overflow/âm do nguồn đồng hồ sai, cần chính sách phù hợp. Đo **System.nanoTime()** trong chương trình thật là cách đo chênh lệch cùng tiến trình; fake clock giúp test không phụ thuộc tốc độ máy.

Trong mã ví dụ, `ObservationAdvice` phân loại **lời gọi đồng bộ** thành returned/threw, không tự xác định business success/failure của một `Result` object hoặc Future chưa hoàn tất. Khi yêu cầu là outcome nghiệp vụ, nên dùng bộ phân loại outcome riêng có test tương ứng.

## 7. Cách gắn handler vào advice thật

Ở mã có cơ chế AOP thực, lớp adapter chịu trách nhiệm:

1. Chọn join point bằng pointcut hoặc cấu hình tương đương.
2. Lấy tên operation ổn định và các binding phù hợp.
3. Chuyển lời gọi proceed của công cụ AOP thành một callback đáp ứng interface handler.
4. Trả result hoặc lan truyền throwable đúng contract.
5. Không tự lặp lại policy hoặc logic logging bên trong adapter.

Sơ đồ khái niệm:

~~~text
around selectedJoinPoint:
    metadata = readValidatedJoinPointMetadata()
    action = adapterForProceed()
    return adviceHandler.around(metadata.operation, action)
~~~

Đây là **giả mã** để minh họa ranh giới trách nhiệm, không phải một đoạn AspectJ có thể biên dịch nguyên xi. Mỗi công cụ AOP có kiểu advice và chữ ký proceed khác nhau; một số áp dụng weaving ở compile time hoặc load time, một số dùng proxy/interceptor. Adapter phải xử lý các quy tắc đó thay vì buộc handler phụ thuộc trực tiếp vào toàn bộ API weaving.

Một adapter mỏng có thể được review riêng: nó không nên biết giá, inventory, policy nghiệp vụ, hay cách serialize log; handler và các policy phụ trợ sở hữu hành vi đó. Khi đổi cơ chế weaving, phần lớn unit tests của handler vẫn giữ nguyên, còn adapter/integration tests phải được cập nhật.

## 8. Unit test advice, test adapter và test weaving khác nhau thế nào?

| Cấp kiểm thử | Đối tượng | Cần weaver không? | Ví dụ câu hỏi |
| --- | --- | --- | --- |
| Pure policy test | Bộ tính policy, classifier, formatter | Không | Lỗi nào là rejected? Ai được quyền? |
| Advice handler unit test | Xử lý proceed/exception/scope/event | Không | Có gọi đúng một lần, trả đúng value? |
| Adapter unit test | Binding và chuyển tiếp metadata/callback | Có thể không nếu adapter tách được | Có lấy đúng operation, args và throwable? |
| Weaving/pointcut test | Pointcut + advice thật + code được instrument | Có | Method này có thật sự được chặn? |
| Composition test | Nhiều aspect cùng join point | Có | Precedence, retry, transaction đúng? |
| End-to-end test | Business flow qua ranh giới thật | Thường có hoặc chạy hệ thống thực | Thao tác hoàn tất đúng và telemetry nhất quán? |

**Không thay thế test weaving bằng test handler.** Một handler hoàn hảo nhưng pointcut sai package sẽ không chạy. Một advice unit test thành công cũng không chứng minh rằng gọi nội bộ trong cùng object đi qua proxy, hoặc execution/call join point được công cụ hỗ trợ như dự kiến.

Một suite cân bằng dùng nhiều unit tests nhanh để bao phủ hành vi, một số ít integration tests có mục tiêu để kiểm tra **điểm nối với AOP** và một vài end-to-end tests quan trọng cho các đường nghiệp vụ nhạy cảm.

## 9. Kiểm thử proceed: một lần, không lần nào, hay nhiều lần có chủ đích?

**Around advice** có quyền tác động tới số lần tiếp tục computation. Vì thế số lần proceed là một trong những assertion có giá trị nhất.

### Observer thuần

Ghi log, metric hoặc tracing thông thường nên gọi proceed **đúng một lần**. Nếu lời gọi bị ném lỗi, advice vẫn lan truyền lỗi và cleanup các scope mà nó tạo.

### Authorization/guard

Khi DENY trước side effect, phải gọi proceed **0 lần**. Khi ALLOW, thường gọi **1 lần**. Ngoài assert response hoặc exception, cần spy đếm hành vi thật để đảm bảo target không chạy sau DENY.

### Retry

Một retry advice có thể gọi proceed **N lần** theo policy. Unit tests phải kiểm tra:

- Lỗi retryable được thử lại đúng số lần tối đa.
- Lỗi non-retryable không bị thử lại.
- Khi lần sau thành công, trả đúng result.
- Khi hết số lượt, giữ hoặc chuyển exception theo hợp đồng đã định.
- Delay/backoff/deadline kiểm thử qua fake sleeper/clock, không ngủ thật.
- Các side effect có idempotency hoặc không được retry nếu unsafe.
- Attempt metric và operation metric không bị đếm trùng.

### Cache hoặc circuit breaker

Cache hit có thể khiến proceed bằng **0**, cache miss bằng **1**. Circuit breaker open có thể fail fast không gọi target. Unit test phải đối chiếu hành vi với policy và trạng thái giả của breaker, không mặc định mọi advice đều thực thi computation.

**Điểm chung:** expected proceed count đến từ hợp đồng của concern, không đến từ một nguyên tắc duy nhất áp dụng cho toàn bộ AOP.

## 10. Dùng spy invocation để kiểm thử thứ tự side effect

Một spy đơn giản có thể lưu trình tự sự kiện:

~~~java
// Đoạn minh họa riêng: event log cho kiểm thử thứ tự.
List<String> steps = new ArrayList<>();
CheckedAction<String> action = () -> {
    steps.add("proceed");
    return "DONE";
};
~~~

Nếu handler có scope:

~~~text
open-scope
before-log
proceed
after-log
close-scope
~~~

Test nên assert **toàn bộ thứ tự** theo contract, chứ không chỉ kiểm tra "có đủ 5 event". Bởi đóng scope trước khi ghi log có thể làm mất correlation context, còn log trước khi cập nhật outcome có thể khiến bản ghi mô tả sai trạng thái.

Một cách tổ chức rõ ràng là dùng in-memory recorder chung cho những dependency giả: logger fake, scope fake và invocation spy cùng append vào một danh sách. Với exception, danh sách mong đợi có thể là:

~~~text
open-scope
before-log
proceed
error-log
close-scope
~~~

Nếu code cần cleanup ngay cả khi event sink lỗi, test thêm đường sink throw và kiểm tra close vẫn xảy ra. Không nên dựa vào sleep hoặc điều kiện race để xác nhận thứ tự trong unit test đồng bộ.

## 11. Kiểm thử bảo toàn result và exception

Một advice chỉ quan sát không được ngầm chuyển đổi kết quả. Các trường hợp cần thử:

| Trường hợp | Assertion |
| --- | --- |
| Target trả object | Chính cùng object được trả nếu contract giữ tham chiếu |
| Target trả null | Vẫn trả null, không ép thành Optional hoặc lỗi |
| Target trả primitive qua adapter | Boxing/unboxing hoặc return type không bị đổi |
| Target ném checked exception | Caller nhận exception hợp lệ ban đầu |
| Target ném runtime exception | Không bị nuốt hoặc bọc bất ngờ |
| Target ném Error | Cần bảo toàn/cleanup theo chính sách, adapter có test riêng |
| Handler precondition sai | Không tự tiến hành nghiệp vụ nếu không hợp lệ |
| Observer ném lỗi | Không che lỗi gốc nếu observer là best-effort |
| Handler thay result có chủ đích | Phải có contract riêng và test chuyển đổi |

Với reflection, `Method.invoke` thường bọc lỗi target trong `InvocationTargetException`; adapter phải unwrap khi hợp đồng yêu cầu giữ lỗi gốc. Fake invocation không tự kiểm chứng được chi tiết reflection đó. Vì vậy cần một adapter test bổ sung với một method thật ném exception.

Nếu advice cố ý chuyển lỗi sang một loại exception chung, hãy xác định **nguyên nhân, stack/cause và mã lỗi** nào được giữ lại. Không biến mọi checked exception thành generic RuntimeException chỉ vì interface test double cho tiện.

## 12. Kiểm thử timing mà không phụ thuộc đồng hồ thật

Việc viết test dạng “method chạy trong khoảng 5–10 ms” rất dễ flaky vì CPU scheduling, JIT warm-up, GC, máy CI và sleep không có độ chính xác tuyệt đối.

Phương án tốt hơn:

1. Truyền clock qua dependency.
2. Dùng fake clock trả một dãy thời điểm xác định.
3. Cho invocation fake trả kết quả hoặc ném lỗi.
4. Kiểm tra duration = end − start và event được phát đúng một lần.
5. Test clock được gọi bao nhiêu lần nếu đó là phần của contract.

Ví dụ trong chương trình Java ở trên, fake clock trả `100` và `135`, nên elapsed = `35` chính xác. Không cần sleep.

Trong môi trường thật, dùng đồng hồ **đơn điệu** như `System.nanoTime()` để đo duration; không dùng chênh lệch wall clock để suy ra thời gian trôi qua vì đồng hồ hệ thống có thể được điều chỉnh.

Nếu advice đo thời gian task bất đồng bộ, unit test đồng hồ vẫn chưa đủ: cần fake completion signal hoặc controlled executor để kiểm tra lúc nào "done" xảy ra. Đây là một hợp đồng lifecycle khác.

## 13. Kiểm thử logging và metric ở mức semantics

Một advice ghi log/metric cần được test theo **schema và ý nghĩa**, không lệ thuộc vào một chuỗi text formatting ngẫu nhiên.

**Nên assert:**

- event type ổn định (invocation_returned, operation_finished, attempt_failed...);
- operation name ổn định;
- outcome đúng theo contract;
- duration và đơn vị chính xác;
- số event: một hay nhiều theo từng occurrence;
- correlation ID được dùng đúng hoặc bị bỏ nếu không hợp lệ;
- error code/exception class đã được chuẩn hóa;
- không chứa secret hoặc dữ liệu nhạy cảm.

**Không nên gắn test vào:** timestamp thực của máy, địa chỉ bộ nhớ, thứ tự trường JSON nếu schema không yêu cầu, log formatter của thư viện hoặc sleep time.

Một test kiểm tra count cũng cần biết **đơn vị đo**. Một operation có retry ba lần có thể hợp lệ với một event cấp operation và ba event cấp attempt. Đừng viết test “mọi event cùng trace ID phải có đúng một bản ghi”; trace có thể chứa rất nhiều sự kiện hợp lệ.

Nếu instrumentation có sampling, unit test nên dùng sampler fake luôn cho phép hoặc luôn từ chối, rồi assert event count tương ứng; không dựa vào random sampling trong bài test quyết định.

## 14. Authorization và context: lỗi unit test phải bắt được

Một authorization handler có thể phụ thuộc vào trusted identity context. Test nên bao phủ:

- Không có context → DENY.
- Danh tính đã xác thực nhưng thiếu permission → DENY.
- Có permission nhưng không có quyền trên resource/tenant → DENY.
- Policy evaluator trả lỗi/timeout → xử lý theo chính sách fail-closed.
- Đủ điều kiện → proceed một lần.
- DENY → proceed bằng 0 và không có side effect.
- Caller cố giả mạo role/tenant trong args → không được nâng thành quyền.
- Delegation có scope → không được mở rộng action/resource ngoài scope được cấp.
- Audit event cho DENY không chứa token hoặc thông tin nhạy cảm.

Thay policy evaluator bằng stub `Permit` như ví dụ giúp kiểm tra chính xác **behavior của advice**. Nhưng việc context thật được lấy từ nguồn xác thực, binding resource đúng và mọi protected method đều được chặn phải có integration/security tests riêng.

Một lời gọi ALLOW ở method entry cũng không bảo đảm quyền chưa bị thu hồi trước lúc commit. Nếu thao tác nhạy cảm phụ thuộc vào state thay đổi, cần test domain/storage invariant hoặc kiểm tra quyền tại mốc cập nhật có thẩm quyền; unit test advice không tự khử race condition này.

## 15. Cleanup scope trong success, throw và early return

Nhiều aspect tạo scope: trace span, thread context, lock, permit hoặc transaction. Mỗi resource có vòng đời khác nhau và không phải resource nào cũng phù hợp với quanh một lời gọi ngắn.

Đối với scope invocation đồng bộ, cấu trúc đúng thường là:

~~~text
scope = open()
try:
    return proceed()
finally:
    scope.close()
~~~

Đây là giả mã. Unit tests nên kiểm tra:

1. Target trả → scope được đóng đúng một lần.
2. Target ném exception → scope vẫn đóng đúng một lần.
3. Advice từ chối trước proceed → những scope đã mở vẫn được cleanup.
4. Scope đóng ném lỗi → exception precedence/cause tuân hợp đồng; không vô tình che lỗi gốc.
5. Nested scopes → khôi phục parent context, không chỉ remove vô điều kiện.
6. Không giữ ThreadLocal cũ sau lời gọi; đặc biệt với pooled threads.
7. Không đóng scope được tạo ở caller thread từ một callback chạy trên worker khác nếu scope yêu cầu thread affinity.

Với một transaction hoặc audit có yêu cầu bền vững, việc đóng scope có thể tác động business behavior. Hãy kiểm thử cả thứ tự commit/rollback và error semantics; không dùng quy tắc best-effort logging cho mọi resource.

## 16. Test advice có state và concurrency

Một singleton aspect có thể chứa field mutable dùng chung giữa các lời gọi. Unit test tuần tự đôi khi vẫn pass dù production có race.

Những điều cần xác minh:

- Counter dùng cho metrics có cơ chế atomic/thread-safe thích hợp.
- Mutable context không được lưu trong field singleton rồi dùng chung giữa request.
- State per-object không bị nhầm là per-request nếu service instance sống lâu.
- State per-control-flow không tự động đi theo task qua executor.
- Khi task chạy song song, context snapshot của task A không bị lẫn với B.
- Callback hoàn tất không gọi lại observer hai lần nếu contract yêu cầu một event duy nhất.
- Scope/thread-local luôn cleanup, kể cả khi cancellation hoặc exception.

Có thể dùng **CountDownLatch** hoặc controlled executor để điều phối một interleaving có ý nghĩa thay vì sleep và cầu may. Nhưng một test chạy được không chứng minh tuyệt đối code không có race; dùng thêm thiết kế immutable data, synchronization và kiểm tra tải phù hợp.

Nếu logic advice dùng một map cache, cần test eviction, lifetime, key collisions, size limit, tenant separation và concurrency. Một fake clock giúp kiểm thử TTL ổn định.

## 17. Async advice: callback được kích hoạt ở mốc nào?

Với một API trả `CompletionStage<T>`, method return chỉ nói rằng caller có một handle tới kết quả, không đồng nghĩa task đã hoàn thành. Một observer muốn ghi **operation outcome** phải hiểu completion lifecycle.

Một thiết kế phù hợp là tách:

- **invocation_returned**: xảy ra khi method trả stage;
- **operation_completed**: xảy ra khi stage hoàn tất theo contract;
- **task_terminated**: xảy ra khi công việc nền thực sự dừng và cleanup.

Ba sự kiện này có thể khác nhau. Nếu timeout/cancellation chỉ tác động lên wrapper stage, task nền có thể tiếp tục chạy.

Bài test async nên dùng một `CompletableFuture` điều khiển được, không hoàn tất ngay từ đầu:

~~~java
// Chỉ là đoạn minh họa cho một test khác, không thuộc
// chương trình runnable ở mục 6.
CompletableFuture<String> future = new CompletableFuture<>();

// Gọi adapter/handler async đã được định nghĩa trong dự án.
// Tại đây future chưa xong: chưa được phát final completion.
future.complete("DONE");

// Bây giờ assertion mới mong có final success event.
~~~

Nếu có lỗi, dùng `completeExceptionally` để xác nhận outcome failure, exception unwrapping và số callback event. Với cancellation, test semantic contract của API, không mặc định task thực sự dừng.

Một callback observer có thể chạy ngay khi đăng ký nếu stage đã hoàn tất, hoặc chạy ở thread hoàn tất về sau. Bởi vậy không nên giả định ThreadLocal caller tự còn hợp lệ; context cần được truyền theo task và cleanup đúng scope.

**Kiểm thử logic async độc lập với weaver** vẫn là phương pháp hữu ích, nhưng cần một handler/adapter cho completion, không dùng around handler đồng bộ rồi gán nhầm returned thành success.

## 18. Không cần mock cả đối tượng join point

Một lỗi thường gặp là viết hàng chục mock cho method signature, declaring type, annotation, args, this, target, source location và proceed chỉ để kiểm thử việc phát một event.

Cách tốt hơn:

1. Tách phần **đọc metadata từ join point** vào adapter nhỏ.
2. Biến metadata thành một record bất biến như `OperationInfo(name, category, resourceId)`.
3. Cho handler nhận record đó cùng `CheckedAction<T>`.
4. Test handler bằng record và fake action.
5. Test adapter riêng bằng một số case có metadata đặc trưng.
6. Test pointcut thực với weaver ở tầng tích hợp.

Nếu handler thật sự cần reflection metadata, vẫn có thể dùng interface trung gian hoặc fixture Method/Annotation thật, nhưng đừng để unit test phụ thuộc vào toàn bộ hệ thống weaving.

Ưu điểm là các bài test mô tả **hành vi cần đạt** thay vì thao tác của API. Điều này giúp đổi cơ chế weaving hoặc nâng cấp thư viện AOP với ít test giòn hơn.

## 19. Điều gì bắt buộc phải kiểm thử với bộ dệt?

Các thuộc tính sau không được chứng minh chỉ bằng unit test của advice handler:

| Thuộc tính weaving | Ví dụ kiểm thử |
| --- | --- |
| Pointcut matching | Method có annotation thực sự bị chọn; method khác không bị chọn |
| Call vs execution join point | Advice chạy ở điểm caller hay trong method execution theo cấu hình |
| Self-invocation/direct call | Những đường gọi này có đi qua interception không |
| Type/argument binding | Advice lấy đúng tham số, target, this và annotation |
| Advice precedence | Around/before/after lồng nhau theo thứ tự đã khai báo |
| Abstract aspect inheritance | Concrete subaspect thực sự áp dụng inherited advice/pointcut |
| Aspect instantiation | Singleton, per-object, per-flow hoặc per-type theo mô hình hỗ trợ |
| Weaving scope | Class/library cần instrument thật sự được xử lý |
| Exception boundary | Exception được truyền qua các lớp advice với semantics đúng |
| Multiple aspects | Không gây duplicate business completion hoặc thay đổi thứ tự ngoài ý muốn |

Với chương trình Java, tích hợp thực tế có thể là compile-time weaving, load-time weaving hoặc proxy/interceptor, tùy công cụ được sử dụng. Mỗi cách có những đường gọi, khả năng join point và chi phí khác nhau. Test chỉ dùng fake invocation sẽ không phát hiện lỗi không nạp agent, không weave bytecode hoặc pointcut vô tình không khớp.

## 20. Tổ chức test suite theo hai lớp

Một tổ chức dễ duy trì:

~~~text
tests/
  advice-logic/
    observation-success-failure
    authorization-allow-deny
    scope-cleanup
    retry-policy
    async-completion
  aspect-integration/
    pointcut-selection
    metadata-binding
    advice-order
    aspect-instantiation
    bypass-cases
  end-to-end/
    protected-business-flow
    transaction-and-outcome
~~~

Đây là sơ đồ cấu trúc, không phải các tệp bắt buộc hay dự án đã tồn tại. Mục tiêu là tách lỗi:

- **Logic test thất bại:** nhiều khả năng bug trong handler/policy.
- **Logic test pass, integration test thất bại:** xem adapter, pointcut, weaving configuration, thứ tự hoặc giới hạn join point.
- **Integration pass, end-to-end thất bại:** xem dữ liệu, transaction, concurrency, lifecycle, external dependencies hoặc assumptions giữa các module.

Không nhất thiết tạo test case cho từng method helper chỉ vì pointcut từng khớp nó. Hãy kiểm thử behavior được hứa hẹn và vài trường hợp đại diện bao phủ cả match lẫn exclusion.

## 21. Anti-pattern trong kiểm thử Advice

### Anti-pattern A: gọi thẳng method bị instrument rồi coi là unit test của advice

Test này có thể hữu ích ở tầng integration, nhưng buộc logic test phụ thuộc weaver, cấu hình, thứ tự aspect và môi trường. Khó mô phỏng lỗi sink hoặc clock.

**Cải thiện:** rút logic vào handler rồi test trực tiếp; giữ một số test dệt thật.

### Anti-pattern B: kiểm thử chỉ bằng số dòng stdout

Một lời gọi có thể in hai dòng start/end hợp lệ; nhiều logger hoặc appenders có thể tạo bản sao. Thay đổi định dạng cũng làm test vỡ.

**Cải thiện:** kiểm tra event có schema/type/scope và số occurrence đúng; dùng in-memory sink.

### Anti-pattern C: sleep để ép timeout/latency

Test thất bại ngẫu nhiên khi CPU scheduler/CI thay đổi.

**Cải thiện:** fake clock, fake sleeper hoặc controlled executor.

### Anti-pattern D: kiểm tra exception type nhưng không kiểm tra nguyên nhân gốc

Advice có thể bọc lại hoặc thay stack/cause ngoài ý muốn.

**Cải thiện:** assert exact reference khi contract là preserve; nếu transform có chủ đích, assert cause và error code.

### Anti-pattern E: bỏ qua proceed call count

Một advice có thể gọi target hai lần trong đường success hoặc gọi dù authorization DENY.

**Cải thiện:** spy đếm proceed, quan sát side effect và kiểm tra đúng số lần theo policy.

### Anti-pattern F: dùng static shared fixture mutable

Các bài test có thể ảnh hưởng nhau và thất bại khi chạy song song.

**Cải thiện:** tạo handler và fake dependencies mới cho mỗi test; cleanup ThreadLocal/registry và test isolation.

### Anti-pattern G: giả lập outcome thành công cho Future chưa hoàn tất

Method return không chứng minh task success.

**Cải thiện:** tách test invocation khỏi test completion, dùng CompletableFuture do test kiểm soát.

### Anti-pattern H: cho rằng test handler pass nên bảo mật đầy đủ

Pointcut có thể bỏ sót protected operation hoặc target vẫn được gọi trực tiếp ngoài proxy.

**Cải thiện:** integration/architecture tests xác minh interception coverage và domain invariants trên các đường quan trọng.

## 22. Ma trận test cho các loại Advice thường gặp

| Loại advice | Test logic độc lập với weaver | Cần test weaving riêng |
| --- | --- | --- |
| Logging | Event, level, outcome, sanitize, sink failure | Những operation nào được log |
| Metrics | Counter/histogram, duration, sampling, dedup | Không đo trùng khi nhiều aspect |
| Tracing | Start/end, span status, scope close | Parent/child binding và order |
| Authorization | ALLOW/DENY, no proceed on deny, resource scope | Protected methods đều bị chặn |
| Retry | Attempt count, backoff, exception classification | Join points an toàn cho retry |
| Transaction boundary | Begin/commit/rollback contract, cleanup | Advice order và phạm vi thật |
| Caching | Hit/miss, key, TTL, invalidation | Những method được cache |
| Rate limiting | Permit/reject/release, timeout | Boundary và identity/key binding |
| Async completion | Return vs completion, failure, cancellation | Binding callback tới lời gọi thật |
| Audit | Event meaning, identity, durability policy | Event phát ở đúng business milestone |

Không phải mọi loại concern đều phù hợp để đưa vào aspect. Một business invariant quan trọng có thể cần được kiểm tra trong domain/storage boundary thay vì chỉ dựa vào advice được chọn bằng pointcut.

## 23. Rà soát thiết kế trước khi áp dụng

**Bước 1:** viết một bản mô tả đầu vào, đầu ra, exception và side effect cho advice. Quy định đây là observer, guard, retry hay transaction manager.

**Bước 2:** tách phần weaving-specific thành adapter mỏng. Không để handler truy cập trực tiếp quá nhiều API của trình dệt.

**Bước 3:** tạo interface nhỏ cho proceed, clock, event sink, policy/context và scheduler nếu cần.

**Bước 4:** thiết kế test doubles chủ động: fake invocation trả value/throw, clock trả dãy mốc, sink ghi list, policy trả allow/deny.

**Bước 5:** kiểm thử các nhánh chính và đường lỗi: success, exception, denial, observer failure, cleanup, null result, retry, nested calls.

**Bước 6:** bổ sung test state/concurrency/async nếu logic advice liên quan. Dùng controlled executor và coordination primitive thay vì sleep.

**Bước 7:** triển khai adapter cho cơ chế AOP đã chọn, kiểm thử riêng việc chuyển dữ liệu và exception.

**Bước 8:** chạy ít nhất một integration test với weaving thật để xác minh pointcut, order, instantiation và một số đường bypass.

**Bước 9:** kiểm thử end-to-end cho operation có side effect hoặc invariant quan trọng.

**Bước 10:** giữ tốc độ của tầng unit test để team có thể chạy thường xuyên; chỉ tăng số test weaving khi có rủi ro thực sự, không thay mọi test bằng integration.

## 24. Checklist hoàn thành một Advice có thể kiểm thử

- [ ] Logic advice có thể được gọi bằng interface thông thường mà không nạp weaver.
- [ ] Invocation giả có thể trả result, ném exception và đếm proceed.
- [ ] Result và exception gốc được bảo toàn theo contract.
- [ ] Deny/circuit-break/cache-hit không vô tình gọi target.
- [ ] Retry có attempt count, idempotency và failure semantics rõ.
- [ ] Clock, logger, metric sink và policy có thể thay bằng test doubles.
- [ ] Unit test không dùng sleep để khẳng định duration.
- [ ] Audit/telemetry failure có chính sách phù hợp, không che lỗi bất ngờ.
- [ ] Scope được đóng/khôi phục trong success, exception và early exit.
- [ ] Thread-local hoặc state của aspect không rò giữa các bài test.
- [ ] Async return và completion được kiểm thử theo mốc riêng.
- [ ] Pointcut, adapter, precedence và weaving coverage có integration tests.
- [ ] Một test bảo mật kiểm tra không có đường gọi nguy hiểm bypass enforcement.
- [ ] Không nhầm test logic handler thành bằng chứng rằng aspect đã được gắn vào runtime.
- [ ] Test tập trung vào **nghĩa** của event và policy, không vào chi tiết formatter.

## 25. Ghi nhớ

**Kiểm thử advice độc lập với bộ dệt** là kỹ thuật tách hai loại rủi ro:

1. **Rủi ro xử lý:** code quanh invocation có gọi tiếp đúng số lần, bảo toàn return/throw, cleanup, tính duration, kiểm tra quyền và phát event chính xác hay không.
2. **Rủi ro gắn kết:** pointcut có chọn đúng join point, adapter có binding đúng, weaving có hiệu lực, precedence và mô hình tạo aspect có phù hợp hay không.

Đưa xử lý có thể tái sử dụng vào một handler được gọi trực tiếp giúp test nhanh, xác định và dễ ép các nhánh khó như sink failure hoặc authorization DENY. **Sau đó vẫn phải kiểm thử bằng cơ chế AOP thật** ở những ranh giới quan trọng: logic đúng mà không được weave thì không có tác dụng; pointcut đúng nhưng advice logic sai cũng không an toàn.

Đây là cách đạt cả **khả năng kiểm thử ở mức đơn vị** lẫn **độ tin cậy của tích hợp AOP** mà không biến mọi bài kiểm thử thành một bài thử hệ thống lớn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-testing-002">Kiểm thử pointcut khớp đúng trường hợp mong muốn</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử pointcut khớp đúng trường hợp mong muốn trong AOP

## 1. Vì sao pointcut phải được kiểm thử?

Trong **Aspect-Oriented Programming (AOP)**, pointcut quyết định **những join point nào sẽ được áp dụng advice**. Một pointcut sai có thể làm advice **bỏ sót** nơi cần chạy (*false negative*) hoặc **can thiệp nhầm** nơi không được chạy (*false positive*).

Đây không chỉ là lỗi ghi log:

- Bỏ sót kiểm tra quyền ở một phương thức quan trọng có thể tạo lỗ hổng.
- Áp transaction lên phương thức không liên quan có thể thay đổi chi phí, locking và semantics.
- Áp retry vào thao tác ghi không idempotent có thể nhân đôi side effect.
- Một aspect quan sát quá rộng có thể ghi dữ liệu nhạy cảm hoặc tạo recursion.
- Một pointcut theo tên quá hẹp có thể mất hiệu lực khi refactor, overload hoặc thêm implementation mới.

**Mục tiêu kiểm thử pointcut** là chứng minh tập join point được chọn **trùng với tập mong muốn theo hợp đồng**, bao gồm cả trường hợp **phải match** và **không được match**. Chỉ kiểm thử việc một phương thức mẫu “đã in log” là chưa đủ: phải kiểm tra **độ chính xác của tập chọn, kiểu join point, thứ tự advice và hành vi thực tế sau weaving/interception**.

Bài này nói về **AOP tổng quát**, từ phân tích pointcut tới kiểm thử nâng cao. Các ví dụ dùng cú pháp AspectJ và compiler `ajc` như một cách hiện thực AOP có weaving. Cơ chế proxy, decorator và weaving có thể hỗ trợ tập join point khác nhau; khi chuyển một bài test giữa các công cụ phải điều chỉnh kỳ vọng theo cơ chế thực tế.

## 2. Những khái niệm cần xác định trước

| Khái niệm | Vai trò trong kiểm thử | Câu hỏi |
| --- | --- | --- |
| **Join point** | Một điểm thực thi/gọi hợp lệ trong mô hình AOP | Có những loại điểm nào: execution, call, constructor, field? |
| **Pointcut** | Biểu thức chọn một tập join point | Biểu thức này có chọn đúng tập được định nghĩa không? |
| **Advice** | Hành vi tại join point đã chọn | Advice chạy mấy lần, lúc nào, có giữ nguyên return/throw? |
| **Weaving / interception** | Cơ chế kết nối advice vào chương trình | Class/call site nào thực sự được xử lý? |
| **Shadow** | Vị trí có thể trở thành join point khi chương trình chạy trong AOP tĩnh/bytecode | Weaver có nhìn thấy và xử lý vị trí đó không? |
| **Positive case** | Một join point phải match | Có đúng một sự kiện advice tương ứng không? |
| **Negative case** | Một join point không được match | Advice không chạy tại vị trí này? |
| **Boundary case** | Trường hợp sát ranh giới biểu thức | Overload, subclass, generic, visibility, package khác xử lý sao? |

**Pointcut là một điều kiện trên join point, không chỉ là một bộ lọc tên method.** Ví dụ, `execution(public void TaskService.save(String))` mô tả việc **thực thi** một method có chữ ký tương ứng; còn `call(void TaskService.save(String))` chọn vị trí **gọi** tương ứng. Nơi phải weave và số lần match có thể khác nhau.

Trong AOP, có hai hợp đồng cần kiểm thử riêng:

1. **Selection contract:** tập join point nào được chọn hoặc loại trừ.
2. **Advice behavior contract:** khi đã được chọn, advice có giữ nguyên semantics nghiệp vụ và thực hiện policy đúng không.

Một phép kiểm thử có thể chứng minh “advice in một dòng” nhưng chưa chứng minh phương thức gốc được gọi đúng một lần, không bị đổi exception hoặc thiếu nhánh cleanup. Do đó hai hợp đồng phải được đo độc lập rồi kiểm thử tích hợp.

## 3. Đặc tả tập match trước khi viết biểu thức

Trước khi viết `execution(...)` hoặc chọn annotation, hãy lập bảng **quyết định** từ yêu cầu.

Giả sử policy là: **quan sát mọi lần thực thi công khai của `TaskService.save(String)`**, bao gồm khi được gọi từ bên trong cùng đối tượng; không quan sát overload nhận `int`, phương thức `cancel()`, phương thức `selfCall()` và phương thức cùng tên ở class khác.

| Tình huống | Kỳ vọng | Lý do |
| --- | --- | --- |
| `new TaskService().save("A")` | **MATCH** | Đúng class, tên, kiểu tham số và method execution |
| `new TaskService().save(7)` | **NO MATCH** | Overload khác kiểu tham số |
| `new TaskService().cancel()` | **NO MATCH** | Method khác |
| `new TaskService().selfCall()` | **NO MATCH** tại execution của `selfCall` | Nó không phải `save(String)` |
| `selfCall()` gọi nội bộ `save("self")` | **MATCH** tại execution của `save(String)` | Weaving tại method execution, không phụ thuộc caller |
| `new OtherService().save("A")` | **NO MATCH** | Class khác dù cùng tên/signature |
| Một join point nằm ngoài class được weave | Phụ thuộc phạm vi weaving | Không thể yêu cầu advice xuất hiện nếu vị trí cần weave không được xử lý |

Bảng này là **nguồn sự thật của bài test**. Không suy luận expected result từ pointcut đang viết: nếu pointcut sai và test cũng copy cùng giả định, cả hai có thể sai mà test vẫn xanh.

### 3.1. Chia kiểm thử thành ba lớp

- **Selection test:** dùng một aspect probe có hành vi rất nhỏ và xác định, ghi lại từng hit để so với bảng expected.
- **Advice contract test:** kiểm tra advice với target giả: số lần gọi, kết quả, lỗi, thứ tự và cleanup.
- **Integration/weaving test:** chạy artifact thật sau weaving/interception, chứng minh các join point được đánh dấu ở đúng class, class loader và đường gọi.

Khi sửa một pointcut bảo mật, phải coi đây là thay đổi về **phạm vi bảo vệ**. Một kiểm thử unit riêng cho hàm kiểm tra quyền không chứng minh pointcut đã bao phủ mọi đường vào.

## 4. Cách nhận biết false positive và false negative

### 4.1. False negative: thiếu một nơi cần chạy advice

Ví dụ một chính sách đòi đo mọi thao tác ghi, nhưng pointcut chỉ match `save(String)`; sau đó người khác thêm `save(Request)` hoặc `persist(Request)` mà không cập nhật scope. Test của method cũ vẫn xanh nhưng thao tác mới không được đo.

Các nguyên nhân thường gặp:

- Chữ ký hoặc kiểu tham số thay đổi.
- Pointcut chỉ chọn một class cụ thể, trong khi implementation mới nằm ở class khác.
- Phạm vi weave thiếu một thư viện, package hoặc module.
- Lời gọi đi qua đường nội bộ không kích hoạt proxy interception dù người viết tưởng có.
- Dùng `call(...)` nhưng caller code không được weave.
- Annotation không xuất hiện hoặc không được đọc ở vị trí mong muốn.
- Method trả asynchronous handle, advice chạy ở thời điểm return nhưng không quan sát completion.

### 4.2. False positive: bắt nhầm một nơi không thuộc chính sách

Ví dụ `execution(* *.save(..))` có thể chọn nhiều method `save` với nghĩa khác nhau: lưu tài liệu public, ghi cấu hình nội bộ, cập nhật token hoặc phương thức của telemetry sink. Một advice ghi full arguments khi đó có thể làm lộ dữ liệu.

Các nguyên nhân:

- Wildcard kiểu trả về, package hoặc tham số quá rộng.
- Chọn mọi class con của một base type trong khi một số subclass có nghĩa nghiệp vụ khác.
- Dùng `within(...)`/`withincode(...)` mà không hiểu phạm vi lexical.
- Nhiều aspect cùng pointcut gây đếm trùng hoặc advice lồng nhau.
- Áp `cflow(...)` khi không phân biệt lời gọi trực tiếp, nested call và recursive call.
- Annotation được đặt ở method khác với method thực sự được weave.

**Negative tests quan trọng ngang positive tests.** Chúng bảo vệ khỏi một kiểu regression rất dễ bỏ qua: chương trình vẫn chạy thành công nhưng advice âm thầm ảnh hưởng thêm các nơi ngoài ý định.

## 5. Test theo ma trận chữ ký và loại join point

### 5.1. Signature matching

Kiểm thử các chiều:

| Chiều | Trường hợp cần có | Rủi ro |
| --- | --- | --- |
| Tên method | `save`, `saveAll`, `persist` | Wildcard match cả tên không mong muốn |
| Kiểu tham số | `save(String)`, `save(int)`, `save(Object)` | `(..)` rộng quá, overload bị chọn nhầm |
| Số lượng tham số | 0, 1, varargs | Quy tắc `*` và `..` bị hiểu sai |
| Return type | `void`, `String`, `CompletionStage<?>` | Đổi return type làm pointcut mất match |
| Visibility | public/protected/package/private | Pointcut và cơ chế chặn có phạm vi khác nhau |
| Static/instance | method static và method instance | Một số cơ chế proxy không áp lên static |
| Interface/class | Interface method, concrete method | Signature ở interface khác execution tại implementation |
| Inheritance/override | Class cha, subclass, override | Target method được thực thi không như tên tĩnh |
| Generic/bridge | Generic method sau type erasure, bridge method | Có thể có bytecode bổ sung, cần kiểm tra weave info |
| Annotation | Trên interface, class, method hoặc override | Không mặc định annotation được kế thừa mọi nơi |
| Package | Class đúng module, class cùng tên ở package khác | Pattern quá rộng hoặc quá hẹp |

Không cần mọi dự án kiểm thử tích Descartes của toàn bộ các chiều. Hãy chọn những **ranh giới dễ làm đổi phạm vi chính sách**, và cố định bằng contract test khi thêm method hoặc nâng compiler.

### 5.2. Phân biệt `execution`, `call` và `within`

Trong mô hình AspectJ:

- `execution(...)` mô tả **method execution**. Với code method được weave, một self-call đến method ấy vẫn đi vào execution join point.
- `call(...)` mô tả **call site**. Code chứa lời gọi phải được weave; không đủ nếu chỉ class đích đã weave.
- `within(TypePattern)` giới hạn về **vị trí lexical** của code chứa join point, không có nghĩa tự động chọn mọi lần method được gọi bởi class đó.
- `withincode(SignaturePattern)` chọn join point **nằm trong thân** method/constructor phù hợp, không tự có nghĩa chọn execution của chính method đó.
- `this(...)` và `target(...)` có semantics khác nhau ở những loại join point khác nhau; phải kiểm tra trường hợp static hoặc join point không có target.
- `args(...)` quan sát kiểu/giá trị đối số runtime theo ngữ nghĩa pointcut, khác với signature pattern chỉ kiểm tra khai báo tĩnh.

Ví dụ trong môi trường có weaving:

~~~java
// Giả mã cú pháp pointcut: các class phải tồn tại trong dự án.
// execution: method được thực thi.
execution(void TaskService.save(String))

// call: vị trí gọi method.
call(void TaskService.save(String))

// Chọn execution nằm trong một package cụ thể.
execution(public * sample.app..*(..))
~~~

Đây là **các biểu thức minh họa**, không phải file Java chạy độc lập. `execution` và `call` không thể thay thế máy móc cho nhau trong mọi tình huống.

### 5.3. Tính động của pointcut

Một số pointcut có phần điều kiện chỉ xác định được khi chạy: `args(...)` với runtime type, `this(...)`, `target(...)`, `cflow(...)` hoặc `if(...)`. Với chúng, test phải thay **giá trị runtime, type của object và đường control flow**, không chỉ liệt kê method name.

Nếu policy phụ thuộc `actorId` hoặc trạng thái đơn hàng, hãy phân biệt **pointcut chọn nơi chạy advice** với **business predicate quyết định allow/deny**. Việc method đã match không chứng minh actor được phép thực hiện thao tác. Chia hai test: test selection và test policy.

## 6. Ví dụ Java 17+ và AspectJ: test đầy đủ cả match lẫn exclusion

Bốn file dưới đây tạo thành chương trình tự kiểm tra thứ tự sự kiện. Pointcut chọn chính xác `execution(public void TaskService.save(String))`; test ghi lại event thay vì chỉ trông vào log bằng mắt.

Dùng compiler `ajc` của AspectJ tương thích Java 17 và `aspectjrt.jar` cùng phiên bản. Đây là **ví dụ AOP weaving tổng quát**, không sử dụng framework web hay container ứng dụng.

### 6.1. Mã nghiệp vụ `TaskService.java`

~~~java
public class TaskService {
    public void save(String id) {
        PointcutContractTest.record("business:save(String):" + id);
    }

    public void save(int id) {
        PointcutContractTest.record("business:save(int):" + id);
    }

    public void cancel() {
        PointcutContractTest.record("business:cancel");
    }

    public void selfCall() {
        PointcutContractTest.record("business:selfCall");
        save("self");
    }
}
~~~

`TaskService` không biết aspect. Có hai overload để kiểm tra chữ ký, và `selfCall()` để chứng minh execution join point của `save(String)` vẫn được weave khi gọi từ trong cùng object.

### 6.2. Class ngoài phạm vi `OtherService.java`

~~~java
public class OtherService {
    public void save(String id) {
        PointcutContractTest.record("other:save(String):" + id);
    }
}
~~~

Dù tên `save` và kiểu tham số giống nhau, class khác phải **không khớp** pointcut đã mô tả cho `TaskService`.

### 6.3. Aspect `TaskPointcut.aj`

~~~java
public aspect TaskPointcut {
    pointcut selectedSave():
        execution(public void TaskService.save(String));

    before(): selectedSave() {
        PointcutContractTest.record("advice:before");
    }

    after() returning: selectedSave() {
        PointcutContractTest.record("advice:after");
    }
}
~~~

Aspect chỉ quan sát hai mốc: trước khi method được chạy và sau khi nó **trả bình thường**. Nếu method ném exception, `after() returning` sẽ không chạy. Khi cần kiểm tra đường lỗi, thêm test/advice thích hợp, không “chữa” bằng cách nuốt lỗi nghiệp vụ.

### 6.4. Bộ kiểm thử `PointcutContractTest.java`

~~~java
import java.util.ArrayList;
import java.util.List;

public class PointcutContractTest {
    private static final List<String> EVENTS = new ArrayList<>();

    public static void record(String event) {
        EVENTS.add(event);
    }

    public static void main(String[] args) {
        TaskService task = new TaskService();
        OtherService other = new OtherService();

        task.save("A");       // MATCH.
        task.save(7);         // NO MATCH: overload.
        task.cancel();        // NO MATCH: method khác.
        task.selfCall();      // selfCall KHÔNG match,
                              // nhưng save("self") ở bên trong MATCH.
        other.save("A");      // NO MATCH: class khác.

        List<String> expected = List.of(
            "advice:before",
            "business:save(String):A",
            "advice:after",
            "business:save(int):7",
            "business:cancel",
            "business:selfCall",
            "advice:before",
            "business:save(String):self",
            "advice:after",
            "other:save(String):A"
        );

        if (!EVENTS.equals(expected)) {
            throw new AssertionError(
                "Pointcut mismatch: expected=" + expected
                + " actual=" + EVENTS
            );
        }
        System.out.println("PASS: 2 matches; 4 other executions not advised");
    }
}
~~~

Đây là bài test **fail-fast**: thiếu advice ở self-call, bắt nhầm overload hoặc bắt nhầm class khác đều khiến danh sách không khớp. `List.of(...)` thuộc Java 9+ và hoạt động trong Java 17.

### 6.5. Build và chạy thực tế với `ajc`

Đặt 4 file cùng thư mục. Chuẩn bị hai artifact cùng phiên bản AspectJ: `aspectjtools.jar` (compiler) và `aspectjrt.jar` (runtime). Các lệnh dưới đây **đặt tên ngắn cho JAR** và có thể thay đường dẫn bằng vị trí file thực tế của bạn.

Trên Windows PowerShell:

~~~powershell
New-Item -ItemType Directory -Force woven | Out-Null
java -cp "lib\aspectjtools.jar" org.aspectj.tools.ajc.Main -17 -classpath "lib\aspectjrt.jar" -d woven TaskService.java OtherService.java PointcutContractTest.java TaskPointcut.aj
java -cp "woven;lib\aspectjrt.jar" PointcutContractTest
~~~

Trên macOS/Linux:

~~~bash
mkdir -p woven
java -cp "lib/aspectjtools.jar" org.aspectj.tools.ajc.Main -17 -classpath "lib/aspectjrt.jar" -d woven TaskService.java OtherService.java PointcutContractTest.java TaskPointcut.aj
java -cp "woven:lib/aspectjrt.jar" PointcutContractTest
~~~

**Stdout của bài test khi tất cả match/exclusion đúng:**

~~~text
PASS: 2 matches; 4 other executions not advised
~~~

Sự hiện diện của dòng `PASS` có nghĩa **mọi phần tử của event list đã được so sánh đúng từng vị trí**; nó không chỉ đơn thuần đếm hai lần advice chạy. Nếu chạy các class chưa weave bằng `javac` rồi `java`, test sẽ thất bại vì các event `advice:before`/`advice:after` không xuất hiện. Đó là hành vi mong đợi: bài test cũng phát hiện **cấu hình weaving bị thiếu**.

### 6.6. Những điều ví dụ đã và chưa chứng minh

Đã chứng minh:

1. Hai execution của `TaskService.save(String)` match, bao gồm self-call.
2. `TaskService.save(int)` không match.
3. `TaskService.cancel()` và `TaskService.selfCall()` không bị advice này chặn tại execution của chúng.
4. `OtherService.save(String)` không match dù cùng tên và tham số.
5. `before`/`after returning` đi đúng thứ tự trên đường thành công.
6. Weaving thực sự được bật trong chương trình đang chạy.

Chưa chứng minh: behavior của inherited method, interface method, abstract class, overload với generic bridge, dynamic pointcut, annotation, class loader khác, exception từ `save(String)`, concurrency, async hay tình huống nhiều advice có precedence. Những phần này cần test riêng trong dự án có các cấu trúc tương ứng.

Vì `EVENTS` là danh sách static không thread-safe, demo chủ ý **chạy tuần tự**; trong kiểm thử nhiều thread, dùng collector an toàn đồng thời và điều khiển thứ tự mà không dựa vào scheduler ngẫu nhiên.

## 7. Đối chiếu contract với sự kiện nhận được

Một lỗi kiểm thử phổ biến là kiểm tra theo kiểu:

~~~java
// MINH HỌA: cách assert còn quá yếu, không phải chương trình độc lập.
if (events.contains("advice:before")) {
    // Cho rằng pointcut đã đúng.
}
~~~

Check trên chỉ chứng minh advice đã chạy **ít nhất một lần**. Nó không phát hiện:

- Bị match thêm một method không mong muốn.
- Bị match hai lần do hai aspect chồng lấn.
- Thiếu match khi self-call.
- Advice chạy đúng method nhưng sai thời điểm hoặc thiếu cleanup.
- Runtime đang load class chưa weave ở một nhánh khác.

Cách kiểm chứng mạnh hơn là tạo **tập kỳ vọng** hoặc **chuỗi sự kiện kỳ vọng** trước, bao gồm giá trị **không được xuất hiện**. Các điểm cần xác minh:

- **Membership:** join point mong đợi đều được chọn, loại trừ đều không xuất hiện.
- **Multiplicity:** mỗi join point có đúng số lần advice mong đợi.
- **Ordering:** `before -> target -> after`, hoặc các lớp around theo order đã công bố.
- **Preservation:** method vẫn được gọi số lần đúng, trả cùng giá trị, truyền đúng exception.
- **Scope:** chỉ artifact/class loader/đường gọi trong phạm vi weaving nhận instrumentation.
- **Stability:** khi refactor, test giúp nhận biết sự thay đổi selection thay vì im lặng.

Khi thứ tự event không ổn định do concurrency, không so sánh một chuỗi global tuyến tính nếu hợp đồng không yêu cầu thứ tự ấy. Dùng correlation ID theo invocation, tập multiset, partial order hoặc rào đồng bộ để xác nhận **quan hệ thứ tự có ý nghĩa**.

## 8. Positive, negative và boundary tests nên viết thế nào?

### 8.1. Positive tests

Mỗi thao tác được policy bao phủ phải có ít nhất một test rõ:

- Thực thi thành công.
- Thực thi ném exception nếu policy bao gồm lỗi.
- Gọi trực tiếp, gọi qua method khác hoặc qua interface nếu hợp đồng yêu cầu.
- Loại concrete class/implementation được áp dụng.
- Thao tác mới được thêm vào API cần được lựa chọn.
- Nếu có điều kiện runtime, cả trường hợp giá trị hợp lệ khiến pointcut match.

Tuy nhiên, không dùng “mọi method đều match” như tiêu chí bao phủ tốt. Pointcut an toàn là **đúng danh sách**, không phải danh sách dài nhất.

### 8.2. Negative tests

Các loại trừ nên được coi là một phần **hợp đồng không can thiệp**:

- Method chỉ có tên gần giống hoặc overload khác.
- Package phụ trợ/telemetry sink, để ngăn recursion.
- Internal helper không mang policy.
- Getter của entity hoặc method dùng trong đường đo lường.
- Các loại giao dịch/thao tác mà retry/cache/audit không được áp dụng.
- Method thuộc class cùng tên ở module/package khác.
- Những join point không thuộc control flow dự định (với `cflow`).
- Trường hợp annotation absent hoặc annotation đặt sai scope.

Một negative test đặc biệt quan trọng với bảo mật và dữ liệu: **không có log sensitive arguments** tại những method không thuộc chính sách.

### 8.3. Boundary tests

Chọn những ranh giới liên quan thật tới expression:

- `save(String)` vs `save(CharSequence)` vs `save(Object)`.
- `process()` vs `process(int)` vs `process(int...)`.
- Class cha định nghĩa method vs subclass override.
- Interface method vs implementation, annotation trên interface hay trên method implementation.
- Package `foo.app` vs subpackage `foo.app.internal`.
- Public vs package-private/private, static vs instance.
- Giá trị runtime `null`, subclass instance, proxy object hoặc đối số bất đồng bộ.
- Một method gọi nhiều lần trong recursion/nested call.

Không giả định các trường hợp trên match thế nào nếu chưa xác định **designator**, **pointcut language** và **cơ chế interception** đang sử dụng.

## 9. Annotation pointcut: vị trí khai báo và retention

Pointcut dựa vào annotation thường giúp biểu đạt contract rõ ràng hơn so với quy tắc tên method, ví dụ đánh dấu thao tác cần audit. Nhưng annotation có nhiều nơi xuất hiện:

- Annotation trên interface method.
- Annotation trên implementation method.
- Annotation trên class/base class.
- Annotation trên method override trong subclass.
- Annotation được giữ tới class file nhưng không có runtime retention.
- Annotation chỉ đặt ở parameter/type use khác với method.

Một ví dụ cú pháp AspectJ mang tính minh họa:

~~~java
// Cần khai báo annotation Audited trong dự án thực.
// Đây là biểu thức pointcut minh họa, không phải chương trình độc lập.
pointcut marked():
    execution(@Audited * *(..));
~~~

Ý nghĩa chính xác của pattern và nơi annotation được chọn cần được xác nhận bằng tài liệu của ngôn ngữ AOP đang dùng. Đừng mặc định annotation trên interface tự hiện diện ở method override hoặc annotation method được kế thừa như annotation class trong mọi cơ chế.

**Chiến lược test:** tạo fixture gồm một method annotated đúng, một method không annotated, một method override, một interface implementation; định nghĩa rõ từng expected match. Nếu đổi retention hoặc vị trí annotation, kiểm thử phải phát hiện.

## 10. Inheritance, overrides, interface và generic bridge

### 10.1. Kế thừa và override

Một pointcut sử dụng kiểu base có thể chọn hoặc không chọn method thực thi ở subclass, tùy **type pattern**, chữ ký được chọn và cơ chế AOP. Với `execution`, chú ý **method body thực sự chạy** và nơi body đó được weave; với `call`, chú ý kiểu/đích ở vị trí gọi.

Test gợi ý:

1. Gọi method kế thừa không override.
2. Gọi method đã override ở subclass.
3. Gọi qua biến có kiểu interface hoặc superclass.
4. Gọi `super.method()` trong implementation.
5. Gọi từ self-call trong class con.
6. Gọi method cùng tên nhưng không nằm trong hierarchy mong muốn.

Không chỉ test một method bằng reflection để suy ra mọi dispatch path.

### 10.2. Generics và synthetic bridge methods

Java dùng **type erasure**. Compiler có thể phát sinh **bridge method** để giữ đa hình khi override generic method. Đối với code AOP/weaving làm việc với bytecode, những method synthetic/bridge có thể ảnh hưởng số join point thực sự được nhìn thấy hoặc loại chữ ký được báo cáo.

Không nên đoán “một method trong source chắc chắn tương ứng một execution shadow duy nhất trong mọi compiler”. Hãy dùng test thực tế, báo cáo weave info và kiểm tra số advice hit trên invocation cụ thể khi policy phụ thuộc đúng một lần.

Nếu đổi compiler/JDK hoặc refactor generic hierarchy, chạy lại test pointcut regression. Nếu project có code generation hoặc annotation processing, kiểm thử trên **artifact cuối cùng** thay vì chỉ source fixture đơn giản.

## 11. Pointcut có điều kiện runtime và control flow

Một biểu thức chọn kiểu động có thể nhận kết quả khác nhau dù cùng static signature.

Ví dụ mục tiêu policy là can thiệp lời gọi trong **control flow** của một operation quan trọng. Khi đó `cflow(...)` có thể giúp mô tả phạm vi, nhưng test cần hai đường:

~~~text
entryImportant()
  -> helper()
      -> work()      => nằm trong cflow mong muốn

entryOrdinary()
  -> helper()
      -> work()      => không nằm trong cflow mong muốn
~~~

Test không chỉ gọi `work()` một mình: phải kích hoạt **cả hai cây lời gọi**. Với recursion, xem policy muốn một advice trên mỗi tầng hay chỉ một lần theo logical operation; điều này khác nhau và cần hợp đồng tường minh.

Với `args(SomeSubtype)`, test cả object là subclass, object không đúng kiểu, `null` và những lời gọi generic/reflection có khả năng xuất hiện trong hệ thống thực. Dynamic matching có thể tốn chi phí vì phải đánh giá khi chạy; đo overhead trên hot path nếu pointcut rộng.

Với pointcut có condition phụ thuộc external state, test phải kiểm soát state, clock và phiên bản policy để không bị flaky. Tối ưu hơn là giữ pointcut mô tả **nơi cần policy chạy**, còn phép kiểm tra actor/resource cụ thể được chuyển vào policy evaluator có unit test riêng.

## 12. Scope của weaving và proxy là một phần của kiểm thử

### 12.1. Aspect không hoạt động không có nghĩa expression sai

Nếu pointcut mô tả đúng `execution(...)` nhưng bytecode class đích chưa weave, advice vẫn không chạy. Tương tự, với `call(...)`, nếu caller không nằm trong phạm vi weave thì call join point không có advice. Với proxy, lời gọi không đi qua proxy có thể bỏ qua interception.

Do đó bài test phải có hai lớp:

1. **Kiểm tra pointcut/selection** với fixture chắc chắn nằm trong phạm vi công cụ.
2. **Kiểm tra pipeline/build/runtime** chứng minh class thật được chặn đúng lúc chạy.

Một bản test chạy trong IDE bằng một classpath khác với artifact đóng gói có thể cho kết quả khác. Kết quả chạy phải xác nhận classpath, phiên bản aspect, output sau weave và môi trường load class.

### 12.2. Compile-time, binary và load-time weaving

- **Compile-time weaving (CTW):** compiler/weaver kết hợp mã aspect trong quá trình build nguồn. Test chạy trên bytecode đã weave.
- **Post-compile / binary weaving:** weave file `.class`/JAR có sẵn; test cần đọc **output artifact**, không chỉ thư mục class gốc.
- **Load-time weaving (LTW):** weaver can thiệp khi class được định nghĩa; test startup phải có agent/config phù hợp và class chưa load quá sớm.

Trong LTW, **class loader delegation** có thể ảnh hưởng lớp nào được weaver thấy. Chỉ kiểm tra unit bằng compiler trong module riêng chưa chứng minh application server tải class theo cùng đường.

Bật thông tin weaving của công cụ (chẳng hạn tùy chọn `-showWeaveInfo` với `ajc`) giúp xác định join point được weave, nhưng **weave-info không thay thế behavior assertion**. Một join point có thể được weave mà advice vẫn chạy sai số lần hoặc làm hỏng semantics.

### 12.3. Self-invocation không có quy tắc chung cho mọi công cụ

Đừng viết assertion “self-call luôn bị chặn” hoặc “self-call luôn bypass” nếu chưa nói cơ chế:

- **Execution-based weaving:** nếu thân method đích được weave, self-call dẫn tới execution ấy vẫn kích hoạt advice.
- **Call-based weaving:** vị trí gọi trong caller phải được weave.
- **Proxy/decorator:** lời gọi trực tiếp bằng `this` bên trong target có thể bỏ qua wrapper/proxy bên ngoài.

Một test tốt ghi rõ **loại join point và cơ chế**, không áp một kết luận cho toàn bộ AOP.

## 13. Kiểm tra advice độc lập với điểm match

Ngay cả khi pointcut match đúng, advice vẫn có thể sai. Thiết kế fake target để kiểm tra:

| Thuộc tính | Điều cần xác minh |
| --- | --- |
| `proceed` | Được gọi đúng một lần trừ khi hợp đồng công khai cho phép retry/skip |
| Args | Đầu vào gốc không bị sửa nếu policy chỉ quan sát |
| Return | Kết quả đúng như target trả khi không có transformation hợp lệ |
| Exception | Exception gốc được truyền hoặc mapping theo contract |
| Cleanup | `finally`/after advice chạy đúng trên các đường cần thiết |
| Side effect | Không bị lặp vì around advice sai |
| Metrics | Số hit/advice count không bị nhầm với số logical operation |
| Thread safety | State của advice không rò giữa invocation đồng thời |
| Async | Terminal outcome được đo ở completion, không chỉ khi trả future |
| Recursion | Advice không tự gọi lại join point vô hạn |

Đặc biệt, `after returning` không chạy khi method ném exception. Để ghi lỗi, test `after throwing`; để cleanup, cân nhắc `after` hoặc around `finally`. Nếu test chỉ chạy đường thành công, thiếu trường hợp lỗi là một false confidence.

## 14. Test được thứ tự của nhiều aspect

Một join point có thể khớp cả tracing, authorization và metrics. Dù mỗi pointcut đúng độc lập, thứ tự có thể tạo semantics khác:

~~~text
Metrics -> Authorization -> Domain
         deny: metrics vẫn đếm request bị từ chối

Authorization -> Metrics -> Domain
         deny: metrics bên trong không chạy
~~~

Với caching:

~~~text
Authorization -> Cache -> Domain
         cache hit vẫn qua authorization

Cache -> Authorization -> Domain
         cache hit có thể bypass authorization
~~~

Với retry:

~~~text
Retry -> Transaction -> Domain
         mỗi attempt có thể có transaction riêng

Transaction -> Retry -> Domain
         nhiều attempt có thể nằm trong một transaction
~~~

**Kiểm thử composition** bằng event collector có thể xác nhận chain trước/sau, cùng số lần target thực thi, exception và side effect. Nếu aspect ordering/precedence được cấu hình, cần test trên cấu hình thật. Không mặc định thứ tự khai báo class, tên file hay thời gian load sẽ là thứ tự advice đã mong muốn.

Một bài pointcut selection test có thể cố định đúng tập match nhưng **không chứng minh hệ thống an toàn nếu một cache bên ngoài trả sớm trước kiểm tra quyền**. Đây là lý do cần integration test cho pipeline đầy đủ.

## 15. Mutation testing cho pointcut

Một cách đo sức mạnh của bộ test là **cố tình làm pointcut sai**:

- Đổi `save(String)` thành `save(..)`, test negative overload phải đỏ.
- Đổi `TaskService.save` thành `*Service.save` hoặc pattern rộng, test class khác phải đỏ nếu bị bắt.
- Đổi `execution` thành `call` trong fixture có self-call/caller chưa weave, test tương ứng phải phát hiện chênh lệch.
- Loại trừ sai một package, test positive phải đỏ.
- Tắt weaving, test integration phải đỏ.
- Đổi `after returning` sang advice khác, test success/error phải phát hiện thay đổi.
- Tăng lời gọi `proceed()` từ một lên hai, test side effect phải đỏ.

Đây là các **mutation có chủ đích trong môi trường kiểm thử**, không phải thay đổi production. Nếu bỏ/đổi pointcut mà mọi test vẫn xanh, tập expected có thể quá ít hoặc chỉ đang test code advice thay vì thực sự test selection.

Mutation testing cho pointcut đặc biệt hữu ích với một policy được áp rộng bằng wildcard: tạo nhiều loại mutation để phát hiện cả overmatching lẫn undermatching.

## 16. Tránh flaky test và dữ liệu kiểm thử không đáng tin

### 16.1. Đồng thời và bất đồng bộ

Khi nhiều luồng được chặn, event từ các invocation có thể xen kẽ. Một assertion `List.of(beforeA, afterA, beforeB, afterB)` có thể sai nếu execution hợp lệ thực sự đan xen:

~~~text
before A
before B
after B
after A
~~~

Dùng correlation ID theo invocation và khẳng định **happens-before** cần thiết, thay vì đòi thứ tự global vốn không có trong contract. Với callback async, `after returning` trên method trả `CompletionStage` không tự chứng minh nghiệp vụ hoàn tất. Test cần chờ completion có kiểm soát, phân biệt terminal event với return-handle event và đảm bảo không double close.

### 16.2. Recorder và fake

Trong fixture tuần tự, một `ArrayList` là đủ để kiểm chứng chuỗi event. Trong test song song, collector cần thread-safe hoặc đồng bộ đúng. Không dùng log bên ngoài có sampling, async batching hoặc rate limiter làm nguồn khẳng định duy nhất số hit của pointcut; nó có thể cố ý bỏ dữ liệu.

Nên có một **probe đơn giản, không lọc/sampling**, được gọi từ advice ở môi trường test; sử dụng assert trên probe. Đừng áp cùng pointcut vào code recorder, tránh instrumentation recursion.

### 16.3. Kiểm soát classpath và trạng thái chung

- Chạy trên artifact được weave thực, không lẫn class gốc cùng tên trên classpath.
- Reset/khởi tạo event collector trước test; không để static state từ case trước lọt sang case sau.
- Xác minh compiler/JDK, class loader và phiên bản AOP tool dùng trong CI.
- Tránh phụ thuộc thứ tự test của framework.
- Không dựa vào `Thread.sleep` để hy vọng một callback đúng lúc.
- Với aspect có state per-target/per-flow, kiểm tra cleanup để tránh ảnh hưởng giữa test.

## 17. Quy trình CI kiểm chứng pointcut

Một pipeline bảo đảm tốt nên gồm:

1. **Compile code + aspect** hoặc cấu hình weaving runtime theo cách sản phẩm thực sẽ dùng.
2. **Bật diagnostics về weaving** ở môi trường CI phù hợp; lưu danh sách join point/weaver warnings để so sánh khi đổi build.
3. **Run positive/negative/boundary selection tests** trên class được weave.
4. **Run advice behavior tests** kiểm tra proceed, return, throw, cleanup và số lần gọi.
5. **Run composition tests** với cache, security, retry, transaction, tracing nếu có.
6. **Run bypass tests** qua self-call, direct access, proxy boundary, async và các đường gọi tin cậy khác.
7. **Run regression tests** khi đổi package, signature, annotation, compiler hoặc dependency.
8. **Benchmark overhead** nếu pointcut mới chọn nhiều method hơn; quan sát impact của logging/metrics trên hot path.
9. **Review kết quả diff coverage:** join point mới được chọn có được yêu cầu chấp thuận không? Join point cũ mất đi có được phép không?
10. **Kiểm thử artifact cuối** đóng gói/deploy thay vì chỉ một bản class dùng trong test.

Nếu dùng weaving ở startup, test chỉ chạy với `ajc` compile-time là chưa đủ để chứng minh agent/class loader trong production hoạt động. Hãy có ít nhất một integration test cùng mô hình startup thích hợp.

### 17.1. Một phép kiểm kê đơn giản

Mỗi pointcut quan trọng có thể đi kèm bảng như:

| Mã | Phạm vi mong đợi | Exclusions | Loại test |
| --- | --- | --- | --- |
| PC-1 | Public execution của method theo policy | Overload không thuộc policy, helper | Selection test |
| PC-2 | Method có annotation audit đúng vị trí | Method không annotated, override không phù hợp | Annotation fixture |
| PC-3 | Join point trong control flow của operation | Cùng method ngoài flow | Dynamic/cflow fixture |
| PC-4 | Chính sách kiểm tra quyền tại API boundary | Bypass qua cache/proxy/self-call | Integration/security test |
| PC-5 | Span đo hoàn tất thao tác async | Chỉ trả handle không tính terminal success | Completion lifecycle test |

Bảng kiểm kê không thay thế tests; nó giúp review **phạm vi mong đợi** trước khi viết một expression rất rộng và khó diễn giải.

## 18. Các sai lầm thường gặp

| Sai lầm | Hệ quả | Cách khắc phục |
| --- | --- | --- |
| Chỉ test một method đúng tên | Không phát hiện overload/subclass bị chọn nhầm | Bảng match và no-match rõ |
| Chỉ assert advice chạy `>=1` lần | Không phát hiện advice chạy thừa/thiếu | Assert tập và multiplicity |
| Test pointcut trên code không weave | False failure hoặc nhầm tưởng advice hỏng | Xác nhận artifact/classpath/weaving |
| Dùng `call` nhưng chỉ weave target method | Không chặn call site trong caller | Weave caller hoặc chọn `execution` theo contract |
| Giả định self-call giống nhau ở mọi AOP | Test sai cơ chế interception | Xác định join point + proxy/weaving |
| Wildcard package quá rộng | Chặn helper, telemetry, method nhạy cảm | Scope có contract/exclusion + negative tests |
| Chỉ kiểm tra thành công | Không biết after throwing/finally đúng không | Test lỗi, return và cleanup |
| Sao chép pointcut vào logic tính expected | Test và implementation sai giống nhau | Viết matrix độc lập từ yêu cầu |
| Bỏ qua static/bridge/generic edge cases | Double hit hoặc miss khó giải thích | Test artifact cuối và weave diagnostics |
| Dựa vào log đã sampling | Đếm sai advice hits | Test probe không bị drop |
| So event global của nhiều thread | Flaky vì scheduler | Correlation ID, partial order |
| Chỉ unit-test policy, không test scope | Chính sách đúng nhưng không bao phủ đường vào | Selection + integration/bypass |
| Không test sau refactor signature/package | Silent break của pointcut | Regression tests trong CI |

## 19. Checklist thực hành

Trước khi coi một pointcut “đúng”, xác minh:

1. **Yêu cầu:** danh sách join point *phải match* và *không được match* được định nghĩa độc lập với expression chưa?
2. **Loại điểm:** method `execution`, `call`, constructor, field hay event khác? Có chọn đúng loại không?
3. **Chữ ký:** return, parameter types/count, visibility, static/instance, overload đã được tính đến chưa?
4. **Hierarchy:** interface/override/inheritance/generics/bridge có làm thay đổi kết quả không?
5. **Annotation:** annotation được đặt và giữ ở nơi nào? Có được match theo rule thật không?
6. **Dynamic conditions:** `args`, `this`, `target`, `cflow` và value runtime đã có cả positive/negative path chưa?
7. **Weaving:** class hoặc call site thật có được weave? Cấu hình loader/startup có bao phủ không?
8. **Số lần chạy:** advice chạy đúng multiplicity, đúng thứ tự và không bị đếm trùng?
9. **Behavior:** target gọi bao nhiêu lần, có bị đổi args/return/exception/cleanup ngoài hợp đồng?
10. **Composition:** cache hit, authorization deny, retry, transaction, fallback và async completion có làm skip advice sai không?
11. **CI:** đổi signature, thêm method, nâng compiler hoặc chỉnh pointcut có kích hoạt regression test đáng tin?
12. **Bảo mật/hiệu năng:** không chọn nhầm sensitive method, không gây recursion, không bùng nổ overhead trên hot path?

**Kết luận:** kiểm thử pointcut đúng nghĩa là kiểm thử **tập join point và các đường loại trừ** ở artifact thực tế. Điểm mạnh của AOP là một biểu thức có thể áp chính sách nhất quán ở nhiều nơi; điểm rủi ro cũng chính là khả năng ấy. Vì vậy, đừng chỉ chứng minh advice **có chạy** — hãy chứng minh nó **chạy đúng nơi, không chạy sai nơi, chạy đúng số lần và không làm thay đổi hợp đồng nghiệp vụ ngoài ý muốn**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-testing-003">Kiểm thử âm để phát hiện can thiệp ngoài phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử âm để phát hiện can thiệp ngoài phạm vi trong AOP

## 1. Mục tiêu và cách hiểu đúng “kiểm thử âm”

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, **AOP**), pointcut chọn những **join point** mà advice sẽ chạy. Kiểm thử thường tập trung vào đường dương tính: một phương thức nằm trong phạm vi được chọn thì advice có thực thi hay không. Nhưng thiết kế aspect chỉ đúng khi nó **không can thiệp vào những điểm ngoài phạm vi**.

**Kiểm thử âm (negative testing) trong bài này** là chủ động thực thi các đường gọi mà một aspect **không được áp dụng**, rồi xác nhận không có dấu vết can thiệp tương ứng. Ví dụ, một advice đo nghiệp vụ không được chạy khi phương thức ghi metric được gọi; một retry advice không được tự động lặp tác vụ gửi thanh toán chưa có idempotency; một advice ghi log thông số không được đọc payload bí mật của phương thức nằm trong danh sách loại trừ.

Điểm phân biệt quan trọng:

- **Điểm phải được can thiệp nhưng advice không chạy:** lỗi **thiếu độ bao phủ**, cần kiểm thử dương tính phát hiện.
- **Điểm bị loại trừ nhưng advice vẫn chạy:** lỗi **can thiệp ngoài phạm vi**, cần kiểm thử âm phát hiện.
- **Điểm bị loại trừ và advice không chạy vì cơ chế AOP bị tắt hoàn toàn:** chưa đủ bằng chứng; phải có kiểm soát dương tính cho thấy aspect thực sự đang hoạt động ở điểm được phép.
- **Advice có chạy nhưng không phát log vì logger bị tắt:** không được coi là bằng chứng advice không chạy. Cần quan sát trực tiếp lời gọi advice hoặc tác động đặc trưng của nó bằng một probe tin cậy.

Kiểm thử âm không đồng nghĩa chỉ kiểm tra rằng kết quả trả về bình thường. Một advice không mong muốn có thể chạy rồi vẫn giữ nguyên kết quả, nhưng làm tăng chi phí, ghi lộ dữ liệu, gọi API ngoài, đổi transaction context hoặc tạo telemetry thừa.

Sau bài này, người học có thể lập danh sách điểm được chọn và bị loại trừ, xây test oracle, kiểm thử lớp proxy/weaving theo ngữ nghĩa AOP thực tế, dùng probe và assertions đáng tin, kiểm tra tương tác nhiều aspect, cũng như thiết kế regression test chống nới pointcut ngoài ý muốn.

## 2. Xác định hai tập điểm trước khi viết test

Gọi:

- **J** là tập join point có thể xuất hiện trong phạm vi công cụ AOP đang kiểm tra.
- **R** là tập **required**: join point phải có một advice cụ thể.
- **F** là tập **forbidden**: join point không được áp dụng advice ấy.
- **A** là tập thực tế đã được advice áp dụng trong phép chạy kiểm thử.

Hai bất biến cần kiểm tra:

~~~text
Required coverage:     R ⊆ A
Forbidden exclusion:   F ∩ A = ∅
~~~

Điều kiện đầu là kiểm thử dương tính; điều kiện sau là **kiểm thử âm**. Một hệ thống muốn tự tin về phạm vi cần kiểm tra **cả hai**.

Ví dụ với một aspect ghi số liệu thời gian ở tầng nghiệp vụ:

| Join point hoặc đường gọi | Phạm vi kỳ vọng | Vì sao? |
| --- | --- | --- |
| Thực thi thao tác submitOrder | REQUIRED | Là ranh giới nghiệp vụ cần đo |
| Thực thi phương thức healthCheck | FORBIDDEN | Không thuộc operation được đo |
| Thực thi MetricsExporter.publish | FORBIDDEN | Advice có thể tự gọi exporter, gây recursion |
| Thực thi passwordFormatter hoặc logSecret | FORBIDDEN | Có nguy cơ đọc hoặc ghi dữ liệu nhạy cảm |
| Thực thi phương thức của một thành phần mới | REVIEW | Chưa xác định nên thêm hay loại |
| Gọi submitOrder từ một callback mới | REVIEW/REQUIRED tùy cơ chế | Phải xác minh join point có thực sự được weaving |

**R và F luôn gắn với một advice cụ thể.** Một lời gọi có thể thuộc R của logging nhưng thuộc F của retry. Không nên tạo một danh sách “cấm mọi AOP” cho cả hệ thống mà không giải thích tính chất của từng aspect.

Các thông tin phải được chốt cho mỗi test case:

1. Loại join point: method execution, method call hay loại khác.
2. Chữ ký/kiểu và vị trí mã chứa join point.
3. Đối tượng thực nhận lời gọi: proxy, target, object đã được weave hay đối tượng khác.
4. Aspect/advice nào đang được xét, thứ tự áp dụng của advice nếu có nhiều.
5. Tín hiệu test dùng để kết luận advice chạy hoặc không chạy.
6. Tác dụng phụ mà advice bị cấm có thể gây ra nếu chạy.
7. Phạm vi build/runtime đã bật instrumentation và có tính đại diện cho sản phẩm.

## 3. Những dạng can thiệp ngoài phạm vi thường cần phát hiện

### 3.1. Pointcut quá rộng

Ví dụ một biểu thức chọn “mọi phương thức public trong toàn module” có thể match thêm utility, health endpoint, logger, exporter hoặc lớp mới được thêm sau khi viết pointcut.

~~~text
Dự kiến:
  OrderService.placeOrder -> advice
  OrderService.health     -> không advice

Sau khi nới pointcut:
  OrderService.placeOrder -> advice
  OrderService.health     -> advice ngoài ý muốn
~~~

Một bài kiểm thử âm gọi health() rồi xác minh advice không chạy sẽ phát hiện sự thay đổi này, ngay cả khi kết quả health vẫn trả về bình thường.

### 3.2. Bỏ sót điều kiện loại trừ

Một pointcut vừa chọn operation nghiệp vụ vừa loại trừ infrastructure có thể được mô hình hóa như:

~~~text
selected = businessOperations AND NOT telemetryInternals
~~~

Nếu điều kiện NOT bị mất trong một lần refactor, cùng một advice logging có thể chặn cả chính phương thức exporter. Kết quả thường là log storm hoặc vòng gọi lồng nhau không kết thúc.

Kiểm thử âm nên có một case riêng cho từng nhóm loại trừ trọng yếu, thay vì kiểm tra chỉ một trường hợp bất kỳ.

### 3.3. Sai vị trí lexical hoặc sai kiểu join point

Trong các mô hình AOP có phân biệt method-call và method-execution:

- Advice tại **call site** có thể chạy vì đoạn mã gọi được match, dù phương thức được gọi thuộc một module khác.
- Advice tại **method execution** bám theo việc thực thi thân phương thức đã được instrument.
- Điều kiện phạm vi như **within** thường giới hạn vùng mã chứa join point theo ngữ nghĩa của công cụ.

Một pointcut định giới hạn code gọi trong module A nhưng lại chọn thêm call site ở module B là lỗi ngoài phạm vi. Ngược lại, một điểm execution ở callee không nhất thiết nằm trong cùng vùng lexical với caller.

Vì vậy, testcase phải ghi rõ kỳ vọng **không advice ở call site cụ thể** hay **không advice ở execution cụ thể**. Nếu chỉ assert kết quả cuối, rất khó biết loại điểm bị chọn sai.

### 3.4. Lời gọi lồng nhau gây advice hai lần

Giả sử một lời gọi nghiệp vụ bên ngoài dẫn vào phương thức con cùng match pointcut. Nếu chính sách chỉ muốn ghi **một kết quả cuối cho operation**, advice ghi kết quả chạy ở cả cha và con có thể tạo hai sự kiện.

~~~text
processOrder  -> RecordFinal?
    reserveInventory -> RecordFinal? (không mong muốn nếu đây chỉ là bước con)
~~~

Kiểm thử âm cần xác nhận **không có sự kiện final ở join point con**, chứ không chỉ xác nhận tổng số log lớn hơn 0. Có thể vẫn có trace/span cấp con nếu đó là một aspect khác đã được phép.

### 3.5. Retry áp dụng vào thao tác không được lặp

Một method gửi email, tạo thanh toán hoặc phát message có thể thuộc ngoài phạm vi của retry advice nếu chưa có idempotency. Nếu advice vô tình chạy, một lỗi tạm thời có thể khiến proceed() được gọi nhiều lần.

Kiểm thử âm phải tiêm lỗi và xác nhận **số lượt thực hiện effect** không bị nhân đôi. Không nên chỉ nhìn vào việc người gọi cuối cùng nhận exception.

### 3.6. Recorder, transaction hoặc context advice chạm sai lifecycle

Một aspect có thể vô tình mở transaction ở hàm chỉ đọc, giữ connection qua callback, hoặc đính correlation context vào thread pool rồi không khôi phục. Các lỗi ấy có thể xảy ra ngay cả khi giá trị trả về giống nhau.

Đối với join point nằm ngoài phạm vi, testcase nên xác minh không phát sinh transaction begin, không thay context, không mở/đóng tài nguyên và không ghi metric đáng lẽ chỉ thuộc operation được chọn.

## 4. Test oracle: làm sao chứng minh “không có advice”?

**Oracle** là điều kiện mà bài test dùng để xác định pass/fail. Chứng minh một điều không xảy ra khó hơn nhìn thấy một sự kiện đã xảy ra. Vì vậy cần thiết kế chứng cứ trực tiếp.

### 4.1. Dùng probe ghi số lần advice vào/ra

Tạo một probe độc lập ghi lại:

~~~text
ADVICE_ENTER: operation-name
ADVICE_RETURN: operation-name
ADVICE_THROW: operation-name
~~~

Với điểm thuộc F, số event advice tương ứng phải **bằng 0**. Với điểm thuộc R, số event phải phù hợp với hợp đồng, thường ít nhất một hoặc chính xác một trong phép thử đơn giản.

Nếu advice chỉ ghi vào logger trong production, trong test có thể sử dụng một recorder/fake có thể quan sát được. Recorder này phải **không bị chính pointcut đang kiểm thử intercept**.

### 4.2. Kết hợp với phép kiểm tra hành vi đích

Một bài test tốt không chỉ assert số advice bằng 0; nó còn xác nhận đường gọi **đã thực thi thật**:

~~~text
invoke excludedOperation()
assert targetInvocations == 1
assert adviceInvocations == 0
assert returnValue == expected
~~~

Nếu targetInvocations cũng bằng 0, có thể test chưa gọi đúng đường, nên giá trị adviceInvocations bằng 0 không nói lên được điều gì.

Với một operation không có side effect, có thể dùng spy/counting stub để chứng minh target được gọi đúng một lần. Với effect nhạy cảm, cần kiểm tra trạng thái tài nguyên thực và số lần gửi thông điệp/đòi tiền bằng stub hoặc sandbox phù hợp.

### 4.3. Không dựa vào “không thấy log”

Vắng log có thể do:

- Advice không chạy.
- Advice chạy nhưng log level không cho phép in.
- Sampling loại bỏ event.
- Exporter mất sự kiện.
- Ghi log bất đồng bộ chưa flush.
- Lỗi logger bị nuốt.
- Logger bị instrument rồi xảy ra recursion hoặc deadlock.

Do đó, đo số lần advice bằng probe/spy trực tiếp trong test là dấu hiệu đáng tin hơn. Log có thể là chứng cứ phụ trợ, không phải oracle duy nhất.

### 4.4. Kiểm soát dương tính chống phép thử rỗng

Luôn có ít nhất một case thuộc R trên **cùng cấu hình và build runtime** để bảo đảm advice hoạt động. Ví dụ:

~~~text
Case positive: approvedOperation()
    targetInvocations = 1
    adviceInvocations = 1

Case negative: health()
    targetInvocations = 1
    adviceInvocations = 0
~~~

Nếu cả hai case đều có adviceInvocations = 0, test âm không được tính là bằng chứng loại trừ đúng; rất có thể aspect chưa được đăng ký hoặc weaving bị tắt.

## 5. Ví dụ Java 17+ tự chạy: kiểm thử âm và kiểm soát dương tính

Đoạn chương trình sau sử dụng **Java Dynamic Proxy** để minh họa cơ chế interception dựa trên interface. Nó không thay thế cơ chế AOP tổng quát hoặc bytecode weaving. Một annotation tên Observed biểu diễn allowlist do bài test định nghĩa. Proxy ghi event khi một phương thức được chọn bị intercept; các phương thức không được chọn vẫn gọi target bình thường.

Ví dụ có các ca sau:

1. Phương thức transfer() thuộc phạm vi: phải có advice.
2. health() bị loại: không được có advice.
3. emitMetric() thuộc hạ tầng quan sát: không được bị instrument bởi cùng advice.
4. batch() không được chọn; nó gọi transfer() trên chính target. Test chứng minh lời gọi nội bộ không đi qua proxy trong mô hình này.
5. Lời gọi thẳng target.transfer() cũng không đi qua proxy.
6. Một proxy **cố tình chọn mọi method** được dùng làm mutation: kiểm thử âm phải nhận ra rằng health() đã bị can thiệp ngoài phạm vi.

Tạo tệp **AspectNegativeTestDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class AspectNegativeTestDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Observed {}

    interface Gateway {
        @Observed
        void transfer();
        void health();
        void emitMetric();
        void batch();
    }

    static final class RealGateway implements Gateway {
        private final List<String> events;

        RealGateway(List<String> events) {
            this.events = events;
        }

        public void transfer() {
            events.add("business.transfer");
        }

        public void health() {
            events.add("business.health");
        }

        public void emitMetric() {
            events.add("infrastructure.metric");
        }

        public void batch() {
            events.add("business.batch");
            transfer(); // Lời gọi trực tiếp trên target, không qua proxy.
        }
    }

    static Object invokeTarget(
            Gateway target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static Gateway instrument(
            Gateway target,
            Predicate<Method> selected,
            List<String> events) {
        return (Gateway) Proxy.newProxyInstance(
                Gateway.class.getClassLoader(),
                new Class<?>[]{Gateway.class},
                (proxy, method, args) -> {
                    if (!selected.test(method)) {
                        return invokeTarget(target, method, args);
                    }

                    events.add("advice.before " + method.getName());
                    try {
                        Object result = invokeTarget(target, method, args);
                        events.add("advice.after " + method.getName());
                        return result;
                    } catch (Throwable failure) {
                        events.add("advice.throw " + method.getName());
                        throw failure;
                    }
                });
    }

    static void expectSequence(List<String> actual, String... expected) {
        if (!actual.equals(List.of(expected))) {
            throw new AssertionError(
                    "expected=" + List.of(expected) + ", actual=" + actual);
        }
    }

    static void expectNoAdvice(List<String> events) {
        boolean intercepted = events.stream()
                .anyMatch(event -> event.startsWith("advice."));
        if (intercepted) {
            throw new AssertionError("Unexpected advice: " + events);
        }
    }

    public static void main(String[] args) {
        List<String> events = new ArrayList<>();
        RealGateway raw = new RealGateway(events);

        Gateway scoped = instrument(
                raw,
                method -> method.isAnnotationPresent(Observed.class),
                events);

        scoped.transfer();
        expectSequence(events,
                "advice.before transfer",
                "business.transfer",
                "advice.after transfer");
        System.out.println("PASS: selected method intercepted");
        events.clear();

        scoped.health();
        expectSequence(events, "business.health");
        expectNoAdvice(events);
        System.out.println("PASS: health excluded");
        events.clear();

        scoped.emitMetric();
        expectSequence(events, "infrastructure.metric");
        expectNoAdvice(events);
        System.out.println("PASS: metric exporter excluded");
        events.clear();

        scoped.batch();
        expectSequence(events, "business.batch", "business.transfer");
        expectNoAdvice(events);
        System.out.println("PASS: internal target call not intercepted");
        events.clear();

        raw.transfer();
        expectSequence(events, "business.transfer");
        expectNoAdvice(events);
        System.out.println("PASS: raw target bypass observed");
        events.clear();

        // Mutation: nới pointcut sai thành "mọi phương thức".
        Gateway tooBroad = instrument(raw, method -> true, events);
        tooBroad.health();
        boolean detected = false;
        try {
            expectNoAdvice(events);
        } catch (AssertionError expected) {
            detected = true;
        }
        if (!detected) {
            throw new AssertionError("Negative test failed to detect mutation");
        }
        System.out.println("PASS: broadened pointcut detected");
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac AspectNegativeTestDemo.java
java AspectNegativeTestDemo
~~~

Stdout:

~~~text
PASS: selected method intercepted
PASS: health excluded
PASS: metric exporter excluded
PASS: internal target call not intercepted
PASS: raw target bypass observed
PASS: broadened pointcut detected
~~~

### 5.1. Diễn giải chính xác các phép thử

**Selected method intercepted:** khi gọi scoped.transfer(), Method object của interface mang annotation Observed, nên interceptor chạy. Bài test so đúng cả thứ tự advice-before, business, advice-after.

**health excluded:** gọi qua chính proxy nhưng Predicate không chọn health(). Điều này chứng minh loại trừ của predicate có hiệu lực; không phải vì test bỏ qua proxy.

**metric exporter excluded:** ghi telemetry không tự kích hoạt cùng advice. Đây là một dạng negative test quan trọng chống recursion và event amplification.

**Internal target call not intercepted:** scoped.batch() chuyển tới target.batch(), sau đó target tự gọi transfer() trên chính nó. Vì target không gọi proxy, advice cho transfer() không xuất hiện. Đây là hành vi của **mô hình proxy minh họa**, không phải quy tắc cho mọi cơ chế weaving.

**Raw target bypass observed:** gọi thẳng raw.transfer() không có advice. Bài test chứng minh được giới hạn của proxy; **đây không phải kết quả an toàn mong muốn nếu transfer() cần bắt buộc kiểm soát quyền**. Phải phân biệt negative test cho một aspect quan sát được phép bỏ qua đường raw với positive/security test đòi mọi đường tới effect nhạy cảm đều được bảo vệ.

**Broadened pointcut detected:** khi predicate biến thành true cho mọi method, health() bị advice chọn sai. expectNoAdvice ném AssertionError như dự kiến; test harness bắt đúng lỗi để chứng minh oracle sẽ báo lỗi nếu pointcut bị nới rộng.

### 5.2. Giới hạn của ví dụ

- Đây là một chương trình Java thuần **mô phỏng cơ chế chọn join point bằng proxy**, không phải test integration cho một AspectJ weaving pipeline.
- Dynamic Proxy nhận lời gọi qua interface; method được gọi trực tiếp trên target không đi qua InvocationHandler. Với method-execution weaving, kết luận về self-invocation có thể khác.
- Test thực tế không nên chỉ chọn bằng annotation; có thể dùng pointcut expression theo cú pháp công cụ AOP, nhưng vẫn cần bảng expected matches độc lập.
- Một chương trình demo dùng List và thao tác đồng bộ; với nhiều thread, recorder phải thread-safe và bảo đảm thứ tự/correlation. Không lấy output ngẫu nhiên hoặc phụ thuộc thời gian làm oracle cứng.
- Không nên cố tình nới pointcut trong production. Mutation ở đây là một thay đổi giả lập chỉ nằm trong testcase.

## 6. Ma trận kiểm thử âm theo loại aspect

Không có một bộ test chung đủ cho mọi chính sách. Mỗi aspect cần negative tests phù hợp rủi ro riêng.

| Aspect/policy | Join point ngoài phạm vi cần thử | Oracle âm tối thiểu | Rủi ro nếu sai |
| --- | --- | --- | --- |
| Logging | Hàm chứa secret, formatter, logger internals | Không có advice capture, không ghi payload/secret | Rò dữ liệu hoặc recursion |
| Metrics | Exporter, health(), utility quá nhỏ | Không advice đo/không tăng counter tương ứng | Metric sai, overhead cao |
| Tracing | Bootstrap tracer, exporter, vùng excluded | Không tạo span/scope ở điểm cấm | Recursion, trace không đúng |
| Retry | Gửi tiền/email chưa idempotent; validation lỗi cố định | Không gọi proceed() lặp, không backoff retry | Effect trùng, sai semantics |
| Transaction | Utility/read-only bị loại; callback không thuộc transaction | Không begin/commit ngoài scope, không giữ resource | Lock thừa, commit sai |
| Authorization | Các tiện ích được phép bỏ guard, nhưng **không** sensitive effect | Không guard ở utility; đồng thời sensitive effect phải được bảo vệ bằng test dương tính | Overhead hoặc bypass bảo mật |
| Cache | Method có effect bắt buộc | Không short-circuit/hit cache ngoài scope | Bỏ qua effect nghiệp vụ |
| Fallback | Lỗi quyền hoặc vi phạm bất biến nghiệp vụ | Không tạo fallback success ngoài policy | Che lỗi và bỏ kiểm tra |
| Correlation context | Formatter/exporter hoặc worker không thuộc scope | Không rò context, không tạo scope mới ngoài dự kiến | Log sai ID hoặc rò context |
| Audit | Những operation không thuộc diện audit bắt buộc | Không phát record bắt buộc/trùng | Sai bằng chứng, chi phí và độ nhạy dữ liệu |

**Bảo mật:** không được diễn giải việc “authorization advice không chạy ở sensitive method” là pass nếu sensitive method thực sự cần authorization. Với operation nhạy cảm, các đường bypass cần được coi là lỗi qua một test khác; negative tests ở đây chỉ áp dụng cho **điểm được phê duyệt loại trừ**.

## 7. Kiểm thử âm ở tầng join point, advice và hậu quả

Có ba tầng oracle bổ trợ nhau.

### 7.1. Tầng chọn join point

Xác minh pointcut có chọn điểm bị cấm không. Công cụ kiểm thử có thể dùng một probe để ghi các static join point signature, tên kiểu, tên method và loại join point.

Ví dụ bảng oracle:

~~~text
method execution Business.placeOrder   expected=selected
method execution Business.health       expected=excluded
method call from Telemetry.export      expected=excluded
method execution PolicyEngine.evaluate expected=excluded
~~~

Việc assert sai signature phải báo lỗi trước khi suy luận về effect nghiệp vụ.

### 7.2. Tầng chạy advice

Một điểm được pointcut chọn nhưng advice có guard nội bộ và quyết định bỏ qua thao tác có thể **vẫn chạy advice**. Nếu yêu cầu là “tuyệt đối không thực thi advice tại điểm ấy”, việc chỉ kiểm tra không thấy metric cuối là chưa đủ.

Phải phân biệt:

- **Không match pointcut:** advice không vào.
- **Match pointcut nhưng advice early-return/no-op:** advice đã vào, dù không tạo effect chính.
- **Advice chạy và bị logger/sampling làm mất dấu:** không thể kết luận bằng log.

Nếu chính sách chỉ yêu cầu “không có tác dụng phụ X”, early-return có thể được chấp nhận. Nếu yêu cầu “không bị can thiệp”, chỉ số advice invocation cần bằng 0.

### 7.3. Tầng hành vi và side effect

Ngay cả khi advice chạy nhưng không thay giá trị trả về, nó có thể:

- Mở transaction/connection không cần thiết.
- Đọc dữ liệu nhạy cảm để format log.
- Chặn thread khi exporter chậm.
- Ghi metric trùng.
- Gây retry một effect chưa được thiết kế chống lặp.
- Đặt/sai khôi phục ThreadLocal.
- Nuốt một exception mà caller cần thấy.

Do đó, negative tests quan trọng nên assert thêm số connection mở, số event đã ghi, số lượt effect, trạng thái context trước/sau và loại exception giữ nguyên theo hợp đồng.

## 8. Chứng minh kiểm thử âm không “pass giả”

Một negative test chỉ có giá trị khi nó thật sự có khả năng **phát hiện advice bị áp vào điểm cấm**. Nếu toàn bộ bộ dệt không được nạp, handler không được đăng ký hoặc test gọi nhầm đối tượng gốc, kiểm tra `adviceCount == 0` có thể “xanh” trong khi bảo vệ đang hỏng. Vì vậy, xây một **phép thử có chứng cứ dương tính ngay trong cùng runtime** thay vì chỉ viết assertion âm.

### 8.1. Quy trình ba chốt kiểm soát

| Chốt | Quan sát cần có | Khi sai phải làm gì? |
| --- | --- | --- |
| **Target activation** | Điểm ngoài phạm vi đã thật sự được gọi và phát effect dự kiến | Báo lỗi fixture hoặc đường gọi, không kết luận exclude đúng |
| **Instrumentation canary** | Một điểm được phép trong cùng artifact/runtime vẫn nhận đúng advice | Báo lỗi weaving, proxy wiring hoặc recorder, không chấp nhận test âm |
| **Forbidden match assertion** | Điểm cấm có advice invocation **bằng 0**, theo đúng loại aspect và join point | Báo lỗi pointcut quá rộng, thiếu exclusion hoặc wrong join point kind |

Ví dụ bộ kiểm chứng theo event recorder riêng biệt:

~~~text
setup:
    reset deterministic recorder
    verify selectedJoinPoint and forbiddenJoinPoint share test runtime

positive control:
    call requiredOperation
    assert targetCount(requiredOperation) == 1
    assert adviceCount(TracingAspect, requiredOperation) == 1

negative control:
    call excludedOperation
    assert targetCount(excludedOperation) == 1
    assert adviceCount(TracingAspect, excludedOperation) == 0

assert recorder is not disabled, sampled away or intercepted recursively
~~~

Đây là *mã giả*, không phải API dùng chung của mọi công cụ AOP. Tên aspect cần được gắn vào event vì có thể một join point bị loại khỏi **TracingAspect** nhưng vẫn hợp lệ cho **AuthorizationAspect**. Nếu chỉ so tổng số event của mọi aspect, phép thử có thể kết luận nhầm.

### 8.2. Kiểm tra sức phát hiện bằng mutation có mục đích

Để chứng minh negative test thực sự nhạy với lỗi, trong môi trường kiểm thử có thể:

1. Bỏ điều kiện exclusion của đúng aspect đang xét hoặc nới predicate để nó match một điểm cấm.
2. Chạy lại **cùng case âm**; kiểm tra nó thất bại với lý do “forbidden advice observed”.
3. Khôi phục cấu hình đúng; chạy lại cả positive canary và negative test; cả hai phải pass.
4. Lặp lại ở các nhóm cấm có rủi ro khác nhau như logger, serializer, health check và thao tác không được retry.

Mutation phải nằm trong test/build thử nghiệm, không gây tác động ngoài ý muốn tới môi trường production. Nếu test vẫn pass sau khi nới pointcut, có thể đường bị cấm chưa được gọi, recorder không thấy advice, hoặc loại join point đang quan sát không trùng loại join point của rule.

### 8.3. Không nhầm exclusion với đường bypass bảo mật

Trong một proxy-based test, việc gọi trực tiếp target có thể bỏ qua proxy, nên không có advice. Nhưng nếu điểm cần bảo vệ **bắt buộc** đi qua authorization, đó là một *coverage failure*, không phải một negative test thành công. Nhãn “forbidden” phải được thiết kế theo **hợp đồng của từng concern**, không suy từ việc hệ thống tình cờ không intercept.

Các phần tiếp theo triển khai phép thử âm cho những join point cụ thể: `call` và `execution`, self-invocation, overload, hành vi lỗi, context và tài nguyên. Mỗi nhóm phải chứng minh đúng **đường được gọi**, **loại advice được kiểm tra** và **tác dụng không được xảy ra**.

## 9. Kiểm thử âm với `call`, `execution` và vùng mã chứa join point

Một số hệ thống AOP hỗ trợ phân biệt join point ở **vị trí gọi** và **thực thi phương thức**. Tùy cơ chế, trường hợp:

~~~text
A.run() --> B.work()
~~~

có thể có:

~~~text
call(B.work)      ở mã A.run
execution(B.work) ở thân B.work
~~~

Nếu cấu hình cần instrument execution của B nhưng **không** instrument call site trong A, test âm phải khẳng định **không có call-advice**, đồng thời test dương xác nhận **có execution-advice**.

Nếu một pointcut giới hạn theo vùng lexical, cần kiểm tra cả trường hợp call site trong vùng được phép và ngoài vùng:

~~~text
CallerAllowed.invoke  -> call B.work  expected advice at call
CallerExcluded.invoke -> call B.work  expected no call advice
B.work execution      -> xem như một kỳ vọng riêng
~~~

Đừng gộp tất cả event của cùng một phương thức vào một counter. Một lượt gọi có thể có cả call và execution join point; đếm trùng không thể phân biệt nếu event chỉ ghi tên method.

Với những phương thức được gọi từ nhiều package/module, thiết kế test cho từng nhóm caller và **biến thể nơi viết lời gọi**. Di chuyển một câu lệnh từ CallerA sang Helper có thể làm điều kiện lexical thay đổi dù nghiệp vụ không đổi; negative test phải phản ánh quy tắc thực sự mong muốn.

## 10. Self-invocation và đối tượng gốc: test âm không được che một lỗ hổng

Trong interception dựa vào proxy:

~~~text
caller -> proxy.operation()
            -> target.operation()
                  -> this.helper()
~~~

Lời gọi this.helper() có thể không đi qua proxy. Nếu helper **được phê duyệt không có advice telemetry**, việc không có telemetry ở helper là kết quả kỳ vọng. Nhưng nếu helper **bắt buộc có authorization**, cùng hiện tượng ấy là lỗi nghiêm trọng.

Đối với một số cơ chế weaving tại method-execution join point, self-call không nhất thiết bỏ qua advice. Vì vậy test nên dùng đúng loại runtime đang triển khai, không sao chép kỳ vọng từ hệ thống proxy sang weaving hoặc ngược lại.

Cần có các ca:

1. Gọi phương thức qua proxy/đối tượng được instrument.
2. Gọi cùng method từ một method khác trên target.
3. Gọi trực tiếp target nếu đường đó tồn tại trong kiến trúc.
4. Gọi từ callback, worker, job hoặc một entry point khác.
5. Kiểm tra riêng **expected no-advice** và **required guard** cho các hành vi nhạy cảm.

**Không dùng sự thiếu advice trên một đường bypass làm lý do để tuyên bố chính sách bảo mật an toàn.**

## 11. Kiểm thử nhiều aspect có thể cùng match một điểm

Khi có nhiều advice, chỉ kiểm tra tổng số invocation là chưa đủ. Một method có thể được phép tracing nhưng không được phép retry. Nếu test chỉ thấy “có một advice chạy”, nó không chứng minh advice đúng loại.

Ví dụ:

~~~text
Allowed:
    Tracing(BusinessOperation)

Forbidden:
    Retry(NonIdempotentPayment)
    LoggingRawPayload(SecretFormatter)
~~~

Trong test, tạo bộ đếm riêng:

~~~text
traceAdviceEnter
retryAdviceEnter
logPayloadAdviceEnter
transactionAdviceEnter
~~~

Kỳ vọng phải gắn với từng join point và **từng concern**, không chỉ một cờ “intercepted”.

Ngoài ra, thứ tự advice có thể khiến một advice không được chạy dù pointcut thực sự match. Ví dụ Cache ở ngoài Authorization có thể trả kết quả từ cache mà không gọi proceed(); test âm chỉ thấy Authorization không chạy không chứng minh Authorization bị loại khỏi pointcut. Để xác minh selection, tạo một đường khiến outer advice có proceed hoặc đo trực tiếp tập match, rồi kiểm tra thêm các nhánh short-circuit để đảm bảo policy của toàn hệ thống đúng.

## 12. Các biến thể lỗi nên đưa vào test âm

### 12.1. Phương thức overload

Một pointcut chỉ dành cho **save(String)** không được tự động áp cho **save(byte[])** nếu byte[] thuộc danh sách cấm. Gọi cả hai overload trong test và assert match đúng chữ ký.

### 12.2. Kiểu kế thừa hoặc interface

Một lớp con có thể override method hoặc sinh bridge method vì generic. Với proxy và weaving, phạm vi interception khác nhau. Không tự suy đoán từ tên lớp; kiểm tra object thực, chữ ký và điểm join point.

### 12.3. Phương thức final/private/static

Khả năng can thiệp phụ thuộc cơ chế AOP. Subclass proxy không thể override final method theo kiểu thông thường, trong khi weaving có thể áp dụng kỹ thuật khác. Test âm phải chạy trên đúng implementation, không chuyển một giới hạn công cụ thành quy tắc phổ quát.

### 12.4. Reflection, method handle và callback

Reflection vào proxy có thể đi qua handler; reflection vào target có thể không. Method handle cũng phụ thuộc receiver và cơ chế instrument. Callback có thể chạy trên thread khác và không đi qua cùng entry point. Các test này cần nêu rõ receiver, thread, thời điểm và ranh giới xử lý.

### 12.5. Các phương thức tiện ích được gọi từ chính advice

Log formatter có thể gọi toString(), equals(), hashCode() hoặc serializer. Một pointcut quá rộng có thể chặn các lời gọi ấy, tạo vòng đệ quy hoặc chi phí lớn. Gọi trực tiếp các tiện ích trong test và xác nhận không có advice bị cấm.

### 12.6. Constructor và khởi tạo đối tượng

Nếu policy chỉ dành cho method execution, hãy kiểm tra nó không vô tình áp vào constructor/initialization join point nếu runtime AOP hỗ trợ loại điểm đó. Không dùng sai pattern constructor rồi coi kết quả âm là bằng chứng.

## 13. Kiểm thử tình huống âm liên quan lỗi và kết quả trả về

Một aspect bị áp ngoài phạm vi có thể làm thay đổi cách lỗi được quan sát.

Ví dụ một method bị loại trừ ném một exception nghiệp vụ nhất định. Test:

~~~text
Given excludedOperation throws DomainException("invalid")
When call excludedOperation
Then target invoked exactly once
And forbidden advice invoked zero times
And caller receives DomainException with preserved meaning
And no retry, no fallback, no transaction side effect occurred
~~~

Đối với một method trả về kết quả hợp lệ mang trạng thái BUSINESS_REJECTED, cũng không được tự biến nó thành success khác qua fallback nếu policy không áp dụng.

Với retry:

~~~text
Given nonIdempotentAction sends one external message then fails
When invoke through configured AOP boundary
Then forbidden retry advice invocations = 0
And action invocations = 1
And external sends = 1
And original failure is propagated
~~~

Không dùng một dịch vụ thanh toán hoặc email thật trong unit test; sử dụng stub/in-memory recorder hoặc môi trường sandbox. Những bài kiểm thử về độ an toàn của retry nên có integration/fault-injection test bổ sung tại nơi effect thực được tạo.

## 14. Kiểm thử âm cho ngữ cảnh và tài nguyên

Aspect quản lý correlation context, transaction context hay scope tracing có thể rò trạng thái dù không đổi giá trị trả về.

Một testcase ngoài phạm vi cần quan sát:

~~~text
before:
    currentContext = previousContext
    activeTransactions = 0

invoke excludedOperation()

after:
    currentContext == previousContext
    activeTransactions == 0
    unexpectedSpanCount == 0
    unexpectedAdviceCount == 0
~~~

Nếu method được chạy trong thread pool, test một nhiệm vụ **tiếp theo trên cùng worker** để xem có context bị giữ sót không. Phải phân biệt ngữ cảnh do caller hợp lệ gắn sẵn với ngữ cảnh mới do aspect bị cấm tạo ra.

Với asynchronous API, ranh giới hoàn tất là quan trọng: một method có thể trả future ngay nhưng tác vụ vẫn chạy. Không kết luận không có advice/effect chỉ vì trước lúc callback chạy chưa thấy sự kiện. Test cần chờ completion hoặc tạo scheduler điều khiển được để kết quả không phụ thuộc may rủi.

Với Publisher có thể có nhiều subscription, một assertion ở lúc tạo publisher không đủ chứng minh không có advice tại subscribe/terminal signal. Hãy thiết kế test theo lifecycle mà policy thật sự định áp dụng.

## 15. Test âm không ổn định và các cách làm cho kết quả đáng tin

### 15.1. Không dùng sleep tùy ý để chờ event

Nếu test phát triển dựa trên Thread.sleep(100), kết quả có thể pass/fail tùy CPU. Hãy dùng synchronization primitives hoặc hoàn tất của future/scheduler do test điều khiển, cộng với deadline hợp lý.

### 15.2. Cô lập event giữa các test

Reset counter và event list trước mỗi case. Nếu dùng recorder toàn cục, một event từ testcase trước có thể khiến case sau fail giả. Trong môi trường concurrent, gắn call ID hoặc test ID và chỉ assert những event thuộc phạm vi hiện tại.

### 15.3. Không ghi nhận event bằng chính aspect bị test

Nếu probe nằm trong phạm vi pointcut, kết quả có thể đệ quy hoặc méo. Recorder nên ở ngoài phạm vi can thiệp hoặc có điều kiện loại trừ độc lập đã được xác minh.

### 15.4. Đối chiếu cả kết quả và absence

Một negative test tốt thường assert:

- Đúng method/đường gọi đã được thực thi.
- Giá trị trả về và exception không bị thay đổi ngoài hợp đồng.
- Số advice bị cấm bằng 0.
- Số side effect phụ do advice bị cấm bằng 0.
- Không có rò resource/context sau khi hoàn tất.

Không cần mọi testcase đều assert tất cả; chọn theo rủi ro. Nhưng riêng absence của một log không đủ.

### 15.5. Dùng kiểm soát dương tính trên cùng runtime

Nếu test âm chạy với weaving bị tắt còn test dương dùng một runtime khác, không chứng minh exclusion đúng trong production. Cấu hình instrumentation phải được xác thực trong môi trường tương đương, hoặc có integration suite kiểm tra đúng build/artefact.

## 16. Mutation testing: cố tình nới pointcut để kiểm tra chất lượng phép thử

Một negative test chỉ đáng tin nếu nó **fail khi vùng cấm bị can thiệp**. Có thể kiểm tra bằng mutation:

1. Bỏ một điều kiện loại trừ khỏi pointcut ở cấu hình test.
2. Thay allowlist predicate bằng một predicate rộng hơn.
3. Thay package giới hạn bằng tập bao phủ toàn module.
4. Đổi điều kiện loại trừ đối với telemetry internals.
5. Chuyển method ngoài phạm vi sang group được match và xem test có phát hiện.

Kỳ vọng: testcase dành cho vùng cấm phải fail. Nếu nó vẫn pass, có thể đang:

- Không gọi đúng method.
- Đo sai loại advice.
- Đọc log của observer bị tắt.
- Kiểm tra kết quả cuối mà bỏ qua việc advice đã chạy.
- Không kích hoạt đúng điều kiện khiến pointcut mới match.
- Dùng runtime không có instrumentation.

Trong ví dụ Java ở phần trước, proxy tooBroad là mutation cố ý; expectNoAdvice bắt được việc health() bị chọn sai. Đó là kiểm chứng chất lượng oracle, không phải yêu cầu đưa mutation vào production.

Không dùng mutation testing để thay thế kiểm thử tích hợp với cơ chế AOP thật. Nó giúp kiểm tra rằng suite có khả năng phát hiện **một loại sai lệch** được mô phỏng.

## 17. Kiểm thử tích hợp trên runtime AOP thật

Ví dụ tự chạy bằng Java Dynamic Proxy chứng minh nguyên lý, nhưng một dự án dùng bytecode weaving hoặc interception khác cần kiểm thử trực tiếp công cụ đang dùng.

### 17.1. Cấu hình cần cố định

- Bản build và tập class được instrument.
- Loại weaving/interception: trước biên dịch, sau biên dịch, tại class load, hoặc proxy.
- Classloader/module và tập điểm được áp dụng.
- Thứ tự/precedence của advice khi nhiều aspect match.
- Các flag tắt/bật hoặc điều kiện môi trường cho instrumentation.
- Phiên bản Java và compiler tương thích.

Một suite test có thể pass vì test chạy trên class chưa được weave, trong khi production đã bật weaving rộng hơn. Ngược lại, test có thể fail vì double-instrument một lớp. Cần kiểm tra cấu hình thực tế tương ứng.

### 17.2. Hai kiểu kiểm thử bổ sung

**Unit test policy-selection:** dùng fake join point hoặc một adapter phân loại method để kiểm thử các rule ALLOW/DENY nhanh và rõ.

**Integration test interception:** thực thi qua cơ chế AOP thật rồi xác minh advice nào chạy, số lần, thứ tự, exception và side effect. Đây là bằng chứng quan trọng cho việc “ngoài phạm vi thực sự không bị can thiệp”.

Một pointcut dạng chuỗi có thể match theo quy tắc rất khác với predicate đơn giản trong unit test. Vì vậy **unit test của predicate không tự chứng minh một expression thật được áp dụng đúng**.

### 17.3. Kiểm thử class mới

Khi thêm lớp mới vào package/mô-đun, pointcut dùng wildcard có thể đột nhiên chọn cả những phương thức chưa từng tồn tại trước đó. Cần:

- Review những loại method mới.
- Thêm negative case cho các loại được loại trừ.
- Kiểm tra signature pattern trong runtime.
- Chạy lại một bộ regression cố định để phát hiện can thiệp mở rộng.

Đây là lý do không nên coi negative testing là một bài test viết một lần rồi quên.

## 18. Chiến lược kiểm thử theo ranh giới bảo mật

Một thiết kế có thể phân tách hai loại yêu cầu:

1. **Advice X không được áp vào operation Y**, vì nó sẽ gây tác động ngoài ý muốn.
2. **Chính sách bảo mật Z phải được thực thi trên mọi đường có thể tới sensitive effect**, kể cả khi đường đó không qua interception của advice khác.

Ví dụ, không muốn tracing aspect đọc thông tin nhạy cảm khi gọi secretFormatter(), nên negative test có kỳ vọng “tracing advice count = 0” tại formatter. Nhưng việc bảo vệ secretFormatter() khỏi caller không có quyền là một vấn đề khác. Không thể suy ra “không tracing” đồng nghĩa “không kiểm tra quyền”.

Để tránh sai lệch kiến trúc:

- Ánh xạ từng điều kiện bảo mật tới principal, action, resource và điểm cưỡng chế tin cậy.
- Kiểm thử mọi đường gọi tới effect nhạy cảm, kể cả đường ngoài proxy hoặc callback.
- Đối với điểm không được áp một aspect quan sát cụ thể, vẫn cho phép một lớp kiểm soát quyền hợp lệ khác chạy.
- Không dùng điểm cấm của một advice làm điểm mù bảo mật của toàn ứng dụng.

Nếu negative test phát hiện một advice authorization không chạy trên một đường phải được bảo vệ, kết quả phải được phân loại **security coverage failure**, không được ghi “excluded as expected”.

## 19. Các sai lầm phổ biến khi viết kiểm thử âm

**Sai lầm 1 — Chỉ viết test dương tính.** Pointcut có thể được nới rộng và bắt thêm health(), exporter hoặc dữ liệu nhạy cảm mà tất cả test dương vẫn pass. Bổ sung test cho từng nhóm điểm cấm.

**Sai lầm 2 — Chỉ kiểm tra không có log.** Logger có thể tắt/sampling/drop. Dùng probe trực tiếp đếm advice invocation, kết hợp assertions trên side effect.

**Sai lầm 3 — Không kiểm tra target thực sự chạy.** Một testcase không gọi đúng đường có thể cho advice count = 0 và pass giả. Assert target invocations/giá trị kết quả.

**Sai lầm 4 — Tắt toàn bộ AOP khi test.** Mọi negative case pass giả. Bắt buộc có positive control chạy trên cùng runtime.

**Sai lầm 5 — Gộp tất cả advice thành một biến intercepted.** Một aspect được phép, aspect khác bị cấm. Đếm riêng theo concern/join point.

**Sai lầm 6 — Không phân biệt call và execution.** Một event execution có thể bị hiểu nhầm là call advice; cần lưu loại điểm và nơi phát sinh.

**Sai lầm 7 — Khẳng định self-call luôn bỏ qua AOP.** Đúng với một số proxy, không đúng như một quy luật cho mọi weaving. Test phải dùng đúng cơ chế đang triển khai.

**Sai lầm 8 — Đánh đồng việc không có advice với không có tác dụng phụ.** Target có thể tạo effect hợp lệ, hoặc một advice khác tạo effect. Kỳ vọng phải cụ thể về advice bị cấm và loại side effect cấm.

**Sai lầm 9 — Không test exception path.** Advice sai có thể chỉ kích hoạt retry, rollback hay fallback khi target ném lỗi. Cần tiêm lỗi để kiểm tra các nhánh này.

**Sai lầm 10 — Test async bằng sleep ngắn.** Event đến sau assertion có thể làm pass giả. Chờ completion theo hợp đồng hoặc dùng scheduler điều khiển được.

**Sai lầm 11 — Coi pointcut mở rộng là vô hại nếu output giống nhau.** Instrumentation quá rộng vẫn có thể làm mất dữ liệu, lộ secret, tăng chi phí, giữ lock hoặc tạo recursion.

**Sai lầm 12 — Không cập nhật nhóm cấm khi refactor.** Một helper mới có thể bị wildcard match. Review và cập nhật regression suite mỗi khi thay đổi module/typing/entry points.

## 20. Bảng checklist trước khi phê duyệt aspect

| Câu hỏi | Điều kiện đạt |
| --- | --- |
| Pointcut có phạm vi rõ không? | Có danh sách required và forbidden theo từng concern |
| Mỗi negative case có kích hoạt join point? | Xác minh target hoặc call site chạy thật |
| Có positive control không? | Advice chạy ở điểm required trên cùng runtime |
| Có oracle trực tiếp không? | Advice count, join point identity, side effect được đo rõ |
| Có kiểm tra telemetry internals? | Không recursion; exporter/logger không bị aspect quan sát chặn |
| Có kiểm tra exception và retry? | Không lặp proceed ngoài policy; lỗi được bảo toàn |
| Có kiểm tra dữ liệu nhạy cảm? | Không capture/log trường bị cấm |
| Có kiểm tra tài nguyên/context? | Không transaction/span/context phát sinh hoặc rò ngoài scope |
| Có kiểm tra call/execution? | Loại join point và vị trí code chứa được phân biệt |
| Có kiểm tra self-call, raw target và callback? | Kỳ vọng dựa vào interception model thật |
| Có xét aspect cùng tồn tại? | Không advice bị cấm nào xuất hiện, dù advice khác được phép |
| Có kiểm tra mutation? | Bỏ exclusion/nới pointcut làm negative test fail |
| Có test trên build được weave thực không? | Runtime/config trong test có chứng cứ đại diện |
| Có đo kết quả async đúng thời điểm? | Không assert trước khi công việc liên quan hoàn tất |
| Có regression test khi thêm class? | Kiểm tra wildcard và nhóm loại trừ mới |

## 21. Bài tập tự kiểm tra

### Tình huống 1

Một aspect ghi log được thiết kế chỉ chạy quanh các phương thức nghiệp vụ. Test gọi health() trả về UP và không in dòng “advice”. Đã chứng minh health() bị loại khỏi pointcut chưa?

**Đáp án:** Chưa đủ. Log có thể bị tắt. Cần xác nhận health() đã chạy qua đúng đối tượng được instrument, probe advice không ghi event và có positive control chứng minh aspect vẫn hoạt động trên operation được chọn.

### Tình huống 2

Một bộ test âm dùng trực tiếp target thay vì proxy và quan sát advice count bằng 0. Nó kết luận rằng pointcut loại trừ method đúng. Sai ở đâu?

**Đáp án:** Nếu interception chỉ diễn ra tại proxy thì gọi raw target không thể chứng minh quy tắc loại trừ của proxy. Phải gọi qua proxy để kiểm tra exclusion, hoặc nếu mục tiêu là kiểm tra bypass bảo mật thì coi đường raw là một ca phủ định khác.

### Tình huống 3

Pointcut có hai loại join point: call và execution cho cùng một phương thức. Một phép thử ghi nhận hai event rồi cho rằng aspect chạy dư một lần. Nhận định ấy có chắc đúng?

**Đáp án:** Không. Một lượt gọi được instrument đầy đủ có thể tạo một call join point và một execution join point. Phải nêu rõ đơn vị đo mong muốn và loại advice nào được phép; nếu metric định đếm một operation thì thiết kế chọn cả hai có thể gây trùng.

### Tình huống 4

Một aspect retry được loại trừ khỏi sendEmail(), nhưng test chỉ chạy đường thành công. Có đủ phát hiện retry ngoài phạm vi không?

**Đáp án:** Không chắc. Khi không có exception, retry advice ngoài phạm vi vẫn có thể gọi proceed() đúng một lần, khiến test không thấy lặp. Cần tiêm lỗi retryable và đếm số invocation/effect hoặc đếm chính advice retry.

### Tình huống 5

Test âm cho thấy advice tracing không chạy trên secretFormatter(). Có thể kết luận bí mật được bảo vệ khỏi mọi đường truy cập không?

**Đáp án:** Không. Negative test chỉ chứng minh một aspect cụ thể không can thiệp tại điểm đó. Quyền truy cập và bảo vệ dữ liệu cần test riêng, ở ranh giới tin cậy.

### Tình huống 6

Sau khi xóa điều kiện NOT telemetryInternals trong pointcut, bộ test âm vẫn pass. Khả năng nào cần điều tra?

**Đáp án:** Test có thể không gọi đúng exporter, không đo trực tiếp advice, chưa bật instrumentation, hoặc predicate trong unit test khác expression thật. Mutation phải khiến test fail nếu rule được kiểm tra đúng.

### Tình huống 7

Một method tạo CompletableFuture trả handle ngay, advice bị cấm chỉ chạy khi completion callback sau vài giây. Test assert “không có event” ngay sau method return có đủ không?

**Đáp án:** Không. Phải chờ completion hoặc điều khiển scheduler để quan sát đúng vòng đời, rồi assert không có advice bị cấm trong toàn scope đã xác định.

### Tình huống 8

Một nhóm muốn loại trừ metric exporter khỏi timing advice, nhưng việc loại trừ khiến họ sợ mất thông tin về lỗi exporter. Có mâu thuẫn không?

**Đáp án:** Không. Có thể theo dõi exporter bằng một cơ chế riêng không tạo recursion, thay vì dùng chính timing advice mà exporter được gọi bên trong. Negative test cần tập trung chứng minh **advice bị cấm** không chạy ở exporter; observability của exporter vẫn có thể thực hiện ở ranh giới độc lập.

## 22. Kết luận

**Kiểm thử âm để phát hiện can thiệp ngoài phạm vi** là kiểm tra tính chính xác của ranh giới pointcut từ phía ngược lại: không chỉ “nơi cần advice thì có advice”, mà còn “nơi cấm advice thì thật sự không có advice”. Phải kích hoạt các đường bị loại trừ, quan sát trực tiếp lượt advice và tác dụng phụ, đồng thời có positive control chống việc test pass giả do instrumentation bị tắt.

Một bộ kiểm thử tốt bao gồm:

- Ma trận required/forbidden riêng cho từng aspect.
- Assertions cho join point thực, advice count, kết quả và side effect.
- Các trường hợp proxy/weaving, call/execution, lời gọi nội bộ, callback và exception.
- Mutation test cố tình mở rộng pointcut để xác minh oracle phát hiện sai lệch.
- Integration/regression test chạy trên build, classloader và cấu hình can thiệp thực tế.

Cuối cùng, việc một advice **không chạy đúng nơi bị loại** không được đánh đồng với việc một **đường tới tài nguyên nhạy cảm được bảo vệ**. Hai yêu cầu này phải được xác minh độc lập để AOP vừa không gây can thiệp thừa vừa không tạo lỗ hổng thiếu bảo vệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-testing-004">Kiểm thử kiểu trả về, ngoại lệ và giá trị null</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử kiểu trả về, ngoại lệ và giá trị null trong Aspect (AOP)

## 1. Kiểm thử aspect là kiểm thử sự bảo toàn hợp đồng

Trong Aspect-Oriented Programming (AOP), pointcut chọn các join point; advice chạy trước, sau hoặc bao quanh một join point. Một aspect có thể đo thời gian, ghi log, kiểm tra quyền, cache hoặc thử lại thao tác. Chính vì advice nằm ngoài mã nghiệp vụ, việc đọc thân phương thức không đủ để xác nhận người gọi thực sự nhận đúng kết quả.

Một around advice chỉ có nhiệm vụ quan sát nhưng viết sai có thể gây những lỗi sau:

- Trả null thay vì object mà phương thức gốc trả về.
- Trả object sai kiểu, gây ClassCastException ở phía caller.
- Trả null cho phương thức có kiểu trả về nguyên thủy, dẫn đến lỗi unboxing.
- Nhầm phương thức void với phương thức có kiểu tham chiếu trả null.
- Nuốt checked exception hoặc unchecked exception, biến lỗi thành “thành công”.
- Bọc ngoại lệ gốc sai hợp đồng hoặc che ngoại lệ gốc bởi lỗi trong đoạn ghi metric.
- Gọi proceed() nhiều lần, nhân đôi cập nhật dữ liệu hoặc tác dụng phụ.
- Chỉ quan sát việc trả về một Future/CompletionStage rồi báo đã thành công dù tác vụ còn đang chạy.
- Không thực sự áp dụng lên join point mong đợi vì pointcut/weaving không phù hợp.

**Mục tiêu bài học:** kiểm thử đầy đủ return type, return value, nullability, exception propagation, số lượt gọi target và thứ tự advice. Cần tách unit test của logic advice với integration test trên đường thực thi được weave/intercept thật.

## 2. Hợp đồng tĩnh và hợp đồng động

Một interface Java có thể có dạng:

~~~java
interface Operations {
    String lookup(String key);
    int count();
    void refresh();
    String load(String key) throws java.io.IOException;
}
~~~

Đây là mảnh interface minh họa, chưa phải chương trình độc lập. Các phương thức đặt ra hai mặt hợp đồng:

**Hợp đồng tĩnh** gồm chữ ký, kiểu trả về, danh sách checked exceptions trong throws, khả năng nhìn thấy và giao diện mà caller biên dịch được.

**Hợp đồng động** gồm kết quả thật, null hợp lệ hay không, object identity khi cần, exception type/cause, thời điểm hoàn tất, số lượt gọi và tác dụng phụ.

Một aspect **chỉ quan sát** thường phải bảo toàn cả hai. Aspect **có chủ đích biến đổi** (ví dụ mapping ngoại lệ, cache hoặc retry) có thể được phép khác ở các điểm đã công bố, nhưng phải có hợp đồng riêng để kiểm thử.

| Đường thực thi gốc | Kết quả kỹ thuật advice có thể quan sát | Người gọi mong đợi khi aspect chỉ quan sát |
| --- | --- | --- |
| Trả object P | RETURN_VALUE | Nhận đúng object P |
| Trả null được phép | RETURN_NULL | Nhận null |
| Trả int 7 | RETURN_VALUE | Nhận giá trị nguyên thủy 7 |
| Void kết thúc bình thường | RETURN_VOID | Không có giá trị trả về; tác dụng phụ đúng |
| Ném IOException E | THROW_IOException | Nhận E theo hợp đồng checked |
| Ném IllegalStateException E | THROW_IllegalStateException | Nhận E, không bị nuốt |
| Ném AssertionError E | THROW_AssertionError | E được truyền, không đổi thành success |
| Before advice từ chối quyền | DENY | Target không được thực thi |

So sánh reference bằng == là một phép thử mạnh cho **aspect chỉ quan sát**: nó phát hiện việc thay object. Với một aspect được phép copy immutable value, hợp đồng có thể yêu cầu bằng giá trị thay vì cùng identity.

## 3. Các loại return type cần kiểm riêng

### 3.1. Kiểu tham chiếu

Ví dụ String, Product, List, mảng hoặc một subtype của interface. Nên kiểm:

1. Giá trị không bị đổi.
2. Object identity được giữ nếu hợp đồng yêu cầu.
3. Runtime class hoặc subtype hợp lệ không bị ép sai.
4. Kết quả mutable không bị advice sửa hoặc tiêu thụ trước caller.
5. Generic type và collection elements không bị biến dạng.

Nếu mọi phép thử dùng Object và không gọi qua interface thật, có thể bỏ sót ClassCastException chỉ phát sinh tại call site khi trả sai kiểu.

### 3.2. Kiểu nguyên thủy

Tất cả boolean, byte, short, char, int, long, float, double đều cần được chuyển tiếp đúng. Khi sử dụng reflection hoặc Java dynamic proxy, các giá trị nguyên thủy được **boxing** trong quá trình xử lý Object và **unboxing** khi về caller. Một handler trả null cho phương thức int không thể biến thành “không có dữ liệu” an toàn; kết quả là lỗi.

Cần test ít nhất một kiểu nguyên thủy và bổ sung trường hợp biên theo domain, ví dụ giá trị âm, zero, boolean false, NaN nếu thực sự liên quan. Với aspect chỉ quan sát, không được làm tròn hoặc tính toán lại mà không có chủ đích.

### 3.3. Void không phải nullable reference

Trong reflection, Method.invoke() trả null cho phương thức void, nhưng điều đó không biến void thành “phương thức trả null”. Nếu dùng null làm dấu hiệu duy nhất để ghi nhận, log/metric có thể báo sai.

~~~java
String outcome = method.getReturnType() == void.class
        ? "RETURN_VOID"
        : result == null ? "RETURN_NULL" : "RETURN_VALUE";
~~~

Đây là đoạn mã trong một InvocationHandler, cần các biến method và result phù hợp. Với void, phải xác nhận side effect xảy ra đúng số lần, không chỉ xác nhận lời gọi không ném exception.

### 3.4. Optional, collection, stream và async

- Optional.empty() là **một đối tượng không-null**, khác với method trả null.
- List hoặc mảng có thể chứa phần tử null dù chính container không null.
- Stream, Iterator và InputStream có thể chỉ dùng được một lần; advice đọc trước để ghi log có thể làm caller mất dữ liệu.
- Future/CompletionStage trả về bình thường là **handle của tác vụ**. Trạng thái cuối của tác vụ phải được quan sát tại completion, không suy ra từ lúc method vừa return.
- Generic và covariant return cần được test từ caller đúng chữ ký, tránh ép object bằng kiểu không tương thích.

## 4. Null: phải đọc theo hợp đồng chứ không đoán từ giá trị

Ba trạng thái quan trọng:

1. **Nullable contract:** tìm một bản ghi không thấy có thể trả null bình thường. Advice không được đổi thành exception chỉ vì null.
2. **Non-null contract:** trả null vi phạm lời hứa của method. Nếu aspect chịu trách nhiệm xác thực non-null thì đó là policy có chủ đích, phải test exception được quy định.
3. **Không có return value:** void không phải một trường hợp nullable.

Một phương thức có thể vừa trả giá trị bình thường vừa trả null ở hai lượt gọi khác nhau. Bộ test nên thử cả hai và chứng minh outcome khác nhau. Cũng nên kiểm tra null argument và null nằm bên trong container khi thuộc phạm vi pointcut.

Ví dụ lỗi thường gặp:

~~~java
Object result = proceed();
System.out.println(result.getClass().getSimpleName());
return result;
~~~

Đây là pseudocode dạng Java của around advice, không thể biên dịch độc lập vì proceed() chưa được định nghĩa. Nếu result là null, chính logic log gây NullPointerException, dù target hoàn tất bình thường. Việc ghi log phải kiểm null trước, và không tạo ra một loại lỗi mới ngoài contract.

## 5. Ngoại lệ: đúng kiểu, nguyên nhân và identity

Trong Java, phân biệt:

- **Checked exception:** ví dụ IOException. Caller được yêu cầu khai báo hoặc xử lý.
- **Unchecked exception:** RuntimeException và subclass như IllegalArgumentException, IllegalStateException.
- **Error:** đại diện những tình huống nghiêm trọng hoặc lỗi lập trình. Với advice chỉ quan sát, không nên nuốt hoặc tự đổi thành success.

Không đủ để test “có ném một exception”. Cần so sánh:

- Exception class.
- Tham chiếu tới cùng exception instance nếu không có chính sách translation.
- Message và cause khi chúng thuộc hợp đồng.
- Target có được thực thi hay không và số lần gọi.
- Exception xảy ra từ **target**, **advice** hay **cơ chế interceptor**.
- Đường lỗi không bị ghi nhầm success và không tự đổi giá trị trả về.

Một cấu trúc around advice đúng về mặt quan sát:

~~~text
try:
    result = proceedExactlyOnce()
    recordNormalCompletionBestEffort()
    return result
catch originalThrowable:
    recordFailureBestEffort(originalThrowable)
    throw originalThrowable
~~~

Đây là pseudocode, không phải một ngôn ngữ AOP cụ thể. Chú ý: nếu recordNormalCompletionBestEffort() có thể ném exception, catch tổng quát phía ngoài có thể nhầm đó là lỗi target. Cần tách vùng try/catch của telemetry để giữ đúng nguồn lỗi và không che exception gốc.

### Ngoại lệ khi dùng reflection

Method.invoke() bọc exception từ phương thức đích trong InvocationTargetException. Nếu proxy chỉ ném nguyên wrapper này thì người gọi không còn thấy IOException hoặc IllegalStateException như hợp đồng gốc. Handler nên unwrap:

~~~java
try {
    return method.invoke(target, args);
} catch (java.lang.reflect.InvocationTargetException ex) {
    throw ex.getCause();
}
~~~

Đây là mảnh code thuộc InvocationHandler.invoke(...) có throws Throwable. Với Java dynamic proxy, checked exception từ handler phải phù hợp với throws của interface. Nếu ném một checked exception không được khai báo, caller có thể gặp UndeclaredThrowableException. Không sử dụng một wrapper đó như bằng chứng rằng target ném sai: cần xác định lỗi được biến đổi ở tầng nào.

## 6. Ví dụ Java 17 hoàn chỉnh và có thể chạy

Ví dụ dùng dynamic proxy để **mô phỏng một around advice tại ranh giới interface**. Đây không phải toàn bộ ngữ nghĩa của mọi cơ chế weaving, nhưng giúp viết bộ test độc lập và xác minh chính xác những bất biến giá trị/ngoại lệ.

Cần kiểm: Token object giữ nguyên identity; int 7; null; String bình thường; void side effect; cùng instance IOException, IllegalStateException và AssertionError; chính xác tám lần gọi target; outcomes theo thứ tự.

Tạo file **AspectContractTest.java**:

~~~java
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class AspectContractTest {
    record Token(String id) {}

    interface Endpoint {
        Token token();
        int number();
        String nullable(boolean present);
        void touch();
        String checked() throws IOException;
        String unchecked();
        String fatal();
    }

    static final class Core implements Endpoint {
        final Token token = new Token("A");
        final IOException io = new IOException("storage");
        final IllegalStateException bad = new IllegalStateException("invalid");
        final AssertionError fatal = new AssertionError("crash");
        int calls;
        int touches;

        public Token token() { calls++; return token; }
        public int number() { calls++; return 7; }
        public String nullable(boolean present) {
            calls++;
            return present ? "ok" : null;
        }
        public void touch() { calls++; touches++; }
        public String checked() throws IOException { calls++; throw io; }
        public String unchecked() { calls++; throw bad; }
        public String fatal() { calls++; throw fatal; }
    }

    static final class Recorder implements InvocationHandler {
        final Endpoint target;
        final List<String> outcomes = new ArrayList<>();

        Recorder(Endpoint target) { this.target = target; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            try {
                Object result = method.invoke(target, args);
                String outcome = method.getReturnType() == void.class
                        ? "RETURN_VOID"
                        : result == null ? "RETURN_NULL" : "RETURN_VALUE";
                outcomes.add(method.getName() + ":" + outcome);
                return result;
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                outcomes.add(method.getName() + ":THROW_"
                        + cause.getClass().getSimpleName());
                throw cause;
            }
        }
    }

    static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError(name);
        System.out.println("PASS " + name);
    }

    public static void main(String[] args) throws IOException {
        Core core = new Core();
        Recorder recorder = new Recorder(core);
        Endpoint endpoint = (Endpoint) Proxy.newProxyInstance(
                Endpoint.class.getClassLoader(),
                new Class<?>[] { Endpoint.class }, recorder);

        check("reference identity", endpoint.token() == core.token);
        check("primitive", endpoint.number() == 7);
        check("null", endpoint.nullable(false) == null);
        check("normal value", "ok".equals(endpoint.nullable(true)));
        endpoint.touch();
        check("void side effect", core.touches == 1);

        try {
            endpoint.checked();
            throw new AssertionError("checked exception missing");
        } catch (IOException actual) {
            check("checked identity", actual == core.io);
        }
        try {
            endpoint.unchecked();
            throw new AssertionError("runtime exception missing");
        } catch (IllegalStateException actual) {
            check("unchecked identity", actual == core.bad);
        }
        try {
            endpoint.fatal();
            throw new AssertionError("Error missing");
        } catch (AssertionError actual) {
            check("Error identity", actual == core.fatal);
        }

        check("proceed once", core.calls == 8);
        check("outcomes", recorder.outcomes.equals(List.of(
                "token:RETURN_VALUE", "number:RETURN_VALUE",
                "nullable:RETURN_NULL", "nullable:RETURN_VALUE",
                "touch:RETURN_VOID", "checked:THROW_IOException",
                "unchecked:THROW_IllegalStateException",
                "fatal:THROW_AssertionError")));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 AspectContractTest.java
java AspectContractTest
~~~

**Stdout chính xác:**

~~~text
PASS reference identity
PASS primitive
PASS null
PASS normal value
PASS void side effect
PASS checked identity
PASS unchecked identity
PASS Error identity
PASS proceed once
PASS outcomes
~~~

Chương trình trên **đã được biên dịch bằng javac --release 17 và chạy thành công** trong môi trường JDK 21. Tất cả mười phép kiểm đều PASS. Việc dùng --release 17 buộc compiler kiểm tra API và cú pháp phù hợp với Java 17.

### Cách đọc các kiểm thử

1. **Reference:** method token() qua proxy trả đúng instance do Core tạo, chứng minh aspect quan sát không thay đổi object.
2. **Primitive:** number() vẫn trả int 7; reflection có thể dùng Integer trung gian nhưng caller vẫn thấy int đúng.
3. **Null:** nullable(false) trả null hợp lệ. Đường khác nullable(true) trả String "ok", nên recorder phải phân biệt.
4. **Void:** touch() không trả giá trị; tác dụng phụ touches tăng đúng một.
5. **Checked exception:** IOException gốc được ném lại, không thành InvocationTargetException hoặc RuntimeException khác.
6. **Unchecked exception:** IllegalStateException gốc không bị nuốt.
7. **Error:** AssertionError gốc không bị đổi thành exception khác.
8. **Proceed count:** Core nhận tám lời gọi, không bị retry hoặc gọi trùng từ recorder.
9. **Outcomes:** recorder ghi RETURN_VOID khác RETURN_NULL, và giữ ba trạng thái lỗi theo loại Throwable.

Bộ test chạy tuần tự, nên ArrayList trong recorder chỉ là cấu trúc đơn giản để assert dữ liệu. Với nhiều luồng, collector cần thiết kế thread-safe và chính sách giới hạn tài nguyên. Java dynamic proxy chỉ chặn lời gọi **qua proxy interface**; không được suy ra rằng method call trực tiếp vào Core hoặc self-invocation cũng bị advice chặn.

## 7. Test logic advice riêng và test điểm weave thật

Một bộ kiểm thử đầy đủ nên có hai tầng.

### 7.1. Unit test cơ chế advice

Tạo test double cho proceed/target có thể:

- Trả cùng một object cố định.
- Trả kiểu primitive.
- Trả null hợp lệ.
- Trả void kèm bộ đếm side effect.
- Ném đúng instance checked, unchecked hoặc Error.
- Theo dõi số lần gọi proceed.
- Có thể làm collector telemetry ném lỗi để đo ảnh hưởng đến target.
- Kiểm thử kết quả mutable, mảng, Optional, generic và stream nếu API cần.

Tầng này xác nhận logic branching và propagation, nhưng **chưa đảm bảo pointcut chọn đúng join point**.

### 7.2. Integration test qua interception/weaving thực

Khởi chạy app hoặc test fixture với cấu hình AOP thật để xác nhận:

1. Pointcut chọn đúng method call/execution, overload, annotation hoặc loại tham số.
2. Advice chạy theo thứ tự đã thiết kế.
3. Caller thật thấy return value/null/checked exception đúng contract.
4. Lời gọi trực tiếp, self-invocation, static context hoặc method không đi qua proxy được xử lý đúng phạm vi cơ chế.
5. Khi nhiều aspect cùng tham gia (cache, authorization, retry, transaction, tracing), giá trị và exception còn chính xác.
6. Bật/tắt riêng phần quan sát tùy chọn không thay đổi nghiệp vụ.

Đặc biệt, không test mỗi class lõi rồi khẳng định aspect đã áp dụng: phép thử đó không chứng minh weaving. Với code-style AspectJ, compile/weave bằng công cụ tương ứng và chạy trên những class đã weave thực sự; ví dụ Java dynamic proxy ở đây chỉ là một phép thử hợp đồng giá trị, không thay thế integration test của AspectJ.

## 8. Test collector lỗi mà không che kết quả nghiệp vụ

Xét advice ghi metric trong finally:

~~~text
start = monotonicClock.now()
try:
    return proceed()
finally:
    metrics.record(elapsed)
~~~

Đây là pseudocode. Nếu metrics.record(...) ném exception, lỗi ấy có thể **thay thế return value hoặc exception gốc**. Nếu metric chỉ best-effort, phải cô lập lỗi ghi metric để giữ kết quả nghiệp vụ.

| Target | Collector | Mong đợi của aspect best-effort |
| --- | --- | --- |
| Trả object P | Thành công | Trả P |
| Trả object P | Ném lỗi | Vẫn trả P |
| Trả null | Ném lỗi | Vẫn trả null |
| Trả void | Ném lỗi | Vẫn hoàn tất void |
| Ném IOException E | Thành công | Ném E |
| Ném IOException E | Ném lỗi | Vẫn ném đúng E |
| Ném Error E | Ném lỗi | Vẫn truyền đúng Error gốc |

Cần kiểm source của lỗi và số lần proceed. Đừng đơn giản catch toàn bộ Throwable rồi return null. Nếu chức năng đang kiểm thử là **audit bắt buộc** theo domain chứ không phải metric best-effort, chính sách lỗi có thể yêu cầu thất bại; phải ghi rõ hợp đồng riêng đó.

### Zero, one và multiple proceed

- Với aspect quan sát và target thực thi bình thường: thường phải gọi proceed đúng một lần.
- Với advice từ chối authorization: phải gọi proceed **zero** lần ở nhánh bị từ chối.
- Với retry policy có chủ đích: có thể gọi nhiều lần **chỉ khi** hợp đồng, idempotency và giới hạn retry cho phép.
- Với cache hit: có thể không gọi target; khi đó test phải xác nhận trả đúng kết quả cache theo ngữ cảnh.

Vì vậy, “proceed once” là invariant của **loại aspect trong ví dụ**, không phải định luật của mọi around advice.

## 9. Exception từ advice, target và proxy khác nhau ra sao?

Để chẩn đoán đúng, phân biệt:

1. Exception từ before/around advice trước proceed: target chưa được gọi.
2. Exception từ target: cần bảo toàn kiểu, cause và identity theo hợp đồng.
3. Exception từ after-returning/after/finally: có thể che kết quả gốc.
4. Exception từ reflection/proxy: có thể là InvocationTargetException, UndeclaredThrowableException, IllegalAccessException, ClassCastException hoặc lỗi unboxing tùy cách triển khai.

Một phép kiểm chỉ có assertThrows(Exception.class) quá yếu: nó có thể PASS khi target ném IOException nhưng interceptor trả ClassCastException. Nên assert đúng lớp ngoại lệ, thông điệp/cause, số lượt target thực thi, và nếu cần cùng instance exception.

Nếu aspect **cố ý chuyển đổi** ngoại lệ, phải có bảng mapping được xác nhận, giữ nguyên nhân cần thiết và test ngoại lệ không thuộc mapping không bị thay đổi.

## 10. Trường hợp bất đồng bộ: return không phải completion

Với CompletionStage<T>, phương thức có thể trả handle thành công ngay trước khi tác vụ thật hoàn tất.

Bộ test phải tách:

**Return-boundary tests**
- Advice trả đúng handle hoặc wrapper được phép theo hợp đồng.
- Không tự join()/get() làm blocking.
- Không tạo coupling cancellation bất ngờ giữa nhiều caller.
- Không coi trả Future thành công là operation success cuối.

**Completion-boundary tests**
- Stage hoàn tất với giá trị non-null.
- Stage hoàn tất bình thường với null.
- Stage hoàn tất exceptional, giữ nguyên nhân gốc phù hợp.
- Cancellation được ghi nhận khác với success và general failure theo hợp đồng.
- Callback quan sát không biến stage thành thất bại do lỗi ghi metric.

Với Future.get() hoặc join(), ngoại lệ wrapper xuất hiện ở API đợi có thể khác exception bên trong task; cần xác minh nguyên nhân gốc. Đường nhận kết quả của stream, iterator hoặc publisher cũng cần test tại thời điểm tiêu thụ, không chỉ thời điểm method return.

## 11. Bảng kiểm thử hồi quy tối thiểu

| Nhóm | Case | Bất biến chính |
| --- | --- | --- |
| Reference | Trả object | Type, giá trị, identity nếu cần |
| Reference | Trả subtype | Không bị cast sai |
| Primitive | int/boolean | Giá trị/kiểu chính xác |
| Void | Side effect | Gọi đúng số lần, hoàn tất bình thường |
| Nullable | Trả null | Không chuyển thành lỗi hay object giả |
| Nullable | Trả non-null | Không đổi giá trị |
| Checked | IOException | Lỗi gốc theo hợp đồng, không wrap ngoài ý muốn |
| Unchecked | IllegalStateException | Lỗi không bị nuốt |
| Error | AssertionError | Không đổi thành success |
| Recorder | Ghi metric lỗi | Không che return/exception nếu best-effort |
| Pointcut | Call qua advice | Advice có chạy đúng ranh giới |
| Pointcut | Không match | Không làm thay đổi code ngoài phạm vi |
| Composition | Nhiều aspect | Thứ tự và kết quả theo chính sách |
| Async | Future trả trước completion | Không ghi nhận success cuối quá sớm |
| Async | Completion null/error/cancel | Trạng thái được phân biệt |
| Concurrency | Nhiều luồng | Collector an toàn, không race/đếm sai |

### Những lỗi phổ biến

- Kiểm một String return rồi kết luận tương thích với primitive/void.
- Coi result == null luôn là lỗi hoặc luôn là void.
- Dùng result.getClass() khi chưa kiểm null.
- Dùng Method.invoke() mà không unwrap InvocationTargetException.
- Nuốt RuntimeException hoặc Error để giữ dashboard “xanh”.
- Assert chỉ exception message nhưng không kiểm type/cause.
- Metric sink ném lỗi trong finally và che IOException gốc.
- Tự động retry làm tăng số tác dụng phụ mà không có idempotency.
- Test trực tiếp class lõi nhưng không test đường gọi đã weave.
- Không kiểm completion thực tế của tác vụ async.

## 12. Checklist áp dụng cho dự án

1. Ghi rõ những kiểu return mà pointcut có thể chọn, gồm primitive, void và nullable reference.
2. Xác định null có hợp lệ không cho từng phương thức, không suy từ object type chung.
3. Xác định advice chỉ quan sát hay có chủ đích biến đổi kết quả.
4. Test đúng giá trị, identity nếu phù hợp và side effect.
5. Test checked, unchecked, Error; so sánh type và cause/identity cần thiết.
6. Khi dùng reflection, kiểm quy tắc unwrap và throws của interface.
7. Test lỗi do chính logic advice gây ra, nhất là collector trong finally.
8. Kiểm số lần proceed trên từng nhánh.
9. Kiểm pointcut/weaving thật bằng integration test, không chỉ unit test.
10. Test async ở cả method-return và task-completion boundaries.
11. Đưa các test dạng negative path vào CI và chạy lại khi đổi method signature hoặc thứ tự aspect.
12. Dùng trace/log tối thiểu để chẩn đoán mà không tiết lộ tham số nhạy cảm.

**Kết luận:** Aspect được viết đúng không chỉ chạy ở đúng join point mà còn phải **tôn trọng hợp đồng của phương thức gốc**. Reference, primitive, void, null bình thường, checked/unchecked exceptions, Error và async completion đều cần những phép thử riêng. Kiểm thử logic advice kết hợp kiểm thử weaving thực tế là cách bảo vệ hiệu quả trước những lỗi khó thấy do AOP tạo ra.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-testing-005">Kiểm thử thứ tự nhiều aspect và lời gọi lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử thứ tự nhiều aspect và lời gọi lồng nhau

## 1. Vì sao kiểm thử thứ tự là cần thiết?

Trong **Aspect-Oriented Programming (AOP)**, một thao tác nghiệp vụ có thể đồng thời thuộc pointcut của nhiều aspect: phân quyền, transaction, ghi audit, đo hiệu năng, retry hoặc quản lý ngữ cảnh. Mỗi advice có thể chạy trước, sau hay bao quanh lời gọi. Khi một phương thức còn gọi tiếp một phương thức khác cũng được instrument, quá trình thực thi tạo thành **nhiều lớp aspect lồng nhau**.

Thứ tự không chỉ làm thay đổi log. Nó quyết định liệu kiểm tra quyền có xảy ra trước side effect, retry có tạo một transaction mới trên mỗi attempt hay không, metric có đo cả overhead của các advice khác, và liệu cleanup vẫn thực thi đúng khi có ngoại lệ. Một chương trình có thể trả kết quả nghiệp vụ đúng nhưng thứ tự aspect sai, gây lỗi khó phát hiện.

Bài này cung cấp phương pháp **kiểm thử cụ thể** thay vì chỉ giải thích lý thuyết:

- Phân biệt thứ tự **entry**, **body** và **exit**, đặc biệt với `around`.
- Xác định quan hệ ưu tiên của các aspect bằng một khai báo rõ ràng.
- Xây dựng event trace để chứng minh cấu trúc `outer() -> inner()`.
- Test cả nhánh trả về bình thường và nhánh ném ngoại lệ.
- Phân biệt **join point khác nhau**, **aspect khác nhau** và **lời gọi trùng do cấu hình sai**.
- Kiểm tra khác biệt giữa weaving tại execution join point và interception chỉ qua proxy.
- Mở rộng sang recursion, retry, short-circuit, các tác vụ bất đồng bộ và concurrent.
- Viết kiểm thử Java 17 bằng **AspectJ code-style**, biên dịch/weave/chạy được với output đã xác minh.

Nội dung tập trung vào kiến thức AOP nói chung. AspectJ được dùng làm công cụ minh họa có weaving thực, không ngầm áp đặt các quy tắc của nó cho mọi công cụ AOP khác.

## 2. Từ pointcut đến chuỗi thực thi

Những thành phần cần biết:

| Thành phần | Vai trò khi kiểm thử |
|---|---|
| **Join point** | Điểm thực thi có thể bị chặn; ví dụ việc thực thi `Business.outer(String)` |
| **Pointcut** | Quy tắc chọn một hay nhiều join point |
| **Advice** | Hành vi bổ sung chạy tại join point đã chọn |
| **Aspect** | Đơn vị chứa pointcut, advice và state liên quan |
| **Precedence** | Quan hệ ưu tiên khi nhiều aspect cùng áp dụng |
| **Proceed** | Hành động gọi tiếp đoạn thực thi mà `around` bao quanh |
| **Weaving** | Tích hợp advice vào bytecode để nó thật sự thực thi |
| **Nested invocation** | Một method tạo join point mới trong body của method đang được chặn |

Cần kiểm tra **hai điều khác nhau**: (1) pointcut chọn đúng method; (2) lời gọi thật thực sự đi qua advice đúng thứ tự. Một kiểm thử chỉ dùng reflection để khẳng định pointcut match không thay thế integration test trên bytecode đã weave.

## 3. Mô hình bao lồng của hai around advice

Giả sử aspect A có precedence cao hơn B, cả hai cùng match execution của method `work()` và đều có cấu trúc:

```text
around:
    record enter
    try:
        return proceed()
    finally:
        record exit
```

Khi đó chuỗi kỳ vọng:

```text
A.enter
B.enter
work.body
B.exit
A.exit
```

Lý do: A nằm **ngoài** B. A vào trước, gọi `proceed` để đi vào B; B gọi `proceed` để đến body. Khi body kết thúc, stack tháo từ trong ra ngoài: B thoát trước A.

Sơ đồ:

```text
caller
  └─ A around
       └─ B around
            └─ target method
```

Vì vậy, diễn đạt “aspect ưu tiên cao chạy trước” chỉ chính xác cho phần **đi vào**; không có nghĩa toàn bộ phần sau `proceed()` của nó phải chạy trước phần sau `proceed()` của aspect ít ưu tiên hơn.

Với exception, `finally` vẫn chạy:

```text
A.enter
B.enter
work.body throws
B.exit
A.exit
caller catches error
```

Nếu advice ghi audit hoặc trả lại lock/scope trong `finally`, test thứ tự **exit** quan trọng không kém thứ tự **entry**.

## 4. Lồng nhiều join point: outer gọi inner

Giả sử `outer()` gọi `inner()`; **cả hai đều thuộc pointcut của A và B**. Chuỗi phải tạo thêm một cặp advice lồng bên trong body của outer:

```text
A.enter outer
B.enter outer
  body outer starts
    A.enter inner
    B.enter inner
      body inner
    B.exit inner
    A.exit inner
  body outer ends
B.exit outer
A.exit outer
```

Đây là **hai execution join point khác nhau**, nên có hai lượt kích hoạt advice. Đừng kết luận advice bị chạy “hai lần sai” chỉ vì log chứa hai event cùng tên aspect.

Ngược lại, nếu chỉ có event cho outer dù pointcut dự kiến chọn cả inner, cần điều tra:

- Pointcut có thực sự match `inner` không?
- Bytecode chứa **execution của inner** có được weave không?
- Ứng dụng đang dùng proxy interception thay vì weaving execution?
- Lời gọi nội bộ đi thẳng vào object gốc, bỏ qua proxy?
- Method có modifier/overload/annotation khác với điều kiện chọn?
- Dữ liệu quan sát có bị filter/drop ở collector không?

Điểm mấu chốt: **thứ tự tại một join point** khác với **cấu trúc của những join point lồng nhau**. Bộ kiểm thử phải xác minh cả hai.

## 5. Khai báo thứ tự rõ ràng

Trong ví dụ AspectJ code-style, khai báo:

```java
public aspect OrderPolicy {
    declare precedence: GuardAspect, TraceAspect;
}
```

Điều này đặt `GuardAspect` ưu tiên cao hơn `TraceAspect`. Khi cùng match một method execution, `GuardAspect` là lớp bao phía ngoài `TraceAspect`. Các aspect vẫn cần được weave vào target phù hợp.

Không nên dựa vào:

- Tên file đứng trước theo alphabet.
- Thứ tự file trong lệnh compiler.
- Thứ tự import, thứ tự source code hoặc thứ tự load class.
- Một kết quả vô tình ổn định của phiên bản runtime mà không có hợp đồng thứ tự.
- Thứ tự callback từ logging backend có queue bất đồng bộ.

Nếu công cụ hỗ trợ quan hệ precedence riêng hoặc có precedence theo kế thừa, phải đọc và kiểm chứng semantics của chính cơ chế đó. Không phải mọi công cụ AOP có cú pháp `declare precedence` giống AspectJ.

Trước khi chọn thứ tự, hãy lập ma trận concern. Ví dụ một retry ở ngoài transaction thường có thể tạo transaction riêng cho mỗi attempt; transaction ở ngoài retry có thể giữ attempt sau trong cùng ranh giới đã hỏng. Phân quyền, timeout, audit và metric cần quyết định trước/sau theo yêu cầu nghiệp vụ, không theo sở thích đặt tên class.

## 6. Thiết kế kiểm thử bằng event trace

Một kiểm thử đáng tin tạo **chuỗi sự kiện dự kiến** rồi assert **bằng nhau toàn bộ**, không chỉ kiểm tra một vài substring.

Schema dùng trong test có thể gồm:

- Mã aspect hoặc loại advice: `guard`, `trace`.
- Giai đoạn: enter, exit, error.
- Tên semantic operation hoặc signature: outer/inner.
- Invocation ID và parent ID nếu nhiều lời gọi cùng tên.
- Thread/task correlation khi test concurrent/async.

Ở demo đơn luồng, string event ngắn là đủ: `guard+outer`, `trace+inner`, `trace-inner`. Ở hệ thống thật, không lấy raw argument để tạo log thử nghiệm, nhất là token/dữ liệu nhạy cảm.

Những assertion quan trọng:

1. **Coverage**: có event cho cả outer và inner.
2. **Precedence**: `guard+` xảy ra trước `trace+` của **cùng** join point.
3. **LIFO**: `trace-` xảy ra trước `guard-` của **cùng** join point.
4. **Nested order**: mọi event inner nằm bên trong khoảng thời gian outer đang thực thi.
5. **Target count**: body của từng method chạy đúng số lần cần thiết.
6. **Outcome**: giá trị trả về và ngoại lệ không bị advice thay đổi ngoài hợp đồng.
7. **Negative coverage**: method không thuộc pointcut không phát các event đó.
8. **Cleanup**: dù exception, mọi scope đã mở vẫn được đóng theo thứ tự.

Test `equals(expected)` giúp phát hiện cả **thiếu**, **thừa**, **sai tên**, **đảo thứ tự** và **log đôi**.

## 7. Ví dụ Java 17 + AspectJ chạy được

Bộ ví dụ gồm bốn file. Kết quả đã được xác minh bằng **AspectJ 1.9.24**, compiler `ajc` với mức ngôn ngữ Java 17.

- `Demo.java`: hai method lồng nhau, recorder và hai assertion thành công/lỗi.
- `GuardAspect.aj`: around advice bao cả outer và inner.
- `TraceAspect.aj`: around advice có cùng pointcut để kiểm thử thứ tự.
- `OrderPolicy.aj`: khai báo precedence.

Tên `Guard` chỉ biểu diễn thứ tự trong bài học, không thay thế một bộ kiểm tra quyền thật. Trong hệ thống production, logic guard phải tính allow/deny theo policy và bảo vệ side effect một cách độc lập.

### 7.1. Demo.java

```java
import java.util.ArrayList;
import java.util.List;

public class Demo {
    static final List<String> EVENTS = new ArrayList<>();

    static final class Business {
        String outer(String id) {
            EVENTS.add("body outer:start");
            String nested = inner(id);
            EVENTS.add("body outer:end");
            return "outer:" + nested;
        }

        String inner(String id) {
            EVENTS.add("body inner");
            if (id.equals("boom")) throw new IllegalStateException("planned");
            return "inner:" + id;
        }
    }

    static void verify(String scenario, List<String> expected) {
        if (!EVENTS.equals(expected)) {
            throw new AssertionError(scenario + " expected=" + expected + " actual=" + EVENTS);
        }
        System.out.println(scenario + "=PASS");
        EVENTS.forEach(System.out::println);
        EVENTS.clear();
    }

    public static void main(String[] args) {
        Business service = new Business();
        String value = service.outer("A");
        EVENTS.add("caller:" + value);
        verify("success", List.of(
                "guard+outer", "trace+outer",
                "body outer:start",
                "guard+inner", "trace+inner",
                "body inner",
                "trace-inner", "guard-inner",
                "body outer:end",
                "trace-outer", "guard-outer",
                "caller:outer:inner:A"));

        try {
            service.outer("boom");
            throw new AssertionError("The method should fail");
        } catch (IllegalStateException expected) {
            EVENTS.add("caller:caught");
        }
        verify("failure", List.of(
                "guard+outer", "trace+outer",
                "body outer:start",
                "guard+inner", "trace+inner",
                "body inner",
                "trace-inner", "guard-inner",
                "trace-outer", "guard-outer",
                "caller:caught"));
    }
}
```

Giải thích:

- `Demo.EVENTS` là `ArrayList` chỉ phù hợp cho bài test **đơn luồng tuần tự**. Nếu ứng dụng chạy song song, cần collector thread-safe và correlation per invocation.
- `Business.outer` ghi `body outer:start`, gọi `inner`, rồi ghi `body outer:end` khi inner trả bình thường.
- `inner("boom")` ném `IllegalStateException("planned")` sau event `body inner`.
- `verify` so sánh **toàn bộ chuỗi event** với `List.of(...)`. Nếu sai, nó ném `AssertionError`; chỉ khi đúng mới in `PASS` và từng event.
- Test chạy success trước, rồi exception. Không có `sleep` hoặc phụ thuộc đồng hồ để quyết định thứ tự.

### 7.2. GuardAspect.aj

```java
public aspect GuardAspect {
    pointcut operation():
        execution(String Demo.Business.outer(String))
        || execution(String Demo.Business.inner(String));

    String around(): operation() {
        String name = thisJoinPointStaticPart.getSignature().getName();
        Demo.EVENTS.add("guard+" + name);
        try {
            return proceed();
        } finally {
            Demo.EVENTS.add("guard-" + name);
        }
    }
}
```

Pointcut chọn execution của chính hai phương thức có signature `String outer(String)` và `String inner(String)`. `thisJoinPointStaticPart` cung cấp chữ ký join point, từ đó lấy tên method để gắn nhãn event.

Advice có return type `String` phù hợp target, gọi `proceed()` đúng một lần, và ghi event thoát trong `finally`. Vì vậy event `guard-` xuất hiện cả khi body trả về lẫn khi ném lỗi.

### 7.3. TraceAspect.aj

```java
public aspect TraceAspect {
    pointcut operation():
        execution(String Demo.Business.outer(String))
        || execution(String Demo.Business.inner(String));

    String around(): operation() {
        String name = thisJoinPointStaticPart.getSignature().getName();
        Demo.EVENTS.add("trace+" + name);
        try {
            return proceed();
        } finally {
            Demo.EVENTS.add("trace-" + name);
        }
    }
}
```

Cùng pointcut, chỉ khác nhãn sự kiện. Đây là cách cô lập nguyên nhân khi test: mọi khác biệt về order đến từ **precedence/weaving/nesting**, không do hai aspect có logic nghiệp vụ khác nhau.

### 7.4. OrderPolicy.aj

```java
public aspect OrderPolicy {
    declare precedence: GuardAspect, TraceAspect;
}
```

File này làm **quan hệ ưu tiên thành một hợp đồng tường minh**. Nếu bỏ nó, test không còn được bảo đảm bởi khai báo precedence mà bài học muốn chứng minh.

## 8. Biên dịch, weaving và stdout đã kiểm chứng

Có thể lấy bộ AspectJ compiler và runtime qua Maven:

```bash
mvn -B dependency:get -Dartifact=org.aspectj:aspectjtools:1.9.24 -Dtransitive=false
mvn -B dependency:get -Dartifact=org.aspectj:aspectjrt:1.9.24 -Dtransitive=false
```

Lưu bốn file trong cùng một thư mục. Trên **Windows PowerShell**:

```powershell
$repo = Join-Path $HOME ".m2/repository/org/aspectj"
$tools = Join-Path $repo "aspectjtools/1.9.24/aspectjtools-1.9.24.jar"
$rt = Join-Path $repo "aspectjrt/1.9.24/aspectjrt-1.9.24.jar"

New-Item -ItemType Directory -Force out | Out-Null
java -cp $tools org.aspectj.tools.ajc.Main -17 -classpath $rt -d out Demo.java GuardAspect.aj TraceAspect.aj OrderPolicy.aj
if ($LASTEXITCODE -ne 0) { throw "AspectJ compilation failed" }

java -cp "out;$rt" Demo
if ($LASTEXITCODE -ne 0) { throw "Test failed" }
```

Các câu lệnh dùng `-17` để chọn mức Java 17 và `-d out` để tạo bytecode đã weave. Trên Linux/macOS, dấu phân tách classpath khi chạy ứng dụng là `:`:

```bash
REPO="$HOME/.m2/repository/org/aspectj"
TOOLS="$REPO/aspectjtools/1.9.24/aspectjtools-1.9.24.jar"
RT="$REPO/aspectjrt/1.9.24/aspectjrt-1.9.24.jar"

mkdir -p out
java -cp "$TOOLS" org.aspectj.tools.ajc.Main -17 -classpath "$RT" -d out \
    Demo.java GuardAspect.aj TraceAspect.aj OrderPolicy.aj
java -cp "out:$RT" Demo
```

**Stdout chính xác đã chạy trên JVM sau weaving:**

```text
success=PASS
guard+outer
trace+outer
body outer:start
guard+inner
trace+inner
body inner
trace-inner
guard-inner
body outer:end
trace-outer
guard-outer
caller:outer:inner:A
failure=PASS
guard+outer
trace+outer
body outer:start
guard+inner
trace+inner
body inner
trace-inner
guard-inner
trace-outer
guard-outer
caller:caught
```

Nếu test không được weave, không match pointcut hoặc precedence sai, `verify` sẽ báo lỗi; câu `success=PASS` hoặc `failure=PASS` sẽ không được in thành công tương ứng.

## 9. Phân tích nhánh success: cấu trúc stack

`outer("A")` tạo trình tự:

1. Guard ở outer ghi `guard+outer`.
2. Guard gọi `proceed`, Trace ở outer ghi `trace+outer`.
3. Trace gọi `proceed`, body outer ghi `body outer:start`.
4. Outer gọi inner trên cùng object. Vì đây là **execution pointcut đã được weave**, inner lại tạo Guard → Trace.
5. Body inner chạy và trả `inner:A`; Trace inner ghi `trace-inner`, Guard inner ghi `guard-inner`.
6. Body outer tiếp tục, ghi `body outer:end`, trả `outer:inner:A`.
7. Trace outer rời scope trước, Guard outer rời sau.
8. Caller nhận return value và ghi `caller:outer:inner:A`.

Chuỗi push/pop:

```text
push Guard(outer)
    push Trace(outer)
        outer body begins
            push Guard(inner)
                push Trace(inner)
                    inner body
                pop Trace(inner)
            pop Guard(inner)
        outer body completes
    pop Trace(outer)
pop Guard(outer)
caller receives value
```

Mỗi `pop` phải tương ứng một `push` **cùng invocation**. Với recursion, một method name có thể xuất hiện ở nhiều stack depth; nên gắn parent/child invocation ID khi cần kiểm tra chính xác.

## 10. Phân tích nhánh failure: cleanup và propagate

Với `outer("boom")`, body inner ghi event rồi ném `IllegalStateException`. Dòng `body outer:end` **không** được thực thi. Tuy vậy `finally` của Trace inner, Guard inner, Trace outer và Guard outer vẫn chạy theo LIFO; caller bắt được exception gốc và ghi `caller:caught`.

Điều này chứng minh ba khía cạnh khác nhau:

- **Advice execution:** đúng các scope đã đi vào đều được rời.
- **Business execution:** body outer không tiếp tục sau inner thất bại.
- **Error semantics:** exception lan đến caller, không bị advice nuốt hoặc đổi thành kết quả thành công giả.

Đừng chỉ assert `assertThrows` mà bỏ qua những event cleanup. Nếu advice quản lý resource, lock, trace span hoặc transaction scope, sai thứ tự cleanup có thể gây rò tài nguyên hoặc context của request trước.

Bản thân cleanup cũng có thể ném exception. Nếu một lời gọi logger trong `finally` ném lỗi, nó có nguy cơ che mất lỗi nghiệp vụ. Cơ chế telemetry cần có chính sách cô lập lỗi, nhưng không được che đậy lỗi bắt buộc của giao dịch nếu hợp đồng yêu cầu xử lý nghiêm ngặt.

## 11. Before, after returning, after throwing và around

Không nên coi các loại advice là những callback giống nhau chỉ khác tên.

| Kiểu advice | Thời điểm | Test phù hợp |
|---|---|---|
| `before` | Trước khi join point tiếp tục | Có chạy đúng vị trí, không thực thi side effect khi policy deny? |
| `after returning` | Chỉ sau kết thúc bình thường | Không chạy khi target ném exception |
| `after throwing` | Khi ném exception phù hợp | Bắt đúng loại lỗi và giữ error contract |
| `after` | Sau join point theo semantics của nó | Cleanup cả success và failure |
| `around` | Bao lời gọi, có `proceed()` | Nesting entry/exit, short-circuit, retry, return value |

Ở các aspect khác nhau, precedence giúp quy định mô hình bao lồng. Với **nhiều advice bên trong cùng một aspect**, quy tắc ordering cụ thể còn phụ thuộc loại advice và cú pháp/công cụ; không suy ra máy móc từ thứ tự dòng source. Khi thứ tự có ý nghĩa, test phải dùng **đúng tổ hợp advice thực tế**.

Ví dụ chính dùng hai around advice vì rõ mô hình stack, không phải vì mọi concern nhất thiết cần around. Với logging đơn giản, before/after có thể đủ; với thay đổi luồng điều khiển như block/retry/cache hit, around có hợp đồng rộng hơn và rủi ro cao hơn.

## 12. Execution weaving, call weaving và self-invocation

Đây là khác biệt quan trọng nhất khi kiểm thử nested calls.

`outer` gọi `inner` trong cùng instance. **AspectJ execution weaving** sửa bytecode tại execution của inner, vì vậy inner vẫn được instrument mà không cần đi qua một proxy bên ngoài.

Các trường hợp khác:

- **Call join point:** được quan sát tại chỗ gọi method; phải weave caller phù hợp. Nếu caller chuyển module/classloader, pointcut `call` có thể bỏ sót.
- **Execution join point:** được quan sát khi thân method được thực thi, miễn target class đã được weave.
- **Proxy interception:** nhiều cơ chế chỉ chặn những lời gọi đi qua proxy. `this.inner()` gọi trực tiếp từ một method của target có thể bỏ qua proxy.
- **Không được weave:** annotation/metadata có tồn tại cũng không biến thành interception tự động.

Do đó, một test chỉ gọi `methodPointcut.matches(inner)` không đủ. Cần chạy thật `outer()` qua đường vào giống ứng dụng và xác nhận event inner.

Nếu dùng cả `call` và `execution` trên cùng logical operation, có thể thấy hai vòng advice do **hai join point khác loại**. Phân biệt chúng trong test bằng kind hoặc signature, đừng quy tất cả thành “aspect chạy trùng”.

## 13. Negative test, refactoring và coverage

Ngoài test hai method được chọn, cần test method **không được chọn**. Ví dụ thêm `Business.health()` không thuộc pointcut và kiểm tra rằng lời gọi đó không sinh `guard+health` hoặc `trace+health`. Đó là một bước mở rộng test, không phải phần đã thêm trong chương trình mẫu.

Các trường hợp cần xem xét:

1. Đổi tên `inner` nhưng quên sửa pointcut.
2. Chuyển `Business` sang package khác; selector gắn tên type cũ không match.
3. Thêm overload cùng tên với ý nghĩa khác; wildcard `inner(..)` chặn thừa.
4. Đổi implementation hoặc thay đường gọi; advice không còn được weave.
5. Dùng interface/annotation metadata nhưng weaver đọc method implementation.
6. Biên dịch đồng thời hai phiên bản aspect làm cùng một concern, gây event trùng.
7. Một aspect tự gọi helper cũng thuộc pointcut rộng, tạo recursion ngoài ý muốn.

Kiểm thử coverage gồm **positive**, **negative** và **integration test trên đường gọi thật**. Khi thay đổi pointcut hoặc refactor cấu trúc code, bộ test phải phát hiện cả **false negative** lẫn **false positive**.

## 14. Short-circuit: around không gọi proceed

Một around advice có thể trả kết quả trực tiếp hoặc ném lỗi mà không gọi `proceed()`:

```text
OuterAdvice enters
   -> InnerAdvice enters
      -> policy denies OR cache hit
      -> returns/throws without proceed
   <- InnerAdvice exits
<- OuterAdvice exits
```

Khi một advice short-circuit, **những advice và body ở bên trong nó không được chạy**. Vì vậy kiểm thử order phải kiểm tra cả **số lần body được gọi** và side effects, không chỉ kiểm tra entry/exit.

Ví dụ khi deny, yêu cầu cơ bản là target không thực thi; nếu cache hit, phải chứng minh dữ liệu trả lại thuộc đúng actor/tenant và hợp đồng cache. Một result từ cache mà đúng order nhưng sai quyền vẫn là lỗi nghiêm trọng.

Nếu advice chỉ log rồi vẫn gọi `proceed()` trên nhánh deny, test đếm số body invocation sẽ phát hiện vấn đề mà test dựa trên log có thể bỏ sót.

## 15. Retry và nhiều lượt thực thi

Một logical operation có thể tạo nhiều attempts nếu một aspect retry có quyền gọi tiếp nhiều lần:

```text
Retry -> Trace -> Transaction -> Business
  attempt 1
  attempt 2
```

So với:

```text
Trace -> Retry -> Transaction -> Business
  Trace đo cả logical operation và các attempts bên trong
```

Số event của Trace và Transaction thay đổi theo vị trí của chúng trong stack. Thử lại có thể làm `inner` chạy nhiều lần, nên việc thấy lặp nhiều event không luôn là cấu hình trùng.

Nhưng **retry không bảo đảm thao tác an toàn khi lặp**. Với lệnh ghi có side effect, cần idempotency key và cơ chế chống duplicate/đối soát. Một bài test đủ tốt kiểm tra cả logical operation ID, attempt number, số lượt body, trạng thái cuối và số hiệu ứng thực.

Tránh nested retries ở nhiều tầng vô tình nhân số attempts. Với lỗi sau commit hoặc timeout mơ hồ, không giả định rằng attempt trước chưa làm gì.

## 16. Lời gọi đệ quy: cùng method nhưng nhiều depth

Nếu `inner` gọi lại chính nó, mỗi execution phù hợp có thể tạo thêm cặp advice:

```text
A.enter depth1
  B.enter depth1
    A.enter depth2
      B.enter depth2
        body depth2
      B.exit depth2
    A.exit depth2
  B.exit depth1
A.exit depth1
```

Chỉ ghi tên method không đủ phân biệt các cấp. Khi kiểm thử recursion hoặc cây lời gọi lớn, bổ sung:

- `invocationId`, `parentInvocationId`.
- `depth` được quản lý bằng push/pop và `try/finally`.
- Giới hạn recursion và base case rõ.
- Bộ lọc để aspect không tự instrument các helper ghi log của chính nó.

Aspect singleton có field mutable dùng chung không tự tạo isolation theo request. `ThreadLocal` có thể chứa state trong luồng đồng bộ nhưng cần restore khi rời scope; nó không tự lan qua executor và không thay thế parent/child IDs trong thực thi bất đồng bộ.

## 17. Async và concurrency: không thể dùng một global order

Một method trả `CompletableFuture` có thể kết thúc **execution của method** khi trả handle, trong khi tác vụ nghiệp vụ chạy sau đó trên thread khác. Một around advice bao method có thể in `exit` trước lúc future hoàn thành. Vì vậy phải phân biệt:

- `method_returned_handle`.
- `async_job_started`.
- `async_job_completed` hoặc `async_job_failed`.
- Join point tại callback/worker nếu được instrument riêng.

Kiểm thử nên điều khiển completion bằng future/scheduler giả, không dùng `Thread.sleep` để hy vọng một thứ tự xảy ra. Context tracing/security cần được truyền và restore khi chuyển luồng, nếu có.

Với nhiều request concurrent, log toàn cục có thể xen kẽ:

```text
guard+outer requestA
guard+outer requestB
trace+outer requestA
trace+outer requestB
...
```

Không assert một chuỗi global duy nhất nếu ứng dụng không bảo đảm thứ tự giữa các request. Thay vào đó:

1. Dùng event collector thread-safe hoặc buffer theo request.
2. Gắn request ID/invocation ID cho mỗi event.
3. Assert quan hệ **happens-before** hoặc stack nesting **trong cùng logical invocation**.
4. Sử dụng latch/barrier để chủ động dựng interleaving cạnh tranh.
5. Không suy ra thứ tự advice chỉ từ timestamp hoặc thứ tự flush trong logging queue.

Ví dụ `ArrayList` của bài này **chỉ là test đơn luồng**; không sử dụng nó như một audit logger concurrent trong production.

## 18. Lỗi phổ biến và hướng kiểm tra

| Hiện tượng | Nguyên nhân có thể | Cách phát hiện hoặc sửa |
|---|---|---|
| Thiếu event inner | Pointcut không match, target chưa weave, self-invocation qua raw object | Integration test outer→inner |
| Event exit ngược thứ tự mong đợi | Hiểu sai nesting/LIFO | Vẽ stack và test `finally` |
| Cùng tên aspect xuất hiện hai lần | Hai join point lồng nhau, retry hoặc duplicate thật | Gắn joinPointId/attemptId để phân biệt |
| `after returning` không chạy khi lỗi | Đó là semantics của after-returning | Test after-throwing/cleanup phù hợp |
| Lỗi gốc bị thay bằng lỗi logger | Cleanup ném exception | Cách ly lỗi telemetry theo hợp đồng |
| Aspect priority thay đổi bất ngờ | Không khai báo precedence rõ | Khai báo và test order |
| Around không gọi target | Short-circuit hoặc quên `proceed` | Assert invocation count/side effect |
| Retry tạo hiệu ứng lặp | Không có idempotency | Kiểm tra state và effect chứ không chỉ event |
| Test flaky khi song song | So sánh log global không correlation | Per-invocation assertions |
| Lồng quá sâu bị tràn stack | Đệ quy + advice quá rộng | Base case, depth limit và pointcut scope |
| Future báo success sớm | Chỉ quan sát lúc trả handle | Hook completion và test scheduler |
| Test pass nhưng production khác | Khác classloader, weaving/agent hoặc pointcut | Test trên build/deploy thực tế |

## 19. Quy trình kiểm thử trong một dự án AOP

**Bước 1.** Liệt kê các aspect cùng bao một business operation và những method được gọi lồng nhau. Vẽ ma trận operation × aspect.

**Bước 2.** Xác định hợp đồng precedence. Tại mỗi join point, aspect nào ở ngoài, aspect nào ở trong? Có trường hợp thứ tự không quan trọng hay không?

**Bước 3.** Dùng recorder thử nghiệm phát event khi vào, trước/ sau `proceed`, khi lỗi và khi dọn resource. Gắn invocation ID khi cần.

**Bước 4.** Viết test một method đơn, assert toàn bộ chuỗi sự kiện và return value. Thêm test method không thuộc pointcut.

**Bước 5.** Thêm nested outer→inner, test thành công và exception; đếm body invocations.

**Bước 6.** Thêm các trường hợp đặc biệt: short-circuit, retry, recursion, overloaded method, execution-vs-call.

**Bước 7.** Với async/concurrency, đo lifecycle theo correlation và thứ tự nhân quả, không cố ép global ordering.

**Bước 8.** Chạy integration test bằng cùng cơ chế interception/weaving mà sản phẩm dùng, và lưu các assertion vào CI.

### Checklist

- [ ] Pointcut match đúng cả outer và inner.
- [ ] Precedence giữa các aspect có ảnh hưởng thực tế được khai báo rõ.
- [ ] Entry và exit tuân thủ stack nesting.
- [ ] Error path vẫn thực hiện cleanup và giữ exception.
- [ ] Có assertion exact cho sự kiện, body count và kết quả.
- [ ] Có negative test cho method ngoài scope.
- [ ] Self-invocation và loại join point được hiểu theo cơ chế thật.
- [ ] Retry/short-circuit không tạo side effect sai.
- [ ] Async không bị nhầm handle return với job completion.
- [ ] Concurrent test có correlation và event collector an toàn.
- [ ] Toolchain build thực sự weave class cần thiết.

## 20. Kết luận

**Kiểm thử thứ tự nhiều aspect là kiểm thử cấu trúc bao lồng và luồng điều khiển, không phải chỉ nhìn số dòng log.** Khi A ưu tiên cao hơn B, A thường bao ngoài B: vào trước và ra sau. Khi outer gọi inner và cả hai là execution join point được weave, mỗi lời gọi tạo một stack advice riêng nằm trong stack của lời gọi cha. Thực hành hiệu quả là khai báo precedence rõ, **assert toàn bộ event trace ở cả success và failure**, kiểm tra số lần body/side effect, rồi mở rộng sang recursion, retry, concurrency và asynchronous completion.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-testing-006">Kiểm thử lời gọi nội bộ, đường gọi trực tiếp và mã không được dệt</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử lời gọi nội bộ, đường gọi trực tiếp và mã không được dệt trong AOP

## 1. Mục tiêu: kiểm chứng advice thật sự có mặt trên đường chạy

Trong **Aspect-Oriented Programming (AOP)**, *pointcut* chọn tập *join point* còn *advice* thực hiện hành vi cắt ngang như logging, tracing, đo lường, kiểm soát truy cập hoặc transaction. Tuy nhiên, **pointcut được khai báo không có nghĩa advice chắc chắn chạy**: đường gọi thực tế có thể không đi qua proxy, bytecode chứa join point chưa được dệt, hoặc điều kiện pointcut không khớp.

Bài học tập trung vào ba đường dễ bị bỏ sót:

1. **Lời gọi nội bộ (`self-invocation`):** một method của target gọi method khác của chính nó, ví dụ `this.inner()`.
2. **Đường gọi trực tiếp (`direct call`):** caller giữ tham chiếu target gốc và gọi không qua proxy/wrapper mong muốn.
3. **Mã không được dệt (`non-woven code`):** classfile hoặc vị trí gọi chưa được biến đổi bằng compile-time, post-compile hay load-time weaving.

Cần trả lời **ba câu hỏi độc lập** trước khi kết luận kiểm thử pass:

- **Pointcut có chọn đúng join point không?** Kiểm tra `call` so với `execution`, chữ ký, overload, annotation, phạm vi package, điều kiện động.
- **Cơ chế gắn advice có áp dụng tại đó không?** Với proxy, object thực tế có đi qua proxy không? Với bytecode weaving, phần thân method hoặc call site cần thiết đã được weave chưa?
- **Hành vi runtime có đúng không?** Advice chạy 0, 1 hay nhiều lần; original method được gọi bao nhiêu lần; return, exception, side effect và ngữ cảnh có được bảo toàn?

Đây là nội dung AOP cơ bản/nâng cao nói chung. Ví dụ bên dưới dùng **JDK dynamic proxy** và **AspectJ code-style** để phân biệt hai cơ chế, không coi quy tắc một cơ chế là quy tắc phổ quát cho tất cả AOP.

## 2. Ba khái niệm: pointcut match, interception và hợp đồng runtime

Một lời gọi có thể được mô hình hóa:

~~~text
caller
  -> đường gọi / call site
  -> (proxy hoặc bytecode đã weave nếu có)
  -> advice được chọn
  -> thao tác nghiệp vụ
~~~

**Pointcut match** nói một join point thỏa điều kiện chọn. **Interception** nói cơ chế AOP thật sự nằm trên đường thực thi. **Advice contract** nói advice được phép quan sát, short-circuit, retry, biến đổi lỗi hoặc quản lý tài nguyên thế nào.

Ví dụ một pointcut chọn mọi method tên `charge`, nhưng một job nền gọi trực tiếp implementation thay vì đi qua proxy. Logic charge vẫn có thể chạy, nhưng advice audit/authorization được áp tại proxy **không chạy**. Ngược lại, một class đã được weave nhưng không thuộc pointcut thì advice cũng không chạy. Tương tự, advice có thể chạy nhưng return sớm trước khi gọi nghiệp vụ; chỉ kiểm tra log “advice entered” vẫn chưa đủ.

Đối với chính sách bắt buộc như quyền truy cập hoặc audit, **thiếu advice có thể là lỗi an toàn** chứ không chỉ mất telemetry. Bài kiểm thử phải chứng minh mọi entrypoint tới tài nguyên nhạy cảm đều có cơ chế cưỡng chế phù hợp, có thể gồm kiểm soát bổ sung tại data/resource boundary.

## 3. Proxy và lời gọi nội bộ

### 3.1 Proxy chặn lời gọi nào?

Một proxy là đối tượng trung gian:

~~~text
Client -> ServiceProxy -> advice -> ServiceTarget.outer()
~~~

Giả sử `ServiceTarget.outer()` gọi:

~~~java
String outer(String value) {
    return "outer(" + inner(value) + ")";
}
~~~

Nếu đây là cơ chế **JDK dynamic proxy** chuyển tiếp method interface sang target, `inner(value)` được gọi trên `this` chính là target, **không tự quay lại invocation handler của proxy**.

~~~text
Client
  -> Proxy.outer()       [advice: outer]
       -> Target.outer()
            -> this.inner()  [gọi trực tiếp Target.inner]
~~~

Lời gọi `Proxy.inner()` từ caller bên ngoài có thể bị advice chặn, nhưng `Target.outer() -> this.inner()` không tạo thêm một interception của proxy. Đây là đặc điểm đường tham chiếu, không phải do tên `inner`.

### 3.2 Khi direct call bỏ qua proxy

Nếu caller giữ cả hai object:

~~~java
Service proxy = createProxy(target);
proxy.operation();    // có thể đi qua advice
target.operation();   // không qua proxy
~~~

Đây là mã minh họa, không độc lập. Tương tự, `new ServiceImpl()` tạo đối tượng chưa được bọc; lời gọi trên nó không chịu advice của proxy. Việc một class *có thể* được proxy không có nghĩa mọi instance và mọi đường gọi đã được proxy.

**Lưu ý giới hạn:** Các kỹ thuật proxy hoặc weaving khác có thể hỗ trợ bề mặt interception khác nhau. Không phát biểu “mọi self-invocation đều bỏ qua AOP”; điều đúng ở đây là **lời gọi nội bộ trên target bỏ qua handler của JDK dynamic proxy**.

## 4. Ví dụ Java 17+ chạy độc lập: ba đường gọi

Ví dụ sau tự dựng JDK dynamic proxy, lưu các method **thật sự đi qua handler** và so sánh với việc gọi trực tiếp. Không cần dependency ngoài JDK.

**Tệp `ProxyPathTest.java`:**

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

public class ProxyPathTest {
    public interface Service {
        String outer(String value);
        String inner(String value);
    }

    public static final class ServiceImpl implements Service {
        @Override
        public String outer(String value) {
            return "outer(" + inner(value) + ")";
        }

        @Override
        public String inner(String value) {
            return "inner(" + value + ")";
        }
    }

    public static final class CountingAdvice implements InvocationHandler {
        private final Service target;
        private final List<String> calls;

        CountingAdvice(Service target, List<String> calls) {
            this.target = target;
            this.calls = calls;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException error) {
                    throw error.getCause();
                }
            }

            calls.add(method.getName());
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        }
    }

    static Service wrapped(Service target, List<String> calls) {
        return (Service) Proxy.newProxyInstance(
            Service.class.getClassLoader(),
            new Class<?>[] { Service.class },
            new CountingAdvice(target, calls)
        );
    }

    public static void main(String[] args) {
        ServiceImpl target = new ServiceImpl();
        List<String> calls = new ArrayList<>();
        Service proxy = wrapped(target, calls);

        System.out.println("through-proxy=" + proxy.outer("A"));
        System.out.println("observed=" + calls);

        System.out.println("explicit-proxy=" + proxy.inner("B"));
        System.out.println("observed=" + calls);

        System.out.println("direct-target=" + target.inner("C"));
        System.out.println("observed=" + calls);

        Service unwrapped = new ServiceImpl();
        System.out.println("unwrapped=" + unwrapped.outer("D"));
        System.out.println("observed=" + calls);

        if (!calls.equals(List.of("outer", "inner"))) {
            throw new AssertionError("Incorrect proxy interception");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ProxyPathTest.java
java ProxyPathTest
~~~

**Stdout xác định:**

~~~text
through-proxy=outer(inner(A))
observed=[outer]
explicit-proxy=inner(B)
observed=[outer, inner]
direct-target=inner(C)
observed=[outer, inner]
unwrapped=outer(inner(D))
observed=[outer, inner]
~~~

**Giải thích từng lần:**

- `proxy.outer("A")` đi qua handler nên ghi `outer`. `ServiceImpl.outer()` gọi `this.inner()` trực tiếp trên target, vì thế handler **không** ghi một lần `inner` cho lời gọi nội bộ.
- `proxy.inner("B")` được caller gọi trực tiếp **trên proxy**, nên ghi thêm `inner`.
- `target.inner("C")` gọi đối tượng gốc, không làm thay đổi danh sách lần interception.
- `unwrapped.outer("D")` không qua proxy từ đầu; bản thân `outer` và `inner` đều không được handler trên ghi nhận.
- Kiểm tra `List.of("outer", "inner")` chứng minh **số lần và thứ tự advice** mong đợi, không chỉ kết quả nghiệp vụ.

Mã dùng một `ArrayList` trong thử nghiệm tuần tự. Nếu nhiều thread cùng gọi proxy, recorder cần cơ chế đồng bộ an toàn và phải đợi tất cả lần chạy hoàn tất trước khi đọc kết quả.

## 5. Bytecode weaving khác proxy: phân biệt `call` và `execution`

Ở AspectJ, weaving biến đổi bytecode tại join point phù hợp. Hai loại quan trọng:

- **`execution(void Worker.inner())`:** chọn lúc vào *thân phương thức* `Worker.inner()`. Muốn advice tại execution chạy, classfile chứa method `Worker.inner` phải được weave đúng.
- **`call(void Worker.inner())`:** chọn tại *vị trí gọi* `worker.inner()`. Muốn advice tại call chạy, bytecode chứa **call site** phải được weave.

Đây là khác biệt quyết định khi kiểm thử mã không được dệt.

| Tình huống | `execution` advice của target | `call` advice tại caller |
| --- | --- | --- |
| Caller woven, target woven, pointcut match | Có thể chạy | Có thể chạy |
| Caller **không** woven, target woven | Vẫn có thể chạy | Không chạy tại call site chưa woven |
| Caller woven, target **không** woven | Không chạy ở method execution chưa woven | Vẫn có thể chạy tại call site woven |
| Caller và target đều chưa woven | Không có advice do weaving ở hai vị trí | Không có advice tại call site |
| Pointcut không match | Không chạy dù target woven | Không chạy dù caller woven |

“Có thể” còn phụ thuộc điều kiện pointcut/runtime và phạm vi weaving. **Target chưa weave không đồng nghĩa mọi `call` advice đều mất**, và **caller chưa weave không đồng nghĩa `execution` advice của target woven bị bỏ qua**.

### Self-invocation với execution đã weave

Nếu `outer()` gọi `this.inner()`, nhưng thân `inner()` đã được weave bằng một `execution` advice, thì khi JVM thực thi `inner()`, advice **vẫn có thể chạy** dù đây là lời gọi nội bộ. Vì vậy không lấy kết luận của proxy thuần áp thẳng sang AspectJ weaving.

### Self-invocation với call đã weave

Một lời gọi `this.inner()` bên trong `outer()` cũng có một call site. Nếu bytecode chứa call site ấy được weave và pointcut match, `call` advice có thể chạy; nếu call site không weave thì không. Điều kiện quan trọng là **vị trí lệnh gọi**, không chỉ vị trí implementation.

## 6. Ví dụ AspectJ: lời gọi nội bộ vẫn kích hoạt execution advice

Ví dụ này gồm một tệp Java và một tệp AspectJ code-style. Tác vụ `outer` gọi `inner` trong cùng một object; aspect chọn **method execution** của `inner`.

**Tệp `WovenSelfCallDemo.java`:**

~~~java
public class WovenSelfCallDemo {
    public static void main(String[] args) {
        Worker worker = new Worker();
        worker.outer();
    }
}

class Worker {
    void outer() {
        inner();
    }

    void inner() {
        System.out.println("body inner");
    }
}
~~~

**Tệp `WovenSelfCallAspect.aj`:**

~~~java
public aspect WovenSelfCallAspect {
    before(): execution(void Worker.inner()) {
        System.out.println("advice inner");
    }
}
~~~

Sử dụng JDK 17 và AspectJ compiler `ajc` tương thích Java 17, cùng `aspectjrt.jar`. Hai tệp nằm trong cùng một thư mục:

~~~bash
mkdir -p out
ajc -17 -d out WovenSelfCallDemo.java WovenSelfCallAspect.aj
java -cp "out:aspectjrt.jar" WovenSelfCallDemo
~~~

Nếu môi trường chưa đưa runtime vào classpath khi compile, thêm `-classpath aspectjrt.jar` vào lệnh `ajc` theo toolchain đang dùng. Trên Windows, dấu phân tách classpath dùng `;` thay `:`.

**Stdout khi weave thành công:**

~~~text
advice inner
body inner
~~~

Lệnh gọi `worker.outer()` đi vào `inner()` qua lời gọi nội bộ, nhưng vì **body `Worker.inner` đã weave** nên advice xuất hiện trước thông điệp nghiệp vụ.

### Đối chứng với bản classfile không được weave

Biên dịch **chỉ tệp Java thường** vào thư mục khác:

~~~bash
mkdir -p plain
javac -d plain WovenSelfCallDemo.java
java -cp plain WovenSelfCallDemo
~~~

**Stdout của bản plain:**

~~~text
body inner
~~~

Kết quả nghiệp vụ giống nhau nhưng advice không hiện diện. Hai bản build riêng giúp tránh nhầm classfile stale: không nên compile `javac` đè lên cùng output đã weave rồi khẳng định đó là kiểm thử đối chứng hợp lệ.

**Phân biệt công cụ:** `.aj` với cú pháp `aspect` không biên dịch bằng `javac` thuần. Ví dụ Java 17 ở phần 4 chạy bằng JDK đơn thuần, còn ví dụ này cần **trình biên dịch và runtime AspectJ**. Nếu build có lỗi hoặc classpath không đúng, không được giả định chương trình đã weave.

## 7. Lập kế hoạch test theo ba tầng

### Tầng 1: pointcut coverage

Ghi danh mục các operation:

| Operation / entrypoint | Advice bắt buộc? | Join point muốn kiểm tra | Điều cần loại trừ |
| --- | --- | --- | --- |
| Lệnh ghi dữ liệu | Authorization và audit | Method execution tại boundary được bảo vệ | Helper thuần túy |
| Lệnh đọc dữ liệu nhạy cảm | Authorization | Boundary/resource access | Endpoint công khai đã khai báo |
| Call tới resource bên ngoài | Metrics/circuit breaker theo thiết kế | Call site hoặc execution cụ thể | Client nội bộ ngoài phạm vi |
| Job nền thay đổi dữ liệu | Policy bắt buộc | Entry job và đường dữ liệu | Không bỏ sót vì không qua API |
| Metrics/exporter code | Thường loại trừ | Không để tự instrument đệ quy | Tránh recursion |

Từ danh mục này tính:
- **False negative:** điểm *phải* có advice nhưng không match/chưa được intercept.
- **False positive:** điểm *không nên* có advice nhưng bị áp dụng, gây overhead hoặc thay đổi semantics.

Đừng dùng tiền tố tên method làm bằng chứng quyền. Quy tắc truy cập phải dựa vào principal đã xác thực, action, resource và context thật; pointcut chỉ là phương tiện gắn policy.

### Tầng 2: wiring và weaving

Với proxy cần xác minh:

- Entry point nhận **proxy hay target**? Có object nào tạo thẳng bằng `new`?
- Handler chặn đúng method interface/chữ ký không?
- Có đường self-invocation, callback hoặc job dùng target gốc không?
- Có nhiều proxy lồng nhau gây chạy advice nhiều lần không?

Với bytecode weaving cần xác minh:

- Classfile nào thực sự được weave; class chứa call site hay method execution?
- Build dùng compile-time, binary/post-compile hay load-time weaving?
- Include/exclude package/module/jar và điều kiện runtime có đúng không?
- Bytecode chạy thật có đến từ build output đã weave, hay build cache/Classpath chứa bản plain?
- Load-time transformer có hoạt động **trước** khi class đích được định nghĩa không?
- Classloader, module access, compiler và runtime có tương thích không?

### Tầng 3: runtime behavior

Ở mỗi test cần xác nhận:

- Advice thực thi **đúng số lần** (0/1/n theo hợp đồng).
- Original method được gọi đúng số lần; không tự thêm side effect.
- Return value và exception gốc được giữ nguyên nếu advice thuần quan sát.
- Advice được phép chặn truy cập thì business method không chạy khi bị deny.
- Khi lỗi, timeout, cancellation, resource được giải phóng đúng theo vòng đời.
- Thứ tự nhiều advice phù hợp hợp đồng: authorization, transaction, retry, audit, metrics có thể cho hành vi khác khi đổi order.

Có metadata đúng hoặc class proxy đúng kiểu **chưa đủ** nếu test không quan sát được hành vi.

## 8. Ma trận test cho đường gọi nội bộ và direct call

| Trường hợp | Cách gọi | Kỳ vọng trong JDK proxy minh họa |
| --- | --- | --- |
| Qua proxy | `proxy.inner("X")` | Handler ghi `inner` |
| Proxy tới outer | `proxy.outer("X")` | Handler ghi `outer` |
| Self-invocation | `outer -> this.inner` trong target | Không có interception **thêm** của `inner` trong handler |
| Direct target | `target.inner("X")` | Không có interception |
| Object chưa bọc | `new ServiceImpl().inner("X")` | Không có interception |
| Method ngoài interface | Caller trực tiếp gọi method chỉ có ở target | JDK proxy không tự cung cấp method đó |
| Reflection trên proxy | `Method.invoke(proxy, ...)` với interface method phù hợp | Có thể đi qua handler |
| Reflection trên target | `Method.invoke(target, ...)` | Bỏ qua handler proxy |
| Target ném exception | Proxy gọi target | Handler chuyển tiếp đúng lỗi gốc |
| Nhiều thread | Nhiều caller cùng proxy | Count đúng và state thread-safe nếu cần |
| Callback dùng target | Task giữ target gốc | Không bị handler proxy chặn |
| Callback dùng proxy | Task giữ proxy | Có thể bị handler chặn khi gọi method interface |

**Reflection không tự động có nghĩa bypass.** Điều quyết định là receiver thực của lời gọi và join point/cơ chế sử dụng. Với execution advice đã weave, reflection gọi vào target woven vẫn có thể kích hoạt advice ở execution.

Khi một test kiểm tra “direct call không có advice”, đó là mô tả bề mặt proxy. **Không được dùng test ấy làm bằng chứng mọi direct call đều an toàn** nếu policy yêu cầu mọi thao tác ghi nhạy cảm phải được kiểm soát; kiến trúc phải đóng đường bypass hoặc có kiểm soát bổ sung.

## 9. Kiểm thử mã không được dệt và chẩn đoán build

### 9.1 Compile-time, binary và load-time weaving

- **Compile-time weaving:** compiler xử lý source/aspect và tạo bytecode đã chứa advice.
- **Binary/post-compile weaving:** mã đã biên dịch được weave tiếp ở một bước build.
- **Load-time weaving:** bytecode được biến đổi khi class được nạp theo cấu hình loader/agent.

Một class được viết trong cùng project với aspect **không chứng minh đã weave**. Điều cần kiểm tra là bytecode mà JVM thật sự nạp.

### 9.2 Nguyên nhân phổ biến gây thiếu weaving

1. Một module được build bằng `javac` rồi đưa vào runtime mà không qua weaver.
2. Dependency JAR nằm ngoài phạm vi include, `inpath` hoặc loader phù hợp.
3. Refactor đổi tên package, method hoặc annotation khiến pointcut không còn match.
4. Load-time weaver khởi động **sau** thời điểm class đích đã được nạp.
5. Classloader khác không tham gia cùng cấu hình biến đổi.
6. Build cache chứa classfile cũ; classpath ưu tiên bản plain trước bản woven.
7. Bytecode version/JDK/compiler/runtime không tương thích.
8. Aspect không được đưa vào build hoặc không được đăng ký với runtime đúng cách.

### 9.3 Các bằng chứng nên thu thập

- Danh sách điểm weave/match do compiler/weaver cung cấp khi có hỗ trợ.
- Thông tin đường classpath, version JDK/compiler/weaver và checksum artifact được chạy.
- Một probe advice tại join point bắt buộc, xác nhận nó chạy trong **tích hợp thật**.
- Một negative test ở điểm excluded, xác nhận advice không chạy.
- Diff số join point trước/sau refactor.
- Phép đối chứng hai output directory woven/plain như trong ví dụ.

Đừng trộn hai bản build trong một thư mục, và đừng chỉ nhìn sự hiện diện của `.aj` hoặc annotation trong source để kết luận bytecode có advice.

## 10. Async, callback, thread và nguồn sự kiện

Một phương thức có thể trả `CompletionStage` hoặc `Future` rất nhanh rồi thực thi ở executor. Around advice bao quanh **lời gọi tạo stage** có thể kết thúc trước công việc thật. Bài test cần phân biệt:

- Advice có chạy ở method return stage không?
- Task nền gọi method trên proxy hay target?
- Context như danh tính xác thực, trace ID, deadline có được truyền sang thread khác đúng cách không?
- Nếu mục tiêu đo **completion**, callback được gắn vào terminal outcome đúng một lần chưa?
- Cancellation của future có đồng nghĩa công việc nền đã dừng hay chưa? Không tự giả định.

Với một publisher sự kiện, lời gọi tạo publisher chưa chắc tạo subscription hoặc phát phần tử. Một publisher có thể có nhiều subscriber, nhiều `onNext` và tín hiệu `onError`, `onComplete` hoặc `cancel`. Cần xác định đang kiểm thử lời gọi tạo publisher, từng subscription, hay vòng đời stream. Advice đo/ghi log không được tự subscribe thêm, thay đổi `request(n)` hoặc tạo queue vô hạn làm hỏng backpressure.

Nếu một callback chạy sau khi outer method trả về, cấu trúc call stack và control-flow pointcut của lời gọi ban đầu có thể không còn. Không coi `ThreadLocal` tự động lan qua executor.

## 11. Kiểm tra advice chạy nhưng sai thứ tự hoặc chạy lặp

Một test chỉ kiểm tra có log `before` vẫn chưa chứng minh advice đúng. Ví dụ:

~~~text
Metrics(Retry(Transaction(Operation)))
~~~

Khi metrics nằm ngoài retry, một request logic có thể được đo một lần bao gồm mọi attempt. Nếu metrics nằm trong retry:

~~~text
Retry(Metrics(Transaction(Operation)))
~~~

nó có thể đếm từng attempt riêng. Điều tương tự xảy ra khi audit nằm trong hoặc ngoài cache/authorization: một nhánh short-circuit có thể ngăn các advice bên trong chạy.

Bài test cần mô tả **outer → inner**, số lần gọi gốc, số lần advice và hành vi success/deny/error. Cũng cần loại trừ code infrastructure khỏi pointcut để tránh advice gọi logger rồi logger lại bị advice, tạo đệ quy hoặc nhân đôi telemetry.

Nếu một request qua API → service → repository, ba join point có thể đều được instrument; đó là ba interceptions, **không phải** ba request người dùng. Metrics phải đặt tên theo đơn vị thực.

## 12. Mẫu kiểm thử âm tính và bất biến quan trọng

**Positive test:** một thao tác bắt buộc đi qua entrypoint production, advice được ghi nhận đúng lần, side effect phù hợp.

**Negative test:** một thao tác không thuộc scope đi qua cùng entrypoint, advice không được ghi nhận.

**Bypass test:** thử gọi trực tiếp target/đường nội bộ; nếu cơ chế cho phép bỏ qua policy bắt buộc, test phải phát hiện lỗ hổng kiến trúc thay vì coi bypass là ngoại lệ an toàn.

**Unwoven test:** chạy artifact plain đối chứng với woven; quan sát sự khác biệt đã dự kiến.

Các bất biến tốt:

1. Khi bị authorization deny, số lần business method chạy là **0**, không chỉ “có exception”.
2. Khi được allow và advice quan sát thuần túy, original method chạy **đúng một lần** và caller nhận giá trị/lỗi gốc.
3. Mỗi join point được chọn nhận số lần advice theo hợp đồng, không thiếu/nhân đôi ngoài chủ ý.
4. Khi thay đổi tên/package/overload, danh mục điểm bắt buộc vẫn được kiểm tra.
5. Khi có exception, advice vẫn ghi nhận đúng outcome và không che lỗi nghiệp vụ.
6. Khi async hoàn tất, resource/context được giải phóng và event được ghi một lần.
7. Khi không weave hoặc không đi qua proxy, bài test chẩn đoán trạng thái đó và không báo thành công giả.

## 13. Cách điều tra khi advice không xuất hiện

Một trình tự ngắn giúp tìm đúng nguồn lỗi:

1. **Kiểm tra join point:** method thực tế là call hay execution? Tên, chữ ký, annotation, modifier và điều kiện động khớp không?
2. **Kiểm tra receiver và đường gọi:** proxy hay target? Self-invocation? Callback giữ object nào?
3. **Kiểm tra wiring:** đã khởi tạo proxy đúng nơi? Đường job/message consumer có đi qua nó không?
4. **Kiểm tra weaving:** class chứa call site hoặc execution body có nằm trong build/loader đã weave không?
5. **Kiểm tra artifact:** JVM chạy classfile nào, đúng output directory/classpath/classloader/version không?
6. **Kiểm tra advice semantics:** advice bị pointcut condition loại ra, bị một advice ngoài short-circuit hoặc ném lỗi trước khi recorder chạy không?
7. **Kiểm tra công cụ đo test:** recorder có bị reset, sampling, async flush hay race làm mất event không?

Nếu pointcut metadata trông đúng nhưng runtime không chạy, nghiêng về **interception/wiring/weaving**. Nếu advice có chạy mà business method không chạy như kỳ vọng, nghiêng về **contract/order**. Nếu business chạy mà chính sách bắt buộc không chạy, cần xử lý như một **đường bypass**.

## 14. Lưu ý về loại phương thức và annotation

JDK dynamic proxy hoạt động qua **interface method** được khai báo. Nó không trực tiếp chặn constructor, private method, static method hoặc method riêng của implementation chỉ vì chúng tồn tại trên target.

Bytecode weaving có thể hỗ trợ thêm các loại join point, nhưng cần xác minh kỹ thuật và include/exclude của công cụ cụ thể. `final`, generic bridge method, overload và default method còn có thể ảnh hưởng cách matcher tra chữ ký hoặc metadata.

Annotation trên interface method không nhất thiết xuất hiện trong reflection của implementation method theo cùng cách, và ngược lại. Bài test phải kiểm tra **Method thực tế mà handler/weaver nhìn thấy**, thay vì khẳng định một annotation được “kế thừa” theo suy đoán.

Một phương thức `toString()` bị interceptor hoặc logger gọi có thể mang dữ liệu nhạy cảm; không vô tình instrument toàn bộ object method mà không có chính sách rõ.

## 15. Chiến lược test không làm mất tính ổn định

Để test nhanh và tin cậy:

- Dùng **fake advice recorder** hoặc counter thay cho log mạng thật. Tái khởi tạo recorder giữa các test.
- Với nhiều thread, dùng `CountDownLatch`, `Future` hoặc barrier để điều phối; không chỉ dựa vào `Thread.sleep()`.
- Chỉ đọc counter sau khi các tác vụ liên quan kết thúc, hoặc dùng snapshot đồng bộ khi cần.
- Tách riêng test business unit khỏi **integration test đi qua cơ chế AOP thật**.
- Tách classfile output cho bản woven và plain, giữ thông tin compiler/weaver/runtime.
- Viết test trường hợp **không match** cũng như **có match**.
- Với aspect bắt buộc về bảo mật/audit, dùng coverage assertion trên danh mục operation thay vì chỉ smoke test một method.
- Chạy regression sau thay đổi pointcut, tên package, chữ ký, cách tạo object, thứ tự advice hoặc đường async.
- Đo overhead của probe/instrumentation trên hot path khi cần; recorder rất nặng có thể làm thay đổi p95/p99 và điều kiện race.

Không dùng số lần log từ hệ thống quan sát vốn có sampling để khẳng định số advice invocation chính xác. Khi cần test logic, một recorder có thể kiểm soát/đếm deterministic đáng tin hơn.

## 16. Checklist trước khi hoàn tất bài kiểm thử AOP

- [ ] Xác định cơ chế: proxy hay weaving, compile-time/post-compile/load-time.
- [ ] Xác định join point: call site hay execution body.
- [ ] Có test lời gọi qua proxy, self-invocation và direct target.
- [ ] Có test object không được bao bọc hoặc đường mới tạo bằng `new`.
- [ ] Có test woven vs plain artifact và không trộn output directory.
- [ ] Ghi danh sách operation **bắt buộc** advice, phát hiện thiếu coverage.
- [ ] Kiểm tra cả join point không được áp dụng advice.
- [ ] Đếm số lần advice và số lần original method thật sự chạy.
- [ ] Kiểm tra return, exception, side effects, thứ tự advice và nhánh short-circuit.
- [ ] Kiểm tra callback, thread pool, async completion/cancellation nếu có.
- [ ] Kiểm tra reflection đúng receiver proxy hay target.
- [ ] Xem xét classloader, build cache, include/exclude, overload và metadata.
- [ ] Phân biệt thiếu telemetry tùy chọn với thiếu kiểm soát bắt buộc.
- [ ] Regression khi refactor và thay đổi toolchain/wiring.
- [ ] Đã tài liệu hóa đường bypass được hỗ trợ và biện pháp bảo vệ bổ sung.

## 17. Kết luận

**Đường gọi quyết định advice có chạy hay không.** Với JDK dynamic proxy, lời gọi nội bộ trên target, gọi thẳng target và object không được bọc bỏ qua invocation handler. Với bytecode weaving, lời gọi nội bộ vẫn có thể nhận `execution` advice nếu thân method đã weave; `call` advice lại phụ thuộc call site đã weave. Bởi vậy kiểm thử cần phân biệt **pointcut match**, **cơ chế gắn advice** và **hành vi runtime**, có đối chứng woven/plain và proxy/direct, cùng kiểm tra số lần gọi và side effect thực tế. Với audit, authorization hoặc transaction bắt buộc, **không có advice phải được phát hiện như một lỗi bao phủ**, không được coi là tình huống vô hại chỉ vì business method vẫn trả kết quả đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-testing-007">Kiểm thử trạng thái dùng chung trong điều kiện đồng thời</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử trạng thái dùng chung trong điều kiện đồng thời của Aspect

## 1. Vì sao cần kiểm thử riêng tính đồng thời của Aspect?

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), một **aspect** áp dụng lời khuyên (**advice**) tại những **join point** mà **pointcut** lựa chọn. Khi cùng một aspect quan sát nhiều lời gọi đến từ các thread hoặc các tác vụ chồng lấn, những field và dependency mà advice sử dụng có thể trở thành **trạng thái dùng chung (shared state)**.

Ví dụ, một advice ghi nhận số operation hoàn tất:

~~~text
Thread A --\
            -> CountingAdvice -> businessOperation
Thread B --/
                  |
            shared count
~~~

Nếu mỗi lần advice đọc giá trị count rồi ghi count + 1 mà không đồng bộ hóa phù hợp, hai lời gọi có thể cùng đọc 0 và cùng ghi 1. Hai operation thực sự đã chạy, nhưng counter cuối chỉ bằng 1. Đây là **lost update**.

Một ví dụ nguy hiểm hơn: aspect giữ **currentUser** hoặc **correlationId** trong field của instance dùng chung. Khi yêu cầu của Alice và Bob đi qua cùng advice, một yêu cầu có thể ghi đè thông tin của yêu cầu kia. Lúc ấy lỗi không chỉ là số liệu sai mà còn có thể là **rò rỉ ngữ cảnh, ghi audit sai danh tính hoặc ra quyết định quyền sai**.

**Mục tiêu:** kiểm thử chính xác những trạng thái nào được chia sẻ, những trạng thái nào phải cô lập, cách advice tương tác với Java Memory Model, sự lồng nhau của lời gọi và vòng đời asynchronous. Đồng thời, phải kiểm tra số lần proceed, bảo toàn kết quả/ngoại lệ và điểm cắt thực của hệ thống AOP.

Bài chỉ đề cập đến AOP nói chung, không phụ thuộc vào một bộ công cụ AOP cụ thể. Các ví dụ Java 17 hoạt động độc lập; một số đoạn là mô hình minh họa cách advice sử dụng state.

## 2. Điều gì thực sự được chia sẻ?

Không phải cứ thấy một field trong aspect là nó được chia sẻ trên toàn bộ chương trình. Phạm vi của field phụ thuộc **mô hình tạo instance (aspect instantiation model)**, phạm vi classloader, cách proxy/weaving được cấu hình và vòng đời dependency.

Các loại state thường gặp:

| Trạng thái | Chủ sở hữu hợp lý | Rủi ro khi chia sẻ sai |
| --- | --- | --- |
| Tổng số operation đã hoàn tất | Metric recorder chung, thread-safe | Lost update, thống kê thấp hơn thực tế |
| Danh mục chính sách bất biến | Config immutable dùng chung | Rủi ro thấp nếu mọi thành phần chỉ đọc |
| Cache có thay đổi | Cache concurrent với key/scope hợp lệ | Race, stale data, cross-tenant leak |
| Current principal | Ngữ cảnh của một invocation/task | Caller A bị nhầm thành caller B |
| Correlation/trace context | Logical operation hoặc task | Trộn trace, sai parent-child relation |
| Start timestamp | Mỗi invocation | Duration bị ghép từ hai lời gọi khác nhau |
| Span đang mở | Scope của một đoạn công việc | Kết thúc nhầm span của lời gọi khác |
| Transaction/connection | Scope transaction cụ thể | Chia sẻ tài nguyên không an toàn, cleanup nhầm |
| Rate-limit bucket | Khóa giới hạn có chủ đích, concurrent | Vượt hạn mức hoặc từ chối nhầm |
| Attempt counter của retry | Một operation hoặc retry invocation | Attempts của nhiều lời gọi trộn nhau |

**Điểm xuất phát của kiểm thử** không phải “aspect có synchronized hay không”, mà là: **state này thuộc về ai, tồn tại bao lâu, và ai được phép truy cập đồng thời?**

## 3. Những mô hình tạo Aspect ảnh hưởng đến state

Các cơ chế AOP khác nhau hỗ trợ các phạm vi tạo aspect khác nhau. Về khái niệm, có thể gặp:

- **Singleton/shared aspect:** một instance phục vụ nhiều join point/lời gọi. Các instance field mutable có thể được nhiều thread truy cập.
- **Per-target/per-object:** mỗi object gắn với một aspect instance. Nếu object đó dùng chung giữa nhiều thread thì aspect state vẫn có thể chịu race.
- **Per-this/caller object:** gắn state với object phía gọi hoặc đang thực thi theo loại join point; không tương đương một instance cho mỗi request.
- **Per-control-flow:** tạo state cho một luồng thực thi động. Luồng này không tự động theo công việc được đẩy sang executor khác.
- **Per-type:** state gắn theo kiểu/lớp, không gắn theo từng object.
- **Per-invocation state:** các biến cục bộ của một lần chạy advice; thường là lựa chọn hợp lý cho timestamp, outcome và tham số của một lời gọi.

**Một per-target aspect không tự thread-safe**: một service singleton có thể là target của hàng nghìn request. **Một singleton aspect cũng không nhất thiết sai** nếu nó stateless, chỉ đọc immutable config hoặc dùng cấu trúc concurrent thích hợp.

Trong kiểm thử, phải xác định model thực được dùng thay vì suy đoán từ tên kiểu. Unit tests có thể chứng minh một handler thread-safe với các dependency giả; integration tests với weaving/proxy cần xác minh **instance thực sự được tạo và tái sử dụng theo phạm vi mong muốn**.

## 4. Bốn nhóm lỗi concurrency phải phân biệt

### 4.1. Data race và lost update

Hai thread truy cập cùng vị trí mutable, ít nhất một thread ghi và không có quan hệ đồng bộ phù hợp. Với phép đọc–sửa–ghi, cả hai có thể cùng đọc giá trị cũ và làm mất một cập nhật.

~~~text
initial count = 0
A read 0
B read 0
A write 1
B write 1
final count = 1, nhưng có 2 operation
~~~

Dùng **volatile int** không biến câu lệnh count++ thành nguyên tử: volatile cung cấp thuộc tính visibility/order nhất định, không gộp ba thao tác đọc–cộng–ghi thành một atomic read-modify-write.

### 4.2. Race logic dù từng thao tác riêng lẻ thread-safe

Một ConcurrentHashMap làm get và put an toàn ở mức từng phương thức, nhưng đoạn:

~~~text
if (!map.containsKey(key)):
    map.put(key, value)
~~~

không phải một thao tác nguyên tử. Hai thread có thể cùng vượt qua check. Cần dùng những primitive phù hợp như computeIfAbsent, putIfAbsent hoặc khóa bảo vệ toàn bộ invariant theo đúng contract.

Lưu ý **computeIfAbsent** có các ràng buộc riêng đối với mapping function: nên giữ function ngắn, không gây cập nhật đệ quy nguy hiểm, và hiểu semantics khi tính toán concurrent. Một concurrent collection không tự giải quyết mọi business invariant.

### 4.3. Rò context giữa các lời gọi

Một field currentPrincipal của shared aspect bị A ghi rồi B ghi trước khi A đọc. Một ThreadLocal không được remove cũng có thể làm task sau trên pooled thread thấy principal trước, dù hai task không chạy cùng lúc.

### 4.4. Vi phạm vòng đời bất đồng bộ

Advice đồng bộ mở scope rồi method trả một Future; background task tiếp tục trên thread khác. Nếu advice đóng resource quá sớm, task bị lỗi. Nếu giữ scope/counter cho tới callback không được thiết kế đúng, có thể rò tài nguyên hoặc đóng scope ở sai thread. Đây là lỗi lifecycle, không đơn thuần là vấn đề atomic counter.

## 5. Java Memory Model: nhìn từ kiểm thử AOP

Java Memory Model xác định khi nào các ghi của thread này **có thể được bảo đảm nhìn thấy** bởi thread khác và thứ tự nào được bảo toàn. Những quan hệ **happens-before** quan trọng:

- Các thao tác trước một lời gọi unlock trên monitor được bảo đảm nhìn thấy sau một lần lock tương ứng.
- Một volatile write happens-before volatile read tương ứng nhìn thấy ghi đó theo quy tắc đồng bộ.
- Các thao tác trước khi gọi **Thread.start()** happens-before hành động của thread mới.
- Hành động của thread kết thúc happens-before thread khác quay về thành công từ **Thread.join()** tương ứng.
- Với CountDownLatch, những thao tác trước countDown() được công bố tới thao tác sau await() thành công theo contract của latch.
- Các primitive trong java.util.concurrent như AtomicInteger, Future và concurrent collections có hợp đồng đồng bộ riêng.

Điều đó có nghĩa test không nên dùng một biến boolean thường để yêu cầu worker “đã xong chưa” rồi busy-spin vô hạn; chưa có ràng buộc visibility phù hợp. Dùng CountDownLatch, CyclicBarrier, Future, join hoặc primitive concurrent đúng chức năng.

Phải phân biệt ba thuộc tính:

**Atomicity:** nhiều bước có được thực hiện như một đơn vị không thể xen vào?

**Visibility:** thread khác có được thấy giá trị vừa ghi theo contract không?

**Ordering:** những thao tác có được quan sát theo thứ tự có thể dự đoán không?

Một biến volatile có thể cải thiện visibility nhưng không thay một cơ chế atomic increment. Một khóa có thể đồng thời đáp ứng mutual exclusion và visibility khi dùng đúng. Một AtomicInteger đáp ứng các thao tác nguyên tử được cung cấp, nhưng không tự ràng buộc với trạng thái nghiệp vụ khác.

## 6. Thiết kế kiểm thử không phụ thuộc vào may rủi

Cách sai thường gặp:

~~~text
Tạo 2 thread
Mỗi thread gọi advice 10 lần
sleep(100)
Nếu count cuối đúng thì kết luận an toàn
~~~

Chạy thử như vậy có thể pass hàng nghìn lần dù một lost update vẫn có thể xảy ra trong production. Sleep không chỉ định thứ tự xen kẽ; tải CI, scheduler, JIT và GC có thể thay đổi kết quả.

Cách tốt hơn: **điều phối một lịch interleaving xác định**.

~~~text
Thread A: read count -> báo "đã đọc" -> chờ cổng mở
Thread B: read count -> báo "đã đọc" -> chờ cổng mở
Test main: đợi đủ cả hai -> mở cổng
Thread A và B: ghi snapshot + 1
Test main: join hai thread rồi kiểm tra kết quả
~~~

Ta không khẳng định mọi cuộc đua dữ liệu đều tái hiện được bằng một lịch duy nhất. Nhưng bằng cách buộc hai lần đọc xảy ra trước bất kỳ lần ghi nào, test này chứng minh **một triển khai đọc–sửa–ghi không nguyên tử có thể mất cập nhật**.

Đối với triển khai đúng, dùng AtomicInteger.incrementAndGet hoặc một cơ chế bảo vệ toàn bộ thao tác tương đương, sau đó assert tổng bằng số lời gọi thực.

## 7. Chương trình Java 17 hoàn chỉnh: lost update và cô lập context

Ví dụ thực thi hai phép kiểm tra tách biệt:

1. Hai thread cùng thực hiện increment không nguyên tử theo một interleaving được điều phối, sau đó thực hiện increment nguyên tử.
2. Một executor chỉ có **một worker thread** chạy các task nối tiếp, nhằm xác minh context được restore/remove qua nested calls, thành công, ngoại lệ và thread reuse.

Điều này phản ánh hai nhóm state trong advice: **counter chia sẻ có chủ đích** và **identity/correlation riêng theo invocation**.

Tạo file **SharedAspectConcurrencyDemo.java**:

~~~java
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class SharedAspectConcurrencyDemo {

    // Minh họa một field mutable có thể nằm trong shared aspect.
    static final class SharedCounter {
        int unsafeCount;
        final AtomicInteger safeCount = new AtomicInteger();
    }

    // Một carrier scope theo thread, chỉ minh họa execution đồng bộ.
    static final class InvocationContext {
        private static final ThreadLocal<String> CURRENT =
                new ThreadLocal<>();

        static String current() {
            return CURRENT.get();
        }

        static <T> T withIdentity(
                String principal, Callable<T> action) throws Exception {
            Objects.requireNonNull(principal);
            Objects.requireNonNull(action);

            String previous = CURRENT.get();
            CURRENT.set(principal);
            try {
                return action.call();
            } finally {
                if (previous == null) {
                    CURRENT.remove();
                } else {
                    CURRENT.set(previous);
                }
            }
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void testLostUpdate() throws InterruptedException {
        SharedCounter state = new SharedCounter();
        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch releaseWrites = new CountDownLatch(1);

        Runnable adviceCall = () -> {
            // Cố ý tách read và write để tái hiện một lịch lỗi.
            int snapshot = state.unsafeCount;
            bothRead.countDown();
            try {
                releaseWrites.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }

            state.unsafeCount = snapshot + 1;
            state.safeCount.incrementAndGet();
        };

        Thread first = new Thread(adviceCall, "caller-a");
        Thread second = new Thread(adviceCall, "caller-b");
        first.start();
        second.start();

        // Không có write nào xảy ra trước khi cả hai read.
        bothRead.await();
        releaseWrites.countDown();

        first.join();
        second.join();

        require(state.unsafeCount == 1,
                "expected forced lost update");
        require(state.safeCount.get() == 2,
                "atomic counter should record both calls");
        System.out.println("unsafe_count=" + state.unsafeCount);
        System.out.println("atomic_count=" + state.safeCount.get());
    }

    static void testContextLifecycle() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<String> first = worker.submit(() ->
                    InvocationContext.withIdentity("alice", () -> {
                        require("alice".equals(InvocationContext.current()),
                                "outer identity");

                        String nested =
                                InvocationContext.withIdentity(
                                        "service", InvocationContext::current);
                        require("service".equals(nested),
                                "nested identity");
                        require("alice".equals(InvocationContext.current()),
                                "outer identity not restored");

                        return InvocationContext.current();
                    }));

            require("alice".equals(first.get()), "first result");
            System.out.println("nested_restored=true");

            // Cùng một worker thread phải không còn principal cũ.
            Future<Boolean> reuse = worker.submit(
                    () -> InvocationContext.current() == null);
            require(reuse.get(), "leak into next task");
            System.out.println("thread_reuse_clean=true");

            Future<Boolean> failure = worker.submit(() -> {
                try {
                    InvocationContext.withIdentity("bob", () -> {
                        throw new IllegalStateException("failure");
                    });
                    return false;
                } catch (IllegalStateException expected) {
                    return InvocationContext.current() == null;
                }
            });

            require(failure.get(), "context leak on exception");
            System.out.println("exception_cleanup=true");
        } finally {
            worker.shutdown();
            if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        testLostUpdate();
        testContextLifecycle();
        System.out.println("ALL CHECKS PASSED");
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac SharedAspectConcurrencyDemo.java
java SharedAspectConcurrencyDemo
~~~

Stdout khi hoàn thành bình thường:

~~~text
unsafe_count=1
atomic_count=2
nested_restored=true
thread_reuse_clean=true
exception_cleanup=true
ALL CHECKS PASSED
~~~

### Giải thích kết quả

**unsafe_count=1:** cả hai worker phải đọc snapshot trước khi cổng releaseWrites mở. Vì cả hai snapshot đều bằng 0, mỗi worker đều ghi giá trị 1. Thứ tự ghi sau khi mở cổng có thể khác nhau, nhưng kết quả cuối đều là 1. Đây là một minh họa **cố ý của bug**, không phải mẫu code nên dùng trong hệ thống thật.

**atomic_count=2:** cả hai worker cùng gọi incrementAndGet() của một AtomicInteger. Phép tăng tương ứng được thực hiện nguyên tử nên cả hai lần cập nhật đều được ghi nhận. AtomicInteger không cung cấp giao dịch nguyên tử cho những biến khác nằm ngoài nó.

**nested_restored=true:** scope inner tạm dùng principal service rồi khôi phục Alice; việc đóng một scope không được xóa nhầm context của scope ngoài.

**thread_reuse_clean=true:** executor một thread chạy task thứ hai trên thread đã dùng cho task thứ nhất. Context cũ đã được remove ở cuối scope trước.

**exception_cleanup=true:** ngay cả khi task ném IllegalStateException, finally của scope vẫn xóa principal Bob. Đây là điều kiện đặc biệt quan trọng nếu worker pool tái sử dụng thread để xử lý một yêu cầu thuộc người dùng khác.

**ALL CHECKS PASSED:** các assertion về hai loại state đã pass. Bài không có framework assertion bên ngoài; require() sẽ ném AssertionError nếu điều kiện sai.

### Phân biệt demo và một integration test AOP

Chương trình tự gọi những lớp helper thông thường; nó **không dùng weaver**. Do đó nó kiểm chứng cách xử lý shared state và context scope, không chứng minh rằng một aspect thực tế được gắn đúng join point hoặc được tạo với model mong muốn. Trong hệ thống dùng AOP, còn phải có kiểm thử tích hợp để xác nhận instance aspect cùng được dùng cho hai lời gọi, pointcut đúng và việc chặn diễn ra tại thời điểm dự kiến.

## 8. Kiểm thử bằng số lần proceed và outcome

Cùng với state, quanh một invocation cần xác minh **số lần advice cho phép computation tiếp tục**.

- Với logging/metrics observer bình thường: proceed() thường đúng **một lần** cho mỗi invocation.
- Với authorization deny: proceed() bằng **không**, nhằm ngăn side effect.
- Với retry có chủ đích: proceed() có thể nhiều lần, mỗi lần được ghi là một **attempt**.
- Với cache hit hoặc circuit breaker đang mở: proceed() có thể bằng **không** theo contract.
- Với một around advice vô tình gọi proceed() hai lần: operation nghiệp vụ có thể chạy hai lần, không chỉ counter sai.

Một phép đo counter đúng không có nghĩa invocation được thực thi đúng số lần. Ví dụ retry adapter gọi target ba lần nhưng chỉ tăng business-operation counter một lần là hợp lệ nếu có ba attempts và một logical operation. Test phải phân biệt các tầng đếm.

Khi có nhiều aspect, nên dùng **spy invocation** hoặc fake target ghi lại các lần proceed, sau đó assert business outcome, event count và state. Đừng chỉ nhìn stdout vì một event có thể được phát nhiều lần bởi các logger hoặc exporter khác nhau.

## 9. Bài test đúng phải xác định đơn vị của counter

Các chỉ số sau có thể khác nhau mà vẫn đúng:

| Counter | Đơn vị | Có thể lớn hơn cái gì? |
| --- | --- | --- |
| request_total | Một request giao thức | Số operation trong một số mô hình |
| operation_total | Một business operation | Số request khi một request sinh nhiều operation |
| attempt_total | Một lần thử | Số operation nếu retry |
| dependency_call_total | Một dependency call | Số operation do fan-out |
| advice_invocation_total | Một lần chạy advice | Số business operation nếu pointcut match nhiều join points |

Nếu aspect singleton của logging ghi **business_operation_total**, hai lời gọi song song phải làm tổng tăng hai. Nếu còn một advice khác cũng tăng cùng metric cho cùng operation, số cuối có thể là bốn. Đó là **semantic duplicate** và không được chữa bằng AtomicInteger: atomic chỉ đảm bảo cập nhật không mất, không đảm bảo định nghĩa phép đo đúng.

Nên có ownership matrix:

~~~text
business_operation_total   -> một boundary observer sở hữu
attempt_total              -> retry observer sở hữu
dependency_total           -> dependency boundary sở hữu
trace span                 -> instrumenter sở hữu từng scope
~~~

Việc kiểm thử concurrency phải xác minh cả **độ an toàn cập nhật** lẫn **ngữ nghĩa phép đo**.

## 10. Khi nào dùng AtomicInteger, LongAdder, synchronized và lock?

### AtomicInteger / AtomicLong

Phù hợp khi cần một counter nguyên tử với phương thức increment, get, compareAndSet hoặc atomic update. Có semantics rõ, dễ assert count cuối trong unit test.

Giới hạn: một atomic counter không tự đảm bảo một tuple state liên quan được cập nhật nguyên tử cùng nhau. Nếu advice phải đổi trạng thái từ OPEN sang CLOSED đồng thời thêm sự kiện hoặc thay timestamp, cần thiết kế invariant tổng thể.

### LongAdder

Phù hợp cho bộ đếm có tần suất cập nhật rất cao và contention lớn. Các phép cộng trải trên nhiều ô nội bộ để tối ưu throughput. Tuy nhiên **sum() khi đang có cập nhật đồng thời không phải snapshot nguyên tử tuyến tính hóa**; tránh assert số trung gian chính xác tại một thời điểm chưa quiescent. Sau khi tất cả tác vụ hoàn tất và có đồng bộ phù hợp, có thể assert tổng cuối.

### synchronized và ReentrantLock

Dùng khi cần bảo vệ một invariant bao gồm nhiều field hoặc một chuỗi thao tác phải atomic. Lock phải bảo vệ **cả đọc và ghi của invariant**, không chỉ một phía. Hạn chế scope lock; không giữ lock xuyên network I/O, logger blocking hoặc callback async tùy tiện.

### Immutable state

Nếu aspect chỉ cần policy/config không thay đổi, immutable record hoặc Map.copyOf giúp giảm tình trạng race do mutation. Nhưng bất biến của **tham chiếu** chưa đủ nếu các object lồng nhau vẫn mutable; cần deep immutability hoặc ranh giới sở hữu rõ.

### Concurrent collections

ConcurrentHashMap, concurrent queues hoặc atomic references có thể hữu ích. Đọc tài liệu từng API: các thao tác riêng lẻ có thể thread-safe nhưng tổ hợp nhiều thao tác chưa chắc duy trì business invariant.

**Không dùng volatile để thay atomic increment hoặc thay lock bảo vệ state nhiều trường.**

## 11. Test tính đồng thời phải kiểm tra cả safety và liveness

**Safety property:** điều xấu không xảy ra. Ví dụ không lost update, không cross-user context, không đóng span hai lần, không trả một permit đã cấp cho task khác.

**Liveness property:** hệ thống còn tiến triển. Ví dụ các lời gọi eventually complete, không deadlock, không starvation vô hạn, không giữ khóa trong khi chờ callback không thể chạy.

Một test chỉ assert count cuối sau join sẽ phát hiện một số safety bug nhưng có thể **treo vô hạn** nếu worker deadlock. Trong môi trường CI nên dùng deadline ở wait, đảm bảo giải phóng latch trong finally, shutdown executor và báo thông tin về thread còn chờ. **Timeout trong test chỉ là cơ chế phát hiện treo**, không chứng minh mọi thực thi production đều an toàn.

Các primitive nên dùng:

- CountDownLatch để chờ một lượng sự kiện đã xảy ra.
- CyclicBarrier hoặc Phaser để điều phối nhiều vòng xen kẽ.
- Future.get với timeout để chặn việc treo mãi.
- Semaphore để giới hạn số lượt đồng thời.
- ExecutorService để quản lý worker và cleanup.
- AtomicInteger và các primitive đồng bộ để đo chính xác số lượt.

Không gọi Thread.sleep như cơ chế đồng bộ chính. Sleep có thể dùng để mô phỏng latency trong benchmark/test khác, nhưng không bảo đảm hai thread đến một điểm theo thứ tự mong muốn.

## 12. Một pattern unit test chống lost update

Khi nghi một field mutable trong advice có phép đọc–sửa–ghi không nguyên tử:

1. Tách phần cập nhật state ra một handler/service nhỏ có thể gọi trực tiếp.
2. Dùng **hai invocation giả** của handler.
3. Đặt **barrier/latch** sau bước đọc snapshot và trước bước ghi trong phiên bản instrumented để ép cùng một lịch.
4. Cho hai thread chạy tới barrier.
5. Mở barrier, chờ cả hai hoàn tất.
6. Assert count đúng theo policy và đánh giá có mất cập nhật hay không.
7. Thay bằng primitive nguyên tử hoặc lock, sau đó viết lại test hành vi công khai (không dựa mã internals) để đảm bảo tổng operation thực sự đúng.

Một anti-pattern của test là **chỉnh implementation chỉ để test đi qua**: giữ một bản test-only instrumented dễ gây khác biệt so với mã sản phẩm. Tốt hơn là thiết kế state update trong một component có seam, kiểm thử riêng tính thread-safe và thêm integration test với advice thật. Lịch ép xen kẽ là công cụ giải thích lỗi, không phải bằng chứng hình thức về mọi interleaving.

## 13. Test read/write một cấu trúc state nhiều trường

Giả sử aspect giữ:

~~~text
lastOutcome = "SUCCESS"
lastOperationId = "O-31"
lastTimeNanos = 123456
~~~

Nếu các trường được cập nhật tách rời, một thread khác có thể đọc cặp outcome của operation A và ID của operation B. AtomicInteger không giải quyết lỗi **tính nhất quán của bản ghi nhiều trường**.

Các cách tổ chức phù hợp:

- Một immutable snapshot record và AtomicReference thay thế toàn bộ snapshot.
- Một lock bao quanh mọi cập nhật/đọc có tính nhất quán.
- Mỗi invocation giữ state cục bộ, chỉ phát immutable event vào thread-safe sink.
- Nếu cần lưu lịch sử, dùng queue phù hợp thay vì các “last” field dùng chung.

Một bài test có thể dùng barrier giữa lần ghi hai field để ép observer đọc đúng thời điểm không nhất quán; sau sửa, assert mọi snapshot đều thuộc một occurrence hoàn chỉnh. Tuy nhiên, cần tránh test tự đọc những biến thường từ nhiều thread khi không có contract đồng bộ; test nên tương tác qua API có semantics rõ.

## 14. Không để context của request trong field dùng chung

Một thiết kế sai:

~~~java
// Đoạn minh họa lỗi, không phải chương trình đầy đủ.
class BadAdviceState {
    String currentPrincipal;

    Object around(String principal, Invocation invocation)
            throws Exception {
        currentPrincipal = principal;
        return invocation.proceed();
    }
}
~~~

Nếu hai caller dùng chung instance này, field currentPrincipal có thể bị thay giữa lúc một operation còn chạy. Cả static field lẫn instance field của singleton aspect đều nguy hiểm khi dùng để lưu ngữ cảnh riêng từng lời gọi.

Các cách thay:

1. **Biến local** cho principal, start time, result và error của một invocation đồng bộ.
2. **Tham số tường minh** truyền identity/context tới những component cần nó.
3. **ThreadLocal có scope rõ** khi thực sự phục vụ call stack đồng bộ trên cùng thread.
4. **Task context immutable** khi công việc chuyển executor hoặc callback.
5. **Registry keyed theo operation ID** khi cần trạng thái workflow kéo dài, có cleanup/TTL/capacity/concurrency policy.

Nếu dùng ThreadLocal, giá trị chưa chắc là danh tính đáng tin; bảo mật vẫn cần trusted authentication và authorization. ThreadLocal là phương tiện mang context, không phải chứng cứ quyền.

## 15. Kiểm thử ThreadLocal đúng vòng đời

ThreadLocal có tính cô lập giữa các thread, nhưng test vẫn cần xét:

- Một lời gọi đặt context rồi trả bình thường → context được xóa hoặc restore.
- Một lời gọi ném exception → finally vẫn cleanup.
- Scope lồng nhau thay context → sau inner, outer giữ nguyên context cũ.
- Worker thread được tái sử dụng → task kế tiếp không thấy context của task trước.
- Caller thread và worker thread khác nhau → worker không tự có principal từ caller.
- Callback có thể chạy trên worker hoặc caller → không dựa vào vị trí chạy không được bảo đảm.
- Task hủy/timeout → scope được đóng ở điểm thực sự sở hữu lifecycle; handle timeout không đồng nghĩa task dừng.

Không chỉ test “hai thread có ThreadLocal khác nhau”. Một lỗi thường gặp nhất là **không remove() trong thread pool**, vì hai request có thể chạy nối tiếp trên cùng thread mà vẫn lẫn principal.

Với nested scope, lưu previous, set mới, và trong finally restore previous hoặc remove nếu previous null. Chỉ gọi remove sau inner scope sẽ làm mất context của outer. Chương trình Java ở trên kiểm tra cả nested restore và thread reuse.

## 16. Vì sao InheritableThreadLocal không phải lời giải cho executor?

**InheritableThreadLocal** có thể chuyển một giá trị từ thread cha sang một thread con vào thời điểm **tạo thread**. Nó không cung cấp cơ chế truyền context theo **mỗi task** của một executor có thread pool sống dài.

Các tác hại:

- Worker thường được tạo từ trước khi task được submit.
- Thread reuse làm giá trị kế thừa không đại diện cho invocation hiện hành.
- Nếu giá trị mutable, việc chia sẻ hoặc copy semantics có thể gây rò dữ liệu.
- Với nhiều caller, một pooled thread không thuộc riêng caller nào.

Thiết kế phù hợp hơn là tạo immutable context snapshot tại trusted boundary, truyền snapshot vào task wrapper, attach scope tại worker, và restore/remove trong finally. Với security context, còn cần kiểm tra hạn dùng/quyền bị thu hồi khi workflow kéo dài.

Một unit test tốt submit lần lượt nhiều task có context khác nhau vào cùng một executor một thread và xác minh task thứ ba không có context khi không được cấp. Sau đó kiểm thử exception path và cancellation path.

## 17. Trạng thái trong advice khi có lời gọi đệ quy/nested

Ngay cả khi chỉ có một thread, **shared mutable field** vẫn gây lỗi nếu lời gọi lồng nhau:

~~~text
Advice A begins operation outer, sets field start=100
    method outer calls inner
    Advice A begins operation inner, sets field start=120
    method inner ends, reads start=120
method outer ends, reads start=120 (mất start=100)
~~~

Đây là **reentrancy bug** không cần hai thread cùng chạy. Vì thế các biến như start time, error, result nên là local của mỗi advice invocation hoặc nằm trong một context stack được thiết kế rõ.

Các test nên bao phủ:

- Method được aspect chặn gọi một method khác cũng khớp pointcut.
- Có một exception ở inner nhưng outer tiếp tục, hoặc ngược lại.
- Scope inner được đóng đúng, outer còn nguyên.
- Counter cấp operation chỉ tăng theo semantics đã định: hai operation độc lập hoặc một operation chứa helper.
- Advice không tự intercept logging/helper của mình gây recursion vô hạn.

Không thêm một global Boolean "đang trong advice" để chữa bừa: nó có thể chặn nhầm invocation của thread khác. Thread-local reentrancy guard cũng cần cleanup và phân biệt nested operation hợp lệ với recursion không mong muốn.

## 18. Kiểm thử aspect singleton không có nghĩa ép nó thành stateless

Một singleton aspect có thể mang **shared state hợp lệ**, ví dụ:

- Bộ đếm tổng cho mỗi loại operation, thông qua thread-safe recorder.
- Chính sách bất biến dùng chung.
- Registry thread-safe quản lý các task có vòng đời rõ.
- Rate limiter chia sẻ số permit cho một tập caller.
- Cache concurrent có key/TTL/invalidation phù hợp.

Vấn đề không phải sự tồn tại của state, mà là **quyền sở hữu, invariant và primitive bảo vệ nó**.

Một bài test cho metric recorder phải phân biệt hai trường hợp:

- Sau N lời gọi hoàn tất, tổng cuối phải bằng N trong điều kiện không sampling/dedup.
- Trong khi N lời gọi đang chạy, một phép đọc giữa chừng có thể thấy giá trị trung gian; không bắt buộc mọi lần đọc là một snapshot của toàn hệ thống nếu API không hứa điều đó.

Nếu dùng LongAdder, sum() giữa chừng không phải snapshot nguyên tử. Với AtomicInteger, get() có semantics riêng; với nhiều metric fields, vẫn cần cơ chế đọc snapshot nhất quán nếu dashboard khẳng định chúng mô tả cùng một thời điểm.

## 19. Async completion và nhiều callback cùng sửa state

Một method được AOP quan sát có thể trả CompletableFuture hoặc CompletionStage. Có hai mốc:

~~~text
method called
    -> method returns stage (invocation kết thúc)
         -> background work
         -> stage completes (logical outcome)
~~~

Nếu một advice tăng counter khi method return và một callback khác cũng tăng counter khi stage hoàn tất, có thể đếm trùng hoặc đếm sai semantic scope. Nếu nhiều callback cùng cập nhật một mutable event object, chúng còn có thể gặp data race.

Thiết kế kiểm thử async cần:

1. Tạo một CompletableFuture chưa hoàn tất.
2. Gọi handler/adapter và xác minh chưa phát final business outcome nếu contract yêu cầu completion.
3. Hoàn tất stage bằng success, exception hoặc cancellation.
4. Assert event phù hợp được phát **đúng một lần**.
5. Khi có callback song song, bảo vệ state dùng chung hoặc phát immutable events qua thread-safe sink.
6. Kiểm tra handler không làm đổi outcome của task vì observer callback ném lỗi.
7. Phân biệt cancellation/timeout trên wrapper với việc task nền đã thực sự dừng và giải phóng tài nguyên.

Một callback có thể chạy ngay trên thread hiện tại nếu stage đã hoàn tất trước khi đăng ký, hoặc chạy trên thread khác về sau. Vì thế không thể giả định ThreadLocal ở method caller còn tồn tại trong callback.

## 20. Một mẫu test async có điều khiển

Đoạn minh họa dưới đây **không phải chương trình độc lập**, vì method observeCompletion và event sink thuộc handler của ứng dụng:

~~~java
CompletableFuture<String> task = new CompletableFuture<>();
List<String> events = new CopyOnWriteArrayList<>();

CompletionStage<String> watched =
        observeCompletion("order.submit", task, events::add);

// Task vẫn chưa hoàn tất: không được có final outcome.
require(events.isEmpty(), "premature completion event");

// Test chủ động hoàn tất kết quả, không cần sleep.
task.complete("DONE");

require(events.equals(List.of("operation=order.submit outcome=success")),
        "wrong completion event");
~~~

Kiểm thử tương tự với completeExceptionally() và cancellation theo contract. Việc CompletableFuture có thể thực hiện callback đồng bộ trên thread gọi complete() giúp test tránh dùng timer may rủi, nhưng handler thực vẫn phải xử lý trường hợp race giữa đăng ký callback và completion.

Nếu API test trả completion stage phụ thuộc mà callback tạo thêm lỗi, cần assert liệu lỗi observer có bị cô lập hay làm hỏng dependent stage theo contract. Đối với event ghi bền vững, không đơn giản nuốt lỗi như telemetry best-effort.

## 21. Kiểm thử shared map/caches trong Aspect

Một aspect có thể giữ cache để nhớ policy decision, metadata của method, compiled pattern hoặc state rate-limiting.

Cần phân biệt:

**Immutable initialization:** tạo một map lúc khởi tạo rồi không thay đổi, công bố theo cách thread-safe. Đây thường là rủi ro thấp.

**Concurrent read/write:** dùng một concurrent collection hoặc lock, nhưng phải xem lại invariant nhiều bước.

**Cache theo principal/tenant:** key phải chứa đủ ngữ cảnh; cache từ user A không được trả kết quả ALLOW cho user B.

**Expiry/eviction:** chỉ dùng một map không giới hạn gây memory leak; chính sách TTL và dung lượng phải rõ.

**Compute operation:** mapping function chạy bao nhiêu lần, có side effect không, có recursive update không? Dùng API theo contract thay vì giả định một lambda luôn được gọi đúng một lần dưới mọi hoàn cảnh.

Kiểm thử:

- Hai thread cùng đưa cùng key vào cache.
- Hai thread dùng key khác nhau không chia sẻ nhầm value.
- Cache invalidation và lookup xảy ra đồng thời.
- Resource/permission thay đổi làm cache cũ không bị sử dụng trái policy.
- Mapping function ném exception không để entry hỏng.
- TTL được kiểm bằng fake clock, không bằng sleep.
- Concurrent put không gây mất cấu trúc map hay exception ngoài contract.

Không cố thay domain transaction bằng một cache lock trong aspect. Invariant phải được bảo đảm tại điểm sở hữu dữ liệu nếu có tranh chấp đồng thời.

## 22. Rate limiter và semaphore: state chia sẻ có chủ đích

Rate limiter là ví dụ rõ ràng về state **phải** dùng chung theo một khóa nhất định. Nếu từng invocation tự tạo một limiter mới, tất cả lời gọi đều được cấp quota như chưa từng có request trước.

Nhưng khóa chia sẻ phải đúng:

- Theo service toàn cục?
- Theo principal?
- Theo tenant?
- Theo resource/endpoint?
- Theo node hay toàn cụm?
- Theo cửa sổ thời gian nào?

Một semaphore đồng bộ có thể giới hạn số lời gọi đang hoạt động trong một tiến trình. Khi test, cần xác minh:

1. Không quá N operations cùng giữ permit.
2. Khi deny/timeout/acquire interrupted, không phát sinh permit giả.
3. Sau khi target thành công hoặc ném lỗi, permit được trả đúng một lần.
4. Với async, permit tồn tại đúng tới mốc task completion nếu quota là concurrency của task, không chỉ tới lúc method trả stage.
5. Một lời gọi nested không bị deadlock do cố lấy lại permit theo cơ chế không reentrant, nếu thiết kế đó không cho phép.

Các assertion về số operations đồng thời có thể dùng AtomicInteger theo dõi active/maxActive và CountDownLatch để dừng worker ở critical section. Không dựa vào timestamp của các dòng log để đoán số worker đồng thời.

## 23. Testing concurrency không chỉ là test một counter

Một ma trận kiểm thử thiết kế tốt:

| Tình huống | Loại lỗi nhắm đến | Cách kiểm tra |
| --- | --- | --- |
| N advice invocation tăng shared counter | Lost update | Latch/barrier, assert total sau join |
| Hai request với principal khác nhau | Cross-request contamination | Gọi chồng lấn, assert identity ở từng pha |
| Hai task liên tiếp trên cùng worker | ThreadLocal leak | Single-thread executor, assert null ở task sau |
| Scope lồng nhau | Restore sai | Assert principal của outer sau inner |
| Advice target ném lỗi | Cleanup thiếu | Spy close, assert finally |
| Shared map check-then-put | Race logic | Controlled interleaving/atomic API |
| Metric observer được đăng ký hai lần | Semantic duplicate | Assert một final event/operation |
| Retry nhiều attempt | Nhầm counter | Operation count 1, attempts N |
| Future hoàn tất sau method return | Lifecycle lệch | Controlled future, assert không ghi sớm |
| Callback hoàn tất song song | Double completion / race | Atomic guard hoặc event ownership test |
| Multiple aspects cùng sửa shared state | Composition | Weaving integration test |
| Một target phục vụ nhiều thread | Per-target không thread-safe | Shared object, concurrent calls |
| Shared lock bị giữ khi observer lỗi | Deadlock/liveness | Timeout và spy release |
| Config hot reload | Publication race | Immutable snapshot/atomic reference |
| Task bị cancel/timeout | Cleanup sai lifecycle | Controlled cancellation và state assertions |

Không nhất thiết có một test duy nhất kiểm tra toàn bộ bảng. Unit tests nhỏ giúp xác định chính xác nguyên nhân; integration tests kiểm chứng những giả định phụ thuộc wiring AOP.

## 24. Tách kiểm thử logic và kiểm thử weaving

**Unit test logic advice** có thể gọi handler trực tiếp, truyền fake invocation, fake clock và fake metrics recorder. Phần này nên nhanh, xác định, không cần compiler/weaver hoặc agent chạy.

**Integration test AOP** cần cơ chế interception thật để xác nhận:

- Pointcut match đúng những join point.
- Advice không chạy ở helper hoặc những đường không mong muốn.
- Aspect instance được tạo theo model khai báo.
- Nhiều thread thực sự đi qua cùng instance nếu đó là thiết kế.
- Thứ tự advice với retry, transaction, authorization, tracing có hiệu lực.
- Lời gọi trực tiếp/self-invocation có được instrument hay bỏ qua theo kiểu AOP được chọn.
- Những aspect khác không cùng ghi một event trùng ngữ nghĩa.
- Với async, observer được đặt tại đúng lifecycle boundary.

**End-to-end test** còn xác minh business side effects, quyền truy cập, dữ liệu lưu trữ và telemetry khi đi qua các tầng. Một unit test chứng minh AtomicInteger tăng đúng không chứng minh rằng pointcut bắt đúng operation hoặc toàn bộ pipeline metrics không scrape trùng.

## 25. Không test bằng một global flag chống recursion

Một số advice tự kích hoạt lại khi ghi log hoặc gọi helper thuộc pointcut. Có người thêm:

~~~text
static boolean insideAdvice = false
if (insideAdvice) proceed()
else:
    insideAdvice = true
    try observeAndProceed()
    finally insideAdvice = false
~~~

Đây là giả mã của thiết kế **sai trong concurrency**: thread A đặt true có thể khiến thread B bỏ qua monitoring của một operation hoàn toàn độc lập.

Thay thế:

- Thu hẹp pointcut để loại helper của instrumentation.
- Dùng một reentrancy guard theo scope phù hợp, có cleanup trong finally.
- Phân biệt nested business operations hợp lệ và self-recursion không mong muốn.
- Kiểm thử hai threads đồng thời cùng qua advice; không để flag của A ảnh hưởng B.
- Nếu context được truyền sang async task, dùng task-specific identity/scope phù hợp; ThreadLocal guard không tự di chuyển sang thread mới.

Một **ThreadLocal<Boolean>** cũng không phải “tất cả đã ổn”: nếu nó không được reset, pooled thread có thể bỏ qua những operation tiếp theo. Nếu guard chặn mọi nested invocation, nó có thể làm mất metric hợp lệ của use case con.

## 26. Khi concurrency bug nằm ngoài Aspect

Một request có thể bị đếm trùng hoặc ghi sai không phải do aspect state:

- Hai collectors nhận cùng metric và dashboard cộng hai lần.
- Retry trong nghiệp vụ thực thi nhiều attempts.
- Message broker giao lại sự kiện nên có nhiều processing attempts thật.
- Database transaction gặp race và trả kết quả sai bất chấp aspect thread-safe.
- Một logger/appender chuyển cùng event qua hai pipeline.
- Một component giữ mutable singleton context mà advice chỉ đọc.
- Pointcut áp dụng nhiều lần do cấu hình instrumentation chồng lấn.

Phương pháp điều tra:

1. Đặt điểm kiểm tra ngay ở handler/advice, xem số lần gọi và state.
2. Xác định source event ID và semantic unit.
3. Theo dõi event qua exporter, collector, storage, query.
4. Kiểm tra những điểm shared mutable state khác trong đường gọi.
5. Tách lỗi race thực khỏi sự kiện hợp lệ do retry, fan-out hoặc nested operations.

Không sửa một counter bằng synchronized rồi kết luận mọi vấn đề quan sát đã hết. Thread safety và đo đúng đơn vị là hai tiêu chí độc lập.

## 27. Một thử nghiệm stress bổ sung

Sau những test điều phối deterministic, có thể chạy **stress test**:

- Cố định số thread và số operation trên mỗi thread.
- Đồng bộ thời điểm bắt đầu bằng latch/barrier.
- Ghi tổng số proceed và business completions.
- Chạy nhiều vòng với các mức concurrency khác nhau.
- Thử xen kẽ nested calls, exception, cancellation và timeout.
- Assert trạng thái sau khi toàn bộ workers hoàn tất; kiểm tra liveness bằng deadline.
- Chẩn đoán khi fail bằng operation ID và event record ít dữ liệu, không log secrets.

Stress test có giá trị khám phá interleavings chưa nghĩ tới; **pass không phải bằng chứng không có data race**. Review code, primitive đồng bộ, kiểm thử deterministic và phân tích concurrency thích hợp đều cần thiết. Nếu test fail không ổn định, cố tái tạo lịch gây lỗi thay vì tăng sleep một cách tùy ý.

## 28. Những sai lầm và cách sửa

### Sai lầm 1: “Aspect singleton nên mọi biến trong nó đều có thể dùng chung”

**Sai:** Một singleton aspect có thể chứa configuration chung, nhưng currentUser/startTime riêng cho invocation không nên là shared field. Dùng local variables hoặc context có scope đúng.

### Sai lầm 2: “Thêm volatile là count++ thread-safe”

**Sai:** volatile không biến read-modify-write thành atomic operation. Dùng AtomicInteger, LongAdder theo mục tiêu hoặc synchronized/lock.

### Sai lầm 3: “Dùng ConcurrentHashMap là mọi check-then-act đã nguyên tử”

**Sai:** các chuỗi thao tác cần API atomic hoặc lock bảo vệ invariant. Test theo các interleavings có thể xảy ra.

### Sai lầm 4: “ThreadLocal tự xóa khi request kết thúc”

**Sai:** worker pool tái sử dụng thread. Cần restore/remove trong finally, kiểm thử với một executor cùng worker.

### Sai lầm 5: “ThreadLocal tự truyền sang Future”

**Sai:** executor có thread riêng. Truyền context của task một cách tường minh và kiểm tra expiry/reauthorization khi cần.

### Sai lầm 6: “Một bài test dùng sleep đã chứng minh không có race”

**Sai:** sleep không điều phối thứ tự. Dùng latch/barrier và assert qua synchronization.

### Sai lầm 7: “Aspect pertarget có nghĩa mỗi request một instance”

**Sai:** target object có thể là singleton phục vụ nhiều request; field pertarget vẫn cần thread-safe nếu mutable.

### Sai lầm 8: “Count bằng số request là đủ”

**Sai:** một request có thể sinh nhiều operations; một operation có retry attempts. Phải định nghĩa semantic unit trước.

### Sai lầm 9: “Chỉ cần unit test handler”

**Sai:** pointcut hoặc weaving có thể sai; cần integration test cho instance lifetime, match và composition.

### Sai lầm 10: “Một lock quanh toàn advice sẽ an toàn”

**Không chắc:** lock rộng gây contention, deadlock khi nested/reentrant hoặc giữ qua blocking I/O. Lock chỉ bảo vệ những invariants cần thiết trong scope được thiết kế.

### Sai lầm 11: “Task timeout là đã cleanup xong”

**Sai:** wrapper có thể timeout trong khi worker vẫn chạy. Resource ownership phải theo task lifecycle thật.

### Sai lầm 12: “Logger không thể gây race”

**Sai:** mutable event objects, context fields và callbacks có thể race; logger có thể giữ tham chiếu vào object mutable để serialize về sau. Gửi immutable snapshot và có schema/redaction.

## 29. Quy trình triển khai bài kiểm thử concurrency cho một Aspect

**Bước 1 — Kiểm kê state.** Liệt kê mọi field của aspect, các dependency mutable, static state, ThreadLocal, cache, metric recorder và scope.

**Bước 2 — Xác định ownership.** State thuộc toàn bộ process, target object, một operation, một attempt hay task? Có bao nhiêu thread cùng đọc/ghi?

**Bước 3 — Viết invariant.** Ví dụ: “mỗi operation hoàn tất tăng counter đúng một lần”, “không invocation nào quan sát principal khác”, “mọi scope mở đều được đóng đúng chỗ”.

**Bước 4 — Chọn primitive.** Immutable snapshots, atomic counter, synchronized/lock, concurrent map hoặc task context theo từng invariant.

**Bước 5 — Tạo seam test.** Handler độc lập với weaver, fake invocation, fake clock, event sink và executor/latch điều khiển được.

**Bước 6 — Kiểm tra đường success và failure.** Target trả bình thường, ném lỗi, callback thất bại, observer lỗi, authorization DENY, retry và cancellation.

**Bước 7 — Điều phối race.** Buộc các interleavings rủi ro tại điểm read/write hoặc critical section; không dựa vào sleep.

**Bước 8 — Kiểm tra state sau kết thúc.** Dùng join/Future/get và những assertion trên counter, event count, context, resource cleanup.

**Bước 9 — Kiểm thử runtime AOP.** Với weaving/proxy thật, xác minh các lời gọi đồng thời cùng dùng đúng aspect instance và pointcut.

**Bước 10 — Stress và theo dõi.** Thử tải đại diện, quan sát throughput/latency/contention và memory leak; giữ test deterministic thành regression test.

## 30. Bài tập tự kiểm tra

**Câu 1:** Một aspect dùng singleton scope, field int total và advice gọi total++. Hai lời gọi concurrent có chắc tổng tăng 2 không?

**Đáp án:** Không. total++ không nguyên tử; có thể lost update. Dùng primitive phù hợp và test interleaving.

**Câu 2:** Đổi total sang volatile int có đủ không?

**Đáp án:** Không. volatile không tự biến thao tác tăng thành atomic.

**Câu 3:** Dùng AtomicInteger đã ngăn mọi hình thức đo trùng chưa?

**Đáp án:** Chưa. Nếu hai aspect đều phát cùng completion event cho một operation, cả hai increment có thể an toàn nhưng số đo vẫn sai gấp đôi.

**Câu 4:** Tại sao test hai task liên tiếp trong cùng một single-thread executor hữu ích?

**Đáp án:** Nó phát hiện ThreadLocal không được cleanup và rò context giữa hai task dù không chạy đồng thời.

**Câu 5:** Một aspect pertarget có field lastUser. Service target là singleton. Có an toàn không?

**Đáp án:** Không mặc nhiên. Một target dùng chung có thể khiến lastUser bị nhiều caller ghi đè.

**Câu 6:** Một span scope kết thúc ở method return nhưng work còn chạy trong Future. Điều gì sai?

**Đáp án:** Phạm vi đo không còn biểu diễn lifecycle của work; nếu muốn đo work phải dùng completion/task observer với context và cleanup phù hợp.

**Câu 7:** Vì sao LongAdder.sum() giữa những increment đang chạy không luôn là một snapshot nguyên tử?

**Đáp án:** Các ô cộng dồn có thể được đọc tại những thời điểm khác nhau; API tối ưu tranh chấp hơn là tính linearizable của mọi lần đọc tổng giữa concurrency.

**Câu 8:** Test handler đúng có chứng minh pointcut áp dụng đúng method không?

**Đáp án:** Không. Cần một integration test có cơ chế AOP thật để xác minh match, binding và instantiation.

## 31. Checklist QA trước khi triển khai

- [ ] Đã xác định mọi state mutable mà advice đọc/ghi.
- [ ] Mỗi state có ownership và lifecycle rõ.
- [ ] Singleton/per-object/per-flow model được hiểu theo thực tế, không theo suy đoán.
- [ ] Shared counters dùng atomic/lock phù hợp, không dùng volatile++.
- [ ] Các invariants nhiều trường được cập nhật/đọc nhất quán.
- [ ] Immutable configuration được công bố an toàn giữa các threads.
- [ ] Không có currentUser/correlationId riêng invocation trong shared field.
- [ ] ThreadLocal được restore/remove trong finally; nested scope đã có test.
- [ ] Executor reuse và async context propagation đã được kiểm chứng.
- [ ] Đồng thời không làm thay đổi số lần proceed ngoài policy.
- [ ] Retry/attempt và operation metric phân biệt rõ, không đo trùng.
- [ ] Timing dùng clock đơn điệu, không dùng sleep để đồng bộ test.
- [ ] Latches/barriers/Futures được quản lý đúng, test có phương án timeout nếu treo.
- [ ] Cancellation và callback exception có semantics được kiểm thử.
- [ ] Cache/key/TTL/tenant isolation được bảo vệ.
- [ ] Scope và permit được release đúng số lần, không giữ tài nguyên quá lifecycle.
- [ ] Pointcut, precedence và model tạo aspect được test bằng AOP integration.
- [ ] Stress tests bổ sung nhưng không thay deterministic tests.
- [ ] Event/log không chứa credentials, không giữ mutable request object về sau.

## 32. Kết luận

**Aspect dùng chung không đồng nghĩa trạng thái nào cũng nên dùng chung.** Bộ đếm và registry có thể chia sẻ khi được bảo vệ bởi primitive phù hợp; identity, timestamp, outcome và scope của một invocation cần được cô lập theo đúng lifecycle. Nhiều lỗi xuất hiện không chỉ khi hai thread thực sự chạy cùng lúc, mà còn khi nested calls ghi đè field hoặc worker thread tái sử dụng ThreadLocal chưa cleanup.

Một chiến lược kiểm thử vững chắc kết hợp **unit test handler với fake dependencies**, **lịch đồng thời điều khiển bằng latch/barrier**, **kiểm thử cleanup và async completion**, cùng **integration test weaving/pointcut/instance scope**. Kết quả cuối không chỉ là số counter đúng: mỗi operation phải được ghi nhận với **đúng ngữ nghĩa, đúng chủ sở hữu state và đúng ranh giới thực thi**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-testing-008">Kiểm thử tác vụ bất đồng bộ và ngữ cảnh được truyền</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử tác vụ bất đồng bộ và ngữ cảnh được truyền trong Aspect (AOP)

## 1. Vì sao kiểm thử bất đồng bộ khác kiểm thử advice đồng bộ?

Trong **Aspect-Oriented Programming (AOP)**, aspect mô-đun hóa những hành vi cắt ngang như tracing, audit, metric, kiểm soát quyền và một số chính sách thời gian thực thi. Pointcut chọn join point; advice được kích hoạt ở thời điểm mà cơ chế AOP thực sự chặn được, chẳng hạn **method execution** hay **method call**.

Với một phương thức đồng bộ, lời gọi có thể kết thúc khi kết quả trả về hoặc exception được ném. Nhưng một phương thức bất đồng bộ có thể **trả một handle trước khi tác vụ hoàn thành**:

~~~text
Caller thread                  Worker thread
     |                               |
     |-- processAsync() ------------>|
     |<-- CompletionStage ---------- |
     |                               |-- thực thi nghiệp vụ
     |                               |-- trả giá trị / phát lỗi
     |<------ completion signal -----|
~~~

Nếu around advice chỉ bao quanh `processAsync()`, thời điểm `after returning` có thể chỉ là lúc **trả stage/future**, không phải lúc công việc nền đã thành công. Một lời gọi trả handle nhanh rồi xử lý lỗi sau 5 giây **không phải thao tác nghiệp vụ thành công** chỉ vì method đã return bình thường.

Ngoài thời gian, **ngữ cảnh** (context) như trace ID, correlation ID, principal đã xác thực, tenant, locale, deadline hoặc policy snapshot có thể bị mất, dùng nhầm hoặc rò khi chuyển giữa các thread. Một aspect dùng `ThreadLocal` trên caller thread không mặc nhiên có dữ liệu ấy trên worker thread.

**Mục tiêu kiểm thử**: xác nhận aspect chọn đúng join point; bảo toàn ngữ nghĩa nghiệp vụ khi có success/error/cancel/timeout; context được capture/propagate/restore/cleanup đúng ranh giới; metric/log/trace gắn đúng operation; không phát hai sự kiện kết thúc, không chặn worker và không rò context giữa những request tái sử dụng thread.

Bài này tập trung **AOP tổng quát từ cơ bản đến nâng cao**, không phụ thuộc một framework AOP cụ thể. Ví dụ Java 17+ dùng JDK dynamic proxy làm điểm chặn minh họa around advice; các nguyên tắc kiểm thử vẫn cần áp dụng với weaving/interceptor/decorator theo đúng tập join point thực tế của công cụ.

## 2. Bốn vòng đời cần phân biệt

Không nên gom mọi mốc thời gian thành một biến `finished`.

| Vòng đời | Bắt đầu | Mốc kết thúc | Điều cần đo |
| --- | --- | --- | --- |
| **Method invocation** | Caller đi vào join point | Method trả về stage/handle hoặc ném lỗi ngay | Thời gian chuẩn bị/schedule, lỗi đồng bộ |
| **Logical operation** | API nhận yêu cầu xử lý | Kết quả logic được xác định: success, failed, cancelled, timed-out, unknown | Trạng thái mà caller thấy |
| **Physical worker** | Task thực sự được worker nhận/chạy | Worker dừng và dọn tài nguyên | CPU/I/O/resource, cooperative cancellation |
| **Observation/export** | Advice ghi nhận event | Telemetry được xác nhận theo hợp đồng | Độ trễ xuất metric/span, dropped/export failures |

Ví dụ, caller hết hạn chờ ở `t=100 ms` nhưng worker gọi API từ xa vẫn tiếp tục đến `t=900 ms`. Nếu advice gắn nhãn “worker cancelled” tại `t=100 ms` thì sai: **hết hạn chờ** có thể chỉ là quyền chờ của caller, không chứng minh side effect đã dừng.

Một stage có thể kết thúc bằng `CompletionException` bao một nguyên nhân nghiệp vụ; một lần cancel có thể là exceptional completion của handle, trong khi cơ chế hủy vật lý được quản lý ở nơi khác. Hợp đồng phải nêu rõ **đang kiểm thử lớp nào** và **đếm terminal event của lớp nào**.

### 2.1. Những câu hỏi test phải trả lời

1. Aspect nhìn thấy điểm bắt đầu nào: invocation, submit, worker-start hay completion?
2. Khi method trả `CompletionStage`, advice có quan sát **kết quả cuối** hay chỉ quan sát object handle?
3. Context được lấy từ đâu, tại thời điểm nào, và có phải nguồn tin cậy không?
4. Callback completion chạy trên thread nào? Nếu chạy ngay trên thread gọi thì có kết quả khác không?
5. Có đường error synchronous trước khi tạo future, và đường error asynchronous sau khi future được trả?
6. Nếu timeout/cancel xảy ra, ai sở hữu resource, worker và cleanup?
7. Khi completion đua với timeout, điểm xác nhận terminal outcome có được thực hiện đúng một lần không?
8. Nếu exporter lỗi, result/exception gốc có bị thay đổi không?
9. Thread pool tái sử dụng thread có mang nhầm context của request trước không?
10. Tập join point được lựa chọn có bao trùm callback/worker thật hay chỉ API submit?

## 3. Context propagation là gì?

Một **context** là tập metadata có ý nghĩa đối với một logical operation. Thường gặp:

- **Trace ID / span context:** liên kết những đoạn công việc có quan hệ nhân quả.
- **Correlation / operation ID:** đối chiếu một request xuyên qua các bước.
- **Principal và tenant:** danh tính/scope đã được xác thực, nếu policy cho phép truyền.
- **Deadline/budget:** mốc hết hạn hoặc ngân sách thời gian còn lại.
- **Locale/time zone:** quy tắc biểu diễn nội dung.
- **Audit metadata:** nguồn lệnh và thông tin cần thiết để chứng minh hành vi.

Context **không phải** tập toàn bộ request body, access token, password hay đối tượng nghiệp vụ. Chỉ capture trường tối thiểu cần thiết, có quy tắc bảo mật và vòng đời rõ; tránh giữ reference lớn trong closure.

### 3.1. Ba thao tác không được thiếu

**Capture:** chụp đúng snapshot context tại ranh giới submit, khi caller còn nắm context hợp lệ.

**Install/propagate:** thiết lập context cho worker/callback trong phạm vi tác vụ; nếu caller không có context, chính sách phải là xóa/thiết lập rỗng hoặc báo lỗi rõ, **không kế thừa giá trị của request trước**.

**Restore/cleanup:** luôn phục hồi context trước đó bằng `finally`, kể cả task ném exception. Với executor dùng lại thread, bỏ qua bước này có thể gây **rò correlation ID hoặc tenant giữa hai tác vụ**.

~~~text
caller: ctx=A
    |
    +-- capture(A) --> enqueue --> worker: old ctx=X
                                      |
                                 install(A)
                                      |
                                task success/error
                                      |
                                  finally
                                      |
                                  restore(X)
~~~

Việc restore giá trị cũ, không chỉ `remove()` vô điều kiện, giúp giữ hành vi đúng khi một tác vụ chạy lồng trong một context hiện có. Nếu old context là `null`, việc `ThreadLocal.remove()` phù hợp hơn `set(null)` ở nhiều cấu trúc lưu context.

### 3.2. `ThreadLocal` không tự vượt thread

Trong Java, `ThreadLocal` lưu dữ liệu theo thread. Gán giá trị trên caller thread không khiến thread pool worker nhìn thấy cùng giá trị. `InheritableThreadLocal` không phải một giải pháp tổng quát cho pool: thread thường được tạo một lần rồi tái sử dụng, nên dữ liệu được thừa hưởng khi tạo thread có thể lỗi thời hoặc lộ sang request khác.

Callback của `CompletableFuture` cũng có thể chạy trên thread hoàn tất stage, thread đăng ký callback (khi stage đã hoàn tất), hoặc executor được chỉ định với biến thể `Async`. **Không kiểm thử bằng giả định một callback nhất định luôn chạy trên một thread xác định** nếu API không cam kết.

### 3.3. Context cần được định nghĩa theo hợp đồng

| Thuộc tính | Điều phải công bố | Ví dụ test |
| --- | --- | --- |
| Nguồn | Caller identity tin cậy hay header tùy ý? | Header giả không đổi principal xác thực |
| Snapshot | Capture ở submit hay lúc worker bắt đầu? | Caller đổi ctx sau submit, worker vẫn thấy snapshot |
| Truyền | Qua executor wrapper, callback hay tham số tường minh? | Nested async task nhận đúng trace |
| Isolation | Giữa request và tenant | A kết thúc, B không thấy context A |
| Restoration | Cleanup cuối tác vụ | Thread pool tái sử dụng có ctx rỗng |
| Retention | Context có giữ dữ liệu nhạy cảm không? | Không log token/secret; closure không giữ full request |
| Deadline | Mốc tuyệt đối hay duration mới? | Không vô tình reset timeout khi qua tầng |
| Error semantics | Không có context sẽ ra sao? | Theo contract, không âm thầm gán identity giả |

Đặc biệt với **principal/authorization**, propagation không tự đồng nghĩa “được phép”. Quyền có thể bị thu hồi trong lúc queue chờ; chính sách phải quy định kiểm tra ở submit, execution hay cả hai, và những nguồn dữ liệu tin cậy cần kiểm tra lại.

## 4. Thiết kế một ma trận kiểm thử bất đồng bộ

| Kịch bản | Context kỳ vọng | Outcome kỳ vọng | Điều không được xảy ra |
| --- | --- | --- | --- |
| Submit và hoàn tất bình thường | Snapshot của caller tại lúc submit | Một success event | Log success trước khi task thực sự xong |
| Tác vụ ném exception | Context vẫn đúng khi chạy/callback | Một failure event và lỗi gốc | Nuốt lỗi thành success |
| Method ném trước khi trả future | Context caller còn đúng | Failure đồng bộ theo API | Chờ một callback sẽ không bao giờ đến |
| Callback chạy trên thread khác | Context được truyền tường minh | Event đúng trace/operation | Dùng `ThreadLocal` trống hay từ request khác |
| Future đã hoàn tất trước khi gắn callback | Snapshot/metadata vẫn tồn tại | Callback quan sát đúng một lần | Giả định callback luôn chạy async |
| Caller timeout nhưng worker chưa dừng | Đúng context cho mỗi vòng đời | Caller timeout và worker còn có thể đang chạy | Ghi “worker stopped” giả |
| Hủy future | Theo contract propagation | Handle biểu thị cancelled | Tự cho rằng `cancel(true)` chắc chắn interrupt mọi worker |
| Completion đua timeout | Tính một terminal outcome cho logical operation | Một winner theo atomic state | Hai lần đóng span/metric |
| Executor từ chối submit | Caller context nguyên vẹn | Rejection đồng bộ/exceptional theo API | Rò context hoặc bỏ sót counter |
| Thread được tái sử dụng | Request B thấy B hoặc rỗng | Mỗi tác vụ độc lập | Request B thấy principal/tenant A |
| Async fan-out/fan-in | Context con đúng và có parent | Tổng hợp kết quả theo policy | Một nhánh lỗi làm mất cleanup nhánh khác |
| Exporter telemetry ném lỗi | Business outcome giữ nguyên theo contract | Lỗi quan sát được thống kê riêng | Thay lỗi nghiệp vụ bằng lỗi exporter |

Với mọi hàng, nêu **join point được advice chặn** và **đơn vị đo**. Việc test chỉ đếm số dòng log có thể bị nhiễu bởi sampling hoặc queue exporter: dùng một recorder không loại bỏ sự kiện trong môi trường kiểm thử.

## 5. Tách ba tầng test

### 5.1. Unit test logic advice

Cho một target giả trả future điều khiển được:

- Counter invocation tăng ngay tại method call.
- Counter terminal success/failure chỉ tăng khi stage hoàn tất.
- `proceed`/lời gọi mục tiêu chỉ diễn ra một lần trừ khi có policy retry tường minh.
- Giá trị trả về và nguyên nhân lỗi không bị đổi ngoài hợp đồng.
- Callback observer chỉ tác động telemetry và cleanup.
- Khi future chưa hoàn tất, không được báo success sớm.

Không cần thread thật cho mọi test. Một `CompletableFuture` tạo thủ công rồi gọi `complete`/`completeExceptionally` giúp điều khiển chính xác từng trạng thái.

### 5.2. Integration test executor và context

Dùng một executor thực có thread tái sử dụng; chụp context A, chạy request A, dọn context; tiếp tục context B, chạy B. Kiểm tra:

- B không thấy A.
- Dù A ném lỗi, thread được restore.
- Callback sau completion mang đúng correlation ID nhờ capture tường minh.
- Nếu tầng giữa tiếp tục `supplyAsync` hoặc `thenApplyAsync`, mỗi ranh giới cần propagation thích hợp.
- Shutdown hoặc rejection không làm task bị mất dấu mà không có trạng thái.

### 5.3. Integration test weaving/interception

Chạy với chính cơ chế AOP của ứng dụng:

- Pointcut chặn đúng **method execution/call** mong muốn.
- Với proxy, self-call có thể không đi qua wrapper; với execution weaving, nếu method được weave, self-call có thể vẫn được chặn.
- Nếu advice chỉ chạy quanh method trả future, test phải chứng minh callback quan sát terminal completion.
- Các lời gọi được tạo trong worker/executor có được chặn hay không phụ thuộc phạm vi weave và class loader.
- Cấu hình load-time weaving hoặc compiler khác có thể làm method không được chặn, dù logic unit advice vẫn xanh.

**Đừng lấy một bài test unit thành bằng chứng rằng mọi thread, mọi class và mọi class loader trong production đều đi qua aspect.**

## 6. Ví dụ Java 17+: truyền context và quan sát completion thật

Ví dụ chạy độc lập dùng:

1. `ThreadLocal<RequestContext>` để biểu diễn context theo thread.
2. `ContextExecutor` để capture tại `execute`, install khi worker chạy, restore bằng `finally`.
3. `TaskApi` trả `CompletionStage<String>` và thực thi nghiệp vụ bất đồng bộ trên worker.
4. `ObservingAdvice` dùng JDK dynamic proxy để chặn invocation, ghi completion callback và phân biệt success/error.
5. Một thread pool một worker được tái sử dụng để kiểm tra không rò context.

**Giới hạn của demo:** principal/trace ID là giá trị giả dùng trong test; hệ thống thật phải lấy từ nguồn xác thực tin cậy. Đây là một mô hình around advice cơ bản, không phải thư viện truyền context cho mọi loại scheduler/executor.

### 6.1. Mã hoàn chỉnh: `AsyncContextAspectDemo.java`

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncContextAspectDemo {
    record RequestContext(String traceId, String actor) { }

    private static final ThreadLocal<RequestContext> CONTEXT =
            new ThreadLocal<>();

    interface TaskApi {
        CompletionStage<String> handle(String command);
    }

    static final class ContextExecutor implements Executor {
        private final Executor delegate;

        ContextExecutor(Executor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable command) {
            RequestContext captured = CONTEXT.get();

            delegate.execute(() -> {
                RequestContext previous = CONTEXT.get();
                try {
                    if (captured == null) {
                        CONTEXT.remove();
                    } else {
                        CONTEXT.set(captured);
                    }
                    command.run();
                } finally {
                    if (previous == null) {
                        CONTEXT.remove();
                    } else {
                        CONTEXT.set(previous);
                    }
                }
            });
        }
    }

    static final class TaskService implements TaskApi {
        private final Executor executor;

        TaskService(Executor executor) {
            this.executor = executor;
        }

        @Override
        public CompletionStage<String> handle(String command) {
            return CompletableFuture.supplyAsync(() -> {
                RequestContext context = CONTEXT.get();
                if (context == null) {
                    throw new IllegalStateException("context missing");
                }

                System.out.println("worker trace=" + context.traceId()
                        + " actor=" + context.actor());

                if ("bad".equals(command)) {
                    throw new IllegalArgumentException("bad request");
                }
                return "accepted:" + command;
            }, executor);
        }
    }

    static final class ObservingAdvice implements InvocationHandler {
        private final TaskApi target;
        private final AtomicInteger successes = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();

        ObservingAdvice(TaskApi target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            RequestContext snapshot = CONTEXT.get();

            final Object value;
            try {
                value = method.invoke(target, args);
            } catch (InvocationTargetException ex) {
                // Loi xay ra truoc khi target tra future.
                throw ex.getCause();
            }

            @SuppressWarnings("unchecked")
            CompletionStage<String> stage = (CompletionStage<String>) value;

            // Tra stage sau callback: join cua stage nay doi observer xong.
            return stage.whenComplete((result, error) -> {
                try {
                    String trace = snapshot == null
                            ? "none" : snapshot.traceId();
                    if (error == null) {
                        successes.incrementAndGet();
                        System.out.println("observe trace=" + trace
                                + " outcome=ok");
                    } else {
                        failures.incrementAndGet();
                        System.out.println("observe trace=" + trace
                                + " outcome=error");
                    }
                } catch (RuntimeException observationFailure) {
                    // Loi telemetry khong duoc thay doi ket qua nghiep vu.
                }
            });
        }

        String summary() {
            return "successes=" + successes.get()
                    + " failures=" + failures.get();
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService raw = Executors.newSingleThreadExecutor();

        try {
            // Khoi tao worker va chung minh ThreadLocal khong tu truyen.
            CONTEXT.set(new RequestContext("t-0", "setup"));
            RequestContext unwrapped = raw.submit(CONTEXT::get).get();
            System.out.println("unwrapped context=" + unwrapped);
            CONTEXT.remove();

            TaskService target = new TaskService(new ContextExecutor(raw));
            ObservingAdvice advice = new ObservingAdvice(target);
            TaskApi observed = (TaskApi) Proxy.newProxyInstance(
                    TaskApi.class.getClassLoader(),
                    new Class<?>[] { TaskApi.class },
                    advice);

            CONTEXT.set(new RequestContext("t-1", "alice"));
            CompletionStage<String> ok = observed.handle("ok");
            CONTEXT.remove();
            System.out.println("caller=" + ok.toCompletableFuture().join());

            CONTEXT.set(new RequestContext("t-2", "bob"));
            CompletionStage<String> bad = observed.handle("bad");
            CONTEXT.remove();

            try {
                bad.toCompletableFuture().join();
            } catch (CompletionException ex) {
                System.out.println("caller error="
                        + ex.getCause().getMessage());
            }

            boolean cleared = raw.submit(() -> CONTEXT.get() == null).get();
            System.out.println("worker context cleared=" + cleared);
            System.out.println("observed " + advice.summary());
        } finally {
            CONTEXT.remove();
            raw.shutdownNow();
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 AsyncContextAspectDemo.java
java AsyncContextAspectDemo
~~~

**Stdout chính xác** của chương trình:

~~~text
unwrapped context=null
worker trace=t-1 actor=alice
observe trace=t-1 outcome=ok
caller=accepted:ok
worker trace=t-2 actor=bob
observe trace=t-2 outcome=error
caller error=bad request
worker context cleared=true
observed successes=1 failures=1
~~~

Tại sao output có tính xác định trong demo:

- `raw` là pool một worker; từng lời gọi được `join` trước khi gửi lời gọi tiếp.
- Callback `whenComplete` nằm trong stage được trả cho caller, nên khi `join` của stage dẫn xuất hoàn tất, callback đã chạy.
- Dù stage nghiệp vụ hoàn tất cực nhanh trước khi advice đăng ký callback, `whenComplete` vẫn chạy khi đăng ký; callback dùng **snapshot tường minh** thay vì đọc `ThreadLocal` của thread bất kỳ.
- Test `raw.submit(() -> CONTEXT.get() == null)` chạy trên cùng worker sau hai tác vụ, xác minh bước restore đã dọn context.
- Với đường lỗi, `CompletionException` là wrapper phát sinh khi `join`; test kiểm tra nguyên nhân `IllegalArgumentException`, không đổi lỗi nghiệp vụ thành success.

**Hai lưu ý production rất quan trọng:**

1. Advice đang trả một **stage dẫn xuất** từ `whenComplete`, không trả cùng object handle gốc. Cách này giúp xác nhận callback đã chạy trước khi caller thấy completion, nhưng có thể thay đổi semantics của **cancellation**, identity và propagation. Muốn bảo toàn chính xác hợp đồng `Future`/`CompletionStage`, phải quyết định rõ có được bọc stage hay chỉ gắn observer lên stage gốc. Không ngầm giả định `cancel(true)` trên stage dẫn xuất sẽ dừng upstream worker.
2. Callback trong `whenComplete` có thể chạy trên worker thread hoặc ngay trên thread gọi nếu stage đã hoàn tất. Callback làm I/O/serialize nặng sẽ ảnh hưởng latency. Demo chỉ dùng `println`; trong hệ thống thật nên có sink/queue hữu hạn và kiểm soát lỗi telemetry. `catch RuntimeException` chỉ bao vùng quan sát, không nuốt lỗi nghiệp vụ.

Demo `TaskService` chỉ minh họa **một bước submit**. Nếu worker tạo một nhiệm vụ mới ở executor khác, context cần được capture/propagate tại **ranh giới mới**; wrapper ở executor đầu không làm `ThreadLocal` tự lan truyền qua mọi scheduler.

## 7. Kiểm thử không truyền context và truyền sai context

Một bài test chỉ thấy request A chạy đúng chưa bảo đảm không rò dữ liệu.

### 7.1. Negative test: bỏ wrapper

Nếu thay `new ContextExecutor(raw)` bằng `raw` khi tạo `TaskService`, worker thường đọc `CONTEXT.get() == null`, dẫn đến exception “context missing”. Hành vi ấy cho thấy context không tự vượt ranh giới thread.

Không viết production code tự động thay `null` bằng principal mặc định có quyền cao. Nếu context là bắt buộc, hệ thống phải fail theo contract hoặc nhận context đúng từ một đường tin cậy.

### 7.2. Two-request isolation test

Một phép kiểm chứng mạnh cho thread pool tái sử dụng:

~~~text
A: caller ctx trace-A, tenant-A
  -> worker nhận trace-A/tenant-A
  -> task throws
  -> finally restore previous context

B: caller ctx trace-B, tenant-B
  -> worker nhận trace-B/tenant-B
  -> task succeeds
  -> finally restore previous context

C: task không có context
  -> worker không được thấy A hay B
~~~

Case A phải cố ý ném lỗi **bên trong worker**, vì leak thường xảy ra khi logic exception bỏ qua cleanup. Cần chạy tất cả case trên cùng một worker hoặc điều phối pool để chắc chắn thread cũ thực sự được dùng lại. Với pool nhiều thread, một test ngẫu nhiên chưa chắc chạy đúng thread từng giữ context cũ.

### 7.3. Capture theo snapshot chứ không tham chiếu mutable request

Nếu closure giữ tham chiếu tới một object context mutable, caller sửa tenant/trace ID sau submit có thể thay đổi ý nghĩa operation đang queue. Ưu tiên immutable record/value object; nếu metadata phức tạp, snapshot phải thật sự tách khỏi dữ liệu mutable.

Nếu contract đòi context sống và tự cập nhật (chẳng hạn quyền bị thu hồi), đó là một vấn đề khác: cần **policy revalidation**, không giải quyết bằng cách chia sẻ mutable context không đồng bộ giữa thread.

## 8. Callback đã hoàn tất trước khi được gắn

Một race phổ biến:

~~~text
Thread A: method trả CompletionStage
Thread B: stage complete ngay lập tức
Thread A: advice gắn whenComplete(...)
~~~

Các toán tử `CompletionStage.whenComplete` vẫn quan sát stage đã hoàn tất; callback có thể chạy đồng bộ trên thread đang đăng ký. Vì vậy test phải bao phủ cả:

- **Pending stage:** advice đăng ký trước, test sau đó gọi `complete(...)`.
- **Already completed:** target trả `CompletableFuture.completedFuture(value)`.
- **Already exceptionally completed:** target trả stage đã lỗi.
- **Completion đồng thời với registration:** callback được tính đúng một lần bất kể bên nào thắng race.

Điều không được làm là kiểm tra `if (!stage.isDone())` rồi mới đăng ký callback; stage có thể hoàn tất ngay sau bước kiểm tra và trước đăng ký. Đăng ký callback trực tiếp qua API đã có cơ chế xử lý race phù hợp.

Nếu callback mở span mới từ `ThreadLocal`, test sẽ lộ việc context không tồn tại khi callback chạy trên thread không mong muốn. Một cách ổn định là **capture metadata cần thiết ngay trong advice** và truyền vào callback; nếu cần thiết lập ThreadLocal cho code trong callback, thực hiện scoped install/restore bằng `try/finally`.

## 9. Timeout, cancellation và completion là các sự kiện khác nhau

### 9.1. Timeout của caller không chứng minh worker dừng

Giả sử một lệnh gọi thanh toán đã được gửi tới hệ thống bên ngoài. Caller timeout có thể xảy ra sau khi bên ngoài đã thực hiện side effect nhưng trước khi phản hồi quay lại. Một aspect ghi “giao dịch chắc chắn thất bại và được rollback” ở mốc timeout có thể làm caller retry sai và nhân đôi side effect.

Kiểm thử phải phân biệt:

- **Deadline reached:** ngân sách chờ đã hết.
- **Logical operation timed out:** API đánh dấu kết quả là timeout.
- **Cancellation requested:** có tín hiệu yêu cầu hủy.
- **Worker observed cancellation:** worker đã đọc tín hiệu.
- **Worker physically stopped:** mã chạy nền thực sự ngừng và cleanup xong.
- **Outcome unknown:** chưa đủ bằng chứng để khẳng định side effect thành công/thất bại.

`CompletableFuture.cancel(boolean)` là một cơ chế biểu diễn cancellation của future. Với `CompletableFuture` chuẩn, tham số `mayInterruptIfRunning` **không biến cancellation thành bảo đảm interrupt worker**. Hành vi ngắt/dừng tùy cơ chế thực thi và chính sách hợp tác của task.

### 9.2. Ví dụ Java 17+ thứ hai: kiểm thử terminal winner có kiểm soát

Ví dụ tiếp theo **không giả lập một thread worker thực đang chạy**. Nó dùng hai future độc lập làm mô hình cho “kết quả mà caller quan sát” và “kết quả công việc vật lý” để kiểm chứng sự khác biệt và bảo đảm chỉ một lần hoàn tất logical handle.

Tạo file `AsyncTerminalStateDemo.java`:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncTerminalStateDemo {
    public static void main(String[] args) {
        CompletableFuture<String> caller = new CompletableFuture<>();
        CompletableFuture<String> worker = new CompletableFuture<>();
        AtomicInteger terminalEvents = new AtomicInteger();

        caller.whenComplete((value, error) ->
                terminalEvents.incrementAndGet());

        boolean timeoutWon = caller.completeExceptionally(
                new TimeoutException("deadline"));
        boolean lateResultAccepted = caller.complete("too late");

        System.out.println("timeout won=" + timeoutWon);
        System.out.println("late caller result accepted=" + lateResultAccepted);
        System.out.println("logical terminal events=" + terminalEvents.get());
        System.out.println("worker done at timeout=" + worker.isDone());

        worker.complete("late worker result");
        System.out.println("worker result=" + worker.join());

        CompletableFuture<String> cancelHandle = new CompletableFuture<>();
        boolean cancelAccepted = cancelHandle.cancel(true);
        System.out.println("cancel accepted=" + cancelAccepted);
        System.out.println("cancelled flag=" + cancelHandle.isCancelled());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 AsyncTerminalStateDemo.java
java AsyncTerminalStateDemo
~~~

**Stdout chính xác:**

~~~text
timeout won=true
late caller result accepted=false
logical terminal events=1
worker done at timeout=false
worker result=late worker result
cancel accepted=true
cancelled flag=true
~~~

Điểm cần rút ra:

- `completeExceptionally` thắng trạng thái của future caller; `complete("too late")` không thể ghi đè một completion đã quyết định.
- `whenComplete` ghi nhận **một terminal completion** ở caller future.
- Worker future riêng vẫn chưa hoàn tất tại mốc caller timeout, sau đó có thể hoàn tất với kết quả muộn. Đây là mô hình hóa trạng thái, **không phải bằng chứng có worker thread hay I/O đã chạy**.
- `cancel(true)` thay đổi trạng thái của handle trong ví dụ; không chứng minh worker thực tế đã bị ngắt.

Đối với một implementation thực, phải kiểm thử riêng **cầu nối giữa logical timeout/cancel và tín hiệu hủy vật lý** bằng executor, cancellation token, interrupt handling, tài nguyên I/O và cleanup được quản lý.

## 10. Xử lý race giữa success, failure, timeout và cancellation

Khi nhiều nguồn có thể báo terminal outcome, test nên kiểm tra quy tắc **single-winner**:

~~~text
          PENDING
         /   |   \
     SUCCESS ERROR TIMEOUT
         \   |   /
       one terminal decision
             |
       close metrics/span ONCE
~~~

Sơ đồ giản lược không khẳng định mọi hệ thống chỉ có ba trạng thái; có thể có `CANCELLED`, `REJECTED` hoặc `UNKNOWN`. Một triển khai thường dùng **state machine** và thao tác atomic như `compareAndSet` để chọn đúng một lần kết thúc logical operation.

Mã giả minh họa:

~~~java
// Đoạn minh họa, không phải chương trình độc lập.
if (state.compareAndSet(PENDING, TERMINAL)) {
    publishLogicalOutcome(outcome);
    closeLogicalSpan();
}
~~~

Nếu timeout đã được công nhận, một success đến muộn không được tạo một logical terminal event thứ hai. Tuy vậy, hệ thống vẫn cần theo dõi **physical late completion** hoặc reconciliation riêng nếu side effect có ý nghĩa.

Kiểm thử nên điều phối hai nhánh đua có chủ đích bằng `CountDownLatch`/`Phaser`, chạy nhiều vòng và assert: chỉ một winner, một event logical, không leak resource. Đừng dùng `Thread.sleep` rồi cho rằng hai thread chắc chắn chạy theo thứ tự dự đoán.

### 10.1. Những case race quan trọng

| Race | Kỳ vọng cần xác minh |
| --- | --- |
| Success và timeout cùng lúc | Một logical terminal outcome; late outcome xử lý theo policy |
| Failure và cancellation cùng lúc | Không xuất cả “success” lẫn “cancelled” cho cùng logical operation |
| Cancellation trước submit | Không cấp context/resource khi không còn việc cần chạy |
| Cancellation sau submit, trước worker-start | Tác vụ được bỏ hoặc nhận cancel theo executor contract |
| Cancellation khi worker đang cleanup | Không đóng tài nguyên hai lần; cleanup an toàn và idempotent |
| Timeout rồi caller retry | Idempotency/budget không cho nhân side effect ngoài hợp đồng |
| Completion rồi callback đăng ký muộn | Observer vẫn nhìn thấy terminal outcome một lần |
| Callback telemetry bị lỗi | Không làm thay đổi outcome nghiệp vụ theo hợp đồng |

Nếu API cung cấp `orTimeout` hoặc `completeOnTimeout`, phải kiểm tra **future nào được sửa/hoàn tất** và **worker có còn chạy không**. Không suy luận trực tiếp về rollback hay interrupt từ tên một toán tử thời gian.

## 11. Context propagation khi có chuỗi stage và fan-out/fan-in

### 11.1. Stage chains

Xét:

~~~java
// Đoạn minh họa, không phải chương trình độc lập.
CompletionStage<Result> stage = loadAsync(id)
    .thenApply(this::validate)
    .thenApplyAsync(this::enrich, anotherExecutor)
    .thenCompose(this::writeAsync);
~~~

Các bước có thể chạy trên nhiều thread hoặc executor. Nếu `validate` dựa vào `ThreadLocal` của worker trước đó, còn `enrich` chuyển qua executor mới, context có thể mất. Kiểm thử phải đánh dấu từng bước bằng trace ID và caller context snapshot, rồi xác nhận ranh giới nào có trách nhiệm capture/restore.

Đừng gọi `ThreadLocal.get()` ở callback và cho rằng nó luôn thấy context của request khi callback được đăng ký. Với `thenApply` không có `Async`, callback có thể chạy ở thread hoàn tất stage trước; nếu stage đã hoàn tất, callback có thể được thực thi ngay khi đăng ký. Hợp đồng context của callback phải độc lập với giả định lịch chạy.

### 11.2. Fan-out / fan-in

Một operation có thể gửi hai tác vụ con và đợi tổng hợp:

~~~text
              parent trace
                  |
              submit work
               /       \
         child A       child B
               \       /
                aggregate
                   |
              terminal result
~~~

Test cần:

- Cả child A/B nhận đúng parent trace nhưng có identity/span con riêng nếu chính sách tracing yêu cầu.
- B có lỗi không làm dọn resource của A sai, kể cả khi policy cancel các nhánh còn lại.
- Parent hoàn tất theo chính sách tập hợp (all-success, partial-success, fail-fast, quorum...); không giả định một policy mặc định cho mọi use case.
- Quá hạn của parent không tự động chứng minh cả child đã dừng.
- Khi một child chậm hoặc bị hủy, context của nó phải được restore khi worker thực sự hoàn tất.
- Event của child được liên kết đúng correlation ID mà không dùng mutable global context.

Nếu dùng `CompletableFuture.allOf`, phải hiểu rằng aggregate future chỉ cung cấp thông tin tổng hợp theo semantics API; không tự hủy mọi child còn lại. Muốn cooperative cancellation, cần thiết kế và test đường truyền yêu cầu hủy riêng.

## 12. Kiểm thử lỗi đồng bộ và lỗi bất đồng bộ

Hai đường lỗi khác nhau:

**Lỗi đồng bộ khi tạo stage:** `TaskApi.handle()` ném ngay do input không hợp lệ, thiếu executor hoặc lỗi submit. Around advice nhìn thấy exception tại method execution. Nếu advice chỉ xử lý `stage.whenComplete` sau khi method return, đường này sẽ không có stage để gắn callback.

**Lỗi bất đồng bộ khi worker hoàn tất:** method đã trả stage; worker ném, future hoàn tất exceptionally. Advice không thể dựa vào `catch` quanh riêng lời gọi method để thấy lỗi này. Nó phải quan sát stage completion hoặc ranh giới worker thực.

Test phải xác minh counters phân biệt:

- `invocations_total`: mọi lời gọi đi vào pointcut.
- `submission_failures_total`: lỗi ngay lúc chuẩn bị/submit (nếu API phân biệt).
- `operations_succeeded_total`: logical operation thành công.
- `operations_failed_total`: logical operation lỗi.
- `operations_timed_out_total` và `operations_cancelled_total`: theo state machine có semantics riêng.
- `physical_workers_stopped_total`: chỉ tăng khi worker thực sự dừng nếu cần đo.

Không cộng các counter trên như thể tất cả đều là tập rời nhau nếu có thao tác đồng thời vừa timeout logical vừa worker kết thúc muộn. Hãy chọn đơn vị đo, định nghĩa mutually-exclusive terminal labels và dùng nhãn/cardinality thấp.

### 12.1. Truyền exception đúng nghĩa

`CompletableFuture.join()` thường ném `CompletionException` khi future hoàn tất exceptionally; `get()` có thể dùng `ExecutionException`. Advice/metric cần phân biệt wrapper với **root cause** để ghi mã lỗi có ý nghĩa, nhưng không được làm mất stack/cause khi truyền lỗi cho caller.

Không chuyển `CancellationException` thành lỗi nghiệp vụ “invalid input”. Không ghi exception message không kiểm soát vì nó có thể chứa secret. Với `whenComplete`, một observer ném exception có thể làm stage dẫn xuất hoàn tất khác với điều mong muốn; hãy test việc exporter hỏng và bảo toàn outcome theo thiết kế.

## 13. Ownership, cleanup và chống rò context

Khi một aspect thêm callback, context wrapper hoặc deadline scheduler, nó cũng có trách nhiệm quản lý tài nguyên **do chính nó tạo ra**.

| Tài nguyên | Chủ sở hữu khả dĩ | Cách test cleanup |
| --- | --- | --- |
| ThreadLocal context được install bởi wrapper | Wrapper/scope helper | Sau success/error/cancel, worker thấy giá trị cũ hoặc null |
| Span logical operation | Observer lifecycle | Span đóng một lần đúng terminal outcome |
| Scheduled timeout handle | Deadline owner | Hủy timer khi operation đã kết thúc, không tích tụ |
| CompletableFuture listener/closure | Stage/observer | Không giữ full request/payload lớn vô hạn |
| Database connection | Worker/transaction boundary | Chỉ đóng khi resource không còn được dùng, không ở caller timeout giả |
| Executor/task queue | Service runtime | Shutdown/reject/bounded queue theo chính sách |
| Physical cancellation token | Worker owner | Signal được nhận, task cleanup hoàn tất hoặc bị đánh dấu còn chạy |

Không được đóng connection tại `finally` của advice bao quanh method trả stage nếu worker vẫn đang sử dụng connection đó. Tương tự, một callback chạy khi caller timeout không thể tùy tiện giải phóng buffer mà worker vẫn đọc.

### 13.1. Bẫy restore context

Sai:

~~~java
// Minh họa anti-pattern, không phải chương trình độc lập.
CONTEXT.set(captured);
command.run();
CONTEXT.remove(); // Bỏ qua nếu command ném exception.
~~~

Đúng về cấu trúc:

~~~java
// Minh họa cấu trúc, cần khai báo CONTEXT/captured/command.
Context previous = CONTEXT.get();
try {
    CONTEXT.set(captured);
    command.run();
} finally {
    if (previous == null) CONTEXT.remove();
    else CONTEXT.set(previous);
}
~~~

Khi `captured == null`, một helper tổng quát có thể dùng `remove()` thay vì `set(null)`. Test cả trường hợp `previous` có giá trị khi tác vụ chạy lồng nhau, không chỉ thread trống.

## 14. Kiểm thử bằng fake clock, latch và recorder

Các test phụ thuộc `sleep(50)`, timeout 100 ms và “hy vọng callback đã chạy” thường flaky trong CI. Một phương án tốt hơn:

1. **Fake clock / injected ticker:** điều khiển deadline mà không chờ thời gian thật. Với thời lượng trong một tiến trình, dùng nguồn đồng hồ đơn điệu như `System.nanoTime()` hoặc ticker trừu tượng.
2. **Manual executor:** gom `Runnable` vào queue, cho test quyết định khi nào chạy; hữu ích để kiểm tra capture-at-submit, cleanup và rejection.
3. **Controllable future:** giữ `CompletableFuture` pending, rồi chủ động `complete`/`completeExceptionally`/`cancel` ở đúng thời điểm.
4. **Latch/barrier:** với integration test có thread thật, dừng worker ở một mốc xác định để tạo race.
5. **Deterministic recorder:** ghi event (operation ID, thread, lifecycle, outcome), không lọc/sampling.
6. **Fake telemetry sink:** cố ý ném lỗi, chặn, trả chậm hoặc đầy queue để kiểm tra isolation với nghiệp vụ.
7. **Constrained executor:** dùng một worker để xác minh tái sử dụng thread, nhiều worker để kiểm tra concurrent isolation.

Một trình tự test capture-at-submit không dùng sleep:

~~~text
1. Khóa manual executor ở trạng thái chưa chạy task.
2. Caller thiết lập context A và submit.
3. Caller đổi sang context B hoặc xóa context.
4. Cho manual executor chạy task đã xếp.
5. Assert task thấy snapshot A.
6. Assert context caller và executor được restore đúng.
~~~

Một trình tự test timeout-vs-success:

~~~text
1. Tạo pending operation và recorder đếm terminal events.
2. Dùng barrier giữ worker trước điểm complete.
3. Kích hoạt timeout bằng fake scheduler.
4. Thả worker để complete success muộn.
5. Assert chỉ một logical terminal event, nhưng physical event
   có thể xảy ra sau; không rò resource.
6. Đảo thứ tự: success trước, timeout đến muộn.
7. Lặp dưới nhiều interleaving, không dựa vào sleep.
~~~

**Chú ý:** các mô hình trên là kế hoạch test, không phải code chạy độc lập. Các API như fake scheduler/manual executor là abstraction bạn cần tự cung cấp hoặc chọn từ bộ công cụ test; JDK không tự có một fake clock dùng chung cho mọi scheduler.

## 15. Khía cạnh bảo mật của context propagation

### 15.1. Context không phải giấy ủy quyền vĩnh viễn

Nếu một background task được submit khi principal có quyền, nhưng quyền bị thu hồi trước khi worker thực thi, quyết định nào có hiệu lực? Không có đáp án chung cho mọi nghiệp vụ.

Hợp đồng có thể chọn:

- **Authorization tại submit:** cho phép task thực thi với quyền được xác nhận ở thời điểm chấp nhận lệnh, nếu mô hình nghiệp vụ cho phép.
- **Authorization tại execution:** kiểm tra lại quyền tại lúc worker thực sự truy cập tài nguyên.
- **Hai tầng kiểm tra:** lọc từ sớm rồi xác thực resource/tenant và trạng thái hiện tại ở operation boundary.

Aspect propagation chỉ chuyển context; **authorization policy** và nguồn quyền tin cậy phải được kiểm thử riêng. Không tự cấp default admin khi worker không có principal.

### 15.2. Không rò tenant qua thread pool

Một worker xử lý dữ liệu tenant A rồi tenant B là tình huống nhạy cảm hơn mất trace ID. Nếu `ThreadLocal<TenantContext>` không được reset, câu truy vấn tiếp theo có thể thực thi dưới tenant sai.

Test phải ép **A -> B -> request không context** trên cùng thread, bao gồm A thất bại. Đối với dữ liệu thực, thêm kiểm tra quyền ở tầng tin cậy/datastore; chỉ dựa vào ThreadLocal ở aspect dễ bị bypass bởi code không qua wrapper.

### 15.3. Tránh context chứa secret

Không đẩy access token thô hoặc toàn bộ request object vào closure/future sống lâu chỉ để log correlation ID. Context nên nhỏ, bất biến, chỉ chứa metadata cần thiết với chính sách retention và logging rõ ràng. Nếu principal được truyền, bảo đảm người đọc telemetry không nhận thông tin quá phạm vi.

## 16. Advice order, retry và context trong một logical operation

Một hệ thống có thể áp nhiều aspect:

~~~text
Tracing -> Retry -> Context -> Task
~~~

hoặc:

~~~text
Retry -> Tracing -> Context -> Task
~~~

Thứ tự quyết định:

- Trace đại diện **một logical operation** hay **mỗi attempt**.
- Deadline bị reset trên mỗi attempt hay là một ngân sách tuyệt đối chung.
- Context được capture một lần hay từng attempt.
- Metrics đếm logical calls, attempts, failures hay cancellations.
- Callback ở nhánh nào có quyền kết thúc span và khi nào.

Với retry của tác vụ bất đồng bộ, **mỗi attempt có thể hoàn tất ở thời điểm khác nhau**. Nếu attempt 1 bị timeout nhưng worker vẫn chạy, attempt 2 có thể nhân side effect. Việc dùng context giống nhau không tự bảo đảm idempotency; cần idempotency key và contract operation phù hợp.

Một integration test cần chủ động làm attempt 1 chậm, khởi chạy attempt 2 theo policy, rồi hoàn tất attempt 1 muộn. Kiểm tra rằng chỉ một logical terminal outcome được công bố, metric attempts phản ánh số lần chạy thật và side effect không bị lặp trái hợp đồng.

## 17. Kiểm tra tính đúng của pointcut bất đồng bộ

Một pointcut có thể chọn method `enqueue(...)`, `submit(...)` hoặc `process(...)`; chúng có semantics khác nhau. Nếu aspect “đo toàn bộ xử lý” nhưng pointcut chỉ chặn `enqueue`, nó sẽ chỉ đo thời gian đưa task vào queue.

Hãy lập bảng cho từng boundary:

| Join point / method | Mục đích | Kỳ vọng |
| --- | --- | --- |
| `submitTask` | Đo thời gian tiếp nhận | Trả handle được ghi nhận, không ghi business success |
| `worker.executeTask` | Đo thời gian physical execution | Worker-start/worker-stop đúng thread |
| `CompletionStage` terminal callback | Đo logical outcome | Success/error/cancel theo contract |
| `executor.execute` | Capture/install context khi submit | Không giữ nhầm context |
| `thenApplyAsync`/nested submit | Propagate sang executor mới | Context đúng tại callback tiếp theo |
| `timeout watcher` | Phát deadline state | Không đóng tài nguyên vật lý quá sớm |

Với AOP dựa trên proxy, lời gọi nội bộ trực tiếp có thể bypass. Với weaving tại method execution, self-call vào method đã weave vẫn có thể kích hoạt advice. Nhưng cả hai **không tự chặn một callback của thư viện khác** nếu điểm đó ngoài phạm vi instrumentation. Kiểm thử theo cơ chế thực tế, không giả định advice có mặt ở mọi đường chạy.

Một câu hỏi quan trọng: “Sau khi `processAsync` trả future, aspect còn giữ được context để quan sát completion không?” Nếu không, callback có thể không gắn được đúng operation. Test bằng cách xóa context caller ngay sau submit và hoàn tất task về sau.

## 18. Metric, tracing và audit phải có định nghĩa riêng

Đối với một trace:

- **Span invocation:** bắt đầu tại method entry, kết thúc lúc trả handle.
- **Span logical operation:** bắt đầu tại yêu cầu và kết thúc theo logical terminal outcome.
- **Span worker:** theo vòng đời vật lý của task.
- **Span attempt:** một lần thử của retry policy.

Không nên tạo một span duy nhất rồi đánh dấu success tại mọi mốc. Nếu span logic đã đóng do timeout, worker muộn có thể phát span/metric **physical late completion** riêng, không mở lại span đã đóng.

Đối với metric:

~~~text
async_calls_total               -> số lời gọi nhận được
async_submit_failures_total     -> lỗi ở bước submit
async_logical_completed_total   -> logical outcome theo nhãn
async_worker_finished_total     -> worker thực sự hoàn tất
async_context_missing_total     -> thiếu context tại boundary cần có
async_observer_failures_total   -> lỗi telemetry/export riêng
~~~

Không có nghĩa tất cả metric đều cần thiết; chọn những metric giúp trả lời câu hỏi vận hành cụ thể và có cardinality thấp. Đừng gắn trace ID hay principal vào labels metric có thể bùng nổ số chuỗi thời gian.

Nếu **audit event** có yêu cầu bảo đảm đầy đủ và bền vững, không thay bằng log debug có sampling hoặc callback dễ bị bỏ khi tiến trình chết. Việc audit thành công hay thất bại có thể ảnh hưởng hợp đồng nghiệp vụ theo chính sách riêng; đừng áp cùng quy tắc “telemetry error bị bỏ” một cách mù quáng.

## 19. Sai lầm thường gặp

| Sai lầm | Hậu quả | Cách sửa/test |
| --- | --- | --- |
| `after returning` đồng nghĩa nghiệp vụ xong | Báo success trước async failure | Test stage pending và completion |
| `ThreadLocal` tự lan qua executor | Worker thiếu/mang sai context | Test raw executor vs context wrapper |
| Không restore trong `finally` | Leak tenant/trace khi tái sử dụng thread | Test A lỗi rồi B chạy cùng worker |
| Callback luôn chạy trên worker | Flaky hoặc mất context nếu stage đã complete | Test pending/already-completed stage |
| Future timeout nghĩa worker dừng | Giải phóng resource sớm, side effect muộn | Test logical vs physical lifecycle |
| `cancel(true)` chắc chắn interrupt worker | Báo hủy sai | Kiểm tra cơ chế cancellation thực |
| Dùng `sleep` để tạo race | Test không ổn định | Manual executor, latch, fake scheduler |
| Dùng một boolean không atomic cho terminal state | Double metric/span close khi đua | Atomic transition và test interleavings |
| Advice trả stage dẫn xuất mà không xét cancel | Khác contract Future gốc | Test identity/cancel propagation |
| Callback observer ném lỗi | Có thể thay outcome stage dẫn xuất | Bắt lỗi telemetry trong vùng hẹp |
| Dùng context mutable chia sẻ | Request đổi context sau submit ảnh hưởng worker | Immutable snapshot và test thay sau submit |
| Mọi async step dùng cùng executor ngầm | Mất context ở nested stage | Test từng submit/thenApplyAsync boundary |
| Log toàn bộ context | Rò principal/token/secret | Allowlist/redaction, test negative |
| Retry lệnh ghi sau timeout không rõ trạng thái | Nhân đôi side effect | Test idempotency và late completion |
| Chỉ test advice nhưng không test weaving | Worker/callback không được chặn ngoài ý định | Integration test artifact/runtime thật |
| Counter lỗi đồng bộ = lỗi async | Sai error rate và thời gian | Tách submission failure và terminal failure |

## 20. Checklist kiểm thử trước khi phát hành

1. **Chọn join point đúng:** đang chặn method entry/return, task start, hay terminal completion? Có đường bypass không?
2. **Nêu rõ completion contract:** result, exception, timeout, cancellation và unknown outcome được định nghĩa thế nào?
3. **Capture-at-submit:** context được chụp đúng lúc và bất biến trong lúc queue chờ?
4. **Propagation đủ mọi boundary:** executor đầu, nested executor, callback async, scheduled job và fan-out/fan-in?
5. **Cleanup bảo đảm:** có `finally`, restore previous, test reuse thread cả success/error/cancel?
6. **Context bảo mật:** principal/tenant đáng tin, không có default privilege và không ghi dữ liệu nhạy cảm?
7. **Observer đúng mốc:** counter logical outcome chỉ tăng khi completion, không phải lúc trả handle?
8. **Already-completed stage:** callback gắn muộn vẫn chạy đúng một lần và không lệ thuộc thread cụ thể?
9. **Race được kiểm soát:** completion vs timeout/cancel có single-winner, không double-close?
10. **Timeout khác dừng vật lý:** tài nguyên chỉ dọn khi owner/worker không còn dùng, có kiểm thử late completion?
11. **Exception nguyên vẹn:** synchronous rejection, asynchronous failure, CompletionException/cancellation phân loại rõ?
12. **Result stage semantics:** nếu thay stage gốc bằng derived stage, cancellation/identity và propagation có phù hợp API contract?
13. **Tải và lỗi telemetry:** exporter chậm/hỏng không làm phá business outcome, queue có giới hạn?
14. **Composition:** retry, transaction, tracing và context advice có thứ tự đã được kiểm thử?
15. **CI ổn định:** fake clock, manual executor, latch và recorder; không dùng sleep ngẫu nhiên?
16. **Kiểm thử runtime thực:** đúng classpath, weaving/proxy, class loader, async executor và cấu hình deploy?

**Kết luận:** kiểm thử aspect bất đồng bộ không chỉ là xác nhận “advice có chạy”. Phải chứng minh **điểm bắt đầu/hoàn tất được hiểu đúng, context được capture và restore qua mọi ranh giới thực thi, lỗi/hủy/timeout không bị đánh đồng, và nhiều sự kiện đua nhau không gây mất dữ liệu hay đóng tài nguyên hai lần**. Một bộ test có điều khiển state/future/executor sẽ phát hiện các lỗi ấy đáng tin hơn các test chỉ dựa vào log và thời gian ngủ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-testing-009">Kiểm thử hồi quy khi cấu trúc mã thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử hồi quy khi cấu trúc mã thay đổi trong AOP

## 1. Vì sao refactor có thể làm aspect hỏng mà nghiệp vụ vẫn chạy đúng?

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, **AOP**), một **aspect** áp dụng lời khuyên (**advice**) tại các **join point** được một **pointcut** lựa chọn. Khi cấu trúc mã thay đổi — chuyển lớp sang package khác, đổi tên phương thức, thêm overload, tách helper, thay interface, thay cơ chế tạo đối tượng hoặc chuyển xử lý sang tác vụ bất đồng bộ — tập join point thực tế có thể thay đổi dù chức năng nghiệp vụ nhìn từ ngoài gần như giữ nguyên.

Ví dụ trước khi refactor, một pointcut chọn mọi lần thực thi phương thức public của kiểu OrderService. Sau khi tách logic đặt hàng sang OrderCoordinator và một helper khác, advice có thể:

1. **Không chạy ở chỗ cần chạy:** operation mới bị bỏ khỏi pointcut.
2. **Chạy thêm ở chỗ không được phép:** wildcard chọn cả helper, exporter hoặc phương thức mới, làm ghi log hai lần hoặc mở transaction thừa.
3. **Đổi số lần chạy:** một lời gọi nghiệp vụ trở thành hai lời gọi con cùng match pointcut.
4. **Đổi thứ tự thực thi:** thay mô hình ủy quyền hoặc precedence làm retry, transaction và recorder lồng nhau khác trước.
5. **Đổi thời điểm kết thúc:** trước đây method chạy đồng bộ, sau refactor method trả Future nên advice ghi thành công quá sớm.
6. **Bỏ sót đường gọi mới:** một entry point, callback hoặc đối tượng tạo trực tiếp không đi qua cơ chế interception.
7. **Làm đổi ngoại lệ, tác dụng phụ hoặc context:** advice vô tình retry thao tác không idempotent, nuốt lỗi hoặc giữ dữ liệu của lời gọi trước.

**Kiểm thử hồi quy aspect** nhằm phát hiện những thay đổi ngoài ý định này bằng cách xác nhận *hợp đồng quan sát và thực thi* vẫn đúng sau refactor. Nó khác với việc chỉ kiểm tra rằng phương thức nghiệp vụ vẫn trả giá trị mong đợi.

Bài này tập trung vào nguyên tắc AOP cơ bản và nâng cao, độc lập với một hệ sinh thái ứng dụng cụ thể. Ví dụ Java 17+ mô phỏng interception qua Java Dynamic Proxy; khi dùng kỹ thuật weaving khác cần chạy thêm kiểm thử tích hợp trên công cụ thực tế.

## 2. Hợp đồng của aspect phải được mô tả trước khi sửa mã

Không thể làm regression testing tốt nếu kỳ vọng chỉ là “có vẻ vẫn chạy”. Trước khi refactor, hãy ghi lại **hợp đồng AOP** ở các cấp sau:

| Cấp | Câu hỏi | Ví dụ tiêu chí |
| --- | --- | --- |
| Business operation | Một thao tác logic là gì? | Một yêu cầu đặt hàng được xem là một operation |
| Join point được chọn | Method-call, method-execution hay vị trí khác nào? | Thực thi entry point của nghiệp vụ |
| Advice bắt buộc | Tại điểm nào advice phải kích hoạt? | Tracing quanh submitOrder |
| Advice bị cấm | Tại điểm nào advice tuyệt đối không được kích hoạt? | Không instrument chính exporter |
| Thứ tự | Các advice bao nhau như thế nào? | RecordFinal(Retry(Transaction(operation))) |
| Số lần | proceed() và advice chạy bao nhiêu lần? | Một final record cho mỗi operation |
| Kết quả/lỗi | Advice được phép biến đổi return/throw không? | Observer không che ngoại lệ gốc |
| Ngữ cảnh | Correlation và principal sống trong scope nào? | Khôi phục context sau khi kết thúc |
| Tác dụng phụ | Effect nào có thể lặp hoặc bị rollback? | Gửi thanh toán không bị retry trùng |
| Bất đồng bộ | Khi nào operation hoàn tất về mặt logic? | Completion của task, không chỉ return handle |

Mỗi refactor nên xác định trước điều gì **được phép thay đổi**. Chẳng hạn chuyển method sang một lớp triển khai khác có thể làm tên class thay đổi, nhưng mục tiêu “một tracing event cho mỗi business operation” vẫn giữ nguyên. Khi đó test không nên đóng băng tên của lớp cũ nếu tên lớp không phải một phần hợp đồng, nhưng vẫn phải giữ số event và đường đi bắt buộc.

Ngược lại, nếu chuyển một method sang lớp khác cũng chủ đích thay đổi ranh giới giao dịch hoặc đơn vị ghi log, hãy cập nhật hợp đồng bằng một quyết định thiết kế được review, không im lặng cập nhật expected output chỉ để test xanh.

## 3. Hai loại hồi quy: mất bao phủ và can thiệp thừa

Gọi tập join point phải được aspect X can thiệp là **R**, tập không được aspect X can thiệp là **F**, và tập điểm mà X thực sự chạy trong bài test là **A**.

~~~text
Phải có:         R ⊆ A
Không được có:   F ∩ A = ∅
~~~

Đây là hai điều kiện độc lập:

- **Positive regression test:** lời gọi mới hoặc đã đổi chỗ vẫn đi qua advice bắt buộc.
- **Negative regression test:** một helper, logger hoặc API mới không bị pointcut vô tình chọn thêm.

Một test dương có thể tiếp tục pass dù pointcut được mở rộng bừa bãi. Một test âm có thể pass giả nếu AOP bị vô hiệu hoàn toàn. Vì vậy, mỗi test suite phải có **cả kiểm soát dương và kiểm soát âm** trên cùng build/runtime.

Ví dụ bảng hợp đồng cho advice OperationTrace:

| Phần chương trình | Trước refactor | Sau refactor mong muốn | Kỳ vọng |
| --- | --- | --- | --- |
| Entry point submit | OrderService.submit | OrderFacade.submit | Một lần advice tại ranh giới operation |
| Hàm trợ giúp tính phí | OrderService.calculate | PricingEngine.calculate | Không phát thêm final-operation advice |
| Exporter ghi trace | Telemetry.publish | TelemetryWriter.publish | Không bị chính OperationTrace instrument |
| Xử lý hoàn tất tác vụ | Đồng bộ trong submit | Callback sau khi submit trả Future | Final outcome phải theo completion nếu hợp đồng yêu cầu |
| Entry point batch mới | Chưa tồn tại | BatchHandler.submit | Vẫn kiểm tra quyền trước effect nếu cùng loại thao tác nhạy cảm |

Hợp đồng phải nói rõ cấp **method execution** hay **business operation**: một operation có thể bao gồm nhiều method execution, nhất là sau khi chia nhỏ thành các thành phần.

## 4. Các thay đổi cấu trúc dễ gây hồi quy AOP

### 4.1. Đổi tên package hoặc chuyển class

Một pointcut chọn theo tên package/type có thể không còn match khi chuyển implementation sang module khác:

~~~text
Trước:
  app.orders.OrderService.submit()

Sau:
  app.checkout.OrderCoordinator.submit()
~~~

Nếu expression chỉ bao phủ app.orders, advice sẽ biến mất ở implementation mới. Mặt khác, việc dời một utility vào package đang bị wildcard chọn có thể khiến advice chạy ở utility đó dù trước đây không chạy.

**Regression cần kiểm tra:** entry point mới vẫn được bảo vệ, class đã chuyển khỏi scope không còn nhận advice thừa, và các điểm bị loại trừ vẫn không match.

### 4.2. Đổi tên method, chữ ký hoặc overload

Ví dụ:

~~~text
submit(Order)
submit(Order, Options)
submit(String)
~~~

Một biểu thức dựa trên tên và danh sách tham số có thể match một overload nhưng bỏ sót overload mới. Refactor đổi return type hoặc modifier cũng có thể làm signature pattern không còn đúng.

**Regression cần kiểm tra:** từng overload có kỳ vọng riêng; không chỉ gọi một overload bất kỳ rồi kết luận toàn bộ operation được bao phủ.

### 4.3. Tách phương thức thành helper

Trước refactor, một method làm toàn bộ công việc. Sau refactor, nó ủy quyền:

~~~text
OrderFacade.submit()
    -> Validation.check()
    -> Pricing.calculate()
    -> Storage.save()
~~~

Nếu pointcut chọn wildcard mọi method dưới một package, bốn method có thể đều bị aspect nhìn thấy. Với recorder “một event cuối cho một operation”, đây là **đếm trùng**. Với transaction, scope giao dịch có thể bị tạo nhiều lần hoặc tương tác với transaction có sẵn. Với retry, hiệu ứng còn đáng ngại hơn: proceed() có thể chạy lặp trên một helper vốn không an toàn để lặp.

**Regression cần kiểm tra:** số lượt advice và loại advice tại từng ranh giới, không chỉ tổng số dòng log.

### 4.4. Trộn hoặc gộp method

Nếu nhiều operation riêng được gộp thành một method tổng quát với tham số mode, một pointcut trước đó phân biệt nghiệp vụ qua tên method có thể mất khả năng chọn chính xác.

**Regression cần kiểm tra:** mỗi loại operation vẫn có đúng quyền, tracing và outcome; không gán cùng transaction/retry policy cho các mode có tác dụng phụ khác nhau.

### 4.5. Chuyển interface hoặc thay đường tạo đối tượng

Một proxy dựa trên interface có thể nhìn thấy lời gọi qua interface nhưng không intercept lời gọi trực tiếp tới target. Sau refactor, nếu code lưu raw target hoặc tự gọi phương thức trên this, đường gọi có thể không đi qua interception tương tự trước.

Một cơ chế weaving tại method-execution có thể khác proxy ở chính tình huống self-invocation. Không được sao chép kỳ vọng của một cơ chế sang mọi dạng AOP.

**Regression cần kiểm tra:** receiver thực sự là proxy hay target, class nào được weave, và những entry point mới nào tiếp cận được sensitive effect.

### 4.6. Thêm inheritance, generic hoặc bridge method

Khi đổi từ một lớp cụ thể sang interface/generic base class, method có thể bị override hoặc compiler tạo bridge method. Một pointcut quá rộng có thể tạo thêm event; một type pattern quá hẹp có thể bỏ implementation mới.

**Regression cần kiểm tra:** signature ở các lớp runtime thật, số event trên một lần gọi và thứ tự advice; không suy đoán chỉ theo tên lớp/interface.

### 4.7. Chuyển từ đồng bộ sang bất đồng bộ

Một method trước đây trả kết quả cuối, sau refactor trả CompletionStage/Future hoặc Publisher. Nếu advice chỉ quan sát return đồng bộ, nó có thể báo success trước khi task fail.

**Regression cần kiểm tra:** định nghĩa completion của operation, thời gian đóng transaction/resource, propagation context và outcome cuối khi task async lỗi hoặc hủy.

### 4.8. Thêm callback, job hoặc event consumer

Cùng hành vi nghiệp vụ có thể được kích hoạt từ nhiều điểm vào. Một advice ở controller không đảm bảo job/callback mới cũng bị kiểm soát.

**Regression cần kiểm tra:** mọi đường tới effect nhạy cảm, kể cả entry point mới; dùng principal đáng tin cậy và kiểm tra quyền tại ranh giới thực sự có thể ngăn effect.

## 5. Những phép đo có giá trị hơn việc chỉ assert output

Một test chỉ xác nhận submit() trả OK chưa chứng minh AOP đúng. Cần thêm dấu vết:

| Quan sát | Phát hiện vấn đề gì? |
| --- | --- |
| Danh tính join point và loại call/execution | Chọn nhầm vị trí can thiệp |
| Advice enter/return/throw | Mất bao phủ hoặc advice chạy ngoài phạm vi |
| Số lần proceed() | Retry hoặc around advice gọi nhiều/ít lần |
| Thứ tự event | Đổi thứ tự transaction, retry và recorder |
| Return value và exception | Aspect nuốt/bọc lỗi hoặc thay giá trị trái hợp đồng |
| Transaction begin/commit/rollback | Scope giao dịch bị tách/gộp sau refactor |
| Số external effects | Thao tác ngoài transaction bị nhân đôi |
| Scope correlation/principal | Ngữ cảnh rò giữa lời gọi hoặc mất qua thread |
| Async terminal signal | Ghi outcome trước khi công việc thực sự hoàn tất |
| Latency và overhead theo ranh giới | Instrumentation rộng hơn gây suy giảm |
| Trạng thái resource sau khi rời scope | Lock/connection/transaction bị rò |

**Oracle của regression** phải định nghĩa cho từng phép đo:

~~~text
given một yêu cầu submit hợp lệ
when chạy qua đường gọi mới sau refactor
then:
    đúng một business operation được hoàn tất
    advice tracing operation chạy đúng một lần
    authorization kiểm tra trước effect nhạy cảm
    helper và exporter không bị final recorder can thiệp
    kết quả/ngoại lệ giữ đúng hợp đồng
    không có external effect thừa
    context và tài nguyên được khôi phục
~~~

Trong các phép thử bất đồng bộ, phải chờ hoặc điều khiển được terminal event. Không assert “không có thêm event” ngay sau khi nhận future nếu callback vẫn chưa chạy.

## 6. Thiết kế test theo hợp đồng, không đóng băng chi tiết refactor hợp lệ

Giả sử một nghiệp vụ trước đây in các bước:

~~~text
OrderService.validate
OrderService.calculate
OrderService.save
~~~

Sau refactor được gộp và đổi lớp:

~~~text
CheckoutEngine.process
~~~

Nếu metric đúng cần là **một operation completed**, test bám cứng ba method cũ sẽ fail dù hành vi AOP tổng thể có thể đúng. Hãy tách:

- **Contract-level assertions:** số event nghiệp vụ, thứ tự chính sách, dữ liệu an toàn, outcome và scope.
- **Implementation-level assertions:** chữ ký, class/package cụ thể chỉ khi chúng thực sự là một phần pointcut cần kiểm tra.

Với pointcut dùng tên package cụ thể, vẫn cần kiểm tra ở tầng implementation vì nó là cấu hình có thể hỏng sau khi đổi cấu trúc. Nhưng expected baseline của hợp đồng nghiệp vụ nên được quản lý độc lập.

Một chiến lược tốt là tạo **bản đồ expectation**:

~~~text
OperationContract:
  name: submit-order
  requiredAdvices:
    - authorization-before-sensitive-effect
    - tracing-around-operation
  forbiddenAdvices:
    - retry-on-non-idempotent-payment
    - log-raw-secret
  cardinality:
    finalOutcomeEvents: 1
  result:
    businessStatus: explicit
  asyncCompletion:
    onlyIfOperationIsAsync: terminal-signal
~~~

Đây là YAML minh họa cho ý tưởng, không phải cấu hình mặc định của một công cụ AOP. Nó cho thấy **cần mô tả điều gì**, không áp đặt schema cụ thể.

## 7. Ví dụ Java 17+ có thể chạy: kiểm tra hồi quy trước và sau khi tách lớp

Ví dụ sau dùng **Java Dynamic Proxy** để minh họa việc chọn method bằng một metadata hợp đồng ổn định, thay vì dựa vào tên lớp triển khai.

Bối cảnh:

- Interface Api định nghĩa các operation và đánh dấu những operation được quan sát bằng annotation Observed.
- LegacyApi thực thi nghiệp vụ trực tiếp.
- RefactoredApi chuyển logic sang Engine, nhưng giữ nguyên hợp đồng công khai.
- Interceptor bao quanh các lời gọi qua proxy, ghi advice.before, advice.return, advice.throw.
- Bộ test xác minh cùng kết quả, thứ tự event và phương thức health() vẫn ngoài phạm vi sau khi refactor.
- Hai mutation được tiêm riêng: pointcut quá hẹp bỏ sót cancel() và pointcut quá rộng vô tình chọn health(). Bộ test phải phát hiện được cả hai.

Đây là phép mô phỏng nguyên lý của AOP, **không phải** một cơ chế bytecode weaving. Khi dùng AOP thật cần kiểm tra runtime pointcut và phạm vi can thiệp tương ứng.

Tạo tệp **AspectRefactorRegressionDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class AspectRefactorRegressionDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Observed {}

    interface Api {
        @Observed String save(String id);
        @Observed String cancel(String id);
        String health();
    }

    static final class LegacyApi implements Api {
        private final List<String> events;

        LegacyApi(List<String> events) {
            this.events = events;
        }

        public String save(String id) {
            events.add("business.save " + id);
            if (id.equals("bad")) {
                throw new IllegalArgumentException("bad id");
            }
            return "saved-" + id;
        }

        public String cancel(String id) {
            events.add("business.cancel " + id);
            return "cancelled-" + id;
        }

        public String health() {
            events.add("business.health");
            return "UP";
        }
    }

    // Sau refactor, logic được dời từ lớp triển khai sang helper.
    static final class Engine {
        private final List<String> events;

        Engine(List<String> events) {
            this.events = events;
        }

        String save(String id) {
            events.add("business.save " + id);
            if (id.equals("bad")) {
                throw new IllegalArgumentException("bad id");
            }
            return "saved-" + id;
        }

        String cancel(String id) {
            events.add("business.cancel " + id);
            return "cancelled-" + id;
        }

        String health() {
            events.add("business.health");
            return "UP";
        }
    }

    static final class RefactoredApi implements Api {
        private final Engine engine;

        RefactoredApi(List<String> events) {
            this.engine = new Engine(events);
        }

        public String save(String id) {
            return engine.save(id);
        }

        public String cancel(String id) {
            return engine.cancel(id);
        }

        public String health() {
            return engine.health();
        }
    }

    static Object invokeTarget(
            Api target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static Api instrument(
            Api target,
            List<String> events,
            Predicate<Method> selected) {
        return (Api) Proxy.newProxyInstance(
                Api.class.getClassLoader(),
                new Class<?>[]{Api.class},
                (proxy, method, args) -> {
                    if (!selected.test(method)) {
                        return invokeTarget(target, method, args);
                    }

                    String name = method.getName();
                    events.add("advice.before " + name);
                    try {
                        Object result = invokeTarget(target, method, args);
                        events.add("advice.return " + name);
                        return result;
                    } catch (Throwable error) {
                        events.add("advice.throw " + name);
                        throw error;
                    }
                });
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void expectEvents(List<String> actual, String... expected) {
        List<String> wanted = List.of(expected);
        if (!actual.equals(wanted)) {
            throw new AssertionError(
                    "expected=" + wanted + "; actual=" + actual);
        }
    }

    static void verifyContract(
            String label,
            Function<List<String>, Api> factory) {
        List<String> events = new ArrayList<>();
        Api raw = factory.apply(events);
        Api guarded = instrument(raw, events,
                method -> method.isAnnotationPresent(Observed.class));

        require("saved-A".equals(guarded.save("A")), "save result");
        require("cancelled-B".equals(guarded.cancel("B")),
                "cancel result");
        require("UP".equals(guarded.health()), "health result");

        expectEvents(events,
                "advice.before save",
                "business.save A",
                "advice.return save",
                "advice.before cancel",
                "business.cancel B",
                "advice.return cancel",
                "business.health");
        events.clear();

        try {
            guarded.save("bad");
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException failure) {
            require("bad id".equals(failure.getMessage()),
                    "error identity/message");
        }

        expectEvents(events,
                "advice.before save",
                "business.save bad",
                "advice.throw save");

        System.out.println("PASS: " + label);
    }

    static void verifyMutations() {
        List<String> events = new ArrayList<>();
        Api raw = new RefactoredApi(events);

        // Lỗi quá hẹp: chỉ chọn save, bỏ mất cancel.
        Api tooNarrow = instrument(raw, events,
                method -> method.getName().equals("save"));
        tooNarrow.cancel("C");
        boolean missingDetected = false;
        try {
            expectEvents(events,
                    "advice.before cancel",
                    "business.cancel C",
                    "advice.return cancel");
        } catch (AssertionError expected) {
            missingDetected = true;
        }
        require(missingDetected, "must detect missing advice");
        System.out.println("PASS: missing coverage detected");

        events.clear();

        // Lỗi quá rộng: health không được chọn nhưng vẫn bị can thiệp.
        Api tooBroad = instrument(raw, events, method -> true);
        tooBroad.health();
        boolean extraDetected = false;
        try {
            expectEvents(events, "business.health");
        } catch (AssertionError expected) {
            extraDetected = true;
        }
        require(extraDetected, "must detect overbroad pointcut");
        System.out.println("PASS: overbroad selection detected");
    }

    public static void main(String[] args) {
        verifyContract("legacy implementation", LegacyApi::new);
        verifyContract("moved implementation", RefactoredApi::new);
        verifyMutations();
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac AspectRefactorRegressionDemo.java
java AspectRefactorRegressionDemo
~~~

Stdout:

~~~text
PASS: legacy implementation
PASS: moved implementation
PASS: missing coverage detected
PASS: overbroad selection detected
~~~

### Vì sao chương trình này đáng dùng để học regression?

**Hai implementation khác nhau, một hợp đồng:** LegacyApi và RefactoredApi tạo cùng return value/exception và chuỗi event ở ranh giới Api. Sau khi logic được dời vào Engine, test vẫn bảo toàn các điều kiện quan trọng của aspect.

**Chính sách gắn ở ranh giới interface:** Dynamic Proxy nhận một Method của interface Api, nên annotation Observed đặt ở interface được đọc bởi selected predicate. Không nên suy ra rằng annotation trên interface luôn tự được kế thừa vào mọi method implementation của mọi cơ chế weaving; với cơ chế khác, cần kiểm tra khả năng match annotation trên method khai báo/implement thực tế.

**Exception được quan sát và truyền ra:** invokeTarget bỏ lớp InvocationTargetException do reflection tạo, nên người gọi tiếp tục nhận IllegalArgumentException từ nghiệp vụ theo hợp đồng. Advice ghi advice.throw nhưng không nuốt lỗi.

**Thử nghiệm mutation có ý nghĩa:** quá hẹp bỏ mất advice trên cancel; quá rộng áp cả vào health. Regression suite chỉ được xem là có giá trị nếu nhận ra hai loại sai lệch đó.

**Không áp dụng proxy cho method gọi nội bộ:** ví dụ chỉ gọi qua Api proxy ở test. Nếu Engine gọi một phương thức nội bộ có yêu cầu bảo vệ riêng, phải kiểm thử thêm đường gọi thật. Cơ chế proxy theo interface không tự biến các lời gọi bên trong Engine thành join point trên proxy.

**Không đánh đồng với security:** dù advice về observability có thể đặt ở proxy, một bất biến bảo mật quan trọng phải được cưỡng chế ở ranh giới đáng tin cậy. Một caller lấy raw target có thể bỏ proxy; với chính sách authorization, đó có thể là security coverage failure.

## 8. Regression theo loại pointcut: tránh chuyển refactor thành mất match

Các hình thức pointcut cần kiểm thử theo ngữ nghĩa riêng:

### 8.1. Method execution

Một execution pointcut chọn thân phương thức đích. Sau khi dời logic sang lớp khác, cần xác định **điểm thực thi nào** nay đại diện đúng business operation. Nếu pointcut chọn theo tên kiểu/phương thức cũ, advice có thể không còn chạy.

Nếu giữ method façade mỏng nhưng đẩy toàn bộ công việc sang helper, execution advice tại façade có thể vẫn chạy **đúng một lần**; điều này có thể đáp ứng hợp đồng “quan sát operation tại API boundary”. Nhưng nếu mục tiêu là đo execution time của phần xử lý thực tế, façade chỉ trả future hoặc delegate có thể làm giá trị đo trở nên khác.

### 8.2. Method call

Một call pointcut chọn **vị trí phát sinh lời gọi**. Di chuyển câu lệnh gọi sang một helper trong package khác có thể làm điều kiện theo vùng mã chứa call site không còn match.

Ví dụ:

~~~text
Trước: Client.submit() gọi RemoteApi.send()
Sau:   Client.submit() gọi NetworkHelper.dispatch()
       NetworkHelper.dispatch() gọi RemoteApi.send()
~~~

Nếu pointcut chọn call(RemoteApi.send) **chỉ tại mã Client**, call site mới ở NetworkHelper có thể không được can thiệp. Regression cần bao phủ đường caller mới theo mục đích kiến trúc, không chỉ method đích.

### 8.3. Vùng mã chứa join point

Một điều kiện lexical theo type/package có thể bị thay đổi khi dời code. Nếu aspect chỉ dành cho mã trong một tầng, việc move class sang tầng khác có thể chủ ý khiến advice không áp; nếu không chủ ý, đó là lỗi.

Hãy ghi rõ “được phép theo kiểu **vị trí mã hiện tại**” hay “được phép theo **vai trò nghiệp vụ**”. Hai cách định nghĩa dẫn đến cách update pointcut khác nhau sau refactor.

### 8.4. Annotation-based selection

Metadata có thể ổn định khi implementation đổi lớp, như ví dụ Java trên. Nhưng cũng có các rủi ro:

- Annotation bị mất khi đổi method hoặc tách interface.
- Annotation đặt ở interface nhưng cơ chế instrument đọc trực tiếp method triển khai và không thấy nó theo cùng cách.
- Một annotation được copy vào helper ngoài dự kiến, khiến advice chạy hai nơi.
- Retention hoặc target của annotation không phù hợp cho runtime selection.
- Một overload mới không được đánh dấu.

Kiểm thử contract và runtime điểm cắt phải phân biệt được các trường hợp này.

### 8.5. Type hierarchy, inheritance và bridge method

Sau refactor về generic interface hoặc subclass, compiler có thể sinh bridge method; dynamic dispatch có thể chuyển từ method khai báo ở base sang override. Một pointcut có thể match nhiều hoặc ít điểm hơn trước tùy cơ chế.

Cần test trên lớp thực tế, không chỉ mock theo tên method. Đo đúng số event nghiệp vụ để tránh việc một lời gọi bị quan sát hai lần do hai join point mà không có chủ ý.

## 9. Regression đối với thứ tự advice và control flow

Một refactor có thể không đổi tập method match nhưng đổi **thứ tự bao quanh advice**. Ví dụ một cơ chế registration thay đổi priority hoặc classpath load order.

Ký pháp:

~~~text
RecordFinal(
  Retry(
    Transaction(
      operation
    )
  )
)
~~~

trong đó Retry bao transaction, mỗi attempt có thể có ranh giới transaction độc lập theo chính sách thực tế. So sánh:

~~~text
Transaction(
  Retry(
    operation
  )
)
~~~

attempt có thể dùng chung transaction, không an toàn khi lỗi đầu khiến transaction bị rollback-only.

Regression nên ghi event theo thứ tự:

~~~text
retry attempt=1
transaction begin T1
business failure
transaction rollback T1
retry attempt=2
transaction begin T2
business return
transaction commit T2
operation final success
~~~

Những event trên là ví dụ cho **mô hình thử lại toàn giao dịch**, không phải trình tự bắt buộc của mọi AOP. Test thật cần khớp transaction manager và chính sách retry.

Nếu recorder được dời vào bên trong transaction, nó có thể ghi success **trước commit**. Nếu recorder ở ngoài retry bị chuyển vào mỗi attempt, một operation có thể có nhiều final records. Các thay đổi này khó phát hiện nếu test chỉ nhìn vào return value.

Với fallback, một advice ở ngoài retry có thể chỉ trả fallback sau khi hết attempts; nếu nằm bên trong, nó có thể che lỗi ngay và khiến retry không chạy. Test nên assert cả số attempt, loại lỗi và outcome cuối.

## 10. Regression kiểm soát quyền khi đường gọi thay đổi

Refactor có thể tạo đường vào mới tới một thao tác nhạy cảm:

~~~text
UI request --> proxy --> resource operation
Batch job -----------------------> raw resource operation
~~~

Nếu authorization chỉ nằm trên proxy, job có thể bỏ qua kiểm tra quyền. Không được coi hiện tượng này là test âm “đúng vì không có advice”; đó là **lỗi thiếu bảo vệ** nếu resource operation bắt buộc được kiểm soát.

Các test bảo mật sau refactor nên:

1. Xác định principal, action, resource và trust boundary.
2. Liệt kê mọi entry point mới và cũ có thể tạo sensitive effect.
3. Gọi từng đường với principal có và không có quyền.
4. Xác nhận **không có tác dụng phụ nhạy cảm xảy ra với principal không được phép**.
5. Kiểm tra chính sách fail-closed khi thiếu dữ liệu danh tính hoặc quyền.
6. Không tin rằng tên annotation/aspect chứng minh mọi đường gọi được instrument.

Nếu chuyển từ xử lý đồng bộ sang job, principal có thể là danh tính hệ thống thay vì người dùng tương tác. Phải định nghĩa quyền theo vai trò tác vụ, không tự cho rằng mọi background job đều có toàn quyền.

## 11. Regression khi chuyển sang API bất đồng bộ

Một thay đổi API như:

~~~text
Trước:
    Receipt submit(Order)          // trả kết quả cuối

Sau:
    CompletionStage<Receipt> submit(Order)
                                   // trả handle
~~~

có thể khiến advice theo before/after-returning quan sát sai **mốc hoàn tất**. Nếu transaction advice mở transaction ở method submit rồi đóng khi trả future, worker tiếp tục xử lý có thể chạy ngoài transaction dự kiến.

Cần xác định riêng:

- **Invocation start/return:** khi method được gọi và khi nó trả handle.
- **Logical completion:** future hoàn tất thành công, lỗi hoặc bị hủy.
- **Transaction ownership:** nơi resource được tạo/dùng/đóng, thread nào tham gia.
- **Retry boundary:** gọi lại function tạo future hay chỉ chờ lại future cũ?
- **Context propagation:** correlation/principal được capture/bind/restore đúng thời điểm.
- **Timeout/cancellation:** caller hết thời gian chờ không có nghĩa task hoặc side effect đã dừng.

Test hồi quy cần tiêm các kết quả: hoàn tất bình thường, completion exceptional, cancellation, timeout, callback trên thread khác và kết quả commit chưa rõ. Nếu mỗi method return thành công nhưng task lỗi sau đó, recorder final phải nhìn thấy thất bại **theo completion contract**, không báo thành công quá sớm.

Với publisher/reactive API, method tạo publisher có thể kết thúc trước subscription; nhiều subscriptions có thể tạo nhiều executions. Cần đo ở đúng lifecycle thay vì sao chép test cho method đồng bộ.

## 12. Regression đối với lỗi và tác dụng phụ

Một aspect có thể thay đổi hành vi dù refactor không làm đổi return cuối:

**Retry và idempotency.** Nếu pointcut vô tình mở rộng để bao một lệnh gửi email/thanh toán, một lỗi tạm thời có thể tạo effect lặp. Test đếm số lần effect dưới fault injection; không chỉ kiểm tra rằng retry cuối cùng thành công.

**Transaction và commit.** Một method có thể return bình thường trước khi commit thất bại. Test ghi committed-success phải đặt observer sau ranh giới commit thực và xét trạng thái commit không chắc chắn.

**Logging/metric best effort.** Nếu recorder bị ném lỗi trong một advice nằm dưới retry, hệ thống có thể gọi lại nghiệp vụ đã commit. Test tiêm lỗi vào exporter rồi assert số business invocations không tăng ngoài hợp đồng.

**Exception mapping.** Advice có thể bọc, nuốt hoặc thay lỗi gốc. Test xác nhận caller vẫn phân biệt lỗi nghiệp vụ, lỗi retryable, timeout và cancellation theo contract.

**Side effect ngoài transaction.** Rollback cơ sở dữ liệu không tự thu hồi email, HTTP request hoặc message đã gửi. Nếu refactor chuyển effect vào vùng retry, cần thiết kế idempotency/outbox/deduplication/compensation phù hợp.

**Context/thread-local state.** Một scope kết thúc mà không restore có thể làm request sau mang correlation ID của request trước. Test worker xử lý liên tiếp hai operation và đối chiếu context trước/sau.

Hãy đưa các failure point quan trọng vào bài kiểm thử, không chỉ happy path.

## 13. Xây test matrix cho một lần refactor thực tế

Ví dụ tình huống thay đổi cấu trúc:

- Chuyển OrderService sang CheckoutCoordinator.
- Tách PricingEngine ra một package khác.
- Thêm overload submit(Order, Flags).
- Thay lời gọi trực tiếp remote client bằng NetworkHelper.
- Chuyển một bước hậu xử lý sang executor.

Bảng regression:

| Hạng mục thay đổi | Positive test | Negative test | Thứ phải đo |
| --- | --- | --- | --- |
| Chuyển class nghiệp vụ | Advice chính vẫn chạy | Không chạy trên class cũ/utility nếu không còn được chọn | Join point identity, advice count |
| Tách helper | Operation vẫn có một final record | Helper không phát final record thừa | Event count, operation scope |
| Thêm overload | Overload được phê duyệt vẫn được kiểm soát | Overload không thuộc policy không bị áp nhầm | Signature, return/throw |
| Dời call site | Call advice được cập nhật nếu cần | Call site ở package không được phép không bị chọn | Call/execution distinction |
| Đổi interface/proxy | Đường hợp lệ đi qua interception | Raw target không vô tình trở thành đường bypass security | Receiver, target state |
| Thêm worker async | Logical completion được quan sát | Không ghi success ngay khi trả handle | Completion, context, resource |
| Đổi precedence | Transaction/retry/record đúng thứ tự | Không retry ngoài policy | Event order, transaction outcomes |
| Thêm exporter | Trace pipeline hoạt động | Exporter không bị cùng tracer intercept | Recursion, event count |
| Đổi kết cấu exception | Lỗi gốc bảo toàn | Observer không che lỗi | Exception class/cause |
| Thêm cache | Cache hit/miss có chính sách rõ | Cache không bỏ qua authorization | Resource visibility, guard events |

Sau refactor, chạy cả test dương, âm và integration test. Nếu pointcut dựa vào wildcard của package, các test phải đủ đại diện cho **những kiểu mới có thể vô tình bị match**.

## 14. Regression bằng cặp baseline và phiên bản đã sửa

Một chiến thuật hiệu quả:

1. **Chụp baseline có ý nghĩa:** tập join point mong đợi, event order, counts, outcome, resource state trên phiên bản trước.
2. **Tách stable contract:** phần nào bắt buộc giữ nguyên sau refactor, phần nào được phép đổi.
3. **Chạy cùng bộ scenario** trên implementation cũ và mới nếu có thể.
4. **So sánh những thuộc tính ổn định:** không cần snapshot từng tên helper, nhưng phải giữ chính sách và ranh giới operation.
5. **Đánh giá chênh lệch có chủ ý:** thay đổi event name hoặc join point do thiết kế mới phải được review và thêm test.
6. **Đưa thành regression suite chạy định kỳ:** không chỉ kiểm tra một lần vào ngày refactor.

Không nhất thiết cần giữ cả hai implementation trong production. Có thể duy trì một fake hoặc một bộ test contract độc lập để kiểm tra implementation mới. Với điểm cắt phụ thuộc cấu trúc thực, vẫn cần integration test trên artifact mới.

### Snapshot testing có giới hạn

Snapshot một danh sách join point/event có thể hữu ích để phát hiện thay đổi bất ngờ, nhưng không nên tự động chấp nhận snapshot mới khi test fail. Cần xem:

- Event mới có phải instrument thừa không?
- Event biến mất có phải mất authorization/tracing không?
- Thứ tự thay đổi có ảnh hưởng tới commit/retry không?
- Tên helper thay đổi có phải biến động hợp lệ, còn business contract vẫn nguyên?
- Thay đổi classloader/build có khiến runtime không còn weave?

Snapshot là tín hiệu để review, không phải bằng chứng tự động rằng một thay đổi đúng.

## 15. Mutation testing để chứng minh regression suite nhạy với lỗi

Một bộ test xanh chưa đủ nếu nó không thể phát hiện thay đổi pointcut. Hãy chủ động tạo các mutation **trong môi trường test**:

- Thu hẹp pointcut làm thiếu một method bắt buộc.
- Nới rộng pointcut để bắt helper hoặc exporter.
- Bỏ điều kiện exclusion cho logging internals.
- Đổi thứ tự advice để recorder nằm trước commit.
- Thay một around advice gọi proceed() một lần thành hai lần.
- Xóa logic khôi phục context trong finally.
- Thay async completion observer bằng after-returning observer.
- Đổi matcher theo interface thành matcher quá phụ thuộc tên lớp triển khai.

Test phù hợp phải fail. Nếu vẫn pass, có thể nó chỉ kiểm tra output nghiệp vụ, không đo advice, hoặc chỉ thử một đường gọi không đại diện.

Ví dụ Java trong bài dùng hai mutation đối nghịch: **tooNarrow** bỏ sót cancel và **tooBroad** chọn cả health. Đây là kiểm chứng rằng bộ test thực sự phát hiện lỗi phạm vi, không chỉ in pass khi chức năng nghiệp vụ vẫn đúng.

Mutation không thay thế việc kiểm tra cơ chế AOP thật; nó giúp đánh giá chất lượng test oracle.

## 16. Kiểm thử trên cơ chế AOP đang triển khai

Java Dynamic Proxy, subclass proxy và bytecode weaving có các ranh giới khác nhau. Sau refactor, phải test theo cơ chế production:

### Proxy interception

- Caller có dùng proxy hay raw target?
- Method còn thuộc interface được proxy cung cấp không?
- Có tự gọi trên this hoặc delegate tới target mà bỏ qua proxy không?
- Proxy có được tạo trước khi khởi tạo xong các phụ thuộc cần thiết không?
- Refactor có làm thay đổi scope hoặc vòng đời của proxy không?

### Subclass interception

- Method có thể override không?
- Có final/private method hoặc constructor được kỳ vọng can thiệp sai cách không?
- Đường gọi nào sử dụng subclass instrumented và đường nào dùng lớp gốc?
- Có thay đổi class hierarchy hoặc generic bridge method không?

### Weaving tại build/load time

- Class/method mới có nằm trong phạm vi weave không?
- Cấu hình build và runtime có đồng nhất không?
- Module, classloader, class nạp động và thư viện bên thứ ba có được instrument như mong muốn không?
- Các loại call/execution join point có được chọn đúng sau khi di chuyển mã không?
- Thứ tự advice trên cùng join point có còn như thiết kế không?

Không suy ra “self-invocation luôn bypass AOP” từ đặc điểm của một proxy; ở một cơ chế method-execution weaving đã cấu hình đúng, lời gọi nội bộ có thể vẫn kích hoạt advice. Tương tự, một file chứa aspect được biên dịch thành công không chứng minh join point mới đã được weave trong production.

### Unit test và integration test làm hai việc khác nhau

**Unit test** xác minh logic selection, policy, return/error handling bằng fake/spy nhỏ, nhanh và có oracle rõ. **Integration test** xác minh chính runtime interception chọn và chạy advice đúng trên build thực, kể cả classloader, method pattern và thứ tự weaving.

Regression suite tốt có cả hai: unit test bắt lỗi logic, integration test bắt lỗi thay đổi cấu trúc và cấu hình.

## 17. Tránh test pass giả vì AOP bị tắt hoặc recorder không hoạt động

Một negative test thấy **không có event** có thể pass giả nếu:

- Không chạy qua đối tượng được instrument.
- Bộ weaving chưa được bật trong môi trường test.
- Advice vẫn chạy nhưng logger sampling/drop event.
- Test kết thúc trước khi callback async phát event.
- Event list bị reset sai lúc.
- Một outer advice short-circuit và không gọi inner advice.
- Spy đo sai instance hoặc chỉ đo một lớp cũ sau refactor.

Để tránh:

1. Dùng **positive control** trên cùng runtime: một điểm bắt buộc có advice và xuất hiện event.
2. Gọi thật đường bị loại trừ và assert target/call site đã chạy.
3. Dùng probe trực tiếp tại advice, không chỉ dựa vào log phát ra ngoài.
4. Gắn operation ID/correlation ID cho event của test concurrent.
5. Chờ completion hoặc dùng scheduler điều khiển được cho async.
6. Quan sát số event **theo loại advice và join point**, không chỉ tổng số.
7. Kiểm tra fixture thực sự tạo đúng proxy/đối tượng đã được weave.

Với một pointcut rất rộng, negative test phải cố tình gọi những hàm nằm trong vùng nguy hiểm như logger, exporter, equals/hashCode/toString hoặc helper, nếu chúng đã được khai báo là điểm cấm của aspect đó.

## 18. Test đồng thời và dữ liệu ngữ cảnh sau refactor

Khi di chuyển code sang executor hoặc chia một operation thành nhiều task, ThreadLocal và state được aspect giữ có thể hoạt động khác.

Các tình huống:

**Cùng thread, hai yêu cầu nối tiếp.** Request A hoàn tất; request B chạy trên thread được tái sử dụng. Nếu advice không restore/remove context của A, log của B có thể mang correlation ID của A.

**Hai task song song.** Nếu aspect giữ per-call state trong một trường mutable trên instance dùng chung, task này có thể ghi đè context của task kia. Dùng snapshot/context bất biến và scope propagation phù hợp.

**Callback hoàn tất sau khi caller return.** Advice theo method execution đã kết thúc nhưng task mới báo lỗi. Final recorder phải gắn với logical completion nếu hợp đồng yêu cầu.

**Cancellation và timeout.** Tác vụ có thể vẫn chạy sau khi caller bỏ chờ. Một refactor chuyển đoạn mã gửi message sang worker có thể tạo message sau một lỗi timeout ở caller. Regression nên xác nhận policy idempotency và cách đối soát.

Test concurrent cần synchronization primitives hoặc scheduler deterministic; tránh sleep ngẫu nhiên rồi assert thiếu event. Điều kiện cleanup phải kiểm tra sau **mọi** đường return, throw và completion.

## 19. Rủi ro khi refactor telemetry và aspect infrastructure

Aspect có thể dùng một Logger, MetricsExporter hoặc PolicyEvaluator. Nếu refactor chuyển các lớp này vào package đang được pointcut chọn, advice có thể tự intercept chính hạ tầng của nó.

Ví dụ:

~~~text
before any selected method:
    MetricsExporter.publish("start")
        -> method bị pointcut chọn
            -> advice lại gọi MetricsExporter.publish
                -> ...
~~~

Kết quả có thể là recursion, log storm hoặc lỗi hiệu năng nghiêm trọng. Negative regression test cần xác minh:

- Exporter và logger internals không bị aspect cùng loại can thiệp.
- Gọi trực tiếp các phương thức của infrastructure không tạo thêm business-operation event.
- Một event nghiệp vụ không khuếch đại thành chuỗi event vô hạn.
- Nếu recorder gặp lỗi, best effort telemetry không làm thay đổi kết quả nghiệp vụ ngoài hợp đồng.

Loại trừ ở pointcut là hàng rào chính; reentrancy guard theo scope chỉ là lớp phòng vệ bổ sung và phải được restore trong finally. Nếu code chuyển thread, ThreadLocal guard không tự lan truyền sang thread khác.

## 20. Đo lường hiệu năng như một regression riêng

Một refactor có thể tạo nhiều method nhỏ hơn, khiến pointcut wildcard bắt thêm hàng nghìn lượt gọi. Kết quả nghiệp vụ không thay đổi nhưng độ trễ tăng.

Cần đo:

- Advice invocations per operation.
- Số event/span/log phát ra trên một request.
- Số lần tạo context hoặc đo thời gian.
- Tỷ lệ CPU/memory/allocations do instrumentation.
- Số time series do nhãn metric sinh ra.
- Latency ở các phân vị phù hợp, không chỉ trung bình.
- Thời gian chờ hoặc mức sử dụng resource do transaction/retry.

Đặc biệt không dùng correlation ID hoặc attempt ID làm metric label có cardinality gần như vô hạn. Nếu trước refactor mỗi operation tạo một span mà sau tạo hàng chục span, cần xem đó là instrumentation chi tiết được phê duyệt hay một sự nới pointcut ngoài ý muốn.

Test hiệu năng có nhiễu nên không cần ép so một con số cứng thiếu căn cứ. Đặt ngân sách/độ lệch theo thực nghiệm và kết hợp event counts mang tính quyết định để xác định nguyên nhân.

## 21. Checklist cho pull request có thay đổi cấu trúc

| Câu hỏi | Bằng chứng cần bổ sung |
| --- | --- |
| Có chuyển class/package không? | Danh sách pointcut phụ thuộc kiểu hoặc vùng mã, test match mới |
| Có đổi tên/signature/overload không? | Contract test cho từng method cần được chọn/loại |
| Có tách/gộp helper không? | Advice cardinality và final-operation count |
| Có đổi interface/proxy/delegation không? | Đường qua proxy và raw target, test self-call |
| Có đổi generic/hierarchy không? | Test override/bridge và số lượt instrument |
| Có thêm entry point không? | Test mọi đường tới sensitive effect |
| Có đổi sync thành async không? | Completion, cancellation, timeout, context |
| Có thay thứ tự advice không? | Event order và transaction/retry semantics |
| Có chuyển code hạ tầng quan sát không? | Negative test chống recursion/instrument thừa |
| Có đổi lỗi/return không? | Exception mapping, result contract |
| Có đổi effect được retry không? | Idempotency, số external effects, in-doubt commit |
| Có đổi weaving/classloader/build không? | Integration test trên cấu hình runtime tương ứng |
| Negative test có positive control không? | Chống pass giả do aspect không hoạt động |
| Test có phát hiện pointcut mutation không? | Thu hẹp và nới rộng đều được phát hiện |
| Có quyền cập nhật golden snapshot không? | Review thay đổi hợp đồng, không auto-accept |

## 22. Các lỗi phổ biến và cách sửa

**Lỗi 1 — Chỉ rerun unit test nghiệp vụ.** Logic vẫn đúng nhưng advice không chạy hoặc chạy hai lần. Cần event/assertions riêng cho AOP contract.

**Lỗi 2 — Bám cứng tên helper cũ.** Refactor hợp lệ làm test fail giả, trong khi hợp đồng operation không đổi. Tách kiểm thử business boundary khỏi kiểm thử cấu trúc thực sự thuộc pointcut.

**Lỗi 3 — Chỉ test dương tính.** Pointcut rộng thêm khiến exporter cũng bị instrument mà mọi positive test vẫn pass. Cần negative test cho từng vùng loại trừ quan trọng.

**Lỗi 4 — Chỉ test âm tính.** AOP bị tắt làm mọi excluded case pass. Thêm positive control trên cùng build.

**Lỗi 5 — Dựa vào vắng log.** Log có thể bị sampling/drop; dùng probe đếm trực tiếp lượt advice và join point.

**Lỗi 6 — Bỏ qua self-invocation sau khi tách lớp.** Với một số proxy, lời gọi nội bộ không quay qua interceptor. Test đường gọi thực tế; không áp dụng giả định ấy cho mọi dạng weaving.

**Lỗi 7 — Quên overload mới.** Một chữ ký mới không được bảo vệ vì pointcut quá hẹp; test mỗi public path có yêu cầu policy.

**Lỗi 8 — Không kiểm tra thứ tự retry/transaction.** Exception ở attempt đầu có thể làm transaction không còn dùng được; recorder có thể ghi success trước commit. Kiểm tra chuỗi event và số transaction.

**Lỗi 9 — Ghi success ở method-return sau khi đổi sang async.** Future vẫn có thể hoàn tất lỗi. Kiểm tra terminal outcome.

**Lỗi 10 — Coi timeout là rollback chắc chắn.** Task hoặc remote effect có thể tiếp tục. Cần idempotency/reconciliation, test trạng thái sau timeout.

**Lỗi 11 — Bỏ qua tác dụng phụ ngoài database.** Retry có thể gửi email hoặc thanh toán trùng. Test count và khóa chống lặp.

**Lỗi 12 — Cập nhật expected output theo implementation mà không review.** Có thể vô tình hợp thức hóa pointcut sai. Mỗi thay đổi hợp đồng phải có lý do rõ ràng và được đánh giá lại.

## 23. Bài tập tự kiểm tra

**Bài 1.** Một pointcut chọn execution của OrderService.submit(). Sau refactor toàn bộ logic chuyển vào CheckoutCoordinator.execute(), OrderService.submit() chỉ delegate. Có nhất thiết phải chuyển pointcut không?

**Đáp án:** Không nhất thiết. Nếu hợp đồng là quan sát một lần gọi API submit(), giữ pointcut tại façade có thể đúng. Nếu muốn đo chính phần tính toán hoặc bảo vệ effect ở implementation mới, phải đánh giá lại ranh giới và cập nhật pointcut/test tương ứng.

**Bài 2.** Method đặt hàng được tách thành bốn helper; metric operation_total tăng từ 1 lên 5 cho mỗi yêu cầu. Chức năng nghiệp vụ vẫn đúng. Đây có phải regression AOP không?

**Đáp án:** Có nếu hợp đồng yêu cầu một event cuối cho mỗi operation. Pointcut có thể mở rộng sang helper; cần negative tests và tách số liệu operation khỏi span/attempt cấp con.

**Bài 3.** Một test âm xác nhận MetricsExporter.publish không phát tracing event, nhưng cũng không có tracing event ở Business.submit. Có kết luận loại trừ exporter đúng không?

**Đáp án:** Chưa. AOP có thể không được bật. Cần positive control chứng minh Business.submit được trace trên cùng runtime.

**Bài 4.** Sau khi dời lời gọi RemoteApi.send từ Client sang NetworkHelper, call advice chỉ áp trong Client không còn chạy. Vì sao?

**Đáp án:** Call site mới nằm trong mã của NetworkHelper. Nếu pointcut ràng buộc vị trí từ vựng của caller, tập match đã đổi; phải đánh giá mục đích và cập nhật test/pointcut.

**Bài 5.** Một retry advice sau refactor cũng chọn sendPayment() chưa có idempotency. Một lỗi kết nối có thể gây hậu quả gì?

**Đáp án:** Có thể thực hiện thanh toán lặp dù attempt đầu đã có effect. Cần loại operation khỏi retry hoặc thiết kế idempotency key bền và đối soát trạng thái.

**Bài 6.** Trước đây method trả Receipt, nay trả CompletionStage<Receipt>. Advice after-returning báo success ngay. Điều gì sai?

**Đáp án:** Return handle không đồng nghĩa logical completion; future có thể hoàn tất lỗi. Cần observer completion đúng hợp đồng, transaction/context phù hợp.

**Bài 7.** Một bộ test unit chọn method qua annotation trên interface và luôn pass. Có chứng minh weaving thực ở implementation sau refactor sẽ nhìn thấy annotation đó không?

**Đáp án:** Không. Dynamic Proxy có thể cung cấp Method từ interface, trong khi cơ chế weaving khác có cách xử lý annotation trên method implementation khác. Phải kiểm thử trên runtime AOP thật và chữ ký runtime cụ thể.

**Bài 8.** Sau refactor, recorder và transaction đổi thứ tự. Test chỉ thấy operation cuối trả OK. Cần bổ sung gì?

**Đáp án:** Chuỗi begin/commit/rollback, thời điểm recorder, số attempt và kết quả khi commit thất bại. Return cuối thành công không chứng minh recorder đã nhìn thấy commit.

**Bài 9.** Một method được chuyển sang classloader khác và execution advice biến mất. Đây là lỗi gì?

**Đáp án:** Có thể do phạm vi weaving/classloader không còn bao phủ lớp mới, hoặc do type pattern không match. Kiểm tra build/load-time config và integration test runtime.

**Bài 10.** Mutation đổi selector chỉ nhận save() khiến cancel() mất advice; test mới vẫn pass. Điều này nói gì?

**Đáp án:** Regression suite không đủ nhạy với lỗi mất độ bao phủ. Cần case assert advice count/join point cho cancel() và kiểm tra target thực sự chạy.

## 24. Kết luận

Refactor là thay đổi cấu trúc, nhưng **tập join point và vòng đời advice có thể thay đổi theo cấu trúc**. Vì vậy, hồi quy AOP phải kiểm tra cả ba lớp:

1. **Selection:** điểm cần có advice vẫn có; điểm cấm không bị advice chọn thêm.
2. **Semantics:** thứ tự, số lượt proceed(), return/throw, transaction, retry, authorization và lifecycle async vẫn theo hợp đồng.
3. **Effects:** không lặp tác dụng phụ, không lộ dữ liệu, không rò context/tài nguyên, không tạo thêm telemetry ngoài phạm vi.

Một bộ regression đáng tin có expected contract độc lập với chi tiết refactor hợp lệ, positive/negative tests trên cùng runtime, kiểm thử ngoại lệ và tác dụng phụ, mutation để chứng minh phát hiện lỗi, cùng integration test của cơ chế proxy/weaving thực sự được triển khai.

**Điều phải bảo toàn sau mỗi lần đổi cấu trúc không phải là tên lớp cũ, mà là hành vi và ranh giới can thiệp đã được phê duyệt.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aspect-testing-010">So sánh hành vi khi bật và tắt một aspect</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh hành vi khi bật và tắt một aspect trong AOP

## 1. Bài toán: bật aspect thay đổi điều gì?

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP), aspect có thể can thiệp vào những join point đã chọn để đo thời gian, ghi log, tạo trace, kiểm tra quyền, cache, retry, quản lý giao dịch hoặc áp dụng một chính sách xuyên suốt. Một phương thức có thể giữ nguyên mã nguồn nhưng biểu hiện khi chạy khác nhau tùy aspect có được gắn và thực thi hay không.

**So sánh hành vi khi bật và tắt aspect** là phép thử để kiểm tra cả hai mặt:

1. **Tính đúng:** aspect có giữ nguyên những kết quả và bất biến bắt buộc của chương trình không?
2. **Tác động có chủ đích:** aspect có tạo đúng khác biệt đã quy định — thêm trace, từ chối quyền, giảm số lần tính toán hoặc thực hiện retry — hay không?

Không có quy tắc chung rằng bật/tắt mọi aspect phải cho kết quả hoàn toàn giống nhau. Đối với aspect chỉ quan sát, **hành vi nghiệp vụ nên tương đương**, trong khi log/metric/trace khác nhau có chủ đích. Đối với aspect kiểm soát quyền hoặc cache, kết quả một vài đường thực thi và số lần gọi target có thể khác theo hợp đồng. Một bài kiểm thử tốt phải xác định trước **cái gì phải giống, cái gì được phép khác và giới hạn sai khác**.

## 2. Ôn lại các thành phần AOP

- **Join point:** vị trí trong luồng chương trình có thể bị can thiệp, chẳng hạn method call hoặc method execution.
- **Pointcut:** biểu thức/rule chọn những join point thuộc phạm vi aspect.
- **Advice:** logic trước, sau hoặc bao quanh join point; around advice có thể quyết định có gọi proceed hay không.
- **Aspect:** tập hợp pointcut và advice cho một mối quan tâm xuyên suốt.
- **Weaving/interception:** cách gắn aspect vào chương trình: có thể là compile-time weaving, load-time weaving hoặc wrapper/proxy theo khả năng của cơ chế cụ thể.
- **Composition root:** nơi chọn implementation và lắp các lớp chính sách để cung cấp một cổng cho caller.

Cần làm rõ chính xác **bật/tắt** được hiểu ra sao:

| Cách tắt | Diễn giải | Giới hạn khi so sánh |
| --- | --- | --- |
| Không weave hoặc không lắp wrapper | Đường gọi thực sự không có advice tương ứng | Có thể dùng làm baseline chưa instrument |
| Advice vẫn có nhưng flag bỏ qua logic | Interception vẫn tồn tại; chỉ một số xử lý được vô hiệu | Không đồng nghĩa với không có overhead |
| Vẫn có advice nhưng tắt collector | Advice vẫn quan sát, bản ghi có thể không được gửi/lưu | Không chứng minh advice đã tắt |
| Dùng một loại build khác | Khác cả quá trình weaving và cấu hình | Cần xác minh hai bản build có cùng code lõi, dependency và data |

Với AOP được weave vào bytecode ở build time, tắt một flag runtime **không tự hoàn nguyên** bytecode. Nếu dùng proxy, gọi thẳng object gốc có thể bỏ qua proxy. Vì vậy, trước khi đánh giá phép thử phải xác định test đang so sánh **hai phiên bản composition** hay chỉ **hai trạng thái của flag**.

## 3. Tiêu chuẩn so sánh theo loại aspect

### 3.1. Aspect quan sát kỹ thuật

Ví dụ: logging, tracing, metrics, đo độ trễ. Bản bật và bản tắt thường phải có cùng:

- Return value và kiểu kết quả theo hợp đồng.
- Null hợp lệ, primitive, void và hành vi ngoại lệ.
- Thứ tự xử lý nghiệp vụ cần thiết.
- Số lần gọi target và số tác dụng phụ thật.
- Trạng thái domain cuối cùng và business event được xác nhận.
- Quyền truy cập vào dữ liệu, kết quả với cùng input/fixture.
- Ngữ nghĩa bất đồng bộ, cancellation và completion.

**Được khác:** lượng log, số span, nhãn metric, bộ đếm nội bộ của recorder và overhead. Đừng đòi hai bản có thời gian chạy bằng nhau; instrumentation vốn tiêu tốn tài nguyên. Nếu collector chỉ best-effort, lỗi collector không được che kết quả hoặc lỗi từ nghiệp vụ.

### 3.2. Aspect kiểm soát truy cập

Bật aspect authorization có thể khiến yêu cầu thiếu quyền bị từ chối trước khi target chạy, trong khi baseline không gắn policy có thể thực thi. Đây là khác biệt có chủ đích trong **fixture kiểm thử cô lập**, không phải lý do để tắt lớp bảo vệ bắt buộc trên hệ thống thực.

Kiểm thử phải phân biệt:

- ALLOW: đúng quyền thì đi vào target và cho kết quả hợp lệ.
- DENY: không đủ quyền thì target được gọi zero lần, lỗi đúng hợp đồng.
- Phạm vi: những entry point bắt buộc phải có enforcement đều thực sự đi qua advice.
- Tính đúng trên từng tài nguyên: quyền tổng quát không thay thế kiểm tra tenant/owner/ACL tại biên giới dữ liệu.

### 3.3. Aspect cache

Khi bật cache, cache hit có thể không gọi target; khi tắt, mỗi yêu cầu có thể thực thi phép tính. Do đó **số lần target chạy không nhất thiết bằng nhau**. Phải so kết quả theo cùng tenant, quyền, phiên bản dữ liệu, khóa cache, TTL và invalidation. Với thao tác có tác dụng phụ, việc bỏ qua target do cache hit có thể vi phạm hợp đồng, cần loại khỏi phạm vi cache hoặc thiết kế lại.

### 3.4. Aspect retry, timeout, transaction và audit

- Retry có thể tạo nhiều attempt, nhưng phải đúng giới hạn và idempotency.
- Timeout có thể phát sinh lỗi có chủ đích khi vượt deadline.
- Transaction có thể thay đổi trạng thái commit/rollback và thời điểm quan sát lỗi.
- Audit bắt buộc có thể là một phần hợp đồng nghiệp vụ chứ không chỉ telemetry best-effort.

Với các policy này, phép thử cần so **điều kiện và tác động cuối**, không chỉ giá trị trả về.

## 4. Differential test: một bộ tình huống, hai chế độ

Mô hình:

~~~text
            Cùng bộ tình huống
                    |
             +------+------+
             |             |
             v             v
        Aspect TẮT    Aspect BẬT
             |             |
          Target        Advice
             |             |
             |          Target
             v             v
      Domain snapshot  Domain snapshot
             |             |
             +------+------+
                    |
             So sánh theo
              hợp đồng
                    |
           So telemetry riêng
~~~

**Domain snapshot** có thể chứa result, exception, số lần cập nhật DB, số business event logic, trạng thái tài nguyên, số lượt gửi lệnh nghiệp vụ. **Technical snapshot** có thể chứa danh sách advice, trace, metric, số attempt và thông tin về overhead.

Để phép thử có ý nghĩa:

1. Dùng **cùng input và policy**: tham số, danh tính, tenant, locale, quyền và version.
2. Dùng **hai fixture trạng thái tương đương nhưng độc lập**; không để mode chạy trước làm thay đổi dữ liệu của mode sau.
3. Cố định những yếu tố phi xác định có thể kiểm soát: clock, random seed, response từ dependency giả, network failure injection.
4. Chọn **tiêu chuẩn so sánh có ý nghĩa** thay vì so toàn bộ log text hoặc timestamp.
5. Chạy cả happy path lẫn exception, null, void, nhiều lượt gọi và đường không match pointcut.
6. Tách so sánh tính đúng và so sánh hiệu năng thành những phép thử riêng.
7. Kiểm cả lời gọi qua interception thực chứ không chỉ gọi trực tiếp code lõi.

Có thể biểu diễn một invariant của aspect chỉ quan sát:

~~~text
businessSnapshot(runWithoutAspect, sameInput)
==
businessSnapshot(runWithAspect, sameInput)

telemetryWithoutAspect != telemetryWithAspect
~~~

Bất biến này **không** áp dụng một cách máy móc cho aspect cố ý chặn lời gọi, cache hoặc retry.

## 5. Ví dụ Java 17 chạy được: bật/tắt aspect trace

Ví dụ dùng **Java dynamic proxy** để mô phỏng một around advice quanh interface method. Trong chế độ tắt, caller nhận trực tiếp Core. Trong chế độ bật, caller nhận proxy do TraceHandler điều khiển, ghi một sự kiện enter và một sự kiện return/throw cho mỗi lời gọi.

**Đặc điểm được kiểm tra:** kiểu int, String bình thường, null hợp lệ, void có side effect, checked IOException, unchecked IllegalArgumentException, số lần gọi target, số lần cập nhật và log quan sát. Đây là minh họa cơ chế interception bằng Java chuẩn; **không khẳng định** mọi cơ chế weaving AOP đều hoạt động giống dynamic proxy.

Tạo tệp **AspectSwitchDemo.java**:

~~~java
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

public class AspectSwitchDemo {
    interface Service {
        int calculate(int value);
        String lookup(boolean found);
        void update();
        String checked() throws IOException;
        String invalid();
    }

    static final class Core implements Service {
        final IOException io = new IOException("disk");
        final IllegalArgumentException bad =
                new IllegalArgumentException("bad");
        int calls, updates;

        public int calculate(int value) {
            calls++;
            return value * 2;
        }

        public String lookup(boolean found) {
            calls++;
            return found ? "item" : null;
        }

        public void update() {
            calls++;
            updates++;
        }

        public String checked() throws IOException {
            calls++;
            throw io;
        }

        public String invalid() {
            calls++;
            throw bad;
        }
    }

    static final class TraceHandler implements InvocationHandler {
        final Service target;
        final List<String> events = new ArrayList<>();

        TraceHandler(Service target) { this.target = target; }

        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            events.add("enter:" + method.getName());
            try {
                Object value = method.invoke(target, args);
                String kind = method.getReturnType() == void.class
                        ? "void"
                        : value == null ? "null" : "value";
                events.add("return:" + method.getName() + ":" + kind);
                return value;
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                events.add("throw:" + method.getName() + ":"
                        + cause.getClass().getSimpleName());
                throw cause;
            }
        }
    }

    record Snapshot(List<String> outcomes, int calls, int updates) {}

    static Snapshot exercise(Service port, Core core) {
        List<String> results = new ArrayList<>();
        results.add("int=" + port.calculate(3));
        results.add("null=" + port.lookup(false));
        results.add("value=" + port.lookup(true));
        port.update();
        results.add("void=done");

        try {
            port.checked();
            throw new AssertionError("missing IOException");
        } catch (IOException ex) {
            results.add("checked-same=" + (ex == core.io));
        }
        try {
            port.invalid();
            throw new AssertionError("missing IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
            results.add("runtime-same=" + (ex == core.bad));
        }

        return new Snapshot(
                List.copyOf(results), core.calls, core.updates);
    }

    public static void main(String[] args) {
        Core raw = new Core();
        Core decorated = new Core();
        TraceHandler handler = new TraceHandler(decorated);

        Service wrapped = (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[]{Service.class}, handler);

        Snapshot before = exercise(raw, raw);
        Snapshot after = exercise(wrapped, decorated);

        System.out.println("same-behavior=" + before.equals(after));
        System.out.println("calls=" + before.calls() + "/"
                + after.calls());
        System.out.println("updates=" + before.updates() + "/"
                + after.updates());
        System.out.println("without-aspect-traces=0");
        System.out.println("with-aspect-traces="
                + handler.events.size());
        System.out.println("first=" + handler.events.get(0));
        System.out.println("last=" + handler.events.get(
                handler.events.size() - 1));
        System.out.println("outcomes=" + after.outcomes());
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 AspectSwitchDemo.java
java AspectSwitchDemo
~~~

**Stdout chính xác của ví dụ:**

~~~text
same-behavior=true
calls=6/6
updates=1/1
without-aspect-traces=0
with-aspect-traces=12
first=enter:calculate
last=throw:invalid:IllegalArgumentException
outcomes=[int=6, null=null, value=item, void=done, checked-same=true, runtime-same=true]
~~~

Mã ví dụ đã được **biên dịch bằng javac --release 17 và chạy thành công trong môi trường JDK 21**. Kết quả không in số đo thời gian ngẫu nhiên nên có thể đối chiếu chính xác.

### Giải thích

- Snapshot của hai mode có cùng danh sách kết quả, sáu lần gọi target và một lần update. Record so sánh giá trị theo các thành phần và danh sách List.copyOf.
- Giá trị int 6 được proxy trả đúng dù InvocationHandler dùng kiểu Object trung gian.
- lookup(false) trả null bình thường, lookup(true) trả "item"; recorder vẫn phân biệt return:null và return:value.
- update() là void nhưng có tác dụng phụ đúng một lần; interceptor không tự xem void như nullable result.
- IOException và IllegalArgumentException giữ **cùng instance trong từng Core**, do handler unwrap InvocationTargetException rồi ném lại cause.
- Với sáu lượt gọi, mỗi lượt được ghi một enter và một return/throw; vì vậy mode bật có 12 sự kiện kỹ thuật.
- Mode tắt không có TraceHandler; nó không sinh event kỹ thuật từ aspect.

Trong ví dụ hai Core được khởi tạo riêng để không làm lẫn side effect. Danh sách events dùng ArrayList chỉ an toàn trong bài test tuần tự; ứng dụng đồng thời cần collector thread-safe. Proxy chỉ chặn lời gọi đi qua chính proxy, không bao phủ lời gọi trực tiếp vào Core hoặc mọi loại join point.

## 6. Kiểm thử pointcut và khả năng bật/tắt thực tế

**Khác biệt log** chưa chắc chứng minh pointcut đã áp dụng đúng tập join point. Cần những phép thử:

- **Match:** advice chạy tại method/call/execution được quy định.
- **Non-match:** advice không chạy tại những join point nằm ngoài phạm vi.
- **Giới hạn cơ chế:** xác định self-invocation, static method, private method và lời gọi trực tiếp có được interception hay không theo công nghệ weaving đang dùng.
- **Composition:** đối tượng được caller thật nhận là bản đã lắp advice, không phải tham chiếu vô tình tới target gốc.
- **Tắt ở mức nào:** tắt registration, tắt logic advice hay tắt exporter phải được phân biệt trong test.
- **Thứ tự:** khi nhiều aspect cùng áp dụng, kiểm thứ tự enter/exit, nhánh exception và lúc policy được quyết định.
- **Khả năng tái lập:** cùng cấu hình, cùng đầu vào phải cho cùng hành vi logic.

Ví dụ, một phương thức bên trong Core gọi trực tiếp một phương thức khác trên cùng Core có thể bypass dynamic proxy. Nhưng không được áp dụng kết luận ấy cho mọi cơ chế AOP: weaving vào method execution có thể có tập join point khác. Vì thế chỉ kết luận từ cơ chế **được cấu hình và kiểm thử**.

## 7. Kiểm chứng tương đương có điều kiện bằng differential oracle

Bài kiểm thử kiểu trả về, `null` và ngoại lệ đã mô tả các contract theo từng method. Ở đây bài toán khác: **cùng một kịch bản chạy hai chế độ cấu hình**, rồi phân loại sự khác nhau thành **được phép** hoặc **hồi quy**.

Không nên so sánh toàn bộ log hay timestamp bằng chuỗi thô. Thay vào đó, dùng một `Outcome` thử nghiệm ghi nhận các thành phần quan trọng:

| Thuộc tính của phép chạy | Aspect TẮT và BẬT khi chỉ quan sát | Nếu aspect có chủ đích thay hành vi |
| --- | --- | --- |
| Giá trị trả về và runtime type | Tương đương theo contract, có thể cần cùng identity | So theo quy tắc biến đổi được công bố |
| `null`, primitive, `void` | Giữ cùng ngữ nghĩa; không gây lỗi unboxing | Cần từng expected outcome rõ |
| Loại, cause và identity của exception | Không đổi theo hợp đồng observer | Map/wrap lỗi phải được khai báo |
| Số lần target và domain side effects | Cùng số lần nếu observer chỉ quan sát | Cache/retry/guard có thể thay đổi có chủ đích |
| Context, lock, resource còn sống sau scope | Cùng postcondition cleanup | Scope bổ sung phải được dọn và không lộ sang request khác |
| Traces, logs, metrics | Khác nhau **theo đúng sơ đồ event** của observer | So theo riêng từng concern |
| Thời lượng | Không yêu cầu hai số nanosecond bằng nhau | Benchmark overhead trong phép đo riêng |

### 7.1. Chuẩn hóa phép so sánh nhưng không xóa tín hiệu lỗi

Một phép đo thực hành có thể dùng recorder đồng bộ và dữ liệu kịch bản quyết định:

~~~text
for each scenario in [normal, null, primitive, void, checkedError, uncheckedError]:
    off = runWithAspectDisabled(freshFixtureFor(scenario))
    on  = runWithAspectEnabled(freshFixtureFor(scenario))

    assert sameContractualBusinessOutcome(off, on)
    assert sameRequiredDomainEffects(off, on)
    assert on.traceEvents == expectedEventsFor(scenario)
    assert off.traceEvents == []
    assert noLeakedContextOrResources(off, on)
~~~

Đây là **mã giả**: hai fixture phải được dựng độc lập và có cùng trạng thái ban đầu. Nếu chạy OFF rồi ON trên cùng database hoặc đối tượng mutable, phép chạy trước có thể làm thay đổi phép chạy sau; đó là lỗi thiết kế thí nghiệm, không phải lỗi aspect. Nếu mỗi lần cần ID mới, hãy so sánh các thuộc tính nghiệp vụ ổn định hoặc chuẩn hóa chỉ những trường được phép biến đổi — **không loại bỏ toàn bộ difference** chỉ để test pass.

### 7.2. Failure injection là phần bắt buộc của phép đối chiếu

Với một observer best-effort, ép bộ ghi telemetry ném lỗi trong nhánh target trả bình thường, trả `null` và ném ngoại lệ; caller vẫn phải nhận outcome gốc theo policy. Nếu aspect là enforcement hoặc audit bắt buộc, kết quả có thể khác, nhưng **phải có bảng expected và tiêu chí lỗi riêng**, không áp quy tắc “mọi sink failure đều được nuốt”.

Nếu caller nhận `CompletionStage`, hãy phân biệt outcome ở **thời điểm method trả handle** và ở **thời điểm stage hoàn tất**. Phép so sánh khi bật/tắt phải chọn đúng mốc cần quan sát và kiểm tra callback/cleanup ở mốc đó.

### 7.3. Đánh giá đúng mức bằng chứng

Nếu bật aspect khiến event mong đợi xuất hiện nhưng kết quả nghiệp vụ vẫn đúng, điều đó chứng minh một phần semantics, **chưa chứng minh pointcut match đủ mọi đường**. Vẫn cần test positive/negative và weaving thật ở các join point quan trọng. Ngược lại, nếu ON/OFF giống hệt nhau và hoàn toàn không có event ở ON, có thể aspect đã bị tắt hoặc test đi qua raw target — test không được tự tuyên bố “transparent aspect” khi chưa có positive canary.

## 8. Số lần proceed và tác dụng phụ

**Proceed count** là chỉ báo quan trọng nhưng phải diễn giải theo policy:

| Aspect | Khi bật có thể thế nào? | Điều cần kiểm |
| --- | --- | --- |
| Metrics/tracing | Mỗi lời gọi được phép qua target đúng một lần | Không nhân đôi side effect |
| Authorization | Từ chối trước target | Zero proceed khi DENY |
| Cache | Cache hit không cần target | Kết quả đúng key, TTL, tenant, quyền |
| Retry | Có nhiều attempt | Bounded retries, idempotency, kết quả cuối |
| Circuit breaker | Từ chối khi circuit open | Đúng state và exception contract |
| Transaction | Có ranh giới commit/rollback | Dữ liệu lưu bền nhất quán |

Ví dụ một thao tác trừ tồn kho có thể trả cùng kết quả hiển thị nhưng bị gọi hai lần và làm lệch số dư. Vì thế so sánh chỉ return value là không đủ: cần assert trạng thái DB, event nghiệp vụ, số lần phát message và số lần thực thi quan trọng.

Một business event logic không phải một metric invocation: retry có thể có ba attempt nhưng chỉ được tạo một sự kiện nghiệp vụ sau idempotency. Tách hai loại thông tin trong snapshot.

## 9. So sánh khi nhiều aspect phối hợp

Một chuỗi như sau:

~~~text
caller -> Authorization -> Metrics -> Cache -> Core
~~~

có ý nghĩa khác với:

~~~text
caller -> Cache -> Authorization -> Metrics -> Core
~~~

Nếu cache trả kết quả trước authorization mà không có kiểm tra tương đương, một người dùng có thể nhận dữ liệu không được phép qua cache hit. Đồng thời metrics bên ngoài/ở trong cache sẽ đếm số request khác nhau.

Test tổ hợp nên kiểm:

- DENY xảy ra trước khi dữ liệu nhạy cảm được tiết lộ.
- Cache key và policy dùng đúng ngữ cảnh và quyền.
- Tracing ghi một yêu cầu tổng hay từng retry attempt theo thiết kế.
- Retry ở trong hay ngoài transaction, có làm trùng side effect không.
- Exception translation làm thay đổi loại lỗi ở lớp quan sát nào.
- Khi tắt **một** aspect, các aspect còn lại giữ hợp đồng và thứ tự như mong đợi.

Một List<String> trong fixture có thể ghi thứ tự enter/exit của advice để so sánh chính xác, nhưng không nên vô tình ghi token, mật khẩu, thông tin tài khoản hoặc payload nhạy cảm.

## 10. Tác vụ bất đồng bộ và timing

Nếu phương thức trả Future hoặc CompletionStage, bản tắt và bản bật có thể cùng **trả handle thành công** trong khi tác vụ xử lý sau đó thất bại.

Tách hai loại phép thử:

**Return-boundary:** aspect giữ kiểu handle, không gọi join()/get() để ép thành đồng bộ, không thay đổi bất ngờ cancellation hoặc timeout.

**Completion-boundary:** cả hai mode hoàn tất bằng cùng giá trị hợp lệ, null bình thường, exceptional completion hoặc cancellation theo hợp đồng. Callback ghi trace không được làm thất bại stage mà caller sử dụng chỉ vì collector lỗi.

Nếu có đo thời gian, phân biệt submission latency, queue wait, execution duration và end-to-end completion latency. Cùng một lời gọi async có thể có kết quả thời gian khác nhau dù nghiệp vụ giống nhau. Không assert thời gian chính xác hoặc thứ tự callback không được đảm bảo.

## 11. Hiệu năng phải được đo riêng với tính đúng

Bật aspect có thể tăng CPU, allocation, latency, I/O logging và cardinality metric. Một bài test correctness không nên thất bại chỉ vì nanoTime dao động một chút.

Để đánh giá overhead:

- Chạy workload tương đương với cùng dữ liệu, số luồng và cấu hình.
- Warm-up JVM; sử dụng công cụ benchmark và đủ vòng đo thay vì một lần đo đơn lẻ.
- Báo số liệu có phân phối, ví dụ p50/p95/p99 latency, throughput hoặc allocation.
- Tách thay đổi do cache hit/miss, GC, network, collector và việc sampling.
- Xác định overhead budget; không đánh đồng performance với correctness.
- Với retry/cache, cải thiện hoặc giảm hiệu năng có thể là mục đích, nhưng **chỉ có giá trị khi kết quả còn đúng**.

Ví dụ Java trong bài không in duration nên stdout có tính xác định. Khi cần benchmarking, hãy bổ sung phép thử riêng với tiêu chí thống kê phù hợp.

## 12. Ma trận kiểm thử hồi quy

| Nhóm | Tình huống | Điều cần khẳng định |
| --- | --- | --- |
| Happy path | Giá trị trả về hợp lệ | Cùng business output nếu aspect chỉ quan sát |
| Null | Return null | Không bị đổi thành exception |
| Primitive | int/boolean | Không cast/unbox sai |
| Void | Side effect | Đúng số lượt thực thi |
| Checked error | IOException | Type/cause/identity chính xác |
| Unchecked/Error | Exception hoặc Error | Không nuốt lỗi |
| Collector | Sink ném lỗi | Không che kết quả nếu best-effort |
| Pointcut match | Join point nằm trong rule | Advice thật sự chạy |
| Pointcut non-match | Join point ngoài rule | Không can thiệp |
| Self-invocation | Lời gọi nội bộ | Đúng giới hạn interception |
| Authorization | ALLOW / DENY | Policy đúng, không bypass |
| Cache | Hit / miss / invalidation | Không dùng dữ liệu sai ngữ cảnh |
| Retry | 1 lỗi rồi thành công | Attempt count và idempotency đúng |
| Async | Success / null / error / cancel | Quan sát đúng completion |
| Composition | Hai hoặc nhiều aspects | Thứ tự và kết quả hợp đồng đúng |
| Performance | Workload ổn định | Overhead được đo riêng |

## 13. Sai lầm thường gặp

1. **Chỉ nhìn log rồi cho rằng aspect đúng:** log có thể được ghi ở sai join point hoặc target không được gọi.
2. **Dùng cùng fixture mutable cho hai mode liên tiếp:** mode trước ảnh hưởng trạng thái mode sau; phải reset hoặc tạo fixture riêng.
3. **Đòi mọi loại aspect không làm thay đổi call count:** cache/retry/authorization vốn làm thay đổi theo policy.
4. **So output console tuyệt đối:** phần trace/log được phép khác; chỉ so domain snapshot và technical snapshot theo mục đích.
5. **Tắt collector rồi gọi là tắt aspect:** advice vẫn có thể chạy, tốn chi phí hoặc thay đổi hành vi.
6. **Test chỉ thành công bình thường:** bỏ sót null, void, exception, Error, async cancellation.
7. **Chỉ gọi class lõi trong unit test:** không chứng minh pointcut/weaving được lắp đúng.
8. **Không kiểm lỗi từ advice:** metric sink có thể che lỗi nghiệp vụ gốc.
9. **Không xét việc bật aspect bảo mật là bắt buộc:** thử nghiệm tắt enforcement phải nằm trong fixture cô lập, không ảnh hưởng hệ thống thật.
10. **Dùng ngưỡng thời gian quá chặt trong unit test:** gây test không ổn định vì scheduling và GC.

## 14. Checklist thiết kế phép so sánh

1. Xác định mục đích aspect: quan sát hay thay đổi chính sách.
2. Ghi rõ chính xác bật/tắt ở mức weaving, wrapper, advice flag hay collector.
3. Xác định join point, pointcut và đường gọi thực tế.
4. Lập bảng các đại lượng **phải bằng nhau** và **được phép khác**.
5. Tạo hai fixture độc lập nhưng có cùng trạng thái ban đầu.
6. Dùng cùng input và dependency response có thể tái lập.
7. So sánh return, null, void, exception, trạng thái domain và số lần side effect.
8. Xác nhận advice chạy đúng pointcut và không chạy ngoài phạm vi.
9. Kiểm proceed count, transaction, cache và retry theo policy.
10. Thử collector failure, cancellation, concurrency và các đường lỗi.
11. Kiểm tích hợp composition/weaving thật.
12. Đo overhead riêng; đưa kiểm thử hồi quy vào build/CI.

**Kết luận:** Bật/tắt một aspect là phép thử về **hợp đồng thực thi**. Với aspect quan sát, giá trị nghiệp vụ và tác dụng phụ nên tương đương, chỉ dữ liệu quan sát khác theo thiết kế. Với aspect chính sách, khác biệt phải được giải thích bằng chính sách và kiểm chứng bằng trạng thái cuối, lỗi, quyền và số lượt gọi. Kết hợp differential test, kiểm thử pointcut và kiểm thử tích hợp là cách phát hiện những thay đổi hành vi âm thầm do AOP gây ra.


</details>

- [Quay lại đầu trang](#back-to-top)

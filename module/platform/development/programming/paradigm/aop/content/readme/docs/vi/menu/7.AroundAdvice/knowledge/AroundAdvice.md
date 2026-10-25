<a id="back-to-top"></a>

# Advice bao quanh

## Menu
- [Advice bao quanh và quyền kiểm soát luồng thực thi](#around-advice-001)
- [Gọi tiếp hành vi gốc qua cơ chế proceed hoặc cơ chế tương đương](#around-advice-002)
- [Không gọi tiếp và trách nhiệm bảo toàn hợp đồng](#around-advice-003)
- [Thay đổi tham số hoặc kết quả khi công cụ cho phép](#around-advice-004)
- [Gọi tiếp nhiều lần và nguy cơ lặp tác dụng phụ](#around-advice-005)
- [Đo thời gian bằng điểm bắt đầu và khối dọn dẹp](#around-advice-006)
- [Bắt, chuyển đổi hoặc truyền tiếp ngoại lệ](#around-advice-007)
- [Giới hạn kiểu trả về và tính tương thích của advice](#around-advice-008)
- [Phân biệt bao quanh một lời gọi đồng bộ với toàn bộ tác vụ bất đồng bộ](#around-advice-009)

## <a id="around-advice-001">Advice bao quanh và quyền kiểm soát luồng thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Advice bao quanh và quyền kiểm soát luồng thực thi

## 1. Bản chất của around advice

Trong **Aspect-Oriented Programming (AOP)**, một **pointcut** chọn những **join point** mà hành vi xuyên suốt có thể được áp dụng. **Advice** là hành vi ấy; **around advice** (advice bao quanh) có vị trí đặc biệt vì nó không chỉ làm việc *trước* hoặc *sau* điểm được chọn: nó thường có **quyền quyết định có tiếp tục vào phần thực thi bên trong hay không, tiếp tục khi nào, và kết quả hoặc ngoại lệ nào được trả ra**.

Hãy tưởng tượng lời gọi nghiệp vụ:

```text
caller -> operation(input) -> result/error
```

Khi có một around advice, đường đi có thể trở thành:

```text
caller
  |
  v
around advice
  |-- thực hiện hành vi trước
  |-- [tiếp tục vào join point hoặc chuỗi advice bên trong]
  |-- thực hiện hành vi sau
  |-- [có thể xử lý/thay đổi result hoặc error]
  v
caller
```

Phần trong ngoặc vuông **không mặc nhiên phải chạy**: tùy công cụ và hợp đồng, around advice có thể tiếp tục vào target một lần, không lần nào hoặc nhiều lần. Vì thế đây là công cụ mạnh nhưng dễ làm thay đổi tính đúng đắn của chương trình.

Bài học này giúp bạn:

1. Hiểu **around advice**, **proceed** và ranh giới điều khiển so với before/after advice.
2. Phân tích các trường hợp **tiếp tục, chặn, thay kết quả, sửa đối số, ném/chuyển lỗi, gọi lại** và các hệ quả hợp đồng.
3. Hiểu thứ tự lồng nhau của nhiều advice, quy tắc cleanup và xử lý ngoại lệ.
4. Phân biệt **proxy**, **weaving** và những đường lời gọi mà advice thực sự nhìn thấy.
5. Tự chạy ví dụ Java 17 minh họa quanh một lời gọi interface bằng dynamic proxy.
6. Biết khi nào nên dùng around advice và khi nào cần cơ chế tường minh hơn như decorator, domain policy hoặc workflow.

**Phạm vi:** Đây là kiến thức **AOP nói chung**. Không có một cú pháp around advice hay `proceed` duy nhất bắt buộc mọi ngôn ngữ/công cụ tuân theo. Tập join point, thời điểm weaving, cách truyền đối số, kiểu kết quả và số lần được phép tiếp tục phải kiểm chứng ở công cụ cụ thể.

## 2. Các khái niệm cần thống nhất trước khi điều khiển luồng

| Khái niệm | Nghĩa trong bài | Lưu ý |
| --- | --- | --- |
| **Concern** | Nhu cầu xuyên suốt như đo latency, audit, retry hoặc điều phối kỹ thuật ở nhiều vị trí. | Không phải mọi quy tắc nghiệp vụ đều nên ẩn vào aspect. |
| **Join point** | Sự kiện/điểm thực thi được mô hình AOP hỗ trợ, ví dụ một lần method execution hoặc method call. | Không đồng nghĩa một dòng source duy nhất. |
| **Pointcut** | Predicate/quy tắc chọn các join point cần advice. | Có thể dùng thông tin tĩnh và/hoặc ngữ cảnh runtime. |
| **Target** | Mã/đối tượng thực thi công việc chính. | Lời gọi có thể đi qua nhiều advice trước target. |
| **Around advice** | Lớp xử lý bao quanh phần bên trong join point theo cơ chế AOP. | Có thể thay đổi flow và kết cục nếu công cụ cho phép. |
| **Proceed / continuation** | Thao tác tiếp tục thực thi phần bên trong advice hoặc target. | Không nhất thiết đi thẳng vào target; có thể là advice kế tiếp. |
| **Weaving/interception** | Cách kết hợp advice với mã đang chạy. | Runtime proxy, class proxy, biến đổi source/bytecode hoặc công cụ khác. |

Trong một số API, `proceed()` là method do công cụ cung cấp. Trong những API khác, continuation có thể là callback, hàm bọc, closure hoặc lời gọi tới object gốc. Dù hình thức khác, hãy luôn hỏi: **“Khi gọi tiếp, chính xác phần nào sẽ chạy?”**

### 2.1. Around advice khác before và after ở mức quyền điều khiển

- **Before:** Thường chạy trước join point đã chọn; có thể gây thất bại bằng cách ném lỗi nhưng không nhất thiết cung cấp cơ chế chọn số lần gọi target.
- **After returning:** Phản ứng khi điểm kết thúc bình thường.
- **After throwing:** Phản ứng khi điểm kết thúc bằng ngoại lệ thoát ra.
- **After/finally:** Dọn dẹp hoặc ghi nhận khi kết thúc qua các đường hợp lệ.
- **Around:** Có thể tổ chức cả các hành vi trên trong một cấu trúc bao quanh **và chủ động gọi hoặc không gọi phần thực thi bên trong**.

Không đồng nhất “around có thể làm mọi việc” với “nên dùng around cho mọi concern”. Nếu chỉ cần bộ đếm khi method trả về, advice sau khi trả về có thể diễn đạt ý định rõ và giảm nguy cơ vô tình bỏ qua target.

### 2.2. Vì sao continuation là ranh giới kiến trúc quan trọng?

Một câu lệnh thường gặp ở around advice:

```text
result = proceed()
```

mang nhiều ý nghĩa đồng thời: chuyển quyền điều khiển vào phần bên trong, chấp nhận rằng side effect có thể xảy ra, chờ phần đó trả kết quả hoặc ném lỗi, rồi tiếp tục đoạn code phía sau của around advice.

Không phải mọi engine đều bảo đảm `proceed` chỉ gọi target một lần. Khi có nhiều aspect, nó có thể chạy chuỗi advice bên trong; do vậy chính advice bên trong lại có thể bỏ qua hoặc chạy nhiều lần. Để phân tích đúng, phải xét **toàn bộ chuỗi continuation**, không chỉ một wrapper.

## 3. Mô hình điều khiển: ba đường đi cơ bản

Giả sử target `charge()` được chọn và around advice có quyền bao quanh.

### 3.1. Tiếp tục một lần

```text
around:
    before()
    result = proceed()
    after()
    return result
```

Luồng là `before → target/inner advice → after` trên đường thành công. Nếu `proceed()` ném lỗi thì `after()` viết ngay phía sau **không chạy**, trừ khi có `finally` hoặc `catch` phù hợp.

Dùng khi muốn quan sát latency, correlation, logging kỹ thuật, nhưng vẫn cần xác định hành vi nếu chính `before`/`after` ném lỗi.

### 3.2. Không tiếp tục

```text
around:
    if resultCanBeReused():
        return cachedResult
    return proceed()
```

Ở nhánh cache hit, target **không chạy**. Điều này có thể hợp lý nếu giá trị cache còn hiệu lực, hợp đồng cho phép bỏ qua target và không bỏ sót side effect cần thiết.

Các ví dụ khác: từ chối yêu cầu tại biên, trả fallback, mô phỏng kết quả trong test, rate limiting. Tuy nhiên, chặn một thao tác nghiệp vụ có side effect không phải quyết định nhẹ: caller có thể tưởng thao tác đã được thực hiện nếu advice trả về một “kết quả thành công” giả.

### 3.3. Tiếp tục nhiều lần

```text
around:
    for attempt in allowedAttempts:
        try:
            return proceed()
        catch transientFailure:
            if canRetrySafely():
                continue
            throw
```

Đây là cách diễn đạt ý tưởng **retry**, không phải cú pháp chạy trực tiếp. Nếu cho phép gọi tiếp nhiều lần, target hoặc advice bên trong có thể thực thi nhiều lượt. Phải xác định phương thức có **idempotent** không, có thể tạo side effect nào, ngoại lệ nào có thể thử lại, backoff/timeout tổng, số lần tối đa và cách chống lặp.

Một số engine **không hỗ trợ** gọi continuation nhiều lần theo ngữ nghĩa bạn mong muốn. Trước khi thiết kế retry bằng around advice, phải xác minh tài liệu công cụ và chạy thử cùng target có side effect để hiểu contract.

## 4. `proceed`: quyền tiếp tục chứ không đơn thuần là “gọi hàm đích”

### 4.1. `proceed()` thường có một continuation cụ thể

Ví dụ có hai around advice A và B, trong đó A ở ngoài B:

```text
caller
  |
  v
A.before
  |
  v
A.proceed() ---> B.before
                   |
                   v
                B.proceed() ---> target
                   |
                B.after
  |
A.after
  |
caller
```

Nếu cả hai gọi tiếp đúng một lần và target trả về bình thường, thứ tự dễ dự đoán:

```text
A before
B before
TARGET
B after
A after
```

Nếu B **không gọi tiếp**, target không chạy, nhưng A vẫn có thể nhìn thấy **một kết quả trả về bình thường** từ B. Nếu B tự ném lỗi thì A có thể thấy lỗi đó trong `catch`. Nếu A không gọi tiếp, B và target không được chạm tới ở đường đó.

### 4.2. Thứ tự advice ảnh hưởng đến ngữ nghĩa

Đảo thứ tự thành B ngoài A sẽ đảo vùng quan sát. Ví dụ, đặt đo latency ở ngoài retry đo **toàn bộ khoảng thời gian nhiều lần thử**, còn đo latency bên trong retry có thể đo **mỗi attempt** riêng.

Với cache, một thứ tự có thể kiểm tra cache trước khi ghi trace của target; thứ tự khác ghi lại cả cache hit. Với audit, việc ghi “thành công” trước transaction commit không có cùng ngữ nghĩa với xác nhận kết quả bền vững sau commit.

Không dựa vào thứ tự viết file, tên class hoặc thứ tự load tình cờ. Hãy xác nhận quy tắc precedence/order của công cụ và có test ghi nhận thứ tự thực tế.

### 4.3. Kiểu kết quả và đối số phải tuân theo hợp đồng

Một continuation có thể có chữ ký tương đương:

```text
proceed(): Result
proceed(newArguments): Result
```

Các chữ ký này chỉ là giả mã. Không phải công cụ AOP nào cũng cho phép thay đối số khi tiếp tục; với cơ chế proxy tự viết, handler có thể chuyển một mảng đối số khác vào target. Khi được phép, cần giữ đúng số lượng, thứ tự, kiểu, quy tắc `null`, generic/primitive và những bất biến của phương thức.

Tương tự, around advice trả kết quả thay thế phải tương thích return type theo hợp đồng. Nếu method Java có kiểu `int`, cố trả `null` từ handler có thể dẫn tới lỗi khi unboxing; nếu trả object sai kiểu có thể phát sinh `ClassCastException` hoặc lỗi khác khi caller nhận kết quả. Với `void`, không nên dùng giá trị trả về như tín hiệu nghiệp vụ tùy ý.

## 5. Around advice có thể can thiệp vào năm thứ khác nhau

### 5.1. Trước khi target chạy

Một advice có thể ghi thời điểm bắt đầu, khởi tạo span, kiểm tra ngưỡng kỹ thuật hoặc gắn context. Điều này tương tự before advice nhưng thuộc lớp around bao quanh.

**Cần lưu ý:** Nếu thao tác trước `proceed` lỗi, target có thể **không được gọi**. Đây là thay đổi quan sát được ở caller; chính sách “best-effort logging” không nên vô tình làm hỏng nghiệp vụ vì backend log tạm thời thất bại.

### 5.2. Quyết định có gọi tiếp hay không

Bằng cách `return` sớm hoặc `throw`, around advice có thể chặn phần bên trong. Ví dụ:

```text
if exceededRateLimit:
    throw TooManyRequests
else:
    return proceed()
```

Giả mã này biểu đạt rate limiting tại một **biên có hợp đồng rõ**. Trả lỗi từ advice khác với target đã thực hiện một phần công việc rồi lỗi. Nếu logic cho phép chặn là chính sách bảo mật/ủy quyền, còn phải bảo đảm **mọi đường đi cần được bảo vệ thực sự đi qua điểm can thiệp**; một proxy bị bypass không phải lớp bảo vệ duy nhất đáng tin.

### 5.3. Thay đổi input trước khi tiếp tục

Nếu công cụ hỗ trợ, advice có thể chuẩn hóa dữ liệu kỹ thuật:

```text
newArgs = sanitizeForProtocol(originalArgs)
return proceed(newArgs)
```

Nhưng “sanitize” không được âm thầm sửa lệnh nghiệp vụ hoặc đổi đối tượng được ủy quyền. Những biến đổi đầu vào ảnh hưởng giá trị đơn hàng, quyền truy cập hoặc logic tính toán cần có vị trí tường minh và test domain.

### 5.4. Thay đổi kết quả sau khi tiếp tục

```text
result = proceed()
return decorateResult(result)
```

Ví dụ chuyển đổi một đối tượng kết quả thành wrapper thống nhất ở biên giao tiếp. Phải kiểm soát type, `null`, identity, immutability và side effect. Nếu method vốn chỉ trả trạng thái thành công sau khi thao tác bền vững, advice không nên tự dựng kết quả làm caller hiểu sai rằng commit đã xong.

### 5.5. Bắt, truyền hoặc biến đổi ngoại lệ

```text
try:
    return proceed()
catch error:
    recordFailureBestEffort(error)
    throw same error
```

Nếu mục tiêu chỉ là quan sát, **ném lại ngoại lệ gốc** thường là lựa chọn bảo toàn hợp đồng. Nếu chuyển `IOException` thành lỗi domain hoặc fallback, cần chính sách có chủ đích, phân biệt loại lỗi được chuyển và giữ `cause` thích hợp. Tránh che lỗi gốc bằng lỗi logging/cleanup.

## 6. Cấu trúc `try/catch/finally` quanh `proceed`

Với around advice đo thời gian, một cấu trúc điển hình:

```text
start = monotonicClock()
try:
    return proceed()
catch error:
    recordFailure(error)
    throw same error
finally:
    recordDuration(monotonicClock() - start)
    closeContext()
```

Đây là giả mã tổng quát. `monotonicClock` là nguồn thời gian đơn điệu (không phụ thuộc chỉnh lại đồng hồ lịch) dùng để đo khoảng thời gian. Thời gian đo **bao gồm những lớp advice nằm bên trong**, không chỉ thân target.

### 6.1. Khi `proceed` trả về bình thường

- Giá trị từ phần bên trong được đưa về around advice.
- Around advice có thể giữ nguyên, biến đổi hoặc thay thế nó.
- `finally` vẫn cần dọn context trên đường `return`.
- Thông điệp “thành công” có thể chỉ nghĩa **execution trả về bình thường**, chưa chắc nghĩa **giao dịch đã commit** hoặc công việc async đã hoàn thành.

### 6.2. Khi `proceed` ném lỗi

- `catch` của advice ngoài có thể thấy ngoại lệ **thoát qua vùng continuation**.
- Nếu advice dưới bắt lỗi rồi trả fallback, advice trên có thể thấy kết quả bình thường.
- Nếu advice dưới đổi lỗi, advice trên thấy lỗi mới.
- `finally` thường chạy khi unwind dù có lỗi, nhưng chính code trong `finally` cũng có thể ném lỗi và **che lỗi ban đầu**.

Với Java, nếu `recordDuration` ném lỗi khi target đã ném lỗi, caller có thể nhận lỗi đo lường thay vì lỗi target. Nếu instrumentation không bắt buộc ảnh hưởng nghiệp vụ, hãy bao các thao tác telemetry bằng chính sách lỗi best-effort rõ ràng. Không có giải pháp một dòng áp dụng được cho mọi mức độ quan trọng của audit, metrics và cleanup.

### 6.3. Bắt `Throwable` không đồng nghĩa nuốt mọi `Error`

Trong Java, `Throwable` gồm `Exception` và `Error`. Một implementation quanh `proceed` có thể cần giữ nguyên *mọi* throwable để cleanup, nhưng không nên “xử lý thành công” các `Error` nghiêm trọng bằng fallback ngẫu nhiên. Bắt → quan sát tối thiểu → ném lại khác với bắt → che lỗi.

### 6.4. Checked exceptions trong proxy

Khi tự dùng JDK dynamic proxy, nếu handler ném **checked exception** không phù hợp danh sách `throws` của method interface, proxy có thể bọc trong `UndeclaredThrowableException`. Nếu dùng reflection `Method.invoke`, exception của target bị bọc trong `InvocationTargetException` và cần gỡ `getCause()` một cách có chủ đích. Cơ chế weaving khác có thể có hợp đồng ngoại lệ khác.

## 7. Around advice và các khái niệm cache, retry, circuit breaker

### 7.1. Cache hit là trường hợp `proceed` có thể chạy 0 lần

```text
around readOperation(key):
    hit = cache.lookup(key)
    if hit is valid:
        return hit
    value = proceed()
    cache.store(key, value)
    return value
```

Những điều kiện cần trước khi dùng:

- Phương thức có thể được gọi là **read-like** hay có side effect quan trọng?
- Khóa cache có đủ tham số/tenant/user/locale/phiên bản dữ liệu?
- Kết quả lỗi hoặc giá trị `null` được cache theo chính sách nào?
- Có yêu cầu invalidation, TTL hoặc consistency?
- Có race giữa nhiều caller cùng cache miss không?
- Advice có thay đổi identity hoặc quyền truy cập dữ liệu không?

Cache skip có thể phù hợp với thao tác đọc thuần; rất nguy hiểm với thao tác “đọc đồng thời tăng số lượt xem” nếu việc tăng là side effect phải xảy ra.

### 7.2. Retry là trường hợp gọi continuation nhiều lần

Một retry đúng cần:

1. **Giới hạn số lần thử** và ngân sách thời gian tổng.
2. Chỉ retry các lỗi thực sự **transient** được công bố.
3. **Idempotency key** hoặc bảo đảm khác nếu thao tác có side effect.
4. Chiến lược **backoff/jitter** hợp lý, không tạo retry storm.
5. Phân biệt log **attempt failure** và **logical-operation failure**.
6. Xác định các lớp advice ở trong/ngoài retry; context từng lần thử được quản lý đúng.
7. Không retry theo phương pháp dựa vào exception message ngẫu nhiên.
8. Xác minh engine có cho phép gọi lại `proceed` với ngữ nghĩa phù hợp hay không.

Ví dụ một thao tác `transferMoney`: nếu lời gọi thứ nhất đã ghi nợ nhưng response bị mất, tiếp tục lần hai có thể trừ tiền lần nữa. Vấn đề nằm ở nghiệp vụ phân tán và idempotency, **không** được giải bằng việc chỉ thêm vòng `for` vào around advice.

### 7.3. Circuit breaker và giới hạn lưu lượng

Một around advice có thể trả fallback hoặc từ chối lời gọi khi một circuit đang mở. Nhưng trạng thái breaker có ý nghĩa theo endpoint/nguồn lỗi, cần concurrency control, time window, half-open probe và metrics chính xác. Nếu mọi giao dịch đều bị chặn bằng fallback “thành công”, hệ thống có thể mất tính đúng đắn.

Rate limiting có thể chặn trước target khi vượt hạn ngạch. Hãy xác định ranh giới tin cậy, tính nhất quán khi nhiều instance và exception/error contract, thay vì chỉ gắn một aspect tại vài method được gọi.

## 8. Ví dụ Java 17 hoàn chỉnh: continuation, skip và exception

Ta minh họa một **around advice** bằng JDK dynamic proxy. Đây là **một cơ chế mô phỏng AOP** với continuation do chính ta định nghĩa; nó không phải cú pháp phổ quát của mọi công cụ và không cung cấp mọi loại join point.

Cấu trúc:

- `TaskApi` là interface của target.
- `TaskService` thực hiện logic nghiệp vụ.
- `Proxy.newProxyInstance` tạo proxy để caller gọi qua.
- `traceAround` là around advice **bên ngoài**, ghi enter, returning/throwing và exit.
- `filterAround` là around advice **bên trong**, bỏ qua target nếu đối số là `"SKIP"`.
- `Proceed` là continuation mô phỏng `proceed` bằng Java functional interface.
- `invokeTarget` dùng reflection, gỡ wrapper để exception gốc tiếp tục tới caller.

Tạo file **`AroundControlDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AroundControlDemo {
    interface TaskApi {
        String run(String input);
    }

    static final class TaskService implements TaskApi {
        @Override
        public String run(String input) {
            System.out.println("TARGET " + input);
            if ("FAIL".equals(input)) {
                throw new IllegalStateException("target failed");
            }
            return "DONE:" + input;
        }
    }

    @FunctionalInterface
    interface Proceed {
        Object call() throws Throwable;
    }

    static Object traceAround(String methodName, Proceed next)
            throws Throwable {
        System.out.println("TRACE enter " + methodName);
        try {
            Object value = next.call();
            System.out.println("TRACE returning " + methodName);
            return value;
        } catch (Throwable original) {
            System.out.println("TRACE throwing "
                    + original.getClass().getSimpleName());
            throw original;
        } finally {
            System.out.println("TRACE exit " + methodName);
        }
    }

    static Object filterAround(Method method, Object[] args, Proceed next)
            throws Throwable {
        if (method.getName().equals("run")
                && "SKIP".equals(args[0])) {
            System.out.println("FILTER skip");
            return "SKIPPED";
        }
        return next.call();
    }

    static Object invokeTarget(TaskApi target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static TaskApi wrapped(TaskApi target) {
        return (TaskApi) Proxy.newProxyInstance(
                TaskApi.class.getClassLoader(),
                new Class<?>[] { TaskApi.class },
                (proxy, method, args) ->
                        traceAround(method.getName(),
                                () -> filterAround(method, args,
                                        () -> invokeTarget(target, method, args)))
        );
    }

    public static void main(String[] args) {
        TaskApi target = new TaskService();
        TaskApi proxy = wrapped(target);

        System.out.println("CALLER " + proxy.run("A"));
        System.out.println("CALLER " + proxy.run("SKIP"));

        try {
            proxy.run("FAIL");
        } catch (IllegalStateException ex) {
            System.out.println("CALLER caught " + ex.getMessage());
        }

        System.out.println("CALLER " + target.run("DIRECT"));
    }
}
```

Chạy với JDK 17 trở lên:

```bash
javac AroundControlDemo.java
java AroundControlDemo
```

**Stdout kỳ vọng:**

```text
TRACE enter run
TARGET A
TRACE returning run
TRACE exit run
CALLER DONE:A
TRACE enter run
FILTER skip
TRACE returning run
TRACE exit run
CALLER SKIPPED
TRACE enter run
TARGET FAIL
TRACE throwing IllegalStateException
TRACE exit run
CALLER caught target failed
TARGET DIRECT
CALLER DONE:DIRECT
```

### 8.1. Giải thích đường bình thường

`proxy.run("A")` vào `traceAround`. Advice ngoài in `TRACE enter run`, gọi continuation vào `filterAround`. `filterAround` không thấy `SKIP`, nên gọi tiếp `invokeTarget`, target in `TARGET A` rồi trả `DONE:A`. Advice ngoài in `TRACE returning run`, `finally` in `TRACE exit run`, sau cùng caller in kết quả.

Ở đường này, target thực thi **đúng một lần**.

### 8.2. Giải thích đường skip

`proxy.run("SKIP")` vẫn vào `traceAround` trước. Advice bên trong thấy đối số đặc biệt, in `FILTER skip` và trả `SKIPPED` **mà không gọi `next.call()`**. Vì vậy không có `TARGET SKIP`.

Điều quan trọng: advice ngoài thấy `filterAround` **trả về bình thường**, nên nó in `TRACE returning run`, dù target chưa từng chạy. Đây chính là lý do không đồng nhất “around trả kết quả thành công” với “target thực hiện thành công”.

`"SKIP"` chỉ là tín hiệu thực hành. Trong hệ thống thật, việc có được skip hay không cần dựa vào hợp đồng rõ về cache, policy hoặc trạng thái; không nên dựa vào một chuỗi input đặc biệt tùy tiện.

### 8.3. Giải thích đường lỗi

`proxy.run("FAIL")` đi qua cả hai advice rồi vào target. Target in `TARGET FAIL` trước khi ném `IllegalStateException`. Reflection bọc lỗi target vào `InvocationTargetException`; `invokeTarget` gỡ wrapper và ném đúng cause. `traceAround` ghi `TRACE throwing IllegalStateException`, ném lại cùng lỗi và chạy `finally`; caller bắt `IllegalStateException` và in message.

Nếu `filterAround` chọn trả fallback thay vì truyền lỗi, `traceAround` sẽ quan sát một lần return bình thường; các tầng advice không nhất thiết nhìn thấy cùng một kết cục.

### 8.4. Giải thích lời gọi không qua proxy

`target.run("DIRECT")` gọi trực tiếp vào đối tượng thật nên chỉ in `TARGET DIRECT`; không có dòng `TRACE` hoặc `FILTER`. JDK dynamic proxy chỉ chặn **lời gọi được thực hiện qua proxy**. Đây không phải giới hạn áp dụng nguyên xi cho mọi công cụ AOP có weaving bytecode.

### 8.5. Hệ quả của thứ tự advice

Đang dùng `traceAround` bên ngoài `filterAround`. Nếu đổi thành `filterAround` ở ngoài và `traceAround` ở trong, với input `SKIP`, `filterAround` có thể trả sớm **trước khi trace chạy**, nên không có dòng TRACE cho cache/skip. Hai thứ tự đo hai phạm vi khác nhau. Phải đặt theo mục tiêu quan sát, không đơn giản theo thứ tự thuận mắt.

### 8.6. Các giới hạn cố ý của ví dụ

- Demo chỉ có một method interface; không minh họa metadata pointcut DSL hay field/constructor join point.
- Không có policy retry, mutation đối số hoặc replacement result vượt ngoài nhánh skip; các quyền đó đã được phân tích ở phần khác.
- Không thể dùng demo này để suy ra mọi around advice của mọi engine cho phép nhiều lần `proceed`.
- Lệnh in log có thể có side effect và lỗi; đây chỉ là cách nhìn thấy thứ tự trong bài học.
- Với method checked exception, JDK proxy có quy tắc về `throws` và `UndeclaredThrowableException`; demo dùng unchecked exception để tránh nhầm lẫn khi tập trung vào continuation.
- `TaskService.run` có thể được gọi trực tiếp, nhấn mạnh nhu cầu bảo đảm call path khi sử dụng proxy trong ứng dụng thật.

## 9. Around advice trong môi trường bất đồng bộ

Một method Java có thể trả `CompletableFuture<Result>`, `CompletionStage<Result>` hoặc một handle bất đồng bộ khác. Around advice ở execution của method có thể thấy:

```text
enter method
  -> target lập lịch công việc
  -> trả future ngay
exit method
                  ... công việc chạy sau ...
                  -> future hoàn tất (success/failure)
```

Nếu advice chỉ đo từ trước `proceed()` đến khi `proceed()` trả future, số đo là **thời gian gọi/tạo tác vụ**, không phải toàn bộ thời gian xử lý. Tương tự, method trả future bình thường vẫn có thể tạo một future sau đó hoàn thành exceptionally.

Muốn quan sát **completion**, cần gắn hook tại future/task completion bằng API thích hợp, nhưng phải xác minh:

- Hook có vô tình **thay đổi result hoặc exception** của future trả cho caller không?
- Cancellation và timeout được ghi như success, failure hay trạng thái riêng?
- Callback chạy trên thread nào? Context có được truyền và dọn đúng?
- Nếu callback đo lường ném lỗi, stage nào bị ảnh hưởng?
- Thời gian end-to-end có tính cả queue delay, retry và scheduling không?

Ví dụ mã minh họa **không chạy độc lập**:

```java
CompletionStage<String> resultStage = invokeAsync();
resultStage.whenComplete((value, error) -> {
    if (error != null) {
        // Ghi failure theo policy; không làm lộ dữ liệu nhạy cảm.
    }
});
```

Đoạn này chỉ mô tả hook completion, giả định `invokeAsync()` đã tồn tại. Không giả định chỉ gọi `whenComplete` sẽ bảo toàn mọi chi tiết exception/cancellation; phải xem hợp đồng API, đặc biệt nếu callback ném lỗi hoặc dùng stage được trả về từ phép đăng ký.

### 9.1. Context và vòng đời tài nguyên

Một around advice tạo correlation ID hoặc span có thể dùng `ThreadLocal`. Nếu continuation chuyển công việc sang executor, context theo thread không tự theo sang task khác. Mở scope lúc bắt đầu và đóng scope trong `finally` ở thread ban đầu là một chuyện; đóng span khi task thực sự hoàn tất là chuyện khác. Tránh để tài nguyên “mở mãi” hoặc đóng quá sớm vì coi return của future là completion.

## 10. Proxy, weaving và giới hạn quyền kiểm soát

Không phải mọi engine AOP nhìn thấy cùng những sự kiện, dù tên advice cùng là “around”.

### 10.1. Interface proxy

Với dynamic proxy theo interface:

```text
caller -> proxy/handler -> target.method(...)
caller ----------------> target.method(...)   // đi tắt
```

Chỉ đường đầu đi qua advice trong handler. Lời gọi nội bộ trên `this` ở target không mặc nhiên quay lại proxy. Một around advice không thể kiểm soát luồng thực thi mà cơ chế của nó chưa từng nhận.

### 10.2. Class proxy

Một số công nghệ tạo proxy dựa trên lớp/subclass hoặc bytecode đặc thù. Khả năng can thiệp có thể phụ thuộc `final`, `private`, `static`, constructor, visibility, dispatch và cách đối tượng được tạo. Không nên suy từ interface proxy rằng class proxy sẽ có tập điểm hệt nhau.

### 10.3. Weaving vào source/bytecode

Weaving có thể chèn hoặc biến đổi hành vi ở compile-time, load-time hoặc một thời điểm công cụ hỗ trợ. Tập join point có thể rộng hơn proxy đơn giản, nhưng phụ thuộc cấu hình build, classloader, loại instruction và phiên bản runtime.

Một pointcut khớp method trên giấy không chứng minh mã đang chạy đã được weave. Phải xác nhận artifact thật, danh sách lớp trong phạm vi và positive/negative integration tests.

### 10.4. Quyền điều khiển chỉ có giá trị trong phạm vi được bảo đảm

Nếu dùng around advice để bảo vệ thao tác có tính an ninh cao, có đường gọi trực tiếp target hoặc module chưa được weave là một lỗ hổng lớn. Những bất biến như quyền sở hữu dữ liệu, hạn mức giao dịch hoặc kiểm soát trạng thái quan trọng nên được áp ở **biên tin cậy và mô hình nghiệp vụ**, thay vì phụ thuộc hoàn toàn vào một lời khuyên “gắn aspect là đủ”.

## 11. Các rủi ro nâng cao của around advice

### 11.1. Tính trong suốt của hợp đồng (semantic transparency)

Một advice quan sát được xem là “trong suốt” khi caller nhận cùng **giá trị, exception, side effect, thứ tự hợp lệ** như không có advice, trừ overhead/telemetry được công bố. Around advice có thể phá điều này bằng skip, gọi lặp, đổi đối số, biến đổi kết quả hoặc exception.

Cần ghi rõ: advice **chỉ quan sát**, **chuyển đổi có hợp đồng** hay **điều phối control flow**. Không quảng cáo một aspect điều phối thành “chỉ thêm log”.

### 11.2. Reentrancy và đệ quy không mong muốn

Một advice ghi metric bằng một method cũng bị pointcut chọn có thể tạo vòng:

```text
around advice
  -> metrics.record()
       -> around advice
            -> metrics.record()
                -> ...
```

Cần giới hạn pointcut và loại trừ code instrumentation, không chỉ dùng cờ tạm thời mà bỏ qua tính thread-safe và cleanup. Nếu engine hỗ trợ chọn theo luồng điều khiển (*control-flow pointcut*), hãy xác minh ngữ nghĩa trong recursion và async.

### 11.3. Thread safety và trạng thái aspect

Một aspect singleton giữ biến mutable `currentStartTime` có thể bị caller đồng thời ghi đè, dẫn tới số đo sai. Thời điểm bắt đầu thường nên nằm trong **local variable của lần invocation** hoặc một context được quản lý rõ. Nếu lưu per-thread state, cần cleanup và chiến lược truyền context khi async.

### 11.4. Result conversion và nullability

Around advice trả object `null` cho method trả primitive có thể dẫn đến lỗi. Đổi kiểu exception hoặc kết quả khi caller không dự kiến sẽ làm test pass ở nhánh đơn giản nhưng fail ở nhánh đa hình, generic, serialization hoặc API công khai. Với một engine có type checking tại compile-time, một số lỗi bị bắt sớm; với reflection/proxy, có thể chỉ lộ ở runtime.

### 11.5. Side effect của advice

Advice không chỉ quan sát “miễn phí”: log đồng bộ, I/O mạng, ghi audit, lock, cấp phát, đo metrics có thể làm trễ target hoặc ném lỗi. Một around advice có thể tăng latency mỗi lời gọi, tạo deadlock khi đặt lock sai, hoặc làm mất tín hiệu lỗi gốc nếu `finally` ném ngoại lệ.

### 11.6. Tần suất và hiệu năng

Một pointcut rộng ở hot path có thể kích hoạt rất nhiều around advice. Nếu bên trong có nhiều wrapper, overhead và số lần logger gọi tăng. Đánh giá theo **lần runtime thực tế**, không đếm số method đã đánh dấu. Dùng benchmark phù hợp, giới hạn cardinality và sampling, tránh log toàn bộ arguments/results nhạy cảm.

## 12. Thiết kế thứ tự advice một cách có chủ đích

Hãy xét hai khía cạnh: **thứ tự lồng** và **ý nghĩa của thành công**.

| Chuỗi ví dụ | Điều quan sát được | Nguy cơ |
| --- | --- | --- |
| `Trace(Retry(Target))` | Trace ngoài bao cả các attempt và backoff. | Một trace chỉ thấy lỗi cuối cùng, bỏ lỡ chi tiết từng attempt nếu không instrument bên trong. |
| `Retry(Trace(Target))` | Mỗi attempt có thể có trace riêng. | Có nhiều trace cho một logical operation; cần correlation. |
| `Metrics(Cache(Target))` | Đo cả cache hit/miss. | Không phân biệt độ trễ cache hit và target nếu thiếu nhãn. |
| `Cache(Metrics(Target))` | Metrics bên trong có thể chỉ chạy lúc cache miss. | Metric không đại diện tổng request. |
| `Audit(Transaction(Target))` | Audit ngoài có thể quan sát kết cục transaction boundary. | Cần xác minh audit có được ghi bền vững và thứ tự commit. |
| `Transaction(Audit(Target))` | Audit trong transaction boundary. | Audit thành công có thể xảy ra trước khi commit hoàn tất. |

Tên hàm trong bảng chỉ là **ký hiệu lồng nhau**: `Outer(Inner(Target))`. Không có bảo đảm chính sách transaction/audit nào được hiện thực chỉ nhờ thứ tự aspect; phải xem engine và hệ thống lưu trữ.

### Nguyên tắc để chọn thứ tự

1. Viết rõ **định nghĩa outcome**: target return, commit, async completion hay logical operation sau retry.
2. Xác định advice nào được gọi khi nhánh bên trong skip.
3. Xác định nơi cần quan sát attempt riêng, nơi cần quan sát tổng.
4. Xác định ai có quyền swallow/convert exception.
5. Dùng test ghi log tên advice để xác minh precedence thực, không suy đoán.

## 13. Kiểm thử around advice theo ma trận luồng thực thi

Không thể kiểm thử around advice chỉ với một ca target trả “OK”. Ít nhất cần positive/negative tests theo control flow:

| Ca | Thiết lập | Điều phải xác nhận |
| --- | --- | --- |
| **Proceed một lần** | Target trả bình thường. | Target thực thi đúng một lần; result và side effect không đổi ngoài phần công bố. |
| **Skip** | Điều kiện cho phép bỏ qua target. | Target không chạy, result/fallback đúng hợp đồng, cleanup advice ngoài vẫn chính xác. |
| **Exception từ target** | Target ném unchecked/checked exception. | Advice ngoài quan sát đúng lỗi; caller nhận exception phù hợp. |
| **Exception từ advice** | Logging/filter tự ném lỗi. | Vùng `catch/finally` nào chạy; có che lỗi target không? |
| **Thay result** | Advice chuyển đổi output. | Kiểu, nullability, identity, consistency được bảo đảm. |
| **Thay args** | Advice thay đối số khi proceed (nếu hỗ trợ). | Target nhận đúng đối số và không phá invariant. |
| **Retry** | Target lỗi lần đầu rồi thành công hoặc lỗi luôn. | Số lần target chạy, backoff, timeout, idempotency và metric đúng. |
| **Nhiều advice** | Hai hoặc ba wrapper cùng chọn join point. | Thứ tự nesting, kết cục từng tầng và số lần advice chạy đúng. |
| **Direct target call** | Caller giữ tham chiếu target thay vì proxy. | Phạm vi can thiệp đúng với cơ chế đang dùng. |
| **Self-invocation** | Target gọi method khác bằng `this`. | Không suy đoán: kiểm tra thực tế theo proxy/weaving. |
| **Async return** | Target trả stage rồi lỗi ở tương lai. | Phân biệt return bình thường và completion failure. |
| **Concurrency** | Nhiều caller đồng thời. | Context không rò, state không trộn, không race. |
| **Hot path** | Gọi nhiều lần qua pointcut rộng. | Overhead chấp nhận được, log/metric cardinality được kiểm soát. |

Đối với skip/retry, nên dùng **bộ đếm target invocation** trong test để chứng minh số lần phần đích thật sự chạy. Việc thấy log của advice “before/after” không đủ vì advice có thể trả sớm hoặc chạy tiếp nhiều lần.

## 14. So sánh around advice với các kỹ thuật thay thế

| Nhu cầu | Kỹ thuật đáng xem xét | Vai trò around advice |
| --- | --- | --- |
| Đo thời gian/log lỗi ở nhiều method độc lập. | Advice before/after; instrumentation tại biên, decorator. | Hữu ích nếu cần bao quanh và cleanup cả success/failure; không nhất thiết là lựa chọn duy nhất. |
| Caching các truy vấn thuần và có khóa rõ. | Memoization/decorator/cache client. | Có thể skip target, nhưng phải bảo đảm semantics cache. |
| Retry I/O transient. | Retry tại client/boundary với idempotency và timeout. | Có thể điều phối continuation nếu engine cho phép, cần test nghiêm ngặt. |
| Điều phối giao dịch nhiều bước. | Domain service, workflow/state machine. | Không nên che các quyết định nghiệp vụ trong pointcut/advice. |
| Kiểm tra quyền truy cập bắt buộc. | Trusted boundary + domain authorization. | Có thể hỗ trợ tại điểm được bảo đảm, không là lớp bảo vệ duy nhất khi có bypass. |
| Transform protocol tại một biên cụ thể. | Adapter/interceptor minh bạch. | Chỉ nên dùng nếu tập điểm và hợp đồng type/result rõ. |
| Quan sát hoàn tất bất đồng bộ. | Completion callback/instrumentation tại async boundary. | Around method call đơn lẻ có thể kết thúc quá sớm. |

**Nguyên tắc:** Dùng quyền điều khiển của around advice khi vấn đề thật sự cần nó và tập join point được bảo đảm. Nếu chỉ muốn code ngắn hơn cho một luồng nghiệp vụ phức tạp, around advice có thể làm kiến trúc khó hiểu thay vì đơn giản hơn.

## 15. Những ngộ nhận thường gặp

**“Around advice luôn gọi target.”** Không. Nó có thể trả sớm hoặc ném lỗi trước continuation; target có thể không chạy.

**“Around advice luôn có thể gọi `proceed` nhiều lần.”** Không thể kết luận cho mọi công cụ. Cần xác minh engine và loại join point đang dùng.

**“Một lần `proceed` đồng nghĩa target chạy đúng một lần.”** Chưa chắc khi phần bên trong có advice khác cho phép retry/skip. Phải xem toàn chuỗi.

**“Trả về bình thường từ around nghĩa là nghiệp vụ thành công.”** Không nếu kết quả do cache/fallback/filter tạo ra mà target chưa chạy; cũng không bảo đảm transaction đã commit hoặc task async hoàn tất.

**“Bắt Throwable rồi trả mặc định giúp hệ thống bền vững.”** Có thể nuốt lỗi nghiêm trọng hoặc phá hợp đồng caller. Recovery chỉ nên thực hiện ở biên có chính sách rõ.

**“Đặt code sau `proceed` bảo đảm cleanup.”** Không nếu `proceed` ném exception. Dùng `finally` và có chính sách lỗi cleanup.

**“Gắn pointcut là mọi lời gọi đều được around.”** Phụ thuộc mô hình join point, proxy/weaving, classloader, marker và đường gọi thực.

**“Có thể dùng around advice để thay mọi validation nghiệp vụ.”** Những bất biến quan trọng cần hiện diện tại nơi xác định kết quả nghiệp vụ và biên tin cậy.

**“Thứ tự A/B không quan trọng vì cả hai cùng chạy.”** Sai: advice ngoài đo phạm vi khác advice trong và có thể thấy kết quả/lỗi đã được tầng khác chuyển đổi.

## 16. Tự đánh giá

### Câu 1

Around advice gọi `proceed()` đúng một lần, target trả `42`, advice trả `0`. Caller nhận gì? Có phải “advice chỉ quan sát” không?

**Đáp án:** Caller nhận `0`, nếu kiểu/hợp đồng cho phép. Advice **đã thay đổi kết quả**, không còn chỉ quan sát.

### Câu 2

Một around advice trả cached value mà không gọi continuation. Advice outside báo “returning”. Target đã chạy chưa?

**Đáp án:** Chưa chắc; nếu nhánh cache hit không gọi tiếp thì target không chạy. “Returning” của tầng ngoài nói **continuation bên trong trả bình thường**, không chứng minh target đã thực hiện.

### Câu 3

Trong `Trace(Retry(Target))`, nếu retry thất bại hai lần rồi thành công lần ba, Trace ngoài thường quan sát bao nhiêu logical call?

**Đáp án:** Một logical invocation có thể chứa ba attempt; số lần target chạy là ba nếu retry thật sự gọi tiếp ba lần. Muốn metrics từng attempt cần điểm đo phù hợp, không chỉ trace ngoài.

### Câu 4

Tại sao không nên retry một thao tác chuyển tiền chỉ dựa trên ngoại lệ timeout?

**Đáp án:** Timeout không chứng minh thao tác chưa commit. Gọi lại có thể nhân side effect; phải có idempotency, chính sách retry, thời hạn tổng và khả năng truy vấn trạng thái.

### Câu 5

Một around advice ghi latency trong code sau `return proceed()` mà không có `finally`. Nếu target ném lỗi thì sao?

**Đáp án:** Lệnh sau `return` không chạy; cũng không chạy trên exception. Phải đo trong `finally` hoặc thiết kế đường lỗi riêng.

### Câu 6

Target trả `CompletableFuture` sau 1 ms và future thất bại sau 2 giây. Around advice đo thời gian `proceed` thấy gì?

**Đáp án:** Thường đo khoảng thời gian lời gọi trả future, không phải đến khi future thất bại. Muốn đo end-to-end phải quan sát completion.

### Câu 7

Bạn dùng interface dynamic proxy; một code path giữ target gốc và gọi thẳng. Around advice có chạy không?

**Đáp án:** Handler proxy không chạy ở đường bypass đó. Cần thống nhất cách caller nhận proxy hoặc dùng cơ chế khác có tập điểm phù hợp.

### Câu 8

Nếu advice B bên trong bắt exception rồi trả fallback, advice A bên ngoài sẽ thấy after-throwing hay after-returning?

**Đáp án:** Nếu B trả bình thường và không ném lỗi, A có thể quan sát completion bình thường của continuation; A không nhất thiết thấy exception target đã bị B xử lý.

### Câu 9

Vì sao advice đo thời gian nên giữ biến thời điểm bắt đầu ở local scope của invocation?

**Đáp án:** Tránh nhiều lời gọi đồng thời ghi đè trạng thái chung của aspect và làm sai latency; local variable cũng giúp cleanup rõ từng invocation.

## 17. Checklist trước khi đưa around advice vào production

1. **Tập điểm:** Pointcut chọn loại join point nào và có đủ đường gọi cần thiết không?
2. **Call path:** Proxy có bị bypass, self-invocation có nằm ngoài phạm vi, artifact weaving có đúng không?
3. **Continuation:** Có quyền gọi 0/nhiều lần không? Nếu có, áp điều kiện nào?
4. **Hợp đồng:** Điều kiện skip/replace có được caller hiểu và chấp nhận không?
5. **Result/args:** Type, nullability, side effect và security invariant có được giữ đúng không?
6. **Exception:** Đường target error và advice error được xử lý, truyền cause và cleanup thế nào?
7. **Ordering:** Advice ngoài/trong đặt theo đúng phạm vi metric, retry, cache, transaction chưa?
8. **Async:** Lời gọi trả stage hay công việc đã hoàn tất? Context có được truyền/đóng đúng không?
9. **Concurrency:** State trong advice có thread-safe, không rò ThreadLocal/span/resource không?
10. **Hiệu năng:** Số runtime occurrences, logging volume, allocation và latency đã được đo ở tải thực tế chưa?
11. **Kiểm thử:** Có ca success, skip, retry, failure, direct target, self-call, nhiều advice và async chưa?
12. **Giải thích được:** Người bảo trì có dễ xác định tại sao target chạy hoặc không chạy cho một request cụ thể không?

## Kết luận

**Around advice** không chỉ thêm hành vi quanh join point mà có thể **điều khiển chính luồng thực thi**: quyết định gọi tiếp hay không, bao quanh lỗi và kết quả, thậm chí gọi lại hoặc biến đổi dữ liệu nếu cơ chế cho phép. Sức mạnh đó tạo ra rủi ro với idempotency, tính trong suốt của hợp đồng, thứ tự advice, đồng bộ/bất đồng bộ và phạm vi weaving. Muốn dùng an toàn, hãy viết rõ **continuation nào được gọi, bao nhiêu lần, giá trị/lỗi nào đến caller và đường nào có thể bypass**; sau đó chứng minh bằng positive/negative integration tests chứ không chỉ nhìn tên pointcut hoặc annotation.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-002">Gọi tiếp hành vi gốc qua cơ chế proceed hoặc cơ chế tương đương</a>

<details>
<summary>Xem chi tiết</summary>

# Gọi tiếp hành vi gốc qua cơ chế proceed hoặc cơ chế tương đương trong around advice

> **Mục tiêu:** Nắm được vai trò của `proceed` — hoặc một cơ chế continuation tương đương — trong **around advice** của lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP). Hiểu rõ vì sao gọi `proceed` thường là cách cho phép luồng xử lý được tiếp tục; điểm tiếp tục có thể đi qua các advice khác trước khi tới hành vi nghiệp vụ; và hậu quả khi không gọi, gọi một lần, gọi nhiều lần, truyền đối số khác, thay giá trị trả về hoặc xử lý ngoại lệ.

## 1. Vấn đề: advice bao quanh phải quyết định có tiếp tục hay không

Trong những loại advice đơn giản, thời điểm chạy thường đã được định nghĩa: chạy trước khi phần gốc bắt đầu, sau khi phần gốc hoàn tất bình thường hoặc khi nó kết thúc bằng ngoại lệ.

**Around advice** khác ở chỗ nó bao lấy một hoạt động được chọn và có thể **quyết định thời điểm, điều kiện và cách tiếp tục luồng thực thi**. Điều đó cho phép nó thực hiện các chính sách như:

- Đo thời gian và quan sát cả trước lẫn sau một lời thực thi.
- Kiểm tra điều kiện rồi cho phép hoặc từ chối thực hiện hoạt động kế tiếp.
- Ghi log trước khi gọi, sau khi trả về và khi có lỗi.
- Gói một ranh giới thực thi trong `try/finally` để dọn ngữ cảnh.
- Với **những cơ chế cho phép**, điều chỉnh đối số, trả về kết quả thay thế hoặc thực hiện lại phần tiếp tục.

Cơ chế then chốt thường được biểu diễn bằng lời gọi `proceed()`, `next()`, `continuation.invoke()`, `invocation.proceed()` hoặc một giao diện tương đương. Đây là tên **khái niệm**, không phải một cú pháp bắt buộc của mọi AOP.

Mô hình ngắn:

~~~text
Caller
  |
  v
[ Around advice: trước ]
  |
  | proceed()
  v
[ Phần thực thi tiếp theo: advice khác và/hoặc phần gốc ]
  |
  v
[ Around advice: sau ]
  |
  v
Caller
~~~

**Chìa khóa:** Trong một chuỗi có nhiều advice, `proceed` thường có nghĩa là **tiếp tục tới mắt xích kế tiếp**, không nhất thiết **gọi thẳng vào phương thức gốc ngay lập tức**.

## 2. Xác định đúng continuation trước khi gọi tiếp

**Join point** là vị trí thực thi có thể can thiệp; **pointcut** chọn tập join point; **weaving/interception** quyết định liệu advice có thực sự được nối vào đường gọi hay không. Khi **around advice** đã nhận quyền điều khiển, thao tác **`proceed` (continuation)** chuyển sang phần còn lại của chuỗi advice và cuối cùng có thể tới target. Nó không mặc nhiên gọi trực tiếp target.

Trước khi đọc một lời gọi `proceed`, hãy kiểm tra ba điều độc lập:

1. **Điểm thực thi nào được chọn?** Pointcut và cơ chế AOP có thực sự bao phủ lời gọi này không?
2. **Advice nào nằm bên trong?** Continuation có thể qua một advice khác rồi mới đến nghiệp vụ.
3. **Lớp hiện tại có gọi tiếp không, và bao nhiêu lần?** Advice bên trong có thể short-circuit, ném lỗi hoặc lặp tiếp nếu cơ chế cho phép.

Một aspect tổ chức pointcut/advice cho concern xuyên suốt; nó không thay đổi các câu hỏi trên. Trong bài này, trọng tâm là **ngữ nghĩa của lời gọi tiếp tục**. Các bài về không gọi, thay đối số, retry và xử lý ngoại lệ sẽ áp dụng cùng ranh giới này vào những hợp đồng riêng.

## 3. Cú pháp khái niệm cơ bản

Một mẫu around advice có thể được diễn đạt như sau:

~~~text
around selectedJoinPoint:
    before()
    result = proceed()
    after()
    return result
~~~

Đây là **pseudocode**, không phải chương trình Java độc lập hay một ngôn ngữ aspect cụ thể.

Ý nghĩa:

1. `before()` chạy khi advice nhận quyền điều khiển.
2. `proceed()` cho phép hoạt động tiếp theo trong chuỗi can thiệp thực thi.
3. Nếu phần tiếp theo **trả về bình thường**, biến `result` nhận kết quả.
4. `after()` chạy sau khi `proceed` trả về bình thường.
5. `return result` chuyển kết quả trở lại lớp gọi phía ngoài.

Mẫu này **chưa** ghi nhận đầy đủ nhánh lỗi: nếu `proceed()` ném ngoại lệ, lệnh `after()` nằm tiếp sau nó sẽ bị bỏ qua theo luồng điều khiển thông thường. Muốn xử lý cả hai kiểu thoát cần dùng `try/catch/finally` tương ứng.

### 3.1. Mẫu bảo toàn kết quả và ngoại lệ

~~~text
around selectedJoinPoint:
    before()
    try:
        result = proceed()
        afterSuccess(result)
        return result
    catch error:
        afterFailure(error)
        throw error
    finally:
        afterExit()
~~~

Mục đích minh họa:

- Không ngầm nuốt ngoại lệ của phần tiếp theo.
- Chỉ ghi **success** sau khi phần tiếp theo trả về bình thường.
- Chỉ ghi **failure** với ngoại lệ thực sự đi ra khỏi ranh giới đó.
- `afterExit` hướng tới cả hai đường thoát nếu có unwinding phù hợp.

Mẫu trên là **ý tưởng logic**, không khẳng định mọi công cụ hỗ trợ đúng cú pháp hoặc mọi kiểu ngoại lệ có cùng semantics. Advice `afterSuccess`, `afterFailure` hoặc `afterExit` tự phát sinh lỗi cũng có thể thay đổi kết quả cuối. Cần quy định chính sách lỗi rõ.

## 4. Proceed là một sự tiếp tục, không chỉ là lời gọi lại cùng tên phương thức

Khi có nhiều around advice, chuỗi có thể trông như sau:

~~~text
Caller
  |
  v
Around A
  |  A.proceed()
  v
Around B
  |  B.proceed()
  v
Original operation
  |
  v
Around B nhận kết quả
  |
  v
Around A nhận kết quả
  |
  v
Caller nhận kết quả
~~~

Nếu A ghi “A trước”, B ghi “B trước”, phần gốc in “business”, sau đó B in “B sau”, A in “A sau”, thứ tự dự kiến là:

~~~text
A before
B before
business
B after
A after
~~~

Trong cách lồng này:

- `A.proceed()` chuyển quyền cho **B**, chưa chắc vào nghiệp vụ ngay.
- `B.proceed()` mới đi tiếp đến **phần gốc** nếu không còn advice bao phía trong.
- Kết quả và ngoại lệ quay ngược lên các lớp bao, giống luồng call stack.
- Chính sách ordering quyết định ai là lớp ngoài/lớp trong; **không được đoán thứ tự** từ tên aspect hoặc vị trí khai báo nếu cơ chế không quy định như vậy.

Hãy tưởng tượng `proceed` như một “continuation” được trao cho advice: nó đại diện cho phần còn lại của quá trình xử lý mà advice có thể cho phép tiếp tục theo hợp đồng.

## 5. Ba tình huống cơ bản: không gọi, gọi một lần, gọi nhiều lần

### 5.1. Không gọi proceed

~~~text
around selectedJoinPoint:
    if forbidden():
        return rejection
    return proceed()
~~~

Nếu điều kiện `forbidden()` đúng và advice trả về ngay, phần tiếp tục không được thực thi **trong mô hình cho phép short-circuit như vậy**.

Hệ quả có thể là:

- Phần nghiệp vụ gốc **không được chạy** ở lượt này.
- Những advice **nằm bên trong continuation** cũng không chạy.
- Những advice **nằm bên ngoài** advice hiện tại vẫn có thể thực hiện phần xử lý sau khi nhận kết quả.
- Caller nhận giá trị thay thế nếu loại join point và hợp đồng cho phép.

Cần cực kỳ thận trọng: nếu pointcut quá rộng hoặc logic từ chối sai, advice có thể vô tình bỏ qua những thao tác nghiệp vụ quan trọng. Không phải mọi dạng join point đều cho phép trả về một giá trị tùy ý khi không proceed.

### 5.2. Gọi proceed một lần

~~~text
around selectedJoinPoint:
    before()
    result = proceed()
    after()
    return result
~~~

Đây là dạng thường gặp nhất để giữ hành vi gốc trong một chính sách quan sát: **một lần tiếp tục**, giữ nguyên giá trị trả về, không chủ ý thay đổi số lần thực thi phần gốc.

Tuy nhiên, một lần gọi `proceed` ở lớp ngoài **không bảo đảm** phần gốc chạy đúng một lần: một advice lớp trong có thể bỏ qua, tự ném lỗi hoặc, nếu cơ chế cho phép, tiếp tục nhiều lần. Chỉ khi xác minh toàn chuỗi mới có thể kết luận số lần chạy nghiệp vụ.

### 5.3. Gọi proceed nhiều lần

~~~text
around selectedJoinPoint:
    first = proceed()
    second = proceed()
    return second
~~~

Đây là **pseudocode nêu ý tưởng**, không phải cú pháp hoặc hành vi được bảo đảm cho mọi AOP. Một số cơ chế quanh join point hỗ trợ tiếp tục nhiều lần; một số chỉ cho phép một lần, hoặc có giới hạn nghiêm ngặt tùy loại điểm.

Nếu mỗi `proceed` khởi động lại phần còn lại của chuỗi thì phần gốc **có thể chạy nhiều lần**: hai lần ghi dữ liệu, hai lần gửi yêu cầu, hai lần trừ tiền hoặc hai lần phát sự kiện. Retry chỉ hợp lý khi thao tác **idempotent** hoặc có bảo đảm chống trùng phù hợp. Không được xem multiple proceed là cách retry an toàn mặc định.

## 6. Java 17: tự xây chuỗi around/continuation bằng Java thuần

Đoạn sau là một **mô hình giáo dục chạy được**. Ta định nghĩa:

- `Invocation`: một đơn vị tiếp tục thực thi.
- `Around`: một mắt xích nhận continuation tới phần còn lại.
- `run(...)`: xây dựng chuỗi bằng cách cho advice hiện tại gọi mắt xích kế tiếp; khi hết advice, gọi hàm nghiệp vụ.
- `trace`: một around advice quan sát trước, sau thành công, ngoại lệ và finally.
- `audit`: một around advice bên trong.
- `block`: một around advice trả về thay thế và không gọi tiếp.

**Chương trình không tự weaving vào Java bytecode và không phải cú pháp của bất kỳ thư viện AOP cụ thể nào.** Nó minh họa chính xác mô hình `proceed/next` bằng các hàm Java 17.

Lưu file **`ProceedChainDemo.java`**:

~~~java
import java.util.List;

public class ProceedChainDemo {
    @FunctionalInterface
    interface Invocation {
        String proceed() throws Exception;
    }

    @FunctionalInterface
    interface Around {
        String invoke(Invocation next) throws Exception;
    }

    static String run(List<Around> chain, int index, Invocation original)
            throws Exception {
        if (index == chain.size()) {
            return original.proceed();
        }
        Around current = chain.get(index);
        return current.invoke(() -> run(chain, index + 1, original));
    }

    public static void main(String[] args) throws Exception {
        Around trace = next -> {
            System.out.println("trace before");
            try {
                String result = next.proceed();
                System.out.println("trace success " + result);
                return result;
            } catch (Exception error) {
                System.out.println("trace error " + error.getMessage());
                throw error;
            } finally {
                System.out.println("trace finally");
            }
        };

        Around audit = next -> {
            System.out.println("audit before");
            String result = next.proceed();
            System.out.println("audit after");
            return result;
        };

        Invocation success = () -> {
            System.out.println("business success");
            return "OK";
        };

        String value = run(List.of(trace, audit), 0, success);
        System.out.println("caller result " + value);

        Invocation failure = () -> {
            System.out.println("business failure");
            throw new IllegalStateException("boom");
        };

        try {
            run(List.of(trace, audit), 0, failure);
        } catch (IllegalStateException error) {
            System.out.println("caller caught " + error.getMessage());
        }

        Around block = next -> {
            System.out.println("blocked");
            return "DENIED";
        };

        String denied = run(List.of(trace, block, audit), 0, success);
        System.out.println("caller result " + denied);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ProceedChainDemo.java
java ProceedChainDemo
~~~

**Stdout chính xác:**

~~~text
trace before
audit before
business success
audit after
trace success OK
trace finally
caller result OK
trace before
audit before
business failure
trace error boom
trace finally
caller caught boom
trace before
blocked
trace success DENIED
trace finally
caller result DENIED
~~~

### 6.1. Đọc lần thành công

Chuỗi `[trace, audit]` được truyền vào `run`.

1. `trace` in `trace before`.
2. `trace` gọi `next.proceed()`, nên quyền điều khiển chuyển đến `audit`.
3. `audit` in `audit before`, rồi gọi tiếp.
4. Vì không còn advice khác, `run` gọi `original.proceed()`.
5. Nghiệp vụ in `business success` rồi trả `"OK"`.
6. `audit` nhận kết quả, in `audit after`, trả nguyên giá trị.
7. `trace` nhận `"OK"`, in `trace success OK`, sau đó `finally` in `trace finally`.
8. Caller nhận và in `caller result OK`.

Dòng `audit after` đứng trước `trace success OK` vì **audit ở trong trace**.

### 6.2. Đọc lần thất bại

`failure` ném `IllegalStateException("boom")`. Advice `audit` không có catch/finally, vì thế dòng `audit after` **không xuất hiện**. Ngoại lệ tiếp tục lên `trace`, `trace` ghi lỗi, ném lại, rồi chạy `finally`. Caller bắt đúng loại ngoại lệ và in `caller caught boom`.

Từ đó rút ra:

- `proceed()` có thể **trả về** hoặc **ném ngoại lệ**.
- Đoạn lệnh nằm **sau** `proceed` chỉ chạy khi có đường đi tới nó.
- `finally` hữu ích để xử lý việc rời phạm vi cả bình thường và ngoại lệ.
- Nếu advice tự ném ngoại lệ mới, lỗi gốc có thể bị thay thế; ví dụ trên không làm như vậy.

### 6.3. Đọc trường hợp block

Chuỗi cuối `[trace, block, audit]`.

- `trace` bắt đầu và gọi `next.proceed()`.
- `block` in `blocked` và trả `"DENIED"` **mà không gọi next**.
- Vì thế `audit` và `success` **không chạy**.
- `trace` phía ngoài vẫn nhận một giá trị trả về bình thường `"DENIED"`, in `trace success DENIED` rồi `trace finally`.

Điểm tinh tế: **“trace success” trong chương trình chỉ có nghĩa continuation trả về bình thường**, không có nghĩa hành vi nghiệp vụ gốc đã chạy hoặc giao dịch nghiệp vụ thành công. Đây là lý do hệ thống quan sát phải phân biệt normal return, short-circuit, trạng thái nghiệp vụ và kết quả thực hiện phần gốc.

## 7. Không gọi proceed không đồng nghĩa dừng toàn bộ thế giới

Một around advice bỏ qua `proceed` chỉ ảnh hưởng tới **continuation nằm bên trong nó** theo mô hình kết hợp của hệ AOP.

Những hành vi sau **có thể vẫn xảy ra**:

- Advice bao ngoài nhận kết quả, xử lý hoặc ghi log.
- Các hoạt động đã được thực hiện trước khi vào advice hiện tại.
- Các tác vụ bất đồng bộ đã được gửi đi từ nơi khác.
- Mã phía caller sau khi nhận giá trị thay thế.
- Những join point khác không thuộc chuỗi này.

Do đó, một điểm bị short-circuit không phải là lệnh “hủy toàn bộ request” trừ khi kết quả và hợp đồng của phần ứng dụng thực sự có nghĩa như vậy.

Nếu dùng short-circuit cho kiểm soát quyền, phải cân nhắc bảo đảm an toàn ở **mọi đường vào** và mọi cơ chế dispatch. Một pointcut có thể bỏ sót join point hoặc một lời gọi đi ngoài weaving, nên không được xem chỉ một advice là bằng chứng đủ cho một chính sách an ninh bắt buộc.

## 8. Khi gọi proceed nhiều lần: demonstration và rủi ro

Để hình dung sự khác biệt giữa một lần và hai lần tiếp tục, dùng chương trình Java 17 thứ hai. Chương trình **tự định nghĩa** continuation có thể gọi nhiều lần; nó không khẳng định rằng tất cả công cụ AOP thực tế đều cho phép làm vậy.

Lưu file **`RepeatProceedDemo.java`**:

~~~java
public class RepeatProceedDemo {
    @FunctionalInterface
    interface Invocation {
        String proceed();
    }

    @FunctionalInterface
    interface Around {
        String invoke(Invocation continuation);
    }

    static String apply(Around advice, Invocation original) {
        return advice.invoke(original);
    }

    public static void main(String[] args) {
        int[] calls = {0};

        Invocation operation = () -> {
            int count = ++calls[0];
            System.out.println("business invocation " + count);
            return "value-" + count;
        };

        Around twice = next -> {
            String first = next.proceed();
            System.out.println("first=" + first);
            String second = next.proceed();
            System.out.println("second=" + second);
            return second;
        };

        String result = apply(twice, operation);
        System.out.println("caller=" + result);
        System.out.println("count=" + calls[0]);
    }
}
~~~

Chạy:

~~~bash
javac RepeatProceedDemo.java
java RepeatProceedDemo
~~~

**Stdout chính xác:**

~~~text
business invocation 1
first=value-1
business invocation 2
second=value-2
caller=value-2
count=2
~~~

Ở mô hình do chương trình này định nghĩa, mỗi lần `next.proceed()` thực thi lại lambda `operation`. Do đó số lần chạy nghiệp vụ bằng **2** dù caller chỉ gọi `apply(...)` một lần.

**Không áp dụng suy luận này một cách máy móc cho mọi advice runtime.** Một số hệ AOP giới hạn số lần gọi tiếp, có semantics riêng cho constructor và field access, hoặc dùng trạng thái invocation không thể phát lại an toàn. Muốn retry thật, phải nghiên cứu hợp đồng của continuation và tính idempotent của phần nghiệp vụ.

### 8.1. Repeat có thể lặp cả những advice phía trong

Nếu continuation bao gồm chuỗi `[audit, business]`, việc gọi nó nhiều lần có thể chạy **cả audit lẫn business nhiều lần**. Tại một ranh giới có nhiều aspect, điều này khiến số bản ghi, số lần đo và số lần nghiệp vụ khác dự đoán ban đầu.

Không chỉ đếm `proceed` của advice ngoài; hãy xét **cây lời gọi continuation hoàn chỉnh**.

## 9. Proceed và nguyên tắc bảo toàn hợp đồng phương thức

Một around advice dùng cho hoạt động trả kết quả nên cân nhắc những thuộc tính sau:

### 9.1. Giá trị trả về

Nếu phần gốc trả một `Order`, advice chỉ làm logging thường nên **trả đúng `Order` đó**. Trả `null`, giá trị kiểu khác hoặc một đối tượng thay thế có thể vi phạm API hoặc tạo lỗi ở caller.

Trong mô hình có cho phép biến đổi kết quả, việc biến đổi phải có **chủ đích và hợp đồng rõ**, không phải tác dụng phụ vô tình.

### 9.2. Ngoại lệ

Nếu `proceed` ném một ngoại lệ, advice quan sát thông thường nên ghi nhận rồi **ném lại ngoại lệ phù hợp**. Nuốt lỗi có thể biến một tác vụ thất bại thành bình thường giả; bọc ngoại lệ tùy tiện có thể phá hợp đồng mà caller phụ thuộc.

Nếu mục đích là chuyển lỗi sang dạng kết quả có kiểm soát, hãy thiết kế đây như một **chính sách lỗi** rõ ràng, có kiểm thử với các loại ngoại lệ được cho phép.

### 9.3. Số lần gọi

Một advice đo thời gian bình thường nên tránh gọi `proceed` hai lần. Nếu có retry, phải xét quyền tái thực thi, tác dụng phụ, transaction, timeout và khả năng trùng dữ liệu.

### 9.4. Đối số

Nếu cơ chế cho phép gọi continuation với đối số mới, advice cần bảo toàn kiểu, thứ tự, số lượng và bất biến. Không tự cho rằng mọi loại join point đều cho sửa đối số, hay cú pháp `proceed(newArgs)` hoạt động giống nhau trong mọi công cụ.

### 9.5. Ngữ cảnh của đối tượng và luồng

Đối tượng đích, receiver, caller và ngữ cảnh thread có thể là những thông tin khác nhau. Khi thay điều kiện hoặc đẩy `proceed` sang thread khác, ngữ nghĩa của advice và quan hệ ngữ cảnh có thể đổi. Nếu công cụ không tài liệu hóa việc tiếp tục bất đồng bộ, không được dùng nó như một mặc định.

## 10. Proceed với tham số thay đổi

Một số mô hình AOP cho phép around advice **cung cấp lại đối số** khi tiếp tục. Một số chỉ hỗ trợ tiếp tục với đối số gốc hoặc có quy tắc ràng buộc riêng.

Mô tả ý tưởng:

~~~text
around selectedCall(originalArguments):
    checked = normalizeAndValidate(originalArguments)
    return proceed(checked)
~~~

Đây là **pseudocode**, không phải cú pháp thống nhất. Những câu hỏi cần làm rõ:

- Có được đổi **giá trị** đối số không?
- Có được đổi **số lượng**, **kiểu**, hoặc **đối tượng đích** không?
- Đối số mới được dùng tại **điểm nào**: call site, execution hay một continuation bao nhiều advice?
- Nếu có advice khác phía trong, nó quan sát **đối số gốc** hay **đối số đã được thay**?
- Việc thay đổi có ảnh hưởng tới kiểm tra pointcut động và điều kiện chọn của những advice tiếp theo không?

Không nên chuẩn hóa/biến đổi dữ liệu bí mật ở một advice “đăng ký vào mọi phương thức” mà không có hợp đồng nghiệp vụ. Sai kiểu hoặc sai ngữ nghĩa có thể tạo lỗi tinh vi.

### 10.1. Phân biệt dữ liệu đọc được với quyền thay đổi dữ liệu

Một mô hình có khả năng **đọc** đối số tại join point không đồng nghĩa có quyền **thay** đối số khi proceed. Hai khả năng phải được kiểm tra riêng trong tài liệu công cụ.

## 11. Advice bao quanh và exception flow

Xét mô hình:

~~~text
around:
    before()
    result = proceed()
    after()
    return result
~~~

Nếu `proceed` ném ngoại lệ, lệnh `after()` bị bỏ qua. Để quan sát lỗi:

~~~text
around:
    before()
    try:
        result = proceed()
        afterSuccess(result)
        return result
    catch error:
        recordFailure(error)
        throw error
    finally:
        restoreContext()
~~~

**Không tự hứa rằng `finally` được gọi trong mọi tai biến tuyệt đối:** nếu tiến trình bị kết thúc cưỡng bức, có lỗi khiến VM không thể unwinding hoặc một hoạt động không bao giờ trả về/throw, không có bảo đảm đó.

### 11.1. Ngoại lệ phát sinh trong advice

Nếu `afterSuccess()` tự ném lỗi, caller có thể nhận lỗi từ advice thay vì kết quả nghiệp vụ bình thường. Nếu `restoreContext()` trong `finally` tự ném lỗi, nó có thể che ngoại lệ gốc đang lan truyền.

Đối với logging/metrics best-effort, cần chính sách xử lý lỗi để việc quan sát không tùy tiện phá nghiệp vụ. Với những tác vụ cleanup bắt buộc cho tính đúng đắn, không nên đơn giản nuốt mọi lỗi; cần một hợp đồng rõ về ngoại lệ chính và thông tin lỗi phụ.

### 11.2. Trả về trong finally là một anti-pattern

Trong Java, `return` hoặc `throw` xuất phát từ `finally` có thể thay đổi kết quả đang chờ. Mô hình around advice có xử lý cleanup nên tránh trả về giá trị mới bên trong phần dọn dẹp trừ khi có chính sách chuyển đổi kết quả rất rõ.

## 12. Advice chaining: ai gọi ai, và thứ tự ra/vào?

Giả sử có ba around advice:

- **A** đặt ngữ cảnh trace.
- **B** kiểm tra chính sách truy cập.
- **C** đo thời gian.
- **Original** chạy nghiệp vụ.

Một thứ tự lồng có thể là:

~~~text
A.before
  B.before
    C.before
      Original
    C.after
  B.after
A.after
~~~

Nhưng với một chính sách “từ chối nếu không có quyền”, nếu **B không gọi proceed**:

~~~text
A.before
  B.before
  B.denied
A.after
~~~

Khi đó **C không được chạy**, Original không được chạy, nhưng A vẫn có cơ hội xử lý phần kết thúc nếu A đã chuyển quyền vào B và B trả về bình thường.

### 12.1. Tầm quan trọng của thứ tự

Nếu yêu cầu là “mọi lời từ chối cũng phải được đo thời gian”, phải chọn ranh giới metrics phù hợp — đặt nó ngoài advice có khả năng chặn, hoặc chọn một điểm quan sát khác. Nếu metrics nằm bên trong nhánh bị bỏ qua thì nó không có dữ liệu cho lời từ chối.

Nếu yêu cầu là “mọi yêu cầu bị từ chối đều phải có audit”, không thể đặt audit tùy tiện phía trong advice chặn mà vẫn kỳ vọng mọi trường hợp đều xuất hiện audit.

**Pointcut xác định tập điểm ứng viên**, còn **ordering** quyết định cấu trúc lồng và tính bao phủ thực tế của từng advice trong một chuỗi.

## 13. Không proceed và return thay thế: quyền lực đi cùng ràng buộc

Một around advice có thể short-circuit trong một số mô hình. Nhưng việc thay thế hành vi chỉ an toàn khi ý nghĩa được xác định:

- Với **method trả boolean**, `false` có phải từ chối hợp lệ không?
- Với **method trả object**, `null` có thuộc hợp đồng không?
- Với **method trả void**, short-circuit có nghĩa không làm nghiệp vụ; caller có được thông báo không?
- Với **constructor execution**, có thể bỏ phần khởi tạo hay thay đối tượng không? Điều này phụ thuộc cơ chế và các ràng buộc của runtime; không được giả định như một method thông thường.
- Với **field get/set**, giá trị và tác dụng phụ có thể chịu quy tắc đặc biệt của mô hình.

Nếu một điểm không cho thay kết quả hoặc không cho bỏ qua hoạt động gốc, một đoạn mô phỏng Java dùng lambda không làm cho thao tác đó hợp lệ trong hệ AOP thật.

### 13.1. Kết quả giả có thể gây hiểu sai

Giả sử advice không gọi tiếp và trả một `PaymentResult.success()` giả trong khi không thực hiện thanh toán. Caller có thể tin rằng tiền đã được chuyển. Vì vậy, short-circuit thường phải trả một kết quả phản ánh **đúng việc không thực hiện nghiệp vụ**, hoặc ném một lỗi từ chối có hợp đồng, tùy thiết kế.

Trong hệ thống có tác vụ gây tác dụng phụ, lựa chọn short-circuit phải được thử ở cả nhánh đồng ý và nhánh từ chối.

## 14. Gọi proceed nhiều lần với retry: điều kiện để có thể làm an toàn

Retry quanh một join point là một ứng dụng nâng cao thường được nhắc tới. Tuy nhiên:

1. **Công cụ có cho multiple proceed không?** Một số continuation chỉ one-shot.
2. **Join point nào được retry?** Method execution, method call hay toàn bộ chuỗi bên trong?
3. **Có tác dụng phụ trước khi lỗi không?** Gửi email, ghi DB, phát message có thể đã xảy ra một phần.
4. **Hành vi gốc có idempotent không?** Thực thi lại nhiều lần cho cùng đầu vào có an toàn không?
5. **Có giới hạn số lần và timeout không?** Tránh retry vô hạn.
6. **Có backoff và phân loại lỗi không?** Không nên retry lỗi dữ liệu không hợp lệ như lỗi mạng tạm thời.
7. **Có tác động lên transaction, lock và context không?** Một lần retry có thể cần phạm vi giao dịch mới.
8. **Các advice bên trong có chạy lại không?** Có thể tạo log/audit nhiều lần.

Một đoạn pseudocode có kiểm soát:

~~~text
around retryableOperation:
    attempts = 0
    while attempts < maxAttempts:
        attempts = attempts + 1
        try:
            return proceed()
        catch retryableError:
            if attempts == maxAttempts:
                throw retryableError
            waitBeforeNextAttempt()
~~~

Pseudocode này chỉ có nghĩa nếu cơ chế cho phép continuation lặp lại và hợp đồng nghiệp vụ đáp ứng các điều kiện trên. Không nên xem nó như công thức copy-paste cho mọi hoạt động.

## 15. Proceed trong vòng bao đo thời gian và dọn ngữ cảnh

Một cách sử dụng phổ biến:

~~~text
around selectedOperation:
    saved = captureContext()
    start = monotonicClock()
    try:
        installContext()
        return proceed()
    finally:
        elapsed = monotonicClock() - start
        recordElapsed(elapsed)
        restore(saved)
~~~

Mục đích:

- Tạo thời điểm bắt đầu ở đúng ranh giới bao.
- Cho phép phần trong thực thi với ngữ cảnh cần thiết.
- Dù phần trong trả về hoặc ném lỗi, kết thúc phép đo và phục hồi ngữ cảnh.
- Trả nguyên kết quả hoặc để ngoại lệ lan truyền nếu phần cleanup không tự phá luồng.

Với Java, `System.nanoTime()` thích hợp để tính **khoảng thời gian**, không dùng trực tiếp để biểu diễn thời điểm lịch.

### 15.1. Đừng dùng biến toàn cục cho mỗi lời gọi

Nếu hai thread thực thi cùng method, dùng một biến tĩnh `startTime` có thể làm thời lượng lẫn nhau. Với lời gọi lồng nhau, một `ThreadLocal` duy nhất mà không có cơ chế stack cũng có thể bị ghi đè.

Tùy công cụ, context của một lần invocation có thể được cung cấp riêng. Nếu tự triển khai wrapper, có thể dùng biến cục bộ của lambda/closure hoặc cấu trúc lưu-phục hồi đúng scope.

### 15.2. Cleanup không đồng nghĩa close mọi tài nguyên nghiệp vụ

Aspect chỉ nên thu dọn những gì **nó sở hữu**. Ví dụ, một method `openStream()` trả một stream để caller dùng tiếp; một around advice không nên đóng stream đó ngay khi method trả, trừ khi hợp đồng API đã quy định như vậy.

Điểm kết thúc của **lời gọi method** và điểm kết thúc **vòng đời tài nguyên** có thể khác nhau.

## 16. Proceed và giá trị trả về: đồng bộ so với bất đồng bộ

Với một method đồng bộ:

~~~java
String result = service.calculate();
~~~

`proceed` thường tiếp tục cho tới khi phần tiếp theo trả về `String` hoặc ném lỗi, theo semantics công cụ.

Với một method bất đồng bộ:

~~~java
CompletionStage<String> pending = service.calculateAsync();
~~~

Hai dòng trên là **mảnh Java minh họa**, không phải chương trình hoàn chỉnh. Nếu phần gốc trả một `CompletionStage`, việc `proceed` trả về **đối tượng đại diện** không đồng nghĩa tác vụ đã hoàn thành. Around advice sau `proceed` có thể chạy trước khi trạng thái cuối của tác vụ được xác định.

Cần phân biệt:

~~~text
T0: vào around
T1: proceed() trả một handle / future
T2: around trả handle cho caller
T3: công việc async mới hoàn thành hoặc thất bại
~~~

Một chính sách cần đo tới **T3** phải có ranh giới quan sát hoàn tất thích hợp, ví dụ một callback an toàn hoặc điểm hoàn tất khác theo kiến trúc. Không tự suy rằng `proceed` chờ mọi tác vụ async.

## 17. Proceed không phải công cụ thay thế kiểm soát giao dịch

Một method có thể thực hiện nhiều thao tác trước khi lỗi:

~~~text
1. Ghi bản ghi A
2. Gửi thông điệp B
3. Ném ngoại lệ
~~~

Nếu around advice bắt lỗi và gọi `proceed` lần nữa, bước 1 hoặc 2 có thể bị lặp. Retry không tự rollback những tác dụng phụ đã xảy ra.

Khi cần tính nguyên tử hoặc nhất quán, phải thiết kế transaction, idempotency key, outbox hoặc cơ chế bù trừ phù hợp **bên trong hợp đồng nghiệp vụ và hạ tầng**. AOP có thể tổ chức điểm can thiệp nhưng không tạo ra giao dịch nguyên tử chỉ bằng tên `proceed`.

Tương tự, nếu around advice chặn không cho thực thi mà caller không biết, dữ liệu nghiệp vụ có thể bị “thành công giả”. Đây là quyết định kiến trúc, không phải chỉ là vấn đề cú pháp.

## 18. Phân biệt proceed với gọi trực tiếp phương thức gốc bằng reflection

Một mô hình can thiệp có thể cung cấp đối tượng đại diện cho lời gọi gốc. Nhưng hai thao tác sau **không mặc nhiên tương đương**:

~~~text
proceed()
~~~

và:

~~~text
reflectivelyInvoke(targetMethod, target, originalArgs)
~~~

Vì:

- `proceed` có thể đi qua **các advice kế tiếp** trong chuỗi.
- Reflection trực tiếp có thể **bỏ qua** một số advice hoặc đi qua một đường gọi khác.
- Reflection có quy tắc access, boxing, varargs và exception wrapping riêng.
- Nếu gọi vào một proxy không đúng cách, một lệnh reflection có thể gây can thiệp lặp hoặc recursion.
- Các cơ chế weaving ở method call/execution có thể khác cách tiếp tục bằng lời gọi reflection.

Trong ví dụ Java 17 ở phần 6, `next.proceed()` là continuation do mô hình dựng lên. Không nên sửa nó thành việc tự gọi lại method bằng tên mà cho rằng vẫn giữ nguyên thứ tự advice.

### 18.1. Nguy cơ đệ quy vô hạn

Nếu advice trên `placeOrder` gọi lại **chính `placeOrder` qua cùng đường can thiệp** thay vì gọi continuation hợp lệ, lời gọi có thể kích hoạt cùng advice lần nữa:

~~~text
advice → call intercepted method → advice → call intercepted method → ...
~~~

Điều đó dễ dẫn tới stack overflow hoặc các tác dụng phụ lặp không mong muốn. Cách đúng là sử dụng cơ chế continuation của framework AOP đang dùng, đúng hợp đồng, thay vì tự mô phỏng bằng cách gọi lại tên method.

## 19. Pointcut động và tiến trình proceed

Một pointcut có thể bao gồm điều kiện ngữ cảnh: chỉ nhận lời gọi khi tier = `"VIP"`. Trong một số mô hình, điểm được chọn trước khi around advice nhận quyền điều khiển; sau đó advice tiếp tục theo semantics của công cụ.

Nếu advice được phép thay đổi đối số ở `proceed`, cần kiểm tra:

- Điều kiện pointcut phía ngoài được đánh giá **trước** hay **sau** phép thay?
- Các pointcut của advice phía trong có nhìn thấy giá trị đã thay không?
- Có cơ chế dùng binding của đối số gốc hay không?
- Có thể tái đánh giá điều kiện động cho mỗi lần multiple proceed không?

Không có đáp án chung cho mọi mô hình AOP. **Điều kiện lựa chọn điểm** và **điều kiện của lần tiếp tục** cần được phân biệt về thời điểm và dữ liệu.

## 20. Các lỗi phổ biến với proceed

### 20.1. Quên gọi proceed

Một advice chỉ ghi “before” rồi trả về có thể bỏ qua hành vi nghiệp vụ. Nếu mục tiêu chỉ là đo thời gian, đây thường là lỗi nghiêm trọng.

**Khắc phục:** trong trường hợp quan sát thuần, tổ chức `try/finally` và gọi continuation đúng một lần, trừ khi có lý do được tài liệu hóa.

### 20.2. Không trả về kết quả từ proceed

Một method có kết quả nhưng advice dùng `proceed()` rồi trả `null` có thể phá hợp đồng hoặc làm caller lỗi.

**Khắc phục:** bảo toàn đúng giá trị trả về hoặc định nghĩa chính sách biến đổi có chủ đích.

### 20.3. Nuốt ngoại lệ

Advice `catch` mọi lỗi và trả một giá trị “thành công” giả khiến caller không biết hoạt động đã thất bại.

**Khắc phục:** xác định rõ lỗi nào được chuyển đổi, lỗi nào phải ném lại; kiểm thử cả nhánh throw.

### 20.4. Gọi proceed hai lần ngoài ý muốn

Ví dụ một advice gọi `proceed` để “lấy dữ liệu”, sau đó gọi lại để “thực thi thật”. Nếu continuation tái chạy phần trong, nghiệp vụ đã bị nhân đôi.

**Khắc phục:** nhớ rằng `proceed` **thực thi**, không phải lời gọi truy vấn metadata.

### 20.5. Cho rằng một proceed đảm bảo một business execution

Advice phía trong có thể short-circuit hoặc tự retry. Vì vậy một `proceed` ở lớp ngoài không bảo đảm phần gốc được gọi đúng một lần.

**Khắc phục:** kiểm thử sự kết hợp toàn chuỗi và số lần thực thi phần gốc.

### 20.6. Đặt advice quan sát ở phía trong advice chặn

Nếu advice authorization bên ngoài short-circuit, metrics/audit phía trong không chạy. Đó có thể là lỗi phạm vi đo.

**Khắc phục:** xác định sự kiện cần quan sát (đã yêu cầu, đã được cho phép, đã thực thi hay hoàn tất) rồi đặt ranh giới phù hợp.

### 20.7. Tin rằng mọi join point đều có cùng quyền thay thế

Một số hệ AOP cho phép around method execution nhưng không around constructor hoặc loại điểm đặc biệt. Một số cho phép sửa đối số, số khác thì không.

**Khắc phục:** xác minh loại join point và semantics proceed cụ thể; không suy từ demo lambda.

### 20.8. Bỏ qua chi phí

Advice chạy ở điểm tần suất cao có thể làm chi phí tăng do logging, kiểm tra điều kiện, phân bổ đối tượng hay retry.

**Khắc phục:** thiết kế pointcut chặt, đo chi phí trong điều kiện thật và không thêm tác vụ chậm vào đường nóng nếu không cần.

## 21. Viết kiểm thử cho around advice có proceed

Một bộ kiểm thử đáng tin cần tách **hành vi lựa chọn** và **hành vi tiếp tục**:

| Tình huống | Kết quả cần kiểm tra |
|---|---|
| Pointcut không chọn | Advice không chạy; nghiệp vụ vẫn theo đường bình thường |
| Around quan sát, phần gốc trả bình thường | Proceed chạy đúng theo thiết kế; kết quả trả về nguyên vẹn |
| Phần gốc ném ngoại lệ | Advice xử lý theo hợp đồng; caller nhận đúng lỗi hoặc chuyển đổi có chủ đích |
| Short-circuit | Phần gốc không chạy; kết quả từ chối đúng hợp đồng |
| Nhiều around lồng nhau | Thứ tự before và after đúng với thứ tự chain |
| Advice phía trong short-circuit | Advice phía ngoài vẫn có thể thấy normal return; phần trong sau điểm chặn không chạy |
| Multiple proceed (nếu hỗ trợ) | Số lần phần gốc chạy và tác dụng phụ đúng như yêu cầu |
| Điều kiện động / ngoại lệ loại trừ | Advice chỉ kích hoạt cho đúng tập điểm |
| Đường gọi ngoài weaving | Không suy ra độ bao phủ sai khi lời gọi bỏ qua cơ chế can thiệp |
| Lời gọi đồng thời/lồng nhau | Trạng thái thời gian/ngữ cảnh tách riêng theo invocation |

### 21.1. Đo số lần thực thi phần gốc

Một test nên tăng một bộ đếm **ngay trong nghiệp vụ**, không chỉ đếm số dòng log advice. Nhờ vậy có thể phát hiện tình huống around chạy nhưng phần gốc bị bỏ qua hoặc bị gọi nhiều lần.

### 21.2. Kiểm thử ngoại lệ từ chính advice

Không chỉ kiểm tra khi nghiệp vụ ném lỗi. Cần xét trường hợp logger, serializer, cleanup hoặc advice phía trong ném lỗi để xem exception nào cuối cùng tới caller và có bị che mất lỗi gốc không.

### 21.3. Kiểm tra giới hạn runtime

Nếu công cụ chỉ chặn qua proxy, phải thử cả lời gọi qua proxy và lời gọi trực tiếp đến target. Nếu công cụ weaving mã, phải xác minh phạm vi class/call site được xử lý. Pointcut đúng về logic không đủ chứng minh mọi đường đi đều nhận advice.

## 22. Quy trình thiết kế around advice chuẩn xác

1. **Định nghĩa mục tiêu:** chỉ quan sát hay có chủ đích chặn/thay đổi/retry?
2. **Chọn join point:** method call, method execution, constructor hay loại khác? Công cụ có hỗ trợ quanh điểm đó không?
3. **Định nghĩa pointcut:** những điểm nào được chọn, có ngoại lệ hoặc điều kiện động không?
4. **Mô tả continuation:** `proceed` chuyển đến đâu; chuỗi có những advice nào và thứ tự nào?
5. **Xác định số lần tiếp tục:** một, không hoặc nhiều; nếu nhiều, cơ chế có cho phép và nghiệp vụ có an toàn?
6. **Định nghĩa return/throw contract:** kết quả và ngoại lệ có được bảo toàn không?
7. **Xác định ngữ cảnh:** đối số/receiver/caller có thể được đọc/thay đổi thế nào?
8. **Thiết kế cleanup:** dùng `finally` đúng phạm vi và tránh che lỗi gốc.
9. **Xem xét async:** proceed trả handle hay đợi công việc hoàn tất?
10. **Đo hiệu năng:** tránh những phép ghi log/đọc trạng thái đắt ở mọi join point.
11. **Lập ca kiểm thử:** success, throw, short-circuit, nested advice, duplicate invocation, đường ngoài weaving.
12. **Tài liệu hóa semantics triển khai:** không giả định các tên API giống nhau luôn có cùng hợp đồng.

## 23. Bài tập vận dụng

### Bài 1: Đọc thứ tự

Cho chuỗi `A → B → business`. Cả A và B đều in “before”, gọi `proceed`, sau đó in “after”. Thứ tự in là gì?

**Đáp án:**

~~~text
A before
B before
business
B after
A after
~~~

### Bài 2: Advice B không gọi tiếp

Vẫn chuỗi đó, nhưng B trả `"DENIED"` mà không proceed. A vẫn gọi `proceed` để chuyển vào B. Business có chạy không?

**Đáp án:** không. A có thể nhận `"DENIED"` rồi chạy đoạn sau `proceed` của A. Đây là short-circuit ở B, không phải toàn bộ chương trình dừng.

### Bài 3: Một proceed có chắc nghiệp vụ chạy?

Một advice A gọi `proceed` một lần. B ở phía trong có khả năng retry hoặc chặn. Có chắc nghiệp vụ chạy đúng một lần không?

**Đáp án:** không. A chỉ cam kết chuyển quyền vào continuation một lần. B có thể làm thay đổi số lần thực thi phần sâu hơn nếu mô hình cho phép.

### Bài 4: Ngoại lệ

Advice có cấu trúc:

~~~text
before()
result = proceed()
after()
return result
~~~

Nếu `proceed` ném ngoại lệ, `after()` có được gọi không?

**Đáp án:** không trên đường đi thông thường. Muốn ghi nhận rời phạm vi bất kể lỗi hay thành công, cần `finally` hoặc cơ chế after/finally tương ứng.

### Bài 5: Retry

Một around advice gọi `proceed()` hai lần khi lần đầu thất bại. Phần gốc trừ tiền trước khi ném ngoại lệ. Đây có an toàn không?

**Đáp án:** không thể kết luận là an toàn. Việc trừ tiền có thể đã xảy ra; lần hai có thể trừ tiếp. Cần idempotency, ranh giới transaction, chính sách retry và xác minh cơ chế cho phép multiple proceed.

### Bài 6: Ghi thời gian bất đồng bộ

`proceed()` trả một `CompletionStage` chưa hoàn tất, và advice ghi thời lượng ngay sau đó. Thời lượng ấy đo điều gì?

**Đáp án:** thời gian tới khi phương thức trả đối tượng đại diện, không tự đo tới khi tác vụ hoàn tất. Muốn thời lượng tác vụ phải quan sát trạng thái hoàn tất ở ranh giới khác.

### Bài 7: Gọi lại method bị chặn

Tại sao tự gọi lại `target.interceptedMethod()` trong advice có thể khác `proceed()`?

**Đáp án:** đó là một đường gọi mới, có thể kích hoạt advice lần nữa hoặc bỏ qua chuỗi hiện tại; `proceed` đại diện cho continuation của join point đang được xử lý theo hợp đồng công cụ. Việc tự gọi có thể tạo đệ quy không mong muốn.

### Bài 8: Đổi giá trị trả về

Một around advice quan sát dùng:

~~~text
proceed()
return null
~~~

với phương thức được khai báo trả `String`. Rủi ro là gì?

**Đáp án:** caller có thể nhận null ngoài hợp đồng; giá trị thật bị đánh mất. Với các mô hình/kiểu khác còn có thể xuất hiện lỗi chuyển kiểu hoặc unboxing. Advice quan sát thường phải trả nguyên kết quả.

## 24. Checklist ghi nhớ

- Around advice **nắm quyền điều phối** phần tiếp theo của join point được chọn.
- `proceed` hoặc continuation tương đương **chuyển quyền điều khiển vào phần còn lại của chuỗi**, không nhất thiết gọi thẳng nghiệp vụ ngay lập tức.
- **Không proceed** có thể short-circuit; **một proceed** không chứng minh original chạy một lần; **nhiều proceed** có thể lặp phần trong khi công cụ hỗ trợ.
- Cần bảo toàn **return value, exception, số lần thực thi và hợp đồng nghiệp vụ** nếu advice chỉ nhằm quan sát.
- Kết hợp `try/catch/finally` giúp phân biệt success, error và cleanup; **lỗi do advice sinh ra** cũng có thể thay đổi kết quả.
- Thứ tự các around advice quyết định lớp nào là **ngoài/trong**, dữ liệu/log nào được nhìn thấy khi advice phía trong short-circuit.
- Khả năng đổi đối số, bỏ qua phần gốc, chạy lại, can thiệp constructor/field access **phụ thuộc mô hình AOP và semantics của cơ chế**.
- Một phương thức bất đồng bộ trả future không đồng nghĩa toàn bộ công việc hoàn tất khi `proceed` trả về.
- Pointcut và weaving vẫn quyết định join point nào thật sự có advice; proceed chỉ có ý nghĩa sau khi around advice đã nhận quyền điều khiển.

**Câu hỏi quan trọng nhất khi đọc một around advice:** “Lời gọi `proceed` này tiếp tục vào những lớp nào, có thể chạy phần gốc bao nhiêu lần, và điều gì xảy ra với kết quả hoặc ngoại lệ khi quyền điều khiển quay trở lại?”


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-003">Không gọi tiếp và trách nhiệm bảo toàn hợp đồng</a>

<details>
<summary>Xem chi tiết</summary>

# Không gọi tiếp và trách nhiệm bảo toàn hợp đồng trong advice bao quanh

**Around advice (advice bao quanh)** là một dạng can thiệp trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**) có thể chạy trước, sau và bao quanh một join point. Khác với việc chỉ quan sát một thời điểm, around advice trong những cơ chế hỗ trợ còn có quyền **quyết định có tiếp tục phần thực thi được bao quanh hay không**, và có thể định hình kết quả hoặc ngoại lệ mà bên gọi nhìn thấy.

Điểm khó không nằm ở cú pháp `proceed()`. Nếu một advice **không gọi tiếp** mà tự trả kết quả hoặc ném ngoại lệ, nó đã thay đổi đường thực thi. Người viết phải chứng minh rằng cách thay thế ấy **bảo toàn hợp đồng có thể quan sát của thao tác gốc**, hoặc đang thay đổi hợp đồng theo một chính sách được thiết kế và công bố rõ ràng.

Ví dụ: một truy vấn giá chỉ đọc có thể trả kết quả từ cache mà không cần gọi hệ thống lưu trữ. Ngược lại, không thể tùy ý bỏ qua thao tác chuyển tiền rồi trả “thành công” chỉ vì muốn tiết kiệm thời gian; thao tác có tác dụng phụ và ràng buộc nghiệp vụ mà kết quả giả không thỏa mãn.

Bài viết tập trung vào mô hình AOP tổng quát: ý nghĩa của **không gọi tiếp**, các nhánh trả về/từ chối, hợp đồng kiểu và ngoại lệ, tác dụng phụ, thứ tự nhiều advice, tính an toàn và cách kiểm chứng. Tên hàm `proceed` trong các đoạn giả mã là cách gọi khái niệm; tên, khả năng và ngữ nghĩa chính xác phụ thuộc công cụ AOP.

## 1. Ba ngả điều khiển của around advice

Một **pointcut** chọn join point, **weaving/interception** nối advice vào đường thực thi, và **around advice** có thể quyết định có chuyển quyền vào phần bên trong hay không. Phần được tiếp tục có thể chứa thêm advice trước khi tới target; vì thế `proceed()` không mặc nhiên đồng nghĩa với một lời gọi trực tiếp đến phương thức gốc.

Trọng tâm bài này là những lần advice **không tiếp tục**. Có ba nhánh cần tách rõ:

~~~text
Around advice
  |-- proceed()  → tiếp tục chuỗi bên trong; nhận result hoặc error
  |-- return R   → kết thúc bình thường, không gọi phần bên trong
  |-- throw E    → kết thúc bằng lỗi, không gọi phần bên trong
~~~

Hai nhánh cuối đều **short-circuit**; chúng có thể bỏ qua target và mọi advice nằm bên trong. Tuy nhiên, advice bao phía ngoài vẫn có thể nhận giá trị `R` hoặc lỗi `E` và tiếp tục xử lý. Nếu có side effect đã hoàn thành trước khi short-circuit, việc không gọi tiếp **không hoàn tác** những tác động đó.

Quyền gọi 0/1/nhiều lần, thay đối số hoặc trả giá trị thay thế phụ thuộc công cụ và loại join point. Đánh giá short-circuit dựa trên **hợp đồng kết quả, lỗi và tác dụng phụ** của thao tác, thay vì suy rằng trả một giá trị có vẻ đúng là đã bảo toàn nghiệp vụ.

## 2. “Không gọi tiếp” có nghĩa chính xác là gì?

Giả sử nhiều lớp advice lồng nhau:

~~~text
Caller
  |
  v
OuterAdvice
  |
  v
CacheAdvice
  |
  v
AuditAdvice
  |
  v
BusinessMethod
~~~

Nếu `CacheAdvice` trả một kết quả cache mà **không tiếp tục**:

- `BusinessMethod` **không được gọi qua chuỗi này**.
- `AuditAdvice` nằm trong `CacheAdvice` **không được vào**, vì luồng chưa đi tới nó.
- `OuterAdvice` đã được vào vẫn có thể quan sát **kết quả trả về bình thường từ CacheAdvice**, rồi chạy xử lý sau/finally của chính nó.
- Một advice **after returning** ở lớp ngoài có thể thấy “thành công” dù phần thân nghiệp vụ không chạy lần này.
- Một advice bên trong không có cơ hội ghi log, đo thời gian hoặc kiểm tra quyền trong nhánh bị rút ngắn.

**Short-circuit có tính vị trí trong chuỗi lồng nhau.** Nói “nghiệp vụ bị bỏ qua” là chưa đủ; phải xác định những lớp advice **đã vào** và những lớp advice **chưa từng được chạy**.

### 2.1 Không gọi tiếp khác với tiếp tục rồi thay kết quả

Hai tình huống khác nhau về tác dụng phụ:

~~~text
A. Không gọi tiếp:
    return substituteValue

B. Gọi tiếp rồi thay kết quả:
    original = proceed()
    return transform(original)
~~~

Ở A, phần bên trong không chạy. Ở B, phần bên trong đã chạy, có thể ghi dữ liệu, gửi sự kiện hoặc mở giao dịch. Việc giá trị trả về cuối cùng giống nhau **không chứng minh** hai đường xử lý tương đương.

Tương tự, một around advice có thể gọi tiếp, bắt ngoại lệ, rồi trả fallback. Lúc đó nghiệp vụ đã được **thử chạy**; đây không phải trường hợp “không gọi tiếp từ đầu”.

## 3. Hợp đồng của phương thức bao gồm những gì?

**Hợp đồng (contract)** là tập những điều người gọi, người triển khai và các thành phần liên quan được quyền kỳ vọng về hành vi của một thao tác. Trong phạm vi kỹ thuật AOP, nó không chỉ có kiểu trả về.

| Thành phần hợp đồng | Ví dụ | Câu hỏi khi không gọi tiếp |
| --- | --- | --- |
| Tiền điều kiện | ID phải dương; người gọi phải có quyền | Advice có duy trì hoặc từ chối đúng điều kiện không? |
| Kiểu và ý nghĩa kết quả | `int` là giá theo đơn vị cent | Giá trị thay thế có đúng kiểu, đơn vị và ý nghĩa không? |
| Hậu điều kiện | Kết quả tương ứng dữ liệu hiện tại | Kết quả cache có đủ mới và hợp lệ không? |
| Ngoại lệ | Đầu vào sai bị từ chối; lỗi hạ tầng được thông báo | Advice có ném đúng loại lỗi và giữ thông tin cần thiết không? |
| Tác dụng phụ | Ghi bản ghi, trừ tiền, gửi thông báo | Bỏ qua thao tác có vi phạm nghĩa vụ tạo tác dụng phụ không? |
| Bất biến hệ thống | Số dư không âm; trạng thái nhất quán | Short-circuit có phá vỡ bất biến không? |
| Quyền truy cập | Trả dữ liệu chỉ cho người có quyền | Có bỏ qua một hàng rào kiểm soát nằm bên trong không? |
| Thời gian và vòng đời | Tài nguyên phải đóng; thao tác có timeout | Nhánh rút ngắn có dọn dẹp và đáp ứng timeout không? |
| Tính lặp / đồng thời | Không đếm trùng; không trả dữ liệu sai dưới race | Cache, idempotency và trạng thái chia sẻ có được đồng bộ đúng không? |
| Quan sát và truy vết | Mỗi yêu cầu có trạng thái cuối | Log/metrics phản ánh “cache hit” khác “backend executed” không? |

Tùy loại phương thức, có thể có ràng buộc khác như transaction, bảo mật, idempotency, tính nhất quán phân tán hoặc chính sách cache. **Trả về đúng Java type là cần, nhưng chưa đủ**.

### 3.1 Trả về giá trị thay thế khi nào là hợp lý?

Một around advice không gọi tiếp có thể hợp lệ nếu nó có **cách thực hiện tương đương hoặc được phép thay thế theo hợp đồng**. Những tình huống thường gặp:

- **Cache hit của truy vấn chỉ đọc:** dữ liệu cache hợp lệ về khóa, phiên bản, phân quyền và giới hạn độ mới.
- **Memoization:** thao tác thuần túy, cùng đầu vào hợp lệ thì cùng kết quả, không phụ thuộc tác dụng phụ ngoài.
- **Idempotency/deduplication:** cùng yêu cầu đã được xử lý theo một giao thức lưu trạng thái kết quả tin cậy; việc trả kết quả đã ghi nhận tuân thủ hợp đồng.
- **Circuit breaker / fallback:** hợp đồng công bố rõ chế độ từ chối hoặc kết quả suy giảm khi dịch vụ phụ thuộc không khả dụng.
- **Validation/authorization:** phát hiện yêu cầu không hợp lệ và ném ngoại lệ từ chối theo chính sách thay vì thực thi tiếp.
- **Test double:** trong kiểm thử được kiểm soát, thay thế thao tác thật nhằm kiểm tra thành phần khác; không suy rằng hành vi giả phù hợp production.

Không nên đánh đồng “có giá trị để trả” với “được phép trả giá trị đó”. Cache cũ, khác tài khoản, khác tenant hoặc không đúng phiên bản có thể vi phạm hợp đồng ngay cả khi không gây lỗi kiểu.

### 3.2 Khi không được tùy ý bỏ qua

Những thao tác sau cần thận trọng đặc biệt:

- **Ghi giao dịch:** nếu hợp đồng yêu cầu thay đổi trạng thái bền vững, trả “thành công” mà không ghi gì thường là sai.
- **Gửi thông báo/sự kiện:** nếu có cam kết gửi hoặc ghi nhận gửi, bỏ qua có thể phá luồng downstream.
- **Cấp số thứ tự/định danh:** trả lại kết quả cũ có thể gây trùng hoặc vi phạm uniqueness.
- **Kiểm toán bắt buộc:** một lớp cache bên ngoài có thể làm advice audit bên trong không chạy.
- **Bảo mật:** một lớp trả cache bên ngoài kiểm tra quyền có thể lộ dữ liệu cho người không đủ quyền.
- **Tài nguyên:** một kết quả thay thế không tự thực hiện cleanup mà phần thực thi gốc chịu trách nhiệm.

Hành vi bỏ qua chỉ hợp lệ khi **được thiết kế là một nhánh của hợp đồng**, không phải khi thuận tiện cho người viết advice.

## 4. Bảo toàn kiểu trả về

Around advice thường phải trả về một giá trị tương thích với **kiểu và ngữ nghĩa kết quả** của join point.

### 4.1 Kiểu nguyên thủy và boxing

Một phương thức Java trả `int` thường được một wrapper dựa trên Reflection biểu diễn qua `Integer`. Nếu handler trả `null` cho kết quả primitive, quá trình unboxing của lời gọi proxy có thể gây `NullPointerException`. Nếu trả một giá trị có kiểu boxing không phù hợp, quá trình chuyển kiểu có thể gây lỗi runtime.

Ví dụ:

~~~java
int countItems();   // Hợp đồng primitive int
String itemName();  // Có thể cho phép null hoặc cấm null tùy hợp đồng
void refresh();     // Hợp đồng không trả giá trị
~~~

Đây là **mảnh khai báo minh họa**, không phải chương trình độc lập.

Không nên nghĩ “around advice trả `Object` nên trả gì cũng được”. Kiểu ở lớp can thiệp có thể rộng, nhưng bên gọi vẫn được biên dịch dựa trên hợp đồng thật.

### 4.2 `null`, tùy chọn và collections

Với kiểu tham chiếu, `null` chỉ hợp lệ nếu hợp đồng cho phép. Một phương thức hứa trả danh sách không null không được thay bằng `null` dù Java cho phép gán kiểu. Nếu hợp đồng trả `Optional<T>`, giá trị thay thế cần là một `Optional` có ý nghĩa đúng, không tự thay thành `null` hay một `T` trần.

Danh sách trả về từ cache còn phải xét **mutability**: trả cùng một danh sách có thể bị bên gọi chỉnh sửa và làm hỏng cache hoặc làm lộ dữ liệu cho người khác. Bản sao bất biến, snapshot và chính sách sở hữu dữ liệu cần được xem xét.

### 4.3 Generics và kiểu runtime

Với phương thức `List<Order> listOrders()`, runtime Java có thể chỉ kiểm tra `List` do type erasure; một danh sách phần tử sai kiểu có thể không bị phát hiện ngay ở ranh giới can thiệp và chỉ gây lỗi khi bên gọi sử dụng. Bảo toàn hợp đồng generic đòi hỏi dữ liệu bên trong cũng đúng kiểu theo khai báo.

### 4.4 Void và giao thức hoàn thành

Với `void`, “trả thành công không gọi tiếp” có nghĩa bên gọi quan sát một lần hoàn thành bình thường dù thao tác gốc không chạy. Điều đó vẫn có thể **sai nghiêm trọng** nếu phương thức `void` có trách nhiệm gửi sự kiện, lưu dữ liệu hoặc giải phóng tài nguyên. `void` chỉ nói **không có giá trị trả về**, không có nghĩa **không có hợp đồng hậu điều kiện**.

## 5. Hợp đồng ngoại lệ và lỗi

Thay vì trả một giá trị, around advice có thể từ chối bằng cách ném ngoại lệ phù hợp:

~~~text
around(context, proceed):
    if invalidInput(context):
        throw InvalidRequestException()
    return proceed()
~~~

Điều này có thể là một nhánh hợp lệ của hợp đồng nếu hành vi gốc vốn yêu cầu từ chối đầu vào sai.

Cần phân biệt:

- **Lỗi đầu vào/chính sách:** ví dụ `IllegalArgumentException`, ngoại lệ phân quyền hoặc kiểu lỗi miền ứng dụng.
- **Lỗi hạ tầng:** timeout, kết nối lỗi, phụ thuộc không khả dụng.
- **Ngoại lệ từ nghiệp vụ:** phát sinh sau khi đã gọi tiếp.
- **Ngoại lệ do chính advice gây ra:** NullPointerException khi dùng context sai, lỗi định dạng, lỗi trong log/cleanup.

Một around advice không nên che giấu lỗi gốc bằng lỗi phụ không liên quan. Nếu cần đổi loại lỗi, hãy xác định rõ ý nghĩa và nguyên nhân `cause`. Với Java, những API dựa trên Reflection có thể bọc lỗi của phương thức đích bằng `InvocationTargetException`; khi chuyển tiếp nên hiểu cách tháo lớp bọc để bên gọi thấy ngoại lệ theo hợp đồng.

### 5.1 Checked exceptions

Một giao diện Java chỉ khai báo một số checked exceptions nhất định. Một proxy động ném ra checked exception không tương thích với hợp đồng khai báo có thể khiến bên gọi nhận `UndeclaredThrowableException`. Không nên dùng khả năng khai báo `throws Throwable` trong bộ xử lý proxy để suy rằng mọi checked exception đều hợp lệ khi truyền ra ngoài.

Around advice cần giữ **miền ngoại lệ hợp lệ** đối với phương thức đang can thiệp, hoặc chuyển thành kiểu ngoại lệ mà hợp đồng cho phép một cách có chủ đích.

### 5.2 Không gọi tiếp và phương thức bất đồng bộ

Nếu phương thức trả `CompletionStage<T>`, `Future<T>` hoặc một kiểu đại diện tác vụ, có hai sự kiện khác nhau:

1. **Phương thức trả về đối tượng đại diện**.
2. **Công việc bất đồng bộ hoàn tất**, thất bại hoặc bị hủy.

Short-circuit bằng một kết quả **đã hoàn tất** có thể hợp lệ trong một số hợp đồng, nhưng không tương đương với “đã khởi chạy tác vụ”. Cần bảo toàn quy tắc completion, cancellation, timeout, context propagation và chính sách báo lỗi. Trả `null` chỉ để bỏ qua tác vụ thường là sai nếu bên gọi được hứa một completion handle hợp lệ.

## 6. Tác dụng phụ và các nghĩa vụ không thể thay bằng một giá trị

Để chứng minh một nhánh short-circuit hợp lệ, cần trả lời:

1. Phương thức gốc có **thay đổi trạng thái** nào mà lời gọi đó phải thực hiện không?
2. Có **gửi sự kiện** hoặc tương tác bên ngoài bắt buộc không?
3. Có **kiểm tra quyền hoặc audit** ở các lớp bên trong không?
4. Có **mở rồi đóng tài nguyên** ở lớp bên ngoài không?
5. Có **cam kết exactly-once, at-least-once hay idempotency** đối với tác vụ này không?
6. Có yêu cầu **độ mới dữ liệu** hoặc tính nhất quán mà cache/fallback phải giữ không?

Từ góc nhìn bên gọi, hai hành vi `return 200` có vẻ giống nhau. Nhưng với hệ thống, một hành vi đã đặt hàng thành công và một hành vi chỉ trả hằng số có thể khác hoàn toàn.

### 6.1 Idempotency không có nghĩa tùy ý bỏ qua

Một yêu cầu có idempotency key có thể được trả lại kết quả đã lưu khi đến lần lặp. Tuy nhiên, hệ thống phải xác minh key thuộc đúng chủ thể/phạm vi, nội dung yêu cầu phù hợp, trạng thái xử lý đã hoàn tất và kết quả có thể tái sử dụng. Chỉ kiểm tra “key tồn tại” rồi trả thành công có thể bỏ qua một lần xử lý đang dở hoặc cho kết quả của yêu cầu khác.

### 6.2 Cache hit cũng cần hợp đồng

Một kết quả cache hợp lệ thường cần xét:

- **Khóa đầy đủ:** đầu vào, tenant, người dùng hoặc phạm vi quyền khi kết quả phụ thuộc các yếu tố ấy.
- **Độ mới:** thời hạn sống, phiên bản, invalidation, mức consistency cho phép.
- **Kết quả đặc biệt:** cache lỗi, cache null, “không tìm thấy” có được phép không?
- **Thay đổi dữ liệu:** khả năng trả snapshot bất biến hoặc bản sao để tránh chia sẻ đối tượng mutable sai cách.
- **Đồng thời:** nhiều cache miss có thể cùng gọi nghiệp vụ; HashMap thông thường không an toàn khi truy cập/ghi đồng thời không được đồng bộ.

## 7. Ví dụ Java 17 chạy độc lập: cache hit và từ chối trước `proceed`

Chương trình dưới đây dùng **Java dynamic proxy** để tạo một điểm can thiệp qua interface. Hàm `around` có ba nhánh:

1. **Đầu vào sai:** ném `IllegalArgumentException`, không gọi tiếp.
2. **Cache hit:** trả kết quả đã lưu, không gọi tiếp.
3. **Cache miss:** gọi tiếp đúng một lần rồi lưu kết quả khi thành công.

Phương thức gốc `price(id)` được giả định là **truy vấn chỉ đọc, xác định theo ID**, không cần tạo tác dụng phụ ngoài việc truy vấn. Ví dụ có một biến đếm để chứng minh **số lần thân phương thức thật chạy**; biến đếm này chỉ phục vụ quan sát, không phải trách nhiệm nghiệp vụ của truy vấn giá.

Tạo file `AroundShortCircuitDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class AroundShortCircuitDemo {
    public interface Catalog {
        int price(int productId);
    }

    public static final class CatalogImpl implements Catalog {
        private int executions;

        @Override
        public int price(int productId) {
            executions++;
            System.out.println("BUSINESS call=" + executions);
            if (productId == 5) {
                throw new IllegalStateException("backend down");
            }
            return productId * 100;
        }

        int executions() {
            return executions;
        }
    }

    @FunctionalInterface
    interface Proceed {
        Object call() throws Throwable;
    }

    static Object around(
            int productId, Map<Integer, Integer> cache, Proceed proceed)
            throws Throwable {
        if (productId <= 0) {
            System.out.println("REJECT " + productId);
            throw new IllegalArgumentException(
                    "productId must be positive");
        }

        Integer cached = cache.get(productId);
        if (cached != null) {
            System.out.println("CACHE HIT " + productId);
            return cached; // Không gọi tiếp.
        }

        System.out.println("PROCEED " + productId);
        try {
            Object value = proceed.call(); // Gọi tiếp đúng một lần.
            int price = (Integer) value;
            cache.put(productId, price);
            System.out.println("CACHE STORE " + productId);
            return price;
        } catch (Throwable error) {
            System.out.println("AROUND ERROR "
                    + error.getClass().getSimpleName());
            throw error;
        }
    }

    static Object invokeTarget(
            Catalog target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static Catalog wrap(Catalog target) {
        Map<Integer, Integer> cache = new HashMap<>();

        return (Catalog) Proxy.newProxyInstance(
                Catalog.class.getClassLoader(),
                new Class<?>[] { Catalog.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return invokeTarget(target, method, args);
                    }
                    Object[] callArgs =
                            args == null ? new Object[0] : args.clone();

                    if (!method.getName().equals("price")) {
                        return invokeTarget(target, method, callArgs);
                    }
                    int productId = (Integer) callArgs[0];
                    return around(productId, cache,
                            () -> invokeTarget(target, method, callArgs));
                });
    }

    public static void main(String[] args) {
        CatalogImpl implementation = new CatalogImpl();
        Catalog catalog = wrap(implementation);

        System.out.println("RESULT " + catalog.price(2));
        System.out.println("RESULT " + catalog.price(2));

        try {
            catalog.price(-1);
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }

        try {
            catalog.price(5);
        } catch (IllegalStateException error) {
            System.out.println("CALLER " + error.getMessage());
        }

        System.out.println("RESULT " + catalog.price(3));
        System.out.println("EXECUTIONS " + implementation.executions());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac AroundShortCircuitDemo.java
java AroundShortCircuitDemo
~~~

Stdout:

~~~text
PROCEED 2
BUSINESS call=1
CACHE STORE 2
RESULT 200
CACHE HIT 2
RESULT 200
REJECT -1
CALLER productId must be positive
PROCEED 5
BUSINESS call=2
AROUND ERROR IllegalStateException
CALLER backend down
PROCEED 3
BUSINESS call=3
CACHE STORE 3
RESULT 300
EXECUTIONS 3
~~~

### 7.1 Phân tích từng nhánh

| Lần gọi | Quyết định của around advice | Có gọi tiếp? | Thân `CatalogImpl.price` chạy? | Bên gọi quan sát |
| --- | --- | --- | --- | --- |
| `price(2)` lần đầu | Cache miss | Có, một lần | Có | `200` |
| `price(2)` lần hai | Cache hit | **Không** | **Không** | `200` từ cache |
| `price(-1)` | Từ chối tiền điều kiện | **Không** | **Không** | `IllegalArgumentException` |
| `price(5)` | Cache miss, nghiệp vụ lỗi | Có, một lần | Có | `IllegalStateException` |
| `price(3)` | Cache miss | Có, một lần | Có | `300` |

Biến đếm `executions` cuối cùng bằng **3** vì chỉ các lần truy vấn ID 2 lần đầu, ID 5 và ID 3 chạy thân nghiệp vụ. Cache hit và đầu vào bị từ chối không làm tăng bộ đếm.

Bản thân việc `price(5)` ném lỗi không làm advice cache kết quả, bởi lệnh `cache.put` chỉ chạy sau `proceed.call()` trả về bình thường. Hàm `invokeTarget` tháo lớp bọc `InvocationTargetException` để chuyển tiếp ngoại lệ gốc từ thân phương thức.

### 7.2 Điều gì được bảo toàn trong ví dụ?

- **Kiểu trả về:** interface hứa `int`, advice trả một `Integer` có thể unbox thành `int`.
- **Giá trị:** cùng một ID hợp lệ trả một số giá ổn định trong giả định của ví dụ.
- **Lỗi đầu vào:** ID không dương bị từ chối bằng ngoại lệ đã chọn, không thực thi nghiệp vụ.
- **Lỗi backend:** lỗi từ thân phương thức được truyền tiếp; nó không bị biến thành giá cache tùy tiện.
- **Phạm vi:** advice chỉ bao những lời gọi đi qua proxy `catalog`.

Ví dụ dùng `HashMap` và biến đếm thường, **chỉ nhằm minh họa luồng đơn luồng**. Nếu truy cập đồng thời, cần chiến lược đồng bộ/cache phù hợp, độ mới dữ liệu, ngữ cảnh phân quyền, cơ chế chống cache stampede và yêu cầu hiệu năng. Ví dụ cũng **không** thể hiện một policy xác thực người dùng thật.

### 7.3 Vì sao không được khái quát quá mức?

Đây là một lớp bọc Java mô phỏng around advice. Nó **không phải cú pháp của một ngôn ngữ AOP phổ quát**, không tự weave bytecode và không nhìn thấy lời gọi trực tiếp như `implementation.price(2)`. Trong một hệ thống AOP khác, thời điểm join point và cách cung cấp continuation có thể khác. Chỉ điều có thể rút ra chắc chắn là: **nếu đường thực thi trả về hoặc ném lỗi trước khi gọi continuation, phần nằm sau continuation không được gọi qua đường đó**.

## 8. Nhiều advice lồng nhau: lớp nào bị bỏ qua?

Giả sử thứ tự lồng nhau:

~~~text
Metrics(
    Cache(
        Authorization(
            Audit(
                Business
            )
        )
    )
)
~~~

Nếu `Cache` trả kết quả mà không gọi tiếp, `Authorization`, `Audit` và `Business` **bị bỏ qua**, còn `Metrics` ở ngoài có thể ghi nhánh trả về bình thường. Điều này tạo **rủi ro nghiêm trọng về phân quyền**, ngay cả khi `Authorization` được viết chính xác.

Hai cách diễn đạt an toàn hơn phải được kiểm chứng theo hợp đồng:

1. Đặt **kiểm tra quyền không thể bỏ qua** ở lớp ngoài trước nhánh cache, để mỗi yêu cầu được kiểm tra trước khi truy xuất dữ liệu.
2. Hoặc bảo đảm lớp cache **tự áp dụng chính sách phân quyền đáng tin** khi trả dữ liệu và khóa cache đã phân vùng đúng; không thể chỉ dựa vào tên “cache” để chứng minh bảo mật.

Một cấu trúc gợi ý, không phải thứ tự mặc định của bất kỳ công cụ nào:

~~~text
Authorization(
    Metrics(
        Cache(
            Business
        )
    )
)
~~~

Đối với ví dụ này, mọi yêu cầu đi vào `Authorization` trước. Nhưng cần xác nhận một advice khác nằm ngoài cùng **không trả sớm trước kiểm tra quyền**, và mọi đường vào cần bảo vệ đều được can thiệp. Hợp đồng bảo mật là một **bất biến của toàn luồng**, không phải thuộc tính riêng của tên aspect.

### 8.1 Advice inside và outside không giống nhau

Khi `Cache` nằm ngoài `Metrics`:

~~~text
Cache(Metrics(Business))
~~~

cache hit làm `Metrics` không chạy; phép đo chỉ thấy cache miss. Khi `Metrics` nằm ngoài `Cache`:

~~~text
Metrics(Cache(Business))
~~~

mọi lời gọi qua wrapper đều được đo, kể cả cache hit. Hai hệ thống có thể cùng đúng nếu gọi rõ phép đo: **thời gian xử lý bên trong cache** so với **độ trễ toàn yêu cầu**.

Tương tự, nếu audit bắt buộc đếm **mọi lần người dùng yêu cầu**, nó không thể chỉ nằm trong lớp bị cache bỏ qua. Cần xác định ranh giới đo và ranh giới sự kiện trước khi cấu hình thứ tự advice.

## 9. Hợp đồng của `proceed`: không lần nào, một lần, nhiều lần

### 9.1 Không lần nào — short-circuit

~~~text
if validCacheHit:
    return cachedValue
if forbidden:
    throw policyError
~~~

Phải chứng minh hành vi thay thế đúng hợp đồng; mọi advice và nghiệp vụ bên trong có thể không chạy.

### 9.2 Một lần — bao quanh thông thường

~~~text
before()
try:
    return proceed()
finally:
    after()
~~~

Ở đây, phần bên trong được yêu cầu thực thi một lần. Tuy nhiên, một advice khác bên trong vẫn có thể short-circuit, nên “gọi `proceed`” không tự chứng minh thân nghiệp vụ thật chạy.

### 9.3 Nhiều lần — retry hoặc lặp có chủ đích

~~~text
for attempt in allowedAttempts:
    try:
        return proceed()
    catch retryable:
        continue
throw lastFailure
~~~

Đây là **giả mã**, và chỉ áp dụng khi cơ chế AOP cho phép gọi lại continuation theo cách phù hợp. Gọi lại có thể lặp tác dụng phụ, gửi yêu cầu trùng hoặc gây sai trạng thái giao dịch. Nếu nghiệp vụ không idempotent, một advice retry đặt sai có thể gây hậu quả nghiêm trọng.

**Phân biệt đơn vị đếm:** một request logic có thể thực hiện nhiều attempt, còn một cache hit có thể thực hiện không attempt nào. Metrics và audit cần phản ánh đúng đơn vị.

## 10. Trách nhiệm với transaction, locks và tài nguyên

Nếu around advice mở tài nguyên trước khi chọn short-circuit, nó vẫn phải đóng tài nguyên đã mở. Ví dụ:

~~~text
resource = open()
try:
    if cacheHit:
        return cachedResult
    return proceed()
finally:
    close(resource)
~~~

Ở đây `finally` cần chạy khi rời phạm vi dù nhánh cache có `return`. Nếu người viết return ở ngoài hoặc trước khối quản lý tài nguyên, cần xét xem tài nguyên đã được mở ở đâu.

Tuy nhiên, một short-circuit ở lớp ngoài trước giao dịch có thể làm **transaction advice bên trong chưa bao giờ được gọi**. Điều đó có thể tốt nếu không cần transaction cho cache hit, nhưng cũng có thể sai nếu hợp đồng yêu cầu thao tác ghi nhận giao dịch hoặc tính nhất quán cụ thể.

Với khóa và đồng bộ, một advice không được tùy tiện giữ khóa khi chuyển sang xử lý bất đồng bộ, bỏ qua `unlock` hoặc thay đổi thứ tự khóa. Nếu advice can thiệp trong vùng đang giữ khóa, việc gọi log hoặc truy cập cache có thể tăng contention hoặc tạo deadlock; phải giữ phần xử lý nhanh và có chiến lược đồng thời đúng.

## 11. Fallback, circuit breaker và tính minh bạch

### 11.1 Fallback không mặc nhiên là thành công

Một around advice có thể trả fallback khi dịch vụ không sẵn sàng, nhưng hợp đồng cần trả lời: fallback là **giá trị cuối cùng hợp lệ**, **dữ liệu suy giảm có đánh dấu**, hay **một sự thất bại được chuyển dạng**?

Ví dụ, API đọc giá có thể cho phép trả giá cache với dấu “stale” nếu tài liệu hợp đồng quy định rõ. API tài chính yêu cầu giá hiện thời có thể **không** được phép làm vậy. Nếu trả giá trị sai mà không thông báo, bên gọi nhận một “thành công giả”.

### 11.2 Circuit breaker có thể từ chối trước khi gọi tiếp

Khi circuit đang mở, advice có thể ném một lỗi “dịch vụ tạm thời không khả dụng” trước khi truy cập backend. Đây là một short-circuit hợp lệ nếu là hành vi được cho phép. Phải quy định chính xác loại lỗi, retry hint, timeout, thời gian mở circuit và cách khôi phục.

### 11.3 Không nuốt ngoại lệ chỉ để giữ đường chạy xanh

Bắt mọi ngoại lệ rồi trả `null`, `0`, chuỗi rỗng hoặc kết quả mặc định thường **phá hợp đồng** khi các giá trị đó mang ý nghĩa hợp lệ khác. Cần phân loại lỗi có thể khôi phục, trường hợp từ chối, lỗi lập trình và trạng thái không thể suy ra kết quả an toàn.

## 12. Quan sát và truy vết đúng khi không thực thi nghiệp vụ

Nếu chỉ đo số lần chạy thân phương thức, cache hit không được tính là “backend execution”. Nếu chỉ đo số yêu cầu từ phía người dùng, cache hit **vẫn** là một request.

Bộ đo nên có tên và phạm vi rõ:

| Đại lượng | Có tính cache hit? | Giải thích |
| --- | --- | --- |
| `requests` | Có | Số yêu cầu đi vào phạm vi ngoài cùng |
| `backend_attempts` | Không | Chỉ những lần thực sự gọi tiếp tới backend theo điểm quan sát |
| `cache_hits` | Có | Số yêu cầu được trả từ cache |
| `policy_rejections` | Có | Số lần advice từ chối theo chính sách |
| `backend_errors` | Không với cache hit | Chỉ lỗi của lần backend thực sự được gọi |
| `end_to_end_latency` | Có | Nếu bộ đo bao ngoài nhánh short-circuit |
| `backend_latency` | Không | Nếu chỉ đo vùng gọi backend |

Điều kiện “có/không” ở bảng phụ thuộc các vị trí instrument đã nêu, không phải là mặc định nền tảng.

Trace nên thể hiện rõ **cache hit, policy reject, fallback, attempted backend** để người vận hành không nghĩ nghiệp vụ đã chạy khi chưa chạy. Nếu một quanh advice rút ngắn từ ngoài, các span/audit nằm trong có thể không xuất hiện; đó là lý do instrumentation cho yêu cầu tổng thể thường cần đặt bên ngoài các lớp có thể short-circuit.

## 13. Bẫy khi chọn điểm can thiệp

Một quanh advice chỉ có ý nghĩa ở những **join point thực sự được weave**. Giả sử một cơ chế dùng proxy qua giao diện:

- Lời gọi thông qua proxy có thể được advice can thiệp.
- Lời gọi trực tiếp tới đối tượng đích có thể bỏ qua proxy.
- Lời gọi nội bộ từ phương thức này tới phương thức khác trên đối tượng đích có thể không quay qua proxy.
- Nếu chỉ can thiệp method call mà không hỗ trợ method execution, một số đường vào phần thân có thể nằm ngoài phạm vi quan sát.
- Kế thừa, overload, bridge/synthetic methods hoặc class được sinh có thể tác động vào chữ ký/đường gọi; phải kiểm chứng theo nền tảng.

Với hệ thống an ninh, đừng đặt mọi bảo vệ vào một around advice chỉ thấy một phần đường gọi. Phải có ranh giới đáng tin **bao phủ tất cả điểm truy cập được bảo vệ**, kết hợp các bất biến nghiệp vụ ở nơi phù hợp. Một pointcut được viết “đúng cú pháp” không chứng minh phạm vi được bảo vệ là đầy đủ.

## 14. Những sai lầm phổ biến

1. **“Không gọi `proceed` luôn là lỗi.”** Sai. Cache hit, từ chối hợp lệ hoặc fallback được định nghĩa có thể chủ đích không gọi tiếp.
2. **“Không gọi tiếp thì chỉ cần trả đúng kiểu.”** Sai. Còn hậu điều kiện, ngoại lệ, tác dụng phụ, consistency, security và quan sát.
3. **“Cache hit là nghiệp vụ đã chạy thành công lần này.”** Sai. Có thể không có lần thực thi backend nào trong yêu cầu hiện tại.
4. **“Trả `null` dùng được với mọi phương thức.”** Sai với primitive, `Optional`, non-null contract và completion handle.
5. **“Return từ advice làm mọi advice khác dừng ngay.”** Sai. Lớp ngoài đã vào có thể tiếp tục after/finally; chỉ phần bên trong bị skip.
6. **“Một advice gọi `proceed` nghĩa là thân nghiệp vụ chắc chắn chạy.”** Sai nếu một advice bên trong có quyền short-circuit.
7. **“Mọi lỗi backend đều nên chuyển thành kết quả mặc định.”** Sai nếu fallback không nằm trong hợp đồng.
8. **“`void` không có tác dụng phụ.”** Sai. Nhiều phương thức void chủ yếu tạo tác dụng phụ.
9. **“Cứ lưu theo khóa đầu vào là cache an toàn.”** Sai nếu kết quả còn phụ thuộc quyền, người dùng, tenant, phiên bản hoặc thời gian.
10. **“Advice kiểm tra quyền ở bên trong cache vẫn luôn chạy.”** Sai nếu cache trả sớm trước khi gọi tiếp.
11. **“Bỏ qua backend nghĩa là đã bảo đảm idempotency.”** Sai nếu thiếu giao thức ghi nhận trạng thái yêu cầu và kết quả tương ứng.
12. **“Một mô hình around advice áp dụng giống nhau cho mọi framework.”** Sai; phải xem cơ chế hỗ trợ continuation, thay kết quả và ngoại lệ.

## 15. Checklist thiết kế một nhánh short-circuit

Trước khi triển khai around advice có thể không gọi tiếp, hãy trả lời:

1. **Điểm chọn:** pointcut áp dụng tại method call hay method execution? Có bao phủ mọi đường vào mong muốn không?
2. **Ngữ cảnh:** advice lấy dữ liệu từ đâu? Dữ liệu runtime nào là đáng tin? Có cần phân biệt target, caller và tham số?
3. **Lý do short-circuit:** cache hit, validation reject, authorization deny, circuit open, fallback hay dedup?
4. **Hợp đồng kiểu:** kết quả thay thế có đúng kiểu/đơn vị/nullability/generic và trạng thái hoàn tất không?
5. **Hợp đồng lỗi:** loại ngoại lệ hoặc tín hiệu thất bại có phù hợp bên gọi không? Có giữ cause và không che lỗi quan trọng không?
6. **Hậu điều kiện:** bên gọi có nhận được trạng thái hợp lệ dù phần thân không thực thi không?
7. **Tác dụng phụ:** điều gì bắt buộc xảy ra? Điều gì được phép bỏ qua?
8. **Bảo mật:** kiểm tra quyền có thể bị lớp ngoài skip không? Cache có bị dùng chung qua người dùng/tenant sai cách không?
9. **Thứ tự advice:** outer/inner nào bị bỏ qua, lớp nào vẫn cleanup hoặc ghi audit?
10. **Tài nguyên:** khóa, giao dịch, kết nối và context tạm có được dọn dẹp đúng mọi đường không?
11. **Đồng thời:** có race condition, cache stampede, yêu cầu đang xử lý dở hoặc retry trùng không?
12. **Quan sát:** log/metrics thể hiện đúng `cache-hit`, `rejected`, `fallback`, `attempted`?
13. **Bất đồng bộ:** trả completion handle hợp lệ, báo hoàn tất/thất bại/hủy đúng hợp đồng không?
14. **Khả năng thực thi:** nền tảng AOP có hỗ trợ thật kiểu join point, around advice và continuation mà thiết kế cần không?

Nếu không trả lời được một điều khoản quan trọng, **không nên coi một lệnh `return` là đủ để thay thế thực thi gốc**.

## 16. Bài tập tự đánh giá

### Bài tập 1: Bộ nhớ đệm trước kiểm tra quyền

Cho mô hình:

~~~text
Cache(Authorization(Business))
~~~

Cache tìm thấy kết quả cho một ID và trả thẳng về, không gọi tiếp. Điều gì xảy ra?

**Đáp:** `Authorization` và `Business` ở bên trong không chạy. Nếu kiểm tra quyền là bắt buộc trên mọi lần đọc, thiết kế có nguy cơ vi phạm bảo mật. Cần đặt rào chắn quyền ngoài đường short-circuit hoặc tích hợp kiểm soát quyền đáng tin tại nhánh trả cache và phân vùng cache phù hợp.

### Bài tập 2: Trả sai kiểu

Phương thức gốc trả `int`, nhưng proxy handler trả `null` để “bỏ qua” phần gốc. Đây là một thay thế hợp lệ không?

**Đáp:** không. Quá trình unboxing kết quả `null` sang `int` có thể phát sinh `NullPointerException`. Ngay cả khi trả `0`, cũng phải chứng minh `0` là kết quả hợp lệ theo ngữ nghĩa nghiệp vụ.

### Bài tập 3: Cache của phương thức ghi

Một phương thức `void submitOrder(Order)` có nghĩa vụ tạo đơn hàng. Có thể dùng cache hit để không gọi tiếp, rồi trả bình thường không?

**Đáp:** không thể suy như vậy. Muốn hỗ trợ yêu cầu lặp, cần giao thức idempotency/dedup đáng tin chứng minh yêu cầu tương đương đã hoàn thành và trạng thái được bảo toàn. Bỏ qua thao tác ghi tùy tiện phá hợp đồng.

### Bài tập 4: Ngoại lệ sau khi đã gọi tiếp

Advice gọi `proceed`, nghiệp vụ ném lỗi, advice bắt lỗi rồi trả fallback. Đây là trường hợp “không gọi tiếp” không?

**Đáp:** không. Nó đã gọi phần bên trong rồi **chuyển đổi đường thất bại** thành một kết quả; tác dụng phụ một phần vẫn có thể đã xảy ra. Phải kiểm tra chính sách rollback và ý nghĩa fallback.

### Bài tập 5: Metrics nằm ở trong cache

~~~text
Cache(Metrics(Business))
~~~

Cache hit thì metrics có ghi không?

**Đáp:** nếu cache trả sớm mà không gọi tiếp, `Metrics` bên trong không được thực thi. Nếu cần đo mọi yêu cầu, đặt phép đo ở phạm vi bên ngoài short-circuit.

### Bài tập 6: Nhánh trả sớm trong `try/finally`

Một around advice đã mở tài nguyên rồi trả cached value từ trong khối `try`. Điều gì cần bảo đảm?

**Đáp:** `finally` của phạm vi đó vẫn chạy để dọn tài nguyên. Mã cleanup cũng không được vô tình làm mất kết quả hoặc che ngoại lệ gốc.

### Bài tập 7: Phương thức bất đồng bộ

Một phương thức hứa trả `CompletionStage<Result>`. Trả `null` ngay vì cache hit có tương đương trả kết quả cache không?

**Đáp:** không. Bên gọi kỳ vọng một đối tượng biểu diễn sự hoàn thành. Nếu short-circuit hợp lệ, cần trả completion handle phù hợp theo hợp đồng, cùng quy tắc lỗi, cancellation và context.

## 17. Tổng kết

**Không gọi tiếp trong around advice** có thể là một kỹ thuật thiết kế hợp lệ và hiệu quả: trả kết quả cache, từ chối yêu cầu hoặc cung cấp fallback. Nhưng điều đó chỉ đúng khi chính sách cho phép rút ngắn đường thực thi và kết quả quan sát được **vẫn thỏa hợp đồng**.

Hãy luôn tách ba việc:

- **Quyết định điều khiển:** có gọi continuation hay short-circuit?
- **Bảo toàn hợp đồng:** kiểu trả về, giá trị, lỗi, tác dụng phụ, bảo mật, nhất quán và vòng đời tài nguyên có còn đúng?
- **Phạm vi can thiệp:** join point/pointcut và thứ tự nhiều advice có bảo đảm mọi hành vi bắt buộc vẫn diễn ra không?

Điểm cốt lõi: **around advice có quyền thay thế đường thực thi, nhưng quyền đó đi cùng trách nhiệm chứng minh hành vi thay thế là hợp lệ**. Khi còn nghi ngờ về hậu điều kiện hoặc bảo mật, đừng bỏ qua `proceed` chỉ vì một giá trị có vẻ hợp lý.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-004">Thay đổi tham số hoặc kết quả khi công cụ cho phép</a>

<details>
<summary>Xem chi tiết</summary>

# Thay đổi tham số hoặc kết quả khi công cụ cho phép trong Around Advice

## 1. Bản chất của việc thay đổi dữ liệu tại điểm can thiệp

Trong **Aspect-Oriented Programming (AOP)**, một **around advice** có thể được đặt bao quanh một **join point** được pointcut chọn. Trong mô hình có khả năng điều phối tiếp tục thực thi, around advice nhận quyền quyết định **có tiếp tục vào hành vi gốc hay không**, **tiếp tục với dữ liệu nào** và **giá trị nào được trả về phía caller**. Điều này khác before advice, vốn thông thường chỉ chạy trước join point mà không trực tiếp sở hữu cơ chế tiếp tục hay giá trị cuối cùng.

Một số ngôn ngữ AOP hỗ trợ gọi một thao tác tương tự **proceed** với đối số mới. Một số cơ chế interceptor cho phép chỉnh sửa danh sách đối số trước khi gọi target hoặc thay giá trị trả về sau lời gọi. Nhưng **không có bảo đảm phổ quát rằng mọi công cụ AOP đều cho phép sửa tham số, đổi kết quả hoặc bỏ qua target**: quyền hạn phụ thuộc loại join point, weaving/interception, API tiếp tục và hợp đồng kiểu của công cụ.

Mô hình ý tưởng:

~~~text
Caller gửi đối số A
        │
        ▼
Pointcut chọn join point
        │
        ▼
Around advice nhận ngữ cảnh ban đầu
        │
        ├─ Kiểm tra A, quyết định gọi tiếp hay dừng
        │
        ├─ Nếu gọi tiếp: tạo đối số A' hợp lệ
        │       │
        │       ▼
        │    proceed(A') / invoke(target, A')
        │       │
        │       ▼
        │    Target thực thi và trả R
        │       │
        └─ Có thể đổi R thành R' hợp lệ
                │
                ▼
         Caller nhận R'
~~~

Có ba quyết định **khác nhau** cần học:

1. **Thay đổi đối số đi vào target:** caller truyền A, target có thể nhận A' nếu cơ chế cho phép.
2. **Thay đổi kết quả đưa về caller:** target trả R, caller có thể nhận R' nếu advice thay kết quả hợp lệ.
3. **Bỏ qua hoặc lặp target:** advice có thể không tiếp tục, hoặc tiếp tục nhiều lần trong một số mô hình; điều này không đồng nhất với việc chỉ thay đối số/kết quả.

Thay đổi tham số hoặc kết quả là **thay đổi hành vi quan sát được của chương trình**, không còn là logging thuần túy. Vì thế phải có yêu cầu thiết kế cụ thể và kiểm thử hợp đồng.

## 2. Phân biệt bốn kiểu thay đổi thường bị nhầm lẫn

| Kiểu thay đổi | Ví dụ | Có chắc target nhận thay đổi không? |
|---|---|---|
| **Gán lại biến local trong advice** | Đổi biến amount từ 10 thành 20 trong scope advice | Không; việc gán lại local không tự sửa tham số của lời gọi gốc |
| **Sửa trạng thái object mutable được chia sẻ** | Thêm phần tử vào List được bind từ argument | Target có thể thấy thay đổi trên cùng object, nhưng đó là side effect, không nhất thiết là thay thế argument |
| **Thay argument lúc tiếp tục** | Gọi cơ chế proceed với amount mới | Có nếu công cụ cho phép và dữ liệu mới được bind đúng vị trí/chữ ký |
| **Thay return sau khi target chạy** | Target trả 50, advice trả 52 | Caller có thể nhận 52 nếu công cụ cho phép, return type tương thích và không có advice bao ngoài đổi thêm |

Ví dụ Java về reference:

~~~java
static void inspect(java.util.List<String> values) {
    values.add("marker"); // Mutate object mà caller và target có thể cùng thấy.
    values = new java.util.ArrayList<>(); // Chỉ đổi local reference.
}
~~~

Đây là đoạn minh họa, không phải chương trình Java độc lập. Java truyền **giá trị của reference** cho parameter. Nếu advice chỉ gán lại một local parameter, target không tự nhận reference mới; muốn thay argument ở ranh giới tiếp tục, phải dùng chức năng do mô hình AOP cung cấp.

Một lỗi nghiêm trọng là gọi việc mutate object là “thay tham số an toàn” rồi quên rằng caller vẫn giữ reference đến object gốc. Điều này có thể gây side effect lâu dài ngoài join point.

## 3. Around advice khác before, after và after returning như thế nào?

| Loại advice | Khi nào chạy? | Có thể tự điều phối target không? | Có thể trực tiếp đổi return của target theo nghĩa thông thường không? |
|---|---|---|---|
| **Before** | Trước join point | Thông thường không có proceed | Không; có thể throw hoặc mutate dữ liệu dùng chung |
| **After returning** | Sau khi hoàn tất bình thường | Không trực tiếp điều phối việc gọi target | Thông thường để quan sát kết quả; không tự thay return theo kiểu around |
| **After throwing** | Khi join point kết thúc bằng ngoại lệ phù hợp | Không trực tiếp điều phối | Thường dùng để quan sát/ghi lỗi |
| **After/finally** | Khi rời join point theo quy tắc của engine | Không trực tiếp điều phối | Không có bảo đảm thay kết quả |
| **Around** | Bao quanh phần tiếp tục của join point | Có thể quyết định khi nào/bao nhiêu lần tiếp tục nếu engine hỗ trợ | Có thể trả kết quả khác nếu API và hợp đồng kiểu cho phép |

“Có thể” không nghĩa “nên”. Một around advice chỉ để đo thời gian thường nên **trả nguyên kết quả và lan truyền nguyên ngoại lệ**, dù về kỹ thuật nó có khả năng thay đổi chúng.

Ngược lại, một around advice thực hiện **cache**, **chặn thao tác**, **retry**, **giới hạn giá trị**, **chuyển đổi kiểu dữ liệu** hoặc **fallback** có thể có lý do chính đáng để thay đối số hoặc return. Lý do cần thể hiện qua hợp đồng và test, không giấu trong tên gọi chung “advice”.

## 4. Quyền tiếp tục là điều kiện để biến đổi dữ liệu

`proceed` hoặc continuation tương đương chuyển điều khiển vào phần còn lại của chuỗi advice, có thể qua nhiều advice trước target. Vì vậy cần tách **khả năng đọc đối số**, **quyền thay đối số lúc tiếp tục**, **quyền thay result** và **quyền điều phối số lần gọi**.

| Quyết định quanh continuation | Điều phải kiểm tra |
|---|---|
| **Gọi tiếp một lần** | Target/inner advice nhận đúng args; kết quả và lỗi được chuyển tiếp hoặc biến đổi có hợp đồng |
| **Không gọi tiếp** | Nếu trả fallback, đó phải là kết quả hợp lệ; side effect bắt buộc của target không được bỏ sót |
| **Gọi tiếp nhiều lần** | Công cụ có hỗ trợ và thao tác có an toàn khi lặp không? Retry phải xét idempotency và số effect |
| **Thay args/result** | API và loại join point có cho phép không? Kiểu, thứ tự, nullability và ý nghĩa nghiệp vụ có còn đúng không? |

Đặc biệt, **gán lại một biến local không tự thay argument mà target nhận**. Những điểm này là ranh giới trước khi đi sâu vào việc thay dữ liệu ở các phần tiếp theo, không phải lời hứa rằng mọi AOP đều cung cấp một lệnh `proceed(newArgs)` thống nhất.

## 5. Thay đổi đối số đầu vào: có những bước nào?

Xét một phương thức:

~~~java
int charge(int units);
~~~

Đây là khai báo minh họa. Một chính sách kỹ thuật có thể yêu cầu **giới hạn units tối đa 5** trước khi tiếp tục.

Trình tự:

1. Nhận giá trị caller truyền, ví dụ units = 9.
2. Kiểm tra loại dữ liệu, phạm vi, hợp lệ/ngữ nghĩa của đơn vị.
3. Tạo giá trị mới, ví dụ min(9, 5) = 5.
4. Tiếp tục gọi target **với 5**, nếu engine cho phép đổi argument.
5. Target thực thi trên giá trị 5, không phải 9.
6. Advice có thể quan sát kết quả hoặc thay đổi kết quả sau đó.

Điểm then chốt: bước 4 **phải thực sự chuyển 5 vào đường tiếp tục**. Việc chỉ in “normalized = 5” hoặc gán lại biến local không bảo đảm target nhận được 5.

### 5.1. Đúng số lượng và đúng vị trí

Với chữ ký:

~~~java
String build(String name, int count, boolean urgent);
~~~

Nếu cơ chế dùng argument array, phần tử 0 phải tương ứng name, phần tử 1 tương ứng count, phần tử 2 tương ứng urgent. Một thay đổi vị trí sẽ làm sai ý nghĩa hoặc thất bại kiểm tra kiểu.

Khi dùng overload, **tên phương thức không đủ** phân biệt chữ ký. Hai overload có thể cần quy tắc normalize hoàn toàn khác.

### 5.2. Primitive, wrapper và chuyển kiểu

Java phân biệt primitive như int, long, boolean và kiểu reference như Integer, Long, Boolean. Khi engine dùng reflection, primitive có thể được đại diện dưới wrapper trong Object array, nhưng giá trị được truyền cuối cùng vẫn phải tương thích với method signature.

Không nên giả định:

- Có thể gửi null cho primitive int.
- Có thể gửi String thay cho int mà Java tự parse.
- Có thể thu hẹp long sang int không mất dữ liệu.
- Mọi conversion hợp lệ ở source Java cũng được interceptor thực hiện ngầm theo cách giống compiler.

Nếu cần convert, hãy convert có kiểm tra, xác định chính sách overflow và validate lại theo hợp đồng method.

### 5.3. Reference type và immutable object

Nếu caller truyền một String, không thể mutate nội dung bên trong String; muốn target thấy String khác phải thay reference được dùng tại lời gọi tiếp tục. Với mutable List, có hai lựa chọn mang nghĩa khác nhau:

~~~text
Option A: mutate list cũ rồi tiếp tục với chính list đó
Option B: tạo bản sao đã chuẩn hóa rồi tiếp tục với list mới
~~~

Option A thay dữ liệu mà caller có thể đang giữ; Option B có thể cô lập một số side effect nếu sao chép đúng độ sâu và loại dữ liệu. Copy shallow chỉ sao chép container, không sao chép mọi object con.

### 5.4. Null và validation

Null có thể là giá trị hợp lệ theo API hoặc là dấu hiệu input bị lỗi. Một around advice tự đổi null thành giá trị mặc định có thể che mất lỗi hoặc biến một tham số mang ý nghĩa “không có” thành dữ liệu giả.

Chỉ normalize null khi **hợp đồng API** yêu cầu hoặc cho phép; ghi rõ sự khác nhau giữa “caller không cung cấp” và “caller cung cấp giá trị mặc định”.

## 6. Thay đổi giá trị trả về: phải bảo toàn những ràng buộc nào?

Giả sử target trả về 50 nhưng advice thêm một khoản tính phí 2 và trả 52. Điều này hợp lệ về mặt kỹ thuật nếu:

- Cả target và advice trả giá trị phù hợp với kiểu trả về khai báo.
- Policy tính phí được phép áp dụng đúng tại điểm đó.
- Không tạo tính phí kép khi có aspect hoặc wrapper khác.
- Nếu target throw, không vô ý trả 52 như thể target thành công.
- Nếu target được gọi nhiều lần, kết quả và side effect vẫn nhất quán.

Một giả mã:

~~~text
around selectedMethod:
    raw = proceed()
    adjusted = raw + surcharge
    return adjusted
~~~

Không phải mọi ngôn ngữ AOP chấp nhận biểu thức trên đúng cú pháp. Ý tưởng là **advice lấy kết quả từ đường tiếp tục và chủ động chọn giá trị trả về**.

### 6.1. Kiểu trả về phải tương thích

| Kiểu method gốc | Thay đổi phù hợp về mặt kiểu | Rủi ro thường gặp |
|---|---|---|
| **int / long / double** | Trả giá trị cùng kiểu tương thích theo engine | Overflow, precision, nhầm đơn vị tiền tệ |
| **String** | Trả một String khác | Đổi ngữ nghĩa, encoding, nội dung bí mật |
| **Object/interface** | Trả object triển khai interface được yêu cầu | ClassCastException hoặc thay behavior bất ngờ |
| **Optional<T>** | Trả Optional phù hợp | Nhầm empty với lỗi/thất bại |
| **void** | Không có return value nghiệp vụ | Không thể thay thành một số hoặc object như method có giá trị |
| **Future/CompletionStage** | Trả một kết quả bất đồng bộ tương thích | Advice chỉ sửa wrapper, chưa chắc sửa payload khi future hoàn tất |

Với method trả primitive, một interceptor trả null hoặc object không tương thích có thể gây lỗi runtime. Với generic, type erasure và bridge methods có thể khiến kiểm tra kiểu ở nhiều lớp khác nhau; phải xác minh với engine thực.

### 6.2. Trả kết quả thay thế mà không gọi target

Nếu quanh một method có **cache hit**, advice có thể trả dữ liệu được lưu từ trước theo chính sách. Nhưng phải trả dữ liệu đúng tuổi, đúng key, đúng phân quyền và đúng type.

Nếu target lẽ ra phải tăng bộ đếm nghiệp vụ, ghi transaction hoặc giữ invariant, bỏ qua target chỉ vì cache hit có thể làm sai hệ thống. Cache quanh **đọc dữ liệu thuần hoặc đọc được phép tái sử dụng** thường có hợp đồng dễ chứng minh hơn.

### 6.3. Trả fallback sau ngoại lệ

Một around advice có thể bắt lỗi từ proceed và trả giá trị fallback, nếu công cụ hỗ trợ:

~~~text
around:
    try:
        return proceed()
    catch TemporaryFailure:
        return approvedFallback
~~~

Đây là quyết định thay đổi **exception contract**. Nếu caller cần biết rằng thao tác thất bại để rollback hoặc cảnh báo, nuốt lỗi rồi trả giá trị mặc định sẽ làm sai hành vi.

Fallback cần giới hạn loại ngoại lệ được xử lý, khả năng chấp nhận dữ liệu cũ/mặc định và cách ghi nhận failure. Không nên catch mọi Throwable rồi trả một giá trị “cho hệ thống ổn định”.

## 7. Ví dụ Java 17 chạy được: biến đổi đối số, thay kết quả, bỏ qua target

Để quan sát điều khiển một cách rõ ràng mà không cần cài ngôn ngữ AOP riêng, ví dụ sau dùng **JDK dynamic proxy**. Invocation handler đóng vai trò **mô phỏng một around interceptor**: nhận tham số, quyết định gọi target, thay tham số trước khi invoke và đổi giá trị sau khi target trả về.

Đây **không phải bộ dệt bytecode** và **không phải một pointcut language hoàn chỉnh**. Nó là ví dụ Java độc lập để kiểm tra hành vi.

Tạo file **AroundTransformationDemo.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class AroundTransformationDemo {
    interface Checkout {
        int charge(int units);
    }

    static final class Core implements Checkout {
        int calls;

        @Override
        public int charge(int units) {
            calls++;
            System.out.println("core: units=" + units);
            return units * 10;
        }
    }

    static Checkout intercepted(Checkout target) {
        return (Checkout) Proxy.newProxyInstance(
                Checkout.class.getClassLoader(),
                new Class<?>[]{Checkout.class},
                (proxy, method, args) -> {
                    int original = (int) args[0];
                    System.out.println("around: original=" + original);

                    if (original < 0) {
                        throw new IllegalArgumentException(
                                "units must be nonnegative");
                    }
                    if (original == 0) {
                        System.out.println("around: skipped");
                        return 0;
                    }

                    int rewritten = Math.min(original, 5);
                    System.out.println("around: proceeding=" + rewritten);
                    try {
                        int raw = (int) method.invoke(
                                target, new Object[]{rewritten});
                        System.out.println("around: raw=" + raw);
                        return raw + 2;
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    public static void main(String[] args) {
        Core core = new Core();
        Checkout checkout = intercepted(core);

        System.out.println("request=3");
        System.out.println("result=" + checkout.charge(3));

        System.out.println("request=9");
        System.out.println("result=" + checkout.charge(9));

        System.out.println("request=0");
        System.out.println("result=" + checkout.charge(0));

        System.out.println("request=-1");
        try {
            checkout.charge(-1);
        } catch (IllegalArgumentException e) {
            System.out.println("caught=" + e.getMessage());
        }

        System.out.println("core calls=" + core.calls);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 AroundTransformationDemo.java
java AroundTransformationDemo
~~~

Stdout chính xác:

~~~text
request=3
around: original=3
around: proceeding=3
core: units=3
around: raw=30
result=32
request=9
around: original=9
around: proceeding=5
core: units=5
around: raw=50
result=52
request=0
around: original=0
around: skipped
result=0
request=-1
around: original=-1
caught=units must be nonnegative
core calls=2
~~~

**Giải thích theo từng trường hợp:**

- **charge(3):** advice nhận 3, chuyển tiếp 3 cho Core; Core tính 30; interceptor thêm 2; caller nhận 32.
- **charge(9):** advice nhận 9, giới hạn đối số thành 5, target chỉ thấy 5; Core tính 50; interceptor trả 52. Đó là **thay input và output** trên cùng một lượt.
- **charge(0):** advice không gọi method.invoke; target hoàn toàn không chạy ở lượt này, và interceptor trả 0.
- **charge(-1):** advice ném IllegalArgumentException trước khi gọi target. Ngoại lệ đến caller, không có kết quả nghiệp vụ bình thường.
- **core calls=2:** chỉ lượt charge(3) và charge(9) đi vào Core, minh chứng rõ việc skip/throw ảnh hưởng số lần target chạy.

Nếu caller giữ reference trực tiếp tới **core** và gọi **core.charge(9)**, lời gọi ấy sẽ đi thẳng vào Core và trả 90, vì không đi qua interceptor. Do đó kỹ thuật proxy chỉ áp dụng chính sách lên đường đã được bao bằng proxy; không nên suy thành “mọi lần gọi charge ở toàn ứng dụng đều tự động được biến đổi”.

Trong ví dụ thực tế về hóa đơn, việc giới hạn số lượng và cộng khoản phí **chỉ là giả định phục vụ giải thích cơ chế**. Không nên coi đây là chính sách tính phí đúng cho mọi ứng dụng; những biến đổi nghiệp vụ phải được chủ sở hữu API quy định.

### 7.1. Vì sao phải unwrap InvocationTargetException?

Khi Java reflection gọi target bằng Method.invoke, một lỗi do target ném có thể được bọc trong InvocationTargetException. Handler dùng **getCause()** để chuyển lỗi gốc đúng hơn đến caller.

Nếu không tách lỗi, caller có thể thấy dạng exception khác với contract nghiệp vụ. Ngoài ra, một dynamic proxy có thể dùng UndeclaredThrowableException để bọc checked exception không hợp lệ theo interface. Đây là rủi ro cần quan sát khi thiết kế around interceptor dùng reflection.

### 7.2. Vì sao cùng một ví dụ có thể biểu diễn nhiều lựa chọn quanh target?

Handler không chỉ chạy một lệnh before. Nó **quyết định cả phần trước, phần tiếp tục và phần sau**:

~~~text
Before: nhận và kiểm tra original
Decision: throw / skip / proceed với rewritten
Target: chạy hoặc không chạy
After: đọc raw và trả adjusted
~~~

Đó là những năng lực khiến around advice khác before/after thuần túy.

## 8. Ví dụ pointcut tổng quát với proceed thay tham số

Đoạn dưới là **giả mã AOP**, độc lập công cụ:

~~~text
aspect QuantityPolicy
    pointcut selected =
        execution of Checkout.charge(int)

    around selected(originalUnits):
        if originalUnits < 0:
            throw InvalidUnits

        if originalUnits == 0:
            return 0

        safeUnits = min(originalUnits, 5)

        raw = proceedWith(safeUnits)
        return raw + 2
end
~~~

Khi chuyển sang một ngôn ngữ AOP cụ thể, cần đổi đúng cú pháp và semantics của công cụ. Ví dụ một số hệ thống ràng buộc tham số qua **args(...)**, rồi cho truyền giá trị mới tương ứng qua **proceed**; một số API cung cấp phương thức tương đương **proceed(newArguments)**.

Không phải cứ đổi nội dung một mảng args do metadata cung cấp là target chắc chắn nhận mảng mới. Một số metadata chỉ để **đọc**; một số request context không đồng nghĩa đối số của method. Hãy kiểm tra ví dụ và test với phiên bản công cụ đang dùng.

## 9. Điều kiện tiên quyết để thay đối số đúng

### 9.1. Chọn đúng join point

Nếu chọn method-call join point, thao tác thay argument gắn với **một vị trí gọi**. Nếu chọn method-execution join point, nó gắn với **phần thân được thực thi** theo cơ chế được hỗ trợ. Sự khác biệt quan trọng khi:

- Một method được gọi từ nhiều caller.
- Một interface có nhiều implementation.
- Lớp con override method.
- Lời gọi nội bộ hoặc private method không đi qua proxy.
- Một method có nhiều overload.

Không nên viết một pattern theo tên method rồi giả định nó bao phủ mọi implementation và mọi caller.

### 9.2. Khớp signature sau khi thay

Nếu method là **charge(int)** mà proceed nhận argument kiểu khác không chuyển đổi được, quá trình tiếp tục có thể thất bại trước khi target chạy. Cần test đường thành công và đường sai kiểu.

Nếu có varargs, array parameter, primitive boxing và generic, phải hiểu representation ở API interception. Một reflection API có thể biểu diễn varargs dưới dạng một array ở vị trí cuối; không được tùy tiện trải nó thành nhiều argument riêng.

### 9.3. Hợp đồng nghiệp vụ không được tự tiện đổi

Một method nhận **requestedUnits = 9** nhưng thực thi với **5** sẽ làm caller dễ hiểu sai nếu nó tưởng cả 9 đã được xử lý. Hãy quyết định xem API có cần:

- Trả về số lượng **đã thực xử lý**.
- Báo rõ việc input bị giới hạn.
- Từ chối thay vì âm thầm sửa input.
- Ghi audit về original và effective value.
- Bảo đảm downstream không tính phí 9 cho một thao tác chỉ xử lý 5.

Với thay đổi chính sách nghiệp vụ, nên cân nhắc đặt normalization/validation vào API hoặc một component tường minh nếu tính minh bạch quan trọng. AOP thích hợp khi quy tắc thực sự là concern xuyên cắt và việc tự động áp dụng có lợi ích rõ.

### 9.4. Không làm hỏng invariant của đối tượng

Thay argument reference bằng một object thuộc subtype hợp lệ về compile type chưa chắc đáp ứng mọi invariant mà target trông đợi, ví dụ object phải được tạo từ một factory có kiểm tra. AOP không làm biến mất những ràng buộc này.

## 10. Hợp đồng kết quả: caller thấy gì?

Hình dung ba giá trị:

~~~text
Caller đưa input A
     │
     ▼
Advice đổi thành A'
     │
     ▼
Target tính R = f(A')
     │
     ▼
Advice đổi thành R' = g(R)
     │
     ▼
Caller nhận R'
~~~

Nếu không có các thay đổi bổ sung, caller quan sát hàm mới **g(f(transform(A)))**, khác với **f(A)** của code gốc. Đó là sự biến đổi có thể chứng minh bằng test.

Một số tình huống cần làm rõ:

1. **Method void:** advice có thể chạy trước/sau và ngăn target trong mô hình hỗ trợ, nhưng không thể trả một “giá trị thành công” kiểu int thay cho method void theo contract thông thường.
2. **Primitive result:** phải tương thích primitive, tránh null và sai boxing.
3. **Reference result:** phải bảo đảm object phù hợp loại mà caller mong đợi và invariant của API.
4. **Generic result:** compiler/bytecode có thể dùng erasure; kết quả giả có thể thất bại khi caller cast hoặc sử dụng.
5. **Exception:** nếu target throw, không có R bình thường để biến đổi; muốn fallback phải chủ động bắt loại lỗi tương ứng.
6. **Async result:** nếu method trả CompletionStage, giá trị ở biên hiện tại là một stage; kết quả nghiệp vụ sau này có thể chỉ xuất hiện khi stage hoàn tất. Phải quyết định biến đổi wrapper hay payload bất đồng bộ.

### 10.1. Bất đồng bộ: biến đổi kết quả ở đúng thời điểm

Ví dụ một API trả CompletionStage<Integer>. Around advice gọi target và nhận stage. Việc cộng 2 trực tiếp vào stage là không có nghĩa; nếu muốn biến đổi payload, có thể cần dùng một phép ánh xạ bất đồng bộ tương tự **thenApply**, hoặc một khả năng được API interception cung cấp.

Điều này thay đổi các cân nhắc khác: exception bất đồng bộ được truyền qua stage, cancellation có thể ảnh hưởng pipeline và context của request có thể không tự đi qua thread khác.

Nếu mục đích chỉ là logging, before/after ở thời điểm method trả stage có thể ghi “đã tạo tác vụ” chứ không phải “tác vụ đã hoàn tất”.

## 11. Khi nhiều around advice cùng bao lấy join point

Giả sử có hai aspect:

- **Normalize:** đổi A thành A'.
- **Fee:** cộng 2 vào kết quả target.

Nếu thứ tự lồng nhau là:

~~~text
Fee(Normalize(Core))
~~~

Thì fee nhận kết quả sau khi Normalize đã thực hiện lời gọi tiếp tục. Nếu thứ tự đảo ngược và các advice cùng can thiệp argument/return, kết quả có thể khác tùy cách triển khai.

Ví dụ cụ thể với toán học:

~~~text
Core(x) = x * 10
Normalize(x) = min(x, 5)
DoubleResult(r) = r * 2
AddTwo(r) = r + 2

AddTwo(DoubleResult(Core(5))) = 102
DoubleResult(AddTwo(Core(5))) = 104
~~~

Thứ tự có thể làm thay đổi giá trị nghiệp vụ, nên phải xác định **precedence** khi nhiều aspect chọn cùng join point. Không được suy thứ tự từ tên file, thứ tự class được tải hoặc vị trí được liệt kê trừ khi công cụ chính thức định nghĩa như vậy.

Nếu around bên ngoài bỏ qua proceed, around bên trong cùng target có thể không chạy. Nếu around bên ngoài gọi proceed hai lần, around bên trong có thể chạy hai lần. Vì thế việc đếm invocation chỉ ở một advice có thể khác số lần caller gọi vào API.

## 12. Ngoại lệ và tính đúng đắn khi đổi kết quả

Một around advice có ba nguồn lỗi:

1. **Lỗi do chính advice tạo ra**, ví dụ validate input âm và throw.
2. **Lỗi do target/proceed tạo ra**, như timeout, lỗi dữ liệu hoặc exception nghiệp vụ.
3. **Lỗi khi biến đổi output**, ví dụ overflow, cast sai hoặc ghi nhận dữ liệu sau xử lý thất bại.

Khi một trong ba lỗi xảy ra, phải quyết định về hợp đồng:

- Lỗi nào được truyền nguyên đến caller?
- Lỗi nào được chuyển thành lỗi khác có chủ ý?
- Có được trả fallback không?
- Có cần ghi event “đã thử”, “đã hoàn tất” hay “thất bại”?
- Khi retry, side effect đã xảy ra có thể lặp không?

### 12.1. Không nuốt lỗi rộng

Giả mã nguy hiểm:

~~~text
try:
    return proceed()
catch any error:
    return defaultValue
~~~

Nếu lỗi phát sinh từ vi phạm invariant hoặc lỗi lập trình, trả default có thể biến sự cố nghiêm trọng thành dữ liệu tưởng như hợp lệ. Chỉ xử lý những lỗi được policy cho phép; giữ metadata lỗi khi cần và không vô ý xóa dấu vết thất bại.

### 12.2. Checked exceptions và reflection

Khi dùng API reflection/proxy, exception từ target có thể được bọc. Nếu xử lý không đúng, caller có thể nhận exception khác với chữ ký method đã khai báo. Một around advice có kiểu trả về đúng nhưng exception không đúng vẫn có thể phá hợp đồng API.

### 12.3. Transaction và tính nguyên tử

Biến đổi tham số hoặc return có thể ảnh hưởng tính nhất quán khi target đã ghi dữ liệu xuống kho. Nếu advice đổi R sau khi target commit, caller có thể tưởng đã ghi một giá trị khác với thực tế. Nếu quanh một thao tác có transaction boundary, hãy xác định rõ advice thực thi **trước, trong hay sau** biên transaction theo thứ tự wrapping; không dựa vào hình thức tên advice.

## 13. Trường hợp dùng hợp lý và trường hợp cần thận trọng

| Nhu cầu | Khả năng around có thể dùng | Rủi ro phải quản lý |
|---|---|---|
| **Chuẩn hóa đầu vào kỹ thuật** | Đổi A thành A' ở điểm chung | Caller có biết giá trị hiệu lực không; mutation ngoài ý định |
| **Cache ở API đọc** | Trả cache hit mà không gọi target | Freshness, authorization, invalidation, side effects |
| **Fallback được định nghĩa** | Bắt một loại lỗi đã cho và trả R' | Che mất lỗi nghiêm trọng hoặc báo thành công giả |
| **Retry hữu hạn** | Gọi tiếp nhiều lần khi lỗi tạm thời | Idempotency, duplicate side effects, timeout |
| **Thay đổi representation của output** | Chuyển một value object sang format yêu cầu | Kiểu dữ liệu, precision, invariant, compatibility |
| **Redaction ở biên xuất dữ liệu** | Bỏ nội dung nhạy cảm khỏi output | Đủ coverage, không mất dữ liệu cần thiết |
| **Trả kết quả giả lập trong test** | Không gọi target trên một đường kiểm thử | Test có đại diện production behavior không |
| **Tính phí/quy tắc miền** | Về kỹ thuật có thể thay input/output | Phụ thuộc ẩn, double charge, sai trạng thái; thường cần contract rõ trong tầng miền |

Nên áp dụng around khi **khả năng điều phối thực sự cần thiết**. Nếu chỉ muốn log thời điểm bắt đầu thì before đã đủ về mặt ý nghĩa; nếu chỉ muốn ghi lỗi sau khi target throw thì after-throwing thường dễ hiểu hơn trong mô hình có nó.

## 14. Cơ chế weaving quyết định giới hạn thay dữ liệu

### 14.1. Compile-time hoặc load-time weaving

Một bộ dệt có thể sửa bytecode tại method call/execution hoặc loại join point khác mà nó hỗ trợ. Việc điều chỉnh dữ liệu phải tuân theo API và luật binding/proceed của ngôn ngữ AOP.

Có trường hợp một lời gọi nội bộ trên cùng object vẫn được weave nếu bộ dệt instrument trực tiếp execution; điều đó không tự đúng ở tất cả cơ chế.

### 14.2. Runtime proxy/interception

Một proxy chỉ áp dụng khi caller thực sự gọi qua proxy. Nếu code gọi target gốc, interceptor không hoạt động trên đường đó.

Một JDK dynamic proxy dựa vào interface, handler nhận đối số theo representation của reflective invocation. Vì thế có thể tạo đối số mới cho method.invoke, nhưng không được suy rằng nó chặn static/private/field access/constructor ở toàn ứng dụng.

### 14.3. Loại join point và quan hệ đến proceed

Method-call, method-execution, constructor, field-access có hợp đồng tiếp tục khác nhau. Ví dụ ghi giá trị field mới không có cùng return/argument contract như gọi method trả int. Nếu công cụ không hỗ trợ thay giá trị của một loại join point, không được dùng cú pháp của loại khác rồi mong nó có tác dụng.

## 15. Những lỗi thường gặp

**Lỗi 1 — Chỉ gán lại biến local rồi tưởng target nhận tham số mới.** Phải dùng cơ chế proceed/invoke nhận dữ liệu đã biến đổi, hoặc thừa nhận rằng chỉ có object mutable bị sửa side effect.

**Lỗi 2 — Thay đầu vào mà không kiểm tra signature.** Null cho primitive, sai thứ tự argument, nhầm overload hoặc kiểu không tương thích có thể gây exception runtime.

**Lỗi 3 — Đổi kết quả không tương thích return type.** Trả String cho method int hoặc null cho int làm hỏng hợp đồng.

**Lỗi 4 — Cộng phí hai lần vì nhiều advice trùng nhau.** Khi policy chạy ở nhiều biên, caller có thể nhận giá trị vượt kỳ vọng; phải chọn một chủ sở hữu concern và kiểm tra số lượt.

**Lỗi 5 — Bỏ qua target khi target có side effect bắt buộc.** Cache/short-circuit chỉ an toàn khi contract cho phép.

**Lỗi 6 — Retry thao tác không idempotent.** Một lần caller gọi có thể khiến target chạy nhiều lần; cần cơ chế chống trùng, giới hạn số lần và nhận diện lỗi có thể thử lại.

**Lỗi 7 — Nuốt mọi ngoại lệ rồi trả fallback.** Làm sai semantics thất bại và gây khó chẩn đoán.

**Lỗi 8 — Nhầm method trả Future với đã hoàn tất nghiệp vụ.** Giá trị tại return boundary có thể là một tác vụ còn chạy.

**Lỗi 9 — Không kiểm tra lời gọi trực tiếp bỏ qua proxy.** Một pointcut hay interceptor đúng ở biên này không chứng minh đã bao phủ mọi đường gọi khác.

**Lỗi 10 — Lạm dụng around cho mọi concern.** Quyền điều phối tăng độ khó đọc/kiểm thử; trước hoặc sau đơn giản có thể phù hợp hơn.

**Lỗi 11 — Dựa vào thứ tự aspect không được định nghĩa.** Khi hai advice đều đổi return, kết quả có thể phụ thuộc thứ tự wrapping; cần quy định precedence và kiểm thử.

**Lỗi 12 — Làm biến đổi dữ liệu nhạy cảm mà không kiểm soát.** Logging original input có thể làm lộ bí mật; biến đổi/redact phải có policy, coverage và audit rõ ràng.

## 16. Quy trình thiết kế an toàn một around advice thay dữ liệu

**Bước 1 — Viết hợp đồng gốc.** Method nhận input nào, trả output gì, ngoại lệ nào được phép, có side effect nào không?

**Bước 2 — Định nghĩa phép biến đổi.** Ví dụ: input bị giới hạn tại 5; output cộng 2; phải nêu rõ dữ liệu thay đổi có ý nghĩa gì.

**Bước 3 — Xác định loại join point.** Method call hay execution? Đối tượng nào thực thi? Phạm vi của policy gồm những caller và implementation nào?

**Bước 4 — Xác minh khả năng engine.** Có thể bind arguments không? Có thể gọi proceed với giá trị mới không? Return type và exception được kiểm tra ở đâu?

**Bước 5 — Định nghĩa số lần gọi tiếp tục.** Mặc định đúng một lần nếu chỉ transform. Nếu skip/retry, phải có điều kiện rõ.

**Bước 6 — Đảm bảo tương thích kiểu.** Primitive, reference, generic, varargs, null, overload và return type phải được kiểm thử.

**Bước 7 — Xử lý lỗi cụ thể.** Phân biệt lỗi advice, lỗi target và lỗi transform output.

**Bước 8 — Kiểm tra thứ tự nhiều aspect.** Dùng precedence hợp lệ nếu kết quả phụ thuộc lớp bọc.

**Bước 9 — Kiểm tra concurrency và dữ liệu.** Advice có trạng thái dùng chung không? Có mutate input object được caller giữ không? Có log bí mật không?

**Bước 10 — Kiểm chứng đường thật.** Test qua đúng proxy/weaving, cả ca khớp và không khớp pointcut, các đường bypass và self-invocation nếu có.

## 17. Ma trận kiểm thử đề xuất

| Kịch bản | Điều cần chứng minh |
|---|---|
| Input bình thường | Target nhận argument đúng; caller nhận result đúng |
| Input cần normalize | Target thật sự nhận giá trị mới, không chỉ log giá trị mới |
| Input bị từ chối | Target không được gọi, exception truyền đúng |
| Chính sách skip | Target không có side effect, return hợp lệ |
| Target throw | Advice không vô tình đổi lỗi thành thành công |
| Output transformation | Giá trị trả về kiểu đúng, không double transform |
| Primitive/null/generic | Không có lỗi boxing/cast ngoài dự kiến |
| Concurrent calls | Không rò dữ liệu giữa request, counter chính xác |
| Around advice lồng nhau | Thứ tự rõ và phép biến đổi đúng |
| Retry | Số lần target, tính idempotent và giới hạn retry đúng |
| Caller giữ target gốc | Biết chính xác khi nào interceptor bị bỏ qua |
| Lời gọi nội bộ | Xác minh giới hạn proxy hoặc mức bao phủ bytecode weaving |
| Asynchronous return | Biến đổi payload tại đúng thời điểm và bảo toàn lỗi |
| Pointcut không khớp | Không có biến đổi ngoài phạm vi |

Một assertion chỉ kiểm tra result chưa đủ. Ví dụ result = 52 có thể xuất phát từ việc target nhận 5 và fee +2; cũng có thể xuất phát từ một đường tính hoàn toàn khác. Nên ghi nhận **argument target thực sự nhận**, **số lần target chạy**, **giá trị target trả**, **giá trị caller nhận** và **exception**.

## 18. Bài tập vận dụng

**Bài 1.** Caller truyền 9. Advice tạo biến local effective = 5 nhưng gọi proceed không truyền giá trị mới trong mô hình yêu cầu proceed(args). Target nhận 9 hay 5?

**Đáp:** Target vẫn có thể nhận 9. Muốn target nhận 5 phải chuyển 5 đúng cách vào đường tiếp tục.

**Bài 2.** Target trả 50, around advice cộng 2. Một around bên ngoài nhân đôi kết quả. Tại sao có thể có 104 thay vì 102?

**Đáp:** Thứ tự wrapping khác nhau: (50 + 2) × 2 = 104, còn (50 × 2) + 2 = 102.

**Bài 3.** Advice không gọi proceed và trả cache value. Có chắc hành vi tương đương target không?

**Đáp:** Không. Nếu target có side effect, kiểm soát quyền hoặc dữ liệu đã đổi, short-circuit có thể phá hợp đồng.

**Bài 4.** Advice bắt mọi exception và trả 0. Điều gì nguy hiểm?

**Đáp:** Caller không biết thao tác thật đã thất bại; lỗi lập trình hoặc thất bại nghiêm trọng có thể bị che giấu như kết quả hợp lệ.

**Bài 5.** Method trả CompletionStage<Integer>. Around advice muốn cộng 2 vào giá trị trả thật. Có thể chỉ cast stage thành int rồi cộng không?

**Đáp:** Không. Phải biến đổi kết quả của stage theo cơ chế bất đồng bộ phù hợp và bảo toàn exception/cancellation.

**Bài 6.** Một aspect được triển khai bằng JDK dynamic proxy. Caller gọi target trực tiếp. Pointcut của proxy có đổi tham số trên đường đó không?

**Đáp:** Không. Đường gọi trực tiếp không qua interceptor của proxy.

**Bài 7.** Một trước advice mutate List và một around advice thay argument bằng List mới. Hai hành động có tác dụng như nhau với caller không?

**Đáp:** Không nhất thiết. Mutate List cũ có thể ảnh hưởng object caller giữ; thay reference trong proceed có thể dùng một List khác và giữ bản cũ không đổi.

**Bài 8.** Một around advice gọi proceed hai lần vì retry. Metric đếm target calls là 2 nhưng caller calls là 1. Có nhất thiết là bug?

**Đáp:** Không; đó có thể là kết quả đúng của retry, nhưng chỉ an toàn nếu policy cho phép và thao tác có biện pháp kiểm soát side effect/trùng lặp.

## 19. Sơ đồ tổng kết cơ chế

~~~text
                    POINTCUT MATCH?
                  ┌─────────┴─────────┐
                 Không                Có
                  │                   │
                  ▼                   ▼
              Chạy gốc          AROUND ADVICE
                                     │
                             nhận args gốc A
                                     │
                    ┌────────────────┼────────────────┐
                    ▼                ▼                ▼
                  Throw             Skip          Transform A→A'
                    │                │                │
                    ▼                ▼                ▼
                  Lỗi             Return R'       Proceed(A')
                                                      │
                                                      ▼
                                               Target trả R
                                                      │
                                                      ▼
                                               Transform R→R'
                                                      │
                                                      ▼
                                                  Return R'
~~~

**Nguyên tắc cốt lõi:** ability to transform data belongs to the **around interception contract**, not merely to the fact that a variable named argument or result exists. Muốn thay dữ liệu một cách đúng đắn, cần biết **pointcut khớp ở đâu, engine có hỗ trợ proceed với dữ liệu mới không, target chạy bao nhiêu lần, hợp đồng kiểu và nghiệp vụ có còn đúng không, và caller thực sự thấy kết quả nào**. Nếu không có yêu cầu thay đổi hành vi, hãy giữ dữ liệu và lỗi gốc.

## Tài liệu đối chiếu

- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): ngữ nghĩa join point, advice, around và proceed trong một ngôn ngữ AOP có weaving.
- [Java Language Specification, Java SE 17 — Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html): truyền đối số, method invocation, đánh giá biểu thức và kiểu kết quả.
- [Java SE 17 — java.lang.reflect.Method](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): API phản xạ dùng để gọi target trong ví dụ Java.
- [Java SE 17 — java.lang.reflect.InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html): cơ chế chặn lời gọi bằng JDK dynamic proxy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-005">Gọi tiếp nhiều lần và nguy cơ lặp tác dụng phụ</a>

<details>
<summary>Xem chi tiết</summary>

# Gọi tiếp nhiều lần và nguy cơ lặp tác dụng phụ trong around advice (AOP)

**Around advice** là dạng hành vi bao quanh một điểm can thiệp (*join point*) trong lập trình hướng khía cạnh (Aspect-Oriented Programming, **AOP**). Nó có thể thực hiện việc gì đó **trước**, điều phối việc **tiếp tục chuỗi thực thi**, rồi xử lý **kết quả hoặc lỗi** sau đó. Trong nhiều mô hình AOP, thao tác tiếp tục được gọi là **`proceed`**. Năng lực đặc biệt — và cũng là nguồn rủi ro — là một số cơ chế cho phép around advice **không tiếp tục, tiếp tục một lần hoặc tiếp tục nhiều lần**.

Với advice chỉ để đo thời gian hoặc ghi log, **tiếp tục đúng một lần** thường là cách bảo toàn hợp đồng tự nhiên nhất. Khi chủ động tiếp tục nhiều lần để thử lại (*retry*), chương trình có thể **thực thi nghiệp vụ nhiều lần**: gửi hai email, tạo hai bản ghi, trừ tiền hai lần hoặc phát hai sự kiện. Việc lần đầu báo lỗi/timeout **không chứng minh** lần đầu chưa tạo tác dụng phụ.

Bài học này tập trung vào lý do và cơ chế lặp tác dụng phụ, sự khác biệt giữa các loại hành vi, retry an toàn, idempotency, thứ tự các aspect và cách kiểm thử. Những khả năng của `proceed` phụ thuộc mô hình AOP và bộ weaving đang dùng: **không nên mặc định mọi hệ AOP đều hỗ trợ gọi tiếp nhiều lần, thay đối số hoặc bỏ qua điểm nối**. Ví dụ Java 17 sử dụng JDK dynamic proxy để mô phỏng rõ hai lần gọi tiếp; nó **không phải** một bộ weaving bytecode tổng quát.

## 1. Mục tiêu học tập

Sau bài học, bạn có thể:

- Giải thích rõ ý nghĩa của `proceed` trong chuỗi around advice và phân biệt nó với lệnh `return`, một lời gọi phương thức thông thường hay vòng lặp tùy ý.
- Xác định những tình huống **0 lần, 1 lần, nhiều lần** tiếp tục và hệ quả đối với kết quả/ngoại lệ.
- Phân biệt **tác dụng phụ (side effect)**, **độ an toàn khi retry**, **idempotency**, **at-most-once**, **at-least-once** và mục tiêu “một hiệu ứng nghiệp vụ”.
- Phân tích vì sao timeout/đứt kết nối có thể đến **sau khi thao tác bên ngoài đã hoàn tất**.
- Thiết kế retry có giới hạn số lần, ngân sách thời gian, bộ lọc lỗi, backoff và khóa chống trùng.
- Phân tích thứ tự `Retry(Logging(Task))` với `Logging(Retry(Task))`.
- Kiểm tra advice không vô tình nhân đôi thao tác chính, kết quả, exception, tài nguyên hay thông tin kiểm toán.
- Nhận biết giới hạn của proxy/interceptor so với weaving thực sự ở các điểm can thiệp khác.

## 2. Proceed là “tiếp tục chuỗi bên trong”, không phải bảo đảm một lần

Hình dung chương trình sau khi kết hợp aspect:

```text
Người gọi
   |
   v
Around A
   |
   +-- before A
   |
   +-- proceed() ------> Around B
   |                         |
   |                         +-- before B
   |                         +-- proceed() ---> Target
   |                         +-- after B
   |
   +-- after A
   |
   v
Người gọi nhận kết quả / ngoại lệ
```

Nếu A gọi `proceed()`, nó thường tiếp tục **chuỗi còn lại** dưới A: có thể là một advice B khác rồi mới đến mã nghiệp vụ đích. Do đó “gọi `proceed` hai lần” không chỉ có khả năng gọi đích hai lần; nó còn có thể chạy **các advice bên trong hai lần**.

Ở một số công nghệ, lời gọi tiếp tục có thể được phép nhiều lần. Ở công nghệ khác, cơ chế có thể hạn chế số lần, hình thức hoặc ngữ nghĩa `proceed`. Người học phải tra hợp đồng của hệ AOP cụ thể; không nên viết một vòng retry nếu framework/weaver không cho phép việc gọi tiếp tương ứng.

### 2.1. Ba lựa chọn cơ bản

| Hành vi của around advice | Luồng thực thi | Tác động dễ gặp |
| --- | --- | --- |
| **0 lần tiếp tục** | Advice tự trả kết quả, chặn hoặc ném lỗi | Đích có thể không chạy, như cache hit hoặc kiểm soát quyền từ chối |
| **1 lần tiếp tục** | Advice chuyển tiếp vào chuỗi bên trong một lần | Dễ bảo toàn hành vi cũ khi chỉ quan sát, nếu không đổi dữ liệu/lỗi |
| **Nhiều lần tiếp tục** | Advice chạy lại chuỗi bên trong | Có thể lặp nghiệp vụ, advice bên trong, log, sự kiện và giao dịch |

Ngay cả trường hợp **một lần** cũng không tuyệt đối bảo đảm chỉ có một tác dụng phụ: phương thức đích có thể tự phát nhiều sự kiện, gọi dịch vụ khác nhiều lần hoặc có retry nằm ở tầng bên trong. Ngược lại, hai lần tiếp tục không nhất thiết tạo hai hiệu ứng nếu nghiệp vụ được thiết kế chống trùng đúng nghĩa. Số lần gọi tiếp và số hiệu ứng nghiệp vụ là **hai đại lượng khác nhau**.

### 2.2. Around advice không chỉ có retry

Around advice còn được dùng để đo thời gian, quản lý bối cảnh, cache, phân quyền, kiểm soát tài nguyên hoặc thay kết quả khi hợp đồng cho phép. Những chính sách đó có đặc điểm khác nhau:

- **Logging/metrics:** thường phải bảo toàn đường thực thi và chỉ gọi tiếp một lần.
- **Cache:** có thể không gọi tiếp khi đã có dữ liệu hợp lệ; cần xác định cache hit có được phép bỏ qua thao tác gốc không.
- **Authorization:** có thể chặn trước khi tiếp tục khi chính sách không cho phép.
- **Retry:** có chủ đích gọi tiếp nhiều lần sau một số lỗi/điều kiện cụ thể.
- **Fallback:** có thể chuyển sang thao tác khác; không mặc định được quyền thay lỗi bằng kết quả khác.
- **Transaction/context:** bao quanh vòng đời, đòi hỏi biết phạm vi và thời điểm dọn dẹp.

Không nên suy luận từ việc mọi tình huống đều dùng around advice rằng chúng có cùng tính an toàn.

## 3. Tác dụng phụ là gì?

**Tác dụng phụ** là thay đổi có thể quan sát được ngoài giá trị trả về trực tiếp của một phép tính. Ví dụ:

| Thao tác | Tác dụng phụ có thể xảy ra | Nếu thực thi hai lần |
| --- | --- | --- |
| Tính `price * quantity` trên dữ liệu bất biến | Thường không có tác dụng phụ bên ngoài | Thường chỉ tốn CPU thêm |
| Đọc một bản ghi | Có thể ghi số lần đọc, ảnh hưởng cache hoặc log | Có thể tăng chi phí và thay thống kê |
| `inventory.decrease(1)` | Giảm tồn kho | Có thể giảm hai đơn vị |
| `account.debit(amount)` | Trừ số dư | Có thể trừ hai lần |
| `email.send(message)` | Gửi thư | Có thể phát hai thư |
| `eventBus.publish(event)` | Công bố sự kiện | Người nhận có thể xử lý lặp |
| `file.append(line)` | Thêm dòng vào tệp | Có thể ghi dòng lặp |
| `insertOrder(request)` | Ghi bản ghi mới | Có thể tạo hai bản ghi nếu không có ràng buộc chống trùng |

Một hàm được gọi là “đọc” cũng không chắc **thuần túy**: nó có thể đếm lượt xem, refresh token, khởi tạo lazy object hoặc gọi API theo dõi. Khi quyết định cho phép `proceed` nhiều lần, cần đánh giá toàn bộ **chuỗi tác dụng phụ phía trong**, không chỉ tên phương thức.

### 3.1. Thao tác thuần túy và thao tác không thuần túy

Một phép tính thuần túy (*pure*) với cùng đầu vào thường trả cùng kết quả và không có tác dụng phụ có thể quan sát được. Lặp phép tính ấy chủ yếu làm tăng chi phí.

Ngược lại, thao tác “tạo đơn”, “gửi tiền”, “đánh dấu đã giao” có thể làm thay đổi trạng thái hệ thống. Gọi lại cùng tham số không đủ chứng minh kết quả và trạng thái cuối sẽ tương đương với một lần gọi.

```text
Pure:          f(x) -> y                  (không đổi trạng thái ngoài)
Side-effect:   g(x) -> y + thay đổi S      (trạng thái ngoài bị tác động)
```

Đó là mô hình giải thích; tính thuần túy thực tế phụ thuộc hiện thực và các phụ thuộc được gọi, không chỉ chữ ký Java.

## 4. Khi nào hai lần gọi tiếp tạo lỗi nghiệp vụ?

Giả sử một around advice thử tối đa hai lần:

```text
try:
    return proceed()
catch transientError:
    return proceed()
```

Đây là **giả mã**, không phải cú pháp một công nghệ AOP cụ thể. Nếu `proceed` lần đầu thực sự tạo đơn hàng rồi lỗi khi xây dựng phản hồi, lần thứ hai có thể tạo **thêm đơn**. Việc code retry trông rất ngắn che giấu hai hoạt động nghiệp vụ thật sự.

### 4.1. Bốn thời điểm lỗi khác nhau

Với tác vụ gửi yêu cầu đến hệ thống bên ngoài:

```text
T0: Chưa gửi yêu cầu
T1: Đã gửi, bên nhận chưa xử lý
T2: Bên nhận đã thực hiện tác dụng phụ
T3: Phản hồi đã về tới người gọi
```

Một timeout có thể xuất hiện do mạng đứt giữa T1 và T2, hoặc **sau T2 nhưng trước T3**. Trong tình huống sau, phía gọi thấy “timeout” nhưng phía nhận **đã làm xong**. Đây là dạng kết quả **không chắc chắn** (*ambiguous outcome*).

Nếu retry ngay lập tức với một yêu cầu mới không nhận diện được trùng lặp, hệ thống bên nhận có thể làm lại. Vấn đề không phải thiếu `catch`, mà là **không biết hiệu ứng đã xảy ra hay chưa**.

### 4.2. Ngoại lệ không tự chứng minh rollback

Một thao tác có thể:

1. Ghi dữ liệu thành công.
2. Gửi sự kiện thành công.
3. Thất bại khi ghi log, đóng kết nối hoặc trả phản hồi.
4. Ném ngoại lệ ra ngoài.

Around advice bắt được ngoại lệ ở bước 4 nhưng không thể kết luận các bước 1–2 chưa diễn ra. Java exception là tín hiệu về **luồng điều khiển của lời gọi quan sát**, không phải biên bản trạng thái của mọi hệ thống bên ngoài.

Vì vậy một chính sách retry phải xét **tính an toàn của thao tác**. Không retry mù quáng mọi `RuntimeException`, `IOException` hay timeout.

## 5. Idempotency: lặp lời gọi nhưng không lặp hiệu ứng đã cam kết

Một thao tác **idempotent** theo nghĩa nghiệp vụ được thiết kế để lặp cùng **yêu cầu logic** mà không tạo thêm hiệu ứng cần tránh. Cách lý tưởng là sau nhiều lần xử lý cùng yêu cầu, trạng thái nghiệp vụ cuối giống với sau một lần xử lý phù hợp.

```text
apply(request) lần 1    -> tạo một hiệu ứng
apply(request) lần 2    -> nhận ra yêu cầu đã xử lý, không tạo hiệu ứng thứ hai
apply(request) lần N    -> vẫn không tạo hiệu ứng mới ngoài hợp đồng
```

Khái niệm này không có nghĩa mọi tín hiệu quan sát đều giống nhau: hệ thống có thể ghi thêm **log các lần thử** nhưng vẫn chỉ có **một khoản thanh toán**.

### 5.1. Idempotency key

Một cách thường gặp là dùng **mã yêu cầu duy nhất** cho một hành động logic:

- Lời gọi đầu sử dụng khóa `REQ-700`.
- Nếu lỗi mạng, lần thử lại phải sử dụng **cùng khóa `REQ-700`**.
- Hệ thống có trách nhiệm thực hiện nghiệp vụ xác định khóa đã hoàn tất hay chưa.
- Nếu hoàn tất, nó trả lại kết quả phù hợp mà không tạo hiệu ứng trùng.
- Nếu vẫn đang xử lý, hệ thống có chính sách rõ cho trạng thái *in progress*, chờ hoặc trả trạng thái để đối soát.

Lưu khóa phải được gắn với danh tính/chủ thể, loại thao tác và nội dung yêu cầu phù hợp. Nếu tái sử dụng cùng khóa nhưng **nội dung khác**, hệ thống nên phát hiện xung đột chứ không âm thầm trả kết quả cũ cho giao dịch mới.

### 5.2. Chỉ lưu khóa trong bộ nhớ chưa đủ cho hệ thống thật

Một `HashMap` trong một tiến trình đơn lẻ không bền khi tiến trình khởi động lại và không tự chia sẻ với các tiến trình khác. Nó cũng không đủ an toàn khi nhiều luồng cùng xử lý một khóa nếu không có phối hợp đồng thời.

Trong hệ thống thật có thể cần:

- Ràng buộc duy nhất hoặc thao tác nguyên tử trên bộ lưu trữ bền vững;
- Ghi trạng thái xử lý cùng hiệu ứng theo ranh giới giao dịch phù hợp;
- Lưu kết quả hoặc trạng thái kết thúc để phục vụ truy vấn/retry;
- Quy tắc xử lý yêu cầu đang chạy, lỗi vĩnh viễn và xung đột payload;
- Cơ chế đối soát nếu hiệu ứng nằm ở hệ thống ngoài, không thể cam kết nguyên tử cùng bộ lưu khóa;
- Chính sách thời hạn lưu khóa đủ dài so với cửa sổ retry và khả năng nhận yêu cầu lặp.

**Idempotency phải được thực hiện ở nơi có quyền kiểm soát hiệu ứng nghiệp vụ**, không chỉ ở around advice phía người gọi.

## 6. Ví dụ Java 17 chạy được: timeout sau khi đã ghi và retry hai lần

Ví dụ dưới đây minh họa điều rất dễ bỏ qua: lần đầu **đã tạo hiệu ứng**, nhưng trước khi nhận phản hồi, người gọi gặp một `ApparentTimeout` giả lập. Around interceptor sẽ thử lại.

Chạy hai phiên bản:

1. **UnsafeCheckout:** không kiểm tra yêu cầu trùng, nên mỗi lần gọi đều tăng số lần ghi nhận giao dịch.
2. **IdempotentCheckout:** nhận ra cùng `requestId`, không tạo giao dịch thứ hai.

Đây là chương trình **mô phỏng trong một tiến trình** với dữ liệu in-memory để thấy đúng luồng điều khiển, **không phải hiện thực idempotency đủ an toàn cho thanh toán thực**.

Lưu toàn bộ mã sau thành tệp `ProceedTwiceDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public class ProceedTwiceDemo {
    interface Checkout {
        String charge(String requestId, int cents);
        int actualCharges();
    }

    static final class ApparentTimeout extends RuntimeException {
        ApparentTimeout() {
            super("response lost after commit");
        }
    }

    static final class UnsafeCheckout implements Checkout {
        private int charges;

        @Override
        public String charge(String requestId, int cents) {
            charges++;
            System.out.println("CORE committed #" + charges);
            if (charges == 1) {
                throw new ApparentTimeout();
            }
            return "RECEIPT-" + charges;
        }

        @Override
        public int actualCharges() {
            return charges;
        }
    }

    static final class IdempotentCheckout implements Checkout {
        private final Map<String, String> receipts = new HashMap<>();
        private int charges;
        private boolean first = true;

        @Override
        public String charge(String requestId, int cents) {
            String known = receipts.get(requestId);
            if (known != null) {
                System.out.println("CORE deduplicated " + requestId);
                return known;
            }

            charges++;
            String receipt = "RECEIPT-" + charges;
            receipts.put(requestId, receipt);
            System.out.println("CORE committed #" + charges);

            if (first) {
                first = false;
                throw new ApparentTimeout();
            }
            return receipt;
        }

        @Override
        public int actualCharges() {
            return charges;
        }
    }

    private static Object invokeTarget(
            Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapper) {
            throw wrapper.getCause();
        }
    }

    static Checkout retryOnce(Checkout target) {
        return (Checkout) Proxy.newProxyInstance(
                Checkout.class.getClassLoader(),
                new Class<?>[]{Checkout.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("charge")) {
                        return invokeTarget(method, target, args);
                    }

                    for (int attempt = 1; attempt <= 2; attempt++) {
                        System.out.println("ADVICE attempt " + attempt);
                        try {
                            return invokeTarget(method, target, args);
                        } catch (ApparentTimeout error) {
                            if (attempt == 2) {
                                throw error;
                            }
                            System.out.println("ADVICE retry after timeout");
                        }
                    }
                    throw new AssertionError("unreachable");
                });
    }

    public static void main(String[] args) {
        System.out.println("UNSAFE");
        Checkout unsafe = retryOnce(new UnsafeCheckout());
        System.out.println("CALLER " + unsafe.charge("REQ-1", 500));
        System.out.println("CHARGES " + unsafe.actualCharges());

        System.out.println("IDEMPOTENT");
        Checkout safe = retryOnce(new IdempotentCheckout());
        System.out.println("CALLER " + safe.charge("REQ-1", 500));
        System.out.println("CHARGES " + safe.actualCharges());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 ProceedTwiceDemo.java
java ProceedTwiceDemo
```

Đầu ra dự kiến chính xác:

```text
UNSAFE
ADVICE attempt 1
CORE committed #1
ADVICE retry after timeout
ADVICE attempt 2
CORE committed #2
CALLER RECEIPT-2
CHARGES 2
IDEMPOTENT
ADVICE attempt 1
CORE committed #1
ADVICE retry after timeout
ADVICE attempt 2
CORE deduplicated REQ-1
CALLER RECEIPT-1
CHARGES 1
```

### 6.1. Giải thích từng đường chạy

**Không chống trùng:**

- Advice in `ADVICE attempt 1`, chuyển tiếp lần đầu.
- `UnsafeCheckout.charge` tăng `charges` lên 1 rồi in `CORE committed #1`. Sau đó nó **cố ý ném timeout giả lập**: lỗi xảy ra sau khi “ghi” đã thực hiện.
- Advice bắt đúng `ApparentTimeout`, in dòng retry và chuyển tiếp **lần thứ hai**.
- Đích tăng `charges` từ 1 lên 2: có **hai hiệu ứng**, dù người gọi chỉ gọi `unsafe.charge(...)` một lần.
- Người gọi nhận `RECEIPT-2`, nhưng đây **không có nghĩa chỉ có một giao dịch**; dòng `CHARGES 2` cho thấy rủi ro.

**Có chống trùng trong phạm vi minh họa:**

- Lần đầu ghi kết quả cho khóa `REQ-1` trước khi ném timeout.
- Lần thử thứ hai giữ **nguyên khóa**, phát hiện `receipts` đã có `REQ-1`.
- Thay vì tăng `charges`, đích in `CORE deduplicated REQ-1` rồi trả lại `RECEIPT-1`.
- Dòng `CHARGES 1` minh họa “hai lần gọi nhưng một hiệu ứng”. Đây là đúng trong **chương trình tuần tự in-memory này**, không phải bằng chứng hoạt động bền vững hoặc an toàn đồng thời của một hệ thống thanh toán.

### 6.2. Vì sao ví dụ vẫn được xem là minh họa around advice?

`retryOnce` bao quanh lời gọi `Checkout.charge` và quyết định **khi nào, bao nhiêu lần** chuyển tiếp vào đối tượng đích. Nó tương tự một around advice có thể gọi `proceed` nhiều lần.

Tuy nhiên, `invokeTarget` ở đây dùng reflection và JDK dynamic proxy, **không phải từ khóa `proceed` của một công nghệ AOP chuyên dụng**. Proxy chỉ chặn những lời gọi đi qua giao diện được đại diện; nếu gọi `new UnsafeCheckout().charge(...)` trực tiếp, logic retry của proxy không chạy.

### 6.3. Điều gì là nguyên nhân chứ không phải triệu chứng?

- **Triệu chứng:** `CHARGES 2` xuất hiện sau một yêu cầu của người gọi.
- **Nguyên nhân trực tiếp:** interceptor chuyển tiếp vào thao tác đích hai lần.
- **Nguyên nhân rủi ro:** đích tạo hiệu ứng không idempotent khi gọi lại cùng yêu cầu logic.
- **Yếu tố kích hoạt:** timeout không phân biệt hiệu ứng đã hoàn tất với hiệu ứng chưa xảy ra.
- **Biện pháp:** quy tắc retry có điều kiện và cơ chế idempotency ở thành phần thực sự sở hữu hiệu ứng.

Chỉ ghi log “attempt=2” giúp quan sát, nhưng **không loại bỏ** lỗi nghiệp vụ.

### 6.4. Giới hạn có chủ đích của code minh họa

- `ApparentTimeout` là lỗi giả lập, không phát sinh từ thư viện mạng.
- Không có cơ sở dữ liệu, khóa giao dịch, kết nối thanh toán hoặc tác vụ thực.
- `HashMap` không chống trùng an toàn khi có hai luồng cập nhật đồng thời hoặc hai tiến trình.
- `IdempotentCheckout` không kiểm tra payload khác nhau sử dụng chung `requestId`; hệ thống thật cần phát hiện xung đột nội dung.
- Không có xử lý trạng thái `IN_PROGRESS`, thời hạn khóa, retry ngân sách thời gian hoặc chiến lược đối soát.
- Mã mẫu bắt **duy nhất `ApparentTimeout`** để retry, thay vì bắt mọi lỗi. Điều này minh họa việc **chọn nhóm lỗi** có chủ ý.
- Kết quả `actualCharges` được gọi qua proxy nhưng không thuộc điều kiện retry, do đó không có `ADVICE attempt` cho phương thức này.
- Dữ liệu `cents` không được dùng để chứng minh tính đúng của giao dịch; mục đích là phân tích **số lần hiệu ứng xảy ra**, không xây dựng ứng dụng tài chính.

## 7. Idempotent không có nghĩa “an toàn để retry mọi lỗi”

Đây là một nhầm lẫn dễ gặp. Idempotency giải quyết **tính lặp của cùng yêu cầu logic**, nhưng retry vẫn cần điều kiện và giới hạn.

### 7.1. Phân loại lỗi trước khi thử lại

| Loại tình huống | Gợi ý đánh giá |
| --- | --- |
| Lỗi kết nối thoáng qua trước khi yêu cầu chắc chắn được gửi | Có thể cân nhắc retry nếu hợp đồng cho phép |
| Timeout nhưng không biết máy chủ đã làm xong chưa | Retry chỉ khi có cơ chế phát hiện trùng hoặc đối soát phù hợp |
| Máy chủ báo quá tải tạm thời | Có thể cân nhắc backoff/jitter và giới hạn ngân sách |
| Dữ liệu sai định dạng, vi phạm bất biến | Retry cùng dữ liệu thường không giúp |
| Không có quyền truy cập | Không retry mù quáng để vượt qua chính sách |
| Xung đột idempotency key với payload khác | Cần báo xung đột/đối soát, không tạo hiệu ứng mới |
| Tác vụ đã xác nhận hoàn tất | Thường nên trả trạng thái/kết quả cũ thay vì thực hiện lại |
| Trạng thái xử lý chưa rõ | Có thể cần truy vấn trạng thái hoặc cơ chế hòa giải |

Một ngoại lệ Java đơn lẻ có thể đại diện cho nhiều trạng thái thực tế; phải đọc hợp đồng của dịch vụ và loại hoạt động chứ không chỉ so sánh tên lớp exception.

### 7.2. Retry phải hữu hạn

Một vòng retry không giới hạn có thể chiếm luồng, liên tục tạo tải cho hệ thống đang gặp sự cố và tăng cơ hội lặp hiệu ứng.

Các tham số thường cần quy định rõ:

- **Số lần thử tối đa** (tính gồm lần đầu hay không phải được định nghĩa nhất quán);
- **Tổng thời gian cho phép** (deadline/budget), không chỉ số lần;
- **Khoảng chờ** có thể tăng dần (*exponential backoff*);
- **Jitter** để tránh nhiều client retry đồng loạt;
- **Điều kiện dừng** trên lỗi vĩnh viễn, hủy tác vụ hoặc cạn ngân sách;
- **Điều kiện an toàn khi lặp tác dụng phụ**, bao gồm khóa idempotency;
- **Điều kiện ghi nhận kết quả** khi cuối cùng vẫn không chắc trạng thái hiệu ứng.

Với `attempt <= 2` trong chương trình mẫu, ta thực hiện **tối đa hai lần thử tổng cộng**: lần gốc và một lần thử lại.

### 7.3. Retry có thể làm tăng tải theo cấp số nhân

Giả sử một lời gọi bên ngoài có tối đa 3 lần thử và thao tác bên trong cũng có tối đa 3 lần. Trong trường hợp mọi tầng đều retry các lần thất bại phù hợp, số lần thực thi ở đích **có thể tới 9**, không phải 3.

Nếu một chuỗi dịch vụ có nhiều tầng đều retry, tải tăng rất nhanh vào đúng lúc hạ tầng đang yếu. Cần quyết định tầng nào chịu trách nhiệm retry và chia sẻ ngân sách chung; đừng đặt retry ở mọi nơi chỉ vì around advice làm việc đó dễ dàng.

## 8. Thứ tự aspect làm thay đổi số lần đo và log

Giả sử ta kết hợp một chính sách retry và một chính sách theo dõi:

```text
Retry(Logging(Target))
```

Nếu Retry gọi tiếp 2 lần, phần Logging nằm **bên trong** có thể chạy **hai lần**. Khi đó log thể hiện hai **attempt**; hữu ích để chẩn đoán từng lần thử.

Ngược lại:

```text
Logging(Retry(Target))
```

Logging ở ngoài thường bao quanh **một lời gọi logic** và có thể ghi **một lần** kết quả cuối, trong khi Retry ở trong tự thực hiện hai attempt.

### 8.1. Minh họa thứ tự

```text
Retry(Logging(Target)):
    Retry begins
        Logging attempt 1
        Target attempt 1 -> timeout
        Logging attempt 1 failed
        Logging attempt 2
        Target attempt 2 -> success
        Logging attempt 2 succeeded
    Retry returns success

Logging(Retry(Target)):
    Logging request begins
        Retry attempt 1 -> timeout
        Retry attempt 2 -> success
    Logging request succeeded
```

Đây là **mô hình cấu trúc**, không phải quy tắc ưu tiên mặc định của mọi công nghệ AOP. Nếu advice bên trong thay đổi kết quả, nuốt lỗi hoặc có pointcut không khớp mọi lần, diễn biến có thể khác. Phải kiểm tra thứ tự và số lần thực thi thực tế.

### 8.2. Đếm logical request, attempt và side effect riêng

Ba loại số liệu nên được phân biệt:

- **Logical request:** một ý định nghiệp vụ do người dùng/hệ thống gửi, ví dụ `REQ-1`.
- **Attempt:** số lần chuỗi tiếp tục thực thi đích hoặc phần bên trong.
- **Committed side effect:** số hiệu ứng nghiệp vụ thực sự được xác nhận.

Một request có thể gồm 2 attempts nhưng chỉ 1 effect, hoặc 2 effects nếu không chống trùng. Nếu chỉ nhìn metric “success=1” ở advice ngoài, ta có thể bỏ sót tác dụng phụ thừa. Nên ghi correlation/request ID và attempt ID riêng, trong giới hạn dữ liệu được phép lưu.

## 9. Tương tác với giao dịch

Một around advice có thể tạo ranh giới giao dịch. Khi kết hợp với retry, thứ tự rất quan trọng:

```text
Transaction(Retry(Target))
Retry(Transaction(Target))
```

Ở cấu trúc đầu, nhiều lần thử có thể diễn ra trong **cùng phạm vi giao dịch** nếu cơ chế đó có ý nghĩa như vậy. Ở cấu trúc sau, mỗi lần thử có thể mở **một giao dịch riêng** nếu transaction advice được thực thi lại.

Không nên tuyên bố một trong hai luôn đúng. Tính phù hợp phụ thuộc lỗi cần xử lý, trạng thái giao dịch sau lỗi, khả năng rollback/commit và hệ thống lưu trữ. Một số lỗi làm giao dịch hiện tại không còn phù hợp để tiếp tục; retry trong cùng giao dịch có thể thất bại hoặc sai logic.

Đặc biệt, **giao dịch cơ sở dữ liệu không tự kéo ngược hiệu ứng ở hệ thống bên ngoài** như email, HTTP hoặc thanh toán. Nếu effect ngoài đã hoàn tất rồi giao dịch DB rollback, chỉ gọi lại toàn bộ phương thức vẫn có thể lặp effect ngoài.

### 9.1. Outbox và xử lý lặp ở người nhận

Khi cần phát sự kiện cùng thay đổi dữ liệu, một **transactional outbox** có thể giúp lưu ý định phát sự kiện trong cùng giao dịch cơ sở dữ liệu. Tuy nhiên việc phát lại bản ghi outbox vẫn có thể xảy ra sau lỗi mạng; phía nhận cũng cần xử lý idempotent/deduplicate nếu không muốn áp hiệu ứng hai lần.

Ở đây trọng tâm không phải triển khai outbox, mà là nhớ rằng **retry và truyền sự kiện là hai tầng bảo đảm khác nhau**. Around advice không thể biến toàn bộ mạng phân tán thành một giao dịch nguyên tử chỉ bằng vài lần gọi `proceed`.

## 10. At-most-once, at-least-once và “exactly once”

Có thể dùng các khái niệm giao nhận sau để hiểu hệ quả:

| Khái niệm | Ý nghĩa trực giác | Đánh đổi |
| --- | --- | --- |
| **At-most-once execution/attempt** | Không tự ý lặp thực thi cùng thao tác trong phạm vi được kiểm soát | Có thể mất cơ hội hoàn tất nếu kết quả không chắc chắn |
| **At-least-once attempts/delivery** | Có thể lặp để tăng khả năng yêu cầu được xử lý | Có thể phát sinh thao tác trùng nếu không chống trùng |
| **Effectively-once effect** | Có thể có nhiều lần thử nhưng hệ thống cố bảo đảm một hiệu ứng nghiệp vụ cho một yêu cầu logic | Cần định danh, lưu trạng thái, phối hợp nhất quán và giới hạn rõ |
| **Exactly-once** | Một cam kết rất mạnh, chỉ có ý nghĩa khi xác định rõ phạm vi thao tác, lỗi và mô hình hệ thống | Không được suy ra từ việc gọi `proceed` hoặc có idempotency key đơn giản |

Trong hệ thống phân tán, việc đạt “exactly once” end-to-end trên tất cả hiệu ứng là rất khó và thường phải được diễn đạt dưới phạm vi giới hạn có thể chứng minh. Dùng cụm từ **một hiệu ứng nghiệp vụ theo khóa yêu cầu** sẽ rõ ràng hơn tuyên bố “mọi thứ chạy đúng một lần”.

Ngay cả khi effect chỉ ghi nhận một lần, log **attempt** có thể xuất hiện nhiều lần — điều đó không nhất thiết là lỗi.

## 11. Hủy tác vụ, timeout và bất đồng bộ

Around advice chạy trong một lời gọi đồng bộ có thể thấy một timeout hoặc nhận về đối tượng đại diện cho tác vụ bất đồng bộ. Điều đó không luôn có nghĩa công việc phía dưới đã dừng.

### 11.1. Cancel không đồng nghĩa rollback

Nếu một luồng gửi yêu cầu mạng, sau đó nhận timeout và hủy tác vụ, máy chủ bên kia **có thể vẫn tiếp tục xử lý**. Việc gọi `Future.cancel(...)` hoặc ngắt luồng ở phía client không chứng minh hiệu ứng bên ngoài được hoàn tác.

Nếu around advice muốn retry sau timeout, điều kiện idempotency và đối soát vẫn cần có dù client nghĩ mình đã “cancel” lần trước.

### 11.2. Future trả về chưa phải hoàn tất công việc

Một phương thức có thể nhanh chóng trả `CompletableFuture<Result>` đang pending. Ở ranh giới phương thức đồng bộ, around advice gọi tiếp một lần rồi nhận **future** là một lần return bình thường. Nếu muốn retry khi future thất bại sau này, phải thiết kế ở **ranh giới completion của future**, không đơn thuần bắt ngoại lệ đồng bộ quanh lời gọi trả future.

Cũng cần quyết định mỗi lần retry có tạo một future mới hay chỉ chờ lại cùng future; chờ lại cùng một future thất bại không biến thành lần thực thi mới. Tương tự, vòng đời tài nguyên và bối cảnh truy vết phải gắn với thời gian xử lý thật, tránh đóng quá sớm.

### 11.3. Retry đồng thời có thể làm trùng hiệu ứng

Hai yêu cầu mang cùng idempotency key có thể đến **gần đồng thời**. Nếu cả hai cùng kiểm tra “chưa tồn tại khóa”, rồi cùng tạo hiệu ứng trước khi đánh dấu hoàn tất, chúng vẫn lặp tác dụng phụ.

Một triển khai đúng thường cần thao tác nguyên tử/khóa duy nhất hoặc một mô hình trạng thái `IN_PROGRESS` có phối hợp. Kiểm tra `containsKey` rồi `put` trong một `HashMap` không đủ, kể cả khi mã chạy ổn trong thử nghiệm tuần tự.

## 12. Khi around advice biến đổi đối số hoặc kết quả

Một số mô hình AOP cho phép `proceed` với đối số thay thế. Điều này làm retry phức tạp hơn:

- Nếu **lần thứ hai dùng idempotency key khác**, bên đích có thể coi là yêu cầu mới và tạo effect thứ hai.
- Nếu lần thứ hai **thay số tiền hoặc sản phẩm**, khóa cũ không nên được dùng để che xung đột nội dung.
- Nếu advice trả **fallback** sau khi lần đầu đã commit nhưng timeout, người gọi có thể tưởng thao tác chưa xảy ra và tự phát yêu cầu mới.
- Nếu advice **nuốt lỗi** sau hết retry, telemetry bên ngoài có thể ghi nhận thành công dù thực tế chưa chắc thành công.
- Nếu advice thực hiện lại quanh **chuỗi aspect** có logic kiểm tra quyền, điều kiện hoặc trạng thái có thể thay đổi giữa các lần thử.

Nói ngắn gọn, gọi tiếp nhiều lần phải có hợp đồng rõ về **đối số nào giữ nguyên, điều kiện dừng, loại lỗi và giá trị trả về cuối**.

### 12.1. Không đổi overload chỉ vì thay đối số runtime

Trong Java, phương thức đã chọn theo overload ở điểm gọi không tự trở thành một overload khác chỉ vì around advice đưa vào một đối tượng runtime có kiểu khác. Khả năng thay đối số theo `proceed` phải tôn trọng kiểu/chữ ký và ngữ nghĩa của cơ chế AOP. Nếu cần gọi một thao tác khác, phải thiết kế rõ đó là **fallback/delegation khác**, không giả định retry tự giải lại overload.

## 13. Dọn dẹp tài nguyên qua nhiều attempt

Khi retry, mỗi lần thử có thể:

- Mở kết nối;
- Chiếm khóa;
- Tạo trace span;
- Gắn bối cảnh vào luồng;
- Mở hoặc tham gia giao dịch;
- Đăng ký callback.

Những tài nguyên đó cần được giải phóng theo **đúng phạm vi**.

```text
RetryScope
    Attempt 1: acquire -> proceed -> fail -> cleanup attempt 1
    Attempt 2: acquire -> proceed -> success -> cleanup attempt 2
    RetryScope cleanup
```

Nếu resource advice nằm **bên trong retry**, mỗi attempt thường được tạo và dọn riêng. Nếu resource advice nằm **ngoài retry**, một scope chung bao cả lần thử. Hai cách có thể đều hợp lệ tùy loại tài nguyên; dùng sai phạm vi có thể dẫn tới mở hai lần nhưng đóng một lần, đóng sớm hoặc cố tái sử dụng transaction đã lỗi.

`finally` đảm bảo chạy trên những đường thoát bình thường/ngoại lệ khi phạm vi đã được thiết lập, **không tự đảm bảo tài nguyên được cấp trước khi vào phạm vi ấy sẽ được dọn**. Nếu cleanup tự ném lỗi, nó còn có thể che lỗi gốc. Nên kiểm tra cả lỗi acquire, lỗi proceed và lỗi close.

## 14. Điểm can thiệp, self-invocation và proxy

Một bộ retry được cài bằng proxy sẽ chỉ hoạt động khi lời gọi **đi qua proxy**. Nếu một phương thức nội bộ của đích gọi trực tiếp `this.charge(...)`, nó có thể không qua lớp retry ngoài. Ngược lại, nếu nhiều proxy bao cùng một đối tượng hoặc một phương thức chứa lời gọi tới một proxy khác, retry có thể bị **lồng nhau**.

Với weaving trực tiếp vào mã/bytecode, loại join point và phạm vi có thể khác. Không nên dùng thành công của ví dụ proxy để kết luận mọi cơ chế AOP hỗ trợ method call, method execution, constructor hay field access có cùng hành vi.

**Một pointcut khớp** không đủ chứng minh số lần thao tác: phải ghi nhận đúng đường gọi và thứ tự các advice trong chương trình thực.

### 14.1. Tránh advice tự gọi chính nó

Nếu trong around advice, thay vì tiếp tục chuỗi bên trong, ta gọi lại cùng phương thức **qua chính proxy đang xử lý**, lời gọi có thể lại kích hoạt advice và tạo recursion không giới hạn:

```text
proxy.charge()
  -> aroundAdvice
       -> proxy.charge()
            -> aroundAdvice
                 -> ...
```

Đây không phải retry được kiểm soát. Cần dùng cơ chế tiếp tục chính thức hoặc đường ủy quyền không tái nhập ngoài ý muốn, và đặt giới hạn số lần rõ ràng.

## 15. Thiết kế một chính sách retry trong around advice

Một quy trình thực tế nên đi theo thứ tự:

1. **Viết hợp đồng nghiệp vụ của thao tác đích.** Tác vụ có side effect không? Side effect nằm ở đâu? Tình huống lỗi sau commit có thể xảy ra không?
2. **Chỉ rõ điều kiện đủ để thử lại.** Loại lỗi nào được xem là tạm thời? Có thông tin xác nhận thất bại chắc chắn hay trạng thái không chắc chắn?
3. **Đặt ranh giới chống trùng.** Yêu cầu logic được nhận diện bằng khóa nào? Ai kiểm soát atomicity và thời hạn khóa?
4. **Quy định ngân sách.** Tối đa bao nhiêu attempt, deadline, backoff/jitter, điều kiện hủy?
5. **Chọn vị trí trong chuỗi aspect.** Retry nằm trong hay ngoài transaction, audit, tracing, metrics và resource scope?
6. **Giữ đối số nhất quán.** Cùng logical request phải giữ idempotency key và payload tương thích, hoặc báo lỗi xung đột.
7. **Bảo toàn lỗi và kết quả.** Khi hết retry, lỗi nào đi ra? Có được trả kết quả trạng thái không chắc chắn không? Không vô tình đổi kết quả.
8. **Quan sát đủ ba số liệu.** Logical request, số attempt, số effect thật; không lấy metric thành công thay bằng chứng không có hiệu ứng lặp.
9. **Kiểm thử cả đường bypass và đa luồng.** Pointcut được chọn, proxy/weaving hoạt động, không có retry lồng hoặc race condition.
10. **Có cách đối soát.** Với trạng thái bên ngoài không chắc chắn, cần khả năng truy vấn trạng thái/giao dịch thay vì luôn gửi thêm một thao tác mới.

### 15.1. Giả mã retry có ngân sách rõ

```text
around request:
    key = stableIdentityOfLogicalRequest(request)
    for attempt in 1..maxAttempts:
        if deadlineExpired():
            return/throw according to operation contract
        try:
            return proceedWithSameRequestAndKey()
        catch error:
            if not retryable(error):
                throw error
            if attempt == maxAttempts:
                throw error
            if outcomeAmbiguous(error) and not safelyDeduplicated(key):
                return/throw unknown-outcome state for reconciliation
            backoffWithinDeadline(attempt)
```

Đây là **giả mã thiết kế**, không phải đoạn triển khai chạy độc lập hay lời bảo đảm một bộ AOP hỗ trợ `proceedWithSameRequestAndKey`. `safelyDeduplicated(key)` biểu thị một **hợp đồng an toàn có cơ sở**, không có nghĩa chỉ kiểm tra một biến Boolean ở client là đủ.

Nếu không thể chứng minh lần thử lại an toàn, **không thử lại tự động** thường tốt hơn cố giấu tình trạng không chắc chắn.

## 16. Ma trận kiểm thử cần có

| Kịch bản | Kỳ vọng cần xác minh |
| --- | --- |
| Thành công ở lần đầu | Một lần tiếp tục, một hiệu ứng, kết quả giữ nguyên |
| Lỗi chắc chắn trước khi có effect | Retry nếu hợp đồng cho phép, không phát sinh effect cũ |
| Timeout sau khi effect đã commit | Không tạo hiệu ứng thứ hai cho cùng logical request |
| Lỗi vĩnh viễn | Không retry vô ích |
| Hết số lần thử | Ngừng đúng ngân sách, truyền lỗi/trạng thái phù hợp |
| Hai request khác nhau | Được phép tạo hai effect khi thực sự là hai yêu cầu |
| Cùng khóa, cùng payload | Hiệu ứng không trùng và có kết quả/trạng thái nhất quán |
| Cùng khóa, payload khác | Bị từ chối/xử lý theo quy tắc xung đột |
| Hai lời gọi cùng khóa đồng thời | Chỉ một effect nếu đó là cam kết idempotency |
| Retry ngoài/ trong metrics | Số logical request và số attempt đúng |
| Retry trong/ngoài transaction | Mỗi phạm vi được tạo, kết thúc hoặc rollback đúng |
| Hủy/timeout nhưng bên nhận vẫn chạy | Có đối soát, không coi cancel là rollback |
| Async future thất bại sau khi method trả | Retry ở đúng ranh giới completion, không chỉ ở method return |
| Đường gọi bỏ qua proxy | Phát hiện cơ chế không chạy thay vì coi là không cần retry |
| Có nhiều around advice | Thứ tự và số lần thực thi advice bên trong đúng |
| Cleanup ném lỗi | Không vô tình che lỗi chính nếu hợp đồng cần giữ nó |

### 16.1. Chỉ test kết quả trả về sẽ bỏ sót lặp effect

Một test như `assertEquals("RECEIPT-2", charge(...))` có thể **đạt** trong trường hợp không chống trùng nhưng vẫn có hai khoản thu. Cần kiểm tra thêm bộ đếm effect, số bản ghi, số sự kiện thực tế hoặc trạng thái của hệ thống lưu trữ.

Nếu là thanh toán, hãy kiểm thử dựa trên **biên bản giao dịch được xác nhận** trong môi trường thử nghiệm, không chỉ một thông báo thành công ở phía client.

## 17. Sai lầm thường gặp

| Sai lầm | Vì sao nguy hiểm | Cách sửa |
| --- | --- | --- |
| Gọi `proceed` hai lần vì muốn đo độ ổn định | Có thể làm lại toàn bộ nghiệp vụ | Dùng một lần chuyển tiếp cho monitoring thông thường |
| Retry mọi `Exception` | Retry cả lỗi vĩnh viễn/bất biến | Chỉ chọn lỗi có hợp đồng retry rõ |
| Cho rằng timeout là chưa thực hiện | Effect có thể đã commit trước timeout | Dùng idempotency/đối soát |
| Tạo key mới cho mỗi attempt | Bên nhận coi là yêu cầu mới | Giữ cùng key cho cùng logical request |
| Chỉ deduplicate trong bộ nhớ client | Không chịu được restart/đa instance | Chống trùng ở nơi sở hữu effect với phối hợp bền vững |
| Kiểm tra key rồi ghi effect không nguyên tử | Hai luồng có thể cùng thấy key chưa tồn tại | Ràng buộc duy nhất/atomic state hoặc giao thức tương đương |
| Tin transaction DB tự rollback cả email/HTTP | Tác dụng phụ ngoài transaction không được hoàn tác | Thiết kế outbox/đối soát/dedup ở ranh giới phù hợp |
| Không giới hạn retry | Tăng tải, treo yêu cầu, lặp effect | Max attempts, deadline, backoff/jitter |
| Hai tầng cùng retry độc lập | Số attempt nhân lên | Thống nhất tầng chịu trách nhiệm và ngân sách |
| Đặt metrics ở ngoài retry rồi gọi một lần là một attempt | Bỏ sót các lần thử bên trong | Tách metric request và attempt |
| Catch rồi nuốt lỗi ở attempt cuối | Người gọi nhận trạng thái sai | Giữ lỗi/unknown state đúng hợp đồng |
| Dùng `finally` sai vòng đời | Rò rỉ hoặc đóng tài nguyên quá sớm | Dọn mỗi scope theo ownership, gồm nhánh lỗi |
| Tự gọi proxy từ advice thay vì tiếp tục chuỗi | Dễ lặp đệ quy không giới hạn | Dùng cơ chế proceed/delegation đúng |
| Bắt lỗi đồng bộ cho future rồi tưởng bắt được lỗi async | Future có thể thất bại sau khi phương thức đã return | Gắn xử lý vào completion thật |
| Chỉ nhìn số lần gọi API mà không nhìn effect | Không phát hiện double charge | Kiểm tra trạng thái xác nhận nghiệp vụ |

## 18. Bài tập vận dụng và đáp án

### Bài 1 — Tác dụng phụ lặp

Một thao tác `sendWelcomeEmail(userId)` được bao bởi around advice retry tối đa ba lần khi timeout. Lần đầu thư đã gửi nhưng phản hồi xác nhận bị mất. Điều gì có thể xảy ra?

**Đáp án:** Lần thử lại có thể gửi thêm email cùng nội dung. Timeout không chứng minh email chưa gửi. Cần khóa yêu cầu hoặc cơ chế gửi chống trùng, và phải phân biệt yêu cầu gửi email **một lần** với các attempt kỹ thuật.

### Bài 2 — Chọn thứ tự aspect

Bạn muốn đo **tổng thời gian** cả chuỗi retry nhưng vẫn muốn biết **độ trễ từng attempt**. Nên đặt hai lớp đo ở đâu?

**Đáp án:** Có thể đặt đo tổng bao ngoài `Retry`, và đo từng attempt ở bên trong: `TotalTimer(Retry(AttemptTimer(Target)))`. Cần xác minh thứ tự/precedence thực tế của công nghệ và tránh suy luận đây là thứ tự mặc định.

### Bài 3 — Timeout sau commit

Một request có idempotency key `K1` đã tạo khoản thu, nhưng client mất kết nối. Retry với key `K2` có bảo đảm tránh thu lặp không?

**Đáp án:** Không. Nếu máy chủ coi `K2` là logical request mới, nó có thể tạo khoản thu thứ hai. Cần giữ `K1` cho cùng ý định nghiệp vụ và có cơ chế xử lý key ở phía chịu trách nhiệm effect.

### Bài 4 — Hai luồng cùng key

Hai luồng cùng kiểm tra `!map.containsKey(key)`, cùng nhận `true`, rồi cùng gọi API tạo đơn trước khi `put(key,...)`. Đã đạt idempotency chưa?

**Đáp án:** Chưa. Check-then-act không nguyên tử tạo race condition. Cần bước đăng ký trạng thái/khóa duy nhất có tính atomic trước hoặc trong ranh giới xử lý effect và chính sách cho yêu cầu đang được xử lý.

### Bài 5 — Retry và lỗi nghiệp vụ

Một hàm từ chối đặt hàng vì số lượng âm bằng `IllegalArgumentException`. Around advice bắt mọi `RuntimeException` rồi gọi `proceed` tiếp. Có hữu ích không?

**Đáp án:** Thường không: dữ liệu âm không tự thành hợp lệ khi chạy lại. Retry gây thêm tải và có thể che nguyên nhân thật. Chỉ retry các lỗi tạm thời có điều kiện và hợp đồng an toàn phù hợp.

### Bài 6 — Số lần chạy advice

Cho cấu trúc `A(Retry(B(Target)))`. Retry thử hai lần. Giả sử A và B đều được áp đúng điểm và B bao quanh mỗi attempt. Mỗi advice chạy bao nhiêu lần?

**Đáp án:** A bao toàn bộ yêu cầu nên thường có **một lần kích hoạt**; B nằm trong Retry nên có **hai lần kích hoạt** nếu mỗi attempt tiếp tục qua B. Đó là kết quả của **cấu trúc lồng giả định**, không phải thứ tự mặc định mọi công cụ.

### Bài 7 — Future và retry

Một around advice đồng bộ gọi `proceed` rồi nhận `CompletableFuture`. Future hoàn tất ngoại lệ sau vài giây. Khối `catch` quanh `proceed` có tự bắt lỗi completion đó không?

**Đáp án:** Không. Phương thức đã trả về future bình thường; lỗi xuất hiện sau trong tác vụ bất đồng bộ. Cần xử lý tại ranh giới completion và làm rõ tạo tác vụ mới hay chỉ chờ future cũ, đồng thời giữ tính an toàn của side effect.

### Bài 8 — Chứng minh một hiệu ứng

Một bài test thấy around advice in một dòng `SUCCESS` sau lần retry thứ hai. Có thể khẳng định chỉ một hiệu ứng đã xảy ra không?

**Đáp án:** Không. Ví dụ Java của bài cho thấy người gọi có thể nhận kết quả thành công sau hai lần ghi. Phải kiểm tra trạng thái nghiệp vụ xác nhận, số hiệu ứng, khóa chống trùng và các đường lỗi trước/sau khi commit.

## 19. Ghi nhớ

- **Around advice có thể điều phối việc tiếp tục**, nhưng khả năng gọi `proceed` 0/1/nhiều lần là **phụ thuộc cơ chế AOP**.
- **Mỗi lần tiếp tục có thể chạy lại cả chuỗi advice bên trong và thao tác đích**, nên có thể lặp log, giao dịch và tác dụng phụ.
- **Timeout/ngoại lệ không chứng minh thao tác chưa tạo hiệu ứng.** Lỗi sau commit là lý do retry mù quáng rất nguy hiểm.
- **Idempotency** dùng để giữ một hiệu ứng logic dù có nhiều attempt; chỉ dùng cùng khóa là chưa đủ nếu không có kiểm soát nguyên tử/bền vững tại nơi sở hữu hiệu ứng.
- **Retry phải có giới hạn và điều kiện**: lỗi được phép, ngân sách, deadline, backoff/jitter, an toàn chống trùng và đối soát.
- **Thứ tự aspect quyết định số lần advice chạy và phạm vi tài nguyên/giao dịch**; đo request và attempt là hai nhiệm vụ khác nhau.
- Với async và hệ phân tán, phải xét **khi nào effect thực sự hoàn tất**, không chỉ khi phương thức hoặc future được tạo trả về.
- Với advice chỉ quan sát, mặc định an toàn là **chuyển tiếp một lần**, bảo toàn kết quả, ngoại lệ và số lần thực thi nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-006">Đo thời gian bằng điểm bắt đầu và khối dọn dẹp</a>

<details>
<summary>Xem chi tiết</summary>

# Đo thời gian bằng điểm bắt đầu và khối dọn dẹp

## 1. Mục tiêu của đo thời gian trong around advice

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP), một **around advice** bao quanh join point được pointcut lựa chọn. Nó có thể thực hiện công việc trước khi chuyển điều khiển tới thao tác gốc, gọi thao tác gốc qua cơ chế **`proceed`** hoặc một hàm chuyển tiếp tương đương, rồi thực hiện công việc khi thao tác kết thúc. Mẫu đo thời gian tiêu biểu là:

> **Lấy mốc bắt đầu trước khi chuyển tiếp, gọi thao tác cần đo, và tính thời gian đã trôi qua trong khối dọn dẹp được bảo đảm chạy khi rời phạm vi.**

Với Java, ta thường dùng `System.nanoTime()` để đo **elapsed time** (thời lượng đã trôi qua), kết hợp `try/finally` để ghi nhận cả trường hợp trả về bình thường lẫn trường hợp ném ngoại lệ.

Ở bài này, **điểm bắt đầu** là thời điểm lấy số đo trước khi gọi `proceed`; **khối dọn dẹp** là `finally` bao quanh lời gọi ấy. Từ “dọn dẹp” không bắt buộc phải giải phóng file hay connection: trong ví dụ, nó là nơi **chốt phép đo và cập nhật số liệu** ngay trước khi rời phạm vi around advice.

Ví dụ, phương thức `checkout()` có thể hoàn tất thành công trong một lần gọi nhưng ném `IllegalArgumentException` trong lần khác. Nếu chỉ lấy thời gian ở nhánh thành công, ta sẽ thiếu dữ liệu của những ca thất bại — đôi khi chính là những ca chậm cần điều tra. Khi đặt phép tính elapsed trong `finally`, ta có thể quan sát cả hai nhánh **mà không buộc phải bắt và nuốt ngoại lệ nghiệp vụ**.

## 2. Luồng điều khiển cốt lõi

Sơ đồ ý niệm:

~~~text
Caller
  |
  v
[Join point khớp pointcut]
  |
  v
[Around advice]
  |
  +-- start = monotonicNow()
  |
  +-- try -------------------------+
  |      |                         |
  |      v                         |
  |    proceed()                  |
  |      |                         |
  |      +--> normal return -------+
  |      |                         |
  |      +--> exception -----------+
  |                                |
  +-- finally ---------------------+
         |
         +-- end = monotonicNow()
         +-- elapsed = end - start
         +-- record(elapsed)
  |
  v
Caller nhận kết quả hoặc ngoại lệ
~~~

Với `try/finally`, mã trong `finally` chạy khi đường `try` **rời phạm vi theo cơ chế thực thi thông thường của Java**. Nó chạy khi `proceed` trả bình thường hoặc ném ngoại lệ, kể cả khi nhánh `try` thực hiện `return`. Tuy nhiên:

- `finally` không phải bảo đảm tuyệt đối khi tiến trình bị kết thúc cưỡng bức, máy ảo dừng bất thường hoặc phạm vi advice chưa từng được đi vào.
- Nếu chính mã `finally` ném một ngoại lệ khác, ngoại lệ ấy **có thể thay thế lỗi gốc** hoặc làm caller không nhận kết quả vốn đã tính thành công.
- Nếu pointcut không khớp hoặc một đường gọi bỏ qua proxy/weaving, không có advice được kích hoạt và khối `finally` của advice cũng không chạy.
- Nếu around advice cố ý **không gọi `proceed`**, phép đo có thể là thời gian của đường fallback/cache/từ chối trong advice, không phải thời gian nghiệp vụ gốc.
- Nếu `proceed` được gọi nhiều lần để retry, định nghĩa thời lượng có thể là thời gian của toàn bộ chuỗi thử hoặc chỉ từng lần thử, tùy vị trí đo.

Bởi vậy, ngoài việc viết đúng `try/finally`, người thiết kế phải định nghĩa **chính xác phạm vi đang đo**.

## 3. `System.nanoTime()` khác `System.currentTimeMillis()` như thế nào?

Java cung cấp nhiều API liên quan đến thời gian, nhưng không phải API nào cũng phục vụ đo elapsed time.

| API | Ý nghĩa | Dùng cho phép đo nào? |
| --- | --- | --- |
| `System.nanoTime()` | Giá trị bộ đếm thời gian độ phân giải nano giây, mốc gốc tùy ý | **Đo khoảng thời gian** giữa hai lần đọc trong cùng tiến trình |
| `System.currentTimeMillis()` | Thời điểm lịch/epoch theo milli giây | Ghi mốc thời gian thực tế, đối chiếu với hệ thống ngoài |
| `Instant.now()` | Đối tượng thời điểm theo clock thời gian thực | Audit mốc giờ, dữ liệu có ý nghĩa lịch |
| `Duration` | Biểu diễn một khoảng thời gian với đơn vị rõ ràng | Lưu trữ, hiển thị và tính toán thời lượng đã xác định |

**Vì sao ưu tiên `nanoTime`?** Phép đo thời lượng cần lấy **hiệu số giữa hai mốc** của cùng đồng hồ monotonic phục vụ đo interval. Đồng hồ lịch có thể được điều chỉnh do đồng bộ giờ hoặc thay đổi cấu hình hệ thống, khiến một phép lấy hiệu thời điểm lịch không phải lựa chọn lý tưởng cho đo latency.

Cách tính:

~~~java
long started = System.nanoTime();
try {
    return operation();
} finally {
    long elapsedNanos = System.nanoTime() - started;
    recordElapsed(elapsedNanos);
}
~~~

Đây là **đoạn minh họa**, chưa độc lập vì `operation()` và `recordElapsed()` được đại diện bằng tên hàm giả định. Đoạn Java chạy đầy đủ xuất hiện ở phần sau.

Lưu ý:

1. **Đơn vị nano giây không đồng nghĩa với độ chính xác thực tế một nano giây.** Độ phân giải và sai số phụ thuộc JVM, hệ điều hành, bộ đếm phần cứng và tải hệ thống.
2. Giá trị tuyệt đối của `nanoTime()` **không có ý nghĩa lịch** và có thể mang dấu âm vì mốc gốc tùy ý. Chỉ quan tâm `end - start` trong cùng hệ đồng hồ.
3. Không lấy `nanoTime()` rồi diễn giải như số nano giây từ Unix epoch. Muốn log lúc nào lời gọi bắt đầu theo giờ thực, ghi thêm một timestamp riêng.
4. Với các interval thực tế ngắn hơn phạm vi tràn số của phép trừ `long` (xấp xỉ 292 năm), cách lấy hiệu thời gian thông thường là phù hợp. Không lưu mốc bắt đầu vào cấu trúc tồn tại qua nhiều tiến trình và mặc định chúng có cùng mốc `nanoTime`.
5. Nếu chỉ muốn hiển thị milli giây, chuyển **sau khi đã tính hiệu**: `TimeUnit.NANOSECONDS.toMillis(elapsedNanos)`. Việc làm tròn có thể khiến nhiều lời gọi nhanh đều hiện `0 ms`.
6. Đo trên một đường chạy thực có độ biến thiên. **Không khẳng định** phép đo trong một lần chạy là số hằng chính xác hoặc luôn ổn định giữa các lần chạy.

## 4. Dùng `try/finally` để bảo toàn cả hai đường kết thúc

Mẫu đáng nhớ:

~~~java
long started = System.nanoTime();
try {
    return proceed();
} finally {
    long elapsed = System.nanoTime() - started;
    metrics.record(elapsed);
}
~~~

Về ngữ nghĩa Java:

- Nếu `proceed()` trả một giá trị bình thường, `finally` chạy **trước khi caller nhận được** giá trị `return` từ wrapper; sau đó giá trị được chuyển tiếp.
- Nếu `proceed()` ném một ngoại lệ, `finally` vẫn chạy trong quá trình tháo ngăn xếp; nếu `finally` không tạo ngoại lệ khác, ngoại lệ gốc tiếp tục lan truyền.
- Nếu wrapper cần hỗ trợ method `void` hoặc giá trị `null`, adapter/engine phải giữ đúng hợp đồng return type; không được ép mọi kết quả thành `String`.
- Nếu `metrics.record(...)` chậm hoặc lỗi, nó có thể làm thay đổi latency và kết quả mà caller quan sát. **`finally` chỉ bảo đảm nơi mã được thực thi, không tự làm mã metrics an toàn.**

Trong một advice chỉ đo lường, việc **trả lại nguyên kết quả và ném lại nguyên ngoại lệ** là bất biến thiết kế tốt. Không cần `catch (Throwable)` quanh toàn bộ target chỉ để đo elapsed time; `finally` đủ để chốt thời gian ở hai nhánh. Chỉ thêm `catch` khi có nhu cầu phân loại lỗi hoặc chính sách chuyển đổi đã được công bố.

## 5. Điều gì chính xác được tính vào thời gian đo?

Khoảng thời gian bằng:

\[
\Delta t = t_{\mathrm{end}} - t_{\mathrm{start}}
\]

Nếu bắt đầu ngay trước `proceed` và kết thúc ngay sau khi `proceed` rời phạm vi:

- Bao gồm thời gian thực thi thao tác gốc **cùng mọi tầng advice nằm bên trong** ranh giới đo.
- Có thể bao gồm thời gian chờ khóa, I/O, scheduling delay, garbage collection hoặc các tác vụ đồng bộ mà thao tác thực hiện.
- Không đo riêng **CPU time**. Elapsed/wall-clock duration có thể lớn dù thread dành phần lớn thời gian chờ.
- Không nhất thiết bao gồm logic chạy **trước khi đo bắt đầu** hoặc **sau khi đã tính end**, bao gồm thao tác gửi metric nếu vị trí đặt end trước khi ghi metric.
- Không tự bao gồm công việc bất đồng bộ tiếp tục sau khi phương thức đã trả Future hoặc schedule task.

Ví dụ:

~~~text
Around outer metric: start
  Audit before
    Authorization check
      Business performs I/O
    Authorization after
  Audit after
Around outer metric: end
Send metric
~~~

Nếu `outer metric` đo bao ngoài toàn chuỗi, số đo có thể bao gồm audit, kiểm quyền và nghiệp vụ bên trong. Nếu chỉ muốn **thời gian thực thi nghiệp vụ sau kiểm quyền**, phải chọn ranh giới hoặc precedence khác. Đừng đặt tên metric `business_duration` khi thực tế nó đo cả một chuỗi wrapper.

### 5.1. Thời gian do chính metrics gây ra

Thông thường, người viết lấy `end` **trước khi gọi sink metrics** để tránh cộng chính việc phát metric vào thời lượng của thao tác đang đo. Điều đó không có nghĩa metrics miễn phí: nếu sink chạy đồng bộ trên cùng lời gọi, caller vẫn phải đợi sink hoàn thành. Khi cần đánh giá **end-to-end caller latency**, phải đo ở một ranh giới ngoài hơn hoặc dùng một phép đo độc lập tính cả overhead.

### 5.2. Nested join points và đo nhiều lần

Nếu `checkout()` gọi `reserve()` và cả hai đều khớp pointcut:

~~~text
measure(checkout)
  checkout
    measure(reserve)
      reserve
~~~

Ta có hai số đo. Thời gian `checkout` có thể **bao gồm** thời gian `reserve`. Cộng hai số đo rồi gọi tổng là “thời gian CPU” hoặc “tổng latency không trùng lặp” sẽ dẫn đến **double-counting**. Cần phân biệt inclusive duration và exclusive duration hoặc phân tích theo trace/span parent-child.

### 5.3. Retry và đo từng lần thử

Nếu advice retry bao trong stopwatch:

~~~text
Stopwatch
  Retry
    attempt 1 -> failed
    attempt 2 -> success
~~~

Stopwatch có thể báo thời gian **cả hai lần thử cùng khoảng chờ**. Nếu stopwatch nằm bên trong retry, mỗi attempt có phép đo riêng. Cả hai đều hợp lý khi được đặt tên rõ `request_duration` và `attempt_duration`. Khi đo retry, tuyệt đối không để đo lường vô tình thay đổi số lần gọi `proceed`.

## 6. Ví dụ Java 17+: around advice đo thời gian với `finally`

Ví dụ chạy độc lập này dùng **JDK dynamic proxy** để mô phỏng một around advice quanh phương thức `buy(int)`; `health()` nằm ngoài bộ chọn nên không được đo. `CheckoutCore` giữ logic nghiệp vụ, `StopwatchAdvice` chỉ bọc và ghi thời gian, `Metrics` là sink chuyên trách.

Để đầu ra **có thể kiểm chứng chính xác**, chương trình sử dụng một **đồng hồ giả monotonic** trả các mốc `0, 100, 200, ...` theo thứ tự. Khi triển khai thực tế, cung cấp `System::nanoTime` thay cho đồng hồ giả. Giá trị `100ns` trong đầu ra **chỉ là phép đo giả lập để kiểm thử**, không phải khẳng định code thật luôn mất 100 nano giây.

Tạo file `AroundTimingDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.LongSupplier;

public final class AroundTimingDemo {
    interface Checkout {
        String buy(int quantity);
        String health();
    }

    static final class CheckoutCore implements Checkout {
        private int successes;

        @Override
        public String buy(int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            successes++;
            System.out.println("BUSINESS buy " + quantity);
            return "order:" + quantity;
        }

        @Override
        public String health() {
            return "READY";
        }

        int successes() {
            return successes;
        }
    }

    @FunctionalInterface
    interface Proceeding {
        Object proceed() throws Throwable;
    }

    static final class Metrics {
        boolean unavailable;

        void record(String operation, long elapsedNanos) {
            if (unavailable) {
                throw new IllegalStateException("sink unavailable");
            }
            System.out.println("METRIC " + operation + " " + elapsedNanos + "ns");
        }
    }

    static final class StopwatchAdvice {
        private final LongSupplier clock;
        private final Metrics metrics;

        StopwatchAdvice(LongSupplier clock, Metrics metrics) {
            this.clock = clock;
            this.metrics = metrics;
        }

        Object around(String operation, Proceeding next) throws Throwable {
            long started = clock.getAsLong();
            try {
                return next.proceed();
            } finally {
                long elapsed = clock.getAsLong() - started;
                try {
                    metrics.record(operation, elapsed);
                } catch (RuntimeException sinkFailure) {
                    System.out.println("METRIC_ERROR " + operation + " "
                            + sinkFailure.getClass().getSimpleName());
                }
            }
        }
    }

    static Object targetCall(Checkout target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapper) {
            throw wrapper.getCause();
        }
    }

    static Checkout instrument(Checkout target, StopwatchAdvice timer) {
        return (Checkout) Proxy.newProxyInstance(
                Checkout.class.getClassLoader(),
                new Class<?>[] { Checkout.class },
                (proxy, method, args) -> {
                    if (!method.getName().equals("buy")) {
                        return targetCall(target, method, args);
                    }
                    return timer.around("buy",
                            () -> targetCall(target, method, args));
                });
    }

    public static void main(String[] args) {
        // Fake monotonic clock: 0, 100, 200, ... for stable demo output.
        final long[] ticks = {0L};
        LongSupplier fakeClock = () -> {
            long now = ticks[0];
            ticks[0] += 100L;
            return now;
        };
        Metrics metrics = new Metrics();
        CheckoutCore core = new CheckoutCore();
        Checkout api = instrument(core, new StopwatchAdvice(fakeClock, metrics));

        System.out.println("STATUS " + api.health());
        System.out.println("RESULT " + api.buy(2));
        try {
            api.buy(0);
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }
        metrics.unavailable = true;
        System.out.println("RESULT " + api.buy(3));
        try {
            api.buy(-1);
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }
        System.out.println("BUSINESS_SUCCESSES " + core.successes());
    }
}
~~~

Biên dịch và chạy trên JDK 17 hoặc mới hơn:

~~~bash
javac --release 17 -encoding UTF-8 AroundTimingDemo.java
java AroundTimingDemo
~~~

Đầu ra đã kiểm chứng:

~~~text
STATUS READY
BUSINESS buy 2
METRIC buy 100ns
RESULT order:2
METRIC buy 100ns
CALLER quantity must be positive
BUSINESS buy 3
METRIC_ERROR buy IllegalStateException
RESULT order:3
METRIC_ERROR buy IllegalStateException
CALLER quantity must be positive
BUSINESS_SUCCESSES 2
~~~

### 6.1. Phân tích ba nhánh đo

**Nhánh thành công, sink hoạt động:** `buy(2)` trả `"order:2"`, `finally` nhận hai mốc giả cách nhau `100ns`, sink ghi metric, rồi caller nhận kết quả ban đầu. Không có phép biến đổi return.

**Nhánh target ném ngoại lệ, sink hoạt động:** `buy(0)` ném `IllegalArgumentException` trước khi in `BUSINESS buy`. `finally` vẫn chạy và ghi `METRIC buy 100ns`. Caller nhận lại thông điệp lỗi nghiệp vụ. Advice không biến một lần thất bại thành “thành công”.

**Nhánh sink lỗi, target thành công hoặc thất bại:** sau khi `metrics.unavailable=true`, hai lượt `buy(3)` và `buy(-1)` khiến sink tự ném `IllegalStateException`. Ví dụ bắt **riêng lỗi RuntimeException của sink** và in `METRIC_ERROR`, không để nó che kết quả `"order:3"` hay `IllegalArgumentException` từ target. Đây là lựa chọn **fail-open riêng cho sink metrics** của ví dụ, không phải chính sách bắt buộc cho mọi hệ thống. Nếu metric là nghĩa vụ bắt buộc thì có thể cần fail-closed hoặc cơ chế bảo đảm khác.

`BUSINESS_SUCCESSES 2` cho biết chỉ hai lần mua với số lượng hợp lệ chạy thành công. Có **bốn phép đo được thử ghi**, nhưng hai lần sau sink lỗi nên không có metric bình thường. Không được lấy số lần `finally` chạy làm số “giao dịch thành công”.

### 6.2. Hợp đồng và giới hạn của chương trình

- `fakeClock` được triển khai bằng mảng `long[]` nhằm có stdout dự đoán được. Nó **không an toàn cho truy cập đồng thời** và không phải bộ đo thời gian production. Trong ứng dụng thực, có thể khởi tạo `new StopwatchAdvice(System::nanoTime, metrics)` và thay sink bằng hệ thống metrics phù hợp.
- `targetCall()` dùng `Method.invoke()` rồi bóc `InvocationTargetException` để truyền tiếp ngoại lệ gốc. Nếu không bóc, lớp bọc reflection có thể làm sai loại lỗi mà caller nhận.
- `Method.getName().equals("buy")` chỉ là pointcut mô phỏng. Nếu interface có nhiều overload `buy`, cần chọn theo chữ ký rõ hơn.
- Proxy chỉ chặn lời gọi **qua đối tượng `api`**. Lời gọi trực tiếp vào `core` hoặc self-invocation không đi qua proxy này.
- `catch (RuntimeException)` quanh `metrics.record` chỉ xử lý loại lỗi hạ tầng phù hợp chính sách demo. Không dùng `catch (Throwable)` rộng để che `Error` nghiêm trọng trong hệ thống thực. Thậm chí lệnh báo lỗi trong `catch` vẫn có thể tự thất bại; muốn fail-open mạnh hơn cần một chiến lược quan sát độc lập và được kiểm thử.
- Dữ liệu nhãn `operation` là chuỗi cố định `"buy"`, tránh đưa định danh người dùng/đơn hàng vào metric label gây tăng cardinality và lộ dữ liệu.

## 7. Đặt mốc bắt đầu ở đâu cho đúng?

Một lỗi phổ biến là lấy mốc bắt đầu **sau khi đã gọi `proceed`**:

~~~java
Object result = next.proceed();
long started = System.nanoTime(); // Sai nếu muốn đo thời gian của proceed()
~~~

Ở đây phép đo không bao giờ bao gồm thời gian của thao tác vừa chạy. Một lỗi khác là đặt mốc `started` trong nhánh điều kiện mà một số đường đi không thực hiện, rồi sử dụng một biến chưa được khởi tạo chính xác.

Mẫu nhất quán:

~~~text
start = clock.now()
try:
    result = proceed()
    return result
finally:
    elapsed = clock.now() - start
    recordSafely(elapsed)
~~~

Đây là **pseudocode**, không phải cú pháp Java. Nguyên tắc: lấy mốc bắt đầu ngay trước vùng công việc muốn đo; chốt mốc kết thúc ngay sau vùng ấy trong `finally`. Nếu cần đo thêm tiền xử lý trước `proceed` thì chuyển `start` ra trước bước tiền xử lý; nếu muốn chỉ đo target, đặt `start` sát lời chuyển tiếp.

### 7.1. Trạng thái theo từng lần gọi

`started` phải là biến **cục bộ của một lần advice invocation** hoặc được quản lý theo đúng phạm vi tương đương. Không lưu `startTime` trong field của advice dùng chung giữa nhiều thread vì lần gọi B có thể ghi đè mốc của lần gọi A.

Phần mềm đo nhiều lời gọi đồng thời cần giữ một cặp mốc cho **mỗi lần thực thi**, đồng thời sink metrics phải hỗ trợ cập nhật đồng thời.

### 7.2. Khi lời gọi lồng và đệ quy

Mỗi lần vào around advice nên có mốc bắt đầu riêng. Ví dụ `factorial(5)` tự gọi `factorial(4)` và nhiều mức thấp hơn, nếu mọi lần đều được can thiệp, ta có thể thu nhiều quan sát duration. Đừng dùng một biến `static started` hoặc bộ đếm toàn cục như một mốc duy nhất vì các lần gọi sẽ chồng lấn lên nhau.

### 7.3. Khi around cố ý bỏ qua `proceed`

Với cache hit, một advice có thể trả kết quả mà không gọi target. Nếu đồng hồ bắt đầu trước quyết định cache và kết thúc ở `finally`, phép đo vẫn hợp lệ **cho thao tác quanh lời gọi**, nhưng không phải “thời gian chạy target”. Khi mục tiêu là metrics của cache, hãy phân biệt `cache_lookup_duration` và `business_execution_duration` theo phạm vi thực tế.

## 8. Khi `finally` tự gây lỗi: bảo toàn hợp đồng ngoại lệ

Xét đoạn mã:

~~~java
long start = System.nanoTime();
try {
    return next.proceed();
} finally {
    metrics.record(System.nanoTime() - start); // có thể ném exception
}
~~~

Đây là đoạn Java minh họa không độc lập (thiếu `next`, `metrics`). Nếu `next.proceed()` ném `IllegalArgumentException` và `metrics.record(...)` lại ném `IllegalStateException`, caller có thể nhận ngoại lệ **của metrics** thay vì lỗi nghiệp vụ gốc. Điều này phá tính quan sát và gây khó debug.

Thiết kế cần nêu chính sách:

| Chính sách lỗi metrics | Cách tiếp cận | Hệ quả cần chấp nhận |
| --- | --- | --- |
| **Fail-open** | Xử lý những lỗi sink được dự đoán; báo chúng qua kênh riêng; trả/throw tiếp như trước | Có thể thiếu dữ liệu đo, cần health monitoring |
| **Fail-closed** | Cho lỗi đo lường ảnh hưởng lời gọi nếu nghĩa vụ bắt buộc | Khả năng phục vụ của nghiệp vụ phụ thuộc hạ tầng quan sát |
| **Buffered/async sink** | Đẩy quan sát vào hàng đợi/buffer có giới hạn và chính sách backpressure | Cần quyết định mất mẫu, flush và vòng đời; không tự có độ bền chắc chắn |

Một hệ thống metric thông thường ưu tiên giảm ảnh hưởng lên nghiệp vụ, nhưng **không phải mọi dữ liệu quan sát đều tùy chọn**; audit bắt buộc có thể cần bảo đảm khác. Đây là quyết định kiến trúc, không được che bằng một `catch` vô điều kiện.

Nếu `clock.getAsLong()` tự gây lỗi trước khi vào `try`, `finally` chưa bắt đầu nên không thể bảo đảm chốt metric. Vì vậy việc đọc đồng hồ phải là thao tác nhẹ, có hợp đồng ổn định. `System.nanoTime()` thường thích hợp; đồng hồ giả nên được kiểm thử riêng.

## 9. Đo thành công và thất bại: elapsed time không phải outcome

`finally` chỉ bảo đảm thời điểm chốt, không tự cung cấp nhãn **thành công nghiệp vụ**. Nếu muốn phân loại:

- **Normal completion:** `proceed` trả bình thường, kể cả trả `null`, `false` hoặc `Result.failure`.
- **Exceptional completion:** `proceed` ném lỗi ra khỏi phạm vi được quan sát.
- **Business success/failure:** xác định bằng quy tắc miền, có thể khác hai nhóm trên.
- **Cancellation/timeout:** phụ thuộc cơ chế đồng bộ/bất đồng bộ; có thể là exception, trạng thái kết quả hoặc hoàn tất theo cách riêng.

Một around advice có thể ghi `outcome=normal` khi `proceed` trả bình thường và `outcome=exceptional` khi nó ném lỗi, rồi ghi metric thời gian ở `finally`. Nhưng nếu yêu cầu chỉ là đo elapsed, không nên nhồi mọi luật phân loại nghiệp vụ vào advice đo thời gian. Hãy cân nhắc một advice hoặc dịch vụ chuyên trách phân loại, có kiểm thử riêng.

### 9.1. Không để lỗi ghi metric biến thành “lỗi nghiệp vụ”

Nếu sink thất bại sau khi target thành công, một wrapper ngoài nhìn thấy exception do sink chứ không phải do target. Cần phân biệt nguồn lỗi trước khi tính tỷ lệ lỗi của nghiệp vụ; nếu không, số liệu reliability sẽ sai. Cấu trúc bọc lỗi trong ví dụ cố ý bắt riêng exception của sink để tránh tình huống này theo chính sách fail-open.

### 9.2. Không coi mọi `finally` là một lần target chạy

Với caching, authorization denial hoặc around advice khác bỏ `proceed`, khối `finally` của stopwatch vẫn chạy, nhưng target có thể chưa được thực hiện. Số lượt đo của wrapper là **số lượt join point đi qua wrapper**, không tự đồng nghĩa với số lần thực thi nghiệp vụ.

## 10. Bất đồng bộ: đo method return khác đo task completion

Giả sử:

~~~java
CompletableFuture<String> startWork() {
    return CompletableFuture.supplyAsync(() -> performWork());
}
~~~

Đoạn này là **mã minh họa chưa độc lập**; `performWork()` là thao tác do ứng dụng cung cấp.

Nếu around advice lấy `start`, gọi `proceed` và chốt `finally` ngay sau khi `startWork()` trả `CompletableFuture`, số đo là **thời gian tạo/trả Future**. Tác vụ bên trong có thể chạy sau hàng giây, thất bại hoặc bị hủy. Việc `finally` đã chạy **không có nghĩa toàn bộ task đã hoàn tất**.

Muốn đo “thời lượng tác vụ đến khi hoàn tất”, cần thiết kế **điểm kết thúc** gắn với sự kiện completion thật, có thể dùng callback hoặc instrumentation của runtime:

~~~java
long started = System.nanoTime();
CompletableFuture<String> future = startWork();
future.whenComplete((value, error) -> {
    long elapsed = System.nanoTime() - started;
    recordTaskDuration(elapsed, error);
});
~~~

Đây là đoạn minh họa, chưa chạy độc lập vì thiếu định nghĩa `startWork` và `recordTaskDuration`. Nó đo khoảng thời gian từ lúc tạo tác vụ đến khi stage `future` hoàn tất, **không phải CPU time** của tác vụ. Callback có thể chạy ở thread khác; trạng thái ngữ cảnh, cancellation, lỗi callback và vòng đời đồng hồ phải được xem xét.

Quan trọng: `whenComplete` trong Java trả **một stage mới**; nếu bỏ qua stage đó, lỗi của callback có thể khó được quan sát. Khi instrument nhiều lớp Future, cần bảo đảm không làm thay đổi result/cancellation/exception của API chỉ để gắn metric.

## 11. Đồng hồ giả và cách kiểm thử phép đo

Kiểm thử có sử dụng `Thread.sleep(50)` rồi yêu cầu duration chính xác `50ms` rất dễ không ổn định do hệ điều hành scheduling, CPU load và thời gian khởi động JVM. Mẫu tốt hơn là **tiêm nguồn đồng hồ** hoặc một `LongSupplier` vào advice, rồi cho nguồn đồng hồ trả trước những giá trị đã biết.

Ví dụ trong chương trình chạy được:

~~~java
final long[] ticks = {0L};
LongSupplier fakeClock = () -> {
    long now = ticks[0];
    ticks[0] += 100L;
    return now;
};
~~~

Đồng hồ giả cung cấp cặp mốc khác nhau `100` đơn vị nanosecond giả cho mỗi lần `buy()`. Việc `buy()` nhanh hay chậm ngoài đời không ảnh hưởng assertion của bài test. Nó giúp chứng minh **đọc đồng hồ hai lần, trừ đúng thứ tự và ghi sau khi `proceed` thoát**, thay vì vô tình đo sau khi target đã xong.

Lưu ý:

- Trong ví dụ này, clock giả được gọi tuần tự và **không thread-safe**. Test đồng thời cần đồng hồ giả được đồng bộ hoặc thiết kế riêng.
- Clock giả chỉ chứng minh **logic phép tính và đường điều khiển**, không chứng minh hiệu năng production.
- Khi test thực tế, có thể kiểm tra elapsed **không âm** trong phạm vi giả định phù hợp nhưng tránh assertion cứng “phải đúng N nano giây” cho đồng hồ thật.
- Kiểm tra cả nhánh thành công, nhánh ngoại lệ và sink thất bại. Một test chỉ đo normal return không chứng minh `finally` được dùng đúng.
- Nếu ghi histogram, xác minh đơn vị đo và bucket. Nhầm `ns` với `ms` có thể làm số liệu sai một triệu lần.

## 12. Chi phí đo lường và tác dụng phụ của metrics

Advice đo thời gian vẫn có overhead: đọc clock, lựa chọn pointcut, gọi wrapper, boxing, ghi metric và có thể gửi sự kiện đi nơi khác. Với join point gọi hàng triệu lần, overhead nhỏ trên từng lần vẫn có thể tích lũy đáng kể.

Các thực hành có ích:

1. **Giới hạn pointcut**: không đo mọi getter/công cụ helper nếu mục tiêu là latency của API quan trọng.
2. **Đọc clock với chi phí thấp**: `System.nanoTime()` phù hợp hơn một lời gọi mạng để lấy giờ.
3. **Chốt thời gian trước khi ghi metric**: tách duration của target khỏi chi phí xuất metric nếu đó là định nghĩa mong muốn.
4. **Không ghi mọi mẫu thành log đầy đủ** nếu khối lượng lớn; cân nhắc histogram, thống kê aggregate hoặc sampling theo yêu cầu.
5. **Đặt tên metric và đơn vị rõ ràng** như `operation_duration_seconds` hoặc nhãn `elapsed_nanos` tùy hệ thống lưu. Nếu backend yêu cầu seconds, chuyển từ nano giây sang seconds đúng cách.
6. **Giới hạn cardinality** của nhãn: tên operation chuẩn hóa là đủ trong nhiều trường hợp; đừng gắn mỗi `orderId`, `userId` hoặc URL đầy đủ có tham số vào nhãn.
7. **Chính sách lỗi sink và backpressure rõ ràng**: không cho lời gọi nghiệp vụ bị treo vô hạn vì một hệ metrics không phản hồi, trừ khi hợp đồng ứng dụng chủ động yêu cầu.

### 12.1. Histogram, percentile và trung bình

Một lượt stopwatch chỉ tạo ra **một mẫu thời lượng**. Muốn trả lời “p95 của checkout trong 5 phút” cần thu nhiều mẫu đúng phạm vi, có phân phối/biểu đồ phù hợp. Một giá trị average không mô tả hết tail latency; một percentile được tính sai trên dữ liệu đã gom trung bình từ trước cũng gây hiểu nhầm.

Nếu có nhiều tầng around, kiểm tra có ghi trùng các mẫu cùng một lời gọi hay không trước khi phân tích thống kê. Nên gắn outcome, operation và loại đường chạy theo tập nhãn có giới hạn, không dùng nhãn động gây cardinality vô hạn.

### 12.2. Concurrency và timer shared

`long started` trong method `around` là biến cục bộ nên mỗi lần gọi có mốc riêng. Không lưu `started` thành mutable field của singleton advice: hai thread có thể ghi đè, dẫn đến hiệu số sai. Nếu metrics dùng counter hoặc histogram chia sẻ, sink phải thread-safe theo hợp đồng.

## 13. Phạm vi của proxy, weaving và điểm đo

Một công cụ AOP thường chỉ can thiệp những join point mà mô hình của nó hỗ trợ. Với JDK dynamic proxy trong ví dụ, chỉ có các lời gọi đi qua interface proxy `Checkout` mới được bọc. Lời gọi trực tiếp vào `CheckoutCore` sẽ không chạy `StopwatchAdvice`. Một lời gọi nội bộ qua `this.otherMethod()` cũng có thể bỏ qua proxy.

Với cơ chế weaving khác, tập join point có thể bao gồm method execution, constructor hoặc những điểm sâu hơn, **nếu công nghệ thật sự hỗ trợ** và mã được đưa vào phạm vi weaving. Không coi mọi phương thức `private`, `final`, `static` hay thư viện ngoài tự động được instrument.

Định nghĩa phép đo phải chỉ rõ **call join point** hay **execution join point**. Một phép đo ở caller có thể bao gồm overhead dispatch/chuyển tiếp khác phép đo bắt đầu khi thân method ở callee thực thi.

Nếu pointcut chọn quá nhiều lời gọi cùng một đường call stack, có thể có nested timing. Nếu một lời gọi không khớp, không có observation: đó có thể là behavior đúng của pointcut, không phải lỗi timer.

## 14. Thứ tự advice: thời gian của phần nào?

Với ba wrapper:

~~~text
Audit (outer)
  Timer (middle)
    Authorization (inner)
      Business
~~~

`Timer` ở giữa có thể tính cả `Authorization` và `Business` nhưng không tính phần audit nằm bên ngoài. Đảo vị trí `Timer` ra ngoài thì số đo bao gồm cả audit. Đặt `Timer` sau authorization thì những yêu cầu bị từ chối trước khi vào Timer không có observation.

Khi có `Retry`, vị trí Timer quyết định metric đo toàn bộ request hay từng attempt. Khi có cache, Timer bên ngoài cache đo cả cache hit; Timer chỉ bên trong nhánh miss có thể không thấy cache hit. **Không có một thứ tự “luôn đúng” cho mọi chính sách.**

Hãy công bố:

- Tên thời lượng đang đo: caller-observed request, business execution, downstream I/O, cache lookup hay attempt.
- Ranh giới bắt đầu và kết thúc.
- Có đo lời gọi bị từ chối/exception không.
- Có đo `proceed` khi target được bỏ qua hay lặp không.
- Outcome được phân loại tại tầng nào.
- Chi phí ghi metrics có được tính vào số đo hay không.

Thứ tự nhiều advice không nên suy ra từ thứ tự file hoặc tên class; phải dựa trên hợp đồng precedence và quy tắc weaving của công nghệ AOP đang dùng.

## 15. Quy trình thiết kế và kiểm thử

Một quy trình ngắn nhưng chặt chẽ:

1. **Viết mục tiêu đo:** “Elapsed time từ trước khi chuyển vào `buy` đến lúc nó return/throw qua wrapper”.
2. **Chọn pointcut đúng mức:** giới hạn theo loại join point, chữ ký và phạm vi nghiệp vụ, tránh chọn nhầm overload.
3. **Quyết định vị trí Timer trong chuỗi advice:** đo request, attempt hay business; ghi rõ phạm vi.
4. **Tiêm hoặc chọn đồng hồ monotonic:** `System::nanoTime` là lựa chọn thực tế cho elapsed interval trong cùng tiến trình.
5. **Lấy mốc trước `proceed`, tính hiệu trong `finally`:** không bị thiếu nhánh ngoại lệ.
6. **Giữ hợp đồng caller:** không đổi kết quả, không che lỗi target; định nghĩa chính sách khi metrics sink lỗi.
7. **Dùng sink thích hợp:** đơn vị đúng, nhãn có giới hạn, thread-safe, có chính sách overhead và backpressure.
8. **Kiểm thử đồng hồ giả:** thành công, ngoại lệ, bypass pointcut, sink lỗi, nested calls, nhiều advice, retry và async nếu có.
9. **Kiểm chứng instrumentation thật:** một unit test gọi trực tiếp `around()` chưa chứng minh proxy/weaving đã áp dụng đúng.
10. **Đo overhead production:** so sánh độ trễ/throughput có và không có advice dưới tải đại diện.

Ma trận kiểm thử gợi ý:

| Kịch bản | Kỳ vọng đo | Kỳ vọng đối với nghiệp vụ |
| --- | --- | --- |
| `buy(2)` thành công | Một elapsed observation | Caller nhận `"order:2"` |
| `buy(0)` ném lỗi | Vẫn chốt duration trong `finally` | Caller nhận `IllegalArgumentException` gốc |
| `health()` không khớp | Không tạo metric | Chạy bình thường |
| Metrics sink lỗi, target thành công | Theo chính sách fail-open của ví dụ: ghi lỗi sink riêng | Caller vẫn nhận kết quả |
| Metrics sink lỗi, target ném lỗi | Không để lỗi sink che lỗi target | Caller vẫn nhận ngoại lệ gốc |
| Around cache hit không gọi target | Có thể đo wrapper nếu khớp | Không giả định target đã chạy |
| Retry bao trong timer | Đo tổng các lần thử nếu timer ngoài retry | Số lần gọi theo chính sách retry |
| Timer nằm trong retry | Có thể đo từng attempt | Không tự làm thay đổi số attempt |
| Lời gọi đồng thời | Mốc bắt đầu mỗi lần độc lập | Không lẫn số đo giữa thread |
| Future trả pending | Đo thời gian return Future | Không đồng nhất với task completion |
| Gọi target trực tiếp bỏ qua proxy | Không kỳ vọng metric của proxy | Target vẫn chạy theo hợp đồng |

## 16. Lỗi thường gặp

- **Lấy `start` sau `proceed`:** không đo được thời gian thao tác gốc.
- **Chỉ ghi duration ở nhánh return thành công:** thiếu các lời gọi lỗi, gây sai thống kê latency.
- **Dùng đồng hồ lịch để làm elapsed timer:** phép đo có thể bị ảnh hưởng bởi điều chỉnh giờ hệ thống.
- **Đọc `nanoTime` rồi coi là timestamp Unix:** sai bản chất mốc gốc.
- **Nhầm nanosecond với millisecond:** báo cáo sai đơn vị.
- **Để mã metrics trong `finally` ném lỗi che exception gốc:** caller nhìn sai nguyên nhân thất bại.
- **Catch mọi `Throwable` rồi bỏ qua:** che những lỗi nghiêm trọng và làm mất quan sát sự cố.
- **Đặt mốc bắt đầu vào field của advice dùng chung:** race condition khi nhiều lời gọi đồng thời.
- **Cộng duration của cha và con rồi gọi tổng không trùng lặp:** double-counting.
- **Không xét retry và cache:** số lần ghi metric khác số lần target thực thi.
- **Coi Future đã return là tác vụ hoàn tất:** bỏ sót độ trễ và lỗi bất đồng bộ.
- **Đặt Timer ở tầng không đúng:** đo cả audit hoặc bỏ qua authorization denial ngoài ý định.
- **Ghi nhãn metric với dữ liệu không giới hạn như user ID:** cardinality cao và có thể rò rỉ thông tin.
- **Đo mọi helper rất ngắn mà không xem overhead:** số liệu có thể bị ảnh hưởng đáng kể bởi chính instrumentation.
- **Cho rằng định nghĩa pointcut là đủ:** lời gọi bỏ qua proxy/weaving không tự được đo.

## 17. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao phép ghi duration nên đặt trong `finally` thay vì ngay sau câu lệnh `proceed`?  
**Đáp án:** Nếu `proceed` ném ngoại lệ, câu lệnh sau nó trong cùng nhánh sẽ bị bỏ qua. `finally` cho phép chốt thời lượng khi phạm vi rời đi qua cả hai đường thông thường.

**Câu 2.** `System.nanoTime()` trả số lớn. Có thể chuyển nó trực tiếp thành ngày giờ theo lịch không?  
**Đáp án:** Không. Mốc gốc của nó tùy ý; chỉ dùng hiệu giữa hai lần đọc phù hợp để đo elapsed time.

**Câu 3.** Nếu metrics sink trong `finally` ném exception, điều gì có thể xảy ra?  
**Đáp án:** Nó có thể che lỗi nghiệp vụ hoặc ngăn caller nhận kết quả. Cần chính sách rõ và kiểm thử xử lý lỗi của sink.

**Câu 4.** Một lần `checkout` gọi hai helper, mỗi helper có timer. Có được cộng cả ba duration thành tổng latency không trùng lặp không?  
**Đáp án:** Không. Duration của checkout có thể bao gồm thời gian helper, vì thế cộng vào tạo trùng lặp.

**Câu 5.** Vì sao test dùng fake monotonic clock hữu ích hơn `sleep` rồi so sánh chính xác millisecond?  
**Đáp án:** Có thể kiểm tra phép trừ và đường chạy một cách tất định, không phụ thuộc tải hệ thống và scheduling.

**Câu 6.** Method trả `CompletableFuture` ngay, công việc còn chạy. `finally` của around trên method đo được gì?  
**Đáp án:** Thời gian đến lúc method trả Future, không tự đo trọn công việc; muốn đo task completion cần quan sát completion riêng.

**Câu 7.** Vì sao số `METRIC buy` không tự đồng nghĩa với số đơn thành công?  
**Đáp án:** `finally` chạy cả ở lần target ném lỗi hoặc wrapper bỏ qua target; ý nghĩa metric là số lượt được quan sát theo phạm vi, không phải business success.

## 18. Tổng kết

**Mẫu đo thời gian bằng điểm bắt đầu và khối dọn dẹp trong around advice** là: lấy mốc từ một đồng hồ thích hợp như `System.nanoTime()`, gọi `proceed` đúng hợp đồng, rồi tính `elapsed = end - start` trong `finally` trước khi ghi metric. Cấu trúc này thu được thời lượng ở cả nhánh normal và exceptional completion, nhưng chỉ an toàn khi **việc ghi metric không vô tình che lỗi hoặc thay kết quả nghiệp vụ**. Hãy định nghĩa rõ phạm vi đo, thứ tự các advice, retry/cache, các lời gọi lồng nhau và giới hạn bất đồng bộ; tiêm đồng hồ giả để test phép tính, đồng thời kiểm thử đường gọi proxy/weaving thật. Một timer có giá trị nhất khi số đo có **đơn vị đúng, ngữ nghĩa rõ, overhead được kiểm soát và hợp đồng caller được bảo toàn**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-007">Bắt, chuyển đổi hoặc truyền tiếp ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Bắt, chuyển đổi hoặc truyền tiếp ngoại lệ trong around advice của AOP

## 1. Mục tiêu và bối cảnh

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, **around advice** có thể bao quanh một join point được pointcut lựa chọn, thực hiện hành vi trước/sau và — nếu cơ chế AOP hỗ trợ — quyết định có tiếp tục đến phần xử lý gốc hay không. Thao tác tiếp tục thường được mô tả bằng khái niệm `proceed`.

Khi đoạn được tiếp tục ném một ngoại lệ, around advice có thể:

- **Truyền tiếp** ngoại lệ đó để caller nhìn thấy lỗi gốc.
- **Chuyển đổi** thành loại lỗi khác phù hợp ranh giới ứng dụng, thường giữ lỗi gốc làm `cause`.
- **Bắt và phục hồi** bằng giá trị dự phòng khi hợp đồng nghiệp vụ cho phép.
- **Thử lại** phần xử lý được tiếp tục, nếu việc lặp an toàn.
- **Bổ sung thông tin chẩn đoán** hoặc xử lý lỗi thứ cấp từ logging/cleanup.

Sức mạnh này tạo ra rủi ro: một thao tác gốc đã hoàn tất có thể bị báo lỗi chỉ vì advice chạy sau đó thất bại; một lỗi chính có thể bị che bởi lỗi kiểm toán; một retry sai thời điểm có thể nhân đôi tác dụng phụ.

**Bài học nằm ở phạm vi AOP tổng quát, cơ bản và nâng cao.** Những đoạn có từ khóa `around`, `pointcut` và `proceed` dưới đây là **mã giả**, không phải một cú pháp chuẩn chung cho mọi công cụ. Các ví dụ Java 17 được nêu riêng để quan sát luồng điều khiển mà không phụ thuộc một framework.

## 2. Around advice đứng ở đâu trong luồng thực thi?

Một hình dung:

```text
Caller
  |
  v
Around advice
  |-- before work
  |
  |-- proceed() ----------------------------+
  |                                         |
  |                                    Advice bên trong
  |                                         |
  |                                    Mã nghiệp vụ gốc
  |                                         |
  |<----------- kết quả hoặc ngoại lệ ------+
  |
  |-- xử lý kết quả/lỗi
  |-- finally nếu có
  v
Caller nhận kết quả hoặc ngoại lệ cuối cùng
```

**Điểm quan trọng:** `proceed()` thường tiếp tục tới **phần còn lại trong chuỗi advice và join point**, chứ không nhất thiết gọi trực tiếp phương thức nghiệp vụ. Vì vậy ngoại lệ ném ra từ `proceed` có thể bắt nguồn từ:

1. Thân phương thức nghiệp vụ.
2. Advice ở một lớp bên trong.
3. Cơ chế can thiệp hoặc adapter trung gian.
4. Tài nguyên được dùng trong phần thực thi được bao.

Không nên chỉ dựa vào việc bắt lỗi ở `proceed` để khẳng định “business failed”.

### 2.1. Mẫu cơ bản cho thao tác đồng bộ

```text
around selectedOperation(context):
    beforeStep(context)
    try:
        result = proceed(context)
        afterSuccessStep(result)
        return result
    catch error:
        reportFailure(error)
        throw error
    finally:
        finishStep()
```

Đây là mã giả. Cách viết trên **bắt cả lỗi từ `afterSuccessStep`**, không chỉ lỗi từ `proceed`. Nếu việc ghi metric thành công thất bại, `reportFailure` vẫn được gọi, dù nghiệp vụ đã chạy thành công. Phạm vi `try/catch` phải được thiết kế có chủ ý.

## 3. Truyền tiếp ngoại lệ gốc: rethrow/propagate

Khi aspect chủ yếu làm logging hoặc đo đạc, giữ nguyên ngoại lệ có thể là lựa chọn phù hợp:

```text
around operation:
    try:
        return proceed()
    catch original:
        recordErrorAccordingToPolicy(original)
        throw original
```

Về ý tưởng, `throw original` **ném lại cùng đối tượng lỗi**, để caller tiếp tục xử lý như hợp đồng gốc. Việc advice đã quan sát lỗi không làm mất nguyên nhân.

Java minh họa bằng wrapper thông thường:

```java
static String passthrough(java.util.concurrent.Callable<String> work)
        throws Exception {
    try {
        return work.call();
    } catch (Exception original) {
        System.out.println("observed:" + original.getClass().getSimpleName());
        throw original;
    }
}
```

Đây là mảnh Java hợp lệ khi đặt trong class; nó không phải một chương trình độc lập có `main`. `Callable.call()` chỉ đóng vai trò continuation minh họa, **không phải API around advice của một engine AOP**.

Ưu điểm truyền tiếp:

- Caller không phải thay đổi đường xử lý lỗi vốn có.
- Loại và thông điệp lỗi gốc được giữ.
- Có thể quan sát thêm mà không biến lỗi thành một tình trạng nghiệp vụ khác.
- Tránh vô tình biến lỗi input thành lỗi hệ thống, hoặc ngược lại.

Nhược điểm tiềm tàng: nếu hợp đồng ứng dụng **cần che lỗi hạ tầng** khỏi caller, truyền tiếp nguyên dạng có thể làm lộ chi tiết hoặc buộc tầng bên ngoài phụ thuộc API của nhà cung cấp. Khi đó nên cân nhắc chuyển đổi.

### 3.1. Nếu logger cũng ném lỗi thì sao?

Giả sử `recordErrorAccordingToPolicy` ném `AuditFailure` trước khi `throw original` được thực hiện. Lỗi gốc có thể bị che hoàn toàn.

Với telemetry tùy chọn, có thể quyết định lỗi logging không được thay thế lỗi nghiệp vụ. Với audit bắt buộc, yêu cầu có thể khác. Không có chính sách chung đúng cho mọi trường hợp; cần định nghĩa từ hợp đồng, không dựa vào việc `catch` có sẵn.

## 4. Chuyển đổi ngoại lệ: translation và wrapping

**Chuyển đổi ngoại lệ** nghĩa là ranh giới ứng dụng công bố một loại lỗi có ý nghĩa hơn thay cho lỗi thấp tầng.

Ví dụ: dịch lỗi truy cập tài nguyên thành lỗi “dịch vụ tạm thời không sẵn sàng”:

```text
around applicationBoundary:
    try:
        return proceed()
    catch LowLevelUnavailable original:
        throw ApplicationUnavailable("cannot complete request", cause=original)
```

Đoạn trên là mã giả, không mang cú pháp constructor chung. Trong Java, cách giữ nguyên nhân gốc có thể như sau:

```java
try {
    return remoteClient.fetch();
} catch (java.io.IOException original) {
    throw new IllegalStateException("remote data unavailable", original);
}
```

Đây là mảnh mã giả định có `remoteClient.fetch()`. Constructor của `IllegalStateException` nhận `original` làm cause, nên caller hoặc hệ thống chẩn đoán có thể gọi `getCause()`.

So sánh:

| Cách xử lý | Caller nhận | Thông tin nguyên nhân |
| --- | --- | --- |
| `throw original` | Lỗi gốc | Vẫn là đối tượng ban đầu |
| `throw new ApplicationFailure("...", original)` | Lỗi ứng dụng mới | Có cause trỏ về lỗi gốc |
| `throw new ApplicationFailure("...")` | Lỗi ứng dụng mới | Có thể mất liên hệ gốc |
| `return fallback` | Giá trị bình thường | Lỗi bị xử lý nội bộ, phải có hợp đồng |
| `retry proceed()` | Kết quả từ lần tiếp theo hoặc lỗi | Có nguy cơ lặp phần gốc |

### 4.1. Translation không phải thay mọi lỗi bằng một lỗi chung

Một anti-pattern:

```text
catch anyError:
    throw ServiceUnavailable()
```

Nó có thể biến cả lỗi bảo mật, thiếu dữ liệu, lỗi lập trình và lỗi cơ sở hạ tầng thành một thông báo duy nhất. Khi đó caller khó phân loại lỗi cần sửa input, lỗi cần đăng nhập và lỗi có thể retry.

Nên ánh xạ **những loại lỗi cụ thể có ngữ nghĩa đã biết**; với các lỗi chưa biết, cân nhắc truyền tiếp hoặc xử lý tại ranh giới có đầy đủ thông tin hơn.

### 4.2. Cause được giữ trong hệ thống không có nghĩa phải đưa mọi chi tiết cho người dùng

Một hệ thống có thể lưu cause và stack trace trong kênh quan sát có kiểm soát, đồng thời trả thông điệp an toàn và mã lỗi ổn định cho client. Không nên in mật khẩu, token, dữ liệu thanh toán hoặc thông tin truy vấn nhạy cảm trong phản hồi.

## 5. Bắt và phục hồi bằng fallback

Around advice có thể biến ngoại lệ thành **kết quả dự phòng**, nhưng chỉ khi chính sách cho phép.

Ví dụ hợp lý: thao tác tra cứu đề xuất tùy chọn được phép trả dữ liệu cache còn hiệu lực nếu nguồn bên ngoài tạm lỗi.

```text
around optionalRead:
    try:
        return proceed()
    catch TemporaryReadFailure:
        return validCachedValue
```

Không nên áp dụng máy móc cho:

- Chuyển tiền chưa biết trạng thái.
- Tạo đơn hàng đã có side effect.
- Thay đổi phân quyền.
- Ghi audit bắt buộc.
- Cập nhật kho hoặc ghi dữ liệu không idempotent.

**Fallback không chứng minh phần gốc chưa chạy.** Nếu lỗi xảy ra sau khi phần gốc đã thực hiện một phần hoặc đã tạo side effect, caller nhận fallback có thể hiểu sai hệ thống.

Một fallback hợp lệ phải:

1. Đúng kiểu và hợp đồng trả về.
2. Đúng ngữ nghĩa nghiệp vụ, không giả mạo thành công.
3. Có quy tắc độ mới và tính nhất quán nếu dùng cache.
4. Có cách nhận biết degraded mode khi cần.
5. Có kiểm thử phục hồi bình thường và thất bại lặp lại.

## 6. Lỗi trước và sau `proceed` khác nhau thế nào?

Xét:

```text
around operation:
    auditStart()            // lỗi ở đây xảy ra trước phần gốc
    result = proceed()      // lỗi từ chuỗi bên trong
    auditSuccess(result)    // lỗi ở đây sau khi đã có result
    return result
```

Các ranh giới có hậu quả khác nhau:

| Thời điểm lỗi | Phần gốc đã chạy? | Rủi ro |
| --- | --- | --- |
| `auditStart` ném lỗi | Chưa, nếu chặn trước `proceed` | Caller có thể nhận lỗi phụ trợ thay vì chạy nghiệp vụ |
| `proceed` ném lỗi | Có thể có hoặc chưa | Cần phân biệt nguồn và trạng thái side effect |
| `auditSuccess` ném lỗi | Phần được tiếp tục đã trả bình thường | Caller nhận lỗi dù phần gốc có thể hoàn tất |
| `finally` ném lỗi | Tùy đường đi trước đó | Che kết quả/lỗi đang lan truyền |

Ví dụ thực tế: một lệnh cập nhật đơn hàng thành công, nhưng advice ghi metric sau đó ném ngoại lệ. Nếu caller retry chỉ vì nhìn thấy exception, lệnh cập nhật có thể bị thực hiện lại.

### 6.1. Thu hẹp phạm vi `try/catch`

Để chỉ bắt lỗi thoát từ phần được tiếp tục:

```text
around operation:
    auditStartAccordingToPolicy()

    try:
        result = proceed()
    catch continuationError:
        auditContinuationFailureAccordingToPolicy(continuationError)
        throw continuationError

    auditSuccessAccordingToPolicy(result)
    return result
```

Điều này không tự xử lý lỗi của `auditStart` và `auditSuccess`; nó chỉ làm ranh giới rõ ràng hơn. Nếu cần phân biệt lỗi từ business và advice con, còn phải có cách xác định nguồn ở tầng tương ứng.

## 7. Ví dụ Java 17 độc lập: propagate, translate, fallback

Java thông thường không tự cung cấp cú pháp khai báo aspect và pointcut. Để học cơ chế kiểm soát ngoại lệ, ta dùng wrapper thủ công giống một around advice và một đối tượng nghiệp vụ.

Lưu tệp `AroundExceptionFlowDemo.java`:

```java
public class AroundExceptionFlowDemo {
    interface Operation {
        String run(String mode);
    }

    static final class BusinessFailure extends RuntimeException {
        BusinessFailure(String message) {
            super(message);
        }
    }

    static final class ApplicationFailure extends RuntimeException {
        ApplicationFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class BusinessOperation implements Operation {
        int calls;

        @Override
        public String run(String mode) {
            calls++;
            System.out.println("business:" + mode);

            if (!"ok".equals(mode)) {
                throw new BusinessFailure("backend:" + mode);
            }

            return "OK:" + mode;
        }
    }

    static String around(Operation target, String mode) {
        System.out.println("around:enter:" + mode);

        try {
            String result;

            try {
                result = target.run(mode);
            } catch (BusinessFailure original) {
                System.out.println("around:caught:" + original.getMessage());

                if ("translate".equals(mode)) {
                    throw new ApplicationFailure(
                            "operation unavailable", original
                    );
                }

                if ("fallback".equals(mode)) {
                    System.out.println("around:recover:" + mode);
                    return "CACHED";
                }

                throw original;
            }

            System.out.println("around:success:" + result);
            return result;
        } finally {
            System.out.println("around:exit:" + mode);
        }
    }

    public static void main(String[] args) {
        BusinessOperation target = new BusinessOperation();

        for (String mode : new String[] {
                "ok", "propagate", "translate", "fallback"
        }) {
            System.out.println("case:" + mode);

            try {
                String result = around(target, mode);
                System.out.println("caller:result:" + result);
            } catch (RuntimeException error) {
                System.out.println("caller:error:"
                        + error.getClass().getSimpleName()
                        + ":" + error.getMessage());

                if (error.getCause() != null) {
                    System.out.println("caller:cause:"
                            + error.getCause().getClass().getSimpleName()
                            + ":" + error.getCause().getMessage());
                }
            }
        }

        System.out.println("business:calls:" + target.calls);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac AroundExceptionFlowDemo.java
java AroundExceptionFlowDemo
```

Kết quả:

```text
case:ok
around:enter:ok
business:ok
around:success:OK:ok
around:exit:ok
caller:result:OK:ok
case:propagate
around:enter:propagate
business:propagate
around:caught:backend:propagate
around:exit:propagate
caller:error:BusinessFailure:backend:propagate
case:translate
around:enter:translate
business:translate
around:caught:backend:translate
around:exit:translate
caller:error:ApplicationFailure:operation unavailable
caller:cause:BusinessFailure:backend:translate
case:fallback
around:enter:fallback
business:fallback
around:caught:backend:fallback
around:recover:fallback
around:exit:fallback
caller:result:CACHED
business:calls:4
```

### 7.1. Phân tích

- `ok`: phần gốc trả kết quả, wrapper truyền tiếp kết quả và chạy `finally`.
- `propagate`: wrapper bắt `BusinessFailure` để ghi thông tin rồi ném lại **cùng lỗi**.
- `translate`: wrapper tạo `ApplicationFailure` mới, giữ `BusinessFailure` làm `cause`.
- `fallback`: wrapper bắt lỗi và trả `CACHED`. Đây là **hành vi phục hồi giả định là được phép**; không nên áp dụng cho mọi nghiệp vụ.
- `business:calls:4`: cả bốn kịch bản đều đã gọi phần gốc **đúng một lần**, kể cả fallback.

Ví dụ cố ý không có weaving hay pointcut thật. Nó minh họa cách `try/catch/finally` hoạt động khi được dùng để mô phỏng các nhánh của around advice.

## 8. Rethrow, wrap, suppress và swallow không giống nhau

| Hành vi | Tác động |
| --- | --- |
| **Rethrow** | Giữ lỗi ban đầu cho caller |
| **Wrap** | Caller nhận lỗi mới, có thể mang cause gốc |
| **Translate** | Chuyển ý nghĩa lỗi sang hợp đồng tầng ứng dụng |
| **Add suppressed** | Giữ lỗi thứ cấp bên cạnh lỗi chính |
| **Swallow** | Nuốt lỗi mà không ném lại; có thể làm caller tưởng đã thành công |
| **Recover/fallback** | Trả giá trị thay thế có chủ đích |
| **Retry** | Có thể khiến phần gốc chạy thêm lần nữa |

Bắt được một lỗi không tự làm thay đổi trạng thái nghiệp vụ. Việc trả fallback hay wrap lỗi là một quyết định riêng; nó phải dựa vào yêu cầu thay vì thói quen “catch tất cả để hệ thống không crash”.

### 8.1. Cause của ngoại lệ mới

Java hỗ trợ tạo ngoại lệ mới kèm lỗi nguyên nhân:

```java
RuntimeException translated =
        new IllegalStateException("application error", original);
```

Đây là mảnh mã giả định `original` là một `Throwable`. `translated.getCause()` trỏ đến `original`. Có cause giúp truy vết nhưng không có nghĩa mọi tầng bên ngoài phải hiển thị nguyên stack trace cho end user.

### 8.2. Suppressed cho lỗi phụ

Nếu business đã ném lỗi `B` và cleanup lại ném lỗi `C`, có thể ưu tiên truyền tiếp `B` rồi gắn `C` là suppressed:

```java
try {
    doOriginalWork();
} catch (RuntimeException original) {
    try {
        recordFailure(original);
    } catch (RuntimeException reportingError) {
        original.addSuppressed(reportingError);
    }
    throw original;
}
```

Đây là mảnh mã Java, giả định hai phương thức trợ giúp tồn tại. Chính sách này phù hợp khi `recordFailure` là **hành vi phụ** không được phép che lỗi chính; một hoạt động audit bắt buộc có thể cần chính sách khác.

Không gọi `addSuppressed` để tự suppress chính ngoại lệ đó vào nó: Java không cho phép self-suppression.

## 9. Lỗi của `finally` có thể che lỗi gốc

Mảnh Java:

```java
static void example() {
    try {
        throw new IllegalArgumentException("original");
    } finally {
        throw new IllegalStateException("cleanup");
    }
}
```

Nếu gọi hàm này, caller nhận `IllegalStateException("cleanup")`, chứ không tự động nhận `IllegalArgumentException("original")` trong suppressed. Khi đưa dọn dẹp vào around advice, phải xác định nên xử lý như thế nào nếu cleanup thất bại trong lúc đã có lỗi chính.

### 9.1. Đối chiếu với `try-with-resources`

Java `try-with-resources` có cơ chế giữ ngoại lệ từ thân `try` làm **primary** và đưa lỗi đóng tài nguyên vào suppressed khi cả hai cùng thất bại.

```java
try (SomeResource resource = acquireResource()) {
    performOperation();
}
```

Đây là mảnh minh họa với kiểu/phương thức giả định. Không được suy từ ngữ nghĩa của `try-with-resources` rằng một `finally` thủ công trong advice cũng tự lưu lỗi phụ tương tự.

### 9.2. Cleanup có thể là hành vi bắt buộc

Nếu một lỗi cleanup chứng tỏ hệ thống mất ràng buộc nghiêm trọng, không nên chỉ giấu nó để ưu tiên lỗi business. Cần phân loại mức độ và quyết định rõ lỗi nào caller nhận, còn lỗi nào lưu ở cause/suppressed hay kênh giám sát.

## 10. Bắt lỗi từ `proceed` không đủ để xác định nguồn lỗi

Xét chuỗi:

```text
OuterAudit(
    InnerMetrics(
        BusinessOperation
    )
)
```

`BusinessOperation` trả kết quả bình thường, nhưng `InnerMetrics` ném `MetricFailure` trong phần sau. Khi `OuterAudit` gọi `proceed`, nó sẽ quan sát một ngoại lệ.

Với outer, đây là **continuation failure**, không mặc nhiên là “business failure”. Nếu outer chuyển tất cả lỗi thành `DomainFailure` thì nó có thể khiến một lỗi logging phụ trợ trông giống lỗi nghiệp vụ.

Kiểm thử nên ghi rõ:

- Body nghiệp vụ có chạy không?
- Body đã trả về bình thường chưa?
- Lỗi đến từ advice nào?
- Lỗi đã bị chuyển đổi bao nhiêu lớp?
- Caller nhận loại nào và có truy vết được cause không?

## 11. Thứ tự của nhiều aspect ảnh hưởng đến lỗi quan sát

Một outer advice có thể nhận giá trị/lỗi sau khi inner đã chuyển đổi:

| Inner xử lý | Outer nhận từ `proceed` |
| --- | --- |
| Ném lại lỗi `BusinessFailure` | `BusinessFailure` |
| Bọc thành `ApplicationFailure(cause=BusinessFailure)` | `ApplicationFailure` |
| Trả fallback | Kết quả bình thường |
| Ném lỗi metrics sau thành công | `MetricFailure` |

Ngược lại, lỗi do outer tạo ra **sau khi inner kết thúc** không thể được inner bắt lại từ quá khứ. Vì thế khi thiết kế audit/transaction/retry/error translation cần biết thứ tự có hiệu lực giữa các advice.

**Không dựa vào thứ tự viết trong file như cam kết toàn cục.** Công cụ AOP có thể có quy tắc precedence riêng; phải đọc hợp đồng công cụ và kiểm thử tình huống nhiều advice cùng khớp.

## 12. Retry là hành vi thay đổi luồng mạnh

Mã giả:

```text
around operation:
    try:
        return proceed()
    catch TransientFailure:
        return proceed()
```

Một số công cụ cho phép gọi lại `proceed`; các công cụ khác có giới hạn riêng. Nếu gọi được nhiều lần, phần gốc **có thể thực thi nhiều lần**.

Nguy cơ quan trọng:

```text
Lần 1:
  business hoàn thành và đã ghi đơn
  inner advice ghi metric ném lỗi

Outer catch:
  hiểu nhầm là lỗi tạm thời của nghiệp vụ
  gọi proceed lần 2

Lần 2:
  business có thể lại ghi đơn
```

Retry an toàn cần:

1. Phân loại lỗi phù hợp, không retry mọi `Exception`.
2. Biết thao tác có side effect và có idempotency hay không.
3. Giới hạn số lần thử, thời gian, backoff và điều kiện dừng.
4. Truy vết mỗi lần gọi để điều tra.
5. Phân biệt lỗi phát sinh **trước**, **trong** và **sau** phần gốc.
6. Kiểm thử cả “business đã thành công nhưng post-processing thất bại”.

Một quanh-advice chỉ muốn giám sát không nên tự phát động retry mà chưa được thiết kế nghiệp vụ chấp thuận.

## 13. Checked và unchecked exceptions trong Java

Trong Java, checked exception thường cần khai báo hoặc được xử lý; `RuntimeException` và `Error` thuộc các nhánh unchecked. Một advice quanh lời gọi Java có thể gặp giới hạn về việc ném checked exception qua chữ ký phương thức không khai báo nó.

Ví dụ:

```java
interface Lookup {
    String find(String key);
}
```

Nếu around advice muốn ném `java.io.IOException` mới qua ranh giới `find`, việc có hợp lệ hay bị bọc phụ thuộc ngôn ngữ AOP, cơ chế weaving hoặc proxy và hợp đồng của chữ ký. Không có quy tắc chung rằng mọi checked exception từ advice đều xuất hiện nguyên dạng ở caller.

Một phương án chuyển đổi có chủ đích:

```java
try {
    return loadExternal();
} catch (java.io.IOException original) {
    throw new IllegalStateException("external data unavailable", original);
}
```

Đoạn giả định có `loadExternal()`. Nó giữ cause trong một loại unchecked mới, nhưng vẫn **đổi hợp đồng lỗi** nên cần cân nhắc.

### 13.1. Đừng nuốt mọi `Throwable`

`catch(Throwable)` có thể chặn cả lỗi môi trường nghiêm trọng. Nuốt `OutOfMemoryError` rồi trả `null` không thể coi là chiến lược phục hồi đáng tin. Bắt những nhóm lỗi đã xác định, và nếu cơ chế buộc phải nhận `Throwable` thì phải phân loại và truyền tiếp có ý thức.

### 13.2. Với reflection có thể có lỗi bọc kỹ thuật

Nếu interceptor dùng `Method.invoke`, ngoại lệ từ phần đích thường được bọc trong `InvocationTargetException`. Khi đó có thể cần đọc `getCause()`. Đây là thuộc tính của cách triển khai dùng Java reflection, không phải quy tắc rằng mọi engine AOP đều để ngoại lệ reflection đi qua caller.

## 14. Lỗi bất đồng bộ nằm ngoài một `catch` đồng bộ

Giả sử phương thức trả về `CompletableFuture<Result>`:

```text
Caller
  |
  v
around -> proceed() trả Future chưa hoàn tất
  |
  v
around trả Future
  |
  v
tác vụ chạy về sau -> Future completed / failed
```

Một `try/catch` đồng bộ quanh `proceed()` chỉ thấy lỗi ném **trong chính lời gọi ấy**. Lỗi phát sinh về sau có thể được lưu trong future và xuất hiện khi future hoàn tất đặc biệt.

Muốn chuyển đổi lỗi bất đồng bộ cần quan sát **completion stage** tương ứng. Mảnh Java minh họa:

```java
CompletableFuture<String> normalized = original.handle((value, error) -> {
    if (error == null) {
        return value;
    }
    throw new IllegalStateException("async operation failed", error);
});
```

Giả định `original` là `CompletableFuture<String>` đã khai báo và có import phù hợp. `normalized` là **stage mới**; khi callback ném lỗi, stage đó thất bại theo ngữ nghĩa API. `error` có thể là một lớp bọc tùy đường completion; cần xem xét cause chain.

Kiểm thử ít nhất: hoàn tất bình thường, hoàn tất thất bại, hủy, timeout, callback lỗi, completion trên luồng khác và ngữ cảnh của từng request.

## 15. Ngoại lệ và side effect không đồng nhất

Đừng dùng một ngoại lệ từ quanh-advice để kết luận:

- Giao dịch đã rollback.
- Đơn hàng chưa được tạo.
- Thanh toán chưa được gửi.
- Một hệ thống bên ngoài chưa xử lý yêu cầu.
- Một task bất đồng bộ chắc chắn chưa hoàn tất.

Khi advice ném lỗi **sau** phần nghiệp vụ, side effect có thể đã được xác nhận bền vững. Khi advice ném lỗi **trước**, phần gốc có thể chưa chạy. Khi `proceed` ném lỗi, một phần xử lý có thể đã xảy ra.

Ở hệ thống cần retry, hãy có mã yêu cầu/idempotency key hoặc cách tra cứu trạng thái để caller không phải suy đoán từ kiểu exception đơn thuần.

## 16. Bảo mật, kiểm toán và lỗi phụ trợ

Không có quy tắc chung rằng lỗi logging luôn có thể bỏ qua. Cần phân loại mối quan tâm:

| Aspect | Nếu chính advice gặp lỗi | Điều cần cân nhắc |
| --- | --- | --- |
| Metrics tùy chọn | Có thể giữ nguyên kết quả/lỗi business | Tránh telemetry làm hỏng đường nghiệp vụ |
| Logging chẩn đoán | Có thể ghi ở kênh phụ hoặc giới hạn lỗi | Không để logger gây đệ quy |
| Audit bắt buộc | Có thể phải từ chối hoặc xác nhận trạng thái | Đảm bảo yêu cầu pháp lý/nghiệp vụ |
| Authorization | Thường không được ngầm cho phép khi hệ thống kiểm tra lỗi | Fail-open/fail-closed phải có hợp đồng |
| Error translator | Giữ cause và mã lỗi ổn định | Không rò rỉ dữ liệu nhạy cảm |
| Retry | Có thể gọi lại operation | Idempotency, phân loại lỗi |
| Cleanup | Có thể sinh lỗi thứ cấp | Primary/cause/suppressed |

Không tự động ghi toàn bộ tham số trong error handler. Chúng có thể chứa token, bí mật hoặc dữ liệu cá nhân; ngay cả `toString()` cũng có thể tốn chi phí hoặc ném lỗi. Thay vào đó dùng correlation ID và các trường đủ cho truy vết.

## 17. Kiểm thử riêng từng nhánh của around advice

Một chiến lược hiệu quả là viết các ca độc lập với kết quả mong muốn:

| Ca | Business được gọi? | Around làm gì? | Caller mong đợi |
| --- | --- | --- | --- |
| Thành công | 1 lần | Chuyển tiếp result | Result ban đầu |
| Lỗi cần giữ | 1 lần | Rethrow | Cùng lỗi gốc |
| Lỗi cần dịch | 1 lần | Wrap | Loại lỗi mới, cause đúng |
| Lỗi có fallback | 1 lần | Return fallback | Giá trị hợp lệ |
| Before lỗi | 0 lần | Không proceed | Lỗi before |
| After lỗi | Business đã chạy | Bước sau ném | Không hiểu nhầm business chưa chạy |
| Cleanup đồng thời lỗi | Tùy trường hợp | Giữ thông tin lỗi chính/phụ | Có primary/cause/suppressed theo hợp đồng |
| Inner advice lỗi | Tùy trường hợp | Outer quan sát | Không gán nhãn sai nguồn |
| Retry | Có thể nhiều lần | Gọi proceed lại | Số lần hợp lệ, không lặp side effect |
| Async failure | Tác vụ chạy về sau | Xử lý completion | Đúng stage và cause |

### 17.1. Test identity của lỗi truyền tiếp

Với Java, khi hợp đồng yêu cầu rethrow đúng đối tượng, kiểm tra bằng identity thay vì chỉ so message:

```java
RuntimeException original = new IllegalArgumentException("bad");
// Sau khi gọi wrapper ở một bài test:
boolean sameObject = (caught == original);
```

Đây là mảnh mã minh họa, giả định `caught` là lỗi đã thu được. Với wrap, hãy kiểm tra `translated.getCause() == original` nếu đó là thiết kế.

### 17.2. Test đường không khớp pointcut

Một error translator dành riêng cho hoạt động thanh toán không được vô tình đổi lỗi của logger hoặc công việc hạ tầng khác. Cần kiểm thử cả tập join point **không nên can thiệp**, cũng như cách công cụ chặn lời gọi nội bộ.

### 17.3. Test thứ tự advice

Dựng hai advice giả (một cái dịch lỗi, một cái ghi audit) với thứ tự được xác định rõ; kiểm tra audit nhìn thấy lỗi trước hay sau dịch. Điều này giúp phát hiện lỗi phân loại khi triển khai các aspect lồng nhau.

## 18. Đồng thời, đệ quy và reentrancy

Một around advice xử lý lỗi theo từng invocation không nên lưu `currentError` vào một biến mutable dùng chung mà không quản lý. Hai request đồng thời có thể ghi đè cause, số lần retry hoặc correlation ID của nhau.

Với đệ quy, nhiều frame cùng kiểu join point có thể đang tồn tại. Giữ counter retry hoặc context theo từng lời gọi, không giả định chỉ một lỗi đang được xử lý tại mọi thời điểm.

Nếu advice báo lỗi qua một logger mà logger lại khớp cùng pointcut, có thể phát sinh vòng gọi advice. Giới hạn phạm vi pointcut hoặc dùng cơ chế chống tái nhập được thiết kế đúng; một cờ global đơn giản có thể chặn nhầm những invocation độc lập.

## 19. Quy trình thiết kế chính sách ngoại lệ

1. **Xác định hợp đồng nghiệp vụ:** thao tác nào, side effect gì, lỗi nào caller cần biết?
2. **Chọn đúng join point và pointcut:** method call hay execution, phạm vi weaving nào?
3. **Liệt kê các nguồn lỗi:** before, continuation, inner advice, after, cleanup, async completion.
4. **Xác định nguyên tắc ưu tiên:** lỗi nào là primary và có cần cause/suppressed không?
5. **Chỉ định nhóm lỗi được bắt:** tránh catch-all có thể nuốt bug và lỗi nghiêm trọng.
6. **Chọn rethrow/translate/fallback/retry theo từng nhóm:** không dùng một chính sách cho mọi lỗi.
7. **Thiết kế việc ghi thông tin:** không để logging tùy chọn che lỗi chính; tránh rò rỉ dữ liệu.
8. **Quy định checked/unchecked:** phù hợp Java và cơ chế AOP đang dùng.
9. **Kiểm thử đường thực thi thật:** cả lời gọi nội bộ, nhiều advice, tình huống lỗi và side effect.
10. **Kiểm thử concurrent/async:** context và lỗi thuộc đúng invocation/stage.

## 20. Những sai lầm thường gặp

**“Catch được lỗi là có thể trả kết quả mặc định.”** Không. Fallback có thể che side effect hoặc thông báo thành công giả.

**“Catch của proceed luôn nhận lỗi nghiệp vụ.”** Không. Inner advice hoặc interception cũng có thể là nguồn.

**“Wrap bằng exception mới nhưng không lưu cause cũng được.”** Có thể làm mất dữ liệu chẩn đoán quan trọng.

**“Business ném lỗi thì after/cleanup không thể ném lỗi thứ hai.”** Có thể, và lỗi thứ hai có thể che lỗi chính.

**“Caller nhận exception thì business chắc chắn chưa chạy.”** Sai với lỗi trong after step.

**“Retry mọi exception sẽ cải thiện độ tin cậy.”** Có thể lặp side effect không an toàn.

**“Checked exception từ advice truyền nguyên dạng trong mọi công cụ.”** Không có cam kết chung.

**“Một around catch sẽ tự xử lý mọi lỗi CompletableFuture.”** Catch đồng bộ khác xử lý lỗi completion bất đồng bộ.

**“Thứ tự nhiều aspect luôn là thứ tự khai báo.”** Cần xác minh precedence thực tế.

**“Logger an toàn vì chỉ quan sát.”** Logger có thể ném lỗi, lộ dữ liệu và kích hoạt lại pointcut.

## 21. Câu hỏi luyện tập

**1.** `proceed` ném `BusinessFailure`, around advice `throw original`. Caller nhận gì?

Thông thường caller nhận cùng đối tượng `BusinessFailure` nếu không có một advice ngoài khác chuyển đổi tiếp.

**2.** Around advice tạo `ApplicationFailure("unavailable", original)`. Khác gì với `ApplicationFailure("unavailable")`?

Dạng đầu giữ `original` làm cause; dạng sau có thể làm mất liên hệ trực tiếp đến lỗi gốc.

**3.** Nghiệp vụ tạo đơn thành công, after step ném `MetricFailure`. Có nên retry ngay không?

Không thể kết luận. Phải xác minh đơn đã tạo và có idempotency hay không; lỗi metric không có nghĩa nghiệp vụ chưa thực hiện.

**4.** Inner advice đã đổi `IOException` thành `DomainFailure`. Outer bắt được lỗi nào từ `proceed`?

Outer nhìn thấy `DomainFailure` do inner đưa ra, trừ khi có tầng khác thay đổi tiếp. Có thể truy về `IOException` nếu cause được giữ.

**5.** Một Future hoàn tất lỗi sau khi method đã trả về. Catch đồng bộ quanh `proceed` có chắc bắt được không?

Không. Cần xử lý completion của Future/stage tương ứng.

**6.** Vì sao catch bao cả `logSuccess` dễ sai?

Vì lỗi từ chính logging sẽ được phân loại như lỗi của `proceed` hoặc business, dù phần gốc có thể thành công.

**7.** Nếu body đã ném lỗi chính và cleanup cũng ném, xử lý thế nào?

Xác định chính sách ưu tiên; có thể giữ lỗi chính và gắn lỗi cleanup bằng suppressed khi phù hợp, không để lỗi thứ cấp vô tình che lỗi chính.

## 22. Checklist trước khi triển khai

- [ ] Phân biệt rethrow, wrap, translate, fallback và retry.
- [ ] Catch chỉ những loại lỗi đã định nghĩa hợp đồng.
- [ ] Biết `proceed` đang bao những tầng advice nào.
- [ ] Đặt `try/catch` đúng phạm vi nguồn lỗi cần phân loại.
- [ ] Bảo toàn cause/suppressed theo chính sách.
- [ ] Không để logging/metrics tùy chọn vô tình che lỗi chính.
- [ ] Phân biệt lỗi trước và sau khi tạo side effect.
- [ ] Fallback hợp lệ và không tạo thành công giả.
- [ ] Retry an toàn, có giới hạn và idempotency.
- [ ] Checked/unchecked tương thích với Java/công cụ AOP.
- [ ] Kiểm thử từng đường lỗi và thứ tự nhiều aspect.
- [ ] Kiểm thử target không khớp, self-invocation và giới hạn weaving.
- [ ] Xử lý đúng completion bất đồng bộ.
- [ ] Không làm lộ bí mật từ args/exception trong log.
- [ ] Tránh lưu lỗi hoặc retry state chung giữa các invocation.

## 23. Kết luận

**Around advice có thể bắt, chuyển đổi hoặc truyền tiếp ngoại lệ, nhưng mỗi lựa chọn làm thay đổi hợp đồng tại ranh giới được bao quanh.** Truyền tiếp phù hợp khi cần giữ lỗi gốc; chuyển đổi phù hợp khi cần mô hình lỗi ổn định nhưng phải giữ nguyên nhân; fallback và retry chỉ hợp lệ khi nghiệp vụ và side effect cho phép.

Thiết kế đáng tin cậy cần xác định đúng **nguồn lỗi, thời điểm xảy ra, thứ tự advice và trạng thái thực tế của phần gốc**. Chỉ khi đó around advice mới thêm được giá trị xuyên suốt mà không làm caller hiểu sai kết quả nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-008">Giới hạn kiểu trả về và tính tương thích của advice</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn kiểu trả về và tính tương thích của advice bao quanh

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), **around advice** (lời khuyên bao quanh) có thể đặt hành vi trước và sau một **join point**; ở những cơ chế hỗ trợ, nó còn có thể quyết định có gọi tiếp thao tác đích (*proceed*) hay không, chuyển đổi kết quả hoặc xử lý ngoại lệ. Tuy vậy, **khả năng trả về một giá trị từ advice không đồng nghĩa với quyền trả về bất kỳ loại dữ liệu nào cho người gọi**.

Nếu một phương thức khai báo trả về `String`, `int`, `void`, một kiểu generic hoặc một đối tượng bất đồng bộ, kết quả mà chuỗi advice cuối cùng đưa tới caller phải phù hợp với **hợp đồng kiểu và ngữ nghĩa của chính join point**. Một giá trị sai kiểu có thể gây `ClassCastException`, lỗi unboxing, lỗi chỉ phát sinh muộn ở caller, hoặc làm sai kết quả nghiệp vụ dù code chạy được.

**Nguyên tắc cốt lõi:** Around advice có khả năng *điều khiển outcome* khi công cụ cho phép, nhưng outcome mới vẫn phải tương thích với chữ ký thực thi, quy tắc kiểu của ngôn ngữ và hợp đồng của API. Phải phân biệt **loại giá trị ở code advice**, **loại trả về của target**, **giá trị thực sự tới caller** và **giới hạn của cơ chế AOP đang dùng**.

> Bài này trình bày AOP cơ bản và nâng cao nói chung, không phụ thuộc một framework. Các từ `around`, `proceed`, `return value`, `join point` và `weaving` mô tả các khái niệm; cú pháp khai báo advice, quy tắc binding, quyền thay kết quả và thời điểm kiểm tra kiểu phải xác minh theo công cụ triển khai thực tế.

## 1. Ba tầng cần phân biệt khi nói “kiểu trả về”

Xét phương thức Java:

```java
// Một khai báo minh họa:
String format(String input);
```

Ta có ba tầng khác nhau:

1. **Kiểu trả về của phương thức đích:** `String`, thể hiện API mà caller sử dụng.
2. **Kiểu mà around advice tự khai báo hoặc cơ chế dispatch nhận:** có thể là `Object` trong API interceptor tổng quát; có thể là một kiểu cụ thể hoặc giá trị trừu tượng khác.
3. **Kiểu và giá trị thực tế được chuyển tới caller:** ví dụ một object `String`, `null`, hoặc một object sai kiểu khiến runtime không chuyển thành `String` được.

Nếu interceptor có chữ ký `Object intercept(...)`, việc hàm này biên dịch được với `return 42;` **không biến `42` thành một `String` hợp lệ** cho phương thức `format`. Thông thường, runtime/proxy sẽ ép kiểu kết quả trả về trước khi chuyển nó tới caller. Ở một cơ chế weaving khác, lỗi có thể được phát hiện sớm hoặc muộn hơn, nhưng **hợp đồng kết quả của phương thức đích không biến mất**.

### Kết quả là một trong hai outcome

Với một thao tác đồng bộ, hãy hình dung:

```text
Outcome<T> =
    Return(T value)
    OR
    Throw(Throwable error)
```

Đây là ký pháp tư duy, **không phải khai báo Java độc lập**. Around advice có thể giữ nguyên một outcome hoặc biến đổi nó nếu được hỗ trợ:

```text
Return(original) -> Return(transformed)
Throw(error)     -> Throw(mappedError)
Throw(error)     -> Return(fallback)
Return(value)    -> Throw(policyViolation)
```

Mỗi phép biến đổi là một quyết định có hệ quả khác nhau. Ví dụ chuyển một lỗi nghiệp vụ thành fallback hợp lệ về kiểu vẫn chưa chắc hợp lệ về nghiệp vụ.

## 2. Khi nào around advice có quyền thay kết quả?

Một mô hình advice bao quanh thường hoạt động theo ý tưởng:

```text
around(joinPoint):
    before()
    result = proceed()          // gọi chuỗi advice / target tiếp theo
    after(result)
    return transform(result)
```

Một around advice có thể **không gọi `proceed`**, trả kết quả từ cache hoặc trả fallback; có thể gọi tiếp nhiều lần để retry khi engine cho phép. Tuy nhiên, nhiều công cụ áp đặt ràng buộc:

- Chỉ hỗ trợ around trên một số loại join point, ví dụ method call hoặc execution.
- Có engine cho phép truyền argument mới vào `proceed`; engine khác chỉ cho gọi tiếp với đối số cũ.
- Advice có thể buộc khai báo cùng loại trả về với target hoặc sử dụng một kiểu bao quát rồi kiểm tra tại runtime.
- Một số môi trường chỉ có callback before/after, không có quyền trả kết quả thay thế.
- Quyền thay outcome của advice có thể bị giới hạn bởi phương thức `void`, primitive, checked exception hoặc cơ chế proxy.

**Không coi một đoạn code viết trong class tên “Aspect” là around advice có đầy đủ quyền.** Cần xem API của cơ chế can thiệp và kiểm thử caller thực sự nhận gì.

### Quan sát kết quả không đồng nghĩa thay kết quả

After-returning advice thường thích hợp để **đọc** giá trị thành công. Việc một biến tên `result` được truyền cho nó không chứng minh rằng `result = replacement` sẽ đổi giá trị caller nhận. Nếu caller phải thấy reference mới hoặc kết quả khác, cần API có quyền thay outcome, thường là around hay hook tương đương.

Ngược lại, sửa *trạng thái* của một object mutable được trả về có thể làm caller thấy dữ liệu đổi dù reference kết quả vẫn giữ nguyên. **Mutation object** và **replacement kết quả** là hai hành vi riêng, có độ rủi ro khác nhau.

## 3. Ma trận tương thích kiểu trả về

| Khai báo target | Around có thể trả | Sai lầm dễ gặp |
| --- | --- | --- |
| `String` | Object thực tế là `String` hoặc `null` nếu contract cho phép | Trả `Integer` chỉ vì API advice khai báo `Object` |
| `Object` | Bất kỳ reference object hợp lệ theo nghiệp vụ; `null` nếu được phép | Nghĩ rằng mọi kiểu đều đúng về ý nghĩa nghiệp vụ |
| `Number` | Một object thuộc subtype của `Number` (ví dụ `Integer`) | Trả `String` hoặc object không có quan hệ gán kiểu |
| `int` | Giá trị phù hợp primitive, thường qua `Integer` khi dùng proxy Java | Trả `null`, `String` hoặc wrapper không tương thích |
| `boolean` | `Boolean` phù hợp cho bước unboxing trong proxy Java | Trả chuỗi `"true"` thay vì boolean |
| `void` | Theo Java dynamic proxy, handler trả về sẽ bị bỏ qua; `null` là lựa chọn rõ ràng | Tưởng có thể truyền một giá trị thành công tới caller qua phương thức `void` |
| `List<String>` | Một đối tượng phù hợp contract `List<String>` | Dùng `List<Integer>` vì cả hai đều qua được raw `List` |
| `Optional<T>` | Một `Optional` hợp lệ với nội dung phù hợp `T` | Trả `null` hoặc nhét kiểu phần tử sai |
| `CompletableFuture<T>` | Future/stage hợp lệ theo declared return type | Trả ngay `T` thay cho object future |
| `T` trong generic method | Kết quả phải hợp đồng với kiểu được suy ra/bound tại caller | Chỉ nhìn type erasure rồi trả object sai kiểu cụ thể |

Bảng mô tả hướng đánh giá, **không bảo đảm một engine AOP sẽ cho phép mọi phép thay thế ở cột giữa**. Một giá trị hợp lệ về Java type vẫn phải đáp ứng non-null contract, bất biến nghiệp vụ, quyền truy cập và tác dụng phụ đã được API cam kết.

## 4. Reference return: subtype, null và kiểu thực tế

Một method khai báo trả về `CharSequence` có thể trả `String` hoặc `StringBuilder` vì cả hai triển khai `CharSequence`. Một method khai báo `String` thì không thể trả `StringBuilder` trực tiếp vì `StringBuilder` không phải subtype của `String`.

Ví dụ:

```java
// Khai báo minh họa, không phải chương trình độc lập.
CharSequence text();
String fixedText();
```

Giả sử around cho phép thay return object:

- Với `text()`, `return new StringBuilder("ok")` có thể tương thích về mặt type.
- Với `fixedText()`, cùng object đó không tương thích `String`.
- Trả `null` về mặt JVM có thể gán cho reference return, nhưng nếu contract không cho null thì caller có thể lỗi khi sử dụng.

**Nullability** là một phần của hợp đồng rộng hơn hệ thống kiểu cơ bản trong nhiều API Java. Vì vậy quanh một method có annotation hoặc quy ước “non-null”, không nên trả `null` để biểu thị “không tìm thấy” trừ khi contract thực sự cho phép.

### 4.1. Tại sao `Object` trong advice dễ gây hiểu nhầm?

Một interceptor Java SE thường có API kiểu:

```java
Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)
        throws Throwable;
```

Đó là kiểu trả về của **handler**, không phải của mọi phương thức được intercept. Với method trả `String`, proxy được sinh phải chuyển object handler trả về thành `String`. Một object `Integer` có thể gây `ClassCastException` khi proxy thực hiện phép chuyển này, **ngay cả khi handler chạy xong mà không tự báo lỗi**.

### 4.2. Kiểu ở compile time và runtime

Java có các phép kiểm tra kiểu tại compile time, nhưng một API interceptor dùng `Object` có thể trì hoãn kiểm tra tới runtime. Do đó:

- Code advice **biên dịch** không phải chứng cứ kết quả hợp lệ.
- Một câu lệnh `(String) result` chỉ ép kiểu, không tự biến object kiểu khác thành String.
- `String.valueOf(object)` tạo chuỗi biểu diễn; đó là **phép biến đổi có chủ đích**, khác với cast. Giá trị chuỗi mới cũng cần đáp ứng semantics của target.

## 5. Primitive return: boxing, unboxing và lỗi null

Trong Java, method trả primitive không trả trực tiếp một reference `null`. Tuy vậy, handler proxy có thể sử dụng `Object` nên nó phải trả một wrapper phù hợp để proxy **unbox**.

Ví dụ contract:

```java
int count();
boolean isEnabled();
long duration();
```

Trong mô hình Java dynamic proxy, những giá trị tương thích thông thường là `Integer`, `Boolean` và `Long` tương ứng. **Đừng kỳ vọng `Integer` tự động được proxy coi như `Long`** chỉ vì cả hai là số. Unboxing wrapper cụ thể là bước runtime do code được sinh thực hiện, và giá trị kiểu khác có thể gây `ClassCastException`.

Nếu handler trả `null` cho method trả `int`, proxy cố unbox một reference null và có thể gây `NullPointerException`. Nếu handler trả `"17"` thì không có cơ chế chuyển chuỗi thành số tự động chỉ vì method khai báo `int`.

### 5.1. Around thay primitive result

Một around advice có thể đọc `int` đã được boxing rồi tính toán:

```java
// Pseudocode logic của một interceptor đã biết target trả int.
int original = (Integer) proceed();
int adjusted = original + 1;
return Integer.valueOf(adjusted);
```

Đây là đoạn minh họa **không độc lập**, giả định `proceed()` trả `Object` với object `Integer`. Kết quả trả về phải được boxing thành wrapper phù hợp để hệ thống chuyển về `int`.

Các rủi ro khác vẫn còn: overflow số nguyên, giá trị không nằm trong miền hợp lệ, giới hạn trên/dưới, hoặc điều kiện nghiệp vụ có thể bị phá vỡ vì advice cộng/trừ tùy ý.

### 5.2. Primitive khác wrapper reference

`Integer getCount()` là **reference return**, nên về mặt kiểu có thể trả `null`. `int getCount()` là **primitive return**, không thể trả null như một giá trị hoàn tất bình thường. Nếu ứng dụng dùng null làm tín hiệu “không có dữ liệu”, việc đổi return type từ `Integer` sang `int` ảnh hưởng sâu tới lựa chọn fallback của around.

## 6. `void` return: có thể chạy logic, không có giá trị cho caller

Một method `void publish(String message)` có thể có side effect nhưng caller không nhận một giá trị kết quả từ method. Around advice vẫn có thể ghi log, chặn, ném exception hoặc điều khiển có gọi target hay không nếu cơ chế cho phép; nhưng **không thể biến API `void` thành API trả `String` chỉ bằng cách `return "done"` trong interceptor**.

Với **Java dynamic proxy** theo contract của `InvocationHandler`, object handler trả về cho phương thức khai báo `void` được **bỏ qua**. Vì vậy convention rõ ràng là `return null` sau khi hoàn thành interception.

Với một **hệ thống advice khai báo tĩnh**, việc trả một object cho một join point `void` có thể bị cấm hoặc đòi hỏi cú pháp riêng. Không lấy hành vi “proxy bỏ qua return object” làm luật áp dụng chung cho mọi ngôn ngữ AOP.

### 6.1. `void` vẫn có thể thất bại

`void` không có kết quả trả về hữu ích, nhưng method vẫn có thể **throw**. Around có thể quan sát hoặc thay đổi luồng lỗi nếu được hỗ trợ. Khả năng ghi log “done” sau proceed không chứng minh publish đã thành công nếu target ném ngoại lệ hoặc công việc chỉ được gửi đi bất đồng bộ.

### 6.2. Phân biệt không có kết quả với “thành công”

Một method `void` có thể chỉ ghi vào queue, cập nhật trạng thái tạm hoặc khởi động công việc khác. Việc nó return không nhất thiết đồng nghĩa tác vụ cuối cùng hoàn tất; advice cần hiểu contract và các kênh trạng thái bên ngoài.

## 7. Ví dụ Java 17+ chạy được: kiểu đúng, kiểu sai và kết quả caller thấy

Ví dụ này dùng **`java.lang.reflect.Proxy`** trong Java SE. Một proxy thứ nhất minh họa around advice biến đổi hợp lệ kết quả `String` và `int`, đồng thời giữ đúng semantics của `void`. Một proxy thứ hai cố tình trả giá trị không tương thích để quan sát lỗi tại ranh giới caller.

Đây là ví dụ **runtime interception**, không phải một DSL pointcut đầy đủ và không khẳng định mọi công cụ AOP có cùng quyền thay kết quả. Chỉ những lời gọi đi qua proxy mới chịu tác động.

Lưu toàn bộ vào tệp **`AroundReturnCompatibilityDemo.java`** (UTF-8):

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AroundReturnCompatibilityDemo {
    public interface Operations {
        String label(String name);
        int doubleValue(int value);
        void notifyEvent(String event);
    }

    public static final class Target implements Operations {
        @Override
        public String label(String name) {
            System.out.println("target label " + name);
            return "hello " + name;
        }

        @Override
        public int doubleValue(int value) {
            System.out.println("target double " + value);
            return value * 2;
        }

        @Override
        public void notifyEvent(String event) {
            System.out.println("target notify " + event);
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

    static Operations compatibleAround(Operations target) {
        return (Operations) Proxy.newProxyInstance(
                Operations.class.getClassLoader(),
                new Class<?>[] { Operations.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    System.out.println("around enter " + name);

                    try {
                        Object original = proceed(method, target, args);

                        if (method.getReturnType() == void.class) {
                            System.out.println("around leave void");
                            return null;
                        }

                        if (name.equals("label")) {
                            String mapped = "[" + (String) original + "]";
                            System.out.println("around leave label");
                            return mapped;
                        }

                        if (name.equals("doubleValue")) {
                            Integer mapped = ((Integer) original) + 1;
                            System.out.println("around leave doubleValue");
                            return mapped;
                        }

                        return original;
                    } finally {
                        System.out.println("around finally " + name);
                    }
                });
    }

    // Proxy cố ý trả các giá trị vi phạm return contract.
    // Không gọi target: chỉ nhằm quan sát cơ chế kiểm tra kiểu.
    static Operations incompatibleAround() {
        return (Operations) Proxy.newProxyInstance(
                Operations.class.getClassLoader(),
                new Class<?>[] { Operations.class },
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "label" -> Integer.valueOf(42);
                        case "doubleValue" ->
                                ((Integer) args[0]) == 0
                                        ? null : "wrong type";
                        case "notifyEvent" -> {
                            System.out.println("bad around void");
                            yield "ignored";
                        }
                        default -> throw new AssertionError("unexpected");
                    };
                });
    }

    public static void main(String[] args) {
        Operations service = compatibleAround(new Target());

        System.out.println("caller " + service.label("Ana"));
        System.out.println("caller " + service.doubleValue(4));
        service.notifyEvent("ready");

        Operations bad = incompatibleAround();

        try {
            bad.label("Q");
        } catch (ClassCastException error) {
            System.out.println("bad label: ClassCastException");
        }

        try {
            bad.doubleValue(1);
        } catch (ClassCastException error) {
            System.out.println("bad int type: ClassCastException");
        }

        try {
            bad.doubleValue(0);
        } catch (NullPointerException error) {
            System.out.println("bad int null: NullPointerException");
        }

        bad.notifyEvent("Q");
        System.out.println("caller after bad void");
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 AroundReturnCompatibilityDemo.java
java AroundReturnCompatibilityDemo
```

**Standard output dự kiến chính xác:**

```text
around enter label
target label Ana
around leave label
around finally label
caller [hello Ana]
around enter doubleValue
target double 4
around leave doubleValue
around finally doubleValue
caller 9
around enter notifyEvent
target notify ready
around leave void
around finally notifyEvent
bad label: ClassCastException
bad int type: ClassCastException
bad int null: NullPointerException
bad around void
caller after bad void
```

### 7.1. Tại sao `label` hợp lệ?

Target trả về `"hello Ana"` kiểu `String`. Around bọc kết quả thành `"[hello Ana]"`, vẫn là một `String`, nên proxy có thể chuyển tới caller. Việc biến đổi có hợp lệ về nghiệp vụ hay không phụ thuộc contract API, nhưng **hợp lệ về kiểu** trong ví dụ.

### 7.2. Tại sao `doubleValue` hợp lệ?

Target trả primitive `int` bằng `8`. Reflection qua `Method.invoke` đưa kết quả cho interceptor dưới dạng object `Integer`. Around chuyển thành `Integer.valueOf(9)` thông qua phép cộng rồi boxing tự động. Proxy có thể unbox `Integer` thành `int` cho caller. Đây là một phép biến đổi **đúng wrapper type**, không phải tự động chuyển mọi `Number` thành primitive yêu cầu.

### 7.3. Tại sao `void` hợp lệ?

Target `notifyEvent` in sự kiện rồi kết thúc, `Method.invoke` trả `null` cho phương thức `void`. Around in dòng thông tin và trả `null`. Không có giá trị cho caller. Điều quan trọng là caller vẫn nhận lại quyền điều khiển bình thường nếu không ném ngoại lệ.

### 7.4. Tại sao proxy sai gặp `ClassCastException`?

Với `label`, handler cố ý trả object `Integer(42)` trong khi method khai báo `String`. Proxy cần chuyển kết quả thành `String`, nên runtime báo `ClassCastException`. Với `doubleValue(1)`, handler trả chuỗi `"wrong type"` trong khi return contract là primitive `int`; proxy cần `Integer` để unbox và gặp lỗi tương thích kiểu.

### 7.5. Tại sao `null` gây lỗi ở primitive?

Với `doubleValue(0)`, handler trả `null` cho `int`. Proxy cần unbox một `Integer`, nhưng reference là null nên `NullPointerException`. Đây là vấn đề của **kiểu trả về**, không phải vì Java không thể chứa null trong biến `Object` của handler.

### 7.6. Tại sao return từ bad `void` bị bỏ qua?

`incompatibleAround` trả chuỗi `"ignored"` khi method được khai báo `void`. Với **Java dynamic proxy**, return object của handler cho method `void` bị bỏ qua; caller chỉ thấy lời gọi hoàn tất. Điều này **không có nghĩa target đã chạy**: trong proxy sai, target không được gọi. Ví dụ cố ý chỉ cho thấy giới hạn của return contract, không phải một implementation ứng dụng đúng.

### 7.7. Vì sao ví dụ phải có cả caller và target?

Chỉ đọc code handler chưa đủ. Ta cần xác nhận **target có chạy không**, **target tạo giá trị gì**, **around trả gì**, **caller nhận gì** hoặc **caller gặp exception nào**. Một matcher/handler có thể chạy đúng nhưng kết quả chuyển tới caller sai kiểu; lỗi chỉ xuất hiện ở bước trả kết quả.

## 8. Around có thể không gọi tiếp: khi nào phải tạo kết quả thay thế?

Nếu around không gọi `proceed`, nó có thể trả một giá trị từ cache, fallback hoặc một kết quả tổng hợp **nếu contract và cơ chế cho phép**. Nhưng nó phải đảm bảo:

- **Type:** cùng kiểu hoặc subtype tương thích với khai báo.
- **Nullability:** không vi phạm non-null/primitive.
- **Business contract:** caller không bị báo thành công giả nếu nghiệp vụ bắt buộc chưa thực thi.
- **Side effects:** bỏ qua target có làm thiếu ghi dữ liệu, audit, transaction hoặc phát sự kiện không?
- **Ordering:** những advice nằm phía trong around có thể **không chạy** nếu bị bỏ qua.
- **Exceptions:** người gọi vẫn có thể chờ một số loại lỗi; fallback phải được định nghĩa rõ.

Ví dụ một method `int availableSeats()` có thể sử dụng cache đã được xác thực để trả về một `Integer` phù hợp trong proxy. Nhưng method `void chargeMoney()` không thể “báo đã thu tiền” chỉ bằng cách around không gọi target và trả một chuỗi; caller thậm chí không có kênh return value để nhận chuỗi đó.

### “Đúng kiểu” không đồng nghĩa “đúng dữ liệu”

Trả `0` cho mọi lỗi của `availableSeats()` có thể khiến hệ thống tưởng không còn chỗ thay vì hiểu rằng dịch vụ dữ liệu đang thất bại. Trả `true` cho một thao tác cấp quyền khi chưa được kiểm tra có thể gây lỗ hổng. **Fallback có nghĩa nghiệp vụ** cần được thiết kế như một phần hợp đồng API, không thêm ngẫu nhiên trong aspect.

## 9. Around gọi tiếp nhiều lần: cùng một chữ ký, nhiều kết quả

Một số cơ chế AOP cho phép around gọi `proceed` nhiều lần để retry. Mỗi lần có thể tạo một return value hoặc exception khác nhau. Around phải quyết định outcome cuối cùng cho caller.

```text
Caller gọi send() một lần
  around:
    proceed #1 -> timeout
    proceed #2 -> Return("ok")
    return "ok"
```

Ký pháp trên là sơ đồ khái niệm. Tương thích kiểu của `"ok"` chỉ là một khía cạnh; nếu lần #1 **đã gửi thành công nhưng phản hồi bị mất**, retry có thể gửi trùng. Những thao tác có side effect như thanh toán, gửi thông điệp, ghi dữ liệu cần idempotency key hoặc cơ chế chống lặp phù hợp.

Với method `void`, retry vẫn có thể gây side effect trùng dù caller không nhận giá trị. Với primitive return, các lỗi của retry vẫn có thể xảy ra ở bước unboxing. Đừng dùng retry chỉ vì API around cho phép gọi tiếp.

## 10. Checked exceptions, unchecked exceptions và return contract

Một phương thức có thể khai báo:

```java
// Ví dụ khai báo.
String load(String key) throws java.io.IOException;
```

Quá trình chạy có thể **return String** hoặc **throw IOException** hay lỗi khác theo quy tắc Java. Around advice bao quanh lời gọi có thể cần chuyển tiếp ngoại lệ.

Một số điểm quan trọng:

- Việc method có kiểu trả `String` **không ngăn** nó ném ngoại lệ.
- `return fallbackString` ở around có thể chuyển outcome từ throw sang return nếu API cho phép.
- Nếu advice tự ném một checked exception **không thuộc contract** của interface method, một số cơ chế proxy có thể bọc nó vào loại ngoại lệ không kiểm tra được dự kiến, thay vì truyền thẳng cho caller.
- Reflection `Method.invoke` có thể bọc lỗi target trong `InvocationTargetException`; interceptor cần xử lý lớp bọc này nếu muốn giữ nguyên throwable gốc.
- Nếu advice có `finally` và logic cleanup ném lỗi mới, lỗi cleanup có thể che đi return/throw trước đó tùy cách xử lý.

**Đổi ngoại lệ thành return hợp lệ về kiểu chưa chắc hợp lệ về nghiệp vụ**. Recovery cần phân loại lỗi có thể phục hồi và giữ thông tin quan sát/tracing phù hợp.

## 11. Return generic: type erasure không xóa hợp đồng của caller

Java generics có *type erasure*. Tại runtime, `List<String>` thường được nhìn qua raw type `List`, còn kiểu phần tử `String` được kiểm tra bởi logic/chèn cast ở nơi sử dụng.

Giả sử API khai báo:

```java
// Minh họa, không phải chương trình độc lập.
java.util.List<String> names();
```

Một around trả `List<Integer>` có thể **lọt qua một phép kiểm tra raw `List`**. Nhưng caller vẫn mong các phần tử là String:

```java
// Mã minh họa:
String first = names().get(0);
```

Nếu phần tử đầu thực tế là `Integer`, lỗi cast có thể phát sinh **ở caller khi lấy phần tử**, không nhất thiết ngay tại thời điểm proxy trả list. Đây là **heap pollution/vi phạm hợp đồng generic**, không phải dấu hiệu kết quả hợp lệ.

### 11.1. Generic method và biến kiểu `T`

Với:

```java
// Minh họa một chữ ký generic:
<T> T identity(T input);
```

Sau erasure, phương thức có thể có descriptor dựa trên `Object` (với upper bound phù hợp). Around trả một object bất kỳ có thể qua được ranh giới raw `Object`, nhưng caller sử dụng kết quả ở một kiểu `T` cụ thể có thể gặp `ClassCastException`.

Ví dụ về mặt ý tưởng, caller mong nhận `String` khi truyền `String`. Around trả `Integer` làm sai contract dù runtime handler chỉ thấy return metadata bao quát. Vì vậy cần **bảo toàn mối quan hệ kiểu giữa input và output**, không chỉ kiểm tra raw return type.

### 11.2. Generic bound và wildcard

`List<? extends Number>`, `List<Number>` và `List<Integer>` có các quan hệ gán kiểu khác nhau ở compile time. Một interceptor chỉ thấy raw `List` có thể không bảo vệ toàn bộ thông tin đó. Cần kiểm thử các phép sử dụng tại caller và các method contract, không coi type erasure là giấy phép trả dữ liệu sai loại.

### 11.3. Bridge methods và synthetic methods

Khi generic/covariant override được biên dịch, compiler có thể tạo **bridge methods** để giữ overriding đúng sau erasure. Một cơ chế AOP làm việc ở bytecode có thể thấy bridge method bên cạnh method do lập trình viên viết. Nếu advice được áp dụng không đúng vùng, có thể tạo can thiệp trùng hoặc không khớp như kỳ vọng.

`Method.isBridge()` và `Method.isSynthetic()` là công cụ reflection hữu ích để phân tích. **Không tự kết luận** mọi engine AOP tự gộp hay loại bridge theo cùng quy tắc.

## 12. Covariant return và interface/implementation

Java cho phép một overriding method trả về **subtype** của return type ở lớp cha/giao diện khi quy tắc ngôn ngữ cho phép. Ví dụ:

```java
// Khai báo minh họa, có thể đặt trong các class thích hợp.
class Animal { }
class Dog extends Animal { }

class AnimalFactory {
    Animal create() { return new Animal(); }
}

class DogFactory extends AnimalFactory {
    @Override
    Dog create() { return new Dog(); }
}
```

`DogFactory.create()` khai báo `Dog`, hẹp hơn `Animal` của lớp cha. Một interceptor quan sát phương thức ở góc nhìn lớp cha có thể thấy metadata khác so với interception trực tiếp ở implementation. Nếu advice biến đổi kết quả thành một `Animal` không phải `Dog`, nó có thể hợp với contract **cha** nhưng không hợp với contract **override trả Dog**.

Đây là lý do khi thiết kế quanh phương thức đa hình cần biết:

- Join point là **call** hay **execution**?
- Return metadata phản ánh interface/base method hay implementation method?
- Cơ chế proxy dùng chữ ký method nào khi tạo phương thức chuyển tiếp?
- Có bridge method nào được compiler sinh ra không?
- Caller thấy kiểu compile-time nào, và implementation có ràng buộc hẹp hơn không?

**Không chọn fallback chỉ theo supertype rộng nhất** nếu một đường gọi thực tế đòi kiểu hẹp hơn.

## 13. `Optional<T>` và các kiểu bao kết quả

Một API có thể dùng:

```java
// Minh họa chữ ký:
java.util.Optional<String> find(String key);
```

Một around trả `null` có thể vượt qua kiểm tra Java reference type nhưng phá contract thiết kế vì caller thường mong **một object `Optional`** chứ không phải null. Nếu “không tìm thấy” là trạng thái hợp lệ, kết quả thường phải là `Optional.empty()`; nếu tìm thấy, là `Optional.of(...)` hoặc dạng phù hợp.

Ngay cả khi trả một `Optional` object đúng loại raw, nội dung bên trong phải phù hợp `String`. Một `Optional<Integer>` đưa vào contract `Optional<String>` qua unchecked cast là một vi phạm tiềm ẩn và có thể gây lỗi khi caller lấy dữ liệu.

Tương tự, các kiểu bao như `Result<T>`, `Either<L,R>` hoặc response object tự định nghĩa có **ngữ nghĩa thành công/thất bại** của riêng chúng. Around cần bảo toàn contract của wrapper, không chỉ làm cho cast runtime qua được.

## 14. Bất đồng bộ: method return khác completion của tác vụ

Một method có thể khai báo:

```java
// Minh họa chữ ký:
java.util.concurrent.CompletableFuture<String> fetchAsync(String key);
```

Trong trường hợp này **giá trị trả về ngay lập tức** là `CompletableFuture<String>`, **không phải String**. Around trả `"data"` trực tiếp thay vì future là sai kiểu. Around có thể, nếu engine hỗ trợ:

- Trả nguyên future của target.
- Trả một future thay thế đã hoàn tất theo contract phù hợp.
- Trả future được tạo từ stage biến đổi, ví dụ map String sau khi tác vụ kết thúc.
- Tạo future thất bại/được hủy theo chính sách rõ ràng, nhưng phải giữ đúng ngữ nghĩa API.

### 14.1. Biến đổi đối tượng future khác biến đổi giá trị bên trong

```java
// Minh họa logic kiểu Java, không phải chương trình độc lập.
java.util.concurrent.CompletableFuture<String> original = fetchAsync("K");
java.util.concurrent.CompletableFuture<String> mapped =
        original.thenApply(value -> value.toUpperCase(java.util.Locale.ROOT));
```

Ở đây `mapped` vẫn là `CompletableFuture<String>` phù hợp declared return, còn biến đổi chuỗi thực hiện khi stage hoàn tất thành công. Việc này **khác** với biến đổi giá trị ngay lúc phương thức `fetchAsync` return.

Nếu muốn theo dõi cả outcome thất bại, phải quan tâm `CompletionException`, ngoại lệ được bao, cancellation và callback semantics. Một after-returning advice đối với `fetchAsync` chỉ biết phương thức đã **trả về future**, chưa thể mặc định biết tác vụ tương lai thành công hay thất bại.

### 14.2. Thread/context propagation

Callback xử lý future có thể chạy trên thread khác. Nếu advice dùng context gắn với thread (chẳng hạn trace ID), phải có cơ chế truyền ngữ cảnh phù hợp qua ranh giới callback. **Return type đúng** không chứng minh context đúng hoặc dữ liệu bảo mật được xử lý an toàn.

## 15. Chuỗi nhiều advice: loại kết quả ở ranh giới nào?

Khi nhiều around advice cùng khớp, chúng thường được tổ chức lồng nhau theo precedence:

```text
OuterAround
    |
    InnerAround
        |
        Target
        |
    InnerAround returns valueA
    |
OuterAround returns valueB
    |
Caller sees valueB
```

Một advice bên trong có thể trả kết quả **đúng kiểu**; advice bên ngoài lại biến nó thành object sai kiểu trước khi caller nhận. Ngược lại, inner có thể ném lỗi và outer bắt để trả fallback.

Các câu hỏi thiết kế bắt buộc:

1. Aspect nào là **lớp ngoài cùng**?
2. Advice nào thấy **argument gốc** và advice nào thấy argument đã biến đổi?
3. Advice nào thấy **kết quả đích** và advice nào thấy kết quả đã bọc?
4. Nếu inner thất bại nhưng outer recover, advice after-returning ở **ngoài** có xem kết quả là thành công không?
5. Loại dữ liệu cuối cùng tại **ranh giới caller** có đúng hợp đồng method không?

Không chỉ QA từng advice riêng lẻ. Cần integration test **composition** vì tính tương thích cuối cùng phụ thuộc toàn bộ chuỗi.

### “Nhiều lần proceed” và type

Nếu inner around trả `Integer` cho method `int`, outer quanh nó có thể nhận object đó rồi biến đổi. Nhưng nếu inner trả `String` sai kiểu, tùy kiến trúc, lỗi có thể xuất hiện khi nó được ép về kiểu method hoặc ở một lớp proxy/handler ngoài. **Vị trí phát hiện lỗi phụ thuộc cách engine ghép advice**, không phải cứ tại dòng `return` của advice sai.

## 16. Type compatibility không thay thế semantic compatibility

Một around trả đúng Java type vẫn có thể làm sai contract. Ví dụ:

| API target | Replacement đúng Java type | Vì sao vẫn có thể sai |
| --- | --- | --- |
| `boolean isAuthorized()` | `true` | Bỏ qua kiểm tra quyền thực tế |
| `int inventoryCount()` | `0` | Nhầm dữ liệu lỗi với “hết hàng” |
| `String paymentStatus()` | `"PAID"` | Thông báo đã trả tiền khi giao dịch chưa xong |
| `Optional<User> findUser()` | `Optional.empty()` | Che lỗi backend thành “không tìm thấy” |
| `List<Item> fetchItems()` | Danh sách rỗng | Che lỗi timeout thành dữ liệu hợp lệ |
| `CompletableFuture<Result> runAsync()` | Future đã complete mặc định | Tác vụ thật chưa chạy hoặc còn side effect |

Đó là lý do **fallback, cache, retry, exception mapping** cần một chính sách nghiệp vụ rõ, giới hạn phạm vi và test hồi quy. Không nên dùng around để “làm đẹp kết quả” mà không thảo luận với nơi sở hữu hợp đồng API.

### Non-null và invariant

Một số API có invariant không biểu diễn đầy đủ trong kiểu Java: chuỗi không được rỗng, collection đã được sắp xếp, ID đúng định dạng, trạng thái giao dịch nhất quán. Around không được phá invariant chỉ vì JVM vẫn chấp nhận kiểu object đó.

## 17. Những giới hạn của kỹ thuật weaving/interception

Không phải một advice có quyền giống nhau trong mọi công cụ. Cần phân biệt:

| Cơ chế | Điểm mạnh thông thường | Giới hạn cần kiểm chứng |
| --- | --- | --- |
| **Runtime proxy dựa trên interface** | Có một handler nhận Method và argument, dễ chặn lời gọi qua proxy | Gọi trực tiếp target hoặc tự gọi nội bộ có thể bỏ qua; return contract vẫn phải được proxy kiểm tra |
| **Proxy kiểu subclass** | Có thể bao quanh những method cho phép override/intercept | `final`, `private`, `static` và đường gọi không qua proxy có thể giới hạn |
| **Compile-time weaving** | Chèn advice vào phần mã khi biên dịch | Chỉ tác động artifact được build/weave; quyền đổi return tùy ngôn ngữ AOP |
| **Post-compile weaving** | Biến đổi bytecode đã biên dịch | Tương thích bytecode và việc dùng đúng artifact đã weave |
| **Load-time weaving** | Biến đổi class trong quá trình load | Class loader, agent, thứ tự load và tập class được weave ảnh hưởng coverage |
| **Interceptor tại pipeline/hook** | API rõ ở một ranh giới được thiết kế sẵn | Có thể chỉ hỗ trợ đọc hoặc đổi một số thành phần dữ liệu |

Trong Java, method có modifier `private` hoặc `static` vẫn có return type, nhưng **proxy dựa trên interface** không tự intercept được những lời gọi đó như một instance method trong interface. Constructor có semantic return đặc biệt; không suy từ `return Object` ở interceptor thành quyền tùy ý đổi object được tạo bởi constructor.

### Bỏ qua đường proxy

`Target` có thể được gọi trực tiếp; một method có thể gọi `this.helper()` bên trong target. Nếu chỉ có interceptor ở proxy ngoài, các đường đó có thể không chạy advice dù signature matcher khớp. **Return transformation chỉ tác động đến những lời gọi thật sự đi qua nơi advice được áp dụng.**

Bytecode weaving có thể hỗ trợ thêm các join point nội bộ tùy engine; không phát biểu rằng “mọi self-invocation đều không thể được advice” trong AOP nói chung.

## 18. Method call và method execution: return ở hai phía

Trong một số mô hình AOP có **method call join point** và **method execution join point**, around advice có thể được đặt ở hai ranh giới khác nhau.

- **Call-side:** nằm tại biểu thức gọi, nơi caller yêu cầu thực thi phương thức.
- **Execution-side:** nằm ở ranh giới method body đích thực sự chạy.

Cả hai đều có thể quan sát result/exception khi cơ chế cho phép, nhưng ngữ cảnh và phạm vi weave khác nhau. Một call site qua interface có thể liên quan kiểu compile-time `Base`, còn execution thực tế có thể thuộc override trả kiểu hẹp hơn.

Nếu thao tác bị lỗi khi tính argument ở caller trước khi target bắt đầu, execution advice ở target có thể chưa chạy. Một call-side advice có thể có quy tắc quan sát khác, tùy thời điểm triển khai. **Không thể mặc định mọi loại advice đều nhìn thấy cùng một return/throw outcome trên mọi đường gọi.**

## 19. Reflection, bytecode và thời điểm phát hiện lỗi

Một proxy dùng reflection có thể nhận result dưới dạng `Object`, nhưng khi generated method trả cho caller, bytecode phải chuyển nó về declared return type. Với generic methods, bridge hoặc cast trong caller có thể khiến lỗi phát sinh **sau khi interceptor đã trả về thành công**.

Các điểm quan sát hữu ích khi debug:

- **Method.getReturnType():** return type đã erasure ở cấp `Class<?>`.
- **Method.getGenericReturnType():** metadata `Type` có thể chứa thông tin generic (không phải kiểu phần tử runtime).
- **Method.getDeclaringClass():** class/interface thực sự khai báo Method.
- **Method.isBridge()/isSynthetic():** dùng để nhận diện một số method compiler sinh.
- **InvocationTargetException.getCause():** lấy lỗi target khi reflection bọc.
- **Stack trace/caller test:** chỉ ra nơi lỗi cast/unbox thực sự phát sinh.

**Không sửa lỗi bằng cách bắt `ClassCastException` rồi trả một object bất kỳ.** Cần truy ngược ai đã thay return, method contract nào bị vi phạm và có chặng generic/covariant nào đang đưa ra yêu cầu kiểu hẹp hơn.

## 20. Thiết kế around advice tương thích kiểu

Một quy trình thực tế:

1. **Xác định join point:** phương thức nào, call hay execution, metadata có thể lấy.
2. **Xác định declared return:** reference, primitive, void, generic wrapper hay asynchronous result?
3. **Xác định desired behavior:** quan sát, map kết quả, fallback, cache, retry hay chặn?
4. **Chỉ chọn quyền cần thiết:** nếu chỉ cần quan sát, dùng advice/handler không đổi outcome.
5. **Nếu thay result:** kiểm tra assignability, nullability, wrapper primitive và generic/semantic contract.
6. **Nếu target throw:** xác định giữ lỗi, map lỗi hay recover; đừng che side effect đã xảy ra.
7. **Nếu không proceed:** xác nhận caller được nhận một outcome phù hợp dù target không chạy.
8. **Nếu nhiều aspect:** quy định precedence và kiểm chuỗi tổng hợp.
9. **Nếu async:** phân biệt object future trả về và completion value.
10. **Nếu nhiều đường gọi:** kiểm tra coverage proxy/weaving, self-invocation, reflection và tác vụ chạy ngoài boundary.
11. **Kiểm thử ở caller thật:** xác minh kiểu/kết quả/exception ở nơi sử dụng, không chỉ return của handler.
12. **Đo và theo dõi:** tránh các phép biến đổi gây overhead, log nhạy cảm hoặc kết quả không lường trước.

### Kiểm tra type ở runtime có thể làm gì?

Một interceptor tổng quát có thể thực hiện kiểm tra phòng vệ trước khi trả object:

```java
// Mã Java minh họa, không phải chương trình độc lập.
static boolean compatibleReference(Class<?> returnType, Object result) {
    return !returnType.isPrimitive()
            && (result == null || returnType.isInstance(result));
}
```

Điều kiện này chỉ kiểm tra **reference return ở mức raw Class**. Nó không xử lý primitive/void, không kiểm tra nội dung generic và không kiểm tra business contract. Vì vậy nó chỉ là một phần nhỏ của validation, **không phải bộ chứng minh mọi outcome đều an toàn**.

## 21. Kiểm thử toàn diện cho return compatibility

| Trường hợp | Kỳ vọng cần kiểm tra |
| --- | --- |
| `String` -> `String` khác | Caller nhận đúng chuỗi đã map |
| `String` -> `Integer` | Ranh giới trả kết quả báo lỗi kiểu |
| `String` -> `null` | Có vi phạm non-null contract không? |
| `int` -> `Integer` | Proxy unbox thành công |
| `int` -> `String` | Runtime không nhận wrapper sai |
| `int` -> `null` | Bước unbox không thể dùng null |
| `boolean` -> `Boolean` | Trả primitive đúng |
| `void` -> handler trả object | Xác minh engine xử lý return handler ra sao |
| `List<String>` -> `List<Integer>` | Test cả khi caller truy cập phần tử |
| `Optional<T>` -> `null` | Có vi phạm contract wrapper không? |
| `CompletableFuture<T>` -> `T` | Có trả sai loại object không? |
| `CompletableFuture<T>` -> mapped future | Completion, error và cancellation có đúng không? |
| Covariant override | Replacement có hợp kiểu hẹp nhất caller cần không? |
| Around không proceed | Side effect cần thiết có bị bỏ qua không? |
| Around retry | Có chạy target nhiều lần, dữ liệu có bị lặp không? |
| Target throw | Có giữ Throwable gốc hoặc recovery đúng contract không? |
| Hai around lồng nhau | Kết quả cuối cùng caller nhận là kiểu gì? |
| Gọi trực tiếp target | Có bỏ qua transformation vì không qua proxy không? |
| Async/concurrency | Có mất context hoặc lẫn kết quả mutable giữa tasks không? |

### Ba mức xác minh

- **Unit test advice:** một giá trị ban đầu và một outcome cụ thể có biến đổi như mong muốn không?
- **Integration test weaving/interception:** lời gọi đi qua đúng ranh giới, type cast/unbox, precedence và return cuối có phù hợp không?
- **Contract test phía caller:** caller xử lý kết quả với kiểu khai báo thật, truy cập generic element, nhận exception đúng và nhìn thấy trạng thái nghiệp vụ nhất quán không?

Đặc biệt, unit test chỉ gọi trực tiếp hàm `Object transform(Object)` có thể **không bắt được** `ClassCastException` chỉ xảy ra ở proxy generated method hoặc caller generic cast.

## 22. Những hiểu lầm thường gặp

1. **“Advice khai báo trả Object nên trả gì cũng được.”** Return contract của target/caller vẫn tồn tại.
2. **“Có thể đổi String thành Integer bằng cast.”** Cast kiểm tra kiểu, không chuyển nội dung dữ liệu.
3. **“Return null cho primitive là tương đương 0.”** Không; unboxing null gây lỗi.
4. **“Long có thể thay Integer cho method trả int.”** Wrapper yêu cầu có thể không tương thích.
5. **“Method void có thể gửi một giá trị cho caller từ handler.”** Caller của void không có return value như vậy; một số handler bỏ qua object trả về.
6. **“After-returning luôn sửa được giá trị caller nhận.”** Nhiều API chỉ cho quan sát.
7. **“Đúng Java type là đúng nghiệp vụ.”** Return fallback vẫn phải tuân thủ invariant và side effects.
8. **“Generic List<String> được kiểm tra đầy đủ ở proxy.”** Type erasure có thể trì hoãn lỗi đến lúc caller đọc phần tử.
9. **“Optional không có dữ liệu thì trả null.”** Thường cần object `Optional.empty()` theo contract.
10. **“Future là String kết quả cuối.”** Future object và completion value là hai kiểu/thời điểm.
11. **“Override trả subtype nên fallback kiểu cha luôn được.”** Covariant return có thể đòi kết quả hẹp hơn.
12. **“Một around đúng kiểu đảm bảo chuỗi các around đúng kiểu.”** Advice ngoài cùng vẫn có thể đổi outcome thành sai.
13. **“Có return hợp lệ thì target chắc đã chạy.”** Around có thể không proceed; return không chứng minh side effect.
14. **“Proxy match method thì mọi đường gọi cùng method đều qua advice.”** Direct call/self-invocation có thể bypass.
15. **“Runtime lỗi ngay dòng return sai.”** Lỗi có thể xảy ra khi proxy cast/unbox hoặc caller generic cast.
16. **“Retry hợp lệ chỉ vì lần cuối return đúng kiểu.”** Các lần trước có thể đã tạo side effect trùng.

## 23. Bài tập thực hành

1. Chạy `AroundReturnCompatibilityDemo.java`, đối chiếu thứ tự `around enter`, `target`, `around leave`, `around finally` và dòng `caller`.
2. Sửa around của `label` để trả `StringBuilder` thay `String`. Chạy và giải thích lỗi kiểu tại proxy.
3. Sửa around của `doubleValue` để trả `Long.valueOf(9)`. Vì sao một object `Number` hợp lệ chưa đủ cho `int`?
4. Đổi handler của `doubleValue` để trả `Integer.valueOf(-1)`. Nó có tương thích Java type không, và có phù hợp contract “double” không?
5. Cho `label` trả null. So sánh kiểm tra kiểu Java với giả định “label phải non-null” ở caller.
6. Thêm method `boolean enabled()` và cho interceptor trả `Boolean.TRUE`, `"true"`, và `null`. Dự đoán kết quả.
7. Đổi `notifyEvent` để handler cố ý trả `Integer(1)`. Giải thích vì sao Java dynamic proxy bỏ qua giá trị này nhưng target có thể đã không chạy.
8. Định nghĩa method `List<String> words()`. Thử trả một list chứa `Integer` bằng unchecked cast trong ví dụ thử nghiệm; xác định lỗi phát sinh khi nào ở caller.
9. Định nghĩa `Optional<String> find()`. So sánh `Optional.empty()` với `null` như hai fallback.
10. Cho `fetchAsync` trả `CompletableFuture<String>`; tạo stage mapped bằng `thenApply`, rồi kiểm thử success và exception completion.
11. Tạo hierarchy có covariant return `Animal`/`Dog`; xem kiểu `Method.getReturnType()` ở lời gọi qua interface/base và ở implementation.
12. Đặt hai around lồng nhau: inner map `String` thành `String`, outer cố map thành `Integer`. Vị trí lỗi ở caller nằm ở đâu?
13. Thử bỏ qua `proceed` trong một thao tác có side effect. Xác định một test chứng minh target không được chạy.
14. Viết một bảng quyết định cho các nhóm return `void`, primitive, nullable reference, generic và future, mô tả outcome phù hợp và invalid cases.
15. Viết một contract test dựa vào **caller thực**, gồm return bình thường, failure, null, generic cast và gọi trực tiếp target bypass proxy.

**Tự kiểm tra:** Vì sao handler trả `Object` không hủy return type của phương thức đích? Primitive khác wrapper như thế nào khi advice trả null? Generic erasure khiến lỗi kiểu xuất hiện muộn ra sao? Around ở ngoài cùng có thể thay outcome mà advice bên trong vừa trả không? Return đúng kiểu có chứng minh nghiệp vụ hoàn thành không?

## Kết luận

**Around advice có thể được phép biến đổi kết quả, nhưng không vượt khỏi hợp đồng kiểu và ngữ nghĩa của join point.** Phải bảo toàn khả năng gán kiểu đối với reference, wrapper/unboxing đối với primitive, semantics của `void`, generic contract, covariant return, các kiểu bao kết quả và asynchronous completion. Đồng thời phải xét null, exception, việc có gọi `proceed` hay không, số lần target chạy và precedence của nhiều advice. Kiểm thử đúng là kiểm thử **giá trị hoặc lỗi mà caller thực sự quan sát** qua cơ chế AOP đang dùng, không chỉ việc một hàm advice biên dịch thành công.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="around-advice-009">Phân biệt bao quanh một lời gọi đồng bộ với toàn bộ tác vụ bất đồng bộ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt bao quanh một lời gọi đồng bộ với toàn bộ tác vụ bất đồng bộ

## 1. Vấn đề cốt lõi: “method đã trả về” không có nghĩa “công việc đã hoàn tất”

Trong **Aspect-Oriented Programming (AOP)**, **around advice** có thể bao quanh một **join point** được **pointcut** chọn. Với một lời gọi đồng bộ thông thường, khoảng từ khi bắt đầu gọi đến khi nhận giá trị/ngoại lệ thường tương ứng gần với khoảng công việc mà caller quan tâm. Nhưng với API bất đồng bộ, phương thức có thể chỉ **khởi tạo hoặc lên lịch công việc rồi trả ngay** một handle như `CompletionStage`, `CompletableFuture`, promise hoặc một đối tượng biểu diễn quá trình thực thi.

Ví dụ ý tưởng:

```text
caller
  |
  v
around advice: bắt đầu đo
  |
  v
target.fetchAsync() ----> gửi tác vụ vào hàng đợi
  |
  v
trả về CompletionStage ngay
  |
  v
around advice: kết thúc đo, trả stage về caller
                                      ... tác vụ vẫn đang chạy ...
                                      -> hoàn tất thành công hoặc thất bại
```

Nếu advice chỉ đo thời gian bao quanh **lời gọi `fetchAsync()`**, số đo là *invocation/dispatch latency*, **không nhất thiết** là thời gian thực sự lấy dữ liệu. Việc future hoàn tất có thể xảy ra nhiều mili giây hoặc nhiều giây sau, ở thread khác, và thậm chí không bao giờ hoàn tất nếu tác vụ bị treo hoặc thiếu xử lý timeout.

Bài học giúp bạn:

1. Phân biệt **lời gọi đồng bộ**, **phương thức trả về handle bất đồng bộ**, **thời điểm bắt đầu xử lý**, **thời điểm completion** và **thời điểm caller quan sát kết quả**.
2. Đọc đúng ngữ nghĩa của `before`, `after-returning`, `after-throwing`, `after/finally` và `around/proceed` khi method trả `CompletionStage`.
3. Nhận diện ba đường lỗi khác nhau: **ném đồng bộ trước return**, **future hoàn tất exceptionally**, và **lỗi ở callback/advice quan sát**.
4. Thiết kế phép đo **thời gian gọi**, **thời gian ở hàng đợi**, **thời gian thực thi** hoặc **end-to-end latency** theo đúng ranh giới.
5. Gắn callback quan sát kết quả cuối, hiểu sự khác biệt giữa **trả lại stage gốc** và **trả stage phụ thuộc**, cùng rủi ro thay đổi semantics.
6. Phân tích cancellation, timeout, retry, context propagation, threading và resource lifecycle.
7. Kiểm thử qua ví dụ Java 17 hoàn chỉnh, có stdout xác định, cả success, failure, lỗi đồng bộ và đường đi bỏ qua proxy.

Phạm vi ở đây là **AOP tổng quát**, không giả định một thư viện nào có khả năng tự mở rộng một join point method-execution thành toàn bộ vòng đời của tác vụ bất đồng bộ.

## 2. Hai ranh giới cần đặt tên rõ

### 2.1. Ranh giới lời gọi phương thức (invocation boundary)

Đây là khoảng một method **được gọi rồi kết thúc lời gọi**, tức trả về bình thường hoặc ném exception ra ngoài. Nếu method đồng bộ thực hiện công việc ngay trong call stack, ranh giới này có thể đủ để đánh giá outcome.

```java
String loadSync(String id) throws java.io.IOException {
    return queryRemote(id); // ví dụ minh họa, queryRemote chưa định nghĩa
}
```

Nếu `queryRemote` chạy đồng bộ rồi trả dữ liệu, `loadSync` chỉ return khi việc nó gọi đã xong. Around advice bao quanh *execution của `loadSync`* (nếu công cụ hỗ trợ và được cấu hình) có thể đo khoảng thời gian của lời gọi này, bao gồm các công việc đồng bộ diễn ra bên trong.

Nếu target ném `IOException` ra khỏi method, after-throwing trên execution phù hợp có thể nhìn thấy lỗi. Nếu target bắt lỗi và trả fallback, execution kết thúc bình thường và after-throwing của execution đó không tự thấy lỗi đã được xử lý nội bộ.

### 2.2. Ranh giới vòng đời công việc bất đồng bộ (task/completion boundary)

Xét:

```java
CompletionStage<String> loadAsync(String id) {
    return requestRemoteLater(id); // giả mã, requestRemoteLater chưa định nghĩa
}
```

Method **có thể trả về bình thường** một stage ngay cả khi công việc chưa thực hiện xong. Kết cục của lời gọi là **trả handle**, còn kết cục của tác vụ được thể hiện bởi **stage** vào thời điểm sau.

```text
T0: caller gọi loadAsync(id)
T1: method lập lịch/gửi công việc
T2: method return stage         <-- invocation hoàn thành
T3: worker bắt đầu làm việc
T4: stage hoàn tất             <-- task outcome có thể quan sát
T5: caller đọc/chuyển tiếp kết quả hoặc lỗi
```

Các mốc T0–T5 là **một kịch bản minh họa**, không phải thứ tự bắt buộc của mọi API: stage có thể hoàn thành ngay trước T2; task có thể bắt đầu trước lúc method return; callback có thể xử lý trước hoặc sau caller tùy tình huống.

**Nguyên tắc:** Đừng suy diễn thứ tự trong một sơ đồ lý tưởng thành bảo đảm scheduling. Hãy xác định sự kiện nào thật sự được công cụ AOP chọn và sự kiện nào được abstraction bất đồng bộ công bố.

### 2.3. Phải phân biệt ít nhất ba độ trễ

| Chỉ số | Mốc bắt đầu | Mốc kết thúc | Ý nghĩa |
| --- | --- | --- | --- |
| **Invocation latency** | Ngay trước lời gọi method. | Method return/throw. | Chi phí kiểm tra, chuẩn bị request, schedule, bọc kết quả. |
| **End-to-end task latency** | Mốc nhận/yêu cầu tác vụ theo hợp đồng. | Tác vụ hoàn tất success/failure/cancelled. | Chi phí tổng thể gồm chờ hàng đợi, xử lý, I/O, retry nếu nằm trong cùng task. |
| **Execution time** | Worker thực sự bắt đầu xử lý. | Worker thực sự kết thúc xử lý. | Không nhất thiết bao gồm thời gian queue/dispatch. |

Nếu chỉ có hook vào method return, không thể chính xác khẳng định đã đo *execution time* hoặc *end-to-end latency*. Nếu chỉ có completion timestamp mà không biết mốc bắt đầu thực sự của worker, không thể tự suy được queue delay.

## 3. Around advice bao quanh cái gì?

Một around advice thường có cấu trúc khái niệm:

```text
around selectedJoinPoint:
    before()
    try:
        result = proceed()
        afterReturn(result)
        return result
    catch error:
        afterThrow(error)
        throw same error
    finally:
        cleanup()
```

Đây là **giả mã**, không phải cú pháp chung. `proceed` là thao tác tiếp tục vào phần bên trong chuỗi advice/target theo cơ chế công cụ, không phải lời hứa rằng phần đó đã hoàn tất mọi tác vụ logic được tạo ra trong tương lai.

Nếu target trả một `CompletionStage` chưa hoàn tất, `proceed()` có thể kết thúc bình thường bằng cách trả chính đối tượng stage. Sau đó:

- Nhánh **after-returning** ở *method invocation* chạy.
- Nhánh **after-throwing** ở *method invocation* **không** chạy chỉ vì stage sau đó hoàn tất exceptionally.
- `finally` của around advice cho *method invocation* chạy khi lời gọi trả stage hoặc ném đồng bộ.
- Callback theo **stage completion** có thể chạy về sau nếu được đăng ký rõ ràng.
- Nếu công cụ AOP có một mô hình join point riêng dành cho hoàn tất task, ngữ nghĩa có thể khác; phải xét API và hợp đồng cụ thể.

### 3.1. Bảng các tình huống

| Tình huống | Method invocation | Task outcome | Advice chỉ bao method sẽ quan sát |
| --- | --- | --- | --- |
| Đồng bộ trả `"OK"` | Return `"OK"`. | Không có stage riêng. | Return bình thường; có thể coi đồng bộ hoàn tất. |
| Đồng bộ ném `IOException` | Throw. | Không có stage riêng. | Throw đồng bộ. |
| Async trả stage chưa hoàn tất | Return stage. | Chưa biết. | Return bình thường với handle. |
| Async trả stage đã hoàn tất success | Return stage. | Đã success. | Vẫn là return stage; có thể kiểm tra trạng thái nếu abstraction cho phép. |
| Async trả stage đã hoàn tất exceptionally | Return stage. | Đã fail. | Vẫn là method return bình thường, không tự thành after-throwing. |
| Async trả stage rồi fail sau đó | Return stage. | Failure sau return. | Chỉ biết method trả stage nếu không gắn hook completion. |
| Async ném lỗi trước khi tạo stage | Throw trực tiếp. | Không có stage hợp lệ. | After-throwing của invocation có thể ghi lỗi đó. |
| Async stage bị cancel | Return stage. | Cancellation/terminal state theo abstraction. | Không tự biết cancellation nếu chỉ nhìn return. |

Khi nói “phương thức bất đồng bộ bị lỗi”, hãy nói rõ đang nói đến **ngoại lệ ném đồng bộ** hay **exceptional completion** của stage.

### 3.2. Một kết quả “bình thường” có thể là một handle của thất bại

`CompletableFuture.failedFuture(new IOException("offline"))` tạo một future hoàn tất exceptionally, nhưng method **trả về** future này vẫn có thể kết thúc **bình thường**. Trạng thái exception là thuộc **giá trị được trả về**, không phải exception thoát ra khỏi lời gọi.

Điều này rất quan trọng với metric: “method return success” và “operation success” không phải luôn cùng định nghĩa.

## 4. `proceed` và quyền điều khiển trong trường hợp bất đồng bộ

Around advice có thể quyết định có gọi continuation không, nhưng quyền đó cần được hiểu đúng khi kết quả là một handle.

### 4.1. Không gọi `proceed`

Ví dụ kiểm soát rate limit có thể trả ngay một **failed stage** thay vì ném exception đồng bộ:

```text
if rateLimited:
    return failedStage(RateLimitError)
return proceed()
```

Ở nhánh trả failed stage, **target có thể không chạy** nhưng *method invocation của advice* vẫn trả bình thường một handle. Advice nằm ở tầng ngoài có thể báo after-returning nếu chỉ xét việc trả handle.

Nếu lựa chọn `throw RateLimitError` thay vì `return failedStage(...)`, caller thấy ngữ nghĩa khác hẳn: throw ngay lúc gọi so với nhận stage báo lỗi khi completion. Một API đã công bố hợp đồng bất đồng bộ không nên chuyển ngẫu nhiên giữa hai hình thức này mà không có yêu cầu rõ ràng.

### 4.2. Gọi `proceed` một lần

`proceed` có thể trả `CompletionStage`. Advice có thể đăng ký callback để quan sát completion và **trả lại stage gốc** cho caller.

Tuy nhiên, việc đính thêm callback không chứng minh task chưa được thực hiện hay callback chắc chắn chạy trên thread hiện tại. `whenComplete` có thể kích hoạt xử lý ngay nếu stage đã hoàn tất; nếu chưa, callback được kích hoạt khi completion xảy ra theo chính sách thực thi của implementation.

### 4.3. Gọi `proceed` nhiều lần

Nếu quanh một API async mà retry bằng cách **gọi `proceed` thêm lần nữa**, có thể tạo **nhiều task khác nhau**. Không thể coi đây là retry cùng một stage.

Ngược lại, nếu chỉ retry bằng cách gọi lại `join()` trên **cùng một future đã exceptional**, bạn thường chỉ đọc lại cùng một trạng thái thất bại, **không tạo attempt mới**. Vì vậy cần phân biệt:

- **Tạo một task mới:** gọi lại phần khởi tạo tác vụ hoặc một hàm retry hợp đồng rõ.
- **Quan sát một stage đã có:** đăng ký callback hoặc đọc completion; không tự khởi động lại công việc.
- **Compose thành chuỗi fallback:** khi stage fail, kích hoạt task khác nếu retry semantics và API cho phép.

Retry bên trong around advice phải tính đến idempotency, sự cố mạng không rõ commit, backoff, timeout tổng và cách chống phát sinh side effect lặp.

## 5. Quan sát toàn bộ tác vụ: hook theo completion

Trong Java, `CompletionStage<T>` cho phép gắn hành vi khi stage hoàn tất, chẳng hạn `whenComplete`, `handle`, `thenApply`, `exceptionally`. Nhưng chúng không đồng nghĩa:

| API / nhóm thao tác | Ý nghĩa điển hình | Rủi ro cần chú ý |
| --- | --- | --- |
| `whenComplete` | Chạy action quan sát giá trị hoặc lỗi, tạo **dependent stage**. | Callback có thể ném lỗi; stage phụ thuộc có thể đổi outcome. |
| `handle` | Tính giá trị mới từ success hoặc failure. | Có thể biến thất bại thành thành công hay ngược lại. |
| `exceptionally` | Có thể phục hồi từ exceptional completion. | Có thể che lỗi gốc nếu trả fallback. |
| `thenApply` | Biến đổi giá trị khi thành công. | Có thể tạo lỗi mới; không chạy trên failure bình thường. |
| `whenCompleteAsync` hoặc biến thể async | Lên lịch callback theo executor/mặc định. | Thread, scheduling, context, latency và exception callback cần kiểm soát. |
| `toCompletableFuture().join()` | Chờ/đọc kết quả ở caller nếu chưa xong. | Có thể block; failure có thể xuất hiện dưới dạng `CompletionException`. |

Mỗi operation trên `CompletionStage` trả về một **stage mới**, trừ những API khác được tài liệu quy định. Vì vậy có hai thiết kế khác biệt.

### 5.1. Trả lại stage gốc: quan sát mà không thay handle của caller

Giả mã Java minh họa (các tên biến là bối cảnh đã có):

```java
CompletionStage<Result> original = invokeAsync();
original.whenComplete((value, failure) -> {
    if (failure == null) {
        recordSuccess();
    } else {
        recordFailure(failure);
    }
});
return original;
```

Ở đây **caller nhận `original`**, không phải stage mà `whenComplete` trả về. Vì thế, exception của callback có thể làm **dependent stage bị lỗi**, nhưng không nhất thiết đổi trạng thái `original`; nếu bỏ qua dependent stage, lỗi callback có thể bị mất tín hiệu quan sát. Cần thiết kế nơi báo lỗi instrumentation hoặc quan sát stage phụ thuộc khi chính sách yêu cầu.

Đoạn này không phải chương trình độc lập; `Result`, `invokeAsync` và các hàm `record...` chỉ là tên minh họa.

### 5.2. Trả stage phụ thuộc: có thể thay đổi semantics của caller

```java
CompletionStage<Result> original = invokeAsync();
return original.whenComplete((value, failure) -> record(value, failure));
```

Ở đây caller nhận stage **phụ thuộc**. Theo hợp đồng `CompletionStage.whenComplete`, kết quả thường được truyền tiếp nếu action không làm lỗi; tuy nhiên lỗi từ callback có thể khiến stage phụ thuộc exceptional, và quy tắc ưu tiên nếu cả source lẫn callback lỗi là một phần hợp đồng API. Điều này nghĩa là **chỉ một thay đổi dòng return** cũng có thể làm một lời gọi tưởng “chỉ quan sát” trở nên có tác động lên kết quả mà caller nhận.

Không thể hứa “đính callback luôn không đổi hợp đồng” nếu không kiểm soát **stage nào được trả ra** và lỗi của callback.

### 5.3. Callback có thể chạy ở thời điểm bất ngờ

Với `CompletableFuture`, một callback không có hậu tố `Async` có thể được chạy bởi thread đang hoàn tất future hoặc thread khác tương tác với completion. Khi stage đã hoàn tất, việc đăng ký callback có thể khiến action thực thi ngay. Không nên dựa vào **thread cố định** hoặc thứ tự tương đối với một log ở nơi khác, trừ khi dùng cơ chế scheduler/executor với hợp đồng thích hợp.

Callback phải không block lâu, không giữ lock gây deadlock và không giả định thread context đã tồn tại. Nếu cần I/O telemetry nặng, dùng pipeline riêng có kiểm soát backpressure hoặc executor phù hợp.

## 6. Ví dụ Java 17 hoàn chỉnh: return-handle khác completion

Chương trình sau dùng **JDK dynamic proxy** để minh họa around advice ở ranh giới phương thức theo interface. `JobApi.schedule` trả `CompletionStage<String>`; target lưu một `CompletableFuture` chưa hoàn tất và chương trình test điều khiển lúc nó hoàn thành.

Mục đích điều khiển completion trong một luồng là để **stdout có thứ tự xác định**, không phụ thuộc sleep, network hoặc scheduler. Ví dụ này **không phải một AOP engine tổng quát** và đối tượng lưu future hiện tại chỉ phục vụ demo tuần tự, **không an toàn cho nhiều job đồng thời**.

Tạo tệp `AsyncBoundaryDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public class AsyncBoundaryDemo {
    interface JobApi {
        CompletionStage<String> schedule(String id);
    }

    static final class JobService implements JobApi {
        private CompletableFuture<String> current;

        @Override
        public CompletionStage<String> schedule(String id) {
            System.out.println("TARGET schedule " + id);
            if ("BAD".equals(id)) {
                throw new IllegalArgumentException("invalid-id");
            }
            current = new CompletableFuture<>();
            return current;
        }

        void succeed(String value) {
            current.complete(value);
        }

        void fail(String message) {
            current.completeExceptionally(
                    new IllegalStateException(message));
        }
    }

    static JobApi observed(JobApi target) {
        return (JobApi) Proxy.newProxyInstance(
                JobApi.class.getClassLoader(),
                new Class<?>[] { JobApi.class },
                (proxy, method, arguments) -> {
                    System.out.println("AROUND enter " + method.getName());
                    try {
                        Object result = method.invoke(target, arguments);
                        System.out.println("AROUND return-handle "
                                + method.getName());

                        if (result instanceof CompletionStage<?> stage) {
                            stage.whenComplete((value, error) -> {
                                if (error == null) {
                                    System.out.println("ASYNC success " + value);
                                } else {
                                    System.out.println("ASYNC failure "
                                            + error.getClass().getSimpleName()
                                            + ": " + error.getMessage());
                                }
                            });
                        }
                        return result;
                    } catch (InvocationTargetException wrapped) {
                        Throwable cause = wrapped.getCause();
                        System.out.println("AROUND sync-throw "
                                + cause.getClass().getSimpleName());
                        throw cause;
                    } finally {
                        System.out.println("AROUND exit-call "
                                + method.getName());
                    }
                }
        );
    }

    public static void main(String[] args) {
        JobService target = new JobService();
        JobApi proxy = observed(target);

        CompletionStage<String> a = proxy.schedule("A");
        System.out.println("CALLER received A");
        target.succeed("done-A");
        System.out.println("CALLER joined A="
                + a.toCompletableFuture().join());

        CompletionStage<String> b = proxy.schedule("B");
        System.out.println("CALLER received B");
        target.fail("network-down");
        try {
            b.toCompletableFuture().join();
        } catch (CompletionException ex) {
            System.out.println("CALLER caught "
                    + ex.getClass().getSimpleName() + ": "
                    + ex.getCause().getClass().getSimpleName());
        }

        try {
            proxy.schedule("BAD");
        } catch (IllegalArgumentException ex) {
            System.out.println("CALLER caught sync " + ex.getMessage());
        }

        CompletionStage<String> direct = target.schedule("DIRECT");
        System.out.println("CALLER direct received");
        target.succeed("done-direct");
        System.out.println("CALLER direct joined "
                + direct.toCompletableFuture().join());
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac AsyncBoundaryDemo.java
java AsyncBoundaryDemo
```

**Stdout kỳ vọng:**

```text
AROUND enter schedule
TARGET schedule A
AROUND return-handle schedule
AROUND exit-call schedule
CALLER received A
ASYNC success done-A
CALLER joined A=done-A
AROUND enter schedule
TARGET schedule B
AROUND return-handle schedule
AROUND exit-call schedule
CALLER received B
ASYNC failure IllegalStateException: network-down
CALLER caught CompletionException: IllegalStateException
AROUND enter schedule
TARGET schedule BAD
AROUND sync-throw IllegalArgumentException
AROUND exit-call schedule
CALLER caught sync invalid-id
TARGET schedule DIRECT
CALLER direct received
CALLER direct joined done-direct
```

### 6.1. Đường thành công A: method return trước completion

1. `proxy.schedule("A")` đi vào proxy: in `AROUND enter schedule`.
2. Target in `TARGET schedule A`, tạo một `CompletableFuture` còn pending và **trả handle**.
3. Handler in `AROUND return-handle schedule`, đăng ký `whenComplete` lên stage gốc rồi in `AROUND exit-call schedule` trong `finally`.
4. Caller in `CALLER received A`: tại thời điểm này **đã có return-handle nhưng chưa có ASYNC success**.
5. `target.succeed("done-A")` hoàn tất future. Vì demo tuần tự và callback không-async, `ASYNC success done-A` xuất hiện tại completion trước câu lệnh `CALLER joined A=...`.
6. `join()` đọc kết quả đã hoàn tất và in `done-A`.

Điều được minh họa là **hai sự kiện tách biệt**. Không dùng thứ tự log của demo này để suy ra mọi callback trong ứng dụng có executor đa luồng đều chạy cùng thread hoặc luôn trước một log ở thread khác.

### 6.2. Đường B: method trả bình thường, task thất bại

`proxy.schedule("B")` cũng in `AROUND return-handle schedule`, tức **method invocation thành công theo nghĩa trả handle**. Sau đó `target.fail("network-down")` hoàn tất future exceptionally với `IllegalStateException`. Callback in `ASYNC failure IllegalStateException: network-down`.

Caller dùng `join()` trên future exceptional nên bắt `CompletionException`; `getCause()` ở đây là `IllegalStateException`. `AROUND sync-throw` **không xuất hiện** cho B bởi target **không ném lỗi trước khi return stage**.

### 6.3. Đường BAD: lỗi đồng bộ ngay trong lời gọi

Target in `TARGET schedule BAD` rồi ném `IllegalArgumentException` trước khi trả future. `Method.invoke` bao cause trong `InvocationTargetException`, handler gỡ wrapper và in `AROUND sync-throw IllegalArgumentException` trước khi ném lại.

Ở nhánh này:

- Không có `AROUND return-handle`.
- Không đăng ký `whenComplete` bởi không nhận được stage.
- Vẫn chạy `AROUND exit-call` trong `finally`.
- Caller bắt exception **ngay tại lời gọi `schedule`**, không phải khi `join`.

### 6.4. Đường DIRECT: bypass proxy

`target.schedule("DIRECT")` gọi thẳng target, nên không xuất hiện bất kỳ `AROUND` hoặc `ASYNC` nào từ handler. Việc target tạo future và future hoàn tất vẫn diễn ra; chỉ có **cơ chế quan sát của proxy** bị bỏ qua. Đây là giới hạn của kỹ thuật proxy trong demo, không phải tuyên bố mọi cơ chế AOP đều giới hạn tương tự.

### 6.5. Stage mà handler trả ra là stage nào?

Handler gọi `stage.whenComplete(...)` để đăng ký quan sát nhưng **bỏ qua stage phụ thuộc được trả từ khi đăng ký**, sau đó `return result`: caller nhận stage gốc của target. Điều này giúp phân biệt việc gắn hook quan sát với thay thế handle. Trong production vẫn phải xử lý lỗi callback, lifecycle, resource, masking và telemetry best-effort để tránh mất lỗi trong stage phụ thuộc bị bỏ qua.

### 6.6. Giới hạn của ví dụ cần nói rõ

- `JobService.current` chỉ hỗ trợ **một job pending tại một thời điểm** trong demo tuần tự; không là thiết kế concurrent production.
- Không có executor, I/O mạng hoặc timer: các sự kiện hoàn tất được `main` gọi trực tiếp để stdout ổn định.
- `whenComplete` là cơ chế completion của Java, còn proxy handler là ranh giới around advice; hai thứ **không phải cùng một join point**.
- Không có pointcut DSL, weaving bytecode hoặc các loại join point khác.
- Lỗi của callback in log không được xử lý đầy đủ; example tập trung vào ngữ nghĩa return-handle và completion.
- `System.out.println` chỉ để quan sát thứ tự, không đại diện cách lưu log an toàn, phi đồng bộ và có kiểm soát trong production.

## 7. Khi mục tiêu là đo độ trễ, đặt điểm bắt đầu và kết thúc đúng chỗ

Một kỹ thuật an toàn để đo *end-to-end latency* của một stage: **lấy `System.nanoTime()` ngay trước invocation** và **tính delta tại completion**. Với Java, `System.nanoTime()` phù hợp đo khoảng thời gian tương đối; không lấy `System.currentTimeMillis()` rồi giả định đồng hồ lịch không thể bị chỉnh.

Giả mã:

```text
started = monotonicClock()
try:
    stage = proceed()
catch synchronousError:
    recordSyncFailure(synchronousError, monotonicClock() - started)
    rethrow

registerCompletionObserver(stage, (value, error) -> {
    duration = monotonicClock() - started
    if error == null:
        recordTaskSuccess(duration)
    else:
        recordTaskFailure(duration, error)
})
return originalStage
```

Cần chú ý **cùng một invocation** chỉ nên tạo một kết quả terminal trong mô hình metric đã chọn. Đừng đồng thời ghi `operation_success` ở nhánh *method return* và `operation_failure` ở callback failure rồi gộp cả hai thành kết quả cuối; chúng đo **hai loại kết cục khác nhau**.

### 7.1. Đồng bộ ném lỗi và async fail: cùng chỉ số hay khác?

Một method async có thể:
- **Throw đồng bộ:** chưa trả handle.
- **Return failed stage:** trả handle, nhưng stage đã exceptional.
- **Return pending stage rồi fail:** lỗi ở completion muộn hơn.

Nếu metric là **logical task outcome**, có thể phân loại cả ba thành “operation failed”, nhưng cần đường ghi **chỉ một lần** cho mỗi logical invocation. Nếu metric là **invocation outcome**, throw đồng bộ là failure còn return stage là normal return, bất kể stage sau đó fail. Hai metric khác nhau và không được trộn tên.

### 7.2. Đo thời gian chờ hàng đợi

Nếu cần tách **queue wait** khỏi **execution time**, phải có dấu mốc worker bắt đầu. Một advice chỉ bao method gửi task không tự biết khi nào scheduler thực sự chạy. Có thể cần instrumentation tại queue/executor/worker hoặc context/tracing xuyên hệ thống.

### 7.3. Timeout không phải lúc nào cũng có nghĩa task đã dừng

Một future có thể hoàn tất exceptionally do timeout, trong khi công việc phía sau vẫn đang chạy, tùy cơ chế cancellation/deadline và executor. Khi đó “time to timeout signal” khác “thời điểm worker dừng tài nguyên”. Không tự coi timeout của handle là bằng chứng thao tác remote đã hủy hoặc chưa commit.

## 8. Exception, cancellation và kết quả async

### 8.1. `CompletionException` và `ExecutionException`

Với `CompletableFuture`, `join()` có thể ném `CompletionException` khi hoàn tất exceptional; `get()` theo hợp đồng `Future` có thể ném `ExecutionException` cho lỗi tính toán, ngoài `InterruptedException` và các ngoại lệ liên quan. Một callback `whenComplete` có thể nhận một `Throwable` biểu diễn lỗi được stage lưu hoặc lớp wrapper; không giả định mọi đường luôn cùng một kiểu Throwable.

Không tùy tiện `getCause()` mọi lúc và ném cause thay thế cho mọi caller. Việc bọc lỗi là một phần hợp đồng cụ thể; nếu cần phân loại lỗi, có thể tìm nguyên nhân sâu hơn để **ghi metric**, nhưng giữ nguyên trạng thái stage nếu mục tiêu là quan sát thuần.

### 8.2. Cancellation là một trạng thái terminal đáng phân loại riêng

`CompletableFuture.cancel(...)` đánh dấu future cancelled theo semantics của Java. Với một số stage, cancellation được biểu diễn như exceptional completion và callback có thể thấy `CancellationException`. Nhưng **cancellation của handle không tự bảo đảm worker đã dừng**, nhất là tác vụ đã gửi tới hệ thống khác.

Nếu phân loại cancellation như failure vận hành, dashboard có thể báo động sai; nếu không ghi cancellation, việc theo dõi tài nguyên có thể thiếu. Nên định nghĩa các outcome như `success`, `failed`, `cancelled`, `timed_out` theo abstraction thực tế, thay vì chỉ kiểm tra `error != null` rồi coi mọi error tương đương.

### 8.3. Lỗi do callback quan sát gây ra

`whenComplete` tạo dependent stage. Nếu callback ném lỗi và bạn **bỏ qua dependent stage**, lỗi callback có thể không được báo ở nơi bạn mong muốn. Nếu **trả dependent stage** cho caller, callback failure có thể tác động lên outcome của caller.

Một chiến lược best-effort instrumentation có thể cố gắng không để lỗi telemetry phá nghiệp vụ, nhưng vẫn cần một kênh báo lỗi riêng được kiểm soát và tránh ném lỗi trong những tình huống tài nguyên cạn kiệt. Một chiến lược audit bắt buộc lại có hợp đồng khác. Không tự động dùng một kiểu catch-all cho mọi chính sách.

## 9. Context propagation và giới hạn thread-local

Một around advice đồng bộ có thể mở scope trace trong một thread:

```text
around:
    context.open(traceId)
    try:
        return proceed()
    finally:
        context.close()
```

Với API async, `finally` phía trên thường chạy khi **method trả handle**, không phải khi job hoàn tất. Nếu context đóng quá sớm, callback/worker có thể mất correlation ID; nếu giữ `ThreadLocal` mở đến task completion trong thread caller, có thể **rò context** và làm ô nhiễm request kế tiếp sử dụng lại thread.

Các điểm cần xác định:

- Context lưu ở **thread**, **logical task**, **request context** hay object gắn với stage?
- Có truyền snapshot context sang executor/worker không?
- Completion callback có khôi phục và dọn context theo scope chính xác không?
- Nếu một task tạo nhiều task con, thông tin quan hệ cha-con truyền như thế nào?
- Nếu stage hoàn tất trên thread do bên khác quản lý, có được phép sửa thread-local tại đó không?
- Cancellation hoặc lỗi trước khi đăng ký callback có gây rò tài nguyên không?

Không có một cách truyền `ThreadLocal` xuyên mọi executor, promise, coroutine hay event loop tự động đúng trong mọi ngôn ngữ. Tư duy AOP giúp xác định biên hook, còn **cơ chế async context propagation** phải được thiết kế riêng.

## 10. Vòng đời resource và cleanup: chỗ `finally` dễ gây sai lầm

### 10.1. Cleanup cho chính lời gọi đồng bộ

Nếu around advice cấp phát tài nguyên chỉ cần cho *invocation* (ví dụ bộ đếm tạm thời hoặc scope dành riêng cho việc tạo request), cleanup trong `finally` sau `proceed` có thể hợp lý.

### 10.2. Cleanup cho toàn bộ tác vụ

Nếu tài nguyên phải sống tới khi job hoàn tất (ví dụ span bao cả network call), dọn trong `finally` của method trả future sẽ **quá sớm**. Cần quản lý tài nguyên bằng callback terminal, lifecycle object hoặc abstraction async chuyên dụng.

Tuy nhiên, cũng không được mở tài nguyên lúc invocation và chỉ đóng khi future hoàn tất nếu stage **không bao giờ complete**. Phải có timeout/deadline, cancellation, giám sát pending tasks và xử lý trường hợp đăng ký callback thất bại. Với tài nguyên nhạy cảm, nên chọn API cung cấp scope rõ thay vì ghép ad hoc từ ThreadLocal và callback.

### 10.3. Cleanup có thể ném exception

Trong Java, lỗi cleanup có thể che lỗi gốc nếu không xử lý đúng. Với async callbacks, lỗi cleanup có thể ảnh hưởng dependent stage hoặc bị bỏ qua tùy thiết kế. Cần giữ nguyên error của công việc khi chính sách là quan sát, quản lý suppressed error hoặc kênh cảnh báo riêng nếu cleanup lỗi.

## 11. Thứ tự nhiều advice và nghĩa của “thành công”

Một chuỗi có thể gồm:

```text
Metrics(Trace(Retry(TaskScheduler)))
```

Tùy vị trí mỗi advice:

- **Metrics ở ngoài Retry:** có thể đo một logical operation gồm nhiều attempts, nếu continuation/abstraction bao gồm toàn bộ retry lifecycle.
- **Metrics ở trong Retry:** có thể ghi từng attempt.
- **Metrics chỉ quanh method submit:** có thể chỉ đo thời gian **lên lịch** của mỗi attempt, chưa đo task completion.
- **Trace được đóng khi method trả stage:** có thể không bao phủ các sự kiện worker phía sau.
- **Trace được đóng trong completion callback:** có thể bao phủ thêm vòng đời task, nếu context và error handling được tổ chức đúng.

Đừng suy từ tên aspect rằng nó đang đo end-to-end. Phải vẽ **điểm bắt đầu, điểm kết thúc và đối tượng kết quả đang được truyền trả**.

### 11.1. Skip và cache

Một quanh advice có thể trả `CompletableFuture.completedFuture(cached)` mà không gọi target. Về mặt invocation, caller nhận stage bình thường; về mặt task, stage đã success ngay. Nếu cache hit không tạo công việc remote, metric “remote execution time” không nên bao gồm trường hợp này như một lần remote call. Nhưng metric “request resolved” có thể bao gồm cache hit.

### 11.2. Retry bất đồng bộ

Nếu cần retry khi **stage thất bại về sau**, việc bắt `Throwable` quanh `proceed()` thường không bắt được failure đó. Phải compose logic retry tại completion hoặc ở task abstraction chuyên dụng, có stop condition, timeout tổng, backoff, idempotency và phân biệt attempt/logical outcome.

## 12. Đừng ép mọi loại bất đồng bộ về cùng một mẫu future

Một số API trả promise/future **eager**; một số abstraction như stream/publisher có thể **lazy**, nghĩa là công việc chỉ bắt đầu khi consumer đăng ký (*subscribe*) hoặc khi có demand. Trong hệ thống event-driven, một method chỉ đăng ký callback mà không trả handle có thể không cho caller cách chờ completion. Với coroutine, suspend/resume và context propagation tạo những biên thực thi riêng.

### 12.1. Future/promise eager

Method gọi một API và trả handle cho tác vụ đã được bắt đầu hoặc lên lịch. Hook vào future completion có thể dùng để quan sát outcome.

### 12.2. Publisher/stream lazy

Method tạo một mô tả pipeline và return **trước khi pipeline thực thi**. Nếu caller subscribe nhiều lần, tùy loại publisher có thể có **nhiều execution** từ cùng object mô tả. Around advice bao quanh method *tạo publisher* chưa chắc bao quanh từng sự kiện data, error, completion hay cancellation. Cần hook theo lifecycle của subscription/stream khi mục tiêu là đo thời gian xử lý thật.

### 12.3. Event/callback không có handle

Một method `registerListener(...)` có thể return ngay còn callback nhận sự kiện nhiều lần về sau. Không có một “completion” duy nhất nếu listener hoạt động tới khi unsubscribe. Cần định nghĩa tuổi thọ subscription, events, errors và cleanup riêng.

**Kết luận phần này:** Không dùng một công thức “method trả nhanh + callback một lần” cho mọi hình thức async. Phải nắm hợp đồng của abstraction được chọn.

## 13. Kiểm thử đúng hai ranh giới

### 13.1. Những ca bắt buộc

| Trường hợp | Kết cục của invocation | Kết cục của tác vụ | Điều cần xác nhận |
| --- | --- | --- | --- |
| Đồng bộ trả result | Return value. | Cùng lời gọi đã xong. | After-returning và thời gian lời gọi đúng. |
| Đồng bộ throw | Throw. | Không có handle. | After-throwing ngay tại call; cleanup. |
| Future pending | Return handle. | Chưa terminal. | Chưa báo task success/failure chỉ vì method return. |
| Future success sau return | Return handle. | Success sau đó. | Completion hook chạy đúng, không ghi trùng. |
| Future fail sau return | Return handle. | Exceptional completion. | Không gán nhầm thành sync-throw. |
| Future đã success trước return | Return handle. | Đã success. | Callback vẫn có thể quan sát completion; thứ tự xử lý hợp lệ. |
| Future đã fail trước return | Return handle. | Đã fail. | Không gọi after-throwing đồng bộ nếu method không throw. |
| Cancellation | Return handle. | Cancelled/terminal. | Phân loại riêng; không khẳng định worker chắc đã dừng. |
| Timeout | Return handle. | Có thể signal timeout. | Định nghĩa có/không ngừng task nền. |
| Handler bỏ qua proxy | Return từ target. | Task tùy target. | Advice không chạy trên đường bypass. |
| Callback instrumentation ném lỗi | Return handle. | Outcome stage gốc/phụ thuộc khác nhau. | Caller có nhận stage nào; lỗi telemetry có được báo? |
| Multi-attempt retry | Một logical call, nhiều attempts. | Có thể cuối cùng success/failure. | Metric và idempotency đúng. |
| Concurrency | Nhiều jobs sống cùng lúc. | Completion không theo thứ tự gửi. | Không trộn trace ID, attempt, resource scope. |
| Never-completing stage | Return handle. | Không terminal. | Deadline/watchdog và resource cleanup có thiết kế. |

### 13.2. Cách làm test không phụ thuộc `Thread.sleep`

Dùng `CompletableFuture` chưa hoàn tất, gọi qua advice và assert:

1. Ngay sau method return: bộ đếm invocation-return = 1; task-success = 0; task-failure = 0.
2. Gọi `future.complete(value)`: task-success = 1; task-failure = 0.
3. Tạo một future khác rồi `completeExceptionally(error)`: task-failure tăng, sync-throw **không tăng**.
4. Cho target ném trước khi trả future: sync-throw tăng; không có task callback từ nhánh đó.
5. Gọi target trực tiếp không qua proxy: không có marker/metric do advice proxy ghi.

Với test đa luồng, thay sleep bằng synchronization primitives như `CountDownLatch`, barrier hoặc stage được điều khiển để assertions diễn ra sau khi sự kiện mong đợi đã thực sự phát sinh. Chỉ dựa vào số mili giây chờ có thể khiến test flakey.

### 13.3. Hai assertion quan trọng nhất

- **Invocation đã return** không tự làm bộ đếm **task completed** tăng.
- **Stage completed exceptionally** không tự làm bộ đếm **method threw synchronously** tăng.

Nếu dashboard hoặc test đang coi hai mệnh đề này tương đương, cách đo đã sai ranh giới.

## 14. Các lỗi thiết kế thường gặp

**“Around advice đo tới lúc `proceed()` return nghĩa là đo xong toàn bộ job.”** Không đúng khi `proceed` chỉ trả handle chưa hoàn tất.

**“Stage hoàn tất exceptionally thì after-throwing của method đã phải chạy.”** Sai nếu method trả stage bình thường. Failure nằm ở completion event, không phải synchronous throw.

**“Đính `whenComplete` rồi trả stage phụ thuộc hay stage gốc cũng như nhau.”** Sai. Callback có thể thay đổi trạng thái stage phụ thuộc khi tự ném lỗi.

**“Để đo end-to-end chỉ cần `Thread.sleep` đủ lâu.”** Không. Dùng completion signal thật và nguồn thời gian đơn điệu; thời gian chờ tùy đoán không định nghĩa outcome.

**“Callback `whenComplete` luôn chạy trên thread đã gọi advice.”** Không có bảo đảm như vậy; không-async callback có thể chạy trên thread tham gia completion, hoặc khi đăng ký nếu stage đã completed.

**“Cancellation của future luôn dừng network request.”** Không. Phụ thuộc API và việc cancellation có được truyền xuống hoạt động thực tế hay không.

**“Đóng trace trong `finally` của method luôn đúng.”** Chỉ đúng nếu trace scope thực sự là lời gọi đồng bộ; nếu trace phải bao task, đóng lúc method return có thể quá sớm.

**“Chỉ cần `ThreadLocal` để correlation tự truyền qua mọi async job.”** Sai. Phải có context propagation và cleanup cho từng biên executor/callback.

**“Gọi lại `join()` là retry tác vụ.”** Sai. `join` chỉ chờ/đọc trạng thái của stage đã có, không tự tạo attempt mới.

**“Gọi `proceed` hai lần là retry vô hại.”** Không. Có thể tạo hai task/side effects, yêu cầu idempotency và xác minh engine cho phép.

**“Mọi async API đều trả `CompletableFuture` eager.”** Sai. Publisher/stream/callback và coroutine có mô hình lifecycle khác.

## 15. Bài tập vận dụng

### Bài 1 — Phân loại outcome

Một method `submit()` trả `CompletableFuture<String>` ngay, rồi remote call thất bại 300 ms sau. Around advice in “success” sau khi `proceed()` trả về.

**Câu hỏi:** Log có sai không?

**Giải thích:** Nếu “success” được định nghĩa là **method return handle**, log nhất quán nhưng dễ gây hiểu nhầm. Nếu nó được dùng làm **task success**, log sai. Cần đổi tên metric hoặc gắn completion hook ở ranh giới tác vụ.

### Bài 2 — Hai lần ném lỗi

Một nhánh `submit()` ném `IllegalArgumentException` trước khi tạo future; nhánh khác trả `failedFuture(new IllegalArgumentException(...))`.

**Đáp án:** Nhánh đầu là synchronous throw. Nhánh sau là normal method return với exceptional stage. Advice chỉ quan sát method execution nhìn thấy hai kết cục khác nhau.

### Bài 3 — Callback lỗi

Advice dùng `return stage.whenComplete(logger)` và `logger` thỉnh thoảng ném exception. Đây có còn là instrumentation trong suốt không?

**Đáp án:** Không được bảo đảm. Caller nhận dependent stage, và callback failure có thể làm thay đổi outcome được quan sát. Cần có chính sách handling rõ, xem hợp đồng của `whenComplete` và chọn có trả stage gốc hay stage phụ thuộc.

### Bài 4 — Retry

Một `CompletionStage` đã exceptional. Bạn gọi `join()` ba lần.

**Đáp án:** Không tự tạo ba task; đó là ba lần đọc cùng stage. Muốn retry phải tạo hoặc compose các attempt mới, có điều kiện và idempotency phù hợp.

### Bài 5 — Span và context

Một around advice mở span, gọi async method, đóng span trong `finally`. Công việc còn chạy một giây sau return.

**Đáp án:** Span chỉ bao phần invocation. Nếu span cần bao cả tác vụ, phải quản lý scope tới completion mà không giữ nhầm ThreadLocal trên thread caller, tránh rò resource và có xử lý timeout/cancel.

### Bài 6 — Test thời gian

Test sử dụng `Thread.sleep(100)` rồi assert future đã xong. Vì sao không đáng tin?

**Đáp án:** Scheduler, CI load, I/O, GC và môi trường có thể làm task chậm hơn hoặc kết thúc theo thứ tự khác. Dùng future được điều khiển/latch/barrier và assert sau tín hiệu hoàn tất.

### Bài 7 — Bypass

Target trả stage và được gọi trực tiếp thay vì qua proxy. Callback do proxy advice đăng ký có chạy không?

**Đáp án:** Không, vì handler proxy không nhận invocation. Stage bản thân vẫn có thể hoàn tất; công cụ AOP khác có weaving có thể có độ phủ khác.

## 16. Checklist trước khi triển khai

1. **Join point:** Around advice chọn loại điểm nào và cơ chế AOP thật sự thấy những đường gọi nào?
2. **Abstraction:** Result đồng bộ, future eager, publisher lazy hay callback/subscription có vòng đời riêng?
3. **Outcome:** “Success” được định nghĩa là return-handle, task completion, commit hay consumer đã nhận dữ liệu?
4. **Thời gian:** Điểm bắt đầu/kết thúc có đo invocation, queue wait, execution hay end-to-end?
5. **Errors:** Đã phân biệt sync throw, exceptional completion, cancellation, timeout và callback failure?
6. **Result identity:** Caller được trả stage gốc hay stage phụ thuộc? Có biến đổi kết quả/exception không?
7. **Context:** Trace ID, security context và ThreadLocal được truyền/dọn đúng qua executor/callback?
8. **Resources:** Scope cần sống tới method return hay task terminal? Nếu task không complete thì sao?
9. **Retry/cache:** Có thể skip hoặc gọi lại continuation không, và nghiệp vụ có idempotent không?
10. **Concurrency:** Có thể nhiều jobs tồn tại cùng lúc; state của aspect có race hoặc lẫn task không?
11. **Observability:** Metric có tránh đếm trùng attempt và logical operation, đã giới hạn dữ liệu nhạy cảm?
12. **Validation:** Có test pending, immediate completion, delayed success/failure, sync throw, cancel, bypass và callback error?

## Kết luận

**Bao quanh một lời gọi đồng bộ** chỉ giúp around advice nắm khoảng từ lúc lời gọi bắt đầu đến khi method **return hoặc throw**. Khi method trả một handle bất đồng bộ, khoảng đó có thể kết thúc **trước khi tác vụ thực sự xong**. Muốn bao trọn tác vụ phải dựa vào **completion/error/cancellation semantics của abstraction bất đồng bộ**, quản lý context, resource và stage được trả về một cách có chủ đích. Tên “around” không tự kéo dài join point của method đến tương lai. Đo đúng ranh giới, giữ nguyên hợp đồng kết quả/ngoại lệ, và kiểm thử thời điểm return-handle độc lập với thời điểm task-completion là nền tảng để áp dụng AOP có kiểm soát.


</details>

- [Quay lại đầu trang](#back-to-top)

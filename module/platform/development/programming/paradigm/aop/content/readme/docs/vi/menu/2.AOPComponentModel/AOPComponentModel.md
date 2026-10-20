<a id="back-to-top"></a>

# Mô hình thành phần

## Menu
- [Aspect là đơn vị đóng gói mối quan tâm cắt ngang](#aop-component-model-001)
- [Join point là điểm có thể can thiệp trong mô hình thực thi](#aop-component-model-002)
- [Pointcut là điều kiện lựa chọn tập điểm can thiệp](#aop-component-model-003)
- [Advice là hành vi được thực hiện tại điểm đã chọn](#aop-component-model-004)
- [Weaving là quá trình kết hợp aspect với chương trình](#aop-component-model-005)
- [Chương trình gốc, bộ dệt và kết quả sau khi dệt](#aop-component-model-006)
- [Phân biệt chọn vị trí, truyền ngữ cảnh và thực hiện hành vi](#aop-component-model-007)
- [Luồng tương tác giữa mã nghiệp vụ và một aspect](#aop-component-model-008)
- [Khả năng của mô hình AOP phụ thuộc ngôn ngữ và công cụ](#aop-component-model-009)

## <a id="aop-component-model-001">Aspect là đơn vị đóng gói mối quan tâm cắt ngang</a>

<details>
<summary>Xem chi tiết</summary>

# Aspect là đơn vị đóng gói mối quan tâm cắt ngang

Trong lập trình hướng khía cạnh (*Aspect-Oriented Programming*, AOP), **aspect** là đơn vị mô-đun hóa một **mối quan tâm cắt ngang** (*cross-cutting concern*): một chính sách hoặc hành vi cần tác động nhất quán tại nhiều vị trí của chương trình. Thay vì rải cùng một đoạn mã vào các lớp nghiệp vụ, aspect gom **hành vi cần thực hiện** và **quy tắc xác định nơi áp dụng** thành một đơn vị có tên, có trách nhiệm, có thể xem xét và kiểm thử.

Ví dụ: `CheckoutService`, `ReportService` và `MessageService` thực hiện những nghiệp vụ khác nhau, nhưng đều cần ghi lại thời gian thực thi. Nếu từng phương thức tự ghi thời gian, logic kỹ thuật sẽ bị phân tán. Ta có thể tạo một aspect `ExecutionTiming` để nêu quy tắc đo thời gian tại những ranh giới được chọn. Ba lớp nghiệp vụ vẫn tập trung vào mục đích riêng.

**Điểm cốt lõi:** Một aspect không chỉ là “một hàm chạy trước/sau”. Aspect thể hiện một *chính sách xuyên suốt* cùng cách gắn chính sách ấy vào chương trình. AOP nói chung là mô hình thiết kế và kết hợp các đơn vị này; cú pháp định nghĩa aspect, tập join point và kỹ thuật thực thi phụ thuộc hệ thống AOP cụ thể.

## 1. Vì sao cần một đơn vị đóng gói riêng?

Mô-đun thông thường thường được tổ chức theo các trách nhiệm dọc, chẳng hạn “xử lý đơn hàng”, “quản lý người dùng”, “tạo báo cáo”. Một số yêu cầu lại cắt qua nhiều trách nhiệm dọc: logging, tracing, kiểm toán (*audit*), đo hiệu năng, kiểm soát truy cập hoặc quản lý ngữ cảnh tài nguyên.

Hai hiện tượng thường gặp khi xử lý trực tiếp tại từng điểm:

- **Scattering – phân tán:** Một quy tắc chung xuất hiện trong nhiều phương thức hoặc lớp. Khi quy tắc thay đổi, phải sửa nhiều vị trí.
- **Tangling – đan xen:** Một phương thức vừa thực hiện nghiệp vụ vừa chứa nhiều đoạn xử lý kỹ thuật không trực tiếp mô tả nghiệp vụ.

Ví dụ minh họa ý tưởng, **không phải chương trình Java độc lập**:

```java
// BEFORE: ba nơi có cùng mẫu xử lý kỹ thuật.
void createOrder() {
    audit("createOrder: begin");
    createOrderCore();
    audit("createOrder: done");
}

void cancelOrder() {
    audit("cancelOrder: begin");
    cancelOrderCore();
    audit("cancelOrder: done");
}

void submitReport() {
    audit("submitReport: begin");
    submitReportCore();
    audit("submitReport: done");
}
```

Đoạn này còn thiếu xử lý ngoại lệ, nghĩa là bản ghi “done” không xuất hiện khi thao tác thất bại. Nếu bổ sung nhánh lỗi và thông tin ngữ cảnh ở từng phương thức, mã lặp sẽ phình to và dễ khác nhau giữa các nơi.

Với AOP, ta hướng đến:

```text
Mô-đun nghiệp vụ             Mô-đun aspect
------------------          ----------------------------
Order operations             Audit aspect
Report operations    <----   - Chọn thao tác cần audit
Message operations           - Trước: ghi nhận bắt đầu
                             - Thành công: ghi nhận kết quả
                             - Lỗi: ghi nhận thất bại

        Cơ chế AOP kết hợp hai phía tại điểm thực thi được chọn.
```

Đây là sự tách **trách nhiệm sở hữu**: nghiệp vụ quyết định *làm việc gì*; aspect quản lý một chính sách *áp dụng tại các ranh giới nào và theo cách nào*. Việc tách này có ý nghĩa khi nó làm trách nhiệm rõ ràng hơn, không phải chỉ để giảm vài dòng code.

## 2. Những thành phần được đóng gói trong một aspect

Một mô hình aspect thường liên hệ các khái niệm sau:

| Thành phần | Vai trò trong aspect | Câu hỏi cần trả lời |
| --- | --- | --- |
| **Concern** | Mục đích duy nhất của mô-đun | Aspect đang giải quyết vấn đề xuyên suốt nào? |
| **Join point** | Điểm thực thi *có thể* chịu tác động, theo mô hình AOP đang dùng | Có những điểm thực thi nào được hệ thống hỗ trợ? |
| **Pointcut** | Quy tắc chọn một tập join point | Chính xác thao tác nào thuộc phạm vi áp dụng? |
| **Advice** | Hành vi bổ sung tại join point được chọn | Trước, sau, khi lỗi hay bao quanh cần làm gì? |
| **Context** | Thông tin được phép quan sát/đưa vào advice | Có thể đọc phương thức, đối số, kết quả hay ngữ cảnh nào? |
| **Ordering/precedence** | Quy tắc kết hợp khi nhiều aspect cùng khớp | Aspect nào bao ngoài, aspect nào ở trong? |
| **Lifecycle/state** | Cách tồn tại và quản lý trạng thái, nếu có | State dùng chung hay theo đối tượng/lần gọi? |

**Không phải mọi công cụ đều yêu cầu khai báo các mục trên cùng một file hoặc class.** Có công cụ diễn đạt pointcut và advice bằng khai báo chuyên biệt, có công cụ biểu diễn aspect qua cấu hình và interceptor, có công cụ ghép vào bytecode. Khái niệm “aspect là đơn vị đóng gói” mô tả **ranh giới trách nhiệm**, không ép một hình thức cú pháp duy nhất.

### 2.1. Aspect khác join point, pointcut và advice

Xét yêu cầu “ghi audit cho mọi lần thực thi thao tác phát hành dữ liệu”:

- **Join point:** một lần thực thi cụ thể của phương thức `publish(...)`.
- **Pointcut:** tiêu chí chọn những lần thực thi `publish(...)` thuộc phạm vi phát hành.
- **Advice:** mã ghi audit trước thao tác, ghi thành công hoặc thất bại sau thao tác.
- **Aspect:** đơn vị `PublicationAudit` gói quy tắc chọn và hành vi audit.

Nếu chỉ viết một advice nhưng rải tiêu chí chọn ở nhiều nơi không liên quan, trách nhiệm chưa thật sự được mô-đun hóa tốt. Ngược lại, một aspect có thể chứa **nhiều advice/pointcut liên quan cùng một concern**, ví dụ audit bắt đầu, audit thành công và audit thất bại.

### 2.2. Cohesion: một aspect nên có một lý do thay đổi

Một aspect `AuditTrail` có thể chứa các quy tắc ghi sự kiện audit cho nhiều loại thao tác nếu cùng chung chính sách. Nhưng ghép cả audit, cache, phân quyền, retry và chuyển đổi ngoại lệ vào `CommonAspect` khiến đơn vị này có nhiều lý do thay đổi, rất khó kiểm thử và bảo trì.

Quy tắc thiết kế hữu ích: **gom theo một mục tiêu xuyên suốt nhất quán**. Không phải “mỗi phương thức một aspect”, cũng không phải “mọi việc kỹ thuật vào một aspect lớn”. Nếu hai concern có quy tắc áp dụng, độ tin cậy hoặc vòng đời thay đổi khác nhau, chúng thường nên có mô-đun riêng.

## 3. Aspect và hợp đồng hành vi

Một aspect dù nằm ngoài mã nghiệp vụ vẫn có thể ảnh hưởng đến kết quả quan sát được của chương trình. Bởi vậy aspect cần có **hợp đồng hành vi** (*behavioral contract*) rõ ràng.

### 3.1. Phạm vi can thiệp

Viết được một pointcut “chọn mọi phương thức” không có nghĩa đó là lựa chọn đúng. Một phạm vi dễ kiểm soát thường trả lời được:

1. Những thao tác nào **phải** khớp?
2. Những thao tác nào **không được** khớp?
3. Lời gọi qua interface, lời gọi trực tiếp target và lời gọi nội bộ có đi qua cơ chế can thiệp hay không?
4. Khi đổi tên, overload, di chuyển lớp hoặc bổ sung phương thức, quy tắc khớp có đổi ngoài ý muốn không?

Đây là một phần của hợp đồng aspect, vì **khớp thiếu** làm bỏ lọt chính sách, còn **khớp thừa** làm chính sách tác động nhầm chỗ.

### 3.2. Mức độ tác động

Không phải mọi advice đều “chỉ quan sát”:

- **Observation:** ghi log/metric/tracing, cố gắng không đổi giá trị trả về hoặc ngoại lệ.
- **Guard:** kiểm tra trước khi cho phép thao tác tiếp tục; có thể chặn thao tác.
- **Transformation:** biến đổi đối số, kết quả hoặc ngoại lệ nếu cơ chế cho phép.
- **Execution control:** around advice có thể quyết định lúc nào gọi tiếp, có gọi tiếp hay không, thậm chí gọi lại.

Mức độ can thiệp càng lớn, yêu cầu tài liệu, kiểm thử và xác định thứ tự càng cao. “Dùng AOP” tự nó không bảo đảm advice giữ nguyên semantics của phương thức đích.

### 3.3. Đối số, kết quả và ngoại lệ

Một aspect được phép quan sát/biến đổi dữ liệu gì phụ thuộc API và cơ chế weaving. Thiết kế cần trả lời: có cần đọc đối số không; kết quả có được thay thế không; ngoại lệ có cần truyền nguyên vẹn không; liệu việc ghi audit thất bại có phải khiến nghiệp vụ thất bại?

Ví dụ, một aspect đo thời gian đúng nghĩa thường dùng cấu trúc `try/finally` quanh điểm chuyển tiếp để vẫn ghi nhận khi target ném lỗi. Một aspect chỉ dùng “after returning” sẽ không quan sát được các lần kết thúc bằng ngoại lệ. Một aspect audit có thể cần phân biệt rõ hai trường hợp ấy.

## 4. Đi từ định nghĩa aspect đến hành vi đang chạy

Ở mức khái niệm, quá trình áp dụng gồm bốn bước:

1. **Khai báo một concern** và những advice đại diện cho hành vi chung.
2. **Định nghĩa pointcut** để chỉ ra tập join point mà aspect được phép tác động.
3. **Kết hợp (weave)** aspect với mã đích bằng cơ chế mà công cụ hỗ trợ.
4. **Thực thi**: khi chương trình đi qua một join point khớp, advice của aspect được chạy theo ngữ nghĩa và thứ tự tương ứng.

```text
                +-----------------------+
                | Aspect: AuditTrail    |
                | concern = audit       |
                | pointcut = publish(*) |
                | advice = around       |
                +-----------+-----------+
                            |
                     kết hợp/weaving
                            |
Caller -----> [điểm publish được chọn] -----> Target.publish()
                            |
               audit attempt / result / error
```

Sơ đồ chỉ mô tả *ý tưởng*, không xác định cú pháp pointcut chuẩn. Điểm kết hợp có thể được cài bằng proxy, interceptor, biến đổi bytecode lúc biên dịch hoặc lúc nạp class. **Không mặc định có proxy trong mọi kỹ thuật AOP.**

Một khác biệt quan trọng là **lời gọi phương thức** (*method call*) và **thực thi phương thức** (*method execution*) có thể là hai loại join point khác nhau. Công cụ chỉ hỗ trợ interception tại proxy thường quan sát được những lời gọi thực sự đi qua proxy; một công cụ weaving ở cấp bytecode có thể nhận diện thêm loại điểm thực thi, tùy khả năng của nó.

## 5. Ví dụ Java 17+: hai aspect là hai mô-đun độc lập

Ví dụ dưới đây dùng Java SE để tạo một cơ chế AOP nhỏ trên `java.lang.reflect.Proxy`. Mỗi aspect **tự chứa pointcut `matches` và advice `around`**. Cơ chế ghép advice chung nằm trong một hàm trung gian, không nằm trong nghiệp vụ. Đây là ví dụ học tập chạy được, **không phải một triển khai AOP đầy đủ**.

Lưu toàn bộ vào tệp **`AspectModuleDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public class AspectModuleDemo {
    // Mô tả thao tác bên trong chuỗi advice.
    @FunctionalInterface
    interface Proceed {
        Object call() throws Throwable;
    }

    // Mỗi mô-đun aspect tự quyết định nơi áp dụng và hành vi.
    interface AspectRule {
        boolean matches(Method method);

        Object around(Method method, Object[] args, Proceed next)
                throws Throwable;
    }

    static final class TraceAspect implements AspectRule {
        @Override
        public boolean matches(Method method) {
            return method.getDeclaringClass() != Object.class;
        }

        @Override
        public Object around(Method method, Object[] args, Proceed next)
                throws Throwable {
            System.out.println("[trace:begin] " + method.getName());
            try {
                Object result = next.call();
                System.out.println("[trace:ok] " + method.getName());
                return result;
            } catch (Throwable error) {
                System.out.println("[trace:failed] " + method.getName());
                throw error;
            } finally {
                System.out.println("[trace:end] " + method.getName());
            }
        }
    }

    static final class PublicationAuditAspect implements AspectRule {
        @Override
        public boolean matches(Method method) {
            return method.getName().equals("publish")
                    && method.getParameterCount() == 1;
        }

        @Override
        public Object around(Method method, Object[] args, Proceed next)
                throws Throwable {
            System.out.println("[audit:attempt] " + method.getName());
            try {
                Object result = next.call();
                System.out.println("[audit:ok] " + method.getName());
                return result;
            } catch (Throwable error) {
                System.out.println("[audit:failed] " + method.getName());
                throw error;
            }
        }
    }

    public interface PublicationService {
        String publish(String id);
        String status();
    }

    public static final class PublicationServiceImpl
            implements PublicationService {
        @Override
        public String publish(String id) {
            if ("bad".equals(id)) {
                throw new IllegalArgumentException("id bị từ chối");
            }
            return "published:" + id;
        }

        @Override
        public String status() {
            return "READY";
        }
    }

    static Object invokeTarget(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static PublicationService weave(
            PublicationService target, List<AspectRule> aspects) {
        return (PublicationService) Proxy.newProxyInstance(
                PublicationService.class.getClassLoader(),
                new Class<?>[]{PublicationService.class},
                (proxy, method, args) -> {
                    Proceed chain = () -> invokeTarget(method, target, args);

                    // Aspect đầu danh sách là lớp ngoài cùng.
                    for (int i = aspects.size() - 1; i >= 0; i--) {
                        AspectRule aspect = aspects.get(i);
                        if (aspect.matches(method)) {
                            Proceed next = chain;
                            chain = () -> aspect.around(method, args, next);
                        }
                    }

                    return chain.call();
                });
    }

    public static void main(String[] args) {
        PublicationService target = new PublicationServiceImpl();
        PublicationService service = weave(
                target,
                List.of(new TraceAspect(), new PublicationAuditAspect()));

        System.out.println(service.publish("P-7"));
        System.out.println(service.status());

        try {
            service.publish("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("caller:" + error.getMessage());
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa file, với nội dung tệp lưu bằng UTF-8:

```bash
javac -encoding UTF-8 AspectModuleDemo.java
java AspectModuleDemo
```

Kết quả chuẩn:

```text
[trace:begin] publish
[audit:attempt] publish
[audit:ok] publish
[trace:ok] publish
[trace:end] publish
published:P-7
[trace:begin] status
[trace:ok] status
[trace:end] status
READY
[trace:begin] publish
[audit:attempt] publish
[audit:failed] publish
[trace:failed] publish
[trace:end] publish
caller:id bị từ chối
```

### 5.1. Aspect đóng gói những gì trong ví dụ?

**`TraceAspect`** là một mô-đun theo concern “theo dõi luồng thực thi”:

- `matches` chọn phương thức do interface nghiệp vụ khai báo, ngoại trừ các phương thức khai báo trực tiếp trên `Object`.
- `around` in mốc bắt đầu, thành công/thất bại và kết thúc.
- `try/catch/finally` bảo đảm dòng kết thúc được in dù thao tác ném ngoại lệ.

**`PublicationAuditAspect`** là một mô-đun khác theo concern “audit thao tác phát hành”:

- `matches` chọn các phương thức tên `publish` có đúng một tham số trong phạm vi proxy demo.
- `around` ghi nhận việc thử phát hành và kết cục của lời gọi.
- `status()` không khớp pointcut nên không ghi audit, nhưng vẫn được trace.

**`PublicationServiceImpl`** là target: chỉ chứa thao tác thật. Nó không cần gọi `TraceAspect` hay `PublicationAuditAspect`. Tính độc lập của target khỏi các lớp aspect là ví dụ của việc *giảm đan xen*, nhưng caller bắt buộc gọi qua `service` (proxy) để advice chạy.

**`weave(...)`** là cơ chế ghép chuỗi cho ví dụ. Nó tách khỏi hai concern nên việc thêm một aspect mới không phải sửa `PublicationServiceImpl`. Tên hàm “weave” ở đây chỉ tính năng ghép advice bằng proxy tại runtime; nó không thực hiện compile-time hoặc bytecode weaving.

### 5.2. Vì sao thứ tự log như vậy?

Danh sách aspect là `TraceAspect` trước `PublicationAuditAspect`. Vòng lặp xây từ cuối lên nên trace trở thành **lớp ngoài**, audit là lớp trong:

```text
Trace.before
    Audit.before
        Target.publish()
    Audit.after-success-or-error
Trace.after-success-or-error
Trace.finally
```

Khi `publish("bad")` ném `IllegalArgumentException`, `Method.invoke` tạm bọc ngoại lệ vào `InvocationTargetException`. Hàm `invokeTarget` mở lớp bọc, để cả hai aspect nhìn thấy và chuyển tiếp lỗi gốc. Nhánh “ok” không chạy khi có lỗi.

### 5.3. Các giới hạn có chủ đích

Đây là cơ chế interception qua interface proxy, vì vậy:

- `target.publish(...)` gọi trực tiếp sẽ **không** kích hoạt hai aspect.
- Lời gọi nội bộ từ target qua `this` không đi ngược qua proxy.
- Pointcut kiểm tra tên và số tham số để dễ học; trong hệ thống lớn, hai điều kiện này có thể khớp nhầm overload hoặc không ổn định khi refactor.
- Không có hỗ trợ riêng cho truy cập trường, constructor, compile-time weaving hoặc ngôn ngữ pointcut tổng quát.
- Thông tin audit được in ra stdout chỉ nhằm minh họa; audit thực tế cần chính sách bảo mật, lưu trữ và độ tin cậy riêng.

Vì thế, **giá trị mô hình** của ví dụ nằm ở `AspectRule`: một đơn vị sở hữu đồng thời tiêu chí áp dụng và hành vi xuyên suốt. Kỹ thuật proxy chỉ là một cách thực hiện mô hình đó.

## 6. Aspect khác utility class, decorator và interceptor ở đâu?

Các kỹ thuật cùng có thể xử lý mã dùng chung, nhưng cách biểu đạt trách nhiệm khác nhau.

| Cách tổ chức | Điều gì được đóng gói? | Cách áp dụng |
| --- | --- | --- |
| **Utility/function** | Thao tác có thể tái sử dụng | Call site chủ động gọi hàm. |
| **Decorator** | Hành vi bao quanh một đối tượng/interface được bọc | Đối tượng được xây dựng hoặc cấu hình qua wrapper. |
| **Interceptor/middleware** | Hành vi xử lý tại một pipeline hay ranh giới lời gọi | Áp dụng ở pipeline/hook đã định. |
| **Aspect** | Một concern cùng quy tắc chọn điểm áp dụng và advice | Kết hợp tại những join point mà mô hình AOP cho phép. |

Một interceptor có thể làm *cơ chế thực thi* cho một aspect, nhưng không phải interceptor nào cũng là aspect đầy đủ. Decorator có thể rất phù hợp khi chỉ cần bao quanh một đối tượng hoặc giao diện cụ thể và muốn giữ tất cả cấu hình tường minh.

**Khi nào aspect đem lại lợi ích hơn?** Khi quy tắc phủ nhiều thành phần rời nhau và cần biểu đạt tập vị trí áp dụng như một phần của chính sách. Nếu chỉ có một điểm gọi duy nhất, một hàm hoặc decorator rõ ràng có thể dễ bảo trì hơn.

Aspect cũng **không thay thế encapsulation của OOP**. Lớp nghiệp vụ vẫn giữ bất biến và hành vi miền nghiệp vụ; aspect đóng gói chính sách cắt ngang ở ranh giới thích hợp.

## 7. Thiết kế ranh giới của aspect: chọn đúng tập điểm thực thi

Một aspect chỉ được đóng gói tốt khi **pointcut chọn đúng các thao tác thuộc cùng mối quan tâm cắt ngang**. Ví dụ, `PublicationAuditAspect` phải ghi audit cho hành vi phát hành thật, không phải mọi helper có tên giống `publish`.

| Cách chọn | Lợi thế với trách nhiệm của aspect | Rủi ro cần kiểm chứng |
| --- | --- | --- |
| Tên/chữ ký | Dễ xác định trên giao diện mã | Đổi tên, overload hoặc sửa chữ ký có thể chọn thiếu/thừa |
| Annotation/metadata | Diễn đạt rõ “thao tác cần audit” | Phải đọc đúng interface/implementation, retention và lookup; marker không tự kích hoạt advice |
| Package/kiểu | Gắn concern với ranh giới kiến trúc | Thêm/chuyển helper, callback hoặc class làm đổi tập match |
| Điều kiện động | Chọn theo đối số, receiver hoặc ngữ cảnh | Tốn chi phí, khác kết quả theo luồng/dữ liệu; không tự bảo vệ mọi đường vào |

Nếu matcher Java dùng reflection tại runtime, annotation phải có retention phù hợp (chẳng hạn `RetentionPolicy.RUNTIME`) và được tra cứu tại đúng method. Một bộ weaving bytecode có thể dùng cơ chế metadata khác.

Kiểm thử cả **positive** (phải chọn) và **negative** (không được chọn). Khi giới hạn pointcut, ưu tiên hợp đồng của concern hơn wildcard khiến ca hiện tại đi qua. Cách đọc bốn trường hợp:

```text
                         Pointcut chọn?
Cần áp dụng + Có chọn      → ĐÚNG
Cần áp dụng + Không chọn   → LỌT CHÍNH SÁCH
Không cần + Có chọn        → ÁP DỤNG NHẦM
Không cần + Không chọn     → ĐÚNG
```

## 8. Aspect phối hợp với nhau: composition và precedence

Khi nhiều aspect chọn cùng một join point, chương trình có một **chuỗi hành vi tổng hợp**. Chuỗi này không nhất thiết giao hoán: đổi thứ tự có thể đổi kết quả quan sát được.

Ví dụ có hai aspect: `Authorization` (kiểm tra quyền) và `Audit` (ghi nhật ký thao tác). Nếu audit nằm ngoài authorization, nó có thể quan sát cả yêu cầu bị từ chối. Nếu audit nằm trong authorization, một yêu cầu bị chặn bên ngoài có thể không được audit. Không có một thứ tự luôn đúng cho mọi ứng dụng; hợp đồng của hệ thống phải quyết định.

Hai hệ quả quan trọng:

1. **Advice không chỉ nối đuôi nhau:** với around advice, hành vi lồng nhau như các wrapper; nhánh hoàn tất chạy ngược thứ tự bắt đầu.
2. **Precedence là một quyết định kiến trúc:** khi aspect giao nhau, cần ghi rõ thứ tự bằng cơ chế mà công cụ hỗ trợ, không dựa trên giả định thứ tự ngẫu nhiên.

Một sơ đồ kết hợp:

```text
             Caller
               |
        Security around
               |
          Audit around
               |
           Target work
               |
       quay ra qua Audit
               |
      quay ra qua Security
               |
             Caller
```

Thay `Security` và `Audit` theo thứ tự ngược có thể làm thay đổi chính sách ghi nhận các yêu cầu bị chặn. Nếu một advice cố ý **không gọi tiếp** (*proceed*), các advice bên trong và target có thể không chạy. Nếu gọi tiếp hai lần, target có thể chạy hai lần. Điều này đặc biệt rủi ro với thanh toán, phát hành sự kiện, ghi dữ liệu và các thao tác không idempotent.

### 8.1. Phụ thuộc giữa các aspect

Hai aspect cần phối hợp không đồng nghĩa nên gộp chúng thành một aspect. Có thể giữ mỗi concern riêng nhưng mô tả một contract: ví dụ “ngữ cảnh truy vết được tạo trước khi audit ghi sự kiện” hoặc “kiểm tra quyền phải diễn ra trước khi cập nhật dữ liệu”.

Khi các aspect có thứ tự bắt buộc, nên có **một nơi quản lý precedence** và test bảo vệ thứ tự ấy. Phụ thuộc vòng tròn kiểu “A phải trước B, B phải trước C, C phải trước A” cho thấy thiết kế không thể sắp thành một chuỗi nhất quán.

### 8.2. Không nuốt lỗi ngoài ý muốn

Một aspect quan sát thông thường nên truyền tiếp ngoại lệ sau khi ghi nhận. Nếu một aspect thay đổi hoặc nuốt lỗi, người gọi có thể nhận kết quả “thành công” dù thao tác đích thất bại. Chỉ thực hiện thay đổi như vậy nếu đó là chính sách công khai, có phạm vi rõ và được kiểm thử.

## 9. Trạng thái và vòng đời của aspect

Aspect có thể **không có trạng thái riêng cho từng lần gọi**, hoặc có state dùng cho một target, một ngữ cảnh hoặc một đối tượng aspect. Mô hình vòng đời cụ thể thay đổi theo kỹ thuật AOP. Không nên giả định mỗi lời gọi có một aspect instance mới.

### 9.1. Stateless thường dễ kiểm soát

Aspect ghi log chỉ dùng dữ liệu từ lời gọi và một logger an toàn để chia sẻ thường đơn giản hơn aspect giữ “tên người dùng hiện tại” trong trường instance. Nếu một instance dùng chung cho nhiều luồng, trường mutable có thể bị ghi đè chéo.

Ví dụ thiết kế nguy hiểm:

```java
// Pseudocode minh họa anti-pattern: trường này có thể bị nhiều luồng ghi đè.
class UnsafeAuditState {
    String currentRequestId;
}
```

Nếu cùng một instance được chia sẻ, request A ghi ID rồi request B ghi ID trước khi A hoàn tất thì bản ghi của A có thể mang nhầm ID của B.

### 9.2. State theo invocation và context propagation

Dữ liệu tạm như thời điểm bắt đầu nên ưu tiên biến cục bộ của advice, vì mỗi lần gọi có một ngữ cảnh riêng. Khi cần ngữ cảnh xuyên qua nhiều lời gọi hoặc chạy bất đồng bộ, phải thiết kế cách truyền ngữ cảnh tường minh hoặc sử dụng cơ chế ngữ cảnh đã được kiểm chứng. Không mặc định dữ liệu gắn với một thread sẽ tự đi theo công việc khi thay đổi thread, executor hay callback.

### 9.3. Reentrancy và recursion

Một aspect ghi log có thể gọi vào thành phần logging; nếu thành phần ấy cũng khớp pointcut của chính aspect, hệ thống có nguy cơ tự gọi lặp vô hạn. Cách phòng ngừa gồm thu hẹp pointcut, loại trừ hạ tầng của advice, hoặc đặt guard có phạm vi chính xác. Guard toàn cục dùng chung dễ làm bỏ lọt các lời gọi độc lập khi chạy đồng thời.

## 10. Cross-cutting tĩnh và động

Aspect giúp mô-đun hóa các tác động **động** như chạy advice quanh phương thức. Một số mô hình AOP còn hỗ trợ kiểu tác động **tĩnh**, ví dụ khai báo bổ sung thành viên, interface hoặc quan hệ cấu trúc cho một loại (*inter-type declaration/introduction*).

Đây là năng lực nâng cao và **không phổ quát**. Công cụ chỉ dùng proxy ở ranh giới interface thường không thể tùy ý thêm trường hay phương thức vào một class đã biên dịch. Vì vậy khi đánh giá một mô-đun aspect, cần phân biệt:

- **Behavioral cross-cutting:** thêm logic vào những thời điểm thực thi được chọn.
- **Structural cross-cutting:** bổ sung hoặc thay đổi cấu trúc loại chương trình, khi mô hình và cơ chế hỗ trợ.

Cả hai đều hướng tới đóng gói một concern, nhưng phạm vi tác động và nguy cơ tương thích khác nhau. Các thay đổi cấu trúc đòi hỏi đặc biệt chú ý đến API công khai, biên dịch, reflection và người dùng type đó.

## 11. Weaving ảnh hưởng đến khả năng đóng gói như thế nào?

Mô-đun aspect mô tả ý định; **cơ chế weaving/interception** quyết định ý định ấy tác động tới chương trình thực tế ở đâu.

| Cơ chế | Đặc trưng chính | Điều cần kiểm chứng |
| --- | --- | --- |
| **Compile-time weaving** | Advice được kết hợp trong giai đoạn biên dịch | Artifact đã được weave theo đúng cấu hình chưa? |
| **Post-compile weaving** | Bytecode đã biên dịch được biến đổi ở bước riêng | Có tác động đến build, debug và tương thích bytecode không? |
| **Load-time weaving** | Kết hợp lúc class được nạp | Class loader và thứ tự nạp có bảo đảm cơ chế áp dụng không? |
| **Runtime proxy** | Lời gọi được chuyển qua đối tượng trung gian | Có đường gọi trực tiếp hoặc nội bộ bỏ qua proxy không? |

Định nghĩa `AuditAspect` có thể trông rất sạch, nhưng nếu một số đường gọi bỏ qua cơ chế AOP thì audit không thật sự được thực thi ở mọi nơi mong đợi. Mô-đun hóa trên giấy chỉ đạt giá trị khi **ranh giới tác động được kiểm chứng bằng hành vi thực tế**.

Một nguyên tắc an toàn: không chọn AOP như lớp kiểm soát an ninh duy nhất nếu vẫn tồn tại đường truy cập bỏ qua advice; các bất biến nghiệp vụ và ranh giới bảo mật cốt lõi phải được bảo vệ tại nơi bảo đảm bao phủ.

## 12. Định lượng lợi ích và cái giá

Lợi ích của một aspect thiết kế tốt:

- **Tập trung chính sách:** thay đổi format audit/metric hoặc quy tắc áp dụng ở ít vị trí hơn.
- **Tăng cohesion:** mã nghiệp vụ tập trung vào nghiệp vụ; aspect tập trung vào concern chung.
- **Tái sử dụng:** cùng một aspect có thể áp dụng cho một tập join point có quy tắc rõ ràng.
- **Đồng nhất:** bớt nguy cơ mỗi lớp tự xử lý ngoại lệ, thời gian hay ngữ cảnh khác nhau.
- **Kiểm thử ở mức mô-đun:** có thể kiểm riêng logic advice, pointcut và sự phối hợp với target.

Chi phí cần tính đúng:

- **Hành vi không hiển thị tại call site:** đọc một phương thức chưa chắc thấy đầy đủ kiểm soát diễn ra quanh nó.
- **Pointcut giòn:** đổi code có thể làm tập khớp thay đổi không mong muốn.
- **Precedence phức tạp:** nhiều aspect giao nhau làm tăng số tổ hợp và khả năng tương tác.
- **Overhead:** thêm lời gọi trung gian, reflection, kiểm tra pointcut, tạo metric/log hoặc bytecode được chèn.
- **Debug khó hơn:** stack trace và breakpoint có thể đi qua lớp code mà nghiệp vụ không gọi trực tiếp.

Hiệu năng phải đo trên đường chạy thật, đặc biệt những join point tần suất cao. Không khẳng định một tỷ lệ overhead cố định cho mọi công cụ; mức chi phí phụ thuộc cơ chế triển khai, pointcut, lời khuyên và khối lượng công việc.

## 13. Tách trách nhiệm theo ranh giới kiến trúc

Một cấu trúc khái niệm dễ hiểu:

```text
business/
  PublicationService       (contract nghiệp vụ)
  PublicationServiceImpl   (quy trình thực hiện)

crosscutting/
  TraceAspect              (điểm áp dụng + hành vi trace)
  PublicationAuditAspect   (điểm áp dụng + hành vi audit)

composition/
  AspectConfiguration      (kích hoạt, thứ tự và cơ chế áp dụng)
```

Đây là **sơ đồ tổ chức gợi ý**, không phải cấu trúc folder bắt buộc. Ý nghĩa của nó:

- Mã nghiệp vụ không phụ thuộc trực tiếp vào class aspect.
- Mỗi aspect có tên diễn đạt mục đích và một lý do thay đổi.
- Phần lắp ghép xác định aspect nào có hiệu lực, theo thứ tự nào.
- Các cấu hình quan trọng nằm ở nơi có thể tìm thấy và kiểm toán được.

Không nên biến module aspect thành một “hộp đen” chứa các quyết định miền nghiệp vụ quan trọng. Ví dụ, “chỉ được hoàn tiền khi trạng thái đơn cho phép” là bất biến nghiệp vụ; nó cần hiện diện ở mô hình nghiệp vụ và được bảo vệ trực tiếp. Aspect có thể bổ sung log/audit quanh thao tác hoàn tiền, nhưng không nên là nơi duy nhất ẩn quy tắc trạng thái cốt lõi.

## 14. Kiểm thử aspect như một mô-đun độc lập và khi kết hợp

Kiểm thử AOP cần chứng minh **cả chính sách chọn điểm** và **hành vi advice**.

| Trường hợp | Xác nhận cần có |
| --- | --- |
| Join point khớp pointcut | Advice chạy đúng số lần và đúng vị trí tương đối. |
| Join point không khớp | Advice không chạy, nghiệp vụ vẫn hoạt động như dự kiến. |
| Target trả về bình thường | Giá trị trả về và nhánh sau thành công chính xác. |
| Target ném ngoại lệ | Nhánh lỗi/finally chính xác; ngoại lệ được bảo toàn nếu contract yêu cầu. |
| Hai aspect cùng khớp | Thứ tự vào/ra đúng precedence đã định. |
| Lời gọi trực tiếp và nội bộ | Kiểm chứng đúng giới hạn của cơ chế interception. |
| Tác vụ đồng thời | Không rò hoặc ghi đè context/state giữa các invocation. |
| Pointcut sau refactor | Không xuất hiện khớp thiếu/khớp thừa. |

Đối với ví dụ Java ở mục 5, có thể bắt đầu bằng bốn khẳng định:

1. `service.publish("P-7")` ghi trace và audit, trả `published:P-7`.
2. `service.status()` chỉ ghi trace, trả `READY`.
3. `service.publish("bad")` ghi nhánh thất bại của cả hai aspect, và người gọi nhận `IllegalArgumentException`.
4. `target.publish("P-7")` không sinh log của hai aspect vì không đi qua proxy.

Kiểm thử thứ tư có giá trị lớn: nó ngăn nhầm lẫn “aspect đã được định nghĩa” với “mọi đường gọi đến target đều được bao phủ”.

Ngoài unit test cho từng aspect, nên có **integration test** cho cấu hình weaving thực tế và **regression test** trên tập join point đặc trưng. Một unit test của `matches` không đủ để chứng minh class thực tế đã được biến đổi hoặc proxy đang được sử dụng bởi caller.

## 15. Các sai lầm thiết kế thường gặp

### “Aspect chỉ là nơi gom các hàm tiện ích”

Nếu concern không có quy tắc áp dụng tại join point và mọi nơi vẫn phải gọi utility một cách tường minh, đó có thể chỉ là utility module. Điều này không xấu; cần gọi đúng khái niệm để thiết kế cơ chế phù hợp.

### “Một aspect xử lý toàn bộ vấn đề kỹ thuật”

Gom audit, cache, authorization, exception mapping, retry và metric vào một aspect tạo coupling mạnh và làm khó kiểm thử precedence. Hãy xác định cohesion theo concern, tách khi trách nhiệm thay đổi độc lập.

### “Có pointcut thì bao phủ hoàn toàn”

Phạm vi thực tế còn phụ thuộc khả năng join point và đường gọi qua cơ chế AOP. Với proxy, gọi trực tiếp target hoặc gọi nội bộ có thể bỏ qua advice; với weaving bytecode, phải xác minh bước build/load thực sự áp dụng.

### “Advice chỉ bổ sung log nên không ảnh hưởng nghiệp vụ”

Log đồng bộ có thể tạo độ trễ; lỗi ghi log có thể làm lời gọi thất bại nếu không được xử lý; in toàn bộ đối số có thể lộ dữ liệu nhạy cảm. Ngay cả aspect quan sát cũng cần hợp đồng thất bại và chính sách dữ liệu.

### “Around advice có thể retry bất kỳ thao tác nào”

Gọi `proceed` nhiều lần có thể tạo side effect trùng lặp. Chỉ retry khi có chính sách rõ về idempotency, timeout, thứ tự và điều kiện ngoại lệ.

### “Aspect luôn không có state”

Một số mô hình có trạng thái theo aspect instance, target hoặc ngữ cảnh. Khi chia sẻ state mutable giữa nhiều luồng, phải kiểm soát race condition, vòng đời, leak và reentrancy.

### “Càng nhiều aspect càng sạch”

Chia quá nhỏ có thể làm mất khả năng thấy được chuỗi hành vi cuối cùng, tăng precedence và khó debug. Mục tiêu là mô-đun có trách nhiệm rõ, không phải tối đa số lượng aspect.

## 16. Bài tập củng cố

Từ `AspectModuleDemo.java`:

1. Thêm một phương thức `health()` vào interface và implementation, chạy qua proxy và giải thích tại sao `TraceAspect` khớp còn `PublicationAuditAspect` không khớp.
2. Đổi thứ tự hai phần tử trong `List.of(...)`, chạy lại `publish("P-7")` và quan sát thứ tự bản ghi ngoài/trong thay đổi.
3. Thêm một phương thức overload `publish(int version)` rồi phân tích vì sao pointcut hiện tại có thể chọn nhầm. Sửa `matches` để kiểm tra kiểu tham số cần thiết.
4. Thử gọi `target.publish("P-7")` trực tiếp sau lời gọi qua `service`. Vì sao lần gọi trực tiếp không có bản ghi trace/audit?
5. Chuyển phần logic cố ý không gọi `next.call()` vào một aspect riêng. Mô tả những nguy cơ nếu dùng aspect này cho phương thức có side effect.
6. Phác thảo hai aspect độc lập `ExecutionTiming` và `PublicationAudit`. Chỉ ra điều kiện áp dụng, dữ liệu được ghi, cách xử lý lỗi và thứ tự nên có trong một tình huống cụ thể.

**Tự kiểm tra nhanh:** Aspect khác advice như thế nào? Vì sao pointcut là một phần quan trọng của ranh giới mô-đun? Một aspect có thể chứa nhiều advice không? Thứ tự hai around advice ảnh hưởng điều gì? Với proxy, tại sao lời gọi trực tiếp target không bị chặn? Khi nào một wrapper đơn giản phù hợp hơn aspect?

## Kết luận

**Aspect là đơn vị đóng gói một mối quan tâm cắt ngang**, có mục tiêu nhất quán, tiêu chí chọn điểm áp dụng và hành vi tương ứng. Giá trị thiết kế nằm ở việc làm rõ *ai sở hữu chính sách dùng chung*, *chính sách có hiệu lực ở đâu* và *nó thay đổi điều gì trong luồng chạy*. Để aspect thực sự hữu ích, cần giữ cohesion, giới hạn pointcut chính xác, công bố tác động lên kết quả/ngoại lệ, xác định precedence và kiểm thử cơ chế weaving trên những đường gọi thật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-component-model-002">Join point là điểm có thể can thiệp trong mô hình thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Join point là điểm có thể can thiệp trong mô hình thực thi AOP

## 1. Join point là gì?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, **join point** là **một điểm được xác định trong mô hình thực thi của chương trình mà cơ chế AOP cho phép gắn thêm hành vi**. Hành vi gắn thêm này thường được gọi là **advice**; tập hợp các điểm cần tác động có thể được diễn đạt bằng **pointcut**.

Định nghĩa cần hiểu theo hai lớp:

1. **Điểm có ý nghĩa trong luồng thực thi:** chẳng hạn trước khi một phương thức bắt đầu, tại một lời gọi phương thức, khi trường dữ liệu được đọc, hoặc khi một ngoại lệ được xử lý.
2. **Điểm thuộc mô hình join point được công cụ hỗ trợ:** một sự kiện xảy ra khi chạy chương trình chưa chắc là join point nếu cơ chế AOP đang sử dụng không thể nhận biết hoặc can thiệp tại đó.

Ví dụ, ứng dụng có các phương thức `PaymentService.pay()` và `RefundService.refund()`. Ta muốn đo thời gian mỗi lần thực hiện nghiệp vụ:

```text
Người dùng gọi pay()
        |
        v
[điểm thực thi pay() bắt đầu]  <-- join point có thể gắn advice bắt đầu đo
        |
   xử lý thanh toán
        |
        v
[điểm thực thi kết thúc]       <-- advice có thể ghi kết quả/thời gian
```

Sơ đồ mang tính **khái niệm**. Trong một mô hình AOP có join point kiểu *method execution*, việc vào thực thi phương thức `pay()` là một join point; advice `around` có thể bao cả lần thực thi đó. Không phải cứ có dòng chương trình hoặc mốc thời gian bất kỳ thì tự nhiên có thể đặt advice vào.

**Phân biệt quan trọng:** join point cho biết **điểm nào có thể can thiệp**, pointcut trả lời **chọn điểm nào**, còn advice mô tả **làm gì tại điểm được chọn**.

## 2. Join point tồn tại trong mô hình thực thi, không chỉ trong mã nguồn

Một chương trình có thể nhìn theo **cấu trúc tĩnh** (lớp, hàm, dòng lệnh) và **diễn tiến động** (những lần gọi, nhánh, ngoại lệ, luồng thực thi). Join point liên quan đến cả hai:

- **Vị trí hoặc loại điểm thực thi xác định được:** ví dụ vị trí gọi `gateway.charge()` ở trong phương thức `checkout()`, hoặc việc bắt đầu thực thi `charge()`.
- **Một lần xuất hiện khi chương trình chạy:** mỗi lần đi qua vị trí đó có thể mang ngữ cảnh khác nhau như tham số, đối tượng đích, kết quả hoặc ngoại lệ.
- **Khả năng can thiệp của công cụ:** có công cụ biến đổi mã đã biên dịch; có công cụ chặn lời gọi qua một đối tượng trung gian. Chúng không nhất thiết quan sát được cùng các điểm.

Một số hệ thống AOP dùng thuật ngữ **join-point shadow** cho vị trí tĩnh trong mã tương ứng với các join point có thể phát sinh lúc chạy. Ví dụ một lời gọi được viết một lần trong vòng lặp có thể tạo ra nhiều lần xuất hiện join point:

```java
for (int i = 0; i < 3; i++) {
    payment.pay(i);
}
```

Nếu cơ chế chọn được điểm gọi `pay(i)`, **một vị trí lời gọi** có thể phát sinh **ba lần thực thi tại điểm đó**, mỗi lần với giá trị `i` khác nhau. Không nên đồng nhất "một dòng mã" với "một lần chạy join point".

Khi một pointcut chứa điều kiện theo tham số hoặc trạng thái khi chạy, việc một vị trí tĩnh có khả năng khớp chưa bảo đảm **mọi lần xuất hiện động** đều thỏa điều kiện. Chẳng hạn chỉ can thiệp khi số tiền thanh toán vượt một ngưỡng; các lần gọi cùng vị trí nhưng số tiền khác nhau có thể cho kết quả khớp khác nhau.

## 3. Join point, pointcut, advice và aspect phối hợp ra sao?

| Khái niệm | Vai trò | Ví dụ |
| --- | --- | --- |
| **Join point** | Điểm/sự kiện thực thi thuộc mô hình có thể can thiệp | Một lần bắt đầu thực thi `pay()` |
| **Pointcut** | Điều kiện/quy tắc lựa chọn tập join point | Chọn các lần thực thi thao tác thanh toán |
| **Advice** | Hành vi gắn vào join point được chọn | Ghi log, đo thời gian, kiểm tra chính sách |
| **Aspect** | Đơn vị mô-đun hóa một mối quan tâm xuyên suốt, thường chứa pointcut và advice | `AuditAspect` |
| **Weaving** | Cơ chế kết hợp advice với điểm được chọn trong chương trình | Biến đổi mã hoặc chặn lời gọi tại thời điểm chạy |

Ví dụ **mã giả AOP tổng quát** (không phải cú pháp Java có thể biên dịch trực tiếp):

```text
aspect AuditPayment:
    pointcut paidOperations =
        executionsOf(PaymentService.pay)
        OR executionsOf(RefundService.refund)

    before paidOperations(context):
        audit("start", context.operationName)

    afterFinally paidOperations(context):
        audit("end", context.operationName)
```

Trong ví dụ:

1. Hệ thống thực thi các thao tác thanh toán/hoàn tiền sẽ tạo ra các điểm thực thi tương ứng, **nếu loại điểm này được công cụ hỗ trợ**.
2. `paidOperations` là pointcut chọn các join point thích hợp.
3. Hai advice ghi thông tin trước và khi kết thúc thao tác.
4. Aspect gom mô tả chính sách kiểm toán; weaving làm cho advice thực sự chạy ở các điểm được chọn.

Cú pháp `executionsOf` và `afterFinally` trong ví dụ chỉ diễn đạt ý tưởng; mỗi ngôn ngữ/công cụ AOP có quy ước và khả năng khác nhau. **Chỉ khai báo pointcut không tạo được join point mới ngoài mô hình mà công cụ hỗ trợ.**

### 3.1. Join point khác lời gọi helper như thế nào?

Ở mã thông thường, lập trình viên chèn lời gọi tường minh:

```text
logStart()
pay()
logEnd()
```

Trong AOP, người viết nghiệp vụ có thể chỉ gọi `pay()`; pointcut và cơ chế weaving khiến việc ghi log được áp dụng ở điểm thực thi được chọn. Lợi ích nằm ở khả năng **mô-đun hóa chính sách trải rộng ở nhiều nơi**, nhưng luồng thực tế phải được tài liệu hóa và kiểm thử vì advice có thể không xuất hiện trong thân phương thức nghiệp vụ.

### 3.2. Join point khác event nghiệp vụ như thế nào?

Một **event nghiệp vụ** như `OrderPaid` là thông tin do ứng dụng chủ động phát ra để mô tả sự kiện miền. Một **join point** là vị trí/sự kiện được xác định bởi mô hình thực thi của AOP. Chúng có thể liên quan nhưng không đồng nghĩa: việc trả về từ `pay()` không tự chứng minh thanh toán đã được hệ thống bên ngoài ghi nhận thành công, và một event có thể được phát ra sau nhiều bước bất đồng bộ.

## 4. Những loại join point thường gặp

**Không có một danh sách join point bắt buộc chung cho mọi nền tảng AOP.** Một mô hình có thể bao quát nhiều loại; một cơ chế đơn giản có thể chỉ chặn được lời gọi phương thức đi qua lớp trung gian.

| Loại điểm thực thi | Ý nghĩa | Tình huống sử dụng |
| --- | --- | --- |
| **Method execution** | Khi thân một phương thức được thực thi | Đo thời gian xử lý một phương thức |
| **Method call** | Tại vị trí mã gọi một phương thức | Quan sát một lời gọi từ phía bên gọi |
| **Constructor call/execution** | Khi gọi hoặc thực thi logic khởi tạo đối tượng | Theo dõi hoạt động tạo đối tượng |
| **Field get/set** | Khi đọc hoặc gán giá trị trường | Quan sát truy cập trạng thái |
| **Exception handler** | Khi luồng điều khiển đi vào phần xử lý ngoại lệ | Ghi nhận đường đi xử lý lỗi |
| **Initialization/static initialization** | Các điểm trong vòng đời khởi tạo (nếu được hỗ trợ) | Phân tích giai đoạn khởi tạo |
| **Sự kiện khác do công cụ định nghĩa** | Chỉ tồn tại nếu mô hình join point mô tả được | Những điểm can thiệp dành riêng cho nền tảng |

Một vài điểm cần lưu ý:

- **Đọc trường dữ liệu** và **gọi getter** có thể là hai dạng hoạt động khác nhau; không được coi chúng luôn tương đương.
- **Gọi constructor** khác với **thực thi phần thân constructor**, và có mô hình còn phân biệt các giai đoạn khởi tạo.
- **Ngoại lệ được ném** và **đi vào handler bắt ngoại lệ** là hai sự kiện khác nhau. Một công cụ chỉ hỗ trợ join point của handler không có nghĩa là nó can thiệp được tại mọi lệnh `throw`.
- Một công cụ hỗ trợ method execution không nhất thiết hỗ trợ field access, thao tác native, đoạn code được tối ưu hóa đặc biệt hoặc mọi lời gọi ở môi trường chạy.

Chính **mô hình join point của giải pháp AOP** xác định tập các điểm có thể áp dụng advice. Không nên suy từ tên các loại điểm phổ biến để kết luận ứng dụng cụ thể đã hỗ trợ hết.

## 5. Method call và method execution: hai góc nhìn khác nhau

Đây là phân biệt quan trọng khi học join point ở mức nâng cao.

Giả sử có mã Java:

```java
class PaymentGateway {
    void charge() {
        System.out.println("charging");
    }
}

class CheckoutService {
    void checkout(PaymentGateway gateway) {
        gateway.charge();
    }
}
```

Đây là **đoạn mã minh họa** (không phải chương trình độc lập có `main`).

- **Method-call join point** nằm ở **phía gọi**, tương ứng vị trí biểu thức `gateway.charge()` trong `checkout()`. Ngữ cảnh có thể liên quan đến vị trí, kiểu và đối tượng bên gọi.
- **Method-execution join point** nằm ở **phía phương thức được gọi**, tương ứng việc chạy thân `charge()`. Các lời gọi khác nhau tới cùng phương thức có thể đi vào cùng loại execution join point.

Sơ đồ:

```text
CheckoutService.checkout()              PaymentGateway.charge()
    |                                           |
    |-- gateway.charge() ---------------------->|
    ^                                           ^
    |                                           |
  call join point                         execution join point
  (phía gọi)                              (phía được gọi)
```

Sự phân biệt này có hậu quả thực tế:

1. Nhiều vị trí gọi có thể cùng hướng tới một phương thức thực thi.
2. Một vị trí gọi có thể dẫn đến những triển khai khác nhau qua đa hình hoặc dispatch động.
3. Mã thư viện, lời gọi phản xạ, lời gọi được tạo bởi công cụ hoặc những đường gọi đặc biệt có thể không nằm trong phạm vi weaving đã cấu hình.
4. Một advice gắn ở phía gọi có thể thu thập ngữ cảnh khác với advice gắn ở phía thực thi.

**Không nên đồng nhất** tên phương thức trong pointcut với khái niệm "mọi đường chạy qua phương thức này"; phải xác định pointcut đang chọn kiểu join point nào.

### 5.1. Tương tác với đa hình

Ví dụ biến có kiểu giao diện `PaymentGateway` nhưng lúc chạy trỏ tới một hiện thực cụ thể. Kiểu khai báo tại vị trí call, chữ ký phương thức được gọi, kiểu đối tượng thực tế và việc dispatch tới phần thân thực thi **có thể khác nhau**. Tùy ngôn ngữ và công cụ, các điều kiện pointcut dựa trên kiểu tĩnh hoặc kiểu thực tế có thể khớp khác nhau.

Khi kiểm chứng, cần phân biệt:

- Kiểu/chữ ký phương thức nhìn từ **vị trí gọi**.
- Kiểu/chữ ký của **phần thực thi thực tế**.
- Đối tượng **bên gọi** và đối tượng **đích** (nếu công cụ cung cấp).
- Giá trị **tham số thực tế** tại lần gọi cụ thể.

## 6. Ví dụ Java 17 chạy được: giới hạn điểm can thiệp qua proxy

Không có cú pháp AOP tích hợp sẵn trong ngôn ngữ Java thông thường. Để quan sát rõ tác động của **cơ chế can thiệp**, ta dùng `java.lang.reflect.Proxy` trong JDK để chặn **lời gọi đi qua một proxy giao diện**. Đây là **ví dụ kỹ thuật interception**, không phải một hệ thống AOP tổng quát và không chứng minh mọi loại join point đều được hỗ trợ.

Tạo tệp `JoinPointDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class JoinPointDemo {
    interface TaskService {
        void outer();
        void inner();
    }

    static final class TaskServiceImpl implements TaskService {
        @Override
        public void outer() {
            System.out.println("body:outer");
            inner();
        }

        @Override
        public void inner() {
            System.out.println("body:inner");
        }
    }

    public static void main(String[] args) {
        TaskService target = new TaskServiceImpl();
        TaskService proxy = (TaskService) Proxy.newProxyInstance(
                TaskService.class.getClassLoader(),
                new Class<?>[] { TaskService.class },
                (ignoredProxy, method, arguments) -> {
                    System.out.println("before:" + method.getName());
                    try {
                        return method.invoke(target, arguments);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    } finally {
                        System.out.println("after:" + method.getName());
                    }
                }
        );

        proxy.outer();
        proxy.inner();
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac JoinPointDemo.java
java JoinPointDemo
```

Kết quả mong đợi chính xác:

```text
before:outer
body:outer
body:inner
after:outer
before:inner
body:inner
after:inner
```

**Giải thích kỹ:**

- `proxy.outer()` đi qua proxy nên handler được gọi và in `before:outer`; sau đó nó chuyển tiếp vào `target.outer()`.
- Trong `target.outer()`, lời gọi `inner()` là lời gọi trên chính đối tượng đích; nó **không đi lại qua proxy**, nên không có `before:inner` hay `after:inner` cho lần gọi bên trong này.
- `proxy.inner()` ở cuối chương trình là một lời gọi **đi qua proxy**, nên handler ghi cả `before:inner` và `after:inner`.

Ví dụ giúp phân biệt **“phương thức đã được thực thi”** và **“lời gọi đã đi qua cơ chế can thiệp cụ thể”**. Ở mô hình can thiệp bằng proxy này, điểm có thể chặn là lời gọi đi qua proxy; việc thực thi trực tiếp trên đối tượng đích không tự động được chặn. Một giải pháp AOP có weaving vào mã chương trình có thể quan sát được những điểm thực thi khác. Đây là **giới hạn của cơ chế triển khai**, không phải quy luật rằng mọi AOP đều bỏ qua lời gọi nội bộ.

## 7. Điều gì xảy ra khi advice can thiệp vào join point?

Khi pointcut chọn một join point và weaving đã áp dụng advice, hành vi phụ trợ có thể chạy ở các thời điểm khác nhau so với thao tác gốc:

```text
Một lần thực thi thao tác
  |
  +--> advice trước (before)
  |
  +--> phần xử lý gốc
  |       |
  |       +--> trả về bình thường ----> after returning
  |       |
  |       +--> ném ngoại lệ ----------> after throwing
  |
  +--> advice kết thúc/finally (nếu được hỗ trợ)
```

**Around advice** có thể bao toàn bộ chuỗi, tùy ngữ nghĩa của công cụ. Mã giả:

```text
around selectedExecution(joinPointContext):
    startedAt = monotonicClock()
    try:
        result = proceed(joinPointContext)
        recordSuccess(joinPointContext, monotonicClock() - startedAt)
        return result
    catch error:
        recordFailure(joinPointContext, error)
        throw error
```

Đây là **mã giả**, không phải Java độc lập. `proceed` biểu thị việc tiếp tục thao tác được bao quanh. Việc dùng đồng hồ tăng đơn điệu giúp tính thời lượng; ném lại ngoại lệ giúp giữ tín hiệu lỗi, trong phạm vi ngữ nghĩa của cơ chế advice.

Các kiểu advice có ý nghĩa khác nhau:

- **Before:** xảy ra trước phần xử lý được chọn, nhưng không nhất thiết có quyền sửa kết quả.
- **After returning:** chỉ áp dụng khi hoàn tất bình thường.
- **After throwing:** xử lý đường kết thúc bằng ngoại lệ.
- **After/finally:** dành cho mọi đường kết thúc nếu công cụ cung cấp.
- **Around:** có thể quyết định việc tiếp tục, thứ tự và cách xử lý kết quả/ngoại lệ; cần đặc biệt cẩn trọng.

Không phải mọi hệ thống đều có tất cả những kiểu advice này; ranh giới và thứ tự áp dụng phải được xác định theo mô hình AOP thực tế.

### 7.1. Join point không quyết định chính sách lỗi

Join point chỉ định **chỗ có thể can thiệp**. Nó không tự quyết định:

- Có tiếp tục thao tác gốc hay không.
- Có biến đổi kết quả trả về hay không.
- Có bắt, thay thế hoặc ném lại ngoại lệ hay không.
- Có chạy lại thao tác gốc khi lỗi hay không.

Những quyết định ấy thuộc về advice và hợp đồng ứng dụng. Một around advice vô tình gọi `proceed` hai lần có thể khiến thao tác có tác dụng phụ được thực hiện hai lần; điều này nguy hiểm cho nghiệp vụ thanh toán, gửi thông báo hoặc cập nhật dữ liệu.

## 8. Ngữ cảnh sẵn có tại một join point

Một join point có thể mang theo **ngữ cảnh** cho advice. Mức độ thông tin phụ thuộc vào loại join point và công cụ:

| Thành phần ngữ cảnh | Dùng để làm gì? | Lưu ý |
| --- | --- | --- |
| Loại join point | Phân biệt call, execution, field access… | Tập loại điểm tùy công cụ |
| Chữ ký phương thức/thành phần | Định danh thao tác | Chữ ký tĩnh và đích thực tế có thể khác |
| Vị trí mã nguồn | Truy vết điểm xuất phát | Có thể không có trong mã biên dịch không kèm thông tin nguồn |
| Đối tượng phía gọi/đích | Theo dõi ngữ cảnh đối tượng | Có loại join point không có một hoặc cả hai |
| Tham số thực tế | Phân loại, đo đạc hoặc kiểm tra quy tắc | Cẩn thận dữ liệu nhạy cảm, dữ liệu thay đổi |
| Giá trị trả về | Quan sát kết quả thành công | Chỉ có sau khi hoàn thành bình thường nếu công cụ hỗ trợ |
| Ngoại lệ | Ghi nhận đường thất bại | Chỉ có tại những thời điểm/kiểu advice thích hợp |

Nhiều framework AOP định nghĩa một đối tượng ngữ cảnh cho join point, nhưng tên API và tập thuộc tính không thống nhất. Tránh thiết kế advice dựa trên dữ liệu mà kiểu join point hoặc cơ chế thực thi không bảo đảm cung cấp.

**Vấn đề riêng tư và bảo mật:** tự động ghi lại mọi tham số/kết quả có thể làm lộ mật khẩu, mã truy cập hoặc dữ liệu cá nhân. Nếu dùng join point để quan sát, nên chọn metadata cần thiết, che dữ liệu nhạy cảm và định nghĩa chính sách lưu trữ phù hợp.

## 9. Weaving quyết định điểm nào thật sự có hiệu lực

Về mặt ý tưởng, join point nằm trong mô hình thực thi; để advice chạy tại điểm đó, hệ thống cần một cơ chế **weaving/interception**.

Các hướng triển khai thường gặp:

1. **Compile-time weaving:** kết hợp hành vi vào mã trong quá trình biên dịch.
2. **Load-time weaving:** kết hợp hoặc biến đổi mã khi thành phần được nạp vào môi trường chạy.
3. **Runtime interception/weaving:** dùng cơ chế tại thời điểm chạy, có thể bao gồm proxy hoặc cách biến đổi/can thiệp khác.

Không phải ba cách này có cùng khả năng chọn join point. Ví dụ một proxy chỉ nhìn thấy cuộc gọi qua proxy của nó; cơ chế biến đổi bytecode có thể tác động đến điểm thực thi trong thân lớp mà proxy không thấy, nếu được thiết kế và cấu hình tương ứng.

**Đừng suy luận rằng chỉ cần viết pointcut chọn `execute()` thì mọi đường gọi đều bị can thiệp.** Cần xác minh:

- **Loại join point:** call hay execution, hoặc một loại khác?
- **Vùng mã được weaving:** chương trình của mình, mã thư viện, mã được nạp động?
- **Đường gọi:** bên ngoài, nội bộ, qua proxy, phản xạ hay native?
- **Khả năng công cụ:** có hỗ trợ đúng loại điểm và đối tượng đích không?
- **Thứ tự áp dụng:** nếu nhiều advice cùng khớp, ai chạy trước/sau?

Một lời gọi không bị advice chạm tới có thể là do **điểm đó nằm ngoài mô hình hoặc phạm vi can thiệp**, không nhất thiết do biểu thức pointcut viết sai.

## 10. Các tình huống nâng cao cần hiểu đúng

### 10.1. Một điểm tĩnh, nhiều lần chạy

Một phương thức có thể được gọi nhiều lần, đồng thời trên nhiều luồng và với các tham số khác nhau. Mỗi lần đi qua join point là một **lần xuất hiện động**. Advice đo thời gian không nên dùng chung một biến mốc thời gian có thể ghi đè giữa các lần gọi; nên giữ trạng thái theo lần thực thi tương ứng.

Với chương trình có đệ quy, cùng một vị trí method execution có thể xuất hiện nhiều lần lồng nhau. Advice quanh thao tác đệ quy cần bảo toàn quan hệ gọi lồng nhau và tránh giả định luôn chỉ có một lần bắt đầu/kết thúc đang hoạt động.

### 10.2. Một join point khớp nhiều pointcut

Một lần thực thi `pay()` có thể đồng thời được chọn bởi pointcut kiểm toán, đo thời gian và kiểm soát quyền. Thứ tự quanh thao tác có thể làm thay đổi ngữ nghĩa:

```text
Audit(Authorization(Timing(pay)))
```

Trong thứ tự minh họa này, audit có thể nhìn thấy một lần gọi bị từ chối quyền, còn timer bên trong chỉ đo được các lần vượt qua kiểm tra. Nếu đổi thứ tự thành `Authorization(Audit(Timing(pay)))`, việc bị từ chối sớm có thể không được audit bởi advice bên trong. Cách công cụ chọn thứ tự mặc định có thể khác nhau: **phải xác định thứ tự rõ ràng, rồi kiểm thử các đường thành công và thất bại**.

### 10.3. Kiểm soát điều kiện động

Một pointcut có thể chọn join point dựa trên cả điều kiện cấu trúc (loại phương thức, kiểu đối tượng) và điều kiện lúc chạy (giá trị tham số, trạng thái). Trong trường hợp đó:

- Vị trí tĩnh thuộc **tập điểm có khả năng khớp**.
- Một lần xuất hiện cụ thể chỉ **thật sự được advice xử lý** khi điều kiện động thỏa.
- Điều kiện động có thể tăng chi phí kiểm tra; điều kiện dựa vào trạng thái dễ đổi có thể khiến việc quan sát khó dự đoán.

Chọn điểm bằng quy tắc quá rộng gây tác động không mong muốn; quy tắc quá hẹp bỏ sót điểm cần bảo vệ.

### 10.4. Bất đồng bộ và ranh giới thời gian

Giả sử phương thức `startPaymentAsync()` trả về ngay một đối tượng công việc hoặc tương lai (future), rồi công việc mới hoàn tất sau đó. Advice quanh **lần thực thi phương thức trả về** có thể đo được thời gian khởi tạo tác vụ, **không nhất thiết là thời gian thanh toán hoàn tất**.

Muốn theo dõi trọn công việc bất đồng bộ, thiết kế phải chọn ranh giới hoàn tất thật sự (ví dụ callback/completion) và quản lý ngữ cảnh giữa các luồng/tác vụ. Không được coi mọi kết quả trả về là kết thúc nghiệp vụ.

### 10.5. Tác động của tối ưu hóa và mã không thuộc phạm vi quản lý

JIT, mã native, thư viện nạp riêng, phản xạ hoặc trình biên dịch có thể thay đổi cách thực thi. Khả năng nhìn thấy join point và bảo toàn advice phụ thuộc vào cam kết của công cụ AOP cũng như điểm weaving đã được cài đặt. Việc phương thức được inline hay tối ưu hóa không nên bị suy đoán là chắc chắn làm mất hoặc giữ join point trong mọi hệ thống.

### 10.6. Join point là ranh giới kỹ thuật, không tự tạo bảo đảm an ninh

Nếu dùng advice tại join point để kiểm tra truy cập, hãy chứng minh **mọi đường vào cần bảo vệ** đều đi qua điểm can thiệp. Nếu còn đường gọi trực tiếp bỏ qua proxy hoặc một lời gọi không được weaving, chính sách có thể bị bỏ qua. Các bất biến an toàn quan trọng phải được thiết kế với ranh giới tin cậy rõ ràng và kiểm chứng đầy đủ.

## 11. Cách thiết kế, kiểm chứng một tập join point

Khi muốn áp dụng chính sách AOP vào nhiều thao tác, có thể làm lần lượt:

1. **Xác định concern:** ví dụ đo thời gian xử lý thao tác thanh toán; không lẫn vào thuật toán tính tiền.
2. **Xác định ranh giới mong muốn:** muốn đo *lần gọi từ phía bên ngoài* hay *lần thực thi phương thức*, trước khi khởi tạo hay sau khi hoàn tất?
3. **Chọn loại join point mà công cụ hỗ trợ:** method call, execution, field access hay loại khác; đọc đúng khả năng triển khai.
4. **Diễn đạt pointcut tối thiểu:** chọn những điểm có chủ ý; tránh wildcard quá rộng hoặc phụ thuộc vào tên tình cờ.
5. **Xác định advice và thứ tự:** kết quả, ngoại lệ, chạy một lần hay nhiều lần, tình huống bị ngăn thực thi.
6. **Kiểm thử hành vi:** một đường phải được can thiệp; một đường không được can thiệp; lời gọi nội bộ/đa hình nếu liên quan; lỗi, đồng thời và bất đồng bộ.
7. **Kiểm tra chi phí và khả năng quan sát:** tác động hiệu năng, dữ liệu ghi nhận, độ dễ truy vết và chính sách nhạy cảm dữ liệu.

Ví dụ bảng ca kiểm thử cho một hệ thống muốn đo mọi lần thực thi của một nhóm thao tác:

| Tình huống | Kết quả mong muốn | Điều cần xác minh |
| --- | --- | --- |
| Gọi `pay()` ở đường sử dụng chuẩn | Đo đúng một lần | Điểm khớp pointcut và advice chạy |
| Gọi `pay()` qua một đường khác | Theo chính sách đã thiết kế | Đường đó nằm trong phạm vi weaving hay không |
| Lời gọi nội bộ trong cùng đối tượng | Theo bảo đảm thực tế của công cụ | Có bị bỏ qua do proxy không |
| Phương thức ném lỗi | Có đo và ghi trạng thái lỗi nếu yêu cầu | Advice không nuốt ngoại lệ |
| Nhiều luồng chạy đồng thời | Dữ liệu từng lần không chồng lẫn | Mốc thời gian/ngữ cảnh theo từng invocation |
| Gọi tác vụ bất đồng bộ | Đo đúng ranh giới đã công bố | Phương thức trả về hay tác vụ hoàn thành |
| Phương thức ngoài danh sách | Không bị ảnh hưởng | Pointcut không quá rộng |

Kiểm thử cả trường hợp **không khớp** là thiết yếu, vì pointcut quá rộng có thể tạo lỗi dù những ca cần can thiệp đều hoạt động.

## 12. Lỗi hiểu sai thường gặp

**Nhầm “mọi chỗ có thể đặt lệnh” với join point.** Join point chỉ bao gồm các điểm mà mô hình AOP cụ thể xác định và hỗ trợ. Không phải từng dòng lệnh, từng phép cộng hoặc mọi sự kiện chạy đều có join point độc lập.

**Nhầm join point với pointcut.** Join point là điểm/sự kiện thực thi; pointcut là quy tắc chọn nhiều điểm. Một pointcut có thể khớp nhiều điểm và một điểm có thể khớp nhiều pointcut.

**Nhầm method call với method execution.** Method call nhìn từ phía vị trí gọi; method execution nhìn từ phía thân được thực thi. Điều này ảnh hưởng đến nơi advice chạy và ngữ cảnh quan sát được.

**Nhầm lời gọi đi qua proxy với toàn bộ lần thực thi của target.** Proxy chỉ thấy những lời gọi đi qua nó. Cơ chế weaving khác có thể có tập điểm rộng hơn hoặc những giới hạn khác.

**Cho rằng advice không thể ảnh hưởng nghiệp vụ.** Around advice có thể bỏ qua, gọi lại hoặc làm biến đổi kết quả nếu cơ chế cho phép. Phải kiểm soát rõ hợp đồng của thao tác gốc.

**Cho rằng thấy method return nghĩa là việc bất đồng bộ đã hoàn tất.** Một phương thức trả về future hoặc handle có thể kết thúc trước tác vụ thật sự.

**Cho rằng quan sát được join point là bảo đảm mọi đường gọi đều bị bảo vệ.** Một chính sách quan trọng cần kiểm tra phạm vi weaving và các đường truy cập khác, không chỉ kiểm tra cú pháp pointcut.

## 13. Câu hỏi tự kiểm tra

**1. Một vị trí gọi `pay()` nằm trong vòng lặp chạy năm lần. Có bao nhiêu join point?**

Có thể có **một vị trí tĩnh** tương ứng nhiều lần xuất hiện khi chạy — thường là năm lần, nếu mỗi lượt đều đi qua vị trí đó. Việc công cụ có thực sự chọn/can thiệp hay không còn tùy pointcut và cơ chế weaving.

**2. Vì sao advice cho `inner()` không chạy trong lời gọi `outer() -> inner()` ở ví dụ Java?**

Vì lời gọi nội bộ được thực hiện trực tiếp trên `target`, không đi qua `proxy`. Lệnh `proxy.inner()` độc lập sau đó đi qua proxy nên bị chặn. Đây là đặc điểm của ví dụ interception bằng proxy.

**3. Khi nào cần phân biệt call và execution join point?**

Khi quan tâm đến phía gọi hoặc phía được gọi, nhiều vị trí gọi tới cùng phương thức, đa hình, phạm vi mã được weaving, hoặc metadata chỉ có ở một phía.

**4. Một join point được chọn bởi hai aspect có đảm bảo hai advice chạy theo thứ tự viết trong mã không?**

Không thể kết luận chung. Cần xác minh quy tắc ưu tiên/thứ tự của cơ chế AOP đang dùng và kiểm thử sự kết hợp của advice, đặc biệt khi có quyền từ chối thao tác hoặc thay đổi ngoại lệ.

**5. Có nên dùng join point để đo thời gian hoàn thành của một tác vụ bất đồng bộ chỉ bằng advice quanh phương thức khởi tạo không?**

Không đủ trong nhiều trường hợp. Advice đó có thể chỉ đo thời gian phương thức tạo/trả tác vụ; cần xác định điểm hoàn tất công việc và ràng buộc ngữ cảnh đúng.

## 14. Ghi nhớ

**Join point là một điểm có thể can thiệp trong mô hình thực thi mà giải pháp AOP hỗ trợ.** Tại những điểm được **pointcut** chọn, **advice** mới có thể được kết hợp thông qua **weaving/interception**. Muốn hiểu đúng và dùng AOP an toàn phải phân biệt *vị trí tĩnh* với *lần xuất hiện động*, *call* với *execution*, và *điểm mong muốn can thiệp* với *điểm công cụ thật sự chặn được*. Đây là nền tảng để thiết kế, giải thích và kiểm chứng các aspect mà không làm mơ hồ luồng chạy của chương trình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-component-model-003">Pointcut là điều kiện lựa chọn tập điểm can thiệp</a>

<details>
<summary>Xem chi tiết</summary>

# Pointcut là điều kiện lựa chọn tập điểm can thiệp trong AOP

> **Mục tiêu:** Hiểu đúng pointcut là **điều kiện lựa chọn các join point** trong mô hình lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP); phân biệt pointcut với join point, advice và aspect; biết xây dựng quy tắc lựa chọn theo cấu trúc hoặc theo ngữ cảnh thực thi; giải thích phạm vi bao phủ, phép kết hợp điều kiện và các lỗi thiết kế thường gặp.

## 1. Câu hỏi mà pointcut trả lời

Trong một chương trình có hàng trăm phương thức và nhiều luồng xử lý, một hành vi xuyên suốt như ghi vết, đo thời gian hay kiểm toán **không nhất thiết được áp dụng ở mọi chỗ**.

Giả sử muốn ghi kiểm toán **chỉ khi** thao tác gửi đơn hàng thuộc nhóm cần theo dõi. Câu hỏi đầu tiên không phải “ghi dòng log nào?”, mà là:

> **Ở những điểm thực thi nào, và khi thỏa những điều kiện nào, hành vi bổ sung được phép tham gia?**

**Pointcut** là thành phần mô tả điều kiện chọn các điểm đó. Hãy hình dung chương trình tạo ra một tập các điểm có khả năng can thiệp, gọi là tập join point ứng viên. Pointcut cung cấp **quy tắc lọc** tập ứng viên, xác định điểm nào được chọn và điểm nào bị loại.

Có thể mô tả bằng ký hiệu khái niệm:

```text
J = tập các join point mà mô hình AOP có thể nhận diện
P = điều kiện pointcut

Matched(P) = { j thuộc J | P(j) đúng }
```

Nếu có một lần thực thi phương thức `submitOrder("VIP")` và điều kiện chọn “lời thực thi submitOrder có đối số VIP” được thỏa mãn, lần thực thi đó thuộc tập điểm được chọn. Lần thực thi `submitOrder("NORMAL")` có cùng tên phương thức nhưng không thỏa điều kiện ở thời điểm chạy nên bị loại.

**Pointcut chọn vị trí/ngữ cảnh; nó không tự định nghĩa hành động cần chạy.** Hành động ở các điểm được chọn thuộc trách nhiệm của *advice*.

## 2. Đặt pointcut vào mô hình thành phần của AOP

| Thành phần | Câu hỏi chính | Ví dụ |
|---|---|---|
| **Concern** | Chương trình đang có trách nhiệm gì? | Xử lý đơn hàng, ghi kiểm toán |
| **Cross-cutting concern** | Trách nhiệm nào cần được áp dụng ở nhiều điểm? | Ghi kiểm toán theo chính sách chung |
| **Join point** | Mô hình AOP có thể can thiệp vào điểm/hoạt động nào? | Một lần thực thi phương thức gửi đơn |
| **Pointcut** | **Điểm nào trong các join point được chọn?** | Thao tác gửi đơn thuộc nhóm VIP |
| **Advice** | Cần làm gì tại những điểm đã chọn? | Ghi sự kiện kiểm toán trước/sau thao tác |
| **Aspect** | Các quy tắc lựa chọn và hành vi liên quan được tổ chức như thế nào? | Khía cạnh kiểm toán thao tác |
| **Weaving** | Hành vi aspect được kết hợp với chương trình ra sao? | Kết nối advice với các join point phù hợp |

Sơ đồ tư duy:

```text
Chương trình nghiệp vụ
       |
       v
Các join point ứng viên
       |
       | pointcut: kiểm tra điều kiện lựa chọn
       v
Các join point được chọn
       |
       | weaving/cơ chế kết hợp hành vi
       v
Advice tham gia vào luồng thực thi
```

Ví dụ có ba join point ứng viên:

```text
j1: thực thi submitOrder("VIP")       → P(j1) = true
j2: thực thi submitOrder("NORMAL")    → P(j2) = false
j3: thực thi cancelOrder("VIP")       → P(j3) = false
```

Một pointcut có thể được mô tả là “chọn thực thi phương thức `submitOrder` có đối số `"VIP"`”. Nó chọn **j1**, không chọn j2 hoặc j3. Advice kiểm toán được kết hợp với j1 theo cơ chế AOP cụ thể.

Đây là **mô tả ngữ nghĩa độc lập công cụ**. Một cơ chế AOP có thể hỗ trợ điểm thực thi phương thức, lời gọi phương thức, truy cập trường, khởi tạo đối tượng hoặc chỉ một tập con trong số đó. Không được giả định mọi loại join point đều được hỗ trợ.

## 3. Join point khác pointcut ở đâu?

Hai khái niệm này dễ bị nhầm vì đều nhắc đến “điểm”.

**Join point** là **điểm có khả năng can thiệp**, chẳng hạn một lần thực thi phương thức hoặc một lời gọi cụ thể. **Pointcut** là **quy tắc chọn** một hoặc nhiều join point phù hợp.

So sánh bằng hình ảnh bộ lọc:

- Join point tương tự một bản ghi đầu vào cần được kiểm tra.
- Pointcut tương tự một điều kiện lọc bản ghi.
- Tập join point phù hợp là **kết quả** của việc áp dụng điều kiện.
- Advice là hành vi chạy tại kết quả đã chọn, không phải điều kiện lọc.

Về mặt logic, cùng một pointcut có thể chọn nhiều join point; nhiều pointcut có thể chọn cùng một join point; và một pointcut cũng có thể không chọn được điểm nào trong lần chạy hiện tại.

**Không nên hiểu pointcut là “một điểm duy nhất”** chỉ vì tên có chữ *point*. Nó thường là biểu thức hoặc định nghĩa điều kiện đại diện cho **một tập**.

### 3.1. Một vị trí mã và nhiều lần xuất hiện động

Xét phương thức:

```java
void submitOrder(String tier) {
    System.out.println("processing " + tier);
}
```

Một điểm thực thi được mô tả ở mức cấu trúc có thể có nhiều **lần xuất hiện khi chạy**:

```text
Lần 1: submitOrder("VIP")
Lần 2: submitOrder("NORMAL")
Lần 3: submitOrder("VIP")
```

Nếu pointcut chỉ kiểm tra tên/phương thức, cả ba lần đều phù hợp. Nếu điều kiện đồng thời kiểm tra đối số bằng `"VIP"`, chỉ lần 1 và lần 3 phù hợp. Vì vậy, phải phân biệt **mẫu vị trí mã** với **ngữ cảnh của từng lần thực thi**.

## 4. Điều kiện pointcut thường xét những gì?

Ngôn ngữ/cơ chế AOP có thể cung cấp nhiều cách mô tả điều kiện. Bảng dưới đây diễn đạt **khái niệm**, không khẳng định một cú pháp chung cho tất cả công cụ.

| Nhóm điều kiện | Điều được kiểm tra | Tình huống |
|---|---|---|
| **Loại join point** | Thực thi phương thức, gọi phương thức, truy cập trường… nếu được hỗ trợ | Chỉ quan sát lúc thực thi phương thức |
| **Chữ ký phương thức** | Tên, kiểu trả về, kiểu tham số, khả năng hiển thị… theo phạm vi công cụ | Chọn các thao tác `submit...` |
| **Kiểu hoặc phạm vi khai báo** | Lớp, giao diện, gói hoặc nhóm thành phần | Chỉ thao tác trong mô-đun đơn hàng |
| **Metadata/annotation** | Phương thức, kiểu hoặc phần tử có một dấu hiệu khai báo | Chọn phương thức mang dấu hiệu cần kiểm toán |
| **Đối tượng nhận lời gọi** | Đặc tính đối tượng thực thi/đích, nếu được hỗ trợ | Chọn thành phần có vai trò nhất định |
| **Đối số thực tế** | Giá trị hoặc kiểu dữ liệu đối số lúc chạy | Chỉ xử lý đơn thuộc nhóm VIP |
| **Luồng điều khiển** | Join point xảy ra trong ngữ cảnh lời gọi nhất định, nếu được hỗ trợ | Chỉ khi đi qua một thao tác cấp cao |
| **Điều kiện ngữ cảnh khác** | Trạng thái hoặc ngữ cảnh có sẵn tại điểm can thiệp | Chỉ khi một điều kiện cho phép được thỏa mãn |

Cần phân biệt “một cơ chế nhận diện được phương thức” với “cơ chế có thể kiểm tra điều kiện bất kỳ trong thân phương thức”. **Pointcut không mặc nhiên đọc được mọi trạng thái nội bộ hay mọi biến cục bộ**; phạm vi phụ thuộc loại join point và thông tin mà mô hình cung cấp.

## 5. Điều kiện theo cấu trúc và điều kiện tại thời điểm chạy

Đây là sự phân chia rất hữu ích khi thiết kế và đánh giá chi phí.

### 5.1. Phần có thể xác định từ cấu trúc

Một điều kiện như “thực thi phương thức tên `submitOrder` của nhóm lớp được chỉ định” có thể được xác định dựa trên tên, kiểu và mô tả chương trình, khi cơ chế có đủ metadata.

Ví dụ:

```text
Tên phương thức = submitOrder
AND lớp khai báo thuộc mô-đun order
```

Điều kiện này thường **không phụ thuộc giá trị đối số của từng lần gọi**. Một lần xác định cấu trúc có thể giúp thu hẹp danh sách điểm cần xử lý.

Tuy nhiên, thời điểm xác định (lúc biên dịch, nạp lớp hoặc chạy) phụ thuộc cơ chế weaving; không nên kết luận tất cả điều kiện cấu trúc luôn được quyết định tại thời điểm biên dịch.

### 5.2. Phần phụ thuộc ngữ cảnh động

Một điều kiện khác có thể phụ thuộc đối số thực tế:

```text
Giá trị đối số đầu tiên = "VIP"
```

Ngay cả khi đã biết đúng phương thức `submitOrder(String)`, điều kiện này có thể cần kiểm tra **mỗi lần thực thi** vì mỗi lần truyền một giá trị khác nhau.

Kết hợp:

```text
P = thực thi phương thức submitOrder(String)
    AND giá trị tham số thứ nhất = "VIP"
```

Nếu đầu vào là `"NORMAL"`, phần cấu trúc đúng nhưng toàn bộ P sai.

**Điều kiện cấu trúc** và **điều kiện động** là hai loại kiểm tra có chi phí, khả năng bao phủ và giới hạn khác nhau. Nhiều cơ chế tối ưu bằng cách loại trước ứng viên không thể khớp theo cấu trúc rồi mới đánh giá phần động khi cần.

## 6. Biểu thức logic: AND, OR, NOT

Pointcut thường cần biểu diễn điều kiện phức hợp. Các phép logic phổ biến ở cấp khái niệm:

- **AND (và):** mọi điều kiện phải đúng; thường **thu hẹp** tập điểm.
- **OR (hoặc):** ít nhất một điều kiện đúng; thường **mở rộng** tập điểm.
- **NOT (phủ định):** loại các điểm thỏa điều kiện bị phủ định, trong **miền join point mà cơ chế xét**.

Giả sử:

```text
A: join point thuộc thao tác submitOrder
B: đối số tier bằng "VIP"
C: join point thuộc thao tác nội bộ cần loại trừ
```

Ta có:

```text
P = (A AND B) AND NOT C
```

Bảng quyết định:

| A | B | C | P = A ∧ B ∧ ¬C |
|---|---|---|---|
| Đúng | Đúng | Sai | **Đúng** |
| Đúng | Đúng | Đúng | Sai |
| Đúng | Sai | Sai | Sai |
| Sai | Đúng | Sai | Sai |
| Sai | Sai | Sai | Sai |

Dấu ngoặc thể hiện nhóm điều kiện. Trong công cụ có ngôn ngữ pointcut cụ thể, phải kiểm tra **cú pháp, độ ưu tiên toán tử và khả năng hỗ trợ phép phủ định**; sơ đồ trên chỉ nói về logic.

### 6.1. Đọc biểu thức như phép toán trên tập hợp

Với tập các join point ứng viên J:

```text
Matched(A AND B) = Matched(A) ∩ Matched(B)
Matched(A OR B)  = Matched(A) ∪ Matched(B)
Matched(NOT A)   = J \ Matched(A)
```

Cách đọc này giúp đánh giá một pointcut lớn có quá rộng hoặc quá hẹp không. Ví dụ `A OR B` có thể vô tình đưa thêm các thao tác chỉ thỏa B nhưng không có ý nghĩa nghiệp vụ mong đợi; `A AND B` có thể bỏ sót hành vi cần quan sát nếu B quá hạn chế.

## 7. Ví dụ Java 17: minh họa điểm chọn bằng điều kiện rõ ràng

Ví dụ sau dùng Java thuần để mô phỏng quá trình **lọc điểm thực thi** và **bao hành vi trước/sau**. Nó **không phải một ngôn ngữ pointcut AOP hoàn chỉnh**. Trong ví dụ, `Proxy` chỉ chặn các lời gọi được thực hiện **qua đối tượng đại diện cho giao diện**; lớp triển khai gọi trực tiếp sẽ đi đường khác.

Chúng ta chọn đúng những lời gọi thỏa **cả ba điều kiện**:

1. Phương thức của giao diện có dấu hiệu `@Observed`.
2. Tên phương thức bằng `submit`.
3. Đối số thứ nhất đúng bằng chuỗi `"VIP"`.

Lưu tệp `PointcutSelectionDemo.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PointcutSelectionDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Observed {}

    interface Orders {
        @Observed
        void submit(String tier);

        void cancel(String tier);
    }

    static class OrdersImpl implements Orders {
        @Override
        public void submit(String tier) {
            System.out.println("submit " + tier);
        }

        @Override
        public void cancel(String tier) {
            System.out.println("cancel " + tier);
        }
    }

    // Quy tắc chọn, mô phỏng vai trò của một pointcut.
    static boolean matches(Method method, Object[] args) {
        boolean structural =
                method.isAnnotationPresent(Observed.class)
                && method.getName().equals("submit");

        boolean runtime =
                args != null
                && args.length == 1
                && "VIP".equals(args[0]);

        return structural && runtime;
    }

    static Object invokeTarget(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    public static void main(String[] args) {
        Orders target = new OrdersImpl();

        Orders observed = (Orders) Proxy.newProxyInstance(
                Orders.class.getClassLoader(),
                new Class<?>[] {Orders.class},
                (proxy, method, values) -> {
                    if (!matches(method, values)) {
                        return invokeTarget(method, target, values);
                    }

                    System.out.println("[audit] before " + method.getName());
                    try {
                        return invokeTarget(method, target, values);
                    } finally {
                        System.out.println("[audit] after " + method.getName());
                    }
                }
        );

        observed.submit("VIP");
        observed.submit("NORMAL");
        observed.cancel("VIP");

        // Gọi trực tiếp: không đi qua proxy ở ví dụ này.
        target.submit("VIP");
    }
}
```

Biên dịch và chạy bằng Java 17 trở lên:

```bash
javac PointcutSelectionDemo.java
java PointcutSelectionDemo
```

Kết quả stdout chính xác:

```text
[audit] before submit
submit VIP
[audit] after submit
submit NORMAL
cancel VIP
submit VIP
```

Giải thích từng lời gọi:

| Lời gọi | Dấu hiệu và tên | Đối số VIP | Kết quả chọn |
|---|---|---|---|
| `observed.submit("VIP")` | Đúng | Đúng | Chọn, chạy phần audit |
| `observed.submit("NORMAL")` | Đúng | Sai | Không chọn |
| `observed.cancel("VIP")` | Sai | Đúng | Không chọn |
| `target.submit("VIP")` | Nếu kiểm tra thuần túy thì đúng | Đúng | **Không được cơ chế proxy này chặn** |

**Điểm cần phân biệt:** `matches(...)` mô phỏng **điều kiện pointcut**, còn những câu `System.out.println("[audit] ...")` mô phỏng **hành vi advice**. `Proxy.newProxyInstance(...)` là phương tiện Java được chọn cho ví dụ để dẫn lời gọi qua bộ lọc; đây không phải bằng chứng rằng AOP nói chung bị giới hạn ở proxy.

Phần `method.isAnnotationPresent(Observed.class)` đang kiểm tra **metadata trên phương thức của giao diện**, vì `Method` mà proxy giao diện nhận được mô tả phương thức giao diện. Một công cụ khác có thể chọn quy tắc dựa trên khai báo của lớp triển khai; hai cách này không đương nhiên cho cùng kết quả. `@Retention(RUNTIME)` làm annotation xuất hiện khi dùng reflection lúc chạy.

Phần `invokeTarget` giải gói `InvocationTargetException` để truyền nguyên nhân lỗi từ phương thức đích; `finally` ghi “after” dù phương thức đích kết thúc bình thường hay ném lỗi. “after” ở đây nghĩa là **đã rời lời gọi được bao**, không có nghĩa thao tác thành công. Ví dụ dùng `void` để giữ trọng tâm tại điều kiện chọn; với phương thức trả kết quả, cơ chế bao phải bảo toàn cả giá trị trả về.

### 7.1. Điều ví dụ chứng minh và điều nó không chứng minh

Ví dụ chứng minh rằng một **điều kiện chọn** có thể tách riêng khỏi **hành động bổ sung**, có thể kết hợp thông tin tĩnh với thông tin động, và có thể bị giới hạn bởi **đường đi mà cơ chế can thiệp quan sát được**.

Ví dụ **không** chứng minh việc weaving ở mọi loại join point; không chọn truy cập trường, khởi tạo đối tượng hay mọi lời gọi tự diễn ra bên trong lớp; không tự động áp dụng cho `target` gốc. Một hệ AOP thực sự cần định nghĩa rõ cơ chế weaving và tập join point hỗ trợ.

## 8. Pointcut và advice: cùng liên quan join point nhưng khác trách nhiệm

Xét yêu cầu:

> Khi thực thi thao tác gửi đơn VIP, ghi thời điểm bắt đầu và kết thúc.

Có hai phần độc lập:

```text
Pointcut: chọn thực thi submitOrder với tier = VIP
Advice:   ghi sự kiện "begin" và "end"
```

Nếu sau này thay định dạng ghi sự kiện từ văn bản sang cấu trúc dữ liệu, quy tắc chọn join point **có thể không đổi**. Nếu mở rộng sang mọi đơn hàng thay vì chỉ VIP, phần hành vi audit **có thể không đổi**. Đó là lợi ích của việc phân biệt hai trách nhiệm.

Một pointcut có thể được tái sử dụng bởi nhiều advice; một advice có thể được liên kết với nhiều điều kiện chọn, tùy mô hình tổ chức. **Aspect** đóng vai trò mô-đun quản lý các định nghĩa liên quan. Mối quan hệ này giúp giảm scattering của việc áp dụng chính sách và tangling của mã nghiệp vụ, nhưng chỉ khi chính sách thực sự là mối quan tâm xuyên suốt.

### 8.1. Pointcut không quyết định việc tiếp tục xử lý

Pointcut trả lời **“có khớp hay không?”**. Nó không tự trả lời **“chạy trước hay sau?”**, **“có thực hiện phần gốc hay không?”** hoặc **“xử lý ngoại lệ thế nào?”**.

Những quyết định đó thuộc semantics của advice và cơ chế kết hợp hành vi. Với một loại advice bao quanh, hành vi có thể gọi phần thực thi gốc qua thao tác tương tự `proceed`; việc bỏ qua hoặc gọi nhiều lần phần gốc có thể thay đổi đáng kể kết quả nghiệp vụ.

Vì thế không nên gán cho pointcut các chức năng như bắt lỗi, thử lại, điều khiển transaction hay thay đổi giá trị trả về. Pointcut chỉ **chọn nơi** một hành vi như vậy có thể áp dụng.

## 9. Pointcut tĩnh, động và thời điểm đánh giá

Nói “điều kiện được kiểm tra khi nào?” đòi hỏi nêu đúng phạm vi:

- **Điều kiện dựa trên cấu trúc** thường có thể được xác định từ mô tả lớp/phương thức. Công cụ có thể xử lý sớm trong quá trình weaving hoặc khi hệ thống tạo cơ chế can thiệp.
- **Điều kiện dựa trên dữ liệu động** có thể cần kiểm tra ở từng lần join point xuất hiện, sau khi biết đối tượng, đối số hoặc ngữ cảnh tương ứng.
- **Điều kiện phụ thuộc luồng điều khiển** (nếu hỗ trợ) có thể cần theo dõi ngữ cảnh lời gọi hoặc trạng thái thực thi bổ sung.

Trong một số mô hình AOP, pointcut trộn cả phần tĩnh và động. Tối ưu hợp lý là thu hẹp phạm vi theo cấu trúc rồi mới kiểm tra điều kiện động ở những chỗ có thể khớp. Nhưng **cú pháp và thời điểm đánh giá chính xác phụ thuộc triển khai**.

Điều này ảnh hưởng đến hiệu năng. Một biểu thức chỉ xét tên phương thức không có cùng chi phí với biểu thức cần kiểm tra trạng thái phức tạp ở mọi lần thực thi. Với hệ thống tần suất lời gọi cao, cần đo đạc số điểm khớp, chi phí kiểm tra và chi phí advice riêng rẽ.

## 10. Các bẫy thường gặp khi viết điều kiện chọn

### 10.1. Chọn quá rộng (*overmatching*)

Quy tắc “mọi phương thức có tên bắt đầu `save`” có thể bao gồm thao tác nội bộ, thao tác thử nghiệm hoặc nhóm dữ liệu khác. Một advice ghi chi tiết dữ liệu có thể vô tình thu thập quá nhiều thông tin hoặc áp dụng sai chính sách.

**Cách xử lý:** xác định mục đích của chính sách rồi giới hạn theo **ranh giới có ý nghĩa** (nhóm thành phần, loại join point, metadata rõ ràng), kết hợp phép loại trừ nếu phù hợp; kiểm tra các điểm khớp ngoài dự kiến.

### 10.2. Chọn quá hẹp (*undermatching*)

Quy tắc “chỉ phương thức tên `submitOrder`” sẽ không bao phủ thao tác cùng ý nghĩa nhưng tên `placeOrder`. Khi mã được refactor, pointcut có thể không còn khớp như mong muốn.

**Cách xử lý:** ưu tiên hợp đồng/metadata ổn định khi thích hợp; kiểm thử những thao tác **phải** khớp, và rà soát chính sách khi thay tên hoặc tách chức năng.

### 10.3. Nhầm lời gọi (*call*) với thực thi (*execution*)

Ở mô hình có phân biệt hai loại join point:

- **Call:** điểm một vị trí mã tạo lời gọi đến phương thức.
- **Execution:** điểm phương thức đích thực sự bắt đầu thực thi.

Một phương thức có thể được gọi từ nhiều vị trí, nên lựa chọn theo call site có ý nghĩa khác chọn theo execution. Không phải mọi cơ chế AOP đều cung cấp cả hai loại; với cơ chế giới hạn ở lời gọi qua một lớp trung gian, phạm vi chặn còn phụ thuộc đường đi lời gọi.

Đừng diễn giải hai loại này như từ đồng nghĩa khi cần chứng minh độ bao phủ.

### 10.4. Giả định mọi lời gọi nội bộ đều được chặn

Một phương thức trong đối tượng có thể gọi phương thức khác của **chính đối tượng đó**. Nếu cơ chế can thiệp chỉ hoạt động khi lời gọi đi qua proxy bên ngoài, đường gọi nội bộ có thể không đi qua proxy. Ngược lại, một cơ chế weaving ở cấp mã có thể hỗ trợ ngữ nghĩa khác.

**Cách xử lý:** kiểm tra đường đi cụ thể, đối tượng/giao diện qua đó lời gọi phát sinh, và các join point thực sự được cơ chế hỗ trợ. Không suy ra phạm vi AOP từ chỉ một tên pointcut.

### 10.5. Nhầm chữ ký khai báo với giá trị thực tế

Ví dụ `submitOrder(String tier)` có kiểu tham số luôn là `String`, nhưng đối số thực tế có thể là `"VIP"`, `"NORMAL"` hoặc `null`. Điều kiện **kiểu đối số** không tự tương đương điều kiện **giá trị đối số**.

Tương tự, metadata trên phương thức giao diện có thể khác metadata trên phương thức của lớp triển khai. Luôn chỉ rõ nơi metadata được đọc và thời điểm ngữ cảnh được kiểm tra.

### 10.6. Điều kiện chọn gây tác dụng phụ

Về mặt thiết kế, một điều kiện pointcut nên dễ dự đoán, diễn tả chính sách lựa chọn và hạn chế tác dụng phụ. Nếu điều kiện vừa truy vấn mạng, vừa thay đổi trạng thái hoặc ném ngoại lệ, việc đánh giá pointcut có thể làm chương trình khó giải thích, khó kiểm thử và gây chi phí đáng kể.

Nếu có điều kiện động cần dữ liệu phức tạp, cân nhắc điểm áp dụng ổn định hơn, cung cấp metadata phù hợp hoặc tổ chức kiểm tra rõ ràng bên trong thành phần chính sách.

### 10.7. Một điểm khớp nhiều aspect

Một join point có thể thỏa nhiều pointcut của các aspect khác nhau. Pointcut **không tự quyết định thứ tự** các advice. Ví dụ có advice đo thời gian và advice kiểm toán; kết quả đo có thể bao gồm hoặc loại trừ chi phí ghi kiểm toán tùy thứ tự bao quanh.

Cần xác định thứ tự bằng hợp đồng hoặc cấu hình của cơ chế đang dùng, và có kiểm thử chứng minh đường chạy khi thứ tự thực sự ảnh hưởng đến ngữ nghĩa.

## 11. Thiết kế pointcut có thể kiểm chứng

Một pointcut hữu ích cần có **tên thể hiện ý định** và có phạm vi giải thích được bằng ví dụ cụ thể. Thay vì đặt tên “allMethods”, có thể chọn tên khái niệm như “các thao tác hoàn tất đơn hàng cần kiểm toán”. Tên là tài liệu cho chính sách, còn biểu thức phải thực sự khớp với tài liệu đó.

Quy trình thực hành:

1. **Viết yêu cầu bằng ngôn ngữ nghiệp vụ/kỹ thuật:** “Ghi kiểm toán cho thao tác gửi đơn hàng VIP”. Làm rõ mục tiêu, điểm bắt đầu/kết thúc, các trường hợp ngoại lệ.
2. **Chọn loại join point:** cần quan sát lúc thực thi phương thức, tại nơi gọi hay một sự kiện khác? Xác minh cơ chế hỗ trợ loại đó.
3. **Chọn điều kiện cấu trúc:** phạm vi lớp/giao diện, tên hoặc metadata biểu đạt chủ đích.
4. **Bổ sung điều kiện động nếu cần:** đối số, đối tượng, ngữ cảnh có thể thay đổi từng lần chạy.
5. **Xác định ngoại lệ lựa chọn:** thao tác nội bộ, các đối tượng/loại dữ liệu không thuộc chính sách.
6. **Lập bảng tình huống khớp/không khớp:** ưu tiên cả nhánh biên, `null`, lỗi và luồng nội bộ.
7. **Kiểm tra bằng hành vi quan sát được:** advice chạy tại điểm nào, bao nhiêu lần, theo thứ tự nào; xác nhận mã nghiệp vụ vẫn giữ đúng kết quả.
8. **Rà lại khi refactor:** đổi tên, đổi phân tầng hoặc chuyển lời gọi qua ranh giới khác có thể làm tập join point được chọn thay đổi.

### 11.1. Bảng kiểm thử lựa chọn

Với pointcut minh họa ở phần 7, lập bảng mong đợi trước khi chạy:

| Trường hợp | Dự kiến |
|---|---|
| `observed.submit("VIP")` | Khớp |
| `observed.submit("NORMAL")` | Không khớp |
| `observed.submit(null)` | Không khớp |
| `observed.cancel("VIP")` | Không khớp |
| `target.submit("VIP")` qua tham chiếu gốc | Không được proxy can thiệp, dù bản thân điều kiện dữ liệu có thể đúng |

Đây là hai tầng kiểm chứng khác nhau:

- **Kiểm chứng điều kiện:** với một join point/ngữ cảnh được đưa vào bộ lọc, `P(j)` trả về đúng hay sai?
- **Kiểm chứng phạm vi can thiệp:** join point đó có **thực sự được cơ chế AOP nhìn thấy/bao phủ** khi chạy không?

Một bộ kiểm thử tốt kiểm tra cả hai tầng; chỉ kiểm tra logic biểu thức nhưng bỏ qua đường gọi có thể tạo cảm giác bao phủ sai.

### 11.2. Quan sát được vì sao một điểm được chọn

Khi một pointcut phức tạp khớp bất ngờ, cần cách xác định **phần nào của biểu thức đúng**, loại join point đã xét, đối tượng/ngữ cảnh nào được dùng và advice nào tham gia. Điều này có thể thực hiện bằng chẩn đoán của công cụ hoặc bằng phép thử nhỏ độc lập với nghiệp vụ.

Mục tiêu của khả năng chẩn đoán là giải thích được: “lần thực thi nào được chọn, theo điều kiện gì, dẫn tới hành vi bổ sung nào?”. Pointcut càng rộng và càng động, nhu cầu kiểm chứng càng cao.

## 12. Tình huống nâng cao cần cân nhắc

### 12.1. Điều kiện theo luồng điều khiển

Có nhu cầu chọn “mọi thao tác ghi dữ liệu xảy ra **bên trong** quy trình xử lý đơn hàng”. Đây không đơn thuần là điều kiện tên lớp hoặc phương thức: một thao tác ghi dữ liệu chỉ phù hợp khi xuất hiện trong ngữ cảnh lời gọi được chỉ định.

Nếu cơ chế hỗ trợ pointcut theo **control flow**, chính sách này có thể biểu diễn được. Nhưng khả năng hỗ trợ, chi phí theo dõi ngữ cảnh, ranh giới giữa luồng đồng bộ và tác vụ chạy ở luồng khác đều phải xác minh. Không nên đồng nhất “được gọi sau” với “nằm trong cùng luồng điều khiển”.

### 12.2. Kết hợp pointcut bằng các định nghĩa tái sử dụng

Khi nhiều chính sách dùng chung một miền hoạt động, có thể đặt tên những tập con ở mức ý định:

```text
P_orderOperation = join point thuộc thao tác đơn hàng
P_observed       = thao tác được đánh dấu cần quan sát
P_vip            = đối số tier là VIP

P_auditVIP = P_orderOperation AND P_observed AND P_vip
```

Phân rã như vậy giúp kiểm tra từng phần riêng rẽ. Tuy nhiên, nếu định nghĩa trung gian quá nhiều hoặc dùng điều kiện tên khó hiểu, hệ thống vẫn có thể trở nên khó lần dấu. Dùng tên mô tả ý nghĩa, có bảng trường hợp và chỉ trừu tượng hóa khi phần điều kiện thực sự được chia sẻ.

### 12.3. Pointcut và thay đổi phiên bản mã

Một pointcut phụ thuộc tên/signature có thể thay đổi tập khớp khi phương thức được đổi tên, overload, chuyển sang giao diện khác hoặc thay đổi khả năng hiển thị. Một pointcut dựa trên metadata có thể khác đi khi một annotation được đặt tại lớp khai báo khác.

Do đó, khi thay đổi API nội bộ, cần coi tập điểm được pointcut chọn như một phần hợp đồng tích hợp, đặc biệt với chính sách kiểm toán, truy cập hay tác vụ có ảnh hưởng lớn đến tính đúng đắn.

### 12.4. Đối số, dữ liệu nhạy cảm và an toàn của chính sách

Điều kiện theo đối số không đồng nghĩa được phép ghi toàn bộ đối số ra log. Pointcut có thể chọn đúng, nhưng advice phải tuân thủ chính sách xử lý dữ liệu. Nếu kết quả kiểm tra quyền phụ thuộc thông tin nhạy cảm hoặc trạng thái thay đổi nhanh, cần đặt kiểm tra đúng ranh giới đáng tin cậy và tránh để bộ chọn tạo ra sự bảo đảm an toàn giả.

Không nên dùng tên phương thức hoặc một pointcut dễ bỏ sót như **bằng chứng duy nhất** rằng một yêu cầu kiểm soát bắt buộc đã được áp dụng cho mọi đường đi. Các đường gọi bị loại trừ và phạm vi join point phải được kiểm chứng độc lập.

### 12.5. Lời gọi bất đồng bộ và ý nghĩa của thời điểm can thiệp

Một phương thức có thể trả về ngay một đối tượng đại diện cho công việc chưa hoàn thành. Pointcut ở **thực thi phương thức tạo tác vụ** chọn thời điểm tạo/lập lịch, không tự động có nghĩa chọn thời điểm hoàn tất công việc. Tùy mục tiêu, có thể cần một join point/sự kiện khác hoặc theo dõi vòng đời tác vụ.

Pointcut chỉ chọn một tập điểm đã mô tả. Nó không tự mở rộng ý nghĩa “đã xử lý xong” sang toàn bộ công việc bất đồng bộ nếu điểm được chọn mới phản ánh phần bắt đầu.

## 13. Bài tập vận dụng

**Bài 1.** Một hệ thống có các lần thực thi:

```text
createOrder("VIP")
createOrder("NORMAL")
cancelOrder("VIP")
createOrder("VIP")
```

Điều kiện `P = tên phương thức là createOrder AND đối số thứ nhất bằng "VIP"` chọn những lần nào?

**Đáp án:** lần thứ nhất và thứ tư. Có hai lần xuất hiện động phù hợp dù cùng một phương thức khai báo.

**Bài 2.** Một advice đo thời gian đang chạy trên cả `healthCheck()`, `calculateTotal()` và `generateReport()`, nhưng yêu cầu chỉ đo tác vụ có ảnh hưởng lớn đến người dùng. Cần sửa pointcut hay sửa advice?

**Gợi ý:** trước tiên làm rõ tập thao tác cần quan sát và **điều chỉnh pointcut** cho đúng phạm vi; hành vi đo thời gian trong advice có thể không cần đổi. Việc đưa hay không đưa `healthCheck()` vào tập chọn là quyết định phạm vi chính sách, không phải thuật toán tính thời lượng.

**Bài 3.** Pointcut dự kiến khớp `submit("VIP")`, kiểm tra riêng hàm `matches` cho ra `true`, nhưng lúc chạy advice không xuất hiện. Nêu hai nguyên nhân có thể kiểm tra.

**Đáp án gợi ý:** (1) lời gọi không đi qua **cơ chế can thiệp** (ví dụ đi trực tiếp đến đối tượng đích trong ví dụ proxy); (2) join point/định nghĩa metadata thực tế mà công cụ xét không giống đối tượng `Method` dùng trong phép thử. Cũng cần xét giới hạn loại join point và cấu hình weaving.

**Bài 4.** Vì sao `P = A OR B` có thể rủi ro hơn `P = A AND B` nếu advice thực hiện thao tác có tác dụng phụ?

**Gợi ý:** OR mở rộng tập khớp, có thể áp dụng hành vi vào điểm chỉ thuộc một nhóm; AND thu hẹp tập khớp và có nguy cơ bỏ sót điểm cần xử lý. Không có toán tử “an toàn hơn” trong mọi trường hợp; phải khớp với hợp đồng nghiệp vụ và kiểm thử cả các điểm nên chọn lẫn điểm phải loại.

## 14. Checklist ghi nhớ

- **Join point**: một điểm mà mô hình AOP nhận diện và có thể can thiệp.
- **Pointcut**: **điều kiện/bộ lọc chọn một tập join point**; có thể khớp không, một hoặc nhiều điểm.
- **Advice**: hành vi bổ sung tại các điểm được chọn; không phải biểu thức lựa chọn.
- **Aspect**: mô-đun hóa chính sách và hành vi liên quan; **weaving** là bước kết hợp vào chương trình.
- Điều kiện có thể dựa trên **cấu trúc** hoặc **ngữ cảnh động**, và dùng **AND/OR/NOT** để kết hợp.
- Tập điểm được chọn chịu ảnh hưởng bởi **loại join point, ranh giới can thiệp, thời điểm đánh giá và khả năng hỗ trợ thực tế**.
- Chất lượng pointcut được đo bằng việc nó **chọn đúng điểm, không bỏ sót, không chọn thừa và kiểm chứng được**.

Khi nhìn thấy một pointcut, hãy luôn hỏi: **“Quy tắc này chọn những join point nào, dựa vào thông tin gì, tại thời điểm nào, và cơ chế AOP có nhìn thấy tất cả những điểm đó không?”** Trả lời được bốn câu này mới hiểu đầy đủ vai trò “điều kiện lựa chọn tập điểm can thiệp” của pointcut.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-component-model-004">Advice là hành vi được thực hiện tại điểm đã chọn</a>

<details>
<summary>Xem chi tiết</summary>

# Advice là hành vi được thực hiện tại điểm đã chọn

## 1. Advice trong mô hình lập trình hướng khía cạnh

Trong **Aspect-Oriented Programming (AOP)**, **advice** là hành vi bổ sung được chạy tại một điểm thực thi được lựa chọn. Nếu **pointcut** trả lời “chọn điểm nào?”, **advice** trả lời “phải làm gì tại điểm ấy và khi nào?”. Advice thường dùng cho logging, audit, đo hiệu năng, truy vết, kiểm soát truy cập ở ranh giới kỹ thuật hoặc quản lý tài nguyên. Nó giúp các chính sách cắt ngang không trộn vào thuật toán nghiệp vụ.

Ví dụ, một thao tác `pay()` có thể cần ghi “bắt đầu”, thực hiện thanh toán, ghi “thành công” hoặc “thất bại”, và luôn cập nhật số liệu đo thời gian. Tính tiền và kiểm tra số dư vẫn là nghiệp vụ; những hành vi quan sát bao quanh nó có thể được mô hình hóa bằng advice.

Các thành phần liên hệ với nhau:

| Thành phần | Vai trò | Ví dụ |
| --- | --- | --- |
| **Join point** | Một điểm thực thi có thể can thiệp | Một lần thực thi hoặc gọi phương thức `pay()` |
| **Pointcut** | Biểu thức/điều kiện chọn tập join point | Chỉ chọn các thao tác thanh toán |
| **Advice** | Hành vi được kích hoạt tại điểm đã chọn | Ghi audit trước/sau thanh toán |
| **Aspect** | Mô-đun chứa chính sách cắt ngang, thường gồm pointcut và advice | Chính sách audit thanh toán |
| **Target** | Đối tượng hoặc logic gốc | Bộ xử lý thanh toán |
| **Weaving** | Cách kết hợp aspect vào đường thực thi | Proxy, biến đổi mã lúc biên dịch hoặc nạp lớp |

**Advice và pointcut là hai quyết định khác nhau.** Việc có một hàm tên `beforeAudit` không tự động khiến nó được gọi tại mọi thao tác. Cơ chế AOP chỉ kích hoạt khi join point phát sinh, được pointcut chọn và nằm trong phạm vi mà công nghệ can thiệp được.

## 2. Những loại advice quan trọng

Các tên gọi sau là mô hình khái niệm phổ biến của AOP. Khả năng và cú pháp cụ thể phụ thuộc vào cơ chế thực thi, vì vậy không suy luận rằng mọi bộ máy AOP có chính xác cùng tập tính năng.

| Loại advice | Khi chạy | Dữ liệu có thể biết | Cách dùng tiêu biểu |
| --- | --- | --- | --- |
| **Before** | Trước khi logic gốc được chuyển quyền điều khiển | Ngữ cảnh, tham số đầu vào | Ghi bắt đầu, kiểm tra điều kiện kỹ thuật |
| **After returning** | Sau khi logic gốc hoàn tất bình thường | Kết quả trả về, kể cả `null` | Ghi thành công, thống kê kết quả |
| **After throwing** | Khi logic gốc thoát bằng ngoại lệ | Ngoại lệ phát sinh | Audit thất bại, thống kê lỗi |
| **After / finally** | Khi phạm vi được bao kết thúc bất kể thành công/lỗi | Tình trạng kết thúc theo hợp đồng cơ chế | Dọn tài nguyên, đo thời gian, bộ đếm |
| **Around** | Bao lấy lời thực thi và quyết định chuyển tiếp | Đầu vào, kết quả, ngoại lệ, quyền gọi tiếp | Thời gian, cache, kiểm soát thực thi, retry |

### Before

Before chạy trước khi chuyển vào thao tác gốc. Nó thích hợp với hành vi như gắn correlation ID hoặc thông báo thao tác bắt đầu. Advice có thể tự gây lỗi hoặc có cơ chế từ chối thao tác, nhưng cách ngăn việc chạy target phải phù hợp hợp đồng AOP đang dùng. **Không đồng nhất before với quy tắc nghiệp vụ**: kiểm tra “số tiền phải dương” thường thuộc logic miền, trong khi kiểm tra danh tính đã xác thực ở ranh giới có thể là chính sách cắt ngang.

### After returning

After returning chỉ chạy khi thao tác hoàn tất bình thường. Trả về `null` vẫn là trả về bình thường; ném ngoại lệ thì không. Nếu advice quan sát kết quả, nó phải tôn trọng kiểu trả về và tránh vô tình sửa đối tượng mutable mà caller cũng đang dùng.

### After throwing

After throwing chạy theo nhánh ngoại lệ từ thao tác được quan sát. Đây không đồng nghĩa với “ngoại lệ đã được xử lý”. Một advice chỉ ghi lỗi nên chuyển tiếp ngoại lệ theo đúng hợp đồng. Việc đổi lỗi nghiệp vụ thành một thông báo “thành công” làm caller hiểu sai trạng thái thực thi.

### After / finally

After/finally dành cho việc phải thực hiện ở cả nhánh thành công và thất bại, như giải phóng tài nguyên hoặc chốt số liệu đo. Từ “finally” chỉ có ý nghĩa **trong phạm vi đã thực sự được bao**: nếu lời gọi không đi qua ranh giới aspect/proxy, advice không chạy. Ngay cả khi có `finally`, việc code chính advice ném ngoại lệ cũng có thể ảnh hưởng ngoại lệ gốc.

### Around

Around nắm quyền bao quanh lời thực thi: làm việc trước, **gọi tiếp**, làm việc sau, hoặc có chủ đích quyết định không gọi tiếp. Một advice `around` có thể hỗ trợ các chính sách như cache, retry hoặc timeout, nhưng cần đặc biệt cẩn trọng vì nó có khả năng thay đổi số lần thực thi, kết quả hoặc ngoại lệ.

## 3. Điều gì xảy ra tại một join point đã chọn?

Hãy giả sử pointcut chọn phương thức `pay(int)` nhưng không chọn `health()`. Một sơ đồ ý niệm:

~~~text
Caller
  |
  +-- health() ------------------------> Target.health()
  |                                      (không khớp pointcut)
  |
  +-- pay(7) --> [Điểm được chọn]
                    |
                    v
                [Before]
                    |
                    v
              [Target.pay(7)]
                    |
            +-------+--------+
            |                |
       return bình thường   ném lỗi
            |                |
            v                v
    [After returning]  [After throwing]
            |                |
            +--------+-------+
                     v
               [After/finally]
                     |
                     v
              Caller nhận kết quả
                 hoặc ngoại lệ
~~~

Sơ đồ không quy định thứ tự của tất cả công nghệ AOP. Khi nhiều aspect cùng khớp, thứ tự cụ thể cần được định nghĩa theo cơ chế precedence. Ngoài ra, **join point là lời gọi** và **join point là lần thực thi** không nhất thiết là một: nơi phát sinh lời gọi và nơi thực thi có thể khác nhau.

Để một advice thực sự kích hoạt, cần xác định đủ bốn điều kiện: loại join point được hỗ trợ, pointcut khớp, đường chạy đi qua vị trí đã weaving/proxy, và advice được kết hợp thành công theo thứ tự đã khai báo.

## 4. Ngữ cảnh và dữ liệu mà advice có thể quan sát

Tùy bộ máy AOP, một advice có thể lấy tên/chữ ký phương thức, thông tin kiểu, annotation, target, đối tượng gọi, tham số, giá trị trả về, ngoại lệ hoặc thông tin ngữ cảnh như correlation ID. Around thường được trao một thao tác chuyển tiếp gọi là `proceed`.

Không phải tất cả các loại advice đều thấy tất cả dữ liệu. **Before** chưa thể quan sát kết quả chưa được tính; **after returning** có kết quả bình thường; **after throwing** có nguyên nhân lỗi; **around** có thể kiểm soát cách chuyển tiếp và quan sát nhánh kết thúc.

Các giá trị đầu vào có thể là mutable. Nếu advice sửa danh sách, mảng hoặc đối tượng truyền vào, nó có thể làm thay đổi hành vi nghiệp vụ. Với audit, tracing và metrics, nên ưu tiên **quan sát** và xử lý dữ liệu tối thiểu cần thiết. Không đưa token, mật khẩu, dữ liệu cá nhân hoặc toàn bộ nội dung thanh toán vào log chỉ vì join point cho phép truy cập.

## 5. Ví dụ Java 17+: một điểm chọn, nhiều thời điểm chạy advice

Ta sử dụng **JDK dynamic proxy** để minh họa cơ chế chọn điểm và bao quanh lời gọi thông qua interface. Đây là một bản dựng nhỏ để học cách advice hoạt động, **không phải một engine AOP đầy đủ**. Bộ chọn đơn giản là `method.getName().equals("pay")`; `Proceeding` là giao diện chuyển tiếp do ví dụ tự định nghĩa.

Tạo tệp `AdviceDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class AdviceDemo {
    interface Payment {
        String pay(int amount);
        String health();
    }

    static final class PaymentCore implements Payment {
        @Override
        public String pay(int amount) {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            System.out.println("BUSINESS pay " + amount);
            return "receipt:" + amount;
        }

        @Override
        public String health() {
            return "OK";
        }
    }

    @FunctionalInterface
    interface Proceeding {
        Object proceed() throws Throwable;
    }

    static final class AuditAdvice {
        int finishedCalls;

        Object around(Method method, Proceeding next) throws Throwable {
            System.out.println("AROUND enter");
            try {
                System.out.println("BEFORE " + method.getName());
                Object result;
                try {
                    result = next.proceed();
                } catch (Throwable failure) {
                    System.out.println("AFTER_THROWING "
                            + failure.getClass().getSimpleName());
                    throw failure;
                }
                System.out.println("AFTER_RETURNING " + result);
                return result;
            } finally {
                finishedCalls++;
                System.out.println("AFTER_FINALLY");
                System.out.println("AROUND exit");
            }
        }
    }

    static Object callTarget(Object target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapper) {
            throw wrapper.getCause();
        }
    }

    static Payment withAdvice(Payment target, AuditAdvice advice) {
        return (Payment) Proxy.newProxyInstance(
                Payment.class.getClassLoader(),
                new Class<?>[] { Payment.class },
                (proxy, method, args) -> {
                    if (!method.getName().equals("pay")) {
                        return callTarget(target, method, args);
                    }
                    return advice.around(
                            method, () -> callTarget(target, method, args));
                });
    }

    public static void main(String[] args) {
        AuditAdvice advice = new AuditAdvice();
        Payment payment = withAdvice(new PaymentCore(), advice);
        System.out.println("HEALTH " + payment.health());
        System.out.println("RESULT " + payment.pay(7));
        try {
            payment.pay(0);
        } catch (IllegalArgumentException failure) {
            System.out.println("CALLER " + failure.getMessage());
        }
        System.out.println("FINALLY_COUNT " + advice.finishedCalls);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -encoding UTF-8 AdviceDemo.java
java AdviceDemo
~~~

Đầu ra chính xác (đã kiểm chứng trên JDK với `javac --release 17`):

~~~text
HEALTH OK
AROUND enter
BEFORE pay
BUSINESS pay 7
AFTER_RETURNING receipt:7
AFTER_FINALLY
AROUND exit
RESULT receipt:7
AROUND enter
BEFORE pay
AFTER_THROWING IllegalArgumentException
AFTER_FINALLY
AROUND exit
CALLER amount must be positive
FINALLY_COUNT 2
~~~

Cách diễn giải:

1. `health()` không khớp pointcut, nên không có dòng audit nào.
2. `pay(7)` khớp: around vào → before → nghiệp vụ → after returning → finally → around ra → caller nhận `receipt:7`.
3. `pay(0)` khớp: around vào → before → nghiệp vụ ném `IllegalArgumentException` → after throwing → finally → around ra → caller nhận lỗi gốc. Không có `AFTER_RETURNING` trên nhánh lỗi.
4. `finishedCalls` bằng `2` bởi chỉ hai lời gọi `pay()` đi qua `AuditAdvice`; `health()` không được tính.

`callTarget()` bóc `InvocationTargetException` do reflection tạo ra. Nhờ vậy advice thấy loại ngoại lệ thực tế thay vì lớp bọc từ API phản chiếu. Code `PaymentCore` không cần biết advice hay proxy đang tồn tại: lớp đó tập trung vào nghiệp vụ.

## 6. Around và `proceed`: quyền lực đi kèm trách nhiệm

Around không chỉ “chạy ở trước và sau”. Nó có thể quyết định số lần target được thực thi. Bảng sau nêu rõ tác động:

| Quyết định trong around | Hệ quả | Điều kiện thiết kế |
| --- | --- | --- |
| Gọi `proceed` đúng một lần | Target có thể chạy đúng một lần | Giữ kết quả, ngoại lệ và hợp đồng |
| Không gọi `proceed` | Target không chạy | Cache hit hoặc từ chối có chủ đích, được phép |
| Gọi `proceed` nhiều lần | Target có thể thực hiện nhiều lần | Retry có giới hạn, phân loại lỗi và idempotency |
| Bắt rồi nuốt ngoại lệ | Caller không biết thao tác gốc lỗi | Chỉ làm nếu đặc tả API cho phép |
| Chuyển kết quả hoặc ngoại lệ | Caller thấy hợp đồng khác ban đầu | Phải được mô tả và kiểm thử rõ ràng |

Một minh họa ý niệm cho around đo thời gian (không phải chương trình chạy độc lập):

~~~java
Object measure(Proceeding next) throws Throwable {
    long started = System.nanoTime();
    try {
        return next.proceed();
    } finally {
        long nanos = System.nanoTime() - started;
        recordDuration(nanos); // Dịch vụ metrics giả định.
    }
}
~~~

`System.nanoTime()` phù hợp để đo thời lượng, không phải thời điểm theo lịch. Nếu `recordDuration()` ném ngoại lệ trong `finally`, lỗi ấy có thể che lỗi gốc đang thoát ra; việc ghi metrics thất bại phải có chính sách xử lý riêng. Đừng tự chọn “nuốt tất cả lỗi” hay “luôn ném lỗi metrics” khi chưa định nghĩa yêu cầu.

**Retry đặc biệt nhạy cảm.** Gọi `proceed` lần nữa có thể thu tiền hai lần hoặc tạo hai đơn hàng. Chỉ retry khi xác định được lỗi tạm thời, giới hạn số lần, không retry những lỗi vĩnh viễn và có cơ chế chống lặp an toàn đối với thao tác có tác dụng phụ.

## 7. Nhiều advice khớp cùng một điểm: thứ tự thực thi

Một phương thức có thể đồng thời khớp advice đo thời gian, audit và kiểm quyền. Nếu chúng được bao theo thứ tự ngoài vào trong:

~~~text
Timing.enter
  Audit.enter
    Authorization.enter
      Business
    Authorization.exit
  Audit.exit
Timing.exit
~~~

Ở nhánh bình thường, wrapper ngoài bắt đầu trước và kết thúc sau wrapper trong. Với `finally`, các tầng đã đi vào sẽ được tháo ngăn xếp theo thứ tự ngược khi một tầng trong ném lỗi. Nhưng **thứ tự aspect không được bảo đảm bởi tên lớp hoặc vị trí trong file**; hãy xác định rõ thứ tự theo quy tắc precedence hoặc cấu hình của cơ chế AOP.

Thứ tự thay đổi ý nghĩa của số liệu:

- Timing bao cả kiểm quyền thì đo thời gian xử lý toàn bộ yêu cầu; timing nằm phía trong bước kiểm quyền thì không tính thời gian kiểm quyền.
- Audit nằm sau bước kiểm quyền có thể bỏ lỡ việc ghi lại lời gọi bị từ chối trước khi nghiệp vụ chạy. Nếu cần ghi từ chối, đặt audit bao ngoài hoặc bổ sung một đường ghi rõ ràng.
- Nếu retry ở ngoài metrics, mỗi lần thử có thể tạo một phép đo; nếu metrics ở ngoài retry, một phép đo có thể tính tổng thời gian các lần thử.
- Nếu advice ngoài đổi ngoại lệ thành kết quả mặc định, caller không nhận được lỗi vốn đã được advice trong quan sát.

Vì vậy, thiết kế cần nêu chính sách ưu tiên, đường lỗi, số lần chạy target, và điều kiện bắt đầu/kết thúc của mỗi advice.

## 8. Phạm vi thực thi và các góc cạnh nâng cao

### 8.1. Lời gọi trực tiếp, self-invocation và proxy

JDK dynamic proxy trong ví dụ chỉ can thiệp lời gọi **qua proxy**, trên interface được khai báo. Nếu thực hiện `new PaymentCore().pay(7)` trực tiếp, `AuditAdvice` không chạy. Khi target gọi phương thức khác của chính nó qua `this`, lời gọi nội bộ có thể không đi qua proxy; hiện tượng này thường gọi là **self-invocation**.

Không được mặc định rằng một bộ máy proxy can thiệp được constructor, phương thức `private`, truy cập field, `final` hay bất kỳ điểm thực thi nào. Một cách weaving ở mức bytecode có thể có tập join point khác. Phạm vi được hỗ trợ phải được kiểm tra theo cơ chế áp dụng thực tế.

### 8.2. Ngoại lệ do advice tự tạo

Lỗi không chỉ phát sinh từ target. `before`, `after returning`, `after throwing`, `finally` hoặc bộ chuyển tiếp đều có thể tự gây ngoại lệ. Trong ví dụ, `AFTER_THROWING` chỉ bao đường gọi `next.proceed()`, vì vậy nó đại diện cho lỗi tầng tiếp theo, không coi mọi lỗi logging sau đó là lỗi nghiệp vụ. Cần xác định khi nào lỗi hạ tầng audit gây thất bại thao tác, khi nào được báo riêng, và làm sao bảo toàn nguyên nhân gốc.

### 8.3. Bất đồng bộ: return chưa đồng nghĩa với hoàn tất

Một phương thức có thể trả về `CompletableFuture` trước khi tác vụ nền hoàn thành. `After returning` tại điểm phương thức trả về chỉ biết đã nhận một Future, **không bảo đảm công việc bên trong đã thành công**. Muốn đo tổng thời gian hoặc audit kết quả cuối, cần theo dõi sự kiện hoàn tất của Future bằng cơ chế phù hợp. Ngữ cảnh tracing cũng có thể cần được truyền rõ qua biên giới thread.

### 8.4. Trạng thái dùng chung và đồng thời

Advice có thể dùng chung một instance cho nhiều lời gọi. Biến `int finishedCalls` trong ví dụ là minh họa tuần tự; nếu nhiều thread cùng cập nhật, phép `++` không an toàn đồng thời. Với bộ đếm chung, có thể dùng `AtomicLong`, `LongAdder` hoặc hệ thống metrics thích hợp. Không cất tham số hay dữ liệu nhạy cảm của lần gọi hiện tại trong field dùng chung nếu chưa có cơ chế cô lập.

### 8.5. Chi phí và khả năng quan sát

Advice chạy tại các điểm được chọn; pointcut quá rộng có thể làm tăng chi phí reflection, ghi log, đo metrics hoặc bùng nổ sự kiện. Hãy đo overhead trên tải đại diện và dùng pointcut đủ cụ thể. Một chính sách ẩn trong AOP cũng cần tài liệu: tìm được pointcut ở đâu, thứ tự advice ra sao, những phương thức nào bị ảnh hưởng và cách theo dấu khi có lỗi.

## 9. Kiểm thử advice như một hợp đồng thực thi

| Tình huống | Đường gọi | Kết quả cần kiểm chứng |
| --- | --- | --- |
| Target thành công | `pay(7)` qua proxy | Before → target → after returning → finally; return giữ nguyên |
| Target ném lỗi | `pay(0)` qua proxy | Before → after throwing → finally; ngoại lệ gốc còn nguyên |
| Pointcut không khớp | `health()` qua proxy | Không có audit, không tăng bộ đếm |
| Đi đường không được can thiệp | `PaymentCore.pay()` trực tiếp | Không kích hoạt advice trên proxy |
| Nhiều advice | Hai wrapper cùng khớp | Thứ tự đúng, số lần thực thi nghiệp vụ đúng |
| Advice ném lỗi | Hệ thống ghi audit gặp lỗi | Xử lý theo chính sách đã mô tả, không vô tình che lỗi gốc |
| Async | Target trả `CompletableFuture` | Phân biệt trả Future với tác vụ hoàn tất |

Kiểm thử trực tiếp phương thức `around()` là kiểm thử logic bên trong; nó **không đủ** chứng minh pointcut chọn đúng hoặc weaving/proxy áp dụng đúng. Phải gọi qua ranh giới tích hợp thật, thử cả trường hợp khớp và không khớp.

Một tiêu chuẩn cơ bản của advice chỉ quan sát: **kết quả, ngoại lệ và số lần thực thi nghiệp vụ phải giữ nguyên**. Nếu advice cố ý thay đổi một trong ba điều đó (ví dụ cache, retry, từ chối), hãy coi đó là hợp đồng chức năng có kiểm thử riêng.

## 10. Sai lầm thường gặp

1. **Lẫn advice với pointcut:** advice là hành vi; pointcut mới xác định điểm áp dụng.
2. **Gọi mọi hành vi tái sử dụng là advice:** quy tắc tính toán và bất biến nghiệp vụ vẫn cần sống trong logic miền.
3. **Cho rằng after returning chạy cả khi lỗi:** nó chỉ chạy khi có kết thúc bình thường.
4. **Cho rằng after throwing tự xử lý hết ngoại lệ:** ghi lỗi khác với quyết định nuốt, đổi hoặc ném tiếp.
5. **Dùng around cho mọi việc:** nếu chỉ cần quan sát trước/sau, hãy ưu tiên hành vi đơn giản ít quyền can thiệp.
6. **Quên `proceed` hoặc gọi nhiều lần vô ý:** làm mất hoặc nhân đôi tác dụng phụ.
7. **Mặc định advice được chạy ở mọi đường gọi:** phải kiểm tra proxy, weaving, loại join point và self-invocation.
8. **Không quy định thứ tự nhiều aspect:** có thể audit thiếu sự kiện từ chối hoặc đo sai phạm vi.
9. **Để lỗi quan sát che lỗi nghiệp vụ:** cần thiết kế cách xử lý lỗi trong advice.
10. **Ghi log dữ liệu nhạy cảm và giữ state theo lời gọi trong field dùng chung:** dễ rò rỉ thông tin hoặc gặp race condition.

## 11. Câu hỏi tự kiểm tra

**Q1.** Pointcut chỉ khớp `pay()`, vậy `health()` có kích hoạt advice không?  
**Đáp án:** Không, nếu `health()` không được bộ chọn chấp nhận.

**Q2.** `pay()` ném `IllegalArgumentException`. Nhánh nào có thể chạy?  
**Đáp án:** `after throwing` quan sát ngoại lệ; phần `finally` trong phạm vi bao vẫn chạy. `after returning` không có kết quả bình thường để quan sát.

**Q3.** Vì sao around có thể gây lỗi nghiêm trọng?  
**Đáp án:** Nó quyết định có chuyển tiếp không, bao nhiêu lần chuyển tiếp và trả gì cho caller; nếu không kiểm soát, nó có thể nhân đôi thao tác hoặc che ngoại lệ.

**Q4.** Một Future vừa được trả về đã đủ kết luận tác vụ thành công chưa?  
**Đáp án:** Chưa. Hoàn tất phương thức trả Future khác với hoàn tất công việc bất đồng bộ bên trong.

**Q5.** Vì sao kiểm thử riêng logic advice chưa đủ?  
**Đáp án:** Vì cần xác minh cả chọn join point, đường đi qua proxy/weaving, thứ tự thực thi và hợp đồng đầu ra.

## 12. Kết luận

**Advice là hành vi được thực thi tại một join point đã được chọn**, còn pointcut xác định tập điểm mà chính sách cần áp dụng. Phân biệt `before`, `after returning`, `after throwing`, `after/finally` và `around` giúp dự đoán chính xác điều xảy ra khi target thành công, thất bại hoặc được bỏ qua. Khi thiết kế advice, phải nêu rõ **điểm được chọn, ngữ cảnh quan sát, thời điểm thực thi, quyền chuyển tiếp, thứ tự với các advice khác và hợp đồng trả về/lỗi**. Nhờ vậy hành vi cắt ngang được tái sử dụng mà nghiệp vụ vẫn rõ ràng và kiểm thử được.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-component-model-005">Weaving là quá trình kết hợp aspect với chương trình</a>

<details>
<summary>Xem chi tiết</summary>

# Weaving: quá trình kết hợp aspect với chương trình trong AOP

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, **AOP**), ta muốn mô tả một số hành vi chung — ví dụ đo thời lượng, kiểm toán, truy vết hay kiểm tra chính sách — một lần và áp dụng chúng tại những điểm xác định của chương trình. **Weaving** là quá trình **kết hợp hành vi được mô tả trong aspect với mã chương trình đích**, tạo ra cách thực thi mà tại các điểm phù hợp, hành vi bổ sung và hành vi chính chạy theo quy tắc đã định.

Cách hiểu quan trọng: định nghĩa một aspect và chọn vị trí áp dụng mới là *ý định*. Weaving là khâu biến ý định ấy thành sự kết hợp **có hiệu lực khi chương trình thực thi**. Tùy cơ chế, quá trình đó diễn ra lúc biên dịch, sau biên dịch, khi tải lớp hoặc lúc chạy. Không có một thời điểm weaving, loại điểm can thiệp hay cách thay đổi mã duy nhất cho mọi công nghệ AOP.

## 1. Mục tiêu bài học

Sau khi học, bạn có thể:

- Mô tả chính xác weaving nhận thông tin gì, xác định điểm áp dụng ra sao và tạo ra hành vi gì.
- Phân biệt **aspect**, **advice**, **join point**, **pointcut**, **target** và **weaver**, đồng thời giải thích mối liên hệ giữa chúng.
- Phân biệt **compile-time weaving**, **binary/post-compile weaving**, **load-time weaving** và các hình thức kết hợp ở thời gian chạy.
- Hiểu sự khác nhau giữa **biến đổi bytecode** và **bao bọc lời gọi qua proxy/interceptor**.
- Phân tích thứ tự các advice, tác động lên kết quả/ngoại lệ, phạm vi có thể bị can thiệp và các tình huống dễ bị bỏ sót.
- Kiểm tra việc weaving thực sự xảy ra, chọn cơ chế phù hợp và nhận diện nguy cơ khi áp chính sách tại nhiều điểm.

## 2. Mô hình trực giác: aspect được kết hợp vào đâu?

Xét ứng dụng bán vé có phương thức `bookTicket()`. Trách nhiệm cốt lõi của phương thức là kiểm tra ghế và tạo đặt chỗ. Những trách nhiệm như theo dõi độ trễ hoặc ghi sự kiện kỹ thuật có thể cần áp dụng cho nhiều thao tác khác nhau.

Nếu chèn thủ công cùng một đoạn đo thời gian ở hàng chục phương thức, mã theo dõi dễ bị **rải rác (scattering)**. Nếu một phương thức vừa giữ ghế, vừa ghi log, vừa định dạng thông tin truy vết, các trách nhiệm dễ **đan xen (tangling)**.

AOP cho phép diễn đạt một chính sách theo kiểu:

> Với những điểm thực thi thỏa điều kiện chọn, hãy thực hiện hành vi bổ sung trước, sau hoặc bao quanh hành vi chính.

Weaving hiện thực sự kết hợp đó.

```text
Mã/chương trình đích                   Định nghĩa aspect
- bookTicket()                         - Quy tắc chọn điểm áp dụng
- cancelTicket()                       - Hành vi đo thời lượng
- calculatePrice()                     - Cách xử lý thành công/lỗi
           \                            /
            \                          /
             +----- Bộ kết hợp (weaver)
                         |
                         v
               Chương trình có hiệu lực
               ┌─────────────────────────┐
Lời gọi ------>│ đo thời điểm bắt đầu     │
               │   bookTicket()          │
               │ đo kết thúc / ghi kết quả│
               └─────────────────────────┘
                         |
                         v
                  Kết quả hoặc lỗi
```

Sơ đồ là **mô hình khái niệm**. Một bộ weaving có thể biến đổi bytecode để tạo mã đã tích hợp; một hệ thống khác có thể kết hợp các bộ chặn lời gọi ở thời gian chạy. Với một số công nghệ, khái niệm weaving được dùng chặt cho biến đổi mã; với một số cách trình bày AOP tổng quát, nó bao gồm cả việc ghép các lớp can thiệp tại thời gian chạy. Cần nói rõ cơ chế đang xét.

### 2.1. Weaving không đồng nghĩa với “gọi thêm một hàm”

Lời gọi trực tiếp `logger.log(...)` trong từng phương thức là mã được lập trình viên đặt vào thủ công. Trong AOP, **pointcut xác định ở đâu** và **advice xác định phải làm gì**; weaving làm chúng tác động đến những điểm thực thi phù hợp. Công việc này có thể tạo bytecode mới, trang bị logic can thiệp khi lớp được định nghĩa hoặc lắp chuỗi lớp bao quanh.

Tuy nhiên, không phải mọi hành vi dùng chung đều cần weaving. Nếu nghiệp vụ chủ động gọi một bộ phận tính giá dùng chung, đó là tái sử dụng chức năng trực tiếp, không tự nhiên trở thành một aspect.

## 3. Các thành phần tham gia weaving

| Thành phần | Vai trò | Câu hỏi cần trả lời |
| --- | --- | --- |
| **Aspect** (khía cạnh) | Đóng gói quy tắc chọn điểm áp dụng và hành vi cắt ngang | Chính sách chung nào được mô tả? |
| **Join point** (điểm nối) | Một điểm trong luồng hoặc cấu trúc thực thi có thể được can thiệp theo cơ chế AOP | Có thể chèn hành vi ở những điểm nào? |
| **Pointcut** (điều kiện chọn điểm nối) | Chọn tập join point phù hợp, có thể kết hợp điều kiện tĩnh và động | Chính sách có hiệu lực ở đâu? |
| **Advice** (hành vi bổ sung) | Mã thực thi trước, sau hoặc bao quanh điểm được chọn | Tại điểm đó cần thực hiện gì? |
| **Target** (đích) | Thành phần cung cấp hành vi chính, ví dụ đối tượng có phương thức đặt vé | Hành vi nghiệp vụ nào được tác động? |
| **Weaver** (bộ kết hợp) | Cơ chế ghép pointcut/advice với điểm áp dụng của chương trình | Sự kết hợp được tạo ra như thế nào, khi nào? |
| **Woven artifact/runtime composition** (sản phẩm đã kết hợp hoặc cấu hình kết hợp khi chạy) | Kết quả sử dụng được sau weaving | Chương trình sẽ thực thi thêm những bước gì? |

Một cách nhớ: **join point là các địa điểm có thể ghé, pointcut là bộ lọc chọn địa điểm, advice là hành động tại địa điểm, aspect là nhóm chính sách, weaver là thành phần thực hiện kết hợp**.

Không nên tráo khái niệm:

- **Pointcut không phải advice.** Điều kiện chọn “mọi lần thực thi tác vụ xử lý đơn hàng” chưa mô tả hành vi cần chèn.
- **Join point không phải pointcut.** Join point là một điểm cụ thể trong mô hình thực thi; pointcut là quy tắc có thể khớp nhiều điểm.
- **Aspect không phải lúc nào cũng là một đối tượng được tạo một lần.** Chu kỳ sống/trạng thái của aspect tùy ngôn ngữ và cơ chế triển khai.
- **Weaver không phải phương thức nghiệp vụ.** Nó đóng vai trò kết hợp các mô tả riêng biệt; một số cơ chế tạo mã mới, một số dựng lớp trung gian.

### 3.1. Cơ chế phải xác định mô hình join point

Ví dụ các loại điểm can thiệp có thể được một ngôn ngữ hoặc công cụ AOP hỗ trợ gồm: thực thi phương thức, lời gọi phương thức, tạo đối tượng, truy cập trường, xử lý ngoại lệ hoặc các điểm điều khiển khác. **Không có cam kết tất cả cơ chế đều hỗ trợ tất cả loại này.**

Điểm **gọi phương thức** và điểm **thực thi thân phương thức** cũng không tương đương: một phương thức có thể được gọi từ nhiều nơi; lựa chọn phía người gọi hoặc phía thân thực thi tạo khả năng quan sát và phạm vi áp dụng khác nhau. Với cơ chế chỉ chặn lời gọi đi qua proxy, một số đường gọi nội bộ không đi qua proxy sẽ không được can thiệp.

## 4. Quá trình weaving hoạt động như thế nào?

Đây là một quy trình khái niệm, chi tiết thực tế có thể khác nhau giữa các công cụ.

1. **Thu thập đầu vào.** Bộ kết hợp nhận mã/lớp đích, định nghĩa aspect, pointcut, advice và thông tin cấu hình hay thứ tự ưu tiên cần thiết.
2. **Xác định join point được hỗ trợ.** Dựa trên mô hình của công nghệ, xác định các điểm có thể chèn hoặc bao bọc hành vi.
3. **Đánh giá điều kiện pointcut.** Các điều kiện tĩnh như tên, kiểu, chữ ký có thể được xác định sớm; điều kiện phụ thuộc dữ liệu thực thi có thể đòi hỏi kiểm tra thêm tại thời gian chạy.
4. **Lập kế hoạch kết hợp.** Quyết định advice nào áp ở điểm nào, cách truyền tham số, kết quả, ngoại lệ và thứ tự giữa nhiều advice.
5. **Thực hiện kết hợp.** Biến đổi mã/bytecode, thiết lập bộ biến đổi lúc tải lớp hoặc tạo chuỗi proxy/interceptor tùy phương án.
6. **Thực thi và quan sát.** Lời gọi thực tế đi qua logic được kết hợp; kiểm tra việc chạy đúng lúc, đúng thứ tự, đúng số lần và giữ hợp đồng của tác vụ chính.

```text
Aspect + Pointcut + Advice        Chương trình đích
            |                            |
            +------------+---------------+
                         |
                    Chọn join point
                         |
                    Ghép hành vi
                         |
        +----------------+----------------+
        |                                 |
  Tạo/sửa mã lớp                     Tạo chuỗi can thiệp
  hoặc bytecode                      tại thời gian chạy
        |                                 |
        +----------------+----------------+
                         |
                   Chạy chương trình
                         |
                Quan sát kết quả/lỗi
```

Điểm chọn **không nhất thiết được quyết định hoàn toàn trước khi chạy**: kiểu chữ ký có thể khớp ngay khi kết hợp mã, còn điều kiện dựa trên giá trị đối số hoặc trạng thái động có thể phải kiểm tra mỗi lần thực thi. Không nên suy luận từ việc “khớp pointcut khi cấu hình” rằng advice chắc chắn chạy trên mọi lời gọi.

## 5. Thời điểm weaving: bốn nhóm thường gặp

Việc phân nhóm giúp chọn giải pháp và dự đoán khả năng quan sát. Tên gọi cụ thể đôi khi khác nhau giữa công nghệ.

| Phương án | Thời điểm | Tác động điển hình | Điều cần đánh đổi |
| --- | --- | --- | --- |
| **Compile-time weaving (CTW)** | Khi biên dịch từ nguồn | Sinh mã hoặc bytecode đã tích hợp hành vi khía cạnh | Cần tích hợp công cụ vào quy trình build, phải quản lý nguồn và bản sinh |
| **Post-compile/binary weaving** | Sau khi đã có bytecode, trước khi chạy hoặc đóng gói | Biến đổi lớp đã biên dịch thành lớp mới | Cần kiểm soát phiên bản và tính tương thích của sản phẩm |
| **Load-time weaving (LTW)** | Khi JVM chuẩn bị định nghĩa một lớp | Chuyển đổi bytecode trong đường tải lớp | Cần cấu hình cơ chế tải/biến đổi và kiểm soát phạm vi lớp |
| **Kết hợp ở thời gian chạy** | Khi tạo đối tượng, dựng proxy/chuỗi bộ chặn hoặc một số dạng instrumentation | Hành vi đi qua lớp trung gian hoặc mã được can thiệp động | Phụ thuộc đường gọi, kiểu đối tượng, khả năng biến đổi và trạng thái đang chạy |

### 5.1. Compile-time weaving

Ở thời điểm biên dịch, bộ kết hợp có thể đọc mô tả aspect và mã nguồn để tạo ra sản phẩm đã mang hành vi bổ sung. Sản phẩm chạy sau đó **không nhất thiết cần một bước tìm điểm áp dụng tương tự tại mỗi lần gọi**; một số điều kiện động vẫn có thể được kiểm tra khi chạy.

Ưu điểm: kết quả có thể được kiểm tra như một sản phẩm build, dễ ghim phiên bản, và không phải thay đổi lớp tại thời điểm tải.

Giới hạn: quy trình build phức tạp hơn; có thể phải dùng bộ biên dịch hoặc bước xử lý đặc biệt. Sửa aspect hoặc pointcut có thể cần build lại. Nếu mã sinh khác với mã nguồn, debugger hoặc stack trace có thể chứa vị trí/khung lời gọi bổ sung.

**Không kết luận compile-time weaving luôn nhanh hơn mọi cách khác.** Chi phí còn phụ thuộc loại advice, độ rộng pointcut, tối ưu của JVM và công việc bên trong advice.

### 5.2. Binary/post-compile weaving

Bộ kết hợp nhận bytecode đã tạo trước đó, chèn hoặc sửa các phần cần thiết và xuất một bản bytecode được kết hợp. Cách này hữu ích nếu có lớp đã biên dịch mà không muốn hoặc không thể biên dịch từ nguồn trong cùng bước.

Cần chú ý: phải bảo toàn khả năng nạp lớp, cấu trúc bytecode hợp lệ, tương thích phiên bản class file, cơ chế xác minh bytecode, việc đóng gói và nguồn gốc artifact. Nếu lớp bị biến đổi nhiều lần ngoài ý muốn, advice có thể bị áp trùng hoặc bản sinh không còn nhất quán.

### 5.3. Load-time weaving

Ở Java, cơ chế gắn với quá trình tải lớp có thể nhận bytecode **trước khi lớp được định nghĩa** và biến đổi nội dung theo quy tắc. Ví dụ, một bộ biến đổi lớp có thể tham gia chuỗi nạp lớp, nhưng mỗi công cụ lại quyết định những lớp nào được phép sửa và bằng cách nào.

Các điểm cần hiểu:

- Thứ tự đăng ký nhiều bộ biến đổi có thể ảnh hưởng kết quả cuối; một biến đổi cần tương thích với biến đổi tiếp theo.
- Lớp đã nạp trước khi cấu hình có hiệu lực thường không tự nhiên được xử lý như lớp sắp nạp.
- Khả năng tác động vào lớp thư viện, lớp thuộc module khác hoặc lớp đặc biệt phụ thuộc quyền, cấu hình và giới hạn của môi trường.
- Công cụ kiểm thử hoặc môi trường đóng gói có thể có đường nạp lớp khác môi trường chạy thật; phải kiểm chứng trong cấu hình thực tế.

Load-time weaving là một cách **thực hiện kết hợp tại giai đoạn nạp lớp**. Nó không đồng nghĩa với mọi tình huống “AOP chạy động”.

### 5.4. Kết hợp ở thời gian chạy: proxy, interceptor và instrumentation

Một hệ thống có thể tạo **proxy** hoặc chuỗi **interceptor** bao quanh đối tượng đích. Người gọi truy cập qua đại diện; đại diện thực hiện advice rồi chuyển tiếp lời gọi. Cách này dễ hình dung và linh hoạt theo cấu hình đối tượng, nhưng nó chỉ can thiệp những lời gọi **thực sự đi qua đại diện**.

Một nhánh công nghệ khác có thể can thiệp mã lúc chạy bằng khả năng instrumentation/biến đổi lớp. Việc biến đổi lại một lớp đã định nghĩa chịu những ràng buộc của máy ảo và công cụ; không được giả định có thể thay đổi tùy ý cấu trúc lớp hoặc mọi điểm nối khi chương trình đang chạy.

Vì vậy:

- **Proxy không phải sửa trực tiếp bytecode của lớp đích**; nó chủ yếu thay đổi đường đi của lời gọi.
- **Instrumentation không tự động là một hệ AOP hoàn chỉnh**; vẫn phải có mô hình chọn join point, advice, thứ tự và ngữ nghĩa kết hợp.
- **Weaving không đồng nghĩa với chạy lại**; kết hợp một lần và gọi nhiều lần là hai hoạt động khác nhau.

### 5.5. Sơ đồ thời điểm

```text
Mã nguồn
   |
   |  [Compile-time weaving có thể diễn ra]
   v
Bytecode ban đầu
   |
   |  [Post-compile/binary weaving có thể diễn ra]
   v
Bytecode để đóng gói/chạy
   |
   |  [Load-time weaving có thể diễn ra trước khi định nghĩa lớp]
   v
Lớp được JVM nạp và định nghĩa
   |
   |  [Có thể dựng proxy/interceptor; một số cơ chế hỗ trợ can thiệp động]
   v
Lời gọi thực thi qua đường đã kết hợp
```

Đây là **các vị trí có thể lựa chọn**, không phải chuỗi bước bắt buộc phải sử dụng hết. Một hệ thống có thể dùng một phương án hoặc phối hợp một số phương án nếu chúng tương thích và thực sự cần thiết.

## 6. Weaving làm thay đổi đường thực thi ra sao?

Giả sử một điểm có `before`, `after returning`, `after throwing`, `finally` và `around` theo những ngữ nghĩa mà công cụ hỗ trợ.

### 6.1. Hành vi trước/sau

- **Before:** chạy trước tác vụ đích; nếu chặn bằng lỗi hoặc quyết định đặc biệt, đích có thể không được thực thi.
- **After returning:** chạy khi đích hoàn tất theo đường thành công; “return” không bao gồm ngoại lệ.
- **After throwing:** chạy theo đường lỗi; không mặc nhiên chạy khi thành công.
- **After/finally:** thường nhằm xử lý cuối cùng dù thành công hay thất bại; phải xem ngữ nghĩa của công cụ cụ thể.
- **Around:** bao quanh điểm được chọn và có thể điều phối việc chuyển tiếp vào thao tác đích; mạnh nhất và có nguy cơ thay đổi hành vi chính lớn nhất.

Khái niệm **proceed** thường biểu đạt bước “tiếp tục chuỗi thực thi bên trong” của around advice. Khi chính sách chỉ nhằm ghi log hoặc đo thời gian, lựa chọn an toàn thường là chuyển tiếp **đúng một lần** và bảo toàn kết quả/ngoại lệ. Một số cơ chế cho phép bỏ qua, thay đối số, gọi nhiều lần hoặc thay kết quả; đây là năng lực phải dùng có chủ đích, không phải mặc định vô hại.

### 6.2. Thứ tự các khía cạnh

Hai khía cạnh A và B cùng bao quanh công việc T có thể kết hợp như sau:

```text
A(
   B(
      T()
   )
)
```

Nếu cả A và B đều thực hiện một hành vi trước, một hành vi sau khi thành công và một hành vi cuối cùng, thứ tự trực giác là:

```text
A before
B before
T
B after returning
B finally
A after returning
A finally
```

Đây là thứ tự **theo cấu trúc lồng A(B(T))**, không phải một quy tắc ưu tiên mặc định cho mọi công nghệ. Nếu đổi thứ tự kết hợp thành B(A(T)), đường thực thi cũng đổi.

Trường hợp có ngoại lệ, thao tác “sau thành công” không chạy. Hành vi “sau lỗi” và “finally” chỉ đúng theo hợp đồng của từng advice; mã người dùng bên trong advice cũng có thể ném lỗi mới. Khi có nhiều aspect, cần xác định rõ thứ tự và chủ ý muốn điều khiển lỗi như thế nào.

### 6.3. Điểm cần giữ nguyên trong hợp đồng chương trình

Một chính sách theo dõi thuần túy không nên vô tình:

- Thay kết quả `return` của nghiệp vụ.
- Nuốt ngoại lệ, đổi lỗi thành kết quả thành công hoặc tạo lỗi mới chỉ do format log.
- Gọi công việc chính thêm lần nữa.
- Chỉnh sửa dữ liệu có thể quan sát được mà không được cho phép.
- Đưa thông tin bí mật trong tham số hoặc kết quả vào bản ghi.

Weaving tăng khả năng tái sử dụng nhưng **không bãi bỏ hợp đồng** giữa người gọi và thành phần nghiệp vụ.

## 7. Ví dụ Java 17: kết hợp hai hành vi quanh một phương thức

Ví dụ sử dụng **JDK dynamic proxy** để tạo hai lớp bao quanh cùng một đối tượng. Mục tiêu là thấy rõ **thứ tự ghép** và hiệu quả trên đường thành công/lỗi. Proxy là một hình thức kết hợp lời gọi **ở thời gian chạy**, không minh họa việc sửa bytecode; nó cũng không đại diện cho mọi join point mà các bộ weaving khác có thể hỗ trợ.

Lưu chương trình dưới tên `WeavingDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;

public class WeavingDemo {
    interface Task {
        String run(String input);
    }

    static final class CoreTask implements Task {
        @Override
        public String run(String input) {
            if (input == null || input.isBlank()) {
                throw new IllegalArgumentException("Dữ liệu rỗng");
            }
            return input.trim().toUpperCase(Locale.ROOT);
        }
    }

    private static Object invokeTarget(
            Method method, Object target, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static Task weave(String name, Task target) {
        return (Task) Proxy.newProxyInstance(
            Task.class.getClassLoader(),
            new Class<?>[] { Task.class },
            (proxy, method, arguments) -> {
                if (!method.getName().equals("run")) {
                    return invokeTarget(method, target, arguments);
                }

                System.out.println(name + " before");
                try {
                    Object result = invokeTarget(method, target, arguments);
                    System.out.println(name + " after returning");
                    return result;
                } catch (Throwable ex) {
                    System.out.println(name + " after throwing");
                    throw ex;
                } finally {
                    System.out.println(name + " finally");
                }
            }
        );
    }

    public static void main(String[] args) {
        Task task = weave("A", weave("B", new CoreTask()));

        System.out.println("Kết quả: " + task.run("  aop  "));

        try {
            task.run(" ");
        } catch (IllegalArgumentException ex) {
            System.out.println("Bắt lỗi: " + ex.getMessage());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac WeavingDemo.java
java WeavingDemo
```

Kết quả dự kiến chính xác:

```text
A before
B before
B after returning
B finally
A after returning
A finally
Kết quả: AOP
A before
B before
B after throwing
B finally
A after throwing
A finally
Bắt lỗi: Dữ liệu rỗng
```

**Giải thích:**

1. `CoreTask` là phần nghiệp vụ cốt lõi: từ chối chuỗi trắng; với chuỗi hợp lệ, cắt khoảng trắng ở hai đầu và chuyển thành chữ hoa.
2. `weave("B", new CoreTask())` tạo đại diện B quanh đích. `weave("A", ...)` tiếp tục đặt A bên ngoài B. Vì vậy lời gọi chạy theo A → B → đích, và các hành vi sau đó quay từ B ra A.
3. `method.getName().equals("run")` là **bộ lọc minh họa** vị trí can thiệp. Nó đủ cho ví dụ một phương thức `run`; một bộ pointcut thực tế có thể xét chữ ký, kiểu đích, annotation, điều kiện động và nhiều tiêu chí khác.
4. `invokeTarget` bóc `InvocationTargetException` do reflection tạo ra để chuyển tiếp nguyên nhân thật. Vì vậy `IllegalArgumentException` gốc đi ra qua B rồi A và đến người gọi.
5. Mọi proxy chỉ can thiệp **lời gọi được thực hiện qua proxy**. Gọi thẳng `new CoreTask().run(...)` sẽ không chạy A/B. Một lời gọi nội bộ trên `this` của đối tượng đích cũng không tự động vòng qua chuỗi proxy.
6. Trong mã minh họa, `Throwable` được bắt để ghi nhánh lỗi rồi ném lại; điều này giúp quan sát lỗi nhưng **không có nghĩa mọi lỗi đều nên được xử lý, phục hồi hoặc ghi đầy đủ nội dung** trong chương trình thật.

Các điểm giới hạn của ví dụ:

- JDK proxy này dựa trên **giao diện** `Task`, chặn lời gọi phương thức qua proxy; nó không tự chặn mọi `private`, `static`, constructor hay truy cập trường của `CoreTask`.
- Ví dụ chỉ chọn tên `run`, chưa phải ngôn ngữ pointcut đầy đủ.
- Nối A/B bằng lời gọi `weave` là cấu hình tường minh; một hệ thống AOP có thể tính toán cấu hình và thứ tự từ quy tắc khác.
- Hàm `invokeTarget` chỉ bóc ngoại lệ bọc do reflection. Nếu một cơ chế proxy khác phải chuyển ngoại lệ được kiểm tra (checked exception) không nằm trong khai báo phương thức, hành vi chuyển tiếp có thể chịu giới hạn của hợp đồng giao diện.
- Log vào `System.out` nhằm minh họa thứ tự; một hệ thống quan sát thật cần cân nhắc độ trễ, độ tin cậy và dữ liệu nhạy cảm.

## 8. Sự khác biệt giữa biến đổi bytecode và proxy

Đây là phần đặc biệt quan trọng khi giải thích cơ chế weaving.

| Câu hỏi | Biến đổi mã/bytecode | Proxy/interceptor |
| --- | --- | --- |
| Đơn vị được thay đổi | Nội dung mã hoặc định nghĩa lớp trong phạm vi hỗ trợ | Đường lời gọi qua một đối tượng đại diện/chuỗi bộ chặn |
| Lời gọi trực tiếp lên đích | Có thể thấy hành vi đã ghép nếu điểm tương ứng thực sự được biến đổi | Bỏ qua proxy thì thường bỏ qua logic của proxy |
| Lời gọi nội bộ từ `this` | Có thể can thiệp nếu join point nằm trong mã đã biến đổi và được hỗ trợ | Thường không đi qua proxy ngoài |
| Constructor/trường | Phụ thuộc công cụ và loại join point hỗ trợ | JDK dynamic proxy thông thường không chặn các điểm này của lớp đích |
| Thời điểm thiết lập | Khi biên dịch, sau biên dịch, lúc tải hoặc đôi khi lúc chạy | Khi tạo proxy/chuỗi và đưa đại diện vào đường sử dụng |
| Chi phí, giới hạn | Biến đổi/kiểm chứng bytecode, tương thích JVM, ánh xạ debug | Chi phí lớp trung gian, định tuyến lời gọi, danh tính proxy, phạm vi giao diện/kiểu |
| Cách kiểm chứng | Kiểm tra artifact/lớp đã biến đổi và hành vi khi chạy | Đảm bảo người gọi nhận đúng proxy và quan sát lời gọi đi qua nó |

Vì vậy không thể dựa vào từ “weaving” rồi kết luận “mọi lời gọi phương thức đều có advice”. Khả năng thực tế phụ thuộc **loại join point, cơ chế kết hợp và đường thực thi**.

### 8.1. Proxy và danh tính đối tượng

Một proxy có thể khác đối tượng đích về `getClass()`, việc so sánh tham chiếu `==`, phương thức `equals`, `hashCode` hoặc `toString`, tùy thiết kế. Chương trình phụ thuộc cụ thể vào danh tính/kiểu của đối tượng phải cân nhắc trước khi đưa proxy vào. Không nên dùng tiêu chí tên phương thức đơn giản để áp chính sách lên các phương thức nền tảng đó một cách vô ý.

### 8.2. Class loading và module

Một bộ biến đổi bytecode trong JVM phải tuân thủ định dạng class file, kiểm chứng kiểu, quyền truy cập và giới hạn của cơ chế biến đổi. Kể cả khi aspect khớp pointcut, môi trường chạy vẫn có thể không cho truy cập hoặc sửa một lớp nhất định. Trong hệ thống module hóa, quyền truy cập/khả năng mở gói cũng có thể ảnh hưởng instrumentation và reflection.

Khả năng **retransform/redefine** một lớp đã được nạp, khi được cung cấp, cũng có giới hạn riêng; không thể suy ra rằng mọi thay đổi cấu trúc lớp đều hợp lệ. Mỗi cơ chế phải được kiểm tra bằng tài liệu và thực nghiệm của công cụ cụ thể.

## 9. Weaving nâng cao: nhiều khía cạnh, điều kiện động và tính đúng đắn

### 9.1. Xung đột và tính kết hợp

Giả sử cùng một thao tác được bao bởi kiểm soát quyền, mở giao dịch, kiểm toán và đo thời gian. Nếu kiểm tra quyền được đặt **bên trong** bước tạo giao dịch, có thể tạo giao dịch cho yêu cầu không hợp lệ; nếu đo thời gian **bên ngoài** một chuỗi thử lại, số đo bao gồm mọi lần thử; nếu đo thời gian **bên trong**, mỗi lần thử có số đo riêng.

Các chính sách không phải lúc nào giao hoán:

```text
Timing(Retry(Operation))   -> đo toàn bộ vòng thử lại
Retry(Timing(Operation))   -> đo riêng từng lần thử
```

Thứ tự tương tự ảnh hưởng tới việc ghi sự kiện kiểm toán trước hay sau khi giao dịch hoàn tất. Cần quy định **thứ tự precedence**, đơn vị công việc, những nhánh lỗi và thời điểm xem kết quả là thành công.

### 9.2. Pointcut động và phạm vi điều khiển

Một điều kiện dựa trên chữ ký có thể được xác định sớm; điều kiện phụ thuộc đối số, trạng thái đối tượng hoặc đường thực thi có thể được kiểm tra khi chạy. Các bộ lọc động mở rộng tính biểu đạt nhưng có thể thêm chi phí tại mỗi lời gọi và làm việc suy luận khó hơn.

Điều kiện điểm áp dụng nên rõ, có phạm vi hẹp và dùng dữ liệu tối thiểu. Tên phương thức phù hợp không đảm bảo chính sách đúng với toàn bộ phiên bản hoặc ngữ nghĩa nghiệp vụ của phương thức.

### 9.3. Aspect có trạng thái và đồng thời

Một bộ theo dõi có trạng thái dùng chung có thể bị nhiều luồng gọi cùng lúc. Nếu advice ghi biến đếm, tổng thời gian hoặc bối cảnh của “yêu cầu hiện tại”, phải hiểu aspect được tạo theo phạm vi nào và dùng cơ chế đồng bộ/phân tách trạng thái tương ứng.

Không lưu định danh yêu cầu vào một biến instance dùng chung rồi mặc định nó thuộc về đúng lời gọi. Với tác vụ bất đồng bộ, bối cảnh gắn luồng cũng có thể thất lạc khi công việc chuyển luồng. Weaving chỉ xác định hành vi được áp dụng ở những điểm phù hợp; **nó không tự giải quyết bài toán truyền bối cảnh và an toàn đồng thời**.

### 9.4. Idempotency và advice quanh thao tác có tác dụng phụ

Một around advice có thể được thiết kế để gọi lại thao tác khi gặp lỗi tạm thời. Nhưng nếu đích đã gửi một lệnh thanh toán rồi lỗi khi nhận phản hồi, gọi lại có thể tạo giao dịch trùng. Cơ chế retry là trách nhiệm chung; **quyết định thao tác có an toàn để retry hay không** là hợp đồng nghiệp vụ/kết nối bên ngoài.

Không thiết kế advice thử lại chỉ theo loại lỗi mà bỏ qua khả năng tác dụng phụ đã xảy ra. Khi cần, sử dụng mã idempotency, phát hiện trùng, xác định ranh giới giao dịch và các trạng thái không chắc chắn.

### 9.5. Vấn đề weave trùng hoặc không weave

Hai lỗi ngược nhau thường xảy ra:

- **Không áp dụng:** pointcut quá hẹp, lớp đã được nạp, đường gọi đi thẳng tới target hoặc cơ chế không hỗ trợ loại join point.
- **Áp dụng lặp:** hai cơ chế cùng chặn một điểm, chạy qua nhiều proxy, hoặc artifact đã được biến đổi rồi bị biến đổi thêm lần nữa ngoài dự kiến.

Kết quả có thể là thiếu kiểm toán, ghi log hai lần, đo sai thời gian hoặc lặp tác dụng phụ. Muốn kiểm chứng, cần xem **số lần advice chạy trên một lời gọi thực tế**, không chỉ kiểm tra một dòng cấu hình có tồn tại.

### 9.6. Bảo mật và hành vi ẩn

Một hệ thống không nên dựa duy nhất vào lớp can thiệp có thể bỏ qua để bảo vệ bất biến quan trọng. Nếu một đường gọi không qua proxy hoặc một lớp không được weaving, thao tác nhạy cảm vẫn có thể chạy. Cần đặt quy tắc bảo vệ ở ranh giới đáng tin cậy và kiểm thử mọi đường truy cập được hỗ trợ.

Ngoài ra, advice ghi log/kiểm toán không được tự ý ghi mật khẩu, khóa bí mật, token truy cập hoặc nội dung riêng tư. Một pointcut rộng áp khắp hệ thống có thể làm phạm vi thu thập dữ liệu trở nên rất lớn.

## 10. Kiểm thử và chẩn đoán khi weaving không chạy như mong đợi

Nên kiểm tra theo thứ tự sau:

1. **Điểm dự định có được hỗ trợ không?** Xác minh mô hình join point: thực thi phương thức, lời gọi, constructor, trường hay điểm khác.
2. **Pointcut có khớp điểm thực tế không?** Kiểm tra chữ ký, kiểu, cấu trúc kế thừa, điều kiện động và các quy tắc loại trừ.
3. **Weaver có tham gia đúng giai đoạn không?** Với compile-time/binary weaving, kiểm tra artifact được dùng; với load-time weaving, kiểm tra thời điểm nạp lớp; với proxy, kiểm tra đối tượng mà người gọi thực sự sử dụng.
4. **Các advice có chạy đúng thứ tự và số lần không?** Kiểm tra thành công, lỗi, gọi lồng, đa luồng và tác vụ bất đồng bộ.
5. **Kết quả có được bảo toàn không?** Đối chiếu giá trị trả về, ngoại lệ gốc, trạng thái và tác dụng phụ của tác vụ đích.
6. **Có ảnh hưởng vận hành không?** Đo thời lượng thực tế, lượng log sinh ra và ảnh hưởng của truy vết, đồng thời kiểm tra việc loại bỏ dữ liệu nhạy cảm.

Với các cơ chế biến đổi mã, debugger và stack trace có thể hiển thị khung lời gọi hoặc thông tin nguồn bổ sung. Với proxy, cần biết chuỗi đại diện thực sự nhận lời gọi. Việc đọc một phương thức nghiệp vụ **không đủ để biết toàn bộ hành vi**, vì advice có thể được định nghĩa ở nơi khác.

### 10.1. Một ma trận kiểm thử nhỏ nhưng có ý nghĩa

| Tình huống | Kỳ vọng cần kiểm tra |
| --- | --- |
| Lời gọi thuộc pointcut, thành công | Advice chạy đúng số lần, đúng thứ tự, kết quả không đổi |
| Lời gọi thuộc pointcut, đích ném lỗi | Nhánh lỗi/finally đúng, ngoại lệ không bị nuốt |
| Lời gọi ngoài pointcut | Không có advice ngoài phạm vi |
| Lời gọi nội bộ hoặc đường bypass | Ghi nhận đúng giới hạn của cơ chế đang dùng |
| Hai aspect cùng áp dụng | Thứ tự kết hợp khớp quy tắc định trước |
| Lời gọi đồng thời/bất đồng bộ | Bối cảnh, dữ liệu đo đạc không lẫn nhau |
| Khởi động/load lại/artifact mới | Phương thức ghép còn hiệu lực và không bị lặp |

Kiểm thử chỉ chạy trực tiếp lớp đích thường **không chứng minh** một cơ chế proxy đã được ghép đúng. Tương tự, kiểm thử chỉ nhìn vào cấu hình chưa chứng minh mã đã biến đổi chính xác. Hãy kiểm tra ở ranh giới sử dụng thực tế.

## 11. Khi nào lựa chọn mỗi phương án kết hợp?

Hãy ra quyết định dựa trên những câu hỏi cụ thể:

- **Phạm vi điểm can thiệp:** chỉ cần chặn những lời gọi qua giao diện, hay cần thao tác bên trong lớp như constructor/truy cập trường?
- **Quyền kiểm soát mã:** có thể biên dịch lại nguồn không, hay chỉ có artifact đã biên dịch?
- **Thời điểm thay đổi chính sách:** có thể đóng gói lại và triển khai lại, hay cần điều chỉnh động?
- **Môi trường chạy:** có thể cấu hình đường tải lớp hoặc instrumentation không? Các giới hạn module và quyền truy cập là gì?
- **Khả năng quan sát:** người bảo trì có tra được điều kiện áp dụng, thứ tự và nơi chèn hành vi không?
- **Bảo toàn hợp đồng:** thao tác có tác dụng phụ, ranh giới giao dịch, ngoại lệ, đồng thời và an toàn dữ liệu đã được xem xét chưa?

**Ví dụ định hướng:**

- Một công cụ phân tích tĩnh có thể dùng compile-time/binary weaving khi muốn sản phẩm build tự chứa cơ chế theo dõi.
- Một môi trường cần can thiệp khi lớp được nạp có thể cân nhắc load-time weaving, với điều kiện cấu hình và kiểm chứng lớp được kiểm soát.
- Một ứng dụng có giao diện rõ và chỉ cần chặn lời gọi thông qua đối tượng đại diện có thể dùng proxy/interceptor, khi mọi nơi sử dụng đều có thể được cung cấp đúng đối tượng đại diện.

Đó là **tiêu chí lựa chọn**, không phải bảo đảm mỗi phương án có sẵn trong mọi nền tảng.

## 12. Sai lầm thường gặp

| Hiểu sai | Sự thật cần nhớ |
| --- | --- |
| “Viết aspect là chương trình tự động chạy advice.” | Cần cơ chế kết hợp đang hoạt động và điểm được chọn thực sự khớp. |
| “Weaving chỉ xảy ra khi biên dịch.” | Có nhiều thời điểm kết hợp, gồm sau biên dịch, khi nạp lớp hoặc qua trung gian ở thời gian chạy. |
| “Proxy sẽ can thiệp mọi lời gọi tới một lớp.” | Chỉ các đường gọi đi qua proxy, trong phạm vi phương thức được hỗ trợ, mới bị chặn. |
| “Bất cứ join point nào cũng được mọi weaver hỗ trợ.” | Mỗi công cụ có mô hình điểm can thiệp riêng. |
| “Advice trước/sau không thể đổi hành vi chính.” | Advice có thể gây lỗi, thay đổi trạng thái; around còn có khả năng ảnh hưởng luồng tiếp tục/kết quả. |
| “Hai aspect cùng áp dụng thì thứ tự không quan trọng.” | Thứ tự quyết định giao dịch, quyền, đo thời gian, kiểm toán và retry. |
| “Khớp pointcut là đã kiểm chứng weaving.” | Cần xác nhận artifact/đường gọi và hành vi thực tế, gồm cả nhánh lỗi. |
| “Weaving càng rộng càng dễ bảo trì.” | Phạm vi quá rộng tăng hành vi ẩn, chi phí và rủi ro áp sai. |

## 13. Bài tập áp dụng

### Bài tập 1 — Xác định thành phần

Cho quy tắc: “Tại mọi lần thực thi chức năng xử lý đơn hàng, ghi thời điểm bắt đầu và kết thúc, kể cả khi có lỗi”.

Hãy xác định: đâu là điểm nối khả dĩ, đâu là pointcut, đâu là advice, đâu là đích và bộ weaving phải tạo ra điều gì?

**Gợi ý trả lời:** Join point khả dĩ là điểm thực thi các phương thức xử lý đơn hàng *nếu công nghệ hỗ trợ loại điểm này*; pointcut lọc đúng nhóm phương thức; advice thực hiện đo và ghi thời điểm; đích là các phương thức nghiệp vụ; weaving ghép hành vi đó vào những điểm khớp sao cho đường thành công/lỗi đều được theo dõi. Thời điểm và kỹ thuật kết hợp phụ thuộc cơ chế được chọn.

### Bài tập 2 — Kiểm tra thứ tự

Cho cấu trúc `Audit(Timeout(Operation))`, trong đó mỗi lớp bao quanh ghi “before” và “finally”. Hãy ghi thứ tự khi tác vụ kết thúc bình thường, rồi so với `Timeout(Audit(Operation))`.

**Gợi ý trả lời:** Với cấu trúc thứ nhất: `Audit before → Timeout before → Operation → Timeout finally → Audit finally`. Với cấu trúc thứ hai: `Timeout before → Audit before → Operation → Audit finally → Timeout finally`. Hành vi timeout cụ thể có thể khác nhau theo công cụ; thứ tự này chỉ mô tả cách lồng hai lớp.

### Bài tập 3 — Chẩn đoán thiếu advice

Một ứng dụng khởi tạo `Task original = new CoreTask()`, sau đó tạo `Task wrapped = weave("Trace", original)`. Gọi `wrapped.run(...)` có log, còn `original.run(...)` không có. Có lỗi trong pointcut không?

**Gợi ý trả lời:** Chưa chắc. Nếu dùng cơ chế bọc lời gọi qua proxy như ví dụ, đây là hành vi đúng với phạm vi can thiệp. Lời gọi đến `original` bỏ qua proxy. Cần chỉnh đường cung cấp phụ thuộc hoặc chọn cơ chế có phạm vi can thiệp phù hợp nếu yêu cầu bắt buộc mọi đường gọi đều được theo dõi.

### Bài tập 4 — So sánh thời điểm

Bạn chỉ sở hữu các tệp `.class` đã biên dịch, nhưng cần áp một chính sách mới trước khi đưa ứng dụng vào vận hành. Bạn có thể dùng compile-time weaving từ nguồn đang có không? Phương án nào đáng xem xét và cần kiểm chứng gì?

**Gợi ý trả lời:** Nếu không có nguồn thì không thể mặc định thực hiện quy trình biên dịch từ nguồn theo kiểu thông thường. Có thể xem xét binary weaving hoặc load-time weaving nếu công cụ, quyền truy cập và bytecode cho phép. Phải kiểm chứng tính tương thích class file, danh sách lớp được can thiệp, hành vi/ngoại lệ và artifact thật sự được chạy.

### Bài tập 5 — Nhận diện ảnh hưởng nghiệp vụ

Một advice tự động retry `chargeCard()` ba lần nếu xảy ra timeout. Cơ chế weaving có bảo đảm đúng nghiệp vụ không?

**Gợi ý trả lời:** Không. Cơ chế chỉ áp chính sách retry tại điểm đã chọn. Timeout không chứng minh lần thanh toán trước chưa thành công; cần điều kiện retry an toàn, idempotency, mã yêu cầu và quy tắc xử lý trạng thái không chắc chắn. Đây là lý do việc kết hợp kỹ thuật phải tôn trọng hợp đồng nghiệp vụ.

## 14. Ghi nhớ

- **Weaving là quá trình kết hợp aspect với chương trình** để advice được thực thi tại những join point phù hợp với pointcut.
- Aspect mô tả chính sách; pointcut chọn nơi áp dụng; advice mô tả hành vi; **weaver biến mô tả đó thành sự kết hợp có hiệu lực**.
- **Thời điểm kết hợp** có thể là lúc biên dịch, sau biên dịch, khi tải lớp hoặc ở thời gian chạy. Các phương án khác nhau về khả năng can thiệp, đường gọi và công cụ cần thiết.
- **Proxy là bao bọc lời gọi; biến đổi bytecode là can thiệp mã lớp.** Không được suy luận khả năng của phương án này sang phương án kia.
- **Thứ tự, điều kiện động, nhánh lỗi, đồng thời và tác dụng phụ** quyết định weaving có giữ đúng hợp đồng chương trình hay không.
- Luôn kiểm chứng **điểm áp dụng thật, đường gọi thật và kết quả thực**, thay vì chỉ nhìn vào tên aspect hay cấu hình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-component-model-006">Chương trình gốc, bộ dệt và kết quả sau khi dệt</a>

<details>
<summary>Xem chi tiết</summary>

# Chương trình gốc, bộ dệt và kết quả sau khi dệt trong AOP

## 1. Ba thành phần tạo nên mô hình dệt aspect

Trong **Aspect-Oriented Programming (AOP)**, chương trình có thể được mô tả bằng hai nhóm quan tâm: hành vi chính của ứng dụng và các quy tắc xuyên cắt như quan sát thời gian chạy, kiểm toán, truy vết hay một số chính sách thực thi. **Weaving (dệt aspect)** là quá trình kết hợp các quy tắc xuyên cắt với những điểm thực thi được chọn, để hành vi cuối cùng bao gồm cả logic gốc lẫn phần được bổ sung.

Ba thành phần của mô hình cần hiểu là:

1. **Chương trình gốc (base program / target program):** những lớp, phương thức và luồng xử lý hiện có, trước khi áp dụng một bộ quy tắc aspect cụ thể.
2. **Bộ dệt (weaver):** cơ chế dùng định nghĩa aspect và quy tắc chọn điểm thực thi để gắn hành vi xuyên cắt vào đúng vị trí; cách gắn phụ thuộc thời điểm và công nghệ triển khai.
3. **Kết quả sau khi dệt (woven program / woven behavior):** chương trình hoặc cấu trúc thực thi có hành vi kết hợp. Khi dùng bytecode weaving, đó có thể là các tệp lớp đã biến đổi. Với interception bằng proxy, lớp gốc có thể không thay đổi trên đĩa, còn hành vi kết hợp tồn tại trong đường gọi qua proxy.

Công thức tư duy:

~~~text
Chương trình gốc ─────────────────────┐
                                      │
Định nghĩa aspect (pointcut + advice) ├──► Bộ dệt / cơ chế áp dụng
                                      │           │
Cấu hình, kiểu dữ liệu và môi trường ─┘           ▼
                                       Kết quả sau khi dệt
                                                  │
                                                  ▼
                                    Hành vi thực thi kết hợp
~~~

Hình trên là **mô hình khái niệm**. Không phải mọi cơ chế AOP đều chạy một bước biến đổi độc lập trước khi ứng dụng khởi động; có những cơ chế chèn hành vi khi tải lớp hoặc điều phối lời gọi ngay trong lúc chạy.

### Mục tiêu học tập

Sau bài này, bạn cần phân biệt được phần mã gốc với quy tắc aspect, giải thích cách bộ dệt xác định join point và gắn advice, dự đoán hành vi sau khi dệt, cũng như xác định được điều gì có thể quan sát trong tệp biên dịch và điều gì chỉ xuất hiện ở runtime.

## 2. Chương trình gốc: mô tả hành vi trước khi có aspect

**Chương trình gốc** là phần mã mang chức năng mà người dùng ứng dụng trực tiếp cần. Ví dụ một dịch vụ tính đơn giá:

~~~java
final class PriceCalculator {
    int total(int basePrice) {
        return basePrice + 20;
    }
}
~~~

Đây là **đoạn mã minh họa**, chưa phải chương trình độc lập có hàm main. Khi chưa áp dụng aspect, lời gọi **total(100)** trả về **120**; lớp chưa chứa thao tác ghi dấu bắt đầu hoặc kết thúc.

Cần hiểu đúng phạm vi của khái niệm “gốc”:

- **Ở mức source:** đó có thể là file Java được lập trình viên viết.
- **Ở mức mã biên dịch:** đó có thể là bytecode tạo ra từ Java trước bước dệt.
- **Ở mức một đối tượng đích:** đó có thể là instance thực thi nghiệp vụ mà một interceptor sẽ ủy quyền đến.

Vì vậy, câu nói “bộ dệt sửa chương trình gốc” cần được làm rõ. Có bộ dệt sửa bytecode; có cơ chế tạo đối tượng đại diện; có cơ chế thêm đường điều phối mà vẫn giữ nguyên file lớp gốc. Không thể suy ra hình dạng kết quả chỉ từ tên gọi “AOP”.

Chương trình gốc vẫn cần có trách nhiệm đúng, giao diện hợp lý và hành vi nghiệp vụ nhất quán. Weaving không tự sửa thiết kế miền sai hoặc bổ sung các bất biến bị thiếu.

## 3. Định nghĩa aspect là đầu vào thứ hai của quá trình dệt

Một aspect thường mô tả **điểm cần áp dụng** và **hành vi được áp dụng**. Các khái niệm liên quan:

| Khái niệm | Vai trò |
|---|---|
| **Join point** | Một điểm thực thi mà mô hình AOP hỗ trợ nhận diện: ví dụ lúc thực thi phương thức, tại lời gọi phương thức, hoặc khi truy cập trường dữ liệu tùy công nghệ. |
| **Pointcut** | Biểu thức hoặc quy tắc chọn những join point thuộc phạm vi cần xử lý. |
| **Advice** | Hành vi bổ sung chạy trước, sau, khi thất bại hoặc bao quanh join point. |
| **Aspect** | Đơn vị đóng gói pointcut, advice và có thể cả cấu hình, trạng thái hoặc các khai báo bổ sung. |
| **Target** | Lớp/đối tượng/mã thực thi gốc chịu tác động. |
| **Weaver** | Bộ xử lý kết hợp các quy tắc aspect với chương trình ở giai đoạn mà cơ chế hỗ trợ. |

Ví dụ **giả mã ngôn ngữ aspect trung lập với công cụ**, không phải cú pháp Java có thể đưa trực tiếp vào javac:

~~~text
aspect TracePrice
    pointcut pricedOperation =
        execution of PriceCalculator.total(int)

    before pricedOperation:
        print("trace: start")

    after-finally pricedOperation:
        print("trace: end")
end
~~~

Ý nghĩa của ví dụ là: chọn điểm **thực thi PriceCalculator.total(int)**; thực hiện một hành vi trước khi chạy mã gốc và một hành vi kết thúc bất kể đường ra thông thường hay ngoại lệ, theo quy tắc giả định. Nếu công nghệ được chọn không hỗ trợ loại join point hoặc advice đó, phải điều chỉnh cấu hình hoặc lựa chọn cơ chế khác.

Cần phân biệt:

- **Join point** là tập vị trí mà hệ thống *có khả năng* tác động.
- **Pointcut** chỉ chọn *một phần* trong tập đó.
- **Advice** quy định *làm gì* tại những vị trí đã chọn.
- **Weaver** quyết định *cách hiện thực* quy tắc trên mã hoặc đường gọi thực tế.

Pointcut không phải là câu lệnh trong thân hàm gốc; cũng không phải cứ một tên phương thức xuất hiện trong biểu thức là mọi lời gọi đến phương thức đó đều chắc chắn được chặn. Tính bao phủ phụ thuộc loại join point, quy tắc khớp và cơ chế dệt.

## 4. Bộ dệt thực hiện những công việc gì?

Dưới góc nhìn khái niệm, bộ dệt giải quyết năm vấn đề.

### 4.1. Nhận đầu vào và xây dựng ngữ cảnh

Bộ dệt cần biết những lớp/mã nào có thể tác động, định nghĩa aspect nào hoạt động và những quy tắc nào áp dụng. Tùy công cụ, đầu vào có thể là mã nguồn, các file đã biên dịch, thư viện trên classpath/module path, annotation, cấu hình tải lớp hoặc metadata về kiểu.

Ví dụ một pointcut chọn phương thức mang annotation sẽ cần cách đọc annotation thích hợp. Một pointcut chọn chữ ký phương thức cần thông tin về tên, tham số và kiểu. Các điều kiện phụ thuộc ngữ cảnh chạy có thể cần được kiểm tra muộn hơn so với giai đoạn đọc mã.

### 4.2. Tìm và kiểm tra các join point có thể áp dụng

Bộ dệt hoặc cơ chế interceptor xác định các điểm thực thi phù hợp với mô hình được hỗ trợ. Cần phân biệt:

- **Method execution:** điểm mã phương thức bắt đầu được thực thi.
- **Method call:** vị trí trong mã gọi một phương thức khác.
- **Field access:** đọc/ghi trường dữ liệu, nếu công nghệ hỗ trợ.
- **Các điểm khác:** khởi tạo đối tượng, constructor execution hoặc exception handler tùy mô hình và công nghệ.

Một lần gọi phương thức có thể liên quan cả **call join point** tại bên gọi và **execution join point** bên được gọi. Hai lựa chọn đó khác nhau về vị trí và số lượng điểm bị tác động. Công cụ chỉ hỗ trợ interception qua interface/proxy thường không quan sát được toàn bộ các điểm vừa kể.

### 4.3. Ghép advice vào đường thực thi

Khi một điểm khớp, bộ dệt chọn cách gắn **before**, **after**, **around** hoặc các loại advice được hỗ trợ. Một mẫu đơn giản có dạng:

~~~text
Caller
   │
   ▼
Advice trước
   │
   ▼
Hành vi gốc
   │
   ▼
Advice sau
   │
   ▼
Caller nhận kết quả
~~~

Sơ đồ này cố ý đơn giản: ngoại lệ, around advice, trả về sớm hoặc nhiều aspect có thể làm luồng thực tế phức tạp hơn. Với **around advice**, hành vi gốc không bắt buộc được gọi đúng một lần nếu công nghệ cho phép advice điều khiển **proceed**. Bỏ gọi proceed có thể bỏ qua hẳn công việc gốc; gọi nhiều lần có thể lặp tác dụng phụ. Đây là một năng lực mạnh đồng thời là rủi ro ngữ nghĩa.

### 4.4. Bảo toàn hợp đồng của lời gọi hoặc thay đổi có chủ đích

Weaving thường phải bảo toàn những yếu tố như chữ ký gọi được, kiểu trả về hợp lệ, khả năng lan truyền ngoại lệ và tính đúng đắn của mã biên dịch. Nhưng **bảo toàn chữ ký không đồng nghĩa bảo toàn hành vi**: aspect có thể thay giá trị trả về, ném lỗi mới, gây I/O, trì hoãn lời gọi hoặc từ chối gọi vào phương thức gốc.

Chính sách thiết kế phải nêu rõ điều gì được phép thay đổi. Ví dụ advice chỉ đo thời gian nên không âm thầm đổi giá trị trả về hoặc nuốt ngoại lệ. Advice thực hiện retry thì bắt buộc xét idempotency của hành vi gốc.

### 4.5. Tạo hiện vật đầu ra hoặc thiết lập cơ chế thực thi

Đầu ra có thể là:

- Bytecode đã dệt được lưu thành class/JAR.
- Bytecode được biến đổi khi lớp nạp vào JVM.
- Proxy/interceptor cùng một chuỗi gọi kết hợp tại runtime.

Do vậy, “kết quả sau khi dệt” cần được mô tả ở hai cấp: **hiện vật vật lý** (file/class/proxy/configuration) và **hành vi quan sát** (advice có chạy, chạy khi nào, có đổi kết quả hay không).

## 5. Từ chương trình gốc đến chương trình đã dệt: một ví dụ từng bước

Giả sử có lớp **PriceCalculator** ở phần 2, cùng quy tắc thực thi giả định:

- Trước mỗi lần thực thi **total(int)**, in **trace: start**.
- Khi phương thức kết thúc, kể cả khi ném ngoại lệ, in **trace: end**.
- Giữ nguyên phép tính giá và trả kết quả cho bên gọi.

### 5.1. Trước khi dệt

~~~java
final class PriceCalculator {
    int total(int basePrice) {
        return basePrice + 20;
    }
}
~~~

Luồng thực thi:

~~~text
Call total(100) → cộng 20 → trả về 120
~~~

### 5.2. Bộ dệt xử lý

Bộ dệt xác nhận có join point tương ứng với điểm thực thi **total(int)**, pointcut chọn được join point và advice được áp dụng theo trình tự đã định.

### 5.3. Mã sau dệt ở dạng mô phỏng dễ đọc

~~~java
// MÔ PHỎNG logic sau dệt; không phải mã nguồn chắc chắn được bộ dệt sinh ra.
final class PriceCalculatorWovenSketch {
    int total(int basePrice) {
        System.out.println("trace: start");
        try {
            return basePrice + 20;
        } finally {
            System.out.println("trace: end");
        }
    }
}
~~~

Khi gọi **total(100)**, hành vi mong đợi:

~~~text
trace: start
trace: end
~~~

Sau đó caller nhận giá trị **120**. Trong trường hợp thêm lệnh **System.out.println(result)** ở caller, dòng **120** mới xuất hiện trên stdout. Bản thân phương thức chỉ trả số nguyên, không tự in kết quả.

**Chú ý về phạm vi minh họa:** bộ dệt thật có thể chèn instruction, tạo method phụ, thay đổi call site, thêm wrapper hoặc dùng hình thức khác; không nhất thiết tạo đúng Java source như lớp minh họa. Nội dung có ý nghĩa ở đây là **hành vi thực thi**, không phải lời khẳng định về hình dạng bytecode.

### 5.4. Mã đầu ra có thể khác nhau nhưng quan sát giống nhau

Hai triển khai khác nhau có thể đều ghi dòng bắt đầu/kết thúc và trả về 120:

1. Biến đổi trực tiếp bytecode của **PriceCalculator**.
2. Giữ **PriceCalculator** nguyên vẹn, nhưng caller sử dụng một đối tượng bao ngoài hoặc proxy chặn lời gọi.

Kết quả quan sát ở đường gọi được chọn giống nhau, nhưng **khả năng bao phủ** khác nhau. Nếu caller giữ tham chiếu trực tiếp đến đối tượng gốc và gọi mà không đi qua proxy, phương án thứ hai có thể bỏ qua advice.

Đó là lý do không nên kết luận từ stdout rằng hệ thống đã thực hiện compile-time weaving.

## 6. Phân biệt thời điểm dệt và loại đầu ra

Ở đây câu hỏi chính không chỉ là *weaving xảy ra lúc nào* mà là **hiện vật nào được tạo ra và caller đang chạy bản nào**. Cùng một aspect có thể tạo kết quả vật lý khác nhau:

| Cơ chế | Đầu vào chính | Đầu ra cần kiểm chứng | Sai sót dễ bỏ qua |
| --- | --- | --- | --- |
| **Compile-time weaving** | Mã nguồn, aspect, compiler hỗ trợ | Class/JAR sau biên dịch đã có advice | Chạy nhầm output từ compiler thường |
| **Post-compile/binary weaving** | Class/JAR gốc, aspect và weaver | **Bản binary mới**, giữ tách khỏi bản gốc | Đóng gói nhầm JAR chưa weave hoặc dùng lại output làm input |
| **Load-time weaving** | Bytecode, agent/transformer và cấu hình | Định nghĩa lớp đã biến đổi trong JVM; file đĩa có thể giữ nguyên | Class đã load sớm, sai classloader hoặc agent không bật |
| **Runtime proxy/interception** | Target, proxy/chuỗi interceptor | Đối tượng trung gian và đường gọi qua nó; class target có thể giữ nguyên | Caller nắm target thô hoặc self-invocation bỏ qua proxy ủy quyền |

**Bài kiểm tra artifact:** giả sử `PriceCalculator.java` tạo ra `out/plain/PriceCalculator.class`, sau đó một bước binary weaving sinh `out/woven/PriceCalculator.class`. Test phải **nạp bản trong `out/woven`** để quan sát advice. Với proxy, kiểm tra receiver có là proxy hay không; với load-time weaving, kiểm tra **định nghĩa lớp** đã được biến đổi chứ không chỉ thấy JAR agent trên classpath.

Compile-time weaving thuận tiện khi muốn kiểm chứng artifact trước phát hành nhưng cần toolchain phù hợp và có thể phải build lại. Binary weaving đòi hỏi quản lý cả trước/sau. Load-time weaving phụ thuộc thời điểm nạp lớp, cấu hình và classloader. Proxy dễ lắp ở API boundary nhưng không tự chặn đường gọi bỏ qua nó. Đây là **phân tích đầu ra của quá trình dệt**, không đánh đồng proxy với biến đổi bytecode.

## 7. Ví dụ Java 17 chạy được: trước và sau khi thêm lớp chặn

Để quan sát sự khác biệt mà không cần cài công cụ AOP riêng, chương trình sau xây dựng ba đường gọi:

1. Gọi **BasePriceService** trực tiếp, đại diện cho đường đi của chương trình gốc.
2. Gọi qua **WrappedPriceService**, mô phỏng hành vi sau khi một bộ dệt chèn before/finally quanh lời gọi.
3. Gọi qua **JDK dynamic proxy**, minh họa một cơ chế interception ở runtime.

Cần phân biệt chính xác: hai đường gọi cuối **chỉ minh họa hiệu ứng kết hợp hành vi và giới hạn của wrapper/proxy**; chương trình demo không thực hiện một bước bytecode weaving thực sự.

Tạo file **WeavingModelDemo.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class WeavingModelDemo {
    interface PriceService {
        int quote(int base);
    }

    static final class BasePriceService implements PriceService {
        public int quote(int base) {
            System.out.println("core: quote");
            return base + 20;
        }
    }

    static final class WrappedPriceService implements PriceService {
        private final PriceService target;

        WrappedPriceService(PriceService target) {
            this.target = target;
        }

        public int quote(int base) {
            System.out.println("advice: before");
            try {
                return target.quote(base);
            } finally {
                System.out.println("advice: after");
            }
        }
    }

    static PriceService intercepted(PriceService target) {
        return (PriceService) Proxy.newProxyInstance(
                PriceService.class.getClassLoader(),
                new Class<?>[] {PriceService.class},
                (proxy, method, args) -> {
                    System.out.println("advice: before");
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    } finally {
                        System.out.println("advice: after");
                    }
                });
    }

    public static void main(String[] args) {
        PriceService original = new BasePriceService();
        System.out.println("original = " + original.quote(100));

        PriceService wrapped = new WrappedPriceService(original);
        System.out.println("wrapped = " + wrapped.quote(100));

        PriceService proxy = intercepted(original);
        System.out.println("proxy = " + proxy.quote(100));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 WeavingModelDemo.java
java WeavingModelDemo
~~~

Stdout chính xác:

~~~text
core: quote
original = 120
advice: before
core: quote
advice: after
wrapped = 120
advice: before
core: quote
advice: after
proxy = 120
~~~

Đọc kết quả:

- **original:** chỉ mã lõi chạy, vì đối tượng gốc không được bao bởi một interceptor trong đường gọi đó.
- **wrapped:** phần before và phần finally bao quanh lần thực thi lõi, kết quả 120 không đổi.
- **proxy:** cuộc gọi qua JDK dynamic proxy kích hoạt handler; handler gọi method gốc bằng reflection, rồi phần finally chạy.

Việc bắt **InvocationTargetException** và ném lại **getCause()** giữ ngoại lệ gây ra bởi phương thức đích, thay vì bắt caller nhận một lớp bọc do reflection. Ví dụ tập trung vào phương thức **quote** và không chứng minh rằng mọi phương thức của Java đều có thể bị proxy chặn trong mọi môi trường.

### Tại sao kết quả ba đường đi không giống kết quả ba chương trình đã dệt khác nhau?

Vì chúng đều chạy trong **một chương trình minh họa** có ba đối tượng. Bước thứ hai là Decorator được lập trình viên viết; bước thứ ba là JDK proxy. Một bytecode weaver thực thụ có thể tạo đường thực thi tương tự nhưng artifact, ngữ nghĩa join point và quy tắc chọn phương thức phải được kiểm chứng trên công cụ thực. Demo chỉ giúp nhìn thấy điều cần dự đoán khi học mô hình dệt.

## 8. Hình dạng kết quả sau dệt: vật lý và ngữ nghĩa

Để tránh nhầm lẫn, đánh giá kết quả theo hai câu hỏi.

### 8.1. Hiện vật vật lý thay đổi ra sao?

Các khả năng bao gồm:

- **Tệp class thay đổi:** mã máy ảo bổ sung lời gọi đến advice, cấu trúc method, metadata hoặc thành phần phụ trợ.
- **JAR đã được dệt:** artifact được đóng gói từ những class đầu ra đã qua bước binary/compile-time weaving.
- **Định nghĩa lớp trong JVM thay đổi:** bộ biến đổi cung cấp bytecode đã dệt khi tải lớp, nhưng bản trên đĩa có thể chưa được dệt.
- **Đối tượng proxy mới:** class gốc giữ nguyên, thay đổi nằm ở đối tượng đại diện và đường gọi được lắp ghép.

Không phải mọi bộ dệt đều sinh ra file **.java**. Việc decompile một class có thể giúp hình dung luồng nhưng mã decompile không nhất thiết tương ứng với cấu trúc nguồn và thứ tự debug ban đầu.

### 8.2. Hành vi ngữ nghĩa thay đổi ra sao?

Đây mới là tiêu chí đánh giá chính khi viết Knowledge:

- Join point nào thực sự có advice chạy?
- Advice chạy trước, sau, khi có ngoại lệ hay bao quanh?
- Chạy bao nhiêu lần trên một lời gọi?
- Giá trị trả về, ngoại lệ và các tác dụng phụ có giữ nguyên không?
- Có thể đi theo một đường khác để bỏ qua advice không?
- Khi nhiều aspect cùng áp dụng thì thứ tự là gì?

Một class thay đổi bytecode **không bảo đảm** rule mong muốn đã áp dụng chính xác. Ngược lại, một proxy làm thay đổi hành vi mà không có file class gốc nào bị sửa.

## 9. Matching ở lúc dệt và điều kiện quyết định khi chạy

Không phải mọi pointcut đều có thể được giải quyết hoàn toàn từ mã tĩnh.

Ví dụ:

- **Điều kiện tĩnh:** một phương thức có chữ ký xác định hoặc mang annotation đánh dấu, nếu bộ dệt có thể nhìn thấy metadata đó.
- **Điều kiện động:** chỉ ghi nhận nếu tham số tại thời điểm gọi thỏa điều kiện hoặc context của tác vụ có giá trị nhất định.

Bộ dệt có thể xác định tập vị trí tiềm năng ở giai đoạn compile/load, rồi để lại các điều kiện kiểm tra khi chương trình chạy. Cũng có công nghệ đánh giá selector khi interceptor nhận lời gọi.

Vì vậy, cần phân biệt ba tập:

1. **Join point được mô hình AOP hỗ trợ**.
2. **Join point có thể khớp pointcut theo thông tin đã biết**.
3. **Join point thực sự kích hoạt advice trong một lần chạy cụ thể**.

Thống kê “có bao nhiêu phương thức khớp ở lúc biên dịch” và “advice đã chạy bao nhiêu lần” trả lời hai câu hỏi khác nhau. Đường đi điều kiện, lazy loading, ngoại lệ hoặc lời gọi bất đồng bộ có thể khiến số lần thực thi nhỏ hoặc lớn hơn số điểm tĩnh.

## 10. Vấn đề thứ tự khi nhiều aspect cùng áp dụng

Giả sử một thao tác được cả **Tracing** và **Retry** bao quanh.

~~~text
Tracing(Retry(Core))
  → Tracing nhìn một chu kỳ gồm tất cả lần thử.

Retry(Tracing(Core))
  → Tracing có thể nhìn riêng từng lần thử.
~~~

Sự khác biệt chỉ đúng khi các wrapper/advice hiện thực những hành vi như trong sơ đồ, nhưng nó cho thấy thứ tự ghép ảnh hưởng **ý nghĩa đầu ra**, không chỉ hình thức. Khi có before/after từ nhiều aspect, việc “cái nào chạy trước” cần được xác định qua cơ chế precedence/order của công cụ và kiểm thử thực tế.

Một ví dụ khác:

~~~text
Before A
    Before B
        Core
    After B
After A
~~~

Đây là **một thứ tự lồng nhau hợp lệ**, không phải quy luật mặc định cho mọi công cụ. Nếu hai aspect không có thứ tự ổn định, việc phụ thuộc vào trình tự log hoặc thay đổi trạng thái là một lỗi thiết kế.

Một **around advice** có thể bỏ qua lời gọi lõi nếu nó không chuyển tiếp, hoặc có thể lặp lời gọi nếu nó retry; kết quả sau dệt vì thế có thể khác rõ so với chương trình gốc. Nếu lõi có thao tác gửi tiền hoặc phát thông điệp, việc lặp gọi cần có bảo đảm chống thực hiện trùng.

## 11. Các ranh giới dễ gây hiểu sai về phạm vi dệt

### 11.1. Self-invocation qua proxy

Xét đoạn code khái niệm:

~~~java
void outer() {
    inner();
}

void inner() {
    // Xử lý cần theo dõi
}
~~~

Nếu đối tượng **outer** đang chạy là target gốc và lời gọi **inner()** được thực hiện nội bộ, bộ chặn dựa hoàn toàn vào proxy bên ngoài có thể không nhìn thấy lời gọi đó. Với một cơ chế dệt bytecode hỗ trợ join point **execution của inner**, advice vẫn có thể được áp dụng. Muốn dự đoán chính xác, phải biết **loại join point và đường đi thực tế**.

### 11.2. Lời gọi phương thức không đồng nghĩa truy cập trường

Một proxy method-call không tự động trở thành bộ chặn read/write của field. Nếu cần aspect theo dõi field access hoặc constructor, phải xác minh công cụ có loại join point này. Không nên khẳng định mọi annotation hoặc mọi thành viên trong lớp đều được xử lý như nhau.

### 11.3. Một class có nhiều loader hoặc không nằm trong phạm vi weaving

Ở những hệ thống sử dụng class loader, cùng một tên class có thể được nạp dưới nhiều danh tính lớp khác nhau. Cấu hình dệt phải áp dụng đến đúng vùng lớp đang được dùng. Một class được nạp trước khi transformer được thiết lập có thể không tự được sửa về sau, trừ khi công nghệ hỗ trợ và thực hiện retransformation/redefinition phù hợp.

### 11.4. Source giữ nguyên không chứng minh advice không hoạt động

Biên dịch hoặc load-time weaving có thể làm đường thực thi thay đổi dù người đọc file Java chỉ thấy mã nghiệp vụ. Vì vậy, khi điều tra lỗi không thể chỉ tìm bằng văn bản xem thân phương thức có gọi helper hay không; cần xem công cụ dệt, điểm chọn và artifact/runtime.

### 11.5. Thoát bằng ngoại lệ khác trả về thành công

**After-returning** và **after-throwing** diễn tả những tình huống khác nhau; **after-finally** có thể dùng để đảm bảo phần kết thúc chạy khi cả đường thành công hoặc lỗi, tùy ngữ nghĩa công cụ. Không nên ghi “thao tác thành công” trong advice chỉ được chứng minh là phương thức đã kết thúc. Đặc biệt, một phương thức trả về đối tượng biểu diễn công việc bất đồng bộ chưa có nghĩa công việc đó đã hoàn thành.

## 12. Bộ dệt và hợp đồng đúng đắn của chương trình

Một bài phân tích weaving tốt phải vượt qua câu hỏi “có in thêm log không?” để xem **hợp đồng thực thi**.

### 12.1. Giá trị trả về

Nếu target trả về số **120**, advice quan sát thuần túy nên để caller tiếp tục nhận **120**. Với **around advice** được phép thay kết quả, thay đổi phải là một phần chính sách có chủ đích và tương thích với hợp đồng phương thức.

### 12.2. Ngoại lệ

Nếu target ném lỗi, interceptor nên có chính sách rõ về việc truyền tiếp, chuyển đổi hoặc ghi nhận. Nuốt ngoại lệ trong advice vô tình biến thất bại thành thành công và có thể làm hỏng invariant của caller. Một exception do chính advice ném ra cũng có thể thay thế exception gốc; cần quyết định cách ưu tiên và ghi nhận lỗi.

### 12.3. Tác dụng phụ

Log hoặc đo metric có thể có lỗi I/O. Trong một số hệ thống, lỗi quan sát không nên làm hỏng nghiệp vụ; trong hệ thống kiểm toán bắt buộc, thất bại ghi nhận có thể phải chặn thao tác. Đây là quyết định chính sách, không phải hành vi mặc định của từ khóa advice.

### 12.4. Concurrency và trạng thái aspect

Nếu aspect lưu trạng thái chia sẻ giữa nhiều lời gọi, cần xét an toàn luồng và reentrancy. Nếu lưu context theo thread, công việc bất đồng bộ có thể chạy trên thread khác; phải có chiến lược truyền context phù hợp. Weaving không tự giải quyết các vấn đề đồng bộ.

### 12.5. Bảo mật và dữ liệu

Advice ghi tham số hay giá trị trả về có thể vô tình lưu thông tin nhạy cảm. Bộ dệt chỉ gắn hành vi vào đúng vị trí; nó không quyết định dữ liệu nào được phép thu thập. Những pointcut áp dụng rộng phải được kiểm soát phạm vi và dữ liệu đầu ra.

## 13. Cách kiểm chứng một kết quả sau khi dệt

Quy trình kiểm tra nên phân biệt **đã nạp/cấu hình bộ dệt** với **advice thực sự chạy**.

### Bước 1 — Xác định hợp đồng dự kiến

Ghi rõ lớp/method nào cần khớp, loại join point, advice nào áp dụng, ngoại lệ dự kiến và yêu cầu thứ tự. Ví dụ: mọi lần **PriceService.quote** được gọi qua đường chặn cụ thể đều ghi begin/end đúng một lần, vẫn trả cùng kết quả.

### Bước 2 — Kiểm tra cấu hình và hiện vật

Tùy cơ chế:

- Với compile-time/binary weaving, kiểm tra artifact triển khai là bản đã dệt.
- Với load-time weaving, kiểm tra transformer/agent và phạm vi tải lớp đã hoạt động.
- Với proxy, kiểm tra caller đang giữ tham chiếu proxy thay vì target trực tiếp.

Có thể dùng các công cụ kiểm tra class/bytecode thích hợp hoặc thông tin chẩn đoán của weaver, nhưng tránh coi việc nhìn thấy một instruction đơn lẻ là bằng chứng đầy đủ về mọi lần chạy.

### Bước 3 — Kiểm tra hành vi ở đường đi thật

Tạo ca có khớp pointcut và ca không khớp. Với ca khớp, kiểm tra số lần advice chạy, kết quả trả về và thứ tự. Với ca không khớp, bảo đảm advice không kích hoạt sai.

### Bước 4 — Kiểm tra ca biên

Bao gồm ngoại lệ, nhiều aspect cùng khớp, gọi lồng nhau, self-invocation nếu dùng proxy, đường gọi trực tiếp vào target, chạy song song và bất đồng bộ khi có liên quan. Đặc biệt kiểm tra điều kiện động của pointcut tại runtime.

### Bước 5 — Đối chiếu hành vi cũ và mới

Tách phép so sánh thành:

| Thuộc tính | Trước dệt | Sau dệt | Câu hỏi cần kiểm chứng |
|---|---|---|---|
| Kết quả nghiệp vụ | Trả **120** | Thường vẫn **120** với advice quan sát thuần túy | Có bị thay giá trị không? |
| Side effect kỹ thuật | Không có trace | Có trace trước/sau | Đúng vị trí, đúng số lần chứ? |
| Ngoại lệ | Truyền lỗi gốc | Có thể thêm ghi nhận lỗi | Có nuốt hoặc đổi lỗi ngoài ý muốn không? |
| Đường gọi | Caller đến target | Có thể qua proxy hoặc mã biến đổi | Có đường đi bỏ qua advice không? |
| Hiện vật | Class/chương trình chưa dệt | Class dệt, lớp nạp đã biến đổi hoặc runtime proxy | Bộ dệt thực tế đã tác động vào đâu? |

## 14. Những sai lầm thường gặp

**Sai lầm 1 — Xem aspect là mã tự chạy chỉ vì đã khai báo.** Một khai báo aspect phải được cơ chế AOP nhận diện và áp dụng. Nếu weaver chưa được kích hoạt hoặc pointcut không khớp, hành vi mong muốn có thể không xuất hiện.

**Sai lầm 2 — Nghĩ kết quả sau dệt luôn là một file Java mới.** Phần lớn phân tích thực tế phải nhìn ở lớp bytecode, class loading hoặc đường chặn lời gọi; mã nguồn Java có thể không đổi.

**Sai lầm 3 — Nhầm static weaving với proxy.** Weaving có thể tác động trực tiếp vào vị trí thực thi đã chọn, còn proxy thường phụ thuộc lời gọi đi qua đại diện. Không thể lấy hạn chế của một công cụ để suy ra giới hạn phổ quát của AOP.

**Sai lầm 4 — Chỉ kiểm tra một đường thành công.** Kết quả có thể đúng ở đường thường nhưng advice bị bỏ qua khi gọi nội bộ, chạy lặp khi retry, hoặc xử lý ngoại lệ không đúng.

**Sai lầm 5 — Xem pointcut là không thể bị ảnh hưởng bởi refactor.** Đổi tên package, annotation, method hoặc cách tổ chức lớp có thể làm tập điểm khớp khác đi. Cần kiểm thử phạm vi và cách đặt tên ổn định.

**Sai lầm 6 — Cho rằng after luôn đồng nghĩa thành công.** Advice chạy khi phương thức kết thúc không đảm bảo thao tác nghiệp vụ đã commit hoặc tác vụ bất đồng bộ đã xong.

**Sai lầm 7 — Bỏ qua thứ tự aspect.** Các advice lồng nhau có thể đổi nghĩa phép đo, transaction, retry hoặc cache. Khi thứ tự là điều kiện đúng đắn, phải đặc tả và kiểm chứng.

**Sai lầm 8 — Giả định woven artifact đồng nhất trên mọi máy.** Đầu vào classpath, phiên bản compiler/weaver, class loader và cấu hình có thể ảnh hưởng kết quả. Build và runtime phải có khả năng tái lập, quan sát được.

## 15. Bài tập suy luận từ ba thành phần

### Bài tập 1: Bộ dệt có tạo file mới không?

Một ứng dụng dùng proxy để bọc **PriceService**. File **BasePriceService.class** không thay đổi, nhưng log advice xuất hiện khi gọi qua interface.

**Hỏi:** có thể xem đó là chương trình có hành vi kết hợp không? Có thể kết luận đã biến đổi bytecode của BasePriceService không?

**Gợi ý:** hành vi kết hợp là có trên đường proxy; không có căn cứ nói class gốc đã được sửa.

### Bài tập 2: Cùng source, khác kết quả

Hai lần triển khai từ cùng source: môi trường A kích hoạt load-time weaving, môi trường B bỏ qua cấu hình bộ biến đổi. B có chạy advice chắc chắn không?

**Gợi ý:** không; cần kiểm tra thực tế bộ dệt và lớp được áp dụng. Source giống nhau chưa đủ khẳng định hành vi giống nhau.

### Bài tập 3: Chọn đúng join point

Muốn ghi dấu **mỗi lần thực thi** của phương thức X, nhưng pointcut lại chọn các vị trí **gọi X** trong chỉ một nhóm caller.

**Hỏi:** có thể bỏ sót khi X được gọi từ nơi khác không?

**Gợi ý:** có. Call join point của một tập caller không giống execution join point tại chính X.

### Bài tập 4: Around advice bỏ qua hành vi gốc

Một advice nhận lời gọi nhưng trả về giá trị mặc định mà không gọi tiếp vào hành vi gốc.

**Hỏi:** chương trình sau dệt còn giữ chắc chắn chức năng ban đầu không?

**Gợi ý:** không. Nếu không có bảo đảm rõ về quy tắc này, bộ dệt hoặc advice đã thay đổi ngữ nghĩa nghiệp vụ.

### Bài tập 5: Kiểm thử tương đương

Chương trình chưa dệt trả 120. Chương trình đã dệt cũng trả 120 nhưng ghi hai lần cùng một metric. Chỉ dùng assert kết quả 120 đã đủ chưa?

**Gợi ý:** chưa; cần kiểm tra hiệu ứng bổ sung, số lần và thứ tự advice, đồng thời xác minh các đường lỗi và đường gọi ngoài phạm vi.

## 16. Sơ đồ ghi nhớ để đọc một hệ thống AOP bất kỳ

~~~text
(1) BASE PROGRAM
    Source / bytecode / đối tượng target
    "Hành vi gốc là gì?"
                │
                ├─────────────────┐
                │                 │
(2) ASPECT SPECIFICATION          │
    Pointcut: ở đâu?              │
    Advice: làm gì?               │
    Precedence: thứ tự?           │
                │                 │
                ▼                 ▼
(3) WEAVER / INTERCEPTOR MECHANISM
    Compile / post-compile / load / runtime
    "Loại join point nào thực sự được hỗ trợ?"
                │
                ▼
(4) WOVEN RESULT
    Bytecode biến đổi hoặc đường gọi qua proxy
    "Hiện vật nào thay đổi?"
                │
                ▼
(5) OBSERVED EXECUTION
    Advice nào chạy, lúc nào, bao nhiêu lần?
    Target có được gọi? Kết quả và lỗi ra sao?
~~~

Khi gặp một công nghệ AOP mới, trước tiên xác định **mã gốc, định nghĩa aspect, cơ chế dệt và điểm áp dụng thực tế**. Sau đó phân biệt **artifact sau dệt** với **hành vi quan sát khi chạy**. Hai bước này giúp đọc và chẩn đoán một hệ thống aspect đúng về mặt kỹ thuật, kể cả khi chương trình gốc không chứa lời gọi trực tiếp đến logic xuyên cắt.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-component-model-007">Phân biệt chọn vị trí, truyền ngữ cảnh và thực hiện hành vi</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt chọn vị trí, truyền ngữ cảnh và thực hiện hành vi trong AOP

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), muốn đưa ghi nhật ký, đo thời gian hoặc kiểm soát quyền ra khỏi mã nghiệp vụ, ta cần giải quyết ba câu hỏi khác nhau:

1. **Chọn vị trí:** những điểm nào trong quá trình chương trình chạy phải chịu tác động?
2. **Truyền ngữ cảnh:** tại điểm đã chọn, hành vi bổ sung cần những thông tin gì của lần thực thi hiện tại?
3. **Thực hiện hành vi:** sau khi có điểm và ngữ cảnh, mã bổ sung làm gì trước, sau hoặc bao quanh nghiệp vụ?

Trong thuật ngữ AOP, việc chọn vị trí liên quan đến **join point** và **pointcut**; việc cung cấp dữ liệu cho mã bổ sung là **context exposure/binding**; còn mã thực hiện tác động là **advice**. Một **aspect** nhóm các quy tắc và hành vi cho cùng mối quan tâm cắt ngang. Đây là ba trách nhiệm có thể phối hợp, nhưng phải phân biệt rõ để thiết kế, kiểm thử và sửa lỗi.

## 1. Ví dụ dẫn nhập: cùng một lời gọi, ba câu hỏi

Một hệ thống đặt chỗ có `reserve(user, seats)` và `status(id)`. Ta muốn ghi log cho mỗi lần đặt chỗ, nhưng không ghi cùng loại log cho thao tác xem trạng thái.

| Trách nhiệm | Câu hỏi cụ thể | Cách trả lời trong ví dụ |
| --- | --- | --- |
| Chọn vị trí | Lời gọi nào kích hoạt chính sách? | Chỉ thao tác `reserve` |
| Truyền ngữ cảnh | Mã log cần dữ liệu nào? | Tên thao tác, `user`, `seats`, kết quả hoặc ngoại lệ |
| Thực hiện hành vi | Khi kích hoạt cần làm gì? | In `BEFORE`, gọi nghiệp vụ, in `AFTER` khi thành công hoặc `ERROR` khi lỗi |

Điểm mấu chốt là: **bộ chọn không tự ghi log**, **context không tự quyết định quyền**, và **advice không tự biết mình nên áp dụng ở đâu** nếu không được kết hợp với cơ chế chọn điểm.

~~~text
Một lời gọi phát sinh
        |
        v
 [Các join point mà cơ chế hỗ trợ]
        |
        v
 [POINTCUT: có khớp điểm này?]
        |                         \
      Có                          Không
        |                           |
        v                           v
 [BINDING: tạo/lấy context]      Không chạy advice này
        |
        v
 [ADVICE: hành vi phụ trợ]
        |
        +--> Before
        +--> Gọi hoặc chặn nghiệp vụ
        +--> After success / After error / Finally
~~~

Đây là sơ đồ khái niệm. Danh sách join point, dữ liệu có thể ràng buộc và hình thức triển khai tùy thuộc kỹ thuật AOP đang dùng.

## 2. Chọn vị trí: phân biệt join point và pointcut

### 2.1 Join point là vị trí có thể can thiệp

**Join point (điểm nối)** là một điểm xác định trong quá trình chương trình thực thi, nơi một cơ chế AOP *có khả năng* gắn xử lý bổ sung. Chẳng hạn: lúc gọi một phương thức, lúc bắt đầu thực thi thân phương thức, hoặc một số sự kiện khác mà cơ chế có hỗ trợ.

Cần phân biệt hai trường hợp thường gặp:

- **Method call:** điểm bên gọi phát sinh lời gọi đến một phương thức.
- **Method execution:** điểm thực thi thân phương thức đích.

Nếu phương thức A gọi B ba lần, ta có ba lần gọi và tương ứng ba lần B được thực thi. Tuy nhiên, một lớp bọc chặn lời gọi từ bên ngoài có thể nhìn thấy điểm gọi qua lớp bọc mà không nhìn thấy các lời gọi nội bộ bên trong B. Một cơ chế biến đổi mã có thể quan sát những điểm khác. Vì vậy, không được suy rằng hỗ trợ một loại join point đồng nghĩa hỗ trợ mọi loại khác.

**Join point là ứng viên của một điểm tác động**, chưa đồng nghĩa advice nào đó đã chạy ở đây.

### 2.2 Pointcut là quy tắc chọn

**Pointcut** mô tả một tập các join point thỏa điều kiện. Điều kiện có thể dựa trên chữ ký phương thức, lớp, phạm vi mã hoặc dữ liệu runtime nếu cơ chế hỗ trợ.

Mô tả bằng giả mã, không phụ thuộc cú pháp thư viện:

~~~text
pointcut: các lần thực thi reserve(user, seats)
advice:   ghi thông tin trước và sau nghiệp vụ
~~~

Dòng thứ nhất là **vị trí chọn**; dòng thứ hai mới nói **hành vi cần thực hiện**. Có thể giữ cùng pointcut nhưng thay advice ghi log bằng advice đo thời gian. Ngược lại, có thể áp dụng cùng advice ở một tập điểm khác, miễn là dữ liệu và hợp đồng thực thi phù hợp.

Một số quy tắc chọn có thể được xác định nhờ cấu trúc tĩnh như tên phương thức và chữ ký. Một số khác phải đánh giá lúc chạy, ví dụ dựa vào kiểu thực của đối tượng hoặc giá trị tham số. Vì vậy, không phải mọi pointcut chỉ là một phép tìm kiếm tên phương thức.

**Chọn quá rộng** dễ làm log nhiễu, tốn tài nguyên hoặc can thiệp vào mã không liên quan. **Chọn quá hẹp** khiến một số đường gọi quan trọng không được quan sát hoặc bảo vệ. Bộ kiểm thử phải xác nhận cả vị trí *phải được chọn* và vị trí *không được chọn*.

### 2.3 Đừng lẫn điểm chọn với quyết định an ninh

Giả sử mọi lần xóa dữ liệu phải được kiểm tra quyền. Nếu pointcut chỉ chọn các lời gọi “đã đủ quyền”, những lần gọi không đủ quyền có thể **không vào advice kiểm tra**, rồi vẫn đến nghiệp vụ nếu không có rào chắn khác. Cách tổ chức rõ ràng hơn là chọn **tất cả các điểm cần bảo vệ** và thực hiện đánh giá quyền trong một hành vi được áp dụng nhất quán.

Kết luận: pointcut trả lời **“xét điểm nào?”**. Việc cho phép hay từ chối một tác động là trách nhiệm của chính sách thực thi tại ranh giới thích hợp.

## 3. Truyền ngữ cảnh: context binding giải quyết điều gì?

Sau khi xác định một điểm, advice thường cần dữ liệu cụ thể của **lần gọi này**, chứ không chỉ biết tên phương thức. **Context exposure/binding** đưa các dữ liệu khả dụng vào phạm vi xử lý của advice.

Ví dụ:

~~~text
reserve("Lan", 2)
~~~

Ngữ cảnh có thể bao gồm:

| Nhóm ngữ cảnh | Ví dụ | Mục đích |
| --- | --- | --- |
| Thông tin join point | Tên, chữ ký `reserve` | Đặt tên thao tác, phân loại log/metrics |
| Đối tượng liên quan | Đối tượng đích nhận thao tác | Hiểu thành phần đang bị tác động |
| Tham số lúc chạy | `"Lan"`, `2` | Quan sát dữ liệu của lần gọi cụ thể |
| Kết quả | Mã đặt chỗ trả về | Xác nhận thành công, ghi kết quả được phép |
| Ngoại lệ | `IllegalArgumentException` | Phân loại và ghi nhận lỗi |
| Ngữ cảnh nghiệp vụ/hạ tầng được cấp hợp lệ | Mã tương quan, danh tính đã xác thực | Liên kết sự kiện và kiểm tra chính sách |

Cần hiểu **thời điểm khả dụng** của dữ liệu. Trước khi nghiệp vụ chạy, ta có thể có chữ ký và tham số, nhưng chưa thể có kết quả của nghiệp vụ. Khi thực thi thành công mới có giá trị trả về; khi lỗi thì có ngoại lệ. Một số advice chỉ chạy trên nhánh thành công, một số chỉ trên nhánh lỗi. Việc ràng buộc dữ liệu phải phù hợp loại điểm và nhánh thực thi đó.

Có hai khái niệm dễ nhầm:

- **Thông tin mô tả điểm** tương đối ổn định, ví dụ chữ ký phương thức.
- **Thông tin động** thay đổi theo từng lần chạy, ví dụ `seats=2` ở lần đầu và `seats=0` ở lần sau.

Cùng một pointcut có thể chọn cả hai lần gọi; context binding làm cho advice phân biệt được dữ liệu của từng lần.

Trong một số mô hình AOP, biểu thức chọn vừa kiểm tra điều kiện vừa đặt tên tham số để truyền sang advice. Điều này không làm ba trách nhiệm trở thành một: **selection quyết định điểm nào**, **binding quyết định dữ liệu nào được cấp**, và **advice quyết định hành động nào xảy ra**.

### 3.1 Truyền ngữ cảnh không phải tự động lan truyền dữ liệu

Không nên suy rằng mọi thông tin của một luồng chạy đều có mặt trong tác vụ nền hay một luồng khác. Với thao tác bất đồng bộ, context có thể cần được truyền một cách tường minh qua ranh giới thread hoặc tác vụ. Nếu cơ chế dùng trạng thái gắn với luồng, phải cân nhắc vòng đời, tính cô lập giữa các lời gọi và dọn dẹp dữ liệu sau khi dùng.

Context cũng không mặc nhiên là nguồn đáng tin. Chuỗi tên vai trò lấy từ tham số người dùng không đủ chứng minh quyền truy cập. Dữ liệu quan sát trong log phải được lọc, đặc biệt với mật khẩu, token, thông tin cá nhân và dữ liệu nhạy cảm.

## 4. Thực hiện hành vi: các dạng advice

**Advice** là đoạn mã bổ sung được kích hoạt tại những join point phù hợp. Nó có thể ghi log, đo thời gian, kiểm tra quyền, theo dõi lỗi hoặc thực hiện xử lý khác.

| Loại hành vi | Thời điểm | Ứng dụng |
| --- | --- | --- |
| Before | Trước phần nghiệp vụ | Ghi START, kiểm tra tiền điều kiện |
| After returning / success | Sau khi nghiệp vụ kết thúc bình thường | Ghi kết quả, thống kê thành công |
| After throwing / error | Khi nghiệp vụ ném lỗi | Ghi hoặc phân loại ngoại lệ |
| After / finally | Khi rời phạm vi được bao quanh | Dừng đồng hồ, giải phóng tài nguyên |
| Around | Bao quanh nghiệp vụ | Kiểm soát trước/sau, gọi tiếp, kết quả và ngoại lệ |

Tên và khả năng chính xác phụ thuộc cơ chế triển khai. Không nên mặc định mọi loại advice có quyền ngăn lời gọi hoặc thay giá trị trả về.

### 4.1 Around advice và cơ chế tiếp tục

Một dạng around advice thường có khả năng gọi `proceed` hoặc continuation tương đương để thực hiện phần được bao quanh.

~~~text
around(context, proceed):
    started = monotonicTime()
    logStart(context.operation)
    try:
        result = proceed()
        logSuccess(context.operation)
        return result
    catch error:
        logFailure(context.operation, error)
        throw error
    finally:
        recordDuration(monotonicTime() - started)
~~~

Đoạn trên là **giả mã khái niệm**: `context.operation` do binding cung cấp, còn việc ghi log, gọi tiếp và đo thời gian là hành vi của advice. Nếu không gọi `proceed`, nghiệp vụ có thể bị chặn; nếu gọi hai lần, nó có thể chạy hai lần trong những cơ chế cho phép. Việc nuốt lỗi, thay kết quả hay gọi lại phải tuân thủ hợp đồng và tính an toàn của tác dụng phụ nghiệp vụ.

## 5. Ví dụ Java 17: mô hình hóa từng trách nhiệm

Ví dụ sau dùng **Java dynamic proxy** để minh họa việc bọc các lời gọi qua giao diện. Đây là một cách diễn giải tư duy AOP, không phải cơ chế AOP bao quát mọi loại join point.

Lưu file `AopRolesDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AopRolesDemo {
    interface Booking {
        String reserve(String actor, int seats);
        String status(int requestId);
    }

    static final class BookingImpl implements Booking {
        @Override
        public String reserve(String actor, int seats) {
            System.out.println("BUSINESS reserve");
            if (seats <= 0) {
                throw new IllegalArgumentException("seats must be positive");
            }
            return "R-" + seats;
        }

        @Override
        public String status(int requestId) {
            System.out.println("BUSINESS status");
            return "OPEN";
        }
    }

    // Context của đúng một lần gọi.
    record JoinContext(Booking target, Method method, Object[] arguments) {
    }

    @FunctionalInterface
    interface Proceed {
        Object call() throws Throwable;
    }

    // 1. CHỌN VỊ TRÍ.
    static boolean pointcut(Method method) {
        return method.getDeclaringClass() == Booking.class
                && method.getName().equals("reserve")
                && method.getParameterCount() == 2;
    }

    static Object invokeTarget(JoinContext context) throws Throwable {
        try {
            return context.method().invoke(
                    context.target(), context.arguments());
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    // 3. THỰC HIỆN HÀNH VI.
    static Object advice(JoinContext context, Proceed proceed)
            throws Throwable {
        System.out.println("BEFORE " + context.method().getName()
                + " actor=" + context.arguments()[0]
                + " seats=" + context.arguments()[1]);
        try {
            Object result = proceed.call();
            System.out.println("AFTER result=" + result);
            return result;
        } catch (Throwable error) {
            System.out.println(
                    "ERROR " + error.getClass().getSimpleName());
            throw error;
        }
    }

    static Booking weave(Booking target) {
        return (Booking) Proxy.newProxyInstance(
                Booking.class.getClassLoader(),
                new Class<?>[] { Booking.class },
                (proxy, method, args) -> {
                    // 2. TRUYỀN NGỮ CẢNH.
                    Object[] callArgs =
                            args == null ? new Object[0] : args.clone();
                    JoinContext context =
                            new JoinContext(target, method, callArgs);

                    if (!pointcut(method)) {
                        return invokeTarget(context);
                    }
                    return advice(context, () -> invokeTarget(context));
                });
    }

    public static void main(String[] args) {
        Booking booking = weave(new BookingImpl());
        System.out.println("RESULT " + booking.reserve("Lan", 2));
        System.out.println("RESULT " + booking.status(7));
        try {
            booking.reserve("Lan", 0);
        } catch (IllegalArgumentException ex) {
            System.out.println("CALLER " + ex.getMessage());
        }
    }
}
~~~

Chạy bằng Java 17+:

~~~bash
javac AopRolesDemo.java
java AopRolesDemo
~~~

Stdout:

~~~text
BEFORE reserve actor=Lan seats=2
BUSINESS reserve
AFTER result=R-2
RESULT R-2
BUSINESS status
RESULT OPEN
BEFORE reserve actor=Lan seats=0
BUSINESS reserve
ERROR IllegalArgumentException
CALLER seats must be positive
~~~

**Đối chiếu với ba trách nhiệm:**

1. `pointcut(Method)` chỉ chọn lời gọi `reserve` qua giao diện `Booking`, có hai tham số. `status` không khớp nên không có dòng `BEFORE` hay `AFTER`.
2. `JoinContext` lưu đối tượng đích, `Method` và tham số của *lần gọi hiện tại*. Cả hai lần `reserve` cùng khớp pointcut, nhưng lần thứ nhất có `seats=2`, lần thứ hai có `seats=0`.
3. `advice(...)` in `BEFORE`, gọi `proceed.call()`, rồi in `AFTER` hoặc `ERROR`. Nhánh lỗi được truyền tiếp; `invokeTarget` tháo `InvocationTargetException` của reflection để giữ ngoại lệ gốc cho bên gọi.

Tên `weave` trong ví dụ chỉ biểu thị thao tác **gắn lớp bọc khi tạo proxy**. Các hệ thống AOP khác có thể kết hợp mã bằng những kỹ thuật khác nhau; việc tạo proxy không phải là định nghĩa duy nhất của weaving.

**Giới hạn quan trọng:** proxy chỉ can thiệp các lời gọi đi **qua proxy**. Lời gọi trực tiếp `new BookingImpl().reserve(...)` không đi qua bộ chọn và advice này. Lời gọi nội bộ trên đối tượng gốc cũng không tự đi qua proxy. Ví dụ sử dụng tham số `actor` để trình bày binding, không đại diện cho cơ chế xác thực danh tính.

## 6. So sánh ba trách nhiệm qua các tình huống thực tế

### 6.1 Ghi nhật ký

- **Chọn vị trí:** thao tác cần được ghi nhận; bao gồm cả các lần có thể thất bại nếu muốn quan sát lỗi.
- **Truyền ngữ cảnh:** tên thao tác, mã tương quan, trạng thái và những trường đã được phép ghi.
- **Thực hiện hành vi:** ghi sự kiện bắt đầu, thành công hoặc thất bại, che dữ liệu nhạy cảm.

Nếu advice chỉ chạy trên nhánh thành công, dù context xử lý lỗi được thiết kế tốt đến đâu, log lỗi vẫn không có cơ hội chạy. Đây là lỗi phối hợp **điểm chọn/kiểu advice** với mục tiêu ghi nhận.

### 6.2 Đo thời gian

- **Chọn vị trí:** các thao tác cần theo dõi độ trễ.
- **Truyền ngữ cảnh:** tên phép đo, loại thao tác, trạng thái; tránh nhãn có số lượng giá trị quá lớn.
- **Thực hiện hành vi:** lấy mốc thời gian bằng đồng hồ đo khoảng thời gian, gọi nghiệp vụ, ghi chênh lệch ở nhánh kết thúc.

Phạm vi đo phụ thuộc vị trí advice được gắn: có tính kiểm tra quyền, thao tác mạng hay thời gian chờ không? Nếu lời gọi chỉ phát động một tác vụ bất đồng bộ và trả về ngay, đo quanh lời gọi chỉ phản ánh thời gian *khởi phát*, không phản ánh thời gian *hoàn tất*.

### 6.3 Kiểm soát truy cập

- **Chọn vị trí:** các điểm vào của những hành động cần bảo vệ.
- **Truyền ngữ cảnh:** danh tính được xác thực, hành động, tài nguyên và thuộc tính chính sách.
- **Thực hiện hành vi:** đánh giá quyền và chặn trước khi xảy ra tác dụng phụ cần bảo vệ.

Một tham số `role` được truyền vào context không mặc nhiên đáng tin. Dữ liệu đầu vào chỉ là **dữ liệu**, còn việc xác minh nguồn và đánh giá quyền thuộc về chính sách an ninh. Cũng phải kiểm tra các đường gọi không đi qua proxy, lời gọi từ tác vụ nền và những ranh giới khác mà cơ chế can thiệp không bao phủ.

## 7. Các nhầm lẫn hay gặp

**Nhầm join point với pointcut.** Join point là một điểm có thể tác động; pointcut là quy tắc chọn một tập các điểm như vậy. Phương thức tồn tại trong chương trình không có nghĩa mọi lần thực thi của nó chịu một advice.

**Nhầm chọn điểm với thực thi.** Quy tắc `reserve` khớp không tự in log, không tự chặn người gọi hoặc tính thời gian. Advice và thứ tự áp dụng mới tạo ra hành vi thực tế.

**Nhầm binding với điều kiện chọn.** Binding `seats=0` cung cấp dữ liệu cho advice; bản thân giá trị 0 không làm lời gọi bị chặn nếu không có quy tắc hoặc hành vi kiểm tra nó.

**Cho rằng mọi context có sẵn ở mọi thời điểm.** Kết quả chỉ có sau khi hoàn thành thành công; ngoại lệ xuất hiện ở nhánh lỗi. Context của thread không tự động lan truyền đến mọi tác vụ bất đồng bộ.

**Quên continuation trong around advice.** Nếu không gọi tiếp, nghiệp vụ có thể không chạy. Nếu gọi hai lần, nó có thể bị thực hiện hai lần. Nếu nuốt lỗi, bên gọi sẽ nhìn thấy hợp đồng khác.

**Tin rằng chọn đúng tên phương thức là đủ cho bảo mật.** Phải kiểm tra mọi đường vào thực tế, nguồn danh tính được xác thực, tài nguyên chịu tác động và thời điểm kiểm tra. Các bất biến nghiệp vụ quan trọng có thể vẫn cần bảo vệ ở tầng dữ liệu hoặc miền thích hợp.

## 8. Khi nhiều advice cùng áp dụng

Giả sử một thao tác đặt chỗ cần kiểm tra quyền, ghi log và đo độ trễ:

~~~text
Bên gọi
  |
  +-- Bắt đầu đo thời gian
       |
       +-- Kiểm tra quyền
            |-- Từ chối --> Lỗi (có thể chưa ghi START)
            |
            +-- Ghi log START
                 |
                 +-- Thực hiện nghiệp vụ
                 |
            +-- Ghi SUCCESS hoặc ERROR
       |
  +-- Dừng đồng hồ trong nhánh kết thúc
~~~

Nếu đưa log ra ngoài kiểm tra quyền, các lần từ chối cũng có thể được ghi. Nếu đặt đồng hồ bên trong kiểm tra quyền, thời gian từ chối không được tính vào phép đo đó. Như vậy, cùng một **tập điểm được chọn**, cùng một **nguồn context**, nhưng thay đổi **thứ tự advice** đã thay đổi kết quả quan sát.

Với nhiều aspect, cần có quy tắc thứ tự rõ ràng, kiểm thử cả thành công, thất bại và từ chối. Việc sắp xếp tác động trong advice cũng phải tránh vô tình ghi thành công cho một lần ném lỗi.

## 9. Bảng kiểm để thiết kế và kiểm thử

| Tình huống | Chọn vị trí | Truyền ngữ cảnh | Thực hiện hành vi |
| --- | --- | --- | --- |
| `reserve("Lan", 2)` | Khớp | `actor=Lan`, `seats=2` | BEFORE → nghiệp vụ → AFTER |
| `status(7)` | Không khớp | Không cần cấp context cho advice đó | Không chạy advice |
| `reserve("Lan", 0)` | Vẫn khớp | `seats=0` | BEFORE → nghiệp vụ ném lỗi → ERROR; bên gọi nhận lỗi gốc |
| Gọi trực tiếp `BookingImpl` | Không đi qua proxy minh họa | Không cấp qua proxy | Không có advice proxy |
| Nhiều advice cùng khớp | Xác định được tập điểm từng chính sách | Mỗi advice nhận đúng dữ liệu được phép | Thứ tự và nhánh kết thúc đúng |

Khi kiểm thử ngoài ví dụ, cần xét overload, các đường gọi nội bộ, phương thức kế thừa, tham số `null`, kết quả `void`, lỗi ngoài dự kiến, tác vụ bất đồng bộ và những trường hợp một điểm bị chọn nhiều lần. Mỗi nhóm kiểm thử phục vụ một trách nhiệm khác nhau; kiểm thử chỉ thấy dòng log xuất hiện chưa chứng minh mọi lời gọi cần bảo vệ đều được bao phủ.

## 10. Tự luyện phân tích

Giả sử mọi thao tác **cập nhật** hồ sơ đều phải kiểm tra quyền và ghi log, còn thao tác **đọc** hồ sơ chỉ cần đo độ trễ. Có người đề xuất “chọn mọi phương thức và ghi toàn bộ tham số”.

Hãy phân tích:

- **Chọn vị trí:** chia đúng phạm vi cập nhật và đọc; tránh tự động gắn chính sách cập nhật cho các phương thức đọc.
- **Truyền ngữ cảnh:** kiểm soát quyền cần danh tính đã xác thực và tài nguyên; log chỉ nhận thông tin được phép công bố; phép đo độ trễ cần tên phép đo và trạng thái, thường không cần toàn bộ hồ sơ.
- **Thực hiện hành vi:** kiểm tra quyền trước khi cập nhật; ghi sự kiện phù hợp ở nhánh thành công/lỗi; đo thời gian ở nhánh kết thúc của thao tác đọc.
- **Kiểm chứng:** thử người không có quyền, đường gọi trực tiếp, thất bại nghiệp vụ, dữ liệu nhạy cảm, kết quả và ngoại lệ trước/sau khi thêm aspect.

**Ghi nhớ:** **pointcut chọn “ở đâu/khi nào”**, **binding cấp “thông tin nào của lần thực thi”**, và **advice quyết định “làm gì với thông tin và luồng thực thi đó”**. Tách rạch ròi ba trách nhiệm giúp một aspect đúng phạm vi, truyền đúng dữ liệu và tạo đúng hành vi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-component-model-008">Luồng tương tác giữa mã nghiệp vụ và một aspect</a>

<details>
<summary>Xem chi tiết</summary>

# Luồng tương tác giữa mã nghiệp vụ và một aspect trong AOP

## 1. Mục tiêu: hiểu điều gì thực sự xảy ra khi có aspect

Trong **Aspect-Oriented Programming (AOP)**, mã nghiệp vụ thực hiện công việc chính như xử lý đơn hàng, tính giá hay lưu dữ liệu. Một **aspect** mô-đun hóa hành vi có tính xuyên suốt, chẳng hạn ghi nhận thao tác, đo thời gian, theo dõi lỗi hoặc áp dụng một chính sách chung ở nhiều điểm trong chương trình.

Điều quan trọng nhất là: **mã nghiệp vụ không nhất thiết gọi trực tiếp aspect**. Thay vào đó, cơ chế AOP xác định các điểm thực thi có thể can thiệp (**join point**), chọn các điểm thích hợp bằng **pointcut** và kết hợp hành vi bổ sung (**advice**) vào luồng thực thi thông qua **weaving** hoặc cơ chế chặn lời gọi tương ứng.

Sơ đồ ý tưởng cho một thao tác đồng bộ:

```text
Bên gọi (caller)
       |
       v
[Cơ chế AOP áp dụng pointcut tại join point]
       |
       v
[Advice trước thao tác]
       |
       v
[Mã nghiệp vụ gốc chạy]
       |
       v
[Advice xử lý kết quả / lỗi / kết thúc]
       |
       v
Bên gọi nhận kết quả hoặc ngoại lệ
```

Sơ đồ này mô tả **thứ tự tương tác có thể quan sát được**, không khẳng định mọi giải pháp AOP đều xây một đối tượng trung gian hoặc tạo ra các bước máy chạy đúng như từng ô trong sơ đồ. Một công cụ có thể biến đổi mã trong lúc biên dịch hoặc nạp mã; công cụ khác có thể chặn lời gọi đi qua proxy. Bản chất cần giữ là hành vi của aspect được kết hợp vào **điểm thực thi đã được chọn**.

Sau bài này, người học cần giải thích được ai gọi ai, lúc nào advice chạy, điều gì trở về bên gọi, cách ngoại lệ đi qua aspect, và tại sao một số đường gọi có thể nằm ngoài phạm vi can thiệp.

## 2. Những thành phần tham gia luồng tương tác

| Thành phần | Trách nhiệm | Có nhất thiết biết về thành phần khác? |
| --- | --- | --- |
| **Caller** | Gọi thao tác và nhận kết quả/ngoại lệ | Biết hợp đồng gọi nghiệp vụ; thường không cần biết aspect |
| **Mã nghiệp vụ (target)** | Thực hiện chức năng cốt lõi | Không cần phụ thuộc trực tiếp vào advice |
| **Join point** | Điểm trong mô hình thực thi có thể bị can thiệp | Là khái niệm của mô hình AOP, không tự là một lời gọi |
| **Pointcut** | Quy tắc lựa chọn những join point cần tác động | Cần khớp đúng loại điểm và điều kiện |
| **Advice** | Hành vi phụ trợ chạy tại điểm được chọn | Có thể quan sát hoặc tác động đến luồng thực thi |
| **Aspect** | Nhóm các pointcut/advice cùng một mối quan tâm | Có thể độc lập với mã nghiệp vụ khi thiết kế đúng |
| **Weaver/interception mechanism** | Kết hợp advice vào đường thực thi | Phụ thuộc công nghệ AOP và thời điểm áp dụng |
| **Join-point context** | Thông tin về điểm/lần thực thi | Các trường được cung cấp khác nhau theo công cụ |

Ví dụ có phương thức `OrderService.placeOrder(orderId)` và chính sách kiểm toán `OrderAudit`:

- `placeOrder` xử lý đơn hàng: kiểm tra dữ liệu, tính tổng và ghi đơn.
- `OrderAudit` ghi nhận thao tác bắt đầu, thành công hoặc thất bại.
- Một pointcut chọn các lần thực thi `placeOrder` cần kiểm toán.
- Cơ chế AOP làm cho advice của `OrderAudit` chạy quanh thao tác được chọn.

Các thành phần này trả lời những câu hỏi riêng: **chương trình đang làm gì** (nghiệp vụ), **can thiệp ở đâu** (join point/pointcut), **làm thêm gì** (advice/aspect), và **việc kết hợp xảy ra bằng cách nào** (weaving/interception).

## 3. Hai thời điểm khác nhau: cấu hình kết hợp và chạy nghiệp vụ

Không nên tưởng tượng pointcut được tạo mới cho từng lệnh nghiệp vụ hoặc aspect chỉ xuất hiện khi caller gọi đến. Có hai nhóm hoạt động cần phân biệt.

### 3.1. Trước hoặc trong quá trình chuẩn bị thực thi

Tùy công cụ, hệ thống thực hiện những việc như:

1. Xác định mô hình join point có thể hỗ trợ: thực thi phương thức, gọi phương thức, truy cập trường, khởi tạo đối tượng hoặc các điểm khác.
2. Đọc/biên dịch quy tắc pointcut, nhận diện điểm có khả năng khớp.
3. Chuẩn bị cách áp dụng advice: biến đổi mã khi biên dịch, can thiệp lúc nạp mã hoặc tạo cấu trúc chặn lời gọi khi chạy.
4. Xác lập thứ tự của các advice/aspect nếu nhiều chính sách cùng khớp một điểm.

Trong một số cơ chế, điều kiện động vẫn phải được đánh giá khi mỗi lần gọi diễn ra. Vì vậy, "đã có cấu hình" không có nghĩa là mọi lần thực thi đều được advice xử lý.

### 3.2. Khi một thao tác thực sự chạy

Khi caller gọi một thao tác, hệ thống có thể:

1. Đi qua một điểm mà cơ chế AOP quan sát hoặc chặn được.
2. Kiểm tra liệu lần thực thi đó thỏa các điều kiện chọn điểm hay không.
3. Chạy advice thích hợp theo thứ tự đã xác định.
4. Tiếp tục thao tác gốc nếu advice cho phép.
5. Xử lý kết quả hoặc ngoại lệ bằng những advice tương ứng.
6. Trả kết quả/ngoại lệ cho caller theo hợp đồng hiệu lực sau can thiệp.

**Phân biệt quan trọng:** mô tả cấu trúc aspect và weaving là chuyện **lắp ghép hành vi**; mỗi lời gọi cụ thể là chuyện **diễn tiến động**. Không nên trộn hai việc này thành một bước "caller gọi aspect".

## 4. Luồng đồng bộ thành công: từ caller đến mã nghiệp vụ rồi quay về

Xét một lần `placeOrder("A-01")` trả về mã xác nhận. Giả sử công cụ cho phép advice trước, advice sau khi trả về và advice cuối cùng.

```text
Caller                  Cơ chế AOP / advice                   OrderService
  |                            |                                   |
  |-- placeOrder("A-01") ------>|                                   |
  |                            |-- before: ghi nhận bắt đầu       |
  |                            |-- gọi thao tác gốc ------------->|
  |                            |                                   |-- kiểm tra đơn
  |                            |                                   |-- xử lý nghiệp vụ
  |                            |<----- trả "OK:A-01" -------------|
  |                            |-- after returning: ghi thành công|
  |                            |-- finally: ghi kết thúc          |
  |<------- "OK:A-01" ----------|                                   |
```

Các bước trên thể hiện một **trình tự minh họa**, không phải quy tắc bắt buộc về thứ tự tuyệt đối giữa tất cả những kiểu advice ở mọi công cụ. Điểm cốt lõi:

- Caller gọi qua đường thực thi có hỗ trợ can thiệp, không tự gọi lệnh `before`.
- Advice trước chạy khi join point phù hợp được chọn.
- Phần nghiệp vụ chỉ chạy nếu đường advice/chặn cho phép tiếp tục.
- Advice sau thành công chỉ có ý nghĩa khi thao tác hoàn thành bình thường.
- Kết quả quay về caller **sau những advice cần thiết**, nên thời điểm caller nhận kết quả có thể muộn hơn thời điểm thân phương thức kết thúc.

Ví dụ này giả định advice chỉ quan sát và không sửa kết quả. Một advice có quyền bao quanh còn có thể thay đổi kết quả hoặc ngăn thao tác gốc, khi cơ chế hỗ trợ và thiết kế cho phép.

### 4.1. “Gọi phương thức” và “thực thi phương thức” có thể là hai join point

Giả sử trong `CheckoutService.checkout()` có lệnh `gateway.charge()`:

```text
checkout():                              gateway.charge():
    gateway.charge() ----------------------> xử lý thanh toán
       ^                                             ^
       |                                             |
   điểm method call                          điểm method execution
   ở phía gọi                               ở phía được gọi
```

Aspect tác động vào **method call** có thể chạy ở phía lời gọi trong `checkout()`; aspect tác động vào **method execution** có thể chạy tại phần thân `charge()`. Hai loại điểm có thể có ngữ cảnh khác nhau (đối tượng bên gọi, đối tượng thực hiện, vị trí mã nguồn, v.v.).

Một công cụ có thể chỉ hỗ trợ một trong hai loại, hoặc chỉ chặn những lời gọi đi qua một lớp trung gian. Vì vậy, sơ đồ tương tác phải nói rõ **đường gọi nào đang được can thiệp** trước khi khẳng định advice chạy ở đâu.

## 5. Advice quyết định cách tiếp tục luồng

Các tên gọi phổ biến dưới đây mô tả vai trò của advice. Không phải công cụ nào cũng hỗ trợ đầy đủ hoặc dùng cùng cú pháp.

| Kiểu advice | Thời điểm | Điều cần lưu ý |
| --- | --- | --- |
| **Before** | Trước thao tác được chọn | Có thể ghi nhận hoặc kiểm tra, nhưng khả năng dừng lời gọi tùy cơ chế |
| **After returning** | Khi hoàn tất bình thường | Có thể quan sát kết quả nếu công cụ cung cấp |
| **After throwing** | Khi thao tác kết thúc bằng ngoại lệ | Cần xác định ngoại lệ được giữ, đổi hay xử lý |
| **After/finally** | Khi kết thúc bất kể thành công hay lỗi | Không có nghĩa mọi kiểu kết thúc bất thường của tiến trình đều được xử lý |
| **Around** | Bao quanh điểm được chọn | Có thể quyết định gọi tiếp, số lần gọi tiếp và cách trả kết quả |

Một dạng mã giả AOP trung lập công cụ:

```text
aspect OperationAudit:
    pointcut tracked = executionsOf(BusinessOperation)

    around tracked(context):
        audit("start", context.name)
        try:
            result = proceed(context)
            audit("success", context.name)
            return result
        catch error:
            audit("failure", context.name, error)
            throw error
        finally:
            audit("finished", context.name)
```

Đây là **mã giả**, không phải Java biên dịch được hay một ngôn ngữ AOP chuẩn chung. `proceed` biểu thị thao tác tiếp tục đến mắt xích kế tiếp hoặc phần nghiệp vụ gốc tùy cấu trúc advice. Ở cách mô tả này, một lượt thành công đi theo thứ tự:

```text
audit(start)
    -> proceed -> business operation -> trả result
audit(success)
audit(finished)
return result
```

Còn lượt lỗi:

```text
audit(start)
    -> proceed -> business operation -> ném error
audit(failure)
audit(finished)
throw error
```

Nên hiểu `finally` ở đây là cơ chế kết thúc theo luồng điều khiển thông thường của lời gọi; không nên suy diễn rằng nó chắc chắn thực thi khi tiến trình bị chấm dứt cưỡng bức hoặc môi trường chạy gặp sự cố không thể phục hồi.

### 5.1. Advice quanh thao tác có thể thay đổi kết quả

Nếu `around` trả về dữ liệu khác kết quả `proceed`, caller nhận dữ liệu đã được biến đổi theo cơ chế chặn, miễn là hợp đồng kiểu/giá trị được thỏa. Nếu advice **không gọi `proceed`**, phần nghiệp vụ gốc có thể bị bỏ qua. Nếu advice **gọi nhiều lần**, phần nghiệp vụ có thể thực thi nhiều lần.

Đây là khác biệt lớn giữa một advice bao quanh và một đoạn ghi log đơn giản. Thao tác có tác dụng phụ như tạo đơn, chuyển tiền hoặc gửi email phải được xem xét kỹ nếu có logic thử lại, bỏ qua hoặc đổi lỗi. Khả năng can thiệp mạnh **không đồng nghĩa với quyền thay đổi nghiệp vụ tùy ý**.

## 6. Ví dụ Java 17 chạy được: mô phỏng luồng advice → nghiệp vụ → advice

Java thông thường không tự động nhận cú pháp pointcut và weaving. Để **quan sát luồng tương tác** bằng JDK 17 thuần, ta dùng `java.lang.reflect.Proxy`: các lời gọi đi qua proxy được chuyển cho một `InvocationHandler`, tại đây viết logic trước/sau và gọi đích gốc.

Ví dụ là **mô phỏng một cơ chế chặn lời gọi**, không phải khai báo aspect theo một ngôn ngữ AOP tổng quát. Nó có phạm vi cụ thể: các phương thức giao diện được gọi **qua proxy**.

Tạo tệp `AspectFlowDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AspectFlowDemo {

    public interface OrderService {
        String place(String orderId);
        String cancel(String orderId);
    }

    public static final class DefaultOrderService implements OrderService {
        @Override
        public String place(String orderId) {
            System.out.println("business:place " + orderId);
            return "placed:" + orderId;
        }

        @Override
        public String cancel(String orderId) {
            System.out.println("business:cancel " + orderId);
            if ("BAD".equals(orderId)) {
                throw new IllegalArgumentException("invalid order");
            }
            return "cancelled:" + orderId;
        }
    }

    public static void main(String[] args) {
        OrderService target = new DefaultOrderService();

        OrderService intercepted = (OrderService) Proxy.newProxyInstance(
                OrderService.class.getClassLoader(),
                new Class<?>[] { OrderService.class },
                (Object proxy, Method method, Object[] arguments) -> {
                    String operation = method.getName();
                    System.out.println("aspect:before " + operation);

                    try {
                        Object result = method.invoke(target, arguments);
                        System.out.println(
                                "aspect:afterReturning " + operation + " -> " + result
                        );
                        return result;
                    } catch (InvocationTargetException ex) {
                        Throwable cause = ex.getCause();
                        System.out.println(
                                "aspect:afterThrowing " + operation +
                                " -> " + cause.getClass().getSimpleName()
                        );
                        throw cause;
                    } finally {
                        System.out.println("aspect:finally " + operation);
                    }
                }
        );

        System.out.println("caller:begin");
        System.out.println("caller:result " + intercepted.place("A-01"));

        try {
            intercepted.cancel("BAD");
        } catch (IllegalArgumentException ex) {
            System.out.println("caller:error " + ex.getMessage());
        }

        System.out.println("caller:end");
    }
}
```

Biên dịch/chạy:

```bash
javac AspectFlowDemo.java
java AspectFlowDemo
```

Đầu ra:

```text
caller:begin
aspect:before place
business:place A-01
aspect:afterReturning place -> placed:A-01
aspect:finally place
caller:result placed:A-01
aspect:before cancel
business:cancel BAD
aspect:afterThrowing cancel -> IllegalArgumentException
aspect:finally cancel
caller:error invalid order
caller:end
```

### 6.1. Đọc kết quả theo từng vai trò

- `caller:begin`: mã bên gọi bắt đầu kịch bản.
- `aspect:before place`: đường gọi đi qua proxy, logic chặn chạy trước khi gọi đích.
- `business:place A-01`: thân phương thức nghiệp vụ thực thi trên `target`.
- `aspect:afterReturning ...`: đích đã trả kết quả bình thường, handler ghi nhận trước khi trao kết quả lại caller.
- `aspect:finally place`: khối dọn dẹp/ghi kết thúc của handler chạy.
- `caller:result placed:A-01`: caller mới nhìn thấy kết quả sau các bước phía trên.
- Với `cancel("BAD")`, nghiệp vụ ném `IllegalArgumentException`. Reflection bọc ngoại lệ đích trong `InvocationTargetException`; handler lấy `getCause()`, ghi nhánh lỗi và **ném lại nguyên nhân** để caller bắt được `IllegalArgumentException`.

Đó là lý do cần phân biệt **ngoại lệ do nghiệp vụ sinh ra** với lớp bọc kỹ thuật của phương tiện gọi phản xạ. Cơ chế AOP thật có thể trình bày/lan truyền ngoại lệ theo những hợp đồng riêng; trong ví dụ này, mã minh họa chủ động giữ ngoại lệ nghiệp vụ.

### 6.2. Điểm tương đồng và khác biệt với AOP đầy đủ

Handler đóng vai trò tương tự một `around advice`: nó chạy trước, thực hiện một lời gọi tiếp tục đến `target`, rồi xử lý kết quả, lỗi và `finally`. Sự tương tác được thể hiện rõ bằng stdout.

Nhưng ví dụ **không có bộ biểu thức pointcut tổng quát** và không thể can thiệp tự động vào mọi loại join point. Mã nghiệp vụ được gọi trực tiếp qua `target` sẽ không đi qua handler. Nếu thêm một phương thức trong `DefaultOrderService` tự gọi phương thức khác của cùng đối tượng, lời gọi nội bộ cũng không mặc nhiên được proxy chặn. Công cụ dùng cách weaving khác có thể cung cấp phạm vi join point rộng hơn.

## 7. Luồng thất bại: ngoại lệ đi qua advice và caller như thế nào?

Một trường hợp quan trọng là thao tác nghiệp vụ ném ngoại lệ. Nếu không có advice xử lý, ngoại lệ đi ngược ngăn xếp lời gọi tới nơi bắt thích hợp. Có aspect thì đường đi có thể gồm các advice quan sát hoặc tác động đến ngoại lệ:

```text
Caller                     Advice / cơ chế AOP               Mã nghiệp vụ
  |                              |                                |
  |-- gọi thao tác -------------->|                                |
  |                              |-- before                      |
  |                              |-- tiếp tục ------------------->|
  |                              |                                |-- ném E
  |                              |<------- E ---------------------|
  |                              |-- after throwing(E)           |
  |                              |-- finally                     |
  |<------------- E -------------|                                |
  |-- bắt E nếu có handler phù hợp|
```

Sơ đồ giả định advice **không nuốt và không chuyển đổi ngoại lệ**. Một advice có thể được thiết kế để dịch ngoại lệ sang kiểu khác hoặc thay bằng kết quả dự phòng, nhưng đó là quyết định có ảnh hưởng đến hợp đồng nghiệp vụ, không phải hệ quả tự nhiên của việc có join point.

Đừng nhầm các khái niệm:

- **After returning** không chạy cho lần gọi đã ném ngoại lệ từ phần được theo dõi, trừ khi một lớp bao bên ngoài thực sự chuyển tình huống thành một kết quả bình thường tại ranh giới mà advice đó quan sát.
- **After throwing** quan sát sự thoát ra bằng ngoại lệ của điểm được chọn; nó không đồng nghĩa với việc chỉ định một trình xử lý bắt ngoại lệ trong mã nghiệp vụ.
- **Finally** dùng để giữ hành vi kết thúc ở cả hai nhánh, trong các tình huống chương trình còn thực thi bình thường.
- Nếu chính advice ném lỗi, lỗi đó có thể che khuất lỗi gốc hoặc thay đổi thứ mà caller nhận được. Vì thế logging và telemetry không nên vô tình làm hỏng kết quả của thao tác chính.

### 7.1. Phân biệt ngoại lệ được bắt bên trong với ngoại lệ thoát khỏi phương thức

Xét đoạn mã:

```java
String calculate() {
    try {
        return parseExternalData();
    } catch (NumberFormatException ex) {
        return "default";
    }
}
```

Đây là **đoạn minh họa**, không phải chương trình Java độc lập; `parseExternalData()` chỉ là ký hiệu cho phương thức phụ thuộc khác. Nếu ngoại lệ xảy ra rồi được xử lý bên trong `calculate()`, một advice quan sát **method execution của `calculate()`** có thể thấy kết quả trả về bình thường `"default"`, chứ không thấy ngoại lệ thoát khỏi điểm thực thi đó. Muốn quan sát chính đoạn bắt ngoại lệ, cần một loại join point hoặc kỹ thuật quan sát khác mà công cụ thực sự hỗ trợ.

## 8. Nhiều aspect cùng áp dụng: tạo thành chuỗi bao quanh

Một thao tác có thể đồng thời chịu chính sách xác thực, kiểm toán và đo thời gian. Nếu các advice đều áp dụng tại cùng điểm, thực thi thường được hiểu bằng hình ảnh các lớp bao quanh:

```text
Caller
  |
  v
Audit.around
  |
  v
Permission.around
  |
  v
Timing.around
  |
  v
BusinessOperation
  |
  v
Timing hoàn tất
  |
  v
Permission hoàn tất
  |
  v
Audit hoàn tất
  |
  v
Caller
```

Trong trường hợp các lớp bao quanh được thiết kế như trên, biểu thức tư duy là:

```text
Audit(Permission(Timing(BusinessOperation)))
```

Lượt thành công có thể có dạng:

```text
audit before
permission before
timer start
business
timer end
permission after
audit after
```

Các dòng chỉ minh họa thứ tự cho **cấu hình đã được ấn định như sơ đồ**. Không thể suy luận thứ tự chỉ từ tên aspect hay thứ tự viết chúng trong tệp nguồn: hệ thống AOP có thể có cơ chế ưu tiên riêng; hãy xác định thứ tự được hỗ trợ và kiểm tra nó trên chương trình.

### 8.1. Thứ tự ảnh hưởng đến chính sách

Giả sử `Permission` từ chối lời gọi trước khi đi tiếp:

- Với `Audit(Permission(Timing(BusinessOperation)))`, audit bên ngoài có thể ghi cả lần truy cập bị từ chối; timer và mã nghiệp vụ không chạy nếu advice phân quyền không tiếp tục.
- Với `Permission(Audit(Timing(BusinessOperation)))`, audit ở bên trong có thể không quan sát được lần gọi bị từ chối từ vòng ngoài.

Tương tự, đặt timer bên ngoài hay bên trong một advice thử lại (retry) quyết định thời gian đo là **toàn bộ các lần thử** hay **từng lần thử**. Thứ tự là một phần của thiết kế ngữ nghĩa, cần được mô tả cùng với chính sách.

### 8.2. Around advice quyết định việc chuyển tiếp

Mã giả:

```text
around operation:
    if not authorized:
        throw AccessDenied
    return proceed()
```

Nếu điều kiện thất bại, `proceed()` không chạy nên nghiệp vụ bị chặn. Còn nếu một advice retry gọi `proceed()` hai lần, đích có thể chạy hai lần. Với thao tác có tác dụng phụ như thu tiền, thêm hàng hoặc cập nhật kho, điều này đòi hỏi ràng buộc về idempotency và tính đúng đắn; không nên tự thêm retry xuyên suốt nếu chưa hiểu hợp đồng nghiệp vụ.

### 8.3. Hai aspect không nhất thiết hoàn toàn độc lập

Dù aspect được mô-đun hóa, chúng vẫn có thể tương tác gián tiếp thông qua thứ tự, ngoại lệ, thông tin ngữ cảnh, trạng thái thread-local hoặc dữ liệu dùng chung. Một aspect biến đổi ngoại lệ có thể khiến aspect bên ngoài quan sát kiểu lỗi khác; một aspect thay đổi tham số có thể đổi điều kiện khớp của logic phía sau trong những mô hình hỗ trợ điều kiện động.

Do đó, ngoài kiểm thử từng aspect, cần kiểm thử **tổ hợp aspect** tại các điểm có nguy cơ thay đổi hành vi.

## 9. Khác biệt giữa luồng logic và cách cơ chế AOP triển khai

Sơ đồ `advice → nghiệp vụ → advice` mô tả **ngữ nghĩa quan sát được**, không quy định có một proxy hoặc một method Java mới. Khi lần theo luồng thực, phân biệt:

| Cơ chế | Đường thực thi khi lời gọi xuất hiện | Điều cần kiểm chứng |
| --- | --- | --- |
| Weaving khi biên dịch / biến đổi bytecode | Caller đi vào mã đã tích hợp advice tại join point được dệt | Class/call site thuộc tập weave; lời gọi nội bộ **có thể** gặp execution advice |
| Weaving lúc nạp lớp | JVM định nghĩa lớp có mã biến đổi | Agent, config và classloader hoạt động đúng **trước khi** nạp lớp cần dệt |
| Proxy/interceptor | `caller → proxy → advice chain → target` | Caller thật sự có dùng proxy, hay dùng target thô/`this.inner()`? |

Luồng qua proxy có thể sinh advice, trong khi lời gọi đến cùng method trên target gốc thì không. Khác biệt ấy nằm ở **biên interception**; weaving tại execution lại có thể thấy cả lời gọi nội bộ. Sơ đồ đường proxy:

```text
caller --> proxy/interceptor --> target
              |                 ^
              +---- advice ------+
```

## 10. Luồng có điều kiện: pointcut chọn điểm nào, vào thời điểm nào?

Điểm có thể can thiệp không đồng nghĩa điểm luôn được advice xử lý. Một pointcut có thể kết hợp:

- **Điều kiện tĩnh:** loại join point, tên/chữ ký phương thức, kiểu lớp, phạm vi mã.
- **Điều kiện động:** tham số, đối tượng thực tế, trạng thái hoặc thông tin ngữ cảnh trong lần gọi, nếu công cụ hỗ trợ.

Ví dụ một quy tắc chọn các thao tác `transfer(amount)` khi `amount` lớn hơn một ngưỡng:

```text
Lần gọi 1: transfer(50)
  -> đi qua vị trí có khả năng là join point
  -> điều kiện động không khớp
  -> không chạy advice tương ứng

Lần gọi 2: transfer(5000)
  -> đi qua cùng loại join point
  -> điều kiện động khớp
  -> chạy advice tương ứng
```

Đây là tình huống **mã giả** và không nói rằng mọi công cụ đều có biểu thức chọn theo giá trị tham số.

Trong một số cách triển khai, việc chọn phần tĩnh được chuẩn bị từ trước, còn điều kiện động được đánh giá khi chương trình chạy. Nếu pointcut quá rộng, việc kiểm tra có thể tốn chi phí và aspect chạm vào thao tác ngoài ý muốn; nếu quá hẹp, thao tác cần kiểm soát có thể bị bỏ sót.

### 10.1. Không nhầm việc pointcut khớp với việc advice chắc chắn hoàn tất

Ngay cả khi điểm đã được chọn, advice có thể ném ngoại lệ trước khi tiếp tục; một advice ngoài có thể từ chối thao tác; môi trường chạy có thể bị gián đoạn. Ngược lại, một lời gọi hợp lệ đi qua nhiều lớp can thiệp nhưng ở mỗi lớp điều kiện chọn điểm có thể khác nhau.

Do đó, chuỗi logic hoàn chỉnh gồm cả **đánh giá điều kiện**, **quyết định tiếp tục**, **thực hiện phần gốc**, và **xử lý kết quả/ngoại lệ**, chứ không chỉ gồm một lệnh "chèn code".

## 11. Luồng lồng nhau, đệ quy và lời gọi nội bộ

### 11.1. Một phương thức nghiệp vụ gọi phương thức nghiệp vụ khác

Ví dụ:

```text
OrderService.placeOrder()
    -> InventoryService.reserve()
    -> PaymentService.charge()
```

Nếu pointcut chọn các phương thức ở cả ba dịch vụ, có thể xuất hiện các đoạn advice lồng nhau:

```text
before(placeOrder)
  business placeOrder
    before(reserve)
      business reserve
    after(reserve)
    before(charge)
      business charge
    after(charge)
after(placeOrder)
```

Chuỗi này **chỉ đúng khi các lời gọi tương ứng đều thực sự đi qua điểm được cơ chế AOP hỗ trợ và khớp pointcut**. Nó không thể được suy ra duy nhất từ sơ đồ gọi phương thức.

### 11.2. Self-invocation có thể thay đổi đường đi

Nếu `placeOrder()` tự gọi `validateInternal()` trên cùng đối tượng, một công cụ chỉ chặn ở proxy phía ngoài có thể ghi advice cho `placeOrder()` nhưng không ghi cho `validateInternal()`. Mã gốc vẫn thực thi `validateInternal()`; chỉ là đường gọi đó **không đi qua điểm chặn**.

Với cơ chế weaving vào thân mã, cùng tình huống có thể được xử lý khác. Khi kiểm thử AOP, cần xác minh cả đường gọi trực tiếp, qua proxy và từ bên trong đối tượng nếu chúng ảnh hưởng đến yêu cầu.

### 11.3. Đệ quy tạo nhiều lần xuất hiện của cùng điểm

Một phương thức gọi chính nó nhiều lần tạo ra những lần xuất hiện join point lồng nhau. Nếu mỗi lần đều được advice bao quanh, thứ tự có thể là:

```text
before(factorial 3)
  before(factorial 2)
    before(factorial 1)
    after(factorial 1)
  after(factorial 2)
after(factorial 3)
```

Tương tự luồng call stack, các phần kết thúc có thể xuất hiện theo thứ tự ngược phần bắt đầu. Advice đo thời gian không nên lưu một biến `startTime` chung duy nhất cho mọi lần gọi đệ quy, vì lần gọi sâu hơn có thể ghi đè mốc của lần ngoài.

## 12. Ngoại lệ, giao dịch và tác dụng phụ trong chuỗi tương tác

Với hành vi xuyên suốt, cần nhìn cả kết quả nghiệp vụ **và** tác động của advice.

### 12.1. Advice xử lý lỗi có thể tạo thêm lỗi

Nếu `auditFailure()` trong advice ném một ngoại lệ mới, caller có thể nhận lỗi của hệ thống audit thay vì lỗi nghiệp vụ ban đầu. Khi đó chính sách báo lỗi phải được thiết kế rõ: cố gắng ghi nhận lỗi theo hướng không làm mất thông tin gốc, hoặc chủ động coi sự cố audit là lý do chặn thao tác nếu yêu cầu hệ thống quy định vậy.

Trong trường hợp advice làm thay đổi lỗi, điều quan trọng là có phương án ghi nhận nguyên nhân gốc để chẩn đoán; không nên vô tình che lỗi bằng các `catch` trống.

### 12.2. Side effect của advice có thể không được hoàn tác

Một advice gửi sự kiện ra ngoài hoặc ghi audit vào hệ thống khác trước khi nghiệp vụ hoàn tất. Nếu nghiệp vụ thất bại, sự kiện đã phát có thể vẫn tồn tại. Dù hai phần được gọi trong cùng luồng, **không có bảo đảm nguyên tử hoặc cùng giao dịch** nếu không thiết kế cơ chế phối hợp cụ thể.

Tương tự, advice `after returning` ghi nhận thành công ở mức lời gọi chưa chắc có nghĩa toàn bộ quy trình phân tán đã cam kết bền vững. Nên định nghĩa "thành công" tại đúng ranh giới có ý nghĩa nghiệp vụ.

### 12.3. Advice retry là một thay đổi luồng lớn

Mã giả:

```text
around operation:
    try:
        return proceed()
    catch TemporaryFailure:
        return proceed()
```

Ở đây phần gốc **có thể chạy hai lần**. Nếu thao tác không an toàn khi lặp, thử lại có thể nhân đôi side effect. Không được xem retry như một log aspect vô hại. Cần có giới hạn lần thử, điều kiện retry, cơ chế idempotency và chính sách lỗi trước khi áp dụng cho nghiệp vụ thực.

## 13. Bất đồng bộ và đa luồng: kết thúc phương thức chưa phải kết thúc công việc

Với hàm đồng bộ, thời điểm trả về của phương thức thường là ranh giới trực tiếp giữa nghiệp vụ và caller. Nhưng một thao tác bất đồng bộ có thể trả về ngay đối tượng biểu diễn công việc đang chạy:

```text
Caller
  |
  |-- startTask() --------> advice trước
  |                        -> tạo / lên lịch công việc
  |<-- Future/handle ------ advice sau thời điểm method return
  |
  |                  ... công việc thật tiếp tục ở thời điểm khác ...
  |
  |<------------------- tín hiệu hoàn tất về sau
```

**Điểm method execution của `startTask()` kết thúc khi phương thức trả về**, không tự động kéo dài đến khi future hoàn tất. Nếu đo khoảng từ advice trước tới advice sau của phương thức, ta có thể chỉ đo **thời gian lên lịch**, không phải **thời gian xử lý toàn tác vụ**.

Muốn đo đến khi tác vụ hoàn tất, thiết kế phải xác định một ranh giới bổ sung: completion callback, completion stage, nơi xử lý kết quả hoặc một điểm khác có thể được can thiệp. Việc chuyển ngữ cảnh trace/audit sang luồng thực thi khác cũng phải có chủ đích.

### 13.1. Trạng thái của advice trong môi trường đồng thời

Nếu nhiều lời gọi được xử lý song song, trạng thái đếm thời gian, số lần thử hay correlation ID phải được gắn với **mỗi invocation hoặc tác vụ** phù hợp. Biến mutable dùng chung thiếu đồng bộ có thể tạo log sai hoặc làm hỏng dữ liệu.

Giả sử hai lượt chạy:

```text
Call A: before(A) ----------- business(A) -------- after(A)
Call B:      before(B) ---- business(B) ---------------- after(B)
```

Thứ tự log liên luồng có thể đan xen; điều đó **không nhất thiết là sai thứ tự nội bộ** của mỗi invocation. Cần phân biệt thứ tự tổng thể và quan hệ nhân quả từng lời gọi; nên ghi correlation ID để gom các bản ghi thuộc cùng lần xử lý.

## 14. Quan sát và gỡ lỗi luồng tương tác

Vì lời gọi đến advice có thể không hiện trực tiếp trong thân phương thức nghiệp vụ, việc gỡ lỗi AOP thường khó hơn lần theo các lời gọi tường minh. Cách tiếp cận hữu ích:

1. Xác định **caller**, điểm gọi và đường đi tới target.
2. Kiểm tra **đúng loại join point** cần can thiệp: call, execution hay loại khác.
3. Xác định **pointcut khớp những vị trí/lần chạy nào**.
4. Kiểm tra **thời điểm và phạm vi weaving**: mã được biên dịch, nạp hoặc đi qua proxy nào.
5. Liệt kê các **advice theo thứ tự hiệu lực**, đặc biệt các advice `around`.
6. Theo dõi `proceed`, kết quả và ngoại lệ: phần nghiệp vụ có chạy một lần, nhiều lần hay không chạy?
7. Đối chiếu **stdout, trace hoặc bài kiểm thử** để xem thứ tự quan sát thực tế có phù hợp hợp đồng.

Ví dụ khi không nhìn thấy log `before charge`, những nguyên nhân khả dĩ gồm: pointcut không khớp, điểm call/execution chưa được hỗ trợ, lớp chưa được weaving, đường gọi đi thẳng vào target, hoặc một advice bên ngoài đã chặn trước khi phần bên trong được tiếp tục.

Không thể kết luận nguyên nhân chỉ bằng cách nhìn tên phương thức. Cần đối chiếu cơ chế triển khai và đường gọi thực tế.

### 14.1. Kiểm thử bằng vết thực thi

Một bộ kiểm thử tốt không chỉ kiểm tra đầu ra nghiệp vụ mà còn kiểm chứng trình tự tại những ranh giới cần thiết:

| Ca kiểm thử | Điều cần kiểm tra |
| --- | --- |
| Lời gọi thành công | Advice bắt đầu, nghiệp vụ, advice kết thúc, caller nhận kết quả đúng thứ tự |
| Nghiệp vụ ném lỗi | Advice đường lỗi, cleanup và ngoại lệ tới caller đúng hợp đồng |
| Pointcut không khớp | Không có advice ngoài ý muốn |
| Aspect ngoài từ chối | Nghiệp vụ không chạy, các advice được phép vẫn xử lý đúng |
| Hai aspect cùng khớp | Thứ tự và quan hệ bao quanh đúng |
| Lời gọi nội bộ | Phạm vi interception/weaving đúng như thiết kế |
| Hai lời gọi đồng thời | Ngữ cảnh từng invocation độc lập |
| Thao tác bất đồng bộ | Ranh giới đo và tín hiệu hoàn tất đúng |
| Retry | Số lần chạy phần nghiệp vụ và side effect đúng mong đợi |

Kiểm thử **đường không được can thiệp** cũng cần thiết như kiểm thử đường phải được can thiệp. Pointcut quá rộng có thể vô tình áp dụng cùng chính sách cho một thao tác không thuộc phạm vi.

## 15. Những hiểu nhầm thường gặp

**“Business method tự động gọi aspect.”** Không phải mô hình chung. Thông thường advice được kết hợp bởi cơ chế AOP tại điểm được chọn; thân nghiệp vụ có thể hoàn toàn không tham chiếu aspect.

**“Aspect luôn chạy trước nghiệp vụ.”** Chỉ đúng với phần trước của một số kiểu advice. Có advice xử lý sau khi trả về, sau khi ném lỗi hoặc bao quanh cả thao tác.

**“Aspect chỉ quan sát, không thể đổi kết quả.”** Sai với những mô hình hỗ trợ `around` có quyền bỏ qua, gọi lại, biến đổi kết quả hoặc ngoại lệ. Việc có nên thực hiện quyền đó là câu hỏi thiết kế riêng.

**“Một phương thức có tên khớp thì mọi lời gọi đều qua aspect.”** Chưa chắc. Cần kiểm tra loại join point, điều kiện pointcut và phạm vi cơ chế weaving/interception.

**“Thân nghiệp vụ kết thúc thì caller lập tức nhận kết quả.”** Nếu có advice sau thao tác, caller chỉ nhận kết quả sau khi các bước cần thiết hoàn tất, trừ cách triển khai đặc biệt khác. Với tác vụ bất đồng bộ, còn phải tách thời điểm trả về handle và thời điểm hoàn thành công việc.

**“Nhiều aspect có thứ tự hiển nhiên.”** Không. Thứ tự có thể thay đổi kết quả, log, phân quyền và số liệu đo. Cần cấu hình và kiểm thử theo cơ chế cụ thể.

**“Ghi audit trong advice bảo đảm cùng giao dịch với nghiệp vụ.”** Không tự động. Tính nguyên tử phụ thuộc ranh giới giao dịch và hệ thống ghi dữ liệu thực tế.

**“Có proxy thì mọi lời gọi tới target đều đi qua proxy.”** Sai. Lời gọi trực tiếp trên target hoặc lời gọi nội bộ có thể bỏ qua proxy; những cách weaving khác có khả năng khác.

## 16. Bài tập đọc luồng

### Bài 1. Thành công và lỗi

Giả sử một `around advice` ghi `start`, gọi `proceed`, ghi `success` khi trả về bình thường, ghi `failure` nếu có ngoại lệ thoát ra và luôn ghi `finish` trong `finally`.

- Nếu thao tác trả `42`, thứ tự mong đợi là: `start → business → success → finish → caller nhận 42`.
- Nếu thao tác ném `IllegalStateException` và advice ném lại lỗi gốc, thứ tự là: `start → business → failure → finish → caller nhận lỗi`.

Chú ý phân biệt `success` với việc một ngoại lệ đã được **bắt và xử lý bên trong** nghiệp vụ trước khi phương thức trả về bình thường.

### Bài 2. Hai lớp advice

Có chuỗi `Logging(Authorization(Operation))`. Nếu `Authorization` từ chối mà không tiếp tục, phần nào có thể chạy? 

`Logging` bên ngoài vẫn có thể ghi bắt đầu và kết thúc; `Operation` không chạy; phần sau của `Authorization` sẽ tùy cách advice đó được viết. Nếu đảo thành `Authorization(Logging(Operation))`, lần từ chối trước khi chuyển tiếp có thể khiến `Logging` không được thực thi.

### Bài 3. Lời gọi nội bộ

Một đối tượng `Target` có `outer()` gọi `inner()`. Caller gọi `proxy.outer()` và proxy chỉ chặn lời gọi giao diện đi qua nó. Có chắc advice cho `inner()` sẽ chạy không?

**Không.** Nếu `outer()` chạy trên target và gọi `inner()` trực tiếp, lời gọi đó có thể không qua proxy. Cần dựa trên cơ chế AOP cụ thể mới biết mức độ can thiệp.

### Bài 4. Đo một tác vụ bất đồng bộ

Một advice quanh `startExport()` dừng đồng hồ ngay khi phương thức trả về `Future`. Nó có đo toàn bộ thời gian xuất dữ liệu không?

**Chưa chắc.** Nó có thể chỉ đo thời gian tạo/lên lịch tác vụ. Để đo đến khi xuất dữ liệu hoàn tất phải theo dõi điểm completion phù hợp và chuyển ngữ cảnh đo qua vòng đời tác vụ.

### Bài 5. Điểm gọi và điểm thực thi

`checkout()` gọi `gateway.pay()`; cùng `pay()` còn được gọi từ `retryPayment()`. Nếu muốn ghi log theo **vị trí lời gọi**, chọn cách nghĩ về **method call join point**. Nếu muốn ghi log cho **mọi lần thực thi thực tế của thân `pay()` trong phạm vi weaving**, chọn cách nghĩ về **method execution join point**. Công cụ thực tế có hỗ trợ được cả hai hay không phải được xác minh riêng.

## 17. Tổng kết mô hình tư duy

Mỗi lần theo dõi tương tác giữa nghiệp vụ và aspect, hãy trả lời lần lượt:

1. **Ai khởi phát?** Caller gọi một thao tác theo hợp đồng nghiệp vụ.
2. **Điểm nào bị can thiệp?** Một join point được hệ thống AOP hỗ trợ.
3. **Vì sao điểm đó được chọn?** Pointcut khớp điều kiện cần thiết.
4. **Hành vi nào chạy?** Advice do aspect định nghĩa, với thứ tự đã xác định.
5. **Nghiệp vụ có được thực thi không?** Phụ thuộc chuỗi advice và việc tiếp tục lời gọi.
6. **Điều gì quay về caller?** Kết quả hoặc ngoại lệ sau các xử lý của advice.
7. **Đường nào nằm ngoài phạm vi?** Phụ thuộc loại join point, weaving và đường gọi cụ thể.

**Luồng AOP là sự kết hợp có kiểm soát giữa xử lý nghiệp vụ và các chính sách xuyên suốt tại những điểm thực thi được lựa chọn.** Thiết kế tốt giữ trách nhiệm nghiệp vụ rõ ràng, làm cho điều kiện can thiệp kiểm chứng được, bảo toàn kết quả/ngoại lệ khi cần và thể hiện rành mạch thứ tự cũng như giới hạn của từng aspect.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="aop-component-model-009">Khả năng của mô hình AOP phụ thuộc ngôn ngữ và công cụ</a>

<details>
<summary>Xem chi tiết</summary>

# Khả năng của mô hình AOP phụ thuộc ngôn ngữ và công cụ

## 1. Điều cần hiểu trước tiên

**Aspect-Oriented Programming (AOP)** là mô hình tổ chức những hành vi xuyên suốt nhiều phần chương trình thành các **aspect**, kết hợp hành vi ấy vào những **điểm thực thi** được chọn. Những khái niệm như *join point*, *pointcut*, *advice*, *aspect* và *weaving* giúp ta mô tả mục tiêu. Tuy nhiên, **không có bảo đảm rằng mọi ngôn ngữ hay mọi công cụ AOP đều hiện thực đầy đủ cùng một mô hình**.

Chẳng hạn, hai công cụ cùng cho phép “chạy mã trước một phương thức” nhưng có thể khác biệt căn bản:

- Một công cụ chỉ can thiệp khi **caller gọi qua proxy**; đối tượng gốc được gọi trực tiếp sẽ không bị chặn.
- Một công cụ dệt hành vi vào **bytecode của phương thức**, nên có thể nhìn thấy một số đường thực thi mà proxy không quan sát được.
- Một công cụ chỉ hỗ trợ **method execution**; một công cụ khác có thể hỗ trợ thêm **method call**, truy cập trường, constructor hoặc xử lý ngoại lệ.
- Một công cụ nhận pointcut tĩnh tại build time; một công cụ khác chọn điểm dựa trên thông tin runtime.
- Một công cụ cho phép advice chỉ quan sát; công cụ khác cho phép thay đối số, kết quả, exception hoặc quyết định có tiếp tục target hay không.

Đây là sự khác nhau về **năng lực thực tế**, không chỉ là khác tên API. Muốn dự đoán đúng chương trình, cần xem chính xác ngôn ngữ, công cụ, phiên bản, thời điểm weaving, cách cấu hình và đường gọi.

**Mục tiêu bài học:** Sau khi học, bạn có thể phân tích một cơ chế AOP bất kỳ bằng bộ câu hỏi nhất quán: nó quan sát được điểm gì, chọn điểm bằng gì, advice làm được gì, kết hợp mã thế nào, giới hạn tại đâu, và ta chứng minh hành vi thực tế ra sao.

## 2. Ba tầng quyết định khả năng AOP

### 2.1. Tầng ngôn ngữ lập trình

Đặc tính ngôn ngữ tạo ra cơ sở cho việc can thiệp:

- **Kiểu và dispatch:** Interface, kế thừa, virtual dispatch, phương thức tĩnh, private/final, extension method, overload và generics quyết định vị trí một lớp bọc có thể tác động.
- **Reflection và metadata:** Có lấy được chữ ký, annotation, kiểu runtime, đối số hay không? Annotation có tồn tại ở runtime hay chỉ ở giai đoạn biên dịch?
- **Cơ chế mở rộng:** Ngôn ngữ có decorator, macro, metaprogramming, compile-time transform, dynamic proxy hoặc API thao tác code/bytecode không?
- **Mô hình lỗi:** Exception, kết quả biểu diễn lỗi, cancellation và cleanup có được advice quan sát hoặc thay đổi không?
- **Concurrency:** Thread, coroutine, task bất đồng bộ, callback, event loop và cách truyền context ảnh hưởng trực tiếp đến ý nghĩa trước/sau một join point.
- **Runtime và mã biên dịch:** Bytecode có thể biến đổi khác với binary native hoặc code bị tối ưu/inlining. Một hook ở nguồn chưa chắc tồn tại tương đương tại runtime.

Ngôn ngữ cho phép một kỹ thuật **không có nghĩa công cụ cụ thể đã triển khai kỹ thuật ấy**. Ví dụ, Java có annotation và reflection, nhưng riêng việc khai báo annotation không tự chạy advice.

### 2.2. Tầng công cụ hoặc framework AOP

Công cụ định nghĩa **join-point model**: tập điểm thực thi có thể chọn và hình thức chọn. Nó cũng quyết định loại advice, cách truyền ngữ cảnh, thứ tự ưu tiên, khả năng tạo aspect theo đối tượng hoặc luồng điều khiển, cùng cơ chế weaving.

Không nên suy luận từ một khái niệm tổng quát ra chức năng cụ thể:

- “Có pointcut” không kéo theo “có thể chọn mọi field access”.
- “Có around advice” không tự bảo đảm được đổi đối số hoặc gọi tiếp nhiều lần.
- “Có marker/annotation” không tự bảo đảm matcher kiểm tra đúng nơi marker được khai báo.
- “Có proxy” không đồng nghĩa “can thiệp được các lời gọi nội bộ”.
- “Có weaving” không tự bảo đảm tương thích mọi bytecode hoặc mọi môi trường nạp lớp.

Cần tra cứu khả năng theo **bản triển khai đang dùng**, rồi xác nhận lại bằng thử nghiệm nhỏ.

### 2.3. Tầng cấu hình và môi trường thực thi

Cùng một công cụ còn có thể hành xử khác nhau theo cấu hình:

- Aspect đã được đăng ký và kích hoạt ở đúng module chưa?
- Các lớp cần can thiệp có đi qua classloader/transformer được cấu hình không?
- Artifact chạy có phải bản đã được weave đúng phiên bản không?
- Runtime có cho phép instrumentation, mở module hoặc truy cập metadata cần thiết không?
- Proxy có được trao cho caller, hay caller vẫn giữ tham chiếu tới target?
- Thứ tự advice, cơ chế khởi tạo và trạng thái aspect có được quy định không?

Do đó, tuyên bố “AOP hỗ trợ X” chỉ đáng tin khi hoàn chỉnh thành: **“Công cụ T, với cấu hình C và môi trường R, hỗ trợ X ở những điểm Y, với giới hạn Z.”**

## 3. Bản đồ khả năng theo từng thành phần mô hình AOP

| Thành phần | Câu hỏi về khả năng | Khác biệt thường gặp |
| --- | --- | --- |
| **Join point** | Những sự kiện nào có thể bị can thiệp? | Method execution, method call, constructor, field get/set, exception handler hoặc chỉ lời gọi qua wrapper. |
| **Pointcut** | Chọn các sự kiện ấy bằng thông tin gì? | Chữ ký, tên, package, kiểu, marker, đối tượng runtime, context luồng điều khiển hoặc predicate tùy biến. |
| **Advice** | Chạy khi nào và can thiệp sâu tới đâu? | Before, after-returning, after-throwing, finally, around; quan sát thuần hoặc được phép thay hợp đồng. |
| **Join-point context** | Advice nhìn thấy gì? | Tên phương thức, target, caller, đối số, result, exception, metadata, context runtime. |
| **Weaving** | Hành vi được nối vào chương trình ở đâu? | Proxy runtime, biến đổi nguồn, biến đổi bytecode khi build, khi load, instrumentation runtime. |
| **Aspect lifecycle** | Có bao nhiêu instance, trạng thái sống bao lâu? | Singleton, mỗi target, mỗi luồng/ngữ cảnh hoặc mô hình lifecycle riêng. |
| **Precedence** | Nhiều aspect khớp cùng điểm thì chạy thứ tự nào? | Thứ tự được khai báo tường minh, mặc định của công cụ hoặc thứ tự không được bảo đảm. |
| **Introduction** | Có thể mở rộng kiểu/interface/member? | Một số công cụ có thể giới thiệu hành vi/thành viên; nhiều cơ chế proxy đơn giản không có. |
| **Tooling** | Debug/inspect/verify được mã đã can thiệp không? | Khác nhau về source map, bytecode inspection, log matching, trace và tích hợp trình biên dịch. |

Hai công cụ có thể tương đương ở hàng “Advice: before” nhưng khác hoàn toàn ở hàng “Join point: field access”. Khi đánh giá AOP, phải đi theo **cột khả năng cụ thể**, không theo nhãn sản phẩm.

## 4. Join-point model: khác biệt nền tảng nhất

### 4.1. Method execution và method call không giống nhau

Xét mã minh họa:

```java
class ReportService {
    void generate() {
        save();
    }
    void save() {
        // lưu báo cáo
    }
}
```

- **Execution join point** của `save()`: sự kiện khi phần thân `save` đang được thực thi.
- **Call join point** của `save()`: điểm mã gọi `save()`, ở đây nằm trong `generate()`.

Một số ngôn ngữ/công cụ phân biệt được hai loại; một số chỉ cung cấp một loại hoặc một abstraction khác. Nếu advice gắn ở phía **caller**, ngữ nghĩa và số điểm khớp có thể khác với advice gắn ở **callee**. Ví dụ, nhiều vị trí gọi cùng `save()` tạo ra nhiều vị trí *call*, trong khi định nghĩa *execution* trỏ vào nơi phương thức chạy.

Không được viết quy tắc pointcut dựa trên giả định rằng hai sự kiện là một.

### 4.2. Không phải cơ chế nào cũng thấy field, constructor và exception handler

Một số mô hình join point có thể biểu diễn:

- Đọc hoặc ghi trường.
- Khởi tạo đối tượng và/hoặc thực thi constructor.
- Khởi tạo lớp.
- Bắt ngoại lệ tại handler.
- Thực thi advice hoặc những sự kiện đặc thù khác.

Nhưng một **dynamic proxy chỉ triển khai interface phương thức** không tự chặn phép gán `object.field = value`, truy cập `object.field`, lời gọi constructor hoặc các lệnh trong thân phương thức của target. Nếu ứng dụng cần các loại điểm ấy, cần tìm cơ chế hỗ trợ tương ứng; cũng phải đánh giá các rủi ro của việc biến đổi code.

### 4.3. Các điểm bị tối ưu hoặc không tồn tại theo cùng cách

Ngôn ngữ động có thể cho phép thay thuộc tính của object hoặc bọc hàm lúc chạy, nhưng đóng gói, built-in/native function, module scope hay cơ chế binding có thể làm một số đường gọi không đi qua wrapper. Ngôn ngữ biên dịch có thể có inlining, generic specialization, native code và các hạn chế biến đổi binary.

Vì thế câu hỏi đúng không phải “ngôn ngữ này có AOP không?”, mà là **“Với điểm thực thi đang cần, công cụ này đặt hook được ở đâu và bằng chứng nào xác nhận hook được kích hoạt?”**

## 5. Pointcut: cú pháp, khả năng biểu đạt và thời điểm đánh giá

Một pointcut có thể sử dụng các loại tiêu chí:

| Tiêu chí | Ví dụ ý tưởng | Điều cần xác minh |
| --- | --- | --- |
| Chữ ký/kiểu tĩnh | Chọn `public` method ở một interface. | Có nhận diện overload, generic bridge, inherited method không? |
| Namespace/package/module | Chọn nhóm thao tác trong một module. | Có thể loại trừ module chứa chính logging/advice để tránh recursion không? |
| Metadata | Chọn method được gắn marker `@Observed`. | Metadata có runtime retention? Được đặt trên interface hay implementation? Có kế thừa không? |
| Đối tượng runtime | Chọn lời gọi trên object thuộc một subtype. | Công cụ hỗ trợ binding kiểu runtime hay chỉ xét chữ ký tĩnh? |
| Tham số/context | Chỉ chọn lời gọi ở một ngữ cảnh. | Có thể lấy đúng đối số; đánh giá predicate trước hay sau biến đổi? |
| Control flow | Chọn sự kiện xảy ra trong phạm vi một lời gọi gốc. | Có hỗ trợ control-flow pointcut và ngữ nghĩa thread/async tương ứng không? |

Ví dụ mô tả **giả mã**, không phải cú pháp được bảo đảm của bất cứ engine nào:

```text
pointcut:
    execution(public method in package "storage")
    AND hasMarker("Observed")
    AND NOT insidePackage("instrumentation")
```

Ở công cụ chỉ có API wrapper, bạn phải tự hiện thực predicate để chọn những method đi qua wrapper. Ở công cụ có ngôn ngữ pointcut, có thể khai báo một biểu thức tương đương nếu đầy đủ loại matcher cần dùng.

**Tĩnh và động:** Một số phần điều kiện có thể được xác định khi build/weave, nhưng một điều kiện phụ thuộc đối tượng hoặc đối số phải được kiểm tra tại runtime. Phân tách này ảnh hưởng đến khả năng tối ưu, chi phí mỗi lần gọi và cách đọc biểu thức.

**Độ phủ:** Cần phân biệt “rule chọn đúng danh sách method” với “mọi lời gọi tới method đó đều đi qua hook”. Đặc biệt ở mô hình proxy, matching chính xác vẫn chưa đủ nếu call path không qua proxy.

## 6. Advice: tên giống nhau nhưng quyền can thiệp khác nhau

Các loại advice quen thuộc gồm:

1. **Before:** chạy trước điểm được chọn.
2. **After returning:** chạy khi điểm đó trả về bình thường.
3. **After throwing:** chạy khi điểm đó kết thúc bởi lỗi/exception phù hợp.
4. **After/finally:** xử lý khi kết thúc qua cả đường thành công và lỗi.
5. **Around:** bao quanh quá trình thực thi, thường có cách tiếp tục vào phần bên trong.

Một số công cụ không có đủ năm loại dưới đúng những tên ấy; có thể chỉ cho phép wrapper/around rồi yêu cầu người dùng tự hiện thực hành vi trước, sau và xử lý lỗi.

### 6.1. `proceed` là khả năng cần khảo sát

Trong mô hình around, thao tác thường gọi là `proceed` chuyển quyền thực thi cho phần bên trong chuỗi advice/target. Nhưng phải kiểm tra:

- Có bắt buộc gọi một lần hay được gọi 0/nhiều lần?
- Có được thay đối số khi gọi tiếp hay không?
- Có thể sửa hoặc thay giá trị trả về không?
- Exception có được truyền lại nguyên dạng hay bị bọc?
- Nếu advice tự lỗi, caller quan sát loại lỗi nào?

Ví dụ giả mã:

```text
around():
    started = monotonicClock()
    try:
        return proceed()
    finally:
        reportDuration(monotonicClock() - started)
```

Đây là mô tả ý tưởng. Tên hàm, cách nhận result/exception, việc truyền tham số và `finally` thực tế tùy công cụ/language. Chỉ đo thời gian một cách chính xác khi hook bao quanh đúng **khoảng công việc cần đo**.

### 6.2. Tại sao khả năng “gọi nhiều lần” đáng quan tâm?

Nếu advice retry bằng cách gọi tiếp hai lần, một thao tác tạo hóa đơn có thể tạo **hai hóa đơn**. Kỹ thuật AOP có thể thực hiện được việc này không đồng nghĩa nó đúng về nghiệp vụ. Chỉ cho phép retry khi đã xác định idempotency, timeout tổng, loại lỗi thử lại được và cách chống trùng lặp.

### 6.3. Binding context không đồng nhất

Một số API cung cấp đối số, tên method, đối tượng receiver, caller, annotation, result và exception; có API chỉ cung cấp callable để tiếp tục. Dữ liệu thu thập ở lúc **call** có thể khác dữ liệu ở lúc **execution**. Chỉ nên dùng những trường context mà công cụ thực sự có hợp đồng hỗ trợ.

## 7. Weaving: cùng aspect, đường chạy có thể khác nhau

### 7.1. Bọc lời gọi bằng proxy hoặc decorator

Sơ đồ:

```text
client -> proxy/wrapper -> [advice] -> target.method()
client -----------------------------> target.method()
                                  (bỏ qua proxy)
```

Cơ chế này phù hợp khi có biên đối tượng rõ và caller nhận proxy thay cho target. Với JDK dynamic proxy, proxy cài các **interface**; callback handler quan sát những lời gọi được phát qua proxy. Nó không sửa class gốc, không tự thay đổi mọi tham chiếu tới target, và không tự can thiệp lời gọi `this.otherMethod()` bên trong target.

Proxy dựa trên lớp có phạm vi khác vì thường phụ thuộc khả năng tạo subtype/override; `final`, `private` và constructor cần được xét riêng theo cơ chế cụ thể.

### 7.2. Dệt vào source hoặc bytecode

Một công cụ weaving có thể kết hợp advice vào mã tại compile time, sau compile, tại lúc load class, hoặc thông qua một cơ chế instrumentation runtime. Thời điểm khác nhau dẫn tới cách build/test/deploy/debug khác nhau.

Các câu hỏi kiểm chứng:

- File nguồn/bytecode nào đã được weave?
- Module và classloader nào nằm trong phạm vi?
- Có tình huống class đã được load trước khi agent/transformer kích hoạt không?
- Mã đã tối ưu, mã sinh tự động và các dependency có nằm trong vùng hỗ trợ không?
- Có cách inspect bytecode hoặc xác nhận danh sách join point thật sự được chọn không?
- Thay phiên bản JDK/compiler/tool có thay tập điểm hỗ trợ không?

Không nên coi weaving tự động “mạnh hơn trên mọi phương diện”. Nó có thể cho phạm vi join point rộng nhưng chi phí build, độ khó gỡ lỗi, yêu cầu tương thích bytecode và nguy cơ thay đổi nhiều module cũng lớn hơn.

### 7.3. Metadata không tự thực thi

Annotation/attribute chỉ là thông tin. Để tạo hành vi, cần **một thành phần đọc metadata**, chọn điểm can thiệp và gắn advice. Trên Java, annotation processing tiêu chuẩn chủ yếu xử lý thông tin trong quá trình biên dịch và có thể sinh file; nó **không mặc nhiên viết lại phần thân mọi phương thức**. Muốn thực hiện kiểu đó phải dùng cơ chế biến đổi hoặc weaving phù hợp.

## 8. So sánh các họ kỹ thuật

Bảng sau là **phân loại kỹ thuật**, không phải lời hứa mọi thư viện thuộc một họ đều hỗ trợ toàn bộ thuộc tính.

| Họ triển khai | Điểm thường nhìn thấy | Cơ chế | Giới hạn nổi bật |
| --- | --- | --- | --- |
| **Wrapper/decorator tường minh** | Lời gọi qua wrapper. | Object gọi object khác với logic trước/sau. | Chỉ áp dụng nơi chủ động dùng wrapper. |
| **Dynamic proxy theo interface** | Lời gọi các method interface qua proxy. | Runtime tạo đối tượng proxy và handler. | Target trực tiếp và self-call không tự đi qua handler; phụ thuộc interface. |
| **Class proxy/subclass** | Lời gọi có thể dispatch qua method được ghi đè/chặn. | Lớp đại diện hoặc subclass. | Phụ thuộc override, visibility, finality, construction và khả năng runtime sinh lớp. |
| **Source/AST transformation** | Những cấu trúc nguồn công cụ có thể nhận diện. | Biến đổi chương trình trước/sát lúc biên dịch. | Ràng buộc compiler, syntax và pipeline build. |
| **Bytecode weaving** | Những join point có thể biểu diễn trong bytecode và được công cụ hỗ trợ. | Chỉnh bytecode trước hoặc trong quá trình nạp/chạy. | Tương thích định dạng bytecode, classloader, agent/build và debugging. |
| **Runtime instrumentation** | Những điểm runtime mà bộ instrumenter có thể can thiệp. | Transformer/hook tại runtime. | Tính khả dụng của API instrumentation; có thể chịu ràng buộc về quyền, startup và tải lớp. |
| **Language-level decorator/macro/metaprogramming** | Các function, method hoặc cú pháp ngôn ngữ cho phép bọc/biến đổi. | Mở rộng/ngữ nghĩa tích hợp của ngôn ngữ hoặc thư viện. | Độ phủ chịu giới hạn binding, đặc tính gọi hàm và khả năng truy cập implementation. |

**Cẩn thận với từ “AOP”:** Một decorator hoặc proxy tự viết có thể hiện thực một phần ý tưởng AOP nhưng không tự trở thành hệ thống pointcut/weaving hoàn chỉnh. Ngược lại, công cụ trình biên dịch có thể hỗ trợ join point ít thấy ở proxy mà không cho phép thay đổi mọi hành vi runtime.

## 9. Ví dụ Java 17 — chứng minh một khả năng và giới hạn của JDK dynamic proxy

Chương trình dùng Java chuẩn: interface `TaskApi`, target `TaskService`, annotation `@Tracked` ở method interface và `InvocationHandler`. Handler chỉ ghi advice cho lời gọi phương thức có marker. Cách triển khai này minh họa **pointcut rất nhỏ bằng predicate Java** và **before/after** trên method invocation qua proxy. Đây **không phải** một bộ engine AOP tổng quát.

Tạo tệp **`AopCapabilityDemo.java`**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AopCapabilityDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Tracked { }

    interface TaskApi {
        @Tracked
        String run(String id);
        String status();
    }

    static final class TaskService implements TaskApi {
        @Override
        public String run(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id required");
            }
            System.out.println("TARGET run " + id);
            return "DONE " + id;
        }

        @Override
        public String status() {
            System.out.println("TARGET status");
            return "READY";
        }
    }

    static TaskApi monitored(TaskApi target) {
        return (TaskApi) Proxy.newProxyInstance(
                TaskApi.class.getClassLoader(),
                new Class<?>[] { TaskApi.class },
                (proxy, method, args) -> invoke(target, method, args)
        );
    }

    static Object invoke(TaskApi target, Method method, Object[] args)
            throws Throwable {
        boolean selected = method.isAnnotationPresent(Tracked.class);
        if (selected) {
            System.out.println("ADVICE before " + method.getName());
        }
        try {
            Object result = method.invoke(target, args);
            if (selected) {
                System.out.println("ADVICE returning " + method.getName());
            }
            return result;
        } catch (InvocationTargetException ex) {
            if (selected) {
                System.out.println("ADVICE throwing " + method.getName());
            }
            throw ex.getCause();
        }
    }

    public static void main(String[] args) {
        TaskApi target = new TaskService();
        TaskApi proxy = monitored(target);

        System.out.println("CALLER " + proxy.run("T-17"));
        System.out.println("CALLER " + proxy.status());

        try {
            proxy.run("");
        } catch (IllegalArgumentException ex) {
            System.out.println("CALLER error " + ex.getMessage());
        }

        System.out.println("CALLER " + target.run("DIRECT"));
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac AopCapabilityDemo.java
java AopCapabilityDemo
```

Đầu ra chuẩn:

```text
ADVICE before run
TARGET run T-17
ADVICE returning run
CALLER DONE T-17
TARGET status
CALLER READY
ADVICE before run
ADVICE throwing run
CALLER error id required
TARGET run DIRECT
CALLER DONE DIRECT
```

### Những điều chương trình **thật sự** chứng minh

1. **Có can thiệp phương thức ở biên interface:** `proxy.run(...)` đi qua handler. Đây là khả năng của đối tượng do `Proxy.newProxyInstance` tạo.
2. **Có chọn điểm bằng metadata:** Handler nhận `Method` từ lời gọi interface và kiểm tra `@Tracked`. Phương thức `status()` vẫn được chuyển qua handler nhưng không kích hoạt các dòng `ADVICE`.
3. **Có quan sát đường thành công/lỗi:** Khi `run` trả về bình thường, handler in `returning`; khi target ném `IllegalArgumentException`, reflective invocation bọc nó vào `InvocationTargetException`; handler gỡ nguyên nhân rồi in `throwing`, caller nhận lỗi gốc.
4. **Gọi trực tiếp bỏ qua cơ chế:** `target.run("DIRECT")` chạy nghiệp vụ mà không có `ADVICE`. Điều này chứng minh đường gọi quan trọng hơn việc phương thức interface có annotation.
5. **Marker nằm trên interface:** Nếu chỉ chuyển annotation sang implementation, biểu thức `method.isAnnotationPresent(Tracked.class)` trong ví dụ sẽ không tự tìm annotation trên phương thức triển khai. Điểm nào được chọn phụ thuộc đúng cách viết matcher.

### Những điều ví dụ **không** chứng minh

- Không chứng minh can thiệp được constructor, field access, `private` method hoặc internal call trên `this`.
- Không chứng minh được capabilities của class proxy hay bytecode weaving.
- Không có pointcut DSL, `proceed` tổng quát, join point context hoàn chỉnh, aspect precedence hoặc lifecycle đa dạng.
- Không cung cấp audit bền vững, bảo mật, retry an toàn hay tracing xuyên tác vụ async.
- Không chứng minh overhead đáng kể hay nhỏ; cần đo trên workload tương ứng.

**Bài thực hành có ích:** Thêm một method không có marker, gọi qua proxy và kiểm tra không in advice; thêm marker vào method interface rồi gọi lại; thử gọi target trực tiếp. Từ ba phép thử, hãy viết **ma trận positive/negative** cho đúng đường gọi đang dùng.

## 10. Sự khác biệt trong kiểu, kế thừa và metadata

### 10.1. Visibility, finality và kế thừa

Với cơ chế tạo subclass để override, những method không thể override sẽ không thể bị chặn theo cách override thông thường. Với proxy interface, phạm vi là những method biểu diễn được trên các interface proxy cài. Với weaving bytecode, tập điểm can thiệp có thể khác; **đừng đem quy tắc của một họ triển khai áp vào họ khác**.

Ở Java, một lời gọi có thể đến method kế thừa, method bị override, bridge method do compiler sinh ra hoặc overload khác. Metadata gắn ở đâu, matcher nhìn vào đối tượng `Method` nào và JVM dispatch tới body nào là các câu hỏi khác nhau.

### 10.2. Annotation retention và nơi khai báo

Một marker được dùng để chọn pointcut tại runtime chỉ hữu dụng nếu metadata cần thiết còn có thể đọc tại runtime hoặc đã được công cụ xử lý ở thời điểm trước đó. Trên Java, `RetentionPolicy.RUNTIME` cho phép API reflection đọc annotation ở runtime; `SOURCE` và `CLASS` có ý nghĩa khác. Việc một annotation đánh dấu method trên interface **không bảo đảm** tự xuất hiện như một annotation trên implementation khi reflection được gọi ở đối tượng khác. Công cụ có thể tự xây matcher mạnh hơn, nhưng đó là **quyết định công cụ**.

### 10.3. Generic và method bridge

Compiler có thể tạo phương thức cầu nối (*bridge*) để duy trì dispatch sau type erasure. Một matcher dựa trên chữ ký thô có thể nhìn thấy method khác với chữ ký source mà người viết tưởng tượng. Công cụ thường có chiến lược riêng để xử lý bridge, synthetic, inherited và overridden methods. Khi phạm vi can thiệp quan trọng, nên thử tình huống generic và kế thừa thật thay vì chỉ dùng ví dụ không có inheritance.

## 11. Nhiều aspect: thứ tự, phạm vi và trạng thái

### 11.1. Thứ tự advice có thể làm thay đổi ý nghĩa

Hai advice dạng around có thể lồng nhau:

```text
A.before
    B.before
        target()
    B.after
A.after
```

Nếu đảo A/B, một advice đo thời gian có thể bao gồm hoặc loại trừ công việc của advice khác. Với retry, đo bên ngoài vòng retry cho **tổng thời gian**, đo bên trong có thể cho **thời gian từng lần thử**. Với audit hoặc transaction, thứ tự có thể quyết định liệu “thành công” được ghi trước hay sau một thao tác hoàn tất.

Thứ tự phải được xác nhận bằng khả năng *precedence/order* của công cụ và ca kiểm thử, không dựa vào thứ tự ngẫu nhiên trong cấu hình.

### 11.2. Lifecycle của aspect có thể khác

Một mô hình cung cấp aspect singleton chia sẻ trạng thái giữa mọi lời gọi; mô hình khác hỗ trợ instance theo target hoặc theo một luồng điều khiển. Trạng thái mutable trong aspect singleton cần được bảo vệ trước cạnh tranh. Một aspect theo đối tượng có quy tắc vòng đời khác, nhưng chỉ sử dụng được nếu công cụ hỗ trợ rõ.

Những thuật ngữ như *per-object*, *per-control-flow* mô tả **các lựa chọn có thể có trong một số công nghệ**, không phải phần bắt buộc trong mọi AOP.

### 11.3. Aspect có thể gây recursion/reentrancy

Giả sử advice ghi log bằng một method nằm trong chính tập pointcut. Việc ghi log có thể tiếp tục kích hoạt advice, dẫn tới đệ quy không mong muốn. Biên giới chọn điểm và loại trừ module instrumentation quan trọng hơn việc đổi tên advice. Nếu công cụ hỗ trợ biểu thức chọn theo control flow, cần hiểu cả ngữ nghĩa lồng nhau và tác vụ bất đồng bộ.

## 12. Async, exception và runtime context: nơi mô hình dễ bị hiểu sai

### 12.1. “After returning” khác “công việc đã xong”

Một phương thức có thể trả về `Future`, promise hoặc handle rất nhanh rồi hoàn tất công việc sau đó. Advice after-returning trên lần gọi **chỉ biết lần gọi trả handle đã hoàn tất**, trừ khi công cụ cung cấp hook theo ngữ nghĩa hoàn tất tác vụ.

Muốn đo toàn bộ thời gian hoặc lỗi cuối cùng, phải quan sát completion của tác vụ, không chỉ thời điểm tạo handle. Trên coroutine/event loop, thời điểm suspend/resume cũng có thể đòi hỏi hook riêng.

### 12.2. Thread-local context không mặc nhiên đi theo mọi tác vụ

Nhiều hệ thống dùng correlation ID, trace context hoặc dữ liệu xác thực theo thread. Nếu công việc chuyển sang thread khác hay callback, context có thể không tự đi cùng. Một AOP engine có hook ở phương thức không tự bảo đảm truyền context xuyên scheduler. Phải kiểm tra mô hình concurrency, chính sách propagation, cleanup và đường cancellation.

### 12.3. Exception có thể bị bọc hoặc đổi loại

Reflective invocation của Java có thể gói exception từ target trong `InvocationTargetException`, như ví dụ ở trên. Wrapper, coroutine hoặc task cũng có cơ chế biểu diễn lỗi khác. Advice muốn “giữ nguyên lỗi” phải xử lý theo đúng abstraction, không nuốt lỗi hoặc vô tình biến lỗi thành thành công.

Nếu advice tự ném exception trong `finally`, nó có thể che mất exception ban đầu. Với logging/metrics nên xác định fail-open hay fail-closed dựa trên hợp đồng; với audit bắt buộc hoặc kiểm tra quyền, quyết định này liên quan trực tiếp tính đúng đắn và cần thiết kế rõ ràng.

## 13. Kiểm chứng năng lực công cụ bằng ma trận, không bằng khẩu hiệu

Một thử nghiệm AOP nghiêm túc nên có **hai chiều**: loại join point và đường gọi. Ví dụ:

| Thử nghiệm | Câu hỏi cần chứng minh |
| --- | --- |
| Phương thức được chọn qua proxy | Advice có chạy đúng một lần, đúng thứ tự không? |
| Phương thức không được chọn qua proxy | Advice có thực sự không chạy không? |
| Gọi thẳng target | Handler có bị bỏ qua như dự kiến không? |
| Target tự gọi method khác | Self-invocation có đi qua hook theo cơ chế được chọn không? |
| Method overloaded/override/inherited | Matcher lấy đúng chữ ký và metadata không? |
| Method private/final/static | Điểm đó có nằm trong join-point model thực tế không? |
| Constructor hoặc field access | Cơ chế có hỗ trợ loại join point này không? |
| Target ném exception | Advice nào chạy; caller nhận lỗi gốc hay lỗi đã biến đổi? |
| Nhiều advice cùng khớp | Thứ tự có cố định và đúng hợp đồng không? |
| Tác vụ asynchronous | Advice đang quan sát việc gọi hay sự kiện hoàn tất thực? |
| Runtime/classloader khác | Hook có thực sự được cài trong artifact sẽ triển khai không? |
| Tải cao hoặc recursion | Advice có gây overhead, vòng lặp, rò context hoặc lộ dữ liệu không? |

Không nhất thiết mọi hàng đều phải “có”. Một thiết kế đúng có thể **cố ý không hỗ trợ** một số điểm. Mục tiêu kiểm thử là khớp với **hợp đồng được tuyên bố**, không phải biến một cơ chế nhỏ thành công cụ làm mọi việc.

### Checklist lựa chọn cơ chế

Trước khi nhận một công cụ AOP vào dự án, trả lời theo thứ tự:

1. **Chính sách cần tách là gì?** Có thực sự là hành vi kỹ thuật dùng chung, hay là quyết định nghiệp vụ cần tường minh?
2. **Cần loại join point nào?** Method execution/call, constructor, field access, xử lý lỗi hay hoàn tất tác vụ async?
3. **Cần pointcut biểu đạt gì?** Chữ ký, marker, kiểu runtime, đối số, ngữ cảnh luồng điều khiển?
4. **Advice có quyền gì?** Chỉ quan sát, thay input/output, gọi lại, chặn, hay tạo side effect?
5. **Weaving đặt ở đâu?** Interface wrapper, class proxy, build tool, classloader hay runtime agent?
6. **Các giới hạn nào quan trọng?** Self-call, method visibility, module/classloader, reflection, generic bridge, native runtime?
7. **Thứ tự và trạng thái ra sao?** Precedence, lifecycle, concurrency, exception và cleanup có contract không?
8. **Có chứng minh được không?** Positive test, negative test, integration test qua pipeline thật và benchmark phù hợp?

Nếu giải pháp chỉ đòi hỏi bọc vài object, decorator/interface proxy có thể đủ và dễ kiểm soát. Nếu yêu cầu điểm can thiệp sâu hơn, cần xem khả năng weaving cụ thể cùng chi phí kiểm thử, build và bảo trì. Nếu chính sách làm thay đổi quyết định nghiệp vụ, có thể cần domain policy hoặc workflow tường minh thay vì aspect.

## 14. Tình huống phân tích nâng cao

### Tình huống A — Đo thời gian lưu dữ liệu qua interface

**Yêu cầu:** Mọi lời gọi tới interface lưu trữ thông qua lớp ứng dụng đều cần ghi latency, không sửa giá trị trả về. **Phương án:** Dùng wrapper/proxy nếu kiểm soát được đường gọi và các điểm không đi qua wrapper đã được loại trừ. **Cần chứng minh:** Mọi caller dùng object được bọc, exception được bảo toàn và overhead chấp nhận được.

### Tình huống B — Cần can thiệp lúc ghi một field

**Yêu cầu:** Kiểm tra một sự kiện thay đổi trạng thái ngay khi lệnh ghi field diễn ra. **Giới hạn:** Interface dynamic proxy thông thường không chặn được thao tác ghi field trực tiếp. **Phương án:** Tốt nhất có thể là thiết kế lại setter/domain method tường minh; nếu cần AOP trên field access, phải dùng công cụ hỗ trợ join point đó và kiểm thử trên bytecode thật. Không suy ra khả năng từ từ khóa “AOP”.

### Tình huống C — Muốn dùng aspect để bảo vệ mọi đường truy cập dữ liệu

**Yêu cầu:** Không một lệnh truy cập trái phép nào được vượt qua. **Rủi ro:** Một nhánh gọi trực tiếp target, method không khớp pointcut hoặc module chưa được weave có thể bỏ qua chính sách. **Phương án:** Đặt kiểm tra quyền tại biên tin cậy và các bất biến nghiệp vụ; AOP có thể bổ sung kiểm tra kỹ thuật ở điểm đã được chứng minh, nhưng không được mặc định là lớp bảo vệ duy nhất.

### Tình huống D — Đo thời gian một công việc bất đồng bộ

**Yêu cầu:** Đo từ lúc gửi task đến lúc task thật sự hoàn tất. **Giới hạn:** Advice bao quanh method trả `Future` có thể chỉ đo thời gian tạo/sắp xếp task. **Phương án:** Quan sát completion của task/future bằng abstraction thích hợp; xác định riêng timeout, cancellation, exception và context propagation.

### Tình huống E — Tác động vào nhiều method có cùng marker

**Yêu cầu:** Gắn chính sách vào nhóm method khai báo marker. **Cần kiểm tra:** Marker ở interface hay implementation, retention, cơ chế tìm annotation, method được kế thừa/override, tên và chữ ký thay đổi sau refactor. Nếu matcher không tìm đúng metadata, aspect có thể không chạy dù source trông đã đánh dấu đủ.

## 15. Ngộ nhận thường gặp

**“Định nghĩa AOP có field join point, nên công cụ AOP tôi dùng cũng phải có.”** Sai: tập join point là một phần năng lực của công cụ.

**“Dùng annotation là đủ để phương thức được can thiệp.”** Sai: annotation chỉ là metadata; cần matcher và cơ chế kết hợp hành vi.

**“Proxy chặn được tất cả method cùng tên.”** Sai: phải xác định call path, interface/class proxy, visibility, dispatch và cách chọn method.

**“Weaving nghĩa là mọi thứ đều được can thiệp.”** Sai: weaving vẫn có tập điểm hỗ trợ, phạm vi artifact/classloader và cấu hình cụ thể.

**“Sau khi method trả về có nghĩa async job đã hoàn tất.”** Sai với method trả handle cho công việc tương lai, trừ khi có hook quan sát completion riêng.

**“Nếu test target trực tiếp chạy đúng, advice cũng sẽ chạy đúng.”** Sai: muốn kiểm tra AOP phải dùng cơ chế thật, cả test được chọn lẫn test loại trừ.

**“Một aspect hoạt động trong demo nên an toàn cho toàn ứng dụng.”** Sai: nhiều aspect, side effect, concurrency, exception, error handling và chi phí thực thi phải được kiểm chứng riêng.

## 16. Câu hỏi tự đánh giá

1. Hai công cụ đều có before advice, nhưng chỉ một công cụ can thiệp được constructor. Đây là khác biệt của thành phần nào trong mô hình AOP?
2. Vì sao kiểm tra `method.isAnnotationPresent` trên một `Method` của interface có thể cho kết quả khác khi marker chỉ đặt trên implementation?
3. Một `TaskService` được đưa vào proxy, nhưng nơi khác vẫn giữ `new TaskService()` rồi gọi trực tiếp. Điều gì xảy ra với advice của proxy?
4. Vì sao cần phân biệt method call và method execution khi viết pointcut?
5. Một around advice đo method trả `Future` sau 2 ms, trong khi tác vụ mất 5 giây. Số đo 2 ms phản ánh điều gì?
6. Công cụ hỗ trợ bytecode weaving. Có thể kết luận chắc chắn nó chặn được mọi thao tác đọc trường và mọi constructor không? Vì sao?
7. Nếu một advice ghi log chính method được pointcut chọn, nguy cơ nào xuất hiện?
8. Cần những kiểm thử nào trước khi tuyên bố một chính sách đã bao phủ đủ điểm thực thi?

**Đáp án gợi ý:** (1) Join-point model. (2) Runtime matcher nhận một `Method` cụ thể; metadata không mặc nhiên chuyển từ method triển khai sang method interface hay ngược lại. (3) Lời gọi trực tiếp bỏ qua proxy. (4) Chúng là hai loại sự kiện/điểm đặt hook có ngữ nghĩa khác nhau. (5) Thời gian gọi/đăng ký tác vụ, chưa chắc là thời gian hoàn tất. (6) Không; phải xác nhận loại join point, phạm vi bytecode, phiên bản và cấu hình. (7) Reentrant/recursive advice có thể chạy lặp vô hạn. (8) Positive/negative path, direct call, self-call, error, nhiều advice, async, cấu hình weave và benchmark theo yêu cầu.

## Kết luận

Mô hình AOP cung cấp **ngôn ngữ tư duy chung** để nói về concern, aspect, join point, pointcut, advice và weaving. Nhưng **năng lực thực thi** không chỉ đến từ định nghĩa; nó xuất hiện từ giao điểm của đặc tính ngôn ngữ, kỹ thuật công cụ và môi trường chạy. Hiểu giới hạn này giúp bạn chọn đúng kỹ thuật, kiểm soát phạm vi tác động và tránh áp đặt lên một proxy nhỏ những khả năng chỉ tồn tại ở một công cụ weaving khác. Trước khi tin rằng aspect sẽ chạy, hãy xác định **điểm nào được hỗ trợ, đường gọi nào đi qua hook, matcher nào đang dùng và phép thử nào chứng minh điều đó**.


</details>

- [Quay lại đầu trang](#back-to-top)

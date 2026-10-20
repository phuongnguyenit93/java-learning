<a id="back-to-top"></a>

# Quan sát chương trình

## Menu
- [Ghi nhật ký tại ranh giới nghiệp vụ thay vì mọi phương thức](#program-observability-001)
- [Tạo mã tương quan và gắn ngữ cảnh theo lời gọi](#program-observability-002)
- [Đo số lần gọi, thời gian và tỷ lệ lỗi](#program-observability-003)
- [Phân biệt số liệu kỹ thuật với sự kiện nghiệp vụ](#program-observability-004)
- [Che dữ liệu nhạy cảm và giới hạn kích thước tham số](#program-observability-005)
- [Lấy mẫu, giới hạn tần suất và giảm khối lượng đầu ra](#program-observability-006)
- [Không để lỗi ghi nhật ký làm thay đổi kết quả ngoài chính sách](#program-observability-007)
- [Tránh đo trùng khi có nhiều lớp aspect hoặc công cụ quan sát](#program-observability-008)
- [Kiểm tra chi phí quan sát trong điều kiện tải thực tế](#program-observability-009)

## <a id="program-observability-001">Ghi nhật ký tại ranh giới nghiệp vụ thay vì mọi phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Ghi nhật ký tại ranh giới nghiệp vụ thay vì mọi phương thức trong AOP

## 1. Ý tưởng cốt lõi

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), ghi nhật ký (logging) là một mối quan tâm xuyên suốt (**cross-cutting concern**): nhiều thao tác nghiệp vụ cần được quan sát theo cách nhất quán mà không chèn cùng một khối ghi log vào mọi nơi.

Nhưng **AOP không đồng nghĩa với việc tự động log tất cả phương thức**. Khi một tác vụ “đặt hàng” gọi hàng chục hàm để kiểm tra dữ liệu, tính giá, kiểm tra tồn kho, ghi trạng thái và gửi thông báo, log mỗi lần vào/ra hàm dễ tạo hàng trăm dòng kỹ thuật mà vẫn không trả lời được câu hỏi quan trọng: *yêu cầu đặt hàng đã được chấp nhận, bị từ chối hay thất bại?*

Nguyên tắc của bài này:

> Đặt quan sát mặc định tại **ranh giới nghiệp vụ có ý nghĩa** — nơi một hành động của người dùng, một lệnh, một job hoặc một sự kiện đi vào và cho ra kết quả — rồi chỉ thêm log ở các bước bên trong khi chúng thực sự giúp chẩn đoán hoặc đáp ứng yêu cầu vận hành.

“Ranh giới” ở đây là ranh giới về **ngữ nghĩa của thao tác**, không nhất thiết là lớp ngoài cùng trong chương trình hoặc mọi public method. Một lần xử lý thông điệp, một lần chạy tác vụ định kỳ, một use case xử lý giao dịch hoặc một operation của dịch vụ có thể là ranh giới phù hợp. Việc chọn điểm cắt phụ thuộc vào kiến trúc và câu hỏi cần quan sát.

### Phân biệt những khái niệm dễ nhầm

- **Business operation / use case:** một hành động có ý nghĩa với người sử dụng hệ thống hoặc nghiệp vụ, ví dụ “xác nhận đơn hàng”, “phê duyệt khoản chi”, “hoàn tất xuất kho”.
- **Business boundary:** điểm bắt đầu/kết thúc có hợp đồng và kết quả của operation đó, đủ ổn định để thống kê và chẩn đoán.
- **Join point:** điểm trong chương trình mà cơ chế AOP có thể can thiệp, như lần gọi hoặc thực thi một phương thức. Không phải mọi điểm nghiệp vụ đều mặc nhiên là join point được công cụ AOP hỗ trợ.
- **Pointcut:** quy tắc chọn các join point cần can thiệp.
- **Advice:** hành vi áp dụng tại điểm được chọn (trước, sau, khi ném lỗi hoặc bao quanh).
- **Aspect:** tập hợp có tổ chức của pointcut và advice phục vụ mối quan tâm xuyên suốt.

**Mục đích của pointcut ở đây là chọn đúng operation; mục đích của advice là ghi lại điều có ích.** Độ bao phủ rộng không phải là chỉ số chất lượng logging.

## 2. Vì sao log mọi phương thức thường phản tác dụng?

Hãy xem đường đi đơn giản:

~~~text
placeOrder()
    validateCustomer()
    normalizeAddress()
    calculatePrice()
        calculateDiscount()
        calculateTax()
    reserveInventory()
    persistOrder()
    sendConfirmation()
~~~

Nếu advice ghi “ENTER/EXIT” cho tất cả phương thức, chỉ một yêu cầu đã sinh ra ít nhất 18 dòng (chưa kể các thao tác bên dưới). Trong một chương trình thực, recursion, thư viện, helper và callback có thể làm số dòng tăng rất mạnh. Người đọc vẫn phải suy luận ngược xem operation nào đang diễn ra và cuối cùng có thành công không.

Những vấn đề điển hình:

1. **Nhiễu tín hiệu:** các sự kiện có giá trị thấp che khuất thành công/thất bại quan trọng.
2. **Chi phí:** format chuỗi, cấp phát đối tượng, ghi I/O, indexing và lưu trữ tăng theo số lần gọi, không theo số operation nghiệp vụ.
3. **Khó liên kết:** log “enter calculateTax()” không cho biết nó thuộc yêu cầu đặt hàng nào nếu thiếu correlation ID.
4. **Lộ dữ liệu:** log tham số và giá trị trả về một cách máy móc có thể ghi mật khẩu, token, thông tin thanh toán hoặc dữ liệu cá nhân.
5. **Phụ thuộc chi tiết triển khai:** tách một phương thức thành ba helper làm số log thay đổi dù hành vi nghiệp vụ không đổi.
6. **Lặp hoặc tự gọi lại:** advice có thể quan sát cả các lời gọi lồng nhau, đôi khi chính bộ phát log cũng khớp pointcut và gây recursion.
7. **Diễn giải sai outcome:** phương thức nội bộ trả bình thường không có nghĩa cả use case thành công; một helper lỗi nhưng được retry thành công cũng không nhất thiết là một operation thất bại.

Ghi log ở mọi phương thức chỉ có thể phù hợp với một phiên chẩn đoán có kiểm soát, tập phương thức nhỏ hoặc công cụ trace chuyên dụng có sampling và ngân sách rõ ràng. Nó không phải chính sách ghi log nghiệp vụ mặc định.

## 3. Chọn ranh giới nghiệp vụ ở đâu?

Một ranh giới tốt thường có một hoặc nhiều đặc tính:

- Có **tên operation ổn định**, phản ánh mục đích, ví dụ `order.place` hoặc `invoice.approve`.
- Có đầu vào và kết quả đủ rõ để phân loại **success, rejected, failed, cancelled**.
- Có thể xác định ai/cái gì khởi tạo, correlation ID và thời điểm bắt đầu.
- Gần với nơi mã thực hiện **use case** hoặc nơi task được điều phối; cho phép quan sát đầy đủ những bước bên trong.
- Không thay đổi chỉ vì refactor helper, cấu trúc package hoặc thuật toán.

Bảng gợi ý:

| Phạm vi | Nên ghi ở đâu? | Sự kiện quan tâm | Mức độ |
| --- | --- | --- | --- |
| Hành động người dùng | Điểm bắt đầu/kết thúc use case | “Đặt hàng được chấp nhận” hoặc “bị từ chối” | Mặc định |
| Job định kỳ | Một lần chạy job, kèm run ID | “Bắt đầu, hoàn tất, thất bại, mất bao lâu” | Mặc định |
| Message consumer | Một lần xử lý message/command | “Đã xử lý, retry, đưa vào hàng đợi lỗi” | Mặc định |
| Giao tiếp dịch vụ bên ngoài | Điểm gọi phụ thuộc quan trọng | “Timeout, lỗi phụ thuộc, retry” | Bổ sung có chọn lọc |
| Repository, mapper, getter, validator nhỏ | Thường không phải boundary nghiệp vụ | Log chỉ khi có lỗi bất thường đáng chẩn đoán | Hạn chế |
| Từng bước tính toán thuần | Bên trong operation | Thường thay bằng test, metric hoặc trace khi cần | Không mặc định |

Ví dụ: một HTTP handler chỉ chuyển tham số sang use case có thể là ranh giới **giao thức**; còn use case `approvePayment` mới là ranh giới **nghiệp vụ**. Nếu log cả hai, hãy gắn vai trò rõ ràng: một bản ghi request transport, một bản ghi business outcome, cùng trace/correlation ID nhưng khác operation và khác ý nghĩa. Không ghi hai dòng tương đương chỉ vì có hai lớp.

Đối với một operation gồm nhiều command hợp lệ, ranh giới không nhất thiết là “một phương thức”. Có thể cần một định danh workflow và các sự kiện nghiệp vụ riêng để nắm được các bước trải qua nhiều process.

## 4. Ghi điều gì ở ranh giới?

Một bản ghi có giá trị thường thể hiện **“điều gì xảy ra với một operation?”**, thay vì “hàm tên gì vừa chạy?”.

### Các trường khuyến nghị

| Trường | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `timestamp` | Thời gian sự kiện phát sinh, có múi giờ hoặc UTC | `2026-10-20T02:00:00Z` |
| `level` | Mức độ log, tách khỏi outcome | `INFO`, `WARN`, `ERROR` |
| `event` | Loại sự kiện ổn định | `business_operation_finished` |
| `operation` | Tên thao tác có cardinality thấp | `order.place` |
| `outcome` | Kết quả đã xác định theo hợp đồng | `success`, `rejected`, `failed` |
| `duration_ms` | Thời gian đo được, thống nhất mốc bắt đầu/kết thúc | `87.4` |
| `correlation_id` / `trace_id` | Ghép bản ghi của cùng luồng xử lý | ID theo ngữ cảnh |
| `error_code` | Nhóm nguyên nhân lỗi ổn định, đã được làm sạch | `INVENTORY_UNAVAILABLE` |
| `attempt` | Lần thử, nếu có retry | `2` |

Các trường khác chỉ thêm khi có mục đích rõ và được phép lưu: tenant ID đã xử lý phù hợp, loại tác vụ, kích thước batch, tên phụ thuộc, version schema. Không tự động ghi toàn bộ đối tượng request/response hoặc stack trace của mọi thành công.

**Structured logging** (ví dụ JSON một sự kiện trên một dòng) giúp truy vấn theo operation/outcome/duration chính xác hơn so với ghép chuỗi tùy ý. Chọn tên trường và schema ổn định để dashboard, alert và hệ thống tổng hợp dùng được lâu dài.

Ví dụ minh họa một bản ghi hoàn tất (các giá trị thời gian, ID và độ trễ chỉ để minh họa):

~~~json
{
  "timestamp": "2026-10-20T02:00:00Z",
  "level": "INFO",
  "event": "business_operation_finished",
  "operation": "order.place",
  "outcome": "success",
  "duration_ms": 87.4,
  "correlation_id": "req-7e23"
}
~~~

Đây là **định dạng dữ liệu ví dụ**, không phải stdout cố định của mã Java ở phần sau. Trong hệ thống thật, cần xác định bên nào phát timestamp, cách xử lý clock, cách tạo ID, cũng như thứ tự giữa log và việc chuyển kết quả tới người gọi.

## 5. Chọn chiến lược sự kiện: bắt đầu và kết thúc, hay chỉ hoàn tất?

Ba cách thường gặp:

**A. Một bản ghi khi operation kết thúc** — thường là điểm khởi đầu hiệu quả.

- Bao gồm tên operation, outcome và duration.
- Ít bản ghi, phù hợp hệ thống có lưu lượng cao.
- Nhược điểm: nếu tiến trình chết trước khi kết thúc, không có bản ghi hoàn tất; để phát hiện operation bị treo cần cơ chế ngoài log hoàn tất, chẳng hạn active-operation metric hoặc watchdog.

**B. Một bản ghi bắt đầu + một bản ghi kết thúc** — phù hợp operation quan trọng hoặc kéo dài.

- Bản ghi bắt đầu giúp nhận diện công việc đang hoạt động.
- Bản ghi kết thúc giúp đo kết quả; cần ID/attempt để ghép cặp.
- Có thể tăng lưu lượng log; việc thiếu dòng kết thúc không tự chứng minh một nguyên nhân cụ thể.

**C. Bản ghi kết thúc + các mốc nghiệp vụ chọn lọc** — phù hợp quy trình nhiều bước.

- Các mốc có ngữ nghĩa như `inventory_reserved`, `payment_authorized` có ích nếu được chính nghiệp vụ xác nhận.
- Tránh biến mọi helper call thành “milestone”.
- Các mốc phải phân biệt trạng thái dự định và trạng thái đã được hệ thống xác nhận.

Chọn một quy ước nhất quán; tránh một Aspect ghi success ở sau `proceed()` và Aspect khác lại ghi success khi kết quả bất đồng bộ hoàn tất, khiến mỗi operation thành công được đếm hai lần.

## 6. Pointcut: chọn theo ý nghĩa, không theo số lượng phương thức

Các cách nhận diện boundary:

1. **Đánh dấu tường minh bằng annotation hoặc metadata** trên method đại diện cho use case. Dễ review và dễ kiểm toán phạm vi; phải tránh đánh dấu helper tùy tiện.
2. **Một interface/contract của lớp điều phối nghiệp vụ**: mọi operation công khai trong một contract được chỉ định là boundary, thay vì toàn bộ package.
3. **Danh sách operation qua cấu hình có kiểm soát**: hữu ích khi không thể sửa source hoặc cần cấu hình instrumentation.
4. **Quy ước package/lớp**: có thể dùng nếu cấu trúc kiến trúc thực sự ổn định, nhưng nên có exclusion rõ cho helper/internal methods.

Các kiểu chọn quá rộng như “mọi phương thức public”, “mọi phương thức tên bắt đầu bằng get”, hoặc “mọi phương thức trong tất cả package ứng dụng” không mô tả được ngữ nghĩa nghiệp vụ. Khi triển khai bằng proxy, weaving lúc biên dịch, lúc tải lớp hoặc một interceptor khác, tập join point có thể khác nhau; cần kiểm tra **đường gọi thực tế được instrument**. Một lời gọi nội bộ cùng đối tượng có thể không đi qua interceptor dạng proxy; các cơ chế weaving khác có đặc điểm khác. Không mặc định annotation tự khiến advice chạy: annotation là metadata, còn AOP mechanism mới thực hiện interception.

Nếu dùng annotation, định nghĩa chặt chẽ:

- `operation` là định danh ổn định, ví dụ `order.place`, không phải UUID hay ID đơn hàng.
- Áp dụng cho những method cụ thể đại diện use case; tránh “đánh dấu cho đủ”.
- Rõ việc tra annotation ở interface method, implementation method hoặc quy tắc kế thừa; metadata trên một method không luôn tự kế thừa sang method override.
- Khi đổi tên phương thức nhưng nghiệp vụ không đổi, cố gắng giữ tên operation ổn định để đồ thị lịch sử không bị đứt.

## 7. Around advice đúng trách nhiệm

Ở một lời gọi đồng bộ, around advice điển hình có thể:

~~~text
around(business operation):
    operation = stableBusinessName()
    context = safeCorrelationContext()
    start = monotonicClock()
    try:
        result = proceedExactlyOnce()
        recordFinished(operation, "success", elapsed(start), context)
        return result
    catch KnownBusinessRejection:
        recordFinished(operation, "rejected", elapsed(start), context)
        rethrow
    catch error:
        recordFinished(operation, "failed", elapsed(start), context)
        rethrow
~~~

Đây là **giả mã** minh họa điểm đặt trách nhiệm; tên hàm và kiểu lỗi chưa định nghĩa.

Những điều cần giữ:

- **Không sửa kết quả nghiệp vụ** nếu chức năng của Aspect chỉ là quan sát.
- **Không nuốt exception** và không biến một lỗi thành kết quả thành công.
- **Không gọi proceed() hai lần**, trừ khi retry thật sự là chính sách riêng có đặc tả về idempotency.
- **Dùng đồng hồ đơn điệu** để đo khoảng thời gian (trong Java: `System.nanoTime()`), không lấy chênh lệch wall clock có thể bị chỉnh.
- **Giữ logging tách biệt khỏi nghiệp vụ**: nếu hệ thống log lỗi, cố gắng không làm operation thất bại theo cách ngoài ý muốn. Chiến lược xử lý lỗi logger là quyết định kiến trúc; không được bí mật bỏ qua lỗi khi log là phần của một bảo đảm kiểm toán bắt buộc.
- **Phân loại exception theo contract**: validation rejection có thể là kết quả được dự kiến, không phải lỗi hệ thống; không thể suy ra mọi `IllegalArgumentException` đều là business rejection.

Một điểm đặc biệt: **trả về bình thường của phương thức không luôn tương đương thành công nghiệp vụ**. Phương thức có thể trả `Result.Failure`, trạng thái “pending”, hoặc CompletionStage chưa hoàn tất. Advice phải hiểu hợp đồng của operation mới gán outcome chính xác.

## 8. Ví dụ Java 17: dynamic proxy chỉ log method được đánh dấu là boundary

Ví dụ chạy độc lập dưới đây sử dụng **JDK dynamic proxy** làm cơ chế interception tối giản để minh họa AOP chung. Chỉ phương thức `placeOrder` được đánh dấu là boundary; `findStatus` và các helper nội bộ không tạo event nghiệp vụ. Đây là mô hình học tập, không phải thư viện AOP hoàn chỉnh.

Tạo tệp **BusinessBoundaryLoggingDemo.java**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.LongAdder;

public final class BusinessBoundaryLoggingDemo {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface BusinessBoundary {
        String operation();
    }

    interface OrderService {
        @BusinessBoundary(operation = "order.place")
        String placeOrder(String orderId);

        String findStatus(String orderId);
    }

    static final class DefaultOrderService implements OrderService {
        @Override
        public String placeOrder(String orderId) {
            validate(orderId);
            return "ACCEPTED";
        }

        @Override
        public String findStatus(String orderId) {
            return "ACTIVE";
        }

        private void validate(String orderId) {
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException("orderId is missing");
            }
        }
    }

    static final class BoundaryAdvice implements InvocationHandler {
        private final Object target;
        private final LongAdder totalDurationNanos = new LongAdder();

        BoundaryAdvice(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {

            BusinessBoundary boundary =
                    method.getAnnotation(BusinessBoundary.class);

            if (boundary == null) {
                return callTarget(method, args);
            }

            String operation = boundary.operation();
            long start = System.nanoTime();
            System.out.println("event=started operation=" + operation);

            try {
                Object result = callTarget(method, args);
                System.out.println("event=finished operation=" + operation
                        + " outcome=success");
                return result;
            } catch (Throwable error) {
                System.out.println("event=finished operation=" + operation
                        + " outcome=failure error_type="
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                totalDurationNanos.add(System.nanoTime() - start);
            }
        }

        private Object callTarget(Method method, Object[] args)
                throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException wrapper) {
                // Phản ánh lỗi gốc của nghiệp vụ, không để lộ lớp bọc reflection.
                throw wrapper.getCause();
            }
        }
    }

    public static void main(String[] args) {
        OrderService service = (OrderService) Proxy.newProxyInstance(
                OrderService.class.getClassLoader(),
                new Class<?>[] { OrderService.class },
                new BoundaryAdvice(new DefaultOrderService())
        );

        // Method không được đánh dấu: không tạo log nghiệp vụ.
        System.out.println("status=" + service.findStatus("O-100"));

        System.out.println("result=" + service.placeOrder("O-100"));

        try {
            service.placeOrder(" ");
        } catch (IllegalArgumentException error) {
            System.out.println("caller_error="
                    + error.getClass().getSimpleName());
        }
    }
}
~~~

Chạy bằng Java 17 trở lên:

~~~bash
javac BusinessBoundaryLoggingDemo.java
java BusinessBoundaryLoggingDemo
~~~

Đầu ra chính xác:

~~~text
status=ACTIVE
event=started operation=order.place
event=finished operation=order.place outcome=success
result=ACCEPTED
event=started operation=order.place
event=finished operation=order.place outcome=failure error_type=IllegalArgumentException
caller_error=IllegalArgumentException
~~~

### Điểm cần hiểu từ ví dụ

- Method `placeOrder` mang metadata `@BusinessBoundary`; `findStatus` và `validate` không được log theo chính sách boundary.
- Proxy chỉ chặn lời gọi thông qua interface `OrderService`; helper `validate` được gọi trực tiếp trong implementation. Cơ chế khác có thể cung cấp tập join point khác.
- `Method.invoke` bọc lỗi do target ném trong `InvocationTargetException`; `callTarget` gỡ lớp bọc để caller vẫn thấy `IllegalArgumentException`.
- Giá trị trả về `ACCEPTED` vẫn được trả về nguyên vẹn; logging không sửa business behavior.
- `LongAdder` cho thấy vị trí ghi nhận duration dùng đồng hồ đơn điệu. Ví dụ không in duration vì giá trị phụ thuộc máy chạy; trong hệ thống thật cần đưa vào hệ thống metric với đơn vị và nhãn rõ ràng.
- `outcome=failure` trong ví dụ chỉ có nghĩa lời gọi ném exception; chưa phân loại `rejected` theo hợp đồng nghiệp vụ. Trong chương trình thật, phải có loại kết quả/lỗi đã định nghĩa để phân biệt rejection có chủ đích với lỗi hạ tầng.
- Code `System.out.println` chỉ để tạo đầu ra dễ kiểm tra. Logging thật nên dùng API ghi log có cấu trúc, ngưỡng level, cơ chế chống nghẽn và chính sách xử lý dữ liệu nhạy cảm.
- Đây là instrumentation đồng bộ; với API bất đồng bộ, việc method trả về handle chưa phải thời điểm operation hoàn tất.

## 9. Điểm quan sát trước và sau: đủ sự kiện, tránh nhân đôi

Nên xác định rõ **ai chịu trách nhiệm phát sự kiện chính của một operation**. Nếu các Aspect cùng bọc nhau, chúng có thể tạo bản ghi khác nhau cho cùng operation:

~~~text
TracingAdvice -> BoundaryLoggingAdvice -> RetryAdvice -> businessOperation
~~~

Trong minh họa này, một lần gọi nghiệp vụ có thể chứa nhiều attempt do retry. Nếu logging nằm ngoài retry, thường ghi một outcome cho operation tổng thể; nếu nằm trong retry, có thể ghi outcome từng attempt. Thứ tự thực thi và phạm vi pointcut quyết định ý nghĩa kết quả, không thể suy đoán chỉ từ tên Aspect.

Quy ước nên phân biệt:

- **Operation:** một lần người dùng hoặc hệ thống yêu cầu thực hiện việc.
- **Attempt:** một lượt thử của operation, có thể có nhiều lần khi retry.
- **Step:** một mốc có ý nghĩa nghiệp vụ hoặc hạ tầng bên trong.
- **Dependency call:** một lần gọi dịch vụ bên ngoài, có thể nhiều hơn một trong một attempt.

Ví dụ, một operation cuối cùng thành công sau hai lần retry không nên mặc định tạo hai sự kiện “order.place failed” ở cấp operation. Nếu cần quan sát attempt, đặt các bản ghi có tên `attempt_failed`, `attempt_succeeded` cùng attempt number và ID của operation. Điều này giúp alert phản ánh lỗi thực thay vì chênh lệch do cơ chế retry.

## 10. Đo thời gian đúng cấp độ

Logging AOP thường bổ sung duration; nhưng định nghĩa mốc đo phải rõ:

| Loại thời gian | Bắt đầu | Kết thúc | Dùng khi |
| --- | --- | --- | --- |
| Invocation duration | Trước `proceed()` | Khi `proceed()` trả về hoặc ném lỗi | Đo lời gọi đồng bộ |
| End-to-end async duration | Khi operation được chấp nhận | Khi kết quả cuối của operation được xác nhận | Đo trải nghiệm/tổng thời gian workflow |
| Queue waiting time | Khi gửi task | Khi task bắt đầu thực thi | Phân tích nghẽn hàng đợi |
| Task processing time | Khi task thực sự bắt đầu | Khi task thực sự dừng | Phân tích khối lượng xử lý |
| Attempt duration | Bắt đầu attempt | Kết thúc attempt | Phân tích retry |

Một `CompletionStage` có thể được trả về ngay trong vài microsecond trong khi công việc chạy vài giây. Nếu advice ghi success ngay sau `proceed()`, log có thể chỉ xác nhận **submission thành công**, không phải **operation thành công**. Cách phù hợp là quan sát tín hiệu completion của contract hoặc đặt instrumentation ở nơi thực thi; không gọi `join()`/`get()` trong advice chỉ để chờ kết quả vì dễ phá vỡ tính bất đồng bộ.

Khi một stage timeout hoặc bị hủy, cần phân biệt **handle đã hoàn tất** với **task thực tế đã dừng và cleanup xong**. Mốc completion nào được ghi phải mô tả trung thực. Với callback và publisher lười, cũng phải hiểu vòng đời callback hoặc subscription trước khi gắn logging; không tự subscribe chỉ để kích hoạt log vì có thể làm chạy thêm công việc.

## 11. Truyền correlation context và theo dõi yêu cầu xuyên luồng

Một bản ghi outcome có thể đúng nhưng vẫn khó tìm nếu không liên kết với bản ghi của bước khác. Dùng **correlation ID** hoặc **trace ID** xuyên suốt đường xử lý:

~~~text
request received: correlation_id = C42
  -> order.place: C42
      -> inventory.reserve: C42
      -> payment.authorize: C42
  -> order.place finished: C42, outcome = success
~~~

Đây là sơ đồ ví dụ. Trong triển khai thật:

- Tạo hoặc xác thực ID ở ranh giới đầu vào; không nhận mọi giá trị client cung cấp mà không kiểm tra độ dài, ký tự và chính sách tin cậy.
- Truyền ID qua lời gọi có chủ đích; không dùng ID khách hàng làm correlation ID mặc định.
- Với thread pool hoặc callback, không giả định `ThreadLocal` tự đi theo tác vụ. Chụp **dữ liệu ngữ cảnh bất biến** rồi gắn/tháo scope ở luồng thực thi bằng try/finally.
- Xác định quan hệ giữa log, span của distributed tracing và metric. Log cung cấp sự kiện chi tiết; trace liên kết các thao tác theo thời gian; metric cho biết tỷ lệ lỗi và phân phối độ trễ. Chúng bổ trợ nhau, không phải thay thế lẫn nhau.
- Không mở một scope gắn với luồng gọi rồi giữ nguyên đến khi công việc nền hoàn tất nếu scope không được thiết kế cho vòng đời đó.

Có trace ID không có nghĩa log đủ thông tin nghiệp vụ. Trường `operation`, `outcome` và `error_code` vẫn quan trọng để query theo nghiệp vụ.

## 12. An toàn dữ liệu và cardinality

Chính sách ghi log tại boundary cần bảo vệ dữ liệu ngay từ lúc thiết kế:

**Không tự động ghi:** mật khẩu, token, authorization header, session secret, private key, thông tin thẻ thanh toán, nội dung tài liệu nhạy cảm, request/response object đầy đủ hoặc stack trace chứa giá trị cá nhân. Ngay cả `toString()` của object cũng có thể tiết lộ dữ liệu.

**Ưu tiên:** tên operation cố định, mã lỗi đã chuẩn hóa, độ trễ, trạng thái, attempt và correlation ID được tạo đúng chính sách. Với mã nghiệp vụ cần tìm kiếm, cân nhắc surrogate ID, hashing có quy tắc và kiểm soát quyền truy cập; hashing không tự bảo đảm ẩn danh.

**Cardinality:** nếu dùng `orderId`, `userId`, URL có ID, hoặc nội dung exception làm **label metric**, hệ thống metric có thể nổ số lượng time series. Tên operation và error code nên thuộc tập hữu hạn; ID có thể nằm trong log khi được phép nhưng không nên làm label thống kê phổ biến.

**Phân loại mức log:**

- `INFO`: sự kiện hoàn tất quan trọng, có giới hạn lưu lượng.
- `WARN`: từ chối hay retry cần chú ý, nhưng có thể nằm trong hoạt động dự kiến.
- `ERROR`: lỗi bất thường hoặc failure ảnh hưởng mục tiêu vận hành.

Level không đồng nhất outcome: `rejected` vì quy tắc nghiệp vụ có thể chỉ ở INFO/WARN; một operation cuối cùng thành công sau retry có thể có WARN cho từng attempt. Chính sách cần dựa trên tính chất lỗi và mục tiêu vận hành.

**Log injection:** nội dung người dùng không đáng tin có thể chứa newline và cấu trúc giả. Dùng encoder/structured logger và giới hạn kích thước thay vì ghép raw input vào log line.

## 13. Sampling, volume và khả năng vận hành

Boundary logging vẫn có thể phát lượng log rất lớn. Thiết kế cần có **ngân sách log**:

1. Luôn giữ những failure quan trọng theo chính sách giữ lại; có thể sampling các success lưu lượng cao nhưng phải biết tỷ lệ lấy mẫu.
2. Dùng metric counter cho số operation thành công/thất bại và histogram cho latency; tránh tổng hợp tỷ lệ lỗi chỉ từ log đã sampling.
3. Quyết định nơi xử lý backpressure: queue logger, giới hạn buffer, chính sách drop/fallback. Blocking logger có thể làm tăng latency của operation.
4. Đặt retention và quyền truy cập theo loại dữ liệu; kết hợp cơ chế xóa đúng hạn và kiểm soát người xem.
5. Dùng operation name ổn định để dashboard không biến động theo refactor. Version hóa event schema nếu thay đổi trường có ảnh hưởng tới downstream.
6. Cảnh báo dựa trên tỷ lệ lỗi, latency, throughput và các tín hiệu nghiệp vụ phù hợp; không tạo cảnh báo mỗi lần helper ném exception rồi đã được xử lý thành công.

**AOP ghi log giúp giảm trùng lặp code**, nhưng không làm biến mất chi phí serialization, lưu trữ hay vận hành. Ngưỡng log và sampling nên thiết kế trước khi nhân rộng instrumentation.

## 14. Ranh giới giữa log quan sát và audit nghiệp vụ

**Log vận hành** trả lời “điều gì đã xảy ra, vì sao thất bại, tốn bao lâu?”. Nó thường được đưa vào hệ thống observability, có thể sampling, xoay vòng, giữ lại theo TTL.

**Audit nghiệp vụ** trả lời “ai đã thay đổi trạng thái nào, từ đâu, vào lúc nào, dựa trên quyền nào?” và có thể có yêu cầu về tính đầy đủ, tính nhất quán, chống sửa, truy vết pháp lý.

Không nên coi một lời gọi `logger.info("approved")` từ around advice là bằng chứng chắc chắn rằng giao dịch phê duyệt đã được commit thành công. Nếu operation rollback sau khi method đã return, hoặc event phát trước durable commit, thông điệp có thể không phản ánh trạng thái tồn tại lâu dài.

Khi cần audit có bảo đảm, nên gắn cơ chế ghi audit vào ranh giới trạng thái nghiệp vụ được xác nhận và định nghĩa rõ các bảo đảm giao dịch/durable event. AOP có thể hỗ trợ bổ sung metadata, correlation ID hoặc quy tắc chung, nhưng bản thân interception không tạo ra tính nguyên tử hay bảo đảm giao dịch.

## 15. Những trường hợp dễ sai và cách sửa

### Trường hợp A: log ở mọi getter, setter, mapper

**Hiện tượng:** log tăng đột biến khi thay đổi thuật toán nội bộ; không có thông tin outcome của use case.

**Sửa:** chọn pointcut theo operation có ý nghĩa nghiệp vụ. Đặt debug/trace mức thấp chỉ ở vị trí cần chẩn đoán, với giới hạn phạm vi và thời gian.

### Trường hợp B: quanh method có retry

**Hiện tượng:** một yêu cầu cuối cùng thành công nhưng dashboard đếm nhiều business failures.

**Sửa:** phân biệt operation với attempt; ghi tên sự kiện, cấp độ và vị trí advice nhất quán với outcome cần theo dõi. Có thể log attempt riêng khi cần.

### Trường hợp C: after-returning ghi thành công cho Future

**Hiện tượng:** log success xuất hiện khi task chưa chạy hoặc sau đó thất bại.

**Sửa:** ghi `submitted` tại thời điểm return, và `completed`/`failed` tại đúng lifecycle kết quả nếu có tín hiệu phù hợp.

### Trường hợp D: Advice log hết tham số

**Hiện tượng:** thông tin cá nhân, token hoặc payload lớn xuất hiện trong log.

**Sửa:** schema với các trường cho phép, bỏ mặc định toàn bộ args/return value; redaction/encoding phù hợp, giới hạn kích thước và kiểm thử dữ liệu nhạy cảm.

### Trường hợp E: Advice làm đổi nghiệp vụ

**Hiện tượng:** logger hoặc serializer ném exception che mất lỗi gốc; callback observer đổi trạng thái trả về.

**Sửa:** tách instrumentation khỏi đường dữ liệu nghiệp vụ; quy định cụ thể phản ứng khi phát log thất bại. Nếu phải tuân thủ audit bắt buộc, mô hình bảo đảm cần thiết kế công khai thay vì âm thầm nuốt lỗi.

### Trường hợp F: nhiều Aspect trùng pointcut

**Hiện tượng:** log đôi, thứ tự không rõ, số success không khớp số yêu cầu.

**Sửa:** xác định một nơi chịu trách nhiệm event cấp operation; các Aspect khác thêm trace/metric hoặc event ở cấp khác với tên rõ ràng. Kiểm thử thứ tự bao quanh thực tế.

### Trường hợp G: tên operation động

**Hiện tượng:** mỗi đơn hàng sinh một operation name khác nhau; query và metric khó tổng hợp.

**Sửa:** giữ `operation="order.place"` ổn định; ID tài nguyên đi vào trường riêng có quy tắc bảo mật thích hợp.

## 16. Kiểm thử AOP logging theo hành vi, không theo số dòng ngẫu nhiên

Nên kiểm thử các thuộc tính có ý nghĩa:

| Kiểm thử | Kết quả kỳ vọng |
| --- | --- |
| Gọi use case thành công | Một outcome `success` cấp operation theo chính sách đã chọn |
| Helper hoặc getter được gọi | Không phát event cấp business operation ngoài chủ đích |
| Use case ném lỗi | Một outcome đúng loại; lỗi vẫn tới caller |
| Nhiều lời gọi lồng nhau | Không đếm trùng một operation; các operation độc lập được phân biệt |
| Retry hai lần rồi thành công | Một outcome cuối cấp operation; attempt được đếm riêng nếu cần |
| Kết quả dạng failure object | Không ghi success chỉ vì method return bình thường |
| CompletionStage lỗi sau return | Log completion là failure nếu observer được thiết kế cho lifecycle đó |
| Luồng executor được tái sử dụng | Correlation context không rò từ task trước sang task sau |
| Input chứa newline/token/PII | Dữ liệu nhạy cảm không bị ghi ra; giá trị không chèn thêm log giả |
| Observer/logger phát sinh lỗi | Kết quả nghiệp vụ tuân theo chính sách thất bại đã định nghĩa |
| Refactor helper | Số event nghiệp vụ ổn định nếu contract use case không đổi |

Hãy kiểm tra **schema, tên operation, outcome, lỗi và correlation**. Tránh gắn test vào chi tiết format của một logger ngẫu nhiên nếu kiến trúc có thể thay thư viện. Khi kiểm thử timing, dùng đồng hồ kiểm soát được hoặc assertion khoảng hợp lý; không dựa vào số nanosecond chính xác.

## 17. Quy trình áp dụng trong dự án

**Bước 1 — Liệt kê các operation quan trọng.** Ghi tên, actor/nguồn kích hoạt, hợp đồng trả về, trạng thái thành công/thất bại và liệu chúng có bất đồng bộ không.

**Bước 2 — Chọn join point gần ranh giới phù hợp nhất.** Đảm bảo lời gọi thực tế đi qua cơ chế AOP; dùng annotation/contract nếu giúp kiểm soát phạm vi.

**Bước 3 — Định nghĩa event schema.** Chọn tên operation cố định, outcome, duration, correlation ID, error code, level và dữ liệu an toàn; đồng thời xác định operation/attempt/step.

**Bước 4 — Viết advice bảo toàn hợp đồng.** Gọi proceed() đúng một lần; hoàn trả kết quả hoặc ném lại lỗi; đo thời gian theo vòng đời thực; không tự log args/return theo reflection một cách mù quáng.

**Bước 5 — Bổ sung quan sát có mục tiêu.** Chỉ thêm mốc ở phụ thuộc quan trọng, retry hoặc trạng thái nghiệp vụ khi có câu hỏi vận hành cần trả lời.

**Bước 6 — Kiểm thử và đo chi phí.** Xác minh không trùng, không rò dữ liệu, không làm đổi kết quả; theo dõi throughput, latency, dung lượng log, tỉ lệ sample và tác động khi logger bị nghẽn.

**Bước 7 — Duy trì chính sách khi hệ thống thay đổi.** Review annotation/pointcut trong code review; bảo vệ event schema và dashboard khỏi thay đổi tên method vô tình.

## 18. Ghi nhớ

Logging AOP chất lượng cao tập trung vào **ý nghĩa của một operation**:

- **Đúng điểm:** quan sát tại boundary có hợp đồng nghiệp vụ; không mặc định mọi method.
- **Đúng dữ liệu:** operation, outcome, duration, correlation, nguyên nhân được chuẩn hóa và thông tin được phép ghi.
- **Đúng vòng đời:** lời gọi đồng bộ, tác vụ bất đồng bộ, retry attempt và trạng thái đã commit có các mốc khác nhau.
- **Đúng tác dụng:** có thể chẩn đoán và thống kê, nhưng không nhân đôi log, tiết lộ dữ liệu, che lỗi hay làm biến dạng hành vi nghiệp vụ.

Một sự kiện `order.place failed` với correlation ID, mã lỗi rõ và duration hữu ích hơn hàng trăm dòng `enter/exit` không cho biết chuyện gì thực sự xảy ra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-observability-002">Tạo mã tương quan và gắn ngữ cảnh theo lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Tạo mã tương quan và gắn ngữ cảnh theo lời gọi trong AOP

## 1. Mục tiêu của bài

Khi một thao tác nghiệp vụ đi qua nhiều phương thức, nhiều lớp hoặc nhiều tác vụ bất đồng bộ, log đơn lẻ thường không cho biết các sự kiện nào thuộc cùng một lần xử lý. **Mã tương quan** (correlation ID) giúp nối các sự kiện liên quan thành một chuỗi; **ngữ cảnh lời gọi** (call context) bổ sung thông tin về từng lần thực thi; **aspect** áp dụng quy tắc gắn, đọc và dọn dẹp ngữ cảnh tại các điểm được chọn bằng pointcut.

Ví dụ một thao tác “xử lý đơn hàng” lần lượt kiểm tra tồn kho, tính phí và ghi nhận kết quả. Nếu từng phương thức in log riêng, muốn tra cứu toàn bộ thao tác phải có một khóa chung. Trong AOP, thay vì viết thao tác lấy/gắn mã lặp lại trong từng phương thức nghiệp vụ, ta có thể đặt advice tại các join point thích hợp.

Sau bài này, cần phân biệt được:

- Mã tương quan dùng để gom các sự kiện thuộc cùng một luồng xử lý logic.
- Mã lời gọi dùng để nhận diện **một lần** đi qua join point, kể cả khi một phương thức được gọi nhiều lần.
- Quan hệ cha–con giữa các lời gọi giúp tái dựng đường đi, nhưng không đồng nghĩa mọi phương thức con phải có mã tương quan mới.
- Advice phải **khôi phục ngữ cảnh cũ** khi rời scope, kể cả khi xảy ra ngoại lệ, callback hoặc lời gọi lồng nhau.
- Ranh giới thread, tác vụ bất đồng bộ và hệ thống từ xa đòi hỏi một cơ chế truyền ngữ cảnh rõ ràng.

Bài trình bày AOP ở mức khái niệm và kỹ thuật tổng quát. Ví dụ Java thuần minh họa logic mà một around advice có thể thực hiện; không cần một thư viện AOP cụ thể để chạy.

## 2. Phân biệt các mã và dữ liệu ngữ cảnh

| Thành phần | Phạm vi định danh | Thường thay đổi khi nào? | Vai trò |
| --- | --- | --- | --- |
| Correlation ID | Một luồng xử lý logic mà hệ thống định nghĩa | Khi bắt đầu luồng mới không có mã được chấp nhận | Tìm toàn bộ log và sự kiện cùng nghiệp vụ |
| Call ID | Một lần đi qua join point đã chọn | Mỗi lần advice tạo một scope lời gọi mới | Phân biệt các lần gọi, kể cả đệ quy và retry |
| Parent call ID | Lời gọi cha trực tiếp trong mô hình scope đang quan sát | Khi chuyển sang một lời gọi con | Tái dựng quan hệ cha–con |
| Operation name | Loại thao tác, chẳng hạn validateOrder | Theo pointcut hoặc join point | Hiểu hành vi đang được quan sát |
| Trace ID | Một trace theo hợp đồng truy vết được sử dụng | Theo quy tắc hệ thống tracing | Ghép các span của một trace |
| Span ID | Một span cụ thể | Mỗi span mới | Phân biệt đơn vị công việc được trace |

**Correlation ID không mặc nhiên là trace ID**, và **Call ID không mặc nhiên là Span ID**. Một hệ thống có thể thiết kế chúng trùng nhau ở những ranh giới nhất định, nhưng phải mô tả rõ sự ánh xạ đó. Trong tracing phân tán, trace và span còn có ngữ nghĩa riêng về quan hệ cha–con và truyền qua ranh giới tiến trình.

Một ngữ cảnh lời gọi tối thiểu có thể được hình dung như sau:

~~~text
CallContext
  correlationId = "corr-8b29"
  callId        = "call-f17a"
  parentCallId  = "call-a803"
  operation     = "reserveInventory"
~~~

Có thể bổ sung mốc thời gian bắt đầu, thông tin chẩn đoán hoặc dữ liệu nghiệp vụ **được phép** ghi nhận. Không nên biến ngữ cảnh thành một “túi” chứa tùy ý toàn bộ request, mật khẩu, token truy cập hoặc thông tin cá nhân.

## 3. Mã tương quan được tạo ở ranh giới nào?

Điểm quan trọng nhất là xác định thế nào là **một luồng xử lý logic**. Có nhiều lựa chọn hợp lệ:

- Ở biên nhận yêu cầu: tạo một correlation ID cho mỗi yêu cầu mới; các lời gọi con thừa kế ID.
- Khi tiêu thụ một sự kiện từ hàng đợi: nhận ID theo hợp đồng thông điệp, hoặc tạo ID mới nếu đây thực sự là thao tác độc lập.
- Khi bắt đầu một công việc theo lịch: tạo ID cho từng lần chạy, không dùng một ID cố định cho mọi lần chạy.
- Khi xử lý một phần riêng của giao dịch: có thể dùng thêm mã con để phân biệt nhánh nhưng vẫn giữ mã gốc để truy vết quan hệ.

Không phải mọi phương thức match pointcut đều sinh **correlation ID mới**. Nếu advice thực hiện như vậy tại mọi lời gọi, log bên trong cùng một nghiệp vụ bị tách thành những nhóm khác nhau, làm mất mục đích tương quan.

Quy tắc thông dụng:

1. Nếu đã có correlation ID hợp lệ trong scope hiện tại, **kế thừa** mã đó.
2. Nếu chưa có, chọn/khởi tạo correlation ID tại ranh giới đầu vào thích hợp.
3. Với mỗi lần join point được instrument, tạo **call ID riêng** nếu cần phân tích chi tiết.
4. Ghi parentCallId bằng callId của scope hiện tại trước khi bước vào join point con.
5. Khi rời join point, **khôi phục scope cũ** thay vì xóa sạch toàn bộ ngữ cảnh.

Điều số 5 đặc biệt quan trọng: một lời gọi con kết thúc không được làm mất correlation ID hoặc call ID của lời gọi cha vẫn đang chạy.

## 4. Ngữ cảnh sống trong scope của lần gọi, không sống mãi trên thread

Giả sử join point xử lý việc đặt hàng kích hoạt một lời gọi con kiểm tra kho:

~~~text
Thread T1
  enter checkout    correlation=corr-01, call=call-01, parent=-
    enter inventory correlation=corr-01, call=call-02, parent=call-01
    exit  inventory -> khôi phục call=call-01
    enter payment   correlation=corr-01, call=call-03, parent=call-01
    exit  payment   -> khôi phục call=call-01
  exit  checkout    -> khôi phục ngữ cảnh trước checkout
~~~

Lời gọi con giữ cùng correlation ID, nhưng có call ID mới. Sau khi inventory kết thúc, context quay về checkout; không được giữ call-02 trên thread khi bắt đầu payment.

Sự khác biệt giữa **scope lời gọi** và **thread** thường bị bỏ qua:

- Một thread có thể lần lượt xử lý nhiều tác vụ độc lập. Nếu context không được xóa/khôi phục, tác vụ sau có thể mang mã của tác vụ trước.
- Một thao tác logic có thể chuyển qua nhiều thread. ThreadLocal của thread khởi tạo không tự xuất hiện trên thread thực thi tiếp theo.
- Một thao tác bất đồng bộ có thể sống lâu hơn thời gian phương thức gốc còn nằm trên call stack. Giữ ngữ cảnh trong ThreadLocal đến khi future hoàn tất bằng cách trì hoãn dọn dẹp thường là thiết kế sai.

Context theo lời gọi nên là một **giá trị có vòng đời rõ ràng**, còn ThreadLocal (nếu dùng) chỉ là cơ chế truy cập context trong đoạn thực thi đồng bộ trên một thread.

## 5. Vai trò của pointcut, join point và advice

Trong AOP, **pointcut** chọn các join point cần quan sát; **advice** xử lý tại các điểm ấy. Một around advice phù hợp cho bài toán này vì có thể bao quanh việc gọi hành vi đích:

~~~text
around(joinPoint):
    previous = currentContext()
    context  = createChildOrRootContext(previous, joinPoint)
    bind(context)
    try:
        logStart(context, joinPoint)
        result = proceed()
        return result
    catch error:
        recordFailure(context, error)
        throw error
    finally:
        recordExit(context)
        restore(previous)
~~~

Đây là **thuật toán khái niệm**, không phải cú pháp của một framework. Tên và khả năng của proceed, join point và lời khuyên trước/sau phụ thuộc cơ chế AOP đang dùng.

Điểm cần kiểm soát:

- **Lựa chọn phạm vi:** chọn các hàm tạo ranh giới nghiệp vụ hoặc kỹ thuật thực sự cần đo. Instrument toàn bộ hàm nhỏ có thể tạo quá nhiều sự kiện, chi phí cao và cây lời gọi khó đọc.
- **Thứ tự advice:** nếu nhiều aspect cùng bao quanh một join point, aspect nào thiết lập correlation ID trước sẽ quyết định advice còn lại có nhìn thấy context hay không. Phải quy định thứ tự khi cơ chế AOP hỗ trợ nhiều advice.
- **Mỗi lần kích hoạt:** lời gọi đệ quy và lặp nhiều lần cần một call ID riêng cho mỗi lần vào scope. Không dùng chung một biến mutable của đối tượng aspect làm trạng thái của từng lượt gọi.
- **Đường lỗi:** nếu proceed ném lỗi, advice vẫn dọn ngữ cảnh bằng finally và giữ nguyên ngoại lệ nghiệp vụ, trừ khi hợp đồng đã chủ đích quy định biến đổi nó.
- **Bảo toàn kết quả:** chỉ để ghi log/correlation, advice không cần thay đổi giá trị trả về, ngoại lệ hay luồng điều khiển của hành vi đích.

Advice ghi log có thể thất bại độc lập với nghiệp vụ. Trong hệ thống thật, logic telemetry nên được bảo vệ để sự cố ghi log không vô tình che mất ngoại lệ gốc hoặc khiến giao dịch thành công bị biến thành lỗi.

## 6. Ví dụ Java 17+ chạy được: tạo, kế thừa, truyền và khôi phục context

Ví dụ dùng một hàm around(...) để **mô phỏng** hành vi của advice trên các join point. ThreadLocal lưu scope hiện hành trên từng thread; một Context bất biến làm snapshot; hàm propagate(...) chụp scope hiện tại rồi gắn tạm vào tác vụ chuyển sang thread khác.

Để đầu ra kiểm chứng được, mã demo dùng bộ đếm tuần tự; **không dùng bộ đếm này tạo ID trong ứng dụng phân tán thực tế**, vì mỗi tiến trình có thể phát sinh mã trùng nhau. Phần tiếp theo trình bày chính sách ID phù hợp cho production.

Tạo tệp **CallContextDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class CallContextDemo {
    record Context(
            String correlationId,
            String callId,
            String parentCallId) {}

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private static final AtomicInteger IDS = new AtomicInteger();

    static void log(String event) {
        Context ctx = CURRENT.get();
        if (ctx == null) {
            System.out.println(event + " context=none");
        } else {
            System.out.println(event
                    + " corr=" + ctx.correlationId()
                    + " call=" + ctx.callId()
                    + " parent=" + ctx.parentCallId());
        }
    }

    static <T> T around(String operation, Supplier<T> proceed) {
        Context previous = CURRENT.get();

        int number = IDS.incrementAndGet();
        String callId = "call-%03d".formatted(number);
        String correlationId = previous == null
                ? "corr-%03d".formatted(number)
                : previous.correlationId();
        String parentId = previous == null ? "-" : previous.callId();

        Context current = new Context(correlationId, callId, parentId);
        CURRENT.set(current);
        try {
            log("enter " + operation);
            return proceed.get();
        } finally {
            log("exit " + operation);
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    static Runnable propagate(Runnable task) {
        // Capture scope ở thread đang lập lịch.
        Context captured = CURRENT.get();

        return () -> {
            // Thread thực thi có thể đang mang scope riêng.
            Context previous = CURRENT.get();
            try {
                if (captured == null) {
                    CURRENT.remove();
                } else {
                    CURRENT.set(captured);
                }
                task.run();
            } finally {
                // Trả thread thực thi về trạng thái ban đầu.
                if (previous == null) {
                    CURRENT.remove();
                } else {
                    CURRENT.set(previous);
                }
            }
        };
    }

    public static void main(String[] args) {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            around("checkout", () -> {
                log("business checkout");

                around("inventory", () -> {
                    log("business inventory");
                    return null;
                });

                CompletableFuture.runAsync(
                        propagate(() -> around("shipment", () -> {
                            log("business shipment");
                            return null;
                        })),
                        pool
                ).join();

                log("back checkout");
                return null;
            });

            System.out.println("main context clean="
                    + (CURRENT.get() == null));

            CompletableFuture.runAsync(
                    () -> System.out.println("worker context clean="
                            + (CURRENT.get() == null)),
                    pool
            ).join();

            try {
                around("failing", () -> {
                    throw new IllegalStateException("boom");
                });
            } catch (IllegalStateException expected) {
                System.out.println("caller received=" + expected.getMessage());
            }

            System.out.println("main after exception clean="
                    + (CURRENT.get() == null));
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac CallContextDemo.java
java CallContextDemo
~~~

Đầu ra:

~~~text
enter checkout corr=corr-001 call=call-001 parent=-
business checkout corr=corr-001 call=call-001 parent=-
enter inventory corr=corr-001 call=call-002 parent=call-001
business inventory corr=corr-001 call=call-002 parent=call-001
exit inventory corr=corr-001 call=call-002 parent=call-001
enter shipment corr=corr-001 call=call-003 parent=call-001
business shipment corr=corr-001 call=call-003 parent=call-001
exit shipment corr=corr-001 call=call-003 parent=call-001
back checkout corr=corr-001 call=call-001 parent=-
exit checkout corr=corr-001 call=call-001 parent=-
main context clean=true
worker context clean=true
enter failing corr=corr-004 call=call-004 parent=-
exit failing corr=corr-004 call=call-004 parent=-
caller received=boom
main after exception clean=true
~~~

Giải thích:

1. checkout là scope gốc nên sinh corr-001 và call-001.
2. inventory là lời gọi lồng nhau: correlation ID vẫn là corr-001, call ID đổi thành call-002 và parent trỏ tới call-001.
3. propagate chụp context của checkout **khi tạo Runnable**; thread trong pool nhận ngữ cảnh đó trước khi chạy shipment. Shipment vì vậy dùng corr-001, tạo call-003 và nhận call-001 làm parent.
4. Cuối mỗi scope, finally khôi phục context trước đó. Sau checkout, thread main không còn scope; sau shipment, thread pool không còn scope. Kiểm tra một tác vụ mới trên thread pool xác nhận không rò context.
5. failing ném ngoại lệ; finally vẫn chạy, sau đó người gọi vẫn nhận IllegalStateException như bình thường.

**Giới hạn của demo:** wrapper quanh Supplier/Runnable chỉ cho thấy thuật toán của advice và context propagation. Nó không dệt mã tại join point, không tự áp dụng cho mọi lời gọi trong chương trình và không thực hiện tracing phân tán. Khi dùng AOP thật, lựa chọn join point và phương thức proceed phải tuân theo ngữ nghĩa của công cụ dệt tương ứng.

## 7. Thiết kế mã tương quan thực tế

Trong môi trường nhiều tiến trình, mã tương quan phải đủ khó trùng lặp trong phạm vi quan sát dự kiến. Có thể dùng UUID phiên bản ngẫu nhiên do thư viện chuẩn Java cung cấp:

~~~java
import java.util.UUID;

String correlationId = UUID.randomUUID().toString();
~~~

Đây là **đoạn minh họa**, không phải chương trình độc lập. Đầu ra là một giá trị UUID thay đổi qua mỗi lần chạy.

Các nguyên tắc tạo và dùng mã:

- **Tạo một lần ở ranh giới gốc phù hợp**, sau đó kế thừa cho các lời gọi thuộc cùng luồng xử lý.
- **Không lấy ID theo thread ID**, vì thread pool tái sử dụng thread cho nhiều yêu cầu và một yêu cầu có thể đi qua nhiều thread.
- **Không dùng đồng hồ đơn thuần** làm mã duy nhất: nhiều tiến trình hoặc nhiều lời gọi đồng thời có thể tạo cùng giá trị.
- **Không dựa vào tính bí mật của ID** để kiểm soát truy cập. Correlation ID là khóa quan sát; nó không xác thực người gọi.
- **Định dạng dễ vận hành:** giới hạn độ dài, tập ký tự, chuẩn hóa cách đọc/ghi và nhãn log. Nếu nhận ID từ bên ngoài, xác thực giá trị theo chính sách trước khi giữ nguyên.
- **Phạm vi đủ rõ:** một yêu cầu, một tác vụ, một job run hoặc một luồng liên quan có thể là đơn vị khác nhau. Không dùng một correlation ID vĩnh viễn cho tất cả hành động của một người dùng.

Khi có ID từ phía gọi đến, có thể chấp nhận nếu ID hợp lệ theo hợp đồng và mức tin cậy của biên hệ thống. Nếu trống, sai định dạng hoặc vượt giới hạn, tạo mã mới và ghi nhận việc chuẩn hóa theo chính sách. Không giả định bất kỳ header do client cung cấp đều đáng tin.

Để tái dựng cây lời gọi, call ID cũng cần chính sách uniqueness thích hợp. Bộ đếm per-process có thể đủ cho một trace ngắn nếu ghép với process ID hoặc correlation ID theo hợp đồng, nhưng không nên xem call-001 là định danh toàn cục. Việc định dạng trong demo chỉ phục vụ học tập.

## 8. Lựa chọn cách giữ context

Một số chiến lược phổ biến trong Java và AOP:

### 8.1. Truyền context tường minh bằng tham số

~~~text
processOrder(context, order)
    -> reserveInventory(context, order)
    -> chargePayment(context, order)
~~~

Ưu điểm là phụ thuộc hiện rõ ở chữ ký, dễ kiểm thử và không phụ thuộc thread. Nhược điểm là cần thay đổi API và truyền qua nhiều tầng, đôi khi xâm lấn các phương thức nghiệp vụ.

### 8.2. Giữ context hiện hành trong ThreadLocal

Around advice thiết lập giá trị trong thời gian join point chạy, sau đó restore/remove trong finally. Ưu điểm là giảm việc chuyển tham số trong call stack đồng bộ; nhược điểm là rủi ro rò rỉ trên thread pool và không tự truyền sang thread khác.

Nếu trong context chứa đối tượng mutable được nhiều task cùng chia sẻ, việc copy tham chiếu sang thread khác **không biến nó thành thread-safe**. Ưu tiên snapshot bất biến và dữ liệu vừa đủ. Không giữ cả HTTP request, session hay đồ thị đối tượng lớn nếu chỉ cần một số ID.

### 8.3. Context carrier theo cơ chế runtime

Một số runtime cung cấp context scope hoặc cơ chế lan truyền riêng. Dù triển khai ra sao, cần đảm bảo ba tính chất:

1. Có thể xác định ngữ cảnh nào đang hoạt động tại join point.
2. Có thể truyền ngữ cảnh qua ranh giới thực thi cần thiết.
3. Có thể kết thúc scope và khôi phục ngữ cảnh trước đó một cách chắc chắn.

Không nên trộn nhiều cơ chế giữ context mà không xác định thứ tự ưu tiên và nguồn dữ liệu chuẩn; hai bản context khác nhau có thể khiến log cùng một tác vụ mang ID khác nhau.

## 9. Lời gọi lồng nhau, đệ quy, retry và fan-out

### 9.1. Lồng nhau và đệ quy

Một phương thức gọi chính nó ba lần có thể tạo ba call ID khác nhau trên cùng correlation ID, với quan hệ parent nối tiếp. Nếu mỗi lượt đệ quy tạo correlation ID mới, rất khó liên kết về lời gọi gốc.

Khôi phục ngữ cảnh theo nguyên tắc stack:

~~~text
before call A:  context=null
inside call A:  context=A
inside call B:  context=B (parent=A)
after call B:   context=A
after call A:   context=null
~~~

Việc restore đặc biệt cần thiết khi cùng thread đang xử lý lồng nhau; gọi remove() vô điều kiện ở cuối B sẽ làm mất A.

### 9.2. Retry

Một thao tác có thể retry ba lần. Có ít nhất hai mô hình hợp lý:

- **Một correlation ID cho yêu cầu logic**, thêm attempt number và call ID riêng cho từng lần thử.
- **Một correlation ID cho mỗi lần thử độc lập**, nhưng có một mã gốc liên kết tất cả lần thử.

Chọn theo câu hỏi vận hành cần trả lời. Nếu theo mô hình đầu, một lỗi trong attempt 1 không nhất thiết là kết quả cuối của request. Advice và metric phải phân biệt **attempt failed** với **operation failed**.

### 9.3. Fan-out sang nhiều tác vụ

Một join point có thể khởi tạo nhiều tác vụ nền. Tất cả tác vụ thuộc cùng luồng logic có thể kế thừa correlation ID chung, nhưng mỗi tác vụ/nhánh cần call ID riêng nếu có instrument. Không chia sẻ một đối tượng mutable và để các nhánh đồng thời ghi đè callId hiện tại.

~~~text
corr-42 / parent=call-01
    ├── task A -> call-02
    ├── task B -> call-03
    └── task C -> call-04
~~~

Khi hợp nhất kết quả, hãy mô tả rõ ranh giới hoàn tất: chỉ callback của task A xong không có nghĩa tất cả task B/C đã hoàn tất. Mã tương quan phục vụ liên kết sự kiện; nó **không tự cung cấp** cơ chế join hoặc đồng bộ các tác vụ.

### 9.4. Công việc độc lập được khởi tạo trong scope hiện tại

Không phải tác vụ nào được spawn từ một request cũng phải giữ cùng correlation ID. Ví dụ, gửi một công việc độc lập để chạy sau nhiều giờ có thể cần correlation ID riêng, cộng với liên kết “originating request”. Cần chọn theo ý nghĩa nghiệp vụ và chính sách giữ dữ liệu.

## 10. Lan truyền context qua ranh giới bất đồng bộ

**ThreadLocal không tự chuyển ngữ cảnh qua executor, future hoặc callback.** Nếu advice lưu ID vào ThreadLocal tại thread gọi, callback trên thread khác có thể đọc null hoặc dữ liệu cũ còn sót lại.

Một cách truyền đúng theo scope:

~~~text
Thread gọi:
    current = requestContext
    snapshot = capture(current)
    submit(wrap(task, snapshot))

Thread worker:
    old = currentContext
    install(snapshot)
    try:
        execute(task)
    finally:
        restore(old)
~~~

Trong đoạn Java ở trên, propagate(...) thực hiện đúng trình tự này. Cần capture trước khi context của thread gọi kết thúc; nếu capture quá muộn, snapshot có thể null hoặc thuộc lời gọi khác.

Có các trường hợp đáng lưu ý:

**CompletionStage/CompletableFuture.** Callback có thể chạy trên thread hoàn tất future, thread gọi hoặc executor được cấu hình, tùy API và kiểu callback. Không dựa vào ThreadLocal hiện hành của một thread cụ thể. Với callback cần context, hãy bọc callback bằng một scope snapshot riêng hoặc dùng carrier do runtime hỗ trợ. Việc lời gọi gốc trả về CompletableFuture không làm cho advice tự động bao phủ phần xử lý sau khi hoàn tất.

**Future được trả về ngay.** Advice quanh lời gọi tạo future chỉ bao quanh thời gian tạo/lấy handle. Nếu muốn log trạng thái cuối, phải quan sát completion theo hợp đồng; nếu muốn context trong callback, cần truyền context đến callback theo cách rõ ràng.

**Publisher hoặc API nhiều lượt tiêu thụ.** Việc tạo một publisher khác với việc có người subscribe. Một đối tượng publisher có thể có nhiều subscription, mỗi subscription nên có scope quan sát riêng. Kế thừa context lúc tạo publisher hay lúc subscribe là quyết định thiết kế phải được mô tả theo tính chất hot/cold và hợp đồng dữ liệu.

**Chuyển thread qua nhiều bước.** Mỗi bước có thể chuyển sang executor khác. Chỉ bọc Runnable ở bước đầu không đảm bảo toàn bộ callback về sau đều có context; cần propagation tại mọi ranh giới mà ngữ cảnh còn được dùng.

**Hủy và timeout.** Nếu callback cuối không chạy theo đường thông thường, cơ chế dọn ngữ cảnh không được phụ thuộc vào một sự kiện duy nhất chưa chắc được kích hoạt. Scope của worker phải được restore trong finally khi worker rời phần việc của nó; lifecycle kết quả bất đồng bộ được theo dõi riêng.

Không nên giữ ThreadLocal của thread gọi tồn tại đến khi tác vụ nền hoàn tất: thread gọi có thể đã được giao nhiệm vụ khác. Thay vào đó, giữ một snapshot bất biến để truyền theo từng đoạn thực thi.

## 11. Truyền mã giữa các tiến trình

Khi luồng xử lý vượt qua HTTP, hàng đợi hoặc giao thức RPC, ThreadLocal trong một tiến trình không có tác dụng ở tiến trình khác. Cần **đưa thông tin tương quan vào metadata trên thông điệp** theo hợp đồng đã thống nhất.

Ví dụ minh họa về ý tưởng, không phải một chuẩn header bắt buộc:

~~~text
Service A:
  correlationId=corr-8b29
  outgoing request:
    X-Correlation-ID: corr-8b29

Service B:
  nhận header và kiểm tra định dạng/chính sách tin cậy
  gắn correlationId=corr-8b29 trong scope xử lý
  tạo call/span mới tùy nhu cầu quan sát
  truyền tiếp khi gọi Service C
~~~

Nếu hệ thống đã sử dụng chuẩn truy vết phân tán như **W3C Trace Context**, cần tuân thủ ngữ nghĩa và định dạng các trường của chuẩn (ví dụ traceparent/tracestate) bằng công cụ tương thích. Đừng tự coi một chuỗi X-Correlation-ID là traceparent, hoặc tự lắp một trace ID không hợp lệ rồi gọi nó là dữ liệu tracing chuẩn.

Ở các ranh giới không tin cậy, hãy giới hạn độ dài, lọc ký tự điều khiển/ngắt dòng, xác định được nguồn gốc ID và có chính sách tạo mới. Không cho phép metadata tương quan thay thế kiểm tra quyền truy cập hoặc xác thực người dùng.

Với thông điệp được retry hoặc xử lý lại nhiều lần, correlation ID có thể giữ nguyên nhưng **lần xử lý** cần ID riêng. Điều này giúp phân biệt việc nhận một message nhiều lần với việc nghiệp vụ tạo nhiều message khác nhau.

## 12. Ghi log có cấu trúc và tránh gây nghẽn hệ thống quan sát

Log nên ghi các trường riêng thay vì nối tất cả metadata vào một chuỗi khó phân tích:

~~~text
timestamp=... level=INFO event=inventory.reserved
correlation_id=corr-8b29
call_id=call-f17a
parent_call_id=call-a803
operation=reserveInventory
outcome=success
duration_ms=12
~~~

Đây là **minh họa định dạng log**, không phải stdout của ví dụ Java. Tùy hệ thống, có thể dùng JSON hoặc bộ ghi log hỗ trợ thuộc tính có cấu trúc.

Nên phân biệt:

- **Log:** ghi correlationId, callId và operation để truy vấn chính xác.
- **Trace:** dùng trace/span theo mô hình tracing, lưu quan hệ cha–con và các mốc thời gian.
- **Metric:** đếm số lần gọi, độ trễ và lỗi theo nhãn có tập giá trị giới hạn như operation/outcome.

Không đưa correlation ID, call ID hoặc user ID có số lượng giá trị gần như vô hạn vào **label của metric có cardinality cao**, vì có thể làm tăng đột biến số time series và chi phí lưu trữ. ID cá biệt phù hợp hơn với log hoặc trace, trong khi metric tổng hợp nên có nhãn ổn định.

Advice quan sát cũng tạo overhead: sinh ID, tạo context, lưu log, đo thời gian, propagate callback. Hãy lựa chọn pointcut có chủ đích; có thể giảm số sự kiện thông qua lọc, sampling và tối ưu exporter. Dù sampling, vẫn phải thống nhất chính sách tạo/lan truyền ID nếu các thành phần phụ thuộc vào đó để liên kết sự kiện.

## 13. Quy tắc an toàn dữ liệu khi gắn ngữ cảnh

Không sử dụng context như một kênh truyền dữ liệu nhạy cảm không kiểm soát. Các nguyên tắc thực hành:

1. Dùng **ít trường cần thiết**: mã tương quan, mã lời gọi, tên thao tác, một vài thông tin chẩn đoán đã được phê duyệt.
2. Không ghi password, secret, access token, khóa riêng, nội dung định danh cá nhân hoặc toàn bộ payload vào log chỉ vì aspect có thể truy cập chúng.
3. Xem ID nhận từ client là dữ liệu bên ngoài. Trước khi cho vào log/header, xác thực độ dài và ký tự để tránh log injection và header injection.
4. Không dùng correlation ID để quyết định quyền truy cập, tenant hoặc người dùng đang đăng nhập; đây là dữ liệu quan sát.
5. Nếu cần tenant hoặc thông tin bảo mật để chạy nghiệp vụ, lấy từ nguồn đã được xác thực và truyền theo chính sách riêng, không tin giá trị metadata do bên gửi tự khai.
6. Xác định thời gian lưu dữ liệu log và quy tắc chia sẻ ID trên các ranh giới tổ chức.

Một ID trông ngẫu nhiên vẫn có thể được dùng để tra cứu hoạt động của người dùng. Do đó, việc công khai hay giữ ID trong log/tracing cũng cần chính sách dữ liệu phù hợp.

## 14. Những lỗi triển khai thường gặp

### 14.1. Tạo correlation ID mới tại mọi join point

**Hậu quả:** các log của checkout và inventory có ID khác nhau, không tìm được cùng một yêu cầu.

**Sửa:** chỉ tạo ở root nếu chưa có mã, các scope con kế thừa cùng correlation ID; dùng call ID riêng cho từng lượt gọi.

### 14.2. Chỉ xóa ThreadLocal ở cuối lời gọi con

**Hậu quả:** parent scope biến mất dù vẫn đang chạy.

**Sửa:** lưu previous trước khi set, cuối cùng restore previous; remove khi previous thực sự null.

### 14.3. Quên finally khi proceed ném lỗi

**Hậu quả:** thread pool tiếp tục chạy tác vụ khác trong context cũ; log bị gắn sai ID, có thể lộ dữ liệu giữa các yêu cầu.

**Sửa:** mọi đường ra khỏi scope đều đi qua finally, bao gồm ngoại lệ đồng bộ. Với callback và worker, từng đoạn thực thi cũng cần finally riêng.

### 14.4. Dùng biến instance của aspect làm currentCorrelationId

**Hậu quả:** nhiều thread/requests có thể ghi đè lẫn nhau nếu aspect được dùng chung.

**Sửa:** không đặt dữ liệu per-call vào state mutable dùng chung không đồng bộ; dùng context được scope hóa hoặc truyền tường minh. Khi có nhiều thread cùng chia sẻ context, tránh mutable state không an toàn.

### 14.5. Nghĩ rằng CompletableFuture tự lan truyền ThreadLocal

**Hậu quả:** callback chạy ở thread khác thiếu correlation ID.

**Sửa:** capture và bind context tại callback/task boundary; luôn restore context trước đó khi xong. Không giả định một executor tự truyền context nếu hợp đồng của nó không nói vậy.

### 14.6. Trộn lẫn correlation ID với call/span ID

**Hậu quả:** sơ đồ lời gọi không thể hiện đúng quan hệ cha–con, hoặc các lời gọi con bị tách thành trace không liên quan.

**Sửa:** xác định rõ mô hình ID, khi nào kế thừa và khi nào tạo mới; nếu có hệ thống tracing thì dùng đúng API/chuẩn của hệ thống đó.

### 14.7. Ghi ID vào metric labels

**Hậu quả:** số lượng chuỗi thời gian tăng theo số request, khiến metric khó vận hành.

**Sửa:** đưa ID vào log/tracing và dùng metric labels có cardinality giới hạn.

### 14.8. Chấp nhận mọi correlation ID đầu vào không kiểm tra

**Hậu quả:** giá trị quá dài hoặc có ký tự điều khiển có thể làm hỏng log, metadata hoặc truyền qua hệ thống khác.

**Sửa:** thiết lập giới hạn độ dài, ký tự hợp lệ, xử lý invalid và chính sách trust boundary; ID không mang quyền bảo mật.

### 14.9. Tạo context sau khi log bắt đầu hoặc kết thúc scope quá sớm

**Hậu quả:** một phần log đầu/cuối của thao tác không có cùng ID hoặc task nền bắt đầu sau khi context bị xóa mà không được capture.

**Sửa:** bind trước khi ghi sự kiện có ý nghĩa đầu tiên, capture trước khi lập lịch task, restore khi **đoạn thực thi hiện tại** kết thúc. Việc đo thời điểm hoàn tất logic của tác vụ async là trách nhiệm của observer riêng.

### 14.10. Ghi “operation success” khi join point chỉ vừa trả về handle

**Hậu quả:** future có thể hoàn tất lỗi sau đó nhưng log đã báo thành công.

**Sửa:** đặt tên sự kiện rõ ràng, chẳng hạn invocation-returned và async-completed. Callback hoàn tất phải phân biệt success, failure, cancel và timeout theo hợp đồng API.

## 15. Bảng kiểm trước khi triển khai một aspect tương quan

| Câu hỏi | Tiêu chí đạt |
| --- | --- |
| Root của luồng xử lý nằm ở đâu? | Có ranh giới tạo/nhận correlation ID rõ ràng |
| Join point nào được theo dõi? | Pointcut đủ chính xác; tránh lặp quá mức |
| Lời gọi con được định danh thế nào? | Giữ correlation ID, tạo call ID, ghi parent phù hợp |
| Khi lời gọi con kết thúc? | Khôi phục đúng context của cha |
| Khi có throw đồng bộ? | Không mất ngoại lệ gốc; finally dọn scope |
| Khi chuyển thread? | Capture, bind, execute và restore theo mỗi boundary |
| Khi lời gọi trả về future/publisher? | Phân biệt kết thúc invocation với hoàn tất logic |
| Khi đi qua dịch vụ khác? | Có metadata propagation, validate nguồn vào và đúng chuẩn tracing nếu dùng |
| Khi nhiều lời gọi chạy đồng thời? | Không có mutable per-call state dùng chung gây race |
| Khi ghi log/tracing/metric? | Tên trường thống nhất; ID không thành nhãn metric cardinality cao |
| Khi dữ liệu nhạy cảm có trong request? | Chỉ đưa metadata được phép, tránh secret/payload |
| Khi test? | Có nested, concurrent, async, exception, retry và khôi phục sau khi kết thúc |

## 16. Bài tập tự kiểm tra

**Tình huống 1.** Yêu cầu gốc có corr-A, lần gọi checkout là call-01. Nó gọi inventory hai lần nối tiếp. Hai log inventory cần những mã nào?

**Trả lời:** cả hai cùng corr-A, nhưng có hai call ID khác nhau; parentCallId có thể đều là call-01 nếu mỗi lần được gọi trực tiếp từ checkout.

**Tình huống 2.** Lời gọi inventory kết thúc và advice gọi CURRENT.remove(), trong khi checkout vẫn đang chạy. Điều gì sai?

**Trả lời:** checkout mất context của nó. Advice phải restore giá trị previous đã lưu khi bắt đầu inventory, không xóa vô điều kiện.

**Tình huống 3.** Checkout lập lịch một Runnable lên thread pool. Advice khôi phục ThreadLocal ở thread checkout ngay sau khi Runnable được submit. Làm thế nào để worker vẫn có mã tương quan?

**Trả lời:** chụp snapshot trước khi lập lịch, gắn snapshot trong scope của Runnable khi worker chạy, sau đó restore ThreadLocal của worker bằng finally. Dọn context ở thread checkout vẫn phải diễn ra đúng thời điểm.

**Tình huống 4.** Một future được trả về thành công nhưng hoàn tất bằng ngoại lệ sau 100 ms. Có nên log nghiệp vụ thành công ở after-returning advice không?

**Trả lời:** không. Đó chỉ là return của join point. Nếu cần outcome của tác vụ logic, theo dõi completion và ghi failure khi future hoàn tất ngoại lệ.

**Tình huống 5.** Hệ thống dùng UUID correlation ID và đưa ID đó làm label cho histogram latency. Có vấn đề gì?

**Trả lời:** mỗi yêu cầu có thể tạo một time series mới, gây cardinality rất cao. Thay bằng nhãn operation/outcome có tập giá trị nhỏ; giữ correlation ID trong log hoặc trace.

**Tình huống 6.** Một request có ba nhánh xử lý song song. Có nên chia sẻ cùng một đối tượng Context mutable để các nhánh cập nhật callId?

**Trả lời:** không, nếu các nhánh cùng sửa callId thì dễ xảy ra race và gắn log sai. Chia sẻ trường correlation ID bất biến; mỗi nhánh tạo scope và call ID riêng hoặc dùng cơ chế context propagation tương đương.

## 17. Ghi nhớ

Cách xây dựng aspect tương quan đáng tin cậy là: **nhận/tạo correlation ID tại ranh giới luồng xử lý, kế thừa ID xuống các lời gọi con, tạo call ID theo từng join point cần quan sát, gắn context trong scope, rồi luôn khôi phục context cũ**. Khi đi qua thread, callback hoặc dịch vụ khác, cần truyền context theo hợp đồng riêng. Khi quan sát future hoặc publisher, phải tách sự kiện trả về của lời gọi với việc hoàn tất công việc bất đồng bộ.

Mã tương quan giúp trả lời “những sự kiện này thuộc cùng một luồng xử lý nào?”, còn ngữ cảnh lời gọi giúp trả lời “sự kiện này diễn ra ở lượt thực thi nào và có quan hệ gì với các lượt khác?”. Aspect phát huy tác dụng khi áp dụng hai quy tắc đó nhất quán, đúng ranh giới và không làm đổi hành vi nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-observability-003">Đo số lần gọi, thời gian và tỷ lệ lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Đo số lần gọi, thời gian và tỷ lệ lỗi bằng Aspect-Oriented Programming (AOP)

## 1. Vì sao cần đo ở điểm cắt ngang?

Khi quan sát một chương trình, ba câu hỏi căn bản là:

1. **Một thao tác đã được gọi bao nhiêu lần?** Trả lời bằng bộ đếm (`call count`) và tốc độ gọi (`calls per second`).
2. **Mỗi thao tác mất bao lâu?** Trả lời bằng độ trễ (`latency`), tổng thời gian và phân phối thời gian; khi phù hợp còn phân biệt thời gian thực (`wall-clock elapsed time`) với thời gian CPU.
3. **Có bao nhiêu thao tác thất bại?** Trả lời bằng bộ đếm lỗi và tỷ lệ lỗi với một mẫu số được định nghĩa rõ.

Các phép đo này thường áp dụng cho nhiều phương thức hoặc thao tác, vì thế rất phù hợp với **Aspect-Oriented Programming (AOP)**. Một aspect gom logic quan sát vào một nơi và áp dụng nó tại nhiều điểm nối (`join point`), thay vì rải lệnh tăng counter và xem đồng hồ vào mọi thân phương thức nghiệp vụ. Những khái niệm quan trọng gồm:

- **Join point:** thời điểm/điểm thực thi có thể được can thiệp, ví dụ gọi phương thức hoặc thực thi phương thức; tập join point khả dụng tùy cơ chế AOP.
- **Pointcut:** điều kiện chọn join point nào cần đo.
- **Advice:** hành vi chạy trước, sau, khi trả kết quả, khi ném lỗi, hoặc bao quanh join point.
- **Around advice:** có thể bắt đầu phép đo, cho thao tác gốc thực thi, ghi nhận kết quả rồi trả nguyên giá trị hoặc ném lại lỗi. Nó thích hợp để quan sát kết quả thành công và thất bại trên cùng một đường code.
- **Weaving / proxy / instrumentation:** các cơ chế gắn advice vào luồng thực thi. Phạm vi chặn được lời gọi có thể khác nhau; cần xác minh điểm nào thực tế đã được instrument.

Mục tiêu không phải chỉ “in ra đã gọi”, mà là tạo **số đo có nghĩa, có thể so sánh và không làm đổi hợp đồng nghiệp vụ**.

## 2. Định nghĩa số đo trước khi viết advice

### 2.1 Số lần gọi khác số lần hoàn tất

Giả sử một request bắt đầu nhưng phải chờ I/O trong 10 giây. Trong khoảng chờ, hệ thống đã nhận một lần gọi, nhưng chưa có kết quả cuối. Do đó nên tách:

- `started_total`: số thao tác bắt đầu đi qua điểm đo.
- `completed_total`: số thao tác kết thúc và đã được ghi nhận.
- `failed_total`: số thao tác kết thúc theo định nghĩa lỗi.
- `in_flight`: số thao tác đang thực hiện. Trong cùng tập đối tượng đo và cùng cửa sổ tích lũy, về mặt khái niệm `in_flight ≈ started_total − completed_total`; cần tính đến snapshot không nguyên tử và chính sách hủy.
- `duration_seconds` hoặc `duration_nanos`: thời gian từng thao tác đã hoàn tất; dùng histogram hoặc cấu trúc tổng hợp để xem phân phối.

Nếu nói “số lần gọi” nhưng thực chất tăng counter ở `finally`, counter đó đếm **lần hoàn tất**, không phải số lần bắt đầu. Cả hai đều hữu ích, nhưng không được đặt nhầm tên.

### 2.2 Công thức tỷ lệ lỗi và mẫu số

Trong một khoảng thời gian được xác định trước:

`error_rate = failed / completed × 100%`

Ví dụ, có 240 thao tác kết thúc và 12 thao tác được phân loại lỗi: tỷ lệ lỗi là `12 / 240 × 100% = 5%`. Nếu không có thao tác hoàn tất (`completed = 0`), tỷ lệ lỗi **không xác định**; hiển thị “N/A”/không xuất rate sẽ trung thực hơn là giả vờ có 0% lỗi.

Mẫu số phải phù hợp mục tiêu:

| Chỉ số | Mẫu số / đơn vị | Phản ánh điều gì |
| --- | --- | --- |
| Tỷ lệ lỗi theo lần thực hiện (`attempt error rate`) | Mọi attempt đã hoàn tất | Xác suất một attempt thất bại, kể cả các lần retry |
| Tỷ lệ lỗi theo tác vụ logic (`operation error rate`) | Các yêu cầu logic đã kết thúc | Xác suất người dùng cuối nhận kết quả lỗi sau chính sách retry |
| Tỷ lệ lỗi theo request đến (`request failure rate`) | Số request có trạng thái cuối trong cửa sổ | Chất lượng phục vụ request; cần quy tắc trạng thái và timeout |
| Tốc độ lỗi (`error throughput`) | Số lỗi / giây | Tải lỗi tuyệt đối; **khác** tỷ lệ lỗi phần trăm |
| Tỷ lệ theo một phương thức | Lần hoàn tất của riêng phương thức đó | Chất lượng của từng điểm cắt ngang |

Nếu 1 request được thử tối đa ba lần, hai lần đầu lỗi và lần thứ ba thành công, ta có ba attempt, hai attempt lỗi, nhưng một tác vụ logic cuối cùng thành công. Trộn các cấp đo này sẽ làm dashboard hoặc cảnh báo sai lệch.

**Định nghĩa “lỗi” phải công khai:** exception thoát khỏi phương thức, một kết quả mang trạng thái thất bại, timeout, cancellation, mã trạng thái giao thức, hay một điều kiện domain cụ thể? Ví dụ mã nghiệp vụ “không tìm thấy” có thể là phản hồi hợp lệ thay vì lỗi hệ thống; ngược lại, phương thức trả về một đối tượng `Result.failure(...)` vẫn là lỗi dù không hề ném exception.

### 2.3 Đo thời gian với đồng hồ phù hợp

Khi tính **độ dài một khoảng** trong một JVM, dùng `System.nanoTime()`:

~~~java
long startedAt = System.nanoTime();
try {
    return operation.run();
} finally {
    long elapsedNanos = System.nanoTime() - startedAt;
    recordDuration(elapsedNanos);
}
~~~

Đây là **đoạn mã minh họa**: `operation` và `recordDuration` phải được cung cấp ở nơi sử dụng. `nanoTime()` dùng để đo khoảng thời gian tương đối, không phải dấu thời gian tuyệt đối. `System.currentTimeMillis()` dành cho thời điểm lịch và có thể bị điều chỉnh theo đồng hồ hệ thống; không nên lấy hai mốc thời gian lịch để thay phép đo elapsed khi cần độ ổn định.

Khoảng `elapsed` ở ví dụ là **wall-clock elapsed time**: nó tính cả lúc thread chờ lock, chờ I/O, bị scheduler tạm ngừng hoặc bị chặn. Không được gọi nó là “CPU time”. Thời lượng đo chính xác phụ thuộc điểm bắt đầu/kết thúc: chỉ thân phương thức, cả proxy/advice, hay toàn vòng đời tác vụ bất đồng bộ.

## 3. Vị trí đặt advice và những gì được đếm

Với một thao tác đồng bộ, mẫu đo quanh phương thức là:

~~~text
caller
  -> [điểm AOP: bắt đầu]
       -> tăng started
       -> lấy mốc nanoTime
       -> thực thi thao tác gốc đúng một lần
          -> thành công HOẶC ném exception
       -> ghi duration và kết quả
       -> tăng completed
     [kết thúc advice]
  -> caller nhận nguyên kết quả hoặc lỗi
~~~

Advice trước (`before`) có thể đếm lời gọi nhưng không trực tiếp biết kết quả. Advice sau khi trả (`after returning`) chỉ bao phủ lần trả thành công, trong khi advice khi ném (`after throwing`) cho thấy exception. **Around advice kết hợp cả hai nhánh** và hữu ích khi cần đo một khoảng thời gian duy nhất cho tất cả đường kết thúc.

Những quy tắc nền tảng:

- Thực thi logic gốc đúng **một lần** nếu phép đo là quan sát thuần túy. Gọi lại vì telemetry có thể nhân đôi side effect.
- Trả đúng giá trị, đúng kiểu và giữ exception gốc, không biến exception nghiệp vụ thành giá trị thành công hoặc ngoại lệ phản chiếu (`InvocationTargetException`).
- Ghi nhận cả đường thoát bất thường; `finally` giúp không bỏ sót duration khi thao tác ném lỗi.
- Tránh đo lặp ở các lớp lồng nhau nếu mục tiêu là đếm request logic một lần. Một lời gọi ngoài có thể đi qua nhiều join point; mỗi join point được đo sẽ tạo bộ đếm riêng.
- Quy định có đo lời gọi `Object.toString()`, `equals()`, getter, phương thức nội bộ, hay chỉ public API. Một số cơ chế proxy chỉ chặn được các lời gọi đi qua proxy; lời gọi nội bộ gọi trực tiếp trên đối tượng có thể không đi qua nó.
- Nếu aspect gọi thư viện metrics nhưng chính thư viện ấy lại bị cùng pointcut bắt, có thể xuất hiện đệ quy vô hạn. Loại trừ code instrumentation hoặc dùng guard tái nhập khi cần.

## 4. Ví dụ Java 17+: advice đo cuộc gọi, thời lượng và tỷ lệ lỗi

Ví dụ này dùng **JDK dynamic proxy** để biểu diễn around advice đối với phương thức của interface. Không cần thư viện AOP ngoài JDK. Kết quả được lưu bằng `ConcurrentHashMap` và `LongAdder` để hỗ trợ nhiều thread cập nhật. Chương trình gọi một thao tác thành công hai lần và cố ý tạo một lỗi một lần.

**Tệp `AopMetricsDemo.java`:**

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class AopMetricsDemo {
    public interface Catalog {
        String lookup(String code);
    }

    public static final class CatalogImpl implements Catalog {
        @Override
        public String lookup(String code) {
            if ("missing".equals(code)) {
                throw new IllegalArgumentException("not found");
            }
            return code.toUpperCase(Locale.ROOT);
        }
    }

    public static final class Stats {
        private final LongAdder completed = new LongAdder();
        private final LongAdder errors = new LongAdder();
        private final LongAdder durationNanos = new LongAdder();

        void record(long duration, boolean failed) {
            durationNanos.add(Math.max(0L, duration));
            if (failed) {
                errors.increment();
            }
            completed.increment();
        }

        long completed() {
            return completed.sum();
        }

        long errors() {
            return errors.sum();
        }

        double errorRatePercent() {
            long count = completed();
            return count == 0 ? Double.NaN : errors() * 100.0 / count;
        }

        double averageMillis() {
            long count = completed();
            return count == 0 ? Double.NaN
                : durationNanos.sum() / 1_000_000.0 / count;
        }
    }

    public static final class MetricsAdvice implements InvocationHandler {
        private final Object target;
        private final ConcurrentHashMap<String, Stats> registry;

        MetricsAdvice(Object target, ConcurrentHashMap<String, Stats> registry) {
            this.target = target;
            this.registry = registry;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            // Không tính các phương thức Object vào nghiệp vụ.
            if (method.getDeclaringClass() == Object.class) {
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            }

            String key = method.toGenericString();
            Stats stats = registry.computeIfAbsent(key, ignored -> new Stats());

            long startedAt = System.nanoTime();
            boolean failed = true;
            try {
                Object result = method.invoke(target, args);
                failed = false;
                return result;
            } catch (InvocationTargetException ex) {
                // Phản chiếu bọc lỗi; đưa lại đúng lỗi gốc cho caller.
                throw ex.getCause();
            } finally {
                stats.record(System.nanoTime() - startedAt, failed);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ConcurrentHashMap<String, Stats> registry = new ConcurrentHashMap<>();

        Catalog service = (Catalog) Proxy.newProxyInstance(
            Catalog.class.getClassLoader(),
            new Class<?>[] { Catalog.class },
            new MetricsAdvice(new CatalogImpl(), registry)
        );

        System.out.println(service.lookup("a1"));
        try {
            service.lookup("missing");
        } catch (IllegalArgumentException ex) {
            System.out.println("error=" + ex.getMessage());
        }
        System.out.println(service.lookup("b2"));

        String key = Catalog.class
            .getMethod("lookup", String.class)
            .toGenericString();
        Stats snapshot = registry.get(key);

        System.out.printf(
            Locale.ROOT,
            "completed=%d errors=%d errorRate=%.1f%%%n",
            snapshot.completed(),
            snapshot.errors(),
            snapshot.errorRatePercent()
        );
        // Có thể lấy snapshot.averageMillis() để xem độ trễ trung bình.
    }
}
~~~

Chạy với JDK 17 hoặc mới hơn:

~~~bash
javac AopMetricsDemo.java
java AopMetricsDemo
~~~

**Stdout xác định trong ví dụ đơn luồng:**

~~~text
A1
error=not found
B2
completed=3 errors=1 errorRate=33.3%
~~~

Tại sao các số đúng?

- Ba lần gọi `lookup` đi qua proxy đều chạy `method.invoke(target, args)` đúng một lần.
- Một lần thao tác gốc ném `IllegalArgumentException`; `InvocationTargetException` chỉ là lớp bọc của Java Reflection và được mở ra bằng `getCause()` để caller nhận lại lỗi gốc.
- `finally` ghi duration và trạng thái ở cả nhánh bình thường lẫn nhánh lỗi. `completed = 3`, `errors = 1`, nên `errorRate = 1/3 × 100 ≈ 33,3%`.
- `durationNanos` có được đo, nhưng kết quả thời gian phụ thuộc CPU, scheduler và tải máy; **không có giá trị millisecond cố định** để đưa vào stdout. `averageMillis()` tính trung bình của các lần hoàn tất.
- `method.toGenericString()` được dùng làm khóa cho riêng chữ ký phương thức; cách này tránh gộp nhầm các overload cùng tên trong ví dụ.

**Giới hạn của ví dụ:** Đây là một kỹ thuật gắn advice bằng dynamic proxy trên interface; nó chỉ đo lời gọi **đi qua proxy**. Nó không tự chặn lời gọi trực tiếp trên `CatalogImpl` hoặc lời gọi nội bộ không đi qua proxy. Nó đo **thời gian gọi đồng bộ**; nếu phương thức chỉ trả về `Future`/`CompletionStage` hoặc một nguồn sự kiện, duration trên chỉ là thời gian tạo và trả đối tượng bất đồng bộ. Ở ứng dụng lớn, cần thêm chính sách telemetry failure, tagging, histogram và cấu hình điểm cắt ngang.

### Lưu ý về bộ đếm nhiều thread

`LongAdder` thích hợp khi có nhiều thread đồng thời tăng counter, nhưng `sum()` **không là ảnh chụp nguyên tử** của toàn bộ các counter. Một bộ đọc có thể thấy `errors` và `completed` ở các thời điểm sát nhau; giá trị tỷ lệ trong lúc có lưu lượng là gần đúng tại một thời điểm. Không dùng sự chênh nhất thời giữa các counter để kết luận hệ thống đã vi phạm logic. Nếu cần tổng kết giao dịch tài chính chính xác, dùng nguồn sự kiện/ghi nhận nhất quán hơn, thay vì lấy một metrics scrape làm dữ liệu kế toán.

Tương tự, ba lần gọi trực tiếp trong demo không định nghĩa tốc độ gọi. Để tính `calls/second`, cần thêm khoảng thời gian đo hoặc tính `rate` từ chênh lệch bộ đếm giữa các mẫu liên tiếp.

## 5. Khi số liệu được đo trên tác vụ bất đồng bộ

Một around advice ngay tại phương thức không nhất thiết quan sát được lúc thao tác thực sự hoàn tất.

### 5.1 Với CompletionStage / CompletableFuture

Giả sử phương thức `fetch()` trả về `CompletionStage<Result>`. Lời gọi có thể chỉ tạo stage rồi trả ngay. Đếm “success” tại lúc trả stage sẽ sai nếu stage hoàn tất thất bại sau đó.

Một thiết kế quan sát hợp lý:

1. Khi lời gọi tạo tác vụ, ghi `started` và mốc thời gian.
2. Khi stage hoàn tất, ghi kết quả, độ trễ end-to-end và `completed` **một lần**.
3. Nếu stage hoàn tất exceptional, phân loại lỗi theo **nguyên nhân gốc và chính sách nghiệp vụ**, tránh đếm `CompletionException` chỉ như một mã lỗi chung.
4. Nếu stage bị hủy, định nghĩa riêng trạng thái `cancelled` và quyết định rõ nó có được tính vào `failed` hay không.
5. Tránh để callback telemetry ném exception ảnh hưởng kết quả mà caller quan sát.

**Mã khái niệm**, minh họa một biến thể của advice dành cho stage (không phải chương trình độc lập):

~~~java
long startedAt = System.nanoTime();
CompletionStage<Result> stage = proceedAndGetStage();

stage.whenComplete((result, error) -> {
    long elapsed = System.nanoTime() - startedAt;
    try {
        telemetry.recordCompletion(elapsed, error);
    } catch (RuntimeException telemetryError) {
        // Ghi nhận ở kênh dự phòng phù hợp; không thay kết quả gốc.
    }
});
return stage;
~~~

`proceedAndGetStage()`, `Result` và `telemetry` là thành phần giả định của ứng dụng. Việc bỏ qua stage phụ thuộc được trả về từ `whenComplete` giúp caller nhận **chính stage gốc**, nhưng callback vẫn được đăng ký để quan sát. Cách xử lý lỗi telemetry cụ thể phải phù hợp chính sách: không nên để lỗi instrumentation lan tới thread xử lý hoàn tất hoặc làm thay đổi trạng thái nghiệp vụ.

Điểm tinh tế: `whenComplete` có thể chạy trên thread hoàn tất stage và đôi khi chạy ngay nếu stage đã xong. Không thực hiện I/O chặn nặng, ghi log khối lượng lớn hoặc xử lý histogram tốn kém trong callback này.

### 5.2 Với luồng sự kiện nhiều phần tử

Một publisher có thể đăng ký nhiều subscriber, phát nhiều `onNext`, gặp `onError` hoặc `onComplete` và nhận `cancel`. Cần chọn **đơn vị đo**:

| Đơn vị | Bắt đầu | Kết thúc | Có thể đo |
| --- | --- | --- | --- |
| Tạo đối tượng nguồn sự kiện | Lời gọi phương thức trả publisher | Phương thức trả về | Chi phí tạo nguồn, không phải thời gian chạy stream |
| Một subscription | `subscribe` / `onSubscribe` theo hợp đồng đo | `onComplete`, `onError` hoặc cancellation theo chính sách | Thời gian vòng đời subscription; số terminal/error/cancel |
| Một sự kiện | Sự kiện được phát hoặc callback `onNext` bắt đầu | Callback xử lý xong, hoặc ack nghiệp vụ | Tốc độ giao/nhận và thời gian xử lý mỗi phần tử |
| Một request logic đi qua nhiều nguồn | ID request bắt đầu | Có kết quả cuối cho request | Độ trễ end-to-end và trạng thái cuối người dùng nhận |

Phải giữ nguyên cơ chế **backpressure**: advice theo dõi không được tự tăng `request(n)`, tạo subscriber phụ chỉ để đếm, hoặc giữ bộ đệm vô hạn. Số sự kiện nhận được sau một subscription không tương đương số lời gọi API ban đầu. Với một nguồn được đăng ký ba lần, số kết quả có thể khác nhau tùy nguồn là nóng/lạnh và side effect của lần subscribe.

### 5.3 Đồng bộ và bất đồng bộ dùng hai “thời gian” khác nhau

Một API async có thể cần hai histogram: `submit_latency` (thời gian tạo/trả stage hoặc publisher) và `completion_latency` (thời gian đến kết quả cuối). Nếu đặt cùng tên mà không phân biệt, dashboard sẽ trộn những đại lượng không cùng nghĩa. Với thao tác timeout, độ trễ nên được gắn trạng thái timeout và xét rõ thời gian từ mốc nào đến mốc nào.

## 6. Average, percentiles và histogram: đọc độ trễ đúng cách

Từ các lần hoàn tất có thời gian `d1, d2, ..., dn`:

- **Tổng thời gian:** `sum(d_i)`, hữu ích để tính trung bình trên đúng tập.
- **Mean (trung bình):** `sum(d_i) / n` nếu `n > 0`. Nó dễ bị kéo lên bởi vài lần rất chậm.
- **Min/max:** giúp biết biên nhưng không cho thấy toàn bộ phân phối.
- **p50:** khoảng phân vị 50%; một nửa quan sát có độ trễ không vượt giá trị này theo quy ước ước lượng đã chọn.
- **p95/p99:** khoảng phân vị cao; thể hiện trải nghiệm của những lần chậm hơn. Kết quả là **ước lượng** nếu dùng histogram/xấp xỉ.

Ví dụ có mười lần đo (ms): `1, 1, 2, 2, 2, 3, 3, 4, 5, 100`. Trung bình là `12,3 ms` dù chín lần đầu đều tối đa 5 ms. Vì vậy “trung bình 12,3 ms” che khuất phần đuôi 100 ms. Nên theo dõi histogram và phân vị phù hợp mục tiêu chất lượng phục vụ.

Trong hệ thống tải cao, không giữ mọi sample vô hạn. Có thể dùng histogram với các bucket đã được chọn theo SLO hoặc một bộ phác họa phân phối có giới hạn bộ nhớ. Nếu thống kê cho nhiều instance:

- Tổng `calls`, `errors` và tổng duration có thể cộng sau khi đã chuẩn hóa đơn vị, nhãn và cửa sổ.
- **Không lấy trung bình đơn giản của trung bình từng instance** nếu lưu lượng khác nhau; hãy dùng trung bình có trọng số theo số lần hoàn tất.
- **Không tính p99 toàn hệ thống bằng trung bình cộng p99 của các instance**. Cần gộp histogram hoặc bản tóm lược phân phối có thuật toán hỗ trợ merge và cùng định nghĩa bucket.
- Mọi so sánh trước/sau cần cùng loại operation, tập trạng thái, định nghĩa lỗi, đơn vị thời gian và độ dài cửa sổ.

## 7. Thiết kế khóa metrics, nhãn và cửa sổ thời gian

Một metric thường gồm: **tên + bộ nhãn cố định + giá trị**. Ví dụ khái niệm:

~~~text
operation_completed_total{operation="catalog.lookup",outcome="success"} 920
operation_completed_total{operation="catalog.lookup",outcome="error"} 30
operation_duration_seconds_count{operation="catalog.lookup"} 950
operation_duration_seconds_sum{operation="catalog.lookup"} 12.4
~~~

Đây là **ví dụ cấu trúc dữ liệu metrics**, không phải stdout từ chương trình `AopMetricsDemo`. Tổng 950 lần hoàn tất, 30 lỗi cho tỷ lệ khoảng `3,16%` trên tập dữ liệu này. Tổng duration 12,4 giây trên 950 lần cho trung bình khoảng `13,05 ms`; nếu tổng duration chỉ tính lần thành công, mẫu số cũng phải đổi để nhất quán.

### Các nhãn nên và không nên gắn

**Nên:** tên thao tác cố định, nhóm endpoint đã chuẩn hóa, loại lỗi theo danh mục hữu hạn, môi trường chạy, trạng thái thành công/thất bại.

**Cẩn thận hoặc tránh:** ID request, email, user ID, URL có token, chuỗi SQL động, giá trị đầu vào tự do, stack trace đầy đủ, tên tệp ngẫu nhiên. Mỗi giá trị nhãn khác nhau tạo thêm series và có thể gây **cardinality explosion**, tăng bộ nhớ và chi phí lưu trữ. Thông tin định danh và dữ liệu nhạy cảm còn gây rủi ro bảo mật. Dùng log/traces có phân quyền thích hợp cho chi tiết từng yêu cầu, còn metrics nên tổng hợp theo danh mục thấp cardinality.

### Counter tích lũy và số liệu trong một khoảng

Một counter thường chỉ tăng từ lúc process khởi động. **Số gọi trong 5 phút** là chênh lệch counter trong cửa sổ 5 phút, có xử lý reset do khởi động lại; **tỷ lệ lỗi 5 phút** được tính từ chênh lệch `failed_total` chia chênh lệch `completed_total` cùng cửa sổ và cùng tập nhãn.

Không lấy `failed_total / completed_total` từ khi ứng dụng chạy để đại diện cho “tình trạng 5 phút vừa qua”: lỗi lịch sử có thể che khuất sự cố mới. Cửa sổ quá ngắn tạo nhiễu với lưu lượng ít; cửa sổ quá dài phản ứng chậm. Cảnh báo thường cần cả **tỷ lệ lỗi và số mẫu tối thiểu** để tránh diễn giải quá mức khi chỉ có một request thất bại.

## 8. Các bẫy AOP nâng cao làm sai số liệu

| Bẫy | Ví dụ | Khắc phục |
| --- | --- | --- |
| Chọn pointcut quá rộng | Instrument cả collector làm collector tự gọi chính nó | Loại trừ mã instrumentation và giới hạn API cần đo |
| Chặn nhiều tầng | Một request đi qua API, service, repository, tạo ba count | Đặt metric theo tên tầng; định nghĩa request-level riêng |
| Gọi gốc hai lần | Advice vô ý `proceed()` lần thứ hai | Chỉ thực thi một lần cho advice đo thuần túy |
| Bỏ nhánh lỗi | Chỉ cập nhật metric khi nhận `return` bình thường | Bắt cả trả thành công và ném lỗi; ghi ở `finally` |
| Bọc exception mới | Caller nhận loại exception lạ | Bảo toàn lỗi gốc và stack/causal chain |
| Telemetry có lỗi | Registry báo lỗi, ném exception ngay trong `finally` | Có fallback rõ ràng; cô lập lỗi quan sát theo chính sách |
| Giá trị trả async | Tăng success ngay khi nhận một future chưa xong | Gắn callback vòng đời và định nghĩa terminal |
| Nhiều thread | `count++` chạy song song gây mất cập nhật | Dùng atomic/adder, snapshot nhất quán theo nhu cầu |
| Đồng hồ không phù hợp | Dùng thời gian lịch giữa hai mốc elapsed | Dùng `nanoTime()` để lấy chênh lệch thời gian |
| Trộn các đơn vị | Nơi ghi nano giây, nơi ghi mili giây vào một metric | Chuẩn hóa tên và đơn vị (ví dụ giây cho exported duration) |
| Nhãn quá chi tiết | Một metric theo user ID hoặc URL đầy đủ | Dùng nhãn nhóm có cardinality thấp |
| Retry và cache | Retry làm tăng attempts; cache tránh gọi DB | Phân tầng logical operations, attempts, cache hits |
| Sampling thiếu cân nhắc | Chỉ lấy 1/100 mẫu nhưng trình bày như tổng số thực | Đếm counter chính xác khi có thể; ghi rõ xác suất mẫu và sai số |
| Nguồn đo tác động tải | Log từng lời gọi bằng I/O đồng bộ | Batch/aggregate telemetry có giới hạn, đo overhead thực tế |

### Mức overhead và hiện tượng observer effect

AOP không miễn phí: lựa chọn điểm cắt, proxy, đo `nanoTime`, ghi bảng metrics, tạo nhãn, lấy stack trace hay gửi dữ liệu ra ngoài đều tốn CPU và bộ nhớ. Nếu thao tác gốc chỉ vài microsecond, chi phí advice có thể chiếm phần lớn kết quả. Khi đo, nên:

1. Tránh cấp phát object hoặc format chuỗi trên mỗi lần gọi nếu chỉ cần đếm.
2. Lấy `nanoTime()` tối thiểu hai lần; không ghi log đồng bộ dưới lock nóng.
3. Tránh vừa ghi duration, vừa thực hiện telemetry nặng **bên trong khoảng thời gian đang đo** nếu mục tiêu là thời gian nghiệp vụ. Trong ví dụ, `Stats` được lấy trước mốc bắt đầu, rồi ghi sau mốc kết thúc.
4. Cân nhắc số phương thức bị đo, chi phí khởi tạo proxy và độ chính xác cần thiết. Với thao tác cực ngắn, benchmark phương thức có/không advice để biết overhead.
5. Với yêu cầu độ tin cậy cao, xác định telemetry gặp lỗi thì fail-open hay fail-closed; thường giám sát không nên làm thay đổi kết quả nghiệp vụ, nhưng quyết định cần theo chính sách cụ thể.

Các số đo latency của proxy cũng có thể bị ảnh hưởng bởi JIT warm-up, GC, contention, CPU throttling và tình trạng tải. Một số lần chạy demo không chứng minh được đặc tính hiệu năng của production.

## 9. Kiểm thử độ đúng của advice và metric

### Kiểm thử đơn vị

Tạo một thao tác trả kết quả cố định, một thao tác ném exception xác định, rồi xác minh:

- Mỗi thao tác gốc được gọi đúng một lần, return value và exception gốc được giữ nguyên.
- Số completed tăng một lần cho mỗi kết thúc; số failed tăng đúng những kết thúc được định nghĩa là lỗi.
- Không có lần gọi: tỷ lệ lỗi là không xác định (`NaN` hoặc N/A), không chia cho 0.
- Duration không âm theo biểu diễn lưu trữ, đơn vị nhất quán; với clock có thể thay thế trong thiết kế test, kiểm tra chính xác duration thay vì dựa vào `Thread.sleep`.
- Lời gọi không qua proxy không vô tình được tính vào metric; các method `Object` được xử lý theo chủ đích.
- Khi có hai overload/operation, khóa metric không vô tình gộp hai tập số đo khác nhau.

### Kiểm thử cạnh tranh và tải

Dùng nhiều thread đồng thời gọi cùng một điểm cắt để tìm mất cập nhật, tranh chấp trên registry và ảnh hưởng overhead. Nếu mỗi thread chạy `m` thao tác và có `t` thread, sau khi **tất cả thread đã kết thúc và mọi ghi metrics hoàn tất**, tổng completed dự kiến là `m × t` (trừ những lần bị loại trừ theo cấu hình). Trong lúc luồng đang chạy, snapshot của nhiều counter có thể lệch ngắn hạn; test chỉ nên kiểm tra invariant cuối sau khi tất cả cập nhật đã hoàn tất.

### Kiểm thử vòng đời bất đồng bộ

Xác minh kết quả hoàn tất thành công, exceptional completion, cancel, timeout, task chưa hoàn tất, completion ngay lập tức và race giữa callback. Mục tiêu là một logical operation có đúng một terminal outcome, không tăng success ngay tại lúc trả stage, và không bỏ sót duration của lần đã được tính là completed. Với streaming, kiểm tra hủy và error theo từng subscription, không giả định một lời gọi tạo publisher tương ứng duy nhất một subscriber.

### Kiểm thử chất lượng số đo sau triển khai

So sánh counter AOP với số giao dịch/trace ở tầng biên phù hợp, phân tích sai lệch do sampling, retry, cache và pointcut. Kiểm tra dashboard theo khoảng thời gian: tỷ lệ lỗi gần 0% ở tải lớn có đúng không, tình trạng ít request có bị gây hiểu lầm không, p95/p99 có bị trộn loại thao tác, histogram có đủ độ phân giải tại ngưỡng chất lượng không. Đặt cảnh báo dựa trên số đo đồng nhất, có chú thích đơn vị và ý nghĩa mẫu số.

## 10. Tổng kết kiến thức cần nhớ

- **Số lần bắt đầu**, **số lần hoàn tất**, **số lần lỗi** là ba counter khác nhau; chỉ tính tỷ lệ lỗi khi mẫu số, thời gian và quy tắc phân loại được nêu rõ.
- **Around advice** đo thao tác đồng bộ bằng `nanoTime()` trước/sau, chuyển tiếp kết quả và lỗi gốc. `finally` giúp ghi nhận cả trường hợp ném exception.
- Với **bất đồng bộ**, việc trả stage/publisher chưa phải kết thúc; gắn đo đạc với vòng đời hoàn tất, subscription hoặc event tùy đối tượng cần đo.
- **Độ trễ trung bình** không đủ phản ánh đuôi phân phối; dùng histogram và p95/p99 khi bài toán cần phân tích các lần chậm.
- **Quan sát phải có chi phí và phạm vi được kiểm soát:** tránh pointcut quá rộng, đếm kép, counter không an toàn đa luồng, nhãn vô hạn và telemetry làm thay đổi hành vi nghiệp vụ.

Một aspect đo lường đúng là aspect có **định nghĩa số liệu rõ ràng**, **đặt tại điểm thực thi phù hợp** và **giữ nguyên ngữ nghĩa của chương trình được quan sát**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-observability-004">Phân biệt số liệu kỹ thuật với sự kiện nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt số liệu kỹ thuật với sự kiện nghiệp vụ trong quan sát chương trình bằng AOP

## 1. Vì sao hai loại thông tin dễ bị nhầm lẫn?

Aspect-Oriented Programming (AOP) cho phép bổ sung hành vi xuyên suốt như đo thời gian, ghi log, thống kê lỗi và tracing tại các **join point** đã chọn. Một *around advice* có thể biết rằng phương thức `placeOrder()` được gọi, mất bao lâu và kết thúc bằng giá trị trả về hay ngoại lệ. Tuy nhiên, chỉ từ những dấu hiệu đó, advice **chưa thể kết luận** rằng “đơn hàng đã được đặt thành công” theo ý nghĩa nghiệp vụ.

Hai câu sau có nghĩa khác nhau:

- **“Có một lần gọi `placeOrder()`, chạy trong 12 ms và trả về bình thường.”** Đây là thông tin quan sát kỹ thuật về một lần thực thi.
- **“Đơn hàng O-100 đã được chấp nhận và trở thành đơn hàng hợp lệ.”** Đây là một sự kiện nghiệp vụ, phải được xác nhận bằng quy tắc và ranh giới trạng thái của domain.

Một lời gọi có thể trả về bình thường nhưng giao dịch lưu dữ liệu vẫn bị rollback ở bước sau; một lời gọi có thể bị retry nhiều lần nhưng chỉ tạo một đơn hàng; một tác vụ bất đồng bộ có thể đã được nhận vào hàng đợi nhưng chưa thực hiện. Vì thế, dùng log/metric tự động từ AOP làm “bằng chứng đã xảy ra sự kiện nghiệp vụ” sẽ dẫn tới đếm sai, phát thông báo sai hoặc tạo lịch sử nghiệp vụ không đáng tin.

**Mục tiêu học tập:** nhận diện nguồn và ý nghĩa của dữ liệu quan sát, chọn nơi đặt advice, giữ quyền quyết định sự kiện nghiệp vụ ở domain, và thiết kế phép đo không làm thay đổi kết quả chương trình.

## 2. Hai khái niệm và sự khác nhau cốt lõi

### 2.1. Số liệu kỹ thuật (technical telemetry)

Số liệu kỹ thuật mô tả **hệ thống vận hành như thế nào**. Chúng thường gồm:

- **Metric:** giá trị định lượng có thể tổng hợp qua thời gian, chẳng hạn số lời gọi, tỷ lệ lỗi, độ trễ, kích thước hàng đợi, bộ nhớ đang sử dụng.
- **Log:** bản ghi về tình huống, thao tác hoặc ngoại lệ tại một thời điểm; thường có mức độ nghiêm trọng và ngữ cảnh.
- **Trace/span:** chuỗi hoặc đoạn hoạt động dùng để lần theo đường đi của một yêu cầu qua những lời gọi, tác vụ và thành phần khác nhau.

AOP phù hợp để thu thập những thông tin này vì có thể can thiệp lặp lại vào một nhóm join point mà không phải chèn cùng đoạn code đo thời gian vào từng phương thức. Dữ liệu kỹ thuật thường dùng để trả lời: “Có gì chậm?”, “Lỗi xảy ra ở đâu?”, “Bao nhiêu lần gọi đã được thực hiện?”.

### 2.2. Sự kiện nghiệp vụ (business/domain event)

Sự kiện nghiệp vụ là **một điều đã xảy ra và có ý nghĩa đối với domain**, thường đặt tên ở thì quá khứ: `OrderPlaced`, `PaymentAuthorized`, `InventoryReserved`. Nó mô tả sự thay đổi hoặc một sự thật nghiệp vụ mà hệ thống khác có thể cần xử lý.

Một sự kiện đáng tin cần xác định rõ:

1. **Điều kiện phát sinh:** quy tắc/domain nào cho phép nói rằng sự kiện đã xảy ra?
2. **Mốc hiệu lực:** thay đổi đã được chấp nhận trong bộ nhớ, hoàn tất giao dịch hay được xác nhận bởi hệ thống bên ngoài?
3. **Danh tính:** sự kiện nào, thuộc thực thể nào; có thể phân biệt với một lần phát lại không?
4. **Dữ liệu:** những trường tối thiểu để người nhận hiểu sự kiện.
5. **Chủ sở hữu ý nghĩa:** thành phần nghiệp vụ nào quyết định khi nào phát sinh?

Ví dụ `OrderPlaced` có thể cần `eventId`, `orderId`, `occurredAt`, `schemaVersion` và trạng thái/giá trị đơn hàng được chấp nhận. Các trường này **không** đồng nghĩa với tên phương thức, số lần gọi hoặc độ dài một span.

| Tiêu chí | Quan sát kỹ thuật | Sự kiện nghiệp vụ |
| --- | --- | --- |
| Câu hỏi chính | Hệ thống đã thực thi thế nào? | Điều gì đã xảy ra trong domain? |
| Ví dụ | `order.place.duration=12ms`; span `OrderService.place` | `OrderPlaced(orderId=O-100)` |
| Nguồn xác thực | Join point, runtime, logger, collector | Quy tắc domain và ranh giới trạng thái đã xác nhận |
| Tần suất | Có thể ghi mỗi lần gọi, retry, lỗi | Theo sự thật nghiệp vụ; thường cần chống trùng |
| Nhãn/dữ liệu | Label ít biến thể để tổng hợp; trace ID để tương quan | ID sự kiện/thực thể, payload có ý nghĩa nghiệp vụ |
| Độ bền | Tùy hệ thống lưu telemetry, chính sách lấy mẫu và thời gian giữ | Theo yêu cầu vận hành/audit/tích hợp nghiệp vụ |
| Hệ quả khi mất một bản ghi | Giảm khả năng chẩn đoán hoặc thống kê | Có thể làm sai lệch tiến trình tích hợp hoặc lịch sử nghiệp vụ |
| Xử lý lỗi | Lỗi ghi telemetry thường không nên đổi kết quả nghiệp vụ | Lỗi tạo/lưu/phát sự kiện phải theo hợp đồng nhất quán đã thiết kế |

Một **log có câu chữ nghiệp vụ** vẫn chỉ là log nếu không có hợp đồng sự kiện, bảo đảm lưu trữ và xử lý trùng. Một **metric đếm “đặt đơn thành công”** chưa phải số lượng đơn hàng nếu nó chỉ đếm lần phương thức trả về mà không đối chiếu trạng thái đã xác nhận.

## 3. Những trường hợp dễ phát sinh số liệu sai

Xét luồng nhận yêu cầu đặt hàng:

```text
Yêu cầu đặt đơn
  -> advice ghi một lần gọi kỹ thuật
  -> kiểm tra dữ liệu và quy tắc
  -> thay đổi trạng thái nghiệp vụ
  -> commit thay đổi
  -> phát sự kiện theo chính sách tin cậy
  -> xử lý bất đồng bộ ở các thành phần khác
```

Các kịch bản có thể quan sát được:

| Kịch bản | Số liệu kỹ thuật có thể ghi | Sự kiện nghiệp vụ phù hợp |
| --- | --- | --- |
| Dữ liệu không hợp lệ, hàm ném ngoại lệ | 1 invocation, 1 failure, thời gian xử lý | Không có `OrderPlaced` |
| Một đơn hợp lệ được xác nhận | 1 invocation, 1 success | 1 `OrderPlaced` theo hợp đồng |
| Client timeout rồi retry cùng thao tác | 2 invocations; có thể 1 timeout và 1 success | Có thể chỉ 1 `OrderPlaced` nếu idempotency bảo đảm |
| Lời gọi trả về nhưng transaction rollback sau đó | Advice có thể thấy “return success” | Không được xác nhận `OrderPlaced` nếu hợp đồng đòi commit |
| Phương thức chỉ gửi tác vụ vào hàng đợi | 1 lần submit thành công | Chưa thể suy ra tác vụ nghiệp vụ đã hoàn tất |
| Sự kiện đã ghi bền nhưng gửi lên broker bị retry | Nhiều lần gửi hoặc nhận kỹ thuật | Vẫn là một sự kiện logic, người nhận cần chống xử lý trùng |

Như vậy:

```text
method_invocation_count  != business_event_count
method_returned_normally != transaction_committed
task_submitted           != task_completed
event_delivery_count     != unique_business_event_count
```

Các dấu `!=` biểu diễn những đại lượng **không mặc nhiên bằng nhau**, không phủ nhận việc chúng đôi khi có giá trị bằng nhau trong một trường hợp cụ thể.

## 4. AOP chịu trách nhiệm phần nào?

### 4.1. AOP quan sát lời gọi, không xác nhận domain event

Trong AOP, **join point** là vị trí có thể can thiệp; **pointcut** chọn vị trí và **advice** thực hiện phép đo. **Around advice** có thể lấy thời gian bằng `System.nanoTime()` quanh `proceed()`, còn after-returning và after-throwing quan sát hai kiểu kết thúc của chính lời gọi. Phạm vi thực tế phụ thuộc cơ chế interception/weaving.

**Các tín hiệu đó xác nhận điều xảy ra tại một lần thực thi, không tự xác nhận việc commit hoặc phát sinh sự kiện nghiệp vụ.** Dù advice nhìn thấy return thành công, quyền định nghĩa `OrderPlaced` vẫn thuộc về logic domain và ranh giới trạng thái đáng tin cậy.

### 4.2. Ranh giới trách nhiệm

**Nên giao cho aspect quan sát kỹ thuật:**

- Đếm số lần gọi và lỗi theo loại thao tác.
- Đo độ trễ của join point hoặc giai đoạn bất đồng bộ được khai báo rõ.
- Gắn trace/span và tương quan các lời gọi.
- Ghi log chẩn đoán đã loại bỏ dữ liệu nhạy cảm.
- Theo dõi số lần cố gắng publish sự kiện, độ trễ gửi, lỗi kết nối.

**Nên giao cho logic nghiệp vụ và cơ chế lưu/phát sự kiện:**

- Xác định khi nào `OrderPlaced` thực sự có hiệu lực.
- Xác minh điều kiện domain, transaction và idempotency.
- Tạo định danh, phiên bản, payload sự kiện.
- Đảm bảo thay đổi dữ liệu và việc lưu sự kiện được xử lý nhất quán.
- Quy định retry, chống trùng, thứ tự và chính sách xử lý lỗi.

AOP có thể giúp **quan sát quá trình phát sự kiện**, hoặc gắn metadata kỹ thuật vào một envelope theo hợp đồng được phê duyệt, nhưng không nên tự tạo một `OrderPlaced` chỉ vì phương thức tên `placeOrder` đã trả về. Đổi tên phương thức, tách lời gọi, retry hoặc thay đổi ranh giới transaction sẽ làm quy tắc ngầm ấy sai lệch.

### 4.3. Pseudocode: advice đo kỹ thuật

```text
around(joinPoint):
    t0 = monotonicClock.now()
    success = false
    try:
        value = proceedExactlyOnce(joinPoint)
        success = true
        return value
    finally:
        elapsed = monotonicClock.now() - t0
        try:
            technicalMetrics.record(
                operation = stableOperationName,
                outcome = success ? SUCCESS : ERROR,
                duration = elapsed
            )
        catch telemetryError:
            reportTelemetryProblemWithoutChangingBusinessResult()
```

Đây là **pseudocode AOP**, không phải cú pháp Java độc lập. Đồng hồ đơn điệu thích hợp để tính thời lượng; thời gian lịch (wall-clock) hữu ích để đóng dấu `occurredAt` cho sự kiện nhưng không thay thế đồng hồ đơn điệu khi đo latency.

Một aspect tốt gọi `proceed()` đúng một lần khi hợp đồng không đòi retry, bảo toàn giá trị trả về và ngoại lệ. Nếu việc thu thập metric là best-effort, lỗi từ metric sink không được biến thao tác nghiệp vụ thành thất bại hoặc che mất ngoại lệ gốc.

## 5. Ví dụ Java 17 hoàn chỉnh: hai nguồn thông tin độc lập

Ví dụ dưới dùng **decorator** để mô phỏng một around advice ở ranh giới gọi phương thức. `OrderService` là nơi quyết định và tạo sự kiện nghiệp vụ; `TimingAspect` chỉ thu thập metric kỹ thuật. Mẫu này hoạt động bằng Java thuần và có thể được áp dụng thành advice/weaving trong một hệ thống AOP độc lập.

Tạo tệp **AopObservationDemo.java**:

```java
import java.util.ArrayList;
import java.util.List;

public class AopObservationDemo {
    record OrderPlaced(String orderId, long amountVnd) {}

    interface EventSink {
        void publish(OrderPlaced event);
    }

    static final class InMemoryEventSink implements EventSink {
        final List<OrderPlaced> events = new ArrayList<>();

        @Override
        public void publish(OrderPlaced event) {
            events.add(event);
        }
    }

    interface OrderUseCase {
        void place(String orderId, long amountVnd);
    }

    static final class OrderService implements OrderUseCase {
        private final EventSink eventSink;

        OrderService(EventSink eventSink) {
            this.eventSink = eventSink;
        }

        @Override
        public void place(String orderId, long amountVnd) {
            if (orderId == null || orderId.isBlank() || amountVnd <= 0) {
                throw new IllegalArgumentException("invalid order");
            }
            // Ví dụ bộ nhớ: sau khi chấp nhận, domain phát sinh event.
            eventSink.publish(new OrderPlaced(orderId, amountVnd));
        }
    }

    record Observation(String operation, String outcome, long durationNanos) {}

    static final class TechnicalMetrics {
        final List<Observation> observations = new ArrayList<>();

        void record(String operation, String outcome, long durationNanos) {
            observations.add(new Observation(operation, outcome, durationNanos));
        }
    }

    // Decorator minh họa around advice ở ranh giới thực thi phương thức.
    static final class TimingAspect implements OrderUseCase {
        private final OrderUseCase target;
        private final TechnicalMetrics metrics;

        TimingAspect(OrderUseCase target, TechnicalMetrics metrics) {
            this.target = target;
            this.metrics = metrics;
        }

        @Override
        public void place(String orderId, long amountVnd) {
            long started = System.nanoTime();
            boolean success = false;
            try {
                target.place(orderId, amountVnd); // tương đương proceed()
                success = true;
            } finally {
                long elapsed = System.nanoTime() - started;
                try {
                    metrics.record("order.place", success ? "SUCCESS" : "ERROR", elapsed);
                } catch (RuntimeException ignored) {
                    // Telemetry không được thay đổi kết quả nghiệp vụ.
                }
            }
        }
    }

    public static void main(String[] args) {
        InMemoryEventSink events = new InMemoryEventSink();
        TechnicalMetrics metrics = new TechnicalMetrics();
        OrderUseCase service = new TimingAspect(new OrderService(events), metrics);

        service.place("O-100", 250_000);
        try {
            service.place("O-101", -1);
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }

        System.out.println("technical.count=" + metrics.observations.size());
        System.out.println("technical.outcomes=" + metrics.observations.stream()
                .map(Observation::outcome).toList());
        System.out.println("business.count=" + events.events.size());
        System.out.println("business.event=" + events.events.get(0));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac --release 17 AopObservationDemo.java
java AopObservationDemo
```

**Stdout chính xác:**

```text
rejected: invalid order
technical.count=2
technical.outcomes=[SUCCESS, ERROR]
business.count=1
business.event=OrderPlaced[orderId=O-100, amountVnd=250000]
```

Giải thích:

1. Lần đầu có lời gọi hợp lệ. `OrderService` tạo một `OrderPlaced`; `TimingAspect` ghi một observation SUCCESS.
2. Lần sau có lời gọi không hợp lệ. Advice vẫn quan sát một lần gọi ERROR, nhưng `OrderService` không phát ra `OrderPlaced`.
3. Vì vậy **2 số liệu về lần gọi kỹ thuật** đi cùng **1 sự kiện nghiệp vụ**. Không có mâu thuẫn: hai nguồn mô tả hai loại sự thật khác nhau.
4. `durationNanos` được đo bằng `System.nanoTime()`, nhưng chương trình cố ý không in một con số thời gian cố định vì độ trễ thực tế thay đổi theo mỗi lần chạy.

**Giới hạn cố ý của ví dụ:** dữ liệu và event sink đều ở bộ nhớ, không có transaction, broker, đa luồng hay bảo đảm lưu bền; `ArrayList` chỉ phù hợp với tình huống một luồng minh họa. Khi ứng dụng thực dùng concurrency, collector phải an toàn luồng và tránh tạo điểm nghẽn. Đoạn code không chứng minh tính nguyên tử giữa ghi cơ sở dữ liệu và phát sự kiện; vấn đề đó được giải quyết ở phần tiếp theo.

## 6. Transaction, outbox và ranh giới “đã xảy ra”

Khi một sự kiện tuyên bố “đơn hàng đã đặt”, hệ thống cần xác định mốc nào khiến lời tuyên bố đó đúng. Với nghiệp vụ dựa trên giao dịch cơ sở dữ liệu, chỉ thực thi thành công phương thức **chưa chắc** đồng nghĩa với commit thành công.

### 6.1. Sự cố phát sinh từ việc tạo event trong advice

Ví dụ có thể xảy ra:

```text
around advice: enter placeOrder()
  -> domain cập nhật trạng thái
  -> placeOrder() trả về bình thường
  -> after-returning advice tự publish OrderPlaced
  -> transaction commit bị lỗi / rollback
Kết quả: bên nhận đã thấy "OrderPlaced" nhưng DB không có đơn đã commit.
```

Điểm mấu chốt là **thời điểm advice quan sát sự trở về không nhất thiết là ranh giới commit**. Vị trí transaction nằm ở đâu và advice bao quanh lớp nào quyết định thứ tự thật. Do đó, chỉ dựa vào tên phương thức hoặc `return normally` để phát event có thể sai.

### 6.2. Mẫu transactional outbox (khái niệm)

Khi yêu cầu sự kiện phản ánh trạng thái commit và được phát đáng tin:

1. Logic domain xác nhận điều kiện phát sinh `OrderPlaced`.
2. Trong **cùng một transaction cơ sở dữ liệu**, lưu trạng thái đơn hàng và một bản ghi outbox chứa event ID, loại, payload, aggregate ID và trạng thái gửi.
3. Commit transaction. Nếu rollback, cả thay đổi nghiệp vụ lẫn bản ghi outbox cùng bị huỷ.
4. Một bộ chuyển tiếp đọc các bản ghi outbox đã commit và gửi tới nơi nhận.
5. Nếu gửi lỗi, bộ chuyển tiếp retry; người nhận dùng **event ID/idempotency** để tránh xử lý sự kiện logic nhiều lần.

```text
BEGIN TRANSACTION
    update order state
    insert outbox(OrderPlaced, eventId, orderId, payload)
COMMIT
          |
          v
    outbox relay --retry--> event broker/consumer
                                  |
                                  v
                       deduplicate by eventId
```

Outbox giúp tránh trường hợp “ghi DB thành công nhưng publish thất bại rồi mất sự kiện”, **không tự mang lại exactly-once delivery**. Relay có thể gửi trùng nếu crash sau khi gửi nhưng trước khi đánh dấu đã gửi; consumer vẫn cần idempotency. Nếu transaction không hỗ trợ outbox chung, phải lựa chọn cơ chế nhất quán phù hợp và nêu rõ giới hạn bảo đảm.

Advice có thể đo `outbox.insert.duration`, `event.publish.attempts` hoặc `event.delivery.failure`; chính sách quyết định event nào được lưu và lúc nào có hiệu lực vẫn thuộc domain và kiến trúc dữ liệu.

### 6.3. Retry và đếm riêng các lớp

Một lần `OrderPlaced` logic có thể tương ứng với nhiều lượt HTTP, nhiều attempt xử lý message và nhiều lần publish. Cần phân biệt các đại lượng:

- `request.attempt.count`: có bao nhiêu yêu cầu/attempt kỹ thuật đi qua ranh giới.
- `order.placed.event.count`: có bao nhiêu sự kiện logic được tạo hoặc ghi bền, xác định theo ID duy nhất.
- `event.delivery.attempt.count`: có bao nhiêu lần thử chuyển sự kiện.
- `event.consumer.processed.count`: có bao nhiêu xử lý thực sự thành công sau quy tắc chống trùng.

Các tên trên chỉ minh họa cách đặt tên metric, không phải API chuẩn. Phải ghi rõ đếm **lần thử**, **sự kiện duy nhất**, hay **thực thi sau chống trùng**. Nếu bộ thu metric nhận trùng thì chính các metric cũng có thể lệch; báo cáo tài chính/nghiệp vụ chính thức nên dựa vào nguồn trạng thái bền vững được kiểm soát.

## 7. Log và audit log có phải sự kiện nghiệp vụ không?

Không nên quyết định dựa trên tên công cụ lưu trữ. **Định nghĩa và hợp đồng dữ liệu** mới quan trọng:

- **Log chẩn đoán:** “`placeOrder completed in 12ms`”, dùng để vận hành, có thể được lấy mẫu hoặc xoá theo thời hạn.
- **Audit record:** “người dùng X yêu cầu thay đổi Y lúc T”, nhằm chứng minh hành vi truy cập/thao tác và tuân theo chính sách audit; không nhất thiết chứng minh thay đổi đã có hiệu lực.
- **Domain event:** “`OrderPlaced` đã có hiệu lực đối với đơn O-100”, có cấu trúc và ý nghĩa nghiệp vụ rõ, tiêu thụ bởi các workflow khác.

Một audit record được lưu bền và định nghĩa như sự kiện trong kiến trúc cụ thể có thể được xử lý như một loại event; tuy nhiên **không thể suy ra mọi log/audit record là domain event**. Ví dụ “người dùng nhấn nút Thanh toán” khác “thanh toán đã được ghi nhận thành công”.

Ngay cả khi aspect ghi log có thông tin `orderId`, đó cũng không đủ để làm log trở thành bằng chứng nghiệp vụ. Trước khi dùng một dòng log để kích hoạt xử lý nghiệp vụ, phải có hợp đồng về ý nghĩa, tính bền, thứ tự, chống trùng, kiểm soát truy cập và xử lý lỗi.

## 8. Quan sát ranh giới bất đồng bộ mà không tạo sự kiện giả

Khi một lời gọi trả về `CompletionStage`, `Future` hoặc chỉ đẩy `Runnable` vào executor, advice quanh phương thức tạo việc chỉ biết **chi phí tạo/gửi**. Tác vụ có thể chờ hàng đợi, chạy, thất bại hoặc bị huỷ sau khi lời gọi trả về.

```text
t0: advice vào enqueue()
t1: enqueue() trả về handle      -> submission latency = t1 - t0
t2: worker bắt đầu chạy          -> queue wait = t2 - t1 (chỉ khi các mốc phù hợp)
t3: worker hoàn thành xử lý
t4: observer nhìn thấy completion -> observed completion = t4 - t0
```

Muốn đo completion phải gắn observer vào handle hoặc instrument trực tiếp thân tác vụ. `CompletionStage.whenComplete(...)` là cách quan sát không chặn, nhưng thời điểm callback chạy **có thể muộn hơn lúc tác vụ thực sự hoàn tất**; callback không có nghĩa là transaction đã commit hay sự kiện nghiệp vụ đã phát. Không dùng `Future.get()` hoặc `join()` ngay trong advice nếu yêu cầu giữ bản chất bất đồng bộ.

Một luồng ví dụ:

```text
Task submitted (technical)
   -> Task started (technical)
   -> Domain validates order (business rule)
   -> Transaction commits (business state)
   -> OrderPlaced persisted in outbox (business event)
   -> Observer sees completion (technical)
```

Nếu task thất bại sau khi được gửi, telemetry vẫn có `submission.success` nhưng không có `OrderPlaced` hợp lệ. Nếu task thực hiện nhiều lệnh nghiệp vụ, một `completion` kỹ thuật chưa thể thay thế các sự kiện tương ứng với từng thay đổi.

Trong trace phân tán, **trace ID** và **correlation ID** giúp tìm các bản ghi liên quan. Event ID dùng để nhận diện sự kiện logic và chống trùng; một trace có thể chứa nhiều event, một event có thể được xử lý trong nhiều trace. Đừng dùng trace ID như event ID một cách mặc định.

## 9. Thiết kế dữ liệu để không lẫn lộn ý nghĩa

### 9.1. Metric cho vận hành

Nên đặt các tên phản ánh đúng phạm vi đo, ví dụ:

```text
use_case.invocation.total{operation="order.place",outcome="success"}
use_case.invocation.total{operation="order.place",outcome="error"}
use_case.duration.seconds{operation="order.place"}
event.publish.attempt.total{event_type="OrderPlaced",outcome="error"}
```

Đây là **các tên và label minh họa**, không khẳng định cú pháp bắt buộc của một thư viện đo đạc. `operation`, `outcome`, `event_type` là label có tập giá trị hữu hạn, phù hợp để tổng hợp. Không đưa `orderId`, `customerId`, event ID hoặc toàn bộ exception message vào label metric vì chúng có cardinality cao, tăng số lượng chuỗi thời gian, tốn bộ nhớ và làm giảm khả năng vận hành.

Ngược lại, bản thân event có thể cần `orderId` và `eventId` trong payload theo hợp đồng nghiệp vụ. Không áp dụng hạn chế cardinality của metric cho danh tính nghiệp vụ trong bản ghi sự kiện.

### 9.2. Envelope cho sự kiện nghiệp vụ

Ví dụ một biểu diễn cấu trúc:

```json
{
  "eventId": "evt-100",
  "eventType": "OrderPlaced",
  "schemaVersion": 1,
  "aggregateId": "O-100",
  "occurredAt": "2026-10-20T02:15:00Z",
  "payload": {
    "amountVnd": 250000
  },
  "correlationId": "req-900"
}
```

Các giá trị là **dữ liệu minh họa**, không phải một sự kiện đã xảy ra thực. `occurredAt` thường là thời điểm hiệu lực do hệ thống phát sự kiện định nghĩa; `publishedAt` nếu có là thời điểm gửi đi và có thể muộn hơn. `correlationId` hỗ trợ truy vết, không thay thế `eventId` để chống trùng. Hợp đồng phải quy định phiên bản schema và cách consumer xử lý thay đổi.

### 9.3. Tránh làm rò rỉ dữ liệu

Aspect quan sát nhiều điểm gọi nên đặc biệt dễ thu thập quá mức. Không tự động log mọi tham số, token, mật khẩu, thông tin thanh toán hoặc toàn bộ payload event. Chọn trường cần thiết, ẩn dữ liệu nhạy cảm và áp dụng chính sách lưu trữ. Một event nghiệp vụ cũng phải giới hạn dữ liệu trong payload theo nhu cầu người nhận.

## 10. Lỗi thiết kế phổ biến và cách sửa

| Sai lầm | Hệ quả | Cách thiết kế lại |
| --- | --- | --- |
| Tạo `OrderPlaced` từ after-returning của phương thức tên `placeOrder` | Có thể phát event khi transaction rollback hoặc phương thức chỉ gửi việc | Domain xác định điều kiện; lưu event nhất quán với trạng thái |
| Đếm số lần gọi thành số đơn mới | Retry và lỗi làm số liệu phình lên | Tách invocation metric khỏi unique event/state |
| Coi event publish thành công là consumer đã xử lý | Không nhìn thấy lỗi hoặc độ trễ phía nhận | Theo dõi publish, delivery và processing riêng |
| Thêm `orderId` vào metric label | Cardinality tăng mạnh, metric khó tổng hợp | Dùng label ít biến thể; ID ở log/trace/event theo chính sách |
| Ghi event nghiệp vụ chỉ bằng log text | Không có schema, tính bền, replay và idempotency rõ ràng | Sử dụng hợp đồng event, ID, lưu trữ và phân phối phù hợp |
| Advice ghi log/metric ném lỗi làm use case thất bại | Quan sát trở thành tác nhân gây lỗi | Đảm bảo recorder best-effort hoặc xử lý lỗi theo hợp đồng rõ ràng |
| Gọi `join()` khi instrument async | Chặn luồng, thay đổi latency thực tế | Theo dõi callback hoặc mốc thực thi riêng |
| Một nghiệp vụ qua nhiều tầng đều bị pointcut bắt | Đếm nhiều invocation cho cùng một operation | Chọn ranh giới instrumentation; chuẩn hóa operation name |
| Tưởng không có trace là không có event | Trace có thể bị sampling/mất dữ liệu | Lấy nguồn sự kiện từ trạng thái/event store bền vững |

## 11. Kiểm thử đúng cả hai loại thông tin

Một bộ kiểm thử đủ sức phát hiện việc lẫn ranh giới nên bao gồm:

1. **Đường thành công:** xác nhận một thao tác nghiệp vụ hợp lệ tạo đúng event theo hợp đồng; metric ghi nhận invocation và thời gian.
2. **Đầu vào sai:** advice vẫn ghi error và thời lượng; không được tạo `OrderPlaced`.
3. **Rollback sau khi phương thức trả về:** nếu event đòi commit, không có event đã xác nhận; một bản ghi “method returned” kỹ thuật vẫn có thể tồn tại.
4. **Retry có cùng idempotency key:** số attempt tăng nhưng số event logic duy nhất không tăng theo.
5. **Broker hoặc consumer nhận trùng:** event ID không đổi khi retry; consumer không thực hiện tác dụng phụ nhiều lần.
6. **Tác vụ gửi thành công nhưng xử lý thất bại:** submission metric vẫn đúng, completion metric phản ánh thất bại, domain event chỉ tồn tại nếu điều kiện nghiệp vụ của nó đã đạt.
7. **Lỗi collector:** telemetry thất bại không che ngoại lệ hoặc thay đổi trạng thái nghiệp vụ, nếu chính sách telemetry là best-effort.
8. **Kiểm tra hiệu năng:** advice gây ít overhead, không tạo vòng gọi đệ quy vô hạn qua logger/collector, và việc bật/tắt quan sát không đổi kết quả domain.

Trong ví dụ Java ở trên, có thể kiểm ngay bất biến: `technical.count == 2`, `business.count == 1`, một lần ERROR không phát sinh event. Với ứng dụng có database và broker, cần kiểm thử tích hợp transaction/outbox thay vì chỉ kiểm thử method-call mock.

## 12. Cách tự xác định “đây là telemetry hay business event?”

Khi định thu thập một thông tin bằng AOP, hãy hỏi:

- **Nếu đổi cách cài đặt mà không đổi kết quả nghiệp vụ, thông tin này có thể đổi không?** Nếu có, rất có thể đó là telemetry: số method call, stack trace, latency, số retry.
- **Nếu telemetry collector mất dữ liệu thì sự thật nghiệp vụ có còn tồn tại không?** Một `OrderPlaced` đã commit không được phụ thuộc vào việc span có được lấy mẫu hay không.
- **Ai có quyền tuyên bố sự kiện đã xảy ra?** Phải là quy tắc domain và mốc hiệu lực đã định nghĩa, không phải tên pointcut.
- **Người tiêu thụ dùng nó để làm gì?** Dashboard hiệu năng dùng metric/trace; một workflow gửi hàng cần event đáng tin và cơ chế xử lý trùng.
- **Một lời gọi nhiều lần có thể cùng một sự thật nghiệp vụ không?** Nếu có retry/idempotency, phải tách invocation count khỏi event count.

**Nguyên tắc thực hành:** để AOP đo **hoạt động của phần mềm tại các ranh giới kỹ thuật**, còn domain quyết định **sự kiện nào thật sự có ý nghĩa và có hiệu lực**. Hai lớp có thể chia sẻ correlation ID để chẩn đoán, nhưng cần giữ riêng thời điểm, định danh, bảo đảm lưu trữ và ý nghĩa của từng loại dữ liệu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-observability-005">Che dữ liệu nhạy cảm và giới hạn kích thước tham số</a>

<details>
<summary>Xem chi tiết</summary>

# Che dữ liệu nhạy cảm và giới hạn kích thước tham số trong Aspect (AOP)

## 1. Mục tiêu

Khi sử dụng **Aspect-Oriented Programming (AOP)** để quan sát chương trình, một advice có thể nhìn thấy phương thức được gọi, danh sách đối số, kết quả trả về, ngoại lệ và thời gian thực thi. Khả năng chặn tại nhiều điểm gọi rất hữu ích để ghi log, trace và đo hiệu năng, nhưng cũng tạo ra một đường rò rỉ thông tin tập trung: chỉ một interceptor ghi tất cả tham số có thể khiến mật khẩu, mã truy cập, dữ liệu cá nhân hoặc payload lớn xuất hiện ở hệ thống log.

Bài này hướng dẫn thiết kế **chính sách quan sát an toàn ở lớp AOP**: phân loại tham số, che hoặc bỏ qua dữ liệu nhạy cảm, chỉ cho phép xuất các trường đã duyệt, giới hạn độ lớn trước khi dựng bản ghi, rồi kiểm tra cả dữ liệu lẫn đường đi của bản ghi quan sát.

Kết quả mong đợi:

- Biết **cần che gì, che ở đâu, ở thời điểm nào** trong chu trình around/before/after advice.
- Phân biệt ***redact*** (thay bằng ký hiệu), ***omit*** (không xuất), ***truncate*** (cắt có giới hạn) và ***aggregate*** (chỉ xuất thông tin tổng hợp).
- Tính giới hạn theo **số tham số**, **số byte UTF-8 mỗi giá trị**, **tổng byte mỗi sự kiện**, độ sâu của cấu trúc và số phần tử.
- Tránh gọi `toString()`, serializer, formatter hoặc mã có tác dụng phụ trên đối tượng tùy ý chỉ để tạo log.
- Xử lý injection vào log, lỗi telemetry, ngoại lệ nghiệp vụ, và nguy cơ vô tình lộ dữ liệu từ các kênh quan sát khác.

Trọng tâm là **AOP tổng quát**, áp dụng cho interception theo proxy, weaving hoặc interceptor tương đương. Chi tiết điểm chặn thay đổi theo kỹ thuật hiện thực, nhưng nguyên tắc bảo vệ dữ liệu và giới hạn tài nguyên vẫn giữ nguyên.

## 2. Vì sao AOP quan sát dễ làm lộ dữ liệu?

Một lời gọi tưởng như bình thường:

```java
paymentService.charge(cardNumber, securityCode, amount, customerProfile);
```

Nếu một advice ghi `Arrays.toString(args)` hoặc biến toàn bộ đối số thành JSON, nó có thể đưa cả số thẻ, mã bảo mật và thông tin khách hàng vào log. Log thường được sao chép qua nhiều chặng: console, file, collector, queue, hệ thống lưu trữ, dashboard và bản xuất hỗ trợ sự cố. Mỗi chặng có chính sách truy cập, thời gian lưu và hệ thống sao lưu riêng.

Rủi ro không giới hạn ở mật khẩu. Cần rà soát theo ngữ cảnh nghiệp vụ:

| Loại dữ liệu | Ví dụ | Cách xử lý mặc định |
|---|---|---|
| Bí mật xác thực | Mật khẩu, khóa API, access/refresh token, cookie phiên | **Không ghi giá trị**; redact hoặc omit |
| Dữ liệu cá nhân | Email, số điện thoại, địa chỉ, hồ sơ khách hàng | Omit hoặc ghi dạng tối thiểu đã được duyệt |
| Dữ liệu tài chính/y tế hoặc hồ sơ riêng tư | Số tài khoản, bệnh án, định danh | Không đưa vào log tổng quát |
| Nội dung người dùng | Tin nhắn, văn bản nhập, biểu mẫu, URL chứa query bí mật | Mặc định ẩn; chỉ xuất trường đã xét duyệt |
| Dữ liệu kích thước lớn | File, byte array, ảnh, JSON sâu, collection dài | Metadata tổng hợp và giới hạn số lượng/kích thước |
| Thông tin vận hành ít nhạy cảm | Tên operation cố định, cờ trạng thái đã duyệt, mã lỗi nội bộ | Có thể allowlist và giới hạn chặt |

Không thể xác định nhạy cảm chỉ bằng kiểu dữ liệu: cùng một `String` có thể là trạng thái `"APPROVED"` hoặc một token. Tên biến như `password` cũng không đủ: tên tham số có thể vắng trong bytecode, bị đổi hoặc nằm sâu trong một DTO. Một tập regex tìm `password|token|secret` có thể hỗ trợ phát hiện bổ sung, nhưng không đủ làm cơ chế bảo vệ chính.

**Nguyên tắc ưu tiên:** *không xuất dữ liệu chưa được phê duyệt*. Redaction theo tên trường là một lớp bổ sung, còn **allowlist** theo hợp đồng quan sát giúp chặn cả những trường mới chưa được đặt tên trong blacklist.

## 3. Điểm chặn AOP và luồng dữ liệu an toàn

Trong AOP, một **join point** là vị trí thực thi mà hệ thống có thể quan sát/chặn theo mô hình của công cụ; một **pointcut** chọn tập join point; **advice** là hành vi bổ sung chạy trước, sau hoặc bao quanh điểm đó.

Một cách đặt trách nhiệm rõ ràng:

```text
Lời gọi nghiệp vụ
    |
    v
Around advice
    |-- Xác định phương thức / hợp đồng quan sát
    |-- Phân loại từng tham số (metadata, annotation, allowlist)
    |-- Redact / omit trước khi chuyển thành chuỗi
    |-- Giới hạn chiều dài, số phần tử và tổng bản ghi
    |-- Escape ký tự điều khiển, chuẩn bị event an toàn
    |-- Gửi event đến sink quan sát
    |
    +--> proceed / gọi phương thức gốc
    |       |
    |       +--> trả kết quả hoặc ném ngoại lệ
    |
    +-- Ghi trạng thái / thời lượng an toàn
    +-- Trả nguyên kết quả hoặc ném lại ngoại lệ gốc
```

Điểm quan trọng là **lọc trước khi chuyển đổi hoặc serialize**, không phải ghi nguyên dữ liệu rồi mới tìm cách xóa ở collector. Nếu hệ thống đã từng in bí mật ở console hay buffer trung gian, xóa ở bước sau không thể thu hồi mọi bản sao.

Một interceptor phù hợp nên tách ít nhất ba trách nhiệm:

1. **Classification policy:** trường/đối số nào được phép quan sát? Phạm vi và trạng thái dữ liệu có thay đổi theo môi trường hoặc use case không?
2. **Safe value renderer:** cách biểu diễn giá trị đã được phép, với escape và định mức tài nguyên.
3. **Event writer:** xuất bản ghi có kích thước cố định, tránh ghi trùng, quản lý lỗi của hệ thống telemetry.

Bộ phân loại nên có chính sách mặc định `HIDDEN` khi không biết kiểu, tên, annotation hoặc cấu trúc. Nếu áp dụng nhiều quy tắc, quy tắc **nhạy cảm/deny** phải thắng quy tắc cho phép.

## 4. Bốn cách xử lý và khi nào dùng

### 4.1. Omit: không xuất giá trị

Đối với token, payload tự do, tập tin hoặc đối tượng chưa phân loại, có thể chỉ xuất tên thao tác và trạng thái. Ví dụ:

```text
operation=payment.authorize outcome=success duration_ms=18
```

Nếu cần biết một tham số có được cung cấp hay không, dùng cờ boolean đã được duyệt như `hasAttachment=true`; đôi khi cả sự tồn tại, chiều dài hoặc tần suất của dữ liệu cũng nhạy cảm, vì vậy không tự động ghi metadata.

### 4.2. Redact: thay giá trị bằng ký hiệu an toàn

Ví dụ: `arg0=[REDACTED]`. Ký hiệu này xác nhận trường đã được che, nhưng không để lộ chuỗi gốc. Tránh giữ bốn số cuối, prefix/suffix của token, domain email hoặc biểu thức khớp nếu chưa có yêu cầu rõ và kiểm tra rủi ro liên kết dữ liệu.

Với đối tượng lồng nhau, redaction ở **trường gốc** thường an toàn hơn việc serialize toàn bộ rồi thay chuỗi con. JSON được dựng trước khi che có thể đã chứa bí mật trong bộ nhớ hoặc sự kiện trung gian.

### 4.3. Truncate: giới hạn giá trị được phép ghi

Truncate chỉ có ý nghĩa với **dữ liệu đã được phân loại là có thể quan sát**. Một token bị cắt còn 20 byte vẫn có thể lộ token; **truncation không thay thế redaction**.

Một giới hạn rõ ràng gồm: tối đa bao nhiêu byte, tính sau khi escape hay trước khi escape, ký hiệu cắt có được tính vào ngân sách không, và có cắt giữa ký tự Unicode hay không. Trong Java:

- `String.length()` đếm **UTF-16 code unit**, không đồng nghĩa số ký tự Unicode theo code point, càng không đồng nghĩa số byte UTF-8.
- Một emoji thường chiếm **hai UTF-16 code unit và bốn byte UTF-8**.
- Escape ký tự xuống dòng `\n` thành hai ký tự hiển thị `\\n` khiến bản ghi lớn hơn dữ liệu đầu vào.
- Phải giữ chỗ cho ký hiệu cắt như `…` (ba byte UTF-8), nếu ký hiệu này nằm trong hạn mức.

Nếu giới hạn áp cho payload sau mã hóa hoặc hệ thống lưu trữ, cần đo **byte của biểu diễn cuối cùng**. Nếu hệ thống dùng JSON, dấu nháy, backslash, tên trường và metadata cũng tiêu tốn byte.

### 4.4. Aggregate: ghi thông tin tổng hợp

Với `byte[]`, collection hoặc DTO lớn, thường chỉ cần loại dữ liệu, mã thao tác, số item theo một khoảng đã cho phép, hoặc trạng thái kiểm tra. Ví dụ `attachment_present=true` hoặc `batch_size_bucket=100-999`. Không nên gọi `collection.toString()` hay duyệt toàn bộ cấu trúc để lấy một đoạn đầu nếu có thể truy xuất thông tin tổng hợp an toàn.

Hash không tự động làm dữ liệu ẩn danh: giá trị có miền nhỏ, email hoặc mã khách hàng có thể bị đoán bằng tấn công từ điển; hash ổn định còn cho phép liên kết hoạt động giữa nhiều request. Chỉ dùng hash/HMAC định danh nếu có mục đích đã duyệt, quản lý khóa và chính sách thời hạn rõ ràng.

## 5. Thiết lập ngân sách quan sát

Giới hạn kích thước cần đo tại **nhiều cấp**, vì chỉ cắt từng chuỗi chưa đủ:

| Cấp | Biện pháp | Trường hợp cần tránh |
|---|---|---|
| Lựa chọn join point | Chỉ instrument những phương thức cần thiết | Ghi mọi getter, vòng lặp, helper nóng |
| Số tham số | Chỉ xử lý N vị trí đầu hoặc các vị trí được chọn | Phương thức có hàng chục trường |
| Kiểu dữ liệu | Chỉ nhận loại scalar đã duyệt | Serializer đi vào đối tượng lớn |
| Chuỗi | Giới hạn byte của bản hiển thị sau escape | Chuỗi nhiều byte Unicode hoặc ký tự điều khiển |
| Collection/array | Giới hạn số phần tử; mặc định không đi sâu | Iterator vô hạn, collection rất lớn |
| Độ sâu lồng nhau | Ngưỡng tối đa (thường chọn không duyệt) | Cấu trúc vòng tròn, đệ quy vô hạn |
| Toàn event | Giới hạn byte tính cả nhãn/dấu phân cách | Nhiều chuỗi nhỏ cộng lại quá lớn |
| Toàn hệ thống | Sampling, rate limit, quota, backpressure | Bão log/trace, tắc collector |

Các con số dưới đây **chỉ là ví dụ thiết kế**, không phải chuẩn chung: tối đa 4 đối số, 24 byte UTF-8 mỗi chuỗi đã escape, 192 byte cho một thông điệp. Chọn hạn mức thực tế theo nhu cầu truy vết, chi phí lưu trữ, tài liệu pháp lý và tải vận hành.

Điều kiện an toàn tài nguyên là giới hạn **trước khi** thực hiện thao tác tốn tài nguyên. Viết `serialize(object)` rồi mới `substring(0, 100)` có thể đã tốn nhiều CPU/RAM và quét dữ liệu nhạy cảm. Với chuỗi đầu vào rất lớn, formatter nên xử lý theo từng code point và dừng khi hết ngân sách, thay vì tạo một bản sao đã escape của toàn chuỗi.

## 6. Ví dụ Java 17 chạy được: interceptor với chính sách ẩn mặc định

Ví dụ dùng **JDK dynamic proxy** để biểu diễn around interceptor tại lời gọi đi qua interface `Operation`. Đây là một cơ chế AOP theo proxy cho phạm vi cụ thể:

- `@Sensitive` đánh dấu tham số phải che, ưu tiên cao nhất.
- `@Visible` cho phép hiển thị **chỉ khi đối số thực sự là String**. Giá trị được escape và giới hạn 24 byte UTF-8.
- Tham số khác được giữ ở trạng thái `[HIDDEN]`, không bị gọi `toString()`.
- Chỉ xét tối đa 4 đối số; kết quả cuối cùng bị giới hạn 192 byte. Mã đi qua code point nên không chia đôi cặp surrogate hợp lệ.
- Interceptor gọi phương thức gốc và ném lại nguyên nhân gốc nếu `Method.invoke` gói lỗi bằng `InvocationTargetException`.

Lưu chương trình dưới tên **SafeAspectDemo.java**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

public class SafeAspectDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface Sensitive {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface Visible {}

    public interface Operation {
        String execute(@Sensitive String password,
                       @Visible String action,
                       @Visible String message,
                       Object body);
    }

    static String limit(String text, int maxBytes, boolean escape) {
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            int next = offset + Character.charCount(cp);
            String piece = new String(Character.toChars(cp));
            if (escape) {
                if (cp == '\n') piece = "\\n";
                else if (cp == '\r') piece = "\\r";
                else if (cp == '\t') piece = "\\t";
                else if (cp == '\\') piece = "\\\\";
                else if (cp == '"') piece = "\\\"";
                else if (Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029) {
                    piece = String.format("\\u%04X", cp);
                }
            }
            int needed = piece.getBytes(StandardCharsets.UTF_8).length;
            int marker = next < text.length() ? 3 : 0;
            if (used + needed + marker > maxBytes) {
                out.append('…');
                return out.toString();
            }
            out.append(piece);
            used += needed;
            offset = next;
        }
        return out.toString();
    }

    static String audit(Method method, Object[] arguments) {
        Object[] args = arguments == null ? new Object[0] : arguments;
        Parameter[] params = method.getParameters();
        StringBuilder line = new StringBuilder("call ")
                .append(limit(method.getName(), 32, false)).append('(');
        int shown = Math.min(args.length, 4);
        for (int i = 0; i < shown; i++) {
            if (i > 0) line.append(", ");
            line.append("arg").append(i).append('=');
            if (params[i].isAnnotationPresent(Sensitive.class)) {
                line.append("[REDACTED]");
            } else if (params[i].isAnnotationPresent(Visible.class)
                    && args[i] instanceof String text) {
                line.append('"').append(limit(text, 24, true)).append('"');
            } else {
                line.append("[HIDDEN]");
            }
        }
        if (args.length > shown) line.append(", ... omitted=").append(args.length - shown);
        line.append(')');
        return limit(line.toString(), 192, false);
    }

    public static void main(String[] ignored) {
        Operation implementation = (password, action, message, body) -> "SUCCESS";
        Operation proxied = (Operation) Proxy.newProxyInstance(
                Operation.class.getClassLoader(),
                new Class<?>[] {Operation.class},
                (proxy, method, args) -> {
                    System.out.println(audit(method, args));
                    try {
                        return method.invoke(implementation, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });

        System.out.println(proxied.execute(
                "pass-123", "CREATE", "hello\nabcdefghijklmnopqrstuvw", new Object()));
    }
}
```

Biên dịch và chạy với Java 17 hoặc phiên bản tương thích mới hơn:

```bash
javac --release 17 -encoding UTF-8 SafeAspectDemo.java
java SafeAspectDemo
```

**Kết quả đã kiểm tra:**

```text
call execute(arg0=[REDACTED], arg1="CREATE", arg2="hello\nabcdefghijklmn…", arg3=[HIDDEN])
SUCCESS
```

Giải thích kết quả:

1. `arg0` mang `@Sensitive` nên không xuất `pass-123`.
2. `arg1` được phép quan sát và có kích thước nhỏ; giá trị `CREATE` xuất hiện.
3. `arg2` chứa xuống dòng: formatter đổi xuống dòng thật thành hai ký tự `\\n`, cắt trong ngân sách 24 byte **bao gồm** ký hiệu `…`.
4. `arg3` là một `Object` không được cho phép và không bị chuyển thành chuỗi.
5. Hàm nghiệp vụ vẫn trả `SUCCESS`; interceptor quan sát không thay đổi dữ liệu mà phương thức đích nhận.

**Phạm vi ví dụ:** `@Visible` là một cam kết do người viết mã đưa ra, không phải kiểm định tự động rằng dữ liệu không nhạy cảm. Việc vô tình gắn annotation cho tham số chứa bí mật vẫn có thể làm lộ nội dung. Cơ chế proxy chỉ chặn lời gọi đi **qua proxy** và phương thức thuộc interface; nó không tự chặn mọi lời gọi nội bộ. Ví dụ không trình bày collector production, đo latency hay lifecycle bất đồng bộ. Một hệ thống thực cần chính sách được quản lý tập trung và kiểm tra thường xuyên.

Mã `limit(..., escape=false)` ở cấp event giả định các đoạn giá trị được đưa vào đã qua safe renderer. Vì vậy **không** được lấy chuỗi tùy ý từ exception, object hoặc request ghép thẳng vào event rồi chỉ áp dụng hàm cắt tổng này. Trong chương trình demo, nguồn dữ liệu động của user phải đi qua `limit(..., escape=true)` trước khi đưa vào event. Bản triển khai production nên dựng event theo trường có kiểu rõ ràng, và chỉ serialize một lần bằng formatter an toàn của đích lưu trữ.

## 7. Khi tham số là DTO, Map, List, array hoặc object lồng nhau

Một lỗi phổ biến là đặt `@Sensitive` trên vài trường rồi vẫn gọi serializer để dựng toàn bộ object. DTO có thể chứa field mới, getter tính toán, tham chiếu vòng, lazily-loaded value hoặc tham chiếu đến credential. Ngay cả annotation cũng có thể khác nhau giữa field, getter, constructor parameter và interface; đừng giả định một annotation sẽ tự đi theo mọi cách truy cập.

Chính sách hợp lý:

- **Mặc định omit object nguyên khối.** Nếu cần chi tiết, khai báo danh sách đường dẫn trường được cho phép, ví dụ `command.kind` hoặc `request.region`, và đọc bằng accessor đã kiểm tra.
- **Không duyệt graph tùy ý.** Giới hạn độ sâu, số phần tử, thời gian, tổng byte và tham chiếu đã ghé thăm; tránh tuần hoàn.
- **Không tự gọi getter vì đang quan sát.** Getter có thể đọc I/O, tải dữ liệu, ném lỗi, mutate state hoặc gây thêm join point được instrument.
- **Không chuyển binary thành Base64 để log.** Mã hóa Base64 vừa tăng kích thước vừa giữ nguyên dữ liệu.
- **Không để serializer đa hình quyết định lớp cần khởi tạo/đọc.** Chỉ xử lý những kiểu đã cho phép, tránh việc đi sâu vào đối tượng ngoại lai.
- **Xử lý dữ liệu null và thiếu:** quyết định có được log trạng thái `missing`/`null` hay không. Trong một số use case, bản thân sự tồn tại của trường cũng tiết lộ dữ liệu.

Ví dụ một payload có `userId`, `status`, `accessToken` và `metadata`: chỉ nên xuất `operation`, một trạng thái nằm trong tập giá trị cố định đã duyệt và có thể là thời gian xử lý. Không nên sao chép cả object rồi che mỗi trường `accessToken` vì các trường còn lại có thể cũng nhận bí mật trong tương lai.

Nếu thật sự cần recursive renderer, cần có ngân sách tường minh và được chứng minh trên dữ liệu độc hại: `maxDepth`, `maxNodes`, `maxCollectionItems`, `maxTotalBytes`, phát hiện vòng tham chiếu, timeout/budget CPU và fail-closed khi gặp kiểu chưa được hỗ trợ. Việc duyệt đến hạn rồi mới cắt string cuối là quá muộn.

## 8. Chính sách theo phương thức, tham số và kết quả trả về

Tầng AOP nên mô tả rõ nơi nào cho phép xuất dữ liệu. Các khả năng thường gặp:

**Allowlist theo phương thức và vị trí.** Dùng bộ metadata liên kết join point với các vị trí đối số đã được duyệt; an toàn hơn lựa chọn toàn cục dựa trên tên biến. Trong Java, `Method.getParameters()` có thể thấy các annotation runtime của tham số. Tuy nhiên `Parameter.getName()` không bảo đảm chứa tên nguồn nếu không biên dịch với `-parameters`; dùng chỉ số hoặc metadata ổn định nếu cần chính xác.

**Annotation.** Có thể gắn đánh dấu nhạy cảm/có thể quan sát vào tham số hoặc property. Annotation chỉ hiệu lực ở nơi công cụ đọc nó: annotation trên interface không nhất thiết tồn tại trên implementation và ngược lại. Nếu thiếu metadata hoặc có xung đột chính sách, chọn `HIDDEN`.

**Kiểu và schema.** Một enum trạng thái đã duyệt dễ quan sát hơn raw String; một ID có cardinality rất cao có thể làm metric tăng chi phí hoặc cho phép liên kết người dùng. Không phải loại dữ liệu có thể log được cũng phù hợp để đặt thành nhãn metric.

**Giá trị trả về.** Cần áp dụng chính sách **riêng** cho return value. Tránh advice sau phương thức kiểu `logger.info("result={}", result)`: giá trị có thể là token, DTO hoặc payload lớn, và `toString()` có thể kích hoạt hành vi ngoài ý muốn. Thường chỉ xuất `outcome=success`, số lượng đã xét duyệt hoặc loại lỗi.

**Ngoại lệ.** `Throwable.getMessage()` có thể chứa query SQL, URL, thông tin người dùng hoặc tham số. Stack trace có thể chứa tên file, đường dẫn, input và dữ liệu ngữ cảnh trong suppressed exceptions. Giới hạn ghi theo loại lỗi đã phân loại, mã lỗi chuẩn hóa và chính sách stack trace riêng; không in nguyên toàn bộ đối tượng exception theo mặc định.

## 9. Escape dữ liệu để chống giả mạo log

Bỏ bí mật thôi chưa đủ. Một giá trị được phép xuất vẫn có thể chứa ký tự làm hỏng cấu trúc sự kiện:

```text
hello
2026-10-20 ERROR login failed
```

Nếu in nguyên giá trị hai dòng, người đọc hoặc parser có thể hiểu dòng thứ hai như một sự kiện hệ thống khác (**log injection**). Hãy escape các ký tự điều khiển, dấu phân tách và ký tự đặc biệt của định dạng đích. Với JSON, ưu tiên serializer JSON được cấu hình an toàn cho những trường **đã được phân loại**; không tự nối dấu nháy và dấu phẩy với dữ liệu gốc.

Một pipeline đáng tin:

```text
original argument
    -> classify (deny/allow)
    -> extract only allowed scalar
    -> normalize/escape for target format
    -> enforce field byte budget
    -> build structured event with bounded fields
    -> enforce entire event budget
    -> publish through controlled sink
```

Nếu giới hạn áp cho JSON hay một giao thức cụ thể, tính byte **sau khi serialization cuối cùng** hoặc dùng writer có quota trong lúc encode. Không giả định ký tự hiển thị bằng một byte. Tránh cắt mù vào giữa sequence escape hoặc giữa hai byte trong UTF-8; có thể cắt ở ranh giới code point/field rồi thêm một marker hợp lệ.

## 10. Advice phải giữ nguyên hành vi nghiệp vụ

Logging là hoạt động phụ trợ. Interceptor quan sát cần hạn chế thay đổi tính đúng đắn:

- **Không thay đổi args gốc để redact.** Tạo biểu diễn an toàn *riêng cho telemetry*; nếu thay chuỗi mật khẩu gốc thành `[REDACTED]` rồi gọi `proceed`, hành vi nghiệp vụ đã bị phá.
- **Không gọi phương thức đích hai lần.** Trong around advice, kiểm soát chính xác một lần gọi trong đường thành công thông thường; đừng retry chỉ vì log thất bại.
- **Không để lỗi ghi log che ngoại lệ gốc.** Cần chính sách cách ly lỗi exporter/sanitizer với ứng dụng. Nếu chính sách yêu cầu telemetry có tính bắt buộc, thay đổi luồng nghiệp vụ phải được thiết kế và công bố rõ.
- **Không gọi `toString()` trên tham số lạ.** `toString()` là mã người dùng, có thể tốn kém, có tác dụng phụ hoặc vô tình in field nhạy cảm.
- **Không lưu reference đến args trong queue telemetry.** Một asynchronous appender nhận trực tiếp object sẽ serialize sau thời điểm advice, có thể nhìn thấy giá trị đã thay đổi hoặc giữ một object graph lớn trong bộ nhớ. Đẩy **snapshot đã lọc và giới hạn** vào queue.
- **Không giả định mọi lời gọi đều đồng bộ.** Với future, callback hoặc stream, kết quả được quan sát có thể chỉ là handle; advice đo "thành công" khi trả handle không nhất thiết nghĩa là công việc đã hoàn tất.

Nếu bản thân sink bị quá tải, áp dụng chính sách giảm tải có dự báo (sampling, drop counters, bounded queue). Đừng giữ một request đang chờ vô thời hạn chỉ vì đẩy log vào hàng đợi đầy.

## 11. Thất bại thường gặp và cách sửa

| Triệu chứng | Nguyên nhân | Cải thiện |
|---|---|---|
| Log chứa thông tin xác thực | Chặn toàn bộ tham số bằng formatter tổng quát | Fail-closed; chỉ render allowlist, redact bí mật |
| Giữ lại prefix token sau khi cắt | Dùng truncation như biện pháp bảo mật | Omit hoặc redact hoàn toàn |
| Bộ nhớ tăng khi có payload lớn | Serialize hết rồi mới `substring` | Dừng ngay khi hết budget trong renderer |
| Dòng log bị tách, giả mạo level | Không escape CR, LF và dấu phân cách | Escape có xét format đích trước khi ghi |
| Cắt hỏng emoji/ký tự Unicode | Dùng chỉ số UTF-16/byte thô tùy ý | Cắt theo code point hoặc encoder có giới hạn |
| Log lộ nội dung lỗi | In nguyên `exception.getMessage()`/stack trace | Phân loại mã lỗi, rà soát thông điệp và stack |
| Sau khi thêm field mới xuất hiện dữ liệu riêng tư | Blacklist theo tên trường | Deny-by-default và review khi thay schema |
| Mất dữ liệu nghiệp vụ | Redact trực tiếp đối số đưa vào `proceed` | Chỉ tạo bản telemetry đã làm sạch |
| Lỗi log làm request thất bại | Recorder ném ngoại lệ trong advice | Cách ly lỗi quan sát theo chính sách |
| Tốn CPU trên đường gọi nóng | Instrument quá nhiều điểm + reflection/regex mỗi lần | Cache chính sách phương thức, giảm cardinality/sampling |
| Log chứa toàn bộ object khi sink xử lý muộn | Đưa reference sống vào queue | Chụp giá trị an toàn, có kích thước hữu hạn trước enqueue |

## 12. Kiểm thử: xem đầu ra quan sát là bề mặt có thể bị tấn công

Ngoài kiểm thử chức năng, cần kiểm tra dữ liệu được gửi đến **đúng sink mà ứng dụng dùng**, bao gồm log thành công, lỗi, log của retry, trace attribute và event bất đồng bộ. Có thể dùng sink giả để thu thập sự kiện và assert nội dung:

1. **Canary bí mật:** truyền mật khẩu/token giả dễ nhận diện; assert **không** xuất hiện trong bất kỳ event, log lỗi, exception render hoặc hàng đợi telemetry nào.
2. **Trường mới:** thêm một thuộc tính nhạy cảm vào DTO; assert renderer mặc định vẫn giữ `[HIDDEN]`.
3. **Chuỗi cực lớn:** cung cấp chuỗi dài hàng triệu ký tự vào tham số đã allowlist; đo rằng output không vượt byte quota và không tạo bản sao khổng lồ.
4. **Unicode:** kiểm thử ASCII, tiếng Việt, emoji surrogate pair, ký tự kết hợp và chuỗi chỉ chứa dấu điều khiển; kiểm tra tính hợp lệ của output UTF-8.
5. **Log injection:** chứa `\r`, `\n`, `\t`, quote, backslash và ký tự xuống dòng Unicode; sự kiện vẫn có cấu trúc hợp lệ.
6. **Object độc hại:** tạo object có `toString()` ném lỗi hoặc tăng bộ đếm; assert interceptor không gọi nó khi kiểu chưa được duyệt.
7. **Collection vòng và rất lớn:** renderer không rơi vào đệ quy vô hạn hoặc duyệt không giới hạn.
8. **Lỗi nghiệp vụ:** phương thức ném exception; advice không che nguyên nhân, không thêm giá trị nhạy cảm vào failure event.
9. **Lỗi quan sát:** sink ném lỗi hoặc queue đầy; luồng nghiệp vụ tuân theo chính sách đã công bố.
10. **Ngân sách tổng:** nhiều tham số vừa dưới quota riêng vẫn không làm event vượt giới hạn chung.

Kiểm tra **đầu ra byte thực tế**, không chỉ độ dài `String.length()`. Nếu sink dùng JSON hoặc escape bổ sung, test event sau serialization. Với log có cấu trúc, bảo đảm tên trường và metadata cố định không phá quota; với metrics, bảo đảm số lượng giá trị nhãn không tăng vô hạn.

## 13. Vận hành và quản trị chính sách

Một chính sách AOP tốt không kết thúc ở formatter. Cần giữ cùng quy tắc qua môi trường và version triển khai:

- **Điểm instrument:** ghi rõ tập pointcut và kiểu join point được bảo vệ. Proxy-based interception có thể bỏ qua lời gọi nội bộ không đi qua proxy; weaving có phạm vi khác, nên phải audit phạm vi thật.
- **Quyền bật debug:** chế độ debug không được tự động vô hiệu hóa redaction. Cấp quyền tạm thời để xem một số trường vẫn cần kiểm soát chặt và dấu vết kiểm toán.
- **Bản đồ dữ liệu:** mỗi trường xuất log có owner, mục đích, mức độ nhạy cảm và thời gian giữ. Thay schema hoặc annotation phải qua kiểm tra tương ứng.
- **Bảo vệ hạ tầng lưu trữ:** phân quyền, mã hóa, thời gian lưu, xóa dữ liệu, quota và kiểm soát việc tải log xuống là các lớp bắt buộc dù formatter đã an toàn.
- **Đánh giá định kỳ:** dùng dữ liệu thử/canary và kiểm tra sample thực tế đã được phép; tìm đường ghi trực tiếp nằm ngoài interceptor (logger thủ công, library, framework, profiler, dump).
- **Quan sát chính cơ chế bảo vệ:** đếm số giá trị bị ẩn/cắt, số event bị bỏ hoặc lỗi exporter bằng metric tổng hợp, tránh đưa dữ liệu gốc vào metric.

Một số trường hợp cần điều tra sâu. Hãy thiết kế kênh thu thập chuyên biệt có cấp quyền, phạm vi và thời hạn rõ ràng; đừng mở rộng log AOP phổ thông thành kho sao chép mọi input.

## 14. Checklist trước khi dùng advice trong sản phẩm

- [ ] Có allowlist theo use case; khi thiếu metadata thì ẩn.
- [ ] Bí mật và dữ liệu riêng tư được omit/redact **trước** khi serialize.
- [ ] Renderer không gọi `toString()` hoặc duyệt đối tượng lạ.
- [ ] Có ngân sách theo tham số, tổng event, phần tử, độ sâu và tốc độ phát.
- [ ] Giới hạn theo byte của định dạng đích; không cắt hỏng Unicode/escape.
- [ ] Không để ký tự điều khiển tạo log giả hoặc phá JSON.
- [ ] Kết quả trả về, ngoại lệ và các đường bất đồng bộ dùng chính sách riêng.
- [ ] Advice giữ nguyên args, return và exception của nghiệp vụ.
- [ ] Test có canary bí mật, payload lớn, chuỗi độc hại, scope interception và lỗi sink.
- [ ] Sink và log store có quyền truy cập, lưu giữ và quota phù hợp.

**Điểm cốt lõi:** AOP giúp đưa quan sát vào một vị trí tập trung, nên lỗi ở vị trí đó cũng ảnh hưởng đồng loạt nhiều chức năng. Hãy coi dữ liệu của join point là **không đáng tin và có thể nhạy cảm**; chỉ phát các trường được duyệt, che bí mật trước khi chuyển đổi, đặt giới hạn byte/tài nguyên cho từng bước, và không làm thay đổi hành vi nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-observability-006">Lấy mẫu, giới hạn tần suất và giảm khối lượng đầu ra</a>

<details>
<summary>Xem chi tiết</summary>

# Lấy mẫu, giới hạn tần suất và giảm khối lượng đầu ra khi quan sát chương trình bằng Aspect (AOP)

## 1. Vì sao cần kiểm soát dữ liệu quan sát?

Một aspect quan sát có thể can thiệp nhiều lượt gọi và tạo log, trace hoặc metric tại các join point đã chọn. Tính tập trung của AOP hữu ích cho mã nguồn, nhưng khiến lượng dữ liệu phát ra tăng nhanh nếu mỗi lần gọi đều ghi chi tiết.

Nhưng “quan sát tất cả, xuất tất cả” thường gây phản tác dụng. Nếu một phương thức được gọi 100.000 lần mỗi giây và mỗi lời gọi phát log 1 KiB, một bản sao ứng dụng có thể tạo xấp xỉ 100 MiB log mỗi giây, **chưa tính** metadata, serialization, đường truyền và bộ thu thập. Khi giám sát tạo thêm áp lực CPU, bộ nhớ, đĩa hoặc mạng, chính cơ chế quan sát có thể làm hệ thống chậm hay mất ổn định.

Ba cơ chế giải quyết các vấn đề khác nhau:

| Cơ chế | Câu hỏi chính | Quyết định tại thời điểm nào? | Ví dụ |
| --- | --- | --- | --- |
| **Lấy mẫu (sampling)** | Sự kiện/truy vết nào được chọn để quan sát chi tiết? | Theo xác suất, khóa ổn định, điều kiện hoặc kết quả thao tác | Giữ khoảng 1% trace thành công |
| **Giới hạn tần suất (rate limiting)** | Được phép phát tối đa bao nhiêu sự kiện trong một khoảng thời gian? | Theo ngân sách/token và thời gian | Không quá 100 log chi tiết/giây cho một nhóm lỗi |
| **Giảm khối lượng đầu ra (output reduction)** | Nếu đã chọn phát, mỗi bản ghi chứa những gì và lớn đến đâu? | Khi xây dựng/chuẩn hóa dữ liệu đầu ra | Chỉ giữ mã lỗi, nhãn thao tác, thời lượng; rút gọn stack trace lặp |

**Sampling không bảo đảm giới hạn tuyệt đối** vì tải đầu vào có thể tăng đột biến. **Rate limiting không bảo đảm mẫu đại diện** vì những sự kiện đến sau có thể bị bỏ có hệ thống. **Truncation không giảm số sự kiện** nếu vẫn phát một dòng log cho mỗi lần gọi. Một Aspect quan sát tốt thường phối hợp cả ba, đồng thời bảo đảm các số đo quan trọng vẫn phản ánh đầy đủ lưu lượng.

Bài này tập trung vào **AOP tổng quát**, từ cơ chế cơ bản đến thiết kế nâng cao. Ví dụ Java 17+ dùng interface proxy tiêu chuẩn như một cách cụ thể để minh họa around advice; các nguyên tắc không phụ thuộc một framework AOP cụ thể.

## 2. Xác định đúng nơi Aspect quan sát

Giả sử có thao tác nghiệp vụ `lookup(key)`. Một advice quanh lời gọi có thể:

1. **Trước khi chạy:** ghi nhận thời điểm bắt đầu bằng `System.nanoTime()`, lấy trace/request context và nhãn nghiệp vụ cho phép.
2. **Gọi phương thức gốc đúng một lần:** giữ nguyên tham số, kiểu trả về và hành vi lỗi; hạn chế sinh telemetry trước khi biết thao tác có thực sự chạy.
3. **Sau khi trả về hoặc ném lỗi:** cập nhật số đếm/độ trễ; quyết định chọn log/trace chi tiết dựa trên sampling, quota và trạng thái.
4. **Cuối cùng:** dọn context, hoàn trả tài nguyên telemetry và không thay đổi kết quả nghiệp vụ.

Trong ví dụ đồng bộ, around advice bao trùm toàn bộ công việc. Nhưng nếu phương thức trả `Future`, `CompletionStage` hoặc một luồng sự kiện, thời điểm advice kết thúc có thể chỉ là **thời điểm trả handle**. Muốn ghi thời gian hoàn tất thật hoặc lỗi bất đồng bộ, phải gắn callback hay chặn đúng ranh giới hoàn tất; không mặc định `finally` quanh lời gọi phản ánh cả vòng đời tác vụ.

Một sơ đồ điều phối hữu ích:

~~~text
                  Invocation / completion
                            |
                   Ghi counter tối thiểu
                            |
                Lấy context và nhãn an toàn
                            |
               Chọn mẫu theo policy / trace
                            |
                    Qua rate limiter?
                       /         \
                     Có          Không
                     |             |
              Giới hạn trường      Ghi nhận số bị bỏ
              và dung lượng        (dạng metric/báo cáo gộp)
                     |
               Phát tới sink/queue
                     |
           Quan sát độ trễ và lỗi của sink
~~~

Thực tế có thể tính quyết định **head sampling** ngay khi bắt đầu để tránh tạo span/tải dữ liệu dư thừa. Với **tail sampling**, phải quan sát hoặc giữ bộ đệm hữu hạn đến khi biết kết quả, rồi mới chọn phát. Sơ đồ chỉ mô tả thứ tự quyết định chung, không yêu cầu mọi phương thức phải tạo sẵn toàn bộ log trước khi lọc.

### 2.1. Phân biệt telemetry không thể đánh đồng

- **Metric tổng hợp:** số yêu cầu, số lỗi, histogram thời lượng, độ dài queue; thông thường cập nhật cho **mọi sự kiện** với nhãn có cardinality kiểm soát.
- **Log chi tiết:** mô tả một biến cố cụ thể; có thể lấy mẫu, giới hạn tốc độ và cắt nội dung, ngoại trừ những nghĩa vụ lưu trữ/kiểm toán bắt buộc.
- **Trace/span:** ghi quan hệ nhân quả và thời gian theo một trace; lựa chọn toàn trace hay từng sự kiện phải bảo toàn context, tránh các đoạn không nối được.
- **Audit/security event:** có thể yêu cầu tính đầy đủ, thứ tự hoặc độ bền khác log chẩn đoán. Không đưa các sự kiện bắt buộc này vào chính sách sampling/drop thông thường mà chưa có hợp đồng cho phép.

Nếu bỏ 99% request log, `requests_total` vẫn phải tăng cho mọi request nếu mục đích metric là đếm tải thực. Nếu chỉ đếm các bản ghi đã phát, số liệu sẽ bị lệch và không còn là tổng số lời gọi.

## 3. Sampling: giảm số lượng nhưng giữ thông tin có ích

### 3.1. Head sampling — quyết định sớm

**Head sampling** quyết định có quan sát chi tiết ở hoặc gần thời điểm bắt đầu thao tác. Một số lựa chọn:

- **Xác suất Bernoulli:** mỗi yêu cầu được chọn độc lập với xác suất `p`, ví dụ `p = 0,01` cho khoảng 1%. Số bản ghi thực tế biến động theo thời gian; 1% không đồng nghĩa đúng một bản ghi trong mỗi 100 yêu cầu.
- **Bộ đếm mỗi N yêu cầu:** chọn một trong mỗi N lượt gọi. Dễ giải thích/kiểm thử nhưng nhạy với thứ tự và những chu kỳ trong lưu lượng, thường không thích hợp để bảo đảm mẫu thống kê đại diện.
- **Hash theo trace ID hoặc operation ID ổn định:** quyết định lặp lại nhất quán cho cùng một trace. Phù hợp khi nhiều thành phần cần dùng chung quyết định; thuật toán/hash, tỷ lệ và quy tắc kế thừa phải thống nhất.
- **Parent-based:** nếu nhận một trace context hợp lệ đã có quyết định sampled, các span con kế thừa chính sách theo quy ước; cần thiết kế rõ trường hợp không có parent hoặc parent chưa sampled.

Với hash ổn định, một ý tưởng là:

~~~text
sample(traceId, p):
    nếu traceId rỗng hoặc không tin cậy: dùng chính sách mặc định
    h = stable_uniform_hash(traceId)
    return h < threshold(p)
~~~

Đây là **giả mã**, không phải API Java. Hash cần phân phối tương đối đều trên miền ID hợp lệ và quy tắc chung ở mọi nơi. Không dùng trực tiếp hash của dữ liệu nhạy cảm như mật khẩu hoặc thông tin nhận dạng; log không nên giữ raw ID nếu không cần.

Sampling theo `traceId` giúp một trace không bị quyết định độc lập tại từng span, tránh trace bị đứt đoạn. Tuy vậy, nó vẫn cần xử lý khi một bên thay đổi xác suất, thiếu context hoặc khi trace đi qua thành phần không hỗ trợ policy.

### 3.2. Tail sampling — chọn theo kết quả

**Tail sampling** cân nhắc sau khi biết kết quả: giữ trace lỗi, thao tác quá chậm, hoặc một số dấu hiệu bất thường; còn trace thành công ngắn thì lấy mẫu thưa. Ưu điểm là giữ được ca hiếm nhưng quan trọng. Đổi lại, nó phải:

- Giữ hoặc chuyển đủ thông tin tạm thời cho đến khi biết trace kết thúc.
- Có giới hạn số trace chờ, kích thước buffer và thời gian giữ; trace dài/mồ côi cần chính sách hết hạn.
- Có cách gom các span cùng trace, đôi khi phải chuyển chúng về cùng điểm ra quyết định.
- Xử lý thiếu span, span đến muộn, worker chết, áp lực bộ nhớ và quota đầu ra.

Một điều cần tránh: lời quảng cáo “giữ toàn bộ lỗi” chỉ đúng nếu hệ thống có đủ dung lượng từ đường thu thập đến sink, và nếu lỗi được nhận diện trước khi sự kiện bị loại. Nếu quota đầu ra đã cạn, chính sách ưu tiên lỗi có thể phải bảo đảm một kênh dự phòng/đường ưu tiên khác, hoặc chấp nhận mất chi tiết và báo rõ tỷ lệ bị bỏ.

### 3.3. Sampling thích nghi và thiên lệch

Có thể điều chỉnh xác suất theo tải, nhóm thao tác hoặc loại sự kiện. Ví dụ, giữ tỷ lệ cao cho giao dịch hiếm và thấp cho health-check. Nhưng phải hiểu hệ quả:

- Mẫu ưu tiên lỗi **không đại diện cho tỷ lệ lỗi thực**. Đừng lấy số log lỗi chia cho tổng log để suy ra error rate.
- Nếu muốn ước lượng tổng từ mẫu Bernoulli độc lập với xác suất biết trước `p`, có thể cân nhắc trọng số `1/p`; chỉ áp dụng khi giả định lấy mẫu phù hợp, không tự suy diễn cho dữ liệu đã bị rate limit theo thứ tự hoặc tail sampling có điều kiện.
- Điều chỉnh tỷ lệ giữa các tenant hoặc operation có thể che khuất nhóm ít lưu lượng nếu một ngân sách chung bị nhóm tải cao chiếm hết.
- Nếu thu mẫu liên tục từ một tập request đặc biệt (ví dụ mọi request lỗi), cần gắn metadata về lý do chọn mẫu để người phân tích không nhầm đó là traffic ngẫu nhiên.

Thay vì xem sampling là công tắc “có/không”, một Aspect nên coi nó là **chính sách chọn dữ liệu có mục đích**, với xác suất/điều kiện được cấu hình, giám sát và kiểm thử.

## 4. Rate limiting: đặt ngân sách phát dữ liệu

Sau khi một sự kiện được chọn, lượng sự kiện thực tế vẫn có thể quá lớn khi tải đột biến. **Rate limiter** kiểm soát tốc độ cấp quyền phát.

### 4.1. Các thuật toán phổ biến

| Cơ chế | Nguyên tắc | Điểm cần chú ý |
| --- | --- | --- |
| **Token bucket** | Một bucket có sức chứa `B`, bổ sung token với tốc độ `r`; mỗi sự kiện tiêu một hoặc nhiều token | Cho phép burst ngắn đến `B`, sau đó giới hạn theo tốc độ bổ sung |
| **Leaky bucket / queue drain** | Đưa vào hàng đợi hữu hạn và rút ra với tốc độ cố định | Làm mượt đầu ra nhưng tăng độ trễ; cần chiến lược khi queue đầy |
| **Fixed window counter** | Đếm số phát trong cửa sổ thời gian cố định | Đơn giản nhưng có thể cho burst dồn ở ranh giới hai cửa sổ |
| **Sliding window** | Tính trên khoảng thời gian trượt, có thể dùng log/tập bucket nhỏ | Mượt hơn nhưng cần thêm trạng thái và đồng bộ |
| **Concurrency cap** | Giới hạn số sự kiện/worker đồng thời đang xử lý | Kiểm soát số việc đang thực thi, **không phải** tốc độ sự kiện/giây |

Với token bucket, để **tốc độ trung bình** khoảng `r` sự kiện/giây và burst tối đa `B`, ta đặt sức chứa `B` và bổ sung token theo thời gian. Nếu bản ghi có kích thước rất khác nhau, limiter theo **byte** hoặc weighted token thường phù hợp hơn limiter mỗi sự kiện một token. Không thể suy ra bandwidth chỉ từ số dòng log.

### 4.2. Phạm vi ngân sách

Một limiter có thể áp dụng cho:

- **Mỗi phương thức/join point:** ngăn một thao tác chatty chiếm toàn bộ quota.
- **Mỗi loại lỗi:** gộp lỗi lặp, bảo vệ những nhóm lỗi quan trọng khác.
- **Mỗi service/instance:** dễ triển khai, nhưng tổng nhiều instance có thể vượt ngân sách hệ thống.
- **Mỗi tenant:** tránh một tenant chiếm tài nguyên; lưu ý số lượng tenant lớn cũng làm phình trạng thái quota.
- **Toàn hệ thống:** cần bộ điều phối phân tán hoặc chia ngân sách; phải xét độ trễ, sự cố mạng, clock và phân vùng.

Nếu mỗi trong 10 instance có quota 100 sự kiện/giây, tổng có thể đến khoảng 1.000 sự kiện/giây. Đó là **giới hạn theo từng instance**, không phải quota toàn hệ thống. Kể cả ở một instance, quota theo bộ đếm thread-local có thể vô tình nhân lên theo số thread.

### 4.3. Chặn, xếp hàng hay bỏ?

Trong đường chạy nghiệp vụ, logging/telemetry thường cần **không chặn** hoặc có chặn trong một ngân sách rất nhỏ. Khi hết quota:

- Bỏ sự kiện chi tiết và tăng counter `telemetry_suppressed_total`.
- Gộp thành báo cáo kiểu “đã bỏ N sự kiện tương tự trong kỳ”.
- Nếu cần giữ thông tin, dùng bounded queue với backpressure hoặc chính sách ưu tiên rõ ràng; không biến queue thành bộ nhớ vô hạn.
- Với sự kiện buộc phải lưu bền, dùng đường ghi có hợp đồng độ bền/khả năng chịu tải riêng và xử lý thất bại theo yêu cầu nghiệp vụ.

Đừng ngầm để một lời gọi nghiệp vụ thông thường đứng chờ hàng giây chỉ vì sink log chậm. Cũng đừng âm thầm coi “ghi log thất bại” là “nghiệp vụ thất bại” khi hợp đồng không cho phép.

### 4.4. Thời gian và đồng thời

Limiter đo khoảng thời gian trong một tiến trình nên dùng **đồng hồ đơn điệu** như `System.nanoTime()`; `System.currentTimeMillis()` là wall clock và có thể nhảy khi chỉnh giờ. Với nhiều thread, quyền tiêu token phải được đồng bộ/atomic để tránh cấp quá mức. Với limiter phân tán, cần một hợp đồng thời gian và điều phối toàn cục phù hợp; dùng `nanoTime()` của hai máy để so trực tiếp là sai.

Khi bổ sung token, xử lý tràn số, thời gian dài bị treo/sleep, clock không tiến và burst sau khi không hoạt động. Một quota “100 sự kiện/giây” cũng phải định nghĩa có cho phép burst bao nhiêu và cách tính trọng lượng bản ghi.

## 5. Giảm khối lượng đầu ra: mỗi sự kiện có ý nghĩa hơn

Ngay cả sau sampling và rate limiting, một bản ghi có thể rất lớn. **Output reduction** thiết kế lại payload để giữ giá trị chẩn đoán với ít byte hơn.

### 5.1. Chỉ ghi những trường cần thiết

Thay vì:

~~~text
DEBUG request=... fullHttpHeaders=... fullBody=... fullObjectGraph=... allStackFrames=...
~~~

Một sự kiện cấu trúc tối giản có thể là:

~~~json
{"event":"lookup.completed","operation":"lookup","outcome":"error","error_type":"TimeoutException","latency_bucket":"100-500ms","trace_id":"<opaque-id>"}
~~~

Ví dụ JSON chỉ minh họa schema; giá trị `trace_id` trong hệ thống thật phải lấy từ context được cho phép, và có chính sách bảo vệ riêng. Trường như access token, session secret, mật khẩu, dữ liệu cá nhân, nội dung request nhạy cảm và đối tượng chứa bí mật **không được đưa vào log** chỉ để rồi cắt ngắn sau đó. Cần **allowlist trường và redaction trước khi phát**, áp dụng tới cả exception message, argument `toString()` và URL/query string.

Thông tin tối thiểu thường gồm mã thao tác, kết quả, loại lỗi đã chuẩn hóa, nhãn môi trường có cardinality thấp và một correlation ID hợp lệ. Chỉ thêm dữ liệu bổ sung khi có trường hợp sử dụng rõ ràng và quyền truy cập phù hợp.

### 5.2. Rút gọn thông tin có kiểm soát

Một số kỹ thuật:

- **Truncation:** giới hạn số code point/byte cho chuỗi người dùng; để ý UTF-8 và surrogate pair, tránh cắt hỏng ký tự Unicode. Giữ thêm cờ `truncated=true` để không nhầm dữ liệu với bản gốc.
- **Stack trace grouping:** gom cùng exception type và nhóm frame liên quan, có số đếm các lần lặp; vẫn giữ nơi thu đủ thông tin cho việc điều tra khi thực sự cần.
- **Aggregation:** đếm theo operation, error code và cửa sổ thời gian; phát một summary thay vì hàng nghìn thông điệp y hệt.
- **Deduplication:** dùng fingerprint đã chuẩn hóa, TTL và số lần xuất hiện; tránh dùng toàn bộ message biến động để tạo vô số nhóm giả.
- **Cardinality reduction:** không gắn user ID, full URL path với ID động hoặc request ID vào nhãn metric. Dùng route template hoặc error code hữu hạn.
- **Batch/compact encoding/compression:** tối ưu truyền và lưu trữ ở collector/sink khi phù hợp; cần kiểm soát queue, latency và khả năng mất dữ liệu.
- **Lazy formatting:** chỉ tạo chuỗi log/serialize object sau khi sampling và quota đã cho phép. Có thể thiết kế `Supplier<String>` hoặc callback formatter để tránh tính toán tốn kém cho bản ghi sẽ bị bỏ.

Không được rút gọn đến mức mất sự phân biệt cần thiết giữa lỗi timeout, lỗi validation và lỗi hệ thống. Nên đánh giá mức độ hữu dụng dựa trên câu hỏi điều tra thực tế, chứ không chỉ phần trăm byte tiết kiệm.

### 5.3. Cẩn thận với output của chương trình và sink

“Giảm khối lượng đầu ra” trong quan sát bằng Aspect có thể bao gồm **log, span attribute, event payload và metric labels**. Nó không đồng nghĩa tự ý cắt **giá trị trả về nghiệp vụ** của phương thức. Around advice phải tôn trọng hợp đồng của API: chỉ thay đổi kết quả nghiệp vụ nếu đó là yêu cầu riêng được định nghĩa rõ.

Nếu telemetry được đưa vào hàng đợi nền, Aspect cần giới hạn kích thước queue và số byte đang chờ. Hệ thống không thể coi dữ liệu đã “xuất thành công” chỉ vì đã thêm nó vào queue; phải phân biệt accepted, exported, retried và dropped.

## 6. Kết hợp chính sách trong một Aspect

Một luồng hợp lý cho phương thức đồng bộ:

1. Thu **metadata tối thiểu**: operation, timestamp đơn điệu, trace context hợp lệ. Không serialize toàn bộ tham số.
2. Thực thi nghiệp vụ theo đúng semantics, đo kết quả và lỗi; tăng các metric cốt lõi cho **mọi invocation**.
3. Xác định event có thuộc nhóm bắt buộc, ưu tiên cao, hay nhóm được phép lấy mẫu.
4. Quyết định sampling theo context và policy. Nếu không chọn, chỉ cập nhật counter “not sampled” phù hợp.
5. Kiểm tra rate limiter ở đúng scope; nếu hết quota, ghi counter “suppressed by quota” mà không tạo payload chi tiết.
6. Với sự kiện được phép phát, chọn các trường an toàn, chuẩn hóa, redact, cắt gọn đúng giới hạn byte; sau đó đưa vào sink một cách có kiểm soát.
7. Ghi nhận lỗi telemetry ở kênh dự phòng phù hợp, không làm thay đổi thành công/thất bại của nghiệp vụ trừ khi hợp đồng yêu cầu.

Thứ tự giữa **lấy mẫu và ghi counter tổng** rất quan trọng: nếu đặt counter trong nhánh “được chọn”, metric sẽ báo số request theo mẫu chứ không còn là tổng thật. Đặt serialization trước sampling/quota cũng triệt tiêu một phần lợi ích tiết kiệm CPU.

### 6.1. Các phép đếm nên tách riêng

Gợi ý dùng các counter với nhãn cardinality thấp:

| Counter | Tăng khi nào? | Mục đích |
| --- | --- | --- |
| `invocations_total` | Mọi lời gọi được chặn | Số lượt chạy thật |
| `invocation_failures_total` | Nghiệp vụ ném lỗi | Tỷ lệ lỗi nghiệp vụ |
| `sample_candidates_total` | Được chính sách sampling chọn | Hiểu chính sách chọn mẫu |
| `telemetry_emitted_total` | Sink xác nhận theo hợp đồng | Số sự kiện thực sự xuất |
| `telemetry_throttled_total` | Đã chọn nhưng hết quota | Mức độ mất chi tiết do limiter |
| `telemetry_export_failures_total` | Gửi sang sink thất bại | Đánh giá độ tin cậy đường quan sát |

Trong một bản demo ghi trực tiếp `System.out`, có thể coi “đã gọi println thành công” là số emitted ở mức minh họa. Trong hệ thống bất đồng bộ, “accepted into queue” chưa phải “exported”; telemetry adapter phải cung cấp semantic chính xác cho từng counter.

## 7. Ví dụ Java 17+: một around advice kết hợp cả ba cơ chế

Chương trình hoàn chỉnh dưới đây dùng **JDK dynamic proxy** để chặn lời gọi qua interface. Aspect:

- Tăng tổng lượt gọi và tổng lỗi cho **mọi** invocation, dù không in log.
- Lấy mẫu có chủ đích **một trong mỗi hai lượt gọi** để đầu ra chạy mẫu dễ kiểm chứng. Chính sách tuần tự này dùng cho demo; trong hệ thống thật, nên cân nhắc xác suất hoặc hash ổn định theo trace ID.
- Dùng token bucket đồng bộ, capacity một sự kiện và refill một token sau mỗi 1.000 tick của **đồng hồ giả lập**. Nhờ clock do demo điều khiển, stdout hoàn toàn xác định.
- Chỉ in request ID đã được làm sạch, kết quả thành công/thất bại; **không in secret payload** và không định dạng các trường không được chọn.
- Phân biệt số “không được chọn mẫu” với số “được chọn nhưng bị giới hạn quota”.

Tạo file `ObservationAspectDemo.java`:

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public class ObservationAspectDemo {
    interface Service {
        String execute(String requestId, String secretPayload);
    }

    static final class ServiceImpl implements Service {
        @Override
        public String execute(String requestId, String secretPayload) {
            if ("r07".equals(requestId)) {
                throw new IllegalArgumentException("invalid request");
            }
            return "OK";
        }
    }

    static final class TokenBucket {
        private final LongSupplier nowNanos;
        private final long intervalNanos;
        private final int capacity;
        private long lastRefill;
        private int tokens;

        TokenBucket(int capacity, long intervalNanos, LongSupplier nowNanos) {
            if (capacity <= 0 || intervalNanos <= 0) {
                throw new IllegalArgumentException("invalid budget");
            }
            this.capacity = capacity;
            this.tokens = capacity;
            this.intervalNanos = intervalNanos;
            this.nowNanos = nowNanos;
            this.lastRefill = nowNanos.getAsLong();
        }

        synchronized boolean tryAcquire() {
            long now = nowNanos.getAsLong();
            long elapsed = now - lastRefill;
            if (elapsed < 0) {
                throw new IllegalStateException("clock moved backwards");
            }

            long refill = elapsed / intervalNanos;
            if (refill > 0) {
                tokens = (int) Math.min((long) capacity, tokens + refill);
                lastRefill += refill * intervalNanos;
            }

            if (tokens == 0) {
                return false;
            }
            tokens--;
            return true;
        }
    }

    static final class ObservationAspect implements InvocationHandler {
        private final Service target;
        private final TokenBucket limiter;
        private long calls;
        private long errors;
        private long candidates;
        private long emitted;
        private long throttled;

        ObservationAspect(Service target, TokenBucket limiter) {
            this.target = target;
            this.limiter = limiter;
        }

        @Override
        public synchronized Object invoke(
                Object proxy, Method method, Object[] args) throws Throwable {
            long ordinal = ++calls;
            boolean success = false;
            try {
                Object result = method.invoke(target, args);
                success = true;
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            } finally {
                if (!success) {
                    errors++;
                }

                // Sampling minh hoa: lay moi invocation thu hai.
                if (ordinal % 2 == 0) {
                    candidates++;
                    if (limiter.tryAcquire()) {
                        String id = safeId(String.valueOf(args[0]));
                        System.out.println("observe id=" + id
                                + " outcome=" + (success ? "ok" : "error"));
                        emitted++;
                    } else {
                        throttled++;
                    }
                }
            }
        }

        private static String safeId(String value) {
            String normalized = value.replaceAll("[^A-Za-z0-9_-]", "_");
            return normalized.length() <= 8
                    ? normalized : normalized.substring(0, 8);
        }

        synchronized String summary() {
            return "total=" + calls + " errors=" + errors
                    + " sampled=" + candidates + " emitted=" + emitted
                    + " throttled=" + throttled;
        }
    }

    public static void main(String[] args) {
        AtomicLong demoClock = new AtomicLong(0);
        TokenBucket bucket = new TokenBucket(1, 1_000, demoClock::get);
        ObservationAspect aspect =
                new ObservationAspect(new ServiceImpl(), bucket);

        Service service = (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] { Service.class },
                aspect);

        for (int i = 1; i <= 8; i++) {
            if (i == 5) {
                demoClock.set(1_000); // Refill truoc invocation thu nam.
            }
            String id = String.format("r%02d", i);
            try {
                service.execute(id, "PRIVATE-SECRET");
            } catch (IllegalArgumentException expected) {
                // Nghiep vu co mot loi co chu y o invocation r07.
            }
        }
        System.out.println(aspect.summary());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ObservationAspectDemo.java
java ObservationAspectDemo
~~~

**Stdout chính xác:**

~~~text
observe id=r02 outcome=ok
observe id=r06 outcome=ok
total=8 errors=1 sampled=4 emitted=2 throttled=2
~~~

Giải thích theo đúng thứ tự thực thi:

| Lời gọi | Lấy mẫu (mỗi 2 lượt) | Còn token? | Kết quả |
| --- | --- | --- | --- |
| r01 | Không | Không cần kiểm tra | Không có log |
| r02 | Có | Có (token đầu tiên) | Phát log r02 |
| r03 | Không | Không cần kiểm tra | Không có log |
| r04 | Có | Không | Tăng throttled |
| r05 | Không | Không cần kiểm tra; clock tăng lên 1.000 | Không có log |
| r06 | Có | Có (token mới) | Phát log r06 |
| r07 | Không | Không cần kiểm tra | Lỗi nghiệp vụ; vẫn tăng errors |
| r08 | Có | Không | Tăng throttled |

Đồng hồ giả lập dùng đơn vị nanosecond **nhằm kiểm thử thuật toán**, không đại diện cho tải thực tế. Khi triển khai, truyền `System::nanoTime` và thiết kế xử lý số học/thời gian dài, độ trễ và burst phù hợp. Trong ví dụ, `synchronized` đơn giản hóa đồng thời để dễ đọc; một Aspect production cần xác định scope và tránh khóa toàn bộ lời gọi nghiệp vụ bằng monitor của advice như mã demo. Có thể tách phần lấy số thứ tự, counter và limiter sang `AtomicLong`/`LongAdder` cùng bucket thread-safe, chỉ khóa trạng thái limiter nếu cần.

Điểm cần quan sát là **r07 bị lỗi nhưng không được log chi tiết vì không được lấy mẫu; `errors=1` vẫn đúng**. Điều này minh họa khác biệt giữa số đo tổng và số log. Nếu lỗi luôn cần được ghi chi tiết, hãy thiết kế thêm policy ưu tiên lỗi với quota/kênh dự phòng thay vì chỉ tăng xác suất chung.

Ví dụ cũng cho thấy **output reduction bằng allowlist**: không hề log `secretPayload`; request ID được chuẩn hóa và giới hạn tám ký tự. Trong hệ thống thật, request ID có thể là dữ liệu nhạy cảm theo chính sách dữ liệu; nên dùng định danh tương quan được cấp phép thay vì nhận bất cứ chuỗi nào từ client rồi đưa vào log.

## 8. Thứ tự advice và khả năng bị quan sát trùng

Một hệ thống có thể dùng nhiều Aspect: một Aspect đo thời gian, một Aspect truy vết, một Aspect log lỗi. Thứ tự áp dụng advice ảnh hưởng đến số lần đo:

~~~text
Aspect quan sat tong
  -> Aspect retry
      -> Nghiep vu (co the chay 3 lan)
~~~

Nếu Aspect tổng nằm ngoài retry, nó ghi **một operation logic**; Aspect nằm trong retry có thể ghi **ba lần thử**. Không có cách đếm nào mặc nhiên đúng: phải đặt tên metric rõ `operations_total` và `attempts_total`. Không nên để sampling của outer Aspect vô tình ngăn ghi metrics ở inner Aspect nếu cần thống kê từng attempt.

Tương tự, một lời gọi chạy qua hai interception boundary có thể tạo hai span/log cho cùng event. Dùng operation ID, context đã gắn dấu hoặc thiết kế điểm chặn nhất quán để tránh đếm trùng. Khi dùng proxy, việc chặn phương thức trên interface phụ thuộc lời gọi đi qua proxy; lời gọi nội bộ trực tiếp không nhất thiết bị chặn. Cơ chế weaving khác có tập join point khác; cần xác định rõ nơi advice thực sự chạy.

Sampling head nên thường được xác định ở cấp **trace/operation**, sau đó truyền quyết định qua các nhánh. Nếu mỗi Aspect ở mỗi tầng tung đồng xu độc lập, một trace có thể mất các span quan trọng, trong khi số event phát vẫn nhiều bất ngờ.

## 9. Quan sát bất đồng bộ, context và callback

Khi phương thức trả `CompletionStage<T>`, một around advice nhìn thấy **stage được trả về**, không tự động thấy hoàn tất thực tế. Nếu muốn quan sát terminal outcome, có thể đăng ký callback:

~~~java
// Doan minh hoa trong advice; stage co kieu CompletionStage<T>.
CompletionStage<T> observed = stage.whenComplete((value, error) -> {
    // Tang counters tong cho terminal outcome.
    // Sau do ap sampling, limiter va xuat metadata an toan.
});
return observed;
~~~

Đây là **đoạn minh họa**, không phải chương trình độc lập. Chú ý:

- `whenComplete` có thể chạy trên thread hoàn tất stage, không có bảo đảm mặc định chạy trên thread gọi. Nếu callback làm serialize/IO lâu hoặc có limiter chặn, nó có thể kéo chậm cả pipeline.
- Callback không nhất thiết giữ `ThreadLocal` từ lúc gọi. Truyền context/sampling decision tường minh qua stage hoặc context propagation được thiết kế rõ.
- Nếu `whenComplete` ném exception, nó có thể ảnh hưởng stage được trả về. Cần bắt lỗi không quan trọng của telemetry và giữ nguyên hợp đồng kết quả, hoặc dùng một kênh observer độc lập theo thiết kế.
- Một stage có thể bị timeout/hủy ở mức handle trong khi worker còn chạy; phân biệt **logical completion** với **physical completion** trước khi tính độ trễ tác vụ thực hoặc giải phóng resource.
- Nếu tạo thêm callback chỉ để log, tránh giữ payload lớn trong closure hoặc làm tăng thời gian sống của dữ liệu nhạy cảm.
- Tại nhánh streaming, số item và số request là hai đơn vị quan sát khác nhau; sampling theo subscription/request hoặc từng item cần hợp đồng rõ. Không tạo một log cho mỗi item theo mặc định khi lưu lượng cực lớn.

Một thiết kế an toàn thường ghi counter tổng thật sớm hoặc tại mốc hoàn tất phù hợp, còn việc chọn/sắp xếp/publish log được chuyển sang adapter/queue có tài nguyên và quota độc lập. Đảm bảo trình xử lý async khi kết thúc không phát hai lần terminal event chỉ vì có cả callback success và callback timeout.

## 10. Bảo mật, độ tin cậy và tác động đến nghiệp vụ

### 10.1. Không rò dữ liệu qua telemetry

Aspect quan sát có thể truy cập nhiều tham số hơn log thủ công trong nghiệp vụ, nên dễ vô tình in dữ liệu nhạy cảm. Không dùng `Arrays.toString(args)`, phản chiếu toàn bộ field hoặc serialize object graph theo mặc định. Thực hiện theo thứ tự: **allowlist trường → chuẩn hóa/redact → giới hạn kích thước → phát**. Với exception, chỉ phát loại lỗi hoặc mã lỗi phù hợp; message có thể chứa secret, SQL, URL hay nội dung request.

Một hash của thông tin cá nhân không tự động vô danh nếu miền giá trị nhỏ hoặc dễ dò. Ưu tiên correlation ID ngẫu nhiên được cấp hợp lệ, và có chính sách retention/phân quyền cho log.

### 10.2. Không làm telemetry trở thành điểm nghẽn

Ở hot path, việc tạo stack trace, format chuỗi, lấy thời gian lặp lại, ghi file đồng bộ hoặc gọi network có thể đắt hơn nghiệp vụ. Khi sink thất bại, một Aspect quan sát thông thường nên ưu tiên giữ semantics nghiệp vụ, báo lỗi qua metric và bộ lọc circuit breaker hoặc cơ chế queue hữu hạn.

Không dùng `catch (Throwable)` một cách tùy tiện quanh logic nghiệp vụ rồi bỏ lỗi, cũng không nuốt `InterruptedException` của tác vụ. Nếu chỉ cần bắt lỗi ở đoạn telemetry tự quản lý, hãy bao phạm vi try/catch hẹp quanh emitter và định nghĩa lỗi nào được phép bỏ.

### 10.3. Trường hợp khẩn cấp và ưu tiên dữ liệu

Khi quá tải, cần phân cấp ưu tiên: lỗi hệ thống hiếm, cảnh báo mất dữ liệu, sự kiện bảo mật bắt buộc và log thông thường có giá trị khác nhau. Có thể dành quota riêng cho nhóm ưu tiên cao, giữ những counter cốt lõi và giảm log debug theo tải. Nhưng nếu một chính sách đòi hỏi **lưu mọi sự kiện kiểm toán**, không được dùng sampling/drop mà phải dự trù đường lưu bền và xử lý tắc nghẽn theo hợp đồng.

Nếu báo cáo “đã loại bỏ 12.000 bản ghi”, cần nói rõ **vì lấy mẫu**, **vì hết quota**, **vì queue đầy** hay **vì sink xuất thất bại**. Những nguyên nhân này có giải pháp khác nhau và không nên cộng bừa thành một chỉ số không giải thích được.

## 11. Thiết kế policy thực tế

Một tài liệu policy cho Aspect nên quy định ít nhất:

| Thành phần | Điều cần quyết định | Ví dụ minh họa |
| --- | --- | --- |
| Join point | Lời gọi logic hay từng attempt? Đồng bộ hay bất đồng bộ? | Bao quanh API truy vấn, đo terminal completion |
| Dữ liệu cốt lõi | Counter/histogram nào không lấy mẫu? | requests_total, failures_total, latency histogram |
| Sampling | Head/tail, xác suất, trace key, ngoại lệ theo kết quả | 1% trace bình thường; ưu tiên lỗi |
| Rate limiter | Scope, tốc độ, burst, trọng lượng bản ghi | 100 events/s, burst 200 cho mỗi instance |
| Reduction | Allowlist, schema, byte cap, redaction, fingerprints | <=2 KiB/event, bỏ body, gộp stack lặp |
| Overflow | Bỏ, gộp, queue hữu hạn hay kênh ưu tiên? | Đếm suppressed, gộp theo mỗi cửa sổ |
| Reliability | Khi emitter thất bại thì nghiệp vụ ra sao? | Ghi nhận export failure; không đổi kết quả |
| Governance | Quyền xem, lưu giữ, xóa và kiểm tra rò rỉ | Hạn lưu trữ và access theo loại dữ liệu |

Các con số trên **chỉ minh họa**, cần hiệu chỉnh từ tải, yêu cầu chẩn đoán, giới hạn sink và đặc tính thời gian của hệ thống cụ thể.

### 11.1. Tính ngân sách đầu ra sơ bộ

Giả sử một instance nhận 50.000 request/giây; một log chi tiết trung bình 1 KiB:

- Ghi mọi request: khoảng 50.000 KiB/giây, chưa tính overhead.
- Head sampling 1%: **kỳ vọng** khoảng 500 log/giây, tức 500 KiB/giây, nhưng số thực tế có biến động.
- Nếu limiter tiếp tục chỉ cho tối đa trung bình 100 log/giây (sau burst), đầu ra dài hạn khoảng 100 KiB/giây với giả định kích thước log giữ 1 KiB.
- Nếu rút payload trung bình xuống 300 byte, đầu ra dài hạn khoảng 30.000 byte/giây, chưa tính overhead, bất kỳ burst nào và queue đang tích lại.

Đây là ước lượng **dung lượng log chi tiết**, không phải tổng chi phí telemetry: counters, trace context, buffer, mạng, CPU serialization và metadata vẫn tiêu tốn tài nguyên. Cũng không thể dùng các số trên để kết luận chất lượng quan sát vẫn đủ: phải đo khả năng tìm lỗi sau khi giảm dữ liệu.

### 11.2. Chọn thứ tự ưu tiên phù hợp

Có thể chia dữ liệu thành một tầng **định lượng đầy đủ** (counter và histogram cardinality thấp) và một tầng **chẩn đoán chi tiết chọn lọc** (log/trace). Các sự kiện lỗi hiếm hoặc cảnh báo bắt buộc có thể đi qua kênh riêng với quota độc lập. Một sampler toàn cục cộng một limiter toàn cục rất dễ khiến một nhóm có tải lớn chiếm hết quyền phát của những nhóm nhỏ nhưng quan trọng.

Nếu đổi chính sách lúc chạy, phải quan sát hiệu lực và tỷ lệ mới. Việc đổi `p` từ 1% thành 0,1% làm lượng log giảm nhưng cũng làm tần suất thấy lỗi hiếm giảm khoảng 10 lần nếu lỗi cũng được lấy mẫu độc lập, cho nên cần đánh giá trên dữ liệu thực.

## 12. Kiểm thử và xác minh Aspect quan sát

Các phép kiểm thử cần tập trung vào **hành vi có thể chứng minh**, không chỉ thấy một số dòng log xuất hiện:

1. **Correctness của nghiệp vụ:** khi sampling từ chối hoặc limiter hết quota, kết quả/exception từ phương thức gốc phải giữ nguyên; phương thức gốc được gọi đúng một lần.
2. **Counter tổng:** gọi N lần, trong đó M lần lỗi; `invocations_total = N` và `failures_total = M` dù mọi log đều bị bỏ.
3. **Sampling xác định:** với fake sampler, kiểm tra các invocation nào được chọn; với hash theo trace ID, cùng ID phải cho quyết định nhất quán.
4. **Quota:** dùng clock giả để kiểm tra hết token, refill, burst, biên cửa sổ, clock bất thường và sự kiện trọng lượng lớn.
5. **Concurrency:** nhiều thread xin token đồng thời không vượt quota; counters thread-safe; không deadlock giữa advice với emitter/queue.
6. **Reduction & privacy:** secret không xuất hiện trong output kể cả lỗi, object có `toString()` nguy hiểm, dữ liệu Unicode dài, newline injection hoặc exception message nhạy cảm.
7. **Async:** phân biệt thời điểm trả stage và terminal completion; đo đúng một lần khi success/error/cancel/timeout; callback telemetry không làm hỏng future nghiệp vụ.
8. **Overload & failure:** queue đầy, exporter chậm, collector từ chối, shutdown; kiểm tra số suppressed/export failure và giới hạn bộ nhớ.
9. **Chất lượng phân tích:** với các ca lỗi hiếm, xác minh policy vẫn để lại bằng chứng đủ điều tra và metric không bị bias bởi sampling.
10. **Phạm vi interception:** nested calls, retry và self-call không tạo telemetry trùng/thiếu ngoài ý muốn.

Một bài kiểm tra tốt dùng fake clock, fake sink, sampler có kết quả kiểm soát được và bộ đếm độc lập. Đừng chỉ dựa trên `Thread.sleep` rồi so thứ tự log vì kết quả dễ dao động theo lịch chạy.

## 13. Sai lầm thường gặp và cách khắc phục

| Sai lầm | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| In toàn bộ arguments và return values bằng reflection | Rò dữ liệu, chuỗi log rất lớn | Dùng allowlist và schema an toàn; lazy serialization |
| Sampling xong mới tăng requests_total | Metric tổng bị thiếu | Tách counter tổng khỏi nhánh log chi tiết |
| Mỗi span tự quyết định sample độc lập | Trace đứt đoạn, khó ghép | Truyền và kế thừa sampling context ở cấp trace |
| Dùng fixed window rồi cho rằng không bao giờ có burst | Có thể dồn burst tại ranh giới | Chọn token bucket/sliding window, khai báo burst |
| Một limiter cho mọi loại sự kiện | Event bình thường lấn át lỗi hiếm | Quota theo mức ưu tiên/nhóm có giới hạn |
| Ghi log đồng bộ trong monitor của advice | Tăng tail latency, nghẽn thread | Chặn tối thiểu, queue hữu hạn, emitter độc lập |
| Coi đã enqueue là đã export | Báo sai độ tin cậy | Phân biệt accepted, exported, dropped |
| Cắt chuỗi theo byte tùy tiện | Hỏng UTF-8, mất ngữ nghĩa | Cắt đúng ranh giới ký tự; gắn cờ truncated |
| Bỏ lỗi telemetry mà không đo đếm | Mù quan sát ngay khi quá tải | Counter/signal dự phòng, giám sát loss |
| Coi sampling “lấy 1%” là quota cứng | Log vẫn bùng nổ khi traffic tăng | Kết hợp limiter theo tốc độ/byte |
| Tự ý cắt kết quả trả về của nghiệp vụ | Thay đổi hợp đồng API | Giảm **telemetry output**, giữ nguyên business result |
| Đếm cả retry là request mới mà không ghi rõ | Error rate/success rate sai nghĩa | Tách operation và attempt với tên metric khác nhau |

Ba cơ chế có quan hệ bổ sung: **sampling quyết định giữ sự kiện nào**, **rate limiting kiểm soát tốc độ hoặc ngân sách phát**, còn **output reduction tối thiểu hóa chi phí mỗi sự kiện**. Khi cài bằng Aspect, thiết kế tốt nhất đồng thời giữ đúng semantics nghiệp vụ, bảo đảm dữ liệu quan trọng không mất âm thầm, và giảm chi phí quan sát đến mức có thể kiểm chứng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-observability-007">Không để lỗi ghi nhật ký làm thay đổi kết quả ngoài chính sách</a>

<details>
<summary>Xem chi tiết</summary>

# Không để lỗi ghi nhật ký làm thay đổi kết quả ngoài chính sách trong AOP

## 1. Mục tiêu: tính đúng đắn nghiệp vụ phải đi trước cơ chế quan sát

Logging advice trong AOP có thể chạy trước, sau hoặc bao quanh nhiều join point để ghi tình trạng lời gọi. Khác với các bài về chọn sự kiện hay lọc dữ liệu, ở đây câu hỏi chính là: **khi chính hệ thống ghi log thất bại, kết quả nghiệp vụ phải được bảo vệ như thế nào?**

Tuy nhiên, **ghi nhật ký cũng là một thao tác có thể thất bại**. Hệ thống ghi log có thể hết dung lượng, không truy cập được tệp, bị từ chối gửi mạng, đầy hàng đợi, serialize dữ liệu lỗi, mắc lỗi khi định dạng chuỗi hoặc chính đoạn instrumentation bị lỗi. Nếu thực hiện sơ suất, lỗi thứ cấp từ logger sẽ:

- Biến một thao tác nghiệp vụ thành công thành một lời gọi ném ngoại lệ.
- Che mất ngoại lệ nghiệp vụ ban đầu, khiến caller chỉ thấy lỗi ghi log.
- Chặn việc gọi nghiệp vụ vì lời ghi log ở advice *before* thất bại.
- Thay đổi giá trị trả về, loại ngoại lệ, thông điệp hoặc trạng thái future.
- Làm một callback/thao tác bất đồng bộ bị đánh dấu thất bại dù công việc gốc đã thành công.
- Gây chậm, tắc hàng đợi, deadlock hoặc tiêu tốn tài nguyên ngoài giới hạn dự kiến.

Vì vậy, trước khi viết advice cần có **chính sách lỗi ghi nhật ký**. Với log quan sát thông thường, chính sách phổ biến là *best effort/fail-open*: logger lỗi thì ghi nhận sự cố theo cơ chế dự phòng được kiểm soát, nhưng vẫn giữ nguyên kết quả nghiệp vụ. Với nhật ký bắt buộc vì kiểm toán, bảo mật hoặc ràng buộc pháp lý, có thể cần *fail-closed*: không cho phép thực hiện hành động khi điều kiện ghi audit chưa được đáp ứng. **Đó phải là quyết định nghiệp vụ có chủ đích**, được thực thi tại một ranh giới phù hợp, không phải tác dụng phụ ngẫu nhiên của một lệnh ghi log bị lỗi.

Mục tiêu học tập:

1. Hiểu vì sao lời gọi logger trong advice có thể phá tính minh bạch của một aspect.
2. Nhận diện các vị trí logging che khuất kết quả hoặc ngoại lệ nghiệp vụ.
3. Thiết kế ranh giới cô lập lỗi cho log best effort, cả đồng bộ lẫn bất đồng bộ.
4. Biết lúc nào cần chính sách fail-open, fail-closed, buffer hoặc cơ chế lưu audit bền vững.
5. Kiểm chứng bằng Java 17+ rằng logger thất bại nhưng caller vẫn nhận đúng giá trị hoặc ngoại lệ gốc.

## 2. Kết quả “không thay đổi” nghĩa là gì?

**Tính minh bạch quan sát (observational transparency)** ở phạm vi này có nghĩa: trừ những khác biệt đã được chính sách cho phép, thêm logging aspect không làm thay đổi các kết quả mà thành phần nghiệp vụ và caller có thể quan sát.

Với một lời gọi đồng bộ, cần bảo toàn:

- **Giá trị trả về**, gồm giá trị null nếu hợp đồng cho phép.
- **Việc ném ngoại lệ hay trả bình thường**: không đổi nhánh điều khiển chỉ vì sink log lỗi.
- **Loại và đối tượng ngoại lệ** khi hợp đồng yêu cầu; tốt nhất truyền tiếp chính ngoại lệ nghiệp vụ đã nhận, không tự thay bằng lỗi logging.
- **Nguyên nhân và stack trace** của lỗi nghiệp vụ; đừng tự đóng gói hoặc xóa mất chúng nếu không có yêu cầu.
- **Tác dụng phụ và thứ tự nghiệp vụ**: logging không được bất ngờ khiến nghiệp vụ chạy hai lần, bỏ qua hoặc rollback giả tạo.
- **Ràng buộc hiệu năng được công bố**: best effort không có nghĩa chấp nhận treo vô hạn khi ghi log.

Với bất đồng bộ, còn phải xem **kết quả cuối cùng của future, callback, task hoặc stream**. Phương thức trả về một future bình thường chưa phải công việc thành công. Nếu observer log ném lỗi vào dependent stage, caller có thể thấy lỗi logging thay cho kết quả công việc. Vì vậy, chỉ kiểm tra advice đồng bộ là chưa đủ.

Không có bảo đảm tuyệt đối rằng logging sẽ không bao giờ ảnh hưởng tới hệ thống: ghi log luôn tốn CPU, bộ nhớ và có thể gây lỗi nghiêm trọng của JVM. Mục tiêu thực tế là **quy định và kiểm soát những failure mode có thể xử lý**, nhất là RuntimeException ở tầng ghi log, chi phí blocking và chính sách quá tải. Không nên hứa rằng bắt mọi Throwable có thể khiến chương trình an toàn trước OutOfMemoryError hay lỗi nghiêm trọng của runtime.

## 3. Các lỗi cơ bản khi viết advice

### 3.1. Ghi log trong finally có thể che ngoại lệ hoặc làm mất kết quả

Đoạn minh họa sau có lỗi thiết kế:

~~~java
// Minh họa anti-pattern; target và sink là thành phần đã được khai báo.
try {
    return target.call();
} finally {
    sink.write("finished");
}
~~~

Nếu target trả về “OK” nhưng sink.write ném IllegalStateException, caller sẽ nhận **IllegalStateException từ sink**, không nhận “OK”. Nếu target đã ném IllegalArgumentException và sink.write ném lỗi khi finally chạy, lỗi từ sink có thể thay thế ngoại lệ nghiệp vụ trên đường thoát. Finally bảo đảm có cơ hội thực thi cleanup; nó **không bảo đảm thao tác trong finally là vô hại**.

Cách sử dụng đúng cho log best effort là bọc riêng thao tác logger trong một vùng xử lý lỗi:

~~~java
// Mã minh họa một đoạn advice; safeLog có hợp đồng không ném
// những lỗi logger thông thường thuộc chính sách best effort.
try {
    return target.call();
} finally {
    safeLog("finished");
}
~~~

Bản chất của safeLog quan trọng hơn tên gọi: nó phải xử lý được **toàn bộ thao tác thuộc logging**, kể cả tạo dữ liệu log nếu dữ liệu đó có thể ném RuntimeException.

### 3.2. Ghi log ngay trong catch có thể che ngoại lệ gốc

~~~java
// Anti-pattern
try {
    return target.call();
} catch (RuntimeException businessError) {
    sink.write("business failed: " + businessError.getMessage());
    throw businessError;
}
~~~

Nếu sink.write lỗi, lệnh throw businessError không chạy đến. Caller nhận lỗi logging. Đây là tình huống che giấu nguyên nhân phổ biến trong xử lý ngoại lệ.

Phiên bản phù hợp với chính sách best effort:

~~~java
try {
    return target.call();
} catch (RuntimeException businessError) {
    safeLogBusinessFailure(businessError);
    throw businessError;
}
~~~

**Điểm then chốt:** giữ lại đối tượng businessError và ném lại chính nó sau khi thử ghi log. Không dùng một catch tổng quát rồi vô tình trả giá trị mặc định hoặc đổi thành ngoại lệ “Logging failed” nếu chính sách không cho phép.

### 3.3. Advice trước lời gọi có thể ngăn nghiệp vụ chạy

~~~java
// Anti-pattern trong chính sách fail-open
sink.write("starting work");
return target.call();
~~~

Nếu sink.write ném lỗi, target.call không được thực thi. Có thể sửa bằng helper ghi log an toàn, rồi tiếp tục gọi target. Với chính sách audit bắt buộc, chặn trước khi hành động diễn ra có thể đúng, nhưng phải nêu tường minh rằng *thiếu audit thì từ chối hành động*, thay vì xem đó là bug logging tình cờ.

### 3.4. Ghi log sau return có thể biến thành công thành lỗi

~~~java
// Anti-pattern
Result result = target.call();
sink.write("success");
return result;
~~~

Một lỗi từ sink sau khi target đã gây side effect sẽ làm caller thấy lời gọi thất bại. Nếu caller retry dựa trên ngoại lệ đó, side effect nghiệp vụ có thể **bị lặp**. Đây là lý do cần tách lỗi logging khỏi kết quả trước khi trả về.

### 3.5. Gom mọi thao tác vào một catch có thể nuốt nhầm lỗi nghiệp vụ

~~~java
// Anti-pattern: kết quả fallback không hề có trong contract
try {
    sink.write("start");
    return target.call();
} catch (RuntimeException error) {
    return fallback;
}
~~~

Catch này không phân biệt lỗi logging với lỗi target. Nếu target lỗi thật, nó cũng bị chuyển thành fallback. Hãy dùng **ranh giới try/catch riêng** cho logger; nếu cần catch lỗi nghiệp vụ, catch để ghi nhận và ném lại theo hợp đồng nghiệp vụ.

## 4. Thiết kế helper logging best effort đúng trách nhiệm

Một wrapper nhỏ giúp các advice tái sử dụng cùng một chính sách:

~~~java
@FunctionalInterface
interface LogSink {
    void append(String event);
}

static void safeLog(LogSink sink, String event) {
    try {
        sink.append(event);
    } catch (RuntimeException loggingFailure) {
        // Chính sách best effort:
        // không để lỗi logger thay thế outcome của target.
        // Ghi nhận degradation ở cơ chế riêng nếu có,
        // nhưng cơ chế dự phòng cũng phải được kiểm soát.
    }
}
~~~

Đây là đoạn minh họa, chưa phải chương trình độc lập. Những điều cần xác định rõ khi triển khai thực tế:

1. **Phạm vi lỗi được chấp nhận:** helper bắt RuntimeException đến từ thao tác ghi log. Không bọc cả target.call trong helper này. Không vô điều kiện bắt Throwable rồi nuốt cả Error của JVM.
2. **Tạo event thuộc vùng được bảo vệ:** nếu message serialization, gọi toString, truy cập dữ liệu hoặc thêm trường ngữ cảnh có thể lỗi, thực hiện chúng **bên trong** ranh giới best effort.
3. **Phản ứng của hệ thống khi log lỗi:** có thể tăng bộ đếm trong bộ nhớ, cảnh báo qua health status, dùng kênh sự cố độc lập hoặc sampling; không gọi ngược cùng sink đang hỏng vô hạn.
4. **Giới hạn thời gian và tài nguyên:** catch exception không giải quyết logger bị treo hay ghi log chặn luồng quá lâu. Cần timeout, queue hữu hạn hoặc phương án non-blocking tùy độ quan trọng của log.
5. **Bảo toàn ngoại lệ đang truyền:** helper không được ném exception riêng, thay đổi cause chain, sửa return value hoặc gọi lại target để “bù” sự cố logging.
6. **Tránh nuốt âm thầm ở mọi trường hợp:** best effort là quyết định có chủ đích; hệ thống vẫn cần chỉ báo đang mất khả năng quan sát ở kênh độc lập nếu yêu cầu vận hành cần biết.

Khi event được xây dựng từ đầu vào có thể lỗi, có thể dùng callback trì hoãn định dạng để đưa toàn bộ thao tác vào catch:

~~~java
// Đoạn minh họa; Supplier lấy từ java.util.function.
static void safeLog(
        LogSink sink,
        java.util.function.Supplier<String> eventSupplier) {
    try {
        sink.append(eventSupplier.get());
    } catch (RuntimeException loggingFailure) {
        // Ghi nhận logger degraded theo kênh độc lập (nếu khả thi).
    }
}
~~~

Thời điểm đánh giá supplier vẫn nằm trong luồng gọi, do đó tránh xây dựng log quá tốn kém hoặc chứa dữ liệu nhạy cảm. Helper này không bảo đảm cô lập tác vụ blocking; cần cấu hình vận chuyển log riêng cho trường hợp đó.

## 5. Một chương trình Java 17+ kiểm chứng trực tiếp

Ví dụ dưới mô phỏng sink luôn ném IllegalStateException. Aspect được biểu diễn bằng các hàm bao quanh lời gọi và completion observer để tập trung vào **ngữ nghĩa lỗi**; đây không phải một runtime weaving đầy đủ.

Tạo file **LoggingIsolationDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public class LoggingIsolationDemo {
    @FunctionalInterface
    interface LogSink {
        void append(String event);
    }

    static void safeLog(LogSink sink, String event) {
        try {
            sink.append(event);
        } catch (RuntimeException loggingFailure) {
            System.out.println("LOGGER DOWN: " + loggingFailure.getMessage());
        }
    }

    static <T> T around(String operation, Supplier<T> business, LogSink sink) {
        safeLog(sink, "begin " + operation);
        try {
            T result = business.get();
            safeLog(sink, "success " + operation);
            return result;
        } catch (RuntimeException businessError) {
            safeLog(sink, "failure " + operation);
            throw businessError;
        }
    }

    static <T> CompletionStage<T> observeCompletion(
            String operation, CompletionStage<T> source, LogSink sink) {
        return source.whenComplete((value, failure) -> {
            safeLog(sink, failure == null
                    ? "completed " + operation
                    : "failed " + operation);
        });
    }

    public static void main(String[] args) {
        LogSink broken = event -> {
            throw new IllegalStateException("storage unavailable");
        };

        String result = around("read", () -> "DATA", broken);
        System.out.println("RESULT: " + result);

        RuntimeException original =
                new IllegalArgumentException("invalid input");
        try {
            around("parse", () -> {
                throw original;
            }, broken);
        } catch (RuntimeException observed) {
            System.out.println("SAME ERROR: " + (observed == original));
        }

        CompletionStage<Integer> success = observeCompletion(
                "calculate", CompletableFuture.completedFuture(42), broken);
        System.out.println("ASYNC VALUE: " + success.toCompletableFuture().join());

        CompletableFuture<Integer> pending = new CompletableFuture<>();
        CompletionStage<Integer> failed = observeCompletion(
                "remote", pending, broken);
        pending.completeExceptionally(new IllegalArgumentException("timeout"));
        try {
            failed.toCompletableFuture().join();
        } catch (CompletionException error) {
            System.out.println("ASYNC ERROR: " + error.getCause().getMessage());
        }
    }
}
~~~

Biên dịch, chạy:

~~~bash
javac LoggingIsolationDemo.java
java LoggingIsolationDemo
~~~

Kết quả stdout xác định:

~~~text
LOGGER DOWN: storage unavailable
LOGGER DOWN: storage unavailable
RESULT: DATA
LOGGER DOWN: storage unavailable
LOGGER DOWN: storage unavailable
SAME ERROR: true
LOGGER DOWN: storage unavailable
ASYNC VALUE: 42
LOGGER DOWN: storage unavailable
ASYNC ERROR: timeout
~~~

### Phân tích kết quả

**Trường hợp read:** logging ở đầu và cuối đều hỏng nhưng around vẫn trả “DATA”. Advice giữ đúng đường return.

**Trường hợp parse:** target ném một đối tượng IllegalArgumentException đã tạo sẵn. Logging lúc bắt đầu và lúc xử lý thất bại đều hỏng nhưng advice ném lại **chính đối tượng ngoại lệ gốc**. Biểu thức so sánh tham chiếu trả true.

**Trường hợp calculate:** source là completedFuture(42), nên observer có thể chạy ngay khi đăng ký. Lỗi logger bị cô lập; stage phụ thuộc vẫn hoàn thành với 42.

**Trường hợp remote:** source future đang pending được gắn observer. Khi completeExceptionally được gọi, callback logging chạy và xử lý lỗi sink. Stage quan sát vẫn hoàn tất exceptional bằng lỗi nghiệp vụ; khi join, caller nhận CompletionException chứa IllegalArgumentException("timeout").

Cách tạo future trong ví dụ tránh yếu tố nondeterministic của lịch chạy nhiều thread, nhờ đó thứ tự stdout ổn định. Trong code sản xuất, completion callback có thể chạy trên nhiều luồng tùy API và thời điểm hoàn tất. Vì vậy không được dựa vào thứ tự dòng log giữa các luồng để kết luận thứ tự nghiệp vụ.

**Giới hạn chủ ý của ví dụ:** safeLog in ra console khi log lỗi để dễ học và kiểm chứng. Console ở ví dụ là **kênh minh họa**, không phải lời hứa rằng kênh dự phòng luôn hoạt động. Nếu chính System.out cũng lỗi, đoạn này chưa cô lập lỗi đó. Trong hệ thống thực, hãy thiết kế một kênh báo degradation phù hợp hoặc chấp nhận best effort hoàn toàn và xác định phạm vi lỗi được cô lập. around chỉ xử lý RuntimeException từ supplier; checked exception cần một giao diện có throws và chính sách tương ứng.

## 6. Phân biệt ba chính sách khi hệ thống ghi log hỏng

Không có một chính sách đúng cho tất cả kiểu nhật ký. Hãy phân loại **mục đích của dữ liệu** trước:

| Loại ghi nhận | Ví dụ | Chính sách thường phù hợp | Điều cần bảo toàn |
| --- | --- | --- | --- |
| Log chẩn đoán best effort | Thời gian gọi, dòng debug, thông tin xử lý | Fail-open, có giới hạn hao phí | Kết quả nghiệp vụ không phụ thuộc sink |
| Metrics/tracing quan sát | Số lời gọi, latency, span, sự kiện tracing | Thường fail-open; có sampling/backpressure | Không thay return, exception hay completion |
| Audit bắt buộc | Sự kiện nghiệp vụ cần chứng minh, yêu cầu tuân thủ | Theo yêu cầu nghiệp vụ: lưu bền vững hoặc fail-closed | Không thực thi thay đổi bị cấm nếu thiếu bảo đảm audit |
| Log vận hành quan trọng | Lỗi cần điều tra, cảnh báo an toàn | Buffer, retry hữu hạn, báo health degraded; có thể nâng mức cảnh báo | Không gây nghẽn vô hạn hoặc vòng lặp lỗi |
| Nhật ký quyết định nghiệp vụ | Bản ghi làm căn cứ thanh toán/đối soát | Coi là dữ liệu nghiệp vụ với transaction/reconciliation rõ ràng | Tính nhất quán và khôi phục theo hợp đồng |

**Fail-open**: khi kênh quan sát thất bại, nghiệp vụ vẫn chạy và trả kết quả theo chính hợp đồng của nó. Cần quy định hệ thống chấp nhận mất log bao nhiêu, cách báo hệ thống mất quan sát, giới hạn queue và tỷ lệ drop.

**Fail-closed**: nếu điều kiện audit là bắt buộc **trước khi thực hiện** một hành động nhạy cảm, có thể từ chối bắt đầu hành động khi chưa thể đảm bảo ghi nhận audit. Đây là một phần của contract nghiệp vụ và có thể phát sinh lỗi rõ ràng như “audit unavailable” theo chính sách được chấp thuận. Không sử dụng fail-closed một cách ngẫu nhiên chỉ vì logger throw sau khi tác dụng phụ đã xảy ra.

**Lưu bền vững/audit transaction**: khi nghiệp vụ và audit phải nhất quán, cần thiết kế commit chung trong phạm vi transaction phù hợp hoặc mẫu transactional outbox. Ví dụ cùng lưu thay đổi nghiệp vụ và một sự kiện audit/outbox trong một transaction của cùng kho dữ liệu; sender gửi tiếp sau commit và có cơ chế retry, idempotency, giám sát tồn đọng. Không tự suy diễn rằng hai hệ thống lưu trữ độc lập có thể atomic chỉ nhờ đặt chúng trong around advice.

### Vì sao một lời gọi ghi log sau khi nghiệp vụ chạy không tạo được audit atomic?

Giả sử chuyển tiền thành công rồi ghi audit bị lỗi. Nếu advice ném “audit unavailable” ra caller, tiền vẫn có thể đã chuyển; caller tưởng thao tác thất bại và có thể thực hiện lại. Ngược lại nếu âm thầm bỏ qua audit, yêu cầu tuân thủ có thể bị vi phạm. Giải pháp là ràng buộc đúng tính nguyên tử hoặc khả năng phục hồi của **thao tác ghi sự kiện audit** với **thao tác nghiệp vụ**, không chỉ đổi catch behavior.

Một quyết định chính sách hoàn chỉnh cần trả lời:

1. Loại log nào được phép mất?
2. Trạng thái logger degraded có làm từ chối thao tác mới không?
3. Ai được phép retry và có cơ chế idempotency nào?
4. Nếu lỗi xảy ra *sau commit*, caller sẽ nhận thông tin gì để tránh thực thi lại không an toàn?
5. Có replay, reconcile hay kiểm tra tồn đọng audit được không?
6. Các giá trị nào là dữ liệu nhạy cảm và phải được loại bỏ/che khi ghi nhận?

## 7. Lỗi logger phát sinh ngoài lời gọi đồng bộ

### 7.1. CompletionStage và future

Một advice quanh phương thức trả CompletionStage có thể kết thúc trước khi future hoàn tất. Nếu cần ghi log tại lúc hoàn tất, callback của stage cũng có khả năng ném lỗi. Trong Java, whenComplete tạo một **stage phụ thuộc**: lỗi do action của observer ném có thể khiến stage mới thất bại nếu stage gốc thành công.

Mã nguy hiểm:

~~~java
// Đoạn minh họa; stage và sink đã có sẵn.
return stage.whenComplete((value, error) -> {
    sink.append("operation ended"); // có thể throw
});
~~~

Mã phù hợp với chính sách best effort:

~~~java
return stage.whenComplete((value, error) -> {
    safeLog(sink, "operation ended");
});
~~~

Cần hiểu các ràng buộc bổ sung:

- Nếu callback thành công hoặc lỗi đã được helper cô lập, observer không cố tình thay đổi kết quả nguồn.
- Nếu callback observer ném lỗi nghiêm trọng ngoài phạm vi helper, dependent stage có thể chịu ảnh hưởng; không tuyên bố bất biến tuyệt đối.
- whenComplete trả stage mới; nếu giữ nguyên identity object là một phần của contract, có thể attach observer bên cạnh rồi trả source gốc, nhưng phải có chính sách riêng với dependent stage tạo ra.
- Cancellation propagation giữa dependent stage và upstream không nên suy đoán. Cần kiểm tra hành vi theo implementation và contract.
- Logger có thể chặn thread hoàn tất future. Bảo vệ chống throw chưa giải quyết độ trễ; có thể cần kênh gửi log không chặn với queue hữu hạn.

Đặc biệt, một callback thenApply/thenAccept do nghiệp vụ định nghĩa có thể throw; đó là lỗi của nhánh xử lý, không nhất thiết là lỗi của stage trước. Instrumentation phải ghi nhận đúng stage được định nghĩa là outcome chính.

### 7.2. Callback API và dispatcher

Nếu một API đăng ký callback rồi trả về, advice quanh register không thể thấy lỗi logging phát sinh sau đó khi callback chạy. Để cô lập lỗi logger ở callback, cần đặt helper **bên trong chính callback hoặc wrapper thực thi callback**.

~~~java
// Đoạn minh họa; delegate và sink là các thành phần đã khai báo.
void onResult(String value) {
    safeLog(sink, "result delivered");
    delegate.onResult(value); // lỗi của delegate là lỗi riêng của delegate
}
~~~

Không được viết catch RuntimeException quanh cả safeLog và delegate rồi nuốt mọi lỗi, vì như vậy có thể nuốt lỗi nghiệp vụ do delegate phát sinh. Nếu callback có phương thức removeListener hoặc sử dụng identity để unsubscribe, cần bảo toàn mapping từ callback gốc sang wrapper. Không gọi callback thêm lần nào chỉ để retry logging.

### 7.3. Executor và tác vụ chạy nền

Nếu target chỉ gọi execute/submit để đưa một task vào hàng đợi, lời gọi đó có thể thành công dù task sau đó thất bại. Nếu logging được thực thi trong Runnable wrapper, helper bảo vệ logging tại **task execution boundary**:

~~~java
// Đoạn minh họa; delegate và sink đã được khai báo.
Runnable wrapped = () -> {
    safeLog(sink, "task started");
    try {
        delegate.run();
        safeLog(sink, "task finished");
    } catch (RuntimeException businessError) {
        safeLog(sink, "task failed");
        throw businessError;
    }
};
~~~

Nếu một task chưa bao giờ chạy vì bị hủy trước lúc thực thi, wrapper không quan sát được thời điểm hủy. Nếu cần metrics đầy đủ, gắn thêm hook ở future/task lifecycle. Tránh gọi logging đồng bộ tốn thời gian trên thread quan trọng; nếu logger hết dung lượng queue, quy định rõ drop, backpressure hoặc blocking trong giới hạn.

### 7.4. Reactive stream hoặc nguồn sự kiện liên tục

Với mô hình phát nhiều sự kiện, logging có thể thực hiện tại các tín hiệu subscribe, onNext, onError, onComplete và cancel. Observer log ném exception trong xử lý một phần tử có thể làm pipeline bị dừng hoặc tạo error signal mới tùy mô hình. Phải dùng hook “do/observe” đúng theo hợp đồng của stream và cô lập lỗi logger trong callback quan sát.

Không tự subscribe lần nữa chỉ để ghi log, vì có thể kích hoạt thêm công việc hoặc nhân đôi side effect. Với stream có nhiều subscriber, xác định rõ một dòng log đại diện cho **mỗi sự kiện**, **mỗi subscription** hay **toàn bộ nguồn**, tránh nhân đôi hoặc sai metric.

## 8. Xử lý chính exception của nghiệp vụ mà không làm méo thông tin lỗi

Nếu advice bắt được lỗi nghiệp vụ, mục tiêu logging thường là thêm ngữ cảnh chẩn đoán mà vẫn cho caller nhận lại lỗi gốc:

~~~java
// Minh họa: chỉ log; không thay businessError.
try {
    return target.call();
} catch (RuntimeException businessError) {
    safeLog(sink, "business failed");
    throw businessError;
}
~~~

Một số quyết định cần cân nhắc:

**Giữ object identity:** Ném lại chính businessError cho phép code caller đang kiểm tra ngoại lệ tham chiếu gốc hoặc đặc tính bổ sung tiếp tục hoạt động. Tạo một ngoại lệ mới chỉ vì logger thất bại sẽ thay contract.

**Giữ cause chain:** Nếu cần phân loại lỗi và log cause, hãy đọc có kiểm soát. Lỗi logging không nên trở thành cause của lỗi nghiệp vụ. Không thay stack trace để “đẹp log”.

**Suppressed exception:** Có thể bổ sung lỗi logging bằng addSuppressed trên businessError khi chính sách cho phép; tuy nhiên hành động này **thay đổi dữ liệu ngoại lệ** và có thể tăng bộ nhớ hay lộ chi tiết vận hành. Với log best effort yêu cầu không đổi kết quả có thể quan sát, không tự động đính loggingFailure vào businessError.

**Checked exception:** Java cho phép phương thức ném checked exception theo khai báo. Around advice phải có chữ ký/giao diện phù hợp và truyền lại chính exception đó. Đừng bọc mọi Exception thành RuntimeException chỉ để viết interceptor gọn hơn.

**Error nghiêm trọng:** Một helper chỉ catch RuntimeException không che VirtualMachineError, ThreadDeath hoặc lỗi JVM khác. Việc cố “bảo đảm không có lỗi ghi log nào thoát ra” bằng catch Throwable không tự làm chương trình an toàn; các lỗi fatal có thể là dấu hiệu runtime không còn khả năng phục hồi. Phạm vi chống lỗi cần được công bố chính xác.

**Độ nhạy dữ liệu:** Một dòng stack trace hoặc toString có thể chứa thông tin cá nhân, mã thông báo hay bí mật. Aspect nên log theo trường đã lọc, tuân theo chính sách ẩn danh/che dữ liệu. Log hỏng không phải lý do dùng fallback kém bảo mật hơn.

## 9. Khi bản thân hệ thống báo lỗi logger lại bị lỗi

Một anti-pattern nguy hiểm là gọi lại chính logger từ catch loggingFailure:

~~~java
// Anti-pattern
try {
    sink.append("business event");
} catch (RuntimeException loggingFailure) {
    sink.append("logging failed: " + loggingFailure);
}
~~~

Nếu sink vẫn hỏng, exception thứ hai lại thoát và có thể che kết quả nghiệp vụ. Nếu logger nội bộ cũng ghi thông qua chính aspect đang áp dụng, vòng gọi lồng nhau có thể dẫn đến đệ quy vô hạn.

Chiến lược thay thế:

- Ghi nhận trạng thái “logging degraded” bằng một thành phần độc lập, giới hạn tần suất và tránh route qua cùng sink lỗi.
- Có thể dùng counter trong bộ nhớ cho số lần ghi thất bại, kết hợp health check hoặc cảnh báo nội bộ; xác định rõ xử lý khi bộ đếm/health reporter cũng gặp lỗi.
- Nếu cần fallback, dùng **kênh khác thật sự** và đảm bảo đường fallback không kích hoạt chính aspect gây sự cố.
- Dùng cơ chế chống reentrancy có vòng đời rõ, ví dụ trạng thái theo scope của invocation và cleanup đúng ở finally; không sử dụng một cờ global đơn nhất cho nhiều task đồng thời.
- Giới hạn dung lượng lưu sự cố, tần suất gửi, số lần retry, mức độ backpressure để lỗi logger không khuếch đại thành quá tải hệ thống.

Không nên dùng một logger hỏng để liên tục thông báo chính lỗi hỏng của nó. Mất vài bản ghi chẩn đoán ít nguy hiểm hơn tạo một failure cascade ảnh hưởng trực tiếp nghiệp vụ.

## 10. Đồng bộ, bất đồng bộ và điểm đo chính xác

AOP cho quan sát chương trình phải xác định **ranh giới** của từng sự kiện:

| Sự kiện | Có thể ghi ở đâu? | Logger hỏng có thể ảnh hưởng gì nếu không cô lập? |
| --- | --- | --- |
| Enter invocation | Before/around advice | Chặn target chưa được gọi |
| Normal return | After-returning/around | Đổi success thành throw |
| Business throw | After-throwing/around catch | Che business exception |
| Invocation finally | Finally của advice | Che cả return lẫn throw |
| Future completion | Completion observer | Làm dependent stage exceptional |
| Callback execution | Callback wrapper | Làm dispatcher/callback thất bại |
| Task execution | Runnable/Callable wrapper | Làm task lỗi hoặc thay exception |
| Stream signal | Subscriber/operator observer | Đổi tiến trình hoặc terminal signal |

**Một thao tác return thành công không tương đương một công việc async thành công.** Nếu aspect ghi “operation success” ngay khi trả future, log có thể sai ngay cả khi logger hoàn toàn khỏe mạnh. Phân biệt log “invocation returned” với “operation completed successfully”; hook completion được dùng cho trạng thái thứ hai.

Cũng cần phân biệt **lỗi business**, **lỗi callback xử lý business** và **lỗi logger**. Nếu callback nghiệp vụ throw sau khi xử lý một stage thành công, dependent stage có thể thất bại trong khi stage upstream vẫn thành công. Hãy quan sát outcome của đúng stage mà contract coi là kết quả cuối cùng.

## 11. Logging bất đồng bộ không tự động an toàn

Ghi log vào hàng đợi rồi trả nhanh thường giúp giảm chi phí trên đường xử lý nghiệp vụ, nhưng chỉ chuyển lỗi sang một tầng khác:

- Queue có thể đầy; producer bị block, bị reject hoặc log bị drop tùy chính sách.
- Worker gửi log có thể bị lỗi I/O, lỗi serialization hoặc chết.
- Khi tiến trình dừng, phần log chưa flush có thể bị mất.
- Retry quá mạnh khi sink chậm có thể chiếm CPU, bộ nhớ và băng thông.
- Mỗi event log có thể chứa dữ liệu mutable, bị thay đổi trước khi worker serialize.

Với log best effort, nên quy định một ngân sách rõ ràng: queue hữu hạn, chính sách drop/sampling hoặc phương án không chặn, số retry tối đa, cách quan sát tỉ lệ drop và shutdown flush trong giới hạn. Nếu nghiệp vụ không được phép mất audit, thiết kế queue volatile đơn thuần không đáp ứng yêu cầu durability.

Không đảm bảo “logger không ảnh hưởng business” chỉ bằng try/catch nếu sink có thể block vô thời hạn. Cần cả chính sách latency và backpressure.

## 12. Testing: xác minh cả logger hỏng và nghiệp vụ hỏng

Một bài kiểm thử tốt phải tiêm lỗi tại từng điểm logging và kiểm tra **outcome quan sát từ caller**, không chỉ kiểm tra rằng chương trình “không crash”:

| Tình huống | Kỳ vọng với log best effort |
| --- | --- |
| Logger hỏng ở before; target trả giá trị | Target vẫn chạy đúng một lần, caller nhận nguyên giá trị |
| Logger hỏng sau khi target trả | Caller vẫn nhận giá trị, không bị throw từ logger |
| Target throw; logger hỏng trong catch | Caller nhận chính lỗi target, cause/identity không bị thay |
| Target throw; logger hỏng trong finally | Không che lỗi gốc |
| Target trả null; logger hỏng | Trả đúng null |
| Future thành công; observer log throw | Stage được trả vẫn có kết quả thành công theo contract |
| Future exceptional; observer log throw | Lỗi nghiệp vụ không bị che hoặc chuyển thành success |
| Callback/stream phát nhiều tín hiệu; logger lỗi | Không thêm callback, không chuyển sai terminal signal |
| Logger bị block/queue đầy | Hành vi phù hợp time budget và overflow policy |
| Fallback reporter cũng lỗi | Không tạo recursion hoặc failure cascade |
| Logging yêu cầu audit bắt buộc không sẵn sàng | Hành vi từ chối/hoãn hoặc transaction recovery đúng chính sách đã công bố |

### 12.1. Kiểm tra giá trị và tham chiếu ngoại lệ

Trong Java có thể kiểm tra:

~~~java
// Minh họa kiểm thử với target và around được thiết lập sẵn.
RuntimeException original = new IllegalArgumentException("bad input");

try {
    around("check", () -> {
        throw original;
    }, brokenLogger);
    throw new AssertionError("Expected business failure");
} catch (RuntimeException actual) {
    if (actual != original) {
        throw new AssertionError("Logging changed business exception", actual);
    }
}
~~~

Đoạn này là **mảnh kiểm thử minh họa**, cần các biến/method tương ứng từ ứng dụng hoặc từ chương trình ở phần 5. Để test đầy đủ, cũng cần bảo đảm target được gọi đúng một lần và mọi side effect của nó không bị lặp.

### 12.2. Kiểm tra lỗi completion và logging callback

Với CompletableFuture, hoàn tất source bằng một exception đã tạo trước; quan sát dependent stage và kiểm tra exception có cause là lỗi gốc, thay vì thông điệp lỗi logger. Đừng chỉ assert advice đồng bộ trả stage thành công; phải quan sát **terminal state**.

Với callback/error channel của một API khác, cần tiêm lỗi vào từng callback và kiểm tra dispatch contract cụ thể. Với stream, kiểm tra cả onError/onComplete/cancel và việc không phát thêm tín hiệu. Trường hợp callback thực thi ngay khi đăng ký cũng cần được kiểm chứng.

### 12.3. Kiểm tra overload và khả năng quan sát sự cố logger

Dùng fake sink có thể chặn, từ chối hoặc ném lỗi theo kịch bản để đo overhead, số event được drop và trạng thái degraded. Không mặc định best effort nghĩa là mọi thất bại đều phải có thêm một dòng log mới; nếu toàn bộ pipeline ghi log đang sập, health signal hoặc counter riêng có thể thực tế hơn.

## 13. Quy trình áp dụng tại join point

Khi thêm một logging aspect vào hệ thống, có thể làm theo trình tự:

1. **Phân loại bản ghi:** debug, telemetry, operational, audit bắt buộc hay event dữ liệu nghiệp vụ? Nếu khác độ tin cậy, không gom vào chung một chính sách.
2. **Xác định join point:** invocation return/throw, completion stage, callback, task execution, subscription lifecycle. Gắn đúng nơi có tín hiệu thực.
3. **Viết contract bảo toàn outcome:** nêu rõ return value, exception, future completion, cancellation, side effects và time budget phải giữ thế nào.
4. **Đặt logging trong vùng xử lý lỗi riêng:** khoanh thao tác sink và chuẩn bị dữ liệu event. Không nuốt nhầm lỗi target.
5. **Thiết kế failure policy:** fail-open, fail-closed ở trước điểm commit, buffered/durable, fallback hoặc degraded mode; ghi rõ quyết định và giới hạn.
6. **Xem xét reentrancy, context và redaction:** tránh aspect gọi chính mình, quản lý trace context qua callback, lọc thông tin nhạy cảm.
7. **Tiêm lỗi để kiểm tra:** before, after, catch, finally, completion callback, task wrapper, sink block, queue full, audit store unavailable và khôi phục sau lỗi.
8. **Theo dõi vận hành:** tỷ lệ mất log, queue depth, thời gian ghi, logger degraded và đối soát audit nếu liên quan.

### Quy tắc lựa chọn nhanh

- **Chỉ ghi log chẩn đoán** → ưu tiên best effort, không làm đổi outcome nghiệp vụ khi sink ném lỗi thuộc phạm vi đã định.
- **Log gắn với kết quả bất đồng bộ** → bổ sung hook completion/callback/task/stream phù hợp và cô lập lỗi log tại chính hook đó.
- **Audit là điều kiện bắt buộc để thao tác được phép diễn ra** → thiết kế bước bảo đảm audit trước hành động hoặc transaction/outbox có bảo đảm; từ chối minh bạch nếu chưa đạt điều kiện.
- **Không chấp nhận mất dữ liệu nghiệp vụ** → coi record đó là phần của luồng giao dịch nghiệp vụ, không dựa vào một logger best effort.
- **Yêu cầu latency nghiêm ngặt** → ngoài catch exception còn phải giới hạn thời gian, kích thước queue và backpressure.

## 14. Câu hỏi tự đánh giá

**1. Một phương thức đã ghi dữ liệu thành công, nhưng lệnh ghi log ở finally ném exception. Caller sẽ thấy gì nếu không cô lập lỗi logger?**

Caller có thể thấy exception từ logger, trong khi side effect nghiệp vụ đã xảy ra. Điều này dễ khiến caller retry nhầm và sinh thay đổi trùng lặp. Với best effort, bọc riêng logging để return hoặc business exception được giữ nguyên.

**2. Một target ném IllegalArgumentException, advice cố log lỗi nhưng logger cũng ném IllegalStateException. Ngoại lệ nào phải đến caller trong chính sách best effort?**

IllegalArgumentException từ target. Advice phải catch lỗi logger ở vùng riêng rồi ném lại **chính business exception**, không thay bằng lỗi logger hoặc fallback.

**3. Future hoàn tất thành công nhưng whenComplete observer ném lỗi logging. Hệ quả có thể là gì?**

Stage phụ thuộc do whenComplete tạo ra có thể hoàn tất exceptional dù source thành công. Cần cô lập lỗi logger trong callback observer, đồng thời hiểu rằng attach observer có thể thay đổi identity và một số đặc tính của stage được trả.

**4. Một thao tác chuyển tiền yêu cầu lưu audit bắt buộc. Có nên cứ bỏ qua khi audit store hỏng không?**

Không nếu hợp đồng bắt buộc lưu audit để thao tác hợp lệ. Cần cơ chế bền vững/transactional hoặc điều kiện từ chối trước hành động với thông báo rõ ràng; không để một log sau side effect ném lỗi tùy tiện vì như vậy có thể khiến caller hiểu nhầm rằng chuyển tiền chưa diễn ra.

**5. Bắt RuntimeException quanh cả target và logger rồi trả fallback có giúp cô lập lỗi logging không?**

Không. Catch đó nuốt cả lỗi target và đổi kết quả nghiệp vụ. Ranh giới bắt lỗi logger phải tách biệt với ranh giới bắt lỗi nghiệp vụ.

**6. Đã catch exception logger, vì sao vẫn có thể gây sự cố?**

Logger có thể block, queue đầy, phát sinh recursion ở fallback, dùng quá nhiều bộ nhớ hoặc làm rò dữ liệu. Chỉ bắt exception chưa đủ; cần chính sách về thời gian, dung lượng, retry, fallback, context và dữ liệu.

## Kết luận kiến thức

Logging aspect tốt giúp quan sát rõ hệ thống mà **không vô tình quyết định kết quả nghiệp vụ**. Với log best effort, hãy cô lập những lỗi ghi log có thể xử lý tại chính nơi phát sinh, kể cả before, after, catch, finally và completion/callback; giữ nguyên giá trị trả về, ngoại lệ gốc, trạng thái tương lai và số lần thực hiện tác dụng phụ. Với audit bắt buộc, hãy diễn đạt rõ quy tắc từ chối hoặc lưu bền vững và triển khai ở ranh giới nghiệp vụ phù hợp. Nguyên tắc xuyên suốt là: **mỗi loại bản ghi có một chính sách đáng tin cậy, và lỗi của cơ chế quan sát chỉ được phép ảnh hưởng nghiệp vụ theo chính sách đó**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-observability-008">Tránh đo trùng khi có nhiều lớp aspect hoặc công cụ quan sát</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh đo trùng khi có nhiều lớp Aspect hoặc công cụ quan sát

## 1. Một thao tác có thể bị đo nhiều lần

Trong **Aspect-Oriented Programming (AOP)**, một aspect có thể ghi log, tăng counter, đo thời gian hoặc tạo trace span tại các **join point** mà **pointcut** lựa chọn. Khi chương trình có nhiều aspect, proxy, decorator, interceptor và công cụ instrument tự động, một phương thức nghiệp vụ có thể bị quan sát bởi nhiều lớp.

Ví dụ:

~~~text
Caller
  -> MetricsAspect
     -> LoggingAspect
        -> AutomaticInstrumentation
           -> approveOrder() // thực thi đúng một lần
~~~

Nếu cả ba lớp đều tăng cùng counter **business_operation_completed_total** khi thao tác kết thúc, một yêu cầu phê duyệt lại bị báo thành ba lượt. Ngược lại, nếu chúng lần lượt đo **business operation**, **dependency call** và **trace child span** có ý nghĩa khác nhau, ba phép đo có thể hoàn toàn hợp lệ.

**Quy tắc:** mỗi phép đo phải có một **đơn vị được đếm**, một **sự kiện/lifecycle cụ thể** và một **chủ sở hữu tín hiệu** rõ ràng. Tránh nhiều lớp cùng phát một sự kiện có cùng ngữ nghĩa cho cùng một occurrence. Không gộp hoặc xóa những sự kiện độc lập chỉ vì cùng method name hoặc trace ID.

## 2. Xác định chính xác thế nào là trùng

Một **join point** có thể được nhiều pointcut và advice chọn; nhiều proxy, decorator hoặc công cụ instrumentation có thể cùng quan sát một đường gọi. Tuy nhiên, **occurrence** là *một lần xảy ra thật* của đơn vị đã định nghĩa (request, operation, attempt, call hoặc span), còn **semantic duplicate** là việc nhiều tín hiệu cùng biểu diễn *chính occurrence ấy ở cùng cấp độ và cùng ngữ nghĩa* do nguồn đo hoặc pipeline lặp.

Hai dòng log cho một method không tự chứng minh đo trùng: đó có thể là hai lần gọi thật, cặp start/end, các attempt khác nhau hoặc span cha–con. Phải xác định **đơn vị, mốc kết thúc và nguồn sở hữu phép đo** trước khi loại bỏ tín hiệu.

## 3. Các cấp độ quan sát khác nhau

~~~text
Một HTTP request
  -> Một business operation
     -> Attempt 1 (timeout)
     -> Attempt 2 (success)
        -> Dependency call A
        -> Dependency call B
~~~

Bảng mô tả những đơn vị thường bị trộn:

| Cấp độ | Một đơn vị | Tín hiệu phù hợp | Khi nào được có nhiều? |
| --- | --- | --- | --- |
| Request | Một request giao thức | request count, latency | Có nhiều request thực |
| Business operation | Một lần yêu cầu thực hiện use case | operation outcome, end-to-end duration | Có nhiều operation độc lập |
| Attempt | Một lượt thử hoặc retry | attempt count, attempt duration | Một operation có thể retry |
| Dependency call | Một lần gọi thành phần phụ thuộc | call outcome, dependency duration | Có fan-out hoặc retry thật |
| Method invocation | Một lần gọi/thực thi method | method timer, debug trace | Có nhiều lần gọi thực |
| Trace span | Một đoạn công việc có phạm vi | span start/end và thuộc tính | Có nhiều span cha/con khác nhau |
| Audit event | Một chuyển trạng thái được xác nhận | event có ID và actor | Có nhiều chuyển trạng thái thực |

Không so sánh thẳng **operation count** với **dependency count** để kết luận sai lệch. Không xem nhiều span trong một trace là duplicate: span cha và span con có nhiệm vụ riêng. Nếu chúng cùng cố mô tả chính một outbound call ở cùng cấp độ, mới có khả năng semantic duplicate.

## 4. Dấu hiệu và tác hại

### Đếm sai số lần xử lý

Một operation thành công nhưng counter tăng hai, khiến throughput và conversion bị nhân đôi. Nếu mỗi attempt thất bại được ghi là lỗi của operation, tỷ lệ lỗi cũng bị thổi phồng dù retry sau cùng đã thành công.

### Histogram bị pha trộn phạm vi

Một timer đo method return sau 2 ms, timer khác đo completion sau 700 ms, nhưng cả hai gửi vào cùng histogram **business_operation_duration_seconds**. Thống kê p95 không còn một ý nghĩa rõ ràng.

### Trace có hai span mô tả cùng hành động

Ví dụ hai instrumenter cùng tạo span "outbound HTTP call" quanh một network request. Số span và tài nguyên lưu trữ tăng, biểu đồ trace khó đọc, có thể hiểu lầm tồn tại hai network call.

### Nhật ký nhân bản

Một event "order.approved" được ghi cả từ aspect bên ngoài và interceptor bên trong; một appender hoặc pipeline chuyển tiếp cũng có thể ghi lại hai lần. Người vận hành khó biết có hai hành động nghiệp vụ thật hay chỉ một hành động bị ghi trùng.

### Overhead tăng

Thêm proxy, callback, capture context, serialization, allocation, metric label, log I/O và exporter queue có thể tiêu tốn CPU, bộ nhớ, lưu trữ và tăng latency. Mức tăng phụ thuộc implementation; không có công thức chung "hai aspect thì chậm gấp đôi".

**Lưu ý:** một log bắt đầu và một log kết thúc của cùng operation không bị coi là trùng, nếu hai event có loại khác nhau.

## 5. Bảy nguồn thường gây trùng phép đo

### 5.1. Hai aspect cùng phát một counter

**MetricsAspect** và **ObservabilityAspect** đều bắt phương thức nghiệp vụ, mỗi aspect tăng một counter có cùng tên, nhãn và định nghĩa. Lời gọi chạy một lần nhưng counter tăng hai.

**Giải pháp:** phân định chủ sở hữu; aspect kia chuyển thành context enrichment hoặc đo tín hiệu khác.

### 5.2. Pointcut quá rộng hoặc chồng lấn

Một pointcut chọn tất cả public methods trong package nghiệp vụ; pointcut khác chọn các method có annotation đánh dấu operation. Phương thức thỏa cả hai bị đo nhiều lần. Cũng có thể đăng ký cùng advice hai lần qua cấu hình.

**Giải pháp:** chọn một pointcut chuẩn ở boundary, đặt exclusion hoặc tách phạm vi theo semantic scope; kiểm tra chính tập join point được instrument lúc runtime.

### 5.3. Nhiều proxy/decorator bao quanh cùng đối tượng

Service có thể bị bọc nhiều tầng. Mỗi tầng thấy cùng lời gọi, nhưng không phải tầng nào cũng là ranh giới business operation. Cơ chế proxy có thể bỏ qua self-invocation, trong khi kiểu weaving khác có thể quan sát những join point khác; cần kiểm tra đường gọi thực tế.

**Giải pháp:** chọn tầng sở hữu operation metric, đặt tên phép đo khác cho các lớp hợp lệ còn lại.

### 5.4. Instrumentation thủ công và tự động

Code tự tạo span cho một dependency call, trong khi công cụ quan sát tự động cũng tạo span cho chính call ấy. Nếu cùng ngữ nghĩa, chúng có thể là semantic duplicate. Nếu span thủ công đại diện cả repository operation, span tự động đại diện network request bên trong thì cả hai hợp lệ: đặt tên, quan hệ cha/con và phạm vi riêng.

**Giải pháp:** kiểm kê instrumenter, xác định tầng tạo span, vô hiệu hóa đúng một nguồn cho đúng scope bị trùng.

### 5.5. Retry làm số attempt nhiều hơn operation

RetryAdvice có thể gọi phần nghiệp vụ bên trong ba lần; một aspect trong retry thấy ba attempts, aspect ngoài retry thấy một operation. Nếu cả hai dùng metric name "operation_total", số liệu mất ý nghĩa.

**Giải pháp:** bộ đếm attempt và operation khác tên; business outcome ghi tại tầng biết kết quả cuối.

### 5.6. Async: đo ở cả return và completion

Method trả CompletionStage nhanh rồi task mới thực thi. Nếu một advice ghi "success" lúc method return, observer khác ghi "success" khi stage hoàn tất, vừa đo trùng vừa đánh đồng **trả handle** với **hoàn thành công việc**.

**Giải pháp:** sự kiện "submitted/invocation_returned" khác "operation_completed". Chỉ observer có lifecycle phù hợp tăng operation completion counter.

### 5.7. Event lặp ở collector, storage hoặc dashboard

Ứng dụng có thể phát một lần nhưng message được replay; một exporter bị scrape qua hai đường; dashboard cộng dữ liệu thô với dữ liệu đã tổng hợp. Lỗi nằm sau nguồn phát, không nhất thiết ở các aspect.

**Giải pháp:** so sánh event ở producer, collector, storage và query; dùng event identity phù hợp để nhận diện delivery duplicate, xem lại aggregation và source labels.

## 6. Thiết kế hợp đồng đo lường trước khi code

Một phép đo nên trả lời bốn câu hỏi:

1. **Tôi đang đếm cái gì?** Một request, một business operation, một attempt, một call hay một span?
2. **Mốc nào xác nhận điều được đo?** Khi vào lời gọi, sau method return, khi callback chạy, sau commit hay sau task thực sự dừng?
3. **Ai được quyền ghi?** Aspect, interceptor, exporter hay một công cụ tự động?
4. **Tín hiệu nào thể hiện kết quả?** success, rejected, failed, cancelled hoặc trạng thái khác theo contract?

Có thể lập **ownership matrix**:

| Tín hiệu | Nguồn phát chuẩn | Đơn vị | Thời điểm |
| --- | --- | --- | --- |
| business_operation_completed_total | Advice tại boundary use case | Operation | Sau outcome cuối |
| business_operation_duration | Cùng boundary hoặc observer completion | Operation | Mốc kết thúc theo contract |
| business_operation_finished log | Chủ sở hữu business outcome | Operation | Một event kết thúc |
| attempt_total | Retry controller / attempt advice | Attempt | Sau mỗi attempt |
| dependency_call_duration | Interceptor ở dependency boundary | Dependency call | Sau call |
| request_total | Thành phần quan sát giao thức | Request | Sau request |
| diagnostic child span | Instrumenter sở hữu bước con | Child work scope | Theo span lifecycle |

Hai công cụ vẫn có thể hoạt động cùng nhau, miễn không phát lại cùng một tín hiệu ở cùng cấp độ. Nếu cần đo cả HTTP latency và business latency, dùng tên và nhãn khác nhau; không nhập chung histogram.

## 7. Ví dụ Java 17 chạy được: hai advice đếm trùng

Ví dụ dùng JDK dynamic proxy để mô phỏng hai lớp advice cùng chặn một interface method. Cấu hình đầu ghi hai business metrics cho một lời gọi; cấu hình sau chỉ một lớp sở hữu counter và lớp kia phụ trách trace riêng.

Tạo file **DuplicateObservationDemo.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class DuplicateObservationDemo {
    interface ApprovalService {
        String approve(String orderId);
    }

    static final class RealApprovalService implements ApprovalService {
        @Override
        public String approve(String orderId) {
            return "APPROVED";
        }
    }

    static final class Counter {
        int completed;

        void record(String source) {
            completed++;
            System.out.println("COUNT source=" + source
                    + " operation=order.approve");
        }
    }

    static Object proceed(Object target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapped) {
            throw wrapped.getCause();
        }
    }

    static ApprovalService withCounter(
            ApprovalService next, Counter counter, String owner) {
        return (ApprovalService) Proxy.newProxyInstance(
                ApprovalService.class.getClassLoader(),
                new Class<?>[] { ApprovalService.class },
                (proxy, method, args) -> {
                    Object value = proceed(next, method, args);
                    if (method.getName().equals("approve")) {
                        counter.record(owner);
                    }
                    return value;
                });
    }

    static ApprovalService withTrace(ApprovalService next) {
        return (ApprovalService) Proxy.newProxyInstance(
                ApprovalService.class.getClassLoader(),
                new Class<?>[] { ApprovalService.class },
                (proxy, method, args) -> {
                    boolean observe = method.getName().equals("approve");
                    if (observe) {
                        System.out.println("TRACE operation=order.approve start");
                    }
                    try {
                        return proceed(next, method, args);
                    } finally {
                        if (observe) {
                            System.out.println("TRACE operation=order.approve end");
                        }
                    }
                });
    }

    public static void main(String[] args) {
        ApprovalService real = new RealApprovalService();

        Counter duplicated = new Counter();
        ApprovalService wrong = withCounter(
                withCounter(real, duplicated, "inner"),
                duplicated, "outer");
        System.out.println("result=" + wrong.approve("A-101"));
        System.out.println("duplicate_count=" + duplicated.completed);

        Counter correct = new Counter();
        ApprovalService fixed = withCounter(
                withTrace(real), correct, "business-boundary");
        System.out.println("result=" + fixed.approve("A-102"));
        System.out.println("correct_count=" + correct.completed);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac DuplicateObservationDemo.java
java DuplicateObservationDemo
~~~

Stdout chính xác:

~~~text
COUNT source=inner operation=order.approve
COUNT source=outer operation=order.approve
result=APPROVED
duplicate_count=2
TRACE operation=order.approve start
TRACE operation=order.approve end
COUNT source=business-boundary operation=order.approve
result=APPROVED
correct_count=1
~~~

**Phân tích:**

- Cả hai lần thử đều gọi business method đúng một lần. Chỉ cấu hình sai mới đếm hai business completions.
- Ở cấu hình đúng, việc in hai dòng TRACE đại diện start/end của một phạm vi, không phải hai business operations. Hệ thống tracing thật tạo một span có context, start/end và quan hệ parent phù hợp.
- Lớp **withCounter** chỉ ghi thành công khi method trả bình thường. Ứng dụng thật phải quy định cả failed/rejected/cancelled theo outcome contract; không đếm là success chỉ vì trả về một đối tượng lỗi.
- **Counter** dùng int cho demo tuần tự; bộ đếm thực tế cần an toàn khi chạy đồng thời, cùng schema và cardinality phù hợp.
- Hàm **proceed** gỡ InvocationTargetException do reflection bọc để giữ nguyên kiểu lỗi gốc.
- Ví dụ dùng proxy để mô tả nguyên lý AOP chung; không giả định mọi cơ chế AOP chặn được cùng tập join point.

**Bài học từ ví dụ:** không cần bỏ hết lớp quan sát. Cần để mỗi lớp cung cấp tín hiệu có ý nghĩa riêng và một nguồn duy nhất đếm completion của operation.

## 8. Thứ tự aspect, retry và thời gian đo

Cùng một lời gọi có hai cách bọc:

~~~text
A: OperationTimer -> RetryAdvice -> BusinessCall
B: RetryAdvice -> AttemptTimer -> BusinessCall
~~~

Trong **A**, timer ngoài đo cả operation, bao gồm các attempt và khoảng nghỉ giữa các lần nếu retry thực hiện việc chờ ở trong. Trong **B**, timer trong được gọi mỗi attempt, tạo N mẫu khi có N attempt. Đó là dữ liệu hợp lệ nếu B ghi **attempt_duration**, không phải **operation_duration**.

~~~text
OperationTimer
  -> RetryAdvice
      -> AttemptTimer
          -> BusinessCall
    1 operation sample
    N attempt samples
~~~

Một operation thành công sau ba attempts có thể có:

- **operation_total{outcome=success}: +1**
- **attempt_total{outcome=failed}: +2**
- **attempt_total{outcome=success}: +1**

Đây không phải đo trùng; nếu dùng tên operation failure cho hai attempt thất bại, đó là sai đơn vị. Thứ tự các aspect cũng tác động đến việc advice thấy lỗi attempt hay kết quả cuối. Không chỉ đọc cấu hình trên giấy; kiểm tra thứ tự bọc và đường gọi thực tế.

Cache cũng ảnh hưởng ngữ nghĩa. Timer ngoài cache thấy cả cache hit và miss; timer trong cache chỉ thấy lần thật sự gọi target. Hai thời gian này không nên nhập chung nếu chúng trả lời hai câu hỏi khác nhau.

## 9. Pointcut chồng lấn: sửa tại nguồn

Ví dụ ba pointcut:

~~~text
P1: mọi method trong nhóm use case
P2: mọi method có annotation chỉ operation
P3: mọi public method thuộc package nghiệp vụ
~~~

Nếu cùng method khớp cả P1, P2 và P3, có thể có ba advice quanh cùng join point. Những hành động nên thực hiện:

1. Xác định một **pointcut chuẩn** cho business outcome hoặc dùng annotation/contract quy ước rõ.
2. Thu hẹp P3 để tránh các method đã có owner; đặt exclusion theo chức năng thực tế nếu công cụ hỗ trợ.
3. Đặt tên metric của P1/P2 riêng nếu chúng cố ý đo **hai cấp độ khác nhau**.
4. Loại registration trùng của cùng interceptor/aspect do module cấu hình.
5. Kiểm tra thực nghiệm tập join point: method qua proxy có thể được chặn khác self-invocation; weaving ở call/execution join point có thể khác proxy.
6. Thêm kiểm thử bật **tất cả** aspect/công cụ quan sát, vì test từng aspect riêng không phát hiện trùng khi ghép.

Pointcut tốt biểu diễn ngữ nghĩa và phạm vi. Không nên chọn mọi getter, helper hoặc mọi method public chỉ vì dễ cấu hình, nhất là khi dự định dùng dữ liệu đó để đếm business operations.

## 10. Khi nào nên sử dụng guard chống lặp?

Guard hữu ích cho **reentrancy**: code ghi log lại gọi một method được pointcut chặn, khiến advice tự kích hoạt mãi. Một guard cục bộ cùng thread có thể hỗ trợ:

~~~text
advice:
    if alreadyInsideThisInstrumentationOnCurrentThread:
        return proceed()
    markEntered()
    try:
        return observeAndProceed()
    finally:
        clearMarker()
~~~

Đây là giả mã; không phải Java độc lập. Hạn chế:

- Nếu hai instrumenter độc lập, guard riêng của mỗi bên **không ngăn chúng cùng đếm**.
- Một request có hai nested business operations hợp lệ có thể bị guard quá rộng xóa mất phép đo của operation bên trong.
- Boolean toàn cục gây lỗi giữa các request đồng thời.
- ThreadLocal không tự truyền sang executor và phải được cleanup trên chính thread dùng nó. Không giữ marker từ invocation ngắn cho đến callback async nếu scope không hỗ trợ.
- Khi task được retry hoặc chạy lại, cần giữ ngữ nghĩa operation/attempt rõ ràng; không xem mọi lần gọi lại là duplicate.

**Ưu tiên thiết kế một owner trước khi thêm runtime guard.** Guard chống recursion và cơ chế dedup dựa trên event identity xử lý hai loại lỗi khác nhau.

## 11. Identity: trace, operation, attempt, event

Những định danh có vai trò khác nhau:

| Định danh | Đại diện | Không nên dùng để |
| --- | --- | --- |
| trace_id | Một trace bao nhiều span/operation | Dedup tất cả sự kiện trong cùng trace |
| operation_id | Một occurrence của use case | Xóa attempt hợp lệ bên trong |
| attempt_id | Một lượt thử cụ thể | Coi mọi retry là một attempt |
| event_id | Một event logic được phát ra | Coi mọi event chung operation là trùng |
| message_id | Một thông điệp logic | Xóa dấu vết các lần delivery/processing hợp lệ |

Ví dụ:

~~~text
trace = T5
  operation = O8, order.approve
    attempt A1 -> timeout
    attempt A2 -> success
  operation outcome -> success
~~~

Hai attempt hợp lệ, một business outcome. Nếu event completion được vận chuyển hai lần với cùng **event_id**, pipeline có thể loại duplicate delivery theo quy tắc phù hợp; không xóa event attempt chỉ vì có cùng trace hoặc operation ID.

Nếu dùng cache ID đã gặp để dedup, cần định nghĩa TTL, dung lượng, chia sẻ state giữa instance, restart và các trường hợp eviction. Sự trùng chuỗi log hay timestamp gần nhau chưa đủ để xác minh cùng occurrence. Không đặt ID theo request/user/trace thành label metric phổ biến vì cardinality có thể tăng mạnh.

## 12. Log: phân biệt sự kiện với bản ghi lặp

Những dòng sau không phải đo trùng:

~~~text
operation=order.approve event=started  operation_id=O8
operation=order.approve event=finished operation_id=O8 outcome=success
~~~

Vì chúng biểu diễn hai mốc khác nhau. Ngược lại:

~~~text
operation=order.approve event=finished operation_id=O8 outcome=success source=A
operation=order.approve event=finished operation_id=O8 outcome=success source=B
~~~

có thể là hai instrumentation sources cùng phát một business completion. Cũng có thể một collector ghi cùng event hai lần; cần kiểm tra event_id và nguồn thực tế.

Để điều tra, thử log trong môi trường kiểm thử:

- instrumentation source và version của aspect;
- semantic operation name và occurrence ID;
- event type (started, finished, attempt_failed...);
- trace/span ID nếu phù hợp;
- nơi log được gửi/thu thập.

Không nên log tràn lan toàn bộ args, token, session secret, thông tin thanh toán hoặc nội dung người dùng để tìm nguyên nhân đếm trùng. Structured logging với schema ổn định, chính sách redaction và encoding giúp an toàn và dễ đối chiếu hơn.

## 13. Metrics: double count tại nguồn và tại truy vấn

Giả sử một metric counter được tăng hai lần ở ứng dụng, hai mẫu đã sai từ producer. Nhưng có trường hợp ứng dụng chỉ phát một lần mà số liệu dashboard vẫn gấp đôi:

- Exporter bị scrape qua hai target trong cùng phép cộng.
- Một pipeline đưa cùng series vào hai kho rồi truy vấn hợp nhất không loại duplicate.
- Recording rule cộng dữ liệu đã tổng hợp cùng dữ liệu thô.
- Cấu hình federation/remote forwarding chuyển cùng measurement theo hai nhánh.
- Nhãn source bị xử lý khiến nhiều phiên bản series nhập chung sai.

Để định vị:

1. Kiểm tra số event/counter increment ngay tại unit test/in-memory collector.
2. Kiểm tra dữ liệu exporter phát ra.
3. Kiểm tra số bản ghi/series được collector nhận.
4. Kiểm tra storage và query aggregation.
5. Thay đổi **tầng gây trùng**; không sửa bừa pointcut nếu producer đã đúng.

Với histogram, một operation nên đóng góp một mẫu theo định nghĩa duration của nó. Nếu có sampling, so sánh count của histogram với counter chưa sampling sẽ không công bằng. Chênh lệch giữa thời điểm flush, trạng thái pending và cửa sổ query cũng tạo sai khác không phải duplicate.

## 14. Trace spans: span cha/con khác span trùng

Ví dụ hợp lệ:

~~~text
span business.operation (150 ms)
  span repository.load (60 ms)
    span database.query (40 ms)
~~~

Ba span phản ánh ba phạm vi lồng nhau. Không cộng 150 + 60 + 40 để kết luận request mất 250 ms: span cha đã bao một phần thời gian con.

Ví dụ đáng nghi:

~~~text
span http.client.request (42 ms) [manual instrumenter]
span http.client.request (40 ms) [automatic instrumenter]
       đúng một HTTP call
~~~

Nếu hai span này cùng đại diện toàn bộ một HTTP request ở cùng cấp, nguồn có thể trùng. Cần kiểm tra instrumenter, operation name, span kind, peer, parent, call site và thời điểm. Nếu manual span thật ra đo công việc chuẩn bị/phân tích thêm, nên đặt tên khác như **dependency.enrichment** hoặc **repository.load** và ghi quan hệ phù hợp.

Không lọc bằng span name đơn thuần: hai HTTP call khác nhau có thể cùng span name; một trace có thể lặp lệnh SQL hợp lệ.

## 15. Bất đồng bộ: khác biệt invocation và completion

Một method có thể trả về:

~~~java
CompletionStage<Receipt> submitApprovalAsync(Command command);
~~~

Đây chỉ là khai báo API minh họa. Khi gọi:

~~~text
enter advice
    proceed() -> trả stage
leave advice -> invocation kết thúc
                         task xử lý...
                         stage completed
~~~

Nếu **after-returning advice** ghi business success, stage có thể chưa chạy hoặc cuối cùng thất bại. Phân biệt:

- **invocation_returned:** method trả handle, chưa biết kết quả cuối.
- **operation_submitted:** một tác vụ được chấp nhận submit nếu contract thực sự bảo đảm điều đó.
- **operation_completed:** tín hiệu kết thúc hợp lệ theo API, có outcome cuối.
- **task_released_resources:** task đã cleanup khi thực sự dừng; không đồng nhất với timeout trên handle.

Với CompletionStage, một observer có thể đăng ký **whenComplete**, nhưng mỗi observer ghi cùng counter sẽ tạo duplicate. Cần một chủ sở hữu outcome; observer khác có thể thêm trace context hoặc tín hiệu khác. Callback có thể chạy trên thread khác hoặc ngay khi stage đã hoàn tất; mã callback phải nhẹ, không giả định ThreadLocal caller còn hợp lệ. Nếu callback ném lỗi, dependent stage có thể thay đổi trạng thái; instrumentation cần chính sách bảo toàn kết quả nghiệp vụ.

Với Future thường, không nên gọi **get()** trong advice chỉ để đo, vì sẽ chặn lời gọi và có thể gây deadlock. Với Publisher/lazy stream, creation khác subscription/completion; không tự subscribe chỉ để ghi log. Với một workflow có nhiều child tasks, completion của một nhánh chưa phải completion của operation toàn bộ.

Timeout/cancellation cũng phải được mô tả đúng: handle có thể timeout trước khi task nền dừng. Một event "finished" phải có contract minh bạch; không gắn chuyện đóng tài nguyên nền vào một observer bên ngoài nếu task còn sử dụng chúng.

## 16. Retry và phân phối: tại sao exactly-once không tự xuất hiện?

Trong hệ thống phân tán, một message có thể được giao lại, request được client retry, task được executor resubmit hoặc collector gửi lại telemetry. Đó có thể là **lần xử lý mới thật**, **lần truyền event cũ** hoặc **chỉ bản sao khi truy vấn**.

Cách nhìn đúng:

1. **Business idempotency:** một lệnh có được phép tạo side effect nhiều lần hay không? Đây là trách nhiệm thiết kế nghiệp vụ, không phải của aspect logging.
2. **Measurement ownership:** trong mỗi lần xử lý, ai đếm operation và ai đếm attempt?
3. **Event delivery:** nếu một event được gửi lặp, có event ID ổn định và cách lưu dấu thích hợp không?
4. **Query correctness:** có cộng cùng dòng telemetry hai lần không?

AOP không tự cung cấp giao dịch, durable event hay bảo đảm thực thi **exactly once** xuyên service. Nếu cần dedup bền vững qua nhiều instance, phải xác định storage, TTL, tính nhất quán và khả năng retry của cơ chế lưu dấu. Không thể kết luận “một business outcome” chỉ từ việc nhìn log bằng mắt.

## 17. Bảo vệ hành vi của chương trình khi instrument

Một around advice lý tưởng cho quan sát cần:

- Chỉ gọi **proceed() đúng một lần**, trừ khi retry là chủ đích được đặc tả riêng.
- Trả lại result gốc hoặc propagate đúng exception; không nuốt lỗi, không chuyển failed thành success.
- Ghi metric theo **outcome đã định nghĩa**, không máy móc coi mọi method return là nghiệp vụ thành công.
- Không mở resource/lock của lời gọi ngắn rồi giữ cho công việc bất đồng bộ kéo dài.
- Không dùng logger/collector có thể block vô hạn trong đường request nếu mục tiêu vận hành không cho phép.
- Có phương án cho lỗi bên observer, tránh logging/serialization exception che lỗi nghiệp vụ.
- Dùng **System.nanoTime()** cho chênh lệch duration trong cùng tiến trình; không cộng các duration lồng nhau như thể độc lập.
- Chỉ capture context tối thiểu, có chính sách bảo mật và scope cleanup rõ.

Một guard chống recursion đôi khi cần thiết, nhưng không thay thế ownership matrix và không nên gây mất signal từ nested operation hợp lệ.

## 18. Kiểm thử tránh đo trùng

Dùng bộ ghi event/metric trong bộ nhớ hoặc sink thử nghiệm để kiểm tra **số occurrence và semantic scope**, không kiểm thử các chuỗi log ngẫu nhiên hoặc timing chính xác từng nanosecond.

| Kịch bản | Kỳ vọng |
| --- | --- |
| Một operation thành công | Một success outcome và một duration cấp operation |
| Hai aspect chồng pointcut | Vẫn đúng một owner business completion |
| Một method helper được gọi nhiều lần | Không phát thêm business operation outcome |
| Operation thất bại | Một failed outcome, không cộng success |
| Retry hai lỗi rồi thành công | Một operation success; ba attempts nếu instrument attempt |
| Nested use case độc lập | Mỗi operation hợp lệ được đo riêng |
| Hai nguồn tracing chồng lấn | Không có semantic duplicate span cho cùng call |
| Async stage trả trước khi xong | Return không được đếm như final completion |
| Timeout/cancel | Mốc handle và task lifecycle được tách đúng |
| Thread pool reuse | Context/guard không rò sang task sau |
| Event gửi lặp ở collector | Đếm đúng nếu pipeline có chính sách dedup |
| Logger/observer ném exception | Kết quả nghiệp vụ tuân chính sách bảo toàn đã định |
| Hai instrumenter được bật đồng thời | Số signal đúng tại producer và backend |

Một phép kiểm tra bất biến trong điều kiện **N operation đồng bộ đã kết thúc, không retry, không sampling** có thể là:

~~~text
operation_completed_total delta = N
operation_duration_sample_count delta = N
success + rejected + failed + cancelled delta = N
~~~

Điều này chỉ đúng nếu các outcome được định nghĩa loại trừ nhau và bao phủ đầy đủ. Với async pending, dropped telemetry, sampling hoặc các cửa sổ thời gian khác nhau, cần điều chỉnh cách đối chiếu.

## 19. Quy trình truy tìm số liệu bị nhân đôi

**Bước 1: xác nhận đơn vị.** Giá trị kỳ vọng là số request, operation, attempt hay dependency call? Chọn đúng nguồn xác thực occurrence.

**Bước 2: tìm điểm nhân đôi sớm nhất.** Dùng một lời gọi có ID cố định trong môi trường kiểm thử, đếm event ngay sau advice, tiếp đó qua exporter/collector/storage và dashboard. Chỗ đầu tiên biến 1 thành 2 là nơi cần sửa.

**Bước 3: vẽ call chain.** Xác định thứ tự aspect và nơi từng metric được tăng; bao gồm registration của tool tự động và manual instrumentation.

**Bước 4: đọc pointcut và scope.** Kiểm tra match chồng lấn, proxy lồng, annotation, advice order, self-invocation, recursion và các đường đi khác nhau.

**Bước 5: chọn một owner.** Giữ business counter ở ranh giới có đủ contract outcome; chuyển tín hiệu bên trong sang attempt/dependency hoặc trace riêng nếu có ý nghĩa.

**Bước 6: kiểm thử các đường đặc biệt.** Success, failed, rejected, retry, nested operation, asynchronous completion, timeout/cancel và lỗi của observer.

**Bước 7: giám sát sau sửa.** So đối chiếu request count, business operation count, attempts và latency histogram trong cùng cửa sổ với nhãn và sampling policy tương thích.

## 20. Những sai lầm phổ biến

**“Hai span cùng tên là trùng.”** Không đủ: cần identity occurrence, instrumenter và scope. Nhiều call khác nhau có thể dùng cùng span name.

**“Mọi metric count phải bằng nhau.”** Không: một request có nhiều business operations, một operation có nhiều attempts, một attempt có nhiều dependency calls.

**“Lọc ở dashboard là đủ.”** Có thể che duplicate trong query nhưng không sửa counter đã tăng sai, log đã ghi đôi hoặc observer đã thay đổi behavior.

**“Dùng một boolean toàn cục để chống trùng.”** Làm mất phép đo của request chạy song song. ThreadLocal cũng có giới hạn qua async và nested operation.

**“Tắt tất cả log bên trong operation.”** Có thể làm mất dependency failure hay business milestone hữu ích. Cần phân biệt event có ý nghĩa khác nhau với event outcome ghi trùng.

**“Method return tức operation thành công.”** Sai với Result.Failure, Future, CompletionStage, task fire-and-forget và transaction có commit trễ.

**“Thấy số liệu x2 thì lỗi ở aspect.”** Cũng có thể là collector retry, exporter scrape trùng, recording rules hoặc query aggregation.

**“Một method bị intercept hai lần nghĩa là business logic chạy hai lần.”** Interception có thể nhiều lớp trong khi proceed chain chỉ vào target một lần. Ngược lại, lỗi advice gọi proceed nhiều lần mới thật sự có nguy cơ lặp side effect.

## 21. Checklist thực hành

- [ ] Mỗi signal có tên, đơn vị đo, điểm kết thúc và owner rõ.
- [ ] Tên metric khác nhau khi phạm vi operation, attempt, dependency hoặc invocation khác.
- [ ] Pointcut đủ hẹp, kiểm tra overlap và đăng ký lặp.
- [ ] Mọi nguồn manual và automatic instrumentation đã được kiểm kê.
- [ ] Không có hai nguồn cùng phát final outcome của một operation occurrence.
- [ ] Trace span cha/con hợp lệ được giữ; semantic duplicate cùng scope được loại.
- [ ] Counter và histogram dùng labels cardinality thấp và cùng contract đo.
- [ ] Async return, task completion, cancellation và timeout được phân biệt.
- [ ] Retry không bị ghi thành nhiều business operations nếu đó chỉ là các attempts.
- [ ] Context/guard cleanup đúng thread và đúng lifecycle.
- [ ] Truy vấn và collector đã được kiểm tra nếu producer phát đúng mà dashboard sai.
- [ ] Unit/integration tests bật nhiều aspect cùng lúc và khẳng định số event chính xác.
- [ ] Ghi log không làm lộ dữ liệu nhạy cảm và instrumentation không đổi hành vi nghiệp vụ.

**Kết luận kiến thức:** giải pháp bền vững là **mỗi tín hiệu có chủ sở hữu và ngữ nghĩa rõ ràng**, thay vì cố ép toàn hệ thống chỉ còn một aspect, một log hay một span. Một business operation được đếm một lần ở cấp operation; các attempt, dependency call và span con thực sự khác biệt vẫn được quan sát đầy đủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="program-observability-009">Kiểm tra chi phí quan sát trong điều kiện tải thực tế</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra chi phí quan sát trong điều kiện tải thực tế với Aspect-Oriented Programming (AOP)

## 1. Vấn đề: chính hoạt động quan sát cũng tiêu tốn tài nguyên

Aspect-Oriented Programming (AOP) giúp gắn những hành vi cắt ngang như metrics, tracing, audit hoặc logging vào nhiều điểm thực thi mà không phải sửa từng phương thức nghiệp vụ. Nhưng mỗi advice được gọi cũng tiêu thụ **CPU, bộ nhớ, băng thông, thời gian chờ và khả năng đồng bộ hóa giữa các thread**. Nếu aspect được áp dụng tại một phương thức rất nóng, một chi phí nhỏ cho mỗi lần gọi có thể trở thành nút nghẽn lớn khi hệ thống chịu lưu lượng thực tế.

Bài toán không chỉ là “một lời gọi chậm thêm bao nhiêu nano giây”. Phải kiểm tra xem **hệ thống còn đáp ứng được số lượng request, độ trễ, tỷ lệ lỗi và giới hạn tài nguyên đã cam kết khi bật quan sát hay không**, đặc biệt ở ngưỡng tải cao, lúc downstream chậm và khi kênh thu thập telemetry trục trặc.

Ví dụ: một advice làm phát sinh thêm 5 µs trên một lần gọi có thể không đáng kể với một thao tác I/O 100 ms. Nhưng nếu một request đi qua 20 join point cùng loại, chi phí trực tiếp danh nghĩa có thể lên tới khoảng 100 µs/request, chưa tính đồng bộ hóa, cấp phát, GC và việc ghi ra ngoài. Ở 20.000 request/giây, 100 µs/request tương ứng khoảng **2 CPU-second mỗi giây** chỉ cho lượng công việc bổ sung đó (ước lượng lý tưởng, không phải kết quả đo). Đây là lý do phải kiểm thử ở phạm vi workload thật.

### Kết quả học tập

Sau bài này, người học có thể:

- Liệt kê các nguồn overhead mà proxy, weaving và advice có thể gây ra.
- Xác định phép thử baseline và biến thể có thể so sánh được.
- Đo thông lượng, độ trễ phân vị, CPU, allocation, GC, queue và error rate khi bật/tắt instrumentation.
- Phân biệt microbenchmark đo cơ chế với kiểm thử tải đánh giá tác động toàn hệ thống.
- Nhận diện nhiễu do warm-up, môi trường, giới hạn tải, sampling và sai lệch cách đo.
- Xây dựng quyết định triển khai dựa trên ngân sách chi phí quan sát và đặc tính suy giảm khi quá tải.

## 2. Xác định đúng phạm vi quan sát của AOP

Trước khi đo chi phí, cần xác định **tập join point thực sự được instrument** và loại advice đang chạy. Proxy, bytecode weaving và instrumentation tại lúc tải lớp có những khoản chi phí riêng; do đó phép so sánh chỉ đáng tin khi phạm vi can thiệp được nêu rõ.

Các cơ chế này có bề mặt chi phí khác nhau:

| Thành phần | Nguồn chi phí có thể xuất hiện | Điều cần kiểm tra |
| --- | --- | --- |
| Proxy / gọi gián tiếp | Thêm bước chuyển tiếp lời gọi, dispatch qua reflection hoặc method handle, giảm cơ hội tối ưu/inlining trong một số trường hợp | Overhead trên hot path và mức tăng khi số join point tăng |
| Weaving / bytecode instrumentation | Thêm nhánh điều kiện, lệnh ghi timestamp, thay đổi kích thước mã, ảnh hưởng tối ưu JIT | Chi phí runtime; riêng chi phí khởi động, nạp lớp và biên dịch |
| Around advice | Đo thời gian, xử lý exception, mở/đóng scope, thay đổi call stack | p50/p95/p99 và chi phí khi lỗi |
| Metrics | Cập nhật counter, histogram, nhãn, map/registry | Contention, tạo series mới, cấp phát và chi phí export |
| Tracing | Tạo span, truyền context, ghi attributes/events, kết thúc span | Số span/request, cấp phát, sampling và độ trễ |
| Logging / audit | Format chuỗi, stack trace, serialize, append queue, file/network I/O | Throughput, backpressure, chính sách đầy hàng đợi |
| Thu thập bất đồng bộ | Queue, batch, worker, kết nối tới collector | Memory/queue depth, retry, mất telemetry và chi phí nền |

**Quan sát ở đâu quyết định đo cái gì.** Advice bao quanh phương thức đồng bộ có thể đo toàn thời gian gọi. Với phương thức trả về một `CompletionStage`, `Future` hoặc publisher sự kiện, thời gian từ lúc gọi đến lúc trả về chỉ là thời gian **khởi tạo/trả đối tượng**, không phải thời gian xử lý bất đồng bộ đến kết quả cuối. Muốn đo vòng đời, instrumentation phải bám vào completion/cancellation/subscription/terminal signal tương ứng.

Cần ghi chính xác số điểm được chọn: một request đi qua API, service và repository có thể kích hoạt nhiều advice. Ba phép đo riêng không có nghĩa là ba request người dùng. Nếu tăng số join point, phải biết tăng do phạm vi đo thay đổi hay do mỗi advice đắt hơn.

## 3. Chi phí nào cần đo?

### 3.1 Độ trễ và thông lượng

- **Latency**: thời gian để một thao tác hoàn tất, đo theo điểm đầu/cuối rõ ràng. Theo dõi p50, p95, p99; có thể bổ sung p99.9 nếu đủ mẫu và yêu cầu phù hợp.
- **Throughput**: số thao tác hoàn tất trong một đơn vị thời gian, chẳng hạn request/giây. Phân biệt tốc độ yêu cầu *được gửi vào* với tốc độ *thực sự xử lý xong*.
- **Concurrency / in-flight**: số thao tác đang xử lý. Với cùng lưu lượng, nếu latency tăng, số thao tác đồng thời có thể tăng, khiến thread pool, connection pool và hàng đợi bão hòa.
- **Error rate và timeout rate**: fraction thao tác thất bại trên tập thao tác có trạng thái kết thúc, với cùng định nghĩa trạng thái ở baseline và variant.
- **Queueing delay**: thời gian chờ trước khi thực thi, có thể tăng mạnh khi tải tiệm cận khả năng phục vụ.

Đối với cùng một workload, một hệ thống có thể giữ throughput gần như không đổi nhưng p99 tăng mạnh trước khi bắt đầu xuất hiện timeout. Vì vậy chỉ so sánh request/giây là chưa đủ.

### 3.2 CPU, memory, GC và vận hành

Theo dõi ít nhất các nhóm sau nếu môi trường có khả năng đo:

- CPU utilization, CPU time và số core hiệu dụng; với máy ảo/container, kiểm tra quota/throttling để không nhầm thiếu CPU với overhead của advice.
- Tốc độ cấp phát bộ nhớ (`bytes allocated per request`), heap live set, heap sau GC, số lần và thời gian GC pause; thay đổi allocation có thể làm p99 xấu đi dù CPU trung bình chưa cao.
- Số thread, trạng thái blocked/waiting, contention trên lock/atomic, context switching; một metrics registry hoặc logger có lock nóng có thể tạo đuôi trễ dài.
- I/O và network tới hệ thống thu thập, số event/span/log mỗi giây, kích thước batch, độ sâu queue, số lần retry và số bản ghi telemetry bị loại.
- Startup time, thời gian class loading/JIT và memory footprint trong trường hợp instrumentation ảnh hưởng giai đoạn khởi động.

**Không suy diễn nguyên nhân chỉ từ một đường biểu đồ.** CPU tăng có thể do thêm tính toán; p99 tăng có thể do GC, contention, I/O telemetry hoặc đã vượt khả năng phục vụ. Nên kết hợp số liệu từ ứng dụng, runtime và hệ thống giám sát.

### 3.3 Chỉ số riêng của hệ thống quan sát

Thêm số đo về chính công cụ đo:

- `aspect_invocations_total`: số lần advice thực sự chạy, theo tên thao tác đã chuẩn hóa.
- `telemetry_events_enqueued_total`, `telemetry_events_dropped_total`: đếm lượng đã đưa vào queue và lượng bị bỏ.
- `telemetry_export_latency`, `telemetry_export_failures_total`: độ trễ/lỗi xuất dữ liệu.
- `telemetry_queue_size`, `telemetry_queue_capacity`: độ đầy và giới hạn queue.
- `sampled_traces_total` hoặc tỷ lệ sampling áp dụng theo cấu hình.

Nếu chỉ đo “tracing bật khiến request chậm hơn” mà không biết queue/logging hoạt động thế nào, khó phân biệt chi phí cố định của advice với chi phí tăng do collector nghẽn.

## 4. Mô hình tính overhead và cách diễn giải

Giả sử cùng một workload và cùng điểm đo:

`delta_latency = latency_instrumented − latency_baseline`

`relative_overhead = (latency_instrumented − latency_baseline) / latency_baseline × 100%`

Ví dụ minh họa (các giá trị là giả định, **không phải benchmark thực tế**):

| Chỉ số | Baseline (tắt advice quan sát) | Variant (bật advice quan sát) | Diễn giải |
| --- | ---: | ---: | --- |
| p50 latency | 8,0 ms | 8,3 ms | Tăng 0,3 ms, tương đương 3,75% |
| p95 latency | 20 ms | 22 ms | Tăng 2 ms, tương đương 10% |
| p99 latency | 42 ms | 61 ms | Tăng 19 ms, tương đương ~45,2% |
| Throughput | 9.800 req/s | 9.300 req/s | Giảm 500 req/s, tương đương ~5,1% |
| CPU | 62% | 76% | Tăng 14 **điểm phần trăm** |
| GC pause p99 | 15 ms | 24 ms | Có tín hiệu cần khảo sát allocation/GC |

Một con số overhead trung bình không thay thế được toàn bộ bảng. p99 có thể suy giảm nặng hơn p50 vì lock, queue hoặc GC. Chênh lệch **tỷ lệ phần trăm** và **điểm phần trăm** cũng khác nhau: CPU từ 62% lên 76% là +14 điểm phần trăm, hay tăng tương đối khoảng 22,6%.

Khi so sánh percentiles, phải lấy p99 trên **cùng loại operation, cửa sổ đo và phân phối workload**. Chênh lệch hai giá trị p99 giữa hai mẫu không phải “p99 của overhead ở từng request”; muốn đo phân phối overhead theo lời gọi cần cách ghép mẫu tương ứng hoặc thiết kế thí nghiệm riêng.

### Khi một request có nhiều điểm cắt

Mô hình đơn giản:

`estimated_extra_cpu ≈ request_rate × invocations_per_request × cpu_cost_per_advice`

Công thức chỉ là **ước lượng bậc độ lớn** trong tình huống chi phí gần cộng tính, không thay thế đo tải. Nó không mô tả nonlinearity do contention, cache, GC, queue, branch prediction hoặc JIT. Đặc biệt, một advice không đồng bộ có thể chuyển chi phí CPU ra background worker; request latency giảm không có nghĩa tổng chi phí tài nguyên biến mất.

## 5. Thiết kế phép thử có thể kết luận được

### Bước 1: Xác định câu hỏi và ngưỡng chấp nhận

Trước khi đo, viết rõ:

- Điều kiện tải nào quan trọng: tải bình thường, tải đỉnh, burst, hoặc khi downstream bị chậm?
- Mức tăng p95/p99, CPU, allocation, số log thất lạc hoặc throughput giảm bao nhiêu được chấp nhận?
- Chỉ đo một advice, tập pointcut, hay toàn bộ pipeline telemetry?
- Cần đạt đồng thời mục tiêu độ trễ, tỷ lệ lỗi, chi phí phần cứng và tính đầy đủ của audit không?

Ví dụ một **tiêu chí nội bộ minh họa**, không phải chuẩn chung: “Ở 5.000 req/s trong 30 phút, p99 tăng dưới 5 ms, throughput đáp ứng tốc độ đến, lỗi không tăng quá 0,1 điểm phần trăm, CPU còn dư ít nhất 20%, queue không tích lũy liên tục, telemetry drop dưới ngưỡng đã quy định.” Ngưỡng thực tế phụ thuộc SLO và khả năng chấp nhận mất telemetry.

### Bước 2: Tạo baseline và variant

Dùng **cùng phiên bản ứng dụng, cùng máy/CPU quota, heap, GC, JDK, cấu hình thread pool, dữ liệu thử, trạng thái cache và traffic generator**. Khác biệt chủ động phải là cấu hình instrumentation:

- **A – Baseline:** giữ business code và workload; không gắn advice quan sát đang đánh giá.
- **B – Aspect enabled:** gắn đúng tập pointcut/advice; xác nhận advice thực sự được gọi và metric/traces được tạo.
- Khi cần phân rã chi phí: thêm các biến thể độc lập như **proxy-only**, **timestamp/counter-only**, **logging-only**, **tracing with sampling**, và **full export**. Tránh bật/tắt cùng lúc quá nhiều yếu tố rồi không biết yếu tố nào gây tăng chi phí.

Nếu có thể dùng một binary với cờ bật/tắt và phần codepath tương đương để hạn chế khác biệt build, phải kiểm tra cờ tắt có còn tốn chi phí dispatch/branch hay không. “Disable export nhưng vẫn tạo span” **không đồng nghĩa** với tắt toàn bộ instrumentation.

### Bước 3: Giữ workload đại diện cho sản xuất

Thử với các đặc tính đã được ẩn danh và tổng hợp:

- Tỷ lệ read/write, API nóng/lạnh, kích thước payload, độ phức tạp truy vấn, số downstream call, cache-hit rate.
- Phân bố thời gian giữa request, mức đồng thời, session think time và burst; không chỉ một vòng lặp gọi đồng loạt.
- Những kịch bản gây lỗi, timeout, retry, cancellation; đường lỗi đôi khi phát sinh stack trace và log nhiều hơn đường thành công.
- Môi trường tương tự production về latency network, database/queue, kích thước thread pool, JIT warm-up và runtime.
- Đặt **load generator** trên tài nguyên độc lập hoặc theo dõi CPU của nó để chắc chắn bên tạo tải chưa trở thành nút nghẽn.

Không sao chép dữ liệu định danh hay bí mật người dùng vào workload. Có thể dùng dữ liệu tổng hợp tái hiện phân bố tải quan sát được.

### Bước 4: Chạy nhiều mức tải

Không chỉ thử một mốc. Xem ít nhất:

1. **Idle / thấp:** biết chi phí cố định và nền tài nguyên.
2. **Tải điển hình:** đo overhead trong điều kiện thường ngày.
3. **Tải đỉnh hợp lệ:** kiểm tra SLO và tài nguyên còn dư.
4. **Tiệm cận bão hòa:** tìm điểm gãy của latency, queue, CPU, error; xác định liệu instrumentation làm giảm capacity.
5. **Burst và recovery:** kiểm tra queue telemetry có phình ra, retry dồn, hoặc mất nhiều thời gian mới trở lại ổn định không.
6. **Collector chậm hoặc ngắt kết nối:** xem advice, exporter và bộ đệm phản ứng thế nào mà không gây tràn bộ nhớ hoặc chặn business thread.

Không nhất thiết cố ý gây quá tải trong môi trường đang phục vụ người dùng. Thực hiện thử nghiệm có giới hạn và isolation thích hợp.

### Bước 5: Warm-up, đo steady state và lặp lại

JVM có JIT, class loading, tối ưu bộ nhớ và cache. Một lần chạy đầu có thể chậm bất thường hoặc nhanh bất thường so với steady state. Khởi động ứng dụng, đưa qua workload warm-up có đại diện, rồi đo các khoảng bằng nhau.

Nên thử nhiều cặp A/B qua nhiều lần chạy, thay đổi thứ tự A/B hoặc luân phiên theo một kế hoạch cố định để giảm tác động của nhiệt độ, tài nguyên và thời gian; ghi lại thứ tự, cấu hình, build hash, runtime, đặc tính máy và thời gian đo. Tránh lấy một lần A vào buổi sáng rồi một lần B khi máy đang chạy nhiều tác vụ khác.

Thu thập kết quả có độ biến thiên: median qua nhiều run, phạm vi sai số hoặc khoảng tin cậy khi đủ dữ liệu. Sự khác biệt vài phần trăm có thể chỉ là nhiễu nếu không chứng minh được kết quả ổn định.

### Bước 6: Kiểm tra đúng workload thực sự đã chạy

Một phép thử chỉ có ý nghĩa khi A/B xử lý số yêu cầu và nội dung tương đương:

- So sánh **offered load** (lượng yêu cầu đưa vào) và **achieved throughput** (lượng được hoàn tất).
- Kiểm tra số lượng kết quả thành công/lỗi, payload, checksum hoặc invariant nghiệp vụ.
- Xác nhận không có retry/timeout ẩn, không thay đổi cache-hit ratio và không bỏ request ở load generator.
- Với phép thử thời gian cố định, đo throughput; với số lượng thao tác cố định, đo thời gian hoàn thành. Cả hai hữu ích nhưng kết luận khác nhau.

### Open-loop, closed-loop và coordinated omission

Trong mô hình **closed-loop**, client chỉ gửi request mới sau khi request trước hoàn thành. Khi server chậm, client cũng giảm tốc độ gửi, vô tình che khuất queueing và tail latency. **Open-loop** phát sinh request theo lịch độc lập với completion, gần hơn với nguồn tải bên ngoài trong nhiều hệ thống, nhưng phải đo cả request bị từ chối và chậm trễ khi generator hoặc queue bị quá tải.

**Coordinated omission** xảy ra khi phép đo bỏ qua khoảng thời gian request *đáng ra* được gửi nhưng không được gửi vì bộ tạo tải đang chờ. Để tránh kết luận p99 quá đẹp, cần cấu hình generator và cách ghi thời điểm arrival/completion phù hợp mô hình lưu lượng mong muốn. Hãy xác định rõ đang đo **service time**, **end-to-end latency**, hay **latency gồm thời gian nằm trong queue**.

## 6. Ví dụ Java 17+: so sánh lời gọi thường với lời gọi qua advice

Đoạn Java sau là một **minh họa giáo dục có thể chạy độc lập** về cách xây dựng baseline và variant. Sử dụng JDK dynamic proxy cho lời gọi qua interface: trong advice có hai lần đọc `System.nanoTime()`, cập nhật `LongAdder` và chuyển tiếp ngoại lệ gốc. Driver dùng nhiều thread, cổng bắt đầu chung và cùng dữ liệu đầu vào; so sánh checksum để phát hiện lỗi ngữ nghĩa.

**Điều quan trọng:** đây là một **load experiment nhỏ để học cơ chế**, không phải bộ benchmark đủ chính xác để kết luận overhead sản xuất. Nếu cần microbenchmark đáng tin cậy, dùng harness như JMH để quản lý warm-up, fork, dead-code elimination và đo phân phối; nếu cần quyết định về SLO, chạy kiểm thử end-to-end ở workload gần production.

**Tệp: `AopObservationCostDemo.java`**

~~~java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;

public class AopObservationCostDemo {
    interface Work {
        long compute(int input);
    }

    static final class PlainWork implements Work {
        @Override
        public long compute(int input) {
            long value = input;
            for (int i = 0; i < 64; i++) {
                value = (value * 1664525L + 1013904223L) & 0x7fffffffL;
            }
            return value;
        }
    }

    static final class Metrics {
        final LongAdder calls = new LongAdder();
        final LongAdder failures = new LongAdder();
        final LongAdder durationNanos = new LongAdder();

        void record(long elapsed, boolean failed) {
            durationNanos.add(Math.max(0L, elapsed));
            if (failed) {
                failures.increment();
            }
            calls.increment();
        }
    }

    static Work observed(Work target, Metrics metrics) {
        InvocationHandler advice = (Object proxy, Method method, Object[] args) -> {
            if (method.getDeclaringClass() == Object.class) {
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException ex) {
                    throw ex.getCause();
                }
            }

            long start = System.nanoTime();
            boolean failed = true;
            try {
                Object result = method.invoke(target, args);
                failed = false;
                return result;
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            } finally {
                metrics.record(System.nanoTime() - start, failed);
            }
        };

        return (Work) Proxy.newProxyInstance(
            Work.class.getClassLoader(),
            new Class<?>[] { Work.class },
            advice
        );
    }

    record Trial(long elapsedNanos, long checksum) {}

    static Trial run(Work work, int threads, int callsPerThread)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> tasks = new ArrayList<>();

        try {
            for (int t = 0; t < threads; t++) {
                Callable<Long> task = () -> {
                    ready.countDown();
                    start.await();
                    long checksum = 0L;
                    for (int i = 0; i < callsPerThread; i++) {
                        checksum += work.compute(i);
                    }
                    return checksum;
                };
                tasks.add(pool.submit(task));
            }

            ready.await();
            long beganAt = System.nanoTime();
            start.countDown();

            long checksum = 0L;
            for (Future<Long> task : tasks) {
                checksum += task.get();
            }
            return new Trial(System.nanoTime() - beganAt, checksum);
        } finally {
            pool.shutdownNow();
        }
    }

    public static void main(String[] args) throws Exception {
        final int threads = 4;
        final int callsPerThread = 20_000;

        Work plain = new PlainWork();

        // Làm nóng cả hai đường thực thi; bỏ số đo của giai đoạn này.
        Work warmObserved = observed(plain, new Metrics());
        for (int i = 0; i < 2; i++) {
            run(plain, threads, 2_000);
            run(warmObserved, threads, 2_000);
        }

        Metrics metrics = new Metrics();
        Work withAdvice = observed(plain, metrics);

        Trial a = run(plain, threads, callsPerThread);
        Trial b = run(withAdvice, threads, callsPerThread);

        boolean equal = a.checksum() == b.checksum();
        long expected = (long) threads * callsPerThread;

        System.out.printf(
            Locale.ROOT,
            "calls=%d failures=%d equal=%s expected=%d%n",
            metrics.calls.sum(), metrics.failures.sum(), equal, expected
        );

        double msA = a.elapsedNanos() / 1_000_000.0;
        double msB = b.elapsedNanos() / 1_000_000.0;
        double extraNsPerCall =
            (b.elapsedNanos() - a.elapsedNanos()) / (double) expected;

        System.out.printf(
            Locale.ROOT,
            "plain=%.3f ms observed=%.3f ms delta=%.1f ns/call%n",
            msA, msB, extraNsPerCall
        );

        if (!equal || metrics.calls.sum() != expected) {
            throw new IllegalStateException("Incorrect observation");
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac AopObservationCostDemo.java
java AopObservationCostDemo
~~~

Dòng stdout đầu tiên (với lần chạy thành công bình thường) có giá trị xác định:

~~~text
calls=80000 failures=0 equal=true expected=80000
~~~

Dòng tiếp theo có dạng:

~~~text
plain=<số đo> ms observed=<số đo> ms delta=<số đo> ns/call
~~~

Ba số ở dòng thứ hai **thay đổi theo máy, JIT, scheduler, tải và cách đo**. Chúng thậm chí có thể cho delta âm trong một lần chạy do nhiễu; không được sửa kết quả âm thành bằng chứng “advice tăng tốc”. Cần chạy nhiều lần, đảo thứ tự baseline/variant và quan sát phân phối trước khi rút kết luận.

Mỗi thread cộng kết quả của tất cả lời gọi `compute(i)`, rồi chương trình cộng checksum của các thread. Khi baseline và biến thể có cùng checksum, hai đường đã tính ra cùng kết quả cho workload này. Counter của advice chỉ được đọc **sau khi toàn bộ `Future` hoàn tất**, nên `LongAdder.sum()` phản ánh đủ 80.000 lần gọi của biến thể trong đoạn đo. Thời gian `Trial` đo theo đồng hồ bao ngoài cả nhóm thread; nó không phải p99 latency của từng lần `compute`.

**Vì sao code không tự động là một phép đo chuẩn?**

- Baseline dùng lời gọi trực tiếp, variant thêm proxy/reflection/timestamp/counter; phép đo gộp các chi phí này.
- Warm-up rất ngắn, không có fork hay cách ly tác vụ nền; thứ tự A trước B có thể gây bias.
- Không tạo traffic từ nhiều máy, network/database, log exporter, phân phối request hay áp lực GC thực tế.
- Không đo p95/p99, allocation, throughput ổn định, tình trạng bão hòa, queue hay lỗi xuất telemetry.

Hãy xem mẫu như một bộ khung để hiểu **đo cái gì và vì sao cần đối chứng**. Nó giúp chứng minh aspect hoạt động đúng, rồi mới bước sang benchmark và load test đủ điều kiện.

## 7. Tách các nguồn overhead để tìm nguyên nhân

Một chuỗi phép thử từ tối thiểu đến đầy đủ giúp phân tách chi phí:

1. **Business only:** thao tác gốc.
2. **Proxy/weaving only:** gắn cơ chế chặn nhưng advice rỗng hoặc chỉ chuyển tiếp một lần.
3. **Counting:** cập nhật counter, không format log hoặc gửi network.
4. **Timing:** thêm timestamp và histogram.
5. **Tracing:** tạo scope/span, ghi attribute, áp dụng sampling.
6. **Logging / serialization:** dựng thông điệp và ghi vào pipeline log.
7. **Exporter end-to-end:** batch, queue, network, retry và collector.

Chênh lệch giữa các bước giúp khoanh vùng nguồn chi phí, nhưng chỉ hợp lệ nếu điều kiện tải và cấu hình giữ nguyên. Khi bước 7 xấu đi mạnh so với bước 6, cần đo queue/export/backpressure chứ không vội quy lỗi cho chi phí ghi `nanoTime()`.

### Metrics không phải luôn “rẻ”

Cập nhật một `LongAdder` thường phù hợp hơn một khóa toàn cục tại hot path có nhiều thread, nhưng vẫn có chi phí; registry có thể phát sinh phân bổ bản ghi mỗi khi tạo series. Histogram có thể dùng nhiều bucket, cập nhật nhiều cấu trúc, hoặc cạnh tranh trên bộ nhớ dùng chung. Nếu dùng nhãn động theo user ID/URL tùy ý, số lượng series tăng không giới hạn, làm tăng CPU và heap cho cả ứng dụng lẫn backend lưu metrics.

Nên định nghĩa nhãn có số giá trị hữu hạn như operation chuẩn hóa, outcome và loại exception đã gom nhóm. ID request, email hoặc tham số nghiệp vụ chi tiết phù hợp hơn với trace/log được kiểm soát quyền truy cập. Cần đo số series và memory của registry khi workload thay đổi, không chỉ khi có vài giá trị nhãn.

### Trace sampling và ảnh hưởng tới phép đo

**Head sampling** chọn trace sớm; **tail sampling** có thể cần giữ thêm dữ liệu trong bộ nhớ trước khi quyết định. Sampling có thể giảm chi phí export nhưng đôi khi vẫn tốn chi phí tạo context/span nội bộ; mức tiết kiệm phụ thuộc điểm sampling và pipeline. Test ít nhất sampling thấp, sampling thiết kế cho production và tình huống tracing dày đặc. Ghi rõ xác suất sampling và những sự kiện vẫn luôn được thu thập.

Không nhầm “chỉ lưu 1% trace” với “chỉ có 1% advice chạy”. Nếu advice vẫn chạy cho 100% request, phép đo proxy dispatch và kiểm tra cờ sampling vẫn tác động lên 100% hot path.

### Logging, I/O và hàng đợi

Log đồng bộ trực tiếp tới file/network có thể chặn thread xử lý request. Đưa log sang queue riêng giúp giảm độ trễ trên request khi queue còn chỗ, nhưng cần giới hạn kích thước. Nếu collector chậm hơn tốc độ sinh log trong thời gian dài, queue sẽ đầy. Phải quyết định: block caller, bỏ bản ghi, giảm sampling, ghi fallback hay backpressure nguồn. Lựa chọn nào cũng có trade-off về độ trễ, độ tin cậy và đầy đủ của bằng chứng audit.

Nếu yêu cầu bảo đảm mọi audit event được lưu, không thể mặc định chọn bỏ log. Chi phí độ bền dữ liệu và chính sách lỗi phải được đưa vào điều kiện chấp nhận và thiết kế kiến trúc ngay từ đầu.

## 8. Đọc kết quả khi hệ thống bão hòa

Trước ngưỡng bão hòa, advice có thể khiến CPU tăng mà latency chỉ tăng nhẹ. Khi chạm giới hạn CPU, thread pool, connection pool hoặc queue, chỉ một thay đổi nhỏ về nhu cầu tài nguyên có thể làm p99, timeout và queueing tăng rất mạnh. Vì thế một phép thử chỉ ở mức tải 10% sẽ khó dự đoán điều xảy ra tại đỉnh 90%.

Xét mô hình đơn giản của hàng đợi:

- Tốc độ đến `λ` (requests/giây).
- Tốc độ xử lý tối đa `μ` (requests/giây), với giả định mô hình có ý nghĩa cho hệ đang xét.
- Khi `λ` tiến gần `μ`, queue và thời gian chờ thường nhạy cảm hơn nhiều với mỗi phần chi phí mới.
- Khi `λ` vượt khả năng phục vụ bền vững, tồn đọng sẽ tăng hoặc hệ thống phải từ chối/giảm tải.

Không áp dụng một công thức queue đơn giản như quy luật phổ quát cho mọi kiến trúc. Nhưng về thực hành, luôn phải vẽ **đường cong tải → p95/p99 → throughput → error → CPU/queue**, để thấy điểm cong gãy của baseline và variant. Instrumentation được xem là có vấn đề nếu điểm gãy chuyển xuống mức tải nằm trong phạm vi vận hành cần hỗ trợ.

**Little's Law** ở trạng thái ổn định (`L = λ × W`) giúp kiểm tra tính hợp lý giữa số request trung bình đang trong hệ, throughput và thời gian chờ trung bình, miễn là tập request và ranh giới đo nhất quán. Đây là quan hệ trung bình ở hệ ổn định, không phải công thức suy ra p99 hoặc cam kết về queue khi đang biến động mạnh.

## 9. Thí nghiệm lỗi có chủ đích với đường telemetry

Một aspect quan sát có thể hoạt động tốt ở trạng thái bình thường nhưng gây sự cố thứ cấp khi hệ giám sát chậm hoặc mất kết nối. Trong môi trường test có kiểm soát, hãy thử:

| Điều kiện | Dấu hiệu cần quan sát | Hành vi thiết kế mong muốn |
| --- | --- | --- |
| Collector phản hồi chậm | Queue tăng, exporter latency tăng | Có giới hạn queue, timeout và chiến lược đầy |
| Collector ngắt kết nối | Retry tăng, thread nền tồn tại, heap thay đổi | Retry có giới hạn, không tích lũy vô hạn |
| Số log/span đột biến khi lỗi | Logging làm CPU và network tăng | Quota/sampling/batch hoặc năng lực dự phòng thích hợp |
| Backend từ chối dữ liệu | Số event xuất thất bại | Metric drop/error chính xác và thông báo trạng thái |
| Lỗi trong logic advice | Exception instrumentation ảnh hưởng request | Chính sách cách ly hoặc fail-closed được định nghĩa |
| Thread pool/queue telemetry đầy | p99 và throughput nghiệp vụ biến đổi | Không đẩy công việc vô giới hạn lên request path |
| Ứng dụng shutdown/redeploy | Flush bị ngắt, event còn trong queue | Timeout và chính sách flush/at-least-once rõ ràng |

Nếu quan sát chỉ phục vụ vận hành, phần lớn ứng dụng muốn telemetry failure không tự thay đổi kết quả nghiệp vụ; tuy vậy một số kiểm soát bảo mật/audit có thể yêu cầu ngừng xử lý khi ghi nhận không thành công. **Không có một chính sách fail-open/fail-closed áp dụng cho mọi trường hợp**. Bài đo cần đánh giá đúng hợp đồng đã chọn.

## 10. Tính đúng đắn vẫn là điều kiện đầu tiên

Chi phí thấp nhưng thay đổi kết quả chương trình là thất bại. Kiểm thử trước/sau instrumentation phải đảm bảo:

- Cùng kết quả, cùng side effect và đúng số lần gọi thao tác gốc; advice ghi đo không tự gọi lại, retry hoặc lọc response.
- Caller vẫn nhận đúng exception gốc và cancellation/timeout semantics phù hợp.
- Không mất context khi chuyển thread, không rò rỉ scope hoặc `ThreadLocal` giữa các request.
- Với nguồn sự kiện có backpressure, không tự gọi thêm `request(n)`, không tạo subscriber mới và không thêm queue không giới hạn.
- Không có recursion vì pointcut vô tình chọn chính thư viện telemetry, cũng không ghi nhận sai vì self-invocation đi vòng ngoài cơ chế proxy.
- Counter chính xác theo đơn vị logic đã chọn: per-attempt, per-method, per-request hoặc per-subscription. Không cộng chồng những đại lượng khác đơn vị.

Cũng phải kiểm chứng dữ liệu **của chính phép đo**: counter đã bắt đủ hot path chưa, metric có đang đo từng phương thức hay cả luồng bất đồng bộ, sample có đại diện cho cả success lẫn failure, exporter drop đã được tính, nhãn có bị trộn giữa các nhóm workload không.

## 11. Báo cáo kiểm thử và tiêu chí ra quyết định

Một báo cáo hữu ích cần có cấu trúc có thể tái hiện. Ví dụ:

~~~text
Scope: around advice cho operation catalog.lookup
Runtime: JDK 17, cấu hình CPU/heap/GC được ghi kèm
Scenario: traffic hỗn hợp theo phân bố request định nghĩa trước
A: chưa bật advice quan sát
B: bật cùng một bộ pointcut + counter + histogram + tracing/export
Load: 500 / 2.000 / 5.000 / 8.000 req/s; burst và collector chậm
Warm-up: theo tiêu chí steady state đã kiểm chứng
Measurement: nhiều lần, cùng khoảng đo; thứ tự A/B được ghi lại
Business correctness: checksum, success/failure, retry và timeout
Performance: achieved throughput, p50/p95/p99, errors, CPU, allocation, GC
Telemetry: span/log volume, queue depth, drop, export error/latency
Decision: đạt/không đạt theo ngưỡng đã đặt trước; ghi rõ bất định
~~~

Đây là **khung báo cáo minh họa**, không phải kết quả thí nghiệm đã được thực thi.

Để tránh kết luận thiếu bằng chứng, kết quả cuối cần trả lời bốn câu hỏi:

1. **Bao nhiêu?** Chi phí tuyệt đối và tương đối là bao nhiêu tại từng mức tải, với độ dao động ra sao?
2. **Ở đâu?** Chi phí xuất hiện ở dispatch, advice, bộ đếm, histogram, trace, log, exporter, GC hay queue?
3. **Khi nào?** Overhead ổn định ở tải thường nhưng có làm điểm bão hòa đến sớm hoặc tăng p99 trong burst không?
4. **Đổi lấy điều gì?** Giá trị quan sát đạt được có tương xứng với tài nguyên, rủi ro, tổn thất telemetry hoặc sai lệch semantics không?

Chỉ thay đổi một nhóm cấu hình mỗi vòng điều tra. Chẳng hạn nếu instrumentation quá tốn kém, thử thu hẹp pointcut, giảm nhãn cardinality, giới hạn attributes và stack trace, điều chỉnh sampling, tổng hợp số đo cục bộ rồi batch export; sau mỗi thay đổi phải đo lại dưới cùng workload. Không tối ưu bằng cách làm mất dữ liệu bắt buộc mà chưa thống nhất chính sách.

## 12. Các lỗi suy luận phổ biến

**“Trong bài đo một thread advice chỉ tốn 50 ns, vậy lên production cũng chỉ tốn 50 ns.”** Không đúng: multi-thread contention, JIT, GC, exporter, I/O và queue có thể thay đổi rất lớn; microbenchmark chỉ trả lời câu hỏi ở cấu hình của nó.

**“Throughput giữ nguyên nên không có overhead.”** Không đúng: khi còn dư CPU, throughput có thể giữ nguyên dù CPU và tail latency tăng; mức dư công suất đã bị bào mòn.

**“Bật asynchronous logging sẽ không ảnh hưởng request.”** Không đúng: enqueue vẫn có chi phí; khi queue đầy có thể block/drop; worker/exporter vẫn dùng tài nguyên chung.

**“Bật tracing 1% thì chi phí chính xác là 1%.”** Không đúng: quyết định sampling, propagation, context và một phần tạo dữ liệu có thể chạy trên mọi request.

**“Bảo đảm 100% log nhưng khi collector chết cứ để queue vô hạn.”** Không đúng: queue không giới hạn đẩy rủi ro sang OOM. Nếu audit không được mất, cần kiến trúc và tài nguyên phù hợp để bảo đảm độ bền, hoặc đưa quyết định xử lý lỗi ra cấp nghiệp vụ.

**“p99 của toàn hệ thống bằng trung bình p99 của các instance.”** Không đúng: percentiles không cộng/trung bình trực tiếp. Cần histogram hoặc thuật toán có hỗ trợ gộp phân phối với cùng quy ước.

**“Bản thử A chạy sáng, B chạy chiều nên B chậm là do advice.”** Chưa đủ bằng chứng: môi trường và profile tải có thể thay đổi. Cần lặp cặp đối chứng, quản lý nhiễu và ghi lại điều kiện.

**“Chỉ cần đo duration do advice báo.”** Không đủ: đồng hồ ngay trong advice thường không bao gồm chi phí dispatch, đo, export, GC hoặc queue ảnh hưởng từ bên ngoài. Đối chiếu số đo của client và hệ thống giúp tránh quan sát chỉ những gì instrumentation chọn ghi.

## 13. Checklist thực hành

- [ ] Pointcut và tập join point đã được xác minh; biết số advice invocation trên một request logic.
- [ ] Baseline/variant có cùng business code, workload và điều kiện tài nguyên.
- [ ] Có warm-up, nhiều lần chạy và đủ thời lượng đo steady state.
- [ ] Load generator không thành nút nghẽn; offered load và achieved throughput được tách biệt.
- [ ] Đã đo p50/p95/p99, throughput, lỗi/timeout, CPU, allocation, GC và queue quan trọng.
- [ ] Đã quan sát mức tải bình thường, tải đỉnh, bão hòa, burst và collector trục trặc.
- [ ] Có phân rã proxy/weaving, counter, histogram, tracing, logging và exporter.
- [ ] Biết giới hạn telemetry buffer, backpressure, sampling, số series và chính sách xử lý khi đầy.
- [ ] Business semantics, exception, retry, cancellation và context còn đúng.
- [ ] Ngưỡng quyết định được viết trước; kết luận nêu kèm biến thiên và giới hạn thí nghiệm.

## 14. Kết luận

**Chi phí quan sát là chi phí thực của hệ thống**, dù được tách vào một aspect hay chạy trên worker nền. Cần kiểm tra nó bằng phép so sánh có đối chứng và tải đại diện, quan sát cả độ trễ đuôi, công suất, CPU, memory, GC, queue, lỗi và khả năng phục hồi khi backend telemetry suy giảm. Microbenchmark giúp hiểu chi phí trên một join point; load test và fault test mới cho biết hiệu ứng tổng thể ở ngưỡng sử dụng thực tế. Chỉ coi việc quan sát là đạt yêu cầu khi **các phép đo chính xác, hành vi nghiệp vụ được giữ nguyên và ngân sách hiệu năng/tài nguyên đáp ứng được yêu cầu vận hành**.

</details>

- [Quay lại đầu trang](#back-to-top)

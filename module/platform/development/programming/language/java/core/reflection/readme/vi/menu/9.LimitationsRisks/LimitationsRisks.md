# Giới hạn và rủi ro của Reflection

Reflection giải quyết một bài toán thật: code có thể làm việc với cấu trúc chỉ được biết ở runtime. Nhưng sự linh hoạt đó đến từ việc dời nhiều đảm bảo của trình biên dịch sang runtime. Vì vậy câu hỏi thiết kế không phải “Reflection có làm được không?”, mà là “hành vi động này có đáng để trả chi phí về độ an toàn, sự phụ thuộc và vận hành không?”.

Các rủi ro trong chương này đều xuất phát từ cùng cách hiểu: code Java trực tiếp tham chiếu type/member bằng symbol mà trình biên dịch hiểu; code dùng Reflection thường đi qua metadata, string, `Class`, `Method`, `Field` và `Object` rồi tự kiểm tra ở runtime.

## <a id="compile-time-safety-loss">Mất độ an toàn ở compile time</a>

Lời gọi trực tiếp tạo ra một contract mà trình biên dịch có thể kiểm tra:

```java
String result = service.pay(request);
```

Nếu `pay` bị đổi tên, parameter đổi type hoặc return type thay đổi, code gọi trực tiếp thường lỗi khi compile. Với Reflection:

```java
Method method = service.getClass().getMethod(methodName, PaymentRequest.class);
Object result = method.invoke(service, request);
```

`methodName` có thể đến từ cấu hình hoặc annotation. Trình biên dịch không biết string đó có còn trỏ đến member hợp lệ hay không. Sai tên, sai signature, sai target hoặc sai argument chỉ lộ ra khi luồng runtime được chạy.

Reflection còn làm luồng type yếu hơn. `invoke()` trả `Object`, nên bên gọi thường phải cast. Generic metadata cũng chỉ mô tả khai báo; nó không tự khôi phục toàn bộ độ an toàn generic ở compile time.

Đây không phải lý do cấm Reflection. Framework cần khám phá động thì không thể yêu cầu trình biên dịch biết trước mọi phần triển khai. Cách giảm rủi ro là đưa việc resolve động vào một ranh giới nhỏ, kiểm tra sớm và cung cấp API có type rõ ràng cho phần còn lại của ứng dụng.

```text
config/metadata động
        ↓ validate một lần
reflection adapter / registry
        ↓ typed contract
application code
```

## <a id="encapsulation-breakage">Sự phụ thuộc khi phá tính đóng gói</a>

Deep reflection cho phép framework làm việc với private constructor/field/method khi ranh giới truy cập cho phép. Nhưng private member vốn là chi tiết phần triển khai mà bên sở hữu được quyền thay đổi mà không cần giữ tương thích cho bên gọi bên ngoài.

Nếu mapper đọc `processedCount` bằng tên field hoặc framework gọi `internalStatus()`, code đó đã tạo một contract ngầm:

```text
chi tiết triển khai của PaymentService
          ↓ private name/signature
reflective consumer phụ thuộc vào nó
```

Đổi tên, refactor package hoặc thay đổi tính đóng gói có thể làm bên sử dụng hỏng ở runtime. JPMS còn khiến sự phụ thuộc này rõ hơn: package không `opens` thì deep reflection từ module khác có thể bị chặn hoàn toàn.

Khi thư viện/framework cần quyền truy cập qua Reflection bền vững, tốt hơn là thiết kế một điểm tích hợp rõ ràng: public interface, annotation contract, quy ước constructor/property được mô tả rõ, hoặc package `opens` có chủ đích. `setAccessible(true)` không nên trở thành cách mặc định để “sửa” một thiết kế API thiếu contract.

## <a id="reflection-performance">Hiệu năng và cache metadata</a>

Reflection có overhead, nhưng câu “Reflection luôn chậm” quá đơn giản để dùng làm quy tắc thiết kế.

Chi phí có thể nằm ở nhiều nơi:

- repeated lookup như `getDeclaredMethod()` hoặc scan toàn bộ members;
- annotation/generic metadata parsing và resolution;
- access checks;
- argument packing, unboxing/widening và return boxing;
- reflective dispatch khó tối ưu giống một direct call site trong một số hot path.

Runtime Java hiện đại đã tối ưu Core Reflection đáng kể, nên không nên dựa vào benchmark cũ hoặc các chi tiết phần triển khai lịch sử để kết luận. Cách thực tế là **không lặp việc khám phá metadata vô ích**.

```java
final class PaymentInvoker {
    private final Method pay;

    PaymentInvoker() throws NoSuchMethodException {
        this.pay = PaymentService.class.getMethod("pay", PaymentRequest.class);
    }

    Method method() {
        return pay;
    }
}
```

Framework thường resolve metadata khi khởi động, đăng ký hoặc lần dùng đầu tiên, sau đó cache descriptor/kế hoạch đã được kiểm tra. Cache giảm lookup/parsing lặp lại; nó không xóa chi phí chuyển đổi argument, dynamic dispatch hay chi phí bảo trì.

Nếu Reflection nằm trong hot loop và hiệu năng thực sự quan trọng, hãy đo bằng benchmark/profiler trên workload thật. Khi target đã được resolve và invoke rất nhiều lần, `MethodHandle` hoặc generated code có thể đáng cân nhắc, nhưng lựa chọn phải dựa trên mô hình của use case chứ không phải khẩu hiệu “Reflection chậm”.

## <a id="native-image-boundary">Ranh giới với native image</a>

Đây là một ranh giới **deployment/runtime**, không phải kiến thức bắt buộc để hiểu `Method.invoke()` hay `Field.get()`. Trước tiên cần giải nghĩa vài thuật ngữ:

- **JVM/HotSpot**: cách chạy Java thông thường, nơi bytecode được JVM load và có thể được JIT compile trong lúc chương trình chạy;
- **AOT (ahead-of-time)**: compile trước khi chương trình bắt đầu chạy thay vì chờ runtime mới compile/resolve mọi thứ;
- **native executable**: file thực thi native được build sẵn cho nền tảng đích;
- **closed-world analysis**: công cụ build giả định tập code cần tồn tại phải được xác định từ những gì nó có thể thấy ở build time;
- **reachability metadata**: thông tin bổ sung nói cho công cụ build biết những class/member nào vẫn cần được giữ vì chúng chỉ được tìm động ở runtime.

Trên JVM thông thường, ứng dụng có thể quyết định ở runtime rằng nó cần class/member nào rồi dùng Reflection để tìm chúng. Một hệ thống native image theo mô hình AOT + closed-world có bài toán khác: công cụ build cần biết phần chương trình nào phải tồn tại trong native executable.

GraalVM Native Image là ví dụ điển hình. Static analysis có thể nhận ra một số quyền truy cập qua Reflection khi target đủ rõ tại build time, nhưng truy cập phụ thuộc string/cấu hình runtime thường cần **reachability metadata** để khai báo class, method, field, proxy hoặc tính năng động cần giữ lại.

Mental model:

```text
JVM dynamic world
runtime input → chọn member → reflection tìm member

native image closed world
build-time analysis + reachability metadata
        ↓
quyết định member nào có trong executable
```

Vì vậy framework dùng Reflection nhiều cần nghĩ đến tích hợp native image ngay tại lớp metadata. Một ứng dụng chạy đúng trên HotSpot nhưng thiếu reachability metadata có thể lỗi trong native executable khi member động không được đăng ký/giữ lại.

Cấu hình native image là ranh giới của môi trường triển khai/runtime khác, không thay đổi ngữ nghĩa của Java Reflection trên JVM. Không nên đưa mối quan tâm về native image vào mọi business class; framework hoặc module tích hợp nên sở hữu hints/metadata tương ứng.

## <a id="reflection-maintainability">Khả năng bảo trì và refactor</a>

Trình biên dịch và IDE hiểu symbol reference tốt hơn Reflection dựa trên string. Nếu code viết:

```java
service.pay(request);
```

thao tác đổi tên khi refactor có thể cập nhật/cảnh báo các call site. Nếu code viết:

```java
getMethod("pay", PaymentRequest.class);
```

công cụ có thể không biết string `"pay"` là một dependency cần đổi. Cấu hình bên ngoài mã nguồn còn khó theo dấu hơn.

Reflection cũng làm luồng điều khiển ít hiển nhiên: đọc mã nguồn của bên gọi chưa chắc biết method nào sẽ chạy, vì target có thể đến từ việc scan, annotation, class name hoặc plugin registry. Debugging và static analysis vì thế cần nhiều ngữ cảnh runtime hơn.

Một thiết kế dùng Reflection dễ bảo trì thường có các đặc điểm:

1. Reflection tập trung ở lớp framework/adapter thay vì rải khắp business code;
2. dùng `Class<?>`, annotation, enum hoặc typed registration khi có thể thay cho raw string;
3. resolve và kiểm tra metadata sớm;
4. cache một mô hình đã kiểm tra thay vì scan lại nhiều lần;
5. có integration test chạy các luồng Reflection quan trọng;
6. public contract vẫn là nguồn sự thật, Reflection vào member private chỉ dùng khi yêu cầu thật sự cần.

Những đánh đổi này chuẩn bị cho chương cuối. Dynamic Proxy cho thấy một use case Reflection rất đáng giá: runtime có thể tạo một phần triển khai của **interface** và đưa mọi lời gọi qua một `InvocationHandler`, giúp interception trở thành một ranh giới có cấu trúc thay vì tự gọi method bằng string ở khắp nơi.

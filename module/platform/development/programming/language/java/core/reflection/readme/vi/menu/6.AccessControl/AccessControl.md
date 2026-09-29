# Reflection và Access Control

Các chương trước cho thấy Reflection có thể tìm `Field`, `Method` và `Constructor` dựa trên metadata thay vì gọi trực tiếp trong mã nguồn. Từ đây xuất hiện một câu hỏi quan trọng: nếu `getDeclaredMethod()` nhìn thấy cả method `private`, vậy `private` còn ý nghĩa gì không?

Điểm cần giữ trong đầu là **nhìn thấy metadata không đồng nghĩa với có quyền sử dụng member đó**. Reflection cho phép chương trình mô tả cấu trúc runtime; khi đọc field, gọi method hoặc tạo object, JVM vẫn áp dụng kiểm soát truy cập. `AccessibleObject` chỉ cung cấp một cơ chế có kiểm soát để yêu cầu bỏ qua một phần kiểm tra truy cập, và Java Module System có thể chặn yêu cầu đó.

Ta tiếp tục **đúng baseline public class/public constructor** của các chương trước; chương này chỉ tập trung vào private member để learner không nhầm rằng lỗi access đến từ việc sample class đã bị đổi:

```java
record PaymentRequest(String orderId, long amount) {}

public class PaymentService {
    private final String provider;
    private int processedCount;

    public PaymentService(String provider) {
        this.provider = provider;
    }

    public String pay(PaymentRequest request) {
        processedCount++;
        return provider + ":" + request.orderId();
    }

    private String internalStatus() {
        return provider + ":" + processedCount;
    }
}
```

## <a id="language-vs-reflective-access">Quyền truy cập của Java và quyền truy cập qua Reflection</a>

Với code bình thường, trình biên dịch kiểm tra `public`, `protected`, package-private và `private` ngay tại nơi gọi. Một class bên ngoài `PaymentService` không thể viết `service.internalStatus()` vì lời gọi đó vi phạm quy tắc truy cập của Java.

Reflection tách quá trình này thành hai bước. Bước đầu là **khám phá (discovery)**: lấy descriptor đại diện cho member. Bước sau là **sử dụng**: đọc/ghi field, invoke method hoặc gọi constructor. `getDeclaredMethod()` có thể trả về descriptor cho method `private`, nhưng `Method.invoke()` vẫn kiểm tra quyền truy cập nếu việc kiểm tra truy cập chưa được tắt.

```java
PaymentService service = new PaymentService("demo");
Method method = PaymentService.class.getDeclaredMethod("internalStatus");

System.out.println(method.getName());        // internalStatus
System.out.println(method.canAccess(service)); // thường là false từ bên gọi bên ngoài

method.invoke(service); // IllegalAccessException nếu bên gọi không có quyền
```

Điều này giải thích vì sao Reflection không làm mất tính đóng gói chỉ bằng việc “thấy” private member. Descriptor là metadata; quyền thao tác là một ranh giới khác.

`canAccess(obj)` kiểm tra member hiện có thể được bên gọi sử dụng hay không. Với instance field/method, `obj` phải là instance phù hợp; với static member và constructor, argument phải là `null`. Đây là cách kiểm tra trạng thái truy cập thực tế rõ ràng hơn API cũ `isAccessible()`, vốn chỉ cho biết cờ bỏ qua kiểm tra truy cập đang bật hay tắt.

## <a id="try-set-accessible">trySetAccessible và setAccessible</a>

`Field`, `Method` và `Constructor` đều kế thừa từ `AccessibleObject`. Chúng có một cờ cho phép bỏ qua các kiểm tra truy cập của ngôn ngữ Java khi runtime cho phép.

```java
Method method = PaymentService.class.getDeclaredMethod("internalStatus");

if (method.trySetAccessible()) {
    Object value = method.invoke(new PaymentService("demo"));
    System.out.println(value);
}
```

`trySetAccessible()` cố bật quyền truy cập và trả `true` nếu thành công. Nếu ranh giới hiện tại không cho phép deep reflection, nó trả `false`. `setAccessible(true)` yêu cầu cùng mục tiêu nhưng thất bại bằng `InaccessibleObjectException` khi quyền truy cập không thể được mở.

Hai API này không nên được hiểu là “biến private thành public”. Modifier của member không thay đổi. Chúng chỉ ảnh hưởng việc đối tượng phản chiếu có được bỏ qua kiểm tra truy cập khi sử dụng hay không.

Trước khi đi sâu vào JPMS, hãy giữ một mental model hai tầng:

~~~text
Java access rules
public / protected / package-private / private
        ↓
module boundary (khi dùng named modules)
exports → cho module khác dùng public API của package
opens   → cho phép deep reflection vào package
~~~

`exports` và `opens` giải hai bài toán khác nhau. Một package có thể export public API nhưng vẫn không open private internals cho framework reflect sâu.

Trong cùng module, Reflection thường có thể bật quyền truy cập tới member của class cùng module. **Unnamed module** là module ngầm của code chạy trên classpath và không có `module-info.java`; **open module** là named module khai báo `open module ...`, nghĩa là các package của nó mặc định được mở cho deep reflection. Với hai trường hợp đó, package được coi là open theo quy tắc JPMS. Khi code nằm ở named module khác, `exports` và `opens` trở thành phần quyết định.

Một cách làm thực tế là **thử trước, xử lý lỗi rõ ràng**:

```java
Method method = PaymentService.class.getDeclaredMethod("internalStatus");

if (!method.trySetAccessible()) {
    throw new IllegalStateException("PaymentService is not open for reflective access");
}

String status = (String) method.invoke(service);
```

Cách làm này tốt hơn việc giả định `setAccessible(true)` luôn thành công rồi để lỗi xuất hiện ở môi trường module khác.

## <a id="strong-encapsulation-boundary">Strong encapsulation của JPMS</a>

Java Platform Module System thêm một ranh giới cao hơn quyền truy cập package. Với named module, cần phân biệt hai khái niệm:

- `exports` cho module khác dùng **public API** của package;
- `opens` cho module khác thực hiện **deep reflection** vào member không public của package.

Ví dụ:

```java
module payment.core {
    exports com.example.payment.api;
    opens com.example.payment.internal to payment.framework;
}
```

Module `payment.framework` có thể reflect sâu vào package `com.example.payment.internal`, nhưng module khác không tự động có quyền đó. Nếu package chỉ `exports` mà không `opens`, public member có thể được dùng theo quy tắc `exports`, còn private/package/protected instance members không thể đơn giản bị mở bằng `setAccessible(true)` từ module ngoài.

Đây là strong encapsulation: ranh giới module có thể nói rõ package nào là API công khai và package nào cho phép deep reflection. Reflection vẫn mạnh, nhưng không phải quyền vượt qua mọi ranh giới module.

Khi chạy ứng dụng, tùy chọn JVM dạng sau có thể mở tạm một package cho một module sử dụng:

```text
--add-opens payment.core/com.example.payment.internal=payment.framework
```

Đó là quyết định khi triển khai/runtime, không phải lý do để thư viện mặc định dựa vào phần nội bộ `private`. Nếu framework cần reflection hợp lệ lâu dài, module descriptor hoặc hợp đồng tích hợp nên biểu diễn ranh giới đó rõ ràng.

## <a id="accessible-object-risk">Rủi ro khi mở quyền truy cập</a>

Việc bỏ qua kiểm tra truy cập tạo ra sức mạnh giống framework: serializer, dependency injection container hay mapper có thể đọc constructor/field mà code ứng dụng bình thường không gọi trực tiếp. Sức mạnh này đi cùng sự phụ thuộc chặt vào chi tiết phần triển khai.

Nếu một công cụ dựa vào `provider`, `processedCount` hoặc `internalStatus()`, các member đó về mặt ngôn ngữ vẫn là private nhưng thực tế đã trở thành hợp đồng ngầm của công cụ. Đổi tên field, đổi constructor, chuyển package hoặc bật strong encapsulation có thể làm tích hợp hỏng ở runtime thay vì được trình biên dịch báo ngay.

Reflection cũng không bảo đảm mọi `final` field có thể bị sửa. Java 21 coi một số final field là không thể sửa qua `AccessibleObject`, gồm `static final`, final field của record và final field của hidden class; bật quyền truy cập có thể cho đọc nhưng không mở quyền ghi cho những field đó.

Vì vậy khi thiết kế code dùng Reflection:

1. ưu tiên hợp đồng public/protected hoặc interface nếu cấu trúc đã biết;
2. chỉ dùng deep reflection ở ranh giới thật sự cần hành vi động;
3. kiểm tra `trySetAccessible()` thay vì giả định quyền truy cập;
4. giữ tên member và logic truy cập tập trung ở một lớp adapter/metadata;
5. coi mức độ mở của module là một phần của hợp đồng tích hợp.

Sau khi hiểu ranh giới truy cập, bước tiếp theo là một loại metadata khác: generic type. Java xóa phần lớn generic type ở runtime để thực thi, nhưng vẫn giữ lại một số signature metadata để Reflection có thể đọc.

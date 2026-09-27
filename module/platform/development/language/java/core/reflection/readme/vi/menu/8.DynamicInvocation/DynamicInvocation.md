# Dynamic Invocation

Ở chương `Methods`, ta đã thấy `Method.invoke()` có thể gọi một method được chọn bằng metadata. Chương này đi sâu vào điều làm lời gọi đó khác lời gọi trực tiếp: trình biên dịch không còn đứng ở call site để chọn overload, kiểm tra target và chuẩn bị argument cho ta. Những quyết định đó chuyển thành công việc runtime.

Với lời gọi trực tiếp:

```java
String result = service.pay(request);
```

Trình biên dịch biết `PaymentService`, biết signature `pay(PaymentRequest)` và có thể báo sai type ngay khi compile. Với lời gọi qua Reflection, chương trình thường nhận class/name/signature từ metadata hoặc cấu hình rồi tự resolve member trước khi invoke.

## <a id="reflective-dispatch">Luồng reflective dispatch</a>

Một lời gọi qua Reflection có thể được nhìn như chuỗi bước:

```text
Class metadata
    ↓ lookup đúng Method
access check
    ↓
kiểm tra target + argument
    ↓
runtime conversion cần thiết
    ↓
dispatch method
    ↓
giá trị trả về hoặc lỗi
```

Ví dụ với mô hình xuyên suốt:

```java
PaymentService service = new PaymentService("stripe");
PaymentRequest request = new PaymentRequest("ORD-42", 150_000L);

Method pay = PaymentService.class.getMethod("pay", PaymentRequest.class);
Object result = pay.invoke(service, request);

System.out.println(result); // stripe:ORD-42
```

`getMethod("pay", PaymentRequest.class)` yêu cầu signature cụ thể. Reflection lookup không nhận một danh sách argument runtime rồi thực hiện toàn bộ overload resolution như trình biên dịch Java. Nếu class có `pay(String)` và `pay(PaymentRequest)`, code động nên xác định signature mong muốn rõ ràng.

Với instance method, `Method.invoke()` vẫn dùng dynamic method lookup trên runtime target. Nghĩa là nếu `Method` đại diện cho method có thể override và target object là instance của subclass override method đó, dispatch vẫn có thể đi tới phần triển khai override. Reflection thay đổi **cách chọn descriptor và ranh giới lời gọi**, không biến virtual dispatch thành static dispatch.

Các lỗi cũng nằm ở các giai đoạn khác nhau:

- lookup sai tên/signature → `NoSuchMethodException`;
- member không accessible → `IllegalAccessException`;
- target sai type, sai số argument hoặc conversion không hợp lệ → `IllegalArgumentException`;
- target `null` cho instance method → `NullPointerException`;
- chính method được gọi throw exception → `InvocationTargetException` bọc exception gốc.

Vì vậy code framework thường tách “không resolve được lời gọi” khỏi “business method đã chạy nhưng thất bại”.

```java
try {
    return pay.invoke(service, request);
} catch (InvocationTargetException ex) {
    Throwable targetFailure = ex.getCause();
    // map theo contract của lớp adapter hiện tại
    throw new IllegalStateException("Target method failed", targetFailure);
}
```

## <a id="argument-conversion">Chuyển đổi argument khi invoke</a>

Signature của `Method.invoke` là `invoke(Object obj, Object... args)`, nên các argument đi qua một `Object[]`. Điều đó không có nghĩa runtime chấp nhận mọi type rồi tự cast tùy ý.

Với primitive formal parameter, Reflection có thể unbox wrapper và thực hiện các chuyển đổi hợp lệ khi gọi method, bao gồm primitive widening. Narrowing conversion không được tự động thực hiện.

Ví dụ với một helper nhỏ:

```java
final class Counter {
    public void record(long value) {
        System.out.println(value);
    }
}

Method record = Counter.class.getMethod("record", long.class);
Counter counter = new Counter();

record.invoke(counter, Integer.valueOf(7)); // int -> long: hợp lệ
record.invoke(counter, Long.valueOf(7));    // long -> long: hợp lệ
record.invoke(counter, null);               // fail: null không unbox thành long
```

Ngược lại, nếu formal parameter là `int`, truyền `Long` sẽ cần narrowing từ `long` xuống `int`; `Method.invoke()` không thực hiện chuyển đổi đó và sẽ ném `IllegalArgumentException`.

Reference argument phải assignable tới formal reference type. `null` hợp lệ cho reference parameter nhưng không hợp lệ cho primitive parameter. Giá trị trả về kiểu primitive được box lại thành wrapper; method `void` trả về `null` qua `invoke()`.

Một ranh giới dễ gây nhầm khác là varargs. `Method.invoke()` bản thân là varargs API, còn method target cũng có thể là varargs. Reflection không thay thế logic của trình biên dịch cho mọi cách viết lời gọi trong mã nguồn; code động nên chuẩn bị mảng argument đúng cấu trúc mà target method thực sự nhận.

Điểm thực hành quan trọng là kiểm tra metadata và argument trước khi gọi nếu đầu vào đến từ cấu hình, plugin hoặc dữ liệu đã serialize. Nếu không, lỗi type sẽ chỉ xuất hiện ở runtime ngay tại `invoke()`.

## <a id="method-handle-boundary">Ranh giới với MethodHandle</a>

`java.lang.invoke.MethodHandle` là cơ chế dynamic invocation lân cận Reflection, nhưng nó giải quyết bài toán ở mức khác.

Với learner của module Reflection, chỉ cần xem `MethodHandle` là một **API lân cận ở mức thấp hơn để giữ và gọi một target động đã được resolve**. Bạn không cần học `MethodType`, `Lookup` hay adaptation sâu để hiểu Reflection; phần dưới chỉ giúp nhận biết ranh giới giữa hai cơ chế.

Core Reflection tập trung vào **khám phá và thao tác metadata**: tìm `Method`, đọc annotation, parameter, modifiers, generic signature rồi có thể invoke. `MethodHandle` đại diện cho một target có thể gọi với `MethodType`; nó hỗ trợ lookup và adaptation cho các trường hợp dynamic linking/invocation cấp thấp hơn.

Một cách hình dung hữu ích:

```text
Reflection
→ “member nào tồn tại và metadata của nó là gì?”
→ Method.invoke(...) khi cần gọi qua descriptor

MethodHandle
→ “tôi đã có một callable target động với type shape nào?”
→ invokeExact/invoke + adaptation khi cần
```

`invokeExact()` yêu cầu call-site type khớp chính xác với `MethodType`; `invoke()` linh hoạt hơn và có thể áp dụng một số adaptation. Quyền truy cập khi tạo handle vẫn đi qua `MethodHandles.Lookup`, nên MethodHandle không phải lối tắt bỏ qua tính đóng gói.

Không nên đổi mọi `Method.invoke()` thành MethodHandle chỉ vì nghe “nhanh hơn”. Nếu nhiệm vụ chính là inspect annotation/generic/member metadata, Reflection vẫn là abstraction tự nhiên. Nếu một hệ thống runtime resolve target có thể gọi một lần rồi invoke lặp lại với type contract được kiểm soát, MethodHandle có thể là cơ chế phù hợp hơn. Chương này chỉ xác định ranh giới; `java.lang.invoke` có mô hình riêng sâu hơn Reflection.

## <a id="dynamic-invocation-design">Thiết kế dynamic invocation</a>

Dynamic invocation đáng dùng khi **target thật sự chỉ được biết ở runtime**. Ví dụ:

- framework chọn handler dựa trên annotation hoặc route metadata;
- serializer/deserializer chọn accessor/constructor theo model class;
- hệ thống plugin load phần triển khai rồi invoke một contract được phát hiện;
- test/tooling cần inspect và gọi code theo tên/signature.

Nếu code đã biết `PaymentService` và luôn cần gọi `pay(PaymentRequest)`, lời gọi trực tiếp đơn giản, type-safe và dễ refactor hơn.

Khi dynamic invocation là yêu cầu thật, nên thu hẹp nó thành một ranh giới có kiểm soát:

```java
final class PaymentInvoker {
    private final Method payMethod;

    PaymentInvoker(Class<?> serviceType) throws NoSuchMethodException {
        this.payMethod = serviceType.getMethod("pay", PaymentRequest.class);
    }

    String invoke(Object service, PaymentRequest request) {
        try {
            return (String) payMethod.invoke(service, request);
        } catch (InvocationTargetException ex) {
            throw new IllegalStateException("Payment target failed", ex.getCause());
        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
            throw new IllegalStateException("Payment invocation contract is invalid", ex);
        }
    }
}
```

Ở đây string lookup và cast tập trung tại adapter; phần còn lại của ứng dụng có thể dùng API typed. `Method` được resolve một lần thay vì lookup lại cho mỗi request. Việc cache như vậy giảm metadata lookup lặp lại, nhưng không làm mất các đánh đổi khác của Reflection.

Chương tiếp theo gom các đánh đổi đó lại: mất compile-time safety, phụ thuộc vào phần nội bộ `private`, runtime overhead, native-image reachability và khả năng refactor.

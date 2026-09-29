# Reflection với Method

Field reflection cho phép code thao tác với trạng thái được phát hiện ở runtime. Method reflection làm điều tương tự với **hành vi**: tìm method theo metadata, đọc signature của nó rồi gọi method mà không cần viết sẵn một Java call expression trực tiếp.

Ta tiếp tục với model chung:

~~~java
public class PaymentService {
    private final String provider;
    private int processedCount;

    public PaymentService(String provider) {
        this.provider = provider;
    }

    public String pay(PaymentRequest request) throws PaymentException {
        processedCount++;
        return provider + ":" + request.orderId();
    }

    private String internalStatus() {
        return provider + ":" + processedCount;
    }
}
~~~

Một framework thường không bắt đầu bằng Method.invoke(...). Nó phải giải quyết ba câu hỏi trước: **method nào**, **signature nào**, và **receiver/argument nào phù hợp**. Invocation chỉ là bước cuối.

## <a id="method-discovery">Tìm đúng Method</a>

Method được chọn bằng tên và parameter types:

~~~java
Method pay = PaymentService.class.getMethod(
        "pay",
        PaymentRequest.class
);

Method internalStatus = PaymentService.class.getDeclaredMethod(
        "internalStatus"
);
~~~

getMethod(...) tìm public method trên public surface và có thể thấy method inherited. getDeclaredMethod(...) tìm method được khai báo trực tiếp trên class hiện tại với mọi visibility, nhưng không tự tìm inherited method.

Ví dụ:

~~~java
class BaseService {
    public String serviceId() {
        return "base";
    }
}

class PaymentService extends BaseService {
    private String internalStatus() {
        return "ok";
    }
}
~~~

~~~text
PaymentService.class.getMethod("serviceId")
→ tìm thấy public method inherited

PaymentService.class.getDeclaredMethod("serviceId")
→ NoSuchMethodException

PaymentService.class.getDeclaredMethod("internalStatus")
→ tìm thấy private method descriptor
~~~

Với overload, parameter types là phần bắt buộc để chọn đúng method:

~~~java
class PaymentFormatter {
    String format(long amount) { return Long.toString(amount); }
    String format(String amount) { return amount; }
}

Method a = PaymentFormatter.class.getDeclaredMethod("format", long.class);
Method b = PaymentFormatter.class.getDeclaredMethod("format", String.class);
~~~

int.class và Integer.class là hai Class object khác nhau. Vì vậy:

~~~java
PaymentFormatter.class.getDeclaredMethod("format", Long.class);
// NoSuchMethodException nếu không có overload nhận Long
~~~

Discovery yêu cầu **declared parameter types chính xác**, không thực hiện overload resolution kiểu compiler cho boxing, widening hay “method gần giống nhất”.

Khi enumerate:

~~~java
Method[] declared = PaymentService.class.getDeclaredMethods();
Method[] publicMethods = PaymentService.class.getMethods();
~~~

Đừng phụ thuộc vào thứ tự array. Compiler còn có thể tạo synthetic/bridge method, đặc biệt quanh generics và override. isSynthetic() và isBridge() giúp framework nhận diện chúng khi chính sách cần lọc.

Public instance method từ superinterface có thể xuất hiện qua `getMethod()`/`getMethods()`, nhưng `static` method của interface **không được kế thừa** bởi implementing class hoặc subinterface. Muốn reflect một static interface method, hãy lookup trên chính interface khai báo nó.

## <a id="method-signature-metadata">Đọc metadata của method signature</a>

Method descriptor chứa nhiều hơn tên:

~~~java
Method method = PaymentService.class.getMethod(
        "pay",
        PaymentRequest.class
);

String name = method.getName();
Class<?> declaringClass = method.getDeclaringClass();
Class<?> returnType = method.getReturnType();
Class<?>[] parameterTypes = method.getParameterTypes();
Class<?>[] exceptionTypes = method.getExceptionTypes();
int modifiers = method.getModifiers();
~~~

`getDeclaringClass()` trả class/interface **thực sự khai báo method descriptor đó**. Điều này khác với class mà framework bắt đầu lookup: `PaymentService.class.getMethod(...)` có thể trả một public method được kế thừa từ superclass/interface, và khi đó declaring class không nhất thiết là `PaymentService`.

Với pay(...), framework có thể kiểm tra contract trước khi invoke:

~~~java
if (method.getReturnType() != String.class) {
    throw new IllegalStateException("pay must return String");
}

if (!Modifier.isPublic(method.getModifiers())) {
    throw new IllegalStateException("pay must be public");
}
~~~

Generic signature có cặp API riêng:

~~~java
method.getGenericReturnType();
method.getGenericParameterTypes();
method.getGenericExceptionTypes();
~~~

Các API này trả Type thay vì chỉ Class<?> và sẽ được đào sâu ở Generic Type Inspection.

Metadata của parameter:

~~~java
for (Parameter parameter : method.getParameters()) {
    System.out.println(parameter.getName());
    System.out.println(parameter.getType());
}
~~~

Tên parameter source-level **không mặc định luôn được giữ**. Khi class không được compile với metadata phù hợp như option -parameters, Reflection có thể chỉ thấy tên tổng hợp kiểu arg0. Framework không nên dựa vào parameter name nếu build contract không đảm bảo nó tồn tại.

Method còn cung cấp các property hữu ích:

~~~java
method.isVarArgs();
method.isDefault();
method.isBridge();
method.isSynthetic();
~~~

isDefault() hữu ích với default method của interface. isBridge()/isSynthetic() cảnh báo rằng method có thể là artifact compiler thay vì declaration mà người viết source trực tiếp tạo.

## <a id="method-invoke">Gọi Method ở runtime</a>

Sau khi đã chọn đúng Method, invoke(receiver, args...) thực hiện lời gọi:

~~~java
PaymentService service = new PaymentService("stripe");
PaymentRequest request = new PaymentRequest("ORD-1", 100_000);

Method method = PaymentService.class.getMethod(
        "pay",
        PaymentRequest.class
);

Object result = method.invoke(service, request);
String paymentId = (String) result;
~~~

Method.invoke trả Object. Primitive return value sẽ được boxing; method trả void cho kết quả null.

Receiver phải tương thích với declaring class đối với instance method. Nếu method là static, receiver bị bỏ qua và thường truyền null:

~~~java
Method parse = Long.class.getMethod("parseLong", String.class);
Object value = parse.invoke(null, "100");
~~~

Argument invocation có thể thực hiện unboxing và primitive widening tương thích với quy tắc của Reflection, nhưng không chạy toàn bộ Java compile-time overload resolution. Nếu descriptor đã là method nhận long, Integer có thể được unbox/widen phù hợp; một String "100" sẽ không tự chuyển thành long.

Sai receiver, sai số lượng argument hoặc argument không convert được dẫn tới IllegalArgumentException. Method không accessible từ bên gọi có thể dẫn tới IllegalAccessException. Cách thay đổi reflective access thuộc chương Access Control.

Một pattern tốt cho framework là tách rõ discovery khỏi invocation:

~~~java
Method payMethod = resolvePaymentMethod(PaymentService.class);

// validate/cached một lần

Object result = payMethod.invoke(service, request);
~~~

Điều này tránh lookup bằng string ở mọi request và gom lỗi cấu hình về giai đoạn bootstrap thay vì để rải rác trong business flow.

## <a id="invocation-exception">InvocationTargetException: lỗi của Reflection hay lỗi của target?</a>

Một distinction quan trọng: lỗi có thể xảy ra **trước khi method body chạy**, hoặc **bên trong chính method body**.

Giả sử pay(...) từ chối amount không hợp lệ:

~~~java
public String pay(PaymentRequest request) throws PaymentException {
    if (request.amount() <= 0) {
        throw new PaymentException("amount must be positive");
    }
    processedCount++;
    return provider + ":" + request.orderId();
}
~~~

Gọi trực tiếp:

~~~java
service.pay(request);
~~~

sẽ ném PaymentException theo contract method.

Gọi qua Method.invoke(...):

~~~java
try {
    method.invoke(service, request);
} catch (InvocationTargetException ex) {
    Throwable targetFailure = ex.getCause();
}
~~~

Exception do method body ném được bọc trong InvocationTargetException. Wrapper này tạo một ranh giới rõ:

~~~text
NoSuchMethodException
→ discovery thất bại

IllegalAccessException
→ reflective access không được phép

IllegalArgumentException
→ receiver/argument không phù hợp

InvocationTargetException
→ method đã được gọi, target code ném exception
~~~

Framework thường nên unwrap cause, rồi chuyển nó theo chính sách của framework. Không nên log mỗi InvocationTargetException và bỏ mất business exception thật ở getCause().

Cũng cần tránh kết luận rằng mọi RuntimeException khi reflective call đều nằm ngoài wrapper. Nếu exception phát sinh từ chính target method, invoke biểu diễn nó qua InvocationTargetException; các lỗi do reflection protocol của bên gọi vẫn có loại lỗi riêng.

## <a id="varargs-reflection">Varargs qua Reflection</a>

Varargs dễ gây nhầm vì có **hai lớp varargs**: target method có thể là varargs, đồng thời Method.invoke(...) bản thân cũng nhận Object... args.

Ví dụ:

~~~java
public String summarize(String prefix, String... values) {
    return prefix + ":" + String.join(",", values);
}
~~~

Reflection nhìn target signature như một method có parameter cuối là array:

~~~java
Method method = PaymentService.class.getMethod(
        "summarize",
        String.class,
        String[].class
);

System.out.println(method.isVarArgs()); // true
~~~

Khi invoke, bên gọi phải cung cấp array đúng ở vị trí cuối:

~~~java
Object result = method.invoke(
        service,
        "payments",
        new String[] {"A", "B"}
);
~~~

Reflection không tự chạy target-level varargs packing giống cú pháp gọi method thông thường. Với target chỉ có một parameter String...:

~~~java
public String join(String... values) { ... }
~~~

nên làm rõ String[] là **một reflective argument**:

~~~java
Method join = PaymentService.class.getMethod(
        "join",
        String[].class
);

join.invoke(service, (Object) new String[] {"A", "B"});
~~~

Cast sang Object ngăn Java compiler hiểu String[] như chính Object... của Method.invoke và bung nó thành nhiều reflective argument.

Khi viết utility gọi reflection, hãy dùng isVarArgs() như metadata để normalize argument theo quy tắc của chính utility, thay vì giả định Method.invoke sẽ thay compiler xử lý mọi varargs case.

Sau Method, mảnh còn thiếu để framework hoàn tất lifecycle object là **Constructor**: nếu concrete class chỉ được biết ở runtime, object ban đầu được tạo thế nào?

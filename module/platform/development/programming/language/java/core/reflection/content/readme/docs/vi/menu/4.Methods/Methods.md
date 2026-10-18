# Khám phá Method và chữ ký phương thức

Sau `Field`, milestone **Khám phá các thành phần của lớp** chuyển sang **hành vi**: tìm `Method` theo siêu dữ liệu và đọc chữ ký của nó mà không cần biết trước method cụ thể trong mã nguồn. Việc thực sự gọi `Method.invoke(...)` được dành cho milestone **Thao tác động bằng siêu dữ liệu**.

Ta tiếp tục với mô hình chung:

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

Một framework thường không bắt đầu bằng `Method.invoke(...)`. Nó phải giải quyết ba câu hỏi trước: **method nào**, **chữ ký nào**, và **đối tượng nhận/đối số nào phù hợp**. Lời gọi chỉ là bước cuối.

## <a id="method-discovery">Tìm đúng Method</a>

Method được chọn bằng tên và kiểu tham số:

~~~java
Method pay = PaymentService.class.getMethod(
        "pay",
        PaymentRequest.class
);

Method internalStatus = PaymentService.class.getDeclaredMethod(
        "internalStatus"
);
~~~

`getMethod(...)` tìm public method trên bề mặt public và có thể thấy method được kế thừa. `getDeclaredMethod(...)` tìm method được khai báo trực tiếp trên class hiện tại với mọi mức truy cập, nhưng không tự tìm method được kế thừa.

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
→ tìm thấy public method được kế thừa

PaymentService.class.getDeclaredMethod("serviceId")
→ NoSuchMethodException

PaymentService.class.getDeclaredMethod("internalStatus")
→ tìm thấy đối tượng mô tả method private
~~~

Với overload, kiểu tham số là phần bắt buộc để chọn đúng method:

~~~java
class PaymentFormatter {
    String format(long amount) { return Long.toString(amount); }
    String format(String amount) { return amount; }
}

Method a = PaymentFormatter.class.getDeclaredMethod("format", long.class);
Method b = PaymentFormatter.class.getDeclaredMethod("format", String.class);
~~~

`int.class` và `Integer.class` là hai đối tượng `Class` khác nhau. Vì vậy:

~~~java
PaymentFormatter.class.getDeclaredMethod("format", Long.class);
// NoSuchMethodException nếu không có overload nhận Long
~~~

Việc khám phá yêu cầu **kiểu tham số đã khai báo phải chính xác**, không thực hiện cơ chế chọn overload kiểu trình biên dịch cho đóng hộp (boxing), mở rộng primitive (widening) hay “method gần giống nhất”.

Khi cần liệt kê:

~~~java
Method[] declared = PaymentService.class.getDeclaredMethods();
Method[] publicMethods = PaymentService.class.getMethods();
~~~

Đừng phụ thuộc vào thứ tự mảng. Trình biên dịch còn có thể tạo synthetic/bridge method, đặc biệt quanh generics và override. `isSynthetic()` và `isBridge()` giúp framework nhận diện chúng khi chính sách cần lọc.

Public instance method từ superinterface có thể xuất hiện qua `getMethod()`/`getMethods()`, nhưng `static` method của interface **không được kế thừa** bởi class triển khai hoặc subinterface. Muốn lấy một static interface method bằng Reflection, hãy tra cứu trên chính interface khai báo nó.

## <a id="method-signature-metadata">Siêu dữ liệu chữ ký của Method</a>

Đối tượng mô tả `Method` chứa nhiều hơn tên:

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

`getDeclaringClass()` trả class/interface **thực sự khai báo method đó**. Điều này khác với class mà framework bắt đầu tra cứu: `PaymentService.class.getMethod(...)` có thể trả một public method được kế thừa từ superclass/interface, và khi đó class khai báo không nhất thiết là `PaymentService`.

Với `pay(...)`, framework có thể kiểm tra hợp đồng trước khi gọi:

~~~java
if (method.getReturnType() != String.class) {
    throw new IllegalStateException("pay must return String");
}

if (!Modifier.isPublic(method.getModifiers())) {
    throw new IllegalStateException("pay must be public");
}
~~~

Chữ ký generic có nhóm API riêng:

~~~java
method.getGenericReturnType();
method.getGenericParameterTypes();
method.getGenericExceptionTypes();
~~~

Các API này trả `Type` thay vì chỉ `Class<?>` và sẽ được đào sâu ở chương **Siêu dữ liệu Generics còn lại sau xóa kiểu**.

Siêu dữ liệu của tham số:

~~~java
for (Parameter parameter : method.getParameters()) {
    System.out.println(parameter.getName());
    System.out.println(parameter.getType());
}
~~~

Tên tham số ở cấp mã nguồn **không mặc định luôn được giữ**. Khi class không được biên dịch với siêu dữ liệu phù hợp như tùy chọn `-parameters`, Reflection có thể chỉ thấy tên tổng hợp kiểu `arg0`. Framework không nên dựa vào tên tham số nếu hợp đồng build không đảm bảo nó tồn tại.

Method còn cung cấp các đặc tính hữu ích:

~~~java
method.isVarArgs();
method.isDefault();
method.isBridge();
method.isSynthetic();
~~~

`isDefault()` hữu ích với default method của interface. `isBridge()`/`isSynthetic()` cảnh báo rằng method có thể là phần tử do trình biên dịch tạo thay vì khai báo mà người viết mã nguồn trực tiếp tạo.

Sau Field và Method, phần khám phá còn thiếu là **Constructor**: lúc chạy có những constructor nào, tham số/ngoại lệ/modifier của chúng ra sao, và framework chọn đối tượng mô tả nào trước khi tạo đối tượng?

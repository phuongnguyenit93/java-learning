# Khám phá Constructor và siêu dữ liệu khởi tạo

Field và Method đã mô tả trạng thái và hành vi. `Constructor` hoàn tất milestone **Khám phá các thành phần của lớp** bằng câu hỏi: **kiểu lúc chạy này khai báo những constructor nào và siêu dữ liệu nào giúp framework chọn đúng đối tượng mô tả?** Việc gọi `Constructor.newInstance(...)` được chuyển sang milestone **Thao tác động bằng siêu dữ liệu**.

Đây là nhu cầu rất phổ biến ở dependency injection container, hệ thống plugin, serializer/deserializer và quá trình khởi động framework. Framework nhận `Class<?>`, khám phá constructor rồi chọn đối tượng mô tả theo quy tắc của nó trước khi bước sang giai đoạn tạo đối tượng.

Mẫu xuyên suốt của mô-đun vẫn là:

~~~java
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
~~~

Riêng chương này ta thêm một overload để thấy cách chọn constructor:

~~~java
public PaymentService(String provider, int initialProcessedCount) {
    this.provider = provider;
    this.processedCount = initialProcessedCount;
}
~~~

## <a id="constructor-discovery">Khám phá constructor</a>

Constructor không được kế thừa, nên việc khám phá đơn giản hơn hệ phân cấp method nhưng vẫn có hai góc nhìn public/declared:

~~~java
Constructor<PaymentService> publicCtor =
        PaymentService.class.getConstructor(String.class);

Constructor<PaymentService> declaredCtor =
        PaymentService.class.getDeclaredConstructor(
                String.class,
                int.class
        );
~~~

`getConstructor(...)` chỉ tìm public constructor của chính class. `getDeclaredConstructor(...)` tìm constructor được khai báo trực tiếp với mọi mức truy cập.

Kiểu tham số phải khớp khai báo chính xác:

~~~java
PaymentService.class.getDeclaredConstructor(
        String.class,
        Integer.class
);
// NoSuchMethodException nếu constructor thực tế nhận int
~~~

Giống việc khám phá Method, tra cứu không chạy cơ chế chọn overload kiểu trình biên dịch và không coi `Integer.class` tương đương `int.class`.

Khi liệt kê:

~~~java
Constructor<?>[] publicCtors =
        PaymentService.class.getConstructors();

Constructor<?>[] allDeclaredCtors =
        PaymentService.class.getDeclaredConstructors();
~~~

Không nên chọn phần tử đầu tiên làm constructor mặc định của framework. Thứ tự Reflection không phải hợp đồng của mã nguồn. Dependency injection container nên có quy tắc rõ ràng, ví dụ annotation đánh dấu constructor, một constructor duy nhất, hoặc một chính sách được mô tả rõ.

Một lỗi rất thường gặp là giả định mọi class có no-arg constructor:

~~~java
PaymentService.class.getDeclaredConstructor();
// NoSuchMethodException với mẫu PaymentService hiện tại
~~~

Java chỉ sinh constructor mặc định không tham số khi mã nguồn không khai báo constructor nào. Chỉ cần class khai báo `PaymentService(String provider)`, trình biên dịch không tự thêm `PaymentService()`.

Inner class không static còn có một ranh giới khác: constructor lúc chạy thường có thêm tham số cho instance bao ngoài. Vì vậy chữ ký Reflection có thể chứa tham số mà lời gọi trong mã nguồn trông không giống hệt.

## <a id="constructor-metadata">Siêu dữ liệu của Constructor</a>

`Constructor<?>` có siêu dữ liệu gần với Method nhưng không có kiểu trả về:

~~~java
Constructor<PaymentService> constructor =
        PaymentService.class.getConstructor(String.class);

Class<PaymentService> owner = constructor.getDeclaringClass();
Class<?>[] parameterTypes = constructor.getParameterTypes();
Type[] genericParameterTypes = constructor.getGenericParameterTypes();
Class<?>[] exceptionTypes = constructor.getExceptionTypes();
int modifiers = constructor.getModifiers();
boolean varArgs = constructor.isVarArgs();
boolean synthetic = constructor.isSynthetic();
~~~

Siêu dữ liệu tham số cũng có thể đọc qua `getParameters()`:

~~~java
for (Parameter parameter : constructor.getParameters()) {
    System.out.println(parameter.getName());
    System.out.println(parameter.getType());
}
~~~

Giống tham số của method, tên ở cấp mã nguồn chỉ đáng tin khi quá trình biên dịch giữ siêu dữ liệu tên tham số; nếu không, lúc chạy có thể chỉ thấy `arg0`, `arg1`...

Framework có thể dùng siêu dữ liệu để xây quy tắc chọn constructor:

~~~java
static boolean acceptsProvider(Constructor<?> constructor) {
    return Arrays.equals(
            constructor.getParameterTypes(),
            new Class<?>[] {String.class}
    );
}
~~~

Constructor có thể khai báo checked exception:

~~~java
public PaymentService(String provider)
        throws ConfigurationException {
    // ...
}
~~~

`getExceptionTypes()` cho phép công cụ thấy khai báo đó. Nếu ngoại lệ thực sự bị ném khi `newInstance(...)`, nó sẽ xuất hiện như nguyên nhân (`cause`) của `InvocationTargetException`.

Varargs constructor cũng được Reflection biểu diễn bằng mảng ở tham số cuối và `isVarArgs() == true`. Đến đây người học đã hoàn thành **việc khám phá** `Field`, `Method` và `Constructor`; chương tiếp theo dùng chính các đối tượng mô tả đó để đọc/ghi field, gọi method và tạo đối tượng lúc chạy.

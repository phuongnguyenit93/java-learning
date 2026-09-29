# Reflection với Constructor

Method reflection xử lý hành vi của một object đã tồn tại. Constructor reflection giải bài toán xảy ra sớm hơn: **làm sao tạo object khi concrete class và constructor cần dùng chỉ được biết ở runtime?**

Đây là nhu cầu rất phổ biến ở dependency injection container, plugin system, serializer/deserializer và framework bootstrapping. Framework nhận Class<?> rồi chọn constructor theo quy tắc của nó, thay vì source code viết trực tiếp new PaymentService(...).

Baseline của module vẫn là:

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

Riêng chương này ta thêm một overload để thấy constructor selection:

~~~java
public PaymentService(String provider, int initialProcessedCount) {
    this.provider = provider;
    this.processedCount = initialProcessedCount;
}
~~~

## <a id="constructor-discovery">Khám phá constructor</a>

Constructor không được kế thừa, nên discovery đơn giản hơn hệ phân cấp method nhưng vẫn có hai public/declared view:

~~~java
Constructor<PaymentService> publicCtor =
        PaymentService.class.getConstructor(String.class);

Constructor<PaymentService> declaredCtor =
        PaymentService.class.getDeclaredConstructor(
                String.class,
                int.class
        );
~~~

getConstructor(...) chỉ tìm public constructor của chính class. getDeclaredConstructor(...) tìm constructor được khai báo trực tiếp với mọi visibility.

Parameter types phải khớp declaration chính xác:

~~~java
PaymentService.class.getDeclaredConstructor(
        String.class,
        Integer.class
);
// NoSuchMethodException nếu constructor thực tế nhận int
~~~

Giống Method discovery, lookup không chạy overload resolution kiểu compiler và không coi Integer.class tương đương int.class.

Khi enumerate:

~~~java
Constructor<?>[] publicCtors =
        PaymentService.class.getConstructors();

Constructor<?>[] allDeclaredCtors =
        PaymentService.class.getDeclaredConstructors();
~~~

Không nên chọn phần tử đầu tiên làm default constructor của framework. Thứ tự reflection không phải contract source. Dependency injection container nên có quy tắc rõ ràng, ví dụ annotation đánh dấu constructor, một constructor duy nhất, hoặc một chính sách được mô tả rõ.

Một lỗi rất thường gặp là giả định mọi class có no-arg constructor:

~~~java
PaymentService.class.getDeclaredConstructor();
// NoSuchMethodException với baseline PaymentService hiện tại
~~~

Java chỉ sinh default no-arg constructor khi source không khai báo constructor nào. Chỉ cần class khai báo PaymentService(String provider), compiler không tự thêm PaymentService().

Non-static inner class còn có một ranh giới khác: constructor runtime thường có thêm parameter cho enclosing instance. Vì vậy signature reflection có thể chứa parameter mà source invocation trông không giống hệt.

## <a id="constructor-newinstance">Tạo object bằng Constructor.newInstance</a>

Khi đã chọn được descriptor:

~~~java
Constructor<PaymentService> constructor =
        PaymentService.class.getConstructor(String.class);

PaymentService service = constructor.newInstance("stripe");
~~~

Đây là reflective equivalent của:

~~~java
PaymentService service = new PaymentService("stripe");
~~~

Điểm khác biệt là constructor có thể được chọn từ runtime metadata:

~~~java
static <T> T create(
        Class<T> type,
        Class<?>[] parameterTypes,
        Object[] arguments
) throws ReflectiveOperationException {
    Constructor<T> constructor =
            type.getDeclaredConstructor(parameterTypes);
    return constructor.newInstance(arguments);
}
~~~

Trong framework thật, trước khi newInstance(...) thường có bước validate chính sách chọn constructor, access và dependency resolution.

Argument có thể trải qua unboxing/widening primitive conversion phù hợp với reflective invocation. Nhưng bên gọi vẫn phải cung cấp số lượng và kiểu argument hợp lệ; Reflection không parse hay convert domain value tùy ý.

Nên ưu tiên Constructor.newInstance(...) thay cho Class.newInstance(). Class.newInstance() đã deprecated vì contract kém rõ hơn, chỉ nhắm tới no-arg constructor và xử lý exception của constructor theo cách khó dùng an toàn hơn. Constructor descriptor biểu diễn chính xác constructor đã chọn và bọc lỗi của target qua InvocationTargetException giống Method.invoke(...).

Reflective construction **vẫn chạy constructor thật**. Nó không phải cơ chế allocate object rồi bỏ qua initialization logic:

~~~java
public PaymentService(String provider) {
    if (provider == null || provider.isBlank()) {
        throw new IllegalArgumentException("provider is required");
    }
    this.provider = provider;
}
~~~

constructor.newInstance("") sẽ thực thi validation này.

## <a id="constructor-metadata">Metadata của constructor</a>

Constructor<?> có metadata gần với Method nhưng không có return type:

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

Parameter metadata cũng có thể đọc qua getParameters():

~~~java
for (Parameter parameter : constructor.getParameters()) {
    System.out.println(parameter.getName());
    System.out.println(parameter.getType());
}
~~~

Giống method parameter, tên source-level chỉ đáng tin khi build giữ parameter-name metadata; nếu không, runtime có thể trả arg0, arg1...

Framework có thể dùng metadata để xây quy tắc chọn constructor:

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

getExceptionTypes() cho phép tooling thấy declaration đó. Nếu exception thực sự bị ném khi newInstance(...), nó sẽ xuất hiện như cause của InvocationTargetException.

Varargs constructor cũng được reflection biểu diễn bằng array ở parameter cuối và isVarArgs() == true. Các lưu ý về outer Object.../array tương tự Method invocation.

## <a id="constructor-reflection-failure">Các lỗi khi tạo object phản chiếu</a>

Constructor reflection có nhiều giai đoạn có thể phát sinh lỗi. Tách chúng ra giúp framework báo đúng nguyên nhân thay vì gom tất cả thành “Reflection thất bại”.

| Lỗi | Ý nghĩa |
| --- | --- |
| NoSuchMethodException | Không tìm thấy constructor đúng parameter types |
| IllegalAccessException | Constructor tồn tại nhưng reflective access không được phép |
| InstantiationException | Declaring class không thể được instantiate theo contract, ví dụ abstract class |
| IllegalArgumentException | Sai số lượng/kiểu argument, hoặc target thuộc case Reflection cấm instantiate như enum |
| InvocationTargetException | Constructor body đã chạy và ném exception |
| ExceptionInInitializerError | Class initialization được kích hoạt và static initialization thất bại |

Ví dụ phân biệt lỗi giao thức Reflection với lỗi nghiệp vụ:

~~~java
Constructor<PaymentService> constructor =
        PaymentService.class.getConstructor(String.class);

try {
    PaymentService service = constructor.newInstance("");
} catch (InvocationTargetException ex) {
    Throwable constructorFailure = ex.getCause();
    // Ví dụ: IllegalArgumentException("provider is required")
}
~~~

Nếu bên gọi truyền Integer thay vì String:

~~~java
constructor.newInstance(123);
~~~

thì lỗi nằm ở reflective invocation contract, không phải constructor body.

Enum là case đặc biệt: enum instance do JVM quản lý theo declaration; Constructor.newInstance không cho phép tạo thêm enum constant.

Private/package-private constructor vẫn có thể được **discover** qua getDeclaredConstructor(...), nhưng discover không đồng nghĩa được invoke. Việc có thể mở reflective access hay không phụ thuộc ranh giới ngôn ngữ/module và chính sách runtime. Chương Access Control sẽ tiếp nối trực tiếp từ đây.

Sau năm chương đầu, ta đã có flow hoàn chỉnh ở mức cơ bản:

~~~text
Class<?> được lấy ở runtime
    ↓
đọc metadata của type
    ↓
tìm Field / Method / Constructor
    ↓
đọc trạng thái, gọi hành vi hoặc tạo object
    ↓
gặp boundary access / generic metadata / dynamic invocation
~~~

Bước tiếp theo vì vậy là câu hỏi Reflection dễ bị hiểu sai nhất: **tìm thấy private member có đồng nghĩa Reflection được quyền dùng nó hay không?**

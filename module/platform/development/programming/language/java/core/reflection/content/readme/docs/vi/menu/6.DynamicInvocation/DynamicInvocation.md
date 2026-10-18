# Thao tác động bằng siêu dữ liệu

Ba chương trước đã **khám phá** `Field`, `Method` và `Constructor` cùng siêu dữ liệu của chúng. Bây giờ milestone này dùng các đối tượng mô tả đã chọn để thực sự **đọc/ghi trạng thái, gọi hành vi và tạo đối tượng**. Khi siêu dữ liệu quyết định thao tác lúc chạy, trình biên dịch không còn chuẩn bị toàn bộ việc kiểm tra kiểu, chọn overload hay hình dạng đối số cho ta.

Với lời gọi trực tiếp:

```java
String result = service.pay(request);
```

Trình biên dịch biết `PaymentService`, biết chữ ký `pay(PaymentRequest)` và có thể báo sai kiểu ngay khi biên dịch. Với lời gọi qua Reflection, chương trình thường nhận class/tên/chữ ký từ siêu dữ liệu hoặc cấu hình rồi tự phân giải thành phần trước khi gọi.

## <a id="field-read-write">Đọc và ghi giá trị qua Field</a>

Khi đã có `Field`, `get(...)` và `set(...)` thao tác trên một đối tượng cụ thể. Trước tiên hãy dùng một field `public` để quan sát cơ chế mà chưa vướng kiểm soát truy cập:

~~~java
class Counter {
    public int value = 1;
}

Counter counter = new Counter();
Field publicField = Counter.class.getField("value");

System.out.println(publicField.get(counter)); // 1
publicField.set(counter, 5);
System.out.println(counter.value);            // 5
~~~

Ở đây `publicField` là đối tượng mô tả cho `value`, còn `counter` là đối tượng nhận chứa trạng thái thực tế.

Với mô hình ví dụ xuyên suốt, `processedCount` là `private`, nên việc khám phá vẫn thành công nhưng thao tác giá trị còn phụ thuộc quyền truy cập:

~~~java
PaymentService service = new PaymentService("stripe");
Field field = PaymentService.class.getDeclaredField("processedCount");

// Hai dòng sau chỉ thành công nếu quyền truy cập phản chiếu được phép.
Object value = field.get(service);
field.set(service, 5);
~~~

Việc tách hai ví dụ này là có chủ ý: **`getDeclaredField()` tìm thấy thành phần không có nghĩa `get()/set()` được quyền dùng thành phần đó**. Chương **Quyền truy cập, đóng gói và giới hạn mô-đun** sẽ quay lại đúng ranh giới này với `canAccess(...)` và `trySetAccessible()`.

`Field.get(...)` trả `Object`. Với field primitive, giá trị được đóng hộp (boxing):

~~~java
int processedCount = (Integer) field.get(service);
~~~

`Field` có các API chuyên biệt như `getInt/setInt`, `getBoolean/setBoolean` để tránh ép kiểu ở bên gọi:

~~~java
int count = field.getInt(service);
field.setInt(service, 5);
~~~

Đối với static field, giá trị thuộc class thay vì một instance. Đối tượng nhận truyền vào `get/set` bị bỏ qua; quy ước dễ đọc nhất là truyền `null`:

~~~java
Field channel = PaymentService.class.getField("CHANNEL");
Object value = channel.get(null);
~~~

Với instance field, đối tượng nhận phải là instance tương thích với class khai báo field. Sai đối tượng nhận hoặc sai kiểu giá trị có thể dẫn tới `IllegalArgumentException`.

Reflection thực hiện một số chuyển đổi mở hộp (unboxing) và mở rộng primitive (widening) hợp lệ cho field primitive, nhưng không biến mọi giá trị thành kiểu đích. Ví dụ `setInt` chỉ phù hợp với field primitive có thể nhận `int` theo quy tắc của API; một `String` "5" không tự được chuyển thành số.

Mô hình cần ghi nhớ:

~~~text
Đối tượng mô tả Field
    +
đối tượng nhận (nếu là instance field)
    +
giá trị phù hợp (nếu set)
    ↓
truy cập lúc chạy
~~~

Nếu field private, việc đối tượng mô tả đã tồn tại không tự bỏ qua quy tắc truy cập. `IllegalAccessException` là lỗi bình thường khi bên gọi không có quyền truy cập phản chiếu. Chương **Quyền truy cập, đóng gói và giới hạn mô-đun** sẽ giải thích `canAccess(...)`, `trySetAccessible()` và JPMS.

## <a id="method-invoke">Gọi Method lúc chạy</a>

Sau khi đã chọn đúng `Method`, `invoke(receiver, args...)` thực hiện lời gọi:

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

`Method.invoke()` trả `Object`. Giá trị trả về primitive sẽ được boxing; method trả `void` cho kết quả `null`.

Đối tượng nhận phải tương thích với class khai báo đối với instance method. Nếu method là static, đối tượng nhận bị bỏ qua và thường truyền `null`:

~~~java
Method parse = Long.class.getMethod("parseLong", String.class);
Object value = parse.invoke(null, "100");
~~~

Lời gọi có thể thực hiện unboxing và primitive widening tương thích với quy tắc của Reflection, nhưng không chạy lại toàn bộ cơ chế chọn overload tại thời điểm biên dịch của Java. Nếu đối tượng mô tả đã là method nhận `long`, `Integer` có thể được mở hộp/mở rộng phù hợp; một `String` "100" sẽ không tự chuyển thành `long`.

Sai đối tượng nhận, sai số lượng đối số hoặc đối số không chuyển đổi được dẫn tới `IllegalArgumentException`. Method không thể truy cập từ bên gọi có thể dẫn tới `IllegalAccessException`. Cách thay đổi quyền truy cập phản chiếu thuộc chương **Quyền truy cập, đóng gói và giới hạn mô-đun**.

Một cách tổ chức tốt cho framework là tách rõ bước khám phá khỏi bước gọi:

~~~java
Method payMethod = resolvePaymentMethod(PaymentService.class);

// kiểm tra/lưu đệm một lần

Object result = payMethod.invoke(service, request);
~~~

Điều này tránh tra cứu bằng chuỗi ở mọi yêu cầu và gom lỗi cấu hình về giai đoạn khởi động thay vì để rải rác trong luồng nghiệp vụ.

## <a id="invocation-exception">InvocationTargetException: lỗi của Reflection hay lỗi của mã đích?</a>

Một phân biệt quan trọng: lỗi có thể xảy ra **trước khi thân method chạy**, hoặc **bên trong chính thân method**.

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

sẽ ném `PaymentException` theo hợp đồng của method.

Gọi qua Method.invoke(...):

~~~java
try {
    method.invoke(service, request);
} catch (InvocationTargetException ex) {
    Throwable targetFailure = ex.getCause();
}
~~~

Ngoại lệ do thân method ném được bọc trong `InvocationTargetException`. Lớp bọc này tạo một ranh giới rõ:

~~~text
NoSuchMethodException
→ khám phá thất bại

IllegalAccessException
→ quyền truy cập phản chiếu không được phép

IllegalArgumentException
→ đối tượng nhận/đối số không phù hợp

InvocationTargetException
→ method đã được gọi, mã đích ném ngoại lệ
~~~

Framework thường nên lấy `cause` gốc rồi chuyển nó theo chính sách của framework. Không nên chỉ ghi log `InvocationTargetException` và bỏ mất ngoại lệ nghiệp vụ thật ở `getCause()`.

Cũng cần tránh kết luận rằng mọi `RuntimeException` khi gọi phản chiếu đều nằm ngoài lớp bọc. Nếu ngoại lệ phát sinh từ chính method đích, `invoke` biểu diễn nó qua `InvocationTargetException`; các lỗi do giao thức Reflection của bên gọi vẫn có loại lỗi riêng.

## <a id="varargs-reflection">Varargs qua Reflection</a>

Varargs dễ gây nhầm vì có **hai lớp varargs**: method đích có thể là varargs, đồng thời `Method.invoke(...)` bản thân cũng nhận `Object... args`.

Ví dụ:

~~~java
public String summarize(String prefix, String... values) {
    return prefix + ":" + String.join(",", values);
}
~~~

Reflection nhìn chữ ký method đích như một method có tham số cuối là mảng:

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

Reflection không tự thực hiện việc đóng gói varargs cho method đích giống cú pháp gọi method thông thường. Với method đích chỉ có một tham số `String...`:

~~~java
public String join(String... values) { ... }
~~~

nên làm rõ `String[]` là **một đối số phản chiếu duy nhất**:

~~~java
Method join = PaymentService.class.getMethod(
        "join",
        String[].class
);

join.invoke(service, (Object) new String[] {"A", "B"});
~~~

Ép sang `Object` ngăn trình biên dịch Java hiểu `String[]` như chính `Object...` của `Method.invoke` và bung nó thành nhiều đối số phản chiếu.

Khi viết hàm tiện ích gọi Reflection, hãy dùng `isVarArgs()` như siêu dữ liệu để chuẩn hóa đối số theo quy tắc của chính hàm đó, thay vì giả định `Method.invoke` sẽ thay trình biên dịch xử lý mọi trường hợp varargs.

Sau lời gọi Method, thao tác còn lại để hoàn tất vòng đời đối tượng là **Constructor**: nếu class cụ thể chỉ được biết lúc chạy, đối tượng ban đầu được tạo thế nào?

## <a id="constructor-newinstance">Tạo đối tượng bằng Constructor.newInstance</a>

Khi đã chọn được đối tượng mô tả:

~~~java
Constructor<PaymentService> constructor =
        PaymentService.class.getConstructor(String.class);

PaymentService service = constructor.newInstance("stripe");
~~~

Đây là thao tác tương đương bằng Reflection của:

~~~java
PaymentService service = new PaymentService("stripe");
~~~

Varargs của constructor có cùng ranh giới đã thấy ở `Method.invoke(...)`: Reflection nhìn tham số `String...` như `String[]`, còn `Constructor.newInstance(...)` bản thân cũng nhận `Object...`.

~~~java
class TagBundle {
    TagBundle(String... tags) {}
}

Constructor<TagBundle> tagsConstructor =
        TagBundle.class.getDeclaredConstructor(String[].class);

TagBundle bundle = tagsConstructor.newInstance(
        (Object) new String[] {"fast", "safe"}
);
~~~

Cast sang `Object` làm rõ rằng `String[]` là **một đối số duy nhất của constructor**. Nếu bỏ cast trong trường hợp constructor chỉ nhận một `String...`, mảng có thể bị Java hiểu như chính mảng varargs `Object...` của `newInstance(...)`, làm các phần tử bị bung thành nhiều đối số phản chiếu. Reflection không tự đóng gói varargs cho constructor đích giống cú pháp gọi constructor thông thường.

Điểm khác biệt là constructor có thể được chọn từ siêu dữ liệu lúc chạy:

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

Trong framework thật, trước khi `newInstance(...)` thường có bước kiểm tra chính sách chọn constructor, quyền truy cập và cách phân giải dependency.

Đối số có thể trải qua chuyển đổi mở hộp/mở rộng primitive phù hợp với lời gọi phản chiếu. Nhưng bên gọi vẫn phải cung cấp số lượng và kiểu đối số hợp lệ; Reflection không tự phân tích hay chuyển đổi giá trị nghiệp vụ tùy ý.

Nên ưu tiên `Constructor.newInstance(...)` thay cho `Class.newInstance()`. `Class.newInstance()` đã deprecated vì hợp đồng kém rõ hơn, chỉ nhắm tới constructor không tham số và xử lý ngoại lệ của constructor theo cách khó dùng an toàn hơn. Đối tượng mô tả `Constructor` biểu diễn chính xác constructor đã chọn và bọc lỗi của mã đích qua `InvocationTargetException` giống `Method.invoke(...)`.

Việc tạo đối tượng bằng Reflection **vẫn chạy constructor thật**. Nó không phải cơ chế cấp phát đối tượng rồi bỏ qua logic khởi tạo:

~~~java
public PaymentService(String provider) {
    if (provider == null || provider.isBlank()) {
        throw new IllegalArgumentException("provider is required");
    }
    this.provider = provider;
}
~~~

`constructor.newInstance("")` sẽ thực thi phần kiểm tra này.

## <a id="constructor-reflection-failure">Các lỗi khi tạo đối tượng bằng Reflection</a>

Việc gọi constructor bằng Reflection có nhiều giai đoạn có thể phát sinh lỗi. Tách chúng ra giúp framework báo đúng nguyên nhân thay vì gom tất cả thành “Reflection thất bại”.

| Lỗi | Ý nghĩa |
| --- | --- |
| NoSuchMethodException | Không tìm thấy constructor đúng kiểu tham số |
| IllegalAccessException | Constructor tồn tại nhưng quyền truy cập phản chiếu không được phép |
| InstantiationException | Class khai báo không thể được tạo instance theo hợp đồng, ví dụ abstract class |
| IllegalArgumentException | Sai số lượng/kiểu đối số, hoặc class đích thuộc trường hợp Reflection cấm tạo instance như enum |
| InvocationTargetException | Thân constructor đã chạy và ném ngoại lệ |
| ExceptionInInitializerError | Quá trình khởi tạo class được kích hoạt và phần khởi tạo tĩnh thất bại |

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

thì lỗi nằm ở hợp đồng của lời gọi phản chiếu, không phải thân constructor.

Enum là trường hợp đặc biệt: instance enum do JVM quản lý theo khai báo; `Constructor.newInstance` không cho phép tạo thêm hằng enum.

Constructor private/package-private vẫn có thể được **khám phá** qua `getDeclaredConstructor(...)`, nhưng tìm thấy không đồng nghĩa được phép gọi. Việc có thể mở quyền truy cập phản chiếu hay không phụ thuộc ranh giới ngôn ngữ/mô-đun và chính sách lúc chạy. Chương **Quyền truy cập, đóng gói và giới hạn mô-đun** sẽ tiếp nối trực tiếp từ đây.

Đến đây ta đã có đủ **ba thao tác cốt lõi** của milestone này:

~~~text
đối tượng mô tả đã được khám phá
    ↓
Field.get/set
Method.invoke
Constructor.newInstance
    ↓
kiểm tra đối tượng nhận / đối số / chuyển đổi / lỗi từ mã đích
    ↓
gặp ranh giới quyền truy cập
~~~

Trước khi chuyển sang ranh giới quyền truy cập, cần tổng hợp rõ **luồng gọi phản chiếu** và các bước kiểm tra mà lời gọi động phải đi qua.

## <a id="reflective-dispatch">Tổng hợp luồng gọi phản chiếu</a>

Một lời gọi qua Reflection có thể được nhìn như chuỗi bước:

```text
siêu dữ liệu Class
    ↓ tra cứu đúng Method
kiểm tra quyền truy cập
    ↓
kiểm tra đối tượng đích + đối số
    ↓
chuyển đổi cần thiết lúc chạy
    ↓
phân phối lời gọi method
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

`getMethod("pay", PaymentRequest.class)` yêu cầu chữ ký cụ thể. Cơ chế tra cứu của Reflection không nhận một danh sách đối số lúc chạy rồi thực hiện toàn bộ quá trình chọn overload như trình biên dịch Java. Nếu class có `pay(String)` và `pay(PaymentRequest)`, mã động nên xác định chữ ký mong muốn rõ ràng.

Với instance method, `Method.invoke()` vẫn dùng cơ chế phân phối động trên đối tượng đích lúc chạy. Nghĩa là nếu `Method` đại diện cho method có thể override và đối tượng đích là instance của subclass đã override method đó, lời gọi vẫn có thể đi tới phần triển khai override. Reflection thay đổi **cách chọn đối tượng mô tả và ranh giới lời gọi**, không biến virtual dispatch thành static dispatch.

Các lỗi cũng nằm ở các giai đoạn khác nhau:

- tra cứu sai tên/chữ ký → `NoSuchMethodException`;
- thành phần không thể truy cập → `IllegalAccessException`;
- đối tượng đích sai kiểu, sai số đối số hoặc chuyển đổi không hợp lệ → `IllegalArgumentException`;
- đối tượng đích là `null` cho instance method → `NullPointerException`;
- chính method được gọi ném ngoại lệ → `InvocationTargetException` bọc ngoại lệ gốc.

Vì vậy mã framework thường tách “không phân giải được lời gọi” khỏi “method nghiệp vụ đã chạy nhưng thất bại”.

```java
try {
    return pay.invoke(service, request);
} catch (InvocationTargetException ex) {
    Throwable targetFailure = ex.getCause();
    // ánh xạ theo hợp đồng của lớp adapter hiện tại
    throw new IllegalStateException("Target method failed", targetFailure);
}
```

## <a id="argument-conversion">Chuyển đổi đối số khi invoke</a>

Chữ ký của `Method.invoke` là `invoke(Object obj, Object... args)`, nên các đối số đi qua một `Object[]`. Điều đó không có nghĩa lúc chạy Reflection chấp nhận mọi kiểu rồi tự ép kiểu tùy ý.

Với tham số hình thức primitive, Reflection có thể mở hộp wrapper và thực hiện các chuyển đổi hợp lệ khi gọi method, bao gồm mở rộng primitive. Chuyển đổi thu hẹp (narrowing) không được tự động thực hiện.

Ví dụ với một hàm hỗ trợ nhỏ:

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

Ngược lại, nếu tham số hình thức là `int`, truyền `Long` sẽ cần thu hẹp từ `long` xuống `int`; `Method.invoke()` không thực hiện chuyển đổi đó và sẽ ném `IllegalArgumentException`.

Đối số kiểu tham chiếu phải gán được cho kiểu tham chiếu hình thức. `null` hợp lệ cho tham số tham chiếu nhưng không hợp lệ cho tham số primitive. Giá trị trả về primitive được box lại thành wrapper; method `void` trả về `null` qua `invoke()`.

Một ranh giới dễ gây nhầm khác là varargs. `Method.invoke()` bản thân là varargs API, còn method đích cũng có thể là varargs. Reflection không thay thế logic của trình biên dịch cho mọi cách viết lời gọi trong mã nguồn; mã động nên chuẩn bị mảng đối số đúng cấu trúc mà method đích thực sự nhận.

Điểm thực hành quan trọng là kiểm tra siêu dữ liệu và đối số trước khi gọi nếu đầu vào đến từ cấu hình, plugin hoặc dữ liệu đã được tuần tự hóa. Nếu không, lỗi kiểu sẽ chỉ xuất hiện lúc chạy ngay tại `invoke()`.

## <a id="dynamic-array-reflection">Tạo và truy cập mảng khi kiểu phần tử chỉ biết lúc chạy</a>

Mảng là một trường hợp khác của **thao tác được điều khiển bởi siêu dữ liệu**. Cú pháp Java thông thường cần kiểu phần tử xuất hiện trực tiếp trong mã nguồn:

```java
String[] values = new String[3];
```

Nhưng framework có thể chỉ nhận `Class<?> componentType` từ cấu hình, schema hoặc một ánh xạ đã được xác định lúc chạy. Khi đó `java.lang.reflect.Array` cho phép tạo mảng mà không cần ghi cứng kiểu phần tử:

```java
Class<?> componentType = String.class;
Object array = Array.newInstance(componentType, 3);

Array.set(array, 0, "A");
Array.set(array, 1, "B");

Object first = Array.get(array, 0);
System.out.println(first); // A
```

Kiểu lúc chạy của `array` vẫn là `String[]`, dù biến ở thời điểm biên dịch trong ví dụ là `Object`:

```java
System.out.println(array.getClass());                   // class [Ljava.lang.String;
System.out.println(array.getClass().isArray());         // true
System.out.println(array.getClass().getComponentType());// class java.lang.String
```

`Array` cũng hỗ trợ mảng primitive. Các hàm như `getInt(...)`, `setInt(...)`, `getLong(...)` giúp thao tác mà không cần ép mảng về đúng kiểu mảng primitive trước:

```java
Object numbers = Array.newInstance(int.class, 2);

Array.setInt(numbers, 0, 10);
Array.setInt(numbers, 1, 20);

System.out.println(Array.getInt(numbers, 1)); // 20
```

Mảng nhiều chiều có thể được tạo bằng overload nhận nhiều kích thước:

```java
Object matrix = Array.newInstance(String.class, 2, 3);
System.out.println(matrix.getClass()); // class [[Ljava.lang.String;
```

Reflection không làm mất quy tắc kiểu của mảng. Sai chỉ số vẫn gây lỗi; giá trị không tương thích với kiểu phần tử vẫn bị từ chối lúc chạy; kích thước âm vẫn không hợp lệ. `Array` chỉ chuyển quyết định **kiểu phần tử + số chiều + truy cập** từ cú pháp tại thời điểm biên dịch sang siêu dữ liệu lúc chạy.

~~~text
kiểu phần tử biết tại thời điểm biên dịch
→ new T[length]

kiểu phần tử chỉ biết lúc chạy
→ Array.newInstance(componentType, length)
→ Array.get / Array.set
~~~

## <a id="method-handle-boundary">Ranh giới với MethodHandle</a>

`java.lang.invoke.MethodHandle` là cơ chế gọi động lân cận Reflection, nhưng nó giải quyết bài toán ở mức khác.

Với người học mô-đun Reflection, chỉ cần xem `MethodHandle` là một **API lân cận ở mức thấp hơn để giữ và gọi một đích động đã được phân giải**. Bạn không cần học `MethodType`, `Lookup` hay cơ chế thích nghi (adaptation) sâu để hiểu Reflection; phần dưới chỉ giúp nhận biết ranh giới giữa hai cơ chế.

Core Reflection tập trung vào **khám phá và thao tác siêu dữ liệu**: tìm `Method`, đọc annotation, tham số, modifier, chữ ký generic rồi có thể gọi. `MethodHandle` đại diện cho một đích có thể gọi với `MethodType`; nó hỗ trợ tra cứu và thích nghi cho các trường hợp liên kết/gọi động cấp thấp hơn.

Một cách hình dung hữu ích:

```text
Reflection
→ “thành phần nào tồn tại và siêu dữ liệu của nó là gì?”
→ Method.invoke(...) khi cần gọi qua đối tượng mô tả

MethodHandle
→ “tôi đã có một đích động có thể gọi với hình dạng kiểu nào?”
→ invokeExact/invoke + thích nghi khi cần
```

`invokeExact()` yêu cầu kiểu tại điểm gọi khớp chính xác với `MethodType`; `invoke()` linh hoạt hơn và có thể áp dụng một số thích nghi. Quyền truy cập khi tạo handle vẫn đi qua `MethodHandles.Lookup`, nên MethodHandle không phải lối tắt bỏ qua tính đóng gói.

Không nên đổi mọi `Method.invoke()` thành MethodHandle chỉ vì nghe “nhanh hơn”. Nếu nhiệm vụ chính là kiểm tra annotation, generic hoặc siêu dữ liệu thành phần, Reflection vẫn là abstraction tự nhiên. Nếu một hệ thống lúc chạy phân giải một đích có thể gọi một lần rồi gọi lặp lại với hợp đồng kiểu được kiểm soát, MethodHandle có thể là cơ chế phù hợp hơn. Chương này chỉ xác định ranh giới; `java.lang.invoke` có mô hình riêng sâu hơn Reflection.

## <a id="dynamic-invocation-design">Thiết kế lời gọi động</a>

Lời gọi động đáng dùng khi **đích thật sự chỉ được biết lúc chạy**. Ví dụ:

- framework chọn handler dựa trên annotation hoặc siêu dữ liệu định tuyến;
- serializer/deserializer chọn accessor/constructor theo class mô hình;
- hệ thống plugin nạp phần triển khai rồi gọi một hợp đồng được phát hiện;
- test/công cụ cần kiểm tra và gọi mã theo tên/chữ ký.

Nếu mã đã biết `PaymentService` và luôn cần gọi `pay(PaymentRequest)`, lời gọi trực tiếp đơn giản, an toàn kiểu và dễ tái cấu trúc hơn.

Khi lời gọi động là yêu cầu thật, nên thu hẹp nó thành một ranh giới có kiểm soát:

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

Ở đây việc tra cứu bằng chuỗi và ép kiểu tập trung tại adapter; phần còn lại của ứng dụng có thể dùng API có kiểu rõ ràng. `Method` được phân giải một lần thay vì tra cứu lại cho mỗi yêu cầu. Việc lưu đệm như vậy giảm tra cứu siêu dữ liệu lặp lại, nhưng không làm mất các đánh đổi khác của Reflection.

Đến đây milestone **Thao tác động bằng siêu dữ liệu** mới hoàn chỉnh: người học đã đi từ `get/set`, `invoke`, `newInstance` tới luồng phân phối lời gọi, chuyển đổi đối số, mảng động và ranh giới `MethodHandle`. Bước tiếp theo của ROADMAP là **Quyền truy cập, đóng gói và giới hạn mô-đun**: tìm thấy đối tượng mô tả chưa có nghĩa bên gọi được phép dùng nó. Sau đó người học mới đi tiếp tới siêu dữ liệu Generics, Dynamic Proxy và phần tổng hợp cuối mô-đun.

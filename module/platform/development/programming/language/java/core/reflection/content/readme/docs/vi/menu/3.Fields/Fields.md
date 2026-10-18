# Khám phá Field và siêu dữ liệu trạng thái

Sau khi chương **Mô hình kiểu lúc chạy với Class<?>** cho biết kiểu lúc chạy là gì, Reflection có thể khám phá **trạng thái được khai báo** của kiểu đó qua `Field`. Chương này tập trung vào việc tìm đúng field và đọc siêu dữ liệu của đối tượng mô tả; thao tác `get/set` sẽ được thực hiện ở milestone **Thao tác động bằng siêu dữ liệu**.

Ta tiếp tục với một `PaymentService` có cả field instance, static, final và generic:

~~~java
public class PaymentService {
    public static final String CHANNEL = "CARD";

    private final String provider;
    private int processedCount;
    private volatile boolean available = true;
    private List<String> supportedCurrencies = List.of("VND", "USD");

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

Field reflection có hai bước tách biệt: **khám phá đối tượng mô tả `Field`** rồi **dùng đối tượng mô tả đó trên một đối tượng nhận cụ thể**. Chương này chỉ tập trung vào bước khám phá; đọc/ghi giá trị nằm ở chương **Thao tác động bằng siêu dữ liệu**, còn ranh giới quyền truy cập được xử lý sau đó ở milestone **Quyền truy cập, đóng gói và giới hạn mô-đun**.

## <a id="field-discovery">Tìm Field theo đúng phạm vi</a>

`Class` cung cấp hai nhóm API theo đúng sự phân biệt ở chương trước:

~~~java
Class<PaymentService> type = PaymentService.class;

Field privateField = type.getDeclaredField("processedCount");
Field publicField = type.getField("CHANNEL");
~~~

`getDeclaredField(name)` chỉ tìm field được khai báo trực tiếp trên class hiện tại, bất kể mức truy cập. `getField(name)` chỉ tìm public field và có thể đi lên hệ phân cấp để tìm public field được kế thừa.

Ví dụ:

~~~java
class BaseService {
    public String region = "VN";
}

class PaymentService extends BaseService {
    private int processedCount = 3;
}
~~~

~~~text
PaymentService.class.getDeclaredField("processedCount")
→ tìm thấy

PaymentService.class.getField("region")
→ tìm thấy public field được kế thừa từ BaseService

PaymentService.class.getDeclaredField("region")
→ NoSuchFieldException

PaymentService.class.getField("processedCount")
→ NoSuchFieldException vì field không public
~~~

Khi cần liệt kê:

~~~java
Field[] declared = PaymentService.class.getDeclaredFields();
Field[] publicSurface = PaymentService.class.getFields();
~~~

Thứ tự phần tử không nên được dùng như hợp đồng. Nếu framework cần thứ tự ổn định, nó phải định nghĩa quy tắc riêng, ví dụ sắp xếp theo tên hoặc đọc một siêu dữ liệu khác.

Reflection cũng có thể thấy field synthetic do trình biên dịch tạo. `isSynthetic()` giúp công cụ phân biệt phần tử do trình biên dịch sinh ra với field xuất hiện trực tiếp trong mã nguồn. Một serializer viết kiểu “lấy mọi field khai báo trực tiếp rồi serialize hết” có thể vô tình kéo theo dữ liệu không thuộc hợp đồng của mô hình.

Một cách an toàn hơn là lọc có chủ đích:

~~~java
static List<Field> instanceFields(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
            .filter(field -> !field.isSynthetic())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .toList();
}
~~~

## <a id="field-modifiers">Modifier và đặc tính của Field</a>

`getModifiers()` cho phép framework hiểu field đang đóng vai trò gì:

~~~java
Field field = PaymentService.class.getDeclaredField("available");
int modifiers = field.getModifiers();

boolean isStatic = Modifier.isStatic(modifiers);
boolean isFinal = Modifier.isFinal(modifiers);
boolean isVolatile = Modifier.isVolatile(modifiers);
boolean isTransient = Modifier.isTransient(modifiers);
boolean isPrivate = Modifier.isPrivate(modifiers);
~~~

Các modifier không chỉ để hiển thị. Chúng thường quyết định chính sách:

~~~text
static
→ trạng thái thuộc class; mapper đối tượng thường bỏ qua

final
→ giá trị/tham chiếu của field chỉ được phép gán theo quy tắc của final;
  nếu field trỏ tới một đối tượng có thể thay đổi (mutable) thì trạng thái bên trong đối tượng đó vẫn có thể thay đổi

transient
→ siêu dữ liệu ở cấp mã nguồn thường được serializer cân nhắc bỏ qua,
  nhưng hành vi cuối cùng tùy serializer

volatile
→ field có ngữ nghĩa bảo đảm khả năng nhìn thấy giữa các luồng của Java;
  Reflection chỉ báo cáo siêu dữ liệu, không thay đổi ngữ nghĩa đó
~~~

Đừng hiểu `final` là “Reflection chắc chắn sửa được nếu cố mở quyền truy cập”. Trên Java hiện đại, việc ghi phản chiếu vào field final bị giới hạn mạnh ở một số loại field; ngoài ra `final` còn mang các bảo đảm ngôn ngữ/JVM mà framework không nên tùy tiện phá vỡ. Hãy xem `final` như một ranh giới thiết kế thay vì một cờ cần vượt qua.

Field còn có:

~~~java
field.isEnumConstant();
field.isSynthetic();
~~~

Hai đặc tính này không nên suy ra chỉ từ bit mask của `Modifier`.

Ví dụ một công cụ chụp trạng thái có chính sách rõ:

~~~java
static boolean shouldRead(Field field) {
    int m = field.getModifiers();
    return !Modifier.isStatic(m)
            && !field.isSynthetic();
}
~~~

Chính sách như vậy tốt hơn việc “Reflection thấy gì thì xử lý hết”, vì siêu dữ liệu lúc chạy có thể chứa thành phần phục vụ trình biên dịch/JVM mà không thuộc mô hình miền.

## <a id="field-type-metadata">Kiểu thô (raw) và kiểu generic của Field</a>

Một field có thể có hai lớp thông tin kiểu:

~~~java
private List<String> supportedCurrencies;
~~~

~~~java
Field field = PaymentService.class
        .getDeclaredField("supportedCurrencies");

Class<?> rawType = field.getType();
Type genericType = field.getGenericType();

System.out.println(rawType);
// interface java.util.List

System.out.println(genericType.getTypeName());
// java.util.List<java.lang.String>
~~~

`getType()` trả `Class<?>` của **kiểu lúc chạy/kiểu thô**. Nó phù hợp cho các câu hỏi như “field này có gán được từ `List` không?”.

`getGenericType()` trả `java.lang.reflect.Type` và có thể giữ siêu dữ liệu từ chữ ký generic. Với `List<String>`, kết quả thường là `ParameterizedType` thay vì chỉ `Class`.

~~~java
if (genericType instanceof ParameterizedType parameterized) {
    Type elementType = parameterized.getActualTypeArguments()[0];
    System.out.println(elementType.getTypeName());
}
~~~

Với biến kiểu:

~~~java
class Box<T> {
    T value;
}
~~~

`getType()` của `value` phản ánh kết quả sau xóa kiểu (thường là `Object` nếu `T` không có giới hạn cụ thể), còn `getGenericType()` có thể trả `TypeVariable` đại diện cho `T`.

Điều này không có nghĩa đối tượng generic thực sự giữ mọi đối số kiểu lúc chạy. Java vẫn dùng xóa kiểu (type erasure) cho quá trình thực thi; Reflection đang đọc **siêu dữ liệu chữ ký generic còn được lưu trên khai báo**. `ParameterizedType`, `TypeVariable`, `WildcardType` và `GenericArrayType` sẽ được học đầy đủ ở chương **Siêu dữ liệu Generics còn lại sau xóa kiểu**.

Field reflection vì thế nối hai lớp siêu dữ liệu:

~~~text
Mô hình kiểu lúc chạy
→ cấu trúc kiểu/thành phần

Field
→ đối tượng mô tả trạng thái + siêu dữ liệu kiểu/modifier/chữ ký

Siêu dữ liệu Generics sau xóa kiểu
→ chữ ký khai báo phong phú hơn khi Class<?> dạng thô không đủ
~~~

Sau khi biết cách tìm và mô tả trạng thái, bước tiếp theo của milestone **Khám phá các thành phần của lớp** là hành vi: **làm sao tìm đúng `Method` và hiểu chữ ký của nó trước khi thực hiện lời gọi?**

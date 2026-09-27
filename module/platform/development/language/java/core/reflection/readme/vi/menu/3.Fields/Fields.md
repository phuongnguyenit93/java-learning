# Reflection với Field

Sau khi Class Metadata cho biết runtime type là gì, Reflection có thể đi sâu vào **trạng thái** của type đó qua Field. Đây là nền tảng cho các mapper, serializer, validator và framework muốn đọc metadata của field mà không viết code riêng cho từng model.

Ta tiếp tục với một PaymentService có cả field instance, static, final và generic:

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

Field reflection có hai bước tách biệt: **khám phá descriptor Field** rồi **dùng descriptor đó trên một receiver cụ thể**. Việc tìm thấy một private field không tự có nghĩa rằng ta được phép đọc hoặc ghi nó; access control được xử lý sâu ở chương sau.

## <a id="field-discovery">Tìm Field theo đúng phạm vi</a>

Class cung cấp hai nhóm API giống distinction ở chương trước:

~~~java
Class<PaymentService> type = PaymentService.class;

Field privateField = type.getDeclaredField("processedCount");
Field publicField = type.getField("CHANNEL");
~~~

getDeclaredField(name) chỉ tìm field được khai báo trực tiếp trên class hiện tại, bất kể visibility. getField(name) chỉ tìm public field và có thể đi lên hệ phân cấp để tìm public field inherited.

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
→ tìm thấy public field inherited từ BaseService

PaymentService.class.getDeclaredField("region")
→ NoSuchFieldException

PaymentService.class.getField("processedCount")
→ NoSuchFieldException vì field không public
~~~

Khi cần enumerate:

~~~java
Field[] declared = PaymentService.class.getDeclaredFields();
Field[] publicSurface = PaymentService.class.getFields();
~~~

Thứ tự phần tử không nên được dùng như contract. Nếu framework cần thứ tự ổn định, nó phải định nghĩa quy tắc riêng, ví dụ sort theo tên hoặc đọc một metadata khác.

Reflection cũng có thể thấy synthetic field do compiler tạo. isSynthetic() giúp tooling phân biệt artifact compiler với field source-level mà nó muốn xử lý. Một serializer viết kiểu “lấy mọi declared field rồi serialize hết” có thể vô tình kéo theo dữ liệu không thuộc contract của model.

Một pattern an toàn hơn là filter có chủ đích:

~~~java
static List<Field> instanceFields(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields())
            .filter(field -> !field.isSynthetic())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .toList();
}
~~~

## <a id="field-read-write">Đọc và ghi giá trị qua Field</a>

Khi đã có `Field`, `get(...)` và `set(...)` thao tác trên một object cụ thể. Trước tiên hãy dùng một field `public` để quan sát cơ chế mà chưa vướng access control:

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

Ở đây `publicField` là descriptor cho `value`, còn `counter` là receiver chứa trạng thái thực tế.

Với running model, `processedCount` là `private`, nên discovery vẫn thành công nhưng thao tác giá trị còn phụ thuộc quyền truy cập:

~~~java
PaymentService service = new PaymentService("stripe");
Field field = PaymentService.class.getDeclaredField("processedCount");

// Hai dòng sau chỉ thành công nếu reflective access được phép.
Object value = field.get(service);
field.set(service, 5);
~~~

Việc tách hai ví dụ này là có chủ ý: **`getDeclaredField()` tìm thấy member không có nghĩa `get()/set()` được quyền dùng member đó**. Chương Access Control sẽ quay lại đúng ranh giới này với `canAccess(...)` và `trySetAccessible()`.

Field.get(...) trả Object. Với primitive field, giá trị được boxing:

~~~java
int processedCount = (Integer) field.get(service);
~~~

Field có các API chuyên biệt như getInt/setInt, getBoolean/setBoolean để tránh cast ở bên gọi:

~~~java
int count = field.getInt(service);
field.setInt(service, 5);
~~~

Đối với static field, giá trị thuộc class thay vì một instance. Receiver truyền vào get/set bị bỏ qua; convention dễ đọc nhất là truyền null:

~~~java
Field channel = PaymentService.class.getField("CHANNEL");
Object value = channel.get(null);
~~~

Với instance field, receiver phải là instance tương thích với declaring class. Sai receiver hoặc sai kiểu value có thể dẫn tới IllegalArgumentException.

Reflection thực hiện một số unboxing/widening conversion hợp lệ cho primitive field, nhưng không biến mọi giá trị thành kiểu đích. Ví dụ setInt chỉ phù hợp với field primitive có thể nhận int theo quy tắc của API; một String "5" không tự được parse thành số.

Điểm cần giữ trong mental model:

~~~text
Field descriptor
    +
receiver object (nếu là instance field)
    +
value phù hợp (nếu set)
    ↓
runtime access
~~~

Nếu field private, việc descriptor đã tồn tại không tự bỏ qua quy tắc access. IllegalAccessException là lỗi bình thường khi bên gọi không có reflective access. Chương Access Control sẽ giải thích canAccess(...), trySetAccessible() và JPMS.

## <a id="field-modifiers">Modifier và đặc tính của Field</a>

getModifiers() cho phép framework hiểu field đang đóng vai trò gì:

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
→ trạng thái thuộc class; mapper object thường bỏ qua

final
→ trạng thái được thiết kế không để gán lại sau initialization

transient
→ metadata source-level thường được serializer cân nhắc bỏ qua,
  nhưng hành vi cuối cùng tùy serializer

volatile
→ field có memory-visibility semantics của Java;
  Reflection chỉ report metadata, không thay đổi meaning đó
~~~

Đừng hiểu final là “Reflection chắc chắn sửa được nếu cố mở access”. Trên Java hiện đại, reflective write vào final field bị giới hạn mạnh ở một số loại field, và ngay cả trường hợp có thể thay đổi một final instance field thì program semantics có thể không đáng tin do assumptions/optimization của JVM. Framework nên xem final như một ranh giới thiết kế thay vì như một cờ cần phá.

Field còn có:

~~~java
field.isEnumConstant();
field.isSynthetic();
~~~

Hai property này không nên suy ra chỉ từ Modifier bit mask.

Ví dụ một snapshotter có chính sách rõ:

~~~java
static boolean shouldRead(Field field) {
    int m = field.getModifiers();
    return !Modifier.isStatic(m)
            && !field.isSynthetic();
}
~~~

Chính sách như vậy tốt hơn việc “Reflection thấy gì thì xử lý hết”, vì metadata runtime có thể chứa member phục vụ compiler/JVM mà không thuộc domain model.

## <a id="field-type-metadata">Kiểu raw và generic của Field</a>

Một field có thể có hai lớp thông tin type:

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

getType() trả Class<?> của **runtime/raw type**. Nó phù hợp cho các câu hỏi như “field này có assignable từ List không?”.

getGenericType() trả java.lang.reflect.Type và có thể giữ metadata từ generic signature. Với List<String>, kết quả thường là ParameterizedType thay vì chỉ Class.

~~~java
if (genericType instanceof ParameterizedType parameterized) {
    Type elementType = parameterized.getActualTypeArguments()[0];
    System.out.println(elementType.getTypeName());
}
~~~

Với type variable:

~~~java
class Box<T> {
    T value;
}
~~~

getType() của value phản ánh erasure (thường Object nếu T không có bound cụ thể), còn getGenericType() có thể trả TypeVariable đại diện cho T.

Điều này không có nghĩa generic object thực sự giữ mọi type argument ở runtime. Java vẫn dùng type erasure cho execution; Reflection đang đọc **generic signature metadata còn được lưu trên declaration**. ParameterizedType, TypeVariable, WildcardType và GenericArrayType sẽ được học đầy đủ ở chương Generic Type Inspection.

Field reflection vì thế nối hai thế giới:

~~~text
Class Metadata
→ type/member structure

Field
→ descriptor trạng thái + truy cập giá trị ở runtime

Generic Type Inspection
→ rich declaration signature khi raw Class<?> không đủ
~~~

Sau khi biết cách tìm và thao tác trạng thái, bước tiếp theo là hành vi: **làm sao tìm đúng Method, hiểu signature của nó và invoke khi method chỉ được biết ở runtime?**

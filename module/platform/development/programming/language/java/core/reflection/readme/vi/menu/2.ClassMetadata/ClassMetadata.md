# Class Metadata

Chương trước đã xác định Class<?> là cửa vào của Reflection. Bây giờ ta cần trả lời câu hỏi đầu tiên mà một framework thường hỏi sau khi có Class object: **runtime type này là loại gì và có cấu trúc tổng quát ra sao?**

Metadata ở mức Class chưa đọc trạng thái của object và chưa gọi hành vi. Nó giúp code phân loại type, lấy tên ổn định phù hợp với mục đích, kiểm tra modifier, hiểu quan hệ kế thừa và quyết định sẽ khám phá member theo quy tắc nào.

Ta tiếp tục dùng PaymentService:

~~~java
public interface PaymentProcessor {
    String pay(PaymentRequest request);
}

public class PaymentService implements PaymentProcessor {
    private final String provider;
    private int processedCount;

    public PaymentService(String provider) {
        this.provider = provider;
    }

    @Override
    public String pay(PaymentRequest request) {
        processedCount++;
        return provider + ":" + request.orderId();
    }

    private String internalStatus() {
        return provider + ":" + processedCount;
    }
}
~~~

## <a id="class-names">Các loại tên của Class</a>

Class cung cấp nhiều API lấy tên vì “tên của một type” có nhiều mục đích khác nhau.

~~~java
Class<?> type = PaymentService.class;

System.out.println(type.getName());
System.out.println(type.getSimpleName());
System.out.println(type.getCanonicalName());
System.out.println(type.getTypeName());
~~~

Với một top-level class bình thường, kết quả thường dễ đoán:

~~~text
getName()          → com.example.PaymentService
getSimpleName()    → PaymentService
getCanonicalName() → com.example.PaymentService
getTypeName()      → com.example.PaymentService
~~~

Nhưng các API này không đồng nghĩa.

getName() trả **binary name** đối với class/interface thông thường. Nested class vì vậy có thể chứa ký tự $:

~~~java
class PaymentModule {
    static class Config {}
}

System.out.println(PaymentModule.Config.class.getName());
// com.example.PaymentModule$Config
~~~

getSimpleName() trả phần tên ngắn, phù hợp cho log/UI hơn, nhưng anonymous class có thể trả chuỗi rỗng. Vì vậy không nên dùng simple name làm định danh duy nhất cho serialization, registry hay persistence.

getCanonicalName() trả canonical name nếu type có canonical name. Local class và anonymous class có thể trả null. Đây là một dấu hiệu quan trọng: code framework phải chấp nhận rằng không phải mọi Class đều ánh xạ sạch sang một tên source-level.

getTypeName() được thiết kế để biểu diễn type dễ đọc hơn trong ngữ cảnh Type. Với array nó hữu ích hơn binary name:

~~~java
System.out.println(String[].class.getName());      // [Ljava.lang.String;
System.out.println(String[].class.getTypeName());  // java.lang.String[]
~~~

Primitive cũng có Class:

~~~java
System.out.println(int.class.getName());      // int
System.out.println(int.class.getTypeName());  // int
~~~

Khi thiết kế framework, hãy chọn loại tên theo contract: binary name cho lookup như Class.forName(...), simple name cho hiển thị, canonical/type name cho mô tả dễ đọc khi phù hợp. Đừng giả định một API tên có thể dùng an toàn cho mọi mục đích.

## <a id="class-kind">Runtime type này thuộc loại nào?</a>

Một Class<?> có thể đại diện nhiều loại type khác nhau. Trước khi áp dụng quy tắc, framework thường phải phân loại nó.

~~~java
Class<?> type = PaymentService.class;

boolean interfaceType = type.isInterface();
boolean enumType = type.isEnum();
boolean recordType = type.isRecord();
boolean annotationType = type.isAnnotation();
boolean arrayType = type.isArray();
boolean primitiveType = type.isPrimitive();
boolean syntheticType = type.isSynthetic();
~~~

Ví dụ:

~~~java
record PaymentRequest(String orderId, long amount) {}
enum PaymentStatus { CREATED, PAID }

System.out.println(PaymentProcessor.class.isInterface()); // true
System.out.println(PaymentRequest.class.isRecord());      // true
System.out.println(PaymentStatus.class.isEnum());         // true
System.out.println(String[].class.isArray());              // true
System.out.println(long.class.isPrimitive());              // true
~~~

Các câu hỏi này quan trọng vì mỗi kind có quy tắc khác nhau. Serializer có thể xử lý record qua record components. Proxy mechanism có thể yêu cầu interface. Object factory không thể đối xử abstract class như concrete class. Array không có source declaration kiểu class thông thường nhưng vẫn có Class object.

Một vài category còn có thể chồng lên nhau. Annotation type về mặt JVM/Java type system cũng là một dạng interface, nên code nên hỏi đúng property mình cần thay vì giả định các isXxx() tạo ra những nhóm loại trừ nhau hoàn toàn.

isSynthetic() cho biết declaration do compiler tạo và đánh dấu synthetic. Reflection có thể nhìn thấy nhiều artifact không xuất hiện rõ trong source; vì vậy tooling thường cần quyết định có giữ hay lọc chúng.

Ngoài việc hỏi “đây là kind gì?”, framework còn thường phải kiểm tra **quan hệ type ở runtime** trước khi cast hoặc đăng ký implementation. `Class` cung cấp các API type-safe hơn việc tự cast mù:

~~~java
Class<?> discovered = PaymentService.class;
Object service = new PaymentService("stripe");

System.out.println(discovered.isInstance(service));
// true — tương tự một instanceof động

System.out.println(
        PaymentProcessor.class.isAssignableFrom(discovered)
);
// true — một PaymentService có thể được gán vào biến PaymentProcessor

PaymentService typed = PaymentService.class.cast(service);

Class<? extends PaymentProcessor> processorType =
        discovered.asSubclass(PaymentProcessor.class);
~~~

Direction của `isAssignableFrom(...)` rất dễ viết ngược. Hãy đọc `A.isAssignableFrom(B)` là: **một giá trị của B có thể gán cho biến kiểu A hay không?** `cast(...)` thực hiện checked cast ở runtime; `asSubclass(...)` làm điều tương tự ở mức `Class` object và ném `ClassCastException` nếu type được phát hiện không phải subtype phù hợp.

## <a id="modifiers">Đọc modifier của Class</a>

getModifiers() trả một bit mask. java.lang.reflect.Modifier cung cấp helper để giải nghĩa các bit có liên quan.

~~~java
int modifiers = PaymentService.class.getModifiers();

System.out.println(Modifier.isPublic(modifiers));
System.out.println(Modifier.isAbstract(modifiers));
System.out.println(Modifier.isFinal(modifiers));
System.out.println(Modifier.toString(modifiers));
~~~

Một object factory thường có thể dùng metadata này để loại class abstract trước khi thử instantiate:

~~~java
static boolean canConstruct(Class<?> type) {
    int modifiers = type.getModifiers();
    return !type.isInterface()
            && !Modifier.isAbstract(modifiers)
            && !type.isEnum()
            && !type.isPrimitive()
            && !type.isArray();
}
~~~

Đây vẫn chỉ là một bước lọc ban đầu. Việc một type vượt qua các điều kiện trên không bảo đảm framework chắc chắn tạo được object: constructor phù hợp, quyền truy cập, argument và lỗi trong constructor vẫn phải được kiểm tra ở bước Constructor Reflection.

Modifier bit mask phải được diễn giải theo loại element đang inspect. Cùng một API Modifier được dùng cho class, field, method và constructor, nhưng không phải modifier nào cũng có nghĩa trên mọi element.

Package-private không có một bit “package-private” riêng. Nếu public/protected/private đều false thì access ở source có thể là package-private, nhưng framework vẫn cần xét context truy cập thực tế. Chương Access Control sẽ xử lý reflective access; ở đây modifier chỉ là metadata.

Ngoài modifier, một số đặc điểm như synthetic, annotation, enum hay record có API riêng vì chúng không đơn giản là một modifier source-level.

Trên Java 21 còn có `accessFlags()`, trả về `Set<AccessFlag>` để biểu diễn các access/property flag ở mức class file/JVM dưới dạng enum thay vì bit mask:

~~~java
Set<AccessFlag> flags = PaymentService.class.accessFlags();
System.out.println(flags.contains(AccessFlag.PUBLIC));
System.out.println(flags.contains(AccessFlag.FINAL));
~~~

`Modifier` vẫn là API nền tảng và rất phổ biến để đọc modifier Java. `AccessFlag` là mô hình hiện đại hơn cho các flag mà JVM/class-file metadata biểu diễn; hai API liên quan nhưng không nên bị hiểu là hai tên khác nhau cho đúng một abstraction. `Field`, `Method` và `Constructor`/`Executable` cũng có `accessFlags()` khi framework cần nhìn metadata theo mô hình này.

## <a id="superclass-interfaces">Superclass và interface metadata</a>

Reflection cho phép đi từ một type sang quan hệ kế thừa trực tiếp của nó:

~~~java
Class<?> type = PaymentService.class;

Class<?> parent = type.getSuperclass();
Class<?>[] interfaces = type.getInterfaces();
~~~

Với PaymentService ở ví dụ trên:

~~~text
getSuperclass() → java.lang.Object
getInterfaces() → [PaymentProcessor]
~~~

getInterfaces() trả các interface **trực tiếp** mà class implements, hoặc các superinterface trực tiếp mà một interface extends. Nó không tự flatten toàn bộ cây interface. Nếu framework cần closure của cả hệ phân cấp, chính framework phải traverse tiếp và tránh lặp.

getSuperclass() có các ranh giới đáng nhớ:

| Type | getSuperclass() |
| --- | --- |
| class bình thường | superclass trực tiếp |
| Object | null |
| interface | null |
| primitive | null |
| void | null |
| array | Object |

Array còn report Cloneable và Serializable qua getInterfaces(), dù ta không viết một class declaration cho array.

Ví dụ traverse superclass chain:

~~~java
static List<Class<?>> classHierarchy(Class<?> type) {
    List<Class<?>> result = new ArrayList<>();
    for (Class<?> current = type;
         current != null;
         current = current.getSuperclass()) {
        result.add(current);
    }
    return result;
}
~~~

Nếu cần giữ generic signature của superclass/interface, Class còn có getGenericSuperclass() và getGenericInterfaces(). Module này sẽ dành một chương riêng cho Generic Type Inspection; đừng nhầm Class<?> metadata với toàn bộ generic type model.

## <a id="declared-vs-public-members">Declared member và public member</a>

Đây là distinction quan trọng nhất trước khi đi vào Field, Method và Constructor.

Hai họ API có ý nghĩa khác nhau:

~~~text
getDeclaredXxx(...)
→ member được khai báo trực tiếp bởi chính class này
→ có thể public/protected/package-private/private
→ không tự lấy member inherited

getXxx(...)
→ nhìn theo public API surface
→ với field/method có thể gồm public member inherited
~~~

Ví dụ:

~~~java
class BaseService {
    public String serviceName() {
        return "base";
    }
}

class PaymentService extends BaseService {
    private int processedCount;

    private String internalStatus() { return "ok"; }
    public String pay(PaymentRequest request) { return "ok"; }
}
~~~

Kết quả conceptually:

~~~text
PaymentService.class.getDeclaredMethod("internalStatus")
→ tìm thấy private method được khai báo tại PaymentService

PaymentService.class.getMethod("serviceName")
→ tìm thấy public method inherited từ BaseService

PaymentService.class.getDeclaredMethod("serviceName")
→ NoSuchMethodException
~~~

Với field:

~~~text
getDeclaredFields() → field khai báo trực tiếp, mọi visibility
getFields()         → public field của class + public field inherited
~~~

Với method:

~~~text
getDeclaredMethods() → method khai báo trực tiếp, mọi visibility
getMethods()         → public method của class + bề mặt public từ hệ phân cấp/interface
~~~

Một ngoại lệ quan trọng: `static` method của interface **không được kế thừa** bởi implementing class hoặc subinterface. Vì vậy đừng đọc câu “public method từ interface hierarchy” thành “mọi static helper trên interface cũng xuất hiện như method inherited của class”. Static interface method thuộc chính interface khai báo nó.

Constructor là trường hợp khác vì constructor **không được kế thừa**:

~~~text
getDeclaredConstructors() → mọi constructor khai báo bởi class
getConstructors()         → chỉ public constructor của chính class
~~~

Các array-returning discovery method không cam kết thứ tự phù hợp với source. Framework không nên chọn “method đầu tiên” hoặc “constructor đầu tiên” rồi coi đó là contract. Hãy chọn bằng điều kiện rõ: tên, parameter types, annotation, modifier hoặc quy tắc domain.

Chương tiếp theo áp dụng distinction này vào Field: **khi đã biết type, ta tìm trạng thái nào và đọc/ghi trạng thái đó ra sao?**

# Mô hình kiểu lúc chạy với Class<?>

Chương trước đã xác định `Class<?>` là cửa vào của Reflection. Bây giờ ta cần trả lời câu hỏi đầu tiên mà một framework thường hỏi sau khi có đối tượng `Class`: **kiểu lúc chạy này là loại gì và có cấu trúc tổng quát ra sao?**

Siêu dữ liệu ở mức `Class` chưa đọc trạng thái của đối tượng và chưa gọi hành vi. Nó giúp mã phân loại kiểu, lấy tên ổn định phù hợp với mục đích, kiểm tra modifier, hiểu quan hệ kế thừa và quyết định sẽ khám phá thành phần theo quy tắc nào.

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

## <a id="class-object-entrypoints">Class<?>: cửa vào của Reflection</a>

Reflection thường bắt đầu từ một `Class<?>`. `Class` không phải mã nguồn của class; nó là đối tượng lúc chạy đại diện cho một kiểu mà JVM đã biết.

Có ba cửa vào thường gặp:

~~~java
// 1. Biết kiểu ngay trong mã nguồn
Class<PaymentService> a = PaymentService.class;

// 2. Có sẵn đối tượng nhưng không biết kiểu cụ thể tại thời điểm biên dịch
Object service = new PaymentService("stripe");
Class<?> b = service.getClass();

// 3. Chỉ có tên nhị phân của class lúc chạy
Class<?> c = Class.forName("com.example.PaymentService");
~~~

Class literal phù hợp khi mã nguồn đã tham chiếu trực tiếp tới kiểu. `Object.getClass()` phù hợp với framework nhận một đối tượng bất kỳ và muốn kiểm tra class cụ thể lúc chạy. `Class.forName(...)` phù hợp với tình huống plugin/cấu hình nơi tên class chỉ có lúc chạy.

`Class<T>` còn mang thông tin kiểu tại thời điểm biên dịch. `Class<PaymentService>` nói rằng đối tượng `Class` này đại diện cho `PaymentService`. `Class<?>` nghĩa là “đây là một đối tượng `Class` hợp lệ nhưng mã hiện tại chưa biết kiểu cụ thể là gì”. Framework thường làm việc với `Class<?>` vì chính việc chưa biết kiểu cụ thể là một phần của bài toán; sau đó nó kiểm tra siêu dữ liệu trước khi ép kiểu hoặc thao tác.

Primitive và mảng cũng có đối tượng `Class`:

~~~java
Class<Integer> primitive = int.class;
Class<String[]> array = String[].class;
~~~

`Class.forName(String)` mặc định **khởi tạo class** sau khi tìm/nạp nó, nên phần khởi tạo static có thể chạy. Overload `Class.forName(name, false, loader)` cho phép yêu cầu không khởi tạo ở bước đó. Chi tiết vòng đời và định danh class thuộc mô-đun ClassLoader; ở đây chỉ cần nhớ rằng lấy `Class` bằng tên có thể có tác dụng phụ khác với việc đơn giản dùng class literal.

Sau khi có Class<?>, ta mới đi sang các câu hỏi cụ thể:

~~~java
Class<?> type = PaymentService.class;

String name = type.getName();
Field[] fields = type.getDeclaredFields();
Method[] methods = type.getDeclaredMethods();
Constructor<?>[] constructors = type.getDeclaredConstructors();
~~~

Phần còn lại của chương **Mô hình kiểu lúc chạy với Class<?>** sẽ đào sâu câu hỏi “kiểu này là gì”; các chương Field/Method/Constructor sau đó xử lý từng loại thành phần.

## <a id="class-names">Các loại tên của Class</a>

`Class` cung cấp nhiều API lấy tên vì “tên của một kiểu” có nhiều mục đích khác nhau.

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

`getName()` trả **tên nhị phân (binary name)** đối với class/interface thông thường. Class lồng nhau vì vậy có thể chứa ký tự `$`:

~~~java
class PaymentModule {
    static class Config {}
}

System.out.println(PaymentModule.Config.class.getName());
// com.example.PaymentModule$Config
~~~

`getSimpleName()` trả phần tên ngắn, phù hợp cho log/giao diện hơn, nhưng class ẩn danh (anonymous class) có thể trả chuỗi rỗng. Vì vậy không nên dùng tên đơn giản làm định danh duy nhất cho tuần tự hóa, registry hay lưu trữ lâu dài.

`getCanonicalName()` trả tên chính tắc (canonical name) nếu kiểu có tên chính tắc. Class cục bộ và class ẩn danh có thể trả `null`. Đây là một dấu hiệu quan trọng: mã framework phải chấp nhận rằng không phải mọi `Class` đều ánh xạ sạch sang một tên ở cấp mã nguồn.

`getTypeName()` được thiết kế để biểu diễn kiểu dễ đọc hơn trong ngữ cảnh `Type`. Với mảng nó hữu ích hơn tên nhị phân:

~~~java
System.out.println(String[].class.getName());      // [Ljava.lang.String;
System.out.println(String[].class.getTypeName());  // java.lang.String[]
~~~

Primitive cũng có Class:

~~~java
System.out.println(int.class.getName());      // int
System.out.println(int.class.getTypeName());  // int
~~~

Khi thiết kế framework, hãy chọn loại tên theo hợp đồng: tên nhị phân cho tra cứu như `Class.forName(...)`, tên đơn giản cho hiển thị, tên chính tắc/tên kiểu cho mô tả dễ đọc khi phù hợp. Đừng giả định một API tên có thể dùng an toàn cho mọi mục đích.

## <a id="class-kind">Kiểu lúc chạy này thuộc loại nào?</a>

Một `Class<?>` có thể đại diện nhiều loại kiểu khác nhau. Trước khi áp dụng quy tắc, framework thường phải phân loại nó.

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

Các câu hỏi này quan trọng vì mỗi loại có quy tắc khác nhau. Serializer có thể xử lý record qua record component. Cơ chế proxy có thể yêu cầu interface. Factory tạo đối tượng không thể đối xử abstract class như class cụ thể. Mảng không có khai báo mã nguồn kiểu class thông thường nhưng vẫn có đối tượng `Class`.

Một vài nhóm còn có thể chồng lên nhau. Annotation type về mặt hệ kiểu JVM/Java cũng là một dạng interface, nên mã nên hỏi đúng đặc tính mình cần thay vì giả định các `isXxx()` tạo ra những nhóm loại trừ nhau hoàn toàn.

`isSynthetic()` cho biết khai báo do trình biên dịch tạo và đánh dấu synthetic. Reflection có thể nhìn thấy nhiều phần tử sinh tự động không xuất hiện rõ trong mã nguồn; vì vậy công cụ thường cần quyết định có giữ hay lọc chúng.

Ngoài việc hỏi “đây là loại gì?”, framework còn thường phải kiểm tra **quan hệ kiểu lúc chạy** trước khi ép kiểu hoặc đăng ký phần triển khai. `Class` cung cấp các API an toàn kiểu hơn việc tự ép kiểu mù:

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

Chiều của `isAssignableFrom(...)` rất dễ viết ngược. Hãy đọc `A.isAssignableFrom(B)` là: **một giá trị của B có thể gán cho biến kiểu A hay không?** `cast(...)` thực hiện ép kiểu có kiểm tra lúc chạy; `asSubclass(...)` làm điều tương tự ở mức đối tượng `Class` và ném `ClassCastException` nếu kiểu được phát hiện không phải kiểu con (subtype) phù hợp.

## <a id="specialized-type-metadata">Siêu dữ liệu đặc thù của mảng, enum, record và kiểu sealed</a>

Không phải mọi `Class<?>` đều có cùng một tập thông tin có ý nghĩa. Sau khi xác định **loại** của kiểu, Reflection thường cung cấp API chuyên biệt cho loại đó.

Với mảng, `Class` cho biết kiểu phần tử trực tiếp:

~~~java
Class<?> arrayType = String[][].class;

System.out.println(arrayType.isArray());
System.out.println(arrayType.getComponentType());
System.out.println(arrayType.getComponentType().getComponentType());
~~~

`getComponentType()` trả `null` nếu `Class<?>` hiện tại không biểu diễn mảng. Với mảng nhiều chiều, mỗi lần gọi bóc ra một chiều vì kiểu phần tử trực tiếp của `String[][]` là `String[]`.

Với enum, `getEnumConstants()` trả các hằng enum theo thứ tự khai báo:

~~~java
enum PaymentStatus { CREATED, PAID, FAILED }

PaymentStatus[] values = PaymentStatus.class.getEnumConstants();
~~~

Nếu kiểu không phải enum, `getEnumConstants()` trả `null`.

Record có siêu dữ liệu riêng vì **record component** là một khái niệm của ngôn ngữ, không đơn giản đồng nghĩa với field hoặc method:

~~~java
record PaymentRequest(String orderId, long amount) {}

for (RecordComponent component : PaymentRequest.class.getRecordComponents()) {
    System.out.println(component.getName());
    System.out.println(component.getType());
    System.out.println(component.getAccessor());
}
~~~

`getRecordComponents()` trả các component theo thứ tự trong record header và trả `null` nếu kiểu không phải record. `RecordComponent` còn cung cấp kiểu generic và siêu dữ liệu annotation, nên serializer/framework có thể làm việc theo **hợp đồng của record** thay vì suy đoán record chỉ từ private field hoặc accessor method.

Sealed class/interface cũng để lại cấu trúc có thể kiểm tra:

~~~java
sealed interface Payment permits CardPayment, BankTransfer {}
final class CardPayment implements Payment {}
final class BankTransfer implements Payment {}

Class<?> paymentType = Payment.class;

if (paymentType.isSealed()) {
    for (Class<?> permitted : paymentType.getPermittedSubclasses()) {
        System.out.println(permitted.getName());
    }
}
~~~

`getPermittedSubclasses()` cho biết các kiểu con trực tiếp được phép khi kiểu là sealed và trả `null` nếu kiểu không sealed. Reflection chỉ **quan sát hợp đồng đã được khai báo**; cách thiết kế hệ phân cấp sealed thuộc phần type/OOP.

~~~text
Class<?> chung
    ↓ hỏi loại kiểu
    ├─ mảng   → kiểu phần tử
    ├─ enum   → các hằng enum
    ├─ record → các record component
    └─ sealed → các kiểu con trực tiếp được phép
~~~

## <a id="runtime-annotation-metadata">Annotation như siêu dữ liệu lúc chạy</a>

Một trường hợp sử dụng quan trọng của Reflection là **đọc annotation có chính sách lưu giữ `RUNTIME`**. Reflection không quyết định annotation tồn tại bao lâu; retention, target, repeatable và quy tắc kế thừa thuộc mô-đun Annotation. Ở đây ta nhìn annotation từ phía **bên đọc lúc chạy**.

`Class`, `Field`, `Method`, `Constructor`, `Parameter`, `RecordComponent` và nhiều phần tử phản chiếu khác tham gia hợp đồng `AnnotatedElement`, nên chúng có nhóm API tra cứu tương đối thống nhất. Trong ví dụ dưới, giả sử `@Audit` là annotation tùy chỉnh đã được khai báo với retention `RUNTIME` trong mô-đun Annotation:

~~~java
Deprecated deprecated = PaymentService.class
        .getAnnotation(Deprecated.class);

Annotation[] declared = PaymentService.class
        .getDeclaredAnnotations();

Audit[] audits = PaymentService.class
        .getAnnotationsByType(Audit.class);
~~~

Các API này trả lời các câu hỏi khác nhau:

- `getAnnotation(...)` dùng ngữ nghĩa **present** theo định nghĩa của `AnnotatedElement`;
- `getDeclaredAnnotation(...)` / `getDeclaredAnnotations()` chỉ nhìn khai báo hiện tại;
- `getAnnotationsByType(...)` phù hợp khi annotation có thể lặp lại và cần xử lý ngữ nghĩa của annotation chứa (container).

Reflection chỉ thấy annotation còn tồn tại lúc chạy. Annotation có retention `SOURCE` hoặc `CLASS` không tự xuất hiện qua các API lúc chạy này chỉ vì mã nguồn/class file từng có annotation đó.

~~~text
Mô-đun Annotation
→ định nghĩa hợp đồng siêu dữ liệu và retention

Mô-đun Reflection
→ đọc siêu dữ liệu RUNTIME và quyết định hành vi lúc chạy
~~~

## <a id="modifiers">Đọc modifier của Class</a>

`getModifiers()` trả một bit mask. `java.lang.reflect.Modifier` cung cấp các hàm hỗ trợ để giải nghĩa những bit có liên quan.

~~~java
int modifiers = PaymentService.class.getModifiers();

System.out.println(Modifier.isPublic(modifiers));
System.out.println(Modifier.isAbstract(modifiers));
System.out.println(Modifier.isFinal(modifiers));
System.out.println(Modifier.toString(modifiers));
~~~

Một factory tạo đối tượng thường có thể dùng siêu dữ liệu này để loại abstract class trước khi thử tạo instance:

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

Đây vẫn chỉ là một bước lọc ban đầu. Việc một kiểu vượt qua các điều kiện trên không bảo đảm framework chắc chắn tạo được đối tượng: constructor phù hợp, quyền truy cập, đối số và lỗi trong constructor vẫn phải được kiểm tra ở bước Constructor Reflection.

Bit mask của modifier phải được diễn giải theo loại phần tử đang được kiểm tra. Cùng một API `Modifier` được dùng cho class, field, method và constructor, nhưng không phải modifier nào cũng có nghĩa trên mọi phần tử.

Package-private không có một bit “package-private” riêng. Nếu public/protected/private đều false thì quyền truy cập trong mã nguồn có thể là package-private, nhưng framework vẫn cần xét ngữ cảnh truy cập thực tế. Chương **Quyền truy cập, đóng gói và giới hạn mô-đun** sẽ xử lý quyền truy cập phản chiếu; ở đây modifier chỉ là siêu dữ liệu.

Ngoài modifier, một số đặc điểm như synthetic, annotation, enum hay record có API riêng vì chúng không đơn giản là một modifier ở cấp mã nguồn.

Trên Java 21 còn có `accessFlags()`, trả về `Set<AccessFlag>` để biểu diễn các cờ truy cập/đặc tính ở mức class file/JVM dưới dạng enum thay vì bit mask:

~~~java
Set<AccessFlag> flags = PaymentService.class.accessFlags();
System.out.println(flags.contains(AccessFlag.PUBLIC));
System.out.println(flags.contains(AccessFlag.FINAL));
~~~

`Modifier` vẫn là API nền tảng và rất phổ biến để đọc modifier Java. `AccessFlag` là mô hình hiện đại hơn cho các cờ mà siêu dữ liệu JVM/class file biểu diễn; hai API liên quan nhưng không nên bị hiểu là hai tên khác nhau cho cùng một khái niệm trừu tượng. `Field`, `Method` và `Constructor`/`Executable` cũng có `accessFlags()` khi framework cần nhìn siêu dữ liệu theo mô hình này.

## <a id="superclass-interfaces">Siêu dữ liệu lớp cha và interface</a>

Reflection cho phép đi từ một kiểu sang quan hệ kế thừa trực tiếp của nó:

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

`getInterfaces()` trả các interface **trực tiếp** mà class `implements`, hoặc các superinterface trực tiếp mà một interface `extends`. Nó không tự trải phẳng toàn bộ cây interface. Nếu framework cần tập đầy đủ của cả hệ phân cấp, chính framework phải duyệt tiếp và tránh lặp.

getSuperclass() có các ranh giới đáng nhớ:

| Type | getSuperclass() |
| --- | --- |
| class bình thường | superclass trực tiếp |
| Object | null |
| interface | null |
| primitive | null |
| void | null |
| array | Object |

Mảng còn báo cáo `Cloneable` và `Serializable` qua `getInterfaces()`, dù ta không viết một khai báo class thông thường cho mảng.

Ví dụ duyệt chuỗi superclass:

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

Nếu cần giữ chữ ký generic của superclass/interface, `Class` còn có `getGenericSuperclass()` và `getGenericInterfaces()`. Mô hình `Type` đầy đủ được học ở chương **Siêu dữ liệu Generics còn lại sau xóa kiểu**; đừng nhầm siêu dữ liệu của `Class<?>` với toàn bộ mô hình kiểu generic.

## <a id="member-abstraction-model">Mô hình chung của các đối tượng mô tả thành phần</a>

Trước khi tách riêng `Field`, `Method` và `Constructor`, cần hiểu chúng không phải ba API hoàn toàn độc lập. Java Reflection cung cấp một số khái niệm trừu tượng chung để framework có thể xử lý thành phần ở mức cao hơn:

| Khái niệm | Vai trò |
| --- | --- |
| `Member` | Hợp đồng chung cho `Field`, `Method`, `Constructor`: tên, class khai báo, modifier, trạng thái synthetic |
| `Executable` | Class nền tảng chung của `Method` và `Constructor`: tham số, ngoại lệ, varargs, khai báo generic |
| `AccessibleObject` | Class nền tảng chung của `Field`, `Method`, `Constructor` cho kiểm tra quyền truy cập phản chiếu |
| `AnnotatedElement` | Hợp đồng đọc annotation lúc chạy; được triển khai bởi `AccessibleObject` và nhiều phần tử khác |

~~~text
                 Member
             ┌─────┴──────┐
           Field       Executable
                         ├─ Method
                         └─ Constructor

Field ───────────────┐
Executable ──────────┴─→ AccessibleObject → AnnotatedElement
~~~

Đây là bản đồ học tập, không phải toàn bộ hệ phân cấp kiểu của `java.lang.reflect`. Ví dụ `Executable` còn triển khai `GenericDeclaration`; `Class<?>` cũng triển khai `AnnotatedElement` nhưng không phải `Member`.

Mô hình tư duy thực hành là:

~~~text
khám phá
→ lấy đối tượng mô tả
→ đọc siêu dữ liệu chung
→ kiểm tra quyền truy cập
→ thực hiện thao tác riêng của Field / Method / Constructor
~~~

Nhờ vậy ba chương tiếp theo có thể tập trung vào hành vi riêng mà không phải học lại modifier, annotation hay abstraction truy cập từ đầu cho từng loại thành phần.

## <a id="declared-vs-public-members">Thành phần khai báo trực tiếp và thành phần public</a>

Đây là sự phân biệt quan trọng nhất trước khi đi vào Field, Method và Constructor.

Hai họ API có ý nghĩa khác nhau:

~~~text
getDeclaredXxx(...)
→ thành phần được khai báo trực tiếp bởi chính class này
→ có thể public/protected/package-private/private
→ không tự lấy thành phần được kế thừa

getXxx(...)
→ nhìn theo bề mặt API public
→ với field/method có thể gồm thành phần public được kế thừa
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

Về mặt khái niệm, kết quả là:

~~~text
PaymentService.class.getDeclaredMethod("internalStatus")
→ tìm thấy private method được khai báo tại PaymentService

PaymentService.class.getMethod("serviceName")
→ tìm thấy public method được kế thừa từ BaseService

PaymentService.class.getDeclaredMethod("serviceName")
→ NoSuchMethodException
~~~

Với field:

~~~text
getDeclaredFields() → field khai báo trực tiếp, mọi mức truy cập
getFields()         → public field của class + public field được kế thừa
~~~

Với method:

~~~text
getDeclaredMethods() → method khai báo trực tiếp, mọi mức truy cập
getMethods()         → public method của class + bề mặt public từ hệ phân cấp/interface
~~~

Một ngoại lệ quan trọng: `static` method của interface **không được kế thừa** bởi class triển khai hoặc subinterface. Vì vậy đừng đọc câu “public method từ hệ phân cấp interface” thành “mọi method hỗ trợ static trên interface cũng xuất hiện như method được kế thừa của class”. Static interface method thuộc chính interface khai báo nó.

Constructor là trường hợp khác vì constructor **không được kế thừa**:

~~~text
getDeclaredConstructors() → mọi constructor khai báo bởi class
getConstructors()         → chỉ public constructor của chính class
~~~

Các method khám phá trả về mảng không cam kết thứ tự phù hợp với mã nguồn. Framework không nên chọn “method đầu tiên” hoặc “constructor đầu tiên” rồi coi đó là hợp đồng. Hãy chọn bằng điều kiện rõ: tên, kiểu tham số, annotation, modifier hoặc quy tắc miền.

Chương tiếp theo áp dụng sự phân biệt này vào `Field`: **khi đã biết kiểu, ta tìm những field trạng thái nào và siêu dữ liệu nào mô tả chúng?** Việc đọc/ghi giá trị sẽ được thực hiện sau đó trong milestone **Thao tác động bằng siêu dữ liệu**.

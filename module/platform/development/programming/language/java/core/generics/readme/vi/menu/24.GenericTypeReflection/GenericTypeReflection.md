<a id="back-to-top"></a>

# Phản chiếu kiểu

## Menu
- [Class&lt;T&gt; như thông tin kiểu được truyền tường minh](#generic-type-reflection-001)
- [Class.cast, Class.isInstance và Class.asSubclass](#generic-type-reflection-002)
- [Phân biệt java.lang.reflect.Type với Class&lt;?&gt;](#generic-type-reflection-003)
- [ParameterizedType: raw type, owner type và actual type arguments](#generic-type-reflection-004)
- [TypeVariable: nơi khai báo và các giới hạn](#generic-type-reflection-005)
- [WildcardType và GenericArrayType](#generic-type-reflection-006)
- [Đọc getGenericSuperclass, getGenericInterfaces và chữ ký thành viên](#generic-type-reflection-007)
- [Lưu kiểu lồng nhau qua lớp con và mẫu type token](#generic-type-reflection-008)
- [Giới hạn phục hồi T chưa được ràng buộc và chi phí xử lý siêu dữ liệu](#generic-type-reflection-009)

## <a id="generic-type-reflection-001">Class&lt;T&gt; như thông tin kiểu được truyền tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# `Class<T>` như thông tin kiểu được truyền tường minh trong Java Generics

## 1. Khi `T` ở compile time chưa đủ cho runtime

Trong Java, một type variable như `T` giúp compiler kiểm tra và liên kết kiểu đầu vào, đầu ra. Ví dụ:

~~~java
static <T> T identity(T input) {
    return input;
}
~~~

Lời gọi `identity("Java")` trả một giá trị có kiểu tĩnh String, còn `identity(7)` trả Integer. Không cần một runtime token nào vì thuật toán chỉ chuyển tiếp tham chiếu đã được kiểm tra ở compile time.

Nhưng nếu phương thức nhận một `Object` từ API legacy, cấu hình hoặc reflection và phải **xác minh class do người gọi yêu cầu khi chương trình đang chạy**, generic type variable `T` không tự cung cấp class ấy.

~~~java
// CỐ Ý SAI theo mục đích kiểm tra runtime:
static <T> T unchecked(Object input) {
    return (T) input; // Cảnh báo unchecked; không biết class cụ thể của T.
}
~~~

Trong mô hình **type erasure**, `T` có thể bị xóa thành Object hoặc erasure của bound đầu tiên. Không có runtime class riêng cho mỗi lời gọi `T=String` hay `T=Integer` để câu `(T) input` thực sự chứng minh đúng kiểu cụ thể.

Thiết kế giải quyết được nhu cầu này bằng cách để **caller truyền một đối tượng Class**:

~~~java
static <T> T checked(Object input, Class<T> expected) {
    return expected.cast(input);
}
~~~

Caller:

~~~java
String word = checked("Java", String.class);
Integer number = checked(42, Integer.class);
~~~

Ở source, `Class<T>` liên kết token với kết quả `T`. Ở runtime, `expected.cast` sử dụng class thật được cung cấp để kiểm tra object.

**Đây là bản chất của class token:** `Class<T>` đại diện cho một class/interface/array type có runtime identity cụ thể; nó không làm Java Generics được reify toàn bộ.

## 2. Phân biệt `T`, `Class<T>`, `Class<?>` và `Type`

| Ký hiệu | Vai trò | Khi nào hữu ích |
| --- | --- | --- |
| `<T>` | Khai báo type parameter. | Khi API cần giữ mối quan hệ kiểu giữa các vị trí. |
| `T` | Type variable trong method/class generic. | Compile-time type checking, suy luận kiểu. |
| `Class<T>` | Class token có type argument liên kết với T. | Kiểm tra class của Object hoặc class được chọn tại runtime. |
| `Class<?>` | Class token nhưng type argument chưa biết. | Đọc metadata class/plugin mà chưa được xác minh kiểu cụ thể. |
| `Class<? extends Base>` | Token thuộc Base hoặc subtype. | API dùng class của một implementation Base. |
| `java.lang.reflect.Type` | Giao diện mô tả kiểu khai báo, có thể chứa type arguments lồng nhau. | Phân tích `List<String>`, `Map<String,List<Integer>>` từ metadata. |

Tương ứng:

~~~java
Class<String> textToken = String.class;
Class<Integer> numberToken = Integer.class;
Class<?> unknown = textToken;
Class<? extends Number> numeric = Integer.class;
~~~

`Class<T>` vẫn theo **invariance**:

~~~java
// Không hợp lệ:
// Class<CharSequence> parent = String.class;

// Hợp lệ:
Class<? extends CharSequence> parent = String.class;
~~~

Một runtime Class object **không phải** một class sinh riêng cho mỗi parameterized type. `Class<String>` mô tả token của class String. Nó **không** có ý nghĩa rằng mọi `ArrayList<String>` instance đều mang sẵn token của String.

### Những phép thử bị cấm trên type variable

~~~java
// Các biểu thức sau cố ý không biên dịch khi T là type variable:
// T.class
// new T()
// new T[10]
// input instanceof T
~~~

Những cú pháp này đòi một runtime type theo cách không thể rút trực tiếp từ `T` chưa được reify. Truyền `Class<T>` nếu cần một runtime class; truyền factory nếu cần tạo object.

## 3. Ranh giới sử dụng phù hợp: dữ liệu ngoài dạng `Object`

Một ví dụ thực tế là đọc cấu hình hoặc data đã giải mã từ map:

~~~java
Map<String, Object> values = new HashMap<>();
values.put("user", "Ada");
values.put("count", 17);
~~~

Một typed API cần nói rõ:
1. Key có tồn tại không?
2. Null có được chấp nhận không?
3. Value có đúng class mà caller yêu cầu không?
4. Nếu đúng, kết quả sẽ mang kiểu tĩnh nào?

~~~java
static <T> T require(
        Map<String, ?> values,
        String key,
        Class<T> expected) {

    Objects.requireNonNull(expected, "expected");

    if (!values.containsKey(key)) {
        throw new IllegalArgumentException("missing: " + key);
    }

    Object candidate = Objects.requireNonNull(
            values.get(key), "null: " + key);

    return expected.cast(candidate);
}
~~~

Caller:

~~~java
String user = require(values, "user", String.class);
Integer count = require(values, "count", Integer.class);
~~~

Nhờ cùng type parameter T trong `Class<T>` và return type, caller nhận kiểu được compiler kiểm tra mà không cần cast thủ công.

Các lỗi được phân tách theo hợp đồng của **API minh họa**:
- Thiếu key → IllegalArgumentException.
- Có key nhưng null → NullPointerException.
- Đúng key nhưng sai class → ClassCastException.
- Đúng kiểu → trả giá trị T.

**Lưu ý:** `Class.cast(null)` tự nó trả null. Chính `Objects.requireNonNull` tạo null policy trong ví dụ, không phải `Class<T>` tự cấm null.

### Vì sao `(T) candidate` không thay thế `expected.cast(candidate)`?

Cast tới type variable `T` sau erasure không có sẵn class String hoặc Integer để kiểm tra chính xác. Một sai kiểu có thể chỉ xuất hiện ở caller khi kết quả được dùng như String. `Class.cast` dùng token thật được truyền vào để kiểm tra và báo lỗi tại boundary, gần nguồn dữ liệu sai hơn.

Cũng đừng nhầm cast với **chuyển đổi dữ liệu**: `String.class.cast(17)` không tự tạo chuỗi `"17"`. Nếu cần parser/converter, dùng `String.valueOf`, `Integer.parseInt` hoặc logic nghiệp vụ riêng.

## 4. Generic object chủ động giữ token để kiểm soát invariant

Một generic object có thể nhận dữ liệu từ cả nguồn typed và nguồn Object. Nếu cần chấp nhận input chưa định kiểu, token được lưu ở object là ràng buộc thực tế:

~~~java
static final class Slot<T> {
    private final Class<T> token;
    private T value;

    Slot(Class<T> token) {
        this.token = Objects.requireNonNull(token);
    }

    void setUnknown(Object input) {
        value = token.cast(input);
    }

    T get() {
        return value;
    }
}
~~~

Cách gọi:

~~~java
Slot<String> slot = new Slot<>(String.class);
slot.setUnknown("ready");

try {
    slot.setUnknown(123);
} catch (ClassCastException ex) {
    // Input không thuộc runtime class String.
}
~~~

Mỗi instance Slot có **runtime class giống nhau** về mặt generic class, nhưng instance cụ thể có thể giữ **token khác nhau** do caller truyền tới. Token không phải dữ liệu JVM tự suy ra từ `Slot<String>`.

Bởi phép cast được thực hiện **trước** lệnh gán field hoàn tất, lỗi ở `setUnknown(123)` không thay đổi giá trị `"ready"`. Đây là một hậu điều kiện phù hợp để test.

Đừng sửa mọi setter thành `setUnknown(Object)` chỉ vì có class token: setter `void set(T value)` vẫn là thiết kế tốt hơn khi API chỉ nhận callers typed bình thường. Token hữu ích ở những điểm **cố ý mở dữ liệu chưa biết kiểu**.

## 5. Chương trình Java 17 hoàn chỉnh: kiểm tra, giữ token, xác minh danh sách

Lưu thành **ClassTokenFoundations.java**:

~~~java
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class ClassTokenFoundations {
    static <T> T require(
            Map<String, ?> values,
            String key,
            Class<T> expected) {

        Objects.requireNonNull(expected, "expected");

        if (!values.containsKey(key)) {
            throw new IllegalArgumentException("missing: " + key);
        }

        Object candidate = Objects.requireNonNull(
                values.get(key), "null: " + key);

        return expected.cast(candidate);
    }

    static <T> List<T> checkedCopy(
            List<?> source,
            Class<T> expected) {

        List<T> output = new ArrayList<>();

        for (Object value : source) {
            output.add(expected.cast(
                    Objects.requireNonNull(value, "null element")));
        }

        return List.copyOf(output);
    }

    static <T> T create(Supplier<? extends T> factory) {
        return factory.get();
    }

    static final class Slot<T> {
        private final Class<T> token;
        private T value;

        Slot(Class<T> token) {
            this.token = Objects.requireNonNull(token);
        }

        void setUnknown(Object value) {
            this.value = token.cast(value);
        }

        T get() {
            return value;
        }
    }

    public static void main(String[] args) {
        Map<String, Object> config = new HashMap<>();
        config.put("user", "Ada");
        config.put("count", 17);

        System.out.println(
                "user=" + require(config, "user", String.class));
        System.out.println(
                "count=" + require(config, "count", Integer.class));

        try {
            require(config, "count", String.class);
        } catch (ClassCastException ex) {
            System.out.println("bad-token=" +
                    ex.getClass().getSimpleName());
        }

        Slot<String> message = new Slot<>(String.class);
        message.setUnknown("ready");

        try {
            message.setUnknown(12);
        } catch (ClassCastException ex) {
            System.out.println("slot-reject=" +
                    ex.getClass().getSimpleName());
        }

        System.out.println("slot=" + message.get());
        System.out.println("verified=" +
                checkedCopy(List.of("a", "b"), String.class));

        try {
            checkedCopy(List.of("a", 8), String.class);
        } catch (ClassCastException ex) {
            System.out.println("list-reject=" +
                    ex.getClass().getSimpleName());
        }

        System.out.println(
                "nullCast=" + (String.class.cast(null) == null));
        System.out.println(
                "nullInstance=" + String.class.isInstance(null));
        System.out.println(
                "subtype=" +
                Number.class.isAssignableFrom(Integer.class));
        System.out.println(
                "factory=" + create(
                        () -> new StringBuilder("made")));
        System.out.println(
                "sameRuntime=" +
                (new ArrayList<String>().getClass()
                        == new ArrayList<Integer>().getClass()));
    }
}
~~~

Biên dịch/chạy không cần thư viện ngoài JDK:

~~~bash
javac --release 17 -Xlint:all ClassTokenFoundations.java
java ClassTokenFoundations
~~~

**Stdout đã kiểm chứng:**

~~~text
user=Ada
count=17
bad-token=ClassCastException
slot-reject=ClassCastException
slot=ready
verified=[a, b]
list-reject=ClassCastException
nullCast=true
nullInstance=false
subtype=true
factory=made
sameRuntime=true
~~~

**Đọc kết quả theo các mốc kiểm tra:**

- `bad-token`: map giữ Integer, nhưng caller yêu cầu String; token phát hiện mismatch ngay tại boundary.
- `slot-reject`/`slot=ready`: Token ở Slot<String> chặn ghi sai và giữ hậu điều kiện.
- `verified`/`list-reject`: Khi nguồn là List<?> chưa biết element type, kiểm tra **từng phần tử** theo token rồi copy mới là cách bảo vệ kết quả.
- `nullCast`/`nullInstance`: API null behaviors khác nhau; thiết kế null policy riêng.
- `subtype`: `Number.class.isAssignableFrom(Integer.class)` đúng chiều assignability.
- `factory`: Supplier tạo object mà không cần reflection.
- `sameRuntime`: hai cách tham số hóa ArrayList trực tiếp có cùng runtime class, không reify String/Integer.

## 6. Giới hạn: `List.class` chỉ kiểm tra container, không kiểm tra elements

Java cho phép:

~~~java
List<String> words = List.of("a", "b");
List<Integer> numbers = List.of(1, 2);

boolean a = List.class.isInstance(words);
boolean b = List.class.isInstance(numbers);
~~~

Cả `a` và `b` đều `true`. Runtime token `List.class` chỉ nhận diện loại container, không biết các phần tử theo generic argument String hay Integer.

Không thể viết:

~~~java
// CỐ Ý KHÔNG BIÊN DỊCH:
// Class<List<String>> token = List<String>.class;
// boolean ok = someObject instanceof List<String>;
~~~

`List<String>` là **non-reifiable parameterized type**; không có class literal riêng cho nó.

Để xác thực dữ liệu đầu vào `List<?>` theo element type, dùng vòng lặp:

~~~java
static <T> List<T> checkedCopy(
        List<?> source, Class<T> type) {

    List<T> result = new ArrayList<>();

    for (Object element : source) {
        result.add(type.cast(
                Objects.requireNonNull(element)));
    }

    return List.copyOf(result);
}
~~~

Phương thức này xác minh từng element trực tiếp và tạo một list mới. `List.copyOf` từ chối null, trả list không sửa được qua các mutation APIs thông thường; nhưng đây là **shallow copy** nếu elements vốn mutable.

Với nested generic types như `List<List<String>>`, một token `List.class` chỉ kiểm tra raw List của mỗi phần tử, **không** đi tới Strings ở tầng sâu. Những cấu trúc nested cần validator/decoder có schema thích hợp.

### `Collections.checkedList` kiểm tra một kiểu khác của thao tác

JDK có:

~~~java
List<String> checked = Collections.checkedList(
        new ArrayList<>(), String.class);
~~~

Wrapper dùng token String để kiểm tra **mutation đi qua wrapper**. Nó không tự xác thực hết phần tử đã có khi tạo wrapper và không thể chặn mutation qua một alias có thể viết trực tiếp lên backing list.

- **Validated copy:** phù hợp với dữ liệu bên ngoài mà bạn muốn kiểm tra toàn bộ một lần và sở hữu bản copy độc lập.
- **Checked wrapper:** phù hợp với nhu cầu chặn write sai kiểu qua một view có class token.

Đừng nhầm hai công cụ này với reification toàn bộ generic type arguments.

## 7. Khi nào `Class<T>` không cần thiết?

Một generic method có thể không hỏi bất kỳ runtime type nào:

~~~java
static <T> T first(List<T> values) {
    if (values.isEmpty()) {
        throw new IllegalArgumentException("empty");
    }
    return values.get(0);
}
~~~

Không cần `Class<T>` vì compiler có đủ quan hệ giữa type argument của List và kết quả T.

Tương tự, nếu caller **biết cách tạo object**, một factory callback thường đơn giản hơn:

~~~java
static <T> T create(
        java.util.function.Supplier<? extends T> factory) {

    return factory.get();
}

// StringBuilder builder = create(StringBuilder::new);
~~~

Nếu class thực sự chỉ được chọn ở runtime và phải gọi constructor bằng reflection, `Class<T>` có thể phù hợp:

~~~java
static <T> T createByClass(Class<T> type)
        throws ReflectiveOperationException {

    return type.getDeclaredConstructor().newInstance();
}
~~~

Nhưng constructor không đối số phải tồn tại, truy cập được theo access/module rules và class phải có thể khởi tạo. Một `Class<T>` không chứng minh điều đó và không tự vượt được quyền truy cập reflection.

### Chọn theo nhiệm vụ

| Nhiệm vụ | Tham số phù hợp |
| --- | --- |
| Chỉ bảo toàn cùng T giữa input/output | Generic `<T>` thông thường |
| Runtime yêu cầu object thuộc class caller chỉ định | `Class<T>` |
| Caller cung cấp cách tạo instance | `Supplier<? extends T>` |
| Runtime chọn class rồi kiểm tra constructor/plugin | `Class<?>`, bước kiểm tra subtype, reflection |
| Cần nested generic declaration `Map<String,List<Integer>>` | `java.lang.reflect.Type` / type reference / schema |

## 8. Thao tác trên class token khác với thao tác trên object

Ba câu hỏi thường gặp:

~~~java
Class<?> pluginClass = Class.forName(className);
~~~

1. “**Object** có thuộc class X không?” — dùng `X.isInstance(object)`, hoặc `X.cast(object)` khi cần typed result.
2. “**Class** runtime này có phải subtype của Base không?” — dùng `pluginClass.asSubclass(Base.class)` để lấy token có bound đúng nếu hợp lệ, hoặc `Base.class.isAssignableFrom(pluginClass)` để hỏi boolean.
3. “Class này có thể **tạo instance** bằng constructor không?” — kiểm tra constructor/access và xử lý reflection exceptions riêng; `asSubclass` không tạo object.

`asSubclass` làm việc với **class token**, không cast một instance. `Class.cast` làm việc với **object thực**, không phát hiện nested generic arguments.

Khi class name đến từ plugin/config không tin cậy, phải có chính sách allowlist, class loader, module access và kiểm soát side effects. Type safety không đồng nghĩa security policy.

### `Class<?>` là loại thông tin chưa biết, không phải raw `Class`

Nếu chưa biết type argument ở compile time, dùng `Class<?>`. Không dùng raw `Class` chỉ để thoát khỏi type checker. Nếu API cần nói “class của subtype Number”, dùng `Class<? extends Number>`; `Class<Number>` sẽ không tự chấp nhận `Integer.class` do generic invariance.

## 9. `Class<T>` khác metadata `Type`

Giả sử một field được khai báo:

~~~java
class Model {
    List<String> names;
}
~~~

Reflection có hai góc nhìn:

~~~java
Field f = Model.class.getDeclaredField("names");

Class<?> erasedClass = f.getType();
java.lang.reflect.Type declared = f.getGenericType();
~~~

`getType()` trả runtime/erased class List. `getGenericType()` có thể trả `ParameterizedType` cho declaration `List<String>` khi generic Signature metadata hiện diện.

`Type` là interface chung được các cấu trúc như `Class`, `ParameterizedType`, `TypeVariable`, `WildcardType` và `GenericArrayType` dùng để mô tả declaration. Nó **không** có method `cast` giống `Class<T>` cho tất cả các loại type.

Một super type token như `new TypeRef<List<String>>() {}` có thể lưu metadata được khai báo trong một anonymous subclass. Điều đó **không** khiến object `new ArrayList<String>()` tự tiết lộ `String` ở `getClass()`, cũng không làm một `T` chưa bound trong generic method tự resolve.

Khi xây schema/serializer, cần phân nhánh đúng loại `Type`, giải các bindings trong hierarchy khi có và thực hiện validation dữ liệu riêng. Bài `Class<T>` này chỉ giúp xác định **khi nào lớp runtime cụ thể là đủ và khi nào không**.

## 10. Ba sai lầm về type safety ở runtime

### Sai lầm A — “`Class.cast` biến kiểu dữ liệu”

Không. `Integer.class.cast("17")` không parse chuỗi "17" thành số; nếu kiểu thực không đúng, ném ClassCastException. Conversion cần parser/mapper thích hợp.

### Sai lầm B — “`Class<T>` biết generic arguments của từng collection”

Không. `List.class` không biết một ArrayList được khai báo thành `List<String>` hay `List<Integer>` trong source. Cần Type metadata từ declaration và/hoặc validation từng element.

### Sai lầm C — “Lấy được `Class<?>` từ string class name là đã tin cậy”

Không. Hai class trùng tên do class loaders khác nhau có thể không phải cùng runtime identity. Và class không tin cậy có thể có constructor/initialization side effects. Sử dụng Class identity và subtype relationship thực, cùng allowlist/policy phù hợp.

## 11. Câu hỏi và bài tập kiểm tra hiểu biết

**1.** `String.class` có type gì ở source?

**Đáp án:** `Class<String>`, là class token truyền được vào generic method nhận `Class<T>`.

**2.** `Class<String>` gán được cho `Class<CharSequence>` không?

**Đáp án:** Không, vì generic invariance. Dùng `Class<? extends CharSequence>` nếu muốn chấp nhận các token subtype như String.class.

**3.** Có thể viết `T.class` trong một generic method không?

**Đáp án:** Không. Type variable T không tự mang runtime class literal. Hãy nhận token nếu thật sự cần runtime class.

**4.** `String.class.cast(null)` và `String.class.isInstance(null)` trả gì?

**Đáp án:** Lần lượt null và false. API phải có null policy nếu không muốn nhận null.

**5.** Một map có `count=17`, caller yêu cầu `String.class`. Điều gì xảy ra ở `require`?

**Đáp án:** ClassCastException tại runtime class check. Class.cast không đổi Integer thành chuỗi.

**6.** Nếu object thật là `List<Integer>`, `List.class.isInstance(object)` có true không?

**Đáp án:** Có. Nó chỉ kiểm tra object thực hiện List, không kiểm tra Integer type argument.

**7.** Làm sao đưa một `List<?>` từ nguồn chưa tin cậy vào API trả `List<String>`?

**Đáp án:** Kiểm tra từng phần tử với String.class, tạo typed copy có chính sách null/mutability/ownership rõ ràng.

**8.** Có `Class<T>` thì `new T()` hợp lệ chưa?

**Đáp án:** Chưa. Dùng reflection với constructor phù hợp hoặc factory callback. Không phải class nào cũng có constructor không đối số hay quyền truy cập cần thiết.

**9.** `Supplier<T>` có ưu điểm gì khi caller biết cách tạo object?

**Đáp án:** Không cần reflection, constructor được compiler kiểm tra tại caller và API đơn giản hơn.

**10.** `getGenericType()` trên field khai báo List<String> có chứng minh object list hiện chỉ chứa String không?

**Đáp án:** Không. Đó là declaration metadata, không phải runtime payload validation.

**11.** Muốn giới hạn một Class<?> do plugin loader trả về thành một subtype của Service thì làm sao?

**Đáp án:** Dùng `discovered.asSubclass(Service.class)` hoặc kiểm tra equivalent, rồi xem xét constructor/access/security riêng.

**12.** Vì sao không so tên class bằng String như bằng chứng subtype?

**Đáp án:** Runtime type identity và subtype còn phụ thuộc class loader và quan hệ kế thừa thật, không chỉ tên nhị phân.

## 12. Checklist thiết kế và kết luận

- [ ] Chỉ đưa `Class<T>` vào API khi **runtime thực sự cần class object**.
- [ ] Tách generic type variable `T` khỏi Class token caller cung cấp.
- [ ] Đặt class check ngay ở boundary nhận `Object`/legacy data.
- [ ] Xác định chính sách key thiếu, null, sai class và conversion.
- [ ] Dùng `Class<?>` khi type argument chưa biết, tránh raw `Class`.
- [ ] Dùng bounded class token nếu cần giới hạn class subtype.
- [ ] Với `List<?>`, `List.class` không xác thực type của elements.
- [ ] Chọn validated copy hoặc checked wrapper theo nhiệm vụ, kiểm soát aliases.
- [ ] Nếu chỉ cần factory, cân nhắc `Supplier<? extends T>`.
- [ ] Nếu cần nested type declarations, dùng `Type`/TypeRef và logic giải/kiểm tra riêng.
- [ ] Không suy generic argument từ `obj.getClass()` ở một instance collection thông thường.
- [ ] Khi load runtime class, kiểm tra class loader, access, trust và subtype contract.

**Ghi nhớ:** `Class<T>` là cầu nối **tường minh** giữa generic type relationship mà javac kiểm tra và class cụ thể mà JVM có thể kiểm tra. Nó không “giải mã lại” các generic arguments đã erasure, không làm `T.class` hợp lệ và không tự validate collections lồng nhau. Dùng token đúng **tại những nơi dữ liệu chưa typed**, còn giữ các generic APIs thông thường ở những nơi compiler đã bảo đảm đầy đủ hợp đồng.

### Tài liệu chuẩn

- [Java 17 Class API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [Java 17 Type API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Type.html)
- [JLS `4.6 Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6)
- [JLS `4.7 Reifiable types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-reflection-002">Class.cast, Class.isInstance và Class.asSubclass</a>

<details>
<summary>Xem chi tiết</summary>

# Java Reflection: `Class.cast`, `Class.isInstance` và `Class.asSubclass`

## Mục tiêu

Khi làm việc với dữ liệu kiểu `Object`, một tên lớp được tải động, hoặc một API dùng generics, bạn đôi khi không biết kiểu cụ thể ngay tại thời điểm biên dịch. `java.lang.Class<T>` cung cấp một **type token** đại diện cho một kiểu ở runtime. Ba phương thức quan trọng của nó — **`cast`**, **`isInstance`**, và **`asSubclass`** — giúp kiểm tra hoặc giới hạn kiểu một cách có căn cứ mà không phải rải unchecked casts.

Sau bài này, bạn có thể:

- Phân biệt câu hỏi “đối tượng này có thuộc kiểu mong đợi không?” với “lớp này có là subtype của lớp/interface yêu cầu không?”.
- Sử dụng `Class<T>.isInstance(Object)` để kiểm tra **một instance**, kể cả khi token `Class<T>` được truyền từ bên ngoài.
- Sử dụng `Class<T>.cast(Object)` để **kiểm tra runtime và lấy lại giá trị kiểu `T`**.
- Sử dụng `Class.asSubclass(Class<U>)` để kiểm tra **quan hệ kế thừa của đối tượng `Class`** và thu hẹp từ `Class<?>` thành `Class<? extends U>`.
- Dự đoán chính xác `false`, `null` và `ClassCastException` trong các trường hợp đúng/sai kiểu.
- Hiểu giới hạn của type erasure: `List.class` không kiểm tra được một `List` đang chứa toàn `String` hay không.
- Xây adapter kiểm tra danh sách từ API cũ, hoặc tải một implementation cụ thể bằng reflection có kiểm soát.
- Phân biệt ba API với `instanceof`, phép cast thường, `Class.isAssignableFrom`, `getClass` và generic metadata.

**Yêu cầu:** JDK 17 trở lên. Các ví dụ dùng `javac`, `java`, `javap`, không cần thư viện ngoài. Ba chương trình hoàn chỉnh được kiểm chứng bằng Java 17 và `-Xlint:all`; code nhỏ ghi “minh họa” cần đặt vào class/method thích hợp.

## 1. Bắt đầu từ `Class<T>` — type token tại runtime

Trong Java, các biểu thức:

```java
String.class
Integer.class
Number.class
java.util.List.class
```

trả những đối tượng `Class` biểu diễn các lớp/interface tương ứng. Về compile-time, chẳng hạn `String.class` có kiểu `Class<String>`; `Number.class` có kiểu `Class<Number>`.

Một method generic có thể yêu cầu token tường minh:

```java
static <T> T readAs(Object source, Class<T> expected) {
    return expected.cast(source);
}
```

Lời gọi minh họa:

```java
String name = readAs("Java", String.class);
Integer age = readAs(18, Integer.class);
```

Compiler suy luận `T` từ token, nhờ đó kiểu trả về của `readAs` rõ ràng ở source. Khi chạy, `Class.cast` so đối tượng thật với kiểu mà token chỉ tới. Nó không tự chuyển số nguyên sang chuỗi hoặc chuyển mọi giá trị về kiểu mong đợi.

**Phân biệt:** `Class<T>` là loại token dành cho một lớp/interface/kiểu runtime cụ thể; nó **không** có nghĩa JVM tạo class `List<String>` riêng. Với `List<String>`, token class thông thường là `List.class`, chưa có thông tin `String` của các phần tử.

## 2. Bảng đối chiếu ba phương thức

| API | Receiver | Đối số | Trả về | Khi không phù hợp |
| --- | --- | --- | --- | --- |
| `Class<T>.isInstance(Object obj)` | Token kiểu cần kiểm tra | **Instance** hoặc `null` | `boolean` | `false` |
| `Class<T>.cast(Object obj)` | Token kiểu đích | **Instance** hoặc `null` | `T` | `ClassCastException` nếu non-null và không hợp kiểu |
| `Class<T>.asSubclass(Class<U> superclass)` | Một đối tượng **`Class`** có sẵn | Class/interface cần làm supertype | `Class<? extends U>` | `ClassCastException` nếu receiver không là subtype của đối số |

Ba ví dụ cho cùng tên lớp `Dog`:

```java
Object animal = new Dog();
boolean yes = Animal.class.isInstance(animal);
Dog dog = Dog.class.cast(animal);
Class<? extends Animal> type = Dog.class.asSubclass(Animal.class);
```

Đây là đoạn mã minh họa giả định `Dog extends Animal` và đang ở trong class/method phù hợp. Hai câu đầu thao tác với một **đối tượng `Dog`**, câu thứ ba thao tác với **đối tượng `Class` đại diện cho `Dog`**.

Mẹo nhận diện:

- `isInstance`: **hỏi** về đối tượng, trả đúng/sai.
- `cast`: **lấy đối tượng với kiểu mong muốn**, lỗi nếu không tương thích.
- `asSubclass`: **hỏi về quan hệ giữa hai class tokens**, lấy token được giới hạn kiểu hơn.

## 3. `Class.isInstance` — kiểm tra một đối tượng theo token

Cách dùng:

```java
boolean accepted = String.class.isInstance(value);
```

Trong ví dụ này `value` là một `Object` đã có trong scope. `isInstance` trả `true` khi object không `null` và thuộc lớp, subclass hoặc interface tương thích với token.

Ví dụ:

```java
Object x = "hello";
System.out.println(String.class.isInstance(x));       // true
System.out.println(CharSequence.class.isInstance(x)); // true
System.out.println(Integer.class.isInstance(x));      // false
```

**`null` đặc biệt quan trọng:** `String.class.isInstance(null)` là `false`. Điều này không có nghĩa `null` “là String sai kiểu”; nó có nghĩa `isInstance` không xem `null` là một instance của bất kỳ class/interface nào.

Nếu token là **interface**, `isInstance` kiểm tra object có implement interface ấy không. Nếu token là **array class** như `String[].class`, nó kiểm tra object có thuộc array class tương ứng theo quan hệ type compatibility của Java không.

Với **primitive class literal** như `int.class`, `int.class.isInstance(Integer.valueOf(7))` trả `false`; một `Integer` wrapper không phải một instance của primitive `int`. Reflection API này không thực hiện boxing/unboxing để “biến” đối tượng thành instance của primitive class.

### So với `instanceof`

```java
if (value instanceof String) { ... }
```

và:

```java
if (String.class.isInstance(value)) { ... }
```

cùng trả lời câu hỏi loại object trong trường hợp `String.class` cố định, nhưng `isInstance` có điểm mạnh: **Class token có thể là tham số runtime**.

```java
static boolean matches(Object item, Class<?> type) {
    return type.isInstance(item);
}
```

Đặt đoạn này vào một class; caller có thể truyền `String.class`, `Number.class` hoặc interface token tùy tình huống. Cách viết `instanceof` thường cần tên kiểu có thể biểu diễn trực tiếp ở source; Java 17 không thể viết một cách tổng quát `value instanceof T` với một type variable `T` không reifiable.

**Không đồng nhất `instanceof` kiểm tra kiểu với cơ chế pattern matching** như `value instanceof String text`. Pattern matching còn ràng buộc biến typed `text` theo kiểu source; `Class.isInstance` chỉ trả boolean, muốn dùng giá trị dưới kiểu `T` thì cần `Class.cast` hoặc logic có kiểm tra tương ứng.

## 4. `Class.cast` — kiểm tra và thu được giá trị kiểu `T`

Chữ ký khái niệm:

```java
public T cast(Object obj);
```

Với token `Class<T>`, method có thể trả một kết quả được compiler xem là `T`. Ở runtime, nếu `obj` khác `null` nhưng không thuộc kiểu tương thích, method ném `ClassCastException`.

```java
Object candidate = "Java";
String text = String.class.cast(candidate);
System.out.println(text.toUpperCase()); // JAVA
```

Lời gọi sai:

```java
Object candidate = Integer.valueOf(42);
// String text = String.class.cast(candidate);
// Biên dịch được, nhưng ném ClassCastException khi chạy.
```

Nên phân biệt **biên dịch được** với **thành công ở runtime**. `Class.cast` không sửa giá trị `Integer` thành `String`; nó xác minh rằng phép dùng dưới kiểu `T` có hợp lệ hay không.

### `cast(null)` trả gì?

```java
String missing = String.class.cast(null);
System.out.println(missing == null); // true
```

`Class.cast(null)` **trả `null`**, không tự ném `ClassCastException`. Khác với `isInstance(null)` trả `false`, phép cast của một tham chiếu `null` về kiểu tham chiếu vẫn được phép.

Nếu API của bạn **không chấp nhận `null`**, hãy viết chính sách riêng, ví dụ kiểm tra `Objects.requireNonNull` hoặc `isInstance` và ném một exception có ngữ cảnh. Không suy luận rằng chỉ dùng `Class.cast` đã loại `null` khỏi đầu vào.

### `Class.cast` so với phép cast thông thường

```java
String a = (String) candidate;
String b = String.class.cast(candidate);
```

Khi token cố định, hai cách đều có thể gây `ClassCastException` nếu object sai kiểu. Nhưng `Class.cast` phù hợp khi kiểu đích đến từ **biến `Class<T>` ở runtime**, trong khi một cast trực tiếp như `(T) candidate` trong generic method có thể là **unchecked cast** hoặc chỉ cast theo upper bound sau type erasure.

Ví dụ:

```java
static <T> T checked(Object input, Class<T> expected) {
    return expected.cast(input);
}
```

Cách này giúp compiler biết kết quả là `T` và thực hiện kiểm tra bằng token thật được truyền vào.

## 5. `Class.asSubclass` — thu hẹp **class token**, không phải instance

Đây là phương thức dễ bị đọc nhầm nhất.

Chữ ký:

```java
public <U> Class<? extends U> asSubclass(Class<U> clazz);
```

Giả sử bạn đang có:

```java
Class<?> discovered = Dog.class;
```

Bạn chưa biết class được phát hiện có liên quan gì tới `Animal`. Có thể kiểm tra:

```java
Class<? extends Animal> type =
        discovered.asSubclass(Animal.class);
```

Nếu `discovered` biểu diễn `Dog` và `Dog extends Animal`, lời gọi thành công. Giá trị trả về vẫn đại diện cho chính class `Dog`, nhưng **kiểu compile-time được thu hẹp** thành `Class<? extends Animal>`.

Nếu:

```java
Class<?> discovered = String.class;
// Class<? extends Animal> type = discovered.asSubclass(Animal.class);
```

thì lời gọi ném `ClassCastException`, vì `String` không là subtype của `Animal`.

### Chính xác `asSubclass` kiểm tra gì?

Nó kiểm tra **class do receiver biểu diễn có thể được xem là subclass/subtype của kiểu do đối số biểu diễn không**. Kiểu đích có thể là class hoặc interface. Ví dụ một class `Dog implements Pet` thì:

```java
Class<? extends Pet> token = Dog.class.asSubclass(Pet.class);
```

hợp lệ, kể cả khi `Pet` là interface. Về mặt kiểm tra quan hệ subtype, bản thân class đích bằng receiver cũng hợp lệ: `Animal.class.asSubclass(Animal.class)` thành công.

**Đừng suy diễn từ tên `asSubclass` rằng method tạo class con mới.** Nó không xây class, không sửa inheritance hierarchy, không tạo object và không tự gọi constructor. Nó trả về một đối tượng `Class` biểu diễn class đã có, dưới một kiểu generic bảo đảm giới hạn subtype.

### `Class<?>` và `Class<? extends Animal>`

- `Class<?>`: một class token chưa ràng buộc với loại cha cụ thể.
- `Class<? extends Animal>`: token đại diện cho class có thể dùng như `Animal` hoặc subclass của nó.

Sau bước `asSubclass`, một API có thể làm việc với những class tokens đã qua kiểm tra thay vì dùng unchecked cast:

```java
Class<? extends Animal> safeType =
        unknownClass.asSubclass(Animal.class);
```

Phần tiếp theo minh họa mẫu này với interface plugin/service.

## 6. Chương trình A — Đối chiếu cả ba API trên cùng mô hình kiểu

Tạo tệp **`ClassReflectionDemo.java`**:

```java
import java.util.List;

public class ClassReflectionDemo {
    interface Pet {
        String sound();
    }

    static class Animal {}

    static final class Dog extends Animal implements Pet {
        @Override
        public String sound() {
            return "woof";
        }
    }

    static final class Cat extends Animal {}

    public static void main(String[] args) {
        Object candidate = new Dog();

        System.out.println("animal instance: "
                + Animal.class.isInstance(candidate));
        System.out.println("dog instance: "
                + Dog.class.isInstance(candidate));
        System.out.println("cat instance: "
                + Cat.class.isInstance(candidate));
        System.out.println("null instance: "
                + Dog.class.isInstance(null));
        System.out.println("interface instance: "
                + Pet.class.isInstance(candidate));
        System.out.println("primitive instance: "
                + int.class.isInstance(Integer.valueOf(7)));
        System.out.println("array instance: "
                + String[].class.isInstance(new String[2]));

        Dog dog = Dog.class.cast(candidate);
        System.out.println("cast result: " + dog.sound());
        System.out.println("cast null: "
                + (Dog.class.cast(null) == null));

        try {
            Cat.class.cast(candidate);
        } catch (ClassCastException ex) {
            System.out.println("bad cast: "
                    + ex.getClass().getSimpleName());
        }

        Class<?> runtime = candidate.getClass();
        Class<? extends Animal> subtype =
                runtime.asSubclass(Animal.class);
        System.out.println("subclass: "
                + subtype.getSimpleName());

        Class<? extends Pet> petSubtype =
                runtime.asSubclass(Pet.class);
        System.out.println("interface subclass: "
                + petSubtype.getSimpleName());

        try {
            String.class.asSubclass(Animal.class);
        } catch (ClassCastException ex) {
            System.out.println("bad subclass: "
                    + ex.getClass().getSimpleName());
        }

        List<Integer> numbers = List.of(1, 2);
        Object collection = numbers;
        System.out.println("list instance: "
                + List.class.isInstance(collection));
        System.out.println("list cast: "
                + List.class.cast(collection).get(0));
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:all ClassReflectionDemo.java
java ClassReflectionDemo
```

**Stdout đã kiểm chứng:**

```text
animal instance: true
dog instance: true
cat instance: false
null instance: false
interface instance: true
primitive instance: false
array instance: true
cast result: woof
cast null: true
bad cast: ClassCastException
subclass: Dog
interface subclass: Dog
bad subclass: ClassCastException
list instance: true
list cast: 1
```

Chương trình biên dịch thành công, không phát cảnh báo với `-Xlint:all`.

### Phân tích các kiểm tra instance

`candidate` trỏ tới đối tượng `Dog`.

- `Animal.class.isInstance(candidate)` trả `true` vì `Dog extends Animal`.
- `Dog.class.isInstance(candidate)` trả `true` vì là instance `Dog`.
- `Cat.class.isInstance(candidate)` trả `false`: cả `Dog` và `Cat` cùng là subclass của `Animal`, nhưng không phải subtype của nhau.
- `Pet.class.isInstance(candidate)` trả `true` vì `Dog implements Pet`.
- `Dog.class.isInstance(null)` trả `false`.
- `int.class.isInstance(Integer.valueOf(7))` trả `false`, không chuyển wrapper thành primitive instance.
- `String[].class.isInstance(new String[2])` trả `true` vì đối tượng thực là mảng `String[]`.

Các câu lệnh `isInstance` **không thay đổi dữ liệu**, cũng không làm compiler tự coi biến `candidate: Object` là `Dog`. Chúng chỉ trả thông tin đúng/sai.

### Phân tích các kiểm tra cast

`Dog.class.cast(candidate)` trả chính đối tượng `Dog` để dùng dưới kiểu `Dog`; sau đó `sound()` cho `woof`.

`Dog.class.cast(null)` trả `null`.

`Cat.class.cast(candidate)` thất bại bằng `ClassCastException` dù cả `Cat` và `Dog` đều là `Animal`. Cast không chuyển đổi runtime class của object.

### Phân tích `asSubclass`

`candidate.getClass()` trả runtime token cho `Dog`. Do `Dog` là subtype của `Animal` **và** implement `Pet`, hai lời gọi `asSubclass` đều thành công.

`String.class.asSubclass(Animal.class)` thất bại vì quan hệ subtype không đúng. Sự thất bại ở đây nói về **quan hệ giữa hai đối tượng `Class`**, khác với lỗi `Cat.class.cast(candidate)` nói về **instance `candidate`**.

## 7. `Class.isAssignableFrom` liên quan thế nào đến `asSubclass`?

Một phương thức phản chiếu khác của `Class` là:

```java
boolean parentAcceptsChild =
        Animal.class.isAssignableFrom(Dog.class);
```

Kết quả là `true`: biến kiểu `Animal` có thể giữ tham chiếu tới một `Dog`.

Quy tắc hướng rất quan trọng:

```java
Animal.class.isAssignableFrom(Dog.class) // true
Dog.class.isAssignableFrom(Animal.class) // false
```

Các đoạn này là biểu thức minh họa đặt trong method có khai báo `Animal`/`Dog`.

Đối chiếu với `asSubclass`:

```java
Class<?> found = Dog.class;
Class<? extends Animal> typed =
        found.asSubclass(Animal.class);
```

Ta có thể hình dung bước kiểm tra là **`Animal.class.isAssignableFrom(found)`**. Nếu thành công, `asSubclass` còn **cung cấp kết quả được giới hạn kiểu** `Class<? extends Animal>`; còn `isAssignableFrom` chỉ trả boolean.

| Method | Receiver | Tham số | Trả về |
| --- | --- | --- | --- |
| `Animal.class.isAssignableFrom(Dog.class)` | Class cha dự kiến | Class con có thể gán | `true` |
| `Dog.class.asSubclass(Animal.class)` | Class con thực tế | Class cha/interface bắt buộc | Token `Class<? extends Animal>` |
| `Animal.class.isInstance(new Dog())` | Class cha dự kiến | **Đối tượng** | `true` |

Đảo sai hướng là lỗi reflection phổ biến. Hãy tự hỏi: “Biến kiểu nào cần nhận một object của class nào?” Nếu biến `Animal` giữ được `Dog`, thì `Animal.class.isAssignableFrom(Dog.class)` đúng.

## 8. Generic erasure: những phép kiểm tra này **không** xác thực type arguments lồng nhau

Ví dụ:

```java
List<Integer> integers = List.of(1, 2);
Object object = integers;

System.out.println(List.class.isInstance(object)); // true
List rawView = List.class.cast(object);
```

`List.class` chỉ nói về **interface `List` ở runtime**. Nó không lưu riêng đối số `Integer` hay `String` của instance.

Vì vậy, một lỗi thiết kế là nghĩ rằng:

```java
// KHÔNG HỢP LỆ trong Java:
// Class<List<String>> token = List<String>.class;
```

Không tồn tại class literal `List<String>.class` tương ứng với một runtime class riêng. Một đối tượng `List<Integer>` và một đối tượng `List<String>` đều có thể thỏa `List.class.isInstance(...)`.

Đặc biệt **không** thực hiện unchecked cast từ `List.class.cast(rawObject)` sang `List<String>` rồi coi đó là kiểm chứng. `Class.cast` tại đây chỉ kiểm tra object có implement `List`; nó không duyệt từng phần tử.

Nếu cần kết quả `List<String>` an toàn từ một nguồn chưa xác định, hãy:

1. Xác minh nguồn là một `List` ở runtime.
2. Duyệt các phần tử.
3. Kiểm tra mỗi phần tử thuộc `String.class` hoặc một chính sách chuyển đổi rõ ràng.
4. Tạo danh sách typed mới, tránh để tham chiếu raw mutable tiếp tục can thiệp vào dữ liệu.

Mẫu đầy đủ ở phần tiếp theo sử dụng `Class<T>` token để tổng quát hóa bước kiểm tra.

## 9. Chương trình B — Xác minh dữ liệu từ API legacy bằng `Class<T>`

Tạo tệp **`TypedBoundaryDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TypedBoundaryDemo {
    static <T> T requireNonNullType(Object value, Class<T> expected) {
        Objects.requireNonNull(expected, "expected");
        if (!expected.isInstance(value)) {
            throw new IllegalArgumentException(
                    "Expected " + expected.getSimpleName());
        }
        return expected.cast(value);
    }

    static <T> List<T> checkedCopy(List<?> source,
                                   Class<T> expected) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(expected, "expected");

        List<T> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            Object value = source.get(i);
            if (!expected.isInstance(value)) {
                throw new IllegalArgumentException(
                        "Invalid element at " + i);
            }
            result.add(expected.cast(value));
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        Object input = "Java";
        System.out.println("scalar: "
                + requireNonNullType(input, String.class));

        List<?> oldData = List.of("Lan", "Minh");
        System.out.println("checked: "
                + checkedCopy(oldData, String.class));

        try {
            checkedCopy(List.of("Lan", 42), String.class);
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }

        System.out.println("class cast null: "
                + (String.class.cast(null) == null));
        try {
            requireNonNullType(null, String.class);
        } catch (IllegalArgumentException ex) {
            System.out.println("null rejected: "
                    + ex.getMessage());
        }

        List<Integer> integers = List.of(10);
        System.out.println("list token permits integers: "
                + List.class.isInstance(integers));
        System.out.println("list class cast first: "
                + List.class.cast(integers).get(0));
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:all TypedBoundaryDemo.java
java TypedBoundaryDemo
```

**Stdout đã kiểm chứng:**

```text
scalar: Java
checked: [Lan, Minh]
rejected: Invalid element at 1
class cast null: true
null rejected: Expected String
list token permits integers: true
list class cast first: 10
```

Không có cảnh báo `rawtypes` hoặc `unchecked` trong chương trình này với `-Xlint:all`.

### `requireNonNullType` dùng phối hợp hai method để làm gì?

```java
if (!expected.isInstance(value)) {
    throw new IllegalArgumentException(...);
}
return expected.cast(value);
```

`isInstance` giúp áp dụng **chính sách dữ liệu nghiệp vụ**, ở đây là không chấp nhận `null` hoặc giá trị sai kiểu, và ném `IllegalArgumentException` có thông điệp do API kiểm soát.

Sau đó `cast` trả `T` dưới một kiểm tra runtime đúng với token. Không cần viết unchecked cast `(T)value`.

Nếu API muốn cho phép `null`, có thể dùng `expected.cast(value)` trực tiếp với token không `null`, và giải thích rõ trong contract rằng kết quả có thể `null`. Sự lựa chọn về `null` là **chính sách API**, không phải một giới hạn ngầm của `Class.cast`.

### `checkedCopy` giải quyết giới hạn của `List.class`

`checkedCopy(List<?> source, Class<T> expected)` nhận **danh sách chưa biết kiểu phần tử**. Nó lấy từng phần tử như `Object`, kiểm tra `expected.isInstance`, rồi thêm `expected.cast(value)` vào `ArrayList<T>`.

Với `List.of("Lan","Minh")`, tất cả phần tử là `String`, trả `List<String>` hợp lệ.

Với `List.of("Lan",42)`, phần tử ở index 1 là `Integer`, nên adapter **từ chối ngay tại ranh giới**, thay vì để `ClassCastException` xảy ra muộn khi code nghiệp vụ đọc giá trị.

`List.copyOf(result)` trả một list không sửa được bằng các thao tác list thông thường và không giữ cấu trúc mutable ban đầu. Tuy vậy, với những phần tử `T` có state mutable, copy list **không** tự biến chúng thành immutable sâu.

### `List.class` không biết bên trong chứa gì

Hai dòng cuối cố ý minh họa:

```text
list token permits integers: true
list class cast first: 10
```

`List<Integer>` vẫn là một `List` runtime. `List.class.cast(integers)` hợp lệ, nhưng chỉ trả một góc nhìn `List` về object ấy, không xác thực nó phù hợp `List<String>`. Vì vậy, adapter cần kiểm tra **từng phần tử** khi phải cung cấp typed collection.

## 10. `Class.asSubclass` trong nạp implementation và cơ chế plugin

Một trường hợp điển hình của `asSubclass` là **nạp class theo tên**, rồi kiểm tra xem nó có implement interface được yêu cầu hay không.

Một chuỗi xử lý điển hình:

```text
Tên class do cấu hình cung cấp
                |
                v
        Class.forName(name)
                |
                v
             Class<?>
                |
                v
   asSubclass(Service.class)
                |
          kiểm tra subtype
          /             \
      hợp lệ           sai kiểu
        |                  |
        v                  v
 Class<? extends      ClassCastException
     Service>
        |
        v
getDeclaredConstructor()
    .newInstance()
        |
        v
   Service instance
```

Chú ý có **hai vấn đề độc lập**:

1. Class được tìm thấy có implement `Service` không? — `asSubclass` giải quyết.
2. Class có thể tạo instance hợp lệ không? — còn phụ thuộc constructor, access, abstract/interface status, ngoại lệ từ constructor, quyền truy cập và những ràng buộc riêng của runtime.

`asSubclass` **không kiểm tra** constructor tồn tại, có public hay không, và cũng không tạo object. Nó chỉ kiểm tra quan hệ subtype để kết quả có một kiểu `Class<? extends Service>` thích hợp.

## 11. Chương trình C — Tải một service class an toàn về kiểu

Tạo tệp **`PluginServiceDemo.java`**:

```java
public class PluginServiceDemo {
    public interface Service {
        String id();
    }

    public static class FastService implements Service {
        public FastService() {}

        @Override
        public String id() {
            return "fast";
        }
    }

    public static class NotAService {
        public NotAService() {}
    }

    static Service load(String className)
            throws ReflectiveOperationException {
        Class<?> unknown = Class.forName(className);
        Class<? extends Service> implementation =
                unknown.asSubclass(Service.class);

        return implementation
                .getDeclaredConstructor()
                .newInstance();
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Service service = load(FastService.class.getName());
        System.out.println("loaded: " + service.id());

        try {
            load(NotAService.class.getName());
        } catch (ClassCastException ex) {
            System.out.println("wrong plugin type: "
                    + ex.getClass().getSimpleName());
        }

        try {
            load("not.existing.Plugin");
        } catch (ClassNotFoundException ex) {
            System.out.println("missing plugin: "
                    + ex.getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:all PluginServiceDemo.java
java PluginServiceDemo
```

**Stdout đã kiểm chứng:**

```text
loaded: fast
wrong plugin type: ClassCastException
missing plugin: ClassNotFoundException
```

Chương trình chạy thành công và không có cảnh báo compiler với `-Xlint:all`.

### Phân tích từng tình huống

**Tải `FastService`:** `Class.forName` trả `Class<?>`, tức biết class thực nhưng chưa có ràng buộc generic về interface. `asSubclass(Service.class)` xác minh nó implement `Service`. `getDeclaredConstructor().newInstance()` tạo instance thông qua public no-arg constructor trong mẫu.

**Tải `NotAService`:** tên class có thật, `Class.forName` thành công. Nhưng class không implement `Service`, nên `asSubclass` ném `ClassCastException`. Vấn đề là **sai quan hệ kiểu**, không phải thiếu class.

**Tải tên không tồn tại:** `Class.forName` ném `ClassNotFoundException` trước khi gọi `asSubclass`. Đây là lỗi nạp class, khác lỗi kiểm tra subtype.

### Những lưu ý khi áp dụng thật

- Nếu class là abstract/interface, thiếu constructor phù hợp hoặc constructor ném lỗi, bước tạo instance vẫn có thể thất bại.
- `newInstance()` trên `Constructor` có thể ném những ngoại lệ reflection như `InstantiationException`, `IllegalAccessException`, `InvocationTargetException` hoặc `NoSuchMethodException`; cần xử lý theo hợp đồng thực tế.
- Java module access có thể hạn chế phản chiếu những thành viên không được mở quyền.
- Không nạp/thực thi **tên class tùy ý từ nguồn không đáng tin** chỉ vì `asSubclass` thành công. Việc implement `Service` không đồng nghĩa mã đó an toàn để chạy. Trong sản phẩm nên dùng registry/allowlist và quy trình tin cậy riêng.

Mẫu minh họa chỉ dùng hai nested classes trong chương trình của chính bạn; không thực hiện cơ chế cài đặt plugin bên ngoài.

## 12. Tổng hợp ba dạng “kiểm tra kiểu” qua một luồng dữ liệu

Một hệ thống có thể có cả class name, object và collection chưa rõ kiểu:

```text
CONFIG: "com.example.FastService"
        |
        +--> Class.forName(...) -> Class<?>
                |
                +--> asSubclass(Service.class)
                |        |
                |        +--> Class<? extends Service>
                |
                +--> Constructor.newInstance()
                         |
                         +--> Object thực

DATA: Object input
        |
        +--> Service.class.isInstance(input) -> boolean
        |
        +--> Service.class.cast(input)       -> Service

COLLECTION: List<?> data
        |
        +--> duyệt từng Object
                |
                +--> type.isInstance(element)
                +--> type.cast(element)
                |
                +--> List<T> mới đã được xác minh
```

Tùy điểm dữ liệu, chọn đúng công cụ:

- Nếu **đã có object**, cần hỏi nó thuộc kiểu đích không: `isInstance`.
- Nếu **đã có object**, muốn đưa giá trị sang kiểu generic `T`: `cast`.
- Nếu **chỉ có một Class<?>**, muốn bảo đảm class ấy là subtype của interface/class yêu cầu: `asSubclass`.
- Nếu **có collection từ nguồn bất định**, ba API không thể một mình khôi phục `List<String>` qua erasure; hãy kiểm tra từng phần tử bằng class token.

## 13. Sự khác nhau giữa lỗi `ClassCastException` và kết quả `false`

Cùng một object `Dog` không phải `Cat`:

```java
Object obj = new Dog();
boolean check = Cat.class.isInstance(obj); // false
// Cat cat = Cat.class.cast(obj);           // ClassCastException
```

`isInstance` thích hợp với **nhánh điều kiện**, kiểm tra đầu vào và validation mềm. `cast` thích hợp khi kiểu đích là **điều kiện bắt buộc** và sai kiểu phải là lỗi của luồng thực thi.

Tương tự với class token:

```java
Class<?> found = String.class;
// Class<? extends Animal> t = found.asSubclass(Animal.class);
// ClassCastException vì String không extends Animal.
```

Nếu chỉ cần hỏi mà không muốn exception, bạn có thể dùng:

```java
boolean ok = Animal.class.isAssignableFrom(found); // false
```

Vậy nên quyết định giữa boolean hay exception là **quyết định về API và luồng lỗi**, không phải method nào “mạnh hơn” theo nghĩa tự sửa dữ liệu.

### Các tình huống `null` cần quy định

- `Animal.class.isInstance(null)` → `false`.
- `Animal.class.cast(null)` → `null`.
- Gọi một instance method trên biến `Class<?> token = null` (ví dụ `token.cast(obj)`) → `NullPointerException` do receiver null.
- Đối số `Class<U>` của `asSubclass` phải là token hợp lệ; trong API nên bắt buộc non-null thay vì coi null như một supertype.
- `Class.forName(null)` cũng không phải một yêu cầu tìm class hợp lệ; cần validation cấu hình trước khi nạp.

Nếu contract đòi **giá trị có mặt và đúng kiểu**, hãy kết hợp `Objects.requireNonNull` và `Class.cast`, hoặc áp dụng `isInstance` rồi báo lỗi có ngữ cảnh như `TypedBoundaryDemo`.

## 14. Các kiểu đặc biệt: interface, array, primitive

Không chỉ class thường mới có thể dùng làm token.

### Interface token

```java
CharSequence.class.isInstance(new StringBuilder("A"))
```

trả `true` vì `StringBuilder implements CharSequence`.

Với `asSubclass`, class thực hiện interface cũng thỏa:

```java
StringBuilder.class.asSubclass(CharSequence.class)
```

kết quả là `Class<? extends CharSequence>` biểu diễn `StringBuilder`.

### Array token

```java
String[].class.isInstance(new String[]{"A", "B"})
```

trả `true`; các array class có runtime identity và component type. Nhưng đừng dùng `List.class` như một token thay thế cho kiểu tham số hóa lồng nhau `List<String>`.

### Primitive token

```java
int.class.isInstance(Integer.valueOf(7))
```

trả `false`: `Integer` không phải runtime instance của primitive `int`. Khi muốn kiểm tra một object chứa giá trị nguyên được boxing, hãy dùng `Integer.class` thay vì `int.class`. Boxing/unboxing của Java không biến `Class.isInstance` thành một phép chuyển đổi tùy ý.

## 15. `Class.cast` không phải phép chuyển đổi dữ liệu

Một hiểu lầm quen thuộc:

```java
Object number = Integer.valueOf(17);
// String text = String.class.cast(number); // Lỗi runtime.
```

`Class.cast` **kiểm tra tương thích kiểu**, không chuyển `17` thành `"17"`. Nếu thật sự muốn chuyển số sang văn bản, dùng API conversion đúng nghĩa:

```java
String text = String.valueOf(number); // "17"
```

Tương tự, `Class.cast` không phân tích một chuỗi `"17"` thành `Integer`; đó là việc của `Integer.parseInt` hoặc parser phù hợp.

Phân biệt hai câu hỏi:

- “Đối tượng này **đã là** kiểu `T` chưa?” → `Class.isInstance`/`Class.cast`.
- “Tôi muốn **biến đổi** giá trị từ miền dữ liệu A sang B” → conversion/parser/mapper.

Đánh đồng sẽ khiến API reflection phát `ClassCastException` không mong muốn và khó debug.

## 16. Phản chiếu kiểu và erasure trong bytecode

Xét method trong chương trình `TypedBoundaryDemo`:

```java
static <T> T requireNonNullType(Object value, Class<T> expected) {
    Objects.requireNonNull(expected, "expected");
    if (!expected.isInstance(value)) {
        throw new IllegalArgumentException(
                "Expected " + expected.getSimpleName());
    }
    return expected.cast(value);
}
```

Chạy sau khi biên dịch:

```bash
javap -p -s TypedBoundaryDemo
```

Một phần output đã được xác minh:

```text
static <T> T requireNonNullType(java.lang.Object, java.lang.Class<T>);
  descriptor: (Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;

static <T> java.util.List<T> checkedCopy(
    java.util.List<?>, java.lang.Class<T>);
  descriptor: (Ljava/util/List;Ljava/lang/Class;)Ljava/util/List;
```

Generic signature metadata mà `javap` hiển thị có `T`, `List<T>` và `Class<T>`, nhưng **JVM descriptors** dùng `Object`, `List`, `Class` sau erasure.

Nhờ token `Class<T>` được truyền tường minh, thân method **vẫn kiểm tra được runtime class** của object, kể cả khi generic method `<T>` bị xóa ở descriptor. Đây là cách **đưa thông tin loại dữ liệu runtime vào API một cách chủ động**, khác với việc trông chờ `T` tự được “phục hồi” từ erasure.

Khi code typed gán kết quả `requireNonNullType(..., String.class)` vào biến `String`, compiler còn có thể sinh cast ở điểm gọi để thực hiện hợp đồng source. Sự tồn tại của token và những cast compiler chèn hỗ trợ type safety; chúng không tạo specialized class riêng cho `T=String`.

## 17. Không dùng `getClass()` để đoán đối số generic tùy ý

Ví dụ:

```java
List<String> names = new java.util.ArrayList<>();
List<Integer> counts = new java.util.ArrayList<>();
boolean same = names.getClass() == counts.getClass(); // true
```

Hai biến có đối số generic khác nhau nhưng cùng runtime class `ArrayList`. Gọi `getClass()` lên một object collection không đủ để biết `String` hay `Integer` là đối số kiểu được khai báo ở source.

Tương tự, `Class<List>` dựa trên `List.class` chỉ kiểm tra object có là `List`, **không kiểm tra `List<String>`**. Nếu code cần kiểm tra một giá trị complex như `Map<String,Integer>`, phải dùng schema/metadata phù hợp và kiểm tra key/value cụ thể; một `Class<Map>` token không đủ.

Reflection có thể đọc generic metadata ở **khai báo field, method, superclass** nhờ `Type`/`ParameterizedType`, nhưng đó là thông tin của **vị trí khai báo**, không phải phép giải mã tùy ý type arguments của mọi object runtime.

Vì vậy, ngay cả khi dùng cả `Class.cast`, `isInstance` và `asSubclass`, hãy giữ rõ ranh giới: chúng làm việc với class/interface runtime, không phải bộ kiểm tra tự động cho mọi generic type argument sau erasure.

## 18. Nên chọn API nào trong một tình huống thực tế?

| Nhu cầu | API ưu tiên | Giải thích |
| --- | --- | --- |
| Hỏi object có là subtype/implementation không | `Class.isInstance` | Trả boolean, dùng được với token runtime |
| Bắt buộc object phải thuộc kiểu đích `T` | `Class.cast` | Trả typed value hoặc ném `ClassCastException` |
| Kiểm tra hai class tokens có quan hệ subtype không | `Class.isAssignableFrom` | Trả boolean, cần chú ý hướng receiver |
| Muốn biến `Class<?>` thành `Class<? extends U>` đã kiểm tra | `Class.asSubclass` | Runtime subtype check và thu hẹp generic token |
| Tìm class từ tên | `Class.forName` | Nạp/tra class; chưa bảo đảm implement interface |
| Tạo instance bằng reflection | `getDeclaredConstructor().newInstance()` | Cần class hợp lệ, constructor và quyền truy cập |
| Cần xác thực `List<String>` từ dữ liệu động | Duyệt phần tử với `String.class.isInstance/cast` | Token `List.class` không đủ do erasure |
| Cần chuyển `Integer` thành `String` | Conversion API như `String.valueOf` | `Class.cast` không chuyển dữ liệu |
| Kiểm tra đúng kiểu tại compile time | Generic type `List<T>`, `Function<T,R>` | Hạn chế unchecked casts trước khi chạy |

Từng công cụ giải quyết một tầng khác nhau. Một thiết kế rõ ràng thường phối hợp chúng ở ranh giới hệ thống thay vì cố dùng một method để kiểm tra mọi cấu trúc dữ liệu.

## 19. Những lỗi phổ biến cần tránh

**Lỗi 1 — Nghĩ `asSubclass` ép object thành subclass.** Nó nhận và trả **đối tượng `Class`**, không nhận một `Object` nghiệp vụ như `Dog`. Nếu đã có object, dùng `isInstance` hoặc `cast`.

**Lỗi 2 — Nghĩ `isInstance` trả một object typed.** Nó chỉ trả `boolean`. Nếu cần `T`, dùng `cast` sau khi kiểm tra hoặc để `cast` tự báo lỗi khi sai kiểu.

**Lỗi 3 — Nghĩ `cast(null)` tự từ chối null.** `Class.cast(null)` trả `null`. Nếu null không hợp đồng, phải kiểm tra riêng.

**Lỗi 4 — Đảo hướng `isAssignableFrom`.** `Animal.class.isAssignableFrom(Dog.class)` là đúng, không phải ngược lại. Trong `asSubclass`, receiver thường là class phát hiện, đối số là kiểu cha bắt buộc.

**Lỗi 5 — Nghĩ `List.class.cast(list)` đã chứng minh list có phần tử `String`.** Runtime chỉ kiểm tra đây là một `List`. Việc xác thực generic elements cần kiểm tra từng phần tử/schema.

**Lỗi 6 — Viết unchecked cast `(Class<? extends Service>) unknown` mà chưa xác minh.** Có thể tạo cảm giác type safety không đúng. `unknown.asSubclass(Service.class)` diễn đạt một runtime subtype check thực tế.

**Lỗi 7 — Coi `Class.cast` là parser hoặc converter.** Cast không biến `Integer` thành `String`, không sửa giá trị không tương thích.

**Lỗi 8 — Giả định class implement interface luôn khởi tạo được.** `asSubclass` không kiểm tra constructor, access, abstract status hoặc logic constructor; bước instantiate cần handling riêng.

**Lỗi 9 — Nạp class tùy ý không kiểm soát từ cấu hình không đáng tin.** Kiểm tra subtype không làm mã bên trong class an toàn; cần allowlist hoặc cơ chế tin cậy.

**Lỗi 10 — Lầm tưởng `getClass()` mang type argument `String` của `List<String>`.** Generic erasure khiến `getClass()` không thể tự suy ra đối số kiểu tùy ý từ object collection.

**Lỗi 11 — Nghĩ `isInstance` cho primitive như wrapper do boxing.** `int.class.isInstance(Integer.valueOf(1))` vẫn `false`; dùng wrapper token khi kiểm tra object.

**Lỗi 12 — Bỏ qua chính sách null và lỗi đầu vào.** Một API trả `false`, một API trả `null`, một API ném `ClassCastException`; cần chọn cách xử lý phù hợp thay vì chỉ đổi cú pháp.

## 20. Bài tập kiểm tra hiểu biết

### Bài 1 — Dự đoán kết quả `isInstance`

Cho:

```java
Object value = new StringBuilder("x");
boolean a = CharSequence.class.isInstance(value);
boolean b = String.class.isInstance(value);
boolean c = Object.class.isInstance(null);
```

**Đáp án:** `a=true`, `b=false`, `c=false`. `StringBuilder` implement `CharSequence` nhưng không phải `String`; `null` không là instance của class nào.

### Bài 2 — Dự đoán `Class.cast`

```java
Object value = Integer.valueOf(9);
Number n = Number.class.cast(value);
// String s = String.class.cast(value);
```

**Đáp án:** cast về `Number` thành công, còn cast về `String` biên dịch được nếu được sử dụng nhưng ném `ClassCastException` khi chạy.

### Bài 3 — `asSubclass` có thay đổi object không?

```java
Class<?> unknown = Integer.class;
Class<? extends Number> numberType =
        unknown.asSubclass(Number.class);
```

**Đáp án:** thành công vì `Integer extends Number`. Không tạo đối tượng `Integer` và không sửa class; chỉ trả token cùng runtime class dưới kiểu `Class<? extends Number>`.

### Bài 4 — Đảo chiều subtype

```java
boolean a = Number.class.isAssignableFrom(Integer.class);
boolean b = Integer.class.isAssignableFrom(Number.class);
```

**Đáp án:** `a=true`, `b=false`. Một biến `Number` giữ được `Integer`, nhưng một biến `Integer` không giữ được mọi `Number`.

### Bài 5 — `null` với hai API

```java
boolean a = String.class.isInstance(null);
String b = String.class.cast(null);
```

**Đáp án:** `a=false`, `b=null`. Muốn loại null từ input, thêm quy tắc API riêng.

### Bài 6 — Interface plugin

Bạn có `Class<?> loaded` do `Class.forName` trả, muốn biết class có implement `Runnable` và chỉ tạo instance nếu hợp lệ.

**Đáp án định hướng:** `Class<? extends Runnable> type = loaded.asSubclass(Runnable.class)`. Nếu kiểm tra qua, tiếp tục xác minh constructor và quyền truy cập rồi mới gọi `newInstance`; nếu không, `ClassCastException` thể hiện sai quan hệ kiểu. Không dùng unchecked cast trực tiếp từ `Class<?>`.

### Bài 7 — Generic collection từ API cũ

`List.class.isInstance(List.of(1,2))` cho `true`. Có thể kết luận đối tượng là `List<String>` không?

**Đáp án:** không. `List.class` không chứa type argument `String`. Muốn kết quả `List<String>` an toàn, kiểm tra từng phần tử và tạo typed copy như `checkedCopy`.

### Bài 8 — Lựa chọn giữa boolean và exception

Một form validation cần kiểm tra một object là `LocalDate` và tự tạo thông điệp “Ngày nhập không hợp lệ”. Nên dùng `Class.cast` trực tiếp hay `isInstance` rồi báo lỗi?

**Đáp án định hướng:** `LocalDate.class.isInstance(value)` phù hợp để quyết định nhánh và tạo thông báo domain; sau đó có thể dùng `LocalDate.class.cast(value)` để nhận typed value. `cast` trực tiếp hợp lý khi sai kiểu được xem là lỗi lập trình cần `ClassCastException`.

### Bài 9 — Đọc generic method sau erasure

Một method `<T> T requireNonNullType(Object, Class<T>)` có JVM descriptor trả `Object`. Vì sao source vẫn trả `T` an toàn khi dùng `expected.cast(value)`?

**Đáp án:** compiler kiểm tra quan hệ token `Class<T>` và generic return `T`, còn `Class.cast` kiểm tra runtime theo token được truyền. Descriptor erased không xóa việc kiểm tra này và không tạo class runtime chuyên biệt cho `T`.

### Bài 10 — Sửa một nhận định sai

“`asSubclass(Service.class)` thành công chứng minh plugin an toàn để thực thi và có public no-arg constructor.”

**Đáp án:** sai. Nó chỉ chứng minh class implement/extends kiểu `Service` theo runtime subtype relation. Constructor, access, khả năng tạo instance và mức độ tin cậy của code là những kiểm tra riêng.

## 21. Checklist trước khi dùng Java Reflection type token

- [ ] Mình đang kiểm tra một **instance** hay một **class token**?
- [ ] Nếu chỉ cần đúng/sai trên object, đã chọn `isInstance`?
- [ ] Nếu cần object typed `T`, đã chọn `cast` và có chính sách lỗi?
- [ ] Nếu cần class token `Class<? extends U>`, đã dùng `asSubclass(U.class)` thay unchecked cast?
- [ ] Đã xử lý `null` đúng hợp đồng?
- [ ] Đã phân biệt subtype của class/interface với type arguments generic bị xóa?
- [ ] Nếu nguồn là collection, đã kiểm tra từng phần tử theo schema chứ không chỉ `List.class`?
- [ ] Nếu nạp class động, đã tách xử lý `ClassNotFoundException`, `ClassCastException` và lỗi constructor?
- [ ] Có giới hạn danh sách các class được phép nạp khi đầu vào không đáng tin?
- [ ] Ví dụ compile-time và output runtime đã được thử với cả dữ liệu đúng và dữ liệu sai?
- [ ] Có tránh được raw types, unchecked casts không có bằng chứng và phép đổi kiểu thiếu an toàn không?

## Kết luận

Ba phương thức giải quyết ba nhu cầu reflection liên quan nhưng khác nhau:

- **`Class.isInstance(Object)`** kiểm tra một đối tượng có tương thích với token runtime không; trả `true` hoặc `false`, với `null` là `false`.
- **`Class.cast(Object)`** kiểm tra một đối tượng và trả typed value `T`; sai kiểu non-null ném `ClassCastException`, còn đầu vào `null` trả `null`.
- **`Class.asSubclass(Class<U>)`** kiểm tra quan hệ subtype của chính đối tượng `Class`, trả `Class<? extends U>` hoặc ném `ClassCastException`. Nó không khởi tạo object.

Khi kết hợp với **`Class<T>` token**, generics và reflection hỗ trợ xây những API ranh giới vừa linh hoạt vừa có kiểm tra runtime. Nhưng vì **type erasure**, ba phương thức này không tự kiểm tra `List<String>` ở mức phần tử. Hãy giữ các cam kết đúng phạm vi, dùng adapter kiểm tra dữ liệu lồng nhau và phân biệt rõ kiểm tra kiểu với conversion.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-reflection-003">Phân biệt java.lang.reflect.Type với Class&lt;?&gt;</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `java.lang.reflect.Type` với `Class<?>` trong Java Generics

Khi làm việc với **reflection** và **Java Generics**, hai kiểu `java.lang.reflect.Type` và `Class<?>` rất dễ bị nhầm vì cả hai đều có thể biểu diễn “kiểu”. Nhưng chúng phục vụ các nhu cầu khác nhau:

- **`Class<?>`** là đối tượng `Class` đại diện cho một **class, interface, enum, record, annotation, array hoặc primitive type cụ thể được JVM nhận biết** (cùng trường hợp `void.class` đặc biệt). Nó phù hợp khi cần kiểm tra runtime class, lấy constructor/method/field, tạo instance theo cơ chế cho phép, hoặc gọi `isInstance`/`cast`.
- **`Type`** là một **interface tổng quát dùng để mô tả cấu trúc kiểu Java** trong reflection. Nó bao trùm `Class` và những kiểu không thể biểu diễn bằng một runtime class đơn lẻ, chẳng hạn `List<String>`, `Map<String, List<Integer>>`, type variable `T`, wildcard `? extends Number` và generic array `T[]`.

Quan hệ quan trọng nhất:

```java
import java.lang.reflect.Type;

Type a = String.class; // Hợp lệ: Class<?> triển khai Type
Class<?> b = String.class;

// Type c = ...;
// Class<?> d = c; // Không luôn hợp lệ: Type có nhiều dạng khác Class
```

**Mọi đối tượng `Class<?>` là một `Type`, nhưng không phải mọi `Type` là `Class<?>`.** Với generic type arguments cụ thể, đối tượng reflection thường là `ParameterizedType`, không phải `Class`.

## 1. Bức tranh tổng thể: `Type` là abstraction, `Class` là một nhánh

Gói `java.lang.reflect` cung cấp interface `Type` và các interface con để mô tả kiểu:

```text
java.lang.reflect.Type
    |
    +-- java.lang.Class<?>                 (Class triển khai Type)
    |
    +-- java.lang.reflect.ParameterizedType
    |
    +-- java.lang.reflect.TypeVariable<D>
    |
    +-- java.lang.reflect.WildcardType
    |
    +-- java.lang.reflect.GenericArrayType
```

Đây là sơ đồ **phân loại các đối tượng reflection phổ biến** chứ không có ý nói `Class` là interface. `Class<T>` là một class của Java, triển khai interface `Type`; các kiểu `ParameterizedType`, `WildcardType` và những mục khác là interface có thể có implementation riêng của JVM/thư viện.

Ví dụ:

```java
Type primitive = int.class;        // Đối tượng Class
Type simple = String.class;        // Đối tượng Class
Type rawList = java.util.List.class; // Đối tượng Class

// List<String> không có class literal dạng:
// Type generic = List<String>.class; // KHÔNG BIÊN DỊCH
```

`List.class` mô tả runtime interface `List`, **không** chứa `String` của `List<String>`. Nếu bạn cần mô tả một kiểu tham số hóa đầy đủ, hãy lấy `Type` từ một khai báo generic bằng reflection hoặc từ cấu trúc type token phù hợp.

## 2. Bảng so sánh nhanh

| Khía cạnh | `Class<?>` | `Type` |
| --- | --- | --- |
| Bản chất | Đối tượng `Class` do JVM cung cấp; `Class<T>` là generic class | Interface chung biểu diễn kiểu |
| Quan hệ | `Class` triển khai `Type` | Có thể giữ `Class` hoặc các dạng type khác |
| `String.class` | Có | Có, vì đó cũng là một `Type` |
| `List.class` | Có: runtime interface `List` | Có |
| `List<String>` | Không có `List<String>.class` | Biểu diễn qua `ParameterizedType` trong ngữ cảnh có metadata |
| Type variable `T` | Không có `T.class` thông thường | Có thể biểu diễn qua `TypeVariable` |
| Wildcard `? extends Number` | Không có `?.class` | Có thể biểu diễn qua `WildcardType` |
| `T[]` trong khai báo generic | Không có `T[].class` để giữ type variable | Có thể biểu diễn qua `GenericArrayType` |
| Kiểm tra đối tượng runtime | Có `isInstance`, `cast`, `isAssignableFrom` | `Type` không khai báo trực tiếp các phương thức ấy |
| Truy cập cấu trúc generic | Không chứa đầy đủ generic type arguments của instance | Có thể thông qua các interface con và metadata khai báo |
| Công dụng thường gặp | Runtime type token, reflection trên class, cast/kiểm tra class | Đọc chữ ký generic, schema, serializer, binder, framework |

**`Class<?>` không phải raw `Class`.** Nó là một kiểu tham số hóa với wildcard chưa biết class được đại diện. Ví dụ `Class<String>` có thông tin kiểu tĩnh cụ thể hơn `Class<?>`, còn `Class<?> unknown = String.class` vẫn là khai báo generic có kiểm tra kiểu.

## 3. Vì sao `Class<?>` không biểu diễn được `List<String>`?

Java Generics thông thường dùng **type erasure**. Ví dụ:

```java
import java.util.ArrayList;
import java.util.List;

List<String> words = new ArrayList<>();
List<Integer> counts = new ArrayList<>();

boolean same = words.getClass() == counts.getClass(); // true
```

Đây là hai **instance của cùng runtime class `ArrayList`**. `getClass()` trả `Class` của object thực, nên không tạo hai `Class` riêng mang tên `ArrayList<String>` và `ArrayList<Integer>`.

Không có cú pháp:

```java
// KHÔNG BIÊN DỊCH:
Class<List<String>> token = List<String>.class;
```

Cũng không thể dùng:

```java
// KHÔNG phải đường giải quyết:
// Class.forName("java.util.List<java.lang.String>");
```

`Class.forName` dùng tên class/interface có thể được JVM tải theo hợp đồng của API, không phân tích một chuỗi tên type generic để tạo runtime class chuyên biệt `List<String>`.

Với một giá trị không biết trước:

```java
Object value = List.of("Java");

// Hợp lệ:
boolean list = value instanceof List<?>;

// Không hợp lệ khi value có kiểu tĩnh Object:
// boolean stringList = value instanceof List<String>;
```

`List<?>` chỉ xác minh đối tượng bên ngoài là `List`. Nó **không** kiểm tra mọi phần tử là `String`.

**Kết luận:** một `Class<?> listClass` có thể mô tả `List.class`; muốn mô tả khai báo `List<String>`, phải dùng một `Type` thuộc dạng phù hợp, nhưng dữ liệu `Type` ấy **vẫn không tự bảo đảm** nội dung list runtime đúng kiểu.

## 4. `Field.getType()` so với `Field.getGenericType()`

Một trường khai báo kiểu generic có thể có thông tin khai báo lưu trong metadata của class file. Reflection cung cấp hai lối đọc:

```java
import java.util.List;

class Example {
    List<String> names;
}
```

Với trường `names`:

```java
java.lang.reflect.Field field =
        Example.class.getDeclaredField("names");

Class<?> erased = field.getType();
java.lang.reflect.Type declared = field.getGenericType();

System.out.println(erased.getTypeName());
System.out.println(declared.getTypeName());
```

Đầu ra:

```text
java.util.List
java.util.List<java.lang.String>
```

`getType()` trả `Class<?>` của **kiểu trường ở dạng runtime/erasure**: `List`. `getGenericType()` trả `Type` diễn đạt **kiểu trường được khai báo**: `List<String>`, thường có dạng `ParameterizedType`.

Không nên hiểu `getGenericType()` là “đọc nội dung object đang nằm trong field và xác định mọi phần tử”. Đây là **metadata của khai báo**, không phải phép kiểm tra collection tại runtime.

### Cùng trường, hai góc nhìn

Nếu class generic có:

```java
class NumericHolder<T extends Number> {
    T number;
}
```

thì:

- `numberField.getType()` trả `Class` tương ứng erasure của `T`, tức `Number.class`.
- `numberField.getGenericType()` có thể trả `TypeVariable` đại diện cho `T`, với bound `Number`.

Vì vậy ngay cả khi không thấy dấu `<String>` trong một chữ ký, `getGenericType()` vẫn có thể khác `getType()`: type variable, wildcard và generic array đều là những trường hợp hữu ích.

## 5. `ParameterizedType`: đọc các đối số kiểu cụ thể

Khi reflection nhìn thấy một khai báo như `List<String>` hoặc `Map<String, List<Integer>>`, `Type` tương ứng thường là `ParameterizedType`.

```java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

Type t = Example.class.getDeclaredField("names").getGenericType();

if (t instanceof ParameterizedType p) {
    Type raw = p.getRawType();
    Type[] args = p.getActualTypeArguments();

    System.out.println(raw.getTypeName());
    System.out.println(args[0].getTypeName());
}
```

Ví dụ `List<String>` cho:

```text
java.util.List
java.lang.String
```

Ba phương thức chính:

- **`getRawType()`**: trả kiểu gốc, ví dụ `java.util.List`. Chữ ký của nó trả `Type`; trên một generic class/interface bình thường, object thực thường là `Class`.
- **`getActualTypeArguments()`**: trả mảng `Type[]` cho type arguments tại khai báo; mỗi phần tử **có thể là `Class`, `ParameterizedType`, `WildcardType` hoặc `TypeVariable`**, không được cast hết về `Class`.
- **`getOwnerType()`**: mô tả owner type của kiểu nested khi có quan hệ owner phù hợp; hữu ích cho các khai báo lớp lồng nhau.

Ví dụ với `Map<String, List<Integer>>`:

```text
Map<String, List<Integer>>
    ParameterizedType(Map)
    ├── Class(String)
    └── ParameterizedType(List)
        └── Class(Integer)
```

Cần **duyệt đệ quy cấu trúc `Type`** nếu framework muốn xây schema đầy đủ. Viết `(Class<?>) args[1]` cho đối số `List<Integer>` sẽ gây `ClassCastException`, bởi đối số đó là `ParameterizedType`, không phải `Class`.

## 6. `TypeVariable`: biểu diễn `T` được khai báo ở class hoặc method

Xét:

```java
class NumberBox<T extends Number> {
    T value;
}
```

Metadata của field `value` có thể chứa `TypeVariable` tương ứng tên `T`:

```java
java.lang.reflect.Type declared =
        NumberBox.class.getDeclaredField("value").getGenericType();

if (declared instanceof java.lang.reflect.TypeVariable<?> variable) {
    System.out.println(variable.getName()); // T
    System.out.println(variable.getBounds()[0].getTypeName());
}
```

Đầu ra dòng bound:

```text
java.lang.Number
```

Các thông tin đáng chú ý:

- `getName()`: tên type variable, ví dụ `T`.
- `getBounds()`: upper bounds của type variable; nếu không ghi bound thì thường có bound ngầm `Object`.
- `getGenericDeclaration()`: class, constructor hoặc method nơi type variable được khai báo.

**Không nên tự thay `T` thành `String` chỉ vì đang giữ một `NumberBox<String>` giả định.** Thực ra `String` không thỏa bound `Number` trong ví dụ, và quan trọng hơn: `TypeVariable` nói về **khai báo `T`**, không chứa một type argument riêng cho mọi instance khi chạy. Khi có một subclass cung cấp type argument cụ thể, reflection trên `getGenericSuperclass()` có thể cung cấp thêm ngữ cảnh cần để giải quyết `T`.

Với `class Box<T>`, `new Box<String>().getClass()` bình thường vẫn trả `Class` của `Box` mà không chứa `String` như một type argument chuyên biệt.

## 7. `WildcardType`: `?`, `? extends` và `? super`

Một type argument dạng wildcard được phản chiếu qua `WildcardType`.

```java
class Bounds {
    java.util.List<? extends Number> producer;
    java.util.List<? super Integer> consumer;
}
```

Khi đọc `producer`, `Field.getGenericType()` trả một `ParameterizedType` cho `List<? extends Number>`; phần tử của `getActualTypeArguments()[0]` là **`WildcardType`**.

```java
java.lang.reflect.Type t =
        Bounds.class.getDeclaredField("producer").getGenericType();

java.lang.reflect.ParameterizedType p =
        (java.lang.reflect.ParameterizedType) t;

java.lang.reflect.WildcardType w =
        (java.lang.reflect.WildcardType) p.getActualTypeArguments()[0];

System.out.println(w.getUpperBounds()[0].getTypeName()); // java.lang.Number
```

Phương thức cần nhớ:

- `getUpperBounds()`: các upper bounds của wildcard, ví dụ `Number` trong `? extends Number`; với `? super Integer` upper bound thường là `Object`.
- `getLowerBounds()`: lower bounds, ví dụ `Integer` trong `? super Integer`; nếu không có lower bound sẽ trả mảng rỗng.

Đừng cast wildcard về `Class` chỉ vì upper bound có thể là `Number.class`. **Wildcard không phải chính upper bound**, và một framework cần giữ đúng thông tin `? extends`/`? super` trong mô hình type của nó.

## 8. `GenericArrayType` và mảng `Class`: khác nhau ở kiểu phần tử

Mảng có runtime component type reifiable như `String[]` thường được mô tả trực tiếp bằng đối tượng `Class`:

```java
Class<?> arrayClass = String[].class;
System.out.println(arrayClass.getTypeName()); // java.lang.String[]
System.out.println(arrayClass.getComponentType()); // class java.lang.String
```

Nhưng với một khai báo generic array có component type non-reifiable:

```java
class ArraysHolder<T> {
    T[] variables;
    java.util.List<String>[] lists;
    String[] strings;
}
```

Các khai báo field như `T[]` và `List<String>[]` **hợp lệ**, dù biểu thức **tạo trực tiếp** `new T[n]` hoặc `new List<String>[n]` bị Java cấm.

Reflection có thể cho:

| Field | `getType()` — kiểu erasure | `getGenericType()` — dạng type |
| --- | --- | --- |
| `T[] variables` khi `T` không bound | `Object[]` | `GenericArrayType` có component `T` |
| `List<String>[] lists` | `List[]` | `GenericArrayType` có component `List<String>` |
| `String[] strings` | `String[]` | `Class` cho `String[]` |

Nếu `T extends Number`, erasure của `T[]` là `Number[]`, không phải `Object[]`. Điều đó vẫn **không biến `T[]` thành array có thể tự tạo bằng `new T[n]`**.

Khi gặp `GenericArrayType`, dùng:

```java
if (type instanceof java.lang.reflect.GenericArrayType arrayType) {
    java.lang.reflect.Type component =
            arrayType.getGenericComponentType();
    System.out.println(component.getTypeName());
}
```

Đừng giả định mọi `Type` có tên kết thúc `[]` đều là `GenericArrayType`: `String[]` và các mảng có component type reifiable có thể là `Class`. Cần kiểm tra **dạng của object `Type`**, không đoán từ chuỗi tên.

## 9. Chương trình Java 17+ hoàn chỉnh: xem từng dạng `Type`

Lưu thành **`TypeVsClassDemo.java`**:

```java
import java.lang.reflect.*;
import java.util.*;

public class TypeVsClassDemo {
    static class Fields<T extends Number> {
        List<String> names;
        Map<String, List<Integer>> index;
        List<? extends Number> upper;
        List<? super Integer> lower;
        T[] variableArray;
        List<String>[] genericArray;
        String[] fixedArray;
        T number;

        List<T> wrap(T value) {
            return List.of(value);
        }
    }

    static abstract class TypeRef<T> {
        final Type captured;

        protected TypeRef() {
            Type parent = getClass().getGenericSuperclass();
            if (!(parent instanceof ParameterizedType p)) {
                throw new IllegalStateException("Missing type argument");
            }
            captured = p.getActualTypeArguments()[0];
        }

        Type getType() {
            return captured;
        }
    }

    static String kind(Type type) {
        if (type instanceof Class<?>) {
            return "Class";
        }
        if (type instanceof ParameterizedType) {
            return "ParameterizedType";
        }
        if (type instanceof TypeVariable<?>) {
            return "TypeVariable";
        }
        if (type instanceof WildcardType) {
            return "WildcardType";
        }
        if (type instanceof GenericArrayType) {
            return "GenericArrayType";
        }
        return "Other";
    }

    static void show(String field) throws NoSuchFieldException {
        Field f = Fields.class.getDeclaredField(field);
        Type generic = f.getGenericType();

        System.out.println(field + ": "
                + f.getType().getTypeName()
                + " | " + generic.getTypeName()
                + " | " + kind(generic));
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Type basic = String.class;
        System.out.println("Class is Type: "
                + (basic instanceof Class<?>));

        show("names");
        show("index");
        show("upper");
        show("lower");
        show("number");
        show("variableArray");
        show("genericArray");
        show("fixedArray");

        ParameterizedType nameType = (ParameterizedType)
                Fields.class.getDeclaredField("names")
                        .getGenericType();

        System.out.println("List raw type: "
                + nameType.getRawType().getTypeName());
        System.out.println("List argument: "
                + nameType.getActualTypeArguments()[0].getTypeName());

        TypeVariable<?> variable = (TypeVariable<?>)
                Fields.class.getDeclaredField("number")
                        .getGenericType();
        System.out.println("T bound: "
                + variable.getBounds()[0].getTypeName());

        ParameterizedType upper = (ParameterizedType)
                Fields.class.getDeclaredField("upper")
                        .getGenericType();
        WildcardType wildcard = (WildcardType)
                upper.getActualTypeArguments()[0];

        System.out.println("Wildcard upper bound: "
                + wildcard.getUpperBounds()[0].getTypeName());

        Method m = Fields.class.getDeclaredMethod(
                "wrap", Number.class);
        System.out.println("Method return: "
                + m.getReturnType().getTypeName()
                + " | " + m.getGenericReturnType().getTypeName());

        List<String> list = new ArrayList<>();
        System.out.println("Runtime list class: "
                + list.getClass().getTypeName());

        Type captured =
                new TypeRef<List<String>>() {}.getType();
        System.out.println("Captured Type: "
                + captured.getTypeName());
        System.out.println("Captured is Class: "
                + (captured instanceof Class<?>));

        System.out.println("String token check: "
                + String.class.isInstance("Java"));
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 TypeVsClassDemo.java
java TypeVsClassDemo
```

Standard output:

```text
Class is Type: true
names: java.util.List | java.util.List<java.lang.String> | ParameterizedType
index: java.util.Map | java.util.Map<java.lang.String, java.util.List<java.lang.Integer>> | ParameterizedType
upper: java.util.List | java.util.List<? extends java.lang.Number> | ParameterizedType
lower: java.util.List | java.util.List<? super java.lang.Integer> | ParameterizedType
number: java.lang.Number | T | TypeVariable
variableArray: java.lang.Number[] | T[] | GenericArrayType
genericArray: java.util.List[] | java.util.List<java.lang.String>[] | GenericArrayType
fixedArray: java.lang.String[] | java.lang.String[] | Class
List raw type: java.util.List
List argument: java.lang.String
T bound: java.lang.Number
Wildcard upper bound: java.lang.Number
Method return: java.util.List | java.util.List<T>
Runtime list class: java.util.ArrayList
Captured Type: java.util.List<java.lang.String>
Captured is Class: false
String token check: true
```

Đầu ra cho thấy một số điểm dễ bị bỏ sót:

1. `Class` triển khai `Type`, vì vậy `String.class` là cả hai theo quan hệ interface.
2. Kiểu field `List<String>` được `getType()` nhìn như `List`, trong khi `getGenericType()` giữ `String` và được phân loại là `ParameterizedType`.
3. `Map<String, List<Integer>>` là một `ParameterizedType` lồng nhau; type argument thứ hai không thể cast trực tiếp thành `Class`.
4. `List<? extends Number>` và `List<? super Integer>` có **kiểu field ngoài** là `ParameterizedType`. `WildcardType` nằm **bên trong mảng type arguments**, không phải tại gốc của field.
5. `T number` là `TypeVariable`; với bound `Number`, `getType()` có erasure `Number`.
6. `T[]` và `List<String>[]` phản chiếu qua `GenericArrayType`; `String[]` lại là `Class`.
7. `Method.getReturnType()` và `Method.getGenericReturnType()` khác nhau tương tự `Field.getType()` và `Field.getGenericType()`.
8. `list.getClass()` không khôi phục `String`; anonymous subclass của `TypeRef<List<String>>` giữ được **metadata generic của superclass được khai báo**, nên cung cấp một `ParameterizedType` để thao tác.
9. `String.class.isInstance("Java")` là một **runtime class check thực sự**; một đối tượng `Type` chung không có method `isInstance` tương ứng.

## 10. `TypeRef<T>` hoạt động thế nào? Nó có “đảo ngược” erasure không?

Trong ví dụ chạy:

```java
Type captured = new TypeRef<List<String>>() {}.getType();
```

Biểu thức `new TypeRef<List<String>>() {}` tạo **một anonymous subclass** có generic superclass được khai báo là `TypeRef<List<String>>`. Reflection trên `getClass().getGenericSuperclass()` đọc **generic signature của superclass** và lấy type argument `List<String>`.

Cách tiếp cận này thường được gọi là **type token pattern** hoặc **super type token**. Nhiều thư viện serialization/binding cũng dùng một ý tưởng tương tự: bạn cung cấp type argument trong **khai báo lớp con**, từ đó framework giữ một mô tả `Type` gồm các lớp generic lồng nhau.

Nó **không** chứng minh bất kỳ `ArrayList` runtime nào có nội dung toàn `String`:

```java
List<String> normal = new ArrayList<>();
Class<?> runtime = normal.getClass(); // ArrayList.class

Type schema = new TypeRef<List<String>>() {}.getType();
// schema mô tả kiểu mong muốn, không duyệt "normal" để kiểm tra phần tử
```

Không có sự tái tạo thần kỳ của type argument đã bị erasure trên object `normal`. Type token tạo thêm **metadata từ nguồn/khai báo có chủ đích**, rồi parser/serializer phải áp dụng cấu trúc đó khi chuyển đổi hoặc xác thực dữ liệu.

Một giới hạn quan trọng: nếu bạn viết `new TypeRef<T>() {}` ở nơi `T` vẫn là type variable chưa được giải quyết, metadata có thể chỉ giữ `T`, không tự suy ra class cụ thể của mọi lời gọi. Framework muốn resolve `T` có thể cần môi trường type bindings theo class/method chứa `T`.

## 11. Cùng câu hỏi khi reflection trên method và superclass

`Field` không phải nơi duy nhất có sự khác biệt giữa runtime class và generic declaration:

| Thành phần | Dạng xóa kiểu / `Class` | Dạng generic / `Type` |
| --- | --- | --- |
| Field | `Field.getType()` | `Field.getGenericType()` |
| Method return | `Method.getReturnType()` | `Method.getGenericReturnType()` |
| Method parameters | `Method.getParameterTypes()` | `Method.getGenericParameterTypes()` |
| Constructor parameters | `Constructor.getParameterTypes()` | `Constructor.getGenericParameterTypes()` |
| Superclass | `Class.getSuperclass()` | `Class.getGenericSuperclass()` |
| Implemented interfaces | `Class.getInterfaces()` | `Class.getGenericInterfaces()` |
| Exceptions của method | `Method.getExceptionTypes()` | `Method.getGenericExceptionTypes()` |

Ví dụ:

```java
class Base<T> {}
class Child extends Base<String> {}

Class<?> superclass = Child.class.getSuperclass();
java.lang.reflect.Type genericSuperclass =
        Child.class.getGenericSuperclass();

System.out.println(superclass.getTypeName());
System.out.println(genericSuperclass.getTypeName());
```

Kết quả:

```text
Base
Base<java.lang.String>
```

Với một class trong package khác, tên đầy đủ có thể gồm tên package; trong ví dụ `Base` không có package. Chú ý `getGenericSuperclass()` ở `Child` mô tả **quan hệ kế thừa được khai báo** `extends Base<String>`, không tạo runtime class mới chuyên biệt `Base<String>`.

Đối với parameter có annotation trên type use, `AnnotatedType` và các API như `Field.getAnnotatedType()` còn cung cấp thông tin annotation liên quan type. `AnnotatedType` là cơ chế bổ sung, không phải một subtype thay thế của `Type`; khi cần mô hình kiểu lẫn type-use annotations, phải xét cả hai.

## 12. `Class<T>`, `Class<?>`, `Type`: dùng trong thiết kế API thế nào?

### 12.1. Cần một runtime class token cụ thể

```java
static <T> T checkedCast(Object value, Class<T> expected) {
    return expected.cast(value);
}

String name = checkedCast("Java", String.class);
// checkedCast(42, String.class) -> ClassCastException
```

`Class<T>.cast` kiểm tra được kiểu runtime tương ứng với một class/interface thực sự. `Class<T>` giúp compiler biết kết quả trả về là `T`.

Nếu API chỉ cần **giữ một class chưa biết**, dùng `Class<?>`:

```java
Class<?> token = Integer.class;
boolean matched = token.isInstance(42); // true
```

Wildcard `?` nói rằng compiler không biết chính xác class được token đại diện. `Class<?>` không đồng nghĩa với `Class<Object>`: token `String.class` có thể gán vào `Class<?>`, nhưng không thể tùy tiện gán vào `Class<Object>`.

### 12.2. Cần mô tả `List<String>`, `Map<String, Integer>`, `T[]`

Hãy chọn `Type` hoặc một abstraction mà thư viện hỗ trợ:

```java
Type declared = new TypeRef<List<String>>() {}.getType();
```

`Type` thích hợp làm tham số cho serializer/deserializer hay schema analyzer cần biết nested generic arguments. Nó không tự có method `cast`, `getDeclaredMethod` hoặc `newInstance` giống `Class`.

Ví dụ, nếu bạn viết:

```java
static void inspect(java.lang.reflect.Type type) {
    if (type instanceof java.lang.reflect.ParameterizedType p) {
        System.out.println(p.getRawType().getTypeName());
    } else if (type instanceof Class<?> c) {
        System.out.println(c.getName());
    }
}
```

thì việc **phân nhánh** theo dạng `Type` là cần thiết. Không cast tất cả `Type` sang `Class`.

### 12.3. API cần cả hai khả năng

Một số API cần **raw runtime class** để phân bổ object/kiểm tra và **`Type`** để mô tả schema generic. Chúng có thể yêu cầu cả hai hoặc tự phân tích `Type` để lấy raw class, nhưng việc lấy raw class từ `Type` **không phải luôn chỉ là một cast**.

Nếu một `Type` là:

- `Class<?>`: đã có class.
- `ParameterizedType`: `getRawType()` thường là class gốc của parameterized declaration.
- `GenericArrayType`: phải xử lý component type, có thể còn type variable.
- `TypeVariable`: cần bounds và môi trường type bindings.
- `WildcardType`: cần bounds và mục đích xử lý wildcard.

Với nhiều loại, không có **một** runtime class duy nhất phản ánh đầy đủ toàn bộ cấu trúc generic. Framework có thể dùng `Object.class` hoặc upper bound làm fallback cho những mục tiêu nhất định, nhưng đó là **quy tắc do framework lựa chọn**, không phải `Type` tự quy định.

## 13. Tại sao không nên chỉ dùng `getTypeName()` để xử lý generic?

Mọi `Type` đều có `getTypeName()` để tạo dạng tên dễ hiển thị, ví dụ:

```text
java.util.List<java.lang.String>
java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>
T[]
```

Nhưng dùng string matching thủ công để hiểu generic type vừa dễ sai vừa làm mất thông tin cấu trúc:

- Chuỗi `Map<String,List<Integer>>` có generic lồng nhau, dấu phẩy, khoảng trắng và các dạng viết tên khác nhau.
- Wildcard có `extends`/`super` và bounds.
- Type variables có thể cùng tên `T` nhưng **khai báo ở hai scope khác nhau**; so chuỗi `"T"` không thể xác định chúng là cùng biến kiểu.
- Nested classes có owner type, và array có component type cần phân tích đúng tầng.

Đúng hơn là dùng `instanceof` với `Class`, `ParameterizedType`, `TypeVariable`, `WildcardType`, `GenericArrayType`, sau đó đọc **các phần tử của cấu trúc**.

Nếu cần làm key cho cache type, hãy tìm hiểu hợp đồng `equals`/`hashCode` của các biểu diễn `Type` và môi trường class loader; đừng lấy tên string làm danh tính kiểu một cách máy móc. Hai class có tên giống nhau được nạp bởi class loaders khác nhau có thể là hai runtime classes khác nhau.

## 14. Metadata generic có đủ để xác minh dữ liệu thật không?

**Không.** Giả sử reflection báo một field được khai báo `List<String>`; một chương trình có raw alias hoặc unchecked cast vẫn có thể đưa giá trị trái kiểu vào một list runtime:

```java
// Snippet CỐ Ý KHÔNG AN TOÀN:
List<String> names = new ArrayList<>();
List raw = names;
raw.add(42); // Unchecked warning; list bị sai hợp đồng String

// String value = names.get(0); // Có thể ClassCastException
```

`getGenericType()` không quét mọi phần tử và không sửa heap pollution. Nó chỉ phản ánh **hợp đồng kiểu khai báo**. Tương tự, `TypeRef<List<String>>` là schema do caller nêu ra, không phải một runtime test chứng minh nội dung object luôn đúng với schema.

Muốn xác minh một object bất kỳ thực sự là danh sách chỉ chứa `String`, có thể:

```java
static boolean allStrings(Object value) {
    if (!(value instanceof List<?> list)) {
        return false;
    }
    return list.stream().allMatch(String.class::isInstance);
}
```

Hàm này kiểm tra **đối tượng bên ngoài** qua `List<?>`, rồi xác minh **từng phần tử** bằng `String.class.isInstance`. Với danh sách rỗng, `allMatch` trả `true` theo quy tắc “mọi phần tử thỏa”; nếu cần danh sách không rỗng, kiểm tra thêm.

Ngay cả sau khi xác thực, một list mutable có alias khác vẫn có thể bị sửa. Nếu cần bảo đảm ổn định qua thời gian, hãy cân nhắc snapshot và chính sách ownership/đồng bộ phù hợp.

## 15. Lỗi phổ biến

**Lỗi 1 — “`Type` và `Class<?>` là hai loại không liên quan.”** Sai. `Class` triển khai `Type`.

**Lỗi 2 — “`Type` lúc nào cũng cast sang `Class` được.”** Sai. `List<String>` phản chiếu thành `ParameterizedType`; `T` là `TypeVariable`; một `T[]` là `GenericArrayType`.

**Lỗi 3 — “`Class<?>` chứa type argument của `ArrayList<String>`.”** Sai. `getClass()` trả runtime class `ArrayList`; generic type argument không nằm trong một class chuyên biệt cho mỗi instance.

**Lỗi 4 — “`Field.getType()` và `getGenericType()` luôn giống nhau.”** Sai. `List<String>`, `T`, generic arrays là phản ví dụ.

**Lỗi 5 — “`getGenericType()` trả `ParameterizedType` với mọi generic field.”** Sai. Field `T` có thể trả `TypeVariable`; `T[]` có thể trả `GenericArrayType`; field `List` raw có thể trả `Class`.

**Lỗi 6 — “Type argument lấy từ `getActualTypeArguments()` luôn là `Class`.”** Sai. Nó có thể lồng `ParameterizedType`, `WildcardType` hoặc `TypeVariable`.

**Lỗi 7 — “Mọi array type đều là `GenericArrayType`.”** Sai. `String[]` thuộc `Class`; `T[]` và `List<String>[]` là các ví dụ generic array.

**Lỗi 8 — “Có `TypeRef<List<String>>` nghĩa JVM biết mọi `ArrayList` nào là `List<String>`.”** Sai. TypeRef giữ metadata từ khai báo subclass, không gắn type argument runtime cho các list instance tùy ý.

**Lỗi 9 — “Reflection thấy `List<String>` thì dữ liệu collection chắc chắn an toàn.”** Sai. Raw alias và unchecked operations có thể gây heap pollution.

**Lỗi 10 — “Chỉ cần so chuỗi `getTypeName()` để xác định bằng nhau.”** Cần thận trọng: type identity, class loader, scope của type variables và cấu trúc nested đều quan trọng.

**Lỗi 11 — “`Type` có `isInstance` giống `Class`.”** Sai. `isInstance` là API của `Class`, không được `Type` khai báo.

## 16. Bài tập tự kiểm tra

**Câu 1.** `String.class` có phải một `Type` không?

Có. `Class` triển khai `java.lang.reflect.Type`.

**Câu 2.** `List<String>.class` có hợp lệ không?

Không. Java không có class literal cho cách tham số hóa cụ thể như `List<String>`; chỉ có `List.class`.

**Câu 3.** Field `Map<String, List<Integer>> index;` thường được `getGenericType()` phản chiếu thành dạng nào?

`ParameterizedType` ở ngoài cho `Map`, với một đối số là `Class(String)` và một đối số là `ParameterizedType(List<Integer>)`.

**Câu 4.** Vì sao `((Class<?>) field.getGenericType())` có thể gây `ClassCastException`?

Vì `getGenericType()` trả `Type` nói chung; nếu field có dạng `List<String>`, object thực là `ParameterizedType`, không phải `Class`.

**Câu 5.** Field `T number` trong `class Box<T extends Number>` có erasure là gì?

`Number`. Nhưng `getGenericType()` vẫn có thể biểu diễn `T` bằng `TypeVariable` với bound `Number`.

**Câu 6.** `String[]` và `List<String>[]` có cùng dạng `Type` phản chiếu không?

Không nhất thiết. `String[]` được phản chiếu bằng `Class`, trong khi `List<String>[]` thường là `GenericArrayType`.

**Câu 7.** `new TypeRef<List<String>>() {}.getType()` có kiểm tra danh sách thật chứa toàn String không?

Không. Nó thu thập mô tả generic type từ khai báo superclass của anonymous subclass.

**Câu 8.** Nếu cần `Class<T>.cast`, tham số API nên nhận `Class<T>` hay `Type`?

Thông thường là `Class<T>` nếu hợp đồng chỉ cần một runtime class cụ thể. Nếu cần nested generic metadata, `Type` mới phù hợp, nhưng cần cơ chế xác thực/chuyển đổi riêng.

**Câu 9.** Khi một kiểu khai báo `List<? super Integer>`, wildcard ở gốc của field hay bên trong `ParameterizedType`?

Field ngoài là `ParameterizedType` của `List`; wildcard là một **type argument** nằm bên trong.

## 17. Checklist đọc reflection an toàn

Khi nhận một biến `Type type`, hãy lần lượt hỏi:

1. **Đó là `Class<?>`?** Nếu có, bạn có runtime class token và những API như `isInstance`.
2. **Đó là `ParameterizedType`?** Đọc `getRawType()`, các `getActualTypeArguments()` và owner type nếu cần.
3. **Đó là `TypeVariable`?** Đọc tên, bounds, declaration; có cần resolve trong ngữ cảnh generic nào không?
4. **Đó là `WildcardType`?** Kiểm tra upper/lower bounds và mục đích sử dụng.
5. **Đó là `GenericArrayType`?** Xử lý component type đệ quy, không ép mặc định thành `Class`.
6. **Nguồn `Type` là khai báo nào?** Field, method, superclass, interface hay type token? Nó có phản ánh actual data không, hay chỉ schema?
7. **Có cần runtime check thật không?** `Class.isInstance` kiểm tra một class/interface; nested generic constraints có thể cần duyệt dữ liệu.
8. **Bạn đang phụ thuộc vào chuỗi type name không?** Tránh parser thủ công không xét type structure/scope/class loader.
9. **Có generic metadata cần phản chiếu đầy đủ không?** Đảm bảo class file và đường truy cập reflection cung cấp thông tin; framework cần dự phòng khi chữ ký thiếu/hỏng hoặc type không resolve.
10. **Bạn đang thiết kế public API nào?** Dùng `Class<T>` cho runtime class token; dùng `Type`/type token cho schema generic, và nói rõ giới hạn xác thực.

**Kết luận:** `Class<?>` là một nhánh cụ thể của `Type`, dùng để mô tả runtime class/interface/array/primitive và hỗ trợ trực tiếp kiểm tra/cast ở runtime. `java.lang.reflect.Type` là abstraction rộng hơn cho những kiểu được viết trong chương trình Java, bao gồm `ParameterizedType`, `TypeVariable`, `WildcardType` và `GenericArrayType`. `Field.getType()` thường cho bạn góc nhìn kiểu đã xóa, còn `Field.getGenericType()` có thể giữ cấu trúc generic của **khai báo**. Nắm rõ phân biệt đó giúp viết reflection, parser, serializer và API generic đúng mà không nhầm metadata kiểu với bằng chứng về dữ liệu runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-reflection-004">ParameterizedType: raw type, owner type và actual type arguments</a>

<details>
<summary>Xem chi tiết</summary>

# ParameterizedType: raw type, owner type và actual type arguments trong Java Generics

## 1. Mục tiêu học tập

Java Generics cho phép viết những kiểu như `List<String>`, `Map<String, List<Integer>>` hoặc `Outer<String>.Inner<Integer>`. Sau *type erasure*, JVM không tạo riêng một class cho từng cách tham số hóa. Tuy nhiên, **thông tin generic được khai báo trên field, method, superclass, interface hoặc một anonymous subclass vẫn có thể được lưu trong class file** và được API reflection đọc lại.

`java.lang.reflect.ParameterizedType` là interface biểu diễn **một kiểu tham số hóa** trong thế giới reflection. Ba phương thức quan trọng nhất là:

- `getRawType()`: kiểu được khai báo làm nền cho việc tham số hóa, ví dụ `List` trong `List<String>`.
- `getOwnerType()`: kiểu bao ngoài mà một kiểu thành viên (member type) thuộc về, nếu có; đặc biệt quan trọng với nested/inner classes.
- `getActualTypeArguments()`: danh sách các đối số kiểu trực tiếp, ví dụ `String` trong `List<String>` hoặc `String` và `List<Integer>` trong `Map<String, List<Integer>>`.

Sau bài này, bạn sẽ biết cách lấy `ParameterizedType` từ các API phản chiếu, hiểu **giá trị `null` của owner**, phân biệt owner có generic với nested class static, đọc đệ quy các đối số kiểu, nhận diện `Class`, `TypeVariable`, `WildcardType`, `GenericArrayType`, và tránh nhầm **metadata khai báo** với **kiểu dữ liệu thực được kiểm tra lúc chạy**.

## 2. `Type` và `ParameterizedType` nằm ở đâu trong reflection?

Một số API reflection hữu ích:

```java
Field field = SomeClass.class.getDeclaredField("names");
Type declared = field.getGenericType();

Type superclass = SomeSubclass.class.getGenericSuperclass();
Type[] interfaces = SomeImplementation.class.getGenericInterfaces();
```

Đây là minh họa cách gọi API; các lớp và field tương ứng phải được định nghĩa nếu chạy độc lập. Chương trình hoàn chỉnh ở phần 8 sẽ cung cấp ví dụ thực tế.

Các kiểu reflective chính:

| API/kiểu | Ý nghĩa | Ví dụ |
| --- | --- | --- |
| `Type` | Interface cha chung của các dạng mô tả kiểu reflection. | Kết quả `Field.getGenericType()`. |
| `Class<?>` | Kiểu class, interface, primitive hoặc array reifiable có Class object tương ứng. | `String.class`, `List.class`, `String[].class`. |
| `ParameterizedType` | Một cách sử dụng kiểu có đối số generic hoặc kiểu thành viên với owner tham số hóa. | `List<String>`, `Outer<String>.Inner<Integer>`. |
| `TypeVariable<?>` | Biến kiểu được khai báo trên class, interface hoặc method. | `T` trong `class Box<T>`. |
| `WildcardType` | Wildcard cùng upper/lower bounds. | `? extends Number`, `? super Integer`. |
| `GenericArrayType` | Mảng mà component type không reifiable theo mô hình generic reflection. | `T[]`, `List<T>[]`. |

Một `ParameterizedType` **không phải** một `Class`. Nó mô tả cấu trúc kiểu generic xuất hiện trong khai báo. Khi cần gọi `Class.isInstance` hoặc `Class.cast`, bạn đang kiểm tra kiểu runtime có thể biểu diễn bằng `Class`, chứ không tự động kiểm tra tất cả đối số generic.

Ví dụ, `List.class` có thể nhận diện một đối tượng triển khai List; nó không phân biệt `List<String>` và `List<Integer>`. Ngược lại, một `ParameterizedType` đọc từ metadata có thể cho biết một field được khai báo là `List<String>`; đó là thông tin **về field**, không phải một cơ chế JVM quét mọi phần tử của danh sách tại runtime.

### Lấy kiểu generic và kiểu raw của field

Giả sử:

```java
class Profile {
    List<String> names;
}
```

Đặt các khai báo trong cùng một file có import `java.util.List` và `java.lang.reflect.Field`:

```java
Field field = Profile.class.getDeclaredField("names");

Class<?> rawFieldType = field.getType();
Type genericFieldType = field.getGenericType();
```

`field.getType()` trả về `Class` biểu diễn `java.util.List`. `field.getGenericType()` thường trả về một `ParameterizedType` biểu diễn `java.util.List<java.lang.String>`.

Muốn truy cập ba thành phần, hãy kiểm tra kiểu trước:

```java
if (genericFieldType instanceof ParameterizedType type) {
    Type raw = type.getRawType();
    Type owner = type.getOwnerType();
    Type[] arguments = type.getActualTypeArguments();
}
```

Kết quả `Type` không phải lúc nào cũng là `ParameterizedType`. Ví dụ field khai báo `String name` thường cho `Class<String>`; field khai báo raw `List names` thường cho `Class<List>`. Nếu ép vô điều kiện `(ParameterizedType) field.getGenericType()`, code có thể gặp `ClassCastException`.

## 3. `getRawType()`: phần nền đã bỏ đối số kiểu

Với `List<String>`, thành phần raw type là `java.util.List`:

```text
List<String>
┌───┘└────┘
 raw   actual type argument
```

Ví dụ:

```java
Type declared = field.getGenericType();

if (declared instanceof ParameterizedType p) {
    System.out.println(p.getRawType().getTypeName());
}
```

Kết quả với field `List<String>`:

```text
java.util.List
```

Với `Map<String, List<Integer>>`:

- Raw type ngoài cùng: `java.util.Map`.
- Actual type argument thứ nhất: `java.lang.String`.
- Actual type argument thứ hai: một **ParameterizedType khác** đại diện `List<Integer>`.
- Raw type của argument thứ hai: `java.util.List`.
- Actual type argument bên trong: `java.lang.Integer`.

**Đừng chỉ gọi `getRawType()` rồi kết luận đã hiểu đầy đủ kiểu.** Nó cố ý không chứa các type arguments, wildcard bounds hoặc thông tin owner đã tham số hóa.

### `getRawType()` trả `Type`, không khai báo trả `Class<?>`

Chữ ký phương thức là:

```java
Type getRawType();
```

Trong những phản chiếu phổ biến trên class/interface Java, giá trị trả về thường là một `Class<?>`. Tuy nhiên, API khai báo kiểu tổng quát `Type`, nên code thư viện đọc reflection nên kiểm tra trước khi downcast:

```java
Type raw = parameterized.getRawType();
if (raw instanceof Class<?> rawClass) {
    System.out.println(rawClass.getName());
}
```

`parameterized` ở đây là biến `ParameterizedType` đã lấy được trong ngữ cảnh. Nếu mục tiêu chỉ là in hoặc so sánh mô tả, `raw.getTypeName()` có thể đủ, không nhất thiết phải cast.

### Raw type khác với raw type usage trong mã nguồn

Cùng cụm từ “raw type” được dùng ở hai ngữ cảnh:

- `getRawType()` mô tả phần class/interface đứng sau một `ParameterizedType`.
- Một **raw usage** trong mã nguồn là khai báo thiếu đối số kiểu, ví dụ `List raw;`.

Field khai báo raw `List` có thể khiến `getGenericType()` trả trực tiếp `Class<List>`, chứ không phải `ParameterizedType` với danh sách argument rỗng. Vì vậy không nên dùng “có `Class<List>`” và “có `ParameterizedType<List<String>>`” như hai cấu trúc reflective tương đương.

## 4. `getActualTypeArguments()`: danh sách đối số kiểu trực tiếp

Với kiểu đơn:

```java
List<String>
```

`getActualTypeArguments()` trả một mảng `Type[]` có **1 phần tử**, mô tả `String`.

Với:

```java
Map<String, List<Integer>>
```

nó trả **2 phần tử**:

```text
Map<String, List<Integer>>
 ├── arg[0]: Class<String>
 └── arg[1]: ParameterizedType(List<Integer>)
                     ├── raw: Class<List>
                     └── arg[0]: Class<Integer>
```

Dù kiểu lồng nhau chứa một Integer, điều đó **không** có nghĩa `Map` có ba actual type arguments. `Map<K,V>` có đúng hai argument trực tiếp; muốn xem Integer, bạn phải đi sâu vào argument thứ hai.

### Cách đọc đệ quy

```java
static void inspectArguments(ParameterizedType type) {
    Type[] arguments = type.getActualTypeArguments();
    for (int index = 0; index < arguments.length; index++) {
        Type argument = arguments[index];
        System.out.println("arg[" + index + "]=" + argument.getTypeName());

        if (argument instanceof ParameterizedType nested) {
            inspectArguments(nested);
        }
    }
}
```

Đây là helper minh họa cần import `Type`, `ParameterizedType`. Trong ứng dụng thực, một trình duyệt Type tổng quát còn phải xử lý `Class`, `WildcardType`, `TypeVariable`, `GenericArrayType` và có thể tránh duyệt vô hạn khi phân tích cấu trúc recursive bounds.

**Điểm quan trọng:** `getActualTypeArguments()` trả `Type[]` chứ không phải `Class<?>[]`, vì argument có thể là nhiều dạng khác nhau.

| Argument được khai báo | Loại reflective thường gặp |
| --- | --- |
| `String` | `Class<?>`. |
| `List<Integer>` | `ParameterizedType`. |
| `T` | `TypeVariable<?>`. |
| `? extends Number` | `WildcardType`. |
| `T[]` khi T là type variable | `GenericArrayType`. |
| `String[]` | `Class<?>`, vì mảng String reifiable. |

Không nên ép mọi phần tử `getActualTypeArguments()` thành `Class`. Việc đó có thể chạy với `List<String>` nhưng ném `ClassCastException` khi gặp `List<T>` hoặc `List<? extends Number>`.

## 5. `getOwnerType()`: kiểu bao ngoài của member type

`Owner type` thường là phần dễ nhầm nhất. Nó **không** đơn giản là superclass, không phải raw type, và không phải một argument nằm chung mảng với type arguments trực tiếp của member class.

Với kiểu top-level `List<String>`, `getOwnerType()` là `null`, bởi `List` không phải member class bên trong một kiểu bao ngoài.

Xét:

```java
class Outer<T> {
    class Inner<U> {}
    class Plain {}
    static class Nested<V> {}
}
```

Ba kiểu thành viên có quan hệ với Outer khác nhau. Khi nhìn khai báo:

```java
Outer<String>.Inner<Integer>
```

ta có hai “tầng” tham số hóa:

- **Inner<U>** có U = Integer.
- **Outer<T>** là owner được tham số hóa với T = String.

Nếu phản chiếu kiểu trên, `ParameterizedType` của `Inner<Integer>` có:

```text
getRawType()            -> Outer$Inner
getOwnerType()          -> ParameterizedType(Outer<String>)
getActualTypeArguments()-> [Integer]
```

Thông tin String thuộc `getOwnerType()`, còn Integer thuộc `getActualTypeArguments()` của Inner. Nếu bạn chỉ duyệt arguments của Inner rồi bỏ owner, bạn **sẽ bỏ sót T = String**.

### 5.1. Inner class không static với owner generic

Khai báo field:

```java
Outer<String>.Inner<Integer> member;
```

Nếu là field trên một lớp và `getGenericType()` trả `ParameterizedType p`:

```java
Type owner = p.getOwnerType();
Type raw = p.getRawType();
Type[] ownArguments = p.getActualTypeArguments();
```

Kết quả điển hình của chương trình phần 8 là:

```text
inner.raw=ParameterizedTypeDemo$Outer$Inner
inner.ownerParameterized=true
inner.ownerRaw=ParameterizedTypeDemo$Outer
inner.ownerArg=java.lang.String
inner.arg=java.lang.Integer
```

Dấu `$` trong tên class binary của các nested types là cách thể hiện tên lớp mà JVM sử dụng trong ví dụ. Khi trình bày cho người đọc, bạn có thể dùng tên nguồn `Outer.Inner`, nhưng khi đối chiếu `Class.getName()`/`Type.getTypeName()`, binary name có thể chứa `$`.

### 5.2. Inner class không tự khai báo tham số kiểu

Đây là tình huống quan trọng:

```java
Outer<String>.Plain plain;
```

`Plain` **không** khai báo `<U>`, nhưng **owner** của nó vẫn là `Outer<String>`. Reflection có thể trả một `ParameterizedType` của member với:

```text
getActualTypeArguments().length -> 0
getOwnerType()                   -> Outer<String>
```

Điều đó chứng minh **một `ParameterizedType` không bắt buộc phải có ít nhất một argument trực tiếp**. Metadata tham số hóa có thể nằm ở owner. Bạn không được suy ra “actual arguments rỗng ⇒ kiểu này chắc chắn không liên quan generic”.

Trong ví dụ được kiểm chứng trên Java 17, `plain.ownArgs=0` và `plain.ownerArg=java.lang.String`.

### 5.3. Static nested class

Khai báo:

```java
Outer.Nested<Long> nested;
```

`Nested<V>` là `static`, do đó không mang type parameter T từ một instance `Outer<T>` cụ thể. Trong kết quả Java reflection đã kiểm chứng:

```text
static.ownerIsClass=true
static.owner=ParameterizedTypeDemo$Outer
static.arg=java.lang.Long
```

`getOwnerType()` lúc này là một `Class` của Outer, **không** phải `ParameterizedType(Outer<String>)`. Argument Long thuộc Nested, không phải Outer.

Đừng kết luận owner luôn là `ParameterizedType` chỉ vì member có generic. Owner có thể là `Class<?>`, `ParameterizedType` hoặc `null`, tùy khai báo. Với member class lồng nhiều tầng, chính owner cũng có thể có owner khác; cần duyệt theo cấu trúc thay vì ghép chuỗi tùy tiện.

### 5.4. Owner không phải superclass

`Outer<String>.Inner<Integer>` có owner `Outer<String>` vì Inner được khai báo là thành viên của Outer. Nó không có nghĩa Outer là superclass trực tiếp của Inner. Quan hệ kế thừa được đọc bằng `getGenericSuperclass()`; quan hệ khai báo member/owner được đọc bằng `getOwnerType()`.

Đây là hai chiều metadata độc lập:

```text
Owner:  Outer<String> ── chứa khai báo ──> Inner<Integer>

Extends: Child ── kế thừa ──> Parent<Something>
```

Khi viết công cụ dựng sơ đồ Type, không nên vẽ cạnh “extends” chỉ dựa trên `getOwnerType()`.

## 6. Những cách lấy `ParameterizedType` đúng ngữ cảnh

Không chỉ field mới có generic type metadata. Tùy nơi Java khai báo kiểu, bạn có thể đọc bằng các API sau:

| Nguồn khai báo | Reflection API | Ví dụ |
| --- | --- | --- |
| Field | `Field.getGenericType()` | `List<String> names`. |
| Superclass của một lớp | `Class.getGenericSuperclass()` | `class Names extends ArrayList<String>`. |
| Interface mà class triển khai trực tiếp | `Class.getGenericInterfaces()` | `implements Supplier<List<String>>`. |
| Kiểu trả về của method | `Method.getGenericReturnType()` | `List<String> fetch()`. |
| Kiểu các tham số method | `Method.getGenericParameterTypes()` | `consume(List<String> values)`. |
| Kiểu exception được khai báo bởi method | `Method.getGenericExceptionTypes()` | Các trường hợp throws dùng type variable hợp lệ. |
| Generic superclass của anonymous TypeRef | `getClass().getGenericSuperclass()` | `new TypeRef<List<String>>() {}`. |

**Không phải mọi giá trị trả về đều là ParameterizedType.** Một method trả `String` có `getGenericReturnType()` thường là `Class<String>`. Một method trả T có thể có `TypeVariable<?>`. Hãy sử dụng `instanceof` để xử lý đúng các dạng.

### 6.1. Generic superclass

Ví dụ:

```java
class StringList extends ArrayList<String> {
    private static final long serialVersionUID = 1L;
}
```

`StringList.class.getGenericSuperclass()` có thể là một `ParameterizedType` với raw type `ArrayList` và actual argument `String`.

`Class.getSuperclass()` chỉ trả raw class của cha là `ArrayList.class`; `getGenericSuperclass()` giữ mô tả cách subclass khai báo tham số hóa cha. Chương trình phần 8 in:

```text
super.raw=java.util.ArrayList
super.arg=java.lang.String
```

### 6.2. Generic interface

Ví dụ:

```java
class StringSource implements Supplier<List<String>> {
    @Override
    public List<String> get() {
        return List.of("hi");
    }
}
```

`StringSource.class.getGenericInterfaces()[0]` mô tả `Supplier<List<String>>` và là `ParameterizedType`. Actual argument trực tiếp của Supplier là **một ParameterizedType khác**, `List<String>`.

Trong chương trình:

```text
interface.raw=java.util.function.Supplier
interface.arg=java.util.List<java.lang.String>
```

`getGenericInterfaces()` trả các interface được khai báo **trực tiếp** trên class/interface được xét. Nó không tự trả mọi interface từ toàn bộ chuỗi kế thừa hay giải quyết mọi type variable của các ancestor thành kiểu cụ thể. Nếu cần dựng một *type resolver* hoàn chỉnh, phải lần qua cây kế thừa và thực hiện thay thế type variables theo từng quan hệ.

### 6.3. Metadata generic không phải “kiểu đầy đủ” của một instance

Với:

```java
class Box<T> {
    List<T> items;
}
```

`Box.class.getDeclaredField("items").getGenericType()` mô tả `List<T>`, trong đó T là `TypeVariable`.

Nếu khởi tạo `new Box<String>()`, bạn không thể đơn giản gọi `getClass()` trên instance rồi mong JVM trả về một class `Box<String>` riêng. Runtime class vẫn là `Box.class`, và metadata trên field gốc vẫn nói `List<T>`. Khi generic được chuyên biệt hóa ở một subclass, reflection có thể thấy cấu trúc chuyên biệt hóa trên generic superclass của subclass; nhưng việc kết nối nhiều tầng và thay T đòi hỏi thêm thuật toán.

## 7. Những dạng `Type` xuất hiện trong actual arguments

### 7.1. `Class<?>`

Trong `List<String>`, argument là `Class<String>`. Bạn có thể kiểm tra:

```java
Type arg = p.getActualTypeArguments()[0];
if (arg instanceof Class<?> c) {
    System.out.println(c.getName());
}
```

Ở đây `p` là ParameterizedType của `List<String>` đã có. Kết quả là `java.lang.String`.

### 7.2. `ParameterizedType` lồng nhau

Trong `Map<String, List<Integer>>`, argument thứ hai là `ParameterizedType` khác:

```java
Type valueArg = mapType.getActualTypeArguments()[1];
if (valueArg instanceof ParameterizedType nestedList) {
    Type listRaw = nestedList.getRawType();
    Type element = nestedList.getActualTypeArguments()[0];
}
```

`mapType` là ParameterizedType mô tả Map ở lớp ngoài. `listRaw` biểu diễn List, `element` biểu diễn Integer. Không cast `valueArg` trực tiếp thành `Class<List<?>>`.

### 7.3. `WildcardType`

Với:

```java
List<? extends Number> producers;
List<? super Integer> consumers;
```

`getActualTypeArguments()[0]` của mỗi khai báo là `WildcardType`.

- `? extends Number` có upper bound Number.
- `? super Integer` có lower bound Integer và upper bound mặc định Object.

Reflection cung cấp `getUpperBounds()` và `getLowerBounds()` trả `Type[]`. Một wildcard không giới hạn `?` có upper bound Object và không có lower bound tường minh.

Không nên suy luận wildcard bound chỉ từ chuỗi `toString()`; dùng đúng API bounds để phân tích có cấu trúc.

### 7.4. `TypeVariable<?>`

Với:

```java
class Samples<N extends Number> {
    List<N> genericList;
}
```

Argument của `List<N>` là một `TypeVariable` đại diện N. Bạn có thể đọc:

- `getName()` → `N`.
- `getBounds()` → Number (trong ví dụ).
- `getGenericDeclaration()` → nơi N được khai báo, là class hoặc method/constructor.

Không được tự thay N thành Object nếu nó có bound Number. Đồng thời, việc nhìn N có bound Number **không** chứng minh N chính là Number trong mọi cách tham số hóa instance.

### 7.5. `GenericArrayType`

Với:

```java
class Samples<N extends Number> {
    N[] genericArray;
    List<N>[] listArray;
}
```

Các khai báo field đều hợp lệ dù bạn **không thể** trực tiếp khởi tạo `new N[3]` hay `new List<N>[3]`.

- `N[]` có thể được reflection biểu diễn bằng `GenericArrayType` với component type N (`TypeVariable`).
- `List<N>[]` có thể là `GenericArrayType` có component là `ParameterizedType` `List<N>`.
- `String[]`, ngược lại, có runtime `Class<String[]>` vì thành phần String reifiable.

Sự khác nhau này giúp tránh việc ép mọi Type mô tả mảng thành `Class<?>`.

## 8. Chương trình Java 17 hoàn chỉnh

Chương trình dưới đây tự khai báo các kiểu cần phản chiếu và in ra chính xác raw type, owner type, actual arguments, wildcard bounds, type variable và generic array types. Nó cũng cho ví dụ lấy metadata từ superclass/interface và một TypeRef.

**Tệp `ParameterizedTypeDemo.java`:**

```java
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ParameterizedTypeDemo {
    static class Outer<T> {
        class Inner<U> {}
        class Plain {}
        static class Nested<V> {}
    }

    static class Samples<N extends Number> {
        List<String> names;
        Map<String, List<Integer>> nested;
        List<? extends Number> producers;
        List<? super Integer> consumers;
        List<N> genericList;
        N[] genericArray;
        List<N>[] listArray;
        Outer<String>.Inner<Integer> inner;
        Outer<String>.Plain plain;
        Outer.Nested<Long> staticNested;
    }

    static class StringList extends ArrayList<String> {
        private static final long serialVersionUID = 1L;
    }

    static class StringSource implements Supplier<List<String>> {
        @Override public List<String> get() {
            return List.of("hi");
        }
    }

    abstract static class TypeRef<T> {
        final Type getType() {
            Type parent = getClass().getGenericSuperclass();
            if (!(parent instanceof ParameterizedType p)) {
                throw new IllegalStateException("Missing type metadata");
            }
            return p.getActualTypeArguments()[0];
        }
    }

    static Type fieldType(String name) throws ReflectiveOperationException {
        return Samples.class.getDeclaredField(name).getGenericType();
    }

    static ParameterizedType requireParameterized(Type type) {
        if (!(type instanceof ParameterizedType p)) {
            throw new IllegalArgumentException("Not parameterized: " + type);
        }
        return p;
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Field field = Samples.class.getDeclaredField("names");
        ParameterizedType names = requireParameterized(field.getGenericType());
        System.out.println("field.rawClass=" + field.getType().getTypeName());
        System.out.println("field.generic=" + field.getGenericType().getTypeName());
        System.out.println("list.raw=" + names.getRawType().getTypeName());
        System.out.println("list.owner=" + names.getOwnerType());
        System.out.println("list.arg=" + names.getActualTypeArguments()[0].getTypeName());

        ParameterizedType nested = requireParameterized(fieldType("nested"));
        Type nestedValue = nested.getActualTypeArguments()[1];
        System.out.println("map.arity=" + nested.getActualTypeArguments().length);
        System.out.println("map.valueParameterized=" + (nestedValue instanceof ParameterizedType));
        System.out.println("map.valueArg=" + requireParameterized(nestedValue)
                .getActualTypeArguments()[0].getTypeName());

        ParameterizedType inner = requireParameterized(fieldType("inner"));
        ParameterizedType innerOwner = requireParameterized(inner.getOwnerType());
        System.out.println("inner.raw=" + inner.getRawType().getTypeName());
        System.out.println("inner.ownerParameterized=" + (inner.getOwnerType() instanceof ParameterizedType));
        System.out.println("inner.ownerRaw=" + innerOwner.getRawType().getTypeName());
        System.out.println("inner.ownerArg=" + innerOwner.getActualTypeArguments()[0].getTypeName());
        System.out.println("inner.arg=" + inner.getActualTypeArguments()[0].getTypeName());

        ParameterizedType plain = requireParameterized(fieldType("plain"));
        System.out.println("plain.ownArgs=" + plain.getActualTypeArguments().length);
        System.out.println("plain.ownerArg=" + requireParameterized(plain.getOwnerType())
                .getActualTypeArguments()[0].getTypeName());

        ParameterizedType staticNested = requireParameterized(fieldType("staticNested"));
        System.out.println("static.ownerIsClass=" + (staticNested.getOwnerType() instanceof Class<?>));
        System.out.println("static.owner=" + staticNested.getOwnerType().getTypeName());
        System.out.println("static.arg=" + staticNested.getActualTypeArguments()[0].getTypeName());

        WildcardType upper = (WildcardType) requireParameterized(fieldType("producers"))
                .getActualTypeArguments()[0];
        WildcardType lower = (WildcardType) requireParameterized(fieldType("consumers"))
                .getActualTypeArguments()[0];
        System.out.println("wildcard.upper=" + upper.getUpperBounds()[0].getTypeName());
        System.out.println("wildcard.lower=" + lower.getLowerBounds()[0].getTypeName());

        TypeVariable<?> variable = (TypeVariable<?>) requireParameterized(fieldType("genericList"))
                .getActualTypeArguments()[0];
        System.out.println("variable.name=" + variable.getName());
        System.out.println("variable.bound=" + variable.getBounds()[0].getTypeName());

        GenericArrayType genericArray = (GenericArrayType) fieldType("genericArray");
        GenericArrayType listArray = (GenericArrayType) fieldType("listArray");
        System.out.println("array.component=" + genericArray.getGenericComponentType().getTypeName());
        System.out.println("array.listComponentParameterized="
                + (listArray.getGenericComponentType() instanceof ParameterizedType));

        ParameterizedType subclass = requireParameterized(StringList.class.getGenericSuperclass());
        System.out.println("super.raw=" + subclass.getRawType().getTypeName());
        System.out.println("super.arg=" + subclass.getActualTypeArguments()[0].getTypeName());

        ParameterizedType implemented =
                requireParameterized(StringSource.class.getGenericInterfaces()[0]);
        System.out.println("interface.raw=" + implemented.getRawType().getTypeName());
        System.out.println("interface.arg=" + implemented.getActualTypeArguments()[0].getTypeName());

        Type captured = new TypeRef<List<String>>() {}.getType();
        System.out.println("captured=" + captured.getTypeName());
        System.out.println("captured.equalsField=" + captured.equals(names));

        List<?> runtimeList = List.of(1, 2);
        System.out.println("runtime.isList=" + List.class.isInstance(runtimeList));
        System.out.println("runtime.isArrayList=" + (runtimeList instanceof ArrayList<?>));
    }
}
```

**Biên dịch và chạy:**

```bash
javac --release 17 -Xlint:all ParameterizedTypeDemo.java
java ParameterizedTypeDemo
```

**Stdout đã kiểm chứng trên JDK hỗ trợ Java 17:**

```text
field.rawClass=java.util.List
field.generic=java.util.List<java.lang.String>
list.raw=java.util.List
list.owner=null
list.arg=java.lang.String
map.arity=2
map.valueParameterized=true
map.valueArg=java.lang.Integer
inner.raw=ParameterizedTypeDemo$Outer$Inner
inner.ownerParameterized=true
inner.ownerRaw=ParameterizedTypeDemo$Outer
inner.ownerArg=java.lang.String
inner.arg=java.lang.Integer
plain.ownArgs=0
plain.ownerArg=java.lang.String
static.ownerIsClass=true
static.owner=ParameterizedTypeDemo$Outer
static.arg=java.lang.Long
wildcard.upper=java.lang.Number
wildcard.lower=java.lang.Integer
variable.name=N
variable.bound=java.lang.Number
array.component=N
array.listComponentParameterized=true
super.raw=java.util.ArrayList
super.arg=java.lang.String
interface.raw=java.util.function.Supplier
interface.arg=java.util.List<java.lang.String>
captured=java.util.List<java.lang.String>
captured.equalsField=true
runtime.isList=true
runtime.isArrayList=false
```

Khi kiểm tra chương trình bằng `javac --release 17 -Xlint:all` sau khi khai báo `serialVersionUID` cho `StringList`, compiler không phát cảnh báo trong phép thử.

### Đọc kết quả theo ba nhóm chính

**Raw type:** `field.getType()` và `names.getRawType()` cùng biểu diễn `List`. `getGenericType()` biểu diễn cả `List<String>`.

**Owner type:** trường `inner` có owner parameterized `Outer<String>`, trường `plain` có **0** actual arguments riêng nhưng owner vẫn chứa String, còn trường `staticNested` có owner dạng Class Outer.

**Actual arguments:** `names` có String; `nested` có String và List<Integer> ở hai vị trí trực tiếp; các field `producers`/`consumers` có wildcard; `genericList` có TypeVariable N, không tự biến thành Number hoặc Integer; các field mảng generic dùng GenericArrayType.

### Vì sao không in tên lớp implementation của `ParameterizedType`?

Một implementation JDK có thể dùng một lớp nội bộ để hiện thực interface `ParameterizedType`. Tên lớp nội bộ đó không phải hợp đồng public API ổn định. Vì vậy chương trình in các kiểm tra `instanceof ParameterizedType` và dữ liệu semantic từ getter, thay vì phụ thuộc vào `type.getClass().getSimpleName()`.

## 9. TypeRef: giữ mô tả `List<String>` qua anonymous subclass

Trong Java, `List<String>.class` không phải biểu thức hợp lệ và `List.class` không lưu argument String. Một kỹ thuật phổ biến để chuyển mô tả generic cho framework là **type reference** (hay *super type token*).

Mẫu tối giản:

```java
abstract static class TypeRef<T> {
    final Type getType() {
        Type superclass = getClass().getGenericSuperclass();
        if (!(superclass instanceof ParameterizedType type)) {
            throw new IllegalStateException("Missing type metadata");
        }
        return type.getActualTypeArguments()[0];
    }
}

Type captured = new TypeRef<List<String>>() {}.getType();
```

Khối trên là trích đoạn phải đặt trong một lớp (và dòng tạo instance trong method); ví dụ hoàn chỉnh ở phần 8 đã đặt đúng vị trí.

Tại sao hoạt động?

1. Biểu thức `new TypeRef<List<String>>() {}` tạo một anonymous subclass.
2. Generic superclass của subclass được **khai báo** là `TypeRef<List<String>>`.
3. Class file có thể lưu metadata về cách subclass khai báo generic superclass.
4. `getGenericSuperclass()` đọc `ParameterizedType` của `TypeRef<List<String>>`.
5. `getActualTypeArguments()[0]` lấy mô tả `List<String>` dưới dạng `Type`.

Trong chương trình phần 8, `captured.equalsField=true` vì `TypeRef` và field `names` cùng mô tả cấu trúc `List<String>`. Đây là bằng chứng so sánh **kiểu khai báo** trong reflection, không có nghĩa hai đối tượng List nào đó đang chứa cùng dữ liệu.

### Khi TypeRef không giúp gì?

Nếu nguồn là:

```java
Object input = List.of(42);
```

thì `TypeRef<List<String>>` **không** chứng minh input chứa toàn String. Nó chỉ lưu mô tả kiểu mà caller mong muốn. Một thư viện deserializer có thể dùng mô tả ấy để quyết định cách tạo/chuyển từng phần tử, nhưng **thư viện phải thực thi việc kiểm tra/chuyển đổi**; JVM không tự gắn metadata của TypeRef lên input.

Một tình huống khác: nếu tạo một TypeRef qua tầng generic mà argument vẫn chỉ là biến kiểu `T`, metadata có thể chứa `TypeVariable<T>` thay vì một Class cụ thể. Không nên kỳ vọng reflection luôn thay T thành kiểu runtime thật của giá trị đang có.

## 10. Phân tích `ParameterizedType` an toàn: mẫu thuật toán

Một Type explorer hữu ích cần phân nhánh theo **loại interface của Type**, không dựa vào tên chuỗi hay class nội bộ của implementation.

Pseudocode định hướng:

```java
static void inspect(Type type) {
    if (type instanceof Class<?> clazz) {
        System.out.println("Class: " + clazz.getName());
    } else if (type instanceof ParameterizedType p) {
        System.out.println("Raw: " + p.getRawType().getTypeName());
        Type owner = p.getOwnerType();
        if (owner != null) {
            System.out.println("Owner:");
            inspect(owner);
        }
        for (Type arg : p.getActualTypeArguments()) {
            inspect(arg);
        }
    } else if (type instanceof TypeVariable<?> variable) {
        System.out.println("Variable: " + variable.getName());
        for (Type bound : variable.getBounds()) {
            System.out.println("Bound: " + bound.getTypeName());
        }
    } else if (type instanceof WildcardType wildcard) {
        for (Type upper : wildcard.getUpperBounds()) {
            System.out.println("Upper: " + upper.getTypeName());
        }
        for (Type lower : wildcard.getLowerBounds()) {
            System.out.println("Lower: " + lower.getTypeName());
        }
    } else if (type instanceof GenericArrayType array) {
        System.out.println("Generic array component:");
        inspect(array.getGenericComponentType());
    }
}
```

Đây là phương thức minh họa: phải đặt trong class với import `java.lang.reflect.*` hoặc import riêng các interface. Một Type explorer sản phẩm cần thêm chính sách tránh lặp/vòng đệ quy của type variables có bounds tự tham chiếu, chẳng hạn `E extends Enum<E>`, và giới hạn độ sâu nếu xử lý metadata không đáng tin.

Các lưu ý:

- Truy cập `getOwnerType()` **trước hoặc cùng lúc** với arguments, để không bỏ sót generic của member enclosing type.
- Đừng giả định `getActualTypeArguments()` luôn có ít nhất một phần tử.
- Đừng ép mọi argument thành Class; dùng `instanceof`.
- Nếu owner là `ParameterizedType`, có thể đi tiếp vào owner của owner.
- Nếu một `TypeVariable` có nhiều bounds, `getBounds()` trả các bound; bounds có thể tự chứa type variable, cần phòng vòng lặp khi phân tích toàn bộ graph.
- `getTypeName()` thích hợp để hiển thị, **không** phải định dạng serialization chuẩn để tự viết parser generic bằng `split("<")` hoặc `split(",")`.

## 11. Những bẫy dễ gặp khi dùng reflection generics

| Nhầm lẫn | Hệ quả | Cách hiểu đúng |
| --- | --- | --- |
| “`getRawType()` trả đầy đủ `List<String>`.” | Mất String khi phân tích. | Raw type chỉ là phần class/interface nền; đọc arguments riêng. |
| “`getOwnerType()` luôn null.” | Bỏ sót Outer<String> trong inner class. | Member types có owner; kiểu top-level thường không. |
| “Owner luôn là `Class<?>`.” | Cast hỏng với `Outer<String>.Inner<Integer>`. | Owner có thể là ParameterizedType mang type arguments. |
| “Mọi argument của nested type đều nằm trong một mảng arguments.” | Trộn hoặc mất argument của owner. | Inner own args và owner args nằm ở hai cấp khác nhau. |
| “ParameterizedType phải có ít nhất một argument.” | Bỏ qua `Outer<String>.Plain`. | Thành viên không generic vẫn có thể có owner tham số hóa và 0 own args. |
| “`List<T>` luôn có argument là Class.” | `ClassCastException` khi gặp TypeVariable. | Argument là Type, có thể có nhiều dạng. |
| “`List<? extends Number>` có argument Number.” | Mất thông tin wildcard. | Actual arg là WildcardType với upper bound Number. |
| “Field.getGenericType() luôn là ParameterizedType.” | Cast lỗi với String, raw List hoặc T[]. | Kiểm tra loại Type thực trước. |
| “`TypeRef<List<String>>` xác thực một List runtime.” | Tin dữ liệu không được kiểm tra. | Nó lưu metadata type declaration; cần validator/deserializer riêng. |
| “Tên lớp nội bộ của reflection là API ổn định.” | Code phụ thuộc JDK implementation. | Phụ thuộc các interface Type, Class, ParameterizedType,... |
| “`getOwnerType()` có nghĩa superclass.” | Dựng sai sơ đồ kiểu. | Owner là quan hệ kiểu khai báo member, khác inheritance. |
| “`getGenericSuperclass()` tự thay thế mọi T trong field kế thừa.” | Resolver generic trả kết quả sai. | Phải đi qua type hierarchy và thay type variables đúng ngữ cảnh. |

## 12. Khi nào raw type, owner và actual args quan trọng trong dự án?

### 12.1. JSON deserialization và data binding

Một thư viện nhận mô tả `List<User>` cần biết:

- Container raw: `List`.
- Type argument: `User`.
- Với `Map<String,List<User>>`, phải đi sâu vào argument thứ hai và argument của List.

Một `Class<List>` riêng chỉ nhận diện container, không nói kiểu object trong mỗi phần tử. `ParameterizedType` giúp truyền **mô tả**; framework vẫn phải tạo object và xác thực/chuyển dữ liệu theo logic của nó.

### 12.2. Dependency injection và generic repositories

Một dependency như `Repository<Order>` và `Repository<Customer>` có cùng raw type Repository. Công cụ có thể đọc actual arguments trong khai báo field/constructor để phân biệt hai dependency mong muốn.

Với inner class chứa generic owner, resolver phải xét `getOwnerType()`; nếu chỉ so raw và actual arguments ở lớp trong, thông tin enclosing generic có thể bị bỏ sót.

### 12.3. ORM/serializer/schema generator

Công cụ tạo schema có thể đọc `Field.getGenericType()` để phân biệt `List<String>`, `List<Money>`, `Map<String,Amount>`. Khi gặp `List<T>`, công cụ có thể cần đi qua type hierarchy để xác định T hoặc chấp nhận rằng T chưa biết.

Không nên dùng metadata làm bằng chứng dữ liệu đã hợp lệ. Ví dụ một trường khai báo `List<String>` vẫn có thể trỏ tới collection bị thêm Integer thông qua raw/unchecked operation.

### 12.4. Công cụ phân tích API và tài liệu

Khi sinh tài liệu từ reflection, cần hiển thị generic declaration chính xác thay vì chỉ `getType()`. Với nested member type, owner quan trọng để hiện dạng `Outer<String>.Inner<Integer>` thay vì chỉ `Inner<Integer>`.

Phải chấp nhận metadata không phải lúc nào cũng có đủ thông tin, nhất là code legacy, raw declarations, erased generic methods và các lớp tạo động không giữ generic Signature như kỳ vọng.

## 13. `equals`, tính bất biến và những điều không nên giả định

`ParameterizedType` định nghĩa `equals` để các implementation phải so sánh dựa trên cùng khai báo generic và các type parameters tương ứng. Vì vậy hai reflective descriptions tương đương về kiểu có thể so sánh bằng `equals`, thay vì kiểm tra cùng tham chiếu object bằng `==`.

Chương trình ở phần 8 đã tạo:

- Type từ field `Samples.names`: `List<String>`.
- Type từ `new TypeRef<List<String>>() {}`: cũng `List<String>`.

Dòng `captured.equalsField=true` minh họa sự tương đương này. Không suy ra các `ParameterizedType` nhất thiết được intern, singleton hoặc trả chính cùng instance giữa các API.

`getActualTypeArguments()` cung cấp một mảng kết quả để người dùng đọc; không nên sửa mảng trả về rồi mong thay đổi metadata của class file hay Type gốc. Hãy xem mô tả kiểu là cấu trúc reflection được dùng để đọc và so sánh; muốn biến đổi một graph of types phục vụ framework, xây dựng mô hình riêng với các quy tắc tương đương rõ ràng.

## 14. Những tình huống biên cần cân nhắc

### 14.1. Kiểu top-level và member type

`List<String>` có owner null. `Outer<String>.Inner<Integer>` có owner `Outer<String>`. `Outer.Nested<Long>` có owner Class Outer. Không có một dạng owner chung dùng được cho mọi member type.

### 14.2. Generic metadata có thể thiếu hoặc hỏng

Reflection có thể gặp metadata `Signature` tham chiếu tới class không tồn tại hoặc cấu trúc signature không phù hợp. Các API reflection generic có thể ném `TypeNotPresentException`, `MalformedParameterizedTypeException` hoặc các ngoại lệ khác tùy tình huống/đường gọi. Đối với công cụ phân tích class file từ nguồn không tin cậy, hãy xử lý lỗi thay vì giả định metadata luôn hợp lệ.

### 14.3. Owner và type variable phức tạp

Với các kiểu lồng nhiều tầng, một inner có thể chịu ảnh hưởng type variable của nhiều owner. Việc ánh xạ tên T ở các scope khác nhau phải dựa trên `TypeVariable.getGenericDeclaration()` và cấu trúc owner, không chỉ so tên chữ `T`.

Ví dụ `Outer<T>` và `Inner<T>` đều có thể đặt tên T trong hai scope khác nhau; hai TypeVariable không tự đồng nhất chỉ vì `getName()` giống nhau.

### 14.4. Đối số là mảng generic

Một field `List<String>[]` có thể khai báo hợp lệ dù không được phép cấp phát trực tiếp `new List<String>[2]`. `getGenericType()` cho Type phản ánh mảng có component tham số hóa. Hãy phân biệt **khai báo kiểu field** với **biểu thức tạo đối tượng mảng**.

### 14.5. TypeName để hiển thị, không phải chứng minh dữ liệu

`type.getTypeName()` giúp in một biểu diễn kiểu dễ đọc. Nó không thực hiện kiểm tra runtime đối với collection, không giải quyết type variables theo object instance và không thay thế một type resolver.

### 14.6. Reflection API và khả năng tương thích JDK

Hãy sử dụng các interface public `java.lang.reflect.Type`, `ParameterizedType`, `WildcardType`, `TypeVariable` và `GenericArrayType`. Tránh phụ thuộc tên hoặc constructor của lớp hiện thực bên trong JDK vì chúng là chi tiết triển khai. Ví dụ mẫu được kiểm thử ở Java 17 và không cần truy cập các internal packages.

## 15. Hướng dẫn đọc `ParameterizedType` khi debug

Nếu log nói “không xác định được generic type” hoặc framework đọc sai một repository nested:

1. **Xác định vị trí khai báo.** Đó là field, generic superclass, implemented interface, return type hay method parameter?
2. **Dùng API `getGeneric...` tương ứng**, không chỉ `getType()`/`getSuperclass()`.
3. **Kiểm tra Type subtype** bằng `instanceof` trước khi cast.
4. **Đọc raw type** để xác định cấu trúc nền: List, Map, Repository, Outer.Inner.
5. **Đọc owner type** và duyệt owner nếu khác null, đặc biệt với inner class không static.
6. **Đọc actual arguments** theo từng cấp; không flatten các nested ParameterizedType tùy tiện.
7. **Xử lý wildcard, variable, generic array** theo API riêng, không cast hết sang Class.
8. **Nếu gặp T chưa biết**, lần theo generic superclass/interfaces và môi trường type variables thay vì tự gán T=Object.
9. **Nếu cần xác thực dữ liệu**, tách bước kiểm tra giá trị khỏi bước đọc metadata. TypeRef hoặc field annotation không đủ làm validator.
10. **Kiểm tra trên đúng bytecode/JDK đang chạy** khi metadata hoặc proxy class khác kết quả mong đợi.

Một cách log ổn định là in `Type.getTypeName()` kèm từng thành phần có cấu trúc và cờ `instanceof`, tránh log tên lớp cài đặt nội bộ của reflection.

## 16. Bài tập và đáp án

### Bài 1: Đọc List String

Field khai báo `List<String> names`. `getRawType()`, `getOwnerType()` và `getActualTypeArguments()` là gì?

**Đáp án:** Raw type là List; owner null; có một actual argument là Class String.

### Bài 2: Map lồng nhau

Với `Map<String,List<Integer>>`, số lượng actual arguments trực tiếp của Map là bao nhiêu?

**Đáp án:** Hai. Argument thứ hai là ParameterizedType List<Integer>, và Integer nằm trong actual arguments của List, không phải argument thứ ba của Map.

### Bài 3: Inner class

Với `Outer<String>.Inner<Integer>`, String nằm ở đâu?

**Đáp án:** Trong `getOwnerType()`, là ParameterizedType Outer<String>. Argument trực tiếp của Inner là Integer.

### Bài 4: Member không generic riêng

Tại sao `Outer<String>.Plain` vẫn có thể được phản chiếu thành ParameterizedType với 0 own arguments?

**Đáp án:** Plain là member không static của một owner được tham số hóa. Kiểu của owner mang String, còn Plain không khai báo type parameter riêng.

### Bài 5: Static nested

Với `Outer.Nested<Long>`, owner có cần mang T của Outer không?

**Đáp án:** Không. Nested static không gắn với một instance Outer<T>; trong ví dụ Java 17, owner là Class Outer và actual argument trực tiếp là Long.

### Bài 6: Wildcard

`List<? extends Number>` có actual argument thuộc loại Class Number hay WildcardType?

**Đáp án:** WildcardType. Number là upper bound đọc từ `getUpperBounds()`.

### Bài 7: Type variable

Một field trong `class Box<T> { List<T> xs; }` có generic argument T được reflection mô tả dưới dạng gì?

**Đáp án:** TypeVariable, không phải Class String tự động theo object Box<String> đang có.

### Bài 8: GenericArrayType

Vì sao `T[]` và `String[]` có thể trả loại Type khác nhau?

**Đáp án:** Mảng `T[]` có component là type variable non-reifiable nên có thể là GenericArrayType; `String[]` có Class runtime cụ thể.

### Bài 9: TypeRef

`new TypeRef<List<String>>() {}.getType()` có xác nhận một List đang chứa toàn String không?

**Đáp án:** Không. Nó mô tả generic superclass của anonymous subclass. Validator hoặc deserializer cần thực sự kiểm tra/chuyển đổi dữ liệu.

### Bài 10: So sánh Type

Có nên so `ParameterizedType` bằng `==` để xác định hai mô tả cùng `List<String>` không?

**Đáp án:** Không. Dùng `equals` cho sự tương đương về mô tả kiểu; hai API không phải trả cùng instance Type.

### Bài 11: Chẩn đoán ClassCastException

Nếu `Field.getGenericType()` trả `Class<?>` nhưng code ép luôn sang `ParameterizedType` rồi lỗi, cần sửa gì?

**Đáp án:** Kiểm tra loại Type bằng `instanceof ParameterizedType`. Field raw hoặc class thường không có ParameterizedType.

### Bài 12: Owner khác extends

`getOwnerType()` có trả superclass không?

**Đáp án:** Không. Nó mô tả owner của member type; quan hệ generic superclass đọc bằng `getGenericSuperclass()`.

## 17. Tóm tắt

`ParameterizedType` giúp Java reflection đọc lại cấu trúc một **kiểu được khai báo** sau khi type erasure khiến các instance không còn lớp runtime riêng cho mọi cách tham số hóa. Hãy hiểu ba thành phần chính:

- **Raw type:** class/interface nền, chẳng hạn List, Map hoặc Outer.Inner.
- **Owner type:** kiểu bao ngoài của member type, có thể null, Class hoặc ParameterizedType.
- **Actual type arguments:** những tham số kiểu được áp dụng trực tiếp cho type này, có thể là Class, ParameterizedType, WildcardType, TypeVariable hoặc các cấu trúc Type khác.

Với member classes, không được bỏ owner: `Outer<String>.Inner<Integer>` chứa String trong owner và Integer trong own arguments. Với generic API phức tạp, hãy duyệt Type như **một cây cấu trúc**, kiểm tra subtype bằng `instanceof`, theo dõi bounds/type variables khi cần, và tách metadata khỏi validation runtime.

**Ghi nhớ:** Reflection có thể mô tả “kiểu đã được viết trong khai báo”, nhưng không biến `List<String>` thành một class runtime mới và không tự bảo đảm dữ liệu thực tế bên trong List là String.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-reflection-005">TypeVariable: nơi khai báo và các giới hạn</a>

<details>
<summary>Xem chi tiết</summary>

# `TypeVariable`: nơi khai báo và các giới hạn trong Java Reflection

Trong Java Generics, một tham số kiểu như `T`, `E` hay `U` có thể được **khai báo bởi class, interface, method hoặc constructor**. Khi dùng reflection để nghiên cứu tham số kiểu đó, Java biểu diễn nó bằng interface **`java.lang.reflect.TypeVariable<D extends GenericDeclaration>`**.

Ví dụ:

```java
class Box<T extends Number> {
    T value;

    <U extends CharSequence> U echo(U input) {
        return input;
    }
}
```

Ở đây:

- `T` là **type variable do class `Box` khai báo**, với upper bound `Number`.
- `U` là **type variable do method `echo` khai báo**, với upper bound `CharSequence`.
- `T` và `U` không phải tên của các runtime class cụ thể. Chúng đại diện cho **biến kiểu của khai báo**; cách gắn type arguments ở nơi sử dụng sẽ được compiler kiểm tra theo hợp đồng generic.

Dùng reflection:

```java
java.lang.reflect.TypeVariable<?> t =
        Box.class.getTypeParameters()[0];

System.out.println(t.getName()); // T
System.out.println(t.getBounds()[0].getTypeName());
// java.lang.Number
```

**Hai câu hỏi cốt lõi khi gặp `TypeVariable` là: “Ai khai báo biến kiểu này?” và “Các upper bounds của nó là gì?”** Câu trả lời nằm ở `getGenericDeclaration()` và `getBounds()`, không phải bằng cách đoán từ tên `T` hay runtime object.

## 1. Vị trí của `TypeVariable` trong hệ thống `Type`

`java.lang.reflect.Type` là interface chung cho những mô tả kiểu được reflection cung cấp. Các dạng tiêu biểu:

| Đối tượng reflection | Đại diện cho | Ví dụ |
| --- | --- | --- |
| `Class<?>` | Runtime class/interface/primitive/array với component type phù hợp | `String.class`, `Number.class`, `String[].class` |
| `ParameterizedType` | Kiểu tham số hóa của một khai báo | `List<String>`, `Comparable<T>` |
| **`TypeVariable<D>`** | **Biến kiểu được khai báo bởi generic declaration** | `T` trong `Box<T>`, `U` trong `<U> U echo(U)` |
| `WildcardType` | Type argument dạng wildcard | `? extends Number`, `? super Integer` |
| `GenericArrayType` | Array có generic component type không reifiable | `T[]`, `List<String>[]` |

`TypeVariable` cũng liên quan `AnnotatedElement`, vì có thể đọc annotation trên **khai báo type parameter** (khi annotation có retention phù hợp).

Không được đánh đồng:

```java
class Example<T extends Number> {
    java.util.List<T> values;
}
```

- `T` là **`TypeVariable`**.
- `List<T>` là **`ParameterizedType`** ở ngoài; phần tử của `getActualTypeArguments()` là `TypeVariable` của `T`.
- `Number.class` là **`Class`** mô tả upper bound đã nêu.
- `T[]` trong một khai báo là **`GenericArrayType`** ở ngoài, và generic component type bên trong là `TypeVariable`.

Một cây reflection có thể chứa nhiều dạng `Type` lồng nhau. Đừng cast mọi giá trị `Type` thành `Class`.

## 2. Chữ ký `TypeVariable<D extends GenericDeclaration>` nói gì?

Có thể hình dung những thành viên quan trọng của interface:

```java
// SƠ ĐỒ API, không phải mã nguồn định nghĩa lại TypeVariable:
interface TypeVariable<D extends java.lang.reflect.GenericDeclaration>
        extends java.lang.reflect.Type,
                java.lang.reflect.AnnotatedElement {

    java.lang.reflect.Type[] getBounds();
    D getGenericDeclaration();
    String getName();
    java.lang.reflect.AnnotatedType[] getAnnotatedBounds();
}
```

Tham số `D` trong **chính interface `TypeVariable<D>`** chỉ định **loại đối tượng reflection đại diện cho nơi khai báo**. `GenericDeclaration` được hiện thực bởi `Class`, `Method` và `Constructor` cho các trường hợp liên quan.

Ví dụ bạn có thể gặp `TypeVariable<Class<MyBox>>` khi đọc type parameters của class và `TypeVariable<Method>` khi đọc từ một method. Trong code xử lý chung, `TypeVariable<?>` đủ dùng nếu không cần giữ loại declaration chính xác ở compile time.

Các thành viên có vai trò khác nhau:

- `getName()`: tên nguồn của type variable, ví dụ `T`, `E`, `K`.
- `getGenericDeclaration()`: đối tượng **Class/Method/Constructor đã khai báo** type variable, không phải object generic ở runtime.
- `getBounds()`: danh sách **upper bounds** dưới dạng `Type[]`, theo thứ tự được biểu diễn trong khai báo.
- `getAnnotatedBounds()`: các upper bounds ở dạng **`AnnotatedType[]`**, để đọc type-use annotations trên bounds.
- Các API từ `AnnotatedElement`, chẳng hạn `getAnnotation(...)`, cho phép đọc annotations đặt trên **chính khai báo type parameter**, khi phù hợp với `@Target` và `@Retention`.

**Lưu ý:** đoạn trên chỉ minh họa hình dạng API. Trong chương trình thực tế, bạn import và sử dụng `java.lang.reflect.TypeVariable` có sẵn, không tự định nghĩa interface trùng tên.

## 3. Ai có thể khai báo type variable?

Một điểm đặc biệt của Java reflection: **generic declaration** có thể là class/interface, method hoặc constructor.

### 3.1. Class/interface generic

```java
class Pair<K, V> {
    K key;
    V value;
}
```

Đọc type variables do class khai báo:

```java
java.lang.reflect.TypeVariable<?>[] variables =
        Pair.class.getTypeParameters();

for (java.lang.reflect.TypeVariable<?> variable : variables) {
    System.out.println(variable.getName());
}
```

Đầu ra:

```text
K
V
```

`Pair.class.getTypeParameters()` trả các tham số kiểu **được khai báo trực tiếp ở `Pair`**, theo thứ tự; không trả type arguments cụ thể của một instance `Pair<String, Integer>`.

Một interface generic cũng là generic declaration:

```java
interface Repository<ID, ENTITY> {
    ENTITY find(ID id);
}
```

`Repository.class.getTypeParameters()` cho hai biến `ID` và `ENTITY`.

### 3.2. Generic method

```java
class Utilities {
    static <T extends Number> T same(T value) {
        return value;
    }
}
```

Đọc từ chính đối tượng `Method`:

```java
java.lang.reflect.Method method =
        Utilities.class.getDeclaredMethod("same", Number.class);

java.lang.reflect.TypeVariable<?> t =
        method.getTypeParameters()[0];

System.out.println(t.getGenericDeclaration().equals(method)); // true
```

**Tại sao tìm method bằng `Number.class` thay vì `T.class`?** Vì `T` có bound `Number`, nên erasure của tham số `T` trong descriptor dùng để tìm `getDeclaredMethod` là `Number`.

### 3.3. Generic constructor

Constructor của một class có thể tự khai báo type parameter:

```java
class Message {
    <T extends CharSequence> Message(T text) {
        System.out.println(text);
    }
}
```

Ví dụ reflection:

```java
java.lang.reflect.Constructor<?> ctor =
        Message.class.getDeclaredConstructor(CharSequence.class);

java.lang.reflect.TypeVariable<?> t =
        ctor.getTypeParameters()[0];

System.out.println(t.getGenericDeclaration().equals(ctor)); // true
```

Constructor type parameter **khác** type parameter của class, ngay cả khi constructor nằm trong một generic class. `getTypeParameters()` của constructor chỉ trả những tham số mà **constructor tự khai báo**.

**Quan trọng:** khi so sánh các đối tượng `Method`/`Constructor` do reflection trả về, dùng `.equals()` thay vì dựa vào tham chiếu `==`. Hai lần truy vấn reflection có thể cung cấp wrapper objects khác nhau đại diện cho cùng một declaration. Với class object, ví dụ `Box.class`, đối tượng `Class` biểu diễn cùng class do một class loader cụ thể định danh.

## 4. `getGenericDeclaration()`: tên giống nhau không có nghĩa cùng một biến kiểu

Java cho phép một generic method dùng lại tên `T` của class; method-level `T` che khuất class-level `T` trong phạm vi method:

```java
class Container<T extends Number> {
    T classValue;

    <T extends CharSequence> T echo(T value) {
        return value;
    }
}
```

Ở đây có **hai type variables độc lập**:

- `T` của `Container<T extends Number>`, được **class `Container`** khai báo.
- `T` của `<T extends CharSequence> T echo(T)`, được **method `echo`** khai báo.

So sánh theo tên là sai:

```java
// Hai TypeVariable đọc từ class và method:
// classT.getName().equals(methodT.getName()) -> true
// classT.equals(methodT)                   -> false
```

Đây không phải mâu thuẫn: **tên `T` chỉ là nhãn**, giống việc hai method khác nhau có cùng tên biến local `value`. Để hiểu type variable, phải xét **generic declaration** và vị trí khai báo.

Nếu đang dựng bản đồ ánh xạ generic types cho một framework, không dùng chuỗi `"T"` làm key toàn cục. Những biến kiểu có tên trùng trong hai class, hai method hoặc hai constructor khác nhau có ý nghĩa và bounds riêng.

### Cách so sánh đúng

```java
boolean sameDeclaration =
        classT.getGenericDeclaration()
              .equals(methodT.getGenericDeclaration());

boolean sameVariable = classT.equals(methodT);
```

Các kết quả phản ánh **định danh khai báo**, không chỉ tên. `TypeVariable` có hợp đồng `equals`/`hashCode` để đại diện type variables của cùng generic declaration; không nên dựa vào object identity `==` của các wrapper reflection.

## 5. `getBounds()`: đọc upper bounds và mặc định `Object`

Xét:

```java
class Simple<T, N extends Number> {}
```

Nếu `T` không có `extends` rõ ràng, nó có **upper bound ngầm `Object`**. Khi đọc qua reflection:

```java
java.lang.reflect.TypeVariable<?>[] vars =
        Simple.class.getTypeParameters();

System.out.println(vars[0].getBounds()[0].getTypeName());
System.out.println(vars[1].getBounds()[0].getTypeName());
```

Đầu ra:

```text
java.lang.Object
java.lang.Number
```

Vì vậy, `getBounds()` không trả một mảng rỗng chỉ vì bạn không ghi `extends`.

### Upper bounds khác actual type arguments

Với `N extends Number`:

- **Bound:** `Number`, mô tả giới hạn trên của các type arguments có thể gắn với `N`.
- **Actual type argument:** chẳng hạn `Integer` tại nơi sử dụng `Simple<String, Integer>`.
- **Type variable:** `N`, vẫn được biểu diễn là `TypeVariable` khi đọc khai báo `Simple`.

Reflection không tự biến `getBounds()` thành `Integer.class` chỉ vì có một instance `Simple<String, Integer>` ở đâu đó.

Tương tự, `T extends Number` không có nghĩa `T` luôn bằng `Number`: `Integer`, `Double` và các lớp con phù hợp cũng có thể là type arguments.

## 6. Nhiều giới hạn: `T extends A & B & C`

Java cho phép khai báo type variable với **intersection bounds**:

```java
class Numeric<T extends Number & Comparable<T>> {
    T value;
}
```

`T` phải thỏa cả hai ràng buộc:

1. Là một subtype của `Number`.
2. Có quan hệ triển khai `Comparable<T>` phù hợp.

Reflection:

```java
java.lang.reflect.TypeVariable<?> variable =
        Numeric.class.getTypeParameters()[0];

for (java.lang.reflect.Type bound : variable.getBounds()) {
    System.out.println(bound.getTypeName());
}
```

Đầu ra:

```text
java.lang.Number
java.lang.Comparable<T>
```

`getBounds()` trả **từng bound theo thứ tự**. Không có một `Class` riêng tên `Number & Comparable<T>`. Cấu trúc intersection được lưu như **nhiều phần tử `Type`**, trong đó:

- `Number` thường là `Class`.
- `Comparable<T>` thường là `ParameterizedType` với type argument `T` là một `TypeVariable` tham chiếu chính nó.

### Vị trí class bound và interface bounds

Nếu có class bound thì nó đứng đầu; các interface bounds theo sau và nối bằng `&`. Không thể khai báo nhiều class bounds độc lập như `T extends Number & String`.

Nếu bound đầu tiên là interface, erasure của `T` dựa trên **bound đầu tiên** ấy. Ví dụ:

```java
class Task<T extends Runnable & AutoCloseable> {
    T task;
}
```

Erasure của `T` dùng `Runnable`, không phải tự động `Object` chỉ vì cả hai bounds đều là interface.

**Thứ tự bounds ảnh hưởng erasure** và có thể ảnh hưởng chữ ký bytecode/các ràng buộc tương thích nguồn. Không nên đảo thứ tự bounds chỉ vì trông tương đương về quan hệ subtype mà bỏ qua tác động đến API đã biên dịch.

### Điều kiện từ bound không biến thành type argument

Nếu `T extends Comparable<T>`, `getBounds()[0]` có thể là một `ParameterizedType` biểu diễn `Comparable<T>`. Đừng cast nó thành `Class` và đừng nhầm `T` bên trong với một runtime class cụ thể.

## 7. Type erasure: bound đầu tiên quyết định góc nhìn runtime

Khi generic type bị xóa, Java dùng erasure của **leftmost bound** đối với type variable. Với:

```java
class Envelope<T extends Number & Comparable<T>> {
    T number;
}
```

Bạn có thể quan sát hai lớp thông tin:

```java
java.lang.reflect.Field f =
        Envelope.class.getDeclaredField("number");

System.out.println(f.getType().getTypeName());
System.out.println(f.getGenericType().getTypeName());
```

Đầu ra:

```text
java.lang.Number
T
```

- `Field.getType()` trả `Class` của kiểu erasure: `Number`.
- `Field.getGenericType()` giữ mô tả khai báo bằng `TypeVariable`: `T`.

Đây là ranh giới quan trọng: **giữ được generic signature qua reflection không có nghĩa một instance `Envelope<Integer>` sẽ có một runtime field class riêng là `Integer`**. Runtime class thông thường vẫn dùng erasure `Number`.

Với method:

```java
<T extends CharSequence & Comparable<T>> T echo(T value)
```

`Method.getReturnType()` có thể trả `CharSequence.class`; `Method.getGenericReturnType()` trả `TypeVariable` tên `T`. Tương tự, `getParameterTypes()` dùng erasure, còn `getGenericParameterTypes()` có thể biểu diễn `T`.

**Không được đồng nhất bound với actual type argument:** `CharSequence` là upper bound, không có nghĩa mọi lời gọi `echo` trả chính một object thuộc runtime class `CharSequence` (đây là interface).

## 8. `getAnnotatedBounds()` khác annotation trên chính type parameter

Java hỗ trợ đặt annotation ở hai vị trí có ý nghĩa khác nhau:

```java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_PARAMETER)
@interface NamedParameter {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface NumberBound {}

class Box<@NamedParameter T extends @NumberBound Number> {}
```

`@NamedParameter` đặt **trên khai báo type parameter `T`**. `@NumberBound` đặt **trên type use `Number` làm bound**.

Reflection:

```java
java.lang.reflect.TypeVariable<?> variable =
        Box.class.getTypeParameters()[0];

boolean onParameter =
        variable.isAnnotationPresent(NamedParameter.class);

boolean onFirstBound =
        variable.getAnnotatedBounds()[0]
                .isAnnotationPresent(NumberBound.class);
```

Cả hai là `true` với ví dụ trên.

Phân biệt:

- `TypeVariable.getAnnotations()` và các phương thức từ `AnnotatedElement` nói về annotations của **khai báo type parameter**.
- `getAnnotatedBounds()` trả `AnnotatedType[]` có annotations của **các kiểu đang được dùng làm bounds**.
- `getBounds()` trả `Type[]` không chứa API đọc trực tiếp annotation cho từng vị trí type use.

Để phản chiếu được annotation lúc chạy, cần `RetentionPolicy.RUNTIME` thích hợp. Nếu annotation chỉ tồn tại ở nguồn hoặc class file mà không có runtime retention, không nên kỳ vọng các lời gọi reflection như trên sẽ thấy nó.

### Annotated bound không thay thế bound

Một `AnnotatedType` cung cấp thêm metadata annotation ở vị trí kiểu, nhưng bạn vẫn có thể gọi `getType()` để lấy `Type` nền. Chẳng hạn:

```java
java.lang.reflect.AnnotatedType annotated =
        variable.getAnnotatedBounds()[0];

java.lang.reflect.Type underlying = annotated.getType();
```

`underlying` có thể là `Number.class`. Khi xây schema hoặc framework cần annotation, hãy giữ đồng thời **cấu trúc `Type`** và **annotation ở đúng vị trí**.

## 9. Chương trình Java 17+ hoàn chỉnh: class, method, constructor và bounds

Lưu mã dưới đây vào **`TypeVariableDemo.java`**:

```java
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

public class TypeVariableDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_PARAMETER)
    @interface NamedParameter {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface NumberBound {}

    static class Envelope<
            @NamedParameter T extends @NumberBound Number & Comparable<T>,
            E> {
        T number;
        List<T> numbers;
        E other;

        public <U extends CharSequence> Envelope(U label) {}

        // T của echo được khai báo bởi method, không phải Envelope.
        public <T extends CharSequence & Comparable<T>> T echo(T value) {
            return value;
        }
    }

    static class IntegerEnvelope extends Envelope<Integer, String> {
        IntegerEnvelope() {
            super("ok");
        }
    }

    static String bounds(TypeVariable<?> variable) {
        return String.join(
                " & ",
                Arrays.stream(variable.getBounds())
                        .map(Type::getTypeName)
                        .toArray(String[]::new));
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        TypeVariable<?> classT =
                Envelope.class.getTypeParameters()[0];
        TypeVariable<?> classE =
                Envelope.class.getTypeParameters()[1];

        // Lookup reflection theo erased parameter type CharSequence.
        Method method = Envelope.class.getDeclaredMethod(
                "echo", CharSequence.class);
        TypeVariable<?> methodT = method.getTypeParameters()[0];

        Constructor<?> ctor = Envelope.class.getDeclaredConstructor(
                CharSequence.class);
        TypeVariable<?> ctorU = ctor.getTypeParameters()[0];

        System.out.println("Class T belongs to Envelope: "
                + classT.getGenericDeclaration().equals(Envelope.class));
        System.out.println("Class T bounds: " + bounds(classT));
        System.out.println("Class T annotation: "
                + classT.isAnnotationPresent(NamedParameter.class));
        System.out.println("First bound annotation: "
                + classT.getAnnotatedBounds()[0]
                        .isAnnotationPresent(NumberBound.class));

        System.out.println("Class E implicit bound: "
                + bounds(classE));

        System.out.println("Method T belongs to echo: "
                + methodT.getGenericDeclaration().equals(method));
        System.out.println("Method T bounds: " + bounds(methodT));
        System.out.println("Same type variable name: "
                + classT.getName().equals(methodT.getName()));
        System.out.println("Same type variable identity: "
                + classT.equals(methodT));

        System.out.println("Constructor U belongs to ctor: "
                + ctorU.getGenericDeclaration().equals(ctor));
        System.out.println("Constructor U bound: " + bounds(ctorU));

        Field field = Envelope.class.getDeclaredField("number");
        System.out.println("Field erased type: "
                + field.getType().getTypeName());
        System.out.println("Field generic type: "
                + field.getGenericType().getTypeName());

        ParameterizedType list = (ParameterizedType)
                Envelope.class.getDeclaredField("numbers")
                        .getGenericType();
        System.out.println("List argument is class T: "
                + list.getActualTypeArguments()[0].equals(classT));

        ParameterizedType parent = (ParameterizedType)
                IntegerEnvelope.class.getGenericSuperclass();
        System.out.println("Subclass argument T: "
                + parent.getActualTypeArguments()[0].getTypeName());
        System.out.println("Subclass argument E: "
                + parent.getActualTypeArguments()[1].getTypeName());

        System.out.println("Method erased return: "
                + method.getReturnType().getTypeName());
        System.out.println("Method generic return: "
                + method.getGenericReturnType().getTypeName());
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 TypeVariableDemo.java
java TypeVariableDemo
```

**Standard output:**

```text
Class T belongs to Envelope: true
Class T bounds: java.lang.Number & java.lang.Comparable<T>
Class T annotation: true
First bound annotation: true
Class E implicit bound: java.lang.Object
Method T belongs to echo: true
Method T bounds: java.lang.CharSequence & java.lang.Comparable<T>
Same type variable name: true
Same type variable identity: false
Constructor U belongs to ctor: true
Constructor U bound: java.lang.CharSequence
Field erased type: java.lang.Number
Field generic type: T
List argument is class T: true
Subclass argument T: java.lang.Integer
Subclass argument E: java.lang.String
Method erased return: java.lang.CharSequence
Method generic return: T
```

Giải thích các nhóm kết quả:

1. **Class-level `T`** thuộc `Envelope.class`, có hai upper bounds `Number` và `Comparable<T>`; bound đầu tiên có annotation riêng.
2. **Class-level `E`** không có `extends` tường minh, nhưng `getBounds()` vẫn phản ánh upper bound ngầm `Object`.
3. **Method-level `T`** thuộc `echo`; nó có cùng tên với class-level `T`, nhưng là biến kiểu **khác khai báo** nên `equals` cho `false`.
4. **Constructor-level `U`** thuộc constructor, có upper bound `CharSequence`. `getDeclaredConstructor(CharSequence.class)` dựa trên erasure của tham số `U`.
5. **Field `T number`** có erased type `Number`, nhưng generic type là `T`. Trường `List<T> numbers` có type argument phản chiếu trỏ đúng tới class-level `T`.
6. **Subclass `IntegerEnvelope`** khai báo rõ `extends Envelope<Integer, String>`, nên `getGenericSuperclass()` cho `ParameterizedType` với hai actual type arguments cụ thể. Điều đó **không thay đổi** `TypeVariable` được khai báo trên `Envelope`.
7. **Return type method `echo`** có erasure `CharSequence`, còn generic return `T` là type variable của chính method.

Chương trình sử dụng các API có sẵn trong Java 17 và không cần thư viện ngoài.

## 10. Khi subclass chọn kiểu cụ thể: không được nhầm `TypeVariable` với actual type argument

Xét:

```java
class Base<T> {}
class Child extends Base<String> {}
```

Có hai nguồn thông tin **khác nhau**:

```java
java.lang.reflect.TypeVariable<?> parameter =
        Base.class.getTypeParameters()[0];

java.lang.reflect.Type actual =
        ((java.lang.reflect.ParameterizedType)
                Child.class.getGenericSuperclass())
                .getActualTypeArguments()[0];
```

- `parameter` là **`T` do `Base` khai báo**. `parameter.getGenericDeclaration()` chỉ về `Base`.
- `actual` là **`String` do `Child` sử dụng khi kế thừa `Base<String>`**. Trong ví dụ này nó là `Class` tương ứng `String.class`.

Nếu framework muốn biết type argument nào đã gắn với `T` trong bối cảnh `Child`, nó phải **ánh xạ biến kiểu khai báo của `Base` sang type argument ở cạnh kế thừa**. `TypeVariable` riêng lẻ không giữ một đáp án `String` duy nhất cho mọi nơi dùng `Base`.

### Nhiều tầng thừa kế cần giải type bindings

```java
class Base<T> {}
class Middle<U> extends Base<java.util.List<U>> {}
class Child extends Middle<String> {}
```

Muốn suy ra type argument của `Base<T>` từ `Child`, một công cụ phải theo chuỗi:

```text
Child
  extends Middle<String>
      U -> String
  Middle<U> extends Base<List<U>>
      T -> List<U>
  => trong ngữ cảnh Child: T -> List<String>
```

Không thể chỉ đọc `Base.class.getTypeParameters()[0].getBounds()` rồi nhận ngay `List<String>`. `getBounds()` chỉ cho ràng buộc trên của `T`, không phải **actual type argument do một subtype lựa chọn**.

Thư viện như ORM, serializer và dependency-injection framework thường cần giải những ánh xạ này để suy luận schema hoặc kiểu property trong ngữ cảnh kế thừa.

## 11. Giới hạn của `TypeVariable` và type erasure

Một số kết luận cần ghi nhớ:

**Không khôi phục type argument từ instance thông thường.**

```java
class Box<T> {}

Box<String> a = new Box<>();
Box<Integer> b = new Box<>();

boolean same = a.getClass() == b.getClass(); // true
```

Cả hai instance có cùng runtime class `Box`. Dù `Box.class.getTypeParameters()` cho biến `T`, nó không thể tự trả lời “instance `a` đã chọn `String` còn instance `b` đã chọn `Integer`”.

**Không có `T.class` hoặc `new T[]` thông thường.**

```java
class Holder<T> {
    void inspect(Object value) {
        // Class<?> type = T.class;        // KHÔNG BIÊN DỊCH
        // boolean ok = value instanceof T; // KHÔNG BIÊN DỊCH
        // T[] items = new T[3];          // KHÔNG BIÊN DỊCH
    }
}
```

Đây là giới hạn của non-reifiable type variables, không được giải quyết bằng cách phản chiếu `T` thành `TypeVariable`.

**Bound chỉ cho một giới hạn trên.** Với `T extends Number`, việc một object là `Number` không chứng minh nó là một subtype cụ thể `T` mà caller kỳ vọng. Một cast theo erasure có thể thành công với `Double` trong khi caller đang mong `Integer`, khiến lỗi lộ ra sau đó.

**Generic signature là metadata khai báo.** Reflection giữ được `T` hoặc `Comparable<T>` ở khai báo, nhưng không duyệt dữ liệu và không xác thực mọi phần tử của collection generic ở runtime.

Nếu cần kiểm tra một class cụ thể được caller cung cấp, có thể nhận `Class<T>` token:

```java
static <T> boolean accepts(Class<T> expected, Object value) {
    return expected.isInstance(value);
}
```

Token giúp kiểm tra một runtime class cụ thể, nhưng không mang đầy đủ nested generic type arguments kiểu `Map<String, List<Integer>>` theo cơ chế `Class` đơn lẻ.

## 12. `TypeVariable` so với `WildcardType`: hai ý nghĩa khác nhau

Dễ nhầm `T extends Number` với `? extends Number` vì cả hai có chữ `extends`.

```java
class Example<T extends Number> {
    T value;
    java.util.List<? extends Number> readings;
}
```

Chúng khác nhau:

- **`T extends Number`** là một **type parameter declaration**, đặt tên `T` để nhiều vị trí trong cùng generic declaration có thể liên hệ tới nhau. Reflection biểu diễn `T` bằng `TypeVariable`.
- **`? extends Number`** là **wildcard type argument** ở vị trí sử dụng một kiểu tham số hóa. Reflection biểu diễn wildcard đó bằng `WildcardType`.

Trường `value` có `getGenericType()` là `TypeVariable`. Trường `readings` có `getGenericType()` là `ParameterizedType` của `List<? extends Number>`, và phần tử `getActualTypeArguments()[0]` mới là `WildcardType`.

| Câu hỏi | `TypeVariable` | `WildcardType` |
| --- | --- | --- |
| Có tên do programmer khai báo? | Có, như `T`, `U` | Không có tên type parameter |
| Có `getGenericDeclaration()`? | Có | Không |
| Có `getBounds()`? | Có, upper bounds | Dùng `getUpperBounds()` và `getLowerBounds()` |
| Có thể kết nối nhiều vị trí bằng cùng tên/khai báo? | Có, cùng một type variable | Wildcard là đối số kiểu tại vị trí sử dụng |
| Ví dụ | `<T extends Number>` | `List<? extends Number>` |

Không dùng `((TypeVariable<?>) type)` trên bất cứ thứ gì có `extends`. Phải kiểm tra dạng `Type` thực tế.

## 13. Không phải `Type[]` nào từ reflection cũng chứa `Class`

Mã sai phổ biến:

```java
java.lang.reflect.TypeVariable<?> t =
        SomeClass.class.getTypeParameters()[0];

for (java.lang.reflect.Type bound : t.getBounds()) {
    // Class<?> boundClass = (Class<?>) bound; // KHÔNG AN TOÀN
}
```

Với `T extends Number & Comparable<T>`, bound `Number` là `Class`, nhưng `Comparable<T>` là `ParameterizedType`.

Nếu chỉ muốn in tên:

```java
for (java.lang.reflect.Type bound : t.getBounds()) {
    System.out.println(bound.getTypeName());
}
```

Nếu cần phân tích cấu trúc, nhánh theo từng dạng:

```java
static void inspectBound(java.lang.reflect.Type bound) {
    if (bound instanceof Class<?> c) {
        System.out.println("Class bound: " + c.getName());
    } else if (bound instanceof java.lang.reflect.ParameterizedType p) {
        System.out.println("Parameterized bound: "
                + p.getRawType().getTypeName());
    } else if (bound instanceof java.lang.reflect.TypeVariable<?> v) {
        System.out.println("Variable bound: " + v.getName());
    } else {
        System.out.println("Other bound: " + bound.getTypeName());
    }
}
```

Khi gặp cấu trúc đệ quy như `T extends Comparable<T>`, một trình duyệt type graph cần **phát hiện vòng lặp**: nếu cứ theo type argument `T` rồi đọc `getBounds()` của `T` mà không ghi nhận node đã thăm, thuật toán có thể lặp mãi. Dùng tập node/declaration đã thăm theo semantics `TypeVariable.equals`, không dựa vào tên `"T"` đơn thuần.

## 14. Cẩn trọng khi xây framework xử lý generic type variables

Một schema resolver hoặc serializer dùng reflection thường phải giải bài toán lớn hơn việc in tên `T`:

1. **Đọc khai báo:** lấy `TypeVariable` từ `Class.getTypeParameters()`, `Method.getTypeParameters()` hoặc `Constructor.getTypeParameters()`.
2. **Duy trì scope:** xác định `getGenericDeclaration()` cho từng biến kiểu. Hai biến tên `T` ở hai declarations không thể tự trộn vào một.
3. **Đọc bounds như cấu trúc `Type`:** không mặc định tất cả bounds là `Class`; chúng có thể có parameterized arguments.
4. **Resolve qua inheritance hoặc method context:** lấy actual type arguments từ `ParameterizedType` của superclass/interface/owner khi có, rồi áp dụng các ánh xạ `TypeVariable -> Type`.
5. **Giữ annotations:** nếu schema cần `@TYPE_PARAMETER` và `@TYPE_USE` annotations, đọc đúng API và vị trí.
6. **Phát hiện vòng lặp:** ví dụ `Comparable<T>` tham chiếu lại chính `T`.
7. **Xử lý metadata không sẵn có:** `Type` được phản chiếu có thể chỉ còn type variable hoặc raw type; không tự bịa type argument cụ thể cho object runtime.
8. **Tách khai báo khỏi dữ liệu:** một field khai báo `List<String>` không chứng minh collection hiện tại chứa toàn `String` nếu hệ thống có raw/unchecked operations.

Các lời gọi reflection chuyên sâu có thể ném những exception như `TypeNotPresentException` hoặc `MalformedParameterizedTypeException` khi phải phân tích type metadata không giải được/hỏng. Code framework nên có chiến lược xử lý lỗi, không mặc định chữ ký generic phản chiếu từ mọi class ngoài luôn hoàn hảo.

## 15. Các lỗi thường gặp

**Lỗi 1 — “`TypeVariable` chính là runtime class đã chọn cho `T`.”** Sai. Nó mô tả biến kiểu ở nơi khai báo, không mang riêng type argument của mọi instance.

**Lỗi 2 — “`getBounds()` trả actual type arguments.”** Sai. `Number` là upper bound, không phải `Integer` do caller đã chọn.

**Lỗi 3 — “Không ghi `extends` thì `getBounds()` rỗng.”** Sai. Upper bound ngầm thông thường là `Object`.

**Lỗi 4 — “Hai type variables tên `T` luôn bằng nhau.”** Sai. Class-level `T` và method-level `T` có generic declarations khác nhau.

**Lỗi 5 — “`getGenericDeclaration()` luôn trả `Class`.”** Sai. Method và constructor cũng có thể khai báo type parameters.

**Lỗi 6 — “Cứ dùng `==` trên `Method` trả bởi reflection là đủ.”** Không nên. So sánh declaration wrappers bằng `.equals()` khi cần đối chiếu logic.

**Lỗi 7 — “Bounds lúc nào cũng là `Class`.”** Sai. `Comparable<T>` là parameterized bound, thường được mô tả qua `ParameterizedType`.

**Lỗi 8 — “Erasure của `T` luôn là `Object`.”** Sai. Nó theo erasure của bound đầu tiên; `T extends Number` có erasure `Number`.

**Lỗi 9 — “`T extends Number & Comparable<T>` tạo một runtime class giao hai interface.”** Sai. Reflection giữ hai bounds, không sinh runtime class mang tên intersection.

**Lỗi 10 — “`getAnnotatedBounds()` và `TypeVariable.getAnnotations()` xem cùng vị trí.”** Sai. Một cái đọc type-use annotations ở bounds, cái kia đọc annotations trên chính type parameter declaration.

**Lỗi 11 — “Có `TypeVariable` thì dùng `T.class` hoặc `new T[]` được.”** Sai. `T` vẫn là non-reifiable type variable theo ngôn ngữ Java.

**Lỗi 12 — “`T extends Comparable<T>` có thể đệ quy duyệt vô hạn mà không cần visited set.”** Sai. Bounds có thể trỏ trở lại type variable ban đầu; cần guard khi duyệt type graph.

## 16. Bài tập tự kiểm tra

**Câu 1.** Trong `class Box<T extends Number>`, ai là generic declaration của `T`?

Chính **`Box.class`**. `getGenericDeclaration()` trả declaration ở class, không phải một `Box<Integer>` instance.

**Câu 2.** Với `<T> T identity(T x)`, nơi khai báo `T` là gì?

Đối tượng **`Method`** của `identity`. `getBounds()` có upper bound ngầm `Object` nếu không ghi `extends`.

**Câu 3.** Constructor có thể có type parameter riêng không?

Có. Ví dụ `<U extends CharSequence> Box(U text)`; phản chiếu bằng `Constructor.getTypeParameters()`.

**Câu 4.** `T extends Number & Comparable<T>` có bao nhiêu upper bounds?

Hai: `Number` và `Comparable<T>`, theo thứ tự; erasure dùng bound đầu tiên `Number`.

**Câu 5.** `T` của class và `T` của method cùng tên có phải cùng type variable?

Không. Cần xét `getGenericDeclaration()` và semantics `TypeVariable.equals`, không chỉ `getName()`.

**Câu 6.** Vì sao `Field.getType()` của field `T value` trong `Box<T extends Number>` là `Number.class`?

Vì `getType()` cung cấp góc nhìn erasure/runtime của field, và erasure của `T` theo upper bound đầu tiên là `Number`. `getGenericType()` vẫn có thể trả `TypeVariable` `T`.

**Câu 7.** `getBounds()` và `getAnnotatedBounds()` khác nhau thế nào?

`getBounds()` trả cấu trúc `Type[]`; `getAnnotatedBounds()` trả `AnnotatedType[]` để xem type-use annotations ở các bounds.

**Câu 8.** Nếu `class Child extends Base<String>`, `Base.class.getTypeParameters()` có trả `String` không?

Không. Nó trả biến kiểu do `Base` khai báo. `Child.class.getGenericSuperclass()` mới mô tả việc `Child` gắn `String` vào generic superclass.

**Câu 9.** Muốn dùng `TypeVariable` làm key khi resolve generic, có nên chỉ dùng tên `"T"`?

Không. Cần giữ identity của declaration và type variable; tên trùng ở nhiều scope không có nghĩa cùng biến kiểu.

**Câu 10.** Vì sao parser Type phải phát hiện vòng lặp khi xử lý `T extends Comparable<T>`?

Vì bound tham chiếu lại chính `T`; nếu liên tục khai triển mà không ghi nhận node đã thăm, traversal có thể không kết thúc.

## 17. Checklist áp dụng nhanh

Khi đọc một `TypeVariable<?> variable`, hãy theo trình tự:

```text
TypeVariable<?> variable
    |
    +-- getName()                    -> Tên hiển thị của type parameter
    |
    +-- getGenericDeclaration()      -> Class / Method / Constructor khai báo
    |
    +-- getBounds()                  -> Danh sách Type upper bounds
    |     +-- Class?
    |     +-- ParameterizedType?     -> Có thể chứa chính T
    |
    +-- getAnnotatedBounds()         -> Annotation ở bound type uses
    |
    +-- getAnnotations()             -> Annotation ở parameter declaration
    |
    +-- Có actual type arguments?    -> Tìm ở generic superclass/interface
          và ánh xạ theo ngữ cảnh, không tự suy ra từ bounds
```

**Kết luận:** `TypeVariable` không đại diện cho một class runtime mới mà đại diện cho **tham số kiểu được khai báo**. Để hiểu nó chính xác, phải xét **nơi khai báo** qua `getGenericDeclaration()`, **tên** qua `getName()`, **upper bounds** qua `getBounds()`, và **type-use annotations** qua `getAnnotatedBounds()`. Hai biến cùng tên vẫn khác nhau nếu được khai báo ở hai scope. Bounds ràng buộc các type arguments hợp lệ và ảnh hưởng erasure, nhưng không thay thế actual type arguments do class/method/constructor ở một ngữ cảnh cụ thể sử dụng. Đây là nền tảng quan trọng để xử lý reflection, serialization, schema resolution và Java Generics một cách đáng tin.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-reflection-006">WildcardType và GenericArrayType</a>

<details>
<summary>Xem chi tiết</summary>

# WildcardType và GenericArrayType trong Java Reflection

## 1. Bức tranh tổng quan

Java Generics cho phép khai báo những kiểu không chỉ có một tên class đơn giản, ví dụ:

```java
List<?> any;
List<? extends Number> readableNumbers;
List<? super Integer> writableIntegers;
List<String>[] groups;
T[] elements;
```

Đây là các **khai báo minh họa**; cần import `java.util.List`, đặt vào class tương ứng và khai báo `T` là type parameter. Trong công cụ serialization, dependency injection, mapping, validation hoặc code generator, ta có thể cần đọc lại chính xác cấu trúc kiểu đã khai báo.

Hai interface thuộc `java.lang.reflect` giải quyết những phần khác nhau của bài toán đó:

- **`WildcardType`** mô tả type argument dùng wildcard: `?`, `? extends Number`, `? super Integer`.
- **`GenericArrayType`** mô tả kiểu mảng có component type là generic type variable hoặc parameterized type: `T[]`, `List<String>[]`, `List<? extends Number>[]`.

Cả hai kế thừa `Type`. Chúng là **mô tả kiểu trong metadata reflection**, không phải runtime class đặc biệt được JVM sinh riêng cho mỗi generic argument.

Điểm phải nắm trước khi đọc ví dụ:

> `Field.getGenericType()` có thể mô tả `List<String>[]` nhưng một runtime array object tương ứng chỉ kiểm tra component raw `List` khi lưu phần tử. Reflection metadata **không xóa bỏ type erasure** và không tự kiểm tra nội dung một collection.

Bài trình bày API, upper/lower bounds, generic component types, sự kết hợp nhiều cấp, ví dụ Java 17 chạy được, lỗi thường gặp và cách xây type walker an toàn.

## 2. Hệ sinh thái `Type`: đừng cast mọi thứ thành `Class<?>`

`java.lang.reflect.Type` không đồng nghĩa `java.lang.Class`. Khi một field hoặc method mang generic declaration, reflection có thể trả nhiều dạng `Type`.

| Kiểu reflection | Khi nào xuất hiện điển hình | API cần biết |
|---|---|---|
| `Class<?>` | `String`, `Number`, `String[]`, `int` | `getTypeName`, `isArray`, `getComponentType` |
| `ParameterizedType` | `List<String>`, `Map<String,Integer>` | `getRawType`, `getActualTypeArguments`, `getOwnerType` |
| `TypeVariable<?>` | `T` trong `class Store<T>` | `getName`, `getBounds`, `getGenericDeclaration` |
| `WildcardType` | `?`, `? super Integer` | `getUpperBounds`, `getLowerBounds` |
| `GenericArrayType` | `T[]`, `List<String>[]` | `getGenericComponentType` |

Ví dụ cấu trúc:

```text
Map<String, List<? extends Number>[]>
└── ParameterizedType Map<..., ...>
    ├── Class<String>
    └── GenericArrayType
        └── ParameterizedType List<...>
            └── WildcardType ? extends Number
                └── upper bound: Class<Number>
```

Sơ đồ không mô tả nội dung của một object `Map` đang chạy. Đây là **cây kiểu được khai báo**. Một parser phải dùng `instanceof` để chọn nhánh thích hợp thay vì ép thẳng toàn bộ cây thành `Class<?>`.

## 3. `WildcardType`: upper bounds và lower bounds

Interface `WildcardType` cung cấp:

```java
Type[] getUpperBounds();
Type[] getLowerBounds();
```

Đây là chữ ký minh họa API; `Type` cần được import từ `java.lang.reflect`.

Giá trị thường gặp:

| Cú pháp wildcard | Upper bounds | Lower bounds |
|---|---|---|
| `?` | `Object` | rỗng |
| `? extends Number` | `Number` | rỗng |
| `? super Integer` | `Object` | `Integer` |
| `? extends Comparable<String>` | `Comparable<String>` (`ParameterizedType`) | rỗng |

Vì sao `? super Integer` vẫn có upper bound `Object`? Mọi kiểu tham chiếu hợp lệ đều bị giới hạn trên bởi `Object`; còn `Integer` đóng vai trò **lower bound**.

Vì sao `?` không tương đương `Object` dù upper bound của nó là Object? `List<?>` biểu diễn một element type **chưa biết**; không được tùy tiện thêm `"x"` hay `123`. `List<Object>` lại có element type **xác định là `Object`**, nên các thao tác thêm đối tượng phù hợp là hợp lệ.

Bounds của reflection có kiểu `Type[]`, không nhất thiết `Class<?>[]`. Một bound có thể tiếp tục là parameterized type hoặc cấu trúc reflection khác.

## 4. Ví dụ đầy đủ: đọc ba loại wildcard từ field

Tạo tệp **`WildcardBoundsDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WildcardBoundsDemo {
    static class Fields {
        List<?> any;
        List<? extends Number> upper;
        List<? super Integer> lower;
    }

    static String display(Type[] types) {
        return Arrays.stream(types)
                .map(Type::getTypeName)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    static void show(String fieldName) throws Exception {
        Field field = Fields.class.getDeclaredField(fieldName);
        ParameterizedType list = (ParameterizedType) field.getGenericType();
        WildcardType w = (WildcardType) list.getActualTypeArguments()[0];

        System.out.println(fieldName
                + ": upper=" + display(w.getUpperBounds())
                + ", lower=" + display(w.getLowerBounds()));
    }

    public static void main(String[] args) throws Exception {
        show("any");
        show("upper");
        show("lower");
    }
}
```

Chạy trên JDK 17:

```bash
javac WildcardBoundsDemo.java
java WildcardBoundsDemo
```

Stdout:

```text
any: upper=[java.lang.Object], lower=[]
upper: upper=[java.lang.Number], lower=[]
lower: upper=[java.lang.Object], lower=[java.lang.Integer]
```

Các bước phản chiếu:

1. `getDeclaredField(...)` lấy **declaration** của field.
2. `getGenericType()` trả `ParameterizedType` vì field dùng `List<...>`.
3. `getActualTypeArguments()[0]` trả phần `?`.
4. `WildcardType` cho phép đọc upper/lower bounds.
5. `Type.getTypeName()` dùng để hiển thị tên kiểu.

Các cast trong chương trình hợp lệ vì đã **biết trước cấu trúc** các field demo. Với input tùy ý, phải kiểm tra `instanceof` và cả số lượng type arguments trước khi truy cập.

## 5. Upper bound có thể là `ParameterizedType`

Đừng giả định `getUpperBounds()[0]` là một `Class<?>`.

Ví dụ:

```java
List<? extends Comparable<String>> values;
```

`Comparable<String>` không phải một runtime class chuyên biệt; nó là parameterized type. Vì vậy upper bound là `ParameterizedType`.

Tệp **`ParameterizedWildcardDemo.java`**:

```java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;

public class ParameterizedWildcardDemo {
    static class Data {
        List<? extends Comparable<String>> values;
    }

    public static void main(String[] args) throws Exception {
        ParameterizedType list = (ParameterizedType)
                Data.class.getDeclaredField("values").getGenericType();

        WildcardType wildcard = (WildcardType)
                list.getActualTypeArguments()[0];

        Type upper = wildcard.getUpperBounds()[0];
        System.out.println(upper instanceof ParameterizedType);
        System.out.println(upper.getTypeName());
    }
}
```

Chạy:

```bash
javac ParameterizedWildcardDemo.java
java ParameterizedWildcardDemo
```

Stdout:

```text
true
java.lang.Comparable<java.lang.String>
```

Nếu code cố `Class<?> clazz = (Class<?>) upper;`, nó sẽ ném `ClassCastException`. Điều đúng là duyệt tiếp `ParameterizedType`: raw type `Comparable`, actual argument `String`.

## 6. Type variable `T` không phải wildcard `?`

Xét generic class:

```java
class Store<T extends Number> {
    List<T> values;
    List<? extends Number> view;
}
```

Đây là class minh họa, cần import `List` nếu dùng đơn lẻ.

- `T` là **type parameter có tên**, được khai báo bởi `Store<T extends Number>`.
- `? extends Number` là **wildcard vô danh** ở vị trí type argument.
- Reflection của argument trong `List<T>` trả `TypeVariable<?>`.
- Reflection của argument trong `List<? extends Number>` trả `WildcardType`.

`TypeVariable` có `getName()`, `getBounds()`, `getGenericDeclaration()`; `WildcardType` có `getUpperBounds()` và `getLowerBounds()`.

Tệp **`WildcardVsVariableDemo.java`**:

```java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;

public class WildcardVsVariableDemo {
    static class Store<T extends Number> {
        List<T> values;
        List<? extends Number> view;
    }

    static Type argument(String fieldName) throws Exception {
        ParameterizedType list = (ParameterizedType)
                Store.class.getDeclaredField(fieldName).getGenericType();
        return list.getActualTypeArguments()[0];
    }

    public static void main(String[] args) throws Exception {
        Type a = argument("values");
        Type b = argument("view");
        System.out.println(a instanceof TypeVariable<?>);
        System.out.println(b instanceof WildcardType);

        TypeVariable<?> t = (TypeVariable<?>) a;
        System.out.println(t.getName());
        System.out.println(t.getBounds()[0].getTypeName());
    }
}
```

Chạy:

```bash
javac WildcardVsVariableDemo.java
java WildcardVsVariableDemo
```

Stdout:

```text
true
true
T
java.lang.Number
```

Nhìn bounds thấy đều liên quan `Number` **không có nghĩa hai kiểu reflection có cùng bản chất**. `T` có thể xuất hiện nhiều lần trong cùng khai báo và buộc các vị trí liên hệ với nhau; từng wildcard đại diện một kiểu chưa biết thích hợp với giới hạn.

## 7. PECS ở mã nguồn và những gì reflection thực sự biết

Khi lập trình, wildcard bounds có tác dụng trong kiểm tra kiểu tĩnh:

```java
static double total(List<? extends Number> values) {
    double sum = 0;
    for (Number n : values) {
        sum += n.doubleValue();
    }
    return sum;
}

static void appendDefaults(List<? super Integer> target) {
    target.add(10);
    target.add(20);
}
```

Đây là các method minh họa; đặt trong class và import `List`.

- `? extends Number` là **producer**: đọc phần tử như `Number`; không được tùy tiện thêm một `Double` vì list thực có thể là `List<Integer>`.
- `? super Integer` là **consumer**: có thể thêm `Integer`; đọc phần tử chỉ chắc chắn là `Object`.
- `?` không cho phép tự ý thêm phần tử non-null có loại chưa biết.

Reflection chỉ cho biết các bounds đã khai báo; nó không thay compiler quyết định những thao tác được phép trên một variable kiểu generic, và không tự duyệt list để chứng minh dữ liệu phù hợp.

## 8. Wildcard lồng trong `Map` rồi `List`

Một field có kiểu:

```java
Map<String, List<? super Integer>> mapping;
```

gồm nhiều lớp:

```text
ParameterizedType Map
  Class<String>
  ParameterizedType List
    WildcardType ? super Integer
      lower bound Integer
      upper bound Object
```

Tệp **`NestedWildcardDemo.java`**:

```java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Map;

public class NestedWildcardDemo {
    static class Fields {
        Map<String, List<? super Integer>> mapping;
    }

    public static void main(String[] args) throws Exception {
        ParameterizedType map = (ParameterizedType)
                Fields.class.getDeclaredField("mapping").getGenericType();

        Type valueArgument = map.getActualTypeArguments()[1];
        ParameterizedType list = (ParameterizedType) valueArgument;

        WildcardType wildcard = (WildcardType)
                list.getActualTypeArguments()[0];

        System.out.println(map.getRawType().getTypeName());
        System.out.println(list.getRawType().getTypeName());
        System.out.println(wildcard.getTypeName());
        System.out.println(wildcard.getLowerBounds()[0].getTypeName());
    }
}
```

Lệnh:

```bash
javac NestedWildcardDemo.java
java NestedWildcardDemo
```

Stdout:

```text
java.util.Map
java.util.List
? super java.lang.Integer
java.lang.Integer
```

Đừng chỉ dựa vào `Map.class` hoặc `List.class`: các raw class tokens đó không giữ các nested type arguments. Với reflection metadata đã khai báo, lần lượt dùng `getActualTypeArguments()` để đi sâu.

## 9. `GenericArrayType` mô tả array declaration với generic component

`GenericArrayType` là interface của `Type` và có phương thức chính:

```java
Type getGenericComponentType();
```

Nó xuất hiện trong các kiểu như:

```java
class Examples<T> {
    T[] elements;
    T[][] grid;
    List<String>[] names;
    List<? extends Number>[] numericGroups;
}
```

Các **field declarations** trên được phép, giả sử import `List`. Chúng không phải thao tác tạo mảng.

- Với `T[]`, generic component là `TypeVariable` `T`.
- Với `List<String>[]`, component là `ParameterizedType` `List<String>`.
- Với `T[][]`, component của outer `GenericArrayType` lại là một `GenericArrayType` khác cho `T[]`.
- Với `List<? extends Number>[]`, component `List<...>` có một nested `WildcardType`.

`GenericArrayType` là **type metadata**, không phải một instance của mảng, không phải mảng có thể `get(index)`, và không có phương thức tạo array object. Khi muốn runtime array component class, xem `Class<?>` của object mảng thực tế hoặc dùng API tạo mảng phù hợp.

## 10. So sánh với mảng có runtime `Class<?>`

Một object `String[]` thực có runtime array class. Reflection có thể trả một `Class<?>` đại diện cho `String[]`:

```java
Class<?> clazz = String[].class;

System.out.println(clazz.isArray());
System.out.println(clazz.getComponentType().getTypeName());
```

Nếu nằm trong method, stdout là:

```text
true
java.lang.String
```

Từ `String[]`, JVM biết runtime component `String` và có thể kiểm tra một store qua `Object[]`.

Trái lại, khai báo `List<String>[]` trong class file có thể được `Field.getGenericType()` thể hiện bằng `GenericArrayType` vì phần `List<String>` vẫn là metadata generic; object array thực ở runtime không có specialized class `List<String>[]`.

Bảng phân biệt:

| Câu hỏi | Reflection API |
|---|---|
| Kiểu runtime của một mảng đang chạy? | `array.getClass()` |
| Component class runtime? | `array.getClass().getComponentType()` |
| Một field khai báo kiểu mảng generic nào? | `field.getGenericType()` |
| Generic component của declaration? | `GenericArrayType.getGenericComponentType()` |
| Một method trả `T[]` hay `Number[]` sau erasure? | `getGenericReturnType()` so với `getReturnType()` |

## 11. Ví dụ phản chiếu nhiều loại array field

Tệp **`GenericArrayFieldsDemo.java`**:

```java
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.List;

public class GenericArrayFieldsDemo {
    static class Fields<T> {
        String[] plain;
        List<String>[] stringLists;
        List<?>[] unknownLists;
        T[] values;
        T[][] grid;
    }

    static void inspect(String name) throws Exception {
        Type type = Fields.class.getDeclaredField(name).getGenericType();
        System.out.println(name + ": "
                + (type instanceof GenericArrayType)
                + " | " + type.getTypeName());
    }

    public static void main(String[] args) throws Exception {
        inspect("plain");
        inspect("stringLists");
        inspect("unknownLists");
        inspect("values");
        inspect("grid");
    }
}
```

Chạy:

```bash
javac GenericArrayFieldsDemo.java
java GenericArrayFieldsDemo
```

Stdout:

```text
plain: false | java.lang.String[]
stringLists: true | java.util.List<java.lang.String>[]
unknownLists: true | java.util.List<?>[]
values: true | T[]
grid: true | T[][]
```

Giải thích:

- `String[]` phản chiếu như một `Class` array.
- `List<String>[]` mang parameterized component, phản chiếu như `GenericArrayType`.
- `List<?>[]` có generic declaration component nên reflection giữ cấu trúc `List<?>`, dù kiểu component `List<?>` là **reifiable** theo JLS.
- `T[]` và `T[][]` chứa type variable, nên dùng `GenericArrayType`.

Không nên tự kết luận “hễ là `GenericArrayType` thì mảng không bao giờ được tạo” — reifiability và cấu trúc generic declaration là hai khía cạnh khác nhau.

## 12. Đọc các component trong mảng nhiều chiều

Tệp **`GenericArrayComponentDemo.java`**:

```java
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;

public class GenericArrayComponentDemo {
    static class Data<T> {
        T[][] grid;
        List<String>[] groups;
    }

    public static void main(String[] args) throws Exception {
        Type grid = Data.class.getDeclaredField("grid")
                .getGenericType();

        GenericArrayType outer = (GenericArrayType) grid;
        GenericArrayType inner = (GenericArrayType)
                outer.getGenericComponentType();
        Type element = inner.getGenericComponentType();

        System.out.println(element instanceof TypeVariable<?>);
        System.out.println(element.getTypeName());

        Type groups = Data.class.getDeclaredField("groups")
                .getGenericType();
        Type component = ((GenericArrayType) groups)
                .getGenericComponentType();

        System.out.println(component instanceof ParameterizedType);
        System.out.println(component.getTypeName());
    }
}
```

Lệnh:

```bash
javac GenericArrayComponentDemo.java
java GenericArrayComponentDemo
```

Stdout:

```text
true
T
true
java.util.List<java.lang.String>
```

Với `T[][]`, ta phải gọi `getGenericComponentType()` **hai lần** để đến `T`. Với `List<String>[]`, chỉ một lần là tới `ParameterizedType`.

Nếu framework xử lý mảng đa chiều, đừng lấy `((Class<?>) type).getComponentType()` ngay trên một `GenericArrayType`: đó là hai mô hình API khác nhau.

## 13. Khai báo `List<String>[]` được, nhưng `new List<String>[n]` bị cấm

Một bẫy thường gặp:

```java
List<String>[] groups; // Khai báo hợp lệ.
```

nhưng:

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
List<String>[] groups = new List<String>[2];
```

Biểu thức tạo mảng yêu cầu **component type reifiable**. `List<String>` không reifiable vì type argument `String` không được biểu diễn đầy đủ để JVM array-store check.

Tương tự:

```java
// CỐ Ý KHÔNG BIÊN DỊCH:
new List<? extends Number>[2];
new List<Integer>[2];
new T[2];
```

Ngược lại:

```java
List<?>[] groups = new List<?>[2];
```

là hợp lệ vì `List<?>` dùng wildcard không giới hạn và là reifiable type theo quy tắc của Java. Runtime component class vẫn là raw `List`.

**Reflection đọc được một kiểu declaration không có nghĩa compiler cho phép tạo một runtime array object bằng chính biểu thức `new` với kiểu đó.**

## 14. Ví dụ runtime component class thực tế

Tệp **`RuntimeArrayClassDemo.java`**:

```java
import java.util.List;

public class RuntimeArrayClassDemo {
    public static void main(String[] args) {
        String[] words = new String[1];
        List<?>[] lists = new List<?>[2];

        System.out.println(words.getClass()
                .getComponentType().getTypeName());
        System.out.println(lists.getClass()
                .getComponentType().getTypeName());
        System.out.println(lists instanceof Object[]);
        System.out.println(lists.getClass().isArray());
    }
}
```

Chạy:

```bash
javac RuntimeArrayClassDemo.java
java RuntimeArrayClassDemo
```

Stdout:

```text
java.lang.String
java.util.List
true
true
```

JVM kiểm tra store theo runtime component class `String` hoặc `List`. Nó không có component class riêng cho `List<String>` hay `List<Integer>`.

`Class.getComponentType()` chỉ dùng cho runtime `Class` của mảng, còn `GenericArrayType.getGenericComponentType()` chỉ dùng cho một generic array declaration được reflection mô tả.

## 15. Generic array xuất hiện ở method, không chỉ field

Một generic method có thể nhận và trả `T[]`.

Tệp **`GenericMethodArrayDemo.java`**:

```java
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Type;

public class GenericMethodArrayDemo {
    static <T extends Number> T[] identity(T[] input) {
        return input;
    }

    public static void main(String[] args) throws Exception {
        Method method = GenericMethodArrayDemo.class
                .getDeclaredMethod("identity", Number[].class);

        Type parameter = method.getGenericParameterTypes()[0];
        Type result = method.getGenericReturnType();

        System.out.println(parameter instanceof GenericArrayType);
        System.out.println(parameter.getTypeName());
        System.out.println(result instanceof GenericArrayType);
        System.out.println(result.getTypeName());
        System.out.println(method.getReturnType().getTypeName());
    }
}
```

Biên dịch/chạy:

```bash
javac GenericMethodArrayDemo.java
java GenericMethodArrayDemo
```

Stdout:

```text
true
T[]
true
T[]
java.lang.Number[]
```

Tại sao tra method bằng `Number[].class` thay vì `Object[].class`? `T` có upper bound đầu tiên `Number`, nên erasure của `T` là `Number`; của `T[]` là `Number[]`.

`getGenericParameterTypes()` và `getGenericReturnType()` đọc declaration generic. `getParameterTypes()` và `getReturnType()` phản ánh class runtime sau erasure.

## 16. Trường hợp chứa cả wildcard và generic array

Xét:

```java
List<? extends Number>[] groups;
```

Cây reflection:

```text
GenericArrayType
  ParameterizedType List<...>
    WildcardType ? extends Number
      upper bound Class Number
```

Tệp **`WildcardArrayReflectionDemo.java`**:

```java
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;

public class WildcardArrayReflectionDemo {
    static class Fields {
        List<? extends Number>[] groups;
    }

    public static void main(String[] args) throws Exception {
        Type declared = Fields.class.getDeclaredField("groups")
                .getGenericType();
        GenericArrayType array = (GenericArrayType) declared;

        ParameterizedType list = (ParameterizedType)
                array.getGenericComponentType();

        WildcardType wildcard = (WildcardType)
                list.getActualTypeArguments()[0];

        System.out.println(array.getTypeName());
        System.out.println(list.getRawType().getTypeName());
        System.out.println(wildcard.getTypeName());
        System.out.println(wildcard.getUpperBounds()[0].getTypeName());
    }
}
```

Chạy:

```bash
javac WildcardArrayReflectionDemo.java
java WildcardArrayReflectionDemo
```

Stdout:

```text
java.util.List<? extends java.lang.Number>[]
java.util.List
? extends java.lang.Number
java.lang.Number
```

Điều quan trọng: phản chiếu được các phần này **không làm** `new List<? extends Number>[2]` hợp lệ. Với code cần nhóm số, `List<List<? extends Number>>` có thể là thiết kế thích hợp hơn.

## 17. Đọc một `Type` bất kỳ cần xử lý năm nhánh

Đối với một thư viện xử lý schema hoặc generic API, có thể viết một bộ duyệt `Type`:

```text
Nếu Type là Class<?>:
    đọc tên, kiểm tra isArray() nếu cần
Nếu Type là ParameterizedType:
    đọc raw type, owner type, actual arguments
Nếu Type là WildcardType:
    đọc upper/lower bounds
Nếu Type là TypeVariable<?>:
    đọc tên, generic declaration, bounds có kiểm soát
Nếu Type là GenericArrayType:
    đọc generic component type
```

Cần hiểu hai nguy cơ:

- **Kiểu lồng nhau nhiều cấp**: không thể dừng ngay khi gặp một `ParameterizedType` hoặc `GenericArrayType`.
- **Bound tự tham chiếu**: ví dụ `T extends Comparable<T>` chứa lại `T` ở bên trong bound. Nếu walker cứ mở rộng bounds không giới hạn, nó có thể đệ quy vô hạn.

Một walker đơn giản có thể **in tên type variable nhưng không mở rộng bounds**. Một công cụ hoàn chỉnh cần tập đã thăm để ngắt chu kỳ.

## 18. Chương trình type walker tổng quát

Tệp **`TypeTreeWalkerDemo.java`**:

```java
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Map;

public class TypeTreeWalkerDemo {
    static class Fields {
        Map<String, List<? extends Number>[]> data;
    }

    static void walk(Type type, int level) {
        String pad = "  ".repeat(level);

        if (type instanceof ParameterizedType p) {
            System.out.println(pad + "Parameterized: "
                    + p.getRawType().getTypeName());
            for (Type argument : p.getActualTypeArguments()) {
                walk(argument, level + 1);
            }
        } else if (type instanceof GenericArrayType array) {
            System.out.println(pad + "GenericArray");
            walk(array.getGenericComponentType(), level + 1);
        } else if (type instanceof WildcardType wildcard) {
            System.out.println(pad + "Wildcard");
            for (Type upper : wildcard.getUpperBounds()) {
                System.out.println(pad + "  Upper:");
                walk(upper, level + 2);
            }
            for (Type lower : wildcard.getLowerBounds()) {
                System.out.println(pad + "  Lower:");
                walk(lower, level + 2);
            }
        } else if (type instanceof TypeVariable<?> variable) {
            System.out.println(pad + "Variable: "
                    + variable.getName());
        } else if (type instanceof Class<?> clazz) {
            System.out.println(pad + "Class: "
                    + clazz.getTypeName());
        } else {
            System.out.println(pad + "Other: "
                    + type.getTypeName());
        }
    }

    public static void main(String[] args) throws Exception {
        Type declared = Fields.class.getDeclaredField("data")
                .getGenericType();
        walk(declared, 0);
    }
}
```

Biên dịch/chạy:

```bash
javac TypeTreeWalkerDemo.java
java TypeTreeWalkerDemo
```

Stdout:

```text
Parameterized: java.util.Map
  Class: java.lang.String
  GenericArray
    Parameterized: java.util.List
      Wildcard
        Upper:
          Class: java.lang.Number
```

Đây là một walker minh họa đủ năm nhánh, không có vòng lặp do không mở rộng `TypeVariable` bounds. Framework lớn còn cần kiểm tra owner types của inner classes, quy ước schema, xử lý thiếu class và tối ưu caching.

Không nên dùng `Type.getTypeName()` rồi tách chuỗi bằng dấu phẩy hoặc dấu `<` như thể đó là một ngôn ngữ schema đơn giản. Với nested types, hãy duyệt **cấu trúc `Type`**.

## 19. Một mảng `List<?>[]` reifiable vẫn có generic declaration

Đây là trường hợp giúp phân biệt chính xác **reifiability** và **reflection object type**.

Java cho phép tạo:

```java
List<?>[] values = new List<?>[3];
```

Runtime component class là `java.util.List`, nên `values.getClass().getComponentType()` trả token cho `List`.

Trong một field khai báo `List<?>[]`, reflection có thể giữ lại tham số hóa `List<?>` và trả `GenericArrayType` có component là `ParameterizedType`.

Vậy:

- **Reifiable:** Java có đủ thông tin runtime để kiểm tra một object lưu vào array có là `List` hay không.
- **Parameterized generic declaration:** Reflection vẫn lưu được phần `<?>` ở source declaration, nên cấu trúc kiểu có thể là `GenericArrayType`.

Không đánh đồng: “Hễ `GenericArrayType` là không thể tạo mảng”, hoặc “Hễ mảng tạo được thì `Field.getGenericType()` phải là `Class`”.

## 20. Vì sao metadata không loại bỏ heap pollution?

Giả sử một tham chiếu được nhìn thành `List<String>[]` qua unchecked conversion, nhưng runtime array component chỉ là raw `List`.

Khi có một alias:

```java
Object[] alias = someArray;
```

thao tác lưu một `List<Integer>` vào slot vẫn có thể vượt qua runtime array store check, vì đó là object implements `List`. Sau đó, đọc giá trị qua `List<String>[]` và cast phần tử thành `String` mới phát hiện lỗi.

`GenericArrayType` có thể cho biết một **field khai báo `List<String>[]`**, nhưng JVM không dùng generic declaration metadata đó để kiểm tra từng type argument trong array store.

Đây là một động cơ của quy tắc cấm trực tiếp tạo `new List<String>[n]`.

## 21. Ví dụ kiểm chứng: heap pollution và ngoại lệ muộn

Tệp **`GenericArrayPollutionDemo.java`**:

```java
import java.util.List;

public class GenericArrayPollutionDemo {

    @SuppressWarnings("unchecked")
    static List<String>[] unsafeArray() {
        return (List<String>[]) new List<?>[1];
    }

    public static void main(String[] args) {
        List<String>[] groups = unsafeArray();
        groups[0] = List.of("safe");

        Object[] alias = groups;
        alias[0] = List.of(123);

        System.out.println(groups.getClass()
                .getComponentType().getName());

        try {
            String value = groups[0].get(0);
            System.out.println(value);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch/chạy:

```bash
javac GenericArrayPollutionDemo.java
java GenericArrayPollutionDemo
```

Stdout:

```text
java.util.List
ClassCastException
```

Giải thích:

1. `new List<?>[1]` hợp lệ.
2. Cast sang `List<String>[]` không được compiler kiểm tra đầy đủ.
3. `Object[] alias` trỏ đến cùng object array.
4. `List.of(123)` phù hợp runtime component `List`, nên không gây `ArrayStoreException`.
5. `get(0)` đọc một `Integer` trong ngữ cảnh cần `String`, nên gây `ClassCastException`.

Đoạn `unsafeArray` **cố ý không an toàn** để giải thích cơ chế, không phải factory nên tái sử dụng. `@SuppressWarnings` chỉ tắt warning, không thêm runtime generic checks.

## 22. Giữ một kiểu generic array phức tạp bằng `TypeRef`

Đôi khi không có field hoặc method cụ thể cho framework đọc `Type`. Một anonymous subclass có thể giữ một generic superclass declaration.

Tệp **`GenericTypeReferenceDemo.java`**:

```java
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

public class GenericTypeReferenceDemo {

    static abstract class TypeRef<T> {
        final Type type;

        protected TypeRef() {
            Type parent = getClass().getGenericSuperclass();
            type = ((ParameterizedType) parent)
                    .getActualTypeArguments()[0];
        }
    }

    public static void main(String[] args) {
        TypeRef<List<? super Integer>[]> ref =
                new TypeRef<List<? super Integer>[]>() {};

        System.out.println(ref.type.getTypeName());
        System.out.println(ref.type instanceof GenericArrayType);
    }
}
```

Chạy:

```bash
javac GenericTypeReferenceDemo.java
java GenericTypeReferenceDemo
```

Stdout:

```text
java.util.List<? super java.lang.Integer>[]
true
```

Anonymous subclass ghi `TypeRef<List<? super Integer>[]>` trong generic superclass metadata; reflection có thể đọc lại một cây gồm `GenericArrayType`, `ParameterizedType`, `WildcardType`.

Điều này không phải một runtime object array chứa sẵn type argument `Integer`. Nếu thư viện muốn dùng metadata để validate hoặc decode, nó cần một bộ xử lý dữ liệu riêng.

## 23. Wildcard có thể ở tham số phương thức

Một method nhận `List<? super Integer>` giữ wildcard trong generic parameter declaration.

Tệp **`WildcardMethodDemo.java`**:

```java
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.util.List;

public class WildcardMethodDemo {
    static void receive(List<? super Integer> values) {
        values.add(10);
    }

    public static void main(String[] args) throws Exception {
        Method method = WildcardMethodDemo.class
                .getDeclaredMethod("receive", List.class);

        ParameterizedType parameter = (ParameterizedType)
                method.getGenericParameterTypes()[0];

        WildcardType wildcard = (WildcardType)
                parameter.getActualTypeArguments()[0];

        System.out.println(method.getParameterTypes()[0].getTypeName());
        System.out.println(wildcard.getTypeName());
        System.out.println(
                wildcard.getLowerBounds()[0].getTypeName());
    }
}
```

Biên dịch/chạy:

```bash
javac WildcardMethodDemo.java
java WildcardMethodDemo
```

Stdout:

```text
java.util.List
? super java.lang.Integer
java.lang.Integer
```

`getDeclaredMethod` dùng runtime signature với `List.class` sau erasure. `getGenericParameterTypes()` mới trả phần `List<? super Integer>` được khai báo.

Nếu method nhận generic array, `getGenericParameterTypes()` có thể tiếp tục trả `GenericArrayType`.

## 24. `getType` so với `getGenericType`

Hai API trên `Field` trả hai lớp thông tin khác nhau:

- `field.getType()`: `Class<?>` phản ánh runtime/erased type.
- `field.getGenericType()`: `Type` phản ánh generic type của field declaration khi metadata còn sẵn.

Tệp **`GenericVsErasedArrayDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.util.List;

public class GenericVsErasedArrayDemo {
    static class Fields {
        List<String>[] groups;
    }

    public static void main(String[] args) throws Exception {
        Field field = Fields.class.getDeclaredField("groups");

        System.out.println(field.getType().getTypeName());
        System.out.println(field.getGenericType().getTypeName());
        System.out.println(
                field.getGenericType() instanceof GenericArrayType);
    }
}
```

Chạy:

```bash
javac GenericVsErasedArrayDemo.java
java GenericVsErasedArrayDemo
```

Stdout:

```text
java.util.List[]
java.util.List<java.lang.String>[]
true
```

`getType()` chỉ biết mảng component `List`. `getGenericType()` mô tả component `List<String>`. Không có mâu thuẫn: hai method phục vụ hai nhu cầu phản chiếu khác nhau.

Tương ứng, method reflection có `getReturnType()` so với `getGenericReturnType()`, và `getParameterTypes()` so với `getGenericParameterTypes()`.

## 25. Metadata `Signature` và ranh giới runtime

Compiler Java có thể lưu generic declaration trong **`Signature` attribute** của class file. Reflection dùng thông tin này để trả `ParameterizedType`, `WildcardType`, `TypeVariable` hoặc `GenericArrayType` ở những declaration phù hợp.

Nhưng không được hiểu rằng:

- Mọi object runtime đều mang một `Signature` ghi rõ type arguments tại nơi `new`.
- `Field.getGenericType()` tự kiểm tra nội dung đang nằm trong field.
- `Class.getComponentType()` kiểm tra được `List<String>` khác `List<Integer>`.
- Một chuỗi trả bởi `Type.getTypeName()` đủ để thực hiện deserialization an toàn.

Khi thông tin generic được tạo từ class files thiếu hoặc hỏng metadata, reflection cũng có thể phát sinh những ngoại lệ/lỗi theo contract API. Framework thực tế cần xử lý các trường hợp như thiếu type được tham chiếu, chữ ký không phù hợp, hoặc recursive type bounds.

Nếu kiểm tra classfile bằng `javap -v`, có thể so sánh JVM descriptor sau erasure với thuộc tính `Signature`. **Signature là metadata declaration, không phải một runtime enforcement mechanism** cho generic element types.

## 26. Những sai lầm phổ biến với `WildcardType`

**Sai lầm 1:** `List<?>` giống `List<Object>`. Không: wildcard có element type chưa biết, còn Object là một argument cụ thể.

**Sai lầm 2:** `? super Integer` có upper bound `Integer`. Không: `Integer` là lower bound; upper bound mặc định `Object`.

**Sai lầm 3:** `getLowerBounds()` luôn có một phần tử. Không: nó rỗng đối với `?` và `? extends Number`.

**Sai lầm 4:** Mọi upper bound đều là `Class<?>`. Không: `Comparable<String>` là `ParameterizedType`.

**Sai lầm 5:** `WildcardType` có `getName()` như `TypeVariable`. Không: wildcard không phải type parameter có tên.

**Sai lầm 6:** Mọi generic argument trả về từ `getActualTypeArguments()` đều có thể cast sang `WildcardType`. Không: chúng có thể là `Class`, `TypeVariable`, `ParameterizedType` hoặc dạng khác.

**Sai lầm 7:** Reflection bounds tự quyết định toàn bộ thao tác ghi vào generic list. Không: quy tắc compiler và hợp đồng API vẫn có vai trò chính.

**Sai lầm 8:** Đọc được wildcard nghĩa là đã xác minh mọi phần tử runtime của một collection. Không: metadata khai báo khác với validation dữ liệu.

## 27. Những sai lầm phổ biến với `GenericArrayType`

**Sai lầm 1:** Mọi kiểu mảng từ reflection là `GenericArrayType`. Không: `String[]` được biểu diễn bằng `Class<?>`.

**Sai lầm 2:** Có thể cast `GenericArrayType` thành `Class<?>` để tạo mảng. Không: chúng là những biểu diễn `Type` khác nhau.

**Sai lầm 3:** Component của `GenericArrayType` luôn là `Class<?>`. Không: có thể là `TypeVariable`, `ParameterizedType` hoặc nested `GenericArrayType`.

**Sai lầm 4:** `T[][]` chỉ có một lớp generic array. Không: phải đi qua hai component levels.

**Sai lầm 5:** Có `GenericArrayType` thì có thể `new T[n]`. Không: array creation theo cú pháp Java đòi component type reifiable.

**Sai lầm 6:** `List<?>[]` không bao giờ tạo được. Không: `new List<?>[2]` hợp lệ.

**Sai lầm 7:** Runtime array component `List` bảo vệ được type argument `String`. Không: nó chỉ kiểm tra object thuộc raw `List`.

**Sai lầm 8:** `Array.newInstance(List.class, n)` tạo một mảng có specialized component `List<String>`. Không: nó tạo mảng raw `List[]`.

**Sai lầm 9:** Unchecked cast `(List<String>[]) ...` làm array trở nên an toàn. Không: có thể tạo heap pollution qua alias kiểu rộng.

## 28. Bài tập có đáp án

### Bài 1 — Wildcard không bound

`List<?>` có upper và lower bounds nào trong reflection?

**Đáp án:** Upper chứa `Object`, lower rỗng.

### Bài 2 — Upper/lower

`List<? super Integer>` lấy `Integer` bằng method nào?

**Đáp án:** `WildcardType.getLowerBounds()[0]`.

### Bài 3 — Bound parameterized

`List<? extends Comparable<String>>` có upper bound thuộc `Class<?>` không?

**Đáp án:** Không; đây là `ParameterizedType` của `Comparable<String>`.

### Bài 4 — Type variable

`List<T>` trong `class Box<T>` có argument reflection loại gì?

**Đáp án:** `TypeVariable<?>`, không phải `WildcardType`.

### Bài 5 — Array declaration

`List<String>[] values;` hợp lệ còn `new List<String>[5]` hợp lệ không?

**Đáp án:** Khai báo hợp lệ; array creation bị cấm vì component non-reifiable.

### Bài 6 — Array of wildcard

Có thể viết `new List<?>[5]` không?

**Đáp án:** Có. Runtime component class là raw `List`.

### Bài 7 — String array

`String[]` nhận được từ `Field.getGenericType()` có dạng nào?

**Đáp án:** `Class<?>` array type trong trường hợp khai báo đó, không phải `GenericArrayType`.

### Bài 8 — Multi-dimensional

Với `T[][]`, cần bao nhiêu bước `getGenericComponentType()` để đến `T`?

**Đáp án:** Hai bước.

### Bài 9 — Kết hợp

`List<? extends Number>[]` phân rã thành những kiểu nào?

**Đáp án:** `GenericArrayType` → `ParameterizedType` → `WildcardType` → upper bound `Class` Number.

### Bài 10 — Method reflection

Với `<T extends Number> T[] identity(T[] input)`, vì sao tra parameter bằng `Number[].class`?

**Đáp án:** Erasure của `T` là `Number`, vì upper bound đầu tiên là `Number`.

### Bài 11 — Metadata và object

Đọc được `List<String>[]` từ field có chứng minh array runtime chỉ chứa các list của String không?

**Đáp án:** Không. Generic metadata không tự áp dụng runtime validation cho content.

### Bài 12 — Type walker

Vì sao `T extends Comparable<T>` có thể gây vòng lặp khi mở rộng bounds?

**Đáp án:** Bound `Comparable<T>` chứa lại chính type variable `T`.

### Bài 13 — Exception

Ghi `List<Integer>` qua một alias `Object[]` đến array runtime `List[]` có thể thành công không?

**Đáp án:** Có, vì runtime store check chỉ thấy raw `List`; việc dùng như `List<String>` về sau mới có thể thất bại.

### Bài 14 — `getTypeName`

Có nên parse `Type.getTypeName()` bằng các phép tách chuỗi đơn giản để xây type validator không?

**Đáp án:** Không. Nên duyệt cây `Type` để giữ các nested arguments, bounds, owner types và arrays.

## 29. Checklist thực hành

1. Xác định declaration cần đọc: field, method parameter, return type, superclass hoặc interface.
2. Dùng API **generic reflection** thay vì chỉ lấy runtime raw `Class`.
3. Kiểm tra `instanceof` trước khi cast `Type`.
4. Với `ParameterizedType`, đọc `getRawType`, `getActualTypeArguments`, và owner type khi cần.
5. Với `WildcardType`, đọc **cả** upper và lower bounds.
6. Đừng đồng nhất unbounded `?` với `Object` chính xác.
7. Với `GenericArrayType`, duyệt `getGenericComponentType` từng cấp.
8. Xét mảng nhiều chiều và generic components lồng nhau.
9. Phân biệt `TypeVariable<?>` và `WildcardType`.
10. Chặn đệ quy vô hạn với self-referential type-variable bounds.
11. Không xem metadata reflection là chứng minh các phần tử runtime đáp ứng generic argument.
12. Không dùng unchecked casts để hợp thức hóa `new List<String>[]`.
13. Khi cần mảng runtime, xét component type reifiable và các array factory hợp lệ.
14. Khi cần lưu nhiều danh sách, cân nhắc `List<List<String>>` thay cho generic arrays.
15. Viết test cho các cây gồm `?`, `? super`, `? extends`, `T[]`, `List<?>[]`, `T[][]` và nested `Map`.
16. Khi xử lý dữ liệu chưa tin cậy, tách bước đọc `Type` khỏi bước parse/validate.

## 30. Tổng kết và tài liệu chuẩn

**`WildcardType`** là đối tượng reflection đại diện wildcard type argument và cung cấp `getUpperBounds()` cùng `getLowerBounds()`. **`GenericArrayType`** mô tả mảng có generic component như `T[]` hoặc `List<String>[]`, cung cấp `getGenericComponentType()`.

Hai loại `Type` này thường nằm bên trong các cây khai báo phức tạp cùng `ParameterizedType`, `TypeVariable` và `Class`. Chúng là công cụ quan trọng khi viết framework xử lý generic signatures.

Quan trọng nhất là phân biệt **thông tin generic ở declaration** với **runtime class của object**. Một `Field` có thể khai báo `List<String>[]` và được reflection mô tả đầy đủ, nhưng array store check vẫn không biết type argument `String` của từng list. Java dùng type erasure; khả năng mô tả không đồng nghĩa khả năng xác minh hoặc tạo object.

Đọc cây `Type` có cấu trúc, không parse chuỗi tên kiểu; kiểm tra `instanceof` trước cast; xử lý bounds và các cấp mảng; bảo toàn an toàn kiểu khi tạo hoặc chuyển đổi dữ liệu.

Tài liệu đối chiếu:

- [Java SE 17 — `WildcardType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/WildcardType.html): API upper/lower bounds.
- [Java SE 17 — `GenericArrayType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/GenericArrayType.html): API generic component type.
- [Java SE 17 — `Type`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Type.html): interface chung.
- [Java SE 17 — `ParameterizedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html): raw type và actual arguments.
- [Java SE 17 — `TypeVariable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/TypeVariable.html): type variables và bounds.
- [JLS §4.5 — Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5): wildcard type arguments.
- [JLS §4.7 — Reifiable Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7): reifiable generic types.
- [JLS §10 — Arrays](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html): mảng và kiểm tra runtime.
- [JLS §15.10.1 — Array Creation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.10.1): quy tắc array creation.
- [JVMS §4.7.9 — Signature Attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.9): generic declaration metadata.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-reflection-007">Đọc getGenericSuperclass, getGenericInterfaces và chữ ký thành viên</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc getGenericSuperclass, getGenericInterfaces và chữ ký thành viên trong Java Reflection

## 1. Mục tiêu: quan sát **khai báo kiểu** thay vì đoán kiểu runtime

Khi tìm hiểu Java Generics bằng Reflection, có ba nhóm câu hỏi thường gặp:

1. Một class **extends** generic superclass với type arguments nào?
2. Một class **implements** generic interfaces nào và dùng type arguments nào?
3. Một field, method, constructor hoặc type parameter **được khai báo** với kiểu generic nào?

Các API chính:

~~~java
Class<?> clazz = SomeClass.class;

Type parent = clazz.getGenericSuperclass();
Type[] directInterfaces = clazz.getGenericInterfaces();

Type fieldType = field.getGenericType();
Type returnType = method.getGenericReturnType();
Type[] parameters = method.getGenericParameterTypes();
Type[] thrown = method.getGenericExceptionTypes();
~~~

Các dòng trên là **minh họa API**: `SomeClass`, `field` và `method` phải được cung cấp từ chương trình cụ thể. Ví dụ Java 17 hoàn chỉnh, có thể chạy ngay, nằm ở phần 9.

Điều đặc biệt quan trọng: Reflection về generics chủ yếu đọc **metadata chữ ký kiểu lưu tại class file**. Nó **không** có nghĩa JVM giữ một phiên bản runtime khác nhau của `List<String>` và `List<Integer>`, và **không** tự xác minh kiểu từng phần tử trong một collection.

Chủ đề này là một phần của **phản chiếu kiểu** (*generic type reflection*), khác với việc đọc object data thông thường. Phải phân biệt:

- **`Class<?>`**: mô tả runtime class hoặc interface.
- **`java.lang.reflect.Type`**: mô tả một dạng kiểu trong hệ thống Reflection, có thể chứa type arguments, wildcard, type variable hoặc generic array.
- **`Field` / `Method` / `Constructor<?>`**: đối tượng Reflection biểu diễn khai báo thành viên, từ đó có thể đọc chữ ký kiểu tổng quát.

## 2. Khung ví dụ xuyên suốt

Giả sử một class con gắn `T = String` cho superclass generic và triển khai một interface generic:

~~~java
interface Source<K, V> {
    V get(K key);
}

class Parent<T> {
    public T item;
    public List<T> items;
    public T[] buffer;
    public List<? extends Number> upper;

    public <E extends Exception> List<? super T> transform(
            List<? extends T> input, E problem) throws E {
        throw problem;
    }
}

final class Child extends Parent<String>
        implements Source<String, List<Integer>>, Serializable {

    @Override
    public List<Integer> get(String key) {
        return List.of(key.length());
    }
}
~~~

Mảnh ví dụ giả định có import `java.io.Serializable` và `java.util.List`; trong chương trình độc lập ở phần 9, chúng sẽ là các nested types.

Các lớp chứa nhiều tầng thông tin generic:

- `Parent<T>` khai báo một **class type variable** `T`.
- `Child extends Parent<String>` cung cấp **type argument String** ở chữ ký superclass.
- `Child implements Source<String,List<Integer>>` cung cấp **hai type arguments**, trong đó type argument thứ hai chính là một parameterized type lồng nhau.
- `Serializable` là non-generic interface, nên không có type arguments.
- Trường `item` có kiểu `T`, `items` có `List<T>`, `buffer` có `T[]`, `upper` có wildcard `? extends Number`.
- `transform` có một **method type variable** `E extends Exception`, một tham số `List<? extends T>` và kết quả `List<? super T>`.

Không có một API duy nhất trả luôn “sơ đồ kiểu đã giải hết mọi biến” cho toàn bộ các khai báo này. Ta phải đọc đúng thành phần của `Type`, đồng thời hiểu ngữ cảnh khai báo gốc.

## 3. Class.getSuperclass và getGenericSuperclass khác nhau ra sao?

Hai API đều liên quan đến **superclass trực tiếp**, nhưng kiểu trả về và lượng thông tin rất khác.

~~~java
Class<?> erased = Child.class.getSuperclass();
Type generic = Child.class.getGenericSuperclass();
~~~

Với `Child extends Parent<String>`:

- `getSuperclass()` trả một **`Class<?>` biểu diễn Parent**. Nó không biểu diễn type argument String.
- `getGenericSuperclass()` trả một **`Type`**, cụ thể thường là một `ParameterizedType` mô tả `Parent<String>`.

Đọc type arguments một cách có kiểm tra:

~~~java
Type type = Child.class.getGenericSuperclass();

if (type instanceof ParameterizedType parameterized) {
    Type raw = parameterized.getRawType();
    Type[] arguments = parameterized.getActualTypeArguments();

    System.out.println(((Class<?>) raw).getSimpleName()); // Parent
    System.out.println(arguments[0].getTypeName());       // java.lang.String
}
~~~

**Không cast mù quáng** kiểu trả về sang ParameterizedType. Nếu class extends một non-generic superclass hay dùng raw parent type, kết quả có thể là `Class<?>`; nếu không có superclass theo trường hợp API quy định, nó có thể là `null`.

Một bảng hữu ích:

| Khai báo class | `getSuperclass()` | `getGenericSuperclass()` |
| --- | --- | --- |
| `class A extends Parent<String>` | Class của Parent | ParameterizedType của Parent<String> |
| `class B extends Parent` (raw) | Class của Parent | Có thể là Class của raw Parent |
| `class C extends Object` | Class của Object | Class của Object |
| `java.lang.Object` | null | null |
| Interface type | Không có class superclass theo cách class có | Thường null cho `getGenericSuperclass()` |

Các ngoại lệ như `TypeNotPresentException` hoặc `MalformedParameterizedTypeException` có thể xuất hiện khi generic signature chứa type không tải được hoặc thông tin bất hợp lệ; cần xem API contract nếu xử lý bytecode lạ hoặc class không đầy đủ.

### 3.1. getGenericSuperclass chỉ xét **quan hệ extends trực tiếp**

Nếu `Grandchild extends Child` thì đọc trực tiếp `Grandchild.class.getGenericSuperclass()` sẽ mô tả Child, không tự đi hết cây tổ tiên đến `Parent<String>`.

Muốn lấy generic arguments của một ancestor nhiều cấp, cần duyệt các generic superclass edges và thay thế những type variables theo từng cấp. Đây là một bài toán giải kiểu khác, không phải tính năng tự động của riêng một lệnh `getGenericSuperclass()`.

## 4. Class.getInterfaces và getGenericInterfaces

Tương tự với các **interfaces được khai báo trực tiếp**:

~~~java
Class<?>[] erased = Child.class.getInterfaces();
Type[] generic = Child.class.getGenericInterfaces();
~~~

Với:

~~~java
final class Child extends Parent<String>
        implements Source<String, List<Integer>>, Serializable {
    // ...
}
~~~

`getInterfaces()` trả các `Class<?>` của `Source` và `Serializable`, không chứa type arguments.

`getGenericInterfaces()` trả một mảng `Type[]` có thể bao gồm:
- `ParameterizedType` cho `Source<String,List<Integer>>`;
- `Class<?>` cho interface không generic `Serializable`.

Vì vậy:

~~~java
Type[] interfaces = Child.class.getGenericInterfaces();

for (Type type : interfaces) {
    System.out.println(type.getTypeName());
}
~~~

Có thể quan sát interface generic đi kèm các type arguments, còn marker interface Serializable được biểu diễn như một class/interface token thông thường.

**Quan trọng:** đây là các **interfaces trực tiếp được class hoặc interface khai báo**, không phải mọi interface nó kế thừa gián tiếp. Để dựng tất cả generic interfaces trên toàn bộ type hierarchy, cần duyệt superclass/interfaces thích hợp và giải type variables khi có kế thừa nhiều cấp.

### 4.1. Bóc tách interface có type argument lồng nhau

~~~java
Type[] interfaces = Child.class.getGenericInterfaces();

if (interfaces[0] instanceof ParameterizedType sourceType) {
    Type[] arguments = sourceType.getActualTypeArguments();

    Type keyType = arguments[0];    // String.
    Type valueType = arguments[1];  // List<Integer>.

    System.out.println(keyType.getTypeName());
    System.out.println(valueType.getTypeName());
}
~~~

Đầu ra theo `getTypeName()` có thể là:

~~~text
java.lang.String
java.util.List<java.lang.Integer>
~~~

Với `arguments[1]`, không nên ép thẳng thành `Class<List>`. `List<Integer>` là một **ParameterizedType**, vì nó có raw type List và actual type argument Integer.

Có thể tiếp tục bóc lớp:

~~~java
ParameterizedType listType = (ParameterizedType) valueType;

Type listRawType = listType.getRawType(); // Class của List.
Type itemType = listType.getActualTypeArguments()[0]; // Integer.
~~~

Không phải mọi generic argument đều là `Class<?>`. Type argument có thể chính là một `TypeVariable<?>`, một `WildcardType`, một `ParameterizedType` khác hoặc một `GenericArrayType`.

## 5. Hệ thống Type: phải kiểm tra dạng trước khi cast

`java.lang.reflect.Type` là abstraction chung. Một cách phân loại thực dụng:

| Kiểu Reflection | Ví dụ khai báo | Dữ liệu cần đọc |
| --- | --- | --- |
| `Class<?>` | `String`, `Integer`, raw `List` | Tên class, superclass, interfaces, thành viên |
| `ParameterizedType` | `List<String>`, `Map<String,List<Integer>>` | `getRawType`, `getActualTypeArguments`, `getOwnerType` |
| `TypeVariable<?>` | `T` trong Parent<T>, `E` của method generic | `getName`, `getBounds`, `getGenericDeclaration` |
| `WildcardType` | `?`, `? extends Number`, `? super T` | `getUpperBounds`, `getLowerBounds` |
| `GenericArrayType` | `T[]`, `List<String>[]` trong chữ ký generic | `getGenericComponentType` |

Đây là các nhánh *kiểu của mô tả Type*, không phải các loại object runtime tương ứng với mọi generic instantiation.

Một visitor đơn giản:

~~~java
static void describe(Type type) {
    if (type instanceof Class<?> clazz) {
        System.out.println("Class: " + clazz.getName());
    } else if (type instanceof ParameterizedType p) {
        System.out.println("Parameterized: " + p.getTypeName());
    } else if (type instanceof TypeVariable<?> v) {
        System.out.println("Variable: " + v.getName());
    } else if (type instanceof WildcardType w) {
        System.out.println("Wildcard: " + w.getTypeName());
    } else if (type instanceof GenericArrayType a) {
        System.out.println("Array: " + a.getTypeName());
    }
}
~~~

Đoạn trên là minh họa helper và cần `import java.lang.reflect.*;`. Nếu muốn in cây lồng nhau, visitor cần **đệ quy** vào raw/owner/arguments/bounds/component types, đồng thời tránh vòng lặp do self-bounded generics như `T extends Comparable<T>`.

### 5.1. Không dựa vào tên class cài đặt nội bộ của Type

Một `ParameterizedType` có thể được JDK thể hiện qua class nội bộ, nhưng tên class cài đặt này không phải public contract phù hợp để so sánh. Hãy dùng:

~~~java
type instanceof ParameterizedType
~~~

và các phương thức của interface chuẩn. Đừng dùng `type.getClass().getSimpleName()` rồi giả định kết quả luôn giống nhau giữa các JDK.

## 6. ParameterizedType: raw type, actual arguments, owner

Ví dụ một generic type:

~~~java
Map<String, List<Integer>>
~~~

có:
- raw type `Map`;
- argument thứ nhất `String`;
- argument thứ hai `List<Integer>`, bản thân là ParameterizedType.

Với:

~~~java
ParameterizedType p = (ParameterizedType) someType;

Type raw = p.getRawType();
Type[] args = p.getActualTypeArguments();
Type owner = p.getOwnerType();
~~~

**`getRawType()`** mô tả generic class/interface đứng làm gốc (`Map`). Nó là `Type`, thường là một `Class<?>` trong những ví dụ thông dụng.

**`getActualTypeArguments()`** trả type arguments được viết trong chữ ký generic ở mức khai báo tương ứng; với Map là hai giá trị. Đừng mặc định từng phần tử đều là Class.

**`getOwnerType()`** cho biết enclosing/owner type khi kiểu generic là member type có owner thích hợp. Chẳng hạn các cấu trúc `Outer<String>.Inner<Integer>` có thể cần thông tin về cả owner Outer<String> lẫn Inner<Integer>. Với top-level Map thông thường, owner là null.

Không được đồng nhất type owner của member type với superclass. Đây là hai quan hệ khác nhau:
- `getGenericSuperclass` đọc **extends**;
- `getOwnerType` đọc **owner/enclosing type** trong cấu trúc tên kiểu member.

### 6.1. Type arguments không bảo đảm số lượng element runtime

Một `ParameterizedType` mô tả `List<String>` không tự duyệt mọi element của object `List`. Nó cũng không phải bằng chứng rằng một raw alias chưa từng thêm Integer. Generic metadata có thể rất đầy đủ ở **declaration** trong khi runtime object vẫn có dữ liệu không phù hợp với kỳ vọng compile-time nếu code sử dụng raw/unchecked operations.

## 7. TypeVariable: biến kiểu thuộc **khai báo nào**?

Trong:

~~~java
class Parent<T> {
    T item;

    <E extends Exception> void act(E problem) throws E {
        throw problem;
    }
}
~~~

có hai type variables khác nhau:

- `T` thuộc **khai báo class Parent<T>**.
- `E` thuộc **khai báo method act<E>**.

Đọc type variables ở class:

~~~java
TypeVariable<?>[] vars = Parent.class.getTypeParameters();
System.out.println(vars[0].getName()); // T
~~~

Đọc ở method:

~~~java
Method m = Parent.class.getDeclaredMethod("act", Exception.class);
TypeVariable<Method>[] methodVars = m.getTypeParameters();
System.out.println(methodVars[0].getName()); // E
~~~

`getGenericDeclaration()` cho biết **nơi khai báo type variable**. Điều này quan trọng vì hai class khác nhau đều có thể dùng tên T mà vẫn là **hai biến kiểu khác nhau**. Không nên map type variables chỉ bằng tên String `"T"` trong một type resolver phức tạp.

Đọc giới hạn:

~~~java
for (Type bound : methodVars[0].getBounds()) {
    System.out.println(bound.getTypeName());
}
~~~

Nếu E extends Exception, upper bound liên quan là `java.lang.Exception`. Với T không có bound cụ thể, giới hạn mặc định là Object.

### 7.1. Raw erasure và TypeVariable không giống nhau

Khi `T` không có bound cụ thể, erasure của T thường là Object; nhưng `field.getGenericType()` vẫn có thể trả **TypeVariable T**, bởi metadata khai báo giữ chữ ký `T`.

Vì vậy:

~~~java
Field f = Parent.class.getDeclaredField("item");

Class<?> erased = f.getType(); // Object.class.
Type generic = f.getGenericType(); // TypeVariable T.
~~~

Đừng kết luận: “Runtime erasure thành Object thì getGenericType cũng phải trả Object”. Hai API cố ý thể hiện **hai lớp thông tin khác nhau**.

## 8. WildcardType: đọc giới hạn ? extends và ? super

Xét:

~~~java
class Parent<T> {
    List<? extends Number> upper;
    List<? super T> lower;
}
~~~

Các field types là ParameterizedType, nhưng bên trong có wildcard:

~~~java
Field field = Parent.class.getDeclaredField("upper");
ParameterizedType p = (ParameterizedType) field.getGenericType();

WildcardType wildcard =
        (WildcardType) p.getActualTypeArguments()[0];

Type[] uppers = wildcard.getUpperBounds();
Type[] lowers = wildcard.getLowerBounds();
~~~

Với `? extends Number`:
- upper bound là Number;
- danh sách lower bounds rỗng.

Với `? super T`:
- lower bound là **TypeVariable T**;
- upper bound mặc định là Object.

Với `?` không có giới hạn tường minh:
- upper bound mặc định Object;
- lower bounds rỗng.

Đọc đúng wildcard bounds có ích cho serializer, schema generator và API analyzer; nhưng đừng tự chuyển chúng thành một runtime check “collection luôn chứa Number” chỉ vì Reflection nhìn thấy `? extends Number` ở field declaration.

### 8.1. Wildcard và Generic Array có thể lồng nhau

Một field có thể khai báo:

~~~java
List<? extends T[]>[] arrays;
~~~

Đây là một kiểu nhiều tầng gồm generic array, parameterized type và wildcard. Một type traversal đúng phải phân loại và đệ quy qua từng tầng thay vì ép toàn bộ thành `Class<?>`.

Đừng viết một utility chỉ xử lý `Class` và `ParameterizedType` rồi xem tất cả kiểu còn lại là “không hợp lệ”; Java Generics có nhiều dạng Type hợp pháp.

## 9. Chương trình Java 17+ hoàn chỉnh

Lưu tệp **ReflectionSignaturesDemo.java**:

~~~java
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;

public class ReflectionSignaturesDemo {
    interface Source<K, V> {
        V get(K key);
    }

    static class Parent<T> {
        public T item;
        public List<T> items;
        public T[] buffer;
        public List<? extends Number> upper;

        public <E extends Exception> List<? super T> transform(
                List<? extends T> input, E problem) throws E {
            throw problem;
        }
    }

    static final class Child extends Parent<String>
            implements Source<String, List<Integer>>, Serializable {

        @Override
        public List<Integer> get(String key) {
            return List.of(key.length());
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {

        Class<Child> clazz = Child.class;

        Type superclass = clazz.getGenericSuperclass();
        ParameterizedType parentType =
                (ParameterizedType) superclass;

        System.out.println(
                "erasedSuperclass=" +
                clazz.getSuperclass().getSimpleName()
        );
        System.out.println(
                "genericSuperclass=" +
                (superclass instanceof ParameterizedType)
        );
        System.out.println(
                "superRaw=" +
                ((Class<?>) parentType.getRawType()).getSimpleName()
        );
        System.out.println(
                "superArg=" +
                parentType.getActualTypeArguments()[0].getTypeName()
        );

        Type[] interfaces = clazz.getGenericInterfaces();
        System.out.println(
                "interfaces=" + interfaces.length
        );

        ParameterizedType source =
                (ParameterizedType) interfaces[0];

        System.out.println(
                "sourceRaw=" +
                ((Class<?>) source.getRawType()).getSimpleName()
        );
        System.out.println(
                "sourceKey=" +
                source.getActualTypeArguments()[0].getTypeName()
        );
        System.out.println(
                "sourceValueNested=" +
                (source.getActualTypeArguments()[1]
                        instanceof ParameterizedType)
        );
        System.out.println(
                "markerClass=" +
                (interfaces[1] instanceof Class<?>)
        );

        System.out.println(
                "declaredT=" +
                Parent.class.getTypeParameters()[0].getName()
        );

        Field item = Parent.class.getDeclaredField("item");
        Field items = Parent.class.getDeclaredField("items");
        Field buffer = Parent.class.getDeclaredField("buffer");
        Field upper = Parent.class.getDeclaredField("upper");

        System.out.println(
                "itemType=" + item.getGenericType().getTypeName()
        );
        System.out.println(
                "itemsType=" + items.getGenericType().getTypeName()
        );
        System.out.println(
                "arrayType=" +
                (buffer.getGenericType()
                        instanceof GenericArrayType)
        );
        System.out.println(
                "wildcard=" +
                (((ParameterizedType) upper.getGenericType())
                        .getActualTypeArguments()[0]
                        instanceof WildcardType)
        );

        Method method = Parent.class.getDeclaredMethod(
                "transform", List.class, Exception.class
        );

        System.out.println(
                "methodReturn=" +
                method.getGenericReturnType().getTypeName()
        );
        System.out.println(
                "parameter0=" +
                method.getGenericParameterTypes()[0].getTypeName()
        );
        System.out.println(
                "parameter1=" +
                method.getGenericParameterTypes()[1].getTypeName()
        );
        System.out.println(
                "exceptionType=" +
                method.getGenericExceptionTypes()[0].getTypeName()
        );
        System.out.println(
                "methodVariable=" +
                method.getTypeParameters()[0].getName()
        );

        System.out.println(
                "inheritedItemType=" +
                clazz.getField("item").getGenericType().getTypeName()
        );
        System.out.println(
                "concreteReturn=" +
                clazz.getMethod("get", String.class)
                        .getGenericReturnType().getTypeName()
        );

        long bridges = Arrays.stream(clazz.getDeclaredMethods())
                .filter(Method::isBridge)
                .count();

        System.out.println("bridges=" + bridges);
    }
}
~~~

Biên dịch ở mức ngôn ngữ Java 17:

~~~bash
javac --release 17 -Xlint:all ReflectionSignaturesDemo.java
java ReflectionSignaturesDemo
~~~

**Stdout chính xác:**

~~~text
erasedSuperclass=Parent
genericSuperclass=true
superRaw=Parent
superArg=java.lang.String
interfaces=2
sourceRaw=Source
sourceKey=java.lang.String
sourceValueNested=true
markerClass=true
declaredT=T
itemType=T
itemsType=java.util.List<T>
arrayType=true
wildcard=true
methodReturn=java.util.List<? super T>
parameter0=java.util.List<? extends T>
parameter1=E
exceptionType=E
methodVariable=E
inheritedItemType=T
concreteReturn=java.util.List<java.lang.Integer>
bridges=1
~~~

Chương trình được thiết kế để đầu ra không phụ thuộc vào tên implementation nội bộ của ParameterizedType hoặc vào thứ tự duyệt tùy ý của `getDeclaredMethods()`; chỉ **đếm** các bridge methods. Trong ví dụ, compiler tạo một bridge cho phương thức triển khai generic interface `Source<K,V>`.

### 9.1. Những điểm cần quan sát trong output

**`erasedSuperclass=Parent`**: API thông thường trả class gốc không có type argument.

**`genericSuperclass=true` và `superArg=java.lang.String`**: chữ ký superclass ở Child cho biết Parent đã được tham số hóa bằng String.

**`interfaces=2`**: Child trực tiếp triển khai Source và Serializable. Source có generic signature; Serializable là non-generic interface.

**`sourceValueNested=true`**: argument thứ hai của Source là `List<Integer>`, bản thân là ParameterizedType, không phải Class<List<Integer>> runtime.

**`itemType=T`, `itemsType=java.util.List<T>`**: field metadata giữ **cách khai báo trong Parent**, không được tự đổi thành String chỉ vì ta đi qua Child.

**`arrayType=true`**: `T[]` được Reflection mô tả bằng GenericArrayType; khác với `String[]` được biểu diễn qua Class có component type String.

**`wildcard=true`**: `List<? extends Number>` chứa WildcardType ở vị trí type argument.

**`methodReturn` và `parameter0`**: method declaration dùng wildcard theo hai chiều `? super T` và `? extends T`.

**`parameter1=E`, `exceptionType=E`, `methodVariable=E`**: generic method có một type variable E dùng trong tham số và mệnh đề throws.

**`inheritedItemType=T`**: ngay cả khi tìm inherited public field qua Child, `Field.getGenericType()` vẫn nói về khai báo Parent<T>.

**`concreteReturn=java.util.List<java.lang.Integer>`**: method `get(String)` được Child khai báo trực tiếp với kiểu trả về List<Integer>, nên Reflection đọc chính chữ ký cụ thể của method này.

**`bridges=1`**: compiler có thể sinh bridge method do type erasure để duy trì overriding/interface dispatch ở JVM. Các phương thức synthetic/bridge có thể xuất hiện trong danh sách Reflection, nhưng không nên xem bridge là một generic declaration do developer tự viết.

## 10. Field.getType và Field.getGenericType

Đây là cặp API cần phân biệt nhất khi xem chữ ký thành viên:

~~~java
Field field = Parent.class.getDeclaredField("item");

Class<?> erasedType = field.getType();
Type genericType = field.getGenericType();
~~~

Với `T item` không có bound tường minh:
- `getType()` thường trả `Object.class` do erasure.
- `getGenericType()` trả **TypeVariable T**.

Với `List<T> items`:
- `getType()` trả `List.class`.
- `getGenericType()` trả **ParameterizedType List<T>**.

Với `T[] buffer`:
- `getType()` trả array class tương ứng erasure (`Object[]` khi T không có bound).
- `getGenericType()` trả **GenericArrayType T[]**.

Bảng:

| Field declaration | `getType()` | `getGenericType()` |
| --- | --- | --- |
| `String name` | String.class | Class của String |
| `T item` | Object.class nếu T unbounded | TypeVariable T |
| `List<T> items` | List.class | ParameterizedType List<T> |
| `T[] buffer` | Object[].class nếu T unbounded | GenericArrayType T[] |
| `List<? extends Number> upper` | List.class | ParameterizedType có WildcardType |

Không nên bỏ qua `getGenericType()` nếu xây schema mapper, serializer hoặc trình đọc metadata generic. Nhưng cũng không nên dùng nó để thay thế `getType()` khi cần runtime class dùng cho `isInstance` hoặc `Class.cast`.

## 11. Method.getReturnType và getGenericReturnType

Với:

~~~java
public <E extends Exception> List<? super T> transform(
        List<? extends T> input, E problem) throws E
~~~

`Method.getReturnType()` cho biết class của return type sau erasure: `List.class`.

`Method.getGenericReturnType()` cho biết kiểu khai báo đầy đủ:

~~~text
java.util.List<? super T>
~~~

Đọc phương thức:

~~~java
Method method = Parent.class.getDeclaredMethod(
        "transform", List.class, Exception.class
);

Type genericReturn = method.getGenericReturnType();
Class<?> erasedReturn = method.getReturnType();
~~~

Chú ý cách tìm `getDeclaredMethod`: tham số truyền vào là các **`Class<?>` theo erased parameter types**, không phải các `Type` đại diện cho `List<? extends T>` hoặc E.

Ở đây:
- `List<? extends T>` bị xóa về List;
- `E extends Exception` bị xóa về Exception;
- vì thế Java Reflection lookup dùng `List.class, Exception.class`.

Nếu nhầm truyền `Object.class` cho E hoặc `ArrayList.class` thay vì List, `getDeclaredMethod` có thể ném NoSuchMethodException do lookup đòi đúng **erased formal parameter types**.

### 11.1. Void và primitive return types

`Method.getGenericReturnType()` không có nghĩa mọi method trả ParameterizedType. Nếu method trả `void`, `int` hoặc `String`, Type tương ứng có thể được biểu diễn qua một `Class<?>` thích hợp. Luôn kiểm tra dạng Type trước khi cast.

## 12. getGenericParameterTypes và getGenericExceptionTypes

Phương thức:

~~~java
Method method = Parent.class.getDeclaredMethod(
        "transform", List.class, Exception.class
);
~~~

có:
- `getParameterTypes()`: các Class<?> đã xóa generic (`List.class`, `Exception.class`).
- `getGenericParameterTypes()`: `List<? extends T>` và `E`.
- `getExceptionTypes()`: các Class<?> trong mệnh đề throws sau erasure.
- `getGenericExceptionTypes()`: `E` dưới dạng TypeVariable.

Ví dụ:

~~~java
Type[] parameters = method.getGenericParameterTypes();
Type[] exceptions = method.getGenericExceptionTypes();

for (Type parameter : parameters) {
    System.out.println(parameter.getTypeName());
}
for (Type exceptionType : exceptions) {
    System.out.println(exceptionType.getTypeName());
}
~~~

Chữ ký generic giúp đọc **mối liên hệ giữa E trong tham số và E trong throws**. Chỉ xem `getExceptionTypes()` thường thấy bound Exception, không biết cả hai vị trí gắn cùng type variable E.

### 12.1. Type parameter của method khác type parameter của class

Đừng lấy `Parent.class.getTypeParameters()` rồi kết luận đã tìm được E. E được khai báo ở method `transform`, nên phải đọc:

~~~java
TypeVariable<Method>[] variables = method.getTypeParameters();
~~~

Trong khi T thuộc class Parent nên lấy qua `Parent.class.getTypeParameters()`. Hai variable có phạm vi khai báo khác nhau và có thể trùng tên nếu người viết source cố tình đặt như vậy ở những declarations khác nhau.

## 13. Declared members và inherited members

Đây là một nguồn lỗi lớn khi duyệt Reflection.

**Các API declared**:
- `getDeclaredFields()`, `getDeclaredField(name)`;
- `getDeclaredMethods()`, `getDeclaredMethod(name, parameterTypes...)`;
- `getDeclaredConstructors()`.

Chúng xét thành viên **được khai báo trực tiếp bởi class/interface được phản chiếu**, gồm cả những visibility khác nhau trong phạm vi API. Chúng không mặc định đi lên superclass để trả các thành viên được kế thừa.

**Các API public/inherited**:
- `getFields()`, `getField(name)`;
- `getMethods()`, `getMethod(name, parameterTypes...)`.

Chúng có thể thấy public members kế thừa và công khai, theo hợp đồng từng API.

Với `Child extends Parent<String>`:
- `Child.class.getDeclaredField("item")` thất bại nếu Child không tự khai báo field item;
- `Child.class.getField("item")` có thể tìm được public field item trong Parent;
- Field trả về có `getDeclaringClass() == Parent.class`;
- `getGenericType()` của Field ấy vẫn mô tả **T** như được viết ở Parent.

Bởi vậy, cách duyệt inherited fields rồi đếm các type variables xuất hiện trong metadata không tự trả một schema đầy đủ đã được specialize theo Child.

### 13.1. Duyệt class hierarchy khác duyệt declared fields

Để ghi một report đầy đủ, có thể duyệt các lớp cha theo `getSuperclass()` hoặc `getGenericSuperclass()`, rồi đọc `getDeclaredFields()` ở từng lớp. Nếu cần giữ kiểu thực tế của Child, phải lưu một **mapping các type variables → actual type arguments** khi đi qua generic superclass edges.

Cần xử lý cả shadowing, override, static fields, visibility, bridge methods và interfaces nếu thực sự xây type resolver production. Bài này tập trung vào cách đọc đúng metadata và phát hiện lúc nào cần bước resolution bổ sung.

## 14. Bẫy quan trọng: getGenericType trên field kế thừa vẫn là T

Với:

~~~java
class Parent<T> {
    public T item;
}

class Child extends Parent<String> {}
~~~

Không nên kỳ vọng:

~~~java
Type type = Child.class.getField("item").getGenericType();
// Không phải lúc nào cũng trả String; ở đây trả T.
~~~

Vì sao?

1. Field `item` được **khai báo** trong Parent<T>.
2. Reflection object `Field` đại diện cho **chính declaration đó**, không phải một bản field mới được compiler sinh cho mỗi subclass.
3. `Child extends Parent<String>` chứa quan hệ T→String trên **generic superclass edge**.
4. `Field.getGenericType()` đọc **chữ ký field gốc** trong Parent, nên biểu diễn T.
5. Muốn suy ra kiểu field trong **ngữ cảnh Child**, phải áp dụng mapping T→String vào Type của field.

Sơ đồ:

~~~text
Child
  extends Parent<String>
              │
              └── Parent<T> khai báo field T item
                             │
                             ▼
                       getGenericType() = T
                             │
               thay T bằng String trong ngữ cảnh Child
                             │
                             ▼
                 resolved contextual type = String
~~~

**Không nhầm type declaration với contextual resolved type.** Chính sự khác biệt đó giải thích vì sao những thư viện như type token utilities hoặc JSON binders cần logic giải generic supertypes thay vì chỉ gọi một API Reflection đơn lẻ.

## 15. Phác thảo thuật toán giải type variable theo ngữ cảnh

Muốn tìm “trong Child, field Parent.item có kiểu gì?”, có thể làm như sau:

1. Bắt đầu từ `Child.class` cùng một môi trường mapping rỗng.
2. Đọc `Child.getGenericSuperclass()`: `Parent<String>`.
3. Lấy raw type Parent, khai báo type parameters của nó: `Parent.class.getTypeParameters()` cho `T`.
4. Ghép từng type parameter với actual argument tương ứng từ ParameterizedType, tạo mapping `T → String`.
5. Đọc `Parent.class.getDeclaredField("item").getGenericType()` cho T.
6. Thay T bằng giá trị mapped là String.
7. Nếu type là `List<T>`, thay **bên trong** ParameterizedType để thành `List<String>` về mặt mô tả ngữ cảnh.
8. Nếu type là `T[]`, wildcard hoặc nested generic, đi sâu vào các thành phần để thay biến kiểu thích hợp.
9. Nếu superclass nhiều cấp có type variables khác, cần lặp và **compose mappings**, không chỉ giải ở một cạnh.
10. Nếu gặp `TypeVariable` không có mapping (hoặc có wildcard, owner types...), giữ thông tin chưa biết đúng ngữ nghĩa thay vì ép về Object một cách tùy tiện.

Ví dụ nhiều tầng:

~~~java
class Base<T> {
    T value;
}

class Middle<U> extends Base<List<U>> {}

class FinalChild extends Middle<String> {}
~~~

Suy luận trong ngữ cảnh FinalChild:

~~~text
Middle<U> nhận U = String
Base<T> nhận T = List<U>
Thay U = String
=> T = List<String>
=> Base.value là List<String> theo ngữ cảnh FinalChild
~~~

`Base.class.getDeclaredField("value").getGenericType()` vẫn trả **T**, trong khi kết quả contextual resolution dự kiến là `List<String>`.

**Đây là mô hình thuật toán**, không phải lời hứa rằng JDK cung cấp sẵn một nút `resolveEverything()` cho mọi cấu trúc kiểu. Viết một Type resolver tổng quát cần xử lý `ParameterizedType`, `TypeVariable`, `WildcardType`, `GenericArrayType`, owner types, interfaces, bounds và các trường hợp đặc biệt.

## 16. Bridge methods do compiler sinh ra

Khi class triển khai generic interface:

~~~java
interface Source<K, V> {
    V get(K key);
}

final class Child implements Source<String, List<Integer>> {
    @Override
    public List<Integer> get(String key) {
        return List.of(key.length());
    }
}
~~~

Sau erasure, interface method có dạng tương thích `Object get(Object)` trong bytecode, trong khi implementation source có `List<Integer> get(String)`. Compiler có thể tạo **bridge method** để bảo toàn cơ chế override/dispatch sau erasure.

Reflection có các API:
- `method.isBridge()`: có phải bridge method?
- `method.isSynthetic()`: có phải method compiler-generated synthetic?
- `method.getGenericReturnType()`: chữ ký kiểu của chính method được Reflection object đó đại diện.

Nếu in tất cả `Child.class.getDeclaredMethods()` mà không lọc, có thể thấy:
- method thực `get(String)` với generic return `List<Integer>`;
- bridge method với signature đã erasure như `get(Object)`.

Đừng tính bridge như một API nghiệp vụ mới do developer tự khai báo, và đừng để nó làm trùng entry khi xây tài liệu method signatures. Trong ví dụ hoàn chỉnh, `isBridge()` phát hiện **một** bridge method.

### 16.1. Kiểm tra bytecode

Dùng:

~~~bash
javap -p -c ReflectionSignaturesDemo
javap -p -v 'ReflectionSignaturesDemo$Child'
~~~

Trên shell cần quote `$` phù hợp; ví dụ lệnh thứ hai dùng cú pháp single quote phổ biến của Bash. Trong Windows PowerShell, cũng có thể dùng single quote để ngăn nội suy biến. Chú ý không dùng tên class chung chung `Child` khi nó là nested type; binary name chứa `$`.

Trong output bytecode, các attributes liên quan có thể gồm:
- `Signature`: metadata chữ ký generic;
- `ACC_BRIDGE` và `ACC_SYNTHETIC` cho bridge method;
- các descriptors đã erasure.

`javap -v` hữu ích để đối chiếu **bytecode descriptor** với **generic signature**; số offset cụ thể và thứ tự methods không phải thứ để phụ thuộc.

## 17. Constructor và ParameterizedType của thành viên khác

Chủ đề chữ ký thành viên không chỉ gồm field/method.

`Constructor<?>` có thể cung cấp:
- `getGenericParameterTypes()`;
- `getGenericExceptionTypes()`;
- `getTypeParameters()`;
- `getAnnotatedParameterTypes()` khi quan tâm type-use annotations.

Ví dụ constructor generic:

~~~java
class Factory {
    <X extends Number> Factory(X value) {
        System.out.println(value);
    }
}
~~~

Khi introspect, `getTypeParameters()` của constructor có thể cho X. Một constructor generic **không** khiến cả class Factory trở thành generic class.

Cần phân biệt:
- Type parameters **của class** từ `Class.getTypeParameters()`.
- Type parameters **của method** từ `Method.getTypeParameters()`.
- Type parameters **của constructor** từ `Constructor.getTypeParameters()`.

Với **non-static inner class constructors**, JVM có thể thêm tham số enclosing instance vào bytecode và cách Reflection báo parameters có các điểm tinh tế; khi viết một analyzer production cần hiểu synthetic/mandated parameters thay vì chỉ nhìn source count.

Đối với type-use annotations (ví dụ annotation trên type arguments), `getAnnotatedType` và họ `getAnnotatedReturnType` / `getAnnotatedParameterTypes` bổ sung góc nhìn về annotation metadata. Nhưng các API `getGeneric*` chủ yếu cung cấp **cấu trúc Type**, không phải toàn bộ annotation metadata.

## 18. Type erasure: generic metadata không thay thế kiểm tra runtime

Khi viết:

~~~java
List<String> names = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();
~~~

hai đối tượng `ArrayList` không trở thành hai runtime classes khác nhau theo type argument:

~~~java
boolean sameClass = names.getClass() == numbers.getClass();
~~~

Kết quả là true với hai đối tượng ArrayList được tạo như trên. Reflection gọi `names.getClass()` chỉ thấy runtime class ArrayList, không thể tự suy ra `String` từ biến source đã giữ đối tượng này.

Trong khi đó:

~~~java
Field f = SomeHolder.class.getDeclaredField("names");
Type declared = f.getGenericType();
~~~

nếu `SomeHolder` khai báo trường `List<String> names;` thì Reflection có thể đọc metadata `List<String>` từ **khai báo field**. Đó là sự khác biệt:
- **Object reflection**: object runtime có class, không mang mọi generic arguments.
- **Declaration reflection**: class file có thể ghi generic signature của field/method/supertype đã viết trong source.

Bản thân việc đọc `List<String>` từ metadata không phải validation:
- Không xác minh mọi phần tử trong list.
- Không chữa lỗi heap pollution do raw/unchecked aliases.
- Không tự xây schema đầy đủ nếu chữ ký chứa TypeVariable chưa giải.

Nếu muốn xử lý JSON, payload API hoặc dữ liệu không tin cậy theo `List<String>`, vẫn cần kiểm tra/copy dữ liệu hoặc dùng library binding có quy tắc runtime validation rõ ràng.

## 19. Chỗ nào Type.getTypeName() hữu ích, chỗ nào không đủ?

`Type.getTypeName()` cho ra tên dễ đọc, rất thích hợp cho logging và chẩn đoán:

~~~java
Type type = Parent.class.getDeclaredField("items").getGenericType();
System.out.println(type.getTypeName()); // java.util.List<T>
~~~

Nhưng một ứng dụng cần **phân tích cấu trúc** không nên dựa vào chuỗi này làm cách duy nhất để:
- đếm type arguments;
- so khớp type variables qua ngữ cảnh;
- đọc owner type;
- xác định wildcard upper/lower bounds;
- phát hiện generic array component;
- gắn type-use annotations.

Những thao tác đó nên dùng các interfaces Reflection và truy cập thành phần qua methods chính thức. String representation thích hợp để **hiển thị**, không phải parser thay thế AST Type trong các hệ thống cần độ chính xác.

### 19.1. Tránh suy ra quá nhiều từ tên Type implementation

Một lời gọi `type.getClass().getName()` có thể cho tên lớp nội bộ của JDK. Những tên này không phải hợp đồng Java Reflection cấp cao. Viết:

~~~java
if (type instanceof ParameterizedType p) {
    // Sử dụng p.getActualTypeArguments(), p.getRawType(), ...
}
~~~

thường bền vững hơn việc so sánh tên `sun.reflect...` hoặc một tên class nội bộ khác.

## 20. Những lỗi thường gặp

### Lỗi A — dùng getSuperclass để lấy type arguments

~~~java
Class<?> parent = Child.class.getSuperclass();
// parent không chứa String như một actual type argument.
~~~

**Sửa:** dùng `getGenericSuperclass()` và phân loại Type; khi nhận ParameterizedType thì đọc `getActualTypeArguments()`.

### Lỗi B — cast mọi getGenericSuperclass thành ParameterizedType

~~~java
// ParameterizedType p =
//     (ParameterizedType) SomeClass.class.getGenericSuperclass();
~~~

**Sai khi** superclass không generic, dùng raw superclass hoặc không có superclass tương ứng. Kiểm tra `instanceof ParameterizedType` và null trước.

### Lỗi C — nghĩ getGenericInterfaces tự trả interfaces được kế thừa gián tiếp

`getGenericInterfaces()` phản ánh **direct interfaces**. Nếu class kế thừa implementation từ parent, phải duyệt hierarchy đúng cách để lấy toàn bộ giao diện liên quan.

### Lỗi D — nghĩ mọi actual type argument là Class

Với `Source<String,List<Integer>>`, argument thứ hai là ParameterizedType, không phải một Class<List<Integer>>. Với `List<? extends Number>`, argument là WildcardType; với `List<T>`, argument là TypeVariable.

### Lỗi E — nghĩ Child.getField("item").getGenericType trả String

Trong ví dụ Parent<T>/Child extends Parent<String>, Field đại diện declaration ở Parent, nên vẫn trả T. Cần contextual substitution T→String từ generic superclass edge.

### Lỗi F — tìm method bằng generic parameterized Type

`getDeclaredMethod` nhận các Class<?> theo **erased parameter types**, không nhận ParameterizedType. Với `transform(List<? extends T>,E)` và E extends Exception, dùng `List.class, Exception.class`.

### Lỗi G — gộp getMethods và getDeclaredMethods như thể cùng semantics

Một cái có thể bao gồm public inherited methods, cái kia chỉ gồm members declared trực tiếp. Khi build analyzer, phải chọn đúng phạm vi, xử lý override và bridge/synthetic members.

### Lỗi H — dùng TypeVariable.getName làm định danh duy nhất

Nhiều khai báo khác nhau cùng dùng `T`. Cần xét `getGenericDeclaration()` và binding context, không chỉ chuỗi ký tự T.

### Lỗi I — đọc T[] như một Class array có component T runtime

`T[]` có thể được phản chiếu bằng GenericArrayType; erasure có thể thành Object[]. Không có runtime class riêng T[] cho mỗi generic instantiation.

### Lỗi J — xem generic metadata như dữ liệu runtime đáng tin

Một field được khai báo `List<String>` không ngăn code raw/unchecked làm hỏng nội dung tại runtime. Reflection không thực hiện validation toàn bộ phần tử.

### Lỗi K — quên các synthetic/bridge methods

Compiler có thể sinh bridge methods để duy trì overriding generic sau erasure. Không nên xuất báo cáo API có phương thức trùng mà không xét `isBridge()`, `isSynthetic()` và declaring class.

### Lỗi L — không nghĩ đến giới hạn generic signature thực tế

Một số class được sinh bytecode bằng công cụ khác hoặc bị loại generic signature có thể khiến thông tin Reflection không đầy đủ hoặc không thể giải như mong muốn. Nếu đang phân tích class ngoài dự án, cần chiến lược fallback và xử lý exceptions phù hợp.

## 21. Checklist khi đọc chữ ký generic trong dự án

1. Xác nhận `Class<?>` cần phân tích là class/interface nào và có đúng classloader không.
2. Dùng `getGenericSuperclass()` để đọc edge **extends trực tiếp**; kiểm tra null, Class hoặc ParameterizedType.
3. Dùng `getGenericInterfaces()` để đọc edges **implements/extends interface trực tiếp**; không bỏ các interface non-generic.
4. Nếu Type là ParameterizedType, đọc `getRawType()`, `getActualTypeArguments()` và `getOwnerType()` khi phù hợp.
5. Nếu Type là TypeVariable, xem `getName()`, `getBounds()`, `getGenericDeclaration()`.
6. Nếu Type là WildcardType, xem `getUpperBounds()` và `getLowerBounds()`.
7. Nếu Type là GenericArrayType, đi vào `getGenericComponentType()`.
8. Dùng `Field.getGenericType()` để đọc khai báo field; đối chiếu `Field.getType()` khi cần erasure.
9. Dùng `Method.getGenericReturnType()`, `getGenericParameterTypes()` và `getGenericExceptionTypes()` cho chữ ký method.
10. Nếu cần type parameters của riêng method/constructor, dùng `getTypeParameters()` đúng đối tượng khai báo.
11. Khi phân tích members inherited, xét `getDeclaringClass()` rồi giải context mapping từ subclass đến declaring class.
12. Lọc hoặc phân loại bridge/synthetic methods nếu mục tiêu là tài liệu API do developer viết.
13. Kiểm tra generic signature là **declaration metadata**, không phải runtime validation của mọi object.
14. Không dựng parser dựa trên `getTypeName()` nếu đã có Type interfaces để điều hướng cấu trúc.
15. Khi xử lý bytecode phức tạp, cân nhắc missing types, classloader boundaries và malformed signatures thay vì assume mọi Type đều tải được.
16. Viết bài kiểm thử cho `List<T>`, nested `Map<String,List<Integer>>`, `T[]`, `? extends`, `? super`, inherited T substitution và generic method throws E.

## 22. Câu hỏi tự kiểm tra

**1.** `Child extends Parent<String>`. API nào cho biết type argument String của superclass?

**Đáp án:** `Child.class.getGenericSuperclass()`; nếu kết quả là ParameterizedType thì đọc `getActualTypeArguments()[0]`.

**2.** Vì sao `getSuperclass()` không trả ParameterizedType?

**Đáp án:** `getSuperclass()` có kiểu trả về Class<?> và chỉ cung cấp runtime class của superclass; generic arguments không được mã hóa vào giá trị Class đó.

**3.** `getGenericInterfaces()` trả tất cả inherited interfaces hay trực tiếp?

**Đáp án:** Các direct interfaces được class/interface khai báo. Muốn toàn bộ hierarchy, phải duyệt tiếp superclass và interfaces.

**4.** Với `Source<String,List<Integer>>`, type argument thứ hai là dạng gì?

**Đáp án:** ParameterizedType của List<Integer>, không phải Class<List<Integer>>.

**5.** Với `Parent<T>` có field `T item`, `getGenericType()` cho biết gì?

**Đáp án:** TypeVariable T của declaration Parent, trong khi `getType()` có thể trả Object.class sau erasure nếu T unbounded.

**6.** Vì sao `Child.class.getField("item").getGenericType()` vẫn là T?

**Đáp án:** Field object mô tả declaration ở Parent<T>. Quan hệ T→String được lưu trên generic superclass edge của Child và phải được áp dụng riêng khi giải contextual type.

**7.** Với `T[] buffer`, Reflection có thể biểu diễn generic type bằng interface nào?

**Đáp án:** GenericArrayType; có thể đọc `getGenericComponentType()` để lấy TypeVariable T.

**8.** Với `List<? extends Number>`, làm sao đọc Number?

**Đáp án:** Lấy Field.getGenericType() dưới dạng ParameterizedType; type argument là WildcardType; đọc `getUpperBounds()`.

**9.** `getDeclaredMethod("transform", List.class, Exception.class)` dùng hai Class vì sao?

**Đáp án:** Lookup bằng erased formal parameter types, không phải generic Type objects của `List<? extends T>` và `E`.

**10.** `method.getGenericExceptionTypes()` trả E hay Exception trong ví dụ transform?

**Đáp án:** E dưới dạng TypeVariable, còn `getExceptionTypes()` phản ánh class sau erasure tương ứng Exception.

**11.** TypeVariable T và TypeVariable E khác nhau ở đâu?

**Đáp án:** T thuộc generic class Parent, E thuộc generic method transform. `getGenericDeclaration()` cho biết declaration chứa mỗi biến.

**12.** Một bridge method có thể xuất hiện dù không có trong source code không?

**Đáp án:** Có. Compiler có thể tạo bridge sau erasure để duy trì overriding và polymorphism, phát hiện bằng `isBridge()`.

**13.** Chỉ có Type object của `List<String>` thì đã xác minh được mọi phần tử của List runtime là String chưa?

**Đáp án:** Chưa. Type là metadata mô tả khai báo; vẫn cần kiểm tra dữ liệu hoặc đảm bảo bất biến qua API được tin cậy.

**14.** `getTypeName()` có nên được parse bằng regex để giải mọi generic type không?

**Đáp án:** Không. Nó thích hợp để hiển thị; dùng Type interfaces và methods chính thức để phân tích cấu trúc, đặc biệt nested types, wildcards và type variables.

**15.** Để suy ra Base.value thành List<String> trong `FinalChild extends Middle<String>` và `Middle<U> extends Base<List<U>>` cần bước gì?

**Đáp án:** Compose mappings qua các superclass edges: U→String, T→List<U>, rồi thay U để có T→List<String>. `Field.getGenericType()` đơn lẻ chỉ cho declaration T.

## 23. Ghi nhớ

**Java Reflection có thể đọc được generic signatures của superclass, interfaces và members nhưng không tự khôi phục toàn bộ type arguments của mọi runtime instance.**

Ba bước quan trọng nhất:

~~~text
Class<?> cần phân tích
    │
    ├── getGenericSuperclass() → Type của superclass trực tiếp
    │
    ├── getGenericInterfaces() → Type[] của direct interfaces
    │
    └── declared/inherited member
            ├── Field.getGenericType()
            ├── Method.getGenericReturnType()
            ├── Method.getGenericParameterTypes()
            └── Method.getGenericExceptionTypes()
~~~

Sau đó **phân loại Type** thành `Class`, `ParameterizedType`, `TypeVariable`, `WildcardType` hoặc `GenericArrayType` để điều hướng tiếp.

Nếu cần xem kiểu thành viên trong **ngữ cảnh subclass**, hãy nhớ rằng Reflection trả **chữ ký tại nơi member được khai báo**: `Parent<T>.item` vẫn là T, dù `Child extends Parent<String>`. Muốn có String theo ngữ cảnh Child, phải thực hiện type-variable substitution theo hierarchy.

Cuối cùng, đừng nhầm **metadata generic trong class file** với **kiểm tra runtime của object**. Hiểu đúng sự khác biệt đó là nền tảng để sử dụng Reflection trong serializers, object mappers, dependency injection containers, API analyzers và các công cụ Java Generics.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-reflection-008">Lưu kiểu lồng nhau qua lớp con và mẫu type token</a>

<details>
<summary>Xem chi tiết</summary>

# Lưu kiểu lồng nhau qua lớp con và mẫu Type Token trong Java Generics

## 1. Bài toán: Làm sao mô tả `List<Map<String, Integer>>` khi chạy?

Giả sử một API cần biết **toàn bộ kiểu của dữ liệu** để đọc JSON, kiểm tra schema, ánh xạ dữ liệu hoặc tạo một adapter:

```java
List<Map<String, Integer>> records;
```

Kiểu trên có nhiều cấp:

- Cấp ngoài là `List`.
- Phần tử của `List` là `Map<String, Integer>`.
- Key của `Map` là `String`.
- Value của `Map` là `Integer`.

Nếu chỉ truyền:

```java
List.class
```

thì thông tin `Map<String, Integer>` **không có trong đối tượng `Class` ấy**. Java cũng không có class literal hợp lệ như:

```java
// Không hợp lệ:
// List<Map<String, Integer>>.class
```

**Super type token** (thường gọi ngắn là *type token*) là một kỹ thuật lưu mô tả generic type bằng cách khai báo **một lớp con có superclass được tham số hóa bằng kiểu cụ thể** rồi đọc phần *generic signature* của superclass thông qua Reflection.

Ví dụ cách sử dụng mong muốn:

```java
TypeRef<List<Map<String, Integer>>> token =
        new TypeRef<List<Map<String, Integer>>>() {};

Type type = token.type();
System.out.println(type.getTypeName());
```

Kết quả Java 17:

```text
java.util.List<java.util.Map<java.lang.String, java.lang.Integer>>
```

Chú ý cặp ngoặc nhọn `{}` cuối `new TypeRef<...>() {}`. Nó tạo một **anonymous subclass**. Chính subclass mang khai báo superclass có kiểu tham số hóa, cho phép Reflection đọc lại chuỗi cấu trúc `List<Map<String,Integer>>` đã viết trong source.

Bài này tập trung vào kỹ thuật lưu kiểu lồng nhau qua superclass, cách duyệt `Type`, những hạn chế do type erasure, cách xử lý `TypeVariable`/wildcard/generic array, cùng nguyên tắc dùng type token đúng và an toàn trong API thực tế. Toàn bộ ví dụ chính dùng Java 17.

## 2. Phân biệt kiểu tĩnh, class runtime và metadata generic

Hãy xem hai biến:

```java
List<String> names = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();
```

Tại source, compiler coi hai biến có **parameterized types khác nhau** và bảo vệ phép thêm/đọc phần tử theo kiểu đó.

Nhưng cả hai object được khởi tạo bằng `new ArrayList<>()`; tại runtime:

```java
System.out.println(
        names.getClass() == numbers.getClass()); // true
```

Không có một runtime class riêng `ArrayList<String>` và một class khác `ArrayList<Integer>`. Đó là đặc điểm của **type erasure** trong Java Generics.

Tuy vậy, nói “Java xóa hết mọi thông tin generic” cũng không chính xác. Compiler có thể ghi **thông tin chữ ký generic của khai báo** vào class file, chẳng hạn:

```java
class StringList extends ArrayList<String> {
}
```

Lớp `StringList` khai báo cụ thể superclass là `ArrayList<String>`. Reflection có thể đọc metadata ấy qua `StringList.class.getGenericSuperclass()`.

Điểm phân biệt:

| Nơi tìm thông tin | Câu hỏi được trả lời | Giới hạn |
| --- | --- | --- |
| Kiểu tĩnh `List<String>` | Compiler cho phép những thao tác nào? | Không tạo runtime specialization riêng. |
| `object.getClass()` | Object có runtime class gì? | Thường không biết type arguments của instance. |
| `SomeClass.class.getGenericSuperclass()` | Class *đã khai báo* generic superclass như thế nào? | Đọc khai báo, không xác nhận dữ liệu object. |
| `Field.getGenericType()` | Field được khai báo có generic type gì? | Không kiểm tra value hiện đang gán vào field. |
| `Method.getGenericReturnType()` | Method khai báo return type generic nào? | Không suy luận tự động type argument cụ thể của mỗi lời gọi. |
| Type token qua subclass | Source đã khai báo kiểu tham số hóa nào ở superclass? | Thường chỉ lưu mô tả `Type`, không tự validate object. |

**Bản chất:** type token tận dụng **metadata còn lưu ở declaration**, không đảo ngược phép xóa kiểu để truy ra generic arguments tùy ý từ một object đã tồn tại.

## 3. Các loại `java.lang.reflect.Type` phải biết

`java.lang.reflect.Type` là giao diện biểu diễn kiểu được Reflection sử dụng. Những dạng thường gặp:

| Dạng `Type` | Ví dụ | Dùng khi |
| --- | --- | --- |
| `Class<?>` | `String`, `int`, `String[]` | Kiểu có class object tương ứng. |
| `ParameterizedType` | `List<String>`, `Map<String,Integer>` | Kiểu có generic arguments đã được viết trong declaration. |
| `TypeVariable<?>` | `T`, `E`, `K`, `V` | Declaration còn biến kiểu chưa thay bằng kiểu cụ thể. |
| `WildcardType` | `?`, `? extends Number`, `? super Integer` | Generic argument là wildcard có bounds. |
| `GenericArrayType` | `T[]`, `List<String>[]` | Kiểu mảng có component là kiểu non-reifiable trong mô tả generic. |

Từ Java 8, mỗi `Type` có phương thức `getTypeName()` để lấy một biểu diễn tên kiểu phù hợp cho chẩn đoán và hiển thị.

### 3.1. `Class<?>` không đồng nghĩa `Type`

`Class<?>` **là một dạng của `Type`**, nhưng không phải mọi `Type` đều là một `Class<?>`.

```java
Type stringType = String.class; // Class<String> là Type.
```

Trong khi:

```java
Type listType =
        new TypeRef<List<String>>() {}.type();
```

`listType` thường là `ParameterizedType`, **không thể cast thẳng** thành `Class<?>`:

```java
// Có thể ném ClassCastException:
// Class<?> cls = (Class<?>) listType;
```

Một framework làm việc với generic metadata phải phân nhánh theo kiểu thực của đối tượng `Type`, thay vì luôn giả định đó là Class.

### 3.2. `ParameterizedType`

Một `ParameterizedType` cho các thông tin chính:

```java
Type raw = p.getRawType();
Type[] arguments = p.getActualTypeArguments();
Type owner = p.getOwnerType();
```

Với `List<Map<String,Integer>>`:

- `getRawType()` của lớp ngoài là `java.util.List`.
- Mảng arguments của lớp ngoài có **một phần tử**: `Map<String,Integer>`.
- Argument đó tiếp tục là `ParameterizedType`.
- Raw type của argument là `java.util.Map`.
- Hai arguments của Map lần lượt là `String.class`, `Integer.class`.

`getOwnerType()` dành cho quan hệ owner của những member/nested classes, **không** phải type argument của List. Với top-level types như `List` và `Map`, owner thông thường là `null`.

### 3.3. `TypeVariable<?>`

Một type variable như `T` có tên, generic declaration và bounds. Ví dụ:

```java
static <T> void work(T value) {
}
```

`T` không tự trở thành `String.class` khi caller gọi `work("Java")`. Nếu metadata của một anonymous subclass vẫn chứa `T`, type token còn **chưa được giải quyết thành kiểu cụ thể**.

### 3.4. `WildcardType`

Một wildcard `? extends Number` có upper bound `Number`. Một `? super Integer` có lower bound `Integer`.

`WildcardType.getUpperBounds()` và `.getLowerBounds()` giúp một parser/validator xem bound đã khai báo. Chúng không có nghĩa có thể biết chính xác subtype được sử dụng ở từng runtime object.

### 3.5. `GenericArrayType`

`List<String>[]` có thể xuất hiện trong **mô tả kiểu** dù Java không cho `new List<String>[n]` trực tiếp. Reflection dùng `GenericArrayType` để mô tả mảng có component generic non-reifiable, ví dụ `List<String>[]` hoặc `T[]`.

Kỹ thuật type token có thể **lưu mô tả** của kiểu này; lưu metadata không đồng nghĩa được phép tạo object array bằng một cú pháp mà compiler cấm.

## 4. Vì sao anonymous subclass lưu được type arguments?

Giả sử bạn định nghĩa:

```java
abstract class TypeRef<T> {
}
```

Sau đó viết:

```java
TypeRef<List<String>> token =
        new TypeRef<List<String>>() {};
```

Đây **không đơn thuần** là `new TypeRef<List<String>>()`. Do `TypeRef` là abstract, không thể tạo instance trực tiếp. Cú pháp `{}` tạo một anonymous concrete subclass, có thể hình dung gần giống:

```java
final class GeneratedTypeRef extends TypeRef<List<String>> {
}
```

Compiler ghi generic superclass signature của class con như một khai báo:

```text
TypeRef<java.util.List<java.lang.String>>
```

Khi chạy:

```java
Type superType = token.getClass().getGenericSuperclass();
```

Reflection trả mô tả generic superclass của **class anonymous**. Nếu superclass được khai báo bằng type arguments, kết quả là một `ParameterizedType`.

Từ đó bạn có thể lấy argument `List<String>`.

### 4.1. Vai trò chính xác của `{}`

So sánh:

```java
new TypeRef<List<String>>() {};
```

với cách tạo object của một generic class thông thường:

```java
new ArrayList<String>();
```

`new ArrayList<String>()` tạo object `ArrayList` nhưng không định nghĩa thêm một class con có generic superclass signature chứa `String`. Trong khi `new TypeRef<List<String>>() {}` tạo anonymous subclass của TypeRef và **superclass signature** có chứa cấu trúc được chỉ rõ.

Một số cú pháp diamond anonymous class đã được Java hỗ trợ từ Java 9, nhưng để học super type token, ghi **tường minh toàn bộ type arguments** ngay trong `new TypeRef<...>() {}` giúp dễ kiểm tra kiểu mà metadata sẽ lưu.

### 4.2. Đây là kỹ thuật giữ metadata, không phải reification đầy đủ

Reflection đọc thông tin **trong class declaration của anonymous subclass**. JVM không tạo các object `List<String>` có runtime class riêng, cũng không có cơ chế tự động kiểm tra toàn bộ elements của một list vừa được nhận từ mạng hoặc file.

Nói cách khác, **Type token mô tả “kiểu mong đợi”**. Bước kiểm tra hoặc chuyển đổi dữ liệu thực vẫn do library/application thực hiện.

## 5. Xây một `TypeRef<T>` nhỏ, độc lập

Một ví dụ tối thiểu nhưng có kiểm tra rõ ràng:

```java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

abstract class TypeRef<T> {
    private final Type type;

    protected TypeRef() {
        Type parent = getClass().getGenericSuperclass();

        if (!(parent instanceof ParameterizedType p)
                || p.getRawType() != TypeRef.class) {
            throw new IllegalStateException(
                    "Use a direct parameterized subclass");
        }

        this.type = Objects.requireNonNull(
                p.getActualTypeArguments()[0]);
    }

    public final Type type() {
        return type;
    }
}
```

Đoạn class trên cần nằm trong một file Java có các imports đã liệt kê. Không chứa logic parse JSON; nó chỉ **đọc và giữ metadata**.

Quy trình trong constructor:

1. `getClass()` lấy runtime class của instance — anonymous subclass hoặc named subclass.
2. `getGenericSuperclass()` đọc kiểu superclass đã khai báo.
3. Pattern `instanceof ParameterizedType p` xác minh metadata superclass có type arguments.
4. `p.getRawType() == TypeRef.class` xác nhận cấu trúc **class con trực tiếp** của `TypeRef` như thiết kế.
5. `getActualTypeArguments()[0]` lấy type argument đầu tiên, có thể là Class, ParameterizedType, TypeVariable hoặc kiểu Type khác.
6. Field `type` lưu mô tả đó để caller hoặc framework sử dụng.

### 5.1. Vì sao constructor `protected`?

Người dùng thiết kế này tạo **subclass**, không tạo instance TypeRef trực tiếp. `protected` thể hiện constructor dành cho subclasses.

Nếu cần không cho class ngoài package kế thừa, API có thể chọn thiết kế khác; ví dụ ở đây ưu tiên đơn giản và dễ kiểm chứng.

### 5.2. Vì sao field `type` là `final`?

Cấu trúc kiểu được đọc một lần khi object được tạo. `final` làm rõ rằng token đại diện cho **một mô tả kiểu cố định**, không phải một holder có thể chuyển từ `List<String>` sang `List<Integer>` sau đó.

Dù `Type` là interface, những implementations do JDK trả từ reflection thể hiện metadata khai báo. Điều đó không có nghĩa mọi implementation `Type` do bên thứ ba tạo đều có tính bất biến/equals/hashCode đúng nếu bạn tự nhận Type từ nguồn khác.

### 5.3. Vì sao kiểm tra `getRawType()`?

Một `ParameterizedType` bất kỳ ở superclass **không đủ** để chứng minh argument đầu tiên chính là `T` của `TypeRef`. Đoạn code này chỉ xử lý trường hợp:

```java
class NamedRef extends TypeRef<List<String>> {
}
```

hoặc anonymous subclass trực tiếp tương đương. Với một lớp trung gian:

```java
class Intermediate<U> extends TypeRef<U> {
}

class Concrete extends Intermediate<List<String>> {
}
```

`Concrete.class.getGenericSuperclass()` là `Intermediate<List<String>>`, không phải `TypeRef<List<String>>`. Ví dụ kiểm tra `getRawType() == TypeRef.class` sẽ từ chối. Đây là **giới hạn có chủ đích của bản `TypeRef` đơn giản**, không phải giới hạn tuyệt đối của mọi type token library.

Một implementation hỗ trợ nhiều tầng kế thừa phải đi dọc superclass/interfaces và **thay thế TypeVariables bằng actual type arguments theo từng tầng**. Chỉ gọi `getGenericSuperclass()` rồi lấy argument `[0]` không giải quyết được bài toán tổng quát đó.

## 6. Ví dụ kiểu lồng nhau `List<Map<String,Integer>>`

Khai báo:

```java
TypeRef<List<Map<String, Integer>>> token =
        new TypeRef<List<Map<String, Integer>>>() {};
```

Đọc:

```java
Type type = token.type();
System.out.println(type.getTypeName());
```

Output:

```text
java.util.List<java.util.Map<java.lang.String, java.lang.Integer>>
```

Phân rã từng cấp:

```java
ParameterizedType outer = (ParameterizedType) type;

Type rawList = outer.getRawType();
Type mapArgument = outer.getActualTypeArguments()[0];

ParameterizedType inner = (ParameterizedType) mapArgument;

Type rawMap = inner.getRawType();
Type key = inner.getActualTypeArguments()[0];
Type value = inner.getActualTypeArguments()[1];
```

Tương ứng:

| Thành phần | Kiểu `Type` được trả | Giá trị mô tả |
| --- | --- | --- |
| `type` | `ParameterizedType` | `List<Map<String,Integer>>` |
| `rawList` | Thường là `Class<?>` | `java.util.List` |
| `mapArgument` | `ParameterizedType` | `Map<String,Integer>` |
| `rawMap` | Thường là `Class<?>` | `java.util.Map` |
| `key` | `Class<?>` | `java.lang.String` |
| `value` | `Class<?>` | `java.lang.Integer` |

Không cần split chuỗi `getTypeName()` theo ký tự `<`, `,`, `>` rồi parse lại. **Hãy dùng cây `Type` thật** với `getRawType()` và `getActualTypeArguments()`; cách này xử lý tốt hơn khi xuất hiện wildcard, owner type hoặc nested arrays.

### 6.1. Vì sao cast sang `ParameterizedType` ở đây hợp lệ?

Vì token được viết với type `List<Map<String,Integer>>`, và bạn kiểm tra/ngầm biết class của `Type` từ mẫu ví dụ. Trong một API nhận **Type bất kỳ**, không được cast vô điều kiện:

```java
Type t = String.class;

// Không an toàn:
// ParameterizedType p = (ParameterizedType) t;
```

`String.class` là `Class`, không phải ParameterizedType. Một helper tổng quát phải dùng `instanceof` để phân nhánh.

### 6.2. `Type.getTypeName()` không phải schema validator

Nó rất hữu ích để log, debug và hiển thị. Tuy nhiên khi framework cần biến JSON thành object, không nên chỉ dựa vào chuỗi này để xác thực toàn bộ cấu trúc. API reflection đã cung cấp các đối tượng loại Type có ý nghĩa rõ hơn chuỗi hiển thị.

## 7. Tạo type token qua named subclass

Không bắt buộc phải dùng anonymous class.

```java
final class StringListRef extends TypeRef<List<String>> {
}
```

Sau đó:

```java
Type type = new StringListRef().type();
```

Kết quả:

```text
java.util.List<java.lang.String>
```

Ưu điểm: đặt tên cho kiểu dùng nhiều lần, dễ tái sử dụng và đặt ở một vị trí chung. Anonymous subclass tiện hơn khi chỉ cần một type token ngay tại call site:

```java
TypeRef<Map<String, List<Integer>>> token =
        new TypeRef<Map<String, List<Integer>>>() {};
```

### 7.1. Không nhầm named subclass với class instance chứa dữ liệu

`StringListRef` **không phải** một `List<String>`. Nó là một đối tượng mô tả kiểu `List<String>`. Viết:

```java
StringListRef token = new StringListRef();
```

không hề tạo một list mới và không chứa các String elements.

Một API parse có thể nhận `token.type()` rồi **dựa trên metadata** để tạo List; đó là công việc của parser, không phải của token.

### 7.2. Dùng constant type token như thế nào?

Nếu một type description không thay đổi, có thể giữ lại token hoặc `Type` theo phạm vi thích hợp, tránh tạo anonymous subclasses không cần thiết ở nhiều call sites.

Tuy vậy không nên tự xây một static cache toàn cục không có giới hạn nếu ứng dụng có nhiều class loaders, plugin classes hoặc dynamic types; quản lý vòng đời cache là trách nhiệm riêng.

## 8. Một type token có thể chứa nhiều dạng Type lồng nhau

### 8.1. Wildcard có bound

```java
TypeRef<List<? extends Number>> token =
        new TypeRef<List<? extends Number>>() {};
```

Cấp ngoài là `ParameterizedType` của List. Argument `? extends Number` là `WildcardType`.

```java
ParameterizedType list =
        (ParameterizedType) token.type();

WildcardType wildcard =
        (WildcardType) list.getActualTypeArguments()[0];

Type upper = wildcard.getUpperBounds()[0];
```

`upper.getTypeName()` cho:

```text
java.lang.Number
```

Điều này chỉ cho biết source đã khai báo upper bound Number. Token không biết liệu object list thực tế chứa Integer, Double hoặc subtype cụ thể nào.

### 8.2. Generic array

```java
TypeRef<List<String>[]> token =
        new TypeRef<List<String>[]>() {};
```

Mô tả `List<String>[]` có thể xuất hiện dưới dạng `GenericArrayType`:

```java
GenericArrayType array =
        (GenericArrayType) token.type();

Type component = array.getGenericComponentType();
```

`component.getTypeName()`:

```text
java.util.List<java.lang.String>
```

Cần phân biệt **mô tả kiểu array** với **khởi tạo array object**:

```java
// Không hợp lệ trong Java:
// new List<String>[3];
```

Đọc/lưu type token không biến biểu thức cấp phát bị cấm thành hợp lệ.

### 8.3. `Class<?>` của kiểu bình thường

```java
TypeRef<String> token =
        new TypeRef<String>() {};
```

Ở ví dụ này `type()` là `Class` cho String:

```java
token.type() == String.class // true trong trường hợp này
```

Không phải mọi token đều chứa ParameterizedType. Một API tổng quát phải chấp nhận cấu trúc `Type` khác nhau.

### 8.4. Kiểu lồng nhau nhiều tầng

Một token có thể mô tả các cấu trúc sâu hơn:

```java
TypeRef<Map<String, List<Map<Integer, String>>>> token =
        new TypeRef<Map<String, List<Map<Integer, String>>>>() {};
```

Cách duyệt vẫn như nhau: đi từ `ParameterizedType` ngoài vào từng `getActualTypeArguments()`, cho đến khi gặp Class, TypeVariable, WildcardType hoặc GenericArrayType.

Không có giới hạn “chỉ hai cấp” trong kỹ thuật này. Giới hạn thực tế là các kiểu Java có thể khai báo hợp lệ, tài nguyên Reflection và năng lực xử lý của framework tiêu thụ Type.

## 9. Vì sao `new TypeRef<List<T>>() {}` chưa đủ để biết `T`?

Đây là **giới hạn quan trọng nhất** khi áp dụng super type token trong generic method.

```java
static <T> Type captureInside(T example) {
    return new TypeRef<List<T>>() {}.type();
}
```

Caller:

```java
Type type = captureInside("hello");
```

Bạn có thể nghĩ token đã biết `T = String`. Nhưng class của anonymous subclass được compiler tạo **từ declaration chung** của method, không tạo specialization riêng theo mỗi invocation.

Kết quả đại diện cho:

```text
java.util.List<T>
```

Argument bên trong là `TypeVariable`, **không phải `String.class`**.

### 9.1. Type erasure vẫn tồn tại

Trong source:

```java
static <T> Type makeToken() {
    return new TypeRef<List<T>>() {}.type();
}
```

type variable `T` được khai báo tại generic method. Metadata có thể giữ **tên và bounds** của T, nhưng không tự thay nó bằng class cụ thể do caller dùng tại runtime.

Thậm chí khi caller viết:

```java
makeToken();
```

ở một vị trí có target type rõ, token tạo bên trong method vẫn không được reify thành một class specialization mới.

### 9.2. Phân biệt “lưu T” với “giải T”

Một type token **có thể lưu** một TypeVariable object. Nhưng lưu tên `T` chưa đủ để biết type argument runtime của lời gọi cụ thể.

Nếu framework cần concrete type, nó phải có **context ánh xạ biến kiểu**, ví dụ một subclass chain cụ thể gắn `T = String`, hoặc caller truyền một `Type`/type token hoàn chỉnh từ nơi biết cấu trúc.

### 9.3. Truyền Type từ caller thay vì tạo token với T bên trong

Thay vì:

```java
static <T> Type wrongExpectation() {
    return new TypeRef<List<T>>() {}.type();
}
```

hãy cung cấp thông tin kiểu cụ thể tại caller:

```java
TypeRef<List<String>> token =
        new TypeRef<List<String>>() {};

// Truyền token.type() vào API nhận Type.
```

Hoặc truyền một `Class<T>`/factory rõ ràng nếu API chỉ cần **class không lồng nhau**. Không nên tin rằng chỉ thấy `<T>` trong method đã đủ phục hồi `T` sau erasure.

## 10. Kế thừa nhiều tầng cần giải type variables theo từng tầng

Xét:

```java
abstract class BaseRef<T> {
}

class Middle<U> extends BaseRef<List<U>> {
}

class Concrete extends Middle<String> {
}
```

Nếu Reflection đọc:

```java
Concrete.class.getGenericSuperclass()
```

kết quả là `Middle<String>`, không trực tiếp là `BaseRef<List<String>>`.

Nếu đọc tiếp khai báo của:

```java
Middle.class.getGenericSuperclass()
```

thì lại thấy `BaseRef<List<U>>`, với `U` là `TypeVariable` của Middle.

Một resolver tổng quát phải kết hợp:

```text
Concrete -> Middle<String>
Middle<U> -> BaseRef<List<U>>
U -> String
Suy ra: BaseRef<List<String>>
```

### 10.1. Vì sao `getGenericSuperclass()` một lần không đủ?

Nó chỉ trả **generic superclass trực tiếp** của class đang hỏi. Khi có nhiều tầng, type arguments có thể nằm ở một class, còn type variable cần thay nằm ở class khác.

Nếu một library tuyên bố hỗ trợ inheritance nhiều cấp, cần xem nó giải `TypeVariable` theo context, xử lý interfaces, owner types, bounds, generic arrays và wildcards ra sao.

### 10.2. Bản `TypeRef` ở đây cố tình chỉ hỗ trợ một tầng

Trong chương trình chạy đầy đủ:

```java
static class Intermediate<U> extends TypeRef<U> {
}

static final class TooDeep
        extends Intermediate<List<String>> {
}
```

Khi tạo `new TooDeep()`, constructor của `TypeRef` lấy direct superclass của `TooDeep`, tức `Intermediate<List<String>>`. Raw type không phải `TypeRef.class`, nên nó chủ động ném:

```text
Use a direct parameterized subclass
```

Đây là cách từ chối **thay vì lưu nhầm** một argument rồi tuyên bố đã giải quyết đúng kiểu. Nếu cần multi-level capture, hãy thay implementation bằng một generic type resolver thật sự, không gỡ bỏ check cho qua.

## 11. Type token khác `Class<T>` token

`Class<T>` rất hữu ích khi kiểu đích là một class có thể kiểm tra runtime:

```java
static <T> T checkedCast(Object value, Class<T> type) {
    return type.cast(value);
}
```

Caller:

```java
String s = checkedCast("Java", String.class);
```

Nhưng không có cú pháp class literal:

```java
// Không hợp lệ:
// List<String>.class
```

`List.class` chỉ biểu đạt runtime class/interface List, không biểu đạt type argument String.

So sánh:

| Mục đích | `Class<T>` | Super type token chứa `Type` |
| --- | --- | --- |
| Biết class `String` | Phù hợp | Cũng mô tả được |
| Mô tả `List<String>` | Không giữ type argument | Giữ được từ declaration của subclass |
| Mô tả `Map<String,List<Integer>>` | Không đủ | Giữ được toàn bộ cấu trúc khai báo |
| Runtime `isInstance` với class | Có | Type không cung cấp tương đương đầy đủ tự động |
| Tạo object bằng reflection | Có thể tra constructor nhưng có thể thất bại | Cần framework hiểu Type và cách tạo object |
| Kiểm tra mọi phần tử List | Không | Cũng **không tự kiểm tra** |
| Xử lý wildcard và type variable | Không đầy đủ | Có các dạng Type đặc thù để mô tả |

**Type token không thay thế `Class<T>` trong mọi tình huống**. Nếu chỉ cần kiểm tra một runtime class đơn giản, Class token thường đủ và dễ dùng hơn.

## 12. Type token không làm một `List<String>` tự an toàn

Giả sử ai đó xây một list bị nhiễm kiểu qua raw alias:

```java
List raw = new ArrayList();
raw.add(42);

@SuppressWarnings("unchecked")
List<String> claimed = (List<String>) raw;
```

Đây là đoạn **cố ý không an toàn** để minh họa; khi bật lint, raw/unchecked operations có thể bị cảnh báo.

Bạn hoàn toàn có thể tạo type token:

```java
Type token = new TypeRef<List<String>>() {}.type();
```

Nhưng token không khiến list `claimed` chứa String. Khi:

```java
String first = claimed.get(0);
```

runtime vẫn có thể ném `ClassCastException`.

Một thư viện deserialize/convert thật sự phải dùng Type token làm **mô tả kiểu đích**, sau đó thực hiện kiểm tra hoặc chuyển đổi từng phần tử theo chính sách của nó.

### 12.1. `Type` là metadata, validation là một quá trình khác

Với token `List<Map<String,Integer>>`, validator có thể cần:

1. Xác nhận value ngoài là một list hoặc có thể chuyển thành list.
2. Xác nhận từng phần tử list là map.
3. Xác nhận key của từng map là String.
4. Xác nhận value của từng map là Integer, hoặc có quy tắc chuyển từ dữ liệu đầu vào.
5. Xây cấu trúc kết quả có generic contract phù hợp.
6. Xử lý null, phần tử thiếu, số không đúng miền, dữ liệu dư, lỗi và vòng đời object.

**Reflection metadata không tự làm các bước này**. Một parser có thể có chính sách khác nhau: reject dữ liệu sai, cố chuyển số, bỏ field không nhận biết, hay trả lỗi chi tiết.

### 12.2. Không coi `ClassCastException` vắng mặt là chứng minh generic type an toàn

Cast runtime từ một Object sang raw `List` chỉ kiểm tra interface/class ngoài. Nếu bên trong có Integer thay vì String, cast generic unchecked không phát hiện cho tới khi code đọc một phần tử và cần String.

Type token là công cụ cho việc **biết phải kiểm tra gì**, không phải phép kiểm tra tổng quát tự động.

## 13. `Type` metadata từ field và method khác gì type token?

Bạn có thể lấy generic type từ khai báo field:

```java
class Model {
    List<Map<String, Integer>> records;
}
```

Reflection:

```java
Field field = Model.class.getDeclaredField("records");
Type type = field.getGenericType();
```

Nếu field declaration cụ thể, `getGenericType()` cũng trả cấu trúc `ParameterizedType` lồng nhau.

Tương tự:

```java
Method method = SomeClass.class.getDeclaredMethod("find");
Type type = method.getGenericReturnType();
```

Lệnh minh họa này giả định có method `find` không đối số. Method return type có thể là ParameterizedType, Class hoặc TypeVariable tùy declaration.

### 13.1. Khi nào nên dùng field/method reflection?

Nếu schema đã tồn tại ở class/record/method cụ thể, Reflection trực tiếp lên declaration có thể đủ. Không phải luôn cần tạo thêm anonymous subclass.

Type token hữu ích khi **call site muốn mô tả một generic type tùy chọn** mà không có một field/method riêng để làm nguồn metadata:

```java
new TypeRef<List<Map<String, Integer>>>() {}.type()
```

### 13.2. Đừng nhầm type được khai báo với type giá trị hiện tại

Nếu field `List<String> values` bị gán một list sai nội dung qua unchecked code, `Field.getGenericType()` vẫn trả đúng kiểu **đã khai báo**. Nó không biết các element hiện tại trong list. Đây là cùng giới hạn của super type token.

## 14. Equality và khả năng tái sử dụng của type descriptions

Hai anonymous subclasses khác nhau có thể khai báo cùng type:

```java
Type a = new TypeRef<List<String>>() {}.type();
Type b = new TypeRef<List<String>>() {}.type();

System.out.println(a.equals(b)); // true
```

Với Reflection types chuẩn, `ParameterizedType.equals` so sánh tính tương đương cấu trúc: raw type, owner type và các actual type arguments. Hai tokens có thể đến từ **hai anonymous classes khác nhau**, nhưng vẫn mô tả cùng `List<String>`.

Ngược lại:

```java
Type x = new TypeRef<List<String>>() {}.type();
Type y = new TypeRef<List<Integer>>() {}.type();

System.out.println(x.equals(y)); // false
```

### 14.1. Không so class của anonymous token instances

Các anonymous subclasses tại hai vị trí source có thể có **class riêng**. So:

```java
aToken.getClass() == bToken.getClass()
```

không phải phép so sánh hợp lý cho kiểu dữ liệu mà chúng mô tả. Hãy so **`Type` metadata** sau khi capture.

### 14.2. Cache theo `Type` cần chú ý scope

Nếu làm cache mapper/adapter theo `Type`, thường cần key bao gồm cấu trúc generic đầy đủ, không chỉ raw class:

```text
List<String>  != List<Integer>
Map<String,Integer> != Map<String,Long>
```

Tuy nhiên đừng tự giả định mọi Type do bên thứ ba implement đều có `equals`/`hashCode` đúng; nếu tự viết `ParameterizedType` implementation, cần tuân thủ hợp đồng của nó. Cũng lưu ý classloaders khác nhau có thể tạo Class objects không tương đương dù tên class giống nhau.

## 15. Chương trình Java 17 hoàn chỉnh

Tạo tệp **`NestedTypeTokenDemo.java`**:

```java
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NestedTypeTokenDemo {
    static abstract class TypeRef<T> {
        private final Type type;

        protected TypeRef() {
            Type parent = getClass().getGenericSuperclass();
            if (!(parent instanceof ParameterizedType p)
                    || p.getRawType() != TypeRef.class) {
                throw new IllegalStateException(
                        "Use a direct parameterized subclass");
            }
            type = Objects.requireNonNull(
                    p.getActualTypeArguments()[0]);
        }

        public final Type type() {
            return type;
        }
    }

    static final class NamedRef extends TypeRef<List<String>> {
    }

    static class Intermediate<U> extends TypeRef<U> {
    }

    static final class TooDeep
            extends Intermediate<List<String>> {
    }

    static <T> Type unboundInMethod(T value) {
        return new TypeRef<List<T>>() {}.type();
    }

    static String category(Type type) {
        if (type instanceof ParameterizedType)
            return "ParameterizedType";
        if (type instanceof GenericArrayType)
            return "GenericArrayType";
        if (type instanceof WildcardType)
            return "WildcardType";
        if (type instanceof TypeVariable<?>)
            return "TypeVariable";
        if (type instanceof Class<?>)
            return "Class";
        return "Unknown";
    }

    public static void main(String[] args) {
        TypeRef<List<Map<String, Integer>>> nested =
                new TypeRef<List<Map<String, Integer>>>() {};

        Type type = nested.type();
        System.out.println("nested=" + type.getTypeName());

        ParameterizedType outer = (ParameterizedType) type;
        System.out.println("outer-raw="
                + outer.getRawType().getTypeName());

        Type map = outer.getActualTypeArguments()[0];
        System.out.println("inner-kind=" + category(map));

        ParameterizedType inner = (ParameterizedType) map;
        System.out.println("map-raw="
                + inner.getRawType().getTypeName());
        System.out.println("map-key="
                + inner.getActualTypeArguments()[0].getTypeName());
        System.out.println("map-value="
                + inner.getActualTypeArguments()[1].getTypeName());

        TypeRef<List<String>> a =
                new TypeRef<List<String>>() {};
        TypeRef<List<String>> b =
                new TypeRef<List<String>>() {};
        TypeRef<List<Integer>> c =
                new TypeRef<List<Integer>>() {};

        System.out.println("type-equal="
                + a.type().equals(b.type()));
        System.out.println("type-different="
                + !a.type().equals(c.type()));
        System.out.println("named="
                + new NamedRef().type().getTypeName());

        Type boundType =
                new TypeRef<List<? extends Number>>() {}.type();
        WildcardType wildcard = (WildcardType)
                ((ParameterizedType) boundType)
                .getActualTypeArguments()[0];

        System.out.println("wildcard-kind="
                + category(wildcard));
        System.out.println("wildcard-upper="
                + wildcard.getUpperBounds()[0].getTypeName());

        Type arrayType =
                new TypeRef<List<String>[]>() {}.type();
        GenericArrayType ga = (GenericArrayType) arrayType;
        System.out.println("array-kind=" + category(arrayType));
        System.out.println("array-component="
                + ga.getGenericComponentType().getTypeName());

        System.out.println("simple-kind="
                + category(new TypeRef<String>() {}.type()));

        Type generic = unboundInMethod("actual String");
        Type nestedArgument = ((ParameterizedType) generic)
                .getActualTypeArguments()[0];

        System.out.println("method-capture="
                + generic.getTypeName());
        System.out.println("type-variable="
                + category(nestedArgument));

        List<String> strings = new ArrayList<>();
        List<Integer> integers = new ArrayList<>();
        System.out.println("same-runtime-class="
                + (strings.getClass() == integers.getClass()));

        try {
            new TooDeep();
        } catch (IllegalStateException ex) {
            System.out.println("indirect-rejected="
                    + ex.getMessage());
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -Xlint:all NestedTypeTokenDemo.java
java NestedTypeTokenDemo
```

Mã trên đã được biên dịch sạch với Java 17 và `-Xlint:all`, không có warnings. **Stdout chính xác**:

```text
nested=java.util.List<java.util.Map<java.lang.String, java.lang.Integer>>
outer-raw=java.util.List
inner-kind=ParameterizedType
map-raw=java.util.Map
map-key=java.lang.String
map-value=java.lang.Integer
type-equal=true
type-different=true
named=java.util.List<java.lang.String>
wildcard-kind=WildcardType
wildcard-upper=java.lang.Number
array-kind=GenericArrayType
array-component=java.util.List<java.lang.String>
simple-kind=Class
method-capture=java.util.List<T>
type-variable=TypeVariable
same-runtime-class=true
indirect-rejected=Use a direct parameterized subclass
```

### 15.1. Phân tích nhóm `nested`, `outer-raw`, `map-*`

Kết quả hiển thị một `ParameterizedType` lồng nhau, chứ không chỉ raw `List`. Duyệt `getActualTypeArguments()[0]` từ List xuống Map, rồi từ Map tới String/Integer giúp phục hồi đúng cấu trúc được **khai báo trong type token**.

### 15.2. `type-equal=true`, `type-different=true`

Hai anonymous subclasses khác nhau nhưng mô tả cùng `List<String>` có `Type` metadata bằng nhau về cấu trúc. `List<Integer>` khác `List<String>` ở generic argument nên không cùng `Type`.

### 15.3. `named=...`

`NamedRef` là một class bình thường được khai báo `extends TypeRef<List<String>>`. Nó cũng cung cấp superclass signature có thể đọc được, không bắt buộc sử dụng anonymous class.

### 15.4. `wildcard-kind`, `array-kind`, `simple-kind`

Ba kết quả cho thấy không thể cast mọi Type thành ParameterizedType:

- Wildcard bên trong `List<? extends Number>` được biểu diễn bởi `WildcardType`.
- `List<String>[]` được biểu diễn bởi `GenericArrayType`.
- `String` là `Class`.

Mỗi loại cần được diễn giải phù hợp với semantics Java.

### 15.5. `method-capture` và `type-variable`

Dù caller truyền `"actual String"` vào method generic, Reflection vẫn thấy `List<T>`, với argument bên trong thuộc loại `TypeVariable`. Đây là bằng chứng rằng type token không reify `T` của mỗi generic method invocation.

### 15.6. `same-runtime-class=true`

Hai `ArrayList` khác type arguments vẫn có cùng runtime class, bởi Generics không tạo runtime specialization riêng cho từng type argument. Type token không thay đổi điều này.

### 15.7. `indirect-rejected=...`

Bản `TypeRef` đơn giản chỉ chấp nhận direct parameterized subclass. Lớp `TooDeep` có direct superclass `Intermediate<List<String>>`, nên constructor từ chối để tránh ghi nhận nhầm `Type` chưa được resolve đúng bối cảnh.

## 16. Ví dụ cố ý không biên dịch: generic class literal

Tạo file **`InvalidClassLiteral.java`**:

```java
import java.util.List;

class InvalidClassLiteral {
    Class<List<String>> token = List<String>.class;
}
```

Biên dịch:

```bash
javac --release 17 InvalidClassLiteral.java
```

Compiler báo lỗi cú pháp tại phần type arguments trước `.class`, với nội dung chính:

```text
error: <identifier> expected
```

Đoạn này **cố tình sai**. Java không cho `List<String>.class` như một class literal specialized.

Sửa tùy mục tiêu:

```java
Class<?> rawClass = List.class;
```

nếu chỉ cần runtime class `List`.

Hoặc dùng:

```java
Type nestedType =
        new TypeRef<List<String>>() {}.type();
```

nếu cần mô tả đầy đủ `List<String>` cho một API nhận `Type`. Đoạn sửa này giả định bạn đã có class `TypeRef` như chương trình hoàn chỉnh.

## 17. Các lỗi thường gặp khi tự viết TypeRef

### 17.1. Quên tạo subclass

Một class `TypeRef` concrete cho phép `new TypeRef<List<String>>()` thông thường có thể không lưu type argument ở runtime class. Constructor lúc đó chỉ thấy superclass của chính `TypeRef`, không thấy declaration `extends TypeRef<List<String>>`.

Giải pháp: tạo anonymous/named subclass có superclass tham số hóa cụ thể. Trong ví dụ này `TypeRef` abstract để caller không tạo instance trực tiếp.

### 17.2. Quên `{}` sau biểu thức tạo anonymous subclass

```java
new TypeRef<List<String>>() {};
```

Dấu `{}` là cú pháp tạo anonymous class body. Nếu sửa thành:

```java
new TypeRef<List<String>>();
```

với abstract TypeRef sẽ không biên dịch. Nếu TypeRef là concrete, nó vẫn không khai báo subclass mới mang generic superclass signature theo cách kỹ thuật này cần.

### 17.3. Ép `getGenericSuperclass()` luôn thành ParameterizedType

Không an toàn:

```java
ParameterizedType p =
        (ParameterizedType) getClass().getGenericSuperclass();
```

Nếu superclass raw, không phải generic declaration như dự kiến, hoặc class hierarchy khác, bạn có thể nhận `ClassCastException` hoặc dữ liệu không đúng.

Nên kiểm tra:

```java
if (!(parent instanceof ParameterizedType p)) {
    throw new IllegalStateException("Expected generic superclass");
}
```

Và nếu implementation chỉ hỗ trợ direct subclass, kiểm tra raw type cụ thể như ví dụ.

### 17.4. Lấy argument đầu rồi tưởng đã có Class

```java
Type t = p.getActualTypeArguments()[0];
```

`t` có thể là `ParameterizedType`, `TypeVariable`, `WildcardType`, `GenericArrayType` hoặc `Class`. Không ép thẳng thành `(Class<?>)` nếu chưa kiểm tra.

Với `List<Map<String,Integer>>`, argument ngoài là **Map parameterized**, không phải Class `Map` chứa luôn arguments.

### 17.5. Bỏ qua owner types của nested classes

Với nested/member generic classes, phần owner có thể là một `Type` có cấu trúc generic riêng. Nếu xây resolver tổng quát, phải xét `ParameterizedType.getOwnerType()` theo quy tắc của class cụ thể.

Đừng nhầm owner type với argument của List hoặc Map, và cũng đừng loại bỏ owner metadata một cách tùy tiện khi so sánh hoặc chuyển đổi type.

### 17.6. Dùng `Type.getTypeName()` làm định danh duy nhất

Tên có thể tiện cho log, nhưng hai class loaders khác nhau có thể nạp những lớp cùng tên không phải cùng `Class` identity. Một cache/reflection resolver nên có key dựa trên `Type`/class identity và hợp đồng phù hợp, không chỉ một string hiển thị.

### 17.7. Nghĩ `TypeRef<List<T>>` trong generic method là đã resolve T

Không. Nó có thể chỉ lưu `TypeVariable T`. Truyền một Type cụ thể từ caller, hoặc dùng resolver có context type mappings, thay vì tuyên bố đã capture String từ giá trị runtime.

### 17.8. Giả định type token tự validate dữ liệu

Không. Nếu một raw alias đưa Integer vào list khai báo String, token cho List<String> không tự sửa và không kiểm tra. Bảo đảm dữ liệu thuộc một generic type đòi code parse/validation thực sự.

### 17.9. Viết API chỉ nhận `Class<T>` rồi cố ép dữ liệu nested vào

`Class<List>` không chứa `String`, và `Class<Map>` không chứa key/value arguments. Nếu mục tiêu là API deserialize generic structures, định nghĩa đầu vào nhận `Type`/type token hoặc loại abstraction tương đương.

### 17.10. Dùng type token làm factory tạo object

`TypeRef<List<String>>` chỉ lưu Type, không có phương thức tạo `ArrayList<String>` hoặc giải constructor cho mọi generic type. Nếu muốn tạo object, cần quy tắc chọn implementation, constructor, dependencies và chính sách dữ liệu riêng.

### 17.11. Xem reflection Signature là nguồn dữ liệu luôn hoàn chỉnh

Generic metadata mô tả những declaration đã biên dịch. Class file tạo từ nguồn không tiêu chuẩn, bytecode bị sửa hoặc generic hierarchy quá phức tạp có thể tạo trường hợp Reflection không đúng kỳ vọng hoặc báo các ngoại lệ liên quan malformed/unresolvable types.

Một framework robust cần có xử lý lỗi, không cast cứng mọi Type thành một dạng duy nhất.

### 17.12. Dùng type token cho mục đích compile-time type safety rồi bỏ qua unchecked warnings

Type token hữu ích để mang metadata qua API; nhưng phép cast unchecked từ Object thành `List<String>` vẫn cần chứng minh invariant. Không coi `@SuppressWarnings("unchecked")` là hành vi validate.

## 18. Type token trong thiết kế JSON/deserialization

Một API giả định có thể trông như:

```java
<T> T fromJson(String json, TypeRef<T> targetType);
```

Với cú pháp:

```java
List<Map<String, Integer>> result =
        fromJson(json,
            new TypeRef<List<Map<String, Integer>>>() {});
```

Đây là **chữ ký và lời gọi minh họa**, không phải API có sẵn của JDK. Nó cần một library/parser implement thực sự.

Một implementation có thể dùng `targetType.type()` để duyệt cấu trúc kiểu rồi quyết định:

- Dùng loại collection implementation nào.
- Dùng adapter nào cho key/value.
- Có chấp nhận null không.
- Làm gì nếu JSON chứa số không phù hợp Integer.
- Có xử lý wildcard/TypeVariable chưa resolve không.
- Trả lỗi cụ thể khi schema không phù hợp ra sao.

Các thư viện khác nhau có API tên khác nhau, ví dụ họ `TypeToken`, `TypeReference`, `ParameterizedTypeReference`; cần tra chính xác phiên bản/documentation của thư viện đang dùng. Kỹ thuật cốt lõi giống nhau: **đưa một mô tả generic Type từ declaration sang runtime logic**.

### 18.1. Kiểm tra thư viện có hỗ trợ `Type` lồng nhau

Không phải mọi parser hỗ trợ tất cả các dạng:

- `Class<?>`.
- `ParameterizedType` nhiều tầng.
- `WildcardType`.
- `TypeVariable<?>` chưa được resolve.
- `GenericArrayType`.
- Owner type của nested class.

Một API nhận `Type` chưa bảo đảm implementation xử lý được mọi dạng Type. Hãy đọc contract và test cấu trúc thực tế cần dùng.

### 18.2. Tránh “JSON đã parse thành Object rồi unchecked cast”

Nếu parser chỉ tạo `Object` hoặc raw `Map`, cast tới `Map<String, List<Integer>>` không xác minh generic arguments. Nên truyền type token ngay lúc parse để converter có metadata cần thiết, hoặc validate và copy rõ từng cấp sau parse.

## 19. Thiết kế helper duyệt cây Type đúng mức

Một helper chẩn đoán có thể phân nhánh:

```java
static String kind(Type type) {
    if (type instanceof ParameterizedType)
        return "ParameterizedType";
    if (type instanceof GenericArrayType)
        return "GenericArrayType";
    if (type instanceof WildcardType)
        return "WildcardType";
    if (type instanceof TypeVariable<?>)
        return "TypeVariable";
    if (type instanceof Class<?>)
        return "Class";
    return "Unknown";
}
```

Đoạn này cần imports của Reflection và được đặt trong class. Nó chỉ phân loại type; chưa phải serializer hoặc validator.

Khi viết một **resolver tổng quát**, các nhánh có thể tiếp tục:

- Với `ParameterizedType`: duyệt owner, raw type và actual arguments.
- Với `GenericArrayType`: duyệt component type.
- Với `WildcardType`: duyệt upper/lower bounds.
- Với `TypeVariable`: duyệt bounds và tra cứu mapping theo generic declaration/context.
- Với `Class`: xét array component qua `getComponentType()` nếu class là mảng.

### 19.1. Cẩn thận với type graphs và vòng lặp

Một type variable có thể có recursive bound:

```java
class Node<T extends Comparable<T>> {
}
```

Nếu walker đệ quy không theo dõi loại Type đã thăm hoặc không có quy tắc dừng, nó có thể lặp lại khi đi từ TypeVariable tới bounds rồi trở về TypeVariable.

Một implementation hoàn chỉnh cần chính sách cycle detection và mapping kiểu, thay vì chỉ gọi đệ quy vô hạn qua mọi bound.

### 19.2. Không tự xây generic type parser nếu chỉ cần dùng library

Khi code ứng dụng chỉ muốn chuyển JSON thành một cấu trúc lồng nhau, dùng type token mà library hỗ trợ thường đủ. Tự implement mọi nhánh Type, owner, wildcard, variable substitution dễ tạo lỗi tinh tế.

Bạn nên hiểu metadata để debug và thiết kế API, nhưng không nhất thiết phải viết một generic framework mới cho từng dự án.

## 20. Những hiểu lầm quan trọng

| Khẳng định | Đúng hay sai? | Vì sao |
| --- | --- | --- |
| `new TypeRef<List<String>>() {}` giữ được metadata List<String>. | Đúng, với TypeRef đọc đúng superclass signature. | Anonymous subclass khai báo generic superclass. |
| `List<String>.class` hợp lệ. | Sai. | Java không có specialized class literal kiểu đó. |
| `List.class` đủ mô tả List<Map<String,Integer>>. | Sai. | Nó chỉ mô tả raw runtime class/interface. |
| `new TypeRef<List<T>>() {}` trong generic method luôn biết T=String ở caller. | Sai. | Metadata có thể vẫn chứa TypeVariable T. |
| Type token tự chứng minh mọi phần tử list là String. | Sai. | Metadata và validation dữ liệu là hai việc khác nhau. |
| `Type` luôn cast được thành `Class`. | Sai. | Có ParameterizedType/WildcardType/TypeVariable/GenericArrayType. |
| `TypeRef<List<String>[]>` lưu được mô tả generic array. | Đúng. | Reflection có GenericArrayType. |
| Có thể `new List<String>[3]` nhờ dùng TypeRef. | Sai. | Java generic array creation vẫn bị giới hạn. |
| Hai anonymous subclasses khác nhau có thể biểu diễn `Type` bằng nhau. | Đúng. | Cùng cấu trúc generic declaration. |
| Lớp con nhiều tầng chỉ cần `getGenericSuperclass()` một lần. | Sai. | Thường cần thay thế type variables qua hierarchy. |

## 21. Câu hỏi thực hành có đáp án

### Bài 1. Vì sao không dùng `List<Map<String,Integer>>.class`?

<details>
<summary>Đáp án</summary>

Java không tạo một runtime Class object chuyên biệt theo mọi type arguments của Generics. Class literal chỉ có thể viết ở những dạng ngôn ngữ cho phép, không có dạng parameterized specialized như `List<String>.class`. Dùng `List.class` nếu chỉ cần raw class, hoặc TypeRef/Type token nếu muốn mô tả generic structure lồng nhau.

</details>

### Bài 2. Dấu `{}` trong `new TypeRef<List<String>>() {}` có tác dụng gì?

<details>
<summary>Đáp án</summary>

Nó tạo anonymous subclass của TypeRef, superclass được khai báo là `TypeRef<List<String>>`. Generic superclass signature của class con là nguồn metadata mà Reflection có thể đọc.

</details>

### Bài 3. `getClass()` của `new ArrayList<String>()` có cho biết String không?

<details>
<summary>Đáp án</summary>

Không. Runtime class của object thông thường là `ArrayList`, không có specialization riêng cho type argument String. Type token hoạt động nhờ superclass metadata **trên class con được tạo**, không nhờ runtime contents của ArrayList.

</details>

### Bài 4. Type bên ngoài của `List<Map<String,Integer>>` thuộc nhóm nào?

<details>
<summary>Đáp án</summary>

`ParameterizedType` của List. Argument duy nhất là một `ParameterizedType` khác của Map; raw type của Map là Map, với arguments String và Integer.

</details>

### Bài 5. Vì sao `getActualTypeArguments()[0]` không luôn là Class?

<details>
<summary>Đáp án</summary>

Vì một generic argument có thể là kiểu tham số hóa, wildcard, type variable hoặc generic array. Chẳng hạn argument của `List<Map<String,Integer>>` là `ParameterizedType`, không phải `Map.class` chứa tự động key/value arguments.

</details>

### Bài 6. `new TypeRef<List<T>>() {}` trong method `<T>` có đảm bảo biết subtype cụ thể caller chọn không?

<details>
<summary>Đáp án</summary>

Không. Nó có thể lưu `List<T>` với TypeVariable T chưa resolve. Muốn kiểu cụ thể như List<String>, caller cần cung cấp Type token cụ thể hoặc framework phải có context ánh xạ T.

</details>

### Bài 7. Vì sao TypeRef của chương trình trên từ chối `TooDeep`?

<details>
<summary>Đáp án</summary>

Constructor kiểm tra direct superclass có raw type chính là TypeRef. `TooDeep` trực tiếp extends `Intermediate<List<String>>`, không phải TypeRef. Hỗ trợ nhiều tầng cần resolver thay thế generic variables theo chain kế thừa.

</details>

### Bài 8. Type token cho `List<? extends Number>` có biết list thực chứa Integer hay Double không?

<details>
<summary>Đáp án</summary>

Không. Wildcard mô tả upper bound Number, không ghi specialization runtime của từng list object. Có thể dùng `WildcardType` để đọc bound, còn dữ liệu thực phải được kiểm tra riêng nếu cần.

</details>

### Bài 9. Có thể giữ generic array type dù không tạo được `new List<String>[n]` không?

<details>
<summary>Đáp án</summary>

Có. `TypeRef<List<String>[]>` có thể giữ metadata `GenericArrayType`, nhưng điều đó không làm generic array creation với non-reifiable component trở nên hợp lệ.

</details>

### Bài 10. Muốn biết JSON có đúng kiểu `Map<String,List<Integer>>` cần làm gì?

<details>
<summary>Đáp án</summary>

Cung cấp một `Type` mô tả cấu trúc mong đợi cho parser/validator hỗ trợ kiểu lồng nhau, rồi đảm bảo parser/validator thực sự kiểm tra/chuyển đổi cả key, value, collection và từng phần tử. Type token chỉ chuyển metadata, không tự validate JSON hay Object graph.

</details>

## 22. Checklist áp dụng vào dự án

1. **Xác định yêu cầu thực sự:** chỉ cần `Class<T>`, hay cần giữ `List<Map<String,Integer>>`?
2. Nếu cần kiểu lồng nhau, tạo token từ **nơi biết concrete type arguments**, ví dụ `new TypeRef<List<Map<String,Integer>>>() {}`.
3. Xem `getGenericSuperclass()` trả Class hay ParameterizedType; không cast mù.
4. Duyệt cây Type theo từng cấp, thay vì cắt chuỗi `getTypeName()` bằng tay.
5. Nhận diện `TypeVariable` chưa resolve; đừng quảng cáo nó là concrete `String.class`.
6. Nếu cần lớp con nhiều tầng, dùng resolver có substitution mapping thay vì một phép Reflection đơn giản.
7. Nếu làm JSON/deserialization, kiểm tra thư viện hỗ trợ Type lồng nhau, wildcard và null policy ra sao.
8. Tách **metadata** khỏi **validation**, tránh unchecked cast dữ liệu ngoài mà không kiểm tra.
9. Khi cache type-based adapters, phân biệt đầy đủ type arguments, owner type và class loader context nếu có.
10. Dùng Java 17 để biên dịch ví dụ/kiểm thử cả nested type, named subclass, wildcard, generic array và unresolved type variables.
11. Nếu API chỉ yêu cầu một runtime class cụ thể, `Class<T>` vẫn là công cụ đơn giản và thích hợp.
12. Không đọc generic metadata của một instance rồi mặc định rằng tất cả dữ liệu hiện tại phù hợp với declaration.

## 23. Tổng kết

**Super type token lưu thông tin generic lồng nhau bằng cách khai báo một subclass có superclass được tham số hóa cụ thể.** Với:

```java
new TypeRef<List<Map<String, Integer>>>() {}
```

anonymous subclass có metadata cho `TypeRef<List<Map<String,Integer>>>`, nên Reflection có thể lấy lại Type của `List<Map<String,Integer>>` và duyệt các nested `ParameterizedType` tới `String`/`Integer`.

Kỹ thuật này đặc biệt hữu ích khi API cần một **mô tả kiểu đầy đủ** chứ không chỉ `List.class`. Nhưng cần nhớ ba giới hạn:

- **Type erasure vẫn còn**: type token không làm mỗi `List<String>` trở thành một runtime class riêng.
- **TypeVariable có thể chưa resolve**: `List<T>` trong generic method không tự biến thành `List<String>` ở mỗi lời gọi.
- **Metadata không phải validation**: parser/validator phải thật sự đọc và kiểm tra dữ liệu theo schema đã mô tả.

Dùng TypeRef trực tiếp và đơn giản cho call sites có concrete generic structure; dùng một resolver/framework đủ năng lực nếu phải xử lý inheritance nhiều tầng, owner type, wildcard hoặc type variables. Khi hiểu đúng nguồn metadata và giới hạn của nó, bạn có thể truyền thông tin kiểu lồng nhau vào Reflection, serialization và data mapping mà không nhầm lẫn với runtime type checks.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-reflection-009">Giới hạn phục hồi T chưa được ràng buộc và chi phí xử lý siêu dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn phục hồi `T` chưa được ràng buộc và chi phí xử lý siêu dữ liệu kiểu trong Java

## Mục tiêu học tập

Java Generics cho phép viết `Box<T>`, `List<String>` và những thuật toán chỉ cần một implementation để hoạt động với nhiều kiểu. Nhưng khi chương trình cần **phản chiếu kiểu (type reflection)**, hai câu hỏi tưởng giống nhau thực ra khác hẳn:

1. “Lớp/field/method này **được khai báo** dùng kiểu gì?” — reflection có thể đọc một phần generic metadata của khai báo.
2. “Với **instance này**, type variable `T` thực sự đã được ràng buộc thành class nào ở nơi tạo/giữ object?” — thường **không thể phục hồi** nếu không có một nguồn ràng buộc tường minh.

Ngoài giới hạn thông tin, việc đi qua class hierarchy, duyệt `Type`, giải quyết `TypeVariable`, tạo/cấu trúc lại `ParameterizedType` và quét nhiều field bằng reflection có chi phí xử lý. Không nên làm việc ấy liên tục trên đường xử lý nóng (*hot path*) nếu có thể tính một lần rồi tái sử dụng metadata.

Sau bài này, bạn sẽ:

- Phân biệt runtime `Class<?>`, `Type`, `TypeVariable`, `ParameterizedType` và **type argument cụ thể của một chỗ sử dụng**.
- Chứng minh `Box<String>` và `Box<Integer>` trực tiếp đều dùng runtime class `Box`, không có token `T.class` từ generic type variable.
- Giải thích khi nào subclass hoặc anonymous subclass giữ `Signature` chứa `String`, và vì sao generic factory `<X>` lại chỉ giữ `X`.
- Thấy trường hợp **thừa kế nhiều tầng** cần map biến kiểu giữa các tầng trước khi suy ra type argument.
- Dùng `Class<T>` hoặc type reference được khai báo ở ngữ cảnh cụ thể để mang metadata chủ động.
- Hiểu `Type`/token kiểu không đồng nghĩa **kiểm tra dữ liệu lồng nhau**.
- Phân biệt chi phí truy vấn metadata, chi phí resolve hierarchy, chi phí kiểm tra từng phần tử, chi phí cache và chi phí class-loading.
- Dùng `ClassValue` cho cache theo runtime class, biết giới hạn của cache và những sai lầm khi benchmark.

**Yêu cầu:** Java 17+ (`javac`, `java`, `javap`), không dùng thư viện ngoài. Ba chương trình chạy độc lập đã được biên dịch với `-Xlint:all`; output dưới đây là kết quả đã xác minh, trừ những đoạn được ghi rõ chỉ minh họa hoặc cố tình không biên dịch.

## 1. Bốn tầng thông tin kiểu cần tách bạch

Giả sử có khai báo:

```java
class Box<T> {
    private final T value;
    Box(T value) { this.value = value; }
    T get() { return value; }
}
```

Và code sử dụng:

```java
Box<String> words = new Box<>("Java");
Box<Integer> count = new Box<>(42);
```

Hãy phân biệt:

| Tầng | Ví dụ | Có thể hỏi bằng API nào? | Giới hạn |
| --- | --- | --- | --- |
| Runtime class của object | `words.getClass()` | `Object.getClass()` | Chỉ thấy class thực `Box`, không biết `String` |
| Generic parameter **khai báo** | `T` trong `class Box<T>` | `Class.getTypeParameters()` | Nhận biến `T`, không phải binding của mọi instance |
| Generic type của một **declaration** | Field `T value`, `extends Box<String>` | `Field.getGenericType()`, `Class.getGenericSuperclass()` | Đọc chữ ký đã được lưu tại khai báo cụ thể |
| Kiểu của **giá trị đang giữ** | `words.get()` chứa `String` | `value.getClass()` nếu khác null | Không chứng minh `T`: có thể null hoặc subtype |

Ở source, compiler dùng `String` để bảo vệ lời gọi với `Box<String>`. Nhưng với class generic thông thường, JVM không tạo phiên bản runtime `Box<String>` và `Box<Integer>` độc lập. Chúng dùng cùng định nghĩa runtime class `Box`.

Điểm mấu chốt: **metadata của một khai báo khác với dữ liệu gắn trên mỗi instance**. Chẳng hạn một field được khai báo `Box<String> field` có thể giữ metadata kiểu của **field**; sau khi lấy riêng object rồi chỉ gọi `getClass()`, object ấy không tự tiết lộ rằng người dùng từng gắn nó vào field như vậy.

## 2. Type erasure không có phép đảo ngược tổng quát

Với type variable `T` không có bound tường minh, bound hiệu dụng là `Object`:

```java
class Box<T> {
    private T value;
    T get() { return value; }
}
```

Ở cấp JVM descriptor, vị trí `T` dùng dạng erased `Object`:

```text
Field T value             -> descriptor: Ljava/lang/Object;
Method T get()            -> descriptor: ()Ljava/lang/Object;
Constructor Box(T)        -> descriptor: (Ljava/lang/Object;)V
```

Class file có thể chứa thuộc tính `Signature` để công cụ reflection thấy rằng field được khai báo là `T`. Nhưng **`Signature` chứa chữ `T` không phải một bảng tra `T -> String` riêng cho từng object**.

Nếu `T extends Number`, erasure của vị trí dùng `T` thường là upper bound đầu tiên, ở đây là `Number`. Điều đó cũng **không** giúp đoán một instance dùng `Integer`, `Double` hay `BigDecimal`: một bound chỉ cho biết tập ràng buộc, không chỉ ra type argument cụ thể.

Về nguyên lý, nhiều lựa chọn ở source có thể cùng biểu diễn bằng một runtime class. Vì vậy, nếu chương trình **không lưu binding ở đâu cả**, reflection không có dữ liệu để “đảo ngược” type erasure. Không thể cải thiện giới hạn ấy chỉ bằng việc gọi nhiều API reflection hơn hoặc dựng thuật toán tìm class phức tạp hơn.

## 3. Những cú pháp bị cấm khi `T` chưa reifiable

Trong một class generic, các thao tác sau **cố ý không biên dịch**:

```java
class IllegalTypeRecovery<T> {
    Class<?> type() { return T.class; }
    T create() { return new T(); }
    boolean check(Object value) { return value instanceof T; }
}
```

Đây là tệp minh họa **negative test**, không phải chương trình cần chạy. Thử lưu tên `IllegalTypeRecovery.java` rồi dùng:

```bash
javac IllegalTypeRecovery.java
```

Trên trình biên dịch Java đã kiểm chứng, các lỗi tương ứng là:

```text
error: cannot select from a type variable
error: unexpected type
error: Object cannot be safely cast to T
3 errors
```

Vì `T` không là một runtime class literal cụ thể nên:

- Không thể viết `T.class` để lấy token.
- Không thể gọi `new T()` khi không biết class/constructor thực.
- Không thể kiểm tra `value instanceof T` với một type variable không reifiable.

Không nên thay các dòng ấy bằng unchecked casts rồi xem như đã phục hồi `T`. Nếu cần tạo/check đối tượng runtime, hãy truyền một `Class<T>` hoặc factory có hợp đồng phù hợp ngay từ chỗ gọi.

## 4. Chương trình A — Đọc metadata nào còn và metadata nào không còn

Tạo tệp **`TypeRecoveryDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;

public class TypeRecoveryDemo {
    static class Box<T> {
        private final T value;
        Box(T value) { this.value = value; }
        T get() { return value; }
    }

    static final class NamedStringBox extends Box<String> {
        NamedStringBox(String value) { super(value); }
    }

    static class GenericChild<U> extends Box<List<U>> {
        GenericChild(List<U> value) { super(value); }
    }

    static final class ConcreteChild extends GenericChild<String> {
        ConcreteChild(List<String> value) { super(value); }
    }

    abstract static class TypeRef<T> {
        private final Type type;

        protected TypeRef() {
            Type parent = getClass().getGenericSuperclass();
            if (!(parent instanceof ParameterizedType p)) {
                throw new IllegalStateException(
                        "Expected parameterized parent");
            }
            type = p.getActualTypeArguments()[0];
        }

        Type type() { return type; }
    }

    static <X> TypeRef<X> makeRef(X value) {
        return new TypeRef<X>() {};
    }

    public static void main(String[] args)
            throws NoSuchFieldException {
        Box<String> text = new Box<>("Java");
        Box<Integer> number = new Box<>(42);
        System.out.println("same runtime class: "
                + (text.getClass() == number.getClass()));
        System.out.println("runtime class: "
                + text.getClass().getSimpleName());

        TypeVariable<?> declared = Box.class.getTypeParameters()[0];
        Field field = Box.class.getDeclaredField("value");
        System.out.println("type variable: " + declared.getName());
        System.out.println("upper bound: "
                + declared.getBounds()[0].getTypeName());
        System.out.println("erased field: "
                + field.getType().getSimpleName());
        System.out.println("declaration type: "
                + field.getGenericType().getTypeName());
        System.out.println("values: "
                + text.get().getClass().getSimpleName() + ", "
                + number.get().getClass().getSimpleName());
        System.out.println("null has no class: "
                + (new Box<String>(null).get() == null));

        System.out.println("fixed parent: "
                + NamedStringBox.class
                        .getGenericSuperclass().getTypeName());
        System.out.println("intermediate parent: "
                + GenericChild.class
                        .getGenericSuperclass().getTypeName());
        System.out.println("concrete parent: "
                + ConcreteChild.class
                        .getGenericSuperclass().getTypeName());

        Type concreteRef = new TypeRef<List<String>>() {}.type();
        Type unresolvedRef = makeRef("hello").type();
        System.out.println("captured type: "
                + concreteRef.getTypeName());
        System.out.println("generic factory type: "
                + unresolvedRef.getTypeName());
        System.out.println("factory unresolved: "
                + (unresolvedRef instanceof TypeVariable<?>));
    }
}
```

Chạy:

```bash
javac -Xlint:all TypeRecoveryDemo.java
java TypeRecoveryDemo
```

**Stdout đã kiểm chứng:**

```text
same runtime class: true
runtime class: Box
type variable: T
upper bound: java.lang.Object
erased field: Object
declaration type: T
values: String, Integer
null has no class: true
fixed parent: TypeRecoveryDemo$Box<java.lang.String>
intermediate parent: TypeRecoveryDemo$Box<java.util.List<U>>
concrete parent: TypeRecoveryDemo$GenericChild<java.lang.String>
captured type: java.util.List<java.lang.String>
generic factory type: X
factory unresolved: true
```

Không có cảnh báo từ `javac -Xlint:all` với chương trình này.

## 5. Diễn giải kết quả: runtime class không mang `T` của mỗi object

Hai dòng đầu:

```text
same runtime class: true
runtime class: Box
```

được tạo khi hai **instance riêng biệt** `new Box<>("Java")` và `new Box<>(42)` cùng trả về runtime class `Box`. `getClass()` cho biết class thực đã tạo object chứ không khôi phục cách biến tham chiếu được tham số hóa ở source.

Các dòng kế:

```text
type variable: T
upper bound: java.lang.Object
erased field: Object
declaration type: T
```

minh họa hai lớp metadata:

- `Box.class.getTypeParameters()[0]` là `TypeVariable` biểu diễn **tham số khai báo `T`**. `getBounds()` cho biết `Object` khi không có bound khác.
- `Field.getType()` đọc **erased runtime field type**: `Object`.
- `Field.getGenericType()` đọc **generic type được khai báo cho field**: `T`.

Nhìn thấy `T` trong phản chiếu là **bằng chứng về chữ ký generic**, không phải bằng chứng rằng `T` của instance `text` đã được giải thành `String`.

## 6. Vì sao không suy từ giá trị đang chứa?

Chương trình in:

```text
values: String, Integer
null has no class: true
```

Ta có thể gọi `getClass()` trên đối tượng `text.get()` vì giá trị hiện tại là một `String`. Nhưng điều đó **không xác minh type argument của `Box`**.

Ví dụ:

```java
Box<CharSequence> a = new Box<>("Java");
Box<Number> b = new Box<>(Integer.valueOf(7));
```

Đây là đoạn minh họa dùng `Box` trong cùng ngữ cảnh class. Nếu gọi `a.get().getClass()`, kết quả là `String.class` dù type argument là `CharSequence`; còn `b.get().getClass()` là `Integer.class` dù type argument là `Number`.

Thêm các trường hợp khác:

- Một `Box<String>` có thể chứa `null`: không có object nào để hỏi runtime class.
- Một `Box<Object>` mutable có thể lần lượt chứa `String`, `Integer` rồi `List`.
- Một collection generic có thể rỗng hoặc chứa subtype của kiểu đã khai báo.
- Raw types và unchecked casts có thể làm dữ liệu không còn khớp với hợp đồng generic.

Do đó, “nhìn phần tử đầu tiên rồi chọn `T`” chỉ là **heuristic không đáng tin**. Nếu một API bắt buộc biết `T` chính xác, phải mang binding vào API bằng một token, một khai báo có metadata cụ thể, hoặc schema được cung cấp từ đầu.

## 7. Khi `getGenericSuperclass()` có thể cho biết kiểu cụ thể

Trong chương trình A:

```java
static final class NamedStringBox extends Box<String> {
    NamedStringBox(String value) { super(value); }
}
```

Khi compiler tạo class `NamedStringBox`, quan hệ **`extends Box<String>`** là một phần của **khai báo lớp con**. Generic `Signature` metadata có thể lưu quan hệ ấy để reflection xem:

```java
Type parent = NamedStringBox.class.getGenericSuperclass();
System.out.println(parent.getTypeName());
```

Với tệp `TypeRecoveryDemo.java`, kết quả:

```text
TypeRecoveryDemo$Box<java.lang.String>
```

Điều này **không mâu thuẫn với erasure**: metadata về khai báo `NamedStringBox` có `String`, còn JVM descriptors của các phương thức kế thừa vẫn dùng erased types.

Hai cách tạo phải được phân biệt:

```java
Box<String> direct = new Box<>("A");
Box<String> viaSubclass = new NamedStringBox("B");
```

- `direct.getClass()` là `Box.class`, không có subclass declaration để tìm `String`.
- `viaSubclass.getClass()` là `NamedStringBox.class`. Từ đó có thể đọc chính khai báo `extends Box<String>` của lớp con.

Nếu một object được chuyển qua nhiều biến tham chiếu có kiểu generic khác nhau mà runtime class không mang ràng buộc phù hợp, **không có cách reflection tổng quát** để khôi phục kiểu từng biến đã giữ nó.

## 8. Kế thừa nhiều tầng: metadata tồn tại nhưng chưa được giải hết

Ví dụ của chương trình A:

```java
static class GenericChild<U> extends Box<List<U>> {
    GenericChild(List<U> value) { super(value); }
}

static final class ConcreteChild extends GenericChild<String> {
    ConcreteChild(List<String> value) { super(value); }
}
```

Output:

```text
intermediate parent: TypeRecoveryDemo$Box<java.util.List<U>>
concrete parent: TypeRecoveryDemo$GenericChild<java.lang.String>
```

Hai lời gọi **khác nhau**:

- `GenericChild.class.getGenericSuperclass()` mô tả lớp cha trực tiếp của `GenericChild`: `Box<List<U>>`.
- `ConcreteChild.class.getGenericSuperclass()` mô tả lớp cha trực tiếp của `ConcreteChild`: `GenericChild<String>`.

Nếu bạn muốn tìm `Box<List<String>>` từ `ConcreteChild`, không thể chỉ lấy một `getGenericSuperclass()` rồi ép mù `getActualTypeArguments()[0]` thành `Class`.

Một resolver có căn cứ phải:

1. Đọc `ConcreteChild extends GenericChild<String>`.
2. Ghi binding `U` (được khai báo trong `GenericChild<U>`) thành `String`.
3. Đi tiếp tới `GenericChild extends Box<List<U>>`.
4. Thay `U` trong **cấu trúc `List<U>`** bằng binding `String` để thu được `List<String>`.
5. Bảo toàn owner types, nested parameterized types, wildcard và generic arrays nếu hierarchy có sử dụng chúng.

Đó là công việc **phân tích khai báo + giải thế type variables**, không phải “khôi phục type argument từ object” nếu thông tin vốn đã mất.

Cần đặc biệt chú ý: `getActualTypeArguments()` trả **`Type[]`**, không phải `Class<?>[]`. Các phần tử có thể là `Class`, `TypeVariable`, `ParameterizedType`, `WildcardType` hoặc `GenericArrayType`. Cast tất cả thành `Class<?>` sẽ không đúng cho cấu trúc như `List<U>`.

## 9. Type reference chỉ capture kiểu được ghi trong khai báo

Một mẫu thường gặp:

```java
abstract class TypeRef<T> {
    private final Type type;

    protected TypeRef() {
        Type parent = getClass().getGenericSuperclass();
        if (!(parent instanceof ParameterizedType p)) {
            throw new IllegalStateException(
                    "Expected parameterized parent");
        }
        type = p.getActualTypeArguments()[0];
    }

    Type type() { return type; }
}
```

Đây là đoạn minh họa của lớp `TypeRef`, cần imports tương ứng; chương trình A có định nghĩa đầy đủ.

Sử dụng ở một **vị trí cụ thể**:

```java
TypeRef<List<String>> ref = new TypeRef<List<String>>() {};
Type captured = ref.type();
```

Anonymous subclass mang trong khai báo `TypeRef<List<String>>`; reflection có thể đọc:

```text
java.util.List<java.lang.String>
```

Đó là **kiểu được lập trình viên/compile-time ghi rõ trong quan hệ kế thừa**, không phải kết quả đoán nội dung của một danh sách runtime.

Nếu code cần mô tả `Map<String, List<Integer>>`, một type reference khai báo đúng cấu trúc cũng có thể mang `ParameterizedType` lồng nhau. Nhưng **metadata mô tả kiểu mong đợi không tự xác minh dữ liệu thực**: một deserializer/validator vẫn phải kiểm tra key, value và từng phần tử theo quy tắc của nó.

## 10. Bẫy quan trọng: type reference tạo trong generic factory

Trong chương trình A:

```java
static <X> TypeRef<X> makeRef(X value) {
    return new TypeRef<X>() {};
}
```

Lời gọi:

```java
Type type = makeRef("hello").type();
```

cho output:

```text
generic factory type: X
factory unresolved: true
```

Nguyên nhân: anonymous subclass được biên dịch tại vị trí mà type argument vẫn là **type variable `X`** của phương thức generic. Lời gọi với `String` không làm `javac` phát sinh một anonymous class mới chuyên biệt cho `X=String`.

Đây là bẫy nghiêm trọng với những hàm tiện ích kiểu:

```java
static <T> Type inferT(T value) {
    return new TypeRef<T>() {}.type();
}
```

Hàm này chỉ capture **`T` của khai báo generic method**, không tự suy ra `String.class` của type argument trong lời gọi cụ thể. Nếu bạn cần kiểu đã được ràng buộc tại runtime, caller phải cung cấp `Class<T>` hoặc `Type` cụ thể, hoặc dùng một anonymous subclass được khai báo tại vị trí có type arguments cụ thể.

**Quy tắc nhanh:** hỏi “`T` được viết ở đâu?” Nếu `T` vẫn là biến kiểu khi compiler tạo lớp anonymous, metadata có thể chỉ giữ `T`. Nếu subclass ghi rõ `String` trong supertype declaration, metadata có cơ hội giữ `String`.

## 11. Không thể biến một `TypeVariable` chưa bind thành `Class` cụ thể

Một `TypeVariable<D>` cho biết:

- Tên biến, ví dụ `T`, `U`, `X`.
- Generic declaration đã khai báo nó (class, method hoặc constructor).
- Bounds hợp lệ, ví dụ `Number`, `Comparable<T>`.

Nó **không nhất thiết mang binding của mọi chỗ sử dụng**. Ví dụ `<T extends Number>` không cho biết `T` là `Integer`, `Long` hay `Double`.

Nếu resolver gặp một `TypeVariable` mà không có mapping tới type argument cụ thể từ những declarations đã đi qua, kết quả có thể phải **giữ nguyên TypeVariable** hoặc biểu diễn trạng thái unresolved. **Không được tự thay `T` bằng `Object` và tuyên bố đã tìm được type argument của người dùng.**

Erasure `T -> Object` là một quy tắc biên dịch JVM descriptors, không phải phép suy luận type arguments. Tương tự, thay `T extends Number` thành `Number` là một thông tin erased/bound, không chứng minh rằng caller đã chọn đúng `Number` thay vì một subtype.

Tại một cấp API, đôi khi chỉ cần erased `Class` là đủ để kiểm tra thô; khi cần **generic type chính xác**, hãy giữ lại trạng thái unresolved thay vì tạo cảm giác đã phục hồi đầy đủ.

## 12. Cơ chế thay thế: truyền `Class<T>` từ đầu

Đây là một cách thiết kế rõ ràng khi cần kiểu runtime của **một lớp cụ thể**:

```java
static <T> T require(Object input, Class<T> token) {
    if (!token.isInstance(input)) {
        throw new IllegalArgumentException("Invalid type");
    }
    return token.cast(input);
}
```

Với lời gọi:

```java
String s = require("hello", String.class);
Integer n = require(42, Integer.class);
```

compiler liên kết type parameter `T` với token truyền vào, và `Class.cast` thực hiện kiểm tra runtime theo token ấy.

**Đây không phải việc reflection tự suy ra `T`**; chính caller đã cung cấp metadata. Cách này rất thích hợp ở ranh giới I/O, plugin factory, deserialization của một kiểu class đơn lẻ hoặc cấu hình chuyển đổi dữ liệu.

Giới hạn cần nhớ:

- `Class<T>` không đại diện đầy đủ cho `List<String>`: không có `List<String>.class`.
- `Class.cast` kiểm tra kiểu runtime của **object**, không tự duyệt các phần tử generic bên trong.
- `String.class.cast(null)` trả `null`; nếu không cho phép null thì API cần quy định riêng.
- Token phải được liên kết nhất quán với kiểu `T`; raw `Class`/unchecked cast có thể phá lời bảo đảm compile-time.

## 13. Chương trình B — Validate dữ liệu theo token thay vì đoán `T`

Tạo tệp **`VerifiedBoundaryDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VerifiedBoundaryDemo {
    static <T> T require(Object candidate, Class<T> token) {
        Objects.requireNonNull(token, "token");
        if (!token.isInstance(candidate)) {
            throw new IllegalArgumentException(
                    "Expected " + token.getSimpleName());
        }
        return token.cast(candidate);
    }

    static <T> List<T> checkedCopy(List<?> input, Class<T> token) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(token, "token");
        List<T> result = new ArrayList<>();

        for (int i = 0; i < input.size(); i++) {
            Object value = input.get(i);
            if (!token.isInstance(value)) {
                throw new IllegalArgumentException(
                        "Invalid index " + i);
            }
            result.add(token.cast(value));
        }
        return List.copyOf(result);
    }

    public static void main(String[] args) {
        System.out.println("single: "
                + require("ok", String.class));
        System.out.println("validated list: "
                + checkedCopy(List.of("A", "B"), String.class));

        try {
            checkedCopy(List.of("A", 9), String.class);
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }

        try {
            require(null, String.class);
        } catch (IllegalArgumentException ex) {
            System.out.println("null rejected: " + ex.getMessage());
        }

        System.out.println("list class accepts integers: "
                + List.class.isInstance(List.of(1, 2)));
    }
}
```

Biên dịch và chạy:

```bash
javac -Xlint:all VerifiedBoundaryDemo.java
java VerifiedBoundaryDemo
```

**Stdout đã kiểm chứng:**

```text
single: ok
validated list: [A, B]
rejected: Invalid index 1
null rejected: Expected String
list class accepts integers: true
```

Không có cảnh báo compiler với `-Xlint:all`.

### Vì sao adapter kiểm tra từng phần tử?

`List.class.isInstance(List.of(1,2))` vẫn trả `true`: runtime object có implement `List`. Nhưng generic argument cụ thể không được JVM tái hiện qua token `List.class`.

Muốn trả một `List<String>` có thể tin cậy từ `List<?>` nhận ở ranh giới, `checkedCopy` làm ba việc tường minh:

1. Lấy từng phần tử nguồn dưới dạng `Object`.
2. Kiểm tra `token.isInstance(value)`, báo lỗi cụ thể khi gặp phần tử sai hoặc `null`.
3. Dùng `token.cast(value)` đưa giá trị đã kiểm tra vào một `ArrayList<T>` mới; trả `List.copyOf(result)`.

Đây là **validation runtime theo từng phần tử**, không phải phục hồi type argument đã mất. Nó cũng có chi phí tỷ lệ với số phần tử (`O(n)` kiểm tra, cộng chi phí sao chép), khác với việc đọc một vài metadata ở class level.

Nếu nguồn có thể bị sửa đồng thời hoặc chứa object mutable, phải thiết kế chính sách snapshot/ownership riêng. `List.copyOf` không tạo immutable sâu cho các phần tử mutable.

## 14. Vì sao đọc generic metadata có chi phí?

Các thao tác reflection không phải tất cả cùng mức độ. Đừng dùng một tuyên bố chung như “reflection luôn chậm” để kết luận mọi workload.

Có thể tách công việc thành các loại:

| Công việc | Ví dụ | Điểm chi phí chính |
| --- | --- | --- |
| Lấy class token đã có | `String.class` | Nhẹ, không quét hierarchy |
| Hỏi runtime class của object | `obj.getClass()` | Lấy class thực, không resolve generic bindings |
| Đọc generic declaration | `field.getGenericType()` | Phân tích/biểu diễn metadata tùy lớp và JDK |
| Liệt kê nhiều fields/methods | `getDeclaredFields()`, `getDeclaredMethods()` | Duyệt nhiều phần tử và có thể tạo mảng/các object mô tả |
| Duyệt hierarchy nhiều tầng | `getGenericSuperclass()` nhiều lần | Có thêm chi phí theo độ sâu và số nhánh |
| Resolve `TypeVariable` | `U -> String` qua hai lớp cha | Cần cấu trúc ánh xạ, đệ quy trên `Type` lồng nhau |
| Phân tích `ParameterizedType` | `getActualTypeArguments()` | Duyệt/cấu trúc biểu thức kiểu lồng nhau |
| Kiểm tra từng phần tử | `Class.isInstance` trong vòng lặp | Chi phí theo kích thước dữ liệu |
| Tạo/mở rộng cache | `ClassValue.get`, `computeValue` | Chi phí lookup, initial compute và lifecycle |

Một lời gọi `getGenericSuperclass()` đơn lẻ có thể hoàn toàn chấp nhận được ở bước khởi tạo. Vấn đề thường xuất hiện khi **cùng một schema/class bị phân tích lại rất nhiều lần** cho từng record hoặc request.

Một số triển khai JDK có cache nội bộ, nhưng không nên suy từ đó rằng toàn bộ pipeline resolver của ứng dụng là miễn phí. Ví dụ: nếu bạn tự tạo mapping `TypeVariable`, duyệt fields, xây descriptors cho serializer rồi bỏ đi ở mỗi request, phần việc bên ngoài cache nội bộ của JDK vẫn bị lặp lại.

**Không có con số nanosecond chung đáng tin** cho tất cả JVM/máy/cấu trúc metadata. Muốn nói một đoạn “chậm X lần”, cần đo bằng workload và cấu hình thực tế.

## 15. Mô hình chi phí đơn giản giúp chọn chỗ cache

Giả sử cùng một lớp schema cần phân tích cho `N` đối tượng.

Nếu mỗi đối tượng đều gọi lại trình phân tích hierarchy:

```text
tổng công việc ≈ N × (quét khai báo + resolve generic + áp dụng lên dữ liệu)
```

Nếu metadata được tính một lần rồi cache theo class:

```text
tổng công việc ≈ (quét khai báo + resolve generic) một lần
               + N × (lookup cache + áp dụng lên dữ liệu)
```

Đây chỉ là mô hình định hướng, **không phải benchmark hoặc công thức thời gian chính xác**. Lợi ích phụ thuộc vào kích thước hierarchy, độ phức tạp của types, số lần tái sử dụng class, contention, alloc/caching của JDK, GC và chi phí lookup.

Cache metadata giúp tránh lặp lại **phần không đổi theo class**. Nó không làm biến mất chi phí xử lý payload: nếu yêu cầu xác minh mỗi phần tử của một `List<?>`, bạn vẫn phải thực hiện kiểm tra phù hợp với dữ liệu của từng instance.

Hãy cache **sơ đồ đã được resolve**; đừng cache một kết quả suy đoán “`T=String`” chỉ từ runtime class `Box` rồi áp dụng cho mọi `Box<Integer>` có cùng class. Làm vậy vừa sai về logic vừa có thể gây heap pollution.

## 16. Thiết kế cache metadata theo `ClassValue`

`ClassValue<V>` là một cơ chế của JDK để liên kết một giá trị được tính với một runtime `Class<?>`. Đây là lựa chọn hữu ích cho metadata **thực sự phụ thuộc vào class**.

Ví dụ ý tưởng:

```java
static final ClassValue<Metadata> CACHE = new ClassValue<>() {
    @Override
    protected Metadata computeValue(Class<?> type) {
        return inspect(type);
    }
};

// Mỗi lần xử lý một đối tượng:
Metadata metadata = CACHE.get(value.getClass());
```

Đoạn trên là minh họa; `Metadata` và `inspect` cần được định nghĩa. Chương trình đầy đủ ở mục 17.

Ưu điểm của cách này:

- Truy vấn qua một `Class` key có sẵn.
- Có điểm tập trung để thực hiện việc phân tích metadata khi cần.
- Không cần duy trì thủ công một `static HashMap<Class<?>,...>` chứa keys mạnh trong một lifetime dài.
- Có thể gọi `remove(Class<?>)` khi muốn bỏ giá trị cached cho một class key.

Nhưng cần nắm giới hạn:

- **Cache chỉ tránh tính lặp metadata nếu metadata được định nghĩa đúng theo class.** Nó không giải được type argument `T` không được ghi trong class.
- `ClassValue.computeValue` có thể bị gọi nhiều hơn một lần trong một số cuộc đua concurrent/khởi tạo. Đừng đặt side effect nghiệp vụ không idempotent vào đó, hoặc suy luận rằng counter phải tăng đúng một lần trong mọi chương trình đa luồng.
- Cache tiêu thụ bộ nhớ; giá trị cached càng lớn, càng cần cân nhắc vòng đời và số lượng class phát sinh.
- Nếu metadata phụ thuộc vào cấu hình/schema version/classloader/locale ngoài `Class`, chỉ dùng `Class` làm key có thể sai. Phải xác định **key đầy đủ** hoặc tổ chức cache phù hợp.
- Không lưu reference đến một object input cụ thể trong metadata cache theo class, vì các instance của cùng class có thể có dữ liệu khác nhau.

## 17. Chương trình C — So sánh số lần phân tích trước/sau cache

Tạo tệp **`MetadataCacheDemo.java`**:

```java
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicInteger;

public class MetadataCacheDemo {
    static class Parent<T> {}
    static final class Child extends Parent<String> {}

    record Metadata(String className, String declaredParent) {}

    static final AtomicInteger uncachedCalls =
            new AtomicInteger();
    static final AtomicInteger cachedComputations =
            new AtomicInteger();

    static Metadata inspectFresh(Class<?> type) {
        uncachedCalls.incrementAndGet();
        Type parent = type.getGenericSuperclass();
        return new Metadata(type.getSimpleName(),
                parent == null ? "(none)" : parent.getTypeName());
    }

    static final ClassValue<Metadata> CACHE = new ClassValue<>() {
        @Override
        protected Metadata computeValue(Class<?> type) {
            cachedComputations.incrementAndGet();
            Type parent = type.getGenericSuperclass();
            return new Metadata(type.getSimpleName(),
                    parent == null ? "(none)" : parent.getTypeName());
        }
    };

    public static void main(String[] args) {
        for (int i = 0; i < 3; i++) {
            inspectFresh(Child.class);
        }
        System.out.println("fresh inspections: "
                + uncachedCalls.get());

        Metadata one = CACHE.get(Child.class);
        Metadata two = CACHE.get(Child.class);
        Metadata three = CACHE.get(Child.class);

        System.out.println("cache same reference: "
                + (one == two && two == three));
        System.out.println("cache computations: "
                + cachedComputations.get());
        System.out.println("declared parent: "
                + one.declaredParent());

        CACHE.get(String.class);
        System.out.println("after another class: "
                + cachedComputations.get());

        CACHE.remove(Child.class);
        CACHE.get(Child.class);
        System.out.println("after removal: "
                + cachedComputations.get());
    }
}
```

Chạy:

```bash
javac -Xlint:all MetadataCacheDemo.java
java MetadataCacheDemo
```

**Stdout đã kiểm chứng trong lượt chạy tuần tự:**

```text
fresh inspections: 3
cache same reference: true
cache computations: 1
declared parent: MetadataCacheDemo$Parent<java.lang.String>
after another class: 2
after removal: 3
```

Trong ví dụ:

- Ba lần gọi `inspectFresh(Child.class)` thực hiện phần thân phân tích ba lần theo cách viết của ứng dụng.
- Ba lần `CACHE.get(Child.class)` trong **một thread, không có remove** tái sử dụng một `Metadata` reference được tính ở lượt đầu.
- `CACHE.get(String.class)` đụng class key mới, làm tăng số lần tính.
- Sau `CACHE.remove(Child.class)`, lời gọi `CACHE.get(Child.class)` lại thực hiện `computeValue`.

**Đừng coi số đếm này là phép đo tốc độ.** Nó chỉ chứng minh **số lần code tự tính metadata** trong kịch bản điều khiển được. Một lookup cache cũng tốn công; việc tiết kiệm có đáng kể hay không phải đo với resolver và workload thật.

## 18. Các lựa chọn cache khác và vòng đời class loader

Một cache thông thường có thể viết:

```java
ConcurrentMap<Class<?>, Metadata> cache = new ConcurrentHashMap<>();
```

Đây là đoạn minh họa cần imports và lớp `Metadata` phù hợp. Với một hệ thống ít class, cùng một classloader sống suốt vòng đời ứng dụng, cách này có thể đủ đơn giản.

Nhưng trong hệ thống plugin, hot reload hoặc application server có thể tạo nhiều classloaders, **một cache static sống lâu giữ strong references tới `Class` và metadata liên quan** có thể góp phần làm classloader không được giải phóng.

Các cân nhắc thực tế:

| Cách lưu | Điểm mạnh | Điều cần kiểm tra |
| --- | --- | --- |
| Tính lại mỗi lần | Đơn giản, không quản lý cache | Có gây lặp phân tích khi số request lớn? |
| Biến cục bộ trong một thao tác | Tái sử dụng trong cùng request | Không tái sử dụng giữa request; tốt nếu workload nhỏ |
| `ClassValue<Metadata>` | Thiết kế cache gắn với class key của JDK | Semantics đồng thời; metadata phải phụ thuộc đúng class |
| `ConcurrentHashMap<Class<?>,Metadata>` | Linh hoạt, quen thuộc | Classloader retention, invalidation, số lượng key |
| Cache theo schema ID/version | Hợp khi metadata phụ thuộc cấu hình ngoài class | Key/đổi version, đồng bộ dữ liệu, evictions |

`ClassValue` thường đáng xem xét khi dữ liệu **thuần theo Class**. Tuy vậy, không có lựa chọn nào miễn phí hoặc đúng trong mọi hệ thống. Hãy đo lượng heap giữ lại, GC, số classloaders và hiệu quả hit rate trong điều kiện thực tế.

Nếu dùng `ClassValue` cho giá trị có **tham chiếu ra classloaders/objects bên ngoài**, vẫn cần xem xét vòng đời những thứ được tham chiếu. Không nên hứa “ClassValue không bao giờ leak”; điều đúng là nó cung cấp một cơ chế lifecycle gắn với `Class` phù hợp hơn việc giữ class keys mạnh vô thời hạn trong nhiều trường hợp.

## 19. Những thao tác nên làm một lần khi khởi tạo

Nếu ứng dụng dùng reflection để map JSON/database rows tới entity objects, một pipeline có thể:

1. Đọc metadata class/field/method một lần.
2. Resolve phần có thể resolve của generic hierarchy.
3. Kiểm tra annotation, constructor hoặc setter cần dùng.
4. Xây một immutable descriptor mô tả cách map dữ liệu.
5. Lưu descriptor vào cache theo class và schema version nếu cần.
6. Khi xử lý từng record, dùng descriptor đã chuẩn bị, chỉ làm validation/conversion thật sự phụ thuộc dữ liệu đầu vào.

Điều này vừa giảm lặp công việc, vừa tập trung logic kiểm tra lỗi metadata về giai đoạn dễ báo lỗi. Nhưng cần chú ý:

- Nếu class được nạp động, phần chuẩn bị có thể phải chạy khi plugin/schema lần đầu xuất hiện.
- Nếu metadata thay đổi theo runtime configuration, cache key/invalidation phải được thiết kế đúng.
- Nếu yêu cầu classloader unloading, không giữ tham chiếu mạnh tùy tiện từ singleton/cache không còn cần thiết.
- Không chia sẻ **mutable resolver state** cho nhiều thread nếu không có hợp đồng thread safety rõ.
- Việc dùng `ClassValue` không đồng nghĩa cache an toàn nếu `Metadata` chứa state bị sửa song song không đồng bộ.

Kết quả mong đợi: **mỗi class/schema ổn định được phân tích ít lần**, còn mỗi input vẫn được kiểm tra đúng hợp đồng tại ranh giới dữ liệu.

## 20. Phân biệt ba loại “chi phí” khi thực hiện reflection

### Chi phí phân tích metadata

Là các thao tác đọc `getGenericSuperclass()`, `getGenericInterfaces()`, `getDeclaredFields()`, `getGenericType()`, duyệt `TypeVariable` và cấu trúc hierarchy. Có thể được tính trước/cache khi phụ thuộc vào class.

### Chi phí áp dụng metadata lên từng object

Là kiểm tra null, gọi `Class.isInstance/cast`, parse dữ liệu, duyệt phần tử collection và xây kết quả. Chi phí này thường **tỷ lệ với số phần tử/fields cần xử lý**, khó loại bỏ hoàn toàn bằng cache.

### Chi phí quản lý cache

Là lookup, lazy initialization, đồng bộ nếu có, bộ nhớ giữ `Metadata`, invalidation và cache misses. Một cache khổng lồ có thể gây GC hoặc retention issues dù giảm số lần parse.

Đừng gom cả ba thành một chữ “reflection overhead”. Trong profile, có thể hot spot nằm ở **validation/serialization** hoặc công việc ứng dụng khác, chứ không phải ở `getGenericSuperclass()`.

## 21. Benchmark thế nào để không kết luận sai?

Nếu cần đánh giá một resolver thật sự, ưu tiên microbenchmark framework như **JMH** với warmup và kiểm soát JIT/GC. Không cần đo nanosecond chỉ để minh họa kiến thức; ví dụ ở mục 17 dùng counter vì mục tiêu là **định lượng số lần tính metadata trong một kịch bản**, không phải tuyên bố hiệu năng.

Quy trình hữu ích:

1. Xác định loại class metadata đại diện: lớp thường, subclass cụ thể, hierarchy nhiều tầng, generic type lồng nhau, class nạp động.
2. Tách ba workload: không cache, cache hit, cache miss/compute.
3. Warm up đủ để JVM tối ưu trước khi lấy mẫu.
4. Tiêu thụ kết quả benchmark đúng cách để tránh JIT loại bỏ công việc không quan sát được.
5. Đo throughput/latency phân phối, allocation rate, GC, CPU; không chỉ một giá trị thời gian trung bình.
6. Chạy nhiều fork/iteration có cấu hình rõ và cùng JDK/hardware.
7. Kiểm tra tính đúng của cache khi classloaders thay đổi, đa luồng hoặc schema version thay đổi.
8. So sánh với end-to-end workload để xem tối ưu resolver có thực sự đáng kể không.

Một vòng lặp gọi `System.nanoTime()` một lần quanh 1000 lời gọi reflection **không đủ đáng tin** để tuyên bố “cache nhanh hơn X lần”: ảnh hưởng của JIT, allocation, warmup, escape analysis và noise có thể lấn át khác biệt thực.

**Định hướng sản phẩm:** chỉ tối ưu sau khi có profile cho thấy metadata resolution là vấn đề có ý nghĩa. Duy trì tính đúng của generic bindings ưu tiên hơn một tối ưu nhỏ không được đo.

## 22. `getGenericSuperclass()` và các API liên quan không phải luôn trả `ParameterizedType`

Ví dụ lỗi phổ biến:

```java
ParameterizedType p =
        (ParameterizedType) someClass.getGenericSuperclass();
```

Đây là đoạn minh họa nguy hiểm: class cha có thể là một raw/non-parameterized class, hoặc `getGenericSuperclass()` trả `null` ở một số class như `Object`/interfaces; cast trực tiếp có thể gây `ClassCastException` hoặc lỗi khác.

Mẫu tốt hơn:

```java
Type parent = someClass.getGenericSuperclass();

if (parent instanceof ParameterizedType p) {
    for (Type argument : p.getActualTypeArguments()) {
        System.out.println(argument.getTypeName());
    }
} else {
    System.out.println("No parameterized direct superclass");
}
```

Đây là đoạn method minh họa, cần khai báo biến `someClass` và imports. Khi cần đi qua interface generic, còn phải đọc `getGenericInterfaces()`; không thể chỉ tìm superclass.

Tương tự, khi lấy `Type`, đừng cast ngay thành `Class<?>`. Metadata có thể là `TypeVariable` chưa resolve, wildcard hoặc `ParameterizedType` lồng nhau. Thường giải pháp tốt là thiết kế một bộ xử lý `Type` có các nhánh được định nghĩa rõ thay vì cast dựa trên một trường hợp đơn giản đang chạy được.

## 23. Chi phí của việc render metadata thành chuỗi

`Type.getTypeName()` rất hữu ích để log, debug và viết tests quan sát. Nhưng **chuỗi hiển thị không phải đối tượng kiểu có thể dùng để kiểm tra runtime**.

Ví dụ:

```java
Type complex = new TypeRef<List<String>>() {}.type();
String printed = complex.getTypeName();
```

`printed` có thể là `"java.util.List<java.lang.String>"`. Nó không phải `Class<List<String>>`, cũng không tự cung cấp mọi ràng buộc/checks cần thiết cho dữ liệu.

Nếu ứng dụng cần thực hiện các phép so sánh/resolve kiểu nhiều lần, có thể lưu **cấu trúc `Type`/descriptor đã chuẩn hóa** thay vì liên tục chuyển metadata thành chuỗi rồi parse lại. Stringification/parsing tự viết dễ bỏ sót owner types, generic arrays, wildcards, bounds và nested types.

Nên phân biệt:

- **Type object**: metadata có cấu trúc.
- **Type name string**: biểu diễn phục vụ con người/logging.
- **Class token**: runtime representation của một class/interface reifiable.
- **Data validation**: thuật toán kiểm tra object/collection theo yêu cầu nghiệp vụ.

Việc cache `getTypeName()` có thể hữu ích nếu log cùng metadata nhiều lần, nhưng đừng xem đó là nguồn đúng đắn để suy ra type arguments chưa tồn tại.

## 24. Trường hợp metadata có thể thiếu hoặc không resolve được

Ngoài type erasure, khi đọc metadata thực tế còn có thể gặp:

- Một raw class/supertype declaration không còn concrete type arguments.
- Type variable chỉ có bounds, không có binding cụ thể.
- Hierarchy qua nhiều interfaces với type variables cần substitutions.
- Generic signature metadata không hiện diện hoặc không hợp lệ trong bytecode được tạo từ công cụ khác.
- Các lớp từ classloader/plugin khác nhau, dù tên class trùng.
- Lớp được tạo động hoặc proxy có hierarchy khác class nghiệp vụ mà code mong đợi.
- Quyền truy cập/module constraints khi phản chiếu thành viên và gọi constructor/method (khác với việc chỉ đọc tên generic type).
- API nhận object không chứa thông tin gì về generic type argument của biến nguồn từng tham chiếu đến nó.

Trong những trường hợp ấy, một resolver vững chắc cần biểu diễn **unresolved** hoặc trả lỗi có ngữ cảnh. Không nên khẳng định “`T=Object`” chỉ vì bound là `Object`.

Nếu yêu cầu nghiệp vụ không thể thỏa do thiếu metadata, cách xử lý đúng là **sửa hợp đồng đầu vào** để nhận `Type`, `Class<T>` hoặc schema tường minh, không viết thêm các cast đoán mò.

## 25. Cách đối chiếu source và bytecode với `javap`

Sau khi biên dịch các chương trình đã kiểm chứng, chạy:

```bash
javap -p -s 'TypeRecoveryDemo$Box'
javap -p -s VerifiedBoundaryDemo
```

Các dấu nháy đơn quanh tên class lồng có ký tự `$` hữu ích trong Bash/PowerShell. Nếu `javap` chạy khác thư mục class file, thêm `-classpath <thư_mục>` tương ứng.

Phần output của `Box<T>`:

```text
class TypeRecoveryDemo$Box<T> {
  private final T value;
    descriptor: Ljava/lang/Object;

  TypeRecoveryDemo$Box(T);
    descriptor: (Ljava/lang/Object;)V

  T get();
    descriptor: ()Ljava/lang/Object;
}
```

Từ `VerifiedBoundaryDemo`:

```text
static <T> T require(java.lang.Object, java.lang.Class<T>);
  descriptor: (Ljava/lang/Object;Ljava/lang/Class;)Ljava/lang/Object;

static <T> java.util.List<T> checkedCopy(java.util.List<?>, java.lang.Class<T>);
  descriptor: (Ljava/util/List;Ljava/lang/Class;)Ljava/util/List;
```

Chính sự khác nhau giữa **dòng generic method/field display** và **JVM descriptor** giúp bạn hiểu `T` được xóa trong executable signatures, trong khi metadata còn có thể mô tả declarations.

Nếu muốn xem generic metadata rõ hơn, dùng `javap -v` và tìm `Signature`. Hãy kiểm tra cả các subclass cụ thể như `TypeRecoveryDemo$NamedStringBox` và `TypeRecoveryDemo$ConcreteChild`.

**Không kết luận rằng metadata hiển thị ở `javap` là bằng chứng JVM đã tái tạo một `Box<String>` runtime class riêng.** Generic `Signature` có thể giữ khai báo nhưng method descriptors vẫn erased.

## 26. So sánh giải pháp khi cần biết kiểu

| Tình huống thực tế | Giải pháp phù hợp | Không nên làm |
| --- | --- | --- |
| Một `Box<T>` trực tiếp, chỉ có object | Chấp nhận giới hạn; thêm token/schema vào API | Đoán `T` từ `getClass()` |
| Class con `extends Box<String>` | Đọc `getGenericSuperclass()` và kiểm tra `Type` | Áp dụng kết luận cho mọi `Box<T>` |
| Subclass nhiều tầng | Duyệt hierarchy và thế `TypeVariable` có căn cứ | Chỉ gọi một cấp rồi cast về `Class` |
| Generic factory `<X>` | Truyền `Class<X>` hoặc `Type` cụ thể | Trông chờ `new TypeRef<X>() {}` thành `String` |
| Kiểu như `List<String>` | Type reference/`Type` + validation cần thiết | Dùng `List.class` để chứng minh phần tử String |
| Dữ liệu legacy `List<?>` | Kiểm tra từng phần tử rồi tạo typed copy | Unchecked cast cả list |
| Resolver dùng rất thường xuyên | Cache metadata đã resolve theo key đúng | Parse hierarchy lại cho từng record |
| Hệ thống plugin/hot-reload | Cân nhắc `ClassValue`, lifecycle, eviction | Giữ strong reference tới mọi class vô hạn |
| Cần số đo hiệu năng | Profile/JMH đại diện workload | Công bố tỷ lệ tăng tốc dựa trên vòng lặp đơn |

## 27. Những lỗi tư duy và lỗi code phổ biến

**Sai lầm 1 — “`Box<T>.getClass()` phải biết `T` vì object đã được tạo.”** Không đúng: object trực tiếp có runtime class `Box`; type argument của biến được compiler sử dụng không tự được ghi như state của object.

**Sai lầm 2 — “`getTypeParameters()[0]` trả actual type argument.”** API trả biến kiểu được **khai báo**, ví dụ `T`. Không đồng nghĩa `String`.

**Sai lầm 3 — “`getGenericSuperclass()` luôn là `ParameterizedType`.”** Không đúng. Kiểm tra `instanceof` và xử lý `Class`/`null` khi cần.

**Sai lầm 4 — “Một `ParameterizedType` chắc chắn có actual args là `Class`.”** Đối số có thể là `TypeVariable`, `ParameterizedType`, wildcard hoặc generic array type.

**Sai lầm 5 — “`new TypeRef<T>() {}` trong generic method bắt được kiểu đã suy luận ở lời gọi.”** Không đúng nếu `T` còn là type variable tại nơi subclass được biên dịch.

**Sai lầm 6 — “Biết giá trị đang là String là biết hộp có type argument String.”** Có thể là `Box<CharSequence>`, `Box<Object>`, hoặc giá trị khác trong lần xử lý khác.

**Sai lầm 7 — “Erasure của T là Object nên actual T chính là Object.”** Erasure không phải actual argument resolution; `Object` chỉ là dạng JVM sử dụng cho vị trí biến không bound.

**Sai lầm 8 — “`Class<List>` chứng minh mọi phần tử đều String.”** Không. Runtime `List` không chứa ràng buộc generic element type tự động.

**Sai lầm 9 — “Dùng unchecked cast là đủ để giải thích T.”** Không. Unchecked cast bỏ kiểm tra chứng minh, có thể đẩy lỗi sang runtime.

**Sai lầm 10 — “Cache metadata giúp phục hồi type arguments đã mất.”** Cache chỉ giữ thông tin đã tính hoặc có sẵn; nó không thêm thông tin không tồn tại.

**Sai lầm 11 — “ClassValue luôn gọi computeValue chính xác một lần trên mọi thread.”** Không nên giả định; cạnh tranh đồng thời có thể dẫn đến tính thừa. Viết hàm tính thuần/idempotent.

**Sai lầm 12 — “Cache `Class<?>` trong static map vô hạn không ảnh hưởng lifecycle.”** Có thể giữ classloaders/classes không còn dùng; cân nhắc lifetime và chính sách cache.

**Sai lầm 13 — “Đếm số lần tính metadata là đo tốc độ.”** Không. Counter chỉ cho biết số lần code chạy; chi phí mỗi thao tác/GC/JIT cần đo riêng.

**Sai lầm 14 — “Reflection luôn quá chậm và phải cache mọi thứ.”** Không có quy tắc tuyệt đối; tối ưu khi profile chứng minh hot spot và lợi ích vượt chi phí phức tạp của cache.

**Sai lầm 15 — “Metadata type name là schema validation.”** Một `Type`/chuỗi `List<String>` mô tả kỳ vọng; vẫn phải xác minh payload khi dữ liệu không đáng tin.

## 28. Bài tập thực hành có đáp án

### Bài 1 — Hai Box khác nhau

```java
Box<String> a = new Box<>("A");
Box<Integer> b = new Box<>(1);
```

**Hỏi:** `a.getClass() == b.getClass()` có thể là `true` không? Có suy ra `T` từ biểu thức đó không?

**Đáp án:** `true` khi cả hai tạo trực tiếp từ cùng class `Box`; phép so sánh không cho biết `String` hay `Integer`.

### Bài 2 — `TypeVariable` chưa resolve

Một generic method `<X> TypeRef<X> makeRef(X x)` tạo `new TypeRef<X>() {}`. Caller truyền `String`. `ref.type()` là gì?

**Đáp án:** metadata của anonymous subclass có thể giữ `X` của method declaration, không tự chuyên biệt hóa thành `String`. Chương trình A xác minh `generic factory type: X`.

### Bài 3 — Metadata lớp con

`class NamedStringBox extends Box<String>` có thể cung cấp gì qua `getGenericSuperclass()`?

**Đáp án:** khai báo `Box<String>` trên class con, vì `String` được ghi tường minh trong supertype declaration.

### Bài 4 — Kế thừa nhiều tầng

`GenericChild<U> extends Box<List<U>>` và `ConcreteChild extends GenericChild<String>`. Chỉ gọi `ConcreteChild.class.getGenericSuperclass()` đã ra `Box<List<String>>` chưa?

**Đáp án:** chưa; nó cho `GenericChild<String>`. Cần đi tiếp tới `GenericChild` và thế `U=String` trong `Box<List<U>>` để thu được cấu trúc tương ứng.

### Bài 5 — `Object` là erasure

Nếu `T` không bound, `Field.getType()` của `T field` trả `Object`. Có thể kết luận caller đã chọn `Box<Object>` không?

**Đáp án:** không. `Object` là erased field type, không phải actual type argument của một instance.

### Bài 6 — Viết API nhận token

Muốn một hàm nhận `Object` và trả `String` sau kiểm tra, nên dùng `String.class` như thế nào?

**Đáp án:** truyền `Class<T>` token vào generic method, dùng `token.isInstance`/ `token.cast`, với chính sách null rõ ràng. Không cần `T.class` trong generic method.

### Bài 7 — List lồng nhau

Một `List<Integer>` thỏa `List.class.isInstance`. Điều đó đủ xác nhận nó là `List<String>` chưa?

**Đáp án:** chưa. Token `List.class` chỉ kiểm tra interface runtime. Muốn bảo đảm danh sách toàn `String`, hãy kiểm tra phần tử hoặc dùng schema validation tương ứng.

### Bài 8 — Caching

Chạy `inspectFresh(Child.class)` ba lần rồi `CACHE.get(Child.class)` ba lần trong ví dụ C. Counter lần lượt là bao nhiêu?

**Đáp án:** `fresh inspections: 3`, `cache computations: 1` trong lượt chạy tuần tự được kiểm thử; đây không phải thời gian thực thi, và tình huống concurrent có thể khác.

### Bài 9 — Key cache sai

Một metadata resolver phụ thuộc cả `Class<?>` và phiên bản schema. Có đủ dùng `Class` làm key không?

**Đáp án:** không, nếu cùng class có thể được xử lý dưới những schema versions khác nhau. Cần cache key chứa version hoặc invalidation rõ ràng.

### Bài 10 — Classloader retention

Một `static ConcurrentHashMap<Class<?>,Metadata>` giữ các class từ plugin classloaders đã unload về mặt nghiệp vụ. Có rủi ro gì?

**Đáp án:** strong keys/values có thể giữ class và classloader reachable, góp phần ngăn giải phóng bộ nhớ. Cần xem lifecycle, eviction, cache thuộc đúng loader hoặc cân nhắc `ClassValue`.

### Bài 11 — Số đo hiệu năng

Một benchmark dùng `System.nanoTime()` một lần quanh vài lời gọi và công bố cache “nhanh hơn 100 lần”. Có đủ căn cứ không?

**Đáp án:** chưa. Cần warmup, kiểm soát JIT/GC/alloc, tiêu thụ kết quả và workload đại diện. JMH giúp giảm nhiều bẫy của microbenchmark.

### Bài 12 — Type reference như phép validation

Có `TypeRef<List<String>>` mô tả schema. Nếu dữ liệu thô có một `Integer` trong list, chỉ tạo type reference đã làm input hợp lệ chưa?

**Đáp án:** chưa. Type reference giữ metadata kỳ vọng, không tự kiểm tra payload. Cần validate/deserialize theo schema.

## 29. Checklist áp dụng trong dự án

- [ ] Phân biệt rõ **runtime class**, **generic declaration**, **binding từ superclass** và **type argument ở nơi sử dụng**.
- [ ] Không suy `T` tùy ý chỉ từ `obj.getClass()` hoặc runtime class của một phần tử.
- [ ] Không coi `TypeVariable` chưa bind là một `Class` cụ thể.
- [ ] Trước khi cast `Type`, kiểm tra dạng `Class` / `ParameterizedType` / `TypeVariable` / wildcard / generic array.
- [ ] Khi resolve hierarchy, thay type variables qua **từng tầng**, không chỉ nhìn superclass trực tiếp.
- [ ] Nếu cần runtime type, yêu cầu caller truyền `Class<T>`, `Type`, type reference hoặc schema.
- [ ] Nếu cần an toàn dữ liệu nested generic, kiểm tra phần tử/key/value theo hợp đồng.
- [ ] Không dùng unchecked cast để “giả lập” thông tin kiểu đã mất.
- [ ] Chỉ cache metadata có thể tái sử dụng theo key được định nghĩa đúng.
- [ ] Đã cân nhắc `ClassValue`, classloader lifecycle, concurrency, versioning và invalidation.
- [ ] Khi đánh giá hiệu năng, đã tách metadata resolution khỏi per-item validation và cost của cache.
- [ ] Đã profile/benchmark trên workload có ý nghĩa, không dựa vào số đếm hoặc thời gian một lượt.
- [ ] Đã đối chiếu generic `Signature` với erased JVM descriptor bằng `javap` khi cần.

## Kết luận

**Không thể phục hồi một `T` chưa được ràng buộc chỉ bằng `getClass()` hay phép reflection phức tạp hơn.** Type erasure khiến nhiều cách tham số hóa dùng chung runtime class, còn generic metadata được lưu tại **khai báo** chỉ có thể giúp giải kiểu khi tồn tại binding đủ cụ thể, như `extends Box<String>` hoặc type reference được ghi rõ. Với hierarchy nhiều tầng, cần thế các `TypeVariable` có căn cứ; nếu không có mapping, kết quả phải vẫn là **unresolved**, không đoán.

Về chi phí, metadata reflection nên được xử lý theo nhu cầu: phân tích một lần ở ranh giới phù hợp, tái sử dụng descriptor khi cùng class/schema lặp lại, và kiểm tra dữ liệu thực khi validation là bắt buộc. `ClassValue` có thể giảm số lần tự phân tích metadata theo class, nhưng cần thiết kế cache đúng vòng đời và đo hiệu năng thật trước khi tối ưu. **Ưu tiên tính đúng của generic bindings trước, rồi mới tối ưu chi phí đọc và resolve siêu dữ liệu.**


</details>

- [Quay lại đầu trang](#back-to-top)

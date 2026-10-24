<a id="back-to-top"></a>

# Phân giải generic

## Menu
- [Type erasure và giới hạn nhìn thấy kiểu List&lt;String&gt; của một instance](#generic-type-resolution-001)
- [Thông tin kiểu từ lớp con có superclass được tham số hóa](#generic-type-resolution-002)
- [Ánh xạ TypeVariable sang kiểu cụ thể qua nhiều tầng kế thừa](#generic-type-resolution-003)
- [Thay thế kiểu lồng trong ParameterizedType](#generic-type-resolution-004)
- [Xử lý wildcard, generic array và owner type](#generic-type-resolution-005)
- [Biến kiểu của phương thức khác biến kiểu của lớp](#generic-type-resolution-006)
- [Giới hạn kiểu chưa được ràng buộc và trường hợp raw type](#generic-type-resolution-007)
- [Mẫu type token và anonymous subclass](#generic-type-resolution-008)
- [Không suy ra kiểu phần tử chỉ bằng việc xem các phần tử đang có](#generic-type-resolution-009)
- [Tránh đệ quy vô hạn khi giới hạn kiểu tự tham chiếu](#generic-type-resolution-010)

## <a id="generic-type-resolution-001">Type erasure và giới hạn nhìn thấy kiểu List&lt;String&gt; của một instance</a>

<details>
<summary>Xem chi tiết</summary>

# Type erasure và giới hạn nhìn thấy kiểu List<String> của một instance trong Java Reflection

## 1. Một đối tượng List<String> có lưu String như một kiểu runtime không?

Giả sử Java 17+ có:

```java
List<String> names = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();
```

Trong mã nguồn, compiler xem `List<String>` và `List<Integer>` là hai kiểu generic khác nhau để kiểm tra tính hợp lệ của `add`, `get` và phép gán. Nhưng khi hai biến chứa object được tạo từ `new ArrayList<>()` thông thường, Java chỉ có **một runtime class `java.util.ArrayList`** cho cả hai.

```java
System.out.println(names.getClass() == numbers.getClass()); // true
```

**Type erasure** là cơ chế compiler chuyển nhiều biểu diễn generic về kiểu JVM tương ứng mà không tạo một class riêng cho mỗi type argument. Do vậy, từ một instance bình thường như `new ArrayList<String>()`, `getClass()` không cho biết đối số kiểu `String` của biểu thức nguồn. Thứ tự phần tử, lớp của từng phần tử, thậm chí việc list đang rỗng cũng không tiết lộ **khai báo generic gốc**.

Cần phân biệt:

- **Runtime class của instance:** `instance.getClass()` trả `ArrayList.class` (hoặc class implementation thực tế).
- **Generic signature của một khai báo:** `Field.getGenericType()` hoặc `Method.getGenericReturnType()` có thể đọc `List<String>` từ class-file metadata nếu field/method được khai báo như vậy.
- **Dữ liệu trong collection:** duyệt phần tử và gọi `element.getClass()` chỉ cho biết lớp của các phần tử hiện tại khác `null`, không cho biết type argument được khai báo trong source.

**Thông tin generic của khai báo có thể còn; type argument gắn riêng với một instance ArrayList thông thường thì không được tự động lưu lại.** Đây là ranh giới quan trọng nhất khi dùng Reflection.

## 2. Type erasure làm gì lúc biên dịch?

Tại compile time, Java generics giúp kiểm tra type safety. Khi tạo JVM class file, compiler áp dụng erasure:

1. Type parameter không có bound, ví dụ `T` trong `class Box<T>`, thường có erasure là `Object`.
2. Type parameter có upper bound như `T extends Number` có erasure là `Number`. Nếu có nhiều bound, quy tắc erasure dùng leftmost bound.
3. Compiler có thể chèn cast tại chỗ giá trị lấy từ generic API cần được xử lý như kiểu cụ thể.
4. Compiler có thể tạo bridge method để giữ đúng overriding/đa hình sau erasure.
5. Một số generic declarations vẫn có thuộc tính `Signature` trong class file để Reflection và công cụ đọc class file có thể phân tích.

Ví dụ:

```java
List<String> names = new ArrayList<>();
names.add("An");
String first = names.get(0);
```

Sau erasure, method `List.get(int)` có kết quả mức bytecode là `Object`. Compiler chèn kiểm tra/cast cần thiết để giá trị gán cho `String first` đáp ứng yêu cầu type nguồn. Runtime object `"An"` vẫn là một `String` thực tế; erasure **không xóa lớp của phần tử**. Điều không còn được lưu trên một object `ArrayList` thường là “nó đã được tạo ra với `<String>`”.

Vì vậy, chương trình có thể giữ được generic type safety nếu toàn bộ mã được type-check chuẩn, nhưng có thể gặp **heap pollution** nếu cố ý đi qua raw type/unchecked operations hoặc bytecode không bảo đảm những quy tắc đó.

## 3. Reflection thấy được gì?

| Câu hỏi | API thích hợp | Giới hạn |
| --- | --- | --- |
| Object đang là class nào? | `object.getClass()` | Không đọc được `<String>` từ ordinary `ArrayList` instance |
| Object có implement List không? | `object instanceof List<?>` hoặc `List.class.isInstance(object)` | Không xác định kiểu generic phần tử |
| Field được khai báo kiểu nào? | `field.getType()` | Cho kiểu đã erasure, như `List.class` |
| Field được khai báo với type arguments nào? | `field.getGenericType()` | Cho metadata của *khai báo field*, không phải nội dung runtime |
| Method được khai báo trả generic type gì? | `method.getGenericReturnType()` | Không phải kiểu concrete của object mà lần gọi hiện tại trả về |
| Method nhận tham số generic nào? | `method.getGenericParameterTypes()` | Thể hiện chữ ký khai báo, không phải actual argument |
| Class khai báo generic superclass gì? | `clazz.getGenericSuperclass()` | Có thể trả type variable `E` hoặc type argument cụ thể tùy khai báo |
| Class khai báo generic interfaces gì? | `clazz.getGenericInterfaces()` | Chỉ mô tả generic declarations của class |
| Mỗi element hiện là lớp nào? | `element.getClass()` nếu khác `null` | Không chứng minh list được khai báo `List<String>` |

Ví dụ, một field khai báo `List<String> names` có thể có `getGenericType()` trả `java.util.List<java.lang.String>`. Nếu field hiện trỏ tới `new LinkedList<String>()`, `Field.getType()` vẫn là `List.class`, `Field.getGenericType()` vẫn mô tả `List<String>`, còn `field.get(instance).getClass()` cho `LinkedList.class`. Đó là **ba loại thông tin khác nhau**.

Các local variables trong method như `List<String> local` không được Java core Reflection cung cấp một API trực tiếp tương đương `Field.getGenericType()` để hỏi kiểu khai báo của biến ấy từ chính object mà nó trỏ đến. Metadata debug/bytecode có thể chứa dữ liệu khác nhưng không phải một đặc tính runtime có thể truy xuất từ mỗi object.

## 4. Chương trình Java 17+ đầy đủ, kết quả đã kiểm chứng

Lưu tệp `ErasureInstanceDemo.java`:

```java
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.List;

public class ErasureInstanceDemo {
    static class Holder {
        List<String> names = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
    }
    static class Box<T> {
        T value;
    }
    static class StringBox extends Box<String> {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void pollute(List<String> strings) {
        List raw = strings;
        raw.add(42);
    }

    public static void main(String[] args) throws Exception {
        List<String> names = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        System.out.println("same runtime class = "
                + (names.getClass() == numbers.getClass()));
        System.out.println("runtime class = " + names.getClass().getName());
        System.out.println("ArrayList generic superclass = "
                + names.getClass().getGenericSuperclass().getTypeName());

        Field field = Holder.class.getDeclaredField("names");
        System.out.println("field raw type = " + field.getType().getTypeName());
        Type generic = field.getGenericType();
        System.out.println("field generic type = " + generic.getTypeName());
        System.out.println("field arg = " + ((ParameterizedType) generic)
                .getActualTypeArguments()[0].getTypeName());

        Type anonSuperclass = new ArrayList<String>() {}
                .getClass().getGenericSuperclass();
        System.out.println("anonymous superclass = "
                + anonSuperclass.getTypeName());

        Type boxFieldType = Box.class.getDeclaredField("value").getGenericType();
        System.out.println("Box.value generic type = " + boxFieldType.getTypeName());
        System.out.println("Box type parameter = "
                + ((TypeVariable<?>) boxFieldType).getName());
        System.out.println("StringBox superclass = "
                + StringBox.class.getGenericSuperclass().getTypeName());

        List<String> polluted = new ArrayList<>();
        polluted.add("ok");
        pollute(polluted);
        System.out.println("size after raw add = " + polluted.size());
        System.out.println("second element runtime = "
                + ((List<?>) polluted).get(1).getClass().getSimpleName());
        try {
            String invalid = polluted.get(1);
            System.out.println(invalid);
        } catch (ClassCastException ex) {
            System.out.println("read as String = ClassCastException");
        }
        System.out.println("List<String> instanceof List<?> = "
                + (polluted instanceof List<?>));
    }
}
```

Biên dịch hướng tới Java 17; chạy trên JDK 17 trở lên:

```bash
javac --release 17 ErasureInstanceDemo.java
java ErasureInstanceDemo
```

Stdout chính xác của lần biên dịch/chạy bằng JDK 24 với `--release 17`:

```text
same runtime class = true
runtime class = java.util.ArrayList
ArrayList generic superclass = java.util.AbstractList<E>
field raw type = java.util.List
field generic type = java.util.List<java.lang.String>
field arg = java.lang.String
anonymous superclass = java.util.ArrayList<java.lang.String>
Box.value generic type = T
Box type parameter = T
StringBox superclass = ErasureInstanceDemo$Box<java.lang.String>
size after raw add = 2
second element runtime = Integer
read as String = ClassCastException
List<String> instanceof List<?> = true
```

Phân tích:

1. Hai object tạo từ `new ArrayList<>()` có chung runtime `Class<?>`. Kiểu tham số hóa của local variables không khiến JVM tạo lớp riêng.
2. `ArrayList.class` khai báo generic superclass `AbstractList<E>`; khi đọc từ instance thường, `E` là **type variable của declaration**, không phải `String` đã gắn cho biến.
3. Field `Holder.names` khai báo trực tiếp `List<String>`, nên `Field.getGenericType()` đọc được chữ ký generic; `Field.getType()` chỉ trả raw `List`.
4. `new ArrayList<String>() {}` tạo **anonymous subclass** và ghi quan hệ `extends ArrayList<String>` trong metadata. Vì vậy có thể đọc `ArrayList<String>` từ **generic superclass của anonymous subclass**.
5. Field `Box<T>.value` được khai báo bằng type variable `T`. Reflection trực tiếp trên `Box.value` thấy `T`. `StringBox extends Box<String>` mang ánh xạ `T → String` ở metadata của quan hệ superclass; Reflection không tự đổi kiểu field khi hỏi `Box.class`.
6. `pollute` cố tình sử dụng raw List để thêm `Integer` vào object được tham chiếu qua `List<String>`. Bản thân list vẫn là List runtime hợp lệ. Lỗi xảy ra muộn, khi code muốn dùng phần tử như một `String` và cast được compiler chèn thất bại.

Đừng dùng `@SuppressWarnings` như cách “sửa” heap pollution. Annotation này chỉ loại thông báo compiler ở điểm đang cố ý minh họa thao tác unchecked; nó không đổi kiểm tra runtime.

## 5. getGenericType trả Type, không phải luôn Class<?>

Các API generic reflection trả interface `java.lang.reflect.Type`. Giá trị cụ thể có thể thuộc một số họ:

- `Class<?>`: kiểu có runtime class như `String.class`, `List.class`, `int.class`, `String[].class`.
- `ParameterizedType`: khai báo `List<String>`, `Map<String, Integer>`; đọc `getRawType()` và `getActualTypeArguments()`.
- `TypeVariable<?>`: tham số kiểu chưa được thay bằng type cụ thể, ví dụ `T` trong `Box<T>`. `getBounds()` cho bound.
- `WildcardType`: thành phần wildcard như `? extends Number` hoặc `? super Integer`, truy cập upper/lower bounds.
- `GenericArrayType`: kiểu mảng có component generic non-reifiable trong chữ ký như `T[]`.

Vì vậy, không nên viết vô điều kiện:

```java
ParameterizedType generic = (ParameterizedType) field.getGenericType();
```

Nó chỉ đúng nếu `field.getGenericType()` thực sự là `ParameterizedType`. Với `String title` hoặc raw `List values`, kết quả có thể là `Class<?>`. Với `T value` trong `Box<T>`, kết quả là `TypeVariable<?>`. Cast nhầm sẽ gây `ClassCastException`.

Một utility đọc metadata có kiểm tra, biên dịch được trên Java 17:

```java
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class GenericFieldInspector {
    private GenericFieldInspector() {}

    public static void describe(Field field) {
        Type declared = field.getGenericType();
        System.out.println("declared type: " + declared.getTypeName());
        if (declared instanceof ParameterizedType pt) {
            System.out.println("raw: " + pt.getRawType().getTypeName());
            for (Type arg : pt.getActualTypeArguments()) {
                System.out.println("argument: " + arg.getTypeName());
            }
        }
    }
}
```

Đây là class utility độc lập có thể biên dịch khi lưu vào `GenericFieldInspector.java` nhưng **không có `main`**. Gọi `describe` từ chương trình khác với `Field` đã tra cứu. Utility phân tích declared type, không kiểm tra các phần tử đang nằm trong collection.

Với `Map<String, List<Integer>>`, argument thứ hai của `Map` lại là một `ParameterizedType`. Vì vậy công cụ giải generic đầy đủ phải đi theo **cây Type**. Với `List<? extends Number>`, argument là `WildcardType`, không phải `Number.class` chính xác. Đừng chỉ cast mọi phần tử của `getActualTypeArguments()` thành `Class<?>`.

## 6. Type variable, inheritance và giới hạn của suy luận

Ví dụ:

```java
class Box<T> {
    T value;
}
class StringBox extends Box<String> { }
```

`Box.class.getDeclaredField("value").getGenericType()` cho `T` vì field **được khai báo ở Box**. Trong khi đó, `StringBox.class.getGenericSuperclass()` mô tả `Box<String>`. Để suy ra kiểu dùng cho `value` trong ngữ cảnh `StringBox`, framework phải tự resolve ánh xạ type variables, có thể phải đi qua nhiều lớp superclass/interface.

Nếu generic declaration là `class Holder<T extends Number>` thì erasure của `T` liên quan `Number`, nhưng metadata có thể vẫn biểu diễn `T` khi dùng `getGenericType()`. Cần phân biệt **erased type dùng cho JVM** với **generic declaration được Reflection đọc qua Signature**.

Không thể dùng `getClass()` của field value để tự động suy ra mapping này: nếu field chứa `null` thì thậm chí không có runtime class, còn một object có thể là subclass của kiểu declared type.

## 7. Anonymous subclass và type token giữ generic metadata ở đâu?

So sánh:

```java
ArrayList<String> ordinary = new ArrayList<>();
ArrayList<String> captured = new ArrayList<String>() {};
```

`ordinary.getClass()` là `ArrayList.class`; generic superclass trên `ArrayList` là `AbstractList<E>`. `captured.getClass()` là một **anonymous subclass riêng**, có metadata khai báo `extends ArrayList<String>`; vì vậy `captured.getClass().getGenericSuperclass()` có thể cho `ParameterizedType` của `ArrayList<String>`.

Kỹ thuật type token tận dụng ý tưởng khai báo một subclass chứa concrete generic parameter:

```java
abstract class TypeRef<T> {
    final java.lang.reflect.Type type =
            ((java.lang.reflect.ParameterizedType)
                getClass().getGenericSuperclass())
                .getActualTypeArguments()[0];
}

// Đặt các lệnh dưới đây trong một method, với import java.util.List:
TypeRef<List<String>> ref = new TypeRef<List<String>>() {};
System.out.println(ref.type.getTypeName());
// java.util.List<java.lang.String>
```

Đây là **mã minh họa**, không phải một tệp có thể chạy độc lập: phần tạo `ref` phải đặt trong một method và cần import `List`. Cách cast trong `TypeRef` giả định caller tạo **anonymous direct subclass với superclass có tham số hóa**, không tự giải quyết trường hợp nhiều tầng kế thừa hoặc subclass raw.

**Type token không đọc kiểu generic ẩn từ một instance ArrayList bình thường.** Nó giữ `Type` do **code đã khai báo tường minh trong anonymous subclass**. Khi một framework nhận `TypeRef<List<String>>` cùng một list object, type token là *metadata bổ sung do caller cấp*. Runtime collection vẫn có thể bị nhiễm phần tử sai kiểu, và cần validation khi xử lý dữ liệu không tin cậy.

## 8. Kiểm tra thực tế từng phần tử khác với xác định List<String>

Nếu nhận `Object unknown` từ API, ta có thể xác nhận nó đang là một list chỉ gồm `String` tại thời điểm duyệt:

```java
import java.util.List;

public final class ListContents {
    private ListContents() {}

    public static boolean containsOnlyStrings(Object unknown) {
        if (!(unknown instanceof List<?> items)) {
            return false;
        }
        for (Object item : items) {
            if (!(item instanceof String)) {
                return false;
            }
        }
        return true;
    }
}
```

Đây là utility class có thể biên dịch với Java 17, không có `main`. Chính sách ví dụ coi phần tử `null` là không hợp lệ; ứng dụng khác có thể muốn cho phép `null`.

Nhưng kết quả `true` **không chứng minh** source type `List<String>`:

- Một `List<Object>` cũng có thể đang chứa toàn `String`.
- Một danh sách rỗng trả `true` dù chẳng có element nào để kiểm chứng; không suy ra được source generic argument.
- Danh sách có thể bị thay đổi sau khi kiểm tra hoặc thay đổi đồng thời.
- Element runtime có subtype của kiểu khai báo; quan sát kiểu runtime không cho biết tổng thể generic declaration.
- `(List<String>) someObject` là unchecked cast trong những bối cảnh thường gặp, không tự kiểm tra tất cả phần tử. Lỗi `ClassCastException` có thể chỉ xuất hiện khi truy cập phần tử.

Đối với serialization, mapping JSON hay binding dữ liệu, nên **dùng generic declared Type/schema do caller cung cấp** để quyết định kiểu phần tử cần chuyển đổi, sau đó validation các giá trị tương ứng. Không nên chỉ lấy `instance.getClass()` rồi suy đoán.

## 9. instanceof, generic array và tính reifiable

Trong Java, `List<String>` thường **không reifiable**: type argument `String` không được JVM kiểm tra riêng cho một List runtime. Các phép sau không hợp lệ trong mã Java thông thường:

```java
// Không hợp lệ; được comment để code mẫu không gây lỗi biên dịch:
// if (obj instanceof List<String>) { }
// Class<?> type = List<String>.class;
// List<String>[] xs = new List<String>[4];
```

`obj instanceof List<?>` hợp lệ vì `List<?>` là dạng reifiable: chỉ kiểm tra xem object có implement List không. Với array như `String[]`, JVM giữ runtime component type (`getClass().getComponentType() == String.class`). `List<?>[]` có thể được tạo, nhưng từng element trong array chỉ được kiểm tra là List chứ không có generic argument cụ thể.

Đừng nhầm **array covariance** với **generic invariance**. `String[]` có thể gán cho `Object[]` và gặp `ArrayStoreException` nếu ghi object sai kiểu, nhưng `List<String>` không phải subtype của `List<Object>`. Điều này giúp hiểu vì sao Java cấm tạo trực tiếp `new List<String>[...]`: runtime array kiểm tra component class, còn generic parameter `String` đã bị erasure.

Một biểu hiện của erasure là không thể khai báo hai overload chỉ khác generic argument:

```java
// Hai overload dưới đây nếu cùng nằm trong một class sẽ bị name clash:
// void handle(List<String> strings) {}
// void handle(List<Integer> numbers) {}
```

Vì cả hai bị erasure về chữ ký JVM với một tham số `List`, không có cách chọn overload theo `<String>` hay `<Integer>` lúc chạy.

## 10. Checklist cho người viết công cụ Reflection

1. **Nếu cần runtime class**, hỏi `getClass()` và chấp nhận rằng nó chỉ phản ánh implementation, không tự tái tạo `List<String>`.
2. **Nếu cần generic declaration**, xác định *vị trí khai báo*: field, method return/parameter, constructor parameter, generic superclass hoặc interface. Dùng API `getGeneric...` tương ứng.
3. **Nếu đang cầm một Type**, kiểm tra biến thể `Class`/`ParameterizedType`/`TypeVariable`/`WildcardType`/`GenericArrayType` trước khi cast. Với generic nested types, duyệt cây Type.
4. **Nếu generic type là T**, cần phân tích quan hệ inheritance và ánh xạ TypeVariable theo context. Không lấy class của giá trị field để kết luận declaration.
5. **Nếu cần runtime type token**, yêu cầu caller khai báo nó tường minh hoặc sử dụng generic declaration của API. Đừng nghĩ `new ArrayList<String>()` thông thường tự tạo được token.
6. **Nếu cần bảo đảm dữ liệu**, định nghĩa schema và validation từng phần tử; lưu ý list rỗng, null, raw types, unchecked casts và concurrency.
7. **Nếu chẩn đoán class file**, có thể dùng `javap -v` xem attribute `Signature`. Cần kiểm tra đúng artifact và classloader đang được sử dụng; toolchain/obfuscation có thể ảnh hưởng metadata.
8. **Nếu chỉ gọi generic API với code Java bình thường**, ưu tiên type checking của compiler. Reflection phù hợp khi schema và type metadata chỉ được xác định lúc runtime.

## 11. Các lỗi phổ biến

| Nhận định | Điều đúng |
| --- | --- |
| “`List<String>` và `List<Integer>` tạo hai runtime class khác nhau” | Ordinary `new ArrayList<>()` của cả hai vẫn có runtime class `ArrayList` |
| “`getClass()` cho biết type argument `String`” | Chỉ cho runtime class implementation |
| “`Field.getGenericType()` chứng minh tất cả phần tử là String” | Nó chỉ mô tả type khai báo của field |
| “`getGenericType()` luôn cast được thành `ParameterizedType`” | Nó có thể là `Class`, `TypeVariable` hoặc loại `Type` khác |
| “Lấy phần tử đầu tiên sẽ biết generic type gốc” | Sai với list rỗng, null, subtype hoặc list khai báo `List<Object>` |
| “Anonymous subclass khiến mọi ArrayList đều có type argument runtime” | Generic signature chỉ nằm trong khai báo superclass của subclass cụ thể |
| “`instanceof List<String>` hợp lệ” | Dùng `instanceof List<?>`; không kiểm tra được `String` generic argument |
| “Unchecked cast sang `List<String>` kiểm tra toàn bộ phần tử” | Không; type pollution có thể lộ khi đọc |
| “`T extends Number` bị erasure thành Object trong mọi trường hợp” | Erasure dùng bound, với ví dụ này là `Number` |
| “Type token bảo đảm dữ liệu collection đúng schema” | Type token mô tả type được khai báo; dữ liệu phải được kiểm tra riêng |

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `List<String> a = new ArrayList<>(); List<Integer> b = new ArrayList<>();`. `a.getClass() == b.getClass()` là gì? **`true`** (cùng runtime class `ArrayList`).

**Câu 2.** Field `List<String> names` có hai cách đọc `getType()` và `getGenericType()`. Chúng khác nhau thế nào? **`getType()` cho raw `List.class`; `getGenericType()` có thể cho `ParameterizedType` mô tả `List<String>`**.

**Câu 3.** Một list chỉ chứa `String` chứng minh nó được khai báo `List<String>`? **Không**. `List<Object>`, `List<CharSequence>` hoặc raw list cũng có thể chứa chỉ String.

**Câu 4.** Một anonymous `new ArrayList<String>() {}` giữ được gì? **Chữ ký generic `extends ArrayList<String>` trong class của anonymous subclass**; không phải metadata tự động trên object `ArrayList` bình thường.

**Câu 5.** `Box<T>.value` khi đọc từ `Box.class` trong chương trình ví dụ trả `String` hay `T`? **`T`**. Muốn resolve sang `String` trong ngữ cảnh `StringBox` phải phân tích superclass metadata.

**Câu 6.** Tại sao có thể xảy ra `ClassCastException` khi đọc từ `List<String>`? Một raw/unchecked operation có thể đưa `Integer` vào list; cast compiler dùng khi đọc dưới dạng String thất bại.

**Câu 7.** List rỗng có thể chứng minh type argument từ các phần tử? **Không**. Không có element nào để kiểm tra.

**Câu 8.** `obj instanceof List<?>` cho biết điều gì? **Chỉ rằng object là List runtime**, không xác minh element type.

## 13. Nguồn tài liệu chính thức

- [Java Language Specification 17, Section 4.6: Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6)
- [Java Language Specification 17, Section 4.7: Reifiable Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.7)
- [Java SE 17 Javadoc: Type](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Type.html)
- [Java SE 17 Javadoc: ParameterizedType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html)
- [Java SE 17 Javadoc: Field.getGenericType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html)
- [Java SE 17 Javadoc: Class generic superclass and interfaces](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [JVM Specification 17, Section 4.7.9: Signature Attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.9)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-resolution-002">Thông tin kiểu từ lớp con có superclass được tham số hóa</a>

<details>
<summary>Xem chi tiết</summary>

# Thông tin kiểu từ lớp con có superclass được tham số hóa

Khi một lớp kế thừa từ superclass generic và cung cấp tham số kiểu cụ thể, Java có thể lưu **thông tin kiểu của quan hệ kế thừa** trong metadata class file. Reflection cho phép đọc thông tin này bằng `Class.getGenericSuperclass()` và phân tích đối tượng `java.lang.reflect.Type`. Nhờ đó, một framework có thể biết `StringRepository extends Repository<String>` đã khai báo `String` làm đối số kiểu cho `Repository<T>`, dù thông tin đối số kiểu thường bị xóa khỏi kiểu đối tượng ở runtime bởi **type erasure**.

Tuy nhiên, kết quả không phải lúc nào cũng là `Class<?>`: nó có thể là `ParameterizedType`, chứa tiếp `TypeVariable`, `WildcardType` hoặc `GenericArrayType`. Nếu có nhiều cấp kế thừa, Reflection **không tự thay thế** mọi biến kiểu từ các lớp trung gian; muốn suy ra kiểu cuối cùng phải lần theo chuỗi kế thừa và thực hiện ánh xạ biến kiểu.

## 1. Bài toán: đọc lại đối số kiểu trên quan hệ extends

Xét hai khai báo:

```java
class Repository<T> {
}

class StringRepository extends Repository<String> {
}
```

Ở mã nguồn, `StringRepository` đã cố định đối số `T` của superclass thành `String`. Vì vậy ta muốn biết:

- Superclass thô (raw superclass) của `StringRepository` là gì?
- Quan hệ kế thừa có chứa đối số kiểu nào?
- Đối số đó đã là class cụ thể, hay vẫn là biến kiểu?
- Có thể suy ra kiểu ở tầng `Repository<T>` nếu có một vài lớp trung gian không?

Hai API `Class` có mục đích khác nhau:

```java
Class<?> raw = StringRepository.class.getSuperclass();
java.lang.reflect.Type generic =
        StringRepository.class.getGenericSuperclass();
```

Kết quả có ý nghĩa:

- `getSuperclass()` trả `Repository.class`, là đối tượng `Class<?>` cho superclass trực tiếp; **không giữ đối số `String`**.
- `getGenericSuperclass()` trả một `Type` đại diện cho `Repository<String>`; trong trường hợp này nó là `ParameterizedType`.
- Hai cách đọc chỉ xét **superclass trực tiếp**, không tự tìm kiểu generic sâu hơn mọi cấp trong cây kế thừa.
- Nếu class là `Object.class` hoặc interface không có superclass tương ứng, `getGenericSuperclass()` trả `null`. Nếu superclass được dùng ở dạng raw, kết quả có thể là một `Class<?>` thay vì `ParameterizedType`.

Thông tin generic tồn tại trên **declaration** như quan hệ `extends Repository<String>`. Điều này khác việc hỏi một đối tượng `new Repository<String>()` ở runtime xem đối số `T` của nó là gì: với class generic thông thường, `new Repository<String>().getClass()` vẫn chỉ trả `Repository.class`, và không có đối số `String` trong đối tượng `Class` đó.

## 2. Những nhánh cần phân biệt của Type

`java.lang.reflect.Type` là giao diện chung cho metadata kiểu. Khi đọc `getGenericSuperclass()`, kết quả cần được kiểm tra bằng `instanceof` rồi mới ép kiểu.

| Dạng `Type` | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| `Class<?>` | `Object`, `String`, superclass raw | Kiểu được biểu diễn trực tiếp bằng đối tượng `Class`, không kèm đối số tham số hóa ở chính vị trí này |
| `ParameterizedType` | `Repository<String>`, `List<Integer>` | Raw type cộng các đối số kiểu, và có thể cả owner type |
| `TypeVariable<?>` | `T` trong `Repository<T>` hoặc `U` từ lớp trung gian | Tham chiếu tới biến kiểu do class, interface hoặc method/constructor khai báo |
| `WildcardType` | `? extends Number`, `? super Integer` | Đối số kiểu wildcard với giới hạn trên/dưới |
| `GenericArrayType` | `U[]` trong một khai báo generic | Kiểu mảng mà component là kiểu generic không thể biểu diễn bằng `Class<?>` thông thường |

Một `ParameterizedType` cung cấp:

```java
ParameterizedType p = (ParameterizedType) generic;

Type rawType = p.getRawType();
Type[] arguments = p.getActualTypeArguments();
Type owner = p.getOwnerType();
```

Đây là đoạn minh họa khi biến `generic` đã được xác định là `ParameterizedType` và đã import `java.lang.reflect.*`.

- `getRawType()` thường là một `Class<?>`, chẳng hạn `Repository.class`.
- `getActualTypeArguments()` trả đối số kiểu của quan hệ tham số hóa, ví dụ `String.class` cho `Repository<String>`. Đối số có thể là các dạng `Type` lồng nhau, nên **không được ép mọi phần tử thành `Class<?>`**.
- `getOwnerType()` có thể quan trọng cho kiểu thành viên nằm bên trong kiểu bao ngoài; các ví dụ top-level/nested static thông thường không cần dựa vào owner.
- Số đối số của một `ParameterizedType` có thể bằng 0 trong một số cấu trúc owner/nested type; không nên giả định mọi `ParameterizedType` đều có đúng một đối số.

Với `TypeVariable<?> v`, `v.getName()` cho biết tên biến; `v.getBounds()` cho biết upper bounds khai báo. Nhưng **bounds không phải giá trị kiểu cụ thể được cung cấp bởi lớp con**. Ví dụ `T extends Number` chỉ nêu ràng buộc, không cho phép tự kết luận `T = Integer`.

## 3. Chương trình Java 17+ hoàn chỉnh

Tạo file `GenericSuperclassDemo.java` như sau:

```java
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GenericSuperclassDemo {
    static class Box<T> {
    }

    static class StringBox extends Box<String> {
    }

    static class Layer<U> extends Box<List<U>> {
    }

    static class IntegerLayer extends Layer<Integer> {
    }

    @SuppressWarnings("rawtypes")
    static class RawBox extends Box {
    }

    static class ArrayBox extends Box<String[]> {
    }

    static class GenericArrayBox<U> extends Box<U[]> {
    }

    static class NumberListBox extends Box<List<? extends Number>> {
    }

    static class Plain {
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

    static String render(Type type) {
        if (type == null) {
            return "null";
        }
        if (type instanceof Class<?> c) {
            if (c.isArray()) {
                return render(c.getComponentType()) + "[]";
            }
            return c.getSimpleName();
        }
        if (type instanceof ParameterizedType p) {
            String args = Arrays.stream(p.getActualTypeArguments())
                    .map(GenericSuperclassDemo::render)
                    .collect(Collectors.joining(", "));
            return render(p.getRawType()) + "<" + args + ">";
        }
        if (type instanceof TypeVariable<?> variable) {
            return variable.getName();
        }
        if (type instanceof GenericArrayType array) {
            return render(array.getGenericComponentType()) + "[]";
        }
        if (type instanceof WildcardType wildcard) {
            if (wildcard.getLowerBounds().length > 0) {
                return "? super " + render(wildcard.getLowerBounds()[0]);
            }
            Type[] upper = wildcard.getUpperBounds();
            if (upper.length == 1 && upper[0] == Object.class) {
                return "?";
            }
            return "? extends " + render(upper[0]);
        }
        return type.getTypeName();
    }

    static void inspect(Class<?> child) {
        Class<?> rawSuperclass = child.getSuperclass();
        Type genericSuperclass = child.getGenericSuperclass();

        String rawName = rawSuperclass == null
                ? "null" : rawSuperclass.getSimpleName();

        System.out.println(child.getSimpleName() + ": raw=" + rawName
                + ", generic=" + render(genericSuperclass));

        if (genericSuperclass instanceof ParameterizedType p) {
            Type[] args = p.getActualTypeArguments();
            for (int i = 0; i < args.length; i++) {
                System.out.println("  arg[" + i + "]="
                        + render(args[i]) + " (" + kind(args[i]) + ")");
            }
        }
    }

    public static void main(String[] args) {
        inspect(StringBox.class);
        inspect(Layer.class);
        inspect(IntegerLayer.class);
        inspect(RawBox.class);
        inspect(ArrayBox.class);
        inspect(GenericArrayBox.class);
        inspect(NumberListBox.class);
        inspect(Plain.class);
        inspect(Object.class);

        ParameterizedType first = (ParameterizedType)
                IntegerLayer.class.getGenericSuperclass();
        Type supplied = first.getActualTypeArguments()[0];

        ParameterizedType second = (ParameterizedType)
                Layer.class.getGenericSuperclass();
        ParameterizedType list = (ParameterizedType)
                second.getActualTypeArguments()[0];
        TypeVariable<?> variable = (TypeVariable<?>)
                list.getActualTypeArguments()[0];

        System.out.println("mapping: " + variable.getName()
                + " -> " + render(supplied));
        System.out.println("resolved Box argument: List<"
                + render(supplied) + ">");
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac GenericSuperclassDemo.java
java GenericSuperclassDemo
```

Kết quả stdout:

```text
StringBox: raw=Box, generic=Box<String>
  arg[0]=String (Class)
Layer: raw=Box, generic=Box<List<U>>
  arg[0]=List<U> (ParameterizedType)
IntegerLayer: raw=Layer, generic=Layer<Integer>
  arg[0]=Integer (Class)
RawBox: raw=Box, generic=Box
ArrayBox: raw=Box, generic=Box<String[]>
  arg[0]=String[] (Class)
GenericArrayBox: raw=Box, generic=Box<U[]>
  arg[0]=U[] (GenericArrayType)
NumberListBox: raw=Box, generic=Box<List<? extends Number>>
  arg[0]=List<? extends Number> (ParameterizedType)
Plain: raw=Object, generic=Object
Object: raw=null, generic=null
mapping: U -> Integer
resolved Box argument: List<Integer>
```

Chương trình tự định dạng `Type` để hiển thị tên ngắn, tránh phụ thuộc vào tên class nội bộ của JDK dùng để hiện thực `ParameterizedType` và tránh in thứ tự mảng Reflection không được bảo đảm. Khi xử lý ứng dụng thật, nên giữ nguyên `Type` để phân tích cấu trúc, chỉ chuyển thành chuỗi khi hiển thị.

## 4. Phân tích chi tiết output

### Trường hợp superclass được tham số hóa cụ thể

`StringBox extends Box<String>` nên:

```java
Type type = StringBox.class.getGenericSuperclass();
boolean hasTypeArguments = type instanceof ParameterizedType; // true
```

Ở đây `((ParameterizedType) type).getRawType() == Box.class` và `getActualTypeArguments()[0] == String.class`. Loại đối số là `Class<?>`.

### Trường hợp lớp trung gian vẫn generic

`Layer<U> extends Box<List<U>>` cho `getGenericSuperclass()` là `Box<List<U>>`, nhưng đối số đầu tiên không phải `Class<List>`. Nó là **`ParameterizedType` biểu diễn `List<U>`**. Trong đối số của `List<U>`, `U` là **`TypeVariable<?>`**.

Cách ép:

```java
Class<?> wrong =
        (Class<?>) ((ParameterizedType)
                Layer.class.getGenericSuperclass())
                .getActualTypeArguments()[0];
```

là **sai tại runtime**: phần tử ở vị trí đó là `ParameterizedType`, do đó có thể ném `ClassCastException`. Phải kiểm tra `instanceof` và duyệt các tầng `Type` lồng nhau.

### Trường hợp raw superclass

`RawBox extends Box` cố ý dùng raw type. `getGenericSuperclass()` trả về `Box.class` — một `Class<?>`, không có `getActualTypeArguments()` để truy ngược kiểu `T`.

Không được giả sử `T = Object` là “đối số kiểu khai báo từ lớp con”. Khi dùng raw type, thông tin đối số đã không được ghi ở quan hệ `extends`. Bounds của `T` có thể gợi ý tính hợp lệ của kiểu, nhưng không phục hồi giá trị generic đã bị bỏ.

### Mảng generic và mảng kiểu cụ thể

`ArrayBox extends Box<String[]>` có đối số `String[].class`, chính là một `Class<?>` của array type.

`GenericArrayBox<U> extends Box<U[]>` lại có đối số kiểu `GenericArrayType`, với component type là biến kiểu `U`. Sự khác biệt này quan trọng khi viết serializer hoặc type resolver cho generic arrays. Không thể ép `U[]` thành `Class<?>` khi `U` còn chưa được ràng buộc.

### Wildcard lồng nhau

`NumberListBox extends Box<List<? extends Number>>` có đối số cấp ngoài là `ParameterizedType` mô tả `List<? extends Number>`. Phần tử bên trong `List` là `WildcardType`. Sử dụng `getUpperBounds()` và `getLowerBounds()` để biết giới hạn; không coi wildcard là một `Class<?>` hoặc kiểu thực tế duy nhất.

## 5. Qua nhiều cấp kế thừa: tự ánh xạ TypeVariable

Đây là phần dễ gây nhầm nhất:

```java
class Box<T> {
}

class Layer<U> extends Box<List<U>> {
}

class IntegerLayer extends Layer<Integer> {
}
```

Gọi:

```java
Type type = IntegerLayer.class.getGenericSuperclass();
```

chỉ nhận được **`Layer<Integer>`**, do superclass trực tiếp của `IntegerLayer` là `Layer`. Gọi tiếp:

```java
Type type2 = Layer.class.getGenericSuperclass();
```

nhận **`Box<List<U>>`**, với `U` vẫn là biến kiểu, chứ không tự thành `Box<List<Integer>>`. Đó là hai declaration riêng, Reflection không thay biến của declaration thứ hai theo mapping phát hiện được ở declaration thứ nhất.

Để suy ra đối số cuối cùng của `Box<T>`, thực hiện:

1. Đọc `IntegerLayer extends Layer<Integer>`: đối số của `Layer<U>` là `Integer`, do đó lập mapping **`U → Integer`**.
2. Đọc `Layer<U> extends Box<List<U>>`: đối số của `Box<T>` là biểu thức kiểu **`List<U>`**.
3. Thay `U` bằng `Integer` trong biểu thức trên: **`List<Integer>`**.
4. Kết luận đối số `T` của `Box<T>` ở quan hệ kế thừa được suy ra là **`List<Integer>`**.

Trong chương trình chạy được ở mục 3, ví dụ chỉ giải một cấp mapping đơn giản. Với công cụ tổng quát, cần xử lý nhiều biến và nhiều cấp, chẳng hạn:

```java
class Pair<A, B> {
}

class Intermediate<X, Y> extends Pair<Y, List<X>> {
}

class FinalPair extends Intermediate<String, Integer> {
}
```

Đây là đoạn minh họa. Mapping ban đầu là `X → String` và `Y → Integer`, nên `FinalPair` nhìn từ `Pair<A,B>` tương ứng `Pair<Integer, List<String>>`.

Để xây resolver đúng cho lớp bất kỳ, nên giữ một `Map<TypeVariable<?>, Type>` thay vì chỉ dựa vào tên biến như `"T"`. Hai class khác nhau có thể cùng khai báo biến `T` nhưng đó là **hai `TypeVariable` thuộc hai `GenericDeclaration` khác nhau**. Nếu chỉ map chuỗi tên, dễ thay sai biến.

Một thuật toán tổng quát:

- Bắt đầu từ `Class<?>` lớp con.
- Ở mỗi bước, lấy `getGenericSuperclass()`. Nếu là `ParameterizedType`, lấy raw superclass và ghép danh sách `rawClass.getTypeParameters()` với `getActualTypeArguments()` theo vị trí.
- Trước khi lưu mapping mới, **thay thế đệ quy** những `TypeVariable` đã có mapping từ các cấp con trong từng đối số.
- Tiếp tục tới superclass tiếp theo cho tới lớp generic đích hoặc `Object`.
- Khi gặp `ParameterizedType`, `GenericArrayType` hoặc `WildcardType` lồng nhau, phải thay thế trong cấu trúc con; không chỉ thay nếu đối số trực tiếp là `TypeVariable`.
- Nếu gặp raw edge, biến không có mapping hoặc chuỗi kế thừa không đi tới target, **giữ trạng thái chưa phân giải** thay vì đoán một `Class<?>` cụ thể.

Nếu công cụ cần xuất ra một đối tượng `Type` hoàn chỉnh sau thay thế, hãy dùng biểu diễn Type phù hợp hoặc thư viện resolver đã được kiểm chứng, đồng thời kiểm tra compatibility với JDK/module path. Việc tạo string `"List<Integer>"` chỉ để in demo **không tương đương** đã xây xong một `ParameterizedType` đúng ngữ nghĩa.

## 6. Phân biệt TypeVariable, bounds và đối số thực tế

Xét:

```java
class NumericBox<T extends Number> {
}

class LongBox extends NumericBox<Long> {
}

class StillGeneric<N extends Number> extends NumericBox<N> {
}
```

Các trường hợp:

- `LongBox.class.getGenericSuperclass()` là `NumericBox<Long>`; argument là `Long.class`, đã cụ thể.
- `StillGeneric.class.getGenericSuperclass()` là `NumericBox<N>`; argument là `TypeVariable<N>`, chưa chọn `Integer`, `Long` hay loại số nào.
- `N.getBounds()` chỉ cho biết upper bound `Number`. Nó **không nói** lớp con đã chọn `Number` làm actual type argument.
- Với `NumericBox<T>`, `NumericBox.class.getTypeParameters()` trả về danh sách biến kiểu do `NumericBox` khai báo, ví dụ `T`. Đây là **type parameter declaration**, khác `getActualTypeArguments()` của quan hệ `extends NumericBox<Long>`.

Trong code generic có nhiều cấp, có thể một biến kiểu vẫn chưa được giải xong. Trường hợp đó, kết quả hợp lệ của resolver có thể là một `TypeVariable<?>`, không nhất thiết là `Class<?>`. Chỉ ép về class khi chắc chắn đã đạt một concrete type là `Class<?>`.

## 7. Anonymous subclass và kỹ thuật type token

Cách tạo object trực tiếp:

```java
Box<String> value = new Box<String>();
Type generic = value.getClass().getGenericSuperclass();
```

không khôi phục được đối số `String`: lớp runtime của `value` vẫn là `Box.class`, và superclass của `Box` là `Object`.

Nhưng nếu tạo **anonymous subclass** có superclass generic cụ thể:

```java
Box<String> value = new Box<String>() {};

Type generic = value.getClass().getGenericSuperclass();
boolean captured = generic instanceof ParameterizedType; // true
```

đây là đoạn minh họa có lớp `Box<T>` từ ví dụ trước. Anonymous class thực sự **extends `Box<String>`**, nên generic superclass signature của chính anonymous class có thể chứa `String`. Kỹ thuật này là nền tảng cho các **type token**:

```java
abstract class TypeToken<T> {
    protected final Type capturedType;

    protected TypeToken() {
        Type parent = getClass().getGenericSuperclass();
        if (!(parent instanceof ParameterizedType p)) {
            throw new IllegalStateException(
                    "Subclass must provide a concrete type argument");
        }
        this.capturedType = p.getActualTypeArguments()[0];
    }

    public Type type() {
        return capturedType;
    }
}
```

Đây là lớp **minh họa** cần import `java.lang.reflect.Type` và `ParameterizedType`. Ví dụ sử dụng:

```java
TypeToken<List<String>> token = new TypeToken<List<String>>() {};
Type captured = token.type(); // Metadata của List<String>
```

**Giới hạn cần hiểu:** type token này đọc **superclass trực tiếp của anonymous subclass**, đúng với mẫu khởi tạo đã nêu. Nếu thêm một lớp trung gian giữa anonymous class và `TypeToken<T>`, cần resolver qua nhiều cấp. Một type token kiểu này cũng không thể tự biến biến kiểu chưa ràng buộc ở nơi gọi thành kiểu cụ thể; ví dụ nếu mã bao ngoài vẫn generic `<U>` thì token có thể chứa `TypeVariable<U>`.

Type erasure vẫn tồn tại. Type token chỉ tận dụng metadata của **generic superclass declaration cụ thể**, không làm mọi instance generic tự mang runtime type arguments.

## 8. Superclass khác interface generic

`getGenericSuperclass()` đi theo **nhánh class extends**, không thay thế việc đọc quan hệ implements hoặc extends interface.

```java
interface Handler<T> {
}

class TextHandler implements Handler<String> {
}
```

Đây là đoạn minh họa. `TextHandler.class.getGenericSuperclass()` trả `Object.class` vì superclass trực tiếp của class là `Object`. Muốn xem `Handler<String>`, hãy dùng:

```java
Type[] interfaces = TextHandler.class.getGenericInterfaces();
```

Kết quả có một `ParameterizedType` tương ứng `Handler<String>`. Với interface kế thừa nhiều interface khác, cần duyệt riêng cây superinterfaces và ánh xạ type variables dọc các cạnh đó.

Nếu mục tiêu là tìm một supertype generic bất kể nó nằm trong đường `extends class` hay `implements interface`, resolver phải xem **cả `getGenericSuperclass()` và `getGenericInterfaces()`**, và tránh lặp khi một interface xuất hiện qua nhiều nhánh.

## 9. Kiểm tra trước khi lấy actual type arguments

Một lỗi phổ biến:

```java
ParameterizedType p = (ParameterizedType)
        child.getGenericSuperclass();
Class<?> argument = (Class<?>)
        p.getActualTypeArguments()[0];
```

Đây là đoạn minh họa; nó chỉ chạy an toàn nếu:

1. `getGenericSuperclass()` thực sự trả `ParameterizedType`, không phải `Class<?>` hoặc `null`.
2. Mảng actual type arguments đủ dài để truy cập chỉ số 0.
3. Đối số đầu là `Class<?>`, không phải nested `ParameterizedType`, `TypeVariable<?>` hay `GenericArrayType`.

Viết code có kiểm tra:

```java
Type parent = child.getGenericSuperclass();

if (parent instanceof ParameterizedType p) {
    Type[] args = p.getActualTypeArguments();
    if (args.length == 1) {
        Type argument = args[0];
        if (argument instanceof Class<?> concreteClass) {
            System.out.println("Concrete: " + concreteClass.getName());
        } else {
            System.out.println("Needs resolution: " + argument.getTypeName());
        }
    }
}
```

Đây là **đoạn minh họa**, biến `child` cần là `Class<?>` hợp lệ và đã import `java.lang.reflect.Type`, `ParameterizedType`. Khi thư viện yêu cầu một kiểu cụ thể, không nên âm thầm fallback về `Object.class` đối với `TypeVariable` chưa giải được; cần trả lỗi rõ ràng hoặc giữ `Type` để tầng gọi quyết định.

Khi đọc generic signature từ bytecode, Reflection có thể gặp `GenericSignatureFormatError` nếu chữ ký không hợp lệ, `TypeNotPresentException` nếu metadata nhắc tới kiểu không có sẵn, hoặc `MalformedParameterizedTypeException` với cấu trúc tham số không tạo được. Vì vậy công cụ xử lý class bên ngoài hoặc plugin phải có đường báo lỗi metadata, thay vì giả định mọi class file luôn có chữ ký hợp lệ và đầy đủ.

## 10. Phân biệt type erasure và metadata đã lưu

Type erasure không có nghĩa *mọi* thông tin generic đều bị xóa sạch. JVM thường thực thi trên raw classes, nhưng compiler có thể lưu **generic signature** trong class file để Reflection đọc lại:

| Tình huống | Reflection có thể biết gì? |
| --- | --- |
| `new Box<String>()` | Runtime class là `Box.class`; không biết riêng đối số `String` từ chính instance |
| `class StringBox extends Box<String>` | Có thể đọc `Box<String>` từ `StringBox.class.getGenericSuperclass()` |
| `class Layer<U> extends Box<List<U>>` | Đọc `Box<List<U>>` nhưng `U` vẫn là biến kiểu |
| `class IntegerLayer extends Layer<Integer>` | Đọc `Layer<Integer>`; muốn đi sâu phải thay thế `U` trên cạnh tiếp theo |
| `class RawBox extends Box` | Không có actual type argument được khai báo trên raw edge |
| `class StringHandler implements Handler<String>` | Phải đọc qua `getGenericInterfaces()` |

Đối với framework serializer/ORM/DI, vấn đề thiết kế thường là **giữ được `Type` đúng biểu thức** trước khi cố lấy `Class<?>`. Các kiểu như `List<String>` và `List<Integer>` có cùng raw type `List.class` nhưng khác type argument, có thể ảnh hưởng quy tắc deserialize và validation.

## 11. Những nhầm lẫn thường gặp

| Nhầm lẫn | Sự thật |
| --- | --- |
| `getSuperclass()` trả đối số generic của superclass | Nó trả `Class<?>` raw; muốn giữ signature dùng `getGenericSuperclass()` |
| `getGenericSuperclass()` luôn là `ParameterizedType` | Có thể là `Class<?>` hoặc `null` |
| Mọi `getActualTypeArguments()[i]` đều là `Class<?>` | Có thể là `ParameterizedType`, `TypeVariable`, `GenericArrayType`, `WildcardType` |
| `Layer<U> extends Box<List<U>>` có đối số `List.class` | Đối số thực là biểu thức `List<U>`, tức `ParameterizedType` |
| `IntegerLayer extends Layer<Integer>` khiến `Layer.class.getGenericSuperclass()` tự đổi thành `Box<List<Integer>>` | Metadata của `Layer.class` vẫn là `Box<List<U>>`; phải ánh xạ `U → Integer` |
| Bound `T extends Number` có nghĩa actual type là `Number` | Bounds là ràng buộc của biến kiểu, không phải giá trị type argument lớp con đã chọn |
| Lớp raw `extends Box` có thể suy ra argument chính xác từ `Box.class` | Raw edge không ghi actual type argument |
| `new Box<String>().getClass()` nhớ được `String` | Runtime class vẫn là `Box.class`; khác với anonymous subclass type token |
| `getGenericSuperclass()` tự xử lý `implements Handler<String>` | Muốn đọc interface dùng `getGenericInterfaces()` |
| Dùng tên biến `"T"` làm khóa resolver | Biến `T` ở hai generic declarations khác nhau có thể là hai biến khác nhau |
| Một `Type.getTypeName()` đủ để dùng làm runtime class | Type có thể chứa cấu trúc generic chưa giải; đừng coi chuỗi hiển thị là `Class<?>` |
| Class file luôn chứa metadata generic hợp lệ | Metadata có thể thiếu/hỏng hoặc nhắc tới type không tìm thấy; cần xử lý lỗi |

## 12. Câu hỏi tự kiểm tra

Dựa trên các lớp ở mục 3:

1. **`StringBox.class.getSuperclass()` trả gì?** `Box.class`, không có đối số kiểu.
2. **`StringBox.class.getGenericSuperclass()` thuộc dạng nào?** `ParameterizedType` biểu diễn `Box<String>`.
3. **Đối số đầu của `Layer.class.getGenericSuperclass()` là gì?** `ParameterizedType` biểu diễn `List<U>`; bên trong có `TypeVariable<U>`.
4. **`IntegerLayer.class.getGenericSuperclass()` có phải `Box<List<Integer>>` không?** Không; nó chỉ là superclass trực tiếp `Layer<Integer>`.
5. **Muốn suy ra argument của `Box<T>` nhìn từ `IntegerLayer`, cần làm gì?** Lần theo `IntegerLayer → Layer → Box` và thay `U → Integer` để nhận biểu thức `List<Integer>`.
6. **`RawBox.class.getGenericSuperclass()` có `getActualTypeArguments()` không?** Không, kết quả là `Class<?>` biểu diễn `Box`.
7. **Đối số của `Box<U[]>` có bắt buộc là `Class<?>` không?** Không; `U[]` được mô tả bằng `GenericArrayType` khi `U` chưa được ràng buộc.
8. **`new Box<String>()` và `new Box<String>() {}` khác nhau ở đâu?** Vế thứ hai tạo anonymous subclass khai báo `extends Box<String>`; generic superclass metadata của anonymous class có thể lưu `String`.
9. **Một class `implements Handler<String>` nhưng không extends class generic, đọc thông tin kiểu ở đâu?** `getGenericInterfaces()`.

**Ghi nhớ:** `getGenericSuperclass()` cho bạn **biểu thức generic của superclass trực tiếp**. Khi biểu thức đó được tham số hóa, `ParameterizedType.getActualTypeArguments()` cho các `Type` thành phần. Với nhiều tầng kế thừa, giữ cấu trúc `Type` và ánh xạ `TypeVariable` qua từng cạnh; chỉ kết luận một `Class<?>` cụ thể khi metadata thật sự đủ căn cứ.

### Tài liệu tham khảo

- [Java SE 17 — Class.getGenericSuperclass()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getGenericSuperclass())
- [Java SE 17 — ParameterizedType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html)
- [Java SE 17 — TypeVariable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/TypeVariable.html)
- [Java SE 17 — GenericArrayType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/GenericArrayType.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-resolution-003">Ánh xạ TypeVariable sang kiểu cụ thể qua nhiều tầng kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Ánh xạ `TypeVariable` sang kiểu cụ thể qua nhiều tầng kế thừa

## 1. Vấn đề: một field được khai báo kiểu `T`, nhưng lớp con đã chọn `String`

Trong Java, generic inheritance có thể truyền tham số kiểu qua nhiều cấp. Xét chuỗi:

~~~java
class Base<T> {
    T item;
}
class Middle<U> extends Base<List<U>> {
}
class Leaf extends Middle<String> {
}
~~~

Khi nhìn `Base.class`, field `item` có kiểu generic là **`T`**; không có `String` nào trong khai báo của `Base`. Tuy nhiên khi nhìn từ `Leaf`, ta lần theo:

~~~text
Leaf extends Middle<String>
     U = String

Middle<U> extends Base<List<U>>
     T = List<U>

Thay U = String vào T = List<U>:
     T = List<String>

Base<T>.item, xét từ Leaf:
     List<String>
~~~

**Đây là phép phân giải kiểu trong ngữ cảnh của lớp con**, không phải thay đổi metadata gốc của `Base`. `Base.class.getDeclaredField("item").getGenericType()` vẫn trả một `TypeVariable` đại diện `T`; Reflection không có API một bước tự biến mọi `T` thành `List<String>` theo lớp con tùy ý. Bạn phải đọc generic superclass/interface, xây dựng ánh xạ biến kiểu và áp dụng lần lượt các phép thế.

Các framework như serializer, ORM, dependency injection container hoặc type-aware API generator thường phải giải bài toán này: cùng một generic base class có thể được chuyên biệt hóa thành nhiều kiểu cụ thể tùy lớp con thực tế.

## 2. Những biểu diễn `Type` phải hiểu

`java.lang.reflect.Type` là giao diện đại diện thông tin kiểu phản chiếu. Các biểu diễn quan trọng:

| Loại `Type` | Ví dụ | Reflection cung cấp |
| --- | --- | --- |
| `Class<?>` | `String.class`, `List.class`, `int.class` | Kiểu cụ thể hoặc raw type |
| `TypeVariable<?>` | `T` trong `Base<T>`, `U` trong `Middle<U>` | Tham số kiểu do class/method/constructor khai báo |
| `ParameterizedType` | `Base<List<U>>`, `List<String>` | Raw type cùng danh sách actual type arguments |
| `WildcardType` | `? extends T`, `? super Number` | Upper/lower bounds của wildcard |
| `GenericArrayType` | `T[]` hoặc `List<T>[]` | Generic component type khi thành phần chưa phải kiểu class thông thường |

Một `TypeVariable` có **declaration identity**, không chỉ có tên. Hai class có thể cùng khai báo tham số kiểu tên `T` nhưng chúng là **hai biến kiểu khác nhau**. Khi xây dựng bảng ánh xạ, khóa nên là `TypeVariable<?>`, chẳng hạn `Map<TypeVariable<?>, Type>`, thay vì `Map<String, Type>`; ánh xạ theo mỗi chuỗi `"T"` có thể đè nhầm biến đến từ declaration khác.

Một `TypeVariable` có thể có bound:

~~~java
class Store<T extends Number> {
    T amount;
}
~~~

`T` vẫn là một biến kiểu. `T.getBounds()` phản ánh bound `Number`, **không** có nghĩa mọi instance của `Store` dùng đúng `Number`. Ví dụ `Store<Integer>` khiến `T` được ràng buộc với `Integer` trong ngữ cảnh lớp con tương ứng. Không dùng bound làm kết quả concrete khi chưa có ánh xạ.

## 3. Các API Reflection nối các mắt xích kế thừa

Một lớp `Leaf extends Middle<String>` chứa dữ liệu quan trọng trong `Leaf.class.getGenericSuperclass()`:

- Nếu superclass có type arguments, kết quả thường là `ParameterizedType`. `getRawType()` trỏ tới `Middle.class`, `getActualTypeArguments()` chứa `String.class`.
- `Middle.class.getTypeParameters()` trả danh sách biến kiểu của `Middle`, ở đây là `U`. Ghép từng formal type variable với actual type argument theo **vị trí**: `U → String`.
- Tiếp theo, `Middle.class.getGenericSuperclass()` biểu diễn `Base<List<U>>`. `Base.class.getTypeParameters()` trả `T` và actual argument là `List<U>`: ghi `T → List<U>`.
- Áp dụng ánh xạ đệ quy: `T → List<U> → List<String>`.

| Bước trên đường từ lớp cụ thể | Formal parameter thuộc declaration cha | Actual argument ở cạnh kế thừa |
| --- | --- | --- |
| `Leaf → Middle<String>` | `Middle.U` | `String` |
| `Middle<U> → Base<List<U>>` | `Base.T` | `List<U>` |
| Phân giải `Base.T` trong ngữ cảnh `Leaf` | `Base.T` | `List<String>` |

Cần lấy cạnh kế thừa từ **`getGenericSuperclass()`**, không phải `getSuperclass()` đơn thuần. API `getSuperclass()` chỉ trả raw `Class` và làm mất thông tin argument `String` hoặc `List<U>` trên cạnh đó.

Tương tự, với generic interface, phải dùng `getGenericInterfaces()` cùng `ParameterizedType` để lấy argument. Nhưng interface có thể tạo thành đồ thị nhiều nhánh, không phải chỉ một chuỗi superclass; cần thuật toán duyệt các cạnh interface và xác định declaration đang cần phân giải.

## 4. Ví dụ Java 17+ chạy được: ánh xạ và phân giải nested generic

Tạo file `GenericTypeResolverDemo.java`:

~~~java
import java.lang.reflect.*;
import java.util.*;

public class GenericTypeResolverDemo {
    static class Base<T> {
        T item;
        List<T> history;
        List<? extends T> readable;
    }

    static class Middle<U> extends Base<List<U>> {
    }

    static class Leaf extends Middle<String> {
    }

    static Map<TypeVariable<?>, Type> bindings(
            Class<?> concrete, Class<?> declaringClass) {
        Map<TypeVariable<?>, Type> result = new LinkedHashMap<>();

        if (!declaringClass.isAssignableFrom(concrete)) {
            throw new IllegalArgumentException("Not an ancestor");
        }

        Class<?> current = concrete;
        while (current != declaringClass) {
            Type parent = current.getGenericSuperclass();
            if (parent == null) {
                throw new IllegalArgumentException("No superclass path");
            }

            if (parent instanceof ParameterizedType p) {
                Class<?> raw = (Class<?>) p.getRawType();
                TypeVariable<?>[] formals = raw.getTypeParameters();
                Type[] actuals = p.getActualTypeArguments();

                for (int i = 0; i < formals.length; i++) {
                    result.put(formals[i], actuals[i]);
                }
                current = raw;
            } else {
                // Một cạnh extends không mang generic arguments.
                current = (Class<?>) parent;
            }
        }
        return result;
    }

    // Hiển thị kiểu sau thế biến. Đây là ví dụ cho đường superclass.
    static String render(
            Type type, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof TypeVariable<?> variable) {
            Type replacement = bindings.get(variable);
            if (replacement == null || replacement.equals(variable)) {
                return variable.getName();
            }
            return render(replacement, bindings);
        }

        if (type instanceof ParameterizedType p) {
            List<String> parts = new ArrayList<>();
            for (Type argument : p.getActualTypeArguments()) {
                parts.add(render(argument, bindings));
            }
            return render(p.getRawType(), bindings)
                    + "<" + String.join(", ", parts) + ">";
        }

        if (type instanceof WildcardType w) {
            if (w.getLowerBounds().length > 0) {
                return "? super "
                        + render(w.getLowerBounds()[0], bindings);
            }
            Type upper = w.getUpperBounds()[0];
            return upper == Object.class
                    ? "?"
                    : "? extends " + render(upper, bindings);
        }

        if (type instanceof GenericArrayType a) {
            return render(a.getGenericComponentType(), bindings) + "[]";
        }

        if (type instanceof Class<?> c) {
            return c.isArray()
                    ? render(c.getComponentType(), bindings) + "[]"
                    : c.getSimpleName();
        }

        return type.getTypeName();
    }

    public static void main(String[] args) throws Exception {
        Map<TypeVariable<?>, Type> map =
                bindings(Leaf.class, Base.class);

        for (TypeVariable<?> variable : Middle.class.getTypeParameters()) {
            System.out.println(variable.getName() + " -> "
                    + render(variable, map));
        }
        for (TypeVariable<?> variable : Base.class.getTypeParameters()) {
            System.out.println(variable.getName() + " -> "
                    + render(variable, map));
        }

        for (String name : List.of("item", "history", "readable")) {
            Field field = Base.class.getDeclaredField(name);
            System.out.println(name
                    + ": raw=" + field.getType().getSimpleName()
                    + ", generic=" + field.getGenericType().getTypeName()
                    + ", resolved=" + render(field.getGenericType(), map));
        }
    }
}
~~~

Lệnh biên dịch và thực thi:

~~~bash
javac --release 17 GenericTypeResolverDemo.java
java GenericTypeResolverDemo
~~~

Đầu ra:

~~~text
U -> String
T -> List<String>
item: raw=Object, generic=T, resolved=List<String>
history: raw=List, generic=java.util.List<T>, resolved=List<List<String>>
readable: raw=List, generic=java.util.List<? extends T>, resolved=List<? extends List<String>>
~~~

Kết quả đã được kiểm tra bằng đoạn tương đương trong JShell: biên dịch/cung cấp type metadata Java 17 và chạy trên JDK 24 cho ra đúng các dòng trên.

### Phân tích từng field

- `item` của `Base<T>`: `Field.getType()` trả `Object.class` vì erasure của `T` không có bound là `Object`; `getGenericType()` vẫn là `TypeVariable T`. Trong ngữ cảnh `Leaf`, `T` được giải thành `List<String>`.
- `history`: khai báo `List<T>`. Khi `T → List<String>` thì toàn biểu thức thành `List<List<String>>`. Thay biến kiểu phải **đệ quy bên trong argument** của `ParameterizedType`.
- `readable`: khai báo `List<? extends T>`. Kết quả là `List<? extends List<String>>`. Không bỏ wildcard khi phân giải; `? extends` vẫn mang ý nghĩa variance của API.
- `U` của `Middle<U>` ánh xạ sang `String`, nên `T` của `Base<T>` có thể được giải tiếp thông qua `List<U>`.

Trong ví dụ, `render` trả chuỗi để **giải thích kết quả**, không tạo lại một đối tượng `Type` mới. Nếu framework thực sự cần đối tượng `ParameterizedType` đã được thay type argument, cần tạo biểu diễn bất biến tuân thủ hợp đồng `ParameterizedType` (hoặc dùng thư viện type-resolution đã được kiểm chứng) và xử lý `ownerType`, `WildcardType`, `GenericArrayType`, equality/hashCode.

## 5. Tại sao bảng ánh xạ phải đi từ lớp cụ thể lên lớp khai báo?

Bạn bắt đầu ở nơi **biết argument cụ thể**, ví dụ `Leaf extends Middle<String>`. Nếu chỉ đọc `Middle.class` và `Base.class` độc lập thì chưa có thông tin nào bảo đảm `U` là `String`; chúng chỉ biết quan hệ `T = List<U>`.

Quy trình tổng quát với một đường superclass:

1. Nhận `Class<?> concrete` là class thực tế và `Class<?> declaring` là class khai báo field/method đang cần phân giải.
2. Xác định tuyến `concrete → ... → declaring` qua `getGenericSuperclass()`.
3. Trên từng cạnh `Subclass extends Parent<...>`, so sánh `Parent.getTypeParameters()` với `ParameterizedType.getActualTypeArguments()` để ghi ánh xạ `TypeVariable → Type`.
4. Khi gặp biến kiểu trong field, method hoặc constructor, tìm binding dựa vào chính đối tượng `TypeVariable<?>` và thay đệ quy cho tới khi hết ánh xạ.
5. Khi gặp `ParameterizedType`, thay **từng argument**; với wildcard và generic array, duyệt các bound/component type theo cấu trúc.
6. Nếu còn biến kiểu chưa giải được, giữ nó ở trạng thái **unresolved** hoặc biểu diễn rõ ràng là chưa biết; không mặc định gán `Object` hay upper bound thành concrete type.

Trong ví dụ, map cố ý lưu `T → List<U>` chứ không đòi biến ngay thành `List<String>` tại thời điểm chèn. Khi `render(T, map)` được gọi, nó lần theo `T → List<U>` và nhìn vào `U → String` để cho kết quả cuối.

Với generic field khai báo trong superclass, đừng dùng `leaf.getDeclaredField("item")`: `getDeclaredField` chỉ tìm field khai báo trực tiếp ở `Leaf`. Hãy tìm `Base.class.getDeclaredField("item")` hoặc đi từ field tìm được qua `field.getDeclaringClass()`; sau đó dùng lớp concrete làm ngữ cảnh phân giải.

## 6. Generic method và biến kiểu riêng của method

`Method.getGenericReturnType()` và `Method.getGenericParameterTypes()` cũng có thể chứa `TypeVariable`. Cần phân biệt hai nguồn:

~~~java
class Container<T> {
    T find() {
        return null;
    }

    <R> R convert(R value) {
        return value;
    }
}
~~~

- `T` thuộc khai báo **class** `Container<T>`. Nếu có `StringContainer extends Container<String>` thì mapping của generic superclass cho phép suy ra return type của `find()` là `String` trong ngữ cảnh đó.
- `R` thuộc khai báo **method** `convert`, không phải biến kiểu của `Container`. Chỉ biết subclass của `Container<String>` **không** đủ để suy ra `R` là kiểu gì; muốn xác định `R` phải có thêm ngữ cảnh áp dụng phương thức hoặc type inference tương ứng.

Bạn có thể gọi `TypeVariable.getGenericDeclaration()` để xác định biến kiểu được khai báo bởi `Class`, `Method` hay `Constructor`. Điều này giúp tránh ánh xạ nhầm một `T` ở method với `T` ở class, dù tên giống nhau.

**Type erasure:** generic metadata cho phép phân tích ràng buộc kiểu, nhưng JVM không biến đối tượng `new ArrayList<String>()` thành class runtime riêng biệt với `new ArrayList<Integer>()`. `obj.getClass()` chỉ cho `ArrayList.class`. Nếu mã cần biết `String`, nguồn thông tin đáng tin phải nằm trong superclass/interface generic được khai báo, `Type` được capture, annotation hoặc một type token tương ứng; nó không thể luôn khôi phục từ object runtime.

## 7. Mở rộng sang generic interface

Ví dụ:

~~~java
interface Handler<T> {
    void handle(T value);
}

interface NamedHandler<U> extends Handler<List<U>> {
}

class StringHandler implements NamedHandler<String> {
    @Override
    public void handle(List<String> value) {
    }
}
~~~

Quan hệ cần giải là:

~~~text
StringHandler -> NamedHandler<String>
NamedHandler<U> -> Handler<List<U>>

U = String
Handler.T = List<U> = List<String>
~~~

Mỗi đường nối **interface** lấy qua `Class.getGenericInterfaces()`; raw interface `Handler.class` có type parameter `T`, còn cạnh generic cần đọc các actual type arguments. Từ `StringHandler.class` đi lên có thể gặp nhiều interface, superclass chứa interface, generic interface lồng nhau: vì thế thuật toán phải duyệt **đồ thị interface + superclass**, tìm đúng declaration mục tiêu rồi tích lũy mapping theo từng đường hợp lệ.

Đoạn khai báo trên là ví dụ lý thuyết; mã `bindings` ở mục 4 **chỉ hỗ trợ một chuỗi superclass** nên không được đem áp dụng trực tiếp cho `StringHandler → Handler` mà kết luận sai rằng không có đường. Một triển khai đủ dùng trong framework nên thêm duyệt `getGenericInterfaces()` và kiểm tra những interface lặp trong đồ thị.

## 8. Các trường hợp mà một mapping đơn giản chưa đủ

### A. Raw type làm mất argument

~~~java
class RawMiddle extends Base { }
class RawLeaf extends RawMiddle { }
~~~

Khi chọn `extends Base` không có type argument, cạnh generic không cung cấp `T → SomeType`. Một thuật toán nghiêm ngặt cần nhận diện việc mất metadata do dùng raw type. Nó **không thể suy ra** một `String` cụ thể chỉ từ thông tin đã bị xóa; `T` có thể còn unresolved, mặc dù erasure runtime tương ứng bound của `T`.

### B. Bounded variable và wildcard

~~~java
class NumberBase<T extends Number> {
    List<? super T> consumer;
}
class IntegerBase extends NumberBase<Integer> {
}
~~~

Từ `IntegerBase`, ánh xạ là `T → Integer`; kiểu của field trở thành `List<? super Integer>`. **Không biến** nó thành `List<Integer>` vì wildcard `? super` không đồng nhất với concrete argument `Integer`. Upper/lower bound của wildcard là metadata cần giữ, cũng như bound của type variable khi còn unresolved.

### C. Generic array

~~~java
class ArrayBase<T> {
    T[] values;
}
class ArrayChild extends ArrayBase<String> {
}
~~~

`ArrayBase.values` có `GenericArrayType` tương ứng `T[]`; từ `ArrayChild` có thể diễn giải về mặt kiểu là `String[]`. Nhưng lời giải trình bày dưới dạng chuỗi chỉ dành cho đọc hiểu. Nếu cần tạo `Type` có thể dùng trong framework, phân biệt `Class<?>` của mảng reifiable, `GenericArrayType` và khả năng runtime khởi tạo mảng một cách an toàn.

### D. Nested/owner types

Ví dụ `Outer<String>.Inner<Integer>` có `ownerType` và các actual type arguments. Chỉ lấy `getRawType()` + `getActualTypeArguments()` rồi tự ghép thành chuỗi chưa đủ để biểu diễn tất cả ý nghĩa kiểu lồng. Resolver thực tế phải xét `ParameterizedType.getOwnerType()`, đặc biệt với non-static inner class.

### E. Chu kỳ bounds và biến chưa ràng buộc

`class Node<T extends Comparable<T>>` chứa quan hệ tự tham chiếu trong **bound**. Một resolver đầy đủ không được đệ quy vô hạn khi phân tích bound hoặc alias. Nó nên có tập `visited`/đánh dấu ngữ cảnh đang phân giải và trả trạng thái unresolved/cyclic khi cần. Hàm `render` trong ví dụ chỉ giải ánh xạ tuyến tính, không phải parser/renderer tổng quát cho mọi Java type.

### F. Class loader và tên biến giống nhau

Một `TypeVariable` gắn với đúng declaration của nó. Nếu nhiều thư viện hoặc class loader cung cấp các lớp có tên đơn giản giống nhau, ánh xạ dựa vào chuỗi như `"T"`, `"U"` rất dễ sai. Dùng đối tượng `TypeVariable<?>` và khai báo nguồn; để log/debug nên in đầy đủ `getGenericDeclaration()` khi cần phân biệt.

## 9. Phân biệt `getType()`, `getGenericType()` và kiểu sau phân giải

| Câu hỏi | API/thao tác | Trên field `Base.item` trong ví dụ |
| --- | --- | --- |
| JVM biết kiểu field sau erasure là gì? | `Field.getType()` | `Object.class` |
| Khai báo generic ban đầu ở Base viết gì? | `Field.getGenericType()` | `TypeVariable T` |
| Trong ngữ cảnh Leaf, T đã được ràng buộc thế nào? | Duyệt `getGenericSuperclass()` và thay type variables | `List<String>` |
| `obj.getClass()` có tiết lộ type argument `String` không? | Class runtime của object | Không đủ để khôi phục generic argument tùy ý |

`getType()` không sai khi trả `Object.class`: JVM dùng type erasure cho descriptor. `getGenericType()` giữ thông tin khai báo để Reflection có thể tái dựng *một phần* quan hệ generic. Việc phân giải đầy đủ cần bối cảnh cụ thể của subclass và các cạnh type arguments.

Đối với method parameter, các API tương ứng là `getParameterTypes()` và `getGenericParameterTypes()`. Đối với return type của method, dùng `getReturnType()` và `getGenericReturnType()`. Việc trả về generic type ban đầu không có nghĩa đã tự áp dụng binding của subclass.

## 10. Các lỗi thường gặp và cách sửa

| Lỗi thường gặp | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| Lấy `Base.class.getDeclaredField("item").getType()` rồi kết luận field là Object ở mọi ngữ cảnh | Đây chỉ là erased type | Kết hợp `getGenericType()` với binding từ concrete subclass |
| Gọi `getSuperclass()` để đi qua generic inheritance | Mất actual type arguments | Dùng `getGenericSuperclass()` |
| Lấy `getGenericSuperclass()` của `Base`, không xét `Leaf` | Base chưa biết argument cụ thể | Bắt đầu từ concrete class có liên kết generic cụ thể |
| Chỉ thay `T` ở lớp trực tiếp, không thay `U` bên trong `List<U>` | Phép thế chưa đệ quy | Đi theo chuỗi mapping cho tới `List<String>` |
| Map type variable bằng tên `"T"` | Các declaration có thể trùng tên | Dùng `Map<TypeVariable<?>, Type>` |
| Bỏ `? extends`/`? super` khi thay kiểu | Mất variance và bound | Phân giải bên trong wildcard, giữ cấu trúc wildcard |
| Coi `T extends Number` đồng nghĩa `T = Number` | Bound là giới hạn, không phải actual argument | Giữ `T` unresolved nếu không có binding |
| Dùng thuật toán một superclass cho đường generic interface | Interface là đồ thị | Duyệt thêm `getGenericInterfaces()` |
| Dùng `new ArrayList<String>().getClass()` để suy ra String | Type erasure | Cần metadata generic trên khai báo hoặc type token |
| Tự chế `ParameterizedType` chỉ bằng chuỗi | Chuỗi không đáp ứng giao diện Type, equality, owner, arrays | Tạo cấu trúc Type đầy đủ hoặc dùng thư viện đã kiểm chứng |

## 11. Tự kiểm tra

**Câu 1:** `class A<T>`, `class B<U> extends A<List<U>>`, `class C extends B<Integer>`. Trong `C`, `A.T` là gì? **`List<Integer>`**.

**Câu 2:** `Field.getGenericType()` trên `A.class` trả `T` hay `List<Integer>`? **`T`**, vì đây là metadata của khai báo `A`; việc phân giải là phép thế theo ngữ cảnh `C`.

**Câu 3:** Vì sao dùng `TypeVariable.getName()` làm khóa có thể sai? **Vì hai declaration khác nhau có thể cùng gọi biến là `T`**.

**Câu 4:** Nếu có `List<? extends T>` và `T → String` thì kiểu sau phân giải là gì? **`List<? extends String>`**, không tự biến thành `List<String>`.

**Câu 5:** Nếu `class B extends A` dùng raw type, có thể chắc chắn `A.T` là `String` không? **Không**, vì cạnh đó không cung cấp concrete type argument.

**Câu 6:** Với `interface I<T>`, lớp triển khai có thể phân giải `T` qua `getGenericSuperclass()` duy nhất không? **Không phải lúc nào cũng được**; phải xét cạnh interface từ `getGenericInterfaces()` và có thể cả nhiều nhánh.

**Câu 7:** Generic method `<R> R convert(R value)` nằm trong `A<T>`. Ràng buộc `A.T → String` có tự làm `R → String` không? **Không**; `R` thuộc declaration của method và cần ngữ cảnh suy luận riêng.

**Ghi nhớ:** Để ánh xạ `TypeVariable` qua nhiều tầng kế thừa, phải giữ **đúng declaration của biến**, đọc **generic edges** từ lớp concrete về nơi khai báo và **thay đệ quy** bên trong nested parameterized types, wildcard, array. Nếu không có đủ metadata (raw type, erasure, method variable chưa ràng buộc), hãy biểu diễn kết quả chưa xác định thay vì đoán một kiểu cụ thể.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-resolution-004">Thay thế kiểu lồng trong ParameterizedType</a>

<details>
<summary>Xem chi tiết</summary>

# Thay thế kiểu lồng trong ParameterizedType bằng Java Reflection

Khi Reflection đọc một khai báo generic, kết quả thường vẫn chứa các biến kiểu như `T`, `U`, `V`, dù ta đang phân tích lớp con đã gắn chúng với các kiểu cụ thể. Ví dụ, field `Map<T, List<U[]>>` của một lớp lồng có thể cần được hiểu dưới ngữ cảnh thành `Map<String, List<Integer[]>>`. Đây là bài toán **generic type substitution**: lần theo quan hệ thừa kế và lớp sở hữu, xây ánh xạ từ `TypeVariable` tới `Type`, rồi thay thế **đệ quy** trong cây `Type`.

Điểm khó nhất là không chỉ thay `T` xuất hiện ở phần tử đầu tiên của `ParameterizedType`. Phải đi sâu vào từng `getActualTypeArguments()`, xử lý `GenericArrayType`, `WildcardType`, các ánh xạ nhiều bước và `getOwnerType()` của kiểu lồng như `Outer<String>.Inner<Integer>`. Bài học dưới đây phân biệt **đọc metadata**, **xây ngữ cảnh generic** và **tạo cấu trúc `Type` đã được thay thế**.

## 1. Các thành phần tạo nên cây `Type`

`java.lang.reflect.Type` là giao diện gốc cho những cách biểu diễn kiểu generic trong Reflection:

| Dạng `Type` | Ví dụ | API dùng để đi sâu |
| --- | --- | --- |
| `Class<?>` | `String.class`, `int.class`, `String[].class` | `getComponentType()` nếu là mảng |
| `ParameterizedType` | `List<T>`, `Map<String, List<U[]>>`, `Outer<T>.Inner<U>` | `getRawType()`, `getActualTypeArguments()`, `getOwnerType()` |
| `TypeVariable<?>` | `T`, `U`, `V` | `getGenericDeclaration()`, `getName()`, `getBounds()` |
| `WildcardType` | `? extends U`, `? super T` | `getUpperBounds()`, `getLowerBounds()` |
| `GenericArrayType` | `U[]`, `List<T>[]` ở khai báo kiểu tổng quát | `getGenericComponentType()` |

Với một field khai báo `Map<T, List<U[]>>`, `field.getType()` chỉ là `Map.class` do *type erasure*. Nhưng `field.getGenericType()` trả về cây `ParameterizedType` chứa `T` và một `ParameterizedType` con là `List<U[]>`. Phần `U[]` được mô tả bởi `GenericArrayType`, vì thành phần `U` chưa phải kiểu reifiable.

Dễ hình dung cấu trúc trước thay thế:

~~~text
ParameterizedType: Map<T, List<U[]>>
├── rawType: Map.class
├── arg[0]: TypeVariable T
└── arg[1]: ParameterizedType List<U[]>
    └── arg[0]: GenericArrayType
        └── component: TypeVariable U
~~~

Ngữ cảnh `T → String`, `U → Integer` sẽ biến nó thành:

~~~text
ParameterizedType: Map<String, List<Integer[]>>
├── rawType: Map.class
├── arg[0]: Class String
└── arg[1]: ParameterizedType List<Integer[]>
    └── arg[0]: Class Integer[] 
~~~

`Integer[]` lúc này là một `Class` đại diện mảng thật vì `Integer` là kiểu cụ thể. **Không bỏ qua các tầng lồng**: thay mỗi `arg[0]` ở cấp Map nhưng không đi vào `List<U[]>` thì vẫn còn biến `U` chưa được giải.

## 2. Vì sao không thể thay biến kiểu chỉ bằng tên?

Giả sử hai khai báo khác nhau đều tên `T`:

~~~java
class First<T> {}
class Second<T> {}
~~~

Hai biến kiểu ở đây **không cùng định danh**. Mỗi `TypeVariable` liên quan đến chính `GenericDeclaration` đã khai báo nó. Một method generic có `<T> void doWork(T value)` còn có thể có một biến `T` khác với biến `T` của lớp.

Do đó, nên dùng:

~~~java
Map<TypeVariable<?>, Type> bindings = new HashMap<>();
~~~

thay vì `Map<String, Type>` theo tên như `"T"`. Dùng `getTypeParameters()` từ đúng raw class để lấy khóa; dùng `getActualTypeArguments()` từ đúng `ParameterizedType` để lấy giá trị tương ứng.

**Không dùng `getSimpleName()`, `getTypeName()` hay thay chuỗi bằng regex** làm cơ chế phân giải kiểu. Các API ấy phục vụ biểu diễn tên; chúng không giữ đầy đủ identity của `TypeVariable`, raw type, owner type, mảng và wildcard.

## 3. Kiểu lồng có hai tầng tham số: owner và chính inner

Ví dụ:

~~~java
class Outer<T> {
    class Slot<U> {
        Map<T, List<U[]>> table;
    }
}
~~~

Ở một sử dụng cụ thể `Outer<String>.Slot<Integer>`:

- `T` thuộc về khai báo `Outer<T>`, nhận `String`.
- `U` thuộc về khai báo `Outer<T>.Slot<U>`, nhận `Integer`.
- `ParameterizedType` biểu diễn **Slot** có `getRawType() == Outer.Slot.class`.
- `getActualTypeArguments()` của **Slot** chỉ chứa `Integer` (biến `U`).
- **Tham số `String` cho `T` nằm trong `getOwnerType()`** — nó không bị gộp thành hai phần tử trong mảng đối số của Slot.

Đây là lỗi cơ bản khi xử lý kiểu lồng: chỉ duyệt `getActualTypeArguments()` mà bỏ `getOwnerType()` sẽ không tìm được ánh xạ `Outer.T → String`.

**Lưu ý:** các nested type `static` không giữ một instance của outer class theo cách của non-static inner class. Owner metadata của một `ParameterizedType` có thể vẫn biểu diễn enclosing type, nhưng không nên suy luận từ đó rằng static nested class sử dụng generic parameter của outer; static nested type không được tham chiếu biến kiểu của outer instance như một inner class non-static.

## 4. Phân giải qua nhiều cấp kế thừa

Xét chuỗi khai báo:

~~~java
class Outer<T> {
    class Slot<U> {}
}

class Middle<V> extends Outer<V>.Slot<Integer> {
    Middle(Outer<V> owner) {
        owner.super();
    }
}

class End extends Middle<String> {
    End() {
        super(new Outer<String>());
    }
}
~~~

**Ví dụ mã minh họa**, chỉ ra quan hệ kiểu. Lưu ý `owner.super()` là cú pháp gọi constructor của inner class không `static` từ subclass.

Các metadata quan trọng:

| Bước đọc | `Type` khai báo | Ánh xạ thu thập |
| --- | --- | --- |
| `End.class.getGenericSuperclass()` | `Middle<String>` | `Middle.V → String` |
| `Middle.class.getGenericSuperclass()` | `Outer<V>.Slot<Integer>` | `Outer.T → Middle.V`, `Slot.U → Integer` |
| Thay thế bắc cầu | `Outer.T → V → String` | `Outer.T → String`, `Slot.U → Integer` |

Không được dừng ở ánh xạ `T → V`; phải tiếp tục tra `V` trong ngữ cảnh. Vì `getGenericSuperclass()` trên `Middle.class` mô tả **khai báo của Middle**, nó vẫn chứa `V`, không tự biến thành `String` chỉ vì caller xuất phát từ `End.class`.

Quy trình chung cho class inheritance chain: bắt đầu ở kiểu đích cụ thể; duyệt từng `getGenericSuperclass()` từ dưới lên; với mỗi `ParameterizedType`, thu ánh xạ biến kiểu cho **raw type** và cả **owner** nếu có; sau đó thế biến đệ quy khi đọc field/method generic ở lớp khai báo ban đầu.

Nếu đường kế thừa liên quan **generic interface**, phải đọc thêm `getGenericInterfaces()` ở các nút liên quan. Các lớp dữ liệu hoặc thư viện có thể có nhiều đường interface; cần giữ quan hệ theo từng nhánh và phát hiện khi thông tin không được cung cấp đầy đủ.

## 5. Thuật toán thay thế đệ quy đúng cấu trúc

Cho `resolve(type, bindings)`:

1. Nếu `type` là `TypeVariable`, tra theo khóa `TypeVariable` và **tiếp tục resolve** kiểu ánh xạ. Nếu chưa có ánh xạ, giữ nguyên `TypeVariable`. Chặn chu trình ánh xạ để tránh đệ quy vô hạn.
2. Nếu là `ParameterizedType`, **resolve owner**, rồi resolve **mọi actual type argument** theo đúng thứ tự, giữ nguyên `rawType`; dựng một `ParameterizedType` mới bảo toàn cấu trúc.
3. Nếu là `GenericArrayType`, resolve component. Khi component là `Class<?>` cụ thể, có thể biểu diễn bằng array `Class` (chẳng hạn `Integer[].class`); nếu vẫn còn generic component, giữ dạng `GenericArrayType`.
4. Nếu là `WildcardType`, resolve cả `getUpperBounds()` và `getLowerBounds()` mà không xóa dấu `? extends` hoặc `? super`.
5. Với `Class<?>` bình thường, giữ nguyên nó. Một `Class<?>[]` hay `String[].class` đã là kiểu runtime có thật, không chứa `TypeVariable` để thay.

Để **trả về một `Type` thực sự**, không thể thay đổi đối tượng `ParameterizedType` mà Reflection cung cấp tại chỗ; cần dựng đối tượng khác triển khai `ParameterizedType`, `GenericArrayType`, `WildcardType` khi cần. Các implementation nên bất biến, có `equals`/`hashCode` nhất quán với hợp đồng của giao diện và trả mảng bản sao. Bên dưới là một implementation nhỏ dành cho học tập, dựa trên record của Java 17.

## 6. Chương trình Java 17+ đầy đủ: thay cả owner, nested arguments, array và wildcard

Tạo tệp **`NestedTypeSubstitutionDemo.java`**:

~~~java
import java.lang.reflect.*;
import java.util.*;

class Outer<T> {
    class Slot<U> {
        Map<T, List<U[]>> table;
        List<? extends U> upper;
    }
}

class Middle<V> extends Outer<V>.Slot<Integer> {
    Middle(Outer<V> outer) {
        outer.super();
    }
}

class End extends Middle<String> {
    End() {
        super(new Outer<String>());
    }
}

public class NestedTypeSubstitutionDemo {
    record P(Type owner, Type raw, List<Type> args)
            implements ParameterizedType {
        P { args = List.copyOf(args); }
        public Type getOwnerType() { return owner; }
        public Type getRawType() { return raw; }
        public Type[] getActualTypeArguments() {
            return args.toArray(Type[]::new);
        }
        public String getTypeName() { return describe(this); }
        @Override public String toString() { return getTypeName(); }
        @Override public boolean equals(Object other) {
            return other instanceof ParameterizedType p
                    && Objects.equals(owner, p.getOwnerType())
                    && Objects.equals(raw, p.getRawType())
                    && Arrays.equals(getActualTypeArguments(),
                                     p.getActualTypeArguments());
        }
        @Override public int hashCode() {
            return Arrays.hashCode(getActualTypeArguments())
                    ^ Objects.hashCode(owner) ^ Objects.hashCode(raw);
        }
    }

    record A(Type component) implements GenericArrayType {
        public Type getGenericComponentType() { return component; }
        public String getTypeName() { return describe(this); }
        @Override public String toString() { return getTypeName(); }
        @Override public boolean equals(Object other) {
            return other instanceof GenericArrayType a
                    && component.equals(a.getGenericComponentType());
        }
        @Override public int hashCode() { return component.hashCode(); }
    }

    record W(List<Type> upper, List<Type> lower)
            implements WildcardType {
        W {
            upper = List.copyOf(upper);
            lower = List.copyOf(lower);
        }
        public Type[] getUpperBounds() {
            return upper.toArray(Type[]::new);
        }
        public Type[] getLowerBounds() {
            return lower.toArray(Type[]::new);
        }
        public String getTypeName() { return describe(this); }
        @Override public String toString() { return getTypeName(); }
        @Override public boolean equals(Object other) {
            return other instanceof WildcardType w
                    && Arrays.equals(getUpperBounds(), w.getUpperBounds())
                    && Arrays.equals(getLowerBounds(), w.getLowerBounds());
        }
        @Override public int hashCode() {
            return Arrays.hashCode(getUpperBounds())
                    ^ Arrays.hashCode(getLowerBounds());
        }
    }

    static void bind(Type type, Map<TypeVariable<?>, Type> env) {
        if (!(type instanceof ParameterizedType p)) {
            return;
        }
        bind(p.getOwnerType(), env);
        TypeVariable<?>[] keys =
                ((Class<?>) p.getRawType()).getTypeParameters();
        Type[] values = p.getActualTypeArguments();
        for (int i = 0; i < keys.length; i++) {
            env.put(keys[i], values[i]);
        }
    }

    static Type substitute(Type type, Map<TypeVariable<?>, Type> env) {
        return substitute(type, env, new HashSet<>());
    }

    private static Type substitute(Type type,
            Map<TypeVariable<?>, Type> env,
            Set<TypeVariable<?>> visiting) {
        if (type instanceof TypeVariable<?> v) {
            Type replacement = env.get(v);
            if (replacement == null || replacement.equals(v)
                    || !visiting.add(v)) {
                return v;
            }
            try {
                return substitute(replacement, env, visiting);
            } finally {
                visiting.remove(v);
            }
        }
        if (type instanceof ParameterizedType p) {
            Type owner = p.getOwnerType() == null ? null
                    : substitute(p.getOwnerType(), env, visiting);
            List<Type> args = new ArrayList<>();
            for (Type arg : p.getActualTypeArguments()) {
                args.add(substitute(arg, env, visiting));
            }
            return new P(owner, p.getRawType(), args);
        }
        if (type instanceof GenericArrayType a) {
            Type child = substitute(
                    a.getGenericComponentType(), env, visiting);
            if (child instanceof Class<?> c) {
                return java.lang.reflect.Array.newInstance(c, 0)
                        .getClass();
            }
            return new A(child);
        }
        if (type instanceof WildcardType w) {
            List<Type> upper = new ArrayList<>();
            List<Type> lower = new ArrayList<>();
            for (Type t : w.getUpperBounds()) {
                upper.add(substitute(t, env, visiting));
            }
            for (Type t : w.getLowerBounds()) {
                lower.add(substitute(t, env, visiting));
            }
            return new W(upper, lower);
        }
        return type;
    }

    static String describe(Type t) {
        if (t instanceof ParameterizedType p) {
            Class<?> raw = (Class<?>) p.getRawType();
            String base = p.getOwnerType() == null
                    ? raw.getSimpleName()
                    : describe(p.getOwnerType()) + "."
                            + raw.getSimpleName();
            Type[] args = p.getActualTypeArguments();
            if (args.length == 0) return base;
            List<String> parts = new ArrayList<>();
            for (Type arg : args) parts.add(describe(arg));
            return base + "<" + String.join(", ", parts) + ">";
        }
        if (t instanceof GenericArrayType a) {
            return describe(a.getGenericComponentType()) + "[]";
        }
        if (t instanceof WildcardType w) {
            Type[] lower = w.getLowerBounds();
            if (lower.length > 0) {
                return "? super " + describe(lower[0]);
            }
            Type[] upper = w.getUpperBounds();
            if (upper.length == 0 || (upper.length == 1
                    && upper[0] == Object.class)) return "?";
            return "? extends " + describe(upper[0]);
        }
        if (t instanceof TypeVariable<?> v) return v.getName();
        if (t instanceof Class<?> c) {
            return c.isArray()
                    ? describe(c.getComponentType()) + "[]"
                    : c.getSimpleName();
        }
        return t.getTypeName();
    }

    public static void main(String[] args) throws NoSuchFieldException {
        Map<TypeVariable<?>, Type> env = new LinkedHashMap<>();

        Type endParent = End.class.getGenericSuperclass();
        bind(endParent, env); // V -> String
        Type middleParent = Middle.class.getGenericSuperclass();
        bind(middleParent, env); // T -> V, U -> Integer

        System.out.println("T -> " + describe(substitute(
                Outer.class.getTypeParameters()[0], env)));
        System.out.println("U -> " + describe(substitute(
                Outer.Slot.class.getTypeParameters()[0], env)));
        System.out.println("V -> " + describe(substitute(
                Middle.class.getTypeParameters()[0], env)));

        ParameterizedType nested = (ParameterizedType) middleParent;
        System.out.println("Owner: " + describe(
                substitute(nested.getOwnerType(), env)));
        System.out.println("Nested: " + describe(
                substitute(middleParent, env)));

        Field table = Outer.Slot.class.getDeclaredField("table");
        Type declared = table.getGenericType();
        Type resolved = substitute(declared, env);
        System.out.println("Raw field: " + table.getType().getSimpleName());
        System.out.println("Declared: " + declared.getTypeName());
        System.out.println("Resolved: " + describe(resolved));
        System.out.println("Nested argument: "
                + (((ParameterizedType) resolved)
                    .getActualTypeArguments()[1]
                    instanceof ParameterizedType));
        Type upper = Outer.Slot.class.getDeclaredField("upper")
                .getGenericType();
        System.out.println("Wildcard: "
                + describe(substitute(upper, env)));
    }
}
~~~

Biên dịch và chạy ở thư mục chứa tệp:

~~~bash
javac --release 17 NestedTypeSubstitutionDemo.java
java NestedTypeSubstitutionDemo
~~~

**Stdout:**

~~~text
T -> String
U -> Integer
V -> String
Owner: Outer<String>
Nested: Outer<String>.Slot<Integer>
Raw field: Map
Declared: java.util.Map<T, java.util.List<U[]>>
Resolved: Map<String, List<Integer[]>>
Nested argument: true
Wildcard: List<? extends Integer>
~~~

`describe(Type)` của ví dụ **cố ý dùng `Class#getSimpleName()`** để hiển thị gọn; vì vậy chuỗi `Resolved: Map<String, List<Integer[]>>` không in `java.util.` hoặc `java.lang.`. Trong hệ thống cần định danh duy nhất hoặc ghi cache bền vững, dùng cấu trúc `Type` và `Class` thay vì dựa vào chuỗi rút gọn này.

### Điều cần nhận ra trong kết quả

- **`T → String`**: đi qua `End → Middle<String>` rồi `Outer<V>`, nên có ánh xạ bắc cầu.
- **`U → Integer`**: nhận trực tiếp từ type argument của `Slot<Integer>`.
- **`Owner: Outer<String>`**: chứng minh thuật toán không quên `getOwnerType()`.
- **`Nested: Outer<String>.Slot<Integer>`**: vừa thay owner, vừa thay argument của inner type.
- **`Raw field: Map`**: `Field#getType()` đã mất các argument do type erasure.
- **`Declared: java.util.Map<T, java.util.List<U[]>>`**: `Field#getGenericType()` phản ánh khai báo, chưa tự biết `End`.
- **`Resolved: Map<String, List<Integer[]>>`**: thuật toán vào sâu qua `Map`, `List`, `GenericArrayType`, rồi thay `U` bằng `Integer`.
- **`Nested argument: true`**: kết quả vẫn có argument `ParameterizedType` thực sự ở cấp `List<...>`; không bị rút thành một chuỗi.
- **`Wildcard: List<? extends Integer>`**: wildcard vẫn giữ toán tử bound; biến kiểu nằm trong upper bound được thay.

### Vì sao có ba record `P`, `A`, `W`?

Chúng là những implementation `Type` **dành cho bài tập**, không thuộc các lớp public JDK cung cấp để dựng `ParameterizedType` bất kỳ:

- `P` lưu owner, raw type và arguments, triển khai `ParameterizedType`.
- `A` giữ generic array component khi component chưa phải `Class<?>.`
- `W` giữ upper/lower bounds của wildcard.
- `List.copyOf` và `toArray(Type[]::new)` tránh cho caller chỉnh mảng/lists nội bộ.
- `equals`/`hashCode` so sánh các thành phần theo hợp đồng phản chiếu; phần hiển thị `getTypeName()` sử dụng `describe` riêng.

Các implementation ở ví dụ phù hợp với mục tiêu học tập và kiểu generic mà Java source có thể tạo ra. Framework production cần thêm kiểm tra invariant (raw type thực sự phù hợp, số lượng argument hợp lệ, owner hợp lệ, bounds không null/sai cấu trúc), chính sách cache, mô hình phân giải đa nhánh và kiểm thử với các dạng kiểu phức tạp khác.

## 7. Dùng `ParameterizedType` đúng cách sau khi thay thế

Một `ParameterizedType` đã được giải có thể kiểm tra bằng API thay vì tách chuỗi:

~~~java
ParameterizedType mapType = (ParameterizedType) resolved;
Type key = mapType.getActualTypeArguments()[0];
Type list = mapType.getActualTypeArguments()[1];

System.out.println(key == String.class); // true
System.out.println(list instanceof ParameterizedType); // true
~~~

Đây là **đoạn mã minh họa** đặt sau phần tạo biến `resolved` trong chương trình chính, với import `java.lang.reflect.*`. Chúng là các đối tượng `Type`, nên có thể tiếp tục đọc raw type, owner, arguments; không cần parse `getTypeName()`.

Kiểm tra generic array sau thay thế:

~~~java
ParameterizedType listType = (ParameterizedType) list;
Type arrayArg = listType.getActualTypeArguments()[0];
System.out.println(arrayArg == Integer[].class); // true
~~~

Sau khi `U` được giải thành `Integer.class`, mảng `U[]` được biểu diễn bằng `Integer[].class`. Nếu component vẫn là một tham số kiểu chưa giải, `GenericArrayType` tiếp tục là biểu diễn phù hợp.

**Không hiểu `substitute` là thay đổi khai báo Java hay bytecode.** `Field#getGenericType()` trên trường `Outer.Slot.table` vẫn trả metadata `Map<T, List<U[]>>`; đối tượng `Type` mới chỉ là **kết quả diễn giải dưới ngữ cảnh** mà chương trình xây dựng. Bản thân `Outer<T>`, `Slot<U>` và các đối tượng runtime không bị sửa.

## 8. Những kiểu lồng khác cần xét

### 8.1. Nested `ParameterizedType` trong collection

Ví dụ `Map<String, List<Map<T, U>>>`. Nếu chỉ duyệt một tầng, `T` và `U` nằm tận bên trong `Map<T,U>` sẽ vẫn chưa được thay. Quy tắc đúng là **duyệt mọi phần tử của `getActualTypeArguments()` rồi gọi cùng thuật toán đệ quy**.

### 8.2. Generic wildcard ở nhiều mức

`List<? extends U>` sử dụng `WildcardType` ở argument của `List`. `Map<? super T, ? extends List<U>>` có thể chứa cả lower bound và upper bound. Cần đi vào `getLowerBounds()` và `getUpperBounds()`, không chuyển wildcard thành kiểu bound trực tiếp; `? extends Integer` **không phải** cùng một khai báo với `Integer`.

### 8.3. Generic array trong argument

`U[]` khác `List<U>`. Khi đang generic, nó có thể là `GenericArrayType`; khi `U` thành `Integer`, kết quả có thể là `Class` của mảng `Integer[].class`. Nếu `U` lại thay bằng một `ParameterizedType` như `List<String>`, một mảng `List<String>[]` cần được giữ dạng `GenericArrayType`, vì không có `Class` reifiable cho `List<String>[]`.

### 8.4. Static nested type và owner

Một static nested class không sử dụng generic parameter của enclosing instance theo cách của non-static inner class. Cần đọc metadata thực tế từ `getOwnerType()` và `getActualTypeArguments()`, tránh "điền" type arguments của enclosing class theo phỏng đoán. `ownerType` không đồng nghĩa với instance reference hay một argument generic mới của raw inner class.

### 8.5. `TypeVariable` khai báo ở method

Một generic method như `<X> X convert(X input)` có `X` khai báo trên chính `Method`, không phải parameter của class. Ánh xạ xây chỉ từ superclass **không tự giải được `X`**. Cần ngữ cảnh riêng (ví dụ chữ ký method và type inference của framework) nếu thật sự có đủ thông tin. Nếu không, giữ `TypeVariable` chưa giải là kết quả đúng hơn tự đoán `Object`.

## 9. Giới hạn quan trọng: type erasure và ngữ cảnh sẵn có

Reflection không thể suy ra mọi tham số generic từ một **instance runtime**.

Ví dụ hai object được tạo từ:

~~~java
// Minh họa; không phải đoạn chương trình độc lập.
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
~~~

Cả `a.getClass()` và `b.getClass()` đều có thể là `ArrayList.class`; type arguments `String` và `Integer` không lưu trên instance theo cách Reflection có thể tự khôi phục. Nếu có generic field ở một subclass cụ thể hoặc interface/superclass được parameterize trong khai báo, Reflection mới có metadata cần thiết.

Chính vì vậy ví dụ dùng **`class End extends Middle<String>`**: khai báo superclass của `End` giữ `String`, khai báo của `Middle` giữ `Outer<V>.Slot<Integer>`. Hai liên kết này cung cấp đủ ngữ cảnh để giải `T` và `U`.

Nếu một đường kiểu bị sử dụng dưới dạng **raw type**, một số tham số có thể không còn ánh xạ cụ thể. Đừng coi `null`/thiếu mapping là bằng chứng biến kiểu phải được thay bằng `Object` theo mọi trường hợp. Giữ unresolved `TypeVariable` hoặc biểu diễn bounds rõ ràng, tùy mục đích API.

## 10. Tránh năm lỗi thiết kế thường gặp

| Sai lầm | Tác hại | Cách sửa |
| --- | --- | --- |
| `Map<String, Type>` với khóa `"T"` | Gộp các `T` của lớp/method không liên quan | Dùng `Map<TypeVariable<?>, Type>` |
| Chỉ duyệt arguments tầng ngoài của `ParameterizedType` | Bỏ lại `List<U[]>` hoặc `Map<T, U>` chưa giải | Resolve đệ quy mọi argument |
| Bỏ `getOwnerType()` | Mất ánh xạ `Outer.T` trong `Outer<T>.Inner<U>` | Bind/resolve owner trước hoặc đồng thời với inner |
| Thế `T → V` một lần rồi dừng | Không tới `V → String` ở supertype gần hơn | Resolve bắc cầu kèm phát hiện chu trình |
| Ép mọi `Type` về `Class` | Mất wildcard, parameterization và generic array | Phân nhánh theo các giao diện `Type` tương ứng |

Ngoài ra, khi tạo `ParameterizedType` tùy chỉnh, **đừng chỉ override `toString()`** rồi coi nó là type resolver. Phải lưu và trả về đúng raw type, owner, arguments, bảo toàn cấu trúc và thực hiện `equals`/`hashCode` phù hợp. Chuỗi hiển thị không thay cho thông tin kiểu thực.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Với `Outer<String>.Inner<Integer>`, tại sao `inner.getActualTypeArguments()` không bắt buộc chứa cả `String` và `Integer`?

**Đáp án:** `String` nằm trong metadata của **owner** `Outer<String>`, còn `getActualTypeArguments()` của `Inner<Integer>` chứa argument `Integer` tương ứng biến kiểu do chính `Inner` khai báo.

**Câu 2.** Từ `Map<T, List<U[]>>`, nếu `T → String`, `U → Integer`, kết quả sau khi thay đầy đủ là gì?

**Đáp án:** `Map<String, List<Integer[]>>`. Phải duyệt nested `List` và `GenericArrayType` của `U[]`.

**Câu 3.** Vì sao không dùng `Map<String, Type>` để lưu ánh xạ cho các biến tên `T`?

**Đáp án:** Những biến cùng tên có thể được khai báo bởi các `GenericDeclaration` khác nhau. Khóa đúng là đối tượng `TypeVariable`, không phải riêng chuỗi tên.

**Câu 4.** Cho `End extends Middle<String>`, `Middle<V> extends Outer<V>.Slot<Integer>`. Phải đi qua mấy lớp ánh xạ để giải `Outer.T`?

**Đáp án:** Hai liên kết: `Outer.T → Middle.V` và `Middle.V → String`; kết quả `Outer.T → String`.

**Câu 5.** Nếu một wildcard là `? extends U` và `U → Integer`, ta có nên bỏ dấu `?` không?

**Đáp án:** Không. Kết quả là `? extends Integer`, vẫn là `WildcardType`, không phải `Integer` đơn thuần.

**Câu 6.** `field.getGenericType()` có tự đổi thành kiểu đã thay thế sau khi gọi utility resolver không?

**Đáp án:** Không. Resolver chỉ tạo một `Type` mô tả cách hiểu metadata dưới ngữ cảnh riêng; nó không sửa khai báo field hoặc bytecode lớp.

**Câu 7.** Có thể tìm type argument `String` chỉ từ `new ArrayList<String>().getClass()` không?

**Đáp án:** Không. `getClass()` trả `ArrayList.class`, còn tham số của biểu thức tạo instance thường bị xóa ở runtime; cần thông tin từ khai báo generic có lưu signature.

## 12. Tổng kết quy trình

Khi phải giải một kiểu lồng trong `ParameterizedType`: bắt đầu bằng một kiểu cụ thể còn lưu chữ ký generic, **theo dõi chuỗi superclass/interface và owner**, ánh xạ các `TypeVariable` đúng identity, rồi **resolve đệ quy** trên `ParameterizedType`, `GenericArrayType` và `WildcardType`. Sau đó kiểm tra kết quả bằng API `Type` thay vì chỉ bằng chuỗi.

Trong ví dụ, chính việc ghép `Middle.V → String`, `Outer.T → V` và `Slot.U → Integer` cho phép biến field khai báo `Map<T, List<U[]>>` thành cây `Type` biểu diễn `Map<String, List<Integer[]>>` mà không mất owner hay các tầng lồng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-resolution-005">Xử lý wildcard, generic array và owner type</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý wildcard, generic array và owner type khi phân giải generic bằng Java Reflection

## 1. Bài toán: `Type` không phải lúc nào cũng là `Class<?>`

Khi lập trình với generics trong Java, một khai báo có thể chứa kiểu tham số hóa, wildcard, mảng generic và kiểu bao ngoài của nested class:

```java
List<? extends Number> numbers;
List<? super Integer> targets;
List<String>[] pages;
Outer<String>.Inner<Integer> entry;
```

Đây là **các khai báo minh họa**, cần đặt trong class thích hợp với import và định nghĩa `Outer` tương ứng.

Nếu công cụ Reflection chỉ gọi `Field.getType()` rồi ép kết quả thành `Class<?>`, phần lớn cấu trúc generic đã bị **type erasure**. Để đọc và phân giải đúng, cần bắt đầu từ `Field.getGenericType()`, `Method.getGenericReturnType()`, `Method.getGenericParameterTypes()`, `Constructor.getGenericParameterTypes()` hoặc `Class.getGenericSuperclass()` / `getGenericInterfaces()`.

Những API đó trả các đối tượng thuộc họ **`java.lang.reflect.Type`**. Ba nhánh đặc biệt quan trọng trong bài này:

- **`WildcardType`:** tham số kiểu `?`, `? extends X` hoặc `? super X` với các giới hạn trên/dưới.
- **`GenericArrayType`:** kiểu mảng có component type là kiểu generic không reifiable, chẳng hạn `T[]` hoặc `List<String>[]`.
- **`ParameterizedType.getOwnerType()`:** kiểu sở hữu của một nested/member type, ví dụ phần `Outer<String>` trong `Outer<String>.Inner<Integer>`.

Để **phân giải generic** chứ không chỉ liệt kê, bạn còn phải xử lý `TypeVariable<?>` và ánh xạ biến kiểu theo class/method và quan hệ kế thừa. Nếu bỏ qua một nhánh, thuật toán có thể mất bounds, thông tin component hay ánh xạ generic của outer.

## 2. Cây `Type` cần nhận diện

`java.lang.reflect.Type` là một interface. Một công cụ phân tích generic phải biết các triển khai cơ bản sau:

| Loại `Type` | Ví dụ khai báo | Ý nghĩa / cách xử lý |
| --- | --- | --- |
| `Class<?>` | `String`, `int`, `String[]` | Kiểu runtime; với mảng thông thường dùng `Class.isArray()` và `getComponentType()` |
| `ParameterizedType` | `List<String>`, `Map<String,Integer>`, `Outer<String>.Inner<Integer>` | Xử lý `getRawType()`, `getActualTypeArguments()` và `getOwnerType()` |
| `TypeVariable<?>` | `T` trong `class Box<T>` | Tra ánh xạ theo biến kiểu **của đúng declaration**, không chỉ tên chuỗi |
| `WildcardType` | `? extends Number`, `? super Integer` | Đọc `getUpperBounds()` và `getLowerBounds()`, có thể cần phân giải đệ quy |
| `GenericArrayType` | `T[]`, `List<? extends Number>[]` | Đọc `getGenericComponentType()` và phân giải component đệ quy |

Nhận diện bằng `instanceof` (Java 17+):

```java
if (type instanceof ParameterizedType parameterized) {
    // Có raw type, actual arguments và có thể có owner type.
} else if (type instanceof WildcardType wildcard) {
    // Đọc upper/lower bounds.
} else if (type instanceof GenericArrayType array) {
    // Đọc generic component type.
} else if (type instanceof TypeVariable<?> variable) {
    // Tra binding / bounds của type variable.
} else if (type instanceof Class<?> clazz) {
    // Kiểu thường, primitive hoặc class array.
}
```

Đây là **mảnh thuật toán**, không phải chương trình độc lập; cần import các interface từ `java.lang.reflect` và khai báo `type`.

**Nguyên tắc không được bỏ qua:** Cấu trúc có thể lồng nhau ở nhiều tầng. Ví dụ `List<? extends T[]>[]` có outer `GenericArrayType`, component `ParameterizedType`, argument `WildcardType` và upper bound là một `GenericArrayType` khác chứa `TypeVariable<T>`. Thuật toán xử lý một cấp rồi ép tất cả các con về `Class<?>` sẽ làm sai kết quả.

## 3. `WildcardType`: phân biệt bounds trên và dưới

Wildcard xuất hiện trong *type argument*, không phải mọi vị trí có biến kiểu:

```java
List<?> any;
List<? extends Number> producers;
List<? super Integer> consumers;
```

Khi đọc `List<? extends Number>` qua Reflection:

1. Type của field là `ParameterizedType` có raw type `List.class`.
2. `getActualTypeArguments()[0]` là một `WildcardType`.
3. `wildcard.getUpperBounds()` có `Number.class`.
4. `wildcard.getLowerBounds()` là mảng rỗng.

Tương tự, với `List<? super Integer>`, lower bound là `Integer.class`, còn upper bound thường là `Object.class`.

### 3.1. Hai hàm bounds của `WildcardType`

| Cú pháp | `getUpperBounds()` | `getLowerBounds()` |
| --- | --- | --- |
| `?` | `[Object.class]` | `[]` |
| `? extends Number` | `[Number.class]` | `[]` |
| `? super Integer` | `[Object.class]` | `[Integer.class]` |
| `? extends T` | `[TypeVariable<T>]` | `[]` |

Các ô trên mô tả cấu trúc điển hình của wildcard do Java compiler tạo, **không** phải chuỗi stdout của `Arrays.toString`. API trả `Type[]`, nên bounds có thể là `Class<?>`, `TypeVariable<?>`, `ParameterizedType` hoặc các kiểu `Type` khác. Khi viết code tổng quát, đừng giả định `getUpperBounds()[0]` luôn là `Class<?>`.

- **Wildcard không giới hạn `?`:** upper bound mang mặc định `Object`; không có lower bound.
- **`? extends U`:** ràng buộc trên bằng `U`. Nếu `U` là `T`, sau khi suy ra `T → String`, biểu diễn đã giải quyết tương ứng là `? extends String`.
- **`? super L`:** ràng buộc dưới bằng `L`; upper bound thông thường là `Object`.
- **Cần giữ đúng hướng ràng buộc:** không được biến `? super Integer` thành `? extends Integer` khi thay kiểu.

### 3.2. Liên hệ sử dụng: PECS

Cụm *Producer Extends, Consumer Super* giúp ghi nhớ nhiều tình huống:

```java
List<? extends Number> src;  // Đọc phần tử như Number; hạn chế thêm giá trị khác null
List<? super Integer> dst;   // Có thể thêm Integer; đọc thường chỉ bảo đảm Object
```

Đây là mô tả về ràng buộc sử dụng trong Java source; trong Reflection, ta đọc **cấu trúc bounds** để phục vụ validation, serialization hoặc sinh metadata. Không dùng `WildcardType` để tự suy luận rằng `List` ở runtime thực sự chứa đúng kiểu phần tử: generic type information của object có thể đã bị erase.

### 3.3. Wildcard không đồng nghĩa `TypeVariable`

`? extends Number` là **wildcard**; `T extends Number` trong `class Holder<T extends Number>` là một **type variable được khai báo có tên**.

Đừng lấy `TypeVariable.getBounds()` rồi nghĩ nó giống hoàn toàn `WildcardType.getUpperBounds()`. `TypeVariable` có declaration identity và có thể có intersection bounds; wildcard là type argument mô tả khoảng biến thiên kiểu.

## 4. `GenericArrayType`: component generic nằm trong mảng

Một khai báo như `T[]` hoặc `List<String>[]` không luôn phản ánh bằng `Class<?>` thông thường.

```java
class Store<T> {
    T[] items;
    List<String>[] lists;
    String[] strings;
}
```

Với `Store.class.getDeclaredField(...).getGenericType()`:

| Field | Dạng `Type` thường nhận được | Component type |
| --- | --- | --- |
| `items` (`T[]`) | `GenericArrayType` | `TypeVariable<T>` |
| `lists` (`List<String>[]`) | `GenericArrayType` | `ParameterizedType List<String>` |
| `strings` (`String[]`) | `Class<?>` có `isArray() == true` | `String.class` |

**Không phải tất cả mảng đều là `GenericArrayType`.** Trong thuật toán phân giải, cần xử lý cả nhánh `Class.isArray()` để đi sâu vào mảng thông thường, đặc biệt khi định dạng hoặc chuyển đổi nhiều tầng mảng.

Chẳng hạn:

```java
Type fieldType = Store.class.getDeclaredField("lists")
        .getGenericType();
GenericArrayType array = (GenericArrayType) fieldType;
Type component = array.getGenericComponentType();
// component là ParameterizedType biểu diễn List<String>
```

Đoạn minh họa yêu cầu import `Type` và `GenericArrayType`, cũng như xử lý `NoSuchFieldException`. Không dùng `(Class<?>) component` nếu chưa kiểm tra subtype.

### 4.1. Vì sao có generic array nhưng không được `new List<String>[10]`?

Java thường **cấm tạo trực tiếp** array với component parameterized không reifiable:

```java
// Không biên dịch:
// List<String>[] data = new List<String>[10];
```

Dù vậy, **khai báo field hoặc tham số** có kiểu `List<String>[]` là hợp lệ. Reflection có thể giữ cấu trúc generic của khai báo đó trong `GenericArrayType`. Sự xuất hiện của generic array metadata không chứng minh bạn được phép tạo mảng ấy bằng cú pháp `new List<String>[...]`.

Một mảng runtime thực tế vẫn có class theo erased component, ví dụ `List[].class`. Phân giải metadata `List<String>[]` không biến runtime array store checks thành hệ kiểm tra type argument `String`; giới hạn type erasure vẫn còn.

### 4.2. Khi thay `T`, nên xử lý thế nào?

Giả sử:

```java
class Base<T> {
    T[] values;
    List<? extends T>[] groups;
}

class StringBase extends Base<String> {}
```

Ánh xạ từ `StringBase` cho biến `T` của `Base` là `T → String`. Khi phân giải *cấu trúc generic*:

- `T[]` trở thành biểu diễn `String[]`.
- `List<? extends T>[]` trở thành `List<? extends String>[]`.
- Nếu cần trả một đối tượng `Type` thực, phải tái tạo/biểu diễn đúng các node sau thay thế; không thể chỉ thay chuỗi trong `getTypeName()`.

Nếu component đã thay thế thành một `Class<?>` cụ thể và bài toán cần **Class object của mảng**, có thể lấy một class tương ứng bằng `java.lang.reflect.Array.newInstance(componentClass, 0).getClass()`. Nếu component vẫn là `ParameterizedType` hoặc chứa wildcard, không có `Class<?>` nào biểu diễn trực tiếp toàn bộ thông tin parameterized; cần giữ một `GenericArrayType` hoặc đối tượng metadata tương đương.

## 5. `ParameterizedType.getOwnerType()`: chủ sở hữu generic của member type

Xét khai báo:

```java
class Outer<A> {
    class Inner<B> {
        A fromOuter;
        B fromInner;
    }
}
```

Một field ở nơi thích hợp có thể dùng kiểu:

```java
Outer<String>.Inner<Integer> entry;
```

Đây là **member inner class**. `ParameterizedType` của field `entry` có cấu trúc:

```text
ParameterizedType: Outer<String>.Inner<Integer>
├── getRawType()                 → Class: Outer.Inner
├── getActualTypeArguments()     → [Class: Integer]
└── getOwnerType()               → ParameterizedType: Outer<String>
    ├── getRawType()             → Class: Outer
    └── getActualTypeArguments() → [Class: String]
```

Điểm quan trọng: **`getActualTypeArguments()` của `Inner<Integer>` chỉ có `Integer`**, không tự lặp lại `String` từ outer. Muốn giải được field `fromOuter` có kiểu `A`, thuật toán phải đi lên **`getOwnerType()`** để lấy binding `A → String`.

Nếu bạn chỉ xây `Map<TypeVariable<?>, Type>` từ `Inner.class.getTypeParameters()`, bạn chỉ có `B → Integer` và sẽ làm mất ý nghĩa của `A`.

### 5.1. Owner type khác enclosing instance

Ba ý nghĩa không được đồng nhất:

- **`getOwnerType()`:** đối tượng `Type` mô tả kiểu chứa member type, có thể mang generic arguments như `Outer<String>`. Đây là **metadata về kiểu**.
- **Enclosing instance:** một **object** cụ thể kiểu `Outer<?>` cần để tạo non-static member inner object, ví dụ `outer.new Inner<Integer>()`. Đây là **giá trị runtime**, không phải `Type`.
- **`Class.getEnclosingClass()` / `getDeclaringClass()`:** trả metadata `Class<?>` về nơi khai báo/bối cảnh từ vựng, không nhất thiết bao gồm type arguments của cách dùng cụ thể.

Không truyền `ParameterizedType getOwnerType()` làm đối số constructor thay cho một instance `Outer` thật.

### 5.2. Owner type của static nested class

`static nested class` có thể có owner type metadata mặc dù nó **không gắn với enclosing instance**. Ví dụ:

```java
class Outer<A> {
    static class Nested<B> {}
}
```

Kiểu sử dụng hợp lệ là `Outer.Nested<Integer>`, **không** phải `Outer<String>.Nested<Integer>` vì static nested class không phụ thuộc parameterization của outer. Trong Reflection, owner có thể biểu diễn `Outer.class` (một `Class<?>`), thay vì `Outer<String>`; bạn vẫn không được suy từ `getOwnerType() != null` rằng phải truyền enclosing instance.

Với top-level parameterized type như `List<String>`, `getOwnerType()` trả `null`. Tuy nhiên, khi raw type là member class, owner có thể khác `null`, và `getActualTypeArguments()` thậm chí có thể rỗng nếu member **không khai báo type parameters** nhưng được đặt dưới một parameterized owner. Xây traversal phải xét owner độc lập với độ dài danh sách actual arguments.

## 6. Thuật toán phân giải generic có hệ thống

Một trình phân giải kiểu (*type resolver*) nên nhận hai thứ:

1. Một **`Type` cần phân giải**, chẳng hạn generic type của field/return/parameter.
2. Một môi trường binding **`Map<TypeVariable<?>, Type>`** đã suy ra từ ngữ cảnh sử dụng (subclass, interface implementation, parameterized owner, method context...).

Sau đó đi đệ quy:

```text
resolve(type, bindings)
    Class:
        nếu là mảng → resolve(component); có thể dựng Class-array nếu phù hợp
        nếu không → giữ nguyên
    TypeVariable:
        tìm binding bằng chính TypeVariable declaration; nếu không có → giữ nguyên
        nếu có → resolve(binding) (với bảo vệ vòng lặp)
    ParameterizedType:
        resolve(owner) nếu khác null
        resolve từng actual type argument
        giữ raw type; kết hợp thành biểu diễn ParameterizedType mới nếu có thay đổi
    GenericArrayType:
        resolve(generic component)
        kết hợp thành array type, giữ tham số hóa nếu component không reifiable
    WildcardType:
        resolve toàn bộ upper bounds
        resolve toàn bộ lower bounds
        giữ nguyên hướng extends/super và sự phân biệt wildcard
    Khác:
        lưu nguyên hoặc báo rõ kiểu không được hỗ trợ
```

Đây là **pseudocode**, không phải cú pháp Java để biên dịch.

**Quan trọng về identity:** Hai biến có cùng tên `T` nhưng thuộc `class Box<T>` và `<T> void send(T item)` là **hai `TypeVariable<?>` khác nhau**. Không dùng `Map<String, Type>` thuần với khóa `"T"`; dùng trực tiếp `TypeVariable<?>` làm khóa và xác định `getGenericDeclaration()` khi debug.

**Quan trọng về chuỗi ánh xạ:** Trong kế thừa nhiều tầng, có thể gặp `U → T` rồi `T → String`. Phân giải phải tiếp tục theo mapping đến khi ổn định, nhưng cần bảo vệ vòng lặp/bounds tự tham chiếu. Nếu không có binding, trả về type variable chưa giải quyết, **không tự đổi thành `Object`** một cách tùy tiện.

### 6.1. Thu thập bindings từ supertype

```java
class Parent<T> {
    T[] values;
}

class Child<U> extends Parent<U> {}

class Concrete extends Child<String> {}
```

Ánh xạ ban đầu từ `Concrete` là `U → String`. Khi xét superclass của `Child<U>`, thêm `T → U`. Thuật toán phải theo tiếp `T → U → String` rồi mới đưa ra kết quả `String[]` cho field `Parent.values`.

Để chạy trên nhiều tầng interface/superclass, hãy dùng `getGenericSuperclass()`, `getGenericInterfaces()` và đi theo đúng đường dẫn từ class thực tế tới declaration sở hữu member. Không nhập nhằng binding từ các nhánh unrelated; một `TypeVariable` luôn thuộc một declaration cụ thể.

### 6.2. Thu thập bindings từ owner trước member

Với `Outer<String>.Inner<Integer>`:

1. Xử lý `getOwnerType() == Outer<String>`, ánh xạ `Outer.A → String`.
2. Xử lý `getRawType() == Inner.class` và arguments của chính `Inner`, ánh xạ `Inner.B → Integer`.
3. Phân giải field trong `Inner`: `A` và `B` sẽ lần lượt trở thành `String` và `Integer`.

Đối với nested type nhiều tầng, đi tiếp theo chuỗi owner đến lúc `null` hoặc tới `Class<?>` không parameterized. Không coi `getOwnerType()` luôn là `ParameterizedType`: nó có thể là `Class<?>`.

## 7. Chương trình Java 17+ đầy đủ: đọc và phân giải ba nhánh

Chương trình sau dùng một **type renderer** đệ quy. Nó không cố giả làm một thư viện tạo `ParameterizedType`/`WildcardType` mới; thay vào đó, nó đọc cấu trúc Reflection và áp dụng bindings để sinh biểu diễn dễ kiểm thử. Đây là bước học thuật toán trước khi viết resolver trả về `Type` mới.

Tạo tệp **`GenericGraphDemo.java`**:

```java
import java.lang.reflect.*;
import java.util.*;

public class GenericGraphDemo {
    static class Container<A> {
        class Entry<B> {
            A ownerValue;
            B value;
            List<? extends A>[] grids;
        }

        static class Nested<C> {
            C data;
        }
    }

    static class Template<T> {
        T[] values;
        List<? extends T>[] producers;
        List<? super T> consumers;
    }

    static class TextTemplate extends Template<String> {}

    static class Cases {
        Container<String>.Entry<Integer> member;
        Container<String>.Entry<Integer>[] memberArray;
        List<? extends Number>[] numberArrays;
        List<? super Integer> sinks;
        List<?> anything;
        String[] ordinaryArray;
    }

    // Thu thập type-variable bindings từ owner trước,
    // rồi từ type arguments của chính member type.
    static void bind(Type type, Map<TypeVariable<?>, Type> env) {
        if (!(type instanceof ParameterizedType p)) {
            return;
        }
        if (p.getOwnerType() != null) {
            bind(p.getOwnerType(), env);
        }

        Class<?> raw = (Class<?>) p.getRawType();
        TypeVariable<?>[] vars = raw.getTypeParameters();
        Type[] args = p.getActualTypeArguments();

        for (int i = 0; i < vars.length; i++) {
            env.put(vars[i], args[i]);
        }
    }

    static String show(Type type, Map<TypeVariable<?>, Type> env) {
        return show(type, env, new HashSet<>());
    }

    static String show(Type type, Map<TypeVariable<?>, Type> env,
                       Set<TypeVariable<?>> visiting) {
        if (type instanceof Class<?> c) {
            return c.isArray()
                    ? show(c.getComponentType(), env, visiting) + "[]"
                    : c.getSimpleName();
        }

        if (type instanceof TypeVariable<?> v) {
            Type replacement = env.get(v);
            if (replacement == null || replacement.equals(v)
                    || !visiting.add(v)) {
                return v.getName();
            }
            try {
                return show(replacement, env, visiting);
            } finally {
                visiting.remove(v);
            }
        }

        if (type instanceof GenericArrayType a) {
            return show(a.getGenericComponentType(), env, visiting) + "[]";
        }

        if (type instanceof WildcardType w) {
            Type[] lowers = w.getLowerBounds();
            if (lowers.length != 0) {
                return "? super " + show(lowers[0], env, visiting);
            }
            Type[] uppers = w.getUpperBounds();
            if (uppers.length == 0 ||
                    (uppers.length == 1 && uppers[0] == Object.class)) {
                return "?";
            }
            StringJoiner upper = new StringJoiner(" & ");
            for (Type bound : uppers) {
                upper.add(show(bound, env, visiting));
            }
            return "? extends " + upper;
        }

        if (type instanceof ParameterizedType p) {
            Class<?> raw = (Class<?>) p.getRawType();
            Type owner = p.getOwnerType();
            String head = owner == null
                    ? raw.getSimpleName()
                    : show(owner, env, visiting) + "." + raw.getSimpleName();
            Type[] args = p.getActualTypeArguments();
            if (args.length == 0) {
                return head;
            }

            StringJoiner body = new StringJoiner(", ", "<", ">");
            for (Type arg : args) {
                body.add(show(arg, env, visiting));
            }
            return head + body;
        }

        // Để không giả định về các implementation Type khác.
        return type.getTypeName();
    }

    public static void main(String[] args) throws Exception {
        Type numbers = Cases.class
                .getDeclaredField("numberArrays").getGenericType();
        GenericArrayType array = (GenericArrayType) numbers;
        ParameterizedType list =
                (ParameterizedType) array.getGenericComponentType();
        WildcardType upper =
                (WildcardType) list.getActualTypeArguments()[0];

        System.out.println("numberArrays generic array: "
                + (numbers instanceof GenericArrayType));
        System.out.println("component raw List: "
                + (list.getRawType() == List.class));
        System.out.println("upper is Number: "
                + (upper.getUpperBounds()[0] == Number.class));
        System.out.println("lower bound count: "
                + upper.getLowerBounds().length);

        ParameterizedType sinks = (ParameterizedType) Cases.class
                .getDeclaredField("sinks").getGenericType();
        WildcardType lower =
                (WildcardType) sinks.getActualTypeArguments()[0];
        System.out.println("super lower Integer: "
                + (lower.getLowerBounds()[0] == Integer.class));
        System.out.println("super upper Object: "
                + (lower.getUpperBounds()[0] == Object.class));

        Type plain = Cases.class
                .getDeclaredField("ordinaryArray").getGenericType();
        System.out.println("ordinary array Class: "
                + (plain instanceof Class<?>));

        Type members = Cases.class
                .getDeclaredField("memberArray").getGenericType();
        System.out.println("memberArray generic array: "
                + (members instanceof GenericArrayType));

        Type memberType = Cases.class
                .getDeclaredField("member").getGenericType();
        ParameterizedType entry = (ParameterizedType) memberType;
        ParameterizedType owner =
                (ParameterizedType) entry.getOwnerType();
        System.out.println("owner argument String: "
                + (owner.getActualTypeArguments()[0] == String.class));
        System.out.println("inner argument Integer: "
                + (entry.getActualTypeArguments()[0] == Integer.class));

        Map<TypeVariable<?>, Type> byOwner = new HashMap<>();
        bind(memberType, byOwner);

        System.out.println("outer field resolved: "
                + show(Container.Entry.class
                        .getDeclaredField("ownerValue").getGenericType(),
                        byOwner));
        System.out.println("inner field resolved: "
                + show(Container.Entry.class
                        .getDeclaredField("value").getGenericType(),
                        byOwner));
        System.out.println("grids resolved: "
                + show(Container.Entry.class
                        .getDeclaredField("grids").getGenericType(),
                        byOwner));

        Type base = TextTemplate.class.getGenericSuperclass();
        Map<TypeVariable<?>, Type> bySubclass = new HashMap<>();
        bind(base, bySubclass);

        System.out.println("T array resolved: "
                + show(Template.class
                        .getDeclaredField("values").getGenericType(),
                        bySubclass));
        System.out.println("wildcard array resolved: "
                + show(Template.class
                        .getDeclaredField("producers").getGenericType(),
                        bySubclass));
        System.out.println("super wildcard resolved: "
                + show(Template.class
                        .getDeclaredField("consumers").getGenericType(),
                        bySubclass));
    }
}
```

Biên dịch và chạy trong thư mục chứa file bằng JDK 17 trở lên:

```bash
javac GenericGraphDemo.java
java GenericGraphDemo
```

**Stdout dự kiến:**

```text
numberArrays generic array: true
component raw List: true
upper is Number: true
lower bound count: 0
super lower Integer: true
super upper Object: true
ordinary array Class: true
memberArray generic array: true
owner argument String: true
inner argument Integer: true
outer field resolved: String
inner field resolved: Integer
grids resolved: List<? extends String>[]
T array resolved: String[]
wildcard array resolved: List<? extends String>[]
super wildcard resolved: List<? super String>
```

### 7.1. Giải thích cây kiểu quan sát được

- `numberArrays` là mảng với component `List<? extends Number>`; `getGenericType()` trả `GenericArrayType`, bên trong là `ParameterizedType`, rồi một `WildcardType`. Upper bound là `Number`.
- `sinks` có argument `? super Integer` nên lower bound là `Integer`, upper bound mặc định là `Object`.
- `ordinaryArray` có generic type là một `Class<?>` đại diện `String[]`.
- `memberArray` có generic component là `Container<String>.Entry<Integer>` nên là `GenericArrayType`.
- `entry.getActualTypeArguments()` chỉ có `Integer`. `String` nằm ở `entry.getOwnerType()`.
- Hàm `bind` đi qua owner và thêm biến `A` của `Container` cùng biến `B` của `Entry`. Do đó khi duyệt field của `Entry`, `A` được giải thành `String`, `B` thành `Integer`.
- Với `TextTemplate extends Template<String>`, binding từ `getGenericSuperclass()` là `T → String`. Hàm `show` duyệt `T[]`, wildcard và generic array, giữ nguyên cấu trúc rồi thay đúng biến kiểu.

**Giới hạn có chủ đích của ví dụ:** `show` tạo **chuỗi mô tả**, không phải một đối tượng `Type` mới. `bind` minh họa hai tình huống cụ thể (owner parameterized và một superclass parameterized). Trình phân giải dùng trong thư viện thực tế phải hỗ trợ nhiều tầng kế thừa/interface, generic method context, việc thiếu `Signature`, raw types và các triển khai `Type` không chuẩn. Không coi đoạn demo là hệ thống generic resolver đầy đủ cho mọi bytecode hợp lệ.

## 8. Nếu cần trả về `Type` đã thay thế, không chỉ một chuỗi

Trong những ứng dụng như schema generation hoặc serializer, kết quả thường cần là **cấu trúc `Type` mới** chứ không phải chuỗi hiển thị. Khi đó:

- `Class<?>` thông thường có thể giữ nguyên.
- `TypeVariable<?>` có binding phải được thay bằng `Type` tương ứng.
- `ParameterizedType` cần biểu diễn mới chứa **owner đã phân giải**, **raw type** và **actual type arguments đã phân giải**.
- `GenericArrayType` cần biểu diễn mới chứa **generic component type đã phân giải**; nếu component đã thành một `Class<?>` hợp lệ, có thể tạo class mảng tương ứng bằng `Array.newInstance(...)`.
- `WildcardType` cần biểu diễn mới với **upper/lower bounds đã phân giải**, không đánh mất hướng `extends`/`super`.
- Nếu không đổi gì, có thể giữ nguyên `Type` ban đầu thay vì tự tạo object.

Các interface `ParameterizedType`, `GenericArrayType` và `WildcardType` mô tả giao diện dữ liệu; Java SE không cung cấp một public factory chuẩn chung để bạn tùy ý tạo mọi tổ hợp. Thư viện có thể sử dụng implementation `Type` riêng, nhưng cần thực hiện đúng `equals`/`hashCode`, mảng bounds/arguments mang bản sao phòng thủ, và semantics của `getTypeName()`/`toString()`. **Không tạo `Class<?>` giả để biểu diễn `List<String>`:** tại runtime, `List.class` không chứa type argument `String`.

Ví dụ, sau `T → String`:

- `T[]` có thể trở thành `String[].class` nếu cần class mảng reifiable.
- `List<? extends T>[]` vẫn cần biểu diễn kiểu generic cho component `List<? extends String>`; không có class literal `List<? extends String>[].class`.
- `Outer<T>.Inner<Integer>` phải thay cả owner `Outer<String>`, không chỉ inner argument `Integer`.

## 9. Giới hạn type erasure và metadata

Không phải `Type` nào bạn mong muốn cũng luôn tồn tại với đủ thông tin:

- **Generic signature có thể bị thiếu.** JVM descriptors của field/method sau erasure không mô tả đầy đủ wildcard và type arguments. Reflection generic APIs dựa vào metadata `Signature` nếu có.
- **Raw type không đủ để tái tạo generic.** Từ `List.class` không thể suy ra giá trị là `List<String>` hay `List<Integer>`.
- **Method type parameters có phạm vi riêng.** Một `<T> T convert(T input)` không tự giải được `T` chỉ nhờ class generic bindings; có thể cần thông tin từ ngữ cảnh gọi mà Reflection cơ bản không cung cấp.
- **Generic arrays không biến JVM array thành cấu trúc kiểm tra type argument.** Kiểu của array ở runtime vẫn chịu erasure và có thể dẫn đến heap pollution nếu lập trình viên dùng raw types/unchecked casts sai.
- **Recursive bounds:** `class Node<T extends Comparable<T>>` có tham chiếu lại chính `T`. Thuật toán in/giải bounds cần chặn vòng lặp, tránh đệ quy vô hạn.
- **Module visibility / classpath:** khi đọc generic metadata, Reflection có thể gặp `TypeNotPresentException`, `MalformedParameterizedTypeException` hoặc `GenericSignatureFormatError` nếu kiểu liên quan không có hoặc signature không hợp lệ. Không coi mọi thông tin thiếu là do wildcard logic.
- **Không suy ra syntax gốc:** dạng viết import, whitespace hoặc cách khai báo tương đương không thể tái tạo chính xác từ `Type.getTypeName()`.

### 9.1. Đừng “giải” type variable bằng upper bound một cách âm thầm

Giả sử:

```java
class NumericBox<T extends Number> {
    T value;
}
```

Nếu bạn đọc `NumericBox.class` mà **chưa có** ngữ cảnh như `NumericBox<Integer>`, `T` vẫn là `TypeVariable<T>`. Biết `getBounds()[0] == Number.class` không cho phép kết luận field thực tế là `Number` về generic semantic: `Number` chỉ là **giới hạn trên**, không phải argument đã gắn. Có thể dùng bound trong validation hoặc erased view nếu bài toán yêu cầu, nhưng phải giữ rõ sự khác biệt.

### 9.2. Chuỗi tên không thay thế cấu trúc

`Type.getTypeName()` hữu ích để log. Tuy nhiên:

- Không parse/replace `"T"` bằng `"String"` trong một chuỗi dài vì có thể trùng tên kiểu, trùng tên biến hoặc biến ở declaration khác.
- Không cắt chuỗi theo dấu `<`, `>`, `[` để tìm wildcard/array; đây là công việc của cây `Type` và các API phản ánh.
- Không dựa hoàn toàn vào tên do implementation `toString()` sinh ra để xác định type identity.
- Dùng `TypeVariable.getGenericDeclaration()`, `ParameterizedType.getRawType()` và các mảng `Type` con để phân giải chính xác.

## 10. Quy trình ứng dụng trong framework

Một cách triển khai có kiểm soát:

1. **Xác định member declaration:** field, method, parameter, superclass hay interface.
2. **Lấy generic metadata:** `getGenericType()` / `getGenericReturnType()` / `getGenericParameterTypes()`; tránh bắt đầu bằng `getType()` nếu cần generic arguments.
3. **Xác định ngữ cảnh sử dụng:** class con, owner type đã parameterized, hoặc type token có lưu generic args.
4. **Xây binding theo declaration:** đi từ context tới class sở hữu member; duyệt superclass/interface và owner type; dùng `TypeVariable<?>` làm khóa.
5. **Duyệt đệ quy mọi nhánh:** `ParameterizedType`, `WildcardType`, `GenericArrayType`, `TypeVariable`, `Class<?>`, kể cả mảng thường.
6. **Giữ đúng semantics:** wildcard bounds theo chiều, array component, owner parameterization, thứ tự type arguments và raw type.
7. **Xử lý chưa xác định:** giữ biến chưa ràng buộc thay vì tự cho là `Object`; nếu signature sai/mất, báo hạn chế.
8. **Kiểm thử:** có test cho `List<?>`, `? extends`, `? super`, `T[]`, `List<T>[]`, `Outer<String>.Inner<Integer>`, static nested class và kế thừa nhiều tầng.

### Bảng lỗi thường gặp

| Lỗi | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Cast mọi `Type` thành `Class<?>` | `ClassCastException` hoặc mất generics | Duyệt theo `instanceof` |
| Bỏ `WildcardType.getLowerBounds()` | `? super Integer` bị hiểu sai | Phân biệt upper/lower |
| Mặc định wildcard có lower bound | Sai với `?` và `? extends X` | Mảng lower có thể rỗng |
| Giải wildcard bằng cách lấy upper bound duy nhất | Mất dấu `?` và hướng variance | Giữ node wildcard khi tái cấu trúc |
| Chỉ hỗ trợ `GenericArrayType` | Bỏ sót `String[]` phản ánh bằng `Class` | Xử lý cả `Class.isArray()` |
| Chỉ dùng `getActualTypeArguments()` ở inner type | Mất `A → String` từ `Outer<String>` | Đọc `getOwnerType()` đệ quy |
| `getOwnerType() != null` nghĩa là cần outer object | Nhầm generic metadata với enclosing instance | Xét `static` và cấu trúc member class riêng |
| Dùng `Map<String,Type>` với tên `T` | Đụng tên type variable khác declaration | Dùng `TypeVariable<?>` làm key |
| Gặp `T extends Number` thì luôn map `T → Number` | Cố định hóa type argument chưa biết | Giữ unresolved `T` |
| Parse `getTypeName()` thay vì đi cây | Sai khi lồng generic/wildcard/owner | Dùng các subtype `Type` |
| Giữ nguyên `ParameterizedType` dù owner đã đổi | Kết quả nội bộ bất nhất | Phân giải cả owner và arguments |
| Giả định metadata generic luôn đầy đủ | Không xử lý class file thiếu/malformed `Signature` | Xử lý trường hợp thiếu và các lỗi phản ánh |

## 11. Bài tự kiểm tra

### Câu 1: wildcard bounds

Với `List<? super Long>`, `getUpperBounds()` và `getLowerBounds()` của wildcard thường có gì?

**Đáp án:** Upper chứa `Object.class`; lower chứa `Long.class`. Nếu bạn chỉ đọc upper, bạn đã làm mất thông tin quan trọng nhất của `super`.

### Câu 2: generic array

Field `Map<String,Integer>[] mapping` có `getGenericType()` là `Class<?>` hay `GenericArrayType`?

**Đáp án:** `GenericArrayType`; component là `ParameterizedType` biểu diễn `Map<String,Integer>`. Nó khác field `String[] names` có `Class<?>` là array class.

### Câu 3: owner type

Cho `Outer<String>.Inner<Integer>` với `class Outer<A> { class Inner<B> {} }`. Vì sao `Inner.class.getTypeParameters()` không giúp tìm `A → String`?

**Đáp án:** `Inner` chỉ khai báo biến `B`. `A` thuộc declaration `Outer`, và argument `String` nằm trong `ParameterizedType.getOwnerType()`.

### Câu 4: nested static

`Outer.Nested<Integer>` là static nested class. Nếu Reflection cho thấy owner type khác `null`, có bắt buộc tạo một đối tượng `Outer` để gọi constructor của `Nested` không?

**Đáp án:** Không. Owner type ở đây là thông tin cấu trúc kiểu. Static nested class không cần enclosing instance cho riêng quan hệ lồng lớp này.

### Câu 5: chuỗi thay thế kiểu

Giả sử `Parent<T>`, `Child<U> extends Parent<U>`, `Concrete extends Child<String>`. Muốn giải field `Parent<T>.value` trong ngữ cảnh `Concrete` thì đi như thế nào?

**Đáp án:** Xây `U → String` và `T → U`, sau đó đệ quy `T → U → String`. Ánh xạ theo `TypeVariable<?>` của từng declaration, không theo tên string.

### Câu 6: metadata chưa đủ

Nếu chỉ có `List.class` trong runtime, thuật toán có thể biết chắc type argument nguồn là `String` không?

**Đáp án:** Không. Erasure đã bỏ tham số kiểu; cần một `ParameterizedType` trong generic declaration, type token, hoặc metadata khác có thực, nếu không phải ghi nhận là chưa biết.

## 12. Tổng kết

Khi phân giải generic bằng Reflection, cần xem `Type` là **cây cấu trúc**:

- **`WildcardType`:** bảo toàn upper/lower bounds và hướng `extends`/`super`.
- **`GenericArrayType`:** phân giải đệ quy component type; phân biệt với mảng thường phản ánh bằng `Class<?>`.
- **`ParameterizedType.getOwnerType()`:** lần theo kiểu sở hữu của member type để không mất bindings của outer generic class.
- **`TypeVariable<?>`:** ánh xạ bằng identity của declaration, đi theo các ràng buộc ngữ cảnh và bảo vệ vòng lặp.

Một resolver tốt trả về metadata có ý nghĩa hoặc ghi rõ điểm chưa biết; nó không tự chế lại generic arguments từ raw `Class<?>` và không đồng nhất type metadata với enclosing instance runtime.

### Tài liệu tham khảo

- [Java SE 21 — `Type`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Type.html)
- [Java SE 21 — `ParameterizedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/ParameterizedType.html)
- [Java SE 21 — `WildcardType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/WildcardType.html)
- [Java SE 21 — `GenericArrayType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/GenericArrayType.html)
- [Java SE 21 — `TypeVariable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/TypeVariable.html)
- [Java Language Specification `4.5 — Parameterized Types](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-resolution-006">Biến kiểu của phương thức khác biến kiểu của lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Biến kiểu của phương thức khác biến kiểu của lớp trong Java Reflection

## 1. Mục tiêu và vấn đề cần phân biệt

Java Generics cho phép khai báo **biến kiểu** (*type variable*) trên **lớp/interface** hoặc trên **phương thức/constructor**. Hai biến kiểu có thể mang **cùng tên**, ví dụ đều là `T`, nhưng vẫn là hai khai báo độc lập với phạm vi và ràng buộc khác nhau.

```java
class Box<T extends Number> {
    T value;

    // Đây là T của lớp Box.
    T get() {
        return value;
    }

    // Đây là T MỚI của riêng phương thức echo, che khuất tên T của lớp.
    <T extends CharSequence> T echo(T input) {
        return input;
    }

    // U thuộc phương thức; T trong tham số đầu thuộc lớp.
    <U> String describe(T item, U extra) {
        return item + ":" + extra;
    }
}
```

Đoạn code trên minh họa cú pháp trong một lớp; chưa phải ứng dụng độc lập (ví dụ hoàn chỉnh ở mục 4).

Trong `Box<Integer>`, biến kiểu `T` của lớp được liên kết với `Integer` tại điểm dùng kiểu có tham số. Tuy nhiên `<T extends CharSequence> T echo(T input)` **khai báo một `T` mới**, có phạm vi riêng của phương thức, vì vậy lời gọi `box.echo("hello")` vẫn hợp lệ. Không được suy ra rằng `echo` buộc phải nhận `Integer` chỉ vì `Box` sử dụng `Integer`.

Với Java Reflection, phân biệt này thể hiện trực tiếp ở `TypeVariable<?>` và nơi khai báo `GenericDeclaration`. Bài học giải thích cách quan sát, xác định nguồn gốc, phân giải và tránh nhầm lẫn giữa **biến kiểu của lớp** với **biến kiểu của phương thức**, kể cả khi hai biến đều tên `T`.

### Kết quả cần đạt

Sau bài, bạn sẽ:

- Xác định `T` thuộc lớp hay phương thức bằng **vị trí khai báo**, không chỉ bằng tên.
- Phân biệt `Class.getTypeParameters()` với `Method.getTypeParameters()` và `Constructor.getTypeParameters()`.
- Dùng `TypeVariable.getGenericDeclaration()` để biết owner thực sự.
- Đọc `getGenericReturnType()`, `getGenericParameterTypes()`, `getBounds()` và nhận biết type erasure.
- Giải thích hiện tượng **shadowing** khi biến kiểu của phương thức cùng tên với biến kiểu của lớp.
- Phân biệt việc Reflection nhìn thấy generic signature với việc biết kiểu cụ thể như `Integer` hoặc `String` ở runtime.

## 2. Biến kiểu được khai báo ở đâu?

### 2.1. Biến kiểu của lớp

```java
class Holder<T> {
    private T item;

    Holder(T item) {
        this.item = item;
    }

    T get() {
        return item;
    }
}
```

Trong `Holder<T>`, tên `T` được khai báo ở danh sách tham số kiểu **ngay sau tên lớp**. Trong các vị trí dùng `T` của lớp (nơi không bị che khuất), một instance có kiểu nguồn `Holder<String>` cho phép trình biên dịch kiểm tra `T` là `String`.

Ví dụ, `Holder<String>` và `Holder<Integer>` là hai cách sử dụng generic class khác nhau; tại runtime, chúng thường cùng trỏ đến **một định nghĩa `Holder.class`** do type erasure. Object `Class` của `Holder` phản ánh **khai báo** `T`, không tự biết đối tượng cụ thể đã được tạo với tham số `String` hay `Integer`.

### 2.2. Biến kiểu của phương thức

```java
class Converter {
    public static <T> T identity(T input) {
        return input;
    }

    public <U extends Number> U keep(U number) {
        return number;
    }
}
```

Biến kiểu được khai báo **trước return type** trong khai báo phương thức: `<T>`, `<U extends Number>`. Nó thuộc riêng phương thức đó, cho dù lớp bên ngoài có generic hay không.

Một lớp không generic vẫn có thể chứa method generic, và một lớp generic vẫn có thể chứa method không generic hoặc method generic với biến kiểu riêng.

**Phương thức static** không được tham chiếu trực tiếp tới biến kiểu thuộc instance của lớp generic, nhưng hoàn toàn có thể **tự khai báo** biến kiểu riêng:

```java
class Factory<T> {
    // Không dùng trực tiếp class T trong bối cảnh static.
    static <S> S identity(S value) {
        return value;
    }
}
```

Ở đây `S` thuộc method `identity`, không phải biến kiểu `T` của `Factory`. Không thể viết method static chỉ dùng `T` lớp trong chữ ký mà không có khai báo `<T>` riêng phù hợp.

### 2.3. Biến kiểu của constructor

Constructor cũng có thể khai báo biến kiểu riêng:

```java
class Sample<T> {
    <U> Sample(U input) {
        System.out.println(input);
    }
}
```

`T` thuộc lớp, còn `U` thuộc constructor. Reflection cung cấp `Constructor.getTypeParameters()` để lấy `U`. Tương tự, `U.getGenericDeclaration()` trả về đối tượng `Constructor<?>`, không phải `Class<?>`.

Các ví dụ mục 2 là các đoạn minh họa cấu trúc Java riêng lẻ; ví dụ mục 4 gồm chương trình có `main` chạy được.

## 3. API Reflection cần dùng

| Câu hỏi | API | Trả về / ý nghĩa |
| --- | --- | --- |
| Lớp khai báo biến kiểu nào? | `Class.getTypeParameters()` | Mảng `TypeVariable<Class<T>>[]` (qua wildcard ở code dùng chung) của chính lớp/interface |
| Phương thức khai báo biến kiểu nào? | `Method.getTypeParameters()` | `TypeVariable<Method>[]` của chính phương thức |
| Constructor khai báo biến kiểu nào? | `Constructor.getTypeParameters()` | `TypeVariable<Constructor<T>>[]` của constructor |
| Biến kiểu thuộc khai báo nào? | `TypeVariable.getGenericDeclaration()` | `GenericDeclaration`: thường là `Class`, `Method` hoặc `Constructor` |
| Tên và cận trên của biến kiểu? | `TypeVariable.getName()`, `getBounds()` | Tên như `T`; các `Type` ràng buộc trên |
| Kiểu trả về generic của method? | `Method.getGenericReturnType()` | `Type`; có thể là `TypeVariable`, `ParameterizedType`, `Class`... |
| Các kiểu tham số generic? | `Method.getGenericParameterTypes()` | Mảng `Type[]` của khai báo generic |
| Kiểu trả về sau erasure? | `Method.getReturnType()` | `Class<?>` đại diện kiểu runtime đã erasure |
| Các kiểu tham số runtime sau erasure? | `Method.getParameterTypes()` | `Class<?>[]`, dùng để tra cứu chữ ký bằng `getDeclaredMethod` |
| Generic superclass của một lớp? | `Class.getGenericSuperclass()` | `Type`, có thể là `ParameterizedType` như `Box<Integer>` |

**Phân biệt hai chữ “parameters”:**

- `getTypeParameters()`: các biến kiểu được **khai báo mới** ở lớp hoặc phương thức (ví dụ `<T, U>`).
- `getGenericParameterTypes()`: các **kiểu của tham số truyền vào** một phương thức (ví dụ `List<T> input, U extra`). Một phương thức không tự khai báo `<T>` vẫn có thể sử dụng biến kiểu `T` của lớp trong danh sách tham số và vì vậy trả mảng `getTypeParameters()` rỗng nhưng `getGenericParameterTypes()` không rỗng.

Các `Type` đặc biệt trong `java.lang.reflect` gồm `TypeVariable<?>`, `ParameterizedType`, `WildcardType` và `GenericArrayType`. Không phải mọi `Type` đều là `Class<?>`; dùng `instanceof` trước khi ép kiểu sang một nhánh cụ thể.

### GenericDeclaration và danh tính của biến kiểu

`TypeVariable.getGenericDeclaration()` là điểm then chốt. Trong Reflection, hai biến cùng tên chỉ biểu diễn cùng một biến kiểu khi chúng **tương ứng với cùng khai báo generic**; tên đơn thuần không đủ xác nhận.

Ví dụ, ở `Box<T>` và `<T> T echo(T)`:

- `classT.getName()` và `methodT.getName()` đều là `"T"`.
- `classT.getGenericDeclaration()` là `Box.class`.
- `methodT.getGenericDeclaration()` là `Method` biểu diễn `echo`.
- Vì khai báo khác nhau, `classT.equals(methodT)` là `false`.

Dùng `getGenericDeclaration()` cùng vị trí/đối tượng khai báo khi lập trình resolver; **không** dùng một map chỉ khóa theo tên `"T"`, vì tên đó có thể được dùng lặp ở nhiều scope.

## 4. Chương trình Java 17+ hoàn chỉnh: hai chữ T nhưng hai biến kiểu

Lưu toàn bộ mã sau vào **`TypeVariableScopeDemo.java`**:

```java
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;

public class TypeVariableScopeDemo {
    static class Box<T extends Number> {
        private final T value;

        Box(T value) {
            this.value = value;
        }

        public T get() {
            return value;
        }

        // T riêng của method, che tên T của class Box.
        public <T extends CharSequence> T echo(T input) {
            return input;
        }

        // U riêng của method; T trong tham số đầu thuộc Box.
        public <U> String describe(T item, U extra) {
            return item + ":" + extra;
        }

        // Method static được tự khai báo type variable S.
        public static <S extends CharSequence> S identity(S value) {
            return value;
        }
    }

    static class IntegerBox extends Box<Integer> {
        IntegerBox(Integer value) {
            super(value);
        }
    }

    public static void main(String[] args) throws Exception {
        Class<?> cls = Box.class;
        TypeVariable<?> classT = cls.getTypeParameters()[0];
        Method echo = cls.getDeclaredMethod(
                "echo", CharSequence.class);
        TypeVariable<?> methodT = echo.getTypeParameters()[0];
        Method get = cls.getDeclaredMethod("get");
        Method describe = cls.getDeclaredMethod(
                "describe", Number.class, Object.class);
        Method identity = cls.getDeclaredMethod(
                "identity", CharSequence.class);
        TypeVariable<?> methodU = describe.getTypeParameters()[0];

        System.out.println("class name: " + classT.getName());
        System.out.println("method name: " + methodT.getName());
        System.out.println("same name: "
                + classT.getName().equals(methodT.getName()));
        System.out.println("same variable: " + classT.equals(methodT));

        System.out.println("class owner Class: "
                + (classT.getGenericDeclaration() == cls));
        System.out.println("method owner Method: "
                + methodT.getGenericDeclaration().equals(echo));

        System.out.println("class bound: "
                + classT.getBounds()[0].getTypeName());
        System.out.println("method bound: "
                + methodT.getBounds()[0].getTypeName());

        System.out.println("get return is class T: "
                + get.getGenericReturnType().equals(classT));
        System.out.println("echo return is method T: "
                + echo.getGenericReturnType().equals(methodT));

        System.out.println("echo erased param: "
                + echo.getParameterTypes()[0].getSimpleName());
        System.out.println("echo erased return: "
                + echo.getReturnType().getSimpleName());

        System.out.println("describe owns U: " + methodU.getName());
        System.out.println("describe first param is class T: "
                + describe.getGenericParameterTypes()[0].equals(classT));
        System.out.println("describe second param is method U: "
                + describe.getGenericParameterTypes()[1].equals(methodU));

        System.out.println("static method variable: "
                + identity.getTypeParameters()[0].getName());

        ParameterizedType parent = (ParameterizedType)
                IntegerBox.class.getGenericSuperclass();
        System.out.println("subclass argument: "
                + parent.getActualTypeArguments()[0].getTypeName());

        Box<Integer> box = new Box<>(10);
        System.out.println("box get: " + box.get());
        System.out.println("echo: " + box.echo("hello"));
    }
}
```

Lệnh biên dịch/chạy:

```bash
javac TypeVariableScopeDemo.java
java TypeVariableScopeDemo
```

**Output** (đã đối chiếu hoạt động Java Reflection trên JVM):

```text
class name: T
method name: T
same name: true
same variable: false
class owner Class: true
method owner Method: true
class bound: java.lang.Number
method bound: java.lang.CharSequence
get return is class T: true
echo return is method T: true
echo erased param: CharSequence
echo erased return: CharSequence
describe owns U: U
describe first param is class T: true
describe second param is method U: true
static method variable: S
subclass argument: java.lang.Integer
box get: 10
echo: hello
```

### Giải thích kết quả cốt lõi

**1. Cùng tên nhưng khác identity.** Cả `Box<T>` và `echo<T>` đều có `getName() == "T"`, nhưng `classT.equals(methodT)` là `false`. Vì vậy resolver phải xác định **declaration owner**, thay vì map tất cả biến tên `T` sang một kiểu thực duy nhất.

**2. Bounds khác nhau.** `T` của lớp có bound `Number`; `T` của `echo` có bound `CharSequence`. Tức là `Box<Integer>` chấp nhận đối tượng Number làm class parameter, trong khi một lời gọi `echo("hello")` chấp nhận String do String triển khai CharSequence. Hai ràng buộc áp dụng cho hai biến kiểu độc lập.

**3. Return type generic khác return type sau erasure.** `get.getGenericReturnType()` là chính biến kiểu class `T`. `echo.getGenericReturnType()` là biến kiểu method `T`. Nhưng `echo.getReturnType()` là `CharSequence.class` do erasure sử dụng cận trên của biến kiểu method.

**4. Tra cứu phương thức dùng chữ ký erased.** `Box.class.getDeclaredMethod("echo", CharSequence.class)` thành công. Dùng `Object.class` hay `Number.class` thay cho `CharSequence.class` sẽ không tìm đúng method vì kiểu tham số erased của method này là `CharSequence`.

**5. Phương thức dùng cả biến kiểu class và method.** `describe` tự khai báo `U`; tham số đầu `T item` vẫn liên kết với class `Box<T>`. Vì thế `getTypeParameters()` của `describe` chỉ chứa `U`, còn `getGenericParameterTypes()` cho thấy cả `T` của lớp lẫn `U` của method.

**6. Static method có biến kiểu riêng.** `identity` là static generic method với `S` do chính nó khai báo; đó không phải `T` của lớp.

**7. Generic superclass cho biết binding trong một nhánh cụ thể.** `IntegerBox extends Box<Integer>` khai báo rõ `Integer` trong generic superclass, vì vậy `IntegerBox.class.getGenericSuperclass()` trả một `ParameterizedType` chứa type argument `Integer`. Nhưng `Box.class.getTypeParameters()[0]` vẫn biểu diễn **khai báo `T`**, không tự đổi thành `Integer`.

## 5. Shadowing: method có thể khai báo T trùng tên T của class

Giả sử:

```java
class Repository<T> {
    T current;

    T load() {
        return current; // T của Repository
    }

    <T> T transform(T input) {
        return input;   // T mới của transform
    }

    <U> T update(U source) {
        return current; // U của update; T của Repository
    }
}
```

Đây là đoạn minh họa về phạm vi tên; không cần chạy riêng. **Shadowing** xảy ra khi declaration `<T>` ở method che tên `T` của lớp trong phạm vi method đó. Nhờ phân biệt owner, Reflection có thể quan sát hai biến có tên giống nhau mà không nhập nhằng.

| Thành phần | Biến kiểu khai báo trực tiếp ở thành phần đó | Ý nghĩa của T xuất hiện trong chữ ký |
| --- | --- | --- |
| `Repository<T>` | `T` của class | Class-level type variable |
| `load()` | Không có | `T` return thuộc class |
| `<T> T transform(T)` | `T` của method | `T` trong return và tham số thuộc method |
| `<U> T update(U)` | `U` của method | `T` return thuộc class, `U` tham số thuộc method |

Khi làm resolver, nên giữ mô hình dữ liệu theo cả **scope của khai báo** lẫn biến kiểu:

```text
Repository<T>              : key = (Repository.class, "T")
Repository.transform<T>(T)  : key = (Method transform, "T")
Repository.update<U>(U)     : key = (Method update, "U")
```

Đây là **mô hình minh họa**, không phải Java code runnable. Chính API `getGenericDeclaration()` và tính tương đương của `TypeVariable` giúp phân biệt những trường hợp trên.

Một lỗi phổ biến là lưu mapping dưới dạng `Map<String, Type>` với key chỉ là `"T"`. Khi resolver đi qua phương thức `transform`, mapping mới có thể **ghi đè** binding `T` của lớp. Kết quả giải thích generic trở nên sai dù code vẫn compile.

## 6. TypeVariable.getBounds() và erasure

Khi khai báo:

```java
class Container<T extends Number> {
    <U extends CharSequence> U use(U text) {
        return text;
    }
}
```

`T.getBounds()` của lớp trả bound `Number`; `U.getBounds()` của phương thức trả bound `CharSequence`. Với một biến kiểu không có bound tường minh như `<T>`, bound ngầm định là `Object`.

### Nhiều bound

```java
class MultiBound<T extends Number & Comparable<T>> {
    T value;
}
```

`getBounds()` có thể trả một mảng `Type[]` gồm nhiều bound theo thứ tự khai báo. Khi thực hiện erasure của một biến kiểu, **bound bên trái** quyết định kiểu erasure; trong ví dụ là `Number`.

Đừng lấy duy nhất `getBounds()[0]` nếu nhiệm vụ cần bảo toàn **mọi constraint**. Ví dụ runnable mục 4 chỉ dùng một bound để output dễ hiểu; resolver thực tế phải xử lý cả intersection bounds, tham chiếu generic trong bounds và trường hợp type variable sử dụng chính nó (chẳng hạn `Comparable<T>`).

### Generic metadata và class runtime

Hai API khác nhau có thể đưa kết quả khác nhau:

```java
Method method = Box.class.getDeclaredMethod(
        "echo", CharSequence.class);

System.out.println(method.getReturnType());        // interface java.lang.CharSequence
System.out.println(method.getGenericReturnType()); // T
```

Đoạn trên cần đặt trong một phương thức xử lý `NoSuchMethodException`, đã import `Method` và dùng lớp `Box` như ví dụ. `getReturnType()` xem return type **sau erasure** dưới dạng `Class<?>`; `getGenericReturnType()` giữ `TypeVariable` `T` của method. Cùng một phương thức, hai góc nhìn đều đúng.

Với generic type dùng kiểu tham số hóa, như `List<T>`, `getGenericReturnType()` có thể là `ParameterizedType`. Khi xử lý, dùng `instanceof ParameterizedType` để lấy `getRawType()` và `getActualTypeArguments()`; một type argument có thể là `TypeVariable` của lớp, của method, một wildcard hoặc cấu trúc phức tạp khác. Không cast `Type` sang `Class<?>` một cách mặc định.

## 7. Vì sao Class.getTypeParameters() không trả biến kiểu của method?

`Class.getTypeParameters()` chỉ liệt kê biến kiểu **được class/interface tự khai báo**. Nó không tập hợp `T` hoặc `U` từ tất cả method thành viên.

Ví dụ:

```java
class Plain {
    <X> X echo(X input) { return input; }
}
```

`Plain.class.getTypeParameters()` là mảng **rỗng**, vì `Plain` không phải generic class. Ngược lại:

```java
Method m = Plain.class.getDeclaredMethod("echo", Object.class);
TypeVariable<?>[] vars = m.getTypeParameters();
```

Đây là đoạn trong một phương thức có xử lý ngoại lệ. `vars.length` là `1`, và biến ở `vars[0]` tên `X`. Chữ ký erased của method dùng `Object` do `X` không có bound tường minh.

Cũng vậy, `Method.getTypeParameters()` trả về **mảng rỗng** cho một method bình thường dùng type variable thuộc lớp nhưng không khai báo type variable mới:

```java
class GenericService<T> {
    T fetch() { return null; }
}
```

Khi inspect `fetch()`, `getTypeParameters()` rỗng dù `getGenericReturnType()` là `T` của class. Để biết owner của `T` này, kiểm tra `TypeVariable.getGenericDeclaration()`.

## 8. Phân giải generic ở lớp con và giới hạn cần biết

Giả sử:

```java
class Parent<T> {
    T get() { return null; }
}

class Child extends Parent<String> {
}
```

Ở lớp `Child`, generic superclass chứa binding `Parent<String>`. Nhưng đối tượng `Method` được khai báo ở `Parent` với return type `T` vẫn giữ metadata `T` của **Parent**. Reflection **không tự thay biến kiểu đó bằng String** chỉ vì caller nhận method thông qua `Child.class`.

Resolver muốn giải đúng return type theo ngữ cảnh `Child` phải:

1. Xác định method được khai báo ở lớp/interface nào bằng `getDeclaringClass()`.
2. Duyệt quan hệ `getGenericSuperclass()` và `getGenericInterfaces()` theo đúng phân cấp.
3. Thu thập mapping từ biến kiểu **của từng declaration** sang type argument cụ thể, chẳng hạn `Parent<T> → String`.
4. Thay thế biến kiểu bằng mapping đúng owner, có xử lý `ParameterizedType`, `WildcardType`, `GenericArrayType` và các biến kiểu trung gian nếu cần.

**Điểm quan trọng:** mapping `Parent.T → String` **không được** tự áp lên `T` khác do một method `<T>` trong Parent khai báo. Hai `T` đó không phải cùng biến kiểu, dù chúng cùng tên.

### Method type argument suy diễn tại compile time

Ví dụ:

```java
class Tools {
    static <T> T identity(T value) {
        return value;
    }
}

String text = Tools.identity("hello");
Integer count = Tools.identity(123);
```

Hai câu lệnh sử dụng method generic với hai cách suy diễn khác nhau ở compile time. Nhưng Reflection trên `Tools.identity` chỉ nhìn thấy **một khai báo `<T> T identity(T)`**. Nó không lưu một binding method-T cố định là String hay Integer trên đối tượng `Method`.

Khi gọi `Method.invoke(...)`, caller phải dùng các kiểu runtime thích hợp; thông tin về type argument suy diễn ở compile time không trở thành metadata “lời gọi method này dùng T = String” trong `Method`.

Nếu API của ứng dụng cần theo dõi binding từ một phương thức generic trong ngữ cảnh cụ thể, nó phải nhận đủ thông tin bổ sung (kiểu của tham số/return expected type/quy tắc resolver) và không suy diễn quá mức từ `Method.getTypeParameters()`.

## 9. Constructor type variable khác class type variable

`Constructor<?>` cũng triển khai `GenericDeclaration`. Điều đó cho phép phân biệt biến kiểu ở constructor với biến kiểu ở class:

```java
class Factory<T> {
    <U extends CharSequence> Factory(U name) {
    }
}
```

Đoạn minh họa:

```java
Class<?> type = Factory.class;
TypeVariable<?> classT = type.getTypeParameters()[0];

Constructor<?> ctor =
        type.getDeclaredConstructor(CharSequence.class);
TypeVariable<?> ctorU = ctor.getTypeParameters()[0];
```

Đây là code đặt trong một phương thức có xử lý/khai báo `NoSuchMethodException` và import `java.lang.reflect.Constructor`, `java.lang.reflect.TypeVariable`.

- `classT.getGenericDeclaration() == Factory.class` là `true`.
- `ctorU.getGenericDeclaration().equals(ctor)` là `true`.
- `ctorU.getName()` là `"U"`, và `getBounds()[0]` là `CharSequence`.
- `getDeclaredConstructor(CharSequence.class)` tra cứu theo **erasure của bound**, không dùng một type argument suy diễn nào như `String.class`.

Với non-static inner class, constructor runtime còn có thể xuất hiện tham số outer-instance do compiler sinh. Đó là vấn đề về chữ ký constructor, **khác** việc constructor có khai báo `<U>` hay không.

## 10. Lỗi thường gặp khi xây dựng generic resolver

| Sai lầm | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| So sánh hai `TypeVariable` chỉ qua `getName()` | Class-T và method-T có thể cùng tên | So sánh identity/declaration owner |
| Giả định class generic thì mọi method trả class-T | Method có thể khai báo T mới và shadow class-T | Đọc `Method.getTypeParameters()` và `getGenericReturnType()` |
| Cho rằng `Class.getTypeParameters()` gồm cả generic methods | API chỉ trả biến kiểu khai báo ở class | Truy vấn trên từng `Method` |
| Cho rằng `Method.getTypeParameters()` là kiểu các tham số runtime | Nó trả biến kiểu method khai báo, không phải argument types | Dùng `getGenericParameterTypes()` hoặc `getParameterTypes()` |
| Dùng `getReturnType()` để hiểu đầy đủ generic | Erasure làm mất biểu thức generic | Dùng `getGenericReturnType()` |
| Tra cứu `echo(T)` bằng `Object.class` khi `T extends CharSequence` | Erased parameter là CharSequence | Tra cứu bằng `CharSequence.class` |
| Đổi mọi T thành Integer vì có `Box<Integer>` | Chỉ class-T được binding; method-T độc lập | Mapping theo `GenericDeclaration` |
| Tin rằng mỗi call `identity("x")` giữ riêng method-T = String trong `Method` | Type inference compile-time không được lưu như vậy | Dùng context/hợp đồng kiểu riêng |
| Ép mọi `Type` sang `Class<?>` | `TypeVariable` và `ParameterizedType` không phải `Class` | Phân nhánh `instanceof` |
| Bỏ qua multi-bound và nested parameterized type | Resolver có thể chọn erasure hoặc constraint sai | Xử lý các loại `Type` và tất cả bounds |
| Dựa vào tên biến để suy ra kiểu runtime object | Erasure và tính đa hình làm suy luận không đủ | Kiểm tra object thực nếu cần |

### Thiết kế map thay thế an toàn

Một resolver nên sử dụng khóa có danh tính khai báo rõ ràng, ví dụ trực tiếp `TypeVariable<?>`:

```java
Map<TypeVariable<?>, java.lang.reflect.Type> bindings =
        new java.util.HashMap<>();
```

Đây là đoạn trong class/phương thức có import `java.util.Map`, `java.lang.reflect.TypeVariable`; ví dụ không cung cấp thuật toán resolver hoàn chỉnh. `TypeVariable.equals` được xác định theo generic declaration tương ứng, giúp tránh tình huống hai biến tên giống nhau ghi đè nhau.

Dù vậy, việc giải binding đầy đủ vẫn phức tạp khi có generic superclass nhiều tầng, wildcard, capture, interface, owner type và biến kiểu tự tham chiếu. Đừng dùng `getName()` rồi thay text trong `Type.getTypeName()`; hãy làm việc với cấu trúc `Type`.

## 11. Các bài thực hành nhỏ

**Bài 1.** Với:

```java
class Service<T> {
    T read() { return null; }
    <T> T convert(T value) { return value; }
    <U> T combine(U input) { return null; }
}
```

Hãy trả lời:

1. `Service.class.getTypeParameters()` có bao nhiêu phần tử?
2. `read.getTypeParameters()` có bao nhiêu phần tử?
3. `convert.getTypeParameters()` có bao nhiêu phần tử?
4. `combine.getGenericReturnType()` thuộc class hay method?
5. Hai chữ `T` của `Service<T>` và `convert<T>` có là một biến kiểu không?

**Đáp án:** lần lượt là **1, 0, 1, class, không**. `combine` tự khai báo `U`, nhưng return type `T` là biến kiểu của class.

**Bài 2.** Với:

```java
class Numeric<T extends Number> {
    <T extends CharSequence> T text(T input) {
        return input;
    }
}
```

**Câu hỏi:** Dùng API nào để lấy biến kiểu của method và Reflection phải dùng parameter class nào để tra cứu `text`?

**Đáp án:** `Numeric.class.getDeclaredMethod("text", CharSequence.class)` rồi gọi `method.getTypeParameters()`. Dùng `CharSequence.class` vì bound của method-T là CharSequence; bound class-T là Number không liên quan đến chữ ký method này.

**Bài 3.** Cho `class Child extends Parent<String>`, trong đó `Parent<T>` có `T read()` và `<T> T echo(T input)`. Có thể thay cả hai T bằng String trong generic resolver không?

**Đáp án:** Không. Binding trong quan hệ kế thừa chỉ áp dụng **Parent.class-T**. `echo` có method-T độc lập nên không được suy ra rằng nó bằng String.

**Bài 4.** Nếu `Method.getTypeParameters()` trả mảng rỗng, có kết luận method không sử dụng generic trong return/parameter được không?

**Đáp án:** Không. Method có thể dùng biến kiểu khai báo ở class, như `T read()`, hoặc tham số `List<T>`. Kiểm tra `getGenericReturnType()` và `getGenericParameterTypes()`.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Hai biến kiểu đều tên `T` có nhất thiết cùng identity không?

**Đáp án:** Không. Phải xét nơi khai báo `GenericDeclaration`. Class-T và method-T là hai biến độc lập.

**Câu 2.** API nào trả các biến kiểu do method tự khai báo, như `<U>`?

**Đáp án:** `Method.getTypeParameters()`. `getGenericParameterTypes()` lại trả kiểu của các tham số nhận vào.

**Câu 3.** Phương thức `<T extends CharSequence> T echo(T x)` có `Method.getReturnType()` và `getGenericReturnType()` khác nhau ra sao?

**Đáp án:** `getReturnType()` trả `CharSequence.class` sau erasure; `getGenericReturnType()` trả `TypeVariable` thuộc method (tên T).

**Câu 4.** Vì sao biến kiểu của `<T>` ở phương thức static không phụ thuộc type argument của generic class?

**Đáp án:** Nó được method khai báo riêng; static context không sử dụng biến kiểu instance của generic class.

**Câu 5.** `Box<Integer>` có tự khiến `<T extends CharSequence> T echo(T)` trả Integer không?

**Đáp án:** Không. Method-T shadow class-T, bound của method-T là CharSequence nên có thể trả String.

**Câu 6.** `TypeVariable.getGenericDeclaration()` trả về những đối tượng nào?

**Đáp án:** Một declaration như `Class`, `Method` hoặc `Constructor` tương ứng với nơi biến kiểu được khai báo.

**Câu 7.** Khi phân giải `Parent<T>` trong `Child extends Parent<String>`, vì sao nên dùng khóa `TypeVariable<?>` thay vì chuỗi `"T"`?

**Đáp án:** Để binding String chỉ áp dụng Parent-T và không ghi đè các biến kiểu tên T ở method hay các class khác.

**Ghi nhớ:** Biến kiểu của **lớp** và biến kiểu của **phương thức** là các khai báo độc lập. Cùng tên `T` không tạo cùng identity; Reflection cho biết owner bằng `TypeVariable.getGenericDeclaration()`. Dùng `Class.getTypeParameters()` để xem biến kiểu lớp, `Method.getTypeParameters()` để xem biến kiểu method, và các API `getGeneric...` để giữ thông tin trước erasure. Khi giải generic, luôn gắn binding với **đúng declaration**, đặc biệt khi method khai báo một biến `T` mới che khuất `T` của lớp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-resolution-007">Giới hạn kiểu chưa được ràng buộc và trường hợp raw type</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn kiểu chưa được ràng buộc và trường hợp raw type trong Java Reflection

## 1. Đặt đúng vấn đề: metadata generic không luôn cung cấp một kiểu cụ thể

Java hỗ trợ các kiểu generic như `Box<String>`, `Box<T>` hoặc `Box<?>`, nhưng Reflection không có khả năng tự khôi phục mọi tham số kiểu của object ở runtime. Khi phân tích một lớp generic, field, method hoặc quan hệ kế thừa, ta cần phân biệt rõ ba kết quả:

1. **Đã ràng buộc cụ thể:** ví dụ `class StringBox extends Box<String>`. Khi hỏi generic superclass của `StringBox.class`, chữ ký khai báo vẫn chứa đối số `String`, nên Reflection có thể trả `ParameterizedType` với đối số `String.class`.
2. **Còn biến kiểu chưa được giải quyết:** ví dụ `class OpenBox<U> extends Box<U>`. Generic superclass có dạng `Box<U>`; đối số `U` là `TypeVariable`, **không phải** kiểu dữ liệu cụ thể của một object `OpenBox` đang giữ.
3. **Kế thừa qua raw type:** ví dụ `class RawBox extends Box`. Khai báo `extends Box` không ghi danh sách tham số kiểu, nên `getGenericSuperclass()` có thể chỉ trả `Class` đại diện `Box`. Không có `getActualTypeArguments()` để lấy một `T` bị bỏ trống.

Trong ứng dụng dùng serializer, mapper, dependency injection hoặc thư viện generic, **không được âm thầm diễn giải trường hợp chưa biết thành một kiểu cụ thể tự đoán**. Upper bound như `Object` hoặc `Number` chỉ đặt giới hạn cho biến kiểu; nó không phải giá trị ràng buộc thực tế.

## 2. Các loại `Type` cần nhận diện

Trong `java.lang.reflect`, `Type` là abstraction chung. Kết quả đọc một chữ ký generic có thể là:

| Loại Reflection | Ví dụ trong khai báo | Ý nghĩa |
| --- | --- | --- |
| `Class<?>` | `String` hoặc superclass dùng raw `Box` | Kiểu lớp cụ thể, không mang danh sách đối số generic ở chính giá trị `Class` |
| `ParameterizedType` | `Box<String>`, `Box<U>`, `List<?>` | Khai báo áp dụng kiểu generic kèm danh sách đối số; **đối số bên trong vẫn có thể chưa giải quyết được** |
| `TypeVariable<?>` | `T`, `U`, `N extends Number` | Biến kiểu do một class, method hoặc constructor khai báo |
| `WildcardType` | `?`, `? extends Number`, `? super Integer` | Đối số wildcard, giới hạn quan hệ kiểu chứ không chỉ một kiểu cụ thể |
| `GenericArrayType` | `T[]` trong khai báo generic | Kiểu mảng có component type không biểu diễn được chỉ bằng `Class<?>[]` |

`Class.getGenericSuperclass()` và `Class.getGenericInterfaces()` trả metadata quan hệ trực tiếp với superclass/superinterface; `Field.getGenericType()` và `Method.getGenericReturnType()` trả chữ ký generic của field/return type. Muốn xem các biến kiểu **được khai báo** trên class hoặc executable, dùng `getTypeParameters()`.

Lưu ý: `getTypeParameters()` cung cấp **danh sách biến khai báo**, không phải các đối số đã được thay thế cho một object runtime. Ví dụ `Box.class.getTypeParameters()[0]` là `T` ngay cả khi một instance nào đó từng được tạo bằng `new Box<String>(...)`.

## 3. `TypeVariable`: khi `T` hoặc `U` chưa được ràng buộc

Ví dụ:

```java
class Box<T> {
    T value;
}

class OpenBox<U> extends Box<U> {}
```

Đây là một **đoạn khai báo minh họa**, không phải file Java hoàn chỉnh.

Khi phân tích `OpenBox.class.getGenericSuperclass()`, ta có thể nhận `ParameterizedType` mô tả `Box<U>`. Nhưng đối số thực của nó là `TypeVariable` đại diện `U`; không có cơ sở để nói `U = String`, `U = Integer` hoặc `U = Object` chỉ từ `OpenBox.class`.

`TypeVariable` cung cấp:

- `getName()`: tên biến, chẳng hạn `U`. **Tên đơn lẻ không đủ để định danh**; hai class khác nhau có thể đều khai báo `T`.
- `getGenericDeclaration()`: class, method hoặc constructor **đã khai báo biến**. Dùng thông tin này (cùng thứ tự biến) khi tạo ánh xạ ràng buộc.
- `getBounds()`: mảng upper bounds theo khai báo; có thể chứa `Class`, `ParameterizedType` hoặc `TypeVariable`.
- `getAnnotatedBounds()`: dùng khi cần cả metadata annotation gắn lên vị trí bound.

### 3.1. Không khai báo bound: `Object` là upper bound mặc định

Với `class Box<T>`, `T.getBounds()` trả mảng có upper bound `java.lang.Object`. Điều đó có nghĩa `T` có thể được thay bởi **nhiều kiểu tham chiếu hợp lệ**; không có nghĩa mọi `Box<T>` thực tế là `Box<Object>`.

Vì vậy, khi một resolver thấy `TypeVariable T` mà không có ánh xạ `T -> kiểu cụ thể`, câu trả lời đúng là **UNRESOLVED `T` (upper bound: `Object`)**. Chọn `Object.class` làm *erasure* đôi khi hữu ích cho thao tác trên bytecode, nhưng **không được gọi đó là đối số kiểu thực tế**.

### 3.2. Bound tường minh vẫn không phải một đối số đã được ràng buộc

```java
class NumericBox<N extends Number & Comparable<N>> {}
```

Ở đây `N.getBounds()` có hai phần, lần lượt là `Number` và `Comparable<N>`. Biết các upper bounds giúp kiểm tra giới hạn và erasure, nhưng vẫn chưa thể chọn giữa `Integer`, `Double` hoặc một kiểu phù hợp khác.

Theo quy tắc type erasure của Java, erasure của một type variable lấy theo **leftmost bound**: `T` không bound tường minh có erasure `Object`, còn `N extends Number & Comparable<N>` có erasure `Number`. Erasure là kiểu được compiler sử dụng cho biểu diễn JVM sau khi xóa thông tin generic, **khác với** việc phân giải một đối số kiểu concrete.

### 3.3. Những điều không thể suy ra từ một instance thông thường

`new Box<String>("x").getClass()` và `new Box<Integer>(123).getClass()` là cùng `Class` `Box` runtime (trong cùng class loader). Do đó, đối tượng `Class` tự nó không lưu `String` hay `Integer` của từng instance generic. Quan sát giá trị hiện tại cũng không giải quyết chắc chắn kiểu generic: object có thể chứa `null`, interface/supertype hoặc được truy cập theo nhiều đường generic.

Nếu chương trình cần kiểu tại runtime, có thể truyền `Class<T>`/`Type` token có chủ đích, dùng subclass đóng kiểu trong chữ ký kế thừa hoặc metadata khai báo tương ứng. Không tự đoán từ tên biến hay dữ liệu bất kỳ.

## 4. Raw type: không có danh sách type arguments

Raw type là việc sử dụng một generic type **không kèm type arguments**:

```java
class Box<T> {}
class RawBox extends Box {}          // extends kiểu raw Box
class StringBox extends Box<String> {}
```

Đây là **mảnh khai báo minh họa**. `Box` vốn là một generic class, nhưng ở biểu thức `extends Box` không hề cung cấp `<...>`; đó là sử dụng raw type để tương thích với mã Java cũ.

Phân biệt ba câu sau:

- `Box<String>`: type argument rõ ràng là `String`.
- `Box<?>`: **parameterized type có wildcard**, vẫn có danh sách type arguments.
- `Box`: **raw type**, không có danh sách type arguments.

Đừng coi `Box` raw tương đương `Box<Object>` hay `Box<?>`. Cả ba biểu thức có quan hệ với cùng generic declaration nhưng khác nhau về kiểm tra kiểu ở compile time, metadata generic và khả năng phát sinh unchecked operations.

Theo JLS, supertypes của raw type là **erasure** của các supertype tương ứng. Các lời gọi member qua raw type có thể đưa tới cảnh báo `unchecked` khi kiểu field/parameter đã mất thông tin generic. Việc chương trình có thể biên dịch không đồng nghĩa đảm bảo loại trừ `ClassCastException`.

### 4.1. Reflection của raw superclass

```java
Type parent = RawBox.class.getGenericSuperclass();

if (parent instanceof Class<?> rawClass) {
    System.out.println(rawClass.getSimpleName());
}
```

Trong ví dụ trên, `parent` là `Class` tương ứng `Box`, không phải một `ParameterizedType` với đối số giả `Object`.

Lỗi phổ biến:

```java
ParameterizedType p =
        (ParameterizedType) RawBox.class.getGenericSuperclass();
```

Câu lệnh này có thể ném `ClassCastException` vì kiểu thực của `Type` là `Class`. Luôn kiểm tra bằng `instanceof` trước khi ép kiểu. Nếu kết quả là `Class` của generic declaration được dùng raw, trạng thái phân giải nên báo **RAW / UNKNOWN TYPE ARGUMENT**, không báo đã giải được một đối số cụ thể.

## 5. Ví dụ Java 17+ hoàn chỉnh và kết quả đã kiểm chứng

Tạo file **`GenericLimitsDemo.java`**:

```java
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.stream.Collectors;

public class GenericLimitsDemo {
    static class Box<T> {
        private final T value;
        Box(T value) { this.value = value; }
        T get() { return value; }
    }

    static class StringBox extends Box<String> {
        StringBox() { super("hello"); }
    }

    static class OpenBox<U> extends Box<U> {
        OpenBox(U value) { super(value); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static class RawBox extends Box {
        RawBox() { super(42); }
    }

    static class BoundedBox<N extends Number & Comparable<N>>
            extends Box<N> {
        BoundedBox(N value) { super(value); }
    }

    static class IntegerBox extends BoundedBox<Integer> {
        IntegerBox() { super(7); }
    }

    static class Lists {
        java.util.List<?> any;
        java.util.List<String> strings;
    }

    static String kind(Type t) {
        if (t instanceof Class<?>) return "Class";
        if (t instanceof TypeVariable<?>) return "TypeVariable";
        if (t instanceof ParameterizedType) return "ParameterizedType";
        if (t instanceof WildcardType) return "WildcardType";
        if (t instanceof GenericArrayType) return "GenericArrayType";
        return "Other";
    }

    static void inspect(Class<?> type) {
        Type parent = type.getGenericSuperclass();
        System.out.println(type.getSimpleName() + " -> " + parent.getTypeName());

        if (parent instanceof ParameterizedType p) {
            Type arg = p.getActualTypeArguments()[0];
            System.out.println(
                    "  argument=" + arg.getTypeName() + ", kind=" + kind(arg)
            );
        } else if (parent instanceof Class<?>) {
            System.out.println("  raw superclass: no actual type argument");
        }
    }

    public static void main(String[] args) throws Exception {
        inspect(StringBox.class);
        inspect(OpenBox.class);
        inspect(RawBox.class);
        inspect(IntegerBox.class);

        TypeVariable<?> t = Box.class.getTypeParameters()[0];
        TypeVariable<?> u = OpenBox.class.getTypeParameters()[0];
        TypeVariable<?> n = BoundedBox.class.getTypeParameters()[0];

        System.out.println("T bounds=" + Arrays.stream(t.getBounds())
                .map(Type::getTypeName).collect(Collectors.joining(" & ")));
        System.out.println("U bounds=" + Arrays.stream(u.getBounds())
                .map(Type::getTypeName).collect(Collectors.joining(" & ")));
        System.out.println("N bounds=" + Arrays.stream(n.getBounds())
                .map(Type::getTypeName).collect(Collectors.joining(" & ")));

        Field value = Box.class.getDeclaredField("value");
        System.out.println("Box.value generic=" + value.getGenericType().getTypeName()
                + ", erased=" + value.getType().getTypeName());

        Field any = Lists.class.getDeclaredField("any");
        Type wildcard = ((ParameterizedType) any.getGenericType())
                .getActualTypeArguments()[0];
        System.out.println("List<?> argument=" + wildcard.getTypeName()
                + ", kind=" + kind(wildcard));

        System.out.println("Same runtime class="
                + (new Box<>("x").getClass() == new Box<>(123).getClass()));

        @SuppressWarnings({"rawtypes", "unchecked"})
        Box<Integer> unsafe = new Box("not an integer");
        try {
            Integer result = unsafe.get();
            System.out.println(result);
        } catch (ClassCastException e) {
            System.out.println("Raw assignment: ClassCastException on read");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 GenericLimitsDemo.java
java GenericLimitsDemo
```

**Stdout trên JDK 21 với `--release 17`:**

```text
StringBox -> GenericLimitsDemo$Box<java.lang.String>
  argument=java.lang.String, kind=Class
OpenBox -> GenericLimitsDemo$Box<U>
  argument=U, kind=TypeVariable
RawBox -> GenericLimitsDemo$Box
  raw superclass: no actual type argument
IntegerBox -> GenericLimitsDemo$BoundedBox<java.lang.Integer>
  argument=java.lang.Integer, kind=Class
T bounds=java.lang.Object
U bounds=java.lang.Object
N bounds=java.lang.Number & java.lang.Comparable<N>
Box.value generic=T, erased=java.lang.Object
List<?> argument=?, kind=WildcardType
Same runtime class=true
Raw assignment: ClassCastException on read
```

**Phân tích theo từng phần:**

1. `StringBox` có generic superclass `Box<String>`. `getActualTypeArguments()[0]` là một `Class` `String.class`; có thể kết luận đối số của **quan hệ kế thừa được khai báo** là `String`.
2. `OpenBox<U>` cũng trả `ParameterizedType`, nhưng đối số là `TypeVariable U`. Sự tồn tại của `ParameterizedType` **không đảm bảo** mọi đối số đã ràng buộc concrete.
3. `RawBox` trả `Class` vì lớp cha được khai báo dưới dạng raw. Nó không có danh sách type argument để đọc.
4. `IntegerBox` ràng buộc `BoundedBox<Integer>`. Nếu còn đi tiếp qua `BoundedBox<N> extends Box<N>`, muốn kết luận `Box<Integer>` phải **truyền ánh xạ `N -> Integer`** qua từng cấp. Chỉ đọc `BoundedBox.class.getGenericSuperclass()` sẽ thấy `N`, chưa phải `Integer`.
5. `Box.value` có `getGenericType() == T` (về ý nghĩa metadata), còn `getType() == Object.class` vì `T` bị erasure về `Object`. `getType()` không cung cấp actual argument của một instance cụ thể.
6. `List<?>` cho `WildcardType`, không phải raw. `?` là đối số chưa biết có upper bound mặc định `Object`; không thể tùy tiện đổi nó thành actual `Object`.
7. Hai `Box` có đối số compile-time khác nhau dùng cùng runtime class. Cuối cùng, phép gán qua raw type bỏ qua kiểm tra generic khiến `Integer result = unsafe.get()` phát sinh `ClassCastException` khi bytecode ép giá trị `String` về `Integer`.

`@SuppressWarnings({"rawtypes", "unchecked"})` trong ví dụ chỉ được đặt sát nơi **cố ý minh họa rủi ro của raw type**. Nó không biến mã mất an toàn thành an toàn; trong code thực tế nên tránh hoặc cô lập tương tác legacy càng nhỏ càng tốt.

## 6. Vì sao `getBounds()` không thay thế được phân giải kiểu?

Một `TypeVariable` có thể vẫn chưa ràng buộc dù có một hoặc nhiều upper bounds:

| Khai báo | `getBounds()` | Có biết actual argument chưa? |
| --- | --- | --- |
| `class Box<T>` | `Object` | **Chưa** |
| `class Numeric<N extends Number>` | `Number` | **Chưa** |
| `class ComparableBox<N extends Number & Comparable<N>>` | `Number`, `Comparable<N>` | **Chưa** |
| `class StringBox extends Box<String>` | `T` vẫn tồn tại trên declaration `Box` | **Có đối số `String` trong quan hệ kế thừa của `StringBox`** |
| `class OpenBox<U> extends Box<U>` | `U` mặc định bound `Object` | **Chưa**, trừ khi có ánh xạ từ tầng bao ngoài |

**Upper bound** được dùng cho ràng buộc kiểu và erasure. **Actual type argument** là giá trị được đưa vào khi một kiểu parameterized được khai báo/sử dụng. Không có quy tắc “nếu không giải được `T` thì đi lấy bound đầu tiên làm `T`”.

Ví dụ `N extends Number` cho biết `N` là một subtype phù hợp của `Number`. Nó **không** cho phép resolver khẳng định `N = Number`; `Integer`, `Double` và nhiều kiểu khác có thể thỏa mãn bound.

## 7. Phân giải qua nhiều tầng cần thế biến, không chỉ lặp `getGenericSuperclass()`

Xét đoạn khai báo:

```java
class Base<T> {}

class Mid<U> extends Base<java.util.List<U>> {}

class Concrete extends Mid<String> {}
```

Đây là **đoạn code minh họa**, không phải một file Java độc lập.

- `Concrete.class.getGenericSuperclass()` biểu diễn `Mid<String>`, nên có ánh xạ `Mid.U -> String`.
- `Mid.class.getGenericSuperclass()` biểu diễn `Base<List<U>>`. Đối số là một `ParameterizedType` `List<U>`, trong đó `U` vẫn là `TypeVariable`.
- Muốn xác định quan hệ từ `Concrete` tới `Base`, resolver phải thế `U -> String` **bên trong** `List<U>`, để được `Base<List<String>>`.

Một thuật toán generic resolver thực tế cần:

1. Đọc các khai báo `TypeVariable` theo **`getTypeParameters()` của đúng declaring class**.
2. Đọc các actual arguments của `ParameterizedType` trên cạnh kế thừa hiện tại.
3. Ghi ánh xạ từ biến khai báo sang `Type` tương ứng, phân biệt biến cùng tên ở các declaration khác nhau.
4. Áp dụng ánh xạ đệ quy vào `ParameterizedType`, `GenericArrayType`, `WildcardType` và `TypeVariable`, tránh vòng lặp khi bound tự tham chiếu như `T extends Comparable<T>`.
5. Truyền ánh xạ đó khi tiếp tục duyệt superclass hoặc interface, đồng thời xử lý nhiều nhánh interface nếu liên quan.
6. Khi gặp raw edge hoặc biến vẫn chưa có ánh xạ, **báo chưa giải được** và giữ thông tin bounds/erasure riêng; không điền `Object` hoặc `Class` bất kỳ cho đủ chỗ.

Trong ví dụ `Concrete -> Mid<String> -> Base<List<U>>`, một resolver chỉ nhìn từng cạnh độc lập sẽ bỏ lỡ phép thế `U -> String`. Ngược lại, một resolver “đoán `U` bằng bound `Object`” sẽ kết luận sai `List<Object>` dù thông tin `String` tồn tại trên cạnh dưới.

## 8. Raw type nằm giữa chuỗi kế thừa

Ví dụ:

```java
class Store<T> {}
@SuppressWarnings("rawtypes")
class LegacyStore extends Store {}
class SpecializedStore extends LegacyStore {}
```

Đây là **mảnh khai báo minh họa**.

- `LegacyStore.class.getGenericSuperclass()` đại diện raw `Store` và không cung cấp `T`.
- `SpecializedStore.class.getGenericSuperclass()` cho `LegacyStore.class` (một superclass thông thường).
- Khi đi ngược lên `Store<T>`, không thể tạo ra một concrete argument cho `Store.T` từ chuỗi này. Kết quả hợp lệ là **không biết `T` vì đã qua raw edge**.

Cần phân biệt hai mức phân tích:

- **Erasure:** có thể xác định lớp runtime cơ sở, chẳng hạn `Store.class`.
- **Generic argument resolution:** không thể khôi phục một `T` đã không được ghi trong chữ ký raw.

Nếu framework không thể xử lý đầu vào raw, hãy yêu cầu khai báo generic rõ ràng, `Type` token hoặc metadata cấu hình hợp lệ; tránh tự coi raw `Store` là `Store<Object>`.

## 9. Wildcard chưa ràng buộc không phải raw type

Xem hai field:

```java
java.util.List<?> unknownElements;
java.util.List rawElements;
```

Đây là **mảnh khai báo field**; đặt chúng trong một class thì mới biên dịch như một chương trình.

Với `unknownElements`:

- `getGenericType()` là `ParameterizedType` mô tả `List<?>`.
- `getActualTypeArguments()[0]` là `WildcardType`.
- `getUpperBounds()` của wildcard không bound tường minh có `Object`; `getLowerBounds()` rỗng.
- `List<?>` giữ được một phần kiểm tra kiểu tại compile time: bạn không thể tùy tiện `add("text")` vào danh sách đó (ngoại lệ `null`).

Với `rawElements`:

- `getGenericType()` thường là `Class` `List.class`, không có actual arguments.
- Raw type cho phép những thao tác có nguy cơ mất an toàn về kiểu, có thể dẫn tới unchecked warning và lỗi muộn.

Tương tự, `? extends Number` là một wildcard **có upper bound** chứ không phải một biến `N extends Number`; `? super Integer` chứa **lower bound**. Cả wildcard và type variable đều không nên bị ép thành `Class<?> concrete` chỉ vì có bound.

## 10. Cách thiết kế API trả kết quả phân giải đáng tin

Khi xây dựng thư viện Reflection, nên trả về **trạng thái phân giải có giải thích**, thay vì một `Class<?>` bịa ra:

| Trạng thái đề xuất | Ví dụ | Hành vi hợp lý |
| --- | --- | --- |
| `RESOLVED` | `Box<String>` trên `StringBox` | Trả `Type` `String.class` |
| `UNRESOLVED_VARIABLE` | `Box<U>` trên `OpenBox<U>` khi không có ánh xạ `U` | Trả `TypeVariable U`, declaration, bounds |
| `RAW` | `extends Box` | Báo thiếu argument ngay trong cạnh kế thừa raw |
| `WILDCARD` | `List<?>`, `List<? extends Number>` | Giữ `WildcardType` và các bounds |
| `PARTIALLY_RESOLVED` | `List<U>` khi `U` chưa biết | Giữ cấu trúc `ParameterizedType` và vị trí biến chưa biết |

Các nhãn trạng thái trên là **gợi ý thiết kế ứng dụng**, không phải enum có sẵn trong JDK.

Nếu cần một kiểu runtime cho serialization/validation, nhận `Type` hoặc một *type token* từ nơi gọi. Ví dụ chữ ký API có thể là `decode(String json, Type targetType)` thay vì chỉ `decode(String json, Class<?> targetClass)`, vì `Class<List>` không biểu diễn được `List<String>`. Dù có `Type` token, vẫn cần kiểm tra các `TypeVariable` chưa ràng buộc và các trường hợp raw/wildcard theo hợp đồng API.

## 11. Sai lầm thường gặp

| Sai lầm | Vì sao sai và cách sửa |
| --- | --- |
| Ép `getGenericSuperclass()` về `ParameterizedType` vô điều kiện | Raw superclass có thể trả `Class`; kiểm tra `instanceof` |
| Thấy `ParameterizedType` là kết luận đã có concrete type | Đối số bên trong có thể là `TypeVariable`, `WildcardType`, `ParameterizedType` lồng nhau |
| Dùng bound `Object` làm actual argument khi không giải được `T` | Bound/erasure không phải binding; giữ trạng thái unresolved |
| Gộp mọi `T` cùng tên thành một biến | Mỗi biến còn có `GenericDeclaration` riêng; dùng declaration và vị trí |
| Dựa vào `obj.getClass()` để lấy `Box<String>` của `new Box<String>()` | Type erasure khiến class runtime chung cho các instantiation |
| Coi raw `Box` như `Box<Object>` | Raw type mất type argument và có hành vi compile-time khác |
| Coi `List<?>` là raw `List` | `List<?>` giữ wildcard metadata và kiểm tra kiểu khác raw |
| Chỉ đi lên superclass một cấp khi có nhiều tầng generic | Phải truyền ánh xạ biến giữa các cạnh kế thừa |
| Không thế biến đệ quy bên trong `List<U>` | Bỏ sót concrete `List<String>` qua nhiều tầng |
| Bỏ qua cảnh báo `unchecked` ở raw assignment | Có thể phát sinh `ClassCastException` ở lúc đọc/sử dụng |
| Cố kiểm tra `obj instanceof T` với type variable chưa ràng buộc | Không hợp lệ theo hạn chế reifiable type; cần thiết kế kiểm tra kiểu khác |

## 12. Tự kiểm tra

1. Tại sao `OpenBox<String>` tạo bằng `new OpenBox<>("x")` không khiến `OpenBox.class.getGenericSuperclass()` trở thành `Box<String>`?
2. `TypeVariable T` không khai báo bound có `getBounds()` thế nào? Giá trị đó có phải actual argument không?
3. `class RawChild extends Box {}` làm `RawChild.class.getGenericSuperclass()` trả loại `Type` nào? Có thể gọi `getActualTypeArguments()` trực tiếp không?
4. Nếu `N extends Number & Comparable<N>`, erasure của `N` là gì? Từ đó có biết `N = Number` không?
5. `List<?>` và raw `List` khác nhau thế nào ở metadata?
6. Với `Concrete extends Mid<String>` và `Mid<U> extends Base<List<U>>`, cần thao tác nào để tìm `Base<List<String>>`?
7. Vì sao việc gán qua raw `Box` có thể dẫn tới `ClassCastException` dù chương trình đã biên dịch?

**Đáp án:** (1) object dùng runtime class `OpenBox`, chữ ký của declaration vẫn là `Box<U>`; (2) bound `Object`, không phải actual argument; (3) `Class` của `Box`, không có `getActualTypeArguments()`; (4) `Number`, vẫn không suy ra binding concrete; (5) wildcard vs thiếu argument raw; (6) truyền ánh xạ `U -> String` qua quan hệ và thế đệ quy trong `List<U>`; (7) raw bypass kiểm tra generic, lỗi xuất hiện khi có cast tại nơi sử dụng.

## 13. Kết luận và tài liệu tham khảo

Trong phân giải generic bằng Reflection, **không có thông tin** và **kiểu `Object`** là hai trạng thái khác nhau. Đối với `TypeVariable` chưa ràng buộc, giữ biến cùng declaration và bounds; đối với raw type, ghi nhận đã mất type arguments; đối với wildcard, giữ cấu trúc wildcard và giới hạn; đối với chuỗi kế thừa, thế biến theo từng tầng nếu có ánh xạ hợp lệ.

Tài liệu chuẩn:

- [Java SE 21 — `TypeVariable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/TypeVariable.html): `getBounds()`, `getGenericDeclaration()`.
- [Java SE 21 — `ParameterizedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/ParameterizedType.html): `getActualTypeArguments()` và `getRawType()`.
- [Java SE 21 — `Class.getGenericSuperclass()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getGenericSuperclass()): quan hệ superclass trong metadata generic.
- [Java Language Specification §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): quy tắc erasure.
- [Java Language Specification §4.8 — Raw Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.8): ý nghĩa và rủi ro raw types.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-resolution-008">Mẫu type token và anonymous subclass</a>

<details>
<summary>Xem chi tiết</summary>

# Mẫu type token và anonymous subclass trong Java Reflection

## 1. Vì sao cần type token?

Trong Java, kiểu generic giúp trình biên dịch kiểm tra tính an toàn kiểu nhưng phần lớn tham số kiểu bị **type erasure** khi chạy. Chẳng hạn, cả `List<String>` và `List<Integer>` cùng có raw class là `java.util.List`. Biểu thức `List<String>.class` không hợp lệ; `List.class` chỉ biểu diễn raw type.

Tuy vậy, Java có thể lưu **chữ ký generic được khai báo** trong metadata của lớp, field, method hoặc superclass. Reflection có thể đọc chữ ký đó dưới dạng `java.lang.reflect.Type`. Mẫu **type token** sử dụng một đối tượng đại diện cho mô tả kiểu, để truyền kiểu phức tạp như `List<String>` hoặc `Map<String, List<Integer>>` tới một API cần thông tin kiểu.

Phân biệt hai trường hợp:

- **Class token**: `String.class`, `List.class` hoặc `Class<T>` phù hợp khi chỉ cần danh tính lớp/raw type.
- **Generic type token**: `Type` mô tả cả cấu trúc generic được khai báo, ví dụ `List<String>`, kiểu lồng nhau, wildcard hoặc mảng generic. Không phải mọi `Type` đều là `Class<?>`.

Type token **không đảo ngược type erasure của một đối tượng bất kỳ**. Nó đọc thông tin generic còn được lưu ở một vị trí khai báo cụ thể.

## 2. Anonymous subclass giúp giữ đối số generic như thế nào?

Đây là cách dùng thường gặp:

~~~java
Type type = new TypeRef<List<String>>() {}.type();
~~~

Dấu `{}` tạo ra **anonymous subclass** của `TypeRef<List<String>>`. Trình biên dịch lưu chữ ký generic của superclass trên lớp ẩn danh. Khi đối tượng được tạo:

1. `getClass()` trả về `Class` của anonymous subclass, **không phải** chính `TypeRef.class`.
2. `getClass().getGenericSuperclass()` đọc kiểu superclass đã khai báo, tức `TypeRef<List<String>>`.
3. Giá trị nhận được là một `ParameterizedType`; `getActualTypeArguments()[0]` chính là `List<String>`, vẫn ở dạng `Type`.
4. Có thể giữ `Type` này để đưa cho thư viện xử lý dữ liệu hoặc để phân tích tiếp bằng Reflection.

Cú pháp `new TypeRef<List<String>>()` **không có** `{}` sẽ không tạo được đối tượng trong ví dụ bên dưới vì `TypeRef` là lớp `abstract`. Quan trọng hơn, anonymous subclass là nơi có **chữ ký generic cụ thể** để đọc. Mẫu này đôi khi được gọi là *super type token*.

## 3. Tự xây dựng một TypeRef đơn giản, chạy được trên Java 17+

Lưu mã sau thành `TypeTokenDemo.java`:

~~~java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class TypeTokenDemo {
    static abstract class TypeRef<T> {
        private final Type type;

        protected TypeRef() {
            Type parent = getClass().getGenericSuperclass();
            if (!(parent instanceof ParameterizedType parameterized)
                    || parameterized.getRawType() != TypeRef.class) {
                throw new IllegalStateException(
                        "Phải kế thừa TypeRef<T> với đối số kiểu cụ thể");
            }
            type = parameterized.getActualTypeArguments()[0];
        }

        public final Type type() {
            return type;
        }
    }

    static final class Example {
        List<String> names;
    }

    public static void main(String[] args) throws NoSuchFieldException {
        Type a = new TypeRef<List<String>>() {}.type();
        Type b = new TypeRef<Map<String, List<Integer>>>() {}.type();
        Type c = new TypeRef<List<Integer>>() {}.type();

        System.out.println(a.getTypeName());
        System.out.println(b.getTypeName());
        System.out.println(a.equals(c));

        if (b instanceof ParameterizedType p) {
            System.out.println(p.getRawType().getTypeName());
            for (Type arg : p.getActualTypeArguments()) {
                System.out.println(arg.getTypeName());
            }
        }

        Type fieldType = Example.class.getDeclaredField("names")
                .getGenericType();
        System.out.println(a.equals(fieldType));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 TypeTokenDemo.java
java TypeTokenDemo
~~~

Stdout:

~~~text
java.util.List<java.lang.String>
java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>
false
java.util.Map
java.lang.String
java.util.List<java.lang.Integer>
true
~~~

Giải thích các kết quả:

- `a` và `c` có raw type giống nhau (`List`) nhưng đối số generic khác nhau (`String` và `Integer`), nên phép so sánh `Type.equals` cho kết quả `false`.
- `b` là `ParameterizedType` cho `Map<String, List<Integer>>`. `getRawType()` trả về `Map.class`; hai phần tử `getActualTypeArguments()` lần lượt là `String.class` và một `ParameterizedType` cho `List<Integer>`.
- Field `names` khai báo trực tiếp `List<String>` nên `Field.getGenericType()` cho một mô tả kiểu tương đương `a`; kết quả so sánh là `true`. Không cần tạo instance của `Example` để đọc khai báo field.
- Constructor của `TypeRef` chặn trường hợp kế thừa trực tiếp bằng raw type hoặc không khai báo `TypeRef<T>` dưới dạng superclass được tham số hóa. Triển khai đơn giản này **chỉ hỗ trợ lớp kế thừa trực tiếp `TypeRef<...>`**, không tự lần theo nhiều tầng kế thừa.

Chữ ký generic được đọc từ cấu trúc lớp đã biên dịch; thao tác này **không kiểm tra nội dung thực tế** của một `List` tại runtime. Mẫu `TypeRef` cũng không tự giải mã JSON, chuyển đổi dữ liệu hay xác minh từng phần tử.

## 4. Đọc cây Type đúng cách

`Type` là một interface chung. Kết quả đọc được có thể thuộc các loại sau:

| Loại Reflection | Ví dụ | Ý nghĩa và API chính |
| --- | --- | --- |
| `Class<?>` | `String.class`, `List.class`, `String[].class` | Lớp hoặc mảng có kiểu thành phần reifiable; `getTypeName()` |
| `ParameterizedType` | `List<String>`, `Map<String, Integer>` | Raw type, owner type và đối số kiểu; `getRawType()`, `getOwnerType()`, `getActualTypeArguments()` |
| `TypeVariable<?>` | `T` trong `class Box<T>` | Biến kiểu từ khai báo; `getName()`, `getBounds()`, `getGenericDeclaration()` |
| `WildcardType` | `? extends Number`, `? super Integer` | Cận trên/dưới; `getUpperBounds()`, `getLowerBounds()` |
| `GenericArrayType` | `List<String>[]`, `T[]` | Mảng có kiểu thành phần không reifiable; `getGenericComponentType()` |

Ví dụ `new TypeRef<List<? extends Number>>() {}.type()` có nút ngoài là `ParameterizedType` cho `List`; đối số kiểu của nút này là `WildcardType`. Với `new TypeRef<List<String>[]>() {}.type()`, nút ngoài thường là `GenericArrayType` có component là `ParameterizedType` cho `List<String>`.

Muốn phân tích chính xác generic lồng nhau, cần duyệt đệ quy theo **loại nút**; không ép tất cả kết quả về `Class<?>`. Ví dụ kiểm tra một `Type` trước khi xử lý:

~~~java
if (type instanceof ParameterizedType p) {
    Type raw = p.getRawType();
    Type[] args = p.getActualTypeArguments();
    // Tiếp tục kiểm tra loại của từng phần tử args.
} else if (type instanceof Class<?> cls) {
    // Kiểu lớp, primitive hoặc mảng reifiable.
} else if (type instanceof TypeVariable<?> tv) {
    // Tên và bounds của biến kiểu; chưa chắc đã là kiểu cụ thể.
}
~~~

Đoạn trên là **mảnh mã minh họa** với biến `type` đã có sẵn; phần chương trình hoàn chỉnh ở mục 3 có thể chạy độc lập.

Các nguồn Reflection khác cũng cung cấp `Type`: `Field.getGenericType()`, `Method.getGenericReturnType()`, `Method.getGenericParameterTypes()`, `Class.getGenericInterfaces()` và `Class.getGenericSuperclass()`. Nếu dùng các phiên bản **không có “Generic”**, như `Field.getType()` hoặc `Method.getReturnType()`, kết quả chỉ là `Class<?>` sau erasure.

## 5. Áp dụng khi thiết kế API

Khi một hàm cần biết cấu trúc kiểu đích để phân tích dữ liệu, nên để nó nhận `Type` (hoặc một abstraction tương đương), thay vì chỉ nhận `Class<?>`. Cách gọi phía client có thể là:

~~~java
Type listOfNames = new TypeRef<List<String>>() {}.type();
Type index = new TypeRef<Map<String, List<Integer>>>() {}.type();
~~~

Hai biến trên là mô tả kiểu, thích hợp làm đầu vào cho API **có hỗ trợ `Type`**. API đó phải tự định nghĩa cách dùng `ParameterizedType`, wildcard và các kiểu lồng nhau; `TypeRef` chỉ làm nhiệm vụ giữ và cung cấp thông tin kiểu.

Đối với type đơn giản, `String.class` thường đủ và gọn hơn. Đối với kiểu khai báo trong một class cụ thể, đọc từ field/method bằng Reflection có thể phù hợp hơn việc tạo anonymous subclass mới. Nhiều thư viện có sẵn loại tương tự (chẳng hạn `TypeToken` hoặc `TypeReference`); tên API, khả năng hỗ trợ biến kiểu và cách tạo token có thể khác nhau giữa thư viện.

## 6. Những giới hạn và lỗi dễ gặp

### 6.1. Không thể suy ra generic của đối tượng thường

~~~java
List<String> names = new java.util.ArrayList<>();
System.out.println(names.getClass() == java.util.ArrayList.class);
~~~

Kết quả là `true`. Runtime class của instance là `ArrayList`, không mang riêng đối số `String` của biến `names`. `names.getClass().getGenericSuperclass()` cho biết khai báo superclass generic của **lớp ArrayList**, không chứng minh list đó có các phần tử `String`.

Không thể dùng `new TypeRef<>(...)` để “đọc ngược” kiểu generic đã mất từ một instance tùy ý nếu không có metadata khai báo bổ sung.

### 6.2. Bắt được biến kiểu chưa chắc bắt được kiểu cụ thể

Trong một phương thức generic:

~~~java
static <T> Type captureInGenericMethod() {
    return new TypeRef<List<T>>() {}.type();
}
~~~

`Type` tạo ra mô tả `List<T>`, trong đó `T` là một `TypeVariable` của khai báo phương thức. Dù gọi từ ngữ cảnh có đối số là `String`, không thể tự suy ra `List<String>` chỉ từ token này. Muốn kiểu cụ thể, hãy truyền vào một `Type` đã được xác định hoặc có cơ chế giải quyết biến kiểu từ ngữ cảnh phù hợp. Đoạn trên giả định `TypeRef` của mục 3 và import `Type`; đây là minh họa, không phải chương trình độc lập.

### 6.3. Nhầm `ParameterizedType` với `Class`

Ép kiểu `(Class<?>) new TypeRef<List<String>>() {}.type()` sẽ gây `ClassCastException` vì nút ngoài là `ParameterizedType`. Hãy kiểm tra bằng `instanceof` và chỉ lấy `getRawType()` khi thực sự cần raw type.

### 6.4. Raw type và lớp kế thừa nhiều tầng

Viết `new TypeRef() {}` làm mất đối số kiểu và bị constructor ví dụ phát hiện. Với `class Mid<T> extends TypeRef<T>` rồi `class Final extends Mid<String>`, `Final.class.getGenericSuperclass()` là `Mid<String>`, không trực tiếp là `TypeRef<String>`; triển khai mục 3 sẽ từ chối. Một phiên bản tổng quát muốn hỗ trợ phân cấp nhiều tầng phải lần theo hierarchy và giải quyết ánh xạ `TypeVariable` qua từng cấp, thay vì chỉ đọc `getActualTypeArguments()[0]` ở cấp đầu.

### 6.5. Đừng nhầm metadata với bảo đảm an toàn runtime

Token `List<String>` mô tả ý định kiểu. Nó không ngăn raw-type code thêm phần tử sai kiểu vào list và không biến kiểm tra `instanceof List<String>` thành hợp lệ. Việc validate/deserialize dữ liệu cần xử lý riêng, bao gồm kiểu con, null, wildcard và sai định dạng.

## 7. Ghi nhớ khi dùng Reflection generic

- Khi cần đối số generic **được khai báo trên superclass**, dùng `getGenericSuperclass()` và kiểm tra `ParameterizedType`.
- Với một type token cụ thể, dùng `new TypeRef<KiểuĐích>() {}`; cặp `{}` là phần tạo subclass có chữ ký để Reflection đọc.
- Sau khi lấy `Type`, duyệt các loại `Class`, `ParameterizedType`, `WildcardType`, `TypeVariable` và `GenericArrayType` theo nhu cầu. Không giả định mọi nút đều có class trực tiếp.
- Phân biệt **kiểu được khai báo** với **kiểu của dữ liệu khi đang chạy**; type erasure vẫn áp dụng.
- Chỉ sử dụng token với API hiểu `Type`. Kiểm thử riêng các trường hợp nested generic, wildcard, generic method và raw type nếu framework của bạn hỗ trợ chúng.

Bản chất của mẫu này là **đưa một biểu thức generic cụ thể vào chữ ký superclass của một anonymous subclass**, rồi lấy lại mô tả đó qua Reflection. Đây là cách truyền thông tin kiểu giàu cấu trúc cho API mà vẫn tuân theo cơ chế type erasure của Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-resolution-009">Không suy ra kiểu phần tử chỉ bằng việc xem các phần tử đang có</a>

<details>
<summary>Xem chi tiết</summary>

# Không suy ra kiểu phần tử chỉ bằng việc xem các phần tử đang có

Trong Java Reflection và **phân giải generic**, việc duyệt một `List`, `Set` hay `Collection` và quan sát `element.getClass()` chỉ cho biết **kiểu runtime của những đối tượng đã được nhìn thấy**. Nó **không chứng minh được** kiểu tham số generic mà tập hợp được khai báo, cũng không bảo đảm tập hợp sẽ chỉ nhận đúng những kiểu đó trong tương lai.

Ví dụ, một `List<Number>` hoàn toàn hợp lệ khi chứa `Integer` và `Double`. Việc thấy phần tử đầu là `Integer` không biến danh sách thành `List<Integer>`. Tương tự, một `List<Object>` có thể tình cờ chỉ chứa `String`; danh sách rỗng không cung cấp phần tử để quan sát; và phần tử `null` không có `getClass()`.

**Nguyên tắc:** muốn biết *kiểu khai báo* của generic, hãy tìm **thông tin kiểu từ khai báo/metadata**, hoặc yêu cầu caller truyền một **type contract** tường minh. Đừng đoán chữ ký generic chỉ từ sample dữ liệu.

## 1. Phân biệt bốn lớp thông tin về kiểu

| Câu hỏi | Ví dụ | Điều có thể xác định |
|---|---|---|
| **Kiểu runtime của collection object?** | `list.getClass()` | Chẳng hạn `java.util.ArrayList`; thường **không** có đối số kiểu `String`/`Number` |
| **Kiểu runtime của phần tử đang có?** | `list.get(0).getClass()` | Chẳng hạn `Integer.class`, nếu có phần tử khác `null` |
| **Kiểu generic được khai báo ở field/method?** | `Field.getGenericType()`, `Method.getGenericReturnType()` | Có thể là `List<Number>`, `List<? extends Number>`, `List<T>`... |
| **Hợp đồng kiểu mà ứng dụng muốn áp dụng?** | `Class<E>`, `Type`/type token, schema | Kiểu đích do API hoặc schema quy định, không nhất thiết có thể phục hồi từ giá trị hiện tại |

Hai kiểu đầu là **runtime observations**. Kiểu thứ ba là **thông tin khai báo có trong metadata** nếu nơi bạn đang xét lưu đủ chữ ký generic. Kiểu thứ tư là **yêu cầu do thiết kế ứng dụng cung cấp**.

Một danh sách cụ thể có thể thỏa đồng thời:

```java
List<Number> prices = new ArrayList<>();
prices.add(Integer.valueOf(7));
prices.add(Double.valueOf(2.5));
```

- Kiểu khai báo của biến `prices`: `List<Number>`.
- Kiểu runtime của object: `ArrayList`.
- Kiểu runtime của hai phần tử: `Integer` và `Double`.
- Không có mâu thuẫn: `Integer` và `Double` đều là lớp con của `Number`.

Đoạn mã trên là minh họa riêng, không có `main`. Nếu chỉ quan sát phần tử đầu, ta thậm chí còn **không thấy được** phần tử `Double` phía sau.

## 2. Tại sao xem phần tử không đủ để suy ra `List<T>`?

### 2.1. Có thể quan sát sai vì dữ liệu chưa đại diện

Một danh sách ban đầu chỉ chứa `Integer` nhưng sau đó được thêm `Double` nếu chữ ký của nó là `List<Number>`. Việc quan sát tập hợp tại một thời điểm chỉ phản ánh **trạng thái dữ liệu tại thời điểm đó**, không phản ánh mọi giá trị hợp lệ.

Tương tự, một `List<Object>` có thể chứa toàn `String` hôm nay, nhưng ngày mai chứa một `LocalDate`. Trong trường hợp này, tìm lớp chung gần nhất giữa các phần tử (nếu cố làm) cũng chỉ là một phép xấp xỉ trên **mẫu**, không phải **kiểu tham số được khai báo**.

### 2.2. Danh sách rỗng: không có thông tin runtime về phần tử

```java
List<String> names = new ArrayList<>();
List<Integer> ids = new ArrayList<>();
```

Cả `names` và `ids` đều rỗng. Việc gọi `getClass()` trên hai object này cho cùng lớp `ArrayList`; không thể lấy một phần tử để nhận diện generic argument. Thậm chí khi danh sách không rỗng, `getClass()` của container vẫn không phục hồi được tham số `String` hoặc `Integer` cho một `ArrayList` thông thường.

### 2.3. Phần tử `null` không có runtime class

```java
List<String> names = new ArrayList<>();
names.add(null);
```

`null` hợp lệ với kiểu phần tử tham chiếu, nhưng `names.get(0).getClass()` ném `NullPointerException`. Ngay cả khi bỏ qua các phần tử `null`, dữ liệu còn lại vẫn không đủ để kết luận chữ ký generic.

### 2.4. Kiểu phần tử runtime thường cụ thể hơn kiểu khai báo

```java
List<CharSequence> texts = new ArrayList<>();
texts.add("abc");
```

Một phần tử `String` là `CharSequence`. Đọc `texts.get(0).getClass()` cho `String.class`, nhưng **kiểu generic khai báo là `CharSequence`**, không phải `String`.

Tương tự: field `List<? extends Number>` có thể cung cấp các phần tử `Integer`, nhưng không thể kết luận đối số kiểu gốc là `Integer`. Wildcard chỉ công bố **ràng buộc**, không công bố nhất thiết một concrete type.

### 2.5. Raw types/unchecked operations có thể làm bẩn dữ liệu

Java generic chủ yếu bảo vệ kiểu ở compile time. Code sử dụng raw type, unchecked cast hoặc interop với thư viện cũ có thể tạo **heap pollution**:

```java
@SuppressWarnings({"rawtypes", "unchecked"})
static void corrupt(List<String> strings) {
    List raw = strings;
    raw.add(Integer.valueOf(99));
}
```

Đây là **đoạn phương thức minh họa**, không nên dùng trong ứng dụng thực. Sau thao tác ấy, metadata khai báo vẫn có thể nói `List<String>`, nhưng nội dung thực tế lại chứa `Integer`. Khi code Java bình thường lấy phần tử như `String`, có thể gặp `ClassCastException`.

Kết luận hai chiều: **không suy ra kiểu khai báo từ phần tử**, đồng thời **không dùng metadata khai báo để khẳng định dữ liệu runtime không bao giờ bị vi phạm** nếu có unchecked/raw operations.

## 3. Vì sao dữ liệu quan sát khác thông tin generic?

Một đối tượng `new ArrayList<String>()` thông thường có runtime class `ArrayList.class`, giống đối tượng tạo với `new ArrayList<Integer>()`. Cơ chế **type erasure** không lưu đối số `String` hay `Integer` riêng trên mỗi instance. Nếu duyệt một danh sách chứa toàn chuỗi, đó vẫn chỉ là dữ liệu hiện tại: kiểu khai báo hoàn toàn có thể là `List<Object>` hoặc `List<CharSequence>`.

Ngược lại, class file có thể giữ *generic signature* của **nơi khai báo**: một field `List<Number> prices`, return type `List<String>` hoặc quan hệ `extends ArrayList<Long>`. Reflection có API đọc metadata đó, nhưng không tự gắn ngược metadata của một biến local bất kỳ vào object mà biến trỏ tới.

| Muốn biết | Đọc bằng | Giới hạn cần giữ rõ |
| --- | --- | --- |
| Collection đang dùng implementation nào | `object.getClass()` | Không tiết lộ generic argument |
| Giá trị hiện có thuộc class nào | `element.getClass()` | Có thể rỗng, `null`, subtype hoặc bị thay đổi |
| Kiểu đã khai báo trên field | `Field.getGenericType()` | Mô tả khai báo; không chứng minh nội dung runtime |
| Kiểu đã khai báo trên method | `Method.getGenericReturnType()` / `getGenericParameterTypes()` | Không tự lưu type argument suy diễn của từng lời gọi |
| Supertype generic đã khai báo | `Class.getGenericSuperclass()` / `getGenericInterfaces()` | Nếu còn `TypeVariable` thì cần ngữ cảnh phân giải |

## 4. Đọc chữ ký phần tử từ đúng declaration

Với field bên dưới, Reflection đọc được `Number` vì thông tin nằm trong **khai báo field**, không phải do lấy `getClass()` của phần tử đầu tiên:

~~~java
class Catalog {
    java.util.List<Number> prices = new java.util.ArrayList<>();
}

// Đặt trong method có xử lý NoSuchFieldException:
java.lang.reflect.Field field = Catalog.class.getDeclaredField("prices");
java.lang.reflect.Type declared = field.getGenericType();
if (declared instanceof java.lang.reflect.ParameterizedType p) {
    System.out.println(p.getActualTypeArguments()[0].getTypeName());
    // java.lang.Number
}
~~~

Nếu chỉ có tham chiếu `Object unknown` tới một list được tạo thông thường, không có phép Reflection tổng quát nào xác định **field hoặc biến nguồn** từng dùng để tham chiếu object đó. Bài thí nghiệm tiếp theo đặt runtime observations cạnh metadata từ field để thấy sự khác biệt này.

## 5. Khi đối số generic chưa phải một Class cụ thể

`Field.getGenericType()` trả `Type`; một argument trực tiếp của `List<E>` có thể là `Class<?>` (như `Number`), `TypeVariable<?>` (như `T` trong `Holder<T>`), `WildcardType` (như `? extends Number`) hoặc một `ParameterizedType` lồng nhau (như `List<Integer>` trong `List<List<Integer>>`). Với `List<String>[]`, lớp bao ngoài có thể là `GenericArrayType`; component là một `ParameterizedType`.

Vì thế, phép lấy `getActualTypeArguments()[0]` phải đặt trong ngữ cảnh **raw type** đang phân tích. `Map<K,V>` có hai arguments, và `List<? extends Number>` mang wildcard bound chứ không công bố chính xác `Number` như đối số cụ thể. Nếu argument còn là biến kiểu `T` của superclass, cần ánh xạ theo khai báo và quan hệ kế thừa; tuyệt đối không thay bằng `Object` hoặc upper bound một cách tùy ý.

## 6. Thí nghiệm Java 17+ hoàn chỉnh: kiểu quan sát khác kiểu khai báo

Lưu thành tệp **`GenericElementDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenericElementDemo {
    static class Catalog {
        List<Number> prices = new ArrayList<>();
        List<String> emptyNames = new ArrayList<>();
        List<String> nullableNames = Arrays.asList(null, "Lan");
        List<List<Integer>> matrix = List.of(List.of(1, 2));
        List<? extends Number> readOnly = List.of(5, 6.5);
        List<? super Integer> writable = new ArrayList<Number>();
        List<String>[] buckets;
    }

    static class Holder<T extends Number> {
        List<T> items = new ArrayList<>();
    }

    static class IntegerHolder extends Holder<Integer> { }
    static class LongList extends ArrayList<Long> { }

    static Type firstArgument(Field field) {
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType parameterized)) {
            throw new IllegalArgumentException(
                    "Not parameterized: " + field.getName());
        }
        return parameterized.getActualTypeArguments()[0];
    }

    public static void main(String[] args) throws Exception {
        Catalog data = new Catalog();
        data.prices.add(7);
        data.prices.add(2.5);

        System.out.println("observed: "
                + data.prices.get(0).getClass().getSimpleName()
                + ", " + data.prices.get(1).getClass().getSimpleName());
        System.out.println("runtime list: "
                + data.prices.getClass().getName());
        System.out.println("empty sample: "
                + data.emptyNames.isEmpty());
        System.out.println("first null: "
                + (data.nullableNames.get(0) == null));

        Field prices = Catalog.class.getDeclaredField("prices");
        System.out.println("erased field: "
                + prices.getType().getTypeName());
        System.out.println("generic field: "
                + prices.getGenericType().getTypeName());
        System.out.println("declared element: "
                + firstArgument(prices).getTypeName());

        System.out.println("nested element: "
                + firstArgument(
                        Catalog.class.getDeclaredField("matrix"))
                        .getTypeName());
        System.out.println("upper wildcard: "
                + firstArgument(
                        Catalog.class.getDeclaredField("readOnly"))
                        .getTypeName());
        System.out.println("lower wildcard: "
                + firstArgument(
                        Catalog.class.getDeclaredField("writable"))
                        .getTypeName());
        System.out.println("array type: "
                + Catalog.class.getDeclaredField("buckets")
                        .getGenericType().getTypeName());

        System.out.println("unresolved variable: "
                + firstArgument(Holder.class.getDeclaredField("items"))
                        .getTypeName());
        Type parent = IntegerHolder.class.getGenericSuperclass();
        System.out.println("subclass binding: "
                + ((ParameterizedType) parent)
                        .getActualTypeArguments()[0].getTypeName());

        Type inherited = LongList.class.getGenericSuperclass();
        System.out.println("specialized subclass: "
                + ((ParameterizedType) inherited)
                        .getActualTypeArguments()[0].getTypeName());

        System.out.println("same erased class: "
                + (new ArrayList<String>().getClass()
                == new ArrayList<Integer>().getClass()));
    }
}
```

Chạy từ thư mục chứa tệp bằng JDK 17+:

```bash
javac GenericElementDemo.java
java GenericElementDemo
```

**Stdout đã kiểm tra trên JDK 21:**

```text
observed: Integer, Double
runtime list: java.util.ArrayList
empty sample: true
first null: true
erased field: java.util.List
generic field: java.util.List<java.lang.Number>
declared element: java.lang.Number
nested element: java.util.List<java.lang.Integer>
upper wildcard: ? extends java.lang.Number
lower wildcard: ? super java.lang.Integer
array type: java.util.List<java.lang.String>[]
unresolved variable: T
subclass binding: java.lang.Integer
specialized subclass: java.lang.Long
same erased class: true
```

### Vì sao những dòng này quan trọng?

1. Hai phần tử của `prices` có kiểu runtime khác nhau (`Integer`, `Double`); chữ ký khai báo vẫn là `List<Number>`.
2. `prices.getClass()` chỉ cho biết container là `ArrayList`, không biết `Number`.
3. Với `emptyNames`, không có phần tử để suy đoán. Với `nullableNames`, phần tử đầu là `null`; gọi `getClass()` trực tiếp trên nó sẽ lỗi.
4. `Field.getType()` trả `java.util.List` do erasure; `Field.getGenericType()` đọc signature khai báo `List<Number>`. Đây là lý do Reflection có thể biết kiểu phần tử **từ metadata**, chứ không phải từ nội dung.
5. Argument đầu của `matrix` là `List<Integer>` (một nested `ParameterizedType`); chưa phải bản thân `Integer`.
6. Hai wildcard công bố bounds, không công bố chính xác một actual type argument đơn lẻ.
7. Field `Holder.items` có argument `T`, vì bối cảnh `Holder<T>` vẫn trừu tượng; `IntegerHolder` mang binding `Integer` ở superclass, nhưng phải **tự kết hợp ngữ cảnh** khi phân giải field kế thừa.
8. `LongList` bảo tồn `Long` ở chữ ký **generic superclass** vì chính class được khai báo là `extends ArrayList<Long>`.
9. Hai object `new ArrayList<String>()` và `new ArrayList<Integer>()` có cùng `Class` runtime; type erasure không tạo một `ArrayList` class riêng cho từng đối số generic.

## 7. Tra cứu generic từ method và superclass/interface

Không phải thông tin generic nào cũng nằm trên field. Một API thường công bố dữ liệu qua return type của getter hoặc parameter type của setter:

```java
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.ParameterizedType;
import java.util.List;

class Service {
    public List<String> findNames() {
        return List.of("Lan");
    }

    public void replaceNames(List<String> names) {
    }
}

// Đoạn minh họa dùng trong phương thức có xử lý NoSuchMethodException:
Method getter = Service.class.getMethod("findNames");
Type returnSignature = getter.getGenericReturnType();
// returnSignature mô tả List<String>

Method setter = Service.class.getMethod("replaceNames", List.class);
Type parameterSignature = setter.getGenericParameterTypes()[0];
// parameterSignature mô tả List<String>
```

Đây là **đoạn minh họa gồm class và các lệnh Reflection**, không phải chương trình độc lập có `main`. Chữ ký `getMethod("replaceNames", List.class)` dùng **raw runtime parameter class** vì lookup method diễn ra sau erasure; sau khi tìm xong mới đọc `getGenericParameterTypes()` để biết `String`.

Bạn cũng có thể đọc:

- `SomeClass.class.getGenericSuperclass()` để xem superclass được khai báo với type arguments nào.
- `SomeClass.class.getGenericInterfaces()` để xem các generic interfaces được triển khai.
- `field.getGenericType()` để xem field signature được khai báo.
- `method.getGenericReturnType()` và `getGenericParameterTypes()` để xem return/parameter signatures.

**Không được suy từ method body:** `findNames()` có thể trả một `List.of()` rỗng, hoặc mọi phần tử là `null` trong các triển khai khác. Điều hữu ích cho Reflection là **return type khai báo** `List<String>`, chứ không phải runtime sample trong từng lần gọi.

### Cần ngữ cảnh khi type variable đi qua nhiều cấp kế thừa

Ví dụ:

```java
class Base<T> {
    List<T> values;
}

class Middle<U> extends Base<U> {
}

class Concrete extends Middle<Long> {
}
```

Field `values` được khai báo ở `Base` với `T`, trong khi `Concrete` cung cấp binding `U -> Long` và `Middle<U>` cung cấp `T -> U`. Để kết luận `values` ứng với `List<Long>` khi xét `Concrete`, bộ phân giải generic phải **đi qua các generic superclass signatures** và thay thế type variables nhất quán.

Chỉ gọi `Concrete.class.getDeclaredField("values")` còn thất bại vì field **không được khai báo trực tiếp** trong `Concrete`. Phải tìm field ở lớp khai báo hoặc duyệt hierarchy, rồi phân giải theo ngữ cảnh class cụ thể.

Khi thiết kế resolver, đừng luôn coi `TypeVariable.getBounds()[0]` là actual type. Bound chỉ giới hạn các kiểu hợp lệ; không phải giá trị thay thế thực tế.

## 8. Khi không có generic metadata: yêu cầu hợp đồng kiểu tường minh

Nhiều API nhận trực tiếp một object:

```java
void process(List<?> items) {
    // Không có field/method declaration của danh sách ban đầu
    // để khôi phục chắc chắn actual element type E.
}
```

Tại đây `items.getClass()` thường chỉ cho lớp collection. Nếu API cần bảo đảm kiểu phần tử, hãy chuyển kiểu mong đợi vào theo một trong các cách:

**Lựa chọn A — truyền `Class<E>` cho phần tử không generic lồng nhau:**

```java
static <E> void validate(List<?> items, Class<E> elementClass) {
    for (Object item : items) {
        if (item != null && !elementClass.isInstance(item)) {
            throw new IllegalArgumentException(
                    "Unexpected element type: " + item.getClass().getName());
        }
    }
}

// Ví dụ trong một phương thức khác:
validate(List.of("a", "b"), String.class);
```

Đây là đoạn phương thức minh họa, đã giả định import `java.util.List`. Logic này **kiểm tra dữ liệu theo kiểu do caller cung cấp**, không *suy ra* generic type. Với `null`, code minh họa đang chấp nhận; nếu domain cấm null thì cần thêm nhánh từ chối riêng. `Class.isInstance` kiểm tra runtime assignability; không làm được kiểm tra đầy đủ với nested type `List<List<String>>`. Đối với generic `List<Integer>`, hãy truyền `Integer.class`, không phải `int.class`.

**Lựa chọn B — truyền một `Type` hoặc type token cho cấu trúc nested:**

```java
abstract class TypeRef<T> {
    final java.lang.reflect.Type type;

    protected TypeRef() {
        java.lang.reflect.Type parent =
                getClass().getGenericSuperclass();
        if (!(parent instanceof java.lang.reflect.ParameterizedType p)) {
            throw new IllegalStateException("Missing generic signature");
        }
        type = p.getActualTypeArguments()[0];
    }
}

// Khi tạo anonymous subclass của TypeRef:
java.lang.reflect.Type target =
        new TypeRef<java.util.List<java.util.Map<String, Integer>>>() { }
                .type;
```

Đây là đoạn **mẫu type token tối giản**, đặt các khai báo ở phạm vi class và câu lệnh `target` trong một phương thức phù hợp; nó dựa vào generic superclass signature được compiler ghi cho anonymous subclass. Không tự động kiểm tra dữ liệu hoặc chuyển JSON thành Java object: nó chỉ giữ cấu trúc `Type` để thư viện/bộ xử lý tiếp tục sử dụng. Với type variable chưa được giải trong anonymous subclass, vẫn phải xem ngữ cảnh phù hợp.

**Lựa chọn C — định nghĩa schema hoặc API rõ ràng:** nếu dữ liệu đến từ JSON, database, message queue hay plugin, hợp đồng bên ngoài (schema, serializer configuration hoặc type descriptor) thường đáng tin hơn việc xem phần tử đầu tiên. Những dữ liệu rỗng hay có null vẫn cần được parse/validate đúng kiểu.

## 9. Vì sao không thể luôn lấy “kiểu chung” của tất cả phần tử?

Giả sử danh sách có `Integer` và `Double`. Ta có thể nhận thấy chúng đều mở rộng `Number`, nhưng:

- Kiểu khai báo có thể là `List<Number>`, `List<Object>`, `List<? extends Number>` hoặc một view/kiểu khác.
- Nhiều loại Java có chung interface như `Serializable`, `Comparable<?>...`; “kiểu chung gần nhất” không nhất thiết là một class duy nhất có ý nghĩa làm type argument.
- Với dữ liệu thiếu, null hoặc collection rỗng, không có đủ chứng cứ để suy ra type.
- Type variable và wildcard diễn đạt **quan hệ/ràng buộc**, không thể được phục hồi đầy đủ từ một tập hữu hạn object đang tồn tại.
- Type erasure cho phép nhiều generic instantiations dùng cùng runtime class; class của container không chứa đủ dữ liệu để đảo ngược erasure.

Một heuristic tính common superclass có thể hữu ích để **phân tích dữ liệu** hoặc đề xuất schema, nhưng phải được gọi đúng là **suy đoán dựa trên sample**. Đừng trình bày heuristic ấy như kết quả của generic type resolution, và đừng đưa nó vào cast/deserialize quan trọng mà không kiểm tra hoặc không có schema chính thức.

## 10. Ứng dụng thực tế

### Serialization / deserialization

Một JSON array `[]` không có phần tử nào để suy kiểu; JSON `[null]` cũng không có runtime class có thể dùng. Nếu mục tiêu là `List<Order>`, serializer cần nhận thông tin kiểu từ field/getter signature hoặc type descriptor, chứ không thể chỉ xem từng giá trị của array.

Ví dụ với nested type `List<Map<String, List<Order>>>`, `Class<List>` không mô tả đủ cấu trúc. Cần `Type`/type token hoặc mô tả kiểu tương đương để bảo toàn nested generic arguments.

### ORM / database mapping

Một quan hệ `List<User>` có thể hiện tại chưa load bản ghi nào. Khung ORM không thể nhìn tập hợp trống để suy entity type, mà cần field/association metadata, annotation, schema hoặc mapping được khai báo.

### Plugin / event processing

Một `List<Event>` có thể chứa nhiều subtype `LoginEvent`, `PurchaseEvent`; đó là polymorphism hợp lệ. Đừng dùng class của event đầu tiên để suy rằng mọi event thuộc subtype đó. Hợp đồng handler cần nói rõ event base type hoặc registry/type descriptor.

### Validation

Nếu hệ thống nhận `List<?>` từ dữ liệu không tin cậy, phải validate từng phần tử theo schema/type đã biết; việc quan sát một phần tử hợp lệ không chứng minh toàn bộ danh sách hợp lệ. Nếu type có nested generics, validation phải đi sâu vào từng tầng dữ liệu theo hợp đồng, không chỉ kiểm tra raw `List.class`.

## 11. Các lỗi tư duy phổ biến

1. **“Phần tử đầu là `Integer` nên list có kiểu `List<Integer>`.”** Sai. Có thể là `List<Number>`, `List<Object>` hoặc kiểu khác.
2. **“Mọi phần tử hiện có đều là `String` nên generic chắc chắn là `String`.”** Sai. Mẫu dữ liệu không chứng minh chữ ký khai báo.
3. **“Danh sách rỗng có thể suy phần tử từ `getClass()`.”** Sai với `ArrayList` thông thường do erasure.
4. **“Đọc `getClass()` trên một phần tử `null` vẫn biết được kiểu.”** Sai: gây `NullPointerException`.
5. **“Java xóa hết generic metadata nên Reflection không thể đọc bất kỳ generic type nào.”** Sai. Signature của field, method, superclass/interface có thể vẫn chứa cấu trúc generic.
6. **“`getType()` và `getGenericType()` luôn giống nhau.”** Sai. Với `List<String>`, một bên có thể trả `List.class`, bên kia trả `ParameterizedType` mô tả `List<String>`.
7. **“Mọi `Type` có thể cast thành `Class<?>`.”** Sai. `ParameterizedType`, `TypeVariable`, `WildcardType` và `GenericArrayType` cần xử lý riêng.
8. **“`List<? extends Number>` có actual argument đúng `Number`.”** Sai. Nó là wildcard có upper bound `Number`, không phải `Number.class`.
9. **“Field `List<T>` trong base class tự trở thành `List<Integer>` khi gọi `getGenericType()` ở base.”** Sai. Phải phân giải type variable theo generic bindings trên đường kế thừa.
10. **“Type token kiểm tra mọi phần tử runtime đã đúng kiểu.”** Sai. Type token chỉ mang metadata kiểu; validation/deserialization là bước khác.
11. **“`List<Integer>` là subtype của `List<Number>` vì `Integer extends Number`.”** Sai. Java generic ở đây **invariant**; có thể dùng `List<? extends Number>` cho read-only view phù hợp.
12. **“Đã biết `List<String>` từ field metadata thì runtime không thể có phần tử `Integer`.”** Không bảo đảm tuyệt đối khi tồn tại raw/unchecked operations; cần validation nếu input không tin cậy.
13. **“Mảng `List<String>[]` có component là `String`.”** Sai. Component là **`List<String>`**; từng List mới có element type `String`.

## 12. Tự kiểm tra

**Câu 1.** Một `List<Number>` hiện chỉ chứa `Integer(1)`. `list.get(0).getClass()` cho biết điều gì?

**Đáp án:** Chỉ cho biết object được quan sát có runtime class `Integer`; không đổi hay xác định được kiểu khai báo `List<Number>`.

**Câu 2.** Một list rỗng có thể là `List<String>` hay `List<Integer>`. Vì sao `list.getClass()` không đủ?

**Đáp án:** Với `ArrayList` thông thường, generic type arguments đã bị erasure ở runtime; cả hai object đều có class `ArrayList`. Không có phần tử để suy thêm và sample inference cũng không chứng minh declaration.

**Câu 3.** Field `List<? extends Number> values` có `getGenericType()` thuộc dạng gì và argument đầu thuộc dạng gì?

**Đáp án:** `getGenericType()` có thể là `ParameterizedType` cho `List<? extends Number>`; argument đầu là `WildcardType` có upper bound `Number`, không phải trực tiếp `Number.class`.

**Câu 4.** Khi field được khai báo `Map<String, List<Integer>>`, có thể luôn lấy `getActualTypeArguments()[0]` làm kiểu phần tử của một List không?

**Đáp án:** Không. Raw type là `Map` với hai tham số `K,V`, trong đó phần tử cần phân tích thuộc **value argument** `List<Integer>`. Phải hiểu cấu trúc generic đúng loại.

**Câu 5.** `Holder<T>` khai báo `List<T> items`, `IntHolder extends Holder<Integer>`. Vì sao chỉ đọc field ở `Holder.class` vẫn thấy `T`?

**Đáp án:** Vì field được khai báo bằng type variable `T`. Binding `T -> Integer` thuộc bối cảnh `IntHolder` và phải được áp dụng khi phân giải.

**Câu 6.** API nhận `List<?>` có thể bảo đảm tất cả phần tử là `User` chỉ bằng kiểm tra phần tử đầu không?

**Đáp án:** Không. Cần hợp đồng `User.class`/schema/type token rõ ràng và validate mọi phần tử cần thiết; dữ liệu còn có thể rỗng, null, hỗn hợp hoặc bị thêm sau khi kiểm tra.

## Tài liệu chuẩn

- Java SE API — `java.lang.reflect.Field.getType()`, `Field.getGenericType()`; `Method.getGenericReturnType()`, `Method.getGenericParameterTypes()`.
- Java SE API — `java.lang.reflect.Type`, `ParameterizedType`, `TypeVariable`, `WildcardType`, `GenericArrayType`.
- Java SE API — `java.lang.Class.getGenericSuperclass()`, `Class.getGenericInterfaces()`, `Class.isInstance(Object)`.
- Java Language Specification — §4.5 *Parameterized Types*; §4.6 *Type Erasure*; §4.8 *Raw Types*; §4.12.2 *Variables of Reference Type*; §5.1.10 *Capture Conversion*.
- Java Language Specification — §10.5 *Array Store Exception* và các quy tắc generic arrays/reifiable types khi phân tích mảng.

**Ghi nhớ:** phần tử đang có chỉ cho biết **runtime class của mẫu dữ liệu**, không xác nhận **kiểu generic được khai báo**. Để phân giải generic, hãy dùng **metadata ở đúng vị trí khai báo và đúng ngữ cảnh kế thừa**, hoặc **hợp đồng kiểu tường minh**. Với dữ liệu không tin cậy, kiểm tra phần tử theo hợp đồng, không suy kiểu từ phần tử đầu tiên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-resolution-010">Tránh đệ quy vô hạn khi giới hạn kiểu tự tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh đệ quy vô hạn khi giới hạn kiểu tự tham chiếu trong Java Reflection

Khi phân tích kiểu generic bằng Java Reflection, một biến kiểu có thể xuất hiện **bên trong chính giới hạn (bound) của nó**. Ví dụ quen thuộc nhất là:

```java
class Self<T extends Self<T>> {}

enum State {
    NEW, DONE
}
```

Ở đây, `T` được ràng buộc bởi `Self<T>`. Tương tự, lớp chuẩn Java `Enum` được khai báo theo mẫu `Enum<E extends Enum<E>>`. Các khai báo như vậy hợp lệ và có ích. Chúng thuộc nhóm **F-bounded polymorphism**, cho phép diễn đạt rằng kiểu tham số có quan hệ với chính kiểu đó.

Vấn đề chỉ xuất hiện khi ta tự xây dựng bộ phân tích metadata kiểu:

```text
T -> bound Self<T> -> argument T -> bound Self<T> -> ...
```

Nếu mã cứ gọi `TypeVariable.getBounds()` rồi duyệt `ParameterizedType.getActualTypeArguments()` mà **không phát hiện chu trình**, quá trình phân tích có thể đệ quy vô hạn, gây `StackOverflowError` hoặc tiêu tốn tài nguyên. **Giải pháp là coi cấu trúc generic như đồ thị, nhận diện nút đã nằm trên đường đệ quy hiện tại, và dừng mở rộng chính nút đó.**

## 1. Hiểu đúng giới hạn kiểu tự tham chiếu

Khai báo:

```java
class Self<T extends Self<T>> {}
```

có thể đọc là: một đối số kiểu thay vào `T` phải thỏa ràng buộc là một subtype thích hợp của `Self<T>`. Đây là một quy tắc **ràng buộc kiểu tại compile time**, không yêu cầu `Self` tạo vô hạn đối tượng lúc runtime.

Một ứng dụng điển hình của F-bounds:

```java
abstract class Builder<T extends Builder<T>> {
    abstract T self();

    T label(String text) {
        System.out.println(text);
        return self();
    }
}

final class ReportBuilder extends Builder<ReportBuilder> {
    @Override
    ReportBuilder self() {
        return this;
    }
}
```

Đây là đoạn khai báo minh họa; nó cho phép `label()` trả về kiểu cụ thể `ReportBuilder` khi sử dụng một builder tuân theo mẫu này. Bản thân cú pháp generic không tạo vòng chạy vô hạn.

Trong API chuẩn, `Enum<E extends Enum<E>>` là ví dụ nền tảng: đối số `E` phải liên hệ với kiểu enum cụ thể. Một số ràng buộc cũng xuyên qua interface, wildcard và nhiều biến kiểu; vì vậy chỉ xét trường hợp `T extends T` hoặc chỉ giới hạn một cấp là chưa đủ.

## 2. Các đối tượng `Type` liên quan trong Reflection

`java.lang.reflect.Type` là giao diện gốc để biểu diễn các dạng kiểu. Khi phân giải generic, ta thường gặp:

| Dạng | API Reflection | Ý nghĩa với việc duyệt |
| --- | --- | --- |
| `Class<?>` | `Class`, `isArray()`, `getComponentType()` | Kiểu thông thường hoặc mảng có component là class |
| `TypeVariable<?>` | `getName()`, `getGenericDeclaration()`, `getBounds()` | Biến kiểu `T`, `E`; có thể dẫn trở lại chính nó |
| `ParameterizedType` | `getRawType()`, `getActualTypeArguments()`, `getOwnerType()` | `List<T>`, `Self<T>`, `Map<K,V>` |
| `WildcardType` | `getUpperBounds()`, `getLowerBounds()` | `? extends X` hoặc `? super X` |
| `GenericArrayType` | `getGenericComponentType()` | `T[]` hoặc mảng có component type generic |

Ví dụ phân tích `Self.class.getTypeParameters()[0]`:

- Kết quả là `TypeVariable<?>` cho `T`.
- `T.getBounds()[0]` là `ParameterizedType` biểu diễn `Self<T>`.
- `getActualTypeArguments()[0]` của `Self<T>` lại là biến kiểu `T`.
- Nếu từ biến kiểu này ta lại gọi `getBounds()`, quá trình quay về `Self<T>` và lặp.

Đây là **chu trình tham chiếu giữa metadata types**, không phải lỗi trong khai báo Java.

Cần phân biệt ba phép đọc: `Class.getTypeParameters()` lấy biến kiểu do class khai báo; `TypeVariable.getBounds()` lấy giới hạn của biến kiểu; `ParameterizedType.getActualTypeArguments()` lấy đối số kiểu trong một kiểu đã tham số hóa. Việc nối ba API có thể vô tình quay lại nút cũ.

## 3. Tại sao cách duyệt đệ quy ngây thơ thất bại?

Ví dụ sau minh họa **thuật toán sai** (không chạy độc lập và không nên dùng trong ứng dụng):

```java
static void brokenWalk(Type type) {
    if (type instanceof TypeVariable<?> variable) {
        for (Type bound : variable.getBounds()) {
            brokenWalk(bound);
        }
    } else if (type instanceof ParameterizedType parameterized) {
        for (Type argument : parameterized.getActualTypeArguments()) {
            brokenWalk(argument);
        }
    }
}
```

Đường đi với `T extends Self<T>`:

```text
TypeVariable T
    |
    v
ParameterizedType Self<T>
    |
    v
Actual type argument: TypeVariable T
    |
    v
Bound: ParameterizedType Self<T>
    |
    v
Actual type argument: TypeVariable T
    ... (lặp lại)
```

Nếu không có điểm dừng, mỗi lần thăm `T` lại tạo thêm một lần đệ quy vào bound. Với đủ lần lặp, chương trình có thể ném `StackOverflowError`. Không nên thử tạo một lần chạy thật sự đến lỗi stack overflow để kiểm chứng; đồ thị đã cho thấy chu trình.

**Cấm suy luận sai:** `T.getBounds()` không có lời hứa rằng kết quả sẽ là tập kiểu không chứa đường dẫn quay về `T`. Generic bounds hợp lệ hoàn toàn có thể tự tham chiếu hoặc tham chiếu qua các kiểu trung gian.

## 4. Cốt lõi giải pháp: theo dõi đường đệ quy đang hoạt động

Đối với một cây thuần túy, ta có thể duyệt các nút con mà không cần nhớ những nút đã gặp. Đối với **đồ thị có chu trình**, cần theo dõi đường đi.

Thuật toán DFS an toàn:

1. Nhận một `Type` và bộ `active` chứa các `Type` **đang nằm trên nhánh đệ quy hiện tại**.
2. Nếu `type` đã có trong `active`, in/chú thích một chu trình và **không duyệt tiếp** từ nút này.
3. Nếu chưa có, thêm vào `active` rồi xử lý children theo loại `Type`.
4. Sau khi xử lý xong children, xóa `type` khỏi `active` trong `finally`.

Pseudocode:

```text
visit(type, active):
    if type in active:
        emit "[cycle]"
        return

    active.add(type)
    try:
        emit type
        for each child of type:
            visit(child, active)
    finally:
        active.remove(type)
```

Điểm quan trọng là **phải xóa khi rời nhánh**. Khi một kiểu hợp lệ xuất hiện ở hai nhánh khác nhau, ta có thể muốn hiển thị kiểu đó ở cả hai nơi. Cờ chu trình cần phát hiện sự lặp **trên cùng đường đi**, không mặc định xóa mọi lần xuất hiện sau này trong toàn bộ đồ thị.

Đối với một công cụ chỉ muốn liệt kê mỗi kiểu đúng một lần, tập `visited` toàn cục cũng hữu ích. Nhưng cần phân biệt rõ: **`active` phát hiện chu trình**, còn **`visited` toàn cục phát hiện mọi sự thăm lại**. Dùng `visited` toàn cục cho kết quả dạng cây có thể làm mất thông tin của một nhánh hợp lệ.

## 5. Ví dụ Java 17+ đầy đủ: duyệt `Type` và đánh dấu chu trình

Chương trình sau duyệt các dạng `TypeVariable`, `ParameterizedType`, `WildcardType`, `GenericArrayType` và `Class` dạng mảng. Để output dễ đọc, ví dụ chỉ in tên ngắn của raw class trong `ParameterizedType`; không đi vào toàn bộ các method/field của raw class.

Lưu nội dung vào `GenericCycleDemo.java`:

```java
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.Set;

public class GenericCycleDemo {

    static class Self<T extends Self<T>> {}

    static class Ranked<T extends Comparable<? super T>> {}

    static class Arrays<T extends Comparable<T>> {
        T[] values;
    }

    static void walk(Type type, int depth, Set<Type> active) {
        String indent = "  ".repeat(depth);

        if (!active.add(type)) {
            System.out.println(indent + "CYCLE -> " + label(type));
            return;
        }

        System.out.println(indent + label(type));

        try {
            if (type instanceof TypeVariable<?> variable) {
                for (Type bound : variable.getBounds()) {
                    walk(bound, depth + 1, active);
                }
            } else if (type instanceof ParameterizedType parameterized) {
                for (Type argument : parameterized.getActualTypeArguments()) {
                    walk(argument, depth + 1, active);
                }
            } else if (type instanceof WildcardType wildcard) {
                for (Type bound : wildcard.getUpperBounds()) {
                    walk(bound, depth + 1, active);
                }
                for (Type bound : wildcard.getLowerBounds()) {
                    walk(bound, depth + 1, active);
                }
            } else if (type instanceof GenericArrayType array) {
                walk(array.getGenericComponentType(), depth + 1, active);
            } else if (type instanceof Class<?> clazz && clazz.isArray()) {
                walk(clazz.getComponentType(), depth + 1, active);
            }
        } finally {
            active.remove(type);
        }
    }

    static String label(Type type) {
        if (type instanceof TypeVariable<?> variable) {
            return "variable " + variable.getName();
        }
        if (type instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            String name = raw instanceof Class<?> clazz
                    ? clazz.getSimpleName() : raw.getTypeName();
            return "parameterized " + name;
        }
        if (type instanceof WildcardType) {
            return "wildcard ?";
        }
        if (type instanceof GenericArrayType) {
            return "generic array []";
        }
        return "class " + type.getTypeName();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Self:");
        walk(Self.class.getTypeParameters()[0], 0, new HashSet<>());

        System.out.println("Ranked:");
        walk(Ranked.class.getTypeParameters()[0], 0, new HashSet<>());

        System.out.println("Arrays:");
        walk(Arrays.class.getDeclaredField("values").getGenericType(),
                0, new HashSet<>());

        System.out.println("Enum:");
        walk(Enum.class.getTypeParameters()[0], 0, new HashSet<>());
    }
}
```

Lệnh:

```bash
javac GenericCycleDemo.java
java GenericCycleDemo
```

Đầu ra (Java 17+):

```text
Self:
variable T
  parameterized Self
    CYCLE -> variable T
Ranked:
variable T
  parameterized Comparable
    wildcard ?
      class java.lang.Object
      CYCLE -> variable T
Arrays:
generic array []
  variable T
    parameterized Comparable
      CYCLE -> variable T
Enum:
variable E
  parameterized Enum
    CYCLE -> variable E
```

Giải thích:

- **Self:** `T` có bound `Self<T>`. Khi vào đối số kiểu `T` lần thứ hai trên cùng đường đệ quy, chương trình in `CYCLE -> variable T` rồi dừng.
- **Ranked:** `T extends Comparable<? super T>` dẫn qua `ParameterizedType`, sau đó `WildcardType`, rồi quay lại `T`. Wildcard có upper bound mặc định `Object`, đồng thời lower bound là `T`.
- **Arrays:** trường `T[] values` được thể hiện bằng `GenericArrayType`; component `T` dẫn tiếp tới bound `Comparable<T>`, sau đó quay lại `T`.
- **Enum:** `E extends Enum<E>` trong API chuẩn Java cũng có chu trình hợp lệ. Bộ phân tích dừng ở lần quay lại `E`.

Với ví dụ này, **mọi đường duyệt đều kết thúc** mà không cần giới hạn độ sâu tùy tiện.

## 6. Vì sao dùng `Set<Type>` và không chỉ `Set<String>`?

Một biến kiểu tên `T` không có danh tính duy nhất trên toàn ứng dụng:

```java
class Box<T> {}

class Other<T> {}

class Service {
    <T> void execute(T value) {}
}
```

Cả ba có thể có biến tên `T`, nhưng được khai báo ở **những generic declaration khác nhau**. Nếu dùng `HashSet<String>` chứa tên `"T"`, bộ phân tích sẽ coi các biến không liên quan là một nút duy nhất.

Thay vào đó, dùng `TypeVariable<?>` và các đối tượng `Type` đúng nghĩa. `TypeVariable.getGenericDeclaration()` xác định khai báo sở hữu biến kiểu: `Class`, `Method` hoặc `Constructor`. Theo hợp đồng `TypeVariable`, nhiều đối tượng runtime có thể đại diện cùng một biến kiểu; chúng phải được xem là tương đương theo khai báo và tên. Vì vậy, với các implementation Reflection chuẩn, `HashSet<Type>` sử dụng `equals()/hashCode()` là lựa chọn phù hợp để nhận diện nút.

**Không chỉ dựa vào `==` hoặc `IdentityHashMap`** cho thuật toán này: API không bảo đảm mọi lần lấy metadata của cùng một biến kiểu đều trả về đúng một object identity. Nếu reflection tái tạo wrapper tương đương, so sánh tham chiếu có thể bỏ sót chu trình.

Nếu cần hiển thị danh tính để debug, có thể in cả:

```java
TypeVariable<?> variable = Self.class.getTypeParameters()[0];
System.out.println(variable.getName());
System.out.println(variable.getGenericDeclaration());
```

Đây là đoạn minh họa sử dụng class `Self` từ chương trình chính. `getGenericDeclaration()` giúp phân biệt hai `T` cùng tên từ hai nơi khai báo khác nhau, còn kết quả duyệt vẫn cần cơ chế `active`.

## 7. Vì sao `active` phải được quản lý theo nhánh?

Xét một cấu trúc generic có hai nhánh cùng tham chiếu một biến kiểu nhưng không tạo chu trình trên từng nhánh. Ví dụ về mặt cấu trúc:

```text
ParameterizedType Pair<T, T>
                /      \
               T        T
```

Nếu dùng một tập `visited` toàn cục rồi không xóa sau nhánh đầu tiên, nhánh thứ hai có thể bị in nhầm là đã xử lý hoặc bị bỏ qua. Với `active` và `finally { active.remove(type); }`, mỗi nhánh độc lập có thể hiển thị `T`, trong khi bất kỳ nhánh nào tự quay lại `T` qua giới hạn generic sẽ vẫn được gắn nhãn `CYCLE`.

Trong ứng dụng thực tế thường có hai nhu cầu:

| Mục tiêu | Dữ liệu theo dõi | Kết quả |
| --- | --- | --- |
| In cây giải thích từng nhánh | Tập `active` cho đường DFS | Gặp lại trên cùng đường: cycle; gặp ở nhánh khác: có thể in lại |
| Lập chỉ mục mỗi kiểu một lần | Tập `visited` toàn cục, có thể kèm `active` | Mỗi nút xử lý một lần; cần đánh dấu rõ cycle và repeated reference |
| Tránh tài nguyên quá mức | Đếm nút / giới hạn độ sâu độc lập | Chặn cây quá lớn, kể cả không có cycle |

Nếu chỉ cần suy ra một tính chất kiểu dạng boolean, có thể dùng memoization để tránh tính lại. Nhưng memoization cho thuật toán trên đồ thị có chu trình đòi hỏi phân biệt trạng thái **đang xử lý** và **đã hoàn tất**, vì khi quay lại một nút chưa hoàn tất, giá trị của nút đó có thể chưa được xác định.

## 8. Đường đi phức tạp: wildcard, mảng, nhiều giới hạn và owner type

Chu trình không phải lúc nào cũng có dạng thẳng `T -> Self<T> -> T`.

### Wildcard

```java
class Ranked<T extends Comparable<? super T>> {}
```

Đường duyệt:

```text
T
  -> Comparable<? super T>
       -> WildcardType (? super T)
            -> lower bound T
                 -> cycle
```

Nếu bộ duyệt xử lý `TypeVariable` và `ParameterizedType` mà bỏ qua `WildcardType.getLowerBounds()`, nó có thể không phát hiện đường chu trình này khi cần phân tích đầy đủ cấu trúc.

### Generic array

```java
class Arrays<T extends Comparable<T>> {
    T[] values;
}
```

`Field.getGenericType()` trên `values` trả về `GenericArrayType` với component `T`. Nếu tiếp tục mở bounds của `T`, ta gặp `Comparable<T>` và vòng tham chiếu. Vì vậy cần xử lý `getGenericComponentType()`; còn với `Class<?>` đại diện kiểu mảng cụ thể như `String[]` thì dùng `Class.getComponentType()`.

### Multiple bounds

```java
class Multi<T extends Comparable<T> & java.io.Serializable> {}
```

`TypeVariable.getBounds()` trả mảng các giới hạn. Bound `Comparable<T>` có đường quay về `T`, còn bound `Serializable` là nhánh riêng không có chu trình. Dùng `active` theo nhánh giúp duyệt được cả hai và không đánh dấu nhầm `Serializable`.

### Owner type của generic nested class

`ParameterizedType.getOwnerType()` có thể chứa kiểu chủ sở hữu của một inner/nested declaration. Chương trình mẫu tập trung vào đường recursion ở **type arguments/bounds** nên không tự đi qua owner type. Nếu xây dựng **bộ duyệt mọi cạnh của đồ thị kiểu**, hãy kiểm tra `getOwnerType() != null` và gọi `walk(owner, ...)` qua **cùng tập `active`**, đúng quy tắc chống chu trình.

Không nên gọi tất cả API Reflection không liên quan đến cấu trúc generic, chẳng hạn duyệt mọi method/field của `Class` từ một `ParameterizedType`, trừ khi bài toán thực sự yêu cầu. Việc mở rộng đồ thị vô ý vừa tăng số nút vừa dễ sinh chu trình không cần thiết.

## 9. Hai cách phòng vệ bổ sung: giới hạn độ sâu và ngân sách nút

Phát hiện chu trình bằng `active` giải quyết **đường quay lại nút cũ**, nhưng một đồ thị rất sâu hoặc quá lớn vẫn có thể gây vấn đề hiệu năng và bộ nhớ, dù không có cycle. Với công cụ đọc generic từ plugin hoặc thư viện lạ, nên cân nhắc:

- `maxDepth`: từ chối duyệt sâu hơn mức được cấu hình; thông báo rõ `[max depth reached]`.
- `maxNodes`: đếm lượng nút đã ghé qua và ngừng nếu vượt ngân sách; thông báo `[node budget exceeded]`.
- Cache/memoization: nếu tính lại cùng một kiểu qua nhiều nhánh tốn kém, có thể lưu kết quả đã hoàn tất.
- Kiểm tra dữ liệu từ implementation `Type` tùy chỉnh: chỉ sử dụng giả định `equals()/hashCode()` đúng hợp đồng; tránh gọi `toString()` phức tạp không kiểm soát nếu object do thư viện cung cấp.

**Không dùng `maxDepth` làm cách duy nhất phát hiện cycle.** Nếu cutoff ở độ sâu 10, nó chỉ che chu trình sau mười bước và còn có thể cắt bỏ cấu trúc generic hợp lệ. Nên có cả cơ chế nhận diện chu trình và giới hạn tài nguyên độc lập, với dấu hiệu dừng khác nhau để người dùng hiểu vì sao dữ liệu bị rút gọn.

Ngoài ra, đừng nuốt `StackOverflowError` để làm tín hiệu dừng. Khi đó stack đã cạn, và lỗi có thể đến từ vấn đề khác. Phát hiện chu trình **trước khi** gọi đệ quy mới là cách tiếp cận đúng.

## 10. Các lỗi thường gặp khi viết bộ phân giải generic

**Lỗi 1 — Coi generic metadata là cây.** Việc một `TypeVariable` xuất hiện lại trong bounds tạo đồ thị có chu trình. Dùng DFS với tập `active`, không duyệt mù.

**Lỗi 2 — Kiểm tra chỉ bằng tên biến kiểu.** Hai biến `T` trong hai class/method khác nhau có tên trùng nhưng không cùng nút. Dùng danh tính ngữ nghĩa theo `TypeVariable` và `GenericDeclaration`.

**Lỗi 3 — So sánh chỉ bằng object identity.** Reflection có thể trả các object `TypeVariable` tương đương nhưng không phải cùng tham chiếu. Với implementation chuẩn, dùng `equals()/hashCode()`.

**Lỗi 4 — Không dọn `active` khi thoát.** Nếu thiếu `finally`, ngoại lệ khi duyệt một nhánh có thể để lại trạng thái sai, hoặc kiểu dùng ở nhánh khác bị đánh dấu nhầm. Chỉ xóa khi chính lần gọi này đã thêm nút thành công.

**Lỗi 5 — Bỏ sót các cạnh `WildcardType` / `GenericArrayType`.** Chu trình có thể đi qua `? super T`, `? extends T` và component array. Muốn phân tích đầy đủ thì phải duyệt các edges liên quan.

**Lỗi 6 — Hiểu `getBounds()` là danh sách superclass thuần túy.** Bounds có thể là interface, parameterized type, nhiều giới hạn, hoặc `Object` khi biến không khai báo bound tường minh. Mỗi phần tử là `Type`, cần phân nhánh theo runtime interface của nó.

**Lỗi 7 — Dùng `ParameterizedType.getRawType()` như đã phân giải đầy đủ.** `Self<T>` có raw type là `Self`, nhưng chu trình nằm trong **actual type argument `T`**. Chỉ lấy raw class sẽ bỏ qua thông tin generic.

**Lỗi 8 — Tự thay `T` bằng `Self<T>` không có điểm dừng.** Một thuật toán substitution phải có quy tắc xử lý biến kiểu đang phân giải (ví dụ giữ tham chiếu, đánh dấu deferred hoặc trả cấu trúc chu trình) thay vì thay thế mãi. Không thể mong tự mở rộng thành một biểu thức hữu hạn với vô số `Self<Self<...>>`.

## 11. Dùng trong ứng dụng: resolver, schema generator và serializer

Các công cụ như object mapper, dependency injection framework, schema generator hoặc trình tạo tài liệu API thường phải đọc những cấu trúc kiểu phức tạp. Một class theo mẫu `Node<N extends Node<N>>` không hiếm. Nếu generator tạo schema đệ quy không kiểm soát, nó có thể lặp vô hạn hoặc tạo JSON schema khổng lồ.

Một chiến lược rõ ràng là:

1. **Phân biệt tham chiếu với khai triển:** Khi gặp lại biến kiểu đang xử lý, tạo một dấu tham chiếu ổn định như `TypeVariable(T, declaringElement)` thay vì mở lại bounds.
2. **Xử lý các kiểu theo cấu trúc:** Duyệt `TypeVariable`, arguments của `ParameterizedType`, upper/lower bounds của `WildcardType` và component của `GenericArrayType`.
3. **Ghi nhận cycle có chủ đích:** Trong trình hiển thị có thể in `[cycle]`; trong schema có thể dùng tham chiếu schema phù hợp để bảo toàn cấu trúc.
4. **Tôn trọng scope khai báo:** Hai `T` thuộc hai class khác nhau không được gộp thành một nút schema vì trùng tên.
5. **Giới hạn tài nguyên độc lập:** Cung cấp cảnh báo khi vượt độ sâu hoặc ngân sách, tránh trả kết quả trông như đầy đủ dù đã bị cắt.

Nếu mục tiêu là kiểm tra một bound cụ thể, đôi khi không cần duyệt toàn bộ đồ thị. Ví dụ kiểm tra raw bound `Comparable` có thể đọc `ParameterizedType.getRawType()` rồi dừng; nếu cần đối số `T` thì mở rộng có kiểm soát. Tối giản phạm vi duyệt giúp tránh công việc thừa.

## 12. Câu hỏi tự kiểm tra

**1. Vì sao `Enum<E extends Enum<E>>` không gây lỗi compile, nhưng có thể làm mã Reflection duyệt sai bị vô hạn?**

Vì khai báo F-bound hợp lệ; lỗi xảy ra khi một thuật toán metadata liên tục đi từ `E` đến `Enum<E>` rồi trở lại `E` mà không nhận diện chu trình.

**2. Dùng `Set<String> seenNames` với mỗi tên `"T"` để chống cycle đã đủ chưa?**

Chưa. Biến `T` của hai generic declarations khác nhau là hai biến độc lập. Cần dùng `Type` với semantics nhận diện phù hợp, hoặc khóa gồm khai báo và tên.

**3. Vì sao code cần `active.remove(type)` trong `finally`?**

Để nhánh duyệt sau không kế thừa trạng thái của nhánh trước và để dọn trạng thái ngay cả khi xử lý children ném exception. Khi một nút lặp lại trên cùng nhánh, ta phát hiện cycle và return **trước khi thêm lần nữa**, nên không được xóa nhầm nút của ancestor.

**4. Với `T extends Comparable<? super T>`, cần theo dõi những loại `Type` nào để thấy chu trình?**

`TypeVariable` của `T` → `ParameterizedType` của `Comparable<? super T>` → `WildcardType` của `? super T` → lower bound là `T`.

**5. Vì sao cutoff độ sâu 10 không phải lời giải hoàn chỉnh?**

Vì nó có thể cắt bỏ một cấu trúc không có cycle nhưng sâu hơn 10; đồng thời không cho biết điểm nào thật sự trùng lặp. Giới hạn tài nguyên nên kết hợp với cycle detection.

## Ghi nhớ

- F-bounded generics như `T extends Self<T>` và `E extends Enum<E>` là **hợp lệ**.
- `TypeVariable.getBounds()` và `ParameterizedType.getActualTypeArguments()` có thể dẫn vòng qua cùng một biến kiểu.
- **Duyệt `Type` như đồ thị:** phát hiện một kiểu đã có trên đường đệ quy hiện tại, đánh dấu `CYCLE` và ngừng mở rộng nhánh đó.
- **Dùng `Set<Type> active`**, so sánh theo ngữ nghĩa `equals/hashCode` của Reflection types; dọn tập khi rời nhánh bằng `finally`.
- Kiểm tra cả `WildcardType`, `GenericArrayType` và các cạnh liên quan; đặt thêm giới hạn độ sâu và số nút khi cần để bảo vệ tài nguyên.
- Không thay thế F-bounds một cách vô hạn; nếu cần mô hình hóa, hãy giữ tham chiếu tới biến kiểu hay nút schema đã biết.

Điểm mấu chốt là **bản thân kiểu tự tham chiếu không sai; thuật toán mở rộng metadata không nhận diện chu trình mới là vấn đề**.


</details>

- [Quay lại đầu trang](#back-to-top)

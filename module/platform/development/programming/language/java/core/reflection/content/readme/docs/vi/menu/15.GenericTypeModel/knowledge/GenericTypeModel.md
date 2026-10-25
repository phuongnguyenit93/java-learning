<a id="back-to-top"></a>

# Mô hình generic

## Menu
- [Class khác Type](#generic-type-model-001)
- [ParameterizedType và actual type arguments](#generic-type-model-002)
- [Raw type, owner type và kiểu lồng có generic](#generic-type-model-003)
- [TypeVariable và GenericDeclaration](#generic-type-model-004)
- [getBounds và nhiều giới hạn kiểu](#generic-type-model-005)
- [WildcardType với upper bound và lower bound](#generic-type-model-006)
- [GenericArrayType khác Class của mảng cụ thể](#generic-type-model-007)
- [getGenericSuperclass và getGenericInterfaces](#generic-type-model-008)
- [getGenericType, getGenericReturnType và getGenericExceptionTypes](#generic-type-model-009)
- [Đọc tên kiểu bằng getTypeName thay vì ép mọi Type sang Class](#generic-type-model-010)
- [Phân biệt metadata generic trong khai báo với thông tin bị xóa lúc chạy](#generic-type-model-011)

## <a id="generic-type-model-001">Class khác Type</a>

<details>
<summary>Xem chi tiết</summary>

# Class khác Type: mô hình kiểu generic trong Java Reflection

## 1. Vì sao chỉ dùng Class<?> là chưa đủ?

Trong Java, `Class<?>` mô tả một lớp, interface, enum, record, kiểu nguyên thủy hoặc kiểu mảng mà JVM biết ở thời điểm chạy. Chẳng hạn:

~~~java
Class<?> stringType = String.class;
Class<?> listType = java.util.List.class;
Class<?> arrayType = String[].class;
~~~

Nhưng khi xét Java Generics, có những cách viết kiểu mang thêm thông tin mà chỉ một đối tượng `Class<?>` không diễn đạt đủ:

~~~java
java.util.List<String>
java.util.List<? extends Number>
T
T[]
java.util.List<String>[]
~~~

Hai khai báo `List<String>` và `List<Integer>` đều có **raw class** là `List.class`, nhưng phần tử kiểu (`String` hay `Integer`) khác nhau. Muốn đọc **chữ ký generic** còn được lưu ở class file, Reflection cung cấp giao diện `java.lang.reflect.Type` và một số dạng triển khai của nó.

**Điểm cốt lõi:** `Class<?>` **là một dạng `Type`**, nhưng **không phải mọi `Type` đều là `Class<?>`**. Một `Type` có thể biểu diễn một lớp thông thường, một generic type đã được tham số hóa, biến kiểu, wildcard hoặc kiểu mảng có thành phần là kiểu generic.

Bài này giúp bạn biết khi nào dùng `Class<?>`, khi nào cần `Type`, cách phân loại các dạng `Type`, và hiểu điều gì thật sự còn tồn tại sau **type erasure**.

## 2. So sánh Class<?> và Type

| Câu hỏi | `Class<?>` | `Type` |
| --- | --- | --- |
| Vai trò | Đại diện cho một kiểu runtime đã được JVM nhận diện | Giao diện chung mô tả các kiểu trong metadata Java, bao gồm chữ ký generic |
| Có biểu diễn `String.class` không? | Có | Có, vì `Class` triển khai `Type` |
| Có chứa đối số `String` của `List<String>` không? | Không: `List.class` chỉ là kiểu thô | Có thể, qua `ParameterizedType` |
| Có thể là một biến kiểu `T` không? | Không có `Class` riêng cho mỗi `T` | Có, qua `TypeVariable<?>` |
| Có biểu diễn `? extends Number` không? | Không | Có, qua `WildcardType` |
| Có biểu diễn `T[]` không? | `Class` chỉ đại diện cho dạng mảng sau xóa kiểu | Có, qua `GenericArrayType` |
| Có các API như `getDeclaredMethods()`, `getConstructor()`, `isAssignableFrom()`? | Có | Không; `Type` chỉ có API chung như `getTypeName()` |

`Type` là một **interface**, không phải “Class nâng cấp” có tất cả hàm của `Class`. Nếu bạn có một biến kiểu `Type`, không thể gọi trực tiếp `type.getDeclaredMethods()` mà chưa chứng minh nó là `Class<?>`.

~~~java
Type type = String.class;
if (type instanceof Class<?> clazz) {
    System.out.println(clazz.getSimpleName()); // String
}
~~~

Đoạn này minh họa với import `java.lang.reflect.Type`. Pattern matching cho `instanceof` được hỗ trợ trong Java 17.

Quan hệ quan trọng:

~~~text
                    java.lang.reflect.Type (interface)
                                 |
        +------------------------+--------------------------+
        |                        |                          |
   Class<?>                 ParameterizedType           TypeVariable<?>
        |                        |                          |
   String.class           List<String>                    T
   List.class             Map<String,Integer>             E
   String[].class
        |
        +------------------------+--------------------------+
                                 |                          |
                         GenericArrayType             WildcardType
                         T[] / List<String>[]          ? extends Number
                                                       ? super Integer
~~~

Sơ đồ trên biểu diễn **các loại đối tượng Type có thể nhận được** chứ không phải cây kế thừa class cụ thể; các nhánh cuối là interface chuyên biệt cùng nằm dưới `Type`.

## 3. Năm dạng Type cần nhận diện

### 3.1. Class<?> — kiểu JVM thông thường

~~~java
Type a = String.class;         // Class
Type b = int.class;            // Class
Type c = String[].class;       // Class
Type d = java.util.List.class; // Class
~~~

`String[]` dù là kiểu mảng vẫn có `Class` runtime thật. Nếu component là một kiểu reifiable cụ thể như `String`, Java biết được class mảng tương ứng ở runtime.

Các API `Class` rất hữu ích khi bạn cần lấy constructor, method, field, kiểm tra `isInstance`/`isAssignableFrom` hoặc tạo mảng qua Reflection. `Class<?>` thích hợp với **kiểu runtime** và **kiểu đã bị xóa tham số generic**.

### 3.2. ParameterizedType — kiểu có đối số generic cụ thể

Ví dụ `List<String>`, `Map<String, Integer>` hoặc `List<? extends Number>` khi xuất hiện trong metadata khai báo:

~~~java
Type type = Sample.class.getDeclaredField("names").getGenericType();
if (type instanceof ParameterizedType p) {
    Type raw = p.getRawType();                 // List
    Type[] arguments = p.getActualTypeArguments(); // [String]
    Type owner = p.getOwnerType();            // null cho List<String>
}
~~~

Đây là mã minh họa giả định `Sample` khai báo `List<String> names`; chương trình đầy đủ ở phần 7.

- `getRawType()`: kiểu thô, thường là `Class<?>` như `List.class`.
- `getActualTypeArguments()`: các đối số kiểu theo đúng thứ tự khai báo; **mỗi phần tử là `Type`**, có thể là `Class`, `ParameterizedType`, `TypeVariable` hoặc `WildcardType`.
- `getOwnerType()`: kiểu chứa đối với một số kiểu thành viên lồng nhau; có thể là `null` khi không có owner liên quan.

Với `Map<String, List<Integer>>`, đối số thứ hai tự nó là một `ParameterizedType`. Không được giả định mọi phần tử trong `getActualTypeArguments()` đều có thể ép sang `Class<?>`.

### 3.3. TypeVariable<?> — biến kiểu T, E, K, V

Ví dụ:

~~~java
class Box<T extends Number> {
    T value;
}
~~~

`value.getGenericType()` cho ra `TypeVariable` mang tên `T`. `value.getType()` trả về **`Number.class`**, vì `Number` là giới hạn trên đầu tiên và dùng làm kiểu sau xóa (erasure).

Các API đáng chú ý:

- `getName()` trả tên biến kiểu, chẳng hạn `"T"`.
- `getBounds()` trả các giới hạn trên dạng `Type[]`; nếu không khai báo giới hạn, giới hạn mặc định là `Object`.
- `getGenericDeclaration()` cho biết `Class`, `Method` hoặc `Constructor` nào khai báo biến kiểu.
- `Class#getTypeParameters()` và `Method#getTypeParameters()` liệt kê các biến kiểu do chính đối tượng tương ứng khai báo.

Cẩn thận: `T` trong `class Box<T>` **không tự trở thành `String.class`** chỉ vì một object nào đó được gán cho biến nguồn `Box<String>`. Metadata khai báo `Box<T>` vẫn chứa `T`. Để biết một subclass khai báo rõ `extends Box<String>`, cần đọc `getGenericSuperclass()` của **subclass**, không dựa vào riêng `Box.class.getTypeParameters()`.

### 3.4. WildcardType — ký hiệu ? và các giới hạn

Ví dụ:

~~~java
List<?> anything;
List<? extends Number> numbers;
List<? super Integer> targets;
~~~

Bên ngoài, cả ba kiểu trường đều thường là `ParameterizedType` có raw type `List`. **WildcardType nằm bên trong danh sách type arguments**, chẳng hạn phần tử đầu tiên của `List<? extends Number>`.

- `getUpperBounds()` lấy giới hạn trên. Với `? extends Number`, đó là `Number`; với `?` thông thường là `Object`.
- `getLowerBounds()` lấy giới hạn dưới. Với `? super Integer`, đó là `Integer`; nếu không có giới hạn dưới, mảng này rỗng.
- Không lấy được wildcard bằng cách gọi `getGenericType()` trên trường `List<? extends Number>` rồi ép ngay sang `WildcardType`; cần đi qua `ParameterizedType#getActualTypeArguments()`.

Wildcard là một thành phần của **kiểu khai báo**, không phải một class có thể nạp bằng `Class.forName("? extends Number")`.

### 3.5. GenericArrayType — mảng có component generic

Ví dụ:

~~~java
class Bag<T> {
    T[] history;
    List<String>[] groups;
    String[] labels;
}
~~~

Khi phản chiếu generic type của từng field:

| Trường | `getType()` | `getGenericType()` |
| --- | --- | --- |
| `T[] history`, với `T extends Number` | `Number[].class` | `GenericArrayType` mô tả `T[]` |
| `List<String>[] groups` | `List[].class` | `GenericArrayType` mô tả `List<String>[]` |
| `String[] labels` | `String[].class` | `Class<?>` đại diện cho `String[]` |

`GenericArrayType#getGenericComponentType()` trả `Type` của **thành phần mảng**. Đối với `T[]`, thành phần là `TypeVariable`; đối với `List<String>[]`, thành phần là `ParameterizedType`.

**Không phải mọi mảng đều là GenericArrayType**. Mảng của một kiểu runtime reifiable như `String[]` hay `int[]` thường được biểu diễn bằng `Class<?>`.

## 4. Reflection API nào trả Class, API nào trả Type?

Các cặp API này dễ bị lẫn:

| Nhu cầu | API kiểu thô / erasure | API giữ metadata generic |
| --- | --- | --- |
| Kiểu của field | `Field.getType() -> Class<?>` | `Field.getGenericType() -> Type` |
| Kiểu trả về method | `Method.getReturnType() -> Class<?>` | `Method.getGenericReturnType() -> Type` |
| Các kiểu tham số method/constructor | `getParameterTypes() -> Class<?>[]` | `getGenericParameterTypes() -> Type[]` |
| Exception khai báo của method/constructor | `getExceptionTypes() -> Class<?>[]` | `getGenericExceptionTypes() -> Type[]` |
| Lớp cha trực tiếp | `Class.getSuperclass() -> Class<?>` | `Class.getGenericSuperclass() -> Type` |
| Các interface trực tiếp | `Class.getInterfaces() -> Class<?>[]` | `Class.getGenericInterfaces() -> Type[]` |
| Biến kiểu do class/method/constructor khai báo | Không có phép suy từ mỗi `Class<?>` thô | `getTypeParameters() -> TypeVariable<?>[]` |

Ví dụ:

~~~java
class Store {
    List<String> customers;
    List<String> load() { return customers; }
}
~~~

- `customers`: `getType() == List.class`, còn `getGenericType().getTypeName()` là `java.util.List<java.lang.String>`.
- `load()`: `getReturnType() == List.class`, còn `getGenericReturnType()` có thể mô tả đầy đủ `List<String>`.
- Nếu method có tham số `Map<String, Integer>`, dùng `getGenericParameterTypes()` để đọc các type argument. `getParameterTypes()` chỉ chứa `Map.class`.

**Lưu ý về tên:** `getTypeName()` cung cấp biểu diễn văn bản của `Type` phục vụ hiển thị/chẩn đoán. Để xử lý kiểu một cách có cấu trúc, hãy dùng `instanceof` và các phương thức chuyên biệt thay vì tách chuỗi `"<...>"` bằng regex.

## 5. Type erasure: metadata generic còn gì sau biên dịch?

Java Generics chủ yếu dùng **type erasure** để tương thích bytecode. Trong nhiều vị trí runtime, JVM thao tác với raw type và giới hạn trên của type variable. Ví dụ:

~~~java
List<String> words = new ArrayList<>();
List<Integer> numbers = new ArrayList<>();

System.out.println(words.getClass() == numbers.getClass()); // true
~~~

(Đoạn minh họa cần import `java.util.List` và `java.util.ArrayList`.)

Nếu hai object trên đều là `ArrayList`, `getClass()` không chứa thông tin `String` hay `Integer`. Java cũng không có cú pháp class literal `List<String>.class`. Hai dòng này có nội dung khác nhau:

~~~java
Class<?> raw = List.class; // đại diện kiểu runtime List
Type metadata = Store.class
        .getDeclaredField("customers").getGenericType(); // List<String>
~~~

Vậy tại sao `Field.getGenericType()` vẫn thấy `String`?

- Trình biên dịch Java có thể ghi **generic signature metadata** cho các khai báo như field, method, superclass và interface vào class file.
- Reflection đọc metadata này và xây dựng `ParameterizedType`, `TypeVariable`, `WildcardType` hoặc `GenericArrayType` tương ứng.
- Metadata mô tả **kiểu được khai báo tại vị trí đó**, không chứng minh kiểu runtime thực của toàn bộ object hoặc từng phần tử dữ liệu.

Ví dụ, field khai báo `List<String> customers` có chữ ký chứa `String`. Nhưng một object `new ArrayList<String>()` không tự mang “chứng chỉ runtime rằng mọi phần tử luôn là String”. Nếu mã dùng raw type, unchecked cast hoặc reflection để đưa sai giá trị vào collection, generic signature của field vẫn giữ nguyên.

**Giới hạn cần nhớ:** Không thể dùng `obj.getClass()` để khôi phục đối số generic đã bị xóa của một đối tượng `new ArrayList<String>()` chỉ dựa trên object đó. Muốn giữ thông tin kiểu cho serialization/deserialization, thường phải cung cấp thêm một đối tượng mô tả kiểu hoặc dùng kỹ thuật **type token**.

## 6. Cách duyệt cấu trúc Type đúng và an toàn

Một tiện ích phân loại dạng Type có thể viết:

~~~java
static String kind(Type type) {
    if (type instanceof Class<?>) return "Class";
    if (type instanceof ParameterizedType) return "ParameterizedType";
    if (type instanceof TypeVariable<?>) return "TypeVariable";
    if (type instanceof GenericArrayType) return "GenericArrayType";
    if (type instanceof WildcardType) return "WildcardType";
    throw new IllegalArgumentException("Unknown Type kind: " + type);
}
~~~

Đây là phương thức sẽ xuất hiện trong chương trình hoàn chỉnh phần 7, với import `java.lang.reflect.*`.

Một quy trình đọc kiểu có cấu trúc:

1. Nhận `Type` qua `getGenericType()`, `getGenericReturnType()`, `getGenericSuperclass()` hoặc nguồn metadata phù hợp.
2. Nếu `type instanceof Class<?>`: đây là lớp/interface/primitive/array runtime. Với array class có thể dùng `clazz.getComponentType()`.
3. Nếu `ParameterizedType`: đọc `getRawType()`, duyệt **đệ quy** từng phần tử `getActualTypeArguments()` và, khi liên quan, xét `getOwnerType()`.
4. Nếu `TypeVariable<?>`: đọc `getName()`, `getBounds()` và `getGenericDeclaration()`. Đừng mặc định biến kiểu đã được thay bằng một Class cụ thể.
5. Nếu `WildcardType`: đọc cả `getUpperBounds()` và `getLowerBounds()`.
6. Nếu `GenericArrayType`: tiếp tục xét `getGenericComponentType()`; component lại có thể là một dạng Type khác.

Ví dụ với `Map<String, List<? extends Number>>`, cấu trúc logic là:

~~~text
ParameterizedType: Map<String, List<? extends Number>>
  rawType: Class(Map)
  argument[0]: Class(String)
  argument[1]: ParameterizedType: List<? extends Number>
    rawType: Class(List)
    argument[0]: WildcardType: ? extends Number
      upperBounds: Class(Number)
~~~

Cây ví dụ giúp thấy vì sao đoạn code `(Class<?>) p.getActualTypeArguments()[1]` có thể ném `ClassCastException`: phần tử thứ hai là `ParameterizedType` chứ không phải Class.

Khi xử lý thư viện thực tế, có thể gặp generic signature thiếu hoặc không hợp lệ do class file được sinh/thay đổi. Một số thao tác Reflection liên quan generic có thể gây `TypeNotPresentException`, `MalformedParameterizedTypeException` hoặc `GenericSignatureFormatError`. Hãy dùng dữ liệu type có cấu trúc khi có thể và báo lỗi metadata rõ ràng.

## 7. Chương trình Java 17+ đầy đủ: quan sát Class và các dạng Type

Tạo **một tệp** tên `ClassVsTypeDemo.java`:

~~~java
import java.lang.reflect.*;
import java.util.List;

public class ClassVsTypeDemo {
    static class Box<T extends Number> {
        List<String> names;
        T value;
        T[] history;
        List<? extends Number> rates;
        List<String>[] groups;
        String[] labels;
    }

    static class IntegerBox extends Box<Integer> { }

    static abstract class TypeCapture<T> {
        Type capture() {
            Type parent = getClass().getGenericSuperclass();
            return ((ParameterizedType) parent).getActualTypeArguments()[0];
        }
    }

    static String kind(Type type) {
        if (type instanceof Class<?>) return "Class";
        if (type instanceof ParameterizedType) return "ParameterizedType";
        if (type instanceof TypeVariable<?>) return "TypeVariable";
        if (type instanceof GenericArrayType) return "GenericArrayType";
        if (type instanceof WildcardType) return "WildcardType";
        throw new IllegalArgumentException("Unknown Type kind: " + type);
    }

    static void describe(String name) throws ReflectiveOperationException {
        Field field = Box.class.getDeclaredField(name);
        Type generic = field.getGenericType();
        System.out.println(name + ": raw=" + field.getType().getTypeName()
                + ", generic=" + generic.getTypeName()
                + ", kind=" + kind(generic));
    }

    public static void main(String[] args) throws Exception {
        for (String name : new String[] {
                "names", "value", "history", "rates", "groups", "labels" }) {
            describe(name);
        }

        ParameterizedType listType = (ParameterizedType)
                Box.class.getDeclaredField("names").getGenericType();
        System.out.println("names argument="
                + listType.getActualTypeArguments()[0].getTypeName());

        ParameterizedType rateType = (ParameterizedType)
                Box.class.getDeclaredField("rates").getGenericType();
        WildcardType wildcard = (WildcardType)
                rateType.getActualTypeArguments()[0];
        System.out.println("rates upper="
                + wildcard.getUpperBounds()[0].getTypeName());

        TypeVariable<?> t = Box.class.getTypeParameters()[0];
        System.out.println("T bound=" + t.getBounds()[0].getTypeName());

        ParameterizedType parent = (ParameterizedType)
                IntegerBox.class.getGenericSuperclass();
        System.out.println("IntegerBox parent arg="
                + parent.getActualTypeArguments()[0].getTypeName());

        Type captured = new TypeCapture<List<String>>() { }.capture();
        System.out.println("captured=" + captured.getTypeName());
    }
}
~~~

Biên dịch và chạy trong thư mục chứa `ClassVsTypeDemo.java`:

~~~bash
javac --release 17 ClassVsTypeDemo.java
java ClassVsTypeDemo
~~~

**Stdout đã kiểm chứng bằng JDK 21 với `--release 17`:**

~~~text
names: raw=java.util.List, generic=java.util.List<java.lang.String>, kind=ParameterizedType
value: raw=java.lang.Number, generic=T, kind=TypeVariable
history: raw=java.lang.Number[], generic=T[], kind=GenericArrayType
rates: raw=java.util.List, generic=java.util.List<? extends java.lang.Number>, kind=ParameterizedType
groups: raw=java.util.List[], generic=java.util.List<java.lang.String>[], kind=GenericArrayType
labels: raw=java.lang.String[], generic=java.lang.String[], kind=Class
names argument=java.lang.String
rates upper=java.lang.Number
T bound=java.lang.Number
IntegerBox parent arg=java.lang.Integer
captured=java.util.List<java.lang.String>
~~~

### Giải thích bằng bằng chứng trong output

**`names`:** Kiểu runtime là `List`. Generic signature giữ `String`, nên `getGenericType()` là `ParameterizedType` và `getActualTypeArguments()[0]` là `String.class`.

**`value`:** Trong khai báo `Box<T extends Number>`, `T` vẫn là biến kiểu ở metadata. `Field.getType()` cho kiểu sau erasure `Number.class`, trong khi `getGenericType()` trả `TypeVariable` mang tên `T`. Giới hạn trên được in thành `Number`.

**`history`:** Với field `T[]`, raw runtime array có component erasure `Number`, nhưng generic signature giữ component `T`, do đó `GenericArrayType`.

**`rates`:** Bên ngoài là `List<? extends Number>`, một `ParameterizedType`. Type argument nằm bên trong là `WildcardType`, có upper bound `Number`.

**`groups` và `labels`:** `List<String>[]` là `GenericArrayType` vì component `List<String>` có tham số generic. `String[]` lại là một `Class` runtime. Đây là một trong các phép so sánh dễ thấy nhất của `Class` và `Type`.

**`IntegerBox`:** Đối tượng `IntegerBox.class.getGenericSuperclass()` trả về chữ ký `Box<Integer>` trong metadata của subclass, vì vậy có thể đọc `Integer`. Điều này **khác với** việc lấy một object của `Box` không có subclass xác định và hỏi `getClass()` xem tham số `T` là gì.

**`TypeCapture`:** Anonymous subclass có khai báo generic superclass chứa `List<String>`. Phương thức `capture()` đọc chữ ký superclass đó để thu được `ParameterizedType`, giữ lại đối số `String`.

Phần output được xây từ tên kiểu chuẩn của các Type, không in tên lớp triển khai nội bộ `sun.reflect.generics.reflectiveObjects.*`, vì tên triển khai nội bộ không phải hợp đồng API công khai.

## 8. Type token: giữ thông tin List<String> khi API cần Type

Đôi khi API nhận một tham số kiểu chung để cấu hình parser, JSON serializer, dependency injection hoặc converter:

~~~java
void register(Type type) {
    // Đọc cấu trúc Type để biết kiểu dữ liệu cần xử lý.
}
~~~

Đây là ví dụ chữ ký minh họa, không phải chương trình độc lập.

Gọi `register(List.class)` chỉ cung cấp raw class; thông tin `String` không còn. Một kỹ thuật thường dùng là **generic type token** thông qua anonymous subclass:

~~~java
abstract class TypeCapture<T> {
    Type capture() {
        Type parent = getClass().getGenericSuperclass();
        return ((ParameterizedType) parent).getActualTypeArguments()[0];
    }
}

Type listOfStrings = new TypeCapture<List<String>>() {}.capture();
System.out.println(listOfStrings.getTypeName());
// java.util.List<java.lang.String>
~~~

Đoạn minh họa cần import `java.lang.reflect.Type`, `java.lang.reflect.ParameterizedType` và `java.util.List`. Phương thức `capture()` dựa trên điều kiện **subclass thực sự khai báo superclass có tham số generic**, như anonymous subclass `new TypeCapture<List<String>>() {}`. Nếu tạo subclass raw hoặc thay cấu trúc kế thừa, việc ép `(ParameterizedType)` không còn được bảo đảm.

Vì sao cách này giữ được thông tin? Trình biên dịch ghi kiểu `List<String>` vào **generic superclass signature của anonymous subclass**, nên Reflection có thể đọc nó. Nó **không khôi phục** thông tin từ một `ArrayList<String>` object bình thường đã bị erasure.

Một số thư viện cung cấp sẵn API type token, ví dụ `TypeReference<T>` hoặc `TypeToken<T>`. Đừng giả định mọi thư viện dùng cùng API hoặc cùng quy tắc equality; ý tưởng chung là lưu `Type` đã mô tả đầy đủ vào thời điểm khai báo.

## 9. Phân biệt ba thông tin thường bị đánh đồng

### Kiểu runtime của đối tượng

~~~java
Object value = java.util.List.of("a", "b");
Class<?> runtimeClass = value.getClass();
~~~

`runtimeClass` mô tả **lớp triển khai thật sự** mà JVM tạo ra cho danh sách. Nó không nói `List<String>` từ biến nguồn. Với factory `List.of`, tên lớp triển khai là chi tiết nội bộ, không nên in/so sánh tên để lập luận về generic.

### Kiểu khai báo tại một vị trí

~~~java
class Response {
    List<String> values;
}
Type declaredType = Response.class.getDeclaredField("values").getGenericType();
~~~

Chữ ký field có thể cho biết `List<String>`. Nó là **metadata của khai báo**, không phải bằng chứng rằng danh sách đang chứa đúng kiểu phần tử tại mọi thời điểm.

### Kiểu thực tế sau khi liên kết generic qua kế thừa

~~~java
class Parent<T> { }
class Child extends Parent<String> { }
Type parentSignature = Child.class.getGenericSuperclass();
~~~

`parentSignature` là `ParameterizedType` chứa `String` vì mã nguồn của **Child** đã cố định tham số kế thừa. Nếu chuỗi kế thừa phức tạp hơn, để ánh xạ biến kiểu qua nhiều cấp thường phải duyệt và thế TypeVariable bằng type argument có liên quan; không có chuyện mỗi `getGenericSuperclass()` tự giải toàn bộ chuỗi kế thừa.

Ba mục tiêu này dẫn đến ba API khác nhau. Nên đặt câu hỏi rõ: cần biết class của object, kiểu field/method được khai báo, hay type argument của một superclass/interface?

## 10. Những lỗi thường gặp và cách khắc phục

| Cách hiểu hoặc đoạn code sai | Vì sao sai | Cách đúng |
| --- | --- | --- |
| `Class<?> c = (Class<?>) field.getGenericType();` cho mọi field | Với `List<String>` nó là `ParameterizedType`, không phải Class | Dùng `instanceof` phân nhánh theo dạng `Type` |
| So sánh `List<String>` và `List<Integer>` bằng `getType()` | Cả hai cho raw `List.class` | Dùng `getGenericType()` và đọc type arguments |
| `Class.forName("java.util.List<java.lang.String>")` | Chuỗi chứa generic signature, không phải binary name của class | Dùng `List.class` cho class, hoặc lấy `Type` từ signature/token |
| `List<String>.class` | Java không cho tạo class literal cho parameterized type | Dùng `List.class` hoặc type token khi cần `List<String>` |
| `(Class<?>) p.getActualTypeArguments()[0]` trong mọi trường hợp | Đối số có thể là `WildcardType`, `TypeVariable` hoặc `ParameterizedType` | Phân loại và xử lý đệ quy |
| Xem `TypeVariable<T>` là `T.class` | Không có class literal độc lập cho biến kiểu T | Đọc bounds và ngữ cảnh generic declaration |
| Cho rằng mọi kiểu mảng là `GenericArrayType` | `String[]` có runtime `Class` | Kiểm tra cả `Class#isArray()` và `GenericArrayType` |
| Dùng `object.getClass()` để lấy `List<String>` | Type erasure không lưu tham số generic như thuộc tính object | Giữ `Type` từ khai báo hoặc type token |
| Đoán cấu trúc từ `Type#getTypeName()` bằng tách dấu phẩy và góc | Generic type có thể lồng sâu, có owner/wildcard/array | Dùng `ParameterizedType`, `WildcardType` và API chuyên biệt |
| Cho rằng `getGenericType()` bảo đảm object chứa dữ liệu đúng generic | Metadata không tự kiểm tra phần tử ở runtime | Thực hiện kiểm tra dữ liệu ở tầng ứng dụng nếu cần |

### Type và AnnotatedType có giống nhau không?

Không. `Type` mô tả cấu trúc kiểu, còn `java.lang.reflect.AnnotatedType` cho phép kiểm tra **annotation gắn lên chính vị trí sử dụng kiểu** (*type-use annotation*). Ví dụ một field có annotation trên generic argument có thể cần `Field.getAnnotatedType()` để đọc annotation tương ứng. `AnnotatedType#getType()` trả lại đối tượng `Type` cơ sở. Đừng nhầm `Field.getAnnotations()` (annotation của declaration) với annotation của các nút bên trong type.

### Khi nào không cần generic metadata?

Nếu bài toán là gọi public method, khởi tạo đối tượng bằng constructor, kiểm tra `Class.isInstance` hoặc xử lý field `int`, `String`, việc dùng `Class<?>` thường đã đủ. Chỉ dùng chuỗi xử lý `ParameterizedType`, `TypeVariable` v.v. khi nghiệp vụ **thật sự cần phân tích chữ ký generic**.

## 11. Bài tập thực hành có đáp án

**Bài 1.** Với `List<String> names`, `field.getType()` và `field.getGenericType()` lần lượt là gì?

**Đáp án:** `List.class` và một `ParameterizedType` mô tả `List<String>`. Type argument đầu là `String.class`.

**Bài 2.** Với `class Box<T extends Number> { T value; }`, vì sao `getType()` trả `Number.class` thay vì `T.class`?

**Đáp án:** Sau type erasure, `T` có giới hạn trên là `Number`; `T` được giữ trong metadata generic dạng `TypeVariable` và không có class literal `T.class`.

**Bài 3.** Với `List<? super Integer> list`, đối tượng `Type` ngoài cùng và type argument bên trong thuộc dạng nào?

**Đáp án:** Ngoài cùng là `ParameterizedType`, argument là `WildcardType`. `getLowerBounds()[0]` cho `Integer.class`.

**Bài 4.** Vì sao `String[]` thường là `Class` trong khi `List<String>[]` thường là `GenericArrayType`?

**Đáp án:** Mảng `String[]` có component reifiable và có runtime class xác định. Component `List<String>` mang tham số generic, được biểu diễn trong generic signature; array generic đó cần `GenericArrayType`.

**Bài 5.** Một field khai báo `Map<String, List<Integer>>`. Có an toàn khi ép argument thứ hai của Map sang `Class<?>` không?

**Đáp án:** Không; argument thứ hai là một `ParameterizedType` mô tả `List<Integer>`. Cần duyệt lồng nhau.

**Bài 6.** `new ArrayList<String>().getClass()` có cho phép lấy `String.class` bằng một API trên Class của object không?

**Đáp án:** Không, generic argument đã bị erasure. Có thể giữ nó bằng generic field metadata hoặc một type token khai báo rõ `List<String>`.

**Bài 7.** Với `class Child extends Box<Integer>`, API nào giúp đọc `Integer` từ khai báo kế thừa?

**Đáp án:** `Child.class.getGenericSuperclass()`, sau đó nếu là `ParameterizedType` thì đọc `getActualTypeArguments()`.

**Bài 8.** Tại sao API serialization nên nhận `Type` thay vì chỉ nhận `Class<?>` khi cần phân tích `List<User>`?

**Đáp án:** `Class<?>` chỉ cho `List.class`; `Type` có thể giữ cả `User` bên trong generic signature/token, giúp serializer biết kiểu phần tử cần ánh xạ.

## 12. Tóm tắt để ghi nhớ

- **`Class<?>` là một `Type`**, nhưng `Type` có thể mang cấu trúc generic mà `Class<?>` không mô tả được.
- Năm dạng cốt lõi: **`Class`, `ParameterizedType`, `TypeVariable`, `WildcardType`, `GenericArrayType`**.
- **`getType()`** lấy class/erasure của field; **`getGenericType()`** có thể đọc generic signature của khai báo. Các cặp API tương tự áp dụng với method, constructor, superclass và interface.
- **Type erasure** không xóa mọi generic signature của các khai báo, nhưng nó ngăn việc suy ra type argument chỉ từ `object.getClass()` của một generic object thông thường.
- Khi phải truyền `List<String>` cho framework, hãy cung cấp `Type` giữ generic information từ metadata hoặc dùng type token; đừng cố tạo `List<String>.class`.
- Đọc `Type` bằng **phân nhánh và duyệt cấu trúc**, không ép mọi thứ sang `Class<?>` và không tự parse văn bản `getTypeName()`.

### Tài liệu Java chính thức

- [Java SE 17 — `java.lang.reflect.Type`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Type.html).
- [Java SE 17 — `ParameterizedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html).
- [Java SE 17 — `TypeVariable`, `WildcardType`, `GenericArrayType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/package-summary.html).
- [Java SE 17 — `Field.getGenericType()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html#getGenericType()).
- [JLS 17 §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-model-002">ParameterizedType và actual type arguments</a>

<details>
<summary>Xem chi tiết</summary>

# `ParameterizedType` và `getActualTypeArguments()` trong Java Reflection

Java Reflection không chỉ đọc được tên của một class. Khi **chữ ký generic** được lưu trong class file, Reflection còn có thể mô tả những kiểu được khai báo như `List<String>`, `Map<String, List<? extends Number>>`, hoặc `Box<Integer>` qua API `java.lang.reflect.Type`. Một dạng đặc biệt quan trọng là **`ParameterizedType`**: đại diện cho việc sử dụng một *generic type* với các *type arguments* cụ thể trong một chữ ký.

Ví dụ:

~~~java
Map<String, List<Integer>> values;
~~~

`Map` là **generic type được khai báo**, còn `String` và `List<Integer>` là **actual type arguments** trong lần sử dụng đó. Lời gọi:

~~~java
Type signature = field.getGenericType();
ParameterizedType map = (ParameterizedType) signature;
Type[] arguments = map.getActualTypeArguments();
~~~

cho phép đọc cấu trúc trên. `arguments[0]` là `String.class`; `arguments[1]` là **một `ParameterizedType` khác**, biểu diễn `List<Integer>`. **Không thể luôn ép phần tử của mảng `Type[]` thành `Class<?>`.**

Bài học tập trung vào **cách lấy `ParameterizedType`, ba phương thức chính (`getRawType`, `getActualTypeArguments`, `getOwnerType`), cách xử lý các dạng `Type` lồng nhau, owner của nested class, generic superclass/interface và giới hạn do type erasure**.

## 1. Phân biệt khai báo generic và một lần sử dụng kiểu có đối số

Xét khai báo:

~~~java
class Box<T> {
    T value;
}
~~~

`Box<T>` khai báo **type variable** tên `T`. Một nơi sử dụng có thể viết:

~~~java
Box<String> names;
Box<Integer> counts;
~~~

Tại đây, `String` và `Integer` là **actual type arguments** trong chữ ký đã khai báo. Khi kiểm tra metadata của hai field bằng `Field.getGenericType()`, ta có thể thu được lần lượt một `ParameterizedType` biểu diễn `Box<String>` và một `ParameterizedType` biểu diễn `Box<Integer>`.

Nhưng hai object được khởi tạo từ `new Box<String>()` và `new Box<Integer>()` đều có **runtime class** là `Box.class`. Vì **type erasure**, thông tin đối số kiểu của *lần khởi tạo object* nói chung không nằm trên object để có thể truy vấn trực tiếp bằng `object.getClass()`.

Phân biệt hai câu hỏi:

- **“Field/method/superclass này đã được *khai báo* với type arguments nào?”** → Reflection generic metadata, thường có `ParameterizedType`.
- **“Object `new ArrayList<String>()` đang chứa những phần tử kiểu nào, hay trước đây được tạo với `String`?”** → Không thể khẳng định chỉ bằng `object.getClass()` và `ParameterizedType`; phải có hợp đồng kiểu khác hoặc kiểm tra dữ liệu.

## 2. Nhận diện đối số bên trong `ParameterizedType`

Một `ParameterizedType` chứa `Type[]`, không phải `Class<?>[]`. Ví dụ `Map<String, List<? extends Number>>` có argument thứ nhất là `Class` (`String`), argument thứ hai là `ParameterizedType` (`List<? extends Number>`); bên trong `List` lại là `WildcardType`. Nếu argument là `T` hoặc `T[]`, nó có thể là `TypeVariable` hoặc `GenericArrayType`. Vì vậy việc đọc đối số của `ParameterizedType` phải giữ nguyên cấu trúc `Type` và kiểm tra `instanceof` ở từng cấp. Sơ đồ ở bài mở đầu giới thiệu đầy đủ năm dạng `Type`; ở đây trọng tâm là đường đi qua `getActualTypeArguments()`.

## 3. Tìm `ParameterizedType` từ Field, Method và quan hệ kế thừa

Các API cần nhớ:

| Vị trí chữ ký | API lấy generic metadata | Ví dụ |
| --- | --- | --- |
| Field | `Field.getGenericType()` | `Map<String, Long> mapping` |
| Tham số method | `Method.getGenericParameterTypes()` | `void save(List<String> rows)` |
| Kiểu trả về method | `Method.getGenericReturnType()` | `List<Integer> read()` |
| Superclass trực tiếp | `Class.getGenericSuperclass()` | `class StringBox extends Box<String>` |
| Interface trực tiếp | `Class.getGenericInterfaces()` | `class Repo implements Repository<User>` |
| Tham số constructor | `Constructor.getGenericParameterTypes()` | `new Holder(List<String> values)` |

Lưu ý **`getGenericType()` khác `getType()`**:

~~~java
Field field = Holder.class.getDeclaredField("records");

Class<?> erased = field.getType(); // Map.class
Type declared = field.getGenericType(); // Map<String, List<? extends Number>>
~~~

Đây là đoạn minh họa (cần class `Holder` có field `records`, import `java.lang.reflect.*` và xử lý `NoSuchFieldException`). `getType()` trả một `Class<?>` đại diện cho kiểu sau erasure; `getGenericType()` có thể trả `ParameterizedType`.

Với method, `getParameterTypes()` và `getReturnType()` cũng cho thông tin đã erasure; còn `getGenericParameterTypes()` và `getGenericReturnType()` giữ phần chữ ký generic nếu metadata hiện diện.

**Không phải kết quả nào của các API `getGeneric...` cũng là `ParameterizedType`**. Với field khai báo `List raw;`, `getGenericType()` trả `Class`. Với field `T data;` trong generic class, kết quả có thể là `TypeVariable`. Luôn kiểm tra biến thể.

## 4. Ba API chính của `ParameterizedType`

`ParameterizedType` có ba phương thức trọng tâm:

~~~java
Type rawType = parameterized.getRawType();
Type[] args = parameterized.getActualTypeArguments();
Type ownerType = parameterized.getOwnerType();
~~~

### 4.1. `getRawType()`

Trả về `Type` đại diện cho **generic type** mà ta đang truyền actual type arguments. Với `List<String>`, raw type thường là `List.class`; với `Map<String, Integer>` là `Map.class`.

Ví dụ:

~~~java
Type raw = map.getRawType();
if (raw instanceof Class<?> rawClass) {
    System.out.println(rawClass.getName()); // java.util.Map
}
~~~

Trong các trường hợp Java Reflection thông thường, raw type là `Class<?>`. API vẫn khai báo trả `Type`, nên code thư viện tổng quát nên kiểm tra biến thể trước khi ép kiểu. `getRawType()` **không trả type argument đầu tiên** và không đưa generic type argument thành một runtime class mới.

### 4.2. `getActualTypeArguments()`

Trả về một mảng **`Type[]`** biểu diễn các type arguments của **phần khai báo parameterized type hiện tại** theo thứ tự nguồn:

~~~java
Type[] arguments = map.getActualTypeArguments();
Type key = arguments[0];
Type value = arguments[1];
~~~

Với `Map<String, List<? extends Number>>`:

- `arguments[0]` biểu diễn `String`, tức `Class<String>`.
- `arguments[1]` biểu diễn `List<? extends Number>`, tức `ParameterizedType`.
- Từ `arguments[1]`, lấy tiếp `getActualTypeArguments()[0]` sẽ gặp `WildcardType`.

**Số type arguments không phải số field, method hay số lần tạo object.** Thông thường nó tương ứng với type parameters ở chính thành viên generic đang được parameterize. Có trường hợp nested type *không tự khai báo type parameters*, nhưng owner của nó là parameterized: `getActualTypeArguments()` của nested type có thể là **mảng rỗng**, trong khi `getOwnerType()` vẫn chứa thông tin kiểu của owner.

Mảng `Type[]` này mô tả metadata; **nó không cam kết `arguments[i] instanceof Class`**. Để xử lý kiểu phức tạp, cần phân loại `Class`, `ParameterizedType`, `TypeVariable`, `WildcardType` và `GenericArrayType`.

### 4.3. `getOwnerType()`

Cho biết **type sở hữu** (*owner*) nếu parameterized type là kiểu lồng nhau. Ví dụ:

~~~java
class Outer<X> {
    class Inner<Y> { }
}

Outer<String>.Inner<Integer> pair;
~~~

Với `pair`:

- `getRawType()` mô tả lớp `Inner`.
- `getActualTypeArguments()` của `Inner` chứa `Integer`.
- `getOwnerType()` mô tả `Outer<String>` — đây cũng là một `ParameterizedType`, có raw type `Outer` và actual argument `String`.

`getOwnerType()` **có thể trả `null`** đối với kiểu không có owner, chẳng hạn `List<String>`, `Map<K,V>` top-level. Ngược lại, không nên giả định owner luôn là `Class<?>`: owner cũng có thể chứa type arguments và lại là `ParameterizedType`. Với member class static, quan hệ owner có ngữ nghĩa khác với inner class không static; không suy từ owner rằng luôn tồn tại enclosing instance.

## 5. Đọc nested `ParameterizedType` và wildcard

Giả sử field:

~~~java
Map<String, List<? extends Number>> records;
~~~

Truy cập lớp ngoài rồi lớp trong:

~~~java
Type type = Holder.class.getDeclaredField("records").getGenericType();

if (!(type instanceof ParameterizedType map)) {
    throw new IllegalStateException("records is not parameterized");
}

Type[] args = map.getActualTypeArguments();
Type keyType = args[0]; // String.class
Type listType = args[1]; // List<? extends Number>

if (listType instanceof ParameterizedType list) {
    Type itemType = list.getActualTypeArguments()[0];
    if (itemType instanceof WildcardType wildcard) {
        Type[] upper = wildcard.getUpperBounds();
        Type[] lower = wildcard.getLowerBounds();
        // Ở đây upper chứa Number, lower rỗng.
    }
}
~~~

Đây là đoạn minh họa (cần `Holder.records` tương ứng, `import java.lang.reflect.*`, xử lý exception tra field). Sơ đồ:

~~~text
Map
├─ String
└─ List
   └─ ? extends Number
~~~

### Hai dạng wildcard quan trọng

| Chữ ký | Metadata cần đọc | Ý nghĩa |
| --- | --- | --- |
| `List<? extends Number>` | `WildcardType.getUpperBounds()` → `Number`; lower bounds rỗng | Phần tử có một upper bound là `Number` |
| `List<? super Integer>` | `getLowerBounds()` → `Integer`; upper bounds chứa `Object` | Wildcard có lower bound `Integer` |

Java Reflection phản ánh **giới hạn khai báo**, không chứng minh nội dung runtime của mọi phần tử của `List`. Với wildcard không có upper bound tường minh (hoặc dạng `? super ...`), `getUpperBounds()` thường trả `Object`.

Một wildcard có thể nằm sâu trong một parameterized argument, trong array hoặc trong superclass generic. Nếu viết một trình formatter/validator tổng quát, xử lý `Type` như một **cây đệ quy** thay vì chỉ một tầng `getActualTypeArguments()`.

## 6. `TypeVariable` và `GenericArrayType` không phải `ParameterizedType`

### Type variable

Ví dụ:

~~~java
class Bucket<T extends Number> {
    T number;
}
~~~

Với `Bucket.class.getDeclaredField("number").getGenericType()`, ta nhận một `TypeVariable<?>` đại diện cho `T`. Nó khai báo:

- `getName()`: tên `T`.
- `getBounds()`: các upper bounds; ở đây là `Number`.
- `getGenericDeclaration()`: class, method hoặc constructor khai báo biến kiểu đó.

`TypeVariable` **khác** actual type argument `Integer` trong field `Bucket<Integer> bucket;`. Nếu đang đọc một method kế thừa dùng type variable từ generic superclass, cần phân giải mapping từ superclass/interface cụ thể nếu muốn biết `T` tương ứng kiểu nào trong ngữ cảnh class con.

### Generic array

Java cho phép **khai báo tham chiếu** tới array có generic component, ví dụ:

~~~java
List<String>[] buckets;
~~~

Khi đọc `Field.getGenericType()` của field đó, kết quả có thể là `GenericArrayType`, với:

~~~java
Type component = ((GenericArrayType) type).getGenericComponentType();
// component mô tả List<String> và có thể là ParameterizedType
~~~

Điều này **không đồng nghĩa** Java cho phép tạo trực tiếp `new List<String>[10]`. Generic array creation với type component không reifiable bị compiler từ chối. Trong thực tế, nên ưu tiên `List<List<String>>` hoặc một cấu trúc phù hợp, tránh unchecked array cast khi không thật sự cần.

Ngược lại, `String[]` là một array class reifiable; `String[].class` là `Class<?>`. Vì thế parser không được xem **mọi array** là `GenericArrayType`.

## 7. Ví dụ hoàn chỉnh Java 17+: inspect generic type arguments

Tạo file **`ParameterizedTypeDemo.java`** trong một thư mục riêng và nhập toàn bộ chương trình sau. Nó kiểm chứng field lồng nhau, wildcard upper/lower bounds, generic array, owner type của inner class, generic superclass, raw declaration và chữ ký method.

~~~java
import java.lang.reflect.*;
import java.util.*;

public class ParameterizedTypeDemo {
    static class Box<T> { }
    static class StringBox extends Box<String> { }

    static class Outer<X> {
        class Inner<Y> { }
    }

    static class Holder {
        Map<String, List<? extends Number>> records;
        List<? super Integer> sink;
        List<String>[] buckets;
        Outer<String>.Inner<Integer> pair;
        @SuppressWarnings("rawtypes")
        List raw;
    }

    static class Service {
        public Map<String, Integer> convert(List<? extends Number> input) {
            return Collections.emptyMap();
        }
    }

    public static void main(String[] args) throws Exception {
        Field records = Holder.class.getDeclaredField("records");
        System.out.println("erased records: " + records.getType().getTypeName());
        ParameterizedType map = (ParameterizedType) records.getGenericType();
        System.out.println("map raw: " + map.getRawType().getTypeName());
        System.out.println("map arguments: " + map.getActualTypeArguments().length);
        System.out.println("key: " + map.getActualTypeArguments()[0].getTypeName());
        ParameterizedType values = (ParameterizedType) map.getActualTypeArguments()[1];
        System.out.println("value raw: " + values.getRawType().getTypeName());
        WildcardType wildcard = (WildcardType) values.getActualTypeArguments()[0];
        System.out.println("value upper: " + wildcard.getUpperBounds()[0].getTypeName());
        System.out.println("value lower count: " + wildcard.getLowerBounds().length);
        ParameterizedType sink = (ParameterizedType) Holder.class.getDeclaredField("sink").getGenericType();
        WildcardType lower = (WildcardType) sink.getActualTypeArguments()[0];
        System.out.println("sink lower: " + lower.getLowerBounds()[0].getTypeName());
        System.out.println("sink upper: " + lower.getUpperBounds()[0].getTypeName());

        Type buckets = Holder.class.getDeclaredField("buckets").getGenericType();
        System.out.println("buckets generic array: " + (buckets instanceof GenericArrayType));
        Type component = ((GenericArrayType) buckets).getGenericComponentType();
        System.out.println("buckets component: " + component.getTypeName());

        ParameterizedType pair = (ParameterizedType) Holder.class.getDeclaredField("pair").getGenericType();
        System.out.println("pair raw simple: " + ((Class<?>) pair.getRawType()).getSimpleName());
        System.out.println("pair argument: " + pair.getActualTypeArguments()[0].getTypeName());
        ParameterizedType owner = (ParameterizedType) pair.getOwnerType();
        System.out.println("owner raw simple: " + ((Class<?>) owner.getRawType()).getSimpleName());
        System.out.println("owner argument: " + owner.getActualTypeArguments()[0].getTypeName());

        ParameterizedType base = (ParameterizedType) StringBox.class.getGenericSuperclass();
        System.out.println("base raw simple: " + ((Class<?>) base.getRawType()).getSimpleName());
        System.out.println("base argument: " + base.getActualTypeArguments()[0].getTypeName());

        Type raw = Holder.class.getDeclaredField("raw").getGenericType();
        System.out.println("raw list is Class: " + (raw instanceof Class<?>));

        Method convert = Service.class.getDeclaredMethod("convert", List.class);
        System.out.println("method arg: " + convert.getGenericParameterTypes()[0].getTypeName());
        System.out.println("method return: " + convert.getGenericReturnType().getTypeName());
    }
}
~~~

Biên dịch và chạy bằng Java 17 hoặc cao hơn:

~~~bash
javac --release 17 -Xlint:all ParameterizedTypeDemo.java
java ParameterizedTypeDemo
~~~

**Stdout chính xác** từ lần biên dịch/chạy đã kiểm chứng:

~~~text
erased records: java.util.Map
map raw: java.util.Map
map arguments: 2
key: java.lang.String
value raw: java.util.List
value upper: java.lang.Number
value lower count: 0
sink lower: java.lang.Integer
sink upper: java.lang.Object
buckets generic array: true
buckets component: java.util.List<java.lang.String>
pair raw simple: Inner
pair argument: java.lang.Integer
owner raw simple: Outer
owner argument: java.lang.String
base raw simple: Box
base argument: java.lang.String
raw list is Class: true
method arg: java.util.List<? extends java.lang.Number>
method return: java.util.Map<java.lang.String, java.lang.Integer>
~~~

Giải thích phần output:

1. `records.getType()` chỉ thấy `Map.class`, còn `getGenericType()` có `Map<String, List<? extends Number>>`, gồm **hai actual arguments**.
2. Argument thứ hai của `Map` lại là `ParameterizedType List<...>`; wildcard argument bên trong có upper bound `Number`.
3. `sink` chứa wildcard `? super Integer` nên lower bound là `Integer` và upper bound là `Object`.
4. `buckets` chứa generic array, với generic component `List<String>`. Việc khai báo field này vẫn biên dịch, dù không thể `new List<String>[n]` trực tiếp.
5. `pair` là member `Inner<Integer>` được sở hữu bởi `Outer<String>`; `getOwnerType()` giữ thông tin `String` của outer class.
6. `StringBox extends Box<String>` cho thấy actual type argument trong *generic superclass*. Đây là metadata của quan hệ kế thừa, **không phải type argument đoán từ object bất kỳ**.
7. Field khai báo raw `List` trả `Class`, chứng minh không nên cast mọi `Type` thành `ParameterizedType`.
8. Method `convert` cho ví dụ `getGenericParameterTypes()` và `getGenericReturnType()` với signature generic.

Chương trình không phụ thuộc thứ tự `getDeclaredFields()` nên kết quả dễ tái hiện: từng field được tìm bằng tên tường minh.

## 8. Kế thừa, generic interface và phân giải type variable

Không phải lúc nào thông tin type argument nằm ngay trong field. Ví dụ:

~~~java
interface Repository<T> { }

class UserRepository implements Repository<String> { }
~~~

Đọc:

~~~java
Type[] interfaces = UserRepository.class.getGenericInterfaces();
ParameterizedType repository = (ParameterizedType) interfaces[0];
Type argument = repository.getActualTypeArguments()[0]; // String.class
~~~

Đây là đoạn minh họa; cần đặt trong class/phương thức với import `java.lang.reflect.*`. `getGenericInterfaces()` mô tả generic superinterfaces khai báo trực tiếp; nếu một lớp con chỉ kế thừa interface gián tiếp, cần duyệt cây class/interface phù hợp.

Một trường hợp phức tạp hơn:

~~~java
class Parent<T> {
    T value;
}

class Child extends Parent<String> { }
~~~

`Parent.class.getDeclaredField("value").getGenericType()` vẫn là **`TypeVariable T`**, bởi field được khai báo trong `Parent<T>`. Trong khi đó, `Child.class.getGenericSuperclass()` trả `ParameterizedType Parent<String>`.

Muốn suy ra field `value` có type argument `String` trong **ngữ cảnh `Child`**, bạn phải xây dựng mapping `T → String` rồi áp vào chữ ký field; API `Field.getGenericType()` không tự làm phép thay thế đó.

Điều này đặc biệt quan trọng ở framework ORM, serializer hoặc dependency injection: nếu chỉ đọc `Field.getGenericType()` rồi luôn ép kết quả thành `Class<?>`, parser sẽ sai với subclass generic, wildcard hoặc nested types.

## 9. Phạm vi thông tin có thể kết luận

`ParameterizedType` phản ánh **chữ ký generic tại vị trí khai báo**, chẳng hạn `Repository<User>` ở field hoặc `Child extends Parent<String>` ở superclass. Nó không xác định kiểu phần tử của một đối tượng `new ArrayList<String>()` thông thường: `new ArrayList<Integer>().getClass()` và `new ArrayList<String>().getClass()` đều cho `ArrayList.class`.

Muốn chuyển `T` của `Parent<T>` thành `String` trong ngữ cảnh `Child`, công cụ phải đọc cạnh `Child extends Parent<String>`, ánh xạ biến kiểu và thay vào declaration. Nếu cần truyền `List<String>` vào serializer, bên gọi có thể giữ `Type` từ field hoặc type token; kỹ thuật type token phụ thuộc vào chữ ký superclass của một subclass khai báo rõ kiểu. Trong mọi trường hợp, metadata không tự kiểm chứng dữ liệu runtime. Bài cuối chương phân tích erasure, class-file `Signature` và thử nghiệm Reflection invocation đầy đủ.

## 10. Kiểm tra cấu trúc trước khi xử lý

Khi đọc một field/method/supertype, xác nhận `type instanceof ParameterizedType p` trước khi gọi ba phương thức của nó. Duyệt từng `Type` từ `p.getActualTypeArguments()`; nếu đối số cũng là `ParameterizedType`, lặp lại theo chiều sâu, nếu là `WildcardType` thì đọc upper/lower bounds, nếu là `TypeVariable` thì xem generic declaration/bounds, nếu là `GenericArrayType` thì đọc generic component. **Duyệt cả `p.getOwnerType()` khi khác null**, vì `Outer<String>.Inner<Integer>` mang `String` ở owner, không ở argument của inner.

Để hiển thị có thể dùng `getTypeName()`; với resolver/cache, phải giữ cấu trúc `Type` chứ không tách chuỗi. Bounds tự tham chiếu như `T extends Comparable<T>` cần tập node đã thăm để tránh đệ quy vô hạn. Nếu signature trong bytecode thiếu hoặc sai, các API generic có thể báo `TypeNotPresentException`, `MalformedParameterizedTypeException` hay `GenericSignatureFormatError`: thông báo lỗi metadata thay vì tự tạo đối số kiểu.

## 11. Lỗi thường gặp và cách sửa

| Lỗi | Vì sao sai | Cách đúng |
| --- | --- | --- |
| Cast mọi `field.getGenericType()` thành `ParameterizedType` | Field có thể là raw `List`, `T`, `String` hoặc array | Kiểm tra `instanceof` trước |
| Cast mọi `getActualTypeArguments()[i]` thành `Class<?>` | Argument có thể là wildcard, type variable hoặc parameterized type lồng nhau | Xử lý toàn bộ họ `Type` |
| Nhầm raw type là kiểu argument đầu tiên | `getRawType()` trả `Map`, không trả `String` | Lấy args qua `getActualTypeArguments()` |
| Đọc một tầng `Map` rồi kết luận value là `List.class` | `List<? extends Number>` chứa metadata sâu hơn | Duyệt cây nested parameterized arguments |
| Mặc định `getOwnerType() == null` | Member class có owner, có thể là parameterized owner | Xử lý owner khi có |
| Hiểu `List<String>[]` là một `Class<List<String>[]>` | Generic component không reifiable | Nhận diện `GenericArrayType` |
| Dùng `getClass()` của `ArrayList<String>` để lấy `String` | Type erasure không giữ tham số kiểu của instance | Dùng chữ ký khai báo hoặc type token được truyền rõ |
| Đọc field `T` ở parent rồi mong thấy `String` của child | `Field` gắn với generic declaration `Parent<T>` | Giải mapping `T → String` từ `Child extends Parent<String>` |
| So sánh `Type.getTypeName()` bằng chuỗi để phân loại | Kiểu có cấu trúc và owner/bounds lồng nhau | Dùng `instanceof` và API của từng subtype |
| Tin rằng actual type arguments tự validate object collection | Generic metadata không bảo đảm trạng thái dữ liệu runtime | Kiểm tra dữ liệu/hợp đồng ở boundary riêng |

## 12. Khi nào nên dùng `ParameterizedType`?

- **Serializer và JSON mapper:** đọc `Map<String, List<Item>>` trong khai báo để định hướng xử lý cấu trúc object, phối hợp với logic kiểm tra dữ liệu riêng.
- **Dependency injection / framework registration:** nhận dạng injection point như `Repository<User>` và phân biệt các specialization dựa trên generic metadata.
- **Tài liệu hóa API và schema:** trình bày đúng tham số, return type hoặc superclass generic, thay vì chỉ dùng raw `Map` hay `List`.
- **Công cụ static/runtime inspection:** phát hiện khai báo raw type, wildcard bounds, nested generic argument và owner type trong chữ ký.
- **Generic type resolver:** theo dõi quan hệ `Child extends Parent<String>` để thay thế type variable `T` trong ngữ cảnh cụ thể.

**Quy tắc ghi nhớ:** `getRawType()` cho biết *generic type gốc*; `getActualTypeArguments()` cho biết *các đối số kiểu ở lớp đang xét*; `getOwnerType()` cho biết *kiểu sở hữu nếu là nested type*. Mỗi actual argument lại là **`Type`**, nên phải phân tích theo cấu trúc — và mọi thông tin đó vẫn bị giới hạn bởi **type erasure** khi muốn suy luận về một object runtime.

### Tài liệu chuẩn

- [Java SE 21 — `java.lang.reflect.ParameterizedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/ParameterizedType.html): `getRawType`, `getActualTypeArguments`, `getOwnerType`.
- [Java SE 21 — `Type`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Type.html), [`WildcardType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/WildcardType.html), [`GenericArrayType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/GenericArrayType.html), [`TypeVariable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/TypeVariable.html).
- [Java SE 21 — `Field`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html) và [`Class`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html) cho các API đọc generic signatures.
- [Java Language Specification §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.6) và [§4.5 — Parameterized Types](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.5).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-model-003">Raw type, owner type và kiểu lồng có generic</a>

<details>
<summary>Xem chi tiết</summary>

# Raw type, owner type và kiểu lồng có generic trong Java Reflection

## 1. Ba khái niệm phải phân biệt

Trong Java, một kiểu generic có thể được sử dụng với tham số kiểu cụ thể, chẳng hạn `List<String>` hoặc `Outer<String>.Inner<Integer>`. Khi đọc cấu trúc kiểu qua Reflection, ba khái niệm quan trọng là **raw type**, **owner type** và **tham số kiểu của bản thân kiểu lồng**.

- **Raw type trong ngôn ngữ Java** là cách dùng một kiểu generic mà bỏ danh sách đối số kiểu, chẳng hạn `List` thay cho `List<String>`, hoặc `Outer.Inner` khi `Outer<T>` bị dùng ở dạng raw. Raw type được Java giữ lại chủ yếu vì tương thích mã nguồn trước khi có generics.
- **Raw type theo `ParameterizedType.getRawType()`** là cấu trúc khai báo nền của một parameterized type, thường là `Class<?>`. Ví dụ, trong `List<String>`, `getRawType()` trả `List.class`. **Điều này không có nghĩa khai báo `List<String>` trong source là raw usage**: ta chỉ đang xem class/interface gốc của kiểu đã được tham số hóa.
- **Owner type** của một kiểu thành viên là kiểu mà nó là thành viên. Với `Outer<String>.Inner<Integer>`, owner của `Inner<Integer>` là `Outer<String>`. Owner giữ thông tin kiểu của phần bao bên ngoài, và bản thân `Inner` giữ tham số kiểu riêng của nó.

Ví dụ khởi đầu:

~~~java
class Outer<T> {
    class Inner<U> {}
    class Plain {}
    static class Nested<V> {}
}
~~~

Các kiểu khác nhau:

| Cách dùng | Ý nghĩa |
| --- | --- |
| `Outer<String>` | `Outer<T>` với `T = String`. |
| `Outer` | Dạng raw của `Outer<T>`. |
| `Outer<String>.Inner<Integer>` | Inner **không static**, vừa có owner `Outer<String>`, vừa có đối số kiểu riêng `Integer` cho `U`. |
| `Outer<String>.Plain` | Inner không generic riêng; owner vẫn chứa `String`. |
| `Outer.Inner` | Inner thuộc raw `Outer`, nên cũng được dùng dưới dạng raw. |
| `Outer.Nested<Long>` | Nested class **static** có tham số kiểu riêng `Long`, không phụ thuộc `T` của `Outer`. |

Lưu ý: “owner type” ở Reflection diễn tả **cấu trúc kiểu thành viên**, không đồng nghĩa một đối tượng outer instance. Static nested class cũng có thể có owner type theo quan hệ khai báo, mặc dù không cần outer instance để khởi tạo.

## 2. Các giao diện Reflection và API cần dùng

Java cung cấp hệ thống `java.lang.reflect.Type`, bao gồm những dạng chủ yếu sau:

| Kiểu Reflection | Khi nào thường xuất hiện? |
| --- | --- |
| `Class<?>` | Class/interface thông thường, kiểu primitive, mảng class, hoặc một khai báo dùng raw type; không chứa tập đối số generic cụ thể. |
| `ParameterizedType` | Kiểu được tham số hóa như `List<String>`, `Outer<String>.Inner<Integer>`, hoặc kiểu lồng không generic nhưng có owner parameterized. |
| `TypeVariable<?>` | Tham số kiểu khai báo như `T`, `U` ở mức class hoặc method, chưa thay bằng đối số cụ thể. |
| `WildcardType` | Ký hiệu wildcard như `? extends Number` hoặc `? super Integer`. |
| `GenericArrayType` | Một số kiểu mảng có thành phần generic như `T[]`, khác với `String[].class`. |

Khi đối tượng thực sự là `ParameterizedType p`, ba phương thức mấu chốt:

- `p.getRawType()`: trả `Type` đại diện cho class/interface khai báo kiểu đang dùng. Với `Outer<String>.Inner<Integer>`, đó là `Outer.Inner.class`, **không phải** `Outer.class`.
- `p.getOwnerType()`: trả `Type` của kiểu chứa nó, chẳng hạn `Outer<String>`. Nếu là top-level type như `List<String>`, kết quả là `null`.
- `p.getActualTypeArguments()`: trả `Type[]` cho các đối số kiểu của **phần đang xét**. Với `Outer<String>.Inner<Integer>`, mảng chỉ có `Integer`; `String` thuộc owner, không nằm trong mảng này.

Trên một `Type` bất kỳ, **không được ép thẳng sang `ParameterizedType`**: `Field.getGenericType()` có thể trả `Class`, `TypeVariable` hoặc kiểu khác tùy khai báo. Dùng `instanceof ParameterizedType p` rồi mới đọc `getRawType()`, `getOwnerType()`, `getActualTypeArguments()`.

## 3. Ví dụ Java 17+ chạy được: quan sát raw/owner/arguments

Để tránh dựa vào tên binary có ký tự `$`, ví dụ in tên ngắn bằng hàm tiện ích `shortName`. Đặt toàn bộ mã trong **`GenericOwnerDemo.java`**:

~~~java
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

class Outer<T> {
    class Inner<U> {}
    class Plain {}
    static class Nested<V> {}
}

public class GenericOwnerDemo {
    static class Holder {
        Outer<String>.Inner<Integer> full;
        Outer<String>.Plain plain;
        Outer.Inner rawInner;
        Outer<String> parameterizedOuter;
        Outer rawOuter;
        Outer.Nested<Long> nested;
        List<String> list;
    }

    static String shortName(Type type) {
        if (type == null) return "-";
        if (type instanceof Class<?> c) return c.getSimpleName();
        if (type instanceof ParameterizedType p) {
            String args = Arrays.stream(p.getActualTypeArguments())
                    .map(GenericOwnerDemo::shortName)
                    .collect(Collectors.joining(", "));
            return shortName(p.getRawType()) + "<" + args + ">";
        }
        return type.getTypeName();
    }

    static void inspect(String fieldName) throws NoSuchFieldException {
        Field field = Holder.class.getDeclaredField(fieldName);
        Type type = field.getGenericType();

        if (type instanceof ParameterizedType p) {
            String args = Arrays.stream(p.getActualTypeArguments())
                    .map(GenericOwnerDemo::shortName)
                    .collect(Collectors.joining(", ", "[", "]"));
            System.out.printf("%s: raw=%s; owner=%s; args=%s%n",
                    fieldName, shortName(p.getRawType()),
                    shortName(p.getOwnerType()), args);
        } else {
            System.out.printf("%s: Class=%s%n",
                    fieldName, shortName(type));
        }
    }

    public static void main(String[] args) throws Exception {
        inspect("full");
        inspect("plain");
        inspect("rawInner");
        inspect("parameterizedOuter");
        inspect("rawOuter");
        inspect("nested");
        inspect("list");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 GenericOwnerDemo.java
java GenericOwnerDemo
~~~

Stdout đã được kiểm chứng từ chương trình trên:

~~~text
full: raw=Inner; owner=Outer<String>; args=[Integer]
plain: raw=Plain; owner=Outer<String>; args=[]
rawInner: Class=Inner
parameterizedOuter: raw=Outer; owner=-; args=[String]
rawOuter: Class=Outer
nested: raw=Nested; owner=Outer; args=[Long]
list: raw=List; owner=-; args=[String]
~~~

**Phân tích:**

1. `full` được khai báo `Outer<String>.Inner<Integer>`. `getRawType()` chỉ ra `Inner.class`; `getOwnerType()` là một `ParameterizedType` cho `Outer<String>`; `getActualTypeArguments()` ở cấp `Inner` chỉ gồm `Integer.class`.
2. `plain` được khai báo `Outer<String>.Plain`. Tuy `Plain` **không khai báo tham số kiểu riêng**, Reflection vẫn dùng `ParameterizedType` để giữ owner `Outer<String>`; `getActualTypeArguments()` rỗng. Đây là trường hợp dễ gây nhầm lẫn nhất.
3. `rawInner` được khai báo `Outer.Inner` trong ngữ cảnh `Outer` raw, nên `getGenericType()` là một `Class<?>`. Không thể cast sang `ParameterizedType` để yêu cầu `getOwnerType()` hay args của khai báo field này.
4. `parameterizedOuter` là kiểu top-level `Outer<String>`; owner `null`. `rawOuter` chỉ là `Class<Outer>` ở mức Reflection vì khai báo dùng `Outer` không có đối số kiểu.
5. `nested` là `Outer.Nested<Long>`. `getOwnerType()` là `Class` cho `Outer` (được in là `Outer`), **không phải `Outer<String>`**, bởi `Nested` là `static` và không mang tham số `T` của outer.
6. `List<String>` là parameterized interface top-level từ thư viện Java, nên `getOwnerType() == null`. Nó khác về quan hệ thành viên so với `Inner` và `Nested`.

Hàm `shortName` chỉ phục vụ trình bày. Nó cố tình hiển thị args của `ParameterizedType` riêng và hiển thị owner ở cột khác; khi kiểu chứa nhiều lớp lồng phức tạp, nên xử lý cấu trúc `Type` đầy đủ thay vì dùng tên rút gọn để tái tạo cú pháp khai báo ban đầu.

## 4. Đọc đúng `getRawType()`: class nền và raw usage là hai chuyện

Giả sử:

~~~java
List<String> typed;
List raw;
~~~

Trong Reflection trên **hai field khai báo khác nhau**:

- `typed`: `Field.getGenericType()` trả `ParameterizedType`; `getRawType() == List.class`, và `getActualTypeArguments()[0] == String.class`.
- `raw`: `Field.getGenericType()` trả thẳng `List.class`. Không có mảng `String` hay đối số generic được khai báo trên field đó.

Vì vậy, không nên nói “`List<String>` là raw” chỉ bởi `getRawType()` của nó là `List.class`. Tên API nói tới **loại khai báo nền của parameterized type**.

`Class<?>` của một generic declaration vẫn có thể cung cấp thông tin **tham số kiểu hình thức**:

~~~java
Outer.class.getTypeParameters(); // chứa TypeVariable tên T
Outer.Inner.class.getTypeParameters(); // chứa TypeVariable tên U
~~~

Nhưng `TypeVariable T` hoặc `U` là “biến kiểu trong khai báo”, không phải **đối số cụ thể** `String`/`Integer` gắn vào một field. Để xem đối số thật được ghi trên field, đọc `Field.getGenericType()` rồi đi qua `ParameterizedType` và owner của nó.

**Raw type và nguy cơ:** Java giữ raw type cho khả năng tương thích thư viện cũ. Truyền dữ liệu qua raw collections có thể dẫn tới **unchecked warning** ở compile time và `ClassCastException` khi lấy lại dữ liệu sai kiểu. Trong code mới, nên khai báo type argument rõ ràng; nếu chỉ không biết kiểu thì dùng wildcard `<?>` khi phù hợp, ví dụ `List<?>` khác `List` raw.

## 5. Owner type không phải outer instance

Trong mã nguồn Java, **inner class không static** có liên hệ với một instance của outer class. Còn static nested class thì không cần instance đó:

~~~java
Outer<String> outer = new Outer<>();
Outer<String>.Inner<Integer> inner = outer.new Inner<>();
Outer.Nested<Long> nested = new Outer.Nested<>();
~~~

Đây là một **đoạn minh họa sử dụng `Outer` ở phần 3**, không phải file riêng có `main`. Với `inner`, cú pháp tạo instance cần `outer.new Inner<>()` vì `Inner` không static. Với `nested`, có thể gọi trực tiếp `new Outer.Nested<>()`.

Các phương thức Reflection liên quan nhưng khác mục đích:

| API | Nội dung |
| --- | --- |
| `ParameterizedType.getOwnerType()` | Owner **trong biểu diễn kiểu generic cụ thể**: ví dụ `Outer<String>` đối với `Outer<String>.Inner<Integer>`. |
| `Class.getDeclaringClass()` | Class khai báo một member class/interface, ví dụ `Outer.class` đối với `Outer.Inner.class`; **không mang `<String>`**. |
| `Class.getEnclosingClass()` | Class bao quanh theo cấu trúc nguồn/binary; còn áp dụng cho một số local/anonymous class. Không cung cấp đối số kiểu của outer. |
| `Class.isMemberClass()` | Kiểm tra kiểu có phải member class không; không nói kiểu đã được tham số hóa ra sao. |
| `Modifier.isStatic(nestedClass.getModifiers())` | Cho biết class lồng có static modifier; không thể suy trực tiếp từ việc `getOwnerType()` khác `null`. |

Một `Outer.Nested<Long>` vẫn có owner type ở metadata vì `Nested` là **member của `Outer`**, dù không có enclosing instance và không kế thừa `T`. Chủ thể “chứa khai báo kiểu” và chủ thể “instance vật lý để truy cập” phải phân biệt.

## 6. Inner generic và non-generic inner: vì sao args có thể rỗng?

Với:

~~~java
class Outer<T> {
    class Inner<U> {}
    class Plain {}
}
~~~

`Outer<String>.Inner<Integer>` có **hai mức ràng buộc kiểu**:

- `T = String` của `Outer<T>`, nằm trong owner type.
- `U = Integer` của `Inner<U>`, nằm trong `getActualTypeArguments()` của inner type.

`Outer<String>.Plain` chỉ có mức owner `T = String`. `Plain` không khai báo `<U>` nên `getActualTypeArguments()` của inner **rỗng** dù toàn bộ kiểu có chứa thông tin generic ở owner.

Một ứng dụng thường sai là chỉ kiểm tra `getActualTypeArguments().length == 0` rồi kết luận toàn bộ kiểu không generic. Cần kiểm tra `getOwnerType()` và đọc đệ quy nếu owner cũng là `ParameterizedType`.

Cách duyệt an toàn về mặt ý tưởng:

~~~java
static void walk(Type type) {
    if (type instanceof ParameterizedType p) {
        Type owner = p.getOwnerType();
        if (owner != null) walk(owner);
        for (Type argument : p.getActualTypeArguments()) walk(argument);
    } else if (type instanceof Class<?> c) {
        System.out.println(c.getName());
    }
}
~~~

Đây là **method minh họa**, cần đặt vào class có import `java.lang.reflect.*`. Nó chưa xử lý chi tiết các nhánh `WildcardType`, `TypeVariable`, `GenericArrayType` nên không phải generic type printer hoàn chỉnh. Mục tiêu là cho thấy phải duyệt **owner lẫn args**, không chỉ mảng args của inner.

## 7. Các dạng raw với class lồng và lỗi “partially raw”

JLS §4.8 nêu quy tắc: nếu outer generic bị sử dụng raw, inner member class không static khai báo trong nó cũng được xem là raw. Java không cho dùng **nửa raw/nửa tham số hóa** tại một chuỗi kiểu inner.

Với `Outer<T>` có `Inner<U>` ở ví dụ, hai khai báo sau **không hợp lệ và không biên dịch**:

~~~text
Outer.Inner<Integer> bad1;      // Sai: raw Outer nhưng cố truyền <Integer> cho Inner
Outer<String>.Inner bad2;       // Sai: Outer<String> nhưng dùng raw Inner
~~~

Hai khai báo đúng tương ứng là:

~~~java
Outer.Inner rawInner;
Outer<String>.Inner<Integer> parameterizedInner;
~~~

Đây là các **field declaration minh họa**: đặt bên trong class nếu muốn biên dịch. Dùng raw inner hợp lệ vì lý do tương thích legacy nhưng mất bớt kiểm tra generic; dạng được tham số hóa đầy đủ an toàn hơn khi viết code mới.

Với class lồng `static`, các quy tắc khác:

~~~java
Outer.Nested<Long> ok;  // hợp lệ: Nested static có V riêng
~~~

Bạn **không** viết `Outer<String>.Nested<Long>`, vì static nested type không phụ thuộc tham số `T` và không được chọn static member type từ một parameterized outer qua cú pháp đó. Hãy dùng `Outer.Nested<Long>`.

Sự phân biệt này rất quan trọng khi viết trình phân tích generic type cho framework: đừng tự ghép chuỗi `Outer<...>.Nested<...>` chỉ vì `getOwnerType()` khác null. Cần biết `Nested` có static modifier hay không và semantics của source syntax.

## 8. `getType()` khác `getGenericType()`

Khi đọc một field:

~~~java
Field field = Holder.class.getDeclaredField("full");
Class<?> erased = field.getType();
Type generic = field.getGenericType();
~~~

Đoạn này dùng `Holder` trong ví dụ phần 3, chưa phải chương trình độc lập.

- `getType()` trả `Class<?>` của kiểu field sau erasure, tức `Outer.Inner.class`. Nó không thể mang hai đối số `String` và `Integer`.
- `getGenericType()` đọc signature generic nếu có và trả `Type` phù hợp, với `full` là `ParameterizedType` có raw=`Outer.Inner.class`, owner=`Outer<String>` và args=`[Integer]`.
- Với field raw `Outer.Inner rawInner`, cả hai biểu diễn có thể là đối tượng `Class`; generic signature của cách dùng field này không khai báo binding cho `T/U`.

Tương tự, `Method.getGenericReturnType()`, `Method.getGenericParameterTypes()`, `Class.getGenericSuperclass()` và `Class.getGenericInterfaces()` giúp giữ cấu trúc generic **được ghi trong declaration**. Dùng các API `getGeneric...` khi nhiệm vụ là phân tích schema/type model; dùng `getType()`/`getReturnType()` khi chỉ cần runtime `Class` để gọi hoặc kiểm tra kiểu.

## 9. Generic metadata không có nghĩa mọi instance giữ type argument runtime

Java generics phần lớn dùng **type erasure**. Hai object được tạo qua:

~~~java
Outer<String> first = new Outer<>();
Outer<Integer> second = new Outer<>();
~~~

đều có cùng runtime `Class` là `Outer.class` (giả sử đặt đoạn trong method hợp lệ). Gọi `first.getClass()` không thể tự xác định `String` và `second.getClass()` không thể tự xác định `Integer`.

Tuy nhiên, field như `Holder.full` có **khai báo generic trong class file**, nên `getGenericType()` vẫn đọc được `Outer<String>.Inner<Integer>`. Hai câu hỏi khác nhau:

- “Một object bất kỳ được khởi tạo với đối số generic nào?”: thường không thể phục hồi chỉ bằng `getClass()` do erasure.
- “Source khai báo một field / method / superclass generic như thế nào?”: có thể đọc metadata signature qua Reflection nếu nó hiện diện.

Điều này giải thích tại sao một raw `Class<?>` không đủ làm nơi lưu `ownerType` và nhiều tầng actual type arguments cho mọi field.

## 10. Phân tích `Type` đúng cách trong framework

Khi xây mapper, serializer, dependency injection hoặc trình tạo tài liệu API, chọn chiến lược theo bản chất `Type`:

1. **Bắt đầu từ đúng vị trí khai báo:** dùng `Field.getGenericType()`, `Method.getGenericReturnType()` hoặc signature cấp superclass/interface, tùy loại metadata cần đọc.
2. **Phân nhánh theo `Type` thực tế:** nếu là `ParameterizedType`, đọc `rawType`, `ownerType` và `actualTypeArguments`. Nếu là `Class<?>`, có thể là kiểu không generic hoặc một raw use; xem chính declaration khi cần phân biệt.
3. **Giữ cả owner và inner args:** `Outer<String>.Inner<Integer>` không tương đương `Outer<Integer>.Inner<Integer>`; nếu cache key chỉ chứa raw `Inner.class` và `Integer`, bạn sẽ bỏ mất thông tin `String`.
4. **Kiểm tra static nested:** owner ở cấp Reflection có thể là một `Class` chưa tham số hóa. Không suy ra tất cả nested types đều cần outer instance hoặc phải có generic binding bên ngoài.
5. **Xử lý các nhánh còn lại:** `TypeVariable` có thể cần phân giải qua môi trường kiểu của class kế thừa; `WildcardType` có upper/lower bounds; `GenericArrayType` mang component type có thể generic. Đừng cast tất cả thành `Class`.
6. **Không nhầm metadata với conversion:** việc biết `T=String` từ field signature không tự động kiểm tra mọi giá trị runtime. Validation và mapping dữ liệu là tác vụ riêng.

Ví dụ lỗi thường gặp:

~~~java
Type type = field.getGenericType();
ParameterizedType p = (ParameterizedType) type; // Có thể ClassCastException!
~~~

Đoạn trên chỉ minh họa **lỗi cast không kiểm tra** (cần biến `field` trong scope). Với field `rawInner` hoặc `rawOuter`, `getGenericType()` trả `Class<?>` nên không thể cast thành `ParameterizedType`. Hãy dùng `instanceof` như chương trình phần 3.

Khi định danh một parameterized type, cần tính đến cả generic declaration, owner type và đối số kiểu. Các implementation `ParameterizedType` được quy định phải có `equals()` dựa vào khai báo và đối số tương ứng; trong cache, dùng `Type` đúng nghĩa thường an toàn hơn ghép tên class đơn giản. Dù vậy, nhiều framework vẫn cần chuẩn hóa/resolve type variables riêng tùy ngữ cảnh.

## 11. Những hiểu lầm cần tránh

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “Có `getRawType()` nghĩa là khai báo field đã bỏ hết generics.” | `ParameterizedType.getRawType()` chỉ trả khai báo nền của một parameterized type; args vẫn nằm trong `getActualTypeArguments()` và owner. |
| “`getActualTypeArguments()` của inner chứa luôn T của outer.” | T của outer nằm ở `getOwnerType()`; mảng args của inner chỉ mô tả phần inner. |
| “Nếu args rỗng, kiểu không có generic liên quan.” | `Outer<String>.Plain` có args rỗng nhưng owner `Outer<String>`. |
| “Owner type luôn là `Class<?>`.” | Owner có thể là `ParameterizedType` như `Outer<String>`, hoặc `Class<?>` như `Outer`, hoặc `null` với top-level. |
| “Owner khác null nghĩa là có outer instance.” | Static nested class có owner theo khai báo nhưng không cần outer instance. |
| “`Class.getDeclaringClass()` cho biết `Outer<String>`.” | Nó trả `Outer.class`, không phải binding `String`. |
| “Raw `Outer` có thể đi với `Inner<Integer>`.” | Java cấm loại partially raw đó đối với non-static member inner. |
| “Static nested `Outer.Nested<Long>` phụ thuộc T của `Outer<T>`.” | Static nested có tham số riêng V và không dùng type argument T của outer. |
| “Mọi `Type` đều là `ParameterizedType`.” | Raw usage có thể trả `Class`; wildcard/type variable/array generic còn có các dạng khác. |
| “Có thể đọc `T=String` từ `new Outer<String>().getClass()`.” | Erasure không giữ đối số kiểu của instance theo cách đó; phân tích declaration signature là chuyện khác. |

## 12. Câu hỏi tự kiểm tra

1. Trong `Outer<String>.Inner<Integer>`, `getRawType()` đại diện class nào? `getOwnerType()` chứa kiểu gì? Mảng `getActualTypeArguments()` có bao nhiêu phần tử?
2. Tại sao `Outer<String>.Plain` vẫn xuất hiện như `ParameterizedType` dù `Plain` không có `<U>`?
3. Một field khai báo `Outer.Inner rawInner` sẽ cho `Class` hay `ParameterizedType` khi gọi `getGenericType()`? Có thể cast kết quả sang `ParameterizedType` không?
4. Owner của `Outer.Nested<Long>` có thể khác `null` không? Vì sao điều đó không chứng minh `Nested` cần outer instance?
5. Hai khai báo `Outer.Inner<Integer>` và `Outer<String>.Inner` có hợp lệ không? Quy tắc JLS nào khiến chúng là “partially raw”?
6. Nếu một mapper chỉ lưu `getRawType()` của `Outer<String>.Inner<Integer>`, nó bỏ mất thông tin nào cần thiết để phân biệt các field generic?

**Điểm nhớ chính:** Với kiểu lồng có generic, hãy xem **ba phần riêng**: `getRawType()` chỉ tới khai báo nền, `getOwnerType()` giữ cấu trúc bao ngoài và `getActualTypeArguments()` mô tả đối số của chính phần đang xét. Raw usage lại là chuyện **bỏ type argument tại nơi dùng kiểu**, không đồng nghĩa với việc gọi `getRawType()`.

## Tài liệu chuẩn

- [Java SE 17 — `ParameterizedType` (raw, owner, actual arguments)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html)
- [Java SE 17 — `Field.getGenericType()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html#getGenericType())
- [JLS 17 §4.5 — Parameterized Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.5)
- [JLS 17 §4.8 — Raw Types và quy tắc inner class](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.8)
- [JLS 17 §8.1.3 — Inner Classes and Enclosing Instances](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.3)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-model-004">TypeVariable và GenericDeclaration</a>

<details>
<summary>Xem chi tiết</summary>

# TypeVariable và GenericDeclaration trong Java Reflection

Java generic cho phép khai báo `class Box<T>`, `interface Pair<K,V>`, phương thức `<U> U convert(U value)` hoặc constructor có tham số kiểu riêng. Khi phân tích những khai báo này bằng Reflection, hai giao diện `java.lang.reflect.TypeVariable` và `java.lang.reflect.GenericDeclaration` giải quyết hai câu hỏi khác nhau:

- **`TypeVariable<D extends GenericDeclaration>`** biểu diễn một **biến kiểu được khai báo**, như `T` hoặc `U`, kèm tên, upper bounds, annotations và chủ thể khai báo.
- **`GenericDeclaration`** biểu diễn **nơi được phép khai báo tham số kiểu**: `Class<?>`, `Method` và `Constructor<?>` đều triển khai giao diện này. Phương thức chung `getTypeParameters()` trả các biến kiểu khai báo trực tiếp tại đó.

Đây là metadata của **khai báo generic**, không phải nơi Java lưu riêng mọi kiểu đối số trên từng object runtime. Phân biệt khai báo, vị trí sử dụng và đối số kiểu cụ thể là trọng tâm để tránh sai lầm do *type erasure*.

## 1. Biến kiểu khác đối số kiểu cụ thể

Xét:

```java
class Box<T extends Number> {
    T value;
}

class IntegerBox extends Box<Integer> {}
```

`T` thuộc `Box<T extends Number>` là **biến kiểu**. Nó được Reflection biểu diễn bằng `TypeVariable`, trong đó upper bound là `Number`. Ngược lại, `Integer` trong `extends Box<Integer>` là **đối số kiểu** của một kiểu parameterized, được đọc từ `ParameterizedType` trên superclass của `IntegerBox`.

`Box.class.getTypeParameters()` trả khai báo `T`. `IntegerBox.class.getTypeParameters()` là mảng rỗng vì lớp con không **tự** khai báo một tham số kiểu nào. `IntegerBox.class.getGenericSuperclass()` lại cho thông tin tương đương `Box<Integer>`.

`new Box<Integer>().getClass()` là `Box.class`, cũng như `new Box<Long>().getClass()`. Generic argument `Integer` hoặc `Long` thường không được reify trong runtime class của một object `Box` thông thường. `TypeVariable.getBounds()` không tự cung cấp kiểu `Integer` cho một object cụ thể.

## 2. `TypeVariable` nằm trong hệ thống `Type`

`java.lang.reflect.Type` là giao diện chung biểu diễn kiểu mà các API Reflection trả về khi đọc metadata generic.

| Dạng `Type` | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| `Class<?>` | `String`, `int`, `String[]` | Kiểu class/interface/primitive/array thông thường |
| `ParameterizedType` | `List<String>`, `Map<K,V>` | Kiểu parameterized có các type arguments |
| `TypeVariable<?>` | `T` của `class Box<T>` | Biến kiểu có declaration riêng |
| `WildcardType` | `? extends Number`, `? super T` | Wildcard trong một lần sử dụng generic |
| `GenericArrayType` | `T[]` | Array với generic component type không phải class array thông thường |

`TypeVariable<D>` triển khai `Type` và `AnnotatedElement`. Giao diện `GenericDeclaration` không phải là một loại `Type`: nó mô tả nơi khai báo biến kiểu. Tuy nhiên `Class<?>` vừa là một `Type` vừa triển khai `GenericDeclaration`; `Method` và `Constructor<?>` triển khai `GenericDeclaration` nhưng không phải các `Type`.

Không có `T.class` cho biến kiểu `T` trong source. Nếu muốn hỏi về một biến kiểu trong khai báo, dùng `TypeVariable`; nếu muốn biết kiểu `List<T>` tại vị trí field, dùng `ParameterizedType` và tiếp tục kiểm tra các phần tử `Type` bên trong.

## 3. `GenericDeclaration.getTypeParameters()`

API cốt lõi:

```java
TypeVariable<?>[] variables = declaration.getTypeParameters();
```

Trong minh họa trên, `declaration` là một biến kiểu `GenericDeclaration` đã được cung cấp. Kết quả là một mảng theo **thứ tự khai báo**; nếu thực thể không trực tiếp khai báo tham số kiểu thì mảng rỗng.

**Class/interface generic:**

```java
class Pair<K, V> {}
```

`Pair.class.getTypeParameters()` trả hai biến `K`, `V`, cùng owner là class `Pair`.

**Method generic:**

```java
class Util {
    public <U extends CharSequence> U echo(U input) {
        return input;
    }
}
```

`Util.class.getMethod("echo", CharSequence.class).getTypeParameters()` trả `U`, owner là `Method` của `echo`. Khi tìm method, tham số runtime sau erasure là `CharSequence.class`: không có `U.class`.

**Constructor generic:**

```java
class Handler {
    public <C extends Runnable> Handler(C action) {}
}
```

`Handler.class.getConstructor(Runnable.class).getTypeParameters()` trả `C` thuộc constructor, dù `Handler.class.getTypeParameters()` rỗng.

`Field` không triển khai `GenericDeclaration`: field **sử dụng** một `T` đã khai báo bởi class/method thích hợp, nhưng không khai báo type parameters theo cách `Class`, `Method` hoặc `Constructor` làm. Đọc generic type của field bằng `Field.getGenericType()`.

## 4. Những phương thức quan trọng của `TypeVariable`

| API | Kết quả | Cách hiểu |
| --- | --- | --- |
| `getName()` | `String` | Tên như `T`, `E`, `K` |
| `getTypeName()` | `String` | Tên mô tả kiểu; với biến kiểu thường là tên biến |
| `getGenericDeclaration()` | `D extends GenericDeclaration` | Class, method hay constructor **sở hữu khai báo** |
| `getBounds()` | `Type[]` | Upper bounds theo thứ tự khai báo |
| `getAnnotatedBounds()` | `AnnotatedType[]` | Upper bounds cùng type-use annotations |
| `getAnnotations()` / `getDeclaredAnnotations()` | `Annotation[]` | Annotations gắn trên **chính tham số kiểu** |

Ví dụ `<T>` có upper bound ngầm định là `Object`, vì vậy `getBounds()` trả một phần tử tương đương `Object.class`, không phải mảng rỗng.

Với `<T extends Number & Comparable<T>>`, `getBounds()` chứa hai phần tử: `Number` (thường là `Class<?>`) và `Comparable<T>` (là `ParameterizedType`). Vì vậy **không ép toàn bộ `getBounds()` sang `Class<?>`**.

Bound đầu tiên quyết định erasure của biến kiểu: `T extends Number & Comparable<T>` được erase thành `Number`; `U extends CharSequence` được erase thành `CharSequence`; `T` không bound được erase thành `Object`. Mọi bound đều góp phần giới hạn type arguments hợp lệ, chứ không chỉ bound đầu.

## 5. Ví dụ Java 17+ chạy độc lập

Lưu đoạn sau vào `TypeVariableDemo.java`. Chương trình có generic class với intersection bounds, method có type variable cùng tên với class, generic constructor và subclass cố định một type argument.

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;

public class TypeVariableDemo<T extends Number & Comparable<T>> {
    public List<T> items;

    public TypeVariableDemo() {}

    public <C extends Runnable> TypeVariableDemo(C action) {}

    // T của method là biến mới, không phải T của class.
    public <T extends CharSequence> T echo(T value) {
        return value;
    }

    public static class Fixed extends TypeVariableDemo<Integer> {}

    public static void main(String[] args) throws Exception {
        TypeVariable<?> classVar =
                TypeVariableDemo.class.getTypeParameters()[0];

        System.out.println("Class variable: " + classVar.getName());
        System.out.println("Class owner: "
                + ((Class<?>) classVar.getGenericDeclaration())
                        .getSimpleName());
        System.out.println("Class bounds: "
                + Arrays.toString(classVar.getBounds()));
        System.out.println("Annotated bounds count: "
                + classVar.getAnnotatedBounds().length);

        Method method = TypeVariableDemo.class.getMethod(
                "echo", CharSequence.class);
        TypeVariable<?> methodVar = method.getTypeParameters()[0];

        System.out.println("Method variable: " + methodVar.getName());
        System.out.println("Method bounds: "
                + Arrays.toString(methodVar.getBounds()));
        System.out.println("Same T variable: "
                + classVar.equals(methodVar));
        System.out.println("Method owner equals: "
                + methodVar.getGenericDeclaration().equals(method));

        Constructor<?> constructor =
                TypeVariableDemo.class.getConstructor(Runnable.class);
        TypeVariable<?> ctorVar = constructor.getTypeParameters()[0];

        System.out.println("Constructor variable: " + ctorVar.getName());
        System.out.println("Constructor bounds: "
                + Arrays.toString(ctorVar.getBounds()));
        System.out.println("Constructor owner equals: "
                + ctorVar.getGenericDeclaration().equals(constructor));

        Field field = TypeVariableDemo.class.getField("items");
        ParameterizedType fieldType =
                (ParameterizedType) field.getGenericType();

        System.out.println("Field generic type: "
                + fieldType.getTypeName());
        System.out.println("Field argument equals class T: "
                + fieldType.getActualTypeArguments()[0]
                        .equals(classVar));
        System.out.println("Field raw type: "
                + field.getType().getTypeName());

        System.out.println("Fixed type parameters: "
                + Fixed.class.getTypeParameters().length);
        ParameterizedType superclass =
                (ParameterizedType) Fixed.class.getGenericSuperclass();
        System.out.println("Fixed raw superclass: "
                + ((Class<?>) superclass.getRawType()).getSimpleName());
        System.out.println("Fixed type argument: "
                + superclass.getActualTypeArguments()[0].getTypeName());
    }
}
```

Biên dịch và chạy:

```bash
javac TypeVariableDemo.java
java TypeVariableDemo
```

Đầu ra:

```text
Class variable: T
Class owner: TypeVariableDemo
Class bounds: [class java.lang.Number, java.lang.Comparable<T>]
Annotated bounds count: 2
Method variable: T
Method bounds: [interface java.lang.CharSequence]
Same T variable: false
Method owner equals: true
Constructor variable: C
Constructor bounds: [interface java.lang.Runnable]
Constructor owner equals: true
Field generic type: java.util.List<T>
Field argument equals class T: true
Field raw type: java.util.List
Fixed type parameters: 0
Fixed raw superclass: TypeVariableDemo
Fixed type argument: java.lang.Integer
```

Điểm cần quan sát:

1. Biến class `T` do `TypeVariableDemo.class` khai báo; bounds là `Number` và `Comparable<T>`.
2. Method cũng dùng tên `T` nhưng sở hữu một declaration riêng. Bởi vậy `classVar.equals(methodVar) == false`. Lời gọi `getMethod("echo", CharSequence.class)` sử dụng chữ ký đã erasure.
3. Generic constructor khai báo `C extends Runnable`; `getGenericDeclaration()` trỏ đến đối tượng `Constructor` tương ứng.
4. Field `List<T>` khi đọc `Field.getGenericType()` tạo `ParameterizedType`, có actual type argument là cùng biến class `T`. Nhưng `Field.getType()` chỉ là `List.class`.
5. `Fixed.class.getTypeParameters()` rỗng vì `Fixed` không tự khai báo biến kiểu. Thay vào đó, `Fixed.class.getGenericSuperclass()` giữ metadata `TypeVariableDemo<Integer>`; từ `getActualTypeArguments()` đọc được `Integer`.

Những giá trị trên là metadata từ khai báo Java, không phải kết quả suy ra kiểu chung từ mọi instance runtime.

## 6. Trùng tên không có nghĩa cùng biến kiểu

Ví dụ:

```java
class Outer<T> {
    public <T> T convert(T value) {
        return value;
    }
}
```

Hai biến đều có `getName()` là `"T"` nhưng khai báo ở hai nơi:

- `Outer.class.getTypeParameters()[0]` là biến class `T`.
- `Outer.class.getDeclaredMethod("convert", Object.class).getTypeParameters()[0]` là biến method `T`.

Method type parameter che khuất tên type parameter ở class trong phạm vi của nó. Theo ngữ nghĩa `equals()`, hai `TypeVariable` khác nhau vì chúng biểu diễn hai khai báo khác nhau.

Không dùng khóa map đơn giản như `"T" -> someType` khi làm generic resolver: T từ class khác, method khác hoặc constructor khác có thể trùng tên. Dùng cặp **declaration + name**, hoặc dùng chính `TypeVariable` với `equals()` và `hashCode()`.

API không bắt buộc Reflection trả về một **instance object duy nhất** cho mọi lần đọc cùng biến kiểu; đừng dựa trên `==` để nhận diện. `equals()` biểu thị biến kiểu theo declaration, có ý nghĩa hơn.

## 7. Đọc bounds của biến kiểu

`TypeVariable.getBounds()` trả mọi **upper bounds** theo thứ tự: `<T>` cho `Object`; `<T extends Number>` cho `Number`; `<T extends Number & Comparable<T>>` cho `Number` và `Comparable<T>`. Bound đầu tiên quyết định erasure; các bound còn lại vẫn ràng buộc kiểu hợp lệ. Mỗi phần tử là `Type`, do đó `Comparable<T>` là `ParameterizedType` và không được ép tất cả sang `Class`. Vòng tham chiếu `T extends Comparable<T>` cần theo dõi khi duyệt đệ quy.

Điểm cần phân biệt trong bài này là **bounds thuộc declaration**, không phải actual type arguments. Các quy tắc class/interface bounds, wildcard upper/lower, annotation trên bound và nhiều trường hợp biên được thực hành chuyên sâu ở mục học `getBounds()` tiếp theo.

## 8. Annotations trên type parameter và trên bound

`TypeVariable` triển khai `AnnotatedElement` để đọc annotation của **chính biến kiểu**; `getAnnotatedBounds()` giúp đọc annotations ở **vị trí sử dụng kiểu trong bound**.

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE_PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@interface ParameterHint {}

@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
@interface BoundHint {}

class AnnotatedExample<
        @ParameterHint T extends @BoundHint Number> {}
```

Trong ví dụ:

- `AnnotatedExample.class.getTypeParameters()[0].getAnnotations()` tìm annotation `@ParameterHint`.
- `getAnnotatedBounds()[0]` mô tả bound `Number` cùng annotation type-use `@BoundHint`.
- `getBounds()[0]` mô tả bản thân kiểu `Number`, không đầy đủ thông tin type-use annotation tại bound.

Phải có `RetentionPolicy.RUNTIME` khi muốn các annotation này xuất hiện qua Reflection tại runtime. Không nên trộn annotation trên type parameter với annotation trên kiểu trong bound.

## 9. Một `TypeVariable` có thể được gặp ở nơi sử dụng

Ngoài `getTypeParameters()`, nhiều API trả `Type` và có thể chứa một `TypeVariable`:

```java
class Holder<T> {
    T value;

    T get() { return value; }

    void set(T input) { value = input; }
}
```

Trong ngữ cảnh class `Holder`, ta có thể xem:

```java
TypeVariable<?> declared = Holder.class.getTypeParameters()[0];

Field field = Holder.class.getDeclaredField("value");
java.lang.reflect.Type fieldType = field.getGenericType();

Method getter = Holder.class.getDeclaredMethod("get");
java.lang.reflect.Type returnType = getter.getGenericReturnType();

Method setter = Holder.class.getDeclaredMethod("set", Object.class);
java.lang.reflect.Type parameterType =
        setter.getGenericParameterTypes()[0];
```

Đây là đoạn minh họa với imports `TypeVariable`, `Field`, `Method` và ngữ cảnh `throws Exception`. `T` không có bound rõ ràng nên erasure của setter parameter là `Object`.

Ba vị trí sử dụng trên đều nói tới biến `T` đã được `Holder` khai báo. Có thể ép kiểu sang `TypeVariable<?>` khi đã kiểm tra loại thực tế và dùng `getGenericDeclaration()`/`equals()` để nhận diện declaration.

Ngược lại, `Field.getType()` và `Method.getReturnType()` trả raw/erased types, không giữ đầy đủ cấu trúc generic. Khi cần đọc cấu trúc, dùng các phương thức bắt đầu bằng `getGeneric...` thích hợp.

## 10. Giải `T` trong ngữ cảnh một lớp con

Xét:

```java
class Base<T> {
    T item;
}

class Child extends Base<String> {}
```

`Base.class.getTypeParameters()[0]` vẫn là một biến kiểu `T`, không biến thành `String` khi truy cập từ `Child`. Muốn biết `T` tương ứng kiểu gì trong ngữ cảnh `Child`, phải:

1. Đọc `Child.class.getGenericSuperclass()` và xác nhận kết quả là `ParameterizedType` biểu diễn `Base<String>`.
2. Đọc type arguments qua `getActualTypeArguments()`.
3. Ánh xạ biến kiểu `T` của declaration `Base` với đối số kiểu `String` ở vị trí tương ứng.
4. Áp dụng ánh xạ vào generic field/method type nếu muốn biết kiểu hiệu dụng trong ngữ cảnh `Child`.

Với nhiều cấp như `Middle<U> extends Base<List<U>>` rồi `Leaf extends Middle<Integer>`, phải xử lý tuần tự các `ParameterizedType`, có thể là ánh xạ từ type variable này sang cấu trúc generic chứa type variable khác. Java Reflection cung cấp metadata nhưng không cung cấp một phương thức lõi duy nhất tự động giải mọi biến generic theo receiver subclass.

Điều này khác hoàn toàn với `getBounds()`: upper bound mô tả *điều kiện một type argument phải thỏa*, còn mapping theo subclass cho biết *type argument được khai báo* ở một quan hệ kế thừa cụ thể.

## 11. `TypeVariable` khác wildcard và parameterized type

Ví dụ bên trong class `<T>`:

```java
java.util.Map<String, java.util.List<? extends T>> values;
```

Nếu đọc `Field.getGenericType()`:

1. Outer `Map<...>` là `ParameterizedType`.
2. `String` là `Class<?>`.
3. `List<? extends T>` là một `ParameterizedType` khác.
4. `? extends T` là `WildcardType`.
5. Upper bound của wildcard là `T`, một `TypeVariable<?>` đã được class hoặc method khai báo.

Wildcard `?` **không tạo ra một declaration mới** như `T`; nó là một cấu trúc ở nơi sử dụng. `WildcardType.getUpperBounds()` nói về giới hạn wildcard, trong khi `TypeVariable.getBounds()` nói về giới hạn trên biến kiểu tại declaration.

Khi một generic parser trả về `Type`, hãy dùng `instanceof` phân nhánh theo `Class`, `ParameterizedType`, `TypeVariable`, `WildcardType` và `GenericArrayType`. Ép mọi giá trị thành `Class<?>` dễ phát sinh `ClassCastException` hoặc làm mất thông tin.

## 12. Những lỗi thực tế khi dùng Reflection generic

| Nhầm lẫn | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Hai biến đều tên `T` là một | Scope/declaration khác nhau | Xét `getGenericDeclaration()` và `equals()` |
| `getBounds()` chứa actual type argument | Bounds là giới hạn khai báo | Đọc `ParameterizedType.getActualTypeArguments()` khi cần |
| `getBounds()` của `<T>` là mảng rỗng | Bound ngầm định là `Object` | Nhận `Object.class` |
| Mọi bounds đều là `Class` | `Comparable<T>` là `ParameterizedType` | Phân loại từng `Type` |
| Lớp con tự động có type parameters của cha | `getTypeParameters()` chỉ trả biến khai báo trực tiếp | Đọc thêm generic superclass/interfaces |
| `getClass()` của `Box<Integer>` chứa `Integer` | Erasure bỏ type arguments runtime trên instance | Dùng metadata subclass/type token hoặc đầu vào rõ ràng |
| `Field.getType()` thể hiện `List<T>` | Nó trả raw class `List` | Dùng `Field.getGenericType()` |
| `getAnnotations()` trả annotations trên bounds | Annotation type parameter khác annotation type-use | Dùng `getAnnotatedBounds()` |
| Dùng `==` để so hai `TypeVariable` | Instance identity không được bảo đảm | Dùng `equals()` |
| Duyệt `T extends Comparable<T>` vô hạn | Bound có thể tự tham chiếu | Theo dõi biến/declaration đã thăm |

Metadata cũng có thể lỗi nếu generic signature tham chiếu đến type không hiện diện hoặc mô hình parameterized không hợp lệ; các API generic có thể ném `TypeNotPresentException` hoặc `MalformedParameterizedTypeException`. Bộ phân tích metadata nghiêm túc cần báo đúng nguyên nhân thay vì coi mọi lỗi là một biến kiểu rỗng.

Với bridge methods do compiler sinh ra khi generic bị erasure, `Method.isBridge()` và `isSynthetic()` giúp bộ quét method phân biệt khai báo thực tế với mã hỗ trợ compiler-generated. Đừng gộp tên method hoặc tên `T` làm khóa mà bỏ qua signature và declaration.

## 13. Quy trình dùng `TypeVariable` trong framework

Khi xây dựng mapper, serializer, schema inspector hay công cụ tài liệu:

1. **Xác định GenericDeclaration.** Đây là `Class`, `Method` hay `Constructor` nào? Gọi `getTypeParameters()` ở chủ thể đúng.
2. **Định danh biến kiểu.** Dùng `getName()` và `getGenericDeclaration()` hoặc `equals()`; chú ý method có thể khai báo biến cùng tên class.
3. **Đọc mọi bounds.** Dùng `getBounds()`, xử lý `ParameterizedType`, `TypeVariable` và các cấu trúc lồng nhau đúng cách.
4. **Đọc annotation đúng vị trí.** Dùng `getAnnotations()` cho type parameter, `getAnnotatedBounds()` cho annotations của bounds.
5. **Đọc nơi sử dụng.** Dùng `getGenericType()`, `getGenericReturnType()`, `getGenericParameterTypes()`, `getGenericSuperclass()` hoặc `getGenericInterfaces()` tùy mục tiêu.
6. **Giải đối số kiểu theo ngữ cảnh nếu cần.** Từ `ParameterizedType` của lớp con hoặc type token, xây mapping từ declaration variable sang type argument cụ thể. Không biến `TypeVariable` nguyên gốc thành một kiểu khác.
7. **Tính đến erasure và metadata thiếu.** Không cố suy ra `Integer` từ một `new Box<Integer>()` thông thường khi thông tin ấy không còn trong runtime class; xử lý cycles khi bound tự tham chiếu.

### Tự kiểm tra

**Câu 1.** `class A<T>` và `class B<T>` cùng khai báo tên `T`. Hai type variables có bằng nhau không?  
Không. Chúng thuộc hai `GenericDeclaration` khác nhau.

**Câu 2.** `class Child extends Parent<String>`. Vì sao `Child.class.getTypeParameters()` rỗng?  
Vì `Child` không khai báo type parameter mới. Kiểu `String` nằm ở `ParameterizedType` của `getGenericSuperclass()`.

**Câu 3.** Tại sao `getBounds()` của `T extends Number & Comparable<T>` không nên ép tất cả sang `Class`?  
Vì `Comparable<T>` là `ParameterizedType`.

**Câu 4.** Nếu method `<U> U echo(U x)` khai báo `U`, `getGenericDeclaration()` của U trả gì?  
Đối tượng `Method` sở hữu khai báo `U`, không phải mặc định class chứa method.

**Câu 5.** `TypeVariable.getBounds()` có cho biết chính xác type argument của mọi instance `Box<T>` không?  
Không. Bounds là ràng buộc; type arguments cụ thể thường bị erasure trên instance và chỉ đọc được khi metadata phù hợp tồn tại.

## Ghi nhớ

- **`GenericDeclaration`** là nơi khai báo tham số kiểu: class/interface, method hoặc constructor; đọc bằng `getTypeParameters()`.
- **`TypeVariable`** biểu diễn biến kiểu của declaration, có tên, owner, upper bounds và annotations.
- **Tên `T` không đủ để nhận diện biến.** Xét owner và dùng `equals()`.
- **Upper bound khác actual type argument.** `getBounds()` không thay thế `ParameterizedType.getActualTypeArguments()`.
- **Type erasure** khiến việc suy ra generic runtime phụ thuộc metadata còn tồn tại trong khai báo.

Khi hiểu rõ thứ tự **declaration → type variable → nơi sử dụng → type arguments theo ngữ cảnh**, ta có thể đọc và xử lý hệ thống generic bằng Java Reflection mà không nhầm lẫn biến kiểu với kiểu cụ thể.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-model-005">getBounds và nhiều giới hạn kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# getBounds() và nhiều giới hạn kiểu trong Java Reflection

## 1. Mục tiêu và vấn đề cốt lõi

Trong Java Generics, một tham số kiểu không nhất thiết chấp nhận mọi kiểu tham chiếu. Người lập trình có thể khai báo **giới hạn trên (upper bounds)** để yêu cầu kiểu thực tế phải là subtype của một lớp và/hoặc triển khai những interface nhất định:

```java
class Metric<T extends Number & Comparable<T> & java.io.Serializable> {
    T value;
}
```

Khai báo này yêu cầu `T` đồng thời:

- Là một subtype của `Number`.
- Thực hiện `Comparable<T>` (so sánh được với kiểu `T` tương ứng).
- Thực hiện `Serializable`.

Dấu `&` có nghĩa **giao (intersection)** của các ràng buộc kiểu; không phải phép AND trên giá trị runtime và cũng không phải nhiều đối số generic khác nhau.

Với Reflection, câu hỏi quan trọng là: **Làm sao đọc được từng bound, theo đúng thứ tự và đúng cấu trúc generic?** API trung tâm là `java.lang.reflect.TypeVariable.getBounds()`.

Bài học phân biệt rõ:

1. `TypeVariable.getBounds()` dùng cho type parameter như `T`, `U`.
2. `WildcardType.getUpperBounds()` và `getLowerBounds()` dùng cho wildcard như `? extends Number` và `? super Integer`.
3. Quy tắc **nhiều bounds**, class bound, interface bounds và **type erasure**.
4. Cách đọc các bounds phức tạp như `Comparable<T>`, annotation trên bound và bound tự tham chiếu.
5. Giới hạn của Reflection: biết **hợp đồng kiểu được khai báo**, chưa chắc biết kiểu cụ thể được dùng cho một object tại runtime.

## 2. Bắt đầu từ `TypeVariable<?>`

Một type parameter được phản ánh qua interface `java.lang.reflect.TypeVariable<D>`, trong đó `D` là dạng generic declaration sở hữu type parameter (class, method hoặc constructor).

Ví dụ:

```java
class Holder<T extends Number> {
    T item;
}
```

Để lấy metadata của `T`:

```java
TypeVariable<?> variable = Holder.class.getTypeParameters()[0];
System.out.println(variable.getName()); // T
```

Các API thiết yếu:

| API | Kết quả | Ý nghĩa |
|---|---|---|
| `getName()` | `String` | Tên khai báo của type variable (`T`, `U`...) |
| `getBounds()` | `Type[]` | Các **upper bounds** theo khai báo |
| `getAnnotatedBounds()` | `AnnotatedType[]` | Những upper bounds kèm type-use annotations |
| `getGenericDeclaration()` | `GenericDeclaration` | Class, method hoặc constructor **khai báo** type variable |
| `getTypeName()` | `String` | Tên biểu diễn type variable; thường là `T` |

Đây là đoạn minh họa rời, cần import `java.lang.reflect.TypeVariable` và đưa lớp `Holder` vào cùng ngữ cảnh; chương trình chạy được hoàn chỉnh ở mục 10.

Muốn lấy type parameters từ các điểm khai báo khác:

```java
TypeVariable<?>[] fromClass = Holder.class.getTypeParameters();
TypeVariable<?>[] fromMethod = someMethod.getTypeParameters();
TypeVariable<?>[] fromConstructor = someConstructor.getTypeParameters();
```

`someMethod` phải là một `java.lang.reflect.Method`, `someConstructor` là một `Constructor<?>` đã tra cứu hợp lệ. `Class`, `Method` và `Constructor` đều cung cấp `getTypeParameters()` vì chúng là những `GenericDeclaration`. Mảng trống nếu chính declaration đó không khai báo type parameter.

**Quan trọng:** Tham số kiểu của một class, ví dụ `Holder<T>`, và tham số kiểu của một method, ví dụ `<T> void work(T arg)`, là **hai khai báo khác nhau** dù cùng tên `T`. Dùng `getGenericDeclaration()` để phân biệt khi phân tích metadata.

## 3. `TypeVariable.getBounds()` thực sự trả gì?

Chữ ký:

```java
Type[] getBounds()
```

Giá trị trả về là mảng các đối tượng `java.lang.reflect.Type`, tương ứng với **những upper bounds của type variable**. Với nhiều bound, chúng xuất hiện theo thứ tự khai báo, vì vậy không được tự sắp xếp lại nếu đang phân tích ý nghĩa erasure.

Ba tình huống quan trọng:

| Khai báo | Nội dung `getBounds()` | Diễn giải |
|---|---|---|
| `class A<T> {}` | `[Object.class]` | Không ghi bound thì upper bound ngầm định là `Object` |
| `class B<T extends Number> {}` | `[Number.class]` | Một bound là class |
| `class C<T extends Number & Comparable<T> & Serializable> {}` | `[Number.class, Comparable<T>, Serializable.class]` | Ba bounds theo thứ tự; bound thứ hai là `ParameterizedType` |

Không nên giả định rằng các phần tử mảng đều là `Class<?>`. Bởi `getBounds()` trả `Type[]`, phần tử có thể biểu diễn:

- `Class<?>`: bound thông thường như `Number`, `Serializable`.
- `ParameterizedType`: bound có type arguments như `Comparable<T>` hoặc `Function<String, Integer>`.
- `TypeVariable<?>`: bound trỏ tới một type parameter khác, như `<A extends Number, B extends A>`.

Với trường hợp không khai báo bound, **`getBounds()` vẫn trả mảng một phần tử `Object.class`**, chứ không phải mảng rỗng. Do đó code liệt kê bounds nên xử lý `Object` như upper bound mặc định, không suy từ `array.length == 0` rằng “không có ràng buộc”.

Ví dụ:

```java
TypeVariable<?> t = C.class.getTypeParameters()[0];
for (Type bound : t.getBounds()) {
    System.out.println(bound.getTypeName());
}
```

Trong ngữ cảnh `C<T extends Number & Comparable<T> & Serializable>` đã khai báo, kết quả là:

```text
java.lang.Number
java.lang.Comparable<T>
java.io.Serializable
```

Các đoạn trên là minh họa phụ thuộc lớp `C` và import `java.lang.reflect.Type`, `TypeVariable`. `getTypeName()` phù hợp để hiển thị; khi cần phân tích nghĩa của một bound, dùng các interface `Type` thay vì cắt chuỗi ở ký tự `<` hoặc `>`.

## 4. Cú pháp nhiều giới hạn: class trước, interface sau

Java cho phép viết nhiều upper bounds cho một type parameter:

```java
class Processor<T extends Number & Comparable<T> & java.io.Serializable> {
}
```

Các luật nguồn quan trọng:

1. Nếu có **class bound**, class đó phải **đứng đầu**, theo sau (nếu có) là các interface.
2. Tối đa **một class** trong danh sách bounds (Java không hỗ trợ kế thừa nhiều class).
3. Có thể có **nhiều interface bounds**, nối bằng `&`.
4. Nếu **chỉ có interface bounds**, interface đầu tiên đứng đầu danh sách và quyết định erasure.
5. Khi không có `extends` thì upper bound ngầm định là `Object`.
6. Java không hỗ trợ `<T super Number>` cho khai báo type parameter. `super` là cú pháp lower bound của **wildcard**, không phải cú pháp bound cho `T`.
7. Các bounds cần tuân theo quy tắc kiểu Java, chẳng hạn không được lặp interface theo cách tạo ra những cách tham số hóa khác nhau có cùng erasure.

Một số ví dụ:

```java
// Hợp lệ: class trước, interfaces sau
class A<T extends Number & Comparable<T> & java.io.Serializable> {}

// Hợp lệ: chỉ interfaces
class B<T extends Runnable & AutoCloseable> {}

// Hợp lệ: một bound
class C<T extends Number> {}

// Hợp lệ: không khai báo bound (ngầm định Object)
class D<T> {}
```

Các khai báo sau **không hợp lệ** (chỉ dùng để nhận diện lỗi, không đưa vào chương trình cần biên dịch):

```java
// Sai: đặt class sau interface
// class WrongA<T extends Comparable<T> & Number> {}

// Sai: không thể có hai class bounds
// class WrongB<T extends Number & java.util.ArrayList<String>> {}

// Sai: khai báo type variable không cho phép "super"
// class WrongC<T super Integer> {}
```

Việc hỗ trợ nhiều interface bounds hữu ích khi thuật toán cần **nhiều khả năng đồng thời**. Ví dụ một kiểu thỏa `Runnable & AutoCloseable` vừa có thể được thực thi qua `run()` vừa có thể đóng qua `close()`, dù không buộc nó kế thừa một class riêng của thư viện.

## 5. Bound đầu tiên và type erasure: tại sao thứ tự quan trọng?

**Erasure** là một lý do phải giữ nguyên thứ tự bounds. Theo luật Java, type parameter được thay bằng erasure của **bound đầu tiên**; nếu không khai báo bound, là `Object`.

Ví dụ:

```java
class Numeric<T extends Number & Comparable<T> & java.io.Serializable> {
    T value;
}
```

Với `Numeric.value`:

- `field.getType()` trả `Number.class`, vì `T` bị erasure thành `Number`.
- `field.getGenericType()` trả một `TypeVariable<?>` biểu diễn `T`.
- `((TypeVariable<?>) field.getGenericType()).getBounds()` vẫn có cả `Number`, `Comparable<T>` và `Serializable`.

Như vậy **erasure không có nghĩa Reflection xóa sạch mọi chữ ký generic**. Erasure ảnh hưởng kiểu runtime/code liên quan, còn metadata signature generic có thể giữ cấu trúc khai báo để Reflection phân tích.

Khi chỉ có các interface bounds:

```java
class ComparableHolder<U extends Comparable<U> & java.io.Serializable> {
    U value;
}
```

`field.getType()` của `value` là `Comparable.class`, không phải `Serializable.class` và không phải `Object.class`. Interface đầu tiên giữ vai trò erasure khi không có class bound.

Khi không có bound:

```java
class AnyHolder<V> {
    V value;
}
```

`getType()` của `value` là `Object.class`; `getGenericType()` là `V`; `getBounds()` của `V` là `[Object.class]`.

Bảng so sánh:

| Khai báo tham số kiểu | Erasure của tham số | `getBounds()` |
|---|---|---|
| `<T>` | `Object` | `[Object]` |
| `<T extends Number>` | `Number` | `[Number]` |
| `<T extends Number & Serializable>` | `Number` | `[Number, Serializable]` |
| `<T extends Comparable<T> & Serializable>` | `Comparable` | `[Comparable<T>, Serializable]` |
| `<T extends Runnable & AutoCloseable>` | `Runnable` | `[Runnable, AutoCloseable]` |

**Không hoán đổi bounds để “đẹp hơn”** khi phân tích chữ ký: thứ tự có thể ảnh hưởng erasure và bytecode method/field descriptors. Nếu chỉ muốn hiển thị thứ tự alphabet cho giao diện, hãy hiểu rằng bạn đang tạo một cách trình bày phụ, không phải thứ tự nguồn có ý nghĩa ngữ nghĩa.

## 6. Phân tích từng bound theo cấu trúc `Type`

Với một type variable, đừng viết code như sau:

```java
Class<?> bound = (Class<?>) variable.getBounds()[0];
```

Cách ép kiểu trực tiếp chỉ hợp lệ nếu bound đầu tiên thật sự được biểu diễn bởi `Class<?>`. Với `T extends Comparable<T>`, bound đầu tiên là `ParameterizedType` và phép ép trên sẽ ném `ClassCastException`.

Hướng an toàn:

```java
for (Type bound : variable.getBounds()) {
    if (bound instanceof Class<?> clazz) {
        System.out.println("Class/interface: " + clazz.getName());
    } else if (bound instanceof ParameterizedType p) {
        System.out.println("Parameterized: " + p.getTypeName());
        System.out.println("Raw: " + p.getRawType().getTypeName());
    } else if (bound instanceof TypeVariable<?> other) {
        System.out.println("Type parameter khác: " + other.getName());
    } else {
        System.out.println("Loại Type khác: " + bound.getTypeName());
    }
}
```

Đoạn minh họa trên giả định đã có biến `variable` hợp lệ và import các kiểu thuộc `java.lang.reflect`. Các đối tượng `Type` có thể lồng nhau: với `Comparable<T>`, `getRawType()` cho `Comparable` và `getActualTypeArguments()[0]` cho type variable `T`. Phải đi vào `ParameterizedType` để giữ được type arguments.

Ví dụ:

```java
class Ranked<T extends Comparable<T>> {
}
```

```java
TypeVariable<?> t = Ranked.class.getTypeParameters()[0];
ParameterizedType p = (ParameterizedType) t.getBounds()[0];

System.out.println(p.getRawType().getTypeName());           // java.lang.Comparable
System.out.println(p.getActualTypeArguments()[0].getTypeName()); // T
```

Nếu cần hỗ trợ các kiểu phức tạp hơn, một bộ phân tích metadata có thể phải xử lý cả `GenericArrayType` và `WildcardType` khi duyệt sâu các type arguments. Nhưng **không phải mọi `Type` đều có `getBounds()`**: đây là phương thức chuyên biệt của `TypeVariable` (và các accessor tương ứng của `WildcardType`).

### Bound là một type variable khác

Java cho phép type parameter phụ thuộc type parameter được khai báo trước:

```java
class Pair<A extends Number, B extends A> {
    B value;
}
```

`Pair.class.getTypeParameters()[1].getBounds()[0]` phản ánh biến kiểu `A` (một `TypeVariable<?>`), chứ không thay tự động thành `Number.class`. Muốn suy ra bound hữu hiệu sâu hơn, công cụ cần đi tiếp tới bounds của `A` và tránh vòng lặp với những kiểu tự tham chiếu.

## 7. Giới hạn tự tham chiếu và xử lý chu trình

Một kiểu generic hợp lệ có thể tham chiếu lại chính type parameter của nó:

```java
class Node<T extends Comparable<T>> {
}
```

Một ví dụ quen thuộc hơn từ Java API:

```java
public abstract class Enum<E extends Enum<E>>
        implements Comparable<E>, java.io.Serializable {
    // Khai báo rút gọn để minh họa signature,
    // không phải toàn bộ mã nguồn của java.lang.Enum.
}
```

Đây là **F-bounded polymorphism**: bound của `E` là `Enum<E>`, trong đó type argument của `Enum` lại là chính `E`. Dữ liệu Reflection biểu diễn quan hệ này hợp lệ; đừng triển khai bộ hiển thị đệ quy theo cách liên tục phân tích `E` → `Enum<E>` → `E` → ... vô hạn.

Ví dụ đọc một cấp:

```java
TypeVariable<?> e = Enum.class.getTypeParameters()[0];
Type[] bounds = e.getBounds();
System.out.println(bounds[0].getTypeName()); // java.lang.Enum<E>
```

Nếu xây dựng schema graph, hãy lưu tập type variables/declarations đã đi qua để cắt chu trình. Khi so sánh type variables, dùng ngữ nghĩa `equals`/`getGenericDeclaration()` phù hợp; không giả định reference identity (`==`) luôn là cách duy nhất nhận diện metadata Reflection được biểu diễn lại.

Một biến kiểu có bound phức tạp **không đồng nghĩa** chương trình đã biết type argument cụ thể tại runtime. Ví dụ `class Holder<T extends Number>` khi nhận `Holder<Integer>` vẫn thường chỉ có một `Class` của `Holder` sau erasure; Reflection không đọc ra `Integer` từ một instance bất kỳ chỉ bằng `T.getBounds()`.

## 8. `getBounds()` của TypeVariable và upper/lower bounds của wildcard

Đây là nhầm lẫn thường gặp nhất khi xây generic inspector.

### 8.1. Type parameter: `T extends ...`

`TypeVariable.getBounds()` trả danh sách **upper bounds**. Type parameter không có API `getLowerBounds()` vì cú pháp khai báo `<T super X>` không được Java hỗ trợ.

Ví dụ:

```java
class Service<T extends Number & Comparable<T>> {
}
```

Kết quả là hai bounds: `Number`, `Comparable<T>`.

### 8.2. Wildcard: `? extends ...` hoặc `? super ...`

Wildcards xuất hiện trong type arguments, ví dụ:

```java
java.util.List<? extends Number> producers;
java.util.List<? super Integer> consumers;
java.util.List<?> unknown;
```

Mỗi wildcard được phản ánh qua `java.lang.reflect.WildcardType` với hai API riêng:

| Wildcard | `getUpperBounds()` | `getLowerBounds()` |
|---|---|---|
| `? extends Number` | `[Number]` | `[]` |
| `? super Integer` | `[Object]` | `[Integer]` |
| `?` | `[Object]` | `[]` |

Nếu wildcard không có upper bound tường minh, API trả upper bound `Object`. Nếu không có lower bound tường minh, `getLowerBounds()` trả **mảng rỗng**, không phải `[Object]`. Với `? super Integer`, upper bound mặc định vẫn là `Object`; lower bound là `Integer`.

Muốn tìm wildcard của một field khai báo `List<? extends Number>`:

```java
Field f = Example.class.getDeclaredField("producers");
ParameterizedType list = (ParameterizedType) f.getGenericType();
WildcardType wildcard =
        (WildcardType) list.getActualTypeArguments()[0];

for (Type upper : wildcard.getUpperBounds()) {
    System.out.println(upper.getTypeName());
}
```

Đây là đoạn minh họa phụ thuộc lớp `Example` có trường `producers`, cần import các reflection types tương ứng. Lưu ý `field.getGenericType()` là **`ParameterizedType` của `List`**, chưa phải `WildcardType`. Phải đi tới actual type argument mới lấy được wildcard.

### 8.3. “Nhiều bounds” ở wildcard có giống TypeVariable không?

**Không giống trong cú pháp Java source hiện nay.**

- Type parameter cho phép `<T extends Number & Comparable<T>>`.
- Wildcard dùng `? extends Number`, `? super Integer` hoặc `?`. Java source **không cho phép viết `List<? extends Number & Serializable>`** với intersection bound theo cú pháp type parameter.
- API `WildcardType.getUpperBounds()` và `getLowerBounds()` vẫn trả **mảng `Type[]`**; tài liệu lưu ý code dùng API nên được viết đủ tổng quát để xử lý nhiều phần tử nếu dạng kiểu trong tương lai có hỗ trợ. Không suy từ kiểu trả về mảng rằng source syntax hiện tại đã chấp nhận nhiều bounds của wildcard.

Các quy tắc generics như **PECS** (producer extends, consumer super) có ích khi quyết định thao tác đọc/ghi collection, nhưng đó là bài về type safety của collection. Trong Reflection, nhiệm vụ trực tiếp ở đây là xác định **cấu trúc bound metadata**, không phải tự ý thay đổi các phần tử của collection.

## 9. Annotation trên từng bound: `getAnnotatedBounds()`

Từ Java 8, annotation có thể được đặt trên **nơi sử dụng kiểu** (`ElementType.TYPE_USE`) nếu annotation được định nghĩa phù hợp.

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Marked {}

class Sample<T extends @Marked Number & @Marked Comparable<T>> {}
```

`TypeVariable.getBounds()` cung cấp các `Type`, giúp biết `Number` và `Comparable<T>`, nhưng không giữ giao diện truy vấn **annotation trên từng bound**. Để lấy những annotation đó, dùng:

```java
TypeVariable<?> t = Sample.class.getTypeParameters()[0];
AnnotatedType[] annotatedBounds = t.getAnnotatedBounds();

boolean firstMarked =
        annotatedBounds[0].isAnnotationPresent(Marked.class);
boolean secondMarked =
        annotatedBounds[1].isAnnotationPresent(Marked.class);
```

Hai đoạn này minh họa trong cùng ngữ cảnh; cần import `Retention`, `RetentionPolicy`, `Target`, `ElementType` và `AnnotatedType`. Một ví dụ chạy đầy đủ có type-use annotations được cung cấp ở mục 10.

Khác biệt giữa hai API:

- `getBounds()`: cấu trúc kiểu theo khai báo, với `Type[]`.
- `getAnnotatedBounds()`: cùng các upper bounds nhưng được bọc dưới `AnnotatedType[]` để đọc type-use annotations.
- `AnnotatedType.getType()` cho `Type` của bound tương ứng.
- `AnnotatedType.getAnnotations()` / `getAnnotation(...)` giúp đọc annotation trên **việc sử dụng kiểu**, không phải mọi annotation được khai báo trên bản thân class `Number` hoặc interface `Comparable`.

Khi không khai báo bound, `getAnnotatedBounds()` thể hiện upper bound `Object` không được gắn annotation. Chú ý retention policy: annotation chỉ có trong Reflection runtime nếu metadata được giữ ở runtime theo cách áp dụng.

## 10. Chương trình Java 17+ hoàn chỉnh: class, method, erasure, wildcard và annotation

Ví dụ sau dùng một file `BoundsDemo.java` và bao gồm:

- Nhiều bounds gồm class + interfaces.
- Nhiều bounds chỉ gồm interfaces.
- Type parameter không bound, bound tự tham chiếu và method type parameter.
- Bound có annotation tại nơi sử dụng kiểu.
- Phân biệt `TypeVariable.getBounds()` với các bounds của wildcard.
- Sự khác biệt giữa erasure và generic metadata khi truy vấn field qua subclass.

### 10.1. Mã nguồn

Lưu nguyên văn vào `BoundsDemo.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;

public class BoundsDemo {
    static class Multi<T extends Number & Comparable<T> & java.io.Serializable> {
        public T item;
    }

    static class IntegerMulti extends Multi<Integer> { }

    static class Interfaces<U extends Comparable<U> & java.io.Serializable> {
        U value;
    }

    static class Unbounded<V> {
        V value;
    }

    static class Recursive<E extends Enum<E>> { }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface Marked { }

    static class Annotated<A extends @Marked Number & @Marked Comparable<A>> { }

    static class Wildcards {
        List<? extends Number> upper;
        List<? super Integer> lower;
        List<?> any;
    }

    static <M extends Runnable & AutoCloseable> void process(M value) { }

    private static String names(Type[] types) {
        return Arrays.toString(Arrays.stream(types)
                .map(Type::getTypeName).toArray(String[]::new));
    }

    private static void wildcard(String fieldName) throws NoSuchFieldException {
        Field field = Wildcards.class.getDeclaredField(fieldName);
        ParameterizedType list = (ParameterizedType) field.getGenericType();
        WildcardType wildcard = (WildcardType) list.getActualTypeArguments()[0];
        System.out.println(fieldName + " upper=" + names(wildcard.getUpperBounds())
                + " lower=" + names(wildcard.getLowerBounds()));
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        TypeVariable<?> multi = Multi.class.getTypeParameters()[0];
        System.out.println("Multi bounds: " + names(multi.getBounds()));
        System.out.println("Multi erased: "
                + Multi.class.getDeclaredField("item").getType().getTypeName());
        System.out.println("Multi generic: "
                + Multi.class.getDeclaredField("item").getGenericType().getTypeName());

        System.out.println("Interfaces bounds: "
                + names(Interfaces.class.getTypeParameters()[0].getBounds()));
        System.out.println("Interfaces erased: "
                + Interfaces.class.getDeclaredField("value").getType().getTypeName());
        System.out.println("Unbounded bounds: "
                + names(Unbounded.class.getTypeParameters()[0].getBounds()));
        System.out.println("Recursive bounds: "
                + names(Recursive.class.getTypeParameters()[0].getBounds()));

        Method method = BoundsDemo.class.getDeclaredMethod("process", Runnable.class);
        System.out.println("Method bounds: "
                + names(method.getTypeParameters()[0].getBounds()));

        AnnotatedType[] annotated =
                Annotated.class.getTypeParameters()[0].getAnnotatedBounds();
        System.out.println("Annotated count: " + annotated.length);
        System.out.println("Annotations present: "
                + (annotated[0].isAnnotationPresent(Marked.class)
                && annotated[1].isAnnotationPresent(Marked.class)));

        wildcard("upper");
        wildcard("lower");
        wildcard("any");

        System.out.println("Subclass field generic: "
                + IntegerMulti.class.getField("item").getGenericType().getTypeName());
        System.out.println("Subclass generic parent: "
                + IntegerMulti.class.getGenericSuperclass().getTypeName());
    }
}
```

### 10.2. Biên dịch và chạy

Dùng JDK 17 trở lên:

```bash
javac --release 17 BoundsDemo.java
java BoundsDemo
```

Kết quả đã được đối chiếu qua biên dịch `--release 17` và chạy trên OpenJDK 21:

```text
Multi bounds: [java.lang.Number, java.lang.Comparable<T>, java.io.Serializable]
Multi erased: java.lang.Number
Multi generic: T
Interfaces bounds: [java.lang.Comparable<U>, java.io.Serializable]
Interfaces erased: java.lang.Comparable
Unbounded bounds: [java.lang.Object]
Recursive bounds: [java.lang.Enum<E>]
Method bounds: [java.lang.Runnable, java.lang.AutoCloseable]
Annotated count: 2
Annotations present: true
upper upper=[java.lang.Number] lower=[]
lower upper=[java.lang.Object] lower=[java.lang.Integer]
any upper=[java.lang.Object] lower=[]
Subclass field generic: T
Subclass generic parent: BoundsDemo$Multi<java.lang.Integer>
```

### 10.3. Giải thích kết quả từng nhóm

**`Multi<T>`:** Có ba upper bounds. `Number` đứng đầu nên field `item` có runtime type sau erasure là `Number`. Tuy vậy `getGenericType()` của field vẫn trả type variable `T`.

**`Interfaces<U>`:** Chỉ có interface bounds. `Comparable<U>` đứng đầu nên erasure thành `Comparable`; `Serializable` vẫn xuất hiện trong kết quả `getBounds()`.

**`Unbounded<V>`:** Không khai báo `extends` thì `getBounds()` trả `Object`.

**`Recursive<E>`:** Bound là `Enum<E>`, chứa tham chiếu ngược tới `E`. Đây là một dạng `ParameterizedType`, không phải `Enum.class` đơn thuần.

**Method `process`:** Tham số kiểu `M` thuộc **generic declaration của method**. `getDeclaredMethod("process", Runnable.class)` dùng `Runnable.class` vì đây là erasure của bound đầu tiên `M extends Runnable & AutoCloseable`.

**Type-use annotations:** Hai bounds của `A` đều có `@Marked`, vì vậy `getAnnotatedBounds()` trả hai phần tử và cả hai đều chứa annotation tại runtime.

**Wildcards:** `? extends Number` có upper bound `Number`, không có lower bound; `? super Integer` có upper bound mặc định `Object` và lower bound `Integer`; `?` có `Object` và không có lower bound.

**`IntegerMulti`:** Dù subclass cố định type argument `Integer`, field `item` vẫn do `Multi<T>` khai báo. Lấy field đó qua subclass, `getGenericType()` **vẫn là `T`**. Quan hệ `Multi<Integer>` nằm trong metadata `IntegerMulti.class.getGenericSuperclass()`, cần được kết hợp nếu muốn suy ra kiểu cụ thể theo ngữ cảnh subclass.

## 11. Ứng dụng của bounds trong framework và công cụ phân tích

### 11.1. Kiểm tra contract của generic API

Một thư viện plugin có thể yêu cầu type parameter phải đáp ứng nhiều khả năng:

```java
interface Task { void run(); }
interface Stoppable { void stop(); }

class TaskPool<T extends Task & Stoppable> { }
```

Một metadata scanner đọc `TaskPool.class.getTypeParameters()[0].getBounds()` sẽ biết contract yêu cầu **cả `Task` lẫn `Stoppable`**. Nếu chỉ xem raw erasure, có thể thấy `Task` rồi bỏ sót `Stoppable`.

Điều đó hữu ích khi tạo tài liệu API, kiểm tra chữ ký plugin hoặc phân tích schema; bản thân metadata không thay thế compile-time generic type checking.

### 11.2. Mapper / serializer có generic superclass

Một serializer có thể khảo sát `T extends Number` để biết contract cơ bản của trường; nhưng khi object thuộc subclass `IntegerMulti`, muốn biết `T = Integer` phải đọc thêm `ParameterizedType` của generic superclass và xây ánh xạ biến kiểu (`T`) sang type argument (`Integer`).

`getBounds()` trả về **giới hạn cho phép**, không trả về **type argument thực tế được chọn**. Đó là hai câu hỏi độc lập:

- `T extends Number` trả lời “`T` tối thiểu phải thỏa kiểu nào?”
- `Multi<Integer>` trả lời “trong quan hệ kế thừa cụ thể này, `T` được gán kiểu nào?”

Không suy rằng khi `T extends Number`, mọi instance đều chứa `Integer`; `Double` hoặc một subtype `Number` khác có thể cũng hợp lệ nếu thỏa các interface bounds bổ sung.

### 11.3. Xây generic inspector robust

Một inspector thường cần:

1. Lấy `Class.getTypeParameters()` hoặc `Method.getTypeParameters()`.
2. Với mỗi `TypeVariable<?>`, lưu `getName()` và `getGenericDeclaration()`.
3. Duyệt **tất cả** phần tử `getBounds()` theo đúng thứ tự.
4. Phân loại từng `Type` bằng `instanceof`.
5. Khi có `ParameterizedType`, đi tiếp vào `getRawType()` và `getActualTypeArguments()`.
6. Với type-use annotations, đọc `getAnnotatedBounds()` theo thứ tự tương ứng.
7. Với recursive bounds, nhận diện chu trình; với superclass generic, lập ánh xạ type variables theo ngữ cảnh.

**Không bắt buộc dùng `setAccessible(true)`** để đọc metadata của generic type parameters. Access suppression liên quan đến thao tác vào thành viên bị hạn chế quyền, không phải điều kiện chung cho `getBounds()`.

## 12. Lỗi phổ biến và cách tránh

| Sai lầm | Nguyên nhân | Cách sửa |
|---|---|---|
| Nghĩ `getBounds()` rỗng khi `<T>` | Không khai báo bound vẫn có `Object` ngầm định | Xử lý `[Object.class]` |
| Chỉ đọc `getBounds()[0]` | Bỏ sót các interface bounds bổ sung | Duyệt toàn bộ `Type[]` |
| Ép mọi bound thành `Class<?>` | `Comparable<T>` là `ParameterizedType` | Phân loại `Type` bằng `instanceof` |
| Đổi thứ tự bounds tùy ý | Erasure dựa trên bound đầu tiên | Giữ nguyên thứ tự nguồn |
| Cho rằng nhiều bound = nhiều class | Java không có multiple class inheritance | Chỉ một class bound, còn lại interfaces |
| Viết `T super Number` | Type parameter không có lower-bound syntax | Dùng `extends` cho type parameter; `? super` cho wildcard |
| Viết `List<? extends A & B>` trong Java source | Wildcard không hỗ trợ intersection syntax này | Dùng type parameter `<T extends A & B>` ở declaration thích hợp |
| Gọi `getBounds()` trực tiếp trên `ParameterizedType` | `ParameterizedType` không có API này | Duyệt `getActualTypeArguments()`, rồi nhận diện `TypeVariable` hoặc `WildcardType` |
| Nghĩ `? super Integer` có upper bound là `Integer` | `Integer` là **lower bound** | `upper=[Object]`, `lower=[Integer]` |
| Cho rằng `getBounds()` cho type cụ thể của `Box<Integer>` | Bounds là metadata của declaration | Xem `getGenericSuperclass()`/generic interfaces và giải biến kiểu |
| Đệ quy vô hạn với `Enum<E extends Enum<E>>` | Bound tham chiếu lại type variable | Ghi nhận các declaration/type variables đã thăm |
| Tìm bound annotation trong `getBounds()` | `Type[]` không có giao diện annotation-use | Dùng `getAnnotatedBounds()` |
| Dùng `Type.getTypeName()` để parse generic bằng regex đơn giản | Type lồng nhau và wildcard làm parsing thủ công dễ sai | Phân tích cấu trúc `Type` |
| Giả định metadata luôn hoàn hảo | Generic signature có thể thiếu/không hợp lệ | Xử lý `TypeNotPresentException`, `MalformedParameterizedTypeException`, `GenericSignatureFormatError` phù hợp |

Một số lưu ý thêm:

- `TypeVariable.getBounds()` có thể kích hoạt việc phân giải kiểu được nhắc tới trong bounds. Với metadata không hợp lệ hoặc type không thể tải, Javadoc cho phép `TypeNotPresentException` hoặc `MalformedParameterizedTypeException` trong những trường hợp cụ thể.
- Chữ ký generic lấy từ bytecode/class metadata. Đừng dùng kết quả Reflection làm cơ chế kiểm chứng an ninh để cho phép tự do tải/chạy class tùy ý; việc đọc schema và việc thực thi mã là hai hoạt động khác nhau.
- Hai type variables trùng tên `T` có thể thuộc các generic declarations khác nhau. Khi xây bảng tra cứu, khóa nhận diện cần tính tới declaration, không chỉ tên ký tự.

## 13. Tự kiểm tra kiến thức

1. Với `class A<T> {}`, `A.class.getTypeParameters()[0].getBounds()` trả mảng mấy phần tử, và phần tử đó là gì?
2. Với `T extends Number & Comparable<T> & Serializable`, tại sao có ba bounds nhưng field kiểu `T` chỉ có một raw `Class<?>` là `Number`?
3. Tại sao `getBounds()[1]` của `Comparable<T>` không nên ép về `Class<?>`?
4. Nếu chỉ viết `T extends Runnable & AutoCloseable`, interface nào quyết định erasure?
5. `<T extends A & B>` khác `List<? extends A>` ở chỗ nào? Có dùng `? extends A & B` được trong Java source không?
6. `getBounds()` và `getAnnotatedBounds()` khác nhau ở dữ liệu nào?
7. Vì sao bound `Enum<E>` của `E extends Enum<E>` có thể dẫn tới vòng lặp khi phân tích đệ quy?
8. Khi field `T` khai báo ở superclass generic nhưng truy vấn qua subclass `Superclass<Integer>`, tại sao `Field.getGenericType()` vẫn có thể trả `T`?
9. Với `List<? super Integer>`, lower bound và upper bound của wildcard lần lượt là gì?
10. Muốn hiển thị tất cả upper bounds của method type parameter, bắt đầu từ đối tượng `Method` và gọi các API nào?

**Ghi nhớ:** `TypeVariable.getBounds()` trả **toàn bộ upper bounds** của một tham số kiểu, với `Object` ngầm định nếu không khai báo bound. Khi có nhiều bounds, **bound đầu tiên quyết định erasure**, nhưng metadata generic còn giữ những bounds bổ sung. `WildcardType` có API upper/lower bounds riêng, và `getAnnotatedBounds()` dùng để khảo sát annotations trên từng giới hạn kiểu.

Tài liệu chuẩn để đối chiếu: [TypeVariable (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/TypeVariable.html), [WildcardType (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/WildcardType.html), [ParameterizedType (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html), [AnnotatedType (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedType.html), [JLS §4.4 — Type Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.4), [JLS §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-model-006">WildcardType với upper bound và lower bound</a>

<details>
<summary>Xem chi tiết</summary>

# WildcardType với upper bound và lower bound trong Java Reflection

Trong Java generics, dấu `?` biểu diễn một **wildcard**: đối số kiểu chưa biết tại một vị trí sử dụng kiểu generic. Ví dụ `List<?>`, `List<? extends Number>` và `List<? super Integer>` đều là những `ParameterizedType` ở cấp `List<...>`, và mỗi dấu `?` bên trong được mô hình hóa bằng `java.lang.reflect.WildcardType`.

`WildcardType` là một nhánh của `java.lang.reflect.Type`. Nó cung cấp hai phương thức quan trọng:

- `Type[] getUpperBounds()`: lấy giới hạn trên của kiểu chưa biết.
- `Type[] getLowerBounds()`: lấy giới hạn dưới của kiểu chưa biết.

Bài học tập trung vào cách đọc **chữ ký generic đã khai báo** bằng Reflection, diễn giải hai mảng bound, đi qua các kiểu generic lồng nhau và phân biệt generic metadata với dữ liệu runtime.

## 1. Ý nghĩa của ba dạng wildcard

| Khai báo | Upper bounds qua Reflection | Lower bounds qua Reflection | Ý nghĩa |
| --- | --- | --- | --- |
| `List<?>` | `[Object]` | `[]` | Một đối số kiểu không xác định |
| `List<? extends Number>` | `[Number]` | `[]` | Đối số kiểu là `Number` hoặc subtype của `Number` |
| `List<? super Integer>` | `[Object]` | `[Integer]` | Đối số kiểu là `Integer` hoặc supertype của `Integer` |

**Upper bound** của `? extends T` là `T`: bất kỳ type argument thực tế nào đều phải thỏa ràng buộc subtype tương ứng. **Lower bound** của `? super T` là `T`: type argument thực tế phải là `T` hoặc supertype của `T`.

Với `?`, upper bound mặc định là `Object` và không có lower bound. Với `? super Integer`, upper bound mặc định **vẫn là `Object`**, còn lower bound là `Integer`. Vì vậy đừng kết luận `getUpperBounds()` rỗng khi khai báo dùng `super`.

Một điểm rất dễ nhầm: `getLowerBounds().length == 0` **không** chứng minh wildcard là `?`; `? extends Number` cũng có mảng lower rỗng. Ngược lại, upper bound `Object` không đủ để kết luận khai báo chỉ là `?`, vì `? super T` và `? extends Object` cũng có cùng giới hạn trên.

## 2. WildcardType nằm trong cây Type ở đâu?

Các kiểu mô tả generic thuộc `java.lang.reflect.Type` bao gồm:

```text
Type
├── Class<?>            ví dụ String.class, Number.class
├── ParameterizedType   ví dụ List<String>, List<? extends Number>
├── TypeVariable<?>     ví dụ T trong class Box<T>
├── WildcardType        ví dụ ?, ? extends Number, ? super Integer
└── GenericArrayType    ví dụ T[] trong chữ ký generic
```

Nếu field khai báo `List<? extends Number> numbers`, lời gọi `Field.getGenericType()` trả một `ParameterizedType` đại diện cho **toàn bộ** `List<? extends Number>`. Để lấy `WildcardType` cần vào `getActualTypeArguments()[0]`:

```text
Field: List<? extends Number> numbers
                 |
                 v
       field.getGenericType()
                 |
                 v
    ParameterizedType: List<? extends Number>
                 |
                 v
       getActualTypeArguments()[0]
                 |
                 v
      WildcardType: ? extends Number
             /                 \
            v                   v
    getUpperBounds()       getLowerBounds()
       [Number]                 []
```

Không ép trực tiếp `field.getGenericType()` sang `WildcardType`, vì kiểu field ở đây là `ParameterizedType`. Với `List<String>`, đối số đầu tiên là `String.class`, **không** phải `WildcardType`. Với `List<T>`, đối số đầu tiên có thể là `TypeVariable`.

## 3. Chương trình Java 17+ đầy đủ và stdout

Lưu mã vào tệp `WildcardTypeDemo.java`. Chương trình tra cứu field, tham số method, wildcard lồng nhau và bound là generic type; sau đó minh họa thao tác đọc/ghi theo `extends`/`super`.

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class WildcardTypeDemo {
    static class Examples {
        List<?> anything;
        List<? extends Number> numbers;
        List<? super Integer> integers;
        List<? extends Comparable<String>> comparable;
        Map<String, ? super List<String>> mappings;
        List<List<? extends Number>> nested;

        void accept(List<? super Integer> values) {
        }
    }

    static Type argument(String fieldName, int index)
            throws NoSuchFieldException {
        Field field = Examples.class.getDeclaredField(fieldName);
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType parameterized)) {
            throw new IllegalArgumentException("Not parameterized");
        }
        return parameterized.getActualTypeArguments()[index];
    }

    static String format(Type[] bounds) {
        return Arrays.stream(bounds)
                .map(Type::getTypeName)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    static void printBounds(String label, Type type) {
        if (!(type instanceof WildcardType wildcard)) {
            throw new IllegalArgumentException("Not wildcard");
        }
        System.out.println(label
                + ": upper=" + format(wildcard.getUpperBounds())
                + "; lower=" + format(wildcard.getLowerBounds()));
    }

    public static void main(String[] args) throws Exception {
        printBounds("unbounded", argument("anything", 0));
        printBounds("extends", argument("numbers", 0));
        printBounds("super", argument("integers", 0));
        printBounds("parameterizedUpper",
                argument("comparable", 0));
        printBounds("parameterizedLower",
                argument("mappings", 1));

        Type inner = argument("nested", 0);
        ParameterizedType innerList = (ParameterizedType) inner;
        printBounds("nested",
                innerList.getActualTypeArguments()[0]);

        Method method = Examples.class.getDeclaredMethod(
                "accept", List.class);
        ParameterizedType parameter = (ParameterizedType)
                method.getGenericParameterTypes()[0];
        printBounds("method",
                parameter.getActualTypeArguments()[0]);

        System.out.println("erased="
                + Examples.class.getDeclaredField("numbers")
                        .getType().getName());

        List<? extends Number> source = List.of(1, 2);
        Number first = source.get(0);
        List<Number> storage = new ArrayList<>();
        List<? super Integer> target = storage;
        target.add(7);
        Object stored = target.get(0);

        System.out.println("producerRead=" + first);
        System.out.println("consumerRead=" + stored);
    }
}
```

Tại thư mục chứa tệp, biên dịch và chạy:

```bash
javac WildcardTypeDemo.java
java WildcardTypeDemo
```

Đầu ra dự kiến:

```text
unbounded: upper=[java.lang.Object]; lower=[]
extends: upper=[java.lang.Number]; lower=[]
super: upper=[java.lang.Object]; lower=[java.lang.Integer]
parameterizedUpper: upper=[java.lang.Comparable<java.lang.String>]; lower=[]
parameterizedLower: upper=[java.lang.Object]; lower=[java.util.List<java.lang.String>]
nested: upper=[java.lang.Number]; lower=[]
method: upper=[java.lang.Object]; lower=[java.lang.Integer]
erased=java.util.List
producerRead=1
consumerRead=7
```

**Diễn giải từng trường hợp:**

1. `anything` là `List<?>`, nên giới hạn trên `Object`, không có giới hạn dưới.
2. `numbers` là `List<? extends Number>` nên giới hạn trên `Number`.
3. `integers` là `List<? super Integer>` nên giới hạn dưới `Integer`, giới hạn trên ngầm định là `Object`.
4. Bound của `? extends Comparable<String>` không phải `Class<?>` đơn giản mà là một `ParameterizedType`; `getTypeName()` cho ra `java.lang.Comparable<java.lang.String>`.
5. Với `Map<String, ? super List<String>>`, wildcard nằm ở **đối số kiểu thứ hai** của `Map`; lower bound `List<String>` cũng là một `ParameterizedType`.
6. `List<List<? extends Number>>` có một tầng `List` bên trong. Phải đi qua đối số kiểu là `List<? extends Number>` trước, rồi mới tìm wildcard `? extends Number`.
7. `accept(List<? super Integer>)` được tra cứu bằng `getDeclaredMethod("accept", List.class)` vì các tham số generic bị erase ở chữ ký method dùng cho lookup. Sau đó `getGenericParameterTypes()` mới cung cấp chữ ký generic chứa wildcard.
8. `Field.getType()` trả `java.util.List` cho field `List<? extends Number>`; nó không chứa thông tin wildcard. `Field.getGenericType()` mới cho phép lấy `ParameterizedType`.
9. Hai dòng cuối dùng collection thông thường: `? extends Number` cho phép đọc như `Number`; `? super Integer` cho phép thêm `Integer` và đọc an toàn như `Object`. Đây là các ràng buộc được compiler kiểm tra, không phải Reflection đang kiểm soát collection ở runtime.

Chương trình dùng pattern matching cho `instanceof` có trong Java 17 và định dạng bound qua `Type.getTypeName()`, tránh phụ thuộc vào `toString()` của một implementation cụ thể.

## 4. Đọc bound qua Type[] thay vì ép về Class[]

Cả `getUpperBounds()` và `getLowerBounds()` đều trả về **`Type[]`**. Các phần tử có thể là `Class<?>` hoặc các biểu diễn generic phức tạp hơn, đặc biệt là `ParameterizedType`. Không viết code giả định mọi bound đều là `Class<?>`:

```java
// Đoạn minh họa khi đã có đối tượng WildcardType wildcard:
for (Type upper : wildcard.getUpperBounds()) {
    System.out.println(upper.getTypeName());
    if (upper instanceof ParameterizedType parameterized) {
        System.out.println(parameterized.getRawType().getTypeName());
    }
}

for (Type lower : wildcard.getLowerBounds()) {
    System.out.println(lower.getTypeName());
}
```

Đoạn này **không phải chương trình độc lập**; nó cần biến `wildcard` và các import `Type`, `ParameterizedType`. Có thể còn gặp `TypeVariable` hoặc những dạng `Type` khác trong metadata phức tạp. Vì thế, khi cần xử lý nghiêm túc, hãy duyệt toàn bộ mảng `Type[]` và phân loại từng phần tử bằng `instanceof`.

Trong cú pháp Java nguồn thường gặp, `? extends T` cho upper bound `T` còn `? super T` cho lower bound `T`. Không nên viết code suy luận tuyệt đối “có upper bound nghĩa là extends”: cả wildcard không ràng buộc và wildcard `super` đều có upper bound mặc định `Object`. Khi Reflection mô tả các bounds, nó phản ánh ý nghĩa kiểu, không nhất thiết lưu lại mọi chi tiết cú pháp người viết đã dùng.

## 5. Tìm wildcard trong field, method và generic superclass/interface

Bước đầu tiên luôn là chọn API Reflection **generic**, không dùng API chỉ trả raw class:

| Vị trí khai báo | API đọc generic metadata | Cách tìm wildcard |
| --- | --- | --- |
| Field `List<? extends Number> values` | `Field.getGenericType()` | `ParameterizedType.getActualTypeArguments()` |
| Parameter `void take(List<? super Integer> values)` | `Method.getGenericParameterTypes()[i]` | Lấy đối số kiểu của `ParameterizedType` |
| Return type `List<?> load()` | `Method.getGenericReturnType()` | Lấy đối số kiểu của `ParameterizedType` |
| `class Child extends Parent<? extends Number>` | `Class.getGenericSuperclass()` | Kiểm tra `ParameterizedType` |
| `class HandlerImpl implements Handler<? super Integer>` | `Class.getGenericInterfaces()` | Kiểm tra từng `Type` trong mảng |

Ví dụ khai báo return type:

```java
public List<? extends Number> find() {
    return List.of(1, 2);
}
```

Với biến `Method method` trỏ đúng vào `find`, logic đọc là:

```java
Type result = method.getGenericReturnType();
if (result instanceof ParameterizedType parameterized) {
    Type first = parameterized.getActualTypeArguments()[0];
    if (first instanceof WildcardType wildcard) {
        System.out.println(
                wildcard.getUpperBounds()[0].getTypeName());
    }
}
```

Hai đoạn trên là **minh họa ngữ cảnh**, không phải một chương trình đầy đủ: cần đặt method vào class, lấy `Method` tương ứng và import các loại `Type` cần thiết. Trong trường hợp nêu trên, dòng in là `java.lang.Number`.

Lưu ý sự khác biệt giữa `Method.getParameterTypes()` (trả `Class<?>[]` sau erasure) và `getGenericParameterTypes()` (trả `Type[]` giữ metadata generic). Tương tự, `Field.getType()` và `getGenericType()` phục vụ hai mục tiêu khác nhau.

## 6. WildcardType không phải TypeVariable

Ví dụ:

```java
class Box<T extends Number> {
    T element;
    List<? extends Number> elements;
}
```

- `T` trong khai báo `Box<T extends Number>` là **type variable có tên**, được mô hình hóa bằng `TypeVariable<?>`. Khi đọc `element` qua `getGenericType()`, có thể gọi `TypeVariable.getBounds()` để biết `T` có bound `Number`.
- `? extends Number` trong `List<? extends Number>` là một **wildcard** được mô hình hóa bằng `WildcardType` khi đi vào đối số kiểu của `ParameterizedType`.

Nếu field là `List<T>`, đối số kiểu là `TypeVariable`, không phải wildcard — mặc dù `T` có thể có cùng bound `Number`. Vì thế không ép mọi đối số generic có `extends` thành `WildcardType`. Type variable là tham số có tên được tái sử dụng trong phạm vi generic declaration; wildcard là một đối số kiểu chưa biết ở **vị trí sử dụng**.

Điều này ảnh hưởng tới công cụ giải kiểu: để giải `T` theo một subclass cụ thể, có thể phải đọc các tham số generic của superclass/interface và lập ánh xạ type variable. Chỉ gọi `WildcardType.getUpperBounds()` không giải được một `TypeVariable`.

## 7. PECS: vì sao extends để đọc và super để ghi?

Quy tắc **Producer Extends, Consumer Super (PECS)** mô tả một cách chọn wildcard theo vai trò của collection:

**`? extends T`** thường phù hợp khi API chỉ cần **đọc** dữ liệu như `T`. Ví dụ:

```java
static double sum(List<? extends Number> numbers) {
    double result = 0;
    for (Number number : numbers) {
        result += number.doubleValue();
    }
    return result;
}
```

Với `List<? extends Number>`, type argument thực tế có thể là `Integer`, `Double` hoặc `Number`. Không được thêm một giá trị `Number` bất kỳ vì có thể không hợp với type argument chưa biết; trường hợp đặc biệt `null` vẫn có thể được thêm.

**`? super T`** phù hợp khi API cần **đưa vào** giá trị kiểu `T`:

```java
static void appendOne(List<? super Integer> destination) {
    destination.add(1);
}
```

Collection có thể thực tế là `List<Integer>`, `List<Number>` hoặc `List<Object>`; cả ba đều nhận được `Integer`. Nhưng khi đọc từ `List<? super Integer>`, kiểu chung được bảo đảm là `Object`, không thể tự động gán cho `Integer` nếu không có kiểm tra/ép kiểu.

**`?`** dùng khi code không cần biết type argument cụ thể. Có thể đọc như `Object`, nhưng không thể thêm tùy ý một giá trị khác `null`.

Hai method trong phần này là **mã minh họa**, cần import `java.util.List` và đặt trong class. `WildcardType` chỉ giúp Reflection **quan sát** chữ ký khai báo; nó không thực thi quy tắc PECS ở runtime. Compiler mới là nơi kiểm tra phần lớn ràng buộc generics trong source Java.

## 8. Type erasure và giới hạn của generic metadata

Giả sử code tạo hai object:

```java
List<String> names = new ArrayList<>();
List<Integer> counts = new ArrayList<>();
```

Trong tình huống này, `names.getClass()` và `counts.getClass()` đều là `java.util.ArrayList.class`. Đối số kiểu `String` hay `Integer` của từng biến không tự tồn tại như thuộc tính runtime có thể truy vấn từ object bằng `getClass()`.

Ngược lại, chữ ký của **field được khai báo** là `List<? super Integer>` vẫn có thể được lưu trong bytecode, cho phép `Field.getGenericType()` đọc được wildcard. Phải phân biệt:

1. **Runtime class của object** — ví dụ `ArrayList.class`.
2. **Chữ ký generic được khai báo** — ví dụ `List<? extends Number>`.
3. **Các phần tử thực tế trong collection** — có thể cần kiểm tra từng giá trị; generic signature không thay thế validation.

Raw types, unchecked casts hoặc bytecode không tuân thủ generic type safety có thể phá giả định của source-level generics. Đừng xem `getUpperBounds()` là bằng chứng rằng tất cả phần tử runtime trong một collection đều phù hợp với bound.

Khi phân tích class file không nhất quán hoặc có kiểu không còn tồn tại, các API generic có thể phát sinh `TypeNotPresentException`, `MalformedParameterizedTypeException` hoặc `GenericSignatureFormatError` tùy API/ngữ cảnh. Ngoại lệ `ClassCastException` khi bạn ép `ParameterizedType` sang `WildcardType` lại là lỗi logic phân nhánh `Type` của chính ứng dụng.

## 9. Các nhầm lẫn thường gặp

| Nhầm lẫn | Điều đúng |
| --- | --- |
| `getUpperBounds()` của `? super Integer` là mảng rỗng | Upper bound ngầm định là `Object` |
| `getLowerBounds()` rỗng nghĩa là `?` | `? extends T` cũng có lower bound rỗng |
| `? extends Number` chỉ tương ứng đúng `Number` | Có thể tương ứng subtype của `Number` |
| `? super Integer` đọc ra chắc chắn `Integer` | Đọc theo type safety chỉ được bảo đảm là `Object` |
| `Field.getGenericType()` của `List<?>` là `WildcardType` | Nó là `ParameterizedType`; wildcard là type argument bên trong |
| Mọi bound đều là `Class<?>` | Bound có thể là `ParameterizedType` như `Comparable<String>` |
| `T extends Number` và `? extends Number` đều là wildcard | `T` là `TypeVariable`, `?` là `WildcardType` |
| `Field.getType()` cho biết wildcard | `getType()` trả raw/erased class; dùng `getGenericType()` |
| Có thể viết `List<?>.class` | Java không có class literal cho parameterized type; đọc từ declaration |
| Reflection kiểm tra type argument thật của mọi collection object | Reflection đọc metadata khai báo, không thay thế generic type checking ở compile time |

## 10. Quy trình áp dụng khi xây công cụ đọc generic

1. Xác định nơi chứa **chữ ký**: field, parameter, return type, superclass hay interface.
2. Dùng API `getGeneric...` phù hợp để nhận `Type`.
3. Phân nhánh bằng `instanceof`: `ParameterizedType` để đi vào type arguments; `WildcardType` để đọc upper/lower; `TypeVariable` để đọc generic parameter bounds.
4. Với generic lồng nhau như `List<List<? extends Number>>`, đi từng tầng `ParameterizedType`, không giả định wildcard luôn nằm ở chỉ số đầu tiên của tầng ngoài.
5. Duyệt toàn bộ `Type[]` của upper/lower bounds, giữ thông tin `ParameterizedType` khi bound còn chứa type argument.
6. Diễn giải theo `extends`/`super` và nhu cầu đọc/ghi thực tế; không dùng Reflection metadata như phép xác thực phần tử runtime.

**Ghi nhớ:** `WildcardType` biểu diễn **`?` bên trong chữ ký generic**. `getUpperBounds()` mô tả giới hạn trên; `getLowerBounds()` mô tả giới hạn dưới. `?` có upper `Object` và lower rỗng; `? extends T` dùng upper `T`; `? super T` dùng lower `T` và upper `Object` ngầm định. Để tìm wildcard trong `List<? ...>`, phải đi qua `ParameterizedType.getActualTypeArguments()` trước.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-model-007">GenericArrayType khác Class của mảng cụ thể</a>

<details>
<summary>Xem chi tiết</summary>

# GenericArrayType khác Class của mảng cụ thể trong Java Reflection

## 1. Vì sao cùng là mảng nhưng Reflection trả về hai loại Type?

Khi đọc thông tin kiểu generic bằng Reflection, không phải mọi kiểu mảng đều được biểu diễn bằng `Class<?>`. API `java.lang.reflect.Type` có nhiều dạng triển khai, trong đó:

- **`Class<?>`** biểu diễn kiểu thực tế có thể được JVM nhận diện tại runtime: `String[]`, `int[]`, `Number[]`, `List[]`, `String[][]`, v.v.
- **`GenericArrayType`** mô tả **khai báo kiểu mảng mà kiểu thành phần là `TypeVariable` hoặc `ParameterizedType`** (hoặc ở mảng nhiều chiều, là một `GenericArrayType` khác). Ví dụ `T[]`, `List<String>[]`, `T[][]`.
- Một `GenericArrayType` là **metadata về kiểu generic trong khai báo**, không phải một kiểu mảng runtime mới, không phải đối tượng mảng và không tự cung cấp `Class` của tham số generic đã được giữ lại trong runtime.

Ví dụ:

```java
class Box<T extends Number> {
    T[] genericValues;
    java.util.List<String>[] parameterizedValues;
    String[] concreteValues;
    int[] primitiveValues;
}
```

Nếu dùng `Field.getGenericType()`:

| Khai báo field | `getGenericType()` thường là | Ý nghĩa |
| --- | --- | --- |
| `T[] genericValues` | `GenericArrayType` | Thành phần `T` là `TypeVariable` |
| `List<String>[] parameterizedValues` | `GenericArrayType` | Thành phần `List<String>` là `ParameterizedType` |
| `String[] concreteValues` | `Class<?>` | Kiểu mảng cụ thể `String[].class` |
| `int[] primitiveValues` | `Class<?>` | Kiểu mảng cụ thể `int[].class` |
| `T[][] nestedValues` | `GenericArrayType` | Thành phần ngoài là `T[]`, lại là `GenericArrayType` |

**Điểm cần nhớ:** “Có dấu `[]`” chỉ cho biết cấu trúc mảng. Muốn biết Reflection trả về `Class` hay `GenericArrayType`, phải xem **đối tượng `Type` cụ thể** do API generic trả về, không chỉ dựa vào tên chuỗi.

## 2. Ba lớp khái niệm phải tách biệt

### 2.1. `Type` là mô hình metadata chung

`java.lang.reflect.Type` là interface chung cho các cách mô tả kiểu qua Reflection:

- `Class<?>`: lớp/interface, primitive, hoặc kiểu mảng runtime cụ thể.
- `ParameterizedType`: kiểu có đối số generic như `List<String>`.
- `TypeVariable<?>`: biến kiểu như `T` trong `class Box<T>`.
- `WildcardType`: ký tự đại diện như `? extends Number`.
- `GenericArrayType`: mảng với thành phần là kiểu generic không biểu diễn đơn thuần bằng `Class`.

Không nên ép mọi `Type` về `Class<?>`, vì `GenericArrayType` **không implement `Class`**.

### 2.2. `Class` mô tả mảng runtime

`String[].class` là một đối tượng `Class`, đồng thời cũng implement `Type`. Nó có các API:

```java
String[].class.isArray();                       // true
String[].class.getComponentType() == String.class; // true
int[].class.getComponentType() == int.class;     // true
```

`getComponentType()` trả về `Class<?>` cho loại phần tử của một **array class**; không có trường hợp trả về `ParameterizedType` hay `TypeVariable`.

### 2.3. `GenericArrayType` mô tả kiểu thành phần generic

`GenericArrayType.getGenericComponentType()` trả về **`Type`** thay vì `Class`; giá trị có thể là `TypeVariable`, `ParameterizedType`, hoặc `GenericArrayType` khi nhiều chiều.

```java
Type type = Box.class.getDeclaredField("genericValues")
        .getGenericType();

if (type instanceof GenericArrayType arrayType) {
    Type component = arrayType.getGenericComponentType();
    System.out.println(component.getTypeName()); // T
}
```

Đây là **mảnh mã đặt trong phương thức** có các import `java.lang.reflect.*` và khai báo `Box` phù hợp, không phải một chương trình độc lập. API trả về `Type` vì kiểu `T` không nhất thiết được xác định là một `Class` cụ thể ở điểm khai báo đó.

## 3. Generic Type khác runtime erasure: vì sao T[] trở thành Number[]?

Xét `class Sample<T extends Number>` với field `T[] genericValues`:

- `field.getGenericType()` trả metadata kiểu `T[]`, thường là `GenericArrayType`.
- `field.getType()` trả **`Class` sau erasure**, cụ thể `Number[].class`.
- `((GenericArrayType) field.getGenericType()).getGenericComponentType()` là `TypeVariable` mô tả `T`.
- Bound đầu tiên của `T` ở đây là `Number`, nên erasure của `T` là `Number`.

Tương tự, với `List<String>[]`, kiểu thành phần generic vẫn là `List<String>`, nhưng erasure của phần tử là `List`; do đó `Field.getType()` trả `List[].class`.

| Field | `Field.getType().getTypeName()` | `Field.getGenericType().getTypeName()` |
| --- | --- | --- |
| `T[]`, với `T extends Number` | `java.lang.Number[]` | `T[]` |
| `List<String>[]` | `java.util.List[]` | `java.util.List<java.lang.String>[]` |
| `List<?>[]` | `java.util.List[]` | `java.util.List<?>[]` |
| `String[]` | `java.lang.String[]` | `java.lang.String[]` |
| `int[]` | `int[]` | `int[]` |
| `T[][]`, với `T extends Number` | `java.lang.Number[][]` | `T[][]` |

**Erasure không biến `T[]` thành đối tượng `GenericArrayType` ở runtime.** Ngược lại, runtime array class vẫn phải là một `Class` cụ thể (ví dụ `Number[]`); `GenericArrayType` là cách API giữ thông tin khai báo generic có sẵn trong class-file metadata.

## 4. Ví dụ Java 17+ hoàn chỉnh: đọc đúng từng loại Type

Lưu chương trình dưới tên `GenericArrayTypeDemo.java`:

```java
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;

public class GenericArrayTypeDemo {
    public static class Sample<T extends Number> {
        public T[] genericValues;
        public List<String>[] parameterizedValues;
        public List<?>[] wildcardValues;
        public String[] concreteValues;
        public int[] primitiveValues;
        public T[][] nestedValues;

        public void accept(T[] values, String[] names, List<String>[] lists) {
        }

        public List<String>[] produce() {
            return parameterizedValues;
        }
    }

    static void check(String name) throws NoSuchFieldException {
        Field field = Sample.class.getField(name);
        Type generic = field.getGenericType();

        System.out.println(name
                + ": erased=" + field.getType().getTypeName()
                + ", generic=" + generic.getTypeName()
                + ", GenericArrayType=" + (generic instanceof GenericArrayType));

        if (generic instanceof GenericArrayType arrayType) {
            Type component = arrayType.getGenericComponentType();
            String kind =
                    component instanceof ParameterizedType ? "ParameterizedType" :
                    component instanceof TypeVariable<?> ? "TypeVariable" :
                    component instanceof GenericArrayType ? "GenericArrayType" :
                    "Class";
            System.out.println("  component=" + component.getTypeName()
                    + ", kind=" + kind);
        }
    }

    public static void main(String[] args) throws Exception {
        check("genericValues");
        check("parameterizedValues");
        check("wildcardValues");
        check("concreteValues");
        check("primitiveValues");
        check("nestedValues");

        Method method = Sample.class.getMethod(
                "accept", Number[].class, String[].class, List[].class);
        String[] erasedParams = Arrays.stream(method.getParameterTypes())
                .map(Class::getTypeName).toArray(String[]::new);
        String[] genericParams = Arrays.stream(method.getGenericParameterTypes())
                .map(Type::getTypeName).toArray(String[]::new);

        System.out.println("Method erased: " + Arrays.toString(erasedParams));
        System.out.println("Method generic: " + Arrays.toString(genericParams));

        Type returnType = Sample.class.getMethod("produce")
                .getGenericReturnType();
        System.out.println("Return generic kind: "
                + (returnType instanceof GenericArrayType));
        System.out.println("Dynamic array: "
                + Array.newInstance(Number.class, 2)
                        .getClass().getTypeName());
        System.out.println("Concrete String[] instanceof Class: "
                + (Sample.class.getField("concreteValues")
                        .getGenericType() instanceof Class<?>));
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac GenericArrayTypeDemo.java
java GenericArrayTypeDemo
```

Stdout:

```text
genericValues: erased=java.lang.Number[], generic=T[], GenericArrayType=true
  component=T, kind=TypeVariable
parameterizedValues: erased=java.util.List[], generic=java.util.List<java.lang.String>[], GenericArrayType=true
  component=java.util.List<java.lang.String>, kind=ParameterizedType
wildcardValues: erased=java.util.List[], generic=java.util.List<?>[], GenericArrayType=true
  component=java.util.List<?>, kind=ParameterizedType
concreteValues: erased=java.lang.String[], generic=java.lang.String[], GenericArrayType=false
primitiveValues: erased=int[], generic=int[], GenericArrayType=false
nestedValues: erased=java.lang.Number[][], generic=T[][], GenericArrayType=true
  component=T[], kind=GenericArrayType
Method erased: [java.lang.Number[], java.lang.String[], java.util.List[]]
Method generic: [T[], java.lang.String[], java.util.List<java.lang.String>[]]
Return generic kind: true
Dynamic array: java.lang.Number[]
Concrete String[] instanceof Class: true
```

Đầu ra được xây dựng không dựa vào thứ tự ngẫu nhiên của Reflection: các field được tra riêng theo tên, các tham số method được lấy theo thứ tự khai báo. Mọi loại `Type` đều được phân loại bằng `instanceof` thay vì kiểm tra tên lớp triển khai nội bộ JDK.

**Ý nghĩa quan trọng của kết quả:**

1. `genericValues` có metadata `T[]` → `GenericArrayType`; component `T` → `TypeVariable`, trong khi erased array class là `Number[]`.
2. `parameterizedValues` và `wildcardValues` có component `List<String>` và `List<?>` → `ParameterizedType`. Metadata này giữ đối số generic mà `getType()` đã loại bỏ.
3. `concreteValues` và `primitiveValues` vẫn có generic type là chính `Class` của mảng.
4. `nestedValues` có component ngoài là `T[]`, do đó có thêm một cấp `GenericArrayType`.
5. `Method.getParameterTypes()` trả các `Class` theo erasure, còn `getGenericParameterTypes()` trả danh sách `Type` giàu thông tin hơn.
6. `Method.getGenericReturnType()` giữ kiểu trả về `List<String>[]` là `GenericArrayType`.
7. `Array.newInstance(Number.class, 2)` tạo mảng `Number[]` thực sự. Không có constructor/allocator chung nhận `GenericArrayType` để tạo một `T[]` còn chưa biết `T` tại runtime.

## 5. Phân tích mảng nhiều chiều bằng getGenericComponentType()

Với khai báo `T[][]`, gọi `getGenericComponentType()` một lần lấy ra `T[]`, **chưa phải `T`**. Phải phân tích từng cấp:

```java
// Đoạn này đặt trong phương thức; giả định đã có Type type từ field T[][].
while (type instanceof GenericArrayType arrayType) {
    System.out.println("Array dimension: " + type.getTypeName());
    type = arrayType.getGenericComponentType();
}
System.out.println("Innermost component: " + type.getTypeName());
```

Kết quả đối với `T[][]`:

```text
Array dimension: T[][]
Array dimension: T[]
Innermost component: T
```

Với mảng **cụ thể** `String[][]`, toàn bộ giá trị ban đầu là `Class`; cần một nhánh riêng:

```java
// Trong phương thức có biến Type type:
if (type instanceof Class<?> clazz && clazz.isArray()) {
    System.out.println(clazz.getComponentType().getTypeName());
}
```

Với `String[][]`, `getComponentType()` trả `String[].class`; tiếp tục gọi `getComponentType()` mới lấy `String.class`. Một hàm phân tích `Type` tổng quát cần xử lý **cả hai đường**: `GenericArrayType` và `Class.isArray()`.

## 6. Tại sao getMethod nhận Class chứ không nhận GenericArrayType?

Các API định vị phương thức như:

```java
clazz.getMethod("accept",
        Number[].class, String[].class, List[].class);
```

nhận `Class<?>... parameterTypes`, nghĩa là dùng chữ ký **sau erasure**, không nhận `Type...` hay `GenericArrayType`.

Trong ví dụ, phương thức khai báo:

```java
public void accept(T[] values, String[] names, List<String>[] lists)
```

được tìm bằng `Number[].class`, `String[].class`, `List[].class` vì `T extends Number`. Không thể truyền `T[].class` (Java không có class literal cho `T[]` ở vị trí đó) và cũng không thể ép `GenericArrayType` thành `Class`.

Sau khi tìm được `Method`, có thể đọc:

- `method.getParameterTypes()`: các `Class<?>[]` runtime sau erasure.
- `method.getGenericParameterTypes()`: các `Type[]` biểu diễn khai báo generic.
- `method.getReturnType()`: `Class<?>` của kiểu trả về sau erasure.
- `method.getGenericReturnType()`: `Type` có thể là `GenericArrayType`.

Phân biệt **định vị method để gọi** với **phân tích generic để tạo schema, serialize, validate hoặc tài liệu hóa**. Một method có parameter `List<String>[]` vẫn phải được tìm bằng `List[].class` và JVM chỉ kiểm tra runtime array component là `List`, không thể tự xác nhận mọi phần tử List chứa chuỗi.

## 7. GenericArrayType không phải đối tượng mảng và không tạo mảng giúp bạn

Hãy phân biệt:

```java
Type metadata = Sample.class.getField("genericValues").getGenericType();
// metadata đại diện cho khai báo T[], không phải một mảng chứa T.
```

với:

```java
Object actualArray = java.lang.reflect.Array.newInstance(Number.class, 3);
// actualArray thực sự là mảng Number[3].
```

`Array.newInstance(Class<?> componentType, int length)` yêu cầu **Class của kiểu thành phần thực tế**. Nếu chỉ nắm một `GenericArrayType` chứa `T`, bạn phải biết cách ánh xạ `T` sang kiểu runtime thực sự (hoặc lựa chọn erasure/bound phù hợp mục đích). **Không thể chuyển metadata `T[]` thành `T[].class` chỉ bằng cast**.

Đặc biệt, `new T[3]` không hợp lệ trong phần thân lớp generic Java thông thường vì `T` không reifiable. Tương tự, `new List<String>[3]` bị cấm bởi quy tắc generic array creation. Nhưng **`List<?>[]` là trường hợp tinh tế**: `List<?>` là kiểu reifiable theo JLS, do đó `new List<?>[3]` hợp lệ. Dù vậy, **metadata khai báo field `List<?>[]` vẫn có thể là `GenericArrayType`** vì component có dạng `ParameterizedType`. Quy tắc tạo mảng trong ngôn ngữ và việc chọn interface `Type` trong Reflection là **hai câu hỏi khác nhau**.

Thay vì tạo `List<String>[]` bằng cast không kiểm soát, thường ưu tiên `List<List<String>>` khi cần tập hợp động có generic type safety. Khi buộc làm việc với thư viện nhận mảng generic, hãy phân biệt cảnh báo unchecked, phạm vi mảng, và những thao tác ghi phần tử có thể gây heap pollution.

## 8. Không nhầm với ParameterizedType hoặc mảng primitive-wrapper

Một số lỗi khái niệm thường gặp:

| Trường hợp | Loại Type được trả về trong generic reflection |
| --- | --- |
| Field `List<String> items` | `ParameterizedType` (không phải mảng) |
| Field `List<String>[] items` | `GenericArrayType`, component `ParameterizedType` |
| Field `T value` | `TypeVariable` |
| Field `T[] values` | `GenericArrayType`, component `TypeVariable` |
| Field `String[] names` | `Class` của `String[]` |
| Field `int[] numbers` | `Class` của `int[]` |
| Field `Integer[] numbers` | `Class` của `Integer[]` |

`int[].class` và `Integer[].class` đều là `Class` của mảng cụ thể, mặc dù **hai loại mảng đó không thể thay thế nhau**: Java boxing/unboxing ở cấp phần tử không tự động chuyển cả mảng. Sự khác nhau giữa **`Class` và `GenericArrayType`** là ở mô hình kiểu generic được phản chiếu, không phải ở primitive hay wrapper.

Đồng thời, `ParameterizedType` biểu diễn riêng `List<String>`; nó trở thành **component** của `GenericArrayType` khi toàn khai báo là `List<String>[]`.

## 9. Lỗi thường gặp khi xử lý metadata

| Lỗi hoặc hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `ClassCastException` khi ép `field.getGenericType()` về `Class<?>` | Kiểu khai báo là `T[]` hoặc `List<String>[]` | Kiểm tra `instanceof GenericArrayType` trước |
| Gọi `getComponentType()` trên kết quả chỉ được khai báo `Type` | `Type` không có method này | Nếu là `Class` array thì dùng `Class.getComponentType()`; nếu `GenericArrayType` dùng `getGenericComponentType()` |
| Chỉ đọc `getType()` nên mất `List<String>` | `getType()` trả kiểu đã erasure | Dùng `getGenericType()` để giữ khai báo generic |
| Cố tra `accept(T[],...)` bằng một `GenericArrayType` | `getMethod` chỉ nhận `Class<?>...` | Tra bằng `Number[].class`, `String[].class`, `List[].class` đúng erasure |
| Cố tạo `new T[3]` | Biến kiểu không reifiable | Dùng collection hoặc có `Class` của component runtime để tạo qua `Array.newInstance` |
| Tin rằng `GenericArrayType` cung cấp đối tượng mảng | Đây là metadata `Type`, không phải array instance | Phân biệt `Type`, `Class` và object |
| Coi `T[][]` chỉ có một cấp `GenericArrayType` | Thành phần ngoài là `T[]` | Duyệt đệ quy tới `TypeVariable` cuối |
| So sánh bằng tên `getClass().getName()` của implementation `Type` | Implementation nội bộ không phải hợp đồng công khai | Kiểm tra các interface `GenericArrayType`, `ParameterizedType`, `TypeVariable` |
| Coi `List<?>[]` có thể tạo được nên Reflection trả `Class` | Tính reifiable và dạng metadata generic không đồng nhất | Phân biệt luật tạo mảng và dạng `Type` do Reflection trả |
| Giả định `Integer[]` là `GenericArrayType` vì `Integer` là wrapper | `Integer` là lớp cụ thể; mảng có runtime class xác định | Kết quả là `Class` của `Integer[]` |

## 10. Tự kiểm tra

1. **Một field `List<Long>[] values` trả `getGenericType()` là gì?** Thông thường là `GenericArrayType`; `getGenericComponentType()` là `ParameterizedType` của `List<Long>`, còn `getType()` là `List[].class`.
2. **Một field `long[] values` trả `getGenericType()` là gì?** `Class` của `long[]`.
3. **Với `class Box<T extends Number>` và field `T[] values`, `getType()` là gì?** `Number[].class`; `getGenericType()` phản ánh `T[]` dưới dạng `GenericArrayType`.
4. **Với `T[][]`, gọi `getGenericComponentType()` một lần đã nhận `T` chưa?** Chưa; lần đầu nhận `T[]`, lần hai mới đến `T`.
5. **Có thể lấy `GenericArrayType` rồi dùng `Array.newInstance(type, 5)` trực tiếp không?** Không; overload thông dụng của `Array.newInstance` cần `Class<?> componentType`, không nhận `Type`.
6. **Nếu method nhận `List<String>[]`, tra bằng kiểu nào?** `List[].class` ở vị trí tham số tương ứng, rồi dùng `getGenericParameterTypes()` để xem đối số `String`.
7. **`List<?>[]` có thể khai báo và `new List<?>[2]` không?** Có; đây là trường hợp reifiable với unbounded wildcard, nhưng kết quả đọc generic metadata có thể vẫn là `GenericArrayType`.

**Ghi nhớ:** `Class` biểu diễn **mảng cụ thể tại runtime**; `GenericArrayType` mô tả **kiểu mảng trong khai báo generic**, với thành phần có thể là `TypeVariable` hoặc `ParameterizedType`. Chọn `Field.getType()` khi cần runtime class và `Field.getGenericType()` khi cần mô hình generic; luôn kiểm tra `instanceof` trước khi phân tích.

Tham khảo: [GenericArrayType — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/GenericArrayType.html), [Type — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Type.html), [Field — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html), [JLS §10.1 Array Types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-10.html#jls-10.1).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-model-008">getGenericSuperclass và getGenericInterfaces</a>

<details>
<summary>Xem chi tiết</summary>

# getGenericSuperclass và getGenericInterfaces trong Java Reflection

Khi Java Reflection phân tích một class, có hai câu hỏi khác nhau: **lớp này kế thừa trực tiếp lớp nào?** và **nó khai báo triển khai/mở rộng trực tiếp những interface nào?**. Nếu quan hệ có tham số kiểu, ví dụ **extends Repository<Order>** hoặc **implements Handler<List<String>>**, những API chỉ trả Class thường làm mất thông tin tham số generic. Hai phương thức **Class.getGenericSuperclass()** và **Class.getGenericInterfaces()** giúp đọc những khai báo generic đó từ class file.

Bài này tập trung vào cách dùng hai API, phân biệt Class với Type/ParameterizedType, giới hạn “trực tiếp” trong cây kế thừa, cách xử lý type variable qua nhiều cấp và những trường hợp đặc biệt như interface, raw type, anonymous subclass hay array class. Ví dụ sử dụng Java 17+ và không cần thư viện bên ngoài.

## 1. Phân biệt API thông thường và API generic

Một class có thể khai báo:

~~~java
class Base<T> {}

interface Named<T> {}

class User extends Base<String> implements Named<Integer> {}
~~~

Từ **User.class**, các API liên quan trả:

| API | Kiểu trả về | Ý nghĩa |
| --- | --- | --- |
| **getSuperclass()** | Class<?> hoặc null | Class superclass trực tiếp, sau erasure; ví dụ Base.class |
| **getGenericSuperclass()** | Type hoặc null | Khai báo superclass trực tiếp, có thể là Base<String> |
| **getInterfaces()** | Class<?>[] | Các interface trực tiếp, kiểu runtime; ví dụ Named.class |
| **getGenericInterfaces()** | Type[] | Các interface trực tiếp, có thể là Named<Integer> |
| **getTypeParameters()** | TypeVariable<?>[] | Các type variable mà chính class/interface khai báo, như T trong Base<T> |
| **getAnnotatedSuperclass()** | AnnotatedType hoặc null | Type use của superclass kèm annotation TYPE_USE nếu có |
| **getAnnotatedInterfaces()** | AnnotatedType[] | Type use của các interface trực tiếp kèm annotation TYPE_USE nếu có |

**Quan trọng:** getGenericSuperclass/getGenericInterfaces không trả về “danh sách tất cả tổ tiên”. Chúng phản ánh **cạnh trực tiếp** trong khai báo class/interface đang xét. Để tìm mọi interface gián tiếp hoặc thay thế type variable trên nhiều lớp, phải tự duyệt tiếp.

Đối với **extends Base<String>**, getSuperclass() cung cấp **Class của Base**, còn getGenericSuperclass() thường cung cấp **ParameterizedType** gồm raw type Base và actual type argument String.

## 2. Cấu trúc Type và ParameterizedType

Đối tượng trả về từ API generic có kiểu tổng quát **java.lang.reflect.Type**. Type là interface chung, không phải mọi Type đều là Class.

| Dạng Type | Ví dụ | Cách nhận biết / truy vấn |
| --- | --- | --- |
| **Class<?>** | Object, String, raw Base, một interface không parameterized | Kiểm tra **instanceof Class<?>** |
| **ParameterizedType** | Base<String>, Handler<List<Integer>> | **getRawType()**, **getActualTypeArguments()**, **getOwnerType()** |
| **TypeVariable<?>** | T, E, X trong khai báo generic | **getName()**, **getBounds()**, **getGenericDeclaration()** |
| **WildcardType** | ? extends Number, ? super String | **getUpperBounds()**, **getLowerBounds()** |
| **GenericArrayType** | T[] nếu thành phần là type variable | **getGenericComponentType()** |

Một phản xạ an toàn luôn **phân nhánh theo loại Type** trước khi ép kiểu:

~~~java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

Type type = User.class.getGenericSuperclass();
if (type instanceof ParameterizedType parameterized) {
    Type raw = parameterized.getRawType();
    Type[] arguments = parameterized.getActualTypeArguments();
    System.out.println(raw);            // class Base, tên phụ thuộc package/nesting
    System.out.println(arguments[0]);   // class java.lang.String
}
~~~

Đây là đoạn minh họa cần có các lớp Base/User cùng compilation unit ở mục 1; hai dòng bình luận không phải stdout chuẩn hóa của một chương trình độc lập.

### Ba thành phần của ParameterizedType

- **getRawType()**: class/interface gốc, ví dụ Base trong Base<String>. Kết quả được khai báo là Type; khi làm việc với Reflection thông thường của JVM, raw type chủ yếu là Class<?>.
- **getActualTypeArguments()**: các đối số kiểu ở cạnh kế thừa đang khai báo, ví dụ String hoặc một Type phức tạp như List<T>. Nếu khai báo nhiều đối số kiểu, thứ tự đúng theo khai báo.
- **getOwnerType()**: kiểu bao ngoài khi parameterized type là kiểu thành viên, ví dụ Outer<String>.Inner<Integer>; với top-level type thì thường null.

**Không dùng cast trực tiếp**:

~~~java
// Không an toàn nếu superclass là Object, raw type hoặc không có superclass:
ParameterizedType p = (ParameterizedType) clazz.getGenericSuperclass();
~~~

Đoạn này cố tình minh họa antipattern. getGenericSuperclass() có thể trả **Class<?>** hoặc **null**; cast trên sẽ gây ClassCastException hoặc dẫn đến NullPointerException về sau.

### Type.getTypeName()

Mỗi Type có **getTypeName()** để lấy chuỗi biểu diễn kiểu, thuận tiện cho log và tài liệu. Tuy nhiên, chuỗi này không phải giao diện chuẩn để parse ngược mọi cấu trúc generic; khi viết tool, xử lý **các đối tượng Type theo loại** vẫn chính xác hơn. Tên đầy đủ có thể bao gồm package, dấu $ của nested class và những biểu thức generic phức tạp.

## 3. getGenericSuperclass(): chỉ lớp cha trực tiếp

Hãy phân biệt các dạng khai báo sau:

~~~java
class Parent<T> {}
class Concrete extends Parent<String> {}
class Plain {}
class RawChild extends Parent {} // Kế thừa raw type, thông tin String không được khai báo
~~~

| Đối tượng Class | getGenericSuperclass() | Giải thích |
| --- | --- | --- |
| Concrete.class | ParameterizedType mô tả Parent<String> | Khai báo superclass có actual type argument |
| Parent.class | Class mô tả Object | Parent không ghi extends và cha mặc định là Object |
| Plain.class | Class mô tả Object | Superclass mặc định |
| RawChild.class | Class mô tả Parent | Raw extends không cung cấp actual type argument |
| Object.class | null | Object không có superclass |
| List.class (interface) | null | Interface không có class superclass |
| int.class, void.class | null | Primitive và void không có superclass |
| String[].class | Class mô tả Object | Array class có superclass Object |

Mỗi lần gọi chỉ đọc **một cấp class superclass**. Ví dụ:

~~~text
Leaf extends Middle<Integer>
Middle<X> extends Parent<List<X>>
Parent<T> extends Object
~~~

Khi gọi **Leaf.class.getGenericSuperclass()**, kết quả là **Middle<Integer>**, không phải Parent<List<Integer>>. Khi gọi **Middle.class.getGenericSuperclass()**, kết quả là **Parent<List<X>>**. Muốn có Parent<List<Integer>> theo ngữ cảnh Leaf, phải ghép hai cạnh và thay **X → Integer** trong chữ ký của Middle.

Đây chính là giới hạn quan trọng nhất của các thư viện tự phân tích hierarchy: chúng có thể đọc đúng Type của mỗi cạnh, nhưng vẫn suy luận sai nếu bỏ qua ánh xạ type variable.

### Class không được khai báo generic vẫn có generic superclass

Concrete có thể không có type variable nào của riêng mình:

~~~java
Concrete.class.getTypeParameters().length == 0
~~~

nhưng vẫn có **Parent<String>** ở getGenericSuperclass(). Ngược lại, Parent.class.getTypeParameters() có T, nhưng Parent.class.getGenericSuperclass() chỉ là Object. Hai API trả lời hai câu hỏi khác nhau: **class này khai báo type variable nào** và **class này dùng kiểu generic gì ở cạnh extends**.

## 4. getGenericInterfaces(): danh sách interface trực tiếp

Với:

~~~java
interface Keyed<T> {}
interface Logged {}
class Service implements Keyed<String>, Logged {}
~~~

**Service.class.getGenericInterfaces()** trả một mảng Type có hai phần tử theo thứ tự khai báo:

1. ParameterizedType biểu diễn Keyed<String>.
2. Class<?> biểu diễn Logged vì interface này không được parameterized tại vị trí đó.

Nếu class không trực tiếp khai báo implements, kết quả thường là **mảng rỗng**, kể cả khi nó **được thừa hưởng** một interface từ superclass.

~~~java
class ParentService implements Keyed<String> {}
class ChildService extends ParentService {}
~~~

**ChildService.class.getGenericInterfaces()** trả mảng rỗng; **ParentService.class.getGenericInterfaces()** chứa Keyed<String>. Điều đó **không có nghĩa** ChildService không phải là Keyed: **Keyed.class.isAssignableFrom(ChildService.class)** vẫn có thể trả true do quan hệ inheritance.

Với một **interface** đang extends các interface khác, getGenericInterfaces() trả các **superinterfaces trực tiếp**, theo thứ tự được khai báo:

~~~java
interface Tag<T> {}
interface Compound<T> extends Tag<java.util.List<T>> {}
~~~

**Compound.class.getGenericInterfaces()** chứa Type đại diện **Tag<List<T>>**, trong đó T là TypeVariable được khai báo bởi Compound. **Compound.class.getGenericSuperclass()** trả **null**, vì Compound là interface và không có class superclass.

### Thứ tự và array type

Java SE 17 chỉ rõ thứ tự các interface từ getInterfaces/getGenericInterfaces theo thứ tự trong **implements** hoặc **extends** clause. Vì vậy khi interface trực tiếp được khai báo là A, B thì mảng tương ứng là A, B.

Với **array class**, getGenericInterfaces() trả hai interface của array runtime: **Cloneable**, **java.io.Serializable**, theo thứ tự đó. Với primitive/void hoặc class/interface không khai báo trực tiếp interface nào thì mảng rỗng. Mảng có superclass Object theo getGenericSuperclass() nhưng không chứa generic element argument theo cơ chế parameterized class.

## 5. Ví dụ chạy hoàn chỉnh trên Java 17+

Chương trình dưới đây cho thấy sự khác biệt giữa generic superclass, generic interfaces, superclass sau erasure, type arguments, raw inheritance và interface được thừa hưởng gián tiếp. Hàm **display(Type)** chủ động in tên ngắn để stdout ổn định, tránh phụ thuộc vào tên đầy đủ của nested class.

Lưu nguyên văn vào **HierarchyReflectionDemo.java**:

~~~java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HierarchyReflectionDemo {
    interface Named<T> {}
    interface Audited {}
    interface Chain<T> extends Named<List<T>> {}

    static class Base<T> {}
    static class Direct extends Base<String> implements Named<Integer>, Audited {}
    static class Middle<X> extends Base<List<X>> implements Named<X> {}
    static class Leaf extends Middle<Integer> {}
    static class Raw extends Base {} // Raw superclass: argument information absent
    static class Plain {}

    private static String display(Type t) {
        if (t == null) return "null";
        if (t instanceof ParameterizedType p) {
            String args = Arrays.stream(p.getActualTypeArguments())
                    .map(HierarchyReflectionDemo::display)
                    .collect(Collectors.joining(", "));
            return display(p.getRawType()) + "<" + args + ">";
        }
        if (t instanceof Class<?> c) return c.getSimpleName();
        if (t instanceof TypeVariable<?> v) return v.getName();
        return t.getTypeName();
    }

    private static String interfacesOf(Class<?> type) {
        return Arrays.stream(type.getGenericInterfaces())
                .map(HierarchyReflectionDemo::display)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    public static void main(String[] args) {
        System.out.println("Direct superclass=" + display(Direct.class.getGenericSuperclass()));
        System.out.println("Direct interfaces=" + interfacesOf(Direct.class));
        System.out.println("Direct raw superclass=" + Direct.class.getSuperclass().getSimpleName());

        Type supertype = Direct.class.getGenericSuperclass();
        if (supertype instanceof ParameterizedType p) {
            System.out.println("Direct super raw=" + display(p.getRawType()));
            System.out.println("Direct super arg=" + display(p.getActualTypeArguments()[0]));
        }

        System.out.println("Leaf superclass=" + display(Leaf.class.getGenericSuperclass()));
        System.out.println("Leaf interfaces=" + interfacesOf(Leaf.class));
        System.out.println("Middle superclass=" + display(Middle.class.getGenericSuperclass()));
        System.out.println("Middle interfaces=" + interfacesOf(Middle.class));
        System.out.println("Chain superclass=" + display(Chain.class.getGenericSuperclass()));
        System.out.println("Chain interfaces=" + interfacesOf(Chain.class));
        System.out.println("Raw superclass=" + display(Raw.class.getGenericSuperclass()));
        System.out.println("Plain superclass=" + display(Plain.class.getGenericSuperclass()));
        System.out.println("Object superclass=" + display(Object.class.getGenericSuperclass()));
        System.out.println("List superclass=" + display(List.class.getGenericSuperclass()));
        System.out.println("String[] superclass=" + display(String[].class.getGenericSuperclass()));
    }
}
~~~

Từ thư mục chứa file, biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 HierarchyReflectionDemo.java
java HierarchyReflectionDemo
~~~

Stdout đã được kiểm tra bằng JDK 21 với source tương thích Java 17:

~~~text
Direct superclass=Base<String>
Direct interfaces=[Named<Integer>, Audited]
Direct raw superclass=Base
Direct super raw=Base
Direct super arg=String
Leaf superclass=Middle<Integer>
Leaf interfaces=[]
Middle superclass=Base<List<X>>
Middle interfaces=[Named<X>]
Chain superclass=null
Chain interfaces=[Named<List<T>>]
Raw superclass=Base
Plain superclass=Object
Object superclass=null
List superclass=null
String[] superclass=Object
~~~

### Cách đọc kết quả

- **Direct superclass=Base<String>**: Type chứa đối số String đúng như cạnh extends.
- **Direct raw superclass=Base**: Class.getSuperclass() không giữ String.
- **Direct interfaces=[Named<Integer>, Audited]**: một interface parameterized và một interface không parameterized, theo thứ tự implements.
- **Leaf interfaces=[]**: Leaf không trực tiếp khai báo implements, dù vẫn nhận hợp đồng Named qua Middle.
- **Middle superclass=Base<List<X>>**: Type còn X vì đang đọc cạnh khai báo bởi Middle, chưa thay X bằng Integer của Leaf.
- **Chain superclass=null**: interface không có class superclass. Nhưng **Chain interfaces=[Named<List<T>>]** thể hiện quan hệ extends với interface.
- **Raw superclass=Base**: kế thừa raw type; không có String hay type argument nào để lấy từ cạnh đó.
- **Plain superclass=Object** trong khi **Object superclass=null**.
- **String[] superclass=Object**: array runtime là một class có superclass Object; nó còn trực tiếp thực thi Cloneable và Serializable.

Hàm display() là tiện ích minh họa. Trong ứng dụng thực tế, Type.getTypeName() có thể trả tên đầy đủ như **java.util.List<java.lang.String>** hoặc tên nested class có dấu $, nên không nên sao chép output đã rút gọn để so sánh chuỗi tên runtime.

## 6. Duyệt nhiều cấp và thay thế TypeVariable

Hai API được bàn trong bài cung cấp **các cạnh trực tiếp**. Để truy ra kiểu của một interface hoặc superclass trong ngữ cảnh một class con cuối cùng, cần duyệt **toàn bộ graph kế thừa** và mang theo ánh xạ kiểu generic.

Xét lại chuỗi:

~~~java
class Base<T> {}
class Middle<X> extends Base<List<X>> {}
class Leaf extends Middle<Integer> {}
~~~

Từ Leaf:

1. Đọc **Leaf.class.getGenericSuperclass()** → Middle<Integer>.
2. Trong ParameterizedType này, raw type là Middle.class và actual argument là Integer.class. Tạo ánh xạ **X → Integer** dựa trên Middle.class.getTypeParameters().
3. Đọc **Middle.class.getGenericSuperclass()** → Base<List<X>>.
4. Thay TypeVariable X bằng Integer theo ánh xạ từ bước 2, được **Base<List<Integer>>** trong ngữ cảnh Leaf.
5. Nếu tiếp tục sang Base.class, duy trì thêm ánh xạ **T → List<Integer>** nếu cần hiểu kiểu của các field/method do Base khai báo.

Đây là quá trình **type-variable substitution**, không phải kết quả tự động của một lệnh getGenericSuperclass() duy nhất. Tương tự khi đi từ class đến interface gián tiếp phải duyệt cả superclasses **và** getGenericInterfaces() của mỗi lớp/interface.

### Pseudocode thuật toán phân giải generic hierarchy

~~~text
resolve(targetClass, soughtRawType):
  queue = [(targetClass, emptyMapping)]
  while queue not empty:
    (currentType, mapping) = take(queue)
    raw = rawClassOf(currentType)
    localMapping = bind typeParameters(raw) to actualTypeArguments(currentType)
                   after replacing variables via mapping
    if raw == soughtRawType:
       return localMapping or resolved parameterized view
    enqueue raw.getGenericSuperclass() if non-null
    enqueue each raw.getGenericInterfaces()
    propagate mappings through every edge
  return not found
~~~

Đây là **pseudocode khái niệm**, không phải Java chạy độc lập. Một implementation đầy đủ phải xử lý nhiều dạng Type, chu kỳ do interface graph, owner type của nested class, type variable có bound, wildcard, generic array, raw type và trường hợp nhiều đường đến cùng một interface. Nên lưu một tập các node/ánh xạ đã thăm và xác định rõ hành vi khi thông tin type argument bị raw inheritance làm mất.

Để xác định **một class có implement interface bất kể generic argument**, có thể dùng **Interface.class.isAssignableFrom(SomeClass.class)**. Nhưng phương thức này chỉ trả boolean dựa trên quan hệ raw class, **không** trả List<Integer> hay thay thế các TypeVariable.

### Khi nào không thể suy ra kiểu cụ thể?

Với:

~~~java
class Container<T> {}
Container<String> first = new Container<>();
Container<Integer> second = new Container<>();
~~~

Hai đối tượng đều có **getClass() == Container.class**. Type arguments của đối tượng được tạo thông thường không tồn tại như hai Class runtime khác nhau. **Container.class.getGenericSuperclass()** chỉ trả Object, không thể tự khôi phục String/Integer từ first/second.

Một cách phổ biến để giữ kiểu trong metadata là tạo **anonymous subclass**:

~~~java
Container<String> captured = new Container<String>() {};
Type capturedParent = captured.getClass().getGenericSuperclass();
// capturedParent có thể là ParameterizedType biểu diễn Container<String>
~~~

Đây là fragment dùng Container từ ví dụ; phải import java.lang.reflect.Type. Anonymous subclass này có superclass khai báo trực tiếp **Container<String>**, nên đọc được String ở **Signature của anonymous class**. Nó **không** chứng minh rằng mọi đối tượng Container<String> thông thường đều lưu String. Khi tạo anonymous subclass trong ngữ cảnh generic chưa ràng buộc, metadata có thể chứa TypeVariable chứ không nhất thiết là concrete Class.

## 7. Raw type, wildcard, nested type và annotation

### Raw inheritance

Nếu **class Raw extends Base** thay vì extends Base<String>, getGenericSuperclass() thường là **Class** Base, không phải ParameterizedType. Bạn không thể lấy actual type argument đã không tồn tại trong khai báo. Đừng tự gán Object rồi nói rằng đó là actual type argument trong Signature; **erasure thành Object** là quy tắc về kiểu runtime, khác với việc có **đối số generic được khai báo**.

### Wildcard và generic argument lồng nhau

Một class có thể khai báo superclass hoặc interface với biểu thức kiểu lồng nhau:

~~~java
interface Source<T> {}
class WildcardSource implements Source<List<? extends Number>> {}
~~~

Khi truy xuất interface trực tiếp:

~~~java
Type t = WildcardSource.class.getGenericInterfaces()[0];
if (t instanceof ParameterizedType p) {
    Type nested = p.getActualTypeArguments()[0]; // List<? extends Number>
}
~~~

Đây là ví dụ mảnh code: các class Source/WildcardSource phải được khai báo như trên. nested thường là ParameterizedType của List, và type argument trong List là **WildcardType** với upper bound Number. Không ép luôn nested thành Class, vì nested cũng có thể là TypeVariable, WildcardType ở vị trí khác hoặc dạng generic phức tạp.

### Nested/inner class và owner type

Khi type nằm trong một generic outer class, cấu trúc có thể là Outer<String>.Inner<Integer>. Nếu một subclass extends kiểu đó, **ParameterizedType.getOwnerType()** có thể chứa Outer<String>, còn getActualTypeArguments() của inner phần lớn mô tả type parameters riêng của Inner. Không bỏ qua owner type khi cần xác định đủ thông tin generic.

### Annotation trên vị trí extends/implements

Nếu có **@Target(ElementType.TYPE_USE)** cùng **@Retention(RetentionPolicy.RUNTIME)**, annotation trên kiểu được sử dụng tại khai báo extends/implements có thể được xem qua:

- **getAnnotatedSuperclass()**: AnnotatedType của superclass trực tiếp.
- **getAnnotatedInterfaces()**: AnnotatedType[] của từng interface trực tiếp.
- **AnnotatedParameterizedType.getAnnotatedActualTypeArguments()** khi muốn đào sâu annotation trên các type arguments của một parameterized type.

Khác biệt giữa **getGenericSuperclass()** và **getAnnotatedSuperclass()** là API thứ hai giữ lớp thông tin annotation TYPE_USE. Không phải annotation nào trong source cũng có thể đọc ở runtime; hãy kiểm tra @Retention và nơi đặt annotation.

## 8. Ngoại lệ và độ tin cậy của metadata

Khi parse generic Signature trong file .class, Reflection có thể gặp dữ liệu bị thiếu hoặc không hợp lệ (ví dụ class file đã bị công cụ bytecode biến đổi sai, hoặc dependency dùng trong type argument không còn tại runtime). Tài liệu Java 17 của **getGenericSuperclass** và **getGenericInterfaces** liệt kê:

- **GenericSignatureFormatError**: chữ ký generic trong class file không phù hợp định dạng theo JVM specification.
- **TypeNotPresentException**: kiểu được tham chiếu trong generic superclass/superinterface không tồn tại hoặc không thể resolve khi đọc metadata.
- **MalformedParameterizedTypeException**: kiểu parameterized trong metadata không thể tạo hợp lệ.

Các trường hợp này khác với **ClassNotFoundException** xảy ra khi code trực tiếp nạp class bằng tên. Không cần bắt cả ba ở mọi ứng dụng đơn giản, nhưng một scanner xử lý bytecode từ nguồn không tin cậy nên ghi rõ lỗi, phân biệt metadata bị hỏng với việc một class không khai báo interface/superclass.

Với class biên dịch Java bình thường, thông tin generic nằm trong **Signature attribute** của class file. Phần lớn runtime thực hiện type erasure để thực thi; Signature cung cấp thông tin bổ sung cho Reflection. Nếu Signature bị xóa bởi công cụ build/obfuscator, class vẫn có thể load, nhưng generic metadata có thể không còn đủ.

### Có cần setAccessible(true) để đọc hierarchy?

**Không.** getGenericSuperclass() và getGenericInterfaces() trên Class đọc metadata kiểu/class, không phải lời gọi private method hoặc đọc private field. Quy tắc mở package JPMS bằng opens chủ yếu quan trọng khi thực hiện **deep reflective member access**, chứ không phải điều kiện bắt buộc để chỉ đọc khai báo generic hierarchy.

## 9. Quy trình thực hành cho thư viện phân tích type

Giả sử một framework muốn tìm type argument của Handler<T> mà user class triển khai qua nhiều lớp:

1. **Bắt đầu ở runtime class** được cung cấp, nhưng xác nhận class đó thực sự chứa generic signature đủ để phân tích; instance thông thường có thể đã mất argument do erasure.
2. **Duyệt class superclass trực tiếp** qua getGenericSuperclass() và **interface trực tiếp** qua getGenericInterfaces() tại từng node.
3. **Kiểm tra dạng Type** trước khi xử lý: Class, ParameterizedType, TypeVariable, WildcardType, GenericArrayType.
4. **Gắn type variable với actual type argument** khi đi qua một ParameterizedType và duy trì mapping xuyên các cấp. Không dừng ở raw type hoặc dựa vào tên T/E/X.
5. **Xác định target raw class** nhờ ParameterizedType.getRawType() hay Type là Class, thay vì so chuỗi getTypeName().
6. **Nếu encounter raw inheritance**, công khai rằng generic argument không đủ dữ liệu; tránh tự bịa ra kiểu cụ thể.
7. **Kiểm tra direct/inherited đúng ngữ cảnh**: getGenericInterfaces() trả trực tiếp, còn assignability có thể đúng do cấp tổ tiên.
8. **Có chiến lược với nested owner, wildcard và array** nếu framework cần hỗ trợ đầy đủ các biểu thức kiểu Java.
9. **Viết test cho nhiều tầng** như Leaf → Middle<Integer> → Base<List<X>>, một interface extends interface khác, raw type, anonymous subclass, primitive/array và Java module boundaries.

Đối với tác vụ chỉ cần biết “có phải một interface không?”, dùng **Class.isAssignableFrom** là đủ. Chỉ xây generic type resolver đầy đủ khi cần chính xác **kiểu tham số** của abstraction như Repository<Customer> hoặc Handler<List<Order>>.

## 10. Lỗi hay gặp

- **Ép mọi getGenericSuperclass() thành ParameterizedType:** superclass có thể là Class hoặc null.
- **Đọc getGenericInterfaces() rồi kết luận không implements interface:** class có thể kế thừa interface từ superclass; cần duyệt tiếp hoặc dùng isAssignableFrom để kiểm tra quan hệ raw.
- **Nhầm superclass của interface là Object:** getGenericSuperclass() của interface trả null, dù Java interface vẫn là reference type có quan hệ với Object theo những quy tắc ngôn ngữ khác.
- **Cho rằng kết quả của Leaf đã là Base<List<Integer>>:** Leaf.getGenericSuperclass() chỉ là Middle<Integer>; phải thay X qua cấp Middle.
- **Dùng getInterfaces() và mong nhận generic arguments:** kết quả là Class<?>[], không chứa các Type arguments của cạnh khai báo.
- **Nhầm generic declaration với runtime object:** Container<String> tạo trực tiếp không lưu String dưới dạng một Class riêng.
- **Coi getTypeName() là format ổn định để parse:** tốt hơn dùng Type và các subtype của nó.
- **Quên raw inheritance:** raw Base không tự mang argument String, và không nên coi erasure Object là metadata actual argument được khai báo.
- **Không xử lý nested owner/type-use annotations:** các framework cần metadata đầy đủ nên phân tích thêm getOwnerType/getAnnotatedInterfaces/getAnnotatedSuperclass khi hợp lý.
- **Nhầm Class.getTypeParameters với getGenericSuperclass:** API đầu đọc biến kiểu do chính class khai báo; API sau đọc kiểu superclass ở cạnh extends.

## 11. Tự kiểm tra

**Câu 1.** class Child extends Base<String> nhưng không implements interface nào. getGenericSuperclass() và getGenericInterfaces() trả gì?

**Đáp án:** ParameterizedType mô tả Base<String>; mảng Type[] rỗng.

**Câu 2.** interface Fancy<T> extends Named<List<T>>. Fancy.class.getGenericSuperclass() trả gì?

**Đáp án:** null vì Fancy là interface. Fancy.class.getGenericInterfaces() trả một ParameterizedType mô tả Named<List<T>>.

**Câu 3.** class Leaf extends Middle<Integer>, còn Middle<X> extends Base<List<X>>. Có thể lấy Base<List<Integer>> chỉ bằng Leaf.class.getGenericSuperclass() không?

**Đáp án:** Không. Nó trả Middle<Integer>; cần đọc tiếp Middle.getGenericSuperclass(), rồi thay X bằng Integer.

**Câu 4.** class Raw extends Base. Đọc getGenericSuperclass() thấy Base.class, vậy có thể khôi phục type argument String không?

**Đáp án:** Không. Raw khai báo raw inheritance, không chứa actual type argument String trong cạnh extends.

**Câu 5.** Một class kế thừa implementation của interface I qua superclass, nhưng getGenericInterfaces() trên class ấy trả mảng rỗng. Đây là lỗi Reflection không?

**Đáp án:** Không. API trả interface **trực tiếp**; interface được thừa hưởng gián tiếp phải duyệt tổ tiên. I.class.isAssignableFrom(ThatClass.class) có thể vẫn true.

**Câu 6.** getGenericSuperclass() của String[].class là gì? Còn Object.class và List.class?

**Đáp án:** String[] có superclass Object; Object và interface List trả null.

**Câu 7.** Tại sao new Box<String>() {} có thể cho biết String nhưng new Box<String>() thông thường không cho biết?

**Đáp án:** Dấu {} tạo anonymous subclass có khai báo extends Box<String> trong generic Signature của **anonymous class**. Instance Box thông thường chỉ mang runtime Class Box sau erasure.

**Ghi nhớ:** **getGenericSuperclass()** đọc **một superclass trực tiếp** dưới dạng Type; **getGenericInterfaces()** đọc **các interface trực tiếp** dưới dạng Type[]. Muốn giữ thông tin generic qua nhiều cấp, phải phân tích **ParameterizedType**, **TypeVariable** và thực hiện **thay thế type argument theo từng cạnh kế thừa**.

### Tài liệu tham khảo

- Java SE 17 — Class.getGenericSuperclass/getGenericInterfaces, getAnnotatedSuperclass/getAnnotatedInterfaces: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17 — Type: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Type.html
- Java SE 17 — ParameterizedType: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html
- Java SE 17 — TypeVariable: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/TypeVariable.html
- Java SE 17 — WildcardType: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/WildcardType.html
- Java SE 17 — GenericArrayType: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/GenericArrayType.html
- Java Language Specification 17 — Types, parameterized types và erasure: https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-model-009">getGenericType, getGenericReturnType và getGenericExceptionTypes</a>

<details>
<summary>Xem chi tiết</summary>

# getGenericType, getGenericReturnType và getGenericExceptionTypes trong Java Reflection

## 1. Vì sao API generic khác API kiểu thông thường?

Java hỗ trợ generic trong source như `List<String>`, `Map<String, List<T>>`, `T extends Number` và method `<E extends Exception> void execute() throws E`. Phần lớn thao tác runtime chịu **type erasure**: nhiều vị trí generic được biểu diễn bằng kiểu thô hoặc bound của type variable. Tuy nhiên, compiler có thể lưu **generic signature metadata** trong class file, và Reflection cho phép đọc lại phần khai báo đó.

Ví dụ một field `List<String> names`:

- `field.getType()` trả `Class<?>` đại diện cho `java.util.List`.
- `field.getGenericType()` trả `Type` phản ánh `java.util.List<java.lang.String>`, thông thường là một `ParameterizedType`.
- Hai kết quả cùng nói về **một field**, nhưng khác độ chi tiết: kiểu erasure cần cho truy cập runtime và khai báo generic hữu ích cho mapper, schema generator, serializer, dependency injection.

Đối với method, phân biệt tương tự:

- `method.getReturnType()` so với `method.getGenericReturnType()`.
- `method.getExceptionTypes()` so với `method.getGenericExceptionTypes()`.

**Mục tiêu học tập:** gọi đúng API theo loại thành viên; hiểu các dạng `Type`; biết khi nào metadata còn type variable chưa được thay thế; phân biệt kiểu khai báo từ generic signature với kiểm tra kiểu đối tượng thực tế.

## 2. Ba API chính thuộc về đối tượng Reflection nào?

| API | Chủ sở hữu | Nội dung trả về |
| --- | --- | --- |
| `Field.getGenericType()` | `Field` | `Type` mô tả kiểu khai báo của **field**, kể cả parameterized type và type variable. |
| `Method.getGenericReturnType()` | `Method` | `Type` mô tả **kiểu trả về được khai báo** của method; có thể là `Class`, `ParameterizedType`, `TypeVariable` hoặc `GenericArrayType`. |
| `Executable.getGenericExceptionTypes()` | `Method` hoặc `Constructor<?>` (qua superclass `Executable`) | `Type[]` mô tả các **kiểu exception trong mệnh đề `throws`** theo thứ tự; có thể gồm type variable như `E`. |

Bộ API không-generic đi kèm:

| Không-generic | Generic | Khác biệt thực tế |
| --- | --- | --- |
| `Field.getType(): Class<?>` | `Field.getGenericType(): Type` | `List<String>` trở thành `List.class` ở `getType()`. |
| `Method.getReturnType(): Class<?>` | `Method.getGenericReturnType(): Type` | `T extends Number` trở thành `Number.class` ở `getReturnType()`. |
| `Executable.getExceptionTypes(): Class<?>[]` | `Executable.getGenericExceptionTypes(): Type[]` | `throws E` có thể trở thành `Exception.class` ở bản erasure và `TypeVariable E` ở bản generic. |

Cần tránh gọi sai loại thành viên: **`Field` không có `getGenericReturnType()`**; **`Constructor` không có kiểu trả về**; `getGenericExceptionTypes()` được kế thừa qua `Executable` và sử dụng được trên cả method lẫn constructor.

Nếu không có mệnh đề `throws` thì `getGenericExceptionTypes()` trả **mảng rỗng**, không trả `null`. Với method khai báo `void`, `getGenericReturnType()` có thể trả `void.class`; đây vẫn là một `Class<?>` ở cấp Reflection, dù `void` không phải một giá trị trả về.

## 3. Dạng `Type` quyết định cách đọc kết quả

Cả ba getter generic trong bài trả `Type` hoặc `Type[]` vì một chữ ký có thể chứa `Class<?>` (ví dụ `String`), `ParameterizedType` (`List<String>`), `TypeVariable` (`T`, `E` trong `throws E`), `WildcardType` nằm trong argument (`List<? extends T>`) hoặc `GenericArrayType` (`T[]`). Vì vậy `(Class<?>) field.getGenericType()` chỉ an toàn sau khi xác nhận đúng subtype; nó sẽ thất bại với `List<String>`.

`Type.getTypeName()` giúp in tên, nhưng một parser cần `instanceof` để đi từ `ParameterizedType` tới arguments, từ `TypeVariable` tới bounds/declaration, và từ `GenericArrayType` tới component. Trong bài này chúng ta dùng những nhánh đó để hiểu **field type, method return và declared throws**, không xây lại toàn bộ mô hình `Type` chung.

## 4. `Field.getGenericType()`: giữ thông tin trên field

Xét khai báo:

~~~java
class Container<T extends Number> {
    T current;
    java.util.List<T> history;
    java.util.List<? extends T> view;
    T[] batch;
}
~~~

Đây là mảnh code hợp lệ dùng để giải thích; các kiểu `Type` có thể được Reflection quan sát như sau:

- `current.getType()` trả `Number.class`, do erasure của `T extends Number` là bound đầu tiên `Number`. `current.getGenericType()` trả một `TypeVariable` tên `T`.
- `history.getType()` trả `List.class`; `getGenericType()` trả `ParameterizedType` chứa argument `T`.
- `view.getGenericType()` là `ParameterizedType` chứa `WildcardType` đại diện cho `? extends T`.
- `batch.getType()` trả class của mảng `Number[]`; `getGenericType()` trả `GenericArrayType` cho `T[]`.

Quan trọng: khi subclass cụ thể hóa `T`, ví dụ `class IntContainer extends Container<Integer> {}`, việc lấy field từ `Container.class` hoặc field kế thừa trên `IntContainer.class` **không tự làm `getGenericType()` biến `T` thành `Integer`**. `Field` vẫn mô tả **khai báo gốc**. Muốn áp dụng substitution `T → Integer`, framework phải tìm quan hệ generic supertype (thí dụ `getGenericSuperclass()`), xây bản đồ type variable và giải quyết đệ quy qua superclass/interface.

Ngoài ra, `getGenericType()` cho biết **kiểu khai báo**, không biết generic parameter thực của một object bất kỳ trong heap. Một instance `new Container<Integer>()` không tự mang toàn bộ tham số generic runtime để field có thể suy ra `Integer` từ nó.

## 5. `Method.getGenericReturnType()`: kiểu kết quả đã khai báo

Với method:

~~~java
class Service<T extends Number> {
    java.util.List<T> list() { return java.util.List.of(); }
    T latest() { return null; }
    <U> U echo(U value) { return value; }
}
~~~

- `list().getReturnType()`: `List.class`; `getGenericReturnType()`: `ParameterizedType` dạng `List<T>`.
- `latest().getReturnType()`: `Number.class`; `getGenericReturnType()`: `TypeVariable T` của **class**.
- `echo(Object).getReturnType()`: `Object.class`; `getGenericReturnType()`: `TypeVariable U` của **method**.

`Method.getTypeParameters()` trả type parameters do **method tự khai báo**, như `U`; `Class.getTypeParameters()` trả type parameters của class, như `T`. Khi thấy một `TypeVariable`, dùng `getGenericDeclaration()` nếu cần xác định nó thuộc class, method hay constructor nào.

Phân biệt **kiểu generic trả về khai báo** với **kiểu object trả về sau một lời gọi**: `getGenericReturnType()` không gọi method, không xét polymorphism tại một instance cụ thể và không bảo đảm người gọi nhận đúng object theo generic ở runtime. Nó là metadata dùng để phân tích API và hỗ trợ conversion.

### Bridge method có thể ảnh hưởng công cụ quét

Compiler có thể tạo **bridge method** để duy trì overriding sau erasure. Công cụ dùng `getDeclaredMethods()` đôi lúc thấy cả method source và bridge method; `method.isBridge()`/`method.isSynthetic()` có ích khi muốn chọn method nghiệp vụ. Đừng phân tích generic return type của một bridge rồi nhận nhầm đó là khai báo source mà người dùng viết. Nếu làm bytecode analysis, có thể cần giữ cả hai như hai entry khác nhau.

## 6. `getGenericExceptionTypes()`: generic ở mệnh đề `throws`

Với một generic method:

~~~java
class Worker {
    <E extends Exception> void run(E exception) throws E {
        throw exception;
    }
}
~~~

- `run(Exception).getExceptionTypes()` trả `Class<?>[]` chứa `Exception.class` do erasure của `E extends Exception`.
- `run(Exception).getGenericExceptionTypes()` trả `Type[]` chứa `TypeVariable E`, phản ánh khai báo `throws E`.
- `getGenericExceptionTypes()` của method không khai báo `throws` trả mảng rỗng.
- Nếu method khai báo `throws java.io.IOException, IllegalStateException`, hai API đều trả hai phần tử theo thứ tự, bản generic thường là các `Class<?>` vì không có type variable tại hai chỗ đó.

Điều này hữu ích cho framework biểu diễn hợp đồng lỗi ở mức source/generic nhưng **không** có nghĩa runtime kiểm tra checked exceptions theo generic khi method được gọi. Java compile-time type checking quyết định yêu cầu `catch`/`throws` cho code Java; JVM không có hệ thống checked exception enforcement ở mức `Method.invoke`. Khi gọi qua Reflection, exception từ body method được đóng trong `InvocationTargetException` và phải được xử lý riêng.

Lưu ý giới hạn ngôn ngữ: không được khai báo một generic class trực tiếp kế thừa `Throwable`; trong `throws`, type variable phải có bound phù hợp với `Throwable`. Cũng không có `throws List<IOException>`; generic exception type ở đây thường là **type variable**, không phải một parameterized collection of exceptions.

### Method và constructor đều có generic exception types

`Constructor<?>` cũng kế thừa `Executable.getGenericExceptionTypes()`. Một constructor generic có thể khai báo type parameter riêng:

~~~java
class Factory {
    <E extends java.io.IOException> Factory(E cause) throws E {
        throw cause;
    }
}
~~~

Khi tra cứu constructor theo kiểu **erasure** của `E` là `IOException.class`, `getGenericExceptionTypes()[0]` mô tả `E`; `getExceptionTypes()[0]` là `IOException.class`. Đây là metadata của **constructor**, không phải generic return type; constructor không có giá trị trả về như method.

## 7. Ví dụ Java 17+ đầy đủ và kết quả kiểm chứng

Lưu file `GenericTypeIntrospectionDemo.java`:

~~~java
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GenericTypeIntrospectionDemo {
    static class Repository<T extends Number & Comparable<T>> {
        public T value;
        public List<? extends T> view;
        public T[] batch;
        public Map<String, List<T>> grouped;

        public List<T> load() {
            return List.of();
        }

        public T current() {
            return null;
        }

        public <E extends Exception> void fail(E error) throws E {
            throw error;
        }

        public void save() throws IOException, IllegalStateException {
        }
    }

    static class LongRepository extends Repository<Long> {
    }

    static class Factory {
        public <X extends IOException> Factory(X problem) throws X {
            throw problem;
        }
    }

    static String kinds(Type type) {
        if (type instanceof ParameterizedType) {
            return "ParameterizedType";
        }
        if (type instanceof TypeVariable<?>) {
            return "TypeVariable";
        }
        if (type instanceof GenericArrayType) {
            return "GenericArrayType";
        }
        if (type instanceof WildcardType) {
            return "WildcardType";
        }
        if (type instanceof Class<?>) {
            return "Class";
        }
        return "Other";
    }

    static String names(Type[] types) {
        return Arrays.stream(types)
                .map(Type::getTypeName)
                .collect(Collectors.joining(", "));
    }

    public static void main(String[] args) throws Exception {
        Field value = Repository.class.getDeclaredField("value");
        Field view = Repository.class.getDeclaredField("view");
        Field batch = Repository.class.getDeclaredField("batch");

        System.out.println("value raw=" + value.getType().getTypeName());
        System.out.println("value generic="
                + value.getGenericType().getTypeName()
                + " (" + kinds(value.getGenericType()) + ")");
        System.out.println("view generic="
                + view.getGenericType().getTypeName()
                + " (" + kinds(view.getGenericType()) + ")");
        System.out.println("batch raw=" + batch.getType().getTypeName());
        System.out.println("batch generic="
                + batch.getGenericType().getTypeName()
                + " (" + kinds(batch.getGenericType()) + ")");

        Method load = Repository.class.getDeclaredMethod("load");
        Method current = Repository.class.getDeclaredMethod("current");
        System.out.println("load return raw="
                + load.getReturnType().getTypeName());
        System.out.println("load return generic="
                + load.getGenericReturnType().getTypeName());
        System.out.println("current return generic="
                + current.getGenericReturnType().getTypeName());

        Method fail = Repository.class.getDeclaredMethod(
                "fail", Exception.class);
        System.out.println("fail throws raw="
                + names(fail.getExceptionTypes()));
        System.out.println("fail throws generic="
                + names(fail.getGenericExceptionTypes()));

        Method save = Repository.class.getDeclaredMethod("save");
        System.out.println("save throws generic="
                + names(save.getGenericExceptionTypes()));
        System.out.println("load throws count="
                + load.getGenericExceptionTypes().length);

        Constructor<?> factory = Factory.class.getDeclaredConstructor(
                IOException.class);
        System.out.println("factory throws raw="
                + names(factory.getExceptionTypes()));
        System.out.println("factory throws generic="
                + names(factory.getGenericExceptionTypes()));

        ParameterizedType parent = (ParameterizedType)
                LongRepository.class.getGenericSuperclass();
        System.out.println("concrete super argument="
                + parent.getActualTypeArguments()[0].getTypeName());
        System.out.println("inherited field generic="
                + LongRepository.class.getField("value")
                        .getGenericType().getTypeName());
    }
}
~~~

Chạy với Java 17 hoặc mới hơn:

~~~bash
javac -encoding UTF-8 GenericTypeIntrospectionDemo.java
java GenericTypeIntrospectionDemo
~~~

Kết quả dự kiến:

~~~text
value raw=java.lang.Number
value generic=T (TypeVariable)
view generic=java.util.List<? extends T> (ParameterizedType)
batch raw=java.lang.Number[]
batch generic=T[] (GenericArrayType)
load return raw=java.util.List
load return generic=java.util.List<T>
current return generic=T
fail throws raw=java.lang.Exception
fail throws generic=E
save throws generic=java.io.IOException, java.lang.IllegalStateException
load throws count=0
factory throws raw=java.io.IOException
factory throws generic=X
concrete super argument=java.lang.Long
inherited field generic=T
~~~

Đối chiếu ý nghĩa:

1. `value` thuộc kiểu biến `T` với bound đầu tiên `Number`, nên erasure của field là `Number`. Phần metadata generic vẫn là `T`.
2. `view` là `ParameterizedType`; nếu cần lấy wildcard `? extends T`, ép `view.getGenericType()` sang `ParameterizedType` rồi lấy `getActualTypeArguments()[0]` và kiểm tra `instanceof WildcardType`.
3. `batch` minh họa `GenericArrayType`: class runtime của mảng đã erasure thành `Number[]`, trong generic signature vẫn là `T[]`.
4. `load()` trả về `List<T>` ở generic metadata nhưng `List.class` ở kiểu raw; `current()` có generic return type là `T`.
5. `fail(...)` dùng một type variable `E` trong `throws`, bản raw trả bound `Exception`; `save()` khai báo hai exception types thực.
6. `Factory` cho thấy constructor cũng có `getGenericExceptionTypes()` và type variable trong `throws`.
7. `LongRepository` biết đối số generic của superclass là `Long`, nhưng field kế thừa `value` vẫn do `Repository` khai báo. `Field.getGenericType()` trả `T`, **không tự resolve thành `Long`**.

Ví dụ không cần `-parameters` vì không đọc tên formal parameters. Metadata generic signature được trình biên dịch lưu trong class file và khác với cờ lưu parameter names.

## 8. Quy trình đọc chữ ký thành viên

Bắt đầu từ đúng loại member: `Field.getGenericType()` cho một field, `Method.getGenericReturnType()` cho kiểu trả về, và `Executable.getGenericExceptionTypes()` cho danh sách kiểu ở `throws` (method **hoặc** constructor). So sánh với `getType()`, `getReturnType()`, `getExceptionTypes()` khi cần erasure.

Với mỗi `Type` nhận được, kiểm tra `instanceof`. Field `Map<String,List<T>>` cần duyệt hai tầng `ParameterizedType`; return `T[]` cần `GenericArrayType.getGenericComponentType()`; `throws E` cần `TypeVariable.getGenericDeclaration()` và bounds. Nếu subclass gắn `T = Long`, phải đọc thêm generic superclass/interface và ánh xạ theo declaration; **getter của member không tự thế `T`**. Đặc biệt, `getGenericExceptionTypes()` chỉ cho những exception khai báo trong `throws`, không là danh sách đầy đủ exception thực thi có thể ném.

## 9. Erasure, generic signature và khả năng thực thi

Ba khía cạnh cần tách bạch:

- **Source declaration:** `List<String>`, `T extends Number`, `throws E`; compiler kiểm tra ràng buộc generic khi biên dịch.
- **JVM method/field descriptors:** sử dụng type sau erasure để liên kết và gọi, ví dụ `List`, `Number`, `Exception`.
- **Signature metadata:** khi có và hợp lệ, Reflection dùng nó để dựng `ParameterizedType`, `TypeVariable` hoặc các dạng `Type` khác.

Vì thế `Field.getGenericType()` không ép JVM ngăn đặt một `List<Integer>` vào field khai báo `List<String>` qua raw Reflection; compiler có kiểm tra generic nhưng runtime check trên `Field.set` chủ yếu dựa vào raw type `List`. Công cụ serializer/mapper muốn bảo đảm kiểu phần tử phải tự kiểm tra hoặc chuyển đổi theo generic metadata.

Tương tự, `Method.getGenericReturnType()` không biến giá trị trả về thành một object kiểu generic có thể kiểm tra mọi phần tử. Những công cụ phản xạ chỉ có thể phân tích **khai báo**; nếu cần đảm bảo dữ liệu runtime thỏa schema, cần validation riêng.

Ngoài ra, vì mảng `Type[]` của exceptions mô tả `throws` khai báo, nó **không thống kê mọi exception có thể ném** ở runtime: unchecked exception có thể không liệt kê, và nhiều nhánh thực thi có thể ném exception được phép bởi hệ thống kiểu mà không hiện diện như một type trực tiếp tại vị trí phản xạ.

## 10. Lưu ý với generic kế thừa, override và bridge

### Type variable thuộc declaration nào?

Cùng chuỗi `T` có thể xuất hiện ở nhiều class/method hoàn toàn khác nhau. `TypeVariable.getGenericDeclaration()` xác định declaration sở hữu `T`. Ví dụ `Repository<T>` khác với method `<T> T identity(T input)`; gộp chúng chỉ vì tên `T` giống nhau là sai.

Khi phân tích field/method kế thừa:

- `Field.getDeclaringClass()` và `Method.getDeclaringClass()` cho biết owner của khai báo gốc.
- `subclass.getGenericSuperclass()` hoặc `getGenericInterfaces()` giúp truy vết kiểu cụ thể hóa tại class con.
- `getGenericType()` và `getGenericReturnType()` vẫn báo kiểu tại **declaration**. Muốn có kiểu áp dụng trong ngữ cảnh subclass, framework phải thay type variable có ngữ cảnh.

### Bridge methods

Compiler đôi khi sinh bridge method vì erasure của override generic/covariant return. `getDeclaredMethods()` có thể trả cả method người viết và bridge; dùng `isBridge()` / `isSynthetic()` để phân biệt theo nhu cầu. Với công cụ phân tích metadata cho người học, thường nên chọn method thực; với công cụ bytecode/debug phải xem cả bridge.

Không nên kết luận một bridge có generic return type mà người dùng trực tiếp khai báo. Hãy kiểm tra declaring class, signature raw, flags và quan hệ override.

## 11. Ngoại lệ và kiểm thử các tình huống biên

Các API generic đọc thông tin class file có thể gặp dữ liệu metadata bất thường:

| Sự cố | Tác động |
| --- | --- |
| `GenericSignatureFormatError` | Generic signature trong class file không tuân định dạng hợp lệ. |
| `TypeNotPresentException` | Generic signature tham chiếu một kiểu không tìm được/không load được. |
| `MalformedParameterizedTypeException` | Không thể dựng biểu diễn parameterized type theo signature. |
| `NoSuchFieldException` hoặc `NoSuchMethodException` | Sai thành viên hoặc chữ ký raw lúc tra cứu, không phải lỗi generic metadata. |
| `ClassCastException` trong code người dùng | Ép một `Type` là `ParameterizedType`/`TypeVariable` trực tiếp sang `Class<?>` hoặc ép nhầm loại metadata. |

Tài liệu Java SE 17 mô tả ngoại lệ cụ thể trên từng getter (`Field.getGenericType`, `Method.getGenericReturnType`, `Executable.getGenericExceptionTypes`). Phân biệt lỗi đọc generic signature với lỗi tìm field/method hay lỗi gọi method.

Các trường hợp đáng thử thêm:

- Field `String plain`: cả `getType()` và `getGenericType()` thường là `String.class`.
- Field `List raw`: generic type có thể là `Class<?>` (không phải `ParameterizedType`).
- Field `List<?>`: cần duyệt wildcard, upper bound mặc định.
- Field `T[]`: phân biệt `GenericArrayType` và `Class<?>` biểu diễn array sau erasure.
- Method `void close()`: generic return type đại diện `void`, danh sách generic throws rỗng khi không khai báo.
- Method `<E extends Exception> void fail() throws E`: exception generic là `TypeVariable E`, không phải `Exception.class`.
- Class implements `Interface<String>` hoặc extends `Base<Integer>`: thử dựng bảng type variable substitution nhiều tầng.

## 12. Bài tập thực hành

1. Thêm `public Map<String, List<T>> grouped` vào output demo; phân tích `ParameterizedType` cấp ngoài và argument `List<T>` cấp trong.
2. Thêm field `List<? super T> sink`; dùng `WildcardType.getLowerBounds()` để thấy bound dưới.
3. Với `LongRepository`, viết hàm giải `T` sang `Long` từ `getGenericSuperclass()` rồi áp dụng vào field `value` mà không thay tên field.
4. Viết method `public <R> R convert(R input)`; so sánh owner của `TypeVariable R` với type variable `T` của class.
5. Thêm method `void normal()` không khai báo `throws` và xác minh `getGenericExceptionTypes().length == 0`.
6. So sánh thông tin `throws IOException`, `throws E extends IOException` và `throws IOException, IllegalStateException`; in cả raw và generic exception arrays.
7. Phát hiện bridge method khi override method trả generic hoặc covariant return, rồi thiết kế điều kiện chọn method dùng cho documentation.
8. Thử field raw `List` cạnh `List<String>` để quan sát khác biệt `Class<?>` và `ParameterizedType`.

## 13. Tham khảo

- [Java SE 17 — Field#getGenericType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html#getGenericType()): kiểu khai báo generic của field và các exception liên quan.
- [Java SE 17 — Method#getGenericReturnType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#getGenericReturnType()): kiểu kết quả được khai báo sau khi phân tích signature.
- [Java SE 17 — Executable#getGenericExceptionTypes](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html#getGenericExceptionTypes()): các exception types theo mệnh đề `throws`; dùng cho method và constructor.
- [Java SE 17 — Type](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Type.html): hợp đồng của các biểu diễn Type.
- [Java SE 17 — ParameterizedType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html), [TypeVariable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/TypeVariable.html), [WildcardType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/WildcardType.html), [GenericArrayType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/GenericArrayType.html).
- [Java Language Specification §4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): quy tắc xóa kiểu generic khi biên dịch.

**Ghi nhớ:** API `getType()`/`getReturnType()`/`getExceptionTypes()` trả `Class` theo kiểu sau erasure; `getGenericType()`/`getGenericReturnType()`/`getGenericExceptionTypes()` trả cấu trúc `Type` mô tả **generic declaration**. Từ khai báo này tới kiểu đã cụ thể hóa trong một subclass là một bước phân giải độc lập do công cụ cần thực hiện.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-model-010">Đọc tên kiểu bằng getTypeName thay vì ép mọi Type sang Class</a>

<details>
<summary>Xem chi tiết</summary>

# Đọc tên kiểu bằng `Type.getTypeName()` thay vì ép mọi `Type` sang `Class`

## 1. Vấn đề thường gặp trong Java Reflection

Trong Java Reflection, nhiều API mô tả kiểu generic bằng giao diện **`java.lang.reflect.Type`**. Khi cần ghi log, hiển thị chữ ký method, mô tả field hoặc chẩn đoán metadata, ta thường muốn đọc **tên của kiểu**.

Cách dễ gây lỗi là giả định mọi `Type` đều là một `Class<?>`:

```java
Type type = field.getGenericType();
String name = ((Class<?>) type).getName(); // Có thể ClassCastException!
```

Đây là **mảnh mã minh họa bên trong method**, nơi `field` là một `Field` hợp lệ. Nó chỉ đúng nếu đối tượng `type` thực sự triển khai `Class<?>`. Nếu field được khai báo `List<String>`, `getGenericType()` thường trả `ParameterizedType`; nếu kiểu là `T`, nó có thể trả `TypeVariable`; với `T[]` có thể là `GenericArrayType`. Ép các đối tượng này thành `Class<?>` gây `ClassCastException`.

Để **đọc chuỗi mô tả kiểu**, hãy gọi trực tiếp:

```java
Type type = field.getGenericType();
String name = type.getTypeName(); // Hoạt động với mọi triển khai Type hợp lệ
```

`Type.getTypeName()` được định nghĩa trên chính giao diện `Type` từ **Java 8**. Nó trả một chuỗi mô tả kiểu, bao gồm thông tin tham số kiểu khi thích hợp; triển khai mặc định của `Type` dùng `toString()`. Vì vậy có thể gọi nó mà không cần ép kiểu sang `Class`.

**Phân biệt rõ hai mục đích:**

- Chỉ cần **hiển thị tên / ghi log / thông báo lỗi** → ưu tiên `type.getTypeName()`.
- Cần **phân tích, so sánh hoặc biến đổi cấu trúc generic** → kiểm tra subtype của `Type` và dùng API tương ứng (`getActualTypeArguments()`, `getBounds()`, `getGenericComponentType()`...). Một chuỗi đẹp không thay thế mô hình dữ liệu kiểu.

## 2. `Type` không đồng nghĩa với `Class<?>`

`Type` là giao diện chung cho các dạng kiểu mà Reflection biểu diễn. `Class<?>` **chỉ là một dạng cụ thể** của `Type`.

| Dạng `Type` | Khai báo ví dụ | Ví dụ của `getTypeName()` | Cách đọc cấu trúc |
| --- | --- | --- | --- |
| `Class<?>` | `String`, `int`, `String[]`, raw `List` | `java.lang.String[]` | `Class` API: `isArray()`, `getComponentType()`... |
| `ParameterizedType` | `List<String>`, `Map<String, Integer>` | `java.util.List<java.lang.String>` | `getRawType()`, `getActualTypeArguments()`, `getOwnerType()` |
| `TypeVariable<?>` | `T` trong `<T extends Number>` | `T` | `getBounds()`, `getGenericDeclaration()`, `getName()` |
| `GenericArrayType` | `T[]`, `List<String>[]` | `T[]`, `java.util.List<java.lang.String>[]` | `getGenericComponentType()` |
| `WildcardType` | `? extends Number`, `? super Integer` | `? extends java.lang.Number` | `getUpperBounds()`, `getLowerBounds()` |

`WildcardType` thường nằm **bên trong đối số kiểu** của `ParameterizedType`, như phần `? extends Number` trong `Map<String, ? extends Number>`. Nó không nhất thiết là `Type` trực tiếp của toàn bộ field/parameter, nhưng có thể lấy ra qua `getActualTypeArguments()`.

Hai điểm dễ nhầm:

1. **`String[]` thường là `Class<?>`**, còn **`T[]` có thể là `GenericArrayType`**. Thấy dấu `[]` trong tên không đủ để suy ra subtype của `Type`.
2. `TypeVariable.getTypeName()` thường chỉ in tên `T`, **không tự liệt kê bound** `Number`/`Comparable<T>`. Muốn biết bounds, phải gọi `getBounds()`.

`ParameterizedType`, `TypeVariable`, `GenericArrayType` và `WildcardType` đều có thể trả chuỗi hiển thị hợp lệ qua cùng phương thức `getTypeName()`; không cần tạo các chuỗi khác nhau bằng ép kiểu.

## 3. Ví dụ Java 17+ đầy đủ: năm dạng `Type`

Tạo tệp **`TypeNameDemo.java`**:

```java
import java.lang.reflect.*;
import java.util.*;

public class TypeNameDemo<T extends Number> {
    List<String> words;
    Map<String, ? extends Number> scores;
    T value;
    T[] history;
    List<String>[] groups;
    String[] labels;

    public void process(List<Integer> input, List<? super Integer> sink) {}

    private static String category(Type type) {
        if (type instanceof Class<?>) return "Class";
        if (type instanceof ParameterizedType) return "ParameterizedType";
        if (type instanceof TypeVariable<?>) return "TypeVariable";
        if (type instanceof GenericArrayType) return "GenericArrayType";
        if (type instanceof WildcardType) return "WildcardType";
        return "Other";
    }

    private static void show(String label, Type type) {
        System.out.println(label + ": " + type.getTypeName()
                + " [" + category(type) + "]");
    }

    public static void main(String[] args) throws Exception {
        for (String name : List.of("words", "scores", "value",
                "history", "groups", "labels")) {
            Field field = TypeNameDemo.class.getDeclaredField(name);
            show(name, field.getGenericType());
            System.out.println("  erased: " + field.getType().getTypeName());
        }

        ParameterizedType map = (ParameterizedType)
                TypeNameDemo.class.getDeclaredField("scores").getGenericType();
        show("map raw", map.getRawType());
        Type wildcard = map.getActualTypeArguments()[1];
        show("wildcard", wildcard);

        Method method = TypeNameDemo.class.getDeclaredMethod(
                "process", List.class, List.class);
        Type[] parameters = method.getGenericParameterTypes();
        show("process param 0", parameters[0]);
        show("process param 1", parameters[1]);

        Type generic = TypeNameDemo.class.getDeclaredField("words")
                .getGenericType();
        System.out.println("words instanceof Class: "
                + (generic instanceof Class<?>));
    }
}
```

Biên dịch/chạy:

```bash
javac TypeNameDemo.java
java TypeNameDemo
```

**Kết quả đã kiểm chứng** khi biên dịch/chạy với JDK hỗ trợ Java 17+:

```text
words: java.util.List<java.lang.String> [ParameterizedType]
  erased: java.util.List
scores: java.util.Map<java.lang.String, ? extends java.lang.Number> [ParameterizedType]
  erased: java.util.Map
value: T [TypeVariable]
  erased: java.lang.Number
history: T[] [GenericArrayType]
  erased: java.lang.Number[]
groups: java.util.List<java.lang.String>[] [GenericArrayType]
  erased: java.util.List[]
labels: java.lang.String[] [Class]
  erased: java.lang.String[]
map raw: java.util.Map [Class]
wildcard: ? extends java.lang.Number [WildcardType]
process param 0: java.util.List<java.lang.Integer> [ParameterizedType]
process param 1: java.util.List<? super java.lang.Integer> [ParameterizedType]
words instanceof Class: false
```

Ví dụ cố ý lấy tên field **theo danh sách cố định**, không dựa vào thứ tự ngẫu nhiên của `getDeclaredFields()`. Vì vậy kết quả in ra có thứ tự xác định.

### Diễn giải kết quả

- `words` khai báo `List<String>`: `Field.getType()` chỉ có erasure `List.class`, còn `Field.getGenericType()` giữ kiểu có argument `String`. `getTypeName()` in đầy đủ `java.util.List<java.lang.String>`.
- `scores` có hai generic arguments; argument thứ hai là wildcard `? extends Number`. Kiểu toàn field là `ParameterizedType`; chỉ khi đọc `map.getActualTypeArguments()[1]` mới có `WildcardType` trực tiếp.
- `value` có kiểu `T extends Number`. Generic representation `TypeVariable` in `T`; kiểu đã erasure là `Number` (bound đầu tiên).
- `history` là mảng của `T`: `getGenericType()` là `GenericArrayType` biểu diễn `T[]`, trong khi `getType()` là `Number[].class`.
- `groups` là `List<String>[]`. Vì thành phần mảng là generic parameterized type, generic representation là `GenericArrayType`; erasure của nó là `List[]`.
- `labels` là `String[]` thông thường, nên kiểu generic cũng chính là `Class<?>`.
- `process` cho thấy `Method.getGenericParameterTypes()` trả `Type[]` cho danh sách tham số. Cả `List<Integer>` và `List<? super Integer>` được hiển thị chính xác mà không cần ép `Type` về `Class`.
- `words instanceof Class: false` là bằng chứng cụ thể tại sao `((Class<?>) field.getGenericType())` không an toàn.

Dùng `Class<?>` khi cần kiểu runtime đã erasure là hợp lý; vấn đề nằm ở việc **ép mọi `Type` thành `Class<?>` mà không kiểm tra**.

## 4. Lấy `Type` từ những API nào?

Các phương thức sau xuất hiện thường xuyên khi viết thư viện mapping, dependency injection, serializer hoặc công cụ phân tích chữ ký:

| Nguồn metadata | Phương thức | Kết quả |
| --- | --- | --- |
| Field | `Field.getGenericType()` | Một `Type` mô tả kiểu khai báo của field |
| Method | `Method.getGenericReturnType()` | Một `Type` mô tả kiểu trả về generic |
| Method/Constructor | `Executable.getGenericParameterTypes()` | `Type[]` mô tả các formal parameters |
| Method/Constructor | `Executable.getGenericExceptionTypes()` | `Type[]` mô tả các kiểu trong `throws` |
| Class | `Class.getGenericSuperclass()` | `Type` biểu diễn superclass có generic args, hoặc `null` khi không có superclass theo hợp đồng |
| Class | `Class.getGenericInterfaces()` | `Type[]` biểu diễn các generic superinterfaces |
| Parameter | `Parameter.getParameterizedType()` | Một `Type` của parameter |
| ParameterizedType | `getActualTypeArguments()` | `Type[]` của đối số kiểu |
| TypeVariable | `getBounds()` | `Type[]` của bounds |
| WildcardType | `getUpperBounds()` / `getLowerBounds()` | `Type[]` của upper/lower bounds |
| GenericArrayType | `getGenericComponentType()` | `Type` của thành phần mảng |

Có thể in nhiều kết quả mà không ép kiểu:

```java
// Các mảnh mã đặt trong một method có Field field, Method method hợp lệ.
System.out.println(field.getGenericType().getTypeName());
System.out.println(method.getGenericReturnType().getTypeName());

for (Type parameterType : method.getGenericParameterTypes()) {
    System.out.println(parameterType.getTypeName());
}
```

Đối với API **có thể trả `null`**, kiểm tra trước khi gọi. Ví dụ `Object.class.getGenericSuperclass() == null`, vì `Object` không có superclass; primitive, void và interface cũng có những trường hợp không có superclass. Không viết `someClass.getGenericSuperclass().getTypeName()` khi chưa chắc kết quả khác `null`.

**Tham số implicit của constructor:** với một số inner-class constructor, `getGenericParameterTypes()` có thể có độ dài khác `getParameterTypes()` do cách metadata generic và tham số implicit/synthetic được biểu diễn. Do đó tránh ghép hai mảng theo cùng index mà không kiểm tra. Việc gọi `getTypeName()` trên từng `Type` đã nhận được vẫn an toàn.

## 5. Muốn đọc cấu trúc: kiểm tra subtype và dùng đúng API

`getTypeName()` cho một **chuỗi để đọc**. Khi muốn biết `List` chứa loại phần tử nào, wildcard có bound gì, hoặc một type variable do declaration nào tạo ra, hãy giữ `Type` và phân nhánh:

```java
// Mảnh phương thức đặt trong một class; cần import java.lang.reflect.*.
static void describe(Type type) {
    System.out.println("type = " + type.getTypeName());

    if (type instanceof Class<?> cls) {
        System.out.println("runtime class = " + cls.getName());
    } else if (type instanceof ParameterizedType p) {
        System.out.println("raw = " + p.getRawType().getTypeName());
        for (Type arg : p.getActualTypeArguments()) {
            System.out.println("argument = " + arg.getTypeName());
        }
    } else if (type instanceof TypeVariable<?> variable) {
        System.out.println("variable = " + variable.getName());
        for (Type bound : variable.getBounds()) {
            System.out.println("bound = " + bound.getTypeName());
        }
    } else if (type instanceof GenericArrayType array) {
        System.out.println("component = "
                + array.getGenericComponentType().getTypeName());
    } else if (type instanceof WildcardType wildcard) {
        for (Type upper : wildcard.getUpperBounds()) {
            System.out.println("upper = " + upper.getTypeName());
        }
        for (Type lower : wildcard.getLowerBounds()) {
            System.out.println("lower = " + lower.getTypeName());
        }
    }
}
```

Đây là **mảnh mã minh họa**, không có `main` độc lập. `instanceof` pattern matching là cú pháp Java 16+, chạy được trên Java 17. Đoạn này chỉ in **một tầng**, không tự đi sâu đệ quy vào generic arguments lồng nhiều cấp. Nếu muốn phân tích `Map<String, List<? extends Number>>`, phải duyệt các `Type` thành phần theo cây.

### Đọc generic arguments

Với `List<String>` trong ví dụ:

```java
Type generic = TypeNameDemo.class.getDeclaredField("words")
        .getGenericType();

if (generic instanceof ParameterizedType p) {
    Type raw = p.getRawType();            // List
    Type element = p.getActualTypeArguments()[0]; // String
    System.out.println(raw.getTypeName());
    System.out.println(element.getTypeName());
}
```

Hai dòng in `java.util.List` và `java.lang.String`. Chỉ ép `ParameterizedType` **sau khi đã xác nhận** kiểu tương ứng; không ép trực tiếp `generic` sang `Class<?>`. Nếu làm việc với `Map<K,V>`, mảng arguments có thể chứa `ParameterizedType`, `TypeVariable` hoặc `WildcardType` lồng nhau.

### Đọc bounds

Một `TypeVariable` như `T` không có đủ thông tin qua chuỗi `"T"`. Ví dụ với `TypeNameDemo<T extends Number>`, `getBounds()` cho `Number`:

```java
Type type = TypeNameDemo.class.getDeclaredField("value").getGenericType();

if (type instanceof TypeVariable<?> variable) {
    System.out.println(variable.getName());
    for (Type bound : variable.getBounds()) {
        System.out.println(bound.getTypeName());
    }
}
```

Các dòng in `T` và `java.lang.Number`. Hai declarations khác nhau có thể dùng cùng tên `T`; chúng không phải cùng một type variable. Nếu xây mô hình generic nghiêm ngặt, dùng `getGenericDeclaration()`/định danh declaration thay vì chỉ so chuỗi `"T"`.

Với wildcard `? super Integer`, `getLowerBounds()` nêu `Integer`; với `? extends Number`, `getUpperBounds()` nêu `Number`. Wildcard không giới hạn tường minh thường có upper bound mặc định `Object`, nhưng chuỗi hiển thị có thể chỉ là `?`. Vì vậy không suy diễn bounds bằng cách tách chuỗi.

### Đọc mảng generic

Đối với `T[]` hoặc `List<String>[]`, hãy dùng `GenericArrayType.getGenericComponentType()`. Với `String[]`, kiểu là `Class<?>` và lấy phần tử bằng `Class.getComponentType()`. Một tiện ích phân tích mảng tổng quát phải xử lý **cả hai nhánh**.

## 6. So sánh `getTypeName()`, `getName()`, `getSimpleName()` và `getCanonicalName()`

Đây là các API dễ bị nhầm do cùng chứa chữ “name”:

| API | Được định nghĩa trên | Dùng cho | Giới hạn |
| --- | --- | --- | --- |
| `Type.getTypeName()` | `Type` | Chuỗi dễ đọc cho **mọi dạng `Type`** | Không bảo đảm là tên `Class` có thể nạp hoặc cú pháp canonical |
| `Class.getTypeName()` | `Class` (ghi đè triển khai `Type`) | Hiển thị tên kiểu kể cả array/primitive | Chỉ áp dụng trực tiếp khi `Type` thực sự là `Class` |
| `Class.getName()` | `Class` | Tên kiểu runtime; thường là binary name với class thông thường, JVM descriptor dạng đặc biệt với mảng | Không giữ `List<String>` từ generic `ParameterizedType` |
| `Class.getSimpleName()` | `Class` | Tên ngắn kiểu nguồn, ví dụ `String` | Không đại diện generic arguments; anonymous class có tên ngắn rỗng |
| `Class.getCanonicalName()` | `Class` | Tên chuẩn nếu kiểu có canonical name | Có thể trả `null` với local/anonymous/hidden classes và một số array tương ứng |

Ví dụ một mảng:

```java
System.out.println(String[].class.getTypeName());
System.out.println(String[].class.getName());
```

Kết quả:

```text
java.lang.String[]
[Ljava.lang.String;
```

`getTypeName()` có thể dễ đọc hơn trong log, nhưng không vì vậy mà hai API trở nên thay thế được ở mọi chỗ. Chẳng hạn, `Class.forName(...)` dành cho **binary names** của class thông thường (và tên descriptor thích hợp cho mảng); không nhận `"java.util.List<java.lang.String>"` để tạo một `Class` của `List<String>`. Trong Java không tồn tại một `Class` riêng cho mỗi tham số hóa như `List<String>` và `List<Integer>`.

Với nested types hoặc triển khai `Type` cụ thể, biểu diễn `getTypeName()` có thể dùng ký tự `$`, dấu `<...>`, `? extends` hoặc `[]`. Đây là **chuỗi mô tả do API cung cấp**, không phải định dạng trao đổi dữ liệu đã được cam kết tương đương Java source canonical name.

## 7. Trường hợp dùng đúng khi cần `Class<?>`

Đừng tránh `Class` trong mọi trường hợp: `Class<?>` là lựa chọn đúng khi thao tác cần **runtime class**, ví dụ `getDeclaredMethod`, `isAssignableFrom`, `isInstance`, `Field.getType` hoặc `Method.getReturnType`.

```java
// Mảnh mã minh họa:
Field field = TypeNameDemo.class.getDeclaredField("words");

Class<?> erased = field.getType();
System.out.println(erased.getTypeName()); // java.util.List

Type declared = field.getGenericType();
System.out.println(declared.getTypeName()); // java.util.List<java.lang.String>
```

Hai kết quả diễn tả hai câu hỏi khác nhau: **erased runtime class** và **generic declaration**.

Đối với `Class.getDeclaredMethod(String, Class<?>...)`, cần truyền erased parameter classes, không thể truyền `Type[]` trực tiếp. Ví dụ method `process(List<Integer>, List<? super Integer>)` trong chương trình:

```java
Method method = TypeNameDemo.class.getDeclaredMethod(
        "process", List.class, List.class);
```

Dùng `List.class` hai lần vì cả hai tham số đã erasure thành `List`. **Không có cú pháp `List<String>.class`**. `getTypeName()` chỉ là phép mô tả; nó không khôi phục các class generic tham số hóa ở runtime.

Nếu nhận `Type type` chưa biết subtype, chỉ cast khi đã kiểm tra:

```java
if (type instanceof Class<?> cls) {
    // Có thể dùng cls để kiểm tra runtime type hoặc lookup.
    System.out.println(cls.getName());
} else {
    // Type này chứa cấu trúc khác: xử lý qua các subtype của Type.
    System.out.println(type.getTypeName());
}
```

Đừng giả định rằng `ParameterizedType.getRawType()` luôn có thể biến mọi `Type` lồng nhau thành `Class<?>` bằng cách cast bừa. Nếu framework cần tính erasure tương ứng cho cả `TypeVariable`, `WildcardType` hay mảng generic, hãy xây thuật toán rõ quy tắc và xử lý giới hạn; `getTypeName()` không thực hiện công việc “erasure” đó.

## 8. Có thể dùng chuỗi `getTypeName()` như một type parser không?

**Không nên** dùng `getTypeName()` như định dạng máy có hợp đồng parsing ổn định. Javadoc mô tả nó là chuỗi **mang thông tin về kiểu**, bao gồm thông tin tham số kiểu; triển khai mặc định của `Type` gọi `toString()`. Hình thức hiển thị không thay thế cấu trúc các đối tượng `Type`.

Những phép xử lý chuỗi dễ sai gồm:

- Tách `List<Map<String, Integer>>` theo dấu phẩy đầu tiên: generic types có thể lồng nhiều cấp.
- Bỏ dấu `<...>` rồi giả định luôn tạo được class với `Class.forName`: tên inner/member và kiểu mảng có quy tắc khác; `T` và wildcard không phải tên class để nạp.
- So `"T"` để đồng nhất type variables từ hai declarations khác nhau: cùng tên chưa chắc cùng biến kiểu.
- Phân tích `? extends Number` bằng substring và bỏ qua bounds lồng nhau/generic owner type.
- Dựa vào tên class triển khai nội bộ của JDK cho `ParameterizedType` thay vì giao diện `java.lang.reflect.ParameterizedType`.

Nếu chỉ cần ghi log hoặc thông báo lỗi, `getTypeName()` rất tiện. Nếu cần mã hóa metadata lâu dài, xây schema chứa category, raw type, type arguments, owner, bounds, component type và thông tin declaration tương ứng. Nếu cần kiểm tra JSON/đối tượng runtime, kiểm tra **giá trị thực** chứ không chỉ chuỗi generic signature.

### Khi không có đủ metadata generic

`Field.getGenericType()`, `Method.getGenericReturnType()` và các API tương tự đọc generic declaration khi class file chứa thông tin cần thiết. Kiểu raw như `List values` không chứa `String` để truy lại; sau type erasure, Reflection không thể nhìn vào instance `List` mà chắc chắn xác định generic argument lúc khai báo ban đầu.

Trong bytecode thiếu/sai generic signature hoặc classpath thiếu kiểu được tham chiếu, một số API generic Reflection có thể ném `GenericSignatureFormatError`, `TypeNotPresentException` hoặc `MalformedParameterizedTypeException`. Những tình huống này khác với `ClassCastException` do code tự ép `Type` không đúng. Một chương trình phân tích bytecode cần báo rõ lỗi metadata, không nên lặng lẽ giả định generic argument là `Object` nếu không có căn cứ.

## 9. Những lỗi lập trình thường gặp

| Sai lầm | Hệ quả | Cách đúng |
| --- | --- | --- |
| `((Class<?>) field.getGenericType()).getName()` | `ClassCastException` nếu là `ParameterizedType` | `field.getGenericType().getTypeName()` để hiển thị |
| `(Class<?>) parameterized.getActualTypeArguments()[0]` | Thất bại nếu argument là wildcard, type variable hoặc parameterized type lồng | Kiểm tra `instanceof` trước khi cast |
| `type.toString()` rồi ép chuỗi theo một định dạng tự đoán | Có thể phá cấu trúc generic phức tạp | `type.getTypeName()` để hiển thị; subtype APIs để phân tích |
| `type.getTypeName()` rồi `Class.forName(name)` | Tên generic/wildcard/variable không phải binary name có thể nạp | Chỉ nạp class theo tên phù hợp hợp đồng của `Class.forName` |
| Cho rằng `getGenericType()` luôn là `ParameterizedType` | Các kiểu nguyên thủy, array thường và raw class trả `Class` | Phân nhánh tất cả dạng `Type` liên quan |
| Cho rằng `TypeVariable.getTypeName()` luôn in cả bounds | Thường chỉ được `T` | Dùng `getBounds()` |
| Dùng `getTypeName()` để suy ra kiểu phần tử của `List` instance | Không phản ánh runtime contents | Kiểm tra metadata declaration và validate dữ liệu riêng |
| Gọi `getGenericSuperclass().getTypeName()` trên `Object` | Có thể `NullPointerException` do superclass là `null` | Kiểm tra `null` trước |

## 10. Bài tập tự kiểm tra

**Bài 1.** Với khai báo `List<String> names`, dự đoán subtype của `getGenericType()` và nội dung của `getTypeName()`. Tại sao cast kết quả thành `Class<?>` thất bại?

**Bài 2.** Với `T extends Number`, đọc generic type của field `T value`. Làm sao lấy `Number` làm bound thay vì chỉ nhận chuỗi `T`?

**Bài 3.** Với `Map<String, ? super Integer> map`, đi từ `ParameterizedType` đến `WildcardType` và in tất cả lower bounds mà không phân tích chuỗi.

**Bài 4.** Với `String[]` và `List<String>[]`, dự đoán loại `Type` và API lấy component type tương ứng.

**Bài 5.** Cho hai method dùng hai biến kiểu `T` khác nhau. Giải thích vì sao so `getTypeName().equals("T")` không chứng minh rằng hai type variables là một declaration.

**Bài 6.** Sửa đoạn sau để vừa in tên kiểu generic vừa tránh `ClassCastException`:

```java
Type[] params = method.getGenericParameterTypes();
for (Type param : params) {
    System.out.println(((Class<?>) param).getName());
}
```

Đáp án cốt lõi:

```java
for (Type param : method.getGenericParameterTypes()) {
    System.out.println(param.getTypeName());
}
```

Đây là **mảnh code bên trong một method** có biến `method` là một `Method` hợp lệ; nếu cần kiểm tra từng generic argument, tiếp tục phân tích subtype của `param`.

**Ghi nhớ:** `Type.getTypeName()` dùng để **mô tả tên kiểu của bất kỳ `Type` nào** mà không ép xuống `Class`. `Class<?>` là **một** triển khai của `Type`. Nếu logic cần hiểu cấu trúc, dùng các interface generic Reflection tương ứng; nếu logic cần erased runtime type, lấy `Class<?>` từ API cung cấp class sau erasure. Ba mục tiêu **hiển thị**, **phân tích generic** và **dùng runtime class** nên được phân biệt rõ ràng.

## 11. Tài liệu tham khảo

- [Java SE 17 — `Type`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Type.html): hợp đồng của `getTypeName()` và các subtype.
- [Java SE 17 — `Field.getGenericType()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html#getGenericType()): metadata generic của field. 
- [Java SE 17 — `Method.getGenericReturnType()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#getGenericReturnType()): kiểu trả về generic của method.
- [Java SE 17 — `Executable.getGenericParameterTypes()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html#getGenericParameterTypes()): generic parameters và lưu ý tham số implicit.
- [Java SE 17 — `ParameterizedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html), [`TypeVariable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/TypeVariable.html), [`GenericArrayType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/GenericArrayType.html), [`WildcardType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/WildcardType.html): API phân tích cấu trúc `Type`.
- [Java Language Specification, mục 4.6](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6): type erasure và cách liên hệ chữ ký generic với runtime types.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="generic-type-model-011">Phân biệt metadata generic trong khai báo với thông tin bị xóa lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt metadata generic trong khai báo với thông tin bị xóa lúc chạy

## 1. Câu hỏi trọng tâm

Khi khai báo một field kiểu `List<String>`, tại sao Reflection có thể in ra `java.util.List<java.lang.String>` bằng `Field.getGenericType()` nhưng một đối tượng `new ArrayList<String>()` ở runtime lại không cho biết phần tử của nó chắc chắn là `String`?

Câu trả lời nằm ở việc Java generic có **hai tầng thông tin khác nhau**:

1. **Metadata generic của khai báo** (*generic declaration metadata*): trình biên dịch có thể ghi lại mô tả kiểu trong class file, để các API Reflection như `getGenericType()`, `getGenericReturnType()`, `getGenericSuperclass()` đọc lại.
2. **Kiểu runtime sau type erasure**: JVM thực thi phần lớn generic thông qua **raw class, erased type và các cast do compiler chèn**. Một đối tượng `ArrayList<String>` thông thường không mang tham số `String` như một danh tính class runtime riêng biệt.

Do vậy, **thấy `List<String>` trong một `Type` qua Reflection không đồng nghĩa có thể kiểm tra toàn bộ kiểu phần tử của bất kỳ đối tượng `List` nào tại runtime**.

Sau bài học, bạn có thể phân biệt `Class<?>` và `Type`, đọc đúng các dạng `ParameterizedType`, `TypeVariable`, `WildcardType`, `GenericArrayType`, kiểm tra erased signatures, truy vết generic arguments trong quan hệ kế thừa, và chẩn đoán những lỗi khi giả định runtime còn giữ toàn bộ kiểu generic.

## 2. Ví dụ nhanh: cùng khai báo nhưng hai góc nhìn

Xét một lớp Java:

```java
import java.util.List;

class Store<T> {
    T value;
    List<String> labels;
}
```

Đây là **mã minh họa**, chưa có `main`. Với Reflection:

| Thành viên | Kiểu khai báo trong mã Java | API cho kiểu đã erasure | API cho metadata generic |
| --- | --- | --- | --- |
| `Store.value` | `T` | `getType()` trả `Object.class` nếu `T` không có bound cụ thể | `getGenericType()` trả một `TypeVariable` tên `T` |
| `Store.labels` | `List<String>` | `getType()` trả `List.class` | `getGenericType()` trả `ParameterizedType` biểu diễn `List<String>` |

Điểm cần phân biệt:

- Thông tin `List<String>` được gắn với **khai báo field `labels`**; đây là một đặc tả kiểu do compiler lưu để công cụ đọc.
- Nếu `labels` chứa một đối tượng `ArrayList<Integer>` do raw type hoặc lời gọi Reflection không an toàn truyền vào, Java runtime **không tự đối chiếu từng phần tử** theo chuỗi `String` lấy từ metadata.
- Nếu `new Store<String>()` và `new Store<Integer>()` là hai object bình thường, `getClass()` của cả hai đều trả về **`Store.class`**, không phải hai runtime class khác nhau.
- Field `value` không tự trở thành một field runtime có kiểu `String` trong mọi instance `Store<String>`. Erasure của `T` không bound là `Object`.

## 3. Type erasure thực sự xóa những gì?

Java generic chủ yếu sử dụng **type erasure** theo các quy tắc của Java Language Specification (JLS `4.6). Khi biên dịch, compiler chuyển thông tin generic cần thiết thành các dạng runtime phù hợp:

1. `List<String>` và `List<Integer>` đều có erasure `List`.
2. Một type variable **không có bound** như `T` có erasure là `Object`.
3. Type variable có một hoặc nhiều bound lấy erasure của **bound đầu tiên**.
4. Parameterized types và generic methods được biên dịch thành các descriptor mang erased types; compiler có thể chèn cast và phát sinh bridge method khi cần giữ tính đa hình.
5. Metadata generic của declaration có thể được ghi vào class file, chủ yếu qua thuộc tính **`Signature`** theo class-file format; JVM vẫn thực thi theo descriptor và kiểu runtime thông thường.

Ví dụ:

```java
class Bounded<T extends Number & Comparable<T>> {
    T amount;
}
```

Field `amount` có kiểu khai báo `T`. Với Reflection, `getType()` trả `Number.class` vì **`Number` là bound đầu tiên**, trong khi `getGenericType()` vẫn có thể trả `TypeVariable` đại diện cho `T` và đọc các bound từ đó.

**Erasure không có nghĩa “Reflection hoàn toàn không thấy generic”.** Nó có nghĩa thông tin kiểu được dùng để định danh object/method ở runtime **không còn đầy đủ như kiểu ở source**. Metadata mô tả declaration là một kênh khác.

### 3.1. Kiểu runtime vẫn có kiểm tra thật

Erasure không bỏ qua mọi kiểm tra kiểu:

- JVM vẫn kiểm tra object có đúng runtime class/interface không khi `checkcast`, `instanceof`, `Field.set` hoặc `Method.invoke` cần kiểm tra kiểu.
- Nếu method generic đọc một phần tử và compiler biết phải nhận `String`, nó có thể chèn `checkcast String` tại nơi sử dụng.
- Những kiểm tra này **không xác nhận đầy đủ** toàn bộ đối tượng `List` là một `List<String>`; cast thường xuất hiện khi một giá trị riêng lẻ được rút ra.

Chẳng hạn, nếu dùng raw API đưa `List<Integer>` vào nơi khai báo `List<String>` rồi trong method gán `String first = list.get(0);`, lỗi `ClassCastException` có thể xuất hiện **khi đọc phần tử**, không phải ngay khi truyền danh sách.

## 4. Chữ ký kiểu vẫn nằm trong mô hình `Type`

Các API generic trả `Type` vì một khai báo có thể chứa `Class` như `String`, `ParameterizedType` như `List<String>`, `TypeVariable` như `T`, wildcard bên trong `List<? extends Number>`, hoặc `GenericArrayType` như `T[]`. Phân biệt quan trọng cho bài erasure là: `Field.getType()` trả class runtime sau erasure; `Field.getGenericType()` trả cấu trúc generic **trên declaration** nếu metadata còn tồn tại. `Type.getTypeName()` chỉ hiển thị tên và không tạo runtime class riêng.

Không ép mọi `Type` sang `Class<?>`. Khi ví dụ bên dưới in ra `Map<String,List<Integer>>`, việc nó có hai tầng `ParameterizedType` nói lên cấu trúc **khai báo field**, chưa chứng minh collection runtime chứa đúng `Integer`.

## 5. Chương trình Java 17+ độc lập và kết quả chạy

Lưu toàn bộ mã sau thành **`GenericMetadataDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GenericMetadataDemo {
    static class Holder<T> {
        T value;
        T[] elements;
        List<String> labels;
        Map<String, List<Integer>> index;
        List<? extends Number> numbers;

        T echo(T item) { return item; }

        void consume(List<String> input) {
            System.out.println("consume size=" + input.size());
        }

        void readFirst(List<String> input) {
            String first = input.get(0);
            System.out.println("first=" + first);
        }
    }

    static class IntegerHolder extends Holder<Integer> {}
    static class Bounded<T extends Number & Comparable<T>> {
        T amount;
    }

    private static void inspect(String fieldName) throws Exception {
        Field field = Holder.class.getDeclaredField(fieldName);
        System.out.println(fieldName + " raw=" + field.getType().getTypeName());
        System.out.println(fieldName + " generic=" + field.getGenericType().getTypeName());
    }

    public static void main(String[] args) throws Exception {
        inspect("value");
        inspect("elements");
        inspect("labels");
        inspect("index");
        inspect("numbers");

        Field labels = Holder.class.getDeclaredField("labels");
        Type declared = labels.getGenericType();
        System.out.println("labels is ParameterizedType="
                + (declared instanceof ParameterizedType));

        Method echo = Holder.class.getDeclaredMethod("echo", Object.class);
        System.out.println("echo raw return=" + echo.getReturnType().getTypeName());
        System.out.println("echo generic return="
                + echo.getGenericReturnType().getTypeName());
        System.out.println("bounded raw="
                + Bounded.class.getDeclaredField("amount").getType().getTypeName());
        System.out.println("subclass generic parent="
                + IntegerHolder.class.getGenericSuperclass().getTypeName());
        System.out.println("Holder type variable="
                + Holder.class.getTypeParameters()[0].getName());

        Class<?> a = new ArrayList<String>().getClass();
        Class<?> b = new ArrayList<Integer>().getClass();
        System.out.println("same runtime class=" + (a == b));

        Holder<String> holder = new Holder<>();
        Method consume = Holder.class.getDeclaredMethod("consume", List.class);
        consume.invoke(holder, List.of(42));
        Method readFirst = Holder.class.getDeclaredMethod("readFirst", List.class);
        try {
            readFirst.invoke(holder, List.of(42));
        } catch (InvocationTargetException e) {
            System.out.println("readFirst cause="
                    + e.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac --release 17 GenericMetadataDemo.java
java GenericMetadataDemo
```

**Stdout chính xác** đã kiểm tra với `javac 21 --release 17` và OpenJDK 21:

```text
value raw=java.lang.Object
value generic=T
elements raw=java.lang.Object[]
elements generic=T[]
labels raw=java.util.List
labels generic=java.util.List<java.lang.String>
index raw=java.util.Map
index generic=java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>
numbers raw=java.util.List
numbers generic=java.util.List<? extends java.lang.Number>
labels is ParameterizedType=true
echo raw return=java.lang.Object
echo generic return=T
bounded raw=java.lang.Number
subclass generic parent=GenericMetadataDemo$Holder<java.lang.Integer>
Holder type variable=T
same runtime class=true
consume size=1
readFirst cause=ClassCastException
```

### 5.1. Giải thích từng nhóm dòng

**`value`:** `T` là type variable không bound, có erasure `Object`. `Field.getType()` chỉ thấy `Object`, còn `getGenericType()` đọc lại ký hiệu `T` từ metadata khai báo.

**`elements`:** khai báo `T[]` có runtime erasure `Object[]`, nhưng `getGenericType()` vẫn mô tả `T[]`, thường bằng một `GenericArrayType`.

**`labels`, `index`, `numbers`:** mọi field đều có raw type là `List` hoặc `Map`. Metadata vẫn chứa argument `String`, cấu trúc lồng nhau `List<Integer>` và wildcard `? extends Number`, tương ứng `ParameterizedType` cùng các nút `Type` con.

**`echo`:** phương thức `T echo(T)` được tìm bằng erased parameter type `Object.class`. Runtime return type là `Object`; generic return type là `T`.

**`bounded`:** `T extends Number & Comparable<T>` lấy erasure của **bound đầu tiên** là `Number`. Điều đó không phủ nhận bound thứ hai; nó vẫn thuộc metadata của `TypeVariable`.

**`IntegerHolder`:** lời khai báo `extends Holder<Integer>` ghi lại tham số kiểu cụ thể ở **quan hệ superclass**; Reflection có thể đọc `Holder<Integer>`. Nhưng field `value` thực ra vẫn được khai báo trong `Holder<T>`, nên bản thân `Field.getGenericType()` của field ấy vẫn là `T`.

**`same runtime class=true`:** generic instantiations `ArrayList<String>` và `ArrayList<Integer>` đều có cùng runtime class `ArrayList`. Chương trình không thể dùng `list.getClass()` để xác định argument `String` hay `Integer` đã sử dụng ở source.

**`consume` và `readFirst`:** Reflection chấp nhận `List.of(42)` ở tham số method khai báo `List<String>` vì erased parameter type là `List`. `consume` chỉ đọc `size()` nên hoạt động. Trong `readFirst`, khi gán phần tử lấy ra vào `String`, phép cast do compiler chèn phát sinh `ClassCastException`; Reflection bao nó vào `InvocationTargetException` và chương trình in cause.

## 6. Metadata generic thực tế được lưu ở đâu?

Trong class file, JVM dùng **field/method descriptors** để biểu diễn các kiểu cần thiết cho runtime invocation và linking. Compiler Java có thể ghi thêm **`Signature` attribute** mô tả generic type parameters, parameterized field types, generic methods hoặc generic superclass/interface theo cấu trúc ở source.

Có thể kiểm tra hai kênh bằng JDK tool:

```bash
javap -v -p 'GenericMetadataDemo$Holder'
javap -v -p 'GenericMetadataDemo$IntegerHolder'
```

Dấu nháy đơn giúp bảo vệ ký tự `$` trong tên binary của nested class ở các shell có hỗ trợ cú pháp này (chẳng hạn Bash và PowerShell).

Trong `Holder`, chú ý:

- `descriptor: Ljava/lang/Object;` cho `value`; phần `Signature` có thể biểu diễn `TT;`.
- `descriptor: Ljava/util/List;` cho `labels`; `Signature` biểu diễn `Ljava/util/List<Ljava/lang/String;>;`.
- `descriptor: [Ljava/lang/Object;` cho `elements`; generic signature vẫn ghi `[TT;`.
- Erased descriptor của `echo` dùng tham số/giá trị trả về `Object`; generic signature dùng `T`.

Trong `IntegerHolder`, generic superclass `Holder<Integer>` được mô tả bằng `Signature` dù runtime superclass vẫn là chính `Holder.class`.

**Không nên hiểu `Signature` là một danh sách type arguments gắn tự động với từng instance.** Nó mô tả khai báo class/member hoặc generic supertype relationship. Một object tạo bằng `new Holder<String>()` thông thường không giữ riêng type argument này ở `getClass()`.

### 6.1. Nếu generic signature không có thì sao?

Không phải class file do mọi ngôn ngữ hoặc tool tạo ra đều lưu đủ metadata generic như `javac`. Công cụ bytecode transformer hoặc obfuscator cũng có thể bỏ hoặc làm hỏng `Signature`:

- Khi không có thông tin generic bổ sung, generic reflection API có thể chỉ cho **raw `Class<?>`** tương ứng.
- Metadata có thể nêu type không load được, khiến truy cập generic type phát sinh `TypeNotPresentException`.
- Chữ ký generic không hợp lệ có thể dẫn tới `GenericSignatureFormatError` hoặc `MalformedParameterizedTypeException` tùy dạng lỗi.

Nếu ứng dụng phân tích bytecode từ nguồn lạ, nên xử lý các lỗi đó rõ ràng. **Không suy diễn một `List.class` trở thành `List<String>` chỉ vì quy ước đặt tên hoặc dữ liệu tình cờ chứa String.**

## 7. Dữ liệu generic phải được đọc có cấu trúc

Khi `Holder.labels` khai báo `List<String>`, `getGenericType()` trả `ParameterizedType` có `getRawType() == List.class`, argument `String.class` và owner `null`. Với field `Map<String, List<Integer>>`, argument thứ hai lại là một `ParameterizedType`; với `List<? extends Number>`, argument là `WildcardType`. Vì vậy metadata cần phân tích qua các subtype của `Type`, không ép mọi argument sang `Class<?>` hoặc parse chuỗi `getTypeName()`.

Điểm của bài cuối là **cùng cấu trúc ấy chỉ mô tả lời khai báo**. Phần tiếp theo thử truy vết type argument ở subclass rồi phân tích những runtime checks thực sự còn hiệu lực.

## 8. Truy vết type argument qua lớp con: có thể biết được gì?

`IntegerHolder extends Holder<Integer>` ghi rõ argument `Integer` trong generic superclass declaration. Vì vậy:

```java
Type parent = IntegerHolder.class.getGenericSuperclass();
if (parent instanceof ParameterizedType pt) {
    System.out.println(pt.getActualTypeArguments()[0].getTypeName());
    // java.lang.Integer
}
```

Đây là **mã minh họa**, sử dụng kiểu từ `GenericMetadataDemo` và import `Type`, `ParameterizedType` như chương trình chính.

Nhưng kết luận cần giới hạn:

- Trong subclass `IntegerHolder`, declaration ở cạnh `extends Holder<Integer>` gắn một argument cụ thể, nên metadata cho biết `T` tương ứng `Integer` ở quan hệ đó.
- `Field value` vẫn do `Holder<T>` khai báo; gọi `field.getGenericType()` một mình trả về `T`, **không tự thay thành Integer** khi đối tượng field được lấy từ `IntegerHolder.class`.
- Nếu có chuỗi `Derived<U> extends Middle<List<U>>` rồi `Concrete extends Derived<String>`, việc suy ra field gốc cần **truy vết nhiều cạnh và ánh xạ `TypeVariable`** qua từng tham số kiểu. Java Reflection cơ bản cung cấp dữ liệu, không có một API duy nhất tự giải mọi phép thay thế generic trong hệ phân cấp.
- Nếu chỉ có một đối tượng bình thường `new Holder<Integer>()` và không có subclass mang tham số cố định hay token bổ sung, thường **không có đủ metadata runtime** để truy ra `Integer`.

### 8.1. Anonymous subclass và type token

Một kỹ thuật thường gặp là tạo subclass ẩn danh với superclass generic cố định:

```java
Holder<String> typed = new Holder<String>() {};
Type genericParent = typed.getClass().getGenericSuperclass();
System.out.println(genericParent.getTypeName());
```

Đây là **mã minh họa** sử dụng `Holder` từ chương trình chính, không phải chương trình độc lập. Một compiler như `javac` giữ metadata `Holder<String>` trên declaration của anonymous subclass, nên nhiều thư viện *type token* đọc được type arguments từ `getGenericSuperclass()`.

Tuy nhiên:

- Kỹ thuật này dựa vào **metadata của lớp con ẩn danh**, không chứng minh mọi instance `Holder<String>` tự có type parameter ở runtime.
- Không phải mọi biểu thức generic đều được giữ đầy đủ nếu kiểu vẫn còn là `T` từ một scope khác.
- Metadata cũng không biến phép kiểm tra `instanceof List<String>` thành hợp lệ trong Java source.

## 9. Những thông tin generic không thể kiểm tra trực tiếp ở runtime

### 9.1. `instanceof` và `List<String>`

Trong Java:

```java
Object value = java.util.List.of("a", "b");

if (value instanceof java.util.List<?>) {
    System.out.println("some list");
}

// Sai: instanceof List<String> không phải kiểm tra reifiable hợp lệ.
// if (value instanceof java.util.List<String>) { ... }
```

Đây là đoạn minh họa; cần import hoặc fully qualified name tương ứng. `List<?>` là một dạng reifiable cho kiểm tra `instanceof`; `List<String>` không reifiable theo kiểu đó.

`value instanceof List<?>` chỉ chứng minh object là một `List` phù hợp ở runtime, **không chứng minh các phần tử là String**.

### 9.2. Không thể tạo generic array trực tiếp với type variable

Trong class generic, biểu thức:

```java
class Buffer<T> {
    // T[] array = new T[10]; // Không hợp lệ.
}
```

không hợp lệ vì runtime cần biết loại component của array để thực hiện store checks, còn `T` không được reify như class tùy ý. Có thể tạo array bằng một `Class<T>` token phù hợp qua `Array.newInstance(...)` và cân nhắc cast, nhưng cần quản lý an toàn kiểu của token/array.

### 9.3. Raw types và unchecked conversions

Nếu sử dụng raw `List`, cast unchecked hoặc Reflection, bạn có thể đưa một collection chứa `Integer` vào nơi khai báo `List<String>`. Compiler có thể cảnh báo, nhưng runtime không tự duyệt collection để xác minh generic element type.

**Hệ quả thực tế:** khi nhận dữ liệu từ JSON, DB hoặc hệ thống plugin, muốn đảm bảo `List<String>` thực sự chỉ chứa `String`, ứng dụng phải kiểm tra **giá trị từng phần tử** hoặc sử dụng framework có runtime type-token và quy tắc validation phù hợp.

## 10. Phân biệt generic declaration metadata và generic erasure khi tìm method

Giả sử một method khai báo:

```java
static void process(java.util.List<String> values) {
    System.out.println(values.size());
}
```

Đây là **mã minh họa** chưa có class chạy. Reflection tìm theo **erased parameter types**, không theo `List<String>`:

```java
Method method = SomeClass.class.getDeclaredMethod(
        "process", java.util.List.class);
```

`SomeClass` là class giả định chứa method. Nếu cần đọc chữ ký generic, gọi `method.getGenericParameterTypes()` sau khi đã tìm được method.

Cần nhớ:

- `getDeclaredMethod` nhận `Class<?>...` của **formal parameters**, không nhận `ParameterizedType`.
- Hai method trong cùng class **không thể overload chỉ bằng `List<String>` so với `List<Integer>`**, bởi cả hai có cùng erasure `List` và gây name clash.
- Compiler có thể sinh **bridge method** khi overriding generic method cần tương thích sau erasure. Reflection sẽ thấy cả method gốc và bridge; `Method.isBridge()` giúp phân biệt, còn `getGenericParameterTypes()` phải đọc trên đúng `Method` bạn đang phân tích.
- `Method.invoke` vẫn thực hiện kiểm tra loại tham số erased tương ứng; metadata generic không tự áp đặt ràng buộc `String` lên từng phần tử collection.

## 11. Đọc metadata generic qua Reflection có phải luôn đáng tin?

Đúng **đối với điều mà metadata thể hiện**: nó mô tả declaration và generic supertype relationships được ghi trong class file, thường do compiler `javac` tạo. Nhưng mục đích dùng phải đúng:

| Mục tiêu | Metadata generic hữu ích? | Giới hạn |
| --- | --- | --- |
| Hiển thị `Map<String, List<Integer>>` trong tài liệu API | Có, dùng `getGenericType()` và phân tích `Type` | Metadata có thể vắng hoặc bị công cụ bytecode sửa |
| Phân tích `Child extends Base<Foo>` | Có, dùng `getGenericSuperclass()` | Phải tự ánh xạ type variables khi quan hệ kế thừa nhiều tầng |
| Xác nhận `List<?>` là collection kiểu `List` | Có thể dùng `isInstance`/`isAssignableFrom` với raw class | Không kiểm tra được type argument |
| Chứng minh mọi phần tử trong runtime collection là `String` | Không chỉ bằng metadata | Cần kiểm tra giá trị hoặc có ràng buộc từ cơ chế khác |
| Tìm method nhận `List<String>` | Dùng `getDeclaredMethod` với `List.class`, rồi xem metadata | Không truy vấn method bằng `List<String>.class` |
| Tìm class của `T` ở một object `new Holder<Integer>()` | Thường không | Type argument của parameterized object không được reify |
| Serialization/deserialization có kiểu phần tử | Có, nếu caller cung cấp `Type`/type token hoặc field declaration | Vẫn cần thực thi conversion/validation từ dữ liệu ngoài |

### 11.1. Rủi ro khi framework suy luận quá mức

Một framework có thể đọc `Field.getGenericType()` ra `List<String>` rồi dùng nó để deserialize JSON thành `List<String>`. Đây là **sử dụng metadata để chủ động hướng dẫn conversion**, không phải JVM bất ngờ “nhớ lại” type argument của đối tượng raw `ArrayList`.

Tương tự, ORM/DI container có thể dùng type arguments để resolve dependency hoặc mapping. Độ đúng của thao tác dựa vào **mô hình và runtime checks của framework**, bao gồm xử lý wildcard, generic hierarchy, raw type và class loader, chứ không chỉ vào một lời gọi `getGenericType()`.

## 12. Lỗi phổ biến và cách khắc phục

**Lỗi 1 — “Generics bị erase nên `getGenericType()` chắc cũng chỉ trả raw class.”** Erasure áp dụng cho runtime representation/descriptor; metadata generic trong `Signature` vẫn có thể được Reflection đọc.

**Lỗi 2 — “`getGenericType()` trả `List<String>` nên object field đang chứa chắc chắn là `List<String>`.”** Metadata mô tả **khai báo field**; không tự xác nhận trạng thái runtime hay từng element.

**Lỗi 3 — Ép mọi `Type` thành `Class<?>`.** `ParameterizedType`, `TypeVariable`, `WildcardType` và `GenericArrayType` không phải `Class<?>`. Cần phân nhánh theo `instanceof`.

**Lỗi 4 — Tìm method `echo(T)` bằng argument class cụ thể.** Runtime lookup dùng erased parameter, ví dụ `Object.class` hoặc erasure của bound đầu tiên.

**Lỗi 5 — Nhầm `getGenericSuperclass()` là generic superclass của object gốc đã truyền tham số.** Nó mô tả declaration **trực tiếp của class được hỏi**; `new Holder<String>().getClass()` vẫn là `Holder.class`, không tự cung cấp `String`. Subclass `IntegerHolder` mới ghi lại `Holder<Integer>` ở cạnh `extends`.

**Lỗi 6 — Kiểm tra `List<String>` bằng `instanceof`.** Đây không phải runtime-reifiable parameterized type. Kiểm tra raw class và dữ liệu phần tử bằng giải pháp thích hợp.

**Lỗi 7 — Cho rằng `Map<String,List<Integer>>` là một `ParameterizedType` phẳng có toàn bộ arguments là `Class<?>`.** Phải duyệt cấu trúc `Type` lồng nhau.

**Lỗi 8 — Cho rằng generic signature luôn tồn tại và luôn hợp lệ.** Class-file tools hoặc code do ngôn ngữ khác biên dịch có thể làm metadata vắng mặt hay không hợp lệ. Cần xử lý trường hợp fallback/lỗi đọc generic type.

**Lỗi 9 — Đồng nhất lỗi Reflection trước invoke với lỗi cast trong method.** Truyền `List<Integer>` vào parameter erased `List` có thể thành công; cast `String` thất bại trong method và `Method.invoke` bọc bằng `InvocationTargetException`.

**Lỗi 10 — Cho rằng danh tính kiểu generic được phân biệt bởi `getClass()`.** `ArrayList<String>` và `ArrayList<Integer>` thông thường cùng runtime class; nơi giữ được generic arguments là metadata ở **declaration**, hoặc type token được tạo có chủ đích.

## 13. Bài tập tự kiểm tra

1. Thay `Holder<T>` bằng `Holder<T extends Number>`. Dự đoán `value.getType()`, `value.getGenericType()` và erased signature của `echo(T)` trước khi chạy chương trình.
2. Dùng `ParameterizedType.getActualTypeArguments()` duyệt `Map<String, List<Integer>>` trong `index`. Vẽ cây `Type` và chỉ ra nút nào là `Class<?>`, nút nào là `ParameterizedType`.
3. Thêm `List<? super Integer> lower;` vào `Holder`. Đọc `WildcardType.getLowerBounds()` và giải thích khác với `? extends Number`.
4. Tạo `new Holder<String>() {}` rồi kiểm tra `getGenericSuperclass()`; so sánh với `new Holder<String>().getClass().getGenericSuperclass()`.
5. Với `List.of(42)` truyền qua Reflection vào `consume(List<String>)`, giải thích vì sao không có lỗi; với `readFirst(List<String>)`, giải thích `ClassCastException` xuất phát từ đâu.
6. Dùng `javap -v -p` xem descriptor và `Signature` của field `value`, `labels` và superclass `IntegerHolder`. Chỉ ra thông tin nào phục vụ runtime linkage và thông tin nào dùng để mô tả generic declaration.

## 14. Checklist áp dụng thực tế

- Khi cần biết **kiểu dùng để gán/invoke ở runtime**, lấy `Field.getType()`, `Method.getParameterTypes()`, `getReturnType()` và kiểm tra assignability.
- Khi cần đọc **chữ ký generic khai báo**, lấy `getGenericType()`, `getGenericParameterTypes()`, `getGenericReturnType()`, `getGenericSuperclass()` hoặc `getGenericInterfaces()` phù hợp.
- Xác định từng dạng `Type` trước khi dùng; đừng cast `Type` thành `Class<?>` vô điều kiện.
- Khi đi qua generic inheritance, duy trì ánh xạ `TypeVariable → Type` và thay thế qua từng cạnh; không suy từ field declaration riêng lẻ.
- Không coi metadata `List<String>` là kiểm chứng runtime element types; tách schema declaration khỏi validation thực tế.
- Đối với raw Reflection, chuẩn bị cho unchecked conversions, runtime casts và `InvocationTargetException` ở nơi code sử dụng giá trị.
- Với class-file tooling, kiểm tra sự tồn tại và độ hợp lệ của `Signature`; đừng suy đoán tham số kiểu đã bị mất.
- Nếu cần giữ type arguments tại runtime, truyền `Class<T>` hoặc một cấu trúc `Type`/type token phù hợp từ caller, sau đó chủ động kiểm tra dữ liệu.

**Ghi nhớ:** **type erasure** làm những tham số generic như `String` trong `List<String>` không trở thành danh tính runtime của từng object, nhưng **metadata generic của khai báo** có thể vẫn được lưu lại để Reflection đọc. `Class<?>` và `Type` trả lời hai câu hỏi khác nhau; phân biệt chúng là nền tảng để viết code Reflection, serializer và framework generic đúng đắn.

</details>

- [Quay lại đầu trang](#back-to-top)
